# 12. Spring Data JPA（Kotlin）

> [!NOTE]
> **状态：查询执行器和 Spring Boot 自动配置已实现。**
> `LambdaQlExecutor<T>`、Spring `Sort` / `Pageable` 支持（包括排序策略）以及 `QueryBuilder` bean 已在 H2、MariaDB 10.11 和 Oracle 23.26 上用 Spring Boot 3.4.2 测试。测试是用 **Java** 编写的。**Kotlin repository 尚未测试。**
> 更新、删除、插入和 upsert 方法也已用 Java 测试：快捷方法（`deleteAll`、`deleteOne`、`updateAll`、`updateOne`、`insertOne`、`insertAll`、`upsertOne`、`upsertAll`）和构建器（`updateQuery()`、`deleteQuery()`、`insertQuery()`、`upsertQuery()`）。它们需要调用方的事务。lambdaQL 不添加 `@Transactional`（决定 D41）。快捷方法的 Kotlin 写法已用 kotlinc 2.3.21 编译；构建中还没有 Kotlin 测试。
> **尚未验证：** Spring Boot 4。
> 本页的 Kotlin 代码用 Kotlin 语法展示与 Java 测试相同的调用。经过测试的 Java 代码见[本页的 Java 版本](../java/12-spring-data-jpa.md)。
> 完整状态请参阅 [Kotlin 路线 README](README.md)。

[Kotlin 路线](README.md) | [手册首页](../README.md) | 上一章：[11. 错误与语言](../11-errors-and-languages.md) | [本页的 Java 版本](../java/12-spring-data-jpa.md)

---

<a id="121-what-you-get"></a>
## 12.1 你能得到什么

使用 Spring Data JPA 时，你编写 repository 接口。lambdaQL 再增加一个接口：`LambdaQlExecutor<T>`。
你的 repository 继承它，然后你把自己的查询方法写成**带主体的接口方法**，在其中使用 lambdaQL。
这些方法不需要编写 `...RepositoryImpl` 类。

| 组成部分 | 作用 | 小节 |
|---|---|---|
| `LambdaQlExecutor<T>` | 在每个 repository 中提供带 Lambda 条件的 `findAll`、`findSet`、`findFirst`、`findOne`、`count`、`exists` 和 `query()` | [12.3](#123-repository-setup)、[12.4](#124-executor-methods) |
| Spring `Sort` 和 `Pageable` | `findAll(where, sort)`、`findSet(where, sort)` 和返回 Spring `Page` 的 `findAll(where, pageable)` | [12.5](#125-spring-sort-and-pageable) |
| 更新、删除、插入、upsert | `deleteAll { ... }`、`updateOne({ ... }) { ... }` 等快捷方法一次调用就执行一条语句。`updateQuery()`、`deleteQuery()`、`insertQuery()`、`upsertQuery()` 在 repository 的实体上开始 [7. 更新、插入、删除](07-dml.md) 中的语句。它们都需要调用方的事务 | [12.4](#124-executor-methods)、[12.6](#126-transactions-and-exceptions) |
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

使用 Spring Boot 时，Gradle（Kotlin DSL）：

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

Maven：

```xml
<dependency>
  <groupId>org.lambdaql</groupId>
  <artifactId>lambdaql-spring-boot-starter</artifactId>
  <version>1.0.0</version>
</dependency>
```

使用 Maven 时，也请添加 `kotlin-reflect`（见 [12.9](#129-kotlin-compile-settings)）。

不使用 Spring Boot 时，在你自己的 Spring Data JPA 配置（Hibernate 和 JDBC 驱动）之外添加 `lambdaql-spring-data-jpa`。它会带入 `lambdaql-core` 和 Spring Data JPA。

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

要求：

| 项目 | 版本 |
|---|---|
| Java（JVM） | 21 |
| Spring Boot | 3.4 或更高版本（已用 3.4.2 测试）。Spring Boot 4 尚未验证 |
| Spring Data JPA | 3.4 或更高版本 |
| Hibernate | 6.6（Spring Boot 3.4 已经带入） |
| Kotlin | 2.2 或更高版本。较旧的 Kotlin 见 [12.9](#129-kotlin-compile-settings) |
| `kotlin-reflect` | Spring Data 在 Kotlin 中需要它 |

**不支持** Spring Data JPA 3.3 及更早版本。lambdaQL 在 `META-INF/spring.factories` 中注册其执行器，而 Spring Data 从 3.4 起才从这个文件读取 repository fragment。

<a id="123-repository-setup"></a>
## 12.3 设置 repository

把 `LambdaQlExecutor<T>` 加到你的 repository 的父类型列表中。`T` 是该 repository 的实体。
你不需要编写任何配置，也不需要 `Impl` 类。

本章的示例使用 Java 测试中的实体 `Member`（`id`、`name`、`active`、`grade`、`team`）和 `Team`（`id`、`name`）。在 Kotlin 中，`grade` 是 `Int?`，因此可以为 NULL。

Spring 模块目前还没有 Kotlin 测试。用 Java 编写的同一个 repository 已在 [`MemberRepository.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/sample/MemberRepository.java) 中测试（H2、MariaDB 和 Oracle）。

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

最后三个方法执行更新、删除和插入语句。它们需要事务（见 [12.6](#126-transactions-and-exceptions)）。

工作原理：

- **带主体的接口方法由 Spring Data 自身执行。** Spring Data 不会尝试从这些方法生成查询，它只是调用方法主体。lambdaQL 不改变这一点。较旧的 Kotlin 见 [12.9](#129-kotlin-compile-settings)。
- **`LambdaQlExecutor<T>` 是一个 Spring Data repository fragment。** 它的实现登记在 `lambdaql-spring-data-jpa` 的 `META-INF/spring.factories` 中。Spring Data 读取该文件，并把实现添加到每个继承了 `LambdaQlExecutor<T>` 的 repository 中。
- **所有 repository 共享一个执行器对象。** 每次调用仍然使用它自己的 repository 的实体。例如，在 `TeamRepository` 上调用 `teams.count { ... }` 统计的是 `Team` 的行（已用 Java 测试，[`AutoConfigurationTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/AutoConfigurationTest.java)）。

规则：

| # | 规则 |
|---|---|
| 1 | 你的 repository 必须**直接**继承 `LambdaQlExecutor<T>`（写在它自己的父类型列表中）。 |
| 2 | 不要在为此目的继承 `LambdaQlExecutor<T>` 的接口上加 `@NoRepositoryBean`。否则 Spring Data 不会添加 lambdaQL 的实现。 |
| 3 | 把 lambdaQL 查询写在**带主体的接口方法**或服务中。没有主体的方法仍然是 Spring Data 的查询方法（`findByName` 等）。 |
| 4 | 不使用 Spring Boot 时，repository 部分仍然可用，因为它就是普通的 Spring Data。只是 `QueryBuilder` bean 不会自动创建（见 [12.8](#128-the-querybuilder-bean)）。 |

<a id="124-executor-methods"></a>
## 12.4 执行器方法

`Where<T>` 与 `where { ... }` 中的条件 Lambda 相同（见 [3. 条件](03-where.md)）。`SortSpec<T>` 是 lambdaQL 的排序（见 [4.10](04-order-paging.md#410-reusable-and-external-sorts)）。`Sort`、`Pageable` 和 `Page` 是 Spring Data 的类型。

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
| `count(Where<T>)` | `Long` | 匹配的行数 |
| `exists(Where<T>)` | `Boolean` | 有匹配的行时为 `true`。最多读取一行，且不加载实体 |
| `deleteAll(Where<T>)` | `Int` | 用一条 SQL 语句（批量）删除所有匹配的行。与 Spring Data 的 `deleteAll()` 不同（见[下文](#deleteallwhere-is-not-spring-datas-deleteall)）。需要事务 |
| `deleteOne(Where<T>)` | `Int` | 以[单行保护](#one-row-protection-deleteone-and-updateone)删除唯一匹配的行。返回 0 或 1。需要事务 |
| `updateAll(Where<T>, Assign<T>)` | `Int` | 修改所有匹配的行。条件在前，赋值是尾随 Lambda。需要事务 |
| `updateOne(Where<T>, Assign<T>)` | `Int` | 以单行保护修改唯一匹配的行。返回 0 或 1。需要事务 |
| `insertOne(Assign<T>)` | `Int` | 插入一行。返回 1。需要事务 |
| `insertAll(Collection<L>, AssignEach<T, L>)` | `Int` | 每个元素插入一行（每条语句最多 500 个值，规则 U13）。需要事务 |
| `upsertOne(Assign<T>)` | `Int` | 插入一行；如果已有相同 `@Id` 的行，则修改该行。返回数据库给出的行数。需要事务 |
| `upsertAll(Collection<L>, AssignEach<T, L>)` | `Int` | 每个元素 upsert 一行（每行一条语句）。需要事务 |
| `updateQuery()` | `UpdateQuery<T>` | 开始一个更新：`updateQuery().set { ... }.where { ... }.execute()`（见 [7.2](07-2-update.md)）。需要事务 |
| `deleteQuery()` | `DeleteQuery<T>` | 开始一个删除：`deleteQuery().where { ... }.execute()`（见 [7.4](07-4-delete.md)）。需要事务 |
| `insertQuery()` | `InsertQuery<T>` | 开始一个插入：`insertQuery().values { ... }.execute()`（见 [7.5](07-5-insert.md)）。需要事务 |
| `upsertQuery()` | `UpsertQuery<T>` | 开始一个以 `@Id` 为键的 upsert（见 [7.6](07-6-upsert.md)）。需要事务 |

这些简短方法等同于 `query()` 加上一个结果函数：`findAll(w)` 是 `query().where(w).toList()`，`findSet(w)` 是 `query().where(w).toSet()`，`findFirst(w)` 是 `query().where(w).first()`，`findOne(w)` 是 `query().where(w).one()`，依此类推。结果函数的说明见[获取结果](02-getting-started.md#getting-the-result)。
`findAll { ... }` 和 `count { ... }` 与 `JpaRepository` 的 `findAll(Sort)`、`findAll(Example)` 或 `count()` 不冲突，因为参数类型不同。
这些方法返回 Java 的 `Optional`。如果想要可空的值，请使用 `.orElse(null)`。

**`query()`：完整的查询。** 12.3 中的 `topGraded` 使用了它。

在下面的示例中，`repository` 是一个 `MemberRepository`。同样的调用已在 Java 的 [`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java) 中测试（region `find-all-sort`、`find-first`、`find-one`、`count-exists`、`find-set`）。

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

<a id="dml-shortcuts"></a>
### DML 快捷方法

八个方法一次调用就执行一条更新、删除、插入或 upsert：

| 快捷方法 | 等同于 |
|---|---|
| `deleteAll { where }` | `deleteQuery().where { where }.execute()` |
| `deleteOne { where }` | `deleteQuery().where { where }.executeOne()` |
| `updateAll({ where }) { set }` | `updateQuery().set { set }.where { where }.execute()` |
| `updateOne({ where }) { set }` | `updateQuery().set { set }.where { where }.executeOne()` |
| `insertOne { values }` | `insertQuery().values { values }.execute()` |
| `insertAll(rows) { m, r -> values }` | `insertQuery().values(rows) { m, r -> values }.execute()` |
| `upsertOne { values }` | `upsertQuery().values { values }.execute()` |
| `upsertAll(rows) { m, r -> values }` | `upsertQuery().values(rows) { m, r -> values }.execute()` |

- **条件在前。** 赋值是尾随 Lambda：`updateAll({ !it.active }) { it.grade = 7 }`。
- **`All`** 表示所有匹配的行（删除、更新），或列表中的所有元素（插入、upsert）。
- **`One`** 对删除和更新表示单行保护（见下一部分），对插入和 upsert 表示一行。
- 它们需要调用方的事务（见 [12.6](#126-transactions-and-exceptions)）。

带方法体的接口方法，与 12.3 相同。这里的 `gradeInactive` 和 `addGuest` 是 12.3 中那两个方法的简短写法。`NewMember` 是 `data class NewMember(val name: String, val grade: Int)`。

```kotlin
fun purgeWithoutTeam(): Int = deleteAll { it.team == null }
fun deleteByName(name: String): Int = deleteOne { it.name == name }
fun gradeInactive(grade: Int): Int = updateAll({ !it.active }) { it.grade = grade }
fun rename(id: Long, name: String): Int = updateOne({ it.id == id }) { it.name = name }
fun addGuest(name: String): Int = insertOne { it.name = name; it.active = false }
fun addMembers(rows: List<NewMember>): Int = insertAll(rows) { m, r -> m.name = r.name; m.grade = r.grade; m.active = true }
fun putMember(id: Long, name: String): Int = upsertOne { it.id = id; it.name = name; it.active = true }
```

这些 Kotlin 写法已用 kotlinc 2.3.21 编译（编译检查）。构建中还没有 Kotlin 测试。相同的调用已用 Java 在 [`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java) 中测试（region `dml-shortcuts` 和 `dml-one`）。

<a id="one-row-protection-deleteone-and-updateone"></a>
### 单行保护：`deleteOne` 和 `updateOne`

`deleteOne { ... }` 和 `updateOne({ ... }) { ... }` 绝不会改变超过一行：

1. lambdaQL 读取匹配行的 id，最多 2 个。
2. 没有 id：什么都不改变。结果为 0。
3. 2 个 id：什么都不改变。方法抛出 `IncorrectResultSizeDataAccessException`。
4. 1 个 id：lambdaQL 用 `id = :p and (你的条件)` 改变该行。结果为 1；如果该行在此期间不再匹配，则为 0。

语句本身只指定一个 id，因此即使另一个事务在此期间添加了匹配的行，它也无法改变第二行。这种保护不依赖回滚。
核心 API 有同样的保护：`executeOne()`（见 [7.4](07-4-delete.md#delete-exactly-one-row-executeone)）。

```kotlin
val renamed = repository.updateOne({ it.id == kimId }) { it.name = "Kim2" }   // exactly one row
repository.deleteOne { it.active }   // two or more rows match: nothing changes, IncorrectResultSizeDataAccessException
```

<a id="deleteallwhere-is-not-spring-datas-deleteall"></a>
### `deleteAll(where)` 不是 Spring Data 的 `deleteAll()`

| 方法 | 来源 | 删除方式 | cascade、`@PreRemove` |
|---|---|---|---|
| `deleteAll()` | `CrudRepository` | 加载所有实体，然后逐个删除 | 执行 |
| `deleteAll(Iterable)` | `CrudRepository` | 逐个删除给定的实体 | 执行 |
| `deleteAll { ... }`（`Where<T>`） | `LambdaQlExecutor<T>` | 一条 SQL `delete` 语句（批量） | **不执行** |

`deleteAll { ... }` 不加载实体。与所有批量语句一样，它不会修改已加载的实体（规则 U16）。
用它可以快速删除大量行。需要 cascade 或 `@PreRemove` 时，请使用 Spring Data 的方法。

编译器按参数选择方法：无参数、`Iterable` 或 Lambda。这已用 kotlinc 2.3.21 和 javac 生成的字节码检查过。

<a id="builders-for-complex-statements"></a>
### 用于复杂语句的构建器

`updateQuery()`、`deleteQuery()`、`insertQuery()` 和 `upsertQuery()` 开始一条完整的语句。快捷方法做不到的事情用它们来做：多次调用 `set`、`all()`、`orderBy { ... }.limit(n)`（见 [7.4](07-4-delete.md#delete-the-first-rows-limit)）、`toHql()` 或 `onConflict { ... }`。
这些方法名以 `Query` 结尾，因此不会与 `delete(...)` 等 `JpaRepository` 方法冲突。语句与使用 `QueryBuilder` 时相同（见 [7. 更新、插入、删除](07-dml.md)）。处理的行与使用同一个 `where` Lambda 的查询相同。
它们与快捷方法一样需要调用方的事务。与所有 JPA 批量语句一样，它们不会修改事务中已加载的实体。

12.3 中的 `gradeInactive`、`deleteWithoutTeam` 和 `addGuest` 方法使用了它们。相同的方法已用 Java 在 [`MemberRepository.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/sample/MemberRepository.java)（region `dml`）中测试，upsert 在 [`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java)（region `upsert`）中测试：

```kotlin
// teams is a TeamRepository, tx a Spring TransactionTemplate
val changed = tx.execute { teams.upsertQuery().values { it.id = teamId; it.name = "A2" }.execute() }
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

用 `findAll(where, pageable)` 分页：

- 与 Spring Data 一样，页码从 0 开始。lambdaQL 用 `limit` 和 `offset` 读取该页。
- 总数来自一个 `count` 查询。当仅凭这一页就能得出总数时（例如第一页的行数少于页大小），Spring 会跳过它。
- 当排序键不包含 id 时，lambdaQL 会把 id 添加为最后一个键，这样行不会在页之间移动（见[决胜键](04-order-paging.md#tie-breaker-automatic-id)）。没有排序的页按 id 排序。
- `Pageable.unpaged()` 在一页中返回所有行。

同样的调用已在 Java 的 [`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java) 中测试（region `find-all-spring-sort` 和 `page`）。

```kotlin
val rows: List<Member> = repository.findAll({ it.active },
    Sort.by(Sort.Order.desc("grade"), Sort.Order.asc("team.name"), Sort.Order.asc("id")))
repository.findAll({ it.active }, Sort.by("nickname"))   // no such property: PropertyReferenceException

// page 1 (the second page) of the active members by name; the total comes from a count query
val page: Page<Member> = repository.findAll({ it.active }, PageRequest.of(1, 2, Sort.by("name")))
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

`<Entity>` 是 JPA 实体名：默认是简单类名，也可以用 `@Entity(name = ...)` 指定。实体名在持久化单元中唯一（两个实体同名时 Hibernate 会在启动时失败），因此不同包中同名的类不会混淆。未知的名称会使应用启动失败，抛出 `IllegalStateException`（LQL-4042）。
路径会在第一次使用规则时与实体核对（见 4.10 中的[字符串形式的规则](04-order-paging.md#rules-as-strings)）。

属性对 Java 和 Kotlin 相同。它们已在 Java 测试 [`SortPropertiesTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/SortPropertiesTest.java) 中检查（region `properties` 和 `properties-use`，见[本页的 Java 版本](../java/12-spring-data-jpa.md#sort-policy-with-spring)）。该测试也检查了自动配置会注册策略。`application.properties` 示例：

```properties
lambdaql.sort.mode=STRICT
lambdaql.sort.allow.Member=name,team.name
lambdaql.sort.alias.Member.level=grade
```

Kotlin 中的调用（设计示例）：

```kotlin
val byLevel: List<Member> = repository.findAll({ it.active }, Sort.by(Sort.Order.desc("level")))
val byTeam: List<Member> = repository.findAll({ it.active }, Sort.by("team.name", "name"))
repository.findAll({ it.active }, Sort.by("grade"))   // not in the rules: SortNotAllowedException
```

**`SortPolicy` bean。** 要使用由编译器检查的规则，请定义一个 `SortPolicy` bean。同一个自动配置步骤会注册它。
同样的策略已在 [`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java) 中用 Java 测试（region `sort-policy`，手动注册）。下面是设计示例：

```kotlin
@Bean
fun sortPolicy(): SortPolicy = SortPolicy.of(SortMode.STRICT,
    SortRules.of(Member::class.java)
        .allow { it.name }
        .alias("level") { it.grade })

// the client sends "level"; lambdaQL sorts by grade
val rows: List<Member> = repository.findAll({ it.active }, Sort.by(Sort.Order.desc("level"), Sort.Order.asc("name")))
repository.findAll({ it.active }, Sort.by("grade"))   // not in the rules: SortNotAllowedException
```

- `level` 这样的别名不是 `Member` 的属性。lambdaQL 不做 Spring `PropertyPath` 检查，按原样传递它，由策略把它变为 `grade`。
- 既不是别名也不是属性的名称，会在询问策略之前先抛出 `PropertyReferenceException`。
- 在 `STRICT` 模式下，不在规则中的属性会抛出 `SortNotAllowedException`（`LQL-4040`）。Spring 不转换它（见 [12.6](#126-transactions-and-exceptions)）。

**HTTP 400。** lambdaQL 没有 Web 依赖，因此不决定 HTTP 状态。Web 应用通常用 400 Bad Request 响应 `SortNotAllowedException`。
下面的处理器只是示例（未测试）：

```kotlin
@RestControllerAdvice
class SortErrorHandler {

    @ExceptionHandler(SortNotAllowedException::class)
    fun sortNotAllowed(e: SortNotAllowedException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.message).apply {
            setProperty("code", e.code())      // "LQL-4040"
        }
}
```

消息中包含实体类名和排序名称。如果不想让客户端看到实体名，只发送 `e.code()`。

<a id="126-transactions-and-exceptions"></a>
## 12.6 事务与异常

执行器使用 Spring 的共享 `EntityManager`，它绑定到当前事务。

**lambdaQL 有意不在其方法上加 `@Transactional`**（决定 D41）。
更新、删除、插入或 upsert 需要调用方的事务。快捷方法（`deleteAll`、`updateOne` 等）、构建器（`updateQuery()` 等）和 `QueryBuilder` bean 都是如此。
请在你的服务方法上加 `@Transactional`，或者使用 `TransactionTemplate`。没有事务时，调用会在发送任何 SQL 之前失败。

| 情况 | 结果 |
|---|---|
| 在 `@Transactional` 内部执行查询 | lambdaQL 在同一个事务中运行。它能看到尚未 flush 的更改。回滚之后，这些更改对 lambdaQL 也不再存在（已用 Java 测试，`ExecutorTest`） |
| 在事务外部执行查询 | Spring 为这次调用使用一个新的 `EntityManager`，调用结束后关闭它，因此返回的实体处于游离（detached）状态。这是 Spring 的正常行为；lambdaQL 没有对此进行测试 |
| 在 `@Transactional` 或 `TransactionTemplate` 内部执行更新、删除、插入或 upsert | 在该事务中执行，并随它一起回滚（已用 Java 测试） |
| 在事务外部执行更新、删除、插入或 upsert | 在发送任何 SQL 之前失败。什么都不改变（已用 Java 测试，见下一张表） |

只有 `QueryBuilder(entityManagerFactory)` 会在自己的事务中执行 DML（见 [7.8](07-dml.md#transactions)）。不要在 Spring 应用中使用它（见 [12.8](#128-the-querybuilder-bean)）。

与其他 repository 方法一样，错误会经过 Spring 的异常转换：

| 情况 | 异常 |
|---|---|
| `findOne` 匹配 2 行或更多行 | `IncorrectResultSizeDataAccessException`（Spring），而不是 JPA 的 `NonUniqueResultException`（已用 Java 测试） |
| `deleteOne` 或 `updateOne` 匹配 2 行或更多行 | `IncorrectResultSizeDataAccessException`。什么都不改变（已用 Java 测试） |
| Spring `Sort` 中有未知属性 | `PropertyReferenceException`（已用 Java 测试） |
| Spring `Sort` 中使用 `ignoreCase()` | `UnsupportedOperationException`（已用 Java 测试） |
| 在 `STRICT` 模式下，排序策略不允许的排序名称（[12.5](#sort-policy-with-spring)） | `SortNotAllowedException`（`LQL-4040`），不转换（已用 Java 测试） |
| 在 repository 调用中（快捷方法，或带方法体的方法内部的 `execute()`）没有事务时执行更新、删除、插入或 upsert | `InvalidDataAccessApiUsageException`（已用 Java 测试），由 Spring 从 JPA 的 `TransactionRequiredException` 转换而来。在 repository 方法外部（`QueryBuilder` bean）则是 JPA 的 `TransactionRequiredException` |
| 违反规则（U1、U3、U4、U6）的赋值 Lambda | `LambdaQLException`（例如 `TranslationException`，`LQL-3042`），不做转换（见 [7.8](07-dml.md#rules-checked-at-analysis-time)） |

`QueryBuilder` bean（12.8）不是 repository，因此 Spring 不会转换它的错误。在那里，`one()` 和 `executeOne()` 遇到 2 行或更多行时会抛出 `NonUniqueResultException`。

<a id="127-other-fragments-and-jpaspecificationexecutor"></a>
## 12.7 其他 fragment 与 `JpaSpecificationExecutor`

`LambdaQlExecutor<T>` 只是众多 fragment 中的一个。Java 测试中的 repository 同时使用了以下所有内容（已用 Java 测试，`ExecutorTest`）：

| 一起使用的对象 | 可用 | 说明 |
|---|---|---|
| 手写的 fragment（`MemberRepositoryCustom` 与 `MemberRepositoryImpl`） | 是 | Spring Data 照常找到 `Impl` 类 |
| `JpaSpecificationExecutor<T>` | 是（Java） | lambdaQL 的 Lambda 有一个参数，`Specification` 的 Lambda 有三个参数，因此 Java 总是选择正确的 `findAll`。使用 Kotlin Lambda 时尚未测试 |
| `JpaRepository` 的方法（`findAll()`、`count()`、`save(...)`） | 是 | 它们的参数类型不同 |

```kotlin
interface MemberRepository : JpaRepository<Member, Long>, LambdaQlExecutor<Member>,
    JpaSpecificationExecutor<Member>, MemberRepositoryCustom {
    // ...
}

class MemberRepositoryImpl(/* ... */) : MemberRepositoryCustom {   // still found by Spring Data
    // ...
}
```

<a id="128-the-querybuilder-bean"></a>
## 12.8 `QueryBuilder` bean

使用 Spring Boot 时，自动配置 `org.lambdaql.spring.boot.LambdaQlAutoConfiguration` 会创建一个 `QueryBuilder` bean。对于不属于某一个 repository 的查询，请把它注入到你的服务中。

- 该 bean 使用 Spring 的共享、绑定到事务的 `EntityManager`。它的查询会加入当前事务，与 repository 方法相同。
- 只有当恰好存在一个 `EntityManagerFactory`，且你没有定义自己的 `QueryBuilder` bean 时，才会创建该 bean。
- repository 方法（`LambdaQlExecutor<T>`）不需要这个 bean。
- 它的更新、删除、插入和 upsert 语句与 repository 方法一样，需要调用方的事务。没有事务时，它们会在发送任何 SQL 之前抛出 `TransactionRequiredException`。

该 bean 已在 Java 的 [`AutoConfigurationTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/AutoConfigurationTest.java) 中测试（region `query-builder-bean`）：在事务内部，尚未 flush 的成员也会被计入。

```kotlin
@Service
class MemberService(private val qb: QueryBuilder) {

    @Transactional(readOnly = true)
    fun countByName(name: String): Long =
        qb.from(Member::class.java).where { it.name == name }.count()
}
```

不使用 Spring Boot，或者有两个及以上 `EntityManagerFactory` bean 时，请自己创建该 bean。像自动配置那样使用共享的 `EntityManager`：

```kotlin
@Bean
fun queryBuilder(entityManagerFactory: EntityManagerFactory) =
    // the same as the auto-configuration: a shared, transaction-bound EntityManager
    QueryBuilder(SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory))
```

`SharedEntityManagerCreator` 是 Spring 的类（`org.springframework.orm.jpa`）。在 Spring 应用中不要使用 `QueryBuilder(entityManagerFactory)`：那样创建的 builder 会为每个查询打开自己的 `EntityManager`，位于 Spring 事务之外。

**使用 `@DataJpaTest` 的测试。** `@DataJpaTest` 切片测试只加载部分自动配置。repository 方法在那里可以使用，但不会创建 `QueryBuilder` bean。如果测试需要它，请在测试类上加 `@ImportAutoConfiguration(LambdaQlAutoConfiguration::class)`（尚未测试）。

<a id="129-kotlin-compile-settings"></a>
## 12.9 Kotlin 编译设置

只有当 repository 中带主体的方法是接口中的 JVM 默认方法时，Spring Data 才会执行它。

| Kotlin | 要做的事 |
|---|---|
| 2.2 或更高版本 | 无需任何操作。带主体的方法默认会成为 JVM 默认方法。 |
| 低于 2.2 | 请添加编译器选项 `-Xjvm-default=all`。否则该方法在接口中没有主体，Spring Data 会尝试根据方法名生成查询，应用程序无法启动（`No property ... found for type ...`）。 |
| 所有版本 | 添加 `kotlin-reflect`。否则 Spring Data 会因 `ClassNotFoundException: kotlin.reflect.jvm.ReflectJvmMapping` 而失败。 |

```kotlin
// build.gradle.kts, only for Kotlin older than 2.2
kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}
```

这些设置已用原型检查过：kotlinc 2.3.21，以及用 `-jvm-default=disable` 模拟低于 2.2 的 Kotlin 的行为。lambdaQL 的测试代码中还没有 Kotlin repository。

<a id="1210-not-implemented-yet"></a>
## 12.10 尚未实现

- **Spring Boot 4。** 自动配置也写出了 Spring Boot 4 的类名，但只测试了 Spring Boot 3.4.2。
- Spring `Sort` 中的 **`ignoreCase()`**。
- **Kotlin repository** 尚未测试。

---

[Kotlin 路线](README.md) | 上一章：[11. 错误与语言](../11-errors-and-languages.md) | [本页的 Java 版本](../java/12-spring-data-jpa.md)
