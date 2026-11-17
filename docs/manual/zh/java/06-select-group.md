# 6. 查询列、聚合与分组（Java）

> [!NOTE]
> **状态**
> - **已实现并测试**，在 H2 2.3、MariaDB 10.11 和 Oracle 23.26 上（Hibernate 6.6.5，里程碑 M9，决定 D27）：查询一个值、Java record 或 DTO 的 `select`；带一个或多个键的 `groupBy`；`having`；`Agg` 函数；`distinct()`、`limit`、`offset`、`toList`、`toSet`、`first`、`one`；`orderBy(...).select(...)`；以及规则 J3、J4 和 J7 的检查。
> - **已设计，尚未实现：** 派生表和 CTE（6.8 和 6.9，里程碑 M8-2）、按聚合排序以及分组查询上的 `orderBy`（v0.2 候选），以及 join 投影 `(o, c) -> ...`。
> - 标记为 **(已测试)** 的示例是从测试代码中复制的。完整状态见 [Java 路线 README](README.md#status)。

[Java 路线](README.md) | [手册首页](../README.md) | 上一章：[5. 子查询](05-subquery.md) | 下一章：[7. 更新、插入、删除](07-dml.md) | [本页的 Kotlin 版本](../kotlin/06-select-group.md)

公共页面：[1. 概述](../01-overview.md) · [11. 错误与语言](../11-errors-and-languages.md)

---

本章说明如何选择查询返回**什么**：

- 每行一个值（`select(o -> o.getPrice())`）
- Java record 或 DTO 类
- 带 `groupBy` 和 `having` 的聚合（`count`、`sum`、`avg` 等）
- 值查询的 `distinct`、分页和结果方法
- FROM 子句中的子查询（派生表）和 CTE（`with`）：仅为设计

## 目录

- [6.1 示例实体与结果类型](#61-example-entities-and-result-types)
- [6.2 查询一个值](#62-select-one-value)
- [6.3 查询 record 或 DTO](#63-select-a-record-or-dto)
- [6.4 聚合函数：`Agg`](#64-aggregate-functions-agg)
- [6.5 Group by](#65-group-by)
- [6.6 Having](#66-having)
- [6.7 Distinct、分页与结果](#67-distinct-paging-and-results)
- [6.8 派生表（FROM 中的子查询）](#68-derived-table-subquery-in-from)
- [6.9 CTE（`with`）](#69-cte-with)
- [6.10 规则 J1 至 J8](#610-rules-j1-to-j8)
- [6.11 v0.1 中不包含的内容](#611-not-in-v01)

---

<a id="61-example-entities-and-result-types"></a>
## 6.1 示例实体与结果类型

示例使用以下实体（见 [2. 入门](02-getting-started.md#example-entities)）：

| 实体 | 这里用到的字段 |
|---|---|
| `Order` | `id`、`price`、`status`、`active`、`customer`（多对一） |
| `Customer` | `id`、`name`、`grade` |
| `Item` | `order`、`price`、`qty` |

标记为 **(已测试)** 的示例是从 [`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java) 中复制的。
该测试检查 HQL，执行查询，并把返回的值与在 Java 中进行同样计算的结果进行比较。它在 H2、MariaDB 和 Oracle 上都能通过。
它使用 [3.3](03-where.md#33-basic-form) 中的测试实体：`price` 是 `double`，`status` 是 `short`，`Customer.id` 是 `int`。测试数据中有一个没有客户的订单。

测试使用以下结果类型：

```java
record OrderView(Long id, String customerName, double price) {}
record Stat(Integer customerId, long count, Double total) {}
record KeyCount(Integer customerId, short status, long count) {}
record Total(long count, Double average) {}
```

> [!TIP]
> 对可能为 NULL 的值使用装箱类型（`Double`、`Long`、`Integer`）。
> 列或像 `sum` 这样的聚合可能是 NULL。基本类型 `double` 的参数无法保存 NULL：这时 lambdaQL 会抛出 `IllegalStateException`（规则 J4，见 [6.4](#null-in-aggregates-rule-j4)）。
> `count` 永远不会是 NULL，所以 `long count` 没有问题。

---

<a id="62-select-one-value"></a>
## 6.2 查询一个值

`select` 接收一个 Lambda。该 Lambda 返回一个值。结果是这个值的 `ValueQuery`。
可以在 `where` 之后和 `orderBy` 之后调用 `select`。排序、`limit` 和 `offset` 会保留。

**(已测试)** [`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java)，region `value`：

```java
ValueQuery<Double> q = qb.from(Order.class)
        .where(o -> o.getStatus() == 1)
        .orderBy((o, by) -> by.asc(o.getPrice()))
        .select(o -> o.getPrice());
assertEquals("select e0.price from Order e0 where e0.status = 1 order by e0.price asc", q.toHql().hql());
```

**关于 SQL 块。** `?` 是绑定参数。这些行来自 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)。该测试使用 Hibernate 6.6.5 在 H2 2.3、MariaDB 10.11 和 Oracle 23.26 上运行每个查询，如果 Hibernate 的 SQL 发生变化，测试就会失败。本章的重点是查询列，所以 select 列表没有缩短。

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `value`：

```sql
-- H2, MariaDB, Oracle (same)
select o1_0.price
from orders o1_0
where o1_0.status=1
order by o1_0.price
```

关联路径就是一个列，与 `where` 中相同（规则 R24）。在 `select` 中，lambdaQL 会添加一个 `left join`，因此没有客户的订单不会丢失（在 `where` 中，同一条路径默认是内连接）：

```java
.select(o -> o.getCustomer().getName())
// HQL: select j1.name from Order e0 left join e0.customer j1
```

[6.3](#63-select-a-record-or-dto) 中的 record 示例测试了这个 `left join`。
如果路径以关联实体的 `@Id` 结尾，就不会有 join。
`o.getCustomer().getId()` 读取的是外键列 `customer_id`。

---

<a id="63-select-a-record-or-dto"></a>
## 6.3 查询 record 或 DTO

在 Lambda 中调用构造函数。每个构造函数参数都是一个值表达式。
Java record 和带构造函数的普通 DTO 类都可以使用。

**(已测试)** [`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java)，region `record`：

```java
// only the columns are selected; the record is made in Java. The path uses a left join,
// so the order without a customer stays (customerName is null)
ValueQuery<OrderView> q = qb.from(Order.class)
        .select(o -> new OrderView(o.getId(), o.getCustomer().getName(), o.getPrice()));
assertEquals("select e0.id, j1.name, e0.price from Order e0 left join e0.customer j1", q.toHql().hql());
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `record`：

```sql
-- H2, MariaDB, Oracle (same)
select o1_0.id,c1_0.name,o1_0.price
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
```

lambdaQL **不会**写 `select new com.acme.OrderView(...)`（规则 J2）。
它只查询列，并在 Java 中调用构造函数。
这样可以避免长类名和嵌套类名带来的问题。
lambdaQL 会把每个值转换为构造函数参数的类型（例如把 Hibernate 的 `Long` 转换为 `Integer` 参数）。

可以返回的内容：

| 结果类型 | 简单 `select` | 派生表或 CTE 的行类型（已设计） |
|---|---|---|
| 一个值（`o.getPrice()`） | 可以 | – |
| Java record | 可以 | 可以 |
| 普通 DTO 类（构造函数） | 可以 | **不可以**（J5） |

---

<a id="64-aggregate-functions-agg"></a>
## 6.4 聚合函数：`Agg`

聚合函数是 **`org.lambdaql.Agg` 的静态方法**。
它们是 SQL 标准函数，所以由 lambdaQL 提供。（自己的数据库函数见 [8. 存储过程与数据库函数](08-procedures-functions.md)。）
`Agg` 是 lambdaQL 中唯一的静态辅助类。

| 方法 | SQL |
|---|---|
| `Agg.count()` | `count(*)` |
| `Agg.count(x)` | `count(x)` |
| `Agg.countDistinct(x)` | `count(distinct x)` |
| `Agg.sum(x)` | `sum(x)` |
| `Agg.avg(x)` | `avg(x)` |
| `Agg.min(x)` | `min(x)` |
| `Agg.max(x)` | `max(x)` |

Java 类型：

```java
long count();
<N extends Number> N sum(N x);
Double avg(Number x);
<R extends Comparable<?>> R min(R x);   // max 相同
```

静态导入是可选的。下面两行是等价的：

```java
.select(o -> new Total(Agg.count(), Agg.avg(o.getPrice())))

import static org.lambdaql.Agg.*;
.select(o -> new Total(count(), avg(o.getPrice())))
```

要点：

- 返回值只是一个**占位符**。Lambda 从不执行，所以这个值从不会被使用。
- 如果在 lambdaQL 的 Lambda **外部**调用 `Agg.sum(...)`，会抛出 `IllegalStateException`。
- `Agg` 可以用在 `select`、`having` 以及 `insert ... select` 的值中（[7.5](07-5-insert.md)）。
- 不能在 `where` 中使用 `Agg`。对聚合的条件请使用 `having`。在 `where` 中使用 `Agg` 会在分析时报错。

### 对所有行聚合（不使用 group by）

**(已测试)** [`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java)，region `total`：

```java
// aggregates without groupBy: one row over all matching orders
ValueQuery<Total> q = qb.from(Order.class)
        .where(o -> o.getStatus() == 1)
        .select(o -> new Total(Agg.count(), Agg.avg(o.getPrice())));
assertEquals("select count(*), avg(e0.price) from Order e0 where e0.status = 1", q.toHql().hql());
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `total`：

```sql
-- H2, MariaDB, Oracle (same)
select count(*),avg(o1_0.price)
from orders o1_0
where o1_0.status=1
```

这个查询总是返回一行。测试用 `q.one()` 读取它。

<a id="null-in-aggregates-rule-j4"></a>
### 聚合中的 NULL（规则 J4）

| 函数 | 没有行，或所有值都是 NULL |
|---|---|
| `sum`、`avg`、`min`、`max` | `NULL` |
| `count` | `0` |

这是 SQL 的正常行为。
如果 NULL 进入基本类型的构造函数参数（`double`、`long`），lambdaQL 会抛出 `IllegalStateException`。消息会指出是哪个参数，并提示改用包装类型（`Double`、`Long`）。已测试的示例见 [6.10](#610-rules-j1-to-j8)。

---

<a id="65-group-by"></a>
## 6.5 Group by

用 `groupBy` 写出分组键。lambdaQL 不会猜测分组键。
`groupBy` 返回分组查询。先调用 `having(...)`（可选），再调用 `select(...)`。

### 一个键

```java
List<Stat> stats = qb.from(Order.class)
    .groupBy(o -> o.getCustomer().getId())
    .select(o -> new Stat(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice())))
    .toList();
// HQL: select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id
```

键以 `@Id` 结尾，所以没有 join。没有客户的订单组成一个键为 NULL 的分组。
带 `having` 的已测试示例见 [6.6](#66-having)。

### 多个键

用 `List.of(...)` 返回键的列表：

**(已测试)** [`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java)，region `group-many`：

```java
// many keys: List.of(...) (Kotlin listOf(...), a to b, Triple)
ValueQuery<KeyCount> q = qb.from(Order.class)
        .where(o -> o.getCustomer() != null)
        .groupBy(o -> List.of(o.getCustomer().getId(), o.getStatus()))
        .select(o -> new KeyCount(o.getCustomer().getId(), o.getStatus(), Agg.count()));
assertEquals("select e0.customer.id, e0.status, count(*) from Order e0 where e0.customer is not null"
        + " group by e0.customer.id, e0.status", q.toHql().hql());
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `group-many`：

```sql
-- H2, MariaDB, Oracle (same)
select o1_0.customer_id,o1_0.status,count(*)
from orders o1_0
where o1_0.customer_id is not null
group by o1_0.customer_id,o1_0.status
```

键按你书写的顺序使用。
这是唯一允许在 Lambda 内部创建集合的地方。

不允许作为键列表的写法：

| 写法 | 原因 |
|---|---|
| `Set.of(a, b)` | Set 会去除重复且没有顺序 |

### 每个非聚合值都必须是键（规则 J3）

当查询包含聚合时，`select` 和 `having` 中每个**不是**聚合的值都必须是 `groupBy` 的键。这是 SQL 的规则。lambdaQL 会在分析时检查它。

```java
// 错误：o.getStatus() 不是分组键
qb.from(Order.class)
  .groupBy(o -> o.getCustomer().getId())
  .select(o -> new KeyCount(o.getCustomer().getId(), o.getStatus(), Agg.count()));
// → LambdaAnalysisException
```

已测试的示例见 [6.10](#610-rules-j1-to-j8)。

---

<a id="66-having"></a>
## 6.6 Having

`having` 是针对分组的条件。它使用与 `where` 相同的 Lambda 规则（[3. 条件](03-where.md)），并且可以使用 `Agg`。
`having` 可以调用多次。各个条件用 `AND` 连接。

**(已测试)** [`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java)，region `group-by`：

```java
ValueQuery<Stat> q = qb.from(Order.class)
        .groupBy(o -> o.getCustomer().getId())
        .having(o -> Agg.sum(o.getPrice()) > 200)
        .select(o -> new Stat(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice())));
assertEquals("select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id"
        + " having sum(e0.price) > 200.0", q.toHql().hql());
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `group-by`：

```sql
-- H2, MariaDB, Oracle (same)
select o1_0.customer_id,count(*),sum(o1_0.price)
from orders o1_0
group by o1_0.customer_id
having sum(o1_0.price)>200.0
```

这里 `Agg.sum(o.getPrice())` 是 `double`，所以 javac 把它与 `200.0` 比较。该常量保留为字面量（决定 D37）。

---

<a id="67-distinct-paging-and-results"></a>
## 6.7 Distinct、分页与结果

`select(...)` 返回 `ValueQuery`。它有以下方法：

| 方法 | 作用 |
|---|---|
| `distinct()` | `select distinct` |
| `limit(n)`、`offset(n)` | 分页 |
| `toList()` | 执行查询；按顺序返回值 |
| `toSet()` | 用 `select distinct` 执行查询；按顺序返回 `SequencedSet` |
| `first()` | 第一个值，或 `Optional.empty()` |
| `one()` | 唯一的值，或 `Optional.empty()`。有两行或更多行时：`NonUniqueResultException` |
| `toHql()` | HQL 和参数，不执行查询 |

**(已测试)** [`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java)，region `distinct`：

```java
ValueQuery<Double> q = qb.from(Order.class)
        .orderBy((o, by) -> by.desc(o.getPrice()))
        .select(o -> o.getPrice())
        .distinct();
assertEquals("select distinct e0.price from Order e0 order by e0.price desc", q.toHql().hql());
// toSet() always uses select distinct
List<Double> prices = q.toSet().stream().toList();
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `distinct`：

```sql
-- H2, MariaDB, Oracle (same)
select distinct o1_0.price
from orders o1_0
order by o1_0.price desc
```

- 使用 `distinct()` 时，每个排序键都必须是被查询的值（规则 J7）。否则 lambdaQL 会在分析时抛出 `LambdaAnalysisException`。
- 值查询上的 `toSet()` 总是使用 `select distinct`。这里出现重复值是预期的，所以没有数量检查（与实体上的 `toSet()` 不同，见 [2. 入门](02-getting-started.md#toset)）。

**分页。** `limit` 和 `offset` 的用法与 [4. 排序与分页](04-order-paging.md) 中相同。
分页时，lambdaQL 会把 `id` 作为最后一个排序键加上，这样各页之间不会重叠（规则 S9）。
对分组查询或 `distinct` 查询不会加上它，因为 `id` 不在它们的 select 列表中。

```java
ValueQuery<Double> paged = qb.from(Order.class)
        .orderBy((o, by) -> by.desc(o.getPrice()))
        .select(o -> o.getPrice())
        .limit(2).offset(1);
// HQL: select e0.price from Order e0 order by e0.price desc, e0.id asc
```

[`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java) 中的测试方法 `pagingAndFirst` 检查了这个 HQL、`first()` 和这一页的内容。

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `value-page`：

```sql
-- H2, Oracle
select o1_0.price
from orders o1_0
order by o1_0.price desc,o1_0.id
offset ? rows
fetch first ? rows only

-- MariaDB
select o1_0.price
from orders o1_0
order by o1_0.price desc,o1_0.id
limit ?,?
```

---

<a id="68-derived-table-subquery-in-from"></a>
## 6.8 派生表（FROM 中的子查询）

> [!NOTE]
> **已设计，尚未实现**（里程碑 M8-2）。下面的 HQL 是**计划**的输出。

把一个 Lambda 传给 `qb.from`。这个 Lambda 接收子查询工厂（`sub`）并构建内部查询。
内部的 `select` 决定行类型。在外层查询中，像使用实体一样使用这一行。

```java
record CustomerTotal(Long customerId, long cnt, Double total) {}

List<CustomerTotal> rows = qb.from(sub -> sub.from(Order.class)
        .groupBy(o -> o.getCustomer().getId())
        .select(o -> new CustomerTotal(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice()))))
    .where(r -> r.total() > 100)
    .toList();
```

HQL（select 别名 `customerId`、`cnt`、`total` 作为列名，规则 J5）：

```sql
select r.customerId, r.cnt, r.total
from (select o.customer.id as customerId, count(*) as cnt, sum(o.price) as total
      from Order o group by o.customer.id) r
where r.total > 100
```

**关于计划中的 SQL 块。** [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java) 把本节和 6.9 中计划的 HQL 直接交给 Hibernate，并记录在 H2 2.3、MariaDB 10.11 和 Oracle 23.26（Hibernate 6.6.5）上得到的 SQL。lambdaQL 目前还不会生成这些查询。如果 Hibernate 的 SQL 发生变化，测试就会失败。

**各数据库的 SQL（计划中）** — Hibernate 对计划中 HQL 的翻译，由 `PlannedSqlTest` 记录；lambdaQL 目前还不会生成此查询。来自 `expected-sql/` 的 [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)，示例 `06-derived`：

```sql
-- H2
select r1_0.customerId,r1_0.cnt,r1_0.total
from (select o1_0.customer_id,count(*),sum(o1_0.price) from orders o1_0 group by o1_0.customer_id) r1_0(customerId,cnt,total)
where r1_0.total>100

-- MariaDB, Oracle
select r1_0.customerId,r1_0.cnt,r1_0.total
from (select o1_0.customer_id customerId,count(*) cnt,sum(o1_0.price) total from orders o1_0 group by o1_0.customer_id) r1_0
where r1_0.total>100
```

H2 把列清单放在别名上（`r1_0(customerId,cnt,total)`）。MariaDB 和 Oracle 把列别名写在子查询里面。

规则：

- 行类型必须是 **Java record**（规则 J5）。
  lambdaQL 按 record 组件的顺序获取列别名。
- 这里不允许使用普通 DTO 类。lambdaQL 无法知道其构造函数参数的名字。
- 在外层 Lambda 中，用 record 的访问器读取这一行：`r.total()`。

---

<a id="69-cte-with"></a>
## 6.9 CTE（`with`）

> [!NOTE]
> **已设计，尚未实现**（里程碑 M8-2）。下面的 HQL 是**计划**的输出。

用 `qb.with(...)` 创建一个句柄，然后在 `qb.from(...)` 中使用这个句柄。
同一个句柄可以使用多次。

```java
var t = qb.with(sub -> sub.from(Order.class)
        .groupBy(o -> o.getCustomer().getId())
        .select(o -> new CustomerTotal(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice()))));

List<CustomerTotal> rows = qb.from(t).where(r -> r.total() > 100).toList();
```

HQL（select 别名 `customerId`、`cnt`、`total` 作为列名，规则 J5）：

```sql
with t as (select o.customer.id as customerId, count(*) as cnt, sum(o.price) as total
           from Order o group by o.customer.id)
select x.customerId, x.cnt, x.total from t x where x.total > 100
```

**各数据库的 SQL（计划中）** — Hibernate 对计划中 HQL 的翻译，由 `PlannedSqlTest` 记录；lambdaQL 目前还不会生成此查询。来自 `expected-sql/` 的 [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)，示例 `06-cte`：

```sql
-- H2
select x1_0.customerId,x1_0.cnt,x1_0.total
from (select o1_0.customer_id,count(*),sum(o1_0.price) from orders o1_0 group by o1_0.customer_id) x1_0(customerId,cnt,total)
where x1_0.total>100

-- MariaDB, Oracle
with t (customerId,cnt,total) as (select o1_0.customer_id,count(*),sum(o1_0.price) from orders o1_0 group by o1_0.customer_id)
select x1_0.customerId,x1_0.cnt,x1_0.total
from t x1_0
where x1_0.total>100
```

MariaDB 和 Oracle 执行真正的 `with`。列清单由 Hibernate 自己写出：`with t (customerId,cnt,total) as (...)`。H2 把 CTE 变成派生表。

说明：

- lambdaQL 在外层查询中总会给 CTE 一个别名（`from t x`）。HQL 需要它（规则 J6）。
- CTE 的列名由 select 别名（`as customerId`）确定。HQL 不接受 CTE 名称后面的列清单；列清单由 Hibernate 加到 SQL 中（规则 J6）。
- MariaDB 和 Oracle 执行真正的 `with`。在 H2 上，Hibernate 会把它展开为派生表。结果相同（已检查）。
- 行类型规则与派生表相同：record（J5）。
- 请用 `var` 声明句柄。它的类型名尚未最终确定。

---

<a id="610-rules-j1-to-j8"></a>
## 6.10 规则 J1 至 J8

| # | 规则 | 状态 |
|---|---|---|
| J1 | `select` 的 Lambda 返回**一个值表达式**或**一次构造函数调用**。每个构造函数参数都是一个值表达式（规则见 [3. 条件](03-where.md)）。只在 `select` 中使用的关联路径会变成 `left join` 别名，因此不会丢失行。如果 `where` 使用同一条路径，`select` 会使用 `where` 的 join（R24）。 | 已实现（已测试） |
| J2 | 对于构造函数，lambdaQL 只查询列，并在 Java 中调用构造函数。它不会写 `select new`。 | 已实现（已测试） |
| J3 | 有聚合时，`select` 和 `having`（以及 `insert ... select` 的值）中每个非聚合值都必须是 `groupBy` 的键。否则会在分析时报错。 | 已实现（已测试）。目前的错误是 `LambdaAnalysisException`；`TranslationException` 是设计中的（[11](../11-errors-and-languages.md)） |
| J4 | 没有行或所有值都是 NULL 时，`sum`/`avg`/`min`/`max` 返回 NULL，`count` 返回 0。对可能为 NULL 的值请使用装箱类型。NULL 进入基本类型的构造函数参数时，会抛出指出该参数的 `IllegalStateException`。 | 已实现（已测试） |
| J5 | 派生表或 CTE 的行类型：**Java record**（Kotlin 代码也可以使用 data class）。普通 DTO 类只允许用于简单 `select`。 | 已设计（M8-2） |
| J6 | 用 `qb.with(...)` 创建的 CTE 用 select 别名为列命名：`with t as (select o.customer.id as customerId, ...) select x.customerId ... from t x`。HQL 不接受名称后面的列清单：Hibernate 会以 `SyntaxException` 拒绝 `with t (col list) as (...)`。列清单由 Hibernate 自己写进 SQL（在 MariaDB 和 Oracle 上为 `with t (customerId,cnt,total) as (...)`）；H2 把 CTE 变成派生表。外层查询总是为它使用别名（`from t x`）。 | 已设计（M8-2） |
| J7 | `distinct()` 渲染为 `select distinct`。与排序一起使用时，排序键必须在查询列中（数据库的规则）。lambdaQL 会在分析时检查这一点。 | 已实现（已测试） |
| J8 | 查询列可以与排序（[4. 排序与分页](04-order-paging.md)）、分页和子查询（[5. 子查询](05-subquery.md)）一起使用。按聚合排序（`by.desc(s.total())`）是 v0.2 候选。 | 已实现，按聚合排序除外 |

**(已测试)** [`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java)，region `rules`：

```java
// J3: a value without an aggregate must be a group key
assertThrows(LambdaAnalysisException.class, () -> qb.from(Order.class)
        .groupBy(o -> o.getCustomer().getId())
        .select(o -> new KeyCount(o.getCustomer().getId(), o.getStatus(), Agg.count())));
// aggregates belong in having, not in where
assertThrows(LambdaAnalysisException.class, () -> qb.from(Order.class).where(o -> Agg.count() > 1));
// J7: with distinct, the sort key must be selected
assertThrows(LambdaAnalysisException.class, () -> qb.from(Order.class)
        .orderBy((o, by) -> by.asc(o.getId())).select(o -> o.getPrice()).distinct().toHql());
// J4: the group without orders gives NULL; a primitive argument cannot take it
assertThrows(IllegalStateException.class, () -> qb.from(Order.class)
        .where(o -> o.getStatus() == 9)
        .select(o -> new Bad(null, Agg.sum(o.getPrice()))).toList());
```

`Bad` 是 `record Bad(Integer customerId, double total)`。没有状态为 9 的订单，所以 `sum` 是 NULL，而基本类型 `double` 无法接收它。

---

<a id="611-not-in-v01"></a>
## 6.11 v0.1 中不包含的内容

| 项目 | 状态 |
|---|---|
| 按聚合结果或查询列别名排序，例如 `orderBy((s, by) -> by.desc(s.total()))`，以及分组查询上的 `orderBy` | v0.2 候选 |
| 派生表和 CTE（6.8、6.9） | 已设计，尚未实现（里程碑 M8-2） |
| 用户自定义聚合函数和窗口函数 | v0.2 候选 |
| 跨 join 的投影，`select((o, c) -> new OrderView(o.getId(), c.getName(), o.getPrice()))` | 已计划；join 语法尚未最终确定 |

目前本章的错误（错误的分组键、`where` 中的 `Agg`、使用 `distinct` 时排序键未被查询）是 `LambdaAnalysisException`。`[LQL-xxxx]` 代码和 `LambdaQLException` 子类是设计中的内容。见 [11. 错误与语言](../11-errors-and-languages.md)。

---

[Java 路线](README.md) | 上一章：[5. 子查询](05-subquery.md) | 下一章：[7. 更新、插入、删除](07-dml.md) | [本页的 Kotlin 版本](../kotlin/06-select-group.md)
