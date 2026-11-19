<a id="lambdaql-manual-kotlin-track"></a>
# lambdaQL Manual: Kotlin track

> [!IMPORTANT]
> This page is older than the Korean manual. See the [Korean page](../../ko/kotlin/README.md) for the latest rules.

**Other languages:** English | [한국어](../../ko/kotlin/README.md) | [日本語](../../ja/kotlin/README.md) | [简体中文](../../zh/kotlin/README.md)

[Manual home](../README.md) | [Java track](../java/README.md) | Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md)

This is the **Kotlin track** of the lambdaQL manual. Every page in this track has **only Kotlin code**.
If you write your queries in Java, read the [Java track](../java/README.md). Both tracks have the same chapter numbers and file names (only chapter 9 is different), so you can switch at any time.

```kotlin
val orders = qb.from(Order::class.java)
    .where { it.status == 1 && it.price!! > minPrice }
    .orderBy { o, by -> by.desc(o.price).asc(o.id) }
    .limit(20)
    .toList()
// HQL: select e0 from Order e0
//      where e0.status = 1 and e0.price > :p1
//      order by e0.price desc nulls last, e0.id asc
```

**About the SQL blocks.** `…` is the shortened select list (all entity columns), and `?` is a bind parameter. The lines come from [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java). It runs each query on H2 2.3, MariaDB 10.11 and Oracle 23.26 with Hibernate 6.6.5, and it fails if Hibernate's SQL changes. The test uses Java lambdas. A Kotlin lambda with the same meaning gives the same HQL, so it gets the same SQL.

**SQL by database (Hibernate's SQL for this HQL; Kotlin gives the same HQL)** — the manual's `Order` has a nullable `price`, but the test `Order` does not, so `SqlByDatabaseTest` gives this HQL to Hibernate directly. From `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `readme-first`:

```sql
-- H2
-- (this HQL is given to Hibernate directly)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>?
order by o1_0.price desc,o1_0.id
fetch first ? rows only

-- MariaDB
-- (this HQL is given to Hibernate directly)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>?
order by o1_0.price desc,o1_0.id
limit ?

-- Oracle
-- (this HQL is given to Hibernate directly)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>?
order by o1_0.price desc nulls last,o1_0.id
fetch first ? rows only
```

> [!NOTE]
> **About the examples.** Examples are design examples. When a feature is implemented, its examples are replaced by code taken from the test suite, with a link to the test file. Examples copied from the Kotlin tests are marked **(tested)** (chapters 3 to 7).

<a id="contents"></a>
## Contents

| # | Page | What you learn |
|---|---|---|
| 1 | [Overview](../01-overview.md) (common) | What lambdaQL is, why, and how it works |
| 2 | [Getting started](02-getting-started.md) | Requirements, Gradle setup, Kotlin entities, first query |
| 3 | [Conditions (`where`)](03-where.md) | Operators, null rules, `?.` `?:` `!!`, ranges, `when`, captured values |
| 4 | [Sorting and paging](04-order-paging.md) | `orderBy`, `Direction`, `limit`, `offset` |
| 5 | [Subqueries](05-subquery.md) | `exists()`, `x in sub...`, scalar and correlated subqueries, joins inside a subquery |
| 6 | [Select, aggregates and group by](06-select-group.md) | `select`, data classes, `Pair`, `groupBy`, `having`, `Agg`, joins, derived tables, CTE |
| 7 | [Update, insert, delete](07-dml.md) | Property assignment, `Dml.value`, `values`, `all()`, `execute()`, `limit`, `executeOne()` |
| 7.6 | [Upsert](07-6-upsert.md) | `upsert`, `onConflict { ... }.update { ... }`, `onConflict { ... }.ignore()`, row counts per database |
| 8 | [Procedures and DB functions](08-procedures-functions.md) | `@SqlProcedure`, `@SqlFunction` in a `companion object`, `qb.call`, `qb.exec` |
| 9 | [Kotlin notes](09-kotlin-notes.md) | What is special in Kotlin: `?.` `?:` `!!`, `==`, `in`, `when`, operators, limits KC1 to KC21 |
| 10 | [Rules and limits](10-rules-and-limits.md) | All rule tables in one place |
| 11 | [Errors and languages](../11-errors-and-languages.md) (common) | Error codes, exceptions, message language, logging |
| 12 | [Spring Data JPA](12-spring-data-jpa.md) | `LambdaQlExecutor<T>`, repository methods with a body, DML shortcuts (`deleteAll`, `updateOne`, ...), Spring `Sort` and `Pageable`, modules, transactions, Kotlin 2.2 / `-Xjvm-default=all` |

<a id="where-to-start"></a>
## Where to start

1. Read the common [Overview](../01-overview.md) to understand the idea: a lambda is a **query**, not code. lambdaQL reads its bytecode and never runs it.
2. Set up your project with [2. Getting started](02-getting-started.md).
3. Learn conditions in [3. Conditions](03-where.md). This is the most important chapter.
4. Read [9. Kotlin notes](09-kotlin-notes.md) once. It explains the Kotlin forms that work differently in a query (`!!`, `?.`, `?:`, `==`, `===`).

<a id="example-entities"></a>
## Example entities

All pages in this track use these Kotlin entities. The full code is in [2. Getting started](02-getting-started.md#example-entities).

| Entity | Properties |
|---|---|
| `Order` | `id: Long?`, `price: Double?`, `status: Int?`, `active: Boolean`, `description: String?`, `customer: Customer?`, `updateAt: LocalDateTime?`, `items: MutableList<Item>` |
| `Customer` | `id: Long?`, `name: String?`, `grade: Int?` |
| `Item` | `id: Long?`, `order: Order?`, `price: Double?`, `qty: Int` |

Most properties are nullable `var` properties, because a database column can be NULL.
`active` and `qty` are Kotlin `Boolean` and `Int`. These are JVM primitives, so they can never be NULL.

<a id="three-things-to-know-first"></a>
## Three things to know first

| Kotlin | In a lambdaQL lambda |
|---|---|
| `it.price!! > 100.0` | `!!` is ignored. It is only there so that the code compiles. There is no `NullPointerException`, because the lambda never runs. |
| `it.customer?.name == "Kim"` | `it.customer?.name` names a column of the joined customer. In `where` it is an inner `join` by default: orders without a customer are not returned. A null guard (`it.customer == null \|\| ...`) does not change this (D48). To keep these orders, use `leftJoin`. `it.customer!!.name` means the same column. |
| `it.status == 1` with `status: Int?` | `e0.status = 1`. A NULL `status` does not match (SQL meaning). lambdaQL ignores the null checks that the Kotlin compiler adds. Only the null checks **you write** count. |

Details: [9. Kotlin notes](09-kotlin-notes.md).

<a id="supported-environment"></a>
## Supported environment

| Item | Version |
|---|---|
| Kotlin | 2.x (the design was checked with kotlinc 1.7.22, 2.1.10, 2.2.21, 2.3.0 and 2.3.21) |
| Java (JVM) | 21 |
| Hibernate ORM | 6.6 (the design was verified with 6.6.5) |
| Databases verified in the design | H2 2.3, MariaDB 10.11, Oracle 23.26 (23ai) |
| Spring Boot (optional) | 3.4 (tested with 3.4.2, Java tests). Spring Boot 4 is not verified yet |

<a id="other-languages"></a>
## Other languages

Every language folder (`en`, `ko`, `ja`, `zh`) has the same layout. To read a page in another language, change `en` to `ko`, `ja` or `zh` in its address. The links are at the top of this page.

<a id="help-and-contributions"></a>
## Help and contributions

- Source code: <https://github.com/codebit0/lambdaql>
- The Korean manual is the source. The other languages are translations.
- If you find a mistake, please open an issue or a pull request.
- Design documents (Korean, for contributors): [ROADMAP.md](../../../design/ROADMAP.md), [KOTLIN.md](../../../design/KOTLIN.md)
