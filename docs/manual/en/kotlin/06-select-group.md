<a id="6-select-aggregates-and-group-by-kotlin"></a>
# 6. Select, aggregates and group by (Kotlin)

[Kotlin track](README.md) | [Manual home](../README.md) | Previous: [5. Subqueries](05-subquery.md) | Next: [7. Update, insert, delete](07-dml.md) | [Java version of this page](../java/06-select-group.md)

Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md)

---

This chapter explains how to choose **what** a query returns.

- One value per row (`select { it.price }`)
- Kotlin data classes, `Pair`, `Triple`
- Aggregates (`count`, `sum`, `avg`, ...) with `groupBy` and `having`
- `distinct`, paging and result functions of value queries
- Explicit joins with other entities (`join`, `leftJoin`, `crossJoin`)
- Subqueries in the FROM clause (derived tables) and CTE (`with`)

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

The examples use the example entities written in Java (`price` is a primitive `double`, `status` is a `short`, `Customer.id` is an `int`) and these result types:

<!-- region: ManualKotlinSelectTest.kt#result-types -->
```kotlin
data class OrderView(val id: Long?, val customerName: String?, val price: Double?)
data class Stat(val customerId: Int?, val count: Long, val total: Double?)
data class KeyCount(val customerId: Int?, val status: Short?, val count: Long)
data class Total(val count: Long, val average: Double?)
```

Kotlin checks types strictly, so the constructor parameter types match the types of the entity properties (`Customer.id` is `Int?`, `status` is `Short?`).

> [!TIP]
> Use nullable types (`Double?`, `String?`) in results.
> A column or an aggregate such as `sum` can be NULL. A non-null `Double` or `Long` parameter is a JVM primitive, and a non-null `String` parameter does not accept NULL either.
> If NULL goes into such a parameter, lambdaQL throws `IllegalStateException`. For a primitive, the message tells you to use the wrapper type for that argument.
> `count` never gives NULL, so `count: Long` is fine.

Example code: [`ManualKotlinSelectTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSelectTest.kt) (`result-types`).

---

<a id="62-select-one-value"></a>
## 6.2 Select one value

`select` takes one lambda. The lambda returns one value. The result is a `ValueQuery` of that value.
You can call `select` after `where` and after `orderBy`. The sort, `limit` and `offset` are kept.

<!-- region: ManualKotlinSelectTest.kt#value -->
```kotlin
val q = qb.from(Order::class.java)
    .where { it.status.toInt() == 1 }
    .orderBy { o -> asc(o.price) }
    .select { it.price }
assertEquals("select e0.price from Order e0 where e0.status = 1 order by e0.price asc", q.toHql().hql())
val prices: List<Double> = q.toList()
```

`price` can never be NULL, so the sort has no `nulls` part. A column that can be NULL gets `nulls first` (see [4.4](04-order-paging.md#44-null-ordering-is-the-same-on-every-database)).

**SQL by database**

<!-- sql: value -->
```sql
-- H2, MariaDB, Oracle (same)
select o1_0.price
from orders o1_0
where o1_0.status=1
order by o1_0.price
```

An association path is a column, the same as in `where`. In `select`, lambdaQL adds a `left join`, so orders without a customer are not dropped (in `where`, the same path is an inner join if an order without a customer cannot match the condition).
A required association mapped with `optional = false` loses no rows, so it is an inner join in `select` too (see [4.5](04-order-paging.md#45-what-a-sort-key-can-be)).

<!-- region: ManualKotlinSelectTest.kt#value-path -->
```kotlin
// a path in select is a left join: the order without a customer stays (its value is null)
val names = qb.from(Order::class.java).select { it.customer?.name }
assertEquals("select j1.name from Order e0 left join e0.customer j1", names.toHql().hql())
// !! gives the same query
assertEquals(names.toHql().hql(), qb.from(Order::class.java).select { it.customer!!.name }.toHql().hql())
// a path that ends with the @Id reads the foreign key column: no join
assertEquals("select e0.customer.id from Order e0", qb.from(Order::class.java).select { it.customer?.id }.toHql().hql())
```

`it.customer!!.name` makes the same HQL. You can also see this `left join` in the data class example in [6.3](#63-select-a-data-class-pair-or-triple).
If the path ends with the `@Id` of the associated entity, there is no join. `it.customer?.id` reads the foreign key column `customer_id`.

**Elvis operator.** `?:` on a non-boolean column becomes `coalesce`. `?:` after a `?.` path (`it.customer?.name ?: "none"`) is read as a condition and rejected with `LQL-2022`.

<!-- region: ManualKotlinSelectTest.kt#value-elvis -->
```kotlin
// ?: on a column that is not a boolean: coalesce
val q = qb.from(Order::class.java).select { it.description ?: "" }
assertEquals("select coalesce(e0.description, '') from Order e0", q.toHql().hql())
// ?: after a ?. path is rejected (LQL-2022)
val afterPath = assertThrows(LambdaQLException::class.java) {
    qb.from(Order::class.java).select { it.customer?.name ?: "none" }.toHql()
}
assertEquals("LQL-2022", afterPath.code())
```

Example code: [`ManualKotlinSelectTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSelectTest.kt) (`value`, `value-path`, `value-elvis`).

---

<a id="63-select-a-data-class-pair-or-triple"></a>
## 6.3 Select a data class, Pair or Triple

Call a constructor inside the lambda. Each argument of the constructor is a value expression.

<!-- region: ManualKotlinSelectTest.kt#data-class -->
```kotlin
// only the columns are selected; lambdaQL calls the constructor
val views = qb.from(Order::class.java).select { OrderView(it.id, it.customer?.name, it.price) }
assertEquals("select e0.id, j1.name, e0.price from Order e0 left join e0.customer j1", views.toHql().hql())
// Pair (a to b) and Triple
val pairs = qb.from(Order::class.java).select { it.customer?.name to it.price }
assertEquals("select j1.name, e0.price from Order e0 left join e0.customer j1", pairs.toHql().hql())
val triples = qb.from(Order::class.java).select { Triple(it.id, it.status, it.isActive) }
assertEquals("select e0.id, e0.status, e0.active from Order e0", triples.toHql().hql())
```

**SQL by database**

<!-- sql: record -->
```sql
-- H2, MariaDB, Oracle (same)
select o1_0.id,c1_0.name,o1_0.price
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
```

lambdaQL does **not** write `select new com.acme.OrderView(...)`.
It selects the columns, and lambdaQL calls the constructor itself. This is the same for data classes, `Pair` and `Triple`.
So it avoids problems with long class names and nested class names. `OrderView` in the example above is also a data class nested in another class.

What you can return:

| Result type | Simple `select` | Row type of a derived table or CTE ([6.9](#69-derived-table-subquery-in-from)) |
|---|---|---|
| One value (`it.price`) | Yes | – |
| Kotlin data class | Yes | Yes |
| Normal class with a constructor | Yes | **No** |
| `Pair` / `Triple` | Yes | No |
| Java record (in a Java module) | Yes | Yes |

Example code: [`ManualKotlinSelectTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSelectTest.kt) (`data-class`).

---

<a id="64-aggregate-functions-agg"></a>
## 6.4 Aggregate functions: `Agg`

Aggregate functions are **static methods of `org.lambdaql.Agg`**.
They are standard SQL functions, so lambdaQL provides them. (For your own DB functions, see [8. Procedures and DB functions](08-procedures-functions.md).)

| Function | SQL |
|---|---|
| `Agg.count()` | `count(*)` |
| `Agg.count(x)` | `count(x)` |
| `Agg.countDistinct(x)` | `count(distinct x)` |
| `Agg.sum(x)` | `sum(x)` |
| `Agg.avg(x)` | `avg(x)` |
| `Agg.min(x)` | `min(x)` |
| `Agg.max(x)` | `max(x)` |

Types seen in Kotlin:

| Function | Returns |
|---|---|
| `count()`, `count(x)`, `countDistinct(x)` | `Long` |
| `sum(x)` | The type of `x` (platform type) |
| `avg(x)` | `Double` (it can be NULL in SQL, so treat it as `Double?`) |
| `min(x)`, `max(x)` | The type of `x` (any `Comparable`, nullable is fine) |

`Agg.sum` does not clash with Kotlin standard library extensions such as `xs.sum()`. You can use both `sum`s in one file.

Important points:

- The return value is only a **placeholder**. lambdaQL does not call the lambda. It reads the bytecode opcodes, so the value is never used.
- If you call `Agg.sum(...)` **outside** a lambdaQL lambda, it throws `IllegalStateException`.
- You can use `Agg` in `select`, `having` and the values of `insert ... select` ([7.5](07-5-insert.md)).
- You cannot use `Agg` in `where`. For a condition on an aggregate, use `having`. `Agg` in `where` gives an error at analysis time.

<a id="aggregate-over-all-rows-no-group-by"></a>
### Aggregate over all rows (no group by)

<!-- region: ManualKotlinSelectTest.kt#total -->
```kotlin
// aggregates without groupBy: always one row
val q = qb.from(Order::class.java)
    .where { it.status.toInt() == 1 }
    .select { Total(Agg.count(), Agg.avg(it.price)) }
assertEquals("select count(*), avg(e0.price) from Order e0 where e0.status = 1", q.toHql().hql())
val t: Total? = q.one().orElse(null)
```

**SQL by database**

<!-- sql: total -->
```sql
-- H2, MariaDB, Oracle (same)
select count(*),avg(o1_0.price)
from orders o1_0
where o1_0.status=1
```

This query always gives one row.

<a id="null-in-aggregates-rule-j4"></a>
### NULL in aggregates (rule J4)

| Function | When there are no rows or all values are NULL |
|---|---|
| `sum`, `avg`, `min`, `max` | `NULL` |
| `count` | `0` |

This is normal SQL behavior. Use nullable parameters (`Double?`) for such results (see the tip in [6.1](#61-example-entities-and-result-types)).

Example code: [`ManualKotlinSelectTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSelectTest.kt) (`total`).

---

<a id="65-group-by"></a>
## 6.5 Group by

You write the group keys with `groupBy`. The group keys are not chosen automatically from the values of `select`.
`groupBy` returns a group query. Call `having { }` (optional) and then `select { }`.

<a id="one-key"></a>
### One key

<!-- region: ManualKotlinSelectTest.kt#group-one -->
```kotlin
// the key ends with the @Id: no join. The orders without a customer are one group with the key NULL
val q = qb.from(Order::class.java)
    .groupBy { it.customer?.id }
    .select { Stat(it.customer?.id, Agg.count(), Agg.sum(it.price)) }
assertEquals("select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id", q.toHql().hql())
```

The key ends with `@Id`, so there is no join. Orders without a customer become one group whose key is NULL.
An example with `having` is in [6.6](#66-having).

<a id="many-keys"></a>
### Many keys

Return a list of keys with `listOf(...)`, or use a `Pair` (`a to b`) or a `Triple`.

<!-- region: ManualKotlinSelectTest.kt#group-many -->
```kotlin
val q = qb.from(Order::class.java)
    .where { it.customer != null }
    .groupBy { listOf(it.customer?.id, it.status) }
    .select { KeyCount(it.customer?.id, it.status, Agg.count()) }
assertEquals("select e0.customer.id, e0.status, count(*) from Order e0 where e0.customer is not null"
    + " group by e0.customer.id, e0.status", q.toHql().hql())
// a Pair (a to b) gives the same keys; a Triple gives three
val pair = qb.from(Order::class.java)
    .where { it.customer != null }
    .groupBy { it.customer?.id to it.status }
    .select { KeyCount(it.customer?.id, it.status, Agg.count()) }
assertEquals(q.toHql().hql(), pair.toHql().hql())
val three = qb.from(Order::class.java)
    .groupBy { Triple(it.customer?.id, it.status, it.isActive) }
    .select { Triple(it.customer?.id, it.status, Agg.count()) }
assertEquals("select e0.customer.id, e0.status, count(*) from Order e0 group by e0.customer.id, e0.status, e0.active",
    three.toHql().hql())
```

**SQL by database (first query)**

<!-- sql: group-many -->
```sql
-- H2, MariaDB, Oracle (same)
select o1_0.customer_id,o1_0.status,count(*)
from orders o1_0
where o1_0.customer_id is not null
group by o1_0.customer_id,o1_0.status
```

The keys are used in the order you write them.
`listOf(...)` in `groupBy` is the **only** place where a list built inside a lambda is allowed.

Not allowed as a key list:

| Form | Why |
|---|---|
| `setOf(a, b)` | A set removes duplicates and has no order |

<a id="every-non-aggregate-value-must-be-a-key-rule-j3"></a>
### Every non-aggregate value must be a key (rule J3)

If a query has aggregates, every value in `select` and `having` that is **not** an aggregate must be a `groupBy` key. This is an SQL rule. lambdaQL checks it at analysis time.

<!-- region: ManualKotlinSelectTest.kt#group-key-error -->
```kotlin
// status is not a group key
val e = assertThrows(LambdaQLException::class.java) {
    qb.from(Order::class.java)
        .groupBy { it.customer?.id }
        .select { KeyCount(it.customer?.id, it.status, Agg.count()) }
}
assertEquals("LQL-3035", e.code())
```

The error happens when you call `select`. For the other rule checks, see [6.11](#611-rules-j1-to-j8).

Example code: [`ManualKotlinSelectTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSelectTest.kt) (`group-one`, `group-many`, `group-key-error`).

---

<a id="66-having"></a>
## 6.6 Having

`having` is a condition on groups. It uses the same lambda rules as `where` ([3. Conditions (`where`)](03-where.md)), and you can use `Agg`.
You can call `having` more than once. The conditions are joined with `AND`.

<!-- region: ManualKotlinSelectTest.kt#having -->
```kotlin
// sum is imported (import org.lambdaql.Agg.sum); having can be called more than once (AND)
val q = qb.from(Order::class.java)
    .groupBy { it.customer?.id }
    .having { sum(it.price) > 200 }
    .having { Agg.count() >= 2 }
    .select { Stat(it.customer?.id, Agg.count(), sum(it.price)) }
assertEquals("select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id"
    + " having sum(e0.price) > 200.0 and count(*) >= 2L", q.toHql().hql())
```

`Agg.sum` is a Java method, so its result is a platform type. So `sum(it.price) > 200` compiles without `!!`. If you add `!!`, lambdaQL ignores it.

Example code: [`ManualKotlinSelectTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSelectTest.kt) (`having`).

---

<a id="67-distinct-paging-and-results"></a>
## 6.7 Distinct, paging and results

`select { }` returns a `ValueQuery`. A `ValueQuery` has these functions:

| Function | What it does |
|---|---|
| `distinct()` | `select distinct` |
| `limit(n)`, `offset(n)` | Paging |
| `toList()` | Runs the query and returns the values in order |
| `toSet()` | Runs the query with `select distinct` and returns an ordered `SequencedSet` |
| `first()` | The first value (Java `Optional`) |
| `one()` | The only value (Java `Optional`). `NonUniqueResultException` if there are two or more rows |
| `toHql()` | Returns the HQL and parameters without running the query |

<!-- region: ManualKotlinSelectTest.kt#distinct -->
```kotlin
val q = qb.from(Order::class.java)
    .orderBy { o -> desc(o.price) }
    .select { it.price }
    .distinct()
assertEquals("select distinct e0.price from Order e0 order by e0.price desc", q.toHql().hql())
// toSet() always uses select distinct
val prices = q.toSet()
```

**SQL by database**

<!-- sql: distinct -->
```sql
-- H2, MariaDB, Oracle (same)
select distinct o1_0.price
from orders o1_0
order by o1_0.price desc
```

- With `distinct()`, every sort key must be a select value. If not, lambdaQL throws `TranslationException` (`LQL-3046`) at analysis time (see [6.11](#611-rules-j1-to-j8)).
- `toSet()` on a value query always uses `select distinct`. Here repeated values are normal, so there is no size check (unlike `toSet()` on entities, see [2. Getting started](02-getting-started.md#toset)).
- If you use a `@Lob` column in `distinct` or as a `groupBy` key, Oracle rejects it (ORA-22848, ORA-22849). H2 and MariaDB run it. lambdaQL does not change the query and logs `LQL-5010` at `DEBUG` level (see [4.5](04-order-paging.md#45-what-a-sort-key-can-be)).

**Paging.** `limit` and `offset` work as in [4. Sorting and paging](04-order-paging.md).
With paging, lambdaQL adds `id` as the last sort key, so pages do not overlap. If the sort key is a unique column that can never be NULL, it is not added (see [4.8](04-order-paging.md#tie-breaker-automatic-id)).
`first()` also reads only one row, so it adds the same extra sort key. If many rows have the same sort key value, it always returns the value of the row with the smallest `id`.
It is not added to group queries and `distinct` queries, because the select list of these queries has no `id`.

<!-- region: ManualKotlinSelectTest.kt#value-page -->
```kotlin
// paging adds the id as the last key, so the pages do not overlap
val page = qb.from(Order::class.java)
    .orderBy { o -> desc(o.price) }
    .select { it.price }
    .limit(2).offset(1)
assertEquals("select e0.price from Order e0 order by e0.price desc, e0.id asc", page.toHql().hql())
```

**SQL by database**

<!-- sql: value-page -->
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

Example code: [`ManualKotlinSelectTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSelectTest.kt) (`distinct`, `value-page`).

---

<a id="68-explicit-joins"></a>
## 6.8 Explicit joins

An association path (`it.customer?.name`) joins a to-one association automatically (see [3.10](03-where.md#310-association-paths)).
To join another entity with a condition that you choose, or with no condition, use `join`, `leftJoin` or `crossJoin`.

| Function | HQL | Rows |
|---|---|---|
| `join(Customer::class.java) { o, c -> ... }` | `join Customer e1 on ...` | Inner join: only rows with a match |
| `leftJoin(Customer::class.java) { o, c -> ... }` | `left join Customer e1 on ...` | Rows of the main entity without a match also stay |
| `crossJoin(Customer::class.java)` | `cross join Customer e1` | Every combination of rows. Write the condition in `where` |

Every lambda after a join takes all entities, in join order:
`where { o, c -> ... }`, `orderBy { o, c -> asc(c.name) }`, `select { o, c -> ... }`.
The aliases of joined entities are `e1` and `e2`.

<!-- region: ManualKotlinSelectTest.kt#join -->
```kotlin
val q = qb.from(Order::class.java)
    .join(Customer::class.java) { o, c -> o.customer?.id == c.id }
    .where { _, c -> c.grade != null && c.grade >= 5 }
assertEquals("select e0 from Order e0 join Customer e1 on e0.customer is not null and e0.customer.id = e1.id"
    + " where e1.grade is not null and e1.grade >= 5", q.toHql().hql())
// without select, the result is the main entity
val result: List<Order> = q.toList()
```

Kotlin's `o.customer?.id == c.id` compares values that can be NULL, so lambdaQL adds `e0.customer is not null and`. The result rows are the same.

**The result is the main entity.** Without `select`, `toList()` returns a `List<Order>`.
A joined entity is used only in conditions, sorts and `select`.
To read values of a joined entity, use `select { o, c -> ... }` (see below).

<a id="left-join-and-null"></a>
### Left join and NULL

<!-- region: ManualKotlinSelectTest.kt#left-join-null -->
```kotlin
// c is missing for an order without a customer: c == null finds those rows
val q = qb.from(Order::class.java)
    .leftJoin(Customer::class.java) { o, c -> o.customer?.id == c.id }
    .where { _, c -> c == null || c.name == "Kim" }
assertEquals("select e0 from Order e0 left join Customer e1 on e0.customer is not null and e0.customer.id = e1.id"
    + " where e1.id is null or e1.name = 'Kim'", q.toHql().hql())
```

- `c == null` means "no match". lambdaQL writes it as `e1.id is null` (the `@Id` of the joined entity).
  The lambda parameter `c` has a non-null type, so the Kotlin compiler can warn that `c == null` is always false. lambdaQL reads this expression as "no match".
- A condition on a column of the joined entity after `leftJoin` follows the **SQL meaning**. Without a match, the column is NULL, so the condition is not true, and the row is left out of the result.
  To keep rows without a match, write `c == null || ...`, as in the example.

<a id="sort-and-select"></a>
### Sort and select

`Row` in the example is this data class:

<!-- region: ManualKotlinSelectTest.kt#row-type -->
```kotlin
data class Row(val orderId: Long?, val customerName: String?)
```

<!-- region: ManualKotlinSelectTest.kt#join-sort-select -->
```kotlin
val sorted = qb.from(Order::class.java)
    .leftJoin(Customer::class.java) { o, c -> o.customer?.id == c.id }
    .orderBy { o, c -> asc(c.name).asc(o.id) }
assertEquals("select e0 from Order e0 left join Customer e1 on e0.customer is not null and e0.customer.id = e1.id"
    + " order by e1.name asc nulls first, e0.id asc", sorted.toHql().hql())
// the order without a customer has customerName = null
val rows = qb.from(Order::class.java)
    .leftJoin(Customer::class.java) { o, c -> o.customer?.id == c.id }
    .orderBy { o, _ -> asc(o.id) }
    .select { o, c -> Row(o.id, c.name) }
assertEquals("select e0.id, e1.name from Order e0 left join Customer e1 on e0.customer is not null and e0.customer.id = e1.id"
    + " order by e0.id asc", rows.toHql().hql())
```

For an order without a customer, `customerName` is `null`.
`select` returns a `ValueQuery`, so you can use `distinct()`, `limit`, `offset` and the result functions in [6.7](#67-distinct-paging-and-results).

<a id="select-entities"></a>
### Select entities

You can also use an entity itself in `select`. You can pick a joined entity, or put many entities into one data class or `Pair`.

<!-- region: KotlinSelectEntityTest.kt#select-joined -->
```kotlin
// the customers that have an order, through an explicit join; Hibernate returns each entity once
val q = qb.from(Order::class.java)
    .join(Customer::class.java) { o, c -> o.customer?.id == c.id }
    .select { _, c -> c }
```

If you pick one entity, Hibernate returns the same entity only once. The HQL is `select e1 from Order e0 join Customer e1 on e0.customer is not null and e0.customer.id = e1.id`.

<!-- region: KotlinSelectEntityTest.kt#select-entities -->
```kotlin
// an order and its customer together; with leftJoin an order without a customer has null
val q = qb.from(Order::class.java)
    .leftJoin(Customer::class.java) { o, c -> o.customer?.id == c.id }
    .select { o, c -> OrderWithCustomer(o, c) }
```

`OrderWithCustomer` is `data class OrderWithCustomer(val order: Order, val customer: Customer?)`. The HQL is `select e0, e1 from Order e0 left join Customer e1 on ...`, with one row per order. You can also take them as a `Pair`, as in `select { o, c -> o to c }`.

A row that holds entities cannot be used as a row of a derived table or CTE (`LQL-3071`). Pick the id or columns of the entity.

Example code: [`KotlinSelectEntityTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinSelectEntityTest.kt) (`select-joined`, `select-entities`).

<a id="three-entities-and-repeated-rows"></a>
### Many entities and repeated rows

You can join up to 10 entities (the main entity and 9 joined entities). Each join adds one more entity to the lambda arguments: `{ o, c, i -> ... }`.

<!-- region: ManualKotlinSelectTest.kt#join-three -->
```kotlin
// orders of a customer with an item of quantity > 10 (an order with two such items is two SQL rows)
val q = qb.from(Order::class.java)
    .join(Customer::class.java) { o, c -> o.customer?.id == c.id }
    .join(Item::class.java) { o, _, i -> i.order?.id == o.id }
    .where { _, _, i -> i.qty > 10 }
assertEquals("select e0 from Order e0 join Customer e1 on e0.customer is not null and e0.customer.id = e1.id"
    + " join Item e2 on e2.order.id = e0.id where e2.qty > 10", q.toHql().hql())
```

An example that joins 10 entities:

<!-- region: KotlinSelectEntityTest.kt#ten -->
```kotlin
// up to 10 entities: each lambda gets all of them in join order
val q = qb.from(Order::class.java)
    .join(Customer::class.java) { o, c -> o.customer?.id == c.id }
    .join(Order::class.java) { o, _, a -> a.id == o.id }
    .join(Order::class.java) { _, _, a, b -> b.id == a.id }
    .join(Order::class.java) { _, _, _, b, d -> d.id == b.id }
    .join(Order::class.java) { _, _, _, _, d, e -> e.id == d.id }
    .join(Order::class.java) { _, _, _, _, _, e, f -> f.id == e.id }
    .join(Order::class.java) { _, _, _, _, _, _, f, g -> g.id == f.id }
    .join(Order::class.java) { _, _, _, _, _, _, _, g, h -> h.id == g.id }
    .leftJoin(Order::class.java) { _, _, _, _, _, _, _, _, h, k -> k.id == h.id }
    .where { _, c, _, _, _, _, _, _, _, k -> c.name == "Kim" && k.price > 0.0 }
    .select { _, c, _, _, _, _, _, _, _, k -> c.name to k.id }
```

> [!WARNING]
> If many rows match the join condition, in SQL **the rows of the main entity repeat for each match**. `toList()` and `toSet()` have no repeats, because Hibernate returns the same entity only once.
> But `count()` and `limit` count the repeated SQL rows. To count orders or to fill pages exactly, use an `exists()` subquery ([5. Subqueries](05-subquery.md)) instead of a join, or apply `distinct()` to the join query (see [`distinct()` on a join](#distinct-on-a-join)).

<a id="cross-join"></a>
### Cross join

`crossJoin` has no join condition. The function name shows that you want every combination of rows. Write the condition in `where`.

<!-- region: ManualKotlinSelectTest.kt#cross-join -->
```kotlin
// every pair of rows; the conditions go in where
val q = qb.from(Order::class.java)
    .crossJoin(Customer::class.java)
    .where { o, c -> c.grade != null && o.price > 100 }
assertEquals("select e0 from Order e0 cross join Customer e1 where e1.grade is not null and e0.price > 100.0",
    q.toHql().hql())
```

lambdaQL has no `from(Order::class.java, Customer::class.java)` (comma join). If you forget the condition, you get every combination by mistake, and it is not clear which entity is the result. Use `crossJoin` for such queries.

<a id="paging"></a>
### Paging

With paging, lambdaQL adds the ids of **all** entities as the last sort keys. So pages do not overlap.

<!-- region: ManualKotlinSelectTest.kt#join-paging -->
```kotlin
// with paging, the id of every entity is added to the order, so the pages do not overlap
val pairs = qb.from(Order::class.java)
    .crossJoin(Customer::class.java)
    .orderBy { o, _ -> desc(o.price) }
    .select { o, c -> Row(o.id, c.name) }
assertEquals("select e0.id, e1.name from Order e0 cross join Customer e1 order by e0.price desc, e0.id asc, e1.id asc",
    pairs.limit(5).toHql().hql())
```

If you read the pages one after another, every combination comes once, in sort order.

<a id="distinct-on-a-join"></a>
### `distinct()` on a join

`distinct()` on a join query makes one SQL row for each row of the main entity. The HQL is `select distinct e0`, and `count()` is `count(distinct e0)`.
`toList()` and `toSet()` return each main entity once even without `distinct()`. You need `distinct()` so that `count()` and `limit` count main entities.

- The sort keys must be columns of the main entity itself (`LQL-3046`). Sorting by a column of a joined entity or by an association path is an error.
- With paging, only the id of the main entity is added as a tie-breaker. The ids of joined entities are not added.
- If you use `select(...)` after `distinct()`, the values are selected with `select distinct`.
- Consider an `exists` subquery ([5. Subqueries](05-subquery.md)) first. It gives the same rows without repeats, and it is often faster.

<!-- region: KotlinQueryTest.kt#join-distinct -->
```kotlin
// an order with two matching items is two SQL rows; distinct() makes it one row in SQL
val q = qb.from(Order::class.java)
    .join(Item::class.java) { o, i -> i.order?.id == o.id }
    .where { _, i -> i.qty > 0 }
    .distinct()
assertEquals("select distinct e0 from Order e0 join Item e1 on e1.order.id = e0.id where e1.qty > 0", q.toHql().hql())
```

<a id="join-a-derived-table-or-cte"></a>
### Join a derived table or CTE

You can join the derived table in [6.9](#69-derived-table-subquery-in-from) and the CTE in [6.10](#610-cte-with) like an entity.
The forms are `join(cte) { c, x -> ... }`, `leftJoin(cte) { ... }`, `join(qb.from(...)...select { Row(...) }) { ... }` and `leftJoin(...) { ... }`, and you can use them as the second or third root.

- The HQL is `with t as (...) ... join t e1 on ...` or `join (select ...) e1 on ...`.
- The row type is a Kotlin data class (or a Java record). A property (`x.total`) reads a column.
- If one statement reads the same `Cte` object twice, the definition is written once, and both joins read the same name: `join t e1 on ... left join t e2 on ...`. Different `Cte` objects get their own names, as in `with t as (...), t2 as (...)` (measured on three databases).
- A derived row has no id, so `x == null` is an error (`LQL-3067`). Compare a column, as in `x.customerId == null`.
- Hibernate 6.6 and 7.4 both accept `select distinct` of an entity, inner/left joins of derived tables and CTEs, and entity joins inside subqueries (measured on three databases).

<!-- region: KotlinQueryTest.kt#join-cte -->
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
        + " join t e1 on e1.customerId = e0.id where e1.total > 100.0", rows.toHql().hql())
```

Even if `x.customerId` is a nullable `Int?`, `x.customerId == c.id` is `e1.customerId = e0.id`. The null check that Kotlin adds is not read.

A join with an unnamed derived table (`CustomerTotal` is the row type in [6.9](#69-derived-table-subquery-in-from)):

<!-- region: ManualKotlinSelectTest.kt#join-derived -->
```kotlin
// a derived table without a name; a customer without orders has no row in it (left join)
val small = qb.from(Customer::class.java)
    .leftJoin(qb.from(Order::class.java)
        .where { it.customer != null }
        .groupBy { it.customer?.id }
        .select { CustomerTotal(it.customer?.id, Agg.count(), Agg.sum(it.price)) }) { c, x -> x.customerId == c.id }
    .where { _, x -> x.customerId == null || x.total!! < 210 }
assertEquals("select e0 from Customer e0 left join (select d0.customer.id as customerId, count(*) as cnt,"
    + " sum(d0.price) as total from Order d0 where d0.customer is not null group by d0.customer.id) e1"
    + " on e1.customerId = e0.id where e1.customerId is null or e1.total < 210.0", small.toHql().hql())
```

Reading the same CTE twice:

<!-- region: ManualKotlinSelectTest.kt#cte-reuse -->
```kotlin
// the same CTE read twice is written once
val reused = qb.from(Customer::class.java)
    .join(t) { c, x -> x.customerId == c.id }
    .leftJoin(t) { c, _, y -> y.customerId == c.id && y.total!! > 210 }
    .select { c, _, y -> c.name to y.total }
assertEquals("with t as (select d0.customer.id as customerId, count(*) as cnt, sum(d0.price) as total from Order d0"
    + " where d0.customer is not null group by d0.customer.id)"
    + " select e0.name, e2.total from Customer e0 join t e1 on e1.customerId = e0.id"
    + " left join t e2 on e2.customerId = e0.id and e2.total > 210.0", reused.toHql().hql())
```

<a id="what-a-join-query-has"></a>
### What a join query has

| After | Functions |
|---|---|
| `join`, `leftJoin`, `crossJoin` | `where`, `join` / `leftJoin` / `crossJoin` (up to 10 entities, including derived tables and CTEs), `distinct`, `orderBy`, `select`, `limit`, `toList`, `toSet`, `first`, `count`, `exists`, `toHql` |
| `orderBy { o, c -> ... }` | `select`, `limit`, `offset`, `toList`, `first` and the other functions of ordered queries ([4. Sorting and paging](04-order-paging.md)) |

For joins inside a subquery, see [5. Subqueries](05-subquery.md#joins-inside-a-subquery).
Not supported: joins in insert, 11 or more entities, the join form of `SortSpec`.
It was decided not to add joins to update and delete. Use association paths or `sub`.

Example code: [`ManualKotlinSelectTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSelectTest.kt) (`join`, `left-join-null`, `row-type`, `join-sort-select`, `join-three`, `cross-join`, `join-paging`, `join-derived`, `cte-reuse`), [`KotlinQueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt) (`join-distinct`, `join-cte`), [`KotlinSelectEntityTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinSelectEntityTest.kt) (`ten`).

---

<a id="69-derived-table-subquery-in-from"></a>
## 6.9 Derived table (subquery in FROM)

Build a value query with `select` (a data class, see [6.3](#63-select-a-data-class-pair-or-triple)), and pass it to `qb.from(...)`.
Its rows become the table of the outer query.
The result is a `DerivedQuery<R>`, where `R` is the row type.

The row types of this section and [6.10](#610-cte-with):

<!-- region: ManualKotlinSelectTest.kt#derived-types -->
```kotlin
/** A row of a derived table or CTE: the properties name the columns (J5). */
data class CustomerTotal(val customerId: Int?, val cnt: Long, val total: Double?)
data class Priced(val id: Long?, val price: Double?)
```

<!-- region: ManualKotlinSelectTest.kt#derived -->
```kotlin
// the rows of a group query are the table of the outer query; r.total reads the column "total"
val q = qb.from(qb.from(Order::class.java)
        .where { it.customer != null }
        .groupBy { it.customer?.id }
        .select { CustomerTotal(it.customer?.id, Agg.count(), Agg.sum(it.price)) })
    .where { r -> r.total!! > 100 }
    .orderBy { r -> desc(r.total) }
assertEquals("select e0.customerId, e0.cnt, e0.total from (select d0.customer.id as customerId, count(*) as cnt,"
    + " sum(d0.price) as total from Order d0 where d0.customer is not null group by d0.customer.id) e0"
    + " where e0.total > 100.0 order by e0.total desc nulls last", q.toHql().hql())
```

- The alias of the inner query is `d0` (joins are `dj1`, ...), and the alias of the outer query is `e0`.
- Each property of the data class gets a column name, in the order of the primary constructor: `as customerId`, `as cnt`, `as total`.
- In the outer lambda, you read columns through properties. `r.total` is `e0.total`.
- The outer query has `where`, `orderBy`, `limit`, `offset`, `select`, `toList`, `first`, `count` and `toHql`.

You can also select one value of the row (`.select { r -> r.customerId }`). The captured values of the inner and outer queries are bound in order.
The `limit` and `offset` of the inner query stay in the inner query (HQL `limit`, `offset`). So you can sort the top rows again.

<!-- region: ManualKotlinSelectTest.kt#derived-limit -->
```kotlin
// the three most expensive orders, then the cheapest of them
val cheapestOfTop = qb.from(qb.from(Order::class.java)
        .orderBy { o -> desc(o.price) }
        .limit(3)
        .select { Priced(it.id, it.price) })
    .orderBy { r -> asc(r.price) }
    .first()
```

Hibernate 7.4 gives the columns of a derived table names such as `c0`, `c1`, `c2`. H2 puts the column list on the alias (`r1_0(c0,c1,c2)`). MariaDB and Oracle write column aliases inside the subquery.

Rules:

- The row type must be a **Kotlin data class** (or a Java record).
  lambdaQL takes the column names from the data class properties, in the order of the primary constructor.
- A normal class, `Pair`, `Triple` or a single value cannot be used here, because lambdaQL cannot decide the column names. In this case `TranslationException` (`LQL-3064`) is thrown.

<!-- region: ManualKotlinSelectTest.kt#derived-row-type -->
```kotlin
// a single value (or a Pair, or a class that is not a data class) has no column names
val e = assertThrows(LambdaQLException::class.java) { qb.from(qb.from(Order::class.java).select { it.price }) }
assertEquals("LQL-3064", e.code())
```

For joining a derived table to an entity query, see [Join a derived table or CTE](#join-a-derived-table-or-cte).

Not supported: joins between derived tables, derived tables inside update, delete and insert.

Hibernate also adds the `@SQLRestriction` and `@SoftDelete` conditions of the entity to the `on` of an entity join and inside a derived table (measured on MariaDB 10.11 and Oracle 23.26). lambdaQL does not handle these conditions on its own.

Example code: [`ManualKotlinSelectTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSelectTest.kt) (`derived-types`, `derived`, `derived-limit`, `derived-row-type`).

---

<a id="610-cte-with"></a>
## 6.10 CTE (`with`)

Make a handle of type `Cte<R>` with `qb.with(valueQuery)`. Then read it with `qb.from(handle)`.
The inner query is the same as for a derived table (6.9).

<!-- region: ManualKotlinSelectTest.kt#cte -->
```kotlin
val perCustomer = qb.from(Order::class.java)
    .where { it.customer != null }
    .groupBy { it.customer?.id }
    .select { CustomerTotal(it.customer?.id, Agg.count(), Agg.sum(it.price)) }
val t: Cte<CustomerTotal> = qb.with(perCustomer)
val q = qb.from(t).where { r -> r.cnt >= 2 }
assertEquals("with t as (select d0.customer.id as customerId, count(*) as cnt, sum(d0.price) as total"
    + " from Order d0 where d0.customer is not null group by d0.customer.id)"
    + " select e0.customerId, e0.cnt, e0.total from t e0 where e0.cnt >= 2L", q.toHql().hql())
```

MariaDB and Oracle run a real `with`. Hibernate writes the column list itself: `with t (customerId,cnt,total) as (...)`. H2 turns the CTE into a derived table.

Notes:

- The name of the CTE is `t`. The outer query always gives it the alias `e0` (`from t e0`). HQL needs the alias.
- The column names of the CTE come from the select aliases (`as customerId`). HQL does not accept a column list after the CTE name, and Hibernate adds the column list to the SQL.
- MariaDB and Oracle run a real `with`. On H2, Hibernate expands it into a derived table. The result is the same.
- The row type rule is the same as for a derived table: a data class or a record.
- For joining a CTE to an entity query, see [Join a derived table or CTE](#join-a-derived-table-or-cte).
- You can read the same CTE twice in one query. The definition is written only once (see [Join a derived table or CTE](#join-a-derived-table-or-cte)).
- Not supported: CTEs inside update, delete and insert.

Example code: [`ManualKotlinSelectTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSelectTest.kt) (`cte`).

---

<a id="611-rules-j1-to-j8"></a>
## 6.11 Rules J1 to J8

| # | Rule | Status |
|---|---|---|
| J1 | A `select` lambda returns **one value expression** or **one constructor call**. Each argument of the constructor is a value expression (the rules of [3. Conditions (`where`)](03-where.md)). An entity itself can also be a value ([Select entities](#select-entities)). An `if` or `when` that picks a value becomes `case when` ([3.5.1](03-where.md#351-values-chosen-by-a-condition)). An association path used only in `select` becomes a `left join` alias, so no rows are lost. A required association (`optional = false`) is an inner join (A4). If the same path is used in `where`, `select` uses the join of `where` (R24). | Implemented |
| J2 | For a constructor, lambdaQL selects only the columns and calls the constructor itself. It does not write `select new`. `Pair`/`Triple` are made in the same way. | Implemented |
| J3 | If there are aggregates, every non-aggregate value in `select` and `having` (and the values of `insert ... select`) must be a `groupBy` key. If not, an error happens at analysis time. | Implemented. The error is `TranslationException` (`LQL-3035`, see [11](../11-errors-and-languages.md)) |
| J4 | `sum`/`avg`/`min`/`max` give NULL when there are no rows or all values are NULL. `count` gives 0. Write constructor parameters as nullable. If NULL comes to a non-null parameter, `IllegalStateException` is thrown. | Implemented |
| J5 | The row type of a derived table or CTE: **only a Kotlin data class or a Java record**. A normal class is allowed only in a simple `select`. Otherwise `LQL-3064`. This also applies to the rows of joined derived tables and CTEs. A derived row cannot be compared with `null` (`LQL-3067`). | Implemented |
| J6 | A CTE made with `qb.with(...)` gets its column names from the select aliases: `with t as (select d0.customer.id as customerId, ...) select e0.customerId ... from t e0`. HQL does not accept a column list after the name. Hibernate rejects `with t (col list) as (...)` with `SyntaxException`. Hibernate writes the column list into the SQL itself (`with t (customerId,cnt,total) as (...)` on MariaDB and Oracle). H2 turns the CTE into a derived table. The outer query always uses an alias (`from t e0`). | Implemented |
| J7 | `distinct()` writes `select distinct`. With a sort, the sort keys must be in the select list (a database rule). lambdaQL checks this at analysis time. For `distinct()` on a join, the sort keys must be columns of the main entity itself. | Implemented |
| J8 | Select works with sorting ([4. Sorting and paging](04-order-paging.md)), paging and subqueries ([5. Subqueries](05-subquery.md)). There is no sort by an aggregate result of a group query (`desc(s.total)`). | Implemented |

An example of J3 is in [6.5](#every-non-aggregate-value-must-be-a-key-rule-j3). `Agg` in `where`, J7 and J4:

<!-- region: ManualKotlinSelectTest.kt#rules -->
```kotlin
// aggregates belong in having, not in where
val where = assertThrows(LambdaQLException::class.java) { qb.from(Order::class.java).where { Agg.count() > 1 } }
assertEquals("LQL-3005", where.code())
// with distinct, the sort key must be selected
val j7 = assertThrows(LambdaQLException::class.java) {
    qb.from(Order::class.java).orderBy { o -> asc(o.id) }.select { it.price }.distinct().toHql()
}
assertEquals("LQL-3046", j7.code())
// sum over no rows is NULL; the non-null parameter of StrictTotal(val total: Double) cannot take it
assertThrows(IllegalStateException::class.java) {
    qb.from(Order::class.java).where { it.status.toInt() == 9 }.select { StrictTotal(Agg.sum(it.price)) }.toList()
}
```

Example code: [`ManualKotlinSelectTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSelectTest.kt) (`rules`).

---

<a id="612-not-in-v01"></a>
## 6.12 Not in v0.1

The features of this chapter do not include the following.

| Item | Note |
|---|---|
| Sorting by an aggregate result or a select alias (for example, `orderBy { s -> desc(s.total) }`), `orderBy` on a group query | |
| User aggregate functions and window functions | |
| Joins in insert, 11 or more entities, three or more entities in a subquery | |
| Joins in update and delete | Decided not to add. Use association paths or `sub` |
| Derived tables and CTEs in DML | |

The errors of this chapter are `TranslationException`s with a code. A wrong group key is `LQL-3035`, `Agg` in `where` is `LQL-3005`, a sort key that is not selected with `distinct` is `LQL-3046`, and the row type of a derived table or CTE is `LQL-3064`. See [11. Errors and languages](../11-errors-and-languages.md).

---

[Kotlin track](README.md) | Previous: [5. Subqueries](05-subquery.md) | Next: [7. Update, insert, delete](07-dml.md) | [Java version of this page](../java/06-select-group.md)
