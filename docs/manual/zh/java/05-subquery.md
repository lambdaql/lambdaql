<a id="5-subqueries-java"></a>
# 5. 子查询（Java）

[Java 路线](README.md) | [手册首页](../README.md) | 上一章：[4. 排序与分页](04-order-paging.md) | 下一章：[6. 查询列、聚合与分组](06-select-group.md) | [本页的 Kotlin 版本](../kotlin/05-subquery.md)

公共页面：[1. 概述](../01-overview.md) · [11. 错误与语言](../11-errors-and-languages.md)

---

<a id="51-the-idea-a-second-lambda-argument-sub"></a>
## 5.1 思路：第二个 Lambda 表达式参数 `sub`

`where` Lambda 表达式可以用**第二个参数**接收子查询工厂。本手册称它为 `sub`（可以使用任何名字）。
在其中用与普通查询**相同的 Lambda 表达式语法**编写子查询。

没有子查询时是 `.where(o -> ...)`，只有一个参数；有子查询时是 `.where((o, sub) -> ...)`，有两个参数。
lambdaQL 根据参数个数区分这两种写法。
子查询可以与 `&&`、`||`、`!` 自由组合。

用 `sub` 可以做的事情：

| 子查询 | 用途 | HQL |
|---|---|---|
| `sub.from(X.class).where(...).exists()` | 条件 | `exists (select 1 from X x where ...)` |
| `!sub.from(X.class).where(...).exists()` | 条件 | `not exists (...)` |
| `sub.from(X.class).where(...).select(x -> v).contains(y)` | 条件 | `y in (select x.v from X x where ...)` |
| `!... .contains(y)` | 条件 | `y not in (select x.v from X x where ...)` |
| `sub.from(X.class).avg(x -> v)`（还有 `sum`、`min`、`max`、`count()`） | 值 | `(select avg(x.v) from X x)` |
| `sub.from(X.class)...orderBy(...).limit(1).select(x -> v).first()` | 值 | top-1 子查询 |
| `sub.from(A.class).join(B.class, (a, b) -> ...).where((a, b) -> ...).exists()` | 条件 | `exists (select 1 from A a join B b on ... where ...)`（[子查询中的 join](#joins-inside-a-subquery)） |

> [!NOTE]
> 返回值（`boolean`、`Double` 等）只是让代码能够编译的占位符。
> 这些方法只在 lambdaQL Lambda 表达式内部使用。lambdaQL 不调用 Lambda 表达式，而是分析其字节码 opcode。

内部 Lambda 表达式（`sub.from(...).where(...)` 中的 `i -> ...`）本身不需要是 `Serializable`。lambdaQL 在外层 Lambda 表达式的字节码 opcode 中找到它们。

在 HQL 中，子查询根的别名是 `s1`、`s2` 等（外层根是 `e0`）。子查询内部的 join 使用 `s1j1` 这样的别名。

示例使用[示例实体](02-getting-started.md#example-entities)。`price` 是 `double`，`status` 是 `short`，`Customer.id` 是 `int`，`Customer.grade` 是 `Integer`。
示例数据中有等级 5、2、NULL，以及一个没有客户的订单。
本章示例在 H2、MariaDB、Oracle 上返回的行，与在 Java 中用同一条件过滤的结果相同。

## 5.2 EXISTS

至少有一个 `qty > 10` 的明细的订单：

<!-- region: SubqueryTest.java#exists -->
```java
// correlated: the inner lambda uses the outer order o
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId() && i.getQty() > 10).exists());
assertEquals("select e0 from Order e0 where exists (select 1 from Item s1 where s1.order.id = e0.id and s1.qty > 10)",
        q.toHql().hql());
```

**各数据库的 SQL**

<!-- sql: sub-exists -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id and i1_0.qty>10)
```

这是**关联**子查询。内部 Lambda 表达式使用了外层的 `o`（见 [5.6](#56-correlated-subqueries)）。

示例代码：[`SubqueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java)（`exists`）。

## 5.3 NOT EXISTS

使用 `!`。它可以与其他条件组合。
status 为 1 的订单，或者没有明细的订单：

<!-- region: SubqueryTest.java#not-exists -->
```java
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> o.getStatus() == 1 || !sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).exists());
assertEquals("select e0 from Order e0 where e0.status = 1 or not exists (select 1 from Item s1 where s1.order.id = e0.id)",
        q.toHql().hql());
```

**各数据库的 SQL**

<!-- sql: sub-not-exists -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 or not exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id)
```

示例代码：[`SubqueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java)（`not-exists`）。

## 5.4 IN

`grade > 3` 的客户的订单：

<!-- region: SubqueryTest.java#in -->
```java
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> sub.from(Customer.class).where(c -> c.getGrade() != null && c.getGrade() > 3)
                .select(c -> c.getId()).contains(o.getCustomer().getId()));
assertEquals("select e0 from Order e0 where e0.customer.id in"
        + " (select s1.id from Customer s1 where s1.grade is not null and s1.grade > 3)", q.toHql().hql());
```

**各数据库的 SQL**

<!-- sql: sub-in -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id in (select c2_0.id from customers c2_0 where c2_0.grade is not null and c2_0.grade>3)
```

`o.getCustomer().getId()` 以客户的 `@Id` 结尾，所以是外键列 `e0.customer.id`。不需要 join。
自行编写的 null 检查（`c.getGrade() != null`）在 HQL 中保留为 `s1.grade is not null`。

示例代码：[`SubqueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java)（`in`）。

<a id="55-not-in-is-written-as-not-exists"></a>
## 5.5 NOT IN 与 NULL

`!values.contains(x)` 按 SQL 语义原样渲染为 `NOT IN`。

<!-- region: SubqueryTest.java#not-in -->
```java
// the SQL meaning: NOT IN. The grades are 5, 2 and NULL, so no row matches: x NOT IN (5, 2, NULL)
// is never true. To skip the NULL, say so in the subquery: where(c -> c.getGrade() != null)
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> !sub.from(Customer.class).select(c -> c.getGrade()).contains((int) o.getStatus()));
assertEquals("select e0 from Order e0 where e0.status not in (select s1.grade from Customer s1)", q.toHql().hql());
SelectQuery<Order> withoutNull = qb.from(Order.class)
        .where((o, sub) -> !sub.from(Customer.class).where(c -> c.getGrade() != null).select(c -> c.getGrade())
                .contains((int) o.getStatus()));
```

**各数据库的 SQL**

<!-- sql: sub-not-in -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status not in (select c2_0.grade from customers c2_0)
```

原因：

- 只要子查询返回一个 NULL，`x NOT IN (subquery)` 就**一行也不返回**。lambdaQL 保留这一 SQL 语义，并以 `DEBUG` 级别记录 `LQL-5009`。
- 要跳过 NULL，请像示例中的第二个查询那样，在子查询中写 `where(c -> c.getGrade() != null)`。这样得到的结果与排除 NULL 等级后 Java 中 `!grades.contains(status)` 的结果相同。
- `!sub...exists()` 仍然写成 `NOT EXISTS`。

示例代码：[`SubqueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java)（`not-in`）。

<a id="56-correlated-subqueries"></a>
## 5.6 关联子查询

内部 Lambda 表达式使用外层实体时，子查询就成为**关联**子查询。不需要特殊语法，直接使用外层变量即可（下面示例中的 `o`）。

lambdaQL 读取内部 Lambda 表达式捕获值的方式：

| 内部 Lambda 表达式中捕获的内容 | 转换结果 |
|---|---|
| 外层实体（`o`） | 对外层别名的引用（`e0.id`） |
| 其他值（局部变量、字段等） | 绑定参数，与 [3.11](03-where.md#311-captured-values) 相同 |

<!-- region: ManualJavaSubqueryTest.java#correlated -->
```java
int minQty = 10;
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> sub.from(Item.class)
                .where(i -> i.getOrder().getId() == o.getId() && i.getQty() > minQty)   // o comes from outside
                .exists());
assertEquals("select e0 from Order e0"
        + " where exists (select 1 from Item s1 where s1.order.id = e0.id and s1.qty > :p1)", q.toHql().hql());
assertEquals(Map.of("p1", 10), q.toHql().parameters());
```

**关联条件中的 `==`。** `i.getOrder().getId() == o.getId()` 比较两个 `Long` 对象。两个实体值按 SQL 语义比较（见 [3.6](03-where.md#36-null-rules)），因此 lambdaQL 写成简单的 `=`（`!=` 写成 `<>`）。
数据库可以使用索引。

示例代码：[`ManualJavaSubqueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaSubqueryTest.java)（`correlated`）。

<a id="57-scalar-subqueries"></a>
## 5.7 标量子查询

返回**一个值**的子查询可以用于比较。

价格高于平均价格的订单：

<!-- region: SubqueryTest.java#scalar -->
```java
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> o.getPrice() > sub.from(Order.class).avg(x -> x.getPrice()));
assertEquals("select e0 from Order e0 where e0.price > (select avg(s1.price) from Order s1)", q.toHql().hql());
```

**各数据库的 SQL**

<!-- sql: sub-scalar -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.price>(select avg(o2_0.price) from orders o2_0)
```

标量子查询必须**始终返回一行**。允许两种形式。

| 形式 | 示例 |
|---|---|
| 聚合：`count()`、`sum`、`avg`、`min`、`max` | `sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).count()` |
| top-1：`orderBy(...).limit(1)...first()` | 见下文 |

其他 `first()` 在分析时报错（`TranslationException`，`LQL-3017`）。

有两个或更多明细的订单（`count()`）：

<!-- region: SubqueryTest.java#count -->
```java
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).count() >= 2);
assertEquals("select e0 from Order e0 where (select count(s1) from Item s1 where s1.order.id = e0.id) >= 2L", q.toHql().hql());
```

**各数据库的 SQL**

<!-- sql: sub-count -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where (select count(i1_0.id) from items i1_0 where i1_0.order_id=o1_0.id)>=2
```

top-1：每个客户最贵的订单。

<!-- region: SubqueryTest.java#top-one -->
```java
// the most expensive order of each customer: a correlated scalar subquery with order + limit 1
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> o.getId().equals(sub.from(Order.class)
                .where(x -> x.getCustomer().getId() == o.getCustomer().getId())
                .orderBy(x -> desc(x.getPrice()))
                .limit(1)
                .select(x -> x.getId())
                .first()));
assertEquals("select e0 from Order e0 where e0.id = (select s1.id from Order s1 where s1.customer.id = e0.customer.id"
        + " order by s1.price desc, s1.id asc limit 1)", q.toHql().hql());
// first() without limit(1) is rejected
assertThrows(LambdaQLException.class, () -> qb.from(Order.class)
        .where((o, sub) -> o.getId().equals(sub.from(Order.class).select(x -> x.getId()).first())));
```

**各数据库的 SQL**

<!-- sql: sub-top-one -->
```sql
-- H2, Oracle
select …
from orders o1_0
where o1_0.id=(select o2_0.id from orders o2_0 where o2_0.customer_id=o1_0.customer_id order by o2_0.price desc,o2_0.id fetch first 1 rows only)

-- MariaDB
select …
from orders o1_0
where o1_0.id=(select o2_0.id from orders o2_0 where o2_0.customer_id=o1_0.customer_id order by o2_0.price desc,o2_0.id limit 1)
```

子查询内部的排序也与外层查询一样使用 `By` 的排序函数：`orderBy(x -> desc(x.getPrice()))`（见 [4.1](04-order-paging.md#41-one-lambda-for-the-whole-sort)）。
lambdaQL 把 `id`（`s1.id asc`）加为最后一个排序键，所以排在最前面的行总是相同。
关联条件比较的是外键（`getCustomer().getId()`），而不是客户实体。见 [5.10](#510-paths-and-null-inside-subqueries)。

聚合的 NULL 遵循 SQL。没有行时，`sum`、`avg`、`min`、`max` 返回 NULL，`count` 返回 0。

示例代码：[`SubqueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java)（`scalar`、`count`、`top-one`）。

<a id="58-nested-subqueries"></a>
## 5.8 嵌套子查询

子查询的 `where` 也可以接收 `sub` 参数。每一层都再次适用同样的规则。

拥有包含大数量明细的订单的客户：

<!-- region: SubqueryTest.java#nested -->
```java
// customers that have an order with an item of quantity > 10: a subquery inside a subquery
SelectQuery<Customer> q = qb.from(Customer.class)
        .where((c, sub) -> sub.from(Order.class).where((o, sub2) -> o.getCustomer().getId() == c.getId()
                && sub2.from(Item.class).where(i -> i.getOrder().getId() == o.getId() && i.getQty() > 10).exists()).exists());
assertEquals("select e0 from Customer e0 where exists (select 1 from Order s1 where s1.customer.id = e0.id"
        + " and exists (select 1 from Item s2 where s2.order.id = s1.id and s2.qty > 10))", q.toHql().hql());
```

**各数据库的 SQL**

<!-- sql: sub-nested -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from customers c1_0
where exists(select 1 from orders o1_0 where o1_0.customer_id=c1_0.id and exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id and i1_0.qty>10))
```

Java 不允许在嵌套的 Lambda 表达式中重复使用同一个名字。请在每一层使用新名字（`sub2`、`o`、`i`）。

示例代码：[`SubqueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java)（`nested`）。

<a id="59-sorting-inside-a-subquery"></a>
## 5.9 子查询内部的排序

| 情况 | lambdaQL 的处理 |
|---|---|
| 带 `limit` 的 `orderBy`（top-1、top-N） | 保留，并加上 `id` 决胜键 |
| 不带 `limit` 的 `orderBy` | **删除**并记录警告（不影响结果。Oracle 上甚至会出现语法错误 ORA-00907） |
| 对带 `limit` 的子查询使用 `contains`（MariaDB 不支持 `IN (... limit n)`，错误 1235） | 在所有数据库上包装成派生表：`in (select t1.v from (select s1.id as v ... limit n) t1)` |

[4. 排序与分页](04-order-paging.md)中的 NULL 策略和自动 `id` 决胜键同样适用于子查询内部。

<a id="510-paths-and-null-inside-subqueries"></a>
## 5.10 子查询内部的路径与 NULL

在子查询内部，条件、路径和 NULL 的行为与外层查询**完全相同**（[3. 条件（`where`）](03-where.md)）。

- 对象上的 `==` 是按 SQL 语义的值比较。捕获值 `null` 不匹配任何行（`LQL-4045`）。
- 子查询自身实体的关联路径遵循与外层 `where` 相同的 join 规则。没有关联的行可能满足条件时用 `left join`，不可能满足时用内连接 `join`。
- 以 `@Id` 结尾的路径是外键列（不需要 join）。
- **外层实体**只能使用自己的列和外键 id（`o.getId()`、`o.getCustomer().getId()`）。需要 join 的外层实体路径（`o.getCustomer().getName()`）在分析时报错。请改为比较外键 id。
- 集合路径（`c.getOrders()`）会被拒绝（`LQL-3010`）。要对集合进行判断，请使用子查询。
- 实体的 `@SQLRestriction` 和 `@SoftDelete` 条件，Hibernate 在子查询（`exists`、`in`）内部也会加上（在 MariaDB 10.11、Oracle 23.26 上实测）。lambdaQL 不单独处理这些条件。

下面的示例用子查询而不是 `Customer.orders` 集合查找“有订单的客户”。

<!-- region: ManualJavaSubqueryTest.java#collection-path -->
```java
// a collection path is rejected
LambdaQLException e = assertThrows(LambdaQLException.class,
        () -> qb.from(Customer.class).where(c -> c.getOrders().size() > 0));
assertEquals("LQL-3010", e.code());
// ask with a subquery instead
SelectQuery<Customer> withOrders = qb.from(Customer.class)
        .where((c, sub) -> sub.from(Order.class).where(o -> o.getCustomer().getId() == c.getId()).exists());
assertEquals("select e0 from Customer e0"
        + " where exists (select 1 from Order s1 where s1.customer.id = e0.id)", withOrders.toHql().hql());
```

<a id="joins-inside-a-subquery"></a>
### 子查询中的 join

子查询内部也可以 join 两个实体。写法是 `sub.from(A.class).join(B.class, (a, b) -> ...)` 和 `leftJoin(...)`，结果是 `From2<A, B>`。
`From2` 的所有 Lambda 表达式都接收两行：`where((a, b) -> ...)`、`exists()`、`count()`、`select((a, b) -> v)` 之后的 `contains(x)` 或 `first()`、`max`、`min`、`avg`、`sum((a, b) -> v)`。

- HQL 形如 `exists (select 1 from Order s1 join Item s2 on s2.order.id = s1.id where ...)`。
- ON 条件中的关联路径变成 `left join`。路径 join 放在该路径起始的根之后。
- 子查询中的实体最多两个。join 形式中不能使用 `orderBy` 和 `limit`。
- `exists`、对 join 后各行的 `sum`、使用 `left join` 和 ON 中关联路径的 `in`，已在三种数据库上实测。

<!-- region: SubqueryTest.java#sub-join -->
```java
// customers with an order that has an item of quantity > 10: the subquery joins Order and Item
SelectQuery<Customer> q = qb.from(Customer.class).where((c, sub) -> sub.from(Order.class)
        .join(Item.class, (o, i) -> i.getOrder().getId() == o.getId())
        .where((o, i) -> o.getCustomer().getId() == c.getId() && i.getQty() > 10)
        .exists());
assertEquals("select e0 from Customer e0 where exists (select 1 from Order s1 join Item s2 on s2.order.id = s1.id"
        + " where s1.customer.id = e0.id and s2.qty > 10)", q.toHql().hql());
// an aggregate over the joined rows
SelectQuery<Customer> total = qb.from(Customer.class).where((c, sub) -> sub.from(Order.class)
        .join(Item.class, (o, i) -> i.getOrder().getId() == o.getId())
        .where((o, i) -> o.getCustomer().getId() == c.getId())
        .sum((o, i) -> i.getQty()) > 30);
assertEquals("select e0 from Customer e0 where (select sum(s2.qty) from Order s1 join Item s2 on s2.order.id = s1.id"
        + " where s1.customer.id = e0.id) > 30", total.toHql().hql());
```

示例代码：[`ManualJavaSubqueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaSubqueryTest.java)（`collection-path`）、[`SubqueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java)（`sub-join`）。

<a id="511-subqueries-in-other-places"></a>
## 5.11 其他位置的子查询

- **UPDATE / DELETE 中的子查询**：在 `where` 中使用同样的 `sub` 语法。见 [7.4 删除](07-4-delete.md) 和 [7.2 更新](07-2-update.md)。
- **`set` 值中的子查询**（`o.setTotal(sub.from(...).sum(...))`）：用赋值 Lambda 表达式的第二个参数 `sub` 编写。见 [7.2 更新](07-2-update.md#an-aggregate-from-a-subquery)。
- **`from` 中的子查询（派生表）和 CTE**：已实现。不使用 `sub`，而是把值查询传给 `qb.from(...)` 或 `qb.with(...)`。见 [6.9 派生表](06-select-group.md#69-derived-table-subquery-in-from) 和 [6.10 CTE](06-select-group.md#610-cte-with)。

<a id="512-rules-summary-q1-to-q6"></a>
## 5.12 规则汇总（Q1~Q6）

| # | 规则 |
|---|---|
| Q1 | 子查询内部的条件、路径和 NULL 规则与外部相同。条件中的关联路径也遵循相同的 join 规则（没有关联的行可能满足条件时用 `left join`，否则用内连接 `join`）。需要 join 的外层实体路径会报错，允许外键 id |
| Q2 | **NOT IN 保持 SQL 语义**：`!values.contains(x)` 变成 `x not in (select c.sel from C c where <condition>)`。子查询中有 NULL 时不返回任何行（`DEBUG` 级别 `LQL-5009`）。要跳过 NULL，在子查询中加上 `c.sel != null` |
| Q3 | 标量子查询必须返回一行：聚合（`max`、`min`、`avg`、`sum`、`count`）或 `orderBy(...).limit(1)...first()`。其他 `first()` 是错误 |
| Q4 | 子查询排序：带 `limit` 时保留，不带时删除并发出警告。`IN (... limit)` 包装成派生表 |
| Q5 | 关联条件中 `==` 写成 `=`，`!=` 写成 `<>`（SQL 语义）。任一侧为 NULL 时不匹配。保留索引的使用 |
| Q6 | 子查询的 `where` 可以接收自己的 `sub` 参数：`where((i, sub2) -> ...)`。每一层都适用同样的规则 |

---

[Java 路线](README.md) | 上一章：[4. 排序与分页](04-order-paging.md) | 下一章：[6. 查询列、聚合与分组](06-select-group.md) | [本页的 Kotlin 版本](../kotlin/05-subquery.md)
