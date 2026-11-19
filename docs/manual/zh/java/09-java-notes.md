<a id="9-java-notes"></a>
# 9. Java 说明

[Java 路线](README.md) | [手册首页](../README.md) | 上一章：[8. 存储过程与数据库函数](08-procedures-functions.md) | 下一章：[10. 规则与限制](10-rules-and-limits.md) | [本页的 Kotlin 版本](../kotlin/09-kotlin-notes.md)

公共页面：[1. 概述](../01-overview.md) · [11. 错误与语言](../11-errors-and-languages.md)

---

本页汇总了 lambdaQL Lambda 中 **Java** 代码的特殊之处。
核心思想始终不变：**lambdaQL 的 Lambda 是查询，而不是代码。**
lambdaQL 读取你写的内容，但不执行它。因此 Java 的一些运行时规则在这里并不适用。

<a id="contents"></a>
## 目录

- [9.1 Lambda 必须是可序列化 Lambda](#91-lambdas-must-be-serializable-lambdas)
- [9.2 装箱与拆箱](#92-boxing-and-unboxing)
- [9.3 装箱值的相等比较](#93-equality-of-boxed-values)
- [9.4 字符串](#94-strings)
- [9.5 null 安全的辅助方法](#95-null-safe-helpers)
- [9.6 小类型：char、short、byte 和 boolean](#96-small-types-char-short-byte-and-boolean)
- [9.7 float、double 和 BigDecimal](#97-float-double-and-bigdecimal)
- [9.8 实例方法中的 Lambda 会捕获 this](#98-lambdas-in-instance-methods-capture-this)
- [9.9 方法引用](#99-method-references)
- [9.10 不要期待的静态辅助方法](#910-static-helpers-you-should-not-expect)
- [9.11 其他 Java 写法](#911-other-java-forms)
- [9.12 编译器与构建](#912-compiler-and-build)
- [9.13 目前可以验证的内容](#913-what-you-can-check-today)

---

<a id="91-lambdas-must-be-serializable-lambdas"></a>
## 9.1 Lambda 必须是可序列化 Lambda

lambdaQL 通过 Java 序列化（`SerializedLambda`）找到 Lambda 的主体。
它不使用 `setAccessible`，也不会真正序列化捕获的值。

| 传入的内容 | 结果 |
|---|---|
| 直接写在调用中的 Lambda：`where(o -> o.isActive())` | 可以。`where` 的参数类型是 `Serializable` 函数式接口 |
| 方法引用：`where(Order::isActive)` | 可以（见 [9.9](#99-method-references)） |
| 保存在 lambdaQL 参数类型变量中的 Lambda | 可以 |
| `java.util.function.Predicate`（不是 `Serializable`） | **拒绝**（`LambdaAnalysisException`） |
| 匿名类：`new Where<Order>() { ... }` | **拒绝** |

说明：

- 捕获值（以及捕获的 `this`）**不需要**是 `Serializable`。lambdaQL 只读取它们，不序列化它们。
- 子查询的内部 Lambda（`sub.from(Item.class).where(i -> ...)`）本身不需要是 `Serializable`。lambdaQL 会在外层 Lambda 的字节码中找到它们。
- lambdaQL 函数式接口的名称尚未最终确定。能直接传入 Lambda 时，请直接传入。

<a id="92-boxing-and-unboxing"></a>
## 9.2 装箱与拆箱

示例实体使用装箱类型：`Long id`、`Double price`、`Integer status`。
把装箱值与基本类型值比较时，javac 会添加一个**拆箱**调用（`intValue()`、`doubleValue()`）。

```java
.where(o -> o.getStatus() == 1)     // javac: o.getStatus().intValue() == 1
// HQL: e0.status = 1
```

lambdaQL **忽略**装箱和拆箱调用（`valueOf`、`intValue`、`doubleValue` 等），只保留其含义。

这对 NULL 意味着：

| Java 写法 | 在 Java 中（如果执行） | 在 lambdaQL 中 |
|---|---|---|
| `o.getStatus() == 1`，status 为 NULL | NullPointerException | SQL 含义：UNKNOWN，不返回该行（规则 R1） |
| `o.getPrice() > 100`，price 为 NULL | NullPointerException | 不返回该行（R1） |
| `!o.getActive()`（`Boolean`），active 为 NULL | NullPointerException | 不返回该行 |
| `o.getPrice() > p`，捕获的 `Double p` 为 `null` | NullPointerException | 向 `e0.price > :p1` 绑定 NULL，没有匹配的行（`DEBUG` 级别 `LQL-4045`） |
| `i.getQty() == q`，`qty` 是 `int`，捕获的 `Integer q` 为 `null` | NullPointerException | 向 `i.qty = :p1` 绑定 NULL，没有匹配的行（`DEBUG` 级别 `LQL-4045`） |
| `o.getStatus() > p.intValue()`，捕获的 `Double p` 为 `null` | NullPointerException | 查询执行时**抛出异常**（`LQL-4012`） |

也就是说，实体值为 NULL 时遵循 SQL 规则。把捕获值拆箱为其自身基本类型的调用（`Double.doubleValue()`、`Integer.intValue()` 等，包括其后的拓宽转换）与 javac 的自动拆箱是相同的字节码，因此 lambdaQL 不执行它，而是绑定装箱值。
转换为其他基本类型的调用（`Double` 的 `intValue()`）只能手写，属于计算，因此在 Java 中执行；值为 `null` 时与 Java 一样是错误。

如果需要 NULL 行，请自己写出检查：

```java
.where(o -> o.getStatus() == null || o.getStatus() != 1)
// HQL: e0.status is null or e0.status <> 1
```

<a id="93-equality-of-boxed-values"></a>
## 9.3 装箱值的相等比较

在 Java 中，两个**对象**之间的 `==` 检查的是同一性（内存中是否为同一个对象）。
在 lambdaQL 中，两个对象之间的 `==` 是**值比较**（决策 D12）。
实体值和捕获值都遵循 SQL 语义（决策 D48，见 [3.6](03-where.md#36-null-rules)）。
装箱缓存（-128 到 127 的 `Integer` 和 `Long`）以及 `new String(...)` 都不影响结果。

适用哪条规则，取决于两侧的 **Java 类型**。

| 左侧 | 右侧 | javac 生成 | lambdaQL 规则 | HQL |
|---|---|---|---|---|
| `Integer` 列 | `int` 常量或变量 | 拆箱，数值比较 | R1，SQL 含义 | `e0.status = 1` |
| `Integer` 列 | `Integer` 变量 | 对象比较 | R2 | `e0.status = :p1` |
| `Long` `@Id` 列 | `Long` 变量 | 对象比较 | R2，但一侧永远不为 NULL | `e0.id = :p1` |
| `Long` 列 | `long` 变量 | 拆箱 | R1 | `e0.id = :p1` |
| `String` | `String` | 对象比较 | R2 | `e0.description = :p1` |
| `String` 列 | `String` 列 | 对象比较 | R2，SQL 含义 | `e0.description = e0.custName`（任一侧为 NULL：不匹配） |
| 枚举 | 枚举 | 对象比较 | R2 | `x = :p1` |
| `Boolean` 列 | `Boolean.TRUE` | 对象比较 | R2 | `e0.active = :p1` |
| `Boolean` 列 | `true` | 拆箱 | R1 | `e0.active = true` |
| 值对象（`@Embedded`） | 同类型的捕获值对象 | `equals` / 对象比较 | 按列展开（B6） | `e0.address.city = :p1 and e0.address.zip = :p2`（见 [3.10](03-where.md#310-association-paths)） |
| 任意 | `null` | null 检查 | R3 | `x is null` |
| 实体 | 实体（`o.getCustomer() == c`） | 对象比较 | **v0.1 不支持** | –（请改为比较 id） |

与捕获值比较时：

| 列 | 捕获值 | HQL | 结果 |
|---|---|---|---|
| 3 | 3 | `e0.status = :p1` | true |
| NULL | `null` | `e0.status = :p1` | **false**：`= NULL` 不会为真（`WARN` 级别 `LQL-4045`） |
| NULL | 3 | `e0.status = :p1` | false |
| 3 | `null` | `e0.status = :p1` | false |

```java
Integer wanted = request.getStatus();        // 可能为 null
qb.from(Order.class).where(o -> o.getStatus() == wanted).toList();
// wanted = 3    -> status 为 3 的行
// wanted = null -> 没有匹配的行（LQL-4045）。要查找 NULL，请写 o.getStatus() == null
```

列不会被函数包裹，因此数据库可以使用索引。

对象之间的 `!=` 生成 `<>`。值为 NULL 的行不匹配 `<>`（SQL 含义，D48）。Java 会返回该行。
要保留这样的行，请写 `o.getStatus() == null || o.getStatus() != wanted`。

两个实体值（`o.getDescription() == o.getCustName()`）生成 `=` 和 `<>`。任一侧为 NULL 时都不匹配，NULL 与 NULL 也一样。
当结果可能与 Java 不同时，lambdaQL 以 `DEBUG` 级别记录 `LQL-5009`（见 [3.6.3](03-where.md#363-write-your-null-checks-yourself)）。

<a id="94-strings"></a>
## 9.4 字符串

| Java 写法 | HQL | 列为 NULL | `p` 为 `null` |
|---|---|---|---|
| `o.getDescription() == p` | `e0.description = :p1` | 不返回该行 | 执行，没有匹配的行（`LQL-4045`） |
| `o.getDescription().equals(p)` | `e0.description = :p1` | 不返回该行 | 执行，没有匹配的行（`LQL-4045`） |
| `p.equals(o.getDescription())` | `e0.description = :p1` | 不返回该行 | 执行，没有匹配的行（`LQL-4045`） |
| `"abc".equals(o.getDescription())`（常量接收者） | `e0.description = 'abc'`（常量永远不为 NULL，所以是普通的 `=`） | 不返回该行 | – |
| `Objects.equals(o.getDescription(), p)` | `e0.description = :p1` | 不返回该行 | 执行，没有匹配的行（`LQL-4045`） |
| `o.getDescription().compareTo(p) < 0` | `e0.description < :p1` | 不返回该行 | 执行，没有匹配的行（`LQL-4045`） |
| `o.getDescription() == null` | `e0.description is null` | 返回 | – |

需要记住的要点：

- 在普通 Java 中，对字符串使用 `==` 是 bug。在 lambdaQL Lambda 中，它是正确的值比较。
- `==`、`equals` 和 `Objects.equals` 对 NULL 的处理相同。列为 NULL 或 `p` 为 `null` 时，没有匹配的行。需要 NULL 行时，请自己写 `o.getDescription() == null`。
- 比较遵循**数据库排序规则（collation）**，而不是 Java。MariaDB 的 `_ci` 排序规则忽略大小写和尾部空格。Oracle 把 `''` 存储为 NULL。
- 直接写在 Lambda 中的字符串常量是 HQL 字面量，单引号会被转义（`'O''Brien'`）。捕获的字符串始终是绑定参数（见 [3.3](03-where.md#33-basic-form)）。
- 列上的字符串方法（`startsWith`、`contains`、`trim`、`toUpperCase`）已在计划中，v0.1 不支持。
- v0.1 会拒绝字符串 `+`，即使只用到捕获值也一样（javac 用 `invokedynamic` 编译 `+`）。请在 Lambda 之前构建字符串。

```java
String code = "A" + suffix;                                  // 外部：普通 Java
qb.from(Order.class).where(o -> o.getDescription() == code).toList();
```

<a id="95-null-safe-helpers"></a>
## 9.5 null 安全的辅助方法

下列 Java 辅助方法遵循与 `==` 相同的规则，即 SQL 含义（规则 R5，D48）。捕获值 `null` 不匹配任何行。

| Java | HQL | 说明 |
|---|---|---|
| `Objects.equals(o.getStatus(), s)` | `e0.status = :p1` | `s` 为 `null` 时没有匹配的行（`LQL-4045`） |
| `!Objects.equals(o.getStatus(), 1)` | `e0.status <> 1` | **不返回** NULL 行（SQL 含义；Java 会返回它们） |
| `Objects.equals(o.getDescription(), o.getCustomer().getName())` | `e0.description = j1.name` + `join e0.customer j1` | 两列比较：任一侧为 NULL 时不匹配 |
| `Boolean.TRUE.equals(o.getActive())` | `e0.active = :p1` | |
| `!Boolean.TRUE.equals(o.getActive())` | `e0.active <> :p1` | **不返回** NULL 行（SQL 含义） |
| `"x".equals(o.getDescription())` | `e0.description = 'x'` | 常量接收者：永远不为 NULL，所以是普通的 `=` |
| `set` 值中的 `Objects.requireNonNullElse(o.getPrice(), 0.0)` | `coalesce(o.price, 0.0)` | 见 [7. 更新、插入、删除](07-dml.md) |

与 `!=` 比较：

```java
.where(o -> o.getStatus() != 1)                 // e0.status <> 1           （不返回 NULL 行）
.where(o -> !Objects.equals(o.getStatus(), 1))  // e0.status <> 1           （同样不返回 NULL 行）
.where(o -> o.getStatus() == null || !Objects.equals(o.getStatus(), 1))   // 返回 NULL 行
```

<a id="96-small-types-char-short-byte-and-boolean"></a>
## 9.6 小类型：char、short、byte 和 boolean

JVM 没有专门针对 `char`、`short`、`byte` 和 `boolean` 的比较指令。javac 把它们按 `int` 编译。
lambdaQL 知道这一点。

- 支持 `long`、`int`、`short`、`byte`、`char`、`float` 和 `double` 值的比较（规则 R1）。
- 常量会被转换为其所在列的类型。例如，对于 `short` 列，`int` 常量 `10` 会变成 `short` 值。
- 单独的 `boolean` getter 就是一个条件：`o.isActive()` 变成 `e0.active = true`。
- 支持 `o.isActive() == (o.getStatus() > s)`（两个布尔表达式）。它会变成 `AND`/`OR`（见 [3.7](03-where.md#37-boolean-columns)）。
- 基本类型属性（`boolean active`、`int qty`）与 `@Id` 或 `nullable = false` 列一样，被视为“永远不为 NULL”。因此 lambdaQL 可以省略对它的 null 检查，`o.getId() == null` 会变成 `1=0`（[3.6.2](03-where.md#362--on-objects-compares-values)）。

<a id="97-float-double-and-bigdecimal"></a>
## 9.7 float、double 和 BigDecimal

| 主题 | 规则 |
|---|---|
| `float` / `double` 常量（`100.0`、`1.5f`） | 与 Lambda 中的其他常量一样，作为字面量写进 HQL（`100.0`、`1.5F`）（D37）。在 Lambda 中创建的 `BigDecimal`（`new BigDecimal("1.5")`）是计算出的值，因此是绑定参数 |
| 逐位精确的结果 | **不保证**。各数据库存储和比较 `float`、`double` 的方式不同。lambdaQL 不会添加 `CAST`。请自行选择列映射（例如 Oracle 的 `binary_float` / `binary_double`） |
| 捕获的 `NaN`、`Infinity` | 查询执行时**抛出异常** |
| 存储在数据库中的 `NaN` | 遵循数据库（H2 把它当作最大值排序；MariaDB 无法存储它） |
| `BigDecimal` 的相等比较 | Java 的 `equals` 会比较标度（`1.0` 不等于 `1.00`）。lambdaQL 比较的是**值** |
| 整数 `/` | 各数据库结果不同（MariaDB 中 `5/2 = 2.5`）。最终规则尚未确定，请避免使用 |

<a id="98-lambdas-in-instance-methods-capture-this"></a>
## 9.8 实例方法中的 Lambda 会捕获 this

当实例方法中的 Lambda 使用了字段时，javac 会把 Lambda 主体编译成**实例方法**并捕获 `this`。

```java
@Service
public class OrderService {

    private final QueryBuilder qb;
    private int defaultStatus = 1;

    public OrderService(QueryBuilder qb) {
        this.qb = qb;
    }

    public List<Order> defaults() {
        return qb.from(Order.class)
                 .where(o -> o.getStatus() == defaultStatus)   // 即 this.defaultStatus
                 .toList();
        // HQL: select e0 from Order e0 where e0.status = :p1
    }
}
```

**关于 SQL 块。** `…` 是缩短的 select 列表（实体的所有列），`?` 是绑定参数。这些行来自 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)。该测试使用 Hibernate 7.4 在 H2 2.3、MariaDB 10.11 和 Oracle 23.26 上运行每个查询，如果 Hibernate 的 SQL 发生变化，测试就会失败。

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `this-capture`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=?
```

lambdaQL 的处理方式：

- 它知道第一个捕获值是 `this`。（这部分已实现并经过测试。）
- `this.defaultStatus` 没有用到实体，因此在 Java 中计算，并作为参数绑定。
- 该字段在**每次执行查询时**读取。分析结果按 Lambda 缓存，而不是按对象缓存。
- 类不需要是 `Serializable`。
- 对 `this` 的方法调用如果不使用实体（`this.minPrice()`），同样在每次执行查询时在 Java 中计算。请保证它们没有副作用。

局部变量则不同。Java 在创建 Lambda 时复制它的值。Java 要求局部变量是 effectively final 的。

```java
int st = defaultStatus;                                   // 此时复制
qb.from(Order.class).where(o -> o.getStatus() == st).toList();
```

两种写法生成相同的 HQL。

<a id="99-method-references"></a>
## 9.9 方法引用

| 方法引用 | 等同于 | 状态 |
|---|---|---|
| `where(Order::isActive)` | `where(o -> o.isActive())` | 已设计（识别部分已实现） |
| `orderBy(Order::getPrice)` | `orderBy(o -> o.getPrice())` | 已实现，已测试 |
| `by` 排序写法 `orderBy((o, by) -> ...)` 中的方法引用 | – | **不允许**。请使用 Lambda（规则 S13） |
| 连接排序 Lambda 中的方法引用 | – | **不允许**。请使用 Lambda（规则 S13） |

lambdaQL 在加载时识别方法引用。它不读取被引用方法的主体，而是自己构建调用 `o.isActive()`。

有些方法引用可以编译，但含义是错误的。lambdaQL 会在分析时拒绝它们（规则 S13）。

- 把单参数实例方法用作双参数 Lambda（第二个参数会变成方法的实参），
- 把双参数静态方法用作双参数 Lambda。

在 `orderBy` 中，只允许实体的 getter。静态方法、带参数的方法，或者绑定到某个对象的引用会被拒绝。

凡是不是简单 getter 的情况，请使用 Lambda。

<a id="910-static-helpers-you-should-not-expect"></a>
## 9.10 不要期待的静态辅助方法

lambdaQL **没有静态 DSL**。除了可选的 `Agg` 导入之外，使用 lambdaQL 不需要 `import static`。
辅助对象以 **Lambda 参数**的形式提供（`by`、`sub`），或者直接写普通 Java。

| 可能期待的写法 | lambdaQL 的写法 |
|---|---|
| `orderBy(asc(...), desc(...))` | `orderBy((o, by) -> by.asc(...).desc(...))`（[4](04-order-paging.md)） |
| `exists(...)`、`in(...)`、`notIn(...)` | `(o, sub) -> sub.from(X.class).where(...).exists()` / `.select(...).contains(x)`（[5](05-subquery.md)） |
| `set(path, value)` | `set(o -> o.setX(v))` 或 `set(o -> value(o.getX(), v))`（`Dml.value`，D49）（[7](07-dml.md)） |
| Q 类、`Order_.price` | getter：`o.getPrice()` |
| `count()`、`sum(...)` 等 | `Agg.count()`、`Agg.sum(...)`：静态辅助类只有 `Agg` 和 `Dml` 两个（[6](06-select-group.md)） |
| `coalesce(a, b)` | 在 `set` 值中使用 `Objects.requireNonNullElse(a, b)` |
| `like(...)`、`lower(...)`、`upper(...)` | 已在计划中（列上的字符串函数） |
| `currentTimestamp()` | `LocalDateTime.now()`。它在应用程序中计算并绑定 |
| 作用于实体值的自定义静态工具方法 | **拒绝**。带有 `@SqlFunction` 的数据库函数除外（[8](08-procedures-functions.md)） |

**只使用捕获值**的静态方法没有问题。它们在每次执行查询时在 Java 中运行。

```java
.where(o -> o.getUpdateAt().isAfter(LocalDateTime.of(2026, 1, 1, 0, 0)))   // 可以：在 Java 中计算
.where(o -> MyUtil.normalize(o.getDescription()) == text)                   // 拒绝：使用了实体
```

<a id="911-other-java-forms"></a>
## 9.11 其他 Java 写法

| Java 写法 | 在 lambdaQL Lambda 中 |
|---|---|
| 单个表达式 `o -> ...` | 标准写法 |
| 带局部变量的块 Lambda `o -> { ...; return ...; }` | 在 `where`、`orderBy`、`select` 中被拒绝 |
| 只包含 setter 调用的块 Lambda | 在 `set`、`values`、`insert ... select` 中允许（[7](07-dml.md)） |
| `if`、`for`、`while`、`switch`（语句或表达式）、`try` | 拒绝 |
| 三元表达式 `c ? x : y` | 只允许 `c ? x : false`、`c ? true : x`、`c ? false : true`（[3.5](03-where.md#35-and-or-not)） |
| 布尔值上的 `&`、`\|`、`^` | 拒绝。请使用 `&&`、`\|\|` |
| Stream、嵌套 Lambda（`sub` 子查询除外） | 拒绝 |
| Lambda 内部的 `List.of(...)` | 拒绝，作为 `groupBy` 键时除外 |
| `compareTo(...) < 0`、`isBefore`、`isAfter` | 变为 `<`、`>` |
| `captured.length`（捕获值数组的长度） | 在 Java 中计算 |
| Record | 作为结果类型（`select`），以及派生表和 CTE 的行类型（用 `r.total()` 读取） |
| `var` | 用于构建器变量。构建器的类型名尚未最终确定 |
| 泛型 `value(o.getStatus(), "abc")` | 在 Java 中可以编译。分析时与列类型核对，报错 `LQL-3065` |
| 在自身带有 `value(...)` 方法的类（例如带 `value` 组件的 Record）中调用 `value(...)` | 静态导入被遮蔽。请写 `Dml.value(...)` |
| 嵌套 Lambda 中使用相同的参数名 | Java 不允许。请使用 `sub2`、`i`、`x` |

<a id="912-compiler-and-build"></a>
## 9.12 编译器与构建

| 主题 | 需要了解的内容 |
|---|---|
| 编译器 | lambdaQL 使用 **javac** 进行测试。不保证支持由 **ECJ**（Eclipse 编译器）编译的 Lambda。Eclipse IDE 以及其他基于 Eclipse JDT 的工具使用 ECJ。请用 Gradle 或 Maven（javac）运行测试 |
| JDK 版本 | Java 21 |
| 读取类文件（ASM） | 在 Java 21~23 上，lambdaQL 使用 ASM 9.11.1 读取类文件（最高支持 Java 28 类文件）。如果使用更新的 JDK 编译，请升级 ASM。lambdaQL 没有对 ASM 做 shade，因此可以自行升级。在 Java 24 及以上版本上，lambdaQL 使用正在运行的 JDK 的 `java.lang.classfile` 读取，因此与 ASM 版本无关。lambdaQL jar 是 Multi-Release JAR，JVM 会选择与运行版本匹配的实现 |
| `-parameters` | 推荐。它使 lambdaQL 能够检查存储过程的参数名（[8.4](08-procedures-functions.md#84-warning-argument-order)） |
| 调试信息 | 保留行号（javac 默认）。lambdaQL 会把源代码行写进错误消息 |
| IntelliJ 的 "Number objects compared using `==`" | 在 lambdaQL Lambda 中是有意为之。可以使用 `@SuppressWarnings("NumberEquality")` |
| IntelliJ 对字符串 `==` 的警告 | 在 lambdaQL Lambda 中同样是有意为之 |

<a id="913-what-you-can-check-today"></a>
## 9.13 目前可以验证的内容

分析核心已经实现。下列测试大多使用的是内部类而非用户 API，但它们展示了本页所述的 Java 行为。

| 测试 | 展示的内容 |
|---|---|
| [`LoadExamplesTest.java`](../../../../query/src/test/java/org/lambdaql/engine/load/LoadExamplesTest.java) `instanceLambda` | 使用字段的 Lambda 把 `this` 作为第一个捕获值（[9.8](#98-lambdas-in-instance-methods-capture-this)） |
| [`LoadExamplesTest.java`](../../../../query/src/test/java/org/lambdaql/engine/load/LoadExamplesTest.java) `methodReference` | `Order::isActive` 被识别为方法引用（[9.9](#99-method-references)） |
| [`LoadExamplesTest.java`](../../../../query/src/test/java/org/lambdaql/engine/load/LoadExamplesTest.java) `notSerializable` | `java.util.function.Predicate` 被拒绝（[9.1](#91-lambdas-must-be-serializable-lambdas)） |
| [`ConditionDecodeTest.java`](../../../../query/src/test/java/org/lambdaql/engine/decode/ConditionDecodeTest.java) | 比较中的拆箱（`o.getId() > minId`）、`&&`、`\|\|`、`!` |
| [`SortQueryTest.java`](../../../../query/src/test/java/org/lambdaql/query/SortQueryTest.java) region `sort-key` | `Order::getPrice` 与 `o -> o.getPrice()` 一样，作为单个排序键的排序使用（[9.9](#99-method-references)） |
| [`HostEvaluatorTest.java`](../../../../query/src/test/java/org/lambdaql/engine/sem/HostEvaluatorTest.java) `convertNumbers` | 数字被转换为列的类型（例如 `int` 转为 `short`）（[9.6](#96-small-types-char-short-byte-and-boolean)） |

---

[Java 路线](README.md) | 上一章：[8. 存储过程与数据库函数](08-procedures-functions.md) | 下一章：[10. 规则与限制](10-rules-and-limits.md) | [本页的 Kotlin 版本](../kotlin/09-kotlin-notes.md)
