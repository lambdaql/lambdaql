<a id="12-spring-data-jpa-kotlin"></a>
# 12. Spring Data JPA（Kotlin）

[Kotlin 路线](README.md) | [手册首页](../README.md) | 上一章：[11. 错误与语言](../11-errors-and-languages.md) | 下一章：[13. Spring Boot 3 与 4](../13-spring-boot-versions.md) | [本页的 Java 版本](../java/12-spring-data-jpa.md)

---

<a id="121-what-you-get"></a>
## 12.1 提供的功能

使用 Spring Data JPA 时，你编写 repository 接口。lambdaQL 在此之上再增加一个接口 `LambdaQlExecutor<T>`。

repository 继承这个接口，自己编写的查询方法则写成使用 lambdaQL 的**带方法体的接口方法**。
不需要为这些方法编写 `...RepositoryImpl` 类。

| 组成部分 | 功能 | 小节 |
|---|---|---|
| `LambdaQlExecutor<T>` | 在每个 repository 中用 Lambda 表达式条件执行 `findAll`、`findSet`、`findFirst`、`findOne`、`count`、`exists`、`query()` | [12.3](#123-repository-setup)、[12.4](#124-executor-methods) |
| Spring `Sort` 和 `Pageable` | `findAll(where, sort)`、`findSet(where, sort)`，以及返回 Spring `Page` 的 `findAll(where, pageable)` | [12.5](#125-spring-sort-and-pageable) |
| 更新、删除、插入、Upsert | `deleteAll { ... }`、`updateOne({ ... }) { ... }` 等快捷方法一次调用就执行一条语句。`updateQuery()`、`deleteQuery()`、`insertQuery()`、`upsertQuery()` 针对 repository 的实体开始 [7. 更新、插入、删除](07-dml.md) 中的语句。都需要调用方的事务 | [12.4](#124-executor-methods)、[12.6](#126-transactions-and-exceptions) |
| `QueryBuilder` bean | Spring Boot 创建供服务使用的 `QueryBuilder` bean | [12.8](#128-the-querybuilder-bean) |

<a id="122-modules"></a>
## 12.2 模块

核心不依赖 Spring，因此也可以和普通 JPA 一起使用。`lambdaql` 和 `lambdaql-spring-data-jpa` 由 Spring Boot 3 与 4 共用，只有 starter 按 Spring Boot 版本分开提供（名称以 `-boot3`、`-boot4` 结尾，[13. Spring Boot 3 与 4](../13-spring-boot-versions.md)）。

| 构件 | 包含内容 | 何时需要 |
|---|---|---|
| `lambdaql` | 引擎和 `QueryBuilder`。不依赖 Spring。Spring Boot 3 与 4 共用 | 始终需要。下面的构件会一并带入 |
| `lambdaql-spring-data-jpa` | `LambdaQlExecutor<T>`、Spring Data 注册、Spring Boot 自动配置（`QueryBuilder` bean）。Spring Data JPA 3.5 与 4.1 共用。使用项目中的 Spring Data JPA | 不使用 Spring Boot 而使用 Spring Data JPA 时 |
| `lambdaql-spring-boot-starter-boot4`、`lambdaql-spring-boot-starter-boot3` | 没有代码。带入 `lambdaql-spring-data-jpa` 和对应 Boot 版本的 `spring-boot-starter-data-jpa` | 使用 Spring Boot 时（与项目的 Boot 版本对应） |

构件已发布到 Maven Central（groupId `io.github.lambdaql`，版本 `0.0.2`）。

在 Spring Boot 4 中使用时的 Gradle（Kotlin DSL）。如果是 Spring Boot 3，请把名称结尾改为 `-boot3`：

```gradle-kts
repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.lambdaql:lambdaql-spring-boot-starter-boot4:0.0.2")
    implementation("org.jetbrains.kotlin:kotlin-reflect")           // Spring Data needs it for Kotlin
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

使用 Maven 时也请添加 `kotlin-reflect`（见 [12.9](#129-kotlin-compile-settings)）。

不使用 Spring Boot 时，请在已有的 Spring Data JPA 配置（Spring Data JPA、Hibernate、JDBC 驱动）中添加 `lambdaql-spring-data-jpa`。这个构件会一并带入 `lambdaql`，Spring Data JPA 则使用项目中已有的版本（3.5 或 4.1）。

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

要求：

| 项目 | 版本 |
|---|---|
| Java（JVM） | 21 或更高 |
| Spring Boot | 3.5 或 4.1 |
| Spring Data JPA | 3.5 或 4.1 |
| Hibernate | 6.6 或 7.4（包含在 Spring Boot 中） |
| Kotlin | 2.2 或更高。更早的版本见 [12.9](#129-kotlin-compile-settings) |
| `kotlin-reflect` | Spring Data 的 Kotlin 支持需要它 |

**不支持** Spring Data JPA 3.3 及更早版本。lambdaQL 在 `META-INF/spring.factories` 中注册执行器，而 Spring Data 从 3.4 起才从这个文件读取 repository fragment。

<a id="123-repository-setup"></a>
## 12.3 设置 repository

把 `LambdaQlExecutor<T>` 加到 repository 的父类型列表中。`T` 是该 repository 的实体。
不需要编写额外的配置和 `Impl` 类。

本章的示例在 Kotlin repository 中使用以 Java 编写的示例实体 `Member`（`id`、`name`、`active`、`grade`、`team`）和 `Team`（`id`、`name`）。在 Kotlin 中，`grade` 是 `Int?`，因此可以为 NULL；`active` 是 `isActive` 属性。

示例 repository 名称中的 `Kt` 是前缀，用于与 Java 路线示例中的 repository 区分。

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

最后三个方法执行更新、删除、插入语句。这些方法需要事务（见 [12.6](#126-transactions-and-exceptions)）。

工作原理：

- **带方法体的接口方法由 Spring Data 直接执行。** Spring Data 不会从这些方法生成查询，而是调用方法体。lambdaQL 不改变这一行为。低于 Kotlin 2.2 时请参阅 [12.9](#129-kotlin-compile-settings)。
- **`LambdaQlExecutor<T>` 是 Spring Data repository fragment。** 实现类注册在 `lambdaql-spring-data-jpa` 的 `META-INF/spring.factories` 中。Spring Data 读取该文件，并把实现添加到每个继承 `LambdaQlExecutor<T>` 的 repository 中。
- **所有 repository 共享一个执行器对象。** 每次调用都使用对应 repository 的实体。例如，在 `TeamRepository` 中 `teams.count { ... }` 返回 `Team` 的行数。

规则：

| # | 规则 |
|---|---|
| 1 | repository 必须**直接**（在它自己的父类型列表中）继承 `LambdaQlExecutor<T>`。 |
| 2 | 不要在为此目的继承 `LambdaQlExecutor<T>` 的接口上加 `@NoRepositoryBean`。加上后 Spring Data 不会添加 lambdaQL 的实现。 |
| 3 | 请把 lambdaQL 查询写在**带方法体的接口方法**或服务中。没有方法体的方法仍然作为 Spring Data 查询方法（`findByName` 等）工作。 |
| 4 | 不使用 Spring Boot 时，repository 部分也是普通的 Spring Data，因此照样可用。只是 `QueryBuilder` bean 不会自动创建（见 [12.8](#128-the-querybuilder-bean)）。 |

示例代码：[`ManualKotlinRepositories.kt`](https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/kotlin/org/lambdaql/spring/ManualKotlinRepositories.kt)（`repository`）。

<a id="124-executor-methods"></a>
## 12.4 执行器方法

`Where<T>` 是与 `where { ... }` 相同的条件 Lambda 表达式（见 [3. 条件](03-where.md)）。`SortSpec<T>` 是 lambdaQL 的排序（见 [4.10](04-order-paging.md#410-reusable-and-external-sorts)）。
`Sort`、`Pageable`、`Page` 是 Spring Data 的类型。

| 方法 | 返回值 | 说明 |
|---|---|---|
| `query()` | `SelectQuery<T>` | 在 repository 的实体上开始查询。用于简短方法无法表达的查询（多次 `where`、`limit`、`offset`、运行时的 `Direction`） |
| `from(Class<E>)` | `SelectQuery<E>` | 在同一持久化单元的另一个实体上开始查询 |
| `findAll(Where<T>)` | `List<T>` | 所有匹配的行。顺序不固定 |
| `findAll(Where<T>, SortSpec<T>)` | `List<T>` | 排序后返回所有匹配的行 |
| `findAll(Where<T>, Sort)` | `List<T>` | 按 Spring `Sort` 排序（见 [12.5](#125-spring-sort-and-pageable)）。`Sort.unsorted()` 表示不排序 |
| `findAll(Where<T>, Pageable)` | `Page<T>` | 一页。只在需要时执行 count 查询。`Pageable.unpaged()` 返回所有行 |
| `findSet(Where<T>)` | `SequencedSet<T>` | 以 Set 返回所有匹配的行。顺序不固定（与 `findAll(Where<T>)` 相同）。不加 `distinct`。Set 的大小小于行数时抛出 `IllegalStateException`（见 [`toSet()`](02-getting-started.md#toset)） |
| `findSet(Where<T>, SortSpec<T>)` | `SequencedSet<T>` | 按排序顺序以 Set 返回所有匹配的行 |
| `findSet(Where<T>, Sort)` | `SequencedSet<T>` | 同上。按 Spring `Sort` 排序（见 [12.5](#125-spring-sort-and-pageable)） |
| `findFirst(Where<T>)` | `Optional<T>` | 匹配的行中的一行。不排序 |
| `findFirst(Where<T>, SortSpec<T>)` | `Optional<T>` | 指定顺序的第一行。只读取一行 |
| `findFirst(Where<T>, Sort)` | `Optional<T>` | Spring `Sort` 顺序的第一行 |
| `findOne(Where<T>)` | `Optional<T>` | 唯一匹配的行。2 行或更多时抛出 `IncorrectResultSizeDataAccessException` |
| `count(Where<T>)` | `Long` | 匹配的行数 |
| `exists(Where<T>)` | `Boolean` | 有匹配的行时为 `true`。最多读取一行，且不加载实体 |
| `deleteAll(Where<T>)` | `Int` | 用一条 SQL 语句删除所有匹配的行（批量）。与 Spring Data 的 `deleteAll()` 不同（见[下文](#deleteallwhere-is-not-spring-datas-deleteall)）。需要事务 |
| `deleteOne(Where<T>)` | `Int` | 以[单行保护](#one-row-protection-deleteone-and-updateone)删除唯一匹配的行。返回 0 或 1。需要事务 |
| `updateAll(Where<T>, Assign<T>)` | `Int` | 更新所有匹配的行。条件在前，赋值是尾随 Lambda 表达式。需要事务 |
| `updateOne(Where<T>, Assign<T>)` | `Int` | 以单行保护更新唯一匹配的行。返回 0 或 1。需要事务 |
| `insertOne(Assign<T>)` | `Int` | 插入一行。返回 1。需要事务 |
| `insertAll(Collection<L>, AssignEach<T, L>)` | `Int` | 每个元素插入一行（每条语句最多 500 个值）。需要事务 |
| `upsertOne(Assign<T>)` | `Int` | 插入一行。已有相同 `@Id` 的行时更新该行。返回数据库报告的行数。需要事务 |
| `upsertAll(Collection<L>, AssignEach<T, L>)` | `Int` | 每个元素 Upsert 一行（每行一条语句）。需要事务 |
| `updateQuery()` | `UpdateQuery<T>` | 开始更新：`updateQuery().set { ... }.where { ... }.execute()`（见 [7.2](07-2-update.md)）。需要事务 |
| `deleteQuery()` | `DeleteQuery<T>` | 开始删除：`deleteQuery().where { ... }.execute()`（见 [7.4](07-4-delete.md)）。需要事务 |
| `insertQuery()` | `InsertQuery<T>` | 开始插入：`insertQuery().values { ... }.execute()`（见 [7.5](07-5-insert.md)）。需要事务 |
| `upsertQuery()` | `UpsertQuery<T>` | 开始以 `@Id` 为键的 Upsert（见 [7.6](07-6-upsert.md)）。需要事务 |

简短方法等同于 `query()` 加上结果函数。`findAll(w)` 对应 `query().where(w).toList()`，`findSet(w)` 对应 `query().where(w).toSet()`，`findFirst(w)` 对应 `query().where(w).first()`，`findOne(w)` 对应 `query().where(w).one()`。结果函数的说明见[获取结果](02-getting-started.md#getting-the-result)。
`findAll { ... }` 和 `count { ... }` 的参数类型不同，因此不会与 `JpaRepository` 的 `findAll(Sort)`、`findAll(Example)`、`count()` 冲突。
这些方法返回 Java 的 `Optional`。

**`query()`：完整的查询。** 12.3 中的 `topGraded` 使用了这个方法。

在下面的示例中，`repository` 是 12.3 中的 `KtMemberRepository`。示例数据是 6 名成员（其中 4 名为活跃成员，包括等级为 NULL 的成员）。

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
### DML 快捷方法

下面 8 个方法一次调用就执行更新、删除、插入、Upsert。

| 快捷方法 | 等同的语句 |
|---|---|
| `deleteAll { where }` | `deleteQuery().where { where }.execute()` |
| `deleteOne { where }` | `deleteQuery().where { where }.executeOne()` |
| `updateAll({ where }) { set }` | `updateQuery().set { set }.where { where }.execute()` |
| `updateOne({ where }) { set }` | `updateQuery().set { set }.where { where }.executeOne()` |
| `insertOne { values }` | `insertQuery().values { values }.execute()` |
| `insertAll(rows) { m, r -> values }` | `insertQuery().values(rows) { m, r -> values }.execute()` |
| `upsertOne { values }` | `upsertQuery().values { values }.execute()` |
| `upsertAll(rows) { m, r -> values }` | `upsertQuery().values(rows) { m, r -> values }.execute()` |

- **条件在前。** 赋值写成尾随 Lambda 表达式：`updateAll({ !it.active }) { it.grade = 7 }`。
- **`All`** 表示所有匹配的行（删除、更新），或列表中的所有元素（插入、Upsert）。
- **`One`** 在删除和更新中表示单行保护（见下一节），在插入和 Upsert 中表示一行。
- 需要调用方的事务（见 [12.6](#126-transactions-and-exceptions)）。

与 12.3 相同，这些是带方法体的接口方法。这里的 `gradeInactive` 和 `addGuest` 是 12.3 中对应方法的简短形式。

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

请在事务中调用这些方法（`@Transactional` 或 `TransactionTemplate`）。没有事务时调用会抛出 `InvalidDataAccessApiUsageException`。

<a id="one-row-protection-deleteone-and-updateone"></a>
### 单行保护：`deleteOne` 和 `updateOne`

`deleteOne { ... }` 和 `updateOne({ ... }) { ... }` 不会改变 2 行或更多行。

1. lambdaQL 读取匹配行的 id，最多 2 个。
2. 没有 id 时，什么都不改变，返回 0。
3. 有 2 个 id 时，什么都不改变，抛出 `IncorrectResultSizeDataAccessException`。
4. 有 1 个 id 时，用 `id = :p and (条件)` 只改变该行，返回 1。如果该行在此期间不再满足条件，则返回 0。

语句本身只指定一个 id，因此即使在此期间另一个事务添加了满足条件的行，也不会改变第二行。这种保护不依赖回滚。

核心 API 也有同样的保护：`executeOne()`（见 [7.4](07-4-delete.md#delete-exactly-one-row-executeone)）。

在 `TransactionTemplate` 中：

<!-- region: ManualKotlinRepositoryTest.kt#dml-one -->
```kotlin
val renamed = repository.updateOne({ it.id == kimId }) { it.name = "Kim2" }   // exactly one row
// two or more rows match: nothing changes
assertThrows<IncorrectResultSizeDataAccessException> { repository.deleteOne { it.isActive } }
```

<a id="deleteallwhere-is-not-spring-datas-deleteall"></a>
### `deleteAll(where)` 与 Spring Data `deleteAll()` 的区别

| 方法 | 所属 | 删除方式 | cascade、`@PreRemove` |
|---|---|---|---|
| `deleteAll()` | `CrudRepository` | 加载所有实体后逐个删除 | 执行 |
| `deleteAll(Iterable)` | `CrudRepository` | 逐个删除传入的实体 | 执行 |
| `deleteAll { ... }`（`Where<T>`） | `LambdaQlExecutor<T>` | 一条 SQL `delete` 语句（批量） | **不执行** |

`deleteAll { ... }` 不加载实体。与其他批量语句一样，它不会反映到已加载的实体上。

需要快速删除大量行时使用它。需要 cascade 或 `@PreRemove` 时，请使用 Spring Data 的方法。

编译器根据参数（无参数、`Iterable`、Lambda 表达式）选择方法。`deleteAll()` 和 `deleteAll { ... }`（快捷方法）可以在同一个 repository 中一起使用。

<a id="builders-for-complex-statements"></a>
### 用于复杂语句的构建器

`updateQuery()`、`deleteQuery()`、`insertQuery()`、`upsertQuery()` 开始一条完整的语句。请在快捷方法无法表达时使用它们：多次 `set`、`all()`、`orderBy { ... }.limit(n)`（见 [7.4](07-4-delete.md#delete-the-first-rows-limit)）、`toHql()`、`onConflict { ... }`。

方法名以 `Query` 结尾，因此不会与 `delete(...)` 等 `JpaRepository` 方法冲突。语句与用 `QueryBuilder` 创建的语句相同（见 [7. 更新、插入、删除](07-dml.md)）。目标行与用同一个 `where` Lambda 表达式查询到的行相同。

与快捷方法一样，需要调用方的事务。与所有 JPA 批量语句一样，它不会反映到事务中已加载的实体上。

12.3 中的 `gradeInactive`、`deleteWithoutTeam`、`addGuest` 方法使用了这些方法。Upsert 用可以为 `@Id` 赋值的 `Team` 来演示（`KtTeamRepository : JpaRepository<Team, Long>, LambdaQlExecutor<Team>`）。

<!-- region: ManualKotlinRepositoryTest.kt#upsert -->
```kotlin
// teams is a KtTeamRepository, tx a Spring TransactionTemplate
val changed = tx.execute { teams.upsertQuery().values { it.id = teamId; it.name = "A2" }.execute() }
```

Upsert 返回数据库报告的行数。插入时为 1；更新时 H2 和 Oracle 为 1，MariaDB 为 2（见 [7.6](07-6-upsert.md#the-row-count)）。

示例代码：[`ManualKotlinRepositoryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/kotlin/org/lambdaql/spring/ManualKotlinRepositoryTest.kt)（`executor`、`dml-one`、`upsert`）、[`ManualKotlinRepositories.kt`](https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/kotlin/org/lambdaql/spring/ManualKotlinRepositories.kt)（`shortcuts`）。

<a id="125-spring-sort-and-pageable"></a>
## 12.5 Spring `Sort` 与 `Pageable`

`findAll(where, sort)`、`findSet(where, sort)`、`findFirst(where, sort)`、`findAll(where, pageable)` 接受 Spring Data 的类型。
lambdaQL 用 `SortSpec.ofPath` 把每个 `Sort.Order` 转换为一个排序键（见 [4.10](04-order-paging.md#410-reusable-and-external-sorts)）。

| Spring | lambdaQL 的处理 |
|---|---|
| 属性名（`"grade"`、`"team.name"`） | 与 Spring Data repository 一样，用 Spring Data `PropertyPath` 检查。名称不存在时抛出 `PropertyReferenceException`（包：Spring Data 3 为 `org.springframework.data.mapping`，4 为 `org.springframework.data.core`）。路径必须以基本值结尾，而不是实体。`team.name` 这样的 to-one 路径使用 `left join`，因此没有 team 的行也包含在结果中。之后由排序策略检查名称（见[下文](#sort-policy-with-spring)） |
| 排序策略的别名（`"level"`） | 不做 `PropertyPath` 检查，按原样传递。由排序策略转换为路径 |
| `NullHandling.NATIVE`（默认值）的 `ASC` / `DESC` | 应用 lambdaQL 的 NULL 规则。升序时 NULL 在前，降序时 NULL 在后。在所有数据库上顺序相同（见 [4.4](04-order-paging.md#44-null-ordering-is-the-same-on-every-database)） |
| `NullHandling.NULLS_FIRST` / `NULLS_LAST` | 按指定的方式使用（`ASC_NULLS_FIRST`、`DESC_NULLS_LAST` 等） |
| `ignoreCase()` | 尚不支持。抛出 `UnsupportedOperationException` |
| `Sort.unsorted()` | 没有 `order by` |

Spring Data JPA 自身的 repository 把 `NATIVE` 的 NULL 位置交给数据库决定，因此不同数据库的顺序可能不同。lambdaQL 使用自己的规则。

`findAll(where, pageable)` 的分页：

- 与 Spring Data 一样，页码从 0 开始。lambdaQL 用 `limit` 和 `offset` 读取该页。
- 总数通过 `count` 查询得到。仅凭该页就能确定总数时（例如第一页的行数少于页大小），Spring 会省略 count 查询。
- 排序键中没有 id 时，lambdaQL 会把 id 添加为最后一个键，因此行不会在页之间移动（见[同序处理](04-order-paging.md#tie-breaker-automatic-id)）。没有排序的页不会加上 `order by`，因此要稳定地分页，请指定 `Sort`。
- `Pageable.unpaged()` 以一页返回所有行。

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

**来自请求参数的排序。** Spring MVC 可以根据 `?page=1&size=20&sort=name,desc` 这样的请求参数创建 `Pageable`。
既没有排序策略也没有 `@Sortable` 字段时，所有持久化属性都会被接受，因此用户可以按任意列排序。此时 lambdaQL 按实体和名称各以 `INFO` 级别记录一次 `LQL-5003`。
如果只允许部分名称，请使用下面的排序策略。

<a id="sort-policy-with-spring"></a>
### 在 Spring 中使用排序策略

排序策略决定允许哪些外部排序名称。`SortRules`、`SortMode` 和规则在 [4.10](04-order-paging.md#sort-policy) 中说明。
Spring `Sort` 或 `Pageable` 始终是外部排序，因此策略会检查其中的所有名称。排序 Lambda 表达式不受检查。

**规则的来源。** 在 Spring Boot 中，自动配置会创建 `EntityManagerFactory` 的策略，并在所有单例 bean 创建之后注册它。
自动配置读取三个地方。

| 来源 | 提供的内容 |
|---|---|
| `SortPolicy` bean | 模式和用 Lambda 表达式编写的 `SortRules` |
| 属性 `lambdaql.sort.*` | 模式和用字符串编写的规则 |
| `@Sortable` 字段（[4.10](04-order-paging.md#rules-from-sortable)） | bean 和属性中都没有规则的实体的规则 |

- 一个实体的规则按表中的顺序只从一个地方获取。不会合并两个地方的规则。
- `lambdaql.sort.mode` 优先于 bean 的模式。两者都没有时，模式为 `INFO`。
- 注册条件是恰好有一个 `EntityManagerFactory`，与 `QueryBuilder` bean 相同（见 [12.8](#128-the-querybuilder-bean)）。其他情况请直接调用 `SortPolicy.register(emf, policy)`。
- 既没有 bean 也没有属性时，不注册任何内容，因此使用默认值：`INFO`，规则只来自 `@Sortable`。

**属性。**

| 属性 | 含义 |
|---|---|
| `lambdaql.sort.mode` | `OFF`、`INFO`（默认值）、`WARN`、`STRICT` |
| `lambdaql.sort.allow.<Entity>` | 允许的路径，用逗号分隔。例如：`name,team.name` |
| `lambdaql.sort.alias.<Entity>.<外部名称>` | 别名的路径。例如：`lambdaql.sort.alias.Member.level=grade` |

**SARGable 模式属性。** `lambdaql.sargable.mode` 取 `OFF`、`WARN`（默认值）、`STRICT` 之一。自动配置以与 `lambdaql.sort.mode` 相同的方式注册它。为 `STRICT` 时，对无法使用索引的条件的分析会以 `NonSargableException`（`LQL-3066`）失败（见 [8.9](08-procedures-functions.md#89-warning-functions-on-columns-and-indexes-sargable)）。

`<Entity>` 处写 JPA 实体名。默认是简单类名，可以用 `@Entity(name = ...)` 更改。

实体名在 persistence unit 中唯一（同名的实体有两个时，Hibernate 会在启动阶段中止）。因此不同包中同名的类也不会混淆。名称不存在时，应用启动会以 `IllegalStateException`（LQL-4042）中止。
路径在第一次使用规则时与实体核对（见 4.10 中的[字符串形式的规则](04-order-paging.md#rules-as-strings)）。

属性在 Java 和 Kotlin 中相同（见[本页的 Java 版本](../java/12-spring-data-jpa.md#sort-policy-with-spring)）。自动配置会注册由属性创建的策略。
`application.properties` 示例：

```properties
lambdaql.sort.mode=STRICT
lambdaql.sort.allow.Member=name,team.name
lambdaql.sort.alias.Member.level=grade
```

**`SortPolicy` bean。** 要使用由编译器检查的规则，请定义 `SortPolicy` bean。同一个自动配置步骤会注册这个 bean。
下面的配置类同时定义了 [12.8](#128-the-querybuilder-bean) 中的 `QueryBuilder` bean。

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

不使用 kotlin-spring（all-open）编译器插件时，请像上面那样把 `@Configuration` 类和 `@Bean` 函数声明为 `open`。

在有这个 bean 的应用中使用 Spring `Sort`。

<!-- region: ManualKotlinBeansTest.kt#sort-policy -->
```kotlin
// the client sends "level"; lambdaQL sorts by grade
val rows: List<Member> = repository.findAll({ it.isActive }, Sort.by(Sort.Order.desc("level"), Sort.Order.asc("name")))
// a property that is not in the rules
assertThrows<SortNotAllowedException> { repository.findAll({ it.isActive }, Sort.by("grade")) }
```

- `level` 这样的别名不是 `Member` 的属性。lambdaQL 不做 Spring `PropertyPath` 检查，按原样传递，由策略转换为 `grade`。
- 既不是别名也不是属性的名称，会在策略检查之前抛出 `PropertyReferenceException`。
- 在 `STRICT` 模式下指定规则中没有的属性时，抛出 `SortNotAllowedException`（`LQL-4040`）。Spring 不转换这个异常（见 [12.6](#126-transactions-and-exceptions)）。

**HTTP 400 响应。** lambdaQL 没有 Web 依赖，因此不决定 HTTP 状态。Web 应用通常在 `@RestControllerAdvice` 的 `@ExceptionHandler(SortNotAllowedException::class)` 中以 400 Bad Request 响应。
异常消息中包含实体类和排序名称。如果不想向客户端暴露实体名，请只发送 `e.code()`（`"LQL-4040"`）。

示例代码：[`ManualKotlinRepositoryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/kotlin/org/lambdaql/spring/ManualKotlinRepositoryTest.kt)（`spring-sort`）、[`ManualKotlinBeansTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/kotlin/org/lambdaql/spring/ManualKotlinBeansTest.kt)（`beans`、`sort-policy`）。

<a id="126-transactions-and-exceptions"></a>
## 12.6 事务与异常

执行器使用绑定到当前事务的 Spring 共享 `EntityManager`。

**lambdaQL 不在方法上加 `@Transactional`。** 这是有意的设计。
更新、删除、插入、Upsert 需要调用方的事务。快捷方法（`deleteAll`、`updateOne` 等）、构建器（`updateQuery()` 等）和 `QueryBuilder` bean 都是如此。

请在服务方法上加 `@Transactional`，或使用 `TransactionTemplate`。没有事务时，调用会在发送 SQL 之前失败。

| 情况 | 行为 |
|---|---|
| 在 `@Transactional` 中查询 | lambdaQL 在同一个事务中执行，也能查询到尚未 flush 的更改。回滚之后，lambdaQL 也查询不到这些更改 |
| 在 `@Transactional` 或 `TransactionTemplate` 中更新、删除、插入、Upsert | 在该事务中执行，并随事务一起回滚 |
| 在事务外更新、删除、插入、Upsert | 在发送 SQL 之前失败。什么都不改变（见下表） |

在自己的事务中执行 DML 的构建器只有 `QueryBuilder(entityManagerFactory)`（见 [7.8](07-dml.md#transactions)）。请不要在 Spring 应用中使用这个构建器（见 [12.8](#128-the-querybuilder-bean)）。

与其他 repository 方法一样，错误会经过 Spring 的异常转换。

| 情况 | 异常 |
|---|---|
| `findOne` 的条件匹配 2 行或更多 | Spring `IncorrectResultSizeDataAccessException`，而不是 JPA `NonUniqueResultException` |
| `deleteOne` 或 `updateOne` 的条件匹配 2 行或更多 | `IncorrectResultSizeDataAccessException`。什么都不改变 |
| Spring `Sort` 中有不存在的属性 | `PropertyReferenceException` |
| Spring `Sort` 的 `ignoreCase()` | `UnsupportedOperationException` |
| 在 `STRICT` 模式下，排序策略不允许的排序名称（[12.5](#sort-policy-with-spring)） | `SortNotAllowedException`（`LQL-4040`），不转换 |
| 在 repository 调用中（快捷方法，或带方法体的方法中的 `execute()`）没有事务就执行更新、删除、插入、Upsert | `InvalidDataAccessApiUsageException`。这是 Spring 由 JPA `TransactionRequiredException` 转换而来的异常。在 repository 方法之外（`QueryBuilder` bean）则是 JPA `TransactionRequiredException` |
| 违反规则的赋值 Lambda 表达式 | `LambdaQLException`（例如 `TranslationException`，`LQL-3042`）。不转换（见 [7.8](07-dml.md#rules-checked-at-analysis-time)） |

`QueryBuilder` bean（12.8）不是 repository，因此不经过 Spring 的异常转换。在这个 bean 中，`one()` 和 `executeOne()` 的条件匹配 2 行或更多时抛出 `NonUniqueResultException`。

<a id="127-other-fragments-and-jpaspecificationexecutor"></a>
## 12.7 其他 fragment 与 `JpaSpecificationExecutor`

`LambdaQlExecutor<T>` 是多个 fragment 中的一个。下面的 Kotlin repository 同时使用了所有这些组合。

| 一起使用的对象 | 是否可用 | 说明 |
|---|---|---|
| 手写的 fragment（`MemberRepositoryCustom` 与 `MemberRepositoryImpl`） | 可用 | Spring Data 照常查找 `Impl` 类 |
| `JpaSpecificationExecutor<T>` | 可用 | lambdaQL 的 Lambda 表达式有一个参数，`Specification` 的 Lambda 表达式有三个参数，因此 Kotlin 也会为 `findAll { it.isActive }` 选择 lambdaQL 的 `findAll`。`Specification` 写成 `Specification<Member> { root, _, cb -> ... }` |
| `JpaRepository` 方法（`findAll()`、`count()`、`save(...)`） | 可用 | 参数类型不同 |

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

`findAll { it.isActive }`、接受 `Specification` 的 `findAll`、`countByHand()`、`count()` 可以在同一个 repository 中一起调用。

示例代码：[`ManualKotlinRepositories.kt`](https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/kotlin/org/lambdaql/spring/ManualKotlinRepositories.kt)（`fragments`）。

<a id="128-the-querybuilder-bean"></a>
## 12.8 `QueryBuilder` bean

在 Spring Boot 中，自动配置 `org.lambdaql.spring.boot.LambdaQlAutoConfiguration` 会创建 `QueryBuilder` bean。不属于某一个 repository 的查询，请把这个 bean 注入到服务中编写。

- 该 bean 使用绑定到事务的 Spring 共享 `EntityManager`。因此，与 repository 方法一样，查询会加入当前事务。
- 只有当恰好有一个 `EntityManagerFactory`，且用户没有另行定义 `QueryBuilder` bean 时，才会创建该 bean。
- repository 方法（`LambdaQlExecutor<T>`）不需要这个 bean。
- 用这个 bean 创建的更新、删除、插入、Upsert 语句，与 repository 方法一样需要调用方的事务。没有事务时，会在发送 SQL 之前抛出 `TransactionRequiredException`。

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

在 `TransactionTemplate` 中保存成员后立即调用 `countByName`，尚未 flush 的成员也会被计入。这是因为服务的查询加入了调用方的事务。

不使用 Spring Boot，或者有两个及以上 `EntityManagerFactory` bean 时，请自己创建 bean。与自动配置一样使用共享的 `EntityManager`。
[12.5](#sort-policy-with-spring) 的 `KtLambdaQlConfig` 中的 `queryBuilder` bean 就是一个例子。这个 bean 替代自动配置的 bean（`QueryBuilder` bean 只有 `queryBuilder` 一个），并加入调用方的事务。

`SharedEntityManagerCreator` 是 Spring 的类（`org.springframework.orm.jpa`）。
请不要在 Spring 应用中使用 `QueryBuilder(entityManagerFactory)`。这个构建器为每个查询打开自己的 `EntityManager`，而这个 `EntityManager` 不加入 Spring 事务。

**`@DataJpaTest` 测试。** `@DataJpaTest` 切片测试只加载部分自动配置。repository 方法可以使用，但不会创建 `QueryBuilder` bean。

示例代码：[`ManualKotlinRepositories.kt`](https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/kotlin/org/lambdaql/spring/ManualKotlinRepositories.kt)（`service`）。

<a id="129-kotlin-compile-settings"></a>
## 12.9 Kotlin 编译设置

只有当带方法体的 repository 方法是接口中的 JVM default 方法时，Spring Data 才会执行该方法体。

| Kotlin | 设置 |
|---|---|
| 2.2 或更高 | 无需设置。带方法体的方法默认编译为 JVM default 方法。 |
| 低于 2.2 | 添加编译器选项 `-Xjvm-default=all`。没有这个选项时，接口中没有方法体，Spring Data 会尝试根据方法名生成查询，应用无法启动（`No property ... found for type ...`）。 |
| 所有版本 | 添加 `kotlin-reflect`。没有它时，Spring Data 会以 `ClassNotFoundException: kotlin.reflect.jvm.ReflectJvmMapping` 失败。 |

```gradle-kts
// build.gradle.kts, only for Kotlin older than 2.2
kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}
```

低于 Kotlin 2.2 时的行为是用 `-jvm-default=disable` 重现的结果（实测）。

<a id="1210-not-implemented-yet"></a>
## 12.10 尚未实现

- **Spring Boot 版本。** 未确认 Spring Boot 3.5 和 4.1 以外的版本（见 [13. Spring Boot 3 与 4](../13-spring-boot-versions.md)）。
- Spring `Sort` 的 **`ignoreCase()`**。

---

[Kotlin 路线](README.md) | 上一章：[11. 错误与语言](../11-errors-and-languages.md) | 下一章：[13. Spring Boot 3 与 4](../13-spring-boot-versions.md) | [本页的 Java 版本](../java/12-spring-data-jpa.md)
