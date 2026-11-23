<a id="3-conditions-where-kotlin"></a>
# 3. 条件（`where`）（Kotlin）

[Kotlin 路线](README.md) | [手册首页](../README.md) | 上一章：[2. 入门](02-getting-started.md) | 下一章：[4. 排序与分页](04-order-paging.md) | [本页的 Java 版本](../java/03-where.md)

公共页面：[1. 概述](../01-overview.md) · [11. 错误与语言](../11-errors-and-languages.md)

---

<a id="31-the-idea-in-one-sentence"></a>
## 3.1 一句话说明核心思想

**lambdaQL 的 Lambda 是查询，不是代码。** lambdaQL 读取 Lambda 的字节码，从不执行它。
因此 Kotlin 的运行时规则（`!!` 引发的 `NullPointerException`、`===` 的对象同一性、编译器添加的 null 检查）都不适用。
lambdaQL 使用它自己的规则。本章将解释这些规则。

如果某种写法没有对应的规则，lambdaQL 不会猜测。它会抛出带有清晰消息的异常（见 [3.13](#313-what-is-rejected)）。

<a id="32-example-entities"></a>
## 3.2 示例实体

所有示例都使用这些实体（完整代码见 [2. 入门](02-getting-started.md#example-entities)）。

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

有几个示例还需要一个额外的属性。这些示例会注明，例如"`flag: Boolean?`"。

<a id="33-basic-form"></a>
## 3.3 基本形式

```kotlin
val qb = QueryBuilder(entityManagerFactory)

val list = qb.from(Order::class.java)
    .where { it.status == 1 && it.price!! > 100.0 }
    .toList()
// HQL: select e0 from Order e0 where e0.status = 1 and e0.price > 100.0
```

**关于 SQL 块。** `…` 是缩短的 select 列表（实体的所有列），`?` 是绑定参数。这些行来自 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)。该测试使用 Hibernate 7.4 在 H2 2.3、MariaDB 10.11 和 Oracle 23.26 上运行每个查询，如果 Hibernate 的 SQL 发生变化，测试就会失败。该测试使用 Java Lambda。含义相同的 Kotlin Lambda 生成相同的 HQL，因此 SQL 也相同。

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

`min` 是捕获的局部 `val`（`100.0`），所以它是绑定参数。Kotlin 测试使用 Java 测试实体：`price` 是基本类型 `double`，所以不需要 `!!`；`status` 是 `short`，所以测试写成 `it.status.toInt() == 1`。lambdaQL 会忽略这个转换。
更多用 Java 写的查询在 [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java) 中测试（H2、MariaDB 和 Oracle）。这些示例见[本页的 Java 版本](../java/03-where.md)。

可以多次调用 `where()`。这些条件以 `AND` 连接。

```kotlin
qb.from(Order::class.java)
    .where { it.status == 1 }
    .where { it.active }
    .toList()
// HQL: select e0 from Order e0 where e0.status = 1 and e0.active = true
```

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `many-where`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.active=true
```

**HQL 中的值。** 规则取决于值的来源（决策 D37）。

| 值 | 在 HQL 中 | 示例 |
|---|---|---|
| 写在 Lambda 中的常量：数字（包括小数）、字符串、`true`/`false`、`null`、enum 常量、`const val` | **字面量** | `it.price!! > 100.0` → `e0.price > 100.0` |
| 捕获的值：局部 `val` 或 `var`、属性、函数参数 | **绑定参数** | `it.price!! > minPrice` → `e0.price > :p1` |
| 计算出的值：`BigDecimal("1.5")`、`form.text.trim()`、`LocalDateTime.of(...)` | **绑定参数** | `:p1` |

- 常量在每次调用时都相同。因此 SQL 文本不会改变，数据库在制定执行计划时也能看到这个值。
- 字符串常量中的单引号会被转义：`"O'Brien"` 变为 `'O''Brien'`。该字符串是编译进类里的常量，而不是用户输入，因此没有 SQL 注入风险。
- 捕获的值可能来自用户，因此它是绑定参数。每次调用只有值会变化。
- 例外只有一个。与整数列比较的小数捕获值写成小数字面量（`2.5BD`）（参见 [3.4](#34-comparisons) 中的“整数列与小数值”）。只写入转换为数字后的值，因此没有 SQL 注入风险。
- enum 常量会带着完整的类名写入，例如 `e0.kind = com.example.OrderKind.GIFT`。

**`!!` 只是给编译器看的。** 当 `price` 是 `Double?` 时，Kotlin 无法编译 `it.price > 100.0`。请写 `it.price!! > 100.0`。lambdaQL 会忽略 `!!`（决策 D23）。`price` 为 NULL 的行不会被返回。

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
如果列为 NULL，结果为 UNKNOWN，该行不会被返回。（如果执行 Kotlin 代码，`!!` 会抛出 `NullPointerException`。）

**算术运算。** 支持 `+`、`-`、`*`、`/` 和一元 `-`。`%` 写作 `mod`。
`/` 也可以使用，但请避免涉及列的整数 `/`。它在 SQL 中计算，因此各数据库的结果不同（MariaDB 给出 `5/2 = 2.5`，Kotlin 给出 `2`）。统一列侧 `/` 结果的规则尚未确定。
捕获值之间的计算（下面的 `n / 2.0`）在查询之前完成，因此没有这种差异。

下面示例中的 `cnt` 是测试实体 `Order`（Java）的整数列（`Integer cnt`）。[3.2](#32-example-entities) 的示例实体中没有它。

**列上的算术运算与索引**（决策 D50）。像 `it.cnt * 2 > n` 这样对列做算术运算时，数据库无法使用索引。lambdaQL 以 `WARN` 级别记录 `LQL-5012`，在 `SargableMode.STRICT` 下分析失败（`LQL-3066`）。
把运算移到值一侧时，请像 `it.cnt > n / 2.0` 这样用小数相除。整数除法 `n / 2` 会得到不同的行。如果乘的是负数，请反转比较方向。详见 [10.2](10-rules-and-limits.md#db-functions-on-a-column)。

**整数列与小数值**（决策 D48 B8）。把整数列与小数值比较时，lambdaQL 按写出的值比较，不做舍入，也不排除值。

- 写在 Lambda 中的常量保持为字面量。`it.cnt >= 2.5` 变为 `e0.cnt >= 2.5`。
- 捕获的值也写成小数字面量。HQL 为 `e0.cnt >= 2.5BD`，没有绑定参数。`BD` 是 HQL 中 `BigDecimal` 字面量的后缀。
- 含有这种元素的 IN 列表会把所有元素都写成小数字面量（`e0.cnt in (2.5BD, 3.0BD)`）。

这是捕获值以绑定参数传递这一原则（D37）的例外。当与整数列比较的参数绑定了小数时，即使指定 `BigDecimal` 类型，Hibernate 7 也会拒绝（`QueryArgumentException`，实测）。

以前，值会被截断为列类型，`2.5` 变成 `2`，返回错误的行。Hibernate 6.6 会拒绝以整数列类型绑定的小数（`CoercionException`），并会静默截断 IN 列表中的元素（在三种数据库上实测）。
没有采用改写为整数比较（`>= 3`）的方式。那样执行的 SQL 会与 Lambda 不同；当实体映射（`Integer`）与数据库列类型（例如带小数的 `DECIMAL`）不同时，还会返回错误的行。

结果在 H2、MariaDB 10.11 和 Oracle 23.26 上正确（实测）。在三种数据库上，SQL 都像 `cnt>=2.5`、`cnt in (2.5,3.0)` 这样保持列不变，直接与小数比较。
MariaDB 的 `EXPLAIN` 显示索引范围扫描（`type=range`）。在 Oracle 上，列没有附加转换，因此是可以使用索引的形式。给出索引提示时为 `INDEX RANGE SCAN`；在 3000 行的表上不加提示时，与整数字面量（`cnt >= 99`）一样选择全表扫描（均为实测）。

这一点没有 Kotlin 测试。由 Java 测试（[`SqlMeaningTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlMeaningTest.java)，region `whole-number`）验证。

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
- lambdaQL **不会简化条件**。写 `it.price!! > p || it.price!! <= p` 时，它会按原样发送。`price` 为 NULL 的行不会被返回。这是正常的 SQL 行为。
- 请使用 `&&` 和 `||`。中缀函数 `and`、`or`、`xor` 会被拒绝（它们不会短路）。

**给出 `true`/`false` 的 `if` 和 `when`。** 只允许以下简短写法。它们分别等同于 `AND`、`OR`。

| Kotlin | 等同于 |
|---|---|
| `if (c) x else false` | `c && x` |
| `if (c) true else x` | `c \|\| x` |
| `when { a -> true; b -> true; else -> false }` | `a \|\| b` |

其他任何 `if (c) x else y`，以及任何用于选择**值**的 `if` 或 `when`（`it.status == (if (flag) 1 else 2)`），都会被拒绝（限制 KC6）。
如果选择只取决于捕获的值，请在 Lambda 之前计算出该值。

```kotlin
val wanted = if (flag) 1 else 2
.where { it.status == wanted }
```

<a id="36-null-rules"></a>
## 3.6 NULL 规则

这是最重要的部分。有三条简单的原则。

1. **在写了 null 检查的地方，lambdaQL 使用它。**（`== null`、`!= null`、`?.`、`?:`）
2. **在没有写的地方，lambdaQL 使用 SQL 语义。** 列为 NULL 时，比较结果为 UNKNOWN，该行不会被返回。
   实体值之间的 `==`、`!=` 也适用同样的规则（决策 D48）。
   当结果可能与 Kotlin 不同时，lambdaQL 以 `DEBUG` 级别记录 `LQL-5009`。
3. **捕获的值先被计算，比较同样遵循 SQL 语义。** 捕获的 `null` 以 NULL 绑定，因此 `= :p1` 和 `<> :p1` 不匹配任何行。
   这时 lambdaQL 在执行时以 `WARN` 级别记录 `LQL-4045`。要查找 NULL，请写 `x == null`（变为 `is null`）。
   对捕获值调用方法的表达式（`text.trim()`、转换为其他基本类型的 `min.toInt()` 等）会先被计算，因此值为 `null` 时会像 Kotlin 一样抛出异常（`LQL-4012`）。
   捕获值的 `!!` 和拆箱为其自身基本类型（`min!!`）不会被执行。值为 `null` 时绑定 NULL，并以 `DEBUG` 级别记录 `LQL-4045`。

Kotlin 编译器会在字节码中添加**隐藏的** null 检查（例如 `status` 为 `Int?` 时的 `it.status == 1`）。lambdaQL 会**忽略**它们（决策 D22）。只有源代码中的 null 检查才算数。
因此，含义相同的 Kotlin Lambda 和 Java Lambda 会生成相同的 SQL。

<a id="361-rule-table"></a>
### 3.6.1 规则表

| # | Kotlin 写法 | HQL | NULL 行为 |
|---|---|---|---|
| R12 | 可空列与常量或非空值：`it.status == 1`、`it.id == id`（`id: Long`） | `e0.status = 1`、`e0.id = :p1` | SQL 语义。列为 NULL：该行不返回 |
| R12 | `it.status != 1` | `e0.status <> 1` | SQL 语义。列为 NULL：该行**不**返回 |
| R1 | 大小比较：`it.price!! > p`、`it.status!! <= 3` | `e0.price > :p1`、`e0.status <= 3` | SQL 语义 |
| R11 | 可空列与可空的捕获值：`it.status == q`（`q: Int?`）、`it.description == text`（`String?`）、enum `==` | `e0.status = :p1` | 值比较。列为 NULL：该行不返回。捕获的 `null`：没有匹配的行（`LQL-4045`） |
| R11 | `it.status != q` | `e0.status <> :p1` | SQL 语义（D48）。列为 NULL：该行**不**返回（Kotlin 会返回它）。捕获的 `null`：没有匹配的行（`LQL-4045`） |
| R11 | 两个实体值：`it.description == it.custName` | `e0.description = e0.custName` | SQL 语义（D48）。任一侧为 NULL：该行不返回（NULL 与 NULL 也一样） |
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
//   status = 3    -> HQL: e0.status = :p1    status 为 3 的行
//   status = null -> HQL: e0.status = :p1    没有匹配的行：= NULL 永远不为真（WARN 级别 LQL-4045）
```

要查找 status 为 NULL 的行，请写出 null 检查。`it.status == null` 变为 `e0.status is null`。

列永远不会被函数包裹，因此数据库可以使用索引（见 [10.2](10-rules-and-limits.md#102-sargable-rule-index-friendly-sql)）。

```kotlin
.where { it.description == text }    // 查询执行时 text 不为 null
// HQL: e0.description = :p1
.where { it.description != text }
// HQL: e0.description <> :p1          （description 为 NULL 时不匹配：SQL 语义）
```

**永远不会是 NULL 的列：`it.id == id`。** 在示例实体中，`id` 是 `Long?`。Kotlin 把 `it.id == id`（捕获的 `id: Long`）编译为一个 null 检查加一个比较。

直接翻译这段字节码会得到 `not (e0.id is null or e0.id <> :p1)`。结果是正确的，但数据库难以很好地使用索引。
id 永远不会是 NULL，因此对它的 `is null` 总是假。lambdaQL 去掉这个检查，写成 `e0.id = :p1`。

同样的规则也适用于基本类型属性和 `nullable = false` 的列，但只在路径**没有关联步骤**时适用。订单可以没有客户，所以 `it.customer?.id` 可能是 NULL，这条路径的 null 检查会保留。

```kotlin
.where { it.id == id }              // id: Long (captured)
// HQL: e0.id = :p1
.where { it.id == null }
// HQL: 1=0
.where { it.customer?.id == null }
// HQL: e0.customer.id is null
```

相同的 HQL 用 Java Lambda 在 [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java) 中测试（region `never-null`，见[本页的 Java 版本](../java/03-where.md#362--on-objects-compares-values)）。Java 测试使用的条件正是 Kotlin 的 `it.id == id` 编译后的形式。

> [!WARNING]
> **Kotlin 的非空类型不能作为证明。** 属性 `var code: String` 中仍然可能有来自数据库的 NULL。
> 因为 Hibernate 会不加检查地把列值写入字段。
> lambdaQL 看的是 JPA 元数据，而不是 Kotlin 类型。

<a id="363-write-your-null-checks-yourself"></a>
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
> 在 lambdaQL 中，`it.status != 1` **不会**返回 NULL 行，因为没有写 null 检查。
> 像 `1` 这样的常量不可空。用 Java 写的相同条件会生成相同的 SQL。

比较常量和可空变量：

```kotlin
val q: Int? = 3
.where { it.status != 3 }    // e0.status <> 3       不返回 NULL 行
.where { it.status != q }    // e0.status <> :p1     同样不返回 NULL 行（SQL 语义，D48）
```

两种写法都不返回 NULL 行。在 Kotlin 中，`null != 3` 为 `true`，因此 Kotlin 会返回这些行。

可能出现这种差异时，lambdaQL 以 `DEBUG` 级别记录 `LQL-5009`（每个条件一次）。
要保留 NULL 行，请像 `it.status == null || it.status != q` 这样写出 null 检查。

对 `String?` 使用 `it.description.equals(text)` 与 `==` 相同。

**实体值的 SQL 语义**（决策 D48）。这一点目前还没有 Kotlin 测试。
相同的规则已用 Java 在 H2、MariaDB 和 Oracle 上测试（[`SqlMeaningTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlMeaningTest.java)，region `sql-meaning`，见[本页的 Java 版本](../java/03-where.md#363-write-your-null-checks-yourself)）。
以下是设计示例。

```kotlin
// two entity values: = and <> as in SQL; a row with a NULL on either side matches neither
qb.from(Order::class.java).where { it.description == it.custName }.toList()     // e0.description = e0.custName
// an entity value and a value: a NULL column does not match <>
qb.from(Order::class.java).where { it.description != text }.toList()           // e0.description <> :p1
// to keep the NULL rows, say so in the lambda
qb.from(Order::class.java).where { it.description == null || it.description != text }.toList()
// a NULL Boolean is not false
qb.from(Coded::class.java).where { it.vip == false }.toList()                   // e0.vip = false
```

关于 `?.`、`?:` 和 `!!` 的更多内容：[9. Kotlin 说明](09-kotlin-notes.md)。

<a id="364-captured-null-values"></a>
### 3.6.4 捕获的 null 值

| 写法 | 捕获的值为 `null` 时 |
|---|---|
| 与捕获的值进行 `==`、`!=` 比较：`it.status == q`、`it.description != text` | 执行：绑定 NULL 的 `= :p1` / `<> :p1`，因此**没有**匹配的行。`WARN` 级别 `LQL-4045` |
| 大小比较：`it.price!! > min!!`，以及所有 `<`、`<=`、`>`、`>=` | 执行：`e0.price > :p1` 绑定 NULL，**没有**匹配的行。`DEBUG` 级别 `LQL-4045`。`min!!`（D23）和拆箱为其自身基本类型不会被执行 |
| 对捕获值调用方法：`text.trim()`、转换为其他基本类型的 `min.toInt()` | 查询执行时**抛出异常**（`LQL-4012`）。因为它们会先被计算 |
| 与 `in` 一起使用的集合中有 null 元素 | 查询执行时**抛出异常** |

原因：Lambda 中的比较遵循 SQL 语义（决策 D48）。在 SQL 中，与 NULL 的 `=`、`<>` 永远不为真。
捕获的值是否为 null 只有在查询执行时才知道，因此 lambdaQL 在执行时记录警告。

对捕获值调用方法的表达式是在查询之前计算的值。`text.trim()`、转换为其他基本类型的 `min.toInt()` 属于这种情况，值为 `null` 时会像 Kotlin 一样抛出异常（`LQL-4012`）。
捕获值的 `!!`（D23）和拆箱为其自身基本类型不会被执行，lambdaQL 绑定装箱值。值为 `null` 时没有匹配的行，并以 `DEBUG` 级别记录 `LQL-4045`。
要查找 NULL 行，请写 `x == null`。

**用 null 守卫省略过滤。** 对同一个捕获值做了 null 检查时，如果该值为 null，这个条件会消失，不做任何过滤。

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

- 只使用捕获值和常量的部分，会在查询执行时在应用程序中决定。与 Kotlin 一样，从左到右并以短路方式求值。
- `min == null` 时，守卫为 true，所以整个条件为 true。不做任何过滤。与 `min` 的比较不会写入 HQL（Kotlin 也不会执行这个比较）。
- `min = 100.0` 时，守卫为 false，所以只剩下 `e0.price > :p1`。
- Lambda 仍然只分析一次。只有 HQL 文本取决于值。
- 这种情况下守卫的位置不影响结果。`min == null` 时，`it.price!! > min!! || min == null` 同样不做过滤，因为 `min!!` 和拆箱不会被执行（Kotlin 会抛出 `NullPointerException`）。
- 对捕获值调用方法的比较（`it.description == text.trim()`）不同。守卫写在后面时，`text.trim()` 会先被计算，因此 `text == null` 时抛出异常（`LQL-4012`）。

这一点目前还没有 Kotlin 测试。用 Java 写的同一个守卫已经测试过。见[本页的 Java 版本](../java/03-where.md#364-captured-null-values)。

<a id="365-converted-attributes-convert"></a>
### 3.6.5 转换的属性（`@Convert`）

带有 JPA `AttributeConverter` 的属性按其 Kotlin 值比较。Hibernate（7.4，以前的 6.6 也是）会转换与这种属性比较的字面量，也会转换绑定参数（已在 H2、MariaDB 和 Oracle 上验证）。

测试用 Java 编写（[`ConvertTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ConvertTest.java)，region `convert`）。以下是设计示例。

```kotlin
// code is stored reversed ("abc" as "cba"); vip is stored as 'Y'/'N' (YesNoConverter)
qb.from(Coded::class.java).where { it.code == "abc" }.toList()   // e0.code = 'abc': Hibernate converts the literal
```

<a id="37-boolean-columns"></a>
## 3.7 布尔列

在示例实体中，`active` 是 Kotlin `Boolean`（JVM 基本类型，永远不为 NULL）。
带有 `flag` 的行针对的是可空列 `var flag: Boolean? = null`。

| Kotlin | HQL | NULL 行 |
|---|---|---|
| `it.active` | `e0.active = true` | –（永远不为 NULL） |
| `!it.active` | `e0.active = false` | – |
| `it.flag == true` | `e0.flag = true` | 不返回 |
| `it.flag != true` | `e0.flag <> true` | **不返回**（SQL 语义，D48；Kotlin 会返回） |
| `it.flag == false` | `e0.flag = false` | 不返回（NULL 不是 false） |
| `it.flag ?: false` | `(e0.flag is not null and e0.flag = true)` | 不返回 |
| `!(it.flag ?: false)` | `(e0.flag is null or not (e0.flag = true))` | **返回** |
| `it.flag!!` | `e0.flag = true` | 不返回（`!!` 被忽略） |

说明：

- lambdaQL 不使用 `is not true` 或 `coalesce(flag, false)`。它们在 Oracle 上会失败（ORA-00932）。
- 对较大条件的取反总是写作 `not (...)`。lambdaQL 不会把条件包裹成 `(...) = false` 或 `(...) is null`，因为 Oracle 23 在 `WHERE` 中对它们的求值是错误的。
- `it.flag ?: false` **在条件中**没有问题。作为值或排序键时，它会被拒绝（限制 KC21）。

**比较两个布尔值**（`==`/`!=`）是支持的（决策 D25）。

```kotlin
.where { it.active == (it.status!! > s) }
// HQL: (e0.active = true and e0.status > :p1) or (e0.active = false and e0.status <= :p1)
```

lambdaQL 用 `AND`/`OR`（而不是 `CASE`）来写，这样数据库可以使用索引。
如果两侧共用同一个条件（例如 `it.active == it.active`），该写法会被拒绝。

<a id="38-strings"></a>
## 3.8 字符串

- 字符串上的 `==` 和 `equals` 使用**数据库的排序规则（collation）**。
  - MariaDB 的 `_ci` 排序规则会忽略大小写和尾随空格。
  - Oracle 把 `''`（空字符串）存储为 NULL。因此在 Oracle 上，`it.description == ""` 与 Kotlin 的行为不同。
- 作用于列的 Kotlin 字符串函数（`startsWith`、`endsWith`、`contains`、`lowercase()`、`length`、`isNullOrEmpty()`、`ignoreCase = true`）已有 Kotlin 设计（规则 R21）。见 [9. Kotlin 说明](09-kotlin-notes.md#97-strings)。
- 作用于列的 `isBlank()`、`isNullOrBlank()`、`trim()` 以及其他标准库函数会被拒绝（限制 KC9、KC20）。
- **只使用捕获值**的字符串模板在设计上可以使用：`"$a-$b" == it.description` 生成 `:p1 = e0.description`，字符串在应用程序中构建。但目前分析核心会拒绝 Lambda 内部的所有字符串拼接，因此暂时请在 Lambda 之前构建字符串。
- 使用**实体值**的字符串模板或 `+`（`"${it.description}-x"`）暂不支持（KC17，计划中）。

<a id="39-in-with-a-captured-collection"></a>
## 3.9 使用捕获集合的 IN

在 Lambda **外部**构建集合，并使用 `in`。

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

`!in` 变为 `not in`。可能为 NULL 的列也只生成简单的 `not in`（SQL 语义，决策 D48）。
值为 NULL 的行不匹配 `not in`，lambdaQL 以 `DEBUG` 级别记录 `LQL-5009`。Kotlin 的 `!in` 对 NULL 值为 true，因此要保留这些行，请像 `it.description == null || it.description !in descs` 这样写出 null 检查。

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
lambdaQL 用 JPA Metamodel 检查路径的每一步。

| 路径步骤 | HQL | 示例 |
|---|---|---|
| 对一关联（`@ManyToOne`、`@OneToOne`） | 带别名的 `join`（默认是内连接，见下文） | `it.customer?.name` -> `join e0.customer j1` ... `j1.name` |
| 嵌入（`@Embedded`） | 同一张表，不需要 join。值对象整体的相等比较按列展开（见下文） | 如果 `Order` 有一个嵌入的 `address`：`it.address.city` -> `e0.address.city` |
| 以目标 `@Id` 结尾的路径 | 外键列，不需要 join | `it.customer?.id` -> `e0.customer.id` |
| 关联本身与 `null` 比较 | 外键列，不需要 join | `it.customer == null` -> `e0.customer is null` |
| 集合（`@OneToMany`、`@ManyToMany`） | **拒绝** | `it.items...` -> 请使用 `exists()` 子查询 |

**值对象整体的比较。** 对嵌入的值对象（`@Embedded`）整体做相等比较时，lambdaQL 把它展开为每列一个比较（B6）。
`it.address == addr` 变为 `e0.address.city = :p1 and e0.address.zip = :p2`。

- 取反（`it.address != addr`）为 `e0.address.city <> :p1 or e0.address.zip <> :p2`。
- `it.address == null` 为 `e0.address.city is null and e0.address.zip is null`，`!= null` 为 `e0.address.city is not null or e0.address.zip is not null`。
- 每一列都遵循 SQL 语义（D48）。为 NULL 的列既不匹配 `=`，也不匹配 `<>`。例如 (Seoul, NULL) 行既不匹配相等比较，也不匹配取反（在三种数据库上实测）。
- 捕获的值对象的每个属性都在查询执行时读取并绑定。捕获的对象为 `null` 时，所有列都绑定 NULL，因此没有匹配的行，并且每列都以 `WARN` 级别记录 `LQL-4045`。
- 嵌套的值对象也会展开。
- 大小比较（`<` 等）以及与其他类型的比较仍然是 `LQL-3012` 错误。请比较字段。

**(已测试)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)，region `embedded-compare`：

```kotlin
// address is @Embedded (city, zip): == with a value object compares each column (D48)
val addr = Address("Seoul", "04524")
val same = qb.from(Order::class.java).where { it.address == addr }
assertEquals("select e0 from Order e0 where e0.address.city = :p1 and e0.address.zip = :p2", same.toHql().hql())
val none = qb.from(Order::class.java).where { it.address == null }
assertEquals("select e0 from Order e0 where e0.address.city is null and e0.address.zip is null", none.toHql().hql())
```

`it.customer?.name`、`it.customer!!.name` 以及（对于 Java 实体）`it.customer.name` 都表示**同一个列** `j1.name`。
不过在 `where` 中，连接类型取决于以何种方式经过关联步骤（决策 D54）。

| 在 `where` 中经过关联步骤的方式 | 连接 | 没有客户的订单 |
|---|---|---|
| `!!`，或不加标记（非空类型 `customer: Customer`、Java 实体的平台类型） | 内连接 `join` | 不在结果中 |
| `?.` | 没有客户的订单可能使整个条件为真时为 `left join`，否则为内连接 `join` | 在 Kotlin 中条件为真时保留在结果中 |

`?.` 是在源码中写明“关联可能不存在”的语法。`!!` 被视为“关联一定存在”的声明。
无论哪种情况，值的比较都遵循 SQL 语义（决策 D48）。例如 `it.customer?.name != "Kim"` 不匹配没有客户的订单。

**内连接已经足够的情况。** 在 `where` 中，关联路径默认是内连接（决策 D36）。
即使是用 `?.` 经过的步骤，如果没有客户的订单无法使条件为真，也写成内连接。结果相同。

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
- lambdaQL 不会替你添加 null 检查。不需要在路径前面写 `it.customer != null &&`。

**(已测试)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)，region `safe-call-and`：

```kotlin
// a row without a customer can never match: lambdaQL keeps the inner join (the same rows)
val q = qb.from(Order::class.java).where { it.customer?.name == "Kim" && it.price > 100.0 }
assertEquals("select e0 from Order e0 join e0.customer j1 where j1.name = 'Kim' and e0.price > 100.0", q.toHql().hql())
```

**`?.` 与 `||`。** 如果没有客户的订单可能使条件为真，用 `?.` 经过的步骤就是 `left join`。
状态为 1 且没有客户的订单也保留在结果中。这与在 Kotlin 中执行同一条件的结果相同。

**(已测试)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)，region `safe-call-or`：

```kotlin
// ?. says the customer may be missing: an order without a customer stays when the rest of the OR is true
val q = qb.from(Order::class.java).where { it.status.toInt() == 1 || it.customer?.name == "Kim" }
assertEquals("select e0 from Order e0 left join e0.customer j1 where e0.status = 1 or j1.name = 'Kim'", q.toHql().hql())
```

**各数据库的 SQL（直接执行 Kotlin region 中 HQL 的结果）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `safe-call-or`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where o1_0.status=1 or c1_0.name='Kim'
```

**`?.` 与 `== null`。** `it.customer?.name == null` 与 Kotlin 一样，既查找没有客户的订单，也查找客户没有名字的订单。

**(已测试)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)，region `safe-call-null`：

```kotlin
// "no customer, or a customer without a name": a left join, as in Kotlin
val q = qb.from(Order::class.java).where { it.customer?.name == null }
assertEquals("select e0 from Order e0 left join e0.customer j1 where j1.name is null", q.toHql().hql())
```

**各数据库的 SQL（直接执行 Kotlin region 中 HQL 的结果）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `safe-call-null`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name is null
```

**null 守卫。** 用 `?.` 写的 null 守卫也会成为 `left join`，没有客户的订单会被保留。
写成 `!!` 则是内连接，不会保留。此时 lambdaQL 以 `DEBUG` 级别记录 `LQL-5009`。

```kotlin
.where { it.customer == null || it.customer?.name == name }
// HQL: select e0 from Order e0 left join e0.customer j1 where e0.customer is null or j1.name = :p1

.where { it.customer == null || it.customer!!.name == name }
// HQL: select e0 from Order e0 join e0.customer j1 where e0.customer is null or j1.name = :p1

.where { it.customer != null && it.customer?.name == name }
// HQL: select e0 from Order e0 join e0.customer j1 where e0.customer is not null and j1.name = :p1
```

下面的 SQL 是用 Java 写出同一条件的测试结果。在 Kotlin 中，用 `!!` 写的第二种形式和用 `&&` 写的第三种形式会生成这个 SQL。

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `null-guard`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
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

Hibernate 把 `e0.customer is null` 写成 `c1_0.id is null`。在内连接之后，这个条件永远不为真。

**`!!`：关联一定存在的声明。** 用 `!!` 经过的步骤总是内连接。
在 Kotlin 中，`||` 在到达 `!!` 之前就结束的行（状态为 1、没有客户）会保留，但 lambdaQL 会排除这样的行。要保留这样的行，请使用 `?.`。

**(已测试)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)，region `not-null-assert`：

```kotlin
// !! says the customer is always there: an inner join, so orders without a customer are left out
// (Kotlin itself would keep the status 1 order without a customer, because || stops before the !!)
val q = qb.from(Order::class.java).where { it.status.toInt() == 1 || it.customer!!.name == "Kim" }
assertEquals("select e0 from Order e0 join e0.customer j1 where e0.status = 1 or j1.name = 'Kim'", q.toHql().hql())
```

**混用 `?.` 和 `!!` 的情况。** 在整个条件（同一查询的所有 `where`）中，如果同一关联步骤在一处用 `?.` 经过，在另一处用 `!!` 或不加标记经过，则为内连接。
lambdaQL 以 `WARN` 级别记录 `LQL-5014`（每个条件一次）。要保留没有客户的订单，请全部写成 `?.`。

**(已测试)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)，region `mixed-warn`：

```kotlin
// the same association with ?. in one place and !! in another: an inner join, and WARN LQL-5014
val q = qb.from(Order::class.java).where { it.customer?.name == "Kim" || it.customer!!.grade!! > 3 }
assertEquals("select e0 from Order e0 join e0.customer j1 where j1.name = 'Kim' or j1.grade > 3", q.toHql().hql())
```

- 在一条路径中，如果后面的步骤是 `!!`，前面的步骤也是内连接。例如 `Item` 的 `it.order?.customer!!.name` 是 `join e0.order j1 join j1.customer j2`。这条路径在 Kotlin 中没有订单时也会抛出 `NullPointerException`，因此不记录 `LQL-5014`。
- 两个步骤都用 `?.` 经过时（`it.order?.customer?.name`），需要时两个步骤都是 `left join`。

要查找**没有**客户的订单，请比较关联本身。这会检查外键列，不需要 join。

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

补充说明：

- 同一条路径只得到一个别名。`where`、`orderBy` 和 `select` 共享这个别名。
- `it.customer?.id == 5L` 使用外键列 `e0.customer.id`。不需要 join。
- **只**在 `orderBy` 或 `select` 中使用的路径是 `left join`，因此那里不会丢失行（规则 S8、J1）。有 `!!` 时也一样。如果 `where` 也使用同一条路径，`orderBy` 和 `select` 会使用 `where` 的 join。
- 按 `?.` 区分连接类型的规则适用于查询、`count`、子查询和连接查询的 `where`。显式连接的 `on` 中的路径仍像现在一样是 `left join`。必需关联（`optional = false`）总是内连接。
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
| `val`（`Long?`、`String` 等也一样） | 绑定参数 |
| `var` | 绑定参数。**每次执行查询时**都会读取其值 |
| 外围类的属性（`private` 也一样） | 每次执行查询时读取 |
| `companion object` 的 `val`、顶层 `val`、`object` 的 `val` | 绑定参数 |
| `const val` | 作为字面量写入 HQL（D37） |
| 在 Lambda 内部修改捕获的变量（`n++`） | **拒绝**（KC12） |

规则：

- 不使用实体的部分会在**每次执行查询时**在应用程序中计算。结果成为绑定参数。
- Lambda 只分析一次，结果会被缓存。每次调用时都会读取新的捕获值。
- 只有实体部分会变成 SQL。

<a id="312-errors-that-depend-on-order-of-evaluation"></a>
## 3.12 取决于求值顺序的错误

SQL 不保证 `AND`/`OR` 从左到右求值。因此像下面这样的"守卫"在 SQL 中并不安全。

```kotlin
qb.from(Item::class.java)
    .where { it.qty != 0 && it.price!! / it.qty > 2 }   // 会被拒绝
```

守卫之下的 `/`、`%` 和类型转换会被拒绝。请把条件改写为不用除法的形式，例如 `it.qty > 0 && it.price!! > 2.0 * it.qty`（当 `qty` 为正数时返回相同的行）。

<a id="313-what-is-rejected"></a>
## 3.13 会被拒绝的内容

lambdaQL 会抛出带有 `[LQL-xxxx]` 错误码和源代码行号的异常。它不会猜测。
大多数结构问题会在分析 Lambda 时（首次使用时）被发现。有问题的捕获值（调用了方法的 `null`、NaN、含有 `null` 元素的集合）会在查询执行时被发现。
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
| 自己写的非 inline 扩展函数（KC11） | `it.isOpen()` | 改为 `inline fun`，或直接写出条件 |
| 实体上的其他方法 | `it.calcSomething()` | 只使用属性 |
| 实体集合路径 | `it.items.size > 0` | `exists()` 子查询 |
| 实体与实体比较（KC13） | `it.customer == c` | 比较 id（实体比较在计划中） |
| 值对象的大小比较、与其他类型的比较（`LQL-3012`） | `it.address > a`（`Address` 为 `Comparable` 时） | 比较字段。支持 `==`、`!=` |
| 守卫之下的 `/`、`%`、类型转换 | 见 [3.12](#312-errors-that-depend-on-order-of-evaluation) | 去掉守卫或修改条件 |
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
对于其他可空类型（例如 `LocalDateTime?`），Kotlin 无法编译 `in`，请添加 `!!`。

```kotlin
.where { it.updateAt!! in from..to }  // e0.updateAt between :p1 and :p2
```

<a id="315-when-kotlin-only"></a>
## 3.15 `when`（仅限 Kotlin）

`when` **作为条件**时受支持。每个分支都必须以 `true` 或 `false`（或一个条件）结尾。
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

enum 主体也可以使用（这里使用了一个额外的属性 `kind: OrderKind?`，`OrderKind` 位于包 `com.example` 中）。

```kotlin
.where { when (it.kind) { OrderKind.EXPRESS, OrderKind.GIFT -> true; else -> false } }
// e0.kind in (com.example.OrderKind.EXPRESS, com.example.OrderKind.GIFT)
```

可空的主体不会得到 null 守卫（D22）。主体为 NULL 的行不会被返回，
在 `NOT` 下也同样如此。如果执行 Kotlin 代码，会进入 `else` 分支，但 lambdaQL 使用 SQL 语义。这是有意设计的规则。

**选择值**的 `when`（或 `if`）不受支持（KC6）。

```kotlin
// 不支持
.where { (if (it.active) it.price else 0.0)!! > 10.0 }
```

请改用 `&&` 和 `||` 来写条件。

---

[Kotlin 路线](README.md) | 上一章：[2. 入门](02-getting-started.md) | 下一章：[4. 排序与分页](04-order-paging.md) | [本页的 Java 版本](../java/03-where.md)
