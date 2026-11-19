<a id="9-kotlin-notes"></a>
# 9. Kotlin 说明

[Kotlin 路线](README.md) | [手册首页](../README.md) | 上一章：[8. 存储过程与数据库函数](08-procedures-functions.md) | 下一章：[10. 规则与限制](10-rules-and-limits.md) | [本页的 Java 版本：9. Java 说明](../java/09-java-notes.md)

公共页面：[1. 概述](../01-overview.md) · [11. 错误与语言](../11-errors-and-languages.md)

lambdaQL 用**同一个引擎**和同一套 API 支持 Kotlin 和 Java。
含义相同的 Kotlin Lambda 和 Java Lambda 会生成相同的 HQL。
本章把 Kotlin 的特别之处集中在一处。

<a id="contents"></a>
## 目录

- [9.1 配置](#91-setup)
- [9.2 可用的 Lambda 写法](#92-lambda-forms-that-work)
- [9.3 只有自己写的 null 检查才算数](#93-only-your-null-checks-count)
- [9.4 相等：`==`、`!=`、`===` 和 `areEqual`](#94-equality----and-areequal)
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

- 示例下方的注释给出 lambdaQL 生成的 HQL（当设计中有明确说明时）。
- 直接写在 Lambda 中的常量（数字、字符串、布尔值、`null`、enum 常量、`const val`）作为字面量写入 HQL。捕获的值和计算出的值始终是绑定参数（`:p1`、`:p2`、...）（D37）。
- `e0` 是根实体的别名，`j1` 是 lambdaQL 为关联路径添加的 join 的别名。在 `where` 中始终是内连接 `join`（D48），路径只在 `orderBy` 或 `select` 中使用时是 `left join`（D36）。子查询的根是 `s1`、`s2` 等（见 [5. 子查询](05-subquery.md)）。计划中功能（派生表、CTE、存储过程）的示例仍使用计划中的别名 `o` 和 `c1`。
- 实体来自 [2. 入门](02-getting-started.md#example-entities)。有些示例在 `Order` 上使用了两个额外属性：`var flag: Boolean? = null` 和 `var kind: OrderKind? = null`（其中 `enum class OrderKind { NORMAL, EXPRESS, GIFT }`）。

---

<a id="91-setup"></a>
## 9.1 配置

| 项目 | 值 |
|---|---|
| Kotlin | 2.x。设计已用 kotlinc 1.7.22、2.1.10、2.2.21、2.3.0 和 2.3.21 检查过 |
| JVM | Java 21 |
| 编译器选项 | 保留默认的 `-Xsam-conversions=indy`。**不要**使用 `-Xsam-conversions=class`（KC1） |
| `-jvm-target` | 1.8、11、17 和 21 都可以 |
| Kotlin 运行时 | lambdaQL 不会把 `kotlin-stdlib` 或 `kotlin-reflect` 加入依赖 |
| JPA 实体 | 普通的 Kotlin JPA 实体（注解加在字段上也可以）。照常使用 `kotlin("plugin.jpa")` 和 all-open 插件 |

详情：[2. 入门](02-getting-started.md#kotlin-compiler-settings)。

<a id="92-lambda-forms-that-work"></a>
## 9.2 可用的 Lambda 写法

lambdaQL 的函数接收 Java 的 `Serializable` 函数式接口。
在 Kotlin 中传入 **Lambda 字面量**。使用尾随 Lambda 和 `it`。

```kotlin
val list = qb.from(Order::class.java)
    .where { it.status == 1 }
    .where { it.price!! > 100.0 }        // 多次 where() 调用用 AND 连接
    .toList()
// select e0 from Order e0 where e0.status = 1 and e0.price > 100.0
```

**关于 SQL 块。** `…` 是缩短的 select 列表（实体的所有列），`?` 是绑定参数。这些行来自 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)。该测试使用 Hibernate 6.6.5 在 H2 2.3、MariaDB 10.11 和 Oracle 23.26 上运行每个查询，如果 Hibernate 的 SQL 发生变化，测试就会失败。该测试使用 Java Lambda。含义相同的 Kotlin Lambda 生成相同的 HQL，因此 SQL 也相同。

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `to-hql`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>100.0
```

有两个或更多参数的 Lambda 使用名字。不需要的参数用 `_`。

```kotlin
.where { o, sub -> sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
.orderBy { o, by -> by.desc(o.price).asc(o.id) }
```

Kotlin 属性通过 getter 读取。`it.price` 与 Java 调用 `getPrice()` 是相同的字节码。
lambdaQL 把 getter 映射到 JPA 属性（规则 R10）。
布尔属性 `active` 的 getter 是 `getActive()`。lambdaQL 把它映射到 `active`。

| 写法 | 可用吗？ |
|---|---|
| `where { it.active }`（Lambda 字面量） | 可用 |
| 位于 `run { }`、`let { }`、`forEach { }`、非 inline 函数、`suspend` 函数中的 Lambda | 可用 |
| 由调用方传给 lambdaQL 类型的 `inline fun` 参数的 Lambda | 可用 |
| SAM 构造器，也包括先存入 `val` 的情况 | 可用 |
| 属性引用：`where(Order::active)`、`orderBy(Order::price)` | 可用（R23）。在 `orderBy` 中尚未实现 |
| Java 实体的 Java getter 引用：`orderBy(JOrder::getPrice)` | 可用 |
| `val f: (Order) -> Boolean = { it.active }; where(f)` | **不可用**（KC4） |
| `object : ...`（用对象表达式代替 Lambda） | **不可用**（KC5） |
| 在 `inline fun` 主体内部创建的 Lambda | **不可用**（KC2） |
| 用 `-Xsam-conversions=class` 编译 | **不可用**（KC1） |

<a id="93-only-your-null-checks-count"></a>
## 9.3 只有自己写的 null 检查才算数

lambdaQL **从不运行 Lambda**。它读取字节码并写出 HQL。
因此有些 Kotlin 规则的作用方式不同。以下四个决策是基础。

| 决策 | 规则 |
|---|---|
| D20、D36 | 像 `it.customer?.name` 这样的路径是**一个列**，而不是调用链。在 `where` 中，对一关联默认是内连接。没有 customer 的订单不会被返回。要查找这些订单，请写 `it.customer == null`。即使写了 `it.customer == null \|\| ...` 这样的 null 保护，内连接也不会改变（D48）。要保留这些订单，请使用 `leftJoin`。 |
| D22 | lambdaQL **只使用自己写的 null 处理**：`== null`、`!= null`、`?.`、`?:`。Kotlin 编译器自己添加的 null 检查会被忽略。其他 NULL 遵循 SQL：条件为 UNKNOWN，该行不会被返回。 |
| D23 | `!!` 会被忽略。它只是为了让代码能够编译。`it.customer!!.name` 和 `it.customer?.name` 是同一个列。 |
| D12 | 对象之间的 `==`（以及 `===`）表示**值相等**，而不是对象同一性。 |

lambdaQL 如何区分“自己写的” null 检查和编译器的 null 检查？字节码不同。

| 源代码 | 字节码形态 | lambdaQL |
|---|---|---|
| 写了 `it.status == null` 或 `it.status != null` | `getStatus; ifnull / ifnonnull` | 使用它：`is null`、`is not null` |
| 对 `status: Int?` 写了 `it.status == 1` | 编译器添加 `dup; ifnonnull; pop; goto`，然后拆箱 | 忽略隐藏的检查：`e0.status = 1` |
| 写了 `it.price!!` | `Intrinsics.checkNotNull(...)` | 忽略 |

结果是 Kotlin 和 Java 生成相同的 SQL。`it.status == 1` 是 `e0.status = 1`，用 Java 写的同一条件也是 `e0.status = 1`。

<a id="94-equality----and-areequal"></a>
## 9.4 相等：`==`、`!=`、`===` 和 `areEqual`

<a id="how-kotlin-compiles-"></a>
### Kotlin 如何编译 `==`

| Kotlin | 编译为 | lambdaQL 规则 | HQL |
|---|---|---|---|
| 两个非空基本类型：`it.qty == 5` | 比较分支（与 Java 相同） | R1 | `i.qty = 5` |
| 可空基本类型与常量：`it.status == 1`（`Int?`） | 隐藏的 null 检查 + 比较 | R12（忽略隐藏检查，D22） | `e0.status = 1` |
| `it.status != 1`（`Int?`） | 同上 | R12 | `e0.status <> 1`（**不**返回 NULL 行） |
| 引用，或两侧都可空：`it.description == text`、`it.status == q`（`q: Int?`） | 调用 `Intrinsics.areEqual(a, b)` | R11：值相等 | `e0.description = :p1`（捕获值 `null`：没有匹配的行，`LQL-4045`） |
| `it.description != text` | `!Intrinsics.areEqual(a, b)` | R11，SQL 语义 | `e0.description <> :p1`（**不**返回 NULL 行） |
| 两个实体值：`it.description == it.custName` | `Intrinsics.areEqual(a, b)` | R11，SQL 语义 | `e0.description = e0.custName`（任一侧为 NULL：不匹配） |
| `it.id === q`、`!==` | `if_acmpeq` / `if_acmpne` | R17：值相等 | 与 `==` 相同 |
| enum：`it.kind == OrderKind.GIFT` | `if_acmpne` | R2、R17 | `e0.kind = com.example.OrderKind.GIFT`（带完整类名的字面量） |

`Intrinsics.areEqual` 是 `==` 背后的 Kotlin 运行时函数。lambdaQL 按名字识别它，并把它理解为**值相等**。
实体值遵循 SQL 语义（决策 D48）。任一侧为 NULL 时不匹配，NULL 与 NULL 也一样。
捕获值 `null` 作为 NULL 绑定，不匹配任何行（`WARN` 级别 `LQL-4045`）。要查找 NULL，请写 `x == null`。
当结果可能与 Kotlin 不同时，lambdaQL 以 `DEBUG` 级别记录 `LQL-5009`。

<a id="plain--and-"></a>
### 普通的 `=` 和 `<>`

lambdaQL 总是写出普通的 `=` 和 `<>`（决策 D48）。
它不写 `is [not] distinct from`。这种形式在 Oracle 上会变成 `decode(...)`，无法使用索引。

```kotlin
.where { it.description == text }    // text 为 "abc" -> e0.description = :p1
.where { it.description != "x" }     // e0.description <> 'x'   （description 为 NULL 时不匹配）
.where { it.description == null || it.description != "x" }   // 也包含 NULL 行
```

> [!WARNING]
> **Kotlin 的非空类型并不能作为证明。** `var code: String` 属性仍可能包含来自数据库的 NULL。
> 因为 Hibernate 会不经检查地把列值写入字段。
> lambdaQL 看的是 JPA 元数据，而不是 Kotlin 类型。

<a id="-and-"></a>
### `===` 和 `!==`

在 lambdaQL 中，`===` 与 `==` 一样表示**值相等**（R17）。
查询中没有对象同一性。对于两个值都是 1000 的不同 `Long` 装箱对象，Kotlin 本身给出 `false`，lambdaQL 给出 `true`。

### `BigDecimal`

在 Kotlin 中，`BigDecimal` 上的 `==` 调用 `equals`，它还会比较标度（`1.0 != 1.00`）。
SQL 的 `=` 只比较值。**lambdaQL 使用值相等。**
支持 `BigDecimal`、`String`、`LocalDate` 和 `BigInteger` 上的 `<`、`>`、`<=`、`>=`（它们编译为 `compareTo`）。

<a id="booleans"></a>
### 布尔值

```kotlin
.where { it.active }                       // e0.active = true
.where { !it.active }                      // not (e0.active = true)
.where { it.flag == true }                 // e0.flag = true
.where { it.flag != true }                 // e0.flag <> true   （不返回 NULL 行：SQL 语义）
.where { it.active == (it.status!! > 1) }  // (e0.active = true and e0.status > 1) or (e0.active = false and e0.status <= 1)
```

支持比较两个布尔表达式（D25）。lambdaQL 把它展开为 `AND`/`OR`（不使用 `CASE`），这样数据库可以使用索引。
如果两侧共用同一个条件（例如 `it.active == it.active`），会被拒绝。

<a id="java-entities-from-kotlin-platform-types"></a>
### 从 Kotlin 使用 Java 实体（平台类型）

如果实体是 Java 类，Kotlin 会把它的 getter 视为平台类型（`Long!`）。
规则相同。`j.id == 1L` 是 `e0.id = 1`，`j.customer.name` 是连接的列（在 `where` 中默认是内连接）。

<a id="95-null-operators---and-"></a>
## 9.5 null 运算符：`?.`、`?:` 和 `!!`

<a id="summary"></a>
### 摘要

| Kotlin | 在 lambdaQL 中的含义 | HQL 示例 |
|---|---|---|
| `it.customer?.name` | 列 `customer.name`。在 `where` 中默认是内连接，因此没有 customer 的订单不会被返回 | `j1.name` + `join e0.customer j1` |
| `it.customer!!.name` | 与 `?.` 相同。`!!` 被忽略（D23） | `j1.name` + `join e0.customer j1` |
| `it.customer?.id` | 路径以目标的 `@Id` 结尾：外键列，不需要 join | `e0.customer.id` |
| `it.customer == null` | 只取没有 customer 的订单 | `e0.customer is null`（外键列，不需要 join） |
| `it.customer != null` | 只取有 customer 的订单 | `e0.customer is not null`（外键列，不需要 join） |
| `it.price!! > 10.0` | `!!` 被忽略。它只是给编译器看的 | `e0.price > 10.0` |
| `it.description ?: ""`（值） | 默认值 | `coalesce(e0.description, '')` |
| `it.flag ?: false`（条件） | NULL 视为 false | `(e0.flag is not null and e0.flag = true)` |
| `lo!!`（捕获值） | `!!` 被忽略。`lo` 为 null 时绑定 NULL，因此没有匹配的行（`DEBUG` 级别 `LQL-4045`，见 [9.8](#98-captured-values)） | `:p1` |

<a id="-and--on-association-paths"></a>
### 关联路径上的 `?.` 和 `!!`

```kotlin
.where { it.customer?.name == "Kim" }
// select e0 from Order e0 join e0.customer j1 where j1.name = 'Kim'

.where { it.customer!!.name == "Kim" }
// 相同的 HQL

.where { it.customer?.id == 5L }
// select e0 from Order e0 where e0.customer.id = 5      （外键列，不需要 join）
```

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `path-literal`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name='Kim'
```

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `fk-literal`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id=5
```

路径是**一个列**，而不是调用链（D20）。在 `where` 中，lambdaQL 默认使用内连接（D36）。
因此没有 customer 的订单不会被返回，也不需要在路径前面写 `it.customer != null &&`。

**`?.` 不会把没有 customer 的订单带回来。** 在 Kotlin 中，没有 customer 时 `it.customer?.name == null` 为 `true`。
在 lambdaQL 中，它只返回 customer 没有名字的订单。路径是内连接，因此即使写了 null 保护也一样（D48）。

```kotlin
.where { it.customer == null }
// select e0 from Order e0 where e0.customer is null      （外键列，不需要 join）

.where { it.customer == null || it.customer?.name == null }
// 只返回 customer 没有名字的订单：内连接排除了没有 customer 的订单
// select e0 from Order e0 join e0.customer j1 where e0.customer is null or j1.name is null
```

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `no-association`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id is null
```

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `null-guard-name-null`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.id is null or c1_0.name is null
```

`?.` 也可以和末尾的函数一起使用。`it.description?.length` 是 `length(e0.description)`。

<a id="-is-just-for-the-compiler"></a>
### `!!` 只是给编译器看的

当 `price` 是 `Double?` 时，Kotlin 不会编译 `it.price > 10.0`。
请写 `it.price!! > 10.0`。lambdaQL 会忽略 `!!`。Lambda 不会运行，因此不会发生 NPE。
`price` 为 NULL 的行不会被返回（SQL 规则）。

<a id="-elvis"></a>
### `?:`（elvis）

在值中（select、DML `set`、排序键），`?:` 变为 `coalesce`（规则 R15）。

```kotlin
.select { it.description ?: "" }                 // coalesce(o.description, '')
.select { it.customer?.name ?: "none" }          // coalesce(c1.name, 'none') + left join
.select { (it.price ?: 0.0) * 2 }                // coalesce(o.price, 0.0) * 2
qb.update(Order::class.java).set { it.price = (it.price ?: 0.0) + 1 }.all().execute()
// set o.price = coalesce(o.price, 0.0) + 1.0
```

在**字符串**条件中，lambdaQL 不在列上加函数（SARGable 规则）。

```kotlin
.where { (it.description ?: "") == "x" }   // e0.description = 'x'
.where { (it.description ?: "") == "" }    // (e0.description = '' or e0.description is null)
```

在**数字**条件中，设计写出 `coalesce(column, default)`。

```kotlin
.where { (it.price ?: 0.0) > 3.0 }            // coalesce(e0.price, 0.0) > 3.0
.where { (it.description?.length ?: 0) > 3 }  // coalesce(length(e0.description), 0) > 3
```

列上有函数时，不会使用 `price` 上的普通索引。
提示：在第一个示例中 NULL 变为 0，而 `0 > 3` 为 false。所以 `it.price!! > 3.0` 返回相同的行，
并生成可以使用索引的 `e0.price > 3.0`。

**布尔 elvis** 在**条件中**受支持。

```kotlin
.where { it.flag ?: false }       // (e0.flag is not null and e0.flag = true)
.where { !(it.flag ?: false) }    // (e0.flag is null or not (e0.flag = true))
```

把布尔 elvis 用作**值或排序键**（`select { it.flag ?: false }`）不受支持（KC21）。
布尔值上的 `coalesce` 在 Oracle 上会失败（ORA-00932）。排序时请使用键 `{ it.flag }`，
并用 `by.asc(key, nullsFirst)` 或 `by.desc(key, nullsFirst)` 选择 NULL 的位置（[4. 排序与分页](04-order-paging.md#43-direction)）。

<a id="smart-casts-and-let"></a>
### 智能转换与 `?.let`

使用前的 null 检查是一个普通条件。

```kotlin
.where { it.description != null && it.description!!.length > 3 }
// (e0.description is not null and length(e0.description) > 3)

.where { it.description?.let { d -> d.length > 3 } == true }
// (e0.description is not null and length(e0.description) > 3)
```

<a id="-in-an-assignment"></a>
### 赋值中的 `?.`

在 `set { }` 和 `values { }` 中，`it.customer?.id = cid` **总是会赋值**。`?.` 被忽略（D23）。

<a id="96-in-ranges-and-when"></a>
## 9.6 `in`、区间和 `when`

本节中的所有写法在 v0.1 中都受支持（决策 D21、D29）。详情和更多示例：[3.9](03-where.md#39-in-with-a-captured-collection)、[3.14](03-where.md#314-ranges-kotlin-only)、[3.15](03-where.md#315-when-kotlin-only)。

| Kotlin | HQL | 说明 |
|---|---|---|
| `it.id in ids`（捕获的集合） | `e0.id in (:p1)` | 为空：FALSE。`null` 元素：报错 |
| `it.id !in ids` | `e0.id not in (:p1)`。可能为 NULL 的列也一样（不返回 NULL 行） | 为空：TRUE |
| `it.status in 1..5` | `e0.status between 1 and 5` | |
| `it.status !in 1..5` | `not (e0.status between 1 and 5)` | 不返回 NULL 行 |
| `it.status in 1 until 5`、`in 1..<5` | `e0.status >= 1 and e0.status < 5` | |
| `it.status in lo..hi` | `e0.status between :p1 and :p2` | |
| `it.status in 5 downTo 1` | 支持 | 与 `1..5` 返回相同的行 |
| `... step 2` | **拒绝** | |
| `when (it.status) { 1, 2 -> true; else -> false }` | `e0.status in (1, 2)` | 主体可以是 `Int`、`String`、enum |
| `when { a -> true; b -> true; else -> false }` | `a or b` | |
| `it.customer?.id in sub.from(...).select { ... }` | `o.customer.id in (select ...)` | 子查询，见 [5.4](05-subquery.md#54-in) |
| `x !in sub.from(...).select { ... }` | `x not in (select ...)`（SQL 语义） | 见 [5.5](05-subquery.md#55-not-in-is-written-as-not-exists) |
| `it.status in listOf(1, 2)`（在 Lambda 中创建的列表） | **拒绝**（KC8） | 在外部创建列表 |

**可空主体不会加 null 保护**（D22）。Kotlin 定义 `null in 1..5` 为 false，`null !in 1..5` 为 true。
lambdaQL 不添加保护。NULL 主体既不会被 `in` 返回，**也**不会被 `!in` 返回（SQL 规则）。
需要 NULL 行时，请写 `it.status == null || it.status !in 1..5`。

对捕获集合使用 `!in` 也一样。`x not in (:p1)` 不返回 NULL 行（SQL 语义，决策 D48）。
需要 NULL 行时，请写 `it.status == null || it.status !in ids`。

<a id="97-strings"></a>
## 9.7 字符串

以下 Kotlin 字符串写法有设计（规则 R21）。它们尽可能遵循 SARGable 规则。

| Kotlin | HQL | 说明 |
|---|---|---|
| `it.description!!.startsWith("A")` | `e0.description like :p1 escape '\'` | lambdaQL 会转义值中的 `%`、`_` 和 `\` |
| `it.description!!.endsWith(s)` | `e0.description like :p1 escape '\'` | |
| `it.description!!.contains(s)` | `e0.description like :p1 escape '\'` | |
| `it.description?.startsWith("A") == true` | 相同的 `like` | 也受支持 |
| `startsWith(s, ignoreCase = true)`、`equals(s, ignoreCase = true)` | 取决于列（见下文） | D24 |
| `it.description.equals(text)` | 与 `==` 相同 | |
| `it.description!!.lowercase() == "abc"` | `lower(e0.description) = 'abc'` | 列上有函数：不会使用普通索引 |
| `it.description!!.length > 3` | `length(e0.description) > 3` | 长度单位可能因数据库而异 |
| `it.description.isNullOrEmpty()` | `(e0.description is null or e0.description = :p1)` | 在 Oracle 上 `''` 就是 NULL |
| `isBlank()`、`isNullOrBlank()` | **拒绝**（KC9） | 使用 `isNullOrEmpty()` 或直接比较 |
| 列上的 `trim()`、`coerceAtLeast()`、`toLocalDate()` 及其他标准库调用 | **拒绝**（KC20） | 计划中（函数映射） |
| `"$a-$b" == it.description`（模板中只有捕获值） | `:p1 = e0.description` | 已设计：字符串在应用程序中构建。目前分析核心拒绝所有字符串拼接，因此请在 Lambda 之前构建字符串 |
| `"${it.description}-x"`（模板中有实体值） | **拒绝**（KC17） | 计划中 |

**`ignoreCase = true`（D24）。** lambdaQL 不写无法使用索引的 `lower(col) = lower(:p)`。
启动时，它读取该列的数据库元数据，并选择数据库能够利用的写法。

| 数据库具备的条件 | HQL |
|---|---|
| 不区分大小写的排序规则（MariaDB `_ci`） | `col = :p` / `col like :p` |
| Oracle 上 `lower(col)` 的函数索引 | `lower(col) = :p`（值在应用程序中转为小写） |
| Oracle 语言索引（linguistic index） | `col collate binary_ci = :p` |
| 都没有 | 启动时 WARN（严格模式：报错） |

Unicode 大小写规则（例如 `ſ`、`İ`）遵循数据库的排序规则，而不是 Kotlin。

<a id="98-captured-values"></a>
## 9.8 捕获值

| 捕获的内容 | lambdaQL 如何使用 |
|---|---|
| `val`（也包括 `Long?`、`String`、...） | 绑定参数 |
| `var` | 绑定参数。**每次执行查询时**都会读取该值 |
| 外围类的属性（`this.minStatus`，包括 `private`） | 每次执行查询时读取 |
| `companion object` 的 `val`、顶层 `val`、`object` 的 `val` | 绑定参数 |
| `const val` | 作为字面量写入 HQL（D37） |
| 在 Lambda 中写入捕获的变量（`n++`） | **拒绝**（KC12） |

```kotlin
class OrderRepo(private val qb: QueryBuilder) {
    private val minStatus = 2

    fun open(lo: Int?) = qb.from(Order::class.java)
        .where { it.status!! >= minStatus }
        .where { lo == null || it.status!! > lo }   // lo == null：不过滤；否则 e0.status > :p2
        .toList()
}
```

用在 `==`、`!=` 中的捕获值 `null` 作为 NULL 绑定，因此没有匹配的行（`WARN` 级别 `LQL-4045`）。
捕获值 `null` 在同一个值的 null 保护之下（`lo == null || ...`）是允许的。这个保护在查询执行时由应用程序决定，因此 `lo == null` 时这个 `where` 不添加任何过滤。
没有保护的大小比较（`lo == null` 时的 `it.status!! > lo!!`）绑定 NULL，因此没有匹配的行（`DEBUG` 级别 `LQL-4045`）。`lo!!` 和拆箱不会执行。
只有对捕获值调用方法的表达式（`text.trim()`、`lo.toLong()` 等）在值为 `null` 时抛出错误（`LQL-4012`）。
捕获的 `NaN` 或 `Infinity` 总是报错。

<a id="99-property-references-and-scope-functions"></a>
## 9.9 属性引用与作用域函数

<a id="property-references"></a>
### 属性引用

```kotlin
.orderBy(Order::price)       // 与 orderBy { it.price } 相同（尚未实现）
.where(Order::active)        // 与 where { it.active } 相同
```

| 写法 | 可用吗？ |
|---|---|
| 把 Kotlin 的 `Order::price` 传给 lambdaQL 函数 | 可用（R23）。在 `orderBy` 中**尚未实现**：目前请使用 `orderBy { it.price }` |
| Java getter 引用 `JOrder::getPrice` | 可用 |
| Java 合成属性 `JOrder::price` | 不可用。Kotlin 本身无法编译它（KC18）。请使用 `JOrder::getPrice` |
| `Order::price.getter` 及其他 `kotlin-reflect` API | 不可用（KC19） |
| 传给自己定义的 Kotlin `fun interface` 的引用 | 不可用（KC3）。请传入 Lambda 字面量 |

<a id="scope-functions-and-inline-functions-in-conditions"></a>
### 条件中的作用域函数和 inline 函数

以下写法在条件中受支持。编译器会把函数主体内联，所以 lambdaQL 看到的是普通条件。

```kotlin
.where { it.description?.let { d -> d.length > 3 } == true }
.where { it.run { status == 1 && active } }
.where { with(it) { status == 1 } }
.where { it.isOpenInline() }   // 自己定义的函数：inline fun Order.isOpenInline() = status == 1
```

| 写法 | 是否支持 |
|---|---|
| `?.let { }`、`run { }`、`with(it) { }`、`also { }` | 支持，在条件中 |
| 自己定义的 `inline fun` | 支持 |
| 自己定义的非 inline 扩展函数或扩展属性 | 不支持（KC11）。把它改为 `inline`，或直接写出条件 |
| `takeIf { }` | 不支持（KC6） |

在更新和插入的 Lambda 中，v0.1 不支持 `apply`、`with` 和 `also`（见 [9.12](#912-update-insert-delete-property-assignment-and-dmlvalue)）。

<a id="910-kotlin-operators"></a>
## 9.10 Kotlin 运算符

此表来自对 126 种运算符写法的编译器检查（kotlinc 2.3.21，设计文档 KOTLIN.md 第 9 节）。

| 运算符 | 在 lambdaQL Lambda 中 |
|---|---|
| 数字上的 `< > <= >=`（可空时加 `!!`） | 支持 |
| `BigDecimal`、`String`、`LocalDate`、`BigInteger` 上的 `< > <= >=` | 支持（编译为 `compareTo`） |
| `x.compareTo(y) > 0` | 支持，理解为 `x > y` |
| `==`、`!=` | 支持（按 SQL 语义的值相等，[9.4](#94-equality----and-areequal)） |
| `===`、`!==` | 支持，值相等 |
| enum `==` | 支持 |
| enum `<`、`>` | 支持。lambdaQL 把它展开为由 enum 常量组成的 `in (...)`，所以 ORDINAL 和 STRING 映射都可以使用 |
| 实体 `==` 实体 | **拒绝**（KC13）。请比较 id |
| 值对象（`@Embedded`）`==`、`!=` | 支持。按列展开（B6，见 [3.10](03-where.md#310-association-paths)）。大小比较是 `LQL-3012` |
| `+ - *`、一元 `-` | 支持 |
| `%` | 支持，渲染为 `mod` |
| 整数 `/` | **请注意。** 各数据库的结果不同（MariaDB 中 `5/2` 是 `2.5`，Kotlin 给出 `2`）。最终规则尚未确定，可能在 v0.1 中被拒绝。在保护条件之下（`qty != 0 && total / qty > 2`）总是被拒绝 |
| `BigDecimal` / `BigInteger` 的 `+ - * %`、一元 `-` | 支持，渲染为 SQL 运算符 |
| `BigDecimal` 的 `/` | 支持（Kotlin 使用 `divide(x, HALF_EVEN)`）。结果的标度遵循数据库。其他 `divide` 形式会被拒绝 |
| `in a..b`、`until`、`..<`、`downTo` | 支持（[9.6](#96-in-ranges-and-when)）。不支持 `step` |
| `x in list`、`x !in list` | 支持（[9.6](#96-in-ranges-and-when)） |
| 字符串 `+`、包含实体值的字符串模板 | 不支持（计划中）。注意：对于 NULL，Kotlin 给出 `"null"`，SQL 给出 NULL |
| 布尔中缀函数 `and`、`or`、`xor` | **拒绝**。请使用 `&&` 和 `\|\|`（它们会短路求值，`and`/`or` 不会） |
| 捕获数组上的 `arr[0]` | 视为捕获值，或给出明确的错误 |
| 实体路径上的 `it.tags["k"]`、`it.lines[0]` | **拒绝** |
| 实体上的 `operator` 函数（`plus`、`compareTo`）、自己定义的非 inline `infix` 函数 | **拒绝**（KC11） |
| `@JvmInline value class` 属性 | **拒绝**（KC16） |

<a id="no-kotlin-only-operator-api"></a>
### 没有 Kotlin 专用的运算符 API

lambdaQL **没有 Kotlin 专用的运算符 API**（已决定）。普通的 Java 方法名已经可以作为 Kotlin 运算符使用。

| Kotlin 写法 | 为什么可用 | 状态 |
|---|---|---|
| `x in sub.from(...).select { ... }` | Kotlin 把 `x in v` 转换为 `v.contains(x)` | 保留 |
| `Agg.sum(x)!! + 1 > 3` | 对占位值进行普通的算术运算 | 保留 |
| 排序运算符（`by + key`、`-key`） | 会给 Java 用户增加奇怪的方法，并且需要 Kotlin 专用的扩展 | 拒绝 |
| 数据库函数的 `invoke` 运算符 | 与 D31（接口静态函数）冲突 | 拒绝 |
| `o["price"]` | 字符串路径。没有类型安全 | 拒绝 |
| `infix` 函数 | Java 方法不能是 `infix` | 不可能 |
| `between` | 写 `x in a..b`（可空值需要 `!!`，`LocalDate?` 需要 `!!`） | 不需要 API |

<a id="911-results-data-classes-pair-and-triple"></a>
## 9.11 结果：data class、`Pair` 和 `Triple`

```kotlin
data class OrderView(val id: Long?, val customerName: String?, val price: Double?)
data class Stat(val customerId: Long?, val cnt: Long, val total: Double?)

qb.from(Order::class.java).select { o -> OrderView(o.id, o.customer?.name, o.price) }   // data class
qb.from(Order::class.java).select { o -> o.customer?.name to o.price }                  // Pair

qb.from(Order::class.java)
    .groupBy { it.customer?.id }
    .having { Agg.sum(it.price)!! > 1000 }
    .select { Stat(it.customer?.id, Agg.count(), Agg.sum(it.price)) }

qb.from(Order::class.java).groupBy { listOf(it.customer?.id, it.status) }   // 多个键
qb.from(Order::class.java).groupBy { it.customer?.id to it.status }         // Pair 也可以
```

| 规则 | 说明 |
|---|---|
| 在 data class 中使用可空类型 | `sum`、`avg`、`min`、`max` 可能为 NULL。非空参数中的 NULL 会在构建对象时抛出 NPE（J4） |
| `Pair`、`Triple` | 可以作为结果和分组键 |
| `groupBy` 中的 `listOf(...)` | 允许（这是唯一允许在 Lambda 中创建列表的地方） |
| `groupBy` 中的 `arrayOf(...)`、`setOf(...)` | 不允许 |
| 派生表或 CTE 的行类型 | 只能是 Kotlin data class（或 Java record）。列遵循 `componentN` 的顺序（J5） |
| `Agg` 函数 | `import org.lambdaql.Agg.sum` 可以使用。与集合上的 Kotlin `sum()` / `count()` 不冲突 |

详情：[6. 查询列、聚合与分组](06-select-group.md)。

<a id="912-update-insert-delete-property-assignment-and-dmlvalue"></a>
## 9.12 更新、插入、删除：属性赋值和 `Dml.value`

Kotlin 的属性赋值编译为与 Java 相同的 setter 调用，所以规则相同。

```kotlin
qb.update(Order::class.java).set { it.status = st; it.price = it.price!! * 1.1 }.where { it.status == 1 }.execute()
qb.update(Order::class.java).set { o -> value(o.status, 1) }.where { it.id == id }.execute()     // 用于 val 属性（Dml.value）
qb.update(Order::class.java).set { it.customer?.id = cid }.where { it.id == id }.execute()       // ?. 赋值 = 普通赋值
qb.update(Order::class.java).set { it.price = (it.price ?: 0.0) + 1 }.all().execute()            // ?: -> coalesce

qb.delete(Order::class.java).where { it.customer?.name == null }.execute()

qb.insert(Order::class.java).values { it.id = id; it.price = p; it.status = 1 }.execute()
qb.insert(Order::class.java).values(lines) { o, l -> o.id = l.id; o.price = l.price; o.status = 1 }.execute()
```

| Kotlin 写法 | 含义 |
|---|---|
| `value(o.status, 1)` | 用 `import org.lambdaql.Dml.value` 导入的函数（D49）。用于带有 `val` 属性（没有 setter）的实体。可以与属性赋值在同一个 Lambda 中混用（已测试） |
| `value(o.cnt, o.cnt + 1)` | 支持 |
| `it.cnt += 1` | 支持 |
| `it.cnt++`、`--` | v0.1 不支持。请写 `it.cnt += 1` |
| `set` 中的 `apply { }`、`with(it) { }`、`also { }` | v0.1 不支持。请直接写赋值 |
| `set { }` 和 `set { o, sub -> }` | 不同的重载。Kotlin 按参数个数选择。第二个参数 `sub` 是子查询的起点（D49） |

详情：[7. 更新、插入、删除](07-dml.md)。

<a id="913-procedures-and-db-functions"></a>
## 9.13 存储过程与数据库函数

<a id="procedures"></a>
### 存储过程

```kotlin
interface OrderProcs {
    @SqlProcedure("SQ_CALC") fun calc(id: Long, rate: Double): Double?
    @SqlProcedure("SP_RECENT") fun recent(custId: Long): List<OrderView>
    @SqlProcedure("SP_PURGE") fun purge(days: Int)
}

val d = qb.call(OrderProcs::class.java) { it.calc(id, rate) }.execute()
val d2 = qb.call(OrderProcs::class.java) { it.calc(rate = rate, id = id) }.execute()   // 命名参数
qb.exec(OrderProcs::class.java) { it.purge(30) }.execute()                            // 没有返回值
```

| 规则 | 原因 |
|---|---|
| 使用可空返回类型（`Double?`） | 非空类型中的 NULL OUT 值会失败。lambdaQL 会抛出一个具名错误 |
| 桩函数中不要使用默认参数 | Kotlin 会把调用改为 `...$default` 并填入 Kotlin 的默认值。lambdaQL 会报告错误 |
| 命名参数只能使用简单值（变量、常量） | 如果是表达式，Kotlin 会创建隐藏的局部变量，lambdaQL 在 v0.1 中不接受 |
| 没有返回值的存储过程使用 `exec`（而不是 `call`） | 两者都叫 `call` 在 Java 中会有歧义，而 `run` 会与 Kotlin 的 `run { }` 冲突 |
| 使用 `-java-parameters` 编译 | 这样 lambdaQL 就能检查被交换的同类型参数（[8.4](08-procedures-functions.md#84-warning-argument-order)） |

<a id="db-functions-interface-companion-object--jvmstatic"></a>
### 数据库函数：接口 `companion object` + `@JvmStatic`

Kotlin 接口没有静态函数。请使用 `companion object` 和 `@JvmStatic`。

```kotlin
interface OrderFns {
    companion object {
        @JvmStatic @SqlFunction("FN_DISCOUNT")
        fun discount(price: Double?): Double? = throw DbOnlyException()
    }
}

qb.from(Order::class.java).where { OrderFns.discount(it.price)!! > 10 }
// where function('FN_DISCOUNT' as Double, o.price) > 10
val v = qb.call { OrderFns.discount(price) }.execute()     // 单独调用函数
```

函数主体从不执行。只有在 Lambda 之外调用它时，它才会抛出异常。
Kotlin 调用 companion 函数时，字节码会先加载 `Companion` 对象。lambdaQL 跳过这一步，读取函数的注解。

| 声明方式 | 是否支持 |
|---|---|
| 接口 `companion object` + `@JvmStatic` | **支持（推荐）** |
| Kotlin `object` + `@JvmStatic` | 支持 |
| 顶层函数 | 支持，但不推荐：如果忘了导入，而这个名字也是标准库的名字（`maxOf`），Kotlin 会悄悄调用标准库函数 |
| 扩展函数 | 不支持（KC11） |
| 带默认参数的 `@SqlFunction` | 不支持 |
| `inline` 的 `@SqlFunction` | 不支持（调用会消失） |
| `vararg` 的 `@SqlFunction` | v0.1 不支持 |
| 函数类型属性 / 函数式接口常量 | 不支持 |

作用于列的函数（`OrderFns.discount(it.price)!! > 10`）无法使用普通索引。
lambdaQL 会为此记录一条 WARN 日志（见 [8.9](08-procedures-functions.md#89-warning-functions-on-columns-and-indexes-sargable)）。

<a id="914-kotlin-limits-kc1-to-kc21"></a>
## 9.14 Kotlin 限制（KC1 到 KC21）

lambdaQL 不会猜测。无法分析某种写法时，它会抛出一个明确的错误，带有代码
`[LQL-xxxx]`、源代码行和提示（见 [11. 错误与语言](../11-errors-and-languages.md)）。
KC1 到 KC5 在加载 Lambda 时就会被发现。在这些情况下 lambdaQL 会立即停止。

| # | 不要这样写 | 原因 | 请改为这样写 |
|---|---|---|---|
| KC1 | 用 `-Xsam-conversions=class` 编译 | 不会创建 `SerializedLambda`。Lambda 会变成一个类 | 保留默认的 `-Xsam-conversions=indy` |
| KC2 | 在 `inline fun` 主体中创建的查询 Lambda（普通、`reified`、`crossinline`） | Lambda 会变成一个类，而不是 Lambda | 把辅助函数改为非 inline，或从调用方传入 Lambda 字面量 |
| KC3 | 对自己定义的 Kotlin `fun interface` 使用可调用引用（`kkey(Order::price)`） | 它会变成一个适配器类 | 使用 Lambda 字面量 `{ it.price }`，或把 `Order::price` 传给 lambdaQL（Java）函数 |
| KC4 | Kotlin 函数类型的值：`val f: (Order) -> Boolean = {...}; where(f)` | lambdaQL 无法读取 `Function1` 的内部 | 用字面量写 `where { ... }` |
| KC5 | `object : ...`（对象表达式），不是 `Serializable` 的函数式接口 | 不是可序列化的 Lambda | Lambda 字面量 |
| KC6 | 用来选择值的 `if`/`when`/`takeIf`，一般的 `if (a) x else y` | v0.1 不支持值分支 | `&&` / `\|\|`。值的 `CASE WHEN` 在计划中 |
| KC7 | *（已被 D25 移除）* | 现在支持比较两个布尔表达式 | – |
| KC8 | 在 Lambda 中使用 `in listOf(1, 2, 3)` 或 `setOf(...)` | 在 Lambda 中构建的集合 | 在外部写 `val ids = listOf(1, 2, 3)`，然后写 `it.status in ids` |
| KC9 | `isBlank()`、`isNullOrBlank()` | Unicode 空白字符没有可移植的 SQL 写法 | `isNullOrEmpty()` 或直接比较 |
| KC10 | *（已被 D21 移除）* | 现在支持区间和 `when`。只有在这些写法推迟发布时，错误才会提示暂时写成 `==` / `\|\|` 链 | – |
| KC11 | 自己定义的非 inline 扩展函数或扩展属性，实体的 `operator` 函数 | lambdaQL 看不到调用的内部 | 改为 `inline fun`，或直接写出条件 |
| KC12 | 对捕获的变量使用 `n++` | Lambda 写入了一个变量 | 在 Lambda 外部计算该值 |
| KC13 | 实体 `==` 实体（`it.customer == c`） | v0.1 不支持（计划：标识符相等） | `it.customer?.id == c.id` |
| KC14 | `isNaN()` | 不支持 | 不要存储 NaN。见 [10.5](10-rules-and-limits.md#105-kotlin-and-sql-differences) |
| KC15 | `@JvmField` 实体属性 | 是字段访问，而不是 getter | 普通的 `var` / `val` |
| KC16 | 被改名（mangled）的 getter 名：`internal` 属性、`value class`、`@get:JvmName` | getter 无法映射到 JPA 属性 | 公开的普通属性 |
| KC17 | 字符串模板中的实体值 | 目前还不支持字符串拼接 | 分别比较各列 |
| KC18 | Java 合成属性引用 `JOrder::price` | Kotlin 编译错误 | `JOrder::getPrice` |
| KC19 | `Order::price.getter` 及其他 `kotlin-reflect` API | 不使用 `kotlin-reflect` | `Order::price` |
| KC20 | 列上的其他标准库函数（`coerceAtLeast`、`trim`、`toLocalDate`、...） | 不在允许列表中 | 计划中（函数映射），或使用带 `@SqlFunction` 的数据库函数 |
| KC21 | 把布尔 elvis 用作值或排序键（`{ it.flag ?: false }`） | 布尔值上的 `coalesce` 在 Oracle 上会失败（ORA-00932） | 按 `{ it.flag }` 排序并指定 NULL 的位置。在条件中 `it.flag ?: false` 没有问题 |

<a id="915-where-lambdaql-differs-from-running-kotlin-code"></a>
## 9.15 lambdaQL 与直接运行 Kotlin 代码的不同之处

这些差异是设计的一部分，不是 bug。

| Kotlin 代码 | lambdaQL（SQL） |
|---|---|
| `!!` 抛出 NPE | `!!` 被忽略。NULL 遵循 SQL 规则 |
| `status == null` 时 `it.status != 1` 为 `true` | 隐藏的 null 检查被忽略：对于 NULL，`e0.status <> 1` 为 UNKNOWN，所以该行不会被返回。要包含它，请写 `it.status == null \|\| ...` |
| 没有 customer 时 `it.customer?.name == null` 为 `true` | 在 `where` 中该路径默认是内连接，所以没有 customer 的订单不会被返回。要包含它们，请使用 `leftJoin` 并写 `c == null \|\| c.name == null`，或者用 `it.customer == null` 再执行一次查询 |
| 主体为 NULL 的 `when` 或区间会进入 `else`，`!in` 为 `true` | 该行不会被返回，在 `NOT` 之下也一样 |
| `===` 是对象同一性 | 值相等 |
| `BigDecimal` 的 `==` 比较标度（`1.0 != 1.00`） | 值相等 |
| `ignoreCase` 使用 Kotlin 的 Unicode 规则 | 使用数据库的排序规则（`ſ`、`İ` 可能不同） |
| `String.length` 计数 UTF-16 单元 | H2 计数 UTF-16 单元，MariaDB 计数码点 |
| `Double.NaN` 的比较 | 捕获的 NaN 会报错。存储的 NaN 遵循数据库 |
| 非空类型 `String` 永远不为 null | 列仍可能为 NULL。lambdaQL 使用 JPA 元数据 |
| 整数 `/` 会丢弃小数部分 | 可能因数据库而异（MariaDB 中 `5/2 = 2.5`） |
| 字符串模板把 NULL 变成 `"null"` | 目前还不支持。SQL 中与 NULL 拼接得到 NULL |

更多规则：[10. 规则与限制](10-rules-and-limits.md)。

---

[Kotlin 路线](README.md) | 上一章：[8. 存储过程与数据库函数](08-procedures-functions.md) | 下一章：[10. 规则与限制](10-rules-and-limits.md) | [本页的 Java 版本：9. Java 说明](../java/09-java-notes.md)
