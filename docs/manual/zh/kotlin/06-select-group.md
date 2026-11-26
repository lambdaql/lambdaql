<a id="6-select-aggregates-and-group-by-kotlin"></a>
# 6. 查询列、聚合与分组（Kotlin）

[Kotlin 路线](README.md) | [手册首页](../README.md) | 上一章：[5. 子查询](05-subquery.md) | 下一章：[7. 更新、插入、删除](07-dml.md) | [本页的 Java 版本](../java/06-select-group.md)

公共页面：[1. 概述](../01-overview.md) · [11. 错误与语言](../11-errors-and-languages.md)

---

本章说明如何选择查询返回**什么**。

- 每行一个值（`select { it.price }`）
- Kotlin data class、`Pair`、`Triple`
- 与 `groupBy`、`having` 一起使用的聚合（`count`、`sum`、`avg` 等）
- 值查询的 `distinct`、分页和结果函数
- 与其他实体的显式 join（`join`、`leftJoin`、`crossJoin`）
- FROM 子句中的子查询（派生表）和 CTE（`with`）

<a id="contents"></a>
## 目录

- [6.1 示例实体与结果类型](#61-example-entities-and-result-types)
- [6.2 查询一个值](#62-select-one-value)
- [6.3 查询 data class、Pair、Triple](#63-select-a-data-class-pair-or-triple)
- [6.4 聚合函数：`Agg`](#64-aggregate-functions-agg)
- [6.5 分组（group by）](#65-group-by)
- [6.6 having](#66-having)
- [6.7 distinct、分页与结果](#67-distinct-paging-and-results)
- [6.8 显式 join](#68-explicit-joins)
- [6.9 派生表（FROM 中的子查询）](#69-derived-table-subquery-in-from)
- [6.10 CTE（`with`）](#610-cte-with)
- [6.11 规则 J1 至 J8](#611-rules-j1-to-j8)
- [6.12 v0.1 中不包含的内容](#612-not-in-v01)

---

<a id="61-example-entities-and-result-types"></a>
## 6.1 示例实体与结果类型

示例使用以下实体（见 [2. 入门](02-getting-started.md#example-entities)）。

| 实体 | 这里使用的属性 |
|---|---|
| `Order` | `id`、`price`、`status`、`active`、`customer`（多对一） |
| `Customer` | `id`、`name`、`grade` |
| `Item` | `order`、`price`、`qty` |

示例使用用 Java 编写的示例实体（`price` 是基本类型 `double`，`status` 是 `short`，`Customer.id` 是 `int`）和以下结果类型。

<!-- region: ManualKotlinSelectTest.kt#result-types -->
```kotlin
data class OrderView(val id: Long?, val customerName: String?, val price: Double?)
data class Stat(val customerId: Int?, val count: Long, val total: Double?)
data class KeyCount(val customerId: Int?, val status: Short?, val count: Long)
data class Total(val count: Long, val average: Double?)
```

Kotlin 严格检查类型，因此构造函数参数的类型要与实体属性的类型一致（`Customer.id` 为 `Int?`，`status` 为 `Short?`）。

> [!TIP]
> 结果请使用可空类型（`Double?`、`String?`）。
> 列或像 `sum` 这样的聚合可能为 NULL。非空的 `Double` 或 `Long` 参数是 JVM 基本类型，非空的 `String` 参数同样不接受 NULL。
> 如果 NULL 被传入这样的参数，lambdaQL 会抛出 `IllegalStateException`。对于基本类型，消息会指出是哪个参数，并提示改用包装类型。
> `count` 不会返回 NULL，因此 `count: Long` 没有问题。

示例代码：[`ManualKotlinSelectTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSelectTest.kt)（`result-types`）。

---

<a id="62-select-one-value"></a>
## 6.2 查询一个值

`select` 接收一个 Lambda 表达式。该 Lambda 表达式返回一个值。结果是这个值的 `ValueQuery`。
可以在 `where` 之后和 `orderBy` 之后调用 `select`。排序、`limit` 和 `offset` 会保留。

<!-- region: ManualKotlinSelectTest.kt#value -->
```kotlin
val q = qb.from(Order::class.java)
    .where { it.status.toInt() == 1 }
    .orderBy { o -> asc(o.price) }
    .select { it.price }
assertEquals("select e0.price from Order e0 where e0.status = 1 order by e0.price asc", q.toHql().hql())
val prices: List<Double> = q.toList()
```

`price` 不会是 NULL，因此排序没有 `nulls` 部分。如果是可能为 NULL 的列，会带上 `nulls first`（见 [4.4](04-order-paging.md#44-null-ordering-is-the-same-on-every-database)）。

**各数据库的 SQL**

<!-- sql: value -->
```sql
-- H2, MariaDB, Oracle (same)
select o1_0.price
from orders o1_0
where o1_0.status=1
order by o1_0.price
```

关联路径与 `where` 中一样是一个列。在 `select` 中，lambdaQL 会添加 `left join`，因此没有客户的订单不会丢失（在 `where` 中，如果没有客户的订单不可能符合条件，同一条路径就是内连接）。
用 `optional = false` 映射的必需关联不会丢失行，因此在 `select` 中也是内连接（见 [4.5](04-order-paging.md#45-what-a-sort-key-can-be)）。

<!-- region: ManualKotlinSelectTest.kt#value-path -->
```kotlin
// a path in select is a left join: the order without a customer stays (its value is null)
val names = qb.from(Order::class.java).select { it.customer?.name }
assertEquals("select j1.name from Order e0 left join e0.customer j1", names.toHql().hql())
// !! gives the same query
assertEquals(names.toHql().hql(), qb.from(Order::class.java).select { it.customer!!.name }.toHql().hql())
// a path that ends with the @Id reads the foreign key column: no join
assertEquals("select e0.customer.id from Order e0", qb.from(Order::class.java).select { it.customer?.id }.toHql().hql())
```

`it.customer!!.name` 也生成相同的 HQL。[6.3](#63-select-a-data-class-pair-or-triple) 的 data class 示例中也能看到这个 `left join`。
如果路径以关联实体的 `@Id` 结尾，就没有 join。`it.customer?.id` 读取外键列 `customer_id`。

**Elvis 运算符。** 非 boolean 列上的 `?:` 会成为 `coalesce`。`?.` 路径之后的 `?:`（`it.customer?.name ?: "none"`）会被读作条件，并以 `LQL-2022` 被拒绝。

<!-- region: ManualKotlinSelectTest.kt#value-elvis -->
```kotlin
// ?: on a column that is not a boolean: coalesce
val q = qb.from(Order::class.java).select { it.description ?: "" }
assertEquals("select coalesce(e0.description, '') from Order e0", q.toHql().hql())
// ?: after a ?. path is rejected (LQL-2022)
val afterPath = assertThrows(LambdaQLException::class.java) {
    qb.from(Order::class.java).select { it.customer?.name ?: "none" }.toHql()
}
assertEquals("LQL-2022", afterPath.code())
```

示例代码：[`ManualKotlinSelectTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSelectTest.kt)（`value`、`value-path`、`value-elvis`）。

---

<a id="63-select-a-data-class-pair-or-triple"></a>
## 6.3 查询 data class、Pair、Triple

请在 Lambda 表达式中调用构造函数。每个构造函数参数都是一个值表达式。

<!-- region: ManualKotlinSelectTest.kt#data-class -->
```kotlin
// only the columns are selected; lambdaQL calls the constructor
val views = qb.from(Order::class.java).select { OrderView(it.id, it.customer?.name, it.price) }
assertEquals("select e0.id, j1.name, e0.price from Order e0 left join e0.customer j1", views.toHql().hql())
// Pair (a to b) and Triple
val pairs = qb.from(Order::class.java).select { it.customer?.name to it.price }
assertEquals("select j1.name, e0.price from Order e0 left join e0.customer j1", pairs.toHql().hql())
val triples = qb.from(Order::class.java).select { Triple(it.id, it.status, it.isActive) }
assertEquals("select e0.id, e0.status, e0.active from Order e0", triples.toHql().hql())
```

**各数据库的 SQL**

<!-- sql: record -->
```sql
-- H2, MariaDB, Oracle (same)
select o1_0.id,c1_0.name,o1_0.price
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
```

lambdaQL **不会**写 `select new com.acme.OrderView(...)`。
它查询列，并由 lambdaQL 自己调用构造函数。data class、`Pair` 和 `Triple` 都是这样。
这样可以避免长类名或嵌套类名带来的问题。上面示例中的 `OrderView` 也是嵌套在其他类中的 data class。

可以返回的内容：

| 结果类型 | 简单的 `select` | 派生表或 CTE 的行类型（[6.9](#69-derived-table-subquery-in-from)） |
|---|---|---|
| 一个值（`it.price`） | 是 | – |
| Kotlin data class | 是 | 是 |
| 带构造函数的普通类 | 是 | **否** |
| `Pair` / `Triple` | 是 | 否 |
| Java record（来自 Java 模块） | 是 | 是 |

示例代码：[`ManualKotlinSelectTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSelectTest.kt)（`data-class`）。

---

<a id="64-aggregate-functions-agg"></a>
## 6.4 聚合函数：`Agg`

聚合函数是 **`org.lambdaql.Agg` 的静态方法**。
它们是 SQL 标准函数，因此由 lambdaQL 提供。（自己创建的数据库函数见 [8. 存储过程与数据库函数](08-procedures-functions.md)。）

| 函数 | SQL |
|---|---|
| `Agg.count()` | `count(*)` |
| `Agg.count(x)` | `count(x)` |
| `Agg.countDistinct(x)` | `count(distinct x)` |
| `Agg.sum(x)` | `sum(x)` |
| `Agg.avg(x)` | `avg(x)` |
| `Agg.min(x)` | `min(x)` |
| `Agg.max(x)` | `max(x)` |

在 Kotlin 中看到的类型：

| 函数 | 返回 |
|---|---|
| `count()`、`count(x)`、`countDistinct(x)` | `Long` |
| `sum(x)` | `x` 的类型（平台类型） |
| `avg(x)` | `Double`（在 SQL 中可能为 NULL，请当作 `Double?` 处理） |
| `min(x)`、`max(x)` | `x` 的类型（任何 `Comparable`，可空也可以） |

`Agg.sum` 不会与 `xs.sum()` 这样的 Kotlin 标准库扩展冲突。两个 `sum` 可以在同一个文件中一起使用。

要点：

- 返回值只是一个**占位符**。lambdaQL 不调用 Lambda 表达式，而是分析字节 opcode，因此这个值不会被使用。
- 如果在 lambdaQL Lambda 表达式**外部**调用 `Agg.sum(...)`，会抛出 `IllegalStateException`。
- `Agg` 可以用在 `select`、`having` 以及 `insert ... select` 的值中（[7.5](07-5-insert.md)）。
- 不能在 `where` 中使用 `Agg`。对聚合的条件请使用 `having`。在 `where` 中使用 `Agg` 会在分析时报错。

<a id="aggregate-over-all-rows-no-group-by"></a>
### 对所有行聚合（没有 group by）

<!-- region: ManualKotlinSelectTest.kt#total -->
```kotlin
// aggregates without groupBy: always one row
val q = qb.from(Order::class.java)
    .where { it.status.toInt() == 1 }
    .select { Total(Agg.count(), Agg.avg(it.price)) }
assertEquals("select count(*), avg(e0.price) from Order e0 where e0.status = 1", q.toHql().hql())
val t: Total? = q.one().orElse(null)
```

**各数据库的 SQL**

<!-- sql: total -->
```sql
-- H2, MariaDB, Oracle (same)
select count(*),avg(o1_0.price)
from orders o1_0
where o1_0.status=1
```

这个查询总是返回一行。

<a id="null-in-aggregates-rule-j4"></a>
### 聚合中的 NULL（规则 J4）

| 函数 | 没有行，或所有值都是 NULL 时 |
|---|---|
| `sum`、`avg`、`min`、`max` | `NULL` |
| `count` | `0` |

这是 SQL 的正常行为。这些结果请使用可空参数（`Double?`）（见 [6.1](#61-example-entities-and-result-types) 中的提示）。

示例代码：[`ManualKotlinSelectTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSelectTest.kt)（`total`）。

---

<a id="65-group-by"></a>
## 6.5 分组（group by）

分组键用 `groupBy` 编写。不会根据 `select` 的值自动确定分组键。
`groupBy` 返回分组查询。请先调用 `having { }`（可选），再调用 `select { }`。

<a id="one-key"></a>
### 一个键

<!-- region: ManualKotlinSelectTest.kt#group-one -->
```kotlin
// the key ends with the @Id: no join. The orders without a customer are one group with the key NULL
val q = qb.from(Order::class.java)
    .groupBy { it.customer?.id }
    .select { Stat(it.customer?.id, Agg.count(), Agg.sum(it.price)) }
assertEquals("select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id", q.toHql().hql())
```

键以 `@Id` 结尾，所以没有 join。没有客户的订单组成一个键为 NULL 的分组。
使用 `having` 的示例见 [6.6](#66-having)。

<a id="many-keys"></a>
### 多个键

请用 `listOf(...)` 返回键的列表，或使用 `Pair`（`a to b`）或 `Triple`。

<!-- region: ManualKotlinSelectTest.kt#group-many -->
```kotlin
val q = qb.from(Order::class.java)
    .where { it.customer != null }
    .groupBy { listOf(it.customer?.id, it.status) }
    .select { KeyCount(it.customer?.id, it.status, Agg.count()) }
assertEquals("select e0.customer.id, e0.status, count(*) from Order e0 where e0.customer is not null"
    + " group by e0.customer.id, e0.status", q.toHql().hql())
// a Pair (a to b) gives the same keys; a Triple gives three
val pair = qb.from(Order::class.java)
    .where { it.customer != null }
    .groupBy { it.customer?.id to it.status }
    .select { KeyCount(it.customer?.id, it.status, Agg.count()) }
assertEquals(q.toHql().hql(), pair.toHql().hql())
val three = qb.from(Order::class.java)
    .groupBy { Triple(it.customer?.id, it.status, it.isActive) }
    .select { Triple(it.customer?.id, it.status, Agg.count()) }
assertEquals("select e0.customer.id, e0.status, count(*) from Order e0 group by e0.customer.id, e0.status, e0.active",
    three.toHql().hql())
```

**各数据库的 SQL（第一个查询）**

<!-- sql: group-many -->
```sql
-- H2, MariaDB, Oracle (same)
select o1_0.customer_id,o1_0.status,count(*)
from orders o1_0
where o1_0.customer_id is not null
group by o1_0.customer_id,o1_0.status
```

键按书写的顺序使用。
`groupBy` 中的 `listOf(...)` 是**唯一**允许在 Lambda 表达式内部创建列表的地方。

不允许作为键列表的写法：

| 写法 | 原因 |
|---|---|
| `setOf(a, b)` | Set 会去除重复且没有顺序 |

<a id="every-non-aggregate-value-must-be-a-key-rule-j3"></a>
### 每个非聚合值都必须是键（规则 J3）

当查询包含聚合时，`select` 和 `having` 中每个**不是**聚合的值都必须是 `groupBy` 的键。这是 SQL 的规则。lambdaQL 会在分析时检查它。

<!-- region: ManualKotlinSelectTest.kt#group-key-error -->
```kotlin
// status is not a group key
val e = assertThrows(LambdaQLException::class.java) {
    qb.from(Order::class.java)
        .groupBy { it.customer?.id }
        .select { KeyCount(it.customer?.id, it.status, Agg.count()) }
}
assertEquals("LQL-3035", e.code())
```

错误在调用 `select` 时发生。其他规则检查见 [6.11](#611-rules-j1-to-j8)。

示例代码：[`ManualKotlinSelectTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSelectTest.kt)（`group-one`、`group-many`、`group-key-error`）。

---

<a id="66-having"></a>
## 6.6 having

`having` 是针对分组的条件。它使用与 `where` 相同的 Lambda 表达式规则（[3. 条件（`where`）](03-where.md)），并且可以使用 `Agg`。
`having` 可以调用多次。各个条件用 `AND` 连接。

<!-- region: ManualKotlinSelectTest.kt#having -->
```kotlin
// sum is imported (import org.lambdaql.Agg.sum); having can be called more than once (AND)
val q = qb.from(Order::class.java)
    .groupBy { it.customer?.id }
    .having { sum(it.price) > 200 }
    .having { Agg.count() >= 2 }
    .select { Stat(it.customer?.id, Agg.count(), sum(it.price)) }
assertEquals("select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id"
    + " having sum(e0.price) > 200.0 and count(*) >= 2L", q.toHql().hql())
```

`Agg.sum` 是 Java 方法，所以它的结果是平台类型。因此 `sum(it.price) > 200` 不加 `!!` 也能编译。即使加上 `!!`，lambdaQL 也会忽略它。

示例代码：[`ManualKotlinSelectTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSelectTest.kt)（`having`）。

---

<a id="67-distinct-paging-and-results"></a>
## 6.7 distinct、分页与结果

`select { }` 返回 `ValueQuery`。`ValueQuery` 有以下函数。

| 函数 | 作用 |
|---|---|
| `distinct()` | `select distinct` |
| `limit(n)`、`offset(n)` | 分页 |
| `toList()` | 执行查询，按顺序返回值 |
| `toSet()` | 用 `select distinct` 执行查询，返回有顺序的 `SequencedSet` |
| `first()` | 第一个值（Java `Optional`） |
| `one()` | 唯一的值（Java `Optional`）。有两行或更多行时为 `NonUniqueResultException` |
| `toHql()` | 不执行查询，返回 HQL 和参数 |

<!-- region: ManualKotlinSelectTest.kt#distinct -->
```kotlin
val q = qb.from(Order::class.java)
    .orderBy { o -> desc(o.price) }
    .select { it.price }
    .distinct()
assertEquals("select distinct e0.price from Order e0 order by e0.price desc", q.toHql().hql())
// toSet() always uses select distinct
val prices = q.toSet()
```

**各数据库的 SQL**

<!-- sql: distinct -->
```sql
-- H2, MariaDB, Oracle (same)
select distinct o1_0.price
from orders o1_0
order by o1_0.price desc
```

- 使用 `distinct()` 时，每个排序键都必须是 select 的值。否则 lambdaQL 会在分析时抛出 `TranslationException`（`LQL-3046`）（见 [6.11](#611-rules-j1-to-j8)）。
- 值查询上的 `toSet()` 总是使用 `select distinct`。这里出现重复值是正常的，所以不做数量检查（与实体上的 `toSet()` 不同，见 [2. 入门](02-getting-started.md#toset)）。
- 把 `@Lob` 列用作 `distinct` 或 `groupBy` 键时，Oracle 会拒绝（ORA-22848、ORA-22849）。H2 和 MariaDB 会执行。lambdaQL 不修改查询，以 `DEBUG` 级别记录 `LQL-5010`（见 [4.5](04-order-paging.md#45-what-a-sort-key-can-be)）。

**分页。** `limit` 和 `offset` 的行为与 [4. 排序与分页](04-order-paging.md) 中相同。
分页时，lambdaQL 会把 `id` 作为最后一个排序键加上，这样各页之间不会重叠。如果排序键是唯一且不能为 NULL 的列，则不加（见 [4.8](04-order-paging.md#tie-breaker-automatic-id)）。
`first()` 也只读取一行，所以会加上同样的辅助排序。排序键值相同的行有多个时，每次都返回 `id` 最小的那一行的值。
对分组查询和 `distinct` 查询不会加上它，因为这些查询的 select 列表中没有 `id`。

<!-- region: ManualKotlinSelectTest.kt#value-page -->
```kotlin
// paging adds the id as the last key, so the pages do not overlap
val page = qb.from(Order::class.java)
    .orderBy { o -> desc(o.price) }
    .select { it.price }
    .limit(2).offset(1)
assertEquals("select e0.price from Order e0 order by e0.price desc, e0.id asc", page.toHql().hql())
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

示例代码：[`ManualKotlinSelectTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSelectTest.kt)（`distinct`、`value-page`）。

---

<a id="68-explicit-joins"></a>
## 6.8 显式 join

关联路径（`it.customer?.name`）会自动 join 对一关联（见 [3.10](03-where.md#310-association-paths)）。
要用自己指定的条件，或者不带条件地 join 另一个实体，请使用 `join`、`leftJoin` 或 `crossJoin`。

| 函数 | HQL | 行 |
|---|---|---|
| `join(Customer::class.java) { o, c -> ... }` | `join Customer e1 on ...` | 内连接：只有匹配的行 |
| `leftJoin(Customer::class.java) { o, c -> ... }` | `left join Customer e1 on ...` | 没有匹配的主实体行也会保留 |
| `crossJoin(Customer::class.java)` | `cross join Customer e1` | 所有行的组合。条件写在 `where` 中 |

join 之后，每个 Lambda 表达式都按 join 的顺序得到所有实体。
即 `where { o, c -> ... }`、`orderBy { o, c -> asc(c.name) }` 和 `select { o, c -> ... }`。
被 join 的实体的别名是 `e1`、`e2`。

<!-- region: ManualKotlinSelectTest.kt#join -->
```kotlin
val q = qb.from(Order::class.java)
    .join(Customer::class.java) { o, c -> o.customer?.id == c.id }
    .where { _, c -> c.grade != null && c.grade >= 5 }
assertEquals("select e0 from Order e0 join Customer e1 on e0.customer is not null and e0.customer.id = e1.id"
    + " where e1.grade is not null and e1.grade >= 5", q.toHql().hql())
// without select, the result is the main entity
val result: List<Order> = q.toList()
```

Kotlin 的 `o.customer?.id == c.id` 是可能为 NULL 的值的比较，因此 lambdaQL 会加上 `e0.customer is not null and`。结果行相同。

**结果是主实体。** 没有 `select` 时，`toList()` 返回 `List<Order>`。
被 join 的实体只用于条件、排序和 `select`。
要读取被 join 实体的值，请使用 `select { o, c -> ... }`（见下文）。

<a id="left-join-and-null"></a>
### left join 与 NULL

<!-- region: ManualKotlinSelectTest.kt#left-join-null -->
```kotlin
// c is missing for an order without a customer: c == null finds those rows
val q = qb.from(Order::class.java)
    .leftJoin(Customer::class.java) { o, c -> o.customer?.id == c.id }
    .where { _, c -> c == null || c.name == "Kim" }
assertEquals("select e0 from Order e0 left join Customer e1 on e0.customer is not null and e0.customer.id = e1.id"
    + " where e1.id is null or e1.name = 'Kim'", q.toHql().hql())
```

- `c == null` 表示“没有匹配”。lambdaQL 把它写成 `e1.id is null`（被 join 实体的 `@Id`）。
  Lambda 表达式参数 `c` 是非空类型，所以 Kotlin 编译器可能会警告 `c == null` 总是 false。lambdaQL 把这个表达式读作“没有匹配”。
- `leftJoin` 之后，对被 join 实体的列的条件遵循 **SQL 含义**。没有匹配时，该列是 NULL，条件不为真，这一行会被排除。
  要保留没有匹配的行，请像示例那样写 `c == null || ...`。

<a id="sort-and-select"></a>
### 排序与 select

示例中的 `Row` 是以下 data class。

<!-- region: ManualKotlinSelectTest.kt#row-type -->
```kotlin
data class Row(val orderId: Long?, val customerName: String?)
```

<!-- region: ManualKotlinSelectTest.kt#join-sort-select -->
```kotlin
val sorted = qb.from(Order::class.java)
    .leftJoin(Customer::class.java) { o, c -> o.customer?.id == c.id }
    .orderBy { o, c -> asc(c.name).asc(o.id) }
assertEquals("select e0 from Order e0 left join Customer e1 on e0.customer is not null and e0.customer.id = e1.id"
    + " order by e1.name asc nulls first, e0.id asc", sorted.toHql().hql())
// the order without a customer has customerName = null
val rows = qb.from(Order::class.java)
    .leftJoin(Customer::class.java) { o, c -> o.customer?.id == c.id }
    .orderBy { o, _ -> asc(o.id) }
    .select { o, c -> Row(o.id, c.name) }
assertEquals("select e0.id, e1.name from Order e0 left join Customer e1 on e0.customer is not null and e0.customer.id = e1.id"
    + " order by e0.id asc", rows.toHql().hql())
```

对于没有客户的订单，`customerName` 是 `null`。
`select` 返回 `ValueQuery`，所以可以使用 `distinct()`、`limit`、`offset` 以及 [6.7](#67-distinct-paging-and-results) 的结果函数。

<a id="select-entities"></a>
### 实体选择

`select` 中也可以写实体本身。可以选择被 join 的实体，也可以把多个实体放进一个 data class 或 `Pair`。

<!-- region: KotlinSelectEntityTest.kt#select-joined -->
```kotlin
// the customers that have an order, through an explicit join; Hibernate returns each entity once
val q = qb.from(Order::class.java)
    .join(Customer::class.java) { o, c -> o.customer?.id == c.id }
    .select { _, c -> c }
```

选择一个实体时，Hibernate 对同一个实体只返回一次。HQL 是 `select e1 from Order e0 join Customer e1 on e0.customer is not null and e0.customer.id = e1.id`。

<!-- region: KotlinSelectEntityTest.kt#select-entities -->
```kotlin
// an order and its customer together; with leftJoin an order without a customer has null
val q = qb.from(Order::class.java)
    .leftJoin(Customer::class.java) { o, c -> o.customer?.id == c.id }
    .select { o, c -> OrderWithCustomer(o, c) }
```

`OrderWithCustomer` 是 `data class OrderWithCustomer(val order: Order, val customer: Customer?)`。HQL 是 `select e0, e1 from Order e0 left join Customer e1 on ...`，每个订单一行。也可以像 `select { o, c -> o to c }` 这样用 `Pair` 接收。

包含实体的行不能用作派生表或 CTE 的行（`LQL-3071`）。请选择实体的 id 或列。

示例代码：[`KotlinSelectEntityTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinSelectEntityTest.kt)（`select-joined`、`select-entities`）。

<a id="three-entities-and-repeated-rows"></a>
### 多个实体与重复的行

最多可以 join 10 个实体（主实体和 9 个被 join 的实体）。每 join 一次，Lambda 表达式接收的实体就增加一个：`{ o, c, i -> ... }`。

<!-- region: ManualKotlinSelectTest.kt#join-three -->
```kotlin
// orders of a customer with an item of quantity > 10 (an order with two such items is two SQL rows)
val q = qb.from(Order::class.java)
    .join(Customer::class.java) { o, c -> o.customer?.id == c.id }
    .join(Item::class.java) { o, _, i -> i.order?.id == o.id }
    .where { _, _, i -> i.qty > 10 }
assertEquals("select e0 from Order e0 join Customer e1 on e0.customer is not null and e0.customer.id = e1.id"
    + " join Item e2 on e2.order.id = e0.id where e2.qty > 10", q.toHql().hql())
```

下面是 join 10 个实体的示例。

<!-- region: KotlinSelectEntityTest.kt#ten -->
```kotlin
// up to 10 entities: each lambda gets all of them in join order
val q = qb.from(Order::class.java)
    .join(Customer::class.java) { o, c -> o.customer?.id == c.id }
    .join(Order::class.java) { o, _, a -> a.id == o.id }
    .join(Order::class.java) { _, _, a, b -> b.id == a.id }
    .join(Order::class.java) { _, _, _, b, d -> d.id == b.id }
    .join(Order::class.java) { _, _, _, _, d, e -> e.id == d.id }
    .join(Order::class.java) { _, _, _, _, _, e, f -> f.id == e.id }
    .join(Order::class.java) { _, _, _, _, _, _, f, g -> g.id == f.id }
    .join(Order::class.java) { _, _, _, _, _, _, _, g, h -> h.id == g.id }
    .leftJoin(Order::class.java) { _, _, _, _, _, _, _, _, h, k -> k.id == h.id }
    .where { _, c, _, _, _, _, _, _, _, k -> c.name == "Kim" && k.price > 0.0 }
    .select { _, c, _, _, _, _, _, _, _, k -> c.name to k.id }
```

> [!WARNING]
> 当 join 条件匹配多行时，在 SQL 中**主实体的行会按每个匹配重复**。`toList()` 和 `toSet()` 没有重复，因为 Hibernate 对同一个实体只返回一次。
> 但 `count()` 和 `limit` 会计入重复的 SQL 行。要统计订单数或准确填满一页，请用 `exists()` 子查询（[5. 子查询](05-subquery.md)）代替 join，或者对 join 查询使用 `distinct()`（见 [join 结果的 `distinct()`](#distinct-on-a-join)）。

<a id="cross-join"></a>
### cross join

`crossJoin` 没有 join 条件。函数名表明需要所有行的组合。条件请写在 `where` 中。

<!-- region: ManualKotlinSelectTest.kt#cross-join -->
```kotlin
// every pair of rows; the conditions go in where
val q = qb.from(Order::class.java)
    .crossJoin(Customer::class.java)
    .where { o, c -> c.grade != null && o.price > 100 }
assertEquals("select e0 from Order e0 cross join Customer e1 where e1.grade is not null and e0.price > 100.0",
    q.toHql().hql())
```

lambdaQL 没有 `from(Order::class.java, Customer::class.java)`（逗号 join）。漏写条件时会意外得到所有组合，而且不清楚哪个实体是结果。这种查询请使用 `crossJoin`。

<a id="paging"></a>
### 分页

分页时，lambdaQL 会把**每个**实体的 id 加为最后的排序键，所以各页不会重叠。

<!-- region: ManualKotlinSelectTest.kt#join-paging -->
```kotlin
// with paging, the id of every entity is added to the order, so the pages do not overlap
val pairs = qb.from(Order::class.java)
    .crossJoin(Customer::class.java)
    .orderBy { o, _ -> desc(o.price) }
    .select { o, c -> Row(o.id, c.name) }
assertEquals("select e0.id, e1.name from Order e0 cross join Customer e1 order by e0.price desc, e0.id asc, e1.id asc",
    pairs.limit(5).toHql().hql())
```

依次读取各页时，每个组合按排序顺序各出现一次。

<a id="distinct-on-a-join"></a>
### join 结果的 `distinct()`

join 查询的 `distinct()` 把每个主实体的 SQL 行合并为一行。HQL 是 `select distinct e0`，`count()` 是 `count(distinct e0)`。
即使没有 `distinct()`，`toList()` 和 `toSet()` 也只返回每个主实体一次。要让 `count()` 和 `limit` 按主实体的数量计算，需要 `distinct()`。

- 排序键必须是主实体自身的列（`LQL-3046`）。按被 join 实体的列或关联路径排序会报错。
- 分页时只把主实体的 id 加为并列键。不加被 join 实体的 id。
- 在 `distinct()` 之后使用 `select(...)` 时，用 `select distinct` 查询值。
- 请先考虑 `exists` 子查询（[5. 子查询](05-subquery.md)）。它不产生重复，返回相同的行，而且很多情况下更快。

<!-- region: KotlinQueryTest.kt#join-distinct -->
```kotlin
// an order with two matching items is two SQL rows; distinct() makes it one row in SQL
val q = qb.from(Order::class.java)
    .join(Item::class.java) { o, i -> i.order?.id == o.id }
    .where { _, i -> i.qty > 0 }
    .distinct()
assertEquals("select distinct e0 from Order e0 join Item e1 on e1.order.id = e0.id where e1.qty > 0", q.toHql().hql())
```

<a id="join-a-derived-table-or-cte"></a>
### 派生表与 CTE 的 join

[6.9](#69-derived-table-subquery-in-from) 的派生表和 [6.10](#610-cte-with) 的 CTE 可以像实体一样 join。
写法是 `join(cte) { c, x -> ... }`、`leftJoin(cte) { ... }`、`join(qb.from(...)...select { Row(...) }) { ... }`、`leftJoin(...) { ... }`，可以作为第二个或第三个根使用。

- HQL 是 `with t as (...) ... join t e1 on ...` 或 `join (select ...) e1 on ...`。
- 行类型是 Kotlin data class（或 Java record）。属性（`x.total`）读取列。
- 在一条语句中两次读取同一个 `Cte` 对象时，定义只写一次，两个 join 读取同一个名字：`join t e1 on ... left join t e2 on ...`。不同的 `Cte` 对象各自得到名字，例如 `with t as (...), t2 as (...)`（在三种数据库上实测）。
- 派生行没有 id，所以 `x == null` 是错误（`LQL-3067`）。请像 `x.customerId == null` 这样比较列。
- Hibernate 6.6 和 7.4 都接受实体的 `select distinct`、派生表和 CTE 的 inner/left join，以及子查询内的实体 join（在三种数据库上实测）。

<!-- region: KotlinQueryTest.kt#join-cte -->
```kotlin
// the total of each customer, joined to the customer row
val t = qb.with(qb.from(Order::class.java)
    .where { it.customer != null }
    .groupBy { it.customer?.id }
    .select { CustTotal(it.customer?.id, Agg.sum(it.price)) })
val rows = qb.from(Customer::class.java)
    .join(t) { c, x -> x.customerId == c.id }
    .where { _, x -> x.total!! > 100 }
    .select { c, x -> c.name to x.total }
assertEquals("with t as (select d0.customer.id as customerId, sum(d0.price) as total from Order d0"
        + " where d0.customer is not null group by d0.customer.id) select e0.name, e1.total from Customer e0"
        + " join t e1 on e1.customerId = e0.id where e1.total > 100.0", rows.toHql().hql())
```

即使 `x.customerId` 是可空的 `Int?`，`x.customerId == c.id` 也是 `e1.customerId = e0.id`。Kotlin 插入的 null 检查不会被读取。

无名派生表的 join（`CustomerTotal` 是 [6.9](#69-derived-table-subquery-in-from) 的行类型）：

<!-- region: ManualKotlinSelectTest.kt#join-derived -->
```kotlin
// a derived table without a name; a customer without orders has no row in it (left join)
val small = qb.from(Customer::class.java)
    .leftJoin(qb.from(Order::class.java)
        .where { it.customer != null }
        .groupBy { it.customer?.id }
        .select { CustomerTotal(it.customer?.id, Agg.count(), Agg.sum(it.price)) }) { c, x -> x.customerId == c.id }
    .where { _, x -> x.customerId == null || x.total!! < 210 }
assertEquals("select e0 from Customer e0 left join (select d0.customer.id as customerId, count(*) as cnt,"
    + " sum(d0.price) as total from Order d0 where d0.customer is not null group by d0.customer.id) e1"
    + " on e1.customerId = e0.id where e1.customerId is null or e1.total < 210.0", small.toHql().hql())
```

两次读取同一个 CTE 的写法：

<!-- region: ManualKotlinSelectTest.kt#cte-reuse -->
```kotlin
// the same CTE read twice is written once
val reused = qb.from(Customer::class.java)
    .join(t) { c, x -> x.customerId == c.id }
    .leftJoin(t) { c, _, y -> y.customerId == c.id && y.total!! > 210 }
    .select { c, _, y -> c.name to y.total }
assertEquals("with t as (select d0.customer.id as customerId, count(*) as cnt, sum(d0.price) as total from Order d0"
    + " where d0.customer is not null group by d0.customer.id)"
    + " select e0.name, e2.total from Customer e0 join t e1 on e1.customerId = e0.id"
    + " left join t e2 on e2.customerId = e0.id and e2.total > 210.0", reused.toHql().hql())
```

<a id="what-a-join-query-has"></a>
### join 查询的函数

| 调用之后 | 函数 |
|---|---|
| `join`、`leftJoin`、`crossJoin` | `where`、`join` / `leftJoin` / `crossJoin`（最多 10 个实体，包括派生表和 CTE）、`distinct`、`orderBy`、`select`、`limit`、`toList`、`toSet`、`first`、`count`、`exists`、`toHql` |
| `orderBy { o, c -> ... }` | `select`、`limit`、`offset`、`toList`、`first`，以及排序后查询的其他函数（[4. 排序与分页](04-order-paging.md)） |

子查询内的 join 见 [5. 子查询](05-subquery.md#joins-inside-a-subquery)。
不支持：insert 中的 join，11 个或更多实体，`SortSpec` 的 join 形式。
update 和 delete 中的 join 决定不再添加。请使用关联路径或 `sub`。

示例代码：[`ManualKotlinSelectTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSelectTest.kt)（`join`、`left-join-null`、`row-type`、`join-sort-select`、`join-three`、`cross-join`、`join-paging`、`join-derived`、`cte-reuse`），[`KotlinQueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)（`join-distinct`、`join-cte`）、[`KotlinSelectEntityTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinSelectEntityTest.kt)（`ten`）。

---

<a id="69-derived-table-subquery-in-from"></a>
## 6.9 派生表（FROM 中的子查询）

请用 `select` 创建一个值查询（data class，见 [6.3](#63-select-a-data-class-pair-or-triple)），然后把它传给 `qb.from(...)`。
它的行会成为外层查询的表。
结果是 `DerivedQuery<R>`，`R` 是行类型。

本节和 [6.10](#610-cte-with) 的行类型：

<!-- region: ManualKotlinSelectTest.kt#derived-types -->
```kotlin
/** A row of a derived table or CTE: the properties name the columns (J5). */
data class CustomerTotal(val customerId: Int?, val cnt: Long, val total: Double?)
data class Priced(val id: Long?, val price: Double?)
```

<!-- region: ManualKotlinSelectTest.kt#derived -->
```kotlin
// the rows of a group query are the table of the outer query; r.total reads the column "total"
val q = qb.from(qb.from(Order::class.java)
        .where { it.customer != null }
        .groupBy { it.customer?.id }
        .select { CustomerTotal(it.customer?.id, Agg.count(), Agg.sum(it.price)) })
    .where { r -> r.total!! > 100 }
    .orderBy { r -> desc(r.total) }
assertEquals("select e0.customerId, e0.cnt, e0.total from (select d0.customer.id as customerId, count(*) as cnt,"
    + " sum(d0.price) as total from Order d0 where d0.customer is not null group by d0.customer.id) e0"
    + " where e0.total > 100.0 order by e0.total desc nulls last", q.toHql().hql())
```

- 内层查询使用别名 `d0`（其中的 join 为 `dj1` 等），外层查询使用 `e0`。
- 每个 data class 属性按主构造函数的顺序决定一个列名：`as customerId`、`as cnt`、`as total`。
- 在外层 Lambda 表达式中，用属性读取列。`r.total` 就是 `e0.total`。
- 外层查询提供 `where`、`orderBy`、`limit`、`offset`、`select`、`toList`、`first`、`count` 和 `toHql`。

也可以 select 行中的一个值（`.select { r -> r.customerId }`）。内层和外层的捕获值按顺序绑定。
内层查询的 `limit` 和 `offset` 保留在内层查询中（HQL `limit`、`offset`）。所以可以对前几行再次排序。

<!-- region: ManualKotlinSelectTest.kt#derived-limit -->
```kotlin
// the three most expensive orders, then the cheapest of them
val cheapestOfTop = qb.from(qb.from(Order::class.java)
        .orderBy { o -> desc(o.price) }
        .limit(3)
        .select { Priced(it.id, it.price) })
    .orderBy { r -> asc(r.price) }
    .first()
```

Hibernate 7.4 给派生表的列起 `c0`、`c1`、`c2` 这样的名字。H2 把列清单放在别名上（`r1_0(c0,c1,c2)`）。MariaDB 和 Oracle 把列别名写在子查询内部。

规则：

- 行类型必须是 **Kotlin data class**（或 Java record）。
  lambdaQL 按主构造函数的顺序从 data class 属性中取得列名。
- 这里不能使用普通类、`Pair`、`Triple` 或单个值，因为 lambdaQL 无法确定列名。这时会抛出 `TranslationException`（`LQL-3064`）。

<!-- region: ManualKotlinSelectTest.kt#derived-row-type -->
```kotlin
// a single value (or a Pair, or a class that is not a data class) has no column names
val e = assertThrows(LambdaQLException::class.java) { qb.from(qb.from(Order::class.java).select { it.price }) }
assertEquals("LQL-3064", e.code())
```

在实体查询中 join 派生表的写法见[派生表与 CTE 的 join](#join-a-derived-table-or-cte)。

不支持：派生表之间的 join，以及 update、delete、insert 中的派生表。

实体的 `@SQLRestriction` 和 `@SoftDelete` 条件，Hibernate 也会加到实体 join 的 `on` 和派生表内部（在 MariaDB 10.11、Oracle 23.26 上实测）。lambdaQL 不单独处理这些条件。

示例代码：[`ManualKotlinSelectTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSelectTest.kt)（`derived-types`、`derived`、`derived-limit`、`derived-row-type`）。

---

<a id="610-cte-with"></a>
## 6.10 CTE（`with`）

请用 `qb.with(valueQuery)` 创建一个类型为 `Cte<R>` 的句柄，然后用 `qb.from(handle)` 读取它。
内层查询与派生表（6.9）相同。

<!-- region: ManualKotlinSelectTest.kt#cte -->
```kotlin
val perCustomer = qb.from(Order::class.java)
    .where { it.customer != null }
    .groupBy { it.customer?.id }
    .select { CustomerTotal(it.customer?.id, Agg.count(), Agg.sum(it.price)) }
val t: Cte<CustomerTotal> = qb.with(perCustomer)
val q = qb.from(t).where { r -> r.cnt >= 2 }
assertEquals("with t as (select d0.customer.id as customerId, count(*) as cnt, sum(d0.price) as total"
    + " from Order d0 where d0.customer is not null group by d0.customer.id)"
    + " select e0.customerId, e0.cnt, e0.total from t e0 where e0.cnt >= 2L", q.toHql().hql())
```

MariaDB 和 Oracle 执行真正的 `with`。列清单由 Hibernate 自己写出：`with t (customerId,cnt,total) as (...)`。H2 把 CTE 变成派生表。

说明：

- CTE 的名字是 `t`。外层查询总是给它别名 `e0`（`from t e0`）。HQL 需要别名。
- CTE 的列名由 select 别名（`as customerId`）决定。HQL 不接受 CTE 名后面的列清单，列清单由 Hibernate 加到 SQL 中。
- MariaDB 和 Oracle 执行真正的 `with`。在 H2 上，Hibernate 把它展开为派生表。结果相同。
- 行类型的规则与派生表相同：data class 或 record。
- 在实体查询中 join CTE 的写法见[派生表与 CTE 的 join](#join-a-derived-table-or-cte)。
- 同一个 CTE 可以在一个查询中读取两次。定义只写一次（见[派生表与 CTE 的 join](#join-a-derived-table-or-cte)）。
- 不支持：update、delete、insert 中的 CTE。

示例代码：[`ManualKotlinSelectTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSelectTest.kt)（`cte`）。

---

<a id="611-rules-j1-to-j8"></a>
## 6.11 规则 J1 至 J8

| # | 规则 | 状态 |
|---|---|---|
| J1 | `select` 的 Lambda 表达式返回**一个值表达式**或**一次构造函数调用**。每个构造函数参数都是一个值表达式（[3. 条件（`where`）](03-where.md) 的规则）。实体本身也可以作为值（[实体选择](#select-entities)）。选择值的 `if` 和 `when` 会成为 `case when`（[3.5.1](03-where.md#351-values-chosen-by-a-condition)）。只在 `select` 中使用的关联路径会变成 `left join` 别名，因此不会丢失行。必需关联（`optional = false`）是内连接（A4）。如果 `where` 使用同一条路径，`select` 会使用 `where` 的 join（R24）。 | 已实现 |
| J2 | 对于构造函数，lambdaQL 只查询列，并自己调用构造函数。它不会写 `select new`。`Pair`/`Triple` 也以同样方式构建。 | 已实现 |
| J3 | 有聚合时，`select` 和 `having`（以及 `insert ... select` 的值）中每个非聚合值都必须是 `groupBy` 的键。否则会在分析时报错。 | 已实现。错误是 `TranslationException`（`LQL-3035`，见 [11](../11-errors-and-languages.md)） |
| J4 | 没有行或所有值都是 NULL 时，`sum`/`avg`/`min`/`max` 返回 NULL，`count` 返回 0。构造函数参数请写成可空类型。NULL 进入非空参数时会抛出 `IllegalStateException`。 | 已实现 |
| J5 | 派生表或 CTE 的行类型：**只能是 Kotlin data class 或 Java record**。普通类只允许用于简单的 `select`。否则为 `LQL-3064`。也适用于被 join 的派生表和 CTE 的行。派生行不能与 `null` 比较（`LQL-3067`）。 | 已实现 |
| J6 | 用 `qb.with(...)` 创建的 CTE 用 select 别名为列命名：`with t as (select d0.customer.id as customerId, ...) select e0.customerId ... from t e0`。HQL 不接受名称后面的列清单。Hibernate 会以 `SyntaxException` 拒绝 `with t (col list) as (...)`。列清单由 Hibernate 自己写进 SQL（在 MariaDB 和 Oracle 上为 `with t (customerId,cnt,total) as (...)`）。H2 把 CTE 变成派生表。外层查询总是使用别名（`from t e0`）。 | 已实现 |
| J7 | `distinct()` 输出 `select distinct`。与排序一起使用时，排序键必须在 select 列表中（数据库的规则）。lambdaQL 会在分析时检查这一点。在 join 结果的 `distinct()` 中，排序键必须是主实体自身的列。 | 已实现 |
| J8 | 查询列可以与排序（[4. 排序与分页](04-order-paging.md)）、分页和子查询（[5. 子查询](05-subquery.md)）一起使用。没有按分组查询的聚合结果排序的功能（`desc(s.total)`）。 | 已实现 |

J3 的示例见 [6.5](#every-non-aggregate-value-must-be-a-key-rule-j3)。`where` 中的 `Agg`、J7、J4：

<!-- region: ManualKotlinSelectTest.kt#rules -->
```kotlin
// aggregates belong in having, not in where
val where = assertThrows(LambdaQLException::class.java) { qb.from(Order::class.java).where { Agg.count() > 1 } }
assertEquals("LQL-3005", where.code())
// with distinct, the sort key must be selected
val j7 = assertThrows(LambdaQLException::class.java) {
    qb.from(Order::class.java).orderBy { o -> asc(o.id) }.select { it.price }.distinct().toHql()
}
assertEquals("LQL-3046", j7.code())
// sum over no rows is NULL; the non-null parameter of StrictTotal(val total: Double) cannot take it
assertThrows(IllegalStateException::class.java) {
    qb.from(Order::class.java).where { it.status.toInt() == 9 }.select { StrictTotal(Agg.sum(it.price)) }.toList()
}
```

示例代码：[`ManualKotlinSelectTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSelectTest.kt)（`rules`）。

---

<a id="612-not-in-v01"></a>
## 6.12 v0.1 中不包含的内容

本章的功能中没有以下内容。

| 项目 | 备注 |
|---|---|
| 按聚合结果或 select 别名排序（例如 `orderBy { s -> desc(s.total) }`），以及分组查询上的 `orderBy` | |
| 用户自定义聚合函数和窗口函数 | |
| insert 中的 join，11 个或更多实体，子查询内三个或更多实体 | |
| update、delete 中的 join | 决定不添加。使用关联路径或 `sub` |
| DML 中的派生表和 CTE | |

本章的错误是带错误码的 `TranslationException`：错误的分组键为 `LQL-3035`，`where` 中的 `Agg` 为 `LQL-3005`，使用 `distinct` 时未 select 的排序键为 `LQL-3046`，派生表或 CTE 的行类型为 `LQL-3064`。见 [11. 错误与语言](../11-errors-and-languages.md)。

---

[Kotlin 路线](README.md) | 上一章：[5. 子查询](05-subquery.md) | 下一章：[7. 更新、插入、删除](07-dml.md) | [本页的 Java 版本](../java/06-select-group.md)
