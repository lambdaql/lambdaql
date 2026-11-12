# 6. 查询列、聚合与分组（Java）

> [!NOTE]
> **状态：已设计，尚未实现。**
> 本章的所有内容都是已确定的设计（决定 D27，里程碑 M9）。
> 该语法已用原型在 H2、MariaDB 和 Oracle 上编译并检查过。
> 目前它在 lambdaQL 中**还不能**运行。目前只有分析核心以及 `where` + `toList()` 可以工作
> （已在 H2、MariaDB 和 Oracle 上测试）。完整状态见 [Java 路线 README](README.md#status)。

[Java 路线](README.md) | [手册首页](../README.md) | 上一章：[5. 子查询](05-subquery.md) | 下一章：[7. 更新、插入、删除](07-dml.md) | [本页的 Kotlin 版本](../kotlin/06-select-group.md)

公共页面：[1. 概述](../01-overview.md) · [11. 错误与语言](../11-errors-and-languages.md)

---

本章说明如何选择查询返回**什么**：

- 每行一个值（`select(o -> o.getPrice())`）
- Java record 或 DTO 类
- 带 `groupBy` 和 `having` 的聚合（`count`、`sum`、`avg` 等）
- FROM 子句中的子查询（派生表）和 CTE（`with`）

## 目录

- [6.1 示例实体与结果类型](#61-example-entities-and-result-types)
- [6.2 查询一个值](#62-select-one-value)
- [6.3 查询 record 或 DTO](#63-select-a-record-or-dto)
- [6.4 聚合函数：`Agg`](#64-aggregate-functions-agg)
- [6.5 Group by](#65-group-by)
- [6.6 Having](#66-having)
- [6.7 派生表（FROM 中的子查询）](#67-derived-table-subquery-in-from)
- [6.8 CTE（`with`）](#68-cte-with)
- [6.9 规则 J1 至 J8](#69-rules-j1-to-j8)
- [6.10 v0.1 中不包含的内容](#610-not-in-v01)

---

<a id="61-example-entities-and-result-types"></a>
## 6.1 示例实体与结果类型

示例使用以下实体（见 [2. 入门](02-getting-started.md#example-entities)）：

| 实体 | 这里用到的字段 |
|---|---|
| `Order` | `id`、`price`、`status`、`active`、`customer`（多对一） |
| `Customer` | `id`、`name`、`grade` |
| `Item` | `order`、`price`、`qty` |

以及以下结果类型：

```java
public record OrderView(Long id, String customerName, Double price) {}
public record Stat(Long customerId, long cnt, Double total) {}
public record Total(long cnt, Double avgPrice) {}
```

> [!TIP]
> 对可能为 NULL 的值使用装箱类型（`Double`、`Long`）。
> 列或像 `sum` 这样的聚合可能是 NULL。基本类型 `double` 的组件无法保存 NULL。
> `count` 永远不会是 NULL，所以 `long cnt` 没有问题（规则 J4）。

---

<a id="62-select-one-value"></a>
## 6.2 查询一个值

`select` 接收一个 Lambda。该 Lambda 返回一个值。结果是这个值的列表。

```java
List<Double> prices = qb.from(Order.class)
    .where(o -> o.getStatus() == 1)
    .select(o -> o.getPrice())
    .toList();
```

HQL：

```sql
select o.price from Order o where o.status = 1
```

**关于 SQL 块。** `…` 是缩短的 select 列表（实体的所有列），`?` 是绑定参数。[`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java) 把本页的 HQL 直接交给 Hibernate，并记录在 H2 2.3、MariaDB 10.11 和 Oracle 23.26（Hibernate 6.6.5）上得到的 SQL。lambdaQL 目前还不会生成这些查询。如果 Hibernate 的 SQL 发生变化，测试就会失败。

**各数据库的 SQL（计划中）** — Hibernate 对计划中 HQL 的翻译，由 `PlannedSqlTest` 记录；lambdaQL 目前还不会生成此查询。来自 `expected-sql/` 的 [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)，示例 `06-select-value`：

```sql
-- H2, MariaDB, Oracle (same)
select o1_0.price
from orders o1_0
where o1_0.status=1
```

关联路径就是一个列，与 `where` 中相同（规则 R24）。在 `select` 中，lambdaQL 会添加一个 `left join`，因此没有客户的订单不会丢失（在 `where` 中，同一条路径默认是内连接）：

```java
.select(o -> o.getCustomer().getName())
// → select c1.name from Order o left join o.customer c1
```

**各数据库的 SQL（计划中）** — Hibernate 对计划中 HQL 的翻译，由 `PlannedSqlTest` 记录；lambdaQL 目前还不会生成此查询。来自 `expected-sql/` 的 [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)，示例 `06-select-path`：

```sql
-- H2, MariaDB, Oracle (same)
select c1_0.name
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
```

如果路径以关联实体的 `@Id` 结尾，就不会有 join。
`o.getCustomer().getId()` 读取的是外键列 `customer_id`。

---

<a id="63-select-a-record-or-dto"></a>
## 6.3 查询 record 或 DTO

在 Lambda 中调用构造函数。每个构造函数参数都是一个值表达式。

```java
// record 或普通 DTO 类
List<OrderView> list = qb.from(Order.class)
    .select(o -> new OrderView(o.getId(), o.getCustomer().getName(), o.getPrice()))
    .toList();
```

HQL（lambdaQL 只查询列，规则 J2）：

```sql
select o.id, c1.name, o.price from Order o left join o.customer c1
```

**各数据库的 SQL（计划中）** — Hibernate 对计划中 HQL 的翻译，由 `PlannedSqlTest` 记录；lambdaQL 目前还不会生成此查询。来自 `expected-sql/` 的 [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)，示例 `06-dto`：

```sql
-- H2, MariaDB, Oracle (same)
select o1_0.id,c1_0.name,o1_0.price
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
```

lambdaQL **不会**写 `select new com.acme.OrderView(...)`。
它只查询列，并请求 Hibernate 返回 `OrderView` 类型的结果。
这样可以避免长类名和嵌套类名带来的问题。

可以返回的内容：

| 结果类型 | 简单 `select` | 派生表或 CTE 的行类型 |
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
- 不能在 `where` 中使用 `Agg`。对聚合的条件请使用 `having`。在 `where` 中使用 `Agg` 会在分析时报错。

### 对所有行聚合（不使用 group by）

```java
List<Total> t = qb.from(Order.class)
    .where(o -> o.getStatus() == 1)
    .select(o -> new Total(Agg.count(), Agg.avg(o.getPrice())))
    .toList();
// → select count(*), avg(o.price) from Order o where o.status = 1
```

**各数据库的 SQL（计划中）** — Hibernate 对计划中 HQL 的翻译，由 `PlannedSqlTest` 记录；lambdaQL 目前还不会生成此查询。来自 `expected-sql/` 的 [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)，示例 `06-aggregate`：

```sql
-- H2, MariaDB, Oracle (same)
select count(*),avg(o1_0.price)
from orders o1_0
where o1_0.status=1
```

### 聚合中的 NULL（规则 J4）

| 函数 | 没有行，或所有值都是 NULL |
|---|---|
| `sum`、`avg`、`min`、`max` | `NULL` |
| `count` | `0` |

这是 SQL 的正常行为。

---

<a id="65-group-by"></a>
## 6.5 Group by

用 `groupBy` 写出分组键。lambdaQL 不会猜测分组键。

### 一个键

```java
List<Stat> stats = qb.from(Order.class)
    .groupBy(o -> o.getCustomer().getId())
    .select(o -> new Stat(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice())))
    .toList();
```

HQL（键以 `@Id` 结尾，所以没有 join）：

```sql
select o.customer.id, count(*), sum(o.price) from Order o group by o.customer.id
```

**各数据库的 SQL（计划中）** — Hibernate 对计划中 HQL 的翻译，由 `PlannedSqlTest` 记录；lambdaQL 目前还不会生成此查询。来自 `expected-sql/` 的 [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)，示例 `06-group-by`：

```sql
-- H2, MariaDB, Oracle (same)
select o1_0.customer_id,count(*),sum(o1_0.price)
from orders o1_0
group by o1_0.customer_id
```

### 多个键

用 `List.of(...)` 返回键的列表：

```java
.groupBy(o -> List.of(o.getCustomer().getId(), o.getStatus()))
// → group by o.customer.id, o.status
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
  .select(o -> new Stat2(o.getCustomer().getId(), o.getStatus(), Agg.count()));   // Stat2：某个 record
// → TranslationException
```

---

<a id="66-having"></a>
## 6.6 Having

`having` 是针对分组的条件。它使用与 `where` 相同的 Lambda 规则（[3. 条件](03-where.md)），并且可以使用 `Agg`。

```java
List<Stat> big = qb.from(Order.class)
    .groupBy(o -> o.getCustomer().getId())
    .having(o -> Agg.sum(o.getPrice()) > 1000)
    .select(o -> new Stat(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice())))
    .toList();
```

HQL：

```sql
select o.customer.id, count(*), sum(o.price)
from Order o
group by o.customer.id
having sum(o.price) > 1000
```

**各数据库的 SQL（计划中）** — Hibernate 对计划中 HQL 的翻译，由 `PlannedSqlTest` 记录；lambdaQL 目前还不会生成此查询。来自 `expected-sql/` 的 [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)，示例 `06-group-having`：

```sql
-- H2, MariaDB, Oracle (same)
select o1_0.customer_id,count(*),sum(o1_0.price)
from orders o1_0
group by o1_0.customer_id
having sum(o1_0.price)>1000
```

`Agg.sum(o.getPrice())` 返回 `Double`。javac 会为 `> 1000` 将它拆箱。这是普通的数值比较（规则 R1）。

---

<a id="67-derived-table-subquery-in-from"></a>
## 6.7 派生表（FROM 中的子查询）

把一个 Lambda 传给 `qb.from`。这个 Lambda 接收子查询工厂（`sub`）并构建内部查询。
内部的 `select` 决定行类型。在外层查询中，像使用实体一样使用这一行。

```java
List<Stat> rows = qb.from(sub -> sub.from(Order.class)
        .groupBy(o -> o.getCustomer().getId())
        .select(o -> new Stat(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice()))))
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

<a id="68-cte-with"></a>
## 6.8 CTE（`with`）

用 `qb.with(...)` 创建一个句柄，然后在 `qb.from(...)` 中使用这个句柄。
同一个句柄可以使用多次。

```java
var t = qb.with(sub -> sub.from(Order.class)
        .groupBy(o -> o.getCustomer().getId())
        .select(o -> new Stat(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice()))));

List<Stat> rows = qb.from(t).where(r -> r.total() > 100).toList();
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

<a id="69-rules-j1-to-j8"></a>
## 6.9 规则 J1 至 J8

| # | 规则 |
|---|---|
| J1 | `select` 的 Lambda 返回**一个值表达式**或**一次构造函数调用**。每个构造函数参数都是一个值表达式（规则见 [3. 条件](03-where.md)）。只在 `select` 中使用的关联路径会变成 `left join` 别名，因此不会丢失行。如果 `where` 使用同一条路径，`select` 会使用 `where` 的 join（R24）。 |
| J2 | 对于构造函数，lambdaQL 只查询列，并以你的类使用 Hibernate 的类型化查询。它不会写 `select new`。 |
| J3 | 有聚合时，`select` 和 `having` 中每个非聚合值都必须是 `groupBy` 的键。否则会在分析时抛出 `TranslationException`。 |
| J4 | 没有行或所有值都是 NULL 时，`sum`/`avg`/`min`/`max` 返回 NULL，`count` 返回 0。对可能为 NULL 的值请使用装箱类型。 |
| J5 | 派生表或 CTE 的行类型：**Java record**（Kotlin 代码也可以使用 data class）。普通 DTO 类只允许用于简单 `select`。 |
| J6 | 用 `qb.with(...)` 创建的 CTE 用 select 别名为列命名：`with t as (select o.customer.id as customerId, ...) select x.customerId ... from t x`。HQL 不接受名称后面的列清单：Hibernate 会以 `SyntaxException` 拒绝 `with t (col list) as (...)`。列清单由 Hibernate 自己写进 SQL（在 MariaDB 和 Oracle 上为 `with t (customerId,cnt,total) as (...)`）；H2 把 CTE 变成派生表。外层查询总是为它使用别名（`from t x`）。 |
| J7 | `distinct()` 渲染为 `select distinct`。与排序一起使用时，排序键必须在查询列中（数据库的规则）。lambdaQL 会在分析时检查这一点。 |
| J8 | 查询列可以与排序（[4. 排序与分页](04-order-paging.md)）、分页和子查询（[5. 子查询](05-subquery.md)）一起使用。 |

---

<a id="610-not-in-v01"></a>
## 6.10 v0.1 中不包含的内容

| 项目 | 状态 |
|---|---|
| 按聚合结果或查询列别名排序，例如 `orderBy((s, by) -> by.desc(s.total()))` | v0.2 候选 |
| 用户自定义聚合函数和窗口函数 | v0.2 候选 |
| 跨 join 的投影，`select((o, c) -> new OrderView(o.getId(), c.getName(), o.getPrice()))` | 已计划；join 语法尚未最终确定 |
| `toList()` 以外的结果方法 | 已计划 |

本章的错误（错误的分组键、`where` 中的 `Agg`、把 DTO 类用作派生表的行）是带有 `[LQL-xxxx]` 代码的 `LambdaQLException` 子类。见 [11. 错误与语言](../11-errors-and-languages.md)。

---

[Java 路线](README.md) | 上一章：[5. 子查询](05-subquery.md) | 下一章：[7. 更新、插入、删除](07-dml.md) | [本页的 Kotlin 版本](../kotlin/06-select-group.md)
