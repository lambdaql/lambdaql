# 8. Stored procedures and DB functions (Kotlin)

> [!NOTE]
> **Status**
> - **Implemented** (decisions D30 and D31, milestone M11, Hibernate 6.6.5) and tested with **Java** lambdas:
>   DB functions in queries (`@SqlFunction`) on H2 2.3, MariaDB 10.11 and Oracle 23.26 ([`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java));
>   procedure calls (`@SqlProcedure`, `@InOut`, `qb.call`, `qb.exec`) on MariaDB and Oracle, and a DB function called alone on all three databases ([`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java)).
>   lambdaQL also reads the Kotlin forms: a `companion object` function with `@JvmStatic`, and a Kotlin data class as a result. There are no Kotlin tests for this chapter yet.
> - **Not implemented yet:** the SARGable warnings for functions on columns (rules PR8, FN7), the check of parameter **types** and **names** against the database (part of rule PR2, and its WARN), and the detection of Oracle package overloads.
> - The Kotlin code in this chapter shows the tested Java calls in Kotlin syntax. The tested Java code is in [the Java version of this page](../java/08-procedures-functions.md). See the [track README](README.md) for the full status.

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
- `@SqlProcedure` also has `schema` and `pkg` (the Oracle package): `@SqlProcedure(value = "CALC", schema = "LQL", pkg = "SQ_PKG")`.
- Use a **nullable return type** (`Double?`). If the procedure returns NULL into a non-null type, lambdaQL throws `IllegalStateException` with the code `LQL-4039`.
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

- `exec` with a procedure that has OUT parameters is an error (`LQL-4037`, tested in Java). This stops OUT values from being lost by mistake.
- A call of a function without `@SqlProcedure` is an error (`LQL-3060`, tested in Java).
- A call that uses the **default arguments** of a stub function (`fun calc(id: Long, rate: Double = 1.0)`) is an error (`LQL-3062`, rule PR11). Kotlin would fill in the Kotlin default, not the DB default.
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
- If the numbers do not match, lambdaQL throws an error (`LQL-4038`).
- Mixed positions like IN, OUT, IN, INOUT are fine. lambdaQL reads the positions from the DB metadata.

### INOUT

Mark the parameter with `@InOut`. The value you pass goes **in**. The value that comes **out** is a property of the returned data class.

```kotlin
@SqlProcedure("SP_ADJUST") fun adjust(id: Long, @InOut balance: Int?): AdjOut

val a = qb.call(OrderProcs::class.java) { it.adjust(id, 100) }.execute()
// a.balance is the new balance
```

`@InOut` must be on exactly the arguments that are INOUT in the database. Otherwise the first call fails (`LQL-4036`).

### Result set element type `T`

| `T` is | How lambdaQL reads it |
|---|---|
| an entity | Hibernate result class (`StoredProcedureQuery`) |
| a Kotlin data class or a Java record | JDBC `CallableStatement`, columns matched **by label**: `_` is removed and case is ignored, so `customer_id` matches `customerId` (tested in Java with a record) |
| one value (`Long?`, `String?`, ...) | the first column of each row |

A normal class is not a row type here. Use a data class.
lambdaQL never maps properties by position, because MariaDB and Oracle return columns of `SELECT *` in different orders.
It converts common types (for example a number to `Long`/`Int`, a timestamp to `LocalDateTime`).

Limits:

- Paging a procedure result is not possible.
- Oracle implicit results (`DBMS_SQL.RETURN_RESULT`) are not supported. Use a `SYS_REFCURSOR` OUT parameter.

### Transactions (rule PR9)

- DML inside a procedure is part of the calling transaction. It rolls back with it.
- Before the call, lambdaQL flushes the persistence context, but only if a transaction is active and there are changes.
- `QueryBuilder(EntityManagerFactory)` runs the call in its own transaction. A `QueryBuilder` with an `EntityManager` (also Spring's shared one) uses the caller's transaction. Without one, the call is autocommit.
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

What lambdaQL checks on the first call (from JDBC metadata `getProcedureColumns`). The result is cached per procedure name:

| Check | Found? |
|---|---|
| the procedure exists | yes, error `LQL-4035` |
| number of IN and INOUT parameters = number of stub arguments | yes, error `LQL-4036` (tested in Java) |
| INOUT parameters = arguments with `@InOut` | yes, error `LQL-4036` |
| number of OUT values and result sets = parts of the return type | yes, error `LQL-4038` |
| type | **not implemented yet** |
| two parameters **of the same type** swapped | **not implemented yet** (planned: only if parameter names are available) |

Example of the dangerous case:

```kotlin
// Database: SP_TRANSFER(p_to NUMBER, p_from NUMBER)
@SqlProcedure("SP_TRANSFER") fun transfer(from: Long, to: Long)   // swapped! same types
```

The count, modes and types all match. Only the names can show the mistake.
lambdaQL does **not** compare the names yet. The plan: compare names when they are available (Kotlin `-java-parameters` or kotlin-reflect, or `@Param`), ignore the `p_` prefix and case, and log a WARN when names are not available and two neighbor parameters have the same type.

**Advice:** check the order of the stub arguments by hand. Compile with `-java-parameters` (see [Kotlin compiler settings](02-getting-started.md#kotlin-compiler-settings)) and use the same names as the database, so the planned check can find mistakes.

Real name binding on Oracle (`pass_procedure_paramater_names=true` together with the stub parameter names) is planned too.

---

## 8.5 Procedure rules PR1 to PR15 (summary)

| # | Rule | Status |
|---|---|---|
| PR1 | Name: the annotation value as is. Empty value: function name in UPPER_SNAKE. Package or schema names (`SQ_PKG.CALC`, `LQL.SQ_CALC`) work. You can also write `@SqlProcedure(schema = ..., pkg = ..., value = ...)` to avoid confusion. | Implemented. Schema and package names are not covered by a test |
| PR2 | Arguments are bound **by position**. On the first call, the count of IN/INOUT parameters and the `@InOut` positions are checked against DB metadata. Type and name checks are planned (see [8.4](#84-warning-argument-order)). | Count and `@InOut` check implemented (tested in Java). Type and name checks, and the detection of Oracle package overloads: not implemented yet |
| PR3 | One OUT → return value. Many OUT/INOUT → data class or record. `@InOut` marks INOUT parameters. | Implemented (tested in Java) |
| PR4 | One result set → `List<T>`. Entities through Hibernate; data classes and records through JDBC with label matching. No paging. | Implemented (tested in Java with a record) |
| PR5 | Many result sets → `List` properties of a data class, in order. Oracle implicit results are not supported. | Implemented, not covered by a test |
| PR6 | A DB function called alone uses a different path per database (see [8.8](#88-call-a-db-function-alone)). | Implemented (tested in Java) |
| PR7 | DB functions in queries: see [8.6](#86-declare-db-functions) and rules FN1 to FN11. | Implemented (tested in Java) |
| PR8 | A DB function on a column in a condition gives a WARN, because the index cannot be used (see [8.9](#89-warning-functions-on-columns-and-indexes-sargable)). | Not implemented yet |
| PR9 | The procedure runs in the calling transaction. lambdaQL flushes before the call when needed. Loaded entities can be stale after it. | Implemented |
| PR10 | No common affected-row count. Use an OUT parameter. | – |
| PR11 | `null` IN values are fine. DB parameter defaults cannot be used. Kotlin default arguments on stub functions are an error (`LQL-3062`). | Implemented |
| PR12 | The lambda is one stub call. Arguments are captured values, constants or calculations of them. Named arguments only with simple values. `exec` with OUT parameters is an error. | Implemented (tested in Java). Named arguments are not covered by a test |
| PR13 | Non-null `Long`/`Double` and nullable `Long?`/`Double?` both work. Boolean parameters work on MariaDB and Oracle 23ai or later. | Not covered by a test |
| PR14 | Each lambda is analyzed once. Procedure metadata is read once per name. | Implemented |
| PR15 | H2 has no OUT, INOUT, many result sets or packages. Test procedures on MariaDB or Oracle (for example with Testcontainers). | The lambdaQL tests follow this |

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

If you call `OrderFns.discount(...)` **outside** a lambdaQL lambda, it throws `DbOnlyException` (`LQL-4034`). This is on purpose.

`@SqlFunction` has two values:

| Value | Meaning |
|---|---|
| `value` | The function name in the query. Empty: the function name in UPPER_SNAKE (`isVip` → `IS_VIP`). A schema name is allowed (`PUBLIC.SQ_FN`) |
| `sql` | The SQL name, when it is not the same as `value`. Such a function must be registered (see [Registration](#registration-optional)) |

The Java test declares its functions as Java interface static methods (see [the Java version of this page](../java/08-procedures-functions.md#86-declare-db-functions)). A Kotlin `companion object` function is read the same way, but it is not covered by a test yet.

Why `@JvmStatic`? Java code can then call `OrderFns.discount(...)` as a static function, and the function looks the same for Java and Kotlin callers.
lambdaQL reads the annotation on the `companion object` function.

Other ways to declare:

| Declaration | Status |
|---|---|
| Interface `companion object` + `@JvmStatic` | **recommended** (implemented, not covered by a test yet) |
| Kotlin `object` + `@JvmStatic` | designed, not covered by a test |
| Java interface static method (called from Kotlin) | supported (the Java form is tested) |
| Kotlin top-level function | designed, **not recommended**: if you forget the import, a standard library function with the same name (for example `maxOf`) can be used silently |
| interface default function + a singleton constant | designed, not covered by a test |
| your repository implements the function interface (mixin) | rejected |
| functional interface constant (`DISCOUNT.apply(x)`), function type property | rejected |
| extension function | rejected in v0.1 (KC11) |
| `@SqlFunction` with default arguments, `inline`, or `vararg` | rejected (FN9: `LQL-3056` for default arguments, `LQL-3057` for `vararg`) |

Call the function **with the interface name** (`OrderFns.discount`). An import of the function works, but a member function with the same name can hide it.

---

## 8.7 Use DB functions in queries

Call the function directly. No context argument is needed. It works in `where`, `select`, `orderBy` and `set` (tested with Java lambdas in [`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java)), and also in `groupBy`, `having` and subqueries (designed, not covered by a test).

```kotlin
qb.from(Order::class.java).where { OrderFns.discount(it.price)!! > 10 }.toList()
// → where function('FN_DISCOUNT' as Double, e0.price) > 10

qb.from(Order::class.java).where { OrderFns.isVip(it.customer?.id)!! }.toList()     // a Boolean function alone: ... = true

qb.from(Order::class.java).where { it.price!! > OrderFns.discount(base)!! }.toList()  // column side stays clean

qb.from(Order::class.java).orderBy { o, by -> by.desc(OrderFns.discount(o.price)) }.toList()

qb.from(Order::class.java)
    .select { o -> OrderView(o.id, o.customer?.name, OrderFns.discount(o.price)) }
    .toList()

qb.update(Order::class.java).set { it.price = OrderFns.discount(it.price) }.all().execute()
```

The Java test checks the same forms with the name `SQ_FN_DISCOUNT`, for example `select e0 from Order e0 where function('SQ_FN_DISCOUNT' as Double, e0.price) > 100.0`. The test runs each query and compares the rows with the same computation in Java.

**About the SQL blocks.** `…` is the shortened select list (all entity columns), and `?` is a bind parameter. [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java) gives an HQL of the same form (with the name `FN_DISCOUNT`) to Hibernate directly and records the SQL on H2 2.3, MariaDB 10.11 and Oracle 23.26 (Hibernate 6.6.5). The test fails if Hibernate's SQL changes.

**SQL by database (Hibernate's SQL for this HQL form)** — recorded by `PlannedSqlTest`. From `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql), example `08-function`:

```sql
-- H2, MariaDB, Oracle (same)
-- HQL: select o from Order o where function('FN_DISCOUNT' as Double, o.price) > 10
select …
from orders o1_0
where FN_DISCOUNT(o1_0.price)>10
```

The test setup has the setting `lambdaql.functions.classes`. With it, Hibernate keeps the case of the name (`FN_DISCOUNT`), also for a function that is not registered (seen on all three databases; the reason is not known yet). Without any registration, Hibernate can write the name in lower case. An unquoted name works either way.

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

lambdaQL has a Hibernate `FunctionContributor`, `org.lambdaql.hibernate.LambdaQlFunctionContributor` (found by ServiceLoader). You list the interfaces in a Hibernate property:

```properties
# plain Hibernate
lambdaql.functions.classes=com.acme.OrderFns,com.acme.CustomerFns

# Spring Boot
spring.jpa.properties.lambdaql.functions.classes=com.acme.OrderFns
```

If the property is not set, nothing is registered. Package scanning is not in v0.1.

### A different SQL name needs registration

`value` is the name in the query, and `sql` is the name in SQL. When they differ, the function must be registered.
This is useful for a function name made with quotes and mixed case (`"SQ_Fn_Mixed"`): without registration the name can become lower case on H2 and Oracle.

```kotlin
@JvmStatic @SqlFunction(value = "SQ_Fn_Mixed", sql = "\"SQ_Fn_Mixed\"")
fun mixed(v: Double?): Double? = throw DbOnlyException()
```

If such a function is not registered, lambdaQL fails when it reads the lambda (`LQL-3058`). The Java test checks a registered function (`value = "SQ_TAX"`, `sql = "SQ_FN_DISCOUNT"`, region `registered`).

---

## 8.8 Call a DB function alone

Use `qb.call { ... }` with a **lambda without arguments**:

```kotlin
val v: Double? = qb.call { OrderFns.discount(price) }.execute()
```

The Java form is tested on H2, MariaDB and Oracle (`ProcedureTest`, region `function`). The lambda must be one `@SqlFunction` call (`LQL-3063`).

**SQL by database (Hibernate's SQL for this HQL form)** — recorded by `PlannedSqlTest` with the name `FN_DISCOUNT`. From `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql), example `08-function-alone`:

```sql
-- H2, MariaDB, Oracle (same)
-- HQL: select function('FN_DISCOUNT' as Double, :p1)
select FN_DISCOUNT(?)
```

This runs as `select FN_DISCOUNT(?)` with no FROM. Oracle accepts that only from 23ai on; on Oracle, lambdaQL uses a JDBC function call instead (see the table below).

There is no conflict with `qb.call(OrderProcs::class.java) { ... }`, because the number of arguments is different.

How lambdaQL runs it (rule PR6):

| Database | Path |
|---|---|
| Oracle | a JDBC function call `{? = call FN(?)}`. This also works for functions that do DML and for functions that return a cursor. |
| MariaDB, H2 | HQL `select function('FN' as T, :p1)` |

lambdaQL flushes pending changes first (as in PR9), so the function sees your latest changes.

---

## 8.9 Warning: functions on columns and indexes (SARGable)

> [!NOTE]
> The warnings in this section are **not implemented yet** (rules PR8, FN7). Today lambdaQL writes these queries without a warning.

lambdaQL tries to make SQL that **can use an index** (see [10. Rules and limits](10-rules-and-limits.md#102-sargable-rule-index-friendly-sql)).
A function around a column usually **cannot** use a normal index:

```kotlin
.where { OrderFns.discount(it.price)!! > 10 }
// → function('FN_DISCOUNT' as Double, e0.price) > 10   -- index on price is not used
```

**SQL by database (Hibernate's SQL for this HQL form)** — recorded by `PlannedSqlTest`. From `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql), example `08-function`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where FN_DISCOUNT(o1_0.price)>10
```

So lambdaQL will log a **WARN** for this form. In strict mode, it is an error (rules PR8, FN7).

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

| # | Rule | Status |
|---|---|---|
| FN1 | `@SqlFunction` is a function annotation. Empty value: function name in UPPER_SNAKE. | Implemented |
| FN2 | lambdaQL finds the called function and reads its annotation. In Kotlin it skips the `Companion` receiver. The body must only throw (`= throw ...`). A function with a real body is an error (`LQL-3052`), because the application and the database could give different values. | Implemented (tested in Java) |
| FN3 | All overloads with the same name must map to the same DB function. Otherwise: error (`LQL-3053`). (A Kotlin `Double?` argument picks the `Double?` overload; a Java entity getter seen from Kotlin can pick a primitive `double` overload.) | Implemented |
| FN4 | A call without the annotation, or a call lambdaQL cannot identify, is an error. It is never skipped silently. | Implemented |
| FN5 | Arguments follow the value rules of [3. Conditions](03-where.md). Even an argument made only from captured values is bound and evaluated **in the database**. | Implemented (tested in Java) |
| FN6 | `!!` and boxing calls are skipped. The return type comes from the declared function. It must map to `Double`, `Integer`, `Long`, `BigDecimal`, `String`, `LocalDate`, `LocalDateTime` or `Boolean` (`LQL-3055`). | Implemented (tested in Java) |
| FN7 | A function on a column gives a WARN (see [8.9](#89-warning-functions-on-columns-and-indexes-sargable)). | Not implemented yet |
| FN8 | A name equal to a Hibernate built-in function (`COALESCE`, `ROUND`, `SYSDATE`, ...) is an error (`LQL-3054`). Hibernate would silently use the built-in. Use a prefix or a schema name (`PUBLIC.SQ_FN`). | Implemented (tested in Java) |
| FN9 | Kotlin: default arguments (`LQL-3056`), `vararg` (`LQL-3057`) and `inline` are errors. | Default arguments and `vararg`: implemented. `inline`: designed |
| FN10 | v0.1 has scalar functions only. User aggregate and window functions are planned for v0.2. A function without arguments renders as `fn()`. | Scalar functions implemented |
| FN11 | Known DB differences: on MariaDB, a function that returns VARCHAR can have a collation conflict with a string literal. The scale of `BigDecimal` results differs (MariaDB and Oracle 0, H2 1). Boolean results need Oracle 23ai or later. | Measured with prototypes |

The rule checks (`LQL-4034`, `LQL-3052`, `LQL-3054`, `LQL-3058`, `LQL-3055`) are tested in Java ([`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java), region `rules`, see [the Java version of this page](../java/08-procedures-functions.md#810-function-rules-fn1-to-fn11-summary)).

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
| DB functions in queries | yes (tested) | yes (tested) | yes (tested) |
| A DB function called alone | yes (tested) | yes (tested) | yes (tested) |
| Procedure calls in lambdaQL | – | yes (tested) | yes (tested) |
| Boolean parameters / results | – | yes | yes (23ai or later) |

Not planned for v0.1: a string builder for dynamic procedure names (v0.2 candidate), Oracle package overloads, table-valued functions, paging of procedure results.

Errors from this chapter are `LambdaQLException` (or its subclass) with a `[LQL-xxxx]` code: `LQL-3052` to `LQL-3063` when lambdaQL reads the lambda, `LQL-4034` to `LQL-4039` when the call runs. See [11. Errors and languages](../11-errors-and-languages.md).

---

[Kotlin track](README.md) | Previous: [7. Update, insert, delete](07-dml.md) | Next: [9. Kotlin notes](09-kotlin-notes.md) | [Java version of this page](../java/08-procedures-functions.md)
