<a id="5-subqueries-java"></a>
# 5. 子查询（Java）

[Java 路线](README.md) | [手册首页](../README.md) | 上一章：[4. 排序与分页](04-order-paging.md) | 下一章：[6. 查询列、聚合与分组](06-select-group.md) | [本页的 Kotlin 版本](../kotlin/05-subquery.md)

公共页面：[1. 概述](../01-overview.md) · [11. 错误与语言](../11-errors-and-languages.md)

---

<a id="51-the-idea-a-second-lambda-argument-sub"></a>
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
| `!... .contains(y)` | 条件 | `y not in (select x.v from X x where ...)` |
| `sub.from(X.class).avg(x -> v)`（还有 `sum`、`min`、`max`、`count()`） | 值 | `(select avg(x.v) from X x)` |
| `sub.from(X.class)...orderBy(...).limit(1).select(x -> v).first()` | 值 | top-1 子查询 |
| `sub.from(A.class).join(B.class, (a, b) -> ...).where((a, b) -> ...).exists()` | 条件 | `exists (select 1 from A a join B b on ... where ...)`（[子查询中的 join](#joins-inside-a-subquery)） |

> [!NOTE]
> 返回值（`boolean`、`Double` 等）只是让代码能够编译的占位符。
> 这个 Lambda 从不会被执行。这些方法只在 lambdaQL 的 Lambda 内部才有意义。

内部 Lambda（`sub.from(...).where(...)` 里的 `i -> ...`）本身不需要是 `Serializable`。lambdaQL 会在外层 Lambda 的字节码中找到它们。

在 HQL 中，子查询的根别名是 `s1`、`s2` 等（外层的根是 `e0`）。子查询内部的 join 使用 `s1j1` 这样的别名。

示例是从 [`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java) 中复制的。
该测试检查 HQL，执行查询，并把返回的行与在 Java 中用同一条件过滤的结果进行比较。它在 H2、MariaDB 和 Oracle 上都能通过。
它使用 [3.3](03-where.md#33-basic-form) 中的测试实体：`price` 是 `double`，`status` 是 `short`，`Customer.id` 是 `int`。`Customer.grade` 是 `Integer`。测试数据中的等级为 5、2 和 NULL，并且有一个没有客户的订单。

## 5.2 EXISTS

至少有一个 `qty > 10` 的明细的订单：

[`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java)，region `exists`：

```java
// correlated: the inner lambda uses the outer order o
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId() && i.getQty() > 10).exists());
assertEquals("select e0 from Order e0 where exists (select 1 from Item s1 where s1.order.id = e0.id and s1.qty > 10)",
        q.toHql().hql());
```

**关于 SQL 块。** `…` 是缩短的 select 列表（实体的所有列），`?` 是绑定参数。这些行来自 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)。该测试使用 Hibernate 7.4 在 H2 2.3、MariaDB 10.11 和 Oracle 23.26 上运行每个查询，如果 Hibernate 的 SQL 发生变化，测试就会失败。

**各数据库的 SQL** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `sub-exists`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id and i1_0.qty>10)
```

这是一个**关联**子查询：内部 Lambda 使用了外层的 `o`（见 [5.6](#56-correlated-subqueries)）。

## 5.3 NOT EXISTS

使用 `!`。它可以与其他条件组合。
状态为 1 的订单，或者没有任何明细的订单：

[`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java)，region `not-exists`：

```java
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> o.getStatus() == 1 || !sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).exists());
assertEquals("select e0 from Order e0 where e0.status = 1 or not exists (select 1 from Item s1 where s1.order.id = e0.id)",
        q.toHql().hql());
```

**各数据库的 SQL** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `sub-not-exists`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 or not exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id)
```

## 5.4 IN

`grade > 3` 的客户的订单：

[`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java)，region `in`：

```java
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> sub.from(Customer.class).where(c -> c.getGrade() != null && c.getGrade() > 3)
                .select(c -> c.getId()).contains(o.getCustomer().getId()));
assertEquals("select e0 from Order e0 where e0.customer.id in"
        + " (select s1.id from Customer s1 where s1.grade is not null and s1.grade > 3)", q.toHql().hql());
```

**各数据库的 SQL** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `sub-in`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id in (select c2_0.id from customers c2_0 where c2_0.grade is not null and c2_0.grade>3)
```

`o.getCustomer().getId()` 以客户的 `@Id` 结尾，所以它就是外键列 `e0.customer.id`。不需要 join。
你写的 null 检查（`c.getGrade() != null`）会作为 `s1.grade is not null` 保留在 HQL 中。

<a id="55-not-in-is-written-as-not-exists"></a>
## 5.5 NOT IN 与 NULL

`!values.contains(x)` 按 SQL 语义原样渲染为 `NOT IN`（决策 D48）。

[`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java)，region `not-in`：

```java
// the SQL meaning (D48): NOT IN. The grades are 5, 2 and NULL, so no row matches: x NOT IN (5, 2, NULL)
// is never true. To skip the NULL, say so in the subquery: where(c -> c.getGrade() != null)
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> !sub.from(Customer.class).select(c -> c.getGrade()).contains((int) o.getStatus()));
assertEquals("select e0 from Order e0 where e0.status not in (select s1.grade from Customer s1)", q.toHql().hql());
SelectQuery<Order> withoutNull = qb.from(Order.class)
        .where((o, sub) -> !sub.from(Customer.class).where(c -> c.getGrade() != null).select(c -> c.getGrade())
                .contains((int) o.getStatus()));
```

**各数据库的 SQL** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `sub-not-in`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status not in (select c2_0.grade from customers c2_0)
```

原因：

- 只要子查询返回哪怕一个 NULL，`x NOT IN (subquery)` 就**一行也不返回**。lambdaQL 保留这一 SQL 语义（决策 D48），并以 `DEBUG` 级别记录 `LQL-5009`。
- 要跳过 NULL，请像测试中的第二个查询那样，在子查询中写 `where(c -> c.getGrade() != null)`。这样得到的结果与排除 NULL 等级后 Java 中 `!grades.contains(status)` 的结果相同。
- `!sub...exists()` 仍然写成 `NOT EXISTS`。

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
| 外层实体（`o`） | 对外层别名的引用（`e0.id`） |
| 其他任何值（局部变量、字段等） | 绑定参数，与 [3.11](03-where.md#311-captured-values) 相同 |

```java
int minQty = 10;
.where((o, sub) -> sub.from(Item.class)
                      .where(i -> i.getOrder().getId() == o.getId() && i.getQty() > minQty)
                      .exists())
// HQL: select e0 from Order e0
//      where exists (select 1 from Item s1 where s1.order.id = e0.id and s1.qty > :p1)
// 参数：{p1=10}
```

[`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java) 中的测试方法 `capturedValueInside` 检查了这个 HQL、参数和返回的行。

**各数据库的 SQL（Hibernate 为此 HQL 生成的 SQL）** — 由 [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java) 记录，它把同样的 HQL 直接交给 Hibernate。来自 `expected-sql/` 的 [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)，示例 `05-correlated-param`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id and i1_0.qty>?)
```

**关联中的 `==`。** `i.getOrder().getId() == o.getId()` 比较的是两个 `Long` 对象。两个实体值按 SQL 语义比较（决策 D48，见 [3.6](03-where.md#36-null-rules)），因此 lambdaQL 写成简单的 `=`（`!=` 则写成 `<>`）。
数据库可以使用索引。

<a id="57-scalar-subqueries"></a>
## 5.7 标量子查询

返回**一个值**的子查询可以用在比较中。

价格高于平均价格的订单：

[`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java)，region `scalar`：

```java
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> o.getPrice() > sub.from(Order.class).avg(x -> x.getPrice()));
assertEquals("select e0 from Order e0 where e0.price > (select avg(s1.price) from Order s1)", q.toHql().hql());
```

**各数据库的 SQL** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `sub-scalar`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.price>(select avg(o2_0.price) from orders o2_0)
```

标量子查询必须**始终返回一行**。允许两种形式（规则 Q3）：

| 形式 | 示例 |
|---|---|
| 聚合：`count()`、`sum`、`avg`、`min`、`max` | `sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).count()` |
| Top-1：`orderBy(...).limit(1)...first()` | 见下文 |

其他任何 `first()` 都会在分析时报错（`TranslationException`，`LQL-3017`）。

有两个或更多明细的订单（`count()`）：

[`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java)，region `count`：

```java
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).count() >= 2);
assertEquals("select e0 from Order e0 where (select count(s1) from Item s1 where s1.order.id = e0.id) >= 2L", q.toHql().hql());
```

**各数据库的 SQL** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `sub-count`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where (select count(i1_0.id) from items i1_0 where i1_0.order_id=o1_0.id)>=2
```

Top-1：每个客户最贵的订单。

[`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java)，region `top-one`：

```java
// the most expensive order of each customer: a correlated scalar subquery with order + limit 1 (rule Q3)
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> o.getId().equals(sub.from(Order.class)
                .where(x -> x.getCustomer().getId() == o.getCustomer().getId())
                .orderBy((x, by) -> by.desc(x.getPrice()))
                .limit(1)
                .select(x -> x.getId())
                .first()));
assertEquals("select e0 from Order e0 where e0.id = (select s1.id from Order s1 where s1.customer.id = e0.customer.id"
        + " order by s1.price desc, s1.id asc limit 1)", q.toHql().hql());
// first() without limit(1) is rejected
assertThrows(LambdaQLException.class, () -> qb.from(Order.class)
        .where((o, sub) -> o.getId().equals(sub.from(Order.class).select(x -> x.getId()).first())));
```

**各数据库的 SQL** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `sub-top-one`：

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

lambdaQL 把 `id` 作为最后一个排序键（`s1.id asc`）加上，所以排在最前面的行总是相同的（规则 S9）。
关联比较的是外键（`getCustomer().getId()`），而不是客户实体。见 [5.10](#510-paths-and-null-inside-subqueries)。

聚合的 NULL 遵循 SQL：没有行时，`sum`、`avg`、`min`、`max` 返回 NULL，`count` 返回 0。

<a id="58-nested-subqueries"></a>
## 5.8 嵌套子查询

子查询的 `where` 也可以接收 `sub` 参数。同样的规则在每一层都适用。

有订单包含大数量明细的客户：

[`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java)，region `nested`：

```java
// customers that have an order with an item of quantity > 10: a subquery inside a subquery
SelectQuery<Customer> q = qb.from(Customer.class)
        .where((c, sub) -> sub.from(Order.class).where((o, sub2) -> o.getCustomer().getId() == c.getId()
                && sub2.from(Item.class).where(i -> i.getOrder().getId() == o.getId() && i.getQty() > 10).exists()).exists());
assertEquals("select e0 from Customer e0 where exists (select 1 from Order s1 where s1.customer.id = e0.id"
        + " and exists (select 1 from Item s2 where s2.order.id = s1.id and s2.qty > 10))", q.toHql().hql());
```

**各数据库的 SQL** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `sub-nested`：

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
| 带 `limit` 的 `orderBy`（top-1、top-N） | 保留，并加上 `id` 决胜键 |
| 不带 `limit` 的 `orderBy` | **删除它**并记录警告（它不会改变结果；Oracle 甚至会报语法错误 ORA-00907） |
| 对带 `limit` 的子查询使用 `contains`（MariaDB 不支持 `IN (... limit n)`，错误 1235） | 在所有数据库上都包装成派生表：`in (select t1.v from (select s1.id as v ... limit n) t1)` |
| MariaDB：`lateral`（在 MariaDB 上结果错误） | 用 `row_number() over (partition by ... order by ...)` 改写：**尚未实现** |

[4. 排序与分页](04-order-paging.md)中的 NULL 策略和自动 `id` 决胜键在子查询内部同样适用。

<a id="510-paths-and-null-inside-subqueries"></a>
## 5.10 子查询内部的路径与 NULL

在子查询内部，条件、路径和 NULL 的行为与外层查询**完全相同**（[3. 条件](03-where.md)）：

- 对象上的 `==` 是按 SQL 语义的值比较（D48）。捕获值 `null` 不匹配任何行（`LQL-4045`）。
- 子查询自身实体的关联路径遵循与外层 `where` 相同的 join 规则：始终是内连接 `join`（决策 D48）。
- 以 `@Id` 结尾的路径是外键列（不需要 join）。
- **外层实体**只能使用它自己的列和外键 id（`o.getId()`、`o.getCustomer().getId()`）。需要 join 的外层实体路径（`o.getCustomer().getName()`）会在分析时报错。请改为比较外键 id。
- 集合路径（`o.getItems()`）会被拒绝。要对集合进行判断，请使用子查询。
- 实体的 `@SQLRestriction` 和 `@SoftDelete` 条件，Hibernate 在子查询（`exists`、`in`）内部也会加上（在 MariaDB 10.11、Oracle 23.26 上实测）。lambdaQL 不单独处理这些条件。

```java
// 代替 o.getItems().size() > 0（会被拒绝）：
.where((o, sub) -> sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).exists())
```

<a id="joins-inside-a-subquery"></a>
### 子查询中的 join

子查询内部也可以 join 两个实体（决策 D51）。写法是 `sub.from(A.class).join(B.class, (a, b) -> ...)` 和 `leftJoin(...)`，结果是 `From2<A, B>`。
`From2` 的所有 Lambda 都接收两行：`where((a, b) -> ...)`、`exists()`、`count()`、`select((a, b) -> v)` 之后的 `contains(x)` 或 `first()`、`max`、`min`、`avg`、`sum((a, b) -> v)`。

- HQL 形如 `exists (select 1 from Order s1 join Item s2 on s2.order.id = s1.id where ...)`。
- ON 条件中的关联路径变成 `left join`。路径 join 放在该路径起始的根之后。
- 子查询中的实体最多两个。join 形式中不能使用 `orderBy` 和 `limit`。
- `exists`、对 join 后各行的 `sum`、以及使用 `left join` 和 ON 中关联路径的 `in`，已在三种数据库上实测。

[`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java)，region `sub-join`：

```java
// customers with an order that has an item of quantity > 10: the subquery joins Order and Item (D51)
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

<a id="511-subqueries-in-other-places"></a>
## 5.11 其他位置的子查询

- **UPDATE / DELETE 中的子查询**：`where` 中使用同样的 `sub` 语法。已实现并测试（region `dml-subquery`）。见 [7.4 删除](07-4-delete.md) 和 [7.2 更新](07-2-update.md)。
- **`set` 值中的子查询**（`o.setTotal(sub.from(...).sum(...))`）：用赋值 Lambda 的第二个参数 `sub` 编写（决策 D49）。见 [7.2 更新](07-2-update.md#an-aggregate-from-a-subquery)。
- **`from` 中的子查询（派生表）和 CTE**：已实现（里程碑 M8-2）。它们不使用 `sub`：把值查询传给 `qb.from(...)` 或 `qb.with(...)`。见 [6.9 派生表](06-select-group.md#69-derived-table-subquery-in-from) 和 [6.10 CTE](06-select-group.md#610-cte-with)。

<a id="512-rules-summary-q1-to-q6"></a>
## 5.12 规则汇总（Q1 至 Q6）

| # | 规则 |
|---|---|
| Q1 | 子查询内部的条件、路径和 NULL 规则与外部相同。条件中的关联路径使用相同的 join 规则（默认是内连接 `join`）。需要 join 的外层实体路径会报错；允许外键 id |
| Q2 | **NOT IN 保持 SQL 语义**（D48）：`!values.contains(x)` 变成 `x not in (select c.sel from C c where <condition>)`。子查询中有 NULL 时不返回任何行（`DEBUG` 级别 `LQL-5009`）。要跳过 NULL，请在子查询中加上 `c.sel != null` |
| Q3 | 标量子查询必须返回一行：聚合（`max`、`min`、`avg`、`sum`、`count`）或 `orderBy(...).limit(1)...first()`。其他任何 `first()` 都是错误 |
| Q4 | 子查询排序：带 `limit` 时保留，不带 `limit` 时删除并发出警告。`IN (... limit)` 会被包装成派生表。MariaDB 用 `row_number()` 改写 `lateral` 尚未实现 |
| Q5 | 在关联中，`==` 写成 `=`，`!=` 写成 `<>`（SQL 语义，D48）：任一侧为 NULL 时不匹配。这样可以保留索引的使用 |
| Q6 | 子查询的 `where` 可以接收自己的 `sub` 参数：`where((i, sub2) -> ...)`。同样的规则在每一层都适用 |

---

[Java 路线](README.md) | 上一章：[4. 排序与分页](04-order-paging.md) | 下一章：[6. 查询列、聚合与分组](06-select-group.md) | [本页的 Kotlin 版本](../kotlin/05-subquery.md)
