# 3. 条件（`where`）（Kotlin）

> [!NOTE]
> **状态**
> - **已实现，并在 H2、MariaDB 和 Oracle 上测试：** `where` 条件和 `toList()`（映射到列、HQL 输出、执行查询）。大多数测试使用 **Java** Lambda。
> - **已用 Kotlin Lambda 测试**（[`KotlinSelectTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinSelectTest.kt)，在构建中，kotlinc 2.3.21）：`?.` 路径、捕获值，以及对捕获集合使用 `in`。`!!` 在 [5. 子查询](05-subquery.md)的 Kotlin 测试中测试。这些示例标记为 **(已测试)**。
> - **已设计，尚未测试：** 本章中其他 Kotlin 特有的写法（`?:`、区间、`when`）。它们的字节码已经编译，并用原型检查过（kotlinc 1.7.22 到 2.3.21），许多条件已在 H2 上逐行检查。
> - **也已实现：** 排序与分页的核心部分（[4. 排序与分页](04-order-paging.md)）、子查询（[5. 子查询](05-subquery.md)）、带 `groupBy`、`having` 和 `Agg` 的 `select`、join、派生表和 CTE（[6. 查询列、聚合与分组](06-select-group.md)）、update/insert/delete/upsert（[7. 更新、插入、删除](07-dml.md)）、存储过程与数据库函数（[8. 存储过程与数据库函数](08-procedures-functions.md)）和 Spring Data JPA 模块（[12. Spring Data JPA](12-spring-data-jpa.md)）。
> - 本章中的其他 HQL 是**计划**的输出。一些小细节（例如 `e0`、`j1` 这样的别名）仍可能变化。

[Kotlin 路线](README.md) | [手册首页](../README.md) | 上一章：[2. 入门](02-getting-started.md) | 下一章：[4. 排序与分页](04-order-paging.md) | [本页的 Java 版本](../java/03-where.md)

公共页面：[1. 概述](../01-overview.md) · [11. 错误与语言](../11-errors-and-languages.md)

---

## 3.1 一句话说明核心思想

**lambdaQL 的 Lambda 是查询，不是代码。** lambdaQL 读取 Lambda 的字节码，从不执行它。
因此 Kotlin 的运行时规则（`!!` 引发的 `NullPointerException`、`===` 的对象同一性、编译器添加的 null 检查）都不适用。
lambdaQL 使用它自己的规则。本章将解释这些规则。

如果某种写法没有对应的规则，lambdaQL 不会猜测。它会抛出带有清晰消息的异常（见 [3.13](#313-what-is-rejected)）。

## 3.2 示例实体

所有示例都使用这些实体（完整代码见 [2. 入门](02-getting-started.md#example-entities)）：

```kotlin
@Entity class Order(
    @Id var id: Long? = null,
    var price: Double? = null,
    var status: Int? = null,
    var active: Boolean = false,          // 基本类型：永远不为 NULL
    var description: String? = null,
    @ManyToOne var customer: Customer? = null,
    var updateAt: LocalDateTime? = null,
    @OneToMany(mappedBy = "order") var items: MutableList<Item> = mutableListOf(),
)
@Entity class Customer(@Id var id: Long? = null, var name: String? = null, var grade: Int? = null)
@Entity class Item(@Id var id: Long? = null, @ManyToOne var order: Order? = null, var price: Double? = null, var qty: Int = 0)
```

有几个示例还需要一个额外的属性，它们会注明，例如"`flag: Boolean?`"。

<a id="33-basic-form"></a>
## 3.3 基本形式

```kotlin
val qb = QueryBuilder(entityManagerFactory)

val list = qb.from(Order::class.java)
    .where { it.status == 1 && it.price!! > 100.0 }
    .toList()
// HQL: select e0 from Order e0 where e0.status = 1 and e0.price > 100.0
```

**关于 SQL 块。** `…` 是缩短的 select 列表（实体的所有列），`?` 是绑定参数。这些行来自 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)。该测试使用 Hibernate 6.6.5 在 H2 2.3、MariaDB 10.11 和 Oracle 23.26 上运行每个查询，如果 Hibernate 的 SQL 发生变化，测试就会失败。 该测试使用 Java Lambda。含义相同的 Kotlin Lambda 生成相同的 HQL，因此 SQL 也相同。

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `to-hql`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>100.0
```

**(已测试)** [`KotlinSelectTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinSelectTest.kt)，region `captured`：

```kotlin
val q = qb.from(Order::class.java).where { it.price > min && it.status.toInt() == 1 }
assertEquals("select e0 from Order e0 where e0.price > :p1 and e0.status = 1", q.toHql().hql())
```

`min` 是捕获的局部 `val`（`100.0`），所以它是绑定参数。Kotlin 测试使用 Java 测试实体：`price` 是基本类型 `double`（不需要 `!!`），`status` 是 `short`（所以测试写成 `it.status.toInt() == 1`；lambdaQL 会忽略这个转换）。
更多用 Java 写的查询在 [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java) 中测试（H2、MariaDB 和 Oracle）。这些示例见[本页的 Java 版本](../java/03-where.md)。

你可以多次调用 `where()`。这些条件以 `AND` 连接：

```kotlin
qb.from(Order::class.java)
    .where { it.status == 1 }
    .where { it.active }
    .toList()
// HQL: select e0 from Order e0 where e0.status = 1 and e0.active = true
```

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `many-where`：

```sql
-- H2
select …
from orders o1_0
where o1_0.status=1 and o1_0.active=true

-- MariaDB, Oracle
select …
from orders o1_0
where o1_0.status=1 and o1_0.active=1
```

**HQL 中的值。** 规则取决于值的来源（决策 D37）：

| 值 | 在 HQL 中 | 示例 |
|---|---|---|
| 写在 Lambda 中的常量：数字（包括小数）、字符串、`true`/`false`、`null`、enum 常量、`const val` | **字面量** | `it.price!! > 100.0` → `e0.price > 100.0` |
| 捕获的值：局部 `val` 或 `var`、属性、函数参数 | **绑定参数** | `it.price!! > minPrice` → `e0.price > :p1` |
| 计算出的值：`BigDecimal("1.5")`、`form.text.trim()`、`LocalDateTime.of(...)` | **绑定参数** | `:p1` |

- 常量在每次调用时都相同。因此 SQL 文本不会改变，数据库在制定执行计划时也能看到这个值。
- 字符串常量中的引号会被转义：`"O'Brien"` 变为 `'O''Brien'`。这没有 SQL 注入风险，因为该字符串是编译进你的类里的常量，而不是用户输入。
- 捕获的值可能来自用户，因此它**总是**绑定参数。每次调用只有值会变化。
- enum 常量会带着完整的类名写入，例如 `e0.kind = com.example.OrderKind.GIFT`。

**`!!` 只是给编译器看的。** 当 `price` 是 `Double?` 时，Kotlin 无法编译 `it.price > 100.0`。请写 `it.price!! > 100.0`。lambdaQL 会忽略 `!!`（决策 D23）。`price` 为 NULL 的行不会被返回。

## 3.4 比较运算

| Kotlin | HQL | 说明 |
|---|---|---|
| `it.status == 1` | `e0.status = 1` | |
| `it.status != 1` | `e0.status <> 1` | NULL 行不返回（见 [3.6](#36-null-rules)） |
| `it.price!! > p` | `e0.price > :p1` | 还有 `<`、`<=`、`>=` |
| `it.updateAt!! > since` | `e0.updateAt > :p1` | 在 Kotlin 中日期可以用 `<`、`>` 比较 |
| `it.updateAt!!.isAfter(since)` | `e0.updateAt > :p1` | `isBefore` 生成 `<` |
| `it.description!! < "m"` | `e0.description < 'm'` | `BigDecimal`、`LocalDate`、`BigInteger` 也可以 |
| `it.status!!.compareTo(3) > 0` | `e0.status > 3` | `compareTo(..) op 0` 变为 `op` |

与可空列的比较遵循 **SQL 语义**：
如果列为 NULL，结果为 UNKNOWN，该行不会被返回。（如果执行 Kotlin 代码，`!!` 会抛出 `NullPointerException`。）

**算术运算。** 支持 `+`、`-`、`*` 和一元 `-`。`%` 写作 `mod`。
避免整数 `/`：各数据库对结果的处理不一致（MariaDB 给出 `5/2 = 2.5`，Kotlin 给出 `2`）。`/` 的最终规则尚未确定；第一个版本中可能会拒绝它。

**浮点数：** 对于 `Float`/`Double` 列，lambdaQL 不保证与 Kotlin 完全一致到每一位的结果。
捕获的 `NaN` 或 `Infinity` 值会在查询执行时被拒绝。

<a id="35-and-or-not"></a>
## 3.5 AND、OR、NOT

| Kotlin | HQL |
|---|---|
| `a && b` | `a and b` |
| `a \|\| b` | `a or b` |
| `!a` | `not (a)` |
| 括号 | 按需保留 |

```kotlin
.where { (it.status == 1 || it.active) && it.price!! > minPrice }
// HQL: (e0.status = 1 or e0.active = true) and e0.price > :p1

.where { !(it.status == 1 && it.price!! > minPrice) }
// HQL: not (e0.status = 1 and e0.price > :p1)
```

规则：

- 结果遵循 **SQL 三值逻辑**（TRUE / FALSE / UNKNOWN）。lambdaQL 已经验证，这在 H2、MariaDB 和 Oracle 上返回相同的行。
- 编译器有时会移动 `!` 的位置（德摩根定律，`!(x > y)` 变为 `x <= y`）。这不会改变结果。
- lambdaQL **不会简化你的条件**。如果你写 `it.price!! > p || it.price!! <= p`，它会按原样发送。`price` 为 NULL 的行不会被返回。这是正常的 SQL 行为。
- 请使用 `&&` 和 `||`。中缀函数 `and`、`or`、`xor` 会被拒绝（它们不会短路）。

**给出 `true`/`false` 的 `if` 和 `when`。** 只允许以下简短写法。它们等同于 `AND`、`OR`：

| Kotlin | 等同于 |
|---|---|
| `if (c) x else false` | `c && x` |
| `if (c) true else x` | `c \|\| x` |
| `when { a -> true; b -> true; else -> false }` | `a \|\| b` |

其他任何 `if (c) x else y`，以及任何用于选择**值**的 `if` 或 `when`（`it.status == (if (flag) 1 else 2)`），都会被拒绝（限制 KC6）。
如果选择只取决于捕获的值，请在 Lambda 之前计算出该值：

```kotlin
val wanted = if (flag) 1 else 2
.where { it.status == wanted }
```

<a id="36-null-rules"></a>
## 3.6 NULL 规则

这是最重要的部分。有两条简单的原则：

1. **在你写了 null 检查的地方，lambdaQL 使用它。**（`== null`、`!= null`、`?.`、`?:`，以及两个可空值之间的 `==`）
2. **在你没有写的地方，lambdaQL 使用 SQL 语义。** NULL 列使比较结果为 UNKNOWN，该行不会被返回。

Kotlin 编译器会在字节码中添加**隐藏的** null 检查（例如 `status` 为 `Int?` 时的 `it.status == 1`）。lambdaQL 会**忽略**它们（决策 D22）。只有源代码中的 null 检查才算数。
因此，含义相同的 Kotlin Lambda 和 Java Lambda 会生成相同的 SQL。

### 3.6.1 规则表

| # | Kotlin 写法 | HQL | NULL 行为 |
|---|---|---|---|
| R12 | 可空列与常量或非空值：`it.status == 1`、`it.id == id`（`id: Long`） | `e0.status = 1`、`e0.id = :p1` | SQL 语义。列为 NULL：该行不返回 |
| R12 | `it.status != 1` | `e0.status <> 1` | SQL 语义。列为 NULL：该行**不**返回 |
| R1 | 大小比较：`it.price!! > p`、`it.status!! <= 3` | `e0.price > :p1`、`e0.status <= 3` | SQL 语义 |
| R11 | 两个都可能为 NULL 的值：`it.status == q`（`q: Int?`）、`it.description == text`（`String?`）、enum `==` | `e0.status is not distinct from :p1` | **null 安全的值比较。** `null == null` 为 true。只有一侧为 null：false |
| R11 | `it.status != q`（两侧都可空） | `e0.status is distinct from :p1` | null 安全。当 `q` 不为 null 时，NULL 行**会**被返回 |
| R3 | `x == null`、`x != null` | `x is null`、`x is not null` | |
| R17 | `===`、`!==` | 与 `==`、`!=` 相同 | 值比较，而不是对象同一性 |
| R9 | 捕获的值 | 绑定参数 | 见 [3.6.4](#364-captured-null-values) |

<a id="362--compares-values"></a>
### 3.6.2 `==` 比较的是值

Kotlin 的 `==` 本来就比较值。在 lambdaQL 中，`===` 也比较**值**（规则 R17）。
查询中没有对象同一性。两个值相同的不同 `Long` 装箱对象是相等的。

```kotlin
val status: Int? = request.status    // 可能为 null
.where { it.status == status }
// HQL: e0.status is not distinct from :p1
//   status = 3    -> status 为 3 的行
//   status = null -> status 为 NULL 的行
```

Hibernate 针对各数据库生成 `is not distinct from` 的写法（已验证）：

| 数据库 | SQL |
|---|---|
| H2 | `status is not distinct from ?` |
| MariaDB | `status <=> ?` |
| Oracle | `decode(status, ?, 0, 1) = 0` |

当 lambdaQL 知道某一侧永远不会为 NULL 时，它会改为生成简单的 `=`。结果相同，而且数据库可以使用索引（见 [10.2](10-rules-and-limits.md#102-sargable-rule-index-friendly-sql)）。
"永远不为 NULL"是指：`@Id`、`nullable = false` 的列、基本类型属性（`Int`、`Long`、`Double`、`Boolean`），或在查询执行时不为 null 的捕获值。

```kotlin
.where { it.description == text }    // 查询执行时 text 不为 null
// HQL: e0.description = :p1
.where { it.description != text }
// HQL: (e0.description <> :p1 or e0.description is null)
```

**永远不会是 NULL 的列：`it.id == id`。** 在示例实体中，`id` 是 `Long?`。Kotlin 把 `it.id == id`（捕获的 `id: Long`）编译为一个 null 检查加一个比较。
直接翻译会得到 `not (e0.id is null or e0.id <> :p1)`。这个形式是正确的，但数据库难以很好地使用索引。
id 永远不会是 NULL，因此对它的 `is null` 总是假。lambdaQL 把它折叠掉，写成 `e0.id = :p1`。

同样的规则也适用于基本类型属性和 `nullable = false` 的列，但只在路径**没有关联步骤**时适用。`it.customer?.id` 可能是 NULL，因为订单可以没有客户，所以它的 null 检查会保留。

```kotlin
.where { it.id == id }              // id: Long (captured)
// HQL: e0.id = :p1
.where { it.id == null }
// HQL: 1=0
.where { it.customer?.id == null }
// HQL: e0.customer.id is null
```

相同的 HQL 用 Java Lambda 在 [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java) 中测试（region `never-null`，见[本页的 Java 版本](../java/03-where.md#362--on-objects-compares-values)）。Java 测试写的条件正是 Kotlin 的 `it.id == id` 编译后的形式。

> [!WARNING]
> **Kotlin 的非空类型不能作为证明。** 属性 `var code: String` 仍然可能从数据库中得到 NULL，
> 因为 Hibernate 会不加检查地把列值写入字段。
> lambdaQL 看的是 JPA 元数据，而不是 Kotlin 类型。

### 3.6.3 自己编写 null 检查

```kotlin
// 只返回 status 不为 NULL 且不等于 1 的行：
.where { it.status != 1 }
// HQL: e0.status <> 1

// 同时包含 status 为 NULL 的行：
.where { it.status == null || it.status != 1 }
// HQL: e0.status is null or e0.status <> 1
```

> [!IMPORTANT]
> 这与执行 Kotlin 代码不同。在 Kotlin 中，`null != 1` 为 `true`。
> 在 lambdaQL 中，`it.status != 1` **不会**返回 NULL 行，因为你没有写 null 检查。
> 像 `1` 这样的常量不可空。用 Java 写的相同条件会生成相同的 SQL。

比较常量和可空变量：

```kotlin
val q: Int? = 3
.where { it.status != 3 }    // e0.status <> 3                                 不返回 NULL 行
.where { it.status != q }    // (e0.status <> :p1 or e0.status is null)        返回 NULL 行
```

第二种写法比较的是两个都可能为 NULL 的值。Kotlin 为 null 定义了结果，因此 lambdaQL 保留它（null 安全）。

对 `String?` 使用 `it.description.equals(text)` 与 `==` 相同（null 安全）。

关于 `?.`、`?:` 和 `!!` 的更多内容：[9. Kotlin 说明](09-kotlin-notes.md)。

<a id="364-captured-null-values"></a>
### 3.6.4 捕获的 null 值

| 写法 | 捕获的值为 `null` |
|---|---|
| null 安全的写法：`it.status == q`、`it.description != text` | 允许 |
| 大小比较：`it.price!! > min!!`，以及所有 `<`、`<=`、`>`、`>=` | 查询执行时**抛出异常** |
| 与 `in` 一起使用的集合中有 null 元素 | 查询执行时**抛出异常** |

原因：Kotlin 在这里会抛出 `NullPointerException`（`min!!`）。把它转换为 `IS NULL` 会返回 Kotlin 永远不会返回的行。

**规则的例外：null 守卫。** 如果先对同一个捕获值做了 null 检查，就允许 null：

```kotlin
val min: Double? = request.minPrice   // 可能为 null
.where { min == null || it.price!! > min }
// min == null:  select e0 from Order e0                        （不过滤）
// min = 100.0:  select e0 from Order e0 where e0.price > :p1
```

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `captured-null-guard`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
```

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `captured-null-guard#2`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.price>?
```

工作方式：

- 只使用捕获值和常量的部分，会在查询执行时在应用程序中决定。决定的顺序是从左到右，并且像 Kotlin 一样短路求值。
- `min == null` 时，守卫为 true，所以整个条件为 true。不会有任何过滤。与 `min` 的比较永远不会被写出（Kotlin 也永远不会执行它）。
- `min = 100.0` 时，守卫为 false，所以只剩下 `e0.price > :p1`。
- Lambda 仍然只分析一次。只有 HQL 文本取决于值。
- 守卫必须写在前面。`it.price!! > min!! || min == null` 在 `min == null` 时会在查询执行时抛出异常，与 Kotlin 的 `NullPointerException` 相同。

这一点目前还没有 Kotlin 测试。用 Java 写的同一个守卫已经测试过；见[本页的 Java 版本](../java/03-where.md#364-captured-null-values)。

## 3.7 布尔列

在示例实体中，`active` 是 Kotlin `Boolean`（JVM 基本类型，永远不为 NULL）。
带有 `flag` 的行针对的是可空列 `var flag: Boolean? = null`。

| Kotlin | HQL | NULL 行 |
|---|---|---|
| `it.active` | `e0.active = true` | –（永远不为 NULL） |
| `!it.active` | `e0.active = false` | – |
| `it.flag == true` | `e0.flag is not distinct from true` | 不返回 |
| `it.flag != true` | `e0.flag is distinct from true` | **返回**（与 Kotlin 一致） |
| `it.flag ?: false` | `(e0.flag is not null and e0.flag = true)` | 不返回 |
| `!(it.flag ?: false)` | `(e0.flag is null or not (e0.flag = true))` | **返回** |
| `it.flag!!` | `e0.flag = true` | 不返回（`!!` 被忽略） |

说明：

- lambdaQL 不使用 `is not true` 或 `coalesce(flag, false)`。它们在 Oracle 上会失败（ORA-00932）。
- 对较大条件的取反总是写作 `not (...)`。lambdaQL 永远不会在条件外面写 `(...) = false` 或 `(...) is null`，因为 Oracle 23 在 `WHERE` 中对它们的求值是错误的。
- `it.flag ?: false` **在条件中**没有问题。作为值或排序键时，它会被拒绝（限制 KC21）。

**比较两个布尔值**（`==`/`!=`）是支持的（决策 D25）：

```kotlin
.where { it.active == (it.status!! > s) }
// HQL: (e0.active = true and e0.status > :p1) or (e0.active = false and e0.status <= :p1)
```

lambdaQL 用 `AND`/`OR`（而不是 `CASE`）来写，这样数据库可以使用索引。
如果两侧共用同一个条件（例如 `it.active == it.active`），该写法会被拒绝。

## 3.8 字符串

- 字符串上的 `==` 和 `equals` 使用**数据库的排序规则（collation）**。
  - MariaDB 的 `_ci` 排序规则会忽略大小写和尾随空格。
  - Oracle 把 `''`（空字符串）存储为 NULL。因此在 Oracle 上，`it.description == ""` 与 Kotlin 的结果不同。
- 作用于列的 Kotlin 字符串函数（`startsWith`、`endsWith`、`contains`、`lowercase()`、`length`、`isNullOrEmpty()`、`ignoreCase = true`）已有 Kotlin 设计（规则 R21）。见 [9. Kotlin 说明](09-kotlin-notes.md#97-strings)。
- 作用于列的 `isBlank()`、`isNullOrBlank()`、`trim()` 以及其他标准库函数会被拒绝（限制 KC9、KC20）。
- **只使用捕获值**的字符串模板在设计上可以使用：`"$a-$b" == it.description` 生成 `:p1 = e0.description`，字符串在应用程序中构建。但目前分析核心仍会拒绝 Lambda 内部的所有字符串拼接，因此暂时请在 Lambda 之前构建字符串。
- 使用**实体值**的字符串模板或 `+`（`"${it.description}-x"`）暂不支持（KC17，计划中）。

<a id="39-in-with-a-captured-collection"></a>
## 3.9 使用捕获集合的 IN

在 Lambda **外部**构建集合，并使用 `in`：

```kotlin
val ids = listOf(1L, 2L, 3L)

qb.from(Order::class.java).where { it.id in ids }.toList()
// HQL: e0.id in (:p1)

qb.from(Order::class.java).where { it.id !in ids }.toList()
// HQL: e0.id not in (:p1)
```

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `not-in`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.id not in (?,?)
```

**(已测试)** [`KotlinSelectTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinSelectTest.kt)，region `in`：

```kotlin
val q = qb.from(Order::class.java).where { it.status in statuses }
assertEquals("select e0 from Order e0 where e0.status in (:p1)", q.toHql().hql())
```

`statuses` 是捕获的 `listOf<Short>(1, 3)`。

`!in` 会变成 `not in`。id 不可能为 NULL，所以没有 `is null` 部分。对于可能为 NULL 的列，lambdaQL 会加上 `x is null or`，因为当列表中没有 `null` 时，Kotlin 的 `!in` 对 NULL 值返回 true。

| 情况 | 结果 |
|---|---|
| 集合是捕获的 `List`、`Set` 或其他 `Collection` | 支持 |
| 集合中有 `null` 元素 | 查询执行时抛出异常 |
| 空集合 | `in` 为 FALSE，`!in` 为 TRUE（Hibernate 生成 `1=0` / `1=1`） |
| 1,500 个元素 | 在 H2、MariaDB 和 Oracle 23ai 上用一条语句即可运行（较旧的 Oracle 版本有 1,000 的限制；未测试） |
| 集合是实体集合：`item in it.items` | **拒绝**。请使用 `exists()` 子查询（[5. 子查询](05-subquery.md)） |
| 集合在 Lambda 内部创建：`it.status in listOf(1, 2)` | **拒绝**（KC8）。请在外部创建并捕获它 |

Kotlin 还有区间（`it.status in 1..5`）和带常量的 `when`。见 [3.14](#314-ranges-kotlin-only) 和 [3.15](#315-when-kotlin-only)。

<a id="310-association-paths"></a>
## 3.10 关联路径

`it.customer?.name` **不会被执行**。它只是指代一个列："关联客户的 `name` 列"。
lambdaQL 用 JPA Metamodel 检查路径的每一步：

| 路径步骤 | HQL | 示例 |
|---|---|---|
| 对一关联（`@ManyToOne`、`@OneToOne`） | 带别名的 `join`（默认是内连接，见下文） | `it.customer?.name` -> `join e0.customer j1` ... `j1.name` |
| 嵌入（`@Embedded`） | 同一张表，不需要 join | 如果 `Order` 有一个嵌入的 `address`：`it.address.city` -> `e0.address.city` |
| 以目标 `@Id` 结尾的路径 | 外键列，不需要 join | `it.customer?.id` -> `e0.customer.id` |
| 关联本身与 `null` 比较 | 外键列，不需要 join | `it.customer == null` -> `e0.customer is null` |
| 集合（`@OneToMany`、`@ManyToMany`） | **拒绝** | `it.items...` -> 请使用 `exists()` 子查询 |

`it.customer?.name`、`it.customer!!.name` 以及（对于 Java 实体）`it.customer.name` 都表示**同一个列** `j1.name`。

在 `where` 中，关联路径默认是**内连接（inner join）**（决策 D36）：

```kotlin
qb.from(Order::class.java)
    .where { it.customer?.name == name }
    .toList()
// HQL: select e0 from Order e0 join e0.customer j1 where j1.name = :p1
```

**(已测试)** [`KotlinSelectTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinSelectTest.kt)，region `safe-call`（用常量代替 `name`）：

```kotlin
// it.customer?.name reads as the path customer.name (inner join: orders without a customer are left out)
val q = qb.from(Order::class.java).where { it.customer?.name == "Kim" }
assertEquals("select e0 from Order e0 join e0.customer j1 where j1.name = 'Kim'", q.toHql().hql())
```

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `inner-join`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name=?
```

- **没有客户的**订单不在结果中。因为没有可以连接的客户行。
- lambdaQL 不会替你添加 null 检查。在路径前面不需要写 `it.customer != null &&`。

**null 守卫会保留没有客户的订单。** 如果条件对没有客户的订单也可能为 true，lambdaQL 会对该路径使用 `left join`。
如果用内连接，Kotlin 和 SQL 会给出不同的行：Kotlin 会返回这个订单，但内连接会把它丢掉（决策 D36）。

```kotlin
.where { it.customer == null || it.customer?.name == name }
// HQL: select e0 from Order e0 left join e0.customer j1 where e0.customer is null or j1.name = :p1

.where { it.customer != null && it.customer?.name == name }
// HQL: select e0 from Order e0 join e0.customer j1 where e0.customer is not null and j1.name = :p1
```

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `null-guard`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.id is null or c1_0.name=?
```

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `null-guard#2`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.id is not null and c1_0.name=?
```

在 `left join` 之后，Hibernate 把 `e0.customer is null` 写成 `c1_0.id is null`。含义相同：订单没有客户。

- `||` 的其他部分也可能保留订单。`it.status == 1 || it.customer?.name == name` 也会使用 `left join`，因为在 Kotlin 中，状态为 1 的订单即使没有客户也会匹配。
- 如果另一个 `where` 调用无论如何都会去掉这些订单（例如 `.where { it.customer?.name != null }`），join 仍然是内连接。
- null 守卫目前还没有 Kotlin 测试。用 Java 写的同样的条件已经测试过；见[本页的 Java 版本](../java/03-where.md#310-association-paths)。

要查找**没有**客户的订单，请比较关联本身。这会检查外键列，不需要 join：

```kotlin
.where { it.customer == null }
// HQL: select e0 from Order e0 where e0.customer is null
```

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `no-association`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id is null
```

> [!WARNING]
> **`?.` 不会把没有客户的订单带回来。** 在 Kotlin 中，没有客户时 `it.customer?.name == null` 为 `true`。
> 在 lambdaQL 中，该路径在这里是内连接，因此 `it.customer?.name == null` 只返回客户没有名字的订单。
> 要包含没有客户的订单，请写 `it.customer == null || it.customer?.name == null`。这个 null 守卫会让该路径变成 `left join`（见上文）。

更多事实：

- 同一条路径只得到一个别名。`where`、`orderBy` 和 `select` 共享这个别名。
- `it.customer?.id == 5L` 使用外键列 `e0.customer.id`。不需要 join。
- **只**在 `orderBy` 或 `select` 中使用的路径是 `left join`，因此那里不会丢失行（规则 S8 和 J1）。如果 `where` 也使用同一条路径，`orderBy` 和 `select` 会使用 `where` 的 join。它是内连接，除非 null 守卫把它变成了 `left join`。
- 比较两个实体（`it.customer == c`）在**计划中**（KC13）。目前请比较 id：`it.customer?.id == c.id`（这里 `c` 是一个捕获的对象）。

<a id="311-captured-values"></a>
## 3.11 捕获的值

Lambda 可以使用外部的值：局部值、属性，以及捕获对象的属性。

```kotlin
val since = LocalDateTime.of(2026, 1, 1, 0, 0)
.where { it.updateAt!! > since }                 // 局部 val
.where { it.status == defaultStatus }            // 外围类的属性
.where { it.price!! > filter.minPrice!! }        // 捕获对象的属性
.where { it.updateAt!! > LocalDateTime.of(2026, 1, 1, 0, 0) }   // 不使用实体的调用
```

| 捕获的内容 | lambdaQL 如何使用 |
|---|---|
| `val`（包括 `Long?`、`String` 等） | 绑定参数 |
| `var` | 绑定参数。**每次执行查询时**都会读取其值 |
| 外围类的属性（包括 `private`） | 每次执行查询时读取 |
| `companion object` 的 `val`、顶层 `val`、`object` 的 `val` | 绑定参数 |
| `const val` | 作为字面量写入 HQL（D37） |
| 在 Lambda 内部修改捕获的变量（`n++`） | **拒绝**（KC12） |

规则：

- 不使用实体的部分会在**每次执行查询时**在应用程序中计算。结果成为绑定参数。
- Lambda 只分析一次，结果会被缓存。每次调用时都会读取新的捕获值。
- 只有实体部分会变成 SQL。

<a id="312-errors-that-depend-on-order-of-evaluation"></a>
## 3.12 取决于求值顺序的错误

SQL 不保证 `AND`/`OR` 从左到右求值。因此像下面这样的"守卫"在 SQL 中并不安全：

```kotlin
qb.from(Item::class.java)
    .where { it.qty != 0 && it.price!! / it.qty > 2 }   // 会被拒绝
```

守卫之下的 `/`、`%` 和类型转换会被拒绝。请把条件改写为不用除法的形式，例如 `it.qty > 0 && it.price!! > 2.0 * it.qty`（当 `qty` 为正数时返回相同的行）。

<a id="313-what-is-rejected"></a>
## 3.13 会被拒绝的内容

lambdaQL 会抛出带有 `[LQL-xxxx]` 错误码和源代码行号的异常。它不会猜测。
大多数结构问题会在分析 Lambda 时（首次使用时）被发现。有问题的捕获值（null、NaN）会在查询执行时被发现。
见 [11. 错误与语言](../11-errors-and-languages.md)。

| 写法 | 示例 | 怎么做 |
|---|---|---|
| 局部 `val`/`var`、循环、包含多条语句的代码块 | `{ val x = 1; it.status == x }` | 写成一个表达式 |
| 作为值的 `if`/`when`（KC6） | `it.status == (if (flag) 1 else 2)` | 在 Lambda 之前计算出值 |
| 一般的 `if (c) x else y`（KC6） | | 使用 [3.5](#35-and-or-not) 中允许的写法 |
| 中缀 `and`、`or`、`xor` | `it.active and (it.status == 1)` | 使用 `&&`、`\|\|` |
| 使用实体值的字符串模板或 `+`（KC17） | `it.description == "A" + it.status` | 分别比较各列（计划中） |
| 在 Lambda 中创建的集合（KC8） | `it.status in listOf(1, 2)` | 在外部创建并捕获它 |
| Lambda 内部的 Lambda | `ids.any { id -> id == it.id }` | 使用 `in`（[3.9](#39-in-with-a-captured-collection)）或子查询 |
| 不支持的标准库函数（KC9、KC20） | `it.description?.trim()`、`isBlank()` | `isNullOrEmpty()` 或直接比较 |
| 你自己写的非 inline 扩展函数（KC11） | `it.isOpen()` | 改为 `inline fun`，或直接写出条件 |
| 实体上的其他方法 | `it.calcSomething()` | 只使用属性 |
| 实体集合路径 | `it.items.size > 0` | `exists()` 子查询 |
| 实体与实体比较（KC13） | `it.customer == c` | 比较 id（计划中：实体比较） |
| 守卫之下的 `/`、`%`、类型转换 | 见 [3.12](#312-errors-that-depend-on-order-of-evaluation) | 去掉守卫或修改条件 |
| 大小比较中捕获的 null 值 | `min == null` 时的 `it.price!! > min!!` | 添加 null 守卫 |
| 捕获的 `NaN` / `Infinity` 值 | `it.price!! > Double.NaN` | 不要发送 NaN |
| `isNaN()`（KC14） | `it.price!!.isNaN()` | – |
| 修改捕获的变量（KC12） | `{ n++; it.active }` | 在 Lambda 外部计算 |
| 匿名对象、函数类型的值（KC4、KC5） | 带有 `val f: (Order) -> Boolean` 的 `where(f)` | 传入 Lambda 字面量 |

异常类型（都继承自 `LambdaQLException`）：

| 异常 | 何时抛出 |
|---|---|
| `LambdaAnalysisException` | 无法读取 Lambda（不是可序列化的 Lambda、被编译为类等） |
| `UnsupportedLambdaShapeException` | 不支持的结构（循环、作为值的 `if` 等） |
| `TranslationException` | 无法翻译其含义（未知函数、集合路径等） |

所有 Kotlin 限制：[9. Kotlin 说明](09-kotlin-notes.md#914-kotlin-limits-kc1-to-kc21)。所有规则：[10. 规则与限制](10-rules-and-limits.md)。

<a id="314-ranges-kotlin-only"></a>
## 3.14 区间（仅限 Kotlin）

以下写法都受支持（决策 D21）。

```kotlin
.where { it.status in 1..5 }          // e0.status between 1 and 5
.where { it.status !in 1..5 }         // not (e0.status between 1 and 5)
.where { it.status in 1 until 5 }     // e0.status >= 1 and e0.status < 5
.where { it.status in 1..<5 }         // e0.status >= 1 and e0.status < 5
.where { it.status in lo..hi }        // e0.status between :p1 and :p2   （lo、hi 为捕获的值）
.where { it.price!! in 1.0..10.0 }    // e0.price between 1.0 and 10.0
.where { it.status in 5 downTo 1 }    // 支持（与 1..5 返回相同的行）
```

| 区间写法 | 是否支持 |
|---|---|
| `a..b`、`a until b`、`a..<b`、`a downTo b` | 是 |
| `!in` | 是，`not (...)` |
| `LocalDate`、`LocalDateTime`、`BigDecimal`、`String` 区间 | 是（它们被编译为 `compareTo`） |
| `step` | 否 |

**可空的主体。** Kotlin 允许对 `Int?` 和 `Long?` 使用 `in`。lambdaQL **不会**添加 null 守卫（D22）。
值为 NULL 的行不会被返回，在 `!in` 下也同样不会被返回（SQL 规则）。
对于其他可空类型（例如 `LocalDateTime?`），Kotlin 无法编译 `in`；请添加 `!!`：

```kotlin
.where { it.updateAt!! in from..to }  // e0.updateAt between :p1 and :p2
```

<a id="315-when-kotlin-only"></a>
## 3.15 `when`（仅限 Kotlin）

`when` **作为条件**时受支持：每个分支都必须以 `true` 或 `false`（或一个条件）结尾。
lambdaQL 会把它转换为 `IN`、`OR` 或 `AND`。

```kotlin
.where { when (it.status) { 1, 2 -> true; else -> false } }
// e0.status in (1, 2)

.where { when (it.description) { "a", "b", "c" -> true; else -> false } }
// e0.description in ('a', 'b', 'c')

.where { when (it.status) { in 1..5 -> true; else -> false } }
// e0.status between 1 and 5

.where { when { it.status == 1 -> true; it.active -> true; else -> false } }
// e0.status = 1 or e0.active = true

.where { when (it.status) { 1 -> it.active; else -> false } }
// e0.status = 1 and e0.active = true
```

enum 主体也可以使用（这里使用了一个额外的属性 `kind: OrderKind?`，`OrderKind` 位于包 `com.example` 中）：

```kotlin
.where { when (it.kind) { OrderKind.EXPRESS, OrderKind.GIFT -> true; else -> false } }
// e0.kind in (com.example.OrderKind.EXPRESS, com.example.OrderKind.GIFT)
```

可空的主体不会得到 null 守卫（D22）。主体为 NULL 的行不会被返回，
在 `NOT` 下也同样如此。如果执行 Kotlin 代码，会进入 `else` 分支；lambdaQL 使用 SQL 语义。这是有意设计的规则。

**选择值**的 `when`（或 `if`）不受支持（KC6）：

```kotlin
// 不支持
.where { (if (it.active) it.price else 0.0)!! > 10.0 }
```

请改用 `&&` 和 `||` 来写条件。

---

[Kotlin 路线](README.md) | 上一章：[2. 入门](02-getting-started.md) | 下一章：[4. 排序与分页](04-order-paging.md) | [本页的 Java 版本](../java/03-where.md)
