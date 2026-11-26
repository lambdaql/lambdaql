<a id="6-select-aggregates-and-group-by-java"></a>
# 6. 查询列、聚合与分组（Java）

> [!IMPORTANT]
> 本页比韩文手册旧。最新规则请参见[韩文页面](../../ko/java/06-select-group.md)。

[Java 路线](README.md) | [手册首页](../README.md) | 上一章：[5. 子查询](05-subquery.md) | 下一章：[7. 更新、插入、删除](07-dml.md) | [本页的 Kotlin 版本](../kotlin/06-select-group.md)

公共页面：[1. 概述](../01-overview.md) · [11. 错误与语言](../11-errors-and-languages.md)

---

本章说明如何选择查询返回**什么**。

- 每行一个值（`select(o -> o.getPrice())`）
- Java record 或 DTO 类
- 与 `groupBy`、`having` 一起使用的聚合（`count`、`sum`、`avg` 等）
- 值查询的 `distinct`、分页和结果方法
- 与其他实体的显式连接（`join`、`leftJoin`、`crossJoin`）
- FROM 子句中的子查询（派生表）和 CTE（`with`）

<a id="contents"></a>
## 目录

- [6.1 示例实体与结果类型](#61-example-entities-and-result-types)
- [6.2 查询一个值](#62-select-one-value)
- [6.3 查询 record 或 DTO](#63-select-a-record-or-dto)
- [6.4 聚合函数：`Agg`](#64-aggregate-functions-agg)
- [6.5 分组（group by）](#65-group-by)
- [6.6 having](#66-having)
- [6.7 distinct、分页与结果](#67-distinct-paging-and-results)
- [6.8 显式连接](#68-explicit-joins)
- [6.9 派生表（FROM 中的子查询）](#69-derived-table-subquery-in-from)
- [6.10 CTE（`with`）](#610-cte-with)
- [6.11 规则 J1 至 J8](#611-rules-j1-to-j8)
- [6.12 不支持的内容](#612-not-in-v01)

---

<a id="61-example-entities-and-result-types"></a>
## 6.1 示例实体与结果类型

示例使用以下实体（见 [2. 入门](02-getting-started.md#example-entities)）。

| 实体 | 这里使用的字段 |
|---|---|
| `Order` | `id`、`price`、`status`、`active`、`customer`（many-to-one） |
| `Customer` | `id`、`name`、`grade` |
| `Item` | `order`、`price`、`qty` |

`price` 是 `double`，`status` 是 `short`，`Customer.id` 是 `int`。示例数据中有一个没有客户的订单。
本章的示例在 H2、MariaDB、Oracle 上返回的值，与在 Java 中做同样计算的结果相同。

示例使用以下结果类型。

- `record OrderView(Long id, String customerName, double price)`
- `record Stat(Integer customerId, long count, Double total)`
- `record KeyCount(Integer customerId, short status, long count)`
- `record Total(long count, Double average)`

部分示例（[`ManualJavaSelectTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaSelectTest.java)）另行声明并使用同名的 `Stat`、`Total` record。

> [!TIP]
> 可能为 NULL 的值请使用装箱类型（`Double`、`Long`、`Integer`）。
> 列以及 `sum` 这类聚合可能为 NULL。基本类型 `double` 参数无法容纳 NULL，这时 lambdaQL 抛出 `IllegalStateException`（见 [6.4](#null-in-aggregates-rule-j4)）。
> `count` 不会为 NULL，因此可以使用 `long count`。

---

<a id="62-select-one-value"></a>
## 6.2 查询一个值

`select` 接收一个 Lambda 表达式。该 Lambda 表达式返回一个值。结果是该值的 `ValueQuery`。
`select` 可以在 `where` 之后和 `orderBy` 之后调用。排序、`limit`、`offset` 保持不变。

<!-- region: ProjectionTest.java#value -->
```java
ValueQuery<Double> q = qb.from(Order.class)
        .where(o -> o.getStatus() == 1)
        .orderBy(o -> asc(o.getPrice()))
        .select(o -> o.getPrice());
assertEquals("select e0.price from Order e0 where e0.status = 1 order by e0.price asc", q.toHql().hql());
```

**各数据库的 SQL**

<!-- sql: value -->
```sql
-- H2, MariaDB, Oracle (same)
select o1_0.price
from orders o1_0
where o1_0.status=1
order by o1_0.price
```

关联路径与在 `where` 中一样是列。在 `select` 中，lambdaQL 会添加 `left join`，因此没有客户的订单也不会遗漏（在 `where` 中，若没有客户的订单不可能满足条件，同一路径就是 inner join）。
用 `optional = false` 映射的必需关联不会丢失行，因此在 `select` 中也是 inner join（见 [4.5](04-order-paging.md#45-what-a-sort-key-can-be)）。

<!-- region: ManualJavaSelectTest.java#select-path -->
```java
ValueQuery<String> names = qb.from(Order.class).select(o -> o.getCustomer().getName());
// a path used only in select is a left join: the order without a customer gives null
assertEquals("select j1.name from Order e0 left join e0.customer j1", names.toHql().hql());
```

[6.3](#63-select-a-record-or-dto) 的 record 示例也使用这个 `left join`。
路径以关联实体的 `@Id` 结尾时没有连接。
`o.getCustomer().getId()` 读取外键列 `customer_id`。

示例代码：[`ProjectionTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java)（`value`）、[`ManualJavaSelectTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaSelectTest.java)（`select-path`）。

---

<a id="63-select-a-record-or-dto"></a>
## 6.3 查询 record 或 DTO

请在 Lambda 表达式中调用构造函数。构造函数的每个参数都是值表达式。
Java record 和带构造函数的普通 DTO 类都可以使用。

<!-- region: ProjectionTest.java#record -->
```java
// only the columns are selected; the record is made in Java. The path uses a left join,
// so the order without a customer stays (customerName is null)
ValueQuery<OrderView> q = qb.from(Order.class)
        .select(o -> new OrderView(o.getId(), o.getCustomer().getName(), o.getPrice()));
assertEquals("select e0.id, j1.name, e0.price from Order e0 left join e0.customer j1", q.toHql().hql());
```

**各数据库的 SQL**

<!-- sql: record -->
```sql
-- H2, MariaDB, Oracle (same)
select o1_0.id,c1_0.name,o1_0.price
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
```

lambdaQL **不**使用 `select new com.acme.OrderView(...)`。
它查询列，并在 Java 中调用构造函数。
这样可以避免长类名或嵌套类名带来的问题。
lambdaQL 把每个值转换为构造函数参数的类型（例如把 Hibernate 的 `Long` 转为 `Integer` 参数）。

可以返回的内容：

| 结果类型 | 简单 `select` | 派生表或 CTE 的行类型（[6.9](#69-derived-table-subquery-in-from)） |
|---|---|---|
| 一个值（`o.getPrice()`） | 可以 | – |
| Java record | 可以 | 可以 |
| 普通 DTO 类（构造函数） | 可以 | **不可以** |

示例代码：[`ProjectionTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java)（`record`）。

---

<a id="64-aggregate-functions-agg"></a>
## 6.4 聚合函数：`Agg`

聚合函数是 **`org.lambdaql.Agg` 的静态方法**。
它们是 SQL 标准函数，因此由 lambdaQL 提供。（自定义的数据库函数见 [8. 存储过程与数据库函数](08-procedures-functions.md)。）

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

- `Agg.count()`：`long`
- `Agg.sum(x)`：`<N extends Number> N`（与参数类型相同）
- `Agg.avg(x)`：`Double`
- `Agg.min(x)`、`Agg.max(x)`：`<R extends Comparable<?>> R`（与参数类型相同）

要点：

- 返回值只是**占位符**。lambdaQL 不执行 `Agg` 调用，而是把它转换为 SQL 聚合，因此这个值不会被使用。
- 在 lambdaQL Lambda 表达式**之外**调用 `Agg.sum(...)` 会抛出 `IllegalStateException`。
- `Agg` 可以用在 `select`、`having` 以及 `insert ... select` 的值（[7.5](07-5-insert.md)）中。
- `where` 中不能使用 `Agg`。针对聚合的条件请使用 `having`。在 `where` 中使用 `Agg` 会在分析时报错。

<a id="aggregate-over-all-rows-no-group-by"></a>
### 对所有行聚合（不使用 group by）

<!-- region: ProjectionTest.java#total -->
```java
// aggregates without groupBy: one row over all matching orders
ValueQuery<Total> q = qb.from(Order.class)
        .where(o -> o.getStatus() == 1)
        .select(o -> new Total(Agg.count(), Agg.avg(o.getPrice())));
assertEquals("select count(*), avg(e0.price) from Order e0 where e0.status = 1", q.toHql().hql());
```

**各数据库的 SQL**

<!-- sql: total -->
```sql
-- H2, MariaDB, Oracle (same)
select count(*),avg(o1_0.price)
from orders o1_0
where o1_0.status=1
```

该查询总是返回一行。可以用 `q.one()` 读取这一行。

<a id="null-in-aggregates-rule-j4"></a>
### 聚合中的 NULL（规则 J4）

| 函数 | 没有行或所有值都为 NULL 时 |
|---|---|
| `sum`、`avg`、`min`、`max` | `NULL` |
| `count` | `0` |

这是正常的 SQL 行为。
NULL 进入基本类型构造函数参数（`double`、`long`）时，lambdaQL 抛出 `IllegalStateException`。消息中会指出该参数，并提示使用包装类型（`Double`、`Long`）。示例见 [6.11](#611-rules-j1-to-j8)。

示例代码：[`ProjectionTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java)（`total`）。

---

<a id="65-group-by"></a>
## 6.5 分组（group by）

分组键用 `groupBy` 编写。不会根据 `select` 的值自动确定分组键。
`groupBy` 返回分组查询。请先调用 `having(...)`（可选），再调用 `select(...)`。

<a id="one-key"></a>
### 一个键

<!-- region: ManualJavaSelectTest.java#group-one -->
```java
ValueQuery<Stat> q = qb.from(Order.class)
        .groupBy(o -> o.getCustomer().getId())
        .select(o -> new Stat(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice())));
assertEquals("select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id",
        q.toHql().hql());
List<Stat> stats = q.toList();
```

键以 `@Id` 结尾，因此没有连接。没有客户的订单组成一个键为 NULL 的分组。
使用 `having` 的示例见 [6.6](#66-having)。

<a id="many-keys"></a>
### 多个键

请用 `List.of(...)` 返回键列表。

<!-- region: ProjectionTest.java#group-many -->
```java
// many keys: List.of(...) (Kotlin listOf(...), a to b, Triple)
ValueQuery<KeyCount> q = qb.from(Order.class)
        .where(o -> o.getCustomer() != null)
        .groupBy(o -> List.of(o.getCustomer().getId(), o.getStatus()))
        .select(o -> new KeyCount(o.getCustomer().getId(), o.getStatus(), Agg.count()));
assertEquals("select e0.customer.id, e0.status, count(*) from Order e0 where e0.customer is not null"
        + " group by e0.customer.id, e0.status", q.toHql().hql());
```

**各数据库的 SQL**

<!-- sql: group-many -->
```sql
-- H2, MariaDB, Oracle (same)
select o1_0.customer_id,o1_0.status,count(*)
from orders o1_0
where o1_0.customer_id is not null
group by o1_0.customer_id,o1_0.status
```

键按编写的顺序使用。
这是唯一允许在 Lambda 表达式中创建集合的地方。

不允许作为键列表的形式：

| 形式 | 原因 |
|---|---|
| `Set.of(a, b)` | 集合会去重且没有顺序 |

<a id="every-non-aggregate-value-must-be-a-key-rule-j3"></a>
### 每个非聚合值都必须是键（规则 J3）

查询中有聚合时，`select` 和 `having` 中所有**非**聚合值都必须是 `groupBy` 键。这是 SQL 规则。lambdaQL 在分析时检查。

例如，在 `groupBy(o -> o.getCustomer().getId())` 之后，把不是分组键的 `o.getStatus()` 写进 `select`，会产生 `TranslationException`（`LQL-3035`）。

示例见 [6.11](#611-rules-j1-to-j8)。

示例代码：[`ManualJavaSelectTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaSelectTest.java)（`group-one`）、[`ProjectionTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java)（`group-many`）。

---

<a id="66-having"></a>
## 6.6 having

`having` 是针对分组的条件。它使用与 `where` 相同的 Lambda 表达式规则（[3. 条件（`where`）](03-where.md)），并且可以使用 `Agg`。
`having` 可以调用多次。条件用 `AND` 连接。

<!-- region: ProjectionTest.java#group-by -->
```java
ValueQuery<Stat> q = qb.from(Order.class)
        .groupBy(o -> o.getCustomer().getId())
        .having(o -> Agg.sum(o.getPrice()) > 200)
        .select(o -> new Stat(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice())));
assertEquals("select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id"
        + " having sum(e0.price) > 200.0", q.toHql().hql());
```

**各数据库的 SQL**

<!-- sql: group-by -->
```sql
-- H2, MariaDB, Oracle (same)
select o1_0.customer_id,count(*),sum(o1_0.price)
from orders o1_0
group by o1_0.customer_id
having sum(o1_0.price)>200.0
```

这里 `Agg.sum(o.getPrice())` 是 `double`，因此 javac 把它与 `200.0` 比较。常量保持为字面量。

示例代码：[`ProjectionTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java)（`group-by`）。

---

<a id="67-distinct-paging-and-results"></a>
## 6.7 distinct、分页与结果

`select(...)` 返回 `ValueQuery`。`ValueQuery` 的方法如下。

| 方法 | 作用 |
|---|---|
| `distinct()` | `select distinct` |
| `limit(n)`、`offset(n)` | 分页 |
| `toList()` | 执行查询，按顺序返回值 |
| `toSet()` | 以 `select distinct` 执行查询，返回有序的 `SequencedSet` |
| `first()` | 第一个值，或 `Optional.empty()` |
| `one()` | 唯一的值，或 `Optional.empty()`。有 2 行以上时抛出 `NonUniqueResultException` |
| `toHql()` | 不执行查询，返回 HQL 和参数 |

<!-- region: ProjectionTest.java#distinct -->
```java
ValueQuery<Double> q = qb.from(Order.class)
        .orderBy(o -> desc(o.getPrice()))
        .select(o -> o.getPrice())
        .distinct();
assertEquals("select distinct e0.price from Order e0 order by e0.price desc", q.toHql().hql());
// toSet() always uses select distinct
List<Double> prices = q.toSet().stream().toList();
```

**各数据库的 SQL**

<!-- sql: distinct -->
```sql
-- H2, MariaDB, Oracle (same)
select distinct o1_0.price
from orders o1_0
order by o1_0.price desc
```

- 使用 `distinct()` 时，所有排序键都必须是 select 的值。否则 lambdaQL 在分析时抛出 `TranslationException`（`LQL-3046`）。
- 值查询的 `toSet()` 总是使用 `select distinct`。这里值重复是正常的，因此不做大小检查（与实体的 `toSet()` 不同，见 [2. 入门](02-getting-started.md#toset)）。
- 把 `@Lob` 列用作 `distinct` 或 `groupBy` 键时，Oracle 会拒绝（ORA-22848、ORA-22849）。H2 和 MariaDB 会执行。lambdaQL 不修改查询，以 `DEBUG` 级别记录 `LQL-5010`（见 [4.5](04-order-paging.md#45-what-a-sort-key-can-be)）。

**分页。** `limit` 和 `offset` 的行为与 [4. 排序与分页](04-order-paging.md) 相同。
使用分页时，lambdaQL 把 `id` 加为最后一个排序键，因此页面不会重叠。排序键是唯一且不可为 NULL 的列时不添加（见 [4.8](04-order-paging.md#tie-breaker-automatic-id)）。
`first()` 也只读取一行，因此添加同样的辅助排序。排序键值相同的行有多个时，每次都返回 `id` 最小的那行的值。
分组查询和 `distinct` 查询不添加，因为这些查询的 select 列表中没有 `id`。

<!-- region: ManualJavaSelectTest.java#value-page -->
```java
ValueQuery<Double> paged = qb.from(Order.class)
        .orderBy(o -> desc(o.getPrice()))
        .select(o -> o.getPrice())
        .limit(2).offset(1);
// paging adds the id as the last sort key
assertEquals("select e0.price from Order e0 order by e0.price desc, e0.id asc", paged.toHql().hql());
List<Double> page = paged.toList();
```

**各数据库的 SQL**

<!-- sql: value-page -->
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

示例代码：[`ProjectionTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java)（`distinct`）、[`ManualJavaSelectTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaSelectTest.java)（`value-page`）。

---

<a id="68-explicit-joins"></a>
## 6.8 显式连接

关联路径（`o.getCustomer().getName()`）会自动连接 to-one 关联（见 [3.10](03-where.md#310-association-paths)）。
要按自己指定的条件连接其他实体，或无条件连接，请使用 `join`、`leftJoin`、`crossJoin`。

| 方法 | HQL | 行 |
|---|---|---|
| `join(Customer.class, (o, c) -> ...)` | `join Customer e1 on ...` | inner join：只有有匹配的行 |
| `leftJoin(Customer.class, (o, c) -> ...)` | `left join Customer e1 on ...` | 没有匹配的主实体行也保留 |
| `crossJoin(Customer.class)` | `cross join Customer e1` | 所有行的组合。条件写在 `where` 中 |

连接之后的所有 Lambda 表达式都按连接顺序接收所有实体。
即 `where((o, c) -> ...)`、`orderBy((o, c) -> asc(c.getName()).desc(o.getPrice()))`、`select((o, c) -> ...)`。
被连接实体的别名是 `e1`、`e2`。

下面的示例在 H2、MariaDB、Oracle 上返回的行，与在 Java 中做同样计算的结果相同。

<!-- region: JoinQueryTest.java#join -->
```java
SelectJoinQuery<Order, Customer> q = qb.from(Order.class)
        .join(Customer.class, (o, c) -> o.getCustomer().getId() == c.getId())
        .where((o, c) -> c.getGrade() != null && c.getGrade() >= 5);
assertEquals("select e0 from Order e0 join Customer e1 on e0.customer.id = e1.id"
        + " where e1.grade is not null and e1.grade >= 5", q.toHql().hql());
```

**结果是主实体。** 没有 `select` 时，`toList()` 返回 `List<Order>`。
被连接的实体只用于条件、排序和 `select`。
要读取被连接实体的值，请使用 `select((o, c) -> ...)`（见下文）。

<a id="left-join-and-null"></a>
### left join 与 NULL

<!-- region: JoinQueryTest.java#left-join -->
```java
// c is missing for an order without a customer: c == null finds those rows
SelectJoinQuery<Order, Customer> q = qb.from(Order.class)
        .leftJoin(Customer.class, (o, c) -> o.getCustomer().getId() == c.getId())
        .where((o, c) -> c == null || c.getName().equals("Kim"));
assertEquals("select e0 from Order e0 left join Customer e1 on e0.customer.id = e1.id"
        + " where e1.id is null or e1.name = 'Kim'", q.toHql().hql());
```

- `c == null` 表示“没有匹配”。lambdaQL 把它写成 `e1.id is null`（被连接实体的 `@Id`）。
- 在 `leftJoin` 之后，针对被连接实体列的条件遵循 **SQL 语义**。没有匹配时列为 NULL，条件不为真，该行会从结果中排除。
  在 Java 中，对不存在的 `c` 调用 `c.getName()` 会产生 `NullPointerException`，但 lambdaQL 不遵循这种行为。
  要保留没有匹配的行，请像示例那样写 `c == null || ...`。

<a id="sort-and-select"></a>
### 排序与 select

<!-- region: JoinQueryTest.java#sort -->
```java
OrderedJoinQuery<Order, Customer> q = qb.from(Order.class)
        .leftJoin(Customer.class, (o, c) -> o.getCustomer().getId() == c.getId())
        .orderBy((o, c) -> asc(c.getName()).asc(o.getId()));
assertEquals("select e0 from Order e0 left join Customer e1 on e0.customer.id = e1.id"
        + " order by e1.name asc nulls first, e0.id asc", q.toHql().hql());
```

适用 [4.4](04-order-paging.md#44-null-ordering-is-the-same-on-every-database) 的 NULL 排序规则。没有客户的订单 `e1.name` 为 NULL，因此在 `asc` 中排在前面。

<!-- region: JoinQueryTest.java#select -->
```java
ValueQuery<Row> rows = qb.from(Order.class)
        .leftJoin(Customer.class, (o, c) -> o.getCustomer().getId() == c.getId())
        .orderBy((o, c) -> asc(o.getId()))
        .select((o, c) -> new Row(o.getId(), c.getName()));
assertEquals("select e0.id, e1.name from Order e0 left join Customer e1 on e0.customer.id = e1.id"
        + " order by e0.id asc", rows.toHql().hql());
```

`Row` 是 `record Row(Long orderId, String customerName)`。没有客户的订单 `customerName` 为 `null`。
`select` 返回 `ValueQuery`，因此可以使用 `distinct()`、`limit`、`offset` 以及 [6.7](#67-distinct-paging-and-results) 的结果方法。

<a id="select-entities"></a>
### 选择实体

`select` 中也可以使用实体本身。可以选择被连接的实体，也可以把多个实体放进一个 record。

<!-- region: SelectEntityTest.java#select-joined -->
```java
// the customers that have an order, through an explicit join; Hibernate returns each entity once
ValueQuery<Customer> q = qb.from(Order.class)
        .join(Customer.class, (o, c) -> o.getCustomer().getId() == c.getId())
        .select((o, c) -> c);
```

选择一个实体时，Hibernate 对同一个实体只返回一次。HQL 是 `select e1 from Order e0 join Customer e1 on e0.customer.id = e1.id`。

<!-- region: SelectEntityTest.java#select-entities -->
```java
// an order and its customer together; with leftJoin an order without a customer has null
ValueQuery<OrderWithCustomer> q = qb.from(Order.class)
        .leftJoin(Customer.class, (o, c) -> o.getCustomer().getId() == c.getId())
        .select((o, c) -> new OrderWithCustomer(o, c));
```

`OrderWithCustomer` 是 `record OrderWithCustomer(Order order, Customer customer)`。HQL 是 `select e0, e1 from Order e0 left join Customer e1 on e0.customer.id = e1.id`，每个订单一行。

<!-- region: SelectEntityTest.java#select-self -->
```java
// o -> o selects the entity itself: the same rows as toList()
ValueQuery<Order> q = qb.from(Order.class).where(o -> o.getPrice() > 150).select(o -> o);
```

- `select(o -> o)` 与不使用 `select` 直接调用 `toList()` 的结果相同。
- 包含实体的行不能用作派生表或 CTE 的行（`LQL-3071`）。请选择实体的 id 或列。

CTE 中只放值，在外层查询中与实体连接来选择实体即可。

<!-- region: SelectEntityTest.java#select-entity-cte -->
```java
// the CTE has values only (customer id, total); the outer query joins it to the entity and selects the entity
Cte<Total> totals = qb.with(qb.from(Order.class)
        .where(o -> o.getCustomer() != null)
        .groupBy(o -> o.getCustomer().getId())
        .select(o -> new Total(o.getCustomer().getId(), Agg.sum(o.getPrice()))));
ValueQuery<Customer> big = qb.from(Customer.class)
        .join(totals, (c, t) -> t.customerId().equals(c.getId()))
        .where((c, t) -> t.total() > 250)
        .select((c, t) -> c);
```

示例代码：[`SelectEntityTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectEntityTest.java)（`select-joined`、`select-entities`、`select-self`、`select-entity-cte`）。

<a id="three-entities-and-repeated-rows"></a>
### 多个实体与重复的行

最多可以连接 10 个实体（主实体和 9 个被连接实体）。每连接一次，Lambda 表达式接收的实体就增加一个：`(o, c, i) -> ...`。

<!-- region: JoinQueryTest.java#three -->
```java
// orders of a customer with an item of quantity > 10 (an order with two such items is two SQL rows)
SelectJoin3Query<Order, Customer, Item> q = qb.from(Order.class)
        .join(Customer.class, (o, c) -> o.getCustomer().getId() == c.getId())
        .join(Item.class, (o, c, i) -> i.getOrder().getId() == o.getId())
        .where((o, c, i) -> i.getQty() > 10);
assertEquals("select e0 from Order e0 join Customer e1 on e0.customer.id = e1.id"
        + " join Item e2 on e2.order.id = e0.id where e2.qty > 10", q.toHql().hql());
```

连接 10 个实体的示例。

<!-- region: JoinQueryTest.java#ten -->
```java
// up to 10 entities: each lambda gets all of them in join order
SelectJoin10Query<Order, Customer, Item, Order, Order, Order, Order, Order, Order, Order> q = qb.from(Order.class)
        .join(Customer.class, (o, c) -> o.getCustomer().getId() == c.getId())
        .join(Item.class, (o, c, i) -> i.getOrder().getId() == o.getId())
        .join(Order.class, (o, c, i, a) -> a.getId() == o.getId())
        .join(Order.class, (o, c, i, a, b) -> b.getId() == a.getId())
        .join(Order.class, (o, c, i, a, b, d) -> d.getId() == b.getId())
        .join(Order.class, (o, c, i, a, b, d, e) -> e.getId() == d.getId())
        .join(Order.class, (o, c, i, a, b, d, e, f) -> f.getId() == e.getId())
        .join(Order.class, (o, c, i, a, b, d, e, f, g) -> g.getId() == f.getId())
        .leftJoin(Order.class, (o, c, i, a, b, d, e, f, g, h) -> h.getId() == g.getId())
        .where((o, c, i, a, b, d, e, f, g, h) -> i.getQty() > 10 && h.getPrice() > 0);
```

> [!WARNING]
> 有多行满足连接条件时，在 SQL 中**主实体的行会按每个匹配重复**。Hibernate 对同一个实体只返回一次，因此 `toList()` 和 `toSet()` 没有重复。
> 但是 `count()` 和 `limit` 会计入重复的 SQL 行。要统计订单数或准确地填满一页，请用 `exists()` 子查询（[5. 子查询](05-subquery.md)）代替连接，或对连接查询应用 `distinct()`（见 [join 结果的 `distinct()`](#distinct-on-a-join)）。

<a id="cross-join"></a>
### cross join

`crossJoin` 没有连接条件。方法名表明需要所有行的组合。条件请写在 `where` 中。

<!-- region: JoinQueryTest.java#cross-join -->
```java
// every pair of rows; the conditions go in where
SelectJoinQuery<Order, Customer> q = qb.from(Order.class)
        .crossJoin(Customer.class)
        .where((o, c) -> c.getGrade() != null && o.getPrice() > 100);
assertEquals("select e0 from Order e0 cross join Customer e1 where e1.grade is not null and e0.price > 100.0",
        q.toHql().hql());
```

lambdaQL 没有 `from(Order.class, Customer.class)`（逗号连接）。因为遗漏条件时会意外得到所有组合，而且哪个实体是结果也不明确。这类查询请使用 `crossJoin`。

<a id="paging"></a>
### 分页

分页时，lambdaQL 把**所有**实体的 id 追加为最后的排序键。因此页面不会重叠。

<!-- region: JoinQueryTest.java#cross-join-paging -->
```java
// with paging, the id of every entity is added to the order, so pages never overlap
ValueQuery<Row> pairs = qb.from(Order.class)
        .crossJoin(Customer.class)
        .orderBy((o, c) -> desc(o.getPrice()))
        .select((o, c) -> new Row(o.getId(), c.getName()));
assertEquals("select e0.id, e1.name from Order e0 cross join Customer e1 order by e0.price desc, e0.id asc, e1.id asc",
        pairs.limit(5).toHql().hql());
```

<a id="distinct-on-a-join"></a>
### join 结果的 `distinct()`

`SelectJoinQuery.distinct()` 和 `SelectJoin3Query.distinct()` 使主实体的每一行只对应一个 SQL 行。HQL 是 `select distinct e0`，`count()` 是 `count(distinct e0)`。
即使没有 `distinct()`，`toList()` 和 `toSet()` 也对每个主实体只返回一次。要让 `count()` 和 `limit` 统计主实体的数量，需要 `distinct()`。

- 排序键必须是主实体自身的列（`LQL-3046`）。按被连接实体的列或关联路径排序是错误。
- 分页时只追加主实体的 id 作为并列键，不追加被连接实体的 id。
- 在 `distinct()` 之后使用 `select(...)` 时，以 `select distinct` 查询值。
- 请先考虑 `exists` 子查询（[5. 子查询](05-subquery.md)）。它不重复地给出同样的行，而且往往更快。

<!-- region: JoinQueryTest.java#join-distinct -->
```java
// an order with two matching items is two SQL rows; distinct() makes it one row in SQL
SelectJoinQuery<Order, Item> q = qb.from(Order.class)
        .join(Item.class, (o, i) -> i.getOrder().getId() == o.getId())
        .where((o, i) -> i.getQty() > 0)
        .distinct();
assertEquals("select distinct e0 from Order e0 join Item e1 on e1.order.id = e0.id where e1.qty > 0", q.toHql().hql());
// a sort key must be a column of the main entity
OrderedJoinQuery<Order, Item> sorted = q.orderBy((o, i) -> desc(o.getPrice()));
assertEquals("select distinct e0 from Order e0 join Item e1 on e1.order.id = e0.id where e1.qty > 0"
        + " order by e0.price desc", sorted.toHql().hql());
```

不使用 `distinct()` 而使用 `toSet()` 时，也可以按被连接实体的列排序。不过 `count()` 统计的是 SQL 行。

<!-- region: JoinQueryTest.java#join-set -->
```java
SelectJoinQuery<Order, Item> joined = qb.from(Order.class)
        .join(Item.class, (o, i) -> i.getOrder().getId() == o.getId());
// the order with two items is two SQL rows, but Hibernate returns it once: toList() and toSet() have no repeats
SequencedSet<Order> orders = joined.orderBy((o, i) -> desc(i.getQty()).asc(o.getId())).toSet();
// count() and limit count the SQL rows; distinct() makes them count the orders
long rows = joined.count();
long distinctOrders = qb.from(Order.class)
        .join(Item.class, (o, i) -> i.getOrder().getId() == o.getId())
        .distinct()
        .count();
```

<a id="join-a-derived-table-or-cte"></a>
### 派生表与 CTE 的连接

[6.9](#69-derived-table-subquery-in-from) 的派生表和 [6.10](#610-cte-with) 的 CTE 可以像实体一样连接。
形式为 `join(cte, (c, x) -> ...)`、`leftJoin(cte, ...)`、`join(qb.from(...)...select(new Row(...)), ...)`、`leftJoin(...)`，可以作为第二或第三个根使用。

- HQL 是 `with t as (...) ... join t e1 on ...` 或 `join (select ...) e1 on ...`。
- 行类型是 record。访问器（`x.total()`）读取列。
- 在一条语句中两次读取同一个 `Cte` 对象时，定义只写一次，两个连接读取同一个名字：`join t e1 on ... left join t e2 on ...`。不同的 `Cte` 对象各自得到名字，如 `with t as (...), t2 as (...)`（已在三种数据库上实测）。
- 派生行没有 id，因此 `x == null` 是错误（`LQL-3067`）。请像 `x.customerId() == null` 这样比较列。
- Hibernate 7.4（之前的 6.6 也一样）接受实体的 `select distinct`、派生表和 CTE 的 inner/left join、子查询中的实体连接（已在三种数据库上实测）。

<!-- region: JoinQueryTest.java#join-cte -->
```java
// the total of each customer, joined to the customer row
Cte<Total> t = qb.with(qb.from(Order.class)
        .where(o -> o.getCustomer() != null)
        .groupBy(o -> o.getCustomer().getId())
        .select(o -> new Total(o.getCustomer().getId(), Agg.sum(o.getPrice()))));
ValueQuery<NameTotal> rows = qb.from(Customer.class)
        .join(t, (c, x) -> x.customerId().equals(c.getId()))
        .where((c, x) -> x.total() > 100)
        .select((c, x) -> new NameTotal(c.getName(), x.total()));
assertEquals("with t as (select d0.customer.id as customerId, sum(d0.price) as total from Order d0"
        + " where d0.customer is not null group by d0.customer.id)"
        + " select e0.name, e1.total from Customer e0 join t e1 on e1.customerId = e0.id where e1.total > 100.0",
        rows.toHql().hql());
```

<!-- region: JoinQueryTest.java#join-derived -->
```java
// a derived table without a name; a customer without orders has no row in it (left join)
SelectJoinQuery<Customer, Total> small = qb.from(Customer.class)
        .leftJoin(totals(), (c, x) -> x.customerId().equals(c.getId()))
        .where((c, x) -> x.customerId() == null || x.total() < 210);
assertEquals("select e0 from Customer e0 left join (select d0.customer.id as customerId, sum(d0.price) as total"
        + " from Order d0 where d0.customer is not null group by d0.customer.id) e1 on e1.customerId = e0.id"
        + " where e1.customerId is null or e1.total < 210.0", small.toHql().hql());
```

<!-- region: JoinQueryTest.java#cte-reuse -->
```java
// the same CTE read twice is written once
ValueQuery<NameTotal> reused = qb.from(Customer.class)
        .join(t, (c, x) -> x.customerId().equals(c.getId()))
        .leftJoin(t, (c, x, y) -> y.customerId().equals(c.getId()) && y.total() > 210)
        .select((c, x, y) -> new NameTotal(c.getName(), y.total()));
assertEquals("with t as (select d0.customer.id as customerId, sum(d0.price) as total from Order d0"
        + " where d0.customer is not null group by d0.customer.id)"
        + " select e0.name, e2.total from Customer e0 join t e1 on e1.customerId = e0.id"
        + " left join t e2 on e2.customerId = e0.id and e2.total > 210.0", reused.toHql().hql());
```

<a id="what-a-join-query-has"></a>
### join 查询的方法

| 调用之后 | 方法 |
|---|---|
| `join`、`leftJoin`、`crossJoin` | `where`、`join` / `leftJoin` / `crossJoin`（最多 10 个实体，包括派生表和 CTE）、`distinct`、`orderBy`、`select`、`limit`、`toList`、`toSet`、`first`、`count`、`exists`、`toHql` |
| `orderBy((o, c) -> ...)` | `select`、`limit`、`offset`、`toList`、`first` 及其他已排序查询的方法（[4. 排序与分页](04-order-paging.md)） |

子查询中的连接见 [5. 子查询](05-subquery.md#joins-inside-a-subquery)。
不支持：insert 中的连接、11 个以上实体、`SortSpec` 的连接形式。
已决定不为 update 和 delete 添加连接。请使用关联路径或 `sub`。

示例代码：[`JoinQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java)（`join`、`left-join`、`sort`、`select`、`three`、`cross-join`、`cross-join-paging`、`join-distinct`、`join-set`、`join-cte`、`join-derived`、`cte-reuse`、`ten`）。

---

<a id="69-derived-table-subquery-in-from"></a>
## 6.9 派生表（FROM 中的子查询）

用 `select` 创建值查询（record，见 [6.3](#63-select-a-record-or-dto)）后，传给 `qb.from(...)`。
它的行成为外层查询的表。
结果是 `DerivedQuery<R>`，`R` 是行类型。

<!-- region: DerivedQueryTest.java#derived -->
```java
// the rows of a group query are the table of the outer query; r.total() reads the column "total"
DerivedQuery<Stat> q = qb.from(qb.from(Order.class)
                .where(o -> o.getCustomer() != null)
                .groupBy(o -> o.getCustomer().getId())
                .select(o -> new Stat(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice()))))
        .where(r -> r.total() > 100)
        .orderBy(r -> desc(r.total()));
assertEquals("select e0.customerId, e0.cnt, e0.total from (select d0.customer.id as customerId, count(*) as cnt,"
        + " sum(d0.price) as total from Order d0 where d0.customer is not null group by d0.customer.id) e0"
        + " where e0.total > 100.0 order by e0.total desc nulls last", q.toHql().hql());
```

此示例中 `Stat` 是 `record Stat(Integer customerId, Long cnt, Double total)`。
结果行和 `count()` 在 H2、MariaDB、Oracle 上与在 Java 中做同样计算的结果相同。

- 内层查询的别名是 `d0`（连接为 `dj1` 等），外层查询的别名是 `e0`。
- 每个 record 组件确定一个列名：`as customerId`、`as cnt`、`as total`。
- 在外层 Lambda 表达式中用 record 访问器读取列。`r.total()` 即 `e0.total`。
- 外层查询有 `where`、`orderBy`、`limit`、`offset`、`select`、`toList`、`first`、`count`、`toHql`。

也可以 select 行中的一个值。内层和外层的捕获值按顺序绑定。

<!-- region: DerivedQueryTest.java#select -->
```java
// a value of the row; captured values inside and outside are bound in order
ValueQuery<Integer> ids = qb.from(qb.from(Order.class)
                .where(o -> o.getCustomer() != null && o.getPrice() > min)
                .groupBy(o -> o.getCustomer().getId())
                .select(o -> new Stat(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice()))))
        .where(r -> r.total() > min * 2)
        .select(r -> r.customerId());
```

内层查询的 `limit` 和 `offset` 保留在内层查询中（HQL `limit`、`offset`）。因此可以对前几行重新排序。

<!-- region: DerivedQueryTest.java#inner-limit -->
```java
// the three most expensive orders, then the cheapest of them
Optional<Priced> cheapestOfTop = qb.from(qb.from(Order.class)
                .orderBy(o -> desc(o.getPrice()))
                .limit(3)
                .select(o -> new Priced(o.getId(), o.getPrice())))
        .orderBy(r -> asc(r.price()))
        .first();
```

`Priced` 是 `record Priced(Long id, Double price)`。

Hibernate 7.4 给派生表的列起 `c0`、`c1`、`c2` 这样的名字。H2 把列列表附在别名上（`r1_0(c0,c1,c2)`）。MariaDB 和 Oracle 在子查询内写列别名。

规则：

- 行类型必须是 **Java record**（也可以是 Kotlin data class）。
  lambdaQL 按顺序从 record 组件获取列名。
- 普通 DTO 类或单个值不能在这里使用，因为 lambdaQL 无法确定列名。这时会产生 `TranslationException`（`LQL-3064`）。

<!-- region: DerivedQueryTest.java#rules -->
```java
// the row type must be a record or Kotlin data class (its components name the columns)
assertEquals("LQL-3064", assertThrows(LambdaQLException.class,
        () -> qb.from(qb.from(Order.class).select(o -> o.getPrice()))).code());
```

在实体查询中连接派生表的形式见[派生表与 CTE 的连接](#join-a-derived-table-or-cte)。

不支持：派生表之间的连接，update、delete、insert 中的派生表。

实体的 `@SQLRestriction` 和 `@SoftDelete` 条件，Hibernate 也会附加到实体连接的 `on` 和派生表内部（已在 MariaDB 10.11、Oracle 23.26 上实测）。lambdaQL 不单独处理这些条件。

示例代码：[`DerivedQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DerivedQueryTest.java)（`derived`、`select`、`inner-limit`、`rules`）。

---

<a id="610-cte-with"></a>
## 6.10 CTE（`with`）

用 `qb.with(valueQuery)` 创建 `Cte<R>` 类型的句柄，然后用 `qb.from(handle)` 读取。
内层查询与派生表（6.9）相同。

<!-- region: DerivedQueryTest.java#cte -->
```java
Cte<Stat> t = qb.with(perCustomer());
DerivedQuery<Stat> q = qb.from(t).where(r -> r.cnt() >= 2);
assertEquals("with t as (select d0.customer.id as customerId, count(*) as cnt, sum(d0.price) as total"
        + " from Order d0 where d0.customer is not null group by d0.customer.id)"
        + " select e0.customerId, e0.cnt, e0.total from t e0 where e0.cnt >= 2L", q.toHql().hql());
```

`perCustomer()` 是示例中的辅助方法。它返回与 [6.9](#69-derived-table-subquery-in-from) 的内层查询（把有客户的订单按客户 id 分组并生成 `Stat` 的分组查询）相同的值查询。

MariaDB 和 Oracle 执行真正的 `with`。列列表由 Hibernate 直接写出：`with t (customerId,cnt,total) as (...)`。H2 把 CTE 转换为派生表。

说明：

- CTE 的名字是 `t`。外层查询总是加上别名 `e0`（`from t e0`）。HQL 中需要别名。
- CTE 的列名由 select 别名（`as customerId`）确定。HQL 不接受 CTE 名字后面的列列表，列列表由 Hibernate 添加到 SQL 中。
- MariaDB 和 Oracle 执行实际的 `with`。在 H2 上，Hibernate 把它展开为派生表。结果相同。
- 行类型规则与派生表相同：record。
- 在实体查询中连接 CTE 的形式见[派生表与 CTE 的连接](#join-a-derived-table-or-cte)。
- 同一个 CTE 可以在一条查询中读取两次。定义只写一次（见[派生表与 CTE 的连接](#join-a-derived-table-or-cte)）。
- 不支持 update、delete、insert 中的 CTE。

示例代码：[`DerivedQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DerivedQueryTest.java)（`cte`）。

---

<a id="611-rules-j1-to-j8"></a>
## 6.11 规则 J1 至 J8

| # | 规则 | 状态 |
|---|---|---|
| J1 | `select` Lambda 表达式返回**一个值表达式**或**一个构造函数调用**。构造函数的每个参数都是值表达式（[3. 条件（`where`）](03-where.md) 的规则）。实体本身也可以作为值（[选择实体](#select-entities)）。选择值的三元运算符会变成 `case when`（[3.5.1](03-where.md#351-values-chosen-by-a-condition)）。只用在 `select` 中的关联路径成为 `left join` 别名，因此不会丢失行。必需关联（`optional = false`）是 inner join（A4）。同一路径在 `where` 中使用时，`select` 使用 `where` 的连接（R24）。 | 已实现 |
| J2 | 对于构造函数，lambdaQL 只查询列，在 Java 中调用构造函数。不使用 `select new`。 | 已实现 |
| J3 | 有聚合时，`select` 和 `having`（以及 `insert ... select` 的值）中所有非聚合值都必须是 `groupBy` 键。否则在分析时报错。 | 已实现。错误为 `TranslationException`（`LQL-3035`，见 [11](../11-errors-and-languages.md)） |
| J4 | `sum`/`avg`/`min`/`max` 在没有行或所有值都为 NULL 时返回 NULL。`count` 返回 0。可能为 NULL 的值请使用装箱类型。NULL 进入基本类型构造函数参数时，会产生指出该参数的 `IllegalStateException`。 | 已实现 |
| J5 | 派生表或 CTE 的行类型：**Java record**（Kotlin 代码也可以使用 data class）。普通 DTO 类只在简单 `select` 中允许，否则为 `LQL-3064`。也适用于被连接的派生表和 CTE 的行。派生行不能与 `null` 比较（`LQL-3067`）。 | 已实现 |
| J6 | 用 `qb.with(...)` 创建的 CTE 以 select 别名确定列名：`with t as (select d0.customer.id as customerId, ...) select e0.customerId ... from t e0`。HQL 不接受名字后面的列列表，Hibernate 以 `SyntaxException` 拒绝 `with t (col list) as (...)`。列列表由 Hibernate 直接写入 SQL（在 MariaDB 和 Oracle 上为 `with t (customerId,cnt,total) as (...)`）。H2 把 CTE 转换为派生表。外层查询总是使用别名（`from t e0`）。 | 已实现 |
| J7 | `distinct()` 渲染 `select distinct`。与排序一起使用时，排序键必须在 select 列表中（数据库规则）。lambdaQL 在分析时检查。对 join 结果使用 `distinct()` 时，排序键必须是主实体自身的列。 | 已实现 |
| J8 | 查询可与排序（[4. 排序与分页](04-order-paging.md)）、分页、子查询（[5. 子查询](05-subquery.md)）一起使用。不支持按聚合排序（`desc(s.total())`）。 | 已实现。按聚合排序除外 |

<!-- region: ProjectionTest.java#rules -->
```java
// a value without an aggregate must be a group key
LambdaQLException j3 = assertThrows(LambdaQLException.class, () -> qb.from(Order.class)
        .groupBy(o -> o.getCustomer().getId())
        .select(o -> new KeyCount(o.getCustomer().getId(), o.getStatus(), Agg.count())));
assertEquals("LQL-3035", j3.code());
// aggregates belong in having, not in where
LambdaQLException where = assertThrows(LambdaQLException.class, () -> qb.from(Order.class).where(o -> Agg.count() > 1));
assertEquals("LQL-3005", where.code());
// with distinct, the sort key must be selected
LambdaQLException j7 = assertThrows(LambdaQLException.class, () -> qb.from(Order.class)
        .orderBy(o -> asc(o.getId())).select(o -> o.getPrice()).distinct().toHql());
assertEquals("LQL-3046", j7.code());
// the group without orders gives NULL; a primitive argument cannot take it
assertThrows(IllegalStateException.class, () -> qb.from(Order.class)
        .where(o -> o.getStatus() == 9)
        .select(o -> new Bad(null, Agg.sum(o.getPrice()))).toList());
```

`Bad` 是 `record Bad(Integer customerId, double total)`。没有 status 为 9 的订单，因此 `sum` 为 NULL，基本类型 `double` 无法接收这个值。

示例代码：[`ProjectionTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java)（`rules`）。

---

<a id="612-not-in-v01"></a>
## 6.12 不支持的内容

| 项目 | 状态 |
|---|---|
| 按聚合结果或 select 别名排序（例如 `orderBy(s -> desc(s.total()))`）、分组查询的 `orderBy` | 不支持 |
| 用户自定义聚合函数和窗口函数 | 不支持 |
| insert 中的连接、11 个以上实体、子查询中 3 个以上实体 | 不支持 |
| update、delete 中的连接 | 不添加。使用关联路径或 `sub` |
| DML 中的派生表和 CTE | 不支持 |

本章的错误是带代码的 `TranslationException`。错误的分组键为 `LQL-3035`，`where` 中的 `Agg` 为 `LQL-3005`，`distinct` 中未 select 的排序键为 `LQL-3046`，派生表或 CTE 的行类型为 `LQL-3064`。见 [11. 错误与语言](../11-errors-and-languages.md)。

---

[Java 路线](README.md) | 上一章：[5. 子查询](05-subquery.md) | 下一章：[7. 更新、插入、删除](07-dml.md) | [本页的 Kotlin 版本](../kotlin/06-select-group.md)
