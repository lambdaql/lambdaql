<a id="8-stored-procedures-and-db-functions-kotlin"></a>
# 8. Stored procedures and DB functions (Kotlin)

> [!IMPORTANT]
> This page is older than the Korean manual. See the [Korean page](../../ko/kotlin/08-procedures-functions.md) for the latest rules.

[Kotlin track](README.md) | [Manual home](../README.md) | Previous: [7. Update, insert, delete](07-dml.md) | Next: [9. Kotlin notes](09-kotlin-notes.md) | [Java version of this page](../java/08-procedures-functions.md)

Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md)

---

This chapter has two parts.

| Part | What | Annotation | How you call it |
|---|---|---|---|
| [8.1 to 8.5](#81-declare-procedures) | **Stored procedures** | `@SqlProcedure` on an abstract interface function | `qb.call(OrderProcs::class.java) { it.calc(id, rate) }.execute()` |
| [8.6 to 8.10](#86-declare-db-functions) | **DB functions** (scalar) | `@SqlFunction` on a `@JvmStatic` function in the interface `companion object` | directly inside any lambda: `where { OrderFns.discount(it.price)!! > 10 }` |

In both cases, you write the signature **once** in a Kotlin interface.
lambdaQL **never runs** these functions. It reads the call in the lambda bytecode and calls the database.

<a id="contents"></a>
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

<a id="81-declare-procedures"></a>
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
  (The names `@Procedure` and `@Function` were not used. `@Procedure` is also a Spring Data JPA annotation, and `@Function` clashes with Kotlin's `kotlin.Function`.)
- If the value is empty, the name comes from the function name: `calcTotal` → `CALC_TOTAL`.
- `@SqlProcedure` also has `schema` and `pkg` (the Oracle package): `@SqlProcedure(value = "CALC", schema = "LQL", pkg = "SQ_PKG")`.
- Use a **nullable return type** (`Double?`). If the procedure returns NULL into a non-null type, lambdaQL throws `IllegalStateException` with the code `LQL-4039`.
- The interface and the result data classes are a copy of the DB signature. This is the price for type safety. lambdaQL checks part of it against the database (rule PR2).
- You can put procedures (abstract functions) and DB functions (in the `companion object`) in **the same interface**.

---

<a id="82-call-procedures"></a>
## 8.2 Call procedures

Use `qb.call(Interface::class.java) { it.function(args) }`. Then call `execute()`.

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
- `run` was not chosen. In Kotlin, if you leave out an argument, `run { }` silently becomes the standard library `run`.

<a id="what-the-lambda-may-contain-rule-pr12"></a>
### What the lambda may contain (rule PR12)

The lambda body is **one call of a stub function**. Nothing else.

| Allowed | Not allowed |
|---|---|
| `{ it.calc(id, rate) }` | logging, two calls, `if`, nested calls |
| arguments: captured values, constants, calculations of them (`rate * 2`) | entity paths (there is no entity here) |
| named arguments with **simple values** (`calc(rate = rate, id = id)`) | named arguments with calculations (`calc(rate = r * 2, id = id)`), see below |

For named arguments with a calculation, Kotlin makes hidden local variables. lambdaQL rejects local variables in v0.1.
In that case, use positional arguments: `it.calc(id, r * 2)`.

Other points:

- `exec` with a procedure that has OUT parameters is an error (`LQL-4037`, tested in Java). This keeps OUT values from being lost by mistake.
- A call of a function without `@SqlProcedure` is an error (`LQL-3060`, tested in Java).
- A call that uses the **default arguments** of a stub function (`fun calc(id: Long, rate: Double = 1.0)`) is an error (`LQL-3062`, rule PR11). The reason is that Kotlin fills in the Kotlin default, not the DB default.
- Database default values for parameters cannot be used. Pass every IN parameter.
- `null` as an IN value is fine.
- Non-null `Long`/`Double` parameters and nullable `Long?`/`Double?` parameters both work (rule PR13).

---

<a id="83-out-inout-and-result-sets"></a>
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
- **`List` properties** ← result sets, in order (Oracle: order of the cursor OUT parameters, MariaDB: order of the returned result sets).
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

<a id="result-set-element-type-t"></a>
### Result set element type `T`

| `T` is | How lambdaQL reads it |
|---|---|
| an entity | Hibernate result class (`StoredProcedureQuery`) |
| a Kotlin data class or a Java record | JDBC `CallableStatement`, columns matched **by label**. `_` is removed and case is ignored, so `customer_id` matches `customerId` (tested in Java with a record) |
| one value (`Long?`, `String?`, ...) | the first column of each row |

A normal class cannot be a row type here. Use a data class.
MariaDB and Oracle return the columns of `SELECT *` in different orders, so lambdaQL never maps properties by absolute position.
It converts common types (for example a number to `Long`/`Int`, a timestamp to `LocalDateTime`).

Limits:

- Paging a procedure result is not possible.
- Oracle implicit results (`DBMS_SQL.RETURN_RESULT`) are not supported. Use a `SYS_REFCURSOR` OUT parameter.

<a id="transactions-rule-pr9"></a>
### Transactions (rule PR9)

- DML inside a procedure is part of the calling transaction. It rolls back with it.
- Before the call, lambdaQL flushes the persistence context. It flushes only when a transaction is active and there are changes.
- `QueryBuilder(EntityManagerFactory)` runs the call in its own transaction. A `QueryBuilder` with an `EntityManager` (including Spring's shared `EntityManager`) uses the caller's transaction. Without a transaction, the call is autocommit.
- After the call, loaded entities can be stale. Use `refresh` or `evict` on those entities.
- A `COMMIT` or DDL inside the procedure breaks the transaction. lambdaQL cannot stop this.

<a id="affected-rows-rule-pr10"></a>
### Affected rows (rule PR10)

There is no common "affected rows" result for procedures (Oracle returns -1).
If you need the number, return it as an OUT parameter.

---

<a id="84-warning-argument-order"></a>
## 8.4 Warning: argument order

> [!WARNING]
> **The order of the stub function parameters must be the same as the order in the database.**

lambdaQL binds arguments **by position** (rule PR2).
Hibernate 6.6.5 binds by position even when names are registered.
The MariaDB and H2 drivers do not support named parameters.
So if the order is wrong, you can get a **wrong result without any error**.

Kotlin named arguments (`calc(rate = rate, id = id)`) do **not** change this. The compiler puts the arguments back in the order of the stub function. Only the order of the stub function counts.

These are the checks lambdaQL makes on the first call, with JDBC metadata (`getProcedureColumns`). The result is cached per procedure name.

| Check | Found? |
|---|---|
| the procedure exists | yes, error `LQL-4035` |
| number of IN and INOUT parameters = number of stub arguments | yes, error `LQL-4036` (tested in Java) |
| INOUT parameters = arguments with `@InOut` | yes, error `LQL-4036` |
| number of OUT values and result sets = number of parts of the return type | yes, error `LQL-4038` |
| the kind of type (number, text, date/time, boolean, binary) | yes, error `LQL-4036` (tested in Java). Differences within one kind are not checked (see "Argument type check" below) |
| the same parameter names in another order | yes, error `LQL-4043` (tested in Java). Only when the stub argument names are known (see below) |
| an Oracle package procedure name with several overloads | yes, error `LQL-4044` (tested in Java on Oracle) |

Example of the dangerous case:

```kotlin
// Database: SP_TRANSFER(p_to NUMBER, p_from NUMBER)
@SqlProcedure("SP_TRANSFER") fun transfer(from: Long, to: Long)   // swapped! same types
```

The count, modes and types all match. Only the names can show the mistake.
So lambdaQL compares the names of the IN and INOUT arguments on the first call.

- The stub argument names come from the class file. The names are there only when you compile with `-java-parameters` (see [Kotlin compiler settings](02-getting-started.md#kotlin-compiler-settings)). `@org.lambdaql.DbParam("p_to")` on a stub argument gives the database name directly.
- From the database names, lambdaQL removes a prefix that **all** names share and that ends with `_` (`p_`, `in_`, `v_` and so on). No fixed prefix is assumed. Then it ignores case and `_`.
- If the names are the same and only the order differs, a `LambdaQLException` `LQL-4043` is thrown before the call.
- If the names differ (the stub follows another naming rule), it is not an error. Add `@DbParam` so the check works.
- If the class file has no names and two neighbouring arguments have the same type, lambdaQL logs `LQL-5004` at `INFO` level, once per procedure.
- Types are checked by kind only (see "Argument type check" below). Two arguments of the same kind in swapped order can be found only by their names.

The same stubs are tested in Java ([`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java), regions `names` and `name-check`, MariaDB and Oracle; see [the Java version of this page](../java/08-procedures-functions.md#84-warning-argument-order)).
Kotlin needs `-java-parameters` to keep the names. The following is a design example.

```kotlin
// Database: SQ_PROC_DIFF(p_to, p_from)
interface DiffProcs {
    @SqlProcedure("SQ_PROC_DIFF")
    fun diff(to: Int?, from: Int?): Int?                             // same order as the database: runs

    @SqlProcedure("SQ_PROC_DIFF")
    fun swapped(from: Int?, to: Int?): Int?                          // same names, other order: LQL-4043

    @SqlProcedure("SQ_PROC_DIFF")
    fun named(@DbParam("p_to") target: Int?, @DbParam("p_from") source: Int?): Int?   // another naming rule
}

qb.call(DiffProcs::class.java) { it.diff(10, 3) }.execute()       // 7
qb.call(DiffProcs::class.java) { it.swapped(3, 10) }.execute()    // LambdaQLException LQL-4043, before the call
```

**Advice:** compile with `-java-parameters` and use the same names as the database (the shared prefix may be left out), or add `@DbParam`. Then a swapped order is found.

**Argument type check.** On the first call, lambdaQL compares the kind of each argument's type with the kind of the DB parameter type.
There are five kinds: number, text, date/time, boolean and binary. If the kinds differ, lambdaQL stops with `LQL-4036` before the call.

- A boolean argument also matches a number parameter. MariaDB's `BOOLEAN` is `TINYINT(1)`, and Oracle before 23 has no `BOOLEAN` type.
- A side whose kind cannot be decided (enum, object type, cursor) is not checked.
- Differences within one kind (for example, an `Int?` argument and a `NUMBER(10,2)` parameter) are left to the driver's conversion.

A Java test ([`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java), region `type-check`, MariaDB and Oracle) checks that passing a string to a number parameter stops with `LQL-4036`.

**Oracle package overloads.** An Oracle package can hold several procedures with the same name.
lambdaQL cannot tell which one the stub means, so a call to such a name stops with `LQL-4044`. Calling one chosen overload is planned for later.

Tested in Java ([`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java), region `overload`, Oracle only). The following is a design example.

```kotlin
interface PkgProcs {
    @SqlProcedure(pkg = "SQ_PKG", value = "TWICE") fun twice(value: Double?): Double?
    @SqlProcedure(pkg = "SQ_PKG", value = "HALF")  fun half(value: Double?): Double?    // HALF has two overloads
}

qb.call(PkgProcs::class.java) { it.twice(2.5) }.execute()     // 5.0
qb.call(PkgProcs::class.java) { it.half(10.0) }.execute()     // LambdaQLException LQL-4044
```

<a id="when-the-metadata-cannot-be-read"></a>
**When the metadata cannot be read.** A database user may have no permission to read the dictionary views or `information_schema`.
If the metadata lookup itself fails (`SQLException`), lambdaQL does not stop the call.

- It logs `LQL-5005` at `INFO` level, once per procedure, and does not check the stub.
- It takes the argument layout from the stub: the IN and INOUT arguments in stub order, then the OUT parameters (in the order of the result parts), then on Oracle one `REF CURSOR` for each `List` part. So without metadata, the database declaration must put the OUT parameters after the IN parameters.
- The checks that need the metadata are skipped: the count and `@InOut` checks (`LQL-4036`) and the name order check (`LQL-4043`).
- If the lookup works but the procedure does not exist, the error is still `LQL-4035`.
- If only the Oracle overload lookup (`all_arguments`) fails, lambdaQL logs `LQL-5006` at `INFO` level and calls without the overload check.

This fallback is tested in Java on MariaDB and Oracle (`ProcedureTest`, test method `withoutMetadata`, which simulates the failure).

Real name binding on Oracle (`pass_procedure_paramater_names=true` together with the stub parameter names) is planned too.

---

<a id="85-procedure-rules-pr1-to-pr15-summary"></a>
## 8.5 Procedure rules PR1 to PR15 (summary)

| # | Rule | Status |
|---|---|---|
| PR1 | Name: the annotation value as is. Empty value: function name in UPPER_SNAKE. Package or schema names (`SQ_PKG.CALC`, `LQL.SQ_CALC`) work. To avoid confusion, you can also write `@SqlProcedure(schema = ..., pkg = ..., value = ...)`. | Implemented. Schema and package names are not covered by a test |
| PR2 | Arguments are bound **by position**. On the first call, the count of IN/INOUT parameters and the `@InOut` positions are checked against DB metadata. Names are also checked when they are known, and the same names in another order is an error. The kind of each argument type (number, text, date/time, boolean, binary) is also checked, and a mismatch is an error (`LQL-4036`) (see [8.4](#84-warning-argument-order)). If the metadata cannot be read, the checks are skipped and the argument layout comes from the stub (see [8.4](#when-the-metadata-cannot-be-read)). | Count, `@InOut` and name checks: implemented (tested in Java). Detection of Oracle package overloads: implemented (tested on Oracle). Type checks: implemented (tested in Java). The fallback without metadata (`LQL-5005`, `LQL-5006`): implemented (tested in Java on MariaDB and Oracle). |
| PR3 | One OUT → return value. Many OUT/INOUT → data class or record. `@InOut` marks INOUT parameters. | Implemented (tested in Java) |
| PR4 | One result set → `List<T>`. Entities through Hibernate, data classes and records through JDBC with label matching. No paging. | Implemented (tested in Java with a record) |
| PR5 | Many result sets → `List` properties of a data class, in order. Oracle implicit results are not supported. | Implemented, not covered by a test |
| PR6 | A DB function called alone uses a different path per database (see [8.8](#88-call-a-db-function-alone)). | Implemented (tested in Java) |
| PR7 | DB functions in queries: see [8.6](#86-declare-db-functions) and rules FN1 to FN11. | Implemented (tested in Java) |
| PR8 | A DB function on a column in a condition logs a WARN, because the index cannot be used (see [8.9](#89-warning-functions-on-columns-and-indexes-sargable)). The code is `LQL-5001`. | Implemented (tested with Java lambdas). In `SargableMode.STRICT` it is an error (`LQL-3066`, D50) |
| PR9 | The procedure runs in the calling transaction. lambdaQL flushes before the call when needed. After the call, loaded entities can be stale. | Implemented |
| PR10 | No common affected-row count. Use an OUT parameter. | – |
| PR11 | `null` IN values are fine. DB parameter defaults cannot be used. Kotlin default arguments on stub functions are an error (`LQL-3062`). | Implemented |
| PR12 | The lambda is one stub call. Arguments are captured values, constants or calculations of them. Named arguments only with simple values. `exec` with OUT parameters is an error. | Implemented (tested in Java). Named arguments are not covered by a test |
| PR13 | Non-null `Long`/`Double` and nullable `Long?`/`Double?` both work. Boolean parameters work on MariaDB and Oracle 23ai or later. | Not covered by a test |
| PR14 | Each lambda is analyzed once. Procedure metadata is read once per name. | Implemented |
| PR15 | H2 has no OUT, INOUT, many result sets or packages. Test procedures on MariaDB or Oracle (for example with Testcontainers). | The lambdaQL tests follow this |

---

<a id="86-declare-db-functions"></a>
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

If you call `OrderFns.discount(...)` **outside** a lambdaQL lambda, it throws `DbOnlyException` (`LQL-4034`). This is intended.

`@SqlFunction` has two values.

| Value | Meaning |
|---|---|
| `value` | The function name in the query. Empty: the function name in UPPER_SNAKE (`isVip` → `IS_VIP`). A schema name is allowed (`PUBLIC.SQ_FN`) |
| `sql` | The SQL name, when it differs from `value`. Such a function must be registered (see [Registration](#registration-optional)) |

The Java test declares its functions as static methods of a Java interface (see [the Java version of this page](../java/08-procedures-functions.md#86-declare-db-functions)). A Kotlin `companion object` function is read the same way, but it is not covered by a test yet.

Why `@JvmStatic`? With it, Java code can call `OrderFns.discount(...)` as a static function, and the function looks the same to Java and Kotlin callers.
lambdaQL reads the annotation on the `companion object` function.

Other ways to declare:

| Declaration | Status |
|---|---|
| Interface `companion object` + `@JvmStatic` | **recommended** (implemented, not covered by a test yet) |
| Kotlin `object` + `@JvmStatic` | designed, not covered by a test |
| Java interface static method (called from Kotlin) | supported (the Java form is tested) |
| Kotlin top-level function | designed, **not recommended**: if you forget the import, a standard library function with the same name (for example `maxOf`) can be used silently |
| interface default function + a singleton constant | designed, not covered by a test |
| the repository implements the function interface (mixin) | rejected |
| functional interface constant (`DISCOUNT.apply(x)`), function type property | rejected |
| extension function | rejected in v0.1 (KC11) |
| `@SqlFunction` with default arguments, `inline` or `vararg` | rejected (FN9: `LQL-3056` for default arguments, `LQL-3057` for `vararg`) |

Call the function **with the interface name** (`OrderFns.discount`). An import of the function works, but a member function with the same name can hide it.

---

<a id="87-use-db-functions-in-queries"></a>
## 8.7 Use DB functions in queries

Call the function directly. No context argument is needed.
It works in `where`, `select`, `orderBy` and `set` (tested with Java lambdas in [`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java)). It is also designed to work in `groupBy`, `having` and subqueries, but this is not covered by a test.

```kotlin
qb.from(Order::class.java).where { OrderFns.discount(it.price)!! > 10 }.toList()
// → where function('FN_DISCOUNT' as Double, e0.price) > 10

qb.from(Order::class.java).where { OrderFns.isVip(it.customer?.id)!! }.toList()     // a Boolean function alone: ... = true

qb.from(Order::class.java).where { it.price!! > OrderFns.discount(base)!! }.toList()  // the column side stays clean

qb.from(Order::class.java).orderBy { o, by -> by.desc(OrderFns.discount(o.price)) }.toList()

qb.from(Order::class.java)
    .select { o -> OrderView(o.id, o.customer?.name, OrderFns.discount(o.price)) }
    .toList()

qb.update(Order::class.java).set { it.price = OrderFns.discount(it.price) }.all().execute()
```

The Java test checks the same forms with the name `SQ_FN_DISCOUNT`, for example `select e0 from Order e0 where function('SQ_FN_DISCOUNT' as Double, e0.price) > 100.0`. The test runs each query and compares the result rows with the same calculation in Java.

**About the SQL blocks.** `…` is the shortened select list (all entity columns), and `?` is a bind parameter. [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java) gives an HQL of the same form (with the name `FN_DISCOUNT`) to Hibernate directly and records the SQL on H2 2.3, MariaDB 10.11 and Oracle 23.26 (Hibernate 6.6.5). The test fails if Hibernate's SQL changes.

**SQL by database (Hibernate's SQL for this HQL form)** — recorded by `PlannedSqlTest`. From `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql), example `08-function`:

```sql
-- H2, MariaDB, Oracle (same)
-- HQL: select o from Order o where function('FN_DISCOUNT' as Double, o.price) > 10
select …
from orders o1_0
where FN_DISCOUNT(o1_0.price)>10
```

The test setup has the setting `lambdaql.functions.classes`. With this setting, Hibernate keeps the case of the name (`FN_DISCOUNT`) even for a function that is not registered (seen on all three databases; the cause is not confirmed yet).
Without any registration, Hibernate can write the name in lower case. An unquoted name works either way.

`!!` is only for the compiler (the function returns `Double?`). lambdaQL skips it (rule FN6).

<a id="how-lambdaql-renders-it"></a>
### How lambdaQL renders it

lambdaQL **always** renders a DB function in this form.

```sql
function('<NAME>' as <Type>, arg1, arg2, ...)
```

`<Type>` comes from the declared return type. Only these types are allowed.

| Allowed return types |
|---|
| `Double`, `Integer`, `Long`, `BigDecimal`, `String`, `LocalDate`, `LocalDateTime`, `Boolean` |

Kotlin `Double?`, `Int?`, `Long?`, `Boolean?` and their non-null forms map to these names.
Generic returns (`fun <X> nvl(a: X, b: X): X`), entities, collections and `Unit` are rejected at analysis time.

<a id="registration-optional"></a>
### Registration (optional)

The `function('FN_DISCOUNT' as Double, ...)` form needs no registration (checked on the three databases).
If you register your function interfaces, Hibernate also keeps the case of the name and checks the argument count and types.

lambdaQL has a Hibernate `FunctionContributor`, `org.lambdaql.hibernate.LambdaQlFunctionContributor` (found by ServiceLoader). You list the interfaces in a Hibernate property.

```properties
# plain Hibernate
lambdaql.functions.classes=com.acme.OrderFns,com.acme.CustomerFns

# Spring Boot
spring.jpa.properties.lambdaql.functions.classes=com.acme.OrderFns
```

If the property is not set, nothing is registered. Package scanning is not in v0.1.

<a id="a-different-sql-name-needs-registration"></a>
### Registration for a different SQL name

`value` is the name in the query, and `sql` is the name in SQL. When the two names differ, the function must be registered.
This is useful for a function name made with quotes and mixed case (`"SQ_Fn_Mixed"`). Without registration, the name can become lower case on H2 and Oracle.

```kotlin
@JvmStatic @SqlFunction(value = "SQ_Fn_Mixed", sql = "\"SQ_Fn_Mixed\"")
fun mixed(v: Double?): Double? = throw DbOnlyException()
```

If such a function is not registered, lambdaQL fails when it reads the lambda (`LQL-3058`). The Java test checks a registered function (`value = "SQ_TAX"`, `sql = "SQ_FN_DISCOUNT"`, region `registered`).

---

<a id="88-call-a-db-function-alone"></a>
## 8.8 Call a DB function alone

Use `qb.call { ... }` with a **lambda without arguments**.

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

This query runs as `select FN_DISCOUNT(?)` with no FROM. Oracle accepts this only from 23ai on. On Oracle, lambdaQL uses a JDBC function call instead (see the table below).

The number of arguments is different, so there is no conflict with `qb.call(OrderProcs::class.java) { ... }`.

How lambdaQL runs it (rule PR6):

| Database | Path |
|---|---|
| Oracle | a JDBC function call `{? = call FN(?)}`. This also works for functions that do DML and for functions that return a cursor. |
| MariaDB, H2 | HQL `select function('FN' as T, :p1)` |

lambdaQL flushes pending changes first (as in PR9), so the function sees the latest changes.

---

<a id="89-warning-functions-on-columns-and-indexes-sargable"></a>
## 8.9 Warning: functions on columns and indexes (SARGable)

lambdaQL tries to make SQL that **can use an index** (see [10. Rules and limits](10-rules-and-limits.md#102-sargable-rule-index-friendly-sql)).
A function around a column usually **cannot** use a normal index.

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

So lambdaQL logs a **WARN** for this form.
The check runs once per `where` lambda, when the analysis result is cached. A condition without an `@SqlFunction` needs no database lookup.

| Case | Code | H2 | MariaDB | Oracle |
|---|---|---|---|---|
| ① A function wraps a column: `fn(o.price) > 10`, a Boolean function on a column, a function inside `IN` or `IS NULL` | `LQL-5001` (WARN) | WARN | WARN (a stored function cannot be used in an index) | WARN. **But** no log if `user_ind_expressions` has a function-based index with the same expression `FN(COLUMN)` on that table |
| ② A function on the value side, compared with a column: `o.price > fn(rate)` | `LQL-5007` (WARN) | not checked | WARN when `information_schema.routines.is_deterministic = 'NO'` (the default!). Then MariaDB scans the full index | not checked (Oracle uses a range scan either way) |

- For the Oracle index match, lambdaQL takes the table and column names from the Hibernate mapping. Only functions with one column argument are matched.
- If the metadata lookup fails (for example without permission), lambdaQL logs `LQL-5008` at `INFO` level. Case ① still warns, and case ② is not checked.

The same checks are tested in Java ([`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java), region `sargable`: H2 gives `LQL-5001`, MariaDB gives `LQL-5001` and `LQL-5007`, Oracle with a function-based index gives none). The following is a design example (`nondet` is an `@SqlFunction` like `discount`).

```kotlin
// the function wraps the column: an index on price cannot be used (LQL-5001 at WARN),
// unless Oracle has a function-based index on SQ_FN_DISCOUNT(price)
qb.from(Order::class.java).where { OrderFns.discount(it.price)!! > 100 }.toList()
// the function is on the value side: fine, unless MariaDB says NOT DETERMINISTIC (LQL-5007 at WARN)
qb.from(Order::class.java).where { it.price!! > OrderFns.nondet(base)!! }.toList()
```

**SARGable mode** (decision D50). `SargableMode` has three values.

- `OFF`: no checks and no metadata lookup.
- `WARN` (default): lambdaQL logs warnings.
- `STRICT`: in the cases where `WARN` would log a warning (`LQL-5001`, `LQL-5007`, `LQL-5012`), the analysis fails with `org.lambdaql.NonSargableException` (`LQL-3066`).

Set the mode per persistence unit with `SargablePolicy.register(emf, SargableMode.STRICT)`, and remove it with `SargablePolicy.unregister(emf)`. In Spring, use `lambdaql.sargable.mode=STRICT` (see [12](12-spring-data-jpa.md)).
The check runs once per lambda, when the analysis result goes into the shared cache. So register the mode before the first query.

**Arithmetic on a column** (decision D50).

- `+ - * /` with only one column and values (`it.cnt * 2 > n`) can be moved to the value side. lambdaQL logs `LQL-5012` at `WARN` level, and it is an error in `STRICT`.
- Arithmetic between columns (`it.price!! * it.cnt > n`) and `%` cannot be moved, so lambdaQL logs `LQL-5013` only at `DEBUG` level. `STRICT` also allows it.
- The moved form must have the same meaning. Divide by a decimal, as in `it.cnt > n / 2.0`. The integer division `n / 2` gives different rows with `>=`, `=`, `<` and with a negative `n`. If you multiplied by a negative number, turn the comparison around. The `LQL-5012` message explains this.
- Looking up whether an index exists, to log fewer warnings, was not adopted. Permissions and synonyms mix "no index" with "unknown", and arithmetic on a column is a rare form.

There is no Kotlin test for this. A Java test ([`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java), region `sargable-strict`) checks the `STRICT` mode.

**Advice:**

- Put the function on the value side when you can: `it.price!! > OrderFns.discount(base)!!`.
- On MariaDB, declare the function `DETERMINISTIC` if it really is deterministic.
- On Oracle, a function-based index on the same expression removes the warning (such an index needs a `DETERMINISTIC` function).

---

<a id="810-function-rules-fn1-to-fn11-summary"></a>
## 8.10 Function rules FN1 to FN11 (summary)

| # | Rule | Status |
|---|---|---|
| FN1 | `@SqlFunction` is a function annotation. Empty value: function name in UPPER_SNAKE. | Implemented |
| FN2 | lambdaQL finds the called function and reads its annotation. In Kotlin it skips the `Companion` receiver. The body must only throw (`= throw ...`). A function with a real body is an error (`LQL-3052`), because the application and the database could give different values. | Implemented (tested in Java) |
| FN3 | All overloads with the same name must map to the same DB function. Otherwise it is an error (`LQL-3053`). (A Kotlin `Double?` argument picks the `Double?` overload. A Java entity getter seen from Kotlin can pick a primitive `double` overload.) | Implemented |
| FN4 | A call without the annotation, or a call lambdaQL cannot identify, is an error. It is never skipped silently. | Implemented |
| FN5 | Arguments follow the value rules of [3. Conditions](03-where.md). Even an argument made only from captured values is bound and evaluated **in the database**. | Implemented (tested in Java) |
| FN6 | `!!` and boxing calls are skipped. The return type comes from the declared function. It must map to one of `Double`, `Integer`, `Long`, `BigDecimal`, `String`, `LocalDate`, `LocalDateTime` and `Boolean` (`LQL-3055`). | Implemented (tested in Java) |
| FN7 | A function on a column gives a WARN (see [8.9](#89-warning-functions-on-columns-and-indexes-sargable)). On MariaDB, a `NOT DETERMINISTIC` function on the value side also gives a WARN (`LQL-5007`). | Implemented (tested with Java lambdas). In `SargableMode.STRICT` it is an error (`LQL-3066`, D50) |
| FN8 | A name equal to a Hibernate built-in function (`COALESCE`, `ROUND`, `SYSDATE`, ...) is an error (`LQL-3054`), because Hibernate would silently use the built-in. Use a prefix or a schema name (`PUBLIC.SQ_FN`). | Implemented (tested in Java) |
| FN9 | Kotlin: default arguments (`LQL-3056`), `vararg` (`LQL-3057`) and `inline` are errors. | Default arguments and `vararg`: implemented. `inline`: designed |
| FN10 | v0.1 supports scalar functions only. User aggregate functions and window functions are planned for v0.2. A function without arguments renders as `fn()`. | Scalar functions implemented |
| FN11 | Known DB differences: on MariaDB, a function that returns VARCHAR can have a collation conflict with a string literal. The scale of `BigDecimal` results differs (MariaDB and Oracle 0, H2 1). Boolean results need Oracle 23ai or later. | Measured with prototypes |

The rule checks (`LQL-4034`, `LQL-3052`, `LQL-3054`, `LQL-3058`, `LQL-3055`) are tested in Java ([`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java), region `rules`; see [the Java version of this page](../java/08-procedures-functions.md#810-function-rules-fn1-to-fn11-summary)).

---

<a id="811-database-support"></a>
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

Not planned for v0.1: a string builder for dynamic procedure names (v0.2 candidate), calling one chosen overload of an Oracle package procedure (overload detection is implemented, `LQL-4044`), table-valued functions, paging of procedure results.

Errors from this chapter are `LambdaQLException` (or a subclass) with a `[LQL-xxxx]` code. They are `LQL-3052` to `LQL-3063` when lambdaQL reads the lambda, plus `LQL-3066` (`NonSargableException`, see [8.9](#89-warning-functions-on-columns-and-indexes-sargable)) for a condition that cannot use an index under `SargableMode.STRICT`. They are `LQL-4034` to `LQL-4039`, `LQL-4043` and `LQL-4044` when the call runs. The call can also log `LQL-5004`, `LQL-5005` or `LQL-5006` at `INFO` level. For DB functions in a condition, lambdaQL can log `LQL-5001`, `LQL-5007` (`WARN`) and `LQL-5008` (`INFO`). See [11. Errors and languages](../11-errors-and-languages.md).

---

[Kotlin track](README.md) | Previous: [7. Update, insert, delete](07-dml.md) | Next: [9. Kotlin notes](09-kotlin-notes.md) | [Java version of this page](../java/08-procedures-functions.md)
