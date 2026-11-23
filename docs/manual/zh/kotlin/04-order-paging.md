<a id="4-sorting-and-paging-kotlin"></a>
# 4. 排序与分页（Kotlin）

[Kotlin 路线](README.md) | [手册首页](../README.md) | 上一章：[3. 条件（`where`）](03-where.md) | 下一章：[5. 子查询](05-subquery.md) | [本页的 Java 版本](../java/04-order-paging.md)

公共页面：[1. 概述](../01-overview.md) · [11. 错误与语言](../11-errors-and-languages.md)

---

<a id="41-one-lambda-for-the-whole-sort"></a>
## 4.1 用一个 Lambda 表达整个排序

你用**一个 Lambda** 编写排序。Lambda 接收实体，并以排序构建器 `by` 作为最后一个参数。
你用 `by.asc(...)` 和 `by.desc(...)` 添加排序键和方向。

```kotlin
qb.from(Order::class.java)
    .where { it.active }
    .orderBy { o, by -> by.desc(o.price).asc(o.customer?.name).asc(o.id) }
    .limit(20).offset(40)
    .toList()
// HQL: select e0 from Order e0 left join e0.customer j1
//      where e0.active = true
//      order by e0.price desc nulls last, j1.name asc nulls first, e0.id asc
```

**关于 SQL 块。** `…` 是缩短的 select 列表（实体的所有列），`?` 是绑定参数。这些行来自 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)。该测试使用 Hibernate 7.4 在 H2 2.3、MariaDB 10.11 和 Oracle 23.26 上运行每个查询，如果 Hibernate 的 SQL 发生变化，测试就会失败。 该测试使用 Java Lambda。含义相同的 Kotlin Lambda 生成相同的 HQL，因此 SQL 也相同。

**各数据库的 SQL（Hibernate 为此 HQL 生成的 SQL；Kotlin 生成相同的 HQL）** — 手册中的 `Order` 有可为 NULL 的 `price`，而测试中的 `Order` 没有，所以 `SqlByDatabaseTest` 把这个 HQL 直接交给 Hibernate。来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `sort-overview`：

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
where o1_0.active=true
order by o1_0.price desc,c1_0.name,o1_0.id
limit ?,?

-- Oracle
-- (this HQL is given to Hibernate directly)
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where o1_0.active=true
order by o1_0.price desc nulls last,c1_0.name asc nulls first,o1_0.id
offset ? rows
fetch first ? rows only
```

- `by` 这个名字只是本手册的习惯。你可以使用任何名字。
- `by` 不需要导入。它是一个 Lambda 参数。
- 排序键和它的方向写在一起。它们的顺序永远不会错乱。
- lambdaQL 读取 Lambda，但从不执行它。`by` 的方法只是描述顺序。

**(已测试)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)，region `sort`：

```kotlin
val q = qb.from(Order::class.java)
    .where { it.isActive }
    .orderBy { o, by -> by.desc(o.price).asc(o.customer?.name) }
    .limit(3)
assertEquals("select e0 from Order e0 left join e0.customer j1 where e0.active = true"
    + " order by e0.price desc, j1.name asc nulls first, e0.id asc", q.toHql().hql())
```

测试使用 Java 测试实体：`price` 是基本类型 `double`（所以没有 `nulls` 部分），`active` 的 getter 是 `isActive()`（Kotlin 中为 `it.isActive`）。
测试在 H2、MariaDB 和 Oracle 上执行查询，并把结果行与用 Kotlin 进行的同样排序作比较。
更多用 Java 写的排序在 [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java) 中测试过。这些示例请看[本页的 Java 版本](../java/04-order-paging.md)。

<a id="42-the-short-forms-one-key"></a>
## 4.2 简短写法（一个排序键）

如果只有一个排序键，可以省略 `by`：

| Kotlin | HQL |
|---|---|
| `orderBy { it.price }` | `order by e0.price asc nulls first` |
| `orderBy({ it.updateAt }, Direction.DESC)` | `order by e0.updateAt desc nulls last` |
| `orderBy(Order::price)` | `order by e0.price asc nulls first`（尚未实现） |

不指定 `Direction` 时，简短写法按升序（`ASC`）排序。
lambdaQL 根据 Lambda 参数的个数（一个或两个）区分这些写法。Kotlin 会毫无歧义地选择正确的重载。

指定 `Direction` 时，Lambda 是第一个参数，因此要写在括号里：`orderBy({ it.updateAt }, Direction.DESC)`。

Kotlin 属性引用（`Order::price`）尚未实现。目前请使用 Lambda（`{ it.price }`）。

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
- `reversed()` 返回相反的方向。NULL 的位置也会翻转，因此整个顺序都会反过来：`Direction.ASC.reversed()` 是 `DESC`，`Direction.ASC_NULLS_LAST.reversed()` 是 `DESC_NULLS_FIRST`。

**选择 NULL 的位置**：使用第二个 `Boolean` 参数：`by.asc(key, nullsFirst)` 和 `by.desc(key, nullsFirst)`（决策 D35）。
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

**当方向来自一个变量时**，请使用 `by.sort(key, direction)` 并传入捕获的 `Direction`：

```kotlin
.orderBy { o, by -> by.desc(o.description, false).asc(o.id) }   // nulls last
// order by e0.description desc nulls last, e0.id asc

val dir = if (request.newestFirst) Direction.DESC else Direction.ASC
.orderBy { o, by -> by.sort(o.updateAt, dir).asc(o.id) }     // 每次调用都会读取 dir

.orderBy({ it.customer?.name }, Direction.DESC_NULLS_LAST)  // 带 NULL 位置的简短写法
```

Lambda 只分析一次。每次执行查询时都会读取 `dir`（规则 S10）。方向为 `null` 时，查询执行时会抛出异常。

> [!TIP]
> 单独一个 `false` 表达的信息不多。Kotlin 不能对 Java 方法使用具名参数（`nullsFirst = false`），而 `by` 构建器是一个 Java 类。
> 因此 Kotlin 也写作 `by.desc(x, false)`，与 Java 相同。加上 `// nulls last` 这样的简短注释是个好习惯。

请在 Lambda **外部**选择 `dir`。排序 Lambda 内部的 `if` 会被拒绝（见 [4.6](#46-rules-for-the-lambda-body)）。

<a id="44-null-ordering-is-the-same-on-every-database"></a>
## 4.4 NULL 的排序在每个数据库上都相同

各数据库对 NULL 的位置并不一致：

| | H2 | MariaDB | Oracle |
|---|---|---|---|
| `ASC` 默认 | NULL 在前 | NULL 在前 | NULL 在**后** |
| `DESC` 默认 | NULL 在后 | NULL 在后 | NULL 在**前** |
| SQL 中的 `NULLS FIRST/LAST` | 支持 | **不支持（语法错误）** | 支持 |

lambdaQL 用一条策略解决这个问题：**NULL 是最小的值**（决策 D16）。

- `ASC` = `ASC_NULLS_FIRST`
- `DESC` = `DESC_NULLS_LAST`

这与 Kotlin 的比较器 `nullsFirst(naturalOrder())` 相同。
使用这条策略，三种数据库都会返回相同的顺序（对于数字和日期）。

**lambdaQL 如何写出它。** 对于每个可能为 NULL 的排序键，lambdaQL 都会把 NULL 的位置写入 HQL，例如 `e0.description asc nulls first`。
永远不会为 NULL 的排序键（像 `active: Boolean` 或 `qty: Int` 这样的 JVM 基本类型，或者 `@Id`）没有 `nulls` 部分。
lambdaQL 看的是 JPA 元数据，而不是 Kotlin 类型。因此带 `@Id` 的 `id: Long?` 没有 `nulls` 部分，而 `price: Double?` 有。
关联后面的列在没有关联时为 NULL（订单可能没有客户），因此会有 `nulls` 部分。如果路径上的每一步关联都是必需的（`optional = false`），并且值不会为 NULL，就不会有这一部分。

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
| 列：`o.price` | 是 | 可空排序键（`Double?`、`String?`）没有问题 |
| 对一路径：`o.customer?.name`、`o.customer!!.name` | 是 | `left join`（必需关联使用 inner join），见下文 |
| `@Lob` 列 | 是 | Oracle 会拒绝。`DEBUG` 级别的 `LQL-5010`，见下文 |
| `@Enumerated(EnumType.STRING)` 枚举列 | 是 | 按名称排序（不是声明顺序）。`DEBUG` 级别的 `LQL-5011`，见下文 |
| 算术运算：`o.price!! * 2`、`+`、`-` | 是 | |
| 拓宽转换：`o.status!!.toLong()` | 是 | |
| 非布尔值上的 Elvis：`o.description ?: ""` | 是 | `coalesce(e0.description, '')`。NULL 策略不适用于它 |
| 实体：`by.desc(o.customer)` | **编译错误** | 实体不是 `Comparable` |
| 不使用实体的值：`by.asc(1)`、捕获的值 | **会被拒绝** | 见 [4.6](#46-rules-for-the-lambda-body) |
| 作用于列的函数：`lower(name)` | 计划中 | |
| 布尔 Elvis：`{ it.flag ?: false }` | **会被拒绝**（KC21） | 请使用 `{ it.flag }` 加上 NULL 位置 |
| 值 `if` / `when` | **会被拒绝**（KC6） | |

排序键必须是 `Comparable`。Kotlin 的可空排序键没有问题。Kotlin 会在编译时拒绝实体排序键。

**关联路径使用 `left join`。** 在 HQL 中，普通路径 `order by o.customer.name` 在三种数据库上都会变成 inner join。没有客户的订单会消失。
lambdaQL 改为添加 `left join`。没有客户的订单会保留在结果中。它们的 `j1.name` 为 NULL，因此在 `asc` 时排在最前面。

```kotlin
.orderBy { o, by -> by.asc(o.customer?.name).asc(o.id) }
// HQL: select e0 from Order e0 left join e0.customer j1 order by j1.name asc nulls first, e0.id asc
```

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `sort-path`：

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

在 SQL 中可以看到 NULL 策略。H2 和 MariaDB 在与自身默认值相同时省略该子句。Oracle 的默认值正好相反，所以写出 `asc nulls first` / `desc nulls last`。MariaDB 没有 `NULLS` 语法，所以对于不是默认值的位置，它先按 `case when … is null then … end` 排序。

**必需关联使用 inner join。** 用 `optional = false` 映射的关联（例如 `@ManyToOne(optional = false)`）总有目标，因此 inner join 不会丢失行（A4）。
所以即使路径只用于排序或 select，这类关联也使用 `join`。其他关联像上面一样使用 `left join`（规则 S8、J1）。
如果路径上的每一步关联都是必需的，并且值不会为 NULL，排序也不会带 `nulls first/last`。

目前还没有针对这一点的 Kotlin 测试。
同样的规则已用 Java 测试（[`MappingRulesTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/MappingRulesTest.java)，region `required-association`，见[本页的 Java 版本](../java/04-order-paging.md#45-what-a-sort-key-can-be)）。
下面是设计示例：

```kotlin
// OrderLine.order is optional = false: a sort or select path through it is an inner join
qb.from(OrderLine::class.java).orderBy { l, by -> by.asc(l.order.price) }
// HQL: select e0 from OrderLine e0 join e0.order j1 order by j1.price asc
qb.from(OrderLine::class.java).select { it.order.price }
// HQL: select j1.price from OrderLine e0 join e0.order j1
```

**`@Lob` 列的排序。** 在 `orderBy`、`distinct`、`groupBy` 中使用 `@Lob` 列时，H2 和 MariaDB 会执行，Oracle 会拒绝（ORA-22848、ORA-22849，实测）。`=` 比较在三种数据库上都可以执行。
lambdaQL 不修改查询，而是对每个列和每种用法记录一次 `DEBUG` 级别的 `LQL-5010`（B3）。

Java 测试（[`MappingRulesTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/MappingRulesTest.java)，region `lob`）检查这三种用法的日志。下面是设计示例：

```kotlin
// memo is @Lob: Oracle rejects it in order by, group by and distinct; lambdaQL logs LQL-5010 at DEBUG
qb.from(OrderLine::class.java).orderBy { l, by -> by.asc(l.memo) }
qb.from(OrderLine::class.java).select { it.memo }.distinct()
```

**`EnumType.STRING` 枚举的排序。** 对于用 `@Enumerated(EnumType.STRING)` 映射的枚举列，数据库按常量**名称**的字符串顺序排序。Java 的枚举比较遵循声明顺序，因此结果的顺序可能不同（B2）。
lambdaQL 不修改查询，而是对每个列记录一次 `DEBUG` 级别的 `LQL-5011`。`ORDINAL`（或没有 `@Enumerated`）按声明顺序排序，因此不记录。

Java 测试（[`MappingRulesTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/MappingRulesTest.java)，region `enum-string-sort`）检查这一行为。下面是设计示例：

```kotlin
// kind is @Enumerated(EnumType.STRING): the database sorts by name, Kotlin by declaration order (LQL-5011, DEBUG)
qb.from(Order::class.java).orderBy { o, by -> by.asc(o.kind) }
// HQL: select e0 from Order e0 order by e0.kind asc nulls first
```

别名与 `where` 共享。如果 `where` 已经 join 了同一条路径，排序就会使用那个 join。
如果 `where` 以 inner join 使用这条路径，没有客户的订单已经被排除（决策 D36、D48）。如果路径用 `?.` 经过并成为 `left join`（决策 D54），排序也会使用那个 `left join`（见 [3.10](03-where.md#310-association-paths)）。

<a id="46-rules-for-the-lambda-body"></a>
## 4.6 Lambda 主体的规则

主体必须是**一条直接的 `by` 调用链**：

```kotlin
// 允许
.orderBy { o, by -> by.desc(o.price).asc(o.id) }

// 会被拒绝：条件化的排序键
.orderBy { o, by -> if (flag) by.desc(o.price) else by.asc(o.id) }

// 会被拒绝：不使用实体的排序键
.orderBy { o, by -> by.asc(1) }

// 会被拒绝：循环、局部 val/var、多于一条语句
```

lambdaQL 在你调用 `orderBy` 时检查这一点，并抛出 `LambdaQLException`（例如条件为 `LQL-2023`，不含实体的排序键为 `LQL-3031`）。
每个排序键都必须使用实体。只有常量或捕获值的排序键会被拒绝。

如果方向必须在运行时改变，请把它作为变量传入：`by.sort(key, dir)`。如果排序键本身必须改变，请在普通 Kotlin 代码中用两个分支分别构建查询。

<a id="47-order-of-calls"></a>
## 4.7 调用顺序

构建器类型控制调用顺序。顺序错误时无法编译：

| 调用 | 是否允许 |
|---|---|
| `from(...).where { }.orderBy { }.limit(...).offset(...)` | 是 |
| `from(...).where { }.limit(...).offset(...)`（没有 `orderBy`） | 是。行按 id 排序（见 [4.8](#48-paging)） |
| 调用两次 `orderBy { }` | 否（编译错误） |
| 在 `orderBy { }` 之后调用 `where { }` | 否（编译错误） |
| 在 `limit(...)` 之后调用 `orderBy { }` | 否（编译错误） |

在 `orderBy`、`limit` 或 `offset` 之后，查询的类型是 `OrderedSelectQuery`。它只有 `limit`、`offset`、`toHql()`、`toList()`、`toSet()` 和 `first()`。`one()`、`count()` 和 `exists()` 只能在 `orderBy`、`limit` 和 `offset` 之前使用（见[获取结果](02-getting-started.md#getting-the-result)）。

<a id="48-paging"></a>
## 4.8 分页

```kotlin
qb.from(Order::class.java)
    .where { it.status == 1 }
    .orderBy { o, by -> by.desc(o.price) }
    .limit(20)      // 每页大小
    .offset(40)     // 跳过 40 行（第 3 页）
    .toList()
// HQL: select e0 from Order e0 where e0.status = 1 order by e0.price desc nulls last, e0.id asc
// limit 和 offset 会传给 Hibernate（setMaxResults / setFirstResult）
```

**各数据库的 SQL（Hibernate 为此 HQL 生成的 SQL；Kotlin 生成相同的 HQL）** — 手册中的 `Order` 有可为 NULL 的 `price`，而测试中的 `Order` 没有，所以 `SqlByDatabaseTest` 把这个 HQL 直接交给 Hibernate。来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `page-design`：

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

`limit` 和 `offset` 接受 0 或更大的值。负值会抛出 `IllegalArgumentException`。

lambdaQL 不会把 `LIMIT` 写入 HQL。Hibernate 会为每种数据库生成正确的 SQL：

| 数据库 | SQL |
|---|---|
| H2 | `offset ? rows fetch first ? rows only` |
| MariaDB | `limit ?, ?` |
| Oracle | `offset ? rows fetch first ? rows only` |

**不带 `orderBy` 的分页。** 你可以不调用 `orderBy`，直接调用 `limit` 和 `offset`。这时 lambdaQL 按 id 对行排序，因此分页是稳定的。

```kotlin
qb.from(Order::class.java)
    .where { it.active }
    .limit(2)
    .toList()
// HQL: select e0 from Order e0 where e0.active = true order by e0.id asc
```

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `page-only`：

```sql
-- H2, Oracle
select …
from orders o1_0
where o1_0.active=true
order by o1_0.id
fetch first ? rows only

-- MariaDB
select …
from orders o1_0
where o1_0.active=true
order by o1_0.id
limit ?
```

分页 SQL 在各数据库上不同：H2 和 Oracle 得到 `fetch first ? rows only`，MariaDB 得到 `limit ?`。有 offset 时，H2 和 Oracle 会加上 `offset ? rows`，MariaDB 写成 `limit ?,?`。

**第一行与总行数。**

- `orderBy { ... }.first()` 以 `Optional` 的形式返回按该顺序排列的第一行。它只读取一行。`offset` 会被保留：`orderBy { ... }.offset(1).first()` 返回第二行。
- `count()` 只存在于 where 阶段（`orderBy`、`limit` 和 `offset` 之前），因此它总是统计所有匹配的行。对于分页画面，请把 where 阶段的查询保存在一个值中。在它上面调用 `count()` 得到总数，然后在它上面调用 `orderBy { ... }.limit(...).toList()` 得到这一页。

见[获取结果](02-getting-started.md#getting-the-result)。

<a id="tie-breaker-automatic-id"></a>
### 决胜键（自动添加 `id`）

看上面的 HQL：lambdaQL 在末尾添加了 `e0.id asc`。为什么？

- 很多行可能有相同的 `price`。数据库可能以**任意顺序**返回这些行。Oracle 确实会这样（已实测）。
- 这样第 2 页和第 3 页可能会把同一行显示两次，或者漏掉一行。

规则：**当你使用分页，并且排序键中不包含 id 时，lambdaQL 会把根实体的 `@Id asc` 添加为最后一个排序键。**

- 它只会添加一次。如果你的排序键已经包含 id（例如 `by.desc(o.id)`），就不会添加。
- 如果排序键中有主实体上唯一且不为 NULL 的列，也不会添加（A5）。`nullable = false` 的 `@Column(unique = true)`、Hibernate `@NaturalId`、`@Id` 都属于这种列，因为仅凭这个键就能确定所有行的顺序。
  可以为 NULL 的唯一列不属于这种列，因为多行可能都是 NULL。
- 没有 `limit` 和 `offset` 时，不会添加任何东西。
- 使用显式 join（`join`、`leftJoin`、`crossJoin`）时，也会按 join 的顺序添加每个被 join 实体的 id（已用 Java Lambda 测试，见 [6.8](06-select-group.md#68-explicit-joins)）。
- 尚未实现：集合的 fetch join 与 `limit`/`offset` 一起使用时拒绝。

目前还没有针对这一点的 Kotlin 测试。同样的规则已用 Java 测试（[`MappingRulesTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/MappingRulesTest.java)，region `unique-key`）。
下面是设计示例：

```kotlin
// OrderLine.code is @Column(unique = true, nullable = false): no id tie-breaker
qb.from(OrderLine::class.java).orderBy { l, by -> by.asc(l.code) }.limit(2).toList()
// HQL: select e0 from OrderLine e0 order by e0.code asc
```

<a id="49-strings-and-special-values"></a>
## 4.9 字符串与特殊值

- 字符串的顺序遵循数据库的排序规则（collation）。MariaDB 的 `_ci` 忽略大小写。Oracle 使用 `NLS_SORT`，它跟随 JVM 的 locale（例如 `de_DE` 会得到德语顺序）。对于 Oracle，我们推荐 `NLS_SORT=BINARY`。
- Oracle 把 `''` 存储为 NULL，因此空字符串会像 NULL 一样排序。
- 各数据库对 `NaN` 的排序不同。

<a id="410-reusable-and-external-sorts"></a>
## 4.10 可重用的排序与外部排序

**`SortSpec`：可以保存起来的排序。** `SortSpec` 是一个可以保存在值中、并在许多查询中使用的排序。
用 `SortSpec.of(Order::class.java) { o, by -> ... }` 创建它。这个 Lambda 与 `orderBy { o, by -> ... }` 中的相同，因此适用同样的规则（见 [4.6](#46-rules-for-the-lambda-body)）。
然后把它传给 `orderBy(spec)`。

```kotlin
// for example at the top level or in a companion object
val NEWEST: SortSpec<Order> = SortSpec.of(Order::class.java) { o, by -> by.desc(o.updateAt).asc(o.id) }

qb.from(Order::class.java).where { it.active }.orderBy(NEWEST).limit(20).toList()
// HQL: select e0 from Order e0 where e0.active = true order by e0.updateAt desc nulls last, e0.id asc
```

- `SortSpec` 不能被修改。`then()` 和 `reversed()` 会返回一个新的 spec。因此一个 spec 可以安全地在许多查询和线程之间共享，例如作为顶层 `val`。
- lambdaQL 在 spec 被用于 `orderBy` 时检查 Lambda。每个 Lambda 只分析一次。
- 捕获的值是创建 Lambda 那一刻的值。放在顶层 `val` 中的 spec 会一直保留这些值。如果方向必须在运行时改变，请使用 `orderBy { o, by -> by.sort(key, dir) }`（见 [4.3](#43-direction)）。
- 在 `orderBy(spec)` 之后，可以像平常一样分页：`limit`、`offset`、`toList()`、`first()`。

目前还没有针对 `SortSpec` 的 Kotlin 测试。用 Java 写的同样的 spec 在 [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java) 中测试过（H2、MariaDB 和 Oracle）。已测试的示例请看[本页的 Java 版本](../java/04-order-paging.md#410-reusable-and-external-sorts)。

```kotlin
val byDescription = SortSpec.of(Order::class.java) { o, by -> by.desc(o.description).asc(o.id) }
qb.from(Order::class.java).where { it.active }.orderBy(byDescription).toList()
// HQL: select e0 from Order e0 where e0.active = true order by e0.description desc nulls last, e0.id asc
```

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `sort-spec`：

```sql
-- H2, MariaDB
select …
from orders o1_0
where o1_0.active=true
order by o1_0.description desc,o1_0.id

-- Oracle
select …
from orders o1_0
where o1_0.active=true
order by o1_0.description desc nulls last,o1_0.id
```

SQL 与用 `orderBy { o, by -> ... }` 写出的同样排序相同。Oracle 会加上 `nulls last`，因为它自己对 `desc` 的默认规则正好相反（见 [4.4](#44-null-ordering-is-the-same-on-every-database)）。

**`then()`：在已有排序键之后追加排序键。** `a.then(b)` 先按 `a` 的排序键排序。对于相等的行，再按 `b` 的排序键排序。
`then()` 接受另一个 `SortSpec` 或一个排序 Lambda。

```kotlin
val byStatus = SortSpec.of(Order::class.java) { o, by -> by.asc(o.status) }
val byPrice = SortSpec.of(Order::class.java) { o, by -> by.desc(o.price) }
qb.from(Order::class.java).orderBy(byStatus.then(byPrice)).toList()
// HQL: select e0 from Order e0 order by e0.status asc nulls first, e0.price desc nulls last
qb.from(Order::class.java).orderBy(byStatus.then { o, by -> by.desc(o.id) }).toList()
// HQL: select e0 from Order e0 order by e0.status asc nulls first, e0.id desc
```

这里的 `status` 和 `price` 可以为 NULL（`Int?`、`Double?`），因此 HQL 中有 `nulls` 部分。在 Java 测试实体中它们永远不会为 NULL，因此已测试的 HQL 和 SQL（示例 `sort-spec-then`）没有 `nulls` 部分。请看[本页的 Java 版本](../java/04-order-paging.md#410-reusable-and-external-sorts)。

**`reversed()`：把每个排序键反过来。** `spec.reversed()` 会反转每个排序键。NULL 的位置也会一起反转：`asc`（NULL 在前）变成 `desc`（NULL 在后）。因此整个行顺序正好完全颠倒。
这与 `Direction.reversed()` 相同（见 [4.3](#43-direction)）。`spec.reversed().reversed()` 会回到最初的顺序。

```kotlin
val spec = SortSpec.of(Order::class.java) { o, by -> by.asc(o.description).asc(o.id) }
qb.from(Order::class.java).orderBy(spec.reversed()).toList()
// HQL: select e0 from Order e0 order by e0.description desc nulls last, e0.id desc
```

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `sort-spec-reversed`：

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

**`SortSpec.ofPath`：根据属性名排序。** `SortSpec.ofPath(Order::class.java, "customer.name", Direction.DESC)` 根据属性路径创建一个 spec，用于以文本形式来自代码外部的排序键（Spring 模块用它处理 Spring `Sort`，见 [12.5](12-spring-data-jpa.md#125-spring-sort-and-pageable)）。
路径的每一部分都必须是持久化属性，最后一部分必须是基本值。其他任何内容都会报错。
允许哪些名称由排序策略决定（见下面的[排序策略](#sort-policy)）。既没有策略也没有 `@Sortable` 字段时，任何持久化属性都会被接受，并且 lambdaQL 会记录 `LQL-5003`（默认为 `INFO` 级别）。
这里没有 Lambda，所以 Kotlin 与 Java 的行为相同。Java 测试见[本页的 Java 版本](../java/04-order-paging.md#410-reusable-and-external-sorts)（region `sort-spec-path`）。

```kotlin
val byName = SortSpec.ofPath(Order::class.java, "customer.name", Direction.DESC)
    .then(SortSpec.ofPath(Order::class.java, "id", Direction.ASC))
qb.from(Order::class.java).orderBy(byName).toList()
// HQL: select e0 from Order e0 left join e0.customer j1 order by j1.name desc nulls last, e0.id asc
```

<a id="sort-policy"></a>
### 排序策略：允许哪些外部名称

**已实现**（决策 D43），已在 H2、MariaDB 10.11 和 Oracle 23.26 上用 Java Lambda 测试。

来自代码外部的排序名称（请求参数、Spring `Sort` 或 `Pageable`）可以指向任何持久化属性。
**排序策略**决定允许哪些名称。

- `SortRules<T>` 列出一个实体允许的名称。每一列都用 Lambda 给出，因此由编译器检查。
- `SortPolicy` 保存各实体的规则和一个模式（`SortMode`）。每个 `EntityManagerFactory`（即每个持久化单元）注册一个策略。使用该工厂的所有 `QueryBuilder` 和仓库都使用它。
- 在实体字段上加 `@Sortable`，无需代码即可给出规则（见下面的[来自 `@Sortable` 的规则](#rules-from-sortable)）。

一个实体的规则只来自**一个**地方，顺序如下：策略中的 `SortRules`（来自代码或 Spring `SortPolicy` bean）、Spring Boot 属性 `lambdaql.sort.*`（见 [12.5](12-spring-data-jpa.md#sort-policy-with-spring)）、实体的 `@Sortable` 字段。
两个地方的规则不会合并。

**检查哪些排序。** 只检查由字符串生成的排序：`SortSpec.ofPath`，以及 Spring `Sort` 或 `Pageable`（Spring 模块使用 `ofPath`，见 [12.5](12-spring-data-jpa.md#sort-policy-with-spring)）。
排序 Lambda（`orderBy { o, by -> ... }`、`SortSpec.of`）是开发者的代码，因此不检查。

| 规则 | 允许的外部名称 |
|---|---|
| `allow { it.price }` | `price`（列的路径） |
| `allow { it.customer?.name }` | `customer.name`。经过关联的路径只有像这样列出时才被允许 |
| `alias("newest") { it.updateAt }` | `newest`，按 `updateAt` 排序。此时路径 `updateAt` 本身**不是**外部名称，因此客户端看不到属性名 |

规则必须是实体的列。
其他任何键会在第一次使用规则时以 `LQL-4041` 拒绝（尚未测试）。

| `SortMode` | 对规则不允许的名称的处理 |
|---|---|
| `OFF` | 执行排序，不做检查 |
| `INFO` | 执行排序，并按实体和名称各以 `INFO` 级别记录一次 `LQL-5003`。**这是默认值：**没有注册策略时，lambdaQL 使用 `INFO`，规则只来自 `@Sortable` |
| `WARN` | 与 `INFO` 相同，但日志级别为 `WARN` |
| `STRICT` | 以 `SortNotAllowedException`（`LQL-4040`）拒绝。既没有规则也没有 `@Sortable` 字段的实体，所有名称都会被拒绝 |

因此，在注册策略之前，每个 `@Sortable` 不允许的外部排序名称都会按实体记录一次 `LQL-5003`。
要拒绝未知名称，请以 `STRICT` 注册策略；要关闭检查，请以 `OFF` 注册。

| 方法 | 作用 |
|---|---|
| `SortPolicy.of(mode, rules...)` | 用模式和各实体的规则创建策略 |
| `SortPolicy.register(emf, policy)` | 注册持久化单元的策略，替换之前的策略 |
| `SortPolicy.unregister(emf)` | 移除策略，恢复默认值（`INFO`，规则只来自 `@Sortable`） |
| `SortPolicy.find(emf)` | 返回已注册的策略（`Optional`） |
| `SortPolicy.effective(emf)` | 返回生效的策略：已注册的策略，否则为默认值 |

在 Spring Boot 中，请定义一个 `SortPolicy` bean，而不是调用 `register`（见 [12.5](12-spring-data-jpa.md#sort-policy-with-spring)）。

目前还没有针对排序策略的 Kotlin 测试。
用 Java 写的同样规则在 [`SortPolicyTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortPolicyTest.java) 中测试过（H2、MariaDB 和 Oracle）。已测试的示例请看[本页的 Java 版本](../java/04-order-paging.md#sort-policy)。
下面是设计示例：

```kotlin
val orderRules = SortRules.of(Order::class.java)
    .allow { it.price }                      // the name "price"
    .allow { it.customer?.name }             // an association path: allowed only when listed
    .alias("newest") { it.updateAt }         // the outside name "newest" sorts by updateAt
SortPolicy.register(emf, SortPolicy.of(SortMode.STRICT, orderRules))

qb.from(Order::class.java).orderBy(SortSpec.ofPath(Order::class.java, "newest", Direction.DESC)).toList()
// HQL: select e0 from Order e0 order by e0.updateAt desc nulls last
qb.from(Order::class.java).orderBy(SortSpec.ofPath(Order::class.java, "updateAt", Direction.ASC)).toList()
// SortNotAllowedException (LQL-4040): the path behind an alias is not an outside name
qb.from(Order::class.java).orderBy { o, by -> by.asc(o.description) }.toList()
// a sort lambda is your code: never checked
```

<a id="rules-from-sortable"></a>
**来自 `@Sortable` 的规则。** 也可以在实体的字段或 getter 上标注 `@org.lambdaql.Sortable`：

| 注解 | 允许的外部名称 |
|---|---|
| 属性 `name` 上的 `@field:Sortable` | `name` |
| 属性 `grade` 上的 `@field:Sortable("level")` | `level`，按 `grade` 排序（别名） |
| 关联属性 `customer` 上的 `@field:Sortable(path = "name")` | `customer.name` |

- lambdaQL 只读取 `from(...)` 中实体的注解。其他实体上的注解不会允许经过关联的路径，请在关联字段上使用 `path`。
- 只有当策略中没有该实体的规则（来自代码、Spring bean 或 Spring Boot 属性）时，才使用注解。
- 在没有注册策略的默认 `INFO` 模式下也有效。

在 Kotlin 中请写 `@field:Sortable`，让注解落在 JVM 字段上。
同样的规则已用 Java 测试（[`SortPolicyTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortPolicyTest.java)，region `annotation`，见[本页的 Java 版本](../java/04-order-paging.md#rules-from-sortable)）。下面是设计示例：

```kotlin
@Entity
class Customer(
    @Id @GeneratedValue
    var id: Long? = null,
    @field:Sortable                     // the outside name "name"
    var name: String? = null,
    @field:Sortable("level")            // the outside name "level" sorts by grade
    var grade: Int? = null,
)

// Customer has no SortRules in the policy, so its @Sortable fields are the rules
qb.from(Customer::class.java).orderBy(SortSpec.ofPath(Customer::class.java, "level", Direction.DESC)).toList()
// HQL: select e0 from Customer e0 order by e0.grade desc nulls last
```

<a id="rules-as-strings"></a>
**字符串形式的规则。** `allowPath(String...)` 和 `aliasPath(name, path)` 以文本形式接收属性路径。它们用于配置：[12.5](12-spring-data-jpa.md#sort-policy-with-spring) 中的 Spring Boot 属性就使用它们。
路径会在第一次使用规则时与实体核对。在代码中请优先使用 Lambda 形式的 `allow` 和 `alias`，因为编译器会检查它们。

已用 Java 测试（[`SortPolicyTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortPolicyTest.java)，region `string-paths`）。这里没有 Lambda，因此 Kotlin 中的行为相同：

```kotlin
// the string forms used by configuration files (lambdaql.sort.allow / lambdaql.sort.alias)
SortPolicy.register(emf, SortPolicy.of(SortMode.STRICT,
    SortRules.of(Order::class.java).allowPath("price", "customer.name").aliasPath("newest", "updateAt")))
```

- 检查发生在 spec 用于 `orderBy` 时，在读取路径之前。
  因此在 `STRICT` 模式下，不在规则中的名称即使根本不是属性，也会得到 `SortNotAllowedException`。
- 规则属于一个实体类。如果为同一个类传入两个 `SortRules`，使用最后一个。
- `SortNotAllowedException` 是 `LambdaQLException`（见 [11. 错误与语言](../11-errors-and-languages.md#exceptions)）。
  lambdaQL 没有 Web 依赖。用 HTTP 400 响应的示例见 [12.5](12-spring-data-jpa.md#sort-policy-with-spring)。

**尚未实现：**

- 把名称和方向放在一起的一段文本（`"price,desc_nulls_last"`，规则 S11）。
  方向请用 `Direction.parse` 读取（见 [4.3](#43-direction)），名称请用 `SortSpec.ofPath`。
- **计划中：** `SortSpec` 的 join 写法、键集分页（keyset paging）。（join 的 Lambda 写法 `{ o, c, by -> ... }` 已实现，见 [6.8](06-select-group.md#68-explicit-joins)。）

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
| S1 | 排序是一个 Lambda：`{ o, by -> by... }`。一个排序键时：`{ it.key }`，可选 `Direction`。join 时：`{ o, c, by -> by... }`（`by` 总是最后一个） | 已实现，join 写法 `{ o, c, by -> }` 也已实现（用 Java Lambda 测试） |
| S2 | 排序键是 `Comparable`（允许 Kotlin 可空排序键）。实体不能作为排序键（编译错误）。排序键必须使用实体 | 已实现 |
| S3 | 方向来自 `asc` / `desc` 或 `sort(key, Direction)`。`asc(key)`/`desc(key)`（`ASC`/`DESC`）使用 NULL 策略；`asc(key, nullsFirst)`、`desc(key, nullsFirst)` 和 `*_NULLS_*` 按写法使用 | 已实现 |
| S4 | 主体只能是一条直接的 `by` 调用链。不能有 `if`、`when` 或循环 | 已实现 |
| S5 | 每个查询只调用一次 `orderBy`。`orderBy` 之后不能调用 `where`。`limit` 之后不能调用 `orderBy`（编译错误） | 已实现 |
| S6 | 用 `SortSpec`（`of`、`ofPath`、`then`、`reversed`、`orderBy(spec)`）重用。外部字符串由持久化单元的排序策略（`SortRules`、`SortPolicy`、`SortMode`，见 [4.10](#sort-policy)）检查 | `SortSpec` 和排序策略已实现（用 Java Lambda 测试）。`SortSpec` 的 join 写法尚未实现 |
| S7 | 排序键：列、对一路径、`+ - *`、拓宽转换。不能有值分支。Kotlin 的 `?.` 被当作路径读取 | 已实现（未用 Kotlin Lambda 测试） |
| S8 | 只在 `orderBy` 中使用的关联路径使用 `left join`，因此没有关联的行会被保留。必需关联（`optional = false`）不会丢失行，因此使用 inner join（A4）。如果 `where` 使用同一条路径，排序会使用 `where` 的 join | 已实现 |
| S9 | 分页时，如果排序键不包含根实体的 `@Id`，会添加 `@Id asc`。如果排序键中有唯一且不为 NULL 的列（`unique = true` 且 `nullable = false`、`@NaturalId`），则不添加（A5）。集合 fetch join + 分页会被拒绝 | 根实体 id 和显式 join 实体的 id 部分已实现（用 Java Lambda 测试）。fetch join 检查尚未实现 |
| S10 | 分析结果按 Lambda 缓存。捕获的 `Direction` 每次调用都会读取 | 已实现 |
| S11 | Spring `Sort.Order` 与 `Direction` 一一对应（Spring 模块，见 [12.5](12-spring-data-jpa.md#125-spring-sort-and-pageable)）。属性名经过排序策略检查，别名按原样传递 | Spring `Sort` 已实现，并用 Java 测试（尚不支持 `ignoreCase`），与排序策略一起使用的情况也已测试。读取 `"price,desc_nulls_last"` 这样的一段文本尚未实现 |
| S12 | join 查询使用相同的形式：`{ o, c, by -> }`、`{ o, c, i, by -> }` | 已实现（用 Java Lambda 测试，见 [6.8](06-select-group.md#68-explicit-joins)） |
| S13 | 属性引用只能用于一个排序键的简短写法（`orderBy(Order::price)`）。能编译但含义错误的引用会被拒绝 | 尚未实现（Kotlin 属性引用） |
| S14 | 子查询的排序：有 `limit` 时保留，没有 `limit` 时移除并发出警告。数据库差异由 lambdaQL 替你处理（见第 5 章） | 已实现（已测试）。MariaDB 的 `lateral` 改写尚未实现 |

---

[Kotlin 路线](README.md) | 上一章：[3. 条件（`where`）](03-where.md) | 下一章：[5. 子查询](05-subquery.md) | [本页的 Java 版本](../java/04-order-paging.md)
