# lambdaQL Manual: Kotlin track

**Other languages:** English | [한국어](../../ko/kotlin/README.md) | [日本語](../../ja/kotlin/README.md) | [简体中文](../../zh/kotlin/README.md)

> [!IMPORTANT]
> **Status:** lambdaQL is in early development.
> The **analysis core**, **`where` with the result functions** (`toList()`, `toSet()`, `first()`, `one()`, `count()`, `exists()`) and the core of **sorting and paging** (`orderBy`, `limit`, `offset`, `SortSpec`) are implemented: lambdaQL loads a lambda, decodes it, writes the HQL and runs it. This is tested on H2, MariaDB and Oracle, mostly with **Java** lambdas.
> **Kotlin lambdas are tested in the build** (kotlinc 2.3.21, [`KotlinSelectTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinSelectTest.kt), [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)) for `?.` paths, `!!`, captured values, `in`, a sort, subqueries, `select` with data classes, `Pair` and `Triple`, `groupBy` and `having`, update, insert and delete. The other Kotlin-only forms (`?:`, ranges, `when`, property references) are **designed**: their bytecode was compiled and checked with prototypes (kotlinc 1.7.22 to 2.3.21).
> **Subqueries**, **`select`** (with `groupBy`, `having` and `Agg`), **update, insert, delete and upsert** (with `limit` and the one-row protection `executeOne()`), **explicit joins**, **derived tables and CTEs**, **stored procedures and DB functions** and **error codes in four languages** are implemented. Joins, derived tables, CTEs, procedures, DB functions and upsert are tested with Java lambdas only.
> The **Spring Data JPA module** (`LambdaQlExecutor<T>`, Spring `Sort` and `Pageable`, the DML shortcuts such as `deleteAll { }` and `updateOne({ }) { }`, the update, delete, insert and upsert builders, the Spring Boot auto-configuration) is implemented and tested with Java repositories (Spring Boot 3.4.2). DML needs the caller's transaction. Kotlin repositories are not tested yet.
> No artifact is published to Maven Central yet. To try it, [build from source](02-getting-started.md#build-from-source).

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

## Contents

| # | Page | What you learn | Status |
|---|---|---|---|
| 1 | [Overview](../01-overview.md) (common) | What lambdaQL is, why, and how it works | – |
| 2 | [Getting started](02-getting-started.md) | Requirements, Gradle setup, Kotlin entities, first query | Build from source works. `where`, `toList()` and the result functions `first()`, `one()`, `count()`, `exists()`, `toSet()`: implemented, tested on H2, MariaDB, Oracle (`where` and `toList()` also with Kotlin lambdas; the result functions with Java lambdas only) |
| 3 | [Conditions (`where`)](03-where.md) | Operators, null rules, `?.` `?:` `!!`, ranges, `when`, captured values | Mapping getters to JPA attributes, HQL rendering and running queries (`where`, `toList()`): implemented, tested on H2, MariaDB, Oracle. `?.`, `!!`, captured values and `in`: tested with Kotlin lambdas. `?:`, ranges, `when`: designed |
| 4 | [Sorting and paging](04-order-paging.md) | `orderBy`, `Direction`, `limit`, `offset` | Core implemented, tested on H2, MariaDB, Oracle (one Kotlin test, the rest Java tests), including `SortSpec`, join sorts `{ o, c, by -> }` and the sort policy for outside sorts (rules in code, Spring Boot properties or `@Sortable`). Property references, the join form of `SortSpec`: designed |
| 5 | [Subqueries](05-subquery.md) | `exists()`, `x in sub...`, scalar and correlated subqueries | Implemented, tested on H2, MariaDB, Oracle (`exists`, `in` and scalar also with Kotlin lambdas). The MariaDB `lateral` rewrite: not implemented yet |
| 6 | [Select, aggregates and group by](06-select-group.md) | `select`, data classes, `Pair`, `groupBy`, `having`, `Agg`, joins, derived tables, CTE | `select`, `groupBy`, `having`, `Agg`, `distinct()`: implemented, tested on H2, MariaDB, Oracle (data class, `Pair`, `Triple`, `groupBy` and `having` also with Kotlin lambdas). Explicit joins, derived tables and CTE: implemented (Java tests; Kotlin not tested yet) |
| 7 | [Update, insert, delete](07-dml.md) | Property assignment, `by.set`, `values`, `all()`, `execute()`, `limit`, `executeOne()` | Implemented, tested on H2, MariaDB, Oracle, including `limit` and `executeOne()` (property assignment, `by[column] = value`, insert and delete also with Kotlin lambdas). Subqueries (`sub`) in the `where` of update and delete and aggregates in `insert ... select`: implemented. Subqueries in `set` values: designed |
| 7.6 | [Upsert](07-6-upsert.md) | `upsert`, `onConflict { ... }.update { ... }`, `onConflict { ... }.ignore()`, row counts per database | Implemented, tested on H2, MariaDB, Oracle (Java tests; Kotlin not tested yet) |
| 8 | [Procedures and DB functions](08-procedures-functions.md) | `@SqlProcedure`, `@SqlFunction` in a `companion object`, `qb.call`, `qb.exec` | Implemented (Java tests; Kotlin not tested yet): DB functions on H2, MariaDB, Oracle; procedures on MariaDB and Oracle, including the argument name check and the detection of Oracle package overloads. SARGable warnings for DB functions (`LQL-5001`, `LQL-5007`): implemented. Type checks of procedure parameters: not implemented yet |
| 9 | [Kotlin notes](09-kotlin-notes.md) | What is special in Kotlin: `?.` `?:` `!!`, `==`, `in`, `when`, operators, limits KC1 to KC21 | Partly tested (`?.`, `!!`, `in`, data classes, property assignment). The other forms: designed |
| 10 | [Rules and limits](10-rules-and-limits.md) | All rule tables in one place | Partly implemented |
| 11 | [Errors and languages](../11-errors-and-languages.md) (common) | Error codes, exceptions, message language, logging | Implemented, unit tested. Strict mode: not implemented yet |
| 12 | [Spring Data JPA](12-spring-data-jpa.md) | `LambdaQlExecutor<T>`, repository methods with a body, DML shortcuts (`deleteAll`, `updateOne`, ...), Spring `Sort` and `Pageable`, modules, transactions, Kotlin 2.2 / `-Xjvm-default=all` | Implemented, tested on H2, MariaDB, Oracle with Spring Boot 3.4.2, including the DML shortcuts and the update, delete, insert and upsert builders (Java tests; Kotlin repositories not tested yet) |

What the words mean:

| Word | Meaning |
|---|---|
| Implemented | The code is in the repository and has unit tests. |
| Designed | The syntax and rules are decided. Prototypes were tested. The library does not have the code yet. |
| Planned | The idea is known, but the details are not decided. |

## Where to start

1. Read the common [Overview](../01-overview.md) to understand the idea: a lambda is a **query**, not code. lambdaQL reads its bytecode and never runs it.
2. Set up your project with [2. Getting started](02-getting-started.md).
3. Learn conditions in [3. Conditions](03-where.md). This is the most important chapter.
4. Read [9. Kotlin notes](09-kotlin-notes.md) once. It explains the Kotlin forms that work differently in a query (`!!`, `?.`, `?:`, `==`, `===`).

## Example entities

All pages in this track use these Kotlin entities. The full code is in [2. Getting started](02-getting-started.md#example-entities).

| Entity | Properties |
|---|---|
| `Order` | `id: Long?`, `price: Double?`, `status: Int?`, `active: Boolean`, `description: String?`, `customer: Customer?`, `updateAt: LocalDateTime?`, `items: MutableList<Item>` |
| `Customer` | `id: Long?`, `name: String?`, `grade: Int?` |
| `Item` | `id: Long?`, `order: Order?`, `price: Double?`, `qty: Int` |

Most properties are nullable `var` properties, because a database column can be NULL.
`active` and `qty` are Kotlin `Boolean` and `Int`. These are JVM primitives, so they can never be NULL.

## Three things to know first

| Kotlin | In a lambdaQL lambda |
|---|---|
| `it.price!! > 100.0` | `!!` is ignored. It is only there so that the code compiles. There is no `NullPointerException`, because the lambda never runs. |
| `it.customer?.name == "Kim"` | `it.customer?.name` names a column of the joined customer. In `where` it is an inner `join` by default: orders without a customer are not returned. A null guard (`it.customer == null \|\| ...`) makes it a `left join`. `it.customer!!.name` means the same column. |
| `it.status == 1` with `status: Int?` | `e0.status = 1`. A NULL `status` does not match (SQL meaning). lambdaQL ignores the null checks that the Kotlin compiler adds. Only the null checks **you write** count. |

Details: [9. Kotlin notes](09-kotlin-notes.md).

## Supported environment

| Item | Version |
|---|---|
| Kotlin | 2.x (the design was checked with kotlinc 1.7.22, 2.1.10, 2.2.21, 2.3.0 and 2.3.21) |
| Java (JVM) | 21 |
| Hibernate ORM | 6.6 (the design was verified with 6.6.5) |
| Databases verified in the design | H2 2.3, MariaDB 10.11, Oracle 23.26 (23ai) |
| Spring Boot (optional) | 3.4 (tested with 3.4.2, Java tests). Spring Boot 4 is not verified yet |

## Other languages

Every language folder (`en`, `ko`, `ja`, `zh`) has the same layout. To read a page in another language, change `en` to `ko`, `ja` or `zh` in its address. The links are at the top of this page.

## Help and contributions

- Source code: <https://github.com/codebit0/lambdaql>
- The English manual is the source. The other languages are translations.
- If you find a mistake, please open an issue or a pull request.
- Design documents (Korean, for contributors): [ROADMAP.md](../../../design/ROADMAP.md), [KOTLIN.md](../../../design/KOTLIN.md)
