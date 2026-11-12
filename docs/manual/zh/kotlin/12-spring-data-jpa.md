# 12. Spring Data JPA（Kotlin）

> [!NOTE]
> **状态：已设计，尚未实现。**
> 本章介绍计划中的模块 `lambdaql-spring-data-jpa`。
> 其装配方式（repository 片段、事务、Kotlin 接口方法）已用原型在 Spring Boot 3.4.2、Spring Data JPA 3.4.2 和 kotlinc 2.3.21 上检查过。
> 目前只有分析核心以及 `where` + `toList()` 可以工作（已在 H2、MariaDB 和 Oracle 上测试）。
> 完整状态请参阅 [Kotlin 路线 README](README.md)。

[Kotlin 路线](README.md) | [手册首页](../README.md) | 上一章：[11. 错误与语言](../11-errors-and-languages.md) | [本页的 Java 版本](../java/12-spring-data-jpa.md)

---

## 12.1 你能得到什么

使用 Spring Data JPA 时，你编写 repository 接口。lambdaQL 再增加一个接口：`LambdaQlExecutor<T>`。
你的 repository 继承它，然后你把自己的查询方法写成**带主体的接口方法**，在其中使用 lambdaQL。
这些方法不需要 `...RepositoryImpl` 类。

```kotlin
interface OrderRepository : JpaRepository<Order, Long>, LambdaQlExecutor<Order> {

    fun active(status: Int): List<Order> =
        findAll { it.status == status && it.active }

    fun recent(): List<Order> =
        query()
            .where { it.active }
            .orderBy { o, by -> by.desc(o.updateAt) }
            .limit(20)
            .toList()
}
```

```kotlin
@Service
class OrderService(private val orders: OrderRepository) {

    @Transactional(readOnly = true)
    fun activeOrders(): List<Order> = orders.active(1)
}
```

## 12.2 模块

lambdaQL 有两个构件。核心不依赖 Spring，所以你也可以在纯 JPA 中使用它。

| 构件 | 包含内容 | 何时需要 |
|---|---|---|
| `lambdaql-core` | 引擎、`QueryBuilder`、select、DML、存储过程 | 总是需要 |
| `lambdaql-spring-data-jpa` | `LambdaQlExecutor<T>`、它的 Spring 注册、Spring Boot 自动配置（`QueryBuilder` bean） | 你使用 Spring Data JPA 或 Spring Boot 时 |

```kotlin
// build.gradle.kts（计划中的坐标；目前还没有发布任何构件）
dependencies {
    implementation("org.lambdaql:lambdaql-spring-data-jpa:0.1.0")   // 同时引入 lambdaql-core
    implementation("org.jetbrains.kotlin:kotlin-reflect")           // Spring Data 在 Kotlin 中需要它
}
```

环境要求：

| 项目 | 版本 |
|---|---|
| Spring Boot | 3.4 或更高版本，以及 Spring Boot 4 |
| Spring Data JPA | 3.4 或更高版本 |
| Hibernate | 6.6 或更高版本（Spring Boot 已经自带） |
| Kotlin | 2.2 或更高版本。较旧的 Kotlin 请参阅 12.6 |
| `kotlin-reflect` | Spring Data 在 Kotlin 中需要它 |

**不支持** Spring Data JPA 3.3 及更早版本。lambdaQL 使用的注册方式（参见 12.6）从 3.4 开始才有。

## 12.3 `LambdaQlExecutor<T>`

`T` 是该 repository 的实体。

| 方法 | 作用 | 等同于 |
|---|---|---|
| `query()` | 对该 repository 的实体开始一个查询 | `qb.from(Order::class.java)` |
| `from(Class<E>)` | 对另一个实体开始一个查询 | `qb.from(Customer::class.java)` |
| `findAll(Where<T>)` | 所有匹配的行 | `query().where(w).toList()` |
| `findOne(Where<T>)` | 一行或空（`Optional`） | |
| `count(Where<T>)` | 匹配的行数 | |
| `exists(Where<T>)` | 至少有一行匹配时为 `true` | |
| `updateQuery()` | 对该实体开始一个更新 | `qb.update(Order::class.java)` |
| `deleteQuery()` | 对该实体开始一个删除 | `qb.delete(Order::class.java)` |
| `insertQuery()` | 对该实体开始一个插入 | `qb.insert(Order::class.java)` |

DML 方法以 `Query` 结尾，因为 `JpaRepository` 已经有 `delete(T)` 等类似的方法。
`findAll { ... }` 和 `count { ... }` 不会与 `findAll(Example)` 或 `count()` 冲突，因为参数类型不同。

本手册的所有章节都可以在 repository 中使用。一些示例：

```kotlin
interface OrderRepository : JpaRepository<Order, Long>, LambdaQlExecutor<Order> {

    // 3. 条件、捕获值
    fun findPaid(id: Long): Optional<Order> =
        findOne { it.id == id && it.status == 2 }

    // 5. 子查询
    fun withBigItems(): List<Order> =
        query()
            .where { o, sub -> sub.from(Item::class.java)
                                  .where { i -> i.order?.id == o.id && i.qty > 10 }
                                  .exists() }
            .toList()

    // 6. 查询到 data class
    fun views(): List<OrderView> =
        query().select { OrderView(it.id, it.customer?.name, it.price) }.toList()

    // 7. 更新（需要事务，参见 12.5）
    fun closeOld(before: LocalDateTime): Int =
        updateQuery()
            .set { it.status = 9 }
            .where { it.updateAt!! < before }
            .execute()
}
```

## 12.4 工作原理

- **带主体的接口方法由 Spring Data 自己执行。** Spring Data 不会尝试从它们生成查询，只是调用方法主体。所以 lambdaQL 不需要改变 Spring Data 处理它们的方式。
- **`LambdaQlExecutor<T>` 是一个 Spring Data 的"repository 片段"（fragment）。** lambdaQL 在 `META-INF/spring.factories` 中注册其实现。Spring Data 3.4 及以上版本会读取这个文件，并把实现添加到每个继承 `LambdaQlExecutor<T>` 的 repository 中。你不需要编写任何配置。
- **实体类型**来自 Lambda（对于 `findAll { ... }`）或来自 repository（对于 `query()`）。

当你的 repository 已经有自己的自定义实现时，它也能工作：

```kotlin
interface OrderRepository : JpaRepository<Order, Long>,
                            LambdaQlExecutor<Order>,
                            OrderRepositoryCustom          // 你自己的片段

class OrderRepositoryCustomImpl : OrderRepositoryCustom { /* ... */ }   // 仍然可以工作
```

## 12.5 事务

lambdaQL 使用 Spring 为当前事务提供的 `EntityManager`。

| 情况 | 结果 |
|---|---|
| 在 `@Transactional` 中 | lambdaQL 能看到尚未刷新（flush）的变更。回滚和 `readOnly` 照常工作。 |
| 在事务之外调用查询方法 | 它们在各自的只读事务中运行。 |
| 在事务之外调用 `execute()`（更新、插入、删除） | 报错。请在调用它的 service 方法上加 `@Transactional`。 |

```kotlin
@Transactional
fun closeOldOrders(): Int = orders.closeOld(LocalDateTime.now().minusYears(1))
```

批量 DML 不会更新同一事务中已经加载的实体。参见 [7. 更新、插入、删除](07-dml.md)。

## 12.6 规则

| # | 规则 |
|---|---|
| 1 | 你的 repository 必须**直接**继承 `LambdaQlExecutor<T>`（写在它自己的父类型列表中）。 |
| 2 | 不要在为此目的继承 `LambdaQlExecutor<T>` 的接口上加 `@NoRepositoryBean`。否则 Spring Data 不会添加 lambdaQL 的实现。 |
| 3 | 把 lambdaQL 查询写在**带主体的接口方法**或 service 中。没有主体的方法仍然是 Spring Data 的查询方法（`findByName` 等）。 |
| 4 | **Kotlin 2.2 或更高版本：**带主体的方法会成为 Java 默认方法，可以正常工作。**低于 2.2 的 Kotlin：**请添加编译器选项 `-Xjvm-default=all`。否则该方法在接口中没有主体，Spring Data 会尝试根据方法名生成查询，应用程序无法启动（`No property ... found for type ...`）。 |
| 5 | 添加 `kotlin-reflect`。否则 Spring Data 会因 `ClassNotFoundException: kotlin.reflect.jvm.ReflectJvmMapping` 而失败。 |
| 6 | 不使用 Spring Boot 时，repository 部分仍然可以工作（它就是普通的 Spring Data）。只是 `QueryBuilder` bean 需要你自己创建（参见 [2. 入门](02-getting-started.md)）。 |
| 7 | 在使用 `@DataJpaTest` 的测试中，请导入 lambdaQL 的自动配置（模块发布时会在这里列出确切的类名）。 |

```kotlin
// build.gradle.kts，仅用于低于 2.2 的 Kotlin
kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}
```

## 12.7 旧的 `DefaultMethodSupportJpaRepositoryFactoryBean`

旧的 lambdaQL 代码中有 `org.lambdaql.jpa.DefaultMethodSupportJpaRepositoryFactoryBean`。它的目的是让带主体的接口方法取代 `...Impl` 类。
在 Spring Data JPA 3.4 和 Kotlin 2.2 中，即使没有它，这些方法也已经可以工作（参见 12.4）。
这个类**正在评估中**：它可能会成为在不使用 `-Xjvm-default=all` 的情况下支持低于 2.2 的 Kotlin 的方法。它将移到 `lambdaql-spring-data-jpa` 中；具体细节会在发布前确定。

---

[Kotlin 路线](README.md) | 上一章：[11. 错误与语言](../11-errors-and-languages.md) | [本页的 Java 版本](../java/12-spring-data-jpa.md)
