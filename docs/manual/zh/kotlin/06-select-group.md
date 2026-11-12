# 6. 查询列、聚合与分组（Kotlin）

> [!NOTE]
> **状态：已设计，尚未实现。**
> 本章的所有内容都是已确定的设计（决定 D27，里程碑 M9）。
> Kotlin 语法已经编译并用原型检查过（kotlinc 2.3.21），HQL 形式已在 H2、MariaDB 和 Oracle 上运行过。
> 目前它在 lambdaQL 中**还不能**运行。目前只有分析核心以及 `where` + `toList()` 可以工作
> （已在 H2、MariaDB 和 Oracle 上测试）。完整状态见[路线 README](README.md)。

[Kotlin 路线](README.md) | [手册首页](../README.md) | 上一章：[5. 子查询](05-subquery.md) | 下一章：[7. 更新、插入、删除](07-dml.md) | [本页的 Java 版本](../java/06-select-group.md)

公共页面：[1. 概述](../01-overview.md) · [11. 错误与语言](../11-errors-and-languages.md)

本章介绍如何选择查询返回**什么**：

- 每行一个值（`select { it.price }`）
- Kotlin data class、`Pair` 或 `Triple`
- 带 `groupBy` 和 `having` 的聚合（`count`、`sum`、`avg` 等）
- FROM 子句中的子查询（派生表）和 CTE（`with`）

## 目录

- [6.1 示例实体与结果类型](#61-example-entities-and-result-types)
- [6.2 查询一个值](#62-select-one-value)
- [6.3 查询 data class、Pair 或 Triple](#63-select-a-data-class-pair-or-triple)
- [6.4 聚合函数：`Agg`](#64-aggregate-functions-agg)
- [6.5 分组（group by）](#65-group-by)
- [6.6 Having](#66-having)
- [6.7 派生表（FROM 中的子查询）](#67-derived-table-subquery-in-from)
- [6.8 CTE（`with`）](#68-cte-with)
- [6.9 规则 J1 到 J8](#69-rules-j1-to-j8)
- [6.10 v0.1 中不包含的内容](#610-not-in-v01)

---

<a id="61-example-entities-and-result-types"></a>
## 6.1 示例实体与结果类型

示例使用以下实体（见 [2. 入门](02-getting-started.md#example-entities)）：

| 实体 | 这里使用的属性 |
|---|---|
| `Order` | `id`、`price`、`status`、`active`、`customer`（多对一） |
| `Customer` | `id`、`name`、`grade` |
| `Item` | `order`、`price`、`qty` |

以及以下结果类型：

```kotlin
data class OrderView(val id: Long?, val customerName: String?, val price: Double?)
data class Stat(val customerId: Long?, val cnt: Long, val total: Double?)
data class Total(val cnt: Long, val avgPrice: Double?)
```

> [!TIP]
> 在 data class 中请使用可空类型（`Double?`）。
> 列或聚合的结果可能为 NULL。如果 NULL 被传入非空
> 参数，构造函数会抛出 `NullPointerException`（规则 J4）。
> `count` 永远不会返回 NULL，因此 `cnt: Long` 没有问题。

---

<a id="62-select-one-value"></a>
## 6.2 查询一个值

`select` 接收一个 Lambda。Lambda 返回一个值。结果是由这个值组成的列表。

```kotlin
val prices: List<Double?> = qb.from(Order::class.java)
    .where { it.status == 1 }
    .select { it.price }
    .toList()
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

关联路径是一个列，与 `where` 中相同（规则 R24）。在 `select` 中，lambdaQL 会添加 `left join`，因此没有客户的订单不会丢失（在 `where` 中，同一条路径默认是内连接）：

```kotlin
.select { it.customer?.name }
// → select c1.name from Order o left join o.customer c1
```

**各数据库的 SQL（计划中）** — Hibernate 对计划中 HQL 的翻译，由 `PlannedSqlTest` 记录；lambdaQL 目前还不会生成此查询。来自 `expected-sql/` 的 [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)，示例 `06-select-path`：

```sql
-- H2, MariaDB, Oracle (same)
select c1_0.name
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
```

`it.customer!!.name` 会得到相同的 HQL。
如果路径以关联实体的 `@Id` 结尾，就不会有 join。
`it.customer?.id` 读取外键列 `customer_id`。

其他值的写法：

```kotlin
.select { it.description ?: "" }              // coalesce(o.description, '')
.select { it.customer?.name ?: "none" }       // coalesce(c1.name, 'none') + left join
.select { (it.price ?: 0.0) * 2 }             // coalesce(o.price, 0.0) * 2
```

布尔 Elvis（`select { it.flag ?: false }`）以及值 `if`/`when` 会被拒绝（限制 KC21、KC6）。

---

<a id="63-select-a-data-class-pair-or-triple"></a>
## 6.3 查询 data class、Pair 或 Triple

在 Lambda 中调用构造函数。每个构造函数参数都是一个值表达式。

```kotlin
// data class
val list = qb.from(Order::class.java)
    .select { o -> OrderView(o.id, o.customer?.name, o.price) }
    .toList()

// Pair（Triple 也可以）
val pairs = qb.from(Order::class.java)
    .select { o -> o.customer?.name to o.price }
    .toList()
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
对于 `Pair` 和 `Triple`，lambdaQL 读取列并自己构建对象。

可以返回的类型：

| 结果类型 | 简单的 `select` | 派生表或 CTE 的行类型 |
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
它们是 SQL 标准函数，因此由 lambdaQL 提供。（关于你自己的数据库函数，见 [8. 存储过程与数据库函数](08-procedures-functions.md)。）

| 函数 | SQL |
|---|---|
| `Agg.count()` | `count(*)` |
| `Agg.count(x)` | `count(x)` |
| `Agg.countDistinct(x)` | `count(distinct x)` |
| `Agg.sum(x)` | `sum(x)` |
| `Agg.avg(x)` | `avg(x)` |
| `Agg.min(x)` | `min(x)` |
| `Agg.max(x)` | `max(x)` |

在 Kotlin 看来的类型：

| 函数 | 返回 |
|---|---|
| `count()`、`count(x)`、`countDistinct(x)` | `Long` |
| `sum(x)` | `x` 的类型（对于 `Double?` 就是 `Double?`） |
| `avg(x)` | `Double`（在 SQL 中可能为 NULL；请当作 `Double?` 处理） |
| `min(x)`、`max(x)` | `x` 的类型（任何 `Comparable`，可空也可以） |

导入是可选的。你可以导入单个函数：

```kotlin
import org.lambdaql.Agg.sum

.having { sum(it.price)!! > 1000 }
```

这不会与 `xs.sum()` 或 `xs.count()` 这样的 Kotlin 标准库扩展函数冲突（已用编译器检查）。

要点：

- 返回值只是一个**占位符**。Lambda 永远不会执行，因此这个值永远不会被使用。
- 如果在 lambdaQL Lambda **外部**调用 `Agg.sum(...)`，会抛出 `IllegalStateException`。
- 不能在 `where` 中使用 `Agg`。对聚合的条件请使用 `having`。在 `where` 中使用 `Agg` 会在分析时报错。

### 对所有行聚合（没有 group by）

```kotlin
val t: List<Total> = qb.from(Order::class.java)
    .where { it.status == 1 }
    .select { Total(Agg.count(), Agg.avg(it.price)) }
    .toList()
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

| 函数 | 没有行，或所有值都为 NULL |
|---|---|
| `sum`、`avg`、`min`、`max` | `NULL` |
| `count` | `0` |

这是 SQL 的正常行为。

---

<a id="65-group-by"></a>
## 6.5 分组（group by）

用 `groupBy` 写出分组键。lambdaQL 不会猜测分组键。

### 一个分组键

```kotlin
val stats = qb.from(Order::class.java)
    .groupBy { it.customer?.id }
    .select { Stat(it.customer?.id, Agg.count(), Agg.sum(it.price)) }
    .toList()
```

HQL（分组键以 `@Id` 结尾，因此没有 join）：

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

### 多个分组键

用 `listOf(...)` 返回分组键列表，或使用 `Pair`（`a to b`）或 `Triple`。

```kotlin
.groupBy { listOf(it.customer?.id, it.status) }
// → group by o.customer.id, o.status

.groupBy { it.customer?.id to it.status }                     // Pair：结果相同
.groupBy { Triple(it.customer?.id, it.status, it.active) }    // 三个分组键
```

分组键按你书写的顺序使用。
`groupBy` 中的 `listOf(...)` 是**唯一**允许在 Lambda 内部创建列表的地方。

不允许作为分组键列表的写法：

| 写法 | 原因 |
|---|---|
| `arrayOf(a, b)` | 类型混合时无法编译 |
| `setOf(a, b)` | Set 会去除重复且没有顺序 |

### 每个非聚合值都必须是分组键（规则 J3）

当查询包含聚合时，`select` 和 `having` 中每个**不是**聚合的值都必须是 `groupBy` 的分组键。这是 SQL 的规则。lambdaQL 会在分析时检查它。

```kotlin
data class Stat2(val customerId: Long?, val status: Int?, val cnt: Long)

// 错误：it.status 不是分组键
qb.from(Order::class.java)
    .groupBy { it.customer?.id }
    .select { Stat2(it.customer?.id, it.status, Agg.count()) }
// → TranslationException
```

---

<a id="66-having"></a>
## 6.6 Having

`having` 是作用于分组的条件。它使用与 `where` 相同的 Lambda 规则（[3. 条件](03-where.md)），并且可以使用 `Agg`。

```kotlin
val big = qb.from(Order::class.java)
    .groupBy { it.customer?.id }
    .having { Agg.sum(it.price)!! > 1000 }
    .select { Stat(it.customer?.id, Agg.count(), Agg.sum(it.price)) }
    .toList()
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

`!!` 只是为了让代码能够编译（`Double?` 的 `sum` 是 `Double?`）。lambdaQL 会忽略它（决定 D23）。

---

<a id="67-derived-table-subquery-in-from"></a>
## 6.7 派生表（FROM 中的子查询）

把一个 Lambda 传给 `qb.from`。这个 Lambda 接收子查询工厂（`sub`）并构建内部查询。
内部的 `select` 决定行类型。在外部查询中，像使用实体一样使用这一行。

```kotlin
val rows: List<Stat> = qb.from { sub ->
        sub.from(Order::class.java)
            .groupBy { it.customer?.id }
            .select { Stat(it.customer?.id, Agg.count(), Agg.sum(it.price)) }
    }
    .where { r -> r.total!! > 100 }
    .toList()
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

- 行类型必须是 **Kotlin data class**（或 Java record）（规则 J5）。
  lambdaQL 按 data class 的 `componentN` 顺序取得列别名。
- 这里不允许使用普通类。lambdaQL 无法知道其构造函数参数的名字。
- 在外部 Lambda 中，通过属性读取这一行（`r.total`）。

---

<a id="68-cte-with"></a>
## 6.8 CTE（`with`）

用 `qb.with { ... }` 创建一个句柄。然后在 `qb.from(...)` 中使用这个句柄。
同一个句柄可以使用多次。

```kotlin
val t = qb.with { sub ->
    sub.from(Order::class.java)
        .groupBy { it.customer?.id }
        .select { Stat(it.customer?.id, Agg.count(), Agg.sum(it.price)) }
}

val rows = qb.from(t).where { r -> r.total!! > 100 }.toList()
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

- lambdaQL 总是在外部查询中为 CTE 指定别名（`from t x`）。HQL 需要它（规则 J6）。
- CTE 的列名由 select 别名（`as customerId`）确定。HQL 不接受 CTE 名称后面的列清单；列清单由 Hibernate 加到 SQL 中（规则 J6）。
- MariaDB 和 Oracle 会执行真正的 `with`。在 H2 上，Hibernate 会把它展开为派生表。结果相同（已检查）。
- 行类型规则与派生表相同：data class 或 record（J5）。

---

<a id="69-rules-j1-to-j8"></a>
## 6.9 规则 J1 到 J8

| # | 规则 |
|---|---|
| J1 | `select` Lambda 返回**一个值表达式**或**一个构造函数调用**。每个构造函数参数都是一个值表达式（规则见 [3. 条件](03-where.md)）。只在 `select` 中使用的关联路径变成 `left join` 别名，因此不会丢失行。如果 `where` 使用同一条路径，`select` 会使用 `where` 的 join（R24）。 |
| J2 | 对于构造函数，lambdaQL 只查询列，并使用带有你的类的 Hibernate 类型化查询。它不会写 `select new`。`Pair`/`Triple` 由 lambdaQL 根据列构建。 |
| J3 | 有聚合时，`select` 和 `having` 中每个非聚合值都必须是 `groupBy` 的分组键。否则：分析时抛出 `TranslationException`。 |
| J4 | 没有行或所有值都为 NULL 时，`sum`/`avg`/`min`/`max` 返回 NULL。`count` 返回 0。请使用可空的构造函数参数。 |
| J5 | 派生表或 CTE 的行类型：**只能是 Kotlin data class 或 Java record**。普通类只允许用于简单的 `select`。 |
| J6 | 用 `qb.with { }` 创建的 CTE 用 select 别名为列命名：`with t as (select o.customer.id as customerId, ...) select x.customerId ... from t x`。HQL 不接受名称后面的列清单：Hibernate 会以 `SyntaxException` 拒绝 `with t (col list) as (...)`。列清单由 Hibernate 自己写进 SQL（在 MariaDB 和 Oracle 上为 `with t (customerId,cnt,total) as (...)`）；H2 把 CTE 变成派生表。外层查询总是为它使用别名（`from t x`）。 |
| J7 | `distinct()` 输出 `select distinct`。与排序一起使用时，排序键必须在查询列列表中（数据库规则）。lambdaQL 会在分析时检查这一点。 |
| J8 | 查询列可以与排序（[4. 排序与分页](04-order-paging.md)）、分页和子查询（[5. 子查询](05-subquery.md)）一起使用。 |

---

<a id="610-not-in-v01"></a>
## 6.10 v0.1 中不包含的内容

| 项目 | 状态 |
|---|---|
| 按聚合结果或查询列别名排序，例如 `orderBy { s, by -> by.desc(s.total) }` | v0.2 候选 |
| 用户自定义聚合函数和窗口函数 | v0.2 候选 |
| 基于 join 的投影，`select { o, c -> OrderView(o.id, c.name, o.price) }` | 计划中；join 语法尚未最终确定 |
| `toList()` 以外的结果函数 | 计划中 |

本章中的错误（错误的分组键、`where` 中的 `Agg`、把普通类用作派生表的行）都是带有 `[LQL-xxxx]` 代码的 `LambdaQLException` 子类。见 [11. 错误与语言](../11-errors-and-languages.md)。

---

[Kotlin 路线](README.md) | 上一章：[5. 子查询](05-subquery.md) | 下一章：[7. 更新、插入、删除](07-dml.md) | [本页的 Java 版本](../java/06-select-group.md)
