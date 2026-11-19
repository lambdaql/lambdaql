<a id="8-stored-procedures-and-db-functions-java"></a>
# 8. Stored procedures and DB functions (Java)

> [!IMPORTANT]
> This page is older than the Korean manual. See the [Korean page](../../ko/java/08-procedures-functions.md) for the latest rules.

[Java track](README.md) | [Manual home](../README.md) | Previous: [7. Update, insert, delete](07-dml.md) | Next: [9. Java notes](09-java-notes.md) | [Kotlin version of this page](../kotlin/08-procedures-functions.md)

Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md)

---

This chapter has two parts.

| Part | What | Annotation | How you call it |
|---|---|---|---|
| [8.1 to 8.5](#81-declare-procedures) | **Stored procedures** | `@SqlProcedure` on an abstract interface method | `qb.call(OrderProcs.class, p -> p.calc(id, rate)).execute()` |
| [8.6 to 8.10](#86-declare-db-functions) | **DB functions** (scalar) | `@SqlFunction` on a static interface method | directly inside any lambda: `where(o -> OrderFns.discount(o.getPrice()) > 10)` |

In both cases, you write the signature **once** in a Java interface.
lambdaQL **never runs** these methods. It reads the call in the lambda bytecode and calls the database.

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

Write an interface. Put `@SqlProcedure("NAME")` on each **abstract** method.
You do not write an implementation. lambdaQL does not make one either.

**(tested)** [`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java), region `declare`:

```java
/** One OUT value or more: a record in the database order. */
record Stat(Double total, Integer cnt) {
}

/** The INOUT value and the OUT value, in the database order. */
record Adjusted(Integer balance, String msg) {
}

/** A row of a result set: columns are matched by name (customer_id or customerId). */
record Line(Long id, Double price) {
}

/** A stub interface: lambdaQL reads the calls and never implements it. */
interface OrderProcs {
    @SqlProcedure("SQ_PROC_CALC")
    Double calc(Integer customerId, Double rate);                 // IN, IN, OUT

    @SqlProcedure("SQ_PROC_STAT")
    Stat stat(Integer customerId);                                // IN, OUT, OUT

    @SqlProcedure("SQ_PROC_ADJUST")
    Adjusted adjust(Integer add, @InOut Integer balance);         // IN, INOUT, OUT

    @SqlProcedure("SQ_PROC_RECENT")
    List<Line> recent(Integer customerId);                        // a result set (Oracle: a REF CURSOR)

    @SqlProcedure("SQ_PROC_PURGE")
    void purge(Integer status);                                   // no result

    @SqlProcedure("SQ_PROC_CALC")
    Double calcWrong(Integer customerId);                         // does not match the database: 1 argument
}

/** A database function. */
interface MathFns {
    @SqlFunction("SQ_FN_HALF")
    static Double half(Double value) {
        throw new DbOnlyException();
    }
}
```

The test makes these procedures on MariaDB and Oracle before it runs. An example (MariaDB):

```sql
CREATE OR REPLACE PROCEDURE SQ_PROC_CALC(IN p_cust INT, IN p_rate DOUBLE, OUT p_total DOUBLE)
  BEGIN SELECT COALESCE(SUM(price), 0) * p_rate INTO p_total FROM SQ_orders WHERE customer_id = p_cust; END
```

`calcWrong` is a method declared wrong on purpose. The test uses it to check rule PR2 (see [8.4](#84-warning-argument-order)).

Notes:

- `@SqlProcedure` (or `@SqlFunction`) is **required**. It tells lambdaQL what the method is.
  (The names `@Procedure` and `@Function` were not used. `@Procedure` is also a Spring Data JPA annotation, and `@Function` clashes with Kotlin's `kotlin.Function`.)
- If the value is empty, the name comes from the method name: `calcTotal` → `CALC_TOTAL`.
- `@SqlProcedure` also has `schema` and `pkg` (the Oracle package): `@SqlProcedure(value = "CALC", schema = "LQL", pkg = "SQ_PKG")`.
- Use boxed return types (`Double`, `Integer`). A procedure can return NULL.
- The interface and the result records are a copy of the DB signature. This is the price for type safety. lambdaQL checks part of it against the database (rule PR2).
- You can put procedures (abstract methods) and DB functions (static methods) in **the same interface**.

---

<a id="82-call-procedures"></a>
## 8.2 Call procedures

Use `qb.call(Interface.class, p -> p.method(args))`. Then call `execute()`.

**(tested)** [`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java), region `call` (MariaDB, Oracle):

```java
Double total = qb.call(OrderProcs.class, p -> p.calc(customerId, rate)).execute();
Stat stat = qb.call(OrderProcs.class, p -> p.stat(customerId)).execute();
Adjusted adjusted = qb.call(OrderProcs.class, p -> p.adjust(5, 10)).execute();
List<Line> recent = qb.call(OrderProcs.class, p -> p.recent(customerId)).execute();
```

`customerId` and `rate` are captured local values. The test compares each result with the value computed in Java. For example, `adjusted` is `new Adjusted(15, "ok")`.

Call a procedure with no result (`void`) with `exec`.

**(tested)** [`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java), region `exec` (MariaDB, Oracle):

```java
qb.exec(OrderProcs.class, p -> p.purge(9)).execute();
```

Why `exec` and not `call` for void?

- In Java, `call` for both would be an ambiguous overload (compile error).
- `run` was not chosen. Kotlin has a standard library function with the same name, so a Kotlin caller could call it by mistake without an error.

<a id="what-the-lambda-may-contain-rule-pr12"></a>
### What the lambda may contain (rule PR12)

The lambda body is **one call of a stub method**. Nothing else.

| Allowed | Not allowed |
|---|---|
| `p -> p.calc(id, rate)` | logging, two calls, `if`, nested calls |
| arguments: captured values, constants, calculations of them (`rate * 2`) | entity paths (there is no entity here) |

Other points:

- `exec` with a procedure that has OUT parameters is an error (`LQL-4037`). This stops OUT values from being lost by mistake.
- A call of a method without `@SqlProcedure` is an error (`LQL-3060`).
- Database default values for parameters cannot be used. Pass every IN parameter.
- `null` as an IN value is fine.

---

<a id="83-out-inout-and-result-sets"></a>
## 8.3 OUT, INOUT and result sets

How the return type maps to the procedure (rules PR3, PR4, PR5):

| Procedure has | Return type of the stub method |
|---|---|
| nothing | `void` → call with `qb.exec(...)` |
| 1 OUT parameter | that value (`Double`, `Integer`, ...) |
| many OUT / INOUT parameters | a Java record (Kotlin: a data class) |
| 1 result set (MariaDB `SELECT`, Oracle `SYS_REFCURSOR` OUT, H2 `ResultSet` function) | `List<T>` |
| many result sets | a record with `List` components |
| OUT values and result sets (MariaDB) | a record with scalar and `List` components |

How record components are filled:

- **Scalar components** ← OUT and INOUT parameters (not cursors), in DB order.
- **`List` components** ← result sets, in order (Oracle: order of the cursor OUT parameters, MariaDB: order of the returned result sets).
- If the numbers do not match, lambdaQL throws an error (`LQL-4038`).
- Mixed positions like IN, OUT, IN, INOUT are fine. lambdaQL reads the positions from the DB metadata.

### INOUT

Mark the parameter with `@InOut`. The value you pass goes **in**. The value that comes **out** is a component of the returned record.

In the test, `SQ_PROC_ADJUST(p_add IN, p_balance IN OUT, p_msg OUT)` adds `p_add` to `p_balance`.

```java
@SqlProcedure("SQ_PROC_ADJUST")
Adjusted adjust(Integer add, @InOut Integer balance);         // IN, INOUT, OUT

Adjusted adjusted = qb.call(OrderProcs.class, p -> p.adjust(5, 10)).execute();
// adjusted.balance() is 15 (the new balance), adjusted.msg() is "ok"
```

`@InOut` must be on exactly the arguments that are INOUT in the database. Otherwise the first call fails (`LQL-4036`).

<a id="result-set-element-type-t"></a>
### Result set element type `T`

| `T` is | How lambdaQL reads it |
|---|---|
| an entity | Hibernate result class (`StoredProcedureQuery`) |
| a Java record (Kotlin: a data class) | JDBC `CallableStatement`, columns matched **by label**. `_` is removed and case is ignored, so `customer_id` matches `customerId` (tested with `Line`) |
| one value (`Long`, `String`, ...) | the first column of each row |

A normal DTO class cannot be a row type here. Use a record.
MariaDB and Oracle return the columns of `SELECT *` in different orders, so lambdaQL does not map record components by position.
It converts common types (for example, a number to `Long`/`Integer`, a timestamp to `LocalDateTime`).

Limits:

- Paging a procedure result is not possible.
- Oracle implicit results (`DBMS_SQL.RETURN_RESULT`) are not supported. Use a `SYS_REFCURSOR` OUT parameter.

<a id="transactions-rule-pr9"></a>
### Transactions (rule PR9)

- DML inside a procedure is part of the calling transaction. It rolls back with it.
- Before the call, lambdaQL flushes the persistence context, but only if a transaction is active and there are changes.
- `QueryBuilder(EntityManagerFactory)` runs the call in its own transaction. A `QueryBuilder` with an `EntityManager` (also Spring's shared `EntityManager`) uses the caller's transaction. Without a transaction, the call is autocommit.
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
> **The order of the stub method parameters must be the same as the order in the database.**

lambdaQL binds arguments **by position** (rule PR2).
Hibernate 6.6.5 binds by position even when names are registered.
The MariaDB and H2 drivers do not support named parameters.
So if the order is wrong, you can get a **wrong result without any error**.

These are the checks lambdaQL makes on the first call, with the JDBC metadata (`getProcedureColumns`). The result is cached per procedure name.

| Check | Found? |
|---|---|
| the procedure exists | yes, error `LQL-4035` |
| number of IN and INOUT parameters = number of stub arguments | yes, error `LQL-4036` (tested) |
| INOUT parameters = arguments with `@InOut` | yes, error `LQL-4036` |
| number of OUT values and result sets = parts of the return type | yes, error `LQL-4038` |
| the kind of type (number, text, date/time, boolean, binary) | yes, error `LQL-4036` (tested). Differences within one kind are not checked (see "Argument type check" below) |
| the same parameter names in another order | yes, error `LQL-4043` (tested). Only when the stub argument names are known (see below) |
| an Oracle package procedure name with several overloads | yes, error `LQL-4044` (tested on Oracle) |

**(tested)** [`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java), region `rules` (MariaDB, Oracle):

```java
// PR2: the stub is checked against the database parameters on the first call
assertEquals("LQL-4036", assertThrows(LambdaQLException.class,
        () -> qb.call(OrderProcs.class, p -> p.calcWrong(1)).execute()).code());
// exec cannot drop OUT values
assertEquals("LQL-4037", assertThrows(LambdaQLException.class,
        () -> qb.exec(OrderProcs.class, p -> p.calc(1, 1.0)).execute()).code());
// only @SqlProcedure methods of the stub can be called
assertEquals("LQL-3060", assertThrows(LambdaQLException.class,
        () -> qb.call(OrderProcs.class, p -> p.toString()).execute()).code());
```

The last case calls `toString()`, which has no `@SqlProcedure`. So the code is `LQL-3060` (the method is not a `@SqlProcedure`).

An example of the dangerous case:

```java
// database: SP_TRANSFER(p_to NUMBER, p_from NUMBER)
@SqlProcedure("SP_TRANSFER") void transfer(Long from, Long to);   // swapped! same types
```

The count, modes and types all match. Only the names can show the mistake.
So lambdaQL compares the names of the IN and INOUT arguments on the first call.

- The stub argument names come from the class file. They are there only when you compile with `-parameters` (see [2. Getting started](02-getting-started.md#recommended-compiler-option--parameters)). `@org.lambdaql.DbParam("p_to")` on a stub argument gives the database name directly.
- From the database names, lambdaQL removes a prefix that ends with `_` and that **all** of them share (`p_`, `in_`, `v_` and so on). No fixed prefix is assumed. Then it ignores case and `_`.
- If the names are the same and only the order differs, `LambdaQLException` `LQL-4043` is thrown before the call.
- If the names differ (the stub follows another naming rule), it is not an error. Add `@DbParam` so that the check works.
- If the class file has no names and two neighbouring arguments have the same type, lambdaQL logs `LQL-5004` at `INFO` level, once per procedure.
- Types are checked by kind only (see "Argument type check" below). Two arguments of the same kind in swapped order can be found only by their names.

**(tested)** [`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java), region `names` (the database procedure is `SQ_PROC_DIFF(p_to, p_from)`. The test stubs are compiled with `-parameters`):

```java
/** Argument names: with -parameters lambdaQL compares them with the database names (p_to, p_from). */
interface DiffProcs {
    @SqlProcedure("SQ_PROC_DIFF")
    Integer diff(Integer to, Integer from);                       // same order as the database: runs

    @SqlProcedure("SQ_PROC_DIFF")
    Integer swapped(Integer from, Integer to);                    // same names, other order: LQL-4043

    @SqlProcedure("SQ_PROC_DIFF")
    Integer named(@DbParam("p_to") Integer target, @DbParam("p_from") Integer source);   // another naming rule

    @SqlProcedure("SQ_PROC_DIFF")
    Integer text(String to, Integer from);                        // a string for a number parameter: LQL-4036
}
```

**(tested)** [`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java), region `name-check` (MariaDB, Oracle):

```java
assertEquals(7, qb.call(DiffProcs.class, p -> p.diff(10, 3)).execute());
assertEquals(7, qb.call(DiffProcs.class, p -> p.named(10, 3)).execute());
// the same names in another order: stopped before the call (it would run with the values swapped)
assertEquals("LQL-4043", assertThrows(LambdaQLException.class,
        () -> qb.call(DiffProcs.class, p -> p.swapped(3, 10)).execute()).code());
```

**Advice:** compile with `-parameters` and use the same names as the database (the shared prefix may be left out), or add `@DbParam`. Then a swapped order is found.

**Argument type check.** On the first call, lambdaQL compares the kind of each argument's Java type with the kind of the DB parameter type.
There are five kinds: number, text, date/time, boolean and binary. If the kinds differ, lambdaQL stops with `LQL-4036` before the call.

- A boolean argument also matches a number parameter. MariaDB's `BOOLEAN` is `TINYINT(1)`, and Oracle before 23 has no `BOOLEAN` type.
- A side whose kind cannot be decided (enum, object type, cursor) is not checked.
- Differences within one kind (for example, an `Integer` argument and a `NUMBER(10,2)` parameter) are left to the driver's conversion.

**(tested)** [`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java), region `type-check` (MariaDB, Oracle):

```java
// the kind of each argument is compared with the database parameter: a string for a number parameter stops
LambdaQLException wrong = assertThrows(LambdaQLException.class,
        () -> qb.call(DiffProcs.class, p -> p.text("10", 3)).execute());
assertEquals("LQL-4036", wrong.code(), wrong.getMessage());
```

**Oracle package overloads.** An Oracle package can hold several procedures with the same name.
lambdaQL cannot tell which one the stub means, so a call to such a name stops with `LQL-4044`. Choosing one overload to call is planned for later.

**(tested)** [`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java), region `overload` (Oracle only). The stub `PkgProcs` declares `@SqlProcedure(pkg = "SQ_PKG", value = "TWICE")` and `@SqlProcedure(pkg = "SQ_PKG", value = "HALF")`:

```java
assertEquals(5.0, qb.call(PkgProcs.class, p -> p.twice(2.5)).execute(), 1e-9);
// HALF has two overloads in the package: lambdaQL cannot tell which one to call
assertEquals("LQL-4044", assertThrows(LambdaQLException.class,
        () -> qb.call(PkgProcs.class, p -> p.half(10.0)).execute()).code());
```

<a id="when-the-metadata-cannot-be-read"></a>
**When the metadata cannot be read.** A database user may have no permission to read the dictionary views or `information_schema`.
If the metadata lookup itself fails (an `SQLException`), lambdaQL does not stop the call.

- It logs `LQL-5005` at `INFO` level, once per procedure, and does not check the stub.
- It takes the argument layout from the stub: the IN and INOUT arguments in stub order, then the OUT parameters (in the order of the result parts), then on Oracle one `REF CURSOR` for each `List` part. So without metadata, the database declaration must put the OUT parameters after the IN parameters.
- The checks that need the metadata are skipped: the count and `@InOut` checks (`LQL-4036`) and the name order check (`LQL-4043`).
- If the lookup works but finds no procedure, the error is still `LQL-4035`.
- If only the Oracle overload lookup (`all_arguments`) fails, lambdaQL logs `LQL-5006` at `INFO` level and calls without the overload check.

This fallback is tested on MariaDB and Oracle (test method `withoutMetadata` in `ProcedureTest`, which simulates the failure).

Real name binding on Oracle (`pass_procedure_paramater_names=true` together with the stub parameter names) is planned too.

---

<a id="85-procedure-rules-pr1-to-pr15-summary"></a>
## 8.5 Procedure rules PR1 to PR15 (summary)

| # | Rule | Status |
|---|---|---|
| PR1 | Name: the annotation value as is. Empty value: method name in UPPER_SNAKE. Package or schema names (`SQ_PKG.CALC`, `LQL.SQ_CALC`) work too. To avoid confusion, you can also write `@SqlProcedure(schema = ..., pkg = ..., value = ...)`. | Implemented. Schema and package names are not covered by a test |
| PR2 | Arguments are bound **by position**. On the first call, the count of IN/INOUT parameters and the `@InOut` positions are checked against the DB metadata. Names are checked too when they are known, and the same names in another order is an error. The kind of each argument type (number, text, date/time, boolean, binary) is checked too, and a difference is an error (`LQL-4036`, see [8.4](#84-warning-argument-order)). If the metadata cannot be read, the checks are skipped and the argument layout comes from the stub (see [8.4](#when-the-metadata-cannot-be-read)). | Count, `@InOut` and name checks implemented (tested). Detection of Oracle package overloads implemented too (tested on Oracle). Type check implemented too (tested on MariaDB and Oracle). The fallback without metadata (`LQL-5005`, `LQL-5006`) implemented (tested on MariaDB and Oracle). |
| PR3 | One OUT → return value. Many OUT/INOUT → record. `@InOut` marks INOUT parameters. | Implemented (tested) |
| PR4 | One result set → `List<T>`. Entities are read through Hibernate, records through JDBC with label matching. No paging. | Implemented (tested with a record) |
| PR5 | Many result sets → `List` components of a record, in order. Oracle implicit results are not supported. | Implemented, not covered by a test |
| PR6 | A DB function called alone uses a different path per database (see [8.8](#88-call-a-db-function-alone)). | Implemented (tested) |
| PR7 | DB functions in queries: see [8.6](#86-declare-db-functions) and rules FN1 to FN11. | Implemented (tested) |
| PR8 | A DB function on a column in a condition cannot use the index, so lambdaQL logs a WARN (see [8.9](#89-warning-functions-on-columns-and-indexes-sargable)). The code is `LQL-5001`. | Implemented (tested). An error with `SargableMode.STRICT` (`LQL-3066`, D50) |
| PR9 | The procedure runs in the calling transaction. lambdaQL flushes before the call when needed. Loaded entities can be stale after the call. | Implemented |
| PR10 | No common affected-row count. Use an OUT parameter. | – |
| PR11 | `null` IN values are fine. DB parameter defaults cannot be used. | Implemented |
| PR12 | The lambda is one stub call. Arguments are captured values, constants or calculations of them. `exec` with OUT parameters is an error. | Implemented (tested) |
| PR13 | Boolean parameters work on MariaDB and Oracle 23ai or later. | Not covered by a test |
| PR14 | Each lambda is analyzed once. Procedure metadata is read once per name. | Implemented |
| PR15 | H2 has no OUT, INOUT, many result sets or packages. Test procedures on MariaDB or Oracle (for example, with Testcontainers). | The lambdaQL tests follow this |

PR11, PR12 and PR13 have extra Kotlin parts (default arguments, named arguments, nullable types). They are in the [Kotlin version of this page](../kotlin/08-procedures-functions.md).

---

<a id="86-declare-db-functions"></a>
## 8.6 Declare DB functions

A DB function is a function you made in the database, for example `FN_DISCOUNT(price)`.
Declare it as a **static method of an interface**, with `@SqlFunction`.
The body must only throw. It never runs inside lambdaQL.

**(tested)** [`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java), region `declare`:

```java
/** Database functions: static methods of your own interface. The bodies only throw. */
public interface OrderFns {
    /** {@code SQ_FN_DISCOUNT(price)}: 90% of the price. */
    @SqlFunction("SQ_FN_DISCOUNT")
    static Double discount(Double price) {
        throw new DbOnlyException();
    }

    /** {@code SQ_FN_IS_GOLD(grade)}: a grade of 5 or more. */
    @SqlFunction("SQ_FN_IS_GOLD")
    static Boolean isGold(Integer grade) {
        throw new DbOnlyException();
    }

    /** A query name with its own SQL name; registered with lambdaql.functions.classes. */
    @SqlFunction(value = "SQ_TAX", sql = "SQ_FN_DISCOUNT")
    static Double tax(Double price) {
        throw new DbOnlyException();
    }
}
```

The test makes `SQ_FN_DISCOUNT` and `SQ_FN_IS_GOLD` in the database before it runs, and drops them at the end. A Boolean result needs Oracle 23ai or later.

If you call `OrderFns.discount(...)` **outside** a lambdaQL lambda, it throws `DbOnlyException` (`LQL-4034`). This is on purpose.

`@SqlFunction` has two values.

| Value | Meaning |
|---|---|
| `value` | The function name in the query. Empty: the method name in UPPER_SNAKE (`isVip` → `IS_VIP`). A schema name is allowed (`PUBLIC.SQ_FN`) |
| `sql` | The SQL name, when it differs from `value`. Such a function must be registered (see [Registration](#registration-optional)) |

Other ways to declare:

| Declaration | Status |
|---|---|
| interface static method | **recommended** (tested) |
| class static method | designed, not covered by a test |
| interface default method + a singleton constant | designed, not covered by a test |
| your repository `implements` the function interface (mixin) | rejected |
| functional interface constant (`DISCOUNT.apply(x)`) | rejected |

Call the function **with the interface name** (`OrderFns.discount`). A static import works too, but a member method with the same name can hide it.

---

<a id="87-use-db-functions-in-queries"></a>
## 8.7 Use DB functions in queries

Call the function directly. No context argument is needed.
It works in `where`, `select`, `orderBy` and `set` (tested). It is also designed to work in `groupBy`, `having` and subqueries, but this is not covered by a test.

**(tested)** [`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java), region `where`:

```java
// the function call is written as function('<name>' as <type>, ...); the column is its argument
SelectQuery<Order> q = qb.from(Order.class).where(o -> OrderFns.discount(o.getPrice()) > 100);
assertEquals("select e0 from Order e0 where function('SQ_FN_DISCOUNT' as Double, e0.price) > 100.0", q.toHql().hql());
```

**(tested)** [`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java), region `captured`:

```java
// a function of captured values only is still computed by the database (the value is bound)
SelectQuery<Order> q = qb.from(Order.class).where(o -> o.getPrice() > OrderFns.discount(base));
assertEquals("select e0 from Order e0 where e0.price > function('SQ_FN_DISCOUNT' as Double, :p1)", q.toHql().hql());
```

A Boolean function can be the whole condition. lambdaQL adds `= true`.

**(tested)** [`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java), region `boolean`:

```java
SelectQuery<Order> q = qb.from(Order.class).where(o -> OrderFns.isGold(o.getCustomer().getGrade()));
assertEquals("select e0 from Order e0 join e0.customer j1 where function('SQ_FN_IS_GOLD' as Boolean, j1.grade) = true",
        q.toHql().hql());
```

**(tested)** [`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java), region `select` (`select` and `orderBy`):

```java
ValueQuery<Priced> q = qb.from(Order.class)
        .orderBy((o, by) -> by.desc(OrderFns.discount(o.getPrice())))
        .select(o -> new Priced(o.getId(), OrderFns.discount(o.getPrice())));
assertEquals("select e0.id, function('SQ_FN_DISCOUNT' as Double, e0.price) from Order e0"
        + " order by function('SQ_FN_DISCOUNT' as Double, e0.price) desc nulls last", q.toHql().hql());
```

`Priced` is `record Priced(Long id, Double discounted)`.

**(tested)** [`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java), region `update`:

```java
ExecutableUpdate<Order> u = qb.update(Order.class)
        .set(o -> o.setPrice(OrderFns.discount(o.getPrice())))
        .where(o -> o.getPrice() > 100);
assertEquals("update Order e0 set e0.price = function('SQ_FN_DISCOUNT' as Double, e0.price) where e0.price > 100.0",
        u.toHql().hql());
```

The test runs each query and compares the rows with the same computation in Java (`price * 0.9`).

**About the SQL blocks.** `…` is the shortened select list (all entity columns), and `?` is a bind parameter. [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java) gives an HQL of the same form (with the name `FN_DISCOUNT`) to Hibernate directly and records the SQL on H2 2.3, MariaDB 10.11 and Oracle 23.26 (Hibernate 6.6.5). The test fails if Hibernate's SQL changes.

**SQL by database (Hibernate's SQL for this HQL form)** — recorded by `PlannedSqlTest`. From `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql), example `08-function`:

```sql
-- H2, MariaDB, Oracle (same)
-- HQL: select o from Order o where function('FN_DISCOUNT' as Double, o.price) > 10
select …
from orders o1_0
where FN_DISCOUNT(o1_0.price)>10
```

The test setup has the setting `lambdaql.functions.classes`. With it, Hibernate keeps the case of the name (`FN_DISCOUNT`), also for a function that is not registered (seen on all three databases, the reason is not confirmed yet).
Without any registration, Hibernate can write the name in lower case. An unquoted name works either way.

<a id="how-lambdaql-renders-it"></a>
### How lambdaQL renders it

lambdaQL **always** renders a DB function as follows.

```sql
function('<NAME>' as <Type>, arg1, arg2, ...)
```

`<Type>` comes from the declared return type. Only these types are allowed.

| Allowed return types |
|---|
| `Double`, `Integer`, `Long`, `BigDecimal`, `String`, `LocalDate`, `LocalDateTime`, `Boolean` |

Generic returns (`<X> X nvl(X, X)`), entities, collections and `void` are rejected at analysis time.

<a id="registration-optional"></a>
### Registration (optional)

Registration is not needed for the `function('FN_DISCOUNT' as Double, ...)` form (checked on the three databases).
If you register your function interfaces, Hibernate also keeps the name case and checks the argument count and types.

lambdaQL has a Hibernate `FunctionContributor`, `org.lambdaql.hibernate.LambdaQlFunctionContributor` (found by ServiceLoader). You list the interfaces in a Hibernate property.

```properties
# plain Hibernate
lambdaql.functions.classes=com.acme.OrderFns,com.acme.CustomerFns

# Spring Boot
spring.jpa.properties.lambdaql.functions.classes=com.acme.OrderFns
```

If the property is not set, nothing is registered. Package scanning is not in v0.1.
For a nested interface, write the binary name with `$`. The test setup has `lambdaql.functions.classes: org.lambdaql.query.FunctionTest$OrderFns`.

<a id="a-different-sql-name-needs-registration"></a>
### A different SQL name needs registration

`value` is the name in the query, and `sql` is the name in SQL. When the two names differ, the function must be registered.
This is useful for a function name made with quotes and mixed case (`"SQ_Fn_Mixed"`). Without registration, the name can become lower case on H2 and Oracle.

```java
@SqlFunction(value = "SQ_Fn_Mixed", sql = "\"SQ_Fn_Mixed\"")
static Double mixed(Double v) { throw new DbOnlyException(); }
```

**(tested)** [`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java), region `registered`:

```java
// SQ_TAX is registered at start-up (lambdaql.functions.classes) and runs SQ_FN_DISCOUNT in SQL
SelectQuery<Order> q = qb.from(Order.class).where(o -> OrderFns.tax(o.getPrice()) > 100);
assertEquals("select e0 from Order e0 where function('SQ_TAX' as Double, e0.price) > 100.0", q.toHql().hql());
```

If such a function is not registered, lambdaQL fails when it reads the lambda (`LQL-3058`, see [8.10](#810-function-rules-fn1-to-fn11-summary)).

---

<a id="88-call-a-db-function-alone"></a>
## 8.8 Call a DB function alone

Use `qb.call(() -> ...)` with a **lambda without arguments**.

**(tested)** [`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java), region `function` (H2, MariaDB, Oracle):

```java
Double half = qb.call(() -> MathFns.half(price)).execute();
```

`price` is a captured `double` (150), and `half` is 75.0. The lambda must be one `@SqlFunction` call (`LQL-3063`).

**SQL by database (Hibernate's SQL for this HQL form)** — recorded by `PlannedSqlTest` with the name `FN_DISCOUNT`. From `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql), example `08-function-alone`:

```sql
-- H2, MariaDB, Oracle (same)
-- HQL: select function('FN_DISCOUNT' as Double, :p1)
select FN_DISCOUNT(?)
```

This query runs as `select FN_DISCOUNT(?)` with no FROM. Oracle accepts this only from 23ai on. On Oracle, lambdaQL uses a JDBC function call instead (see the table below).

There is no conflict with `qb.call(OrderProcs.class, p -> ...)`, because the number of arguments is different.

How lambdaQL runs it (rule PR6):

| Database | Path |
|---|---|
| Oracle | a JDBC function call `{? = call FN(?)}`. This also works for functions that do DML and for functions that return a cursor. |
| MariaDB, H2 | HQL `select function('FN' as T, :p1)` |

lambdaQL flushes pending changes first (as in PR9). So the function sees your latest changes.

---

<a id="89-warning-functions-on-columns-and-indexes-sargable"></a>
## 8.9 Warning: functions on columns and indexes (SARGable)

lambdaQL tries to make SQL that **can use an index** (see [10. Rules and limits](10-rules-and-limits.md#102-sargable-rule-index-friendly-sql)).
A function around a column usually **cannot** use a normal index.

```java
.where(o -> OrderFns.discount(o.getPrice()) > 10)
// → function('FN_DISCOUNT' as Double, e0.price) > 10   -- does not use the index on price
```

**SQL by database (Hibernate's SQL for this HQL form)** — recorded by `PlannedSqlTest`. From `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql), example `08-function`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where FN_DISCOUNT(o1_0.price)>10
```

So lambdaQL logs a **WARN** for this form.
The check runs once per `where` lambda, when its analysis is cached. A condition without an `@SqlFunction` needs no database lookup.

| Case | Code | H2 | MariaDB | Oracle |
|---|---|---|---|---|
| ① A function wraps a column: `fn(o.price) > 10`, a Boolean function on a column, a function inside `IN` or `IS NULL` | `LQL-5001` (WARN) | WARN | WARN (a stored function cannot be used in an index) | WARN. **But** no log if `user_ind_expressions` has a function-based index with the same expression `FN(COLUMN)` on that table |
| ② A function on the value side, compared with a column: `o.price > fn(rate)` | `LQL-5007` (WARN) | not checked | WARN when `information_schema.routines.is_deterministic = 'NO'` (the default!). Then MariaDB scans the full index | not checked (Oracle uses a range scan either way) |

- For the Oracle index match, lambdaQL takes the table and column names from the Hibernate mapping. Only functions with one column argument are matched.
- If the metadata lookup fails (for example, without permission), lambdaQL logs `LQL-5008` at `INFO` level. Case ① still warns, and case ② is not checked.

**(tested)** [`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java), region `sargable` (the test captures the log: H2 gives `LQL-5001`, MariaDB gives `LQL-5001` and `LQL-5007`, and Oracle with a function-based index on `SQ_FN_DISCOUNT(price)` gives none):

```java
// the function wraps the column: an index on price cannot be used (LQL-5001 at WARN),
// unless Oracle has a function-based index on SQ_FN_DISCOUNT(price)
qb.from(Order.class).where(o -> OrderFns.discount(o.getPrice()) > 100).toHql();
// the function is on the value side: fine, unless MariaDB says NOT DETERMINISTIC (LQL-5007 at WARN)
qb.from(Order.class).where(o -> o.getPrice() > OrderFns.nondet(base)).toHql();
```

**SARGable mode** (decision D50). `SargableMode` has three values.

- `OFF`: no check, and no metadata lookup.
- `WARN` (default): logs warnings.
- `STRICT`: in the cases where `WARN` would log a warning (`LQL-5001`, `LQL-5007`, `LQL-5012`), the analysis fails with `org.lambdaql.NonSargableException` (`LQL-3066`).

Set the mode per persistence unit with `SargablePolicy.register(emf, SargableMode.STRICT)`, and remove it with `SargablePolicy.unregister(emf)`. In Spring, use `lambdaql.sargable.mode=STRICT` (see [12](12-spring-data-jpa.md)).
The check runs once per lambda, when its analysis is put into the shared cache. So register the mode before the first query.

**Arithmetic on columns** (decision D50).

- `+ - * /` with one column and values only (`o.getCnt() * 2 > n`) can be moved to the value side. lambdaQL logs `LQL-5012` at `WARN` level. With `STRICT` it is an error.
- Arithmetic between columns (`o.getPrice() * o.getCnt() > n`) and `%` cannot be moved, so lambdaQL logs `LQL-5013` only at `DEBUG` level. `STRICT` allows it too.
- The moved form must have the same meaning. Divide by a decimal, as in `o.getCnt() > n / 2.0`. The integer division `n / 2` gives other rows with `>=`, `=`, `<` and with a negative `n`. If you multiplied by a negative number, turn the comparison around. The `LQL-5012` message explains this.
- Looking up whether an index exists, to log fewer warnings, was not adopted. Because of permissions and synonyms, "no index" and "unknown" get mixed up, and arithmetic on columns is a rare form.

**(tested)** [`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java), region `sargable-strict`:

```java
// STRICT: what WARN would log fails the analysis (NonSargableException, LQL-3066)
SargablePolicy.register(emf, SargableMode.STRICT);
NonSargableException fn = assertThrows(NonSargableException.class,
        () -> qb.from(Order.class).where(o -> OrderFns.discount(o.getTotal()) > 100).toHql());
assertEquals("LQL-3066", fn.code());
assertThrows(NonSargableException.class, () -> qb.from(Order.class).where(o -> o.getCnt() * 2 > n).toHql());
// the same meaning on the value side uses the index; divide by a decimal (n / 2 is integer division)
var moved = qb.from(Order.class).where(o -> o.getCnt() > n / 2.0).toHql();
assertEquals("select e0 from Order e0 where e0.cnt > :p1", moved.hql());
assertEquals(Map.of("p1", new java.math.BigDecimal("2.5")), moved.parameters());   // 2.5, never cut to 2
// arithmetic between columns cannot move, so STRICT allows it
qb.from(Order.class).where(o -> o.getCnt() * o.getStatus() > n).toHql();
```

**Advice:**

- Put the function on the value side when you can: `o.getPrice() > OrderFns.discount(base)`.
- On MariaDB, declare your function `DETERMINISTIC` if it really is deterministic.
- On Oracle, a function-based index on the same expression removes the warning (such an index needs a `DETERMINISTIC` function).

---

<a id="810-function-rules-fn1-to-fn11-summary"></a>
## 8.10 Function rules FN1 to FN11 (summary)

| # | Rule | Status |
|---|---|---|
| FN1 | `@SqlFunction` is a method annotation. Empty value: method name in UPPER_SNAKE. | Implemented |
| FN2 | lambdaQL finds the called method and reads its annotation. The method body must only throw (`throw new ...`). A method with a real body is an error (`LQL-3052`), because Java and the database could give different values. | Implemented (tested) |
| FN3 | All overloads with the same name must map to the same DB function. Otherwise it is an error (`LQL-3053`). | Implemented |
| FN4 | A call without the annotation, or a call lambdaQL cannot identify, is an error. It is never skipped silently. | Implemented |
| FN5 | Arguments follow the value rules of [3. Conditions](03-where.md). Even an argument made only from captured values is bound and evaluated **in the database**. | Implemented (tested) |
| FN6 | Boxing calls are skipped. The return type comes from the declared method. It must be one of `Double`, `Integer`, `Long`, `BigDecimal`, `String`, `LocalDate`, `LocalDateTime`, `Boolean` (`LQL-3055`). | Implemented (tested) |
| FN7 | A function on a column gives a WARN (see [8.9](#89-warning-functions-on-columns-and-indexes-sargable)). On MariaDB, a `NOT DETERMINISTIC` function on the value side gives a WARN too (`LQL-5007`). | Implemented (tested). An error with `SargableMode.STRICT` (`LQL-3066`, D50) |
| FN8 | A name equal to a Hibernate built-in function (`COALESCE`, `ROUND`, `SYSDATE`, ...) is an error (`LQL-3054`), because Hibernate would silently use the built-in. Use a prefix or a schema name (`PUBLIC.SQ_FN`). | Implemented (tested) |
| FN9 | Kotlin only (default arguments, `inline`, `vararg`). See the Kotlin track. | Default arguments and `vararg` implemented. `inline` designed |
| FN10 | v0.1 has scalar functions only. User aggregate and window functions are planned for v0.2. A function without arguments renders as `fn()`. | Scalar functions implemented |
| FN11 | Known DB differences: on MariaDB, a function that returns VARCHAR can have a collation conflict with a string literal. The scale of `BigDecimal` results differs (MariaDB and Oracle 0, H2 1). Boolean results need Oracle 23ai or later. | Measured with prototypes |

**(tested)** [`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java), region `rules`:

```java
// the body never runs in Java
assertEquals("LQL-4034", assertThrows(DbOnlyException.class, () -> OrderFns.discount(1.0)).code());
// FN2: a body that computes a value is rejected (Java and the database could give different values)
assertEquals("LQL-3052", assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(o -> BadFns.computes(o.getPrice()) > 1)).code());
// FN8: a Hibernate built-in name would silently run the built-in function
assertEquals("LQL-3054", assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(o -> BadFns.builtin(o.getPrice()) > 1)).code());
// a function with its own SQL name must be registered
assertEquals("LQL-3058", assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(o -> BadFns.unregistered(o.getPrice()) > 1)).code());
// the return type must have an HQL type
assertEquals("LQL-3055", assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(o -> BadFns.anything(o.getPrice()) != null)).code());
```

`BadFns` is an interface in the test with one broken function for each case. There are four: a body that returns `price`, the name `COALESCE`, `value = "SQ_OTHER"` with `sql = "SQ_FN_DISCOUNT"` (not registered), and the return type `Object`.

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

Not planned for v0.1: a string builder for dynamic procedure names (v0.2 candidate), calling one overload of an Oracle package procedure (overload detection is implemented, `LQL-4044`), table-valued functions, paging of procedure results.

Errors from this chapter are `LambdaQLException` (or its subclass) with a `[LQL-xxxx]` code: `LQL-3052` to `LQL-3063` when lambdaQL reads the lambda, plus `LQL-3066` (`NonSargableException`, see [8.9](#89-warning-functions-on-columns-and-indexes-sargable)) for a condition that cannot use an index under `SargableMode.STRICT`. They are `LQL-4034` to `LQL-4039`, `LQL-4043` and `LQL-4044` when the call runs. During the call, lambdaQL can also log `LQL-5004`, `LQL-5005` or `LQL-5006` at `INFO` level. For DB functions in a condition, lambdaQL can log `LQL-5001`, `LQL-5007` (`WARN`) and `LQL-5008` (`INFO`). See [11. Errors and languages](../11-errors-and-languages.md).

---

[Java track](README.md) | Previous: [7. Update, insert, delete](07-dml.md) | Next: [9. Java notes](09-java-notes.md) | [Kotlin version of this page](../kotlin/08-procedures-functions.md)
