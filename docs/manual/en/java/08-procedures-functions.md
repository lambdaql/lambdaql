# 8. Stored procedures and DB functions (Java)

> [!NOTE]
> **Status: designed, not implemented yet.**
> This chapter is a decided design (decisions D30 and D31, milestone M11).
> The syntax compiles in Java, and the calls were checked on H2, MariaDB and Oracle 23ai with Hibernate 6.6.5.
> It does **not** run in lambdaQL today. Today only the analysis core and `where` + `toList()` work
> (tested on H2, MariaDB and Oracle). See the [Java track README](README.md#status) for the full status.

[Java track](README.md) | [Manual home](../README.md) | Previous: [7. Update, insert, delete](07-dml.md) | Next: [9. Java notes](09-java-notes.md) | [Kotlin version of this page](../kotlin/08-procedures-functions.md)

Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md)

---

This chapter has two parts:

| Part | What | Annotation | How you call it |
|---|---|---|---|
| [8.1 to 8.5](#81-declare-procedures) | **Stored procedures** | `@SqlProcedure` on an abstract interface method | `qb.call(OrderProcs.class, p -> p.calc(id, rate)).execute()` |
| [8.6 to 8.10](#86-declare-db-functions) | **DB functions** (scalar) | `@SqlFunction` on a static interface method | directly inside any lambda: `where(o -> OrderFns.discount(o.getPrice()) > 10)` |

In both cases, you write the signature **once** in a Java interface.
lambdaQL **never runs** these methods. It reads the call in the lambda bytecode and calls the database.

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

Write an interface. Put `@SqlProcedure("NAME")` on each **abstract** method.
You do not write an implementation. lambdaQL does not make one either.

```java
interface OrderProcs {
    @SqlProcedure("SQ_CALC")     Double calc(Long id, Double rate);              // 1 OUT → return value
    @SqlProcedure("SQ_CALC2")    CalcOut calc2(Long id, Double rate);            // many OUT → record
    @SqlProcedure("SP_ADJUST")   AdjOut adjust(Long id, @InOut Integer balance); // INOUT
    @SqlProcedure("SP_RECENT")   List<OrderView> recent(Long custId);           // result set / Oracle REF CURSOR
    @SqlProcedure("SQ_PKG.CALC") Double pkgCalc(Long id, Double rate);           // package or schema name
    @SqlProcedure("SP_PURGE")    void purge(Integer days);                       // no result
}

record CalcOut(Double total, Integer cnt) {}
record AdjOut(Integer balance, String msg) {}
```

Notes:

- `@SqlProcedure` (or `@SqlFunction`) is **required**. It tells lambdaQL what the method is.
  (The names `@Procedure` and `@Function` were not used: `@Procedure` is also a Spring Data JPA annotation, and `@Function` clashes with Kotlin's `kotlin.Function`.)
- If the value is empty, the name comes from the method name: `calcTotal` → `CALC_TOTAL`.
- Use boxed return types (`Double`, `Integer`). A procedure can return NULL.
- The interface and the result records are a copy of the DB signature. This is the price for type safety. lambdaQL checks part of it against the database (rule PR2).
- You can put procedures (abstract methods) and DB functions (static methods) in **the same interface**.

---

## 8.2 Call procedures

Use `qb.call(Interface.class, p -> p.method(args))`. Then `execute()`.

```java
Double d          = qb.call(OrderProcs.class, p -> p.calc(id, rate)).execute();
CalcOut co        = qb.call(OrderProcs.class, p -> p.calc2(id, rate)).execute();
AdjOut a          = qb.call(OrderProcs.class, p -> p.adjust(id, balance)).execute();
List<OrderView> r = qb.call(OrderProcs.class, p -> p.recent(custId)).execute();

// No result (void): use exec
qb.exec(OrderProcs.class, p -> p.purge(30)).execute();
```

Why `exec` and not `call` for void?

- In Java, `call` for both would be an ambiguous overload (compile error).
- `run` was not chosen: Kotlin has a standard library function with the same name, and a Kotlin caller could call it by mistake without an error.

### What the lambda may contain (rule PR12)

The lambda body is **one call of a stub method**. Nothing else.

| Allowed | Not allowed |
|---|---|
| `p -> p.calc(id, rate)` | logging, two calls, `if`, nested calls |
| arguments: captured values, constants, calculations of them (`rate * 2`) | entity paths (there is no entity here) |

Other points:

- `exec` with a procedure that has OUT parameters is an error. This stops OUT values from being lost by mistake.
- Database default values for parameters cannot be used. Pass every IN parameter.
- `null` as an IN value is fine.

---

## 8.3 OUT, INOUT and result sets

How the return type maps to the procedure (rules PR3, PR4, PR5):

| Procedure has | Return type of the stub method |
|---|---|
| nothing | `void` → call with `qb.exec(...)` |
| 1 OUT parameter | that value (`Double`, `Integer`, ...) |
| many OUT / INOUT parameters | a Java record |
| 1 result set (MariaDB `SELECT`, Oracle `SYS_REFCURSOR` OUT, H2 `ResultSet` function) | `List<T>` |
| many result sets | a record with `List` components |
| OUT values and result sets (MariaDB) | a record with scalar and `List` components |

How record components are filled:

- **Scalar components** ← OUT and INOUT parameters (not cursors), in DB order.
- **`List` components** ← result sets, in order (Oracle: order of the cursor OUT parameters; MariaDB: order of the returned result sets).
- If the numbers do not match, lambdaQL throws an error.
- Mixed positions like IN, OUT, IN, INOUT are fine. lambdaQL reads the positions from the DB metadata.

### INOUT

Mark the parameter with `@InOut`. The value you pass goes **in**. The value that comes **out** is a component of the returned record.

```java
@SqlProcedure("SP_ADJUST") AdjOut adjust(Long id, @InOut Integer balance);

AdjOut a = qb.call(OrderProcs.class, p -> p.adjust(id, 100)).execute();
// a.balance() is the new balance
```

### Result set element type `T`

| `T` is | How lambdaQL reads it |
|---|---|
| an entity | Hibernate result class |
| a record or DTO | JDBC `CallableStatement`, columns matched **by label** (`camelCase` ↔ `snake_case`, case ignored) |

lambdaQL never maps columns by position, because MariaDB and Oracle return columns of `SELECT *` in different orders.
It converts common types (for example a number to `Long`/`Integer`, a timestamp to `LocalDateTime`).

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
> **The order of the stub method parameters must be the same as the order in the database.**

lambdaQL binds arguments **by position** (rule PR2).
Hibernate 6.6.5 binds by position even when names are registered.
The MariaDB and H2 drivers do not support named parameters.
So if the order is wrong, you can get a **wrong result without any error**.

What lambdaQL checks on the first call (from JDBC metadata):

| Check | Found? |
|---|---|
| number of parameters | yes, error |
| mode (IN / OUT / INOUT / cursor) | yes, error |
| type | yes, error |
| two parameters **of the same type** swapped | **only if parameter names are available** |

Example of the dangerous case:

```java
// Database: SP_TRANSFER(p_to NUMBER, p_from NUMBER)
@SqlProcedure("SP_TRANSFER") void transfer(Long from, Long to);   // swapped! same types
```

The count, modes and types all match. Only the names can show the mistake.
lambdaQL compares names when it can get them: compile with javac `-parameters`, or use `@Param`.
It ignores the `p_` prefix and case. If names are not available and two neighbor parameters have the same type, lambdaQL logs a WARN.

**Advice:** compile with `-parameters` (see [2. Getting started](02-getting-started.md#recommended-compiler-option--parameters)), and use the same names as the database.

On Oracle, lambdaQL can also use real name binding (`pass_procedure_paramater_names=true` together with the stub parameter names).

---

## 8.5 Procedure rules PR1 to PR15 (summary)

| # | Rule |
|---|---|
| PR1 | Name: the annotation value as is. Empty value: method name in UPPER_SNAKE. Package or schema names (`SQ_PKG.CALC`, `LQL.SQ_CALC`) work. You can also write `@SqlProcedure(schema = ..., pkg = ..., value = ...)` to avoid confusion. |
| PR2 | Arguments are bound **by position**. On the first call, count, mode and type are checked against DB metadata. Swapped same-type arguments are found only with parameter names (see [8.4](#84-warning-argument-order)). Oracle overloads in a package are an error in v0.1. |
| PR3 | One OUT → return value. Many OUT/INOUT → record. `@InOut` marks INOUT parameters. |
| PR4 | One result set → `List<T>`. Entities through Hibernate; records/DTOs through JDBC with label matching. No paging. |
| PR5 | Many result sets → `List` components of a record, in order. Oracle implicit results are rejected. |
| PR6 | A DB function called alone uses a different path per database (see [8.8](#88-call-a-db-function-alone)). |
| PR7 | DB functions in queries: see [8.6](#86-declare-db-functions) and rules FN1 to FN11. |
| PR8 | A DB function on a column in a condition gives a WARN, because the index cannot be used (see [8.9](#89-warning-functions-on-columns-and-indexes-sargable)). |
| PR9 | The procedure runs in the calling transaction. lambdaQL flushes before the call when needed. Loaded entities can be stale after it. |
| PR10 | No common affected-row count. Use an OUT parameter. |
| PR11 | `null` IN values are fine. DB parameter defaults cannot be used. |
| PR12 | The lambda is one stub call. Arguments are captured values, constants or calculations of them. `exec` with OUT parameters is an error. |
| PR13 | Boolean parameters work on MariaDB and Oracle 23ai or later. |
| PR14 | Each lambda is analyzed once. Procedure metadata is read once per name. |
| PR15 | H2 has no OUT, INOUT, many result sets or packages. Test procedures on MariaDB or Oracle (for example with Testcontainers). |

PR11, PR12 and PR13 have extra Kotlin parts (default arguments, named arguments, nullable types). They are in the [Kotlin version of this page](../kotlin/08-procedures-functions.md).

---

## 8.6 Declare DB functions

A DB function is a function you made in the database, for example `FN_DISCOUNT(price)`.
Declare it as a **static method of an interface**, with `@SqlFunction`.
The body must only throw. It never runs inside lambdaQL.

```java
public interface OrderFns {
    @SqlFunction("FN_DISCOUNT") static Double discount(Double price) { throw new DbOnlyException(); }
    @SqlFunction("FN_IS_VIP")   static Boolean isVip(Long customerId) { throw new DbOnlyException(); }  // Boolean: Oracle 23ai or later
}
```

If you call `OrderFns.discount(...)` **outside** a lambdaQL lambda, it throws. This is on purpose.

Other ways to declare:

| Declaration | Status |
|---|---|
| interface static method | **recommended** |
| class static method | supported |
| interface default method + a singleton constant | supported, but longer to write |
| your repository `implements` the function interface (mixin) | rejected |
| functional interface constant (`DISCOUNT.apply(x)`) | rejected |

Call the function **with the interface name** (`OrderFns.discount`). A static import works, but a member method with the same name can hide it.

---

## 8.7 Use DB functions in queries

Call the function directly. No context argument is needed. It works in every lambda:
`where`, `select`, `orderBy`, `groupBy`, `having`, `set`, and inside subqueries.

```java
qb.from(Order.class).where(o -> OrderFns.discount(o.getPrice()) > 10).toList();
// → where function('FN_DISCOUNT' as Double, o.price) > 10

qb.from(Order.class).where(o -> OrderFns.isVip(o.getCustomer().getId())).toList();   // a Boolean function alone

qb.from(Order.class).where(o -> o.getPrice() > OrderFns.discount(base)).toList();     // column side stays clean

qb.from(Order.class).orderBy((o, by) -> by.desc(OrderFns.discount(o.getPrice()))).toList();

qb.from(Order.class).select(o -> new OrderView(o.getId(), o.getCustomer().getName(), OrderFns.discount(o.getPrice()))).toList();

qb.update(Order.class).set(o -> o.setPrice(OrderFns.discount(o.getPrice()))).all().execute();
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

### How lambdaQL renders it

lambdaQL **always** renders a DB function as:

```sql
function('<NAME>' as <Type>, arg1, arg2, ...)
```

`<Type>` comes from the declared return type. Only these types are allowed:

| Allowed return types |
|---|
| `Double`, `Integer`, `Long`, `BigDecimal`, `String`, `LocalDate`, `LocalDateTime`, `Boolean` |

Generic returns (`<X> X nvl(X, X)`), entities, collections and `void` are rejected at analysis time.

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

```java
@SqlFunction(key = "SQ_Fn_Mixed", sql = "\"SQ_Fn_Mixed\"")
static Double mixed(Double v) { throw new DbOnlyException(); }
```

If such a function is not registered, lambdaQL fails on the first use.

---

## 8.8 Call a DB function alone

Use `qb.call(() -> ...)` with a **lambda without arguments**:

```java
Double v = qb.call(() -> OrderFns.discount(price)).execute();
```

**SQL by database (planned)** — Hibernate's translation of the planned HQL, recorded by `PlannedSqlTest`; lambdaQL does not make this query yet. From `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql), example `08-function-alone`:

```sql
-- H2, MariaDB, Oracle (same)
select fn_discount(?)
```

This runs as `select fn_discount(?)` with no FROM. Oracle accepts that only from 23ai on; on Oracle, lambdaQL uses a JDBC function call instead (see the table below).

There is no conflict with `qb.call(OrderProcs.class, p -> ...)`, because the number of arguments is different.

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

```java
.where(o -> OrderFns.discount(o.getPrice()) > 10)
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

- Put the function on the value side when you can: `o.getPrice() > OrderFns.discount(base)`.
- On MariaDB, declare your function `DETERMINISTIC` if it is.
- On Oracle, a `DETERMINISTIC` function plus a function-based index removes the warning.

---

## 8.10 Function rules FN1 to FN11 (summary)

| # | Rule |
|---|---|
| FN1 | `@SqlFunction` is a method annotation. Empty value: method name in UPPER_SNAKE. |
| FN2 | lambdaQL finds the called method and reads its annotation. The method body must only throw (`throw new ...`). A method with a real body is an error, because Java and the database could give different values. |
| FN3 | All overloads with the same name must map to the same DB function. Otherwise: error. |
| FN4 | A call without the annotation, or a call lambdaQL cannot identify, is an error. It is never skipped silently. |
| FN5 | Arguments follow the value rules of [3. Conditions](03-where.md). Even an argument made only from captured values is bound and evaluated **in the database**. |
| FN6 | Boxing calls are skipped. The return type comes from the declared method. |
| FN7 | A function on a column gives a WARN (see [8.9](#89-warning-functions-on-columns-and-indexes-sargable)). |
| FN8 | A name equal to a Hibernate built-in function (`COALESCE`, `ROUND`, `SYSDATE`, ...) is an error. Hibernate would silently use the built-in. Use a prefix or a schema name (`PUBLIC.SQ_FN`). |
| FN9 | Kotlin only (default arguments, `inline`, `vararg`). See the Kotlin track. |
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

[Java track](README.md) | Previous: [7. Update, insert, delete](07-dml.md) | Next: [9. Java notes](09-java-notes.md) | [Kotlin version of this page](../kotlin/08-procedures-functions.md)
