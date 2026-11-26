<a id="7-update-insert-delete-kotlin"></a>
# 7. 更新、插入、删除（Kotlin）

> [!IMPORTANT]
> 本页比韩文手册旧。最新规则请参见[韩文页面](../../ko/kotlin/07-dml.md)。

[Kotlin 路线](README.md) | [手册首页](../README.md) | 上一章：[6. 查询列、聚合与分组](06-select-group.md) | 下一章：[8. 存储过程与数据库函数](08-procedures-functions.md) | [本页的 Java 版本](../java/07-dml.md)

公共页面：[1. 概述](../01-overview.md) · [11. 错误与语言](../11-errors-and-languages.md)

lambdaQL 有五个入口。

| 入口 | 用途 |
|---|---|
| `qb.from(X::class.java)` | 查询（第 3 章到第 6 章） |
| `qb.update(X::class.java)` | 更新行 |
| `qb.insert(X::class.java)` | 插入行 |
| `qb.delete(X::class.java)` | 删除行 |
| `qb.upsert(X::class.java)` | 插入行。如果已有相同 `@Id` 的行，则更新该行 |

基本思路与查询相同。

- 每个部分写成**一个 Lambda 表达式**。对 `val` 属性用 `Dml.value` 赋值（[7.3](#73-two-ways-to-assign-property-or-dmlvalue)）。
- lambdaQL 不调用 Lambda 表达式，而是分析其字节 opcode。像 `it.status = 1` 这样的属性赋值会编译为 setter 调用，并变成 `set e0.status = 1`。
- `where` 使用与 [3. 条件（`where`）](03-where.md) 相同的规则。目标行与用同一 Lambda 表达式查询到的行相同。
- `execute()` 执行语句并返回**受影响的行数**（`Int`）。
- `executeOne()` 执行**最多只能改变一行**的更新或删除（见[单行保护](#one-row-protection)）。
- `orderBy { ... }.limit(n)` 只改变前 `n` 行（见 [limit](#limit-change-the-first-rows)）。
- `toHql()` 不执行语句，只返回 HQL 和参数。
- 执行语句需要事务（见[事务](#transactions)）。

<a id="contents"></a>
## 目录

- [7.1 示例实体](#71-example-entities)
- [7.2 更新](#72-update)
- [7.3 两种赋值方式：属性或 `Dml.value`](#73-two-ways-to-assign-property-or-dmlvalue)
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
示例在 Kotlin 中直接使用 [`query/src/test/java/org/lambdaql/query`](https://github.com/lambdaql/lambdaql/tree/main/query/src/test/java/org/lambdaql/query) 中的 Java 示例实体。
Kotlin 把 Java 的 getter 和 setter 视为属性，因此像 `it.status = st` 这样赋值。

| 类 | 本章使用的属性 |
|---|---|
| `Order` | `id`（IDENTITY）、`price`（`double`）、`status`（`short`）、`active`（`boolean`，在 Kotlin 中为 `isActive`）、`description`、`updateAt`、`kind`（enum `Order.Kind`）、`customer`。可为 NULL 的 `total`（`Double`）、`cnt`（`Integer`）、`custName`、`address`（`@Embedded` `Address`：`city`、`zip`） |
| `VOrder` | `id`、`status`（`Integer`）、`version`（`@Version`） |
| `Archive` | `id`（无生成器）、`price`、`customerName` |
| `Summary` | `customerId`（`Integer`）、`total`、`cnt`（`Long`） |
| `IdOrder` | `id`（IDENTITY）、`price`、`status` |

列表插入使用的 `Line` 和 `Ref` 是普通的数据类（例如 `data class Line(val id: Long, val price: Double)`）。
`status` 是 `short`，因此条件写成 `it.status.toInt() == 1`。

---

<a id="72-update"></a>
## 7.2 更新

→ [7.2 更新](07-2-update.md)

<a id="73-two-ways-to-assign-property-or-dmlvalue"></a>
## 7.3 两种赋值方式：属性或 `Dml.value`

| 方式 | 何时使用 | 示例 |
|---|---|---|
| 属性赋值 | 属性是 `var`（有 setter）时。编译器会检查值的类型。 | `set { it.status = 1 }` |
| `Dml.value(column, value)` | 属性是 `val`（没有 setter）时。例如 DDD 风格的实体。 | `set { o -> value(o.status, 1) }` |

`value` 是函数 `org.lambdaql.Dml.value`。用 `import org.lambdaql.Dml.value` 导入后，在单参数 Lambda 表达式中使用。

<!-- region: KotlinQueryTest.kt#update-value -->
```kotlin
// entities without setters (val): Dml.value(column, value)
val u = qb.update(Order::class.java)
    .set { o -> value(o.description, "done") }
    .where { it.status.toInt() == 2 }
assertEquals("update Order e0 set e0.description = 'done' where e0.status = 2", u.toHql().hql())
```

示例实体的 `status` 是 `short`，因此条件写成 `it.status.toInt() == 2`。同一语句的 Java 写法见[本页的 Java 版本](../java/07-dml.md#73-two-ways-to-assign-setter-or-dmlvalue)。

**各数据库的 SQL**

<!-- sql: update-value -->
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

Oracle 在 `set` 中写出别名（`set o1_0.status=?`）。H2 和 MariaDB 不写。

本章的所有示例也都可以用 `value` 来写。每条 Kotlin 语句写一个 `value` 调用。

<!-- region: ManualKotlinDmlTest.kt#value-forms -->
```kotlin
// Dml.value(column, value) for properties without a setter (val); one value call per statement
val u = qb.update(Order::class.java)
    .set { o -> value(o.status, st); value(o.price, o.price * 1.1) }
    .where { it.status.toInt() == 1 }
assertEquals("update Order e0 set e0.status = :p1, e0.price = (e0.price * 1.1) where e0.status = 1", u.toHql().hql())
val embedded = qb.update(Order::class.java).set { o -> value(o.address.city, city) }.where { it.id == id }
assertEquals("update Order e0 set e0.address.city = :p1 where e0.id = :p2", embedded.toHql().hql())
val fk = qb.update(Order::class.java).set { o -> value(o.customer?.id, cid) }.where { it.id == id }
assertEquals("update Order e0 set e0.customer.id = :p1 where e0.id = :p2", fk.toHql().hql())
```

`value` 的规则：

- 第一个参数必须是**从目标开始的属性读取（getter 调用）**：属性、嵌入路径或关联的 `@Id`。
  捕获值、计算式或源实体的路径是分析时错误（`LQL-3039`）。
- 同一属性的 setter 规则同样适用。`value(o.x, v)` 与 `o.x = v` 相同，因此适用 U1 和 U6，关联只能赋值 `@Id`（`LQL-3050`）。
- 属性赋值和 `value` **可以在同一个 Lambda 表达式中混用**。
- `value(column, value)` 是泛型的，因此编译器不会检查值是否与列匹配。
  lambdaQL 在分析时比对列类型与值类型，不匹配时报 `LQL-3065` 错误。
  数字与数字列匹配，但小数与整数列不匹配。类型未知的值（`Any`、泛型聚合的 `Number`）不做比对。
- 在拥有自己的 `value(...)` 函数的类中，导入的 `value` 会被遮蔽。在那里请写 `Dml.value(...)`。
- `val` 属性用 `value(...)` 赋值。

<a id="subquery-in-a-set-value"></a>
### `set` 值中的子查询

赋值 Lambda 表达式的第二个参数是子查询的起点 `sub`。写法与 `where { o, sub -> ... }` 相同。
在 `insert ... select` 中，第三个参数是 `sub`（见 [7.5](07-5-insert.md)）。

<!-- region: KotlinQueryTest.kt#update-sub -->
```kotlin
// the second parameter starts a subquery, as in where { o, sub -> ... }
val u = qb.update(Order::class.java)
    .set { o, sub -> o.price = sub.from(Item::class.java).where { i -> i.order?.id == o.id }.sum { it.price } }
    .where { o, sub -> sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
assertEquals("update Order e0 set e0.price = (select sum(s1.price) from Item s1 where s1.order.id = e0.id)"
        + " where exists (select 1 from Item s1 where s1.order.id = e0.id)", u.toHql().hql())
```

- 其他表的聚合和同一张表的聚合都可以用作 `set` 值（在 H2、MariaDB 10.11、Oracle 23.26 上实测）。在 `update` 中，MariaDB 也不会报错误 1093。
- 没有匹配的行时，`sum` 等返回 NULL。如果是 NOT NULL 列，数据库会报错，因此请加上 `?: 0.0`。它会变成 `coalesce(...)`（见 [7.2](07-2-update.md#an-aggregate-from-a-subquery)）。
- insert 的 `values { a, sub -> ... }` 和 upsert（`onConflict { ... }.update { a, sub -> ... }`）也以同样的方式使用子查询（见 [7.5](07-5-insert.md#a-subquery-in-a-value)）。列表形式像 `values(list) { a, l, sub -> ... }` 这样，第三个参数是 `sub`。

示例代码：[`KotlinQueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)（`update-value`、`update-sub`）、[`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`value-forms`）。

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
| `a ?: b`（字符串或数字值） | `coalesce(a, b)` |
| `it.customer?.id = x` | 总是执行赋值（`?.` 会被忽略） |
| `it.cnt += 1` | 支持（`set e0.cnt = e0.cnt + 1`） |
| `value(o.status, x)` | 用于 `val` 属性的赋值。每条语句一个 `value` |
| `value(o.cnt, o.cnt + 1)` | 支持（`set e0.cnt = e0.cnt + 1`） |
| `it.cnt++`、`it.cnt--` | 拒绝（`LQL-2003`）。请使用 `+= 1` |
| `it.apply { }`、`with(it) { }`、`it.also { }` | 拒绝（`LQL-2003`）。请直接赋值 |
| 选择值的 `if` / `when`、切换 `!it.isActive` | `case when`（[3.5.1](03-where.md#351-values-chosen-by-a-condition)） |
| 选择是否赋值的 `if (c) it.price = x` | 拒绝（`LQL-2009`）。请把条件写在 `where` 中，或多次调用 `set()` |
| Lambda 表达式中的局部 `val` | 拒绝（`LQL-2003`）。它可能隐藏再次读取已赋值列的问题 |

<!-- region: KotlinCaseWhenTest.kt#set-toggle -->
```kotlin
// a toggle and a number elvis are values too
val toggle = qb.update(Order::class.java).set { it.isActive = !it.isActive }.all()
assertEquals("update Order e0 set e0.active = case when e0.active = false then true else false end", toggle.toHql().hql())
val zero = qb.update(Order::class.java).set { it.cnt = it.cnt ?: 0 }.all()
assertEquals("update Order e0 set e0.cnt = coalesce(e0.cnt, 0)", zero.toHql().hql())
// an if that chooses whether to assign is rejected (LQL-2009): put that condition in where
val e = assertThrows(LambdaQLException::class.java) {
    qb.update(Order::class.java).set { if (it.isActive) it.price = 1.0 }
}
assertEquals("LQL-2009", e.code())
```

示例代码：[`KotlinCaseWhenTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinCaseWhenTest.kt)（`set-toggle`）。

更多关于 Kotlin 的内容：[9. Kotlin 说明](09-kotlin-notes.md)。

---

<a id="78-important-rules"></a>
## 7.8 重要规则

<a id="the-right-side-reads-the-old-value"></a>
### 右侧值的基准：旧值

在 SQL 中，SET 右侧的每个列都表示更新**之前**的值。lambdaQL 遵循这条规则。

<!-- region: ManualKotlinDmlTest.kt#old-value -->
```kotlin
// the right side reads the value before the change
val self = qb.update(Order::class.java).set { it.price = it.price * 1.1 }.all()             // reads itself: allowed
val readFirst = qb.update(Order::class.java).set { it.total = it.price; it.price = 0.0 }.all() // read, then assign: allowed
assertEquals("update Order e0 set e0.total = e0.price, e0.price = 0.0", readFirst.toHql().hql())
// assign, then read again: an error at set(), because MariaDB would read the new value
val error = assertThrows(LambdaQLException::class.java) {
    qb.update(Order::class.java).set { it.price = it.price * 2; it.total = it.price }
}
assertEquals("LQL-3042", error.code())
```

如果**先给某列赋值，然后在同一条语句中又读取它**，lambdaQL 会在调用 `set(...)` 时抛出 `TranslationException`（`LQL-3042`）。
原因：在这种情况下，MariaDB 使用**新**值，而 H2 和 Oracle 使用旧值。结果会不同（实测：MariaDB 为 200，H2 和 Oracle 为 100）。

以下情况三种数据库的结果一致，因此允许。

| 写法 | 是否允许 |
|---|---|
| `it.price = it.price * 1.1`（读取自身） | 允许 |
| `it.total = it.price; it.price = 0.0`（先读取，后赋值） | 允许 |
| `it.price = ...; it.total = it.price`（先赋值，后读取） | **错误** |

该检查以物理列（嵌入的子列、外键列）为单位进行，也跨越多次 `set()` 调用。

<a id="assign-a-column-only-once"></a>
### 每列只赋值一次

给同一列赋值两次是错误。这包括先 `it.address = ...` 再 `it.address.city = ...`，
或先 `it.customer = ...` 再 `it.customer?.id = ...`。

<a id="rules-checked-at-analysis-time"></a>
### 分析时的规则检查

lambdaQL 在读取 Lambda 表达式时检查赋值规则，并从 `set(...)` 或 `values(...)` 抛出 `LambdaQLException`（例如 `LQL-3037`、`LQL-3042`、`LQL-3041`）。
用 Kotlin Lambda 表达式编写的以下写法会在读取 Lambda 表达式时被拒绝。再次读取已赋值的列、对同一列赋值两次这两种写法的示例见[右侧值的基准：旧值](#the-right-side-reads-the-old-value)和 [7.2](07-2-update.md#many-set-calls-patch)。

<!-- region: ManualKotlinDmlTest.kt#assign-rules -->
```kotlin
// forms that the assignment rules reject when the lambda is read
val increment = assertThrows(LambdaQLException::class.java) {
    qb.update(Order::class.java).set { it.cnt++ }                       // ++: write += 1
}
val scope = assertThrows(LambdaQLException::class.java) {
    qb.update(Order::class.java).set { it.apply { status = 1 } }        // apply: assign directly
}
val other = assertThrows(LambdaQLException::class.java) {
    qb.update(Order::class.java).set { it.customer?.name = "x" }        // a property of another table
}
val noNull = assertThrows(LambdaQLException::class.java) {
    qb.update(Archive::class.java).set { it.id = null }.all()            // null into the @Id
}
```

`++`、作用域函数和局部变量是 `UnsupportedLambdaShapeException`（`LQL-2003`），关联的非 `@Id` 属性是 `LQL-3050`，给 `@Id` 赋 `null` 是 `LQL-3044`。

<a id="where-or-all-is-required"></a>
### 必须指定 `where` 或 `all()`

要执行 update 和 delete，必须指定 `where(...)` 或 `all()`。

<a id="association-paths-same-rows-as-select"></a>
### 关联路径：与查询相同的行

lambdaQL 把 DML 中非外键的关联路径写成**关联标量子查询**，在 `where` 中和 SET 的右侧都是如此。
例如：`where { it.customer?.name == "Kim" }` 变为 `where (select s1.name from Customer s1 where s1.id = fk(e0.customer)) = 'Kim' and e0.customer.id is not null`，`it.custName = it.customer?.name` 变为 `set e0.custName = (select s1.name from Customer s1 where s1.id = fk(e0.customer))`。示例见 [7.2](07-2-update.md#a-value-from-another-table)。

原因如下。如果 lambdaQL 直接把路径交给 Hibernate，Hibernate 会为每种数据库生成不同的 join SQL
（H2 用 `merge`，MariaDB 用 `update ... join`，Oracle 用内联视图）。另外，当路径出现在 SET 中时，**没有客户的行会被遗漏**
（实测：使用 `.all()` 时，10 行中只有 9 行被更新）。
lambdaQL 自己编写子查询。这样语句只读取自己的表，因此不受 MariaDB 对读取目标表的限制（错误 1093）。

对于在查询的 `where` 中成为内连接的路径，lambdaQL 在 DML 中会加上 `e0.customer.id is not null`。
这样 `update` 和 `delete` 处理的**正好是** `qb.from(X::class.java).where { 同一个 Lambda 表达式 }` 返回的那些行（把删除的行与查询结果进行比较）。
对于像 null 守卫（`it.customer == null || it.customer!!.name == ...`）或 `it.status == 1 || ...` 这样在查询中成为 `left join` 的路径，不添加这个检查。用 `?.` 写还是用 `!!` 写都一样。此时 DML 处理的也是与查询相同的行（见 [7.2](07-2-update.md)、[7.4](07-4-delete.md)）。

以 `@Id` 结尾的路径（`it.customer?.id`）是外键列。没有子查询，也没有 join。

<a id="batching-for-many-rows"></a>
### 多行的分批处理

`values(list) { ... }` 会写出一条带多个 VALUES 行的语句。

| 项目 | 规则 |
|---|---|
| 拆分单位 | 值的个数（行数 x 列数），而不是行数 |
| 上限 | 每条语句最多 500 个值（固定） |
| 返回值 | 所有语句的合计 |
| 事务 | 所有语句在一个事务中执行 |
| 空列表 | 不执行 SQL，返回 0 |

Oracle 有 65,535 个绑定值的硬性上限（ORA-01745）。远在达到这个上限之前，解析就会变得非常慢
（设计阶段实测：21,845 行大约需要 6 分钟）。因此上限设得较小。

<a id="limit-change-the-first-rows"></a>
### limit：改变前几行

在 `where { ... }` 或 `all()` 之后加上 `orderBy { ... }.limit(n)`，更新或删除最多只改变 `n` 行。目标是 `qb.from(X::class.java).where { same }.orderBy { same }` 的前 `n` 行。

| 项目 | 规则 |
|---|---|
| 顺序 | `orderBy` 的键，然后是 id。没有 `orderBy` 时按 id |
| 没有 `limit` 的 `orderBy` | 错误（`IllegalStateException`） |
| 渲染 | `e0.id in (select t.id from (select x0.id as id from X x0 ... order by ... limit n) t)` |
| 目标行 | 内层 select 包括连接在内都遵循查询的规则，因此行与查询相同 |
| MariaDB | 用派生表 `t` 避开错误 1093 和 1235 |

示例和各数据库的 SQL：[7.4](07-4-delete.md#delete-the-first-rows-limit)（delete）、[7.2](07-2-update.md#update-the-first-rows-limit)（update）。

<a id="one-row-protection"></a>
### 单行保护

用 `executeOne()` 代替 `execute()`，**最多只改变一行**。

1. lambdaQL 读取匹配行的 id，最多 2 个。
2. 有 2 个 id 时，什么都不改变，并抛出 `NonUniqueResultException`。
3. 有 1 个 id 时，用 `id = :p and (条件)` 改变该行。

不会改变两行及以上，也不依赖回滚。结果为 0 或 1。`executeOne()` 与 `orderBy` 或 `limit` 一起使用是错误（`IllegalStateException`）。

示例：[7.4](07-4-delete.md#delete-exactly-one-row-executeone)。在 Spring 仓库中使用 `deleteOne` 和 `updateOne`（见 [12.4](12-spring-data-jpa.md#one-row-protection-deleteone-and-updateone)）。

<a id="transactions"></a>
### 事务

批量语句需要事务。只有使用 `EntityManagerFactory` 构造函数的构建器，lambdaQL 才会自己打开事务。

| 构建器 | 事务 |
|---|---|
| `QueryBuilder(entityManagerFactory)` | `execute()` 和 `executeOne()` 打开自己的事务并提交。出错时回滚 |
| 使用 `EntityManager` 的构建器：`QueryBuilder(entityManager)`、Spring 的 `QueryBuilder` bean、仓库执行器 | 调用方的事务（`@Transactional`，或该 `EntityManager` 中处于活动状态的事务）。没有事务时，在发送 SQL 之前以 `TransactionRequiredException` 失败（Java）。在 Spring 仓库调用中会变为 `InvalidDataAccessApiUsageException`（见 [12.6](12-spring-data-jpa.md#126-transactions-and-exceptions)） |

<a id="the-persistence-context-is-not-updated"></a>
### 持久化上下文不会更新

批量 DML 不经过 JPA 持久化上下文。

- Hibernate 会在语句执行前刷新（flush）待处理的更改。
- 语句执行后，同一个 `EntityManager` 中已加载的实体保留的是**旧值**。如果要再次使用这些实体，请调用 `entityManager.clear()`。
- JPA 的 `cascade` 和 `orphanRemoval` **不会**执行。请先删除子实体，或在数据库中使用 `ON DELETE CASCADE`。

<a id="sqlrestriction-and-softdelete"></a>
### `@SQLRestriction` 与 `@SoftDelete`

Hibernate 会把实体的 `@SQLRestriction` 条件和 `@SoftDelete` 条件加到 lambdaQL 生成的每条语句上（在 MariaDB 10.11、Oracle 23.26 上实测，H2 未测量）。
批量 `update` 和 `delete` 也一样。例如：`where m1_0.order_id=? and (m1_0.hidden = 0)`。

`@SoftDelete` 实体的 `delete` 会变成 `update … set deleted = 1 where … and deleted = 0`。行不会被真正删除。
lambdaQL 不单独处理这些条件。

示例代码：[`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`old-value`、`assign-rules`）。

---

<a id="79-all-rules-u1-to-u21-summary"></a>
## 7.9 全部规则 U1 到 U21（总结）

lambdaQL 读取 Lambda 表达式时检查的规则：U1、U3、U4、U6、U15。
lambdaQL 会拒绝对 `@Version` 属性的赋值（`LQL-3068`）。U11 和 U12 不做检查。

| # | 规则 |
|---|---|
| U1 | 赋值 Lambda 表达式**只包含**对目标、目标的嵌入路径或关联的 `@Id` 的**赋值（setter 调用）和 `Dml.value(column, value)`**（`LQL-3037`）。`if`、`when`、循环、局部变量、`++`、作用域函数以及其他调用都是错误。没有任何赋值的 Lambda 表达式也是错误。 |
| U2 | 右侧遵循 `where` 的值规则（[3. 条件（`where`）](03-where.md)）。捕获值是绑定参数，写在 Lambda 表达式中的常量是字面量。`a ?: b` 变成 `coalesce(a, b)`。 |
| U3 | 右侧读取的是**旧**值。在同一条语句中先给某列赋值再读取它是错误。 |
| U4 | 给同一物理列赋值两次是错误。跨 `set()` 调用也是如此。 |
| U5 | 没有 `where()` 或 `all()` 就没有 `execute()`。由编译器检查。 |
| U6 | 给非可选的列（`@Id`、`nullable=false`、基本类型）赋 `null` 是错误。给集合或关联实体的非 `@Id` 属性赋值也是错误。 |
| U7 | 捕获值的每次使用都会得到自己的参数。（STRING 列和 ORDINAL 列共享一个 enum 参数时，H2 和 MariaDB 不报错却存入错误的值。） |
| U8 | `LocalDateTime.now()` 在应用程序中计算并绑定。不支持数据库时间（`current_timestamp`）。 |
| U9 | DML 中非外键的关联路径在 `where` 中和 SET 的右侧都会变成关联标量子查询。在 `where` 中，查询时会做内连接的路径加上 `customer.id is not null`，会做 `left join` 的路径不加。DML 处理的行与查询相同。 |
| U10 | 在 `insert ... select` 中，源路径会变成显式的 `left join`。 |
| U11 | `it.customer = entity` 绑定该实体的 `@Id`。如果 id 为 null，绑定会失败。 |
| U12 | IDENTITY 和 SEQUENCE 的 id 可以省略。TABLE 生成器只能用于 `values`，不能用于 `insert ... select`。没有生成器时，id 是必需的。其他非可选的属性也是必需的。 |
| U13 | 多行按每条语句最多 500 个值拆分。结果会相加。所有语句在一个事务中执行。 |
| U14 | 一个 `set` Lambda 表达式可以给多个列赋值，`set()` 也可以多次调用。所有赋值按调用顺序合并为一个 SET 列表。属性赋值和 `Dml.value` 也可以在同一个 Lambda 表达式中混用。 |
| U15 | `@Version` 实体使用 `update versioned`。直接给版本赋值会得到 `LQL-3068`。检查 `execute() == 0` 由使用者负责。 |
| U16 | 批量 DML 绕过持久化上下文。执行前刷新，执行后实体过时，不会级联。 |
| U17 | 如果 DELETE 的子查询读取目标实体（或同一继承层次），lambdaQL 会先通过派生表读取目标 id：`d0.id in (select t.id from (select e0.id as id from … where <condition>) t)`。否则 MariaDB 会报错误 1093。关联子查询也能使用；`update` 和复合 id 实体不做包装（见 [7.4](07-4-delete.md#delete-with-a-subquery-on-the-same-table)）。 |
| U18 | 继承：完全支持 SINGLE_TABLE。JOINED 和 TABLE_PER_CLASS 的 update/delete 使用临时表。向它们的子类插入会被拒绝。 |
| U19 | 查询缓存键包括入口、目标实体、set Lambda 表达式、where/all、versioned 以及拆分大小。 |
| U20 | `Dml.value(column, value)`：第一个参数必须是从目标（或 `insert ... select` 的目标 `a`）开始的属性读取（`LQL-3039`）。同一属性的 setter 规则（U1、U6，关联只能赋值 `@Id`）同样适用。值的类型与列类型比对（`LQL-3065`）。可以与属性赋值混用。 |
| U21 | 每个 Lambda 表达式只分析一次并缓存。跨 `set()` 调用的检查只比较缓存的摘要（在原型中约 0.1 到 0.2 µs）。 |

Upsert 的规则见 [7.6 Upsert](07-6-upsert.md#rules)。

---

<a id="710-not-in-v01"></a>
## 7.10 v0.1 中不包含的内容

| 项目 | 现在可用的方法 |
|---|---|
| 在 Lambda 表达式中用 `if` 选择赋值：`LQL-2009` | 多次调用 `set()` |
| `++`、`--`、`apply`、`with`、`also`：`LQL-2003` | 使用 `+= 1` 或直接赋值 |
| 字符串模板或 `+`（`"${it.description}_x"`）：`LQL-2007` | 在 Lambda 表达式外部构建字符串并捕获 |
| `insert ... select` 中的显式 join | 使用多对一路径 |
| `returning` | DML 之后再查询一次 |
| 数据库时间（`current_timestamp`） | 绑定应用程序时间 |

违反规则时会抛出带错误码 `[LQL-xxxx]` 的 `LambdaQLException`。大多数规则是 `TranslationException`，赋值 Lambda 表达式中的条件或局部变量是 `UnsupportedLambdaShapeException`。见 [11. 错误与语言](../11-errors-and-languages.md)。
要在一处查看所有规则：[10. 规则与限制](10-rules-and-limits.md)。

---

[Kotlin 路线](README.md) | 上一章：[6. 查询列、聚合与分组](06-select-group.md) | 下一章：[8. 存储过程与数据库函数](08-procedures-functions.md) | [本页的 Java 版本](../java/07-dml.md)
