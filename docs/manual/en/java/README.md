<a id="lambdaql-manual-java-track"></a>
# lambdaQL Manual: Java track

> [!IMPORTANT]
> This page is older than the Korean manual. See the [Korean page](../../ko/java/README.md) for the latest rules.

**Other languages:** English | [한국어](../../ko/java/README.md) | [日本語](../../ja/java/README.md) | [简体中文](../../zh/java/README.md)

[Manual home](../README.md) | [Kotlin track](../kotlin/README.md) | Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md)

This is the **Java track** of the lambdaQL manual. Every page in this track has **Java code only**.
If you write your queries in Kotlin, read the [Kotlin track](../kotlin/README.md). It has the same chapters with the same numbers.

lambdaQL is a query library for Java and Kotlin.
You write normal lambdas. lambdaQL reads the bytecode of the lambda and builds a Hibernate 7 HQL query.
The lambda is never run.

```java
List<Order> orders = qb.from(Order.class)
    .where(o -> o.getStatus() == 1 && o.getPrice() > minPrice)
    .orderBy((o, by) -> by.desc(o.getPrice()).asc(o.getId()))
    .limit(20)
    .toList();
// HQL: select e0 from Order e0
//      where e0.status = 1 and e0.price > :p1
//      order by e0.price desc nulls last, e0.id asc
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

> [!NOTE]
> **About the examples.**
> Examples are design examples. When a feature is implemented, its examples are replaced by code taken from the test suite, with a link to the test file. In [3. Conditions](03-where.md), [4. Sorting and paging](04-order-paging.md), [5. Subqueries](05-subquery.md), [6. Select, aggregates and group by](06-select-group.md), [7. Update, insert, delete](07-dml.md), [8. Procedures and DB functions](08-procedures-functions.md), [11. Errors and languages](../11-errors-and-languages.md) and [12. Spring Data JPA](12-spring-data-jpa.md), these examples are marked **(tested)**.

<a id="contents"></a>
## Contents

The pages marked "common" are shared by the Java and Kotlin tracks. They have small examples in both languages, in separate sections.

| # | Page | What you learn |
|---|---|---|
| 1 | [Overview](../01-overview.md) (common) | What lambdaQL is, why, and how it works |
| 2 | [Getting started](02-getting-started.md) | Requirements, build from source, entities, `QueryBuilder`, first query |
| 3 | [Conditions (`where`)](03-where.md) | Operators, NULL rules, captured values, association paths |
| 4 | [Sorting and paging](04-order-paging.md) | `orderBy`, `Direction`, NULL order, `limit`, `offset` |
| 5 | [Subqueries](05-subquery.md) | `exists`, `contains` (IN), NOT EXISTS, scalar and correlated subqueries |
| 6 | [Select, aggregates and group by](06-select-group.md) | `select`, records and DTOs, `Agg`, `groupBy`, `having`, joins (`join`, `leftJoin`, `crossJoin`), derived tables, CTE |
| 7 | [Update, insert, delete](07-dml.md) | `set`, `Dml.value`, `values`, `all()`, `execute()`, `limit`, `executeOne()` |
| 7.6 | [Upsert](07-6-upsert.md) | `upsert`, `onConflict(...).update(...)`, `onConflict(...).ignore()`, row counts per database |
| 8 | [Procedures and DB functions](08-procedures-functions.md) | `@SqlProcedure`, `@SqlFunction`, `qb.call`, `qb.exec` |
| 9 | [Java notes](09-java-notes.md) | Boxing, `==` on objects, `String` compare, `Objects.equals`, method references, `this` capture, compiler notes |
| 10 | [Rules and limits](10-rules-and-limits.md) | All rule tables, Java and SQL differences, database differences, what is not supported |
| 11 | [Errors and languages](../11-errors-and-languages.md) (common) | Error codes, exceptions, message language, logging |
| 12 | [Spring Data JPA](12-spring-data-jpa.md) | `LambdaQlExecutor<T>`, repository default methods, DML shortcuts (`deleteAll`, `updateOne`, ...), Spring `Sort` and `Pageable`, modules, transactions, `QueryBuilder` bean |

<a id="example-entities"></a>
## Example entities

All examples use these three entities. The full code is in [Getting started](02-getting-started.md#example-entities).

| Entity | Fields |
|---|---|
| `Order` | `id`, `price`, `status`, `active`, `description`, `customer` (to `Customer`), `updateAt`, `items` (to many `Item`) |
| `Customer` | `id`, `name`, `grade` |
| `Item` | `order` (to `Order`), `price`, `qty` |

<a id="supported-environment"></a>
## Supported environment

| Item | Version |
|---|---|
| Java | 21 |
| Compiler | javac (ECJ, the Eclipse compiler, is not guaranteed) |
| Hibernate ORM | 7.4 (tested with 7.4.13 from Spring Boot 4.1.2) |
| Databases verified | H2 2.3, MariaDB 10.11, Oracle 23.26 (23ai) |
| Spring Boot (optional) | 4.1 (tested with 4.1.2). Spring Boot 3 is not verified in this edition |

<a id="help-and-contributions"></a>
## Help and contributions

- Source code: <https://github.com/codebit0/lambdaql>
- The Korean manual is the source. The English, Japanese and Chinese manuals are translations.
- If you find a mistake, please open an issue or a pull request.
- Design documents (Korean, for contributors): [ROADMAP.md](../../../design/ROADMAP.md), [KOTLIN.md](../../../design/KOTLIN.md)
