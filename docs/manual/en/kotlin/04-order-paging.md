<a id="4-sorting-and-paging-kotlin"></a>
# 4. Sorting and paging (Kotlin)

[Kotlin track](README.md) | [Manual home](../README.md) | Previous: [3. Conditions (`where`)](03-where.md) | Next: [5. Subqueries](05-subquery.md) | [Java version of this page](../java/04-order-paging.md)

Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md)

---

<a id="41-one-lambda-for-the-whole-sort"></a>
## 4.1 Sorting with a lambda

You write the sort as **one lambda**. The lambda takes the entity and describes the keys and directions with the sort functions of the class `By`.
Chain `desc(...)` and `asc(...)` to add keys.

<!-- region: KotlinQueryTest.kt#sort -->
```kotlin
import org.lambdaql.query.By.desc

val q = qb.from(Order::class.java)
    .where { it.isActive }
    .orderBy { o -> desc(o.price).asc(o.customer?.name) }
    .limit(3)
assertEquals("select e0 from Order e0 left join e0.customer j1 where e0.active = true"
    + " order by e0.price desc, j1.name asc nulls first, e0.id asc", q.toHql().hql())
```

The examples use the example entities written in Java (`Order`, `Customer`, `Item`, `OrderLine`). `price` is a primitive `double`, so it has no `nulls` part, and the getter of `active` is `isActive()` (Kotlin `it.isActive`).
If you use `limit`, lambdaQL adds `e0.id asc` as the last sort key (see [Tie-breaker](#tie-breaker-automatic-id)).

- The sort functions are static methods of `By`: `asc(key)`, `desc(key)`, `asc(key, Nulls.FIRST)`, `desc(key, Nulls.LAST)`, `sort(key, Direction)`.
- lambdaQL does not call the lambda. It reads the bytecode opcodes. The sort functions only describe the order. If you call one outside a sort lambda, it throws `IllegalStateException` (`LQL-4046`).

Example code: [`KotlinQueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt) (`sort`).

<a id="42-the-short-forms-one-key"></a>
## 4.2 The short forms (one key)

If there is only one key, you can return just the key, with no sort function.

| Kotlin | HQL |
|---|---|
| `orderBy { it.price }` | `order by e0.price asc` |
| `orderBy({ it.updateAt }, Direction.DESC)` | `order by e0.updateAt desc nulls last` |

<!-- region: ManualKotlinSortTest.kt#short-form -->
```kotlin
// one key: the key alone, ascending unless a Direction is given
val byPrice = qb.from(Order::class.java).orderBy { it.price }
assertEquals("select e0 from Order e0 order by e0.price asc", byPrice.toHql().hql())
val newest = qb.from(Order::class.java).orderBy({ it.updateAt }, Direction.DESC)
assertEquals("select e0 from Order e0 order by e0.updateAt desc nulls last", newest.toHql().hql())
```

Without a `Direction`, the short form sorts in ascending order (`ASC`).
The `Direction` argument is only for one key. If you use a sort function together with a `Direction` argument, as in `orderBy({ o -> desc(o.price) }, Direction.ASC)`, it is rejected with `LQL-3069`. If you need to take the direction as an argument, use `orderBy { o -> sort(o.price, direction) }`.

Example code: [`ManualKotlinSortTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSortTest.kt) (`short-form`).

## 4.3 `Direction`

`Direction` has six values.

| Value | Meaning | NULL position |
|---|---|---|
| `ASC` | Ascending | NULL **first** (default policy) |
| `DESC` | Descending | NULL **last** (default policy) |
| `ASC_NULLS_FIRST` | Ascending | NULL first. Same as `ASC` |
| `ASC_NULLS_LAST` | Ascending | NULL last |
| `DESC_NULLS_FIRST` | Descending | NULL first |
| `DESC_NULLS_LAST` | Descending | NULL last. Same as `DESC` |

`Direction` also has two helper methods.

- `Direction.parse(text)` reads a direction from text, such as a request parameter. Case does not matter, and `-`, `_` and spaces are treated the same: `Direction.parse("desc-nulls-last")` is `DESC_NULLS_LAST`. Unknown text throws `IllegalArgumentException`.
- `reversed()` returns the opposite direction. The NULL position is reversed too, so the whole order is turned around: `Direction.ASC.reversed()` is `DESC`, and `Direction.ASC_NULLS_LAST.reversed()` is `DESC_NULLS_FIRST`.

**To choose the NULL position**, use the enum `Nulls` as the second argument: `asc(key, Nulls.LAST)`, `desc(key, Nulls.FIRST)`.
`Nulls.FIRST` means "NULL first", and `Nulls.LAST` means "NULL last".
Without the second argument, `asc(key)` and `desc(key)` use the default NULL policy (see [4.4](#44-null-ordering-is-the-same-on-every-database)).

| What you write | Same meaning |
|---|---|
| `asc(key)` | `ASC` (NULL first, default policy) |
| `desc(key)` | `DESC` (NULL last, default policy) |
| `asc(key, Nulls.FIRST)` | `ASC_NULLS_FIRST` |
| `asc(key, Nulls.LAST)` | `ASC_NULLS_LAST` |
| `desc(key, Nulls.FIRST)` | `DESC_NULLS_FIRST` |
| `desc(key, Nulls.LAST)` | `DESC_NULLS_LAST` |

**When the direction comes from a variable**, use `sort(key, direction)` with a captured `Direction`.

<!-- region: ManualKotlinSortTest.kt#nulls-and-direction -->
```kotlin
// the second argument: true = NULL first, false = NULL last
val nullsLast = qb.from(Order::class.java).orderBy { o -> asc(o.description, Nulls.LAST).asc(o.id) }
assertEquals("select e0 from Order e0 order by e0.description asc nulls last, e0.id asc", nullsLast.toHql().hql())
// a direction from a variable: choose it outside the lambda
val dir = if (newestFirst) Direction.DESC else Direction.ASC
val byDate = qb.from(Order::class.java).orderBy { o -> sort(o.updateAt, dir).asc(o.id) }
assertEquals("select e0 from Order e0 order by e0.updateAt desc nulls last, e0.id asc", byDate.toHql().hql())
// the short form with a NULL position
val byName = qb.from(Order::class.java).orderBy({ it.customer?.name }, Direction.DESC_NULLS_LAST)
assertEquals("select e0 from Order e0 left join e0.customer j1 order by j1.name desc nulls last", byName.toHql().hql())
```

The lambda is analyzed only once. `dir` is read each time the query runs. If you pass another direction to the same lambda, the HQL and the result rows follow that direction. If the direction is `null`, an exception is thrown when the query runs.

Choose `dir` **outside** the lambda. An `if` inside a sort lambda is rejected (see [4.6](#46-rules-for-the-lambda-body)).

Example code: [`ManualKotlinSortTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSortTest.kt) (`nulls-and-direction`).

<a id="44-null-ordering-is-the-same-on-every-database"></a>
## 4.4 NULL ordering is the same on every database

The position of NULL differs by database.

| | H2 | MariaDB | Oracle |
|---|---|---|---|
| `ASC` default | NULL first | NULL first | NULL **last** |
| `DESC` default | NULL last | NULL last | NULL **first** |
| `NULLS FIRST/LAST` in SQL | Supported | **Not supported (syntax error)** | Supported |

lambdaQL solves this with one policy: **NULL is the smallest value**.

- `ASC` = `ASC_NULLS_FIRST`
- `DESC` = `DESC_NULLS_LAST`

This is the same as the Kotlin comparator `nullsFirst(naturalOrder())`.
With this policy, all three databases return the same order (for numbers and dates).

**How lambdaQL writes it.** For a key that can be NULL, lambdaQL always writes the NULL position in the HQL. Example: `e0.description asc nulls first`.
A key that can never be NULL (a JVM primitive such as `active: Boolean` or `qty: Int`, or the `@Id`) has no `nulls` part.
lambdaQL looks at the JPA metadata, not at the Kotlin type. So `id`, the `@Id` (`Long?` in Kotlin), has no `nulls` part, and `description` (`String?`) gets one (see [4.10](#410-reusable-and-external-sorts)).
A column after an association gets one, because it is NULL when there is no association (there can be orders without a customer). It does not get one if every association step of the path is required (`optional = false`) and the value can never be NULL.

Then Hibernate writes the SQL for each database. If the position is the same as the database default, it leaves out this clause.
MariaDB has no `NULLS` syntax, so Hibernate writes a non-default position as a `CASE` expression.

The SQL that is made:

| What you write | H2 | MariaDB | Oracle |
|---|---|---|---|
| `asc(key)`, `desc(key)` (`ASC`, `DESC`) | No extra clause (already the default) | No extra clause | Adds `nulls first` / `nulls last` |
| A NULL position that is the same as the database default (`asc(key, nulls)`, `desc(key, nulls)`, `*_NULLS_*`) | No extra clause | No extra clause | No extra clause |
| A NULL position that is **not** the database default | `nulls first/last` | Hibernate writes `case when x is null ...` | `nulls first/last` |
| A key that can never be NULL (any direction) | No extra clause | No extra clause | No extra clause |

> [!TIP]
> On MariaDB, `asc(key, Nulls.LAST)` (`ASC_NULLS_LAST`) and `desc(key, Nulls.FIRST)` (`DESC_NULLS_FIRST`) become a `CASE` expression. The database cannot use an index for this sort. If you can, use `asc(key)` / `desc(key)`.

<a id="45-what-a-sort-key-can-be"></a>
## 4.5 What a sort key can be

| Key | Allowed | Notes |
|---|---|---|
| Column: `o.price` | Yes | Nullable keys (`Double?`, `String?`) are fine |
| To-one path: `o.customer?.name`, `o.customer!!.name` | Yes | `left join` (inner join for a required association), see below |
| `@Lob` column | Yes | Oracle rejects it. `LQL-5010` at `DEBUG` level, see below |
| `@Enumerated(EnumType.STRING)` enum column | Yes | Sorted by name (not by declaration order). `LQL-5011` at `DEBUG` level, see below |
| Arithmetic: `o.price * 2`, `+`, `-` | Yes | `(e0.price * 2.0)` |
| Widening conversion: `o.status.toLong()` | Yes | The conversion does not appear in the HQL |
| Elvis on a non-boolean value: `o.description ?: ""` | Yes | `coalesce(e0.description, '') asc nulls first` |
| A value that does not use the entity: `asc(1)`, a captured value | **Rejected** | See [4.6](#46-rules-for-the-lambda-body) |
| `if` inside the key value: `asc(if (o.isActive) 0 else 1)` | Yes | `case when` ([3.5.1](03-where.md#351-values-chosen-by-a-condition)) |
| `if` that picks a sort function | **Rejected** (`LQL-2023`) | See [4.6](#46-rules-for-the-lambda-body) |

<!-- region: ManualKotlinSortTest.kt#sort-keys -->
```kotlin
// arithmetic, a widening conversion and the Elvis operator of a value
assertEquals("select e0 from Order e0 order by (e0.price * 2.0) asc",
    qb.from(Order::class.java).orderBy { o -> asc(o.price * 2) }.toHql().hql())
assertEquals("select e0 from Order e0 order by e0.status asc",
    qb.from(Order::class.java).orderBy { o -> asc(o.status.toLong()) }.toHql().hql())
val elvis = qb.from(Order::class.java).orderBy { o -> asc(o.description ?: "").asc(o.id) }
assertEquals("select e0 from Order e0 order by coalesce(e0.description, '') asc nulls first, e0.id asc", elvis.toHql().hql())
```

**Association paths use `left join`.** In HQL, the simple path `order by o.customer.name` becomes an inner join on all three databases. Orders without a customer disappear.
lambdaQL adds a `left join` instead. Orders without a customer also stay in the result. For such orders, `j1.name` is NULL, so they come first with `asc`.

<!-- region: ManualKotlinSortTest.kt#sort-path -->
```kotlin
// a path used only for sorting is a left join: the orders without a customer stay
val q = qb.from(Order::class.java).orderBy { o -> asc(o.customer?.name).asc(o.id) }
assertEquals("select e0 from Order e0 left join e0.customer j1 order by j1.name asc nulls first, e0.id asc", q.toHql().hql())
```

**SQL by database**

<!-- sql: sort-path -->
```sql
-- H2, MariaDB
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
order by c1_0.name,o1_0.id

-- Oracle
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
order by c1_0.name asc nulls first,o1_0.id
```

You can see the NULL policy in the SQL. H2 and MariaDB leave out the clause when it is the same as their own default. Oracle's own default is the opposite, so it writes `asc nulls first` / `desc nulls last`. MariaDB has no `NULLS` syntax, so for a non-default position it first sorts by `case when … is null then … end`.

**A required association uses an inner join.** For an association mapped with `optional = false` (for example, `@ManyToOne(optional = false)`), the target always exists, so an inner join loses no rows.
So such an association uses `join`, even for a path used only in a sort or select. Other associations use `left join`, as above.
If every association step of the path is required and the value can never be NULL, the sort does not get `nulls first/last` either.

The `order` of the example entity `OrderLine` is `@ManyToOne(optional = false)`.

<!-- region: ManualKotlinSortTest.kt#required-association -->
```kotlin
// order is optional = false: a sort or select path through it cannot lose rows, so it is an inner join
val sorted = qb.from(OrderLine::class.java).orderBy { l -> asc(l.order.price) }
assertEquals("select e0 from OrderLine e0 join e0.order j1 order by j1.price asc", sorted.toHql().hql())
val prices = qb.from(OrderLine::class.java).select { it.order.price }
assertEquals("select j1.price from OrderLine e0 join e0.order j1", prices.toHql().hql())
```

**Sorting a `@Lob` column.** If you use a `@Lob` column in `orderBy`, `distinct` or `groupBy`, H2 and MariaDB run it, but Oracle rejects it (ORA-22848, ORA-22849, measured). An `=` comparison works on all three databases.
lambdaQL does not change the query. It logs `LQL-5010` at `DEBUG` level once per column and use.

Each of the three queries below logs `LQL-5010` once.

<!-- region: ManualKotlinSortTest.kt#lob -->
```kotlin
// memo is @Lob: H2 and MariaDB sort it, group it and make it distinct, Oracle rejects it (ORA-22848, ORA-22849).
// lambdaQL keeps the query and logs LQL-5010 at DEBUG
qb.from(OrderLine::class.java).orderBy { l -> asc(l.memo) }.toHql()
qb.from(OrderLine::class.java).groupBy { it.memo }.select { it.memo }.toHql()
qb.from(OrderLine::class.java).select { it.memo }.distinct().toHql()
```

**Sorting an `EnumType.STRING` enum.** For an enum column mapped with `@Enumerated(EnumType.STRING)`, the database sorts by the string order of the constant **names**. A Java enum comparison follows the declaration order, so the result order can differ.
lambdaQL does not change the query. It logs `LQL-5011` at `DEBUG` level once per column. `ORDINAL` (or no `@Enumerated`) sorts by declaration order, so it is not logged.

The query below logs `LQL-5011` once.

<!-- region: ManualKotlinSortTest.kt#enum-string-sort -->
```kotlin
// kind is @Enumerated(EnumType.STRING): the database sorts by name, Kotlin by declaration order (LQL-5011, DEBUG)
val q = qb.from(Order::class.java).orderBy { o -> asc(o.kind) }
assertEquals("select e0 from Order e0 order by e0.kind asc nulls first", q.toHql().hql())
```

The alias is shared with `where`. If `where` already joined the same path, the sort uses that join.
If `where` used this path with an inner join, orders without a customer are already gone. If `where` used the path with a `left join`, the sort also uses that `left join` (see [3.10](03-where.md#310-association-paths)).

Example code: [`ManualKotlinSortTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSortTest.kt) (`sort-keys`, `sort-path`, `required-association`, `lob`, `enum-string-sort`).

<a id="46-rules-for-the-lambda-body"></a>
## 4.6 Rules for the lambda body

The body must be **one key** or a **straight chain of `By` functions**.

<!-- region: ManualKotlinSortTest.kt#lambda-rules -->
```kotlin
// allowed: a straight chain of by calls
qb.from(Order::class.java).orderBy { o -> desc(o.price).asc(o.id) }
// rejected when orderBy is called: sort functions chosen by a condition (a condition inside one key is allowed)
val branch = assertThrows(LambdaQLException::class.java) {
    qb.from(Order::class.java).orderBy { o -> if (flag) desc(o.price) else asc(o.id) }
}
assertEquals("LQL-2023", branch.code())
// rejected: a key that does not use the entity
val constant = assertThrows(LambdaQLException::class.java) {
    qb.from(Order::class.java).orderBy { _ -> asc(1) }
}
assertEquals("LQL-3031", constant.code())
```

lambdaQL checks this when you call `orderBy` and throws `LambdaQLException` (`LQL-2023` for a sort function chosen by a condition, `LQL-3031` for a key that does not use the entity).
Every key must use the entity. A key made only of constants or captured values is rejected.

If you need to change the direction at run time, pass it as a variable: `sort(key, dir)`. If you need to change the key itself, build a query in each of two branches of normal Kotlin code.

Example code: [`ManualKotlinSortTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSortTest.kt) (`lambda-rules`).

<a id="47-order-of-calls"></a>
## 4.7 Order of calls

Write a query in the order `from(...).where { }.orderBy { }.limit(...).offset(...)`.
If you use only `limit` or `offset` without `orderBy`, no `order by` is added (see [4.8](#48-paging)).

<a id="48-paging"></a>
## 4.8 Paging

<!-- region: ManualKotlinSortTest.kt#page -->
```kotlin
val page = qb.from(Order::class.java)
    .where { it.status.toInt() == 1 }
    .orderBy { o -> desc(o.price) }
    .limit(2)      // page size
    .offset(2)     // skip 2 rows (page 2)
// limit and offset go to Hibernate; the id is added as the last key
assertEquals("select e0 from Order e0 where e0.status = 1 order by e0.price desc, e0.id asc", page.toHql().hql())
```

`limit` and `offset` are passed to Hibernate (`setMaxResults`, `setFirstResult`). The `status` of the example entity is a `short`, so the example compares with `it.status.toInt() == 1`. lambdaQL ignores this conversion.

`limit` and `offset` take a value of 0 or more. A negative value throws `IllegalArgumentException`.

lambdaQL does not write `LIMIT` in the HQL. Hibernate writes the right SQL for each database.

| Database | SQL |
|---|---|
| H2 | `offset ? rows fetch first ? rows only` |
| MariaDB | `limit ?, ?` |
| Oracle | `offset ? rows fetch first ? rows only` |

**Paging without `orderBy`.** If you call `limit` and `offset` without `orderBy`, no `order by` is added. The database decides which rows go on which page. To split pages in a stable way, use `orderBy`.

<!-- region: ManualKotlinSortTest.kt#page-only -->
```kotlin
// paging without orderBy: no order by, so which rows come is up to the database; use orderBy for stable pages
val q = qb.from(Order::class.java).where { it.isActive }.limit(2)
assertEquals("select e0 from Order e0 where e0.active = true", q.toHql().hql())
```

**SQL by database**

<!-- sql: page-only -->
```sql
-- H2, Oracle
select …
from orders o1_0
where o1_0.active=true
order by o1_0.id
fetch first ? rows only

-- MariaDB
select …
from orders o1_0
where o1_0.active=true
order by o1_0.id
limit ?
```

The paging SQL differs by database. H2 and Oracle take `fetch first ? rows only`, and MariaDB takes `limit ?`. With an offset, H2 and Oracle add `offset ? rows`, and MariaDB writes `limit ?,?`.

**First row and total count.**

- `orderBy { ... }.first()` returns the first row in that order as an `Optional`. It reads only one row. The `offset` is kept: `orderBy { ... }.offset(1).first()` returns the second row.
- `count()` counts all rows that match the condition. For a paged screen, keep the where-stage query in a value. Call `count()` on that value to get the total, and then call `orderBy { ... }.limit(...).toList()` to get the page.

<!-- region: ManualKotlinSortTest.kt#first-and-count -->
```kotlin
// keep the where step in a value: count() gives the number of all rows, orderBy ... limit gives one page
val active = qb.from(Order::class.java).where { it.isActive }
val total = active.count()
val page = active.orderBy { o -> desc(o.price) }.limit(2).toList()
// first() reads one row; offset(1).first() is the second row
val second = active.orderBy { o -> desc(o.price) }.offset(1).first()
```

See [Getting the result](02-getting-started.md#getting-the-result).

<a id="tie-breaker-automatic-id"></a>
### Tie-breaker (automatic `id`)

At the end of the HQL above there is `e0.id asc`, which lambdaQL added. The reasons are:

- Many rows can have the same `price`. The database can return such rows **in any order**. Oracle really does this (measured).
- Then the same row can appear twice on page 2 and page 3, or a row can be missing.

The rule: **if you use `orderBy` together with paging and the sort keys have no id, lambdaQL adds the root `@Id asc` as the last key.**

- It is added only once. If the keys already have the id (for example, `desc(o.id)`), it is not added.
- If the sort keys have a unique column of the main entity that can never be NULL, it is not added. This covers `@Column(unique = true)` with `nullable = false`, Hibernate `@NaturalId` and `@Id`, because this key alone decides the order of all rows.
  A unique column that can be NULL does not count, because NULL can be in many rows.
- Without `orderBy`, or without `limit` and `offset`, nothing is added.
- If there are explicit joins (`join`, `leftJoin`, `crossJoin`), the ids of all joined entities are also added, in join order (see [6.8](06-select-group.md#68-explicit-joins)).

The `code` of the example entity `OrderLine` is `@Column(unique = true, nullable = false)`.

<!-- region: ManualKotlinSortTest.kt#unique-key -->
```kotlin
// code is @Column(unique = true, nullable = false): it orders every row, so paging adds no id tie-breaker
assertEquals("select e0 from OrderLine e0 order by e0.code asc",
    qb.from(OrderLine::class.java).orderBy { l -> asc(l.code) }.limit(2).toHql().hql())
// a key that is not unique still gets the id tie-breaker
assertEquals("select e0 from OrderLine e0 join e0.order j1 order by j1.price asc, e0.id asc",
    qb.from(OrderLine::class.java).orderBy { l -> asc(l.order.price) }.limit(2).toHql().hql())
```

Example code: [`ManualKotlinSortTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSortTest.kt) (`page`, `page-only`, `first-and-count`, `unique-key`).

<a id="49-strings-and-special-values"></a>
## 4.9 Strings and special values

- String order follows the database collation. MariaDB `_ci` ignores case. Oracle uses `NLS_SORT`, which follows the JVM locale (for example, German order for `de_DE`). On Oracle, `NLS_SORT=BINARY` is recommended.
- Oracle stores `''` as NULL, so an empty string sorts like NULL.
- The order of `NaN` differs by database.

<a id="410-reusable-and-external-sorts"></a>
## 4.10 Reusable and external sorts

**`SortSpec`: a sort you can keep.** `SortSpec` is a sort that you keep in a value and use in many queries.
Make it like this: `SortSpec.of(Order::class.java) { o -> desc(o.id) }`. The lambda is the same as the lambda of `orderBy { o -> ... }`, so the same rules apply (see [4.6](#46-rules-for-the-lambda-body)).
Then pass it to `orderBy(spec)`.

<!-- region: ManualKotlinSortTest.kt#sort-spec -->
```kotlin
// keep it in a value (for example at the top level or in a companion object) and use it in many queries
val byDescription = SortSpec.of(Order::class.java) { o -> desc(o.description).asc(o.id) }
val q = qb.from(Order::class.java).where { it.isActive }.orderBy(byDescription)
assertEquals("select e0 from Order e0 where e0.active = true order by e0.description desc nulls last, e0.id asc",
    q.toHql().hql())
```

- A `SortSpec` cannot be changed. `then()` and `reversed()` return a new spec. So many queries and threads can safely share one spec. For example, you can keep it in a top-level `val`.
- lambdaQL checks the lambda when the spec is used in `orderBy`. Each lambda is analyzed only once.
- A captured value is the value at the moment the lambda was made. A spec in a top-level `val` keeps that value. If you need to change the direction at run time, use `orderBy { o -> sort(key, dir) }` (see [4.3](#43-direction)).
- You can also page after `orderBy(spec)`: `limit`, `offset`, `toList()`, `first()`.

**SQL by database**

<!-- sql: sort-spec -->
```sql
-- H2, MariaDB
select …
from orders o1_0
where o1_0.active=true
order by o1_0.description desc,o1_0.id

-- Oracle
select …
from orders o1_0
where o1_0.active=true
order by o1_0.description desc nulls last,o1_0.id
```

The SQL is the same as when you write the same sort with `orderBy { o -> ... }`. Oracle's default for `desc` is the opposite, so it adds `nulls last` (see [4.4](#44-null-ordering-is-the-same-on-every-database)).

**`then()`: add keys after the existing keys.** `a.then(b)` sorts first by the keys of `a`. Rows with the same values are then sorted by the keys of `b`.
`then()` takes another `SortSpec` or a sort lambda.

<!-- region: ManualKotlinSortTest.kt#sort-spec-then -->
```kotlin
val byStatus = SortSpec.of(Order::class.java) { o -> asc(o.status) }
val byPrice = SortSpec.of(Order::class.java) { o -> desc(o.price) }
val q = qb.from(Order::class.java).orderBy(byStatus.then(byPrice))
assertEquals("select e0 from Order e0 order by e0.status asc, e0.price desc", q.toHql().hql())
// then() also takes a sort lambda
assertEquals("select e0 from Order e0 order by e0.status asc, e0.id desc",
    qb.from(Order::class.java).orderBy(byStatus.then { o -> desc(o.id) }).toHql().hql())
```

`status` (`short`) and `price` (`double`) can never be NULL, so the HQL has no `nulls` part.

**`reversed()`: reverse all keys.** `spec.reversed()` turns every key around. The NULL position is reversed too: `asc` (NULL first) becomes `desc` (NULL last). So the whole row order is turned around exactly.
This is the same as `Direction.reversed()` (see [4.3](#43-direction)). `spec.reversed().reversed()` gives back the original order.

<!-- region: ManualKotlinSortTest.kt#sort-spec-reversed -->
```kotlin
val spec = SortSpec.of(Order::class.java) { o -> asc(o.description).asc(o.id) }
// every key is reversed, the NULL position too: asc nulls first -> desc nulls last
val q = qb.from(Order::class.java).orderBy(spec.reversed())
assertEquals("select e0 from Order e0 order by e0.description desc nulls last, e0.id desc", q.toHql().hql())
```

The result rows of `reversed()` are in exactly the reverse of the original order.

**SQL by database**

<!-- sql: sort-spec-reversed -->
```sql
-- H2, MariaDB
select …
from orders o1_0
order by o1_0.description desc,o1_0.id desc

-- Oracle
select …
from orders o1_0
order by o1_0.description desc nulls last,o1_0.id desc
```

H2 and MariaDB already put NULL last for `desc`, so they write no extra syntax. Oracle adds `nulls last`.

**`SortSpec.ofPath`: a sort made from a property name.** `SortSpec.ofPath(Order::class.java, "customer.name", Direction.DESC)` makes a spec from a property path. Use it for sort keys that come as strings from outside the code. The Spring module turns a Spring `Sort` into a spec in this way (see [12.5](12-spring-data-jpa.md#125-spring-sort-and-pageable)).
Each part of the path must be a persistent attribute, and the last part must be a basic value. Other paths are an error.
The sort policy decides which names are allowed (see [Sort policy](#sort-policy) below). If there is no policy and no `@Sortable` field, any persistent attribute is accepted, and lambdaQL logs `LQL-5003` (the default level is `INFO`).
<!-- region: ManualKotlinSortTest.kt#sort-spec-path -->
```kotlin
// for sorts chosen outside the code (the Spring module uses it); only real properties are accepted
val byName = SortSpec.ofPath(Order::class.java, "customer.name", Direction.DESC)
    .then(SortSpec.ofPath(Order::class.java, "id", Direction.ASC))
val q = qb.from(Order::class.java).orderBy(byName)
assertEquals("select e0 from Order e0 left join e0.customer j1 order by j1.name desc nulls last, e0.id asc", q.toHql().hql())
assertThrows(IllegalArgumentException::class.java) {
    qb.from(Order::class.java).orderBy(SortSpec.ofPath(Order::class.java, "price; drop table x", Direction.ASC))
}
```

<a id="sort-policy"></a>
### Sort policy: allowed external names

**Implemented**.

A sort name that comes from outside the code (a request parameter, a Spring `Sort` or `Pageable`) can point to any persistent attribute.
A **sort policy** decides which names are allowed.

- `SortRules<T>` is the list of allowed names for one entity. Each column is given as a lambda.
- `SortPolicy` holds the rules for each entity and the mode (`SortMode`). You register one policy per `EntityManagerFactory`, that is, per persistence unit. Every `QueryBuilder` and repository that uses that factory uses this policy.
- You can give rules without code with `@Sortable` on entity fields (see [`@Sortable` rules](#rules-from-sortable) below).

The rules of an entity come from **one place** only, in this order: the `SortRules` of the policy (in code or a Spring `SortPolicy` bean), the Spring Boot properties `lambdaql.sort.*` (see [12.5](12-spring-data-jpa.md#sort-policy-with-spring)), and the `@Sortable` fields of the entity.
Rules from two places are not merged.

**What is checked.** Only sorts made from strings are checked: `SortSpec.ofPath`, and Spring `Sort` or `Pageable` (the Spring module uses `ofPath`, see [12.5](12-spring-data-jpa.md#sort-policy-with-spring)).
Sort lambdas (`orderBy { o -> ... }`, `SortSpec.of`) are developer code, so they are not checked.

| Rule | Allowed external name |
|---|---|
| `allow { it.price }` | `price` (the path of the column) |
| `allow { it.customer?.name }` | `customer.name`. A path through an association is allowed only when you name it like this |
| `alias("newest") { it.updateAt }` | `newest`. The sort key is `updateAt`. The path `updateAt` itself is **not** an external name, so the property name is not shown to the client |

A rule must be a column of the entity.
Other keys (for example, `allow { it.price * 2 }`) are rejected with `LQL-4041` when the rule is first used.

| `SortMode` | What happens to a name that the rules do not allow |
|---|---|
| `OFF` | The sort runs. No check |
| `INFO` | The sort runs, and `LQL-5003` is logged at `INFO` level once per entity and name. **This is the default:** if no policy is registered, `INFO` is used, and the rules come only from `@Sortable` |
| `WARN` | Same as `INFO`, but the log level is `WARN` |
| `STRICT` | Rejected with `SortNotAllowedException` (`LQL-4040`). For an entity with no rules and no `@Sortable` field, every name is rejected |

So before you register a policy, `LQL-5003` is logged once per entity for each external sort name that `@Sortable` does not allow.
To reject unknown names, register a policy with `STRICT`. To turn off the check, register one with `OFF`.

| Method | What it does |
|---|---|
| `SortPolicy.of(mode, rules...)` | Makes a policy from a mode and rules per entity |
| `SortPolicy.register(emf, policy)` | Registers the policy of a persistence unit. Replaces the earlier policy |
| `SortPolicy.unregister(emf)` | Removes the policy. Goes back to the default (`INFO`, only `@Sortable` rules) |
| `SortPolicy.find(emf)` | Returns the registered policy (`Optional`) |
| `SortPolicy.effective(emf)` | Returns the policy in effect. The default if no policy is registered |

In Spring Boot, define a `SortPolicy` bean instead of calling `register` (see [12.5](12-spring-data-jpa.md#sort-policy-with-spring)).

A registered policy keeps applying to all queries of that persistence unit. When you no longer need the policy, remove it with `SortPolicy.unregister(emf)`.

<!-- region: ManualKotlinSortTest.kt#policy -->
```kotlin
val orderRules = SortRules.of(Order::class.java)
    .allow { it.price }                      // the name "price"
    .allow { it.customer?.name }             // an association path: allowed only when listed
    .alias("newest") { it.updateAt }         // the outside name "newest" sorts by updateAt
SortPolicy.register(emf, SortPolicy.of(SortMode.STRICT, orderRules))

val newest = qb.from(Order::class.java).orderBy(SortSpec.ofPath(Order::class.java, "newest", Direction.DESC))
assertEquals("select e0 from Order e0 order by e0.updateAt desc nulls last", newest.toHql().hql())
// not in the rules, and the path behind an alias: SortNotAllowedException when orderBy is called
for (name in listOf("description", "updateAt")) {
    val e = assertThrows(SortNotAllowedException::class.java) {
        qb.from(Order::class.java).orderBy(SortSpec.ofPath(Order::class.java, name, Direction.ASC))
    }
    assertEquals("LQL-4040", e.code())
}
// a sort lambda is your code: never checked
assertEquals("select e0 from Order e0 order by e0.description asc nulls first",
    qb.from(Order::class.java).orderBy { o -> asc(o.description) }.toHql().hql())
```

<a id="rules-from-sortable"></a>
**`@Sortable` rules.** `@org.lambdaql.Sortable` on a field or getter of the entity also makes a rule.

| Annotation | Allowed external name |
|---|---|
| `@Sortable` on the field `name` | `name` |
| `@Sortable("level")` on the field `grade` | `level`. The sort key is `grade` (alias) |
| `@Sortable(path = "name")` on the association field `customer` | `customer.name` |

- lambdaQL reads only the annotations of the entity given in `from(...)`. Annotations of other entities do not allow association paths, so give `path` on the association field.
- The annotations are used only when the policy has no rules for that entity (code, Spring bean, Spring Boot properties).
- They also work in the default `INFO` mode with no registered policy.

The example entity `Customer` (a Java class) has `@Sortable` on `name` and `@Sortable("level")` on `grade`. The example runs with a registered `STRICT` policy that has rules only for `Order`.

<!-- region: ManualKotlinSortTest.kt#annotation -->
```kotlin
// Customer has no rules in the policy, so its @Sortable fields are the rules:
//   @Sortable private String name;   @Sortable("level") private Integer grade;
val byLevel = qb.from(Customer::class.java).orderBy(SortSpec.ofPath(Customer::class.java, "level", Direction.DESC))
assertEquals("select e0 from Customer e0 order by e0.grade desc nulls last", byLevel.toHql().hql())
assertThrows(SortNotAllowedException::class.java) {
    qb.from(Customer::class.java).orderBy(SortSpec.ofPath(Customer::class.java, "id", Direction.ASC))
}
```

<a id="rules-as-strings"></a>
**Rules as strings.** `allowPath(String...)` and `aliasPath(name, path)` take property paths as strings. They are for configuration files, and the Spring Boot properties in [12.5](12-spring-data-jpa.md#sort-policy-with-spring) use this form.
The paths are checked against the entity when the rule is first used. In code, use the lambda forms `allow` and `alias`.

<!-- region: ManualKotlinSortTest.kt#string-paths -->
```kotlin
// the string forms used by configuration files (lambdaql.sort.allow / lambdaql.sort.alias)
SortPolicy.register(emf, SortPolicy.of(SortMode.STRICT,
    SortRules.of(Order::class.java).allowPath("price", "customer.name").aliasPath("newest", "updateAt")))
assertEquals("select e0 from Order e0 order by e0.updateAt desc nulls last",
    qb.from(Order::class.java).orderBy(SortSpec.ofPath(Order::class.java, "newest", Direction.DESC)).toHql().hql())
// in STRICT mode a name that is not in the rules is rejected, even when it is not a property
assertThrows(SortNotAllowedException::class.java) {
    qb.from(Order::class.java).orderBy(SortSpec.ofPath(Order::class.java, "noSuchName", Direction.ASC))
}
```

- The check happens when the spec is passed to `orderBy`, before the path is read.
  So in `STRICT` mode, a name that is not in the rules throws `SortNotAllowedException` even if it is not an attribute.
- Rules belong to one entity class. If you pass `SortRules` for the same class twice, the last one is used.
- `SortNotAllowedException` is a `LambdaQLException` (see [11. Errors and languages](../11-errors-and-languages.md#exceptions)).
  lambdaQL has no web dependency. For an example HTTP 400 response, see [12.5](12-spring-data-jpa.md#sort-policy-with-spring).
- If you take a name and a direction from a request, read the direction with `Direction.parse` (see [4.3](#43-direction)) and the name with `SortSpec.ofPath`.

Example code: [`ManualKotlinSortTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSortTest.kt) (`sort-spec`, `sort-spec-then`, `sort-spec-reversed`, `sort-spec-path`, `policy`, `annotation`, `string-paths`).

<a id="411-sorting-inside-subqueries"></a>
## 4.11 Sorting inside subqueries

A sort inside a subquery is kept only when it is used with `limit` (top-1 or top-N).
Without `limit`, it does not change the result, so lambdaQL removes the sort and logs a warning.
See [5. Subqueries](05-subquery.md#59-sorting-inside-a-subquery).

<a id="412-rules-summary-s1-to-s14"></a>
## 4.12 Rules summary (S1 to S14)

| # | Rule | Status |
|---|---|---|
| S1 | A sort is one lambda: one key (`{ it.price }`, with an optional `Direction`) or keys made with the functions of `By` (`{ o -> desc(...) }`). For a join, `{ o, c -> ... }` | Implemented. The join form `{ o, c -> }` is also implemented |
| S2 | A key must use a value of the entity (Kotlin nullable keys are allowed). The entity itself cannot be a key | Implemented |
| S3 | The direction is set with `asc` / `desc` or `sort(key, Direction)`. `asc(key)`/`desc(key)` (`ASC`/`DESC`) use the NULL policy, and `asc(key, nulls)`, `desc(key, nulls)` and `*_NULLS_*` are used as written | Implemented |
| S4 | The body is only one key or a straight chain of `By` functions. No `if` or `when` that picks a sort function, and no loops. An `if` inside a key value becomes `case when` | Implemented |
| S5 | `orderBy` once per query. No `where` after `orderBy`. No `orderBy` after `limit` | Implemented |
| S6 | Reuse with `SortSpec` (`of`, `ofPath`, `then`, `reversed`, `orderBy(spec)`). External strings are checked by the sort policy of the persistence unit (`SortRules`, `SortPolicy`, `SortMode`, see [4.10](#sort-policy)) | Implemented |
| S7 | Keys: columns, to-one paths, `+ - *`, widening conversions. An `if` inside a key value becomes `case when`. Kotlin `?.` is read as a path | Implemented |
| S8 | An association path used only in `orderBy` uses a `left join`, so rows without the association are kept. A required association (`optional = false`) loses no rows, so it is an inner join (A4). If the same path is used in `where`, the sort uses the join of `where` | Implemented |
| S9 | With `orderBy` and paging together, the root `@Id asc` is added when the keys have no id. Without `orderBy`, no `order by` is added. The ids of explicitly joined entities are added too. If the keys have a unique column that can never be NULL (`unique = true` and `nullable = false`, `@NaturalId`), it is not added (A5) | Implemented |
| S10 | The analysis result is cached per lambda. A captured `Direction` is read on each call | Implemented |
| S11 | A Spring `Sort.Order` maps 1:1 to a `Direction` (Spring module, see [12.5](12-spring-data-jpa.md#125-spring-sort-and-pageable)). Property names go through the check of the sort policy, and aliases are passed as they are | Implemented (`ignoreCase` is not supported). Also works with the sort policy |
| S12 | Join queries use the same shape: from `{ o, c -> }` up to 10 entities | Implemented (see [6.8](06-select-group.md#68-explicit-joins)) |
| S14 | Subquery sort: kept with `limit`, removed with a warning without it (see chapter 5) | Implemented |

---

[Kotlin track](README.md) | Previous: [3. Conditions (`where`)](03-where.md) | Next: [5. Subqueries](05-subquery.md) | [Java version of this page](../java/04-order-paging.md)
