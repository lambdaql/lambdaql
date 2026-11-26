<a id="3-conditions-where-java"></a>
# 3. 条件（`where`）（Java）

> [!IMPORTANT]
> 本页比韩文手册旧。最新规则请参见[韩文页面](../../ko/java/03-where.md)。

[Java 路线](README.md) | [手册首页](../README.md) | 上一章：[2. 入门](02-getting-started.md) | 下一章：[4. 排序与分页](04-order-paging.md) | [本页的 Kotlin 版本](../kotlin/03-where.md)

公共页面：[1. 概述](../01-overview.md) · [11. 错误与语言](../11-errors-and-languages.md)

---

<a id="31-the-idea-in-one-sentence"></a>
## 3.1 概述

**lambdaQL 的 Lambda 表达式是查询表达式，不是代码。** lambdaQL 不调用条件 Lambda 表达式，而是分析其字节 opcode。
只有仅由捕获值组成的表达式才会先在 Java 中计算（参阅 [3.11](#311-captured-values)）。
因此，处理实体的部分不适用 Java 的执行规则（对象同一性、装箱缓存）。
lambdaQL 使用自己的规则。本章说明这些规则。

lambdaQL 无法把 Lambda 表达式分析为查询时，会抛出带代码的异常（参阅 [3.13](#313-what-is-rejected)）。

<a id="32-example-entities"></a>
## 3.2 示例实体

示例使用示例实体 `Order`、`Customer`、`Item`。属性列表见 [2. 入门中的示例实体](02-getting-started.md#example-entities)。

本章常用的属性如下。

- `Order.price`（`double`）和 `Order.status`（`short`）是基本类型，不能为 NULL。
- `Order.cnt`（`Integer`）、`Order.total`（`Double`）、`Order.description`（`String`）可以为 NULL。
- `Order.customer` 是指向 `Customer` 的 `@ManyToOne` 关联，`Order.kind` 是 enum `Order.Kind`（`NORMAL`、`RUSH`）。

<a id="33-basic-form"></a>
## 3.3 基本形式

<!-- region: SelectQueryTest.java#to-list -->
```java
QueryBuilder qb = new QueryBuilder(emf);
List<Order> orders = qb.from(Order.class)
        .where(o -> o.getStatus() == 1 && o.getPrice() > 100)
        .toList();
```

**各数据库的 SQL**

<!-- sql: to-hql -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>100.0
```

本章的示例在 H2、MariaDB、Oracle 上返回的行，与在 Java 中应用相同过滤的结果一致。

`toHql()` 不执行查询，而是返回 HQL 和参数。`assertEquals` 中的字符串就是 lambdaQL 生成的 HQL。
<!-- region: SelectQueryTest.java#to-hql -->
```java
RenderedQuery q = qb.from(Order.class)
        .where(o -> o.getStatus() == 1 && o.getPrice() > 100)
        .toHql();
// constants written in the lambda are literals
assertEquals("select e0 from Order e0 where e0.status = 1 and e0.price > 100.0", q.hql());
assertEquals(Map.of(), q.parameters());

double minPrice = 100;
RenderedQuery q2 = qb.from(Order.class)
        .where(o -> o.getStatus() == 1 && o.getPrice() > minPrice)
        .toHql();
// captured values are bind parameters
assertEquals("select e0 from Order e0 where e0.status = 1 and e0.price > :p1", q2.hql());
assertEquals(Map.of("p1", 100.0), q2.parameters());
```

**各数据库的 SQL**

<!-- sql: to-hql -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>100.0
```

**各数据库的 SQL**

<!-- sql: to-hql#2 -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>?
```

`where()` 可以多次调用。条件用 `AND` 连接。
<!-- region: SelectQueryTest.java#where-many -->
```java
RenderedQuery q = qb.from(Order.class)
        .where(o -> o.getStatus() == 1 || o.getStatus() == 3)
        .where(o -> o.isActive())
        .toHql();
assertEquals("select e0 from Order e0 where (e0.status = 1 or e0.status = 3) and e0.active = true", q.hql());
```

**各数据库的 SQL**

<!-- sql: where-many -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where (o1_0.status=1 or o1_0.status=3) and o1_0.active=true
```

`where` 中不能使用方法引用。`where(Order::isActive)` 会以 `LQL-1009` 被拒绝，请写成 `where(o -> o.isActive())`（参阅 [9.9](09-java-notes.md#99-method-references)）。

**HQL 中的值。** 规则取决于值的来源。

| 值 | 在 HQL 中 | 示例 |
|---|---|---|
| 直接写在 Lambda 表达式中的常量：数字（包括小数）、字符串、`true`/`false`、`null`、enum 常量、编译期 `static final` 常量 | **字面量** | `o.getPrice() > 100` → `e0.price > 100.0` |
| 捕获值：局部变量、字段、方法参数 | **绑定参数** | `o.getPrice() > minPrice` → `e0.price > :p1` |
| 计算出的值：`new BigDecimal("1.5")`、`form.getText().trim()`、`LocalDateTime.of(...)` | **绑定参数** | `:p1` |

- 常量每次调用都相同。因此 SQL 语句不变，数据库在制定查询计划时可以看到值。
- 字符串常量中的单引号会被转义：`"O'Brien"` 变成 `'O''Brien'`。该字符串不是用户输入，而是编译进类中的常量，因此没有 SQL 注入风险。
- 捕获值可能来自用户，因此是绑定参数。每次调用只有值变化。
- 例外只有一个。与整数列比较的小数捕获值写成小数字面量（`2.5BD`）（参阅 [3.4](#34-comparisons) 的"整数列与小数值"）。只写入转换为数字的值，因此没有 SQL 注入风险。

enum 常量使用完整类名。
<!-- region: SelectQueryTest.java#enum -->
```java
RenderedQuery q = qb.from(Order.class).where(o -> o.getKind() == Order.Kind.RUSH).toHql();
assertEquals("select e0 from Order e0 where e0.kind = org.lambdaql.query.Order$Kind.RUSH", q.hql());
```

**各数据库的 SQL**

<!-- sql: enum -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.kind='RUSH'
```

示例代码：[`SelectQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java)（`to-list`、`to-hql`、`where-many`、`enum`）。

<a id="34-comparisons"></a>
## 3.4 比较运算

| Java | HQL | 说明 |
|---|---|---|
| `o.getStatus() == 1` | `e0.status = 1` | |
| `o.getStatus() != 1` | `e0.status <> 1` | |
| `o.getPrice() > p` | `e0.price > :p1` | `<`、`<=`、`>=` 同理 |
| `o.getUpdateAt().compareTo(since) > 0` | `e0.updateAt > :p1` | 日期和时间也用 `compareTo` 比较。不支持 `isAfter`、`isBefore`（`LQL-3008`） |
| `o.getDescription().compareTo("m") < 0` | `e0.description < 'm'` | `compareTo(..) op 0` 变成 `op` |

与数字的比较（像 `== 1` 或 `> 100.0` 这样一侧是基本类型）遵循 **SQL 语义**。
列为 NULL 的行不会返回。

可以使用 `+`、`-`、`*`、`/` 的简单算术。
请避免包含列的整数 `/`。它在 SQL 中计算，因此各数据库结果不同（MariaDB 中 `5/2 = 2.5`）。
捕获值之间的计算（下面的 `n / 2.0`）在查询前于 Java 中完成，因此没有这种差异。

下面示例中的 `cnt` 是 `Order` 的整数列（`Integer cnt`）。

**对列的算术运算与索引**。像 `o.getCnt() * 2 > n` 这样对列做算术运算时，无法使用索引。lambdaQL 以 `WARN` 级别记录 `LQL-5012`，在 `SargableMode.STRICT` 下分析失败（`LQL-3066`）。
移到值一侧时，请像 `o.getCnt() > n / 2.0` 这样用小数相除。整数除法 `n / 2` 会得到不同的行。如果乘的是负数，请反转比较方向。详情请参阅 [10.2](10-rules-and-limits.md#db-functions-on-a-column)。

**整数列与小数值**。把整数列与小数值比较时，lambdaQL 按写出的值进行比较。不做四舍五入，也不排除该值。

- 写在 Lambda 表达式中的常量保留为字面量。`o.getCnt() >= 2.5` 变成 `e0.cnt >= 2.5`。
- 捕获值也写成小数字面量。变成 `e0.cnt >= 2.5BD`，没有绑定参数。`BD` 是 HQL 的 `BigDecimal` 字面量后缀。
- 含有这类元素的 IN 列表，所有元素都写成小数字面量（`e0.cnt in (2.5BD, 3.0BD)`）。

这是把捕获值作为绑定参数发送这一原则的例外。Hibernate 7.4 在与整数列比较的参数上绑定小数时，即使指定 `BigDecimal` 类型也会拒绝（`QueryArgumentException`，实测）。

以前值会被截断为列类型，`2.5` 变成 `2`，返回错误的行。Hibernate 6.6 拒绝以整数列类型绑定的小数（`CoercionException`），并静默截断 IN 列表中的元素（在三种数据库上实测）。
没有采用改写为整数比较（`>= 3`）的方式。执行的 SQL 会与 Lambda 表达式不同，而且当实体映射（`Integer`）与数据库列类型（例如带小数的 `DECIMAL`）不同时，会得到错误的行。

结果在 H2、MariaDB 10.11、Oracle 23.26 上正确（实测）。三种数据库的 SQL 都像 `cnt>=2.5`、`cnt in (2.5,3.0)` 这样保持列不变，与小数比较。
MariaDB 的 `EXPLAIN` 显示索引范围扫描（`type=range`）。Oracle 中列上没有附加转换，是可以使用索引的形式。给出索引提示时是 `INDEX RANGE SCAN`；在 3000 行的表上没有提示时，与整数字面量（`cnt >= 99`）一样选择全表扫描（均为实测）。

<!-- region: SqlMeaningTest.java#whole-number -->
```java
// cnt is a whole number; 2.5 is compared as written (it is never cut to 2): a decimal literal
double half = 2.5;
RenderedQuery atLeast = qb.from(Order.class).where(o -> o.getCnt() >= half).toHql();
assertEquals("select e0 from Order e0 where e0.cnt >= 2.5BD", atLeast.hql());
assertEquals(Map.of(), atLeast.parameters());                                // written as a decimal literal
// a constant written in the lambda stays a literal
assertEquals("select e0 from Order e0 where e0.cnt >= 2.5", qb.from(Order.class).where(o -> o.getCnt() >= 2.5).toHql().hql());
// an IN list with such a value is written as decimal literals
List<Double> values = List.of(2.5, 3.0);
RenderedQuery in = qb.from(Order.class).where(o -> values.contains(o.getCnt().doubleValue())).toHql();
assertEquals("select e0 from Order e0 where e0.cnt in (2.5BD, 3.0BD)", in.hql());
```

**浮点数：** 对于 `float`/`double` 列，lambdaQL 不保证与 Java 逐位相同的结果。
捕获的 `NaN` 或 `Infinity` 值会在执行查询时被拒绝。

示例代码：[`SqlMeaningTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlMeaningTest.java)（`whole-number`）。

<a id="35-and-or-not"></a>
## 3.5 AND、OR、NOT

| Java | HQL |
|---|---|
| `a && b` | `a and b` |
| `a \|\| b` | `a or b` |
| `!a` | `not (a)` |
| 括号 | 按需保留 |

<!-- region: ManualJavaWhereTest.java#and-or-not -->
```java
double minPrice = 100;
SelectQuery<Order> both = qb.from(Order.class)
        .where(o -> (o.getStatus() == 1 || o.isActive()) && o.getPrice() > minPrice);
assertEquals("select e0 from Order e0 where (e0.status = 1 or e0.active = true) and e0.price > :p1",
        both.toHql().hql());
SelectQuery<Order> not = qb.from(Order.class)
        .where(o -> !(o.getStatus() == 1 && o.getPrice() > minPrice));
// the NOT is pushed into the comparisons: the same rows (status and price are never NULL)
assertEquals("select e0 from Order e0 where e0.status <> 1 or e0.price <= :p1", not.toHql().hql());
```

规则：

- 结果遵循 **SQL 三值逻辑**（TRUE / FALSE / UNKNOWN）。lambdaQL 已确认这种方式在 H2、MariaDB、Oracle 上返回相同的行。
- javac 和 lambdaQL 可能移动 `!` 的位置（德摩根定律，`!(x > y)` 变为 `x <= y`）。上例中的 `!(... && ...)` 变成 `e0.status <> 1 or e0.price <= :p1`。两列都不能为 NULL，因此结果与 `not (...)` 相同。
- lambdaQL **不简化条件**。写成 `o.getPrice() > p || o.getPrice() <= p` 时按原样发送。`price` 为 NULL 的行不会返回。这是正常的 SQL 行为。
- 请使用 `&&` 和 `||`。不做短路求值的运算符 `&`、`|`、`^` 会被拒绝。

**boolean 三元运算符。** 只允许三种简短形式。它们分别等同于 `AND`、`OR`、`NOT`。

| Java | 等价写法 |
|---|---|
| `c ? x : false` | `c && x` |
| `c ? true : x` | `c \|\| x` |
| `c ? false : true` | `!c` |

只用捕获值和常量选择值的三元运算符（`o.getStatus() == (flag ? 1 : 2)`）在执行查询时于 Java 中计算，并作为绑定参数传递。
用实体值选择值的三元运算符（`o.getStatus() == (o.isActive() ? 1 : 2)`）变成 SQL `case when … end`（参阅 [3.5.1](#351-values-chosen-by-a-condition)）。
其他 boolean 三元运算符（`c ? x : y`）会被拒绝（`LQL-2021`）。

<!-- region: ManualJavaWhereTest.java#ternary-outside -->
```java
// a ternary of captured values and constants: computed in Java, bound as :p1
SelectQuery<Order> query = qb.from(Order.class).where(o -> o.getStatus() == (flag ? 1 : 2));
assertEquals("select e0 from Order e0 where e0.status = :p1", query.toHql().hql());
// a ternary that reads the entity becomes case when
SelectQuery<Order> byEntity = qb.from(Order.class).where(o -> o.getStatus() == (o.isActive() ? 1 : 2));
assertEquals("select e0 from Order e0 where e0.status = case when e0.active = true then 1 else 2 end",
        byEntity.toHql().hql());
```

示例代码：[`ManualJavaWhereTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaWhereTest.java)（`and-or-not`、`ternary-outside`）。

<a id="351-values-chosen-by-a-condition"></a>
### 3.5.1 按条件选择的值（`case when`）

用实体值选择值的三元运算符会变成一个 SQL `case when … end` 表达式。
条件中可以混用 `&&`、`||`、`!`。连续使用三元运算符（`a ? x : b ? y : z`）时，变成一个带多个分支的 `case`。

`case` 可以用于 `where` 的比较值、`select` 的查询值、`groupBy` 键、排序键、`update` 的赋值。

<!-- region: CaseWhenTest.java#case-where -->
```java
SelectQuery<Order> q = qb.from(Order.class).where(o -> o.getStatus() == (o.isActive() ? 1 : 2));
assertEquals("select e0 from Order e0 where e0.status = case when e0.active = true then 1 else 2 end", q.toHql().hql());
```

<!-- region: CaseWhenTest.java#case-select -->
```java
ValueQuery<String> q = qb.from(Order.class).select(o -> o.getPrice() > 100 ? "big" : "small");
assertEquals("select case when e0.price > 100.0 then 'big' else 'small' end from Order e0", q.toHql().hql());
```

<!-- region: CaseWhenTest.java#case-group -->
```java
// orders per price band: the case is the group key and the selected value
ValueQuery<String> q = qb.from(Order.class)
        .groupBy(o -> o.getPrice() > 100 ? "big" : "small")
        .select(o -> o.getPrice() > 100 ? "big" : "small");
assertEquals("select case when e0.price > 100.0 then 'big' else 'small' end from Order e0"
        + " group by case when e0.price > 100.0 then 'big' else 'small' end", q.toHql().hql());
```

<!-- region: CaseWhenTest.java#case-sort -->
```java
// active orders first, then by price
OrderedSelectQuery<Order> q = qb.from(Order.class).orderBy(o -> asc(o.isActive() ? 0 : 1).asc(o.getPrice())).limit(10);
assertEquals("select e0 from Order e0 order by case when e0.active = true then 0 else 1 end asc, e0.price asc, e0.id asc",
        q.toHql().hql());
```

<!-- region: CaseWhenTest.java#case-set -->
```java
// 10 % off the orders above 100
ExecutableUpdate<Order> u = qb.update(Order.class).set(o -> o.setPrice(o.getPrice() > 100 ? o.getPrice() * 0.9 : o.getPrice())).all();
assertEquals("update Order e0 set e0.price = case when e0.price > 100.0 then (e0.price * 0.9) else e0.price end", u.toHql().hql());
```

规则：

- `case` 从上到下依次检查分支。因此第二个及之后分支的条件就是 Lambda 表达式中写的条件本身。
- 在 `where` 中比较的 `case` 按行计算，因此数据库无法使用其中列的索引。此时 lambdaQL 以 `WARN` 级别记录 `LQL-5013`。
- 按条件选择排序函数链本身的形式（`flag ? desc(o.getPrice()) : asc(o.getId())`）会被拒绝（`LQL-2023`）。请把条件写在键值内部。

示例代码：[`CaseWhenTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/CaseWhenTest.java)（`case-where`、`case-select`、`case-group`、`case-sort`、`case-set`）。

<a id="36-null-rules"></a>
## 3.6 NULL 规则

这是最重要的部分。有两条简单的原则。

1. **实体值按 SQL 语义比较**。对象之间的 `==`、`equals`、`Objects.equals`、`!=` 变成 `=` 和 `<>`。
   值为 NULL 的行两边都不匹配。
   结果可能与 Java 不同时，lambdaQL 以 `DEBUG` 级别记录 `LQL-5009`。
2. **捕获值先在 Java 中计算，比较同样遵循 SQL 语义。** 捕获值 `null` 绑定为 NULL，因此 `= :p1` 和 `<> :p1` 不匹配任何行。
   此时 lambdaQL 在执行时以 `WARN` 级别记录 `LQL-4045`。要查找 NULL，请写 `x == null`（变成 `is null`）。
   仅由捕获值组成的表达式（`p.trim()`、转换为其他基本类型的 `min.intValue()` 等）是在 Java 中计算的值，因此接收对象为 `null` 时会像 Java 一样抛出异常（`LQL-4012`）。
   拆箱为自身基本类型（`min.doubleValue()`）与 javac 的自动拆箱是相同的字节 opcode，因此不执行，而是直接绑定装箱值（参阅 [3.6.4](#364-captured-null-values)）。

lambdaQL **只处理显式写出的 null**（`== null`、`!= null`）。
它不会代为添加隐藏的 null 检查。

<a id="361-rule-table"></a>
### 3.6.1 规则表

| # | Java 写法 | HQL | NULL 行为 |
|---|---|---|---|
| R1 | 一侧是基本类型：`o.getStatus() == 1`、`o.getPrice() > p` | `e0.status = 1`、`e0.price > :p1` | SQL 语义。列为 NULL 时不返回该行 |
| R2 | 两侧都是对象：`o.getCnt() == wanted`（`Integer == Integer`）、`String == String`、enum | `e0.cnt = :p1` | **值比较。** 列为 NULL 时不返回该行。捕获值 `null`：没有匹配的行（`LQL-4045`） |
| R2 | `o.getCnt() != wanted`（对象） | `e0.cnt <> :p1` | SQL 语义。列为 NULL 时**不**返回该行（Java 会返回）。捕获值 `null`：没有匹配的行（`LQL-4045`） |
| R2 | 两个实体值：`o.getDescription() == o.getCustName()` | `e0.description = e0.custName` | SQL 语义。任意一侧为 NULL 时不返回该行（NULL 与 NULL 也是如此） |
| R3 | `x == null`、`x != null` | `x is null`、`x is not null` | |
| R4 | `o.getDescription().equals(p)` | `e0.description = :p1` | SQL 语义。列为 NULL 时不返回该行。`p == null` 时没有匹配的行（`LQL-4045`） |
| R5 | `Objects.equals(a, b)`、`"x".equals(a)`、`Boolean.TRUE.equals(a)` | 与 R2 相同：`a = b`、`a = :p1` | 与 R2 相同 |
| R9 | 捕获值 | 绑定参数 | 参阅 [3.6.4](#364-captured-null-values) |

哪些 Java 类型属于 R1、哪些属于 R2，在 [9. Java 说明](09-java-notes.md#93-equality-of-boxed-values) 中说明。

<a id="362--on-objects-compares-values"></a>
### 3.6.2 对象上的 `==` 比较值

在 Java 中，两个 `Integer`、`Long`、`String` 对象上的 `==` 检查**对象同一性**。
在 lambdaQL 中它是**值比较**。大于 127 的 `Long` 值或 `new String(...)` 也一样。
Lambda 表达式是查询，因此 Java 的内存规则不适用。

<!-- region: ManualJavaWhereTest.java#boxed-equals -->
```java
// cnt is an Integer column; Integer == Integer compares values, not objects
Integer wanted = 3;
SelectQuery<Order> three = qb.from(Order.class).where(o -> o.getCnt() == wanted);
assertEquals("select e0 from Order e0 where e0.cnt = :p1", three.toHql().hql());
// a captured null is bound as NULL: "= NULL" matches no row (WARN LQL-4045)
Integer none = null;
SelectQuery<Order> noRow = qb.from(Order.class).where(o -> o.getCnt() == none);
assertEquals("select e0 from Order e0 where e0.cnt = :p1", noRow.toHql().hql());
// to find NULL, write the null check
SelectQuery<Order> nullCnt = qb.from(Order.class).where(o -> o.getCnt() == null);
assertEquals("select e0 from Order e0 where e0.cnt is null", nullCnt.toHql().hql());
```

要查找 cnt 为 NULL 的行，请像上例最后一个查询那样写 null 检查。

列没有被函数包裹，因此数据库可以使用索引。

lambdaQL 在执行查询时绑定捕获值。`null` 绑定为 NULL，没有可匹配的行，因此 lambdaQL 以 `WARN` 级别记录 `LQL-4045`。
<!-- region: SelectQueryTest.java#null-safe -->
```java
// the SQL meaning: = NULL matches no row; lambdaQL logs LQL-4045 at WARN when it runs with a null
String d = null;
RenderedQuery q = qb.from(Order.class).where(o -> Objects.equals(o.getDescription(), d)).toHql();
assertEquals("select e0 from Order e0 where e0.description = :p1", q.hql());
// to find NULL, write the null check
RenderedQuery q3 = qb.from(Order.class).where(o -> o.getDescription() == null).toHql();
assertEquals("select e0 from Order e0 where e0.description is null", q3.hql());

String d2 = "a";
RenderedQuery q2 = qb.from(Order.class).where(o -> Objects.equals(o.getDescription(), d2)).toHql();
assertEquals("select e0 from Order e0 where e0.description = :p1", q2.hql());
```

**各数据库的 SQL**

<!-- sql: null-safe -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.description=?
```

**各数据库的 SQL**

<!-- sql: null-safe#2 -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.description=?
```

> [!TIP]
> **IDE 警告。** IntelliJ 会对这种写法显示 "Number objects compared using `==`"。
> 在 lambdaQL 的 Lambda 表达式中这是预期的行为。可以在方法上声明 `@SuppressWarnings("NumberEquality")` 来消除警告。

**不能为 NULL 的列。** id、基本类型列、`nullable = false` 列不能为 NULL。
这类列的 `is null` 总是为假，因此 lambdaQL 把该检查折叠为常量并移除。

- `o.getId() == null` 变成 `1=0`（没有行）。
- `o.getId() != null && o.getId() == id` 变成 `e0.id = :p1`。null 检查消失，因此数据库可以使用索引。

针对 `Long?` id 的 Kotlin `it.id == id` 也编译为相同的字节 opcode，因此 Kotlin 写法同样变成简单的 `e0.id = :p1`。

此规则只在路径中**没有关联步骤时**适用。存在没有客户的订单，因此 `o.getCustomer().getId()` 可能为 NULL，该路径上的 null 检查会保留。

<!-- region: SelectQueryTest.java#never-null -->
```java
// the id can never be NULL, so the null check is dropped (this is what Kotlin's it.id == id compiles to)
assertEquals("select e0 from Order e0 where e0.id = :p1",
        qb.from(Order.class).where(o -> o.getId() != null && o.getId() == id).toHql().hql());
assertEquals("select e0 from Order e0 where 1=0", qb.from(Order.class).where(o -> o.getId() == null).toHql().hql());
// through an association the row may have no customer, so the check stays
assertEquals("select e0 from Order e0 where e0.customer.id is null",
        qb.from(Order.class).where(o -> Objects.equals(o.getCustomer().getId(), null)).toHql().hql());
```

<a id="363-write-your-null-checks-yourself"></a>
### 3.6.3 自己编写 null 检查

<!-- region: ManualJavaWhereTest.java#null-check -->
```java
// only rows where cnt is not NULL and not 1
SelectQuery<Order> notOne = qb.from(Order.class).where(o -> o.getCnt() != 1);
assertEquals("select e0 from Order e0 where e0.cnt <> 1", notOne.toHql().hql());
// rows where cnt is NULL too
SelectQuery<Order> notOneOrNull = qb.from(Order.class).where(o -> o.getCnt() == null || o.getCnt() != 1);
assertEquals("select e0 from Order e0 where e0.cnt is null or e0.cnt <> 1", notOneOrNull.toHql().hql());
```

`equals()` 和 `Objects.equals()` 变成相同的比较（`=`）。取反的 `!Objects.equals(...)` 变成 `<>`，值为 NULL 的行不会返回。

<!-- region: ManualJavaWhereTest.java#equals-forms -->
```java
String text = "a";
assertEquals("select e0 from Order e0 where e0.description = :p1",
        qb.from(Order.class).where(o -> o.getDescription().equals(text)).toHql().hql());
// the same comparison; when text is null, no row matches
assertEquals("select e0 from Order e0 where e0.description = :p1",
        qb.from(Order.class).where(o -> Objects.equals(o.getDescription(), text)).toHql().hql());
// rows where cnt is NULL are not returned (the SQL meaning)
SelectQuery<Order> notOne = qb.from(Order.class).where(o -> !Objects.equals(o.getCnt(), 1));
assertEquals("select e0 from Order e0 where e0.cnt <> 1", notOne.toHql().hql());
```

**实体值的 SQL 语义**。两个实体值之间的比较，以及实体值与值的 `!=` 比较，都遵循 SQL。值为 NULL 的行既不匹配 `=`，也不匹配 `<>`。

在 Java 中，`!text.equals(null)` 为 true，`Objects.equals(null, null)` 也为 true，因此 Java 会返回这些行。
可能出现这种差异时，lambdaQL 对每个条件以 `DEBUG` 级别记录一次 `LQL-5009`。要保留 NULL 行，请在 Lambda 表达式中直接写 null 检查。

H2、MariaDB、Oracle。行是 description/custName 对 `a/a`、`a/b`、`null/a`、`null/null`、`b/null`，`text` 是 `"a"`：

<!-- region: SqlMeaningTest.java#sql-meaning -->
```java
// two entity values: = and <> as in SQL; a row with a NULL on either side matches neither
SelectQuery<Order> same = qb.from(Order.class).where(o -> Objects.equals(o.getDescription(), o.getCustName()));
assertEquals("select e0 from Order e0 where e0.description = e0.custName", same.toHql().hql());
assertEquals(List.of("a/a"), pairs(same.toList()));                    // null/null is not "equal" in SQL
SelectQuery<Order> different = qb.from(Order.class).where(o -> !Objects.equals(o.getDescription(), o.getCustName()));
assertEquals(List.of("a/b"), pairs(different.toList()));               // rows with a NULL are left out
// an entity value and a value: a NULL column does not match <>
SelectQuery<Order> notA = qb.from(Order.class).where(o -> !text.equals(o.getDescription()));
assertEquals("select e0 from Order e0 where e0.description <> :p1", notA.toHql().hql());
assertEquals(List.of("b/null"), pairs(notA.toList()));
// to keep the NULL rows, say so in the lambda
SelectQuery<Order> notAOrNull = qb.from(Order.class).where(o -> o.getDescription() == null || !text.equals(o.getDescription()));
assertEquals(List.of("b/null", "null/a", "null/null"), pairs(notAOrNull.toList()));
// a NULL Boolean is not false
assertEquals(List.of("f"), qb.from(Coded.class).where(c -> !c.getVip()).toList().stream().map(Coded::getCode).toList());
```

<a id="364-captured-null-values"></a>
### 3.6.4 null 捕获值

| 写法 | 捕获值为 `null` 时 |
|---|---|
| 与捕获值的 `==`、`!=`、`Objects.equals(...)`、`o.getDescription().equals(p)` | 执行：绑定 NULL 的 `= :p1` / `<> :p1`，因此**没有**匹配的行。`WARN` 级别 `LQL-4045` |
| `p.equals(o.getDescription())`（`p` 是接收对象） | 同上：`e0.description = :p1`（NULL），**没有**匹配的行。`WARN` 级别 `LQL-4045`。不抛出异常 |
| Java 会拆箱值的比较：`o.getPrice() > p`（`Double p`）、`i.getQty() == q`（`int qty`、`Integer q`） | 执行：`e0.price > :p1` 绑定 NULL，**没有**匹配的行。`DEBUG` 级别 `LQL-4045`。拆箱为自身基本类型（`p.doubleValue()`）不执行 |
| 转换为其他基本类型的拆箱（`p.intValue()`、`Double p`）和方法调用（`p.trim()`） | 执行查询时抛出**异常**（`LQL-4012`）。因为值在 Java 中计算 |

**不推荐 `p.equals(o.getDescription())` 写法。** 在 SQL 中它与 `o.getDescription().equals(p)` 是相同的比较（`e0.description = :p1`），因此即使 `p` 为 `null` 也不会抛出异常。
但作为 Java 代码阅读时，`null.equals(...)` 看起来像 NullPointerException，因此请写成实体值在前的 `o.getDescription().equals(p)`。

原因：Lambda 表达式中的比较遵循 SQL 语义。在 SQL 中，与 NULL 的 `=`、`<>` 不会为真。
捕获值是否为 null 只有在执行查询时才能知道，因此 lambdaQL 在执行时记录警告。

仅由捕获值组成的表达式是在查询前于 Java 中计算的值。`p.trim()` 这类方法调用以及转换为其他基本类型的拆箱（`p.intValue()`、`Double p`）属于此类，为 `null` 时会像 Java 一样抛出异常（`LQL-4012`）。
但拆箱为自身基本类型（`Double.doubleValue()`、`Integer.intValue()`、`Long.longValue()` 等，包括其后的拓宽转换）不计算。
javac 的自动拆箱与手写的调用是相同的字节 opcode，因此 lambdaQL 把它视为自动拆箱并绑定装箱值。为 `null` 时没有匹配的行，并以 `DEBUG` 级别记录 `LQL-4045`。
要查找 NULL 行，请写 `x == null`。

**用 null 守卫省略过滤。** 对同一个捕获值做 null 检查时，值为 null 时该条件会作为无过滤而消失。
<!-- region: SelectQueryTest.java#captured-null-guard -->
```java
Double min = null;                                       // for example an empty search field
RenderedQuery q = qb.from(Order.class).where(o -> min == null || o.getPrice() > min).toHql();
// the guard is decided in Java when the query runs: no filter at all
assertEquals("select e0 from Order e0", q.hql());

Double min2 = 100.0;
RenderedQuery q2 = qb.from(Order.class).where(o -> min2 == null || o.getPrice() > min2).toHql();
assertEquals("select e0 from Order e0 where e0.price > :p1", q2.hql());
assertEquals(Map.of("p1", 100.0), q2.parameters());
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

- 只使用捕获值和常量的部分在执行查询时于 Java 中决定。像 Java 一样从左到右、以短路求值（short-circuit）处理。
- `min == null` 时守卫为真，因此整个条件为真。没有过滤。比较 `o.getPrice() > min` 不写入 HQL（Java 也不执行这个比较）。
- `min = 100.0` 时守卫为假，因此只剩下 `e0.price > :p1`。
- Lambda 表达式仍只分析一次。随值变化的只有 HQL 文本。
- 这种情况下守卫的位置不影响结果。`min == null` 时 `o.getPrice() > min || min == null` 同样没有过滤。因为比较部分的拆箱不在 Java 中执行（在 Java 中会是 NullPointerException）。
- 对捕获值调用方法的比较（`o.getDescription().equals(p.trim())`）则不同。守卫在后面时会先计算 `p.trim()`，因此 `p == null` 时抛出异常（`LQL-4012`）。
- 在 `&&` 中守卫的作用相反。`min == null` 时 `min != null && o.getPrice() > min` 不返回行（`where 1=0`）。

<a id="365-converted-attributes-convert"></a>
### 3.6.5 转换属性（`@Convert`）

带有 JPA `AttributeConverter` 的属性按 Java 值比较。Hibernate（7.4，之前的 6.6 也是）会转换与这类属性比较的字面量，也会转换绑定参数。

因此即使用 Java 值写条件，也能找到存储的行（已在 H2、MariaDB、Oracle 上确认）。

<!-- region: ConvertTest.java#convert -->
```java
// code is stored reversed ("abc" as "cba"); vip is stored as 'Y'/'N' (YesNoConverter)
SelectQuery<Coded> literal = qb.from(Coded.class).where(c -> c.getCode().equals("abc"));
assertEquals("select e0 from Coded e0 where e0.code = 'abc'", literal.toHql().hql());
assertEquals(List.of("abc"), codes(literal.toList()));       // Hibernate converts the literal
```

示例代码：[`ManualJavaWhereTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaWhereTest.java)（`boxed-equals`、`null-check`、`equals-forms`）、[`SelectQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java)（`null-safe`、`never-null`、`captured-null-guard`）、[`SqlMeaningTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlMeaningTest.java)（`sql-meaning`）、[`ConvertTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ConvertTest.java)（`convert`）。

<a id="37-boolean-columns"></a>
## 3.7 boolean 列

示例实体中的 `active` 是基本类型 `boolean`（getter `isActive()`）。
出现 `getActive()` 的行是 nullable `Boolean` 列的情况。

| Java | HQL | NULL 行 |
|---|---|---|
| `o.isActive()` | `e0.active = true` | –（基本类型 `boolean` 不能为 NULL） |
| `!o.isActive()` | `e0.active = false` | –（基本类型 `boolean` 不能为 NULL） |
| `Boolean.TRUE.equals(o.getActive())`（nullable `Boolean`） | `e0.active = :p1`（`:p1` 是 `true`） | 不返回 |
| `!Boolean.TRUE.equals(o.getActive())` | `e0.active <> :p1` | **不返回**（SQL 语义。Java 会返回） |
| `!o.getActive()`（nullable `Boolean`） | `e0.active = false` | **不返回**（SQL 语义：NULL 不是 false） |

<!-- region: SelectQueryTest.java#boolean -->
```java
assertEquals("select e0 from Order e0 where e0.active = true",
        qb.from(Order.class).where(o -> o.isActive()).toHql().hql());
assertEquals("select e0 from Order e0 where e0.active = false",       // primitive boolean: never NULL
        qb.from(Order.class).where(o -> !o.isActive()).toHql().hql());
```

**各数据库的 SQL**

<!-- sql: boolean -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.active=true
```

**各数据库的 SQL**

<!-- sql: boolean#2 -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.active=false
```

Hibernate 7.4 在三种数据库的 SQL 中都写 `true` / `false`。Hibernate 6.6 在 MariaDB 和 Oracle 上写 `1` / `0`。

说明：

- 对 nullable `Boolean` 列的 `!o.getActive()` 在 Java 中是拆箱调用（为 NULL 时 NullPointerException）。lambdaQL 使用 SQL 语义。NULL 行不会返回。
- lambdaQL 不使用 `is not true` 或 `coalesce(active, false)`。它们在 Oracle 上会失败（ORA-00932）。
- 更大条件的取反总是写成 `not (...)`。lambdaQL 不把条件包装成 `(...) = false` 或 `(...) is null`。因为 Oracle 23 在 `WHERE` 中会错误地求值这些形式。

**boolean 列与条件的比较**（`o.isActive() == (o.getStatus() > s)`）中，条件一侧变成 `case when … then true else false end`。
该 `case` 按行计算，因此数据库无法使用 `status` 索引（`WARN` 级别 `LQL-5013`）。
需要索引时，请用 `&&` 和 `||` 把两种情况分开写。这种形式会写成 `AND`/`OR`。

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

示例代码：[`SelectQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java)（`boolean`）、[`ManualJavaWhereTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaWhereTest.java)（`boolean-compare`）。

<a id="38-strings"></a>
## 3.8 字符串

- 字符串上的 `equals`、`Objects.equals`、`==` 使用**数据库排序规则（collation）**。
  - MariaDB 的 `_ci` 排序规则忽略大小写和末尾空格。
  - Oracle 把 `''`（空字符串）存为 NULL。因此在 Oracle 上 `"".equals(o.getDescription())` 的行为与 Java 不同。
- 两个字符串上的 `==` 是值比较，不是同一性比较。请参阅 [9.4](09-java-notes.md#94-strings)。
- Lambda 表达式中的字符串常量写成字面量。单引号会被转义（`''`）。

<!-- region: SelectQueryTest.java#string-literal -->
```java
RenderedQuery q = qb.from(Order.class).where(o -> o.getDescription().equals("O'Brien")).toHql();
assertEquals("select e0 from Order e0 where e0.description = 'O''Brien'", q.hql());
```

**各数据库的 SQL**

<!-- sql: string-literal -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.description='O''Brien'
```

**`startsWith`、`endsWith`、`contains`** 变成 `like`。检索词中的 `%` 和 `_` 不是通配符，而是按字面查找。

<!-- region: LikeTest.java#like -->
```java
// % and _ in the text are not wildcards: only "50%_off sale" matches, not "50x off"
SelectQuery<Order> q = qb.from(Order.class).where(o -> o.getDescription().startsWith(p));
assertEquals("select e0 from Order e0 where e0.description like :p1 escape '!'", q.toHql().hql());
assertEquals("50!%!_%", q.toHql().parameters().get("p1"));
// a constant is written in the HQL
SelectQuery<Order> ends = qb.from(Order.class).where(o -> o.getDescription().endsWith("all"));
assertEquals("select e0 from Order e0 where e0.description like '%all' escape '!'", ends.toHql().hql());
```

- 常量直接写入 HQL，捕获值成为绑定参数。
- `endsWith` 和 `contains` 的模式以 `%` 开头，因此无法使用列的普通索引。
- MariaDB 和 MySQL 在默认排序规则下 `like` 不区分大小写。因此结果可能与 Java 的 `startsWith` 不同。
- 用作检索词的捕获值为 `null` 时报错（`LQL-4047`）。
- 不支持 `trim()`、`toUpperCase()` 等其他字符串方法（`LQL-3008`）。
- Lambda 表达式中的字符串 `+` 会被拒绝（`LQL-2007`）。只拼接捕获值时也一样。请在 Lambda 表达式之前构造字符串。

<!-- region: ManualJavaWhereTest.java#rejected-forms -->
```java
// a String method on an entity value other than startsWith, endsWith and contains
assertEquals("LQL-3008", assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(o -> o.getDescription().trim().equals("a"))).code());
// string concatenation inside the lambda, even of captured values only
assertEquals("LQL-2007", assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(o -> o.getDescription().equals("A" + code))).code());
// an entity compared with an entity: compare the ids
assertEquals("LQL-3011", assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(o -> o.getCustomer() == c)).code());
assertEquals("select e0 from Order e0 where e0.customer.id = :p1",
        qb.from(Order.class).where(o -> o.getCustomer().getId() == c.getId()).toHql().hql());
// operators without short-circuit
assertEquals("LQL-2013", assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(o -> o.isActive() & o.getStatus() == 1)).code());
```

示例代码：[`SelectQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java)（`string-literal`）、[`ManualJavaWhereTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaWhereTest.java)（`rejected-forms`）、[`LikeTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/LikeTest.java)（`like`）。

<a id="39-in-with-a-captured-collection"></a>
## 3.9 使用捕获集合的 IN

请在 Lambda 表达式**外部**创建集合，并调用该集合的 `contains`。集合成为一个绑定参数。
示例实体的 status 是 `short`：

<!-- region: SelectQueryTest.java#in -->
```java
List<Short> statuses = List.of((short) 2, (short) 3);
RenderedQuery q = qb.from(Order.class).where(o -> statuses.contains(o.getStatus())).toHql();
assertEquals("select e0 from Order e0 where e0.status in (:p1)", q.hql());
```

**各数据库的 SQL**

<!-- sql: in -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status in (?,?)
```

`!contains` 变成 `not in`。可以为 NULL 的列也是简单的 `not in`（SQL 语义）。
值为 NULL 的行不匹配 `not in`，lambdaQL 以 `DEBUG` 级别记录 `LQL-5009`。Java 的 `!contains` 对 NULL 值为 true，因此要保留这些行，请像 `o.getDescription() == null || !descs.contains(o.getDescription())` 这样写 null 检查。
<!-- region: SelectQueryTest.java#not-in -->
```java
List<Long> ids = List.of(1L, 2L);
// the id can never be NULL: a plain NOT IN
assertEquals("select e0 from Order e0 where e0.id not in (:p1)",
        qb.from(Order.class).where(o -> !ids.contains(o.getId())).toHql().hql());
// a column that can be NULL: the SQL meaning, a NULL value is not "not in" the list (DEBUG LQL-5009);
// to keep those rows, write o.getDescription() == null || !descs.contains(o.getDescription())
List<String> descs = Arrays.asList("a", "b");
assertEquals("select e0 from Order e0 where e0.description not in (:p1)",
        qb.from(Order.class).where(o -> !descs.contains(o.getDescription())).toHql().hql());
```

**各数据库的 SQL**

<!-- sql: not-in -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.id not in (?,?)
```

**各数据库的 SQL**

<!-- sql: not-in#2 -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.description not in (?,?)
```

| 情况 | 结果 |
|---|---|
| 接收对象是捕获的 `Collection`、`List`、`Set` | 支持 |
| 集合中有 `null` 元素 | 执行查询时抛出异常 |
| 空集合 | `contains` 为 FALSE，`!contains` 为 TRUE（Hibernate 写 `1=0` / `1=1`） |
| 1,500 个元素 | 在 H2、MariaDB、Oracle 23ai 上以一条语句运行（旧版 Oracle 有 1,000 个的限制。旧版未确认） |
| 接收对象是实体集合：`c.getOrders().contains(order)` | **被拒绝**。请使用 `exists` 子查询（[5. 子查询](05-subquery.md)） |
| 在 Lambda 表达式中创建的集合：`List.of(1, 2).contains(o.getStatus())` | **被拒绝**。请在外部创建并捕获 |

示例代码：[`SelectQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java)（`in`、`not-in`）。

<a id="310-association-paths"></a>
## 3.10 关联路径

lambdaQL **不调用** `o.getCustomer().getName()`。该表达式指代"所连接客户的 `name` 列"。
lambdaQL 通过 JPA Metamodel 确认每一步。

| 路径步骤 | HQL | 示例 |
|---|---|---|
| to-one 关联（`@ManyToOne`、`@OneToOne`） | 带别名的 `join` 或 `left join`（见下文） | `o.getCustomer().getName()` -> `join e0.customer j1` ... `j1.name` |
| 嵌入（`@Embedded`） | 同一张表，没有连接。值对象整体的相等比较按列展开（见下文） | `o.getAddress().getCity()` -> `e0.address.city` |
| 以目标的 `@Id` 结尾的路径 | 外键列，没有连接 | `o.getCustomer().getId()` -> `e0.customer.id` |
| 把关联本身与 `null` 比较 | 外键列，没有连接 | `o.getCustomer() == null` -> `e0.customer is null` |
| 集合（`@OneToMany`、`@ManyToMany`） | **被拒绝** | `c.getOrders()...` -> 使用 `exists` 子查询 |

**值对象整体的比较。** 对嵌入值对象（`@Embedded`）整体做相等比较时，lambdaQL 把它展开为每列一个比较。
`o.getAddress().equals(addr)`、`Objects.equals(o.getAddress(), addr)`、`addr.equals(o.getAddress())` 都变成 `e0.address.city = :p1 and e0.address.zip = :p2`。

- 取反（`!o.getAddress().equals(addr)`）是 `e0.address.city <> :p1 or e0.address.zip <> :p2`。
- `o.getAddress() == null` 是 `e0.address.city is null and e0.address.zip is null`，`!= null` 是 `e0.address.city is not null or e0.address.zip is not null`。
- 每列遵循 SQL 语义。NULL 列既不匹配 `=`，也不匹配 `<>`。例如 (Seoul, NULL) 行既不匹配相等比较，也不匹配取反（在三种数据库上实测）。
- 捕获值对象的每个属性在执行查询时读取并绑定。捕获的对象为 `null` 时，所有列都绑定 NULL，因此没有匹配的行，并对每列以 `WARN` 级别记录 `LQL-4045`。
- 嵌套的值对象也会展开。
- 大小比较（`<` 等）以及与其他类型的比较仍是 `LQL-3012` 错误。请比较字段。

<!-- region: MappingRulesTest.java#embedded-compare -->
```java
// address is @Embedded (city, zip): comparing the whole value object compares each column
Address addr = new Address("Seoul", "04524");
SelectQuery<Order> same = qb.from(Order.class).where(o -> o.getAddress().equals(addr));
assertEquals("select e0 from Order e0 where e0.address.city = :p1 and e0.address.zip = :p2", same.toHql().hql());
assertEquals(Map.of("p1", "Seoul", "p2", "04524"), same.toHql().parameters());
// Objects.equals and addr.equals(...) are the same comparison
assertEquals(same.toHql().hql(), qb.from(Order.class).where(o -> Objects.equals(o.getAddress(), addr)).toHql().hql());
// null checks each column: "no address" means every column is NULL
SelectQuery<Order> none = qb.from(Order.class).where(o -> o.getAddress() == null);
assertEquals("select e0 from Order e0 where e0.address.city is null and e0.address.zip is null", none.toHql().hql());
SelectQuery<Order> some = qb.from(Order.class).where(o -> o.getAddress() != null);
assertEquals("select e0 from Order e0 where e0.address.city is not null or e0.address.zip is not null",
        some.toHql().hql());
// each column follows the SQL meaning: a NULL column matches neither = nor <>
SelectQuery<Order> other = qb.from(Order.class).where(o -> !o.getAddress().equals(addr));
assertEquals("select e0 from Order e0 where e0.address.city <> :p1 or e0.address.zip <> :p2", other.toHql().hql());
```

在 `where` 中，关联路径的连接类型由整个条件决定。
如果没有客户的订单可能满足条件，则是 `left join`；不可能满足时是 **inner join**。
结果与在订单列表上执行同一个 Lambda 表达式的结果相同。
经过 `o.getCustomer().getName()` 的比较只对有客户的订单为真。下面的条件只有这一个比较，因此是 inner join。
<!-- region: SelectQueryTest.java#inner-join -->
```java
String name = "Kim";
RenderedQuery q = qb.from(Order.class).where(o -> o.getCustomer().getName().equals(name)).toHql();
assertEquals("select e0 from Order e0 join e0.customer j1 where j1.name = :p1", q.hql());
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

- **没有客户的订单**不在结果中。因为它们不可能满足条件。
- lambdaQL 不会代为添加 null 检查。不需要在路径前写 `o.getCustomer() != null &&`。

**null 守卫会保留没有客户的订单。** `o.getCustomer() == null || ...` 中没有客户的订单也满足条件，因此变成 `left join`。
`o.getCustomer() != null && ...` 中没有客户的订单不可能满足条件，因此是 inner join。
<!-- region: SelectQueryTest.java#null-guard -->
```java
String name = "Kim";
// the null guard keeps orders without a customer, as in Java: a left join
RenderedQuery q = qb.from(Order.class)
        .where(o -> o.getCustomer() == null || o.getCustomer().getName().equals(name))
        .toHql();
assertEquals("select e0 from Order e0 left join e0.customer j1 where e0.customer is null or j1.name = :p1", q.hql());

// no order without a customer can match: an inner join
RenderedQuery q2 = qb.from(Order.class)
        .where(o -> o.getCustomer() != null && o.getCustomer().getName().equals(name))
        .toHql();
assertEquals("select e0 from Order e0 join e0.customer j1 where e0.customer is not null and j1.name = :p1", q2.hql());
```

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

Hibernate 把 `e0.customer is null` 写成 `c1_0.id is null`。

- `||` 的其他部分也一样。`o.getStatus() == 1 || o.getCustomer().getName().equals(name)` 在 status 为 1 时即使没有客户也满足，因此是 `left join`，这些订单也会返回。
- 在 NULL 列上可能为真的比较（`is null`、`coalesce`）经过路径时，lambdaQL 只为该比较添加 `e0.customer is not null`。因此这个比较也只对有客户的订单为真。
  `o.getCustomer() == null || o.getCustomer().getName() == null` 变成 `left join ... where e0.customer is null or (e0.customer is not null and j1.name is null)`。
- 与值的比较遵循 SQL 语义。`!o.getCustomer().getName().equals(name)` 不返回没有客户的订单。
- 以 `optional = false` 映射的必需关联总是 inner join。

要查找**没有**客户的订单，请比较关联本身。不连接，直接检查外键列。
<!-- region: SelectQueryTest.java#no-association -->
```java
RenderedQuery q = qb.from(Order.class).where(o -> o.getCustomer() == null).toHql();
assertEquals("select e0 from Order e0 where e0.customer is null", q.hql());
```

**各数据库的 SQL**

<!-- sql: no-association -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id is null
```

以关联实体的 `@Id` 结尾的路径是外键列。不需要连接。
<!-- region: SelectQueryTest.java#fk-path -->
```java
RenderedQuery q = qb.from(Order.class).where(o -> o.getCustomer().getId() == 1).toHql();
assertEquals("select e0 from Order e0 where e0.customer.id = 1", q.hql());
```

**各数据库的 SQL**

<!-- sql: fk-path -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id=1
```

补充：

- 相同的路径获得一个别名。`where`、`orderBy`、`select` 共用该别名。
- **只**在 `orderBy` 或 `select` 中使用的路径是 `left join`，因此在那里不会丢失行。同一路径也在 `where` 中使用时，`orderBy` 和 `select` 使用 `where` 的连接。
- 两个实体的比较（`o.getCustomer() == c`）会被拒绝（`LQL-3011`）。请比较 id：`o.getCustomer().getId() == c.getId()`（这里 `c` 是捕获的对象，参阅 [3.8](#38-strings) 的 `rejected-forms` 示例）。

示例代码：[`MappingRulesTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/MappingRulesTest.java)（`embedded-compare`）、[`SelectQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java)（`inner-join`、`null-guard`、`no-association`、`fk-path`）。

<a id="311-captured-values"></a>
## 3.11 捕获值

Lambda 表达式可以使用外部的值：局部变量、字段、对捕获对象的方法调用。

<!-- region: ManualJavaWhereTest.java#captured-forms -->
```java
LocalDateTime since = LocalDateTime.of(2026, 1, 1, 0, 0);
Filter filter = new Filter(60.0);
Form form = new Form(" a ");
SelectQuery<Order> query = qb.from(Order.class)
        .where(o -> o.getUpdateAt().compareTo(since) > 0)            // a local variable
        .where(o -> o.getStatus() == this.defaultStatus)             // a field of this
        .where(o -> o.getPrice() > filter.minPrice())                // a method of a captured object
        .where(o -> o.getDescription().equals(form.text().trim()));  // computed in Java
assertEquals("select e0 from Order e0 where e0.updateAt > :p1 and e0.status = :p2"
        + " and e0.price > :p3 and e0.description = :p4", query.toHql().hql());
```

规则：

- 不使用实体的部分在**每次执行查询时**于 Java 中计算。其结果成为绑定参数。
- 编译期常量（`static final int MAX = 5;`）不是捕获值。javac 把值复制到 Lambda 表达式中，因此像 `5` 一样成为字面量（参阅 [3.3](#33-basic-form)）。
- Lambda 表达式只分析一次，结果会被缓存。每次调用都读取新的捕获值。
- 只有实体部分成为 SQL。`form.text().trim()` 在 Java 中计算。对实体值的 `o.getDescription().trim()` 会被拒绝（`LQL-3008`）。
- Java 中捕获的局部变量必须是事实上的 final。如果值会变化，请在 Lambda 表达式之前计算到新变量中。
- 使用字段的 Lambda 表达式会捕获 `this`。请参阅 [9.8](09-java-notes.md#98-lambdas-in-instance-methods-capture-this)。

示例代码：[`ManualJavaWhereTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaWhereTest.java)（`captured-forms`）。

<a id="312-errors-that-depend-on-order-of-evaluation"></a>
## 3.12 取决于求值顺序的错误

SQL 不保证从左到右求值 `AND`/`OR`。因此下面这样的"守卫"在 SQL 中并不安全。
lambdaQL 按原样把守卫写入 HQL。如果数据库先计算除法，在 `qty` 为 0 的行上可能出现除以零错误。

<!-- region: ManualJavaWhereTest.java#guard -->
```java
// the guard is written as it is; SQL does not promise to check "qty <> 0" before the division
assertEquals("select e0 from Item e0 where e0.qty <> 0 and (e0.price / e0.qty) > 2.0",
        qb.from(Item.class).where(i -> i.getQty() != 0 && i.getPrice() / i.getQty() > 2).toHql().hql());
// compare without the division
SelectQuery<Item> query = qb.from(Item.class).where(i -> i.getQty() > 0 && i.getPrice() > 2 * i.getQty());
assertEquals("select e0 from Item e0 where e0.qty > 0 and e0.price > (2.0 * e0.qty)", query.toHql().hql());
```

不要依赖守卫，请改为不含除法的比较：`i.getQty() > 0 && i.getPrice() > 2 * i.getQty()`。

示例代码：[`ManualJavaWhereTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaWhereTest.java)（`guard`）。

<a id="313-what-is-rejected"></a>
## 3.13 会被拒绝的内容

lambdaQL 分析失败时，抛出包含代码 `[LQL-xxxx]` 和源代码行号的异常。
大多数形式在分析 Lambda 表达式时（首次使用时）被发现。错误的捕获值（调用了方法的 `null`、NaN、含 `null` 元素的集合）在执行查询时被发现。
请参阅 [11. 错误与语言](../11-errors-and-languages.md)。

| 写法 | 示例 | 解决方法 |
|---|---|---|
| 块 Lambda 表达式中的循环、`switch`、局部变量 | `o -> { int x = 1; return ...; }` | 写成一个表达式 |
| 一般的 boolean 三元运算符 | `c ? x : y` | 使用 [3.5](#35-and-or-not) 中允许的形式 |
| 不做短路求值的运算符（`LQL-2013`） | `a & b`、`a \| b`、`a ^ b` | 使用 `&&`、`\|\|` |
| 字符串 `+`（`LQL-2007`） | `o.getDescription().equals("A" + code)` | 在 Lambda 表达式之前构造字符串 |
| Lambda 表达式中的 Lambda 表达式或 stream | `o -> list.stream().anyMatch(...)` | 使用 `contains`（[3.9](#39-in-with-a-captured-collection)）或子查询 |
| 对实体值的字符串方法（`LQL-3008`） | `o.getDescription().trim()`（支持 `startsWith`、`endsWith`、`contains`，[3.8](#38-strings)） | 不支持 |
| 实体的其他方法 | `o.calcSomething()` | 只使用 getter |
| 实体集合路径 | `c.getOrders().size() > 0`（`Customer.orders`） | `exists` 子查询 |
| 实体与实体的比较（`LQL-3011`） | `o.getCustomer() == c` | 比较 id |
| 值对象的大小比较、与其他类型的比较（`LQL-3012`） | `o.getAddress().compareTo(a) > 0`（`Address` 为 `Comparable` 时） | 比较字段。支持相等比较 |
| 调用方法的 null 捕获值 | `p == null` 时的 `o.getDescription().equals(p.trim())`、`o.getStatus() > p.intValue()`（`Double p`） | 在前面写 null 守卫（`p == null \|\| ...`），或在查询前检查 `p` |
| `NaN` / `Infinity` 捕获值 | `o.getPrice() > Double.NaN` | 不要传入 NaN |
| 匿名类、不是 `Serializable` 的 Lambda 表达式 | `new Where<Order>() { ... }`、`java.util.function.Predicate` | 使用 Lambda 表达式 |

异常类型（都继承 `LambdaQLException`）：

| 异常 | 发生时机 |
|---|---|
| `LambdaAnalysisException` | 无法读取 Lambda 表达式时（不是可序列化的 Lambda 表达式等） |
| `UnsupportedLambdaShapeException` | 不支持的形式（循环、`switch`、...） |
| `TranslationException` | 无法翻译含义时（未知方法、集合路径、...） |

Java 专有的细节见 [9. Java 说明](09-java-notes.md)。要在一处查看所有规则：[10. 规则与限制](10-rules-and-limits.md)。

---

[Java 路线](README.md) | 上一章：[2. 入门](02-getting-started.md) | 下一章：[4. 排序与分页](04-order-paging.md) | [本页的 Kotlin 版本](../kotlin/03-where.md)
