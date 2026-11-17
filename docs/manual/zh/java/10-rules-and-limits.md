# 10. 规则与限制（Java）

> [!NOTE]
> **状态：部分已实现。**
> 本章汇总了 lambdaQL 设计中的规则：Lambda 如何变成 SQL、NULL 如何处理、
> Java 与 SQL 的差异、各数据库之间的差异，以及哪些内容不受支持。
> 这些规则已在 H2 2.3、MariaDB 10.11 和 Oracle 23ai（Hibernate 6.6.5）上用原型检查过。
> 目前分析核心、`where`、结果函数（`toList()`、`toSet()`、`first()`、`one()`、`count()`、`exists()`）、排序与分页的核心部分、`SortSpec`、update/insert/delete/upsert（[7](07-dml.md)）以及 Spring Data JPA 模块（[12](12-spring-data-jpa.md)）可以工作（已在 H2、MariaDB 和 Oracle 上测试）。
> 子查询（[5](05-subquery.md)）和带聚合的 `select`（[6](06-select-group.md)）的规则同样已实现，在 DML 中也是如此。显式 join、派生表、CTE（[6](06-select-group.md)）以及存储过程与数据库函数（[8](08-procedures-functions.md)）的规则同样已实现。列上的数据库函数的警告（[10.2](#db-functions-on-a-column)）同样已实现。一部分排序规则也尚未实现（见 [4.12](04-order-paging.md#412-rules-summary-s1-to-s14)）。
> 完整状态请参见 [Java 路线 README](README.md#status)。

[Java 路线](README.md) | [手册首页](../README.md) | 上一章：[9. Java 说明](09-java-notes.md) | 下一章：[11. 错误与语言](../11-errors-and-languages.md) | [本页的 Kotlin 版本](../kotlin/10-rules-and-limits.md)

公共页面：[1. 概述](../01-overview.md) · [11. 错误与语言](../11-errors-and-languages.md)

---

## 目录

- [10.1 基本规则：Lambda 就是查询](#101-the-base-rule-a-lambda-is-a-query)
- [10.2 SARGable 规则（对索引友好的 SQL）](#102-sargable-rule-index-friendly-sql)
- [10.3 NULL 规则](#103-null-rules)
- [10.4 运算符规则（R1 到 R24）](#104-operator-rules-r1-to-r24)
- [10.5 Java 与 SQL 的差异](#105-java-and-sql-differences)
- [10.6 数据库差异](#106-database-differences)
- [10.7 不支持的内容及替代写法](#107-not-supported-and-what-to-write-instead)
- [10.8 缓存：一个 Lambda 只分析一次](#108-caching-a-lambda-is-analyzed-once)
- [10.9 测试环境](#109-tested-environment)

如何阅读示例：

- 示例下方的注释给出 lambdaQL 生成的 HQL（在设计中已明确的情况下）。
- 写在 Lambda 中的常量（数字、字符串、布尔值、`null`、enum 常量）会作为字面量写进 HQL。
  捕获的值和计算出的值始终是绑定参数（`:p1`、`:p2` ……）（D37）。
- 实体：`Order`（`id`、`price`、`status`、`active`、`description`、`customer`、`updateAt`、`items`），
  `Customer`（`id`、`name`、`grade`），`Item`（`order`、`price`、`qty`）。

---

<a id="101-the-base-rule-a-lambda-is-a-query"></a>
## 10.1 基本规则：Lambda 就是查询

lambdaQL **从不执行你的 Lambda**。它读取字节码并编写 HQL。
Lambda 是用 Java 语法编写的查询。因此：

1. 像 `o.getCustomer().getName()` 这样的路径**表示一个列**，而不是一连串的调用。
   lambdaQL 不检查 customer 是否为 null（D20）。在 `where` 中，该路径默认是内连接，因此没有 customer 的订单不会被选中（D36）。
   如果条件在没有 customer 时也可能为 true（null 守卫），该路径是 `left join`，因此结果与 Java 相同。
   要查找这些订单，请比较关联本身：`o.getCustomer() == null`。
2. NULL **只在你写出的地方**处理（`== null`、`!= null`）（D22）。
   其他 NULL 遵循 SQL 规则：条件为 UNKNOWN，该行不会被选中。
3. Java 的内存规则（对象同一性、装箱缓存、字符串驻留、NPE）不适用。
   对象之间的 `==` 表示**值相等**（D12）。
4. **当某种写法没有对应规则时，lambdaQL 不会猜测。** 它会抛出一个清晰的错误，
   带有错误码 `[LQL-xxxx]` 和源代码行。它从不生成"大概正确"的 SQL。

<a id="102-sargable-rule-index-friendly-sql"></a>
## 10.2 SARGable 规则（对索引友好的 SQL）

"SARGable"的意思是：数据库可以为该条件使用索引。
这是 lambdaQL 的一条基本规则（设计文档第 3.9 节）。

| # | 规则 |
|---|---|
| 1 | **不要在列一侧放函数、运算符或 `CASE`。** 如果值必须变换，请在绑定之前在 Java 中变换参数（例如用 `Locale.ROOT` 转为小写） |
| 2 | 如果两种写法含义相同，选择只包含简单比较和 `AND` / `OR` / `NOT` 的那种。正确的三值逻辑优先；在此前提下，选择 SARGable 的写法 |
| 3 | 如果语义确实需要在列上使用函数（例如忽略大小写），lambdaQL 会在启动时读取数据库元数据，并使用数据库已有的途径：列的排序规则、函数索引、语言索引。如果没有可用途径，它会在启动时按列记录一条 WARN（严格模式下为错误） |
| 4 | 每条生成规则都在 H2、MariaDB 和 Oracle 上用 `EXPLAIN` 检查过 |

### 示例

**忽略大小写**（D24）。设计中将此规则用于 Kotlin 的 `ignoreCase` 函数。列上的 Java 字符串函数已在计划中，也将遵循同一规则。

| 数据库具备的条件 | lambdaQL 生成 | 而不是 |
|---|---|---|
| 不区分大小写的排序规则（MariaDB `_ci`） | `e0.name = :p1` | `lower(e0.name) = lower(:p1)` |
| Oracle 函数索引 `LOWER(name)` | `lower(e0.name) = :p1`（`:p1` 在 Java 中转为小写） | `lower(e0.name) = lower(:p1)` |
| Oracle 语言索引 `NLSSORT(... BINARY_CI)` | `e0.name collate binary_ci = :p1` | |
| 没有可用途径 | 启动时 WARN（严格模式下为错误） | |

**一侧为 NOT NULL 时的 null 安全相等比较**

```java
.where(o -> o.getDescription() == text)   // 查询执行时 text 不为 null
// e0.description = :p1                       （而不是 "is not distinct from"）
.where(o -> o.getDescription() != text)
// (e0.description <> :p1 or e0.description is null)
```

在 Oracle 上，`is not distinct from` 会变成 `decode(...)`，无法使用索引。
因此，当已知一侧为 NOT NULL 时（`@Id`、`nullable = false`、基本类型属性，
或不为 null 的绑定值），lambdaQL 会写成普通的 `=`。

**比较两个布尔表达式**（D25）

```java
.where(o -> o.isActive() == (o.getStatus() > c))
// (e0.active = true and e0.status > :p1) or (e0.active = false and e0.status <= :p1)
```

`CASE` 写法（`e0.active = case when ... end`）能得到正确的行，但需要全表扫描，
因此 lambdaQL 不使用它。

**带子查询的 NOT IN** 会写成 `NOT EXISTS`（反连接，并且是 null 安全的；见 10.3）。

### 测量结果

MariaDB 10.11 和 Oracle 23ai，20,000 到 50,000 行：

| 写法 | MariaDB | Oracle | 可以吗？ |
|---|---|---|---|
| 在 `_ci` 列上 `name = 'abc'` | 索引查找 | – | 是 |
| `LOWER(name) = LOWER('abc')` | 全索引扫描（19,791 行） | 全表扫描（无函数索引） | **否** |
| `lower(name) = :p_lowered` + 函数索引 | – | 索引范围扫描（`LIKE 'ab%'` 也是） | 是 |
| `name COLLATE BINARY_CI = :p` + 语言索引 | – | 索引范围扫描 | 是 |
| 布尔 `==` 写成 `AND`/`OR` | range（350 行） | 2 次索引范围扫描 + bitmap OR | 是 |
| 布尔 `==` 写成 `CASE` | 全索引扫描（49,648 行） | 全表扫描 | **否** |

### 同一规则下的计划项

| 写法 | 计划的 SQL |
|---|---|
| 日期函数，如"`updateAt` 的年份是 2024" | `e0.updateAt >= :from and e0.updateAt < :to` |
| 按函数键排序（`lower(name)`） | 先检查访问路径，如果没有则 WARN |

<a id="db-functions-on-a-column"></a>
### 列上的数据库函数

列一侧的数据库函数（`OrderFns.discount(o.getPrice()) > 10`）无法使用普通索引。
lambdaQL 会记录 **WARN**（`LQL-5001`）。例外：在 Oracle 上，存在相同表达式的函数索引时不记录。
在 MariaDB 上，`NOT DETERMINISTIC`（默认）的函数即使参数只有参数值（`o.getPrice() > OrderFns.discount(rate)`），也会导致全扫描，因此这种情况也会警告（`LQL-5007`）。
严格模式（用错误代替警告）尚未实现。
参见 [8. 存储过程与数据库函数](08-procedures-functions.md)。

<a id="103-null-rules"></a>
## 10.3 NULL 规则

| Lambda | SQL | NULL 行为 |
|---|---|---|
| `o.getId() == 10L`（拆箱，一侧为基本类型） | `e0.id = 10` | NULL：UNKNOWN，不选中该行（Java 会抛出 NPE） |
| `o.getId() == p`（两个对象：`Long`、`String`、枚举等） | null 安全的 `is not distinct from`，一侧为 NOT NULL 时为 `=` | `null == null` 为 true |
| `o.getDescription().equals(p)` | `e0.description = :p1` | description 为 NULL：UNKNOWN（Java 会抛出 NPE） |
| `Objects.equals(o.getDescription(), p)`、`"x".equals(o.getDescription())`、`Boolean.TRUE.equals(o.getActive())` | null 安全 | 与 Java 相同 |
| `!Boolean.TRUE.equals(o.getActive())` | `e0.active is distinct from true` | 与 Java 相同。在三种数据库上都可用 |
| `x == null`、`x != null` | `is null`、`is not null` | 二值 |
| 对永远不会是 NULL 的列（id、基本类型、`nullable = false`，且路径上没有关联步骤）写 `o.getId() == null` | `1=0`。与比较并列时，检查被去掉：`o.getId() != null && o.getId() == id` 得到 `e0.id = :p1`（已测试） | 该列没有 NULL。经过关联的路径（`o.getCustomer().getId()`）保留检查 |
| `!`、`&&`、`\|\|` | `NOT`、`AND`、`OR` | SQL 三值逻辑 |
| `where` 中的 `o.getCustomer().getName()` | `j1.name` + `join e0.customer j1`（默认是内连接） | 没有 customer：该订单不会被选中 |
| `o.getCustomer() == null \|\| o.getCustomer().getName().equals(p)` | `e0.customer is null or j1.name = :p1` + `left join e0.customer j1` | 没有 customer：会被选中，与 Java 相同 |
| 只在 `orderBy` 或 `select` 中使用的 `o.getCustomer().getName()` | `j1.name` + `left join e0.customer j1` | 没有 customer：该列为 NULL |
| `o.getCustomer() == null`、`o.getCustomer() != null` | `e0.customer is null`、`e0.customer is not null`（外键列，不连接） | 没有 customer 的订单 / 有 customer 的订单 |
| `o.getCustomer().getId()` | `e0.customer.id`（外键列，不连接） | 没有 customer：NULL |
| `ids.contains(o.getId())` | `e0.id in (:p1)` | id 为 NULL：不选中 |
| `!ids.contains(o.getId())` | `e0.id not in (:p1)`（id 不可能为 NULL）。可能为 NULL 的列：`(x is null or x not in (:p1))` | NULL 值（可为 NULL 的列）：会被选中，与 Java 相同 |
| 带 `!...contains(x)` 的子查询 | `not exists (...)` | 即使子查询返回 NULL 也正确 |
| `Agg.sum`、`avg`、`min`、`max` | `sum(...)` …… | 没有行或所有值都为 NULL 时为 NULL |
| `Agg.count()` | `count(*)` | 没有行时为 0 |

### 捕获的 null 值

| 捕获值所在位置 | 允许 `null` 吗？ |
|---|---|
| null 安全写法：对象之间的 `==` / `!=`、`Objects.equals`、`"x".equals(col)` | 是 |
| 在同一值的 null 守卫之下：`p == null \|\| o.getStatus() > p` | 是。这个守卫会在查询执行时在 Java 中决定：`p == null` 时不过滤，其他值时为 `e0.status > :p1` |
| `p.equals(col)` 的接收者、`col.equals(p)` 的参数、拆箱比较、`<` `<=` `>` `>=` | **否。** 绑定值时报错（Java 会抛出 NPE 或结果始终为 false） |
| `in` 中包含 `null` 元素的集合 | **否。** 绑定时报错 |
| `NaN`、`Infinity` | **否。** 始终报错 |

<a id="104-operator-rules-r1-to-r24"></a>
## 10.4 运算符规则（R1 到 R24）

这是设计文档中规则表（第 3.6 节）的简要版本。

| # | Lambda 写法 | 含义 |
|---|---|---|
| R1 | 基本类型比较，或一侧为基本类型（`o.getId() == 10L`、`o.getStatus() > p`） | `=`、`<>`、`<` ……，SQL NULL 规则 |
| R2 | 对象的 `==` / `!=`（`Long`、`String`、`Boolean`、枚举） | null 安全的值相等。不是对象同一性 |
| R3 | `x == null`、`x != null` | `IS NULL`、`IS NOT NULL` |
| R4 | `a.equals(b)` | `a = b`，SQL NULL 规则 |
| R5 | `Objects.equals(a, b)`、`"x".equals(a)`、`Boolean.TRUE.equals(a)` | null 安全的值相等 |
| R6 | `!`、`&&`、`\|\|` | `NOT`、`AND`、`OR`。SQL 三值逻辑 |
| R7 | 字符串值比较 | 遵循**数据库排序规则**（大小写、尾部空格）。Oracle 把 `''` 视为 NULL |
| R8 | 实体 `==` 实体 | 计划中：标识符相等。v0.1 不支持 |
| R9 | 捕获值 | 以运行时的值作为绑定参数 |
| R10 | 属性访问 | getter 调用，映射到 JPA 属性 |
| R11 到 R23 | Kotlin 规则 | 参见[本页的 Kotlin 版本](../kotlin/10-rules-and-limits.md) |
| R24 | 关联路径（`o.getCustomer().getName()`） | 一个列。对一关联：where、order by、select 共用的别名。在 `where` 中默认是内连接，当 null 守卫保留没有关联的行时是 `left join`（D36）；只在 order by 或 select 中使用时是 `left join`。嵌入类型：同一张表。以目标 `@Id` 结尾，或关联本身与 `null` 比较：外键列，不连接。集合：错误 |

其他规则：

- `x.compareTo(y) > 0`、`isBefore`、`isAfter` 会变成 `x > y` 等。
- 写在 Lambda 中的常量（包括 `static final` 编译期常量）是字面量：`o.getPrice() > 100` 是 `e0.price > 100.0`，`"O'Brien"` 是 `'O''Brien'`。捕获的值和计算出的值（`new BigDecimal("1.5")`）是绑定参数（D37）。
- javac 有时会移动 `!` 的位置（德摩根定律、双重否定）。这不会改变结果。
  lambdaQL 保证每个条件恰好出现一次，也从不"简化"你的逻辑。
- 你写的在 Java 中恒为 true 的条件（`x > 5 || !(x > 5)`）不会被简化。
  在 SQL 中，它会排除 `x` 为 NULL 的行。这是 SQL 的规则。
- IntelliJ 会对 `o.getId() == p` 警告 "Number objects compared using `==`"。在 lambdaQL Lambda 中这是
  有意的写法（值相等）。如有需要，可以使用 `@SuppressWarnings("NumberEquality")`。

哪些 Java 类型适用 R1、哪些适用 R2：见 [9.3](09-java-notes.md#93-equality-of-boxed-values)。

<a id="105-java-and-sql-differences"></a>
## 10.5 Java 与 SQL 的差异

这些差异来自 SQL 本身，而不是编译器的问题。
"G" 编号是设计文档中的 ID（第 2.7 节）。

| # | 差异 | lambdaQL 的处理 |
|---|---|---|
| G1 | 当 `status` 为 NULL 时，`!Objects.equals(status, 1)` 在 Java 中为 true；而 `status <> 1` 在 SQL 中为 UNKNOWN | 生成 null 安全的写法（`is distinct from`），返回与 Java 相同的行 |
| G2 | `!Boolean.TRUE.equals(active)` 与 `NOT active` | 生成 `active is distinct from true`（在三种数据库上都可用） |
| G3 | 参数为 NULL 时，`name = ?` 返回 0 行 | null 安全写法使用 `is [not] distinct from` |
| G4 | `status NOT IN (1, NULL)` 始终返回 0 行 | 捕获的集合中出现 `null` 是错误。子查询的 `NOT IN` 写成 `NOT EXISTS` |
| G5 | Java 会抛出 NPE（拆箱、`o.getName().startsWith(...)`），SQL 只是返回行 | 已在文档中说明：实体的 NULL 遵循 SQL 规则 |
| G6 | `NaN`：H2 把 NaN 当作最大值排序，且 `NaN = NaN` 为 true；MariaDB 无法存储它；Oracle 只能在 `BINARY_DOUBLE` 中存储它 | 捕获的 NaN 或 Infinity 是错误。已存储的 NaN 遵循数据库 |
| G7 | `float` 列与 `double` 值比较：结果因数据库而异 | 对于 float/double，**不保证与 Java 逐位一致的结果**。lambdaQL 不会添加 `CAST`。请自行选择列映射（例如 Oracle 的 `binary_float` / `binary_double`） |
| G9 | `x > 5 \|\| !(x > 5)` 会排除 NULL 行 | 不简化，按原样生成 |
| G10 | SQL 不保证求值顺序：`qty != 0 && total / qty > 2` 仍可能除以零 | v0.1 拒绝守卫之下的 `/`、`%`、`CAST`（D13） |
| G11 | 字符串：MariaDB `_ci` 忽略大小写和尾部空格；Oracle 把 `''` 存储为 NULL；Oracle `NLS_SORT` 跟随 JVM 区域设置 | 已在文档中说明：字符串比较和排序遵循数据库。在 Oracle 上请设置 `NLS_SORT=BINARY`（会话初始化或 `-Duser.language=en`）。在 Oracle 上 `"".equals(x)` 与 `isEmpty()` 结果不同 |
| G12 | Java 中对象的 `==` 是同一性比较（对 128 及以上的 `Long`、`new String` 为 false） | lambdaQL 把 `==` 定义为值相等 |
| G13 | 旧式布尔列（MariaDB `TINYINT(1)`、Oracle `NUMBER(1)`）中存有 0/1 以外的值时，读取结果不同 | Hibernate 自己生成的 DDL（`bit(1)`、Oracle 23 的 `boolean`）没有问题。使用旧模式时请注意 |
| G14 | Oracle 23 的 bug：对复合条件使用 `WHERE (a AND b) = false` / `IS FALSE` / `IS NULL` 会丢失行 | lambdaQL 从不把复合条件包在布尔测试中。否定始终写成 `NOT(...)` |
| G15 | Oracle 的 `CHAR` 列无法匹配 `String` 绑定值 | 已在文档中说明：请注意实体映射 |

更多差异：

| 差异 | Java | SQL | lambdaQL |
|---|---|---|---|
| `BigDecimal` 的相等比较 | `equals` 比较标度（`1.0` 与 `1.00` 为 false） | `=` 比较值 | 值相等 |
| 整数除法 | `5 / 2` 为 `2` | MariaDB 得到 `2.5` | 取决于数据库。最终规则尚未确定；v0.1 可能会拒绝 |
| `BigDecimal` 除法和函数结果 | Java 的标度规则 | 由数据库决定标度（例如函数结果：H2 为 1，MariaDB 和 Oracle 为 0） | 已在文档中说明 |
| Unicode 忽略大小写（`ſ`、`İ`） | Java 规则 | 数据库排序规则 | 遵循数据库 |
| 字符串长度 | UTF-16 单元 | H2 为 UTF-16 单元，MariaDB 为码点 | 已在文档中说明 |
| 与 NULL 的字符串拼接 | 文本 `"null"` | NULL（Oracle：`NULL \|\| 'x'` 为 `'x'`） | 字符串拼接已在计划中，v0.1 不支持 |
| Lambda 中的 `LocalDateTime.now()` | 应用时间 | – | 作为**应用时间**绑定。不支持数据库时间（`current_timestamp`） |
| `Set` 中相等的元素 | 由 `equals` / `hashCode` 决定 | 用主键区分行 | 如果 Set 比行数小，`toSet()` 和 `findSet()` 抛出 `IllegalStateException`（实体的 `equals` / `hashCode` 把不同的行视为相等，或 `@Id` 不唯一，例如视图）。请使用 `toList()`（D42，已测试） |

<a id="106-database-differences"></a>
## 10.6 数据库差异

lambdaQL 隐藏了许多差异，但有一些你必须了解。

### 排序与分页

| 项目 | H2 | MariaDB | Oracle | lambdaQL |
|---|---|---|---|---|
| NULL 位置，`ASC` | 最前 | 最前 | **最后** | 在所有数据库上 `ASC` 都表示 NULLS FIRST（D16） |
| NULL 位置，`DESC` | 最后 | 最后 | **最前** | 在所有数据库上 `DESC` 都表示 NULLS LAST（D16） |
| `NULLS FIRST/LAST` 语法 | 支持 | **不支持**（语法错误） | 支持 | Hibernate 在 MariaDB 上用 `CASE` 模拟：排序**无法使用索引** |
| 分页 SQL | `offset ? rows fetch first ? rows only` | `limit ?,?` | `offset ? rows fetch first ? rows only` | 交给 Hibernate（`setFirstResult` / `setMaxResults`） |
| 相等行的顺序 | – | – | 随机 | 分页时，如果排序键不包含 id，lambdaQL 会添加根实体的 `@Id asc` |
| 字符串顺序 | 区分大小写 | `_ci` | `NLS_SORT`（JVM 区域设置） | 遵循数据库 |
| `order by` 中的隐式路径 | inner join（丢失行） | 相同 | 相同 | lambdaQL 使用 `left join`（或同一路径的 `where` join） |

对于每个可能为 NULL 的排序键，lambdaQL 都会把 `nulls first` / `nulls last` 写入 HQL。当它就是数据库的默认值时，Hibernate 会去掉它。
默认行为（`ASC` = NULLS FIRST，`DESC` = NULLS LAST）与 H2 和 MariaDB 相同，因此在这两种数据库上
不需要额外的 SQL；只有 Oracle 会加上 `NULLS` 子句。要改变 NULL 的位置，请使用
`by.asc(key, false)`（NULL 在后）或 `by.desc(key, true)`（NULL 在前）（D35）。在 MariaDB 上它们使用 `CASE` 模拟。

### 条件与值

| 项目 | H2 | MariaDB | Oracle |
|---|---|---|---|
| `is not distinct from` | 原生支持 | `<=>`（可使用索引） | `decode(...)`（无法使用索引），因此 lambdaQL 在可能时优先使用 `=` |
| `is [not] true`、`coalesce(boolean, false)` | 可用 | 可用 | **ORA-00932**。lambdaQL 从不生成它们 |
| 空字符串 `''` | 一个值 | 一个值 | **NULL** |
| 大小写和尾部空格 | 精确 | `_ci`：忽略 | 精确（排序使用 `NLS_SORT`） |
| `NaN` | 可存储，最大值 | 无法存储 | 仅 `BINARY_DOUBLE` |
| `float` 列 | REAL 字面量规则 | 扩展为 double | Hibernate 把 `Float` 映射为 `float(24)`（十进制） |
| 整数 `/` | 整数 | **小数**（`5/2 = 2.5`） | – |
| 很大的 `IN` 列表（1,500 个值） | 可用 | 可用 | 在 23ai 上可用（Oracle 22 及更低版本的 1,000 个限制未测量） |

### 子查询

| 项目 | 现象 | lambdaQL |
|---|---|---|
| 子查询中没有 `limit` 的 `order by` | Oracle：语法错误（ORA-00907）。MariaDB：被忽略 | 移除，并发出 WARN |
| MariaDB `IN (... limit n)` | 不支持（错误 1235） | 自动包装：`in (select x.id from (... limit n) x)` |
| MariaDB `lateral` | 结果错误（模拟实现） | 用 `row_number() over (...)` 重写（尚未实现） |

### 更新、插入、删除

| 项目 | 现象 | lambdaQL |
|---|---|---|
| `SET a = ..., b = a + 1`（设置某列后再读取它） | MariaDB 读取**新**值，H2 和 Oracle 读取旧值 | 分析时报错（U3） |
| DML 中的关联路径（`o.getCustomer().getName()`） | Hibernate 在不同数据库上生成不同的连接，并**丢失**没有 customer 的行 | lambdaQL 写成关联标量子查询（在 `where` 和 SET 中）。在 `where` 中，查询会做内连接的路径还会加上 `customer.id is not null`，使 DML 作用于与查询相同的行（U9，已测试） |
| 带有同表子查询的 `DELETE` | MariaDB 错误 1093 | 包装在派生表中（U17；尚未实现） |
| 同一个枚举值只绑定一次，同时用于 STRING 列和 ORDINAL 列 | H2 和 MariaDB 会**不报错地存入错误的值** | 每个位置一个参数，从不共用（U7） |
| `values(list, ...)` 中有很多行 | Oracle：绑定变量硬性上限 65,535（ORA-01745），远未达到上限前就已经很慢 | 拆分：每条语句最多 500 个值，全部在一个事务中执行（U13） |
| 带 `order by ... limit` 的 `update` / `delete` | MariaDB 拒绝 `in` 子查询中的 `limit`（错误 1235）和读取目标表的子查询（错误 1093） | `e0.id in (select t.id from (select x0.id as id ... order by ... limit n) t)`：派生表 `t` 在三个数据库上都可用。内层 select 使用查询的连接（D36），因此行与查询相同（已测试） |
| `on conflict` / upsert | H2 和 Oracle：`merge`。MariaDB：`insert ... on duplicate key update`，更新的行计为 2。Hibernate 的 `do nothing` 在 H2 和 Oracle 上会失败 | `upsert` 和 `onConflict(...).update(...)` 使用 Hibernate 的形式。`ignore()` 写成 `insert ... select ... where not exists`。行数原样返回（D40，已测试） |

### 存储过程与函数

| 项目 | H2 | MariaDB | Oracle |
|---|---|---|---|
| OUT / INOUT 参数 | **不支持** | 支持 | 支持 |
| 多个结果集 | 不支持 | 支持 | 支持（REF CURSOR） |
| 存储过程影响的行数 | – | – | 始终为 -1，因此 lambdaQL 不提供；请使用 OUT 参数 |
| 布尔参数和结果 | – | 支持 | 23ai 及更高版本 |
| 在虚拟列 / 函数索引中使用存储函数 | – | 不可能（错误 1901） | 可以（基于函数的索引） |

由于 H2 没有 OUT 参数，存储过程相关功能在 MariaDB 和 Oracle 上检查。

<a id="107-not-supported-and-what-to-write-instead"></a>
## 10.7 不支持的内容及替代写法

对于此列表中的每种写法，lambdaQL 都会抛出清晰的错误。"计划中"表示将在以后的版本中支持。

### Lambda 写法与加载

| 不支持 | 替代写法 |
|---|---|
| 匿名类、非 `Serializable` 的函数式接口（`java.util.function.Predicate`） | Lambda 字面量或方法引用 |
| 带局部变量、循环、`switch`、`try` 的块 Lambda | 单个表达式。在 Lambda 外部计算值 |
| 写入捕获的变量 | 在 Lambda 外部计算（Java 也要求捕获的局部变量是 effectively final 的） |
| 由 ECJ（Eclipse 编译器）编译的 Lambda | 不保证。只用 javac 测试过 |
| 为比捆绑的 ASM 所支持的版本更新的 JDK 编译的 Lambda | 升级 ASM（lambdaQL 不对它做 shade） |

### 条件

| 不支持 | 替代写法 |
|---|---|
| 一般的三元表达式 `c ? x : y`、值三元表达式 `o.getId() == (c ? p1 : p2)` | `&&` / `\|\|`。在 Lambda 外部选择值。`CASE WHEN` 已在计划中 |
| （支持：`c ? x : false`、`c ? true : x`、`c ? false : true`） | 它们分别表示 `AND`、`OR`、`NOT` |
| 非短路的 `&`、`\|`、`^` | `&&`、`\|\|` |
| 守卫之下的 `/`、`%`、`CAST`（`qty != 0 && total / qty > 2`） | 不用除法进行比较：`qty > 0 && total > 2 * qty` |
| 实体 `==` 实体（`o.getCustomer() == c`） | `o.getCustomer().getId() == c.getId()`（计划中：标识符相等） |
| 集合路径（`o.getItems()...`、`@OneToMany`、`@ManyToMany`） | `exists()` 子查询或连接 |
| `o.getItems().contains(x)` | `exists()` 子查询 |
| 在 Lambda 内部构建的集合（`List.of(1, 2)`） | 在外部构建并捕获。（`groupBy` 键除外） |
| 捕获的集合中的 `null` 元素 | 移除 null；如果需要 NULL 行，加上 `\|\| x == null` |
| 捕获的 `NaN` / `Infinity` | 在查询之前检查该值 |
| `isNaN()` | – |
| 对实体值的其他任何方法调用（`o.items(...)`、你自己的方法） | 用列来编写条件 |
| 字符串拼接（`o.getDescription() + "_x"`，以及 `"A" + code`） | 在 Lambda 外部构建字符串。与列的拼接已在计划中 |
| Lambda 中的 Stream 和其他 Lambda | 对捕获集合使用 `contains`，或使用子查询 |

### 函数与字符串

| 不支持（v0.1） | 替代写法 |
|---|---|
| 实体值上的 Java `String` 方法（`startsWith`、`contains`、`trim`、`toUpperCase`） | 计划中 |
| 日期函数（`YEAR(col) == 2024`） | 范围：`o.getUpdateAt() >= from && o.getUpdateAt() < to` |
| 返回类型为泛型（`<X> X nvl(X, X)`）、实体、集合或 `void` 的 `@SqlFunction` | 以下之一：`Double`、`Integer`、`Long`、`BigDecimal`、`String`、`LocalDate`、`LocalDateTime`、`Boolean` |
| 与 Hibernate 内置函数同名的 `@SqlFunction`（`COALESCE`、`ROUND`、`SYSDATE` 等） | 使用前缀或模式名（`PUBLIC.SQ_FN`） |
| 作为数据库函数的函数式接口常量、mixin | 接口的 `static` 方法 |
| 不带 `@SqlFunction` 的、作用于实体值的自定义静态方法 | 带 `@SqlFunction` 的数据库函数，或用列编写的条件 |
| 用户自定义聚合函数或窗口函数 | 计划中 |
| 在查询 Lambda 外部或在 `where` 中调用 `Agg.*` | 在 `select` / `having` 中使用 |

### 排序与分页

| 不支持 | 替代写法 |
|---|---|
| 排序 Lambda 中的条件或循环（`if (x) by.asc(...)`） | 构建两个查询，或把 `Direction` 作为捕获值传入：`by.sort(o.getPrice(), dir)` |
| 两次 `orderBy`、`orderBy` 之后的 `where`、`limit` 之后的 `orderBy` | 用一个 `orderBy` 包含所有键（这些写法无法编译） |
| 把实体作为排序键（`by.desc(o.getCustomer())`） | 使用列：`by.desc(o.getCustomer().getName())`（无法编译） |
| 连接排序 Lambda 中的方法引用 | Lambda |
| 把排序名称和方向放在一起的一段文本（`"price,desc_nulls_last"`），尚未实现 | 方向用 `Direction.parse(text)`，名称用 `SortSpec.ofPath`。名称由排序策略检查（[4.10](04-order-paging.md#sort-policy)） |
| 带 `limit` / `offset` 的集合 fetch join | 先分页，再加载集合 |
| Spring `Sort` 中的 `ignoreCase()`（[12.5](12-spring-data-jpa.md#125-spring-sort-and-pageable)），尚不支持 | 不带 `ignoreCase()` 的排序 |
| 函数键（`lower(name)`） | 计划中 |
| 按聚合别名排序（`by.desc(s.total())`） | 计划中 |

### 子查询

| 不支持 | 替代写法 |
|---|---|
| 在不是单行的标量比较中使用 `first()` | 使用聚合（`max`、`min`、`avg`、`sum`、`count`）或 `orderBy(...).limit(1)...first()` |
| 子查询中没有 `limit` 的 `orderBy` | 它不起作用，因此会被移除（WARN） |

### 查询列与分组

| 不支持 | 替代写法 |
|---|---|
| `select` / `having` 中不是 `groupBy` 键的非聚合值 | 把它加入 `groupBy` |
| 用普通 DTO 类作为派生表或 CTE 的行类型 | Java record |
| 用 `Set.of(...)` 作为 `groupBy` 键 | `List.of(...)` |

### 更新、插入、删除

| 不支持（v0.1） | 替代写法 |
|---|---|
| 没有 `where` 的 `update` / `delete` | 加上 `.all()`（否则 `execute()` 无法编译） |
| 在同一语句中设置某列后再读取它 | 有意使用旧值，或执行两次更新 |
| 对同一列设置两次 | 一次赋值 |
| 条件值（`c ? a : b`、切换 `!o.isActive()`） | 计划中（`CASE WHEN`）。使用两次带不同 `where` 的更新 |
| 在 `set` Lambda 中用 `if` 选择要设置的列 | 多次调用 `set()` |
| 设置关联的非 `@Id` 属性（`o.getCustomer().setName(x)`） | 直接更新 `Customer` |
| 把 `null` 写入非可选列（`@Id`、`nullable = false`、基本类型） | – |
| 自己写入 `@Version` 属性 | lambdaQL 会替你使用 `update versioned` |
| 值中的字符串拼接 | 计划中 |
| 带显式连接的 `insert ... select` | 计划中。在 v0.1 中请使用多对一路径 |
| 并发插入时原子的 `onConflict(...).ignore()` | 捕获键重复错误（`ignore()` 使用 `not exists`，见 [7.6](07-6-upsert.md)） |
| 基于 `insert ... select` 的 upsert | 用 `values(list, ...)` 逐行 upsert |
| 对更新或删除写 `orderBy(...)` 而没有 `limit(n)` | 加上 `limit(n)`，或去掉 `orderBy`（否则 `IllegalStateException`） |
| 必须只改变一行的更新或删除，用 `execute() == 1` 和回滚来检查 | `executeOne()`：先读取 id（最多 2 个）。有 2 个时什么都不改变，并抛出 `NonUniqueResultException`。有 1 个时用 `id = :p and (条件)` 改变该行。绝不会改变超过一行（D41，已测试） |
| `executeOne()` 与 `orderBy` 或 `limit` 一起使用 | 只用其中之一（否则 `IllegalStateException`） |
| update 或 delete 上的 `offset` | 不计划提供（D42）。DML 只有 `limit(n)`。用 `where`、`orderBy(...)` 和 `limit(n)` 选择行 |
| 对复合键实体使用 `limit` 或 `executeOne()` | 目前还不支持。先查询 id，再执行 `delete ... where ids.contains(id)` |
| 使用 Spring 共享 `EntityManager` 且没有事务的 DML（lambdaQL 中没有 `@Transactional`，D41） | 在 `@Transactional` 或 `TransactionTemplate` 中调用 |
| 对仓库方法 `deleteAll(where)`（批量删除）期望 cascade 或 `@PreRemove` | 使用 Spring Data 的 `deleteAll(Iterable)` 或 `delete(entity)`（见 [12.4](12-spring-data-jpa.md#deleteallwhere-is-not-spring-datas-deleteall)） |
| `returning` | DML 之后再查询一次 |
| 数据库时间（`current_timestamp`） | 传入应用时间（`LocalDateTime.now()` 作为值绑定） |
| 使用 `TABLE` id 生成器的 `insert ... select` | 使用 `values(...)`，或 IDENTITY / SEQUENCE |
| 向 JOINED 或 TABLE_PER_CLASS 子类 `insert` | 不支持（Hibernate 6.6.5 会失败） |

关于 DML 还需注意：批量 DML 不经过持久化上下文。Hibernate 会先执行 flush，但
同一会话中的托管实体之后仍保留旧值。请调用 `clear()`（或 `refresh`）。
JPA 的级联和 `orphanRemoval` 不适用。
DML 语句需要事务：`QueryBuilder(EntityManagerFactory)` 会打开自己的事务；使用 `EntityManager`（包括 Spring 的共享 `EntityManager`）的构建器需要调用方的事务，没有事务时会在发送任何 SQL 之前失败。

### 存储过程

| 不支持 | 替代写法 |
|---|---|
| Lambda 中除一次占位方法调用之外的任何内容（日志、嵌套调用、分支） | 一次调用。事先计算好参数 |
| 对有 OUT 参数的存储过程使用 `exec` | `call` |
| 参数的数据库默认值 | 传入所有参数 |
| 选择并调用 Oracle 包存储过程的某个重载（已能检测有重载的名称：`LQL-4044`）、动态存储过程名 | 计划中 |
| Oracle 隐式结果（`DBMS_SQL.RETURN_RESULT`）、MariaDB 包、存储过程结果的分页 | 不支持 |

<a id="108-caching-a-lambda-is-analyzed-once"></a>
## 10.8 缓存：一个 Lambda 只分析一次

读取字节码是较慢的部分，因此 lambdaQL **对每个 Lambda 只做一次**。

| 项目 | 规则 |
|---|---|
| 缓存什么 | 分析结果：HQL 模板和参数来源列表。失败结果也会缓存 |
| 缓存键 | Lambda 的实现类和方法（名称、签名、种类）、它的类型、实体类，以及 **ClassLoader** |
| 大小 | 有上限（LRU），因此不会无限增长，也不会泄漏类加载器 |
| 不缓存 | 字节码本身（重新读取的代价很低，而保存的体积很大） |
| 捕获值 | **从不缓存。** 每次执行查询时读取并绑定 |
| 对捕获对象的调用（`order.getDescription().trim()`、`this.field`、`LocalDateTime.of(...)`） | 每次执行查询时都在 Java 中**运行**，然后作为一个参数绑定 |
| 只涉及捕获值的条件（`p != null && ...`） | 保留为 `:p1 is not null`，不折叠为常量，以保证缓存正确 |
| 作为捕获值的 `Direction` | 一个绑定值，每次读取（结构仍然保持缓存） |
| DML | 每个 Lambda 类分析一次；`set()` 调用之间的检查使用小型摘要 |

用原型测量（JDK 21）：新 Lambda 的首次分析约需 **0.2 ms**。
此后，包含两次 `set()` 调用的更新，其缓存查找、值提取和检查的开销约为 **0.1 到 0.2 µs**，
即使运行 200 万次之后也是如此。目标是：同一个 Lambda 调用 1,000 次，只读取一次
字节码。Hibernate 也会按 HQL 字符串缓存其查询计划。

这对你意味着：

```java
// 好：一个 Lambda，值会变化
.where(o -> o.getStatus() == status)

// v0.1 中不可行：查询的结构在 Lambda 内部变化
.where(o -> flag ? o.getStatus() == 1 : o.isActive())

// 替代做法：在外部选择 Lambda（两个 Lambda，各自只分析一次）
List<Order> list = flag
    ? qb.from(Order.class).where(o -> o.getStatus() == 1).toList()
    : qb.from(Order.class).where(o -> o.isActive()).toList();
```

<a id="109-tested-environment"></a>
## 10.9 测试环境

| 项目 | 版本 |
|---|---|
| Java | 21（javac）。不保证支持 ECJ |
| Hibernate | 6.6（设计已用 6.6.5 检查）。使用了 HQL 扩展（例如 `is distinct from`）；不保证支持其他 JPA 提供者 |
| 数据库 | H2 2.3、MariaDB 10.11、Oracle 23.26 (23ai) |
| Spring | Spring Boot 3.4.2 和 Spring Data JPA 3.4.2（Spring 模块，[12](12-spring-data-jpa.md)）。Spring Boot 4 尚未验证 |
| 未测试 | PostgreSQL、SQL Server、Oracle 19c 及更早版本（例如 PL/SQL 的 `BOOLEAN` 参数） |

---

[Java 路线](README.md) | 上一章：[9. Java 说明](09-java-notes.md) | 下一章：[11. 错误与语言](../11-errors-and-languages.md) | [本页的 Kotlin 版本](../kotlin/10-rules-and-limits.md)
