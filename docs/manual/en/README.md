<a id="lambdaql-user-manual-english"></a>
# lambdaQL User Manual (English)

**Other languages:** English | [한국어](../ko/README.md) | [日本語](../ja/README.md) | [简体中文](../zh/README.md)

lambdaQL is a query library for Java and Kotlin that runs on top of JPA and Hibernate.
You write the conditions and the sort order of a query as lambda expressions. lambdaQL reads the bytecode opcodes of the lambda expressions and builds an HQL query.

<a id="a-first-look"></a>
## Example

### Java

<!-- region: ManualSharedTest.java#readme-first -->
```java
List<Order> orders = qb.from(Order.class)
        .where(o -> o.getStatus() == 1 && o.getPrice() > minPrice)
        .orderBy(o -> desc(o.getPrice()).asc(o.getId()))
        .limit(20)
        .toList();
```

### Kotlin

<!-- region: ManualKotlinSharedTest.kt#readme-first -->
```kotlin
val orders = qb.from(Order::class.java)
    .where { it.status.toInt() == 1 && it.price > minPrice }
    .orderBy { o -> desc(o.price).asc(o.id) }
    .limit(20)
    .toList()
```

<a id="hql-the-same-for-both"></a>
### HQL

```
select e0 from Order e0
where e0.status = 1 and e0.price > :p1
order by e0.price desc, e0.id asc
```

Example code: [`ManualSharedTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualSharedTest.java) (`readme-first`), [`ManualKotlinSharedTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSharedTest.kt) (`readme-first`).

<a id="choose-your-track"></a>
## Choose your track

This manual has **two tracks**: one for Java and one for Kotlin.  
Each track has only code in its own language. Pick the language you write your queries in.

| Track | Start page | For you if |
|---|---|---|
| **Java** | [Java track](java/README.md) | You write your queries in Java. |
| **Kotlin** | [Kotlin track](kotlin/README.md) | You write your queries in Kotlin. |

Both tracks use the same engine, so the same query gives the same HQL in both languages.

<a id="table-of-contents"></a>
## Table of contents

| # | Chapter | Java | Kotlin |
|---|---|---|---|
| 1 | Overview (common) | [01-overview.md](01-overview.md) | [01-overview.md](01-overview.md) |
| 2 | Getting started: requirements, build from source, first query | [Java](java/02-getting-started.md) | [Kotlin](kotlin/02-getting-started.md) |
| 3 | Conditions (`where`): operators, null rules, captured values | [Java](java/03-where.md) | [Kotlin](kotlin/03-where.md) |
| 4 | Sorting and paging: `orderBy`, `Direction`, `limit`, `offset` | [Java](java/04-order-paging.md) | [Kotlin](kotlin/04-order-paging.md) |
| 5 | Subqueries: `exists`, `contains`, scalar and correlated subqueries | [Java](java/05-subquery.md) | [Kotlin](kotlin/05-subquery.md) |
| 6 | Select, aggregates and grouping: `select`, `groupBy`, `having`, `Agg`, joins (`join`, `leftJoin`, `crossJoin`), derived tables and CTE (`with`) | [Java](java/06-select-group.md) | [Kotlin](kotlin/06-select-group.md) |
| 7 | Update, insert, delete: `set`, `values`, `all()`, `execute()`, `limit`, `executeOne()` | [Java](java/07-dml.md) | [Kotlin](kotlin/07-dml.md) |
| 7.6 | Upsert: `upsert`, `onConflict(...).update(...)`, `onConflict(...).ignore()` | [Java](java/07-6-upsert.md) | [Kotlin](kotlin/07-6-upsert.md) |
| 8 | Procedures and DB functions: `@SqlProcedure`, `@SqlFunction`, `qb.call`, `qb.exec` | [Java](java/08-procedures-functions.md) | [Kotlin](kotlin/08-procedures-functions.md) |
| 9 | Language notes | [Java notes](java/09-java-notes.md) | [Kotlin notes](kotlin/09-kotlin-notes.md) |
| 10 | Rules and limits: the full rule tables | [Java](java/10-rules-and-limits.md) | [Kotlin](kotlin/10-rules-and-limits.md) |
| 11 | Errors and languages (common) | [11-errors-and-languages.md](11-errors-and-languages.md) | [11-errors-and-languages.md](11-errors-and-languages.md) |
| 12 | Spring Data JPA: `LambdaQlExecutor<T>`, DML shortcuts, Spring `Sort` and `Pageable`, modules, transactions, `QueryBuilder` bean | [Java](java/12-spring-data-jpa.md) | [Kotlin](kotlin/12-spring-data-jpa.md) |
| 13 | Spring Boot 3 and 4 (common): versions, artifacts, SQL differences between Hibernate 6.6 and 7.4 | [13-spring-boot-versions.md](13-spring-boot-versions.md) | [13-spring-boot-versions.md](13-spring-boot-versions.md) |

<a id="about-the-examples"></a>
## About the examples

The example code is verification code that compares the run result and the HQL. Use the "Example code" link at the end of each section to see the full code.

**How to read the SQL blocks by database.** `…` is the shortened select list (all columns of the entity), and `?` is a bind parameter.

<a id="example-entities"></a>
## Example entities

All examples in this manual use these three entities.

| Entity | Fields |
|---|---|
| `Order` | `id`, `price`, `status`, `active`, `description`, `customer` (to `Customer`), `updateAt`, `items` (to many `Item`) |
| `Customer` | `id`, `name`, `grade` |
| `Item` | `order` (to `Order`), `price`, `qty` |

The full entity code is in Getting started: [Java](java/02-getting-started.md#example-entities) | [Kotlin](kotlin/02-getting-started.md#example-entities).

<a id="supported-environment"></a>
## Supported environment

| Item | Version |
|---|---|
| Java | 21 or later |
| Kotlin | 2.x or later |
| Hibernate ORM | 6.6 or 7.4 (tested with 6.6.53 and 7.4.13) |
| Verified databases | H2 2.3, MariaDB 10.11, MySQL 8.4, PostgreSQL 17, Oracle 23.26 (23ai) |
| Spring Boot (optional) | 3.5 or 4.1 (tested with 3.5.16 and 4.1.2). See [13. Spring Boot 3 and 4](13-spring-boot-versions.md) |

<a id="help-and-contributions"></a>
## Help and contributions

- Source code: <https://github.com/lambdaql/lambdaql>
- If you find a mistake in any language, please open an issue or a pull request. Help with translations is also welcome.
- Design documents (Korean, for contributors): [ROADMAP.md](../../design/ROADMAP.md), [KOTLIN.md](../../design/KOTLIN.md)
