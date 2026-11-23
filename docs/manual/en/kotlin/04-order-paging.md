<a id="4-sorting-and-paging-kotlin"></a>
# 4. Sorting and paging (Kotlin)

[Kotlin track](README.md) | [Manual home](../README.md) | Previous: [3. Conditions (`where`)](03-where.md) | Next: [5. Subqueries](05-subquery.md) | [Java version of this page](../java/04-order-paging.md)

Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md)

---

<a id="41-one-lambda-for-the-whole-sort"></a>
## 4.1 One lambda for the whole sort

You write the sort as **one lambda**. The lambda gets the entity and a sort builder `by`. `by` is the last argument.
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

**About the SQL blocks.** `…` is the shortened select list (all entity columns), and `?` is a bind parameter. The lines come from [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java). It runs each query on H2 2.3, MariaDB 10.11 and Oracle 23.26 with Hibernate 7.4, and it fails if Hibernate's SQL changes. The test uses Java lambdas. A Kotlin lambda with the same meaning gives the same HQL, so it gets the same SQL.

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
where o1_0.active=true
order by o1_0.price desc,c1_0.name,o1_0.id
limit ?,?

-- Oracle
-- (this HQL is given to Hibernate directly)
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where o1_0.active=true
order by o1_0.price desc nulls last,c1_0.name asc nulls first,o1_0.id
offset ? rows
fetch first ? rows only
```

- The name `by` is only a habit in this manual. You can use any name.
- No import is needed for `by`. It is a lambda argument.
- The key and its direction are written together. They can never get out of order.
- lambdaQL only reads the lambda. It never runs it. The `by` methods only describe the order.

[`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt), region `sort`:

```kotlin
val q = qb.from(Order::class.java)
    .where { it.isActive }
    .orderBy { o, by -> by.desc(o.price).asc(o.customer?.name) }
    .limit(3)
assertEquals("select e0 from Order e0 left join e0.customer j1 where e0.active = true"
    + " order by e0.price desc, j1.name asc nulls first, e0.id asc", q.toHql().hql())
```

The test uses the Java test entity. `price` is a primitive `double`, so there is no `nulls` part, and the getter of `active` is `isActive()` (Kotlin `it.isActive`).
The test runs the query on H2, MariaDB and Oracle and compares the rows with the same sort done in Kotlin.
More sorts are tested in Java in [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java). See [the Java version of this page](../java/04-order-paging.md) for those examples.

<a id="42-the-short-forms-one-key"></a>
## 4.2 The short forms (one key)

If you have only one key, you can leave out `by`.

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

`Direction` has six values.

| Value | Meaning | NULL position |
|---|---|---|
| `ASC` | ascending | NULLs **first** (default policy) |
| `DESC` | descending | NULLs **last** (default policy) |
| `ASC_NULLS_FIRST` | ascending | NULLs first |
| `ASC_NULLS_LAST` | ascending | NULLs last |
| `DESC_NULLS_FIRST` | descending | NULLs first |
| `DESC_NULLS_LAST` | descending | NULLs last |

`Direction` also has two helper methods.

- `Direction.parse(text)` reads a direction from text, for example a request parameter. Case does not matter, and `-`, `_` and a space are treated the same: `Direction.parse("desc-nulls-last")` is `DESC_NULLS_LAST`. Unknown text throws `IllegalArgumentException`.
- `reversed()` returns the opposite direction. The NULL position flips too, so the whole order is reversed: `Direction.ASC.reversed()` is `DESC`, and `Direction.ASC_NULLS_LAST.reversed()` is `DESC_NULLS_FIRST`.

**To choose the NULL position**, use a second `Boolean` argument: `by.asc(key, nullsFirst)`, `by.desc(key, nullsFirst)` (decision D35).
`true` means "NULLs first", and `false` means "NULLs last".
Without the second argument, `asc(key)` and `desc(key)` use the default NULL policy (decision D16, see [4.4](#44-null-ordering-is-the-same-on-every-database)).

| You write | Same as |
|---|---|
| `by.asc(key)` | `ASC` (NULLs first, default policy) |
| `by.desc(key)` | `DESC` (NULLs last, default policy) |
| `by.asc(key, true)` | `ASC_NULLS_FIRST` |
| `by.asc(key, false)` | `ASC_NULLS_LAST` |
| `by.desc(key, true)` | `DESC_NULLS_FIRST` |
| `by.desc(key, false)` | `DESC_NULLS_LAST` |

**When the direction comes from a variable**, use `by.sort(key, direction)` with a captured `Direction`.

```kotlin
.orderBy { o, by -> by.desc(o.description, false).asc(o.id) }   // nulls last
// order by e0.description desc nulls last, e0.id asc

val dir = if (request.newestFirst) Direction.DESC else Direction.ASC
.orderBy { o, by -> by.sort(o.updateAt, dir).asc(o.id) }     // dir is read on every call

.orderBy({ it.customer?.name }, Direction.DESC_NULLS_LAST)  // short form with a NULL position
```

The lambda is analyzed only once. `dir` is read each time the query runs (rule S10). A `null` direction throws an exception when the query runs.

> [!TIP]
> A bare `false` does not show its meaning well. Kotlin cannot use named arguments (`nullsFirst = false`) on Java methods, and the `by` builder is a Java class.
> So Kotlin also writes `by.desc(x, false)`, the same as Java. A short comment such as `// nulls last` is a good habit.

Choose `dir` **outside** the lambda. An `if` inside the sort lambda is rejected (see [4.6](#46-rules-for-the-lambda-body)).

<a id="44-null-ordering-is-the-same-on-every-database"></a>
## 4.4 NULL ordering is the same on every database

Databases put NULLs in different places.

| | H2 | MariaDB | Oracle |
|---|---|---|---|
| `ASC` default | NULLs first | NULLs first | NULLs **last** |
| `DESC` default | NULLs last | NULLs last | NULLs **first** |
| `NULLS FIRST/LAST` in SQL | yes | **no (syntax error)** | yes |

lambdaQL solves this with one policy: **NULL is the smallest value** (decision D16).

- `ASC` = `ASC_NULLS_FIRST`
- `DESC` = `DESC_NULLS_LAST`

This is the same as the Kotlin comparator `nullsFirst(naturalOrder())`.
With this policy, all three databases return the same order (for numbers and dates).

**How lambdaQL writes it.** For every key that can be NULL, lambdaQL always writes the NULL position into the HQL, for example `e0.description asc nulls first`.
A key that can never be NULL (a JVM primitive such as `active: Boolean` or `qty: Int`, or the `@Id`) gets no `nulls` part.
lambdaQL looks at the JPA metadata, not at the Kotlin type. So `id: Long?` with `@Id` gets no `nulls` part, and `price: Double?` gets one.
A column behind an association is NULL when the association is missing (an order may have no customer), so it gets one. It gets none when every association step of the path is required (`optional = false`) and the value can never be NULL.

Then Hibernate writes the SQL for each database. It drops the clause when it is the database default.
MariaDB has no `NULLS` syntax, so Hibernate writes a `CASE` expression for a position that is not the default.

The SQL you get:

| You write | H2 | MariaDB | Oracle |
|---|---|---|---|
| `asc(key)`, `desc(key)` (`ASC`, `DESC`) | no extra clause (already the default) | no extra clause | `nulls first` / `nulls last` added |
| A NULL position that is the DB default (`asc(key, b)`, `desc(key, b)`, `*_NULLS_*`) | no extra clause | no extra clause | no extra clause |
| A NULL position that is **not** the DB default | `nulls first/last` | Hibernate writes `case when x is null ...` | `nulls first/last` |
| A key that can never be NULL (any direction) | no extra clause | no extra clause | no extra clause |

> [!TIP]
> On MariaDB, `by.asc(key, false)` (`ASC_NULLS_LAST`) and `by.desc(key, true)` (`DESC_NULLS_FIRST`) become a `CASE` expression. The database cannot use an index for that sort. Use `asc(key)` / `desc(key)` when you can.

<a id="45-what-a-sort-key-can-be"></a>
## 4.5 What a sort key can be

| Key | Allowed | Note |
|---|---|---|
| Column: `o.price` | yes | nullable keys (`Double?`, `String?`) are fine |
| To-one path: `o.customer?.name`, `o.customer!!.name` | yes | `left join` (inner join for a required association), see below |
| `@Lob` column | yes | Oracle rejects it. `LQL-5010` at `DEBUG` level, see below |
| `@Enumerated(EnumType.STRING)` enum column | yes | sorted by name (not by declaration order). `LQL-5011` at `DEBUG` level, see below |
| Arithmetic: `o.price!! * 2`, `+`, `-` | yes | |
| Widening conversion: `o.status!!.toLong()` | yes | |
| Elvis on a non-boolean value: `o.description ?: ""` | yes | `coalesce(e0.description, '')`. The NULL policy does not apply to it |
| An entity: `by.desc(o.customer)` | **compile error** | an entity is not `Comparable` |
| A value that does not use the entity: `by.asc(1)`, a captured value | **rejected** | see [4.6](#46-rules-for-the-lambda-body) |
| Function on the column: `lower(name)` | planned | |
| Boolean elvis: `{ it.flag ?: false }` | **rejected** (KC21) | use `{ it.flag }` and a NULL position |
| Value `if` / `when` | **rejected** (KC6) | |

Keys must be `Comparable`. Kotlin nullable keys are fine. Kotlin rejects an entity key at compile time.

**Association paths use `left join`.** A plain path in HQL, `order by o.customer.name`, becomes an inner join on all three databases. Orders without a customer would disappear.
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

**Required associations use an inner join.** An association mapped with `optional = false` (for example `@ManyToOne(optional = false)`) always has a target, so an inner join loses no rows (A4).
So such an association uses `join` even for a path that is used only in the sort or the select. Other associations use `left join` as shown above (rules S8, J1).
When every association step of the path is required and the value can never be NULL, the sort also gets no `nulls first/last`.

There is no Kotlin test for this yet.
The same rule is tested in Java ([`MappingRulesTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/MappingRulesTest.java), region `required-association`, see [the Java version of this page](../java/04-order-paging.md#45-what-a-sort-key-can-be)).
This is a design example:

```kotlin
// OrderLine.order is optional = false: a sort or select path through it is an inner join
qb.from(OrderLine::class.java).orderBy { l, by -> by.asc(l.order.price) }
// HQL: select e0 from OrderLine e0 join e0.order j1 order by j1.price asc
qb.from(OrderLine::class.java).select { it.order.price }
// HQL: select j1.price from OrderLine e0 join e0.order j1
```

**Sorting a `@Lob` column.** When you use a `@Lob` column in `orderBy`, `distinct` or `groupBy`, H2 and MariaDB run the query, but Oracle rejects it (ORA-22848, ORA-22849, measured). An `=` comparison works on all three databases.
lambdaQL does not change the query. It logs `LQL-5010` at `DEBUG` level once for each column and use (B3).

A Java test ([`MappingRulesTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/MappingRulesTest.java), region `lob`) checks the log for the three uses. This is a design example:

```kotlin
// memo is @Lob: Oracle rejects it in order by, group by and distinct; lambdaQL logs LQL-5010 at DEBUG
qb.from(OrderLine::class.java).orderBy { l, by -> by.asc(l.memo) }
qb.from(OrderLine::class.java).select { it.memo }.distinct()
```

**Sorting an `EnumType.STRING` enum.** For an enum column mapped with `@Enumerated(EnumType.STRING)`, the database sorts by the text order of the constant **names**. A Java enum comparison follows the declaration order, so the order of the result can be different (B2).
lambdaQL does not change the query. It logs `LQL-5011` at `DEBUG` level once for each column. `ORDINAL` (or no `@Enumerated`) sorts by declaration order, so nothing is logged.

A Java test ([`MappingRulesTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/MappingRulesTest.java), region `enum-string-sort`) checks this behavior. This is a design example:

```kotlin
// kind is @Enumerated(EnumType.STRING): the database sorts by name, Kotlin by declaration order (LQL-5011, DEBUG)
qb.from(Order::class.java).orderBy { o, by -> by.asc(o.kind) }
// HQL: select e0 from Order e0 order by e0.kind asc nulls first
```

The alias is shared with `where`. If `where` already joined the same path, the sort uses that join.
If `where` used this path as an inner join, the orders without a customer are already removed (decisions D36, D48). If the path was reached with `?.` and became a `left join` (decision D54), the sort uses that `left join` too (see [3.10](03-where.md#310-association-paths)).

<a id="46-rules-for-the-lambda-body"></a>
## 4.6 Rules for the lambda body

The body must be **a straight chain of `by` calls**.

```kotlin
// allowed
.orderBy { o, by -> by.desc(o.price).asc(o.id) }

// rejected: a key that changes with a condition
.orderBy { o, by -> if (flag) by.desc(o.price) else by.asc(o.id) }

// rejected: a key that does not use the entity
.orderBy { o, by -> by.asc(1) }

// rejected: loops, local val/var, more than one statement
```

lambdaQL checks this when you call `orderBy` and throws a `LambdaQLException` (for example `LQL-2023` for a condition, `LQL-3031` for a key that does not use the entity).
Every key must use the entity. A key made only of a constant or a captured value is rejected.

If the direction must change at run time, pass it as a variable: `by.sort(key, dir)`. If the key itself must change, build the query in two branches of normal Kotlin code.

<a id="47-order-of-calls"></a>
## 4.7 Order of calls

The builder types control the order of calls. A wrong order does not compile.

| Call | Allowed |
|---|---|
| `from(...).where { }.orderBy { }.limit(...).offset(...)` | yes |
| `from(...).where { }.limit(...).offset(...)` (no `orderBy`) | yes. The rows are sorted by the id (see [4.8](#48-paging)) |
| `orderBy { }` two times | no (compile error) |
| `where { }` after `orderBy { }` | no (compile error) |
| `orderBy { }` after `limit(...)` | no (compile error) |

After `orderBy`, `limit` or `offset`, the query has the type `OrderedSelectQuery`. It has only `limit`, `offset`, `toHql()`, `toList()`, `toSet()` and `first()`. `one()`, `count()` and `exists()` exist only before `orderBy`, `limit` and `offset` (see [Getting the result](02-getting-started.md#getting-the-result)).

<a id="48-paging"></a>
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

lambdaQL does not write `LIMIT` into the HQL. Hibernate writes the right SQL for each database.

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

Paging SQL is different on each database. H2 and Oracle get `fetch first ? rows only`, and MariaDB gets `limit ?`. With an offset, H2 and Oracle add `offset ? rows`, and MariaDB writes `limit ?,?`.

**The first row and the total count.**

- `orderBy { ... }.first()` returns the first row in that order, as an `Optional`. It reads only one row. An `offset` is kept: `orderBy { ... }.offset(1).first()` returns the second row.
- `count()` exists only on the where stage (before `orderBy`, `limit` and `offset`), so it always counts all matching rows. For a paged screen, keep the where-stage query in a value. Call `count()` on it for the total, then call `orderBy { ... }.limit(...).toList()` on it for the page.

See [Getting the result](02-getting-started.md#getting-the-result).

<a id="tie-breaker-automatic-id"></a>
### Tie-breaker (automatic `id`)

Look at the HQL above: lambdaQL added `e0.id asc` at the end. Why?

- Many rows can have the same `price`. The database may return those rows in **any order**. Oracle really does this (measured).
- Then page 2 and page 3 can show the same row twice, or skip a row.

Rule: **when you use paging and the sort keys do not contain the id, lambdaQL adds the root `@Id asc` as the last key.**

- It is added only once. If your keys already contain the id (for example `by.desc(o.id)`), it is not added.
- It is not added when the sort keys contain a unique column of the main entity that can never be NULL (A5). This means `@Column(unique = true)` with `nullable = false`, Hibernate `@NaturalId`, and `@Id`. Such a key alone fixes the order of all rows.
  A unique column that can be NULL does not count, because many rows can be NULL.
- Without `limit` and `offset`, nothing is added.
- With explicit joins (`join`, `leftJoin`, `crossJoin`), the id of every joined entity is also added, in join order (see [6.8](06-select-group.md#68-explicit-joins)).
- Not implemented yet: a collection fetch join together with `limit`/`offset` is rejected.

There is no Kotlin test for this yet. The same rule is tested in Java ([`MappingRulesTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/MappingRulesTest.java), region `unique-key`).
This is a design example:

```kotlin
// OrderLine.code is @Column(unique = true, nullable = false): no id tie-breaker
qb.from(OrderLine::class.java).orderBy { l, by -> by.asc(l.code) }.limit(2).toList()
// HQL: select e0 from OrderLine e0 order by e0.code asc
```

<a id="49-strings-and-special-values"></a>
## 4.9 Strings and special values

- String order follows the database collation. MariaDB `_ci` ignores case. Oracle uses `NLS_SORT`, which follows the JVM locale (for example `de_DE` gives German order). For Oracle we recommend `NLS_SORT=BINARY`.
- Oracle stores `''` as NULL, so empty strings sort as NULLs.
- `NaN` is ordered differently by each database.

<a id="410-reusable-and-external-sorts"></a>
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

The SQL is the same as for the same sort written in `orderBy { o, by -> ... }`. Oracle adds `nulls last`, because its own default for `desc` is the opposite (see [4.4](#44-null-ordering-is-the-same-on-every-database)).

**`then()`: more keys after the existing ones.** `a.then(b)` sorts by the keys of `a` first. Rows with equal values are then sorted by the keys of `b`.
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

**`SortSpec.ofPath`: a sort from a property name.** `SortSpec.ofPath(Order::class.java, "customer.name", Direction.DESC)` makes a spec from a property path. It is for sort keys that come as text from outside the code. The Spring module converts a Spring `Sort` this way (see [12.5](12-spring-data-jpa.md#125-spring-sort-and-pageable)).
Every part of the path must be a persistent property, and the last part must be a basic value. Any other path is an error.
The sort policy decides which names are allowed (see [the sort policy](#sort-policy) below). Without a policy and without `@Sortable` fields, any persistent property is accepted, and lambdaQL logs `LQL-5003` (at `INFO` level by default).
This form has no lambda, so Kotlin works the same as Java. The Java test is in [the Java version of this page](../java/04-order-paging.md#410-reusable-and-external-sorts) (region `sort-spec-path`).

```kotlin
val byName = SortSpec.ofPath(Order::class.java, "customer.name", Direction.DESC)
    .then(SortSpec.ofPath(Order::class.java, "id", Direction.ASC))
qb.from(Order::class.java).orderBy(byName).toList()
// HQL: select e0 from Order e0 left join e0.customer j1 order by j1.name desc nulls last, e0.id asc
```

<a id="sort-policy"></a>
### The sort policy: which outside names are allowed

**Implemented** (decision D43). Tested with Java lambdas on H2, MariaDB 10.11 and Oracle 23.26.

A sort name that comes from outside the code (a request parameter, a Spring `Sort` or `Pageable`) can name any persistent property.
The **sort policy** decides which names are allowed.

- `SortRules<T>` lists the allowed names of one entity. You give each column as a lambda, so the compiler checks it.
- `SortPolicy` holds the rules of each entity and a mode (`SortMode`). You register one policy for each `EntityManagerFactory`, that is, for each persistence unit. Every `QueryBuilder` and repository on that factory uses it.
- `@Sortable` on the fields of an entity gives rules without code (see [Rules from `@Sortable`](#rules-from-sortable) below).

The rules of an entity come from **one** place only, in this order: the `SortRules` in the policy (from code or a Spring `SortPolicy` bean), the Spring Boot properties `lambdaql.sort.*` (see [12.5](12-spring-data-jpa.md#sort-policy-with-spring)), the `@Sortable` fields of the entity.
Rules from two places are not merged.

**Which sorts are checked.** Only sorts made from strings: `SortSpec.ofPath`, and a Spring `Sort` or `Pageable` (the Spring module uses `ofPath`, see [12.5](12-spring-data-jpa.md#sort-policy-with-spring)).
A sort lambda (`orderBy { o, by -> ... }`, `SortSpec.of`) is your own code, so it is not checked.

| Rule | Allowed outside name |
|---|---|
| `allow { it.price }` | `price` (the path of the column) |
| `allow { it.customer?.name }` | `customer.name`. A path through an association is allowed only when it is listed like this |
| `alias("newest") { it.updateAt }` | `newest`, which sorts by `updateAt`. The path `updateAt` itself is then **not** an outside name, so the client never sees the property name |

A rule must be a column of the entity.
Any other key is rejected with `LQL-4041` the first time the rules are used (not tested yet).

| `SortMode` | A name that the rules do not allow |
|---|---|
| `OFF` | The sort runs. Nothing is checked |
| `INFO` | The sort runs, and lambdaQL logs `LQL-5003` at `INFO` level once for each entity and name. **This is the default:** without a registered policy, lambdaQL uses `INFO`, with rules only from `@Sortable` |
| `WARN` | The same as `INFO`, but the log level is `WARN` |
| `STRICT` | The sort is rejected with `SortNotAllowedException` (`LQL-4040`). An entity that has neither rules nor `@Sortable` fields rejects every name |

So, until you register a policy, each outside sort name that no `@Sortable` field allows logs `LQL-5003` once for each entity.
To reject unknown names, register a policy with `STRICT`. To turn the check off, register one with `OFF`.

| Method | What it does |
|---|---|
| `SortPolicy.of(mode, rules...)` | Makes a policy from a mode and the rules of each entity |
| `SortPolicy.register(emf, policy)` | Registers the policy of a persistence unit. It replaces an earlier one |
| `SortPolicy.unregister(emf)` | Removes the policy. The default (`INFO`, rules only from `@Sortable`) applies again |
| `SortPolicy.find(emf)` | Returns the registered policy (`Optional`) |
| `SortPolicy.effective(emf)` | Returns the policy in force. Without a registered policy, the default |

With Spring Boot, define a `SortPolicy` bean instead of calling `register` (see [12.5](12-spring-data-jpa.md#sort-policy-with-spring)).

There are no Kotlin tests for the sort policy yet.
The same rules written in Java are tested in [`SortPolicyTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortPolicyTest.java) (H2, MariaDB and Oracle). See [the Java version of this page](../java/04-order-paging.md#sort-policy) for the tested examples.
This is a design example:

```kotlin
val orderRules = SortRules.of(Order::class.java)
    .allow { it.price }                      // the name "price"
    .allow { it.customer?.name }             // an association path: allowed only when listed
    .alias("newest") { it.updateAt }         // the outside name "newest" sorts by updateAt
SortPolicy.register(emf, SortPolicy.of(SortMode.STRICT, orderRules))

qb.from(Order::class.java).orderBy(SortSpec.ofPath(Order::class.java, "newest", Direction.DESC)).toList()
// HQL: select e0 from Order e0 order by e0.updateAt desc nulls last
qb.from(Order::class.java).orderBy(SortSpec.ofPath(Order::class.java, "updateAt", Direction.ASC)).toList()
// SortNotAllowedException (LQL-4040): the path behind an alias is not an outside name
qb.from(Order::class.java).orderBy { o, by -> by.asc(o.description) }.toList()
// a sort lambda is your code: never checked
```

<a id="rules-from-sortable"></a>
**Rules from `@Sortable`.** You can also mark the fields or getters of an entity with `@org.lambdaql.Sortable`.

| Annotation | Allowed outside name |
|---|---|
| `@field:Sortable` on the property `name` | `name` |
| `@field:Sortable("level")` on the property `grade` | `level`, which sorts by `grade` (an alias) |
| `@field:Sortable(path = "name")` on the association property `customer` | `customer.name` |

- lambdaQL reads only the annotations of the entity in `from(...)`. An annotation on another entity does not allow a path through an association, so use `path` on the association field.
- The annotations are used only when the policy has no rules for that entity (from code, a Spring bean or Spring Boot properties).
- They also work without a registered policy, in the default `INFO` mode.

In Kotlin, write `@field:Sortable`, so the annotation goes on the JVM field.
The same rules are tested in Java ([`SortPolicyTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortPolicyTest.java), region `annotation`, see [the Java version of this page](../java/04-order-paging.md#rules-from-sortable)). This is a design example:

```kotlin
@Entity
class Customer(
    @Id @GeneratedValue
    var id: Long? = null,
    @field:Sortable                     // the outside name "name"
    var name: String? = null,
    @field:Sortable("level")            // the outside name "level" sorts by grade
    var grade: Int? = null,
)

// Customer has no SortRules in the policy, so its @Sortable fields are the rules
qb.from(Customer::class.java).orderBy(SortSpec.ofPath(Customer::class.java, "level", Direction.DESC)).toList()
// HQL: select e0 from Customer e0 order by e0.grade desc nulls last
```

<a id="rules-as-strings"></a>
**Rules as strings.** `allowPath(String...)` and `aliasPath(name, path)` take property paths as text. They are meant for configuration files: the Spring Boot properties in [12.5](12-spring-data-jpa.md#sort-policy-with-spring) use them.
The paths are checked against the entity the first time the rules are used. In code, use the lambda forms `allow` and `alias`, because the compiler checks them.

Tested in Java ([`SortPolicyTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortPolicyTest.java), region `string-paths`). There is no lambda, so Kotlin works the same.

```kotlin
// the string forms used by configuration files (lambdaql.sort.allow / lambdaql.sort.alias)
SortPolicy.register(emf, SortPolicy.of(SortMode.STRICT,
    SortRules.of(Order::class.java).allowPath("price", "customer.name").aliasPath("newest", "updateAt")))
```

- The check runs when the spec is passed to `orderBy`, before the path is read.
  So in `STRICT` mode, a name that is not in the rules gives `SortNotAllowedException` even when it is not a property at all.
- The rules belong to one entity class. If you pass two `SortRules` for the same class, the last one is used.
- `SortNotAllowedException` is a `LambdaQLException` (see [11. Errors and languages](../11-errors-and-languages.md#exceptions)).
  lambdaQL has no web dependency. For an example of an HTTP 400 response, see [12.5](12-spring-data-jpa.md#sort-policy-with-spring).

**Not implemented yet:**

- One text with a name and a direction (`"price,desc_nulls_last"`, rule S11).
  Read the direction with `Direction.parse` (see [4.3](#43-direction)) and the name with `SortSpec.ofPath`.
- **Planned:** the join form of `SortSpec`, keyset paging. (The lambda form `{ o, c, by -> ... }` for joins is implemented, see [6.8](06-select-group.md#68-explicit-joins).)

<a id="411-sorting-inside-subqueries"></a>
## 4.11 Sorting inside subqueries

**Implemented** (milestone M8-1, together with the subqueries of [5. Subqueries](05-subquery.md)). The MariaDB `lateral` rewrite is not implemented yet.

A sort inside a subquery is kept only when it is used with `limit` (top-1 or top-N).
Without `limit`, it does not change the result, so lambdaQL removes it and logs a warning.
See [5. Subqueries](05-subquery.md#59-sorting-inside-a-subquery).

<a id="412-rules-summary-s1-to-s14"></a>
## 4.12 Rules summary (S1 to S14)

| # | Rule | Status |
|---|---|---|
| S1 | A sort is one lambda: `{ o, by -> by... }`. For one key: `{ it.key }` with an optional `Direction`. For joins: `{ o, c, by -> by... }` (`by` is always last) | Implemented. The join form `{ o, c, by -> }` is also implemented |
| S2 | A key is `Comparable` (Kotlin nullable keys are allowed). An entity cannot be a key (compile error). A key must use the entity | Implemented |
| S3 | The direction comes from `asc` / `desc` or `sort(key, Direction)`. `asc(key)`/`desc(key)` (`ASC`/`DESC`) use the NULL policy. `asc(key, nullsFirst)`, `desc(key, nullsFirst)` and `*_NULLS_*` are used as written | Implemented |
| S4 | The body is only a straight chain of `by` calls. No `if`, `when` or loop | Implemented |
| S5 | `orderBy` once per query. No `where` after `orderBy`. No `orderBy` after `limit` (compile error) | Implemented |
| S6 | Reuse with `SortSpec` (`of`, `ofPath`, `then`, `reversed`, `orderBy(spec)`). Outside strings are checked by the sort policy of the persistence unit (`SortRules`, `SortPolicy`, `SortMode`, see [4.10](#sort-policy)) | `SortSpec` and the sort policy implemented. The join form of `SortSpec` is not implemented yet |
| S7 | Keys: columns, to-one paths, `+ - *`, widening. No value branches. Kotlin `?.` is read as a path | Implemented (not tested with Kotlin lambdas) |
| S8 | Association paths that are used only in `orderBy` use a `left join`, so rows without the association are kept. A required association (`optional = false`) loses no rows, so it uses an inner join (A4). If `where` uses the same path, the sort uses the `where` join | Implemented |
| S9 | With paging, the root `@Id asc` is added if the keys do not contain the id. It is not added when the keys contain a unique column that can never be NULL (`unique = true` and `nullable = false`, `@NaturalId`) (A5). Collection fetch join + paging is rejected | Implemented for the root id and the ids of explicitly joined entities. The fetch join check is not implemented yet |
| S10 | The analysis is cached per lambda. A captured `Direction` is read on every call | Implemented |
| S11 | Spring `Sort.Order` maps 1:1 to `Direction` (Spring module, see [12.5](12-spring-data-jpa.md#125-spring-sort-and-pageable)). Its property names go through the sort policy, and an alias is passed on as it is | Spring `Sort` implemented and tested with Java (`ignoreCase` not supported yet). Use together with the sort policy is also tested. Reading one text such as `"price,desc_nulls_last"` is not implemented yet |
| S12 | Join queries use the same shape: `{ o, c, by -> }`, `{ o, c, i, by -> }` | Implemented (see [6.8](06-select-group.md#68-explicit-joins)) |
| S13 | Property references only in the one-key short form (`orderBy(Order::price)`). A reference that compiles but has the wrong meaning is rejected | Not implemented yet (Kotlin property references) |
| S14 | Subquery sorts: kept with `limit`, removed with a warning without `limit`. lambdaQL handles the database differences (see chapter 5) | Implemented. The MariaDB `lateral` rewrite is not implemented yet |

---

[Kotlin track](README.md) | Previous: [3. Conditions (`where`)](03-where.md) | Next: [5. Subqueries](05-subquery.md) | [Java version of this page](../java/04-order-paging.md)
