# 12. Spring Data JPA（Java）

> [!NOTE]
> **状态：已设计，尚未实现。**
> 本章介绍计划中的模块 `lambdaql-spring-data-jpa`。
> 其装配方式（repository fragment、事务）已在 Spring Boot 3.4.2 和 Spring Data JPA 3.4.2 上用原型检查过。
> 目前只有分析核心以及 `where` + `toList()` 可以工作（已在 H2、MariaDB 和 Oracle 上测试）。
> 完整状态请参见 [Java 路线 README](README.md#status)。

[Java 路线](README.md) | [手册首页](../README.md) | 上一章：[11. 错误与语言](../11-errors-and-languages.md) | [本页的 Kotlin 版本](../kotlin/12-spring-data-jpa.md)

---

## 12.1 你将得到什么

使用 Spring Data JPA 时，你编写 repository 接口。lambdaQL 额外提供一个接口 `LambdaQlExecutor<T>`。
你的 repository 继承它，然后把自己的查询方法写成内部使用 lambdaQL 的**默认方法**。
这些方法不需要 `...RepositoryImpl` 类。

```java
public interface OrderRepository extends JpaRepository<Order, Long>, LambdaQlExecutor<Order> {

    default List<Order> active(int status) {
        return findAll(o -> o.getStatus() == status && o.isActive());
    }

    default List<Order> recent() {
        return query()
            .where(o -> o.isActive())
            .orderBy((o, by) -> by.desc(o.getUpdateAt()))
            .limit(20)
            .toList();
    }
}
```

```java
@Service
public class OrderService {
    private final OrderRepository orders;

    public OrderService(OrderRepository orders) {
        this.orders = orders;
    }

    @Transactional(readOnly = true)
    public List<Order> activeOrders() {
        return orders.active(1);
    }
}
```

## 12.2 模块

lambdaQL 有两个构件。核心模块不依赖 Spring，因此也可以和普通 JPA 一起使用。

| 构件 | 包含内容 | 何时需要 |
|---|---|---|
| `lambdaql-core` | 引擎、`QueryBuilder`、查询、DML、存储过程 | 始终需要 |
| `lambdaql-spring-data-jpa` | `LambdaQlExecutor<T>`、它在 Spring 中的注册、Spring Boot 自动配置（`QueryBuilder` bean） | 使用 Spring Data JPA 或 Spring Boot 时 |

```groovy
// build.gradle（计划中的坐标；目前尚未发布任何内容）
dependencies {
    implementation 'org.lambdaql:lambdaql-spring-data-jpa:0.1.0'   // 同时引入 lambdaql-core
}
```

环境要求：

| 项目 | 版本 |
|---|---|
| Spring Boot | 3.4 或更高版本，以及 Spring Boot 4 |
| Spring Data JPA | 3.4 或更高版本 |
| Hibernate | 6.6 或更高版本（Spring Boot 已经包含） |

**不支持** Spring Data JPA 3.3 及更早版本。lambdaQL 使用的注册方式（见 12.6）从 3.4 开始才有。

## 12.3 `LambdaQlExecutor<T>`

`T` 是该 repository 的实体。

| 方法 | 作用 | 等同于 |
|---|---|---|
| `query()` | 对该 repository 的实体开始一个查询 | `qb.from(Order.class)` |
| `from(Class<E>)` | 对另一个实体开始一个查询 | `qb.from(Customer.class)` |
| `findAll(Where<T>)` | 所有匹配的行 | `query().where(w).toList()` |
| `findOne(Where<T>)` | 一行或空（`Optional`） | |
| `count(Where<T>)` | 匹配的行数 | |
| `exists(Where<T>)` | 至少有一行匹配时为 `true` | |
| `updateQuery()` | 对该实体开始一个更新 | `qb.update(Order.class)` |
| `deleteQuery()` | 对该实体开始一个删除 | `qb.delete(Order.class)` |
| `insertQuery()` | 对该实体开始一个插入 | `qb.insert(Order.class)` |

DML 方法以 `Query` 结尾，是因为 `JpaRepository` 已经有 `delete(T)` 等类似的方法。
`findAll(Where<T>)` 和 `count(Where<T>)` 与 `findAll(Example)` 或 `count()` 不冲突，因为参数类型不同。

本手册的所有章节在 repository 中都适用。一些示例：

```java
public interface OrderRepository extends JpaRepository<Order, Long>, LambdaQlExecutor<Order> {

    // 3. 条件、捕获值
    default Optional<Order> findPaid(long id) {
        return findOne(o -> o.getId() == id && o.getStatus() == 2);
    }

    // 5. 子查询
    default List<Order> withBigItems() {
        return query()
            .where((o, sub) -> sub.from(Item.class)
                                  .where(i -> i.getOrder().getId() == o.getId() && i.getQty() > 10)
                                  .exists())
            .toList();
    }

    // 6. 查询到 record 中
    default List<OrderView> views() {
        return query().select(o -> new OrderView(o.getId(), o.getCustomer().getName(), o.getPrice())).toList();
    }

    // 7. 更新（需要事务，见 12.5）
    default int closeOld(LocalDateTime before) {
        return updateQuery()
            .set(o -> o.setStatus(9))
            .where(o -> o.getUpdateAt().isBefore(before))
            .execute();
    }
}
```

## 12.4 工作原理

- **默认方法由 Spring Data 自身执行。** Spring Data 不会尝试从默认方法生成查询，它只是调用方法主体。因此 lambdaQL 不需要改变 Spring Data 处理默认方法的方式。
- **`LambdaQlExecutor<T>` 是一个 Spring Data "repository fragment"。** lambdaQL 在 `META-INF/spring.factories` 中注册其实现。Spring Data 3.4 及更高版本会读取该文件，并把实现添加到每个继承了 `LambdaQlExecutor<T>` 的 repository 中。你不需要编写任何配置。
- **实体类型**来自 Lambda（对于 `findAll(o -> ...)`）或来自 repository（对于 `query()`）。

当你的 repository 已经有自己的自定义实现时，它同样可以工作：

```java
public interface OrderRepository extends JpaRepository<Order, Long>,
                                         LambdaQlExecutor<Order>,
                                         OrderRepositoryCustom {          // 你自己的 fragment
}
class OrderRepositoryCustomImpl implements OrderRepositoryCustom { ... }  // 仍然可用
```

## 12.5 事务

lambdaQL 使用 Spring 为当前事务提供的 `EntityManager`。

| 情况 | 结果 |
|---|---|
| 在 `@Transactional` 内部 | lambdaQL 能看到尚未 flush 的更改。回滚和 `readOnly` 照常工作。 |
| 在事务外部调用查询方法 | 它们在各自的只读事务中运行。 |
| 在事务外部调用 `execute()`（更新、插入、删除） | 报错。请在调用它的服务方法上加上 `@Transactional`。 |

```java
@Transactional
public int closeOldOrders() {
    return orders.closeOld(LocalDateTime.now().minusYears(1));
}
```

批量 DML 不会更新同一事务中已经加载的实体。参见 [7. 更新、插入、删除](07-dml.md)。

## 12.6 规则

| # | 规则 |
|---|---|
| 1 | 你的 repository 必须**直接**继承 `LambdaQlExecutor<T>`（写在它自己的 `extends` 列表中）。 |
| 2 | 不要在为此目的继承 `LambdaQlExecutor<T>` 的接口上加 `@NoRepositoryBean`。否则 Spring Data 不会添加 lambdaQL 的实现。 |
| 3 | 把 lambdaQL 查询写在**默认方法**或服务中。抽象方法仍然是 Spring Data 的查询方法（`findByName` 等）。 |
| 4 | 不使用 Spring Boot 时，repository 部分仍然可用（它就是普通的 Spring Data）。只有 `QueryBuilder` bean 需要你自己创建（见 [2. 入门](02-getting-started.md)）。 |
| 5 | 在使用 `@DataJpaTest` 的测试中，请导入 lambdaQL 的自动配置（模块发布时会在这里列出确切的类名）。 |

## 12.7 旧的 `DefaultMethodSupportJpaRepositoryFactoryBean`

旧版 lambdaQL 代码中有 `org.lambdaql.jpa.DefaultMethodSupportJpaRepositoryFactoryBean`。它的目的是让默认方法取代 `...Impl` 类。
在 Spring Data JPA 3.4 中，Java 默认方法无需它也能工作（见 12.4）。
这个类**正在评估中**。它将移到 `lambdaql-spring-data-jpa`；具体细节会在发布前确定。

---

[Java 路线](README.md) | 上一章：[11. 错误与语言](../11-errors-and-languages.md) | [本页的 Kotlin 版本](../kotlin/12-spring-data-jpa.md)
