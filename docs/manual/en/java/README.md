# lambdaQL Manual: Java track

**Other languages:** English | [한국어](../../ko/java/README.md) | [日本語](../../ja/java/README.md) | [简体中文](../../zh/java/README.md)

> [!IMPORTANT]
> **Status:** lambdaQL is in early development.
> The **analysis core**, **`where` + `toList()`** and the core of **sorting and paging** (`orderBy`, `limit`, `offset`) are implemented: lambdaQL loads a lambda, decodes it, writes the HQL and runs it. This is tested on H2, MariaDB and Oracle.
> Everything else in this track (subqueries, `select`, update/insert/delete, procedures, the Spring module) is **designed**: the syntax and rules are decided and were checked with prototypes on H2, MariaDB and Oracle, but the code is not in the library yet.
> No artifact is published yet. To try it, [build from source](02-getting-started.md#build-from-source).

[Manual home](../README.md) | [Kotlin track](../kotlin/README.md) | Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md)

This is the **Java track** of the lambdaQL manual. Every page in this track has **Java code only**.
If you write your queries in Kotlin, read the [Kotlin track](../kotlin/README.md). It has the same chapters with the same numbers.

lambdaQL is a query library for Java and Kotlin.
You write normal lambdas. lambdaQL reads the bytecode of the lambda and builds a Hibernate 6.6 HQL query.
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

**About the SQL blocks.** `…` is the shortened select list (all entity columns), and `?` is a bind parameter. The lines come from [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java). It runs each query on H2 2.3, MariaDB 10.11 and Oracle 23.26 with Hibernate 6.6.5, and it fails if Hibernate's SQL changes.

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
> Examples are design examples. When a feature is implemented, its examples are replaced by code taken from the test suite, with a link to the test file. In [3. Conditions](03-where.md) and [4. Sorting and paging](04-order-paging.md), these examples are marked **(tested)**.

## Contents

The pages marked "common" are shared by the Java and Kotlin tracks. They have small examples in both languages, in separate sections.

| # | Page | What you learn |
|---|---|---|
| 1 | [Overview](../01-overview.md) (common) | What lambdaQL is, why, and how it works |
| 2 | [Getting started](02-getting-started.md) | Requirements, build from source, entities, `QueryBuilder`, first query |
| 3 | [Conditions (`where`)](03-where.md) | Operators, NULL rules, captured values, association paths |
| 4 | [Sorting and paging](04-order-paging.md) | `orderBy`, `Direction`, NULL order, `limit`, `offset` |
| 5 | [Subqueries](05-subquery.md) | `exists`, `contains` (IN), NOT EXISTS, scalar and correlated subqueries |
| 6 | [Select, aggregates and group by](06-select-group.md) | `select`, records and DTOs, `Agg`, `groupBy`, `having`, derived tables, CTE |
| 7 | [Update, insert, delete](07-dml.md) | `set`, `by.set`, `values`, `all()`, `execute()` |
| 8 | [Procedures and DB functions](08-procedures-functions.md) | `@SqlProcedure`, `@SqlFunction`, `qb.call`, `qb.exec` |
| 9 | [Java notes](09-java-notes.md) | Boxing, `==` on objects, `String` compare, `Objects.equals`, method references, `this` capture, compiler notes |
| 10 | [Rules and limits](10-rules-and-limits.md) | All rule tables, Java and SQL differences, database differences, what is not supported |
| 11 | [Errors and languages](../11-errors-and-languages.md) (common) | Error codes, exceptions, message language, logging |
| 12 | [Spring Data JPA](12-spring-data-jpa.md) | `LambdaQlExecutor<T>`, repository default methods, modules, transactions |

## Status

| Feature | Status | Chapter |
|---|---|---|
| Lambda loading (find the lambda body, read its bytecode) | Implemented, unit tested | [1](../01-overview.md) |
| Condition decoding (`&&`, `\|\|`, `!`, comparisons, captured values) | Implemented, unit tested | [3](03-where.md) |
| Mapping getters to JPA attributes | Implemented, tested on H2, MariaDB, Oracle | [3](03-where.md) |
| HQL rendering and running queries (`where`, `toList()`) | Implemented, tested on H2, MariaDB, Oracle | [3](03-where.md) |
| Sorting and paging (`orderBy`, `limit`, `offset`) | Core implemented, tested on H2, MariaDB, Oracle. `SortSpec`, `SortWhitelist` and join sorts: designed | [4](04-order-paging.md) |
| Subqueries (`exists`, `contains`, scalar, correlated) | Designed | [5](05-subquery.md) |
| Projection, `groupBy`, `having`, aggregates (`Agg`) | Designed | [6](06-select-group.md) |
| Derived tables and CTE (`qb.from(sub -> ...)`, `qb.with(...)`) | Designed | [6](06-select-group.md) |
| `update`, `insert`, `delete` | Designed | [7](07-dml.md) |
| Stored procedures and DB functions | Designed | [8](08-procedures-functions.md) |
| Error codes and messages in 4 languages | Designed | [11](../11-errors-and-languages.md) |
| Spring Data JPA (`LambdaQlExecutor<T>`, module `lambdaql-spring-data-jpa`) | Designed | [12](12-spring-data-jpa.md) |

What the words mean:

| Word | Meaning |
|---|---|
| Implemented | The code is in the repository and has unit tests. |
| In progress | Some code exists, but it is not finished. |
| Designed | The syntax and rules are decided. Prototypes were tested. The library does not have the code yet. |
| Planned | The idea is known, but the details are not decided. |

## Example entities

All examples use these three entities. The full code is in [Getting started](02-getting-started.md#example-entities).

| Entity | Fields |
|---|---|
| `Order` | `id`, `price`, `status`, `active`, `description`, `customer` (to `Customer`), `updateAt`, `items` (to many `Item`) |
| `Customer` | `id`, `name`, `grade` |
| `Item` | `order` (to `Order`), `price`, `qty` |

## Supported environment

| Item | Version |
|---|---|
| Java | 21 |
| Compiler | javac (ECJ, the Eclipse compiler, is not guaranteed) |
| Hibernate ORM | 6.6 (the design was verified with 6.6.5) |
| Databases verified | H2 2.3, MariaDB 10.11, Oracle 23.26 (23ai) |

## Help and contributions

- Source code: <https://github.com/codebit0/lambdaql>
- The English manual is the source. The Korean, Japanese and Chinese manuals are translations.
- If you find a mistake, please open an issue or a pull request.
- Design documents (Korean, for contributors): [ROADMAP.md](../../../design/ROADMAP.md), [KOTLIN.md](../../../design/KOTLIN.md)
