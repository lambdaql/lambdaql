# 3. Conditions (`where`) (Java)

> [!IMPORTANT]
> This page is older than the Korean manual. See the [Korean page](../../ko/java/03-where.md) for the latest rules.

[Java track](README.md) | [Manual home](../README.md) | Previous: [2. Getting started](02-getting-started.md) | Next: [4. Sorting and paging](04-order-paging.md) | [Kotlin version of this page](../kotlin/03-where.md)

Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md)

---

## 3.1 The idea in one sentence

**A lambdaQL lambda is a query, not code.** lambdaQL reads the bytecode of the lambda. It never runs it.
So Java run-time rules (NullPointerException, object identity, boxing cache) do not apply.
lambdaQL uses its own rules. This chapter explains them.

If a form has no rule, lambdaQL does not guess. It throws an exception with a clear message (see [3.13](#313-what-is-rejected)).

## 3.2 Example entities

All examples use these entities (getters and setters not shown):

```java
@Entity class Order {
    @Id Long id;
    Double price;
    Integer status;
    boolean active;            // getter: isActive()
    String description;
    @ManyToOne Customer customer;
    LocalDateTime updateAt;
    @OneToMany(mappedBy = "order") List<Item> items;
}
@Entity class Customer { @Id Long id; String name; Integer grade; }
@Entity class Item     { @Id Long id; @ManyToOne Order order; Double price; int qty; }
```

## 3.3 Basic form

```java
QueryBuilder qb = new QueryBuilder(entityManagerFactory);

List<Order> list = qb.from(Order.class)
    .where(o -> o.getStatus() == 1 && o.getPrice() > 100.0)
    .toList();
// HQL: select e0 from Order e0 where e0.status = 1 and e0.price > 100.0
```

**About the SQL blocks.** `…` is the shortened select list (all entity columns), and `?` is a bind parameter. The lines come from [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java). It runs each query on H2 2.3, MariaDB 10.11 and Oracle 23.26 with Hibernate 6.6.5, and it fails if Hibernate's SQL changes.

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `to-hql`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>100.0
```

Examples marked **(tested)** are copied from [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java).
The test checks the HQL, runs the queries and compares the rows with the same filter in Java. It passes on H2, MariaDB and Oracle.
The test entity is a little different from 3.2: `price` is `double`, `status` is `short`, `Customer.id` is `int`, and `Order` has an enum `kind` (`Order.Kind`: `NORMAL`, `RUSH`).

`toHql()` gives the HQL and the parameters without running the query.
**(tested)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java), region `to-hql`:

```java
RenderedQuery q = qb.from(Order.class)
        .where(o -> o.getStatus() == 1 && o.getPrice() > 100)
        .toHql();
// constants written in the lambda are literals
assertEquals("select e0 from Order e0 where e0.status = 1 and e0.price > 100.0", q.hql());
assertEquals(Map.of(), q.parameters());

double minPrice = 100;
RenderedQuery q2 = qb.from(Order.class)
        .where(o -> o.getStatus() == 1 && o.getPrice() > minPrice)
        .toHql();
// captured values are bind parameters
assertEquals("select e0 from Order e0 where e0.status = 1 and e0.price > :p1", q2.hql());
assertEquals(Map.of("p1", 100.0), q2.parameters());
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `to-hql`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>100.0
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `to-hql#2`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>?
```

You can call `where()` many times. The conditions are joined with `AND`.
**(tested)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java), region `where-many`:

```java
RenderedQuery q = qb.from(Order.class)
        .where(o -> o.getStatus() == 1 || o.getStatus() == 3)
        .where(o -> o.isActive())
        .toHql();
assertEquals("select e0 from Order e0 where (e0.status = 1 or e0.status = 3) and e0.active = true", q.hql());
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `where-many`:

```sql
-- H2
select …
from orders o1_0
where (o1_0.status=1 or o1_0.status=3) and o1_0.active=true

-- MariaDB, Oracle
select …
from orders o1_0
where (o1_0.status=1 or o1_0.status=3) and o1_0.active=1
```

A method reference works too. `where(Order::isActive)` is the same as `where(o -> o.isActive())`.

**Values in the HQL.** The rule depends on where the value comes from (decision D37):

| Value | In the HQL | Example |
|---|---|---|
| A constant written in the lambda: a number (also a decimal), a string, `true`/`false`, `null`, an enum constant, a compile-time `static final` constant | a **literal** | `o.getPrice() > 100` → `e0.price > 100.0` |
| A captured value: a local variable, a field, a method parameter | a **bind parameter** | `o.getPrice() > minPrice` → `e0.price > :p1` |
| A computed value: `new BigDecimal("1.5")`, `form.getText().trim()`, `LocalDateTime.of(...)` | a **bind parameter** | `:p1` |

- A constant is the same on every call. So the SQL text does not change, and the database can see the value when it plans the query.
- A quote in a string constant is escaped: `"O'Brien"` becomes `'O''Brien'`. There is no SQL injection risk, because the string is a constant compiled into your class, not user input.
- A captured value can come from a user, so it is **always** a bind parameter. Only the value changes from call to call.

An enum constant is written with its full class name.
**(tested)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java), region `enum`:

```java
RenderedQuery q = qb.from(Order.class).where(o -> o.getKind() == Order.Kind.RUSH).toHql();
assertEquals("select e0 from Order e0 where e0.kind = org.lambdaql.query.Order$Kind.RUSH", q.hql());
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `enum`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.kind='RUSH'
```

## 3.4 Comparisons

| Java | HQL | Note |
|---|---|---|
| `o.getStatus() == 1` | `e0.status = 1` | |
| `o.getStatus() != 1` | `e0.status <> 1` | |
| `o.getPrice() > p` | `e0.price > :p1` | also `<`, `<=`, `>=` |
| `o.getUpdateAt().isAfter(since)` | `e0.updateAt > :p1` | `isBefore` gives `<` |
| `o.getDescription().compareTo("m") < 0` | `e0.description < 'm'` | `compareTo(..) op 0` becomes `op` |

Comparison with a number (one side is a primitive, like `== 1` or `> 100.0`) follows **SQL meaning**:
if the column is NULL, the result is UNKNOWN and the row is not returned. (In Java this would be a NullPointerException.)

Simple arithmetic with `+`, `-`, `*` is part of the design.
Avoid integer `/`: databases do not agree on the result (MariaDB gives `5/2 = 2.5`). The final rule for `/` is not decided yet.

**Floating point:** for `float`/`double` columns, lambdaQL does not promise the same bit-exact result as Java.
Captured `NaN` or `Infinity` values are rejected when the query runs.

## 3.5 AND, OR, NOT

| Java | HQL |
|---|---|
| `a && b` | `a and b` |
| `a \|\| b` | `a or b` |
| `!a` | `not (a)` |
| parentheses | kept as needed |

```java
.where(o -> (o.getStatus() == 1 || o.isActive()) && o.getPrice() > minPrice)
// HQL: (e0.status = 1 or e0.active = true) and e0.price > :p1

.where(o -> !(o.getStatus() == 1 && o.getPrice() > minPrice))
// HQL: not (e0.status = 1 and e0.price > :p1)
```

Rules:

- The result follows **SQL three-valued logic** (TRUE / FALSE / UNKNOWN). lambdaQL has checked that this gives the same rows on H2, MariaDB and Oracle.
- javac sometimes moves `!` around (De Morgan, `!(x > y)` to `x <= y`). This does not change the result.
- lambdaQL **does not simplify your condition**. If you write `o.getPrice() > p || o.getPrice() <= p`, it is sent as written. Rows where `price` is NULL are not returned. This is normal SQL behavior.
- Use `&&` and `||`. The non-short-circuit operators `&`, `|`, `^` are rejected.

**Boolean ternary.** Only three short forms are allowed. They are the same as `AND`, `OR`, `NOT`:

| Java | Same as |
|---|---|
| `c ? x : false` | `c && x` |
| `c ? true : x` | `c \|\| x` |
| `c ? false : true` | `!c` |

Any other ternary (`c ? x : y`) and any ternary that picks a value (`o.getStatus() == (flag ? 1 : 2)`) is rejected.
If the choice only depends on captured values, compute the value before the lambda:

```java
int wanted = flag ? 1 : 2;
qb.from(Order.class).where(o -> o.getStatus() == wanted).toList();
```

## 3.6 NULL rules

This is the most important part. There are two simple principles:

1. **Values of the entity are compared with the SQL meaning** (decision D48). `==` between objects, `equals`, `Objects.equals` and `!=` become `=` and `<>`. A row whose value is NULL does not match either of them. When the result can differ from Java, lambdaQL logs `LQL-5009` at `DEBUG` level.
2. **Captured values are computed in Java first, then compared with the SQL meaning too.** A captured `null` is bound as NULL, so `= :p1` and `<> :p1` match no row. lambdaQL logs `LQL-4045` at `WARN` level when this happens at run time. To find NULL, write `x == null` (it gives `is null`). Where Java would throw while computing the value (unboxing, `null.equals(...)`), lambdaQL throws too (see [3.6.4](#364-captured-null-values)).

lambdaQL **only handles null when you write it** (`== null`, `!= null`).
It never adds a hidden null check for you.

### 3.6.1 Rule table

| # | Java form | HQL | NULL behavior |
|---|---|---|---|
| R1 | One side is a primitive: `o.getStatus() == 1`, `o.getPrice() > p` | `e0.status = 1`, `e0.price > :p1` | SQL meaning. NULL column: row not returned |
| R2 | Both sides are objects: `o.getStatus() == status` (`Integer == Integer`), `String == String`, enum | `e0.status = :p1` | **value compare.** NULL column: row not returned. A captured `null`: no row (`LQL-4045`) |
| R2 | `o.getStatus() != status` (objects) | `e0.status <> :p1` | SQL meaning: NULL column: row **not** returned (Java would return it). A captured `null`: no row (`LQL-4045`) |
| R2 | Two entity values: `o.getDescription() == o.getCustName()` | `e0.description = e0.custName` | SQL meaning: a NULL on either side: row not returned (also NULL and NULL) |
| R3 | `x == null`, `x != null` | `x is null`, `x is not null` | |
| R4 | `o.getDescription().equals(p)` | `e0.description = :p1` | SQL meaning. NULL column: row not returned. `p == null`: exception |
| R5 | `Objects.equals(a, b)`, `"x".equals(a)`, `Boolean.TRUE.equals(a)` | the same as R2: `a = b`, `a = :p1` | the same as R2 |
| R9 | Captured values | bind parameters | see [3.6.4](#364-captured-null-values) |

Which Java types give R1 and which give R2 is explained in [9. Java notes](09-java-notes.md#93-equality-of-boxed-values).

### 3.6.2 `==` on objects compares values

In Java, `==` on two `Integer`, `Long` or `String` objects checks **object identity**.
In lambdaQL, it is a **value compare**. `Long` values above 127 or `new String(...)` do not matter.
The lambda is a query, so Java memory rules do not apply.

```java
Integer status = request.getStatus();    // may be null
qb.from(Order.class).where(o -> o.getStatus() == status).toList();
//   status = 3    -> HQL: e0.status = :p1    rows with status 3
//   status = null -> HQL: e0.status = :p1    no row: = NULL is never true (LQL-4045 at WARN)
```

To find rows where `status` is NULL, write the null check: `o.getStatus() == null` gives `e0.status is null`.

The column is never wrapped, so the database can use an index.

lambdaQL binds a captured value when the query runs. A `null` value is bound as NULL, and lambdaQL logs `LQL-4045` at `WARN` level because no row can match.
**(tested)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java), region `null-safe`:

```java
// the SQL meaning (D48): = NULL matches no row; lambdaQL logs LQL-4045 at WARN when it runs with a null
String d = null;
RenderedQuery q = qb.from(Order.class).where(o -> Objects.equals(o.getDescription(), d)).toHql();
assertEquals("select e0 from Order e0 where e0.description = :p1", q.hql());
// to find NULL, write the null check
RenderedQuery q3 = qb.from(Order.class).where(o -> o.getDescription() == null).toHql();
assertEquals("select e0 from Order e0 where e0.description is null", q3.hql());

String d2 = "a";
RenderedQuery q2 = qb.from(Order.class).where(o -> Objects.equals(o.getDescription(), d2)).toHql();
assertEquals("select e0 from Order e0 where e0.description = :p1", q2.hql());
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `null-safe`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.description=?
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `null-safe#2`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.description=?
```

> [!TIP]
> **IDE warning.** IntelliJ shows "Number objects compared using `==`" for this form.
> Inside a lambdaQL lambda this is intended. You can use `@SuppressWarnings("NumberEquality")`.

**A column that can never be NULL.** Some columns can never be NULL: the id, a primitive column, and a `nullable = false` column.
For such a column, `is null` is always false, so lambdaQL folds it away:

- `o.getId() == null` becomes `1=0` (no row).
- `o.getId() != null && o.getId() == id` becomes `e0.id = :p1`. The null check is gone, and the database can use the index.

This is also the bytecode of Kotlin's `it.id == id` with a `Long?` id, so the Kotlin form gets the plain `e0.id = :p1` too.
The rule applies only when the path has **no association step**. `o.getCustomer().getId()` can be NULL, because an order can have no customer, so its null check stays.

**(tested)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java), region `never-null`:

```java
// the id can never be NULL, so the null check is dropped (this is what Kotlin's it.id == id compiles to)
assertEquals("select e0 from Order e0 where e0.id = :p1",
        qb.from(Order.class).where(o -> o.getId() != null && o.getId() == id).toHql().hql());
assertEquals("select e0 from Order e0 where 1=0", qb.from(Order.class).where(o -> o.getId() == null).toHql().hql());
// through an association the row may have no customer, so the check stays
assertEquals("select e0 from Order e0 where e0.customer.id is null",
        qb.from(Order.class).where(o -> Objects.equals(o.getCustomer().getId(), null)).toHql().hql());
```

### 3.6.3 Write your null checks yourself

```java
// Only rows where status is not NULL and not 1:
.where(o -> o.getStatus() != 1)
// HQL: e0.status <> 1

// Also include rows where status is NULL:
.where(o -> o.getStatus() == null || o.getStatus() != 1)
// HQL: e0.status is null or e0.status <> 1
```

`equals()` and `Objects.equals()` are different:

```java
.where(o -> o.getDescription().equals(text))     // HQL: e0.description = :p1
.where(o -> Objects.equals(o.getDescription(), text))
                                                 // HQL: e0.description = :p1 (a null text: no row)
.where(o -> !Objects.equals(o.getStatus(), 1))   // HQL: e0.status <> 1
                                                 //      (NULL rows are not returned: SQL meaning)
```

**The SQL meaning for values of the entity** (decision D48). Two entity values, and an entity value compared with a value by `!=`, follow SQL: a row whose value is NULL does not match `=` or `<>`.
In Java, `!text.equals(null)` is true and `Objects.equals(null, null)` is true, so Java would return those rows. lambdaQL logs `LQL-5009` at `DEBUG` level (once per condition) when this can happen. To keep the NULL rows, write the null check in the lambda.

**(tested)** [`SqlMeaningTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlMeaningTest.java), region `sql-meaning` (H2, MariaDB and Oracle; the rows are description/custName pairs `a/a`, `a/b`, `null/a`, `null/null`, `b/null`, and `text` is `"a"`):

```java
// two entity values: = and <> as in SQL; a row with a NULL on either side matches neither
SelectQuery<Order> same = qb.from(Order.class).where(o -> Objects.equals(o.getDescription(), o.getCustName()));
assertEquals("select e0 from Order e0 where e0.description = e0.custName", same.toHql().hql());
assertEquals(List.of("a/a"), pairs(same.toList()));                    // null/null is not "equal" in SQL
SelectQuery<Order> different = qb.from(Order.class).where(o -> !Objects.equals(o.getDescription(), o.getCustName()));
assertEquals(List.of("a/b"), pairs(different.toList()));               // rows with a NULL are left out
// an entity value and a value: a NULL column does not match <>
SelectQuery<Order> notA = qb.from(Order.class).where(o -> !text.equals(o.getDescription()));
assertEquals("select e0 from Order e0 where e0.description <> :p1", notA.toHql().hql());
assertEquals(List.of("b/null"), pairs(notA.toList()));
// to keep the NULL rows, say so in the lambda
SelectQuery<Order> notAOrNull = qb.from(Order.class).where(o -> o.getDescription() == null || !text.equals(o.getDescription()));
assertEquals(List.of("b/null", "null/a", "null/null"), pairs(notAOrNull.toList()));
// a NULL Boolean is not false
assertEquals(List.of("f"), qb.from(Coded.class).where(c -> !c.getVip()).toList().stream().map(Coded::getCode).toList());
```

### 3.6.4 Captured null values

| Form | Captured value is `null` |
|---|---|
| `==`, `!=`, `Objects.equals(...)` and `o.getDescription().equals(p)` with a captured value | runs: `= :p1` / `<> :p1` with NULL, so **no row** matches. `LQL-4045` at `WARN` level |
| `p.equals(o.getDescription())` (`p` is the receiver) | **exception** when the query runs (`LQL-4011`) |
| compare where Java unboxes the value: `o.getPrice() > p` (`Double p`), and every `<`, `<=`, `>`, `>=` | **exception** |

Why: in SQL, `= NULL` and `<> NULL` are never true, so lambdaQL keeps that meaning and warns. The forms that need the value in Java (a method on `null`, unboxing) throw, as in Java. To find NULL rows, write `x == null`.

**Exception to the rule: a null guard.** If the same captured value is checked for null first, null is allowed.
**(tested)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java), region `captured-null-guard`:

```java
Double min = null;                                       // for example an empty search field
RenderedQuery q = qb.from(Order.class).where(o -> min == null || o.getPrice() > min).toHql();
// the guard is decided in Java when the query runs: no filter at all
assertEquals("select e0 from Order e0", q.hql());

Double min2 = 100.0;
RenderedQuery q2 = qb.from(Order.class).where(o -> min2 == null || o.getPrice() > min2).toHql();
assertEquals("select e0 from Order e0 where e0.price > :p1", q2.hql());
assertEquals(Map.of("p1", 100.0), q2.parameters());
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `captured-null-guard`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `captured-null-guard#2`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.price>?
```

How it works:

- The parts that use only captured values and constants are decided in Java when the query runs. This is done from left to right, with short-circuit, like Java.
- With `min == null`, the guard is true, so the whole condition is true. There is no filter. The compare `o.getPrice() > min` is never written (Java never runs it either).
- With `min = 100.0`, the guard is false, so only `e0.price > :p1` is left.
- The lambda is still analyzed only once. Only the HQL text depends on the value.
- The guard must come first. `o.getPrice() > min || min == null` with `min == null` throws when the query runs, the same as Java's NullPointerException.
- With `&&` the guard works the other way: `min != null && o.getPrice() > min` with `min == null` returns no rows (`where 1=0`).

<a id="365-converted-attributes-convert"></a>
### 3.6.5 Converted attributes (`@Convert`)

An attribute with a JPA `AttributeConverter` is compared with its Java value. Hibernate 6.6 converts a literal that is compared with such an attribute, and it converts bound parameters too. So the condition is written with Java values, and it finds the stored rows (checked on H2, MariaDB and Oracle).

**(tested)** [`ConvertTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ConvertTest.java), region `convert`:

```java
// code is stored reversed ("abc" as "cba"); vip is stored as 'Y'/'N' (YesNoConverter)
SelectQuery<Coded> literal = qb.from(Coded.class).where(c -> c.getCode().equals("abc"));
assertEquals("select e0 from Coded e0 where e0.code = 'abc'", literal.toHql().hql());
assertEquals(List.of("abc"), codes(literal.toList()));       // Hibernate converts the literal
```

## 3.7 Boolean columns

In the example entity, `active` is a primitive `boolean` (getter `isActive()`).
The rows with `getActive()` are for a nullable `Boolean` column.

| Java | HQL | NULL rows |
|---|---|---|
| `o.isActive()` | `e0.active = true` | – (a primitive `boolean` is never NULL) |
| `!o.isActive()` | `e0.active = false` | – (a primitive `boolean` is never NULL) |
| `Boolean.TRUE.equals(o.getActive())` (nullable `Boolean`) | `e0.active = :p1` (`:p1` is `true`) | not returned |
| `!Boolean.TRUE.equals(o.getActive())` | `e0.active <> :p1` | **not returned** (SQL meaning, D48; Java would return them) |
| `!o.getActive()` (nullable `Boolean`) | `e0.active = false` | **not returned** (SQL meaning: NULL is not false) |

**(tested)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java), region `boolean`:

```java
assertEquals("select e0 from Order e0 where e0.active = true",
        qb.from(Order.class).where(o -> o.isActive()).toHql().hql());
assertEquals("select e0 from Order e0 where e0.active = false",       // primitive boolean: never NULL
        qb.from(Order.class).where(o -> !o.isActive()).toHql().hql());
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `boolean`:

```sql
-- H2
select …
from orders o1_0
where o1_0.active=true

-- MariaDB, Oracle
select …
from orders o1_0
where o1_0.active=1
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `boolean#2`:

```sql
-- H2
select …
from orders o1_0
where o1_0.active=false

-- MariaDB, Oracle
select …
from orders o1_0
where o1_0.active=0
```

H2 has a real boolean type, so its SQL says `true` / `false`. MariaDB and Oracle store a boolean as a number, so Hibernate writes `1` / `0`.

Notes:

- `!o.getActive()` with a nullable `Boolean` column is an unboxing call in Java (NullPointerException for NULL). lambdaQL uses SQL meaning: NULL rows are not returned.
- lambdaQL does not use `is not true` or `coalesce(active, false)`. They fail on Oracle (ORA-00932).
- Negation of a bigger condition is always written as `not (...)`. lambdaQL never writes `(...) = false` or `(...) is null` around a condition, because Oracle 23 evaluates those wrong in `WHERE`.

**Comparing two boolean values** (`==`/`!=`) is supported:

```java
.where(o -> o.isActive() == (o.getStatus() > s))
// HQL: (e0.active = true and e0.status > :p1) or (e0.active = false and e0.status <= :p1)
```

lambdaQL writes it with `AND`/`OR` (not `CASE`), so the database can use indexes.
If the two sides share the same condition (for example `o.isActive() == o.isActive()`), the form is rejected.

## 3.8 Strings

- `equals`, `Objects.equals` and `==` on strings use the **database collation**.
  - MariaDB `_ci` collations ignore upper/lower case and trailing spaces.
  - Oracle stores `''` (empty string) as NULL. So `"".equals(o.getDescription())` is different from Java on Oracle.
- `==` on two strings is a value compare, not an identity compare (rule R2). See [9.4](09-java-notes.md#94-strings).
- A string constant in the lambda is written as a literal. A quote is escaped (`''`).

**(tested)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java), region `string-literal`:

```java
RenderedQuery q = qb.from(Order.class).where(o -> o.getDescription().equals("O'Brien")).toHql();
assertEquals("select e0 from Order e0 where e0.description = 'O''Brien'", q.hql());
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `string-literal`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.description='O''Brien'
```

- `startsWith`, `endsWith`, `contains` (LIKE) and functions on the column like `trim()`, `toUpperCase()` are **planned**, not in the first version.
- String `+` is **planned**. The analysis core already rejects `+` inside a lambda, also when only captured values are joined. Build the string before the lambda.

## 3.9 IN with a captured collection

Build a collection **outside** the lambda and call `contains` on it:

```java
List<Long> ids = List.of(1L, 2L, 3L);
qb.from(Order.class).where(o -> ids.contains(o.getId())).toList();
// HQL: e0.id in (:p1)

qb.from(Order.class).where(o -> !ids.contains(o.getId())).toList();
// HQL: e0.id not in (:p1)
```

The collection is one bind parameter.
**(tested)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java), region `in` (the test entity has a `short` status):

```java
List<Short> statuses = List.of((short) 2, (short) 3);
RenderedQuery q = qb.from(Order.class).where(o -> statuses.contains(o.getStatus())).toHql();
assertEquals("select e0 from Order e0 where e0.status in (:p1)", q.hql());
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `in`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status in (?,?)
```

`!contains` gives `not in`. The id can never be NULL, so there is no `is null` part. For a column that can be NULL, lambdaQL adds `x is null or`, because Java's `!contains` is true for a NULL value (when the list has no `null`).
**(tested)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java), region `not-in`:

```java
List<Long> ids = List.of(1L, 2L);
// the id can never be NULL: a plain NOT IN
assertEquals("select e0 from Order e0 where e0.id not in (:p1)",
        qb.from(Order.class).where(o -> !ids.contains(o.getId())).toHql().hql());
// a column that can be NULL: Java keeps the rows with NULL (the list has no null), so "is null" is added
// (List.of(...).contains(null) would throw NullPointerException in Java; Arrays.asList returns false)
List<String> descs = Arrays.asList("a", "b");
assertEquals("select e0 from Order e0 where (e0.description is null or e0.description not in (:p1))",
        qb.from(Order.class).where(o -> !descs.contains(o.getDescription())).toHql().hql());
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `not-in`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.id not in (?,?)
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `not-in#2`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where (o1_0.description is null or o1_0.description not in (?,?))
```

| Case | Result |
|---|---|
| The receiver is a captured `Collection`, `List` or `Set` | supported |
| The collection has a `null` element | exception when the query runs |
| Empty collection | `contains` is FALSE, `!contains` is TRUE (Hibernate writes `1=0` / `1=1`) |
| 1,500 elements | worked in one statement on H2, MariaDB and Oracle 23ai (older Oracle versions have a 1,000 limit; not tested) |
| The receiver is an entity collection: `o.getItems().contains(item)` | **rejected**. Use an `exists` subquery ([5. Subqueries](05-subquery.md)) |
| Collection made inside the lambda: `List.of(1, 2).contains(o.getStatus())` | **rejected**. Make it outside and capture it |

## 3.10 Association paths

`o.getCustomer().getName()` is **not run**. It only names a column: "the `name` column of the joined customer".
lambdaQL checks each step with the JPA Metamodel:

| Path step | HQL | Example |
|---|---|---|
| to-one association (`@ManyToOne`, `@OneToOne`) | `join` with an alias (inner by default, see below) | `o.getCustomer().getName()` -> `join e0.customer j1` ... `j1.name` |
| embedded (`@Embedded`) | same table, no join | if `Order` had an embedded `address`: `o.getAddress().getCity()` -> `e0.address.city` |
| path that ends with the target `@Id` | foreign key column, no join | `o.getCustomer().getId()` -> `e0.customer.id` |
| the association itself, compared with `null` | foreign key column, no join | `o.getCustomer() == null` -> `e0.customer is null` |
| collection (`@OneToMany`, `@ManyToMany`) | **rejected** | `o.getItems()...` -> use an `exists` subquery |

In `where`, an association path is an **inner join** by default (decision D36).
**(tested)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java), region `inner-join`:

```java
String name = "Kim";
RenderedQuery q = qb.from(Order.class).where(o -> o.getCustomer().getName().equals(name)).toHql();
assertEquals("select e0 from Order e0 join e0.customer j1 where j1.name = :p1", q.hql());
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `inner-join`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name=?
```

- An order **without a customer** is not in the result. There is no customer row to join.
- lambdaQL does not add a null check for you. You do not need `o.getCustomer() != null &&` before the path.

**A null guard does not keep orders without a customer.** A path in `where` is always an inner join (the SQL meaning, decision D48). So `o.getCustomer() == null || ...` cannot match an order without a customer: that order has no customer row to join. lambdaQL logs `LQL-5009` at `DEBUG` level for such a condition.
To keep orders without a customer, use an explicit `leftJoin` (decision D44, see [6.8](06-select-group.md#68-explicit-joins)) and write `c == null || ...`.
**(tested)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java), region `null-guard`:

```java
String name = "Kim";
// the SQL meaning (D48): a where path is an inner join, so "customer is null" never matches here
// (lambdaQL logs LQL-5009 at DEBUG); to keep orders without a customer, use leftJoin (D44)
RenderedQuery q = qb.from(Order.class)
        .where(o -> o.getCustomer() == null || o.getCustomer().getName().equals(name))
        .toHql();
assertEquals("select e0 from Order e0 join e0.customer j1 where e0.customer is null or j1.name = :p1", q.hql());

// the guard removes those orders, so an inner join is right
RenderedQuery q2 = qb.from(Order.class)
        .where(o -> o.getCustomer() != null && o.getCustomer().getName().equals(name))
        .toHql();
assertEquals("select e0 from Order e0 join e0.customer j1 where e0.customer is not null and j1.name = :p1", q2.hql());
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `null-guard`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.id is null or c1_0.name=?
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `null-guard#2`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.id is not null and c1_0.name=?
```

Hibernate writes `c1_0.id is null` for `e0.customer is null`. After the inner join it is never true.

- The same holds for other parts of an `||`: `o.getStatus() == 1 || o.getCustomer().getName().equals(name)` also uses an inner join, so an order with status 1 but without a customer is not returned (Java would return it).

To find orders **without** a customer, compare the association itself. This checks the foreign key column, with no join.
**(tested)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java), region `no-association`:

```java
RenderedQuery q = qb.from(Order.class).where(o -> o.getCustomer() == null).toHql();
assertEquals("select e0 from Order e0 where e0.customer is null", q.hql());
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `no-association`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id is null
```

A path that ends with the `@Id` of the associated entity is the foreign key column. No join is needed.
**(tested)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java), region `fk-path`:

```java
RenderedQuery q = qb.from(Order.class).where(o -> o.getCustomer().getId() == 1).toHql();
assertEquals("select e0 from Order e0 where e0.customer.id = 1", q.hql());
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `fk-path`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id=1
```

More facts:

- The same path gets one alias. It is shared by `where`, `orderBy` and `select`.
- A path that is used **only** in `orderBy` or `select` is a `left join`, so no rows are lost there (rules S8 and J1). If `where` uses the same path too, `orderBy` and `select` use the `where` join. It is an inner join unless a null guard made it a `left join`.
- Comparing two entities (`o.getCustomer() == c`) is **planned**. For now compare ids: `o.getCustomer().getId() == c.getId()` (here `c` is a captured object).

## 3.11 Captured values

A lambda can use values from outside: local variables, fields, method calls on captured objects.

```java
LocalDateTime since = LocalDateTime.of(2026, 1, 1, 0, 0);
.where(o -> o.getUpdateAt().isAfter(since))              // local variable
.where(o -> o.getStatus() == this.defaultStatus)          // field of this
.where(o -> o.getPrice() > filter.getMinPrice())           // method on a captured object
.where(o -> o.getDescription().equals(form.getText().trim()))
```

Rules:

- The part that does not use the entity is computed in Java **each time the query runs**. The result becomes a bind parameter.
- A compile-time constant (`static final int MAX = 5;`) is not a captured value. javac copies it into the lambda, so it is a literal, like `5` (see [3.3](#33-basic-form)).
- The lambda is analyzed once and the result is cached. New captured values are read on every call.
- Only the entity part becomes SQL. `form.getText().trim()` is Java; `o.getDescription().trim()` would be a database function (planned).
- Java needs captured local variables to be effectively final. If a value changes, compute it in a new variable before the lambda.
- A lambda that uses a field captures `this`. See [9.8](09-java-notes.md#98-lambdas-in-instance-methods-capture-this).

## 3.12 Errors that depend on order of evaluation

SQL does not promise to evaluate `AND`/`OR` from left to right. So a "guard" like this is not safe in SQL:

```java
qb.from(Item.class)
  .where(i -> i.getQty() != 0 && i.getPrice() / i.getQty() > 2)   // rejected
```

`/`, `%` and casts under a guard are rejected. Compare without division instead: `i.getQty() > 0 && i.getPrice() > 2 * i.getQty()`.

## 3.13 What is rejected

lambdaQL throws an exception with a code `[LQL-xxxx]` and the source line. It does not guess.
Most shapes are found when the lambda is analyzed (first use). Bad captured values (null, NaN) are found when the query runs.
See [11. Errors and languages](../11-errors-and-languages.md).

| Form | Example | What to do |
|---|---|---|
| Loops, `switch`, local variables in a block lambda | `o -> { int x = 1; return ...; }` | Write one expression |
| Value ternary | `o.getStatus() == (flag ? 1 : 2)` | Compute the value before the lambda |
| General boolean ternary | `c ? x : y` | Use the allowed forms in [3.5](#35-and-or-not) |
| Non-short-circuit operators | `a & b`, `a \| b`, `a ^ b` | Use `&&`, `\|\|` |
| String `+` | `o.getDescription() == "A" + code` | Build the string before the lambda (planned for columns) |
| Lambdas or streams inside the lambda | `o -> list.stream().anyMatch(...)` | Use `contains` ([3.9](#39-in-with-a-captured-collection)) or a subquery |
| String functions on a column | `o.getDescription().startsWith("A")` | Planned |
| Other methods on the entity | `o.calcSomething()` | Use getters only |
| Entity collection paths | `o.getItems().size() > 0` | `exists` subquery |
| Entity compared to entity | `o.getCustomer() == c` | Compare ids (planned: entity compare) |
| `/`, `%`, cast under a guard | see [3.12](#312-errors-that-depend-on-order-of-evaluation) | Remove the guard or change the condition |
| Null captured value as the receiver | `p.equals(o.getDescription())` with `p == null` | Write `o.getDescription().equals(p)`, or check `p` before the query |
| `NaN` / `Infinity` captured value | `o.getPrice() > Double.NaN` | Do not send NaN |
| Anonymous class, lambda that is not `Serializable` | `new Where<Order>() { ... }`, a `java.util.function.Predicate` | Use a lambda |

Exception types (all extend `LambdaQLException`):

| Exception | When |
|---|---|
| `LambdaAnalysisException` | The lambda cannot be read (not a serializable lambda, and so on) |
| `UnsupportedLambdaShapeException` | The shape is not supported (loop, value ternary, ...) |
| `TranslationException` | The meaning cannot be translated (unknown method, collection path, ...) |

Java-specific details are in [9. Java notes](09-java-notes.md). All rules in one place: [10. Rules and limits](10-rules-and-limits.md).

---

[Java track](README.md) | Previous: [2. Getting started](02-getting-started.md) | Next: [4. Sorting and paging](04-order-paging.md) | [Kotlin version of this page](../kotlin/03-where.md)
