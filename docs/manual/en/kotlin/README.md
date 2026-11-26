<a id="lambdaql-manual-kotlin-track"></a>
# lambdaQL Manual: Kotlin track

**Other languages:** English | [한국어](../../ko/kotlin/README.md) | [日本語](../../ja/kotlin/README.md) | [简体中文](../../zh/kotlin/README.md)

[Manual home](../README.md) | [Java track](../java/README.md) | Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md) · [13. Spring Boot 3 and 4](../13-spring-boot-versions.md)

This is the **Kotlin track** of the lambdaQL manual. Every page in this track has **only Kotlin code**.
If you write your queries in Java, read the [Java track](../java/README.md). Both tracks have the same chapter numbers and file names (only chapter 9 is different), so you can switch at any time.

<!-- region: ManualKotlinStartTest.kt#readme -->
```kotlin
val q = qb.from(Order::class.java)
    .where { it.status.toInt() == 1 && it.price > minPrice }
    .orderBy { o -> desc(o.price).asc(o.id) }
    .limit(20)
assertEquals("select e0 from Order e0 where e0.status = 1 and e0.price > :p1"
    + " order by e0.price desc, e0.id asc", q.toHql().hql())
val orders: List<Order> = q.toList()
```

> [!NOTE]
> **About the examples.** Every code example is a part of running Kotlin code, copied as it is. The "Example code:" line at the end of a section gives the source file and the part name. The string in `assertEquals` is the HQL that lambdaQL makes. In the SQL blocks per database, `…` is the shortened select list (all entity columns), and `?` is a bind parameter.

Example code: [`ManualKotlinStartTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinStartTest.kt) (`readme`).

<a id="contents"></a>
## Contents

| # | Page | What you learn |
|---|---|---|
| 1 | [Overview](../01-overview.md) (common) | What lambdaQL is, why it is needed, and how it works |
| 2 | [Getting started](02-getting-started.md) | Requirements, Gradle setup, example entities, first query, result functions |
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
| 13 | [Spring Boot 3 and 4](../13-spring-boot-versions.md) (common) | Version table, artifacts (common jars and the `-boot3` and `-boot4` starters), SQL differences between Hibernate 6.6 and 7.4, what is verified |

<a id="where-to-start"></a>
## Where to start

1. Read the common [Overview](../01-overview.md) to understand the basic idea. A lambda is a **query**, not code. lambdaQL does not call the lambda. It reads the bytecode opcodes and makes HQL.
2. Set up your project with [2. Getting started](02-getting-started.md).
3. Learn conditions in [3. Conditions](03-where.md). This is the most important chapter.
4. Read [9. Kotlin notes](09-kotlin-notes.md) once. It explains the Kotlin forms that work differently in a query (`!!`, `?.`, `?:`, `==`, `===`).

<a id="example-entities"></a>
## Example entities

The examples in this track use example entities written as Java classes (`query/src/test/java/org/lambdaql/query/`). The details are in [2. Getting started](02-getting-started.md#example-entities).

| Entity | Main properties (Java types) |
|---|---|
| `Order` | `Long id`, `double price`, `short status`, `boolean active` (`isActive` in Kotlin), `String description`, `Integer cnt`, `String custName`, `LocalDateTime updateAt`, `Customer customer`, `Address address` |
| `Customer` | `int id`, `String name`, `Integer grade` |
| `Item` | `Long id`, `Order order`, `Double price`, `int qty` |

`price`, `status`, `active` and `qty` are Java primitive types, so they can never be NULL.
Properties of reference types can be NULL. In Kotlin they appear as platform types.

<a id="three-things-to-know-first"></a>
## Three things to know first

| Kotlin | In a lambdaQL lambda |
|---|---|
| `it.price!! > 100.0` | `!!` is ignored. It is only there so that the code compiles. There is no `NullPointerException`, because lambdaQL does not call the lambda. It reads the bytecode opcodes. |
| `it.customer?.name == "Kim"` | `it.customer?.name` names a column of the joined customer. This condition cannot match an order without a customer, so it is an inner `join`, and orders without a customer are not returned. It becomes a `left join` when an order without a customer can match the condition (`it.status == 1 \|\| ...`, `it.customer?.name == null`). `it.customer!!.name` is the same column and follows the same rule. But a comparison that passes through `!!` is true only for orders that have a customer. |
| `it.cnt == n` with `n: Int?` | `e0.cnt = :p1`. A row with a NULL `cnt` does not match (SQL meaning). To find NULL rows, **write** the null check yourself, for example `it.cnt == null`. |

Details: [9. Kotlin notes](09-kotlin-notes.md).

<a id="supported-environment"></a>
## Supported environment

| Item | Version |
|---|---|
| Kotlin | 2.x (the design was checked with kotlinc 1.7.22, 2.1.10, 2.2.21, 2.3.0 and 2.3.21) |
| Java (JVM) | 21 or later |
| Hibernate ORM | 6.6 or 7.4 (tested with 6.6.53 and 7.4.13) |
| Databases verified in the design | H2 2.3, MariaDB 10.11, Oracle 23.26 (23ai) |
| Spring Boot (optional) | 3.5 or 4.1 (tested with 3.5.16 and 4.1.2). See [13. Spring Boot 3 and 4](../13-spring-boot-versions.md) |

<a id="other-languages"></a>
## Other languages

Every language folder (`en`, `ko`, `ja`, `zh`) has the same layout. To read a page in another language, change `en` to `ko`, `ja` or `zh` in its address. The links are at the top of this page.

<a id="help-and-contributions"></a>
## Help and contributions

- Source code: <https://github.com/lambdaql/lambdaql>
- The Korean manual is the source. The other languages are translations.
- If you find a mistake, please open an issue or a pull request.
- Design documents (Korean, for contributors): [ROADMAP.md](../../../design/ROADMAP.md), [KOTLIN.md](../../../design/KOTLIN.md)
