<a id="6-select-aggregates-and-group-by-java"></a>
# 6. Select, aggregates and group by (Java)

[Java track](README.md) | [Manual home](../README.md) | Previous: [5. Subqueries](05-subquery.md) | Next: [7. Update, insert, delete](07-dml.md) | [Kotlin version of this page](../kotlin/06-select-group.md)

Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md)

---

This chapter shows how to choose **what** a query returns:

- one value per row (`select(o -> o.getPrice())`)
- a Java record or a DTO class
- aggregates (`count`, `sum`, `avg`, ...) with `groupBy` and `having`
- `distinct`, paging and the result methods of a value query
- explicit joins with other entities (`join`, `leftJoin`, `crossJoin`)
- a subquery in the FROM clause (derived table) and a CTE (`with`)

<a id="contents"></a>
## Contents

- [6.1 Example entities and result types](#61-example-entities-and-result-types)
- [6.2 Select one value](#62-select-one-value)
- [6.3 Select a record or DTO](#63-select-a-record-or-dto)
- [6.4 Aggregate functions: `Agg`](#64-aggregate-functions-agg)
- [6.5 Group by](#65-group-by)
- [6.6 Having](#66-having)
- [6.7 Distinct, paging and results](#67-distinct-paging-and-results)
- [6.8 Explicit joins](#68-explicit-joins)
- [6.9 Derived table (subquery in FROM)](#69-derived-table-subquery-in-from)
- [6.10 CTE (`with`)](#610-cte-with)
- [6.11 Rules J1 to J8](#611-rules-j1-to-j8)
- [6.12 Not in v0.1](#612-not-in-v01)

---

<a id="61-example-entities-and-result-types"></a>
## 6.1 Example entities and result types

The examples use these entities (see [2. Getting started](02-getting-started.md#example-entities)):

| Entity | Fields used here |
|---|---|
| `Order` | `id`, `price`, `status`, `active`, `customer` (many-to-one) |
| `Customer` | `id`, `name`, `grade` |
| `Item` | `order`, `price`, `qty` |

Examples are copied from [`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java).
The test checks the HQL, runs the queries and compares the values with the same computation in Java. It passes on H2, MariaDB and Oracle.
It uses the test entity from [3.3](03-where.md#33-basic-form): `price` is `double`, `status` is `short` and `Customer.id` is `int`. The test data has one order without a customer.

The test uses these result types:

```java
record OrderView(Long id, String customerName, double price) {}
record Stat(Integer customerId, long count, Double total) {}
record KeyCount(Integer customerId, short status, long count) {}
record Total(long count, Double average) {}
```

> [!TIP]
> Use boxed types (`Double`, `Long`, `Integer`) for values that can be NULL.
> A column or an aggregate like `sum` can be NULL. A primitive `double` argument cannot hold NULL: lambdaQL then throws `IllegalStateException` (rule J4, see [6.4](#null-in-aggregates-rule-j4)).
> `count` is never NULL, so `long count` is fine.

---

<a id="62-select-one-value"></a>
## 6.2 Select one value

`select` takes one lambda. The lambda returns one value. The result is a `ValueQuery` of that value.
You can call `select` after `where` and after `orderBy`. The sort, `limit` and `offset` stay.

[`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java), region `value`:

```java
ValueQuery<Double> q = qb.from(Order.class)
        .where(o -> o.getStatus() == 1)
        .orderBy((o, by) -> by.asc(o.getPrice()))
        .select(o -> o.getPrice());
assertEquals("select e0.price from Order e0 where e0.status = 1 order by e0.price asc", q.toHql().hql());
```

**About the SQL blocks.** `?` is a bind parameter. The lines come from [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java). It runs each query on H2 2.3, MariaDB 10.11 and Oracle 23.26 with Hibernate 7.4, and it fails if Hibernate's SQL changes. In this chapter the select list is the point, so it is not shortened.

**SQL by database** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `value`:

```sql
-- H2, MariaDB, Oracle (same)
select o1_0.price
from orders o1_0
where o1_0.status=1
order by o1_0.price
```

An association path is a column, the same as in `where` (rule R24). In `select`, lambdaQL adds a `left join`, so orders without a customer are not lost (in `where`, the same path is an inner join by default).
A required association mapped with `optional = false` cannot lose rows, so it is an inner join in `select` too (see [4.5](04-order-paging.md#45-what-a-sort-key-can-be)).

```java
.select(o -> o.getCustomer().getName())
// HQL: select j1.name from Order e0 left join e0.customer j1
```

The record example in [6.3](#63-select-a-record-or-dto) tests this `left join`.
If the path ends with the `@Id` of the associated entity, there is no join.
`o.getCustomer().getId()` reads the foreign key column `customer_id`.

---

<a id="63-select-a-record-or-dto"></a>
## 6.3 Select a record or DTO

Call a constructor inside the lambda. Each constructor argument is a value expression.
A Java record and a normal DTO class with a constructor both work.

[`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java), region `record`:

```java
// only the columns are selected; the record is made in Java. The path uses a left join,
// so the order without a customer stays (customerName is null)
ValueQuery<OrderView> q = qb.from(Order.class)
        .select(o -> new OrderView(o.getId(), o.getCustomer().getName(), o.getPrice()));
assertEquals("select e0.id, j1.name, e0.price from Order e0 left join e0.customer j1", q.toHql().hql());
```

**SQL by database** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `record`:

```sql
-- H2, MariaDB, Oracle (same)
select o1_0.id,c1_0.name,o1_0.price
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
```

lambdaQL does **not** write `select new com.acme.OrderView(...)` (rule J2).
It selects the columns and calls the constructor in Java.
This avoids problems with long class names and nested class names.
lambdaQL converts each value to the type of the constructor argument (for example, Hibernate's `Long` to an `Integer` argument).

What you can return:

| Result type | Simple `select` | Row type of a derived table or CTE ([6.9](#69-derived-table-subquery-in-from)) |
|---|---|---|
| One value (`o.getPrice()`) | yes | – |
| Java record | yes | yes |
| Normal DTO class (constructor) | yes | **no** (J5) |

---

<a id="64-aggregate-functions-agg"></a>
## 6.4 Aggregate functions: `Agg`

Aggregate functions are **static methods of `org.lambdaql.Agg`**.
They are SQL standard functions, so lambdaQL provides them. (For your own DB functions, see [8. Procedures and functions](08-procedures-functions.md).)
There are only two static helper classes: `Agg`, and `Dml` for DML assignments (decision D49).

| Method | SQL |
|---|---|
| `Agg.count()` | `count(*)` |
| `Agg.count(x)` | `count(x)` |
| `Agg.countDistinct(x)` | `count(distinct x)` |
| `Agg.sum(x)` | `sum(x)` |
| `Agg.avg(x)` | `avg(x)` |
| `Agg.min(x)` | `min(x)` |
| `Agg.max(x)` | `max(x)` |

Java types:

```java
long count();
<N extends Number> N sum(N x);
Double avg(Number x);
<R extends Comparable<?>> R min(R x);   // max is the same
```

Static import is optional. These two lines are the same:

```java
.select(o -> new Total(Agg.count(), Agg.avg(o.getPrice())))

import static org.lambdaql.Agg.*;
.select(o -> new Total(count(), avg(o.getPrice())))
```

Important points:

- The return value is only a **placeholder**. The lambda never runs, so the value is never used.
- If you call `Agg.sum(...)` **outside** a lambdaQL lambda, it throws `IllegalStateException`.
- You can use `Agg` in `select`, in `having` and in the values of `insert ... select` ([7.5](07-5-insert.md)).
- You cannot use `Agg` in `where`. Use `having` for conditions on aggregates. Using `Agg` in `where` is an error at analysis time.

<a id="aggregate-over-all-rows-no-group-by"></a>
### Aggregate over all rows (no group by)

[`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java), region `total`:

```java
// aggregates without groupBy: one row over all matching orders
ValueQuery<Total> q = qb.from(Order.class)
        .where(o -> o.getStatus() == 1)
        .select(o -> new Total(Agg.count(), Agg.avg(o.getPrice())));
assertEquals("select count(*), avg(e0.price) from Order e0 where e0.status = 1", q.toHql().hql());
```

**SQL by database** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `total`:

```sql
-- H2, MariaDB, Oracle (same)
select count(*),avg(o1_0.price)
from orders o1_0
where o1_0.status=1
```

This query always gives one row. The test reads it with `q.one()`.

<a id="null-in-aggregates-rule-j4"></a>
### NULL in aggregates (rule J4)

| Function | No rows, or all values NULL |
|---|---|
| `sum`, `avg`, `min`, `max` | `NULL` |
| `count` | `0` |

This is normal SQL behavior.
If the NULL goes into a primitive constructor argument (`double`, `long`), lambdaQL throws `IllegalStateException`. The message names the argument and tells you to use the wrapper type (`Double`, `Long`). See the tested example in [6.11](#611-rules-j1-to-j8).

---

<a id="65-group-by"></a>
## 6.5 Group by

Write the group key with `groupBy`. lambdaQL does not guess the key.
`groupBy` returns a grouped query. Call `having(...)` (optional) and then `select(...)`.

<a id="one-key"></a>
### One key

```java
List<Stat> stats = qb.from(Order.class)
    .groupBy(o -> o.getCustomer().getId())
    .select(o -> new Stat(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice())))
    .toList();
// HQL: select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id
```

The key ends with `@Id`, so there is no join. Orders without a customer form one group with a NULL key.
The tested example with `having` is in [6.6](#66-having).

<a id="many-keys"></a>
### Many keys

Return a list of keys with `List.of(...)`:

[`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java), region `group-many`:

```java
// many keys: List.of(...) (Kotlin listOf(...), a to b, Triple)
ValueQuery<KeyCount> q = qb.from(Order.class)
        .where(o -> o.getCustomer() != null)
        .groupBy(o -> List.of(o.getCustomer().getId(), o.getStatus()))
        .select(o -> new KeyCount(o.getCustomer().getId(), o.getStatus(), Agg.count()));
assertEquals("select e0.customer.id, e0.status, count(*) from Order e0 where e0.customer is not null"
        + " group by e0.customer.id, e0.status", q.toHql().hql());
```

**SQL by database** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `group-many`:

```sql
-- H2, MariaDB, Oracle (same)
select o1_0.customer_id,o1_0.status,count(*)
from orders o1_0
where o1_0.customer_id is not null
group by o1_0.customer_id,o1_0.status
```

The keys are used in the order you write them.
This is the one place where a collection made inside the lambda is allowed.

Not allowed as a key list:

| Form | Why |
|---|---|
| `Set.of(a, b)` | a set removes duplicates and has no order |

<a id="every-non-aggregate-value-must-be-a-key-rule-j3"></a>
### Every non-aggregate value must be a key (rule J3)

When a query has aggregates, each value in `select` and `having` that is **not** an aggregate must be a `groupBy` key. This is the SQL rule. lambdaQL checks it at analysis time.

```java
// Error: o.getStatus() is not a group key
qb.from(Order.class)
  .groupBy(o -> o.getCustomer().getId())
  .select(o -> new KeyCount(o.getCustomer().getId(), o.getStatus(), Agg.count()));
// → TranslationException(LQL-3035)
```

The tested example is in [6.11](#611-rules-j1-to-j8).

---

## 6.6 Having

`having` is a condition on groups. It uses the same lambda rules as `where` ([3. Conditions (`where`)](03-where.md)), and it can use `Agg`.
You can call `having` many times. The conditions are joined with `AND`.

[`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java), region `group-by`:

```java
ValueQuery<Stat> q = qb.from(Order.class)
        .groupBy(o -> o.getCustomer().getId())
        .having(o -> Agg.sum(o.getPrice()) > 200)
        .select(o -> new Stat(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice())));
assertEquals("select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id"
        + " having sum(e0.price) > 200.0", q.toHql().hql());
```

**SQL by database** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `group-by`:

```sql
-- H2, MariaDB, Oracle (same)
select o1_0.customer_id,count(*),sum(o1_0.price)
from orders o1_0
group by o1_0.customer_id
having sum(o1_0.price)>200.0
```

`Agg.sum(o.getPrice())` is a `double` here, so javac compares it with `200.0`. The constant stays a literal (decision D37).

---

<a id="67-distinct-paging-and-results"></a>
## 6.7 Distinct, paging and results

`select(...)` returns a `ValueQuery`. It has these methods:

| Method | What it does |
|---|---|
| `distinct()` | `select distinct` |
| `limit(n)`, `offset(n)` | paging |
| `toList()` | runs the query; the values in order |
| `toSet()` | runs the query with `select distinct`; a `SequencedSet` in order |
| `first()` | the first value, or `Optional.empty()` |
| `one()` | the only value, or `Optional.empty()`. Two or more rows: `NonUniqueResultException` |
| `toHql()` | the HQL and the parameters, without running the query |

[`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java), region `distinct`:

```java
ValueQuery<Double> q = qb.from(Order.class)
        .orderBy((o, by) -> by.desc(o.getPrice()))
        .select(o -> o.getPrice())
        .distinct();
assertEquals("select distinct e0.price from Order e0 order by e0.price desc", q.toHql().hql());
// toSet() always uses select distinct
List<Double> prices = q.toSet().stream().toList();
```

**SQL by database** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `distinct`:

```sql
-- H2, MariaDB, Oracle (same)
select distinct o1_0.price
from orders o1_0
order by o1_0.price desc
```

- With `distinct()`, every sort key must be a selected value (rule J7). Otherwise lambdaQL throws `TranslationException` (`LQL-3046`) at analysis time.
- `toSet()` on a value query always uses `select distinct`. Repeated values are expected here, so there is no size check (unlike `toSet()` on entities, see [2. Getting started](02-getting-started.md#toset)).
- Oracle rejects a `@Lob` column used in `distinct` or as a `groupBy` key (ORA-22848, ORA-22849). H2 and MariaDB run it. lambdaQL does not change the query. It logs `LQL-5010` at `DEBUG` level (see [4.5](04-order-paging.md#45-what-a-sort-key-can-be)).

**Paging.** `limit` and `offset` work like in [4. Sorting and paging](04-order-paging.md).
With paging, lambdaQL adds the `id` as the last sort key, so the pages do not overlap (rule S9). It does not add it when the sort key is a unique column that cannot be NULL (see [4.8](04-order-paging.md#tie-breaker-automatic-id)).
`first()` also reads only one row, so it adds the same tie-breaker. When several rows have the same sort key value, it always returns the value of the row with the smallest `id`.
It does not add it to a grouped query or a `distinct` query, because the `id` is not in their select list.

```java
ValueQuery<Double> paged = qb.from(Order.class)
        .orderBy((o, by) -> by.desc(o.getPrice()))
        .select(o -> o.getPrice())
        .limit(2).offset(1);
// HQL: select e0.price from Order e0 order by e0.price desc, e0.id asc
```

The test method `pagingAndFirst` in [`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java) checks this HQL, `first()` and the page.

**SQL by database** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `value-page`:

```sql
-- H2, Oracle
select o1_0.price
from orders o1_0
order by o1_0.price desc,o1_0.id
offset ? rows
fetch first ? rows only

-- MariaDB
select o1_0.price
from orders o1_0
order by o1_0.price desc,o1_0.id
limit ?,?
```

---

<a id="68-explicit-joins"></a>
## 6.8 Explicit joins

An association path (`o.getCustomer().getName()`) joins a to-one association for you (see [3.10](03-where.md#310-association-paths)).
To join another entity with your own condition, or with no condition, use `join`, `leftJoin` or `crossJoin` (decisions D44, D45).

| Method | HQL | Rows |
|---|---|---|
| `join(Customer.class, (o, c) -> ...)` | `join Customer e1 on ...` | inner join: only rows with a match |
| `leftJoin(Customer.class, (o, c) -> ...)` | `left join Customer e1 on ...` | rows of the main entity without a match stay |
| `crossJoin(Customer.class)` | `cross join Customer e1` | every pair of rows; write the conditions in `where` |

After a join, every lambda gets all entities in join order:
`where((o, c) -> ...)`, `orderBy((o, c, by) -> ...)` (`by` is last, rule S12) and `select((o, c) -> ...)`.
The joined entities get the aliases `e1`, `e2`.

Examples are copied from [`JoinQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java).
The test runs each query on H2, MariaDB and Oracle and compares the rows with the same computation in Java.

[`JoinQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java), region `join`:

```java
SelectJoinQuery<Order, Customer> q = qb.from(Order.class)
        .join(Customer.class, (o, c) -> o.getCustomer().getId() == c.getId())
        .where((o, c) -> c.getGrade() != null && c.getGrade() >= 5);
assertEquals("select e0 from Order e0 join Customer e1 on e0.customer.id = e1.id"
        + " where e1.grade is not null and e1.grade >= 5", q.toHql().hql());
```

**The result is the main entity.** Without `select`, `toList()` returns `List<Order>`.
The joined entity is only for conditions, sorting and `select`.
To read values of the joined entity, use `select((o, c) -> ...)` (see below).

<a id="left-join-and-null"></a>
### Left join and NULL

[`JoinQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java), region `left-join`:

```java
// c is missing for an order without a customer: c == null finds those rows
SelectJoinQuery<Order, Customer> q = qb.from(Order.class)
        .leftJoin(Customer.class, (o, c) -> o.getCustomer().getId() == c.getId())
        .where((o, c) -> c == null || c.getName().equals("Kim"));
assertEquals("select e0 from Order e0 left join Customer e1 on e0.customer.id = e1.id"
        + " where e1.id is null or e1.name = 'Kim'", q.toHql().hql());
```

- `c == null` means "no match". lambdaQL writes it as `e1.id is null` (the `@Id` of the joined entity).
- After `leftJoin`, a condition on a column of the joined entity has the **SQL meaning**. When there is no match, the column is NULL, the condition is not true, and the row is left out.
  In Java, `c.getName()` on a missing `c` would throw `NullPointerException`. lambdaQL does not copy that.
  To keep the rows without a match, write `c == null || ...` as in the example.

<a id="sort-and-select"></a>
### Sort and select

[`JoinQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java), region `sort`:

```java
OrderedJoinQuery<Order, Customer> q = qb.from(Order.class)
        .leftJoin(Customer.class, (o, c) -> o.getCustomer().getId() == c.getId())
        .orderBy((o, c, by) -> by.asc(c.getName()).asc(o.getId()));
assertEquals("select e0 from Order e0 left join Customer e1 on e0.customer.id = e1.id"
        + " order by e1.name asc nulls first, e0.id asc", q.toHql().hql());
```

The NULL order rules of [4.4](04-order-paging.md#44-null-ordering-is-the-same-on-every-database) apply. Orders without a customer have a NULL `e1.name`, so they come first with `asc`.

[`JoinQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java), region `select`:

```java
ValueQuery<Row> rows = qb.from(Order.class)
        .leftJoin(Customer.class, (o, c) -> o.getCustomer().getId() == c.getId())
        .orderBy((o, c, by) -> by.asc(o.getId()))
        .select((o, c) -> new Row(o.getId(), c.getName()));
assertEquals("select e0.id, e1.name from Order e0 left join Customer e1 on e0.customer.id = e1.id"
        + " order by e0.id asc", rows.toHql().hql());
```

`Row` is `record Row(Long orderId, String customerName)`. For an order without a customer, `customerName` is `null`.
`select` gives a `ValueQuery`, so `distinct()`, `limit`, `offset` and the result methods of [6.7](#67-distinct-paging-and-results) work.

<a id="three-entities-and-repeated-rows"></a>
### Three entities and repeated rows

You can join up to three entities. The third join lambda gets all three: `(o, c, i) -> ...`.

[`JoinQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java), region `three`:

```java
// orders of a customer with an item of quantity > 10; an order repeats once per matching item
SelectJoin3Query<Order, Customer, Item> q = qb.from(Order.class)
        .join(Customer.class, (o, c) -> o.getCustomer().getId() == c.getId())
        .join(Item.class, (o, c, i) -> i.getOrder().getId() == o.getId())
        .where((o, c, i) -> i.getQty() > 10);
assertEquals("select e0 from Order e0 join Customer e1 on e0.customer.id = e1.id"
        + " join Item e2 on e2.order.id = e0.id where e2.qty > 10", q.toHql().hql());
```

> [!WARNING]
> When the join matches several rows, **the main row repeats**: once per match. `toList()` and `count()` count every repeat.
> If you want each order once, use an `exists()` subquery ([5. Subqueries](05-subquery.md)) instead of the join, or apply `distinct()` to the join query (see [`distinct()` on a join](#distinct-on-a-join)).

<a id="cross-join"></a>
### Cross join

`crossJoin` has no join condition. The method name says that you want every pair of rows. Write the conditions in `where`.

[`JoinQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java), region `cross-join`:

```java
// every pair of rows; the conditions go in where
SelectJoinQuery<Order, Customer> q = qb.from(Order.class)
        .crossJoin(Customer.class)
        .where((o, c) -> c.getGrade() != null && o.getPrice() > 100);
assertEquals("select e0 from Order e0 cross join Customer e1 where e1.grade is not null and e0.price > 100.0",
        q.toHql().hql());
```

lambdaQL has no `from(Order.class, Customer.class)` (a comma join). Without a condition it gives every pair by mistake, and it is not clear which entity is the result (decision D45). Use `crossJoin` for that.

<a id="paging"></a>
### Paging

With paging, lambdaQL adds the id of **every** entity as the last sort keys, so the pages do not overlap (rule S9).

[`JoinQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java), region `cross-join-paging`:

```java
// with paging, the id of every entity is added to the order, so pages never overlap (S9)
ValueQuery<Row> pairs = qb.from(Order.class)
        .crossJoin(Customer.class)
        .orderBy((o, c, by) -> by.desc(o.getPrice()))
        .select((o, c) -> new Row(o.getId(), c.getName()));
assertEquals("select e0.id, e1.name from Order e0 cross join Customer e1 order by e0.price desc, e0.id asc, e1.id asc",
        pairs.limit(5).toHql().hql());
```

<a id="distinct-on-a-join"></a>
### `distinct()` on a join

`SelectJoinQuery.distinct()` and `SelectJoin3Query.distinct()` return each row of the main entity once, even when the join repeats it (decision D51). The HQL is `select distinct e0`, and `count()` is `count(distinct e0)`.

- A sort key must be a column of the main entity itself (J7, `LQL-3046`). Sorting by a column of a joined entity or by an association path is an error.
- With paging, lambdaQL adds only the id of the main entity as the tie-breaker. It does not add the ids of the joined entities.
- With `select(...)` after `distinct()`, the values are read with `select distinct`.
- Consider an `exists` subquery ([5. Subqueries](05-subquery.md)) first. It gives the same rows without repeats, and it is often faster.

[`JoinQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java), region `join-distinct`:

```java
// an order with two matching items would come twice; distinct() returns it once
SelectJoinQuery<Order, Item> q = qb.from(Order.class)
        .join(Item.class, (o, i) -> i.getOrder().getId() == o.getId())
        .where((o, i) -> i.getQty() > 0)
        .distinct();
assertEquals("select distinct e0 from Order e0 join Item e1 on e1.order.id = e0.id where e1.qty > 0", q.toHql().hql());
// a sort key must be a column of the main entity (J7)
OrderedJoinQuery<Order, Item> sorted = q.orderBy((o, i, by) -> by.desc(o.getPrice()));
assertEquals("select distinct e0 from Order e0 join Item e1 on e1.order.id = e0.id where e1.qty > 0"
        + " order by e0.price desc", sorted.toHql().hql());
```

<a id="join-a-derived-table-or-cte"></a>
### Join a derived table or CTE

You can join the derived table of [6.9](#69-derived-table-subquery-in-from) and the CTE of [6.10](#610-cte-with) like an entity (decision D51).
The forms are `join(cte, (c, x) -> ...)`, `leftJoin(cte, ...)`, `join(qb.from(...)...select(new Row(...)), ...)` and `leftJoin(...)`. You can use them as the second or third root.

- The HQL is `with t as (...) ... join t e1 on ...` or `join (select ...) e1 on ...`.
- The row type is a record (J5). The accessor (`x.total()`) reads the column.
- If one statement reads the same `Cte` object twice, the definition is written once, and both joins read the same name: `join t e1 on ... left join t e2 on ...`. Different `Cte` objects get their own names, as in `with t as (...), t2 as (...)` (measured on the three databases).
- A derived row has no id, so `x == null` is an error (`LQL-3067`). Compare a column instead, as in `x.customerId() == null`.
- Hibernate 7.4 (and 6.6 before it) accepts `select distinct` of an entity, inner and left joins of a derived table or CTE, and entity joins inside a subquery (measured on the three databases).

[`JoinQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java), region `join-cte`:

```java
// the total of each customer, joined to the customer row
Cte<Total> t = qb.with(qb.from(Order.class)
        .where(o -> o.getCustomer() != null)
        .groupBy(o -> o.getCustomer().getId())
        .select(o -> new Total(o.getCustomer().getId(), Agg.sum(o.getPrice()))));
ValueQuery<NameTotal> rows = qb.from(Customer.class)
        .join(t, (c, x) -> x.customerId().equals(c.getId()))
        .where((c, x) -> x.total() > 100)
        .select((c, x) -> new NameTotal(c.getName(), x.total()));
assertEquals("with t as (select d0.customer.id as customerId, sum(d0.price) as total from Order d0"
        + " where d0.customer is not null group by d0.customer.id)"
        + " select e0.name, e1.total from Customer e0 join t e1 on e1.customerId = e0.id where e1.total > 100.0",
        rows.toHql().hql());
```

[`JoinQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java), region `join-derived`:

```java
// a derived table without a name; a customer without orders has no row in it (left join)
SelectJoinQuery<Customer, Total> small = qb.from(Customer.class)
        .leftJoin(totals(), (c, x) -> x.customerId().equals(c.getId()))
        .where((c, x) -> x.customerId() == null || x.total() < 210);
assertEquals("select e0 from Customer e0 left join (select d0.customer.id as customerId, sum(d0.price) as total"
        + " from Order d0 where d0.customer is not null group by d0.customer.id) e1 on e1.customerId = e0.id"
        + " where e1.customerId is null or e1.total < 210.0", small.toHql().hql());
```

[`JoinQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java), region `cte-reuse`:

```java
// the same CTE read twice is written once
ValueQuery<NameTotal> reused = qb.from(Customer.class)
        .join(t, (c, x) -> x.customerId().equals(c.getId()))
        .leftJoin(t, (c, x, y) -> y.customerId().equals(c.getId()) && y.total() > 210)
        .select((c, x, y) -> new NameTotal(c.getName(), y.total()));
assertEquals("with t as (select d0.customer.id as customerId, sum(d0.price) as total from Order d0"
        + " where d0.customer is not null group by d0.customer.id)"
        + " select e0.name, e2.total from Customer e0 join t e1 on e1.customerId = e0.id"
        + " left join t e2 on e2.customerId = e0.id and e2.total > 210.0", reused.toHql().hql());
```

<a id="what-a-join-query-has"></a>
### What a join query has

| After | Methods |
|---|---|
| `join`, `leftJoin`, `crossJoin` | `where`, `join` / `leftJoin` / `crossJoin` (up to three entities, derived tables and CTEs included), `distinct`, `orderBy`, `select`, `limit`, `toList`, `toSet`, `first`, `count`, `exists`, `toHql` |
| `orderBy((o, c, by) -> ...)` | `select`, `limit`, `offset`, `toList`, `first`, and the other methods of a sorted query ([4. Sorting and paging](04-order-paging.md)) |

For joins inside a subquery, see [5. Subqueries](05-subquery.md#joins-inside-a-subquery) (D51).
Not in v0.1 yet: joins in insert, more than three entities, and the join form of `SortSpec`.
Joins in update and delete will not be added (D51). Use an association path (U9) or `sub` (D49).

---

<a id="69-derived-table-subquery-in-from"></a>
## 6.9 Derived table (subquery in FROM)

Make a value query with `select` (a record, see [6.3](#63-select-a-record-or-dto)). Then pass it to `qb.from(...)`.
Its rows become the table of the outer query (decision D46).
The result is a `DerivedQuery<R>`. `R` is the row type.

[`DerivedQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DerivedQueryTest.java), region `derived`:

```java
// the rows of a group query are the table of the outer query; r.total() reads the column "total"
DerivedQuery<Stat> q = qb.from(qb.from(Order.class)
                .where(o -> o.getCustomer() != null)
                .groupBy(o -> o.getCustomer().getId())
                .select(o -> new Stat(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice()))))
        .where(r -> r.total() > 100)
        .orderBy((r, by) -> by.desc(r.total()));
assertEquals("select e0.customerId, e0.cnt, e0.total from (select d0.customer.id as customerId, count(*) as cnt,"
        + " sum(d0.price) as total from Order d0 where d0.customer is not null group by d0.customer.id) e0"
        + " where e0.total > 100.0 order by e0.total desc nulls last", q.toHql().hql());
```

In this test `Stat` is `record Stat(Integer customerId, Long cnt, Double total)`.
The test runs the query on H2, MariaDB and Oracle and compares the rows and `count()` with the same computation in Java.

- The inner query uses the alias `d0` (its joins `dj1`, ...). The outer query uses `e0`.
- Each record component names a column: `as customerId`, `as cnt`, `as total` (rule J5).
- In the outer lambdas, read a column with the record accessor: `r.total()` is `e0.total`.
- The outer query has `where`, `orderBy`, `limit`, `offset`, `select`, `toList`, `first`, `count` and `toHql`.

You can also select one value of the row. Captured values inside and outside are bound in order:

[`DerivedQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DerivedQueryTest.java), region `select`:

```java
// a value of the row; captured values inside and outside are bound in order
ValueQuery<Integer> ids = qb.from(qb.from(Order.class)
                .where(o -> o.getCustomer() != null && o.getPrice() > min)
                .groupBy(o -> o.getCustomer().getId())
                .select(o -> new Stat(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice()))))
        .where(r -> r.total() > min * 2)
        .select(r -> r.customerId());
```

`limit` and `offset` of the inner query stay in the inner query (HQL `limit` and `offset`). So you can sort the top rows again:

[`DerivedQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DerivedQueryTest.java), region `inner-limit`:

```java
// the three most expensive orders, then the cheapest of them
Optional<Priced> cheapestOfTop = qb.from(qb.from(Order.class)
                .orderBy((o, by) -> by.desc(o.getPrice()))
                .limit(3)
                .select(o -> new Priced(o.getId(), o.getPrice())))
        .orderBy((r, by) -> by.asc(r.price()))
        .first();
```

`Priced` is `record Priced(Long id, Double price)`.

**About the SQL block.** [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java) gives a simpler HQL of the same form to Hibernate directly and records the SQL on H2 2.3, MariaDB 10.11 and Oracle 23.26 (Hibernate 7.4). The test fails if Hibernate's SQL changes. The block shows how each database writes a derived table.

**SQL by database (Hibernate's SQL for this HQL form)** — from `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql), example `06-derived`:

```sql
-- HQL: select r.customerId, r.cnt, r.total from (select o.customer.id as customerId, count(*) as cnt, sum(o.price) as total from Order o group by o.customer.id) r where r.total > 100

-- H2
select r1_0.c0,r1_0.c1,r1_0.c2
from (select o1_0.customer_id,count(*),sum(o1_0.price) from orders o1_0 group by o1_0.customer_id) r1_0(c0,c1,c2)
where r1_0.c2>100

-- MariaDB, Oracle
select r1_0.c0,r1_0.c1,r1_0.c2
from (select o1_0.customer_id c0,count(*) c1,sum(o1_0.price) c2 from orders o1_0 group by o1_0.customer_id) r1_0
where r1_0.c2>100
```

Hibernate 7.4 names the columns of a derived table `c0`, `c1`, `c2` and so on. H2 puts the column list on the alias (`r1_0(c0,c1,c2)`). MariaDB and Oracle put the column aliases inside the subquery.

Rules:

- The row type must be a **Java record** (a Kotlin data class also works) (rule J5).
  lambdaQL takes the column names from the record components, in order.
- A normal DTO class or a single value is not allowed here: lambdaQL cannot name its columns. lambdaQL throws `TranslationException` (`LQL-3064`).

[`DerivedQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DerivedQueryTest.java), region `rules`:

```java
// J5: the row type must be a record or Kotlin data class (its components name the columns)
assertEquals("LQL-3064", assertThrows(LambdaQLException.class,
        () -> qb.from(qb.from(Order.class).select(o -> o.getPrice()))).code());
```

To join a derived table to an entity query, see [Join a derived table or CTE](#join-a-derived-table-or-cte) (D51).

Not in v0.1 yet: a join of two derived tables, and a derived table in update, delete or insert.

Hibernate also adds the `@SQLRestriction` and `@SoftDelete` conditions of an entity to the `on` of an entity join and inside a derived table (measured on MariaDB 10.11 and Oracle 23.26). lambdaQL does not handle these conditions itself.

---

<a id="610-cte-with"></a>
## 6.10 CTE (`with`)

Use `qb.with(valueQuery)` to make a handle of type `Cte<R>`. Then read it with `qb.from(handle)`.
The inner query is the same as for a derived table (6.9).

[`DerivedQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DerivedQueryTest.java), region `cte`:

```java
Cte<Stat> t = qb.with(perCustomer());
DerivedQuery<Stat> q = qb.from(t).where(r -> r.cnt() >= 2);
assertEquals("with t as (select d0.customer.id as customerId, count(*) as cnt, sum(d0.price) as total"
        + " from Order d0 where d0.customer is not null group by d0.customer.id)"
        + " select e0.customerId, e0.cnt, e0.total from t e0 where e0.cnt >= 2L", q.toHql().hql());
```

`perCustomer()` is a helper in the test. It returns the same value query as the inner query in [6.9](#69-derived-table-subquery-in-from):

```java
private ValueQuery<Stat> perCustomer() {
    return qb.from(Order.class)
            .where(o -> o.getCustomer() != null)
            .groupBy(o -> o.getCustomer().getId())
            .select(o -> new Stat(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice())));
}
```

**SQL by database (Hibernate's SQL for this HQL form)** — recorded by `PlannedSqlTest`. From `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql), example `06-cte`:

```sql
-- HQL: with t as (select o.customer.id as customerId, count(*) as cnt, sum(o.price) as total from Order o group by o.customer.id) select x.customerId, x.cnt, x.total from t x where x.total > 100

-- H2
select x1_0.customerId,x1_0.cnt,x1_0.total
from (select o1_0.customer_id,count(*),sum(o1_0.price) from orders o1_0 group by o1_0.customer_id) x1_0(customerId,cnt,total)
where x1_0.total>100

-- MariaDB, Oracle
with t (customerId,cnt,total) as (select o1_0.customer_id,count(*),sum(o1_0.price) from orders o1_0 group by o1_0.customer_id)
select x1_0.customerId,x1_0.cnt,x1_0.total
from t x1_0
where x1_0.total>100
```

MariaDB and Oracle run a real `with`. Hibernate writes the column list itself: `with t (customerId,cnt,total) as (...)`. H2 turns the CTE into a derived table.

Notes:

- The CTE is named `t`. The outer query always gives it the alias `e0` (`from t e0`). HQL needs an alias (rule J6).
- The CTE columns are named with select aliases (`as customerId`). HQL does not accept a column list after the CTE name; Hibernate adds it in the SQL (rule J6).
- MariaDB and Oracle run a real `with`. On H2, Hibernate expands it into a derived table. The result is the same.
- The row type rule is the same as for a derived table: a record (J5).
- To join a CTE to an entity query, see [Join a derived table or CTE](#join-a-derived-table-or-cte) (D51).
- You can read the same CTE twice in one query. The definition is written once (see [Join a derived table or CTE](#join-a-derived-table-or-cte)).
- Not in v0.1 yet: a CTE in update, delete or insert.

---

<a id="611-rules-j1-to-j8"></a>
## 6.11 Rules J1 to J8

| # | Rule | Status |
|---|---|---|
| J1 | A `select` lambda returns **one value expression** or **one constructor call**. Each constructor argument is a value expression (rules in [3. Conditions (`where`)](03-where.md)). Association paths that are used only in `select` become `left join` aliases, so no rows are lost. A required association (`optional = false`) is an inner join (A4). If `where` uses the same path, `select` uses the `where` join (R24). | Implemented |
| J2 | For a constructor, lambdaQL selects only the columns and calls the constructor in Java. It does not write `select new`. | Implemented |
| J3 | With aggregates, every non-aggregate value in `select` and `having` (and in the values of `insert ... select`) must be a `groupBy` key. Otherwise: an error at analysis time. | Implemented. The error is `TranslationException` (`LQL-3035`, see [11](../11-errors-and-languages.md)) |
| J4 | `sum`/`avg`/`min`/`max` give NULL when there are no rows or all values are NULL. `count` gives 0. Use boxed types for values that can be NULL. A NULL for a primitive constructor argument throws `IllegalStateException`, which names the argument. | Implemented |
| J5 | Row type of a derived table or CTE: a **Java record** (Kotlin code can also use a data class). Normal DTO classes are allowed only in simple `select`. Otherwise: `LQL-3064`. This also applies to the rows of a joined derived table or CTE (D51). A derived row cannot be compared with `null` (`LQL-3067`). | Implemented |
| J6 | A CTE made with `qb.with(...)` names its columns with select aliases: `with t as (select d0.customer.id as customerId, ...) select e0.customerId ... from t e0`. HQL does not accept a column list after the name: Hibernate rejects `with t (col list) as (...)` with a `SyntaxException`. Hibernate writes the column list in the SQL itself (`with t (customerId,cnt,total) as (...)` on MariaDB and Oracle); H2 turns the CTE into a derived table. The outer query always uses an alias for it (`from t e0`). | Implemented |
| J7 | `distinct()` renders `select distinct`. With sorting, the sort keys must be in the select list (a DB rule). lambdaQL checks this at analysis time. For `distinct()` on a join, a sort key must be a column of the main entity itself (D51). | Implemented |
| J8 | Select works together with sorting ([4. Sorting and paging](04-order-paging.md)), paging and subqueries ([5. Subqueries](05-subquery.md)). Sorting by an aggregate (`by.desc(s.total())`) is a v0.2 candidate. | Implemented, except the sort by an aggregate |

[`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java), region `rules`:

```java
// J3: a value without an aggregate must be a group key
LambdaQLException j3 = assertThrows(LambdaQLException.class, () -> qb.from(Order.class)
        .groupBy(o -> o.getCustomer().getId())
        .select(o -> new KeyCount(o.getCustomer().getId(), o.getStatus(), Agg.count())));
assertEquals("LQL-3035", j3.code());
// aggregates belong in having, not in where
LambdaQLException where = assertThrows(LambdaQLException.class, () -> qb.from(Order.class).where(o -> Agg.count() > 1));
assertEquals("LQL-3005", where.code());
// J7: with distinct, the sort key must be selected
LambdaQLException j7 = assertThrows(LambdaQLException.class, () -> qb.from(Order.class)
        .orderBy((o, by) -> by.asc(o.getId())).select(o -> o.getPrice()).distinct().toHql());
assertEquals("LQL-3046", j7.code());
// J4: the group without orders gives NULL; a primitive argument cannot take it
assertThrows(IllegalStateException.class, () -> qb.from(Order.class)
        .where(o -> o.getStatus() == 9)
        .select(o -> new Bad(null, Agg.sum(o.getPrice()))).toList());
```

`Bad` is `record Bad(Integer customerId, double total)`. No order has status 9, so `sum` is NULL, and the primitive `double` cannot take it.

---

<a id="612-not-in-v01"></a>
## 6.12 Not in v0.1

| Item | Status |
|---|---|
| Sort by an aggregate result or a select alias, for example `orderBy((s, by) -> by.desc(s.total()))`, and `orderBy` on a grouped query | v0.2 candidate |
| User aggregate functions and window functions | v0.2 candidate |
| Joins in insert, more than three entities, three or more entities inside a subquery | later |
| Joins in update and delete | will not be added (D51). Use an association path (U9) or `sub` (D49) |
| Derived tables and CTEs in DML | later |

The errors from this chapter are `TranslationException` with a code: `LQL-3035` (wrong group key, J3), `LQL-3005` (`Agg` in `where`), `LQL-3046` (a sort key that is not selected with `distinct`, J7) and `LQL-3064` (the row type of a derived table or CTE, J5). See [11. Errors and languages](../11-errors-and-languages.md).

---

[Java track](README.md) | Previous: [5. Subqueries](05-subquery.md) | Next: [7. Update, insert, delete](07-dml.md) | [Kotlin version of this page](../kotlin/06-select-group.md)
