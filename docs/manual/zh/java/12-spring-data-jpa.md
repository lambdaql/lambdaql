<a id="12-spring-data-jpa-java"></a>
# 12. Spring Data JPA（Java）

[Java 路线](README.md) | [手册首页](../README.md) | 上一章：[11. 错误与语言](../11-errors-and-languages.md) | 下一章：[13. Spring Boot 3 与 4](../13-spring-boot-versions.md) | [本页的 Kotlin 版本](../kotlin/12-spring-data-jpa.md)

---

<a id="121-what-you-get"></a>
## 12.1 提供的功能

在 Spring Data JPA 中编写 repository 接口。lambdaQL 在此基础上增加一个接口 `LambdaQlExecutor<T>`。

repository 继承这个接口，自己编写的查询方法写成使用 lambdaQL 的**默认方法**。
不需要为这些方法编写 `...RepositoryImpl` 类。

| 组成部分 | 功能 | 小节 |
|---|---|---|
| `LambdaQlExecutor<T>` | 在所有 repository 中以 Lambda 条件执行 `findAll`、`findSet`、`findFirst`、`findOne`、`count`、`exists`、`query()` | [12.3](#123-repository-setup)、[12.4](#124-executor-methods) |
| Spring `Sort` 和 `Pageable` | `findAll(where, sort)`、`findSet(where, sort)`，以及返回 Spring `Page` 的 `findAll(where, pageable)` | [12.5](#125-spring-sort-and-pageable) |
| 更新、删除、插入、upsert | `deleteAll(where)`、`updateOne(where, set)` 等快捷方法一次调用即执行一条语句。`updateQuery()`、`deleteQuery()`、`insertQuery()`、`upsertQuery()` 针对 repository 的实体开始 [7. 更新、插入、删除](07-dml.md) 中的语句。都需要调用方的事务 | [12.4](#124-executor-methods)、[12.6](#126-transactions-and-exceptions) |
| `QueryBuilder` bean | Spring Boot 创建供服务使用的 `QueryBuilder` bean | [12.8](#128-the-querybuilder-bean) |

<a id="122-modules"></a>
## 12.2 模块

核心不依赖 Spring，因此也可以在普通 JPA 中使用。`lambdaql` 和 `lambdaql-spring-data-jpa` 为 Spring Boot 3 与 4 共用，只有 starter 按 Spring Boot 版本分开提供（名称以 `-boot3`、`-boot4` 结尾，[13. Spring Boot 3 与 4](../13-spring-boot-versions.md)）。

| 构件 | 包含内容 | 何时需要 |
|---|---|---|
| `lambdaql` | 引擎和 `QueryBuilder`。不依赖 Spring。Spring Boot 3 与 4 共用 | 始终需要。下面的构件会一并带入 |
| `lambdaql-spring-data-jpa` | `LambdaQlExecutor<T>`、Spring Data 注册、Spring Boot 自动配置（`QueryBuilder` bean）。Spring Data JPA 3.5 与 4.1 共用。使用项目中的 Spring Data JPA | 不使用 Spring Boot 而使用 Spring Data JPA 时 |
| `lambdaql-spring-boot-starter-boot4`、`lambdaql-spring-boot-starter-boot3` | 没有代码。带入 `lambdaql-spring-data-jpa` 和对应 Boot 版本的 `spring-boot-starter-data-jpa` | 使用 Spring Boot 时（与项目的 Boot 版本对应） |

构件发布在 Maven Central（groupId `io.github.lambdaql`，版本 `0.0.2`）。

在 Spring Boot 4 中使用时的 Gradle（Groovy DSL）。如果是 Spring Boot 3，请把名称结尾改为 `-boot3`：

```groovy
repositories {
    mavenCentral()
}

dependencies {
    implementation 'io.github.lambdaql:lambdaql-spring-boot-starter-boot4:0.0.2'
}
```

Maven：

```xml
<dependency>
  <groupId>io.github.lambdaql</groupId>
  <artifactId>lambdaql-spring-boot-starter-boot4</artifactId>
  <version>0.0.2</version>
</dependency>
```

不使用 Spring Boot 时，请在现有的 Spring Data JPA 配置（Spring Data JPA、Hibernate、JDBC 驱动）中添加 `lambdaql-spring-data-jpa`。这个构件会一并带入 `lambdaql`，Spring Data JPA 则使用项目中已有的版本（3.5 或 4.1）。

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

要求：

| 项目 | 版本 |
|---|---|
| Java | 21 及以上 |
| Spring Boot | 3.5 或 4.1 |
| Spring Data JPA | 3.5 或 4.1 |
| Hibernate | 6.6 或 7.4（Spring Boot 已包含） |

**不支持** Spring Data JPA 3.3 及更早版本。lambdaQL 在 `META-INF/spring.factories` 中注册执行器，而 Spring Data 从 3.4 起才从这个文件读取 repository fragment。

<a id="123-repository-setup"></a>
## 12.3 设置 repository

把 `LambdaQlExecutor<T>` 加到 repository 的 `extends` 列表中。`T` 是该 repository 的实体。
不需要编写额外的配置和 `Impl` 类。

本章的示例使用示例实体 `Member`（`id`、`name`、`active`、`grade`、`team`）和 `Team`（`id`、`name`）。`grade` 是 `Integer`，因此可以为 NULL。

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

最后三个方法执行更新、删除、插入语句。这些方法需要事务（见 [12.6](#126-transactions-and-exceptions)）。

这个 repository 还继承了 `JpaSpecificationExecutor<Member>`。lambdaQL 并不需要它，这样配置是为了展示一起使用时的行为。
另一个示例 repository [`MemberRepository`](https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/sample/MemberRepository.java) 在相同的方法之外，还继承了手写的 fragment `MemberRepositoryCustom`（见 [12.7](#127-other-fragments-and-jpaspecificationexecutor)）。

工作原理：

- **默认方法由 Spring Data 直接执行。** Spring Data 不会从默认方法生成查询，而是调用方法主体。lambdaQL 不改变这一行为。
- **`LambdaQlExecutor<T>` 是 Spring Data repository fragment。** 实现类登记在 `lambdaql-spring-data-jpa` 的 `META-INF/spring.factories` 中。Spring Data 读取该文件，并把实现添加到所有继承 `LambdaQlExecutor<T>` 的 repository 中。
- **所有 repository 共享一个执行器对象。** 每次调用使用对应 repository 的实体。例如，在 `TeamRepository` 中 `teams.count(t -> ...)` 返回 `Team` 的行数。

规则：

| # | 规则 |
|---|---|
| 1 | repository 必须**直接**（在自己的 `extends` 列表中）继承 `LambdaQlExecutor<T>`。 |
| 2 | 不要在为此目的继承 `LambdaQlExecutor<T>` 的接口上加 `@NoRepositoryBean`。加上后 Spring Data 不会添加 lambdaQL 的实现。 |
| 3 | 请把 lambdaQL 查询写在**默认方法**或服务中。抽象方法仍然作为 Spring Data 查询方法（`findByName` 等）工作。 |
| 4 | 不使用 Spring Boot 时，repository 部分仍是普通的 Spring Data，因此照常工作。只是 `QueryBuilder` bean 不会自动创建（见 [12.8](#128-the-querybuilder-bean)）。 |

示例代码：[`ManualMemberRepository.java`](https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ManualMemberRepository.java)（`repository`）。

<a id="124-executor-methods"></a>
## 12.4 执行器方法

`Where<T>` 是与 `where(...)` 相同的条件 Lambda（见 [3. 条件](03-where.md)）。`SortSpec<T>` 是 lambdaQL 的排序（见 [4.10](04-order-paging.md#410-reusable-and-external-sorts)）。
`Sort`、`Pageable`、`Page` 是 Spring Data 的类型。

| 方法 | 返回值 | 说明 |
|---|---|---|
| `query()` | `SelectQuery<T>` | 针对 repository 的实体开始查询。用于简短方法无法表达的查询（多次 `where`、`limit`、`offset`、运行时的 `Direction`） |
| `from(Class<E>)` | `SelectQuery<E>` | 针对同一持久化单元中的另一个实体开始查询 |
| `findAll(Where<T>)` | `List<T>` | 所有匹配的行。顺序不固定 |
| `findAll(Where<T>, SortSpec<T>)` | `List<T>` | 排序后返回所有匹配的行 |
| `findAll(Where<T>, Sort)` | `List<T>` | 按 Spring `Sort` 排序（见 [12.5](#125-spring-sort-and-pageable)）。`Sort.unsorted()` 表示不排序 |
| `findAll(Where<T>, Pageable)` | `Page<T>` | 一页。只在需要时执行 count 查询。`Pageable.unpaged()` 返回所有行 |
| `findSet(Where<T>)` | `SequencedSet<T>` | 以 Set 返回所有匹配的行。顺序不固定（与 `findAll(Where<T>)` 相同）。不加 `distinct`。Set 的大小小于行数时抛出 `IllegalStateException`（见 [`toSet()`](02-getting-started.md#toset)） |
| `findSet(Where<T>, SortSpec<T>)` | `SequencedSet<T>` | 按排序顺序以 Set 返回所有匹配的行 |
| `findSet(Where<T>, Sort)` | `SequencedSet<T>` | 同上。按 Spring `Sort` 排序（见 [12.5](#125-spring-sort-and-pageable)） |
| `findFirst(Where<T>)` | `Optional<T>` | 匹配的行之一。不排序 |
| `findFirst(Where<T>, SortSpec<T>)` | `Optional<T>` | 按指定顺序的第一行。只读取一行 |
| `findFirst(Where<T>, Sort)` | `Optional<T>` | 按 Spring `Sort` 顺序的第一行 |
| `findOne(Where<T>)` | `Optional<T>` | 唯一匹配的行。2 行及以上时抛出 `IncorrectResultSizeDataAccessException` |
| `count(Where<T>)` | `long` | 匹配的行数 |
| `exists(Where<T>)` | `boolean` | 有匹配的行时为 `true`。最多读取一行，且不加载实体 |
| `deleteAll(Where<T>)` | `int` | 用一条 SQL 语句删除所有匹配的行（批量）。与 Spring Data 的 `deleteAll()` 不同（见[下文](#deleteallwhere-is-not-spring-datas-deleteall)）。需要事务 |
| `deleteOne(Where<T>)` | `int` | 以[单行保护](#one-row-protection-deleteone-and-updateone)删除唯一匹配的行。返回 0 或 1。需要事务 |
| `updateAll(Where<T>, Assign<T>)` | `int` | 修改所有匹配的行。条件在前。需要事务 |
| `updateOne(Where<T>, Assign<T>)` | `int` | 以单行保护修改唯一匹配的行。返回 0 或 1。需要事务 |
| `insertOne(Assign<T>)` | `int` | 插入一行。返回 1。需要事务 |
| `insertAll(Collection<L>, AssignEach<T, L>)` | `int` | 每个元素插入一行（每条语句最多 500 个值）。需要事务 |
| `upsertOne(Assign<T>)` | `int` | 插入一行。已有相同 `@Id` 的行时修改该行。返回数据库报告的行数。需要事务 |
| `upsertAll(Collection<L>, AssignEach<T, L>)` | `int` | 每个元素 upsert 一行（每行一条语句）。需要事务 |
| `updateQuery()` | `UpdateQuery<T>` | 开始更新：`updateQuery().set(...).where(...).execute()`（见 [7.2](07-2-update.md)）。需要事务 |
| `deleteQuery()` | `DeleteQuery<T>` | 开始删除：`deleteQuery().where(...).execute()`（见 [7.4](07-4-delete.md)）。需要事务 |
| `insertQuery()` | `InsertQuery<T>` | 开始插入：`insertQuery().values(...).execute()`（见 [7.5](07-5-insert.md)）。需要事务 |
| `upsertQuery()` | `UpsertQuery<T>` | 开始以 `@Id` 为键的 upsert（见 [7.6](07-6-upsert.md)）。需要事务 |

简短方法等同于在 `query()` 后加上结果函数。`findAll(w)` 对应 `query().where(w).toList()`，`findSet(w)` 对应 `query().where(w).toSet()`，`findFirst(w)` 对应 `query().where(w).first()`，`findOne(w)` 对应 `query().where(w).one()`。结果函数在[获取结果](02-getting-started.md#getting-the-result)中说明。
`findAll(Where<T>)` 和 `count(Where<T>)` 的参数类型不同，因此不会与 `JpaRepository` 的 `findAll(Sort)`、`findAll(Example)`、`count()` 冲突。

**`query()`：完整的查询。**
12.3 中 repository 的一部分：

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

下面的示例中，`repository` 是 `MemberRepository`。

<!-- region: ExecutorTest.java#find-all-sort -->
```java
SortSpec<Member> byGrade = SortSpec.of(Member.class, m -> desc(m.getGrade()).asc(m.getId()));
List<Member> active = repository.findAll(m -> m.isActive(), byGrade);
```

等级最高的活跃成员：

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

`active` 保持 `Sort` 的顺序。`any` 与 `findAll(where)` 一样，顺序不固定。

<a id="dml-shortcuts"></a>
### DML 快捷方法

以下 8 个方法一次调用即执行更新、删除、插入、upsert。

| 快捷方法 | 等同的语句 |
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
- **`One`** 在删除和更新中表示单行保护（见下一节），在插入和 upsert 中表示一行。
- 需要调用方的事务（见 [12.6](#126-transactions-and-exceptions)）。示例在 `TransactionTemplate` 中调用。

<!-- region: ExecutorTest.java#dml-shortcuts -->
```java
int changed = repository.updateAll(m -> !m.isActive(), m -> m.setGrade(7));      // condition first
int deleted = repository.deleteAll(m -> m.getTeam() == null);                     // bulk: no cascade
int one = repository.insertOne(m -> { m.setName("Guest"); m.setActive(false); });
int many = repository.insertAll(List.of(new NewMember("A", 1), new NewMember("B", 2)),
        (m, r) -> { m.setName(r.name()); m.setGrade(r.grade()); m.setActive(true); });
```

`NewMember` 是示例中的 record `record NewMember(String name, int grade)`。

<a id="one-row-protection-deleteone-and-updateone"></a>
### 单行保护：`deleteOne` 和 `updateOne`

`deleteOne(where)` 和 `updateOne(where, set)` 不会修改 2 行及以上。

1. lambdaQL 读取匹配行的 id，最多 2 个。
2. 没有 id 时，什么都不修改，返回 0。
3. 有 2 个 id 时，什么都不修改，抛出 `IncorrectResultSizeDataAccessException`。
4. 有 1 个 id 时，用 `id = :p and (条件)` 只修改该行，返回 1。如果该行在此期间已不再满足条件，则返回 0。

语句本身指定了一个 id，因此即使在此期间另一个事务添加了匹配的行，也不会修改第二行。这种保护不依赖回滚。

核心 API 也有同样的保护：`executeOne()`（见 [7.4](07-4-delete.md#delete-exactly-one-row-executeone)）。

<!-- region: ExecutorTest.java#dml-one -->
```java
int renamed = repository.updateOne(m -> m.getId() == kimId, m -> m.setName("Kim2"));   // exactly one row
// two or more rows match: nothing changes
assertThrows(org.springframework.dao.IncorrectResultSizeDataAccessException.class,
        () -> repository.deleteOne(m -> m.isActive()));
```

<a id="deleteallwhere-is-not-spring-datas-deleteall"></a>
### `deleteAll(where)` 与 Spring Data `deleteAll()` 的区别

| 方法 | 所属 | 删除方式 | cascade、`@PreRemove` |
|---|---|---|---|
| `deleteAll()` | `CrudRepository` | 加载所有实体后逐个删除 | 执行 |
| `deleteAll(Iterable)` | `CrudRepository` | 逐个删除传入的实体 | 执行 |
| `deleteAll(Where<T>)` | `LambdaQlExecutor<T>` | 一条 SQL `delete` 语句（批量） | **不执行** |

`deleteAll(where)` 不加载实体。与其他批量语句一样，不会反映到已加载的实体上。

需要快速删除大量行时使用。需要 cascade 或 `@PreRemove` 时，请使用 Spring Data 的方法。

编译器根据参数（无参数、`Iterable`、Lambda）选择方法。已通过 javac 和 kotlinc 2.3.21 的字节码 opcode 确认（实测）。

<a id="builders-for-complex-statements"></a>
### 用于复杂语句的构建器

`updateQuery()`、`deleteQuery()`、`insertQuery()`、`upsertQuery()` 开始完整的语句。请在快捷方法无法表达时使用：多次 `set()`、`all()`、`orderBy(...).limit(n)`（见 [7.4](07-4-delete.md#delete-the-first-rows-limit)）、`toHql()`、`onConflict(...)`。

方法名以 `Query` 结尾，因此不会与 `delete(...)` 等 `JpaRepository` 方法冲突。语句与用 `QueryBuilder` 创建的语句相同（见 [7. 更新、插入、删除](07-dml.md)）。目标行与用同一个 `where` Lambda 查询到的行相同。

与快捷方法一样，需要调用方的事务。与所有 JPA 批量语句一样，不会反映到事务中已加载的实体上。

12.3 中 repository 的一部分：

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

upsert 也在事务中执行。`teams` 是 `TeamRepository`，`tx` 是 Spring `TransactionTemplate`。

<!-- region: ExecutorTest.java#upsert -->
```java
int changed = tx.execute(s -> teams.upsertQuery().values(t -> { t.setId(teamId); t.setName("A2"); }).execute());
```

upsert 返回数据库报告的行数。插入时为 1；更新时 H2 和 Oracle 为 1，MariaDB 为 2（见 [7.6](07-6-upsert.md#the-row-count)）。

示例代码：[`MemberRepository.java`](https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/sample/MemberRepository.java)（`query`、`dml`），[`ExecutorTest.java`](https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java)（`find-all-sort`、`find-first`、`find-one`、`count-exists`、`find-set`、`dml-shortcuts`、`dml-one`、`upsert`）。

<a id="125-spring-sort-and-pageable"></a>
## 12.5 Spring `Sort` 与 `Pageable`

`findAll(where, sort)`、`findSet(where, sort)`、`findFirst(where, sort)`、`findAll(where, pageable)` 接受 Spring Data 的类型。
lambdaQL 用 `SortSpec.ofPath` 把每个 `Sort.Order` 转换为一个排序键（见 [4.10](04-order-paging.md#410-reusable-and-external-sorts)）。

| Spring | lambdaQL 的处理 |
|---|---|
| 属性名（`"grade"`、`"team.name"`） | 与 Spring Data repository 一样，用 Spring Data `PropertyPath` 检查。名称不存在时抛出 `PropertyReferenceException`（包：Spring Data 3 为 `org.springframework.data.mapping`，4 为 `org.springframework.data.core`）。路径必须以基本值而不是实体结尾。`team.name` 这样的 to-one 路径使用 `left join`，因此没有 team 的行也包含在结果中。之后由排序策略检查名称（见[下文](#sort-policy-with-spring)） |
| 排序策略的别名（`"level"`） | 不做 `PropertyPath` 检查，按原样传递。由排序策略换成路径 |
| `NullHandling.NATIVE`（默认值）的 `ASC` / `DESC` | 适用 lambdaQL 的 NULL 规则。升序时 NULL 在前，降序时 NULL 在后。在所有数据库上顺序相同（见 [4.4](04-order-paging.md#44-null-ordering-is-the-same-on-every-database)） |
| `NullHandling.NULLS_FIRST` / `NULLS_LAST` | 按指定的方式使用（`ASC_NULLS_FIRST`、`DESC_NULLS_LAST` 等） |
| `ignoreCase()` | 不支持。抛出 `UnsupportedOperationException` |
| `Sort.unsorted()` | 没有 `order by` |

Spring Data JPA 自身的 repository 把 `NATIVE` 的 NULL 位置交给数据库决定，因此顺序可能因数据库而异。lambdaQL 适用自己的规则。

<!-- region: ExecutorTest.java#find-all-spring-sort -->
```java
List<Member> rows = repository.findAll(m -> m.isActive(),
        Sort.by(Sort.Order.desc("grade"), Sort.Order.asc("team.name"), Sort.Order.asc("id")));
// a property that does not exist: the same error as Spring Data's own repositories
RuntimeException unknown = assertThrows(RuntimeException.class,
        () -> repository.findAll(m -> m.isActive(), Sort.by("nickname")));
assertEquals("PropertyReferenceException", unknown.getClass().getSimpleName());
```

`findAll(where, pageable)` 的分页：

- 与 Spring Data 一样，页码从 0 开始。lambdaQL 用 `limit` 和 `offset` 读取页。
- 总数通过 `count` 查询获得。仅凭这一页就能确定总数时（例如第一页的行数少于页大小），Spring 会省略 count 查询。
- 排序键中没有 id 时，lambdaQL 会把 id 添加为最后一个键，因此行不会在页之间移动（见[同序处理](04-order-paging.md#tie-breaker-automatic-id)）。没有排序的页不加 `order by`，要稳定地分页，请指定 `Sort`。
- `Pageable.unpaged()` 在一页中返回所有行。

<!-- region: ExecutorTest.java#page -->
```java
Page<Member> page = repository.findAll(m -> m.isActive(), PageRequest.of(1, 2, Sort.by("name")));
// page 1 (the second page) of the active members by name; the total comes from a count query
```

**通过请求参数接收的排序。** Spring MVC 可以根据 `?page=1&size=20&sort=name,desc` 这样的请求参数创建 `Pageable`。
既没有排序策略也没有 `@Sortable` 字段时，所有持久化属性都会被接受，因此用户可以按任意列排序。此时 lambdaQL 会按实体和名称各以 `INFO` 级别记录一次 `LQL-5003`。
如果只允许部分名称，请使用下面的排序策略。

<a id="sort-policy-with-spring"></a>
### 在 Spring 中使用排序策略

排序策略规定允许的外部排序名称。`SortRules`、`SortMode` 和规则在 [4.10](04-order-paging.md#sort-policy) 中说明。
Spring `Sort` 或 `Pageable` 始终是外部排序，因此策略会检查其中的所有名称。排序 Lambda 不做检查。

**规则的来源。** 在 Spring Boot 中，自动配置会创建 `EntityManagerFactory` 的策略，并在所有单例 bean 创建之后注册。
自动配置读取三个地方。

| 来源 | 提供的内容 |
|---|---|
| `SortPolicy` bean | 模式和用 Lambda 编写的 `SortRules` |
| 属性 `lambdaql.sort.*` | 模式和用字符串编写的规则 |
| `@Sortable` 字段（[4.10](04-order-paging.md#rules-from-sortable)） | bean 和属性中都没有规则的实体的规则 |

- 一个实体的规则按表中的顺序只从一个地方获取。不会合并两个地方的规则。
- `lambdaql.sort.mode` 优先于 bean 的模式。两者都没有时，模式为 `INFO`。
- 注册条件是 `EntityManagerFactory` 恰好只有一个，与 `QueryBuilder` bean 相同（见 [12.8](#128-the-querybuilder-bean)）。其他情况请直接调用 `SortPolicy.register(emf, policy)`。
- 既没有 bean 也没有属性时，不注册任何内容，因此使用默认值：`INFO`，规则只从 `@Sortable` 获取。

**属性。**

| 属性 | 含义 |
|---|---|
| `lambdaql.sort.mode` | `OFF`、`INFO`（默认值）、`WARN`、`STRICT` |
| `lambdaql.sort.allow.<Entity>` | 允许的路径，用逗号分隔。例：`name,team.name` |
| `lambdaql.sort.alias.<Entity>.<外部名称>` | 别名的路径。例：`lambdaql.sort.alias.Member.level=grade` |

**SARGable 模式属性。** `lambdaql.sargable.mode` 取 `OFF`、`WARN`（默认值）、`STRICT` 之一。自动配置以与 `lambdaql.sort.mode` 相同的方式注册。为 `STRICT` 时，对无法使用索引的条件的分析会以 `NonSargableException`（`LQL-3066`）失败（见 [8.9](08-procedures-functions.md#89-warning-functions-on-columns-and-indexes-sargable)）。

`<Entity>` 处写 JPA 实体名。默认值是简单类名，可以用 `@Entity(name = ...)` 更改。

实体名在 persistence unit 中唯一（同名的有两个时，Hibernate 会在启动阶段中止）。因此不同包中同名的类也不会混淆。名称不存在时，应用启动会以 `IllegalStateException`（LQL-4042）中止。
路径会在第一次使用规则时与实体核对（见 4.10 中的[字符串形式的规则](04-order-paging.md#rules-as-strings)）。

在应用中写在 `application.properties` 或 `application.yaml` 中：

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

自动配置用这些属性注册排序策略。

**`SortPolicy` bean。** 要使用由编译器检查的规则，请定义 `SortPolicy` bean。同一个自动配置步骤会注册这个 bean。
有这个 bean 时，`Sort.Order.desc("level")` 按 `grade` 排序，`Sort.by("grade")` 以 `SortNotAllowedException` 被拒绝。之后的示例直接注册同样的策略。

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

下面的代码是 `try` 块中的部分。在 `finally` 块中调用 `SortPolicy.unregister(emf)` 解除策略。

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

- `level` 这样的别名不是 `Member` 的属性。lambdaQL 不做 Spring `PropertyPath` 检查，按原样传递，由策略换成 `grade`。
- 既不是别名也不是属性的名称，会在策略检查之前抛出 `PropertyReferenceException`。
- 在 `STRICT` 模式下指定规则中没有的属性时，抛出 `SortNotAllowedException`（`LQL-4040`）。Spring 不转换这个异常（见 [12.6](#126-transactions-and-exceptions)）。

**HTTP 400 响应。** lambdaQL 没有 Web 依赖，因此不决定 HTTP 状态。Web 应用通常在 `@ExceptionHandler(SortNotAllowedException.class)` 中以 400 Bad Request 响应，错误代码用 `e.code()`（`"LQL-4040"`）读取。

消息中包含实体类和排序名称。如果不想向客户端暴露实体名，请只发送 `e.code()`。

示例代码：[`ExecutorTest.java`](https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java)（`find-all-spring-sort`、`page`、`sort-policy`），[`SortPropertiesTest.java`](https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/SortPropertiesTest.java)（`properties`、`properties-use`），[`ManualJavaSpringTest.java`](https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ManualJavaSpringTest.java)（`sort-policy-bean`）。

<a id="126-transactions-and-exceptions"></a>
## 12.6 事务与异常

执行器使用绑定到当前事务的 Spring 共享 `EntityManager`。

**lambdaQL 不在方法上加 `@Transactional`。** 这是有意的设计。
更新、删除、插入、upsert 需要调用方的事务。快捷方法（`deleteAll`、`updateOne` 等）、构建器（`updateQuery()` 等）和 `QueryBuilder` bean 都是如此。

请在服务方法上加 `@Transactional`，或使用 `TransactionTemplate`。没有事务时，调用会在发送 SQL 之前失败。

| 情况 | 行为 |
|---|---|
| 在 `@Transactional` 内部查询 | lambdaQL 在同一个事务中执行，也能查询到尚未 flush 的更改。回滚之后，lambdaQL 也查询不到这些更改 |
| 在事务外部查询 | Spring 每次调用使用新的 `EntityManager`，调用结束后关闭，因此返回的实体处于游离（detached）状态（下面的 `detached` 示例） |
| 在 `@Transactional` 或 `TransactionTemplate` 内部更新、删除、插入、upsert | 在该事务中执行，并随事务一起回滚 |
| 在事务外部更新、删除、插入、upsert | 在发送 SQL 之前失败。什么都不修改（见下一张表） |

<!-- region: ManualJavaSpringTest.java#detached -->
```java
// outside a transaction each call uses a new EntityManager: the returned entity is detached
Member kim = repository.findOne(m -> m.getName().equals("Kim")).orElseThrow();
boolean managed = new TransactionTemplate(txManager).execute(s -> entityManager.contains(kim));
assertFalse(managed);
```

在自己的事务中执行 DML 的构建器只有 `new QueryBuilder(entityManagerFactory)`（见 [7.8](07-dml.md#transactions)）。请不要在 Spring 应用中使用这个构建器（见 [12.8](#128-the-querybuilder-bean)）。

与其他 repository 方法一样，错误会经过 Spring 的异常转换。

| 情况 | 异常 |
|---|---|
| `findOne` 匹配 2 行及以上 | Spring `IncorrectResultSizeDataAccessException`，而不是 JPA `NonUniqueResultException` |
| `deleteOne` 或 `updateOne` 匹配 2 行及以上 | `IncorrectResultSizeDataAccessException`。什么都不修改 |
| Spring `Sort` 中不存在的属性 | `PropertyReferenceException` |
| Spring `Sort` 的 `ignoreCase()` | `UnsupportedOperationException` |
| 在 `STRICT` 模式下，排序策略不允许的排序名称（[12.5](#sort-policy-with-spring)） | `SortNotAllowedException`（`LQL-4040`），不转换 |
| 在 repository 调用（快捷方法，或默认方法中的 `execute()`）中没有事务时执行更新、删除、插入、upsert | `InvalidDataAccessApiUsageException`。这是 Spring 转换 JPA `TransactionRequiredException` 得到的异常。在 repository 方法外部（`QueryBuilder` bean）则是 JPA `TransactionRequiredException` |
| 违反规则的赋值 Lambda | `LambdaQLException`（例如 `TranslationException`，`LQL-3042`）。不转换（见 [7.8](07-dml.md#rules-checked-at-analysis-time)） |

`QueryBuilder` bean（12.8）不是 repository，因此不经过 Spring 的异常转换。在这个 bean 中，`one()` 和 `executeOne()` 匹配 2 行及以上时抛出 `NonUniqueResultException`。

示例代码：[`ManualJavaSpringTest.java`](https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ManualJavaSpringTest.java)（`detached`）。

<a id="127-other-fragments-and-jpaspecificationexecutor"></a>
## 12.7 其他 fragment 与 `JpaSpecificationExecutor`

`LambdaQlExecutor<T>` 是多个 fragment 之一。12.3 中的 repository 同时使用以下所有配置。

| 一起使用的对象 | 是否可用 | 说明 |
|---|---|---|
| 手写的 fragment（`MemberRepositoryCustom` 与 `MemberRepositoryImpl`） | 可用 | Spring Data 照常查找 `Impl` 类 |
| `JpaSpecificationExecutor<T>` | 可用 | lambdaQL 的 Lambda 有一个参数，`Specification` 的 Lambda 有三个参数，因此 Java 总是选择正确的 `findAll` |
| `JpaRepository` 方法（`findAll()`、`count()`、`save(...)`） | 可用 | 参数类型不同 |

下面示例中的 `repository` 是 `MemberRepository`。

<!-- region: ManualJavaSpringTest.java#fragments -->
```java
long byHand = repository.countByHand();                                               // hand-written fragment
List<Member> bySpec = repository.findAll((root, query, cb) -> cb.isTrue(root.get("active")));   // Specification: three arguments
List<Member> byLambda = repository.findAll(m -> m.isActive());                        // lambdaQL Where: one argument
long total = repository.count();                                                      // Spring Data's own count()
```

示例代码：[`ManualJavaSpringTest.java`](https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ManualJavaSpringTest.java)（`fragments`）。

<a id="128-the-querybuilder-bean"></a>
## 12.8 `QueryBuilder` bean

在 Spring Boot 中，自动配置 `org.lambdaql.spring.boot.LambdaQlAutoConfiguration` 会创建 `QueryBuilder` bean。不属于某一个 repository 的查询，把这个 bean 注入服务中编写。

- bean 使用绑定到事务的 Spring 共享 `EntityManager`。因此与 repository 方法一样，查询会加入当前事务。
- 只有在 `EntityManagerFactory` 恰好只有一个，且用户没有另外定义 `QueryBuilder` bean 时，才会创建该 bean。
- repository 方法（`LambdaQlExecutor<T>`）不需要这个 bean。
- 用这个 bean 创建的更新、删除、插入、upsert 语句，也与 repository 方法一样需要调用方的事务。没有事务时，会在发送 SQL 之前抛出 `TransactionRequiredException`。

`qb` 是注入的 bean：

<!-- region: AutoConfigurationTest.java#query-builder-bean -->
```java
// inject it into a service: it joins the current transaction
TransactionTemplate tx = new TransactionTemplate(txManager);
long seen = tx.execute(status -> {
    members.save(new Member("Kim", true, 3, null));                       // not flushed yet
    return qb.from(Member.class).where(m -> m.getName().equals("Kim")).count();
});
```

不使用 Spring Boot，或 `EntityManagerFactory` bean 有两个及以上时，请自己创建 bean。与自动配置一样使用共享 `EntityManager`。

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

`SharedEntityManagerCreator` 是 Spring 的类（`org.springframework.orm.jpa`）。
请不要在 Spring 应用中使用 `new QueryBuilder(entityManagerFactory)`。这个构建器会为每个查询打开自己的 `EntityManager`，而这个 `EntityManager` 不加入 Spring 事务。

**`@DataJpaTest` 测试。** `@DataJpaTest` 切片测试只加载部分自动配置。repository 方法可以工作，但不会创建 `QueryBuilder` bean。
如果测试需要这个 bean，请使用像 `@SpringBootTest` 这样加载全部自动配置的测试。

示例代码：[`AutoConfigurationTest.java`](https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/AutoConfigurationTest.java)（`query-builder-bean`），[`ManualJavaStartTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaStartTest.java)（`config`）。

<a id="129-kotlin-repositories"></a>
## 12.9 Kotlin repository

用 Kotlin 编写的 repository 需要编译器设置。Kotlin 2.2 及以上使用默认设置即可，低于 2.2 需要 `-Xjvm-default=all`。Spring Data 还需要 `kotlin-reflect`。
详细内容请参考[本页的 Kotlin 版本](../kotlin/12-spring-data-jpa.md#129-kotlin-compile-settings)。

<a id="1210-not-implemented-yet"></a>
## 12.10 不支持的内容

- Spring `Sort` 的 **`ignoreCase()`**。调用时抛出 `UnsupportedOperationException`（见 [12.6](#126-transactions-and-exceptions)）。

---

[Java 路线](README.md) | 上一章：[11. 错误与语言](../11-errors-and-languages.md) | 下一章：[13. Spring Boot 3 与 4](../13-spring-boot-versions.md) | [本页的 Kotlin 版本](../kotlin/12-spring-data-jpa.md)
