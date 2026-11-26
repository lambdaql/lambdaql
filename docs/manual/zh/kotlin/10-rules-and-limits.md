<a id="10-rules-and-limits-kotlin"></a>
# 10. 规则与限制（Kotlin）

[Kotlin 路线](README.md) | [手册首页](../README.md) | 上一章：[9. Kotlin 说明](09-kotlin-notes.md) | 下一章：[11. 错误与语言](../11-errors-and-languages.md) | [本页的 Java 版本](../java/10-rules-and-limits.md)

公共页面：[1. 概述](../01-overview.md) · [11. 错误与语言](../11-errors-and-languages.md)

<a id="contents"></a>
## 目录

- [10.1 基本规则：Lambda 表达式就是查询](#101-the-base-rule-a-lambda-is-a-query)
- [10.2 SARGable 规则（便于使用索引的 SQL）](#102-sargable-rule-index-friendly-sql)
- [10.3 NULL 规则](#103-null-rules)
- [10.4 运算符规则（R1 到 R24）](#104-operator-rules-r1-to-r24)
- [10.5 Kotlin 与 SQL 的差异](#105-kotlin-and-sql-differences)
- [10.6 数据库之间的差异](#106-database-differences)
- [10.7 不支持的写法及替代写法](#107-not-supported-and-what-to-write-instead)
- [10.8 缓存：每个 Lambda 表达式只分析一次](#108-caching-a-lambda-is-analyzed-once)
- [10.9 支持的环境](#109-tested-environment)

如何阅读示例：

- 示例中 `assertEquals` 的字符串是 lambdaQL 生成的 HQL。
- 直接写在 Lambda 表达式中的常量（数字、字符串、布尔值、`null`、enum 常量）作为字面量写入 HQL。
  捕获的值和计算出的值始终是绑定参数（`:p1`、`:p2`、...）。
- 实体：`Order`（`id`、`price`、`status`、`active`、`description`、`customer`、`updateAt`、`items`）、
  `Customer`（`id`、`name`、`grade`）、`Item`（`order`、`price`、`qty`）。请参见 [2. 入门](02-getting-started.md#example-entities)。
  10.3 中的行还使用 `Order` 的属性 `cnt`（`Integer`）、`custName`（`String`）、`address`（`@Embedded`，`city` 和 `zip`）。
  可能为 NULL 的 Boolean 示例使用示例实体 `Coded` 的 `vip`。示例实体是 Java 类，因此布尔属性 `active` 在 Kotlin 中用 `it.isActive` 读取。

---

<a id="101-the-base-rule-a-lambda-is-a-query"></a>
## 10.1 基本规则：Lambda 表达式就是查询

lambdaQL 不调用 Lambda 表达式，而是分析字节码 opcode 来生成 HQL。
Lambda 表达式是用 Kotlin 语法编写的查询。因此：

1. 像 `it.customer?.name` 这样的路径**指代一个列**，而不是调用链。
   lambdaQL 不检查客户是否为 null。在 `where` 中，如果没有客户的订单可能使条件为真，就是 `left join`，否则是内连接。
   因此 `it.status == 1 || it.customer?.name == "Kim"`、`it.customer?.name == null` 和 null 保护会像 Kotlin 一样保留没有客户的订单。
   用 `!!` 或不加标记经过的比较只对有客户的订单为真。`?.` 和 `!!` 可以混用。
   只查找没有客户的订单时，请比较关联本身：`it.customer == null`。
2. NULL **只在你自己写的地方**被处理（`== null`、`!= null`、`?.`、`?:`）。
   Kotlin 编译器添加的 null 检查会被忽略。其他 NULL 遵循 SQL 规则：被比较的值为 NULL 的行不会被返回。
3. `!!` 不会抛出异常（关联步骤上的 `!!` 见第 1 条）。Kotlin 的内存规则（对象同一性、装箱）不适用。`==` 和 `===` 表示**值相等**。
4. **无法分析的 Lambda 表达式是错误。** lambdaQL 无法把它分析为查询时，会抛出带有代码 `[LQL-xxxx]` 和源代码行号的异常。

<a id="102-sargable-rule-index-friendly-sql"></a>
## 10.2 SARGable 规则（便于使用索引的 SQL）

“SARGable”表示数据库可以对条件使用索引。
这是 lambdaQL 的基本规则（设计第 3.9 节）。

| # | 规则 |
|---|---|
| 1 | **列这一侧不加函数、运算符或 `CASE`。** 如果需要改变值，就在绑定前由应用程序改变参数（例如用 `Locale.ROOT` 转为小写） |
| 2 | 如果有两种含义相同的写法，选择只使用简单比较和 `AND` / `OR` / `NOT` 的写法。正确的三值逻辑优先，在此前提下选择 SARGable 的写法 |
| 3 | 每条渲染规则都在 H2、MariaDB 和 Oracle 上用 `EXPLAIN` 确认 |

<a id="examples"></a>
### 示例

**用普通的 `=` 和 `<>` 进行相等比较**

相同的示例也在 [9.4](09-kotlin-notes.md#plain--and-) 中。

<!-- region: ManualKotlinNotesTest.kt#equality -->
```kotlin
// plain = and <>: a row whose description is NULL matches neither
val same = qb.from(Order::class.java).where { it.description == text }
assertEquals("select e0 from Order e0 where e0.description = :p1", same.toHql().hql())
val other = qb.from(Order::class.java).where { it.description != "x" }
assertEquals("select e0 from Order e0 where e0.description <> 'x'", other.toHql().hql())
// NULL rows too: write the null check yourself
val orNull = qb.from(Order::class.java).where { it.description == null || it.description != "x" }
assertEquals("select e0 from Order e0 where e0.description is null or e0.description <> 'x'", orNull.toHql().hql())
```

lambdaQL 不写 `is [not] distinct from`。这种形式在 Oracle 上会变成 `decode(...)`，无法使用索引。
实体值遵循 SQL 语义，因此普通的 `=` 或 `<>` 总是正确的。捕获值 `null` 作为 NULL 绑定，不匹配任何行（`WARN` 级别 `LQL-4045`）。

**Elvis**

<!-- region: ManualKotlinNotesTest.kt#elvis-value -->
```kotlin
// ?: in a select value: coalesce
val text = qb.from(Order::class.java).select { it.description ?: "" }
assertEquals("select coalesce(e0.description, '') from Order e0", text.toHql().hql())
// ?: after ?. in a select value is rejected
val error = assertThrows(LambdaQLException::class.java) {
    qb.from(Order::class.java).select { it.customer?.name ?: "none" }
}
assertEquals("LQL-2022", error.code())
// ?: with a number: coalesce too
val number = qb.from(Order::class.java).where { (it.total ?: 0.0) > 3.0 }
assertEquals("select e0 from Order e0 where coalesce(e0.total, 0.0) > 3.0", number.toHql().hql())
// in a string condition, ?: is coalesce too
val x = qb.from(Order::class.java).where { (it.description ?: "") == "x" }
assertEquals("select e0 from Order e0 where coalesce(e0.description, '') = 'x'", x.toHql().hql())
```

字符串条件中的 `?:` 变为 `coalesce(e0.description, '') = 'x'`。列外面包了函数，因此不会使用 `description` 上的普通索引。
需要索引时，请像 `it.description == "x"` 这样直接比较。与 `"x"` 相等的行不是 NULL，因此结果相同。

**带子查询的 NOT IN** 写成 `NOT IN`（SQL 语义）。子查询中有 NULL 时不返回任何行。要跳过 NULL，请在子查询中加上 null 检查（参见 [10.3](#103-null-rules)）。

<a id="what-the-measurements-showed"></a>
### 测量结果

MariaDB 10.11 和 Oracle 23ai，20,000 到 50,000 行：

| 写法 | MariaDB | Oracle | 可以吗？ |
|---|---|---|---|
| `_ci` 列上的 `name = 'abc'` | 索引查找 | – | 可以 |
| `LOWER(name) = LOWER('abc')` | 全索引扫描（19,791 行） | 全表扫描（没有函数索引） | **不可以** |
| `lower(name) = :p_lowered` + 函数索引 | – | 索引范围扫描（`LIKE 'ab%'` 也一样） | 可以 |
| `name COLLATE BINARY_CI = :p` + 语言索引 | – | 索引范围扫描 | 可以 |
| 用 `AND`/`OR` 写的布尔 `==` | 范围（350 行） | 2 次索引范围扫描 + 位图 OR | 可以 |
| 用 `CASE` 写的布尔 `==` | 全索引扫描（49,648 行） | 全表扫描 | **不可以** |

<a id="db-functions-on-a-column"></a>
### 列上的数据库函数

列这一侧的数据库函数（`OrderFns.discount(it.price)!! > 10`）无法使用普通索引。
lambdaQL 会记录 **WARN**（`LQL-5001`）。例外：在 Oracle 上存在相同表达式的基于函数的索引时不记录。
在 MariaDB 上，`NOT DETERMINISTIC`（默认）函数即使参数只有参数值也会导致全扫描（`it.price!! > OrderFns.discount(rate)!!`），因此这种情况也会发出警告（`LQL-5007`）。
在 `SargableMode.STRICT` 下，分析会失败而不是发出警告（`LQL-3066`）。

**SARGable 模式**。`SargableMode` 有三个值。

- `OFF`：不检查，也不查询元数据。
- `WARN`（默认）：记录警告。
- `STRICT`：在 `WARN` 模式下会记录警告的情况（`LQL-5001`、`LQL-5007`、`LQL-5012`）中，分析以 `org.lambdaql.NonSargableException`（`LQL-3066`）失败。

按 persistence unit 用 `SargablePolicy.register(emf, SargableMode.STRICT)` 指定，用 `SargablePolicy.unregister(emf)` 解除。在 Spring 中使用 `lambdaql.sargable.mode=STRICT`（参见 [12](12-spring-data-jpa.md)）。
检查对每个 Lambda 表达式只执行一次，在把分析结果放入共享缓存时进行。因此请在第一个查询之前注册模式。

**列上的算术**。

- 只由一个列和值组成的 `+ - * /`（`it.cnt * 2 > n`）可以移到值一侧。lambdaQL 以 `WARN` 级别记录 `LQL-5012`，在 `STRICT` 下为错误。
- 列与列之间的算术（`it.price!! * it.cnt > n`）和 `%` 无法移动，因此只以 `DEBUG` 级别记录 `LQL-5013`。在 `STRICT` 下也允许。
- 移动后的写法必须含义相同。请像 `it.cnt > n / 2.0` 这样用小数相除。整数除法 `n / 2` 在 `>=`、`=`、`<` 以及 `n` 为负数时会得到不同的行。乘以负数时请反转比较方向。`LQL-5012` 的消息会给出这些提示。
- 没有采用通过查询索引是否存在来减少警告的方式。因为权限和同义词会使“没有索引”与“未知”混在一起，而且列上的算术是少见的写法。
请参见 [8. 存储过程与数据库函数](08-procedures-functions.md)。

示例代码：[`ManualKotlinNotesTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinNotesTest.kt)（`equality`、`elvis-value`）。

<a id="103-null-rules"></a>
## 10.3 NULL 规则

| Lambda 表达式 | SQL | NULL 行为 |
|---|---|---|
| `it.cnt == 1`（`Integer` 与常量） | `e0.cnt = 1` | NULL：不返回该行。不读取编译器插入的 null 检查 |
| `it.cnt != 1` | `e0.cnt <> 1` | NULL：不返回该行（SQL 语义）。需要时自己写 `it.cnt == null \|\|` |
| `it.price!! > p` | `e0.price > :p1` | NULL：不返回该行。`!!` 被忽略 |
| `it.status == q`（两侧都可空：`q: Int?`、`String?`、enum） | `e0.status = :p1` | 列为 NULL：不返回。捕获值 `null`：没有匹配的行（`= NULL`，`WARN` 级别 `LQL-4045`） |
| `it.status != q`（两侧都可空） | `e0.status <> :p1` | 列为 NULL：**不**返回（SQL 语义。Kotlin 中会返回）。捕获值 `null`：没有匹配的行 |
| `it.description == it.custName`（两个实体值） | `e0.description = e0.custName` | 任一侧为 NULL：不返回。NULL 与 NULL 也一样（SQL 语义） |
| `it.description.equals(text)`（`String?`） | 与 `==` 相同 | 与 `==` 相同 |
| `x == null`、`x != null` | `is null`、`is not null` | 二值 |
| `it.cnt >= 2.5`（整数列，小数值） | 按写出的值比较：常量是字面量（`e0.cnt >= 2.5`），捕获值和 IN 列表也是小数字面量（`e0.cnt >= 2.5BD`、`in (2.5BD, 3.0BD)`） | 保持索引范围扫描（在 MariaDB 上实测）。不会像以前那样截断值而返回错误的行 |
| 整个值对象（`@Embedded`）：`it.address == addr`、`it.address != addr` | 按列展开：`e0.address.city = :p1 and e0.address.zip = :p2`，否定为 `<> ... or ...`。`== null` 是所有列 `is null`（and），`!= null` 是 `is not null`（or） | 每列按 SQL 语义：NULL 列对 `=` 和 `<>` 都不匹配。捕获的对象为 `null` 时没有匹配的行（`WARN` 级别 `LQL-4045`） |
| `it.id == id`（`it.id` 是 `Long?`，`id` 是捕获的 `Long`）、`it.id == null`：不可能为 NULL 的列（id、基本类型、`nullable = false`、路径中没有关联步骤） | 用 `e0.id = :p1` 代替 `not (e0.id is null or e0.id <> :p1)`。`it.id == null` 是 `1=0` | 列中没有 NULL。经过关联的路径（`it.customer?.id`）保留检查 |
| `!`、`&&`、`\|\|` | `NOT`、`AND`、`OR` | SQL 三值逻辑 |
| `it.vip ?: false`（条件） | `e0.vip is not null and e0.vip = true` | NULL 视为 false |
| `where` 中的 `it.customer!!.name`（或不加标记的路径） | `j1.name`。`it.customer!!.name == "Kim"`、`it.customer!!.name == null` 是 `join e0.customer j1` | 没有客户：比较不为真。`it.status == 1 \|\| it.customer!!.name == "Kim"` 是 `left join`，status 为 1 的订单会被返回 |
| `where` 中的 `it.customer?.name` | `j1.name`。`it.customer?.name == "Kim"` 是 `join`，`it.customer?.name == null` 是 `left join e0.customer j1` | `it.customer?.name == "Kim"`：不返回（`j1.name` 为 NULL）。`it.status == 1 \|\| it.customer?.name == "Kim"`、`it.customer?.name == null`：返回 |
| `it.customer == null \|\| it.customer?.name == p` | `e0.customer is null or j1.name = :p1` + `left join e0.customer j1` | 没有客户：返回。用 `!!` 写也一样 |
| 混用 `?.` 和 `!!`：`it.customer?.name == null \|\| it.customer!!.grade == null` | `j1.name is null or (e0.customer is not null and j1.grade is null)` + `left join e0.customer j1` | 没有客户：通过 `?.` 一侧的比较返回。没有警告 |
| 同一路径只用在 `orderBy` 或 `select` 中 | `j1.name` + `left join e0.customer j1`。必需关联（`optional = false`）为 `join` | 没有客户：列为 NULL |
| `it.customer == null`、`it.customer != null` | `e0.customer is null`、`e0.customer is not null`（外键列，不需要 join） | 没有客户的订单 / 有客户的订单 |
| `it.customer?.id` | `e0.customer.id`（外键列，不需要 join） | 没有客户：NULL |
| `a ?: b` | 在 select 值、条件、`set` 值中为 `coalesce(a, b)`。Boolean 条件中的 `?: false` 理解为 null 检查 | 参见 [9.5](09-kotlin-notes.md#95-null-operators---and-) |
| `it.id in ids` | `e0.id in (:p1)` | NULL id：不返回 |
| `it.id !in ids` | `e0.id not in (:p1)`。可能为 NULL 的列也一样 | NULL 值：**不**返回（SQL 语义。`DEBUG` 级别 `LQL-5009`。Kotlin 中会返回） |
| `it.cnt in 1..5`、`it.cnt !in 1..5` | `1 <= e0.cnt and e0.cnt < 6`、`1 > e0.cnt or e0.cnt >= 6` | 主体为 NULL：不返回。`!in` 也一样（没有保护） |
| `x !in sub...` 形式的子查询 | `x not in (select ...)` | 子查询中有 NULL 时不返回任何行（SQL 语义） |
| `Agg.sum`、`avg`、`min`、`max` | `sum(...)` ... | 没有行或所有值都为 NULL 时为 NULL |
| `Agg.count()` | `count(*)` | 没有行时为 0 |

<a id="captured-null-values"></a>
### 捕获的 null 值

| 捕获值所在的位置 | 允许 `null` 吗？ |
|---|---|
| 两个可空值之间的 `==` / `!=` | 允许。但没有匹配的行（`= NULL`）。执行查询时以 `WARN` 级别记录 `LQL-4045` |
| 在同一个值的 null 保护之下：`p == null \|\| it.status!! > p` | 允许。保护在执行查询时由应用程序决定。`p == null` 时没有过滤，其他值时为 `e0.status > :p1`。只有 `!!` 和自身基本类型拆箱的比较与保护的位置无关 |
| 与 `!!` 一起使用或用于大小比较（`<` `<=` `>` `>=`）：`it.price!! > p!!` | 允许。但没有匹配的行（`> NULL`）。`!!` 和自身基本类型拆箱不会执行。执行查询时以 `DEBUG` 级别记录 `LQL-4045` |
| 对捕获值调用方法（`p!!.trim()`） | **不允许。** 计算值时报错（`LQL-3027`）。参见 [9.8](09-kotlin-notes.md#98-captured-values) |
| 用于 `in` 的集合中的 `null` 元素 | **不允许。** 绑定时报错（`LQL-4008`） |

<a id="104-operator-rules-r1-to-r24"></a>
## 10.4 运算符规则（R1 到 R24）

这是把设计（第 3.6 节和 KOTLIN.md 第 5 节）中的规则表按 Kotlin 写法简要整理的结果。

| # | Lambda 表达式写法 | 含义 |
|---|---|---|
| R1 | 非空基本类型的比较，或 `!!` 之后的比较（`it.qty > 5`、`it.price!! > p`） | `=`、`<>`、`<`、... SQL 的 NULL 规则 |
| R2 | 编译为引用比较的 `==` / `!=`（enum `==`；`===` 参见 R17） | 值相等，不是对象同一性。按 SQL 语义的 `=` / `<>` |
| R3 | `x == null`、`x != null` | `IS NULL`、`IS NOT NULL` |
| R6 | `!`、`&&`、`\|\|` | `NOT`、`AND`、`OR`。SQL 三值逻辑 |
| R7 | 字符串值比较 | 遵循**数据库的排序规则**（大小写、末尾空格）。Oracle 把 `''` 视为 NULL |
| R8 | 实体 `==` 实体 | 不支持（KC13，`LQL-3011`） |
| R9 | 捕获值 | 以执行时的值作为绑定参数 |
| R10 | 属性访问（`it.price`） | getter 调用，映射到 JPA 属性 |
| R11 | 编译为 `Intrinsics.areEqual` 的 Kotlin `==`、`!=`（引用，或两侧都可空） | 值相等：按 SQL 语义的 `=` / `<>`。捕获值 `null` 不匹配任何行 |
| R12 | 可空基本类型与基本类型（`it.cnt == 1`，`cnt: Integer`） | 不读取编译器插入的 null 检查：`e0.cnt = 1`、`e0.cnt <> 1` |
| R13 | `!!` | 忽略 |
| R14 | `?.` | NULL 会传播的路径或值。与不带 `?.` 时是同一个列 |
| R15 | `?:` | 值中为 `coalesce(a, b)`。条件中的布尔 elvis 变为 `(x is not null and x = true)`。作为值的布尔 elvis 会被拒绝（KC21） |
| R16 | 关联 join | 已被 R24 取代 |
| R17 | `===`、`!==` | 与 `==` 一样是值相等 |
| R18 | 区间 `in a..b`、`until`、`..<`、`!in` | `a <= x and x < b + 1`（常量）、`:p1 <= x and x <= :p2`（捕获值）、`a > x or x >= b + 1`（`!in`）。不会自动添加 null 保护 |
| R19 | 集合 `x in c`、`x !in c`（捕获的 `c`） | `x in (:p)`、`x not in (:p)`（SQL 语义）。null 元素：报错。空集合：FALSE / TRUE |
| R20 | 分支以 `true`/`false` 结尾、没有主体的 `when { }` | 条件的 `OR`。带主体的 `when (subject)` 会被拒绝（`LQL-2010`） |
| R21 | Kotlin 字符串函数 | `startsWith`、`endsWith`、`contains`（包括 `ignoreCase`）为 `like`。`equals(ignoreCase)`、`lowercase`、`length`、`isNullOrEmpty` 会被拒绝（`LQL-3008`）。`equals(text)` 与 `==` 相同 |
| R22 | Kotlin 宿主值（捕获的 `var`、private 属性、`companion` 中的值） | 每次执行时读取。写入会被拒绝 |
| R23 | 属性引用 `Order::price` | 在条件中会被拒绝（`LQL-1009`）。请使用 Lambda 表达式 `{ it.price }` |
| R24 | 关联路径（`it.customer?.name`、`it.customer!!.name`、Java 实体的 `it.customer.name`） | 一个列。对一关联：where、order by、select 共用的别名。在 `where` 中，如果没有关联的行可能使条件为真，就是 `left join`，否则是内连接（用 `!!` 或不加标记经过的比较只对有关联的行为真，`?.` 和 `!!` 可以混用）；只在 order by 或 select 中使用时是 `left join`（必需关联 `optional = false` 为内连接，A4）。嵌入类型：同一张表。以目标的 `@Id` 结尾，或把关联与 `null` 比较：外键列，不需要 join。集合：报错 |

Java 专用的规则 R4（实体值上的 `equals`）和 R5（Java 的 null 安全辅助方法）在 [Java 路线](../java/10-rules-and-limits.md)中。在 Kotlin 中，`==` 与 R5 含义相同。

其他规则：

- `x.compareTo(y) > 0` 变为 `x > y`。`isBefore`、`isAfter` 会被拒绝（`LQL-3008`）。
- 直接写在 Lambda 表达式中的常量（包括 `const val`）是字面量：`it.price > 100.0` 是 `e0.price > 100.0`，`"O'Brien"` 是 `'O''Brien'`。捕获值和计算出的值（`BigDecimal("1.5")`）是绑定参数。例外：与整数列比较的小数捕获值是小数字面量（`2.5BD`）。
- 编译器有时会移动 `!` 的位置（德摩根定律、双重否定）。结果不会因此改变。
  lambdaQL 把每个条件恰好保留一次，不会“简化”你写的逻辑。
- 即使写了在 Kotlin 中恒为真的条件（`x > 5 || !(x > 5)`），也不会简化。
  在 SQL 中，`x` 为 NULL 的行会被排除。这是 SQL 的规则。

<a id="105-kotlin-and-sql-differences"></a>
## 10.5 Kotlin 与 SQL 的差异

这些差异来自 SQL 本身，不是编译器的问题。
“G” 编号是设计（第 2.7 节）中的 ID。

| # | 差异 | lambdaQL 的处理 |
|---|---|---|
| G1 | 在 Kotlin 中 `null != "x"` 为 true，但 SQL 的 `description <> 'x'` 不返回 description 为 NULL 的行 | 编译为 `Intrinsics.areEqual` 的 `!=` 渲染为 `<>`：SQL 语义。NULL 行被排除，并以 `DEBUG` 级别记录 `LQL-5009`。要保留这些行，请写 `it.description == null \|\| ...`。可空数字与常量的 `!=`（`it.cnt != 1`）也会变为 `e0.cnt <> 1`，因此不包含 NULL 行 |
| G3 | 用 NULL 参数执行 `name = ?` 返回 0 行 | 保持原样（SQL 语义）。执行时以 `WARN` 级别记录 `LQL-4045`。要查找 NULL，请写 `x == null` |
| G4 | `status NOT IN (1, NULL)` 总是返回 0 行 | 捕获集合中的 `null` 会报错。子查询的 `!in` 保持 SQL 语义：请在子查询中加上 null 检查 |
| G5 | 实体的列值为 NULL 的情况（包括 `!!`、拆箱） | 已写入文档：该行按 SQL 规则不返回 |
| G6 | `NaN`：H2 把 NaN 排为最大值且 `NaN = NaN` 为 true；MariaDB 无法存储它；Oracle 只能在 `BINARY_DOUBLE` 中存储 | 已写入文档：包含 NaN 的比较遵循数据库 |
| G7 | `Float` 列与 `Double` 值比较：结果因数据库而异 | 对于 `Float`/`Double`，**不保证与 Kotlin 的结果逐位一致**。lambdaQL 不添加 `CAST`。请自行选择列映射（例如 Oracle 的 `binary_float` / `binary_double`） |
| G9 | `x > 5 \|\| !(x > 5)` 会丢掉 NULL 行 | 不简化，按写出的原样渲染 |
| G10 | SQL 不保证求值顺序：`qty != 0 && total / qty > 2` 仍可能除以零 | 已写入文档：保护条件之下的除法也按原样写出。请不用除法进行比较（`it.qty > 0 && it.price > 2.0 * it.qty`） |
| G11 | 字符串：MariaDB `_ci` 忽略大小写和末尾空格；Oracle 把 `''` 存储为 NULL；Oracle 的 `NLS_SORT` 跟随 JVM 的区域设置 | 已写入文档：字符串比较和排序遵循数据库。在 Oracle 上请设置 `NLS_SORT=BINARY`（会话初始化或 `-Duser.language=en`）。在 Oracle 上 `it.description == ""` 和 `isEmpty()` 的行为不同 |
| G12 | Kotlin 的 `===` 是同一性（两个值为 1000 的 `Long` 装箱对象为 false） | lambdaQL 把 `===` 和 `==` 定义为值相等 |
| G13 | 存有 0/1 以外值的遗留布尔列（MariaDB `TINYINT(1)`、Oracle `NUMBER(1)`）读取结果不同 | Hibernate 自己的 DDL（`bit(1)`、Oracle 23 的 `boolean`）没有问题。使用遗留 schema 时请注意 |
| G14 | Oracle 23 的 bug：对复合条件使用 `WHERE (a AND b) = false` / `IS FALSE` / `IS NULL` 会丢失行 | lambdaQL 不把复合条件包在布尔测试中。否定总是写成 `NOT(...)` |
| G15 | Oracle 的 `CHAR` 列与 `String` 绑定值不匹配 | 已写入文档：请注意实体映射 |

其他差异：

| 差异 | Kotlin | SQL | lambdaQL |
|---|---|---|---|
| `BigDecimal` 相等 | `==` 还比较标度（`1.0` 与 `1.00` 为 false） | `=` 比较值 | 值相等 |
| 整数除法 | `5 / 2` 是 `2` | MariaDB 是 `2.5` | 取决于数据库 |
| `BigDecimal` 除法和函数结果 | Kotlin 的标度规则（`HALF_EVEN`） | 标度由数据库决定（例如对函数结果，H2 为 1，MariaDB 和 Oracle 为 0） | 已写入文档 |
| Unicode 忽略大小写（`ſ`、`İ`） | Kotlin 规则 | 数据库排序规则 | 遵循数据库 |
| 字符串长度 | UTF-16 单元 | H2 为 UTF-16 单元，MariaDB 为码点 | 已写入文档 |
| 包含 NULL 的字符串模板 | 文本 `"null"` | NULL（Oracle：`NULL \|\| 'x'` 是 `'x'`） | 字符串拼接会被拒绝（`LQL-2007`） |
| 可空的区间主体 | false。`!in` 为 true | 不返回。`!in` 也一样 | SQL 语义 |
| 非空类型 `String` | 不为 null | 列可能为 NULL | lambdaQL 使用 JPA 元数据 |
| Lambda 表达式中的 `LocalDateTime.now()` | 应用程序时间 | – | 以**应用程序时间**绑定。不支持数据库时间（`current_timestamp`） |
| `Set` 中的相同元素 | 由 `equals` / `hashCode` 决定（`data class` 为主构造器的属性） | 用主键区分行 | Set 的大小小于行数时，`toSet()` 和 `findSet()` 抛出 `IllegalStateException`（实体的 `equals` / `hashCode` 把不同的行判断为相等，或 `@Id` 不唯一，例如视图）。请使用 `toList()` |

<a id="106-database-differences"></a>
## 10.6 数据库之间的差异

lambdaQL 隐藏了许多差异。有些需要了解。

<a id="sorting-and-paging"></a>
### 排序与分页

| 项目 | H2 | MariaDB | Oracle | lambdaQL |
|---|---|---|---|---|
| NULL 位置，`ASC` | 最前 | 最前 | **最后** | 所有数据库中 `ASC` 都是 NULLS FIRST |
| NULL 位置，`DESC` | 最后 | 最后 | **最前** | 所有数据库中 `DESC` 都是 NULLS LAST |
| `NULLS FIRST/LAST` 语法 | 有 | **没有**（语法错误） | 有 | 在 MariaDB 上 Hibernate 用 `CASE` 模拟：排序**无法使用索引** |
| 分页 SQL | `offset ? rows fetch first ? rows only` | `limit ?,?` | `offset ? rows fetch first ? rows only` | 交给 Hibernate（`setFirstResult` / `setMaxResults`） |
| 值相同的行的顺序 | – | – | 随机 | 分页时如果键中没有 id，lambdaQL 会添加根的 `@Id asc`。存在唯一且不可能为 NULL 的键（`unique = true` 且 `nullable = false`、`@NaturalId`）时不添加 |
| 字符串顺序 | 区分大小写 | `_ci` | `NLS_SORT`（JVM 区域设置） | 遵循数据库 |
| `order by` 中的隐式路径 | 内连接（丢失行） | 相同 | 相同 | lambdaQL 使用 `left join`（必需关联为内连接，或使用同一路径在 `where` 中的 join） |
| `order by`、`distinct`、`group by` 中的 `@Lob` 列 | 可用 | 可用 | **ORA-22848 / ORA-22849** | 不改变查询，以 `DEBUG` 级别记录 `LQL-5010` |
| `@Enumerated(EnumType.STRING)` enum 列的排序 | 按名称 | 按名称 | 按名称 | 不改变查询，以 `DEBUG` 级别记录 `LQL-5011`。声明顺序只在 `ORDINAL` 映射中 |

lambdaQL 对所有可能为 NULL 的键在 HQL 中写出 `nulls first` / `nulls last`。只经过必需关联且不可能为 NULL 的路径不写。与数据库默认值相同时，Hibernate 会去掉它。
默认值（`ASC` = NULLS FIRST，`DESC` = NULLS LAST）与 H2、MariaDB 相同，因此在这些数据库上不需要额外的
SQL。只有 Oracle 会加上 `NULLS` 子句。要改变 NULL 的位置，请使用
`asc(key, Nulls.LAST)`（NULL 在最后）或 `desc(key, Nulls.FIRST)`（NULL 在最前）。在 MariaDB 上，它们使用 `CASE` 模拟。

<a id="conditions-and-values"></a>
### 条件与值

| 项目 | H2 | MariaDB | Oracle |
|---|---|---|---|
| `is not distinct from` | 原生支持 | `<=>`（使用索引） | `decode(...)`（无法使用索引）。lambdaQL 不写这种形式 |
| `is [not] true`、`coalesce(boolean, false)` | 可用 | 可用 | **ORA-00932**。lambdaQL 不写 |
| 空字符串 `''` | 值 | 值 | **NULL** |
| 大小写和末尾空格 | 精确比较 | `_ci`：忽略 | 精确比较（排序使用 `NLS_SORT`） |
| `NaN` | 可存储，最大值 | 无法存储 | 只能在 `BINARY_DOUBLE` 中 |
| `Float` 列 | REAL 字面量规则 | 扩展为 double | Hibernate 把 `Float` 映射为 `float(24)`（十进制） |
| 整数 `/` | 整数 | **小数**（`5/2 = 2.5`） | – |
| 很大的 `in` 列表（1,500 个值） | 可用 | 可用 | 在 23ai 上可用（未测量旧版 Oracle 的 1,000 个限制） |

<a id="subqueries"></a>
### 子查询

| 项目 | 会发生什么 | lambdaQL |
|---|---|---|
| 没有 `limit` 的子查询 `order by` | Oracle：语法错误（ORA-00907）。MariaDB：被忽略 | 移除并记录 WARN |
| MariaDB `IN (... limit n)` | 不支持（错误 1235） | 自动包装：`in (select x.id from (... limit n) x)` |

<a id="update-insert-delete"></a>
### 更新、插入、删除

| 项目 | 会发生什么 | lambdaQL |
|---|---|---|
| `SET a = ..., b = a + 1`（设置之后读取该列） | MariaDB 读取**新**值，H2 和 Oracle 读取旧值 | 分析时报错 |
| DML 中的关联路径（`it.customer?.name`） | Hibernate 在各数据库上生成不同的 join，并**漏掉**没有客户的行 | lambdaQL 写成关联标量子查询（`where` 和 SET）。在 `where` 中，对于查询时会内连接的路径，还加上 `customer.id is not null`。因此 DML 作用于与查询相同的行 |
| 带有同表子查询的 `DELETE` | MariaDB 错误 1093 | 先用派生表读取目标 id：`d0.id in (select t.id from (select e0.id as id … where <condition>) t)`。关联子查询也可用，在三种数据库上删除相同的行（实测）。`update` 不包装（在 MariaDB 上也可用） |
| 同一个 enum 值只绑定一次，同时用于 STRING 列和 ORDINAL 列 | H2 和 MariaDB **不报错地**存储错误的值 | 每个位置一个参数，不共享 |
| `values(list) { }` 中的大量行 | Oracle：绑定变量上限 65,535 个（ORA-01745），远在此之前就已非常慢 | 按每条语句不超过 500 个值拆分，所有语句在一个事务中执行 |
| 带 `order by ... limit` 的 `update` / `delete` | MariaDB 拒绝 `in` 子查询中的 `limit`（错误 1235）和读取目标表的子查询（错误 1093） | `e0.id in (select t.id from (select x0.id as id ... order by ... limit n) t)`：借助派生表 `t` 在三种数据库上都可用。内层 select 使用查询的 join，因此作用于与查询相同的行 |
| `on conflict` / upsert | H2 和 Oracle 为 `merge`。MariaDB 为 `insert ... on duplicate key update`，把更新的行计为 2。Hibernate 的 `do nothing` 在 H2 和 Oracle 上失败 | `upsert` 和 `onConflict(...).update(...)` 使用 Hibernate 的形式。`ignore()` 写成 `insert ... select ... where not exists`。行数按原样返回 |

<a id="procedures-and-functions"></a>
### 存储过程与函数

| 项目 | H2 | MariaDB | Oracle |
|---|---|---|---|
| OUT / INOUT 参数 | **没有** | 有 | 有 |
| 多个结果集 | 没有 | 有 | 有（REF CURSOR） |
| 存储过程影响的行数 | – | – | 总是 -1，因此 lambdaQL 不提供。请使用 OUT 参数 |
| 布尔参数和结果 | – | 有 | 23ai 及以上 |
| 虚拟列 / 函数索引中的存储函数 | – | 不可能（错误 1901） | 可以（基于函数的索引） |

H2 没有 OUT 参数，因此存储过程功能在 MariaDB 和 Oracle 上确认。

<a id="107-not-supported-and-what-to-write-instead"></a>
## 10.7 不支持的写法及替代写法

对此列表中的所有写法，lambdaQL 都会抛出明确的错误。

<a id="lambda-form-and-loading"></a>
### Lambda 表达式写法与加载

| 不支持 | 替代写法 |
|---|---|
| 对象表达式（`object : ...`），不是 `Serializable` 的函数式接口 | Lambda 字面量 |
| 存为 Kotlin 函数类型的 Lambda 表达式（`val f: (Order) -> Boolean`） | 传入 Lambda 字面量 |
| `-Xsam-conversions=class`，在 `inline fun` 主体中创建的 Lambda 表达式 | 参见 [9.14](09-kotlin-notes.md#914-kotlin-limits-kc1-to-kc21) |
| 对自己定义的 Kotlin `fun interface` 使用可调用引用 | Lambda 字面量 |
| 条件、排序、select Lambda 表达式中的局部 `val`/`var`、循环、多条语句 | 一个表达式。值在 Lambda 表达式外部计算 |
| 写入捕获的变量（`n++`） | 在 Lambda 表达式外部计算 |
| `@JvmField`、`internal`、`value class`、`@get:JvmName` 属性 | 公开的普通属性 |
| 在 Java 21 到 23 上运行时，Lambda 表达式是为内置 ASM 不认识的更新 JDK 编译的 | 更新 ASM（lambdaQL 不对 ASM 做 shading）。在 Java 24 及以上运行时不适用（使用 `java.lang.classfile`） |

<a id="conditions"></a>
### 条件

| 不支持 | 替代写法 |
|---|---|
| 布尔 `if (c) x else y`、`takeIf`（`LQL-2021`）、带主体的 `when (subject)`（`LQL-2010`） | `&&` / `\|\|`。选择值的 `if`/`when` 受支持：只用捕获值和常量时是绑定参数，用到实体值时是 `case when`（[3.5.1](03-where.md#351-values-chosen-by-a-condition)） |
| （受支持：`if (c) x else false`、`if (c) true else x`、`when { a -> true; ...; else -> false }`） | 分别表示 `AND`、`OR` |
| 布尔中缀函数 `and`、`or`、`xor` | `&&`、`\|\|` |
| 实体 `==` 实体（`it.customer == c`）（`LQL-3011`） | `it.customer?.id == c.id` |
| 值对象（`@Embedded`）的大小比较、与其他类型的比较（`LQL-3012`） | 比较字段。与同类型的 `==`、`!=` 以及与 `null` 的比较受支持 |
| 集合路径（`it.items...`、`@OneToMany`、`@ManyToMany`） | `exists()` 子查询或 join |
| `item in it.items` | `exists()` 子查询 |
| 在 Lambda 表达式中创建的集合（`listOf(1, 2)`、`setOf(...)`） | 在外部创建并捕获。（`groupBy` 键除外） |
| 区间的 `step` | 两个比较，或捕获的集合 |
| 捕获集合中的 `null` 元素 | 去掉 null。需要 NULL 行时加上 `\|\| it.x == null` |
| 捕获的 `NaN` / `Infinity` | 在查询之前检查值 |
| `isNaN()` | – |
| 对实体值的其他函数调用（自己定义的非 inline 函数、`operator` 函数） | 用属性写条件，或把函数改为 `inline` |
| 字符串模板或 `+`（`LQL-2007`） | 在 Lambda 表达式外部构建字符串并捕获 |
| 实体路径上的 `it.tags["k"]`、`it.lines[0]` | – |

<a id="functions-and-strings"></a>
### 函数与字符串

| 不支持 | 替代写法 |
|---|---|
| 实体值上的字符串函数：`lowercase`、`length`、`isNullOrEmpty`、`isBlank`、`equals(s, ignoreCase = true)`（`LQL-3008`） | 直接比较。`startsWith`、`endsWith`、`contains` 受支持 |
| 列上的 `trim`、`coerceAtLeast`、`toLocalDate` 及其他标准库函数 | 直接比较，或数据库函数（`@SqlFunction`） |
| 日期函数（`YEAR(col) == 2024`） | 区间：`it.updateAt!! >= from && it.updateAt!! < to` |
| 泛型返回类型（`fun <X> nvl(a: X, b: X): X`），返回实体、集合或 `Unit` 的 `@SqlFunction` | 以下之一：`Double`、`Integer`（`Int`）、`Long`、`BigDecimal`、`String`、`LocalDate`、`LocalDateTime`、`Boolean` |
| 与 Hibernate 内置函数同名的 `@SqlFunction`（`COALESCE`、`ROUND`、`SYSDATE`、...） | 使用前缀或 schema 名（`PUBLIC.SQ_FN`） |
| 带默认参数、`inline` 或 `vararg` 的 `@SqlFunction` | 参数固定的普通函数 |
| 作为数据库函数的扩展函数、函数式接口常量、函数类型属性 | 接口 `companion object` + `@JvmStatic` |
| 用户定义的聚合函数或窗口函数 | – |
| 在查询 Lambda 表达式外部或在 `where` 中调用 `Agg.*` | 在 `select` / `having` 中使用 |

<a id="sort-and-paging"></a>
### 排序与分页

| 不支持 | 替代写法 |
|---|---|
| 排序 Lambda 表达式中的条件或循环（`if (x) asc(...)`） | 构建两个查询，或把 `Direction` 作为捕获值传入：`sort(o.price, dir)` |
| join 排序 Lambda 表达式中的属性引用 | Lambda 表达式 |
| `orderBy` 中的属性引用（`orderBy(Order::price)`） | Lambda 表达式：`orderBy { it.price }` |
| 同时包含排序名称和方向的一个字符串（`"price,desc_nulls_last"`） | 方向用 `Direction.parse(text)`，名称用 `SortSpec.ofPath`。名称由排序策略检查（[4.10](04-order-paging.md#sort-policy)） |
| 在 join 结果的 `distinct()` 中按被 join 实体的列或关联路径排序（`LQL-3046`） | 按主实体自身的列排序，或使用 `exists` 子查询 |
| 与 `limit` / `offset` 一起使用的集合 fetch join | 先分页，再加载集合 |
| Spring `Sort` 的 `ignoreCase()`（[12.5](12-spring-data-jpa.md#125-spring-sort-and-pageable)） | 不带 `ignoreCase()` 的排序 |
| 函数键（`lower(name)`） | – |
| 按聚合别名排序（`desc(s.total)`） | – |
| 用作排序键的布尔 elvis | 对键 `o.flag` 指定 NULL 的位置：`asc(o.flag, Nulls.LAST)`（NULL 在最后） |

<a id="limits-subqueries"></a>
### 子查询

| 不支持 | 替代写法 |
|---|---|
| 不是单行的标量比较中的 `first()` | 使用聚合（`max`、`min`、`avg`、`sum`、`count`）或 `orderBy { }.limit(1)...first()` |
| 没有 `limit` 的子查询中的 `orderBy` | 没有效果，因此会被移除（WARN） |

<a id="select-and-group-by"></a>
### 查询列与分组

| 不支持 | 替代写法 |
|---|---|
| `select` / `having` 中不是 `groupBy` 键的非聚合值 | 加入 `groupBy` |
| 用作派生表或 CTE 行类型的普通类 | Kotlin data class（或 Java record） |
| 用作 `groupBy` 键的 `arrayOf(...)` / `setOf(...)` | `listOf(...)`、`Pair`、`Triple` |
| 用作 select 值的布尔 elvis（`LQL-2022`） | 查询 `it.vip`，在 Kotlin 中处理 NULL |

<a id="limits-update-insert-delete"></a>
### 更新、插入、删除

| 不支持 | 替代写法 |
|---|---|
| 没有 `where` 的 `update` / `delete` | 加上 `.all()` |
| 在同一语句中设置后再读取该列 | 有意使用旧值，或执行两次 update |
| 把同一列设置两次 | 一次赋值 |
| 在 `set` Lambda 表达式中用 `if` 选择要设置的列 | 多次调用 `set()` |
| `set` 中的 `++`、`--`、`apply`、`with`、`also` | `it.cnt += 1`，直接赋值 |
| 赋值 Lambda 表达式中的局部 `val` | 在每个位置写出表达式 |
| 设置关联中不是 `@Id` 的属性（`it.customer?.name = x`） | 直接更新 `Customer` |
| 对非可选列（`@Id`、`nullable = false`、基本类型）设置 `null` | – |
| 直接写入 `@Version` 属性 | lambdaQL 改用 `update versioned` |
| 值中的字符串模板（`LQL-2007`） | 在 Lambda 表达式外部构建字符串并捕获 |
| update 或 delete 中的显式 join | 不添加。使用关联路径或 `sub` |
| 带显式 join 的 `insert ... select` | 使用多对一路径 |
| 并发插入中原子性的 `onConflict { ... }.ignore()` | 处理键重复错误（`ignore()` 使用 `not exists`。参见 [7.6](07-6-upsert.md)） |
| 用 `insert ... select` 进行的 upsert | 用 `values(list) { ... }` 逐行 upsert |
| 在 update 或 delete 中指定 `orderBy { ... }` 而没有 `limit(n)` | 加上 `limit(n)` 或去掉 `orderBy`（否则 `IllegalStateException`） |
| 用 `execute() == 1` 和回滚检查只应改变一行的 update 或 delete | `executeOne()`：先最多读取 2 个 id。读到 2 个时什么也不改变，抛出 `NonUniqueResultException`。读到 1 个时用 `id = :p and (条件)` 改变该行。不会改变 2 行或更多 |
| 把 `executeOne()` 与 `orderBy` 或 `limit` 一起使用 | 只用其中一个（否则 `IllegalStateException`） |
| update 或 delete 的 `offset` | 没有计划。DML 只有 `limit(n)`。用 `where { }`、`orderBy { }`、`limit(n)` 指定目标行 |
| 复合键实体的 `limit` 或 `executeOne()` | 尚不支持。先查询 id，再执行 `delete ... where { it.id in ids }` |
| 通过 Spring 共享 `EntityManager` 在没有事务的情况下执行 DML（lambdaQL 中没有 `@Transactional`） | 在 `@Transactional` 或 `TransactionTemplate` 中调用 |
| 在仓库方法 `deleteAll { ... }`（批量删除）中期望 cascade 或 `@PreRemove` | 使用 Spring Data 的 `deleteAll(Iterable)` 或 `delete(entity)`（参见 [12.4](12-spring-data-jpa.md#deleteallwhere-is-not-spring-datas-deleteall)） |
| `returning` | DML 之后重新查询 |
| 数据库时间（`current_timestamp`） | 传入应用程序时间（`LocalDateTime.now()` 作为值绑定） |
| 使用 `TABLE` id 生成器的 `insert ... select` | 使用 `values { }` 或 IDENTITY / SEQUENCE |
| 对 JOINED 或 TABLE_PER_CLASS 子类执行 `insert` | 不支持（Hibernate 6.6.5 会失败，7.4 未测量） |

关于 DML 还需要了解一点：批量 DML 不经过持久化上下文。Hibernate 会先 flush，
但同一会话中的托管实体之后仍保留旧值。请调用 `clear()`（或 `refresh`）。
JPA 的 cascade 和 `orphanRemoval` 不适用。
DML 语句需要事务。`QueryBuilder(EntityManagerFactory)` 会自己开启事务。使用 `EntityManager` 的构建器（包括 Spring 的共享 `EntityManager`）使用调用方的事务，没有事务时会在发送 SQL 之前失败。

<a id="procedures"></a>
### 存储过程

| 不支持 | 替代写法 |
|---|---|
| Lambda 表达式中除了一次桩函数调用之外的任何内容（日志、嵌套调用、分支） | 一次调用。参数预先计算 |
| 包含计算的命名参数（`calc(rate = r * 2, id = id)`） | 使用位置参数，或预先计算值 |
| 对带 OUT 参数的存储过程使用 `exec` | `call` |
| 参数的数据库默认值 | 传入所有参数 |
| 桩函数中的 Kotlin 默认参数 | 传入所有参数并去掉默认值 |
| 选择并调用 Oracle 包存储过程的某一个重载（会检测带重载的名称：`LQL-4044`），动态存储过程名 | – |
| Oracle 隐式结果（`DBMS_SQL.RETURN_RESULT`）、MariaDB 包、存储过程结果的分页 | 不支持 |

<a id="108-caching-a-lambda-is-analyzed-once"></a>
## 10.8 缓存：每个 Lambda 表达式只分析一次

读取字节码 opcode 是慢的部分，因此 lambdaQL **对每个 Lambda 表达式只做一次**。

| 项目 | 规则 |
|---|---|
| 缓存什么 | 分析结果：HQL 模板和参数来源列表。失败也会缓存 |
| 缓存键 | Lambda 表达式的实现类和方法（名称、签名、种类）、Lambda 表达式的类型、实体类、**ClassLoader** |
| 大小 | 有上限（LRU）。因此不会无限增长，也不会泄漏类加载器 |
| 不缓存什么 | 字节码 opcode 本身（重新读取很便宜，保存则占用很大） |
| 捕获值 | **不缓存。** 每次执行查询时读取并绑定 |
| 对捕获对象的调用（`filter.minPrice`、`this.minStatus`、`LocalDateTime.of(...)`） | **每次**执行查询时在应用程序中运行，然后作为一个参数绑定 |
| 捕获的 `var` | 每次执行查询时读取 |
| 只使用捕获值的 null 保护（`lo == null \|\| ...`） | 在构建查询时按值决定。因此 HQL 会随值而变（参见 [9.8](09-kotlin-notes.md#98-captured-values)） |
| 作为捕获值的 `Direction` | 是绑定值，每次读取（结构保持缓存状态） |
| DML | 每个 Lambda 表达式类分析一次。`set()` 调用之间的检查使用小型摘要 |

Kotlin Lambda 表达式的名称（`...$lambda$0`）可能随编译器版本或源代码修改而改变。
这没有问题。缓存键中还包含类、签名和类加载器，lambdaQL 不依赖名称格式。

用原型测量的结果（JDK 21）：新 Lambda 表达式的首次分析大约需要 **0.2 ms**。
之后，带两次 `set()` 调用的 update 在缓存查找、值提取和检查上大约花费 **0.1 到 0.2 µs**，
执行 200 万次之后也一样。目标：同一个 Lambda 表达式调用 1,000 次，字节码 opcode 也只读取一次。
Hibernate 也以 HQL 字符串为键缓存查询计划。

这对你意味着什么：

<!-- region: ManualKotlinRulesTest.kt#cache-choose -->
```kotlin
// good: one lambda, only the value changes (the lambda is analyzed once)
val byStatus = qb.from(Order::class.java).where { it.status.toInt() == status }
assertEquals("select e0 from Order e0 where e0.status = :p1", byStatus.toHql().hql())

// not possible: the shape of the query changes inside the lambda
val error = assertThrows(LambdaQLException::class.java) {
    qb.from(Order::class.java).where { if (flag) it.status.toInt() == 1 else it.isActive }
}
assertEquals("LQL-2021", error.code())

// instead: choose the lambda outside (two lambdas, each analyzed once)
val list = if (flag) {
    qb.from(Order::class.java).where { it.status.toInt() == 1 }.toList()
} else {
    qb.from(Order::class.java).where { it.isActive }.toList()
}
```

示例代码：[`ManualKotlinRulesTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinRulesTest.kt)（`cache-choose`）。

<a id="109-tested-environment"></a>
## 10.9 支持的环境

| 项目 | 版本 |
|---|---|
| Kotlin | 2.x（设计已用 kotlinc 1.7.22、2.1.10、2.2.21、2.3.0 和 2.3.21 检查） |
| Java（JVM） | 21 及以上 |
| Hibernate | 6.6 或 7.4。使用了 HQL 扩展（例如 `function(...)`、`nulls first`），因此不保证其他 JPA 实现 |
| 数据库 | H2 2.3、MariaDB 10.11、MySQL 8.4、PostgreSQL 17、Oracle 23.26 (23ai) |
| Spring | Spring Boot 3.5（Spring Data JPA 3.5）和 4.1（Spring Data JPA 4.1）（Spring 模块，[12](12-spring-data-jpa.md)、[13](../13-spring-boot-versions.md)） |
| 未确认 | 比 2.3 更新的 Kotlin、Kotlin Multiplatform、SQL Server、Oracle 19c 及更早版本（例如 PL/SQL 的 `BOOLEAN` 参数） |

---

[Kotlin 路线](README.md) | 上一章：[9. Kotlin 说明](09-kotlin-notes.md) | 下一章：[11. 错误与语言](../11-errors-and-languages.md) | [本页的 Java 版本](../java/10-rules-and-limits.md)
