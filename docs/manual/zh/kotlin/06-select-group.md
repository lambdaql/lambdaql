<a id="6-select-aggregates-and-group-by-kotlin"></a>
# 6. 查询列、聚合与分组（Kotlin）

[Kotlin 路线](README.md) | [手册首页](../README.md) | 上一章：[5. 子查询](05-subquery.md) | 下一章：[7. 更新、插入、删除](07-dml.md) | [本页的 Java 版本](../java/06-select-group.md)

公共页面：[1. 概述](../01-overview.md) · [11. 错误与语言](../11-errors-and-languages.md)

---

本章说明如何选择查询返回**什么**。

- 每行一个值（`select { it.price }`）
- Kotlin data class、`Pair`、`Triple`
- 带 `groupBy` 和 `having` 的聚合（`count`、`sum`、`avg` 等）
- 值查询的 `distinct`、分页和结果函数
- 与其他实体的显式 join（`join`、`leftJoin`、`crossJoin`）
- FROM 子句中的子查询（派生表）和 CTE（`with`）

<a id="contents"></a>
## 目录

- [6.1 示例实体与结果类型](#61-example-entities-and-result-types)
- [6.2 查询一个值](#62-select-one-value)
- [6.3 查询 data class、Pair、Triple](#63-select-a-data-class-pair-or-triple)
- [6.4 聚合函数：`Agg`](#64-aggregate-functions-agg)
- [6.5 Group by](#65-group-by)
- [6.6 Having](#66-having)
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

并使用以下结果类型。

```kotlin
data class OrderView(val id: Long?, val customerName: String?, val price: Double?)
data class Stat(val customerId: Long?, val count: Long, val total: Double?)
data class KeyCount(val customerId: Long?, val status: Int?, val count: Long)
data class Total(val count: Long, val average: Double?)
```

> [!TIP]
> 结果请使用可空类型（`Double?`、`String?`）。
> 列或像 `sum` 这样的聚合可能为 NULL（规则 J4）。非空的 `Double` 或 `Long` 参数是 JVM 基本类型，非空的 `String` 参数同样不接受 NULL。
> 如果 NULL 被传入这样的参数，lambdaQL 会抛出 `IllegalStateException`。对于基本类型，消息会指出是哪个参数，并提示改用包装类型。
> `count` 永远不会返回 NULL，因此 `count: Long` 没有问题。

标记为 **(已测试)** 的示例来自 [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)。该测试使用 Java 测试实体（`price` 是基本类型 `double`，`status` 是 `short`，`Customer.id` 是 `int`）和以下结果类型。

```kotlin
/** A row of one order (Kotlin data class). */
data class OrderRow(val id: Long?, val customerName: String?, val price: Double)

/** A row of one customer group. Aggregates can be NULL, so the types are nullable (J4). */
data class CustomerStat(val customerId: Int?, val count: Long, val total: Double?)
```

更多用 Java 写的查询在 [`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java) 中测试（H2、MariaDB、Oracle）。这些示例见[本页的 Java 版本](../java/06-select-group.md)。

---

<a id="62-select-one-value"></a>
## 6.2 查询一个值

`select` 接收一个 Lambda。该 Lambda 返回一个值。结果是这个值的 `ValueQuery`。
可以在 `where` 之后和 `orderBy` 之后调用 `select`。排序、`limit` 和 `offset` 会保留。

```kotlin
val prices: List<Double?> = qb.from(Order::class.java)
    .where { it.status == 1 }
    .orderBy { o, by -> by.asc(o.price) }
    .select { it.price }
    .toList()
// HQL: select e0.price from Order e0 where e0.status = 1 order by e0.price asc nulls first
```

这里 `price` 是 `Double?`，所以排序带有 `nulls first`（见 [4.4](04-order-paging.md#44-null-ordering-is-the-same-on-every-database)）。在 Java 测试中 `price` 是 `double`，所以它的 HQL（示例 `value`）没有 `nulls` 部分。

**关于 SQL 块。** `?` 是绑定参数。这些行来自 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)。该测试使用 Hibernate 6.6.5 在 H2 2.3、MariaDB 10.11 和 Oracle 23.26 上运行每个查询，如果 Hibernate 的 SQL 发生变化，测试就会失败。本章的重点是 select 列表，所以 select 列表没有缩短。该测试使用 Java Lambda。含义相同的 Kotlin Lambda 生成相同的 HQL，因此 SQL 也相同。

**各数据库的 SQL（来自 Java 测试运行）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `value`：

```sql
-- H2, MariaDB, Oracle (same)
select o1_0.price
from orders o1_0
where o1_0.status=1
order by o1_0.price
```

关联路径就是一个列，与 `where` 中相同（规则 R24）。在 `select` 中，lambdaQL 会添加 `left join`，因此没有客户的订单不会丢失（在 `where` 中，同一条路径默认是内连接）。
用 `optional = false` 映射的必需关联不会丢失行，所以在 `select` 中也是内连接（见 [4.5](04-order-paging.md#45-what-a-sort-key-can-be)）。

```kotlin
.select { it.customer?.name }
// HQL: select j1.name from Order e0 left join e0.customer j1
```

`it.customer!!.name` 生成相同的 HQL。[6.3](#63-select-a-data-class-pair-or-triple) 中的 data class 示例展示了这个 `left join`。
如果路径以关联实体的 `@Id` 结尾，就没有 join。
`it.customer?.id` 读取外键列 `customer_id`。

其他值的写法（Kotlin 特有写法，已设计；见 [9. Kotlin 说明](09-kotlin-notes.md)）：

```kotlin
.select { it.description ?: "" }              // coalesce(e0.description, '')
.select { it.customer?.name ?: "none" }       // coalesce(j1.name, 'none') + left join
.select { (it.price ?: 0.0) * 2 }             // coalesce(e0.price, 0.0) * 2
```

布尔 Elvis（`select { it.flag ?: false }`）以及值 `if`/`when` 会被拒绝（限制 KC21、KC6）。

---

<a id="63-select-a-data-class-pair-or-triple"></a>
## 6.3 查询 data class、Pair、Triple

在 Lambda 中调用构造函数。每个构造函数参数都是一个值表达式。

```kotlin
// data class
val list: List<OrderView> = qb.from(Order::class.java)
    .select { OrderView(it.id, it.customer?.name, it.price) }
    .toList()
// HQL: select e0.id, j1.name, e0.price from Order e0 left join e0.customer j1

// Pair（Triple 也可以）
val pairs: List<Pair<String?, Double?>> = qb.from(Order::class.java)
    .select { it.customer?.name to it.price }
    .toList()
// HQL: select j1.name, e0.price from Order e0 left join e0.customer j1
```

**(已测试)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)，region `data-class`：

```kotlin
val q = qb.from(Order::class.java).select { OrderRow(it.id, it.customer?.name, it.price) }
assertEquals("select e0.id, j1.name, e0.price from Order e0 left join e0.customer j1", q.toHql().hql())
```

**(已测试)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)，region `pair`：

```kotlin
val q = qb.from(Order::class.java).where { it.status.toInt() == 1 }.select { it.customer?.name to it.price }
assertEquals("select j1.name, e0.price from Order e0 left join e0.customer j1 where e0.status = 1", q.toHql().hql())
```

测试实体的 `status` 是 `short`，所以测试用 `it.status.toInt() == 1` 比较。lambdaQL 会忽略这个转换。

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `record`（data class 示例）：

```sql
-- H2, MariaDB, Oracle (same)
select o1_0.id,c1_0.name,o1_0.price
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
```

lambdaQL **不会**写 `select new com.acme.OrderView(...)`（规则 J2）。
它只查询列，并由 lambdaQL 自己调用构造函数。data class、`Pair` 和 `Triple` 都是这样。
这样可以避免长类名和嵌套类名带来的问题。
lambdaQL 会把每个值转换为构造函数参数的类型（例如把 Hibernate 的 `Integer` 转换为 `Long?` 参数）。

可以返回的内容：

| 结果类型 | 简单的 `select` | 派生表或 CTE 的行类型（[6.9](#69-derived-table-subquery-in-from)） |
|---|---|---|
| 一个值（`it.price`） | 是 | – |
| Kotlin data class | 是 | 是 |
| 带构造函数的普通类 | 是 | **否**（J5） |
| `Pair` / `Triple` | 是 | 否 |
| Java record（来自 Java 模块） | 是 | 是 |

---

<a id="64-aggregate-functions-agg"></a>
## 6.4 聚合函数：`Agg`

聚合函数是 **`org.lambdaql.Agg` 的静态方法**。
它们是 SQL 标准函数，因此由 lambdaQL 提供。（自己的数据库函数见 [8. 存储过程与数据库函数](08-procedures-functions.md)。）

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

导入是可选的。可以导入单个函数。

```kotlin
import org.lambdaql.Agg.sum

.having { sum(it.price) > 200 }
```

这不会与 `xs.sum()` 或 `xs.count()` 这样的 Kotlin 标准库扩展函数冲突（已用编译器确认）。

要点：

- 返回值只是一个**占位符**。Lambda 永远不会执行，因此这个值永远不会被使用。
- 如果在 lambdaQL Lambda **外部**调用 `Agg.sum(...)`，会抛出 `IllegalStateException`。
- `Agg` 可以用在 `select`、`having` 以及 `insert ... select` 的值中（[7.5](07-5-insert.md)）。
- 不能在 `where` 中使用 `Agg`。对聚合的条件请使用 `having`。在 `where` 中使用 `Agg` 会在分析时报错。

<a id="aggregate-over-all-rows-no-group-by"></a>
### 对所有行聚合（没有 group by）

```kotlin
val t: Total? = qb.from(Order::class.java)
    .where { it.status == 1 }
    .select { Total(Agg.count(), Agg.avg(it.price)) }
    .one()
    .orElse(null)
// HQL: select count(*), avg(e0.price) from Order e0 where e0.status = 1
```

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `total`：

```sql
-- H2, MariaDB, Oracle (same)
select count(*),avg(o1_0.price)
from orders o1_0
where o1_0.status=1
```

这个查询总是返回一行。

<a id="null-in-aggregates-rule-j4"></a>
### 聚合中的 NULL（规则 J4）

| 函数 | 没有行，或所有值都是 NULL |
|---|---|
| `sum`、`avg`、`min`、`max` | `NULL` |
| `count` | `0` |

这是 SQL 的正常行为。这些结果请使用可空参数（`Double?`）（见 [6.1](#61-example-entities-and-result-types) 中的提示）。

---

## 6.5 Group by

用 `groupBy` 写出分组键。lambdaQL 不会猜测分组键。
`groupBy` 返回分组查询。先调用 `having { }`（可选），再调用 `select { }`。

<a id="one-key"></a>
### 一个键

```kotlin
val stats: List<Stat> = qb.from(Order::class.java)
    .groupBy { it.customer?.id }
    .select { Stat(it.customer?.id, Agg.count(), Agg.sum(it.price)) }
    .toList()
// HQL: select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id
```

键以 `@Id` 结尾，所以没有 join。没有客户的订单组成一个键为 NULL 的分组。
带 `having` 的示例见 [6.6](#66-having)。

<a id="many-keys"></a>
### 多个键

用 `listOf(...)` 返回键的列表，或使用 `Pair`（`a to b`）或 `Triple`。

```kotlin
val counts: List<KeyCount> = qb.from(Order::class.java)
    .where { it.customer != null }
    .groupBy { listOf(it.customer?.id, it.status) }
    .select { KeyCount(it.customer?.id, it.status, Agg.count()) }
    .toList()
// HQL: select e0.customer.id, e0.status, count(*) from Order e0 where e0.customer is not null
//      group by e0.customer.id, e0.status

.groupBy { it.customer?.id to it.status }                     // Pair：结果相同
.groupBy { Triple(it.customer?.id, it.status, it.active) }    // 三个键
```

**(已测试)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)，region `group-many`（结果是 `Triple`）：

```kotlin
val q = qb.from(Order::class.java)
    .where { it.customer != null }
    .groupBy { listOf(it.customer?.id, it.status) }
    .select { Triple(it.customer?.id, it.status, Agg.count()) }
assertEquals("select e0.customer.id, e0.status, count(*) from Order e0 where e0.customer is not null"
    + " group by e0.customer.id, e0.status", q.toHql().hql())
```

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `group-many`：

```sql
-- H2, MariaDB, Oracle (same)
select o1_0.customer_id,o1_0.status,count(*)
from orders o1_0
where o1_0.customer_id is not null
group by o1_0.customer_id,o1_0.status
```

键按书写的顺序使用。
`groupBy` 中的 `listOf(...)` 是**唯一**允许在 Lambda 内部创建列表的地方。

不允许作为键列表的写法：

| 写法 | 原因 |
|---|---|
| `arrayOf(a, b)` | 类型混合时无法编译 |
| `setOf(a, b)` | Set 会去除重复且没有顺序 |

<a id="every-non-aggregate-value-must-be-a-key-rule-j3"></a>
### 每个非聚合值都必须是键（规则 J3）

当查询包含聚合时，`select` 和 `having` 中每个**不是**聚合的值都必须是 `groupBy` 的键。这是 SQL 的规则。lambdaQL 会在分析时检查它。

```kotlin
// 错误：it.status 不是分组键
qb.from(Order::class.java)
    .groupBy { it.customer?.id }
    .select { KeyCount(it.customer?.id, it.status, Agg.count()) }
// → TranslationException(LQL-3035)
```

同样的检查已用 Java 测试（region `rules`，见 [6.11](#611-rules-j1-to-j8)）。

---

## 6.6 Having

`having` 是针对分组的条件。它使用与 `where` 相同的 Lambda 规则（[3. 条件（`where`）](03-where.md)），并且可以使用 `Agg`。
`having` 可以调用多次。各个条件用 `AND` 连接。

```kotlin
val big: List<Stat> = qb.from(Order::class.java)
    .groupBy { it.customer?.id }
    .having { Agg.sum(it.price) > 200 }
    .select { Stat(it.customer?.id, Agg.count(), Agg.sum(it.price)) }
    .toList()
// HQL: select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id
//      having sum(e0.price) > 200.0
```

**(已测试)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)，region `group-by`：

```kotlin
val q = qb.from(Order::class.java)
    .groupBy { it.customer?.id }
    .having { Agg.sum(it.price) > 200 }
    .select { CustomerStat(it.customer?.id, Agg.count(), Agg.sum(it.price)) }
assertEquals("select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id"
    + " having sum(e0.price) > 200.0", q.toHql().hql())
```

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `group-by`：

```sql
-- H2, MariaDB, Oracle (same)
select o1_0.customer_id,count(*),sum(o1_0.price)
from orders o1_0
group by o1_0.customer_id
having sum(o1_0.price)>200.0
```

`Agg.sum` 是 Java 方法，所以它的结果是平台类型。因此 `Agg.sum(it.price) > 200` 不加 `!!` 也能编译。即使加上 `!!`，lambdaQL 也会忽略它（决策 D23）。

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

```kotlin
val q = qb.from(Order::class.java)
    .orderBy { o, by -> by.desc(o.price) }
    .select { it.price }
    .distinct()
// HQL: select distinct e0.price from Order e0 order by e0.price desc nulls last
val prices = q.toSet()      // always select distinct
```

**各数据库的 SQL（来自 Java 测试运行）** — Java 测试中的 `price` 是 `double`，所以它的 HQL 没有 `nulls` 部分。来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `distinct`：

```sql
-- H2, MariaDB, Oracle (same)
select distinct o1_0.price
from orders o1_0
order by o1_0.price desc
```

- 使用 `distinct()` 时，每个排序键都必须是 select 的值（规则 J7）。否则 lambdaQL 会在分析时抛出 `TranslationException`（`LQL-3046`）。
- 值查询上的 `toSet()` 总是使用 `select distinct`。这里出现重复值是正常的，所以不做数量检查（与实体上的 `toSet()` 不同，见 [2. 入门](02-getting-started.md#toset)）。
- 把 `@Lob` 列用作 `distinct` 或 `groupBy` 键时，Oracle 会拒绝（ORA-22848、ORA-22849）。H2 和 MariaDB 会执行。lambdaQL 不修改查询，以 `DEBUG` 级别记录 `LQL-5010`（见 [4.5](04-order-paging.md#45-what-a-sort-key-can-be)）。

**分页。** `limit` 和 `offset` 的用法与 [4. 排序与分页](04-order-paging.md) 中相同。
分页时，lambdaQL 会把 `id` 作为最后一个排序键加上，这样各页之间不会重叠（规则 S9）。如果排序键是唯一且不能为 NULL 的列，则不加（见 [4.8](04-order-paging.md#tie-breaker-automatic-id)）。
`first()` 也只读取一行，所以会加上同样的辅助排序。排序键值相同的行有多个时，每次都返回 `id` 最小的那一行的值。
对分组查询和 `distinct` 查询不会加上它，因为 `id` 不在这些查询的 select 列表中。

```kotlin
val page = qb.from(Order::class.java)
    .orderBy { o, by -> by.desc(o.price) }
    .select { it.price }
    .limit(2).offset(1)
    .toList()
// HQL: select e0.price from Order e0 order by e0.price desc nulls last, e0.id asc
```

**各数据库的 SQL（来自 Java 测试运行）** — Java 测试中的 `price` 是 `double`，所以它的 HQL 没有 `nulls` 部分。来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `value-page`：

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

<a id="68-explicit-joins"></a>
## 6.8 显式 join

关联路径（`it.customer?.name`）会自动 join 对一关联（见 [3.10](03-where.md#310-association-paths)）。
要用自己指定的条件，或者不带条件地 join 另一个实体，请使用 `join`、`leftJoin` 或 `crossJoin`（决策 D44、D45）。

| 函数 | HQL | 行 |
|---|---|---|
| `join(Customer::class.java) { o, c -> ... }` | `join Customer e1 on ...` | 内连接：只有匹配的行 |
| `leftJoin(Customer::class.java) { o, c -> ... }` | `left join Customer e1 on ...` | 没有匹配的主实体行也会保留 |
| `crossJoin(Customer::class.java)` | `cross join Customer e1` | 所有行的组合。条件写在 `where` 中 |

join 之后，每个 Lambda 都按 join 的顺序得到所有实体。
即 `where { o, c -> ... }`、`orderBy { o, c, by -> ... }`（`by` 在最后，规则 S12）和 `select { o, c -> ... }`。
被 join 的实体得到别名 `e1`、`e2`。

join 已用 **Java** Lambda 在 [`JoinQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java) 中测试（H2、MariaDB、Oracle）。join 结果的 `distinct()` 和 CTE 的 join 也用 Kotlin Lambda 测试过（[`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)，region `join-distinct`、`join-cte`）。
下面其余的 Kotlin 代码是把已测试的 Java 查询改写为 Kotlin 语法，HQL 来自 Java 测试。测试代码见[本页的 Java 版本](../java/06-select-group.md#68-explicit-joins)。

```kotlin
val q = qb.from(Order::class.java)
    .join(Customer::class.java) { o, c -> o.customer?.id == c.id }
    .where { o, c -> c.grade != null && c.grade!! >= 5 }
// HQL: select e0 from Order e0 join Customer e1 on e0.customer.id = e1.id
//      where e1.grade is not null and e1.grade >= 5
```

**结果是主实体。** 没有 `select` 时，`toList()` 返回 `List<Order>`。
被 join 的实体只用于条件、排序和 `select`。
要读取被 join 实体的值，请使用 `select { o, c -> ... }`（见下文）。

<a id="left-join-and-null"></a>
### left join 与 NULL

```kotlin
// 没有客户的订单没有 c：c == null 能找到这些行
val q = qb.from(Order::class.java)
    .leftJoin(Customer::class.java) { o, c -> o.customer?.id == c.id }
    .where { o, c -> c == null || c.name == "Kim" }
// HQL: select e0 from Order e0 left join Customer e1 on e0.customer.id = e1.id
//      where e1.id is null or e1.name = 'Kim'
```

- `c == null` 表示“没有匹配”。lambdaQL 把它写成 `e1.id is null`（被 join 实体的 `@Id`）。
  Lambda 参数 `c` 是非空类型，所以 Kotlin 编译器可能会警告 `c == null` 总是 false。lambdaQL 把这个表达式读作“没有匹配”。
- `leftJoin` 之后，对被 join 实体的列的条件遵循 **SQL 含义**。没有匹配时，该列是 NULL，条件不为真，这一行会被排除。
  要保留没有匹配的行，请像示例那样写 `c == null || ...`。

<a id="sort-and-select"></a>
### 排序与 select

```kotlin
val q = qb.from(Order::class.java)
    .leftJoin(Customer::class.java) { o, c -> o.customer?.id == c.id }
    .orderBy { o, c, by -> by.asc(c.name).asc(o.id) }
// HQL: select e0 from Order e0 left join Customer e1 on e0.customer.id = e1.id
//      order by e1.name asc nulls first, e0.id asc

data class Row(val orderId: Long?, val customerName: String?)

val rows = qb.from(Order::class.java)
    .leftJoin(Customer::class.java) { o, c -> o.customer?.id == c.id }
    .orderBy { o, c, by -> by.asc(o.id) }
    .select { o, c -> Row(o.id, c.name) }
// HQL: select e0.id, e1.name from Order e0 left join Customer e1 on e0.customer.id = e1.id
//      order by e0.id asc
```

对于没有客户的订单，`customerName` 是 `null`。
`select` 返回 `ValueQuery`，所以 `distinct()`、`limit`、`offset` 以及 [6.7](#67-distinct-paging-and-results) 的结果函数都可以使用。

<a id="three-entities-and-repeated-rows"></a>
### 三个实体与重复的行

最多可以 join 三个实体。第三个 join 的 Lambda 得到全部三个实体：`{ o, c, i -> ... }`。

```kotlin
// 有数量 > 10 的商品的客户订单；每匹配一个商品，订单就重复一次
val q = qb.from(Order::class.java)
    .join(Customer::class.java) { o, c -> o.customer?.id == c.id }
    .join(Item::class.java) { o, c, i -> i.order?.id == o.id }
    .where { o, c, i -> i.qty > 10 }
// HQL: select e0 from Order e0 join Customer e1 on e0.customer.id = e1.id
//      join Item e2 on e2.order.id = e0.id where e2.qty > 10
```

> [!WARNING]
> 当 join 条件匹配多行时，**主实体的行会重复**：每个匹配一次。`toList()` 和 `count()` 会计入每一次重复（已用 Java 测试）。
> 如果每个订单只需要一次，请用 `exists()` 子查询（[5. 子查询](05-subquery.md)）代替 join，或者对 join 查询使用 `distinct()`（见 [join 结果的 `distinct()`](#distinct-on-a-join)）。

<a id="cross-join"></a>
### cross join

`crossJoin` 没有 join 条件。函数名表明需要所有行的组合。条件写在 `where` 中。

```kotlin
// 所有行的组合；条件写在 where 中
val q = qb.from(Order::class.java)
    .crossJoin(Customer::class.java)
    .where { o, c -> c.grade != null && o.price!! > 100 }
// HQL: select e0 from Order e0 cross join Customer e1 where e1.grade is not null and e0.price > 100.0
```

lambdaQL 没有 `from(Order::class.java, Customer::class.java)`（逗号 join）。漏写条件时会意外得到所有组合，而且不清楚哪个实体是结果（决策 D45）。这种查询请使用 `crossJoin`。

<a id="paging"></a>
### 分页

分页时，lambdaQL 会把**每个**实体的 id 加为最后的排序键，所以各页不会重叠（规则 S9）。

```kotlin
// 分页时，每个实体的 id 都会加到排序中，所以各页不会重叠（S9）
val pairs = qb.from(Order::class.java)
    .crossJoin(Customer::class.java)
    .orderBy { o, c, by -> by.desc(o.price) }
    .select { o, c -> Row(o.id, c.name) }
    .limit(5)
// HQL: select e0.id, e1.name from Order e0 cross join Customer e1 order by e0.price desc, e0.id asc, e1.id asc
```

这个 HQL 来自 `price` 为基本类型 `double` 的 Java 测试。使用本手册中可空的 `price: Double?` 时，排序键会带上 `nulls last`（见 [4.4](04-order-paging.md#44-null-ordering-is-the-same-on-every-database)）。

<a id="distinct-on-a-join"></a>
### join 结果的 `distinct()`

join 查询的 `distinct()` 对因 join 而重复的主实体行只返回一次（决策 D51）。HQL 是 `select distinct e0`，`count()` 是 `count(distinct e0)`。

- 排序键必须是主实体自身的列（J7，`LQL-3046`）。按被 join 实体的列或关联路径排序会报错。
- 分页时只把主实体的 id 加为平局键。不加被 join 实体的 id。
- 在 `distinct()` 之后使用 `select(...)` 时，用 `select distinct` 查询值。
- 请先考虑 `exists` 子查询（[5. 子查询](05-subquery.md)）。它不产生重复，返回相同的行，而且很多情况下更快。

**(已测试)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)，region `join-distinct`：

```kotlin
// an order with two matching items would come twice; distinct() returns it once
val q = qb.from(Order::class.java)
    .join(Item::class.java) { o, i -> i.order?.id == o.id }
    .where { _, i -> i.qty > 0 }
    .distinct()
assertEquals("select distinct e0 from Order e0 join Item e1 on e1.order.id = e0.id where e1.qty > 0", q.toHql().hql())
```

<a id="join-a-derived-table-or-cte"></a>
### 派生表与 CTE 的 join

[6.9](#69-derived-table-subquery-in-from) 的派生表和 [6.10](#610-cte-with) 的 CTE 可以像实体一样 join（决策 D51）。
写法是 `join(cte) { c, x -> ... }`、`leftJoin(cte) { ... }`、`join(qb.from(...)...select { Row(...) }) { ... }`、`leftJoin(...) { ... }`，可以作为第二个或第三个根使用。

- HQL 是 `with t as (...) ... join t e1 on ...` 或 `join (select ...) e1 on ...`。
- 行类型是 Kotlin data class（或 Java record）（J5）。属性（`x.total`）读取列。
- 在一条语句中两次读取同一个 `Cte` 对象时，定义只写一次，两个 join 读取同一个名字：`join t e1 on ... left join t e2 on ...`。不同的 `Cte` 对象各自得到名字，例如 `with t as (...), t2 as (...)`（在三种数据库上实测）。
- 派生行没有 id，所以 `x == null` 是错误（`LQL-3067`）。请像 `x.customerId == null` 这样比较列。
- Hibernate 6.6 接受实体的 `select distinct`、派生表和 CTE 的 inner/left join，以及子查询内的实体 join（在三种数据库上实测）。

**(已测试)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)，region `join-cte`：

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
        + " join t e1 on e1.customerId is not null and e1.customerId = e0.id where e1.total > 100.0", rows.toHql().hql())
```

`x.customerId` 是可空的 `Int?` 时，`x.customerId == c.id` 变为 `e1.customerId is not null and e1.customerId = e0.id`（见上面的 region）。无名派生表的 join 由 Java 测试（[`JoinQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java)，region `join-derived`）确认。

两次读取同一个 CTE 的写法由 Java 测试（[`JoinQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java)，region `cte-reuse`）确认。

<a id="what-a-join-query-has"></a>
### join 查询的函数

| 调用之后 | 函数 |
|---|---|
| `join`、`leftJoin`、`crossJoin` | `where`、`join` / `leftJoin` / `crossJoin`（最多三个实体，包括派生表和 CTE）、`distinct`、`orderBy`、`select`、`limit`、`toList`、`toSet`、`first`、`count`、`exists`、`toHql` |
| `orderBy { o, c, by -> ... }` | `select`、`limit`、`offset`、`toList`、`first`，以及排序后查询的其他函数（[4. 排序与分页](04-order-paging.md)） |

子查询内的 join 见 [5. 子查询](05-subquery.md#joins-inside-a-subquery)（D51）。
v0.1 中尚不支持：insert 中的 join，超过三个实体，`SortSpec` 的 join 形式。
update 和 delete 中的 join 决定不再添加（D51）。请使用关联路径（U9）或 `sub`（D49）。

---

<a id="69-derived-table-subquery-in-from"></a>
## 6.9 派生表（FROM 中的子查询）

用 `select` 创建一个值查询（data class，见 [6.3](#63-select-a-data-class-pair-or-triple)），然后把它传给 `qb.from(...)`。
它的行会成为外层查询的表（决策 D46）。
结果是 `DerivedQuery<R>`，`R` 是行类型。

派生表已用 **Java** Lambda 在 [`DerivedQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DerivedQueryTest.java) 中测试（H2、MariaDB、Oracle）。目前还没有 Kotlin 测试。
下面的 Kotlin 代码是把已测试的 Java 查询改写为 Kotlin 语法。测试代码见[本页的 Java 版本](../java/06-select-group.md#69-derived-table-subquery-in-from)。

```kotlin
data class CustomerTotal(val customerId: Long?, val cnt: Long, val total: Double?)

// 分组查询的行成为外层查询的表；r.total 读取列 "total"
val q = qb.from(qb.from(Order::class.java)
        .where { it.customer != null }
        .groupBy { it.customer?.id }
        .select { CustomerTotal(it.customer?.id, Agg.count(), Agg.sum(it.price)) })
    .where { r -> r.total!! > 100 }
    .orderBy { r, by -> by.desc(r.total) }
// HQL: select e0.customerId, e0.cnt, e0.total from (select d0.customer.id as customerId, count(*) as cnt,
//      sum(d0.price) as total from Order d0 where d0.customer is not null group by d0.customer.id) e0
//      where e0.total > 100.0 order by e0.total desc nulls last
```

- 内层查询使用别名 `d0`（其中的 join 为 `dj1` 等），外层查询使用 `e0`。
- 每个 data class 属性按主构造函数的顺序决定一个列名：`as customerId`、`as cnt`、`as total`（规则 J5）。
- 在外层 Lambda 中，用属性读取列。`r.total` 就是 `e0.total`。
- 外层查询提供 `where`、`orderBy`、`limit`、`offset`、`select`、`toList`、`first`、`count` 和 `toHql`。

也可以 select 行中的一个值（`.select { r -> r.customerId }`）。内层和外层的捕获值按顺序绑定。
内层查询的 `limit` 和 `offset` 保留在内层查询中（HQL `limit`、`offset`）。所以可以对前几行再次排序。

```kotlin
data class Priced(val id: Long?, val price: Double?)

// 价格最高的三个订单中最便宜的那个
val cheapestOfTop = qb.from(qb.from(Order::class.java)
        .orderBy { o, by -> by.desc(o.price) }
        .limit(3)
        .select { Priced(it.id, it.price) })
    .orderBy { r, by -> by.asc(r.price) }
    .first()
```

**关于 SQL 块。** [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java) 把同样形式但更简单的 HQL 直接交给 Hibernate，并记录在 H2 2.3、MariaDB 10.11 和 Oracle 23.26（Hibernate 6.6.5）上得到的 SQL。如果 Hibernate 的 SQL 发生变化，测试就会失败。从这个块可以看到各数据库如何写派生表。

**各数据库的 SQL（Hibernate 对这种 HQL 形式生成的 SQL）** — 来自 `expected-sql/` 的 [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)，示例 `06-derived`：

```sql
-- HQL: select r.customerId, r.cnt, r.total from (select o.customer.id as customerId, count(*) as cnt, sum(o.price) as total from Order o group by o.customer.id) r where r.total > 100

-- H2
select r1_0.customerId,r1_0.cnt,r1_0.total
from (select o1_0.customer_id,count(*),sum(o1_0.price) from orders o1_0 group by o1_0.customer_id) r1_0(customerId,cnt,total)
where r1_0.total>100

-- MariaDB, Oracle
select r1_0.customerId,r1_0.cnt,r1_0.total
from (select o1_0.customer_id customerId,count(*) cnt,sum(o1_0.price) total from orders o1_0 group by o1_0.customer_id) r1_0
where r1_0.total>100
```

H2 把列清单放在别名上（`r1_0(customerId,cnt,total)`）。MariaDB 和 Oracle 把列别名放在子查询内部。

规则：

- 行类型必须是 **Kotlin data class**（或 Java record）（规则 J5）。
  lambdaQL 按主构造函数的顺序从 data class 属性中取得列名。
- 这里不能使用普通类、`Pair`、`Triple` 或单个值，因为 lambdaQL 无法确定列名。这时会抛出 `TranslationException`（`LQL-3064`，已用 Java 以单个值测试）。

在实体查询中 join 派生表的写法见 [派生表与 CTE 的 join](#join-a-derived-table-or-cte)（D51）。

v0.1 中尚不支持：派生表之间的 join，以及在 update、delete 或 insert 中使用派生表。

实体的 `@SQLRestriction` 和 `@SoftDelete` 条件，Hibernate 也会加到实体 join 的 `on` 和派生表内部（在 MariaDB 10.11、Oracle 23.26 上实测）。lambdaQL 不单独处理这些条件。

---

<a id="610-cte-with"></a>
## 6.10 CTE（`with`）

用 `qb.with(valueQuery)` 创建一个类型为 `Cte<R>` 的句柄，然后用 `qb.from(handle)` 读取它。
内层查询与派生表（6.9）相同。

```kotlin
val perCustomer = qb.from(Order::class.java)
    .where { it.customer != null }
    .groupBy { it.customer?.id }
    .select { CustomerTotal(it.customer?.id, Agg.count(), Agg.sum(it.price)) }

val t: Cte<CustomerTotal> = qb.with(perCustomer)
val rows = qb.from(t).where { r -> r.cnt >= 2 }.toList()
// HQL: with t as (select d0.customer.id as customerId, count(*) as cnt, sum(d0.price) as total
//      from Order d0 where d0.customer is not null group by d0.customer.id)
//      select e0.customerId, e0.cnt, e0.total from t e0 where e0.cnt >= 2L
```

**各数据库的 SQL（Hibernate 对这种 HQL 形式生成的 SQL）** — 由 `PlannedSqlTest` 记录。来自 `expected-sql/` 的 [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)，示例 `06-cte`：

```sql
-- HQL: with t as (select o.customer.id as customerId, count(*) as cnt, sum(o.price) as total from Order o group by o.customer.id) select x.customerId, x.cnt, x.total from t x where x.total > 100

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

- CTE 的名字是 `t`。外层查询总是给它别名 `e0`（`from t e0`）。HQL 需要别名（规则 J6）。
- CTE 的列名由 select 别名（`as customerId`）决定。HQL 不接受 CTE 名后面的列清单，列清单由 Hibernate 加到 SQL 中（规则 J6）。
- MariaDB 和 Oracle 执行真正的 `with`。在 H2 上，Hibernate 把它展开为派生表。结果相同（已用 Java Lambda 在三种数据库上测试）。
- 行类型的规则与派生表相同：data class 或 record（J5）。
- 在实体查询中 join CTE 的写法见 [派生表与 CTE 的 join](#join-a-derived-table-or-cte)（D51）。
- 同一个 CTE 可以在一个查询中读取两次。定义只写一次（见 [派生表与 CTE 的 join](#join-a-derived-table-or-cte)）。
- v0.1 中尚不支持：在 update、delete 或 insert 中使用 CTE。

---

<a id="611-rules-j1-to-j8"></a>
## 6.11 规则 J1 至 J8

| # | 规则 | 状态 |
|---|---|---|
| J1 | `select` 的 Lambda 返回**一个值表达式**或**一次构造函数调用**。每个构造函数参数都是一个值表达式（[3. 条件（`where`）](03-where.md) 的规则）。只在 `select` 中使用的关联路径会变成 `left join` 别名，因此不会丢失行。必需关联（`optional = false`）是内连接（A4）。如果 `where` 使用同一条路径，`select` 会使用 `where` 的 join（R24）。 | 已实现（已测试，也用 Kotlin Lambda 测试） |
| J2 | 对于构造函数，lambdaQL 只查询列，并自己调用构造函数。它不会写 `select new`。`Pair`/`Triple` 也以同样方式构建。 | 已实现（已测试，也用 Kotlin Lambda 测试） |
| J3 | 有聚合时，`select` 和 `having`（以及 `insert ... select` 的值）中每个非聚合值都必须是 `groupBy` 的键。否则会在分析时报错。 | 已实现（用 Java Lambda 测试）。错误是 `TranslationException`（`LQL-3035`，见 [11](../11-errors-and-languages.md)） |
| J4 | 没有行或所有值都是 NULL 时，`sum`/`avg`/`min`/`max` 返回 NULL，`count` 返回 0。构造函数参数请写成可空类型。NULL 进入非空参数时会抛出 `IllegalStateException`。 | 已实现（用 Java Lambda 测试） |
| J5 | 派生表或 CTE 的行类型：**只能是 Kotlin data class 或 Java record**。普通类只允许用于简单的 `select`。否则为 `LQL-3064`。也适用于被 join 的派生表和 CTE 的行（D51）。派生行不能与 `null` 比较（`LQL-3067`）。 | 已实现（用 Java Lambda 测试） |
| J6 | 用 `qb.with(...)` 创建的 CTE 用 select 别名为列命名：`with t as (select d0.customer.id as customerId, ...) select e0.customerId ... from t e0`。HQL 不接受名称后面的列清单。Hibernate 会以 `SyntaxException` 拒绝 `with t (col list) as (...)`。列清单由 Hibernate 自己写进 SQL（在 MariaDB 和 Oracle 上为 `with t (customerId,cnt,total) as (...)`）。H2 把 CTE 变成派生表。外层查询总是使用别名（`from t e0`）。 | 已实现（用 Java Lambda 测试） |
| J7 | `distinct()` 输出 `select distinct`。与排序一起使用时，排序键必须在 select 列表中（数据库的规则）。lambdaQL 会在分析时检查这一点。在 join 结果的 `distinct()` 中，排序键必须是主实体自身的列（D51）。 | 已实现（用 Java Lambda 测试） |
| J8 | 查询列可以与排序（[4. 排序与分页](04-order-paging.md)）、分页和子查询（[5. 子查询](05-subquery.md)）一起使用。按聚合排序（`by.desc(s.total)`）是 v0.2 候选。 | 已实现，按聚合排序除外 |

[`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java) 中的 Java 测试 region `rules` 检查了 J3、`where` 中的 `Agg`、J7 和 J4。见[本页的 Java 版本](../java/06-select-group.md#611-rules-j1-to-j8)。

---

<a id="612-not-in-v01"></a>
## 6.12 v0.1 中不包含的内容

| 项目 | 状态 |
|---|---|
| 按聚合结果或 select 别名排序（例如 `orderBy { s, by -> by.desc(s.total) }`），以及分组查询上的 `orderBy` | v0.2 候选 |
| 用户自定义聚合函数和窗口函数 | v0.2 候选 |
| insert 中的 join，超过三个实体，子查询内超过三个实体 | 以后 |
| update、delete 中的 join | 不添加（D51）。使用关联路径（U9）或 `sub`（D49） |
| DML 中的派生表和 CTE | 以后 |

本章的错误是带错误码的 `TranslationException`：错误的分组键（J3）为 `LQL-3035`，`where` 中的 `Agg` 为 `LQL-3005`，使用 `distinct` 时未 select 的排序键（J7）为 `LQL-3046`，派生表或 CTE 的行类型（J5）为 `LQL-3064`。见 [11. 错误与语言](../11-errors-and-languages.md)。

---

[Kotlin 路线](README.md) | 上一章：[5. 子查询](05-subquery.md) | 下一章：[7. 更新、插入、删除](07-dml.md) | [本页的 Java 版本](../java/06-select-group.md)
