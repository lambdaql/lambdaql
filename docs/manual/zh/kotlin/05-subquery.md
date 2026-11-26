<a id="5-subqueries-kotlin"></a>
# 5. 子查询（Kotlin）

[Kotlin 路线](README.md) | [手册首页](../README.md) | 上一章：[4. 排序与分页](04-order-paging.md) | 下一章：[6. 查询列、聚合与分组](06-select-group.md) | [本页的 Java 版本](../java/05-subquery.md)

公共页面：[1. 概述](../01-overview.md) · [11. 错误与语言](../11-errors-and-languages.md)

---

<a id="51-the-idea-a-second-lambda-argument-sub"></a>
## 5.1 思路：第二个 Lambda 表达式参数 `sub`

`where` Lambda 表达式可以用**第二个参数**接收子查询工厂。本手册称它为 `sub`（可以使用任何名字）。
在其中用与普通查询**相同的 Lambda 表达式语法**编写子查询。

只有一个参数的 `where { ... }`（`it`）没有子查询，有两个参数的 `where { o, sub -> ... }` 带有子查询。
lambdaQL 根据参数的个数区分这两种写法。Kotlin 会选择合适的重载。
子查询可以与 `&&`、`||`、`!` 自由组合。不需要 import。

用 `sub` 可以做的事：

| 子查询 | 用途 | HQL |
|---|---|---|
| `sub.from(X::class.java).where { ... }.exists()` | 条件 | `exists (select 1 from X x where ...)` |
| `!sub.from(X::class.java).where { ... }.exists()` | 条件 | `not exists (...)` |
| `y in sub.from(X::class.java).where { ... }.select { x -> v }` | 条件 | `y in (select x.v from X x where ...)` |
| `y !in sub.from(...)...select { ... }` | 条件 | `y not in (select x.v from X x where ...)` |
| `sub.from(X::class.java).avg { x -> v }`（还有 `sum`、`min`、`max`、`count()`） | 值 | `(select avg(x.v) from X x)` |
| `sub.from(X::class.java)...orderBy { ... }.limit(1).select { x -> v }.first()` | 值 | top-1 子查询 |
| `sub.from(A::class.java).join(B::class.java) { a, b -> ... }.where { a, b -> ... }.exists()` | 条件 | `exists (select 1 from A a join B b on ... where ...)`（[子查询中的 join](#joins-inside-a-subquery)） |

> [!NOTE]
> 返回值（`Boolean`、`Double` 等）只是为了让代码能够编译的占位符。
> 这些函数只在 lambdaQL 的 Lambda 表达式内部使用。lambdaQL 不调用 Lambda 表达式，而是分析其字节 opcode。

在 Kotlin 中，`y in values` 会编译为 `values.contains(y)`。因此 Kotlin 的 `in` 运算符无需额外代码就能与子查询 API 一起使用。

内部 Lambda 表达式（`sub.from(...).where { }` 中的 `{ i -> ... }`）本身不需要是 `Serializable`。lambdaQL 会在外部 Lambda 表达式的字节 opcode 中找到它。

在 HQL 中，子查询的根别名是 `s1`、`s2` 等（外层的根是 `e0`）。子查询内部的 join 使用 `s1j1` 这样的别名。

示例使用用 Java 编写的示例实体。`price` 是基本类型 `double`，`qty` 是 `int`，`status` 是 `short`，`Customer.id` 是 `int`。

## 5.2 EXISTS

至少有一个 `qty > 10` 的订单项的订单：

<!-- region: KotlinQueryTest.kt#exists -->
```kotlin
val q = qb.from(Order::class.java)
    .where { o, sub -> sub.from(Item::class.java).where { i -> i.order?.id == o.id && i.qty > 10 }.exists() }
assertEquals("select e0 from Order e0 where exists (select 1 from Item s1 where s1.order.id = e0.id and s1.qty > 10)",
    q.toHql().hql())
```

**各数据库的 SQL**

<!-- sql: sub-exists -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id and i1_0.qty>10)
```

这是**关联**子查询。内部 Lambda 表达式使用了外部的 `o`（见 [5.6](#56-correlated-subqueries)）。

示例代码：[`KotlinQueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)（`exists`）。

## 5.3 NOT EXISTS

使用 `!`。可以与其他条件组合。

<!-- region: ManualKotlinSubqueryTest.kt#not-exists -->
```kotlin
// orders with status 1, or orders without items
val q = qb.from(Order::class.java)
    .where { o, sub -> o.status.toInt() == 1 || !sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
assertEquals("select e0 from Order e0 where e0.status = 1 or not exists (select 1 from Item s1 where s1.order.id = e0.id)",
    q.toHql().hql())
```

**各数据库的 SQL**

<!-- sql: sub-not-exists -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 or not exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id)
```

示例代码：[`ManualKotlinSubqueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSubqueryTest.kt)（`not-exists`）。

## 5.4 IN

`grade > 3` 的客户的订单。使用 `in`。

<!-- region: KotlinQueryTest.kt#in -->
```kotlin
val q = qb.from(Order::class.java)
    .where { o, sub -> o.customer?.id in sub.from(Customer::class.java).where { c -> c.grade!! > 3 }.select { c -> c.id } }
assertEquals("select e0 from Order e0 where e0.customer.id in (select s1.id from Customer s1 where s1.grade > 3)",
    q.toHql().hql())
```

`o.customer?.id` 以客户的 `@Id` 结尾，因此是外键列 `e0.customer.id`。不需要 join。
`!!` 会被忽略。`c.grade!! > 3` 就是 `s1.grade > 3`。

示例代码：[`KotlinQueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)（`in`）。

<a id="55-not-in-is-written-as-not-exists"></a>
## 5.5 NOT IN 与 NULL

`x !in values` 按 SQL 语义原样输出为 `NOT IN`。示例查找 status 与任何客户的等级都不相等的订单。

<!-- region: ManualKotlinSubqueryTest.kt#not-in -->
```kotlin
// the SQL meaning: NOT IN. The grades are 5, 2 and NULL, so no row matches: x NOT IN (5, 2, NULL)
// is never true. To skip the NULL, say so in the subquery: where { c -> c.grade != null }
val q = qb.from(Order::class.java)
    .where { o, sub -> o.status.toInt() !in sub.from(Customer::class.java).select { c -> c.grade } }
assertEquals("select e0 from Order e0 where e0.status not in (select s1.grade from Customer s1)", q.toHql().hql())
val withoutNull = qb.from(Order::class.java)
    .where { o, sub -> o.status.toInt() !in sub.from(Customer::class.java).where { c -> c.grade != null }.select { c -> c.grade } }
assertEquals("select e0 from Order e0 where e0.status not in (select s1.grade from Customer s1 where s1.grade is not null)",
    withoutNull.toHql().hql())
```

`status` 是 `short`，`grade` 是 `Int?`，因此用 `o.status.toInt()` 对齐类型。lambdaQL 会忽略这个转换。

**各数据库的 SQL（第一个查询）**

<!-- sql: sub-not-in -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status not in (select c2_0.grade from customers c2_0)
```

原因：

- 只要子查询返回一个 NULL，`x NOT IN (subquery)` 就**不返回任何行**。lambdaQL 保持这一 SQL 语义，并以 `DEBUG` 级别记录 `LQL-5009`。
- 要跳过 NULL，请像上面示例中的 `withoutNull` 一样，在子查询中写 null 检查（`where { c -> c.grade != null }`）。
  示例数据中有 NULL 等级，因此第一个查询不返回任何行。
- `!sub...exists()` 仍然写成 `NOT EXISTS`。

示例代码：[`ManualKotlinSubqueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSubqueryTest.kt)（`not-in`）。

<a id="56-correlated-subqueries"></a>
## 5.6 关联子查询

内部 Lambda 表达式使用外部实体时，子查询就成为**关联**子查询。不需要特殊语法，直接使用外部变量即可。

lambdaQL 读取内部 Lambda 表达式捕获值的方式：

| 内部 Lambda 表达式捕获的内容 | 转换结果 |
|---|---|
| 外部实体（`o`） | 对外部别名的引用（`e0.id`） |
| 其他值（局部 `val`、属性等） | 与 [3.11](03-where.md#311-captured-values) 相同的绑定参数 |

<!-- region: ManualKotlinSubqueryTest.kt#correlated-param -->
```kotlin
// o comes from the outer query; minQty is a captured value: a bind parameter
val minQty = 10
val q = qb.from(Order::class.java)
    .where { o, sub -> sub.from(Item::class.java).where { i -> i.order?.id == o.id && i.qty > minQty }.exists() }
assertEquals("select e0 from Order e0 where exists (select 1 from Item s1 where s1.order.id = e0.id and s1.qty > :p1)",
    q.toHql().hql())
assertEquals(mapOf("p1" to 10), q.toHql().parameters())
```

**关联条件中的 `==`。** `i.order?.id == o.id` 比较两个 `Long?` 值。两个实体值按 SQL 语义比较（见 [3.6](03-where.md#36-null-rules)），因此 lambdaQL 写成简单的 `=`（`!=` 写成 `<>`）。
数据库可以使用索引。

示例代码：[`ManualKotlinSubqueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSubqueryTest.kt)（`correlated-param`）。

<a id="57-scalar-subqueries"></a>
## 5.7 标量子查询

返回**单个值**的子查询可以用于比较。

价格高于平均价格的订单：

<!-- region: KotlinQueryTest.kt#scalar -->
```kotlin
val q = qb.from(Order::class.java).where { o, sub -> o.price > sub.from(Order::class.java).avg { x -> x.price } }
assertEquals("select e0 from Order e0 where e0.price > (select avg(s1.price) from Order s1)", q.toHql().hql())
```

**各数据库的 SQL**

<!-- sql: sub-scalar -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.price>(select avg(o2_0.price) from orders o2_0)
```

标量子查询**必须总是返回一行**。允许两种写法。

| 写法 | 示例 |
|---|---|
| 聚合：`count()`、`sum`、`avg`、`min`、`max` | `sub.from(Item::class.java).where { i -> i.order?.id == o.id }.count()` |
| top-1：`orderBy { ... }.limit(1)...first()` | 见下文 |

其他任何 `first()` 都是分析时错误（`TranslationException`，`LQL-3017`）。

订单项有两个及以上的订单（`count()`）：

<!-- region: ManualKotlinSubqueryTest.kt#count -->
```kotlin
// orders with two or more items
val q = qb.from(Order::class.java)
    .where { o, sub -> sub.from(Item::class.java).where { i -> i.order?.id == o.id }.count() >= 2 }
assertEquals("select e0 from Order e0 where (select count(s1) from Item s1 where s1.order.id = e0.id) >= 2L", q.toHql().hql())
```

**各数据库的 SQL**

<!-- sql: sub-count -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where (select count(i1_0.id) from items i1_0 where i1_0.order_id=o1_0.id)>=2
```

top-1 示例：同一客户最贵的订单。

<!-- region: ManualKotlinSubqueryTest.kt#top-one -->
```kotlin
// the most expensive order of each customer: a correlated scalar subquery with orderBy and limit(1)
val q = qb.from(Order::class.java)
    .where { o, sub ->
        o.id == sub.from(Order::class.java)
            .where { x -> x.customer?.id == o.customer?.id }
            .orderBy { x -> desc(x.price) }
            .limit(1)
            .select { x -> x.id }
            .first()
    }
assertEquals("select e0 from Order e0 where e0.id = (select s1.id from Order s1 where s1.customer.id = e0.customer.id"
    + " order by s1.price desc, s1.id asc limit 1)", q.toHql().hql())
// first() without orderBy and limit(1) is rejected
val e = assertThrows(LambdaQLException::class.java) {
    qb.from(Order::class.java).where { o, sub -> o.id == sub.from(Order::class.java).select { x -> x.id }.first() }
}
assertEquals("LQL-3017", e.code())
```

lambdaQL 在子查询中把 `id` 添加为最后一个排序键。
关联条件比较的是外键（`customer?.id`），而不是客户实体。没有客户的订单不会使 `s1.customer.id = e0.customer.id` 为真，因此不在结果中。见 [5.10](#510-paths-and-null-inside-subqueries)。

聚合中的 NULL 遵循 SQL。没有行时，`sum`、`avg`、`min`、`max` 给出 NULL，`count` 给出 0。

示例代码：[`KotlinQueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)（`scalar`）、[`ManualKotlinSubqueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSubqueryTest.kt)（`count`、`top-one`）。

<a id="58-nested-subqueries"></a>
## 5.8 嵌套子查询

子查询的 `where` 也可以接收 `sub` 参数。同样的规则在每一层都再次适用。

拥有含大订单项订单的客户：

<!-- region: ManualKotlinSubqueryTest.kt#nested -->
```kotlin
// customers that have an order with an item of quantity > 10: a subquery inside a subquery
val q = qb.from(Customer::class.java)
    .where { c, sub ->
        sub.from(Order::class.java)
            .where { o, sub2 ->
                o.customer?.id == c.id &&
                    sub2.from(Item::class.java).where { i -> i.order?.id == o.id && i.qty > 10 }.exists()
            }
            .exists()
    }
assertEquals("select e0 from Customer e0 where exists (select 1 from Order s1 where s1.customer is not null"
    + " and s1.customer.id = e0.id and exists (select 1 from Item s2 where s2.order.id = s1.id and s2.qty > 10))", q.toHql().hql())
```

Kotlin 的 `o.customer?.id == c.id` 会加上 `s1.customer is not null and ...`。下面的 SQL 来自没有这个条件、含义相同的 HQL。

每一层请使用新的名字（`sub2`、`o`、`i`）。Kotlin 允许重复使用同一名字，但会给出名字遮蔽（shadowing）警告，代码也更难读。

示例代码：[`ManualKotlinSubqueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSubqueryTest.kt)（`nested`）。

<a id="59-sorting-inside-a-subquery"></a>
## 5.9 子查询内部的排序

| 情况 | lambdaQL 的行为 |
|---|---|
| 与 `limit` 一起使用的 `orderBy`（top-1、top-N） | 保留，并加上 `id` 的平局处理 |
| 没有 `limit` 的 `orderBy` | **删除**并在日志中记录警告（它不改变结果。在 Oracle 上甚至会出现语法错误 ORA-00907） |
| 对带 `limit` 的子查询使用 `in`（MariaDB 不支持 `IN (... limit n)`，错误 1235） | 在所有数据库上包装成派生表：`in (select t1.v from (select s1.id as v ... limit n) t1)` |

[4. 排序与分页](04-order-paging.md) 中的 NULL 策略和自动 `id` 平局处理在子查询中同样适用。

<a id="510-paths-and-null-inside-subqueries"></a>
## 5.10 子查询内部的路径与 NULL

在子查询内部，条件、路径和 NULL 的行为**与外部查询完全相同**（[3. 条件（`where`）](03-where.md)）。

- 实体值之间的 `==` 是 SQL 语义的值比较。捕获的 `null` 不匹配任何行（`LQL-4045`）。
- `!!` 不改变值。路径中的 `?.` 和 `!!` 指向同一个列。
- 子查询自身实体的关联路径遵循与外部 `where` 相同的连接规则。没有关联的行可能满足条件时为 `left join`，不可能满足时为内连接 `join`。
- 以 `@Id` 结尾的路径是外键列（不需要 join）。
- **外部实体**只能使用它自己的列和外键 id（`o.id`、`o.customer?.id`）。需要 join 的外部实体路径（`o.customer?.name`）是分析时错误（`LQL-3047`）。请改为比较外键 id。
- 集合路径（像 `Customer.orders` 这样的 `@OneToMany`）会被拒绝（`LQL-3010`）。要对集合提问，请使用子查询。
- 实体的 `@SQLRestriction` 和 `@SoftDelete` 条件也会由 Hibernate 加到子查询（`exists`、`in`）内部（在 MariaDB 10.11 和 Oracle 23.26 上实测）。lambdaQL 不单独处理这些条件。

<!-- region: ManualKotlinSubqueryTest.kt#rejected-paths -->
```kotlin
// a collection path is rejected: ask about the collection with a subquery
val collection = assertThrows(LambdaQLException::class.java) {
    qb.from(Customer::class.java).where { it.orders.size > 0 }.toHql()
}
assertEquals("LQL-3010", collection.code())
val withOrders = qb.from(Customer::class.java)
    .where { c, sub -> sub.from(Order::class.java).where { o -> o.customer?.id == c.id }.exists() }
assertEquals("select e0 from Customer e0 where exists (select 1 from Order s1 where s1.customer is not null and s1.customer.id = e0.id)",
    withOrders.toHql().hql())
// a path of the outer entity that needs a join is rejected: compare the foreign key id instead
val outerJoin = assertThrows(LambdaQLException::class.java) {
    qb.from(Order::class.java)
        .where { o, sub -> sub.from(Customer::class.java).where { c -> c.name == o.customer?.name }.exists() }.toHql()
}
assertEquals("LQL-3047", outerJoin.code())
```

<a id="joins-inside-a-subquery"></a>
### 子查询中的 join

在子查询中也可以连接两个实体。写法是 `sub.from(A::class.java).join(B::class.java) { a, b -> ... }` 和 `leftJoin(...) { ... }`，结果是 `From2<A, B>`。
`From2` 的所有 Lambda 表达式都接收两行：`where { a, b -> ... }`、`exists()`、`count()`、`select { a, b -> v }` 之后的 `contains(x)` 或 `first()`，以及 `max`、`min`、`avg`、`sum { a, b -> v }`。

- HQL 形如 `exists (select 1 from Order s1 join Item s2 on s2.order.id = s1.id where ...)`。
- ON 条件中的关联路径成为 `left join`。路径 join 紧跟在该路径起始的根之后。
- 子查询中的实体最多两个。join 写法中不能使用 `orderBy` 和 `limit`。
- 在三种数据库上实测了 `exists`、对连接行的 `sum`，以及使用 `left join` 和 ON 中关联路径的 `in`。

<!-- region: KotlinQueryTest.kt#sub-join -->
```kotlin
// customers with an order that has an item of quantity > 10: the subquery joins Order and Item
val q = qb.from(Customer::class.java).where { c, sub ->
    sub.from(Order::class.java)
        .join(Item::class.java) { o, i -> i.order?.id == o.id }
        .where { o, i -> o.customer?.id == c.id && i.qty > 10 }
        .exists()
}
assertEquals("select e0 from Customer e0 where exists (select 1 from Order s1 join Item s2 on s2.order.id = s1.id"
        + " where s1.customer is not null and s1.customer.id = e0.id and s2.qty > 10)", q.toHql().hql())
```

Kotlin 的 `o.customer?.id == c.id` 会加上 `s1.customer is not null and ...`（见上面的示例）。

对连接行的聚合（`sum { a, b -> v }`）：

<!-- region: ManualKotlinSubqueryTest.kt#sub-join-sum -->
```kotlin
// an aggregate over the joined rows: customers whose items have a total quantity over 30
val q = qb.from(Customer::class.java).where { c, sub ->
    sub.from(Order::class.java)
        .join(Item::class.java) { o, i -> i.order?.id == o.id }
        .where { o, _ -> o.customer?.id == c.id }
        .sum { _, i -> i.qty } > 30
}
assertEquals("select e0 from Customer e0 where (select sum(s2.qty) from Order s1 join Item s2 on s2.order.id = s1.id"
    + " where s1.customer is not null and s1.customer.id = e0.id) > 30", q.toHql().hql())
```

示例代码：[`ManualKotlinSubqueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSubqueryTest.kt)（`rejected-paths`、`sub-join-sum`）、[`KotlinQueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)（`sub-join`）。

<a id="511-subqueries-in-other-places"></a>
## 5.11 其他位置的子查询

- **update / delete 中的子查询**：在 `where` 中使用同样的 `sub` 语法。见 [7.4 删除（delete）](07-4-delete.md) 和 [7.2 更新（update）](07-2-update.md)。
- **`set` 值中的子查询**（`o.total = sub.from(...).sum { ... }`）：用赋值 Lambda 表达式的第二个参数 `sub` 编写。见 [7.2 更新（update）](07-2-update.md#an-aggregate-from-a-subquery)。
- **`from` 中的子查询（派生表）和 CTE**：已实现。不使用 `sub`，而是把值查询传给 `qb.from(...)` 或 `qb.with(...)`。见 [6.9 派生表](06-select-group.md#69-derived-table-subquery-in-from) 和 [6.10 CTE](06-select-group.md#610-cte-with)。

<a id="512-rules-summary-q1-to-q6"></a>
## 5.12 规则汇总（Q1 至 Q6）

| # | 规则 |
|---|---|
| Q1 | 子查询内部的条件、路径和 NULL 规则与外部相同。条件中的关联路径也遵循相同的连接规则（没有关联的行可能满足条件时为 `left join`，否则为内连接 `join`）。需要 join 的外部实体路径会报错；允许外键 id |
| Q2 | **NOT IN 保持 SQL 语义**：`x !in values` 变成 `x not in (select c.sel from C c where <condition>)`。子查询中有 NULL 时不返回任何行（`DEBUG` 级别 `LQL-5009`）。要跳过 NULL，请在子查询中加上 `c.sel != null` |
| Q3 | 标量子查询必须返回一行：聚合（`max`、`min`、`avg`、`sum`、`count`）或 `orderBy { }.limit(1)...first()`。其他任何 `first()` 都是错误 |
| Q4 | 子查询排序：带 `limit` 时保留，不带时删除并发出警告。`IN (... limit)` 会被包装成派生表 |
| Q5 | 在关联条件中，`==` 写成 `=`，`!=` 写成 `<>`（SQL 语义）。任一侧为 NULL 时不匹配。可以继续使用索引 |
| Q6 | 子查询的 `where` 可以接收自己的 `sub` 参数：`where { i, sub2 -> ... }`。同样的规则在每一层都适用 |

---

[Kotlin 路线](README.md) | 上一章：[4. 排序与分页](04-order-paging.md) | 下一章：[6. 查询列、聚合与分组](06-select-group.md) | [本页的 Java 版本](../java/05-subquery.md)
