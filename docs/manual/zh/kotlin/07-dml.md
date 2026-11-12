# 7. 更新、插入、删除（Kotlin）

> [!NOTE]
> **状态：已设计，尚未实现。**
> 本章是已确定的设计（决定 D28，里程碑 M10）。
> 该 Kotlin 语法可以编译（kotlinc 2.3.21），SQL 已在 H2、MariaDB 和 Oracle 上使用 Hibernate 6.6.5 检查过。
> 目前它在 lambdaQL 中**还不能**运行。目前只有分析核心以及 `where` + `toList()` 可以工作
> （已在 H2、MariaDB 和 Oracle 上测试）。完整状态见[路线 README](README.md)。

[Kotlin 路线](README.md) | [手册首页](../README.md) | 上一章：[6. 查询列、聚合与分组](06-select-group.md) | 下一章：[8. 存储过程与数据库函数](08-procedures-functions.md) | [本页的 Java 版本](../java/07-dml.md)

公共页面：[1. 概述](../01-overview.md) · [11. 错误与语言](../11-errors-and-languages.md)

lambdaQL 有四个入口：

| 入口 | 用途 |
|---|---|
| `qb.from(X::class.java)` | 查询（第 3 章到第 6 章） |
| `qb.update(X::class.java)` | 更新行 |
| `qb.insert(X::class.java)` | 插入行 |
| `qb.delete(X::class.java)` | 删除行 |

思路与查询相同：

- 每个部分写**一个 Lambda**。不需要静态辅助函数。
- Lambda **永远不会执行**。lambdaQL 读取它的字节码。像 `it.status = 1` 这样的属性赋值会编译为 setter 调用，并变成 `set o.status = 1`。
- `where` 使用与 [3. 条件](03-where.md) 相同的规则。子查询像 [5. 子查询](05-subquery.md) 中那样使用 `sub`。
- `execute()` 执行语句并返回**受影响的行数**（`Int`）。

## 目录

- [7.1 示例实体](#71-example-entities)
- [7.2 更新](#72-update)
- [7.3 两种赋值方式：属性或 `by[column] = value`](#73-two-ways-to-assign-property-or-bycolumn--value)
- [7.4 删除](#74-delete)
- [7.5 插入](#75-insert)
- [7.6 DML 的 Kotlin 说明](#76-kotlin-notes-for-dml)
- [7.7 重要规则](#77-important-rules)
- [7.8 全部规则 U1 到 U21（总结）](#78-all-rules-u1-to-u21-summary)
- [7.9 v0.1 中不包含的内容](#79-not-in-v01)

---

<a id="71-example-entities"></a>
## 7.1 示例实体

本章使用 `Order`、`Customer` 和 `Item`（见 [2. 入门](02-getting-started.md#example-entities)）。
在本章中，`Order` 有几个**额外的属性**：

```kotlin
@Entity
@Table(name = "orders")
class Order(
    // ... id、price、status、active、description、customer、updateAt、items 与平常相同
    var total: Double? = null,                 // 订单项价格的合计
    var cnt: Int = 0,                          // 计数器（Int，以便 += 能够编译）
    var custName: String? = null,              // 客户名称的副本
    @Embedded var address: Address = Address(),
)

@Embeddable
class Address(var city: String? = null, var zip: String? = null)
```

这里使用的其他类：

```kotlin
@Entity class VOrder(@Id var id: Long? = null, var status: Int? = null, @Version var version: Long? = null)
@Entity class Archive(@Id var id: Long? = null, var price: Double? = null, var customerName: String? = null)
@Entity class Summary(@Id var customerId: Long? = null, var total: Double? = null, var cnt: Long? = null)
@Entity class IdOrder(@Id @GeneratedValue var id: Long? = null, var price: Double? = null, var status: Int? = null)

data class Line(val id: Long, val price: Double)   // insert 的输入行
```

---

<a id="72-update"></a>
## 7.2 更新

→ [7.2 更新](07-2-update.md)

<a id="73-two-ways-to-assign-property-or-bycolumn--value"></a>
## 7.3 两种赋值方式：属性或 `by[column] = value`

| 写法 | 何时使用 | 示例 |
|---|---|---|
| 属性赋值 | 属性是 `var`（有 setter）。编译器会检查值的类型。 | `set { it.status = 1 }` |
| `by[column] = value` | 属性是 `val`（没有 setter），例如 DDD 风格的实体。 | `set { o, by -> by[o.status] = 1 }` |

`by[o.status] = 1` 是方法 `by.set(o.status, 1)` 的 Kotlin 索引运算符。你也可以直接写 `by.set(...)`；含义相同。

上面的每个示例也都可以用 `by` 来写：

```kotlin
qb.update(Order::class.java)
    .set { o, by -> by[o.status] = st; by[o.price] = o.price!! * 1.1 }
    .where { it.status == 1 }
    .execute()

qb.update(Order::class.java)
    .set { o, by -> by.set(o.status, st).set(o.price, o.price!! * 1.1) }   // 相同，写成调用链
    .where { it.status == 1 }
    .execute()

qb.update(Order::class.java).set { o, by -> by[o.address.city] = city }.where { it.id == id }.execute()  // 嵌入
qb.update(Order::class.java).set { o, by -> by[o.customer?.id] = cid }.where { it.id == id }.execute()   // 外键
```

第二个 Lambda 参数是**一个上下文对象**。它同时有 `set(column, value)` 和子查询函数 `from(...)`。
你可以随意把它命名为 `by` 或 `sub`。名字并不重要。

```kotlin
qb.update(Order::class.java)
    .set { o, by ->
        by[o.total] = by.from(Item::class.java).where { i -> i.order?.id == o.id }.sum { i -> i.price }
    }
    .all()
    .execute()
```

`by[column] = value` 的规则（规则 U20）：

- 列必须是**从目标开始的属性路径**：属性、嵌入路径或关联的 `@Id`。
  捕获值、计算式或源实体的路径都是错误。
- `by.set(column, value)` 是泛型的，因此编译器不会检查值是否与列匹配。
  lambdaQL 会在分析时检查列类型与值类型。
- `by[o.cnt] += 1` **无法编译**（这是有意的）。请写 `by[o.cnt] = o.cnt + 1`。
- 不要在**同一个 Lambda 中**混用属性赋值和 `by[...]`。你可以在不同的 `set()` 调用中混用它们。

---

<a id="74-delete"></a>
## 7.4 删除

→ [7.4 删除](07-4-delete.md)

<a id="75-insert"></a>
## 7.5 插入

→ [7.5 插入](07-5-insert.md)

<a id="76-kotlin-notes-for-dml"></a>
## 7.6 DML 的 Kotlin 说明

| Kotlin 写法 | 含义 |
|---|---|
| `it.status = x` | setter 调用：`set o.status = :p1` |
| 右侧的 `!!` | 会被忽略；它只是为了让代码能够编译（D23） |
| `a ?: b` | `coalesce(a, b)` |
| `it.customer?.id = x` | 总是会执行赋值（`?.` 会被忽略） |
| `it.cnt += 1` | 支持（`set o.cnt = o.cnt + 1`） |
| `by[o.status] = x` | 与 `by.set(o.status, x)` 相同 |
| `by[o.cnt] += 1` | 无法编译（这是有意的）。请写 `by[o.cnt] = o.cnt + 1` |
| `it.cnt++`、`it.cnt--` | **v0.1 中不支持**。请使用 `+= 1`。错误消息会提示这一点 |
| `it.apply { }`、`with(it) { }`、`it.also { }` | **v0.1 中不支持**。请直接赋值。错误消息会提示这一点 |
| Lambda 中的 `if` / `when` | **v0.1 中不支持**。请改为多次调用 `set()`（U14） |
| Lambda 中的局部 `val` | 会被拒绝。它可能隐藏 U3 问题 |

更多关于 Kotlin 的内容：[9. Kotlin 说明](09-kotlin-notes.md)。

---

<a id="77-important-rules"></a>
## 7.7 重要规则

### 右侧读取的是旧值

在 SQL 中，SET 右侧的每个列都表示更新**之前**的值。lambdaQL 遵循这条规则。

```kotlin
.set { it.price = it.price!! * 2; it.total = it.price }
// 错误：price 先被赋值，然后又被读取
```

如果你**先给某列赋值，然后在同一条语句中又读取它**，lambdaQL 会在分析时报错并停止（规则 U3）。
原因：在这种情况下，MariaDB 使用**新**值，而 H2 和 Oracle 使用旧值。结果会不同（已检查：MariaDB 为 200，H2 和 Oracle 为 100）。

以下情况没有问题，因为三种数据库的结果一致：

| 写法 | 可以吗？ |
|---|---|
| `it.price = it.price!! * 1.1`（读取自身） | 可以 |
| `it.total = it.price; it.price = 0.0`（先读取，后赋值） | 可以 |
| `it.price = ...; it.total = it.price`（先赋值，后读取） | **错误** |

该检查作用于物理列（嵌入的子列、外键列），跨越多次 `set()` 调用，也作用于嵌套的 `sub` Lambda 内部。

### 每列只赋值一次

给同一列赋值两次是错误（规则 U4）。这包括先 `it.address = ...` 再 `it.address.city = ...`，
或先 `it.customer = ...` 再 `it.customer?.id = ...`。

### 必须有 `where` 或 `all()`

`set()` 返回一个**没有** `execute()` 的类型。只有 `where()` 和 `all()` 返回带有 `execute()` 的类型。
因此忘记写 `where` 会导致编译错误（规则 U5）。`delete` 也是如此。

### 关联路径：与查询相同的行

在 `where` 中，非外键的关联路径会变成**关联 `exists`**（规则 U9）：

```kotlin
.where { it.customer?.name == "Kim" }
// → where exists (select 1 from Customer c where c.id = o.customer.id and c.name = 'Kim')
```

在 SET 的右侧，它会变成**关联标量子查询**：

```kotlin
it.custName = it.customer?.name
// → set o.custName = (select c.name from Customer c where c.id = o.customer.id)
```

为什么？如果 lambdaQL 直接把路径交给 Hibernate，Hibernate 会为每种数据库生成不同的 join SQL
（H2 用 `merge`，MariaDB 用 `update ... join`，Oracle 用内联视图），而且当路径出现在 SET 中时，**没有客户的行会丢失**
（已检查：使用 `.all()` 时，10 行中只有 9 行被更新）。
lambdaQL 自己编写这些子查询。因此 `update` 和 `delete` 处理的**正好是**同一个 `where` Lambda 在查询中返回的那些行。
查询对 `where` 中的路径默认使用内连接（决策 D36），所以针对客户路径的 `where` 不会处理没有客户的订单，除非 null 守卫保留了它们。

以 `@Id` 结尾的路径（`it.customer?.id`）是外键列。没有子查询，也没有 join。

<a id="batching-for-many-rows"></a>
### 多行的分批处理

`values(list) { ... }` 会写出一条带多个 VALUES 行的语句（规则 U13）。

| 要点 | 规则 |
|---|---|
| 拆分单位 | 绑定值的个数（行数 x 列数），而不是行数 |
| 默认上限 | 每条语句 1000 个绑定值 |
| 返回值 | 所有部分的合计 |
| 事务 | 只允许在事务内部拆分 |
| 空列表 | 不执行 SQL，返回 0 |

Oracle 有 65,535 个绑定值的硬性上限（ORA-01745）。在达到这个上限之前，解析就会变得非常慢
（在一次测试中，21,845 行大约需要 6 分钟）。因此请保持默认上限。

### 持久化上下文不会更新

批量 DML 不经过 JPA 持久化上下文（规则 U16）：

- Hibernate 会在语句执行前刷新（flush）待处理的更改。
- 语句执行后，同一会话中已加载的实体保留的是**旧值**。如果要再次使用它们，请调用 `entityManager.clear()`。
- JPA 的 `cascade` 和 `orphanRemoval` **不会**执行。请先删除子实体，或在数据库中使用 `ON DELETE CASCADE`。

---

<a id="78-all-rules-u1-to-u21-summary"></a>
## 7.8 全部规则 U1 到 U21（总结）

| # | 规则 |
|---|---|
| U1 | 赋值 Lambda **只能包含**对目标、其嵌入路径或关联的 `@Id` 的赋值（setter 调用）。`if`、`when`、循环、局部变量、`++`、作用域函数以及其他调用都是错误。没有任何赋值的 Lambda 也是错误。 |
| U2 | 右侧遵循 `where` 的值规则（[3. 条件](03-where.md)）。捕获值是绑定参数。`a ?: b` 变成 `coalesce(a, b)`。 |
| U3 | 右侧读取的是**旧**值。在同一条语句中先给某列赋值再读取它是错误。 |
| U4 | 给同一物理列赋值两次是错误，跨 `set()` 调用也是如此。 |
| U5 | 没有 `where()` 或 `all()` 就没有 `execute()`。由编译器检查。 |
| U6 | 给不可为空的列（`@Id`、`nullable=false`、基本类型）赋 `null` 是错误。给集合或关联实体的非 `@Id` 属性赋值也是错误。 |
| U7 | 捕获值的每次使用都会得到自己的参数。（在测试中，让 STRING 列和 ORDINAL 列共享一个枚举参数时，会悄无声息地存入错误的值。） |
| U8 | `LocalDateTime.now()` 在应用程序中求值并绑定。不支持数据库时间（`current_timestamp`）。 |
| U9 | DML 中非外键的关联路径：在 `where` 中是关联 `exists`（与查询得到相同的行，D36）；在 SET 的右侧是关联标量子查询。DML 处理的行与查询相同。 |
| U10 | 在 `insert ... select` 中，源路径会变成显式的 `left join`。 |
| U11 | `it.customer = entity` 绑定该实体的 `@Id`。如果 id 为 null，绑定会失败。 |
| U12 | IDENTITY 和 SEQUENCE 的 id 可以省略。TABLE 生成器只能与 `values` 一起使用，不能用于 `insert ... select`。没有生成器时，id 是必需的。其他不可为空的属性也是必需的。 |
| U13 | 多行按绑定值个数拆分（默认 1000）。结果会相加。只能在事务内部进行。 |
| U14 | 一个 `set` Lambda 可以给多个列赋值，`set()` 也可以多次调用。所有赋值按调用顺序合并为一个 SET 列表。 |
| U15 | `@Version` 实体使用 `update versioned`。不要自己给版本赋值。检查 `execute() == 0` 是你的工作。 |
| U16 | 批量 DML 绕过持久化上下文。执行前刷新，执行后实体过时，不会级联。 |
| U17 | 如果 DELETE 的子查询读取同一个实体，lambdaQL 会把它包装在派生表中。（否则 MariaDB 会报错 1093。） |
| U18 | 继承：完全支持 SINGLE_TABLE。JOINED 和 TABLE_PER_CLASS 的 update/delete 使用临时表。向它们的子类插入会被拒绝。 |
| U19 | 查询缓存键包括入口、目标实体、set Lambda、where/all、versioned 以及拆分大小。 |
| U20 | `by[column] = value` / `by.set(column, value)`：列必须是目标上的属性路径。类型在分析时检查。不要在同一个 Lambda 中混用属性赋值和 `by`。 |
| U21 | 每个 Lambda 只分析一次并缓存。跨 `set()` 调用的检查只比较缓存的摘要（在原型中约 0.1 到 0.2 µs）。 |

---

<a id="79-not-in-v01"></a>
## 7.9 v0.1 中不包含的内容

| 项目 | 计划 | 现在怎么做 |
|---|---|---|
| 条件值（`if (c) a else b`、`when`、作为值的切换 `!it.active`） | v0.2（CASE WHEN） | 用不同的 `where` 执行两次更新 |
| 在 Lambda 中用 `if` 选择赋值 | v0.2 | 多次调用 `set()`（U14） |
| `++`、`--`、`apply`、`with`、`also` | v0.2 | 使用 `+= 1` 或直接赋值 |
| 字符串模板或 `+`（`"${it.description}_x"`） | v0.2 | – |
| `insert ... select` 中的显式 join | v0.2 | 使用多对一路径 |
| Upsert（`on conflict`） | v0.2 | – |
| 带 `order by ... limit` 的 DML | 拒绝 | 先查询 id，再按 id 列表删除 |
| `returning` | 拒绝 | DML 之后再查询一次 |
| 数据库时间（`current_timestamp`） | 拒绝 | 绑定应用程序时间（U8） |

本章中的错误都是带有 `[LQL-xxxx]` 代码的 `LambdaQLException` 子类。见 [11. 错误与语言](../11-errors-and-languages.md)。
所有规则集中在一处：[10. 规则与限制](10-rules-and-limits.md)。

---

[Kotlin 路线](README.md) | 上一章：[6. 查询列、聚合与分组](06-select-group.md) | 下一章：[8. 存储过程与数据库函数](08-procedures-functions.md) | [本页的 Java 版本](../java/07-dml.md)
