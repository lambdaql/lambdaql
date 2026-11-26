<a id="12-spring-data-jpa-java"></a>
# 12. Spring Data JPA (Java)

[Java track](README.md) | [Manual home](../README.md) | Previous: [11. Errors and languages](../11-errors-and-languages.md) | Next: [13. Spring Boot 3 and 4](../13-spring-boot-versions.md) | [Kotlin version of this page](../kotlin/12-spring-data-jpa.md)

---

<a id="121-what-you-get"></a>
## 12.1 What you get

With Spring Data JPA, you write repository interfaces. lambdaQL adds one more interface to them: `LambdaQlExecutor<T>`.

Your repository extends this interface, and you write your own query methods as **default methods** that use lambdaQL.
You do not write a `...RepositoryImpl` class for these methods.

| Part | What it does | Section |
|---|---|---|
| `LambdaQlExecutor<T>` | Runs `findAll`, `findSet`, `findFirst`, `findOne`, `count`, `exists` and `query()` with lambda expression conditions in every repository | [12.3](#123-repository-setup), [12.4](#124-executor-methods) |
| Spring `Sort` and `Pageable` | `findAll(where, sort)`, `findSet(where, sort)`, and `findAll(where, pageable)` that returns a Spring `Page` | [12.5](#125-spring-sort-and-pageable) |
| Update, delete, insert, upsert | Shortcut methods such as `deleteAll(where)` and `updateOne(where, set)` run a statement in one call. `updateQuery()`, `deleteQuery()`, `insertQuery()` and `upsertQuery()` start the statements of [7. Update, insert, delete](07-dml.md) on the repository entity. All need the caller's transaction | [12.4](#124-executor-methods), [12.6](#126-transactions-and-exceptions) |
| `QueryBuilder` bean | Spring Boot creates a `QueryBuilder` bean for use in services | [12.8](#128-the-querybuilder-bean) |

<a id="122-modules"></a>
## 12.2 Modules

The core does not depend on Spring, so you can also use it with plain JPA. `lambdaql` and `lambdaql-spring-data-jpa` are shared by Spring Boot 3 and 4, and only the starter is separate for each Spring Boot version (the name ends with `-boot3` or `-boot4`, [13. Spring Boot 3 and 4](../13-spring-boot-versions.md)).

| Artifact | What it has | When you need it |
|---|---|---|
| `lambdaql` | The engine and `QueryBuilder`. No Spring dependency. Shared by Spring Boot 3 and 4 | Always. The artifacts below bring it in |
| `lambdaql-spring-data-jpa` | `LambdaQlExecutor<T>`, Spring Data registration, Spring Boot auto-configuration (`QueryBuilder` bean). Shared by Spring Data JPA 3.5 and 4.1. Uses the Spring Data JPA of your project | When you use Spring Data JPA without Spring Boot |
| `lambdaql-spring-boot-starter-boot4`, `lambdaql-spring-boot-starter-boot3` | No code. Brings in `lambdaql-spring-data-jpa` and the `spring-boot-starter-data-jpa` of that Boot version | When you use Spring Boot (match the Boot version of your project) |

The artifacts are on Maven Central (groupId `io.github.lambdaql`, version `0.0.2`).

Gradle (Groovy DSL) for Spring Boot 4. For Spring Boot 3, change the end of the name to `-boot3`:

```groovy
repositories {
    mavenCentral()
}

dependencies {
    implementation 'io.github.lambdaql:lambdaql-spring-boot-starter-boot4:0.0.2'
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

Without Spring Boot, add `lambdaql-spring-data-jpa` to your existing Spring Data JPA setup (Spring Data JPA, Hibernate, JDBC driver). This artifact brings in `lambdaql`, and it uses the Spring Data JPA that your project has (3.5 or 4.1).

```groovy
dependencies {
    implementation 'io.github.lambdaql:lambdaql-spring-data-jpa:0.0.2'
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
| Java | 21 or later |
| Spring Boot | 3.5 or 4.1 |
| Spring Data JPA | 3.5 or 4.1 |
| Hibernate | 6.6 or 7.4 (included in Spring Boot) |

Spring Data JPA 3.3 or earlier is **not supported**. lambdaQL registers its executor in `META-INF/spring.factories`, and Spring Data reads repository fragments from this file since 3.4.

<a id="123-repository-setup"></a>
## 12.3 Repository setup

Add `LambdaQlExecutor<T>` to the `extends` list of your repository. `T` is the entity of the repository.
You do not write any extra configuration or `Impl` class.

The examples in this chapter use the example entities `Member` (`id`, `name`, `active`, `grade`, `team`) and `Team` (`id`, `name`). `grade` is an `Integer`, so it can be NULL.

<!-- region: ManualMemberRepository.java#repository -->
```java
/** No Impl class: Spring Data finds the LambdaQlExecutor implementation by itself. */
public interface ManualMemberRepository extends JpaRepository<Member, Long>, LambdaQlExecutor<Member>,
        JpaSpecificationExecutor<Member> {

    /** @return the active members */
    default List<Member> activeMembers() {
        return findAll(m -> m.isActive());
    }

    /**
     * @param name a name
     * @return the member with that name, or empty
     */
    default Optional<Member> byName(String name) {
        return findOne(m -> name.equals(m.getName()));
    }

    /**
     * @param n how many
     * @return the active members with the highest grades
     */
    default List<Member> topGraded(int n) {
        return query()
                .where(m -> m.isActive())
                .orderBy(m -> desc(m.getGrade()).asc(m.getId()))
                .limit(n)
                .toList();
    }

    /**
     * @param grade the new grade
     * @return how many inactive members changed (call inside a transaction)
     */
    default int gradeInactive(int grade) {
        return updateQuery().set(m -> m.setGrade(grade)).where(m -> !m.isActive()).execute();
    }

    /** @return how many members without a team were deleted (call inside a transaction) */
    default int deleteWithoutTeam() {
        return deleteQuery().where(m -> m.getTeam() == null).execute();
    }

    /**
     * @param name a name
     * @return 1 (call inside a transaction)
     */
    default int addGuest(String name) {
        return insertQuery().values(m -> { m.setName(name); m.setActive(false); }).execute();
    }
}
```

The last three methods run update, delete and insert statements. These methods need a transaction (see [12.6](#126-transactions-and-exceptions)).

This repository also extends `JpaSpecificationExecutor<Member>`. lambdaQL does not need it. It is there to show how the two work together.
Another example repository, [`MemberRepository`](https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/sample/MemberRepository.java), also extends a hand-written fragment `MemberRepositoryCustom` with the same methods (see [12.7](#127-other-fragments-and-jpaspecificationexecutor)).

How it works:

- **Spring Data runs default methods directly.** Spring Data does not build a query from a default method. It calls the method body. lambdaQL does not change this behavior.
- **`LambdaQlExecutor<T>` is a Spring Data repository fragment.** Its implementation class is registered in `META-INF/spring.factories` of `lambdaql-spring-data-jpa`. Spring Data reads this file and adds the implementation to every repository that extends `LambdaQlExecutor<T>`.
- **All repositories share one executor object.** Each call uses the entity of its repository. For example, `teams.count(t -> ...)` in `TeamRepository` returns the number of `Team` rows.

Rules:

| # | Rule |
|---|---|
| 1 | The repository must extend `LambdaQlExecutor<T>` **directly** (in its own `extends` list). |
| 2 | Do not put `@NoRepositoryBean` on an interface that extends `LambdaQlExecutor<T>` for this purpose. If you do, Spring Data does not add the lambdaQL implementation. |
| 3 | Write lambdaQL queries in **default methods** or in services. Abstract methods keep working as Spring Data query methods (`findByName` and others). |
| 4 | Without Spring Boot, the repository part still works, because it is plain Spring Data. Only the `QueryBuilder` bean is not created automatically (see [12.8](#128-the-querybuilder-bean)). |

Example code: [`ManualMemberRepository.java`](https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ManualMemberRepository.java) (`repository`).

<a id="124-executor-methods"></a>
## 12.4 Executor methods

`Where<T>` is the same condition lambda expression as in `where(...)` (see [3. Conditions](03-where.md)). `SortSpec<T>` is a lambdaQL sort (see [4.10](04-order-paging.md#410-reusable-and-external-sorts)).
`Sort`, `Pageable` and `Page` are Spring Data types.

| Method | Returns | Description |
|---|---|---|
| `query()` | `SelectQuery<T>` | Starts a query on the repository entity. Use it for queries that the short methods cannot express (many `where` calls, `limit`, `offset`, a `Direction` chosen at run time) |
| `from(Class<E>)` | `SelectQuery<E>` | Starts a query on another entity of the same persistence unit |
| `findAll(Where<T>)` | `List<T>` | All rows that match the condition. The order is not defined |
| `findAll(Where<T>, SortSpec<T>)` | `List<T>` | All rows that match the condition, sorted |
| `findAll(Where<T>, Sort)` | `List<T>` | Sorted with a Spring `Sort` (see [12.5](#125-spring-sort-and-pageable)). `Sort.unsorted()` means no sort |
| `findAll(Where<T>, Pageable)` | `Page<T>` | One page. The count query runs only when needed. `Pageable.unpaged()` gives all rows |
| `findSet(Where<T>)` | `SequencedSet<T>` | All rows that match the condition, as a Set. The order is not defined (same as `findAll(Where<T>)`). No `distinct`. `IllegalStateException` if the Set is smaller than the number of rows (see [`toSet()`](02-getting-started.md#toset)) |
| `findSet(Where<T>, SortSpec<T>)` | `SequencedSet<T>` | All rows that match the condition, as a Set in sort order |
| `findSet(Where<T>, Sort)` | `SequencedSet<T>` | Same as above. Sorted with a Spring `Sort` (see [12.5](#125-spring-sort-and-pageable)) |
| `findFirst(Where<T>)` | `Optional<T>` | One of the rows that match the condition. Not sorted |
| `findFirst(Where<T>, SortSpec<T>)` | `Optional<T>` | The first row in the given order. Reads only one row |
| `findFirst(Where<T>, Sort)` | `Optional<T>` | The first row in the order of the Spring `Sort` |
| `findOne(Where<T>)` | `Optional<T>` | The only row that matches the condition. `IncorrectResultSizeDataAccessException` if there are two or more rows |
| `count(Where<T>)` | `long` | The number of rows that match the condition |
| `exists(Where<T>)` | `boolean` | `true` if a row matches the condition. Reads at most one row and does not load entities |
| `deleteAll(Where<T>)` | `int` | Deletes all rows that match the condition with one SQL statement (bulk). Different from Spring Data's `deleteAll()` (see [below](#deleteallwhere-is-not-spring-datas-deleteall)). Needs a transaction |
| `deleteOne(Where<T>)` | `int` | Deletes the only row that matches the condition, with [one-row protection](#one-row-protection-deleteone-and-updateone). Returns 0 or 1. Needs a transaction |
| `updateAll(Where<T>, Assign<T>)` | `int` | Updates all rows that match the condition. The condition comes first. Needs a transaction |
| `updateOne(Where<T>, Assign<T>)` | `int` | Updates the only row that matches the condition, with one-row protection. Returns 0 or 1. Needs a transaction |
| `insertOne(Assign<T>)` | `int` | Inserts one row. Returns 1. Needs a transaction |
| `insertAll(Collection<L>, AssignEach<T, L>)` | `int` | Inserts one row per element (500 values or fewer per statement). Needs a transaction |
| `upsertOne(Assign<T>)` | `int` | Inserts one row. If a row with the same `@Id` exists, updates that row. Returns the row count that the database reports. Needs a transaction |
| `upsertAll(Collection<L>, AssignEach<T, L>)` | `int` | Upserts one row per element (one statement per row). Needs a transaction |
| `updateQuery()` | `UpdateQuery<T>` | Starts an update: `updateQuery().set(...).where(...).execute()` (see [7.2](07-2-update.md)). Needs a transaction |
| `deleteQuery()` | `DeleteQuery<T>` | Starts a delete: `deleteQuery().where(...).execute()` (see [7.4](07-4-delete.md)). Needs a transaction |
| `insertQuery()` | `InsertQuery<T>` | Starts an insert: `insertQuery().values(...).execute()` (see [7.5](07-5-insert.md)). Needs a transaction |
| `upsertQuery()` | `UpsertQuery<T>` | Starts an upsert with the `@Id` as the key (see [7.6](07-6-upsert.md)). Needs a transaction |

The short methods are the same as `query()` with a result function. `findAll(w)` is `query().where(w).toList()`, `findSet(w)` is `query().where(w).toSet()`, `findFirst(w)` is `query().where(w).first()`, and `findOne(w)` is `query().where(w).one()`. The result functions are explained in [Getting the result](02-getting-started.md#getting-the-result).
`findAll(Where<T>)` and `count(Where<T>)` have different argument types, so they do not clash with `findAll(Sort)`, `findAll(Example)` and `count()` of `JpaRepository`.

**`query()`: the full query.**
Part of the repository of 12.3:

<!-- region: MemberRepository.java#query -->
```java
/**
 * @param n how many
 * @return the active members with the highest grades
 */
default List<Member> topGraded(int n) {
    return query()
            .where(m -> m.isActive())
            .orderBy(m -> desc(m.getGrade()).asc(m.getId()))
            .limit(n)
            .toList();
}
```

In the next examples, `repository` is a `MemberRepository`.

<!-- region: ExecutorTest.java#find-all-sort -->
```java
SortSpec<Member> byGrade = SortSpec.of(Member.class, m -> desc(m.getGrade()).asc(m.getId()));
List<Member> active = repository.findAll(m -> m.isActive(), byGrade);
```

The active member with the highest grade:

<!-- region: ExecutorTest.java#find-first -->
```java
Optional<Member> best = repository.findFirst(m -> m.isActive(),
        SortSpec.of(Member.class, m -> desc(m.getGrade())));
```

<!-- region: ExecutorTest.java#find-one -->
```java
Optional<Member> kim = repository.findOne(m -> m.getName().equals("Kim"));
// two or more rows: Spring's IncorrectResultSizeDataAccessException
assertThrows(IncorrectResultSizeDataAccessException.class, () -> repository.findOne(m -> m.isActive()));
```

<!-- region: ExecutorTest.java#count-exists -->
```java
long active = repository.count(m -> m.isActive());
boolean anyInTeamB = repository.exists(m -> m.getTeam().getName().equals("B"));
```

<!-- region: ExecutorTest.java#find-set -->
```java
java.util.SequencedSet<Member> active = repository.findSet(m -> m.isActive(), Sort.by(Sort.Order.desc("grade"), Sort.Order.asc("id")));
java.util.SequencedSet<Member> any = repository.findSet(m -> m.isActive());
```

`active` keeps the order of the `Sort`. The order of `any` is not defined, as with `findAll(where)`.

<a id="dml-shortcuts"></a>
### DML shortcuts

These 8 methods run an update, delete, insert or upsert in one call.

| Shortcut | Same statement |
|---|---|
| `deleteAll(where)` | `deleteQuery().where(where).execute()` |
| `deleteOne(where)` | `deleteQuery().where(where).executeOne()` |
| `updateAll(where, set)` | `updateQuery().set(set).where(where).execute()` |
| `updateOne(where, set)` | `updateQuery().set(set).where(where).executeOne()` |
| `insertOne(values)` | `insertQuery().values(values).execute()` |
| `insertAll(rows, values)` | `insertQuery().values(rows, values).execute()` |
| `upsertOne(values)` | `upsertQuery().values(values).execute()` |
| `upsertAll(rows, values)` | `upsertQuery().values(rows, values).execute()` |

- **The condition comes first**, then the assignment: `updateAll(m -> !m.isActive(), m -> m.setGrade(7))`.
- **`All`** means all rows that match the condition (delete, update) or all elements of the list (insert, upsert).
- **`One`** means one-row protection for delete and update (see the next section), and one row for insert and upsert.
- They need the caller's transaction (see [12.6](#126-transactions-and-exceptions)). The example calls them inside a `TransactionTemplate`.

<!-- region: ExecutorTest.java#dml-shortcuts -->
```java
int changed = repository.updateAll(m -> !m.isActive(), m -> m.setGrade(7));      // condition first
int deleted = repository.deleteAll(m -> m.getTeam() == null);                     // bulk: no cascade
int one = repository.insertOne(m -> { m.setName("Guest"); m.setActive(false); });
int many = repository.insertAll(List.of(new NewMember("A", 1), new NewMember("B", 2)),
        (m, r) -> { m.setName(r.name()); m.setGrade(r.grade()); m.setActive(true); });
```

`NewMember` is the record `record NewMember(String name, int grade)` of the example.

<a id="one-row-protection-deleteone-and-updateone"></a>
### One-row protection: `deleteOne` and `updateOne`

`deleteOne(where)` and `updateOne(where, set)` never change two or more rows.

1. lambdaQL reads at most 2 ids of the rows that match the condition.
2. If there is no id, it changes nothing and returns 0.
3. If there are 2 ids, it changes nothing and throws `IncorrectResultSizeDataAccessException`.
4. If there is 1 id, it changes only that row with `id = :p and (condition)` and returns 1. If the row stopped matching the condition in the meantime, it returns 0.

The statement itself names one id, so even if another transaction adds a matching row in the meantime, a second row is not changed. This protection does not rely on a rollback.

The core API has the same protection: `executeOne()` (see [7.4](07-4-delete.md#delete-exactly-one-row-executeone)).

<!-- region: ExecutorTest.java#dml-one -->
```java
int renamed = repository.updateOne(m -> m.getId() == kimId, m -> m.setName("Kim2"));   // exactly one row
// two or more rows match: nothing changes
assertThrows(org.springframework.dao.IncorrectResultSizeDataAccessException.class,
        () -> repository.deleteOne(m -> m.isActive()));
```

<a id="deleteallwhere-is-not-spring-datas-deleteall"></a>
### `deleteAll(where)` is not Spring Data's `deleteAll()`

| Method | Belongs to | How it deletes | cascade, `@PreRemove` |
|---|---|---|---|
| `deleteAll()` | `CrudRepository` | Loads all entities, then deletes them one by one | Run |
| `deleteAll(Iterable)` | `CrudRepository` | Deletes the given entities one by one | Run |
| `deleteAll(Where<T>)` | `LambdaQlExecutor<T>` | One SQL `delete` statement (bulk) | **Not run** |

`deleteAll(where)` does not load entities. Like other bulk statements, it is not reflected in entities that are already loaded.

Use it to delete many rows fast. If you need cascade or `@PreRemove`, use the Spring Data methods.

The compiler chooses the method by the argument (no argument, `Iterable`, lambda expression). This was checked in the bytecode opcodes of javac and kotlinc 2.3.21 (measured).

<a id="builders-for-complex-statements"></a>
### Builders for complex statements

`updateQuery()`, `deleteQuery()`, `insertQuery()` and `upsertQuery()` start a full statement. Use them when the shortcut methods cannot express it: many `set()` calls, `all()`, `orderBy(...).limit(n)` (see [7.4](07-4-delete.md#delete-the-first-rows-limit)), `toHql()`, `onConflict(...)`.

The method names end with `Query`, so they do not clash with `JpaRepository` methods such as `delete(...)`. The statements are the same as the statements built with `QueryBuilder` (see [7. Update, insert, delete](07-dml.md)). The target rows are the same as the rows selected with the same `where` lambda expression.

Like the shortcut methods, they need the caller's transaction. Like every JPA bulk statement, they are not reflected in entities already loaded in the transaction.

Part of the repository of 12.3:

<!-- region: MemberRepository.java#dml -->
```java
/**
 * @param grade the new grade
 * @return how many inactive members changed (call inside a transaction)
 */
default int gradeInactive(int grade) {
    return updateQuery().set(m -> m.setGrade(grade)).where(m -> !m.isActive()).execute();
}

/** @return how many members without a team were deleted (call inside a transaction) */
default int deleteWithoutTeam() {
    return deleteQuery().where(m -> m.getTeam() == null).execute();
}

/**
 * @param name a name
 * @return 1 (call inside a transaction)
 */
default int addGuest(String name) {
    return insertQuery().values(m -> { m.setName(name); m.setActive(false); }).execute();
}
```

An upsert also runs inside a transaction. `teams` is a `TeamRepository`, and `tx` is a Spring `TransactionTemplate`.

<!-- region: ExecutorTest.java#upsert -->
```java
int changed = tx.execute(s -> teams.upsertQuery().values(t -> { t.setId(teamId); t.setName("A2"); }).execute());
```

An upsert returns the row count that the database reports. For an insert it is 1. For an update it is 1 on H2 and Oracle, and 2 on MariaDB (see [7.6](07-6-upsert.md#the-row-count)).

Example code: [`MemberRepository.java`](https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/sample/MemberRepository.java) (`query`, `dml`), [`ExecutorTest.java`](https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java) (`find-all-sort`, `find-first`, `find-one`, `count-exists`, `find-set`, `dml-shortcuts`, `dml-one`, `upsert`).

<a id="125-spring-sort-and-pageable"></a>
## 12.5 Spring `Sort` and `Pageable`

`findAll(where, sort)`, `findSet(where, sort)`, `findFirst(where, sort)` and `findAll(where, pageable)` take Spring Data types.
lambdaQL converts each `Sort.Order` into one sort key with `SortSpec.ofPath` (see [4.10](04-order-paging.md#410-reusable-and-external-sorts)).

| Spring | What lambdaQL does |
|---|---|
| Property names (`"grade"`, `"team.name"`) | Checked with Spring Data `PropertyPath`, as in Spring Data repositories. An unknown name gives `PropertyReferenceException` (package: `org.springframework.data.mapping` in Spring Data 3, `org.springframework.data.core` in 4). The end of the path must be a basic value, not an entity. A to-one path such as `team.name` uses a `left join`, so rows without a team are also in the result. Then the sort policy checks the name (see [below](#sort-policy-with-spring)) |
| An alias of the sort policy (`"level"`) | Passed as it is, without the `PropertyPath` check. The sort policy turns it into a path |
| `ASC` / `DESC` with `NullHandling.NATIVE` (the default) | The lambdaQL NULL rule applies. NULL first for ascending, NULL last for descending. The same order on every database (see [4.4](04-order-paging.md#44-null-ordering-is-the-same-on-every-database)) |
| `NullHandling.NULLS_FIRST` / `NULLS_LAST` | Used as given (`ASC_NULLS_FIRST`, `DESC_NULLS_LAST` and so on) |
| `ignoreCase()` | Not supported. Throws `UnsupportedOperationException` |
| `Sort.unsorted()` | No `order by` |

Spring Data JPA's own repositories leave the NULL position of `NATIVE` to the database, so the order can differ by database. lambdaQL applies its own rule.

<!-- region: ExecutorTest.java#find-all-spring-sort -->
```java
List<Member> rows = repository.findAll(m -> m.isActive(),
        Sort.by(Sort.Order.desc("grade"), Sort.Order.asc("team.name"), Sort.Order.asc("id")));
// a property that does not exist: the same error as Spring Data's own repositories
RuntimeException unknown = assertThrows(RuntimeException.class,
        () -> repository.findAll(m -> m.isActive(), Sort.by("nickname")));
assertEquals("PropertyReferenceException", unknown.getClass().getSimpleName());
```

Paging with `findAll(where, pageable)`:

- Page numbers start at 0, as in Spring Data. lambdaQL reads the page with `limit` and `offset`.
- The total count comes from a `count` query. When the page alone fixes the total (for example, the first page has fewer rows than the page size), Spring skips the count query.
- If the sort keys have no id, lambdaQL adds the id as the last key, so rows do not move between pages (see [Tie-breaker](04-order-paging.md#tie-breaker-automatic-id)). A page without a sort gets no `order by`, so give a `Sort` to split pages in a stable way.
- `Pageable.unpaged()` returns all rows as one page.

<!-- region: ExecutorTest.java#page -->
```java
Page<Member> page = repository.findAll(m -> m.isActive(), PageRequest.of(1, 2, Sort.by("name")));
// page 1 (the second page) of the active members by name; the total comes from a count query
```

**Sorts from request parameters.** Spring MVC can build a `Pageable` from request parameters such as `?page=1&size=20&sort=name,desc`.
With no sort policy and no `@Sortable` fields, every persistent property is accepted, so a user can sort by any column. In this case lambdaQL logs `LQL-5003` at `INFO` level, once per entity and name.
To allow only some names, use the sort policy below.

<a id="sort-policy-with-spring"></a>
### Sort policy with Spring

The sort policy decides which outside sort names are allowed. `SortRules`, `SortMode` and the rules are explained in [4.10](04-order-paging.md#sort-policy).
A Spring `Sort` or `Pageable` is always an outside sort, so the policy checks every name in it. Sort lambda expressions are not checked.

**Where the rules come from.** In Spring Boot, the auto-configuration builds the policy of the `EntityManagerFactory` and registers it after all singleton beans are created.
The auto-configuration reads three places.

| Source | What it gives |
|---|---|
| A `SortPolicy` bean | A mode and `SortRules` written with lambda expressions |
| The properties `lambdaql.sort.*` | A mode and rules written as strings |
| `@Sortable` fields ([4.10](04-order-paging.md#rules-from-sortable)) | Rules for entities that have no rules in the bean or the properties |

- The rules of one entity come from one place only, in the order of the table. Rules from two places are not merged.
- `lambdaql.sort.mode` takes priority over the mode of the bean. If neither is set, the mode is `INFO`.
- It is registered when there is exactly one `EntityManagerFactory`, the same as for the `QueryBuilder` bean (see [12.8](#128-the-querybuilder-bean)). In other cases, call `SortPolicy.register(emf, policy)` yourself.
- If there is neither a bean nor properties, nothing is registered, so the default is used: `INFO`, with rules only from `@Sortable`.

**Properties.**

| Property | Meaning |
|---|---|
| `lambdaql.sort.mode` | `OFF`, `INFO` (default), `WARN`, `STRICT` |
| `lambdaql.sort.allow.<Entity>` | Allowed paths, separated by commas. Example: `name,team.name` |
| `lambdaql.sort.alias.<Entity>.<outside name>` | The path of an alias. Example: `lambdaql.sort.alias.Member.level=grade` |

**SARGable mode property.** `lambdaql.sargable.mode` is one of `OFF`, `WARN` (default) and `STRICT`. The auto-configuration registers it in the same way as `lambdaql.sort.mode`. With `STRICT`, the analysis of a condition that cannot use an index fails with `NonSargableException` (`LQL-3066`) (see [8.9](08-procedures-functions.md#89-warning-functions-on-columns-and-indexes-sargable)).

For `<Entity>`, write the JPA entity name. The default is the simple class name, and you can change it with `@Entity(name = ...)`.

An entity name is unique in a persistence unit (if two have the same name, Hibernate stops at startup). So classes with the same name in different packages do not get mixed. If the name does not exist, the application startup stops with `IllegalStateException` (LQL-4042).
The paths are checked against the entity when the rule is first used (see [Rules as strings](04-order-paging.md#rules-as-strings) in 4.10).

In an application, write them in `application.properties` or `application.yaml`:

<!-- region: SortPropertiesTest.java#properties -->
```java
@SpringBootTest(classes = SampleApplication.class, properties = {
        "lambdaql.sort.mode=STRICT",
        "lambdaql.sort.allow.Member=name,team.name",
        "lambdaql.sort.alias.Member.level=grade"})
```

<!-- region: SortPropertiesTest.java#properties-use -->
```java
List<Member> byLevel = repository.findAll(m -> m.isActive(), Sort.by(Sort.Order.desc("level")));
List<Member> byTeam = repository.findAll(m -> m.isActive(), Sort.by("team.name", "name"));
assertThrows(SortNotAllowedException.class, () -> repository.findAll(m -> m.isActive(), Sort.by("grade")));
```

The auto-configuration registers the sort policy from these properties.

**A `SortPolicy` bean.** To use rules that the compiler checks, define a `SortPolicy` bean. The same auto-configuration step registers this bean.
With this bean, `Sort.Order.desc("level")` sorts by `grade`, and `Sort.by("grade")` is rejected with `SortNotAllowedException`. The example after it registers the same policy directly.

<!-- region: ManualJavaSpringTest.java#sort-policy-bean -->
```java
@Configuration
static class SortConfig {

    @Bean
    public SortPolicy sortPolicy() {
        return SortPolicy.of(SortMode.STRICT,
                SortRules.of(Member.class)
                        .allow(m -> m.getName())
                        .alias("level", m -> m.getGrade()));
    }
}
```

The code below is the part inside a `try` block. The `finally` block calls `SortPolicy.unregister(emf)` to remove the policy.

<!-- region: ExecutorTest.java#sort-policy -->
```java
// usually a @Bean SortPolicy; the Boot auto-configuration registers it for the EntityManagerFactory
org.lambdaql.query.SortPolicy.register(emf, org.lambdaql.query.SortPolicy.of(org.lambdaql.query.SortMode.STRICT,
        org.lambdaql.query.SortRules.of(Member.class)
                .allow(m -> m.getName())
                .alias("level", m -> m.getGrade())));
try {
    // the client sends "level"; lambdaQL sorts by grade
    List<Member> rows = repository.findAll(m -> m.isActive(), Sort.by(Sort.Order.desc("level"), Sort.Order.asc("name")));
    // a property that is not in the rules
    assertThrows(org.lambdaql.SortNotAllowedException.class,
            () -> repository.findAll(m -> m.isActive(), Sort.by("grade")));
```

- An alias such as `level` is not a property of `Member`. lambdaQL passes it as it is, without the Spring `PropertyPath` check, and the policy turns it into `grade`.
- A name that is neither an alias nor a property gives `PropertyReferenceException` before the policy check.
- In `STRICT` mode, a property that is not in the rules gives `SortNotAllowedException` (`LQL-4040`). Spring does not translate this exception (see [12.6](#126-transactions-and-exceptions)).

**HTTP 400 response.** lambdaQL has no web dependency, so it does not decide the HTTP status. A web application usually answers with 400 Bad Request in `@ExceptionHandler(SortNotAllowedException.class)` and reads the error code with `e.code()` (`"LQL-4040"`).

The message has the entity class and the sort name. To avoid showing the entity name to clients, send only `e.code()`.

Example code: [`ExecutorTest.java`](https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java) (`find-all-spring-sort`, `page`, `sort-policy`), [`SortPropertiesTest.java`](https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/SortPropertiesTest.java) (`properties`, `properties-use`), [`ManualJavaSpringTest.java`](https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ManualJavaSpringTest.java) (`sort-policy-bean`).

<a id="126-transactions-and-exceptions"></a>
## 12.6 Transactions and exceptions

The executor uses Spring's shared `EntityManager`, which is bound to the current transaction.

**lambdaQL does not put `@Transactional` on its methods.** This is by design.
Update, delete, insert and upsert need the caller's transaction. This is the same for the shortcut methods (`deleteAll`, `updateOne` and others), the builders (`updateQuery()` and others) and the `QueryBuilder` bean.

Put `@Transactional` on your service method, or use `TransactionTemplate`. Without a transaction, the call fails before any SQL is sent.

| Case | Behavior |
|---|---|
| A select inside `@Transactional` | lambdaQL runs in the same transaction and also sees changes that are not flushed yet. After a rollback, lambdaQL does not see those changes either |
| A select outside a transaction | Spring uses a new `EntityManager` for each call and closes it when the call ends, so the returned entities are detached (the `detached` example below) |
| Update, delete, insert, upsert inside `@Transactional` or `TransactionTemplate` | Runs in that transaction and is rolled back with it |
| Update, delete, insert, upsert outside a transaction | Fails before any SQL is sent. Nothing is changed (see the next table) |

<!-- region: ManualJavaSpringTest.java#detached -->
```java
// outside a transaction each call uses a new EntityManager: the returned entity is detached
Member kim = repository.findOne(m -> m.getName().equals("Kim")).orElseThrow();
boolean managed = new TransactionTemplate(txManager).execute(s -> entityManager.contains(kim));
assertFalse(managed);
```

The only builder that runs DML in its own transaction is `new QueryBuilder(entityManagerFactory)` (see [7.8](07-dml.md#transactions)). Do not use this builder in a Spring application (see [12.8](#128-the-querybuilder-bean)).

Errors go through Spring's exception translation, like other repository methods.

| Case | Exception |
|---|---|
| Two or more rows match the condition of `findOne` | Spring `IncorrectResultSizeDataAccessException` instead of JPA `NonUniqueResultException` |
| Two or more rows match the condition of `deleteOne` or `updateOne` | `IncorrectResultSizeDataAccessException`. Nothing is changed |
| A property that does not exist in a Spring `Sort` | `PropertyReferenceException` |
| `ignoreCase()` of a Spring `Sort` | `UnsupportedOperationException` |
| A sort name that the sort policy does not allow in `STRICT` mode ([12.5](#sort-policy-with-spring)) | `SortNotAllowedException` (`LQL-4040`), not translated |
| Running an update, delete, insert or upsert without a transaction from a repository call (a shortcut method, or `execute()` inside a default method) | `InvalidDataAccessApiUsageException`. Spring translated the JPA `TransactionRequiredException` into it. Outside repository methods (the `QueryBuilder` bean), it is the JPA `TransactionRequiredException` |
| An assignment lambda expression that breaks a rule | `LambdaQLException` (for example `TranslationException`, `LQL-3042`). Not translated (see [7.8](07-dml.md#rules-checked-at-analysis-time)) |

The `QueryBuilder` bean (12.8) is not a repository, so it does not go through Spring's exception translation. With this bean, if two or more rows match the condition of `one()` or `executeOne()`, you get `NonUniqueResultException`.

Example code: [`ManualJavaSpringTest.java`](https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ManualJavaSpringTest.java) (`detached`).

<a id="127-other-fragments-and-jpaspecificationexecutor"></a>
## 12.7 Other fragments and `JpaSpecificationExecutor`

`LambdaQlExecutor<T>` is one of many fragments. The repository of 12.3 uses all of the following together.

| Used together with | Works | Description |
|---|---|---|
| A hand-written fragment (`MemberRepositoryCustom` and `MemberRepositoryImpl`) | Yes | Spring Data finds the `Impl` class in the usual way |
| `JpaSpecificationExecutor<T>` | Yes | A lambdaQL lambda expression has one argument and a `Specification` lambda expression has three, so Java always chooses the correct `findAll` |
| `JpaRepository` methods (`findAll()`, `count()`, `save(...)`) | Yes | The argument types are different |

In the next example, `repository` is a `MemberRepository`.

<!-- region: ManualJavaSpringTest.java#fragments -->
```java
long byHand = repository.countByHand();                                               // hand-written fragment
List<Member> bySpec = repository.findAll((root, query, cb) -> cb.isTrue(root.get("active")));   // Specification: three arguments
List<Member> byLambda = repository.findAll(m -> m.isActive());                        // lambdaQL Where: one argument
long total = repository.count();                                                      // Spring Data's own count()
```

Example code: [`ManualJavaSpringTest.java`](https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ManualJavaSpringTest.java) (`fragments`).

<a id="128-the-querybuilder-bean"></a>
## 12.8 The `QueryBuilder` bean

In Spring Boot, the auto-configuration `org.lambdaql.spring.boot.LambdaQlAutoConfiguration` creates a `QueryBuilder` bean. For queries that do not belong to one repository, inject this bean into a service.

- The bean uses Spring's shared `EntityManager`, which is bound to the transaction. So, like repository methods, its queries join the current transaction.
- The bean is created only when there is exactly one `EntityManagerFactory` and you did not define your own `QueryBuilder` bean.
- Repository methods (`LambdaQlExecutor<T>`) do not need this bean.
- Update, delete, insert and upsert statements built with this bean also need the caller's transaction, like repository methods. Without a transaction, `TransactionRequiredException` is raised before any SQL is sent.

`qb` is the injected bean:

<!-- region: AutoConfigurationTest.java#query-builder-bean -->
```java
// inject it into a service: it joins the current transaction
TransactionTemplate tx = new TransactionTemplate(txManager);
long seen = tx.execute(status -> {
    members.save(new Member("Kim", true, 3, null));                       // not flushed yet
    return qb.from(Member.class).where(m -> m.getName().equals("Kim")).count();
});
```

Without Spring Boot, or when there are two or more `EntityManagerFactory` beans, create the bean yourself. Like the auto-configuration, use the shared `EntityManager`.

<!-- region: ManualJavaStartTest.java#config -->
```java
@Configuration
public static class LambdaQlConfig {

    @Bean
    public QueryBuilder queryBuilder(EntityManagerFactory entityManagerFactory) {
        return new QueryBuilder(SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory));
    }
}
```

`SharedEntityManagerCreator` is a Spring class (`org.springframework.orm.jpa`).
Do not use `new QueryBuilder(entityManagerFactory)` in a Spring application. This builder opens its own `EntityManager` for each query, and this `EntityManager` does not join Spring transactions.

**`@DataJpaTest` tests.** A `@DataJpaTest` slice test loads only some auto-configurations. Repository methods work, but the `QueryBuilder` bean is not created.
If your test needs this bean, use a test that loads the full auto-configuration, such as `@SpringBootTest`.

Example code: [`AutoConfigurationTest.java`](https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/AutoConfigurationTest.java) (`query-builder-bean`), [`ManualJavaStartTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaStartTest.java) (`config`).

<a id="129-kotlin-repositories"></a>
## 12.9 Kotlin repositories

Repositories written in Kotlin need a compiler setting. Kotlin 2.2 or later works with the default settings, and versions before 2.2 need `-Xjvm-default=all`. Spring Data also needs `kotlin-reflect`.
For details, see the [Kotlin version of this page](../kotlin/12-spring-data-jpa.md#129-kotlin-compile-settings).

<a id="1210-not-implemented-yet"></a>
## 12.10 What is not supported

- **`ignoreCase()`** of Spring `Sort`. Calling it throws `UnsupportedOperationException` (see [12.6](#126-transactions-and-exceptions)).

---

[Java track](README.md) | Previous: [11. Errors and languages](../11-errors-and-languages.md) | Next: [13. Spring Boot 3 and 4](../13-spring-boot-versions.md) | [Kotlin version of this page](../kotlin/12-spring-data-jpa.md)
