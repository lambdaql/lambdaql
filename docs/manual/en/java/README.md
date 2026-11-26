<a id="lambdaql-manual-java-track"></a>
# lambdaQL Manual: Java track

**Other languages:** English | [한국어](../../ko/java/README.md) | [日本語](../../ja/java/README.md) | [简体中文](../../zh/java/README.md)

[Manual home](../README.md) | [Kotlin track](../kotlin/README.md) | Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md) · [13. Spring Boot 3 and 4](../13-spring-boot-versions.md)

This is the **Java track** of the lambdaQL manual. Every page in this track has **Java code only**.
If you write your queries in Kotlin, read the [Kotlin track](../kotlin/README.md). It has the same chapters with the same numbers.

lambdaQL is a query library for Java and Kotlin that runs on top of JPA and Hibernate.
You write the conditions and the sort order of a query as lambda expressions. lambdaQL reads the bytecode opcodes of the lambda expressions and builds an HQL query.

<!-- region: ManualJavaStartTest.java#readme -->
```java
import static org.lambdaql.query.By.desc;

double minPrice = 100;
OrderedSelectQuery<Order> query = qb.from(Order.class)
        .where(o -> o.getStatus() == 1 && o.getPrice() > minPrice)
        .orderBy(o -> desc(o.getPrice()).asc(o.getId()))
        .limit(20);
assertEquals("select e0 from Order e0 where e0.status = 1 and e0.price > :p1"
        + " order by e0.price desc, e0.id asc", query.toHql().hql());
List<Order> orders = query.toList();
```

`toHql()` returns the HQL without running the query. The string in `assertEquals` is the HQL that lambdaQL builds.
`price` and `id` are columns that cannot be NULL, so the sort has no `nulls` part.
`desc` and `asc` are sort functions of the class `By` (see [4.1](04-order-paging.md#41-one-lambda-for-the-whole-sort)).

> [!NOTE]
> **About the examples.**
> All example code is copied as it is from runnable Java code in the repository.
> The "Example code:" line at the end of each section links to the source file.
> In the SQL blocks by database, `…` is the shortened select list (all columns of the entity), and `?` is a bind parameter.

Example code: [`ManualJavaStartTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaStartTest.java) (`readme`).

<a id="contents"></a>
## Contents

Pages marked "common" are shared by the Java track and the Kotlin track. They have small examples in both languages, in separate sections.

| # | Page | What you learn |
|---|---|---|
| 1 | [Overview](../01-overview.md) (common) | What lambdaQL is, why you need it, how it works |
| 2 | [Getting started](02-getting-started.md) | Requirements, build from source, entities, `QueryBuilder`, first query |
| 3 | [Conditions (`where`)](03-where.md) | Operators, NULL rules, captured values, association paths |
| 4 | [Sorting and paging](04-order-paging.md) | `orderBy`, `Direction`, NULL ordering, `limit`, `offset` |
| 5 | [Subqueries](05-subquery.md) | `exists`, `contains` (IN), NOT EXISTS, scalar and correlated subqueries |
| 6 | [Select, aggregates and grouping](06-select-group.md) | `select`, records and DTOs, `Agg`, `groupBy`, `having`, joins (`join`, `leftJoin`, `crossJoin`), derived tables, CTE |
| 7 | [Update, insert, delete](07-dml.md) | `set`, `Dml.value`, `values`, `all()`, `execute()`, `limit`, `executeOne()` |
| 7.6 | [Upsert](07-6-upsert.md) | `upsert`, `onConflict(...).update(...)`, `onConflict(...).ignore()`, row counts by database |
| 8 | [Procedures and DB functions](08-procedures-functions.md) | `@SqlProcedure`, `@SqlFunction`, `qb.call`, `qb.exec` |
| 9 | [Java notes](09-java-notes.md) | Boxing, `==` on objects, `String` comparison, `Objects.equals`, method references, capturing `this`, compiler notes |
| 10 | [Rules and limits](10-rules-and-limits.md) | The full rule tables, differences between Java and SQL, differences by database, what is not supported |
| 11 | [Errors and languages](../11-errors-and-languages.md) (common) | Error codes, exceptions, message language, logging |
| 12 | [Spring Data JPA](12-spring-data-jpa.md) | `LambdaQlExecutor<T>`, repository default methods, DML shortcuts (`deleteAll`, `updateOne` and others), Spring `Sort` and `Pageable`, modules, transactions, `QueryBuilder` bean |
| 13 | [Spring Boot 3 and 4](../13-spring-boot-versions.md) (common) | Version table, artifacts (shared jars and the `-boot3` and `-boot4` starters), SQL differences between Hibernate 6.6 and 7.4, verified scope |

<a id="example-entities"></a>
## Example entities

The examples mostly use these three example entities. The property types and the source files are in [Getting started](02-getting-started.md#example-entities).

| Entity | Fields |
|---|---|
| `Order` | `id`, `price`, `status`, `active`, `description`, `kind`, `customer` (to `Customer`), `updateAt`, `total`, `cnt`, `custName`, `address` |
| `Customer` | `id`, `name`, `grade`, `orders` (to many `Order`) |
| `Item` | `id`, `order` (to `Order`), `price`, `qty` |

<a id="supported-environment"></a>
## Supported environment

| Item | Version |
|---|---|
| Java | 21 or later |
| Compiler | javac (ECJ, the Eclipse compiler, is not guaranteed) |
| Hibernate ORM | 6.6 or 7.4 (tested with 6.6.53 and 7.4.13) |
| Verified databases | H2 2.3, MariaDB 10.11, MySQL 8.4, PostgreSQL 17, Oracle 23.26 (23ai) |
| Spring Boot (optional) | 3.5 or 4.1 (tested with 3.5.16 and 4.1.2). See [13. Spring Boot 3 and 4](../13-spring-boot-versions.md) |

<a id="help-and-contributions"></a>
## Help and contributions

- Source code: <https://github.com/lambdaql/lambdaql>
- The Korean manual is the source. The English, Japanese and Chinese manuals are translations.
- If you find a mistake, please open an issue or a pull request.
- Design documents (Korean, for contributors): [ROADMAP.md](../../../design/ROADMAP.md), [KOTLIN.md](../../../design/KOTLIN.md)
