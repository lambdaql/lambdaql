# 2. 入门（Kotlin）

> [!NOTE]
> **状态：** 目前你可以从源码构建 lambdaQL 并运行它的测试。
> `where` 条件和 `toList()` 可以端到端运行。它们已在 H2、MariaDB 和 Oracle 上用 **Java** Lambda 测试。Kotlin Lambda 尚未测试。
> 排序与分页的核心部分（`orderBy`、`limit`、`offset`）也可以运行（同样只用 Java Lambda 测试过）。
> 子查询、`select`、update/insert/delete、存储过程和 Spring 模块**已设计，尚未实现**。
> 目前还没有发布任何构件。下面的构件名称是临时的。

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
./gradlew :query:build
```

- Gradle wrapper（8.10）只能在 **JDK 23 或更低版本**上运行。请使用 JDK 21。
- 仓库中有 `gradle/gradle-daemon-jvm.properties`，因此 Gradle 守护进程会使用 Java 21。
- 在 IntelliJ IDEA 中，把 **Settings > Build Tools > Gradle > Gradle JVM** 设置为 Java 21 JDK。

运行单元测试：

```bash
./gradlew :query:test
```

[`SelectQueryTest.java`](../../../../query/src/test/java/org/lambdaql/query/SelectQueryTest.java) 中的测试展示了 lambdaQL 目前能做什么：`qb.from(...).where(...)`、HQL、参数，以及在 H2、MariaDB 和 Oracle 上返回的行。[`ConditionDecodeTest.java`](../../../../query/src/test/java/org/lambdaql/engine/decode/ConditionDecodeTest.java) 测试内部的条件解码器。这些测试是用 Java 编写的。Kotlin 测试还在计划中（`query/src/test/kotlin`）。

### 在你的项目中使用本地构建

把 jar 发布到本地 Maven 仓库：

```bash
./gradlew :query:publishToMavenLocal
```

然后把它添加到你的项目中（Gradle Kotlin DSL）：

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("org.lambdaql:lambadaql-query:1.0.0")
}
```

Maven：

```xml
<dependency>
  <groupId>org.lambdaql</groupId>
  <artifactId>lambadaql-query</artifactId>
  <version>1.0.0</version>
</dependency>
```

> [!WARNING]
> 这些坐标是**临时的**。
> 名称 `lambadaql` 有一个拼写错误，会在首次发布前修正。
> group、名称和版本都可能改变。lambdaQL 发布后请再次查看本页。

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

`QueryBuilder` 是入口。它需要一个 JPA `EntityManagerFactory`。

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

Spring Boot 已经创建了 `EntityManagerFactory`。注入它并创建一个 `QueryBuilder` bean。

```kotlin
@Configuration
class LambdaQlConfig {

    @Bean
    fun queryBuilder(entityManagerFactory: EntityManagerFactory) = QueryBuilder(entityManagerFactory)
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

- 在 `lambdaql-spring-data-jpa` 模块发布之前，你需要自己创建 bean。该模块将为你创建它。
- 在 Spring 事务中，lambdaQL 使用该事务共享的 `EntityManager`。
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
- `toList()` 是目前唯一已确定的结果方法。其他结果方法还在计划中。

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
