<a id="9-java-notes"></a>
# 9. Java 说明

[Java 路线](README.md) | [手册首页](../README.md) | 上一章：[8. 存储过程与数据库函数](08-procedures-functions.md) | 下一章：[10. 规则与限制](10-rules-and-limits.md) | [本页的 Kotlin 版本](../kotlin/09-kotlin-notes.md)

公共页面：[1. 概述](../01-overview.md) · [11. 错误与语言](../11-errors-and-languages.md)

---

本页汇总 lambdaQL Lambda 中 **Java** 代码的特殊之处。
核心始终不变：**lambdaQL Lambda 不是代码，而是查询表达式。**
lambdaQL 不调用 Lambda，而是分析其字节码 opcode。因此 Java 的部分运行规则在这里不适用。

<a id="contents"></a>
## 目录

- [9.1 Lambda 必须是可序列化 Lambda](#91-lambdas-must-be-serializable-lambdas)
- [9.2 装箱与拆箱](#92-boxing-and-unboxing)
- [9.3 装箱值的相等比较](#93-equality-of-boxed-values)
- [9.4 字符串](#94-strings)
- [9.5 null 安全的辅助方法](#95-null-safe-helpers)
- [9.6 小类型：char、short、byte、boolean](#96-small-types-char-short-byte-and-boolean)
- [9.7 float、double、BigDecimal](#97-float-double-and-bigdecimal)
- [9.8 实例方法中的 Lambda 会捕获 this](#98-lambdas-in-instance-methods-capture-this)
- [9.9 方法引用](#99-method-references)
- [9.10 不要期待的静态辅助方法](#910-static-helpers-you-should-not-expect)
- [9.11 其他 Java 写法](#911-other-java-forms)
- [9.12 编译器与构建](#912-compiler-and-build)

---

<a id="91-lambdas-must-be-serializable-lambdas"></a>
## 9.1 Lambda 必须是可序列化 Lambda

lambdaQL 通过 Java 序列化（`SerializedLambda`）找到 Lambda 的主体。
它不使用 `setAccessible`，也不会真正序列化捕获值。

| 传入的内容 | 结果 |
|---|---|
| 直接写在调用中的 Lambda：`where(o -> o.isActive())` | 可以。`where` 的参数类型是 `Serializable` 函数式接口 |
| 条件中的方法引用：`where(Order::isActive)` | **拒绝**（`LQL-1009`）。请使用 Lambda（见 [9.9](#99-method-references)） |
| 保存在 lambdaQL 参数类型变量中的 Lambda | 可以 |
| `java.util.function.Predicate`（不是 `Serializable`） | **拒绝**（`LambdaAnalysisException`） |
| 匿名类：`new Where<Order>() { ... }` | **拒绝** |

说明：

- 捕获值（以及捕获的 `this`）**不需要**是 `Serializable`。lambdaQL 只读取这些值，不序列化它们。
- 子查询的内部 Lambda（`sub.from(Item.class).where(i -> ...)`）本身不需要是 `Serializable`。lambdaQL 在外层 Lambda 的字节码 opcode 中找到它们。

<a id="92-boxing-and-unboxing"></a>
## 9.2 装箱与拆箱

示例实体 `Order` 同时具有装箱类型属性（`Long id`、`Integer cnt`、`Double total`）和基本类型属性（`double price`、`short status`）。
把装箱值与基本类型比较时，javac 会添加**拆箱**调用（`intValue()`、`doubleValue()`）。

<!-- region: ManualJavaNotesTest.java#unboxing -->
```java
// cnt is an Integer column: javac writes o.getCnt().intValue() == 1; lambdaQL keeps only the meaning
SelectQuery<Order> q = qb.from(Order.class).where(o -> o.getCnt() == 1);
assertEquals("select e0 from Order e0 where e0.cnt = 1", q.toHql().hql());
// a NULL cnt: no NullPointerException, the row is simply not returned (the SQL meaning)
```

lambdaQL **忽略**装箱和拆箱调用（`valueOf`、`intValue`、`doubleValue` 等），只保留其含义。

这对 NULL 意味着：

| Java 写法 | 在 Java 中（如果执行） | 在 lambdaQL 中 |
|---|---|---|
| `o.getCnt() == 1`，cnt 为 NULL | NullPointerException | SQL 含义：UNKNOWN，不返回该行 |
| `o.getTotal() > 100`，total 为 NULL | NullPointerException | 不返回该行 |
| `!c.getVip()`（`Boolean`，示例实体 `Coded`），vip 为 NULL | NullPointerException | 不返回该行（见 [3.6.3](03-where.md#363-write-your-null-checks-yourself)） |
| `o.getPrice() > p`，捕获的 `Double p` 为 `null` | NullPointerException | 向 `e0.price > :p1` 绑定 NULL，没有匹配的行（`DEBUG` 级别 `LQL-4045`） |
| `i.getQty() == q`，`qty` 是 `int`，捕获的 `Integer q` 为 `null` | NullPointerException | 向 `i.qty = :p1` 绑定 NULL，没有匹配的行（`DEBUG` 级别 `LQL-4045`） |
| `o.getStatus() > p.intValue()`，捕获的 `Double p` 为 `null` | NullPointerException | 查询执行时**抛出异常**（`LQL-4012`） |

也就是说，实体值为 NULL 时遵循 SQL。把捕获值拆箱为其自身基本类型的调用（`Double.doubleValue()`、`Integer.intValue()` 等，包括其后的拓宽转换）与 javac 的自动拆箱是相同的字节码 opcode，因此不执行，而是绑定装箱值。
转换为其他基本类型的调用（`Double` 的 `intValue()`）只能手写，属于计算，因此在 Java 中执行；值为 `null` 时与 Java 一样是错误。

需要 NULL 行时，请自己写出检查。

<!-- region: ManualJavaWhereTest.java#null-check -->
```java
// only rows where cnt is not NULL and not 1
SelectQuery<Order> notOne = qb.from(Order.class).where(o -> o.getCnt() != 1);
assertEquals("select e0 from Order e0 where e0.cnt <> 1", notOne.toHql().hql());
// rows where cnt is NULL too
SelectQuery<Order> notOneOrNull = qb.from(Order.class).where(o -> o.getCnt() == null || o.getCnt() != 1);
assertEquals("select e0 from Order e0 where e0.cnt is null or e0.cnt <> 1", notOneOrNull.toHql().hql());
```

示例代码：[`ManualJavaNotesTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaNotesTest.java)（`unboxing`）、[`ManualJavaWhereTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaWhereTest.java)（`null-check`）。

<a id="93-equality-of-boxed-values"></a>
## 9.3 装箱值的相等比较

在 Java 中，两个**对象**之间的 `==` 检查同一性（是否为内存中的同一个对象）。
在 lambdaQL 中，两个对象之间的 `==` 是**值比较**。
实体值和捕获值都遵循 SQL 含义（见 [3.6](03-where.md#36-null-rules)）。
装箱缓存（-128 到 127 的 `Integer` 和 `Long`）以及 `new String(...)` 都不影响结果。

javac 生成的比较取决于两侧的 **Java 类型**。

| 左侧 | 右侧 | javac 生成 | HQL |
|---|---|---|---|
| `Integer` 列 | `int` 常量或变量 | 拆箱，数值比较 | `e0.cnt = 1` |
| `Integer` 列 | `Integer` 变量 | 对象比较 | `e0.cnt = :p1` |
| `Long` `@Id` 列 | `Long` 变量 | 对象比较 | `e0.id = :p1` |
| `Long` 列 | `long` 变量 | 拆箱 | `e0.id = :p1` |
| `String` | `String` | 对象比较 | `e0.description = :p1` |
| `String` 列 | `String` 列 | 对象比较 | `e0.description = e0.custName`（任一侧为 NULL 时不匹配） |
| 枚举 | 枚举 | 对象比较 | `x = :p1` |
| `Boolean` 列 | `Boolean.TRUE` | 对象比较 | `e0.active = :p1` |
| `Boolean` 列 | `true` | 拆箱 | `e0.active = true` |
| 值对象（`@Embedded`） | 同类型的捕获值对象 | `equals` / 对象比较 | `e0.address.city = :p1 and e0.address.zip = :p2`（见 [3.10](03-where.md#310-association-paths)） |
| 任意 | `null` | null 检查 | `x is null` |
| 实体 | 实体（`o.getCustomer() == c`） | 对象比较 | –（请改为比较 id） |

与捕获值比较时：

| 列 | 捕获值 | HQL | 结果 |
|---|---|---|---|
| 3 | 3 | `e0.cnt = :p1` | true |
| NULL | `null` | `e0.cnt = :p1` | **false**：`= NULL` 不会为真（`WARN` 级别 `LQL-4045`） |
| NULL | 3 | `e0.cnt = :p1` | false |
| 3 | `null` | `e0.cnt = :p1` | false |

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

列不会被函数包裹，因此数据库可以使用索引。

对象之间的 `!=` 生成 `<>`。值为 NULL 的行不匹配 `<>`（SQL 含义）。Java 则会返回该行。
要保留这样的行，请写 `o.getCnt() == null || o.getCnt() != wanted`。

两个实体值（`o.getDescription() == o.getCustName()`）生成 `=` 和 `<>`。任一侧为 NULL 时都不匹配，NULL 与 NULL 也一样。
结果可能与 Java 不同时，lambdaQL 以 `DEBUG` 级别记录 `LQL-5009`（见 [3.6.3](03-where.md#363-write-your-null-checks-yourself)）。

示例代码：[`ManualJavaWhereTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaWhereTest.java)（`boxed-equals`）。

<a id="94-strings"></a>
## 9.4 字符串

| Java 写法 | HQL | 列为 NULL | `p` 为 `null` |
|---|---|---|---|
| `o.getDescription() == p` | `e0.description = :p1` | 不返回该行 | 执行，没有匹配的行（`LQL-4045`） |
| `o.getDescription().equals(p)` | `e0.description = :p1` | 不返回该行 | 执行，没有匹配的行（`LQL-4045`） |
| `p.equals(o.getDescription())` | `e0.description = :p1` | 不返回该行 | 执行，没有匹配的行（`LQL-4045`） |
| `"abc".equals(o.getDescription())`（常量接收者） | `e0.description = 'abc'`（常量不可能为 NULL，所以是普通的 `=`） | 不返回该行 | – |
| `Objects.equals(o.getDescription(), p)` | `e0.description = :p1` | 不返回该行 | 执行，没有匹配的行（`LQL-4045`） |
| `o.getDescription().compareTo(p) < 0` | `e0.description < :p1` | 不返回该行 | 执行，没有匹配的行（`LQL-4045`） |
| `o.getDescription() == null` | `e0.description is null` | 返回 | – |

需要记住的要点：

- 在普通 Java 中，对字符串使用 `==` 是 bug。在 lambdaQL Lambda 中，它是正确的值比较。
- `==`、`equals`、`Objects.equals` 对 NULL 的处理相同。列为 NULL 或 `p` 为 `null` 时没有匹配的行。需要 NULL 行时，请直接写 `o.getDescription() == null`。
- 比较遵循**数据库排序规则（collation）**，而不是 Java。MariaDB 的 `_ci` 排序规则忽略大小写和尾部空格。Oracle 把 `''` 存储为 NULL。
- 直接写在 Lambda 中的字符串常量成为 HQL 字面量，单引号会被转义（`'O''Brien'`）。捕获的字符串始终是绑定参数（见 [3.3](03-where.md#33-basic-form)）。
- 列上的 `startsWith`、`endsWith`、`contains` 变为 `like`（[3.8](03-where.md#38-strings)）。`trim`、`toUpperCase` 等其他字符串方法不支持（`LQL-3008`）。
- Lambda 中的字符串 `+` 会被拒绝（`LQL-2007`），即使只用到捕获值也一样（javac 把 `+` 编译为 `invokedynamic`）。请在 Lambda 之前构建字符串。

<!-- region: ManualJavaNotesTest.java#string-outside -->
```java
String code = "A" + suffix;                                  // outside: plain Java
SelectQuery<Order> q = qb.from(Order.class).where(o -> o.getDescription() == code);
assertEquals("select e0 from Order e0 where e0.description = :p1", q.toHql().hql());
```

示例代码：[`ManualJavaNotesTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaNotesTest.java)（`string-outside`）。

<a id="95-null-safe-helpers"></a>
## 9.5 null 安全的辅助方法

下列 Java 辅助方法遵循与 `==` 相同的规则，即 SQL 含义。捕获值 `null` 不匹配任何行。

| Java | HQL | 说明 |
|---|---|---|
| `Objects.equals(o.getCnt(), s)` | `e0.cnt = :p1` | `s` 为 `null` 时没有匹配的行（`LQL-4045`） |
| `!Objects.equals(o.getCnt(), 1)` | `e0.cnt <> 1` | **不返回** NULL 行（SQL 含义；Java 会返回） |
| `Objects.equals(o.getDescription(), o.getCustomer().getName())` | `e0.description = j1.name` + `join e0.customer j1` | 两列比较：任一侧为 NULL 时不匹配 |
| `Boolean.TRUE.equals(c.getVip())` | `e0.vip = :p1` | |
| `!Boolean.TRUE.equals(c.getVip())` | `e0.vip <> :p1` | **不返回** NULL 行（SQL 含义） |
| `"x".equals(o.getDescription())` | `e0.description = 'x'` | 常量接收者：不可能为 NULL，所以是普通的 `=` |
| `set` 值中的 `Objects.requireNonNullElse(o.getTotal(), 0.0)` | `coalesce(e0.total, 0.0)` | 见 [7. 更新、插入、删除](07-dml.md) |

与 `!=` 比较：

<!-- region: ManualJavaNotesTest.java#not-equals -->
```java
SelectQuery<Order> notOne = qb.from(Order.class).where(o -> o.getCnt() != 1);
assertEquals("select e0 from Order e0 where e0.cnt <> 1", notOne.toHql().hql());              // no NULL rows
SelectQuery<Order> notEquals = qb.from(Order.class).where(o -> !Objects.equals(o.getCnt(), 1));
assertEquals("select e0 from Order e0 where e0.cnt <> 1", notEquals.toHql().hql());           // no NULL rows either
SelectQuery<Order> withNull = qb.from(Order.class)
        .where(o -> o.getCnt() == null || !Objects.equals(o.getCnt(), 1));
assertEquals("select e0 from Order e0 where e0.cnt is null or e0.cnt <> 1", withNull.toHql().hql());  // NULL rows too
```

示例代码：[`ManualJavaNotesTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaNotesTest.java)（`not-equals`）。

<a id="96-small-types-char-short-byte-and-boolean"></a>
## 9.6 小类型：char、short、byte、boolean

JVM 没有专门针对 `char`、`short`、`byte`、`boolean` 的比较指令。javac 把它们按 `int` 编译。
lambdaQL 知道这一点。

- 支持 `long`、`int`、`short`、`byte`、`char`、`float`、`double` 值的比较。
- 常量会被转换为列的类型。例如，对 `short` 列而言，`int` 常量 `10` 会变成 `short` 值。
- 单独的 `boolean` getter 就是条件：`o.isActive()` 变成 `e0.active = true`。
- 布尔列与条件的比较 `o.isActive() == (o.getStatus() > s)` 中，条件一侧变成 `case when`。需要索引时，请拆成 `&&` 和 `||` 来写（见 [3.7](03-where.md#37-boolean-columns)）。
- 基本类型属性（`boolean active`、`int qty`）与 `@Id` 或 `nullable = false` 列一样，被视为“不可能为 NULL”。因此 lambdaQL 可以省略对这些属性的 null 检查，`o.getId() == null` 变成 `1=0`（[3.6.2](03-where.md#362--on-objects-compares-values)）。

<a id="97-float-double-and-bigdecimal"></a>
## 9.7 float、double、BigDecimal

| 主题 | 规则 |
|---|---|
| `float` / `double` 常量（`100.0`、`1.5f`） | 与 Lambda 中的其他常量一样，作为字面量写进 HQL（`100.0`、`1.5F`）。在 Lambda 中创建的 `BigDecimal`（`new BigDecimal("1.5")`）是计算出的值，因此是绑定参数 |
| 逐位相同的结果 | **不保证**。各数据库存储和比较 `float`、`double` 的方式不同。lambdaQL 不添加 `CAST`。请自行选择列映射（例如 Oracle 的 `binary_float` / `binary_double`） |
| 捕获的 `NaN`、`Infinity` | 查询执行时**抛出异常** |
| 存储在数据库中的 `NaN` | 遵循数据库（H2 把它当作最大值排序，MariaDB 无法存储） |
| `BigDecimal` 的相等比较 | Java 的 `equals` 会比较标度（`1.0` 不等于 `1.00`）。lambdaQL 比较的是**值** |
| 整数 `/` | 各数据库结果不同（MariaDB 中 `5/2 = 2.5`）。最终规则尚未确定。请避免使用 |

<a id="98-lambdas-in-instance-methods-capture-this"></a>
## 9.8 实例方法中的 Lambda 会捕获 this

实例方法中的 Lambda 使用字段时，javac 会把 Lambda 主体编译成**实例方法**并捕获 `this`。

<!-- region: ManualJavaNotesTest.java#this-capture -->
```java
@Service
public static class OrderService {

    private final QueryBuilder qb;
    private int defaultStatus = 1;

    public OrderService(QueryBuilder qb) {
        this.qb = qb;
    }

    public List<Order> defaults() {
        return qb.from(Order.class)
                 .where(o -> o.getStatus() == defaultStatus)   // means this.defaultStatus
                 .toList();
    }
}
```

**各数据库的 SQL**

<!-- sql: this-capture -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=?
```

lambdaQL 的处理方式：

- 它知道第一个捕获值是 `this`。
- `this.defaultStatus` 不使用实体，因此在 Java 中计算，并作为参数绑定。
- 字段在**每次执行查询时**读取。分析结果按 Lambda 缓存，而不是按对象缓存。
- 类不需要是 `Serializable`。
- 不使用实体的 `this` 方法调用（`this.minPrice()`）同样在每次执行查询时在 Java 中计算。请保证它们没有副作用。

局部变量则不同。Java 在创建 Lambda 时复制其值。Java 要求局部变量是 effectively final 的。

<!-- region: ManualJavaNotesTest.java#local-copy -->
```java
int st = defaultStatus;                                   // copied now
SelectQuery<Order> q = qb.from(Order.class).where(o -> o.getStatus() == st);
// the same HQL as the lambda that reads the field
assertEquals("select e0 from Order e0 where e0.status = :p1", q.toHql().hql());
assertEquals(q.toHql().hql(), qb.from(Order.class).where(o -> o.getStatus() == defaultStatus).toHql().hql());
```

两种写法生成相同的 HQL。

示例代码：[`ManualJavaNotesTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaNotesTest.java)（`this-capture`、`local-copy`）。

<a id="99-method-references"></a>
## 9.9 方法引用

| 方法引用 | 等同于 | 状态 |
|---|---|---|
| `where(Order::isActive)` | `where(o -> o.isActive())` | **拒绝**（`LQL-1009`）。请使用 Lambda |
| `orderBy(Order::getPrice)` | `orderBy(o -> o.getPrice())` | 已实现 |
| `orderBy(Order::getPrice, Direction.DESC)` | `orderBy(o -> o.getPrice(), Direction.DESC)` | 已实现 |
| `SortSpec.of(Order.class, Order::getPrice)` | `SortSpec.of(Order.class, o -> o.getPrice())` | 已实现。在使用单个键的排序写法中都可以使用 |

lambdaQL 在加载方法引用时识别它，不读取被引用方法的主体。在排序键中，它自己构建 `o.getPrice()` 调用；在条件中则拒绝。

<!-- region: ManualJavaNotesTest.java#method-ref -->
```java
// a method reference as a sort key: the same as o -> o.getPrice()
assertEquals("select e0 from Order e0 order by e0.price asc",
        qb.from(Order.class).orderBy(Order::getPrice).toHql().hql());
// a method reference as a condition is rejected: write a lambda
assertEquals("LQL-1009", assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(Order::isActive)).code());
```

有些方法引用可以编译，但含义是错误的。lambdaQL 在分析时拒绝它们。

- 把单参数实例方法用作双参数 Lambda（第二个参数变成方法的实参），
- 把双参数静态方法用作双参数 Lambda。

在 `orderBy` 中只允许实体的 getter。静态方法、带参数的方法、绑定到对象的引用会被拒绝。

不是简单 getter 时，请使用 Lambda。

示例代码：[`ManualJavaNotesTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaNotesTest.java)（`method-ref`）。

<a id="910-static-helpers-you-should-not-expect"></a>
## 9.10 不要期待的静态辅助方法

lambdaQL 的静态辅助类 `By`（排序函数）、`Agg`（聚合）、`Dml`（赋值）只在 Lambda 中使用。
子查询工厂以 **Lambda 参数**（`sub`）的形式传入。其余部分用普通 Java 编写。

| 可能期待的写法 | lambdaQL 的写法 |
|---|---|
| `orderBy(asc(...), desc(...))` | `orderBy(o -> asc(...).desc(...))`：在一个 Lambda 中连接多个键（[4](04-order-paging.md)） |
| `exists(...)`、`in(...)`、`notIn(...)` | `(o, sub) -> sub.from(X.class).where(...).exists()` / `.select(...).contains(x)`（[5](05-subquery.md)） |
| `set(path, value)` | `set(o -> o.setX(v))` 或 `set(o -> value(o.getX(), v))`（`Dml.value`）（[7](07-dml.md)） |
| Q 类、`Order_.price` | getter：`o.getPrice()` |
| `count()`、`sum(...)` 等 | `Agg.count()`、`Agg.sum(...)`（[6](06-select-group.md)） |
| `coalesce(a, b)` | `set` 值中的 `Objects.requireNonNullElse(a, b)` |
| `like(...)`、`lower(...)`、`upper(...)` | 不支持（列上的字符串函数） |
| `currentTimestamp()` | `LocalDateTime.now()`。在应用程序中计算并绑定 |
| 作用于实体值的自定义静态工具方法 | **拒绝**。带有 `@SqlFunction` 的数据库函数除外（[8](08-procedures-functions.md)） |

**只使用捕获值**的静态方法没有问题。它们在每次执行查询时在 Java 中运行。

<!-- region: ManualJavaNotesTest.java#static-helper -->
```java
// a static method on captured values only: computed in Java when the query runs
SelectQuery<Order> q = qb.from(Order.class)
        .where(o -> o.getUpdateAt().compareTo(LocalDateTime.of(2026, 1, 1, 0, 0)) > 0);
assertEquals("select e0 from Order e0 where e0.updateAt > :p1", q.toHql().hql());
// your own static method on an entity value: rejected
assertEquals("LQL-3008", assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(o -> normalize(o.getDescription()) == text)).code());
```

示例代码：[`ManualJavaNotesTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaNotesTest.java)（`static-helper`）。

<a id="911-other-java-forms"></a>
## 9.11 其他 Java 写法

| Java 写法 | 在 lambdaQL Lambda 中 |
|---|---|
| 单个表达式 `o -> ...` | 标准写法 |
| 带局部变量的块 Lambda `o -> { ...; return ...; }` | 在 `where`、`orderBy`、`select` 中拒绝 |
| 只包含 setter 调用的块 Lambda | 在 `set`、`values`、`insert ... select` 中允许（[7](07-dml.md)） |
| `if`、`for`、`while`、`switch`（语句或表达式）、`try` | 拒绝 |
| 三元运算符 `c ? x : y` | 只允许 `c ? x : false`、`c ? true : x`、`c ? false : true`（[3.5](03-where.md#35-and-or-not)） |
| 布尔值上的 `&`、`\|`、`^` | 拒绝。请使用 `&&`、`\|\|` |
| Stream、嵌套 Lambda（`sub` 子查询除外） | 拒绝 |
| Lambda 中的 `List.of(...)` | 拒绝。作为 `groupBy` 键时除外 |
| `compareTo(...) < 0` | 变为 `<`、`>`。`LocalDateTime` 的 `isBefore`、`isAfter` 被拒绝（`LQL-3008`） |
| `captured.length`（捕获值的数组） | 在 Java 中计算 |
| Record | 作为结果类型（`select`），以及派生表和 CTE 的行类型（用 `r.total()` 读取） |
| `var` | 可用于构建器变量。类型为 `SelectQuery<T>`、`OrderedSelectQuery<T>`、`ValueQuery<R>` 等 |
| 泛型 `value(o.getStatus(), "abc")` | 在 Java 中可以编译。分析时与列类型核对，报错 `LQL-3065` |
| 在自身带有 `value(...)` 方法的类（例如带 `value` 组件的 Record）中调用 `value(...)` | 静态导入被遮蔽。请写成 `Dml.value(...)` |
| 嵌套 Lambda 中使用相同的参数名 | Java 不允许。请使用 `sub2`、`i`、`x` |

<a id="912-compiler-and-build"></a>
## 9.12 编译器与构建

| 主题 | 需要了解的内容 |
|---|---|
| 编译器 | lambdaQL 以 **javac** 输出为基准。不保证支持由 **ECJ**（Eclipse 编译器）编译的 Lambda。Eclipse IDE 以及其他基于 Eclipse JDT 的工具使用 ECJ。请用 Gradle 或 Maven（javac）运行测试 |
| JDK 版本 | Java 21 及以上 |
| 读取类文件（ASM） | 在 Java 21~23 上，使用 ASM 9.11.1 读取类文件（最高到 Java 28 类文件）。使用更新的 JDK 编译时，请升级 ASM。lambdaQL 没有对 ASM 做 shade，因此可以自行升级。在 Java 24 及以上版本上，使用正在运行的 JDK 的 `java.lang.classfile` 读取，因此与 ASM 版本无关。lambdaQL jar 是 Multi-Release JAR，JVM 会选择与运行版本匹配的一侧 |
| `-parameters` | 推荐。lambdaQL 可以借此检查存储过程的参数名（[8.4](08-procedures-functions.md#84-warning-argument-order)） |
| 调试信息 | 请保留行号（javac 默认）。lambdaQL 会把源代码行写进错误消息 |
| IntelliJ 的 "Number objects compared using `==`" | 在 lambdaQL Lambda 中是有意为之。可以在方法上声明 `@SuppressWarnings("NumberEquality")` 来消除警告 |
| IntelliJ 对字符串 `==` 的警告 | 在 lambdaQL Lambda 中同样是有意为之 |

---

[Java 路线](README.md) | 上一章：[8. 存储过程与数据库函数](08-procedures-functions.md) | 下一章：[10. 规则与限制](10-rules-and-limits.md) | [本页的 Kotlin 版本](../kotlin/09-kotlin-notes.md)
