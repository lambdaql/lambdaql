# 8. Stored procedures and DB functions (Kotlin)

> [!NOTE]
> **Status: designed, not implemented yet.**
> This chapter is a decided design (decisions D30 and D31, milestone M11).
> The Kotlin syntax compiles (kotlinc 2.3.21), and the calls were checked on H2, MariaDB and Oracle 23ai with Hibernate 6.6.5.
> It does **not** run in lambdaQL today. Today only the analysis core and `where` + `toList()` work
> (tested on H2, MariaDB and Oracle). See the [track README](README.md) for the full status.

[Kotlin track](README.md) | [Manual home](../README.md) | Previous: [7. Update, insert, delete](07-dml.md) | Next: [9. Kotlin notes](09-kotlin-notes.md) | [Java version of this page](../java/08-procedures-functions.md)

Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md)

This chapter has two parts:

| Part | What | Annotation | How you call it |
|---|---|---|---|
| [8.1 to 8.5](#81-declare-procedures) | **Stored procedures** | `@SqlProcedure` on an abstract interface function | `qb.call(OrderProcs::class.java) { it.calc(id, rate) }.execute()` |
| [8.6 to 8.10](#86-declare-db-functions) | **DB functions** (scalar) | `@SqlFunction` on a `@JvmStatic` function in the interface `companion object` | directly inside any lambda: `where { OrderFns.discount(it.price)!! > 10 }` |

In both cases, you write the signature **once** in a Kotlin interface.
lambdaQL **never runs** these functions. It reads the call in the lambda bytecode and calls the database.

## Contents

- [8.1 Declare procedures](#81-declare-procedures)
- [8.2 Call procedures](#82-call-procedures)
- [8.3 OUT, INOUT and result sets](#83-out-inout-and-result-sets)
- [8.4 Warning: argument order](#84-warning-argument-order)
- [8.5 Procedure rules PR1 to PR15 (summary)](#85-procedure-rules-pr1-to-pr15-summary)
- [8.6 Declare DB functions](#86-declare-db-functions)
- [8.7 Use DB functions in queries](#87-use-db-functions-in-queries)
- [8.8 Call a DB function alone](#88-call-a-db-function-alone)
- [8.9 Warning: functions on columns and indexes (SARGable)](#89-warning-functions-on-columns-and-indexes-sargable)
- [8.10 Function rules FN1 to FN11 (summary)](#810-function-rules-fn1-to-fn11-summary)
- [8.11 Database support](#811-database-support)

---

## 8.1 Declare procedures

Write an interface. Put `@SqlProcedure("NAME")` on each **abstract** function.
You do not write an implementation. lambdaQL does not make one either.

```kotlin
interface OrderProcs {
    @SqlProcedure("SQ_CALC")     fun calc(id: Long, rate: Double): Double?               // 1 OUT → return value
    @SqlProcedure("SQ_CALC2")    fun calc2(id: Long, rate: Double): CalcOut              // many OUT → data class
    @SqlProcedure("SP_ADJUST")   fun adjust(id: Long, @InOut balance: Int?): AdjOut      // INOUT
    @SqlProcedure("SP_RECENT")   fun recent(custId: Long): List<OrderView>               // result set / Oracle REF CURSOR
    @SqlProcedure("SQ_PKG.CALC") fun pkgCalc(id: Long, rate: Double): Double?            // package or schema name
    @SqlProcedure("SP_PURGE")    fun purge(days: Int)                                    // no result
}

data class CalcOut(val total: Double?, val cnt: Int?)
data class AdjOut(val balance: Int?, val msg: String?)
```

Notes:

- `@SqlProcedure` (or `@SqlFunction`) is **required**. It tells lambdaQL what the function is.
  (The names `@Procedure` and `@Function` were not used: `@Procedure` is also a Spring Data JPA annotation, and `@Function` clashes with Kotlin's `kotlin.Function`.)
- If the value is empty, the name comes from the function name: `calcTotal` → `CALC_TOTAL`.
- Use a **nullable return type** (`Double?`). If the procedure returns NULL into a non-null type, lambdaQL throws a named exception.
- The interface and the result data classes are a copy of the DB signature. This is the price for type safety. lambdaQL checks part of it against the database (rule PR2).
- You can put procedures (abstract functions) and DB functions (in the `companion object`) in **the same interface**.

---

## 8.2 Call procedures

Use `qb.call(Interface::class.java) { it.function(args) }`. Then `execute()`.

```kotlin
val d: Double?           = qb.call(OrderProcs::class.java) { it.calc(id, rate) }.execute()
val co: CalcOut          = qb.call(OrderProcs::class.java) { it.calc2(id, rate) }.execute()
val a: AdjOut            = qb.call(OrderProcs::class.java) { it.adjust(id, balance) }.execute()
val r: List<OrderView>   = qb.call(OrderProcs::class.java) { it.recent(custId) }.execute()

// Named arguments with simple values
val d2 = qb.call(OrderProcs::class.java) { it.calc(rate = rate, id = id) }.execute()

// No result: use exec
qb.exec(OrderProcs::class.java) { it.purge(30) }.execute()
```

Why `exec` and not `call` for a function with no result?

- In Java, `call` for both would be an ambiguous overload (compile error). Both languages use the same API.
- `run` was not chosen: in Kotlin, if you forget an argument, `run { }` silently becomes the standard library `run`.

### What the lambda may contain (rule PR12)

The lambda body is **one call of a stub function**. Nothing else.

| Allowed | Not allowed |
|---|---|
| `{ it.calc(id, rate) }` | logging, two calls, `if`, nested calls |
| arguments: captured values, constants, calculations of them (`rate * 2`) | entity paths (there is no entity here) |
| named arguments with **simple values** (`calc(rate = rate, id = id)`) | named arguments with calculations (`calc(rate = r * 2, id = id)`), see below |

Kotlin makes hidden local variables for named arguments that have a calculation. lambdaQL rejects local variables in v0.1.
Use positional arguments in that case: `it.calc(id, r * 2)`.

Other points:

- `exec` with a procedure that has OUT parameters is an error. This stops OUT values from being lost by mistake.
- A stub function with **default arguments** (`fun calc(id: Long, rate: Double = 1.0)`) is an error (rule PR11). Kotlin would fill in the Kotlin default, not the DB default.
- Database default values for parameters cannot be used. Pass every IN parameter.
- `null` as an IN value is fine.
- Non-null `Long`/`Double` parameters and nullable `Long?`/`Double?` parameters both work (rule PR13).

---

## 8.3 OUT, INOUT and result sets

How the return type maps to the procedure (rules PR3, PR4, PR5):

| Procedure has | Return type of the stub function |
|---|---|
| nothing | no return type (`Unit`) → call with `qb.exec(...)` |
| 1 OUT parameter | that value (`Double?`, `Int?`, ...) |
| many OUT / INOUT parameters | a Kotlin data class (or a Java record) |
| 1 result set (MariaDB `SELECT`, Oracle `SYS_REFCURSOR` OUT, H2 `ResultSet` function) | `List<T>` |
| many result sets | a data class with `List` properties |
| OUT values and result sets (MariaDB) | a data class with scalar and `List` properties |

How data class properties are filled:

- **Scalar properties** ← OUT and INOUT parameters (not cursors), in DB order.
- **`List` properties** ← result sets, in order (Oracle: order of the cursor OUT parameters; MariaDB: order of the returned result sets).
- If the numbers do not match, lambdaQL throws an error.
- Mixed positions like IN, OUT, IN, INOUT are fine. lambdaQL reads the positions from the DB metadata.

### INOUT

Mark the parameter with `@InOut`. The value you pass goes **in**. The value that comes **out** is a property of the returned data class.

```kotlin
@SqlProcedure("SP_ADJUST") fun adjust(id: Long, @InOut balance: Int?): AdjOut

val a = qb.call(OrderProcs::class.java) { it.adjust(id, 100) }.execute()
// a.balance is the new balance
```

### Result set element type `T`

| `T` is | How lambdaQL reads it |
|---|---|
| an entity | Hibernate result class |
| a data class, record or DTO | JDBC `CallableStatement`, columns matched **by label** (`camelCase` ↔ `snake_case`, case ignored) |

lambdaQL never maps columns by position, because MariaDB and Oracle return columns of `SELECT *` in different orders.
It converts common types (for example a number to `Long`/`Int`, a timestamp to `LocalDateTime`).

Limits:

- Paging a procedure result is not possible.
- Oracle implicit results (`DBMS_SQL.RETURN_RESULT`) are not supported. Use a `SYS_REFCURSOR` OUT parameter.

### Transactions (rule PR9)

- DML inside a procedure is part of the calling transaction. It rolls back with it.
- Before the call, lambdaQL flushes the persistence context, but only if a transaction is active and there are changes.
- Outside a transaction, the call is autocommit.
- After the call, loaded entities can be stale. Use `refresh` or `evict` on those entities.
- A `COMMIT` or DDL inside the procedure breaks the transaction. lambdaQL cannot stop this.

### Affected rows (rule PR10)

There is no common "affected rows" result for procedures (Oracle returns -1).
If you need the number, return it as an OUT parameter.

---

## 8.4 Warning: argument order

> [!WARNING]
> **The order of the stub function parameters must be the same as the order in the database.**

lambdaQL binds arguments **by position** (rule PR2).
Hibernate 6.6.5 binds by position even when names are registered.
The MariaDB and H2 drivers do not support named parameters.
So if the order is wrong, you can get a **wrong result without any error**.

Kotlin named arguments (`calc(rate = rate, id = id)`) do **not** change this. The compiler puts the arguments back in the order of the stub function. Only the stub function order counts.

What lambdaQL checks on the first call (from JDBC metadata):

| Check | Found? |
|---|---|
| number of parameters | yes, error |
| mode (IN / OUT / INOUT / cursor) | yes, error |
| type | yes, error |
| two parameters **of the same type** swapped | **only if parameter names are available** |

Example of the dangerous case:

```kotlin
// Database: SP_TRANSFER(p_to NUMBER, p_from NUMBER)
@SqlProcedure("SP_TRANSFER") fun transfer(from: Long, to: Long)   // swapped! same types
```

The count, modes and types all match. Only the names can show the mistake.
lambdaQL compares names when it can get them: Kotlin `-java-parameters` (or kotlin-reflect), or `@Param`.
It ignores the `p_` prefix and case. If names are not available and two neighbor parameters have the same type, lambdaQL logs a WARN.

**Advice:** compile with `-java-parameters` (see [Kotlin compiler settings](02-getting-started.md#kotlin-compiler-settings)), and use the same names as the database.

On Oracle, lambdaQL can also use real name binding (`pass_procedure_paramater_names=true` together with the stub parameter names).

---

## 8.5 Procedure rules PR1 to PR15 (summary)

| # | Rule |
|---|---|
| PR1 | Name: the annotation value as is. Empty value: function name in UPPER_SNAKE. Package or schema names (`SQ_PKG.CALC`, `LQL.SQ_CALC`) work. You can also write `@SqlProcedure(schema = ..., pkg = ..., value = ...)` to avoid confusion. |
| PR2 | Arguments are bound **by position**. On the first call, count, mode and type are checked against DB metadata. Swapped same-type arguments are found only with parameter names (see [8.4](#84-warning-argument-order)). Oracle overloads in a package are an error in v0.1. |
| PR3 | One OUT → return value. Many OUT/INOUT → data class or record. `@InOut` marks INOUT parameters. |
| PR4 | One result set → `List<T>`. Entities through Hibernate; data classes/records/DTOs through JDBC with label matching. No paging. |
| PR5 | Many result sets → `List` properties of a data class, in order. Oracle implicit results are rejected. |
| PR6 | A DB function called alone uses a different path per database (see [8.8](#88-call-a-db-function-alone)). |
| PR7 | DB functions in queries: see [8.6](#86-declare-db-functions) and rules FN1 to FN11. |
| PR8 | A DB function on a column in a condition gives a WARN, because the index cannot be used (see [8.9](#89-warning-functions-on-columns-and-indexes-sargable)). |
| PR9 | The procedure runs in the calling transaction. lambdaQL flushes before the call when needed. Loaded entities can be stale after it. |
| PR10 | No common affected-row count. Use an OUT parameter. |
| PR11 | `null` IN values are fine. DB parameter defaults cannot be used. Kotlin default arguments on stub functions are an error. |
| PR12 | The lambda is one stub call. Arguments are captured values, constants or calculations of them. Named arguments only with simple values. `exec` with OUT parameters is an error. |
| PR13 | Non-null `Long`/`Double` and nullable `Long?`/`Double?` both work. Boolean parameters work on MariaDB and Oracle 23ai or later. |
| PR14 | Each lambda is analyzed once. Procedure metadata is read once per name. |
| PR15 | H2 has no OUT, INOUT, many result sets or packages. Test procedures on MariaDB or Oracle (for example with Testcontainers). |

---

## 8.6 Declare DB functions

A DB function is a function you made in the database, for example `FN_DISCOUNT(price)`.
Kotlin interfaces have no static functions, so declare it in the interface **`companion object`**, with **`@JvmStatic`** and `@SqlFunction`.
The body must only throw. It never runs inside lambdaQL.

```kotlin
interface OrderFns {
    companion object {
        @JvmStatic @SqlFunction("FN_DISCOUNT")
        fun discount(price: Double?): Double? = throw DbOnlyException()

        @JvmStatic @SqlFunction("FN_IS_VIP")            // Boolean: Oracle 23ai or later
        fun isVip(customerId: Long?): Boolean? = throw DbOnlyException()
    }
}
```

If you call `OrderFns.discount(...)` **outside** a lambdaQL lambda, it throws. This is on purpose.

Why `@JvmStatic`? Java code can then call `OrderFns.discount(...)` as a static function, and the function looks the same for Java and Kotlin callers.
lambdaQL reads the annotation on the `companion object` function.

Other ways to declare:

| Declaration | Status |
|---|---|
| Interface `companion object` + `@JvmStatic` | **recommended** |
| Kotlin `object` + `@JvmStatic` | supported |
| Java interface static method (called from Kotlin) | supported |
| Kotlin top-level function | supported, **not recommended**: if you forget the import, a standard library function with the same name (for example `maxOf`) can be used silently |
| interface default function + a singleton constant | supported, but longer to write |
| your repository implements the function interface (mixin) | rejected |
| functional interface constant (`DISCOUNT.apply(x)`), function type property | rejected |
| extension function | rejected in v0.1 (KC11) |
| `@SqlFunction` with default arguments, `inline`, or `vararg` | rejected (FN9) |

Call the function **with the interface name** (`OrderFns.discount`). An import of the function works, but a member function with the same name can hide it.

---

## 8.7 Use DB functions in queries

Call the function directly. No context argument is needed. It works in every lambda:
`where`, `select`, `orderBy`, `groupBy`, `having`, `set`, and inside subqueries.

```kotlin
qb.from(Order::class.java).where { OrderFns.discount(it.price)!! > 10 }.toList()
// → where function('FN_DISCOUNT' as Double, o.price) > 10

qb.from(Order::class.java).where { OrderFns.isVip(it.customer?.id)!! }.toList()     // a Boolean function alone

qb.from(Order::class.java).where { it.price!! > OrderFns.discount(base)!! }.toList()  // column side stays clean

qb.from(Order::class.java).orderBy { o, by -> by.desc(OrderFns.discount(o.price)) }.toList()

qb.from(Order::class.java)
    .select { o -> OrderView(o.id, o.customer?.name, OrderFns.discount(o.price)) }
    .toList()

qb.update(Order::class.java).set { it.price = OrderFns.discount(it.price) }.all().execute()
```

**About the SQL blocks.** `…` is the shortened select list (all entity columns), and `?` is a bind parameter. [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java) gives the HQL of this page to Hibernate directly and records the SQL on H2 2.3, MariaDB 10.11 and Oracle 23.26 (Hibernate 6.6.5). lambdaQL does not make these queries yet. The test fails if Hibernate's SQL changes.

**SQL by database (planned)** — Hibernate's translation of the planned HQL, recorded by `PlannedSqlTest`; lambdaQL does not make this query yet. From `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql), example `08-function`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where fn_discount(o1_0.price)>10
```

Function names come out in lower case (`fn_discount`) unless the function is registered (see Registration below).

`!!` is only for the compiler (the function returns `Double?`). lambdaQL skips it (rule FN6).

### How lambdaQL renders it

lambdaQL **always** renders a DB function as:

```sql
function('<NAME>' as <Type>, arg1, arg2, ...)
```

`<Type>` comes from the declared return type. Only these types are allowed:

| Allowed return types |
|---|
| `Double`, `Integer`, `Long`, `BigDecimal`, `String`, `LocalDate`, `LocalDateTime`, `Boolean` |

Kotlin `Double?`, `Int?`, `Long?`, `Boolean?` and the non-null forms map to these names.
Generic returns (`fun <X> nvl(a: X, b: X): X`), entities, collections and `Unit` are rejected at analysis time.

### Registration (optional)

Registration is not needed for the `function('FN_DISCOUNT' as Double, ...)` form (checked on the three databases).
If you register your function interfaces, Hibernate also keeps the name case and checks the argument count and types.

lambdaQL plans a `FunctionContributor` (found by ServiceLoader). You list the interfaces in a Hibernate property:

```properties
# plain Hibernate
lambdaql.functions.classes=com.acme.OrderFns,com.acme.CustomerFns

# Spring Boot
spring.jpa.properties.lambdaql.functions.classes=com.acme.OrderFns
```

If the property is not set, nothing is registered. Package scanning is not in v0.1.

### Mixed-case names need registration

A function name made with quotes and mixed case (`"SQ_Fn_Mixed"`) fails without registration (the name becomes lower case on H2 and Oracle).
Split the key and the SQL name, and register the interface:

```kotlin
@JvmStatic @SqlFunction(key = "SQ_Fn_Mixed", sql = "\"SQ_Fn_Mixed\"")
fun mixed(v: Double?): Double? = throw DbOnlyException()
```

If such a function is not registered, lambdaQL fails on the first use.

---

## 8.8 Call a DB function alone

Use `qb.call { ... }` with a **lambda without arguments**:

```kotlin
val v: Double? = qb.call { OrderFns.discount(price) }.execute()
```

**SQL by database (planned)** — Hibernate's translation of the planned HQL, recorded by `PlannedSqlTest`; lambdaQL does not make this query yet. From `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql), example `08-function-alone`:

```sql
-- H2, MariaDB, Oracle (same)
select fn_discount(?)
```

This runs as `select fn_discount(?)` with no FROM. Oracle accepts that only from 23ai on; on Oracle, lambdaQL uses a JDBC function call instead (see the table below).

There is no conflict with `qb.call(OrderProcs::class.java) { ... }`, because the number of arguments is different.

How lambdaQL runs it (rule PR6):

| Database | Path |
|---|---|
| Oracle | a JDBC function call `{? = call FN(?)}`. This also works for functions that do DML and for functions that return a cursor. |
| MariaDB, H2 | HQL `select function('FN' as T, :p1)` |

lambdaQL flushes pending changes first (as in PR9), so the function sees your latest changes.

---

## 8.9 Warning: functions on columns and indexes (SARGable)

lambdaQL tries to make SQL that **can use an index** (see [10. Rules and limits](10-rules-and-limits.md#102-sargable-rule-index-friendly-sql)).
A function around a column usually **cannot** use a normal index:

```kotlin
.where { OrderFns.discount(it.price)!! > 10 }
// → function('FN_DISCOUNT' as Double, o.price) > 10   -- index on price is not used
```

**SQL by database (planned)** — Hibernate's translation of the planned HQL, recorded by `PlannedSqlTest`; lambdaQL does not make this query yet. From `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql), example `08-function`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where fn_discount(o1_0.price)>10
```

So lambdaQL logs a **WARN** for this form. In strict mode, it is an error (rules PR8, FN7).

| Case | MariaDB | Oracle |
|---|---|---|
| function on a column: `fn(o.price) > 10` | WARN (a stored function cannot be used in an index) | WARN, **unless** the function is `DETERMINISTIC` **and** a function-based index exists for the same expression |
| function only on values: `o.price > fn(rate)` | WARN if the function is `NOT DETERMINISTIC` (the default!). Then MariaDB scans the full index. | no warning (index range scan) |

**Advice:**

- Put the function on the value side when you can: `it.price!! > OrderFns.discount(base)!!`.
- On MariaDB, declare your function `DETERMINISTIC` if it is.
- On Oracle, a `DETERMINISTIC` function plus a function-based index removes the warning.

---

## 8.10 Function rules FN1 to FN11 (summary)

| # | Rule |
|---|---|
| FN1 | `@SqlFunction` is a function annotation. Empty value: function name in UPPER_SNAKE. |
| FN2 | lambdaQL finds the called function and reads its annotation. In Kotlin it skips the `Companion` receiver. The body must only throw (`= throw ...`). A function with a real body is an error, because the application and the database could give different values. |
| FN3 | All overloads with the same name must map to the same DB function. Otherwise: error. (A Kotlin `Double?` argument picks the `Double?` overload; a Java entity getter seen from Kotlin can pick a primitive `double` overload.) |
| FN4 | A call without the annotation, or a call lambdaQL cannot identify, is an error. It is never skipped silently. |
| FN5 | Arguments follow the value rules of [3. Conditions](03-where.md). Even an argument made only from captured values is bound and evaluated **in the database**. |
| FN6 | `!!` and boxing calls are skipped. The return type comes from the declared function. |
| FN7 | A function on a column gives a WARN (see [8.9](#89-warning-functions-on-columns-and-indexes-sargable)). |
| FN8 | A name equal to a Hibernate built-in function (`COALESCE`, `ROUND`, `SYSDATE`, ...) is an error. Hibernate would silently use the built-in. Use a prefix or a schema name (`PUBLIC.SQ_FN`). |
| FN9 | Kotlin: default arguments, `inline` and `vararg` are errors. |
| FN10 | v0.1 has scalar functions only. User aggregate and window functions are planned for v0.2. A function without arguments renders as `fn()`. |
| FN11 | Known DB differences: on MariaDB, a function that returns VARCHAR can have a collation conflict with a string literal. The scale of `BigDecimal` results differs (MariaDB and Oracle 0, H2 1). Boolean results need Oracle 23ai or later. |

---

## 8.11 Database support

| Feature | H2 2.3 | MariaDB 10.11 | Oracle 23ai |
|---|---|---|---|
| IN parameters | yes | yes | yes |
| OUT / INOUT | **no** (H2 limit) | yes | yes |
| One result set | yes (`ResultSet` function) | yes (`SELECT` in the procedure) | yes (`SYS_REFCURSOR` OUT) |
| Many result sets | no | yes | yes (many cursor OUT parameters) |
| OUT values and result sets together | no | yes | yes |
| Package names (`PKG.PROC`) | no | no (needs `sql_mode=ORACLE`) | yes |
| DB functions in queries | yes | yes | yes |
| Boolean parameters / results | – | yes | yes (23ai or later) |

Not planned for v0.1: a string builder for dynamic procedure names (v0.2 candidate), Oracle package overloads, table-valued functions, paging of procedure results.

Errors from this chapter are `LambdaQLException` subclasses with a `[LQL-xxxx]` code. See [11. Errors and languages](../11-errors-and-languages.md).

---

[Kotlin track](README.md) | Previous: [7. Update, insert, delete](07-dml.md) | Next: [9. Kotlin notes](09-kotlin-notes.md) | [Java version of this page](../java/08-procedures-functions.md)
