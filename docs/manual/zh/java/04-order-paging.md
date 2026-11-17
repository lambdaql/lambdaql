<a id="4-sorting-and-paging-java"></a>
# 4. 排序与分页（Java）

> [!NOTE]
> **状态**
> - **核心部分已实现并测试**，在 H2、MariaDB 10.11 和 Oracle 23.26（Hibernate 6.6.5）上：带有 `by.asc`、`by.desc` 和 `by.sort` 的 `orderBy((o, by) -> ...)`，一个排序键的写法 `orderBy(o -> key)` 和 `orderBy(key, Direction)`（也可以使用 `Order::getPrice` 这样的 getter 方法引用），`Direction.parse` / `reversed`，`limit` / `offset`，`orderBy` 之后的 `first()`，以及可重用排序 `SortSpec`（`of`、`ofPath`、`then`、`reversed`、`orderBy(spec)`）。Spring `Sort` 和 `Pageable` 可以在 Spring Data JPA 模块中使用（[12.5](12-spring-data-jpa.md#125-spring-sort-and-pageable)）。
> - **也已实现并测试：** 在 `join`、`leftJoin` 或 `crossJoin` 之后使用的 join 写法 `(o, c, by) -> ...`（见 [6.8 显式 join](06-select-group.md#68-explicit-joins)），以及用于外部排序的排序策略（`SortRules`、`SortPolicy`、`SortMode`，见 [4.10](#sort-policy)）。
> - **尚未实现：** `SortSpec` 的 join 写法。
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

lambdaQL 在你调用 `orderBy` 时检查这一点，并抛出 `LambdaQLException`（例如条件为 `LQL-2023`，不含实体的排序键为 `LQL-3031`）。
每个排序键都必须使用实体。只有常量或捕获值的排序键会被拒绝。

**(已测试)** [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java)，region `sort-rejected`：

```java
// a condition inside the sort lambda (rule S4)
assertThrows(LambdaQLException.class, () -> qb.from(Order.class)
        .orderBy((o, by) -> o.isActive() ? by.asc(o.getPrice()) : by.desc(o.getPrice())));
// a key without the entity
assertThrows(LambdaQLException.class, () -> qb.from(Order.class).orderBy((o, by) -> by.asc(1)));
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
- 使用显式 join（`join`、`leftJoin`、`crossJoin`）时，也会按 join 的顺序添加每个被 join 实体的 id（已测试，见 [6.8](06-select-group.md#68-explicit-joins)）。
- 尚未实现：集合的 fetch join 与 `limit`/`offset` 一起使用时拒绝。

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
- 允许哪些名称由持久化单元的排序策略决定（见下面的[排序策略](#sort-policy)）。
  既没有策略也没有 `@Sortable` 字段时，**任何**持久化属性都会被接受，并且 lambdaQL 会记录警告 `LQL-5003`。
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

<a id="sort-policy"></a>
### 排序策略：允许哪些外部名称

**已实现并测试**（H2、MariaDB 10.11、Oracle 23.26，决策 D43）。

来自代码外部的排序名称（请求参数、Spring `Sort` 或 `Pageable`）可以指向任何持久化属性。
**排序策略**决定允许哪些名称。

- `SortRules<T>` 列出一个实体允许的名称。每一列都用 Lambda 给出，因此由编译器检查。
- `SortPolicy` 保存各实体的规则和一个模式（`SortMode`）。每个 `EntityManagerFactory`（即每个持久化单元）注册一个策略。使用该工厂的所有 `QueryBuilder` 和仓库都使用它。
- 在实体字段上加 `@Sortable`，无需代码即可给出规则（见下面的[来自 `@Sortable` 的规则](#rules-from-sortable)）。

一个实体的规则只来自**一个**地方，顺序如下：策略中的 `SortRules`（来自代码或 Spring `SortPolicy` bean）、Spring Boot 属性 `lambdaql.sort.*`（见 [12.5](12-spring-data-jpa.md#sort-policy-with-spring)）、实体的 `@Sortable` 字段。两个地方的规则不会合并。

**检查哪些排序。** 只检查由字符串生成的排序：`SortSpec.ofPath`，以及 Spring `Sort` 或 `Pageable`（Spring 模块使用 `ofPath`，见 [12.5](12-spring-data-jpa.md#sort-policy-with-spring)）。
排序 Lambda（`orderBy((o, by) -> ...)`、`SortSpec.of`）是你自己的代码，因此从不检查。

| 规则 | 允许的外部名称 |
|---|---|
| `allow(o -> o.getPrice())` | `price`（列的路径） |
| `allow(o -> o.getCustomer().getName())` | `customer.name`。经过关联的路径只有像这样列出时才被允许 |
| `alias("newest", o -> o.getUpdateAt())` | `newest`，按 `updateAt` 排序。此时路径 `updateAt` 本身**不是**外部名称，因此客户端看不到属性名 |

规则必须是实体的列。
其他任何键会在第一次使用规则时以 `LQL-4041` 拒绝（尚未测试）。

| `SortMode` | 对规则不允许的名称的处理 |
|---|---|
| `OFF` | 执行排序，不做检查 |
| `WARN` | 执行排序，并按实体和名称各记录一次警告 `LQL-5003`。**这是默认值：**没有注册策略时，lambdaQL 使用 `WARN`，规则只来自 `@Sortable` |
| `STRICT` | 以 `SortNotAllowedException`（`LQL-4040`）拒绝。既没有规则也没有 `@Sortable` 字段的实体，所有名称都会被拒绝 |

因此，在注册策略之前，每个 `@Sortable` 不允许的外部排序名称都会按实体记录一次 `LQL-5003`。
要拒绝未知名称，请以 `STRICT` 注册策略；要关闭检查，请以 `OFF` 注册。

| 方法 | 作用 |
|---|---|
| `SortPolicy.of(mode, rules...)` | 用模式和各实体的规则创建策略 |
| `SortPolicy.register(emf, policy)` | 注册持久化单元的策略，替换之前的策略 |
| `SortPolicy.unregister(emf)` | 移除策略，恢复默认值（`WARN`，规则只来自 `@Sortable`） |
| `SortPolicy.find(emf)` | 返回已注册的策略（`Optional`） |
| `SortPolicy.effective(emf)` | 返回生效的策略：已注册的策略，否则为默认值 |

在 Spring Boot 中，请定义一个 `SortPolicy` bean，而不是调用 `register`（见 [12.5](12-spring-data-jpa.md#sort-policy-with-spring)）。

**(已测试)** [`SortPolicyTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortPolicyTest.java)，region `rules` 和 `strict`：

```java
SortRules<Order> orderRules = SortRules.of(Order.class)
        .allow(o -> o.getPrice())                        // the name "price"
        .allow(o -> o.getCustomer().getName())           // an association path: allowed only when listed
        .alias("newest", o -> o.getUpdateAt());          // the outside name "newest" sorts by updateAt
SortPolicy.register(emf, SortPolicy.of(SortMode.STRICT, orderRules));
```

下面示例中的 `hql(name, direction)` 是测试的辅助方法。
它返回 `qb.from(Order.class).orderBy(SortSpec.ofPath(Order.class, name, direction)).toHql().hql()`。

**(已测试)** [`SortPolicyTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortPolicyTest.java)，region `allowed`：

```java
assertEquals("select e0 from Order e0 order by e0.price asc", hql("price", Direction.ASC));
assertEquals("select e0 from Order e0 left join e0.customer j1 order by j1.name asc nulls first", hql("customer.name", Direction.ASC));
// the alias: the client sends "newest", lambdaQL sorts by updateAt
assertEquals("select e0 from Order e0 order by e0.updateAt desc nulls last", hql("newest", Direction.DESC));
```

**(已测试)** [`SortPolicyTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortPolicyTest.java)，region `rejected`（`STRICT` 模式）：

```java
// not in the rules, an unlisted association path, the path behind an alias, an entity without rules
for (String name : new String[]{"description", "customer.grade", "updateAt"}) {
    assertEquals("LQL-4040", assertThrows(SortNotAllowedException.class, () -> hql(name, Direction.ASC)).code());
}
assertThrows(SortNotAllowedException.class,
        () -> qb.from(Item.class).orderBy(SortSpec.ofPath(Item.class, "qty", Direction.ASC)).toHql());
// a sort written as a lambda is code, not outside input: never checked
assertEquals("select e0 from Order e0 order by e0.description asc nulls first",
        qb.from(Order.class).orderBy((o, by) -> by.asc(o.getDescription())).toHql().hql());
```

**(已测试)** [`SortPolicyTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortPolicyTest.java)，region `warn`：

```java
// WARN (the default): the sort runs and LQL-5003 is logged once per entity and name
SortPolicy.register(emf, SortPolicy.of(SortMode.WARN));
assertEquals("select e0 from Order e0 order by e0.description asc nulls first", hql("description", Direction.ASC));
```

<a id="rules-from-sortable"></a>
**来自 `@Sortable` 的规则。** 也可以在实体的字段或 getter 上标注 `@org.lambdaql.Sortable`：

| 注解 | 允许的外部名称 |
|---|---|
| 字段 `name` 上的 `@Sortable` | `name` |
| 字段 `grade` 上的 `@Sortable("level")` | `level`，按 `grade` 排序（别名） |
| 关联字段 `customer` 上的 `@Sortable(path = "name")` | `customer.name` |

- lambdaQL 只读取 `from(...)` 中实体的注解。其他实体上的注解不会允许经过关联的路径，请在关联字段上使用 `path`。
- 只有当策略中没有该实体的规则（来自代码、Spring bean 或 Spring Boot 属性）时，才使用注解。
- 在没有注册策略的默认 `WARN` 模式下也有效。

**(已测试)** [`SortPolicyTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortPolicyTest.java)，region `annotation`（上面的 `STRICT` 策略只有 `Order` 的规则）：

```java
// Customer has no rules in the policy, so its @Sortable fields are the rules:
//   @Sortable private String name;   @Sortable("level") private Integer grade;
assertEquals("select e0 from Customer e0 order by e0.name asc nulls first",
        qb.from(Customer.class).orderBy(SortSpec.ofPath(Customer.class, "name", Direction.ASC)).toHql().hql());
assertEquals("select e0 from Customer e0 order by e0.grade desc nulls last",
        qb.from(Customer.class).orderBy(SortSpec.ofPath(Customer.class, "level", Direction.DESC)).toHql().hql());
assertThrows(SortNotAllowedException.class,
        () -> qb.from(Customer.class).orderBy(SortSpec.ofPath(Customer.class, "id", Direction.ASC)).toHql());
```

<a id="rules-as-strings"></a>
**字符串形式的规则。** `allowPath(String...)` 和 `aliasPath(name, path)` 以文本形式接收属性路径。它们用于配置：[12.5](12-spring-data-jpa.md#sort-policy-with-spring) 中的 Spring Boot 属性就使用它们。
路径会在第一次使用规则时与实体核对。在代码中请优先使用 Lambda 形式的 `allow` 和 `alias`，因为编译器会检查它们。

**(已测试)** [`SortPolicyTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortPolicyTest.java)，region `string-paths`：

```java
// the string forms used by configuration files (lambdaql.sort.allow / lambdaql.sort.alias)
SortPolicy.register(emf, SortPolicy.of(SortMode.STRICT,
        SortRules.of(Order.class).allowPath("price", "customer.name").aliasPath("newest", "updateAt")));
assertEquals("select e0 from Order e0 order by e0.updateAt desc nulls last", hql("newest", Direction.DESC));
assertThrows(SortNotAllowedException.class, () -> hql("description", Direction.ASC));
```

- 检查发生在 spec 用于 `orderBy` 时，在读取路径之前。
  因此在 `STRICT` 模式下，不在规则中的名称即使根本不是属性，也会得到 `SortNotAllowedException`。
- 规则属于一个实体类。如果为同一个类传入两个 `SortRules`，使用最后一个。
- `SortNotAllowedException` 是 `LambdaQLException`（见 [11. 错误与语言](../11-errors-and-languages.md#exceptions)）。
  lambdaQL 没有 Web 依赖。用 HTTP 400 响应的示例见 [12.5](12-spring-data-jpa.md#sort-policy-with-spring)。

**尚未实现：**

- 把名称和方向放在一起的一段文本（`"price,desc_nulls_last"`，规则 S11）。
  方向请用 `Direction.parse` 读取（见 [4.3](#43-direction)），名称请用 `SortSpec.ofPath`。
- **计划中：** `SortSpec` 的 join 写法、键集分页（keyset paging）。（join 的 Lambda 写法 `(o, c, by) -> ...` 已实现，见 [6.8](06-select-group.md#68-explicit-joins)。）

<a id="411-sorting-inside-subqueries"></a>
## 4.11 子查询内部的排序

**已实现**（里程碑 M8-1，随 [5. 子查询](05-subquery.md) 的子查询一起实现）。MariaDB 的 `lateral` 改写尚未实现。

子查询内部的排序只有在与 `limit` 一起使用时（top-1 或 top-N）才会保留。
没有 `limit` 时，它不会改变结果，因此 lambdaQL 会移除它并记录一条警告日志。
见 [5. 子查询](05-subquery.md#59-sorting-inside-a-subquery)。

<a id="412-rules-summary-s1-to-s14"></a>
## 4.12 规则总结（S1 到 S14）

| # | 规则 | 状态 |
|---|---|---|
| S1 | 排序是一个 Lambda：`(o, by) -> by...`。一个排序键时：`o -> key`，可选 `Direction`。join 时：`(o, c, by) -> by...`（`by` 总是最后一个） | 已实现，join 写法 `(o, c, by)` 也已实现（已测试） |
| S2 | 排序键是 `Comparable`。实体不能作为排序键（编译错误）。排序键必须使用实体 | 已实现 |
| S3 | 方向来自 `asc` / `desc` 或 `sort(key, Direction)`。`asc(key)`/`desc(key)`（`ASC`/`DESC`）使用 NULL 策略；`asc(key, nullsFirst)`、`desc(key, nullsFirst)` 和 `*_NULLS_*` 按写法使用 | 已实现 |
| S4 | 主体只能是一条直接的 `by` 调用链。不能有 `if`、三元表达式或循环 | 已实现 |
| S5 | 每个查询只调用一次 `orderBy`。`orderBy` 之后不能调用 `where`。`limit` 之后不能调用 `orderBy`（编译错误） | 已实现 |
| S6 | 用 `SortSpec`（`of`、`ofPath`、`then`、`reversed`、`orderBy(spec)`）重用。外部字符串由持久化单元的排序策略（`SortRules`、`SortPolicy`、`SortMode`，见 [4.10](#sort-policy)）检查 | `SortSpec` 和排序策略已实现并测试。`SortSpec` 的 join 写法尚未实现 |
| S7 | 排序键：列、对一路径、`+ - *`、拓宽转换。不能有值分支 | 已实现。列和对一路径已测试 |
| S8 | 只在 `orderBy` 中使用的关联路径使用 `left join`，因此没有关联的行会被保留。如果 `where` 使用同一条路径，排序会使用 `where` 的 join | 已实现 |
| S9 | 分页时，如果排序键不包含根实体的 `@Id`，会添加 `@Id asc`。集合 fetch join + 分页会被拒绝 | 根实体 id 和显式 join 实体的 id 部分已实现（已测试）。fetch join 检查尚未实现 |
| S10 | 分析结果按 Lambda 缓存。捕获的 `Direction` 每次调用都会读取 | 已实现 |
| S11 | Spring `Sort.Order` 与 `Direction` 一一对应（Spring 模块，见 [12.5](12-spring-data-jpa.md#125-spring-sort-and-pageable)）。属性名经过排序策略检查，别名按原样传递 | Spring `Sort` 已实现并测试（尚不支持 `ignoreCase`），与排序策略一起使用的情况也已测试。读取 `"price,desc_nulls_last"` 这样的一段文本尚未实现 |
| S12 | join 查询使用相同的形式：`(o, c, by)`、`(o, c, i, by)` | 已实现（已测试，见 [6.8](06-select-group.md#68-explicit-joins)） |
| S13 | 方法引用只能用于一个排序键的简短写法。能编译但含义错误的方法引用会被拒绝（见 [9. Java 说明](09-java-notes.md#99-method-references)） | 已实现（getter 引用） |
| S14 | 子查询的排序：有 `limit` 时保留，没有 `limit` 时移除并发出警告。数据库差异由 lambdaQL 替你处理（见第 5 章） | 已实现（已测试）。MariaDB 的 `lateral` 改写尚未实现 |

---

[Java 路线](README.md) | 上一章：[3. 条件](03-where.md) | 下一章：[5. 子查询](05-subquery.md) | [本页的 Kotlin 版本](../kotlin/04-order-paging.md)
