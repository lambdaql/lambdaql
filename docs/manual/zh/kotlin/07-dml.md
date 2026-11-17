# 7. 更新、插入、删除（Kotlin）

> [!NOTE]
> **状态**
> - **已实现并测试**，在 H2 2.3、MariaDB 10.11 和 Oracle 23.26 上（Hibernate 6.6.5，里程碑 M10，决定 D28 和 D40）：更新、删除、插入、`insert ... select`、upsert，以及（决定 D41）更新和删除的 `limit` 与单行保护 `executeOne()`。大多数测试使用 **Java** Lambda。
> - **已用 Kotlin Lambda 测试**（[`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)，在构建中，H2、MariaDB 和 Oracle）：`set` 中的属性赋值、索引写法 `by[column] = value`、使用 `values` 的 `insert`，以及带 `not exists` 子查询的 `delete`。这些示例标记为 **(已测试)**。
> - **同样已实现并测试（使用 Java Lambda）：** update 和 delete 的 `where` 中的子查询（`sub`）（里程碑 M8，见 [7.4](07-4-delete.md)），以及 `insert ... select` 中的 `groupBy`、`having` 和 `Agg`（里程碑 M9，见 [7.5](07-5-insert.md)）。
> - **尚未实现：** `set` 值中的子查询（上下文对象的 `by.from(...)`），以及复合键实体上的 `limit` / `executeOne()`。
> - 本章的 Kotlin 代码用 Kotlin 语法展示已测试的 Java 语句。HQL 来自 Java 测试。没有 HQL 的示例遵循设计。
> - 完整状态见[路线 README](README.md)。

[Kotlin 路线](README.md) | [手册首页](../README.md) | 上一章：[6. 查询列、聚合与分组](06-select-group.md) | 下一章：[8. 存储过程与数据库函数](08-procedures-functions.md) | [本页的 Java 版本](../java/07-dml.md)

公共页面：[1. 概述](../01-overview.md) · [11. 错误与语言](../11-errors-and-languages.md)

lambdaQL 有五个入口：

| 入口 | 用途 |
|---|---|
| `qb.from(X::class.java)` | 查询（第 3 章到第 6 章） |
| `qb.update(X::class.java)` | 更新行 |
| `qb.insert(X::class.java)` | 插入行 |
| `qb.delete(X::class.java)` | 删除行 |
| `qb.upsert(X::class.java)` | 插入一行；如果已有相同 `@Id` 的行，则修改该行 |

思路与查询相同：

- 每个部分写**一个 Lambda**。不需要静态辅助函数。
- Lambda **永远不会执行**。lambdaQL 读取它的字节码。像 `it.status = 1` 这样的属性赋值会编译为 setter 调用，并变成 `set e0.status = 1`。
- `where` 使用与 [3. 条件](03-where.md) 相同的规则，它修改的行与使用同一个 Lambda 的查询返回的行相同。
- `execute()` 执行语句并返回**受影响的行数**（`Int`）。
- `executeOne()` 执行**最多只能改变一行**的更新或删除（见[单行保护](#one-row-protection)）。
- `orderBy { ... }.limit(n)` 只改变前 `n` 行（见 [Limit](#limit-change-the-first-rows)）。
- `toHql()` 不执行语句，只给出 HQL 和参数。
- 语句需要事务（见[事务](#transactions)）。

<a id="contents"></a>
## 目录

- [7.1 示例实体](#71-example-entities)
- [7.2 更新](#72-update)
- [7.3 两种赋值方式：属性或 `by.set`](#73-two-ways-to-assign-property-or-byset)
- [7.4 删除](#74-delete)
- [7.5 插入](#75-insert)
- [7.6 Upsert](#76-upsert)
- [7.7 DML 的 Kotlin 说明](#77-kotlin-notes-for-dml)
- [7.8 重要规则](#78-important-rules)
- [7.9 全部规则 U1 到 U21（总结）](#79-all-rules-u1-to-u21-summary)
- [7.10 v0.1 中不包含的内容](#710-not-in-v01)

---

<a id="71-example-entities"></a>
## 7.1 示例实体

本章使用 `Order`、`Customer` 和 `Item`（见 [2. 入门](02-getting-started.md#example-entities)）。
在本章中，`Order` 有几个**额外的属性**：

```kotlin
@Entity
@Table(name = "orders")
class Order(
    // ... id, price, status, active, description, customer, updateAt, items as usual
    var total: Double? = null,                 // sum of item prices
    var cnt: Int = 0,                          // a counter (Int, so that += compiles)
    var custName: String? = null,              // copy of the customer name
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

data class Line(val id: Long, val price: Double)   // input rows for insert
```

Java 测试使用同名的 Java 测试实体（[`query/src/test/java/org/lambdaql/query`](https://github.com/codebit0/lambdaql/tree/main/query/src/test/java/org/lambdaql/query)）。

---

<a id="72-update"></a>
## 7.2 更新

→ [7.2 更新](07-2-update.md)

<a id="73-two-ways-to-assign-property-or-byset"></a>
## 7.3 两种赋值方式：属性或 `by.set`

| 写法 | 何时使用 | 示例 |
|---|---|---|
| 属性赋值 | 属性是 `var`（有 setter）。编译器会检查值的类型。 | `set { it.status = 1 }` |
| `by.set(column, value)` | 属性是 `val`（没有 setter），例如 DDD 风格的实体。 | `set { o, by -> by.set(o.status, 1) }` |

```kotlin
val u = qb.update(Order::class.java)
    .set { o, by -> by.set(o.description, "done") }
    .where { it.status == 2 }
// HQL: update Order e0 set e0.description = 'done' where e0.status = 2
```

**关于 SQL 块。** `…` 是缩短的 select 列表（实体的所有列），`?` 是绑定参数。这些行来自 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)。该测试使用 Hibernate 6.6.5 在 H2 2.3、MariaDB 10.11 和 Oracle 23.26 上运行每个查询，如果 Hibernate 的 SQL 发生变化，测试就会失败。 该测试使用 Java Lambda。含义相同的 Kotlin Lambda 生成相同的 HQL，因此 SQL 也相同。

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `update-by`：

```sql
-- H2, MariaDB
update orders o1_0
set description='done'
where o1_0.status=2

-- Oracle
update orders o1_0
set o1_0.description='done'
where o1_0.status=2
```

Oracle 在 `set` 中写出别名（`set o1_0.status=?`）；H2 和 MariaDB 不写。

Kotlin 的索引运算符 `by[column] = value` 调用的是同一个 `set` 方法。它已用 Kotlin Lambda 测试：

**(已测试)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)，region `update-by`：

```kotlin
// the builder form, Kotlin index assignment: by[column] = value
val u = qb.update(Order::class.java)
    .set { o, by -> by[o.description] = "done" }
    .where { it.status.toInt() == 2 }
assertEquals("update Order e0 set e0.description = 'done' where e0.status = 2", u.toHql().hql())
```

测试实体的 `status` 是 `short`，所以测试写成 `it.status.toInt() == 2`。用 Java 写的相同语句在 [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) 中测试（region `update-by`）。

本章的每个示例也都可以用 `by.set` 来写。把调用串起来：Lambda 返回最后一个 `by.set(...)`。

```kotlin
qb.update(Order::class.java)
    .set { o, by -> by.set(o.status, st).set(o.price, o.price!! * 1.1) }
    .where { it.status == 1 }
    .execute()

qb.update(Order::class.java).set { o, by -> by.set(o.address.city, city) }.where { it.id == id }.execute()  // embedded
qb.update(Order::class.java).set { o, by -> by.set(o.customer?.id, cid) }.where { it.id == id }.execute()   // FK
```

使用索引运算符时，每列写一个 `by[column] = value`：`{ o, by -> by[o.status] = st; by[o.price] = o.price!! * 1.1 }`。单列的写法已测试（见上文）；多条语句的写法还没有 Kotlin 测试覆盖。

在设计中，第二个 Lambda 参数是**一个上下文对象**。它同时有 `set(column, value)` 和子查询函数 `from(...)`。
你可以随意把它命名为 `by` 或 `sub`。名字并不重要。
这个对象的子查询部分**尚未实现**。目前这个对象只有 `set`。（`where` 中的子查询可以使用：`where { o, sub -> ... }`，见 [5. 子查询](05-subquery.md)。）

```kotlin
qb.update(Order::class.java)
    .set { o, by ->
        by.set(o.total, by.from(Item::class.java).where { i -> i.order?.id == o.id }.sum { i -> i.price })
    }
    .all()
    .execute()
```

`by.set` 的规则（规则 U20）：

- 列必须是**从目标开始的属性路径**：属性、嵌入路径或关联的 `@Id`。
  捕获值、计算式或源实体的路径都是错误。
- Lambda 必须是一条 `by.set(...)` 调用链。其他语句都是错误。
- `by.set(column, value)` 是泛型的，因此编译器不会检查值是否与列匹配。
  设计中，会在分析时检查列类型与值类型。这项检查尚未实现。
- 不要在**同一个 Lambda 中**混用属性赋值和 `by.set`。你可以在不同的 `set()` 调用中混用它们。

---

<a id="74-delete"></a>
## 7.4 删除

→ [7.4 删除](07-4-delete.md)

<a id="75-insert"></a>
## 7.5 插入

→ [7.5 插入](07-5-insert.md)

<a id="76-upsert"></a>
## 7.6 Upsert

→ [7.6 Upsert](07-6-upsert.md)

<a id="77-kotlin-notes-for-dml"></a>
## 7.7 DML 的 Kotlin 说明

| Kotlin 写法 | 含义 |
|---|---|
| `it.status = x` | setter 调用：`set e0.status = :p1` |
| 右侧的 `!!` | 会被忽略；它只是为了让代码能够编译（D23） |
| `a ?: b` | `coalesce(a, b)` |
| `it.customer?.id = x` | 总是会执行赋值（`?.` 会被忽略） |
| `it.cnt += 1` | 支持（`set e0.cnt = e0.cnt + 1`） |
| `by.set(o.status, x).set(...)` | `by` 写法：调用链，或每条语句一个 `by.set` |
| `by[o.status] = x` | 与 `by.set(o.status, x)` 相同 |
| `by[o.cnt] += 1` | 无法编译（这是有意的：`by` 没有 `get`）。请写 `by[o.cnt] = o.cnt + 1` |
| `it.cnt++`、`it.cnt--` | **v0.1 中不支持**。请使用 `+= 1`。错误消息会提示这一点 |
| `it.apply { }`、`with(it) { }`、`it.also { }` | **v0.1 中不支持**。请直接赋值。错误消息会提示这一点 |
| Lambda 中的 `if` / `when` | **v0.1 中不支持**。请改为多次调用 `set()`（U14） |
| Lambda 中的局部 `val` | 会被拒绝。它可能隐藏 U3 问题 |

更多关于 Kotlin 的内容：[9. Kotlin 说明](09-kotlin-notes.md)。

---

<a id="78-important-rules"></a>
## 7.8 重要规则

<a id="the-right-side-reads-the-old-value"></a>
### 右侧读取的是旧值

在 SQL 中，SET 右侧的每个列都表示更新**之前**的值。lambdaQL 遵循这条规则。

```kotlin
.set { it.price = it.price!! * 2; it.total = it.price }
// error: price is assigned first, then read again
```

如果你**先给某列赋值，然后在同一条语句中又读取它**，lambdaQL 会在你调用 `set(...)` 时抛出 `TranslationException`（`LQL-3042`）（规则 U3）。
原因：在这种情况下，MariaDB 使用**新**值，而 H2 和 Oracle 使用旧值。结果会不同（已检查：MariaDB 为 200，H2 和 Oracle 为 100）。

以下情况没有问题，因为三种数据库的结果一致：

| 写法 | 可以吗？ |
|---|---|
| `it.price = it.price!! * 1.1`（读取自身） | 可以 |
| `it.total = it.price; it.price = 0.0`（先读取，后赋值） | 可以 |
| `it.price = ...; it.total = it.price`（先赋值，后读取） | **错误** |

该检查作用于物理列（嵌入的子列、外键列），也跨越多次 `set()` 调用。

<a id="assign-a-column-only-once"></a>
### 每列只赋值一次

给同一列赋值两次是错误（规则 U4）。这包括先 `it.address = ...` 再 `it.address.city = ...`，
或先 `it.customer = ...` 再 `it.customer?.id = ...`。

<a id="rules-checked-at-analysis-time"></a>
### 在分析时检查的规则

lambdaQL 在读取 Lambda 时检查规则 U1、U3、U4 和 U6，并从 `set(...)` 或 `values(...)` 抛出 `LambdaQLException`（例如 U1 为 `LQL-3037`，U3 为 `LQL-3042`，U4 为 `LQL-3041`）。
这些检查已用 Java Lambda 在 [`AssignmentReaderTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/sem/AssignmentReaderTest.java)（region `rules`）和 [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) 中测试。这些检查目前还没有 Kotlin 测试。

<a id="where-or-all-is-required"></a>
### 必须有 `where` 或 `all()`

`set()` 返回一个**没有** `execute()` 的类型（`UpdateSet`）。只有 `where()` 和 `all()` 返回带有 `execute()` 的类型（`ExecutableUpdate`）。
因此忘记写 `where` 会导致编译错误（规则 U5）。`delete` 也是如此。

<a id="association-paths-same-rows-as-select"></a>
### 关联路径：与查询相同的行

lambdaQL 把 DML 中非外键的关联路径写成**关联标量子查询**，在 `where` 中和 SET 的右侧都是如此（规则 U9）。HQL 来自 [7.2](07-2-update.md#a-value-from-another-table) 中的 Java 测试：

```kotlin
.where { it.customer?.name == "Kim" }
// → where (select s1.name from Customer s1 where s1.id = e0.customer.id) = 'Kim' and e0.customer.id is not null

it.custName = it.customer?.name
// → set e0.custName = (select s1.name from Customer s1 where s1.id = e0.customer.id)
```

为什么？如果 lambdaQL 直接把路径交给 Hibernate，Hibernate 会为每种数据库生成不同的 join SQL
（H2 用 `merge`，MariaDB 用 `update ... join`，Oracle 用内联视图），而且当路径出现在 SET 中时，**没有客户的行会丢失**
（已检查：使用 `.all()` 时，10 行中只有 9 行被更新）。
lambdaQL 自己编写这些子查询。这样语句只读取自己的表，所以 MariaDB 对读取目标表的限制（错误 1093）不会生效。

查询对 `where` 中的路径使用内连接（决策 D36）。因此在 DML 中，lambdaQL 会为这样的路径加上 `e0.customer.id is not null`。
这样 `update` 和 `delete` 处理的**正好是** `qb.from(X::class.java).where { 同一个 Lambda }` 返回的那些行（已用 Java 测试：把删除的行与查询结果进行比较）。
像 `it.customer == null || ...` 这样的 null 守卫会在查询中保留没有客户的行，所以该路径不会加上 `is not null` 检查。

以 `@Id` 结尾的路径（`it.customer?.id`）是外键列。没有子查询，也没有 join。

<a id="batching-for-many-rows"></a>
### 多行的分批处理

`values(list) { ... }` 会写出一条带多个 VALUES 行的语句（规则 U13）。

| 要点 | 规则 |
|---|---|
| 拆分单位 | 值的个数（行数 x 列数），而不是行数 |
| 上限 | 每条语句最多 500 个值（固定） |
| 返回值 | 所有语句的合计 |
| 事务 | 所有语句在一个事务中执行 |
| 空列表 | 不执行 SQL，返回 0（已用 Java 测试） |

Oracle 有 65,535 个绑定值的硬性上限（ORA-01745）。远在达到这个上限之前，解析就会变得非常慢
（设计阶段的测试中，21,845 行大约需要 6 分钟）。因此上限设得较小。

<a id="limit-change-the-first-rows"></a>
### Limit：改变前几行

在 `where { ... }` 或 `all()` 之后写 `orderBy { ... }.limit(n)`，更新或删除最多改变 `n` 行：即 `qb.from(X::class.java).where { same }.orderBy { same }` 的前 `n` 行。

```kotlin
qb.delete(Order::class.java).where { it.status == 1 }.orderBy { o, by -> by.asc(o.price) }.limit(100).execute()
```

| 要点 | 规则 |
|---|---|
| 顺序 | `orderBy` 的键，然后是 id（规则 S9）。没有 `orderBy` 时按 id |
| 没有 `limit` 的 `orderBy` | 错误（`IllegalStateException`） |
| 渲染 | `e0.id in (select t.id from (select x0.id as id from X x0 ... order by ... limit n) t)` |
| 行 | 内层 select 遵循查询的规则，包括连接（D36），因此行与查询相同 |
| MariaDB | 派生表 `t` 避免错误 1093 和 1235 |

示例和各数据库的 SQL：[7.4](07-4-delete.md#delete-the-first-rows-limit)（删除）和 [7.2](07-2-update.md#update-the-first-rows-limit)（更新）。

<a id="one-row-protection"></a>
### 单行保护

用 `executeOne()` 代替 `execute()`，**最多只改变一行**：

1. lambdaQL 读取匹配行的 id，最多 2 个。
2. 有 2 个 id 时，什么都不改变，并抛出 `NonUniqueResultException`。
3. 有 1 个 id 时，用 `id = :p and (你的条件)` 改变该行。

它绝不会改变超过一行，也不依赖回滚。结果为 0 或 1。`executeOne()` 与 `orderBy` 或 `limit` 一起使用是错误（`IllegalStateException`）。
示例：[7.4](07-4-delete.md#delete-exactly-one-row-executeone)。在 Spring 仓库中：`deleteOne` 和 `updateOne`（见 [12.4](12-spring-data-jpa.md#one-row-protection-deleteone-and-updateone)）。

<a id="transactions"></a>
### 事务

批量语句需要事务。只有使用 `EntityManagerFactory` 构造函数时，lambdaQL 才会自己打开事务。

| 构建器 | 事务 |
|---|---|
| `QueryBuilder(entityManagerFactory)` | `execute()` 和 `executeOne()` 打开自己的事务并提交。出错时回滚 |
| 使用 `EntityManager` 的构建器：`QueryBuilder(entityManager)`、Spring 的 `QueryBuilder` bean、repository 执行器 | 使用调用方的事务（`@Transactional`，或该 `EntityManager` 中处于活动状态的事务）。没有事务时，语句在发送任何 SQL 之前以 `TransactionRequiredException` 失败（用 Java 测试）。在 Spring 的 repository 调用中，它会变为 `InvalidDataAccessApiUsageException`（见 [12.6](12-spring-data-jpa.md#126-transactions-and-exceptions)） |

<a id="the-persistence-context-is-not-updated"></a>
### 持久化上下文不会更新

批量 DML 不经过 JPA 持久化上下文（规则 U16）：

- Hibernate 会在语句执行前刷新（flush）待处理的更改。
- 语句执行后，同一个 `EntityManager` 中已加载的实体保留的是**旧值**。如果要再次使用它们，请调用 `entityManager.clear()`。
- JPA 的 `cascade` 和 `orphanRemoval` **不会**执行。请先删除子实体，或在数据库中使用 `ON DELETE CASCADE`。

---

<a id="79-all-rules-u1-to-u21-summary"></a>
## 7.9 全部规则 U1 到 U21（总结）

lambdaQL 读取 Lambda 时检查：U1、U3、U4、U6（已用 Java Lambda 测试）。由编译器检查：U5。
U11、U12、U15 和 U20 的 lambdaQL 检查尚未实现。U17（MariaDB 中对同一张表的子查询的包装）尚未实现。

| # | 规则 |
|---|---|
| U1 | 赋值 Lambda **只能包含**对目标、其嵌入路径或关联的 `@Id` 的赋值（setter 调用）。`if`、`when`、循环、局部变量、`++`、作用域函数以及其他调用都是错误。没有任何赋值的 Lambda 也是错误。 |
| U2 | 右侧遵循 `where` 的值规则（[3. 条件](03-where.md)）。捕获值是绑定参数，Lambda 中的常量是字面量（D37）。`a ?: b` 变成 `coalesce(a, b)`。 |
| U3 | 右侧读取的是**旧**值。在同一条语句中先给某列赋值再读取它是错误。 |
| U4 | 给同一物理列赋值两次是错误，跨 `set()` 调用也是如此。 |
| U5 | 没有 `where()` 或 `all()` 就没有 `execute()`。由编译器检查。 |
| U6 | 给不可为空的列（`@Id`、`nullable=false`、基本类型）赋 `null` 是错误。给集合或关联实体的非 `@Id` 属性赋值也是错误。 |
| U7 | 捕获值的每次使用都会得到自己的参数。（在测试中，让 STRING 列和 ORDINAL 列共享一个枚举参数时，会悄无声息地存入错误的值。） |
| U8 | `LocalDateTime.now()` 在应用程序中求值并绑定。不支持数据库时间（`current_timestamp`）。 |
| U9 | DML 中非外键的关联路径会变成关联标量子查询，在 `where` 中和 SET 的右侧都是如此。在 `where` 中，查询会做内连接的路径（D36）还会加上 `customer.id is not null`。DML 处理的行与查询相同。 |
| U10 | 在 `insert ... select` 中，源路径会变成显式的 `left join`。 |
| U11 | `it.customer = entity` 绑定该实体的 `@Id`。如果 id 为 null，绑定会失败。 |
| U12 | IDENTITY 和 SEQUENCE 的 id 可以省略。TABLE 生成器只能与 `values` 一起使用，不能用于 `insert ... select`。没有生成器时，id 是必需的。其他不可为空的属性也是必需的。 |
| U13 | 多行会被拆分：每条语句最多 500 个值。结果会相加。所有语句在一个事务中执行。 |
| U14 | 一个 `set` Lambda 可以给多个列赋值，`set()` 也可以多次调用。所有赋值按调用顺序合并为一个 SET 列表。 |
| U15 | `@Version` 实体使用 `update versioned`。不要自己给版本赋值。检查 `execute() == 0` 是你的工作。 |
| U16 | 批量 DML 绕过持久化上下文。执行前刷新，执行后实体过时，不会级联。 |
| U17 | 如果 DELETE 的子查询读取同一个实体，lambdaQL 会把它包装在派生表中。（否则 MariaDB 会报错 1093。） |
| U18 | 继承：完全支持 SINGLE_TABLE。JOINED 和 TABLE_PER_CLASS 的 update/delete 使用临时表。向它们的子类插入会被拒绝。 |
| U19 | 查询缓存键包括入口、目标实体、set Lambda、where/all、versioned 以及拆分大小。 |
| U20 | `by.set(column, value)`：列必须是目标上的属性路径。类型在分析时检查。不要在同一个 Lambda 中混用属性赋值和 `by.set`。 |
| U21 | 每个 Lambda 只分析一次并缓存。跨 `set()` 调用的检查只比较缓存的摘要（在原型中约 0.1 到 0.2 µs）。 |

Upsert 的规则（决定 D40）见 [7.6 Upsert](07-6-upsert.md#rules)。

---

<a id="710-not-in-v01"></a>
## 7.10 v0.1 中不包含的内容

| 项目 | 计划 | 现在怎么做 |
|---|---|---|
| 条件值（`if (c) a else b`、`when`、作为值的切换 `!it.active`） | v0.2（CASE WHEN） | 用不同的 `where` 执行两次更新 |
| 在 Lambda 中用 `if` 选择赋值 | v0.2 | 多次调用 `set()`（U14） |
| `++`、`--`、`apply`、`with`、`also` | v0.2 | 使用 `+= 1` 或直接赋值 |
| 字符串模板或 `+`（`"${it.description}_x"`） | v0.2 | – |
| `insert ... select` 中的显式 join | v0.2 | 使用多对一路径 |
| `returning` | 拒绝 | DML 之后再查询一次 |
| 数据库时间（`current_timestamp`） | 拒绝 | 绑定应用程序时间（U8） |

违反规则时会抛出带 `[LQL-xxxx]` 错误码的 `LambdaQLException`（大多数规则为 `TranslationException`，赋值 Lambda 中的条件或局部变量为 `UnsupportedLambdaShapeException`）。见 [11. 错误与语言](../11-errors-and-languages.md)。
所有规则集中在一处：[10. 规则与限制](10-rules-and-limits.md)。

---

[Kotlin 路线](README.md) | 上一章：[6. 查询列、聚合与分组](06-select-group.md) | 下一章：[8. 存储过程与数据库函数](08-procedures-functions.md) | [本页的 Java 版本](../java/07-dml.md)
