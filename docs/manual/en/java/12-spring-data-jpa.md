<a id="12-spring-data-jpa-java"></a>
# 12. Spring Data JPA (Java)

[Java track](README.md) | [Manual home](../README.md) | Previous: [11. Errors and languages](../11-errors-and-languages.md) | [Kotlin version of this page](../kotlin/12-spring-data-jpa.md)

---

<a id="121-what-you-get"></a>
## 12.1 What you get

With Spring Data JPA you write repository interfaces. lambdaQL adds one more interface, `LambdaQlExecutor<T>`.

Your repository extends this interface, and you write your own query methods as **default methods** that use lambdaQL.
You do not write a `...RepositoryImpl` class for these methods.

| Part | What it does | Section |
|---|---|---|
| `LambdaQlExecutor<T>` | `findAll`, `findSet`, `findFirst`, `findOne`, `count`, `exists` and `query()` with a lambda condition, in every repository | [12.3](#123-repository-setup), [12.4](#124-executor-methods) |
| Spring `Sort` and `Pageable` | `findAll(where, sort)`, `findSet(where, sort)` and `findAll(where, pageable)`, which returns a Spring `Page` | [12.5](#125-spring-sort-and-pageable) |
| Update, delete, insert, upsert | Shortcuts such as `deleteAll(where)` and `updateOne(where, set)` run a statement in one call. `updateQuery()`, `deleteQuery()`, `insertQuery()`, `upsertQuery()` start the statements of [7. Update, insert, delete](07-dml.md) on the repository's entity. All need the caller's transaction | [12.4](#124-executor-methods), [12.6](#126-transactions-and-exceptions) |
| `QueryBuilder` bean | Spring Boot makes a `QueryBuilder` bean for your services | [12.8](#128-the-querybuilder-bean) |

<a id="122-modules"></a>
## 12.2 Modules

lambdaQL has three artifacts. The core has no Spring dependency, so you can use it with plain JPA too.

| Artifact | What it has | You need it when |
|---|---|---|
| `lambdaql-core` | The engine and `QueryBuilder`. No Spring dependency | Always. The other two artifacts bring it |
| `lambdaql-spring-data-jpa` | `LambdaQlExecutor<T>`, its Spring Data registration, the Spring Boot auto-configuration (`QueryBuilder` bean) | You use Spring Data JPA without Spring Boot |
| `lambdaql-spring-boot-starter` | No code. It brings `lambdaql-spring-data-jpa` and `spring-boot-starter-data-jpa` | You use Spring Boot |

The artifacts are **not published to Maven Central yet**. Build them from source and publish them to your local Maven repository first (see [2. Getting started](02-getting-started.md#build-from-source)). The version is `1.0.0`.

With Spring Boot, Gradle (Groovy DSL):

```groovy
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation 'org.lambdaql:lambdaql-spring-boot-starter:1.0.0'
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

Without Spring Boot, add `lambdaql-spring-data-jpa` next to your own Spring Data JPA setup (Hibernate and a JDBC driver). It brings `lambdaql-core` and Spring Data JPA.

```groovy
dependencies {
    implementation 'org.lambdaql:lambdaql-spring-data-jpa:1.0.0'
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
| Java | 21 |
| Spring Boot | 3.4 or newer (tested with 3.4.2). Spring Boot 4 is not verified yet |
| Spring Data JPA | 3.4 or newer |
| Hibernate | 6.6 (Spring Boot 3.4 already brings it) |

Spring Data JPA 3.3 and older are **not supported**. lambdaQL registers its executor in `META-INF/spring.factories`, and Spring Data reads repository fragments from this file only from 3.4.

<a id="123-repository-setup"></a>
## 12.3 Repository setup

Add `LambdaQlExecutor<T>` to the `extends` list of your repository. `T` is the entity of the repository.
You write no configuration and no `Impl` class.

The examples in this chapter use the test entities `Member` (`id`, `name`, `active`, `grade`, `team`) and `Team` (`id`, `name`). `grade` is an `Integer`, so it can be NULL.

**(tested)** [`MemberRepository.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/sample/MemberRepository.java), region `repository`:

```java
/** No Impl class: Spring Data finds the LambdaQlExecutor implementation by itself. */
public interface MemberRepository extends JpaRepository<Member, Long>, LambdaQlExecutor<Member>,
        JpaSpecificationExecutor<Member>, MemberRepositoryCustom {

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
                .orderBy((m, by) -> by.desc(m.getGrade()).asc(m.getId()))
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

The last three methods run update, delete and insert statements. They need a transaction (see [12.6](#126-transactions-and-exceptions)).

This repository also extends `JpaSpecificationExecutor<Member>` and a hand-written fragment, `MemberRepositoryCustom`.
lambdaQL does not need either of them. They are there to check that everything works together (see [12.7](#127-other-fragments-and-jpaspecificationexecutor)).

How it works:

- **Spring Data runs default methods by itself.** It does not try to make a query from a default method. It just calls the method body. lambdaQL does not change this.
- **`LambdaQlExecutor<T>` is a Spring Data repository fragment.** Its implementation is listed in `META-INF/spring.factories` of `lambdaql-spring-data-jpa`. Spring Data reads this file and adds the implementation to every repository that extends `LambdaQlExecutor<T>`.
- **One executor object is shared by all repositories.** Each call still uses the entity of its own repository. For example, `teams.count(t -> ...)` on a `TeamRepository` counts `Team` rows (tested in [`AutoConfigurationTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/AutoConfigurationTest.java)).

Rules:

| # | Rule |
|---|---|
| 1 | Your repository must extend `LambdaQlExecutor<T>` **directly** (in its own `extends` list). |
| 2 | Do not put `@NoRepositoryBean` on an interface that extends `LambdaQlExecutor<T>` for this purpose. Spring Data then does not add the lambdaQL implementation. |
| 3 | Write your lambdaQL queries in **default methods** or in services. Abstract methods are still Spring Data query methods (`findByName` and so on). |
| 4 | Without Spring Boot, the repository part still works, because it is plain Spring Data. Only the `QueryBuilder` bean is not made for you (see [12.8](#128-the-querybuilder-bean)). |

<a id="124-executor-methods"></a>
## 12.4 Executor methods

`Where<T>` is the same condition lambda as in `where(...)` (see [3. Conditions](03-where.md)). `SortSpec<T>` is a lambdaQL sort (see [4.10](04-order-paging.md#410-reusable-and-external-sorts)).
`Sort`, `Pageable` and `Page` are the Spring Data types.

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
| `count(Where<T>)` | `long` | The number of rows that match |
| `exists(Where<T>)` | `boolean` | `true` if a row matches. Reads one row at most and loads no entity |
| `deleteAll(Where<T>)` | `int` | Deletes every row that matches, with one SQL statement (bulk). Not the same as Spring Data's `deleteAll()` (see [below](#deleteallwhere-is-not-spring-datas-deleteall)). Needs a transaction |
| `deleteOne(Where<T>)` | `int` | Deletes the only row that matches, with the [one-row protection](#one-row-protection-deleteone-and-updateone). Returns 0 or 1. Needs a transaction |
| `updateAll(Where<T>, Assign<T>)` | `int` | Changes every row that matches. The condition comes first. Needs a transaction |
| `updateOne(Where<T>, Assign<T>)` | `int` | Changes the only row that matches, with the one-row protection. Returns 0 or 1. Needs a transaction |
| `insertOne(Assign<T>)` | `int` | Inserts one row. Returns 1. Needs a transaction |
| `insertAll(Collection<L>, AssignEach<T, L>)` | `int` | Inserts one row per element (at most 500 values per statement, rule U13). Needs a transaction |
| `upsertOne(Assign<T>)` | `int` | Inserts one row, or changes it if a row with the same `@Id` exists. Returns the row count of the database. Needs a transaction |
| `upsertAll(Collection<L>, AssignEach<T, L>)` | `int` | Upserts one row per element (one statement per row). Needs a transaction |
| `updateQuery()` | `UpdateQuery<T>` | Starts an update: `updateQuery().set(...).where(...).execute()` (see [7.2](07-2-update.md)). Needs a transaction |
| `deleteQuery()` | `DeleteQuery<T>` | Starts a delete: `deleteQuery().where(...).execute()` (see [7.4](07-4-delete.md)). Needs a transaction |
| `insertQuery()` | `InsertQuery<T>` | Starts an insert: `insertQuery().values(...).execute()` (see [7.5](07-5-insert.md)). Needs a transaction |
| `upsertQuery()` | `UpsertQuery<T>` | Starts an upsert keyed by the `@Id` (see [7.6](07-6-upsert.md)). Needs a transaction |

The short methods are the same as `query()` with a result function: `findAll(w)` is `query().where(w).toList()`, `findSet(w)` is `query().where(w).toSet()`, `findFirst(w)` is `query().where(w).first()`, and `findOne(w)` is `query().where(w).one()`. The result functions are explained in [Getting the result](02-getting-started.md#getting-the-result).
`findAll(Where<T>)` and `count(Where<T>)` do not clash with `findAll(Sort)`, `findAll(Example)` or `count()` from `JpaRepository`, because the argument types are different.

**`query()`: the full query.** **(tested)** [`MemberRepository.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/sample/MemberRepository.java), region `query` (a part of the repository in 12.3):

```java
/**
 * @param n how many
 * @return the active members with the highest grades
 */
default List<Member> topGraded(int n) {
    return query()
            .where(m -> m.isActive())
            .orderBy((m, by) -> by.desc(m.getGrade()).asc(m.getId()))
            .limit(n)
            .toList();
}
```

In the next examples, `repository` is a `MemberRepository`.

**(tested)** [`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java), region `find-all-sort`:

```java
SortSpec<Member> byGrade = SortSpec.of(Member.class, (m, by) -> by.desc(m.getGrade()).asc(m.getId()));
List<Member> active = repository.findAll(m -> m.isActive(), byGrade);
```

**(tested)** [`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java), region `find-first` (the active member with the highest grade):

```java
Optional<Member> best = repository.findFirst(m -> m.isActive(),
        SortSpec.of(Member.class, (m, by) -> by.desc(m.getGrade())));
```

**(tested)** [`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java), region `find-one`:

```java
Optional<Member> kim = repository.findOne(m -> m.getName().equals("Kim"));
// two or more rows: Spring's IncorrectResultSizeDataAccessException
assertThrows(IncorrectResultSizeDataAccessException.class, () -> repository.findOne(m -> m.isActive()));
```

**(tested)** [`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java), region `count-exists`:

```java
long active = repository.count(m -> m.isActive());
boolean anyInTeamB = repository.exists(m -> m.getTeam().getName().equals("B"));
```

**(tested)** [`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java), region `find-set`:

```java
java.util.SequencedSet<Member> active = repository.findSet(m -> m.isActive(), Sort.by(Sort.Order.desc("grade"), Sort.Order.asc("id")));
java.util.SequencedSet<Member> any = repository.findSet(m -> m.isActive());
```

`active` keeps the order of the `Sort`. `any` has no fixed order, like `findAll(where)`.

<a id="dml-shortcuts"></a>
### DML shortcuts

These 8 methods run an update, delete, insert or upsert in one call.

| Shortcut | Same as |
|---|---|
| `deleteAll(where)` | `deleteQuery().where(where).execute()` |
| `deleteOne(where)` | `deleteQuery().where(where).executeOne()` |
| `updateAll(where, set)` | `updateQuery().set(set).where(where).execute()` |
| `updateOne(where, set)` | `updateQuery().set(set).where(where).executeOne()` |
| `insertOne(values)` | `insertQuery().values(values).execute()` |
| `insertAll(rows, values)` | `insertQuery().values(rows, values).execute()` |
| `upsertOne(values)` | `upsertQuery().values(values).execute()` |
| `upsertAll(rows, values)` | `upsertQuery().values(rows, values).execute()` |

- **The condition comes first**, the assignments second: `updateAll(m -> !m.isActive(), m -> m.setGrade(7))`.
- **`All`** means every row that matches (delete, update), or every element of the list (insert, upsert).
- **`One`** means the one-row protection for delete and update (see the next part), and one row for insert and upsert.
- They need the caller's transaction (see [12.6](#126-transactions-and-exceptions)). The test runs them in a `TransactionTemplate`.

**(tested)** [`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java), region `dml-shortcuts`:

```java
int changed = repository.updateAll(m -> !m.isActive(), m -> m.setGrade(7));      // condition first
int deleted = repository.deleteAll(m -> m.getTeam() == null);                     // bulk: no cascade
int one = repository.insertOne(m -> { m.setName("Guest"); m.setActive(false); });
int many = repository.insertAll(List.of(new NewMember("A", 1), new NewMember("B", 2)),
        (m, r) -> { m.setName(r.name()); m.setGrade(r.grade()); m.setActive(true); });
```

`NewMember` is a record in the test: `record NewMember(String name, int grade)`.

<a id="one-row-protection-deleteone-and-updateone"></a>
### One-row protection: `deleteOne` and `updateOne`

`deleteOne(where)` and `updateOne(where, set)` never change more than one row.

1. lambdaQL reads the ids of the rows that match, at most 2.
2. No id: nothing changes. The result is 0.
3. 2 ids: nothing changes. The method throws `IncorrectResultSizeDataAccessException`.
4. 1 id: lambdaQL changes that row with `id = :p and (your condition)`. The result is 1, or 0 if the row stopped matching in the meantime.

The statement itself names one id, so it cannot change a second row, even if another transaction adds a matching row in the meantime. The protection does not rely on a rollback.

The core API has the same protection: `executeOne()` (see [7.4](07-4-delete.md#delete-exactly-one-row-executeone)).

**(tested)** [`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java), region `dml-one`:

```java
int renamed = repository.updateOne(m -> m.getId() == kimId, m -> m.setName("Kim2"));   // exactly one row
// two or more rows match: nothing changes
assertThrows(org.springframework.dao.IncorrectResultSizeDataAccessException.class,
        () -> repository.deleteOne(m -> m.isActive()));
```

<a id="deleteallwhere-is-not-spring-datas-deleteall"></a>
### `deleteAll(where)` is not Spring Data's `deleteAll()`

| Method | From | How it deletes | Cascade, `@PreRemove` |
|---|---|---|---|
| `deleteAll()` | `CrudRepository` | Loads every entity, then removes them one by one | run |
| `deleteAll(Iterable)` | `CrudRepository` | Removes the given entities one by one | run |
| `deleteAll(Where<T>)` | `LambdaQlExecutor<T>` | One SQL `delete` statement (bulk) | **not run** |

`deleteAll(where)` does not load the entities. Like every bulk statement, it does not change entities already loaded (rule U16).

Use it to delete many rows quickly. Use Spring Data's methods when you need cascade or `@PreRemove`.

The compiler picks the method by the argument: no argument, an `Iterable`, or a lambda. This was checked in the bytecode from javac and kotlinc 2.3.21 (measured).

<a id="builders-for-complex-statements"></a>
### Builders for complex statements

`updateQuery()`, `deleteQuery()`, `insertQuery()` and `upsertQuery()` start a full statement. Use them for what the shortcuts cannot do: many `set()` calls, `all()`, `orderBy(...).limit(n)` (see [7.4](07-4-delete.md#delete-the-first-rows-limit)), `toHql()`, or `onConflict(...)`.

The names end with `Query`, so they do not clash with `JpaRepository` methods such as `delete(...)`. The statements are the same as with `QueryBuilder` (see [7. Update, insert, delete](07-dml.md)). The rows are the same as a select with the same `where` lambda.

They need the caller's transaction, like the shortcuts. Like every JPA bulk statement, they do not change entities already loaded in the transaction.

**(tested)** [`MemberRepository.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/sample/MemberRepository.java), region `dml` (a part of the repository in 12.3):

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

The upsert runs in a transaction too. `teams` is a `TeamRepository`, and `tx` is a Spring `TransactionTemplate`.

**(tested)** [`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java), region `upsert`:

```java
int changed = tx.execute(s -> teams.upsertQuery().values(t -> { t.setId(teamId); t.setName("A2"); }).execute());
```

The upsert returns the row count of the database: 1 for an insert, and for an update 1 on H2 and Oracle but 2 on MariaDB (see [7.6](07-6-upsert.md#the-row-count)).

<a id="125-spring-sort-and-pageable"></a>
## 12.5 Spring `Sort` and `Pageable`

`findAll(where, sort)`, `findSet(where, sort)`, `findFirst(where, sort)` and `findAll(where, pageable)` take the Spring Data types.
lambdaQL turns each `Sort.Order` into one sort key with `SortSpec.ofPath` (see [4.10](04-order-paging.md#410-reusable-and-external-sorts)).

| Spring | In lambdaQL |
|---|---|
| A property name (`"grade"`, `"team.name"`) | Checked with Spring Data `PropertyPath`, the same as in Spring Data's own repositories. An unknown name gives `PropertyReferenceException`. The path must end at a basic value, not at an entity. A to-one path such as `team.name` uses a `left join`, so rows without a team are kept. Then the sort policy checks the name (see [below](#sort-policy-with-spring)) |
| An alias of the sort policy (`"level"`) | Passed on as it is, without the `PropertyPath` check. The sort policy turns it into its path |
| `ASC` / `DESC` with `NullHandling.NATIVE` (the default) | The lambdaQL NULL rule: NULLs first for ascending, NULLs last for descending. The order is the same on every database (see [4.4](04-order-paging.md#44-null-ordering-is-the-same-on-every-database)) |
| `NullHandling.NULLS_FIRST` / `NULLS_LAST` | Used as written (`ASC_NULLS_FIRST`, `DESC_NULLS_LAST` and so on) |
| `ignoreCase()` | Not supported yet. It throws `UnsupportedOperationException` |
| `Sort.unsorted()` | No `order by` |

Spring Data JPA's own repositories leave the NULL position of `NATIVE` to the database, so the order there can be different on each database. lambdaQL uses its own rule instead.

**(tested)** [`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java), region `find-all-spring-sort`:

```java
List<Member> rows = repository.findAll(m -> m.isActive(),
        Sort.by(Sort.Order.desc("grade"), Sort.Order.asc("team.name"), Sort.Order.asc("id")));
// a property that does not exist: the same error as Spring Data's own repositories
assertThrows(PropertyReferenceException.class,
        () -> repository.findAll(m -> m.isActive(), Sort.by("nickname")));
```

Paging with `findAll(where, pageable)`:

- Page numbers start at 0, as in Spring Data. lambdaQL reads the page with `limit` and `offset`.
- The total comes from a `count` query. Spring skips it when the page alone shows the total (for example, the first page has fewer rows than the page size).
- When the sort keys do not contain the id, lambdaQL adds the id as the last key, so a row does not move between pages (see [Tie-breaker](04-order-paging.md#tie-breaker-automatic-id)). A page without a sort is ordered by the id.
- `Pageable.unpaged()` returns all rows in one page.

**(tested)** [`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java), region `page`:

```java
Page<Member> page = repository.findAll(m -> m.isActive(), PageRequest.of(1, 2, Sort.by("name")));
// page 1 (the second page) of the active members by name; the total comes from a count query
```

**Sort from request parameters.** Spring MVC can make a `Pageable` from request parameters such as `?page=1&size=20&sort=name,desc`.
Without a sort policy and without `@Sortable` fields, every persistent property is accepted, so a user can sort by any column. lambdaQL then logs `LQL-5003` at `INFO` level once for each entity and name.
To allow only some names, use the sort policy below.

<a id="sort-policy-with-spring"></a>
### The sort policy with Spring

The sort policy (decision D43) decides which outside sort names are allowed. `SortRules`, `SortMode` and the rules are explained in [4.10](04-order-paging.md#sort-policy).
A Spring `Sort` or `Pageable` is always an outside sort, so the policy checks every name in it. A sort lambda is never checked.

**Where the rules come from.** With Spring Boot, the auto-configuration builds the policy of the `EntityManagerFactory` and registers it after all singleton beans are made.
The auto-configuration reads three places.

| Place | What it gives |
|---|---|
| A `SortPolicy` bean | A mode and `SortRules` written with lambdas |
| The properties `lambdaql.sort.*` | A mode and rules written as strings |
| `@Sortable` fields ([4.10](04-order-paging.md#rules-from-sortable)) | Rules for an entity that has no rules from the bean or the properties |

- The rules of one entity come from one place only, in the order of the table. Rules from two places are not merged.
- `lambdaql.sort.mode` wins over the mode of the bean. Without both, the mode is `INFO`.
- This works when there is exactly one `EntityManagerFactory`, the same condition as for the `QueryBuilder` bean (see [12.8](#128-the-querybuilder-bean)). Otherwise call `SortPolicy.register(emf, policy)` yourself.
- Without a bean and without properties, nothing is registered, so the default applies: `INFO`, with rules only from `@Sortable`.

**Properties.**

| Property | Meaning |
|---|---|
| `lambdaql.sort.mode` | `OFF`, `INFO` (the default), `WARN` or `STRICT` |
| `lambdaql.sort.allow.<Entity>` | The allowed paths, separated by commas, for example `name,team.name` |
| `lambdaql.sort.alias.<Entity>.<outside name>` | The path of an alias, for example `lambdaql.sort.alias.Member.level=grade` |

**The SARGable mode property.** `lambdaql.sargable.mode` is one of `OFF`, `WARN` (the default) and `STRICT` (decision D50). The auto-configuration registers it in the same way as `lambdaql.sort.mode`. With `STRICT`, the analysis of a condition that cannot use an index fails with `NonSargableException` (`LQL-3066`) (see [8.9](08-procedures-functions.md#89-warning-functions-on-columns-and-indexes-sargable)).

`<Entity>` is the JPA entity name. By default it is the simple class name, and you can change it with `@Entity(name = ...)`.

The entity name is unique in a persistence unit (Hibernate stops at start-up when two entities have the same name). So two classes with the same name in different packages are never mixed up. An unknown name stops the application start with `IllegalStateException` (LQL-4042).
The paths are checked against the entity the first time the rules are used (see [Rules as strings](04-order-paging.md#rules-as-strings) in 4.10).

**(tested)** [`SortPropertiesTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/SortPropertiesTest.java), region `properties` (the properties of the test; in an application they go into `application.properties` or `application.yaml`):

```java
@SpringBootTest(classes = SampleApplication.class, properties = {
        "lambdaql.sort.mode=STRICT",
        "lambdaql.sort.allow.Member=name,team.name",
        "lambdaql.sort.alias.Member.level=grade"})
```

**(tested)** [`SortPropertiesTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/SortPropertiesTest.java), region `properties-use`:

```java
List<Member> byLevel = repository.findAll(m -> m.isActive(), Sort.by(Sort.Order.desc("level")));
List<Member> byTeam = repository.findAll(m -> m.isActive(), Sort.by("team.name", "name"));
assertThrows(SortNotAllowedException.class, () -> repository.findAll(m -> m.isActive(), Sort.by("grade")));
```

This test also checks that the auto-configuration registers the policy.

**A `SortPolicy` bean.** For rules that the compiler checks, define a `SortPolicy` bean. The same auto-configuration step registers it.
This bean is a design example. The test below registers the same policy by hand.

```java
@Bean
public SortPolicy sortPolicy() {
    return SortPolicy.of(SortMode.STRICT,
            SortRules.of(Member.class)
                    .allow(m -> m.getName())
                    .alias("level", m -> m.getGrade()));
}
```

**(tested)** [`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java), region `sort-policy` (the region ends inside a `try`; the `finally` block calls `SortPolicy.unregister(emf)`):

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

- An alias such as `level` is not a property of `Member`. lambdaQL passes it on without the Spring `PropertyPath` check, and the policy turns it into `grade`.
- A name that is neither an alias nor a property still gives `PropertyReferenceException` first, before the policy is asked.
- In `STRICT` mode, a property that is not in the rules gives `SortNotAllowedException` (`LQL-4040`). Spring does not translate it (see [12.6](#126-transactions-and-exceptions)).

**HTTP 400.** lambdaQL has no web dependency, so it does not choose the HTTP status. A web application usually answers `SortNotAllowedException` with 400 Bad Request.
This handler is an example only (not tested).

```java
@RestControllerAdvice
public class SortErrorHandler {

    @ExceptionHandler(SortNotAllowedException.class)
    public ProblemDetail sortNotAllowed(SortNotAllowedException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setProperty("code", e.code());      // "LQL-4040"
        return problem;
    }
}
```

The message names the entity class and the sort name. If clients must not see the entity name, send only `e.code()`.

<a id="126-transactions-and-exceptions"></a>
## 12.6 Transactions and exceptions

The executor uses Spring's shared `EntityManager`, which is bound to the current transaction.

**lambdaQL puts no `@Transactional` on its methods, on purpose** (decision D41).
An update, delete, insert or upsert needs the caller's transaction. This is true for the shortcuts (`deleteAll`, `updateOne`, ...), the builders (`updateQuery()`, ...) and the `QueryBuilder` bean.

Put `@Transactional` on your service method, or use a `TransactionTemplate`. Without a transaction, the call fails before any SQL is sent.

| Case | What happens |
|---|---|
| A select inside `@Transactional` | lambdaQL runs in the same transaction. It sees changes that are not flushed yet. After a rollback, these changes are gone for lambdaQL too (tested in `ExecutorTest`) |
| A select outside a transaction | Spring uses a new `EntityManager` for the call and closes it after the call, so the returned entities are detached. This is Spring's normal behavior; it is not tested by lambdaQL |
| An update, delete, insert or upsert inside `@Transactional` or a `TransactionTemplate` | It runs in that transaction and rolls back with it (tested) |
| An update, delete, insert or upsert outside a transaction | It fails before any SQL is sent. Nothing changes (tested, see the next table) |

Only `new QueryBuilder(entityManagerFactory)` runs DML in its own transaction (see [7.8](07-dml.md#transactions)). Do not use it in a Spring application (see [12.8](#128-the-querybuilder-bean)).

Errors go through Spring's exception translation, the same as for other repository methods.

| Case | Exception |
|---|---|
| `findOne` matches 2 or more rows | `IncorrectResultSizeDataAccessException` (Spring), instead of JPA's `NonUniqueResultException` (tested) |
| `deleteOne` or `updateOne` matches 2 or more rows | `IncorrectResultSizeDataAccessException`. Nothing is changed (tested) |
| An unknown property in a Spring `Sort` | `PropertyReferenceException` (tested) |
| `ignoreCase()` in a Spring `Sort` | `UnsupportedOperationException` (tested) |
| A sort name that the sort policy does not allow, in `STRICT` mode ([12.5](#sort-policy-with-spring)) | `SortNotAllowedException` (`LQL-4040`), not translated (tested) |
| An update, delete, insert or upsert without a transaction, in a repository call (a shortcut, or `execute()` inside a default method) | `InvalidDataAccessApiUsageException` (tested), made by Spring from JPA's `TransactionRequiredException`. Outside a repository method (the `QueryBuilder` bean), JPA's `TransactionRequiredException` |
| An assignment lambda that breaks a rule (U1, U3, U4, U6) | `LambdaQLException` (for example `TranslationException`, `LQL-3042`), not translated (see [7.8](07-dml.md#rules-checked-at-analysis-time)) |

The `QueryBuilder` bean (12.8) is not a repository, so Spring does not translate its errors. There, `one()` and `executeOne()` with 2 or more rows throw `NonUniqueResultException`.

<a id="127-other-fragments-and-jpaspecificationexecutor"></a>
## 12.7 Other fragments and `JpaSpecificationExecutor`

`LambdaQlExecutor<T>` is one fragment among others. The repository in 12.3 uses all of these at the same time (tested in `ExecutorTest`).

| Used together with | Works | Notes |
|---|---|---|
| A hand-written fragment (`MemberRepositoryCustom` with `MemberRepositoryImpl`) | Yes | Spring Data finds the `Impl` class as usual |
| `JpaSpecificationExecutor<T>` | Yes | A lambdaQL lambda has one argument, a `Specification` lambda has three, so Java always picks the right `findAll` |
| The `JpaRepository` methods (`findAll()`, `count()`, `save(...)`) | Yes | Their argument types are different |

These calls come from the test method `worksWithOtherFragments` in `ExecutorTest`.

```java
repository.countByHand();                                                  // hand-written fragment
repository.findAll((root, query, cb) -> cb.isTrue(root.get("active")));    // Specification: three arguments
repository.findAll(m -> m.isActive());                                     // lambdaQL Where: one argument
repository.count();                                                        // Spring Data's own count()
```

<a id="128-the-querybuilder-bean"></a>
## 12.8 The `QueryBuilder` bean

With Spring Boot, the auto-configuration `org.lambdaql.spring.boot.LambdaQlAutoConfiguration` makes a `QueryBuilder` bean. Inject it into your services for queries that do not belong to one repository.

- The bean uses Spring's shared, transaction-bound `EntityManager`. Its queries join the current transaction, the same as the repository methods.
- The bean is made only when there is exactly one `EntityManagerFactory` and you did not define your own `QueryBuilder` bean.
- The repository methods (`LambdaQlExecutor<T>`) do not need this bean.
- Its update, delete, insert and upsert statements need the caller's transaction, the same as the repository methods. Without one, they throw `TransactionRequiredException` before any SQL is sent.

**(tested)** [`AutoConfigurationTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/AutoConfigurationTest.java), region `query-builder-bean` (`qb` is the injected bean):

```java
// inject it into a service: it joins the current transaction
TransactionTemplate tx = new TransactionTemplate(txManager);
long seen = tx.execute(status -> {
    members.save(new Member("Kim", true, 3, null));                       // not flushed yet
    return qb.from(Member.class).where(m -> m.getName().equals("Kim")).count();
});
```

Without Spring Boot, or with two or more `EntityManagerFactory` beans, make the bean yourself. Use a shared `EntityManager`, as the auto-configuration does.

```java
@Bean
public QueryBuilder queryBuilder(EntityManagerFactory entityManagerFactory) {
    // the same as the auto-configuration: a shared, transaction-bound EntityManager
    return new QueryBuilder(SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory));
}
```

`SharedEntityManagerCreator` is a Spring class (`org.springframework.orm.jpa`).
Do not use `new QueryBuilder(entityManagerFactory)` in a Spring application. That builder opens its own `EntityManager` for each query, and this `EntityManager` does not join the Spring transaction.

**Tests with `@DataJpaTest`.** A `@DataJpaTest` slice test loads only some auto-configurations. The repository methods work there, but the `QueryBuilder` bean is not made.
If a test needs it, add `@ImportAutoConfiguration(LambdaQlAutoConfiguration.class)` to the test class (not tested yet).

<a id="129-kotlin-repositories"></a>
## 12.9 Kotlin repositories

Repositories written in Kotlin need compiler settings. Kotlin 2.2 or newer works with the default settings, and older Kotlin needs `-Xjvm-default=all`. Spring Data also needs `kotlin-reflect`.
For details, see [the Kotlin version of this page](../kotlin/12-spring-data-jpa.md#129-kotlin-compile-settings).

<a id="1210-not-implemented-yet"></a>
## 12.10 Not implemented yet

- **Spring Boot 4.** The auto-configuration names the Spring Boot 4 classes too, but only Spring Boot 3.4.2 is tested.
- **`ignoreCase()`** in a Spring `Sort`.
- **Kotlin repositories** are not tested yet.

---

[Java track](README.md) | Previous: [11. Errors and languages](../11-errors-and-languages.md) | [Kotlin version of this page](../kotlin/12-spring-data-jpa.md)
