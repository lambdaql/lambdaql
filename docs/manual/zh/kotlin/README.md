# lambdaQL 手册：Kotlin 路线

**其他语言：** [English](../../en/kotlin/README.md) | [한국어](../../ko/kotlin/README.md) | [日本語](../../ja/kotlin/README.md) | [简体中文](../../zh/kotlin/README.md)

> [!IMPORTANT]
> **状态：** lambdaQL 处于早期开发阶段。
> 已实现**分析核心**、**带结果函数的 `where`**（`toList()`、`toSet()`、`first()`、`one()`、`count()`、`exists()`）以及**排序与分页**的核心部分（`orderBy`、`limit`、`offset`、`SortSpec`）：lambdaQL 加载 Lambda 表达式，解码它，生成 HQL 并执行它。这已在 H2、MariaDB 和 Oracle 上测试，大多使用 **Java** Lambda。
> **Kotlin Lambda 已在构建中测试**（kotlinc 2.3.21，[`KotlinSelectTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinSelectTest.kt)、[`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)）：`?.` 路径、`!!`、捕获值、`in`、排序、子查询、使用 data class、`Pair` 和 `Triple` 的 `select`、`groupBy` 和 `having`、update、insert 和 delete。其他 Kotlin 特有的写法（`?:`、区间、`when`、属性引用）处于**已设计**状态：它们的字节码已经编译，并用原型检查过（kotlinc 1.7.22 到 2.3.21）。
> **子查询**、**`select`**（带 `groupBy`、`having` 和 `Agg`）、**更新、插入、删除和 upsert**（包括 `limit` 和单行保护 `executeOne()`）、**显式 join**、**派生表和 CTE**、**存储过程与数据库函数**以及**四种语言的错误码**都已实现。join、派生表、CTE、存储过程、数据库函数和 upsert 只用 Java Lambda 测试过。
> **Spring Data JPA 模块**（`LambdaQlExecutor<T>`、Spring `Sort` 和 `Pageable`、`deleteAll { }` 和 `updateOne({ }) { }` 等 DML 快捷方法、更新、删除、插入和 upsert 构建器、Spring Boot 自动配置）已实现，并已用 Java repository 测试（Spring Boot 3.4.2）。DML 需要调用方的事务。Kotlin repository 尚未测试。
> 目前还没有任何构件发布到 Maven Central。如果想试用，请[从源码构建](02-getting-started.md#build-from-source)。

[手册首页](../README.md) | [Java 路线](../java/README.md) | 公共页面：[1. 概述](../01-overview.md) · [11. 错误与语言](../11-errors-and-languages.md)

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

**关于 SQL 块。** `…` 是缩短的 select 列表（实体的所有列），`?` 是绑定参数。这些行来自 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)。该测试使用 Hibernate 6.6.5 在 H2 2.3、MariaDB 10.11 和 Oracle 23.26 上运行每个查询，如果 Hibernate 的 SQL 发生变化，测试就会失败。 该测试使用 Java Lambda。含义相同的 Kotlin Lambda 生成相同的 HQL，因此 SQL 也相同。

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
> **关于示例。** 示例均为设计示例。某个功能实现后，其示例将替换为取自测试套件的代码，并附上测试文件的链接。取自 Kotlin 测试的示例标记为 **(已测试)**（第 3 至 7 章）。

## 目录

| # | 页面 | 你将学到 | 状态 |
|---|---|---|---|
| 1 | [概述](../01-overview.md)（公共） | lambdaQL 是什么、为什么需要它、它如何工作 | – |
| 2 | [入门](02-getting-started.md) | 环境要求、Gradle 配置、Kotlin 实体、第一个查询 | 从源码构建可用。`where`、`toList()` 以及结果函数 `first()`、`one()`、`count()`、`exists()`、`toSet()`：已实现，已在 H2、MariaDB、Oracle 上测试（`where` 和 `toList()` 也用 Kotlin Lambda 测试；结果函数只用 Java Lambda 测试） |
| 3 | [条件（`where`）](03-where.md) | 运算符、null 规则、`?.` `?:` `!!`、区间、`when`、捕获的值 | 将 getter 映射到 JPA 属性、HQL 生成与查询执行（`where`、`toList()`）：已实现，已在 H2、MariaDB、Oracle 上测试。`?.`、`!!`、捕获值和 `in`：已用 Kotlin Lambda 测试。`?:`、区间、`when`：已设计 |
| 4 | [排序与分页](04-order-paging.md) | `orderBy`、`Direction`、`limit`、`offset` | 核心部分已实现，已在 H2、MariaDB、Oracle 上测试（一个 Kotlin 测试，其余为 Java 测试），包括 `SortSpec`、join 排序 `{ o, c, by -> }` 和用于外部排序的排序策略（规则来自代码、Spring Boot 属性或 `@Sortable`）。属性引用、`SortSpec` 的 join 写法：已设计 |
| 5 | [子查询](05-subquery.md) | `exists()`、`x in sub...`、标量子查询和关联子查询 | 已实现，已在 H2、MariaDB、Oracle 上测试（`exists`、`in` 和标量也用 Kotlin Lambda 测试）。MariaDB 的 `lateral` 改写：尚未实现 |
| 6 | [查询列、聚合与分组](06-select-group.md) | `select`、data class、`Pair`、`groupBy`、`having`、`Agg`、join、派生表、CTE | `select`、`groupBy`、`having`、`Agg`、`distinct()`：已实现，已在 H2、MariaDB、Oracle 上测试（data class、`Pair`、`Triple`、`groupBy` 和 `having` 也用 Kotlin Lambda 测试）。显式 join、派生表和 CTE：已实现（Java 测试；Kotlin 尚未测试） |
| 7 | [更新、插入、删除](07-dml.md) | 属性赋值、`by.set`、`values`、`all()`、`execute()`、`limit`、`executeOne()` | 已实现，已在 H2、MariaDB、Oracle 上测试，包括 `limit` 和 `executeOne()`（属性赋值、`by[column] = value`、insert 和 delete 也用 Kotlin Lambda 测试）。update 和 delete 的 `where` 中的子查询（`sub`）以及 `insert ... select` 中的聚合：已实现。`set` 值中的子查询：已设计 |
| 7.6 | [Upsert](07-6-upsert.md) | `upsert`、`onConflict { ... }.update { ... }`、`onConflict { ... }.ignore()`、各数据库的行数 | 已实现，已在 H2、MariaDB、Oracle 上测试（Java 测试；Kotlin 尚未测试） |
| 8 | [存储过程与数据库函数](08-procedures-functions.md) | `@SqlProcedure`、`companion object` 中的 `@SqlFunction`、`qb.call`、`qb.exec` | 已实现（Java 测试；Kotlin 尚未测试）：数据库函数在 H2、MariaDB、Oracle 上；存储过程在 MariaDB 和 Oracle 上。SARGable 警告、存储过程参数的类型和名字检查：尚未实现 |
| 9 | [Kotlin 说明](09-kotlin-notes.md) | Kotlin 的特别之处：`?.` `?:` `!!`、`==`、`in`、`when`、运算符、限制 KC1 到 KC21 | 部分已测试（`?.`、`!!`、`in`、data class、属性赋值）。其他写法：已设计 |
| 10 | [规则与限制](10-rules-and-limits.md) | 所有规则表集中在一处 | 部分已实现 |
| 11 | [错误与语言](../11-errors-and-languages.md)（公共） | 错误码、异常、消息语言、日志 | 已实现，已做单元测试。警告 `LQL-5001` 和严格模式：尚未实现 |
| 12 | [Spring Data JPA](12-spring-data-jpa.md) | `LambdaQlExecutor<T>`、带方法体的仓库方法、DML 快捷方法（`deleteAll`、`updateOne` 等）、Spring `Sort` 和 `Pageable`、模块、事务、Kotlin 2.2 / `-Xjvm-default=all` | 已实现，已在 H2、MariaDB、Oracle 上用 Spring Boot 3.4.2 测试，包括 DML 快捷方法以及更新、删除、插入和 upsert 构建器（Java 测试；Kotlin repository 尚未测试） |

术语含义：

| 术语 | 含义 |
|---|---|
| 已实现 | 代码已在仓库中，并且有单元测试。 |
| 已设计 | 语法和规则已经确定。原型已测试。库中还没有这部分代码。 |
| 计划中 | 思路已知，但细节尚未确定。 |

## 从哪里开始

1. 阅读公共的[概述](../01-overview.md)来理解基本思想：Lambda 是**查询**，不是代码。lambdaQL 读取它的字节码，从不执行它。
2. 按照 [2. 入门](02-getting-started.md) 配置你的项目。
3. 在 [3. 条件](03-where.md) 中学习条件。这是最重要的一章。
4. 读一遍 [9. Kotlin 说明](09-kotlin-notes.md)。它解释了在查询中行为不同的 Kotlin 写法（`!!`、`?.`、`?:`、`==`、`===`）。

## 示例实体

本路线的所有页面都使用以下 Kotlin 实体。完整代码见 [2. 入门](02-getting-started.md#example-entities)。

| 实体 | 属性 |
|---|---|
| `Order` | `id: Long?`、`price: Double?`、`status: Int?`、`active: Boolean`、`description: String?`、`customer: Customer?`、`updateAt: LocalDateTime?`、`items: MutableList<Item>` |
| `Customer` | `id: Long?`、`name: String?`、`grade: Int?` |
| `Item` | `id: Long?`、`order: Order?`、`price: Double?`、`qty: Int` |

大多数属性是可空的 `var` 属性，因为数据库列可能为 NULL。
`active` 和 `qty` 是 Kotlin 的 `Boolean` 和 `Int`。它们是 JVM 基本类型，因此永远不会为 NULL。

## 首先要知道的三件事

| Kotlin | 在 lambdaQL Lambda 中 |
|---|---|
| `it.price!! > 100.0` | `!!` 会被忽略。它只是为了让代码能够编译。不会出现 `NullPointerException`，因为 Lambda 从不执行。 |
| `it.customer?.name == "Kim"` | `it.customer?.name` 指代关联客户的一个列。在 `where` 中它默认是内连接 `join`：没有客户的订单不会被返回。null 守卫（`it.customer == null \|\| ...`）会让它变成 `left join`。`it.customer!!.name` 表示同一个列。 |
| `status: Int?` 时的 `it.status == 1` | `e0.status = 1`。`status` 为 NULL 的行不匹配（SQL 语义）。lambdaQL 忽略 Kotlin 编译器添加的 null 检查。只有**你写的** null 检查才算数。 |

详情：[9. Kotlin 说明](09-kotlin-notes.md)。

## 支持的环境

| 项目 | 版本 |
|---|---|
| Kotlin | 2.x（设计已用 kotlinc 1.7.22、2.1.10、2.2.21、2.3.0 和 2.3.21 检查） |
| Java（JVM） | 21 |
| Hibernate ORM | 6.6（设计已用 6.6.5 验证） |
| 设计中已验证的数据库 | H2 2.3、MariaDB 10.11、Oracle 23.26 (23ai) |
| Spring Boot（可选） | 3.4（已用 3.4.2 测试，Java 测试）。Spring Boot 4 尚未验证 |

## 其他语言

每个语言文件夹（`en`、`ko`、`ja`、`zh`）的结构都相同。要阅读某个页面的其他语言版本，请把地址中的 `zh` 改为 `en`、`ko` 或 `ja`。链接在本页顶部。

## 帮助与贡献

- 源代码：<https://github.com/codebit0/lambdaql>
- 英文手册是原始版本。其他语言是翻译版本。
- 如果你发现错误，请提交 issue 或 pull request。
- 设计文档（韩文，面向贡献者）：[ROADMAP.md](../../../design/ROADMAP.md)、[KOTLIN.md](../../../design/KOTLIN.md)
