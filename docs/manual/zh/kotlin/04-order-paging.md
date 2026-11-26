<a id="4-sorting-and-paging-kotlin"></a>
# 4. 排序与分页（Kotlin）

[Kotlin 路线](README.md) | [手册首页](../README.md) | 上一章：[3. 条件（`where`）](03-where.md) | 下一章：[5. 子查询](05-subquery.md) | [本页的 Java 版本](../java/04-order-paging.md)

公共页面：[1. 概述](../01-overview.md) · [11. 错误与语言](../11-errors-and-languages.md)

---

<a id="41-one-lambda-for-the-whole-sort"></a>
## 4.1 用 Lambda 表达式表达排序

排序用**一个 Lambda 表达式**编写。Lambda 表达式接收实体，并用类 `By` 的排序函数描述排序键和方向。
连续调用 `desc(...)`、`asc(...)` 来添加排序键。

<!-- region: KotlinQueryTest.kt#sort -->
```kotlin
import org.lambdaql.query.By.desc

val q = qb.from(Order::class.java)
    .where { it.isActive }
    .orderBy { o -> desc(o.price).asc(o.customer?.name) }
    .limit(3)
assertEquals("select e0 from Order e0 left join e0.customer j1 where e0.active = true"
    + " order by e0.price desc, j1.name asc nulls first, e0.id asc", q.toHql().hql())
```

示例使用以 Java 编写的示例实体（`Order`、`Customer`、`Item`、`OrderLine`）。`price` 是基本类型 `double`，因此没有 `nulls` 部分；`active` 的 getter 是 `isActive()`（Kotlin 中为 `it.isActive`）。
使用 `limit` 时，lambdaQL 会把 `e0.id asc` 添加为最后一个排序键（见[同序处理](#tie-breaker-automatic-id)）。

- 排序函数是 `By` 的 static 方法：`asc(key)`、`desc(key)`、`asc(key, Nulls.FIRST)`、`desc(key, Nulls.LAST)`、`sort(key, Direction)`。
- lambdaQL 不调用 Lambda 表达式，而是分析字节码 opcode。排序函数只描述顺序。在排序 Lambda 表达式之外调用时会抛出 `IllegalStateException`（`LQL-4046`）。

示例代码：[`KotlinQueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)（`sort`）。

<a id="42-the-short-forms-one-key"></a>
## 4.2 简短写法（一个排序键）

只有一个排序键时，可以不用排序函数，只返回排序键。

| Kotlin | HQL |
|---|---|
| `orderBy { it.price }` | `order by e0.price asc` |
| `orderBy({ it.updateAt }, Direction.DESC)` | `order by e0.updateAt desc nulls last` |

<!-- region: ManualKotlinSortTest.kt#short-form -->
```kotlin
// one key: the key alone, ascending unless a Direction is given
val byPrice = qb.from(Order::class.java).orderBy { it.price }
assertEquals("select e0 from Order e0 order by e0.price asc", byPrice.toHql().hql())
val newest = qb.from(Order::class.java).orderBy({ it.updateAt }, Direction.DESC)
assertEquals("select e0 from Order e0 order by e0.updateAt desc nulls last", newest.toHql().hql())
```

不指定 `Direction` 时，简短写法按升序（`ASC`）排序。
`Direction` 参数只用于单个排序键。像 `orderBy({ o -> desc(o.price) }, Direction.ASC)` 这样同时使用排序函数和 `Direction` 参数，会以 `LQL-3069` 被拒绝。如果方向需要作为参数传入，请使用 `orderBy { o -> sort(o.price, direction) }`。

示例代码：[`ManualKotlinSortTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSortTest.kt)（`short-form`）。

## 4.3 `Direction`

`Direction` 有六个值。

| 值 | 含义 | NULL 的位置 |
|---|---|---|
| `ASC` | 升序 | NULL 在**前**（默认策略） |
| `DESC` | 降序 | NULL 在**后**（默认策略） |
| `ASC_NULLS_FIRST` | 升序 | NULL 在前。与 `ASC` 相同 |
| `ASC_NULLS_LAST` | 升序 | NULL 在后 |
| `DESC_NULLS_FIRST` | 降序 | NULL 在前 |
| `DESC_NULLS_LAST` | 降序 | NULL 在后。与 `DESC` 相同 |

`Direction` 还有两个辅助方法。

- `Direction.parse(text)` 从请求参数之类的文本读取方向。大小写无关，`-`、`_` 和空格视为相同：`Direction.parse("desc-nulls-last")` 是 `DESC_NULLS_LAST`。未知的文本会抛出 `IllegalArgumentException`。
- `reversed()` 返回相反的方向。NULL 的位置也一起翻转，因此整个顺序都会反过来：`Direction.ASC.reversed()` 是 `DESC`，`Direction.ASC_NULLS_LAST.reversed()` 是 `DESC_NULLS_FIRST`。

**要选择 NULL 的位置**，请用枚举 `Nulls` 作为第二个参数：`asc(key, Nulls.LAST)`、`desc(key, Nulls.FIRST)`。
`Nulls.FIRST` 表示"NULL 在前"，`Nulls.LAST` 表示"NULL 在后"。
没有第二个参数时，`asc(key)` 和 `desc(key)` 使用默认的 NULL 策略（见 [4.4](#44-null-ordering-is-the-same-on-every-database)）。

| 写法 | 等同于 |
|---|---|
| `asc(key)` | `ASC`（NULL 在前，默认策略） |
| `desc(key)` | `DESC`（NULL 在后，默认策略） |
| `asc(key, Nulls.FIRST)` | `ASC_NULLS_FIRST` |
| `asc(key, Nulls.LAST)` | `ASC_NULLS_LAST` |
| `desc(key, Nulls.FIRST)` | `DESC_NULLS_FIRST` |
| `desc(key, Nulls.LAST)` | `DESC_NULLS_LAST` |

**当方向来自变量时**，请使用 `sort(key, direction)` 并传入捕获的 `Direction`。

<!-- region: ManualKotlinSortTest.kt#nulls-and-direction -->
```kotlin
// the second argument: true = NULL first, false = NULL last
val nullsLast = qb.from(Order::class.java).orderBy { o -> asc(o.description, Nulls.LAST).asc(o.id) }
assertEquals("select e0 from Order e0 order by e0.description asc nulls last, e0.id asc", nullsLast.toHql().hql())
// a direction from a variable: choose it outside the lambda
val dir = if (newestFirst) Direction.DESC else Direction.ASC
val byDate = qb.from(Order::class.java).orderBy { o -> sort(o.updateAt, dir).asc(o.id) }
assertEquals("select e0 from Order e0 order by e0.updateAt desc nulls last, e0.id asc", byDate.toHql().hql())
// the short form with a NULL position
val byName = qb.from(Order::class.java).orderBy({ it.customer?.name }, Direction.DESC_NULLS_LAST)
assertEquals("select e0 from Order e0 left join e0.customer j1 order by j1.name desc nulls last", byName.toHql().hql())
```

Lambda 表达式只分析一次。每次执行查询时都会读取 `dir`。为同一个 Lambda 表达式传入不同的方向时，HQL 和结果行会随之改变。方向为 `null` 时，执行查询时会抛出异常。

请在 Lambda 表达式**外部**选择 `dir`。排序 Lambda 表达式内部的 `if` 会被拒绝（见 [4.6](#46-rules-for-the-lambda-body)）。

示例代码：[`ManualKotlinSortTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSortTest.kt)（`nulls-and-direction`）。

<a id="44-null-ordering-is-the-same-on-every-database"></a>
## 4.4 NULL 的排序在每个数据库上都相同

各数据库对 NULL 的位置并不一致。

| | H2 | MariaDB | Oracle |
|---|---|---|---|
| `ASC` 默认 | NULL 在前 | NULL 在前 | NULL 在**后** |
| `DESC` 默认 | NULL 在后 | NULL 在后 | NULL 在**前** |
| SQL 中的 `NULLS FIRST/LAST` | 支持 | **不支持（语法错误）** | 支持 |

lambdaQL 用一条策略解决这个问题：**NULL 是最小的值**。

- `ASC` = `ASC_NULLS_FIRST`
- `DESC` = `DESC_NULLS_LAST`

这与 Kotlin 的比较器 `nullsFirst(naturalOrder())` 相同。
使用这条策略，三种数据库都会返回相同的顺序（对于数字和日期）。

**lambdaQL 的写法。** 对于可能为 NULL 的排序键，lambdaQL 总是把 NULL 的位置写入 HQL，例如 `e0.description asc nulls first`。
不会为 NULL 的排序键（像 `active: Boolean` 或 `qty: Int` 这样的 JVM 基本类型，或者 `@Id`）没有 `nulls` 部分。
lambdaQL 看的是 JPA 元数据，而不是 Kotlin 类型。因此带 `@Id` 的 `id`（Kotlin 中为 `Long?`）没有 `nulls` 部分，而 `description`（`String?`）有（见 [4.10](#410-reusable-and-external-sorts)）。
关联后面的列在没有关联时为 NULL（订单可能没有客户），因此会带上这一部分。如果路径上的每一步关联都是必需的（`optional = false`），并且值不会为 NULL，就不会带上。

然后 Hibernate 为每种数据库生成 SQL。与数据库默认值相同时，会省略这个子句。
MariaDB 没有 `NULLS` 语法，因此对于不是默认值的位置，Hibernate 会写出 `CASE` 表达式。

生成的 SQL：

| 写法 | H2 | MariaDB | Oracle |
|---|---|---|---|
| `asc(key)`、`desc(key)`（`ASC`、`DESC`） | 没有额外子句（本来就是默认值） | 没有额外子句 | 添加 `nulls first` / `nulls last` |
| 与数据库默认值一致的 NULL 位置（`asc(key, nulls)`、`desc(key, nulls)`、`*_NULLS_*`） | 没有额外子句 | 没有额外子句 | 没有额外子句 |
| 与数据库默认值**不**一致的 NULL 位置 | `nulls first/last` | Hibernate 生成 `case when x is null ...` | `nulls first/last` |
| 不会为 NULL 的排序键（与方向无关） | 没有额外子句 | 没有额外子句 | 没有额外子句 |

> [!TIP]
> 在 MariaDB 上，`asc(key, Nulls.LAST)`（`ASC_NULLS_LAST`）和 `desc(key, Nulls.FIRST)`（`DESC_NULLS_FIRST`）会变成 `CASE` 表达式。数据库无法为这种排序使用索引。请尽量使用 `asc(key)` / `desc(key)`。

<a id="45-what-a-sort-key-can-be"></a>
## 4.5 排序键可以是什么

| 排序键 | 是否允许 | 说明 |
|---|---|---|
| 列：`o.price` | 是 | 可空排序键（`Double?`、`String?`）也可以 |
| 对一路径：`o.customer?.name`、`o.customer!!.name` | 是 | `left join`（必需关联使用 inner join），见下文 |
| `@Lob` 列 | 是 | Oracle 会拒绝。`DEBUG` 级别的 `LQL-5010`，见下文 |
| `@Enumerated(EnumType.STRING)` 枚举列 | 是 | 按名称排序（不是声明顺序）。`DEBUG` 级别的 `LQL-5011`，见下文 |
| 算术运算：`o.price * 2`、`+`、`-` | 是 | `(e0.price * 2.0)` |
| 拓宽转换：`o.status.toLong()` | 是 | 转换不出现在 HQL 中 |
| 非布尔值上的 Elvis：`o.description ?: ""` | 是 | `coalesce(e0.description, '') asc nulls first` |
| 不使用实体的值：`asc(1)`、捕获的值 | **会被拒绝** | 见 [4.6](#46-rules-for-the-lambda-body) |
| 排序键值中的 `if`：`asc(if (o.isActive) 0 else 1)` | 是 | `case when`（[3.5.1](03-where.md#351-values-chosen-by-a-condition)） |
| 选择排序函数的 `if` | **会被拒绝**（`LQL-2023`） | 见 [4.6](#46-rules-for-the-lambda-body) |

<!-- region: ManualKotlinSortTest.kt#sort-keys -->
```kotlin
// arithmetic, a widening conversion and the Elvis operator of a value
assertEquals("select e0 from Order e0 order by (e0.price * 2.0) asc",
    qb.from(Order::class.java).orderBy { o -> asc(o.price * 2) }.toHql().hql())
assertEquals("select e0 from Order e0 order by e0.status asc",
    qb.from(Order::class.java).orderBy { o -> asc(o.status.toLong()) }.toHql().hql())
val elvis = qb.from(Order::class.java).orderBy { o -> asc(o.description ?: "").asc(o.id) }
assertEquals("select e0 from Order e0 order by coalesce(e0.description, '') asc nulls first, e0.id asc", elvis.toHql().hql())
```

**关联路径使用 `left join`。** 在 HQL 中，普通路径 `order by o.customer.name` 在三种数据库上都会变成 inner join。没有客户的订单会消失。
lambdaQL 改为添加 `left join`。没有客户的订单也会保留在结果中。这些订单的 `j1.name` 为 NULL，因此在 `asc` 时排在最前面。

<!-- region: ManualKotlinSortTest.kt#sort-path -->
```kotlin
// a path used only for sorting is a left join: the orders without a customer stay
val q = qb.from(Order::class.java).orderBy { o -> asc(o.customer?.name).asc(o.id) }
assertEquals("select e0 from Order e0 left join e0.customer j1 order by j1.name asc nulls first, e0.id asc", q.toHql().hql())
```

**各数据库的 SQL**

<!-- sql: sort-path -->
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

在 SQL 中可以看到 NULL 策略。H2 和 MariaDB 在与自身默认值相同时省略该子句。Oracle 的默认值正好相反，所以写出 `asc nulls first` / `desc nulls last`。MariaDB 没有 `NULLS` 语法，所以对于不是默认值的位置，先按 `case when … is null then … end` 排序。

**必需关联使用 inner join。** 用 `optional = false` 映射的关联（例如 `@ManyToOne(optional = false)`）总有目标，因此 inner join 不会丢失行。
所以即使路径只用于排序或 select，这类关联也使用 `join`。其他关联像上面一样使用 `left join`。
如果路径上的每一步关联都是必需的，并且值不会为 NULL，排序也不会带 `nulls first/last`。

示例实体 `OrderLine` 的 `order` 是 `@ManyToOne(optional = false)`。

<!-- region: ManualKotlinSortTest.kt#required-association -->
```kotlin
// order is optional = false: a sort or select path through it cannot lose rows, so it is an inner join
val sorted = qb.from(OrderLine::class.java).orderBy { l -> asc(l.order.price) }
assertEquals("select e0 from OrderLine e0 join e0.order j1 order by j1.price asc", sorted.toHql().hql())
val prices = qb.from(OrderLine::class.java).select { it.order.price }
assertEquals("select j1.price from OrderLine e0 join e0.order j1", prices.toHql().hql())
```

**`@Lob` 列的排序。** 在 `orderBy`、`distinct`、`groupBy` 中使用 `@Lob` 列时，H2 和 MariaDB 会执行，Oracle 会拒绝（ORA-22848、ORA-22849，实测）。`=` 比较在三种数据库上都可以执行。
lambdaQL 不修改查询，而是对每个列和每种用法以 `DEBUG` 级别记录一次 `LQL-5010`。

下面三个查询各记录一次 `LQL-5010`。

<!-- region: ManualKotlinSortTest.kt#lob -->
```kotlin
// memo is @Lob: H2 and MariaDB sort it, group it and make it distinct, Oracle rejects it (ORA-22848, ORA-22849).
// lambdaQL keeps the query and logs LQL-5010 at DEBUG
qb.from(OrderLine::class.java).orderBy { l -> asc(l.memo) }.toHql()
qb.from(OrderLine::class.java).groupBy { it.memo }.select { it.memo }.toHql()
qb.from(OrderLine::class.java).select { it.memo }.distinct().toHql()
```

**`EnumType.STRING` 枚举的排序。** 对于用 `@Enumerated(EnumType.STRING)` 映射的枚举列，数据库按常量**名称**的字符串顺序排序。Java 的枚举比较遵循声明顺序，因此结果的顺序可能不同。
lambdaQL 不修改查询，而是对每个列以 `DEBUG` 级别记录一次 `LQL-5011`。`ORDINAL`（或没有 `@Enumerated`）按声明顺序排序，因此不记录。

下面的查询记录一次 `LQL-5011`。

<!-- region: ManualKotlinSortTest.kt#enum-string-sort -->
```kotlin
// kind is @Enumerated(EnumType.STRING): the database sorts by name, Kotlin by declaration order (LQL-5011, DEBUG)
val q = qb.from(Order::class.java).orderBy { o -> asc(o.kind) }
assertEquals("select e0 from Order e0 order by e0.kind asc nulls first", q.toHql().hql())
```

别名与 `where` 共用。如果 `where` 已经 join 了同一条路径，排序就使用那个 join。
如果 `where` 以 inner join 使用这条路径，没有客户的订单已经被排除。如果 `where` 以 `left join` 使用这条路径，排序也使用那个 `left join`（见 [3.10](03-where.md#310-association-paths)）。

示例代码：[`ManualKotlinSortTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSortTest.kt)（`sort-keys`、`sort-path`、`required-association`、`lob`、`enum-string-sort`）。

<a id="46-rules-for-the-lambda-body"></a>
## 4.6 Lambda 表达式主体的规则

主体必须是**一个排序键**，或者是**一条直接的 `By` 函数调用链**。

<!-- region: ManualKotlinSortTest.kt#lambda-rules -->
```kotlin
// allowed: a straight chain of by calls
qb.from(Order::class.java).orderBy { o -> desc(o.price).asc(o.id) }
// rejected when orderBy is called: sort functions chosen by a condition (a condition inside one key is allowed)
val branch = assertThrows(LambdaQLException::class.java) {
    qb.from(Order::class.java).orderBy { o -> if (flag) desc(o.price) else asc(o.id) }
}
assertEquals("LQL-2023", branch.code())
// rejected: a key that does not use the entity
val constant = assertThrows(LambdaQLException::class.java) {
    qb.from(Order::class.java).orderBy { _ -> asc(1) }
}
assertEquals("LQL-3031", constant.code())
```

lambdaQL 在调用 `orderBy` 时检查这一点，并抛出 `LambdaQLException`（按条件选择的排序函数为 `LQL-2023`，不使用实体的排序键为 `LQL-3031`）。
每个排序键都必须使用实体。只由常量或捕获值构成的排序键会被拒绝。

如果方向需要在运行时改变，请作为变量传入：`sort(key, dir)`。如果排序键本身需要改变，请在普通 Kotlin 代码的两个分支中分别构建查询。

示例代码：[`ManualKotlinSortTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSortTest.kt)（`lambda-rules`）。

<a id="47-order-of-calls"></a>
## 4.7 调用顺序

查询按 `from(...).where { }.orderBy { }.limit(...).offset(...)` 的顺序编写。
只使用 `limit` 或 `offset` 而没有 `orderBy` 时，不会加上 `order by`（见 [4.8](#48-paging)）。

<a id="48-paging"></a>
## 4.8 分页

<!-- region: ManualKotlinSortTest.kt#page -->
```kotlin
val page = qb.from(Order::class.java)
    .where { it.status.toInt() == 1 }
    .orderBy { o -> desc(o.price) }
    .limit(2)      // page size
    .offset(2)     // skip 2 rows (page 2)
// limit and offset go to Hibernate; the id is added as the last key
assertEquals("select e0 from Order e0 where e0.status = 1 order by e0.price desc, e0.id asc", page.toHql().hql())
```

`limit` 和 `offset` 会传给 Hibernate（`setMaxResults`、`setFirstResult`）。示例实体的 `status` 是 `short`，因此用 `it.status.toInt() == 1` 比较。lambdaQL 忽略这个转换。

`limit` 和 `offset` 接受 0 或更大的值。传入负值会抛出 `IllegalArgumentException`。

lambdaQL 不把 `LIMIT` 写入 HQL。Hibernate 为每种数据库生成合适的 SQL。

| 数据库 | SQL |
|---|---|
| H2 | `offset ? rows fetch first ? rows only` |
| MariaDB | `limit ?, ?` |
| Oracle | `offset ? rows fetch first ? rows only` |

**不带 `orderBy` 的分页。** 不调用 `orderBy` 而调用 `limit` 和 `offset` 时，不会加上 `order by`。哪些行出现在哪一页由数据库决定。要稳定地分页，请使用 `orderBy`。

<!-- region: ManualKotlinSortTest.kt#page-only -->
```kotlin
// paging without orderBy: no order by, so which rows come is up to the database; use orderBy for stable pages
val q = qb.from(Order::class.java).where { it.isActive }.limit(2)
assertEquals("select e0 from Order e0 where e0.active = true", q.toHql().hql())
```

**各数据库的 SQL**

<!-- sql: page-only -->
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

分页 SQL 在各数据库上不同。H2 和 Oracle 接受 `fetch first ? rows only`，MariaDB 接受 `limit ?`。有 offset 时，H2 和 Oracle 会加上 `offset ? rows`，MariaDB 写成 `limit ?,?`。

**第一行与总行数。**

- `orderBy { ... }.first()` 以 `Optional` 返回按该顺序排列的第一行。只读取一行。`offset` 会保留：`orderBy { ... }.offset(1).first()` 返回第二行。
- `count()` 统计所有匹配条件的行。在分页画面中，请把 where 阶段的查询保存在一个值中。在这个值上调用 `count()` 得到总数，再调用 `orderBy { ... }.limit(...).toList()` 得到这一页。

<!-- region: ManualKotlinSortTest.kt#first-and-count -->
```kotlin
// keep the where step in a value: count() gives the number of all rows, orderBy ... limit gives one page
val active = qb.from(Order::class.java).where { it.isActive }
val total = active.count()
val page = active.orderBy { o -> desc(o.price) }.limit(2).toList()
// first() reads one row; offset(1).first() is the second row
val second = active.orderBy { o -> desc(o.price) }.offset(1).first()
```

见[获取结果](02-getting-started.md#getting-the-result)。

<a id="tie-breaker-automatic-id"></a>
### 同序处理（自动添加 `id`）

上面 HQL 的末尾有 lambdaQL 添加的 `e0.id asc`。原因如下。

- 多行可能有相同的 `price`。数据库可能以**任意顺序**返回这些行。Oracle 确实会这样（已实测）。
- 这样第 2 页和第 3 页可能会把同一行显示两次，或者漏掉某一行。

规则：**同时使用 `orderBy` 和分页且排序键中没有 id 时，lambdaQL 会把根实体的 `@Id asc` 添加为最后一个排序键。**

- 只添加一次。排序键中已有 id 时（例如 `desc(o.id)`）不添加。
- 排序键中有主实体上唯一且不为 NULL 的列时不添加。`nullable = false` 的 `@Column(unique = true)`、Hibernate `@NaturalId`、`@Id` 都属于这种列，因为仅凭这个键就能确定所有行的顺序。
  可以为 NULL 的唯一列不属于这种列，因为多行可能都是 NULL。
- 没有 `orderBy`，或者没有 `limit` 和 `offset` 时，什么都不添加。
- 有显式 join（`join`、`leftJoin`、`crossJoin`）时，也会按 join 的顺序添加每个被 join 实体的 id（见 [6.8](06-select-group.md#68-explicit-joins)）。

示例实体 `OrderLine` 的 `code` 是 `@Column(unique = true, nullable = false)`。

<!-- region: ManualKotlinSortTest.kt#unique-key -->
```kotlin
// code is @Column(unique = true, nullable = false): it orders every row, so paging adds no id tie-breaker
assertEquals("select e0 from OrderLine e0 order by e0.code asc",
    qb.from(OrderLine::class.java).orderBy { l -> asc(l.code) }.limit(2).toHql().hql())
// a key that is not unique still gets the id tie-breaker
assertEquals("select e0 from OrderLine e0 join e0.order j1 order by j1.price asc, e0.id asc",
    qb.from(OrderLine::class.java).orderBy { l -> asc(l.order.price) }.limit(2).toHql().hql())
```

示例代码：[`ManualKotlinSortTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSortTest.kt)（`page`、`page-only`、`first-and-count`、`unique-key`）。

<a id="49-strings-and-special-values"></a>
## 4.9 字符串与特殊值

- 字符串的顺序遵循数据库的排序规则（collation）。MariaDB 的 `_ci` 忽略大小写。Oracle 使用跟随 JVM locale 的 `NLS_SORT`（例如 `de_DE` 时为德语顺序）。对于 Oracle，推荐使用 `NLS_SORT=BINARY`。
- Oracle 把 `''` 存储为 NULL，因此空字符串会像 NULL 一样排序。
- 各数据库对 `NaN` 的排序不同。

<a id="410-reusable-and-external-sorts"></a>
## 4.10 可重用的排序与外部排序

**`SortSpec`：可以保存起来的排序。** `SortSpec` 是保存在值中、在多个查询中使用的排序。
用 `SortSpec.of(Order::class.java) { o -> desc(o.id) }` 这样的方式创建。这个 Lambda 表达式与 `orderBy { o -> ... }` 的 Lambda 表达式相同，因此适用同样的规则（见 [4.6](#46-rules-for-the-lambda-body)）。
然后传给 `orderBy(spec)`。

<!-- region: ManualKotlinSortTest.kt#sort-spec -->
```kotlin
// keep it in a value (for example at the top level or in a companion object) and use it in many queries
val byDescription = SortSpec.of(Order::class.java) { o -> desc(o.description).asc(o.id) }
val q = qb.from(Order::class.java).where { it.isActive }.orderBy(byDescription)
assertEquals("select e0 from Order e0 where e0.active = true order by e0.description desc nulls last, e0.id asc",
    q.toHql().hql())
```

- `SortSpec` 不能修改。`then()` 和 `reversed()` 返回新的 spec。因此一个 spec 可以安全地在多个查询和线程之间共享，例如可以放在顶层 `val` 中。
- lambdaQL 在 spec 用于 `orderBy` 时检查 Lambda 表达式。每个 Lambda 表达式只分析一次。
- 捕获的值是创建 Lambda 表达式那一刻的值。顶层 `val` 中的 spec 会一直保留这些值。如果方向需要在执行时改变，请使用 `orderBy { o -> sort(key, dir) }`（见 [4.3](#43-direction)）。
- `orderBy(spec)` 之后也可以分页：`limit`、`offset`、`toList()`、`first()`。

**各数据库的 SQL**

<!-- sql: sort-spec -->
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

SQL 与用 `orderBy { o -> ... }` 写出同样的排序时相同。Oracle 对 `desc` 的默认值正好相反，因此加上 `nulls last`（见 [4.4](#44-null-ordering-is-the-same-on-every-database)）。

**`then()`：在已有排序键之后追加排序键。** `a.then(b)` 先按 `a` 的排序键排序。值相同的行再按 `b` 的排序键排序。
`then()` 接受另一个 `SortSpec` 或排序 Lambda 表达式。

<!-- region: ManualKotlinSortTest.kt#sort-spec-then -->
```kotlin
val byStatus = SortSpec.of(Order::class.java) { o -> asc(o.status) }
val byPrice = SortSpec.of(Order::class.java) { o -> desc(o.price) }
val q = qb.from(Order::class.java).orderBy(byStatus.then(byPrice))
assertEquals("select e0 from Order e0 order by e0.status asc, e0.price desc", q.toHql().hql())
// then() also takes a sort lambda
assertEquals("select e0 from Order e0 order by e0.status asc, e0.id desc",
    qb.from(Order::class.java).orderBy(byStatus.then { o -> desc(o.id) }).toHql().hql())
```

`status`（`short`）和 `price`（`double`）不会为 NULL，因此 HQL 中没有 `nulls` 部分。

**`reversed()`：把所有排序键反过来。** `spec.reversed()` 反转所有排序键。NULL 的位置也一起反转：`asc`（NULL 在前）变成 `desc`（NULL 在后）。因此整个行顺序正好完全颠倒。
这与 `Direction.reversed()` 相同（见 [4.3](#43-direction)）。`spec.reversed().reversed()` 会回到原来的顺序。

<!-- region: ManualKotlinSortTest.kt#sort-spec-reversed -->
```kotlin
val spec = SortSpec.of(Order::class.java) { o -> asc(o.description).asc(o.id) }
// every key is reversed, the NULL position too: asc nulls first -> desc nulls last
val q = qb.from(Order::class.java).orderBy(spec.reversed())
assertEquals("select e0 from Order e0 order by e0.description desc nulls last, e0.id desc", q.toHql().hql())
```

`reversed()` 的结果行正好是原来顺序的倒序。

**各数据库的 SQL**

<!-- sql: sort-spec-reversed -->
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

H2 和 MariaDB 对 `desc` 本来就把 NULL 放在后面，因此不写额外的子句。Oracle 会加上 `nulls last`。

**`SortSpec.ofPath`：根据属性名创建的排序。** `SortSpec.ofPath(Order::class.java, "customer.name", Direction.DESC)` 根据属性路径创建 spec。用于以字符串形式来自代码外部的排序键，Spring 模块就是这样转换 Spring `Sort` 的（见 [12.5](12-spring-data-jpa.md#125-spring-sort-and-pageable)）。
路径的每一部分都必须是持久化属性，最后一部分必须是基本值。其他路径都是错误。
允许哪些名称由排序策略决定（见下面的[排序策略](#sort-policy)）。既没有策略也没有 `@Sortable` 字段时，任何持久化属性都会被接受，并且 lambdaQL 会记录 `LQL-5003`（默认级别为 `INFO`）。
<!-- region: ManualKotlinSortTest.kt#sort-spec-path -->
```kotlin
// for sorts chosen outside the code (the Spring module uses it); only real properties are accepted
val byName = SortSpec.ofPath(Order::class.java, "customer.name", Direction.DESC)
    .then(SortSpec.ofPath(Order::class.java, "id", Direction.ASC))
val q = qb.from(Order::class.java).orderBy(byName)
assertEquals("select e0 from Order e0 left join e0.customer j1 order by j1.name desc nulls last, e0.id asc", q.toHql().hql())
assertThrows(IllegalArgumentException::class.java) {
    qb.from(Order::class.java).orderBy(SortSpec.ofPath(Order::class.java, "price; drop table x", Direction.ASC))
}
```

<a id="sort-policy"></a>
### 排序策略：允许哪些外部名称

**已实现**。

来自代码外部的排序名称（请求参数、Spring `Sort` 或 `Pageable`）可以指向任何持久化属性。
**排序策略**决定允许哪些名称。

- `SortRules<T>` 是一个实体允许的名称列表。每一列都用 Lambda 表达式指定。
- `SortPolicy` 保存各实体的规则和模式（`SortMode`）。每个 `EntityManagerFactory`（即每个 persistence unit）注册一个策略。使用该工厂的所有 `QueryBuilder` 和 repository 都使用这个策略。
- 在实体字段上加 `@Sortable`，无需代码即可指定规则（见下面的 [`@Sortable` 规则](#rules-from-sortable)）。

一个实体的规则按以下顺序只从**一个地方**获取：策略中的 `SortRules`（代码或 Spring `SortPolicy` bean）、Spring Boot 属性 `lambdaql.sort.*`（见 [12.5](12-spring-data-jpa.md#sort-policy-with-spring)）、实体的 `@Sortable` 字段。
不会合并两个地方的规则。

**检查对象。** 只检查由字符串创建的排序：`SortSpec.ofPath`，以及 Spring `Sort` 或 `Pageable`（Spring 模块使用 `ofPath`，见 [12.5](12-spring-data-jpa.md#sort-policy-with-spring)）。
排序 Lambda 表达式（`orderBy { o -> ... }`、`SortSpec.of`）是开发者的代码，因此不检查。

| 规则 | 允许的外部名称 |
|---|---|
| `allow { it.price }` | `price`（列的路径） |
| `allow { it.customer?.name }` | `customer.name`。经过关联的路径只有像这样明确指定时才允许 |
| `alias("newest") { it.updateAt }` | `newest`。排序依据是 `updateAt`。此时路径 `updateAt` 本身**不是**外部名称，因此客户端看不到属性名 |

规则必须是实体的列。
其他排序键（例如 `allow { it.price * 2 }`）会在第一次使用规则时以 `LQL-4041` 被拒绝。

| `SortMode` | 对规则不允许的名称的处理 |
|---|---|
| `OFF` | 执行排序，不做检查 |
| `INFO` | 执行排序，并按实体和名称各以 `INFO` 级别记录一次 `LQL-5003`。**这是默认值：**没有注册策略时使用 `INFO`，规则只来自 `@Sortable` |
| `WARN` | 与 `INFO` 相同，只是日志级别为 `WARN` |
| `STRICT` | 以 `SortNotAllowedException`（`LQL-4040`）拒绝。既没有规则也没有 `@Sortable` 字段的实体，所有名称都会被拒绝 |

因此在注册策略之前，`@Sortable` 不允许的每个外部排序名称都会按实体记录一次 `LQL-5003`。
要拒绝未知名称，请以 `STRICT` 注册策略；要关闭检查，请以 `OFF` 注册。

| 方法 | 功能 |
|---|---|
| `SortPolicy.of(mode, rules...)` | 用模式和各实体的规则创建策略 |
| `SortPolicy.register(emf, policy)` | 注册 persistence unit 的策略。替换之前的策略 |
| `SortPolicy.unregister(emf)` | 移除策略。恢复默认值（`INFO`，只使用 `@Sortable` 规则） |
| `SortPolicy.find(emf)` | 返回已注册的策略（`Optional`） |
| `SortPolicy.effective(emf)` | 返回生效中的策略。没有已注册的策略时返回默认值 |

在 Spring Boot 中，请定义 `SortPolicy` bean，而不是调用 `register`（见 [12.5](12-spring-data-jpa.md#sort-policy-with-spring)）。

已注册的策略会一直应用于该 persistence unit 的所有查询。不再需要策略时，请用 `SortPolicy.unregister(emf)` 移除。

<!-- region: ManualKotlinSortTest.kt#policy -->
```kotlin
val orderRules = SortRules.of(Order::class.java)
    .allow { it.price }                      // the name "price"
    .allow { it.customer?.name }             // an association path: allowed only when listed
    .alias("newest") { it.updateAt }         // the outside name "newest" sorts by updateAt
SortPolicy.register(emf, SortPolicy.of(SortMode.STRICT, orderRules))

val newest = qb.from(Order::class.java).orderBy(SortSpec.ofPath(Order::class.java, "newest", Direction.DESC))
assertEquals("select e0 from Order e0 order by e0.updateAt desc nulls last", newest.toHql().hql())
// not in the rules, and the path behind an alias: SortNotAllowedException when orderBy is called
for (name in listOf("description", "updateAt")) {
    val e = assertThrows(SortNotAllowedException::class.java) {
        qb.from(Order::class.java).orderBy(SortSpec.ofPath(Order::class.java, name, Direction.ASC))
    }
    assertEquals("LQL-4040", e.code())
}
// a sort lambda is your code: never checked
assertEquals("select e0 from Order e0 order by e0.description asc nulls first",
    qb.from(Order::class.java).orderBy { o -> asc(o.description) }.toHql().hql())
```

<a id="rules-from-sortable"></a>
**`@Sortable` 规则。** 在实体的字段或 getter 上加 `@org.lambdaql.Sortable` 也会成为规则。

| 注解 | 允许的外部名称 |
|---|---|
| 字段 `name` 上的 `@Sortable` | `name` |
| 字段 `grade` 上的 `@Sortable("level")` | `level`。排序依据是 `grade`（别名） |
| 关联字段 `customer` 上的 `@Sortable(path = "name")` | `customer.name` |

- lambdaQL 只读取 `from(...)` 中指定的实体的注解。其他实体上的注解不会允许关联路径，因此请在关联字段上指定 `path`。
- 只有当策略中没有该实体的规则（代码、Spring bean、Spring Boot 属性）时，才使用注解。
- 在没有注册策略的默认 `INFO` 模式下也有效。

示例实体 `Customer`（Java 类）的 `name` 上有 `@Sortable`，`grade` 上有 `@Sortable("level")`。示例在注册了只包含 `Order` 规则的 `STRICT` 策略的状态下执行。

<!-- region: ManualKotlinSortTest.kt#annotation -->
```kotlin
// Customer has no rules in the policy, so its @Sortable fields are the rules:
//   @Sortable private String name;   @Sortable("level") private Integer grade;
val byLevel = qb.from(Customer::class.java).orderBy(SortSpec.ofPath(Customer::class.java, "level", Direction.DESC))
assertEquals("select e0 from Customer e0 order by e0.grade desc nulls last", byLevel.toHql().hql())
assertThrows(SortNotAllowedException::class.java) {
    qb.from(Customer::class.java).orderBy(SortSpec.ofPath(Customer::class.java, "id", Direction.ASC))
}
```

<a id="rules-as-strings"></a>
**字符串形式的规则。** `allowPath(String...)` 和 `aliasPath(name, path)` 以字符串接收属性路径。它们用于配置文件，[12.5](12-spring-data-jpa.md#sort-policy-with-spring) 中的 Spring Boot 属性就使用这种形式。
路径在第一次使用规则时与实体核对。在代码中请使用 Lambda 表达式形式 `allow` 和 `alias`。

<!-- region: ManualKotlinSortTest.kt#string-paths -->
```kotlin
// the string forms used by configuration files (lambdaql.sort.allow / lambdaql.sort.alias)
SortPolicy.register(emf, SortPolicy.of(SortMode.STRICT,
    SortRules.of(Order::class.java).allowPath("price", "customer.name").aliasPath("newest", "updateAt")))
assertEquals("select e0 from Order e0 order by e0.updateAt desc nulls last",
    qb.from(Order::class.java).orderBy(SortSpec.ofPath(Order::class.java, "newest", Direction.DESC)).toHql().hql())
// in STRICT mode a name that is not in the rules is rejected, even when it is not a property
assertThrows(SortNotAllowedException::class.java) {
    qb.from(Order::class.java).orderBy(SortSpec.ofPath(Order::class.java, "noSuchName", Direction.ASC))
}
```

- 检查发生在把 spec 传给 `orderBy` 时，在读取路径之前。
  因此在 `STRICT` 模式下，不在规则中的名称即使不是属性，也会抛出 `SortNotAllowedException`。
- 规则属于一个实体类。为同一个类传入两次 `SortRules` 时，使用最后一个。
- `SortNotAllowedException` 是 `LambdaQLException`（见 [11. 错误与语言](../11-errors-and-languages.md#exceptions)）。
  lambdaQL 没有 Web 依赖。HTTP 400 响应的示例见 [12.5](12-spring-data-jpa.md#sort-policy-with-spring)。
- 从请求中接收名称和方向时，请用 `Direction.parse`（见 [4.3](#43-direction)）读取方向，用 `SortSpec.ofPath` 读取名称。

示例代码：[`ManualKotlinSortTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSortTest.kt)（`sort-spec`、`sort-spec-then`、`sort-spec-reversed`、`sort-spec-path`、`policy`、`annotation`、`string-paths`）。

<a id="411-sorting-inside-subqueries"></a>
## 4.11 子查询内部的排序

子查询内部的排序只有与 `limit` 一起使用时（top-1 或 top-N）才会保留。
没有 `limit` 时排序不会改变结果，因此 lambdaQL 会移除排序并记录一条警告日志。
见 [5. 子查询](05-subquery.md#59-sorting-inside-a-subquery)。

<a id="412-rules-summary-s1-to-s14"></a>
## 4.12 规则总结（S1 到 S14）

| # | 规则 | 状态 |
|---|---|---|
| S1 | 排序是一个 Lambda 表达式：一个排序键（`{ it.price }`，可选 `Direction`），或用 `By` 的函数创建的排序键（`{ o -> desc(...) }`）。join 时为 `{ o, c -> ... }` | 已实现。join 写法 `{ o, c -> }` 也已实现 |
| S2 | 排序键必须使用实体的值（允许 Kotlin 可空排序键）。实体本身不能作为排序键 | 已实现 |
| S3 | 方向由 `asc` / `desc` 或 `sort(key, Direction)` 决定。`asc(key)`/`desc(key)`（`ASC`/`DESC`）使用 NULL 策略，`asc(key, nulls)`、`desc(key, nulls)`、`*_NULLS_*` 按写法使用 | 已实现 |
| S4 | 主体只能是一个排序键或一条直接的 `By` 函数调用链。不能有选择排序函数的 `if`、`when` 或循环。排序键值中的 `if` 会变成 `case when` | 已实现 |
| S5 | 每个查询只调用一次 `orderBy`。`orderBy` 之后不能调用 `where`。`limit` 之后不能调用 `orderBy` | 已实现 |
| S6 | 用 `SortSpec`（`of`、`ofPath`、`then`、`reversed`、`orderBy(spec)`）重用。外部字符串由 persistence unit 的排序策略（`SortRules`、`SortPolicy`、`SortMode`，见 [4.10](#sort-policy)）检查 | 已实现 |
| S7 | 排序键：列、对一路径、`+ - *`、拓宽转换。排序键值中的 `if` 会变成 `case when`。Kotlin 的 `?.` 被当作路径读取 | 已实现 |
| S8 | 只在 `orderBy` 中使用的关联路径使用 `left join`，因此没有关联的行也会保留。必需关联（`optional = false`）不会丢失行，因此使用 inner join（A4）。`where` 使用同一条路径时，排序使用 `where` 的 join | 已实现 |
| S9 | 同时使用 `orderBy` 和分页且排序键中没有 id 时，添加根实体的 `@Id asc`。没有 `orderBy` 时不加 `order by`。显式 join 的实体的 id 也会添加。排序键中有唯一且不为 NULL 的列（`unique = true` 且 `nullable = false`、`@NaturalId`）时不添加（A5） | 已实现 |
| S10 | 分析结果按 Lambda 表达式缓存。捕获的 `Direction` 每次调用时读取 | 已实现 |
| S11 | Spring `Sort.Order` 与 `Direction` 一一对应（Spring 模块，见 [12.5](12-spring-data-jpa.md#125-spring-sort-and-pageable)）。属性名经过排序策略检查，别名按原样传递 | 已实现（不支持 `ignoreCase`）。与排序策略一起使用也可以 |
| S12 | join 查询也使用相同的形式：从 `{ o, c -> }` 到 10 个实体 | 已实现（见 [6.8](06-select-group.md#68-explicit-joins)） |
| S14 | 子查询的排序：有 `limit` 时保留，没有时移除并发出警告（见第 5 章） | 已实现 |

---

[Kotlin 路线](README.md) | 上一章：[3. 条件（`where`）](03-where.md) | 下一章：[5. 子查询](05-subquery.md) | [本页的 Java 版本](../java/04-order-paging.md)
