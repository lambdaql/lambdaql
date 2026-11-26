<a id="12-spring-data-jpa-kotlin"></a>
# 12. Spring Data JPA (Kotlin)

[Kotlin track](README.md) | [Manual home](../README.md) | Previous: [11. Errors and languages](../11-errors-and-languages.md) | Next: [13. Spring Boot 3 and 4](../13-spring-boot-versions.md) | [Java version of this page](../java/12-spring-data-jpa.md)

---

<a id="121-what-you-get"></a>
## 12.1 What you get

In Spring Data JPA, you write repository interfaces. lambdaQL adds one interface to them: `LambdaQlExecutor<T>`.

Your repository extends this interface, and you write your own query methods as **interface methods with a body** that use lambdaQL.
You do not write a `...RepositoryImpl` class for these methods.

| Part | What it does | Section |
|---|---|---|
| `LambdaQlExecutor<T>` | Runs `findAll`, `findSet`, `findFirst`, `findOne`, `count`, `exists` and `query()` with a lambda condition on any repository | [12.3](#123-repository-setup), [12.4](#124-executor-methods) |
| Spring `Sort` and `Pageable` | `findAll(where, sort)`, `findSet(where, sort)`, and `findAll(where, pageable)` that returns a Spring `Page` | [12.5](#125-spring-sort-and-pageable) |
| Update, delete, insert, upsert | Shortcut methods such as `deleteAll { ... }` and `updateOne({ ... }) { ... }` run a statement in one call. `updateQuery()`, `deleteQuery()`, `insertQuery()` and `upsertQuery()` start the statements of [7. Update, insert, delete](07-dml.md) on the repository entity. All of them need the caller's transaction | [12.4](#124-executor-methods), [12.6](#126-transactions-and-exceptions) |
| The `QueryBuilder` bean | Spring Boot makes a `QueryBuilder` bean to use in services | [12.8](#128-the-querybuilder-bean) |

<a id="122-modules"></a>
## 12.2 Modules

The core does not depend on Spring, so you can also use it with plain JPA. `lambdaql` and `lambdaql-spring-data-jpa` are common to Spring Boot 3 and 4. Only the starters are separate for each Spring Boot version (name ending `-boot3`, `-boot4`, [13. Spring Boot 3 and 4](../13-spring-boot-versions.md)).

| Artifact | Contents | When you need it |
|---|---|---|
| `lambdaql` | The engine and `QueryBuilder`. No Spring dependency. Common to Spring Boot 3 and 4 | Always. The artifacts below bring it with them |
| `lambdaql-spring-data-jpa` | `LambdaQlExecutor<T>`, Spring Data registration, Spring Boot auto-configuration (the `QueryBuilder` bean). Common to Spring Data JPA 3.5 and 4.1. It uses the Spring Data JPA of your project | When you use Spring Data JPA without Spring Boot |
| `lambdaql-spring-boot-starter-boot4`, `lambdaql-spring-boot-starter-boot3` | No code. It brings `lambdaql-spring-data-jpa` and the `spring-boot-starter-data-jpa` of that Boot version | When you use Spring Boot (match the Boot version of your project) |

The artifacts are on Maven Central (groupId `io.github.lambdaql`, version `0.0.2`).

Gradle (Kotlin DSL) for Spring Boot 4. For Spring Boot 3, change the name ending to `-boot3`:

```gradle-kts
repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.lambdaql:lambdaql-spring-boot-starter-boot4:0.0.2")
    implementation("org.jetbrains.kotlin:kotlin-reflect")           // Spring Data needs it for Kotlin
}
```

Maven:

```xml
<dependency>
  <groupId>io.github.lambdaql</groupId>
  <artifactId>lambdaql-spring-boot-starter-boot4</artifactId>
  <version>0.0.2</version>
</dependency>
```

With Maven, also add `kotlin-reflect` (see [12.9](#129-kotlin-compile-settings)).

Without Spring Boot, add `lambdaql-spring-data-jpa` to your existing Spring Data JPA setup (Spring Data JPA, Hibernate, JDBC driver). This artifact brings `lambdaql` with it, and it uses the Spring Data JPA in your project (3.5 or 4.1).

```gradle-kts
dependencies {
    implementation("io.github.lambdaql:lambdaql-spring-data-jpa:0.0.2")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
}
```

```xml
<dependency>
  <groupId>io.github.lambdaql</groupId>
  <artifactId>lambdaql-spring-data-jpa</artifactId>
  <version>0.0.2</version>
</dependency>
```

Requirements:

| Item | Version |
|---|---|
| Java (JVM) | 21 or later |
| Spring Boot | 3.5 or 4.1 |
| Spring Data JPA | 3.5 or 4.1 |
| Hibernate | 6.6 or 7.4 (included in Spring Boot) |
| Kotlin | 2.2 or later. For older versions, see [12.9](#129-kotlin-compile-settings) |
| `kotlin-reflect` | Needed by the Kotlin support of Spring Data |

Spring Data JPA 3.3 or older is **not supported**. lambdaQL registers the executor in `META-INF/spring.factories`, and Spring Data reads repository fragments from this file from 3.4.

<a id="123-repository-setup"></a>
## 12.3 Repository setup

Add `LambdaQlExecutor<T>` to the list of supertypes of your repository. `T` is the entity of the repository.
You do not write extra configuration or an `Impl` class.

The examples in this chapter use the example entities `Member` (`id`, `name`, `active`, `grade`, `team`) and `Team` (`id`, `name`), written in Java, from Kotlin repositories. In Kotlin, `grade` is `Int?`, so it can be NULL, and `active` is the property `isActive`.

`Kt` in the example repository names is a prefix that tells them apart from the repositories of the Java track examples.

<!-- region: ManualKotlinRepositories.kt#repository -->
```kotlin
/** No Impl class: Spring Data finds the LambdaQlExecutor implementation by itself. */
interface KtMemberRepository : JpaRepository<Member, Long>, LambdaQlExecutor<Member> {

    fun activeMembers(): List<Member> =
        findAll { it.isActive }

    fun byName(name: String): Optional<Member> =
        findOne { it.name == name }

    fun topGraded(n: Int): List<Member> =
        query()
            .where { it.isActive }
            .orderBy { m -> desc(m.grade).asc(m.id) }
            .limit(n)
            .toList()

    // update, delete, insert: call inside a transaction
    fun gradeInactive(grade: Int): Int =
        updateQuery().set { it.grade = grade }.where { !it.isActive }.execute()

    fun deleteWithoutTeam(): Int =
        deleteQuery().where { it.team == null }.execute()

    fun addGuest(name: String): Int =
        insertQuery().values { it.name = name; it.isActive = false }.execute()
}
```

The last three methods run update, delete and insert statements. These methods need a transaction (see [12.6](#126-transactions-and-exceptions)).

How it works:

- **Spring Data runs interface methods with a body itself.** Spring Data does not make a query from these methods. It calls the body. lambdaQL does not change this behavior. For Kotlin older than 2.2, see [12.9](#129-kotlin-compile-settings).
- **`LambdaQlExecutor<T>` is a Spring Data repository fragment.** The implementation class is registered in `META-INF/spring.factories` of `lambdaql-spring-data-jpa`. Spring Data reads this file and adds the implementation to every repository that extends `LambdaQlExecutor<T>`.
- **One executor object is shared by all repositories.** Each call uses the entity of that repository. For example, `teams.count { ... }` on `TeamRepository` returns the number of `Team` rows.

Rules:

| # | Rule |
|---|---|
| 1 | The repository must extend `LambdaQlExecutor<T>` **directly** (in its own list of supertypes). |
| 2 | Do not put `@NoRepositoryBean` on an interface that extends `LambdaQlExecutor<T>` for this purpose. If you do, Spring Data does not add the lambdaQL implementation. |
| 3 | Write lambdaQL queries in **interface methods with a body** or in services. Methods without a body keep working as Spring Data query methods (`findByName` and so on). |
| 4 | Without Spring Boot, the repository part still works, because it is plain Spring Data. Only the `QueryBuilder` bean is not made automatically (see [12.8](#128-the-querybuilder-bean)). |

Example code: [`ManualKotlinRepositories.kt`](https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/kotlin/org/lambdaql/spring/ManualKotlinRepositories.kt) (`repository`).

<a id="124-executor-methods"></a>
## 12.4 Executor methods

`Where<T>` is a condition lambda, the same as `where { ... }` (see [3. Conditions](03-where.md)). `SortSpec<T>` is a lambdaQL sort (see [4.10](04-order-paging.md#410-reusable-and-external-sorts)).
`Sort`, `Pageable` and `Page` are Spring Data types.

| Method | Returns | Description |
|---|---|---|
| `query()` | `SelectQuery<T>` | Starts a query on the repository entity. Use it for queries that the short methods cannot express (many `where` calls, `limit`, `offset`, a `Direction` chosen at run time) |
| `from(Class<E>)` | `SelectQuery<E>` | Starts a query on another entity of the same persistence unit |
| `findAll(Where<T>)` | `List<T>` | All matching rows. The order is not defined |
| `findAll(Where<T>, SortSpec<T>)` | `List<T>` | All matching rows, sorted |
| `findAll(Where<T>, Sort)` | `List<T>` | Sorted by a Spring `Sort` (see [12.5](#125-spring-sort-and-pageable)). `Sort.unsorted()` means no sort |
| `findAll(Where<T>, Pageable)` | `Page<T>` | One page. The count query runs only when needed. `Pageable.unpaged()` gives all rows |
| `findSet(Where<T>)` | `SequencedSet<T>` | All matching rows as a Set. The order is not defined (the same as `findAll(Where<T>)`). No `distinct`. `IllegalStateException` if the Set is smaller than the number of rows (see [`toSet()`](02-getting-started.md#toset)) |
| `findSet(Where<T>, SortSpec<T>)` | `SequencedSet<T>` | All matching rows as a Set in sort order |
| `findSet(Where<T>, Sort)` | `SequencedSet<T>` | The same as above. Sorted by a Spring `Sort` (see [12.5](#125-spring-sort-and-pageable)) |
| `findFirst(Where<T>)` | `Optional<T>` | One of the matching rows. Not sorted |
| `findFirst(Where<T>, SortSpec<T>)` | `Optional<T>` | The first row in the given order. Reads only one row |
| `findFirst(Where<T>, Sort)` | `Optional<T>` | The first row in the order of a Spring `Sort` |
| `findOne(Where<T>)` | `Optional<T>` | The only matching row. `IncorrectResultSizeDataAccessException` if two or more rows match |
| `count(Where<T>)` | `Long` | The number of matching rows |
| `exists(Where<T>)` | `Boolean` | `true` if a row matches. Reads at most one row and does not load entities |
| `deleteAll(Where<T>)` | `Int` | Deletes all matching rows with one SQL statement (bulk). Different from Spring Data's `deleteAll()` (see [below](#deleteallwhere-is-not-spring-datas-deleteall)). Needs a transaction |
| `deleteOne(Where<T>)` | `Int` | Deletes the only matching row with [one-row protection](#one-row-protection-deleteone-and-updateone). Returns 0 or 1. Needs a transaction |
| `updateAll(Where<T>, Assign<T>)` | `Int` | Updates all matching rows. The condition comes first, and the assignment is a trailing lambda. Needs a transaction |
| `updateOne(Where<T>, Assign<T>)` | `Int` | Updates the only matching row with one-row protection. Returns 0 or 1. Needs a transaction |
| `insertOne(Assign<T>)` | `Int` | Inserts one row. Returns 1. Needs a transaction |
| `insertAll(Collection<L>, AssignEach<T, L>)` | `Int` | Inserts one row per element (at most 500 values per statement). Needs a transaction |
| `upsertOne(Assign<T>)` | `Int` | Inserts one row. If a row with the same `@Id` exists, updates that row. Returns the row count that the database reports. Needs a transaction |
| `upsertAll(Collection<L>, AssignEach<T, L>)` | `Int` | Upserts one row per element (one statement per row). Needs a transaction |
| `updateQuery()` | `UpdateQuery<T>` | Starts an update: `updateQuery().set { ... }.where { ... }.execute()` (see [7.2](07-2-update.md)). Needs a transaction |
| `deleteQuery()` | `DeleteQuery<T>` | Starts a delete: `deleteQuery().where { ... }.execute()` (see [7.4](07-4-delete.md)). Needs a transaction |
| `insertQuery()` | `InsertQuery<T>` | Starts an insert: `insertQuery().values { ... }.execute()` (see [7.5](07-5-insert.md)). Needs a transaction |
| `upsertQuery()` | `UpsertQuery<T>` | Starts an upsert with the `@Id` as the key (see [7.6](07-6-upsert.md)). Needs a transaction |

A short method is the same as `query()` with a result function. `findAll(w)` is `query().where(w).toList()`, `findSet(w)` is `query().where(w).toSet()`, `findFirst(w)` is `query().where(w).first()`, and `findOne(w)` is `query().where(w).one()`. The result functions are explained in [Getting the result](02-getting-started.md#getting-the-result).
`findAll { ... }` and `count { ... }` have different argument types, so they do not clash with `findAll(Sort)`, `findAll(Example)` and `count()` of `JpaRepository`.
The methods return a Java `Optional`.

**`query()`: a full query.** `topGraded` in 12.3 uses this method.

In the next example, `repository` is the `KtMemberRepository` of 12.3. The example data has 6 members (4 active, including a member whose grade is NULL).

<!-- region: ManualKotlinRepositoryTest.kt#executor -->
```kotlin
val byGrade = SortSpec.of(Member::class.java) { m -> desc(m.grade).asc(m.id) }
val active: List<Member> = repository.findAll({ it.isActive }, byGrade)

// the active member with the highest grade
val best: Optional<Member> = repository.findFirst({ it.isActive },
    SortSpec.of(Member::class.java) { m -> desc(m.grade) })

val kim: Optional<Member> = repository.findOne { it.name == "Kim" }
// two or more rows: Spring's IncorrectResultSizeDataAccessException
assertThrows<IncorrectResultSizeDataAccessException> { repository.findOne { it.isActive } }

val activeCount: Long = repository.count { it.isActive }
val anyInTeamB: Boolean = repository.exists { it.team?.name == "B" }

val byName: SequencedSet<Member> = repository.findSet({ it.isActive }, Sort.by("name"))
val anyOrder: SequencedSet<Member> = repository.findSet { it.isActive }   // no fixed order, like findAll { }
```

<a id="dml-shortcuts"></a>
### DML shortcuts

These 8 methods run an update, delete, insert or upsert in one call.

| Shortcut | Same statement |
|---|---|
| `deleteAll { where }` | `deleteQuery().where { where }.execute()` |
| `deleteOne { where }` | `deleteQuery().where { where }.executeOne()` |
| `updateAll({ where }) { set }` | `updateQuery().set { set }.where { where }.execute()` |
| `updateOne({ where }) { set }` | `updateQuery().set { set }.where { where }.executeOne()` |
| `insertOne { values }` | `insertQuery().values { values }.execute()` |
| `insertAll(rows) { m, r -> values }` | `insertQuery().values(rows) { m, r -> values }.execute()` |
| `upsertOne { values }` | `upsertQuery().values { values }.execute()` |
| `upsertAll(rows) { m, r -> values }` | `upsertQuery().values(rows) { m, r -> values }.execute()` |

- **The condition comes first.** Write the assignment as a trailing lambda: `updateAll({ !it.active }) { it.grade = 7 }`.
- **`All`** means all matching rows (delete, update) or all elements of the list (insert, upsert).
- **`One`** means one-row protection for delete and update (see the next section), and one row for insert and upsert.
- They need the caller's transaction (see [12.6](#126-transactions-and-exceptions)).

These are interface methods with a body, like 12.3. `gradeInactive` and `addGuest` here are the short forms of the 12.3 methods.

<!-- region: ManualKotlinRepositories.kt#shortcuts -->
```kotlin
/** A list element for insertAll. */
data class NewMember(val name: String, val grade: Int)

/** The DML shortcuts: one call runs the statement (inside the caller's transaction). */
interface KtMemberShortcutRepository : JpaRepository<Member, Long>, LambdaQlExecutor<Member> {

    fun purgeWithoutTeam(): Int = deleteAll { it.team == null }

    fun deleteByName(name: String): Int = deleteOne { it.name == name }

    fun gradeInactive(grade: Int): Int = updateAll({ !it.isActive }) { it.grade = grade }

    fun rename(id: Long, name: String): Int = updateOne({ it.id == id }) { it.name = name }

    fun addGuest(name: String): Int = insertOne { it.name = name; it.isActive = false }

    fun addMembers(rows: List<NewMember>): Int =
        insertAll(rows) { m, r -> m.name = r.name; m.grade = r.grade; m.isActive = true }
}
```

Call these methods inside a transaction (`@Transactional` or `TransactionTemplate`). Without a transaction, you get `InvalidDataAccessApiUsageException`.

<a id="one-row-protection-deleteone-and-updateone"></a>
### One-row protection: `deleteOne` and `updateOne`

`deleteOne { ... }` and `updateOne({ ... }) { ... }` never change two or more rows.

1. lambdaQL reads at most 2 ids of the rows that match the condition.
2. If there is no id, it changes nothing and returns 0.
3. If there are 2 ids, it changes nothing and throws `IncorrectResultSizeDataAccessException`.
4. If there is 1 id, it changes only that row with `id = :p and (condition)` and returns 1. If the row no longer matches the condition by then, it returns 0.

The statement itself names one id, so even if another transaction adds a matching row in the meantime, the second row is not changed. This protection does not depend on a rollback.

The core API has the same protection: `executeOne()` (see [7.4](07-4-delete.md#delete-exactly-one-row-executeone)).

Inside a `TransactionTemplate`:

<!-- region: ManualKotlinRepositoryTest.kt#dml-one -->
```kotlin
val renamed = repository.updateOne({ it.id == kimId }) { it.name = "Kim2" }   // exactly one row
// two or more rows match: nothing changes
assertThrows<IncorrectResultSizeDataAccessException> { repository.deleteOne { it.isActive } }
```

<a id="deleteallwhere-is-not-spring-datas-deleteall"></a>
### `deleteAll(where)` and Spring Data's `deleteAll()`

| Method | Belongs to | How it deletes | cascade, `@PreRemove` |
|---|---|---|---|
| `deleteAll()` | `CrudRepository` | Loads all entities, then deletes them one by one | Run |
| `deleteAll(Iterable)` | `CrudRepository` | Deletes the given entities one by one | Run |
| `deleteAll { ... }` (`Where<T>`) | `LambdaQlExecutor<T>` | One SQL `delete` statement (bulk) | **Not run** |

`deleteAll { ... }` does not load entities. Like other bulk statements, it is not reflected in entities that are already loaded.

Use it to delete many rows fast. If you need cascade or `@PreRemove`, use the Spring Data methods.

The compiler chooses the method by the argument (no argument, `Iterable`, a lambda). You can use `deleteAll()` and `deleteAll { ... }` (the shortcut) together in one repository.

<a id="builders-for-complex-statements"></a>
### Builders for complex statements

`updateQuery()`, `deleteQuery()`, `insertQuery()` and `upsertQuery()` start a full statement. Use them when a shortcut cannot express it: many `set` calls, `all()`, `orderBy { ... }.limit(n)` (see [7.4](07-4-delete.md#delete-the-first-rows-limit)), `toHql()`, `onConflict { ... }`.

The method names end with `Query`, so they do not clash with `JpaRepository` methods such as `delete(...)`. The statements are the same as those you build with `QueryBuilder` (see [7. Update, insert, delete](07-dml.md)). The target rows are the same as the rows selected with the same `where` lambda.

Like the shortcuts, they need the caller's transaction. Like all JPA bulk statements, they are not reflected in entities already loaded in the transaction.

The methods `gradeInactive`, `deleteWithoutTeam` and `addGuest` of 12.3 use these methods. Upsert is shown with `Team`, whose `@Id` can be assigned (`KtTeamRepository : JpaRepository<Team, Long>, LambdaQlExecutor<Team>`).

<!-- region: ManualKotlinRepositoryTest.kt#upsert -->
```kotlin
// teams is a KtTeamRepository, tx a Spring TransactionTemplate
val changed = tx.execute { teams.upsertQuery().values { it.id = teamId; it.name = "A2" }.execute() }
```

An upsert returns the row count that the database reports. It is 1 for an insert. For an update, it is 1 on H2 and Oracle, and 2 on MariaDB (see [7.6](07-6-upsert.md#the-row-count)).

Example code: [`ManualKotlinRepositoryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/kotlin/org/lambdaql/spring/ManualKotlinRepositoryTest.kt) (`executor`, `dml-one`, `upsert`), [`ManualKotlinRepositories.kt`](https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/kotlin/org/lambdaql/spring/ManualKotlinRepositories.kt) (`shortcuts`).

<a id="125-spring-sort-and-pageable"></a>
## 12.5 Spring `Sort` and `Pageable`

`findAll(where, sort)`, `findSet(where, sort)`, `findFirst(where, sort)` and `findAll(where, pageable)` take Spring Data types.
lambdaQL turns each `Sort.Order` into one sort key with `SortSpec.ofPath` (see [4.10](04-order-paging.md#410-reusable-and-external-sorts)).

| Spring | What lambdaQL does |
|---|---|
| Property name (`"grade"`, `"team.name"`) | Checked with Spring Data `PropertyPath`, as Spring Data repositories do. An unknown name gives `PropertyReferenceException` (package: `org.springframework.data.mapping` in Spring Data 3, `org.springframework.data.core` in 4). The end of the path must be a basic value, not an entity. A to-one path such as `team.name` uses a `left join`, so rows without a team are also in the result. Then the sort policy checks the name (see [below](#sort-policy-with-spring)) |
| An alias of the sort policy (`"level"`) | Passed as it is, without the `PropertyPath` check. The sort policy turns it into a path |
| `ASC` / `DESC` with `NullHandling.NATIVE` (default) | The lambdaQL NULL rule applies. Ascending puts NULL first, descending puts NULL last. The same order on every database (see [4.4](04-order-paging.md#44-null-ordering-is-the-same-on-every-database)) |
| `NullHandling.NULLS_FIRST` / `NULLS_LAST` | Used as given (`ASC_NULLS_FIRST`, `DESC_NULLS_LAST` and so on) |
| `ignoreCase()` | Not supported yet. Throws `UnsupportedOperationException` |
| `Sort.unsorted()` | No `order by` |

Spring Data JPA's own repositories leave the NULL position of `NATIVE` to the database, so the order can differ by database. lambdaQL applies its own rule.

Paging with `findAll(where, pageable)`:

- Page numbers start at 0, as in Spring Data. lambdaQL reads the page with `limit` and `offset`.
- The total count comes from a `count` query. If the page alone settles the total count (for example, when the first page has fewer rows than the page size), Spring skips the count query.
- If the sort keys have no id, lambdaQL adds the id as the last key, so rows do not move between pages (see [Tie-breaker](04-order-paging.md#tie-breaker-automatic-id)). A page without a sort gets no `order by`, so to split pages in a stable way, give a `Sort`.
- `Pageable.unpaged()` returns all rows as one page.

<!-- region: ManualKotlinRepositoryTest.kt#spring-sort -->
```kotlin
val rows: List<Member> = repository.findAll({ it.isActive },
    Sort.by(Sort.Order.desc("grade"), Sort.Order.asc("team.name"), Sort.Order.asc("id")))
// a property that does not exist: the same error as Spring Data's own repositories
val unknown = assertThrows<RuntimeException> { repository.findAll({ it.isActive }, Sort.by("nickname")) }
assertEquals("PropertyReferenceException", unknown.javaClass.simpleName)

// page 1 (the second page) of the active members by name; the total comes from a count query
val page: Page<Member> = repository.findAll({ it.isActive }, PageRequest.of(1, 2, Sort.by("name")))
```

**Sorts from request parameters.** Spring MVC can make a `Pageable` from request parameters such as `?page=1&size=20&sort=name,desc`.
If there is no sort policy and no `@Sortable` field, every persistent attribute is accepted, so a user can sort by any column. In this case lambdaQL logs `LQL-5003` at `INFO` level once per entity and name.
To allow only some names, use the sort policy below.

<a id="sort-policy-with-spring"></a>
### The sort policy with Spring

The sort policy decides which external sort names are allowed. `SortRules`, `SortMode` and the rules are explained in [4.10](04-order-paging.md#sort-policy).
A Spring `Sort` or `Pageable` is always an external sort, so the policy checks every name in it. Sort lambdas are not checked.

**Where the rules come from.** In Spring Boot, the auto-configuration makes the policy of the `EntityManagerFactory` and registers it after all singleton beans are made.
The auto-configuration reads three places.

| Source | What it gives |
|---|---|
| A `SortPolicy` bean | The mode and `SortRules` written as lambdas |
| The properties `lambdaql.sort.*` | The mode and rules written as strings |
| `@Sortable` fields ([4.10](04-order-paging.md#rules-from-sortable)) | Rules for entities that have no rules in the bean or the properties |

- The rules of one entity come from only one place, in the order of the table. Rules from two places are not merged.
- `lambdaql.sort.mode` wins over the mode of the bean. If neither exists, the mode is `INFO`.
- The policy is registered when there is exactly one `EntityManagerFactory`, the same as for the `QueryBuilder` bean (see [12.8](#128-the-querybuilder-bean)). In other cases, call `SortPolicy.register(emf, policy)` yourself.
- If there is no bean and no property, nothing is registered, so the default is used: `INFO`, with rules only from `@Sortable`.

**Properties.**

| Property | Meaning |
|---|---|
| `lambdaql.sort.mode` | `OFF`, `INFO` (default), `WARN`, `STRICT` |
| `lambdaql.sort.allow.<Entity>` | Allowed paths, separated by commas. Example: `name,team.name` |
| `lambdaql.sort.alias.<Entity>.<external name>` | The path of an alias. Example: `lambdaql.sort.alias.Member.level=grade` |

**SARGable mode property.** `lambdaql.sargable.mode` is one of `OFF`, `WARN` (default) and `STRICT`. The auto-configuration registers it in the same way as `lambdaql.sort.mode`. With `STRICT`, the analysis of a condition that cannot use an index fails with `NonSargableException` (`LQL-3066`) (see [8.9](08-procedures-functions.md#89-warning-functions-on-columns-and-indexes-sargable)).

For `<Entity>`, write the JPA entity name. The default is the simple class name, and you can change it with `@Entity(name = ...)`.

Entity names are unique in a persistence unit (if two have the same name, Hibernate stops at startup). So classes with the same name in different packages are not mixed up. An unknown name stops the application startup with `IllegalStateException` (LQL-4042).
The paths are checked against the entity when the rule is first used (see [Rules as strings](04-order-paging.md#rules-as-strings) in 4.10).

The properties are the same in Java and Kotlin (see the [Java version of this page](../java/12-spring-data-jpa.md#sort-policy-with-spring)). The auto-configuration registers the policy made from the properties.
An `application.properties` example:

```properties
lambdaql.sort.mode=STRICT
lambdaql.sort.allow.Member=name,team.name
lambdaql.sort.alias.Member.level=grade
```

**The `SortPolicy` bean.** To write rules that the compiler checks, define a `SortPolicy` bean. The same auto-configuration step registers this bean.
The configuration class below also defines the `QueryBuilder` bean of [12.8](#128-the-querybuilder-bean).

<!-- region: ManualKotlinBeansTest.kt#beans -->
```kotlin
/** Beans of your own: a sort policy, and a QueryBuilder (for example with two EntityManagerFactory beans). */
@Configuration
open class KtLambdaQlConfig {

    @Bean
    open fun sortPolicy(): SortPolicy = SortPolicy.of(SortMode.STRICT,
        SortRules.of(Member::class.java)
            .allow { it.name }
            .alias("level") { it.grade })

    @Bean
    open fun queryBuilder(entityManagerFactory: EntityManagerFactory) =
        // the same as the auto-configuration: a shared, transaction-bound EntityManager
        QueryBuilder(SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory))
}
```

If you do not use the kotlin-spring (all-open) compiler plugin, declare the `@Configuration` class and the `@Bean` functions as `open`, as above.

An application with this bean uses a Spring `Sort`:

<!-- region: ManualKotlinBeansTest.kt#sort-policy -->
```kotlin
// the client sends "level"; lambdaQL sorts by grade
val rows: List<Member> = repository.findAll({ it.isActive }, Sort.by(Sort.Order.desc("level"), Sort.Order.asc("name")))
// a property that is not in the rules
assertThrows<SortNotAllowedException> { repository.findAll({ it.isActive }, Sort.by("grade")) }
```

- An alias such as `level` is not a property of `Member`. lambdaQL passes it as it is, without the Spring `PropertyPath` check, and the policy turns it into `grade`.
- A name that is neither an alias nor a property gives `PropertyReferenceException` before the policy check.
- In `STRICT` mode, a property that is not in the rules gives `SortNotAllowedException` (`LQL-4040`). Spring does not translate this exception (see [12.6](#126-transactions-and-exceptions)).

**HTTP 400 response.** lambdaQL has no web dependency, so it does not decide the HTTP status. A web application usually answers with 400 Bad Request from `@ExceptionHandler(SortNotAllowedException::class)` in a `@RestControllerAdvice`.
The message of the exception has the entity class and the sort name. To not show entity names to the client, send only `e.code()` (`"LQL-4040"`).

Example code: [`ManualKotlinRepositoryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/kotlin/org/lambdaql/spring/ManualKotlinRepositoryTest.kt) (`spring-sort`), [`ManualKotlinBeansTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/kotlin/org/lambdaql/spring/ManualKotlinBeansTest.kt) (`beans`, `sort-policy`).

<a id="126-transactions-and-exceptions"></a>
## 12.6 Transactions and exceptions

The executor uses Spring's shared `EntityManager`, which is bound to the current transaction.

**lambdaQL does not put `@Transactional` on its methods.** This is by design.
Update, delete, insert and upsert need the caller's transaction. This is the same for the shortcuts (`deleteAll`, `updateOne` and so on), the builders (`updateQuery()` and so on) and the `QueryBuilder` bean.

Put `@Transactional` on the service method, or use `TransactionTemplate`. Without a transaction, the call fails before it sends SQL.

| Case | Behavior |
|---|---|
| A select inside `@Transactional` | lambdaQL runs in the same transaction and also sees changes that are not flushed yet. After a rollback, lambdaQL does not see those changes either |
| Update, delete, insert, upsert inside `@Transactional` or `TransactionTemplate` | Runs in that transaction and is rolled back with it |
| Update, delete, insert, upsert outside a transaction | Fails before it sends SQL. Nothing is changed (see the next table) |

The only builder that runs DML in its own transaction is `QueryBuilder(entityManagerFactory)` (see [7.8](07-dml.md#transactions)). Do not use this builder in a Spring application (see [12.8](#128-the-querybuilder-bean)).

Errors go through Spring's exception translation, like other repository methods.

| Case | Exception |
|---|---|
| Two or more rows match the condition of `findOne` | Spring `IncorrectResultSizeDataAccessException` instead of JPA `NonUniqueResultException` |
| Two or more rows match the condition of `deleteOne` or `updateOne` | `IncorrectResultSizeDataAccessException`. Nothing is changed |
| A property that does not exist in a Spring `Sort` | `PropertyReferenceException` |
| `ignoreCase()` of a Spring `Sort` | `UnsupportedOperationException` |
| In `STRICT` mode, a sort name that the sort policy does not allow ([12.5](#sort-policy-with-spring)) | `SortNotAllowedException` (`LQL-4040`), not translated |
| Running update, delete, insert or upsert without a transaction from a repository call (a shortcut, or `execute()` inside a method with a body) | `InvalidDataAccessApiUsageException`. This is JPA `TransactionRequiredException` translated by Spring. Outside repository methods (the `QueryBuilder` bean), it is JPA `TransactionRequiredException` |
| An assignment lambda that breaks a rule | `LambdaQLException` (for example, `TranslationException`, `LQL-3042`). Not translated (see [7.8](07-dml.md#rules-checked-at-analysis-time)) |

The `QueryBuilder` bean (12.8) is not a repository, so it does not go through Spring's exception translation. With this bean, if two or more rows match the condition of `one()` or `executeOne()`, `NonUniqueResultException` is thrown.

<a id="127-other-fragments-and-jpaspecificationexecutor"></a>
## 12.7 Other fragments and `JpaSpecificationExecutor`

`LambdaQlExecutor<T>` is one fragment among many. The following Kotlin repository uses all of these together.

| Used together with | Works? | Description |
|---|---|---|
| Your own fragment (`MemberRepositoryCustom` and `MemberRepositoryImpl`) | Works | Spring Data finds the `Impl` class in the usual way |
| `JpaSpecificationExecutor<T>` | Works | A lambdaQL lambda has one argument and a `Specification` lambda has three, so Kotlin also picks the lambdaQL `findAll` for `findAll { it.isActive }`. Write a `Specification` as `Specification<Member> { root, _, cb -> ... }` |
| `JpaRepository` methods (`findAll()`, `count()`, `save(...)`) | Works | The argument types differ |

<!-- region: ManualKotlinRepositories.kt#fragments -->
```kotlin
/** LambdaQlExecutor together with JpaSpecificationExecutor and a hand-written fragment. */
interface KtMemberSpecRepository : JpaRepository<Member, Long>, LambdaQlExecutor<Member>,
    JpaSpecificationExecutor<Member>, KtMemberRepositoryCustom

/** A hand-written fragment. */
interface KtMemberRepositoryCustom {
    fun countByHand(): Long
}

/** Its implementation: Spring Data still finds it by the name (fragment interface name + Impl). */
class KtMemberRepositoryCustomImpl(private val em: EntityManager) : KtMemberRepositoryCustom {
    override fun countByHand(): Long =
        em.createQuery("select count(m) from Member m", java.lang.Long::class.java).singleResult.toLong()
}
```

You can call `findAll { it.isActive }`, `findAll` with a `Specification`, `countByHand()` and `count()` on the same repository.

Example code: [`ManualKotlinRepositories.kt`](https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/kotlin/org/lambdaql/spring/ManualKotlinRepositories.kt) (`fragments`).

<a id="128-the-querybuilder-bean"></a>
## 12.8 The `QueryBuilder` bean

In Spring Boot, the auto-configuration `org.lambdaql.spring.boot.LambdaQlAutoConfiguration` makes a `QueryBuilder` bean. For queries that do not belong to one repository, inject this bean into a service.

- The bean uses Spring's shared `EntityManager`, which is bound to the transaction. So, like repository methods, its queries join the current transaction.
- The bean is made only when there is exactly one `EntityManagerFactory` and you did not define a `QueryBuilder` bean yourself.
- Repository methods (`LambdaQlExecutor<T>`) do not need this bean.
- Update, delete, insert and upsert statements made with this bean also need the caller's transaction, like repository methods. Without a transaction, `TransactionRequiredException` is thrown before SQL is sent.

<!-- region: ManualKotlinRepositories.kt#service -->
```kotlin
/** open: without the kotlin-spring compiler plugin, Spring needs an open class for the @Transactional proxy. */
@Service
open class KtMemberService(private val qb: QueryBuilder) {

    @Transactional(readOnly = true)
    open fun countByName(name: String): Long =
        qb.from(Member::class.java).where { it.name == name }.count()
}
```

If you call `countByName` right after you save a member inside a `TransactionTemplate`, the member that is not flushed yet is also counted. This is because the service query joins the caller's transaction.

If you do not use Spring Boot, or if there are two or more `EntityManagerFactory` beans, make the bean yourself. Use the shared `EntityManager`, as the auto-configuration does.
The `queryBuilder` bean in `KtLambdaQlConfig` of [12.5](#sort-policy-with-spring) is an example. This bean replaces the bean of the auto-configuration (the only `QueryBuilder` bean is `queryBuilder`), and it joins the caller's transaction.

`SharedEntityManagerCreator` is a Spring class (`org.springframework.orm.jpa`).
Do not use `QueryBuilder(entityManagerFactory)` in a Spring application. This builder opens its own `EntityManager` for each query, and this `EntityManager` does not join Spring transactions.

**`@DataJpaTest` tests.** A `@DataJpaTest` slice test loads only some auto-configurations. Repository methods work, but the `QueryBuilder` bean is not made.

Example code: [`ManualKotlinRepositories.kt`](https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/kotlin/org/lambdaql/spring/ManualKotlinRepositories.kt) (`service`).

<a id="129-kotlin-compile-settings"></a>
## 12.9 Kotlin compile settings

Spring Data runs the body of a repository method only when the method is a JVM default method of the interface.

| Kotlin | Setting |
|---|---|
| 2.2 or later | No setting needed. Methods with a body are compiled as JVM default methods by default. |
| Older than 2.2 | Add the compiler option `-Xjvm-default=all`. Without it, the interface has no method body, so Spring Data tries to make a query from the method name, and the application does not start (`No property ... found for type ...`). |
| All versions | Add `kotlin-reflect`. Without it, Spring Data fails with `ClassNotFoundException: kotlin.reflect.jvm.ReflectJvmMapping`. |

```gradle-kts
// build.gradle.kts, only for Kotlin older than 2.2
kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}
```

The behavior of Kotlin older than 2.2 was reproduced with `-jvm-default=disable` (measured).

<a id="1210-not-implemented-yet"></a>
## 12.10 Not implemented yet

- **Spring Boot versions.** Versions other than Spring Boot 3.5 and 4.1 were not checked (see [13. Spring Boot 3 and 4](../13-spring-boot-versions.md)).
- **`ignoreCase()`** of Spring `Sort`.

---

[Kotlin track](README.md) | Previous: [11. Errors and languages](../11-errors-and-languages.md) | Next: [13. Spring Boot 3 and 4](../13-spring-boot-versions.md) | [Java version of this page](../java/12-spring-data-jpa.md)
