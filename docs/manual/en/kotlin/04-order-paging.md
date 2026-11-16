# 4. Sorting and paging (Kotlin)

> [!NOTE]
> **Status**
> - **Core implemented** and tested on H2, MariaDB 10.11 and Oracle 23.26 (Hibernate 6.6.5): `orderBy { o, by -> ... }` with `by.asc`, `by.desc` and `by.sort`, the one-key forms `orderBy { it.key }` and `orderBy({ it.key }, Direction)`, `Direction.parse` / `reversed`, `limit` / `offset`, `first()` after `orderBy`, and the reusable sort `SortSpec` (`of`, `ofPath`, `then`, `reversed`, `orderBy(spec)`). Spring `Sort` and `Pageable` work in the Spring Data JPA module ([12.5](12-spring-data-jpa.md#125-spring-sort-and-pageable)). The tests use **Java** lambdas. **Kotlin lambdas are not tested yet.**
> - **Not implemented yet:** Kotlin property references (`orderBy(Order::price)`), `SortWhitelist`, the join forms `{ o, c, by -> }` (also for `SortSpec`) and sorting inside subqueries.
> - The Kotlin syntax compiles (checked with prototypes, kotlinc 2.3.21). Nullable keys, `?.` keys and property references compile; an entity key does not.
> - The HQL in this chapter follows the rules of the library, but it is not tested with Kotlin lambdas.

[Kotlin track](README.md) | [Manual home](../README.md) | Previous: [3. Conditions](03-where.md) | Next: [5. Subqueries](05-subquery.md) | [Java version of this page](../java/04-order-paging.md)

Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md)

---

## 4.1 One lambda for the whole sort

You write the sort as **one lambda**. The lambda gets the entity and a sort builder `by` as the last argument.
You add keys and directions with `by.asc(...)` and `by.desc(...)`.

```kotlin
qb.from(Order::class.java)
    .where { it.active }
    .orderBy { o, by -> by.desc(o.price).asc(o.customer?.name).asc(o.id) }
    .limit(20).offset(40)
    .toList()
// HQL: select e0 from Order e0 left join e0.customer j1
//      where e0.active = true
//      order by e0.price desc nulls last, j1.name asc nulls first, e0.id asc
```

**About the SQL blocks.** `…` is the shortened select list (all entity columns), and `?` is a bind parameter. The lines come from [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java). It runs each query on H2 2.3, MariaDB 10.11 and Oracle 23.26 with Hibernate 6.6.5, and it fails if Hibernate's SQL changes. The test uses Java lambdas. A Kotlin lambda with the same meaning gives the same HQL, so it gets the same SQL.

**SQL by database (Hibernate's SQL for this HQL; Kotlin gives the same HQL)** — the manual's `Order` has a nullable `price`, but the test `Order` does not, so `SqlByDatabaseTest` gives this HQL to Hibernate directly. From `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `sort-overview`:

```sql
-- H2
-- (this HQL is given to Hibernate directly)
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where o1_0.active=true
order by o1_0.price desc,c1_0.name,o1_0.id
offset ? rows
fetch first ? rows only

-- MariaDB
-- (this HQL is given to Hibernate directly)
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where o1_0.active=1
order by o1_0.price desc,c1_0.name,o1_0.id
limit ?,?

-- Oracle
-- (this HQL is given to Hibernate directly)
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where o1_0.active=1
order by o1_0.price desc nulls last,c1_0.name asc nulls first,o1_0.id
offset ? rows
fetch first ? rows only
```

- The name `by` is only a habit in this manual. You can use any name.
- No import is needed for `by`. It is a lambda argument.
- The key and its direction are written together. They can never get out of order.
- lambdaQL reads the lambda. It never runs it. The `by` methods only describe the order.

There are no Kotlin tests yet. The same sorts written in Java are tested in [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java) (H2, MariaDB and Oracle). See [the Java version of this page](../java/04-order-paging.md) for the tested examples.

## 4.2 The short forms (one key)

If you have only one key, you can leave out `by`:

| Kotlin | HQL |
|---|---|
| `orderBy { it.price }` | `order by e0.price asc nulls first` |
| `orderBy({ it.updateAt }, Direction.DESC)` | `order by e0.updateAt desc nulls last` |
| `orderBy(Order::price)` | `order by e0.price asc nulls first` (not implemented yet) |

Without a `Direction`, the short form sorts ascending (`ASC`).
lambdaQL tells the forms apart by the number of lambda arguments (one or two). Kotlin picks the right overload with no ambiguity.

With a `Direction`, the lambda is the first argument, so it goes inside the parentheses: `orderBy({ it.updateAt }, Direction.DESC)`.

Kotlin property references (`Order::price`) are not implemented yet. Use a lambda (`{ it.price }`) for now.

## 4.3 `Direction`

`Direction` has six values:

| Value | Meaning | NULL position |
|---|---|---|
| `ASC` | ascending | NULLs **first** (default policy) |
| `DESC` | descending | NULLs **last** (default policy) |
| `ASC_NULLS_FIRST` | ascending | NULLs first |
| `ASC_NULLS_LAST` | ascending | NULLs last |
| `DESC_NULLS_FIRST` | descending | NULLs first |
| `DESC_NULLS_LAST` | descending | NULLs last |

`Direction` also has two helpers:

- `Direction.parse(text)` reads a direction from text, for example a request parameter. Case does not matter, and `-`, `_` and a space are the same: `Direction.parse("desc-nulls-last")` is `DESC_NULLS_LAST`. Unknown text throws `IllegalArgumentException`.
- `reversed()` gives the opposite direction. The NULL position flips too, so the whole order is reversed: `Direction.ASC.reversed()` is `DESC`, and `Direction.ASC_NULLS_LAST.reversed()` is `DESC_NULLS_FIRST`.

**Choose the NULL position** with a second `Boolean` argument: `by.asc(key, nullsFirst)` and `by.desc(key, nullsFirst)` (decision D35).
`true` means "NULLs first", `false` means "NULLs last".
Without the second argument, `asc(key)` and `desc(key)` use the default NULL policy (decision D16, see [4.4](#44-null-ordering-is-the-same-on-every-database)).

| You write | Same as |
|---|---|
| `by.asc(key)` | `ASC` (NULLs first, default policy) |
| `by.desc(key)` | `DESC` (NULLs last, default policy) |
| `by.asc(key, true)` | `ASC_NULLS_FIRST` |
| `by.asc(key, false)` | `ASC_NULLS_LAST` |
| `by.desc(key, true)` | `DESC_NULLS_FIRST` |
| `by.desc(key, false)` | `DESC_NULLS_LAST` |

**When the direction comes from a variable**, use `by.sort(key, direction)` with a captured `Direction`:

```kotlin
.orderBy { o, by -> by.desc(o.description, false).asc(o.id) }   // nulls last
// order by e0.description desc nulls last, e0.id asc

val dir = if (request.newestFirst) Direction.DESC else Direction.ASC
.orderBy { o, by -> by.sort(o.updateAt, dir).asc(o.id) }     // dir is read on every call

.orderBy({ it.customer?.name }, Direction.DESC_NULLS_LAST)  // short form with a NULL position
```

The lambda is analyzed only once. `dir` is read each time the query runs (rule S10). A `null` direction throws when the query runs.

> [!TIP]
> A bare `false` does not say much. Kotlin cannot use named arguments (`nullsFirst = false`) on Java methods, and the `by` builder is a Java class.
> So Kotlin also writes `by.desc(x, false)`, the same as Java. A short comment like `// nulls last` is a good habit.

Choose `dir` **outside** the lambda. An `if` inside the sort lambda is rejected (see [4.6](#46-rules-for-the-lambda-body)).

## 4.4 NULL ordering is the same on every database

Databases do not agree on where NULLs go:

| | H2 | MariaDB | Oracle |
|---|---|---|---|
| `ASC` default | NULLs first | NULLs first | NULLs **last** |
| `DESC` default | NULLs last | NULLs last | NULLs **first** |
| `NULLS FIRST/LAST` in SQL | yes | **no (syntax error)** | yes |

lambdaQL fixes this with one policy: **NULL is the smallest value** (decision D16).

- `ASC` = `ASC_NULLS_FIRST`
- `DESC` = `DESC_NULLS_LAST`

This is the same as the Kotlin comparator `nullsFirst(naturalOrder())`.
With this policy, all three databases return the same order (for numbers and dates).

**How lambdaQL writes it.** For every key that can be NULL, lambdaQL writes the NULL position into the HQL, for example `e0.description asc nulls first`.
A key that can never be NULL (a JVM primitive like `active: Boolean` or `qty: Int`, or the `@Id`) gets no `nulls` part.
lambdaQL looks at the JPA metadata, not at the Kotlin type. So `id: Long?` with `@Id` gets no `nulls` part, and `price: Double?` gets one.
A column behind an association can always be NULL (an order may have no customer), so it always gets one.

Then Hibernate writes the SQL for each database. It drops the clause when it is the database default.
MariaDB has no `NULLS` syntax, so there Hibernate writes a `CASE` expression for a position that is not the default.

What SQL you get:

| You write | H2 | MariaDB | Oracle |
|---|---|---|---|
| `asc(key)`, `desc(key)` (`ASC`, `DESC`) | no extra clause (already the default) | no extra clause | `nulls first` / `nulls last` added |
| A NULL position (`asc(key, b)`, `desc(key, b)`, `*_NULLS_*`) that is the DB default | no extra clause | no extra clause | no extra clause |
| A NULL position that is **not** the DB default | `nulls first/last` | Hibernate writes `case when x is null ...` | `nulls first/last` |
| Any direction on a key that can never be NULL | no extra clause | no extra clause | no extra clause |

> [!TIP]
> On MariaDB, `by.asc(key, false)` (`ASC_NULLS_LAST`) and `by.desc(key, true)` (`DESC_NULLS_FIRST`) become a `CASE` expression. The database cannot use an index for that sort. Use `asc(key)` / `desc(key)` when you can.

## 4.5 What a sort key can be

| Key | Allowed | Note |
|---|---|---|
| Column: `o.price` | yes | nullable keys (`Double?`, `String?`) are fine |
| To-one path: `o.customer?.name`, `o.customer!!.name` | yes | `left join`, see below |
| Arithmetic: `o.price!! * 2`, `+`, `-` | yes | |
| Widening conversion: `o.status!!.toLong()` | yes | |
| Elvis on a non-boolean: `o.description ?: ""` | yes | `coalesce(e0.description, '')`. The NULL policy does not apply to it |
| An entity: `by.desc(o.customer)` | **compile error** | an entity is not `Comparable` |
| A value without the entity: `by.asc(1)`, a captured value | **rejected** | see [4.6](#46-rules-for-the-lambda-body) |
| Function on the column: `lower(name)` | planned | |
| Boolean elvis: `{ it.flag ?: false }` | **rejected** (KC21) | use `{ it.flag }` and a NULL position |
| Value `if` / `when` | **rejected** (KC6) | |

Keys must be `Comparable`. Kotlin nullable keys are fine. Kotlin refuses an entity key at compile time.

**Association paths use `left join`.** A plain path in HQL `order by o.customer.name` becomes an inner join on all three databases. Orders without a customer would disappear.
lambdaQL adds a `left join` instead. Orders without a customer stay in the result. Their `j1.name` is NULL, so they come first with `asc`.

```kotlin
.orderBy { o, by -> by.asc(o.customer?.name).asc(o.id) }
// HQL: select e0 from Order e0 left join e0.customer j1 order by j1.name asc nulls first, e0.id asc
```

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `sort-path`:

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

You can see the NULL policy in the SQL. H2 and MariaDB drop the clause when it is their default. Oracle writes `asc nulls first` / `desc nulls last`, because its own default is the opposite. MariaDB has no `NULLS` syntax, so for a position that is not its default it sorts by `case when … is null then … end` first.

The alias is shared with `where`. If `where` already joined the same path, the sort uses that join.
In most cases it is an inner join: the `where` condition already removed the orders without a customer (decision D36, see [3.10](03-where.md#310-association-paths)).

## 4.6 Rules for the lambda body

The body must be **a straight chain of `by` calls**:

```kotlin
// OK
.orderBy { o, by -> by.desc(o.price).asc(o.id) }

// Rejected: conditional key
.orderBy { o, by -> if (flag) by.desc(o.price) else by.asc(o.id) }

// Rejected: a key without the entity
.orderBy { o, by -> by.asc(1) }

// Rejected: loops, local val/var, more than one statement
```

lambdaQL checks this when you call `orderBy` and throws `LambdaAnalysisException`.
Every key must use the entity. A key with only a constant or a captured value is rejected.

If the direction must change at run time, pass it as a variable: `by.sort(key, dir)`. If the key itself must change, build the query in two branches of normal Kotlin code.

## 4.7 Order of calls

The builder types control the order. Wrong order does not compile:

| Call | Allowed |
|---|---|
| `from(...).where { }.orderBy { }.limit(...).offset(...)` | yes |
| `from(...).where { }.limit(...).offset(...)` (no `orderBy`) | yes. The rows are sorted by the id (see [4.8](#48-paging)) |
| `orderBy { }` two times | no (compile error) |
| `where { }` after `orderBy { }` | no (compile error) |
| `orderBy { }` after `limit(...)` | no (compile error) |

After `orderBy`, `limit` or `offset`, the query has the type `OrderedSelectQuery`. It has only `limit`, `offset`, `toHql()`, `toList()`, `toSet()` and `first()`. `one()`, `count()` and `exists()` are only before `orderBy`, `limit` and `offset` (see [Getting the result](02-getting-started.md#getting-the-result)).

## 4.8 Paging

```kotlin
qb.from(Order::class.java)
    .where { it.status == 1 }
    .orderBy { o, by -> by.desc(o.price) }
    .limit(20)      // page size
    .offset(40)     // skip 40 rows (page 3)
    .toList()
// HQL: select e0 from Order e0 where e0.status = 1 order by e0.price desc nulls last, e0.id asc
// limit and offset are passed to Hibernate (setMaxResults / setFirstResult)
```

**SQL by database (Hibernate's SQL for this HQL; Kotlin gives the same HQL)** — the manual's `Order` has a nullable `price`, but the test `Order` does not, so `SqlByDatabaseTest` gives this HQL to Hibernate directly. From `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `page-design`:

```sql
-- H2
-- (this HQL is given to Hibernate directly)
select …
from orders o1_0
where o1_0.status=1
order by o1_0.price desc,o1_0.id
offset ? rows
fetch first ? rows only

-- MariaDB
-- (this HQL is given to Hibernate directly)
select …
from orders o1_0
where o1_0.status=1
order by o1_0.price desc,o1_0.id
limit ?,?

-- Oracle
-- (this HQL is given to Hibernate directly)
select …
from orders o1_0
where o1_0.status=1
order by o1_0.price desc nulls last,o1_0.id
offset ? rows
fetch first ? rows only
```

`limit` and `offset` take 0 or more. A negative value throws `IllegalArgumentException`.

lambdaQL does not write `LIMIT` into the HQL. Hibernate writes the right SQL for each database:

| Database | SQL |
|---|---|
| H2 | `offset ? rows fetch first ? rows only` |
| MariaDB | `limit ?, ?` |
| Oracle | `offset ? rows fetch first ? rows only` |

**Paging without `orderBy`.** You can call `limit` and `offset` without `orderBy`. Then lambdaQL sorts the rows by the id, so the pages are stable.

```kotlin
qb.from(Order::class.java)
    .where { it.active }
    .limit(2)
    .toList()
// HQL: select e0 from Order e0 where e0.active = true order by e0.id asc
```

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `page-only`:

```sql
-- H2
select …
from orders o1_0
where o1_0.active=true
order by o1_0.id
fetch first ? rows only

-- MariaDB
select …
from orders o1_0
where o1_0.active=1
order by o1_0.id
limit ?

-- Oracle
select …
from orders o1_0
where o1_0.active=1
order by o1_0.id
fetch first ? rows only
```

Paging SQL is different on each database: H2 and Oracle get `fetch first ? rows only`, and MariaDB gets `limit ?`. With an offset, H2 and Oracle add `offset ? rows`, and MariaDB writes `limit ?,?`.

**The first row and the total count.**

- `orderBy { ... }.first()` returns the first row in that order, as an `Optional`. It reads only one row. An `offset` is kept: `orderBy { ... }.offset(1).first()` returns the second row.
- `count()` is only on the where stage (before `orderBy`, `limit` and `offset`), so it always counts all matching rows. For a paged screen, keep the where-stage query in a value. Call `count()` on it for the total, then call `orderBy { ... }.limit(...).toList()` on it for the page.

See [Getting the result](02-getting-started.md#getting-the-result).

### Tie-breaker (automatic `id`)

Look at the HQL above: lambdaQL added `e0.id asc` at the end. Why?

- Many rows can have the same `price`. The database may return those rows in **any order**. Oracle really does this (measured).
- Then page 2 and page 3 can show the same row twice, or skip a row.

Rule: **when you use paging and the sort keys do not contain the id, lambdaQL adds the root `@Id asc` as the last key.**

- It is added only once. If your keys already contain the id (for example `by.desc(o.id)`), it is not added.
- Without `limit` and `offset`, nothing is added.
- **Not implemented yet** (joins are not implemented yet): with a to-many join, the id of the joined entity is also added. A collection fetch join together with `limit`/`offset` is rejected.

## 4.9 Strings and special values

- String order follows the database collation. MariaDB `_ci` ignores case. Oracle uses `NLS_SORT`, which follows the JVM locale (for example `de_DE` gives German order). For Oracle we recommend `NLS_SORT=BINARY`.
- Oracle stores `''` as NULL, so empty strings sort as NULLs.
- `NaN` is ordered differently by each database.

## 4.10 Reusable and external sorts

**`SortSpec`: a sort you can keep.** A `SortSpec` is a sort that you keep in a value and use in many queries.
Make it with `SortSpec.of(Order::class.java) { o, by -> ... }`. The lambda is the same as in `orderBy { o, by -> ... }`, so the same rules apply (see [4.6](#46-rules-for-the-lambda-body)).
Then pass it to `orderBy(spec)`.

```kotlin
// for example at the top level or in a companion object
val NEWEST: SortSpec<Order> = SortSpec.of(Order::class.java) { o, by -> by.desc(o.updateAt).asc(o.id) }

qb.from(Order::class.java).where { it.active }.orderBy(NEWEST).limit(20).toList()
// HQL: select e0 from Order e0 where e0.active = true order by e0.updateAt desc nulls last, e0.id asc
```

- A `SortSpec` cannot be changed. `then()` and `reversed()` return a new spec. So one spec is safe to share between many queries and threads, for example as a top-level `val`.
- lambdaQL checks the lambda when the spec is used in `orderBy`. Each lambda is analyzed only once.
- Captured values are the values at the moment the lambda was made. A spec in a top-level `val` keeps them forever. If the direction must change at run time, use `orderBy { o, by -> by.sort(key, dir) }` (see [4.3](#43-direction)).
- After `orderBy(spec)` you can page as usual: `limit`, `offset`, `toList()`, `first()`.

There are no Kotlin tests for `SortSpec` yet. The same specs written in Java are tested in [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java) (H2, MariaDB and Oracle). See [the Java version of this page](../java/04-order-paging.md#410-reusable-and-external-sorts) for the tested examples.

```kotlin
val byDescription = SortSpec.of(Order::class.java) { o, by -> by.desc(o.description).asc(o.id) }
qb.from(Order::class.java).where { it.active }.orderBy(byDescription).toList()
// HQL: select e0 from Order e0 where e0.active = true order by e0.description desc nulls last, e0.id asc
```

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `sort-spec`:

```sql
-- H2
select …
from orders o1_0
where o1_0.active=true
order by o1_0.description desc,o1_0.id

-- MariaDB
select …
from orders o1_0
where o1_0.active=1
order by o1_0.description desc,o1_0.id

-- Oracle
select …
from orders o1_0
where o1_0.active=1
order by o1_0.description desc nulls last,o1_0.id
```

The SQL is the same as for the same sort written in `orderBy { o, by -> ... }`. Oracle adds `nulls last`, because its own default for `desc` is the opposite (see [4.4](#44-null-ordering-is-the-same-on-every-database)).

**`then()`: more keys after the existing ones.** `a.then(b)` sorts by the keys of `a` first. For rows that are equal, it then sorts by the keys of `b`.
`then()` takes another `SortSpec` or a sort lambda.

```kotlin
val byStatus = SortSpec.of(Order::class.java) { o, by -> by.asc(o.status) }
val byPrice = SortSpec.of(Order::class.java) { o, by -> by.desc(o.price) }
qb.from(Order::class.java).orderBy(byStatus.then(byPrice)).toList()
// HQL: select e0 from Order e0 order by e0.status asc nulls first, e0.price desc nulls last
qb.from(Order::class.java).orderBy(byStatus.then { o, by -> by.desc(o.id) }).toList()
// HQL: select e0 from Order e0 order by e0.status asc nulls first, e0.id desc
```

Here `status` and `price` can be NULL (`Int?`, `Double?`), so the HQL has a `nulls` part. In the Java test entity they can never be NULL, so the tested HQL and SQL (example `sort-spec-then`) have no `nulls` part. See [the Java version of this page](../java/04-order-paging.md#410-reusable-and-external-sorts).

**`reversed()`: every key the other way.** `spec.reversed()` flips every key. The NULL position flips too: `asc` (NULLs first) becomes `desc` (NULLs last). So the whole row order is exactly backwards.
This is the same as `Direction.reversed()` (see [4.3](#43-direction)). `spec.reversed().reversed()` gives the first order again.

```kotlin
val spec = SortSpec.of(Order::class.java) { o, by -> by.asc(o.description).asc(o.id) }
qb.from(Order::class.java).orderBy(spec.reversed()).toList()
// HQL: select e0 from Order e0 order by e0.description desc nulls last, e0.id desc
```

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `sort-spec-reversed`:

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

H2 and MariaDB already put NULLs last for `desc`, so they write no extra clause. Oracle adds `nulls last`.

**`SortSpec.ofPath`: a sort from a property name.** `SortSpec.ofPath(Order::class.java, "customer.name", Direction.DESC)` makes a spec from a property path, for sort keys that come as text from outside the code (the Spring module uses it for Spring `Sort`, see [12.5](12-spring-data-jpa.md#125-spring-sort-and-pageable)).
Every part of the path must be a persistent property, and the last part a basic value. Anything else is an error, but any persistent property is accepted, so check text from users against your own list first.
There is no lambda here, so Kotlin works the same as Java. The Java test is in [the Java version of this page](../java/04-order-paging.md#410-reusable-and-external-sorts) (region `sort-spec-path`).

```kotlin
val byName = SortSpec.ofPath(Order::class.java, "customer.name", Direction.DESC)
    .then(SortSpec.ofPath(Order::class.java, "id", Direction.ASC))
qb.from(Order::class.java).orderBy(byName).toList()
// HQL: select e0 from Order e0 left join e0.customer j1 order by j1.name desc nulls last, e0.id asc
```

**Not implemented yet:**

- `SortWhitelist<T>` to turn an outside string (a request parameter like `"price,desc_nulls_last"`) into a sort. It accepts only the names on its list. Until then, `Direction.parse` and `by.sort(key, dir)` give you a run-time direction (see [4.3](#43-direction)). Choose the key in normal Kotlin code, or check the text against your own list and use `SortSpec.ofPath`.
- **Planned:** joins with `{ o, c, by -> ... }` (also in `SortSpec`), keyset paging.

## 4.11 Sorting inside subqueries

**Not implemented yet** (subqueries are not implemented yet).

A sort inside a subquery is only kept when it is used with `limit` (top-1 or top-N).
Without `limit`, it does not change the result, so lambdaQL removes it and logs a warning.
See [5. Subqueries](05-subquery.md#59-sorting-inside-a-subquery).

## 4.12 Rules summary (S1 to S14)

| # | Rule | Status |
|---|---|---|
| S1 | A sort is one lambda: `{ o, by -> by... }`. For one key: `{ it.key }` with an optional `Direction`. For joins: `{ o, c, by -> by... }` (`by` is always last) | Implemented, except the join forms |
| S2 | A key is `Comparable` (Kotlin nullable keys are allowed). An entity cannot be a key (compile error). A key must use the entity | Implemented |
| S3 | Direction comes from `asc` / `desc` or `sort(key, Direction)`. `asc(key)`/`desc(key)` (`ASC`/`DESC`) use the NULL policy; `asc(key, nullsFirst)`, `desc(key, nullsFirst)` and `*_NULLS_*` are used as written | Implemented |
| S4 | The body is only a straight chain of `by` calls. No `if`, `when` or loop | Implemented |
| S5 | `orderBy` once per query. No `where` after `orderBy`. No `orderBy` after `limit` (compile error) | Implemented |
| S6 | Reuse with `SortSpec` (`of`, `ofPath`, `then`, `reversed`, `orderBy(spec)`). Outside strings only through `SortWhitelist` | `SortSpec` implemented (tested with Java lambdas). `SortWhitelist` and the join form are not implemented yet |
| S7 | Keys: columns, to-one paths, `+ - *`, widening. No value branches. Kotlin `?.` is read as a path | Implemented (not tested with Kotlin lambdas) |
| S8 | Association paths that are used only in `orderBy` use a `left join`, so rows without the association are kept. If `where` uses the same path, the sort uses the `where` join | Implemented |
| S9 | With paging, the root `@Id asc` is added if the keys do not contain it. Collection fetch join + paging is rejected | Implemented for the root id. The join parts are not implemented yet |
| S10 | The analysis is cached per lambda. A captured `Direction` is read on every call | Implemented |
| S11 | Spring `Sort.Order` maps 1:1 to `Direction` (Spring module, see [12.5](12-spring-data-jpa.md#125-spring-sort-and-pageable)). `SortWhitelist.parse("price,desc_nulls_last")` | Spring `Sort` implemented and tested with Java (`ignoreCase` not supported yet). `SortWhitelist` not implemented yet |
| S12 | Join queries use the same shape: `{ o, c, by -> }`, `{ o, c, i, by -> }` | Not implemented yet |
| S13 | Property references only in the one-key short form (`orderBy(Order::price)`). A reference that compiles but has the wrong meaning is rejected | Not implemented yet (Kotlin property references) |
| S14 | Subquery sorts: kept with `limit`, removed with a warning without `limit`. Database differences are handled for you (see chapter 5) | Not implemented yet |

---

[Kotlin track](README.md) | Previous: [3. Conditions](03-where.md) | Next: [5. Subqueries](05-subquery.md) | [Java version of this page](../java/04-order-paging.md)
