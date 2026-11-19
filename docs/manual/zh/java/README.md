<a id="lambdaql-manual-java-track"></a>
# lambdaQL 手册：Java 路线

**其他语言：** [English](../../en/java/README.md) | [한국어](../../ko/java/README.md) | [日本語](../../ja/java/README.md) | [简体中文](../../zh/java/README.md)

[手册首页](../README.md) | [Kotlin 路线](../kotlin/README.md) | 公共页面：[1. 概述](../01-overview.md) · [11. 错误与语言](../11-errors-and-languages.md)

这是 lambdaQL 手册的 **Java 路线**。本路线的每个页面**只包含 Java 代码**。
如果你用 Kotlin 编写查询，请阅读 [Kotlin 路线](../kotlin/README.md)。它的章节和编号与本路线相同。

lambdaQL 是一个面向 Java 和 Kotlin 的查询库。
你只需编写普通的 Lambda 表达式。lambdaQL 读取 Lambda 的字节码，并构建 Hibernate 6.6 HQL 查询。
Lambda 永远不会被执行。

```java
List<Order> orders = qb.from(Order.class)
    .where(o -> o.getStatus() == 1 && o.getPrice() > minPrice)
    .orderBy((o, by) -> by.desc(o.getPrice()).asc(o.getId()))
    .limit(20)
    .toList();
// HQL: select e0 from Order e0
//      where e0.status = 1 and e0.price > :p1
//      order by e0.price desc nulls last, e0.id asc
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

> [!NOTE]
> **关于示例。**
> 示例均为设计示例。某个功能实现后，其示例将替换为取自测试套件的代码，并附上测试文件的链接。在 [3. 条件](03-where.md)、[4. 排序与分页](04-order-paging.md)、[5. 子查询](05-subquery.md)、[6. 查询列、聚合与分组](06-select-group.md)、[7. 更新、插入、删除](07-dml.md)、[8. 存储过程与数据库函数](08-procedures-functions.md)、[11. 错误与语言](../11-errors-and-languages.md) 和 [12. Spring Data JPA](12-spring-data-jpa.md) 中，这些示例标记为 **(已测试)**。

<a id="contents"></a>
## 目录

标记为"公共"的页面由 Java 和 Kotlin 两条路线共用。它们在不同小节中分别提供两种语言的小示例。

| # | 页面 | 你将学到 |
|---|---|---|
| 1 | [概述](../01-overview.md)（公共） | lambdaQL 是什么、为什么需要它、它如何工作 |
| 2 | [入门](02-getting-started.md) | 环境要求、从源码构建、实体、`QueryBuilder`、第一个查询 |
| 3 | [条件（`where`）](03-where.md) | 运算符、NULL 规则、捕获的值、关联路径 |
| 4 | [排序与分页](04-order-paging.md) | `orderBy`、`Direction`、NULL 排序、`limit`、`offset` |
| 5 | [子查询](05-subquery.md) | `exists`、`contains`（IN）、NOT EXISTS、标量子查询和关联子查询 |
| 6 | [查询列、聚合与分组](06-select-group.md) | `select`、record 和 DTO、`Agg`、`groupBy`、`having`、join（`join`、`leftJoin`、`crossJoin`）、派生表、CTE |
| 7 | [更新、插入、删除](07-dml.md) | `set`、`Dml.value`、`values`、`all()`、`execute()`、`limit`、`executeOne()` |
| 7.6 | [Upsert](07-6-upsert.md) | `upsert`、`onConflict(...).update(...)`、`onConflict(...).ignore()`、各数据库的行数 |
| 8 | [存储过程与数据库函数](08-procedures-functions.md) | `@SqlProcedure`、`@SqlFunction`、`qb.call`、`qb.exec` |
| 9 | [Java 说明](09-java-notes.md) | 装箱、对象上的 `==`、`String` 比较、`Objects.equals`、方法引用、`this` 捕获、编译器说明 |
| 10 | [规则与限制](10-rules-and-limits.md) | 所有规则表、Java 与 SQL 的差异、数据库差异、不支持的内容 |
| 11 | [错误与语言](../11-errors-and-languages.md)（公共） | 错误码、异常、消息语言、日志 |
| 12 | [Spring Data JPA](12-spring-data-jpa.md) | `LambdaQlExecutor<T>`、仓库默认方法、DML 快捷方法（`deleteAll`、`updateOne` 等）、Spring `Sort` 和 `Pageable`、模块、事务、`QueryBuilder` bean |

<a id="example-entities"></a>
## 示例实体

所有示例都使用以下三个实体。完整代码见[入门](02-getting-started.md#example-entities)。

| 实体 | 字段 |
|---|---|
| `Order` | `id`、`price`、`status`、`active`、`description`、`customer`（指向 `Customer`）、`updateAt`、`items`（指向多个 `Item`） |
| `Customer` | `id`、`name`、`grade` |
| `Item` | `order`（指向 `Order`）、`price`、`qty` |

<a id="supported-environment"></a>
## 支持的环境

| 项目 | 版本 |
|---|---|
| Java | 21 |
| 编译器 | javac（不保证支持 Eclipse 编译器 ECJ） |
| Hibernate ORM | 6.6（设计已用 6.6.5 验证） |
| 已验证的数据库 | H2 2.3、MariaDB 10.11、Oracle 23.26 (23ai) |
| Spring Boot（可选） | 3.4（已用 3.4.2 测试）。Spring Boot 4 尚未验证 |

<a id="help-and-contributions"></a>
## 帮助与贡献

- 源代码：<https://github.com/codebit0/lambdaql>
- 韩文手册是原始版本。英文、日文和中文手册是翻译版本。
- 如果你发现错误，请提交 issue 或 pull request。
- 设计文档（韩文，面向贡献者）：[ROADMAP.md](../../../design/ROADMAP.md)、[KOTLIN.md](../../../design/KOTLIN.md)
