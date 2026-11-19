<a id="3-conditions-where-java"></a>
# 3. Conditions (`where`) (Java)

[Java track](README.md) | [Manual home](../README.md) | Previous: [2. Getting started](02-getting-started.md) | Next: [4. Sorting and paging](04-order-paging.md) | [Kotlin version of this page](../kotlin/03-where.md)

Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md)

---

<a id="31-the-idea-in-one-sentence"></a>
## 3.1 The idea in one sentence

**A lambdaQL lambda is a query, not code.** lambdaQL reads the bytecode of the lambda. It never runs it.
So Java run-time rules (NullPointerException, object identity, boxing cache) do not apply.
lambdaQL uses its own rules. This chapter explains them.

If a form has no rule, lambdaQL does not guess. It throws an exception with a clear message (see [3.13](#313-what-is-rejected)).

<a id="32-example-entities"></a>
## 3.2 Example entities

All examples use these entities (getters and setters not shown).

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

<a id="33-basic-form"></a>
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
The test checks the HQL, runs the queries and compares the rows with the result of the same filter in Java. It passes on H2, MariaDB and Oracle.
The test entity is a little different from 3.2: `price` is `double`, `status` is `short`, `Customer.id` is `int`, and `Order` has an enum `kind` (`Order.Kind`: `NORMAL`, `RUSH`).

`toHql()` returns the HQL and the parameters without running the query.
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

You can call `where()` more than once. The conditions are joined with `AND`.
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

**Values in the HQL.** The rule depends on where the value comes from (decision D37).

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

<a id="34-comparisons"></a>
## 3.4 Comparisons

| Java | HQL | Note |
|---|---|---|
| `o.getStatus() == 1` | `e0.status = 1` | |
| `o.getStatus() != 1` | `e0.status <> 1` | |
| `o.getPrice() > p` | `e0.price > :p1` | also `<`, `<=`, `>=` |
| `o.getUpdateAt().isAfter(since)` | `e0.updateAt > :p1` | `isBefore` gives `<` |
| `o.getDescription().compareTo("m") < 0` | `e0.description < 'm'` | `compareTo(..) op 0` becomes `op` |

A comparison with a number (one side is a primitive, like `== 1` or `> 100.0`) follows the **SQL meaning**.
If the column is NULL, the result is UNKNOWN and the row is not returned. (In Java this would be a NullPointerException.)

You can use simple arithmetic with `+`, `-`, `*` and `/`.
Avoid integer `/` on a column. The database computes it, so databases do not agree on the result (MariaDB gives `5/2 = 2.5`). A rule that makes `/` on the column side give the same result is not decided yet.
A computation with captured values only (like `n / 2.0` below) is done in Java before the query, so it has no such difference.

In the examples below, `cnt` is an integer column (`Integer cnt`) of the test entity `Order`. The example entities in [3.2](#32-example-entities) do not have it.

**Arithmetic on a column and indexes** (decision D50). Arithmetic on a column, like `o.getCnt() * 2 > n`, prevents the use of an index. lambdaQL logs `LQL-5012` at `WARN` level, and with `SargableMode.STRICT` the analysis fails (`LQL-3066`).
When you move the arithmetic to the value side, divide by a decimal, like `o.getCnt() > n / 2.0`. The integer division `n / 2` gives different rows. If you multiplied by a negative number, reverse the comparison. For details, see [10.2](10-rules-and-limits.md#db-functions-on-a-column).

**An integer column and a decimal value** (decision D48 B8). When you compare an integer column with a decimal value, lambdaQL compares the value as written. It does not round the value or drop it.

- A constant written in the lambda stays a literal. `o.getCnt() >= 2.5` becomes `e0.cnt >= 2.5`.
- A captured value is bound as a `BigDecimal` (Hibernate type `BIG_DECIMAL`). The HQL is `e0.cnt >= :p1`, and `p1` is `2.5`.
- An IN list with such an element binds all elements as `BigDecimal` (`[2.5, 3.0]`).

Before this change, the value was cut to the column type, so `2.5` became `2` and the query returned wrong rows. Hibernate rejects a decimal bound with an integer column type (`CoercionException`), and it silently cuts the elements of an IN list (measured on the three databases).
lambdaQL does not change the comparison to an integer comparison (`>= 3`). The SQL that runs would then differ from the lambda, and it would give wrong rows when the entity mapping (`Integer`) differs from the database column type (for example, a `DECIMAL` with decimal places).

The results are correct on H2, MariaDB 10.11 and Oracle 23.26 (measured). MariaDB's `EXPLAIN` shows an index range scan (`type=range`) for int, `BigDecimal` and double parameters (measured). The Oracle execution plan is not measured.

**(tested)** [`SqlMeaningTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlMeaningTest.java), region `whole-number`:

```java
// cnt is a whole number; 2.5 is compared as written (it is never cut to 2)
double half = 2.5;
RenderedQuery atLeast = qb.from(Order.class).where(o -> o.getCnt() >= half).toHql();
assertEquals("select e0 from Order e0 where e0.cnt >= :p1", atLeast.hql());
assertEquals(Map.of("p1", new BigDecimal("2.5")), atLeast.parameters());    // bound as a decimal
// a constant written in the lambda stays a literal
assertEquals("select e0 from Order e0 where e0.cnt >= 2.5", qb.from(Order.class).where(o -> o.getCnt() >= 2.5).toHql().hql());
// an IN list with such a value is bound as decimals
List<Double> values = List.of(2.5, 3.0);
RenderedQuery in = qb.from(Order.class).where(o -> values.contains(o.getCnt().doubleValue())).toHql();
assertEquals("select e0 from Order e0 where e0.cnt in (:p1)", in.hql());
assertEquals(Map.of("p1", List.of(new BigDecimal("2.5"), new BigDecimal("3.0"))), in.parameters());
```

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

**Boolean ternary.** Only three short forms are allowed. They are the same as `AND`, `OR` and `NOT`.

| Java | Same as |
|---|---|
| `c ? x : false` | `c && x` |
| `c ? true : x` | `c \|\| x` |
| `c ? false : true` | `!c` |

Any other ternary (`c ? x : y`) and any ternary that picks a value (`o.getStatus() == (flag ? 1 : 2)`) is rejected.
If the choice depends only on captured values, compute the value before the lambda.

```java
int wanted = flag ? 1 : 2;
qb.from(Order.class).where(o -> o.getStatus() == wanted).toList();
```

<a id="36-null-rules"></a>
## 3.6 NULL rules

This is the most important part. There are two simple principles.

1. **Values of the entity are compared with the SQL meaning** (decision D48). `==` between objects, `equals`, `Objects.equals` and `!=` become `=` and `<>`.
   A row whose value is NULL matches neither of them.
   When the result can differ from Java, lambdaQL logs `LQL-5009` at `DEBUG` level.
2. **Captured values are computed in Java first, and the comparison also follows the SQL meaning.** A captured `null` is bound as NULL, so `= :p1` and `<> :p1` match no row.
   In this case lambdaQL logs `LQL-4045` at `WARN` level when the query runs. To find NULL, write `x == null` (it gives `is null`).
   An expression made only of captured values (`p.trim()`, `min.intValue()` that converts to another primitive type, and so on) is a value computed in Java. So if the receiver is `null`, it throws an exception (`LQL-4012`), as in Java.
   Unboxing to the value's own primitive type (`min.doubleValue()`) is the same bytecode as javac's automatic unboxing. So lambdaQL does not run it and binds the boxed value as it is (see [3.6.4](#364-captured-null-values)).

lambdaQL **only handles null when you write it** (`== null`, `!= null`).
It never adds a hidden null check for you.

<a id="361-rule-table"></a>
### 3.6.1 Rule table

| # | Java form | HQL | NULL behavior |
|---|---|---|---|
| R1 | One side is a primitive: `o.getStatus() == 1`, `o.getPrice() > p` | `e0.status = 1`, `e0.price > :p1` | SQL meaning. NULL column: row not returned |
| R2 | Both sides are objects: `o.getStatus() == status` (`Integer == Integer`), `String == String`, enum | `e0.status = :p1` | **Value compare.** NULL column: row not returned. A captured `null`: no row matches (`LQL-4045`) |
| R2 | `o.getStatus() != status` (objects) | `e0.status <> :p1` | SQL meaning. NULL column: row **not** returned (Java would return it). A captured `null`: no row matches (`LQL-4045`) |
| R2 | Two entity values: `o.getDescription() == o.getCustName()` | `e0.description = e0.custName` | SQL meaning. A NULL on either side: row not returned (also NULL and NULL) |
| R3 | `x == null`, `x != null` | `x is null`, `x is not null` | |
| R4 | `o.getDescription().equals(p)` | `e0.description = :p1` | SQL meaning. NULL column: row not returned. `p == null`: no row matches (`LQL-4045`) |
| R5 | `Objects.equals(a, b)`, `"x".equals(a)`, `Boolean.TRUE.equals(a)` | The same as R2: `a = b`, `a = :p1` | The same as R2 |
| R9 | Captured values | bind parameters | See [3.6.4](#364-captured-null-values) |

Which Java types give R1 and which give R2 is explained in [9. Java notes](09-java-notes.md#93-equality-of-boxed-values).

<a id="362--on-objects-compares-values"></a>
### 3.6.2 `==` on objects compares values

In Java, `==` on two `Integer`, `Long` or `String` objects checks **object identity**.
In lambdaQL, it is a **value compare**. `Long` values above 127 or `new String(...)` do not matter.
The lambda is a query, so Java memory rules do not apply.

```java
Integer status = request.getStatus();    // can be null
qb.from(Order.class).where(o -> o.getStatus() == status).toList();
//   status = 3    -> HQL: e0.status = :p1    rows where status is 3
//   status = null -> HQL: e0.status = :p1    no row matches: = NULL is never true (LQL-4045 at WARN level)
```

To find rows where status is NULL, write the null check. `o.getStatus() == null` gives `e0.status is null`.

The column is not wrapped in a function, so the database can use an index.

lambdaQL binds a captured value when the query runs. `null` is bound as NULL, and no row can match, so lambdaQL logs `LQL-4045` at `WARN` level.
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

**A column that can never be NULL.** The id, a primitive column and a `nullable = false` column can never be NULL.
For such a column, `is null` is always false, so lambdaQL folds the check into a constant and removes it.

- `o.getId() == null` becomes `1=0` (no row).
- `o.getId() != null && o.getId() == id` becomes `e0.id = :p1`. The null check is gone, so the database can use the index.

Kotlin's `it.id == id` with a `Long?` id compiles to the same bytecode, so the Kotlin form also gets the plain `e0.id = :p1`.

The rule applies only when the path has **no association step**. Some orders have no customer, so `o.getCustomer().getId()` can be NULL, and the null check on this path stays.

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

<a id="363-write-your-null-checks-yourself"></a>
### 3.6.3 Write your null checks yourself

```java
// only rows where status is not NULL and not 1:
.where(o -> o.getStatus() != 1)
// HQL: e0.status <> 1

// also include rows where status is NULL:
.where(o -> o.getStatus() == null || o.getStatus() != 1)
// HQL: e0.status is null or e0.status <> 1
```

`equals()` and `Objects.equals()` are different.

```java
.where(o -> o.getDescription().equals(text))     // HQL: e0.description = :p1
.where(o -> Objects.equals(o.getDescription(), text))
                                                 // HQL: e0.description = :p1 (if text is null, no row matches)
.where(o -> !Objects.equals(o.getStatus(), 1))   // HQL: e0.status <> 1
                                                 //      (NULL rows are not returned: SQL meaning)
```

**The SQL meaning for values of the entity** (decision D48). A comparison of two entity values, and a `!=` between an entity value and a value, follow SQL. A row whose value is NULL matches neither `=` nor `<>`.

In Java, `!text.equals(null)` is true and `Objects.equals(null, null)` is true, so Java would return such rows.
When this difference can happen, lambdaQL logs `LQL-5009` at `DEBUG` level, once per condition. To keep the NULL rows, write the null check in the lambda.

**(tested)** [`SqlMeaningTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlMeaningTest.java), region `sql-meaning` (H2, MariaDB and Oracle. The rows are the description/custName pairs `a/a`, `a/b`, `null/a`, `null/null`, `b/null`, and `text` is `"a"`):

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

<a id="364-captured-null-values"></a>
### 3.6.4 Captured null values

| Form | When the captured value is `null` |
|---|---|
| `==`, `!=`, `Objects.equals(...)` and `o.getDescription().equals(p)` with a captured value | Runs: `= :p1` / `<> :p1` with NULL bound, so **no row** matches. `LQL-4045` at `WARN` level |
| `p.equals(o.getDescription())` (`p` is the receiver) | The same as above: `e0.description = :p1` (NULL), **no row** matches. `LQL-4045` at `WARN` level. No exception |
| A comparison where Java unboxes the value: `o.getPrice() > p` (`Double p`), `i.getQty() == q` (`int qty`, `Integer q`) | Runs: `e0.price > :p1` with NULL bound, **no row** matches. `LQL-4045` at `DEBUG` level. Unboxing to the value's own primitive type (`p.doubleValue()`) is not run |
| Unboxing that converts to another primitive type (`p.intValue()`, `Double p`) and a method call (`p.trim()`) | **Exception** when the query runs (`LQL-4012`), because Java computes the value |

**The form `p.equals(o.getDescription())` is not recommended.** In SQL it is the same comparison as `o.getDescription().equals(p)` (`e0.description = :p1`), so a `null` `p` does not throw.
Read as Java code, however, `null.equals(...)` looks like a NullPointerException. So put the entity value first and write `o.getDescription().equals(p)`.

Why: comparisons in the lambda follow the SQL meaning (decision D48). In SQL, `=` and `<>` with NULL are never true.
Whether a captured value is null is known only when the query runs, so lambdaQL logs the warning at run time.

An expression made only of captured values is computed in Java before the query. This covers method calls like `p.trim()` and unboxing that converts to another primitive type (`p.intValue()`, `Double p`). If the value is `null`, it throws an exception (`LQL-4012`), as in Java.
Unboxing to the value's own primitive type (`Double.doubleValue()`, `Integer.intValue()`, `Long.longValue()` and so on, including a widening conversion after it) is not computed, however.
javac's automatic unboxing and the hand-written call are the same bytecode, so lambdaQL treats them as automatic unboxing and binds the boxed value. If the value is `null`, no row matches, and lambdaQL logs `LQL-4045` at `DEBUG` level.
To find NULL rows, write `x == null`.

**Skipping a filter with a null guard.** If the same captured value is checked for null, the condition disappears with no filter when the value is null.
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

- The parts that use only captured values and constants are decided in Java when the query runs. This is done from left to right, with short-circuit evaluation, as in Java.
- With `min == null`, the guard is true, so the whole condition is true. There is no filter. The comparison `o.getPrice() > min` is not written into the HQL (Java does not run it either).
- With `min = 100.0`, the guard is false, so only `e0.price > :p1` is left.
- The lambda is still analyzed only once. Only the HQL text depends on the value.
- In this case the position of the guard does not change the result. With `min == null`, `o.getPrice() > min || min == null` has no filter either, because the unboxing in the comparison is not run in Java (Java would throw a NullPointerException).
- A comparison that calls a method on the captured value (`o.getDescription().equals(p.trim())`) is different. If the guard comes after it, `p.trim()` is computed first, so it throws an exception (`LQL-4012`) when `p == null`.
- With `&&` the guard works the other way. With `min == null`, `min != null && o.getPrice() > min` returns no rows (`where 1=0`).

<a id="365-converted-attributes-convert"></a>
### 3.6.5 Converted attributes (`@Convert`)

An attribute with a JPA `AttributeConverter` is compared with its Java value. Hibernate 6.6 converts a literal that is compared with such an attribute, and it converts bind parameters too.

So a condition written with Java values finds the stored rows (checked on H2, MariaDB and Oracle).

**(tested)** [`ConvertTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ConvertTest.java), region `convert`:

```java
// code is stored reversed ("abc" as "cba"); vip is stored as 'Y'/'N' (YesNoConverter)
SelectQuery<Coded> literal = qb.from(Coded.class).where(c -> c.getCode().equals("abc"));
assertEquals("select e0 from Coded e0 where e0.code = 'abc'", literal.toHql().hql());
assertEquals(List.of("abc"), codes(literal.toList()));       // Hibernate converts the literal
```

<a id="37-boolean-columns"></a>
## 3.7 Boolean columns

In the example entity, `active` is a primitive `boolean` (getter `isActive()`).
The rows with `getActive()` are for a nullable `Boolean` column.

| Java | HQL | NULL rows |
|---|---|---|
| `o.isActive()` | `e0.active = true` | – (a primitive `boolean` is never NULL) |
| `!o.isActive()` | `e0.active = false` | – (a primitive `boolean` is never NULL) |
| `Boolean.TRUE.equals(o.getActive())` (nullable `Boolean`) | `e0.active = :p1` (`:p1` is `true`) | not returned |
| `!Boolean.TRUE.equals(o.getActive())` | `e0.active <> :p1` | **not returned** (SQL meaning, D48. Java would return them) |
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

H2 has a real boolean type, so its SQL has `true` / `false`. MariaDB and Oracle store a boolean as a number, so Hibernate writes `1` / `0`.

Notes:

- `!o.getActive()` on a nullable `Boolean` column is an unboxing call in Java (NullPointerException for NULL). lambdaQL uses the SQL meaning. NULL rows are not returned.
- lambdaQL does not use `is not true` or `coalesce(active, false)`. They fail on Oracle (ORA-00932).
- The negation of a bigger condition is always written as `not (...)`. lambdaQL never wraps a condition in `(...) = false` or `(...) is null`, because Oracle 23 evaluates those wrong in `WHERE`.

**Comparing two boolean values** (`==`/`!=`) is supported.

```java
.where(o -> o.isActive() == (o.getStatus() > s))
// HQL: (e0.active = true and e0.status > :p1) or (e0.active = false and e0.status <= :p1)
```

lambdaQL writes it with `AND`/`OR`, not `CASE`, so the database can use indexes.
If the two sides share the same condition (for example `o.isActive() == o.isActive()`), the form is rejected.

<a id="38-strings"></a>
## 3.8 Strings

- `equals`, `Objects.equals` and `==` on strings use the **database collation**.
  - MariaDB `_ci` collations ignore upper/lower case and trailing spaces.
  - Oracle stores `''` (empty string) as NULL. So on Oracle, `"".equals(o.getDescription())` behaves differently from Java.
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

- `startsWith`, `endsWith`, `contains` (LIKE) and functions on the column like `trim()` and `toUpperCase()` are **planned**. They are not in the first version.
- String `+` is **planned**. The analysis core already rejects `+` inside a lambda, also when only captured values are joined. Build the string before the lambda.

<a id="39-in-with-a-captured-collection"></a>
## 3.9 IN with a captured collection

Build a collection **outside** the lambda and call `contains` on it.

```java
List<Long> ids = List.of(1L, 2L, 3L);
qb.from(Order.class).where(o -> ids.contains(o.getId())).toList();
// HQL: e0.id in (:p1)

qb.from(Order.class).where(o -> !ids.contains(o.getId())).toList();
// HQL: e0.id not in (:p1)
```

The collection becomes one bind parameter.
**(tested)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java), region `in` (the status of the test entity is `short`):

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

`!contains` gives `not in`. A column that can be NULL also gets a plain `not in` (SQL meaning, decision D48).
A row whose value is NULL does not match `not in`, and lambdaQL logs `LQL-5009` at `DEBUG` level. Java's `!contains` is true for a NULL value, so to keep such rows, write a null check like `o.getDescription() == null || !descs.contains(o.getDescription())`.
**(tested)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java), region `not-in`:

```java
List<Long> ids = List.of(1L, 2L);
// the id can never be NULL: a plain NOT IN
assertEquals("select e0 from Order e0 where e0.id not in (:p1)",
        qb.from(Order.class).where(o -> !ids.contains(o.getId())).toHql().hql());
// a column that can be NULL: the SQL meaning (D48), a NULL value is not "not in" the list (DEBUG LQL-5009);
// to keep those rows, write o.getDescription() == null || !descs.contains(o.getDescription())
List<String> descs = Arrays.asList("a", "b");
assertEquals("select e0 from Order e0 where e0.description not in (:p1)",
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
where o1_0.description not in (?,?)
```

| Case | Result |
|---|---|
| The receiver is a captured `Collection`, `List` or `Set` | Supported |
| The collection has a `null` element | Exception when the query runs |
| Empty collection | `contains` is FALSE, `!contains` is TRUE (Hibernate writes `1=0` / `1=1`) |
| 1,500 elements | Worked in one statement on H2, MariaDB and Oracle 23ai (older Oracle versions have a limit of 1,000. Not tested) |
| The receiver is an entity collection: `o.getItems().contains(item)` | **Rejected**. Use an `exists` subquery ([5. Subqueries](05-subquery.md)) |
| A collection made inside the lambda: `List.of(1, 2).contains(o.getStatus())` | **Rejected**. Make it outside and capture it |

<a id="310-association-paths"></a>
## 3.10 Association paths

`o.getCustomer().getName()` is **not run**. It only names a column: "the `name` column of the joined customer".
lambdaQL checks each step with the JPA Metamodel.

| Path step | HQL | Example |
|---|---|---|
| to-one association (`@ManyToOne`, `@OneToOne`) | `join` with an alias (inner by default, see below) | `o.getCustomer().getName()` -> `join e0.customer j1` ... `j1.name` |
| embedded (`@Embedded`) | Same table, no join. An equality compare of the whole value object is expanded per column (see below) | If `Order` had an embedded `address`: `o.getAddress().getCity()` -> `e0.address.city` |
| path that ends with the target `@Id` | Foreign key column, no join | `o.getCustomer().getId()` -> `e0.customer.id` |
| the association itself, compared with `null` | Foreign key column, no join | `o.getCustomer() == null` -> `e0.customer is null` |
| collection (`@OneToMany`, `@ManyToMany`) | **Rejected** | `o.getItems()...` -> use an `exists` subquery |

**Comparing a whole value object.** When you compare a whole embedded value object (`@Embedded`) for equality, lambdaQL expands it into one comparison per column (B6).
`o.getAddress().equals(addr)`, `Objects.equals(o.getAddress(), addr)` and `addr.equals(o.getAddress())` all become `e0.address.city = :p1 and e0.address.zip = :p2`.

- The negation (`!o.getAddress().equals(addr)`) is `e0.address.city <> :p1 or e0.address.zip <> :p2`.
- `o.getAddress() == null` is `e0.address.city is null and e0.address.zip is null`, and `!= null` is `e0.address.city is not null or e0.address.zip is not null`.
- Each column follows the SQL meaning (D48). A NULL column matches neither `=` nor `<>`. For example, a (Seoul, NULL) row matches neither the equality compare nor its negation (measured on the three databases).
- Each attribute of a captured value object is read and bound when the query runs. If the captured object is `null`, NULL is bound for every column, so no row matches, and lambdaQL logs `LQL-4045` at `WARN` level for each column.
- Nested value objects are expanded too.
- Order comparisons (`<` and so on) and comparisons with another type are still the error `LQL-3012`. Compare the fields.

**(tested)** [`MappingRulesTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/MappingRulesTest.java), region `embedded-compare`:

```java
// address is @Embedded (city, zip): comparing the whole value object compares each column (D48)
Address addr = new Address("Seoul", "04524");
SelectQuery<Order> same = qb.from(Order.class).where(o -> o.getAddress().equals(addr));
assertEquals("select e0 from Order e0 where e0.address.city = :p1 and e0.address.zip = :p2", same.toHql().hql());
assertEquals(Map.of("p1", "Seoul", "p2", "04524"), same.toHql().parameters());
// Objects.equals and addr.equals(...) are the same comparison
assertEquals(same.toHql().hql(), qb.from(Order.class).where(o -> Objects.equals(o.getAddress(), addr)).toHql().hql());
// null checks each column: "no address" means every column is NULL
SelectQuery<Order> none = qb.from(Order.class).where(o -> o.getAddress() == null);
assertEquals("select e0 from Order e0 where e0.address.city is null and e0.address.zip is null", none.toHql().hql());
SelectQuery<Order> some = qb.from(Order.class).where(o -> o.getAddress() != null);
assertEquals("select e0 from Order e0 where e0.address.city is not null or e0.address.zip is not null",
        some.toHql().hql());
// each column follows the SQL meaning: a NULL column matches neither = nor <>
SelectQuery<Order> other = qb.from(Order.class).where(o -> !o.getAddress().equals(addr));
assertEquals("select e0 from Order e0 where e0.address.city <> :p1 or e0.address.zip <> :p2", other.toHql().hql());
```

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

**A null guard does not keep orders without a customer.** A path in `where` is always an inner join (SQL meaning, decision D48).
So `o.getCustomer() == null || ...` does not match an order without a customer, because that order has no customer row to join. For such a condition, lambdaQL logs `LQL-5009` at `DEBUG` level.
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

Hibernate writes `e0.customer is null` as `c1_0.id is null`. After the inner join, this condition is never true.

- The same holds for other parts of an `||`. `o.getStatus() == 1 || o.getCustomer().getName().equals(name)` also uses an inner join, so an order with status 1 but without a customer is not returned (Java would return it).

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

- The same path gets one alias. `where`, `orderBy` and `select` share this alias.
- A path that is used **only** in `orderBy` or `select` is a `left join`, so no rows are lost there (rules S8 and J1). If `where` uses the same path too, `orderBy` and `select` use the join of `where`, which is an inner join.
- Comparing two entities (`o.getCustomer() == c`) is **planned**. For now, compare the ids: `o.getCustomer().getId() == c.getId()` (here `c` is a captured object).

<a id="311-captured-values"></a>
## 3.11 Captured values

A lambda can use values from outside: local variables, fields and method calls on captured objects.

```java
LocalDateTime since = LocalDateTime.of(2026, 1, 1, 0, 0);
.where(o -> o.getUpdateAt().isAfter(since))              // local variable
.where(o -> o.getStatus() == this.defaultStatus)          // field of this
.where(o -> o.getPrice() > filter.getMinPrice())           // method of a captured object
.where(o -> o.getDescription().equals(form.getText().trim()))
```

Rules:

- The part that does not use the entity is computed in Java **each time the query runs**. The result becomes a bind parameter.
- A compile-time constant (`static final int MAX = 5;`) is not a captured value. javac copies the value into the lambda, so it becomes a literal, like `5` (see [3.3](#33-basic-form)).
- The lambda is analyzed once and the result is cached. New captured values are read on every call.
- Only the entity part becomes SQL. `form.getText().trim()` is Java. `o.getDescription().trim()` would be a database function (planned).
- Java requires captured local variables to be effectively final. If a value changes, compute it into a new variable before the lambda.
- A lambda that uses a field captures `this`. See [9.8](09-java-notes.md#98-lambdas-in-instance-methods-capture-this).

<a id="312-errors-that-depend-on-order-of-evaluation"></a>
## 3.12 Errors that depend on order of evaluation

SQL does not promise to evaluate `AND`/`OR` from left to right. So a "guard" like this is not safe in SQL.

```java
qb.from(Item.class)
  .where(i -> i.getQty() != 0 && i.getPrice() / i.getQty() > 2)   // rejected
```

`/`, `%` and casts under a guard are rejected. Compare without division instead: `i.getQty() > 0 && i.getPrice() > 2 * i.getQty()`.

<a id="313-what-is-rejected"></a>
## 3.13 What is rejected

lambdaQL throws an exception with a code `[LQL-xxxx]` and the source line number. It does not guess.
Most shapes are found when the lambda is analyzed (on first use). Bad captured values (a `null` with a method call on it, NaN, a collection with a `null` element) are found when the query runs.
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
| Entity compared to entity | `o.getCustomer() == c` | Compare ids (entity compare is planned) |
| Order comparison of a value object, comparison with another type (`LQL-3012`) | `o.getAddress().compareTo(a) > 0` (when `Address` is `Comparable`) | Compare the fields. Equality compare is supported |
| `/`, `%`, cast under a guard | See [3.12](#312-errors-that-depend-on-order-of-evaluation) | Remove the guard or change the condition |
| A null captured value with a method call on it | `o.getDescription().equals(p.trim())`, `o.getStatus() > p.intValue()` (`Double p`) with `p == null` | Put a null guard first (`p == null \|\| ...`), or check `p` before the query |
| `NaN` / `Infinity` captured value | `o.getPrice() > Double.NaN` | Do not send NaN |
| Anonymous class, lambda that is not `Serializable` | `new Where<Order>() { ... }`, `java.util.function.Predicate` | Use a lambda |

Exception types (all extend `LambdaQLException`):

| Exception | When |
|---|---|
| `LambdaAnalysisException` | The lambda cannot be read (not a serializable lambda, and so on) |
| `UnsupportedLambdaShapeException` | The shape is not supported (loop, value ternary, ...) |
| `TranslationException` | The meaning cannot be translated (unknown method, collection path, ...) |

Java-specific details are in [9. Java notes](09-java-notes.md). For all rules in one place, see [10. Rules and limits](10-rules-and-limits.md).

---

[Java track](README.md) | Previous: [2. Getting started](02-getting-started.md) | Next: [4. Sorting and paging](04-order-paging.md) | [Kotlin version of this page](../kotlin/03-where.md)
