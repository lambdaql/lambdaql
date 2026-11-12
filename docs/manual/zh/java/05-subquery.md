# 5. 子查询（Java）

> [!NOTE]
> **状态**
> - **已设计，尚未实现**（里程碑 M8）。
> - 该语法在 Java 中可以编译，字节码已用原型检查过。SQL 形式已在 H2 2.3、MariaDB 10.11 和 Oracle 23ai 上使用 Hibernate 6.6.5 检查过。
> - 本章中的 HQL 是**计划**的输出。

[Java 路线](README.md) | [手册首页](../README.md) | 上一章：[4. 排序与分页](04-order-paging.md) | 下一章：[6. 查询列、聚合与分组](06-select-group.md) | [本页的 Kotlin 版本](../kotlin/05-subquery.md)

公共页面：[1. 概述](../01-overview.md) · [11. 错误与语言](../11-errors-and-languages.md)

---

## 5.1 思路：第二个 Lambda 参数 `sub`

`where` 的 Lambda 可以接收**第二个参数**：子查询工厂。本手册称它为 `sub`（你可以使用任何名字）。
在 Lambda 内部，你用与普通查询**相同的 Lambda 语法**编写子查询。

```java
.where(o -> ...)              // 没有子查询：一个参数
.where((o, sub) -> ...)       // 有子查询：两个参数
```

lambdaQL 根据参数个数区分这两种写法。
子查询可以与 `&&`、`||` 和 `!` 自由组合。不需要静态导入。

用 `sub` 可以做的事情：

| 子查询 | 用作 | HQL |
|---|---|---|
| `sub.from(X.class).where(...).exists()` | 条件 | `exists (select 1 from X x where ...)` |
| `!sub.from(X.class).where(...).exists()` | 条件 | `not exists (...)` |
| `sub.from(X.class).where(...).select(x -> v).contains(y)` | 条件 | `y in (select x.v from X x where ...)` |
| `!... .contains(y)` | 条件 | `not exists (... and x.v = y)` |
| `sub.from(X.class).avg(x -> v)`（还有 `sum`、`min`、`max`、`count()`） | 值 | `(select avg(x.v) from X x)` |
| `sub.from(X.class)...orderBy(...).limit(1).select(x -> v).first()` | 值 | top-1 子查询 |

> [!NOTE]
> 返回值（`boolean`、`Double` 等）只是让代码能够编译的占位符。
> 这个 Lambda 从不会被执行。这些方法只在 lambdaQL 的 Lambda 内部才有意义。

内部 Lambda（`sub.from(...).where(...)` 里的 `i -> ...`）本身不需要是 `Serializable`。lambdaQL 会在外层 Lambda 的字节码中找到它们。

## 5.2 EXISTS

至少有一个 `qty > 10` 的明细的订单：

```java
qb.from(Order.class)
  .where((o, sub) -> sub.from(Item.class)
                        .where(i -> i.getOrder().getId() == o.getId() && i.getQty() > 10)
                        .exists())
  .toList();
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

这是一个**关联**子查询：内部 Lambda 使用了外层的 `o`（见 [5.6](#56-correlated-subqueries)）。

## 5.3 NOT EXISTS

使用 `!`。它可以与其他条件组合：

```java
// 状态为 1 的订单，或者没有任何明细的订单
qb.from(Order.class)
  .where((o, sub) -> o.getStatus() == 1
         || !sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).exists())
  .toList();
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

## 5.4 IN

`grade > 3` 的客户的订单：

```java
qb.from(Order.class)
  .where((o, sub) -> sub.from(Customer.class)
                        .where(c -> c.getGrade() > 3)
                        .select(c -> c.getId())
                        .contains(o.getCustomer().getId()))
  .toList();
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

`o.getCustomer().getId()` 以客户的 `@Id` 结尾，所以它就是外键列 `o.customer.id`。不需要 join。

## 5.5 NOT IN 被写成 NOT EXISTS

`!values.contains(x)` **不会**被渲染为 `NOT IN`。lambdaQL 会写成 `NOT EXISTS`：

```java
qb.from(Order.class)
  .where((o, sub) -> !sub.from(Customer.class)
                         .where(c -> c.getGrade() > 3)
                         .select(c -> c.getId())
                         .contains(o.getCustomer().getId()))
  .toList();
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

- 只要子查询返回哪怕一个 NULL，`x NOT IN (subquery)` 就**一行也不返回**。这是一个常见的 SQL 陷阱。
- `NOT EXISTS` 没有这个问题。数据库也能很好地执行它（作为反连接）。
- 结果：没有客户的订单会被上面的查询返回，因为没有任何客户与它匹配。

<a id="56-correlated-subqueries"></a>
## 5.6 关联子查询

当内部 Lambda 使用外层实体时，子查询就是**关联**的。不需要任何特殊语法，直接使用外层变量即可：

```java
.where((o, sub) -> sub.from(Item.class)
                      .where(i -> i.getOrder().getId() == o.getId())   // o 来自外层
                      .exists())
```

lambdaQL 如何读取内部 Lambda 的捕获值：

| 内部 Lambda 中捕获的内容 | 变成 |
|---|---|
| 外层实体（`o`） | 对外层别名的引用（`o.id`） |
| 其他任何值（局部变量、字段等） | 绑定参数，与 [3.11](03-where.md#311-captured-values) 相同 |

```java
int minQty = 10;
.where((o, sub) -> sub.from(Item.class)
                      .where(i -> i.getOrder().getId() == o.getId() && i.getQty() > minQty)
                      .exists())
// HQL: exists (select 1 from Item i where i.order.id = o.id and i.qty > :p1)
```

**各数据库的 SQL（计划中）** — Hibernate 对计划中 HQL 的翻译，由 `PlannedSqlTest` 记录；lambdaQL 目前还不会生成此查询。来自 `expected-sql/` 的 [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)，示例 `05-correlated-param`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id and i1_0.qty>?)
```

**关联中的 `==`。** `i.getOrder().getId() == o.getId()` 比较的是两个 `Long` 对象。按照通常的规则，这是一次 null 安全比较（`is not distinct from`，见 [3.6](03-where.md#36-null-rules)）。
但 `o.id` 是 `@Id`，永远不会是 NULL。这种情况下 lambdaQL 会写成简单的 `=`（`!=` 则写成 `(a <> b or a is null)`）。
结果相同，而且数据库可以使用索引。在 Oracle 上，`is not distinct from` 会变成 `decode(...)`，无法使用索引。

## 5.7 标量子查询

返回**一个值**的子查询可以用在比较中。

价格高于平均价格的订单：

```java
qb.from(Order.class)
  .where((o, sub) -> o.getPrice() > sub.from(Order.class).avg(x -> x.getPrice()))
  .toList();
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

标量子查询必须**始终返回一行**。允许两种形式：

| 形式 | 示例 |
|---|---|
| 聚合：`count()`、`sum`、`avg`、`min`、`max` | `sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).count()` |
| Top-1：`orderBy(...).limit(1)...first()` | 见下文 |

其他任何 `first()` 都会在分析时报错。

Top-1 示例：同一客户的最新订单。

```java
qb.from(Order.class)
  .where((o, sub) -> o.getId().equals(
          sub.from(Order.class)
             .where(x -> x.getCustomer().getId() == o.getCustomer().getId())
             .orderBy((x, by) -> by.desc(x.getUpdateAt()))
             .limit(1)
             .select(x -> x.getId())
             .first()))
  .toList();
```

聚合的 NULL 遵循 SQL：没有行时，`sum`、`avg`、`min`、`max` 返回 NULL，`count` 返回 0。

## 5.8 嵌套子查询

子查询的 `where` 也可以接收 `sub` 参数。同样的规则在每一层都适用。

有订单包含大数量明细的客户：

```java
qb.from(Customer.class)
  .where((c, sub) -> sub.from(Order.class)
      .where((o, sub2) -> o.getCustomer().getId() == c.getId()
             && sub2.from(Item.class)
                    .where(i -> i.getOrder().getId() == o.getId() && i.getQty() > 10)
                    .exists())
      .exists())
  .toList();
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

Java 不允许在嵌套的 Lambda 中重复使用同一个名字。请在每一层使用新名字（`sub2`、`o`、`i`）。

<a id="59-sorting-inside-a-subquery"></a>
## 5.9 子查询内部的排序

| 情况 | lambdaQL 的处理 |
|---|---|
| 带 `limit` 的 `orderBy`（top-1、top-N） | 保留 |
| 不带 `limit` 的 `orderBy` | **删除它**并记录警告（它不会改变结果；Oracle 甚至会报语法错误 ORA-00907） |
| MariaDB：`IN (... limit n)`（MariaDB 不支持） | 自动包装：`in (select x.id from (... limit n) x)` |
| MariaDB：`lateral`（在 MariaDB 上结果错误） | 用 `row_number() over (partition by ... order by ...)` 改写 |

[4. 排序与分页](04-order-paging.md)中的 NULL 策略和自动 `id` 决胜键在子查询内部同样适用。

## 5.10 子查询内部的路径与 NULL

在子查询内部，条件、路径和 NULL 的行为与外层查询**完全相同**（[3. 条件](03-where.md)）：

- 对象上的 `==` 是 null 安全的值比较。
- 子查询条件中的关联路径遵循与外层 `where` 相同的 join 规则（决策 D36）：默认是内连接 `join`，当 null 守卫保留没有关联的行时是 `left join`。
- 以 `@Id` 结尾的路径是外键列（不需要 join）。
- 集合路径（`o.getItems()`）会被拒绝。要对集合进行判断，请使用子查询：

```java
// 代替 o.getItems().size() > 0（会被拒绝）：
.where((o, sub) -> sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).exists())
```

## 5.11 其他位置的子查询

- **`from` 中的子查询（派生表）和 CTE**（`qb.from(sub -> ...)`、`qb.with(sub -> ...)`）：见 [6. 查询列、聚合与分组](06-select-group.md)。
- **UPDATE / DELETE 中的子查询**：同样的 `sub` 语法。见 [7. 更新、插入、删除](07-dml.md)。

## 5.12 规则汇总（Q1 至 Q6）

| # | 规则 |
|---|---|
| Q1 | 子查询内部的条件、路径和 NULL 规则与外部相同。条件中的关联路径使用相同的 join 规则（默认是内连接 `join`） |
| Q2 | **NOT IN 被渲染为 NOT EXISTS**：`!values.contains(x)` 变成 `not exists (select 1 from C c where <condition> and c.sel = x)`。这样可以避开 `NOT IN` + NULL 的陷阱 |
| Q3 | 标量子查询必须返回一行：聚合（`max`、`min`、`avg`、`sum`、`count`）或 `orderBy(...).limit(1)...first()`。其他任何 `first()` 都是错误 |
| Q4 | 子查询排序：带 `limit` 时保留，不带 `limit` 时删除并发出警告。MariaDB 的 `IN (... limit)` 会被包装，`lateral` 会用 `row_number()` 改写 |
| Q5 | 在关联中，当一侧永远不会是 NULL（`@Id`、`nullable = false`、基本类型）时，`==` 写成 `=`。`!=` 变成 `(a <> b or a is null)`。这样可以保留索引的使用 |
| Q6 | 子查询的 `where` 可以接收自己的 `sub` 参数：`where((i, sub2) -> ...)`。同样的规则在每一层都适用 |

---

[Java 路线](README.md) | 上一章：[4. 排序与分页](04-order-paging.md) | 下一章：[6. 查询列、聚合与分组](06-select-group.md) | [本页的 Kotlin 版本](../kotlin/05-subquery.md)
