# 6. Select, aggregates and group by (Java)

> [!NOTE]
> **Status: designed, not implemented yet.**
> Everything in this chapter is a decided design (decision D27, milestone M9).
> The syntax was compiled and checked with prototypes on H2, MariaDB and Oracle.
> It does **not** run in lambdaQL today. Today only the analysis core and `where` + `toList()` work
> (tested on H2, MariaDB and Oracle). See the [Java track README](README.md#status) for the full status.

[Java track](README.md) | [Manual home](../README.md) | Previous: [5. Subqueries](05-subquery.md) | Next: [7. Update, insert, delete](07-dml.md) | [Kotlin version of this page](../kotlin/06-select-group.md)

Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md)

---

This chapter shows how to choose **what** a query returns:

- one value per row (`select(o -> o.getPrice())`)
- a Java record or a DTO class
- aggregates (`count`, `sum`, `avg`, ...) with `groupBy` and `having`
- a subquery in the FROM clause (derived table) and a CTE (`with`)

## Contents

- [6.1 Example entities and result types](#61-example-entities-and-result-types)
- [6.2 Select one value](#62-select-one-value)
- [6.3 Select a record or DTO](#63-select-a-record-or-dto)
- [6.4 Aggregate functions: `Agg`](#64-aggregate-functions-agg)
- [6.5 Group by](#65-group-by)
- [6.6 Having](#66-having)
- [6.7 Derived table (subquery in FROM)](#67-derived-table-subquery-in-from)
- [6.8 CTE (`with`)](#68-cte-with)
- [6.9 Rules J1 to J8](#69-rules-j1-to-j8)
- [6.10 Not in v0.1](#610-not-in-v01)

---

## 6.1 Example entities and result types

The examples use these entities (see [2. Getting started](02-getting-started.md#example-entities)):

| Entity | Fields used here |
|---|---|
| `Order` | `id`, `price`, `status`, `active`, `customer` (many-to-one) |
| `Customer` | `id`, `name`, `grade` |
| `Item` | `order`, `price`, `qty` |

And these result types:

```java
public record OrderView(Long id, String customerName, Double price) {}
public record Stat(Long customerId, long cnt, Double total) {}
public record Total(long cnt, Double avgPrice) {}
```

> [!TIP]
> Use boxed types (`Double`, `Long`) for values that can be NULL.
> A column or an aggregate like `sum` can be NULL. A primitive `double` component cannot hold NULL.
> `count` is never NULL, so `long cnt` is fine (rule J4).

---

## 6.2 Select one value

`select` takes one lambda. The lambda returns one value. The result is a list of that value.

```java
List<Double> prices = qb.from(Order.class)
    .where(o -> o.getStatus() == 1)
    .select(o -> o.getPrice())
    .toList();
```

HQL:

```sql
select o.price from Order o where o.status = 1
```

**About the SQL blocks.** `…` is the shortened select list (all entity columns), and `?` is a bind parameter. [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java) gives the HQL of this page to Hibernate directly and records the SQL on H2 2.3, MariaDB 10.11 and Oracle 23.26 (Hibernate 6.6.5). lambdaQL does not make these queries yet. The test fails if Hibernate's SQL changes.

**SQL by database (planned)** — Hibernate's translation of the planned HQL, recorded by `PlannedSqlTest`; lambdaQL does not make this query yet. From `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql), example `06-select-value`:

```sql
-- H2, MariaDB, Oracle (same)
select o1_0.price
from orders o1_0
where o1_0.status=1
```

An association path is a column, the same as in `where` (rule R24). In `select`, lambdaQL adds a `left join`, so orders without a customer are not lost (in `where`, the same path is an inner join by default):

```java
.select(o -> o.getCustomer().getName())
// → select c1.name from Order o left join o.customer c1
```

**SQL by database (planned)** — Hibernate's translation of the planned HQL, recorded by `PlannedSqlTest`; lambdaQL does not make this query yet. From `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql), example `06-select-path`:

```sql
-- H2, MariaDB, Oracle (same)
select c1_0.name
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
```

If the path ends with the `@Id` of the associated entity, there is no join.
`o.getCustomer().getId()` reads the foreign key column `customer_id`.

---

## 6.3 Select a record or DTO

Call a constructor inside the lambda. Each constructor argument is a value expression.

```java
// a record or a normal DTO class
List<OrderView> list = qb.from(Order.class)
    .select(o -> new OrderView(o.getId(), o.getCustomer().getName(), o.getPrice()))
    .toList();
```

HQL (lambdaQL selects only the columns, rule J2):

```sql
select o.id, c1.name, o.price from Order o left join o.customer c1
```

**SQL by database (planned)** — Hibernate's translation of the planned HQL, recorded by `PlannedSqlTest`; lambdaQL does not make this query yet. From `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql), example `06-dto`:

```sql
-- H2, MariaDB, Oracle (same)
select o1_0.id,c1_0.name,o1_0.price
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
```

lambdaQL does **not** write `select new com.acme.OrderView(...)`.
It selects the columns and asks Hibernate for typed results of `OrderView`.
This avoids problems with long class names and nested class names.

What you can return:

| Result type | Simple `select` | Row type of a derived table or CTE |
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
- You cannot use `Agg` in `where`. Use `having` for conditions on aggregates. Using `Agg` in `where` is an error at analysis time.

### Aggregate over all rows (no group by)

```java
List<Total> t = qb.from(Order.class)
    .where(o -> o.getStatus() == 1)
    .select(o -> new Total(Agg.count(), Agg.avg(o.getPrice())))
    .toList();
// → select count(*), avg(o.price) from Order o where o.status = 1
```

**SQL by database (planned)** — Hibernate's translation of the planned HQL, recorded by `PlannedSqlTest`; lambdaQL does not make this query yet. From `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql), example `06-aggregate`:

```sql
-- H2, MariaDB, Oracle (same)
select count(*),avg(o1_0.price)
from orders o1_0
where o1_0.status=1
```

### NULL in aggregates (rule J4)

| Function | No rows, or all values NULL |
|---|---|
| `sum`, `avg`, `min`, `max` | `NULL` |
| `count` | `0` |

This is normal SQL behavior.

---

## 6.5 Group by

Write the group key with `groupBy`. lambdaQL does not guess the key.

### One key

```java
List<Stat> stats = qb.from(Order.class)
    .groupBy(o -> o.getCustomer().getId())
    .select(o -> new Stat(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice())))
    .toList();
```

HQL (the key ends with `@Id`, so there is no join):

```sql
select o.customer.id, count(*), sum(o.price) from Order o group by o.customer.id
```

**SQL by database (planned)** — Hibernate's translation of the planned HQL, recorded by `PlannedSqlTest`; lambdaQL does not make this query yet. From `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql), example `06-group-by`:

```sql
-- H2, MariaDB, Oracle (same)
select o1_0.customer_id,count(*),sum(o1_0.price)
from orders o1_0
group by o1_0.customer_id
```

### Many keys

Return a list of keys with `List.of(...)`:

```java
.groupBy(o -> List.of(o.getCustomer().getId(), o.getStatus()))
// → group by o.customer.id, o.status
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
  .select(o -> new Stat2(o.getCustomer().getId(), o.getStatus(), Agg.count()));   // Stat2: some record
// → TranslationException
```

---

## 6.6 Having

`having` is a condition on groups. It uses the same lambda rules as `where` ([3. Conditions](03-where.md)), and it can use `Agg`.

```java
List<Stat> big = qb.from(Order.class)
    .groupBy(o -> o.getCustomer().getId())
    .having(o -> Agg.sum(o.getPrice()) > 1000)
    .select(o -> new Stat(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice())))
    .toList();
```

HQL:

```sql
select o.customer.id, count(*), sum(o.price)
from Order o
group by o.customer.id
having sum(o.price) > 1000
```

**SQL by database (planned)** — Hibernate's translation of the planned HQL, recorded by `PlannedSqlTest`; lambdaQL does not make this query yet. From `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql), example `06-group-having`:

```sql
-- H2, MariaDB, Oracle (same)
select o1_0.customer_id,count(*),sum(o1_0.price)
from orders o1_0
group by o1_0.customer_id
having sum(o1_0.price)>1000
```

`Agg.sum(o.getPrice())` returns a `Double`. javac unboxes it for `> 1000`. This is the normal number compare (rule R1).

---

## 6.7 Derived table (subquery in FROM)

Pass a lambda to `qb.from`. The lambda gets a subquery factory (`sub`) and builds the inner query.
The inner `select` gives the row type. In the outer query, use the row like an entity.

```java
List<Stat> rows = qb.from(sub -> sub.from(Order.class)
        .groupBy(o -> o.getCustomer().getId())
        .select(o -> new Stat(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice()))))
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

## 6.8 CTE (`with`)

Use `qb.with(...)` to make a handle. Then use the handle in `qb.from(...)`.
You can use the same handle more than once.

```java
var t = qb.with(sub -> sub.from(Order.class)
        .groupBy(o -> o.getCustomer().getId())
        .select(o -> new Stat(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice()))));

List<Stat> rows = qb.from(t).where(r -> r.total() > 100).toList();
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

## 6.9 Rules J1 to J8

| # | Rule |
|---|---|
| J1 | A `select` lambda returns **one value expression** or **one constructor call**. Each constructor argument is a value expression (rules in [3. Conditions](03-where.md)). Association paths that are used only in `select` become `left join` aliases, so no rows are lost. If `where` uses the same path, `select` uses the `where` join (R24). |
| J2 | For a constructor, lambdaQL selects only the columns and uses a Hibernate typed query with your class. It does not write `select new`. |
| J3 | With aggregates, every non-aggregate value in `select` and `having` must be a `groupBy` key. Otherwise: `TranslationException` at analysis time. |
| J4 | `sum`/`avg`/`min`/`max` give NULL when there are no rows or all values are NULL. `count` gives 0. Use boxed types for values that can be NULL. |
| J5 | Row type of a derived table or CTE: a **Java record** (Kotlin code can also use a data class). Normal DTO classes are allowed only in simple `select`. |
| J6 | A CTE made with `qb.with(...)` names its columns with select aliases: `with t as (select o.customer.id as customerId, ...) select x.customerId ... from t x`. HQL does not accept a column list after the name: Hibernate rejects `with t (col list) as (...)` with a `SyntaxException`. Hibernate writes the column list in the SQL itself (`with t (customerId,cnt,total) as (...)` on MariaDB and Oracle); H2 turns the CTE into a derived table. The outer query always uses an alias for it (`from t x`). |
| J7 | `distinct()` renders `select distinct`. With sorting, the sort keys must be in the select list (a DB rule). lambdaQL checks this at analysis time. |
| J8 | Select works together with sorting ([4. Sorting and paging](04-order-paging.md)), paging and subqueries ([5. Subqueries](05-subquery.md)). |

---

## 6.10 Not in v0.1

| Item | Status |
|---|---|
| Sort by an aggregate result or a select alias, for example `orderBy((s, by) -> by.desc(s.total()))` | v0.2 candidate |
| User aggregate functions and window functions | v0.2 candidate |
| Projection over joins, `select((o, c) -> new OrderView(o.getId(), c.getName(), o.getPrice()))` | planned; the join syntax is not final yet |
| Result methods other than `toList()` | planned |

Errors from this chapter (wrong group key, `Agg` in `where`, a DTO class as a derived table row) are `LambdaQLException` subclasses with a `[LQL-xxxx]` code. See [11. Errors and languages](../11-errors-and-languages.md).

---

[Java track](README.md) | Previous: [5. Subqueries](05-subquery.md) | Next: [7. Update, insert, delete](07-dml.md) | [Kotlin version of this page](../kotlin/06-select-group.md)
