<a id="3-conditions-where-kotlin"></a>
# 3. 条件（`where`）（Kotlin）

> [!IMPORTANT]
> 本页比韩文手册旧。最新规则请参见[韩文页面](../../ko/kotlin/03-where.md)。

[Kotlin 路线](README.md) | [手册首页](../README.md) | 上一章：[2. 入门](02-getting-started.md) | 下一章：[4. 排序与分页](04-order-paging.md) | [本页的 Java 版本](../java/03-where.md)

公共页面：[1. 概述](../01-overview.md) · [11. 错误与语言](../11-errors-and-languages.md)

---

<a id="31-the-idea-in-one-sentence"></a>
## 3.1 概述

**lambdaQL 的 Lambda 表达式不是代码，而是查询表达式。** lambdaQL 不调用 Lambda 表达式，而是分析其字节 opcode。
因此，处理实体的部分不适用 Kotlin 的执行规则（`===` 的对象同一性、编译器添加的 null 检查）。
只由捕获值构成的表达式先在 Kotlin 中计算（见 [3.11](#311-captured-values)）。
lambdaQL 使用它自己的规则。本章解释这些规则。

如果 lambdaQL 无法把 Lambda 表达式分析为查询，会抛出带错误码的异常（见 [3.13](#313-what-is-rejected)）。

<a id="32-example-entities"></a>
## 3.2 示例实体

所有示例都使用示例实体（Java 类）。详情见 [2. 入门](02-getting-started.md#example-entities)。

| 实体 | 本章使用的属性（Java 类型） |
|---|---|
| `Order` | `Long id`、`double price`、`short status`、`boolean active`（在 Kotlin 中为 `isActive`）、`String description`、`Integer cnt`、`String custName`、`LocalDateTime updateAt`、`Kind kind`、`Customer customer`、`Address address` |
| `Customer` | `int id`、`String name`、`Integer grade` |
| `Item` | `Long id`、`Order order`、`Double price`、`int qty` |
| `Coded` | `Long id`、`String code`（通过 `@Convert` 反转后存储）、`Boolean vip`（可为 NULL，`YesNoConverter`） |

`price`、`status`、`active`、`qty` 是基本类型，因此不会为 NULL。引用类型的属性可能为 NULL，在 Kotlin 中是平台类型。
`status` 是 `Short`，因此示例写成 `it.status.toInt() == 1`。lambdaQL 会忽略这个转换。

<a id="33-basic-form"></a>
## 3.3 基本形式

<!-- region: ManualKotlinStartTest.kt#first-query -->
```kotlin
val q = qb.from(Order::class.java).where { it.status.toInt() == 1 && it.price > 100.0 }
assertEquals("select e0 from Order e0 where e0.status = 1 and e0.price > 100.0", q.toHql().hql())
val orders: List<Order> = q.toList()
```

`qb` 是 `QueryBuilder`（见 [2. 入门](02-getting-started.md#create-a-querybuilder)）。

**各数据库的 SQL**

<!-- sql: to-hql -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>100.0
```

<!-- region: KotlinSelectTest.kt#captured -->
```kotlin
val min = 100.0
val q = qb.from(Order::class.java).where { it.price > min && it.status.toInt() == 1 }
assertEquals("select e0 from Order e0 where e0.price > :p1 and e0.status = 1", q.toHql().hql())
```

`min` 是捕获的局部 `val`，因此成为绑定参数。`price` 是基本类型 `double`，因此不需要 `!!`。
用 Java 编写的同一查询示例见[本页的 Java 版本](../java/03-where.md)。

可以多次调用 `where()`。这些条件以 `AND` 连接。

<!-- region: ManualKotlinWhereTest.kt#many-where -->
```kotlin
val q = qb.from(Order::class.java)
    .where { it.status.toInt() == 1 }
    .where { it.isActive }
assertEquals("select e0 from Order e0 where e0.status = 1 and e0.active = true", q.toHql().hql())
```

**各数据库的 SQL**

<!-- sql: many-where -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.active=true
```

**HQL 中的值。** 规则取决于值的来源。

| 值 | 在 HQL 中 | 示例 |
|---|---|---|
| 直接写在 Lambda 表达式中的常量：数字（包括小数）、字符串、`true`/`false`、`null`、enum 常量、`const val` | **字面量** | `it.price > 100.0` → `e0.price > 100.0` |
| 捕获的值：局部 `val` 或 `var`、属性、函数参数 | **绑定参数** | `it.price > minPrice` → `e0.price > :p1` |
| 计算出的值：`BigDecimal("1.5")`、`form.text.trim()`、`LocalDateTime.of(...)` | **绑定参数** | `:p1` |

- 常量在每次调用时都相同。因此 SQL 语句不会改变，数据库在制定执行计划时也能看到这个值。
- 字符串常量中的单引号会被转义：`"O'Brien"` 变为 `'O''Brien'`。该字符串不是用户输入，而是编译进类里的常量，因此没有 SQL 注入风险。
- 捕获的值可能来自用户，因此是绑定参数。每次调用只有值会变化。
- 例外只有一个。与整数列比较的小数捕获值写成小数字面量（`2.5BD`）（见 [3.4](#34-comparisons) 中的“整数列与小数值”）。只写入转换为数字后的值，因此没有 SQL 注入风险。
- enum 常量带完整类名写入。例如：`e0.kind = org.lambdaql.query.Order$Kind.RUSH`。

**`!!` 是给编译器看的。** 如果 Kotlin 实体的属性像 `Double?` 这样可空，Kotlin 无法编译 `it.price > 100.0`，因此要写 `it.price!! > 100.0`。lambdaQL 会忽略 `!!`。值为 NULL 的行不会被返回。

<!-- region: ManualKotlinWhereTest.kt#bang -->
```kotlin
// !! is only for the compiler: lambdaQL ignores it, and rows with a NULL cnt are not returned
val q = qb.from(Order::class.java).where { it.cnt!! > 1 }
assertEquals("select e0 from Order e0 where e0.cnt > 1", q.toHql().hql())
```

示例代码：[`ManualKotlinStartTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinStartTest.kt)（`first-query`）、[`KotlinSelectTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinSelectTest.kt)（`captured`）、[`ManualKotlinWhereTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt)（`many-where`、`bang`）。

<a id="34-comparisons"></a>
## 3.4 比较运算

| Kotlin | HQL | 说明 |
|---|---|---|
| `it.status == 1` | `e0.status = 1` | |
| `it.status != 1` | `e0.status <> 1` | NULL 行不返回（见 [3.6](#36-null-rules)） |
| `it.price!! > p` | `e0.price > :p1` | `<`、`<=`、`>=` 也一样 |
| `it.updateAt!! > since` | `e0.updateAt > :p1` | 在 Kotlin 中日期用 `<`、`>` 比较 |
| `it.updateAt!!.isAfter(since)` | `e0.updateAt > :p1` | `isBefore` 生成 `<` |
| `it.description!! < "m"` | `e0.description < 'm'` | `BigDecimal`、`LocalDate`、`BigInteger` 也一样 |
| `it.status!!.compareTo(3) > 0` | `e0.status > 3` | `compareTo(..) op 0` 变为 `op` |

与可空列的比较遵循 **SQL 语义**。
列为 NULL 的行不会被返回。

**算术运算。** 支持 `+`、`-`、`*`、`/` 和一元 `-`。`%` 写作 `mod`。
`/` 也可以使用，但请避免涉及列的整数 `/`。它在 SQL 中计算，因此各数据库的结果不同（MariaDB 给出 `5/2 = 2.5`，Kotlin 给出 `2`）。统一列侧 `/` 结果的规则尚未确定。
捕获值之间的计算（下面的 `n / 2.0`）在查询之前完成，因此没有这种差异。

下面示例中的 `cnt` 是 `Order` 的整数列（`Integer cnt`）。

**列上的算术运算与索引**。像 `it.cnt * 2 > n` 这样对列做算术运算时，无法使用索引。lambdaQL 以 `WARN` 级别记录 `LQL-5012`，在 `SargableMode.STRICT` 下分析失败（`LQL-3066`）。
把运算移到值一侧时，请像 `it.cnt > n / 2.0` 这样用小数相除。整数除法 `n / 2` 会得到不同的行。如果乘的是负数，请反转比较方向。详见 [10.2](10-rules-and-limits.md#db-functions-on-a-column)。

**整数列与小数值**。把整数列与小数值比较时，lambdaQL 按写出的值比较，不做舍入，也不排除值。

- 写在 Lambda 表达式中的常量保持为字面量。`it.cnt >= 2.5` 变为 `e0.cnt >= 2.5`。
- 捕获的值也写成小数字面量。结果为 `e0.cnt >= 2.5BD`，没有绑定参数。`BD` 是 HQL 中 `BigDecimal` 字面量的后缀。
- 含有这种元素的 IN 列表会把所有元素都写成小数字面量（`e0.cnt in (2.5BD, 3.0BD)`）。

这是捕获值以绑定参数传递这一原则的例外。当与整数列比较的参数绑定了小数时，即使指定 `BigDecimal` 类型，Hibernate 7 也会拒绝（`QueryArgumentException`，实测）。

以前，值会被截断为列类型，`2.5` 变成 `2`，返回错误的行。Hibernate 6.6 会拒绝以整数列类型绑定的小数（`CoercionException`），并会静默截断 IN 列表中的元素（在三种数据库上实测）。
没有采用改写为整数比较（`>= 3`）的方式。那样执行的 SQL 会与 Lambda 表达式不同；当实体映射（`Integer`）与数据库列类型（例如带小数的 `DECIMAL`）不同时，还会返回错误的行。

结果在 H2、MariaDB 10.11 和 Oracle 23.26 上正确（实测）。在三种数据库上，SQL 都像 `cnt>=2.5`、`cnt in (2.5,3.0)` 这样保持列不变，直接与小数比较。
MariaDB 的 `EXPLAIN` 显示索引范围扫描（`type=range`）。在 Oracle 上，列没有附加转换，因此是可以使用索引的形式。给出索引提示时为 `INDEX RANGE SCAN`；在 3000 行的表上不加提示时，与整数字面量（`cnt >= 99`）一样选择全表扫描（均为实测）。

<!-- region: ManualKotlinWhereTest.kt#whole-number -->
```kotlin
// cnt is an integer column: a decimal value is compared as it is, not cut to 2
val literal = qb.from(Order::class.java).where { it.cnt >= 2.5 }
assertEquals("select e0 from Order e0 where e0.cnt >= 2.5", literal.toHql().hql())

val v = 2.5
val captured = qb.from(Order::class.java).where { it.cnt >= v }
assertEquals("select e0 from Order e0 where e0.cnt >= 2.5BD", captured.toHql().hql())
```

**浮点数：** 对于 `Float`/`Double` 列，lambdaQL 不保证与 Kotlin 逐位一致的结果。
捕获的 `NaN` 或 `Infinity` 值会在查询执行时被拒绝。

示例代码：[`ManualKotlinWhereTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt)（`whole-number`）。

<a id="35-and-or-not"></a>
## 3.5 AND、OR、NOT

| Kotlin | HQL |
|---|---|
| `a && b` | `a and b` |
| `a \|\| b` | `a or b` |
| `!a` | `not (a)` |
| 括号 | 按需保留 |

<!-- region: ManualKotlinWhereTest.kt#and-or-not -->
```kotlin
val both = qb.from(Order::class.java).where { (it.status.toInt() == 1 || it.isActive) && it.price > minPrice }
assertEquals("select e0 from Order e0 where (e0.status = 1 or e0.active = true) and e0.price > :p1", both.toHql().hql())

// the compiler moves the ! inside (De Morgan): the rows are the same
val not = qb.from(Order::class.java).where { !(it.status.toInt() == 1 && it.price > minPrice) }
assertEquals("select e0 from Order e0 where e0.status <> 1 or e0.price <= :p1", not.toHql().hql())
```

规则：

- 结果遵循 **SQL 三值逻辑**（TRUE / FALSE / UNKNOWN）。lambdaQL 已经确认，这种方式在 H2、MariaDB 和 Oracle 上返回相同的行。
- 编译器有时会移动 `!` 的位置（德摩根定律，`!(x > y)` 变为 `x <= y`）。这不会改变结果。
- lambdaQL **不会简化条件**。写 `it.price!! > p || it.price!! <= p` 时，按原样发送。`price` 为 NULL 的行不会被返回。这是正常的 SQL 行为。
- 请使用 `&&` 和 `||`。中缀函数 `and`、`or`、`xor` 会被拒绝（它们不做短路求值）。

**给出 `true`/`false` 的 `if` 和 `when`。** 只允许以下简短写法。它们分别等同于 `AND`、`OR`。

| Kotlin | 等同于 |
|---|---|
| `if (c) x else false` | `c && x` |
| `if (c) true else x` | `c \|\| x` |
| `when { a -> true; b -> true; else -> false }` | `a \|\| b` |

<!-- region: ManualKotlinWhereTest.kt#if-when -->
```kotlin
val ifAnd = qb.from(Order::class.java).where { if (it.isActive) it.price > 100.0 else false }
assertEquals("select e0 from Order e0 where e0.active = true and e0.price > 100.0", ifAnd.toHql().hql())

val ifOr = qb.from(Order::class.java).where { if (it.isActive) true else it.price > 100.0 }
assertEquals("select e0 from Order e0 where e0.active = true or e0.price > 100.0", ifOr.toHql().hql())

val whenOr = qb.from(Order::class.java).where { when { it.status.toInt() == 3 -> true; it.price > 200.0 -> true; else -> false } }
assertEquals("select e0 from Order e0 where e0.status = 3 or e0.price > 200.0", whenOr.toHql().hql())
```

只用捕获值和常量选择值的 `if`（`it.status.toInt() == (if (flag) 1 else 2)`）会在执行查询时于 Kotlin 中计算，并作为绑定参数传递。
用实体值选择值的 `if` 和 `when` 会成为 SQL `case when … end`（见 [3.5.1](#351-values-chosen-by-a-condition)）。
其他布尔 `if (c) x else y` 会被拒绝。

<!-- region: ManualKotlinWhereTest.kt#value-before -->
```kotlin
// an if of captured values and constants: computed in Kotlin, bound as :p1
val q = qb.from(Order::class.java).where { it.status.toInt() == (if (flag) 1 else 2) }
assertEquals("select e0 from Order e0 where e0.status = :p1", q.toHql().hql())

// an if that reads the entity becomes case when
val c = qb.from(Order::class.java).where { it.status.toInt() == (if (it.isActive) 1 else 2) }
assertEquals("select e0 from Order e0 where e0.status = case when e0.active = true then 1 else 2 end", c.toHql().hql())
```

示例代码：[`ManualKotlinWhereTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt)（`and-or-not`、`if-when`、`value-before`）。

<a id="351-values-chosen-by-a-condition"></a>
### 3.5.1 按条件选择的值（`case when`）

用实体值选择值的 `if (c) x else y`、`if … else if … else` 以及没有主体的 `when { }` 会成为一个 SQL `case when … end` 表达式。
条件中可以混用 `&&`、`||`、`!`。

`case` 可以用于 `where` 的比较值、`select` 的查询值、`groupBy` 键、排序键以及 `update` 的赋值。

<!-- region: KotlinCaseWhenTest.kt#case-where -->
```kotlin
val q = qb.from(Order::class.java).where { it.status.toInt() == if (it.isActive) 1 else 2 }
assertEquals("select e0 from Order e0 where e0.status = case when e0.active = true then 1 else 2 end", q.toHql().hql())
```

<!-- region: KotlinCaseWhenTest.kt#case-when-select -->
```kotlin
val q = qb.from(Order::class.java).select {
    when {
        it.price > 200.0 -> "big"
        it.price > 100.0 -> "medium"
        else -> "small"
    }
}
assertEquals("select case when e0.price > 200.0 then 'big' when e0.price > 100.0 then 'medium' else 'small' end from Order e0",
    q.toHql().hql())
```

<!-- region: KotlinCaseWhenTest.kt#case-group -->
```kotlin
// orders per price band: the when is the group key and the selected value
val q = qb.from(Order::class.java)
    .groupBy { if (it.price > 100.0) "big" else "small" }
    .select { if (it.price > 100.0) "big" else "small" }
assertEquals("select case when e0.price > 100.0 then 'big' else 'small' end from Order e0"
    + " group by case when e0.price > 100.0 then 'big' else 'small' end", q.toHql().hql())
```

<!-- region: KotlinCaseWhenTest.kt#case-sort -->
```kotlin
// active orders first, then by price
val q = qb.from(Order::class.java).orderBy { o -> asc(if (o.isActive) 0 else 1).asc(o.price) }.limit(10)
assertEquals("select e0 from Order e0 order by case when e0.active = true then 0 else 1 end asc, e0.price asc, e0.id asc",
    q.toHql().hql())
```

<!-- region: KotlinCaseWhenTest.kt#case-set -->
```kotlin
// 10 % off the orders above 100
val u = qb.update(Order::class.java).set { it.price = if (it.price > 100.0) it.price * 0.9 else it.price }.all()
assertEquals("update Order e0 set e0.price = case when e0.price > 100.0 then (e0.price * 0.9) else e0.price end", u.toHql().hql())
```

规则：

- `case` 从上到下依次检查分支。因此第二个及之后分支的条件就是 Lambda 表达式中写的条件本身。
- 在 `where` 中参与比较的 `case` 按行计算，因此数据库无法使用其中列的索引。这时 lambdaQL 以 `WARN` 级别记录 `LQL-5013`。
- 按条件选择排序函数链本身的写法（`if (flag) desc(o.price) else asc(o.id)`）会被拒绝（`LQL-2023`）。请把条件写在键值中。
- 有主体的 `when (x)` 遵循 [3.15](#315-when-kotlin-only) 的规则。不支持 `takeIf`。

示例代码：[`KotlinCaseWhenTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinCaseWhenTest.kt)（`case-where`、`case-when-select`、`case-group`、`case-sort`、`case-set`）。

<a id="36-null-rules"></a>
## 3.6 NULL 规则

这是最重要的部分。有三条简单的原则。

1. **在写了 null 检查的地方，lambdaQL 使用该检查。**（`== null`、`!= null`、`?.`、`?:`）
2. **在没有写的地方，lambdaQL 使用 SQL 语义。** 列为 NULL 时，比较结果为 UNKNOWN，该行不会被返回。
   实体值之间的 `==`、`!=` 也适用同样的规则。
   当结果可能与 Kotlin 不同时，lambdaQL 以 `DEBUG` 级别记录 `LQL-5009`。
3. **捕获的值先被计算，比较同样遵循 SQL 语义。** 捕获的 `null` 以 NULL 绑定，因此 `= :p1` 和 `<> :p1` 不匹配任何行。
   这时 lambdaQL 在执行时以 `WARN` 级别记录 `LQL-4045`。要查找 NULL，请写 `x == null`（变为 `is null`）。
   对捕获值调用方法的表达式（`text.trim()`、转换为其他基本类型的 `min.toInt()` 等）会先被计算，因此值为 `null` 时会像 Kotlin 一样抛出异常（`LQL-4012`）。
   捕获值的 `!!` 和拆箱为其自身基本类型（`min!!`）不会被执行。值为 `null` 时绑定 NULL，并以 `DEBUG` 级别记录 `LQL-4045`。

Kotlin 编译器会在字节 opcode 中添加**隐藏的** null 检查（例如与捕获的 `id: Long` 比较的 `it.id == id`）。只有源代码中的 null 检查才有意义。见 [3.6.2](#362--compares-values)。

<a id="361-rule-table"></a>
### 3.6.1 规则表

| # | Kotlin 写法 | HQL | NULL 行为 |
|---|---|---|---|
| R12 | 不可能为 NULL 的列与非空值：`it.id == id`（`id: Long`） | `e0.id = :p1` | 去掉编译器添加的 null 检查 |
| R1 | 大小比较：`it.price > p`、`it.cnt!! > 1` | `e0.price > :p1`、`e0.cnt > 1` | SQL 语义 |
| R11 | 可空列与可空的捕获值：`it.cnt == n`（`n: Int?`）、`it.description == text`（`String?`）、enum `==` | `e0.cnt = :p1` | 值比较。列为 NULL 时该行不返回。捕获的 `null`：没有匹配的行（`LQL-4045`） |
| R11 | `it.cnt != c` | `e0.cnt <> :p1` | SQL 语义。列为 NULL 时该行**不**返回（Kotlin 会返回）。捕获的 `null`：没有匹配的行（`LQL-4045`） |
| R11 | 两个实体值：`it.description == it.custName` | `e0.description = e0.custName` | SQL 语义。任一侧为 NULL 时该行不返回（NULL 与 NULL 也一样） |
| R3 | `x == null`、`x != null` | `x is null`、`x is not null` | |
| R17 | `===`、`!==` | 与 `==`、`!=` 相同 | 值比较，而不是对象同一性 |
| R9 | 捕获的值 | 绑定参数 | 见 [3.6.4](#364-captured-null-values) |

<a id="362--compares-values"></a>
### 3.6.2 `==` 比较的是值

Kotlin 的 `==` 本来就比较值。在 lambdaQL 中，`===` 也比较**值**。
查询中没有对象同一性。两个值相同的不同 `Long` 装箱对象是相等的。

<!-- region: ManualKotlinWhereTest.kt#captured-eq -->
```kotlin
val n: Int? = 3
val q = qb.from(Order::class.java).where { it.cnt == n }
assertEquals("select e0 from Order e0 where e0.cnt = :p1", q.toHql().hql())

// a captured null matches no row: "= NULL" is never true (WARN LQL-4045)
val none: Int? = null
assertEquals(0, qb.from(Order::class.java).where { it.cnt == none }.toList().size)
```

要查找 `cnt` 为 NULL 的行，请写出 null 检查。`it.cnt == null` 变为 `e0.cnt is null`。

列不会被函数包裹，因此数据库可以使用索引（见 [10.2](10-rules-and-limits.md#102-sargable-rule-index-friendly-sql)）。

<!-- region: ManualKotlinWhereTest.kt#string-eq -->
```kotlin
val eq = qb.from(Order::class.java).where { it.description == text }
assertEquals("select e0 from Order e0 where e0.description = :p1", eq.toHql().hql())

// a NULL description matches neither = nor <> (SQL meaning)
val ne = qb.from(Order::class.java).where { it.description != text }
assertEquals("select e0 from Order e0 where e0.description <> :p1", ne.toHql().hql())
```

**不可能为 NULL 的列：`it.id == id`。** 示例实体的 `id` 是 `Long`，在 Kotlin 中是平台类型。Kotlin 把 `it.id == id`（捕获的 `id: Long`）编译为一个 null 检查加一个比较。

直接翻译这段字节 opcode 会得到 `not (e0.id is null or e0.id <> :p1)`。结果是正确的，但数据库难以很好地使用索引。
id 不可能为 NULL，因此对 id 的 `is null` 总是假。lambdaQL 去掉这个检查，写成 `e0.id = :p1`。

同样的规则也适用于基本类型属性和 `nullable = false` 的列，但只在路径**没有关联步骤**时适用。存在没有客户的订单，所以 `it.customer?.id` 可能为 NULL，这条路径的 null 检查会保留。

<!-- region: ManualKotlinWhereTest.kt#never-null -->
```kotlin
// the id can never be NULL: the null check that Kotlin adds is dropped
val byId = qb.from(Order::class.java).where { it.id == id }
assertEquals("select e0 from Order e0 where e0.id = :p1", byId.toHql().hql())

val noId = qb.from(Order::class.java).where { it.id == null }
assertEquals("select e0 from Order e0 where 1=0", noId.toHql().hql())

// a path through an association can be NULL: the check stays
val noCustomerId = qb.from(Order::class.java).where { it.customer?.id == null }
assertEquals("select e0 from Order e0 where e0.customer.id is null", noCustomerId.toHql().hql())
```

> [!WARNING]
> **Kotlin 的非空类型不能作为证据。** `var code: String` 属性中也可能有来自数据库的 NULL。
> 因为 Hibernate 会不加检查地把列值写入字段。
> lambdaQL 看的是 JPA 元数据，而不是 Kotlin 类型。

<a id="363-write-your-null-checks-yourself"></a>
### 3.6.3 自己编写 null 检查

在 Kotlin 中，`null != 3` 为 `true`。在 lambdaQL 中则不同。下面的 `it.cnt != c` **不会**返回 `cnt` 为 NULL 的行，因为没有写 null 检查。

<!-- region: ManualKotlinWhereTest.kt#null-check -->
```kotlin
val c: Int? = 3
// rows with a NULL cnt are left out (SQL meaning); Kotlin would keep them
val ne = qb.from(Order::class.java).where { it.cnt != c }
assertEquals("select e0 from Order e0 where e0.cnt <> :p1", ne.toHql().hql())

// to keep the NULL rows, write the null check
val keep = qb.from(Order::class.java).where { it.cnt == null || it.cnt != c }
assertEquals("select e0 from Order e0 where e0.cnt is null or e0.cnt <> :p1", keep.toHql().hql())
```

> [!IMPORTANT]
> 这与执行 Kotlin 代码不同。要保留 NULL 行，请像 `it.cnt == null || it.cnt != c` 这样自己写出 null 检查。

可能出现这种差异时，lambdaQL 以 `DEBUG` 级别记录 `LQL-5009`（每个条件一次）。

对 `String?` 使用 `it.description.equals(text)` 与 `==` 相同。

**实体值的 SQL 语义**。两个实体值的比较以及可为 NULL 的 `Boolean` 比较也遵循 SQL 语义。

<!-- region: ManualKotlinWhereTest.kt#sql-meaning -->
```kotlin
// two entity values: = as in SQL; a row with a NULL on either side does not match
val same = qb.from(Order::class.java).where { it.description == it.custName }
assertEquals("select e0 from Order e0 where e0.description = e0.custName", same.toHql().hql())

// to keep the NULL rows, say so in the lambda
val keep = qb.from(Order::class.java).where { it.description == null || it.description != text }
assertEquals("select e0 from Order e0 where e0.description is null or e0.description <> :p1", keep.toHql().hql())

// a NULL Boolean is not false
val notVip = qb.from(Coded::class.java).where { it.vip == false }
assertEquals("select e0 from Coded e0 where e0.vip = false", notVip.toHql().hql())
```

关于 `?.`、`?:` 和 `!!` 的更多内容：[9. Kotlin 说明](09-kotlin-notes.md)。

<a id="364-captured-null-values"></a>
### 3.6.4 捕获的 null 值

| 写法 | 捕获的值为 `null` 时 |
|---|---|
| 与捕获的值进行 `==`、`!=` 比较：`it.cnt == n`、`it.description != text` | 执行：绑定 NULL 的 `= :p1` / `<> :p1`，因此**没有**匹配的行。`WARN` 级别 `LQL-4045` |
| 大小比较：`it.price!! > min!!`，以及所有 `<`、`<=`、`>`、`>=` | 执行：`e0.price > :p1` 绑定 NULL，**没有**匹配的行。`DEBUG` 级别 `LQL-4045`。`min!!` 和拆箱为其自身基本类型不会被执行 |
| 对捕获值调用方法：`text.trim()`、转换为其他基本类型的 `min.toInt()` | 查询执行时**抛出异常**（`LQL-4012`）。因为它们会先被计算 |
| 与 `in` 一起使用的集合中有 null 元素 | 查询执行时**抛出异常** |

原因：Lambda 表达式中的比较遵循 SQL 语义。在 SQL 中，与 NULL 的 `=`、`<>` 不会为真。
捕获的值是否为 null 只有在查询执行时才知道，因此 lambdaQL 在执行时记录警告。

对捕获值调用方法的表达式是在查询之前计算的值。`text.trim()`、转换为其他基本类型的 `min.toInt()` 属于这种情况，值为 `null` 时会像 Kotlin 一样抛出异常（`LQL-4012`）。
捕获值的 `!!` 和拆箱为其自身基本类型不会被执行，而是绑定装箱值。值为 `null` 时没有匹配的行，并以 `DEBUG` 级别记录 `LQL-4045`。
要查找 NULL 行，请写 `x == null`。

**用 null 守卫省略过滤。** 对同一个捕获值做了 null 检查时，如果该值为 null，这个条件会消失，不做任何过滤。

<!-- region: ManualKotlinWhereTest.kt#captured-null-guard -->
```kotlin
fun byMinPrice(min: Double?) = qb.from(Order::class.java).where { min == null || it.price > min }

val all = byMinPrice(null)
assertEquals("select e0 from Order e0", all.toHql().hql())               // no filter
val some = byMinPrice(100.0)
assertEquals("select e0 from Order e0 where e0.price > :p1", some.toHql().hql())
```

**各数据库的 SQL**

<!-- sql: captured-null-guard -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
```

**各数据库的 SQL**

<!-- sql: captured-null-guard#2 -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.price>?
```

工作方式：

- 只使用捕获值和常量的部分，会在查询执行时在应用程序中决定。与 Kotlin 一样，从左到右并以短路方式（short-circuit）求值。
- `min == null` 时，守卫为真，所以整个条件为真。没有过滤。与 `min` 的比较不会写入 HQL（Kotlin 也不会执行这个比较）。
- `min = 100.0` 时，守卫为假，所以只剩下 `e0.price > :p1`。
- Lambda 表达式仍然只分析一次。随值变化的只有 HQL 文本。
- 这种情况下守卫的位置不影响结果。`min == null` 时，`it.price > min!! || min == null` 同样没有过滤，因为 `min!!` 和拆箱不会被执行（Kotlin 会抛出 `NullPointerException`）。
- 对捕获值调用方法的比较（`it.description == text.trim()`）不同。守卫写在后面时，`text.trim()` 会先被计算，因此 `text == null` 时抛出异常（`LQL-4012`）。

<a id="365-converted-attributes-convert"></a>
### 3.6.5 转换属性（`@Convert`）

带有 JPA `AttributeConverter` 的属性按 Kotlin 值比较。Hibernate（7.4，以前的 6.6 也是）会转换与这种属性比较的字面量，也会转换绑定参数（已在 H2、MariaDB 和 Oracle 上确认）。

<!-- region: ManualKotlinWhereTest.kt#convert -->
```kotlin
// code is stored reversed ("abc" as "cba"): Hibernate converts the literal
val q = qb.from(Coded::class.java).where { it.code == "abc" }
assertEquals("select e0 from Coded e0 where e0.code = 'abc'", q.toHql().hql())
```

示例代码：[`ManualKotlinWhereTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt)（`captured-eq`、`string-eq`、`never-null`、`null-check`、`sql-meaning`、`captured-null-guard`、`convert`）。

<a id="37-boolean-columns"></a>
## 3.7 布尔列

`Order` 的 `active` 是基本类型 `boolean`（不为 NULL，Kotlin 属性名为 `isActive`）。
出现 `vip` 的行针对的是 `Coded` 中可为 NULL 的 `Boolean vip` 列。

| Kotlin | HQL | NULL 行 |
|---|---|---|
| `it.isActive` | `e0.active = true` | –（不为 NULL） |
| `it.vip == true` | `e0.vip = true` | 不返回 |
| `it.vip != true` | `e0.vip <> true` | **不返回**（SQL 语义；Kotlin 会返回） |
| `it.vip == false` | `e0.vip = false` | 不返回（NULL 不是 false） |
| `it.vip ?: false` | `e0.vip is not null and e0.vip = true` | 不返回 |
| `!(it.vip ?: false)` | `e0.vip is null or e0.vip = false` | **返回** |

说明：

- lambdaQL 不使用 `is not true` 或 `coalesce(flag, false)`。它们在 Oracle 上会失败（ORA-00932）。
- 对较大条件的取反总是写作 `not (...)`。lambdaQL 不会把条件包裹成 `(...) = false` 或 `(...) is null`，因为 Oracle 23 在 `WHERE` 中对它们的求值是错误的。
- `it.vip ?: false` **在条件中**没有问题。作为值或排序键时会被拒绝。

<!-- region: ManualKotlinWhereTest.kt#nullable-boolean -->
```kotlin
// vip is a Boolean column that can be NULL
val yes = qb.from(Coded::class.java).where { it.vip == true }
assertEquals("select e0 from Coded e0 where e0.vip = true", yes.toHql().hql())

val notYes = qb.from(Coded::class.java).where { it.vip != true }
assertEquals("select e0 from Coded e0 where e0.vip <> true", notYes.toHql().hql())

val elvis = qb.from(Coded::class.java).where { it.vip ?: false }
assertEquals("select e0 from Coded e0 where e0.vip is not null and e0.vip = true", elvis.toHql().hql())

val notElvis = qb.from(Coded::class.java).where { !(it.vip ?: false) }
assertEquals("select e0 from Coded e0 where e0.vip is null or e0.vip = false", notElvis.toHql().hql())
```

示例代码：[`ManualKotlinWhereTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt)（`nullable-boolean`）。

<a id="38-strings"></a>
## 3.8 字符串

- 字符串上的 `==` 和 `equals` 使用**数据库的排序规则（collation）**。
  - MariaDB 的 `_ci` 排序规则会忽略大小写和尾随空格。
  - Oracle 把 `''`（空字符串）存储为 NULL。因此在 Oracle 上，`it.description == ""` 与 Kotlin 的行为不同。
**`startsWith`、`endsWith`、`contains`** 会变为 `like`。搜索词中的 `%` 和 `_` 不是通配符，而是按字面字符查找。

<!-- region: KotlinLikeTest.kt#like -->
```kotlin
// % and _ in the text are not wildcards: only "50%_off sale" matches, not "50x off"
val starts = qb.from(Order::class.java).where { it.description.startsWith(p) }
assertEquals("select e0 from Order e0 where e0.description like :p1 escape '!'", starts.toHql().hql())
// ignoreCase = true: both sides in lower case
val any = qb.from(Order::class.java).where { it.description.contains("all", ignoreCase = true) }
assertEquals("select e0 from Order e0 where lower(e0.description) like '%all%' escape '!'", any.toHql().hql())
```

- 常量按原样写入 HQL，捕获的值成为绑定参数。
- `ignoreCase = true` 会把两侧改为 `lower(...)`。这样无法使用列上的普通索引，因此以 `WARN` 级别记录 `LQL-5013`。
- `endsWith` 和 `contains` 的模式以 `%` 开头，因此无法使用列上的普通索引。
- 在 MariaDB 和 MySQL 的默认排序规则下，`like` 不区分大小写。因此结果可能与 Kotlin 的 `startsWith` 不同。
- 用作搜索词的捕获值为 `null` 时会报错（`LQL-4047`）。
- 不支持 `lowercase()`、`isBlank()`、`trim()` 等其他字符串函数（`LQL-3008`）。
- Lambda 表达式中的字符串模板和 `+` 会被拒绝（`LQL-2007`）。只使用捕获值的模板（`"$a-$b"`）也一样，因此请在 Lambda 表达式之前构建字符串并捕获它。

<!-- region: ManualKotlinWhereTest.kt#string-rejected -->
```kotlin
// string functions on a column other than startsWith, endsWith and contains are not translated
val f = assertThrows<LambdaQLException> {
    qb.from(Order::class.java).where { it.description.lowercase() == "a" }.toHql()
}
assertTrue(f.message!!.startsWith("[LQL-3008]"))

// string building in the lambda is rejected: build the string before the lambda
val a = "x"
val b = "y"
val t = assertThrows<LambdaQLException> {
    qb.from(Order::class.java).where { "$a-$b" == it.description }.toHql()
}
assertTrue(t.message!!.startsWith("[LQL-2007]"))
```

示例代码：[`ManualKotlinWhereTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt)（`string-rejected`）、[`KotlinLikeTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinLikeTest.kt)（`like`）。

<a id="39-in-with-a-captured-collection"></a>
## 3.9 使用捕获集合的 IN

在 Lambda 表达式**外部**构建集合，并使用 `in`。

<!-- region: ManualKotlinWhereTest.kt#in-ids -->
```kotlin
val ids = seeded.toList()        // two order ids, built outside the lambda

val inList = qb.from(Order::class.java).where { it.id in ids }
assertEquals("select e0 from Order e0 where e0.id in (:p1)", inList.toHql().hql())

val notInList = qb.from(Order::class.java).where { it.id !in ids }
assertEquals("select e0 from Order e0 where e0.id not in (:p1)", notInList.toHql().hql())
```

`seeded` 是预先保存的两个订单的 id。

**各数据库的 SQL**

<!-- sql: not-in -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.id not in (?,?)
```

<!-- region: KotlinSelectTest.kt#in -->
```kotlin
val q = qb.from(Order::class.java).where { it.status in statuses }
assertEquals("select e0 from Order e0 where e0.status in (:p1)", q.toHql().hql())
```

`statuses` 是捕获的 `listOf<Short>(1, 3)`。

`!in` 变为 `not in`。可能为 NULL 的列也只生成简单的 `not in`（SQL 语义）。
值为 NULL 的行不匹配 `not in`，lambdaQL 以 `DEBUG` 级别记录 `LQL-5009`。Kotlin 的 `!in` 对 NULL 值为 true，因此要保留这些行，请像 `it.description == null || it.description !in descs` 这样写出 null 检查。

| 情况 | 结果 |
|---|---|
| 集合是捕获的 `List`、`Set` 或其他 `Collection` | 支持 |
| 集合中有 `null` 元素 | 查询执行时抛出异常 |
| 空集合 | `in` 为 FALSE，`!in` 为 TRUE（Hibernate 生成 `1=0` / `1=1`） |
| 1,500 个元素 | 在 H2、MariaDB 和 Oracle 23ai 上用一条语句即可运行（较旧的 Oracle 版本有 1,000 个的限制；未验证） |
| 集合是实体集合：`item in it.items` | **拒绝**。请使用 `exists()` 子查询（[5. 子查询](05-subquery.md)） |
| 在 Lambda 表达式内部创建的集合：`it.status in listOf(1, 2)` | **拒绝**。请在外部创建并捕获它 |

Kotlin 还有区间（`it.status in 1..5`）和使用常量的 `when`。见 [3.14](#314-ranges-kotlin-only) 和 [3.15](#315-when-kotlin-only)。

示例代码：[`ManualKotlinWhereTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt)（`in-ids`）、[`KotlinSelectTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinSelectTest.kt)（`in`）。

<a id="310-association-paths"></a>
## 3.10 关联路径

lambdaQL 不执行 `it.customer?.name`。这条路径只是指代“关联客户的 `name` 列”。
lambdaQL 用 JPA Metamodel 检查每一步。

| 路径步骤 | HQL | 示例 |
|---|---|---|
| 对一关联（`@ManyToOne`、`@OneToOne`） | 带别名的 `join` 或 `left join`（见下文） | `it.customer?.name` -> `join e0.customer j1` ... `j1.name` |
| 嵌入（`@Embedded`） | 同一张表，不需要 join。值对象整体的相等比较按列展开（见下文） | 如果 `Order` 有嵌入的 `address`：`it.address.city` -> `e0.address.city` |
| 以目标 `@Id` 结尾的路径 | 外键列，不需要 join | `it.customer?.id` -> `e0.customer.id` |
| 关联本身与 `null` 比较 | 外键列，不需要 join | `it.customer == null` -> `e0.customer is null` |
| 集合（`@OneToMany`、`@ManyToMany`） | **拒绝** | `it.items...` -> 请使用 `exists()` 子查询 |

**值对象整体的比较。** 对嵌入的值对象（`@Embedded`）整体做相等比较时，lambdaQL 把它展开为每列一个比较。
`it.address == addr` 变为 `e0.address.city = :p1 and e0.address.zip = :p2`。

- 取反（`it.address != addr`）为 `e0.address.city <> :p1 or e0.address.zip <> :p2`。
- `it.address == null` 为 `e0.address.city is null and e0.address.zip is null`，`!= null` 为 `e0.address.city is not null or e0.address.zip is not null`。
- 每一列都遵循 SQL 语义。为 NULL 的列既不匹配 `=`，也不匹配 `<>`。例如 (Seoul, NULL) 行既不匹配相等比较，也不匹配取反（在三种数据库上实测）。
- 捕获的值对象的每个属性都在查询执行时读取并绑定。捕获的对象为 `null` 时，所有列都绑定 NULL，因此没有匹配的行，并且每列都以 `WARN` 级别记录 `LQL-4045`。
- 嵌套的值对象也会展开。
- 大小比较（`<` 等）以及与其他类型的比较仍然是 `LQL-3012` 错误。请比较字段。

<!-- region: KotlinQueryTest.kt#embedded-compare -->
```kotlin
// address is @Embedded (city, zip): == with a value object compares each column
val addr = Address("Seoul", "04524")
val same = qb.from(Order::class.java).where { it.address == addr }
assertEquals("select e0 from Order e0 where e0.address.city = :p1 and e0.address.zip = :p2", same.toHql().hql())
val none = qb.from(Order::class.java).where { it.address == null }
assertEquals("select e0 from Order e0 where e0.address.city is null and e0.address.zip is null", none.toHql().hql())
```

`it.customer?.name`、`it.customer!!.name` 以及（对于 Java 实体）`it.customer.name` 都表示**同一个列** `j1.name`。
在 `where` 中，连接类型由整个条件决定。没有客户的订单可能满足条件时为 `left join`，不可能满足时为内连接 `join`。
结果与对列表执行同一 Lambda 表达式的结果相同。`&&` 和 `||` 的短路求值也会被考虑。

对没有客户的订单，各比较按经过关联步骤的方式如下求值。

| 在 `where` 中经过关联步骤的方式 | 对没有客户的订单的比较 |
|---|---|
| `!!`，或不加标记（非空类型 `customer: Customer`、Java 实体的平台类型） | 不为真。只在有客户的订单上为真 |
| `?.` | 把客户的列视为 NULL，按 SQL 语义比较。`j1.name = 'Kim'` 不为真，`j1.name is null` 为真 |

无论哪种情况，值的比较都遵循 SQL 语义。例如 `it.customer?.name != "Kim"` 不匹配没有客户的订单。
`?.` 和 `!!`（或不加标记）可以在同一个条件中混用。

**成为内连接的情况。** 如果没有客户的订单不可能满足条件，即使用 `?.` 书写也是内连接。结果相同。

<!-- region: ManualKotlinStartTest.kt#join-path -->
```kotlin
val name = "Kim"
val q = qb.from(Order::class.java).where { it.customer?.name == name }
assertEquals("select e0 from Order e0 join e0.customer j1 where j1.name = :p1", q.toHql().hql())
```

**各数据库的 SQL**

<!-- sql: inner-join -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name=?
```

- **没有客户的订单**不在结果中。因为没有可以连接的客户行。
- lambdaQL 不会替你添加 null 检查。不需要在路径前面写 `it.customer != null &&`。

<!-- region: KotlinQueryTest.kt#safe-call-and -->
```kotlin
// no order without a customer can match: an inner join, also with ?.
val q = qb.from(Order::class.java).where { it.customer?.name == "Kim" && it.price > 100.0 }
assertEquals("select e0 from Order e0 join e0.customer j1 where j1.name = 'Kim' and e0.price > 100.0", q.toHql().hql())
```

**`?.` 与 `||`。** status 为 1 的订单即使没有客户也满足条件，因此是 `left join`。
status 为 1 且没有客户的订单也保留在结果中。这与在 Kotlin 中执行同一条件的结果相同。

<!-- region: KotlinQueryTest.kt#safe-call-or -->
```kotlin
// ?. says the customer may be missing: an order without a customer stays when the rest of the OR is true
val q = qb.from(Order::class.java).where { it.status.toInt() == 1 || it.customer?.name == "Kim" }
assertEquals("select e0 from Order e0 left join e0.customer j1 where e0.status = 1 or j1.name = 'Kim'", q.toHql().hql())
```

**各数据库的 SQL**

<!-- sql: safe-call-or -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where o1_0.status=1 or c1_0.name='Kim'
```

**`?.` 与 `== null`。** `it.customer?.name == null` 与 Kotlin 一样，既查找没有客户的订单，也查找客户没有名字的订单。
用 `!!` 写的 `it.customer!!.name == null` 只在有客户的订单上为真，因此是内连接，只查找客户没有名字的订单。

<!-- region: KotlinQueryTest.kt#safe-call-null -->
```kotlin
// "no customer, or a customer without a name": a left join, as in Kotlin
val q = qb.from(Order::class.java).where { it.customer?.name == null }
assertEquals("select e0 from Order e0 left join e0.customer j1 where j1.name is null", q.toHql().hql())
```

**各数据库的 SQL**

<!-- sql: safe-call-null -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name is null
```

**null 守卫。** `it.customer == null || ...` 中没有客户的订单满足条件，因此是 `left join`，没有客户的订单会保留。用 `?.` 写还是用 `!!` 写都一样。
`it.customer != null && ...` 中没有客户的订单不可能满足条件，因此是内连接。

<!-- region: ManualKotlinWhereTest.kt#null-guard -->
```kotlin
// the null guard keeps orders without a customer: a left join (?. or !! give the same)
val safe = qb.from(Order::class.java).where { it.customer == null || it.customer?.name == name }
assertEquals("select e0 from Order e0 left join e0.customer j1 where e0.customer is null or j1.name = :p1",
    safe.toHql().hql())
val sure = qb.from(Order::class.java).where { it.customer == null || it.customer!!.name == name }
assertEquals("select e0 from Order e0 left join e0.customer j1 where e0.customer is null or j1.name = :p1",
    sure.toHql().hql())

// no order without a customer can match: an inner join
val and = qb.from(Order::class.java).where { it.customer != null && it.customer?.name == name }
assertEquals("select e0 from Order e0 join e0.customer j1 where e0.customer is not null and j1.name = :p1",
    and.toHql().hql())
```

下面第一个 SQL 由用 `!!` 写的第二种形式生成，第二个 SQL 由用 `&&` 写的第三种形式生成。

**各数据库的 SQL**

<!-- sql: null-guard -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.id is null or c1_0.name=?
```

**各数据库的 SQL**

<!-- sql: null-guard#2 -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.id is not null and c1_0.name=?
```

Hibernate 把 `e0.customer is null` 写成 `c1_0.id is null`。在内连接之后，这个条件不会为真。

**`!!` 与 `||`。** 用 `!!` 经过的比较只在有客户的订单上为真。
在 Kotlin 中，`||` 在到达 `!!` 之前就结束的行（status 1、没有客户）会保留。这一行满足条件，因此 lambdaQL 也使用 `left join` 并保留该行。

<!-- region: KotlinQueryTest.kt#not-null-assert -->
```kotlin
// !! counts only for orders with a customer; || stops before it, so the status 1 order without a customer
// stays, as in Kotlin: a left join
val q = qb.from(Order::class.java).where { it.status.toInt() == 1 || it.customer!!.name == "Kim" }
assertEquals("select e0 from Order e0 left join e0.customer j1 where e0.status = 1 or j1.name = 'Kim'", q.toHql().hql())
```

**混用 `?.` 和 `!!` 的情况。** 同一关联步骤可以在一处用 `?.` 经过，在另一处用 `!!` 或不加标记经过。每个比较都适用上表的求值。
第一个示例中，任何一侧都不可能在没有客户的订单上为真，因此是内连接。第二个示例中，`?.` 一侧在没有客户的订单上为真，因此是 `left join`。
在 `left join` 中，如果用 `!!` 或不加标记写出像 `j1.grade is null` 这样在 NULL 列上可能为真的比较（`is null`、`coalesce`），lambdaQL 只给该比较加上 `e0.customer is not null`。

<!-- region: KotlinQueryTest.kt#safe-and-assert -->
```kotlin
// the same association with ?. in one place and !! in another: neither side can match an order without
// a customer, so an inner join (no warning)
val q = qb.from(Order::class.java).where { it.customer?.name == "Kim" || it.customer!!.grade!! > 3 }
assertEquals("select e0 from Order e0 join e0.customer j1 where j1.name = 'Kim' or j1.grade > 3", q.toHql().hql())
// ?. side true without a customer: a left join, and the !! side counts only with a customer
val keep = qb.from(Order::class.java).where { it.customer?.name == null || it.customer!!.grade == null }
assertEquals("select e0 from Order e0 left join e0.customer j1"
    + " where j1.name is null or (e0.customer is not null and j1.grade is null)", keep.toHql().hql())
```

- 路径有多个步骤时（`Item` 的 `it.order?.customer?.name`），每一步都按同样的规则决定连接类型。

要查找**没有**客户的订单，请比较关联本身。这会检查外键列，不需要 join。

<!-- region: ManualKotlinWhereTest.kt#no-customer -->
```kotlin
val q = qb.from(Order::class.java).where { it.customer == null }
assertEquals("select e0 from Order e0 where e0.customer is null", q.toHql().hql())
```

**各数据库的 SQL**

<!-- sql: no-association -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id is null
```

补充说明：

- 同一条路径只得到一个别名。`where`、`orderBy` 和 `select` 共用这个别名。
- `it.customer?.id == 5L` 使用外键列 `e0.customer.id`。不需要 join。
- **只**在 `orderBy` 或 `select` 中使用的路径是 `left join`，因此那里不会丢失行。有 `!!` 时也一样。如果 `where` 也使用同一条路径，`orderBy` 和 `select` 会使用 `where` 的连接。
- 这一连接规则适用于查询、`count`、子查询和连接查询的 `where`。显式连接的 `on` 中的路径仍像现在一样是 `left join`。必需关联（`optional = false`）总是内连接。
- 不要把关联与实体对象比较（`it.customer == c`），请比较 id：`it.customer?.id == c.id`（这里 `c` 是捕获的对象）。

示例代码：[`KotlinQueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)（`embedded-compare`、`safe-call-and`、`safe-call-or`、`safe-call-null`、`not-null-assert`、`safe-and-assert`）、[`ManualKotlinStartTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinStartTest.kt)（`join-path`）、[`ManualKotlinWhereTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt)（`null-guard`、`no-customer`）。

<a id="311-captured-values"></a>
## 3.11 捕获的值

Lambda 表达式可以使用外部的值：局部值、属性，以及捕获对象的属性。

<!-- region: ManualKotlinWhereTest.kt#captured-kinds -->
```kotlin
val since = LocalDateTime.of(2026, 1, 1, 0, 0)
val local = qb.from(Order::class.java).where { it.updateAt > since }                     // a local val
assertEquals("select e0 from Order e0 where e0.updateAt > :p1", local.toHql().hql())

val property = qb.from(Order::class.java).where { it.status == defaultStatus }           // a property of this class
assertEquals("select e0 from Order e0 where e0.status = :p1", property.toHql().hql())

val objectProperty = qb.from(Order::class.java).where { it.price > filter.minPrice!! }   // a property of a captured object
assertEquals("select e0 from Order e0 where e0.price > :p1", objectProperty.toHql().hql())

val call = qb.from(Order::class.java).where { it.updateAt > LocalDateTime.of(2026, 1, 1, 0, 0) }   // a call without the entity
assertEquals("select e0 from Order e0 where e0.updateAt > :p1", call.toHql().hql())
```

`defaultStatus` 是构建查询的类的属性（`Short`），`filter` 是捕获的 `PriceFilter` 对象。

| 捕获的内容 | lambdaQL 的使用方式 |
|---|---|
| `val`（`Long?`、`String` 等也一样） | 绑定参数 |
| `var` | 绑定参数。**每次执行查询时**读取其值 |
| 外围类的属性（`private` 也一样） | 每次执行查询时读取 |
| `companion object` 的 `val`、顶层 `val`、`object` 的 `val` | 绑定参数 |
| `const val` | 作为字面量写入 HQL |
| 在 Lambda 表达式内部写入捕获的变量（`n++`） | **拒绝** |

规则：

- 不使用实体的部分会在**每次执行查询时**在应用程序中计算。结果成为绑定参数。
- Lambda 表达式只分析一次，结果会被缓存。每次调用时都会读取新的捕获值。
- 只有实体部分会变成 SQL。

示例代码：[`ManualKotlinWhereTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt)（`captured-kinds`）。

<a id="312-errors-that-depend-on-order-of-evaluation"></a>
## 3.12 取决于求值顺序的错误

SQL 不保证 `AND`/`OR` 从左到右求值。因此像下面这样的“守卫”在 SQL 中并不安全。
lambdaQL 不拒绝这种守卫，而是按原样写入 HQL。如果数据库先计算除法再计算 `e0.qty <> 0`，可能出现除以零错误。

<!-- region: ManualKotlinWhereTest.kt#division-guard -->
```kotlin
// lambdaQL sends the guard as written; SQL does not promise to check qty <> 0 first
val q = qb.from(Item::class.java).where { it.qty != 0 && it.price!! / it.qty > 2 }
assertEquals("select e0 from Item e0 where e0.qty <> 0 and (e0.price / e0.qty) > 2.0", q.toHql().hql())

// the same rows when qty is positive, without a division
val safe = qb.from(Item::class.java).where { it.qty > 0 && it.price!! > 2.0 * it.qty }
assertEquals("select e0 from Item e0 where e0.qty > 0 and e0.price > (2.0 * e0.qty)", safe.toHql().hql())
```

请不用除法来写条件。`qty` 为正数时，第二个查询返回与第一个查询相同的行。

示例代码：[`ManualKotlinWhereTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt)（`division-guard`）。

<a id="313-what-is-rejected"></a>
## 3.13 会被拒绝的内容

分析失败时，lambdaQL 会抛出带有错误码 `[LQL-xxxx]` 和源代码行号的异常。
大多数写法在分析 Lambda 表达式时（首次使用时）被发现。有问题的捕获值（调用了方法的 `null`、NaN、含有 `null` 元素的集合）在查询执行时被发现。
见 [11. 错误与语言](../11-errors-and-languages.md)。

| 写法 | 示例 | 解决方法 |
|---|---|---|
| 局部 `val`/`var`、循环、包含多条语句的代码块 | `{ val x = 1; it.status == x }` | 写成一个表达式 |
| 一般的 `if (c) x else y` | | 使用 [3.5](#35-and-or-not) 中允许的写法 |
| 中缀 `and`、`or`、`xor` | `it.isActive and (it.price > 100.0)` | 使用 `&&`、`\|\|` |
| Lambda 表达式中的字符串模板或 `+`（`LQL-2007`） | `it.description == "A" + it.status` | 在 Lambda 表达式之前构建字符串并捕获 |
| 在 Lambda 表达式中创建的集合 | `it.status in listOf(1, 2)` | 在外部创建并捕获 |
| Lambda 表达式中的 Lambda 表达式 | `ids.any { id -> id == it.id }` | 使用 `in`（[3.9](#39-in-with-a-captured-collection)）或子查询 |
| Kotlin 字符串函数和其他标准库函数（`LQL-3008`）。支持 `startsWith`、`endsWith`、`contains`（[3.8](#38-strings)） | `it.description?.trim()`、`isBlank()`、`lowercase()` | 直接比较 |
| 编译为 `switch` 的 `when`（`LQL-2010`） | `when (it.status.toInt()) { 1, 2 -> true; else -> false }` | 用 `\|\|` 连接的比较（[3.15](#315-when-kotlin-only)） |
| 需要区间对象的区间（`LQL-3008`） | `it.customer?.grade in 1..5`、`LocalDateTime` 区间 | 写成两个比较（[3.14](#314-ranges-kotlin-only)） |
| 自己写的非 inline 扩展函数 | `it.isOpen()` | 改为 `inline fun`，或直接写出条件 |
| 实体上的其他方法 | `it.calcSomething()` | 只使用属性 |
| 实体集合路径 | `it.items.size > 0` | `exists()` 子查询 |
| 实体与实体比较 | `it.customer == c` | 比较 id |
| 值对象的大小比较、与其他类型的比较（`LQL-3012`） | `it.address > a`（`Address` 为 `Comparable` 时） | 比较字段。支持 `==`、`!=` |
| 捕获的 `NaN` / `Infinity` 值 | `it.price!! > Double.NaN` | 不要发送 NaN |
| `isNaN()` | `it.price!!.isNaN()` | – |
| 写入捕获的变量 | `{ n++; it.isActive }` | 在 Lambda 表达式外部计算 |
| 匿名对象、函数类型的值 | 用 `val f: (Order) -> Boolean` 调用 `where(f)` | 传入 Lambda 表达式字面量 |

异常类型（都继承自 `LambdaQLException`）：

| 异常 | 何时抛出 |
|---|---|
| `LambdaAnalysisException` | 无法读取 Lambda 表达式时（不是可序列化的 Lambda 表达式、被编译为类等） |
| `UnsupportedLambdaShapeException` | 不支持的写法（循环、有主体的 `when` 的 `switch` 等） |
| `TranslationException` | 无法翻译其含义时（未知函数、集合路径等） |

所有 Kotlin 限制：[9. Kotlin 说明](09-kotlin-notes.md#914-kotlin-limits-kc1-to-kc21)。所有规则：[10. 规则与限制](10-rules-and-limits.md)。

<a id="314-ranges-kotlin-only"></a>
## 3.14 区间（仅限 Kotlin）

Kotlin 编译器把以不可为 NULL 的数字和字符串为主体的区间编译为两个比较。lambdaQL 按原样写出这些比较（不是 `between`）。
对于上界为常量的 `a..b`，编译器会改写为 `< b+1`。例如 `in 1..2` 变为 `e0.status < 3`。

<!-- region: ManualKotlinWhereTest.kt#ranges -->
```kotlin
val closed = qb.from(Order::class.java).where { it.status.toInt() in 1..2 }
assertEquals("select e0 from Order e0 where 1 <= e0.status and e0.status < 3", closed.toHql().hql())

val outside = qb.from(Order::class.java).where { it.status.toInt() !in 1..2 }
assertEquals("select e0 from Order e0 where 1 > e0.status or e0.status >= 3", outside.toHql().hql())

val until = qb.from(Order::class.java).where { it.status.toInt() in 1 until 3 }
assertEquals("select e0 from Order e0 where 1 <= e0.status and e0.status < 3", until.toHql().hql())

val open = qb.from(Order::class.java).where { it.status.toInt() in 1..<3 }
assertEquals("select e0 from Order e0 where 1 <= e0.status and e0.status < 3", open.toHql().hql())

val captured = qb.from(Order::class.java).where { it.cnt in lo..hi }                  // lo, hi: captured values
assertEquals("select e0 from Order e0 where :p1 <= e0.cnt and e0.cnt <= :p2", captured.toHql().hql())

val prices = qb.from(Order::class.java).where { it.price in 100.0..200.0 }
assertEquals("select e0 from Order e0 where 100.0 <= e0.price and e0.price <= 200.0", prices.toHql().hql())

val texts = qb.from(Order::class.java).where { it.description in "a".."m" }
assertEquals("select e0 from Order e0 where e0.description >= 'a' and e0.description <= 'm'", texts.toHql().hql())
```

| 区间写法 | 是否支持 |
|---|---|
| `a..b`、`a until b`、`a..<b`（主体为 `Int`、`Double`、`String`） | 是。两个比较 |
| `!in` | 是。反转比较并用 `or` 连接 |
| 捕获值作边界（`lo..hi`） | 是。绑定参数 |
| 可空主体（`Int?`）、`LocalDate`·`LocalDateTime` 区间 | 否（`LQL-3008`） |
| `downTo`、`step` | 是。展开值并以 `in (:p1)` 绑定 |

**可能为 NULL 的主体。** Java 实体的 `Integer` 属性（`cnt`）是平台类型，编译器把它编译为两个比较。lambdaQL **不会**添加 null 守卫，因此 `cnt` 为 NULL 的行不会被返回（SQL 语义）。
通过 `?.` 得到的 `Int?` 主体和日期区间会被编译为区间对象，因此会被拒绝。请写成两个比较。

<!-- region: ManualKotlinWhereTest.kt#range-rejected -->
```kotlin
// a range object is not translated: a nullable subject, or a range of dates
val nullable = assertThrows<LambdaQLException> {
    qb.from(Order::class.java).where { it.customer?.grade in 1..5 }.toHql()
}
assertTrue(nullable.message!!.startsWith("[LQL-3008]"))

val dates = assertThrows<LambdaQLException> {
    qb.from(Order::class.java).where { it.updateAt in from..to }.toHql()
}
assertTrue(dates.message!!.startsWith("[LQL-3008]"))

// write the two comparisons instead
val q = qb.from(Order::class.java).where { it.updateAt >= from && it.updateAt <= to }
assertEquals("select e0 from Order e0 where e0.updateAt >= :p1 and e0.updateAt <= :p2", q.toHql().hql())
```

示例代码：[`ManualKotlinWhereTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt)（`ranges`、`range-rejected`）。

<a id="315-when-kotlin-only"></a>
## 3.15 `when`（仅限 Kotlin）

`when` **作为条件**时受支持。每个分支都必须以 `true` 或 `false`（或一个条件）结尾。
lambdaQL 会把它转换为比较、`OR` 和 `AND`。

<!-- region: ManualKotlinWhereTest.kt#when -->
```kotlin
val twoTexts = qb.from(Order::class.java).where { when (it.description) { "a", "b" -> true; else -> false } }
assertEquals("select e0 from Order e0 where e0.description = 'a' or e0.description = 'b'", twoTexts.toHql().hql())

val oneValue = qb.from(Order::class.java).where { when (it.status.toInt()) { 1 -> true; else -> false } }
assertEquals("select e0 from Order e0 where e0.status = 1", oneValue.toHql().hql())

val range = qb.from(Order::class.java).where { when (it.status.toInt()) { in 1..2 -> true; else -> false } }
assertEquals("select e0 from Order e0 where 1 <= e0.status and e0.status < 3", range.toHql().hql())

val condition = qb.from(Order::class.java).where { when (it.status.toInt()) { 1 -> it.isActive; else -> false } }
assertEquals("select e0 from Order e0 where e0.status = 1 and e0.active = true", condition.toHql().hql())
```

主体为 NULL 的行不会被返回。如果执行 Kotlin 代码，会进入 `else` 分支，但 lambdaQL 使用 SQL 语义。

编译器把 `when` 编译为 `switch` 时，目前还不支持（`LQL-2010`）。常量有两个及以上的 `Int` 主体、常量有三个及以上的 `String` 主体属于这种情况。enum 主体也不支持。
这类条件请写成用 `||` 连接的比较，或使用捕获集合的 `in`（[3.9](#39-in-with-a-captured-collection)）。
用实体值选择值的无主体 `when { }` 会成为 `case when`（见 [3.5.1](#351-values-chosen-by-a-condition)）。

<!-- region: ManualKotlinWhereTest.kt#when-rejected -->
```kotlin
// when compiled to a switch is not supported yet (LQL-2010): two or more Int values, three or more strings
val ints = assertThrows<LambdaQLException> {
    qb.from(Order::class.java).where { when (it.status.toInt()) { 1, 2 -> true; else -> false } }.toHql()
}
assertTrue(ints.message!!.startsWith("[LQL-2010]"))

val texts = assertThrows<LambdaQLException> {
    qb.from(Order::class.java).where { when (it.description) { "a", "b", "c" -> true; else -> false } }.toHql()
}
assertTrue(texts.message!!.startsWith("[LQL-2010]"))

// an enum subject is not supported either
assertThrows<LambdaQLException> {
    qb.from(Order::class.java).where { when (it.kind) { Order.Kind.RUSH -> true; else -> false } }.toHql()
}
```

请改用 `&&` 和 `||` 来写条件。

示例代码：[`ManualKotlinWhereTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt)（`when`、`when-rejected`）。

---

[Kotlin 路线](README.md) | 上一章：[2. 入门](02-getting-started.md) | 下一章：[4. 排序与分页](04-order-paging.md) | [本页的 Java 版本](../java/03-where.md)
