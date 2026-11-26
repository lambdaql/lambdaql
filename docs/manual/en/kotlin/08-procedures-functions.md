<a id="8-stored-procedures-and-db-functions-kotlin"></a>
# 8. Stored procedures and DB functions (Kotlin)

[Kotlin track](README.md) | [Manual home](../README.md) | Previous: [7. Update, insert, delete](07-dml.md) | Next: [9. Kotlin notes](09-kotlin-notes.md) | [Java version of this page](../java/08-procedures-functions.md)

Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md)

---

This chapter has two parts.

| Part | What | Annotation | How to call |
|---|---|---|---|
| [8.1 to 8.5](#81-declare-procedures) | **Stored procedures** | `@SqlProcedure` on an abstract function of an interface | `qb.call(OrderProcs::class.java) { it.calc(id, rate) }.execute()` |
| [8.6 to 8.10](#86-declare-db-functions) | **DB functions** (scalar) | `@SqlFunction` on a `@JvmStatic` function in the `companion object` of an interface | Call it directly inside any lambda: `where { OrderFns.discount(it.price)!! > 10 }` |

In both cases, you write the signature **only once**, in a Kotlin interface.
The bodies of these functions are never called. lambdaQL reads the call from the lambda bytecode opcodes and calls the procedure or function in the database.

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

<!-- region: ManualKotlinProcedureTest.kt#declare -->
```kotlin
/** Two OUT values: a data class, in the database order. */
data class Stat(val total: Double?, val cnt: Int?)

/** The INOUT value and the OUT value, in the database order. */
data class Adjusted(val balance: Int?, val msg: String?)

/** A row of a result set: columns are matched by name (customer_id or customerId). */
data class Line(val id: Long?, val price: Double?)

/** A stub interface: lambdaQL reads the calls and never implements it. */
interface OrderProcs {
    @SqlProcedure("SQ_PROC_CALC")
    fun calc(customerId: Int, rate: Double): Double?                // IN, IN, OUT (non-null parameters)

    @SqlProcedure("SQ_PROC_STAT")
    fun stat(customerId: Int?): Stat                                // IN, OUT, OUT

    @SqlProcedure("SQ_PROC_ADJUST")
    fun adjust(add: Int?, @InOut balance: Int?): Adjusted           // IN, INOUT, OUT

    @SqlProcedure("SQ_PROC_RECENT")
    fun recent(customerId: Int?): List<Line>                        // a result set (Oracle, PostgreSQL: a cursor)

    @SqlProcedure("SQ_PROC_PURGE")
    fun purge(status: Int?)                                         // no result

    @SqlProcedure("SQ_PROC_CALC")
    fun calcWrong(customerId: Int?): Double?                        // does not match the database: 1 argument
}
```

The examples in this chapter call procedures created in the database in advance (`SQ_PROC_CALC` and others). The `Order` of the examples has the columns `customer_id` and `price`.

Notes:

- `@SqlProcedure` (or `@SqlFunction`) is **required**. It tells lambdaQL what the function is.
  (The names `@Procedure` and `@Function` were not used. `@Procedure` is also a Spring Data JPA annotation, and `@Function` clashes with Kotlin's `kotlin.Function`.)
- If the value is empty, the name is made from the function name: `calcTotal` → `CALC_TOTAL`.
- For a procedure in an Oracle package, give `pkg`: `@SqlProcedure(pkg = "SQ_PKG", value = "TWICE")` (see `PkgProcs` in [8.4](#84-warning-argument-order)).
- Use **nullable return types** (`Double?`). If a procedure returns NULL for a non-null type, lambdaQL throws `IllegalStateException` with the code `LQL-4039`.
- The interface and the result data classes are copies of the DB signature. This is the cost of type safety. lambdaQL checks part of them against the database.
- You can put procedures (abstract functions) and DB functions (in the `companion object`) in **the same interface**.

Example code: [`ManualKotlinProcedureTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinProcedureTest.kt) (`declare`).

---

<a id="82-call-procedures"></a>
## 8.2 Call procedures

Use `qb.call(Interface::class.java) { it.function(args) }`. Then call `execute()`.

<!-- region: ManualKotlinProcedureTest.kt#call -->
```kotlin
val total: Double? = qb.call(OrderProcs::class.java) { it.calc(customerId, rate) }.execute()
val stat: Stat = qb.call(OrderProcs::class.java) { it.stat(customerId) }.execute()
val recent: List<Line> = qb.call(OrderProcs::class.java) { it.recent(customerId) }.execute()
// named arguments with plain values: the compiler puts them back in the stub order
val same: Double? = qb.call(OrderProcs::class.java) { it.calc(rate = rate, customerId = customerId) }.execute()
```

Call a procedure without a result with `exec`.

<!-- region: ManualKotlinProcedureTest.kt#exec -->
```kotlin
// no result: exec, not call
qb.exec(OrderProcs::class.java) { it.purge(9) }.execute()
```

Why `exec` and not `call` for a function without a result?

- `run` was not chosen. In Kotlin, if you forget the argument, `run { }` quietly becomes the standard library `run`.

<a id="what-the-lambda-may-contain-rule-pr12"></a>
### What the lambda may contain (rule PR12)

The lambda body is **one stub function call**. Nothing else.

| Allowed | Not allowed |
|---|---|
| `{ it.calc(customerId, rate) }` | Logging, two calls, `if`, nested calls |
| Arguments: captured values, constants, and computations of them (`rate * 2`) | Entity paths (there is no entity here) |
| Named arguments with **simple values** (`calc(rate = rate, customerId = customerId)`) | Named arguments with a computation (`calc(rate = r * 2, customerId = customerId)`), see below |

For a named argument with a computation, Kotlin makes a hidden local variable. lambdaQL rejects local variables.
In this case, use positional arguments: `it.calc(customerId, r * 2)`.

Other points:

- Using `exec` for a procedure with OUT parameters is an error (`LQL-4037`). This keeps you from losing OUT values by mistake.
- Database default values of parameters cannot be used. Pass every IN parameter.
- `null` as an IN value is fine.
- Both non-null parameters (`Int`, `Double` of `calc`) and nullable parameters (`Int?` of `stat`) work.

If you call a function without `@SqlProcedure`, you get an error when lambdaQL reads the lambda (`LQL-3060`). This happens before the database is queried, so it is checked on H2 too.

<!-- region: ManualKotlinProcedureTest.kt#not-annotated -->
```kotlin
// only @SqlProcedure functions of the stub can be called
assertEquals("LQL-3060", assertThrows<LambdaQLException> {
    qb.call(OrderProcs::class.java) { it.toString() }.execute()
}.code())
```

Comparing the stub with the database, and the OUT check of `exec`:

<!-- region: ManualKotlinProcedureTest.kt#rules -->
```kotlin
// the stub is checked against the database parameters on the first call
assertEquals("LQL-4036", assertThrows<LambdaQLException> {
    qb.call(OrderProcs::class.java) { it.calcWrong(1) }.execute()
}.code())
// exec cannot drop OUT values
assertEquals("LQL-4037", assertThrows<LambdaQLException> {
    qb.exec(OrderProcs::class.java) { it.calc(1, 1.0) }.execute()
}.code())
```

Example code: [`ManualKotlinProcedureTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinProcedureTest.kt) (`call`, `exec`, `not-annotated`, `rules`).

---

<a id="83-out-inout-and-result-sets"></a>
## 8.3 OUT, INOUT and result sets

How the return type maps to the procedure:

| The procedure has | Return type of the stub function |
|---|---|
| Nothing | No return type (`Unit`) → call with `qb.exec(...)` |
| 1 OUT parameter | That value (`Double?`, `Int?`, ...) |
| Many OUT / INOUT parameters | A Kotlin data class (or a Java record) |
| 1 result set (MariaDB and MySQL `SELECT`, Oracle `SYS_REFCURSOR` OUT, PostgreSQL `REFCURSOR` OUT) | `List<T>` |

How the data class properties are filled:

- **Scalar properties** ← OUT and INOUT parameters (not cursors), in DB order.
- If the counts do not match, lambdaQL throws an error (`LQL-4038`).
- lambdaQL reads the parameter positions from the DB metadata.

### INOUT

Mark the parameter with `@InOut`. The value you pass is the value that goes **in**. The value that comes **out** is a property of the returned data class.

<!-- region: ManualKotlinProcedureTest.kt#inout -->
```kotlin
// 10 goes in as the INOUT value; the new balance comes back in the data class
val adjusted: Adjusted = qb.call(OrderProcs::class.java) { it.adjust(5, 10) }.execute()
assertEquals(Adjusted(15, "ok"), adjusted)
```

Put `@InOut` exactly on the arguments that are INOUT in the database. Otherwise the first call fails (`LQL-4036`).

<a id="result-set-element-type-t"></a>
### Result set element type `T`

| If `T` is | How lambdaQL reads it |
|---|---|
| An entity | A Hibernate result class (`StoredProcedureQuery`) |
| A Kotlin data class or a Java record | JDBC `CallableStatement`, columns matched **by label**. It drops `_` and ignores case, so `customer_id` maps to `customerId` |
| A single value (`Long?`, `String?`, ...) | The first column of each row |

A normal class cannot be used as the row type here. Use a data class.
MariaDB and Oracle return the columns of `SELECT *` in different orders, so lambdaQL never maps properties by absolute position.
Common types are converted (for example, numbers to `Long`/`Int`, timestamps to `LocalDateTime`).

Limits:

- Procedure results cannot be paged.
- Oracle implicit results (`DBMS_SQL.RETURN_RESULT`) are not supported. Use a `SYS_REFCURSOR` OUT parameter.

<a id="transactions-rule-pr9"></a>
### Transactions

- DML inside the procedure is part of the caller's transaction. It is rolled back together.
- Before the call, lambdaQL flushes the persistence context. But it flushes only when a transaction is active and there are changes.
- `QueryBuilder(EntityManagerFactory)` runs the call in its own transaction. A `QueryBuilder` that got an `EntityManager` (including Spring's shared `EntityManager`) uses the caller's transaction. Without a transaction, the call is auto-committed.
- After the call, loaded entities can be stale. Use `refresh` or `evict` on those entities.
- `COMMIT` or DDL inside the procedure breaks the transaction. lambdaQL cannot stop this.

<a id="affected-rows-rule-pr10"></a>
### Affected rows (rule PR10)

Procedures have no common "affected rows" result (Oracle returns -1).
If you need the count, return it in an OUT parameter.

Example code: [`ManualKotlinProcedureTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinProcedureTest.kt) (`inout`).

---

<a id="84-warning-argument-order"></a>
## 8.4 Warning: argument order

> [!WARNING]
> **The order of the stub function parameters must be the same as the order in the database.**

lambdaQL binds arguments **by position**.
Hibernate 6.6.5 binds by position even if you register names (measured, 7.4 not measured).
The MariaDB and H2 drivers do not support named parameters.
So a wrong order can give **wrong results with no error**.

Kotlin named arguments (`calc(rate = rate, id = id)`) do **not** change this. The compiler puts the arguments back in the order of the stub function. Only the order of the stub function matters.

What lambdaQL checks with JDBC metadata (`getProcedureColumns`) on the first call. The result is cached per procedure name.

| Check | Found |
|---|---|
| The procedure exists | Yes, error `LQL-4035` |
| Number of IN and INOUT parameters = number of stub arguments | Yes, error `LQL-4036` |
| INOUT parameters = arguments with `@InOut` | Yes, error `LQL-4036` |
| Number of OUT values and result sets = number of parts of the return type | Yes, error `LQL-4038` |
| Kind of type (number, string, date and time, boolean, binary) | Yes, error `LQL-4036`. Differences within the same kind are not compared (see "Argument type check" below) |
| The same parameter names in a different order | Yes, error `LQL-4043`. Only when the stub argument names are known (see below) |
| An Oracle package procedure name with many overloads | Yes, error `LQL-4044` |

For example, if you declare the stub as `fun swapped(from: Int?, to: Int?)` for `SQ_PROC_DIFF(p_to, p_from)` in the database, the arguments are swapped.
The count, modes and types all match. Only the names can show the mistake.
So lambdaQL compares the names of IN and INOUT arguments on the first call.

- The stub argument names are read from the class file. kotlinc keeps the names only when you compile with `-java-parameters` (see [Kotlin compiler settings](02-getting-started.md#kotlin-compiler-settings)). If you put `@org.lambdaql.DbParam("p_to")` on a stub argument, you give the DB name directly, with no compiler option.
- From the DB names, lambdaQL removes a prefix that is common to **all** names and ends with `_` (`p_`, `in_`, `v_` and so on). It does not assume a fixed prefix. Then it ignores case and `_`.
- If the names are the same and only the order differs, `LambdaQLException` `LQL-4043` is thrown before the call.
- If the names differ (the stub follows another naming style), it is not an error. Add `@DbParam` so that the check works.
- If the class file has no names and two neighboring arguments have the same type, `LQL-5004` is logged once per procedure at `INFO` level.
- Types are compared only by kind (see "Argument type check" below). Two swapped arguments of the same kind can be found only by name.

If you compile without `-java-parameters`, give the names with `@DbParam`. The stub below is an example.

<!-- region: ManualKotlinProcedureTest.kt#names -->
```kotlin
/** kotlinc keeps argument names only with -java-parameters; @DbParam gives the database name without it. */
interface DiffProcs {
    @SqlProcedure("SQ_PROC_DIFF")
    fun diff(@DbParam("p_to") to: Int?, @DbParam("p_from") from: Int?): Int?        // the database order: runs

    @SqlProcedure("SQ_PROC_DIFF")
    fun swapped(@DbParam("p_from") from: Int?, @DbParam("p_to") to: Int?): Int?     // the same names, other order

    @SqlProcedure("SQ_PROC_DIFF")
    fun text(to: String?, from: Int?): Int?                                         // a string for a number parameter
}
```

<!-- region: ManualKotlinProcedureTest.kt#name-check -->
```kotlin
assertEquals(7, qb.call(DiffProcs::class.java) { it.diff(10, 3) }.execute())
// the same names in another order: stopped before the call (it would run with the values swapped)
assertEquals("LQL-4043", assertThrows<LambdaQLException> {
    qb.call(DiffProcs::class.java) { it.swapped(3, 10) }.execute()
}.code())
// the kind of each argument is compared with the database parameter: a string for a number parameter stops
assertEquals("LQL-4036", assertThrows<LambdaQLException> {
    qb.call(DiffProcs::class.java) { it.text("10", 3) }.execute()
}.code())
```

**Recommended:** compile with `-java-parameters` and use the same names as the DB (you can leave out the common prefix), or add `@DbParam`. Then a swapped order is found.

**Argument type check.** On the first call, the type of each argument is compared with the kind of the DB parameter type.
There are five kinds: number, string, date and time, boolean, binary. If they differ, the call stops with `LQL-4036` before it runs. `text` in the example above is this case.

- A boolean argument is also treated as matching a number parameter. MariaDB's `BOOLEAN` is `TINYINT(1)`, and Oracle before 23 has no `BOOLEAN` type.
- A side whose kind cannot be decided (enums, object types, cursors) is not compared.
- Differences within the same kind (an `Int?` argument and a `NUMBER(10,2)` parameter, and so on) are left to the driver conversion.

**Oracle package overloads.** An Oracle package can have many procedures with the same name.
lambdaQL cannot tell which one the stub means, so a call to such a name stops with `LQL-4044` (Oracle only).

<!-- region: ManualKotlinProcedureTest.kt#package -->
```kotlin
/** Oracle package procedures: TWICE has one form, HALF has two (an overload). */
interface PkgProcs {
    @SqlProcedure(pkg = "SQ_PKG", value = "TWICE")
    fun twice(value: Double?): Double?

    @SqlProcedure(pkg = "SQ_PKG", value = "HALF")
    fun half(value: Double?): Double?
}
```

<!-- region: ManualKotlinProcedureTest.kt#overload -->
```kotlin
assertEquals(5.0, qb.call(PkgProcs::class.java) { it.twice(2.5) }.execute()!!, 1e-9)
// HALF has two overloads in the package: lambdaQL cannot tell which one to call
assertEquals("LQL-4044", assertThrows<LambdaQLException> {
    qb.call(PkgProcs::class.java) { it.half(10.0) }.execute()
}.code())
```

<a id="when-the-metadata-cannot-be-read"></a>
**When the metadata cannot be read.** The DB user may not have the right to read the dictionary views or `information_schema`.
If the metadata query itself fails (`SQLException`), lambdaQL does not stop the call.

- It logs `LQL-5005` once per procedure at `INFO` level and does not check the stub.
- The argument layout comes from the stub: IN and INOUT arguments in stub order, then the OUT parameters (in the order of the result parts), and on Oracle then one `REF CURSOR` per `List` part. So without metadata, the OUT parameters must come after the IN parameters in the DB declaration.
- Checks that need metadata are skipped: the count and `@InOut` check (`LQL-4036`) and the name order check (`LQL-4043`).
- If the query succeeds but the procedure does not exist, it is still `LQL-4035`.
- If only the Oracle overload query (`all_arguments`) fails, `LQL-5006` is logged at `INFO` level, and the call runs without the overload check.

Example code: [`ManualKotlinProcedureTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinProcedureTest.kt) (`names`, `name-check`, `package`, `overload`).

---

<a id="85-procedure-rules-pr1-to-pr15-summary"></a>
## 8.5 Procedure rules PR1 to PR15 (summary)

| # | Rule | Status |
|---|---|---|
| PR1 | Name: the annotation value is used as it is. If the value is empty, the function name in UPPER_SNAKE. Oracle packages: `@SqlProcedure(pkg = ..., value = ...)`. | Implemented |
| PR2 | Arguments are bound **by position**. On the first call, the number of IN/INOUT parameters and the `@InOut` positions are compared with the DB metadata. If the names are known, they are compared too, and the same names in a different order are an error. The kind of the argument types (number, string, date and time, boolean, binary) is also compared, and a difference is an error (`LQL-4036`) (see [8.4](#84-warning-argument-order)). If the metadata cannot be read, the checks are skipped and the argument layout comes from the stub (see [8.4](#when-the-metadata-cannot-be-read)). | The count, `@InOut` and name checks are implemented. Oracle package overload detection is also implemented. The type check is also implemented. The fallback without metadata (`LQL-5005`, `LQL-5006`) is implemented (MariaDB). |
| PR3 | 1 OUT → return value. Many OUT/INOUT → data class or record. `@InOut` marks INOUT parameters. | Implemented |
| PR4 | 1 result set → `List<T>`. Entities through Hibernate, data classes/records through JDBC with label matching. No paging. | Implemented |
| PR5 | Oracle implicit results are not supported. Use a `SYS_REFCURSOR` OUT parameter. | – |
| PR6 | A DB function called alone uses a different path for each database (see [8.8](#88-call-a-db-function-alone)). | Implemented |
| PR7 | DB functions in queries: see [8.6](#86-declare-db-functions) and rules FN1 to FN11. | Implemented |
| PR8 | A DB function on a column in a condition cannot use an index, so a WARN is logged (see [8.9](#89-warning-functions-on-columns-and-indexes-sargable)). The code is `LQL-5001`. | Implemented. An error with `SargableMode.STRICT` (`LQL-3066`) |
| PR9 | A procedure runs in the caller's transaction. lambdaQL flushes before the call when needed. After the call, loaded entities can be stale. | Implemented |
| PR10 | No common affected-row count. Use an OUT parameter. | – |
| PR11 | `null` IN values are fine. DB parameter default values cannot be used. | Implemented |
| PR12 | The lambda is one stub call. Arguments are captured values, constants or computations of them. Named arguments only with simple values. `exec` with OUT parameters is an error. | Implemented |
| PR13 | Both non-null parameters (`Int`, `Double`) and nullable parameters (`Int?`, `Double?`) work. | Implemented |
| PR14 | Each lambda is analyzed only once. Procedure metadata is read only once per name. | Implemented |
| PR15 | H2 has no OUT, INOUT or packages. Test procedures on databases such as MariaDB, MySQL, PostgreSQL and Oracle (for example, with Testcontainers). | – |

---

<a id="86-declare-db-functions"></a>
## 8.6 Declare DB functions

A DB function is a function that you created in the database. For example, `SQ_FN_DISCOUNT(price)`.
A Kotlin interface has no static functions, so declare them in the **`companion object`** of the interface with **`@JvmStatic`** and `@SqlFunction`.
The body must only throw an exception. The body is never called. If you call it directly outside a lambda, it throws an exception.

<!-- region: ManualKotlinFunctionTest.kt#declare -->
```kotlin
/** Database functions: functions in the companion object of your own interface. The bodies only throw. */
interface OrderFns {
    companion object {
        /** `SQ_FN_DISCOUNT(price)`: 90% of the price. */
        @JvmStatic
        @SqlFunction("SQ_FN_DISCOUNT")
        fun discount(price: Double?): Double? = throw DbOnlyException()

        /** `SQ_FN_IS_GOLD(grade)`: a grade of 5 or more. */
        @JvmStatic
        @SqlFunction("SQ_FN_IS_GOLD")
        fun isGold(grade: Int?): Boolean? = throw DbOnlyException()
    }
}
```

The examples use two functions created in the database in advance (on H2 with `CREATE ALIAS`). `SQ_FN_DISCOUNT` gives 90% of the price, and `SQ_FN_IS_GOLD` is true for grade 5 or higher.

If you call `OrderFns.discount(...)` **outside** a lambdaQL lambda, you get `DbOnlyException` (`LQL-4034`). This is intended (see [8.10](#810-function-rules-fn1-to-fn11-summary)).

`@SqlFunction` has two values.

| Value | Meaning |
|---|---|
| `value` | The function name used in the query. If empty, the function name in UPPER_SNAKE (`isGold` → `IS_GOLD`). A schema name is also allowed (`PUBLIC.SQ_FN`) |
| `sql` | The SQL name when it differs from `value`. Such a function needs registration (see [Registration](#registration-optional)) |

Why `@JvmStatic`? Then Java code can call `OrderFns.discount(...)` as a static function, and the function looks the same to Java and Kotlin callers.
lambdaQL reads the annotations on the `companion object` function.

Other ways to declare:

| Declaration | Status |
|---|---|
| Interface `companion object` + `@JvmStatic` | **Recommended** |
| Kotlin `object` + `@JvmStatic` | Works |
| Java interface static method (called from Kotlin) | Works |
| Kotlin top-level function | Works, **not recommended**: if you forget the import, a standard library function with the same name (for example, `maxOf`) can be used quietly |
| Function type value (`val fn: (Double?) -> Double?`) | Rejected (`LQL-3008`) |
| `@SqlFunction` with default arguments or `vararg` | Rejected (`LQL-3056` for default arguments, `LQL-3057` for `vararg`) |

`object`, top-level functions and Java static methods make the same HQL.

<!-- region: ManualKotlinFunctionTest.kt#other-forms -->
```kotlin
// a Kotlin object with @JvmStatic, and a top-level function: the same HQL
val inObject = qb.from(Order::class.java).where { PriceFns.discount(it.price)!! > 100 }
val topLevel = qb.from(Order::class.java).where { manualKotlinTopLevelDiscount(it.price)!! > 100 }
assertEquals(inObject.toHql().hql(), topLevel.toHql().hql())
```

Call the function **with the interface name** (`OrderFns.discount`). Importing the function also works, but a member function with the same name can hide it.

Example code: [`ManualKotlinFunctionTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinFunctionTest.kt) (`declare`, `other-forms`).

---

<a id="87-use-db-functions-in-queries"></a>
## 8.7 Use DB functions in queries

Call the function directly. No context argument is needed.
It works in `where`, `select`, `orderBy` and `set`.

<!-- region: ManualKotlinFunctionTest.kt#where -->
```kotlin
// the function call is written as function('<name>' as <type>, ...); the column is its argument
val q = qb.from(Order::class.java).where { OrderFns.discount(it.price)!! > 100 }
assertEquals("select e0 from Order e0 where function('SQ_FN_DISCOUNT' as Double, e0.price) > 100.0", q.toHql().hql())
```

A function that returns Boolean is a condition on its own (`... = true`).

<!-- region: ManualKotlinFunctionTest.kt#boolean -->
```kotlin
val q = qb.from(Order::class.java).where { OrderFns.isGold(it.customer!!.grade)!! }
assertEquals("select e0 from Order e0 join e0.customer j1 where function('SQ_FN_IS_GOLD' as Boolean, j1.grade) = true",
    q.toHql().hql())
```

If you use only captured values as arguments, the column side stays clean, and the function is computed in the database with the bound values.

<!-- region: ManualKotlinFunctionTest.kt#captured -->
```kotlin
// a function of captured values only is still computed by the database (the value is bound)
val q = qb.from(Order::class.java).where { it.price > OrderFns.discount(base)!! }
assertEquals("select e0 from Order e0 where e0.price > function('SQ_FN_DISCOUNT' as Double, :p1)", q.toHql().hql())
```

`select` and `orderBy` (`Priced` is `data class Priced(val id: Long?, val discounted: Double?)`):

<!-- region: ManualKotlinFunctionTest.kt#select -->
```kotlin
val q = qb.from(Order::class.java)
    .orderBy { o -> desc(OrderFns.discount(o.price)) }
    .select { Priced(it.id, OrderFns.discount(it.price)) }
assertEquals("select e0.id, function('SQ_FN_DISCOUNT' as Double, e0.price) from Order e0"
    + " order by function('SQ_FN_DISCOUNT' as Double, e0.price) desc nulls last", q.toHql().hql())
```

`set`:

<!-- region: ManualKotlinFunctionTest.kt#update -->
```kotlin
val u = qb.update(Order::class.java)
    .set { it.price = OrderFns.discount(it.price)!! }
    .where { it.price > 100 }
assertEquals("update Order e0 set e0.price = function('SQ_FN_DISCOUNT' as Double, e0.price) where e0.price > 100.0",
    u.toHql().hql())
```

If the `lambdaql.functions.classes` setting exists, Hibernate keeps the case of the name (`FN_DISCOUNT`) even for functions that are not registered (seen on three databases, the cause is not checked yet).
With no registration at all, Hibernate can write the name in lower case. A name without quotes works either way.

`!!` is only for the compiler (because the function returns `Double?`). lambdaQL skips it.

<a id="how-lambdaql-renders-it"></a>
### How lambdaQL renders it

lambdaQL **always** writes a DB function in this form:

```text
function('<NAME>' as <Type>, arg1, arg2, ...)
```

`<Type>` comes from the declared return type. Only these types are allowed:

| Allowed return types |
|---|
| `Double`, `Integer`, `Long`, `BigDecimal`, `String`, `LocalDate`, `LocalDateTime`, `Boolean` |

Kotlin `Double?`, `Int?`, `Long?`, `Boolean?` and their non-null forms map to these names.
Generic returns (`fun <X> nvl(a: X, b: X): X`), entities, collections and `Unit` are rejected at analysis time.

<a id="registration-optional"></a>
### Registration (optional)

The form `function('FN_DISCOUNT' as Double, ...)` needs no registration (checked on three databases).
If you register the function interface, Hibernate also keeps the case of the name and checks the number and types of arguments.

lambdaQL has a Hibernate `FunctionContributor`, `org.lambdaql.hibernate.LambdaQlFunctionContributor` (found by ServiceLoader). List the interfaces in a Hibernate property:

```properties
# plain Hibernate
lambdaql.functions.classes=com.acme.OrderFns,com.acme.CustomerFns

# Spring Boot
spring.jpa.properties.lambdaql.functions.classes=com.acme.OrderFns
```

If you do not set the property, nothing is registered. Package scanning is not supported.

<a id="a-different-sql-name-needs-registration"></a>
### Registration for a different SQL name

`value` is the name in the query, and `sql` is the name in SQL. If the two names differ, the function must be registered.
This is useful for a function name made with quotes and mixed case (`"SQ_Fn_Mixed"`). Without registration, the name can become lower case on H2 and Oracle.

If such a function is not registered, lambdaQL fails when it reads the lambda (`LQL-3058`). `BadFns.unregistered` (`value = "SQ_TAX_KT"`, `sql = "SQ_FN_DISCOUNT"`) in [8.10](#810-function-rules-fn1-to-fn11-summary) is this case. For how to register, see [Registration](#registration-optional).

Example code: [`ManualKotlinFunctionTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinFunctionTest.kt) (`where`, `boolean`, `captured`, `select`, `update`).

---

<a id="88-call-a-db-function-alone"></a>
## 8.8 Call a DB function alone

Use `qb.call { ... }` with a **lambda that has no argument**.

<!-- region: ManualKotlinFunctionTest.kt#call -->
```kotlin
// a function on its own: a lambda without parameters
val discounted: Double? = qb.call { OrderFns.discount(price) }.execute()
```

The lambda must be one `@SqlFunction` call (`LQL-3063`).

This query runs as `select FN_DISCOUNT(?)` with no FROM. Oracle accepts this only from 23ai. On Oracle, lambdaQL uses a JDBC function call instead (see the table below).

The number of arguments is different, so it does not clash with `qb.call(OrderProcs::class.java) { ... }`.

How lambdaQL runs it:

| Database | Path |
|---|---|
| Oracle | JDBC function call `{? = call FN(?)}`. It also works for functions that do DML and functions that return a cursor. |
| MariaDB, H2 | HQL `select function('FN' as T, :p1)` |

lambdaQL first flushes pending changes (the same as for procedure calls), so the function sees the latest changes.

Example code: [`ManualKotlinFunctionTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinFunctionTest.kt) (`call`).

---

<a id="89-warning-functions-on-columns-and-indexes-sargable"></a>
## 8.9 Warning: functions on columns and indexes (SARGable)

lambdaQL tries to make SQL that **can use indexes** (see [10. Rules and limits](10-rules-and-limits.md#102-sargable-rule-index-friendly-sql)).
A function that wraps a column usually **cannot** use a normal index.

`where { OrderFns.discount(it.price)!! > 100 }` in [8.7](#87-use-db-functions-in-queries) becomes `function('SQ_FN_DISCOUNT' as Double, e0.price) > 100.0`, and it does not use the index on `price`.

So lambdaQL logs a **WARN** for this form.
The check runs once per `where` lambda, when the analysis result is cached. A condition without `@SqlFunction` does not query the DB.

| Case | Code | H2 | MariaDB | Oracle |
|---|---|---|---|---|
| ① A function wraps a column: `fn(o.price) > 10`, a Boolean function on a column, a function inside `IN` or `IS NULL` | `LQL-5001` (WARN) | WARN | WARN (stored functions cannot be used in indexes) | WARN. **But** it is not logged if the table has a function-based index with the same expression `FN(COLUMN)` in `user_ind_expressions`. A number conversion that Oracle added because the column type differs from the function argument type (`FN(TO_NUMBER(COLUMN))` and so on) is treated as the same expression |
| ② A function on the value side compared with a column: `o.price > fn(rate)` | `LQL-5007` (WARN) | Not checked | WARN if `information_schema.routines.is_deterministic = 'NO'` (the default!). In this case MariaDB scans the whole index | Not checked (Oracle does a range scan either way) |

- The table and column names used for the Oracle index check come from the Hibernate mapping. Only functions with one column argument are checked.
- If the metadata query fails (for example, no permission), `LQL-5008` is logged at `INFO` level. ① still warns, and ② is not checked.

The warning codes that the example below logs differ by database. H2 logs `LQL-5001`, MariaDB and MySQL log `LQL-5001` and `LQL-5007`, PostgreSQL with a function-based index logs `LQL-5007`, and Oracle with a function-based index logs no warning. `CheckFns.nondet` is a `@SqlFunction` (`SQ_FN_NONDET`) of the same form as `discount`. It is declared as `NOT DETERMINISTIC` on MariaDB and MySQL and as `VOLATILE` on PostgreSQL.

<!-- region: ManualKotlinFunctionTest.kt#sargable -->
```kotlin
// the function wraps the column: an index on price cannot be used (LQL-5001 at WARN),
// unless Oracle or PostgreSQL has a function-based index on SQ_FN_DISCOUNT(price)
qb.from(Order::class.java).where { OrderFns.discount(it.price)!! > 100 }.toHql()
// the function is on the value side: fine, unless the database says it is not deterministic (LQL-5007 at WARN)
qb.from(Order::class.java).where { it.price > CheckFns.nondet(base)!! }.toHql()
```

**SARGable mode**. `SargableMode` has three values.

- `OFF`: no check and no metadata query.
- `WARN` (default): logs warnings.
- `STRICT`: in the cases where `WARN` would log a warning (`LQL-5001`, `LQL-5007`, `LQL-5012`), the analysis fails with `org.lambdaql.NonSargableException` (`LQL-3066`).

Set it per persistence unit with `SargablePolicy.register(emf, SargableMode.STRICT)`, and remove it with `SargablePolicy.unregister(emf)`. In Spring, use `lambdaql.sargable.mode=STRICT` (see [12](12-spring-data-jpa.md)).
The check runs once per lambda, when the analysis result is put into the shared cache. So register the mode before the first query.

**Arithmetic on a column**.

- `+ - * /` with one column and values only (`it.cnt * 2 > n`) can be moved to the value side. lambdaQL logs `LQL-5012` at `WARN` level, and it is an error with `STRICT`.
- Arithmetic between columns (`it.price!! * it.cnt > n`) and `%` cannot be moved, so `LQL-5013` is logged only at `DEBUG` level. It is allowed even with `STRICT`.
- The moved form must have the same meaning. Divide by a decimal, as in `it.cnt > n / 2.0`. The integer division `n / 2` gives different rows for `>=`, `=`, `<` and a negative `n`. If you multiplied by a negative number, turn the comparison around. The `LQL-5012` message explains this.
- Reducing warnings by checking whether an index exists was not chosen. Because of permissions and synonyms, "no index" and "unknown" get mixed, and column arithmetic is a rare form.

`STRICT` mode and column arithmetic:

<!-- region: ManualKotlinFunctionTest.kt#sargable-strict -->
```kotlin
// STRICT: what WARN would log fails the analysis (NonSargableException, LQL-3066)
SargablePolicy.register(emf, SargableMode.STRICT)
val fn = assertThrows<NonSargableException> {
    qb.from(Order::class.java).where { OrderFns.discount(it.total)!! > 100 }.toHql()
}
assertEquals("LQL-3066", fn.code())
assertThrows<NonSargableException> { qb.from(Order::class.java).where { it.cnt!! * 2 > n }.toHql() }
// the same meaning on the value side uses the index; divide by a decimal (n / 2 is integer division)
val moved = qb.from(Order::class.java).where { it.cnt!! > n / 2.0 }.toHql()
assertEquals("select e0 from Order e0 where e0.cnt > 2.5BD", moved.hql())
// arithmetic between columns cannot move, so STRICT allows it
qb.from(Order::class.java).where { it.cnt!! * it.status > n }.toHql()
```

**Recommended:**

- If you can, put the function on the value side: `it.price!! > OrderFns.discount(base)!!`.
- On MariaDB, declare the function `DETERMINISTIC` if it really is deterministic.
- On Oracle, a function-based index with the same expression removes the warning (such an index needs a `DETERMINISTIC` function).

Example code: [`ManualKotlinFunctionTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinFunctionTest.kt) (`sargable`, `sargable-strict`).

---

<a id="810-function-rules-fn1-to-fn11-summary"></a>
## 8.10 Function rules FN1 to FN11 (summary)

| # | Rule | Status |
|---|---|---|
| FN1 | `@SqlFunction` is the function annotation. If the value is empty, the function name in UPPER_SNAKE. | Implemented |
| FN2 | lambdaQL finds the called function and reads its annotation. In Kotlin, it skips the `Companion` receiver. The body must only throw an exception (`= throw ...`). The application and the database could give different values, so a function with a real body is an error (`LQL-3052`). | Implemented |
| FN3 | All overloads with the same name must map to the same DB function. Otherwise it is an error (`LQL-3053`). (A Kotlin `Double?` argument picks the `Double?` overload. A Java entity getter seen from Kotlin can pick the primitive `double` overload.) | Implemented |
| FN4 | A call without the annotation, or a call that lambdaQL cannot identify, is an error. It is not skipped quietly. | Implemented |
| FN5 | Arguments follow the value rules of [3. Conditions](03-where.md). An argument made only of captured values is also bound and evaluated **in the database**. | Implemented |
| FN6 | `!!` and boxing calls are skipped. The return type comes from the declared function. It must map to one of `Double`, `Integer`, `Long`, `BigDecimal`, `String`, `LocalDate`, `LocalDateTime`, `Boolean` (`LQL-3055`). | Implemented |
| FN7 | A function on a column gives a WARN (see [8.9](#89-warning-functions-on-columns-and-indexes-sargable)). On MariaDB, a `NOT DETERMINISTIC` function on the value side also gives a WARN (`LQL-5007`). | Implemented. An error with `SargableMode.STRICT` (`LQL-3066`) |
| FN8 | A name that is the same as a Hibernate built-in function (`COALESCE`, `ROUND`, `SYSDATE`, ...) is an error (`LQL-3054`), because Hibernate would quietly use the built-in function. Use a prefix or a schema name (`PUBLIC.SQ_FN`). | Implemented |
| FN9 | Kotlin: a call that uses default arguments (`LQL-3056`) and `vararg` (`LQL-3057`) are errors. | Implemented |
| FN10 | Only scalar functions are supported. A function with no arguments is written as `fn()`. | Implemented |
| FN11 | Known DB differences: on MariaDB, a function that returns VARCHAR can have a collation clash with a string literal. The scale of `BigDecimal` results differs (0 on MariaDB and Oracle, 1 on H2). Boolean results need Oracle 23ai or later. | Measured with a prototype |

Declarations that break the rules:

<!-- region: ManualKotlinFunctionTest.kt#bad-declare -->
```kotlin
/** Functions that break the rules. */
interface BadFns {
    companion object {
        @JvmStatic
        @SqlFunction("SQ_FN_DISCOUNT")
        fun computes(price: Double?): Double? = price                  // a body that computes a value

        @JvmStatic
        @SqlFunction("SQ_FN_DISCOUNT")
        fun withDefault(price: Double?, rate: Double = 0.9): Double? = throw DbOnlyException()   // a default argument

        @JvmStatic
        @SqlFunction("SQ_FN_DISCOUNT")
        fun many(vararg prices: Double?): Double? = throw DbOnlyException()                      // vararg

        @JvmStatic
        @SqlFunction(value = "SQ_TAX_KT", sql = "SQ_FN_DISCOUNT")
        fun unregistered(price: Double?): Double? = throw DbOnlyException()   // its own SQL name, not registered
    }
}
```

<!-- region: ManualKotlinFunctionTest.kt#rules -->
```kotlin
// the body never runs in Kotlin
assertEquals("LQL-4034", assertThrows<DbOnlyException> { OrderFns.discount(1.0) }.code())
// a body that computes a value is rejected (Kotlin and the database could give different values)
assertEquals("LQL-3052", assertThrows<LambdaQLException> {
    qb.from(Order::class.java).where { BadFns.computes(it.price)!! > 1 }
}.code())
// a call that uses a Kotlin default argument, and a vararg function
assertEquals("LQL-3056", assertThrows<LambdaQLException> {
    qb.from(Order::class.java).where { BadFns.withDefault(it.price)!! > 1 }
}.code())
assertEquals("LQL-3057", assertThrows<LambdaQLException> {
    qb.from(Order::class.java).where { BadFns.many(it.price)!! > 1 }
}.code())
// a function with its own SQL name must be registered (lambdaql.functions.classes)
assertEquals("LQL-3058", assertThrows<LambdaQLException> {
    qb.from(Order::class.java).where { BadFns.unregistered(it.price)!! > 1 }
}.code())
```

A name that is the same as a built-in function (`LQL-3054`) and a return type that does not map (`LQL-3055`) are also rejected when the lambda is read.

Example code: [`ManualKotlinFunctionTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinFunctionTest.kt) (`bad-declare`, `rules`).

---

<a id="811-database-support"></a>
## 8.11 Database support

| Feature | H2 | MariaDB, MySQL | PostgreSQL | Oracle |
|---|---|---|---|---|
| Procedure calls by lambdaQL (IN, OUT, INOUT) | **No** (H2 has no OUT) | Yes | Yes | Yes |
| 1 result set | – | Yes (`SELECT` inside the procedure) | Yes (`REFCURSOR` OUT) | Yes (`SYS_REFCURSOR` OUT) |
| Packages (`pkg`) | – | – | – | Yes |
| DB functions in queries | Yes | Yes | Yes | Yes |
| Calling a DB function alone | Yes | Yes | Yes | Yes |
| DB functions with Boolean results | Yes | Yes | Yes | Yes (23ai or later) |

The table shows the range where the behavior was checked on each database.

Not supported: picking one overload of an Oracle package procedure to call (overload detection is implemented, `LQL-4044`), table-valued functions, paging of procedure results.

The errors of this chapter are `LambdaQLException` (or its subclasses) with a code `[LQL-xxxx]`. When the lambda is read: `LQL-3052` to `LQL-3063`, and `LQL-3066` (`NonSargableException`, see [8.9](#89-warning-functions-on-columns-and-indexes-sargable)) for a condition that cannot use an index with `SargableMode.STRICT`. When the call runs: `LQL-4034` to `LQL-4039`, `LQL-4043`, `LQL-4044`. During a call, `LQL-5004`, `LQL-5005` and `LQL-5006` can also be logged at `INFO` level. For DB functions in conditions, `LQL-5001`, `LQL-5007` (`WARN`) and `LQL-5008` (`INFO`) can be logged. See [11. Errors and languages](../11-errors-and-languages.md).

---

[Kotlin track](README.md) | Previous: [7. Update, insert, delete](07-dml.md) | Next: [9. Kotlin notes](09-kotlin-notes.md) | [Java version of this page](../java/08-procedures-functions.md)
