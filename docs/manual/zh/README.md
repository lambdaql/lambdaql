# lambdaQL 用户手册（简体中文）

**其他语言：** [English](../en/README.md) | [한국어](../ko/README.md) | [日本語](../ja/README.md) | [简体中文](../zh/README.md)

> [!IMPORTANT]
> **状态：lambdaQL 处于早期开发阶段。**
> 目前已实现**分析核心**、**`where` + `toList()`** 以及**排序与分页**的核心部分（`orderBy`、`limit`、`offset`）：lambdaQL 加载 Lambda 表达式，解码它，生成 HQL 并执行它。这已在 H2、MariaDB 和 Oracle 上测试（使用 Java Lambda）。
> 本手册中的其他内容（子查询、`select`、update/insert/delete、存储过程、Spring 模块）都处于**已设计**状态：语法和规则已经确定，并已在 H2、MariaDB 和 Oracle 上用原型验证过，但代码还没有进入库中。
> 目前还没有发布任何构件。如果想试用，请从源码构建（[Java](java/02-getting-started.md#build-from-source) | [Kotlin](kotlin/02-getting-started.md#build-from-source)）。

lambdaQL 是一个面向 Java 和 Kotlin 的查询库。
你只需编写普通的 Lambda 表达式。lambdaQL 读取 Lambda 的字节码，并构建 Hibernate 6.6 HQL 查询。
Lambda 永远不会被执行。

## 初步了解

### Java

```java
List<Order> orders = qb.from(Order.class)
    .where(o -> o.getStatus() == 1 && o.getPrice() > minPrice)
    .orderBy((o, by) -> by.desc(o.getPrice()).asc(o.getId()))
    .limit(20)
    .toList();
```

### Kotlin

```kotlin
val orders = qb.from(Order::class.java)
    .where { it.status == 1 && it.price!! > minPrice }
    .orderBy { o, by -> by.desc(o.price).asc(o.id) }
    .limit(20)
    .toList()
```

### HQL（两种语言相同）

```
select e0 from Order e0
where e0.status = 1 and e0.price > :p1
order by e0.price desc nulls last, e0.id asc
```

**关于 SQL 块。** `…` 是缩短的 select 列表（实体的所有列），`?` 是绑定参数。这些行来自 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)。该测试使用 Hibernate 6.6.5 在 H2 2.3、MariaDB 10.11 和 Oracle 23.26 上运行每个查询，如果 Hibernate 的 SQL 发生变化，测试就会失败。

**各数据库的 SQL（Hibernate 为此 HQL 生成的 SQL）** — 手册中的 `Order` 有可为 NULL 的 `Double price`，而测试中的 `Order` 没有，所以 `SqlByDatabaseTest` 把这个 HQL 直接交给 Hibernate。来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `readme-first`：

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

## 选择你的路线

本手册有**两条路线**：一条面向 Java，一条面向 Kotlin。
每条路线只包含其自身语言的代码。请选择你编写查询时使用的语言。

| 路线 | 从这里开始 | 适合你，如果 |
|---|---|---|
| **Java** | [Java 路线](java/README.md) | 你用 Java 编写查询。 |
| **Kotlin** | [Kotlin 路线](kotlin/README.md) | 你用 Kotlin 编写查询。 |

两条路线使用同一个引擎，所以同一个查询在两种语言中生成相同的 HQL。
两条路线的章节编号和文件名相同。只有第 9 章不同（Java 说明或 Kotlin 说明）。
每个路线页面都有指向另一条路线中同一页面的链接，你可以随时切换。

## 公共页面

这些页面适用于两种语言。代码分别展示在 Java 和 Kotlin 两个小节中。

| 页面 | 内容 |
|---|---|
| [1. 概述](01-overview.md) | lambdaQL 是什么、为什么需要它、它如何工作、设计原则 |
| [11. 错误与语言](11-errors-and-languages.md) | 错误码、异常、消息语言、日志 |

## 目录

| # | 章节 | Java | Kotlin |
|---|---|---|---|
| 1 | 概述（公共） | [01-overview.md](01-overview.md) | [01-overview.md](01-overview.md) |
| 2 | 入门：环境要求、从源码构建、第一个查询 | [Java](java/02-getting-started.md) | [Kotlin](kotlin/02-getting-started.md) |
| 3 | 条件（`where`）：运算符、null 规则、捕获的值 | [Java](java/03-where.md) | [Kotlin](kotlin/03-where.md) |
| 4 | 排序与分页：`orderBy`、`Direction`、`limit`、`offset` | [Java](java/04-order-paging.md) | [Kotlin](kotlin/04-order-paging.md) |
| 5 | 子查询：`exists`、`contains`、标量子查询和关联子查询 | [Java](java/05-subquery.md) | [Kotlin](kotlin/05-subquery.md) |
| 6 | 查询列、聚合与分组：`select`、`groupBy`、`having`、`Agg` | [Java](java/06-select-group.md) | [Kotlin](kotlin/06-select-group.md) |
| 7 | 更新、插入、删除：`set`、`values`、`all()`、`execute()` | [Java](java/07-dml.md) | [Kotlin](kotlin/07-dml.md) |
| 8 | 存储过程与数据库函数：`@SqlProcedure`、`@SqlFunction`、`qb.call`、`qb.exec` | [Java](java/08-procedures-functions.md) | [Kotlin](kotlin/08-procedures-functions.md) |
| 9 | 语言说明 | [Java 说明](java/09-java-notes.md) | [Kotlin 说明](kotlin/09-kotlin-notes.md) |
| 10 | 规则与限制：完整的规则表 | [Java](java/10-rules-and-limits.md) | [Kotlin](kotlin/10-rules-and-limits.md) |
| 11 | 错误与语言（公共） | [11-errors-and-languages.md](11-errors-and-languages.md) | [11-errors-and-languages.md](11-errors-and-languages.md) |
| 12 | Spring Data JPA：`LambdaQlExecutor<T>`、仓库方法、模块、事务 | [Java](java/12-spring-data-jpa.md) | [Kotlin](kotlin/12-spring-data-jpa.md) |

## 状态

| 功能 | 状态 | Java | Kotlin |
|---|---|---|---|
| Lambda 加载（找到 Lambda 主体，读取其字节码） | 已实现，有单元测试 | [01](01-overview.md#how-it-works-5-steps) | [01](01-overview.md#how-it-works-5-steps) |
| 条件解码（`&&`、`\|\|`、`!`、比较运算、捕获的值） | 已实现，有单元测试（Java Lambda） | [03](java/03-where.md) | [03](kotlin/03-where.md) |
| 将 getter 映射到 JPA 属性，计算捕获的值 | 已实现，已在 H2、MariaDB、Oracle 上测试 | [03](java/03-where.md) | [03](kotlin/03-where.md) |
| HQL 生成与查询执行（`where`、`toList()`） | 已实现，已在 H2、MariaDB、Oracle 上测试 | [03](java/03-where.md) | [03](kotlin/03-where.md) |
| 排序与分页（`orderBy`、`limit`、`offset`） | 核心部分已实现，已在 H2、MariaDB、Oracle 上测试（Java Lambda）。`SortSpec`、`SortWhitelist` 和 join 排序：已设计 | [04](java/04-order-paging.md) | [04](kotlin/04-order-paging.md) |
| 子查询（`exists`、`contains`、标量、关联） | 已设计 | [05](java/05-subquery.md) | [05](kotlin/05-subquery.md) |
| 投影、`groupBy`、`having`、聚合（`Agg`） | 已设计 | [06](java/06-select-group.md) | [06](kotlin/06-select-group.md) |
| 派生表与 CTE | 已设计 | [06](java/06-select-group.md) | [06](kotlin/06-select-group.md) |
| `update`、`insert`、`delete` | 已设计 | [07](java/07-dml.md) | [07](kotlin/07-dml.md) |
| 存储过程与数据库函数 | 已设计 | [08](java/08-procedures-functions.md) | [08](kotlin/08-procedures-functions.md) |
| Kotlin 写法（`?.`、`?:`、`!!`、区间、`when`、`in`） | 已设计（解码器的部分功能已就绪） | – | [09](kotlin/09-kotlin-notes.md) |
| 4 种语言的错误码和消息 | 已设计 | [11](11-errors-and-languages.md) | [11](11-errors-and-languages.md) |
| Spring Data JPA（`LambdaQlExecutor<T>`） | 已设计 | [12](java/12-spring-data-jpa.md) | [12](kotlin/12-spring-data-jpa.md) |

术语含义：

| 术语 | 含义 |
|---|---|
| 已实现 | 代码已在仓库中，并且有单元测试。 |
| 进行中 | 已有部分代码，但尚未完成。 |
| 已设计 | 语法和规则已经确定。原型已测试。库中还没有这部分代码。 |
| 计划中 | 思路已知，但细节尚未确定。 |

测试在这里。`SelectQueryTest` 和 `SortQueryTest` 使用用户 API，其他测试检查内部类。

| 测试 | 检查内容 |
|---|---|
| [SelectQueryTest.java](../../../query/src/test/java/org/lambdaql/query/SelectQueryTest.java) | `qb.from(...).where(...)`：在 H2、MariaDB 和 Oracle 上的 HQL、参数和返回的行 |
| [SortQueryTest.java](../../../query/src/test/java/org/lambdaql/query/SortQueryTest.java) | `orderBy`、`limit`、`offset`：在 H2、MariaDB 和 Oracle 上的 HQL 和行的顺序 |
| [LoadExamplesTest.java](../../../query/src/test/java/org/lambdaql/engine/load/LoadExamplesTest.java) | 找到 Lambda 主体，读取捕获的值，拒绝不可序列化的 Lambda |
| [ConditionDecodeTest.java](../../../query/src/test/java/org/lambdaql/engine/decode/ConditionDecodeTest.java) | 从字节码重建 `&&`、`\|\|`、`!` 和比较运算，拒绝值三元表达式 |
| [IrExamplesTest.java](../../../query/src/test/java/org/lambdaql/engine/ir/IrExamplesTest.java) | 内部条件树（比较运算符、AND、OR、NOT） |
| [NegationMinimizerTest.java](../../../query/src/test/java/org/lambdaql/engine/decode/NegationMinimizerTest.java) | 将 `!` 下推到比较运算中（德摩根定律），且不改变结果 |
| [HostEvaluatorTest.java](../../../query/src/test/java/org/lambdaql/engine/sem/HostEvaluatorTest.java) | 在绑定之前，在 Java 中根据捕获的变量计算值 |
| [JpaEntityModelTest.java](../../../query/src/test/java/org/lambdaql/engine/sem/JpaEntityModelTest.java) | 从 JPA Metamodel 读取实体属性（映射 getter 的初步代码） |

## 关于示例

示例均为设计示例。某个功能实现后，其示例将替换为取自测试套件的代码，并附上测试文件的链接。在 3. 条件和 4. 排序与分页（Java）中，这些示例标记为 **(已测试)**。

## 示例实体

本手册中的所有示例都使用以下三个实体。

| 实体 | 字段 |
|---|---|
| `Order` | `id`、`price`、`status`、`active`、`description`、`customer`（指向 `Customer`）、`updateAt`、`items`（指向多个 `Item`） |
| `Customer` | `id`、`name`、`grade` |
| `Item` | `order`（指向 `Order`）、`price`、`qty` |

完整的实体代码见入门章节：[Java](java/02-getting-started.md#example-entities) | [Kotlin](kotlin/02-getting-started.md#example-entities)。

## 支持的环境

| 项目 | 版本 |
|---|---|
| Java | 21 |
| Kotlin | 2.x |
| Hibernate ORM | 6.6（设计已用 6.6.5 验证） |
| 已验证的数据库 | H2 2.3、MariaDB 10.11、Oracle 23.26 (23ai) |

## 目录结构

```
manual/en/
├── README.md                      本页面
├── 01-overview.md                 公共
├── 11-errors-and-languages.md     公共
├── java/                          Java 路线（仅 Java 代码）
│   ├── README.md
│   ├── 02-getting-started.md ... 08-procedures-functions.md
│   ├── 09-java-notes.md
│   └── 10-rules-and-limits.md
└── kotlin/                        Kotlin 路线（仅 Kotlin 代码）
    ├── README.md
    ├── 02-getting-started.md ... 08-procedures-functions.md
    ├── 09-kotlin-notes.md
    └── 10-rules-and-limits.md
```

每个语言文件夹（`en`、`ko`、`ja`、`zh`）中的文件名都相同。要切换语言，只需更改链接中的文件夹名。

## 帮助与贡献

- 源代码：<https://github.com/codebit0/lambdaql>
- 英文手册是原始版本。韩文、日文和中文手册是翻译版本。
- 如果你在任何语言版本中发现错误，请提交 issue 或 pull request。欢迎帮助翻译。
- 设计文档（韩文，面向贡献者）：[ROADMAP.md](../../design/ROADMAP.md)、[KOTLIN.md](../../design/KOTLIN.md)
