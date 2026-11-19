# 10. 规则与限制（Kotlin）

> [!IMPORTANT]
> 本页比韩文手册旧。最新规则请参见[韩文页面](../../ko/kotlin/10-rules-and-limits.md)。

[Kotlin 路线](README.md) | [手册首页](../README.md) | 上一章：[9. Kotlin 说明](09-kotlin-notes.md) | 下一章：[11. 错误与语言](../11-errors-and-languages.md) | [本页的 Java 版本](../java/10-rules-and-limits.md)

公共页面：[1. 概述](../01-overview.md) · [11. 错误与语言](../11-errors-and-languages.md)

## 目录

- [10.1 基本规则：Lambda 就是查询](#101-the-base-rule-a-lambda-is-a-query)
- [10.2 SARGable 规则（便于使用索引的 SQL）](#102-sargable-rule-index-friendly-sql)
- [10.3 NULL 规则](#103-null-rules)
- [10.4 运算符规则（R1 到 R24）](#104-operator-rules-r1-to-r24)
- [10.5 Kotlin 与 SQL 的差异](#105-kotlin-and-sql-differences)
- [10.6 数据库之间的差异](#106-database-differences)
- [10.7 不支持的写法及替代写法](#107-not-supported-and-what-to-write-instead)
- [10.8 缓存：每个 Lambda 只分析一次](#108-caching-a-lambda-is-analyzed-once)
- [10.9 测试环境](#109-tested-environment)

如何阅读示例：

- 示例下方的注释给出 lambdaQL 生成的 HQL（当设计中有明确说明时）。
- 写在 Lambda 中的常量（数字、字符串、布尔值、`null`、enum 常量）作为字面量写入 HQL。
  捕获的值和计算出的值始终是绑定参数（`:p1`、`:p2`、...）（D37）。
- 实体：`Order`（`id`、`price`、`status`、`active`、`description`、`customer`、`updateAt`、`items`），
  `Customer`（`id`、`name`、`grade`），`Item`（`order`、`price`、`qty`）。参见 [2. 入门](02-getting-started.md#example-entities)。
  有些行使用了一个额外的可空属性 `flag: Boolean?`。

---

<a id="101-the-base-rule-a-lambda-is-a-query"></a>
## 10.1 基本规则：Lambda 就是查询

lambdaQL **从不运行你的 Lambda**。它读取字节码并写出 HQL。
Lambda 是用 Kotlin 语法写成的查询。因此：

1. 像 `it.customer?.name` 这样的路径**指的是一个列**，而不是一串调用。
   lambdaQL 不会检查 customer 是否为 null（D20）。在 `where` 中，该路径默认是内连接，因此没有 customer 的订单不会被返回（D36）。
   如果条件在没有 customer 时也可能为 true（null 保护），该路径是 `left join`，因此结果与 Kotlin 相同。
   要查找这些订单，请比较关联本身：`it.customer == null`。`it.customer?.name == null` 不会返回它们。
2. NULL **只在你写出的地方**处理（`== null`、`!= null`、`?.`、`?:`、两个可空值之间的 `==`）（D22）。
   Kotlin 编译器添加的 null 检查会被忽略。其他 NULL 遵循 SQL 规则：条件为 UNKNOWN，该行不会被返回。
3. `!!` 被忽略（D23）。Kotlin 的内存规则（对象同一性、装箱）不适用。`==` 和 `===` 表示**值相等**（D12、R17）。
4. **当某种写法没有对应规则时，lambdaQL 不会猜测。**它会抛出一个明确的错误，
   带有代码 `[LQL-xxxx]` 和源代码行号。它永远不会生成"大概正确"的 SQL。

<a id="102-sargable-rule-index-friendly-sql"></a>
## 10.2 SARGable 规则（便于使用索引的 SQL）

"SARGable"的意思是：数据库可以为该条件使用索引。
这是 lambdaQL 的一条基本规则（设计文档第 3.9 节）。

| # | 规则 |
|---|---|
| 1 | **不要在列这一侧放函数、运算符或 `CASE`。**如果值需要变换，请在绑定之前在应用程序中变换参数（例如用 `Locale.ROOT` 转为小写） |
| 2 | 如果两种写法含义相同，选择只包含简单比较和 `AND` / `OR` / `NOT` 的那种。正确的三值逻辑优先；在此前提下，选择 SARGable 的写法 |
| 3 | 如果含义确实需要在列上使用函数（例如忽略大小写），lambdaQL 会在启动时读取数据库元数据，使用数据库已有的路径：列的排序规则、函数索引、语言索引。如果没有可用路径，它会在启动时对每个列记录一条 WARN（严格模式：报错） |
| 4 | 每条渲染规则都在 H2、MariaDB 和 Oracle 上用 `EXPLAIN` 检查过 |

### 示例

**忽略大小写**（`equals(s, ignoreCase = true)`、`startsWith(s, ignoreCase = true)`；D24）

| 数据库具备的条件 | lambdaQL 写出 | 而不是 |
|---|---|---|
| 不区分大小写的排序规则（MariaDB `_ci`） | `e0.description = :p1` | `lower(e0.description) = lower(:p1)` |
| Oracle 函数索引 `LOWER(description)` | `lower(e0.description) = :p1`（`:p1` 在应用程序中转为小写） | `lower(e0.description) = lower(:p1)` |
| Oracle 语言索引 `NLSSORT(... BINARY_CI)` | `e0.description collate binary_ci = :p1` | |
| 没有可用路径 | 启动时 WARN（严格模式：报错） | |

**用普通的 `=` 和 `<>` 进行相等比较**（决策 D48）

```kotlin
.where { it.description == text }   // 查询执行时 text 不为 null
// e0.description = :p1
.where { it.description != text }
// e0.description <> :p1               （description 为 NULL 时不匹配）
```

lambdaQL 永远不会写出 `is [not] distinct from`。在 Oracle 上它会变成 `decode(...)`，无法使用索引。
实体值遵循 SQL 语义，因此普通的 `=` 或 `<>` 总是正确的。捕获的 `null` 生成 `is null` / `is not null`。

**比较两个布尔表达式**（D25）

```kotlin
.where { it.active == (it.status!! > c) }
// (e0.active = true and e0.status > :p1) or (e0.active = false and e0.status <= :p1)
```

`CASE` 写法（`e0.active = case when ... end`）能得到正确的行，但需要全表扫描，
所以 lambdaQL 不使用它。

**Elvis 与空字符串**

```kotlin
.where { (it.description ?: "") == "x" }     // e0.description = :p1
.where { (it.description ?: "") == "" }      // (e0.description = :p1 or o.description is null)
.where { it.description.isNullOrEmpty() }    // (o.description is null or e0.description = :p1)
```

**带子查询的 NOT IN** 写成 `NOT EXISTS`（反连接，并且是 NULL 安全的；参见 [10.3](#103-null-rules)）。

### 测量结果

MariaDB 10.11 和 Oracle 23ai，20,000 到 50,000 行：

| 写法 | MariaDB | Oracle | 是否可以？ |
|---|---|---|---|
| 在 `_ci` 列上 `name = 'abc'` | 索引查找 | – | 可以 |
| `LOWER(name) = LOWER('abc')` | 全索引扫描（19,791 行） | 全表扫描（没有函数索引） | **不可以** |
| `lower(name) = :p_lowered` + 函数索引 | – | 索引范围扫描（`LIKE 'ab%'` 也是） | 可以 |
| `name COLLATE BINARY_CI = :p` + 语言索引 | – | 索引范围扫描 | 可以 |
| 布尔 `==` 写成 `AND`/`OR` | 范围扫描（350 行） | 2 次索引范围扫描 + 位图 OR | 可以 |
| 布尔 `==` 写成 `CASE` | 全索引扫描（49,648 行） | 全表扫描 | **不可以** |

### 遵循同一规则的计划项

| 写法 | 计划的 SQL |
|---|---|
| 类似"`updateAt` 的年份是 2024"的日期函数 | `e0.updateAt >= :from and e0.updateAt < :to` |
| 按函数键排序（`lower(name)`） | 先检查访问路径，没有则 WARN |

<a id="db-functions-on-a-column"></a>
### 列上的数据库函数

列一侧的数据库函数（`OrderFns.discount(it.price)!! > 10`）无法使用普通索引。
lambdaQL 会记录 **WARN**（`LQL-5001`）。例外：在 Oracle 上，存在相同表达式的函数索引时不记录。
在 MariaDB 上，`NOT DETERMINISTIC`（默认）的函数即使参数只有参数值（`it.price!! > OrderFns.discount(rate)!!`），也会导致全扫描，因此这种情况也会警告（`LQL-5007`）。
严格模式（用错误代替警告）尚未实现。
参见 [8. 存储过程与数据库函数](08-procedures-functions.md)。

<a id="103-null-rules"></a>
## 10.3 NULL 规则

| Lambda | SQL | NULL 行为 |
|---|---|---|
| `it.status == 1`（`Int?` 与常量） | `e0.status = 1` | NULL：UNKNOWN，不返回该行。忽略编译器隐藏的 null 检查（D22） |
| `it.status != 1` | `e0.status <> 1` | NULL：**不**返回该行 |
| `it.price!! > p` | `e0.price > :p1` | NULL：不返回该行。`!!` 被忽略（D23） |
| `it.status == q`（两侧都可空：`q: Int?`、`String?`、enum） | `e0.status = :p1`；捕获的 `null`：`is null` | 列为 NULL：不返回。捕获的 `null` 遵循 Kotlin（`is null`） |
| `it.status != q`（两侧都可空） | `e0.status <> :p1`；捕获的 `null`：`is not null` | 列为 NULL：**不**返回（SQL 语义，D48；Kotlin 会返回它） |
| `it.description == it.custName`（两个实体值） | `e0.description = e0.custName` | 任一侧为 NULL：不返回，NULL 与 NULL 也一样（SQL 语义，D48） |
| `it.description.equals(text)`（`String?`） | 与 `==` 相同 | 与 `==` 相同 |
| `x == null`、`x != null` | `is null`、`is not null` | 二值 |
| `it.id == id`（`it.id` 是 `Long?`，`id` 是捕获的 `Long`）、`it.id == null`：永远不会是 NULL 的列（id、基本类型、`nullable = false`，且路径上没有关联步骤） | `e0.id = :p1`，而不是 `not (e0.id is null or e0.id <> :p1)`；`it.id == null` 得到 `1=0`（用 Java 测试） | 该列没有 NULL。经过关联的路径（`it.customer?.id`）保留检查 |
| `!`、`&&`、`\|\|` | `NOT`、`AND`、`OR` | SQL 三值逻辑 |
| `it.flag == true` / `it.flag != true` | `e0.flag = true` / `e0.flag <> true` | NULL：不返回（SQL 语义，D48） |
| `it.flag ?: false`（条件） | `(e0.flag is not null and e0.flag = true)` | NULL 视为 false |
| `where` 中的 `it.customer?.name`、`it.customer!!.name` | `j1.name` + `join e0.customer j1`（默认是内连接） | 没有 customer：该订单不会被返回，`it.customer?.name == null` 也是如此 |
| `it.customer == null \|\| it.customer?.name == p` | `e0.customer is null or j1.name = :p1` + `left join e0.customer j1` | 没有 customer：会被返回，与 Kotlin 相同 |
| 只在 `orderBy` 或 `select` 中使用的同一路径 | `j1.name` + `left join e0.customer j1` | 没有 customer：该列为 NULL |
| `it.customer == null`、`it.customer != null` | `e0.customer is null`、`e0.customer is not null`（外键列，不需要 join） | 没有 customer 的订单 / 有 customer 的订单 |
| `it.customer?.id` | `e0.customer.id`（外键列，不需要 join） | 没有 customer：NULL |
| `a ?: b`（值） | 值中为 `coalesce(a, b)`；字符串条件中为 SARGable 写法 | 参见 [9.5](09-kotlin-notes.md#95-null-operators---and-) |
| `it.id in ids` | `e0.id in (:p1)` | NULL id：不返回 |
| `it.id !in ids` | `e0.id not in (:p1)`（id 不可能为 NULL）。可能为 NULL 的列：`(x is null or x not in (:p1))` | NULL 值（可为 NULL 的列）：会被返回，与 Kotlin 相同 |
| `it.status in 1..5`、`!in 1..5`、`when (it.status) { ... }` | `between`、`not (... between ...)`、`in (...)` | NULL 主体：不返回，在 `NOT` 之下也一样（没有保护，D22） |
| 带 `x !in sub...` 的子查询 | `not exists (...)` | 即使子查询返回 NULL 也正确 |
| `Agg.sum`、`avg`、`min`、`max` | `sum(...)` ... | 没有行或所有值都为 NULL 时为 NULL |
| `Agg.count()` | `count(*)` | 没有行时为 0 |

### 捕获的 null 值

| 捕获值所在的位置 | 允许 `null` 吗？ |
|---|---|
| 两个可空值之间的 `==` / `!=` | 允许。生成 `is null` / `is not null` |
| 在同一个值的 null 保护之下：`p == null \|\| it.status!! > p` | 允许。这个保护会在查询执行时在应用程序中决定：`p == null` 时不过滤，其他值时为 `e0.status > :p1` |
| 带 `!!` 或在大小比较（`<` `<=` `>` `>=`）中：`it.price!! > p!!` | **不允许。**绑定该值时报错（Kotlin 会抛出 NPE） |
| `in` 中带有 `null` 元素的集合 | **不允许。**绑定时报错 |
| `NaN`、`Infinity` | **不允许。**总是报错 |

<a id="104-operator-rules-r1-to-r24"></a>
## 10.4 运算符规则（R1 到 R24）

这是设计文档中规则表（第 3.6 节和 KOTLIN.md 第 5 节）的简化版本，使用 Kotlin 写法。

| # | Lambda 写法 | 含义 |
|---|---|---|
| R1 | 非空基本类型的比较，或 `!!` 之后的比较（`it.qty > 5`、`it.price!! > p`） | `=`、`<>`、`<`、... SQL 的 NULL 规则 |
| R2 | 编译为引用比较的 `==` / `!=`（enum `==`；`===` 参见 R17） | 值相等，不是对象同一性。按 SQL 语义的 `=` / `<>`（D48） |
| R3 | `x == null`、`x != null` | `IS NULL`、`IS NOT NULL` |
| R6 | `!`、`&&`、`\|\|` | `NOT`、`AND`、`OR`。SQL 三值逻辑 |
| R7 | 字符串值比较 | 遵循**数据库的排序规则**（大小写、末尾空格）。Oracle 把 `''` 视为 NULL |
| R8 | 实体 `==` 实体 | 计划：标识符相等。v0.1 不支持（KC13） |
| R9 | 捕获值 | 以运行时的值作为绑定参数 |
| R10 | 属性访问（`it.price`） | getter 调用，映射到 JPA 属性 |
| R11 | 编译为 `Intrinsics.areEqual` 的 Kotlin `==`、`!=`（引用，或两侧都可空） | 值相等：按 SQL 语义的 `=` / `<>`（D48）；捕获的 `null` 生成 `is null` / `is not null` |
| R12 | 可空基本类型与基本类型（`it.id == 1L`，`id: Long?`） | 忽略隐藏的 null 检查：`e0.id = 1`、`e0.id <> 1` |
| R13 | `!!` 及其他编译器 null 检查 | 忽略（D23） |
| R14 | `?.` | 带 NULL 传播的路径或值；与不带 `?.` 时是同一个列 |
| R15 | `?:` | 值中为 `coalesce(a, b)`。条件中的布尔 elvis 变为 `(x is not null and x = true)`。作为值的布尔 elvis 会被拒绝（KC21） |
| R16 | 关联 join | 已被 R24 取代 |
| R17 | `===`、`!==` | 值相等，与 `==` 相同 |
| R18 | 区间 `in a..b`、`until`、`..<`、`downTo`、`!in` | `between`、`>= and <`、`not (...)`。不会自动添加 null 保护 |
| R19 | 集合 `x in c`、`x !in c`（捕获的 `c`） | `x in (:p)`、`(x is null or x not in (:p))`。null 元素：报错。为空：FALSE / TRUE |
| R20 | 带常量分支并以 `true`/`false` 结尾的 `when (subject)` | `in (...)` 或比较的 `OR` |
| R21 | Kotlin 字符串函数（`startsWith`、`endsWith`、`contains`、`equals(ignoreCase)`、`lowercase`、`length`、`isNullOrEmpty`） | `like ... escape '\'`、D24 的忽略大小写路径、`lower`、`length`、`(x is null or x = :p)` |
| R22 | Kotlin 宿主值（捕获的 `var`、private 属性、`companion` 中的值） | 每次执行时读取。写入它们会被拒绝 |
| R23 | 属性引用 `Order::price` | 与 getter 相同。在 `orderBy`（`orderBy(Order::price)`）中**尚未实现**：请使用 `orderBy { it.price }` |
| R24 | 关联路径（`it.customer?.name`、`it.customer!!.name`、Java 实体的 `it.customer.name`） | 一个列。对一关联：where、order by、select 共用的别名。在 `where` 中默认是内连接，当 null 保护保留没有关联的行时是 `left join`（D36）；只在 order by 或 select 中使用时是 `left join`。嵌入类型：同一张表。以目标 `@Id` 结尾，或关联本身与 `null` 比较：外键列，不需要 join。集合：报错 |

Java 专用规则 R4（对实体值使用 `equals`）和 R5（Java 的 null 安全辅助方法）在 [Java 路线](../java/10-rules-and-limits.md) 中。在 Kotlin 中，`==` 具有与 R5 相同的含义。

其他规则：

- `x.compareTo(y) > 0`、`isBefore`、`isAfter` 变为 `x > y`、...
- 写在 Lambda 中的常量（包括 `const val`）是字面量：`it.price!! > 100.0` 是 `e0.price > 100.0`，`"O'Brien"` 是 `'O''Brien'`。捕获的值和计算出的值（`BigDecimal("1.5")`）是绑定参数（D37）。
- 编译器有时会移动 `!` 的位置（德摩根定律、双重否定）。这永远不会改变结果。
  lambdaQL 让每个条件恰好只出现一次，并且从不"简化"你的逻辑。
- 你写的在 Kotlin 中恒为真的条件（`x > 5 || !(x > 5)`）不会被简化。
  在 SQL 中它会丢掉 `x` 为 NULL 的行。这是 SQL 的规则。

<a id="105-kotlin-and-sql-differences"></a>
## 10.5 Kotlin 与 SQL 的差异

这些差异来自 SQL 本身，不是编译器的问题。
"G" 编号是设计文档（第 2.7 节）中的 ID。

| # | 差异 | lambdaQL 的做法 |
|---|---|---|
| G1 | 在 Kotlin 中 `null != 3` 为 true；SQL 的 `status <> 3` 为 UNKNOWN | `!=` 渲染为 `<>`：SQL 语义（D48）。NULL 行被排除，并以 `DEBUG` 级别记录 `LQL-5009`。要保留它们，请写 `it.status == null \|\| ...` |
| G2 | `it.flag != true` 与 Kotlin 的差异 | 渲染为 `e0.flag <> true`：NULL 的 Boolean 不匹配（SQL 语义） |
| G3 | 参数为 NULL 时 `name = ?` 返回 0 行 | 捕获的 `null` 生成 `name is null`（捕获的值适用 Kotlin 规则） |
| G4 | `status NOT IN (1, NULL)` 总是返回 0 行 | 捕获集合中的 `null` 会报错。子查询的 `!in` 写成 `NOT EXISTS` |
| G5 | Kotlin 会抛出 NPE（`!!`、拆箱），SQL 只是比较 | 已写入文档：实体中的 NULL 遵循 SQL 规则 |
| G6 | `NaN`：H2 把 NaN 排为最大值且 `NaN = NaN` 为 true；MariaDB 无法存储它；Oracle 只能在 `BINARY_DOUBLE` 中存储 | 捕获的 NaN 或 Infinity 会报错。存储的 NaN 遵循数据库 |
| G7 | `Float` 列与 `Double` 值比较：结果因数据库而异 | 对于 `Float`/`Double`，**不保证与 Kotlin 的结果逐位一致**。lambdaQL 不添加 `CAST`。请自行选择列映射（例如 Oracle 的 `binary_float` / `binary_double`） |
| G9 | `x > 5 \|\| !(x > 5)` 会丢掉 NULL 行 | 不简化，按原样渲染 |
| G10 | SQL 不保证求值顺序：`qty != 0 && total / qty > 2` 仍可能除以零 | 在 v0.1 中，保护条件之下的 `/`、`%`、`CAST` 会被拒绝（D13） |
| G11 | 字符串：MariaDB `_ci` 忽略大小写和末尾空格；Oracle 把 `''` 存储为 NULL；Oracle 的 `NLS_SORT` 跟随 JVM 的区域设置 | 已写入文档：字符串比较和排序遵循数据库。在 Oracle 上请设置 `NLS_SORT=BINARY`（会话初始化或 `-Duser.language=en`）。在 Oracle 上 `it.description == ""` 和 `isEmpty()` 的结果不同 |
| G12 | Kotlin 的 `===` 是同一性（两个值为 1000 的 `Long` 装箱对象为 false） | lambdaQL 把 `===` 和 `==` 都定义为值相等 |
| G13 | 遗留的布尔列（MariaDB `TINYINT(1)`、Oracle `NUMBER(1)`）中存有 0/1 以外的值时，读取结果不同 | Hibernate 自己生成的 DDL（`bit(1)`、Oracle 23 的 `boolean`）没有问题。使用遗留 schema 时请注意 |
| G14 | Oracle 23 的 bug：对复合条件使用 `WHERE (a AND b) = false` / `IS FALSE` / `IS NULL` 会丢失行 | lambdaQL 从不把复合条件包在布尔测试中。否定总是写成 `NOT(...)` |
| G15 | Oracle 的 `CHAR` 列与 `String` 绑定值不匹配 | 已写入文档：请注意实体映射 |

更多差异：

| 差异 | Kotlin | SQL | lambdaQL |
|---|---|---|---|
| `BigDecimal` 相等 | `==` 比较小数位数（`1.0` 与 `1.00` 为 false） | `=` 比较值 | 值相等 |
| 整数除法 | `5 / 2` 是 `2` | MariaDB 给出 `2.5` | 取决于数据库。最终规则尚未确定；它可能在 v0.1 中被拒绝 |
| `BigDecimal` 除法和函数结果 | Kotlin 的小数位数规则（`HALF_EVEN`） | 由数据库决定小数位数（例如函数结果在 H2 中为 1，在 MariaDB 和 Oracle 中为 0） | 已写入文档 |
| Unicode 忽略大小写（`ſ`、`İ`） | Kotlin 的规则 | 数据库的排序规则 | 遵循数据库 |
| 字符串长度 | UTF-16 单元 | H2 为 UTF-16 单元，MariaDB 为码点 | 已写入文档 |
| 含 NULL 的字符串模板 | `"null"` 文本 | NULL（Oracle：`NULL \|\| 'x'` 为 `'x'`） | 字符串拼接在计划中，v0.1 不支持 |
| 可空的 `when` / 区间主体 | 进入 `else`；`!in` 为 true | 不返回，在 `NOT` 之下也一样 | SQL 的含义（D22） |
| 非空类型 `String` | 永远不为 null | 列可能为 NULL | lambdaQL 使用 JPA 元数据 |
| Lambda 中的 `LocalDateTime.now()` | 应用程序时间 | – | 作为**应用程序时间**绑定。不支持数据库时间（`current_timestamp`） |
| `Set` 中相等的元素 | 由 `equals` / `hashCode` 决定（`data class`：主构造函数中的属性） | 用主键区分行 | 如果 Set 比行数小，`toSet()` 和 `findSet()` 抛出 `IllegalStateException`（实体的 `equals` / `hashCode` 把不同的行视为相等，或 `@Id` 不唯一，例如视图）。请使用 `toList()`（D42，用 Java 测试） |

<a id="106-database-differences"></a>
## 10.6 数据库之间的差异

lambdaQL 隐藏了许多差异。有些你必须了解。

### 排序与分页

| 项目 | H2 | MariaDB | Oracle | lambdaQL |
|---|---|---|---|---|
| NULL 的位置，`ASC` | 在前 | 在前 | **在后** | 在所有数据库上 `ASC` 都表示 NULLS FIRST（D16） |
| NULL 的位置，`DESC` | 在后 | 在后 | **在前** | 在所有数据库上 `DESC` 都表示 NULLS LAST（D16） |
| `NULLS FIRST/LAST` 语法 | 支持 | **不支持**（语法错误） | 支持 | Hibernate 在 MariaDB 上用 `CASE` 模拟：排序**无法使用索引** |
| 分页 SQL | `offset ? rows fetch first ? rows only` | `limit ?,?` | `offset ? rows fetch first ? rows only` | 交给 Hibernate（`setFirstResult` / `setMaxResults`） |
| 相等行的顺序 | – | – | 随机 | 分页时，如果排序键不包含 id，lambdaQL 会添加根实体的 `@Id asc` |
| 字符串顺序 | 区分大小写 | `_ci` | `NLS_SORT`（JVM 区域设置） | 遵循数据库 |
| `order by` 中的隐式路径 | inner join（丢失行） | 相同 | 相同 | lambdaQL 使用 `left join`（或同一路径的 `where` join） |

对于每个可能为 NULL 的排序键，lambdaQL 都会把 `nulls first` / `nulls last` 写入 HQL。当它就是数据库的默认值时，Hibernate 会去掉它。
默认值（`ASC` = NULLS FIRST，`DESC` = NULLS LAST）与 H2 和 MariaDB 相同，所以在它们上面
不需要额外的 SQL；只有 Oracle 会加上 `NULLS` 子句。要改变 NULL 的位置，请使用
`by.asc(key, false)`（NULL 在后）或 `by.desc(key, true)`（NULL 在前）（D35）。在 MariaDB 上它们使用 `CASE` 模拟。

### 条件与值

| 项目 | H2 | MariaDB | Oracle |
|---|---|---|---|
| `is not distinct from` | 原生支持 | `<=>`（可以使用索引） | `decode(...)`（无法使用索引）。lambdaQL 不会写出它（D48） |
| `is [not] true`、`coalesce(boolean, false)` | 可用 | 可用 | **ORA-00932**。lambdaQL 从不写出它们 |
| 空字符串 `''` | 一个值 | 一个值 | **NULL** |
| 大小写和末尾空格 | 精确 | `_ci`：忽略 | 精确（排序使用 `NLS_SORT`） |
| `NaN` | 可存储，为最大值 | 无法存储 | 只能用 `BINARY_DOUBLE` |
| `Float` 列 | REAL 字面量规则 | 扩展为 double | Hibernate 把 `Float` 映射为 `float(24)`（十进制） |
| 整数 `/` | 整数 | **小数**（`5/2 = 2.5`） | – |
| 很大的 `in` 列表（1,500 个值） | 可用 | 可用 | 在 23ai 上可用（未测量旧版 Oracle 的 1,000 个限制） |

### 子查询

| 项目 | 现象 | lambdaQL |
|---|---|---|
| 不带 `limit` 的子查询 `order by` | Oracle：语法错误（ORA-00907）。MariaDB：被忽略 | 移除，并给出 WARN |
| MariaDB `IN (... limit n)` | 不支持（错误 1235） | 自动包装：`in (select x.id from (... limit n) x)` |
| MariaDB `lateral` | 结果错误（模拟实现） | 用 `row_number() over (...)` 改写（尚未实现） |

### 更新、插入、删除

| 项目 | 现象 | lambdaQL |
|---|---|---|
| `SET a = ..., b = a + 1`（在设置某列之后又读取它） | MariaDB 读取**新**值，H2 和 Oracle 读取旧值 | 分析时报错（U3） |
| DML 中的关联路径（`it.customer?.name`） | Hibernate 在各数据库上生成不同的 join，并且会**丢失**没有 customer 的行 | lambdaQL 写出关联标量子查询（在 `where` 和 SET 中）。在 `where` 中，查询会做内连接的路径还会加上 `customer.id is not null`，所以 DML 与查询命中相同的行（U9，已测试） |
| 带有同一张表子查询的 `DELETE` | MariaDB 错误 1093 | 包装在派生表中（U17；尚未实现） |
| 同一个 enum 值只绑定一次，同时用于 STRING 列和 ORDINAL 列 | H2 和 MariaDB 会**不报错地存入错误的值** | 每个位置使用一个参数，从不共用（U7） |
| `values(list) { }` 中的大量行 | Oracle：绑定变量硬上限为 65,535（ORA-01745），远在达到之前就已非常慢 | 拆分：每条语句最多 500 个值，全部在一个事务中执行（U13） |
| 带 `order by ... limit` 的 `update` / `delete` | MariaDB 拒绝 `in` 子查询中的 `limit`（错误 1235）和读取目标表的子查询（错误 1093） | `e0.id in (select t.id from (select x0.id as id ... order by ... limit n) t)`：派生表 `t` 在三个数据库上都可用。内层 select 使用查询的连接（D36），因此行与查询相同（用 Java 测试） |
| `on conflict` / upsert | H2 和 Oracle：`merge`。MariaDB：`insert ... on duplicate key update`，更新的行计为 2。Hibernate 的 `do nothing` 在 H2 和 Oracle 上会失败 | `upsert` 和 `onConflict(...).update(...)` 使用 Hibernate 的形式。`ignore()` 写成 `insert ... select ... where not exists`。行数原样返回（D40，已测试） |

### 存储过程与函数

| 项目 | H2 | MariaDB | Oracle |
|---|---|---|---|
| OUT / INOUT 参数 | **不支持** | 支持 | 支持 |
| 多个结果集 | 不支持 | 支持 | 支持（REF CURSOR） |
| 存储过程的受影响行数 | – | – | 总是 -1，所以 lambdaQL 不提供它；请使用 OUT 参数 |
| Boolean 参数和结果 | – | 支持 | 23ai 及以上版本 |
| 在虚拟列 / 函数索引中使用存储函数 | – | 不可以（错误 1901） | 可以（函数索引） |

由于 H2 没有 OUT 参数，存储过程功能在 MariaDB 和 Oracle 上检查。

<a id="107-not-supported-and-what-to-write-instead"></a>
## 10.7 不支持的写法及替代写法

对于此列表中的每种写法，lambdaQL 都会抛出明确的错误。"计划中"表示以后的版本。

### Lambda 写法与加载

| 不支持 | 替代写法 |
|---|---|
| 对象表达式（`object : ...`），不是 `Serializable` 的函数式接口（KC5） | Lambda 字面量 |
| 存为 Kotlin 函数类型的 Lambda（`val f: (Order) -> Boolean`）（KC4） | 传入 Lambda 字面量 |
| `-Xsam-conversions=class`（KC1），在 `inline fun` 主体中创建的 Lambda（KC2） | 参见 [9.14](09-kotlin-notes.md#914-kotlin-limits-kc1-to-kc21) |
| 对你自己的 Kotlin `fun interface` 的可调用引用（KC3） | Lambda 字面量 |
| 条件、排序或查询列 Lambda 中的局部 `val`/`var`、循环、多条语句 | 一个表达式。在 Lambda 外部计算值 |
| 写入捕获的变量（`n++`）（KC12） | 在 Lambda 外部计算 |
| `@JvmField`、`internal`、`value class`、`@get:JvmName` 属性（KC15、KC16） | 公开的普通属性 |
| 针对比内置 ASM 所知更新的 JDK 编译的 Lambda | 更新 ASM（lambdaQL 不对它做 shade） |

### 条件

| 不支持 | 替代写法 |
|---|---|
| 值的 `if` / `when` / `takeIf`，一般的 `if (c) x else y`（KC6） | `&&` / `\|\|`。在 Lambda 外部选择值。`CASE WHEN` 在计划中 |
| （支持：`if (c) x else false`、`if (c) true else x`、`when { a -> true; ...; else -> false }`） | 它们表示 `AND`、`OR` |
| 布尔中缀 `and`、`or`、`xor` | `&&`、`\|\|` |
| 保护条件之下的 `/`、`%`、`CAST`（`it.qty != 0 && it.price!! / it.qty > 2`） | 不用除法比较：`it.qty > 0 && it.price!! > 2.0 * it.qty` |
| 实体 `==` 实体（`it.customer == c`）（KC13） | `it.customer?.id == c.id`（计划：标识符相等） |
| 集合路径（`it.items...`、`@OneToMany`、`@ManyToMany`） | `exists()` 子查询，或 join |
| `item in it.items` | `exists()` 子查询 |
| 在 Lambda 内部构建的集合（`listOf(1, 2)`、`setOf(...)`）（KC8） | 在外部构建并捕获它。（`groupBy` 键是例外） |
| 区间中的 `step` | 两个比较，或捕获的集合 |
| 捕获集合中的 `null` 元素 | 去掉 null；如果你想要 NULL 行，加上 `\|\| it.x == null` |
| 捕获的 `NaN` / `Infinity` | 在查询之前检查该值 |
| `isNaN()`（KC14） | – |
| 对实体值的其他函数调用（你自己的非 inline 函数、`operator` 函数）（KC11） | 用属性写出条件，或把你的函数改为 `inline` |
| 带实体值的字符串模板或 `+`（KC17） | 计划中 |
| 实体路径上的 `it.tags["k"]`、`it.lines[0]` | – |

### 函数与字符串

| 不支持（v0.1） | 替代写法 |
|---|---|
| `isBlank`、`isNullOrBlank`（KC9） | `isNullOrEmpty()` 或直接比较 |
| 列上的 `trim`、`coerceAtLeast`、`toLocalDate` 及其他标准库函数（KC20） | 直接比较，或数据库函数（`@SqlFunction`）。函数映射在计划中 |
| 日期函数（`YEAR(col) == 2024`） | 区间：`it.updateAt!! >= from && it.updateAt!! < to` |
| 返回泛型类型（`fun <X> nvl(a: X, b: X): X`）、实体、集合或 `Unit` 的 `@SqlFunction` | 以下之一：`Double`、`Integer`（`Int`）、`Long`、`BigDecimal`、`String`、`LocalDate`、`LocalDateTime`、`Boolean` |
| 与 Hibernate 内置函数（`COALESCE`、`ROUND`、`SYSDATE`、...）同名的 `@SqlFunction` | 使用前缀或 schema 名（`PUBLIC.SQ_FN`） |
| 带默认参数、`inline`、`vararg` 的 `@SqlFunction` | 参数固定的普通函数 |
| 用扩展函数、函数式接口常量、函数类型属性作为数据库函数 | 接口 `companion object` + `@JvmStatic` |
| 用户自定义的聚合函数或窗口函数 | 计划中 |
| 在查询 Lambda 之外或在 `where` 中调用 `Agg.*` | 在 `select` / `having` 中使用 |

### 排序与分页

| 不支持 | 替代写法 |
|---|---|
| 排序 Lambda 中的条件或循环（`if (x) by.asc(...)`） | 构建两个查询，或把 `Direction` 作为捕获值传入：`by.sort(o.price, dir)` |
| 两次 `orderBy`、`orderBy` 之后的 `where`、`limit` 之后的 `orderBy` | 一个包含所有键的 `orderBy`（这些写法无法编译） |
| 以实体作为排序键（`by.desc(o.customer)`） | 一个列：`by.desc(o.customer?.name)`（无法编译） |
| join 排序 Lambda 中的属性引用 | Lambda |
| `orderBy` 中的属性引用（`orderBy(Order::price)`，尚未实现） | Lambda：`orderBy { it.price }` |
| 把排序名称和方向放在一起的一段文本（`"price,desc_nulls_last"`），尚未实现 | 方向用 `Direction.parse(text)`，名称用 `SortSpec.ofPath`。名称由排序策略检查（[4.10](04-order-paging.md#sort-policy)） |
| 集合 fetch join 与 `limit` / `offset` 一起使用 | 先分页，再加载集合 |
| Spring `Sort` 中的 `ignoreCase()`（[12.5](12-spring-data-jpa.md#125-spring-sort-and-pageable)），尚不支持 | 不带 `ignoreCase()` 的排序 |
| 函数键（`lower(name)`） | 计划中 |
| 按聚合别名排序（`by.desc(s.total)`） | 计划中 |
| 以布尔 elvis 作为排序键（KC21） | 用键 `o.flag` 并指定 NULL 位置：`by.asc(o.flag, false)`（NULL 在后） |

### 子查询

| 不支持 | 替代写法 |
|---|---|
| 在标量比较中使用 `first()`，但结果不是一行 | 使用聚合（`max`、`min`、`avg`、`sum`、`count`）或 `orderBy { }.limit(1)...first()` |
| 子查询中不带 `limit` 的 `orderBy` | 它没有效果，所以会被移除（WARN） |

### 查询列与分组

| 不支持 | 替代写法 |
|---|---|
| `select` / `having` 中不是 `groupBy` 键的非聚合值 | 把它加入 `groupBy` |
| 用普通类作为派生表或 CTE 的行类型 | Kotlin data class（或 Java record） |
| 用 `arrayOf(...)` / `setOf(...)` 作为 `groupBy` 键 | `listOf(...)`、`Pair`、`Triple` |
| 以布尔 elvis 作为查询值（KC21） | 查询 `it.flag`，在 Kotlin 中处理 NULL |

### 更新、插入、删除

| 不支持（v0.1） | 替代写法 |
|---|---|
| 不带 `where` 的 `update` / `delete` | 加上 `.all()`（没有它，`execute()` 无法编译） |
| 在同一条语句中设置某列之后又读取它 | 有意使用旧值，或执行两次更新 |
| 两次设置同一列 | 一次赋值 |
| 条件值（`if (c) a else b`、`when`、作为值的切换 `!it.active`） | 计划中（`CASE WHEN`）。使用两次带不同 `where` 的更新 |
| 在 `set` Lambda 中用 `if` 选择要设置的列 | 多次调用 `set()` |
| `set` 中的 `++`、`--`、`apply`、`with`、`also` | `it.cnt += 1`、直接赋值 |
| `by[o.cnt] += 1` | `by[o.cnt] = o.cnt + 1` 或 `by.set(o.cnt, o.cnt + 1)` |
| 赋值 Lambda 中的局部 `val` | 在每个位置写出表达式 |
| 设置关联的非 `@Id` 属性（`it.customer?.name = x`） | 直接更新 `Customer` |
| 向非可选列（`@Id`、`nullable = false`、基本类型）写入 `null` | – |
| 自己写入 `@Version` 属性 | lambdaQL 会为你使用 `update versioned` |
| 值中的字符串模板 | 计划中 |
| 带显式 join 的 `insert ... select` | 计划中。在 v0.1 中请使用多对一路径 |
| 并发插入时原子的 `onConflict { ... }.ignore()` | 捕获键重复错误（`ignore()` 使用 `not exists`，见 [7.6](07-6-upsert.md)） |
| 基于 `insert ... select` 的 upsert | 用 `values(list) { ... }` 逐行 upsert |
| 对更新或删除写 `orderBy { ... }` 而没有 `limit(n)` | 加上 `limit(n)`，或去掉 `orderBy`（否则 `IllegalStateException`） |
| 必须只改变一行的更新或删除，用 `execute() == 1` 和回滚来检查 | `executeOne()`：先读取 id（最多 2 个）。有 2 个时什么都不改变，并抛出 `NonUniqueResultException`。有 1 个时用 `id = :p and (条件)` 改变该行。绝不会改变超过一行（D41，用 Java 测试） |
| `executeOne()` 与 `orderBy` 或 `limit` 一起使用 | 只用其中之一（否则 `IllegalStateException`） |
| update 或 delete 上的 `offset` | 不计划提供（D42）。DML 只有 `limit(n)`。用 `where { }`、`orderBy { }` 和 `limit(n)` 选择行 |
| 对复合键实体使用 `limit` 或 `executeOne()` | 目前还不支持。先查询 id，再 `delete ... where { it.id in ids }` |
| 使用 Spring 共享 `EntityManager` 且没有事务的 DML（lambdaQL 中没有 `@Transactional`，D41） | 在 `@Transactional` 或 `TransactionTemplate` 中调用 |
| 对仓库方法 `deleteAll { ... }`（批量删除）期望 cascade 或 `@PreRemove` | 使用 Spring Data 的 `deleteAll(Iterable)` 或 `delete(entity)`（见 [12.4](12-spring-data-jpa.md#deleteallwhere-is-not-spring-datas-deleteall)） |
| `returning` | 在 DML 之后再查询一次 |
| 数据库时间（`current_timestamp`） | 传入应用程序时间（`LocalDateTime.now()` 作为值绑定） |
| 使用 `TABLE` id 生成器的 `insert ... select` | 使用 `values { }`，或 IDENTITY / SEQUENCE |
| 向 JOINED 或 TABLE_PER_CLASS 子类 `insert` | 不支持（Hibernate 6.6.5 会失败） |

DML 还需注意：批量 DML 不经过持久化上下文。Hibernate 会先刷新，但
同一会话中的托管实体之后仍保留旧值。请调用 `clear()`（或 `refresh`）。
JPA 的级联和 `orphanRemoval` 不适用。
DML 语句需要事务：`QueryBuilder(EntityManagerFactory)` 会打开自己的事务；使用 `EntityManager`（包括 Spring 的共享 `EntityManager`）的构建器需要调用方的事务，没有事务时会在发送任何 SQL 之前失败。

### 存储过程

| 不支持 | 替代写法 |
|---|---|
| Lambda 中除一次桩函数调用之外的任何内容（日志、嵌套调用、分支） | 一次调用。事先计算好参数 |
| 带计算的命名参数（`calc(rate = r * 2, id = id)`） | 位置参数，或事先计算好值 |
| 对带有 OUT 参数的存储过程使用 `exec` | `call` |
| 参数的数据库默认值 | 传入每一个参数 |
| 桩函数中的 Kotlin 默认参数 | 传入每一个参数；去掉默认值 |
| 选择并调用 Oracle 包存储过程的某个重载（已能检测有重载的名称：`LQL-4044`）、动态存储过程名 | 计划中 |
| Oracle 隐式结果（`DBMS_SQL.RETURN_RESULT`）、MariaDB 包、存储过程结果的分页 | 不支持 |

<a id="108-caching-a-lambda-is-analyzed-once"></a>
## 10.8 缓存：每个 Lambda 只分析一次

读取字节码是较慢的部分，所以 lambdaQL **对每个 Lambda 只做一次**。

| 项目 | 规则 |
|---|---|
| 缓存什么 | 分析结果：HQL 模板和参数来源列表。失败结果也会被缓存 |
| 缓存键 | Lambda 的实现类和方法（名字、签名、种类）、它的类型、实体类，以及 **ClassLoader** |
| 大小 | 有上限（LRU），所以不会无限增长，也不会泄漏类加载器 |
| 不缓存 | 字节码本身（重新读取的成本很低，而保存它很占空间） |
| 捕获值 | **从不缓存。**每次执行查询时都会读取并绑定 |
| 对捕获对象的调用（`filter.minPrice`、`this.minStatus`、`LocalDateTime.of(...)`） | **每次**执行查询时在应用程序中运行，然后作为一个参数绑定 |
| 捕获的 `var` | 每次执行查询时读取 |
| 只含捕获值的条件（`p != null && ...`） | 保留为 `:p1 is not null`，不会折叠为常量，所以缓存保持正确 |
| 作为捕获值的 `Direction` | 一个绑定值，每次读取（结构仍然被缓存） |
| DML | 每个 Lambda 类分析一次；`set()` 调用之间的检查使用小型摘要 |

Kotlin Lambda 的名字（`...$lambda$0`）可能会随编译器版本或你编辑源代码而改变。
这没有问题：缓存键中还包含类、签名和类加载器，而且 lambdaQL 不依赖名字的格式。

用原型测量（JDK 21）：新 Lambda 的首次分析大约需要 **0.2 ms**。
之后，带两次 `set()` 调用的更新在缓存查找、值提取和检查上大约只需 **0.1 到 0.2 µs**，
即使执行 200 万次之后也是如此。目标：同一个 Lambda 调用 1,000 次只读取一次
字节码。Hibernate 也按 HQL 字符串缓存它的查询计划。

这对你意味着什么：

```kotlin
// 好：一个 Lambda，值会变化
.where { it.status == status }

// v0.1 中不可行：查询的结构在 Lambda 内部变化
.where { if (flag) it.status == 1 else it.active }

// 替代做法：在外部选择 Lambda（两个 Lambda，各自只分析一次）
val list = if (flag) {
    qb.from(Order::class.java).where { it.status == 1 }.toList()
} else {
    qb.from(Order::class.java).where { it.active }.toList()
}
```

<a id="109-tested-environment"></a>
## 10.9 测试环境

| 项目 | 版本 |
|---|---|
| Kotlin | 2.x（设计已用 kotlinc 1.7.22、2.1.10、2.2.21、2.3.0、2.3.21 检查过） |
| Java（JVM） | 21 |
| Hibernate | 6.6（设计已用 6.6.5 检查过）。使用了 HQL 扩展（例如 `function(...)` 和 `nulls first`）；不保证其他 JPA 提供者可用 |
| 数据库 | H2 2.3、MariaDB 10.11、Oracle 23.26 (23ai) |
| Spring | Spring Boot 3.4.2 和 Spring Data JPA 3.4.2（Spring 模块，[12](12-spring-data-jpa.md)；仅 Java repository）。Spring Boot 4 尚未验证 |
| Kotlin 逐行检查 | 目前只在 H2 上进行。计划在 MariaDB 和 Oracle 上运行 Kotlin 用例 |
| 未测试 | 高于 2.3 的 Kotlin、Kotlin Multiplatform、PostgreSQL、SQL Server、Oracle 19c 及更早版本（例如 PL/SQL 的 `BOOLEAN` 参数） |

---

[Kotlin 路线](README.md) | 上一章：[9. Kotlin 说明](09-kotlin-notes.md) | 下一章：[11. 错误与语言](../11-errors-and-languages.md) | [本页的 Java 版本](../java/10-rules-and-limits.md)
