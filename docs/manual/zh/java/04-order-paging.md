<a id="4-sorting-and-paging-java"></a>
# 4. 排序与分页（Java）

> [!NOTE]
> **状态**
> - **核心部分已实现并测试**，在 H2、MariaDB 10.11 和 Oracle 23.26（Hibernate 6.6.5）上：带有 `by.asc`、`by.desc` 和 `by.sort` 的 `orderBy((o, by) -> ...)`，一个排序键的写法 `orderBy(o -> key)` 和 `orderBy(key, Direction)`（也可以使用 `Order::getPrice` 这样的 getter 方法引用），`Direction.parse` / `reversed`，`limit` / `offset`，`orderBy` 之后的 `first()`，以及可重用排序 `SortSpec`（`of`、`ofPath`、`then`、`reversed`、`orderBy(spec)`）。Spring `Sort` 和 `Pageable` 可以在 Spring Data JPA 模块中使用（[12.5](12-spring-data-jpa.md#125-spring-sort-and-pageable)）。
> - **尚未实现：** `SortWhitelist`、join 写法 `(o, c, by)`（`SortSpec` 也是）以及子查询内部的排序。
> - 标记为 **(已测试)** 的示例是从测试代码中复制的。本章中的其他 HQL 遵循相同的规则，但没有经过测试。

[Java 路线](README.md) | [手册首页](../README.md) | 上一章：[3. 条件](03-where.md) | 下一章：[5. 子查询](05-subquery.md) | [本页的 Kotlin 版本](../kotlin/04-order-paging.md)

公共页面：[1. 概述](../01-overview.md) · [11. 错误与语言](../11-errors-and-languages.md)

---

<a id="41-one-lambda-for-the-whole-sort"></a>
## 4.1 用一个 Lambda 表达整个排序

你用**一个 Lambda** 编写排序。Lambda 接收实体，并以排序构建器 `by` 作为最后一个参数。
你用 `by.asc(...)` 和 `by.desc(...)` 添加排序键和方向。

```java
qb.from(Order.class)
  .where(o -> o.isActive())
  .orderBy((o, by) -> by.desc(o.getPrice()).asc(o.getCustomer().getName()).asc(o.getId()))
  .limit(20).offset(40)
  .toList();
// HQL: select e0 from Order e0 left join e0.customer j1
//      where e0.active = true
//      order by e0.price desc nulls last, j1.name asc nulls first, e0.id asc
```

**关于 SQL 块。** `…` 是缩短的 select 列表（实体的所有列），`?` 是绑定参数。这些行来自 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)。该测试使用 Hibernate 6.6.5 在 H2 2.3、MariaDB 10.11 和 Oracle 23.26 上运行每个查询，如果 Hibernate 的 SQL 发生变化，测试就会失败。

**各数据库的 SQL（Hibernate 为此 HQL 生成的 SQL）** — 手册中的 `Order` 有可为 NULL 的 `Double price`，而测试中的 `Order` 没有，所以 `SqlByDatabaseTest` 把这个 HQL 直接交给 Hibernate。来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `sort-overview`：

```sql
-- H2
-- (this HQL is given to Hibernate directly)
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where o1_0.active=true
order by o1_0.price desc,c1_0.name,o1_0.id
offset ? rows
fetch first ? rows only

-- MariaDB
-- (this HQL is given to Hibernate directly)
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where o1_0.active=1
order by o1_0.price desc,c1_0.name,o1_0.id
limit ?,?

-- Oracle
-- (this HQL is given to Hibernate directly)
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where o1_0.active=1
order by o1_0.price desc nulls last,c1_0.name asc nulls first,o1_0.id
offset ? rows
fetch first ? rows only
```

- `by` 这个名字只是本手册的习惯。你可以使用任何名字。
- 不需要静态导入。`by` 是一个 Lambda 参数。
- 排序键和它的方向写在一起。它们的顺序永远不会错乱。
- lambdaQL 读取 Lambda，但从不执行它。`by` 的方法只是描述顺序。

标记为 **(已测试)** 的示例是从 [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java) 中复制的。
该测试检查 HQL，执行查询，并把行的顺序与在 Java 中用同一排序得到的结果进行比较。它在 H2、MariaDB 和 Oracle 上都能通过。
它使用 [3.3](03-where.md#33-basic-form) 中的测试实体：`price` 是 `double`，`status` 是 `short`。这些排序键永远不会为 NULL，因此它们的 HQL 中没有 `nulls` 部分。
在其他示例中，`price` 是可以为 NULL 的 `Double`（见 [3.2](03-where.md#32-example-entities)），因此会得到 `nulls first` 或 `nulls last`（见 [4.4](#44-null-ordering-is-the-same-on-every-database)）。

**(已测试)** [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java)，region `sort-basic`：

```java
RenderedQuery q = qb.from(Order.class)
        .where(o -> o.isActive())
        .orderBy((o, by) -> by.desc(o.getPrice()).asc(o.getId()))
        .toHql();
// price and id can never be NULL, so there is no "nulls" part
assertEquals("select e0 from Order e0 where e0.active = true order by e0.price desc, e0.id asc", q.hql());
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `sort-basic`：

```sql
-- H2
select …
from orders o1_0
where o1_0.active=true
order by o1_0.price desc,o1_0.id

-- MariaDB, Oracle
select …
from orders o1_0
where o1_0.active=1
order by o1_0.price desc,o1_0.id
```

`toHql()` 返回 HQL，但不执行查询。`toList()` 会执行查询。

<a id="42-the-short-forms-one-key"></a>
## 4.2 简短写法（一个排序键）

如果只有一个排序键，可以省略 `by`：

| Java | HQL |
|---|---|
| `orderBy(o -> o.getPrice())` | `order by e0.price asc nulls first` |
| `orderBy(o -> o.getUpdateAt(), Direction.DESC)` | `order by e0.updateAt desc nulls last` |
| `orderBy(Order::getPrice)` | `order by e0.price asc nulls first` |
| `orderBy(Order::getPrice, Direction.DESC)` | `order by e0.price desc nulls last` |

不指定 `Direction` 时，简短写法按升序（`ASC`）排序。
lambdaQL 根据 Lambda 参数的个数（一个或两个）区分这些写法。Java 编译器会毫无歧义地选择正确的重载。

方法引用必须是实体的 getter，例如 `Order::getPrice`。静态方法、带参数的方法，或者绑定到某个对象的引用会被拒绝（规则 S13）。
`by` 写法（两个参数）需要 Lambda。在那里使用方法引用会被拒绝。

**(已测试)** [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java)，region `sort-key`（在测试实体中，`price` 是 `double`，因此没有 `nulls` 部分）：

```java
assertEquals("select e0 from Order e0 order by e0.price asc",
        qb.from(Order.class).orderBy(o -> o.getPrice()).toHql().hql());
assertEquals("select e0 from Order e0 order by e0.price desc",
        qb.from(Order.class).orderBy(Order::getPrice, Direction.DESC).toHql().hql());
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `sort-key`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
order by o1_0.price
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `sort-key#2`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
order by o1_0.price desc
```

<a id="43-direction"></a>
## 4.3 `Direction`

`Direction` 有六个值：

| 值 | 含义 | NULL 的位置 |
|---|---|---|
| `ASC` | 升序 | NULL 在**前**（默认策略） |
| `DESC` | 降序 | NULL 在**后**（默认策略） |
| `ASC_NULLS_FIRST` | 升序 | NULL 在前 |
| `ASC_NULLS_LAST` | 升序 | NULL 在后 |
| `DESC_NULLS_FIRST` | 降序 | NULL 在前 |
| `DESC_NULLS_LAST` | 降序 | NULL 在后 |

`Direction` 还有两个辅助方法：

- `Direction.parse(text)` 从文本（例如请求参数）读取方向。大小写无关，`-`、`_` 和空格视为相同：`Direction.parse("desc-nulls-last")` 是 `DESC_NULLS_LAST`。未知的文本会抛出 `IllegalArgumentException`。
- `reversed()` 返回相反的方向。NULL 的位置也会翻转，因此整个顺序都会反过来：`ASC.reversed()` 是 `DESC`，`ASC_NULLS_LAST.reversed()` 是 `DESC_NULLS_FIRST`。

**选择 NULL 的位置**：使用第二个 `boolean` 参数：`by.asc(key, nullsFirst)` 和 `by.desc(key, nullsFirst)`（决策 D35）。
`true` 表示"NULL 在前"，`false` 表示"NULL 在后"。
不带第二个参数时，`asc(key)` 和 `desc(key)` 使用默认的 NULL 策略（决策 D16，见 [4.4](#44-null-ordering-is-the-same-on-every-database)）。

| 你写的 | 等同于 |
|---|---|
| `by.asc(key)` | `ASC`（NULL 在前，默认策略） |
| `by.desc(key)` | `DESC`（NULL 在后，默认策略） |
| `by.asc(key, true)` | `ASC_NULLS_FIRST` |
| `by.asc(key, false)` | `ASC_NULLS_LAST` |
| `by.desc(key, true)` | `DESC_NULLS_FIRST` |
| `by.desc(key, false)` | `DESC_NULLS_LAST` |

**(已测试)** [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java)，region `sort-nulls`（`description` 是可以为 NULL 的 `String`；`id` 永远不会为 NULL）：

```java
// default: asc puts NULL first, desc puts NULL last (the same on every database)
assertEquals("select e0 from Order e0 order by e0.description asc nulls first, e0.id asc",
        qb.from(Order.class).orderBy((o, by) -> by.asc(o.getDescription()).asc(o.getId())).toHql().hql());
// the second argument chooses: true = NULL first, false = NULL last
assertEquals("select e0 from Order e0 order by e0.description asc nulls last, e0.id asc",
        qb.from(Order.class).orderBy((o, by) -> by.asc(o.getDescription(), false).asc(o.getId())).toHql().hql());
assertEquals("select e0 from Order e0 order by e0.description desc nulls first, e0.id asc",
        qb.from(Order.class).orderBy((o, by) -> by.desc(o.getDescription(), true).asc(o.getId())).toHql().hql());
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `sort-nulls`：

```sql
-- H2, MariaDB
select …
from orders o1_0
order by o1_0.description,o1_0.id

-- Oracle
select …
from orders o1_0
order by o1_0.description asc nulls first,o1_0.id
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `sort-nulls#2`：

```sql
-- H2
select …
from orders o1_0
order by o1_0.description asc nulls last,o1_0.id

-- MariaDB
select …
from orders o1_0
order by case when (o1_0.description) is null then 1 else 0 end,o1_0.description,o1_0.id

-- Oracle
select …
from orders o1_0
order by o1_0.description,o1_0.id
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `sort-nulls#3`：

```sql
-- H2
select …
from orders o1_0
order by o1_0.description desc nulls first,o1_0.id

-- MariaDB
select …
from orders o1_0
order by case when (o1_0.description) is null then 0 else 1 end,o1_0.description desc,o1_0.id

-- Oracle
select …
from orders o1_0
order by o1_0.description desc,o1_0.id
```

在 SQL 中可以看到 NULL 策略。H2 和 MariaDB 在与自身默认值相同时省略该子句。Oracle 的默认值正好相反，所以写出 `asc nulls first` / `desc nulls last`。MariaDB 没有 `NULLS` 语法，所以对于不是默认值的位置，它先按 `case when … is null then … end` 排序。

**当方向来自一个变量时**，请使用 `by.sort(key, direction)` 并传入捕获的 `Direction`：

```java
.orderBy((o, by) -> by.desc(o.getDescription(), false).asc(o.getId()))   // nulls last

Direction dir = request.isNewestFirst() ? Direction.DESC : Direction.ASC;
.orderBy((o, by) -> by.sort(o.getUpdateAt(), dir).asc(o.getId()))   // 每次调用都会读取 dir
```

这里的三元表达式 `request.isNewestFirst() ? ... : ...` 没有问题，因为它在 Lambda **外部**执行。

**(已测试)** [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java)，region `sort-dynamic`：

```java
Direction dir = Direction.parse(text);           // for example from a request parameter
OrderedSelectQuery<Order> q = qb.from(Order.class)
        .orderBy((o, by) -> by.sort(o.getDescription(), dir).asc(o.getId()));
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `sort-dynamic`：

```sql
-- H2, MariaDB
select …
from orders o1_0
order by o1_0.description desc,o1_0.id

-- Oracle
select …
from orders o1_0
order by o1_0.description desc nulls last,o1_0.id
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `sort-dynamic#2`：

```sql
-- H2
select …
from orders o1_0
order by o1_0.description asc nulls last,o1_0.id

-- MariaDB
select …
from orders o1_0
order by case when (o1_0.description) is null then 1 else 0 end,o1_0.description,o1_0.id

-- Oracle
select …
from orders o1_0
order by o1_0.description,o1_0.id
```

测试把这段代码执行两次，`text` 分别为 `"desc"` 和 `"asc_nulls_last"`。
HQL 依次是 `... order by e0.description desc nulls last, e0.id asc` 和 `... order by e0.description asc nulls last, e0.id asc`。
Lambda 只分析一次。每次执行查询时都会读取 `dir`（规则 S10）。方向为 `null` 时，查询执行时会抛出异常。

> [!TIP]
> 单独一个 `false` 表达的信息不多。Java 和 Kotlin 都写作 `by.desc(x, false)`：Kotlin 不能对 Java 方法使用具名参数（`nullsFirst = false`）。
> 因此，加上 `// nulls last` 这样的简短注释是个好习惯。

<a id="44-null-ordering-is-the-same-on-every-database"></a>
## 4.4 NULL 的排序在每个数据库上都相同

各数据库对 NULL 的位置并不一致：

| | H2 | MariaDB | Oracle |
|---|---|---|---|
| `ASC` 默认 | NULL 在前 | NULL 在前 | NULL 在**后** |
| `DESC` 默认 | NULL 在后 | NULL 在后 | NULL 在**前** |
| SQL 中的 `NULLS FIRST/LAST` | 支持 | **不支持（语法错误）** | 支持 |

lambdaQL 用一条策略解决这个问题：**NULL 是最小的值。**

- `ASC` = `ASC_NULLS_FIRST`
- `DESC` = `DESC_NULLS_LAST`

这与 Java 的 `Comparator.nullsFirst(Comparator.naturalOrder())` 相同。
使用这条策略，三种数据库都会返回相同的顺序（对于数字和日期）。

**lambdaQL 如何写出它。** 对于每个可能为 NULL 的排序键，lambdaQL 都会把 NULL 的位置写入 HQL，例如 `e0.description asc nulls first`。
永远不会为 NULL 的排序键（像 `double` 这样的基本类型列，或者 `@Id`）没有 `nulls` 部分。
关联后面的列总是可能为 NULL（订单可能没有客户），因此它总会有 `nulls` 部分。

然后 Hibernate 为每种数据库生成 SQL。当该子句就是数据库的默认值时，Hibernate 会去掉它。
MariaDB 没有 `NULLS` 语法，因此对于不是默认值的位置，Hibernate 会在那里写出 `CASE` 表达式。

你会得到的 SQL：

| 你写的 | H2 | MariaDB | Oracle |
|---|---|---|---|
| `asc(key)`、`desc(key)`（`ASC`、`DESC`） | 没有额外子句（本来就是默认值） | 没有额外子句 | 添加 `nulls first` / `nulls last` |
| 与数据库默认值一致的 NULL 位置（`asc(key, b)`、`desc(key, b)`、`*_NULLS_*`） | 没有额外子句 | 没有额外子句 | 没有额外子句 |
| 与数据库默认值**不**一致的 NULL 位置 | `nulls first/last` | Hibernate 生成 `case when x is null ...` | `nulls first/last` |
| 永远不会为 NULL 的排序键，任何方向 | 没有额外子句 | 没有额外子句 | 没有额外子句 |

> [!TIP]
> 在 MariaDB 上，`by.asc(key, false)`（`ASC_NULLS_LAST`）和 `by.desc(key, true)`（`DESC_NULLS_FIRST`）会变成 `CASE` 表达式。数据库无法为这种排序使用索引。能用 `asc(key)` / `desc(key)` 时请尽量使用它们。

<a id="45-what-a-sort-key-can-be"></a>
## 4.5 排序键可以是什么

| 排序键 | 是否允许 | 说明 |
|---|---|---|
| 列：`o.getPrice()` | 是 | 已测试 |
| 对一路径：`o.getCustomer().getName()` | 是 | `left join`，见下文（已测试） |
| 算术运算：`o.getPrice() * 2`、`+`、`-` | 是 | 尚未测试 |
| 拓宽转换（`int` 到 `long`） | 是 | 尚未测试 |
| 实体：`by.desc(o.getCustomer())` | **编译错误** | 实体不是 `Comparable` |
| 不使用实体的值：`by.asc(1)`、捕获的值 | **拒绝** | 见 [4.6](#46-rules-for-the-lambda-body)（已测试） |
| 作用于列的函数：`lower(name)` | 计划中 | |

排序键必须是 `Comparable`。装箱类型（`Double`、`Integer`、`Long`）、`String` 以及 `java.time` 中的类型都是 `Comparable`，因此可以使用。Java 编译器会拒绝实体排序键。
如果你的实体类实现了 `Comparable`，lambdaQL 在读取 Lambda 时会拒绝把它作为排序键。

**关联路径使用 `left join`。** 在 HQL 中，普通路径 `order by o.customer.name` 在三种数据库上都会变成 inner join。没有客户的订单会消失。
lambdaQL 改为添加 `left join`。没有客户的订单会保留在结果中。它们的 `j1.name` 为 NULL，因此在 `asc` 时排在最前面。

**(已测试)** [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java)，region `sort-path`：

```java
RenderedQuery q = qb.from(Order.class)
        .orderBy((o, by) -> by.asc(o.getCustomer().getName()).asc(o.getId()))
        .toHql();
// a path used only for sorting is a LEFT JOIN: the order without a customer stays
assertEquals("select e0 from Order e0 left join e0.customer j1 order by j1.name asc nulls first, e0.id asc", q.hql());
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `sort-path`：

```sql
-- H2, MariaDB
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
order by c1_0.name,o1_0.id

-- Oracle
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
order by c1_0.name asc nulls first,o1_0.id
```

别名与 `where` 共享。如果 `where` 已经 join 了同一条路径，排序就会使用那个 join。
大多数情况下它是内连接：`where` 条件已经去掉了没有客户的订单（决策 D36，见 [3.10](03-where.md#310-association-paths)）。

**(已测试)** [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java)，region `sort-path-where`：

```java
String name = "Kim";
RenderedQuery q = qb.from(Order.class)
        .where(o -> o.getCustomer().getName().equals(name))
        .orderBy((o, by) -> by.desc(o.getCustomer().getName()).desc(o.getPrice()))
        .toHql();
// the condition already joined the customer, so the sort uses the same inner join
assertEquals("select e0 from Order e0 join e0.customer j1 where j1.name = :p1"
        + " order by j1.name desc nulls last, e0.price desc", q.hql());
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `sort-path-where`：

```sql
-- H2, MariaDB
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name=?
order by c1_0.name desc,o1_0.price desc

-- Oracle
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name=?
order by c1_0.name desc nulls last,o1_0.price desc
```

<a id="46-rules-for-the-lambda-body"></a>
## 4.6 Lambda 主体的规则

主体必须是**一条直接的 `by` 调用链**：

```java
// 可以
.orderBy((o, by) -> by.desc(o.getPrice()).asc(o.getId()))

// 会被拒绝：条件化的排序键
.orderBy((o, by) -> flag ? by.desc(o.getPrice()) : by.asc(o.getId()))

// 会被拒绝：循环、if、局部变量
```

lambdaQL 在你调用 `orderBy` 时检查这一点，并抛出 `LambdaAnalysisException`。
每个排序键都必须使用实体。只有常量或捕获值的排序键会被拒绝。

**(已测试)** [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java)，region `sort-rejected`：

```java
// a condition inside the sort lambda (rule S4)
assertThrows(LambdaAnalysisException.class, () -> qb.from(Order.class)
        .orderBy((o, by) -> o.isActive() ? by.asc(o.getPrice()) : by.desc(o.getPrice())));
// a key without the entity
assertThrows(LambdaAnalysisException.class, () -> qb.from(Order.class).orderBy((o, by) -> by.asc(1)));
```

如果方向必须在运行时改变，请把它作为变量传入：`by.sort(key, dir)`。如果排序键本身必须改变，请在普通 Java 代码中用两个分支分别构建查询。

<a id="47-order-of-calls"></a>
## 4.7 调用顺序

构建器类型控制调用顺序。顺序错误时无法编译：

| 调用 | 是否允许 |
|---|---|
| `from(...).where(...).orderBy(...).limit(...).offset(...)` | 是 |
| `from(...).where(...).limit(...).offset(...)`（没有 `orderBy`） | 是。行按 id 排序（见 [4.8](#48-paging)） |
| 调用两次 `orderBy(...)` | 否（编译错误） |
| 在 `orderBy(...)` 之后调用 `where(...)` | 否（编译错误） |
| 在 `limit(...)` 之后调用 `orderBy(...)` | 否（编译错误） |

在 `orderBy`、`limit` 或 `offset` 之后，查询的类型是 `OrderedSelectQuery`。它只有 `limit`、`offset`、`toHql()`、`toList()`、`toSet()` 和 `first()`。`one()`、`count()` 和 `exists()` 只能在 `orderBy`、`limit` 和 `offset` 之前使用（见[获取结果](02-getting-started.md#getting-the-result)）。

<a id="48-paging"></a>
## 4.8 分页

```java
qb.from(Order.class)
  .where(o -> o.getStatus() == 1)
  .orderBy((o, by) -> by.desc(o.getPrice()))
  .limit(20)      // 每页大小
  .offset(40)     // 跳过 40 行（第 3 页）
  .toList();
// HQL: select e0 from Order e0 where e0.status = 1 order by e0.price desc nulls last, e0.id asc
// limit 和 offset 会传给 Hibernate（setMaxResults / setFirstResult）
```

**各数据库的 SQL（Hibernate 为此 HQL 生成的 SQL）** — 手册中的 `Order` 有可为 NULL 的 `Double price`，而测试中的 `Order` 没有，所以 `SqlByDatabaseTest` 把这个 HQL 直接交给 Hibernate。来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `page-design`：

```sql
-- H2
-- (this HQL is given to Hibernate directly)
select …
from orders o1_0
where o1_0.status=1
order by o1_0.price desc,o1_0.id
offset ? rows
fetch first ? rows only

-- MariaDB
-- (this HQL is given to Hibernate directly)
select …
from orders o1_0
where o1_0.status=1
order by o1_0.price desc,o1_0.id
limit ?,?

-- Oracle
-- (this HQL is given to Hibernate directly)
select …
from orders o1_0
where o1_0.status=1
order by o1_0.price desc nulls last,o1_0.id
offset ? rows
fetch first ? rows only
```

**(已测试)** [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java)，region `sort-page`：

```java
OrderedSelectQuery<Order> page = qb.from(Order.class)
        .orderBy((o, by) -> by.asc(o.getStatus()))
        .limit(2)
        .offset(2);
// status has ties, so the id is added as the last key and rows do not move between pages
assertEquals("select e0 from Order e0 order by e0.status asc, e0.id asc", page.toHql().hql());
List<Order> rows = page.toList();
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `sort-page`：

```sql
-- H2, Oracle
select …
from orders o1_0
order by o1_0.status,o1_0.id
offset ? rows
fetch first ? rows only

-- MariaDB
select …
from orders o1_0
order by o1_0.status,o1_0.id
limit ?,?
```

分页 SQL 在各数据库上不同：H2 和 Oracle 得到 `offset ? rows fetch first ? rows only`，MariaDB 得到 `limit ?,?`。没有 offset 时，只写 `fetch first ? rows only` 或 `limit ?`（见下面的 `page-only`）。

`limit` 和 `offset` 接受 0 或更大的值。负值会抛出 `IllegalArgumentException`。

lambdaQL 不会把 `LIMIT` 写入 HQL。Hibernate 会为每种数据库生成正确的 SQL：

| 数据库 | SQL |
|---|---|
| H2 | `offset ? rows fetch first ? rows only` |
| MariaDB | `limit ?, ?` |
| Oracle | `offset ? rows fetch first ? rows only` |

**不带 `orderBy` 的分页。** 你可以不调用 `orderBy`，直接调用 `limit` 和 `offset`。这时 lambdaQL 按 id 对行排序，因此分页是稳定的。

**(已测试)** [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java)，region `page-only`：

```java
RenderedQuery q = qb.from(Order.class).where(o -> o.isActive()).limit(2).toHql();
assertEquals("select e0 from Order e0 where e0.active = true order by e0.id asc", q.hql());
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `page-only`：

```sql
-- H2
select …
from orders o1_0
where o1_0.active=true
order by o1_0.id
fetch first ? rows only

-- MariaDB
select …
from orders o1_0
where o1_0.active=1
order by o1_0.id
limit ?

-- Oracle
select …
from orders o1_0
where o1_0.active=1
order by o1_0.id
fetch first ? rows only
```

**第一行与总行数。**

- `orderBy(...).first()` 以 `Optional` 的形式返回按该顺序排列的第一行。它只读取一行。`offset` 会被保留：`orderBy(...).offset(1).first()` 返回第二行。
- `count()` 只存在于 where 阶段（`orderBy`、`limit` 和 `offset` 之前），因此它总是统计所有匹配的行。对于分页画面，请把 where 阶段的查询保存在一个变量中。在它上面调用 `count()` 得到总数，然后在它上面调用 `orderBy(...).limit(...).toList()` 得到这一页。

两者都已测试。见[获取结果](02-getting-started.md#getting-the-result)。

<a id="tie-breaker-automatic-id"></a>
### 决胜键（自动添加 `id`）

看上面的 HQL：lambdaQL 在末尾添加了 `e0.id asc`。为什么？

- 很多行可能有相同的 `status` 或 `price`。数据库可能以**任意顺序**返回这些行。Oracle 确实会这样（已实测）。
- 这样第 2 页和第 3 页可能会把同一行显示两次，或者漏掉一行。

规则：**当你使用分页，并且排序键中不包含 id 时，lambdaQL 会把根实体的 `@Id asc` 添加为最后一个排序键。**

- 它只会添加一次。如果你的排序键已经包含 id（例如 `by.desc(o.getId())`），就不会添加。
- 没有 `limit` 和 `offset` 时，不会添加任何东西。
- **尚未实现**（join 尚未实现）：有对多 join 时，也会添加被 join 实体的 id。集合的 fetch join 与 `limit`/`offset` 一起使用会被拒绝。

<a id="49-strings-and-special-values"></a>
## 4.9 字符串与特殊值

- 字符串的顺序遵循数据库的排序规则（collation）。MariaDB 的 `_ci` 忽略大小写。Oracle 使用 `NLS_SORT`，它跟随 JVM 的 locale（例如 `de_DE` 会得到德语顺序）。对于 Oracle，我们推荐 `NLS_SORT=BINARY`。
- Oracle 把 `''` 存储为 NULL，因此空字符串会像 NULL 一样排序。
- 各数据库对 `NaN` 的排序不同。

<a id="410-reusable-and-external-sorts"></a>
## 4.10 可重用的排序与外部排序

**`SortSpec`：可以保存起来的排序。** `SortSpec` 是一个可以保存在变量或字段中、并在许多查询中使用的排序。
用 `SortSpec.of(Order.class, (o, by) -> ...)` 创建它。这个 Lambda 与 `orderBy((o, by) -> ...)` 中的相同，因此适用同样的规则（见 [4.6](#46-rules-for-the-lambda-body)）。
然后把它传给 `orderBy(spec)`。

```java
public static final SortSpec<Order> NEWEST =
        SortSpec.of(Order.class, (o, by) -> by.desc(o.getUpdateAt()).asc(o.getId()));

qb.from(Order.class).where(o -> o.isActive()).orderBy(NEWEST).limit(20).toList();
// HQL: select e0 from Order e0 where e0.active = true order by e0.updateAt desc nulls last, e0.id asc
```

- `SortSpec` 不能被修改。`then()` 和 `reversed()` 会返回一个新的 spec。因此一个 spec 可以安全地在许多查询和线程之间共享，例如作为 `static final` 字段。
- lambdaQL 在 spec 被用于 `orderBy` 时检查 Lambda。每个 Lambda 只分析一次。
- 捕获的值是创建 Lambda 那一刻的值。放在 `static final` 字段中的 spec 会一直保留这些值。如果方向必须在运行时改变，请使用 `orderBy((o, by) -> by.sort(key, dir))`（见 [4.3](#43-direction)）。
- 在 `orderBy(spec)` 之后，可以像平常一样分页：`limit`、`offset`、`toList()`、`first()`。

**(已测试)** [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java)，region `sort-spec`：

```java
// keep it, for example as a static final field, and use it in many queries
SortSpec<Order> byDescription = SortSpec.of(Order.class, (o, by) -> by.desc(o.getDescription()).asc(o.getId()));
RenderedQuery q = qb.from(Order.class).where(o -> o.isActive()).orderBy(byDescription).toHql();
assertEquals("select e0 from Order e0 where e0.active = true order by e0.description desc nulls last, e0.id asc", q.hql());
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `sort-spec`：

```sql
-- H2
select …
from orders o1_0
where o1_0.active=true
order by o1_0.description desc,o1_0.id

-- MariaDB
select …
from orders o1_0
where o1_0.active=1
order by o1_0.description desc,o1_0.id

-- Oracle
select …
from orders o1_0
where o1_0.active=1
order by o1_0.description desc nulls last,o1_0.id
```

SQL 与用 `orderBy((o, by) -> ...)` 写出的同样排序相同。Oracle 会加上 `nulls last`，因为它自己对 `desc` 的默认规则正好相反（见 [4.4](#44-null-ordering-is-the-same-on-every-database)）。

**`then()`：在已有排序键之后追加排序键。** `a.then(b)` 先按 `a` 的排序键排序。对于相等的行，再按 `b` 的排序键排序。
`then()` 接受另一个 `SortSpec` 或一个排序 Lambda。

**(已测试)** [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java)，region `sort-spec-then`（在测试实体中，`status` 和 `price` 永远不会为 NULL，因此没有 `nulls` 部分）：

```java
SortSpec<Order> byStatus = SortSpec.of(Order.class, (o, by) -> by.asc(o.getStatus()));
SortSpec<Order> byPrice = SortSpec.of(Order.class, (o, by) -> by.desc(o.getPrice()));
assertEquals("select e0 from Order e0 order by e0.status asc, e0.price desc",
        qb.from(Order.class).orderBy(byStatus.then(byPrice)).toHql().hql());
// then() also takes a lambda
assertEquals("select e0 from Order e0 order by e0.status asc, e0.id desc",
        qb.from(Order.class).orderBy(byStatus.then((o, by) -> by.desc(o.getId()))).toHql().hql());
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `sort-spec-then`（第一个查询）：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
order by o1_0.status,o1_0.price desc
```

**`reversed()`：把每个排序键反过来。** `spec.reversed()` 会反转每个排序键。NULL 的位置也会一起反转：`asc`（NULL 在前）变成 `desc`（NULL 在后）。因此整个行顺序正好完全颠倒。
这与 `Direction.reversed()` 相同（见 [4.3](#43-direction)）。`spec.reversed().reversed()` 会回到最初的顺序。

**(已测试)** [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java)，region `sort-spec-reversed`：

```java
SortSpec<Order> spec = SortSpec.of(Order.class, (o, by) -> by.asc(o.getDescription()).asc(o.getId()));
// every key is reversed, the NULL position too: asc nulls first -> desc nulls last
assertEquals("select e0 from Order e0 order by e0.description desc nulls last, e0.id desc",
        qb.from(Order.class).orderBy(spec.reversed()).toHql().hql());
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `sort-spec-reversed`：

```sql
-- H2, MariaDB
select …
from orders o1_0
order by o1_0.description desc,o1_0.id desc

-- Oracle
select …
from orders o1_0
order by o1_0.description desc nulls last,o1_0.id desc
```

H2 和 MariaDB 对 `desc` 本来就把 NULL 放在最后，因此不写额外的子句。Oracle 会加上 `nulls last`。

**`SortSpec.ofPath`：根据属性名排序。** 有时排序键以文本形式来自代码外部，例如来自 Spring `Sort`（Spring 模块正是用 `ofPath` 处理它，见 [12.5](12-spring-data-jpa.md#125-spring-sort-and-pageable)）。
`SortSpec.ofPath(Order.class, "customer.name", Direction.DESC)` 根据属性路径和 `Direction` 创建一个 spec。

- 路径是用 `.` 连接的属性名。每一部分都必须是持久化属性：中间是 to-one 关联或嵌入值，最后是基本值。
- 其他任何内容都会报错（在 `orderBy` 中使用该 spec 时抛出 `IllegalArgumentException`）。因此这段文本永远不会变成其他 HQL。
- 但是**任何**持久化属性都会被接受。如果文本来自用户（例如请求参数），请先用你自己的允许名称列表检查它。
- 关联路径使用 `left join`，与排序 Lambda 中相同（规则 S8）。`then()` 可以把 `ofPath` spec 和 Lambda spec 连接起来。

**(已测试)** [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java)，region `sort-spec-path`：

```java
// for sorts chosen outside the code (Spring Sort uses this); only real properties are accepted
SortSpec<Order> byName = SortSpec.ofPath(Order.class, "customer.name", Direction.DESC)
        .then(SortSpec.ofPath(Order.class, "id", Direction.ASC));
assertEquals("select e0 from Order e0 left join e0.customer j1 order by j1.name desc nulls last, e0.id asc",
        qb.from(Order.class).orderBy(byName).toHql().hql());
assertThrows(IllegalArgumentException.class,
        () -> qb.from(Order.class).orderBy(SortSpec.ofPath(Order.class, "price; drop table x", Direction.ASC)));
```

**尚未实现：**

- `SortWhitelist<T>`，用于把外部字符串（例如 `"price,desc_nulls_last"` 这样的请求参数）转换为排序。它只接受列表中的名称。在此之前，`Direction.parse` 和 `by.sort(key, dir)` 可以提供运行时的方向（见 [4.3](#43-direction)）。排序键请在普通 Java 代码中选择，或者先用你自己的列表检查文本，再使用 `SortSpec.ofPath`。
- **计划中：** 使用 `(o, c, by) -> ...` 的 join（`SortSpec` 中也是）、键集分页（keyset paging）。

<a id="411-sorting-inside-subqueries"></a>
## 4.11 子查询内部的排序

**尚未实现**（子查询尚未实现）。

子查询内部的排序只有在与 `limit` 一起使用时（top-1 或 top-N）才会保留。
没有 `limit` 时，它不会改变结果，因此 lambdaQL 会移除它并记录一条警告日志。
见 [5. 子查询](05-subquery.md#59-sorting-inside-a-subquery)。

<a id="412-rules-summary-s1-to-s14"></a>
## 4.12 规则总结（S1 到 S14）

| # | 规则 | 状态 |
|---|---|---|
| S1 | 排序是一个 Lambda：`(o, by) -> by...`。一个排序键时：`o -> key`，可选 `Direction`。join 时：`(o, c, by) -> by...`（`by` 总是最后一个） | 已实现，join 写法除外 |
| S2 | 排序键是 `Comparable`。实体不能作为排序键（编译错误）。排序键必须使用实体 | 已实现 |
| S3 | 方向来自 `asc` / `desc` 或 `sort(key, Direction)`。`asc(key)`/`desc(key)`（`ASC`/`DESC`）使用 NULL 策略；`asc(key, nullsFirst)`、`desc(key, nullsFirst)` 和 `*_NULLS_*` 按写法使用 | 已实现 |
| S4 | 主体只能是一条直接的 `by` 调用链。不能有 `if`、三元表达式或循环 | 已实现 |
| S5 | 每个查询只调用一次 `orderBy`。`orderBy` 之后不能调用 `where`。`limit` 之后不能调用 `orderBy`（编译错误） | 已实现 |
| S6 | 用 `SortSpec`（`of`、`ofPath`、`then`、`reversed`、`orderBy(spec)`）重用。外部字符串只能通过 `SortWhitelist` | `SortSpec` 已实现并测试。`SortWhitelist` 和 join 写法尚未实现 |
| S7 | 排序键：列、对一路径、`+ - *`、拓宽转换。不能有值分支 | 已实现。列和对一路径已测试 |
| S8 | 只在 `orderBy` 中使用的关联路径使用 `left join`，因此没有关联的行会被保留。如果 `where` 使用同一条路径，排序会使用 `where` 的 join | 已实现 |
| S9 | 分页时，如果排序键不包含根实体的 `@Id`，会添加 `@Id asc`。集合 fetch join + 分页会被拒绝 | 根实体 id 部分已实现。join 相关部分尚未实现 |
| S10 | 分析结果按 Lambda 缓存。捕获的 `Direction` 每次调用都会读取 | 已实现 |
| S11 | Spring `Sort.Order` 与 `Direction` 一一对应（Spring 模块，见 [12.5](12-spring-data-jpa.md#125-spring-sort-and-pageable)）。`SortWhitelist.parse("price,desc_nulls_last")` | Spring `Sort` 已实现并测试（尚不支持 `ignoreCase`）。`SortWhitelist` 尚未实现 |
| S12 | join 查询使用相同的形式：`(o, c, by)`、`(o, c, i, by)` | 尚未实现 |
| S13 | 方法引用只能用于一个排序键的简短写法。能编译但含义错误的方法引用会被拒绝（见 [9. Java 说明](09-java-notes.md#99-method-references)） | 已实现（getter 引用） |
| S14 | 子查询的排序：有 `limit` 时保留，没有 `limit` 时移除并发出警告。数据库差异由 lambdaQL 替你处理（见第 5 章） | 尚未实现 |

---

[Java 路线](README.md) | 上一章：[3. 条件](03-where.md) | 下一章：[5. 子查询](05-subquery.md) | [本页的 Kotlin 版本](../kotlin/04-order-paging.md)
