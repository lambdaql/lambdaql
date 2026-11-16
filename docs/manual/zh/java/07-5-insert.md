<a id="75-insert"></a>
# 7.5 插入（Java）

> [!NOTE]
> **状态：已实现并测试**，在 H2 2.3、MariaDB 10.11 和 Oracle 23.26 上（里程碑 M10）。
> **尚未实现：** `insert ... select` 中的聚合（里程碑 M9）以及 `insert ... select` 的 `by.set` 写法。
> 标记为 **(已测试)** 的示例是从 [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) 中复制的。其他示例遵循设计，还没有测试覆盖。
> 完整状态见 [Java 路线 README](README.md#status)。

[7. 更新、插入、删除（Java）](07-dml.md) | [7.2 更新](07-2-update.md) | [7.4 删除](07-4-delete.md) | **7.5 插入** | [7.6 Upsert](07-6-upsert.md) | [Kotlin](../kotlin/07-5-insert.md)

---


<a id="one-row"></a>
## 一行

**(已测试)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)，region `insert`：

```java
ExecutableInsert<Archive> i = qb.insert(Archive.class)
        .values(a -> { a.setId(100L); a.setPrice(9.5); a.setCustomerName("Kim"); });
assertEquals("insert into Archive (id, price, customerName) values (100L, 9.5, 'Kim')", i.toHql().get(0).hql());
int inserted = i.execute();
// a generated id (IDENTITY) can be left out
int generated = qb.insert(IdOrder.class).values(o -> { o.setPrice(1.0); o.setStatus(1); }).execute();
```

**关于 SQL 块。** `…` 是缩短的 select 列表（实体的所有列），`?` 是绑定参数。这些行来自 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)。该测试使用 Hibernate 6.6.5 在 H2 2.3、MariaDB 10.11 和 Oracle 23.26 上运行每个查询，如果 Hibernate 的 SQL 发生变化，测试就会失败。 标记为 **（计划中）** 的块来自 [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java)。由于 lambdaQL 目前还不会生成这些语句，该测试把计划中的 HQL 直接交给 Hibernate。

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `insert`：

```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price,customer_name)
values (100,9.5,'Kim')
```

Lambda 中的常量仍是字面量（决定 D37）。值不能读取新行：`a.setPrice(a.getPrice())` 是错误。

一行也可以使用 `by.set` 写法：

```java
qb.insert(Order.class).values((o, by) -> by.set(o.getId(), id).set(o.getStatus(), 1)).execute();
```

<a id="many-rows-from-a-list"></a>
## 从列表插入多行

列表的每个元素对应一行。第二个 Lambda 参数就是元素。

**(已测试)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)，region `insert-list`：

```java
List<Line> lines = List.of(new Line(201, 1.0), new Line(202, 2.0));
ExecutableInsert<Archive> i = qb.insert(Archive.class).values(lines, (a, l) -> { a.setId(l.id()); a.setPrice(l.price()); });
assertEquals("insert into Archive (id, price) values (:p1, :p2), (:p3, :p4)", i.toHql().get(0).hql());
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `insert-list`：

```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price)
values (?,?), (?,?)
```

lambdaQL 在 Java 中读取元素的 getter，所以每个值都是绑定参数。
它写出一条带多个 VALUES 行的语句。较长的列表会被拆分，使每条语句最多有 500 个值（见 [7.8](07-dml.md#batching-for-many-rows)）。空列表不执行 SQL，返回 0。

<a id="generated-id"></a>
## 自动生成的 id

如果 id 使用 IDENTITY 或 SEQUENCE，可以省略 id（规则 U12）。
第一个示例中的 `IdOrder` 那一行就是这样做的：`IdOrder` 的 id 使用 IDENTITY。

<a id="insert--select"></a>
## Insert ... select

从另一个实体复制行。`select` 的 Lambda 接收目标（`a`）和源（`o`）。

**(已测试)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)，region `insert-select`：

```java
ExecutableInsert<Archive> i = qb.insert(Archive.class)
        .from(Order.class)
        .where(o -> o.getStatus() == 1)
        .select((a, o) -> { a.setId(o.getId()); a.setPrice(o.getPrice()); a.setCustomerName(o.getCustomer().getName()); });
// a path in the select list: left join, so orders without a customer stay (U10)
assertEquals("insert into Archive (id, price, customerName) select e0.id, e0.price, j1.name"
        + " from Order e0 left join e0.customer j1 where e0.status = 1", i.toHql().get(0).hql());
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `insert-select`：

```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price,customer_name)
select o1_0.id,o1_0.price,c1_0.name
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where o1_0.status=1
```

lambdaQL 对源路径使用显式的 `left join`，所以没有客户的订单不会丢失（规则 U10）。三种数据库上的 SQL 相同。

设计中还有一种 `by.set` 写法，上下文对象是第三个参数。它**尚未实现**：

```java
qb.insert(Archive.class).from(Order.class).where(o -> o.getStatus() == 9)
  .select((a, o, by) -> by.set(a.getId(), o.getId()).set(a.getCustomerName(), o.getCustomer().getName()))
  .execute();
```

<a id="insert--select-with-aggregates"></a>
## 带聚合的 Insert ... select

使用 [6. 查询列、聚合与分组](06-select-group.md)中的 `groupBy` 和 `Agg` 函数。
这种写法**尚未实现**（里程碑 M9）：

```java
qb.insert(Summary.class).from(Order.class).groupBy(o -> o.getCustomer().getId())
  .select((s, o) -> { s.setCustomerId(o.getCustomer().getId()); s.setTotal(Agg.sum(o.getPrice())); s.setCnt(Agg.count()); })
  .execute();
```

**各数据库的 SQL（计划中）** — Hibernate 对计划中 HQL 的翻译，由 `PlannedSqlTest` 记录；lambdaQL 目前还不会生成此查询。来自 `expected-sql/` 的 [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)，示例 `07-insert-group`：

```sql
-- H2, MariaDB, Oracle (same)
insert into summary(customer_id,total,cnt)
select o1_0.customer_id,sum(o1_0.price),count(*)
from orders o1_0
group by o1_0.customer_id
```

---

---

[7. 更新、插入、删除（Java）](07-dml.md) | [7.2 更新](07-2-update.md) | [7.4 删除](07-4-delete.md) | **7.5 插入** | [7.6 Upsert](07-6-upsert.md) | [Kotlin](../kotlin/07-5-insert.md)
