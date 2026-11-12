# 7. 更新、插入、删除（Java）

> [!NOTE]
> **状态：已设计，尚未实现。**
> 本章是已确定的设计（决定 D28，里程碑 M10）。
> 该语法在 Java 中可以编译，SQL 已在 H2、MariaDB 和 Oracle 上使用 Hibernate 6.6.5 检查过。
> 目前它在 lambdaQL 中**还不能**运行。目前只有分析核心以及 `where` + `toList()` 可以工作
> （已在 H2、MariaDB 和 Oracle 上测试）。完整状态见 [Java 路线 README](README.md#status)。

[Java 路线](README.md) | [手册首页](../README.md) | 上一章：[6. 查询列、聚合与分组](06-select-group.md) | 下一章：[8. 存储过程与数据库函数](08-procedures-functions.md) | [本页的 Kotlin 版本](../kotlin/07-dml.md)

公共页面：[1. 概述](../01-overview.md) · [11. 错误与语言](../11-errors-and-languages.md)

---

lambdaQL 有四个入口：

| 入口 | 用途 |
|---|---|
| `qb.from(X.class)` | 查询（第 3 章至第 6 章） |
| `qb.update(X.class)` | 更新行 |
| `qb.insert(X.class)` | 插入行 |
| `qb.delete(X.class)` | 删除行 |

思路与查询相同：

- 每个部分写**一个 Lambda**。没有静态辅助方法。
- Lambda **从不执行**。lambdaQL 读取它的字节码。像 `o.setStatus(1)` 这样的 setter 调用会变成 `set o.status = 1`。
- `where` 使用与 [3. 条件](03-where.md)相同的规则。子查询像 [5. 子查询](05-subquery.md)中一样使用 `sub`。
- `execute()` 执行语句并返回**受影响的行数**（`int`）。

## 目录

- [7.1 示例实体](#71-example-entities)
- [7.2 更新](#72-update)
- [7.3 两种赋值方式：setter 或 `by.set`](#73-two-ways-to-assign-setter-or-byset)
- [7.4 删除](#74-delete)
- [7.5 插入](#75-insert)
- [7.6 DML 的 Java 说明](#76-java-notes-for-dml)
- [7.7 重要规则](#77-important-rules)
- [7.8 全部规则 U1 至 U21（汇总）](#78-all-rules-u1-to-u21-summary)
- [7.9 v0.1 中不包含的内容](#79-not-in-v01)

---

<a id="71-example-entities"></a>
## 7.1 示例实体

本章使用 `Order`、`Customer` 和 `Item`（见 [2. 入门](02-getting-started.md#example-entities)）。
这里的 `Order` 还多了几个字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `id`、`price`、`status`、`active`、`description`、`updateAt`、`customer` | | 与其他章节相同 |
| `total` | `Double` | 明细价格之和 |
| `cnt` | `Integer` | 计数器 |
| `custName` | `String` | 客户名的副本 |
| `address` | `Address`（嵌入：`city`、`zip`） | 嵌入值 |

其他实体：`VOrder`（带有 `@Version` 字段 `version` 的订单）、`IdOrder`（id 自动生成的订单）、`Archive`（`id`、`price`、`customerName`）和 `Summary`（`customerId`、`total`、`cnt`）。

---

<a id="72-update"></a>
## 7.2 更新

→ [7.2 更新](07-2-update.md)

<a id="73-two-ways-to-assign-setter-or-byset"></a>
## 7.3 两种赋值方式：setter 或 `by.set`

| 方式 | 何时使用 | 示例 |
|---|---|---|
| Setter | 实体有 setter。编译器会检查值的类型。 | `set(o -> o.setStatus(1))` |
| `by.set(column, value)` | 实体**没有 setter**（例如 DDD 风格的实体）。 | `set((o, by) -> by.set(o.getStatus(), 1))` |

上面每个 setter 示例也都可以用 `by.set` 来写：

```java
qb.update(Order.class)
  .set((o, by) -> by.set(o.getStatus(), st).set(o.getPrice(), o.getPrice() * 1.1))
  .where(o -> o.getStatus() == 1)
  .execute();

qb.update(Order.class).set((o, by) -> by.set(o.getAddress().getCity(), city)).where(o -> o.getId() == id).execute(); // 嵌入
qb.update(Order.class).set((o, by) -> by.set(o.getCustomer().getId(), cid)).where(o -> o.getId() == id).execute();  // 外键
```

第二个 Lambda 参数是**一个上下文对象**。它既有 `set(column, value)`，也有子查询方法 `from(...)`。
你可以随意把它命名为 `by` 或 `sub`。名字无关紧要。

```java
qb.update(Order.class)
  .set((o, by) -> by.set(o.getTotal(),
        by.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).sum(i -> i.getPrice())))
  .all()
  .execute();
```

`by.set` 的规则（规则 U20）：

- 第一个参数必须是**从目标开始的列路径**：字段、嵌入路径或关联的 `@Id`。
  捕获值、计算式或源实体的路径都是错误。
- 在 Java 中，`by.set(o.getStatus(), "abc")` 可以编译，因为类型是泛型的。
  lambdaQL 会在分析时对照列类型检查值的类型。
- 不要在**同一个 Lambda 中**混用 setter 和 `by.set`。可以在不同的 `set()` 调用中混用。

---

<a id="74-delete"></a>
## 7.4 删除

→ [7.4 删除](07-4-delete.md)

<a id="75-insert"></a>
## 7.5 插入

→ [7.5 插入](07-5-insert.md)

<a id="76-java-notes-for-dml"></a>
## 7.6 DML 的 Java 说明

| Java 写法 | 含义 |
|---|---|
| 块 Lambda `o -> { o.setA(...); o.setB(...); }` | 可用于 `set`、`values` 和 `insert ... select`，但**只能包含 setter 调用**。不能有局部变量、`if` 或循环（规则 U1） |
| `Objects.requireNonNullElse(a, b)` | `coalesce(a, b)`（规则 U2） |
| `o.setCnt(o.getCnt() + 1)` | 支持。javac 会对值拆箱和装箱；lambdaQL 会忽略这些 |
| `o.setActive(!o.isActive())`（取反切换） | **v0.1 中不支持**（这是条件值）。请使用 `where` 不同的两次更新 |
| `o.setDescription(o.getDescription() + "_x")` | **v0.1 中不支持**（字符串拼接） |
| 以 `LocalDateTime.now()` 作为值 | 在应用程序中计算后绑定（规则 U8） |
| `var u = qb.update(...).set(...)` | 用 `var` 逐步构建更新 |

---

<a id="77-important-rules"></a>
## 7.7 重要规则

### 右侧读取的是旧值

在 SQL 中，SET 右侧的每一列都表示更新**之前**的值。lambdaQL 遵循这条规则。

```java
.set(o -> { o.setPrice(o.getPrice() * 2); o.setTotal(o.getPrice()); })
// 错误：先给 price 赋值，然后又读取它
```

如果你**在同一条语句中先给某列赋值、之后又读取它**，lambdaQL 会在分析时报错并停止（规则 U3）。
原因：这种情况下 MariaDB 使用**新**值，而 H2 和 Oracle 使用旧值。结果不同（已检查：MariaDB 为 200，H2 和 Oracle 为 100）。

以下情况没有问题，因为三种数据库的结果一致：

| 写法 | 可以吗？ |
|---|---|
| `o.setPrice(o.getPrice() * 1.1)`（读取自身） | 可以 |
| `o.setTotal(o.getPrice()); o.setPrice(0.0);`（先读取，后赋值） | 可以 |
| `o.setPrice(...); o.setTotal(o.getPrice());`（先赋值，后读取） | **错误** |

该检查针对物理列（嵌入的子列、外键列），跨多个 `set()` 调用，也在嵌套的 `sub` Lambda 内部进行。

### 每列只能赋值一次

对同一列赋值两次是错误（规则 U4）。这包括先 `setAddress(...)` 再 `getAddress().setCity(...)`，
或者先 `setCustomer(...)` 再 `getCustomer().setId(...)`。

### 必须有 `where` 或 `all()`

`set()` 返回的类型**没有** `execute()`。只有 `where()` 和 `all()` 返回带有 `execute()` 的类型。
所以忘记写 `where` 会导致编译错误（规则 U5）。`delete` 也是如此。

### 关联路径：与查询相同的行

在 `where` 中，非外键的关联路径会变成**关联 `exists`**（规则 U9）：

```java
.where(o -> o.getCustomer().getName().equals("Kim"))
// → where exists (select 1 from Customer c where c.id = o.customer.id and c.name = 'Kim')
```

在 SET 的右侧，它会变成**关联标量子查询**：

```java
o.setCustName(o.getCustomer().getName())
// → set o.custName = (select c.name from Customer c where c.id = o.customer.id)
```

为什么？如果 lambdaQL 直接把路径交给 Hibernate，Hibernate 会为每种数据库生成不同的 join SQL
（H2 用 `merge`，MariaDB 用 `update ... join`，Oracle 用内联视图），而且当路径出现在 SET 中时，**没有客户的行会丢失**
（已检查：使用 `.all()` 时，10 行中只有 9 行被更新）。
lambdaQL 自己编写这些子查询。因此 `update` 和 `delete` 处理的**正好是**同一个 `where` Lambda 在查询中返回的那些行。
查询对 `where` 中的路径默认使用内连接（决策 D36），所以针对客户路径的 `where` 不会处理没有客户的订单，除非 null 守卫保留了它们。

以 `@Id` 结尾的路径（`o.getCustomer().getId()`）是外键列。没有子查询，也没有 join。

<a id="batching-for-many-rows"></a>
### 多行的分批

`values(list, ...)` 写出一条带多个 VALUES 行的语句（规则 U13）。

| 要点 | 规则 |
|---|---|
| 拆分单位 | 绑定值的数量（行数 x 列数），而不是行数 |
| 默认上限 | 每条语句 1000 个绑定值 |
| 返回值 | 所有部分的总和 |
| 事务 | 只允许在事务内部拆分 |
| 空列表 | 不执行 SQL，返回 0 |

Oracle 有 65,535 个绑定值的硬性上限（ORA-01745）。在达到上限之前解析就会变得非常慢
（测试中 21,845 行大约需要 6 分钟）。所以请保持默认上限。

### 持久化上下文不会被更新

批量 DML 不经过 JPA 持久化上下文（规则 U16）：

- Hibernate 会在语句执行前刷新（flush）待处理的更改。
- 语句执行后，同一会话中已加载的实体保留的是**旧值**。如果还要使用它们，请调用 `entityManager.clear()`。
- JPA 的 `cascade` 和 `orphanRemoval` **不会**执行。请先删除子行，或在数据库中使用 `ON DELETE CASCADE`。

---

<a id="78-all-rules-u1-to-u21-summary"></a>
## 7.8 全部规则 U1 至 U21（汇总）

| # | 规则 |
|---|---|
| U1 | 赋值 Lambda **只包含**对目标、其嵌入路径或关联的 `@Id` 的 **setter 调用**。`if`、三元运算 `c ? a : b`、循环、局部变量和其他方法调用都是错误。没有任何赋值的 Lambda 是错误。 |
| U2 | 右侧遵循 `where` 的值规则（[3. 条件](03-where.md)）。捕获值是绑定参数。`Objects.requireNonNullElse(a, b)` 变成 `coalesce(a, b)`。 |
| U3 | 右侧读取的是**旧**值。在同一条语句中给某列赋值后再读取它是错误。 |
| U4 | 对同一物理列赋值两次是错误，跨 `set()` 调用也是如此。 |
| U5 | 没有 `where()` 或 `all()` 就没有 `execute()`。由编译器检查。 |
| U6 | 给不可为空的列（`@Id`、`nullable=false`、基本类型）赋 `null` 是错误。给集合赋值，或给关联实体的非 `@Id` 字段赋值，都是错误。 |
| U7 | 捕获值的每次使用都有自己的参数。（测试中，STRING 列和 ORDINAL 列共享一个枚举参数时，会悄无声息地存入错误的值。） |
| U8 | `LocalDateTime.now()` 在应用程序中求值并绑定。不支持数据库时间（`current_timestamp`）。 |
| U9 | DML 中非外键的关联路径：在 `where` 中是关联 `exists`（与查询得到相同的行，D36）；在 SET 的右侧是关联标量子查询。DML 处理的行与查询相同。 |
| U10 | 在 `insert ... select` 中，源路径会变成显式的 `left join`。 |
| U11 | `o.setCustomer(entity)` 绑定该实体的 `@Id`。如果 id 为 null，绑定会失败。 |
| U12 | IDENTITY 和 SEQUENCE 的 id 可以省略。TABLE 生成器只适用于 `values`，不适用于 `insert ... select`。没有生成器时，id 是必需的。其他不可为空的字段也是必需的。 |
| U13 | 多行按绑定数量拆分（默认 1000）。结果会相加。只能在事务内部进行。 |
| U14 | 一个 `set` Lambda 可以给多列赋值，`set()` 也可以调用多次。所有赋值按调用顺序合并成一个 SET 列表。 |
| U15 | `@Version` 实体使用 `update versioned`。不要自己给版本号赋值。检查 `execute() == 0` 是你的工作。 |
| U16 | 批量 DML 跳过持久化上下文。执行前刷新，执行后实体是过期的，没有级联。 |
| U17 | 如果 DELETE 的子查询读取同一个实体，lambdaQL 会把它包装在派生表中。（否则会出现 MariaDB 错误 1093。） |
| U18 | 继承：完全支持 SINGLE_TABLE。JOINED 和 TABLE_PER_CLASS 的更新/删除使用临时表。拒绝向它们的子类插入。 |
| U19 | 查询缓存的键包括入口、目标实体、set Lambda、where/all、versioned 以及拆分大小。 |
| U20 | `by.set(column, value)`：列必须是目标上的 getter 路径。类型在分析时检查。不要在同一个 Lambda 中混用 setter 和 `by.set`。 |
| U21 | 每个 Lambda 只分析一次并缓存。跨 `set()` 调用的检查只比较缓存的摘要（原型中约 0.1 到 0.2 µs）。 |

---

<a id="79-not-in-v01"></a>
## 7.9 v0.1 中不包含的内容

| 项目 | 计划 | 现在该怎么做 |
|---|---|---|
| 条件值（`c ? a : b`、作为值的 `!o.isActive()`） | v0.2（CASE WHEN） | – |
| 在 Lambda 内部用 `if` 选择赋值 | v0.2 | 多次调用 `set()`（U14） |
| 字符串拼接（`o.getDescription() + "_x"`） | v0.2 | – |
| `insert ... select` 中的显式 join | v0.2 | 使用多对一路径 |
| Upsert（`on conflict`） | v0.2 | – |
| 带 `order by ... limit` 的 DML | 不支持 | 先查询 id，再按 id 列表删除 |
| `returning` | 不支持 | – |
| 数据库时间（`current_timestamp`） | 不支持 | 绑定应用程序时间（U8） |

本章的错误是带有 `[LQL-xxxx]` 代码的 `LambdaQLException` 子类。见 [11. 错误与语言](../11-errors-and-languages.md)。
所有规则汇总在一处：[10. 规则与限制](10-rules-and-limits.md)。

---

[Java 路线](README.md) | 上一章：[6. 查询列、聚合与分组](06-select-group.md) | 下一章：[8. 存储过程与数据库函数](08-procedures-functions.md) | [本页的 Kotlin 版本](../kotlin/07-dml.md)
