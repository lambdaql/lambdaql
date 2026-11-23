<a id="lambdaql-user-manual-english"></a>
# lambdaQL 用户手册（简体中文）

> [!IMPORTANT]
> 本页比韩文手册旧。最新规则请参见[韩文页面](../ko/README.md)。

**其他语言：** [English](../en/README.md) | [한국어](../ko/README.md) | [日本語](../ja/README.md) | [简体中文](../zh/README.md)

lambdaQL 是一个面向 Java 和 Kotlin 的查询库。
你只需编写普通的 Lambda 表达式。lambdaQL 读取 Lambda 的字节码，并构建 Hibernate 7 HQL 查询。
Lambda 永远不会被执行。

<a id="a-first-look"></a>
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

<a id="hql-the-same-for-both"></a>
### HQL（两种语言相同）

```
select e0 from Order e0
where e0.status = 1 and e0.price > :p1
order by e0.price desc nulls last, e0.id asc
```

**关于 SQL 块。** `…` 是缩短的 select 列表（实体的所有列），`?` 是绑定参数。这些行来自 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)。该测试使用 Hibernate 7.4 在 H2 2.3、MariaDB 10.11 和 Oracle 23.26 上运行每个查询，如果 Hibernate 的 SQL 发生变化，测试就会失败。

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

<a id="choose-your-track"></a>
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

<a id="common-pages"></a>
## 公共页面

这些页面适用于两种语言。代码分别展示在 Java 和 Kotlin 两个小节中。

| 页面 | 内容 |
|---|---|
| [1. 概述](01-overview.md) | lambdaQL 是什么、为什么需要它、它如何工作、设计原则 |
| [11. 错误与语言](11-errors-and-languages.md) | 错误码、异常、消息语言、日志 |

<a id="table-of-contents"></a>
## 目录

| # | 章节 | Java | Kotlin |
|---|---|---|---|
| 1 | 概述（公共） | [01-overview.md](01-overview.md) | [01-overview.md](01-overview.md) |
| 2 | 入门：环境要求、从源码构建、第一个查询 | [Java](java/02-getting-started.md) | [Kotlin](kotlin/02-getting-started.md) |
| 3 | 条件（`where`）：运算符、null 规则、捕获的值 | [Java](java/03-where.md) | [Kotlin](kotlin/03-where.md) |
| 4 | 排序与分页：`orderBy`、`Direction`、`limit`、`offset` | [Java](java/04-order-paging.md) | [Kotlin](kotlin/04-order-paging.md) |
| 5 | 子查询：`exists`、`contains`、标量子查询和关联子查询 | [Java](java/05-subquery.md) | [Kotlin](kotlin/05-subquery.md) |
| 6 | 查询列、聚合与分组：`select`、`groupBy`、`having`、`Agg`、join（`join`、`leftJoin`、`crossJoin`）、派生表与 CTE（`with`） | [Java](java/06-select-group.md) | [Kotlin](kotlin/06-select-group.md) |
| 7 | 更新、插入、删除：`set`、`values`、`all()`、`execute()`、`limit`、`executeOne()` | [Java](java/07-dml.md) | [Kotlin](kotlin/07-dml.md) |
| 7.6 | Upsert：`upsert`、`onConflict(...).update(...)`、`onConflict(...).ignore()` | [Java](java/07-6-upsert.md) | [Kotlin](kotlin/07-6-upsert.md) |
| 8 | 存储过程与数据库函数：`@SqlProcedure`、`@SqlFunction`、`qb.call`、`qb.exec` | [Java](java/08-procedures-functions.md) | [Kotlin](kotlin/08-procedures-functions.md) |
| 9 | 语言说明 | [Java 说明](java/09-java-notes.md) | [Kotlin 说明](kotlin/09-kotlin-notes.md) |
| 10 | 规则与限制：完整的规则表 | [Java](java/10-rules-and-limits.md) | [Kotlin](kotlin/10-rules-and-limits.md) |
| 11 | 错误与语言（公共） | [11-errors-and-languages.md](11-errors-and-languages.md) | [11-errors-and-languages.md](11-errors-and-languages.md) |
| 12 | Spring Data JPA：`LambdaQlExecutor<T>`、DML 快捷方法、Spring `Sort` 和 `Pageable`、模块、事务、`QueryBuilder` bean | [Java](java/12-spring-data-jpa.md) | [Kotlin](kotlin/12-spring-data-jpa.md) |

<a id="about-the-examples"></a>
## 关于示例

示例均为设计示例。某个功能实现后，其示例将替换为取自测试套件的代码，并附上测试文件的链接。在 3. 条件、4. 排序与分页、5. 子查询、6. 查询列、聚合与分组、7. 更新、插入、删除、8. 存储过程与数据库函数、11. 错误与语言和 12. Spring Data JPA（Java）中，这些示例标记为 **(已测试)**。Kotlin 路线中取自 Kotlin 测试的示例也同样标记。

<a id="example-entities"></a>
## 示例实体

本手册中的所有示例都使用以下三个实体。

| 实体 | 字段 |
|---|---|
| `Order` | `id`、`price`、`status`、`active`、`description`、`customer`（指向 `Customer`）、`updateAt`、`items`（指向多个 `Item`） |
| `Customer` | `id`、`name`、`grade` |
| `Item` | `order`（指向 `Order`）、`price`、`qty` |

完整的实体代码见入门章节：[Java](java/02-getting-started.md#example-entities) | [Kotlin](kotlin/02-getting-started.md#example-entities)。

<a id="supported-environment"></a>
## 支持的环境

| 项目 | 版本 |
|---|---|
| Java | 21 |
| Kotlin | 2.x |
| Hibernate ORM | 7.4（已用 Spring Boot 4.1.2 中的 7.4.13 测试） |
| 已验证的数据库 | H2 2.3、MariaDB 10.11、Oracle 23.26 (23ai) |
| Spring Boot（可选） | 4.1（已用 4.1.2 测试）。本版未验证 Spring Boot 3 |

<a id="folder-layout"></a>
## 目录结构

```
manual/zh/
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

<a id="help-and-contributions"></a>
## 帮助与贡献

- 源代码：<https://github.com/codebit0/lambdaql>
- 韩文手册是原始版本。英文、日文和中文手册是翻译版本。
- 如果你在任何语言版本中发现错误，请提交 issue 或 pull request。欢迎帮助翻译。
- 设计文档（韩文，面向贡献者）：[ROADMAP.md](../../design/ROADMAP.md)、[KOTLIN.md](../../design/KOTLIN.md)
