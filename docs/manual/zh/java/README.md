<a id="lambdaql-manual-java-track"></a>
# lambdaQL 手册：Java 路线

**其他语言：** [English](../../en/java/README.md) | [한국어](../../ko/java/README.md) | [日本語](../../ja/java/README.md) | [简体中文](../../zh/java/README.md)

[手册首页](../README.md) | [Kotlin 路线](../kotlin/README.md) | 公共页面：[1. 概述](../01-overview.md) · [11. 错误与语言](../11-errors-and-languages.md) · [13. Spring Boot 3 与 4](../13-spring-boot-versions.md)

这是 lambdaQL 手册的 **Java 路线**。本路线的所有页面**只包含 Java 代码**。
用 Kotlin 编写查询时，请阅读 [Kotlin 路线](../kotlin/README.md)。它由编号相同的相同章节组成。

lambdaQL 是运行在 JPA 和 Hibernate 之上、面向 Java 和 Kotlin 的查询库。
用 Lambda 表达式编写查询的条件和排序后，lambdaQL 分析 Lambda 表达式的字节码 opcode 并生成 HQL 查询。

<!-- region: ManualJavaStartTest.java#readme -->
```java
import static org.lambdaql.query.By.desc;

double minPrice = 100;
OrderedSelectQuery<Order> query = qb.from(Order.class)
        .where(o -> o.getStatus() == 1 && o.getPrice() > minPrice)
        .orderBy(o -> desc(o.getPrice()).asc(o.getId()))
        .limit(20);
assertEquals("select e0 from Order e0 where e0.status = 1 and e0.price > :p1"
        + " order by e0.price desc, e0.id asc", query.toHql().hql());
List<Order> orders = query.toList();
```

`toHql()` 不执行查询，只返回 HQL。`assertEquals` 中的字符串就是 lambdaQL 生成的 HQL。
`price` 和 `id` 是不可为 NULL 的列，所以排序中没有 `nulls` 部分。
`desc` 和 `asc` 是类 `By` 的排序函数（见 [4.1](04-order-paging.md#41-one-lambda-for-the-whole-sort)）。

> [!NOTE]
> **关于示例。**
> 所有示例代码都原样取自仓库中可执行的 Java 代码。
> 各节末尾的"示例代码："一行链接到源文件。
> 在各数据库的 SQL 块中，`…` 是缩写的 select 列表（实体的所有列），`?` 是绑定参数。

示例代码：[`ManualJavaStartTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaStartTest.java)（`readme`）。

<a id="contents"></a>
## 目录

标记为"公共"的页面由 Java 路线和 Kotlin 路线共用。两种语言的小示例按小节分开。

| # | 页面 | 学习内容 |
|---|---|---|
| 1 | [概述](../01-overview.md)（公共） | lambdaQL 是什么、为什么需要它、它如何工作 |
| 2 | [入门](02-getting-started.md) | 环境要求、从源码构建、实体、`QueryBuilder`、第一个查询 |
| 3 | [条件（`where`）](03-where.md) | 运算符、NULL 规则、捕获的值、关联路径 |
| 4 | [排序与分页](04-order-paging.md) | `orderBy`、`Direction`、NULL 顺序、`limit`、`offset` |
| 5 | [子查询](05-subquery.md) | `exists`、`contains`（IN）、NOT EXISTS、标量子查询和关联子查询 |
| 6 | [查询、聚合与分组](06-select-group.md) | `select`、record 和 DTO、`Agg`、`groupBy`、`having`、join（`join`、`leftJoin`、`crossJoin`）、派生表、CTE |
| 7 | [更新、插入、删除](07-dml.md) | `set`、`Dml.value`、`values`、`all()`、`execute()`、`limit`、`executeOne()` |
| 7.6 | [Upsert](07-6-upsert.md) | `upsert`、`onConflict(...).update(...)`、`onConflict(...).ignore()`、各数据库的行数 |
| 8 | [存储过程与数据库函数](08-procedures-functions.md) | `@SqlProcedure`、`@SqlFunction`、`qb.call`、`qb.exec` |
| 9 | [Java 说明](09-java-notes.md) | 装箱、对象上的 `==`、`String` 比较、`Objects.equals`、方法引用、`this` 捕获、编译器说明 |
| 10 | [规则与限制](10-rules-and-limits.md) | 完整的规则表、Java 与 SQL 的差异、各数据库的差异、不支持的内容 |
| 11 | [错误与语言](../11-errors-and-languages.md)（公共） | 错误码、异常、消息语言、日志 |
| 12 | [Spring Data JPA](12-spring-data-jpa.md) | `LambdaQlExecutor<T>`、repository default 方法、DML 快捷方法（`deleteAll`、`updateOne` 等）、Spring `Sort` 和 `Pageable`、模块、事务、`QueryBuilder` bean |
| 13 | [Spring Boot 3 与 4](../13-spring-boot-versions.md)（公共） | 版本表、构件（公共 jar 与 `-boot3`、`-boot4` starter）、Hibernate 6.6 与 7.4 的 SQL 差异、验证范围 |

<a id="example-entities"></a>
## 示例实体

示例主要使用以下三个示例实体。属性的类型和源文件见[入门](02-getting-started.md#example-entities)。

| 实体 | 字段 |
|---|---|
| `Order` | `id`、`price`、`status`、`active`、`description`、`kind`、`customer`（指向 `Customer`）、`updateAt`、`total`、`cnt`、`custName`、`address` |
| `Customer` | `id`、`name`、`grade`、`orders`（指向多个 `Order`） |
| `Item` | `id`、`order`（指向 `Order`）、`price`、`qty` |

<a id="supported-environment"></a>
## 支持的环境

| 项目 | 版本 |
|---|---|
| Java | 21 及以上 |
| 编译器 | javac（不保证支持 Eclipse 编译器 ECJ） |
| Hibernate ORM | 6.6 或 7.4（已用 6.6.53、7.4.13 测试） |
| 已验证的数据库 | H2 2.3、MariaDB 10.11、MySQL 8.4、PostgreSQL 17、Oracle 23.26 (23ai) |
| Spring Boot（可选） | 3.5 或 4.1（已用 3.5.16、4.1.2 测试）。见 [13. Spring Boot 3 与 4](../13-spring-boot-versions.md) |

<a id="help-and-contributions"></a>
## 帮助与贡献

- 源代码：<https://github.com/lambdaql/lambdaql>
- 韩文手册是原始版本。英文、日文和中文手册是翻译版本。
- 发现错误时，请提交 issue 或 pull request。
- 设计文档（韩文，面向贡献者）：[ROADMAP.md](../../../design/ROADMAP.md)、[KOTLIN.md](../../../design/KOTLIN.md)
