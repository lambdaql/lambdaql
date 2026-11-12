# 5. 子查询（Kotlin）

> [!NOTE]
> **状态**
> - **已设计，尚未实现**（里程碑 M8）。
> - 该 Kotlin 语法可以编译，其字节码已用原型检查过（kotlinc 2.3.21）。SQL 形式已在 H2 2.3、MariaDB 10.11 和 Oracle 23ai 上使用 Hibernate 6.6.5 检查过。
> - 本章中的 HQL 是**计划**的输出。

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
// HQL: select o from Order o
//      where exists (select 1 from Item i where i.order.id = o.id and i.qty > 10)
```

**关于 SQL 块。** `…` 是缩短的 select 列表（实体的所有列），`?` 是绑定参数。[`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java) 把本页的 HQL 直接交给 Hibernate，并记录在 H2 2.3、MariaDB 10.11 和 Oracle 23.26（Hibernate 6.6.5）上得到的 SQL。lambdaQL 目前还不会生成这些查询。如果 Hibernate 的 SQL 发生变化，测试就会失败。

**各数据库的 SQL（计划中）** — Hibernate 对计划中 HQL 的翻译，由 `PlannedSqlTest` 记录；lambdaQL 目前还不会生成此查询。来自 `expected-sql/` 的 [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)，示例 `05-exists`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id and i1_0.qty>10)
```

这是一个**关联**子查询：内部 Lambda 使用了外部的 `o`（见 [5.6](#56-correlated-subqueries)）。

## 5.3 NOT EXISTS

使用 `!`。你可以把它与其他条件组合：

```kotlin
// 状态为 1 的订单，或者没有任何订单项的订单
qb.from(Order::class.java)
    .where { o, sub ->
        o.status == 1 ||
            !sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists()
    }
    .toList()
// HQL: select o from Order o
//      where o.status = 1
//         or not exists (select 1 from Item i where i.order.id = o.id)
```

**各数据库的 SQL（计划中）** — Hibernate 对计划中 HQL 的翻译，由 `PlannedSqlTest` 记录；lambdaQL 目前还不会生成此查询。来自 `expected-sql/` 的 [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)，示例 `05-not-exists-or`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 or not exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id)
```

<a id="54-in"></a>
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
// HQL: select o from Order o
//      where o.customer.id in (select c.id from Customer c where c.grade > 3)
```

**各数据库的 SQL（计划中）** — Hibernate 对计划中 HQL 的翻译，由 `PlannedSqlTest` 记录；lambdaQL 目前还不会生成此查询。来自 `expected-sql/` 的 [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)，示例 `05-in-subquery`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id in (select c2_0.id from customers c2_0 where c2_0.grade>3)
```

`o.customer?.id` 以客户的 `@Id` 结尾，因此它就是外键列 `o.customer.id`。不需要 join。

<a id="55-not-in-is-written-as-not-exists"></a>
## 5.5 NOT IN 会写成 NOT EXISTS

`x !in values` **不会**被输出为 `NOT IN`。lambdaQL 会写成 `NOT EXISTS`：

```kotlin
qb.from(Order::class.java)
    .where { o, sub ->
        o.customer?.id !in sub.from(Customer::class.java)
            .where { c -> c.grade!! > 3 }
            .select { c -> c.id }
    }
    .toList()
// HQL: select o from Order o
//      where not exists (select 1 from Customer c
//                        where c.grade > 3 and c.id = o.customer.id)
```

**各数据库的 SQL（计划中）** — Hibernate 对计划中 HQL 的翻译，由 `PlannedSqlTest` 记录；lambdaQL 目前还不会生成此查询。来自 `expected-sql/` 的 [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)，示例 `05-not-in`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where not exists(select 1 from customers c2_0 where c2_0.grade>3 and c2_0.id=o1_0.customer_id)
```

原因：

- 只要子查询返回哪怕一个 NULL，`x NOT IN (subquery)` 就**一行都不会返回**。这是一个常见的 SQL 陷阱。
- `NOT EXISTS` 没有这个问题。数据库执行它的效率也很好（作为反连接）。
- 结果：没有客户的订单会被上面的查询返回，因为没有任何客户与它匹配。

<a id="56-correlated-subqueries"></a>
## 5.6 关联子查询

当内部 Lambda 使用外部实体时，子查询就是**关联**的。你不需要任何特殊语法，直接使用外部变量即可：

```kotlin
.where { o, sub ->
    sub.from(Item::class.java)
        .where { i -> i.order?.id == o.id }   // o 来自外部
        .exists()
}
```

lambdaQL 如何读取内部 Lambda 的捕获值：

| 在内部 Lambda 中捕获的 | 变成 |
|---|---|
| 外部实体（`o`） | 对外部别名的引用（`o.id`） |
| 任何其他值（局部 `val`、属性等） | 绑定参数，与 [3.11](03-where.md#311-captured-values) 中相同 |

```kotlin
val minQty = 10
.where { o, sub ->
    sub.from(Item::class.java)
        .where { i -> i.order?.id == o.id && i.qty > minQty }
        .exists()
}
// HQL: exists (select 1 from Item i where i.order.id = o.id and i.qty > :p1)
```

**各数据库的 SQL（计划中）** — Hibernate 对计划中 HQL 的翻译，由 `PlannedSqlTest` 记录；lambdaQL 目前还不会生成此查询。来自 `expected-sql/` 的 [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)，示例 `05-correlated-param`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id and i1_0.qty>?)
```

**关联中的 `==`。** `i.order?.id == o.id` 比较两个 `Long?` 值。按照一般规则，这是 null 安全比较（`is not distinct from`，见 [3.6](03-where.md#36-null-rules)）。
但 `o.id` 是 `@Id`，永远不可能为 NULL。在这种情况下，lambdaQL 会写成简单的 `=`（`!=` 则写成 `(a <> b or a is null)`）。
结果相同，而且数据库可以使用索引。在 Oracle 上，`is not distinct from` 会变成 `decode(...)`，无法使用索引。

## 5.7 标量子查询

给出**一个值**的子查询可以用在比较中。

价格高于平均价格的订单：

```kotlin
qb.from(Order::class.java)
    .where { o, sub -> o.price!! > sub.from(Order::class.java).avg { x -> x.price } }
    .toList()
// HQL: select o from Order o
//      where o.price > (select avg(x.price) from Order x)
```

**各数据库的 SQL（计划中）** — Hibernate 对计划中 HQL 的翻译，由 `PlannedSqlTest` 记录；lambdaQL 目前还不会生成此查询。来自 `expected-sql/` 的 [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)，示例 `05-scalar-avg`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.price>(select avg(o2_0.price) from orders o2_0)
```

标量子查询必须**始终给出一行**。允许两种形式：

| 形式 | 示例 |
|---|---|
| 聚合：`count()`、`sum`、`avg`、`min`、`max` | `sub.from(Item::class.java).where { i -> i.order?.id == o.id }.count()` |
| Top-1：`orderBy { ... }.limit(1)...first()` | 见下文 |

其他任何 `first()` 都会在分析时报错。

Top-1 示例：同一客户的最新订单。

```kotlin
qb.from(Order::class.java)
    .where { o, sub ->
        o.id == sub.from(Order::class.java)
            .where { x -> x.customer?.id == o.customer?.id }
            .orderBy { x, by -> by.desc(x.updateAt) }
            .limit(1)
            .select { x -> x.id }
            .first()
    }
    .toList()
```

聚合的 NULL 遵循 SQL：没有行时，`sum`、`avg`、`min`、`max` 返回 NULL，`count` 返回 0。

## 5.8 嵌套子查询

子查询的 `where` 也可以接收 `sub` 参数。同样的规则在每一层都适用。

有包含大数量订单项的订单的客户：

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
// HQL: select c from Customer c
//      where exists (select 1 from Order o
//                    where o.customer.id = c.id
//                      and exists (select 1 from Item i
//                                  where i.order.id = o.id and i.qty > 10))
```

**各数据库的 SQL（计划中）** — Hibernate 对计划中 HQL 的翻译，由 `PlannedSqlTest` 记录；lambdaQL 目前还不会生成此查询。来自 `expected-sql/` 的 [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)，示例 `05-nested`：

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
| 带 `limit` 的 `orderBy`（top-1、top-N） | 保留 |
| 不带 `limit` 的 `orderBy` | **移除**并记录警告日志（它不会改变结果；Oracle 甚至会报语法错误 ORA-00907） |
| MariaDB：`IN (... limit n)`（MariaDB 不支持） | 自动包装：`in (select x.id from (... limit n) x)` |
| MariaDB：`lateral`（在 MariaDB 上结果错误） | 用 `row_number() over (partition by ... order by ...)` 改写 |

[4. 排序与分页](04-order-paging.md) 中的 NULL 策略和自动 `id` 决胜键也适用于子查询内部。

## 5.10 子查询内部的路径与 NULL

在子查询内部，条件、路径和 NULL 的行为**与外部查询完全相同**（[3. 条件](03-where.md)）：

- 两个可空值之间的 `==` 是 null 安全的值比较。
- `!!` 会被忽略。路径上的 `?.` 指向同一列。
- 子查询条件中的关联路径遵循与外层 `where` 相同的 join 规则（决策 D36）：默认是内连接 `join`，当 null 守卫保留没有关联的行时是 `left join`。
- 以 `@Id` 结尾的路径是外键列（不需要 join）。
- 集合路径（`o.items`）会被拒绝。要查询集合，请使用子查询：

```kotlin
// 代替 it.items.size > 0（会被拒绝）：
.where { o, sub -> sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
```

## 5.11 其他位置的子查询

- **`from` 中的子查询（派生表）和 CTE**（`qb.from { sub -> ... }`、`qb.with { sub -> ... }`）：见 [6. 查询列、聚合与分组](06-select-group.md)。
- **update / delete 中的子查询**：使用相同的 `sub` 语法。见 [7. 更新、插入、删除](07-dml.md)。

## 5.12 规则总结（Q1 到 Q6）

| # | 规则 |
|---|---|
| Q1 | 子查询内部的条件、路径和 NULL 规则与外部相同。条件中的关联路径使用相同的 join 规则（默认是内连接 `join`） |
| Q2 | **NOT IN 输出为 NOT EXISTS**：`x !in values` 变成 `not exists (select 1 from C c where <condition> and c.sel = x)`。这样可以避开 `NOT IN` + NULL 的陷阱 |
| Q3 | 标量子查询必须给出一行：聚合（`max`、`min`、`avg`、`sum`、`count`）或 `orderBy { }.limit(1)...first()`。其他任何 `first()` 都是错误 |
| Q4 | 子查询的排序：有 `limit` 时保留，没有 `limit` 时移除并发出警告。MariaDB 的 `IN (... limit)` 会被包装，`lateral` 会用 `row_number()` 改写 |
| Q5 | 在关联中，当一侧永远不可能为 NULL（`@Id`、`nullable = false`、基本类型）时，`==` 写成 `=`。`!=` 变成 `(a <> b or a is null)`。这样可以保持索引的使用 |
| Q6 | 子查询的 `where` 可以接收自己的 `sub` 参数：`where { i, sub2 -> ... }`。同样的规则在每一层都适用 |

---

[Kotlin 路线](README.md) | 上一章：[4. 排序与分页](04-order-paging.md) | 下一章：[6. 查询列、聚合与分组](06-select-group.md) | [本页的 Java 版本](../java/05-subquery.md)
