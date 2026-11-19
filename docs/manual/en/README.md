# lambdaQL User Manual (English)

**Other languages:** English | [한국어](../ko/README.md) | [日本語](../ja/README.md) | [简体中文](../zh/README.md)

lambdaQL is a query library for Java and Kotlin.
You write normal lambdas. lambdaQL reads the bytecode of the lambda and builds a Hibernate 6.6 HQL query.
The lambda is never run.

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

### HQL (the same for both)

```
select e0 from Order e0
where e0.status = 1 and e0.price > :p1
order by e0.price desc nulls last, e0.id asc
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

## Common pages

These pages are for both languages. They show code in separate Java and Kotlin sections.

| Page | What it has |
|---|---|
| [1. Overview](01-overview.md) | What lambdaQL is, why it exists, how it works, design principles |
| [11. Errors and languages](11-errors-and-languages.md) | Error codes, exceptions, message language, logging |

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

## Status

| Feature | Status | Java | Kotlin |
|---|---|---|---|
| Lambda loading (find the lambda body, read its bytecode) | Implemented, unit tested | [01](01-overview.md#how-it-works-5-steps) | [01](01-overview.md#how-it-works-5-steps) |
| Condition decoding (`&&`, `\|\|`, `!`, comparisons, captured values) | Implemented, unit tested (Java lambdas) | [03](java/03-where.md) | [03](kotlin/03-where.md) |
| Mapping getters to JPA attributes, evaluating captured values | Implemented, tested on H2, MariaDB, Oracle | [03](java/03-where.md) | [03](kotlin/03-where.md) |
| HQL rendering and running queries (`where`, `toList()`) | Implemented, tested on H2, MariaDB, Oracle | [03](java/03-where.md) | [03](kotlin/03-where.md) |
| Result functions (`first()`, `one()`, `count()`, `exists()`, `toSet()`) | Implemented, tested on H2, MariaDB, Oracle (Java lambdas) | [02](java/02-getting-started.md#getting-the-result) | [02](kotlin/02-getting-started.md#getting-the-result) |
| Sorting and paging (`orderBy`, `limit`, `offset`) | Core implemented, tested on H2, MariaDB, Oracle, including `SortSpec`, join sorts `(o, c, by)` and the sort policy for outside sorts (rules in code, Spring Boot properties or `@Sortable`). The join form of `SortSpec`: designed | [04](java/04-order-paging.md) | [04](kotlin/04-order-paging.md) |
| Subqueries (`exists`, `contains`, scalar, correlated) | Implemented, tested on H2, MariaDB, Oracle (Java lambdas). The MariaDB `lateral` rewrite: not implemented yet | [05](java/05-subquery.md) | [05](kotlin/05-subquery.md) |
| Projection, `groupBy`, `having`, aggregates (`Agg`) | Implemented, tested on H2, MariaDB, Oracle (Java lambdas), including `distinct()` and the result functions. Sorting by an aggregate: v0.2 candidate | [06](java/06-select-group.md) | [06](kotlin/06-select-group.md) |
| Explicit joins (`join`, `leftJoin`, `crossJoin`, up to three entities) | Implemented, tested on H2, MariaDB, Oracle (Java lambdas), including `where`, `orderBy`, `select` and paging over all entities. Joins in DML and in subqueries: later | [06](java/06-select-group.md#68-explicit-joins) | [06](kotlin/06-select-group.md#68-explicit-joins) |
| Derived tables and CTE (`qb.from(valueQuery)`, `qb.with(valueQuery)`) | Implemented, tested on H2, MariaDB, Oracle (Java lambdas). Joins with a derived table and the same CTE twice: later | [06](java/06-select-group.md#69-derived-table-subquery-in-from) | [06](kotlin/06-select-group.md#69-derived-table-subquery-in-from) |
| `update`, `insert`, `delete`, upsert | Implemented, tested on H2, MariaDB, Oracle (Java lambdas), including `limit` and `executeOne()`. Subqueries (`sub`) in the `where` of update and delete and aggregates in `insert ... select`: implemented. Subqueries in `set` values: designed | [07](java/07-dml.md) | [07](kotlin/07-dml.md) |
| Stored procedures and DB functions | Implemented: DB functions in queries and alone, tested on H2, MariaDB, Oracle; procedures (`call`, `exec`, OUT, INOUT, result sets) tested on MariaDB and Oracle (H2 has no OUT parameters). Argument name check (tested on MariaDB and Oracle) and detection of Oracle package overloads (tested on Oracle): implemented. SARGable warnings for DB functions (`LQL-5001`, `LQL-5007`): implemented. Type checks of procedure parameters: not implemented yet | [08](java/08-procedures-functions.md) | [08](kotlin/08-procedures-functions.md) |
| Kotlin forms (`?.`, `?:`, `!!`, ranges, `when`, `in`) | `?.`, `!!`, `in` with a captured collection, data classes, `Pair`/`Triple`, property assignment: tested with Kotlin lambdas on H2, MariaDB, Oracle. `?:`, ranges, `when`, property references: designed | – | [09](kotlin/09-kotlin-notes.md) |
| Error codes and messages in 4 languages | Implemented, unit tested: `[LQL-xxxx]` codes, `LambdaQLException` and its subclasses, English, Japanese, Chinese and Korean messages, `lambdaql.locale`. Strict mode: not implemented yet | [11](11-errors-and-languages.md) | [11](11-errors-and-languages.md) |
| Spring Data JPA (`LambdaQlExecutor<T>`, Spring `Sort` / `Pageable`, Spring Boot auto-configuration) | Implemented, tested on H2, MariaDB, Oracle with Spring Boot 3.4.2 (Java), including the DML shortcuts (`deleteAll`, `updateOne`, ...) and the update, delete, insert and upsert builders. Spring Boot 4: not verified yet | [12](java/12-spring-data-jpa.md) | [12](kotlin/12-spring-data-jpa.md) |

What the words mean:

| Word | Meaning |
|---|---|
| Implemented | The code is in the repository and has unit tests. |
| In progress | Some code exists, but it is not finished. |
| Designed | The syntax and rules are decided. Prototypes were tested. The library does not have the code yet. |
| Planned | The idea is known, but the details are not decided. |

The tests are here. `SelectQueryTest`, `SortQueryTest`, `SubqueryTest`, `ProjectionTest`, `JoinQueryTest`, `DerivedQueryTest`, `DmlQueryTest`, `FunctionTest`, `ProcedureTest`, `KotlinSelectTest`, `KotlinQueryTest`, `LqlMessagesTest`, `ExecutorTest` and `AutoConfigurationTest` use the user API; the other tests check internal classes.

| Test | What it checks |
|---|---|
| [SelectQueryTest.java](../../../query/src/test/java/org/lambdaql/query/SelectQueryTest.java) | `qb.from(...).where(...)`: the HQL, the parameters and the rows on H2, MariaDB and Oracle |
| [SortQueryTest.java](../../../query/src/test/java/org/lambdaql/query/SortQueryTest.java) | `orderBy`, `limit`, `offset`: the HQL and the row order on H2, MariaDB and Oracle |
| [SubqueryTest.java](../../../query/src/test/java/org/lambdaql/query/SubqueryTest.java) | Subqueries in `where`: `exists`, `contains` (IN), NOT EXISTS, scalar, top-1, nested, and a delete with a subquery: the HQL and the rows on H2, MariaDB and Oracle |
| [ProjectionTest.java](../../../query/src/test/java/org/lambdaql/query/ProjectionTest.java) | `select`, records, `groupBy`, `having`, `Agg`, `distinct`, paging and `insert ... select` with `groupBy`: the HQL and the values on H2, MariaDB and Oracle |
| [JoinQueryTest.java](../../../query/src/test/java/org/lambdaql/query/JoinQueryTest.java) | `join`, `leftJoin`, `crossJoin` with up to three entities: conditions, sorting, `select`, repeated rows, `count` and paging over all entities, on H2, MariaDB and Oracle |
| [DerivedQueryTest.java](../../../query/src/test/java/org/lambdaql/query/DerivedQueryTest.java) | Derived tables and CTEs (`qb.from(valueQuery)`, `qb.with(valueQuery)`): the HQL and the rows on H2, MariaDB and Oracle, the inner `limit`, and the row type rule J5 |
| [DmlQueryTest.java](../../../query/src/test/java/org/lambdaql/query/DmlQueryTest.java) | `update`, `delete`, `insert`, upsert: the HQL and the rows after the run on H2, MariaDB and Oracle, compared with a select with the same condition |
| [FunctionTest.java](../../../query/src/test/java/org/lambdaql/query/FunctionTest.java) | `@SqlFunction` DB functions in `where`, `select`, `orderBy` and update `set`, a Boolean function, a registered function and the rule checks, on H2, MariaDB and Oracle |
| [ProcedureTest.java](../../../query/src/test/java/org/lambdaql/query/ProcedureTest.java) | `@SqlProcedure` calls with `qb.call` and `qb.exec` (OUT, many OUT, INOUT, result set and REF CURSOR) and the stub check on MariaDB and Oracle; a DB function called alone on H2, MariaDB and Oracle |
| [KotlinSelectTest.kt](../../../query/src/test/kotlin/org/lambdaql/query/KotlinSelectTest.kt) | Kotlin lambdas in `where`: `?.` paths, captured values, `in` with a captured collection |
| [KotlinQueryTest.kt](../../../query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt) | Kotlin lambdas: a sort, subqueries, data class, `Pair` and `Triple` results, `groupBy` and `having`, update (property assignment and `by[column] = value`), insert and delete |
| [LqlMessagesTest.java](../../../query/src/test/java/org/lambdaql/i18n/LqlMessagesTest.java) | Message codes and the four message languages: the same keys and arguments in every bundle, unique codes, the locale choice, exception classes by code range |
| [ExecutorTest.java](../../../spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java) | `LambdaQlExecutor<T>` in a Spring Boot application: the repository methods, Spring `Sort` and `Pageable`, transactions |
| [AutoConfigurationTest.java](../../../spring-data-jpa/src/test/java/org/lambdaql/spring/AutoConfigurationTest.java) | The `QueryBuilder` bean of the Spring Boot auto-configuration |
| [LoadExamplesTest.java](../../../query/src/test/java/org/lambdaql/engine/load/LoadExamplesTest.java) | Find the lambda body, read captured values, reject a lambda that is not serializable |
| [ConditionDecodeTest.java](../../../query/src/test/java/org/lambdaql/engine/decode/ConditionDecodeTest.java) | Rebuild `&&`, `\|\|`, `!` and comparisons from the bytecode, reject a value ternary |
| [IrExamplesTest.java](../../../query/src/test/java/org/lambdaql/engine/ir/IrExamplesTest.java) | The internal condition tree (comparison operators, AND, OR, NOT) |
| [NegationMinimizerTest.java](../../../query/src/test/java/org/lambdaql/engine/decode/NegationMinimizerTest.java) | Push `!` into comparisons (De Morgan) without changing the result |
| [HostEvaluatorTest.java](../../../query/src/test/java/org/lambdaql/engine/sem/HostEvaluatorTest.java) | Compute values from captured variables in Java before binding |
| [JpaEntityModelTest.java](../../../query/src/test/java/org/lambdaql/engine/sem/JpaEntityModelTest.java) | Read entity properties from the JPA Metamodel (first code for mapping getters) |
| [AssignmentReaderTest.java](../../../query/src/test/java/org/lambdaql/engine/sem/AssignmentReaderTest.java) | Read assignment lambdas (setters and `by.set`) into column and value pairs, reject rule breaks (U1, U3, U6) |

## About the examples

Examples are design examples. When a feature is implemented, its examples are replaced by code taken from the test suite, with a link to the test file. In 3. Conditions, 4. Sorting and paging, 5. Subqueries, 6. Select, aggregates and group by, 7. Update, insert, delete, 8. Procedures and DB functions, 11. Errors and languages and 12. Spring Data JPA (Java), these examples are marked **(tested)**. The Kotlin track marks the examples from the Kotlin tests the same way.

## Example entities

All examples in this manual use these three entities.

| Entity | Fields |
|---|---|
| `Order` | `id`, `price`, `status`, `active`, `description`, `customer` (to `Customer`), `updateAt`, `items` (to many `Item`) |
| `Customer` | `id`, `name`, `grade` |
| `Item` | `order` (to `Order`), `price`, `qty` |

The full entity code is in Getting started: [Java](java/02-getting-started.md#example-entities) | [Kotlin](kotlin/02-getting-started.md#example-entities).

## Supported environment

| Item | Version |
|---|---|
| Java | 21 |
| Kotlin | 2.x |
| Hibernate ORM | 6.6 (the design was verified with 6.6.5) |
| Databases verified | H2 2.3, MariaDB 10.11, Oracle 23.26 (23ai) |
| Spring Boot (optional) | 3.4 (tested with 3.4.2). Spring Boot 4 is not verified yet |

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

## Help and contributions

- Source code: <https://github.com/codebit0/lambdaql>
- The English manual is the source. The Korean, Japanese and Chinese manuals are translations.
- If you find a mistake in any language, please open an issue or a pull request. Help with translations is welcome.
- Design documents (Korean, for contributors): [ROADMAP.md](../../design/ROADMAP.md), [KOTLIN.md](../../design/KOTLIN.md)
