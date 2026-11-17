# 12. Spring Data JPA (Kotlin)

> [!NOTE]
> **Status: the query executor and the Spring Boot auto-configuration are implemented.**
> `LambdaQlExecutor<T>`, Spring `Sort` / `Pageable` support and the `QueryBuilder` bean are tested on H2, MariaDB 10.11 and Oracle 23.26 with Spring Boot 3.4.2. The tests are written in **Java**. **Kotlin repositories are not tested yet.**
> The update, delete, insert and upsert methods are tested too, in Java: the shortcuts (`deleteAll`, `deleteOne`, `updateAll`, `updateOne`, `insertOne`, `insertAll`, `upsertOne`, `upsertAll`) and the builders (`updateQuery()`, `deleteQuery()`, `insertQuery()`, `upsertQuery()`). They need the caller's transaction. lambdaQL does not add `@Transactional` (decision D41). The Kotlin forms of the shortcuts were compiled with kotlinc 2.3.21; there is no Kotlin test in the build yet.
> **Not verified yet:** Spring Boot 4.
> The Kotlin code on this page shows the same calls as the Java tests, in Kotlin syntax. The tested Java code is in [the Java version of this page](../java/12-spring-data-jpa.md).
> See the [Kotlin track README](README.md) for the full status.

[Kotlin track](README.md) | [Manual home](../README.md) | Previous: [11. Errors and languages](../11-errors-and-languages.md) | [Java version of this page](../java/12-spring-data-jpa.md)

---

## 12.1 What you get

With Spring Data JPA you write repository interfaces. lambdaQL adds one more interface, `LambdaQlExecutor<T>`.
Your repository extends it. Then you write your own query methods as **interface methods with a body**, with lambdaQL inside.
You do not write a `...RepositoryImpl` class for these methods.

| Part | What it does | Section |
|---|---|---|
| `LambdaQlExecutor<T>` | `findAll`, `findSet`, `findFirst`, `findOne`, `count`, `exists` and `query()` with a lambda condition, in every repository | [12.3](#123-repository-setup), [12.4](#124-executor-methods) |
| Spring `Sort` and `Pageable` | `findAll(where, sort)`, `findSet(where, sort)` and `findAll(where, pageable)`, which returns a Spring `Page` | [12.5](#125-spring-sort-and-pageable) |
| Update, delete, insert, upsert | Shortcuts such as `deleteAll { ... }` and `updateOne({ ... }) { ... }` run a statement in one call. `updateQuery()`, `deleteQuery()`, `insertQuery()`, `upsertQuery()` start the statements of [7. Update, insert, delete](07-dml.md) on the repository's entity. All need the caller's transaction | [12.4](#124-executor-methods), [12.6](#126-transactions-and-exceptions) |
| `QueryBuilder` bean | Spring Boot makes a `QueryBuilder` bean for your services | [12.8](#128-the-querybuilder-bean) |

## 12.2 Modules

lambdaQL has three artifacts. The core has no Spring dependency, so you can use it with plain JPA too.

| Artifact | What it has | You need it when |
|---|---|---|
| `lambdaql-core` | The engine and `QueryBuilder`. No Spring dependency | Always. The other two artifacts bring it |
| `lambdaql-spring-data-jpa` | `LambdaQlExecutor<T>`, its Spring Data registration, the Spring Boot auto-configuration (`QueryBuilder` bean) | You use Spring Data JPA without Spring Boot |
| `lambdaql-spring-boot-starter` | No code. It brings `lambdaql-spring-data-jpa` and `spring-boot-starter-data-jpa` | You use Spring Boot |

The artifacts are **not published to Maven Central yet**. Build them from source and publish them to your local Maven repository first (see [2. Getting started](02-getting-started.md#build-from-source)). The version is `1.0.0`.

With Spring Boot, Gradle (Kotlin DSL):

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("org.lambdaql:lambdaql-spring-boot-starter:1.0.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect")           // Spring Data needs it for Kotlin
}
```

Maven:

```xml
<dependency>
  <groupId>org.lambdaql</groupId>
  <artifactId>lambdaql-spring-boot-starter</artifactId>
  <version>1.0.0</version>
</dependency>
```

With Maven, add `kotlin-reflect` too (see [12.9](#129-kotlin-compile-settings)).

Without Spring Boot, add `lambdaql-spring-data-jpa` next to your own Spring Data JPA setup (Hibernate and a JDBC driver). It brings `lambdaql-core` and Spring Data JPA.

```kotlin
dependencies {
    implementation("org.lambdaql:lambdaql-spring-data-jpa:1.0.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
}
```

```xml
<dependency>
  <groupId>org.lambdaql</groupId>
  <artifactId>lambdaql-spring-data-jpa</artifactId>
  <version>1.0.0</version>
</dependency>
```

Requirements:

| Item | Version |
|---|---|
| Java (JVM) | 21 |
| Spring Boot | 3.4 or newer (tested with 3.4.2). Spring Boot 4 is not verified yet |
| Spring Data JPA | 3.4 or newer |
| Hibernate | 6.6 (Spring Boot 3.4 already brings it) |
| Kotlin | 2.2 or newer. For older Kotlin, see [12.9](#129-kotlin-compile-settings) |
| `kotlin-reflect` | Required by Spring Data for Kotlin |

Spring Data JPA 3.3 and older are **not supported**. lambdaQL registers its executor in `META-INF/spring.factories`, and Spring Data reads repository fragments from this file only from 3.4.

## 12.3 Repository setup

Add `LambdaQlExecutor<T>` to the supertype list of your repository. `T` is the entity of the repository.
You write no configuration and no `Impl` class.

The examples in this chapter use the entities `Member` (`id`, `name`, `active`, `grade`, `team`) and `Team` (`id`, `name`) of the Java tests. In Kotlin, `grade` is an `Int?`, so it can be NULL.

There are no Kotlin tests for the Spring module yet. The same repository written in Java is tested in [`MemberRepository.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/sample/MemberRepository.java) (H2, MariaDB and Oracle).

```kotlin
interface MemberRepository : JpaRepository<Member, Long>, LambdaQlExecutor<Member> {

    fun activeMembers(): List<Member> =
        findAll { it.active }

    fun byName(name: String): Optional<Member> =
        findOne { it.name == name }

    fun topGraded(n: Int): List<Member> =
        query()
            .where { it.active }
            .orderBy { m, by -> by.desc(m.grade).asc(m.id) }
            .limit(n)
            .toList()

    // update, delete, insert: call inside a transaction
    fun gradeInactive(grade: Int): Int =
        updateQuery().set { it.grade = grade }.where { !it.active }.execute()

    fun deleteWithoutTeam(): Int =
        deleteQuery().where { it.team == null }.execute()

    fun addGuest(name: String): Int =
        insertQuery().values { it.name = name; it.active = false }.execute()
}
```

The last three methods run update, delete and insert statements. They need a transaction (see [12.6](#126-transactions-and-exceptions)).

How it works:

- **Spring Data runs interface methods with a body by itself.** It does not try to make a query from them. It just calls the body. lambdaQL does not change this. For older Kotlin, see [12.9](#129-kotlin-compile-settings).
- **`LambdaQlExecutor<T>` is a Spring Data repository fragment.** Its implementation is listed in `META-INF/spring.factories` of `lambdaql-spring-data-jpa`. Spring Data reads this file and adds the implementation to every repository that extends `LambdaQlExecutor<T>`.
- **One executor object is shared by all repositories.** Each call still uses the entity of its own repository. For example, `teams.count { ... }` on a `TeamRepository` counts `Team` rows (tested in Java, [`AutoConfigurationTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/AutoConfigurationTest.java)).

Rules:

| # | Rule |
|---|---|
| 1 | Your repository must extend `LambdaQlExecutor<T>` **directly** (in its own supertype list). |
| 2 | Do not put `@NoRepositoryBean` on an interface that extends `LambdaQlExecutor<T>` for this purpose. Spring Data then does not add the lambdaQL implementation. |
| 3 | Write your lambdaQL queries in **interface methods with a body** or in services. Methods without a body are still Spring Data query methods (`findByName` and so on). |
| 4 | Without Spring Boot, the repository part still works, because it is plain Spring Data. Only the `QueryBuilder` bean is not made for you (see [12.8](#128-the-querybuilder-bean)). |

## 12.4 Executor methods

`Where<T>` is the same condition lambda as in `where { ... }` (see [3. Conditions](03-where.md)). `SortSpec<T>` is a lambdaQL sort (see [4.10](04-order-paging.md#410-reusable-and-external-sorts)). `Sort`, `Pageable` and `Page` are the Spring Data types.

| Method | Returns | Notes |
|---|---|---|
| `query()` | `SelectQuery<T>` | Starts a query on the repository's entity. Use it for everything the short methods cannot do: more `where` calls, `limit`, `offset`, a run-time `Direction` |
| `from(Class<E>)` | `SelectQuery<E>` | Starts a query on another entity of the same persistence unit |
| `findAll(Where<T>)` | `List<T>` | All rows that match, in no fixed order |
| `findAll(Where<T>, SortSpec<T>)` | `List<T>` | All rows that match, sorted |
| `findAll(Where<T>, Sort)` | `List<T>` | Sorted with a Spring `Sort` (see [12.5](#125-spring-sort-and-pageable)). `Sort.unsorted()` gives no order |
| `findAll(Where<T>, Pageable)` | `Page<T>` | One page. The count query runs only when it is needed. `Pageable.unpaged()` gives all rows |
| `findSet(Where<T>)` | `SequencedSet<T>` | All rows that match, as a set, in no fixed order (like `findAll(Where<T>)`). No `distinct`. If the set is smaller than the number of rows: `IllegalStateException` (see [`toSet()`](02-getting-started.md#toset)) |
| `findSet(Where<T>, SortSpec<T>)` | `SequencedSet<T>` | All rows that match, as a set in the sort order |
| `findSet(Where<T>, Sort)` | `SequencedSet<T>` | The same, sorted with a Spring `Sort` (see [12.5](#125-spring-sort-and-pageable)) |
| `findFirst(Where<T>)` | `Optional<T>` | The matching row with the smallest id |
| `findFirst(Where<T>, SortSpec<T>)` | `Optional<T>` | The first row in that order. Reads one row |
| `findFirst(Where<T>, Sort)` | `Optional<T>` | The first row in the order of a Spring `Sort` |
| `findOne(Where<T>)` | `Optional<T>` | The only row that matches. 2 or more rows: `IncorrectResultSizeDataAccessException` |
| `count(Where<T>)` | `Long` | The number of rows that match |
| `exists(Where<T>)` | `Boolean` | `true` if a row matches. Reads one row at most and loads no entity |
| `deleteAll(Where<T>)` | `Int` | Deletes every row that matches, with one SQL statement (bulk). Not the same as Spring Data's `deleteAll()` (see [below](#deleteallwhere-is-not-spring-datas-deleteall)). Needs a transaction |
| `deleteOne(Where<T>)` | `Int` | Deletes the only row that matches, with the [one-row protection](#one-row-protection-deleteone-and-updateone). Returns 0 or 1. Needs a transaction |
| `updateAll(Where<T>, Assign<T>)` | `Int` | Changes every row that matches. The condition comes first, the assignment is the trailing lambda. Needs a transaction |
| `updateOne(Where<T>, Assign<T>)` | `Int` | Changes the only row that matches, with the one-row protection. Returns 0 or 1. Needs a transaction |
| `insertOne(Assign<T>)` | `Int` | Inserts one row. Returns 1. Needs a transaction |
| `insertAll(Collection<L>, AssignEach<T, L>)` | `Int` | Inserts one row per element (at most 500 values per statement, rule U13). Needs a transaction |
| `upsertOne(Assign<T>)` | `Int` | Inserts one row, or changes it if a row with the same `@Id` exists. Returns the row count of the database. Needs a transaction |
| `upsertAll(Collection<L>, AssignEach<T, L>)` | `Int` | Upserts one row per element (one statement per row). Needs a transaction |
| `updateQuery()` | `UpdateQuery<T>` | Starts an update: `updateQuery().set { ... }.where { ... }.execute()` (see [7.2](07-2-update.md)). Needs a transaction |
| `deleteQuery()` | `DeleteQuery<T>` | Starts a delete: `deleteQuery().where { ... }.execute()` (see [7.4](07-4-delete.md)). Needs a transaction |
| `insertQuery()` | `InsertQuery<T>` | Starts an insert: `insertQuery().values { ... }.execute()` (see [7.5](07-5-insert.md)). Needs a transaction |
| `upsertQuery()` | `UpsertQuery<T>` | Starts an upsert keyed by the `@Id` (see [7.6](07-6-upsert.md)). Needs a transaction |

The short methods are the same as `query()` with a result function: `findAll(w)` is `query().where(w).toList()`, `findSet(w)` is `query().where(w).toSet()`, `findFirst(w)` is `query().where(w).first()`, `findOne(w)` is `query().where(w).one()`, and so on. The result functions are explained in [Getting the result](02-getting-started.md#getting-the-result).
`findAll { ... }` and `count { ... }` do not clash with `findAll(Sort)`, `findAll(Example)` or `count()` from `JpaRepository`, because the argument types are different.
The methods return Java `Optional`. Use `.orElse(null)` if you want a nullable value.

**`query()`: the full query.** `topGraded` in 12.3 uses it.

In the next examples, `repository` is a `MemberRepository`. The same calls are tested in Java in [`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java) (regions `find-all-sort`, `find-first`, `find-one`, `count-exists`, `find-set`).

```kotlin
val byGrade = SortSpec.of(Member::class.java) { m, by -> by.desc(m.grade).asc(m.id) }
val active: List<Member> = repository.findAll({ it.active }, byGrade)

// the active member with the highest grade
val best: Optional<Member> = repository.findFirst({ it.active },
    SortSpec.of(Member::class.java) { m, by -> by.desc(m.grade) })

val kim: Optional<Member> = repository.findOne { it.name == "Kim" }
repository.findOne { it.active }        // two or more rows: Spring's IncorrectResultSizeDataAccessException

val activeCount: Long = repository.count { it.active }
val anyInTeamB: Boolean = repository.exists { it.team?.name == "B" }

val byName: SequencedSet<Member> = repository.findSet({ it.active }, Sort.by("name"))
val anyOrder: SequencedSet<Member> = repository.findSet { it.active }   // no fixed order, like findAll { }
```

### DML shortcuts

Eight methods run an update, delete, insert or upsert in one call:

| Shortcut | Same as |
|---|---|
| `deleteAll { where }` | `deleteQuery().where { where }.execute()` |
| `deleteOne { where }` | `deleteQuery().where { where }.executeOne()` |
| `updateAll({ where }) { set }` | `updateQuery().set { set }.where { where }.execute()` |
| `updateOne({ where }) { set }` | `updateQuery().set { set }.where { where }.executeOne()` |
| `insertOne { values }` | `insertQuery().values { values }.execute()` |
| `insertAll(rows) { m, r -> values }` | `insertQuery().values(rows) { m, r -> values }.execute()` |
| `upsertOne { values }` | `upsertQuery().values { values }.execute()` |
| `upsertAll(rows) { m, r -> values }` | `upsertQuery().values(rows) { m, r -> values }.execute()` |

- **The condition comes first.** The assignment is the trailing lambda: `updateAll({ !it.active }) { it.grade = 7 }`.
- **`All`** means every row that matches (delete, update), or every element of the list (insert, upsert).
- **`One`** means the one-row protection for delete and update (see the next part), and one row for insert and upsert.
- They need the caller's transaction (see [12.6](#126-transactions-and-exceptions)).

Interface methods with a body, as in 12.3. `gradeInactive` and `addGuest` here are the short forms of the methods in 12.3. `NewMember` is a `data class NewMember(val name: String, val grade: Int)`.

```kotlin
fun purgeWithoutTeam(): Int = deleteAll { it.team == null }
fun deleteByName(name: String): Int = deleteOne { it.name == name }
fun gradeInactive(grade: Int): Int = updateAll({ !it.active }) { it.grade = grade }
fun rename(id: Long, name: String): Int = updateOne({ it.id == id }) { it.name = name }
fun addGuest(name: String): Int = insertOne { it.name = name; it.active = false }
fun addMembers(rows: List<NewMember>): Int = insertAll(rows) { m, r -> m.name = r.name; m.grade = r.grade; m.active = true }
fun putMember(id: Long, name: String): Int = upsertOne { it.id = id; it.name = name; it.active = true }
```

These Kotlin forms were compiled with kotlinc 2.3.21 (a compile check). There is no Kotlin test in the build yet. The same calls are tested in Java in [`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java) (regions `dml-shortcuts` and `dml-one`).

### One-row protection: `deleteOne` and `updateOne`

`deleteOne { ... }` and `updateOne({ ... }) { ... }` never change more than one row:

1. lambdaQL reads the ids of the rows that match, at most 2.
2. No id: nothing changes. The result is 0.
3. 2 ids: nothing changes. The method throws `IncorrectResultSizeDataAccessException`.
4. 1 id: lambdaQL changes that row with `id = :p and (your condition)`. The result is 1, or 0 if the row stopped matching in the meantime.

The statement itself names one id, so it cannot change a second row, even if another transaction adds a matching row in the meantime. The protection does not rely on a rollback.
The core API has the same protection: `executeOne()` (see [7.4](07-4-delete.md#delete-exactly-one-row-executeone)).

```kotlin
val renamed = repository.updateOne({ it.id == kimId }) { it.name = "Kim2" }   // exactly one row
repository.deleteOne { it.active }   // two or more rows match: nothing changes, IncorrectResultSizeDataAccessException
```

### `deleteAll(where)` is not Spring Data's `deleteAll()`

| Method | From | How it deletes | Cascade, `@PreRemove` |
|---|---|---|---|
| `deleteAll()` | `CrudRepository` | Loads every entity, then removes them one by one | run |
| `deleteAll(Iterable)` | `CrudRepository` | Removes the given entities one by one | run |
| `deleteAll { ... }` (`Where<T>`) | `LambdaQlExecutor<T>` | One SQL `delete` statement (bulk) | **not run** |

`deleteAll { ... }` does not load the entities. Like every bulk statement, it does not change entities already loaded (rule U16).
Use it to delete many rows quickly. Use Spring Data's methods when you need cascade or `@PreRemove`.

The compiler picks the method by the argument: no argument, an `Iterable`, or a lambda. This was checked with kotlinc 2.3.21 and in the javac bytecode.

### Builders for complex statements

`updateQuery()`, `deleteQuery()`, `insertQuery()` and `upsertQuery()` start a full statement. Use them for what the shortcuts cannot do: many `set` calls, `all()`, `orderBy { ... }.limit(n)` (see [7.4](07-4-delete.md#delete-the-first-rows-limit)), `toHql()`, or `onConflict { ... }`.
The names end with `Query`, so they do not clash with `JpaRepository` methods such as `delete(...)`. The statements are the same as with `QueryBuilder` (see [7. Update, insert, delete](07-dml.md)). The rows are the same as a select with the same `where` lambda.
They need the caller's transaction, like the shortcuts. Like every JPA bulk statement, they do not change entities already loaded in the transaction.

The methods `gradeInactive`, `deleteWithoutTeam` and `addGuest` in 12.3 use them. The same methods are tested in Java in [`MemberRepository.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/sample/MemberRepository.java) (region `dml`), and the upsert in [`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java) (region `upsert`):

```kotlin
// teams is a TeamRepository, tx a Spring TransactionTemplate
val changed = tx.execute { teams.upsertQuery().values { it.id = teamId; it.name = "A2" }.execute() }
```

The upsert returns the row count of the database: 1 for an insert, and for an update 1 on H2 and Oracle but 2 on MariaDB (see [7.6](07-6-upsert.md#the-row-count)).

## 12.5 Spring `Sort` and `Pageable`

`findAll(where, sort)`, `findSet(where, sort)`, `findFirst(where, sort)` and `findAll(where, pageable)` take the Spring Data types.
lambdaQL turns each `Sort.Order` into one sort key with `SortSpec.ofPath` (see [4.10](04-order-paging.md#410-reusable-and-external-sorts)).

| Spring | In lambdaQL |
|---|---|
| A property name (`"grade"`, `"team.name"`) | Checked with Spring Data `PropertyPath`, the same as in Spring Data's own repositories. An unknown name gives `PropertyReferenceException`. The path must end at a basic value, not at an entity. A to-one path such as `team.name` uses a `left join`, so rows without a team are kept |
| `ASC` / `DESC` with `NullHandling.NATIVE` (the default) | The lambdaQL NULL rule: NULLs first for ascending, NULLs last for descending. The order is the same on every database (see [4.4](04-order-paging.md#44-null-ordering-is-the-same-on-every-database)) |
| `NullHandling.NULLS_FIRST` / `NULLS_LAST` | Used as written (`ASC_NULLS_FIRST`, `DESC_NULLS_LAST` and so on) |
| `ignoreCase()` | Not supported yet. It throws `UnsupportedOperationException` |
| `Sort.unsorted()` | No `order by` |

Spring Data JPA's own repositories leave the NULL position of `NATIVE` to the database, so the order there can be different on each database. lambdaQL uses its own rule instead.

Paging with `findAll(where, pageable)`:

- Page numbers start at 0, as in Spring Data. lambdaQL reads the page with `limit` and `offset`.
- The total comes from a `count` query. Spring skips it when the page alone shows the total (for example, the first page has fewer rows than the page size).
- When the sort keys do not contain the id, lambdaQL adds the id as the last key, so a row does not move between pages (see [Tie-breaker](04-order-paging.md#tie-breaker-automatic-id)). A page without a sort is ordered by the id.
- `Pageable.unpaged()` returns all rows in one page.

The same calls are tested in Java in [`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java) (regions `find-all-spring-sort` and `page`).

```kotlin
val rows: List<Member> = repository.findAll({ it.active },
    Sort.by(Sort.Order.desc("grade"), Sort.Order.asc("team.name"), Sort.Order.asc("id")))
repository.findAll({ it.active }, Sort.by("nickname"))   // no such property: PropertyReferenceException

// page 1 (the second page) of the active members by name; the total comes from a count query
val page: Page<Member> = repository.findAll({ it.active }, PageRequest.of(1, 2, Sort.by("name")))
```

**Sort from request parameters.** Spring MVC can make a `Pageable` from request parameters such as `?page=1&size=20&sort=name,desc`. Every persistent property is accepted, so a user can sort by any column. If that is a problem, check the property names against your own list of allowed names first.

## 12.6 Transactions and exceptions

The executor uses Spring's shared `EntityManager`, which is bound to the current transaction.

**lambdaQL puts no `@Transactional` on its methods, on purpose** (decision D41).
An update, delete, insert or upsert needs the caller's transaction. This is true for the shortcuts (`deleteAll`, `updateOne`, ...), the builders (`updateQuery()`, ...) and the `QueryBuilder` bean.
Put `@Transactional` on your service method, or use a `TransactionTemplate`. Without a transaction, the call fails before any SQL is sent.

| Case | What happens |
|---|---|
| A select inside `@Transactional` | lambdaQL runs in the same transaction. It sees changes that are not flushed yet. After a rollback, these changes are gone for lambdaQL too (tested in Java, `ExecutorTest`) |
| A select outside a transaction | Spring uses a new `EntityManager` for the call and closes it after the call, so the returned entities are detached. This is Spring's normal behavior; it is not tested by lambdaQL |
| An update, delete, insert or upsert inside `@Transactional` or a `TransactionTemplate` | It runs in that transaction and rolls back with it (tested in Java) |
| An update, delete, insert or upsert outside a transaction | It fails before any SQL is sent. Nothing changes (tested in Java, see the next table) |

Only `QueryBuilder(entityManagerFactory)` runs DML in its own transaction (see [7.8](07-dml.md#transactions)). Do not use it in a Spring application (see [12.8](#128-the-querybuilder-bean)).

Errors go through Spring's exception translation, the same as for other repository methods:

| Case | Exception |
|---|---|
| `findOne` matches 2 or more rows | `IncorrectResultSizeDataAccessException` (Spring), instead of JPA's `NonUniqueResultException` (tested in Java) |
| `deleteOne` or `updateOne` matches 2 or more rows | `IncorrectResultSizeDataAccessException`. Nothing is changed (tested in Java) |
| An unknown property in a Spring `Sort` | `PropertyReferenceException` (tested in Java) |
| `ignoreCase()` in a Spring `Sort` | `UnsupportedOperationException` (tested in Java) |
| An update, delete, insert or upsert without a transaction, in a repository call (a shortcut, or `execute()` inside a method with a body) | `InvalidDataAccessApiUsageException` (tested in Java), made by Spring from JPA's `TransactionRequiredException`. Outside a repository method (the `QueryBuilder` bean), JPA's `TransactionRequiredException` |
| An assignment lambda that breaks a rule (U1, U3, U4, U6) | `LambdaQLException` (for example `TranslationException`, `LQL-3042`), not translated (see [7.8](07-dml.md#rules-checked-at-analysis-time)) |

The `QueryBuilder` bean (12.8) is not a repository, so Spring does not translate its errors. There, `one()` and `executeOne()` with 2 or more rows throw `NonUniqueResultException`.

## 12.7 Other fragments and `JpaSpecificationExecutor`

`LambdaQlExecutor<T>` is one fragment among others. The Java test repository uses all of these at the same time (tested in Java, `ExecutorTest`):

| Used together with | Works | Notes |
|---|---|---|
| A hand-written fragment (`MemberRepositoryCustom` with `MemberRepositoryImpl`) | Yes | Spring Data finds the `Impl` class as usual |
| `JpaSpecificationExecutor<T>` | Yes (Java) | A lambdaQL lambda has one argument, a `Specification` lambda has three, so Java always picks the right `findAll`. With Kotlin lambdas this is not tested yet |
| The `JpaRepository` methods (`findAll()`, `count()`, `save(...)`) | Yes | Their argument types are different |

```kotlin
interface MemberRepository : JpaRepository<Member, Long>, LambdaQlExecutor<Member>,
    JpaSpecificationExecutor<Member>, MemberRepositoryCustom {
    // ...
}

class MemberRepositoryImpl(/* ... */) : MemberRepositoryCustom {   // still found by Spring Data
    // ...
}
```

## 12.8 The `QueryBuilder` bean

With Spring Boot, the auto-configuration `org.lambdaql.spring.boot.LambdaQlAutoConfiguration` makes a `QueryBuilder` bean. Inject it into your services for queries that do not belong to one repository.

- The bean uses Spring's shared, transaction-bound `EntityManager`. Its queries join the current transaction, the same as the repository methods.
- The bean is made only when there is exactly one `EntityManagerFactory` and you did not define your own `QueryBuilder` bean.
- The repository methods (`LambdaQlExecutor<T>`) do not need this bean.
- Its update, delete, insert and upsert statements need the caller's transaction, the same as the repository methods. Without one, they throw `TransactionRequiredException` before any SQL is sent.

The bean is tested in Java in [`AutoConfigurationTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/AutoConfigurationTest.java) (region `query-builder-bean`): inside a transaction, a member that is not flushed yet is counted.

```kotlin
@Service
class MemberService(private val qb: QueryBuilder) {

    @Transactional(readOnly = true)
    fun countByName(name: String): Long =
        qb.from(Member::class.java).where { it.name == name }.count()
}
```

Without Spring Boot, or with two or more `EntityManagerFactory` beans, make the bean yourself. Use a shared `EntityManager`, as the auto-configuration does:

```kotlin
@Bean
fun queryBuilder(entityManagerFactory: EntityManagerFactory) =
    // the same as the auto-configuration: a shared, transaction-bound EntityManager
    QueryBuilder(SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory))
```

`SharedEntityManagerCreator` is a Spring class (`org.springframework.orm.jpa`). Do not use `QueryBuilder(entityManagerFactory)` in a Spring application: that builder opens its own `EntityManager` for each query, outside the Spring transaction.

**Tests with `@DataJpaTest`.** A `@DataJpaTest` slice test loads only some auto-configurations. The repository methods work there, but the `QueryBuilder` bean is not made. If a test needs it, add `@ImportAutoConfiguration(LambdaQlAutoConfiguration::class)` to the test class (not tested yet).

## 12.9 Kotlin compile settings

Spring Data runs a repository method with a body only when it is a JVM default method in the interface.

| Kotlin | What to do |
|---|---|
| 2.2 or newer | Nothing. A method with a body becomes a JVM default method by default. |
| Older than 2.2 | Add the compiler option `-Xjvm-default=all`. Without it, the method has no body in the interface. Spring Data then tries to make a query from its name, and the application does not start (`No property ... found for type ...`). |
| Every version | Add `kotlin-reflect`. Without it Spring Data fails with `ClassNotFoundException: kotlin.reflect.jvm.ReflectJvmMapping`. |

```kotlin
// build.gradle.kts, only for Kotlin older than 2.2
kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}
```

These settings were checked with a prototype: kotlinc 2.3.21, and `-jvm-default=disable` for the behavior of Kotlin older than 2.2. The lambdaQL test suite has no Kotlin repository yet.

## 12.10 Not implemented yet

- **Spring Boot 4.** The auto-configuration names the Spring Boot 4 classes too, but only Spring Boot 3.4.2 is tested.
- **`ignoreCase()`** in a Spring `Sort`.
- **Kotlin repositories** are not tested yet.

---

[Kotlin track](README.md) | Previous: [11. Errors and languages](../11-errors-and-languages.md) | [Java version of this page](../java/12-spring-data-jpa.md)
