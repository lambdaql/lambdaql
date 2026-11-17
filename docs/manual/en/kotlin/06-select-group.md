# 6. Select, aggregates and group by (Kotlin)

> [!NOTE]
> **Status**
> - **Implemented** (milestone M9, decision D27) and tested on H2 2.3, MariaDB 10.11 and Oracle 23.26 (Hibernate 6.6.5): `select` of one value, a data class, a `Pair` or a `Triple`; `groupBy` with one key or many keys (`listOf`, `a to b`, `Triple`); `having`; the `Agg` functions; `distinct()`, `limit`, `offset`, `toList`, `toSet`, `first`, `one`; `orderBy { }.select { }`; and the checks of rules J3, J4 and J7. The tests use **Java** lambdas. **Kotlin lambdas are not tested in the build yet.**
> - The Kotlin forms in this chapter (data class, `to`, `listOf` keys with `it.customer?.id`, `Triple`, `Agg` in `select` and `having`) were compiled with kotlinc 2.3.21 outside the build. lambdaQL gives the same HQL for them as for the Java forms.
> - **Designed, not implemented yet:** derived tables and CTEs (6.8 and 6.9, milestone M8-2), sorting by an aggregate and `orderBy` on a grouped query (v0.2 candidate), and join projections `{ o, c -> ... }`.
> - The Kotlin code shows the tested Java queries in Kotlin syntax. The tested Java examples are in [the Java version of this page](../java/06-select-group.md). See the [track README](README.md) for the full status.

[Kotlin track](README.md) | [Manual home](../README.md) | Previous: [5. Subqueries](05-subquery.md) | Next: [7. Update, insert, delete](07-dml.md) | [Java version of this page](../java/06-select-group.md)

Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md)

---

This chapter shows how to choose **what** a query returns:

- one value per row (`select { it.price }`)
- a Kotlin data class, a `Pair` or a `Triple`
- aggregates (`count`, `sum`, `avg`, ...) with `groupBy` and `having`
- `distinct`, paging and the result functions of a value query
- a subquery in the FROM clause (derived table) and a CTE (`with`): designed only

## Contents

- [6.1 Example entities and result types](#61-example-entities-and-result-types)
- [6.2 Select one value](#62-select-one-value)
- [6.3 Select a data class, Pair or Triple](#63-select-a-data-class-pair-or-triple)
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

| Entity | Properties used here |
|---|---|
| `Order` | `id`, `price`, `status`, `active`, `customer` (many-to-one) |
| `Customer` | `id`, `name`, `grade` |
| `Item` | `order`, `price`, `qty` |

And these result types:

```kotlin
data class OrderView(val id: Long?, val customerName: String?, val price: Double?)
data class Stat(val customerId: Long?, val count: Long, val total: Double?)
data class KeyCount(val customerId: Long?, val status: Int?, val count: Long)
data class Total(val count: Long, val average: Double?)
```

> [!TIP]
> Use nullable types (`Double?`, `String?`) for results.
> A column or an aggregate like `sum` can be NULL (rule J4). A non-null `Double` or `Long` parameter is a JVM primitive, and a non-null `String` parameter does not accept NULL either.
> If a NULL goes into such a parameter, lambdaQL throws `IllegalStateException`. For a primitive, the message names the argument and tells you to use the wrapper type.
> `count` never gives NULL, so `count: Long` is fine.

There are no Kotlin tests yet. The same queries written in Java are tested in [`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java) (H2, MariaDB and Oracle). See [the Java version of this page](../java/06-select-group.md) for the tested examples.

---

## 6.2 Select one value

`select` takes one lambda. The lambda returns one value. The result is a `ValueQuery` of that value.
You can call `select` after `where` and after `orderBy`. The sort, `limit` and `offset` stay.

```kotlin
val prices: List<Double?> = qb.from(Order::class.java)
    .where { it.status == 1 }
    .orderBy { o, by -> by.asc(o.price) }
    .select { it.price }
    .toList()
// HQL: select e0.price from Order e0 where e0.status = 1 order by e0.price asc nulls first
```

`price` is a `Double?` here, so the sort gets `nulls first` (see [4.4](04-order-paging.md#44-null-ordering-is-the-same-on-every-database)). In the Java test `price` is a `double`, so its HQL (example `value`) has no `nulls` part.

**About the SQL blocks.** `?` is a bind parameter. The lines come from [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java). It runs each query on H2 2.3, MariaDB 10.11 and Oracle 23.26 with Hibernate 6.6.5, and it fails if Hibernate's SQL changes. In this chapter the select list is the point, so it is not shortened. The test uses Java lambdas. A Kotlin lambda with the same meaning gives the same HQL, so it gets the same SQL.

**SQL by database (from the Java test run)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `value`:

```sql
-- H2, MariaDB, Oracle (same)
select o1_0.price
from orders o1_0
where o1_0.status=1
order by o1_0.price
```

An association path is a column, the same as in `where` (rule R24). In `select`, lambdaQL adds a `left join`, so orders without a customer are not lost (in `where`, the same path is an inner join by default):

```kotlin
.select { it.customer?.name }
// HQL: select j1.name from Order e0 left join e0.customer j1
```

`it.customer!!.name` gives the same HQL. The data class example in [6.3](#63-select-a-data-class-pair-or-triple) shows this `left join`.
If the path ends with the `@Id` of the associated entity, there is no join.
`it.customer?.id` reads the foreign key column `customer_id`.

Other value forms (Kotlin-only forms, designed; see [9. Kotlin notes](09-kotlin-notes.md)):

```kotlin
.select { it.description ?: "" }              // coalesce(e0.description, '')
.select { it.customer?.name ?: "none" }       // coalesce(j1.name, 'none') + left join
.select { (it.price ?: 0.0) * 2 }             // coalesce(e0.price, 0.0) * 2
```

A boolean elvis (`select { it.flag ?: false }`) and a value `if`/`when` are rejected (limits KC21, KC6).

---

## 6.3 Select a data class, Pair or Triple

Call a constructor inside the lambda. Each constructor argument is a value expression.

```kotlin
// data class
val list: List<OrderView> = qb.from(Order::class.java)
    .select { OrderView(it.id, it.customer?.name, it.price) }
    .toList()
// HQL: select e0.id, j1.name, e0.price from Order e0 left join e0.customer j1

// Pair (Triple is also OK)
val pairs: List<Pair<String?, Double?>> = qb.from(Order::class.java)
    .select { it.customer?.name to it.price }
    .toList()
// HQL: select j1.name, e0.price from Order e0 left join e0.customer j1
```

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `record` (the data class example):

```sql
-- H2, MariaDB, Oracle (same)
select o1_0.id,c1_0.name,o1_0.price
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
```

lambdaQL does **not** write `select new com.acme.OrderView(...)` (rule J2).
It selects the columns and calls the constructor itself. This is the same for a data class, a `Pair` and a `Triple`.
This avoids problems with long class names and nested class names.
lambdaQL converts each value to the type of the constructor parameter (for example, Hibernate's `Integer` to a `Long?` parameter).

What you can return:

| Result type | Simple `select` | Row type of a derived table or CTE (designed) |
|---|---|---|
| One value (`it.price`) | yes | – |
| Kotlin data class | yes | yes |
| Normal class with a constructor | yes | **no** (J5) |
| `Pair` / `Triple` | yes | no |
| Java record (from a Java module) | yes | yes |

---

## 6.4 Aggregate functions: `Agg`

Aggregate functions are **static methods of `org.lambdaql.Agg`**.
They are SQL standard functions, so lambdaQL provides them. (For your own DB functions, see [8. Procedures and DB functions](08-procedures-functions.md).)

| Function | SQL |
|---|---|
| `Agg.count()` | `count(*)` |
| `Agg.count(x)` | `count(x)` |
| `Agg.countDistinct(x)` | `count(distinct x)` |
| `Agg.sum(x)` | `sum(x)` |
| `Agg.avg(x)` | `avg(x)` |
| `Agg.min(x)` | `min(x)` |
| `Agg.max(x)` | `max(x)` |

Types, as Kotlin sees them:

| Function | Returns |
|---|---|
| `count()`, `count(x)`, `countDistinct(x)` | `Long` |
| `sum(x)` | the type of `x` (a platform type) |
| `avg(x)` | `Double` (can be NULL in SQL; treat it as `Double?`) |
| `min(x)`, `max(x)` | the type of `x` (any `Comparable`, nullable is fine) |

An import is optional. You can import one function:

```kotlin
import org.lambdaql.Agg.sum

.having { sum(it.price) > 200 }
```

This does not clash with Kotlin standard library extensions like `xs.sum()` or `xs.count()` (checked with the compiler).

Important points:

- The return value is only a **placeholder**. The lambda never runs, so the value is never used.
- If you call `Agg.sum(...)` **outside** a lambdaQL lambda, it throws `IllegalStateException`.
- You can use `Agg` in `select`, in `having` and in the values of `insert ... select` ([7.5](07-5-insert.md)).
- You cannot use `Agg` in `where`. Use `having` for conditions on aggregates. Using `Agg` in `where` is an error at analysis time.

### Aggregate over all rows (no group by)

```kotlin
val t: Total? = qb.from(Order::class.java)
    .where { it.status == 1 }
    .select { Total(Agg.count(), Agg.avg(it.price)) }
    .one()
    .orElse(null)
// HQL: select count(*), avg(e0.price) from Order e0 where e0.status = 1
```

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `total`:

```sql
-- H2, MariaDB, Oracle (same)
select count(*),avg(o1_0.price)
from orders o1_0
where o1_0.status=1
```

This query always gives one row.

### NULL in aggregates (rule J4)

| Function | No rows, or all values NULL |
|---|---|
| `sum`, `avg`, `min`, `max` | `NULL` |
| `count` | `0` |

This is normal SQL behavior. Use nullable parameters (`Double?`) for these results (see the tip in [6.1](#61-example-entities-and-result-types)).

---

## 6.5 Group by

Write the group key with `groupBy`. lambdaQL does not guess the key.
`groupBy` returns a grouped query. Call `having { }` (optional) and then `select { }`.

### One key

```kotlin
val stats: List<Stat> = qb.from(Order::class.java)
    .groupBy { it.customer?.id }
    .select { Stat(it.customer?.id, Agg.count(), Agg.sum(it.price)) }
    .toList()
// HQL: select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id
```

The key ends with `@Id`, so there is no join. Orders without a customer form one group with a NULL key.
The example with `having` is in [6.6](#66-having).

### Many keys

Return a list of keys with `listOf(...)`, or use a `Pair` (`a to b`) or a `Triple`.

```kotlin
val counts: List<KeyCount> = qb.from(Order::class.java)
    .where { it.customer != null }
    .groupBy { listOf(it.customer?.id, it.status) }
    .select { KeyCount(it.customer?.id, it.status, Agg.count()) }
    .toList()
// HQL: select e0.customer.id, e0.status, count(*) from Order e0 where e0.customer is not null
//      group by e0.customer.id, e0.status

.groupBy { it.customer?.id to it.status }                     // Pair: same result
.groupBy { Triple(it.customer?.id, it.status, it.active) }    // three keys
```

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `group-many`:

```sql
-- H2, MariaDB, Oracle (same)
select o1_0.customer_id,o1_0.status,count(*)
from orders o1_0
where o1_0.customer_id is not null
group by o1_0.customer_id,o1_0.status
```

The keys are used in the order you write them.
`listOf(...)` in `groupBy` is the **only** place where a list made inside the lambda is allowed.

Not allowed as a key list:

| Form | Why |
|---|---|
| `arrayOf(a, b)` | does not compile when types are mixed |
| `setOf(a, b)` | a set removes duplicates and has no order |

### Every non-aggregate value must be a key (rule J3)

When a query has aggregates, each value in `select` and `having` that is **not** an aggregate must be a `groupBy` key. This is the SQL rule. lambdaQL checks it at analysis time.

```kotlin
// Error: it.status is not a group key
qb.from(Order::class.java)
    .groupBy { it.customer?.id }
    .select { KeyCount(it.customer?.id, it.status, Agg.count()) }
// → LambdaAnalysisException
```

The same check is tested in Java (region `rules`, see [6.10](#610-rules-j1-to-j8)).

---

## 6.6 Having

`having` is a condition on groups. It uses the same lambda rules as `where` ([3. Conditions](03-where.md)), and it can use `Agg`.
You can call `having` many times. The conditions are joined with `AND`.

```kotlin
val big: List<Stat> = qb.from(Order::class.java)
    .groupBy { it.customer?.id }
    .having { Agg.sum(it.price) > 200 }
    .select { Stat(it.customer?.id, Agg.count(), Agg.sum(it.price)) }
    .toList()
// HQL: select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id
//      having sum(e0.price) > 200.0
```

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `group-by`:

```sql
-- H2, MariaDB, Oracle (same)
select o1_0.customer_id,count(*),sum(o1_0.price)
from orders o1_0
group by o1_0.customer_id
having sum(o1_0.price)>200.0
```

`Agg.sum` is a Java method, so its result is a platform type. `Agg.sum(it.price) > 200` compiles without `!!`. If you add `!!`, lambdaQL ignores it (decision D23).

---

## 6.7 Distinct, paging and results

`select { }` returns a `ValueQuery`. It has these functions:

| Function | What it does |
|---|---|
| `distinct()` | `select distinct` |
| `limit(n)`, `offset(n)` | paging |
| `toList()` | runs the query; the values in order |
| `toSet()` | runs the query with `select distinct`; a `SequencedSet` in order |
| `first()` | the first value as a Java `Optional` |
| `one()` | the only value as a Java `Optional`. Two or more rows: `NonUniqueResultException` |
| `toHql()` | the HQL and the parameters, without running the query |

```kotlin
val q = qb.from(Order::class.java)
    .orderBy { o, by -> by.desc(o.price) }
    .select { it.price }
    .distinct()
// HQL: select distinct e0.price from Order e0 order by e0.price desc nulls last
val prices = q.toSet()      // always select distinct
```

**SQL by database (from the Java test run)** — the Java test `price` is a `double`, so its HQL has no `nulls` part. From `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `distinct`:

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

```kotlin
val page = qb.from(Order::class.java)
    .orderBy { o, by -> by.desc(o.price) }
    .select { it.price }
    .limit(2).offset(1)
    .toList()
// HQL: select e0.price from Order e0 order by e0.price desc nulls last, e0.id asc
```

**SQL by database (from the Java test run)** — the Java test `price` is a `double`, so its HQL has no `nulls` part. From `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `value-page`:

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

```kotlin
data class CustomerTotal(val customerId: Long?, val cnt: Long, val total: Double?)

val rows: List<CustomerTotal> = qb.from { sub ->
        sub.from(Order::class.java)
            .groupBy { it.customer?.id }
            .select { CustomerTotal(it.customer?.id, Agg.count(), Agg.sum(it.price)) }
    }
    .where { r -> r.total!! > 100 }
    .toList()
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

- The row type must be a **Kotlin data class** (or a Java record) (rule J5).
  lambdaQL takes the column aliases from the data class `componentN` order.
- A normal class is not allowed here. lambdaQL cannot know the names of its constructor parameters.
- In the outer lambda, read the row with its properties (`r.total`).

---

## 6.9 CTE (`with`)

> [!NOTE]
> **Designed, not implemented yet** (milestone M8-2). The HQL below is the **planned** output.

Use `qb.with { ... }` to make a handle. Then use the handle in `qb.from(...)`.
You can use the same handle more than once.

```kotlin
val t = qb.with { sub ->
    sub.from(Order::class.java)
        .groupBy { it.customer?.id }
        .select { CustomerTotal(it.customer?.id, Agg.count(), Agg.sum(it.price)) }
}

val rows = qb.from(t).where { r -> r.total!! > 100 }.toList()
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
- The row type rule is the same as for a derived table: data class or record (J5).

---

## 6.10 Rules J1 to J8

| # | Rule | Status |
|---|---|---|
| J1 | A `select` lambda returns **one value expression** or **one constructor call**. Each constructor argument is a value expression (rules in [3. Conditions](03-where.md)). Association paths that are used only in `select` become `left join` aliases, so no rows are lost. If `where` uses the same path, `select` uses the `where` join (R24). | Implemented (tested with Java lambdas) |
| J2 | For a constructor, lambdaQL selects only the columns and calls the constructor itself. It does not write `select new`. `Pair`/`Triple` are built the same way. | Implemented (tested with Java lambdas) |
| J3 | With aggregates, every non-aggregate value in `select` and `having` (and in the values of `insert ... select`) must be a `groupBy` key. Otherwise: an error at analysis time. | Implemented (tested with Java lambdas). Today the error is `LambdaAnalysisException`; `TranslationException` is designed ([11](../11-errors-and-languages.md)) |
| J4 | `sum`/`avg`/`min`/`max` give NULL when there are no rows or all values are NULL. `count` gives 0. Use nullable constructor parameters. A NULL for a non-null parameter throws `IllegalStateException`. | Implemented (tested with Java lambdas) |
| J5 | Row type of a derived table or CTE: **Kotlin data class or Java record only**. Normal classes are allowed only in a simple `select`. | Designed (M8-2) |
| J6 | A CTE made with `qb.with { }` names its columns with select aliases: `with t as (select o.customer.id as customerId, ...) select x.customerId ... from t x`. HQL does not accept a column list after the name: Hibernate rejects `with t (col list) as (...)` with a `SyntaxException`. Hibernate writes the column list in the SQL itself (`with t (customerId,cnt,total) as (...)` on MariaDB and Oracle); H2 turns the CTE into a derived table. The outer query always uses an alias for it (`from t x`). | Designed (M8-2) |
| J7 | `distinct()` renders `select distinct`. With sorting, the sort keys must be in the select list (a DB rule). lambdaQL checks this at analysis time. | Implemented (tested with Java lambdas) |
| J8 | Select works together with sorting ([4. Sorting and paging](04-order-paging.md)), paging and subqueries ([5. Subqueries](05-subquery.md)). Sorting by an aggregate (`by.desc(s.total)`) is a v0.2 candidate. | Implemented, except the sort by an aggregate |

The Java test region `rules` in [`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java) checks J3, `Agg` in `where`, J7 and J4. See [the Java version of this page](../java/06-select-group.md#610-rules-j1-to-j8).

---

## 6.11 Not in v0.1

| Item | Status |
|---|---|
| Sort by an aggregate result or a select alias, for example `orderBy { s, by -> by.desc(s.total) }`, and `orderBy` on a grouped query | v0.2 candidate |
| Derived tables and CTEs (6.8, 6.9) | designed, not implemented yet (milestone M8-2) |
| User aggregate functions and window functions | v0.2 candidate |
| Projection over joins, `select { o, c -> OrderView(o.id, c.name, o.price) }` | planned; the join syntax is not final yet |

Today the errors from this chapter (wrong group key, `Agg` in `where`, a sort key that is not selected with `distinct`) are `LambdaAnalysisException`. The `[LQL-xxxx]` codes and the `LambdaQLException` subclasses are designed. See [11. Errors and languages](../11-errors-and-languages.md).

---

[Kotlin track](README.md) | Previous: [5. Subqueries](05-subquery.md) | Next: [7. Update, insert, delete](07-dml.md) | [Java version of this page](../java/06-select-group.md)
