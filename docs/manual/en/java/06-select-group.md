# 6. Select, aggregates and group by (Java)

> [!NOTE]
> **Status**
> - **Implemented and tested** on H2 2.3, MariaDB 10.11 and Oracle 23.26 (Hibernate 6.6.5, milestone M9, decision D27): `select` of one value, a Java record or a DTO; `groupBy` with one or many keys; `having`; the `Agg` functions; `distinct()`, `limit`, `offset`, `toList`, `toSet`, `first`, `one`; `orderBy(...).select(...)`; and the checks of rules J3, J4 and J7.
> - **Designed, not implemented yet:** derived tables and CTEs (6.8 and 6.9, milestone M8-2), sorting by an aggregate and `orderBy` on a grouped query (v0.2 candidate), and join projections `(o, c) -> ...`.
> - Examples marked **(tested)** are copied from the test suite. See the [Java track README](README.md#status) for the full status.

[Java track](README.md) | [Manual home](../README.md) | Previous: [5. Subqueries](05-subquery.md) | Next: [7. Update, insert, delete](07-dml.md) | [Kotlin version of this page](../kotlin/06-select-group.md)

Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md)

---

This chapter shows how to choose **what** a query returns:

- one value per row (`select(o -> o.getPrice())`)
- a Java record or a DTO class
- aggregates (`count`, `sum`, `avg`, ...) with `groupBy` and `having`
- `distinct`, paging and the result methods of a value query
- a subquery in the FROM clause (derived table) and a CTE (`with`): designed only

## Contents

- [6.1 Example entities and result types](#61-example-entities-and-result-types)
- [6.2 Select one value](#62-select-one-value)
- [6.3 Select a record or DTO](#63-select-a-record-or-dto)
- [6.4 Aggregate functions: `Agg`](#64-aggregate-functions-agg)
- [6.5 Group by](#65-group-by)
- [6.6 Having](#66-having)
- [6.7 Distinct, paging and results](#67-distinct-paging-and-results)
- [6.8 Derived table (subquery in FROM)](#68-derived-table-subquery-in-from)
- [6.9 CTE (`with`)](#69-cte-with)
- [6.10 Rules J1 to J8](#610-rules-j1-to-j8)
- [6.11 Not in v0.1](#611-not-in-v01)

---

## 6.1 Example entities and result types

The examples use these entities (see [2. Getting started](02-getting-started.md#example-entities)):

| Entity | Fields used here |
|---|---|
| `Order` | `id`, `price`, `status`, `active`, `customer` (many-to-one) |
| `Customer` | `id`, `name`, `grade` |
| `Item` | `order`, `price`, `qty` |

Examples marked **(tested)** are copied from [`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java).
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

## 6.2 Select one value

`select` takes one lambda. The lambda returns one value. The result is a `ValueQuery` of that value.
You can call `select` after `where` and after `orderBy`. The sort, `limit` and `offset` stay.

**(tested)** [`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java), region `value`:

```java
ValueQuery<Double> q = qb.from(Order.class)
        .where(o -> o.getStatus() == 1)
        .orderBy((o, by) -> by.asc(o.getPrice()))
        .select(o -> o.getPrice());
assertEquals("select e0.price from Order e0 where e0.status = 1 order by e0.price asc", q.toHql().hql());
```

**About the SQL blocks.** `?` is a bind parameter. The lines come from [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java). It runs each query on H2 2.3, MariaDB 10.11 and Oracle 23.26 with Hibernate 6.6.5, and it fails if Hibernate's SQL changes. In this chapter the select list is the point, so it is not shortened.

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `value`:

```sql
-- H2, MariaDB, Oracle (same)
select o1_0.price
from orders o1_0
where o1_0.status=1
order by o1_0.price
```

An association path is a column, the same as in `where` (rule R24). In `select`, lambdaQL adds a `left join`, so orders without a customer are not lost (in `where`, the same path is an inner join by default):

```java
.select(o -> o.getCustomer().getName())
// HQL: select j1.name from Order e0 left join e0.customer j1
```

The record example in [6.3](#63-select-a-record-or-dto) tests this `left join`.
If the path ends with the `@Id` of the associated entity, there is no join.
`o.getCustomer().getId()` reads the foreign key column `customer_id`.

---

## 6.3 Select a record or DTO

Call a constructor inside the lambda. Each constructor argument is a value expression.
A Java record and a normal DTO class with a constructor both work.

**(tested)** [`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java), region `record`:

```java
// only the columns are selected; the record is made in Java. The path uses a left join,
// so the order without a customer stays (customerName is null)
ValueQuery<OrderView> q = qb.from(Order.class)
        .select(o -> new OrderView(o.getId(), o.getCustomer().getName(), o.getPrice()));
assertEquals("select e0.id, j1.name, e0.price from Order e0 left join e0.customer j1", q.toHql().hql());
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `record`:

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

| Result type | Simple `select` | Row type of a derived table or CTE (designed) |
|---|---|---|
| One value (`o.getPrice()`) | yes | – |
| Java record | yes | yes |
| Normal DTO class (constructor) | yes | **no** (J5) |

---

## 6.4 Aggregate functions: `Agg`

Aggregate functions are **static methods of `org.lambdaql.Agg`**.
They are SQL standard functions, so lambdaQL provides them. (For your own DB functions, see [8. Procedures and functions](08-procedures-functions.md).)
`Agg` is the only static helper class in lambdaQL.

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

### Aggregate over all rows (no group by)

**(tested)** [`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java), region `total`:

```java
// aggregates without groupBy: one row over all matching orders
ValueQuery<Total> q = qb.from(Order.class)
        .where(o -> o.getStatus() == 1)
        .select(o -> new Total(Agg.count(), Agg.avg(o.getPrice())));
assertEquals("select count(*), avg(e0.price) from Order e0 where e0.status = 1", q.toHql().hql());
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `total`:

```sql
-- H2, MariaDB, Oracle (same)
select count(*),avg(o1_0.price)
from orders o1_0
where o1_0.status=1
```

This query always gives one row. The test reads it with `q.one()`.

### NULL in aggregates (rule J4)

| Function | No rows, or all values NULL |
|---|---|
| `sum`, `avg`, `min`, `max` | `NULL` |
| `count` | `0` |

This is normal SQL behavior.
If the NULL goes into a primitive constructor argument (`double`, `long`), lambdaQL throws `IllegalStateException`. The message names the argument and tells you to use the wrapper type (`Double`, `Long`). See the tested example in [6.10](#610-rules-j1-to-j8).

---

## 6.5 Group by

Write the group key with `groupBy`. lambdaQL does not guess the key.
`groupBy` returns a grouped query. Call `having(...)` (optional) and then `select(...)`.

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

### Many keys

Return a list of keys with `List.of(...)`:

**(tested)** [`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java), region `group-many`:

```java
// many keys: List.of(...) (Kotlin listOf(...), a to b, Triple)
ValueQuery<KeyCount> q = qb.from(Order.class)
        .where(o -> o.getCustomer() != null)
        .groupBy(o -> List.of(o.getCustomer().getId(), o.getStatus()))
        .select(o -> new KeyCount(o.getCustomer().getId(), o.getStatus(), Agg.count()));
assertEquals("select e0.customer.id, e0.status, count(*) from Order e0 where e0.customer is not null"
        + " group by e0.customer.id, e0.status", q.toHql().hql());
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `group-many`:

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

### Every non-aggregate value must be a key (rule J3)

When a query has aggregates, each value in `select` and `having` that is **not** an aggregate must be a `groupBy` key. This is the SQL rule. lambdaQL checks it at analysis time.

```java
// Error: o.getStatus() is not a group key
qb.from(Order.class)
  .groupBy(o -> o.getCustomer().getId())
  .select(o -> new KeyCount(o.getCustomer().getId(), o.getStatus(), Agg.count()));
// → LambdaAnalysisException
```

The tested example is in [6.10](#610-rules-j1-to-j8).

---

## 6.6 Having

`having` is a condition on groups. It uses the same lambda rules as `where` ([3. Conditions](03-where.md)), and it can use `Agg`.
You can call `having` many times. The conditions are joined with `AND`.

**(tested)** [`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java), region `group-by`:

```java
ValueQuery<Stat> q = qb.from(Order.class)
        .groupBy(o -> o.getCustomer().getId())
        .having(o -> Agg.sum(o.getPrice()) > 200)
        .select(o -> new Stat(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice())));
assertEquals("select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id"
        + " having sum(e0.price) > 200.0", q.toHql().hql());
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `group-by`:

```sql
-- H2, MariaDB, Oracle (same)
select o1_0.customer_id,count(*),sum(o1_0.price)
from orders o1_0
group by o1_0.customer_id
having sum(o1_0.price)>200.0
```

`Agg.sum(o.getPrice())` is a `double` here, so javac compares it with `200.0`. The constant stays a literal (decision D37).

---

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

**(tested)** [`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java), region `distinct`:

```java
ValueQuery<Double> q = qb.from(Order.class)
        .orderBy((o, by) -> by.desc(o.getPrice()))
        .select(o -> o.getPrice())
        .distinct();
assertEquals("select distinct e0.price from Order e0 order by e0.price desc", q.toHql().hql());
// toSet() always uses select distinct
List<Double> prices = q.toSet().stream().toList();
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `distinct`:

```sql
-- H2, MariaDB, Oracle (same)
select distinct o1_0.price
from orders o1_0
order by o1_0.price desc
```

- With `distinct()`, every sort key must be a selected value (rule J7). Otherwise lambdaQL throws `LambdaAnalysisException` at analysis time.
- `toSet()` on a value query always uses `select distinct`. Repeated values are expected here, so there is no size check (unlike `toSet()` on entities, see [2. Getting started](02-getting-started.md#toset)).

**Paging.** `limit` and `offset` work like in [4. Sorting and paging](04-order-paging.md).
With paging, lambdaQL adds the `id` as the last sort key, so the pages do not overlap (rule S9).
It does not add it to a grouped query or a `distinct` query, because the `id` is not in their select list.

```java
ValueQuery<Double> paged = qb.from(Order.class)
        .orderBy((o, by) -> by.desc(o.getPrice()))
        .select(o -> o.getPrice())
        .limit(2).offset(1);
// HQL: select e0.price from Order e0 order by e0.price desc, e0.id asc
```

The test method `pagingAndFirst` in [`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java) checks this HQL, `first()` and the page.

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `value-page`:

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

## 6.8 Derived table (subquery in FROM)

> [!NOTE]
> **Designed, not implemented yet** (milestone M8-2). The HQL below is the **planned** output.

Pass a lambda to `qb.from`. The lambda gets a subquery factory (`sub`) and builds the inner query.
The inner `select` gives the row type. In the outer query, use the row like an entity.

```java
record CustomerTotal(Long customerId, long cnt, Double total) {}

List<CustomerTotal> rows = qb.from(sub -> sub.from(Order.class)
        .groupBy(o -> o.getCustomer().getId())
        .select(o -> new CustomerTotal(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice()))))
    .where(r -> r.total() > 100)
    .toList();
```

HQL (the select aliases `customerId`, `cnt`, `total` name the columns, rule J5):

```sql
select r.customerId, r.cnt, r.total
from (select o.customer.id as customerId, count(*) as cnt, sum(o.price) as total
      from Order o group by o.customer.id) r
where r.total > 100
```

**About the planned SQL blocks.** [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java) gives the planned HQL of this section and of 6.9 to Hibernate directly and records the SQL on H2 2.3, MariaDB 10.11 and Oracle 23.26 (Hibernate 6.6.5). lambdaQL does not make these queries yet. The test fails if Hibernate's SQL changes.

**SQL by database (planned)** — Hibernate's translation of the planned HQL, recorded by `PlannedSqlTest`; lambdaQL does not make this query yet. From `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql), example `06-derived`:

```sql
-- H2
select r1_0.customerId,r1_0.cnt,r1_0.total
from (select o1_0.customer_id,count(*),sum(o1_0.price) from orders o1_0 group by o1_0.customer_id) r1_0(customerId,cnt,total)
where r1_0.total>100

-- MariaDB, Oracle
select r1_0.customerId,r1_0.cnt,r1_0.total
from (select o1_0.customer_id customerId,count(*) cnt,sum(o1_0.price) total from orders o1_0 group by o1_0.customer_id) r1_0
where r1_0.total>100
```

H2 puts the column list on the alias (`r1_0(customerId,cnt,total)`). MariaDB and Oracle put the column aliases inside the subquery.

Rules:

- The row type must be a **Java record** (rule J5).
  lambdaQL takes the column aliases from the order of the record components.
- A normal DTO class is not allowed here. lambdaQL cannot know the names of its constructor parameters.
- In the outer lambda, read the row with the record accessor: `r.total()`.

---

## 6.9 CTE (`with`)

> [!NOTE]
> **Designed, not implemented yet** (milestone M8-2). The HQL below is the **planned** output.

Use `qb.with(...)` to make a handle. Then use the handle in `qb.from(...)`.
You can use the same handle more than once.

```java
var t = qb.with(sub -> sub.from(Order.class)
        .groupBy(o -> o.getCustomer().getId())
        .select(o -> new CustomerTotal(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice()))));

List<CustomerTotal> rows = qb.from(t).where(r -> r.total() > 100).toList();
```

HQL (the select aliases `customerId`, `cnt`, `total` name the columns, rule J5):

```sql
with t as (select o.customer.id as customerId, count(*) as cnt, sum(o.price) as total
           from Order o group by o.customer.id)
select x.customerId, x.cnt, x.total from t x where x.total > 100
```

**SQL by database (planned)** — Hibernate's translation of the planned HQL, recorded by `PlannedSqlTest`; lambdaQL does not make this query yet. From `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql), example `06-cte`:

```sql
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

- lambdaQL always gives the CTE an alias in the outer query (`from t x`). HQL needs it (rule J6).
- The CTE columns are named with select aliases (`as customerId`). HQL does not accept a column list after the CTE name; Hibernate adds it in the SQL (rule J6).
- MariaDB and Oracle run a real `with`. On H2, Hibernate expands it into a derived table. The result is the same (checked).
- The row type rule is the same as for a derived table: a record (J5).
- Use `var` for the handle. Its type name is not final.

---

## 6.10 Rules J1 to J8

| # | Rule | Status |
|---|---|---|
| J1 | A `select` lambda returns **one value expression** or **one constructor call**. Each constructor argument is a value expression (rules in [3. Conditions](03-where.md)). Association paths that are used only in `select` become `left join` aliases, so no rows are lost. If `where` uses the same path, `select` uses the `where` join (R24). | Implemented (tested) |
| J2 | For a constructor, lambdaQL selects only the columns and calls the constructor in Java. It does not write `select new`. | Implemented (tested) |
| J3 | With aggregates, every non-aggregate value in `select` and `having` (and in the values of `insert ... select`) must be a `groupBy` key. Otherwise: an error at analysis time. | Implemented (tested). Today the error is `LambdaAnalysisException`; `TranslationException` is designed ([11](../11-errors-and-languages.md)) |
| J4 | `sum`/`avg`/`min`/`max` give NULL when there are no rows or all values are NULL. `count` gives 0. Use boxed types for values that can be NULL. A NULL for a primitive constructor argument throws `IllegalStateException`, which names the argument. | Implemented (tested) |
| J5 | Row type of a derived table or CTE: a **Java record** (Kotlin code can also use a data class). Normal DTO classes are allowed only in simple `select`. | Designed (M8-2) |
| J6 | A CTE made with `qb.with(...)` names its columns with select aliases: `with t as (select o.customer.id as customerId, ...) select x.customerId ... from t x`. HQL does not accept a column list after the name: Hibernate rejects `with t (col list) as (...)` with a `SyntaxException`. Hibernate writes the column list in the SQL itself (`with t (customerId,cnt,total) as (...)` on MariaDB and Oracle); H2 turns the CTE into a derived table. The outer query always uses an alias for it (`from t x`). | Designed (M8-2) |
| J7 | `distinct()` renders `select distinct`. With sorting, the sort keys must be in the select list (a DB rule). lambdaQL checks this at analysis time. | Implemented (tested) |
| J8 | Select works together with sorting ([4. Sorting and paging](04-order-paging.md)), paging and subqueries ([5. Subqueries](05-subquery.md)). Sorting by an aggregate (`by.desc(s.total())`) is a v0.2 candidate. | Implemented, except the sort by an aggregate |

**(tested)** [`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java), region `rules`:

```java
// J3: a value without an aggregate must be a group key
assertThrows(LambdaAnalysisException.class, () -> qb.from(Order.class)
        .groupBy(o -> o.getCustomer().getId())
        .select(o -> new KeyCount(o.getCustomer().getId(), o.getStatus(), Agg.count())));
// aggregates belong in having, not in where
assertThrows(LambdaAnalysisException.class, () -> qb.from(Order.class).where(o -> Agg.count() > 1));
// J7: with distinct, the sort key must be selected
assertThrows(LambdaAnalysisException.class, () -> qb.from(Order.class)
        .orderBy((o, by) -> by.asc(o.getId())).select(o -> o.getPrice()).distinct().toHql());
// J4: the group without orders gives NULL; a primitive argument cannot take it
assertThrows(IllegalStateException.class, () -> qb.from(Order.class)
        .where(o -> o.getStatus() == 9)
        .select(o -> new Bad(null, Agg.sum(o.getPrice()))).toList());
```

`Bad` is `record Bad(Integer customerId, double total)`. No order has status 9, so `sum` is NULL, and the primitive `double` cannot take it.

---

## 6.11 Not in v0.1

| Item | Status |
|---|---|
| Sort by an aggregate result or a select alias, for example `orderBy((s, by) -> by.desc(s.total()))`, and `orderBy` on a grouped query | v0.2 candidate |
| Derived tables and CTEs (6.8, 6.9) | designed, not implemented yet (milestone M8-2) |
| User aggregate functions and window functions | v0.2 candidate |
| Projection over joins, `select((o, c) -> new OrderView(o.getId(), c.getName(), o.getPrice()))` | planned; the join syntax is not final yet |

Today the errors from this chapter (wrong group key, `Agg` in `where`, a sort key that is not selected with `distinct`) are `LambdaAnalysisException`. The `[LQL-xxxx]` codes and the `LambdaQLException` subclasses are designed. See [11. Errors and languages](../11-errors-and-languages.md).

---

[Java track](README.md) | Previous: [5. Subqueries](05-subquery.md) | Next: [7. Update, insert, delete](07-dml.md) | [Kotlin version of this page](../kotlin/06-select-group.md)
