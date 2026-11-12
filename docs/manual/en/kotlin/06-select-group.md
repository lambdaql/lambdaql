# 6. Select, aggregates and group by (Kotlin)

> [!NOTE]
> **Status: designed, not implemented yet.**
> Everything in this chapter is a decided design (decision D27, milestone M9).
> The Kotlin syntax was compiled and checked with prototypes (kotlinc 2.3.21), and the HQL forms were run on H2, MariaDB and Oracle.
> It does **not** run in lambdaQL today. Today only the analysis core and `where` + `toList()` work
> (tested on H2, MariaDB and Oracle). See the [track README](README.md) for the full status.

[Kotlin track](README.md) | [Manual home](../README.md) | Previous: [5. Subqueries](05-subquery.md) | Next: [7. Update, insert, delete](07-dml.md) | [Java version of this page](../java/06-select-group.md)

Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md)

This chapter shows how to choose **what** a query returns:

- one value per row (`select { it.price }`)
- a Kotlin data class, a `Pair` or a `Triple`
- aggregates (`count`, `sum`, `avg`, ...) with `groupBy` and `having`
- a subquery in the FROM clause (derived table) and a CTE (`with`)

## Contents

- [6.1 Example entities and result types](#61-example-entities-and-result-types)
- [6.2 Select one value](#62-select-one-value)
- [6.3 Select a data class, Pair or Triple](#63-select-a-data-class-pair-or-triple)
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

| Entity | Properties used here |
|---|---|
| `Order` | `id`, `price`, `status`, `active`, `customer` (many-to-one) |
| `Customer` | `id`, `name`, `grade` |
| `Item` | `order`, `price`, `qty` |

And these result types:

```kotlin
data class OrderView(val id: Long?, val customerName: String?, val price: Double?)
data class Stat(val customerId: Long?, val cnt: Long, val total: Double?)
data class Total(val cnt: Long, val avgPrice: Double?)
```

> [!TIP]
> Use nullable types (`Double?`) in data classes.
> A column or an aggregate can be NULL. If a NULL goes into a non-null
> parameter, the constructor throws a `NullPointerException` (rule J4).
> `count` never gives NULL, so `cnt: Long` is fine.

---

## 6.2 Select one value

`select` takes one lambda. The lambda returns one value. The result is a list of that value.

```kotlin
val prices: List<Double?> = qb.from(Order::class.java)
    .where { it.status == 1 }
    .select { it.price }
    .toList()
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

```kotlin
.select { it.customer?.name }
// → select c1.name from Order o left join o.customer c1
```

**SQL by database (planned)** — Hibernate's translation of the planned HQL, recorded by `PlannedSqlTest`; lambdaQL does not make this query yet. From `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql), example `06-select-path`:

```sql
-- H2, MariaDB, Oracle (same)
select c1_0.name
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
```

`it.customer!!.name` gives the same HQL.
If the path ends with the `@Id` of the associated entity, there is no join.
`it.customer?.id` reads the foreign key column `customer_id`.

Other value forms:

```kotlin
.select { it.description ?: "" }              // coalesce(o.description, '')
.select { it.customer?.name ?: "none" }       // coalesce(c1.name, 'none') + left join
.select { (it.price ?: 0.0) * 2 }             // coalesce(o.price, 0.0) * 2
```

A boolean elvis (`select { it.flag ?: false }`) and a value `if`/`when` are rejected (limits KC21, KC6).

---

## 6.3 Select a data class, Pair or Triple

Call a constructor inside the lambda. Each constructor argument is a value expression.

```kotlin
// data class
val list = qb.from(Order::class.java)
    .select { o -> OrderView(o.id, o.customer?.name, o.price) }
    .toList()

// Pair (Triple is also OK)
val pairs = qb.from(Order::class.java)
    .select { o -> o.customer?.name to o.price }
    .toList()
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
For `Pair` and `Triple`, lambdaQL reads the columns and builds the object itself.

What you can return:

| Result type | Simple `select` | Row type of a derived table or CTE |
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
| `sum(x)` | the type of `x` (for `Double?` it is `Double?`) |
| `avg(x)` | `Double` (can be NULL in SQL; treat it as `Double?`) |
| `min(x)`, `max(x)` | the type of `x` (any `Comparable`, nullable is fine) |

An import is optional. You can import one function:

```kotlin
import org.lambdaql.Agg.sum

.having { sum(it.price)!! > 1000 }
```

This does not clash with Kotlin standard library extensions like `xs.sum()` or `xs.count()` (checked with the compiler).

Important points:

- The return value is only a **placeholder**. The lambda never runs, so the value is never used.
- If you call `Agg.sum(...)` **outside** a lambdaQL lambda, it throws `IllegalStateException`.
- You cannot use `Agg` in `where`. Use `having` for conditions on aggregates. Using `Agg` in `where` is an error at analysis time.

### Aggregate over all rows (no group by)

```kotlin
val t: List<Total> = qb.from(Order::class.java)
    .where { it.status == 1 }
    .select { Total(Agg.count(), Agg.avg(it.price)) }
    .toList()
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

```kotlin
val stats = qb.from(Order::class.java)
    .groupBy { it.customer?.id }
    .select { Stat(it.customer?.id, Agg.count(), Agg.sum(it.price)) }
    .toList()
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

Return a list of keys with `listOf(...)`, or use a `Pair` (`a to b`) or a `Triple`.

```kotlin
.groupBy { listOf(it.customer?.id, it.status) }
// → group by o.customer.id, o.status

.groupBy { it.customer?.id to it.status }                     // Pair: same result
.groupBy { Triple(it.customer?.id, it.status, it.active) }    // three keys
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
data class Stat2(val customerId: Long?, val status: Int?, val cnt: Long)

// Error: it.status is not a group key
qb.from(Order::class.java)
    .groupBy { it.customer?.id }
    .select { Stat2(it.customer?.id, it.status, Agg.count()) }
// → TranslationException
```

---

## 6.6 Having

`having` is a condition on groups. It uses the same lambda rules as `where` ([3. Conditions](03-where.md)), and it can use `Agg`.

```kotlin
val big = qb.from(Order::class.java)
    .groupBy { it.customer?.id }
    .having { Agg.sum(it.price)!! > 1000 }
    .select { Stat(it.customer?.id, Agg.count(), Agg.sum(it.price)) }
    .toList()
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

`!!` only makes the code compile (`sum` of a `Double?` is a `Double?`). lambdaQL ignores it (decision D23).

---

## 6.7 Derived table (subquery in FROM)

Pass a lambda to `qb.from`. The lambda gets a subquery factory (`sub`) and builds the inner query.
The inner `select` gives the row type. In the outer query, use the row like an entity.

```kotlin
val rows: List<Stat> = qb.from { sub ->
        sub.from(Order::class.java)
            .groupBy { it.customer?.id }
            .select { Stat(it.customer?.id, Agg.count(), Agg.sum(it.price)) }
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

## 6.8 CTE (`with`)

Use `qb.with { ... }` to make a handle. Then use the handle in `qb.from(...)`.
You can use the same handle more than once.

```kotlin
val t = qb.with { sub ->
    sub.from(Order::class.java)
        .groupBy { it.customer?.id }
        .select { Stat(it.customer?.id, Agg.count(), Agg.sum(it.price)) }
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

## 6.9 Rules J1 to J8

| # | Rule |
|---|---|
| J1 | A `select` lambda returns **one value expression** or **one constructor call**. Each constructor argument is a value expression (rules in [3. Conditions](03-where.md)). Association paths that are used only in `select` become `left join` aliases, so no rows are lost. If `where` uses the same path, `select` uses the `where` join (R24). |
| J2 | For a constructor, lambdaQL selects only the columns and uses a Hibernate typed query with your class. It does not write `select new`. `Pair`/`Triple` are built by lambdaQL from the columns. |
| J3 | With aggregates, every non-aggregate value in `select` and `having` must be a `groupBy` key. Otherwise: `TranslationException` at analysis time. |
| J4 | `sum`/`avg`/`min`/`max` give NULL when there are no rows or all values are NULL. `count` gives 0. Use nullable constructor parameters. |
| J5 | Row type of a derived table or CTE: **Kotlin data class or Java record only**. Normal classes are allowed only in a simple `select`. |
| J6 | A CTE made with `qb.with { }` names its columns with select aliases: `with t as (select o.customer.id as customerId, ...) select x.customerId ... from t x`. HQL does not accept a column list after the name: Hibernate rejects `with t (col list) as (...)` with a `SyntaxException`. Hibernate writes the column list in the SQL itself (`with t (customerId,cnt,total) as (...)` on MariaDB and Oracle); H2 turns the CTE into a derived table. The outer query always uses an alias for it (`from t x`). |
| J7 | `distinct()` renders `select distinct`. With sorting, the sort keys must be in the select list (a DB rule). lambdaQL checks this at analysis time. |
| J8 | Select works together with sorting ([4. Sorting and paging](04-order-paging.md)), paging and subqueries ([5. Subqueries](05-subquery.md)). |

---

## 6.10 Not in v0.1

| Item | Status |
|---|---|
| Sort by an aggregate result or a select alias, for example `orderBy { s, by -> by.desc(s.total) }` | v0.2 candidate |
| User aggregate functions and window functions | v0.2 candidate |
| Projection over joins, `select { o, c -> OrderView(o.id, c.name, o.price) }` | planned; the join syntax is not final yet |
| Result functions other than `toList()` | planned |

Errors from this chapter (wrong group key, `Agg` in `where`, a normal class as a derived table row) are `LambdaQLException` subclasses with a `[LQL-xxxx]` code. See [11. Errors and languages](../11-errors-and-languages.md).

---

[Kotlin track](README.md) | Previous: [5. Subqueries](05-subquery.md) | Next: [7. Update, insert, delete](07-dml.md) | [Java version of this page](../java/06-select-group.md)
