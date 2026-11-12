<a id="74-delete"></a>
# 7.4 删除（Kotlin）

> [!NOTE]
> **状态：已设计，尚未实现。**
> 本章是已确定的设计（决定 D28，里程碑 M10）。
> 该 Kotlin 语法可以编译（kotlinc 2.3.21），SQL 已在 H2、MariaDB 和 Oracle 上使用 Hibernate 6.6.5 检查过。
> 目前它在 lambdaQL 中**还不能**运行。目前只有分析核心以及 `where` + `toList()` 可以工作
> （已在 H2、MariaDB 和 Oracle 上测试）。完整状态见[路线 README](README.md)。

[7. 更新、插入、删除（Kotlin）](07-dml.md) | [7.2 更新](07-2-update.md) | **7.4 删除** | [7.5 插入](07-5-insert.md) | [Java](../java/07-4-delete.md)

---


```kotlin
qb.delete(Order::class.java).where { it.status == 9 }.execute()

qb.delete(Order::class.java).where { it.customer?.name == null }.execute()

qb.delete(Order::class.java)
    .where { o, sub -> !sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
    .execute()

qb.delete(Order::class.java).all().execute()     // 删除所有行：必须写 all()
```

第二个示例的 HQL：

```sql
delete from Order o
where exists (select 1 from Customer c where c.id = o.customer.id and c.name is null)
```

**关于 SQL 块。** `…` 是缩短的 select 列表（实体的所有列），`?` 是绑定参数。[`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java) 把本页的 HQL 直接交给 Hibernate，并记录在 H2 2.3、MariaDB 10.11 和 Oracle 23.26（Hibernate 6.6.5）上得到的 SQL。lambdaQL 目前还不会生成这些查询。如果 Hibernate 的 SQL 发生变化，测试就会失败。

**各数据库的 SQL（计划中）** — Hibernate 对计划中 HQL 的翻译，由 `PlannedSqlTest` 记录；lambdaQL 目前还不会生成此查询。来自 `expected-sql/` 的 [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)，示例 `07-delete-exists`：

```sql
-- H2, Oracle
delete from orders o1_0
where exists(select 1 from customers c1_0 where c1_0.id=o1_0.customer_id and c1_0.name is null)

-- MariaDB
delete o1_0 from orders o1_0
where exists(select 1 from customers c1_0 where c1_0.id=o1_0.customer_id and c1_0.name is null)
```

MariaDB 写成 `delete o1_0 from orders o1_0`。H2 和 Oracle 写成 `delete from orders o1_0`。

这会删除与 `qb.from(Order::class.java).where { it.customer?.name == null }` 返回的**相同的行**：客户没有名字的订单。
**没有**客户的订单不会被删除（规则 U9，决策 D36）。要把它们也删除，请写 `it.customer == null || it.customer?.name == null`。

按 id 列表删除（捕获的集合，决定 D29）：

```kotlin
qb.delete(Item::class.java).where { it.id in ids }.execute()
```

---

---

[7. 更新、插入、删除（Kotlin）](07-dml.md) | [7.2 更新](07-2-update.md) | **7.4 删除** | [7.5 插入](07-5-insert.md) | [Java](../java/07-4-delete.md)
