# 5. 子查询（Kotlin）

> [!IMPORTANT]
> 本页比韩文手册旧。最新规则请参见[韩文页面](../../ko/kotlin/05-subquery.md)。

[Kotlin 路线](README.md) | [手册首页](../README.md) | 上一章：[4. 排序与分页](04-order-paging.md) | 下一章：[6. 查询列、聚合与分组](06-select-group.md) | [本页的 Java 版本](../java/05-subquery.md)

公共页面：[1. 概述](../01-overview.md) · [11. 错误与语言](../11-errors-and-languages.md)

---

## 5.1 思路：第二个 Lambda 参数 `sub`

`where` Lambda 可以接收**第二个参数**：子查询工厂。本手册称它为 `sub`（你可以使用任何名字）。
在 Lambda 内部，你用与普通查询**相同的 Lambda 语法**编写子查询。

```kotlin
.where { ... }                // 没有子查询：一个参数（it）
.where { o, sub -> ... }      // 有子查询：两个参数
```

lambdaQL 根据参数的个数区分这两种写法。Kotlin 会选择正确的重载。
你可以把子查询与 `&&`、`||` 和 `!` 自由组合。不需要任何导入。

用 `sub` 可以做的事：

| 子查询 | 用作 | HQL |
|---|---|---|
| `sub.from(X::class.java).where { ... }.exists()` | 条件 | `exists (select 1 from X x where ...)` |
| `!sub.from(X::class.java).where { ... }.exists()` | 条件 | `not exists (...)` |
| `y in sub.from(X::class.java).where { ... }.select { x -> v }` | 条件 | `y in (select x.v from X x where ...)` |
| `y !in sub.from(...)...select { ... }` | 条件 | `not exists (... and x.v = y)` |
| `sub.from(X::class.java).avg { x -> v }`（还有 `sum`、`min`、`max`、`count()`） | 值 | `(select avg(x.v) from X x)` |
| `sub.from(X::class.java)...orderBy { ... }.limit(1).select { x -> v }.first()` | 值 | top-1 子查询 |

> [!NOTE]
> 返回值（`Boolean`、`Double` 等）只是为了让代码能够编译的占位符。
> Lambda 永远不会被执行。这些函数只在 lambdaQL 的 Lambda 内部才有意义。

在 Kotlin 中，`y in values` 会编译为 `values.contains(y)`。因此 Kotlin 的 `in` 运算符无需额外代码就能与子查询 API 一起使用。

内部 Lambda（`sub.from(...).where { }` 中的 `{ i -> ... }`）本身不需要是 `Serializable`。lambdaQL 会在外部 Lambda 的字节码中找到它们。

在 HQL 中，子查询的根别名是 `s1`、`s2` 等（外层的根是 `e0`）。子查询内部的 join 使用 `s1j1` 这样的别名。

标记为 **(已测试)** 的示例来自 [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)。它使用 Java 测试实体：`price` 是基本类型 `double`，`qty` 是 `int`。更多用 Java 写的查询在 [`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java) 中测试过（H2、MariaDB 和 Oracle）。这些示例请看[本页的 Java 版本](../java/05-subquery.md)。

## 5.2 EXISTS

至少有一个 `qty > 10` 的订单项的订单：

```kotlin
qb.from(Order::class.java)
    .where { o, sub ->
        sub.from(Item::class.java)
            .where { i -> i.order?.id == o.id && i.qty > 10 }
            .exists()
    }
    .toList()
// HQL: select e0 from Order e0
//      where exists (select 1 from Item s1 where s1.order.id = e0.id and s1.qty > 10)
```

**(已测试)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)，region `exists`：

```kotlin
val q = qb.from(Order::class.java)
    .where { o, sub -> sub.from(Item::class.java).where { i -> i.order?.id == o.id && i.qty > 10 }.exists() }
assertEquals("select e0 from Order e0 where exists (select 1 from Item s1 where s1.order.id = e0.id and s1.qty > 10)",
    q.toHql().hql())
```

**关于 SQL 块。** `…` 是缩短的 select 列表（实体的所有列），`?` 是绑定参数。这些行来自 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)。该测试使用 Hibernate 6.6.5 在 H2 2.3、MariaDB 10.11 和 Oracle 23.26 上运行每个查询，如果 Hibernate 的 SQL 发生变化，测试就会失败。该测试使用 Java Lambda。含义相同的 Kotlin Lambda 生成相同的 HQL，因此得到相同的 SQL。

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `sub-exists`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id and i1_0.qty>10)
```

这是一个**关联**子查询：内部 Lambda 使用了外部的 `o`（见 [5.6](#56-correlated-subqueries)）。

## 5.3 NOT EXISTS

使用 `!`。可以与其他条件组合：

```kotlin
// 状态为 1 的订单，或者没有任何订单项的订单
qb.from(Order::class.java)
    .where { o, sub ->
        o.status == 1 ||
            !sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists()
    }
    .toList()
// HQL: select e0 from Order e0
//      where e0.status = 1 or not exists (select 1 from Item s1 where s1.order.id = e0.id)
```

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `sub-not-exists`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 or not exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id)
```

## 5.4 IN

`grade > 3` 的客户的订单。使用 `in`：

```kotlin
qb.from(Order::class.java)
    .where { o, sub ->
        o.customer?.id in sub.from(Customer::class.java)
            .where { c -> c.grade!! > 3 }
            .select { c -> c.id }
    }
    .toList()
// HQL: select e0 from Order e0
//      where e0.customer.id in (select s1.id from Customer s1 where s1.grade > 3)
```

**(已测试)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)，region `in`：

```kotlin
val q = qb.from(Order::class.java)
    .where { o, sub -> o.customer?.id in sub.from(Customer::class.java).where { c -> c.grade!! > 3 }.select { c -> c.id } }
assertEquals("select e0 from Order e0 where e0.customer.id in (select s1.id from Customer s1 where s1.grade > 3)",
    q.toHql().hql())
```

**各数据库的 SQL（Hibernate 为此 HQL 生成的 SQL）** — 由 [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java) 记录，它把同样的 HQL 直接交给 Hibernate。Java 测试（region `in`）还写了 null 检查 `c.getGrade() != null`；它的 SQL 是示例 `sub-in`。来自 `expected-sql/` 的 [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)，示例 `05-in-subquery`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id in (select c2_0.id from customers c2_0 where c2_0.grade>3)
```

`o.customer?.id` 以客户的 `@Id` 结尾，所以它就是外键列 `e0.customer.id`。不需要 join。
`!!` 会被忽略（决定 D23）：`c.grade!! > 3` 就是 `s1.grade > 3`。

<a id="55-not-in-is-written-as-not-exists"></a>
## 5.5 NOT IN 被写成 NOT EXISTS

`x !in values` **不会**被输出为 `NOT IN`。lambdaQL 会写成 `NOT EXISTS`：

```kotlin
// 状态不是任何客户等级的订单
qb.from(Order::class.java)
    .where { o, sub -> o.status !in sub.from(Customer::class.java).select { c -> c.grade } }
    .toList()
// HQL: select e0 from Order e0
//      where e0.status not in (select s1.grade from Customer s1)
```

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `sub-not-in`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status not in (select c2_0.grade from customers c2_0)
```

原因：

- 只要子查询返回哪怕一个 NULL，`x NOT IN (subquery)` 就**一行也不返回**。这是一个常见的 SQL 陷阱。
- `NOT EXISTS` 没有这个问题。数据库也能很好地执行它（作为反连接）。
- 结果：该查询返回的行与 Kotlin 中 `o.status !in grades` 的结果相同。Java 测试用数据中的 NULL 等级检查了这一点。

<a id="56-correlated-subqueries"></a>
## 5.6 关联子查询

当内部 Lambda 使用外部实体时，子查询就是**关联**的。不需要任何特殊语法，直接使用外部变量即可：

```kotlin
.where { o, sub ->
    sub.from(Item::class.java)
        .where { i -> i.order?.id == o.id }   // o 来自外部
        .exists()
}
```

lambdaQL 如何读取内部 Lambda 的捕获值：

| 内部 Lambda 中捕获的内容 | 变成 |
|---|---|
| 外部实体（`o`） | 对外部别名的引用（`e0.id`） |
| 其他任何值（局部 `val`、属性等） | 绑定参数，与 [3.11](03-where.md#311-captured-values) 相同 |

```kotlin
val minQty = 10
.where { o, sub ->
    sub.from(Item::class.java)
        .where { i -> i.order?.id == o.id && i.qty > minQty }
        .exists()
}
// HQL: select e0 from Order e0
//      where exists (select 1 from Item s1 where s1.order.id = e0.id and s1.qty > :p1)
// 参数：{p1=10}
```

[`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java) 中的 Java 测试方法 `capturedValueInside` 检查了这个 HQL、参数和返回的行。

**各数据库的 SQL（Hibernate 为此 HQL 生成的 SQL）** — 由 [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java) 记录，它把同样的 HQL 直接交给 Hibernate。来自 `expected-sql/` 的 [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)，示例 `05-correlated-param`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id and i1_0.qty>?)
```

**关联中的 `==`。** `i.order?.id == o.id` 比较两个 `Long?` 值。两个实体值按 SQL 语义比较（决策 D48，见 [3.6](03-where.md#36-null-rules)），因此 lambdaQL 写成简单的 `=`（`!=` 写成 `<>`）。
数据库可以使用索引。

<a id="57-scalar-subqueries"></a>
## 5.7 标量子查询

返回**一个值**的子查询可以用在比较中。

价格高于平均价格的订单：

```kotlin
qb.from(Order::class.java)
    .where { o, sub -> o.price!! > sub.from(Order::class.java).avg { x -> x.price } }
    .toList()
// HQL: select e0 from Order e0
//      where e0.price > (select avg(s1.price) from Order s1)
```

**(已测试)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)，region `scalar`（测试的 `price` 是 `double`，所以没有 `!!`）：

```kotlin
val q = qb.from(Order::class.java).where { o, sub -> o.price > sub.from(Order::class.java).avg { x -> x.price } }
assertEquals("select e0 from Order e0 where e0.price > (select avg(s1.price) from Order s1)", q.toHql().hql())
```

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `sub-scalar`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.price>(select avg(o2_0.price) from orders o2_0)
```

标量子查询必须**始终返回一行**。允许两种形式（规则 Q3）：

| 形式 | 示例 |
|---|---|
| 聚合：`count()`、`sum`、`avg`、`min`、`max` | `sub.from(Item::class.java).where { i -> i.order?.id == o.id }.count()` |
| Top-1：`orderBy { ... }.limit(1)...first()` | 见下文 |

其他任何 `first()` 都会在分析时报错（`TranslationException`，`LQL-3017`）。

有两个或更多订单项的订单（`count()`）：

```kotlin
qb.from(Order::class.java)
    .where { o, sub -> sub.from(Item::class.java).where { i -> i.order?.id == o.id }.count() >= 2 }
    .toList()
// HQL: select e0 from Order e0
//      where (select count(s1) from Item s1 where s1.order.id = e0.id) >= 2L
```

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `sub-count`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where (select count(i1_0.id) from items i1_0 where i1_0.order_id=o1_0.id)>=2
```

Top-1 示例：同一客户最贵的订单。

```kotlin
qb.from(Order::class.java)
    .where { o, sub ->
        o.id == sub.from(Order::class.java)
            .where { x -> x.customer?.id == o.customer?.id }
            .orderBy { x, by -> by.desc(x.price) }
            .limit(1)
            .select { x -> x.id }
            .first()
    }
    .toList()
```

同样的查询已用 Java 测试（region `top-one`，示例 `sub-top-one`）。lambdaQL 在子查询内部把 `id` 作为最后一个排序键加上（规则 S9）。
在 Java 测试中，`price` 和 `Customer.id` 是基本类型。对于 Kotlin 实体中可空的 `price: Double?` 和 `customer?.id`，适用 [3.6](03-where.md#36-null-rules) 和 [4.4](04-order-paging.md#44-null-ordering-is-the-same-on-every-database) 的 NULL 规则，因此 HQL 在 `nulls` 部分和比较方式上可能不同。
关联比较的是外键（`customer?.id`），而不是客户实体。见 [5.10](#510-paths-and-null-inside-subqueries)。

聚合的 NULL 遵循 SQL：没有行时，`sum`、`avg`、`min`、`max` 返回 NULL，`count` 返回 0。

<a id="58-nested-subqueries"></a>
## 5.8 嵌套子查询

子查询的 `where` 也可以接收 `sub` 参数。同样的规则在每一层都会再次适用。

有订单包含大数量订单项的客户：

```kotlin
qb.from(Customer::class.java)
    .where { c, sub ->
        sub.from(Order::class.java)
            .where { o, sub2 ->
                o.customer?.id == c.id &&
                    sub2.from(Item::class.java)
                        .where { i -> i.order?.id == o.id && i.qty > 10 }
                        .exists()
            }
            .exists()
    }
    .toList()
// HQL: select e0 from Customer e0
//      where exists (select 1 from Order s1 where s1.customer.id = e0.id
//                    and exists (select 1 from Item s2 where s2.order.id = s1.id and s2.qty > 10))
```

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `sub-nested`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from customers c1_0
where exists(select 1 from orders o1_0 where o1_0.customer_id=c1_0.id and exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id and i1_0.qty>10))
```

请在每一层使用新的名字（`sub2`、`o`、`i`）。Kotlin 允许重复使用相同的名字，但会发出名字遮蔽（shadowing）警告，而且代码更难读。

<a id="59-sorting-inside-a-subquery"></a>
## 5.9 子查询内部的排序

| 情况 | lambdaQL 的处理 |
|---|---|
| 带 `limit` 的 `orderBy`（top-1、top-N） | 保留，并加上 `id` 决胜键 |
| 不带 `limit` 的 `orderBy` | **删除它**并记录警告（它不会改变结果；Oracle 甚至会报语法错误 ORA-00907） |
| 对带 `limit` 的子查询使用 `in`（MariaDB 不支持 `IN (... limit n)`，错误 1235） | 在所有数据库上都包装成派生表：`in (select t1.v from (select s1.id as v ... limit n) t1)` |
| MariaDB：`lateral`（在 MariaDB 上结果错误） | 用 `row_number() over (partition by ... order by ...)` 改写：**尚未实现** |

[4. 排序与分页](04-order-paging.md)中的 NULL 策略和自动 `id` 决胜键在子查询内部同样适用。

<a id="510-paths-and-null-inside-subqueries"></a>
## 5.10 子查询内部的路径与 NULL

在子查询内部，条件、路径和 NULL 的行为与外部查询**完全相同**（[3. 条件](03-where.md)）：

- 两个实体值之间的 `==` 是按 SQL 语义的值比较（D48）；捕获的 `null` 生成 `is null`。
- `!!` 会被忽略。路径上的 `?.` 指向同一列。
- 子查询自身实体的关联路径遵循与外部 `where` 相同的 join 规则（决策 D36）：默认是内连接 `join`，当 null 守卫保留没有关联的行时是 `left join`。
- 以 `@Id` 结尾的路径是外键列（不需要 join）。
- **外部实体**只能使用它自己的列和外键 id（`o.id`、`o.customer?.id`）。需要 join 的外部实体路径（`o.customer?.name`）会在分析时报错。请改为比较外键 id。
- 集合路径（`o.items`）会被拒绝。要对集合进行判断，请使用子查询：

```kotlin
// 代替 it.items.size > 0（会被拒绝）：
.where { o, sub -> sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
```

<a id="511-subqueries-in-other-places"></a>
## 5.11 其他位置的子查询

- **update / delete 中的子查询**：`where` 中使用同样的 `sub` 语法。已实现（用 Java Lambda 测试，region `dml-subquery`）。见 [7.4 删除](07-4-delete.md) 和 [7.2 更新](07-2-update.md)。
- **`set` 值中的子查询**（`o.total = sub.from(...).sum { ... }`）：尚未实现。见 [7.2 更新](07-2-update.md)。
- **`from` 中的子查询（派生表）和 CTE**：已实现（里程碑 M8-2）。它们不使用 `sub`：把值查询传给 `qb.from(...)` 或 `qb.with(...)`。见 [6.9 派生表](06-select-group.md#69-derived-table-subquery-in-from) 和 [6.10 CTE](06-select-group.md#610-cte-with)。

<a id="512-rules-summary-q1-to-q6"></a>
## 5.12 规则汇总（Q1 至 Q6）

| # | 规则 |
|---|---|
| Q1 | 子查询内部的条件、路径和 NULL 规则与外部相同。条件中的关联路径使用相同的 join 规则（默认是内连接 `join`）。需要 join 的外部实体路径会报错；允许外键 id |
| Q2 | **NOT IN 输出为 NOT EXISTS**：`x !in values` 变成 `not exists (select 1 from C c where <condition> and c.sel = x)`。这样可以避开 `NOT IN` + NULL 的陷阱 |
| Q3 | 标量子查询必须返回一行：聚合（`max`、`min`、`avg`、`sum`、`count`）或 `orderBy { }.limit(1)...first()`。其他任何 `first()` 都是错误 |
| Q4 | 子查询排序：带 `limit` 时保留，不带 `limit` 时删除并发出警告。`IN (... limit)` 会被包装成派生表。MariaDB 用 `row_number()` 改写 `lateral` 尚未实现 |
| Q5 | 在关联中，`==` 写成 `=`，`!=` 写成 `<>`（SQL 语义，D48）：任一侧为 NULL 时不匹配。这样可以保留索引的使用 |
| Q6 | 子查询的 `where` 可以接收自己的 `sub` 参数：`where { i, sub2 -> ... }`。同样的规则在每一层都适用 |

---

[Kotlin 路线](README.md) | 上一章：[4. 排序与分页](04-order-paging.md) | 下一章：[6. 查询列、聚合与分组](06-select-group.md) | [本页的 Java 版本](../java/05-subquery.md)
