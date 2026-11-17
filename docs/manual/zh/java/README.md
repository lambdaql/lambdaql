# lambdaQL 手册：Java 路线

**其他语言：** [English](../../en/java/README.md) | [한국어](../../ko/java/README.md) | [日本語](../../ja/java/README.md) | [简体中文](../../zh/java/README.md)

> [!IMPORTANT]
> **状态：** lambdaQL 处于早期开发阶段。
> 已实现**分析核心**、**带结果函数的 `where`**（`toList()`、`toSet()`、`first()`、`one()`、`count()`、`exists()`）以及**排序与分页**的核心部分（`orderBy`、`limit`、`offset`、`SortSpec`）：lambdaQL 加载 Lambda 表达式，解码它，生成 HQL 并执行它。这已在 H2、MariaDB 和 Oracle 上测试。
> **更新、插入、删除和 upsert** 也已实现，包括 `limit` 和单行保护 `executeOne()`（已在 H2、MariaDB 和 Oracle 上测试）。
> **Spring Data JPA 模块**也已实现：repository 执行器 `LambdaQlExecutor<T>`（支持 Spring `Sort`、`Pageable`、`deleteAll(where)` 和 `updateOne(where, set)` 等 DML 快捷方法，以及更新、删除、插入和 upsert 构建器）以及 Spring Boot 自动配置（已用 Spring Boot 3.4.2 测试）。DML 需要调用方的事务。
> **子查询**（`where((o, sub) -> ...)`，也可用于 update 和 delete）和 **`select`**（record 和 DTO、`groupBy`、`having`、`Agg` 聚合，也可用于 `insert ... select`）同样已实现（已在 H2、MariaDB 和 Oracle 上测试）。
> **显式 join**（`join`、`leftJoin`、`crossJoin`）、**派生表和 CTE**（`qb.from(valueQuery)`、`qb.with(valueQuery)`）、**存储过程与数据库函数**（`@SqlProcedure`、`@SqlFunction`、`qb.call`、`qb.exec`）以及**带四种语言消息的错误码**（`[LQL-xxxx]`）同样已实现（已在 H2、MariaDB 和 Oracle 上测试；存储过程在 MariaDB 和 Oracle 上测试）。
> 用于外部排序的**排序策略**（`SortRules`、`SortPolicy`、`SortMode`、`@Sortable`、Spring Boot 属性 `lambdaql.sort.*`）也已实现（已在 H2、MariaDB、Oracle 上测试）。
> 本路线中的其余内容（例如 `SortSpec` 的 join 写法和 SARGable 警告）处于**已设计**状态：语法和规则已经确定，并已用原型验证过，但代码还没有进入库中。
> 目前还没有任何构件发布到 Maven Central。如果想试用，请[从源码构建](02-getting-started.md#build-from-source)。

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
| 7 | [更新、插入、删除](07-dml.md) | `set`、`by.set`、`values`、`all()`、`execute()`、`limit`、`executeOne()` |
| 7.6 | [Upsert](07-6-upsert.md) | `upsert`、`onConflict(...).update(...)`、`onConflict(...).ignore()`、各数据库的行数 |
| 8 | [存储过程与数据库函数](08-procedures-functions.md) | `@SqlProcedure`、`@SqlFunction`、`qb.call`、`qb.exec` |
| 9 | [Java 说明](09-java-notes.md) | 装箱、对象上的 `==`、`String` 比较、`Objects.equals`、方法引用、`this` 捕获、编译器说明 |
| 10 | [规则与限制](10-rules-and-limits.md) | 所有规则表、Java 与 SQL 的差异、数据库差异、不支持的内容 |
| 11 | [错误与语言](../11-errors-and-languages.md)（公共） | 错误码、异常、消息语言、日志 |
| 12 | [Spring Data JPA](12-spring-data-jpa.md) | `LambdaQlExecutor<T>`、仓库默认方法、DML 快捷方法（`deleteAll`、`updateOne` 等）、Spring `Sort` 和 `Pageable`、模块、事务、`QueryBuilder` bean |

<a id="status"></a>
## 状态

| 功能 | 状态 | 章节 |
|---|---|---|
| Lambda 加载（找到 Lambda 主体，读取其字节码） | 已实现，有单元测试 | [1](../01-overview.md) |
| 条件解码（`&&`、`\|\|`、`!`、比较运算、捕获的值） | 已实现，有单元测试 | [3](03-where.md) |
| 将 getter 映射到 JPA 属性 | 已实现，已在 H2、MariaDB、Oracle 上测试 | [3](03-where.md) |
| HQL 生成与查询执行（`where`、`toList()`） | 已实现，已在 H2、MariaDB、Oracle 上测试 | [3](03-where.md) |
| 结果函数（`first()`、`one()`、`count()`、`exists()`、`toSet()`） | 已实现，已在 H2、MariaDB、Oracle 上测试 | [2](02-getting-started.md#getting-the-result) |
| 排序与分页（`orderBy`、`limit`、`offset`） | 核心部分已实现，已在 H2、MariaDB、Oracle 上测试，包括 `SortSpec`、join 排序 `(o, c, by)` 和用于外部排序的排序策略（规则来自代码、Spring Boot 属性或 `@Sortable`）。`SortSpec` 的 join 写法：已设计 | [4](04-order-paging.md) |
| 子查询（`exists`、`contains`、标量、关联） | 已实现，已在 H2、MariaDB、Oracle 上测试。MariaDB 的 `lateral` 改写：尚未实现 | [5](05-subquery.md) |
| 投影、`groupBy`、`having`、聚合（`Agg`） | 已实现，已在 H2、MariaDB、Oracle 上测试，包括 `distinct()` 和结果函数。按聚合排序：v0.2 候选 | [6](06-select-group.md) |
| 显式 join（`join`、`leftJoin`、`crossJoin`，最多三个实体） | 已实现，已在 H2、MariaDB、Oracle 上测试，包括所有实体上的 `where`、`orderBy`、`select` 和分页。DML 中和子查询中的 join：以后 | [6](06-select-group.md#68-explicit-joins) |
| 派生表与 CTE（`qb.from(valueQuery)`、`qb.with(valueQuery)`） | 已实现，已在 H2、MariaDB、Oracle 上测试。与派生表的 join 以及两次使用同一个 CTE：以后 | [6](06-select-group.md#69-derived-table-subquery-in-from) |
| `update`、`insert`、`delete`、upsert | 已实现，已在 H2、MariaDB、Oracle 上测试，包括 `limit` 和 `executeOne()`。update 和 delete 的 `where` 中的子查询（`sub`）以及 `insert ... select` 中的聚合：已实现。`set` 值中的子查询：已设计 | [7](07-dml.md) |
| 存储过程与数据库函数 | 已实现：查询中的数据库函数和单独调用，已在 H2、MariaDB、Oracle 上测试；存储过程（`call`、`exec`、OUT、INOUT、结果集）已在 MariaDB 和 Oracle 上测试（H2 没有 OUT 参数）。参数名检查（已在 MariaDB 和 Oracle 上测试）和 Oracle 包重载检测（已在 Oracle 上测试）：已实现。SARGable 警告、存储过程参数的类型检查：尚未实现 | [8](08-procedures-functions.md) |
| 4 种语言的错误码和消息 | 已实现，已做单元测试：`[LQL-xxxx]` 错误码、`LambdaQLException` 及其子类、英文、日文、中文和韩文消息、`lambdaql.locale`。警告 `LQL-5001` 和严格模式：尚未实现 | [11](../11-errors-and-languages.md) |
| Spring Data JPA（`LambdaQlExecutor<T>`、Spring `Sort` / `Pageable`、Spring Boot 自动配置） | 已实现，已在 H2、MariaDB、Oracle 上用 Spring Boot 3.4.2 测试，包括 DML 快捷方法（`deleteAll`、`updateOne` 等）以及更新、删除、插入和 upsert 构建器。Spring Boot 4：尚未验证 | [12](12-spring-data-jpa.md) |

术语含义：

| 术语 | 含义 |
|---|---|
| 已实现 | 代码已在仓库中，并且有单元测试。 |
| 进行中 | 已有部分代码，但尚未完成。 |
| 已设计 | 语法和规则已经确定。原型已测试。库中还没有这部分代码。 |
| 计划中 | 思路已知，但细节尚未确定。 |

## 示例实体

所有示例都使用以下三个实体。完整代码见[入门](02-getting-started.md#example-entities)。

| 实体 | 字段 |
|---|---|
| `Order` | `id`、`price`、`status`、`active`、`description`、`customer`（指向 `Customer`）、`updateAt`、`items`（指向多个 `Item`） |
| `Customer` | `id`、`name`、`grade` |
| `Item` | `order`（指向 `Order`）、`price`、`qty` |

## 支持的环境

| 项目 | 版本 |
|---|---|
| Java | 21 |
| 编译器 | javac（不保证支持 Eclipse 编译器 ECJ） |
| Hibernate ORM | 6.6（设计已用 6.6.5 验证） |
| 已验证的数据库 | H2 2.3、MariaDB 10.11、Oracle 23.26 (23ai) |
| Spring Boot（可选） | 3.4（已用 3.4.2 测试）。Spring Boot 4 尚未验证 |

## 帮助与贡献

- 源代码：<https://github.com/codebit0/lambdaql>
- 英文手册是原始版本。韩文、日文和中文手册是翻译版本。
- 如果你发现错误，请提交 issue 或 pull request。
- 设计文档（韩文，面向贡献者）：[ROADMAP.md](../../../design/ROADMAP.md)、[KOTLIN.md](../../../design/KOTLIN.md)
