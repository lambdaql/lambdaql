<a id="lambdaql-user-manual-english"></a>
# lambdaQL User Manual (English)

**Other languages:** English | [한국어](../ko/README.md) | [日本語](../ja/README.md) | [简体中文](../zh/README.md)

lambdaQL is a query library for Java and Kotlin.
You write normal lambdas. lambdaQL reads the bytecode of the lambda and builds a Hibernate 7 HQL query.
The lambda is never run.

<a id="a-first-look"></a>
## A first look

### Java

```java
List<Order> orders = qb.from(Order.class)
    .where(o -> o.getStatus() == 1 && o.getPrice() > minPrice)
    .orderBy((o, by) -> by.desc(o.getPrice()).asc(o.getId()))
    .limit(20)
    .toList();
```

### Kotlin

```kotlin
val orders = qb.from(Order::class.java)
    .where { it.status == 1 && it.price!! > minPrice }
    .orderBy { o, by -> by.desc(o.price).asc(o.id) }
    .limit(20)
    .toList()
```

<a id="hql-the-same-for-both"></a>
### HQL (the same for both)

```
select e0 from Order e0
where e0.status = 1 and e0.price > :p1
order by e0.price desc nulls last, e0.id asc
```

**About the SQL blocks.** `…` is the shortened select list (all entity columns), and `?` is a bind parameter. The lines come from [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java). It runs each query on H2 2.3, MariaDB 10.11 and Oracle 23.26 with Hibernate 7.4, and it fails if Hibernate's SQL changes.

**SQL by database (Hibernate's SQL for this HQL)** — the manual's `Order` has a nullable `Double price`, but the test `Order` does not, so `SqlByDatabaseTest` gives this HQL to Hibernate directly. From `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `readme-first`:

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

<a id="choose-your-track"></a>
## Choose your track

The manual has **two tracks**: one for Java and one for Kotlin.
Each track has only code in its own language. Pick the language you write your queries in.

| Track | Start here | For you if |
|---|---|---|
| **Java** | [Java track](java/README.md) | You write your queries in Java. |
| **Kotlin** | [Kotlin track](kotlin/README.md) | You write your queries in Kotlin. |

Both tracks use the same engine, so the same query gives the same HQL in both languages.
The two tracks have the same chapter numbers and file names. Only chapter 9 is different (Java notes or Kotlin notes).
Every track page has a link to the same page in the other track, so you can switch at any time.

<a id="common-pages"></a>
## Common pages

These pages are for both languages. They show code in separate Java and Kotlin sections.

| Page | What it has |
|---|---|
| [1. Overview](01-overview.md) | What lambdaQL is, why it exists, how it works, design principles |
| [11. Errors and languages](11-errors-and-languages.md) | Error codes, exceptions, message language, logging |
| [13. Spring Boot 3 and 4](13-spring-boot-versions.md) | Version table, artifacts, SQL differences between Hibernate 6.6 and 7.4, what is verified |

<a id="table-of-contents"></a>
## Table of contents

| # | Chapter | Java | Kotlin |
|---|---|---|---|
| 1 | Overview (common) | [01-overview.md](01-overview.md) | [01-overview.md](01-overview.md) |
| 2 | Getting started: requirements, build from source, first query | [Java](java/02-getting-started.md) | [Kotlin](kotlin/02-getting-started.md) |
| 3 | Conditions (`where`): operators, null rules, captured values | [Java](java/03-where.md) | [Kotlin](kotlin/03-where.md) |
| 4 | Sorting and paging: `orderBy`, `Direction`, `limit`, `offset` | [Java](java/04-order-paging.md) | [Kotlin](kotlin/04-order-paging.md) |
| 5 | Subqueries: `exists`, `contains`, scalar and correlated subqueries | [Java](java/05-subquery.md) | [Kotlin](kotlin/05-subquery.md) |
| 6 | Select, aggregates and group by: `select`, `groupBy`, `having`, `Agg`, joins (`join`, `leftJoin`, `crossJoin`), derived tables and CTE (`with`) | [Java](java/06-select-group.md) | [Kotlin](kotlin/06-select-group.md) |
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

Every code example is a part of the test code, copied as it is. The test file and the part name (region `name`) are written above the code. The tests check the HQL that lambdaQL makes and the rows it returns.
Examples of the Java track come from Java tests, and examples of the Kotlin track from Kotlin tests. Features that are not implemented yet are not in the manual; they are in the [ROADMAP](../../design/ROADMAP.md).

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
| Java | 21 |
| Kotlin | 2.x |
| Hibernate ORM | 6.6 or 7.4 (tested with 6.6.53 and 7.4.13) |
| Databases verified | H2 2.3, MariaDB 10.11, MySQL 8.4, PostgreSQL 17, Oracle 23.26 (23ai) |
| Spring Boot (optional) | 3.5 or 4.1 (tested with 3.5.16 and 4.1.2). See [13. Spring Boot 3 and 4](13-spring-boot-versions.md) |

<a id="folder-layout"></a>
## Folder layout

```
manual/en/
├── README.md                      this page
├── 01-overview.md                 common
├── 11-errors-and-languages.md     common
├── java/                          Java track (Java code only)
│   ├── README.md
│   ├── 02-getting-started.md ... 08-procedures-functions.md
│   ├── 09-java-notes.md
│   └── 10-rules-and-limits.md
└── kotlin/                        Kotlin track (Kotlin code only)
    ├── README.md
    ├── 02-getting-started.md ... 08-procedures-functions.md
    ├── 09-kotlin-notes.md
    └── 10-rules-and-limits.md
```

Every language folder (`en`, `ko`, `ja`, `zh`) has the same file names. To switch the language, change the folder name in the link.

<a id="help-and-contributions"></a>
## Help and contributions

- Source code: <https://github.com/codebit0/lambdaql>
- The Korean manual is the source. The English, Japanese and Chinese manuals are translations.
- If you find a mistake in any language, please open an issue or a pull request. Help with translations is welcome.
- Design documents (Korean, for contributors): [ROADMAP.md](../../design/ROADMAP.md), [KOTLIN.md](../../design/KOTLIN.md)
