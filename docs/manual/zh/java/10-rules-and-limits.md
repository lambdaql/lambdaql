<a id="10-rules-and-limits-java"></a>
# 10. 规则与限制（Java）

> [!IMPORTANT]
> 本页比韩文手册旧。最新规则请参见[韩文页面](../../ko/java/10-rules-and-limits.md)。

[Java 路线](README.md) | [手册首页](../README.md) | 上一章：[9. Java 说明](09-java-notes.md) | 下一章：[11. 错误与语言](../11-errors-and-languages.md) | [本页的 Kotlin 版本](../kotlin/10-rules-and-limits.md)

公共页面：[1. 概述](../01-overview.md) · [11. 错误与语言](../11-errors-and-languages.md)

---

<a id="contents"></a>
## 目录

- [10.1 基本规则：Lambda 就是查询](#101-the-base-rule-a-lambda-is-a-query)
- [10.2 SARGable 规则（对索引友好的 SQL）](#102-sargable-rule-index-friendly-sql)
- [10.3 NULL 规则](#103-null-rules)
- [10.4 运算符规则（R1 ~ R24）](#104-operator-rules-r1-to-r24)
- [10.5 Java 与 SQL 的差异](#105-java-and-sql-differences)
- [10.6 数据库差异](#106-database-differences)
- [10.7 不支持的内容及替代写法](#107-not-supported-and-what-to-write-instead)
- [10.8 缓存：一个 Lambda 只分析一次](#108-caching-a-lambda-is-analyzed-once)
- [10.9 支持环境](#109-tested-environment)

如何阅读示例：

- `assertEquals` 中的字符串是 lambdaQL 生成的 HQL。
- 直接写在 Lambda 中的常量（数字、字符串、布尔值、`null`、enum 常量）作为字面量写进 HQL。
  捕获值和计算出的值始终是绑定参数（`:p1`、`:p2` ……）。
- 实体：`Order`（`id`、`price`、`status`、`active`、`description`、`customer`、`updateAt`），
  `Customer`（`id`、`name`、`grade`、`orders`），`Item`（`order`、`price`、`qty`）。
  10.3 中的行还使用 `Order` 的属性 `cnt`（`Integer`）、`custName`（`String`）、`address`（`@Embedded`，含 `city` 和 `zip`）。
  在这些行中，`active` 是可为 NULL 的 `Boolean`，getter 为 `getActive()`。

---

<a id="101-the-base-rule-a-lambda-is-a-query"></a>
## 10.1 基本规则：Lambda 就是查询

lambdaQL **不调用**条件、排序、赋值 Lambda，而是分析字节码 opcode 并编写 HQL。
只由捕获值构成的表达式以及 `select` 中的构造器调用在 Java 中计算。
Lambda 是用 Java 语法编写的查询。因此：

1. 像 `o.getCustomer().getName()` 这样的路径**表示列**，而不是一连串的调用。
   由于不调用 getter，不会发生 `NullPointerException`。该路径上的比较只在有客户的订单上为真。
   在 `where` 中，如果没有客户的订单可能满足条件，就是 `left join`，否则是 inner join。结果与把 Lambda 在列表上执行的结果相同。
   写了 null 守卫（`o.getCustomer() == null || ...`）时，没有客户的订单也会被选中。
   要查找这些订单，请比较关联本身：`o.getCustomer() == null`。
2. NULL **只在自己写出的地方**处理（`== null`、`!= null`）。
   其他 NULL 遵循 SQL 规则。比较值为 NULL 的行不会返回。
3. Java 的内存规则（对象同一性、装箱缓存、字符串驻留）不适用。
   对象之间的 `==` 表示**值相等**。
4. **无法分析的 Lambda 是错误。** lambdaQL 无法把它作为查询分析时，会抛出带有代码 `[LQL-xxxx]` 和源代码行的异常。

<a id="102-sargable-rule-index-friendly-sql"></a>
## 10.2 SARGable 规则（对索引友好的 SQL）

"SARGable"的意思是数据库可以为该条件使用索引。
这是 lambdaQL 的基本规则（设计文档第 3.9 节）。

| # | 规则 |
|---|---|
| 1 | **不在列一侧放函数、运算符或 `CASE`。** 值需要变换时，在绑定之前在 Java 中变换参数（例如用 `Locale.ROOT` 转为小写） |
| 2 | 两种写法含义相同时，选择只包含简单比较和 `AND` / `OR` / `NOT` 的写法。正确的三值逻辑优先，在此前提下选择 SARGable 的写法 |
| 3 | 生成规则以 H2、MariaDB、Oracle 的 `EXPLAIN` 结果为准 |

<a id="examples"></a>
### 示例

**用普通的 `=` 和 `<>` 进行相等比较**

<!-- region: ManualJavaRulesTest.java#plain-equals -->
```java
// when the query runs, text is not null
SelectQuery<Order> same = qb.from(Order.class).where(o -> o.getDescription() == text);
assertEquals("select e0 from Order e0 where e0.description = :p1", same.toHql().hql());
SelectQuery<Order> other = qb.from(Order.class).where(o -> o.getDescription() != text);
// a NULL description matches neither
assertEquals("select e0 from Order e0 where e0.description <> :p1", other.toHql().hql());
```

lambdaQL 不使用 `is [not] distinct from`。这种写法在 Oracle 上会变成无法使用索引的 `decode(...)`。
实体值遵循 SQL 含义，因此普通的 `=` 或 `<>` 总是正确的。捕获值 `null` 作为 NULL 绑定，不匹配任何行（`WARN` 级别 `LQL-4045`）。

**布尔列与条件的比较**中，条件一侧会变成 `case when`，无法使用索引（`WARN` 级别 `LQL-5013`）。把两种情况拆成 `&&` 和 `||` 来写，就会变成 `AND`/`OR`。

<!-- region: ManualJavaWhereTest.java#boolean-compare -->
```java
int s = 1;
// a boolean column compared with a condition: the condition becomes case when
SelectQuery<Order> compared = qb.from(Order.class).where(o -> o.isActive() == (o.getStatus() > s));
assertEquals("select e0 from Order e0 where e0.active = case when e0.status > :p1 then true else false end",
        compared.toHql().hql());
// the same rows with && and ||: plain comparisons that an index can serve
SelectQuery<Order> query = qb.from(Order.class)
        .where(o -> (o.isActive() && o.getStatus() > s) || (!o.isActive() && o.getStatus() <= s));
assertEquals("select e0 from Order e0 where (e0.active = true and e0.status > :p1)"
        + " or (e0.active = false and e0.status <= :p2)", query.toHql().hql());
```

`CASE` 写法（`e0.active = case when ... end`）能得到正确的行，但需要全表扫描，
因此 lambdaQL 不使用它。

**带子查询的 NOT IN** 写成 `NOT IN`（SQL 含义）。子查询中有 NULL 时不返回任何行。要跳过 NULL，请在子查询中加上 null 检查（见 10.3）。

<a id="what-the-measurements-showed"></a>
### 测量结果

MariaDB 10.11 和 Oracle 23ai，20,000 ~ 50,000 行：

| 写法 | MariaDB | Oracle | 可以吗？ |
|---|---|---|---|
| 在 `_ci` 列上 `name = 'abc'` | 索引查找 | – | 是 |
| `LOWER(name) = LOWER('abc')` | 全索引扫描（19,791 行） | 全表扫描（无函数索引） | **否** |
| `lower(name) = :p_lowered` + 函数索引 | – | 索引范围扫描（`LIKE 'ab%'` 也是） | 是 |
| `name COLLATE BINARY_CI = :p` + 语言索引 | – | 索引范围扫描 | 是 |
| 用 `AND`/`OR` 写的布尔 `==` | range（350 行） | 2 次索引范围扫描 + bitmap OR | 是 |
| 用 `CASE` 写的布尔 `==` | 全索引扫描（49,648 行） | 全表扫描 | **否** |

<a id="db-functions-on-a-column"></a>
### 列上的数据库函数

列一侧的数据库函数（`OrderFns.discount(o.getPrice()) > 10`）无法使用普通索引。
lambdaQL 记录 **WARN**（`LQL-5001`）。例外：在 Oracle 上存在相同表达式的函数索引时不记录。
在 MariaDB 上，`NOT DETERMINISTIC`（默认）函数即使实参只有参数，也会导致全扫描（`o.getPrice() > OrderFns.discount(rate)`），因此这种情况也会警告（`LQL-5007`）。
在 `SargableMode.STRICT` 下，分析会失败而不是警告（`LQL-3066`）。

**SARGable 模式**。`SargableMode` 有三个值。

- `OFF`：不检查，也不查询元数据。
- `WARN`（默认）：记录警告。
- `STRICT`：在 `WARN` 会记录警告的情况下（`LQL-5001`、`LQL-5007`、`LQL-5012`），分析以 `org.lambdaql.NonSargableException`（`LQL-3066`）失败。

按 persistence unit 用 `SargablePolicy.register(emf, SargableMode.STRICT)` 指定，用 `SargablePolicy.unregister(emf)` 解除。在 Spring 中使用 `lambdaql.sargable.mode=STRICT`（见 [12](12-spring-data-jpa.md)）。
检查在每个 Lambda 上执行一次，时机是把分析结果放入共享缓存时。因此请在第一个查询之前注册模式。

**列上的算术**。

- 只由一个列和值组成的 `+ - * /`（`o.getCnt() * 2 > n`）可以移到值一侧。lambdaQL 以 `WARN` 级别记录 `LQL-5012`，在 `STRICT` 下为错误。
- 列与列之间的算术（`o.getPrice() * o.getCnt() > n`）和 `%` 无法移动，因此只以 `DEBUG` 级别记录 `LQL-5013`。在 `STRICT` 下也允许。
- 移动后的写法必须含义相同。请像 `o.getCnt() > n / 2.0` 这样用小数相除。整数除法 `n / 2` 在 `>=`、`=`、`<` 和负数 `n` 时会得到不同的行。乘以负数时请反转比较方向。`LQL-5012` 的消息会给出这些提示。
- 没有采用通过查询索引是否存在来减少警告的方式。原因是权限和同义词会使“没有索引”和“无法得知”混在一起，而且列上的算术是少见的写法。
参见 [8. 存储过程与数据库函数](08-procedures-functions.md)。

示例代码：[`ManualJavaRulesTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaRulesTest.java)（`plain-equals`）、[`ManualJavaWhereTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaWhereTest.java)（`boolean-compare`）。

<a id="103-null-rules"></a>
## 10.3 NULL 规则

| Lambda | SQL | NULL 行为 |
|---|---|---|
| `o.getId() == 10L`（拆箱，一侧是基本类型） | `e0.id = 10` | NULL：不返回该行 |
| `o.getId() == p`（两个对象：`Long`、`String`、enum 等） | `e0.id = :p1` | 列为 NULL：不选中。捕获值 `null`：没有匹配的行（`= NULL`，`WARN` 级别 `LQL-4045`） |
| `o.getDescription() != p` | `e0.description <> :p1` | 列为 NULL：**不**选中（SQL 含义；Java 会选中）。捕获值 `null`：没有匹配的行 |
| `o.getDescription() == o.getCustName()`（两个实体值） | `e0.description = e0.custName` | 任一侧为 NULL：不选中。NULL 与 NULL 也一样（SQL 含义） |
| `o.getDescription().equals(p)` | `e0.description = :p1` | description 为 NULL：不返回该行 |
| `Objects.equals(o.getDescription(), p)`、`"x".equals(o.getDescription())`、`Boolean.TRUE.equals(o.getActive())` | 与 `==` 相同 | 与 `==` 相同 |
| `!Boolean.TRUE.equals(o.getActive())` | `e0.active <> :p1` | NULL：不选中（SQL 含义） |
| `!o.getActive()`（可为 NULL 的 `Boolean`） | `e0.active = false` | NULL：不选中（NULL 不是 false） |
| `x == null`、`x != null` | `is null`、`is not null` | 二值 |
| `o.getCnt() >= 2.5`（整数列，小数值） | 按写出的值比较：常量是字面量（`e0.cnt >= 2.5`），捕获值和 IN 列表也是小数字面量（`e0.cnt >= 2.5BD`、`in (2.5BD, 3.0BD)`） | 保持索引范围扫描（在 MariaDB 上实测）。不会像以前那样截断值而返回错误的行 |
| 整个值对象（`@Embedded`）：`o.getAddress().equals(addr)`、`Objects.equals(o.getAddress(), addr)`、`!o.getAddress().equals(addr)` | 按列展开：`e0.address.city = :p1 and e0.address.zip = :p2`，否定为 `<> ... or ...`。`== null` 为所有列 `is null`（and），`!= null` 为 `is not null`（or） | 每列遵循 SQL 含义：NULL 列既不匹配 `=` 也不匹配 `<>`。捕获的对象为 `null` 时没有匹配的行（`WARN` 级别 `LQL-4045`） |
| 不可能为 NULL 的列（id、基本类型、`nullable = false`，路径中没有关联步骤）上的 `o.getId() == null` | `1=0`。与比较一起使用时去掉检查：`o.getId() != null && o.getId() == id` 为 `e0.id = :p1` | 列中没有 NULL。经过关联的路径（`o.getCustomer().getId()`）保留检查 |
| `!`、`&&`、`\|\|` | `NOT`、`AND`、`OR` | SQL 三值逻辑 |
| `where` 中的 `o.getCustomer().getName().equals(p)` | `j1.name = :p1` + `join e0.customer j1` | 没有客户时不选中订单 |
| `o.getStatus() == 1 \|\| o.getCustomer().getName().equals(p)` | `e0.status = 1 or j1.name = :p1` + `left join e0.customer j1` | 没有客户但 status 为 1 时选中 |
| `o.getCustomer() == null \|\| o.getCustomer().getName().equals(p)` | `e0.customer is null or j1.name = :p1` + `left join e0.customer j1` | 没有客户时选中（与 Java 相同） |
| `o.getCustomer() == null \|\| o.getCustomer().getName() == null` | `e0.customer is null or (e0.customer is not null and j1.name is null)` + `left join e0.customer j1` | 经过路径的 `is null` 比较会加上 `e0.customer is not null` |
| 只用于 `orderBy` 或 `select` 的 `o.getCustomer().getName()` | `j1.name` + `left join e0.customer j1`。必需关联（`optional = false`）为 `join` | 没有客户时该列为 NULL |
| `o.getCustomer() == null`、`o.getCustomer() != null` | `e0.customer is null`、`e0.customer is not null`（FK 列，无连接） | 没有客户的订单 / 有客户的订单 |
| `o.getCustomer().getId()` | `e0.customer.id`（FK 列，无连接） | 没有客户时为 NULL |
| `ids.contains(o.getId())` | `e0.id in (:p1)` | id 为 NULL：不选中 |
| `!ids.contains(o.getId())` | `e0.id not in (:p1)`。可为 NULL 的列也一样 | NULL 值：**不**选中（SQL 含义；`DEBUG` 级别 `LQL-5009`；Java 会选中） |
| 带 `!...contains(x)` 的子查询 | `x not in (select ...)` | 子查询中有 NULL 时不选中任何行（SQL 含义） |
| `Agg.sum`、`avg`、`min`、`max` | `sum(...)` …… | 没有行或所有值为 NULL 时为 NULL |
| `Agg.count()` | `count(*)` | 没有行时为 0 |

<a id="captured-null-values"></a>
### 捕获值为 null

| 捕获值的位置 | 允许 `null` 吗？ |
|---|---|
| 对象之间的 `==` / `!=`、`Objects.equals`、`col.equals(p)`、`p.equals(col)` | 是。但没有匹配的行（`= NULL`）。查询执行时以 `WARN` 级别记录 `LQL-4045` |
| 在针对同一个值的 null 守卫之下：`p == null \|\| o.getStatus() > p` | 是。守卫在查询执行时由 Java 判定。`p == null` 时不过滤，其他值时为 `e0.status > :p1`。只有自身基本类型拆箱的比较与守卫位置无关 |
| 拆箱比较（`o.getPrice() > p`，`Double p`） | 是。但没有匹配的行（`> NULL`）。不执行拆箱为自身基本类型的调用。查询执行时以 `DEBUG` 级别记录 `LQL-4045` |
| 对捕获值的方法调用（`p.trim()`）、转换为其他基本类型的拆箱（`p.intValue()`，`Double p`） | **否。** 在 Java 中计算值时出错（`LQL-4012`） |
| 用于 `in` 的集合中的 `null` 元素 | **否。** 绑定时出错 |
| `NaN`、`Infinity` | **否。** 始终出错 |

<a id="104-operator-rules-r1-to-r24"></a>
## 10.4 运算符规则（R1 ~ R24）

这是设计文档（第 3.6 节）规则表的精简版。

| # | Lambda 写法 | 含义 |
|---|---|---|
| R1 | 基本类型比较，或一侧为基本类型（`o.getId() == 10L`、`o.getStatus() > p`） | `=`、`<>`、`<` ……，SQL NULL 规则 |
| R2 | 对象 `==` / `!=`（`Long`、`String`、`Boolean`、enum） | 值相等，而不是对象同一性。SQL 含义的 `=` / `<>`。捕获值 `null` 不匹配任何行 |
| R3 | `x == null`、`x != null` | `IS NULL`、`IS NOT NULL` |
| R4 | `a.equals(b)` | `a = b`，SQL NULL 规则 |
| R5 | `Objects.equals(a, b)`、`"x".equals(a)`、`Boolean.TRUE.equals(a)` | 与 R2 相同 |
| R6 | `!`、`&&`、`\|\|` | `NOT`、`AND`、`OR`。SQL 三值逻辑 |
| R7 | 字符串值比较 | 遵循**数据库排序规则**（大小写、尾部空格）。Oracle 把 `''` 视为 NULL |
| R8 | 实体 `==` 实体 | 拒绝（`LQL-3011`）。请比较 id |
| R9 | 捕获值 | 带有执行时值的绑定参数 |
| R10 | 属性访问 | getter 调用，映射到 JPA 属性 |
| R11 ~ R23 | Kotlin 规则 | 见[本页的 Kotlin 版本](../kotlin/10-rules-and-limits.md) |
| R24 | 关联路径（`o.getCustomer().getName()`） | 列。to-one 关联：where、order by、select 共享的别名。在 `where` 中，没有关联的行可能满足条件时为 `left join`，否则为 inner join（结果与把 Lambda 在列表上执行的结果相同）；只用于 order by 或 select 时为 `left join`（必需关联 `optional = false` 为 inner join，A4）。嵌入：同一张表。以目标 `@Id` 结尾或把关联与 `null` 比较：FK 列，无连接。集合：错误 |

其他规则：

- `x.compareTo(y) > 0`、`isBefore`、`isAfter` 变为 `x > y` 等。
- 直接写在 Lambda 中的常量（包括编译期 `static final` 常量）是字面量：`o.getPrice() > 100` 为 `e0.price > 100.0`，`"O'Brien"` 为 `'O''Brien'`。捕获值和计算出的值（`new BigDecimal("1.5")`）是绑定参数。例外：与整数列比较的小数捕获值是小数字面量（`2.5BD`）。
- javac 有时会移动 `!` 的位置（德摩根定律、双重否定）。结果不会因此改变。
  lambdaQL 把每个条件恰好保留一次，不“简化”逻辑。
- 即使写出在 Java 中恒为真的条件（`x > 5 || !(x > 5)`），也不会简化。
  在 SQL 中，`x` 为 NULL 的行会被排除。这是 SQL 的规则。
- IntelliJ 会对 `o.getId() == p` 给出 "Number objects compared using `==`" 警告。在 lambdaQL Lambda 中，
  这是有意的写法（值相等）。可以在方法上声明 `@SuppressWarnings("NumberEquality")` 来消除警告。

哪些 Java 类型属于 R1、哪些属于 R2，见 [9.3](09-java-notes.md#93-equality-of-boxed-values)。

<a id="105-java-and-sql-differences"></a>
## 10.5 Java 与 SQL 的差异

这些差异来自 SQL 本身，不是编译器的问题。
"G" 编号是设计文档（第 2.7 节）中的 ID。

| # | 差异 | lambdaQL 的处理 |
|---|---|---|
| G1 | `status` 为 NULL 时，Java 中 `!Objects.equals(status, 1)` 为 true，但 SQL 的 `status <> 1` 不返回该行 | 渲染为 `status <> 1`：SQL 含义。排除 NULL 行，并以 `DEBUG` 级别记录 `LQL-5009`。要保留这些行，请写 `status == null \|\| ...` |
| G2 | `!Boolean.TRUE.equals(active)`、`!active`（可为 NULL）与 Java | 渲染为 `active <> :p1`、`active = false`：NULL 的 Boolean 不是 false（SQL 含义） |
| G3 | 使用 NULL 参数的 `name = ?` 返回 0 行 | 保持原样（SQL 含义）。执行时以 `WARN` 级别记录 `LQL-4045`。要查找 NULL，请写 `x == null` |
| G4 | `status NOT IN (1, NULL)` 始终返回 0 行 | 捕获集合中的 `null` 是错误。子查询 `NOT IN` 保持 SQL 含义：请在子查询中加上 null 检查 |
| G5 | 实体的列值为 NULL 的情况 | 已写入文档：该行按 SQL 规则不返回 |
| G6 | `NaN`：H2 把 NaN 作为最大值排序且 `NaN = NaN` 为 true，MariaDB 无法存储，Oracle 只能存储在 `BINARY_DOUBLE` 中 | 捕获的 NaN 或 Infinity 是错误。已存储的 NaN 遵循数据库 |
| G7 | `float` 列与 `double` 值的比较：各数据库结果不同 | 对 float/double **不保证与 Java 逐位相同的结果**。lambdaQL 不添加 `CAST`。请自行选择列映射（例如 Oracle 的 `binary_float` / `binary_double`） |
| G9 | `x > 5 \|\| !(x > 5)` 会排除 NULL 行 | 不简化，按写出的样子渲染 |
| G10 | SQL 不保证求值顺序：`qty != 0 && total / qty > 2` 也可能除以 0 | 按写出的样子渲染。请不要依赖守卫，改为不用除法的比较（[3.12](03-where.md#312-errors-that-depend-on-order-of-evaluation)） |
| G11 | 字符串：MariaDB `_ci` 忽略大小写和尾部空格，Oracle 把 `''` 存储为 NULL，Oracle `NLS_SORT` 跟随 JVM 区域设置 | 已写入文档：字符串比较和排序遵循数据库。在 Oracle 上请设置 `NLS_SORT=BINARY`（会话初始化或 `-Duser.language=en`）。在 Oracle 上 `"".equals(x)` 与 `isEmpty()` 的行为不同 |
| G12 | Java 中对象的 `==` 是同一性（128 以上的 `Long`、`new String` 为 false） | lambdaQL 把 `==` 定义为值相等 |
| G13 | 存有 0/1 以外值的旧式布尔列（MariaDB `TINYINT(1)`、Oracle `NUMBER(1)`）读出的结果不同 | Hibernate 生成的 DDL（`bit(1)`、Oracle 23 `boolean`）没有问题。旧式 schema 请注意 |
| G14 | Oracle 23 的 bug：对复合条件使用 `WHERE (a AND b) = false` / `IS FALSE` / `IS NULL` 会丢失行 | lambdaQL 不用布尔测试包裹复合条件。否定始终是 `NOT(...)` |
| G15 | Oracle `CHAR` 列与 `String` 绑定不匹配 | 已写入文档：请注意实体映射 |

更多差异：

| 差异 | Java | SQL | lambdaQL |
|---|---|---|---|
| `BigDecimal` 相等比较 | `equals` 比较标度（`1.0` 与 `1.00` 为 false） | `=` 比较值 | 值相等 |
| 整数除法 | `5 / 2` 为 `2` | MariaDB 为 `2.5` | 取决于数据库。请避免含列的整数 `/` |
| `BigDecimal` 除法与函数结果 | Java 标度规则 | 由数据库决定标度（例如对函数结果，H2 为 1，MariaDB 和 Oracle 为 0） | 已写入文档 |
| Unicode 忽略大小写（`ſ`、`İ`） | Java 规则 | 数据库排序规则 | 遵循数据库 |
| 字符串长度 | UTF-16 单元 | H2 为 UTF-16 单元，MariaDB 为码点 | 已写入文档 |
| NULL 与字符串连接 | 文本 `"null"` | NULL（Oracle：`NULL \|\| 'x'` 为 `'x'`） | Lambda 中的字符串连接被拒绝（`LQL-2007`） |
| Lambda 中的 `LocalDateTime.now()` | 应用时间 | – | 以**应用时间**绑定。不支持数据库时间（`current_timestamp`） |
| `Set` 中的相同元素 | 由 `equals` / `hashCode` 决定 | 按主键区分行 | Set 的大小小于行数时，`toSet()` 和 `findSet()` 抛出 `IllegalStateException`（实体的 `equals` / `hashCode` 把不同的行判定为相同，或 `@Id` 不唯一，例如视图）。请使用 `toList()` |

<a id="106-database-differences"></a>
## 10.6 数据库差异

lambdaQL 隐藏了许多差异，但有些仍需要了解。

<a id="sorting-and-paging"></a>
### 排序与分页

| 项目 | H2 | MariaDB | Oracle | lambdaQL |
|---|---|---|---|---|
| NULL 位置，`ASC` | 最前 | 最前 | **最后** | 在所有数据库上 `ASC` 为 NULLS FIRST |
| NULL 位置，`DESC` | 最后 | 最后 | **最前** | 在所有数据库上 `DESC` 为 NULLS LAST |
| `NULLS FIRST/LAST` 语法 | 有 | **没有**（语法错误） | 有 | Hibernate 在 MariaDB 上用 `CASE` 模拟：排序**无法使用索引** |
| 分页 SQL | `offset ? rows fetch first ? rows only` | `limit ?,?` | `offset ? rows fetch first ? rows only` | 交给 Hibernate（`setFirstResult` / `setMaxResults`） |
| 值相同的行的顺序 | – | – | 随机 | 分页时如果键中没有 id，lambdaQL 会追加根实体的 `@Id asc`。有唯一且不可能为 NULL 的键（`unique = true` 且 `nullable = false`、`@NaturalId`）时不追加 |
| 字符串顺序 | 区分大小写 | `_ci` | `NLS_SORT`（JVM 区域设置） | 遵循数据库 |
| `order by` 中的隐式路径 | inner join（丢失行） | 相同 | 相同 | lambdaQL 使用 `left join`（必需关联为 inner join，或同一路径的 `where` 连接） |
| `order by`、`distinct`、`group by` 中的 `@Lob` 列 | 可用 | 可用 | **ORA-22848 / ORA-22849** | 不修改查询，以 `DEBUG` 级别记录 `LQL-5010` |
| `@Enumerated(EnumType.STRING)` enum 列的排序 | 按名称 | 按名称 | 按名称 | 不修改查询，以 `DEBUG` 级别记录 `LQL-5011`。声明顺序只在 `ORDINAL` 映射中 |

lambdaQL 对所有可能为 NULL 的键在 HQL 中写出 `nulls first` / `nulls last`。只经过必需关联且不可能为 NULL 的路径不写。与数据库默认值相同时，Hibernate 会去掉它。
默认值（`ASC` = NULLS FIRST，`DESC` = NULLS LAST）与 H2、MariaDB 相同，因此在这两者上不需要额外的
SQL。只有 Oracle 会加上 `NULLS` 子句。要改变 NULL 的位置，请使用
`asc(key, Nulls.LAST)`（NULL 在最后）或 `desc(key, Nulls.FIRST)`（NULL 在最前）。在 MariaDB 上它们使用 `CASE` 模拟。

<a id="conditions-and-values"></a>
### 条件与值

| 项目 | H2 | MariaDB | Oracle |
|---|---|---|---|
| `is not distinct from` | 原生支持 | `<=>`（使用索引） | `decode(...)`（无索引）。lambdaQL 不使用这种写法 |
| `is [not] true`、`coalesce(boolean, false)` | 可用 | 可用 | **ORA-00932**。lambdaQL 不使用 |
| 空字符串 `''` | 值 | 值 | **NULL** |
| 大小写与尾部空格 | 精确匹配 | `_ci`：忽略 | 精确匹配（排序按 `NLS_SORT`） |
| `NaN` | 可存储，最大值 | 无法存储 | 仅 `BINARY_DOUBLE` |
| `float` 列 | REAL 字面量规则 | 扩展为 double | Hibernate 把 `Float` 映射为 `float(24)`（十进制） |
| 整数 `/` | 整数 | **小数**（`5/2 = 2.5`） | – |
| 大型 `IN` 列表（1,500 个值） | 可用 | 可用 | 在 23ai 上可用（Oracle 22 及以下的 1,000 个限制未测量） |

<a id="subqueries"></a>
### 子查询

| 项目 | 发生的情况 | lambdaQL |
|---|---|---|
| 没有 `limit` 的子查询 `order by` | Oracle：语法错误（ORA-00907）。MariaDB：被忽略 | 去掉并记录 WARN |
| MariaDB `IN (... limit n)` | 不支持（错误 1235） | 自动包裹：`in (select x.id from (... limit n) x)` |

<a id="update-insert-delete"></a>
### 更新、插入、删除

| 项目 | 发生的情况 | lambdaQL |
|---|---|---|
| `SET a = ..., b = a + 1`（设置后读取该列） | MariaDB 读取**新**值，H2 和 Oracle 读取旧值 | 分析时报错 |
| DML 中的关联路径（`o.getCustomer().getName()`） | Hibernate 在各数据库上生成不同的连接，并**丢失**没有客户的**行** | lambdaQL 使用相关标量子查询（`where` 和 SET）。在 `where` 中，对查询时会 inner join 的路径还会加上 `customer.id is not null`（会 `left join` 的路径不加）。因此 DML 作用于与查询相同的行 |
| 带有对同一张表的子查询的 `DELETE` | MariaDB 错误 1093 | 先通过派生表读取目标 id：`d0.id in (select t.id from (select e0.id as id … where <condition>) t)`。对相关子查询也有效，在三个数据库上删除相同的行（实测）。`update` 不包裹（在 MariaDB 上也可用） |
| 把同一个 enum 值只绑定一次，同时用于 STRING 列和 ORDINAL 列 | H2 和 MariaDB **不报错地存储错误的值** | 每个位置一个参数，不共享 |
| `values(list, ...)` 的大量行 | Oracle：绑定变量固定上限 65,535 个（ORA-01745），远在此之前就已非常慢 | 拆分为每条语句不超过 500 个值，并在一个事务中执行所有语句 |
| 带 `order by ... limit` 的 `update` / `delete` | MariaDB 拒绝 `in` 子查询中的 `limit`（错误 1235）和读取目标表的子查询（错误 1093） | `e0.id in (select t.id from (select x0.id as id ... order by ... limit n) t)`：借助派生表 `t` 在三个数据库上都可用。内层 select 使用查询的连接，因此作用于与查询相同的行 |
| `on conflict` / upsert | H2 和 Oracle 为 `merge`。MariaDB 为 `insert ... on duplicate key update`，把更新的行计为 2。Hibernate 的 `do nothing` 在 H2 和 Oracle 上失败 | `upsert` 和 `onConflict(...).update(...)` 使用 Hibernate 的写法。`ignore()` 写成 `insert ... select ... where not exists`。行数原样返回 |

<a id="procedures-and-functions"></a>
### 存储过程与函数

| 项目 | H2 | MariaDB | Oracle |
|---|---|---|---|
| OUT / INOUT 参数 | **没有** | 有 | 有 |
| 多个结果集 | 没有 | 有 | 有（REF CURSOR） |
| 存储过程的受影响行数 | – | – | 始终为 -1。因此 lambdaQL 不提供。请使用 OUT 参数 |
| 布尔参数与结果 | – | 有 | 23ai 及以上 |
| 虚拟列 / 函数索引中的存储函数 | – | 不可以（错误 1901） | 可以（基于函数的索引） |

H2 没有 OUT 参数，因此请在 MariaDB 或 Oracle 上确认存储过程功能。

<a id="107-not-supported-and-what-to-write-instead"></a>
## 10.7 不支持的内容及替代写法

对于此列表中的写法，lambdaQL 大多会抛出清晰的错误。

<a id="lambda-form-and-loading"></a>
### Lambda 写法与加载

| 不支持 | 替代写法 |
|---|---|
| 匿名类、不是 `Serializable` 的函数式接口（`java.util.function.Predicate`） | Lambda 字面量（排序键也可以使用 getter 方法引用） |
| 条件中的方法引用（`where(Order::isActive)`，`LQL-1009`） | Lambda：`where(o -> o.isActive())` |
| 带局部变量、循环、`switch`、`try` 的块 Lambda | 单个表达式。值在 Lambda 之外计算 |
| 写入捕获的变量 | 在 Lambda 之外计算（Java 也要求捕获的局部变量是 effectively final 的） |
| 用 ECJ（Eclipse 编译器）编译的 Lambda | 不保证。请使用 javac |
| 在 Java 21~23 上运行时，为捆绑的 ASM 不认识的更新 JDK 编译的 Lambda | 升级 ASM（lambdaQL 没有对 ASM 做 shade）。在 Java 24 及以上运行时不适用（使用 `java.lang.classfile`） |

<a id="conditions"></a>
### 条件

| 不支持 | 替代写法 |
|---|---|
| 布尔三元运算符 `c ? x : y` | `&&` / `\|\|`。支持选择值的三元运算符：只用捕获值和常量时为绑定参数，使用实体值时为 `case when`（[3.5.1](03-where.md#351-values-chosen-by-a-condition)） |
| （支持：`c ? x : false`、`c ? true : x`、`c ? false : true`） | 分别表示 `AND`、`OR`、`NOT` |
| 不短路求值的 `&`、`\|`、`^` | `&&`、`\|\|` |
| 实体 `==` 实体（`o.getCustomer() == c`） | `o.getCustomer().getId() == c.getId()`（`LQL-3011`） |
| 值对象（`@Embedded`）的大小比较、与其他类型的比较（`LQL-3012`） | 比较字段。支持与同类型的相等比较（`equals`、`==`、`!=`、`null`） |
| 集合路径（`c.getOrders()...`、`@OneToMany`、`@ManyToMany`，`LQL-3010`） | `exists()` 子查询或连接 |
| `c.getOrders().contains(x)` | `exists()` 子查询 |
| 在 Lambda 中创建的集合（`List.of(1, 2)`） | 在外部创建后捕获。（`groupBy` 键除外） |
| 捕获集合中的 `null` 元素 | 去掉 null。需要 NULL 行时加上 `\|\| x == null` |
| 捕获的 `NaN` / `Infinity` | 在查询前检查值 |
| `isNaN()` | – |
| 对实体值的其他方法调用（`o.items(...)`、自定义方法） | 用列写条件 |
| 字符串连接（`o.getDescription() + "_x"`，以及 `"A" + code`） | 在 Lambda 之外构建字符串（`LQL-2007`） |
| Lambda 中的 Stream 和其他 Lambda | 捕获集合的 `contains` 或子查询 |

<a id="functions-and-strings"></a>
### 函数与字符串

| 不支持 | 替代写法 |
|---|---|
| 对实体值调用 Java `String` 方法（`trim`、`toUpperCase` 等，`LQL-3008`）。`startsWith`、`endsWith`、`contains` 以 `like` 支持 | – |
| 日期函数（`YEAR(col) == 2024`） | 范围：`o.getUpdateAt() >= from && o.getUpdateAt() < to` |
| 返回泛型类型（`<X> X nvl(X, X)`）、实体、集合、`void` 的 `@SqlFunction` | 以下之一：`Double`、`Integer`、`Long`、`BigDecimal`、`String`、`LocalDate`、`LocalDateTime`、`Boolean` |
| 与 Hibernate 内置函数（`COALESCE`、`ROUND`、`SYSDATE` 等）同名的 `@SqlFunction` | 使用前缀或 schema 名（`PUBLIC.SQ_FN`） |
| 作为数据库函数使用的函数式接口常量、mixin | 接口 `static` 方法 |
| 不带 `@SqlFunction`、作用于实体值的自定义静态方法 | 带 `@SqlFunction` 的数据库函数，或用列写的条件 |
| 用户自定义聚合函数或窗口函数 | – |
| 在查询 Lambda 之外或在 `where` 中调用 `Agg.*` | 在 `select` / `having` 中使用 |
| 在排序 Lambda 之外调用 `By` 的排序函数（`IllegalStateException`，`LQL-4046`） | 在 `orderBy`、`SortSpec.of`、`then` 的 Lambda 中使用 |

<a id="sort-and-paging"></a>
### 排序与分页

| 不支持 | 替代写法 |
|---|---|
| 排序 Lambda 中的条件或循环（`if (x) asc(...)`） | 构建两个查询，或把 `Direction` 作为捕获值传入：`sort(o.getPrice(), dir)` |
| 与排序函数一起使用的 `Direction` 参数（`orderBy(o -> desc(o.getPrice()), dir)`，`LQL-3069`） | `orderBy(o -> sort(o.getPrice(), dir))` 或单个键：`orderBy(o -> o.getPrice(), dir)` |
| 同时包含排序名称和方向的单个字符串（`"price,desc_nulls_last"`） | 方向用 `Direction.parse(text)`，名称用 `SortSpec.ofPath`。名称由排序策略检查（[4.10](04-order-paging.md#sort-policy)） |
| 在连接结果的 `distinct()` 中按被连接实体的列或关联路径排序（`LQL-3046`） | 按主实体自身的列排序，或使用 `exists` 子查询 |
| 与 `limit` / `offset` 一起使用的集合 fetch join | 先分页，再加载集合 |
| Spring `Sort` 的 `ignoreCase()`（[12.5](12-spring-data-jpa.md#125-spring-sort-and-pageable)） | 不带 `ignoreCase()` 的排序 |
| 函数键（`lower(name)`） | – |
| 按聚合别名排序（`desc(s.total())`） | – |

<a id="limits-subqueries"></a>
### 子查询

| 不支持 | 替代写法 |
|---|---|
| 在不是单行的标量比较中使用 `first()` | 使用聚合（`max`、`min`、`avg`、`sum`、`count`）或 `orderBy(...).limit(1)...first()` |
| 没有 `limit` 的子查询中的 `orderBy` | 没有效果，因此被去掉（WARN） |

<a id="select-and-group-by"></a>
### 查询与分组

| 不支持 | 替代写法 |
|---|---|
| `select` / `having` 中不是 `groupBy` 键的非聚合值 | 加入 `groupBy` |
| 用作派生表或 CTE 行类型的普通 DTO 类 | Java Record |
| 作为 `groupBy` 键的 `Set.of(...)` | `List.of(...)` |

<a id="limits-update-insert-delete"></a>
### 更新、插入、删除

| 不支持 | 替代写法 |
|---|---|
| 没有 `where` 的 `update` / `delete` | 加上 `.all()` |
| 在同一条语句中设置后再读取该列 | 有意使用旧值，或执行两次 update |
| 两次设置同一列 | 一次赋值 |
| 条件值（`c ? a : b`、切换 `!o.isActive()`） | 使用 `where` 不同的两个 update |
| 在 `set` Lambda 中用 `if` 选择要设置哪一列 | 多次调用 `set()` |
| 设置关联中非 `@Id` 的属性（`o.getCustomer().setName(x)`） | 直接更新 `Customer` |
| 向必需列（`@Id`、`nullable = false`、基本类型）放入 `null` | – |
| 直接写入 `@Version` 属性 | lambdaQL 会改用 `update versioned` |
| 值中的字符串连接 | – |
| update 或 delete 中的显式连接 | 不添加。使用关联路径或 `sub` |
| 带显式连接的 `insert ... select` | 使用多对一路径 |
| 并发插入时原子性的 `onConflict(...).ignore()` | 处理键重复错误（`ignore()` 使用 `not exists`，见 [7.6](07-6-upsert.md)） |
| 用 `insert ... select` 做 upsert | 用 `values(list, ...)` 逐行 upsert |
| 不带 `limit(n)` 为 update 或 delete 指定 `orderBy(...)` | 加上 `limit(n)` 或去掉 `orderBy`（否则 `IllegalStateException`） |
| 用 `execute() == 1` 加回滚检查只应修改一行的 update 或 delete | `executeOne()`：先最多读取 2 个 id。为 2 个时不做任何修改并抛出 `NonUniqueResultException`。为 1 个时用 `id = :p and (条件)` 修改该行。不会修改 2 行以上 |
| 把 `executeOne()` 与 `orderBy` 或 `limit` 一起使用 | 只用其中之一（否则 `IllegalStateException`） |
| update 或 delete 中的 `offset` | 没有计划。DML 只有 `limit(n)`。用 `where`、`orderBy(...)`、`limit(n)` 指定目标行 |
| 复合键实体的 `limit` 或 `executeOne()` | 先查询 id，再 `delete ... where ids.contains(id)` |
| 用 Spring 共享 `EntityManager` 在没有事务的情况下执行 DML（lambdaQL 中没有 `@Transactional`） | 在 `@Transactional` 或 `TransactionTemplate` 中调用 |
| 在 repository 方法 `deleteAll(where)`（批量删除）中期待 cascade 或 `@PreRemove` | 使用 Spring Data 的 `deleteAll(Iterable)` 或 `delete(entity)`（见 [12.4](12-spring-data-jpa.md#deleteallwhere-is-not-spring-datas-deleteall)） |
| `returning` | DML 之后再次查询 |
| 数据库时间（`current_timestamp`） | 传入应用时间（`LocalDateTime.now()` 作为值绑定） |
| 使用 `TABLE` id 生成器的 `insert ... select` | 使用 `values(...)` 或 IDENTITY / SEQUENCE |
| 向 JOINED 或 TABLE_PER_CLASS 子类 `insert` | 不支持（Hibernate 6.6.5 失败，7.4 未测量） |

关于 DML 还有一点：批量 DML 不经过持久化上下文。Hibernate 会先 flush，
但同一会话中受管理的实体之后仍保留旧值。请调用 `clear()`（或 `refresh`）。
JPA cascade 和 `orphanRemoval` 不适用。
DML 语句需要事务。`QueryBuilder(EntityManagerFactory)` 会自行开启事务。使用 `EntityManager` 的构建器（包括 Spring 的共享 `EntityManager`）使用调用方的事务，没有事务时会在发送 SQL 之前失败。

<a id="procedures"></a>
### 存储过程

| 不支持 | 替代写法 |
|---|---|
| Lambda 中除一次桩方法调用之外的任何内容（日志、嵌套调用、分支） | 一次调用。参数预先计算 |
| 对带 OUT 参数的存储过程使用 `exec` | `call` |
| 参数的数据库默认值 | 传入所有参数 |
| 选择 Oracle 包存储过程的某个重载来调用（会检测出有重载的名称：`LQL-4044`）、动态存储过程名 | – |
| Oracle 隐式结果（`DBMS_SQL.RETURN_RESULT`）、MariaDB 包、存储过程结果分页 | 不支持 |

<a id="108-caching-a-lambda-is-analyzed-once"></a>
## 10.8 缓存：一个 Lambda 只分析一次

读取字节码 opcode 是较慢的部分，因此 lambdaQL **对每个 Lambda 只做一次**。

| 项目 | 规则 |
|---|---|
| 缓存的内容 | 分析结果：HQL 模板和参数来源列表。失败也会缓存 |
| 缓存键 | Lambda 的实现类和方法（名称、签名、种类）、Lambda 的类型、实体类，以及 **ClassLoader** |
| 大小 | 有上限（LRU）。因此不会无限增长，也不会泄漏类加载器 |
| 不缓存的内容 | 字节码 opcode 本身（重新读取开销小，保留则占用大） |
| 捕获值 | **不缓存。** 每次执行查询时读取并绑定 |
| 对捕获对象的调用（`order.getDescription().trim()`、`this.field`、`LocalDateTime.of(...)`） | **每次**执行查询时在 Java 中执行，然后作为一个参数绑定 |
| 只使用捕获值的条件（`p != null && ...`） | 不折叠为常量，保留为 `:p1 is not null`。因此缓存保持正确 |
| 作为捕获值的 `Direction` | 绑定值，每次读取（结构保持缓存） |
| DML | 每个 Lambda 类分析一次。`set()` 调用之间的检查使用小型摘要 |

用原型测量的结果（JDK 21）：新 Lambda 的首次分析约需 **0.2 ms**。
此后，带两次 `set()` 调用的 update 在缓存查找、取值和检查上约需 **0.1 ~ 0.2 µs**。
执行 200 万次之后也是如此。目标：同一个 Lambda 调用 1,000 次，字节码 opcode
也只读取一次。Hibernate 同样按 HQL 字符串缓存查询计划。

这对使用者意味着：

<!-- region: ManualJavaRulesTest.java#cache-shape -->
```java
// good: one lambda; only the value changes
int status = 1;
SelectQuery<Order> one = qb.from(Order.class).where(o -> o.getStatus() == status);
assertEquals("select e0 from Order e0 where e0.status = :p1", one.toHql().hql());
// rejected: the shape of the query changes inside the lambda
assertEquals("LQL-2021", assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(o -> flag ? o.getStatus() == 1 : o.isActive())).code());
// instead: choose the lambda outside (two lambdas, each analyzed once)
List<Order> list = flag
        ? qb.from(Order.class).where(o -> o.getStatus() == 1).toList()
        : qb.from(Order.class).where(o -> o.isActive()).toList();
```

示例代码：[`ManualJavaRulesTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaRulesTest.java)（`cache-shape`）。

<a id="109-tested-environment"></a>
## 10.9 支持环境

| 项目 | 版本 |
|---|---|
| Java | 21（javac）。不保证 ECJ |
| Hibernate | 6.6 或 7.4。使用了 HQL 扩展（例如 `function(...)`、`nulls first`），因此不保证其他 JPA 实现 |
| 数据库 | H2 2.3、MariaDB 10.11、MySQL 8.4、PostgreSQL 17、Oracle 23.26（23ai） |
| Spring | Spring Boot 3.5（Spring Data JPA 3.5）和 4.1（Spring Data JPA 4.1）（Spring 模块，[12](12-spring-data-jpa.md)、[13](../13-spring-boot-versions.md)） |
| 未确认 | SQL Server、Oracle 19c 及以下（例如 PL/SQL `BOOLEAN` 参数） |

---

[Java 路线](README.md) | 上一章：[9. Java 说明](09-java-notes.md) | 下一章：[11. 错误与语言](../11-errors-and-languages.md) | [本页的 Kotlin 版本](../kotlin/10-rules-and-limits.md)
