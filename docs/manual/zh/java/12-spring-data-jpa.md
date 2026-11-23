<a id="12-spring-data-jpa-java"></a>
# 12. Spring Data JPA（Java）

> [!IMPORTANT]
> 本页比韩文手册旧。最新规则请参见[韩文页面](../../ko/java/12-spring-data-jpa.md)。

[Java 路线](README.md) | [手册首页](../README.md) | 上一章：[11. 错误与语言](../11-errors-and-languages.md) | [本页的 Kotlin 版本](../kotlin/12-spring-data-jpa.md)

---

<a id="121-what-you-get"></a>
## 12.1 你将得到什么

使用 Spring Data JPA 时，你编写 repository 接口。lambdaQL 额外提供一个接口 `LambdaQlExecutor<T>`。
你的 repository 继承它，然后把自己的查询方法写成内部使用 lambdaQL 的**默认方法**。
这些方法不需要编写 `...RepositoryImpl` 类。

| 组成部分 | 作用 | 小节 |
|---|---|---|
| `LambdaQlExecutor<T>` | 在每个 repository 中提供带 Lambda 条件的 `findAll`、`findSet`、`findFirst`、`findOne`、`count`、`exists` 和 `query()` | [12.3](#123-repository-setup)、[12.4](#124-executor-methods) |
| Spring `Sort` 和 `Pageable` | `findAll(where, sort)`、`findSet(where, sort)` 和返回 Spring `Page` 的 `findAll(where, pageable)` | [12.5](#125-spring-sort-and-pageable) |
| 更新、删除、插入、upsert | `deleteAll(where)`、`updateOne(where, set)` 等快捷方法一次调用就执行一条语句。`updateQuery()`、`deleteQuery()`、`insertQuery()`、`upsertQuery()` 在 repository 的实体上开始 [7. 更新、插入、删除](07-dml.md) 中的语句。它们都需要调用方的事务 | [12.4](#124-executor-methods)、[12.6](#126-transactions-and-exceptions) |
| `QueryBuilder` bean | Spring Boot 为你的服务创建一个 `QueryBuilder` bean | [12.8](#128-the-querybuilder-bean) |

<a id="122-modules"></a>
## 12.2 模块

lambdaQL 有三个构件。核心不依赖 Spring，因此也可以和普通 JPA 一起使用。

| 构件 | 包含内容 | 何时需要 |
|---|---|---|
| `lambdaql-core` | 引擎和 `QueryBuilder`。不依赖 Spring | 始终需要。另外两个构件会带入它 |
| `lambdaql-spring-data-jpa` | `LambdaQlExecutor<T>`、它在 Spring Data 中的注册、Spring Boot 自动配置（`QueryBuilder` bean） | 不使用 Spring Boot 而使用 Spring Data JPA 时 |
| `lambdaql-spring-boot-starter` | 没有代码。它带入 `lambdaql-spring-data-jpa` 和 `spring-boot-starter-data-jpa` | 使用 Spring Boot 时 |

这些构件**尚未发布到 Maven Central**。请先从源码构建，并发布到本地 Maven 仓库（见 [2. 入门](02-getting-started.md#build-from-source)）。版本是 `1.0.0`。

使用 Spring Boot 时，Gradle（Groovy DSL）：

```groovy
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation 'org.lambdaql:lambdaql-spring-boot-starter:1.0.0'
}
```

Maven：

```xml
<dependency>
  <groupId>org.lambdaql</groupId>
  <artifactId>lambdaql-spring-boot-starter</artifactId>
  <version>1.0.0</version>
</dependency>
```

不使用 Spring Boot 时，在你自己的 Spring Data JPA 配置（Hibernate 和 JDBC 驱动）之外添加 `lambdaql-spring-data-jpa`。它会带入 `lambdaql-core` 和 Spring Data JPA。

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

要求：

| 项目 | 版本 |
|---|---|
| Java | 21 |
| Spring Boot | 4.1（已用 4.1.2 测试）。本版未验证 Spring Boot 3 |
| Spring Data JPA | 4.1（已用 4.1.2 测试） |
| Hibernate | 7.4（Spring Boot 4.1 已经带入） |

**不支持** Spring Data JPA 3.3 及更早版本。lambdaQL 在 `META-INF/spring.factories` 中注册其执行器，而 Spring Data 从 3.4 起才从这个文件读取 repository fragment。

<a id="123-repository-setup"></a>
## 12.3 设置 repository

把 `LambdaQlExecutor<T>` 加到你的 repository 的 `extends` 列表中。`T` 是该 repository 的实体。
你不需要编写任何配置，也不需要 `Impl` 类。

本章的示例使用测试实体 `Member`（`id`、`name`、`active`、`grade`、`team`）和 `Team`（`id`、`name`）。`grade` 是 `Integer`，因此可以为 NULL。

**(已测试)** [`MemberRepository.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/sample/MemberRepository.java)，region `repository`：

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

最后三个方法执行更新、删除和插入语句。它们需要事务（见 [12.6](#126-transactions-and-exceptions)）。
这个 repository 还继承了 `JpaSpecificationExecutor<Member>` 和一个手写的 fragment `MemberRepositoryCustom`。lambdaQL 不需要它们。它们用于检查所有部分能否一起工作（见 [12.7](#127-other-fragments-and-jpaspecificationexecutor)）。

工作原理：

- **默认方法由 Spring Data 自身执行。** Spring Data 不会尝试从默认方法生成查询，它只是调用方法主体。lambdaQL 不改变这一点。
- **`LambdaQlExecutor<T>` 是一个 Spring Data repository fragment。** 它的实现登记在 `lambdaql-spring-data-jpa` 的 `META-INF/spring.factories` 中。Spring Data 读取该文件，并把实现添加到每个继承了 `LambdaQlExecutor<T>` 的 repository 中。
- **所有 repository 共享一个执行器对象。** 每次调用仍然使用它自己的 repository 的实体。例如，在 `TeamRepository` 上调用 `teams.count(t -> ...)` 统计的是 `Team` 的行（已在 [`AutoConfigurationTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/AutoConfigurationTest.java) 中测试）。

规则：

| # | 规则 |
|---|---|
| 1 | 你的 repository 必须**直接**继承 `LambdaQlExecutor<T>`（写在它自己的 `extends` 列表中）。 |
| 2 | 不要在为此目的继承 `LambdaQlExecutor<T>` 的接口上加 `@NoRepositoryBean`。否则 Spring Data 不会添加 lambdaQL 的实现。 |
| 3 | 把 lambdaQL 查询写在**默认方法**或服务中。抽象方法仍然是 Spring Data 的查询方法（`findByName` 等）。 |
| 4 | 不使用 Spring Boot 时，repository 部分仍然可用，因为它就是普通的 Spring Data。只是 `QueryBuilder` bean 不会自动创建（见 [12.8](#128-the-querybuilder-bean)）。 |

<a id="124-executor-methods"></a>
## 12.4 执行器方法

`Where<T>` 与 `where(...)` 中的条件 Lambda 相同（见 [3. 条件](03-where.md)）。`SortSpec<T>` 是 lambdaQL 的排序（见 [4.10](04-order-paging.md#410-reusable-and-external-sorts)）。`Sort`、`Pageable` 和 `Page` 是 Spring Data 的类型。

| 方法 | 返回值 | 说明 |
|---|---|---|
| `query()` | `SelectQuery<T>` | 在 repository 的实体上开始一个查询。用于简短方法做不到的所有事情：多次调用 `where`、`limit`、`offset`、运行时的 `Direction` |
| `from(Class<E>)` | `SelectQuery<E>` | 在同一持久化单元的另一个实体上开始一个查询 |
| `findAll(Where<T>)` | `List<T>` | 所有匹配的行，顺序不固定 |
| `findAll(Where<T>, SortSpec<T>)` | `List<T>` | 所有匹配的行，已排序 |
| `findAll(Where<T>, Sort)` | `List<T>` | 按 Spring `Sort` 排序（见 [12.5](#125-spring-sort-and-pageable)）。`Sort.unsorted()` 表示不排序 |
| `findAll(Where<T>, Pageable)` | `Page<T>` | 一页。只在需要时才执行 count 查询。`Pageable.unpaged()` 返回所有行 |
| `findSet(Where<T>)` | `SequencedSet<T>` | 以 Set 返回所有匹配的行，顺序不固定（与 `findAll(Where<T>)` 相同）。不加 `distinct`。Set 比行数小时：`IllegalStateException`（见 [`toSet()`](02-getting-started.md#toset)） |
| `findSet(Where<T>, SortSpec<T>)` | `SequencedSet<T>` | 按排序顺序以 Set 返回所有匹配的行 |
| `findSet(Where<T>, Sort)` | `SequencedSet<T>` | 同上，用 Spring `Sort` 排序（见 [12.5](#125-spring-sort-and-pageable)） |
| `findFirst(Where<T>)` | `Optional<T>` | 匹配的行中 id 最小的那一行 |
| `findFirst(Where<T>, SortSpec<T>)` | `Optional<T>` | 按该顺序的第一行。只读取一行 |
| `findFirst(Where<T>, Sort)` | `Optional<T>` | 按 Spring `Sort` 顺序的第一行 |
| `findOne(Where<T>)` | `Optional<T>` | 唯一匹配的行。2 行或更多行：`IncorrectResultSizeDataAccessException` |
| `count(Where<T>)` | `long` | 匹配的行数 |
| `exists(Where<T>)` | `boolean` | 有匹配的行时为 `true`。最多读取一行，且不加载实体 |
| `deleteAll(Where<T>)` | `int` | 用一条 SQL 语句（批量）删除所有匹配的行。与 Spring Data 的 `deleteAll()` 不同（见[下文](#deleteallwhere-is-not-spring-datas-deleteall)）。需要事务 |
| `deleteOne(Where<T>)` | `int` | 以[单行保护](#one-row-protection-deleteone-and-updateone)删除唯一匹配的行。返回 0 或 1。需要事务 |
| `updateAll(Where<T>, Assign<T>)` | `int` | 修改所有匹配的行。条件在前。需要事务 |
| `updateOne(Where<T>, Assign<T>)` | `int` | 以单行保护修改唯一匹配的行。返回 0 或 1。需要事务 |
| `insertOne(Assign<T>)` | `int` | 插入一行。返回 1。需要事务 |
| `insertAll(Collection<L>, AssignEach<T, L>)` | `int` | 每个元素插入一行（每条语句最多 500 个值，规则 U13）。需要事务 |
| `upsertOne(Assign<T>)` | `int` | 插入一行；如果已有相同 `@Id` 的行，则修改该行。返回数据库给出的行数。需要事务 |
| `upsertAll(Collection<L>, AssignEach<T, L>)` | `int` | 每个元素 upsert 一行（每行一条语句）。需要事务 |
| `updateQuery()` | `UpdateQuery<T>` | 开始一个更新：`updateQuery().set(...).where(...).execute()`（见 [7.2](07-2-update.md)）。需要事务 |
| `deleteQuery()` | `DeleteQuery<T>` | 开始一个删除：`deleteQuery().where(...).execute()`（见 [7.4](07-4-delete.md)）。需要事务 |
| `insertQuery()` | `InsertQuery<T>` | 开始一个插入：`insertQuery().values(...).execute()`（见 [7.5](07-5-insert.md)）。需要事务 |
| `upsertQuery()` | `UpsertQuery<T>` | 开始一个以 `@Id` 为键的 upsert（见 [7.6](07-6-upsert.md)）。需要事务 |

这些简短方法等同于 `query()` 加上一个结果函数：`findAll(w)` 是 `query().where(w).toList()`，`findSet(w)` 是 `query().where(w).toSet()`，`findFirst(w)` 是 `query().where(w).first()`，`findOne(w)` 是 `query().where(w).one()`，依此类推。结果函数的说明见[获取结果](02-getting-started.md#getting-the-result)。
`findAll(Where<T>)` 和 `count(Where<T>)` 与 `JpaRepository` 的 `findAll(Sort)`、`findAll(Example)` 或 `count()` 不冲突，因为参数类型不同。

**`query()`：完整的查询。** **(已测试)** [`MemberRepository.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/sample/MemberRepository.java)，region `query`（12.3 中 repository 的一部分）：

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

在下面的示例中，`repository` 是一个 `MemberRepository`。

**(已测试)** [`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java)，region `find-all-sort`：

```java
SortSpec<Member> byGrade = SortSpec.of(Member.class, (m, by) -> by.desc(m.getGrade()).asc(m.getId()));
List<Member> active = repository.findAll(m -> m.isActive(), byGrade);
```

**(已测试)** [`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java)，region `find-first`（等级最高的活跃成员）：

```java
Optional<Member> best = repository.findFirst(m -> m.isActive(),
        SortSpec.of(Member.class, (m, by) -> by.desc(m.getGrade())));
```

**(已测试)** [`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java)，region `find-one`：

```java
Optional<Member> kim = repository.findOne(m -> m.getName().equals("Kim"));
// two or more rows: Spring's IncorrectResultSizeDataAccessException
assertThrows(IncorrectResultSizeDataAccessException.class, () -> repository.findOne(m -> m.isActive()));
```

**(已测试)** [`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java)，region `count-exists`：

```java
long active = repository.count(m -> m.isActive());
boolean anyInTeamB = repository.exists(m -> m.getTeam().getName().equals("B"));
```

**(已测试)** [`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java)，region `find-set`：

```java
java.util.SequencedSet<Member> active = repository.findSet(m -> m.isActive(), Sort.by(Sort.Order.desc("grade"), Sort.Order.asc("id")));
java.util.SequencedSet<Member> any = repository.findSet(m -> m.isActive());
```

`active` 保持 `Sort` 的顺序。`any` 与 `findAll(where)` 一样，顺序不固定。

<a id="dml-shortcuts"></a>
### DML 快捷方法

八个方法一次调用就执行一条更新、删除、插入或 upsert：

| 快捷方法 | 等同于 |
|---|---|
| `deleteAll(where)` | `deleteQuery().where(where).execute()` |
| `deleteOne(where)` | `deleteQuery().where(where).executeOne()` |
| `updateAll(where, set)` | `updateQuery().set(set).where(where).execute()` |
| `updateOne(where, set)` | `updateQuery().set(set).where(where).executeOne()` |
| `insertOne(values)` | `insertQuery().values(values).execute()` |
| `insertAll(rows, values)` | `insertQuery().values(rows, values).execute()` |
| `upsertOne(values)` | `upsertQuery().values(values).execute()` |
| `upsertAll(rows, values)` | `upsertQuery().values(rows, values).execute()` |

- **条件在前**，赋值在后：`updateAll(m -> !m.isActive(), m -> m.setGrade(7))`。
- **`All`** 表示所有匹配的行（删除、更新），或列表中的所有元素（插入、upsert）。
- **`One`** 对删除和更新表示单行保护（见下一部分），对插入和 upsert 表示一行。
- 它们需要调用方的事务（见 [12.6](#126-transactions-and-exceptions)）。测试在 `TransactionTemplate` 中执行它们。

**(已测试)** [`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java)，region `dml-shortcuts`：

```java
int changed = repository.updateAll(m -> !m.isActive(), m -> m.setGrade(7));      // condition first
int deleted = repository.deleteAll(m -> m.getTeam() == null);                     // bulk: no cascade
int one = repository.insertOne(m -> { m.setName("Guest"); m.setActive(false); });
int many = repository.insertAll(List.of(new NewMember("A", 1), new NewMember("B", 2)),
        (m, r) -> { m.setName(r.name()); m.setGrade(r.grade()); m.setActive(true); });
```

`NewMember` 是测试中的一个 record：`record NewMember(String name, int grade)`。

<a id="one-row-protection-deleteone-and-updateone"></a>
### 单行保护：`deleteOne` 和 `updateOne`

`deleteOne(where)` 和 `updateOne(where, set)` 绝不会改变超过一行：

1. lambdaQL 读取匹配行的 id，最多 2 个。
2. 没有 id：什么都不改变。结果为 0。
3. 2 个 id：什么都不改变。方法抛出 `IncorrectResultSizeDataAccessException`。
4. 1 个 id：lambdaQL 用 `id = :p and (你的条件)` 改变该行。结果为 1；如果该行在此期间不再匹配，则为 0。

语句本身只指定一个 id，因此即使另一个事务在此期间添加了匹配的行，它也无法改变第二行。这种保护不依赖回滚。
核心 API 有同样的保护：`executeOne()`（见 [7.4](07-4-delete.md#delete-exactly-one-row-executeone)）。

**(已测试)** [`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java)，region `dml-one`：

```java
int renamed = repository.updateOne(m -> m.getId() == kimId, m -> m.setName("Kim2"));   // exactly one row
// two or more rows match: nothing changes
assertThrows(org.springframework.dao.IncorrectResultSizeDataAccessException.class,
        () -> repository.deleteOne(m -> m.isActive()));
```

<a id="deleteallwhere-is-not-spring-datas-deleteall"></a>
### `deleteAll(where)` 不是 Spring Data 的 `deleteAll()`

| 方法 | 来源 | 删除方式 | cascade、`@PreRemove` |
|---|---|---|---|
| `deleteAll()` | `CrudRepository` | 加载所有实体，然后逐个删除 | 执行 |
| `deleteAll(Iterable)` | `CrudRepository` | 逐个删除给定的实体 | 执行 |
| `deleteAll(Where<T>)` | `LambdaQlExecutor<T>` | 一条 SQL `delete` 语句（批量） | **不执行** |

`deleteAll(where)` 不加载实体。与所有批量语句一样，它不会修改已加载的实体（规则 U16）。
用它可以快速删除大量行。需要 cascade 或 `@PreRemove` 时，请使用 Spring Data 的方法。

编译器按参数选择方法：无参数、`Iterable` 或 Lambda。这已在 javac 和 kotlinc 2.3.21 生成的字节码中检查过（实测）。

<a id="builders-for-complex-statements"></a>
### 用于复杂语句的构建器

`updateQuery()`、`deleteQuery()`、`insertQuery()` 和 `upsertQuery()` 开始一条完整的语句。快捷方法做不到的事情用它们来做：多次调用 `set()`、`all()`、`orderBy(...).limit(n)`（见 [7.4](07-4-delete.md#delete-the-first-rows-limit)）、`toHql()` 或 `onConflict(...)`。
这些方法名以 `Query` 结尾，因此不会与 `delete(...)` 等 `JpaRepository` 方法冲突。语句与使用 `QueryBuilder` 时相同（见 [7. 更新、插入、删除](07-dml.md)）。处理的行与使用同一个 `where` Lambda 的查询相同。
它们与快捷方法一样需要调用方的事务。与所有 JPA 批量语句一样，它们不会修改事务中已加载的实体。

**(已测试)** [`MemberRepository.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/sample/MemberRepository.java)，region `dml`（12.3 中 repository 的一部分）：

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

upsert 也在事务中执行。`teams` 是 `TeamRepository`，`tx` 是 Spring 的 `TransactionTemplate`。

**(已测试)** [`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java)，region `upsert`：

```java
int changed = tx.execute(s -> teams.upsertQuery().values(t -> { t.setId(teamId); t.setName("A2"); }).execute());
```

upsert 返回数据库的行数：插入时为 1；更新时在 H2 和 Oracle 上为 1，在 MariaDB 上为 2（见 [7.6](07-6-upsert.md#the-row-count)）。

<a id="125-spring-sort-and-pageable"></a>
## 12.5 Spring `Sort` 与 `Pageable`

`findAll(where, sort)`、`findSet(where, sort)`、`findFirst(where, sort)` 和 `findAll(where, pageable)` 接受 Spring Data 的类型。
lambdaQL 用 `SortSpec.ofPath` 把每个 `Sort.Order` 转换为一个排序键（见 [4.10](04-order-paging.md#410-reusable-and-external-sorts)）。

| Spring | 在 lambdaQL 中 |
|---|---|
| 属性名（`"grade"`、`"team.name"`） | 用 Spring Data 的 `PropertyPath` 检查，与 Spring Data 自身的 repository 相同。未知的名称会抛出 `PropertyReferenceException`。路径必须以基本值结尾，而不是以实体结尾。`team.name` 这样的 to-one 路径使用 `left join`，因此没有 team 的行也会保留。之后由排序策略检查名称（见[下文](#sort-policy-with-spring)） |
| 排序策略的别名（`"level"`） | 不做 `PropertyPath` 检查，按原样传递。由排序策略变为其路径 |
| 带 `NullHandling.NATIVE`（默认值）的 `ASC` / `DESC` | lambdaQL 的 NULL 规则：升序时 NULL 在前，降序时 NULL 在后。在每个数据库上顺序都相同（见 [4.4](04-order-paging.md#44-null-ordering-is-the-same-on-every-database)） |
| `NullHandling.NULLS_FIRST` / `NULLS_LAST` | 按写法使用（`ASC_NULLS_FIRST`、`DESC_NULLS_LAST` 等） |
| `ignoreCase()` | 尚不支持。会抛出 `UnsupportedOperationException` |
| `Sort.unsorted()` | 没有 `order by` |

Spring Data JPA 自身的 repository 把 `NATIVE` 的 NULL 位置交给数据库决定，因此那里的顺序在不同数据库上可能不同。lambdaQL 则使用自己的规则。

**(已测试)** [`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java)，region `find-all-spring-sort`：

```java
List<Member> rows = repository.findAll(m -> m.isActive(),
        Sort.by(Sort.Order.desc("grade"), Sort.Order.asc("team.name"), Sort.Order.asc("id")));
// a property that does not exist: the same error as Spring Data's own repositories
assertThrows(PropertyReferenceException.class,
        () -> repository.findAll(m -> m.isActive(), Sort.by("nickname")));
```

用 `findAll(where, pageable)` 分页：

- 与 Spring Data 一样，页码从 0 开始。lambdaQL 用 `limit` 和 `offset` 读取该页。
- 总数来自一个 `count` 查询。当仅凭这一页就能得出总数时（例如第一页的行数少于页大小），Spring 会跳过它。
- 当排序键不包含 id 时，lambdaQL 会把 id 添加为最后一个键，这样行不会在页之间移动（见[决胜键](04-order-paging.md#tie-breaker-automatic-id)）。没有排序的页按 id 排序。
- `Pageable.unpaged()` 在一页中返回所有行。

**(已测试)** [`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java)，region `page`：

```java
Page<Member> page = repository.findAll(m -> m.isActive(), PageRequest.of(1, 2, Sort.by("name")));
// page 1 (the second page) of the active members by name; the total comes from a count query
```

**来自请求参数的排序。** Spring MVC 可以根据 `?page=1&size=20&sort=name,desc` 这样的请求参数创建 `Pageable`。既没有排序策略也没有 `@Sortable` 字段时，任何持久化属性都会被接受，因此用户可以按任意列排序。此时 lambdaQL 会按实体和名称以 `INFO` 级别各记录一次 `LQL-5003`。如果只允许部分名称，请使用下面的排序策略。

<a id="sort-policy-with-spring"></a>
### 在 Spring 中使用排序策略

排序策略（决策 D43）决定允许哪些外部排序名称。`SortRules`、`SortMode` 和规则在 [4.10](04-order-paging.md#sort-policy) 中说明。
Spring `Sort` 或 `Pageable` 始终是外部排序，因此策略会检查其中的每个名称。排序 Lambda 从不检查。

**规则的来源。** 在 Spring Boot 中，自动配置会构建 `EntityManagerFactory` 的策略，并在所有单例 bean 创建之后注册它。它读取三个地方：

| 来源 | 提供的内容 |
|---|---|
| `SortPolicy` bean | 模式和用 Lambda 写的 `SortRules` |
| 属性 `lambdaql.sort.*` | 模式和用字符串写的规则 |
| `@Sortable` 字段（[4.10](04-order-paging.md#rules-from-sortable)） | bean 和属性都没有给出规则的实体的规则 |

- 一个实体的规则按表中的顺序只来自一个地方。两个地方的规则不会合并。
- `lambdaql.sort.mode` 优先于 bean 的模式。两者都没有时，模式为 `INFO`。
- 这只在恰好有一个 `EntityManagerFactory` 时生效，与 `QueryBuilder` bean 的条件相同（见 [12.8](#128-the-querybuilder-bean)）。否则请自己调用 `SortPolicy.register(emf, policy)`。
- 既没有 bean 也没有属性时，不注册任何内容，因此使用默认值：`INFO`，规则只来自 `@Sortable`。

**属性。**

| 属性 | 含义 |
|---|---|
| `lambdaql.sort.mode` | `OFF`、`INFO`（默认值）、`WARN` 或 `STRICT` |
| `lambdaql.sort.allow.<Entity>` | 允许的路径，用逗号分隔，例如 `name,team.name` |
| `lambdaql.sort.alias.<Entity>.<外部名称>` | 别名的路径，例如 `lambdaql.sort.alias.Member.level=grade` |

**SARGable 模式属性。** `lambdaql.sargable.mode` 取 `OFF`、`WARN`（默认值）、`STRICT` 之一（决策 D50）。自动配置以与 `lambdaql.sort.mode` 相同的方式注册它。为 `STRICT` 时，对无法使用索引的条件的分析会以 `NonSargableException`（`LQL-3066`）失败（见 [8.9](08-procedures-functions.md#89-warning-functions-on-columns-and-indexes-sargable)）。

`<Entity>` 是 JPA 实体名：默认是简单类名，也可以用 `@Entity(name = ...)` 指定。

实体名在持久化单元中唯一（两个实体同名时 Hibernate 会在启动时失败），因此不同包中同名的类不会混淆。未知的名称会使应用启动失败，抛出 `IllegalStateException`（LQL-4042）。
路径会在第一次使用规则时与实体核对（见 4.10 中的[字符串形式的规则](04-order-paging.md#rules-as-strings)）。

**(已测试)** [`SortPropertiesTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/SortPropertiesTest.java)，region `properties`（测试的属性；在应用中写在 `application.properties` 或 `application.yaml` 中）：

```java
@SpringBootTest(classes = SampleApplication.class, properties = {
        "lambdaql.sort.mode=STRICT",
        "lambdaql.sort.allow.Member=name,team.name",
        "lambdaql.sort.alias.Member.level=grade"})
```

**(已测试)** [`SortPropertiesTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/SortPropertiesTest.java)，region `properties-use`：

```java
List<Member> byLevel = repository.findAll(m -> m.isActive(), Sort.by(Sort.Order.desc("level")));
List<Member> byTeam = repository.findAll(m -> m.isActive(), Sort.by("team.name", "name"));
assertThrows(SortNotAllowedException.class, () -> repository.findAll(m -> m.isActive(), Sort.by("grade")));
```

该测试也检查了自动配置会注册策略。

**`SortPolicy` bean。** 要使用由编译器检查的规则，请定义一个 `SortPolicy` bean。同一个自动配置步骤会注册它。
这个 bean 是设计示例。下面的测试手动注册同样的策略：

```java
@Bean
public SortPolicy sortPolicy() {
    return SortPolicy.of(SortMode.STRICT,
            SortRules.of(Member.class)
                    .allow(m -> m.getName())
                    .alias("level", m -> m.getGrade()));
}
```

**(已测试)** [`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java)，region `sort-policy`（region 在 `try` 内结束；`finally` 块调用 `SortPolicy.unregister(emf)`）：

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

- `level` 这样的别名不是 `Member` 的属性。lambdaQL 不做 Spring `PropertyPath` 检查，按原样传递它，由策略把它变为 `grade`。
- 既不是别名也不是属性的名称，会在询问策略之前先抛出 `PropertyReferenceException`。
- 在 `STRICT` 模式下，不在规则中的属性会抛出 `SortNotAllowedException`（`LQL-4040`）。Spring 不转换它（见 [12.6](#126-transactions-and-exceptions)）。

**HTTP 400。** lambdaQL 没有 Web 依赖，因此不决定 HTTP 状态。Web 应用通常用 400 Bad Request 响应 `SortNotAllowedException`。
下面的处理器只是示例（未测试）：

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

消息中包含实体类名和排序名称。如果不想让客户端看到实体名，只发送 `e.code()`。

<a id="126-transactions-and-exceptions"></a>
## 12.6 事务与异常

执行器使用 Spring 的共享 `EntityManager`，它绑定到当前事务。

**lambdaQL 有意不在其方法上加 `@Transactional`**（决策 D41）。
更新、删除、插入或 upsert 需要调用方的事务。快捷方法（`deleteAll`、`updateOne` 等）、构建器（`updateQuery()` 等）和 `QueryBuilder` bean 都是如此。
请在你的服务方法上加 `@Transactional`，或者使用 `TransactionTemplate`。没有事务时，调用会在发送任何 SQL 之前失败。

| 情况 | 结果 |
|---|---|
| 在 `@Transactional` 内部执行查询 | lambdaQL 在同一个事务中运行。它能看到尚未 flush 的更改。回滚之后，这些更改对 lambdaQL 也不再存在（已在 `ExecutorTest` 中测试） |
| 在事务外部执行查询 | Spring 为这次调用使用一个新的 `EntityManager`，调用结束后关闭它，因此返回的实体处于游离（detached）状态。这是 Spring 的正常行为；lambdaQL 没有对此进行测试 |
| 在 `@Transactional` 或 `TransactionTemplate` 内部执行更新、删除、插入或 upsert | 在该事务中执行，并随它一起回滚（已测试） |
| 在事务外部执行更新、删除、插入或 upsert | 在发送任何 SQL 之前失败。什么都不改变（已测试，见下一张表） |

只有 `new QueryBuilder(entityManagerFactory)` 会在自己的事务中执行 DML（见 [7.8](07-dml.md#transactions)）。不要在 Spring 应用中使用它（见 [12.8](#128-the-querybuilder-bean)）。

与其他 repository 方法一样，错误会经过 Spring 的异常转换：

| 情况 | 异常 |
|---|---|
| `findOne` 匹配 2 行或更多行 | `IncorrectResultSizeDataAccessException`（Spring），而不是 JPA 的 `NonUniqueResultException`（已测试） |
| `deleteOne` 或 `updateOne` 匹配 2 行或更多行 | `IncorrectResultSizeDataAccessException`。什么都不改变（已测试） |
| Spring `Sort` 中有未知属性 | `PropertyReferenceException`（已测试） |
| Spring `Sort` 中使用 `ignoreCase()` | `UnsupportedOperationException`（已测试） |
| 在 `STRICT` 模式下，排序策略不允许的排序名称（[12.5](#sort-policy-with-spring)） | `SortNotAllowedException`（`LQL-4040`），不转换（已测试） |
| 在 repository 调用中（快捷方法，或默认方法内部的 `execute()`）没有事务时执行更新、删除、插入或 upsert | `InvalidDataAccessApiUsageException`（已测试），由 Spring 从 JPA 的 `TransactionRequiredException` 转换而来。在 repository 方法外部（`QueryBuilder` bean）则是 JPA 的 `TransactionRequiredException` |
| 违反规则（U1、U3、U4、U6）的赋值 Lambda | `LambdaQLException`（例如 `TranslationException`，`LQL-3042`），不做转换（见 [7.8](07-dml.md#rules-checked-at-analysis-time)） |

`QueryBuilder` bean（12.8）不是 repository，因此 Spring 不会转换它的错误。在那里，`one()` 和 `executeOne()` 遇到 2 行或更多行时会抛出 `NonUniqueResultException`。

<a id="127-other-fragments-and-jpaspecificationexecutor"></a>
## 12.7 其他 fragment 与 `JpaSpecificationExecutor`

`LambdaQlExecutor<T>` 只是众多 fragment 中的一个。12.3 中的 repository 同时使用了以下所有内容（已在 `ExecutorTest` 中测试）：

| 一起使用的对象 | 可用 | 说明 |
|---|---|---|
| 手写的 fragment（`MemberRepositoryCustom` 与 `MemberRepositoryImpl`） | 是 | Spring Data 照常找到 `Impl` 类 |
| `JpaSpecificationExecutor<T>` | 是 | lambdaQL 的 Lambda 有一个参数，`Specification` 的 Lambda 有三个参数，因此 Java 总是选择正确的 `findAll` |
| `JpaRepository` 的方法（`findAll()`、`count()`、`save(...)`） | 是 | 它们的参数类型不同 |

下面这些调用来自 `ExecutorTest` 中的测试方法 `worksWithOtherFragments`：

```java
repository.countByHand();                                                  // hand-written fragment
repository.findAll((root, query, cb) -> cb.isTrue(root.get("active")));    // Specification: three arguments
repository.findAll(m -> m.isActive());                                     // lambdaQL Where: one argument
repository.count();                                                        // Spring Data's own count()
```

<a id="128-the-querybuilder-bean"></a>
## 12.8 `QueryBuilder` bean

使用 Spring Boot 时，自动配置 `org.lambdaql.spring.boot.LambdaQlAutoConfiguration` 会创建一个 `QueryBuilder` bean。对于不属于某一个 repository 的查询，请把它注入到你的服务中。

- 该 bean 使用 Spring 的共享、绑定到事务的 `EntityManager`。它的查询会加入当前事务，与 repository 方法相同。
- 只有当恰好存在一个 `EntityManagerFactory`，且你没有定义自己的 `QueryBuilder` bean 时，才会创建该 bean。
- repository 方法（`LambdaQlExecutor<T>`）不需要这个 bean。
- 它的更新、删除、插入和 upsert 语句与 repository 方法一样，需要调用方的事务。没有事务时，它们会在发送任何 SQL 之前抛出 `TransactionRequiredException`。

**(已测试)** [`AutoConfigurationTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/AutoConfigurationTest.java)，region `query-builder-bean`（`qb` 是注入的 bean）：

```java
// inject it into a service: it joins the current transaction
TransactionTemplate tx = new TransactionTemplate(txManager);
long seen = tx.execute(status -> {
    members.save(new Member("Kim", true, 3, null));                       // not flushed yet
    return qb.from(Member.class).where(m -> m.getName().equals("Kim")).count();
});
```

不使用 Spring Boot，或者有两个及以上 `EntityManagerFactory` bean 时，请自己创建该 bean。像自动配置那样使用共享的 `EntityManager`：

```java
@Bean
public QueryBuilder queryBuilder(EntityManagerFactory entityManagerFactory) {
    // the same as the auto-configuration: a shared, transaction-bound EntityManager
    return new QueryBuilder(SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory));
}
```

`SharedEntityManagerCreator` 是 Spring 的类（`org.springframework.orm.jpa`）。在 Spring 应用中不要使用 `new QueryBuilder(entityManagerFactory)`：那样创建的 builder 会为每个查询打开自己的 `EntityManager`，位于 Spring 事务之外。

**使用 `@DataJpaTest` 的测试。** `@DataJpaTest` 切片测试只加载部分自动配置。repository 方法在那里可以使用，但不会创建 `QueryBuilder` bean。如果测试需要它，请在测试类上加 `@ImportAutoConfiguration(LambdaQlAutoConfiguration.class)`（尚未测试）。

<a id="129-kotlin-repositories"></a>
## 12.9 Kotlin repository

用 Kotlin 编写的 repository 需要一些编译器设置：Kotlin 2.2 或更高版本使用默认设置即可，较旧的 Kotlin 需要 `-Xjvm-default=all`，并且 Spring Data 需要 `kotlin-reflect`。
见[本页的 Kotlin 版本](../kotlin/12-spring-data-jpa.md#129-kotlin-compile-settings)。

<a id="1210-not-implemented-yet"></a>
## 12.10 尚未实现

- **Spring Boot 版本。** 本版使用 Spring Boot 4.1.2 测试。自动配置也写出了 Spring Boot 3 的类名，但本版未验证 Spring Boot 3。
- Spring `Sort` 中的 **`ignoreCase()`**。
- **Kotlin repository** 尚未测试。

---

[Java 路线](README.md) | 上一章：[11. 错误与语言](../11-errors-and-languages.md) | [本页的 Kotlin 版本](../kotlin/12-spring-data-jpa.md)
