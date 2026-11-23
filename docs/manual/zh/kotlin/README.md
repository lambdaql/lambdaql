<a id="lambdaql-manual-kotlin-track"></a>
# lambdaQL 手册：Kotlin 路线

**其他语言：** [English](../../en/kotlin/README.md) | [한국어](../../ko/kotlin/README.md) | [日本語](../../ja/kotlin/README.md) | [简体中文](../../zh/kotlin/README.md)

[手册首页](../README.md) | [Java 路线](../java/README.md) | 公共页面：[1. 概述](../01-overview.md) · [11. 错误与语言](../11-errors-and-languages.md) · [13. Spring Boot 3 与 4](../13-spring-boot-versions.md)

这是 lambdaQL 手册的 **Kotlin 路线**。本路线的每个页面**只包含 Kotlin 代码**。
如果你用 Java 编写查询，请阅读 [Java 路线](../java/README.md)。两条路线的章节编号和文件名相同（只有第 9 章不同），你可以随时切换。

```kotlin
val orders = qb.from(Order::class.java)
    .where { it.status == 1 && it.price!! > minPrice }
    .orderBy { o, by -> by.desc(o.price).asc(o.id) }
    .limit(20)
    .toList()
// HQL: select e0 from Order e0
//      where e0.status = 1 and e0.price > :p1
//      order by e0.price desc nulls last, e0.id asc
```

**关于 SQL 块。** `…` 是缩短的 select 列表（实体的所有列），`?` 是绑定参数。这些行来自 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)。该测试使用 Hibernate 7.4 在 H2 2.3、MariaDB 10.11 和 Oracle 23.26 上运行每个查询，如果 Hibernate 的 SQL 发生变化，测试就会失败。该测试使用 Java Lambda。含义相同的 Kotlin Lambda 生成相同的 HQL，因此 SQL 也相同。

**各数据库的 SQL（Hibernate 为此 HQL 生成的 SQL；Kotlin 生成相同的 HQL）** — 手册中的 `Order` 有可为 NULL 的 `price`，而测试中的 `Order` 没有，所以 `SqlByDatabaseTest` 把这个 HQL 直接交给 Hibernate。来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `readme-first`：

```sql
-- H2
-- (this HQL is given to Hibernate directly)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>?
order by o1_0.price desc,o1_0.id
fetch first ? rows only

-- MariaDB
-- (this HQL is given to Hibernate directly)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>?
order by o1_0.price desc,o1_0.id
limit ?

-- Oracle
-- (this HQL is given to Hibernate directly)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>?
order by o1_0.price desc nulls last,o1_0.id
fetch first ? rows only
```

> [!NOTE]
> **关于示例。** 所有代码示例都是从 Kotlin 测试代码中原样复制的一段。代码上方写有测试文件和片段名称（区域 `name`）。测试会检查 lambdaQL 生成的 HQL 和返回的行。

<a id="contents"></a>
## 目录

| # | 页面 | 你将学到 |
|---|---|---|
| 1 | [概述](../01-overview.md)（公共） | lambdaQL 是什么、为什么需要它、它如何工作 |
| 2 | [入门](02-getting-started.md) | 环境要求、Gradle 配置、Kotlin 实体、第一个查询 |
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

1. 阅读公共的[概述](../01-overview.md)来理解基本思想：Lambda 是**查询**，不是代码。lambdaQL 读取它的字节码，从不执行它。
2. 按照 [2. 入门](02-getting-started.md) 配置你的项目。
3. 在 [3. 条件](03-where.md) 中学习条件。这是最重要的一章。
4. 读一遍 [9. Kotlin 说明](09-kotlin-notes.md)。它解释了在查询中行为不同的 Kotlin 写法（`!!`、`?.`、`?:`、`==`、`===`）。

<a id="example-entities"></a>
## 示例实体

本路线的所有页面都使用以下 Kotlin 实体。完整代码见 [2. 入门](02-getting-started.md#example-entities)。

| 实体 | 属性 |
|---|---|
| `Order` | `id: Long?`、`price: Double?`、`status: Int?`、`active: Boolean`、`description: String?`、`customer: Customer?`、`updateAt: LocalDateTime?`、`items: MutableList<Item>` |
| `Customer` | `id: Long?`、`name: String?`、`grade: Int?` |
| `Item` | `id: Long?`、`order: Order?`、`price: Double?`、`qty: Int` |

大多数属性是可空的 `var` 属性，因为数据库列可能为 NULL。
`active` 和 `qty` 是 Kotlin 的 `Boolean` 和 `Int`。它们是 JVM 基本类型，因此永远不会为 NULL。

<a id="three-things-to-know-first"></a>
## 首先要知道的三件事

| Kotlin | 在 lambdaQL Lambda 中 |
|---|---|
| `it.price!! > 100.0` | `!!` 会被忽略。它只是为了让代码能够编译。不会出现 `NullPointerException`，因为 Lambda 从不执行。 |
| `it.customer?.name == "Kim"` | `it.customer?.name` 指代关联客户的一个列。这个条件不可能与没有客户的订单匹配，因此是内连接 `join`，没有客户的订单不会被返回。当没有客户的订单可能使条件为真时（`it.status == 1 \|\| ...`、`it.customer?.name == null`），`?.` 会成为 `left join`（D54）。`it.customer!!.name` 也表示同一个列，但总是内连接。 |
| `status: Int?` 时的 `it.status == 1` | `e0.status = 1`。`status` 为 NULL 的行不匹配（SQL 语义）。lambdaQL 忽略 Kotlin 编译器添加的 null 检查。只有**你写的** null 检查才算数。 |

详情：[9. Kotlin 说明](09-kotlin-notes.md)。

<a id="supported-environment"></a>
## 支持的环境

| 项目 | 版本 |
|---|---|
| Kotlin | 2.x（设计已用 kotlinc 1.7.22、2.1.10、2.2.21、2.3.0 和 2.3.21 检查） |
| Java（JVM） | 21 |
| Hibernate ORM | 6.6 或 7.4（已用 6.6.53、7.4.13 测试） |
| 设计中已验证的数据库 | H2 2.3、MariaDB 10.11、Oracle 23.26 (23ai) |
| Spring Boot（可选） | 3.5 或 4.1（已用 3.5.16、4.1.2 测试，Java 测试）。见 [13. Spring Boot 3 与 4](../13-spring-boot-versions.md) |

<a id="other-languages"></a>
## 其他语言

每个语言文件夹（`en`、`ko`、`ja`、`zh`）的结构都相同。要阅读某个页面的其他语言版本，请把地址中的 `zh` 改为 `en`、`ko` 或 `ja`。链接在本页顶部。

<a id="help-and-contributions"></a>
## 帮助与贡献

- 源代码：<https://github.com/codebit0/lambdaql>
- 韩文手册是原始版本。其他语言是翻译版本。
- 如果你发现错误，请提交 issue 或 pull request。
- 设计文档（韩文，面向贡献者）：[ROADMAP.md](../../../design/ROADMAP.md)、[KOTLIN.md](../../../design/KOTLIN.md)
