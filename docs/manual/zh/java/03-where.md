<a id="3-conditions-where-java"></a>
# 3. 条件（`where`）（Java）

[Java 路线](README.md) | [手册首页](../README.md) | 上一章：[2. 入门](02-getting-started.md) | 下一章：[4. 排序与分页](04-order-paging.md) | [本页的 Kotlin 版本](../kotlin/03-where.md)

公共页面：[1. 概述](../01-overview.md) · [11. 错误与语言](../11-errors-and-languages.md)

---

<a id="31-the-idea-in-one-sentence"></a>
## 3.1 一句话说明核心思想

**lambdaQL 的 Lambda 是查询，不是代码。** lambdaQL 读取 Lambda 的字节码，从不执行它。
因此 Java 的运行时规则（NullPointerException、对象同一性、装箱缓存）都不适用。
lambdaQL 使用它自己的规则。本章将解释这些规则。

如果某种写法没有对应的规则，lambdaQL 不会猜测。它会抛出带有清晰消息的异常（见 [3.13](#313-what-is-rejected)）。

<a id="32-example-entities"></a>
## 3.2 示例实体

所有示例都使用这些实体（省略了 getter 和 setter）：

```java
@Entity class Order {
    @Id Long id;
    Double price;
    Integer status;
    boolean active;            // getter: isActive()
    String description;
    @ManyToOne Customer customer;
    LocalDateTime updateAt;
    @OneToMany(mappedBy = "order") List<Item> items;
}
@Entity class Customer { @Id Long id; String name; Integer grade; }
@Entity class Item     { @Id Long id; @ManyToOne Order order; Double price; int qty; }
```

<a id="33-basic-form"></a>
## 3.3 基本形式

```java
QueryBuilder qb = new QueryBuilder(entityManagerFactory);

List<Order> list = qb.from(Order.class)
    .where(o -> o.getStatus() == 1 && o.getPrice() > 100.0)
    .toList();
// HQL: select e0 from Order e0 where e0.status = 1 and e0.price > 100.0
```

**关于 SQL 块。** `…` 是缩短的 select 列表（实体的所有列），`?` 是绑定参数。这些行来自 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)。该测试使用 Hibernate 7.4 在 H2 2.3、MariaDB 10.11 和 Oracle 23.26 上运行每个查询，如果 Hibernate 的 SQL 发生变化，测试就会失败。

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `to-hql`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>100.0
```

标记为 **(已测试)** 的示例是从 [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java) 中复制的。
该测试检查 HQL，执行查询，并把返回的行与在 Java 中用同一条件过滤的结果进行比较。它在 H2、MariaDB 和 Oracle 上都能通过。
测试用的实体与 3.2 略有不同：`price` 是 `double`，`status` 是 `short`，`Customer.id` 是 `int`，并且 `Order` 有一个 enum `kind`（`Order.Kind`：`NORMAL`、`RUSH`）。

`toHql()` 返回 HQL 和参数，但不执行查询。
**(已测试)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java)，region `to-hql`：

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

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `to-hql`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>100.0
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `to-hql#2`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>?
```

你可以多次调用 `where()`。这些条件以 `AND` 连接。
**(已测试)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java)，region `where-many`：

```java
RenderedQuery q = qb.from(Order.class)
        .where(o -> o.getStatus() == 1 || o.getStatus() == 3)
        .where(o -> o.isActive())
        .toHql();
assertEquals("select e0 from Order e0 where (e0.status = 1 or e0.status = 3) and e0.active = true", q.hql());
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `where-many`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where (o1_0.status=1 or o1_0.status=3) and o1_0.active=true
```

方法引用也可以使用。`where(Order::isActive)` 与 `where(o -> o.isActive())` 相同。

**HQL 中的值。** 规则取决于值的来源（决策 D37）：

| 值 | 在 HQL 中 | 示例 |
|---|---|---|
| 写在 Lambda 中的常量：数字（包括小数）、字符串、`true`/`false`、`null`、enum 常量、编译期 `static final` 常量 | **字面量** | `o.getPrice() > 100` → `e0.price > 100.0` |
| 捕获的值：局部变量、字段、方法参数 | **绑定参数** | `o.getPrice() > minPrice` → `e0.price > :p1` |
| 计算出的值：`new BigDecimal("1.5")`、`form.getText().trim()`、`LocalDateTime.of(...)` | **绑定参数** | `:p1` |

- 常量在每次调用时都相同。因此 SQL 文本不会改变，数据库在制定执行计划时也能看到这个值。
- 字符串常量中的引号会被转义：`"O'Brien"` 变为 `'O''Brien'`。这没有 SQL 注入风险，因为该字符串是编译进类里的常量，而不是用户输入。
- 捕获的值可能来自用户，因此它是绑定参数。每次调用只有值会变化。
- 例外只有一个。与整数列比较的小数捕获值写成小数字面量（`2.5BD`）（参见 [3.4](#34-comparisons) 中的“整数列与小数值”）。只写入转换为数字后的值，因此没有 SQL 注入风险。

enum 常量会带着完整的类名写入。
**(已测试)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java)，region `enum`：

```java
RenderedQuery q = qb.from(Order.class).where(o -> o.getKind() == Order.Kind.RUSH).toHql();
assertEquals("select e0 from Order e0 where e0.kind = org.lambdaql.query.Order$Kind.RUSH", q.hql());
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `enum`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.kind='RUSH'
```

<a id="34-comparisons"></a>
## 3.4 比较运算

| Java | HQL | 说明 |
|---|---|---|
| `o.getStatus() == 1` | `e0.status = 1` | |
| `o.getStatus() != 1` | `e0.status <> 1` | |
| `o.getPrice() > p` | `e0.price > :p1` | 还有 `<`、`<=`、`>=` |
| `o.getUpdateAt().isAfter(since)` | `e0.updateAt > :p1` | `isBefore` 生成 `<` |
| `o.getDescription().compareTo("m") < 0` | `e0.description < 'm'` | `compareTo(..) op 0` 变为 `op` |

与数字比较（一侧是基本类型，例如 `== 1` 或 `> 100.0`）遵循 **SQL 语义**。
如果列为 NULL，结果为 UNKNOWN，该行不会被返回。（在 Java 中这会是 NullPointerException。）

可以使用 `+`、`-`、`*`、`/` 的简单算术运算。
避免包含列的整数 `/`。它在 SQL 中计算，因此各数据库的结果不同（MariaDB 给出 `5/2 = 2.5`）。统一列侧 `/` 结果的规则尚未确定。
捕获值之间的计算（下面的 `n / 2.0`）在查询之前于 Java 中进行，因此没有这种差异。

下面示例中的 `cnt` 是测试实体 `Order` 的整数列（`Integer cnt`）。[3.2](#32-example-entities) 的示例实体中没有这个列。

**对列的算术运算与索引**（决策 D50）。像 `o.getCnt() * 2 > n` 这样对列做算术运算时，无法使用索引。lambdaQL 以 `WARN` 级别记录 `LQL-5012`，在 `SargableMode.STRICT` 下分析失败（`LQL-3066`）。
把运算移到值一侧时，请像 `o.getCnt() > n / 2.0` 这样用小数相除。整数除法 `n / 2` 会得到不同的行。如果乘的是负数，请反转比较方向。详见 [10.2](10-rules-and-limits.md#db-functions-on-a-column)。

**整数列与小数值**（决策 D48 B8）。把整数列与小数值比较时，lambdaQL 按写出的值进行比较，不做四舍五入，也不排除该值。

- 写在 Lambda 中的常量保留为字面量。`o.getCnt() >= 2.5` 变为 `e0.cnt >= 2.5`。
- 捕获的值也写成小数字面量。HQL 为 `e0.cnt >= 2.5BD`，没有绑定参数。`BD` 是 HQL 中 `BigDecimal` 字面量的后缀。
- 含有这种元素的 IN 列表，所有元素都写成小数字面量（`e0.cnt in (2.5BD, 3.0BD)`）。

这是捕获值以绑定参数传递这一原则（D37）的例外。当与整数列比较的参数绑定了小数时，即使指定 `BigDecimal` 类型，Hibernate 7 也会拒绝（`QueryArgumentException`，实测）。

以前，值会被截断为列的类型，`2.5` 变成 `2`，从而返回错误的行。Hibernate 6.6 会拒绝以整数列类型绑定的小数（`CoercionException`），而对 IN 列表的元素则静默截断（在三种数据库上实测）。
没有采用改写为整数比较（`>= 3`）的方式。那样执行的 SQL 与 Lambda 不同，而且当实体映射（`Integer`）与数据库列类型（例如带小数的 `DECIMAL`）不一致时，会得到错误的行。

结果在 H2、MariaDB 10.11 和 Oracle 23.26 上正确（实测）。在三种数据库上，SQL 都像 `cnt>=2.5`、`cnt in (2.5,3.0)` 这样保持列不变，直接与小数比较。
MariaDB 的 `EXPLAIN` 显示索引范围扫描（`type=range`）。在 Oracle 上，列没有附加转换，因此是可以使用索引的形式。给出索引提示时为 `INDEX RANGE SCAN`；在 3000 行的表上不加提示时，与整数字面量（`cnt >= 99`）一样选择全表扫描（均为实测）。

**(已测试)** [`SqlMeaningTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlMeaningTest.java)，region `whole-number`：

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

**浮点数：** 对于 `float`/`double` 列，lambdaQL 不保证与 Java 完全一致到每一位的结果。
捕获的 `NaN` 或 `Infinity` 值会在查询执行时被拒绝。

<a id="35-and-or-not"></a>
## 3.5 AND、OR、NOT

| Java | HQL |
|---|---|
| `a && b` | `a and b` |
| `a \|\| b` | `a or b` |
| `!a` | `not (a)` |
| 括号 | 按需保留 |

```java
.where(o -> (o.getStatus() == 1 || o.isActive()) && o.getPrice() > minPrice)
// HQL: (e0.status = 1 or e0.active = true) and e0.price > :p1

.where(o -> !(o.getStatus() == 1 && o.getPrice() > minPrice))
// HQL: not (e0.status = 1 and e0.price > :p1)
```

规则：

- 结果遵循 **SQL 三值逻辑**（TRUE / FALSE / UNKNOWN）。lambdaQL 已经验证，这在 H2、MariaDB 和 Oracle 上返回相同的行。
- javac 有时会移动 `!` 的位置（德摩根定律，`!(x > y)` 变为 `x <= y`）。这不会改变结果。
- lambdaQL **不会简化条件**。如果写 `o.getPrice() > p || o.getPrice() <= p`，它会按原样发送。`price` 为 NULL 的行不会被返回。这是正常的 SQL 行为。
- 请使用 `&&` 和 `||`。非短路运算符 `&`、`|`、`^` 会被拒绝。

**布尔三元表达式。** 只允许三种简短写法。它们分别等同于 `AND`、`OR`、`NOT`：

| Java | 等同于 |
|---|---|
| `c ? x : false` | `c && x` |
| `c ? true : x` | `c \|\| x` |
| `c ? false : true` | `!c` |

其他任何三元表达式（`c ? x : y`），以及任何用于选择值的三元表达式（`o.getStatus() == (flag ? 1 : 2)`），都会被拒绝。
如果选择只取决于捕获的值，请在 Lambda 之前计算出该值：

```java
int wanted = flag ? 1 : 2;
qb.from(Order.class).where(o -> o.getStatus() == wanted).toList();
```

<a id="36-null-rules"></a>
## 3.6 NULL 规则

这是最重要的部分。有两条简单的原则：

1. **实体值按 SQL 语义比较**（决策 D48）。对象之间的 `==`、`equals`、`Objects.equals` 和 `!=` 变为 `=` 和 `<>`。
   值为 NULL 的行与两者都不匹配。
   当结果可能与 Java 不同时，lambdaQL 以 `DEBUG` 级别记录 `LQL-5009`。
2. **捕获的值先在 Java 中计算，比较同样遵循 SQL 语义。** 捕获的 `null` 以 NULL 绑定，因此 `= :p1` 和 `<> :p1` 不匹配任何行。
   这时 lambdaQL 在执行时以 `WARN` 级别记录 `LQL-4045`。要查找 NULL，请写 `x == null`（生成 `is null`）。
   只由捕获值组成的表达式（`p.trim()`、转换为其他基本类型的 `min.intValue()` 等）是在 Java 中计算的值，因此接收者为 `null` 时，会像 Java 一样抛出异常（`LQL-4012`）。
   拆箱为自身基本类型（`min.doubleValue()`）与 javac 的自动拆箱是相同的字节码，因此不执行它，而是直接绑定装箱值（见 [3.6.4](#364-captured-null-values)）。

lambdaQL **只在写出 null 处理时才处理 null**（`== null`、`!= null`）。
它永远不会代为添加隐藏的 null 检查。

<a id="361-rule-table"></a>
### 3.6.1 规则表

| # | Java 写法 | HQL | NULL 行为 |
|---|---|---|---|
| R1 | 一侧是基本类型：`o.getStatus() == 1`、`o.getPrice() > p` | `e0.status = 1`、`e0.price > :p1` | SQL 语义。列为 NULL：该行不返回 |
| R2 | 两侧都是对象：`o.getStatus() == status`（`Integer == Integer`）、`String == String`、enum | `e0.status = :p1` | **值比较。** 列为 NULL：该行不返回。捕获的 `null`：没有匹配的行（`LQL-4045`） |
| R2 | `o.getStatus() != status`（对象） | `e0.status <> :p1` | SQL 语义。列为 NULL：该行**不**返回（Java 会返回它）。捕获的 `null`：没有匹配的行（`LQL-4045`） |
| R2 | 两个实体值：`o.getDescription() == o.getCustName()` | `e0.description = e0.custName` | SQL 语义。任一侧为 NULL：该行不返回（NULL 与 NULL 也一样） |
| R3 | `x == null`、`x != null` | `x is null`、`x is not null` | |
| R4 | `o.getDescription().equals(p)` | `e0.description = :p1` | SQL 语义。列为 NULL：该行不返回。`p == null`：没有匹配的行（`LQL-4045`） |
| R5 | `Objects.equals(a, b)`、`"x".equals(a)`、`Boolean.TRUE.equals(a)` | 与 R2 相同：`a = b`、`a = :p1` | 与 R2 相同 |
| R9 | 捕获的值 | 绑定参数 | 见 [3.6.4](#364-captured-null-values) |

哪些 Java 类型适用 R1、哪些适用 R2，见 [9. Java 说明](09-java-notes.md#93-equality-of-boxed-values)。

<a id="362--on-objects-compares-values"></a>
### 3.6.2 对象上的 `==` 比较的是值

在 Java 中，两个 `Integer`、`Long` 或 `String` 对象之间的 `==` 检查的是**对象同一性**。
在 lambdaQL 中，它是**值比较**。大于 127 的 `Long` 值或 `new String(...)` 都没有影响。
Lambda 是查询，因此 Java 的内存规则不适用。

```java
Integer status = request.getStatus();    // 可能为 null
qb.from(Order.class).where(o -> o.getStatus() == status).toList();
//   status = 3    -> HQL: e0.status = :p1    status 为 3 的行
//   status = null -> HQL: e0.status = :p1    没有匹配的行：= NULL 不会为真（WARN 级别 LQL-4045）
```

要查找 status 为 NULL 的行，请写出 null 检查。`o.getStatus() == null` 生成 `e0.status is null`。

列永远不会被函数包裹，因此数据库可以使用索引。

lambdaQL 在查询执行时绑定捕获的值。`null` 以 NULL 绑定，没有可以匹配的行，因此 lambdaQL 以 `WARN` 级别记录 `LQL-4045`。
**(已测试)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java)，region `null-safe`：

```java
// the SQL meaning (D48): = NULL matches no row; lambdaQL logs LQL-4045 at WARN when it runs with a null
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

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `null-safe`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.description=?
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `null-safe#2`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.description=?
```

> [!TIP]
> **IDE 警告。** IntelliJ 会对这种写法显示 "Number objects compared using `==`"。
> 在 lambdaQL 的 Lambda 内部这是有意为之。可以使用 `@SuppressWarnings("NumberEquality")`。

**永远不会是 NULL 的列。** id、基本类型列以及 `nullable = false` 的列永远不会是 NULL。
对这样的列，`is null` 总是假，因此 lambdaQL 把这个检查折叠为常量并去掉。

- `o.getId() == null` 变为 `1=0`（没有行）。
- `o.getId() != null && o.getId() == id` 变为 `e0.id = :p1`。null 检查消失，数据库可以使用索引。

Kotlin 中 id 为 `Long?` 时的 `it.id == id` 也编译为相同的字节码，因此 Kotlin 写法同样得到简单的 `e0.id = :p1`。

该规则只在路径**没有关联步骤**时适用。订单可以没有客户，因此 `o.getCustomer().getId()` 可能是 NULL，这条路径的 null 检查会保留。

**(已测试)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java)，region `never-null`：

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

```java
// 只返回 status 不为 NULL 且不等于 1 的行：
.where(o -> o.getStatus() != 1)
// HQL: e0.status <> 1

// 同时包含 status 为 NULL 的行：
.where(o -> o.getStatus() == null || o.getStatus() != 1)
// HQL: e0.status is null or e0.status <> 1
```

`equals()` 和 `Objects.equals()` 是不同的：

```java
.where(o -> o.getDescription().equals(text))     // HQL: e0.description = :p1
.where(o -> Objects.equals(o.getDescription(), text))
                                                 // HQL: e0.description = :p1（text 为 null 时没有匹配的行）
.where(o -> !Objects.equals(o.getStatus(), 1))   // HQL: e0.status <> 1
                                                 //      （不返回 NULL 行：SQL 语义）
```

**实体值的 SQL 语义**（决策 D48）。两个实体值之间的比较，以及实体值与一个值的 `!=` 比较，都遵循 SQL。值为 NULL 的行既不匹配 `=`，也不匹配 `<>`。

在 Java 中，`!text.equals(null)` 为 true，`Objects.equals(null, null)` 也为 true，因此 Java 会返回这些行。
可能出现这种差异时，lambdaQL 以 `DEBUG` 级别记录 `LQL-5009`（每个条件一次）。要保留 NULL 行，请在 Lambda 中写出 null 检查。

**(已测试)** [`SqlMeaningTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlMeaningTest.java)，region `sql-meaning`（H2、MariaDB 和 Oracle；各行的 description/custName 组合为 `a/a`、`a/b`、`null/a`、`null/null`、`b/null`，`text` 为 `"a"`）：

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
### 3.6.4 捕获的 null 值

| 写法 | 捕获的值为 `null` 时 |
|---|---|
| 与捕获的值进行 `==`、`!=`、`Objects.equals(...)`、`o.getDescription().equals(p)` 比较 | 执行：以 NULL 绑定的 `= :p1` / `<> :p1`，因此**没有**匹配的行。`WARN` 级别 `LQL-4045` |
| `p.equals(o.getDescription())`（`p` 是接收者） | 同上：`e0.description = :p1`（NULL），**没有**匹配的行。`WARN` 级别 `LQL-4045`。不抛出异常 |
| Java 会对值拆箱的比较：`o.getPrice() > p`（`Double p`）、`i.getQty() == q`（`int qty`、`Integer q`） | 执行：`e0.price > :p1` 绑定 NULL，**没有**匹配的行。`DEBUG` 级别 `LQL-4045`。拆箱为自身基本类型（`p.doubleValue()`）不会被执行 |
| 转换为其他基本类型的拆箱（`p.intValue()`、`Double p`）和方法调用（`p.trim()`） | 查询执行时**抛出异常**（`LQL-4012`）。因为值在 Java 中计算 |

**不推荐 `p.equals(o.getDescription())` 写法。** 在 SQL 中它与 `o.getDescription().equals(p)` 是相同的比较（`e0.description = :p1`），因此即使 `p` 为 `null` 也不会抛出异常。
但作为 Java 代码阅读时，`null.equals(...)` 看起来像 NullPointerException，因此请把实体值放在前面，写成 `o.getDescription().equals(p)`。

原因：Lambda 中的比较遵循 SQL 语义（决策 D48）。在 SQL 中，与 NULL 的 `=`、`<>` 不会为真。
捕获的值是否为 null 只有在查询执行时才能知道，因此 lambdaQL 在执行时记录警告。

只由捕获值组成的表达式是在查询之前于 Java 中计算的值。`p.trim()` 这样的方法调用，以及转换为其他基本类型的拆箱（`p.intValue()`、`Double p`）都属于这一类；值为 `null` 时会像 Java 一样抛出异常（`LQL-4012`）。
但拆箱为自身基本类型（`Double.doubleValue()`、`Integer.intValue()`、`Long.longValue()` 等，包括其后的拓宽转换）不会被计算。
javac 的自动拆箱与手写的调用是相同的字节码，因此 lambdaQL 把它视为自动拆箱并绑定装箱值。值为 `null` 时没有匹配的行，并以 `DEBUG` 级别记录 `LQL-4045`。
要查找 NULL 行，请写 `x == null`。

**用 null 守卫省略过滤。** 如果对同一个捕获值做了 null 检查，当值为 null 时，该条件会变成无过滤并消失。
**(已测试)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java)，region `captured-null-guard`：

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

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `captured-null-guard`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `captured-null-guard#2`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.price>?
```

工作方式：

- 只使用捕获值和常量的部分，会在查询执行时在 Java 中决定。与 Java 一样从左到右、按短路求值处理。
- `min == null` 时，守卫为 true，所以整个条件为 true。没有过滤。比较 `o.getPrice() > min` 不会写入 HQL（Java 也不会执行这个比较）。
- `min = 100.0` 时，守卫为 false，所以只剩下 `e0.price > :p1`。
- Lambda 仍然只分析一次。只有 HQL 文本取决于值。
- 这种情况下守卫的位置不影响结果。`min == null` 时，`o.getPrice() > min || min == null` 同样没有过滤。因为比较部分的拆箱不在 Java 中执行（Java 会抛出 NullPointerException）。
- 对捕获值调用方法的比较（`o.getDescription().equals(p.trim())`）则不同。守卫写在后面时，会先计算 `p.trim()`，因此 `p == null` 时抛出异常（`LQL-4012`）。
- 使用 `&&` 时，守卫的作用相反：`min == null` 时，`min != null && o.getPrice() > min` 不返回任何行（`where 1=0`）。

<a id="365-converted-attributes-convert"></a>
### 3.6.5 转换的属性（`@Convert`）

带有 JPA `AttributeConverter` 的属性按其 Java 值比较。Hibernate（7.4，以前的 6.6 也是）会转换与这种属性比较的字面量，也会转换绑定参数。

因此即使用 Java 值写条件，也能找到存储的行（已在 H2、MariaDB 和 Oracle 上验证）。

**(已测试)** [`ConvertTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ConvertTest.java)，region `convert`：

```java
// code is stored reversed ("abc" as "cba"); vip is stored as 'Y'/'N' (YesNoConverter)
SelectQuery<Coded> literal = qb.from(Coded.class).where(c -> c.getCode().equals("abc"));
assertEquals("select e0 from Coded e0 where e0.code = 'abc'", literal.toHql().hql());
assertEquals(List.of("abc"), codes(literal.toList()));       // Hibernate converts the literal
```

<a id="37-boolean-columns"></a>
## 3.7 布尔列

在示例实体中，`active` 是基本类型 `boolean`（getter 为 `isActive()`）。
带有 `getActive()` 的行针对的是可空的 `Boolean` 列。

| Java | HQL | NULL 行 |
|---|---|---|
| `o.isActive()` | `e0.active = true` | –（基本类型 `boolean` 永远不为 NULL） |
| `!o.isActive()` | `e0.active = false` | –（基本类型 `boolean` 永远不为 NULL） |
| `Boolean.TRUE.equals(o.getActive())`（可空 `Boolean`） | `e0.active = :p1`（`:p1` 为 `true`） | 不返回 |
| `!Boolean.TRUE.equals(o.getActive())` | `e0.active <> :p1` | **不返回**（SQL 语义，D48；Java 会返回它们） |
| `!o.getActive()`（可空 `Boolean`） | `e0.active = false` | **不返回**（SQL 语义：NULL 不是 false） |

**(已测试)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java)，region `boolean`：

```java
assertEquals("select e0 from Order e0 where e0.active = true",
        qb.from(Order.class).where(o -> o.isActive()).toHql().hql());
assertEquals("select e0 from Order e0 where e0.active = false",       // primitive boolean: never NULL
        qb.from(Order.class).where(o -> !o.isActive()).toHql().hql());
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `boolean`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.active=true
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `boolean#2`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.active=false
```

Hibernate 7.4 在三个数据库上都在 SQL 中写 `true` / `false`。Hibernate 6.6 在 MariaDB 和 Oracle 上写的是 `1` / `0`。

说明：

- 对可空 `Boolean` 列使用 `!o.getActive()`，在 Java 中是一次拆箱调用（值为 NULL 时抛出 NullPointerException）。lambdaQL 使用 SQL 语义：NULL 行不返回。
- lambdaQL 不使用 `is not true` 或 `coalesce(active, false)`。它们在 Oracle 上会失败（ORA-00932）。
- 对较大条件的取反总是写作 `not (...)`。lambdaQL 不会在条件外面写 `(...) = false` 或 `(...) is null`，因为 Oracle 23 在 `WHERE` 中对它们的求值是错误的。

**比较两个布尔值**（`==`/`!=`）是支持的：

```java
.where(o -> o.isActive() == (o.getStatus() > s))
// HQL: (e0.active = true and e0.status > :p1) or (e0.active = false and e0.status <= :p1)
```

lambdaQL 用 `AND`/`OR`（而不是 `CASE`）来写，这样数据库可以使用索引。
如果两侧共用同一个条件（例如 `o.isActive() == o.isActive()`），该写法会被拒绝。

<a id="38-strings"></a>
## 3.8 字符串

- 字符串上的 `equals`、`Objects.equals` 和 `==` 使用**数据库的排序规则（collation）**。
  - MariaDB 的 `_ci` 排序规则会忽略大小写和尾随空格。
  - Oracle 把 `''`（空字符串）存储为 NULL。因此在 Oracle 上，`"".equals(o.getDescription())` 与 Java 的结果不同。
- 两个字符串之间的 `==` 是值比较，而不是同一性比较（规则 R2）。见 [9.4](09-java-notes.md#94-strings)。
- Lambda 中的字符串常量以字面量写入。引号会被转义（`''`）。

**(已测试)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java)，region `string-literal`：

```java
RenderedQuery q = qb.from(Order.class).where(o -> o.getDescription().equals("O'Brien")).toHql();
assertEquals("select e0 from Order e0 where e0.description = 'O''Brien'", q.hql());
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `string-literal`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.description='O''Brien'
```

- `startsWith`、`endsWith`、`contains`（LIKE）以及 `trim()`、`toUpperCase()` 等作用于列的函数都在**计划中**，不在第一个版本中。
- 字符串 `+` 在**计划中**。分析核心已经会拒绝 Lambda 内部的 `+`，即使只是连接捕获的值也会拒绝。请在 Lambda 之前构建字符串。

<a id="39-in-with-a-captured-collection"></a>
## 3.9 使用捕获集合的 IN

在 Lambda **外部**构建集合，并对它调用 `contains`：

```java
List<Long> ids = List.of(1L, 2L, 3L);
qb.from(Order.class).where(o -> ids.contains(o.getId())).toList();
// HQL: e0.id in (:p1)

qb.from(Order.class).where(o -> !ids.contains(o.getId())).toList();
// HQL: e0.id not in (:p1)
```

整个集合是一个绑定参数。
**(已测试)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java)，region `in`（测试实体的 status 是 `short`）：

```java
List<Short> statuses = List.of((short) 2, (short) 3);
RenderedQuery q = qb.from(Order.class).where(o -> statuses.contains(o.getStatus())).toHql();
assertEquals("select e0 from Order e0 where e0.status in (:p1)", q.hql());
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `in`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status in (?,?)
```

`!contains` 会变成 `not in`。可能为 NULL 的列也是简单的 `not in`（SQL 语义，决策 D48）。
值为 NULL 的行不匹配 `not in`，lambdaQL 以 `DEBUG` 级别记录 `LQL-5009`。Java 的 `!contains` 对 NULL 值为 true，因此要保留这些行，请写出 null 检查，例如 `o.getDescription() == null || !descs.contains(o.getDescription())`。
**(已测试)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java)，region `not-in`：

```java
List<Long> ids = List.of(1L, 2L);
// the id can never be NULL: a plain NOT IN
assertEquals("select e0 from Order e0 where e0.id not in (:p1)",
        qb.from(Order.class).where(o -> !ids.contains(o.getId())).toHql().hql());
// a column that can be NULL: the SQL meaning (D48), a NULL value is not "not in" the list (DEBUG LQL-5009);
// to keep those rows, write o.getDescription() == null || !descs.contains(o.getDescription())
List<String> descs = Arrays.asList("a", "b");
assertEquals("select e0 from Order e0 where e0.description not in (:p1)",
        qb.from(Order.class).where(o -> !descs.contains(o.getDescription())).toHql().hql());
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `not-in`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.id not in (?,?)
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `not-in#2`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.description not in (?,?)
```

| 情况 | 结果 |
|---|---|
| 接收者是捕获的 `Collection`、`List` 或 `Set` | 支持 |
| 集合中有 `null` 元素 | 查询执行时抛出异常 |
| 空集合 | `contains` 为 FALSE，`!contains` 为 TRUE（Hibernate 生成 `1=0` / `1=1`） |
| 1,500 个元素 | 在 H2、MariaDB 和 Oracle 23ai 上用一条语句即可运行（较旧的 Oracle 版本有 1,000 的限制；未测试） |
| 接收者是实体集合：`o.getItems().contains(item)` | **拒绝**。请使用 `exists` 子查询（[5. 子查询](05-subquery.md)） |
| 在 Lambda 内部创建的集合：`List.of(1, 2).contains(o.getStatus())` | **拒绝**。请在外部创建并捕获它 |

<a id="310-association-paths"></a>
## 3.10 关联路径

`o.getCustomer().getName()` **不会被执行**。它只是指代一个列："关联客户的 `name` 列"。
lambdaQL 用 JPA Metamodel 检查路径的每一步：

| 路径步骤 | HQL | 示例 |
|---|---|---|
| 对一关联（`@ManyToOne`、`@OneToOne`） | 带别名的 `join`（默认是内连接，见下文） | `o.getCustomer().getName()` -> `join e0.customer j1` ... `j1.name` |
| 嵌入（`@Embedded`） | 同一张表，不需要 join。值对象整体的相等比较按列展开（见下文） | 如果 `Order` 有一个嵌入的 `address`：`o.getAddress().getCity()` -> `e0.address.city` |
| 以目标 `@Id` 结尾的路径 | 外键列，不需要 join | `o.getCustomer().getId()` -> `e0.customer.id` |
| 关联本身与 `null` 比较 | 外键列，不需要 join | `o.getCustomer() == null` -> `e0.customer is null` |
| 集合（`@OneToMany`、`@ManyToMany`） | **拒绝** | `o.getItems()...` -> 请使用 `exists` 子查询 |

**值对象整体的比较。** 对嵌入值对象（`@Embedded`）整体做相等比较时，lambdaQL 把它展开为每列一个比较（B6）。
`o.getAddress().equals(addr)`、`Objects.equals(o.getAddress(), addr)`、`addr.equals(o.getAddress())` 都变为 `e0.address.city = :p1 and e0.address.zip = :p2`。

- 取反（`!o.getAddress().equals(addr)`）为 `e0.address.city <> :p1 or e0.address.zip <> :p2`。
- `o.getAddress() == null` 为 `e0.address.city is null and e0.address.zip is null`，`!= null` 为 `e0.address.city is not null or e0.address.zip is not null`。
- 每一列都遵循 SQL 语义（D48）。NULL 列既不匹配 `=`，也不匹配 `<>`。例如 (Seoul, NULL) 行既不匹配相等比较，也不匹配取反（在三种数据库上实测）。
- 捕获的值对象的各属性在查询执行时读取并绑定。捕获的对象为 `null` 时，所有列都绑定 NULL，因此没有匹配的行，并对每一列以 `WARN` 级别记录 `LQL-4045`。
- 嵌套的值对象也会展开。
- 大小比较（`<` 等）以及与其他类型的比较仍然是 `LQL-3012` 错误。请比较字段。

**(已测试)** [`MappingRulesTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/MappingRulesTest.java)，region `embedded-compare`：

```java
// address is @Embedded (city, zip): comparing the whole value object compares each column (D48)
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

在 `where` 中，关联路径默认是**内连接（inner join）**（决策 D36）。
**(已测试)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java)，region `inner-join`：

```java
String name = "Kim";
RenderedQuery q = qb.from(Order.class).where(o -> o.getCustomer().getName().equals(name)).toHql();
assertEquals("select e0 from Order e0 join e0.customer j1 where j1.name = :p1", q.hql());
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `inner-join`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name=?
```

- **没有客户的**订单不在结果中。因为没有可以连接的客户行。
- lambdaQL 不会代为添加 null 检查。在路径前面不需要写 `o.getCustomer() != null &&`。

**null 守卫不会保留没有客户的订单。** `where` 中的路径总是内连接（SQL 语义，决策 D48）。
因此 `o.getCustomer() == null || ...` 不匹配没有客户的订单，因为这些订单没有可以连接的客户行。对于这样的条件，lambdaQL 以 `DEBUG` 级别记录 `LQL-5009`。
要保留没有客户的订单，请使用显式的 `leftJoin`（决策 D44，见 [6.8](06-select-group.md#68-explicit-joins)），并写 `c == null || ...`。
**(已测试)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java)，region `null-guard`：

```java
String name = "Kim";
// the SQL meaning (D48): a where path is an inner join, so "customer is null" never matches here
// (lambdaQL logs LQL-5009 at DEBUG); to keep orders without a customer, use leftJoin (D44)
RenderedQuery q = qb.from(Order.class)
        .where(o -> o.getCustomer() == null || o.getCustomer().getName().equals(name))
        .toHql();
assertEquals("select e0 from Order e0 join e0.customer j1 where e0.customer is null or j1.name = :p1", q.hql());

// the guard removes those orders, so an inner join is right
RenderedQuery q2 = qb.from(Order.class)
        .where(o -> o.getCustomer() != null && o.getCustomer().getName().equals(name))
        .toHql();
assertEquals("select e0 from Order e0 join e0.customer j1 where e0.customer is not null and j1.name = :p1", q2.hql());
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `null-guard`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.id is null or c1_0.name=?
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `null-guard#2`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.id is not null and c1_0.name=?
```

Hibernate 把 `e0.customer is null` 写成 `c1_0.id is null`。在内连接之后，这个条件不会为真。

- `||` 的其他部分也一样。`o.getStatus() == 1 || o.getCustomer().getName().equals(name)` 同样使用内连接，因此 status 为 1 但没有客户的订单不会返回（Java 会返回它）。

要查找**没有**客户的订单，请比较关联本身。这会检查外键列，不需要 join。
**(已测试)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java)，region `no-association`：

```java
RenderedQuery q = qb.from(Order.class).where(o -> o.getCustomer() == null).toHql();
assertEquals("select e0 from Order e0 where e0.customer is null", q.hql());
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `no-association`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id is null
```

以关联实体的 `@Id` 结尾的路径就是外键列。不需要 join。
**(已测试)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java)，region `fk-path`：

```java
RenderedQuery q = qb.from(Order.class).where(o -> o.getCustomer().getId() == 1).toHql();
assertEquals("select e0 from Order e0 where e0.customer.id = 1", q.hql());
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `fk-path`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id=1
```

补充说明：

- 同一条路径只得到一个别名。`where`、`orderBy` 和 `select` 共享这个别名。
- **只**在 `orderBy` 或 `select` 中使用的路径是 `left join`，因此那里不会丢失行（规则 S8 和 J1）。如果 `where` 也使用同一条路径，`orderBy` 和 `select` 会使用 `where` 的 join，即内连接。
- 比较两个实体（`o.getCustomer() == c`）在**计划中**。目前请比较 id：`o.getCustomer().getId() == c.getId()`（这里 `c` 是一个捕获的对象）。

<a id="311-captured-values"></a>
## 3.11 捕获的值

Lambda 可以使用外部的值：局部变量、字段、对捕获对象的方法调用。

```java
LocalDateTime since = LocalDateTime.of(2026, 1, 1, 0, 0);
.where(o -> o.getUpdateAt().isAfter(since))              // 局部变量
.where(o -> o.getStatus() == this.defaultStatus)          // this 的字段
.where(o -> o.getPrice() > filter.getMinPrice())           // 捕获对象上的方法
.where(o -> o.getDescription().equals(form.getText().trim()))
```

规则：

- 不使用实体的部分会在**每次执行查询时**在 Java 中计算。结果成为绑定参数。
- 编译期常量（`static final int MAX = 5;`）不是捕获的值。javac 会把值复制到 Lambda 中，因此它和 `5` 一样是字面量（见 [3.3](#33-basic-form)）。
- Lambda 只分析一次，结果会被缓存。每次调用时都会读取新的捕获值。
- 只有实体部分会变成 SQL。`form.getText().trim()` 是 Java；`o.getDescription().trim()` 则会是数据库函数（计划中）。
- Java 要求捕获的局部变量是 effectively final 的。如果某个值会改变，请在 Lambda 之前把它计算到一个新变量中。
- 使用字段的 Lambda 会捕获 `this`。见 [9.8](09-java-notes.md#98-lambdas-in-instance-methods-capture-this)。

<a id="312-errors-that-depend-on-order-of-evaluation"></a>
## 3.12 取决于求值顺序的错误

SQL 不保证 `AND`/`OR` 从左到右求值。因此像下面这样的"守卫"在 SQL 中并不安全：

```java
qb.from(Item.class)
  .where(i -> i.getQty() != 0 && i.getPrice() / i.getQty() > 2)   // 会被拒绝
```

守卫之下的 `/`、`%` 和类型转换会被拒绝。请改为不用除法的比较：`i.getQty() > 0 && i.getPrice() > 2 * i.getQty()`。

<a id="313-what-is-rejected"></a>
## 3.13 会被拒绝的内容

lambdaQL 会抛出带有 `[LQL-xxxx]` 错误码和源代码行号的异常。它不会猜测。
大多数结构问题会在分析 Lambda 时（首次使用时）被发现。有问题的捕获值（调用方法的 `null`、NaN、含有 `null` 元素的集合）会在查询执行时被发现。
见 [11. 错误与语言](../11-errors-and-languages.md)。

| 写法 | 示例 | 怎么做 |
|---|---|---|
| 块 Lambda 中的循环、`switch`、局部变量 | `o -> { int x = 1; return ...; }` | 写成一个表达式 |
| 值三元表达式 | `o.getStatus() == (flag ? 1 : 2)` | 在 Lambda 之前计算出值 |
| 一般的布尔三元表达式 | `c ? x : y` | 使用 [3.5](#35-and-or-not) 中允许的写法 |
| 非短路运算符 | `a & b`、`a \| b`、`a ^ b` | 使用 `&&`、`\|\|` |
| 字符串 `+` | `o.getDescription() == "A" + code` | 在 Lambda 之前构建字符串（对列的支持在计划中） |
| Lambda 内部的 Lambda 或 stream | `o -> list.stream().anyMatch(...)` | 使用 `contains`（[3.9](#39-in-with-a-captured-collection)）或子查询 |
| 作用于列的字符串函数 | `o.getDescription().startsWith("A")` | 计划中 |
| 实体上的其他方法 | `o.calcSomething()` | 只使用 getter |
| 实体集合路径 | `o.getItems().size() > 0` | `exists` 子查询 |
| 实体与实体比较 | `o.getCustomer() == c` | 比较 id（计划中：实体比较） |
| 值对象的大小比较、与其他类型的比较（`LQL-3012`） | `o.getAddress().compareTo(a) > 0`（`Address` 为 `Comparable` 时） | 比较字段。支持相等比较 |
| 守卫之下的 `/`、`%`、类型转换 | 见 [3.12](#312-errors-that-depend-on-order-of-evaluation) | 去掉守卫或修改条件 |
| 调用方法的 null 捕获值 | `p == null` 时的 `o.getDescription().equals(p.trim())`、`o.getStatus() > p.intValue()`（`Double p`） | 在前面写 null 守卫（`p == null \|\| ...`），或在查询之前检查 `p` |
| 捕获的 `NaN` / `Infinity` 值 | `o.getPrice() > Double.NaN` | 不要发送 NaN |
| 匿名类、不是 `Serializable` 的 Lambda | `new Where<Order>() { ... }`、`java.util.function.Predicate` | 使用 Lambda |

异常类型（都继承自 `LambdaQLException`）：

| 异常 | 何时抛出 |
|---|---|
| `LambdaAnalysisException` | 无法读取 Lambda（不是可序列化的 Lambda 等） |
| `UnsupportedLambdaShapeException` | 不支持的结构（循环、值三元表达式等） |
| `TranslationException` | 无法翻译其含义（未知方法、集合路径等） |

Java 特有的细节见 [9. Java 说明](09-java-notes.md)。所有规则集中在一处：[10. 规则与限制](10-rules-and-limits.md)。

---

[Java 路线](README.md) | 上一章：[2. 入门](02-getting-started.md) | 下一章：[4. 排序与分页](04-order-paging.md) | [本页的 Kotlin 版本](../kotlin/03-where.md)
