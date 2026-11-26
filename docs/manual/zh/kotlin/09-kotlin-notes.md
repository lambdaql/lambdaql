<a id="9-kotlin-notes"></a>
# 9. Kotlin 说明

> [!IMPORTANT]
> 本页比韩文手册旧。最新规则请参见[韩文页面](../../ko/kotlin/09-kotlin-notes.md)。

[Kotlin 路线](README.md) | [手册首页](../README.md) | 上一章：[8. 存储过程与数据库函数](08-procedures-functions.md) | 下一章：[10. 规则与限制](10-rules-and-limits.md) | [本页的 Java 版本：9. Java 说明](../java/09-java-notes.md)

公共页面：[1. 概述](../01-overview.md) · [11. 错误与语言](../11-errors-and-languages.md)

lambdaQL 用**同一个引擎**和同一套 API 支持 Kotlin 和 Java。
含义相同的 Kotlin Lambda 表达式和 Java Lambda 表达式会生成相同的 HQL。
本章把 Kotlin 的特别之处集中在一处。

<a id="contents"></a>
## 目录

- [9.1 配置](#91-setup)
- [9.2 可用的 Lambda 表达式写法](#92-lambda-forms-that-work)
- [9.3 自己写的 null 检查与编译器的 null 检查](#93-only-your-null-checks-count)
- [9.4 相等比较：`==`、`!=`、`===` 和 `areEqual`](#94-equality----and-areequal)
- [9.5 null 运算符：`?.`、`?:` 和 `!!`](#95-null-operators---and-)
- [9.6 `in`、区间和 `when`](#96-in-ranges-and-when)
- [9.7 字符串](#97-strings)
- [9.8 捕获值](#98-captured-values)
- [9.9 属性引用与作用域函数](#99-property-references-and-scope-functions)
- [9.10 Kotlin 运算符](#910-kotlin-operators)
- [9.11 结果：data class、`Pair` 和 `Triple`](#911-results-data-classes-pair-and-triple)
- [9.12 更新、插入、删除：属性赋值和 `Dml.value`](#912-update-insert-delete-property-assignment-and-dmlvalue)
- [9.13 存储过程与数据库函数](#913-procedures-and-db-functions)
- [9.14 Kotlin 限制（KC1 到 KC21）](#914-kotlin-limits-kc1-to-kc21)
- [9.15 lambdaQL 与直接运行 Kotlin 代码的不同之处](#915-where-lambdaql-differs-from-running-kotlin-code)

如何阅读示例：

- 示例中 `assertEquals` 的字符串是 lambdaQL 生成的 HQL。
- 直接写在 Lambda 表达式中的常量（数字、字符串、布尔值、`null`、enum 常量、`const val`）作为字面量写入 HQL。捕获的值和计算出的值始终是绑定参数（`:p1`、`:p2`、...）。
- `e0` 是根实体的别名，`j1` 是 lambdaQL 为关联路径添加的 join 的别名。在 `where` 中，如果没有关联的行可能使条件为真，就是 `left join`，否则是内连接 `join`。路径只在 `orderBy` 或 `select` 中使用时是 `left join`。子查询的根是 `s1`、`s2`、...（见 [5. 子查询](05-subquery.md)）。
- 实体是用 Java 编写的示例实体（`Order`、`Customer`、`Item`、`Coded`）。Kotlin 把 Java getter 视为平台类型的属性。
  `Order.status` 是 `short`，因此条件写成 `it.status.toInt() == 1`；布尔属性 `active` 用 `it.isActive` 读取。可能为 NULL 的 Boolean 示例使用 `Coded.vip`。

---

<a id="91-setup"></a>
## 9.1 配置

| 项目 | 值 |
|---|---|
| Kotlin | 2.x。设计已用 kotlinc 1.7.22、2.1.10、2.2.21、2.3.0 和 2.3.21 检查过 |
| JVM | Java 21 及以上 |
| 编译器选项 | 保留默认的 `-Xsam-conversions=indy`。**不要**使用 `-Xsam-conversions=class` |
| `-jvm-target` | 1.8、11、17 和 21 都可以 |
| Kotlin 运行时 | lambdaQL 不会把 `kotlin-stdlib` 或 `kotlin-reflect` 加入依赖 |
| JPA 实体 | 普通的 Kotlin JPA 实体（注解加在字段上也可以） |

详情：[2. 入门](02-getting-started.md#kotlin-compiler-settings)。

<a id="92-lambda-forms-that-work"></a>
## 9.2 可用的 Lambda 表达式写法

lambdaQL 的函数接收 Java 的 `Serializable` 函数式接口。
在 Kotlin 中传入 **Lambda 字面量**。请使用尾随 Lambda 表达式和 `it`。

<!-- region: ManualKotlinNotesTest.kt#where-chain -->
```kotlin
val q = qb.from(Order::class.java)
    .where { it.status.toInt() == 1 }
    .where { it.price > 100.0 }        // more where() calls are joined with AND
assertEquals("select e0 from Order e0 where e0.status = 1 and e0.price > 100.0", q.toHql().hql())
```

**各数据库的 SQL**

<!-- sql: to-hql -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>100.0
```

有两个或更多参数的 Lambda 表达式使用名字。不需要的参数请用 `_`。

<!-- region: ManualKotlinNotesTest.kt#named-params -->
```kotlin
// lambdas with two parameters use names
val q = qb.from(Order::class.java)
    .where { o, sub -> sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
    .orderBy { o -> desc(o.price).asc(o.id) }
assertEquals("select e0 from Order e0 where exists (select 1 from Item s1 where s1.order.id = e0.id)"
    + " order by e0.price desc, e0.id asc", q.toHql().hql())
```

Kotlin 属性通过 getter 读取。`it.price` 与 Java 调用 `getPrice()` 是相同的字节码 opcode。
lambdaQL 把 getter 映射到 JPA 属性。
Java 实体的布尔属性 `active` 的 getter 是 `isActive()`，因此在 Kotlin 中用 `it.isActive` 读取。lambdaQL 把它映射到 `active`。

| 写法 | 可用吗？ |
|---|---|
| `where { it.isActive }`（Lambda 字面量） | 可用 |
| 位于 `run { }`、`let { }`、`forEach { }`、非 inline 函数、`suspend` 函数中的 Lambda 表达式 | 可用 |
| 由调用方传给 lambdaQL 类型的 `inline fun` 参数的 Lambda 表达式 | 可用 |
| SAM 构造器，也包括先存入 `val` 的情况 | 可用 |
| 传给条件的属性引用或 Java getter 引用：`where(Order::isActive)` | **不可用**（`LQL-1009`）。请使用 Lambda 表达式 |
| `val f: (Order) -> Boolean = { it.isActive }; where(f)` | **不可用** |
| `object : ...`（用对象表达式代替 Lambda 表达式） | **不可用** |
| 在 `inline fun` 主体内部创建的 Lambda 表达式 | **不可用** |
| 用 `-Xsam-conversions=class` 编译 | **不可用** |

示例代码：[`ManualKotlinNotesTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinNotesTest.kt)（`where-chain`、`named-params`）。

<a id="93-only-your-null-checks-count"></a>
## 9.3 自己写的 null 检查与编译器的 null 检查

lambdaQL 不调用 Lambda 表达式，而是分析字节码 opcode 来生成 HQL。
因此有些 Kotlin 规则的作用方式不同。

| 主题 | 规则 |
|---|---|
| 关联路径 | 像 `it.customer?.name` 这样的路径是**一个列**，而不是调用链。在 `where` 中，如果没有客户的订单可能使条件为真，对一关联就是 `left join`，否则是内连接。只查找没有客户的订单时，请写 `it.customer == null`。 |
| `?.` 与 join | 用 `?.` 经过的比较，会把没有客户的订单中客户的列视为 NULL，并按 SQL 语义求值。`it.status.toInt() == 1 \|\| it.customer?.name == "Kim"`、`it.customer?.name == null` 和 null 保护都可能使没有客户的订单满足条件，因此是 `left join`。`?.` 和 `!!` 可以在同一个条件中混用。 |
| `!!` | `!!` 不改变值。`it.customer!!.name` 和 `it.customer?.name` 是同一个列。但在 `where` 中，用 `!!` 经过关联步骤的比较只对有客户的订单为真（`it.customer!!.name == null` 是内连接）。 |
| `==`、`===` | 对象之间的 `==`（以及 `===`）表示**值比较**，而不是对象同一性。 |

把可能为 NULL 的数字属性（`Integer`）与常量比较时，Kotlin 编译器会在拆箱前插入 null 检查。
lambdaQL 不读取这个没有写在源代码中的检查，因此 SQL 语义与 Java 相同。
`it.cnt == 1` 是 `e0.cnt = 1`，`it.cnt != 1` 是 `e0.cnt <> 1`，`cnt` 为 NULL 的行不会被返回。需要 NULL 行时，请像 `it.cnt == null || it.cnt != 1` 这样自己写出来。

这种行为的示例见 [9.4](#94-equality----and-areequal)。

<a id="94-equality----and-areequal"></a>
## 9.4 相等比较：`==`、`!=`、`===` 和 `areEqual`

<a id="how-kotlin-compiles-"></a>
### Kotlin 如何编译 `==`

| Kotlin | 编译结果 | HQL |
|---|---|---|
| 两个非空基本类型：`it.qty == 5` | 比较分支（与 Java 相同） | `e0.qty = 5` |
| 可空基本类型与常量：`it.cnt == 1`（`Integer`） | null 检查 + 比较 | `e0.cnt = 1`（不读取 null 检查） |
| `it.cnt != 1`（`Integer`） | null 检查 + 比较 | `e0.cnt <> 1`（**不**返回 NULL 行） |
| 引用，或两侧都可空：`it.description == text`、`it.cnt == q`（`q: Int?`） | 调用 `Intrinsics.areEqual(a, b)` | `e0.description = :p1`（捕获值 `null`：没有匹配的行） |
| `it.description != "x"` | `!Intrinsics.areEqual(a, b)` | `e0.description <> 'x'`（**不**返回 NULL 行） |
| 两个实体值：`it.description == it.custName` | `Intrinsics.areEqual(a, b)` | `e0.description = e0.custName`（任一侧为 NULL：不匹配） |
| `it.id === q`、`!==` | `if_acmpeq` / `if_acmpne` | 与 `==` 相同 |
| enum：`it.kind == Order.Kind.RUSH` | `if_acmpne` | `e0.kind = org.lambdaql.query.Order$Kind.RUSH`（带完整类名的字面量） |

`Intrinsics.areEqual` 是 `==` 背后的 Kotlin 运行时函数。lambdaQL 按名字识别它，并把它理解为**值比较**。
实体值遵循 SQL 语义。任一侧为 NULL 时不匹配，NULL 与 NULL 也一样。
捕获值 `null` 作为 NULL 绑定，不匹配任何行（`WARN` 级别 `LQL-4045`）。要查找 NULL，请写 `x == null`。

<!-- region: ManualKotlinNotesTest.kt#hidden-null-check -->
```kotlin
// cnt is a nullable Integer: the null check Kotlin adds before the unboxing is not read (SQL meaning)
val one = qb.from(Order::class.java).where { it.cnt == 1 }
assertEquals("select e0 from Order e0 where e0.cnt = 1", one.toHql().hql())
val notOne = qb.from(Order::class.java).where { it.cnt != 1 }          // NULL rows are left out, as in SQL
assertEquals("select e0 from Order e0 where e0.cnt <> 1", notOne.toHql().hql())
// both sides nullable: Intrinsics.areEqual, a value comparison
val same = qb.from(Order::class.java).where { it.cnt == q }
assertEquals("select e0 from Order e0 where e0.cnt = :p1", same.toHql().hql())
// two entity values: NULL on either side does not match
val columns = qb.from(Order::class.java).where { it.description == it.custName }
assertEquals("select e0 from Order e0 where e0.description = e0.custName", columns.toHql().hql())
```

<a id="plain--and-"></a>
### 普通的 `=` 和 `<>`

对于编译为 `Intrinsics.areEqual` 的比较，lambdaQL 写出普通的 `=` 和 `<>`。
它不写 `is [not] distinct from`。这种形式在 Oracle 上会变成 `decode(...)`，无法使用索引。

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

> [!WARNING]
> **Kotlin 的非空类型并不能作为证明。** `var code: String` 属性仍可能包含来自数据库的 NULL。
> 因为 Hibernate 会不经检查地把列值写入字段。
> lambdaQL 看的是 JPA 元数据，而不是 Kotlin 类型。

<a id="-and-"></a>
### `===` 和 `!==`

在 lambdaQL 中，`===` 与 `==` 一样表示**值比较**。
查询中没有对象同一性。对于两个值都是 1000 的不同 `Long` 装箱对象，Kotlin 本身给出 `false`，lambdaQL 给出 `true`。

<a id="bigdecimal"></a>
### `BigDecimal`

在 Kotlin 中，`BigDecimal` 上的 `==` 调用 `equals`，它还会比较标度（`1.0 != 1.00`）。
SQL 的 `=` 只比较值。**lambdaQL 使用值比较。**
支持 `BigDecimal`、`String`、`LocalDate` 和 `BigInteger` 上的 `<`、`>`、`<=`、`>=`（它们编译为 `compareTo`）。

<a id="booleans"></a>
### 布尔值

<!-- region: ManualKotlinNotesTest.kt#booleans -->
```kotlin
val active = qb.from(Order::class.java).where { it.isActive }
assertEquals("select e0 from Order e0 where e0.active = true", active.toHql().hql())
val inactive = qb.from(Order::class.java).where { !it.isActive }
assertEquals("select e0 from Order e0 where e0.active = false", inactive.toHql().hql())
```

`!it.isActive` 变为 `e0.active = false`。

<a id="java-entities-from-kotlin-platform-types"></a>
### 从 Kotlin 使用 Java 实体（平台类型）

如果实体是 Java 类，Kotlin 会把它的 getter 视为平台类型（`Long!`）。
本章的示例都属于这种情况。路径 `it.customer?.name` 是连接的列（`where` 中的 join 种类见 [3.10](03-where.md#310-association-paths)）。

示例代码：[`ManualKotlinNotesTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinNotesTest.kt)（`hidden-null-check`、`equality`、`booleans`）。

<a id="95-null-operators---and-"></a>
## 9.5 null 运算符：`?.`、`?:` 和 `!!`

<a id="summary"></a>
### 摘要

| Kotlin | 在 lambdaQL 中的含义 | HQL 示例 |
|---|---|---|
| `it.customer?.name` | 列 `customer.name`。在没有客户的订单中按 NULL 列比较（SQL 语义） | `j1.name` + `join` 或 `left join e0.customer j1` |
| `it.customer!!.name` | 同一个列。比较只对有客户的订单为真 | `j1.name` + `join` 或 `left join e0.customer j1` |
| `it.customer?.id == 1` | 路径以目标的 `@Id` 结尾：外键列，不需要 join | `e0.customer is not null and e0.customer.id = 1` |
| `it.customer == null` | 只取没有客户的订单 | `e0.customer is null`（外键列，不需要 join） |
| `it.customer != null` | 只取有客户的订单 | `e0.customer is not null`（外键列，不需要 join） |
| `it.description ?: ""`（值） | 默认值 | `coalesce(e0.description, '')` |
| `it.vip ?: false`（条件） | NULL 视为 false | `e0.vip is not null and e0.vip = true` |
| `lo!!`（捕获值） | `!!` 被忽略。`lo` 为 null 时绑定 NULL，因此没有匹配的行（`DEBUG` 级别 `LQL-4045`，见 [9.8](#98-captured-values)） | `:p1` |

<a id="-and--on-association-paths"></a>
### 关联路径上的 `?.` 和 `!!`

<!-- region: ManualKotlinNotesTest.kt#path-forms -->
```kotlin
// ?. and !! on a path: the same column; no order without a customer can match, so both are an inner join
val safe = qb.from(Order::class.java).where { it.customer?.name == "Kim" }
assertEquals("select e0 from Order e0 join e0.customer j1 where j1.name = 'Kim'", safe.toHql().hql())
val sure = qb.from(Order::class.java).where { it.customer!!.name == "Kim" }
assertEquals("select e0 from Order e0 join e0.customer j1 where j1.name = 'Kim'", sure.toHql().hql())
// a path that ends at the @Id of the target: the FK column, no join
val fk = qb.from(Order::class.java).where { it.customer?.id == 1 }
assertEquals("select e0 from Order e0 where e0.customer is not null and e0.customer.id = 1", fk.toHql().hql())
```

**各数据库的 SQL**

<!-- sql: path-literal -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name='Kim'
```

路径是**一个列**，而不是调用链。上面的条件不可能与没有客户的订单匹配，因此 `?.` 和 `!!` 都是内连接。
所以不需要在路径前面写 `it.customer != null &&`。
`it.customer?.id == 1` 直接使用 Kotlin 插入的 null 检查（`e0.customer is not null`），但没有 join。

**用 `?.` 写的条件会像 Kotlin 一样保留没有客户的订单。** 在 Kotlin 中，没有客户时 `it.customer?.name == null` 为 `true`。
lambdaQL 把这个条件写成 `left join`，因此也会返回没有客户的订单。用 `!!` 写的比较只对有客户的订单为真，因此是内连接，只返回客户没有名字的订单。

<!-- region: ManualKotlinNotesTest.kt#null-paths -->
```kotlin
// orders without a customer: the FK column, no join
val none = qb.from(Order::class.java).where { it.customer == null }
assertEquals("select e0 from Order e0 where e0.customer is null", none.toHql().hql())
// orders without a customer, and orders of a customer without a name: a left join
val safe = qb.from(Order::class.java).where { it.customer?.name == null }
assertEquals("select e0 from Order e0 left join e0.customer j1 where j1.name is null", safe.toHql().hql())
// only orders of a customer without a name: the inner join leaves out orders without a customer
val sure = qb.from(Order::class.java).where { it.customer!!.name == null }
assertEquals("select e0 from Order e0 join e0.customer j1 where j1.name is null", sure.toHql().hql())
```

**各数据库的 SQL**

<!-- sql: no-association -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id is null
```

<a id="-is-just-for-the-compiler"></a>
### `!!` 只是给编译器看的

当属性是 `Double?` 时，Kotlin 不会编译 `it.total > 10.0`。
请写 `it.total!! > 10.0`。lambdaQL 会忽略 `!!`。lambdaQL 不调用 Lambda 表达式，因此不会发生 NPE。
值为 NULL 的行不会被返回（SQL 规则）。

<a id="-elvis"></a>
### `?:`（elvis）

在 select 值和字符串条件中，`?:` 变为 `coalesce`。

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

- 数字条件中的 `?:` 也是 `coalesce`：`(it.total ?: 0.0) > 3.0` 是 `coalesce(e0.total, 0.0) > 3.0`。
- 把 `?.` 之后的 `?:` 用作 select 值会被拒绝（`LQL-2022`）。
- 列外面包了 `coalesce`，因此不会使用 `description` 上的普通索引。
- DML 值中的 `?:` 请参见 [7.2](07-2-update.md#add-to-a-value-default-for-null)。

**布尔 elvis** 在**条件中**受支持。

<!-- region: ManualKotlinNotesTest.kt#boolean-elvis -->
```kotlin
// Coded.vip is a nullable Boolean: in a condition, ?: false treats NULL as false
val vip = qb.from(Coded::class.java).where { it.vip ?: false }
assertEquals("select e0 from Coded e0 where e0.vip is not null and e0.vip = true", vip.toHql().hql())
val notVip = qb.from(Coded::class.java).where { !(it.vip ?: false) }
assertEquals("select e0 from Coded e0 where e0.vip is null or e0.vip = false", notVip.toHql().hql())
```

把布尔 elvis 用作**值或排序键**（`select { it.vip ?: false }`）不受支持（`LQL-2022`）。
排序时请使用键 `it.vip`，并像 `asc(key, Nulls.LAST)` 或 `desc(key, Nulls.FIRST)` 这样用 `Nulls` 选择 NULL 的位置（[4. 排序与分页](04-order-paging.md#43-direction)）。

<a id="-in-an-assignment"></a>
### 赋值中的 `?.`

在 `set { }` 和 `values { }` 中，`it.customer?.id = cid` **总是会赋值**。`?.` 被忽略。示例见 [7.2](07-2-update.md#change-an-association-foreign-key)。

示例代码：[`ManualKotlinNotesTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinNotesTest.kt)（`path-forms`、`null-paths`、`elvis-value`、`boolean-elvis`）。

<a id="96-in-ranges-and-when"></a>
## 9.6 `in`、区间和 `when`

详情和更多示例：[3.9](03-where.md#39-in-with-a-captured-collection)、[3.14](03-where.md#314-ranges-kotlin-only)、[3.15](03-where.md#315-when-kotlin-only)。

| Kotlin | HQL | 说明 |
|---|---|---|
| `it.id in ids`（捕获的集合） | `e0.id in (:p1)` | 为空时没有匹配的行。`null` 元素会报错（`LQL-4008`） |
| `it.id !in ids` | `e0.id not in (:p1)`。可能为 NULL 的列也一样（不返回 NULL 行） | |
| `it.status.toInt() in 1..5` | `1 <= e0.status and e0.status < 6` | |
| `it.status.toInt() !in 1..5` | `1 > e0.status or e0.status >= 6` | 不返回 NULL 行 |
| `in 1 until 5`、`in 1..<5` | `1 <= e0.status and e0.status < 5` | |
| `it.status.toInt() in lo..hi` | `:p1 <= e0.status and e0.status <= :p2` | |
| `when { a -> true; b -> true; else -> false }` | `a or b` | |
| `when (it.status.toInt()) { 1, 2 -> true; else -> false }` | **拒绝**（`LQL-2010`） | 用 `\|\|` 编写 |
| `it.customer?.id in sub.from(...).select { ... }` | `e0.customer.id in (select ...)` | 子查询，见 [5.4](05-subquery.md#54-in) |
| `x !in sub.from(...).select { ... }` | `x not in (select ...)`（SQL 语义） | 见 [5.5](05-subquery.md#55-not-in-is-written-as-not-exists) |
| `it.id in listOf(1L, 2L)`（在 Lambda 表达式中创建的列表） | **拒绝**（`LQL-3025`） | 在外部创建列表 |

<!-- region: ManualKotlinNotesTest.kt#ranges -->
```kotlin
val closed = qb.from(Order::class.java).where { it.status.toInt() in 1..2 }
assertEquals("select e0 from Order e0 where 1 <= e0.status and e0.status < 3", closed.toHql().hql())
val outside = qb.from(Order::class.java).where { it.status.toInt() !in 1..2 }
assertEquals("select e0 from Order e0 where 1 > e0.status or e0.status >= 3", outside.toHql().hql())
val until = qb.from(Order::class.java).where { it.status.toInt() in 1 until 3 }
assertEquals("select e0 from Order e0 where 1 <= e0.status and e0.status < 3", until.toHql().hql())
val captured = qb.from(Order::class.java).where { it.status.toInt() in lo..hi }
assertEquals("select e0 from Order e0 where :p1 <= e0.status and e0.status <= :p2", captured.toHql().hql())
// a captured collection
val inIds = qb.from(Order::class.java).where { it.id in ids }
assertEquals("select e0 from Order e0 where e0.id in (:p1)", inIds.toHql().hql())
// a nullable target gets no null guard: NULL rows are returned by neither in nor !in (SQL)
val notIn = qb.from(Order::class.java).where { it.cnt !in counts }
assertEquals("select e0 from Order e0 where e0.cnt not in (:p1)", notIn.toHql().hql())
// when without a subject, with true / false branches: OR
val anyOf = qb.from(Order::class.java).where { when { it.status.toInt() == 3 -> true; it.price > 200.0 -> true; else -> false } }
assertEquals("select e0 from Order e0 where e0.status = 3 or e0.price > 200.0", anyOf.toHql().hql())
```

**可空主体不会加 null 保护**。Kotlin 定义 `null in 1..5` 为 false，`null !in 1..5` 为 true。
lambdaQL 不添加保护。NULL 主体既不会被 `in` 返回，**也**不会被 `!in` 返回（SQL 规则）。
需要 NULL 行时，请写 `it.cnt == null || it.cnt !in 1..5`。

对捕获集合使用 `!in` 也一样。上面示例中的 `x not in (:p1)` 不返回 NULL 行（SQL 语义）。

示例代码：[`ManualKotlinNotesTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinNotesTest.kt)（`ranges`）。

<a id="97-strings"></a>
## 9.7 字符串

实体值上的 `startsWith`、`endsWith`、`contains`（包括 `ignoreCase`）变为 `like`（[3.8](03-where.md#38-strings)）。
其他字符串函数会在 lambdaQL 读取 Lambda 表达式时以 `TranslationException`（`LQL-3008`）拒绝。`lowercase`、`length`、`isNullOrEmpty`、`isBlank`、`equals(s, ignoreCase = true)` 都属于这一类。
条件请写成直接比较值的形式。

| Kotlin | lambdaQL |
|---|---|
| `it.description.equals(text)` | 与 `==` 相同：`e0.description = :p1` |
| `(it.description ?: "") == "x"` | `coalesce(e0.description, '') = 'x'`（[9.5](#-elvis)） |
| 上述字符串函数 | 拒绝（`LQL-3008`） |
| 字符串拼接和模板（`"${it.description}-x"`、`"$a-$b" == it.description`、`it.description + "x"`） | 拒绝（`LQL-2007`）。只用捕获值的模板也会被拒绝，因此请在 Lambda 表达式外部构建字符串再捕获 |

<a id="98-captured-values"></a>
## 9.8 捕获值

| 捕获的内容 | lambdaQL 如何使用 |
|---|---|
| `val`（`Long?`、`String`、... 也一样） | 绑定参数 |
| `var` | 绑定参数。**每次执行查询时**都会读取该值 |
| 外围类的属性（`this.minStatus`，`private` 也一样） | 每次执行查询时读取 |
| `companion object` 的 `val`、顶层 `val`、`object` 的 `val` | 绑定参数 |
| `const val` | 作为字面量写入 HQL |
| 在 Lambda 表达式中写入捕获的变量（`n++`） | **拒绝** |

<!-- region: ManualKotlinNotesTest.kt#order-repo -->
```kotlin
class OrderRepo(private val qb: QueryBuilder) {
    private val minStatus = 2

    fun open(lo: Int?) = qb.from(Order::class.java)
        .where { it.status >= minStatus }                // a private property: read each time the query runs
        .where { lo == null || it.status > lo }          // lo == null: no filter
}
```

<!-- region: ManualKotlinNotesTest.kt#order-repo-use -->
```kotlin
val all = repo.open(null)
val above = repo.open(2)
assertEquals("select e0 from Order e0 where e0.status >= :p1", all.toHql().hql())
assertEquals("select e0 from Order e0 where e0.status >= :p1 and e0.status > :p2", above.toHql().hql())
```

捕获值 `null` 在同一个值的 null 保护之下（`lo == null || ...`）是允许的。这个保护在构建查询时由应用程序决定。
`lo == null` 时这个 `where` 不添加任何过滤；有值时变为 `e0.status > :p2`。

用在 `==`、`!=` 中的捕获值 `null` 作为 NULL 绑定，因此没有匹配的行（`WARN` 级别 `LQL-4045`）。
没有保护的大小比较（`lo == null` 时的 `it.status > lo!!`）绑定 NULL，因此没有匹配的行（`DEBUG` 级别 `LQL-4045`）。`lo!!` 和拆箱不会执行。
对捕获值调用方法的表达式（`text!!.trim()` 等）在值为 `null` 时，会在计算值时抛出错误（`LQL-3027`）。

<!-- region: ManualKotlinNotesTest.kt#captured-null -->
```kotlin
// a captured null in == is bound as NULL: no row matches
val text: String? = null
val none = qb.from(Order::class.java).where { it.description == text }
assertEquals("select e0 from Order e0 where e0.description = :p1", none.toHql().hql())
assertEquals(0, none.toList().size)
// a method call on a captured null fails when the value is computed
val error = assertThrows(LambdaQLException::class.java) {
    qb.from(Order::class.java).where { it.description == text!!.trim() }.toList()
}
assertEquals("LQL-3027", error.code())
```

示例代码：[`ManualKotlinNotesTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinNotesTest.kt)（`order-repo`、`order-repo-use`、`captured-null`）。

<a id="99-property-references-and-scope-functions"></a>
## 9.9 属性引用与作用域函数

<a id="property-references"></a>
### 属性引用

| 写法 | 可用吗？ |
|---|---|
| 传给条件的 Kotlin 属性引用或 Java getter 引用（`where(Order::isActive)`） | 不可用（`LQL-1009`）。请使用 Lambda 表达式 `where { it.isActive }` |
| Java 合成属性 `JOrder::price` | 不可用。Kotlin 本身无法编译它。 |
| `Order::price.getter` 及其他 `kotlin-reflect` API | 不可用 |
| 传给自己定义的 Kotlin `fun interface` 的引用 | 不可用。请传入 Lambda 字面量 |

<a id="scope-functions-and-inline-functions-in-conditions"></a>
### 条件中的作用域函数和 inline 函数

自己定义的 `inline fun` 可以在条件中使用。编译器会把函数主体复制到 Lambda 表达式中，所以 lambdaQL 看到的是普通条件。

<!-- region: ManualKotlinNotesTest.kt#inline-fun -->
```kotlin
/** Your own inline function: the compiler copies the body into the lambda, so lambdaQL reads a plain condition. */
private inline fun Order.isOpenInline() = status.toInt() == 1
```

<!-- region: ManualKotlinNotesTest.kt#scope-functions -->
```kotlin
val inline = qb.from(Order::class.java).where { it.isOpenInline() }     // inline fun Order.isOpenInline()
assertEquals("select e0 from Order e0 where e0.status = 1", inline.toHql().hql())
```

| 写法 | 是否支持 |
|---|---|
| 自己定义的 `inline fun` | 支持 |
| `?.let { }`、`run { }`、`with(it) { }`、`also { }` | 不支持（`LQL-2013`）。请直接写出条件 |
| 自己定义的非 inline 扩展函数或扩展属性 | 不支持。把它改为 `inline`，或直接写出条件 |
| `takeIf { }` | 不支持（`LQL-2021`） |

在更新和插入的 Lambda 表达式中，`apply`、`with` 和 `also` 会被拒绝（`LQL-2003`，见 [9.12](#912-update-insert-delete-property-assignment-and-dmlvalue)）。

示例代码：[`ManualKotlinNotesTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinNotesTest.kt)（`inline-fun`、`scope-functions`）。

<a id="910-kotlin-operators"></a>
## 9.10 Kotlin 运算符

此表来自对 126 种运算符写法的编译器检查结果（kotlinc 2.3.21，设计文档 KOTLIN.md 第 9 节）。

| 运算符 | 在 lambdaQL Lambda 表达式中 |
|---|---|
| 数字上的 `< > <= >=`（可空时加 `!!`） | 支持 |
| `BigDecimal`、`String`、`LocalDate`、`BigInteger` 上的 `< > <= >=` | 支持（编译为 `compareTo`） |
| `x.compareTo(y) > 0` | 支持，理解为 `x > y` |
| `==`、`!=` | 支持（[9.4](#94-equality----and-areequal)） |
| `===`、`!==` | 支持，值比较 |
| enum `==` | 支持 |
| 实体 `==` 实体 | **拒绝**（`LQL-3011`）。请比较 id |
| 值对象（`@Embedded`）`==`、`!=` | 支持。按列展开（见 [3.10](03-where.md#310-association-paths)）。大小比较是 `LQL-3012` |
| `+ - *`、一元 `-` | 支持 |
| `%` | 支持，输出为 `mod` |
| 整数 `/` | **请注意。** 各数据库的结果不同（MariaDB 中 `5/2` 是 `2.5`，Kotlin 给出 `2`）。lambdaQL 按原样写成 `(e0.cnt / 2)` |
| `BigDecimal` / `BigInteger` 的 `+ - * %`、一元 `-` | 支持，输出为 SQL 运算符 |
| `BigDecimal` 的 `/` | 支持（Kotlin 使用 `divide(x, HALF_EVEN)`）。结果的标度遵循数据库。其他 `divide` 形式会被拒绝 |
| `in a..b`、`until`、`..<` | 支持（[9.6](#96-in-ranges-and-when)） |
| `x in list`、`x !in list` | 支持（[9.6](#96-in-ranges-and-when)） |
| 字符串 `+`、字符串模板 | **拒绝**（`LQL-2007`）。注意：对于 NULL，Kotlin 给出 `"null"`，SQL 给出 NULL |
| 布尔中缀函数 `and`、`or`、`xor` | **拒绝**（`LQL-2013`）。请使用 `&&` 和 `\|\|`（它们会短路求值，`and`/`or` 不会） |
| 捕获数组上的 `arr[0]` | 视为捕获值，或给出明确的错误 |
| 实体路径上的 `it.tags["k"]`、`it.lines[0]` | **拒绝** |
| 实体上的 `operator` 函数（`plus`、`compareTo`）、自己定义的非 inline `infix` 函数 | **拒绝** |
| `@JvmInline value class` 属性 | **拒绝** |

<a id="no-kotlin-only-operator-api"></a>
### 没有 Kotlin 专用的运算符 API

lambdaQL **没有 Kotlin 专用的运算符 API**（已决定）。普通的 Java 方法名已经可以作为 Kotlin 运算符使用。

| Kotlin 写法 | 为什么可用 | 状态 |
|---|---|---|
| `x in sub.from(...).select { ... }` | Kotlin 把 `x in v` 转换为 `v.contains(x)` | 保留 |
| `Agg.sum(x)!! + 1 > 3` | 对占位值进行普通的算术运算 | 保留 |
| 排序运算符（`+key`、`-key`） | 会给 Java 用户增加奇怪的方法，并且需要 Kotlin 专用的扩展 | 拒绝 |
| 数据库函数的 `invoke` 运算符 | 与数据库函数的声明方式（接口静态方法）冲突 | 拒绝 |
| `o["price"]` | 字符串路径。没有类型安全 | 拒绝 |
| `infix` 函数 | Java 方法不能是 `infix` | 不可能 |
| `between` | 写 `x in a..b`（可空值需要 `!!`，`LocalDate?` 需要 `!!`） | 不需要 API |

<a id="911-results-data-classes-pair-and-triple"></a>
## 9.11 结果：data class、`Pair` 和 `Triple`

下面示例中的 data class 是 `data class OrderRow(val id: Long?, val customerName: String?, val price: Double)` 和 `data class CustomerStat(val customerId: Int?, val count: Long, val total: Double?)`。

<!-- region: KotlinQueryTest.kt#data-class -->
```kotlin
val q = qb.from(Order::class.java).select { OrderRow(it.id, it.customer?.name, it.price) }
assertEquals("select e0.id, j1.name, e0.price from Order e0 left join e0.customer j1", q.toHql().hql())
```

<!-- region: KotlinQueryTest.kt#group-by -->
```kotlin
val q = qb.from(Order::class.java)
    .groupBy { it.customer?.id }
    .having { Agg.sum(it.price) > 200 }
    .select { CustomerStat(it.customer?.id, Agg.count(), Agg.sum(it.price)) }
assertEquals("select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id"
    + " having sum(e0.price) > 200.0", q.toHql().hql())
```

<!-- region: ManualKotlinNotesTest.kt#group-pair -->
```kotlin
// a Pair as the group key: two keys
val q = qb.from(Order::class.java)
    .where { it.customer != null }
    .groupBy { it.customer?.id to it.status }
    .select { Triple(it.customer?.id, it.status, Agg.count()) }
assertEquals("select e0.customer.id, e0.status, count(*) from Order e0 where e0.customer is not null"
    + " group by e0.customer.id, e0.status", q.toHql().hql())
```

`Pair` 结果和 `listOf(...)` 分组键的示例见 [第 6 章](06-select-group.md)。

| 规则 | 说明 |
|---|---|
| 在 data class 中使用可空类型 | `sum`、`avg`、`min`、`max` 可能为 NULL。非空参数中的 NULL 会在构建对象时抛出 NPE |
| `Pair`、`Triple` | 可以作为结果和分组键 |
| `groupBy` 中的 `listOf(...)` | 允许（这是唯一允许在 Lambda 表达式中创建列表的地方） |
| `groupBy` 中的 `arrayOf(...)`、`setOf(...)` | 不允许 |
| 派生表或 CTE 的行类型 | 只能是 Kotlin data class（或 Java record）。列遵循 `componentN` 的顺序 |
| `Agg` 函数 | `import org.lambdaql.Agg.sum` 可以使用。与集合上的 Kotlin `sum()` / `count()` 不冲突 |

详情：[6. 查询列、聚合与分组](06-select-group.md)。

示例代码：[`KotlinQueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)（`data-class`、`group-by`），[`ManualKotlinNotesTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinNotesTest.kt)（`group-pair`）。

<a id="912-update-insert-delete-property-assignment-and-dmlvalue"></a>
## 9.12 更新、插入、删除：属性赋值和 `Dml.value`

Kotlin 的属性赋值编译为与 Java 相同的 setter 调用，所以规则相同。

<!-- region: ManualKotlinDmlTest.kt#update-fk -->
```kotlin
// the FK column: the ?. assignment always assigns
val fk = qb.update(Order::class.java).set { it.customer?.id = cid }.where { it.id == first }
assertEquals("update Order e0 set e0.customer.id = :p1 where e0.id = :p2", fk.toHql().hql())
// no customer
val none = qb.update(Order::class.java).set { it.customer = null }.where { it.status.toInt() == 3 }
assertEquals("update Order e0 set e0.customer = null where e0.status = 3", none.toHql().hql())
// a captured entity: its @Id is bound
val entity = qb.update(Order::class.java).set { it.customer = kim }.where { it.id == third }
assertEquals("update Order e0 set e0.customer = :p1 where e0.id = :p2", entity.toHql().hql())
```

| Kotlin 写法 | 含义 |
|---|---|
| `value(o.status, st)` | 用 `import org.lambdaql.Dml.value` 导入的函数。用于带有 `val` 属性（没有 setter）的实体。可以与属性赋值在同一个 Lambda 表达式中混用（见 [7.3](07-dml.md#73-two-ways-to-assign-property-or-dmlvalue)） |
| `it.cnt += 1` | 支持（见 [7.2](07-2-update.md#add-to-a-value-default-for-null)） |
| `it.custName = it.description ?: "none"` | 支持：`coalesce(e0.description, 'none')` |
| `it.cnt++`、`--` | 拒绝（`LQL-2003`）。请写 `it.cnt += 1` |
| `set` 中的 `apply { }`、`with(it) { }`、`also { }` | 拒绝（`LQL-2003`）。请直接写赋值 |
| `set { }` 和 `set { o, sub -> }` | 不同的重载。Kotlin 按参数个数选择。第二个参数 `sub` 是子查询的起点 |

详情：[7. 更新、插入、删除](07-dml.md)。

示例代码：[`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`update-fk`）。

<a id="913-procedures-and-db-functions"></a>
## 9.13 存储过程与数据库函数

示例见 [8. 存储过程与数据库函数](08-procedures-functions.md)。本节只汇总在 Kotlin 中需要了解的规则。

<a id="procedures"></a>
### 存储过程

| 规则 | 原因 |
|---|---|
| 使用可空返回类型（`Double?`） | 非空类型中的 NULL OUT 值会失败。lambdaQL 会抛出一个具名错误 |
| 桩函数中不要使用默认参数 | Kotlin 会把调用改为 `...$default` 并填入 Kotlin 的默认值。lambdaQL 会报告错误 |
| 命名参数只能使用简单值（变量、常量） | 如果是表达式，Kotlin 会创建隐藏的局部变量，lambdaQL 不接受它 |
| 没有返回值的存储过程使用 `exec`（而不是 `call`） | 两者都叫 `call` 在 Java 中会有歧义，而 `run` 会与 Kotlin 的 `run { }` 冲突 |
| 使用 `-java-parameters` 编译 | 这样 lambdaQL 就能检查被交换的同类型参数（[8.4](08-procedures-functions.md#84-warning-argument-order)） |

<a id="db-functions-interface-companion-object--jvmstatic"></a>
### 数据库函数：接口 `companion object` + `@JvmStatic`

Kotlin 接口没有静态函数。请使用 `companion object` 和 `@JvmStatic`。
函数主体不会被调用。在 Lambda 表达式之外直接调用它时，它会抛出异常。
Kotlin 调用 companion 函数时，字节码 opcode 会先加载 `Companion` 对象。lambdaQL 跳过这一步，读取函数的注解。

| 声明方式 | 是否支持 |
|---|---|
| 接口 `companion object` + `@JvmStatic` | **支持（推荐）** |
| Kotlin `object` + `@JvmStatic` | 支持 |
| 顶层函数 | 支持，但不推荐：如果忘了导入，而这个名字也是标准库的名字（`maxOf`），Kotlin 会悄悄调用标准库函数 |
| 扩展函数 | 不支持 |
| 带默认参数的 `@SqlFunction` | 不支持 |
| `inline` 的 `@SqlFunction` | 不支持（调用会消失） |
| `vararg` 的 `@SqlFunction` | 不支持 |
| 函数类型属性 / 函数式接口常量 | 不支持 |

作用于列的函数（`OrderFns.discount(it.price)!! > 10`）无法使用普通索引。
lambdaQL 会为此记录一条 WARN 日志（见 [8.9](08-procedures-functions.md#89-warning-functions-on-columns-and-indexes-sargable)）。

<a id="914-kotlin-limits-kc1-to-kc21"></a>
## 9.14 Kotlin 限制（KC1 到 KC21）

lambdaQL 无法把 Lambda 表达式分析为查询时，会抛出带有代码
`[LQL-xxxx]`、源代码行和提示的异常（见 [11. 错误与语言](../11-errors-and-languages.md)）。
KC1 到 KC5 在加载 Lambda 表达式时就会被发现。在这些情况下 lambdaQL 会立即停止。

| # | 不要这样写 | 原因 | 请改为这样写 |
|---|---|---|---|
| KC1 | 用 `-Xsam-conversions=class` 编译 | 不会创建 `SerializedLambda`。Lambda 表达式会变成一个类 | 保留默认的 `-Xsam-conversions=indy` |
| KC2 | 在 `inline fun` 主体（普通、`reified`、`crossinline`）中创建的查询 Lambda 表达式 | Lambda 表达式会变成一个类，而不是 Lambda 表达式 | 把辅助函数改为非 inline，或从调用方传入 Lambda 字面量 |
| KC3 | 对自己定义的 Kotlin `fun interface` 使用可调用引用（`kkey(Order::price)`） | 它会变成一个适配器类 | Lambda 字面量 `{ it.price }` |
| KC4 | Kotlin 函数类型的值：`val f: (Order) -> Boolean = {...}; where(f)` | lambdaQL 无法读取 `Function1` 的内部 | 用字面量写 `where { ... }` |
| KC5 | `object : ...`（对象表达式），不是 `Serializable` 的函数式接口 | 不是可序列化的 Lambda 表达式 | Lambda 字面量 |
| KC6 | `takeIf`，一般的布尔 `if (a) x else y` | 不支持（`LQL-2021`）。选择值的 `if`/`when` 受支持（只用捕获值时是绑定参数，用到实体值时是 `case when`） | `&&` / `\|\|` |
| KC7 | *（已被 D25 移除）* | – | – |
| KC8 | 在 Lambda 表达式中使用 `in listOf(1, 2, 3)` 或 `setOf(...)` | 在 Lambda 表达式中创建的集合 | 在外部写 `val ids = listOf(1, 2, 3)`，然后写 `it.id in ids` |
| KC9 | `isBlank()`、`isNullOrBlank()` | Unicode 空白字符没有可移植的 SQL 写法 | 直接比较 |
| KC10 | *（已被 D21 移除）* | – | – |
| KC11 | 自己定义的非 inline 扩展函数或扩展属性，实体的 `operator` 函数 | lambdaQL 看不到调用的内部 | 改为 `inline fun`，或直接写出条件 |
| KC12 | 对捕获的变量使用 `n++` | Lambda 表达式写入了一个变量 | 在 Lambda 表达式外部计算该值 |
| KC13 | 实体 `==` 实体（`it.customer == c`） | 不支持（`LQL-3011`） | `it.customer?.id == c.id` |
| KC14 | `isNaN()` | 不支持 | 不要存储 NaN。见 [10.5](10-rules-and-limits.md#105-kotlin-and-sql-differences) |
| KC15 | `@JvmField` 实体属性 | 是字段访问，而不是 getter | 普通的 `var` / `val` |
| KC16 | 被改名（mangled）的 getter 名：`internal` 属性、`value class`、`@get:JvmName` | getter 无法映射到 JPA 属性 | 公开的普通属性 |
| KC17 | 字符串模板中的实体值 | 不支持字符串拼接（`LQL-2007`） | 分别比较各列 |
| KC18 | Java 合成属性引用 `JOrder::price` | Kotlin 编译错误 | Lambda 表达式 `{ it.price }` |
| KC19 | `Order::price.getter` 及其他 `kotlin-reflect` API | 不使用 `kotlin-reflect` | Lambda 表达式 `{ it.price }` |
| KC20 | 列上的其他标准库函数（`coerceAtLeast`、`trim`、`toLocalDate`、...） | 不在允许列表中（`LQL-3008`） | 使用 `@SqlFunction` 的数据库函数 |
| KC21 | 把布尔 elvis 用作值或排序键（`{ it.vip ?: false }`） | 布尔值上的 `coalesce` 在 Oracle 上会失败（ORA-00932） | 指定 NULL 的位置并按 `{ it.vip }` 排序。在条件中 `it.vip ?: false` 没有问题 |

<a id="915-where-lambdaql-differs-from-running-kotlin-code"></a>
## 9.15 lambdaQL 与直接运行 Kotlin 代码的不同之处

这些差异是设计的一部分，不是 bug。

| Kotlin 代码 | lambdaQL（SQL） |
|---|---|
| `!!` 抛出 NPE | 值上的 `!!` 被忽略。NULL 遵循 SQL 规则。在 `where` 中，用 `!!` 经过关联步骤的比较只对有关联的行为真 |
| `description == null` 时 `it.description != "x"` 为 `true` | 对于 NULL，`e0.description <> 'x'` 为 UNKNOWN，所以该行不会被返回。要包含它，请写 `it.description == null \|\| ...` |
| 没有客户时 `it.customer?.name == null` 为 `true` | 相同：用 `?.` 经过的步骤成为 `left join`，没有客户的订单也会被返回。`it.customer!!.name == null` 只对有客户的订单为真，因此不会返回 |
| 主体为 NULL 的区间为 false，`!in` 为 `true` | 该行不会被返回 |
| `===` 是对象同一性 | 值比较 |
| `BigDecimal` 的 `==` 比较标度（`1.0 != 1.00`） | 值比较 |
| 非空类型 `String` 永远不为 null | 列仍可能为 NULL。lambdaQL 使用 JPA 元数据 |
| 整数 `/` 会丢弃小数部分 | 可能因数据库而异（MariaDB 中 `5/2 = 2.5`） |

可空数字属性与常量的比较（`it.cnt != 1`）也会变为 `e0.cnt <> 1`，因此不返回 `cnt` 为 NULL 的行。结果可能与 Kotlin 不同（[9.3](#93-only-your-null-checks-count)）。

更多规则：[10. 规则与限制](10-rules-and-limits.md)。

---

[Kotlin 路线](README.md) | 上一章：[8. 存储过程与数据库函数](08-procedures-functions.md) | 下一章：[10. 规则与限制](10-rules-and-limits.md) | [本页的 Java 版本：9. Java 说明](../java/09-java-notes.md)
