<a id="lambdaql-user-manual-english"></a>
# lambdaQL 用户手册（简体中文）

**其他语言：** [English](../en/README.md) | [한국어](../ko/README.md) | [日本語](../ja/README.md) | [简体中文](../zh/README.md)

lambdaQL 是运行在 JPA 和 Hibernate 之上、面向 Java 和 Kotlin 的查询库。
用 Lambda 表达式编写查询的条件和排序后，lambdaQL 分析 Lambda 表达式的字节码 opcode 并生成 HQL 查询。

<a id="a-first-look"></a>
## 示例

### Java

<!-- region: ManualSharedTest.java#readme-first -->
```java
List<Order> orders = qb.from(Order.class)
        .where(o -> o.getStatus() == 1 && o.getPrice() > minPrice)
        .orderBy(o -> desc(o.getPrice()).asc(o.getId()))
        .limit(20)
        .toList();
```

### Kotlin

<!-- region: ManualKotlinSharedTest.kt#readme-first -->
```kotlin
val orders = qb.from(Order::class.java)
    .where { it.status.toInt() == 1 && it.price > minPrice }
    .orderBy { o -> desc(o.price).asc(o.id) }
    .limit(20)
    .toList()
```

<a id="hql-the-same-for-both"></a>
### HQL

```
select e0 from Order e0
where e0.status = 1 and e0.price > :p1
order by e0.price desc, e0.id asc
```

示例代码：[`ManualSharedTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualSharedTest.java)（`readme-first`）、[`ManualKotlinSharedTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSharedTest.kt)（`readme-first`）。

<a id="choose-your-track"></a>
## 选择路线

本手册有**两条路线**：一条面向 Java，一条面向 Kotlin。  
每条路线只包含该语言的代码。请选择编写查询所用的语言。

| 路线 | 起始页面 | 适用对象 |
|---|---|---|
| **Java** | [Java 路线](java/README.md) | 用 Java 编写查询。 |
| **Kotlin** | [Kotlin 路线](kotlin/README.md) | 用 Kotlin 编写查询。 |

两条路线使用同一个引擎，所以同一个查询在两种语言中生成相同的 HQL。

<a id="table-of-contents"></a>
## 目录

| # | 章节 | Java | Kotlin |
|---|---|---|---|
| 1 | 概述（公共） | [01-overview.md](01-overview.md) | [01-overview.md](01-overview.md) |
| 2 | 入门：环境要求、从源码构建、第一个查询 | [Java](java/02-getting-started.md) | [Kotlin](kotlin/02-getting-started.md) |
| 3 | 条件（`where`）：运算符、null 规则、捕获的值 | [Java](java/03-where.md) | [Kotlin](kotlin/03-where.md) |
| 4 | 排序与分页：`orderBy`、`Direction`、`limit`、`offset` | [Java](java/04-order-paging.md) | [Kotlin](kotlin/04-order-paging.md) |
| 5 | 子查询：`exists`、`contains`、标量子查询和关联子查询 | [Java](java/05-subquery.md) | [Kotlin](kotlin/05-subquery.md) |
| 6 | 查询、聚合与分组：`select`、`groupBy`、`having`、`Agg`、join（`join`、`leftJoin`、`crossJoin`）、派生表与 CTE（`with`） | [Java](java/06-select-group.md) | [Kotlin](kotlin/06-select-group.md) |
| 7 | 更新、插入、删除：`set`、`values`、`all()`、`execute()`、`limit`、`executeOne()` | [Java](java/07-dml.md) | [Kotlin](kotlin/07-dml.md) |
| 7.6 | Upsert：`upsert`、`onConflict(...).update(...)`、`onConflict(...).ignore()` | [Java](java/07-6-upsert.md) | [Kotlin](kotlin/07-6-upsert.md) |
| 8 | 存储过程与数据库函数：`@SqlProcedure`、`@SqlFunction`、`qb.call`、`qb.exec` | [Java](java/08-procedures-functions.md) | [Kotlin](kotlin/08-procedures-functions.md) |
| 9 | 语言说明 | [Java 说明](java/09-java-notes.md) | [Kotlin 说明](kotlin/09-kotlin-notes.md) |
| 10 | 规则与限制：完整的规则表 | [Java](java/10-rules-and-limits.md) | [Kotlin](kotlin/10-rules-and-limits.md) |
| 11 | 错误与语言（公共） | [11-errors-and-languages.md](11-errors-and-languages.md) | [11-errors-and-languages.md](11-errors-and-languages.md) |
| 12 | Spring Data JPA：`LambdaQlExecutor<T>`、DML 快捷方法、Spring `Sort` 和 `Pageable`、模块、事务、`QueryBuilder` bean | [Java](java/12-spring-data-jpa.md) | [Kotlin](kotlin/12-spring-data-jpa.md) |
| 13 | Spring Boot 3 与 4（公共）：版本、构件、Hibernate 6.6 与 7.4 的 SQL 差异 | [13-spring-boot-versions.md](13-spring-boot-versions.md) | [13-spring-boot-versions.md](13-spring-boot-versions.md) |

<a id="about-the-examples"></a>
## 关于示例

示例代码是比对执行结果和 HQL 的验证代码。各节末尾的"示例代码"链接可查看完整代码。

**各数据库 SQL 块的读法。** `…` 是缩写的 select 列表（实体的所有列），`?` 是绑定参数。

<a id="example-entities"></a>
## 示例实体

本手册的所有示例都使用以下三个实体。

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
| Java | 21 及以上 |
| Kotlin | 2.x 及以上 |
| Hibernate ORM | 6.6 或 7.4（已用 6.6.53、7.4.13 测试） |
| 已验证的数据库 | H2 2.3、MariaDB 10.11、MySQL 8.4、PostgreSQL 17、Oracle 23.26 (23ai) |
| Spring Boot（可选） | 3.5 或 4.1（已用 3.5.16、4.1.2 测试）。见 [13. Spring Boot 3 与 4](13-spring-boot-versions.md) |

<a id="help-and-contributions"></a>
## 帮助与贡献

- 源代码：<https://github.com/lambdaql/lambdaql>
- 在任何语言版本中发现错误，请提交 issue 或 pull request。欢迎帮助翻译。
- 设计文档（韩文，面向贡献者）：[ROADMAP.md](../../design/ROADMAP.md)、[KOTLIN.md](../../design/KOTLIN.md)
