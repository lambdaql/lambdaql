<a id="75-insert"></a>
# 7.5 插入（Java）

> [!NOTE]
> **状态：已设计，尚未实现。**
> 本章是已确定的设计（决定 D28，里程碑 M10）。
> 该语法在 Java 中可以编译，SQL 已在 H2、MariaDB 和 Oracle 上使用 Hibernate 6.6.5 检查过。
> 目前它在 lambdaQL 中**还不能**运行。目前只有分析核心以及 `where` + `toList()` 可以工作
> （已在 H2、MariaDB 和 Oracle 上测试）。完整状态见 [Java 路线 README](README.md#status)。

[7. 更新、插入、删除（Java）](07-dml.md) | [7.2 更新](07-2-update.md) | [7.4 删除](07-4-delete.md) | **7.5 插入** | [Kotlin](../kotlin/07-5-insert.md)

---


## 一行

```java
qb.insert(Order.class).values(o -> { o.setId(id); o.setPrice(p); o.setStatus(1); }).execute();
```

## 从列表插入多行

列表的每个元素对应一行。第二个 Lambda 参数就是元素。

```java
qb.insert(Order.class)
  .values(lines, (o, l) -> { o.setId(l.id()); o.setPrice(l.price()); o.setStatus(1); })
  .execute();
```

lambdaQL 写出一条带多个 VALUES 行的语句。如果列表很大，它会按**绑定值的数量**拆分工作（见 [7.7](07-dml.md#batching-for-many-rows)）。

## 自动生成的 id

如果 id 使用 IDENTITY 或 SEQUENCE，可以省略 id（规则 U12）：

```java
qb.insert(IdOrder.class).values(o -> { o.setPrice(p); o.setStatus(1); }).execute();
```

## Insert ... select

从另一个实体复制行。`select` 的 Lambda 接收目标（`a`）和源（`o`）。

```java
qb.insert(Archive.class).from(Order.class).where(o -> o.getStatus() == 9)
  .select((a, o) -> { a.setId(o.getId()); a.setPrice(o.getPrice()); a.setCustomerName(o.getCustomer().getName()); })
  .execute();
```

```sql
insert into Archive (id, price, customerName)
select o.id, o.price, c.name from Order o left join o.customer c where o.status = 9
```

**关于 SQL 块。** `…` 是缩短的 select 列表（实体的所有列），`?` 是绑定参数。[`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java) 把本页的 HQL 直接交给 Hibernate，并记录在 H2 2.3、MariaDB 10.11 和 Oracle 23.26（Hibernate 6.6.5）上得到的 SQL。lambdaQL 目前还不会生成这些查询。如果 Hibernate 的 SQL 发生变化，测试就会失败。

**各数据库的 SQL（计划中）** — Hibernate 对计划中 HQL 的翻译，由 `PlannedSqlTest` 记录；lambdaQL 目前还不会生成此查询。来自 `expected-sql/` 的 [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)，示例 `07-insert-select`：

```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price,customer_name)
select o1_0.id,o1_0.price,c1_0.name
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where o1_0.status=9
```

lambdaQL 对源路径使用显式的 `left join`，所以没有客户的订单不会丢失（规则 U10）。

使用 `by.set` 时，上下文对象是第三个参数：

```java
qb.insert(Archive.class).from(Order.class).where(o -> o.getStatus() == 9)
  .select((a, o, by) -> by.set(a.getId(), o.getId()).set(a.getCustomerName(), o.getCustomer().getName()))
  .execute();
```

## 带聚合的 Insert ... select

使用 [6. 查询列、聚合与分组](06-select-group.md)中的 `groupBy` 和 `Agg` 函数：

```java
qb.insert(Summary.class).from(Order.class).groupBy(o -> o.getCustomer().getId())
  .select((s, o) -> { s.setCustomerId(o.getCustomer().getId()); s.setTotal(Agg.sum(o.getPrice())); s.setCnt(Agg.count()); })
  .execute();
```

---

---

[7. 更新、插入、删除（Java）](07-dml.md) | [7.2 更新](07-2-update.md) | [7.4 删除](07-4-delete.md) | **7.5 插入** | [Kotlin](../kotlin/07-5-insert.md)
