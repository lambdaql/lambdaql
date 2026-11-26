<a id="8-stored-procedures-and-db-functions-java"></a>
# 8. Stored procedures and DB functions (Java)

[Java track](README.md) | [Manual home](../README.md) | Previous: [7. Update, insert, delete](07-dml.md) | Next: [9. Java notes](09-java-notes.md) | [Kotlin version of this page](../kotlin/08-procedures-functions.md)

Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md)

---

This chapter has two parts.

| Part | Target | Annotation | How to call |
|---|---|---|---|
| [8.1 to 8.5](#81-declare-procedures) | **Stored procedures** | `@SqlProcedure` on abstract methods of an interface | `qb.call(OrderProcs.class, p -> p.calc(id, rate)).execute()` |
| [8.6 to 8.10](#86-declare-db-functions) | **DB functions** (scalar) | `@SqlFunction` on static methods of an interface | Directly inside any lambda expression: `where(o -> OrderFns.discount(o.getPrice()) > 10)` |

In both cases, you write the signature **only once**, in a Java interface.
lambdaQL **does not call** these methods in Java. It reads the calls from the bytecode opcodes of the lambda expression and calls the procedure or function in the database.

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
You do not write an implementation. lambdaQL does not create one either.

<!-- region: ManualJavaFunctionTest.java#declare-procs -->
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

The `call`, `exec` and `rules` examples below use this declaration.
The database must have the matching procedures. For example, MariaDB's `SQ_PROC_CALC(IN p_cust INT, IN p_rate DOUBLE, OUT p_total DOUBLE)` multiplies the sum of `price` of a customer by `p_rate` and puts it into `p_total`.

`calcWrong` is a method declared wrong on purpose, to show the argument order check (see [8.4](#84-warning-argument-order)).

Notes:

- `@SqlProcedure` (or `@SqlFunction`) is **required**. It tells lambdaQL what this method is.
  (The names `@Procedure` and `@Function` were not used. `@Procedure` is also a Spring Data JPA annotation, and `@Function` clashes with Kotlin's `kotlin.Function`.)
- If the value is empty, the name is made from the method name: `calcTotal` → `CALC_TOTAL`.
- `@SqlProcedure` also has `pkg` (an Oracle package): `@SqlProcedure(pkg = "SQ_PKG", value = "TWICE")` (see [8.4](#84-warning-argument-order)).
- Use boxed types (`Double`, `Integer`) as return types. A procedure can return NULL.
- The interface and the result records are copies of the database signature. This is the price of type safety. lambdaQL checks part of it against the database.
- You can put procedures (abstract methods) and DB functions (static methods) in the **same interface**.

Example code: [`ManualJavaFunctionTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaFunctionTest.java) (`declare-procs`).

---

<a id="82-call-procedures"></a>
## 8.2 Call procedures

Use `qb.call(Interface.class, p -> p.method(args))`. Then call `execute()`.

MariaDB, Oracle:

<!-- region: ProcedureTest.java#call -->
```java
Double total = qb.call(OrderProcs.class, p -> p.calc(customerId, rate)).execute();
Stat stat = qb.call(OrderProcs.class, p -> p.stat(customerId)).execute();
Adjusted adjusted = qb.call(OrderProcs.class, p -> p.adjust(5, 10)).execute();
List<Line> recent = qb.call(OrderProcs.class, p -> p.recent(customerId)).execute();
```

`customerId` and `rate` are captured local values. For example, `adjusted` is `new Adjusted(15, "ok")`.

Call a procedure with no result (`void`) with `exec`.

MariaDB, Oracle:

<!-- region: ProcedureTest.java#exec -->
```java
qb.exec(OrderProcs.class, p -> p.purge(9)).execute();
```

Why `exec` and not `call` for void?

- `run` was not chosen. Kotlin has a standard library function with the same name, so a Kotlin caller could call that function by mistake without an error.

<a id="what-the-lambda-may-contain-rule-pr12"></a>
### What the lambda expression may contain (rule PR12)

The lambda expression body is **one stub method call**. Nothing else.

| Allowed | Not allowed |
|---|---|
| `p -> p.calc(id, rate)` | Logging, two calls, `if`, nested calls |
| Arguments: captured values, constants, computations of them (`rate * 2`) | Entity paths (there is no entity here) |

Other points:

- `exec` on a procedure with OUT parameters is an error (`LQL-4037`). This keeps you from losing OUT values by mistake.
- Calling a method without `@SqlProcedure` is an error (`LQL-3060`).
- Database default values of parameters cannot be used. Pass all IN parameters.
- You can pass `null` as an IN value.

Example code: [`ProcedureTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java) (`call`, `exec`).

---

<a id="83-out-inout-and-result-sets"></a>
## 8.3 OUT, INOUT and result sets

How the return type maps to the procedure:

| What the procedure has | Return type of the stub method |
|---|---|
| Nothing | `void` → call with `qb.exec(...)` |
| 1 OUT parameter | That value (`Double`, `Integer`, ...) |
| Many OUT / INOUT parameters | A Java record (Kotlin: data class) |
| 1 result set (MariaDB `SELECT`, Oracle `SYS_REFCURSOR` OUT, H2 `ResultSet` function) | `List<T>` |

How the record components are filled:

- **Scalar components** ← OUT and INOUT parameters (except cursors), in database order.
- If the counts do not match, lambdaQL throws an error (`LQL-4038`).
- Mixed positions like IN, OUT, IN, INOUT are fine. lambdaQL reads the positions from the database metadata.

### INOUT

Put `@InOut` on the parameter. The value you pass is the value that goes **in**. The value that comes **out** is a component of the returned record.

`SQ_PROC_ADJUST(p_add IN, p_balance IN OUT, p_msg OUT)` in the example adds `p_add` to `p_balance`.

`adjust(Integer add, @InOut Integer balance)` in the declaration above has this form. In the `call` example of 8.2, `p.adjust(5, 10)` returns `new Adjusted(15, "ok")`. `balance()` is the new balance 15, and `msg()` is `"ok"`.

Put `@InOut` exactly on the arguments that are INOUT in the database. Otherwise the first call fails (`LQL-4036`).

<a id="result-set-element-type-t"></a>
### Result set element type `T`

| If `T` is | How lambdaQL reads it |
|---|---|
| An entity | Hibernate result class (`StoredProcedureQuery`) |
| A Java record (Kotlin: data class) | JDBC `CallableStatement`, columns matched **by label**. `_` is removed and case is ignored, so `customer_id` matches `customerId` |
| One value (`Long`, `String`, ...) | The first column of each row |

A normal DTO class cannot be used as a row type here. Use a record.
MariaDB and Oracle return the columns of `SELECT *` in different orders, so lambdaQL does not match record components by position.
Common types are converted (for example, numbers to `Long`/`Integer`, timestamps to `LocalDateTime`).

Limits:

- Procedure results cannot be paged.
- Oracle implicit results (`DBMS_SQL.RETURN_RESULT`) are not supported. Use a `SYS_REFCURSOR` OUT parameter.

<a id="transactions-rule-pr9"></a>
### Transactions

- DML inside the procedure is part of the calling transaction. It is rolled back together.
- Before the call, lambdaQL flushes the persistence context, but only when a transaction is active and there are changes.
- `QueryBuilder(EntityManagerFactory)` runs the call in its own transaction. A `QueryBuilder` that takes an `EntityManager` (including Spring's shared `EntityManager`) uses the caller's transaction. Without a transaction, the call is auto-committed.
- After the call, loaded entities can be stale. Use `refresh` or `evict` on those entities.
- A `COMMIT` or DDL inside the procedure breaks the transaction. lambdaQL cannot prevent this.

<a id="affected-rows-rule-pr10"></a>
### Affected rows (rule PR10)

Procedures have no common "affected rows" result (Oracle returns -1).
If you need the count, return it in an OUT parameter.

---

<a id="84-warning-argument-order"></a>
## 8.4 Warning: argument order

> [!WARNING]
> **The order of the stub method parameters must be the same as the order in the database.**

lambdaQL binds arguments **by position**.
Hibernate 6.6.5 binds by position even when names are registered (measured; 7.4 not measured).
The MariaDB and H2 drivers do not support named parameters.
So a wrong order can give **wrong results without any error**.

What lambdaQL checks with JDBC metadata (`getProcedureColumns`) on the first call. The result is cached per procedure name.

| Check | Found |
|---|---|
| The procedure exists | Yes, error `LQL-4035` |
| Number of IN·INOUT parameters = number of stub arguments | Yes, error `LQL-4036` |
| INOUT parameters = arguments with `@InOut` | Yes, error `LQL-4036` |
| Number of OUT values and result sets = number of parts of the return type | Yes, error `LQL-4038` |
| Kind of type (number, text, date·time, boolean, binary) | Yes, error `LQL-4036`. Differences within the same kind are not compared (see "Argument type check" below) |
| The same parameter names in a different order | Yes, error `LQL-4043`. Only when the stub argument names are known (see below) |
| Many overloads for an Oracle package procedure name | Yes, error `LQL-4044` |

MariaDB, Oracle:

<!-- region: ProcedureTest.java#rules -->
```java
// the stub is checked against the database parameters on the first call
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

An example of a dangerous case:

For example, if the database has `SP_TRANSFER(p_to NUMBER, p_from NUMBER)` and you declare the stub as `void transfer(Long from, Long to)`, the order is swapped. The two arguments have the same type.

The count, the modes and the types all match. Only the names can show the mistake.
So lambdaQL compares the names of the IN and INOUT arguments on the first call.

- The stub argument names are read from the class file. The names are kept only when you compile with `-parameters` (see [2. Getting started](02-getting-started.md#recommended-compiler-option--parameters)). Put `@org.lambdaql.DbParam("p_to")` on a stub argument to give the database name directly.
- From the database names, a prefix that is common to **all** names and ends with `_` (`p_`, `in_`, `v_` and so on) is removed. No fixed prefix is assumed. Then case and `_` are ignored.
- If the names are the same and only the order differs, a `LambdaQLException` `LQL-4043` is raised before the call.
- If the names differ (the stub follows another naming style), it is not an error. Put `@DbParam` so that the check works.
- If the class file has no names and two neighboring arguments have the same type, `LQL-5004` is logged at `INFO` level, once per procedure.
- Types are compared only by kind (see "Argument type check" below). Two swapped arguments of the same kind can be found only by name.

The database procedure is `SQ_PROC_DIFF(p_to, p_from)`. The example stub is compiled with `-parameters`:

<!-- region: ProcedureTest.java#names -->
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

MariaDB, Oracle:

<!-- region: ProcedureTest.java#name-check -->
```java
assertEquals(7, qb.call(DiffProcs.class, p -> p.diff(10, 3)).execute());
assertEquals(7, qb.call(DiffProcs.class, p -> p.named(10, 3)).execute());
// the same names in another order: stopped before the call (it would run with the values swapped)
assertEquals("LQL-4043", assertThrows(LambdaQLException.class,
        () -> qb.call(DiffProcs.class, p -> p.swapped(3, 10)).execute()).code());
```

**Recommended:** compile with `-parameters` and use the same names as the database (the common prefix can be left out), or put `@DbParam`. Then a swapped order is detected.

**Argument type check.** On the first call, the Java type of each argument is compared with the kind of the database parameter type.
There are five kinds: number, text, date·time, boolean and binary. If they differ, the call stops with `LQL-4036` before it runs.

- A boolean argument is also treated as matching a number parameter. MariaDB's `BOOLEAN` is `TINYINT(1)`, and Oracle before 23 has no `BOOLEAN` type.
- A side whose kind cannot be decided (enums, object types, cursors) is not compared.
- Differences within the same kind (an `Integer` argument and a `NUMBER(10,2)` parameter, and so on) are left to the driver conversion.

MariaDB, Oracle:

<!-- region: ProcedureTest.java#type-check -->
```java
// the kind of each argument is compared with the database parameter: a string for a number parameter stops
LambdaQLException wrong = assertThrows(LambdaQLException.class,
        () -> qb.call(DiffProcs.class, p -> p.text("10", 3)).execute());
assertEquals("LQL-4036", wrong.code(), wrong.getMessage());
```

**Oracle package overloads.** An Oracle package can have many procedures with the same name.
lambdaQL cannot tell which one the stub points to, so a call to such a name stops with `LQL-4044`.

(Oracle only.) The stub `PkgProcs` declares `@SqlProcedure(pkg = "SQ_PKG", value = "TWICE")` and `@SqlProcedure(pkg = "SQ_PKG", value = "HALF")`:

<!-- region: ProcedureTest.java#overload -->
```java
assertEquals(5.0, qb.call(PkgProcs.class, p -> p.twice(2.5)).execute(), 1e-9);
// HALF has two overloads in the package: lambdaQL cannot tell which one to call
assertEquals("LQL-4044", assertThrows(LambdaQLException.class,
        () -> qb.call(PkgProcs.class, p -> p.half(10.0)).execute()).code());
```

<a id="when-the-metadata-cannot-be-read"></a>
**When the metadata cannot be read.** The database user may have no permission to read the dictionary views or `information_schema`.
If the metadata query itself fails (`SQLException`), lambdaQL does not stop the call.

- It logs `LQL-5005` at `INFO` level, once per procedure, and does not check the stub.
- The argument layout is taken from the stub: the IN and INOUT arguments in stub order, then the OUT parameters (in the order of the result parts), and on Oracle, after those, one `REF CURSOR` for each `List` part. So without metadata, the OUT parameters must come after the IN parameters in the database declaration.
- Checks that need metadata are skipped: the count and `@InOut` checks (`LQL-4036`) and the name order check (`LQL-4043`).
- If the query succeeds but the procedure does not exist, it is still `LQL-4035`.
- If only the Oracle overload lookup (`all_arguments`) fails, `LQL-5006` is logged at `INFO` level, and the call goes on without the overload check.

This fallback works on MariaDB and Oracle.

Example code: [`ProcedureTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java) (`rules`, `names`, `name-check`, `type-check`, `overload`).

---

<a id="85-procedure-rules-pr1-to-pr15-summary"></a>
## 8.5 Procedure rules PR1 to PR15 (summary)

| # | Rule | Status |
|---|---|---|
| PR1 | Name: the annotation value as it is. If the value is empty, the method name in UPPER_SNAKE. Write Oracle package procedures as `@SqlProcedure(pkg = ..., value = ...)`. | Implemented. Package names work on Oracle |
| PR2 | Arguments are bound **by position**. On the first call, the number of IN/INOUT parameters and the `@InOut` positions are compared with the database metadata. If the names are known, they are compared too, and the same names in a different order are an error. The kinds of the argument types (number, text, date·time, boolean, binary) are compared too, and a difference is an error (`LQL-4036`) (see [8.4](#84-warning-argument-order)). If the metadata cannot be read, the checks are skipped and the argument layout is taken from the stub (see [8.4](#when-the-metadata-cannot-be-read)). | The count, `@InOut` and name checks are implemented. Oracle package overload detection is implemented. The type check is implemented (MariaDB). The fallback without metadata (`LQL-5005`, `LQL-5006`) is implemented (MariaDB). |
| PR3 | 1 OUT → the return value. Many OUT/INOUT → a record. `@InOut` marks INOUT parameters. | Implemented |
| PR4 | 1 result set → `List<T>`. Entities are read with Hibernate, records through JDBC with label matching. No paging. | Implemented |
| PR5 | Oracle implicit results (`DBMS_SQL.RETURN_RESULT`) are not supported. Use a `SYS_REFCURSOR` OUT parameter. | – |
| PR6 | A DB function called alone uses a different path on each database (see [8.8](#88-call-a-db-function-alone)). | Implemented |
| PR7 | DB functions in queries: see [8.6](#86-declare-db-functions) and rules FN1 to FN11. | Implemented |
| PR8 | A DB function on a column in a condition cannot use an index, so a WARN is logged (see [8.9](#89-warning-functions-on-columns-and-indexes-sargable)). The code is `LQL-5001`. | Implemented. In `SargableMode.STRICT` it is an error (`LQL-3066`) |
| PR9 | A procedure runs inside the calling transaction. lambdaQL flushes before the call when needed. After the call, loaded entities can be stale. | Implemented |
| PR10 | There is no common affected row count. Use an OUT parameter. | – |
| PR11 | A `null` IN value is fine. Database parameter default values cannot be used. | Implemented |
| PR12 | The lambda expression is one stub call. The arguments are captured values, constants, or computations of them. `exec` is an error when there are OUT parameters. | Implemented |
| PR13 | In the argument type check, a boolean argument is also treated as matching a number parameter ("Argument type check" in [8.4](#84-warning-argument-order)). | Implemented |
| PR14 | Each lambda expression is analyzed once. Procedure metadata is read once per name. | Implemented |
| PR15 | H2 has no OUT, INOUT, multiple result sets or packages. Test procedures on MariaDB or Oracle (for example, with Testcontainers). | – |

PR11, PR12 and PR13 have more Kotlin-only parts (default arguments, named arguments, nullable types). They are in the [Kotlin version of this page](../kotlin/08-procedures-functions.md).

---

<a id="86-declare-db-functions"></a>
## 8.6 Declare DB functions

A DB function is a function that you created in the database. Example: `FN_DISCOUNT(price)`.
Declare it as a **static method of an interface** with `@SqlFunction`.
The body is never called. The body must only throw an exception.

<!-- region: FunctionTest.java#declare -->
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

    /** {@code SQ_FN_NONDET(value)}: on MariaDB declared NOT DETERMINISTIC (SARGable test). */
    @SqlFunction("SQ_FN_NONDET")
    static Double nondet(Double value) {
        throw new DbOnlyException();
    }

    /** A query name with its own SQL name; registered with lambdaql.functions.classes. */
    @SqlFunction(value = "SQ_TAX", sql = "SQ_FN_DISCOUNT")
    static Double tax(Double price) {
        throw new DbOnlyException();
    }
}
```

The database must already have the functions `SQ_FN_DISCOUNT` and `SQ_FN_IS_GOLD`. A Boolean result needs Oracle 23ai or later.

If you call `OrderFns.discount(...)` **outside** a lambdaQL lambda expression, you get a `DbOnlyException` (`LQL-4034`). This is intended.

`@SqlFunction` has two values.

| Value | Meaning |
|---|---|
| `value` | The function name used in the query. If empty, the method name in UPPER_SNAKE (`isVip` → `IS_VIP`). A schema name is also allowed (`PUBLIC.SQ_FN`) |
| `sql` | The SQL name when it is different from `value`. Such functions need registration (see [Registration](#registration-optional)) |

Other ways to declare:

| Declaration | Status |
|---|---|
| Interface static method | **Recommended** |
| A repository that `implements` the function interface (mixin) | Rejected |
| A functional interface constant (`DISCOUNT.apply(x)`) | Rejected |

Call the function **with the interface name** (`OrderFns.discount`). A static import also works, but a member method with the same name can hide it.

Example code: [`FunctionTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java) (`declare`).

---

<a id="87-use-db-functions-in-queries"></a>
## 8.7 Use DB functions in queries

Call the function directly. No context argument is needed.
It works in `where`, `select`, `orderBy`, `set`, `having`, arguments of aggregate functions, and subqueries (see the `function-group` and `function-sub` examples below).

<!-- region: FunctionTest.java#where -->
```java
// the function call is written as function('<name>' as <type>, ...); the column is its argument
SelectQuery<Order> q = qb.from(Order.class).where(o -> OrderFns.discount(o.getPrice()) > 100);
assertEquals("select e0 from Order e0 where function('SQ_FN_DISCOUNT' as Double, e0.price) > 100.0", q.toHql().hql());
```

<!-- region: FunctionTest.java#captured -->
```java
// a function of captured values only is still computed by the database (the value is bound)
SelectQuery<Order> q = qb.from(Order.class).where(o -> o.getPrice() > OrderFns.discount(base));
assertEquals("select e0 from Order e0 where e0.price > function('SQ_FN_DISCOUNT' as Double, :p1)", q.toHql().hql());
```

A Boolean function can be used as a whole condition. lambdaQL adds `= true`.

<!-- region: FunctionTest.java#boolean -->
```java
SelectQuery<Order> q = qb.from(Order.class).where(o -> OrderFns.isGold(o.getCustomer().getGrade()));
assertEquals("select e0 from Order e0 join e0.customer j1 where function('SQ_FN_IS_GOLD' as Boolean, j1.grade) = true",
        q.toHql().hql());
```

`select` and `orderBy`:

<!-- region: FunctionTest.java#select -->
```java
ValueQuery<Priced> q = qb.from(Order.class)
        .orderBy(o -> desc(OrderFns.discount(o.getPrice())))
        .select(o -> new Priced(o.getId(), OrderFns.discount(o.getPrice())));
assertEquals("select e0.id, function('SQ_FN_DISCOUNT' as Double, e0.price) from Order e0"
        + " order by function('SQ_FN_DISCOUNT' as Double, e0.price) desc nulls last", q.toHql().hql());
```

`Priced` is `record Priced(Long id, Double discounted)`.

<!-- region: FunctionTest.java#update -->
```java
ExecutableUpdate<Order> u = qb.update(Order.class)
        .set(o -> o.setPrice(OrderFns.discount(o.getPrice())))
        .where(o -> o.getPrice() > 100);
assertEquals("update Order e0 set e0.price = function('SQ_FN_DISCOUNT' as Double, e0.price) where e0.price > 100.0",
        u.toHql().hql());
```

The result rows of each query are the same as the same computation (`price * 0.9`) done in Java.

The form is the same in arguments of aggregate functions, in `having` and inside subqueries. In the next example, `Discounted` is `record Discounted(Integer customerId, Double total)`.

<!-- region: ManualJavaFunctionTest.java#function-group -->
```java
// a database function inside an aggregate, in having and in select
ValueQuery<Discounted> q = qb.from(Order.class)
        .where(o -> o.getCustomer() != null)
        .groupBy(o -> o.getCustomer().getId())
        .having(o -> Agg.sum(OrderFns.discount(o.getPrice())) > 200)
        .select(o -> new Discounted(o.getCustomer().getId(), Agg.sum(OrderFns.discount(o.getPrice()))));
assertEquals("select e0.customer.id, sum(function('SQ_FN_DISCOUNT' as Double, e0.price)) from Order e0"
        + " where e0.customer is not null group by e0.customer.id"
        + " having sum(function('SQ_FN_DISCOUNT' as Double, e0.price)) > 200.0", q.toHql().hql());
List<Discounted> rows = q.toList();
```

<!-- region: ManualJavaFunctionTest.java#function-sub -->
```java
SelectQuery<Customer> q = qb.from(Customer.class).where((c, sub) -> sub.from(Order.class)
        .where(o -> o.getCustomer().getId() == c.getId() && OrderFns.discount(o.getPrice()) > 100)
        .exists());
assertEquals("select e0 from Customer e0 where exists (select 1 from Order s1"
        + " where s1.customer.id = e0.id and function('SQ_FN_DISCOUNT' as Double, s1.price) > 100.0)", q.toHql().hql());
```

When `lambdaql.functions.classes` is set, Hibernate keeps the case of the name (`FN_DISCOUNT`) even for functions that are not registered (observed on three databases; the cause is not confirmed yet).
With no registration at all, Hibernate may write the name in lower case. Names without quotes work either way.

<a id="how-lambdaql-renders-it"></a>
### How lambdaQL renders it

lambdaQL **always** renders a DB function like this:

`function('<NAME>' as <Type>, arg1, arg2, ...)`

`<Type>` comes from the declared return type. Only these types are allowed:

| Allowed return types |
|---|
| `Double`, `Integer`, `Long`, `BigDecimal`, `String`, `LocalDate`, `LocalDateTime`, `Boolean` |

Generic returns (`<X> X nvl(X, X)`), entities, collections and `void` are rejected at analysis time.

<a id="registration-optional"></a>
### Registration (optional)

The form `function('FN_DISCOUNT' as Double, ...)` needs no registration (checked on three databases).
If you register the function interface, Hibernate also keeps the case of the name and checks the number and types of the arguments.

lambdaQL has `org.lambdaql.hibernate.LambdaQlFunctionContributor`, a Hibernate `FunctionContributor` (found with ServiceLoader). List the interfaces in a Hibernate property.

```properties
# plain Hibernate
lambdaql.functions.classes=com.acme.OrderFns,com.acme.CustomerFns

# Spring Boot
spring.jpa.properties.lambdaql.functions.classes=com.acme.OrderFns
```

If the property is not set, nothing is registered. Package scanning is not supported.
Write nested interfaces with their binary name that has `$`. For example, `OrderFns` inside `com.acme.Fns` is written as `com.acme.Fns$OrderFns`.

<a id="a-different-sql-name-needs-registration"></a>
### A different SQL name needs registration

`value` is the name in the query, and `sql` is the name in SQL. If the two names are different, the function must be registered.
This is useful for mixed-case function names made with quotes (`"SQ_Fn_Mixed"`). Without registration, the name can become lower case on H2 and Oracle.

Write the declaration like `@SqlFunction(value = "SQ_Fn_Mixed", sql = "\"SQ_Fn_Mixed\"")`. `OrderFns.tax` in the declaration above (`value = "SQ_TAX"`, `sql = "SQ_FN_DISCOUNT"`) has the same form.

<!-- region: FunctionTest.java#registered -->
```java
// SQ_TAX is registered at start-up (lambdaql.functions.classes) and runs SQ_FN_DISCOUNT in SQL
SelectQuery<Order> q = qb.from(Order.class).where(o -> OrderFns.tax(o.getPrice()) > 100);
assertEquals("select e0 from Order e0 where function('SQ_TAX' as Double, e0.price) > 100.0", q.toHql().hql());
```

If such a function is not registered, lambdaQL fails when it reads the lambda expression (`LQL-3058`, see [8.10](#810-function-rules-fn1-to-fn11-summary)).

Example code: [`FunctionTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java) (`registered`), [`FunctionTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java) (`where`, `captured`, `boolean`, `select`, `update`), [`ManualJavaFunctionTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaFunctionTest.java) (`function-group`, `function-sub`).

---

<a id="88-call-a-db-function-alone"></a>
## 8.8 Call a DB function alone

Use `qb.call(() -> ...)` with a **lambda expression without arguments**.

H2, MariaDB, Oracle:

<!-- region: ProcedureTest.java#function -->
```java
Double half = qb.call(() -> MathFns.half(price)).execute();
```

`price` is a captured `double` (150), and `half` is 75.0. The lambda expression must be one `@SqlFunction` call (`LQL-3063`).

This query runs as `select FN_DISCOUNT(?)` without FROM. Oracle accepts this only from 23ai. On Oracle, lambdaQL uses a JDBC function call instead (see the table below).

The number of arguments is different, so it does not clash with `qb.call(OrderProcs.class, p -> ...)`.

How lambdaQL runs it:

| Database | Path |
|---|---|
| Oracle | JDBC function call `{? = call FN(?)}`. It also works for functions that do DML and functions that return a cursor. |
| MariaDB, H2 | HQL `select function('FN' as T, :p1)` |

lambdaQL first flushes pending changes (the same as for procedure calls). So the function sees the latest changes.

Example code: [`ProcedureTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java) (`function`).

---

<a id="89-warning-functions-on-columns-and-indexes-sargable"></a>
## 8.9 Warning: functions on columns and indexes (SARGable)

lambdaQL tries to build SQL that **can use indexes** (see [10. Rules and limits](10-rules-and-limits.md#102-sargable-rule-index-friendly-sql)).
A function that wraps a column usually **cannot use** a normal index.

The `where` example of [8.7](#87-use-db-functions-in-queries), `OrderFns.discount(o.getPrice()) > 100`, becomes `function('SQ_FN_DISCOUNT' as Double, e0.price) > 100.0`. This condition does not use the index on `price`.

So lambdaQL logs a **WARN** for this form.
The check runs once per `where` lambda expression, when the analysis result is cached. Conditions without `@SqlFunction` do not query the database.

| Case | Code | H2 | MariaDB | Oracle |
|---|---|---|---|---|
| ① A function wraps a column: `fn(o.price) > 10`, a Boolean function on a column, a function inside `IN` or `IS NULL` | `LQL-5001` (WARN) | WARN | WARN (stored functions cannot be used in indexes) | WARN. **But** not logged if `user_ind_expressions` has a function-based index on the same expression `FN(COLUMN)` for that table. A number conversion that Oracle added because the column type differs from the function argument type (`FN(TO_NUMBER(COLUMN))` and so on) is treated as the same expression |
| ② A function on the value side compared with a column: `o.price > fn(rate)` | `LQL-5007` (WARN) | Not checked | WARN if `information_schema.routines.is_deterministic = 'NO'` (the default!). In this case MariaDB scans the whole index | Not checked (Oracle does a range scan either way) |

- The table and column names used for the Oracle index check come from the Hibernate mapping. Only functions with one column argument are checked.
- If the metadata query fails (for example, no permission), `LQL-5008` is logged at `INFO` level. ① is still warned, and ② is not checked.

Logged warnings: `LQL-5001` on H2, `LQL-5001` and `LQL-5007` on MariaDB, and none on Oracle with a function-based index on `SQ_FN_DISCOUNT(price)`:

<!-- region: FunctionTest.java#sargable -->
```java
// the function wraps the column: an index on price cannot be used (LQL-5001 at WARN),
// unless Oracle has a function-based index on SQ_FN_DISCOUNT(price)
qb.from(Order.class).where(o -> OrderFns.discount(o.getPrice()) > 100).toHql();
// the function is on the value side: fine, unless MariaDB says NOT DETERMINISTIC (LQL-5007 at WARN)
qb.from(Order.class).where(o -> o.getPrice() > OrderFns.nondet(base)).toHql();
```

**SARGable mode**. `SargableMode` has three values.

- `OFF`: no check, and no metadata query.
- `WARN` (default): logs warnings.
- `STRICT`: in the cases where `WARN` would log a warning (`LQL-5001`, `LQL-5007`, `LQL-5012`), the analysis fails with `org.lambdaql.NonSargableException` (`LQL-3066`).

Set it per persistence unit with `SargablePolicy.register(emf, SargableMode.STRICT)`, and remove it with `SargablePolicy.unregister(emf)`. In Spring, use `lambdaql.sargable.mode=STRICT` (see [12](12-spring-data-jpa.md)).
The check runs once per lambda expression, when the analysis result is put into the shared cache. So register the mode before the first query.

**Arithmetic on a column**.

- `+ - * /` with only one column and values (`o.getCnt() * 2 > n`) can be moved to the value side. lambdaQL logs `LQL-5012` at `WARN` level, and it is an error in `STRICT`.
- Arithmetic between columns (`o.getPrice() * o.getCnt() > n`) and `%` cannot be moved, so `LQL-5013` is logged only at `DEBUG` level. It is allowed also in `STRICT`.
- The moved form must have the same meaning. Divide by a decimal, like `o.getCnt() > n / 2.0`. The integer division `n / 2` gives different rows for `>=`, `=`, `<` and a negative `n`. If you multiplied by a negative number, reverse the comparison. The `LQL-5012` message explains this.
- Reducing warnings by looking up whether an index exists was not adopted. Because of permissions and synonyms, "no index" and "unknown" get mixed, and column arithmetic is a rare form.

<!-- region: FunctionTest.java#sargable-strict -->
```java
// STRICT: what WARN would log fails the analysis (NonSargableException, LQL-3066)
SargablePolicy.register(emf, SargableMode.STRICT);
NonSargableException fn = assertThrows(NonSargableException.class,
        () -> qb.from(Order.class).where(o -> OrderFns.discount(o.getTotal()) > 100).toHql());
assertEquals("LQL-3066", fn.code());
assertThrows(NonSargableException.class, () -> qb.from(Order.class).where(o -> o.getCnt() * 2 > n).toHql());
// the same meaning on the value side uses the index; divide by a decimal (n / 2 is integer division)
var moved = qb.from(Order.class).where(o -> o.getCnt() > n / 2.0).toHql();
assertEquals("select e0 from Order e0 where e0.cnt > 2.5BD", moved.hql());   // 2.5, never cut to 2
// arithmetic between columns cannot move, so STRICT allows it
qb.from(Order.class).where(o -> o.getCnt() * o.getStatus() > n).toHql();
```

**Recommended:**

- When possible, put the function on the value side: `o.getPrice() > OrderFns.discount(base)`.
- On MariaDB, declare a function `DETERMINISTIC` if it really is deterministic.
- On Oracle, a function-based index on the same expression removes the warning (such an index needs a `DETERMINISTIC` function).

Example code: [`FunctionTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java) (`sargable`, `sargable-strict`).

---

<a id="810-function-rules-fn1-to-fn11-summary"></a>
## 8.10 Function rules FN1 to FN11 (summary)

| # | Rule | Status |
|---|---|---|
| FN1 | `@SqlFunction` is a method annotation. If the value is empty, the method name in UPPER_SNAKE. | Implemented |
| FN2 | lambdaQL finds the called method and reads the annotation. The method body must only throw an exception (`throw new ...`). A method with a real body is an error, because Java and the database could give different values (`LQL-3052`). | Implemented |
| FN3 | All overloads with the same name must map to the same DB function. Otherwise it is an error (`LQL-3053`). | Implemented |
| FN4 | A call without the annotation, or a call that lambdaQL cannot identify, is an error. It is never skipped silently. | Implemented |
| FN5 | Arguments follow the value rules of [3. Conditions](03-where.md). Arguments made only of captured values are also bound and evaluated **in the database**. | Implemented |
| FN6 | Boxing calls are skipped. The return type comes from the declared method. It must be one of `Double`, `Integer`, `Long`, `BigDecimal`, `String`, `LocalDate`, `LocalDateTime`, `Boolean` (`LQL-3055`). | Implemented |
| FN7 | A function on a column gives a WARN (see [8.9](#89-warning-functions-on-columns-and-indexes-sargable)). On MariaDB, a `NOT DETERMINISTIC` function on the value side also gives a WARN (`LQL-5007`). | Implemented. In `SargableMode.STRICT` it is an error (`LQL-3066`) |
| FN8 | A name that is the same as a Hibernate built-in function (`COALESCE`, `ROUND`, `SYSDATE`, ...) is an error (`LQL-3054`), because Hibernate would silently use the built-in function. Use a prefix or a schema name (`PUBLIC.SQ_FN`). | Implemented |
| FN9 | Kotlin only (default arguments, `vararg`). See the Kotlin track. | Default arguments and `vararg` are implemented |
| FN10 | Only scalar functions are supported. User aggregate functions and window functions are not supported. A function without arguments is rendered as `fn()`. | Scalar functions implemented |
| FN11 | Known database differences: on MariaDB, a function that returns VARCHAR can have a collation conflict with string literals. The scale of `BigDecimal` results differs (0 on MariaDB and Oracle, 1 on H2). Boolean results need Oracle 23ai or later. | Measured with a prototype |

<!-- region: FunctionTest.java#rules -->
```java
// the body never runs in Java
assertEquals("LQL-4034", assertThrows(DbOnlyException.class, () -> OrderFns.discount(1.0)).code());
// a body that computes a value is rejected (Java and the database could give different values)
assertEquals("LQL-3052", assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(o -> BadFns.computes(o.getPrice()) > 1)).code());
// a Hibernate built-in name would silently run the built-in function
assertEquals("LQL-3054", assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(o -> BadFns.builtin(o.getPrice()) > 1)).code());
// a function with its own SQL name must be registered
assertEquals("LQL-3058", assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(o -> BadFns.unregistered(o.getPrice()) > 1)).code());
// the return type must have an HQL type
assertEquals("LQL-3055", assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(o -> BadFns.anything(o.getPrice()) != null)).code());
```

`BadFns` is an interface in the example with one wrong function for each case: a body that returns `price`, the name `COALESCE`, an unregistered `value = "SQ_OTHER"`·`sql = "SQ_FN_DISCOUNT"`, and the return type `Object`.

Example code: [`FunctionTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java) (`rules`).

---

<a id="811-database-support"></a>
## 8.11 Database support

| Feature | H2 2.3 | MariaDB 10.11 | Oracle 23ai |
|---|---|---|---|
| IN parameters | Yes | Yes | Yes |
| OUT / INOUT | **No** (H2 limit) | Yes | Yes |
| 1 result set | Yes (`ResultSet` function) | Yes (`SELECT` inside the procedure) | Yes (`SYS_REFCURSOR` OUT) |
| Package names (`PKG.PROC`) | No | No (needs `sql_mode=ORACLE`) | Yes |
| DB functions in queries | Yes | Yes | Yes |
| DB function called alone | Yes | Yes | Yes |
| Procedure calls by lambdaQL | – | Yes | Yes |
| DB functions with boolean results | Yes | Yes | Yes (23ai or later) |

Not supported: dynamic procedure names, choosing and calling one overload of an Oracle package procedure (overload detection is implemented, `LQL-4044`), table-valued functions, paging of procedure results.

The errors in this chapter are `LambdaQLException`s (or subclasses) with an `[LQL-xxxx]` code. When the lambda expression is read: `LQL-3052` to `LQL-3063`, and `LQL-3066` (`NonSargableException`, see [8.9](#89-warning-functions-on-columns-and-indexes-sargable)) for a condition that cannot use an index in `SargableMode.STRICT`. When the call runs: `LQL-4034` to `LQL-4039`, `LQL-4043`, `LQL-4044`. During a call, `LQL-5004`, `LQL-5005` and `LQL-5006` can also be logged at `INFO` level. For DB functions in conditions, `LQL-5001`, `LQL-5007` (`WARN`) and `LQL-5008` (`INFO`) can be logged. See [11. Errors and languages](../11-errors-and-languages.md).

---

[Java track](README.md) | Previous: [7. Update, insert, delete](07-dml.md) | Next: [9. Java notes](09-java-notes.md) | [Kotlin version of this page](../kotlin/08-procedures-functions.md)
