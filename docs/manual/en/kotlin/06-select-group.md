<a id="6-select-aggregates-and-group-by-kotlin"></a>
# 6. Select, aggregates and group by (Kotlin)

[Kotlin track](README.md) | [Manual home](../README.md) | Previous: [5. Subqueries](05-subquery.md) | Next: [7. Update, insert, delete](07-dml.md) | [Java version of this page](../java/06-select-group.md)

Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md)

---

This chapter shows how to choose **what** a query returns.

- One value per row (`select { it.price }`)
- A Kotlin data class, a `Pair` or a `Triple`
- Aggregates (`count`, `sum`, `avg`, ...) with `groupBy` and `having`
- `distinct`, paging and the result functions of a value query
- Explicit joins with other entities (`join`, `leftJoin`, `crossJoin`)
- A subquery in the FROM clause (derived table) and a CTE (`with`)

<a id="contents"></a>
## Contents

- [6.1 Example entities and result types](#61-example-entities-and-result-types)
- [6.2 Select one value](#62-select-one-value)
- [6.3 Select a data class, Pair or Triple](#63-select-a-data-class-pair-or-triple)
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

The examples use these entities (see [2. Getting started](02-getting-started.md#example-entities)).

| Entity | Properties used here |
|---|---|
| `Order` | `id`, `price`, `status`, `active`, `customer` (many-to-one) |
| `Customer` | `id`, `name`, `grade` |
| `Item` | `order`, `price`, `qty` |

They also use these result types.

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

The examples marked **(tested)** come from [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt). The test uses the Java test entity (`price` is a primitive `double`, `status` is a `short`, `Customer.id` is an `int`) and these result types.

```kotlin
/** A row of one order (Kotlin data class). */
data class OrderRow(val id: Long?, val customerName: String?, val price: Double)

/** A row of one customer group. Aggregates can be NULL, so the types are nullable (J4). */
data class CustomerStat(val customerId: Int?, val count: Long, val total: Double?)
```

More queries written in Java are tested in [`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java) (H2, MariaDB and Oracle). See [the Java version of this page](../java/06-select-group.md) for those examples.

---

<a id="62-select-one-value"></a>
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

An association path is a column, the same as in `where` (rule R24). In `select`, lambdaQL adds a `left join`, so orders without a customer are not lost (in `where`, the same path is an inner join by default).
A required association mapped with `optional = false` cannot lose rows, so it is an inner join in `select` too (see [4.5](04-order-paging.md#45-what-a-sort-key-can-be)).

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

<a id="63-select-a-data-class-pair-or-triple"></a>
## 6.3 Select a data class, Pair or Triple

Call a constructor inside the lambda. Each constructor argument is a value expression.

```kotlin
// data class
val list: List<OrderView> = qb.from(Order::class.java)
    .select { OrderView(it.id, it.customer?.name, it.price) }
    .toList()
// HQL: select e0.id, j1.name, e0.price from Order e0 left join e0.customer j1

// Pair (Triple works too)
val pairs: List<Pair<String?, Double?>> = qb.from(Order::class.java)
    .select { it.customer?.name to it.price }
    .toList()
// HQL: select j1.name, e0.price from Order e0 left join e0.customer j1
```

**(tested)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt), region `data-class`:

```kotlin
val q = qb.from(Order::class.java).select { OrderRow(it.id, it.customer?.name, it.price) }
assertEquals("select e0.id, j1.name, e0.price from Order e0 left join e0.customer j1", q.toHql().hql())
```

**(tested)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt), region `pair`:

```kotlin
val q = qb.from(Order::class.java).where { it.status.toInt() == 1 }.select { it.customer?.name to it.price }
assertEquals("select j1.name, e0.price from Order e0 left join e0.customer j1 where e0.status = 1", q.toHql().hql())
```

The test entity's `status` is a `short`, so the test compares `it.status.toInt() == 1`. lambdaQL ignores the conversion.

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

| Result type | Simple `select` | Row type of a derived table or CTE ([6.9](#69-derived-table-subquery-in-from)) |
|---|---|---|
| One value (`it.price`) | yes | – |
| Kotlin data class | yes | yes |
| Normal class with a constructor | yes | **no** (J5) |
| `Pair` / `Triple` | yes | no |
| Java record (from a Java module) | yes | yes |

---

<a id="64-aggregate-functions-agg"></a>
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

An import is optional. You can import one function.

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

<a id="aggregate-over-all-rows-no-group-by"></a>
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

<a id="null-in-aggregates-rule-j4"></a>
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

<a id="one-key"></a>
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

<a id="many-keys"></a>
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

.groupBy { it.customer?.id to it.status }                     // Pair: the same result
.groupBy { Triple(it.customer?.id, it.status, it.active) }    // three keys
```

**(tested)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt), region `group-many` (the result is a `Triple`):

```kotlin
val q = qb.from(Order::class.java)
    .where { it.customer != null }
    .groupBy { listOf(it.customer?.id, it.status) }
    .select { Triple(it.customer?.id, it.status, Agg.count()) }
assertEquals("select e0.customer.id, e0.status, count(*) from Order e0 where e0.customer is not null"
    + " group by e0.customer.id, e0.status", q.toHql().hql())
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

<a id="every-non-aggregate-value-must-be-a-key-rule-j3"></a>
### Every non-aggregate value must be a key (rule J3)

When a query has aggregates, each value in `select` and `having` that is **not** an aggregate must be a `groupBy` key. This is the SQL rule. lambdaQL checks it at analysis time.

```kotlin
// error: it.status is not a group key
qb.from(Order::class.java)
    .groupBy { it.customer?.id }
    .select { KeyCount(it.customer?.id, it.status, Agg.count()) }
// → TranslationException(LQL-3035)
```

The same check is tested in Java (region `rules`, see [6.11](#611-rules-j1-to-j8)).

---

## 6.6 Having

`having` is a condition on groups. It uses the same lambda rules as `where` ([3. Conditions (`where`)](03-where.md)), and it can use `Agg`.
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

**(tested)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt), region `group-by`:

```kotlin
val q = qb.from(Order::class.java)
    .groupBy { it.customer?.id }
    .having { Agg.sum(it.price) > 200 }
    .select { CustomerStat(it.customer?.id, Agg.count(), Agg.sum(it.price)) }
assertEquals("select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id"
    + " having sum(e0.price) > 200.0", q.toHql().hql())
```

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `group-by`:

```sql
-- H2, MariaDB, Oracle (same)
select o1_0.customer_id,count(*),sum(o1_0.price)
from orders o1_0
group by o1_0.customer_id
having sum(o1_0.price)>200.0
```

`Agg.sum` is a Java method, so its result is a platform type. `Agg.sum(it.price) > 200` therefore compiles without `!!`. If you add `!!`, lambdaQL ignores it (decision D23).

---

<a id="67-distinct-paging-and-results"></a>
## 6.7 Distinct, paging and results

`select { }` returns a `ValueQuery`. It has these functions.

| Function | What it does |
|---|---|
| `distinct()` | `select distinct` |
| `limit(n)`, `offset(n)` | paging |
| `toList()` | runs the query and returns the values in order |
| `toSet()` | runs the query with `select distinct` and returns a `SequencedSet` in order |
| `first()` | the first value (a Java `Optional`) |
| `one()` | the only value (a Java `Optional`). Two or more rows: `NonUniqueResultException` |
| `toHql()` | returns the HQL and the parameters without running the query |

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

- With `distinct()`, every sort key must be a selected value (rule J7). Otherwise lambdaQL throws `TranslationException` (`LQL-3046`) at analysis time.
- `toSet()` on a value query always uses `select distinct`. Repeated values are expected here, so there is no size check (unlike `toSet()` on entities, see [2. Getting started](02-getting-started.md#toset)).
- Oracle rejects a `@Lob` column used in `distinct` or as a `groupBy` key (ORA-22848, ORA-22849). H2 and MariaDB run it. lambdaQL does not change the query and logs `LQL-5010` at `DEBUG` level (see [4.5](04-order-paging.md#45-what-a-sort-key-can-be)).

**Paging.** `limit` and `offset` work as in [4. Sorting and paging](04-order-paging.md).
With paging, lambdaQL adds the `id` as the last sort key, so the pages do not overlap (rule S9). It does not add it when the sort key is a unique column that cannot be NULL (see [4.8](04-order-paging.md#tie-breaker-automatic-id)).
`first()` also reads only one row, so it adds the same tie-breaker. When several rows have the same sort key value, it always returns the value of the row with the smallest `id`.
It does not add the `id` to a grouped query or a `distinct` query, because the `id` is not in their select list.

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

<a id="68-explicit-joins"></a>
## 6.8 Explicit joins

An association path (`it.customer?.name`) joins a to-one association automatically (see [3.10](03-where.md#310-association-paths)).
To join another entity with your own condition, or with no condition, use `join`, `leftJoin` or `crossJoin` (decisions D44, D45).

| Function | HQL | Rows |
|---|---|---|
| `join(Customer::class.java) { o, c -> ... }` | `join Customer e1 on ...` | inner join: only rows with a match |
| `leftJoin(Customer::class.java) { o, c -> ... }` | `left join Customer e1 on ...` | rows of the main entity without a match stay |
| `crossJoin(Customer::class.java)` | `cross join Customer e1` | every pair of rows; write the conditions in `where` |

After a join, every lambda gets all entities in join order:
`where { o, c -> ... }`, `orderBy { o, c, by -> ... }` (`by` is last, rule S12) and `select { o, c -> ... }`.
The joined entities get the aliases `e1`, `e2`.

Joins are tested with **Java** lambdas in [`JoinQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java) (H2, MariaDB and Oracle). `distinct()` on a join result and the CTE join are also tested with Kotlin lambdas ([`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt), regions `join-distinct` and `join-cte`).
The other Kotlin code below shows the tested Java queries in Kotlin syntax, and the HQL is the result of the Java test. See [the Java version of this page](../java/06-select-group.md#68-explicit-joins) for the tested code.

```kotlin
val q = qb.from(Order::class.java)
    .join(Customer::class.java) { o, c -> o.customer?.id == c.id }
    .where { o, c -> c.grade != null && c.grade!! >= 5 }
// HQL: select e0 from Order e0 join Customer e1 on e0.customer.id = e1.id
//      where e1.grade is not null and e1.grade >= 5
```

**The result is the main entity.** Without `select`, `toList()` returns `List<Order>`.
The joined entity is used only for conditions, sorting and `select`.
To read values of the joined entity, use `select { o, c -> ... }` (see below).

<a id="left-join-and-null"></a>
### Left join and NULL

```kotlin
// an order without a customer has no c: find such rows with c == null
val q = qb.from(Order::class.java)
    .leftJoin(Customer::class.java) { o, c -> o.customer?.id == c.id }
    .where { o, c -> c == null || c.name == "Kim" }
// HQL: select e0 from Order e0 left join Customer e1 on e0.customer.id = e1.id
//      where e1.id is null or e1.name = 'Kim'
```

- `c == null` means "no match". lambdaQL writes it as `e1.id is null` (the `@Id` of the joined entity).
  The lambda parameter `c` has a non-null type, so the Kotlin compiler may warn that `c == null` is always false. lambdaQL reads this expression as "no match".
- After `leftJoin`, a condition on a column of the joined entity follows the **SQL meaning**. When there is no match, the column is NULL, the condition is not true, and the row is left out of the result.
  To keep the rows without a match, write `c == null || ...` as in the example.

<a id="sort-and-select"></a>
### Sort and select

```kotlin
val q = qb.from(Order::class.java)
    .leftJoin(Customer::class.java) { o, c -> o.customer?.id == c.id }
    .orderBy { o, c, by -> by.asc(c.name).asc(o.id) }
// HQL: select e0 from Order e0 left join Customer e1 on e0.customer.id = e1.id
//      order by e1.name asc nulls first, e0.id asc

data class Row(val orderId: Long?, val customerName: String?)

val rows = qb.from(Order::class.java)
    .leftJoin(Customer::class.java) { o, c -> o.customer?.id == c.id }
    .orderBy { o, c, by -> by.asc(o.id) }
    .select { o, c -> Row(o.id, c.name) }
// HQL: select e0.id, e1.name from Order e0 left join Customer e1 on e0.customer.id = e1.id
//      order by e0.id asc
```

For an order without a customer, `customerName` is `null`.
`select` returns a `ValueQuery`, so you can use `distinct()`, `limit`, `offset` and the result functions of [6.7](#67-distinct-paging-and-results).

<a id="three-entities-and-repeated-rows"></a>
### Three entities and repeated rows

You can join up to three entities. The third join lambda gets all three entities: `{ o, c, i -> ... }`.

```kotlin
// orders of customers that have an item with a quantity over 10. The order repeats once per matching item
val q = qb.from(Order::class.java)
    .join(Customer::class.java) { o, c -> o.customer?.id == c.id }
    .join(Item::class.java) { o, c, i -> i.order?.id == o.id }
    .where { o, c, i -> i.qty > 10 }
// HQL: select e0 from Order e0 join Customer e1 on e0.customer.id = e1.id
//      join Item e2 on e2.order.id = e0.id where e2.qty > 10
```

> [!WARNING]
> When several rows match the join condition, **the row of the main entity repeats**: once per match. `toList()` and `count()` count every repeated row (tested in Java).
> If you need each order only once, use an `exists()` subquery ([5. Subqueries](05-subquery.md)) instead of the join, or apply `distinct()` to the join query (see [`distinct()` on a join](#distinct-on-a-join)).

<a id="cross-join"></a>
### Cross join

`crossJoin` has no join condition. The function name shows that you want every pair of rows. Write the conditions in `where`.

```kotlin
// every pair of rows. Write the conditions in where
val q = qb.from(Order::class.java)
    .crossJoin(Customer::class.java)
    .where { o, c -> c.grade != null && o.price!! > 100 }
// HQL: select e0 from Order e0 cross join Customer e1 where e1.grade is not null and e0.price > 100.0
```

lambdaQL has no `from(Order::class.java, Customer::class.java)` (a comma join). If you forget the condition, you get every pair by mistake, and it is not clear which entity is the result (decision D45). Use `crossJoin` for such a query.

<a id="paging"></a>
### Paging

With paging, lambdaQL adds the id of **every** entity as the last sort keys. So the pages do not overlap (rule S9).

```kotlin
// with paging, the id of every entity is added to the sort, so the pages do not overlap (S9)
val pairs = qb.from(Order::class.java)
    .crossJoin(Customer::class.java)
    .orderBy { o, c, by -> by.desc(o.price) }
    .select { o, c -> Row(o.id, c.name) }
    .limit(5)
// HQL: select e0.id, e1.name from Order e0 cross join Customer e1 order by e0.price desc, e0.id asc, e1.id asc
```

This HQL is the result of the Java test, where `price` is a primitive `double`. With the nullable `price: Double?` of this manual, the sort key gets `nulls last` (see [4.4](04-order-paging.md#44-null-ordering-is-the-same-on-every-database)).

<a id="distinct-on-a-join"></a>
### `distinct()` on a join

`distinct()` on a join query returns each row of the main entity only once, even when the join repeats it (decision D51). The HQL is `select distinct e0`, and `count()` is `count(distinct e0)`.

- The sort keys must be columns of the main entity itself (J7, `LQL-3046`). Sorting by a column of a joined entity or by an association path is an error.
- With paging, lambdaQL adds only the id of the main entity as the tie-breaker. It does not add the ids of the joined entities.
- With `select(...)` after `distinct()`, the values are read with `select distinct`.
- Consider an `exists` subquery ([5. Subqueries](05-subquery.md)) first. It gives the same rows without repeats, and it is often faster.

**(tested)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt), region `join-distinct`:

```kotlin
// an order with two matching items would come twice; distinct() returns it once
val q = qb.from(Order::class.java)
    .join(Item::class.java) { o, i -> i.order?.id == o.id }
    .where { _, i -> i.qty > 0 }
    .distinct()
assertEquals("select distinct e0 from Order e0 join Item e1 on e1.order.id = e0.id where e1.qty > 0", q.toHql().hql())
```

<a id="join-a-derived-table-or-cte"></a>
### Join a derived table or CTE

You can join the derived table of [6.9](#69-derived-table-subquery-in-from) and the CTE of [6.10](#610-cte-with) like an entity (decision D51).
The forms are `join(cte) { c, x -> ... }`, `leftJoin(cte) { ... }`, `join(qb.from(...)...select { Row(...) }) { ... }` and `leftJoin(...) { ... }`. You can use them as the second or the third root.

- The HQL is `with t as (...) ... join t e1 on ...` or `join (select ...) e1 on ...`.
- The row type is a Kotlin data class (or a Java record) (J5). A property (`x.total`) reads a column.
- When one statement reads the same `Cte` object twice, the definition is written only once, and both joins read the same name: `join t e1 on ... left join t e2 on ...`. Different `Cte` objects get separate names, as in `with t as (...), t2 as (...)` (measured on the three databases).
- A derived row has no id, so `x == null` is an error (`LQL-3067`). Compare a column instead, as in `x.customerId == null`.
- Hibernate 6.6 accepts `select distinct` of an entity, inner and left joins of a derived table or CTE, and entity joins inside a subquery (measured on the three databases).

**(tested)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt), region `join-cte`:

```kotlin
// the total of each customer, joined to the customer row
val t = qb.with(qb.from(Order::class.java)
    .where { it.customer != null }
    .groupBy { it.customer?.id }
    .select { CustTotal(it.customer?.id, Agg.sum(it.price)) })
val rows = qb.from(Customer::class.java)
    .join(t) { c, x -> x.customerId == c.id }
    .where { _, x -> x.total!! > 100 }
    .select { c, x -> c.name to x.total }
assertEquals("with t as (select d0.customer.id as customerId, sum(d0.price) as total from Order d0"
        + " where d0.customer is not null group by d0.customer.id) select e0.name, e1.total from Customer e0"
        + " join t e1 on e1.customerId is not null and e1.customerId = e0.id where e1.total > 100.0", rows.toHql().hql())
```

When `x.customerId` is a nullable `Int?`, `x.customerId == c.id` becomes `e1.customerId is not null and e1.customerId = e0.id` (see the region above). The join of an unnamed derived table is checked by a Java test ([`JoinQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java), region `join-derived`).

Reading the same CTE twice is checked by a Java test ([`JoinQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java), region `cte-reuse`).

<a id="what-a-join-query-has"></a>
### What a join query has

| After | Functions |
|---|---|
| `join`, `leftJoin`, `crossJoin` | `where`, `join` / `leftJoin` / `crossJoin` (up to three entities, including derived tables and CTEs), `distinct`, `orderBy`, `select`, `limit`, `toList`, `toSet`, `first`, `count`, `exists`, `toHql` |
| `orderBy { o, c, by -> ... }` | `select`, `limit`, `offset`, `toList`, `first`, and the other functions of a sorted query ([4. Sorting and paging](04-order-paging.md)) |

For joins inside a subquery, see [5. Subqueries](05-subquery.md#joins-inside-a-subquery) (D51).
Not in v0.1 yet: joins in insert, more than three entities, and the join form of `SortSpec`.
Joins in update and delete will not be added (D51). Use an association path (U9) or `sub` (D49).

---

<a id="69-derived-table-subquery-in-from"></a>
## 6.9 Derived table (subquery in FROM)

Make a value query with `select` (a data class, see [6.3](#63-select-a-data-class-pair-or-triple)). Then pass it to `qb.from(...)`.
Its rows become the table of the outer query (decision D46).
The result is a `DerivedQuery<R>`. `R` is the row type.

Derived tables are tested with **Java** lambdas in [`DerivedQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DerivedQueryTest.java) (H2, MariaDB and Oracle). There are no Kotlin tests yet.
The Kotlin code below shows the tested Java queries in Kotlin syntax. See [the Java version of this page](../java/06-select-group.md#69-derived-table-subquery-in-from) for the tested code.

```kotlin
data class CustomerTotal(val customerId: Long?, val cnt: Long, val total: Double?)

// the rows of a grouped query become the table of the outer query. r.total reads the column "total"
val q = qb.from(qb.from(Order::class.java)
        .where { it.customer != null }
        .groupBy { it.customer?.id }
        .select { CustomerTotal(it.customer?.id, Agg.count(), Agg.sum(it.price)) })
    .where { r -> r.total!! > 100 }
    .orderBy { r, by -> by.desc(r.total) }
// HQL: select e0.customerId, e0.cnt, e0.total from (select d0.customer.id as customerId, count(*) as cnt,
//      sum(d0.price) as total from Order d0 where d0.customer is not null group by d0.customer.id) e0
//      where e0.total > 100.0 order by e0.total desc nulls last
```

- The inner query uses the alias `d0` (its joins `dj1`, ...). The outer query uses `e0`.
- Each data class property names a column, in the order of the primary constructor: `as customerId`, `as cnt`, `as total` (rule J5).
- In the outer lambdas, read a column with the property: `r.total` is `e0.total`.
- The outer query has `where`, `orderBy`, `limit`, `offset`, `select`, `toList`, `first`, `count` and `toHql`.

You can also select one value of the row (`.select { r -> r.customerId }`). Captured values inside and outside are bound in order.
`limit` and `offset` of the inner query stay in the inner query (HQL `limit` and `offset`). So you can sort the top rows again.

```kotlin
data class Priced(val id: Long?, val price: Double?)

// the cheapest of the three most expensive orders
val cheapestOfTop = qb.from(qb.from(Order::class.java)
        .orderBy { o, by -> by.desc(o.price) }
        .limit(3)
        .select { Priced(it.id, it.price) })
    .orderBy { r, by -> by.asc(r.price) }
    .first()
```

**About the SQL block.** [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java) gives a simpler HQL of the same form to Hibernate directly and records the SQL on H2 2.3, MariaDB 10.11 and Oracle 23.26 (Hibernate 6.6.5). The test fails if Hibernate's SQL changes. This block shows how each database writes a derived table.

**SQL by database (Hibernate's SQL for this HQL form)** — from `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql), example `06-derived`:

```sql
-- HQL: select r.customerId, r.cnt, r.total from (select o.customer.id as customerId, count(*) as cnt, sum(o.price) as total from Order o group by o.customer.id) r where r.total > 100

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
  lambdaQL takes the column names from the data class properties, in the order of the primary constructor.
- A normal class, a `Pair`, a `Triple` or a single value cannot be used here, because lambdaQL cannot name its columns. In this case lambdaQL throws `TranslationException` (`LQL-3064`) (tested in Java with a single value).

To join a derived table to an entity query, see [Join a derived table or CTE](#join-a-derived-table-or-cte) (D51).

Not in v0.1 yet: a join of two derived tables, and a derived table in update, delete or insert.

Hibernate also adds the `@SQLRestriction` and `@SoftDelete` conditions of an entity to the `on` of an entity join and inside a derived table (measured on MariaDB 10.11 and Oracle 23.26). lambdaQL does not handle these conditions separately.

---

<a id="610-cte-with"></a>
## 6.10 CTE (`with`)

Use `qb.with(valueQuery)` to make a handle of type `Cte<R>`. Then read it with `qb.from(handle)`.
The inner query is the same as for a derived table (6.9).

```kotlin
val perCustomer = qb.from(Order::class.java)
    .where { it.customer != null }
    .groupBy { it.customer?.id }
    .select { CustomerTotal(it.customer?.id, Agg.count(), Agg.sum(it.price)) }

val t: Cte<CustomerTotal> = qb.with(perCustomer)
val rows = qb.from(t).where { r -> r.cnt >= 2 }.toList()
// HQL: with t as (select d0.customer.id as customerId, count(*) as cnt, sum(d0.price) as total
//      from Order d0 where d0.customer is not null group by d0.customer.id)
//      select e0.customerId, e0.cnt, e0.total from t e0 where e0.cnt >= 2L
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
- The CTE columns are named with select aliases (`as customerId`). HQL does not accept a column list after the CTE name; Hibernate adds the column list in the SQL (rule J6).
- MariaDB and Oracle run a real `with`. On H2, Hibernate expands it into a derived table. The result is the same (tested on the three databases with Java lambdas).
- The row type rule is the same as for a derived table: data class or record (J5).
- To join a CTE to an entity query, see [Join a derived table or CTE](#join-a-derived-table-or-cte) (D51).
- You can read the same CTE twice in one query. The definition is written only once (see [Join a derived table or CTE](#join-a-derived-table-or-cte)).
- Not in v0.1 yet: a CTE in update, delete or insert.

---

<a id="611-rules-j1-to-j8"></a>
## 6.11 Rules J1 to J8

| # | Rule | Status |
|---|---|---|
| J1 | A `select` lambda returns **one value expression** or **one constructor call**. Each constructor argument is a value expression (rules in [3. Conditions (`where`)](03-where.md)). Association paths that are used only in `select` become `left join` aliases, so no rows are lost. A required association (`optional = false`) is an inner join (A4). If `where` uses the same path, `select` uses the `where` join (R24). | Implemented (tested with Java and Kotlin lambdas) |
| J2 | For a constructor, lambdaQL selects only the columns and calls the constructor itself. It does not write `select new`. `Pair`/`Triple` are built the same way. | Implemented (tested with Java and Kotlin lambdas) |
| J3 | With aggregates, every non-aggregate value in `select` and `having` (and in the values of `insert ... select`) must be a `groupBy` key. Otherwise there is an error at analysis time. | Implemented (tested with Java lambdas). The error is `TranslationException` (`LQL-3035`, see [11](../11-errors-and-languages.md)) |
| J4 | `sum`/`avg`/`min`/`max` give NULL when there are no rows or all values are NULL. `count` gives 0. Use nullable constructor parameters. A NULL for a non-null parameter throws `IllegalStateException`. | Implemented (tested with Java lambdas) |
| J5 | Row type of a derived table or CTE: **Kotlin data class or Java record only**. Normal classes are allowed only in a simple `select`. Otherwise: `LQL-3064`. This also applies to the rows of a joined derived table or CTE (D51). A derived row cannot be compared with `null` (`LQL-3067`). | Implemented (tested with Java lambdas) |
| J6 | A CTE made with `qb.with(...)` names its columns with select aliases: `with t as (select d0.customer.id as customerId, ...) select e0.customerId ... from t e0`. HQL does not accept a column list after the name: Hibernate rejects `with t (col list) as (...)` with a `SyntaxException`. Hibernate writes the column list in the SQL itself (`with t (customerId,cnt,total) as (...)` on MariaDB and Oracle). H2 turns the CTE into a derived table. The outer query always uses an alias (`from t e0`). | Implemented (tested with Java lambdas) |
| J7 | `distinct()` renders `select distinct`. With sorting, the sort keys must be in the select list (a DB rule). lambdaQL checks this at analysis time. For `distinct()` on a join, the sort keys must be columns of the main entity itself (D51). | Implemented (tested with Java lambdas) |
| J8 | Select works together with sorting ([4. Sorting and paging](04-order-paging.md)), paging and subqueries ([5. Subqueries](05-subquery.md)). Sorting by an aggregate (`by.desc(s.total)`) is a v0.2 candidate. | Implemented, except the sort by an aggregate |

The Java test region `rules` in [`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java) checks J3, `Agg` in `where`, J7 and J4. See [the Java version of this page](../java/06-select-group.md#611-rules-j1-to-j8).

---

<a id="612-not-in-v01"></a>
## 6.12 Not in v0.1

| Item | Status |
|---|---|
| Sort by an aggregate result or a select alias (for example `orderBy { s, by -> by.desc(s.total) }`), and `orderBy` on a grouped query | v0.2 candidate |
| User aggregate functions and window functions | v0.2 candidate |
| Joins in insert, more than three entities, three or more entities inside a subquery | later |
| Joins in update and delete | will not be added (D51). Use an association path (U9) or `sub` (D49) |
| Derived tables and CTEs in DML | later |

The errors from this chapter are `TranslationException` with a code: `LQL-3035` for a wrong group key (J3), `LQL-3005` for `Agg` in `where`, `LQL-3046` for a sort key that is not selected with `distinct` (J7), and `LQL-3064` for the row type of a derived table or CTE (J5). See [11. Errors and languages](../11-errors-and-languages.md).

---

[Kotlin track](README.md) | Previous: [5. Subqueries](05-subquery.md) | Next: [7. Update, insert, delete](07-dml.md) | [Java version of this page](../java/06-select-group.md)
