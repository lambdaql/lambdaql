# 2. 入门（Kotlin）

> [!IMPORTANT]
> 本页比韩文手册旧。最新规则请参见[韩文页面](../../ko/kotlin/02-getting-started.md)。

> [!NOTE]
> **状态：** 目前你可以从源码构建 lambdaQL 并运行它的测试。
> `where` 条件和 `toList()` 可以端到端运行。它们已在 H2、MariaDB 和 Oracle 上测试，大多使用 **Java** Lambda。Kotlin Lambda 已对 `?.` 路径、捕获值、`in`、子查询、带 data class 和 `groupBy` 的 `select`、update、insert 和 delete 进行测试（[`KotlinSelectTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinSelectTest.kt)、[`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)）。
> 结果函数 `first()`、`one()`、`count()`、`exists()` 和 `toSet()` 也可以运行（见[获取结果](#getting-the-result)；同样只用 Java Lambda 测试过）。
> 排序与分页的核心部分（`orderBy`、`limit`、`offset`、`SortSpec`）也可以运行（有一个 Kotlin 排序测试；其余用 Java Lambda 测试）。
> Spring Data JPA 模块（repository 执行器和 Spring Boot 自动配置）也可以运行（见 [12. Spring Data JPA](12-spring-data-jpa.md)；只用 Java repository 测试过）。
> 更新、插入、删除和 upsert 也可以运行（见 [7. 更新、插入、删除](07-dml.md)；upsert 只用 Java Lambda 测试过）。
> 子查询和 `select`（带 `groupBy`、`having` 和 `Agg`）同样可以使用（见 [5. 子查询](05-subquery.md) 和 [6. 查询列、聚合与分组](06-select-group.md)）。
> 显式 join、派生表和 CTE、存储过程与数据库函数同样可以使用（见 [6](06-select-group.md#68-explicit-joins) 和 [8](08-procedures-functions.md)；只用 Java Lambda 测试过）。
> 目前还没有任何构件发布到 Maven Central。请按下文从源码构建。

[Kotlin 路线](README.md) | [手册首页](../README.md) | 上一章：[1. 概述](../01-overview.md) | 下一章：[3. 条件](03-where.md) | [本页的 Java 版本](../java/02-getting-started.md)

公共页面：[1. 概述](../01-overview.md) · [11. 错误与语言](../11-errors-and-languages.md)

---

## 环境要求

| 项目 | 版本 | 说明 |
|---|---|---|
| Kotlin | 2.x | 设计已用 kotlinc 1.7.22、2.1.10、2.2.21、2.3.0 和 2.3.21 检查。 |
| JVM | Java 21 | 构建 lambdaQL 需要。 |
| `-jvm-target` | 1.8、11、17 或 21 | 这四个版本编译查询 Lambda 的方式都相同。 |
| JPA 提供者 | Hibernate ORM 6.6 | lambdaQL 生成 Hibernate 6 HQL。设计已用 6.6.5 验证。不支持其他 JPA 提供者。 |
| Jakarta Persistence | 3.1 | 随 Hibernate 6.6 一起提供。 |
| 数据库 | H2 2.3、MariaDB 10.11、Oracle 23.26 (23ai) | 这三种已验证。其他数据库可能可用，但未经测试。 |
| Spring Boot | 3.4 | 可选。项目的测试使用 Spring Boot 3.4。 |
| Kotlin 运行时 | – | lambdaQL 不会向你的依赖中添加 `kotlin-stdlib` 或 `kotlin-reflect`。 |

<a id="build-from-source"></a>
## 从源码构建

```bash
git clone https://github.com/codebit0/lambdaql.git
cd lambdaql
./gradlew build
```

- Gradle wrapper（8.10）只能在 **JDK 23 或更低版本**上运行。请使用 JDK 21。
- 仓库中有 `gradle/gradle-daemon-jvm.properties`，因此 Gradle 守护进程会使用 Java 21。
- 在 IntelliJ IDEA 中，把 **Settings > Build Tools > Gradle > Gradle JVM** 设置为 Java 21 JDK。
- 构建包含三个模块：`query`（构件 `lambdaql-core`）、`spring-data-jpa`（`lambdaql-spring-data-jpa`）和 `spring-boot-starter`（`lambdaql-spring-boot-starter`）。Spring 模块的说明见 [12. Spring Data JPA](12-spring-data-jpa.md)。

运行单元测试：

```bash
./gradlew :query:test
```

[`SelectQueryTest.java`](../../../../query/src/test/java/org/lambdaql/query/SelectQueryTest.java) 中的测试展示了 lambdaQL 目前能做什么：`qb.from(...).where(...)`、HQL、参数，以及在 H2、MariaDB 和 Oracle 上返回的行。[`ConditionDecodeTest.java`](../../../../query/src/test/java/org/lambdaql/engine/decode/ConditionDecodeTest.java) 测试内部的条件解码器。这些测试是用 Java 编写的。Kotlin 测试还在计划中（`query/src/test/kotlin`）。

### 在你的项目中使用本地构建

把三个模块的 jar 发布到本地 Maven 仓库：

```bash
./gradlew publishToMavenLocal
```

然后把 `lambdaql-core` 添加到你的项目中（Gradle Kotlin DSL）：

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("org.lambdaql:lambdaql-core:1.0.0")
}
```

Maven：

```xml
<dependency>
  <groupId>org.lambdaql</groupId>
  <artifactId>lambdaql-core</artifactId>
  <version>1.0.0</version>
</dependency>
```

使用 Spring Data JPA 或 Spring Boot 时，请改用 `lambdaql-spring-boot-starter` 或 `lambdaql-spring-data-jpa`。它们会带入 `lambdaql-core`（见 [12.2](12-spring-data-jpa.md#122-modules)）。

> [!WARNING]
> 这些构件**尚未发布到 Maven Central**。
> 版本 `1.0.0` 来自仓库的 `gradle.properties`，在首次发布前可能改变。lambdaQL 发布后请再次查看本页。

<a id="kotlin-compiler-settings"></a>
## Kotlin 编译器设置

| 设置 | 怎么做 | 原因 |
|---|---|---|
| `-Xsam-conversions` | 保持默认值（`indy`）。**不要**设置 `-Xsam-conversions=class`。 | 使用 `class` 时，Kotlin 生成的是一个类，而不是可序列化的 Lambda。lambdaQL 无法读取它（限制 KC1）。 |
| `-java-parameters` | 如果你调用存储过程，推荐开启。 | 这样 lambdaQL 就能把你的参数名与数据库进行比较（见 [8.4](08-procedures-functions.md#84-warning-argument-order)）。 |
| JPA 插件 | 使用 `kotlin("plugin.jpa")` 和 all-open（或 `kotlin("plugin.spring")`）。 | JPA 需要无参构造函数和 open 类。这对于 Kotlin JPA 实体来说很常见。 |

示例（`build.gradle.kts`）：

```kotlin
plugins {
    kotlin("jvm")
    kotlin("plugin.jpa")
    kotlin("plugin.spring")   // 或 all-open 插件
}

kotlin {
    compilerOptions {
        javaParameters = true   // 等同于 -java-parameters
    }
}
```

请使用与你的 Kotlin 版本相匹配的插件版本。

<a id="example-entities"></a>
## 示例实体

Kotlin 路线中的所有示例都使用这些实体。

```kotlin
@Entity
@Table(name = "orders")
class Order(
    @Id @GeneratedValue
    var id: Long? = null,
    var price: Double? = null,
    var status: Int? = null,
    var active: Boolean = false,          // Kotlin Boolean：JVM 基本类型，永远不为 NULL
    var description: String? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    var customer: Customer? = null,
    var updateAt: LocalDateTime? = null,
    @OneToMany(mappedBy = "order")
    var items: MutableList<Item> = mutableListOf(),
)

@Entity
class Customer(
    @Id @GeneratedValue
    var id: Long? = null,
    var name: String? = null,
    var grade: Int? = null,
)

@Entity
class Item(
    @Id @GeneratedValue
    var id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    var order: Order? = null,
    var price: Double? = null,
    var qty: Int = 0,                     // Kotlin Int：JVM 基本类型，永远不为 NULL
)
```

实体规则：

| 规则 | 原因 |
|---|---|
| 使用普通的 public `var` 或 `val` 属性。 | lambdaQL 读取 getter（`it.price` 就是 getter `getPrice()`），并把它映射到 JPA 属性。 |
| 对可能为 NULL 的列使用可空类型（`Double?`、`String?`）。 | Kotlin 的非空类型无法阻止数据库中的 NULL。Hibernate 会不加检查地把列值写入字段。 |
| 不要在查询中使用 `@JvmField`、`internal` 属性、`value class` 属性或 `@get:JvmName`。 | lambdaQL 无法把它们映射到 JPA 属性（限制 KC15、KC16）。 |
| `Boolean`、`Int`、`Long`、`Double`（不可空）是 JVM 基本类型。 | 它们永远不会存放 NULL。lambdaQL 知道这一点，可以生成更简单的 SQL。 |

## 创建 QueryBuilder

`QueryBuilder` 是入口。它需要一个 JPA `EntityManagerFactory` 或一个 `EntityManager`。

```kotlin
val qb = QueryBuilder(entityManagerFactory)
```

创建一个 `QueryBuilder` 并重复使用它。不要为每个查询都创建一个新的。

### 使用纯 JPA

```kotlin
val emf = Persistence.createEntityManagerFactory("my-unit")
val qb = QueryBuilder(emf)
```

### 使用 Spring Boot

使用 `lambdaql-spring-boot-starter` 时，Spring Boot 会为你创建 `QueryBuilder` bean（见 [12.8](12-spring-data-jpa.md#128-the-querybuilder-bean)）。
不使用它时，请自己创建这个 bean。像自动配置那样，使用 Spring 的共享 `EntityManager`。

```kotlin
@Configuration
class LambdaQlConfig {

    @Bean
    fun queryBuilder(entityManagerFactory: EntityManagerFactory) =
        QueryBuilder(SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory))
}
```

然后在需要的地方注入 `QueryBuilder`。

```kotlin
@Service
class OrderService(private val qb: QueryBuilder) {

    @Transactional(readOnly = true)
    fun activeOrders(): List<Order> =
        qb.from(Order::class.java)
            .where { it.active }
            .toList()
}
```

- 共享的 `EntityManager` 绑定到当前事务。在 `@Transactional` 内部，lambdaQL 在同一个事务中运行。
- 在 Spring 应用中不要使用接收 `EntityManagerFactory` 的构造函数。那样创建的 builder 会为每个查询打开自己的 `EntityManager`，位于 Spring 事务之外。
- 如果要在 Spring Data 仓库中编写 lambdaQL 查询，请参阅 [12. Spring Data JPA](12-spring-data-jpa.md)。

## 你的第一个查询

```kotlin
val orders = qb.from(Order::class.java)
    .where { it.status == 1 && it.price!! > 100.0 }
    .toList()
// HQL: select e0 from Order e0 where e0.status = 1 and e0.price > 100.0
```

**关于 SQL 块。** `…` 是缩短的 select 列表（实体的所有列），`?` 是绑定参数。这些行来自 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)。该测试使用 Hibernate 6.6.5 在 H2 2.3、MariaDB 10.11 和 Oracle 23.26 上运行每个查询，如果 Hibernate 的 SQL 发生变化，测试就会失败。 该测试使用 Java Lambda。含义相同的 Kotlin Lambda 生成相同的 HQL，因此 SQL 也相同。

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `to-hql`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>100.0
```

- `qb.from(Order::class.java)` 开始一个针对 `Order` 的查询。
- `where { ... }` 接收一个条件 Lambda。使用尾随 Lambda 和 `it`。
- `toList()` 执行查询并返回 `List<Order>`。
- `toList()` 返回所有行。其他结果函数（`first()`、`one()`、`count()`、`exists()`、`toSet()`）见[获取结果](#getting-the-result)。

为什么要用 `!!`？`price` 是 `Double?`，对于可空值，Kotlin 无法编译 `it.price > 100.0`。`!!` 让它可以编译。lambdaQL 会**忽略** `!!`。`price` 为 NULL 的行只是不会被返回（SQL 语义）。

为什么 `1` 和 `100.0` 被直接写入 HQL，而不是绑定参数？它们是**写在 Lambda 中的常量**。lambdaQL 会把常量（数字、字符串、布尔值、`null`、enum 常量、`const val`）作为字面量写入 HQL。来自 Lambda 外部的值则不同：它们始终是绑定参数（见下一节和 [3.3](03-where.md#33-basic-form)）。

### 使用 Lambda 外部的值

Lambda 可以使用局部值、属性和函数参数。这些是**捕获的值**。
lambdaQL 把它们转换为绑定参数。每次执行查询时，它都会读取新的值。

```kotlin
fun expensive(status: Int, minPrice: Double): List<Order> =
    qb.from(Order::class.java)
        .where { it.status == status && it.price!! >= minPrice }
        .toList()
// HQL: select e0 from Order e0 where e0.status = :p1 and e0.price >= :p2
```

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `start-captured`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=? and o1_0.price>=?
```

### 多次调用 `where`

你可以多次调用 `where`。lambdaQL 用 `AND` 连接这些条件。

```kotlin
qb.from(Order::class.java)
    .where { it.active }
    .where { it.status == 1 || it.status == 2 }
    .toList()
// HQL: select e0 from Order e0
//      where e0.active = true and (e0.status = 1 or e0.status = 2)
```

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `start-many-where`：

```sql
-- H2
select …
from orders o1_0
where o1_0.active=true and (o1_0.status=1 or o1_0.status=2)

-- MariaDB, Oracle
select …
from orders o1_0
where o1_0.active=1 and (o1_0.status=1 or o1_0.status=2)
```

当你逐步添加条件时，这很有用：

```kotlin
var query = qb.from(Order::class.java).where { it.active }
if (minPrice != null) {
    query = query.where { it.price!! >= minPrice }
}
val orders = query.toList()
```

### 指向其他实体的路径

像 `it.customer?.name` 这样的路径指代关联实体的一个列。
在 `where` 中，lambdaQL 默认会添加内连接 `join`。没有客户的订单**不在**结果中。

```kotlin
qb.from(Order::class.java)
    .where { it.customer?.name == name }
    .toList()
// HQL: select e0 from Order e0 join e0.customer j1 where j1.name = :p1
//      （这里 name 是非空 String，所以 lambdaQL 生成普通的 =；见 3.6）
```

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `inner-join`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name=?
```

`it.customer!!.name` 生成相同的 HQL。使用你喜欢的写法即可。
要查找没有客户的订单，请比较关联本身：`it.customer == null` 生成 `e0.customer is null`（不需要 join）。
`it.customer?.name == null` **不会**包含它们。
如果 null 守卫保留了没有客户的订单（`it.customer == null || ...`），lambdaQL 会使用 `left join`。见 [3.10](03-where.md#310-association-paths)。

<a id="getting-the-result"></a>
## 获取结果

`toList()` 并不是执行查询的唯一方式。lambdaQL 有六个结果函数（决策 D38 和 D42）：

| 函数 | 返回 | 可以在哪里调用 | 作用 |
|---|---|---|---|
| `toList()` | `List<T>` | `where` 之后，以及 `orderBy` / `limit` / `offset` 之后 | 返回所有行（或一页）。 |
| `first()` | `Optional<T>` | `where` 之后，以及 `orderBy` / `limit` / `offset` 之后 | 返回第一行。只读取一行。 |
| `one()` | `Optional<T>` | 只能在 `where` 之后 | 返回唯一的一行。如果有 2 行或更多行匹配，抛出 `NonUniqueResultException`。最多读取 2 行。 |
| `count()` | `Long` | 只能在 `where` 之后 | 返回匹配的行数。 |
| `exists()` | `Boolean` | 只能在 `where` 之后 | 如果有行匹配，返回 `true`。只读取一行，不加载任何实体。 |
| `toSet()` | `SequencedSet<T>` | `where` 之后，以及 `orderBy` / `limit` / `offset` 之后 | 以保持行顺序的 Set 返回所有行（或一页）。如果 Set 比行数小，抛出 `IllegalStateException`。 |

"`where` 之后" 指的是通过 `qb.from(...)` 和 `where { }` 得到的查询（类型为 `SelectQuery`）。本手册把它称为 **where 阶段**。也可以不写 `where`，直接在 `qb.from(...)` 之后调用这些函数。

在 `orderBy`、`limit` 或 `offset` 之后，查询的类型是 `OrderedSelectQuery`。它的结果函数只有 `toList()`、`toSet()` 和 `first()`。在那里 `one()`、`count()` 和 `exists()` 无法编译。
这是有意的设计（决策 D38）：`limit(10).count()` 可能表示"所有行"，也可能表示"最多 10 行"，因此 lambdaQL 不允许这种写法。

`first()` 和 `one()` 返回 Java 的 `Optional`。在 Kotlin 中，`.orElse(null)` 可以把它变成可空值（`Order?`）。

目前还没有针对结果函数的 Kotlin 测试。用 Java 写的同样的调用在 [`ResultQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ResultQueryTest.java) 中测试过（H2、MariaDB 和 Oracle）。已测试的示例请看[本页的 Java 版本](../java/02-getting-started.md#getting-the-result)。

<a id="first"></a>
### `first()`

没有 `orderBy` 时，"第一行"指的是 id 最小的那一行（规则 S9）。因此每次得到的都是同一行。
要选择顺序，请先调用 `orderBy { }`。之后 `first()` 返回按该顺序排列的第一行。`offset` 会被保留：`orderBy { ... }.offset(1).first()` 返回第二行。
如果没有匹配的行，结果是 `Optional.empty()`。

```kotlin
val cheapest: Order? = qb.from(Order::class.java)
    .orderBy { o, by -> by.asc(o.price) }
    .first()
    .orElse(null)
// HQL: select e0 from Order e0 order by e0.price asc nulls first, e0.id asc

// without orderBy, "first" is the row with the smallest id
val firstActive = qb.from(Order::class.java).where { it.active }.first()
// HQL: select e0 from Order e0 where e0.active = true order by e0.id asc
```

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `first#2`（`firstActive`）：

```sql
-- H2
select …
from orders o1_0
where o1_0.active=true
order by o1_0.id
fetch first ? rows only

-- MariaDB
select …
from orders o1_0
where o1_0.active=1
order by o1_0.id
limit ?

-- Oracle
select …
from orders o1_0
where o1_0.active=1
order by o1_0.id
fetch first ? rows only
```

`first()` 只读取一行，因此它是一个分页查询。lambdaQL 会把 `id` 添加为最后一个排序键（见 [4.8](04-order-paging.md#tie-breaker-automatic-id)），Hibernate 会为每个数据库写出分页 SQL：H2 和 Oracle 上是 `fetch first ? rows only`，MariaDB 上是 `limit ?`。这里的 `?` 是 1。
针对 `cheapest` 的测试使用的 `price` 永远不会为 NULL，因此它的 HQL 没有 `nulls` 部分。这里的 `price` 是 `Double?`，因此 HQL 略有不同（请看 Java 页面中的示例 `first`）。

<a id="one"></a>
### `one()`

当条件最多只能匹配一行时（例如唯一键），请使用 `one()`。
没有行时得到 `Optional.empty()`。两行或更多行时抛出 `jakarta.persistence.NonUniqueResultException`。如果返回任意一个匹配的行都可以，请使用 `first()`。

```kotlin
fun find(id: Long): Order? =
    qb.from(Order::class.java).where { it.id == id }.one().orElse(null)
// HQL: select e0 from Order e0 where e0.id = :p1

// two or more rows: an exception (use first() if any row is fine)
assertThrows<NonUniqueResultException> { qb.from(Order::class.java).where { it.active }.one() }
```

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `one`：

```sql
-- H2, Oracle
select …
from orders o1_0
where o1_0.id=?
fetch first ? rows only

-- MariaDB
select …
from orders o1_0
where o1_0.id=?
limit ?
```

`one()` 最多向数据库请求 2 行。这足以发现是否存在第二行。因此 SQL 中有 `fetch first ? rows only`（H2、Oracle）或 `limit ?`（MariaDB），这里的 `?` 是 2。没有 `order by`，因为这里顺序无关紧要。

<a id="count-and-a-paged-screen"></a>
### `count()` 与分页画面

`count()` 返回匹配的行数。它只存在于 where 阶段，因此 `limit` 和 `offset` 永远不会改变它。

对于分页画面，请把 where 阶段的查询保存在一个值中。在它上面调用 `count()` 得到总数。然后在同一个值上调用 `orderBy { }.limit(...).toList()` 得到这一页。

```kotlin
val active = qb.from(Order::class.java).where { it.active }
val total: Long = active.count()                       // every matching row
val page = active.orderBy { o, by -> by.desc(o.price) }.limit(20).toList()
// count HQL: select count(e0) from Order e0 where e0.active = true
```

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `count`：

```sql
-- H2
select count(o1_0.id)
from orders o1_0
where o1_0.active=true

-- MariaDB, Oracle
select count(o1_0.id)
from orders o1_0
where o1_0.active=1
```

Hibernate 把 `count(e0)` 变成 `select count(o1_0.id)`：它统计 id 列。计数查询没有分页。

<a id="exists"></a>
### `exists()`

如果至少有一行匹配，`exists()` 返回 `true`。它执行 `select 1`，最多读取一行。不会加载任何实体。

```kotlin
val anyHighStatus: Boolean = qb.from(Order::class.java).where { it.status == 3 }.exists()
// HQL: select 1 from Order e0 where e0.status = 3
```

已测试的示例（Java 测试中的 `exists`）使用了一个 Kotlin 示例实体中没有的枚举属性。它的 SQL 形式相同：H2 和 Oracle 上是 `select 1 from orders o1_0 where … fetch first ? rows only`，MariaDB 上是 `… limit ?`。这里的 `?` 是 1。请看[本页的 Java 版本](../java/02-getting-started.md#exists)。

<a id="toset"></a>
### `toSet()`

`toSet()` 像 `toList()` 一样执行查询，但返回 `SequencedSet<T>`（决策 D42）。
它是 `LinkedHashSet`，所以元素保持行的顺序。有 `orderBy { }` 时，就是排序的顺序。

```kotlin
val byPrice: SequencedSet<Order> = qb.from(Order::class.java)
    .orderBy { o, by -> by.desc(o.price) }
    .toSet()
```

SQL 与 `toList()` 相同。lambdaQL **不会**添加 `distinct`：

- 每个实体行都有主键。因此即使只查询一张表，同一行也不会返回两次。
- lambdaQL 只 join to-one 关联。集合会变成 `exists` 子查询。因此 join 永远不会让行重复。
- 所以 `distinct` 不会改变结果，只会增加开销。（这与数据库的限制无关：Oracle 23.26 也接受 LOB 列上的 `distinct`。这是实测结果。）

对于值查询（`select`），`distinct()` 和 `toSet()` 使用 `select distinct`（见 [6.7](06-select-group.md#67-distinct-paging-and-results)）。

**大小检查。** 如果 Set 比行数小，`toSet()` 抛出 `IllegalStateException`。以下情况会发生这种问题：

- 实体的 `equals` / `hashCode` 把两个不同的行视为相等
- 它的 `@Id` 不唯一（例如映射到视图的实体）

如果没有这个检查，行会在没有任何迹象的情况下丢失。这些情况请使用 `toList()`。
Kotlin 的 `data class` 只比较主构造函数中的属性。如果 `@Id` 不在其中，两个不同的行可能相等。

```kotlin
// Tag.equals compares only the name: two different rows would become one element, so toSet() refuses
assertThrows<IllegalStateException> { qb.from(Tag::class.java).toSet() }
val tags: List<Tag> = qb.from(Tag::class.java).toList()   // use toList() instead
```

目前还没有针对 `toSet()` 的 Kotlin 测试。用 Java 写的同样的调用在 [`ResultQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ResultQueryTest.java) 中测试过（region `to-set` 和 `to-set-check`，H2、MariaDB 和 Oracle）。请看[本页的 Java 版本](../java/02-getting-started.md#toset)。

## 需要知道的事

| 规则 | 示例 |
|---|---|
| 传入 Lambda 字面量或属性引用。 | `where { it.active }`、`where(Order::active)` |
| 不要先把 Lambda 存到 Kotlin 函数类型中。 | `val f: (Order) -> Boolean = { it.active }; where(f)` 会被拒绝（KC4）。 |
| 不要在 `inline fun` 中创建查询 Lambda。 | 会被拒绝（KC2）。把辅助函数改为非 inline，或从调用方传入 Lambda。 |
| Lambda 必须是一个表达式（用于条件、排序和 select 时）。 | Lambda 内部的循环和局部 `val`/`var` 会被拒绝。 |
| 不要在 Lambda 中放必须执行的代码。Lambda 永远不会被执行。 | 有副作用的调用，例如 `println(...)`，会被拒绝。 |
| 新的 JDK 版本需要匹配的 ASM 版本。 | lambdaQL 使用 ASM 9.11.1（可读取最高到 Java 28 的 class 文件）。 |

所有 Kotlin 限制见 [9. Kotlin 说明](09-kotlin-notes.md#914-kotlin-limits-kc1-to-kc21)。

## 下一步

- [3. 条件（`where`）](03-where.md)：所有运算符和 null 规则。
- [4. 排序与分页](04-order-paging.md)

---

[Kotlin 路线](README.md) | 上一章：[1. 概述](../01-overview.md) | 下一章：[3. 条件](03-where.md) | [本页的 Java 版本](../java/02-getting-started.md)
