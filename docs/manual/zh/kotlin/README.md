<a id="lambdaql-manual-kotlin-track"></a>
# lambdaQL 手册：Kotlin 路线

**其他语言：** [English](../../en/kotlin/README.md) | [한국어](../../ko/kotlin/README.md) | [日本語](../../ja/kotlin/README.md) | [简体中文](../../zh/kotlin/README.md)

[手册首页](../README.md) | [Java 路线](../java/README.md) | 公共页面：[1. 概述](../01-overview.md) · [11. 错误与语言](../11-errors-and-languages.md) · [13. Spring Boot 3 与 4](../13-spring-boot-versions.md)

这是 lambdaQL 手册的 **Kotlin 路线**。本路线的每个页面**只包含 Kotlin 代码**。
如果你用 Java 编写查询，请阅读 [Java 路线](../java/README.md)。两条路线的章节编号和文件名相同（只有第 9 章不同），可以随时切换。

<!-- region: ManualKotlinStartTest.kt#readme -->
```kotlin
val q = qb.from(Order::class.java)
    .where { it.status.toInt() == 1 && it.price > minPrice }
    .orderBy { o -> desc(o.price).asc(o.id) }
    .limit(20)
assertEquals("select e0 from Order e0 where e0.status = 1 and e0.price > :p1"
    + " order by e0.price desc, e0.id asc", q.toHql().hql())
val orders: List<Order> = q.toList()
```

> [!NOTE]
> **关于示例。** 所有示例代码都是从可运行的 Kotlin 代码中原样复制的一段。节末的"示例代码："一行写明源文件和片段名称。`assertEquals` 中的字符串就是 lambdaQL 生成的 HQL。在各数据库的 SQL 块中，`…` 是缩写的 select 列表（实体的所有列），`?` 是绑定参数。

示例代码：[`ManualKotlinStartTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinStartTest.kt)（`readme`）。

<a id="contents"></a>
## 目录

| # | 页面 | 你将学到 |
|---|---|---|
| 1 | [概述](../01-overview.md)（公共） | lambdaQL 是什么、为什么需要它、它如何工作 |
| 2 | [入门](02-getting-started.md) | 环境要求、Gradle 配置、示例实体、第一个查询、结果函数 |
| 3 | [条件（`where`）](03-where.md) | 运算符、null 规则、`?.` `?:` `!!`、区间、`when`、捕获的值 |
| 4 | [排序与分页](04-order-paging.md) | `orderBy`、`Direction`、`limit`、`offset` |
| 5 | [子查询](05-subquery.md) | `exists()`、`x in sub...`、标量子查询和关联子查询、子查询中的 join |
| 6 | [查询列、聚合与分组](06-select-group.md) | `select`、data class、`Pair`、`groupBy`、`having`、`Agg`、join、派生表、CTE |
| 7 | [更新、插入、删除](07-dml.md) | 属性赋值、`Dml.value`、`values`、`all()`、`execute()`、`limit`、`executeOne()` |
| 7.6 | [Upsert](07-6-upsert.md) | `upsert`、`onConflict { ... }.update { ... }`、`onConflict { ... }.ignore()`、各数据库的行数 |
| 8 | [存储过程与数据库函数](08-procedures-functions.md) | `@SqlProcedure`、`companion object` 中的 `@SqlFunction`、`qb.call`、`qb.exec` |
| 9 | [Kotlin 说明](09-kotlin-notes.md) | Kotlin 的特别之处：`?.` `?:` `!!`、`==`、`in`、`when`、运算符、限制 KC1 到 KC21 |
| 10 | [规则与限制](10-rules-and-limits.md) | 所有规则表集中在一处 |
| 11 | [错误与语言](../11-errors-and-languages.md)（公共） | 错误码、异常、消息语言、日志 |
| 12 | [Spring Data JPA](12-spring-data-jpa.md) | `LambdaQlExecutor<T>`、带方法体的仓库方法、DML 快捷方法（`deleteAll`、`updateOne` 等）、Spring `Sort` 和 `Pageable`、模块、事务、Kotlin 2.2 / `-Xjvm-default=all` |
| 13 | [Spring Boot 3 与 4](../13-spring-boot-versions.md)（公共） | 版本表、构件（公共 jar 与 `-boot3`、`-boot4` starter）、Hibernate 6.6 与 7.4 的 SQL 差异、验证范围 |

<a id="where-to-start"></a>
## 从哪里开始

1. 阅读公共的[概述](../01-overview.md)，理解基本思想。Lambda 表达式不是代码，而是**查询**。lambdaQL 不调用 Lambda 表达式，而是分析其字节码 opcode 来生成 HQL。
2. 按照 [2. 入门](02-getting-started.md) 配置项目。
3. 在 [3. 条件](03-where.md) 中学习条件。这是最重要的一章。
4. 读一遍 [9. Kotlin 说明](09-kotlin-notes.md)。它说明在查询中行为不同的 Kotlin 写法（`!!`、`?.`、`?:`、`==`、`===`）。

<a id="example-entities"></a>
## 示例实体

本路线的示例使用以 Java 类编写的示例实体（`query/src/test/java/org/lambdaql/query/`）。详情见 [2. 入门](02-getting-started.md#example-entities)。

| 实体 | 主要属性（Java 类型） |
|---|---|
| `Order` | `Long id`、`double price`、`short status`、`boolean active`（在 Kotlin 中为 `isActive`）、`String description`、`Integer cnt`、`String custName`、`LocalDateTime updateAt`、`Customer customer`、`Address address` |
| `Customer` | `int id`、`String name`、`Integer grade` |
| `Item` | `Long id`、`Order order`、`Double price`、`int qty` |

`price`、`status`、`active`、`qty` 是 Java 基本类型，因此不可能为 NULL。
引用类型的属性可能为 NULL，在 Kotlin 中表现为平台类型。

<a id="three-things-to-know-first"></a>
## 首先要知道的三件事

| Kotlin | 在 lambdaQL Lambda 表达式中 |
|---|---|
| `it.price!! > 100.0` | `!!` 会被忽略，它只是为了让代码能够编译。lambdaQL 不调用 Lambda 表达式，而是分析字节码 opcode，因此不会发生 `NullPointerException`。 |
| `it.customer?.name == "Kim"` | `it.customer?.name` 指关联客户的列。这个条件不可能与没有客户的订单匹配，因此是内连接 `join`，没有客户的订单不会被返回。当没有客户的订单可能满足条件时（`it.status == 1 \|\| ...`、`it.customer?.name == null`），会成为 `left join`。`it.customer!!.name` 也是同一个列，遵循同样的规则。不过，经过 `!!` 的比较只在有客户的订单上为真。 |
| `n: Int?` 时的 `it.cnt == n` | `e0.cnt = :p1`。`cnt` 为 NULL 的行不匹配（SQL 语义）。要查找 NULL 行，请像 `it.cnt == null` 这样**自己编写** null 检查。 |

详情：[9. Kotlin 说明](09-kotlin-notes.md)。

<a id="supported-environment"></a>
## 支持的环境

| 项目 | 版本 |
|---|---|
| Kotlin | 2.x（设计已用 kotlinc 1.7.22、2.1.10、2.2.21、2.3.0、2.3.21 确认） |
| Java（JVM） | 21 及以上 |
| Hibernate ORM | 6.6 或 7.4（已用 6.6.53、7.4.13 测试） |
| 设计中已验证的数据库 | H2 2.3、MariaDB 10.11、Oracle 23.26 (23ai) |
| Spring Boot（可选） | 3.5 或 4.1（已用 3.5.16、4.1.2 测试）。见 [13. Spring Boot 3 与 4](../13-spring-boot-versions.md) |

<a id="other-languages"></a>
## 其他语言

所有语言文件夹（`en`、`ko`、`ja`、`zh`）的结构都相同。要阅读某个页面的其他语言版本，请把地址中的 `zh` 改为 `en`、`ko` 或 `ja`。链接在本页顶部。

<a id="help-and-contributions"></a>
## 帮助与贡献

- 源代码：<https://github.com/lambdaql/lambdaql>
- 韩文手册是原始版本，其他语言是翻译版本。
- 如果发现错误，请提交 issue 或 pull request。
- 设计文档（韩文，面向贡献者）：[ROADMAP.md](../../../design/ROADMAP.md)、[KOTLIN.md](../../../design/KOTLIN.md)
