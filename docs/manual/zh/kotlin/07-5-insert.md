<a id="75-insert"></a>
# 7.5 插入（Kotlin）

> [!NOTE]
> **状态：已设计，尚未实现。**
> 本章是已确定的设计（决定 D28，里程碑 M10）。
> 该 Kotlin 语法可以编译（kotlinc 2.3.21），SQL 已在 H2、MariaDB 和 Oracle 上使用 Hibernate 6.6.5 检查过。
> 目前它在 lambdaQL 中**还不能**运行。目前只有分析核心以及 `where` + `toList()` 可以工作
> （已在 H2、MariaDB 和 Oracle 上测试）。完整状态见[路线 README](README.md)。

[7. 更新、插入、删除（Kotlin）](07-dml.md) | [7.2 更新](07-2-update.md) | [7.4 删除](07-4-delete.md) | **7.5 插入** | [Java](../java/07-5-insert.md)

---


## 一行

```kotlin
qb.insert(Order::class.java).values { it.id = id; it.price = p; it.status = 1 }.execute()

// 使用 by，适用于 val 属性
qb.insert(Order::class.java).values { o, by -> by[o.id] = id; by[o.status] = 1 }.execute()
```

## 从列表插入多行

列表的每个元素对应一行。第二个 Lambda 参数是元素。

```kotlin
val lines = listOf(Line(1L, 10.0), Line(2L, 20.0))

qb.insert(Order::class.java)
    .values(lines) { o, l -> o.id = l.id; o.price = l.price; o.status = 1 }
    .execute()
```

lambdaQL 会写出一条带多个 VALUES 行的语句。如果列表很大，它会按**绑定值的个数**拆分工作（见 [7.7](07-dml.md#batching-for-many-rows)）。

## 生成的 id

如果 id 使用 IDENTITY 或 SEQUENCE，可以省略 id（规则 U12）：

```kotlin
qb.insert(IdOrder::class.java).values { it.price = p; it.status = 1 }.execute()
```

## Insert ... select

从另一个实体复制行。`select` Lambda 接收目标（`a`）和源（`o`）。

```kotlin
qb.insert(Archive::class.java).from(Order::class.java).where { it.status == 9 }
    .select { a, o -> a.id = o.id; a.price = o.price; a.customerName = o.customer?.name }
    .execute()
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

lambdaQL 对源路径使用显式的 `left join`，因此没有客户的订单不会丢失（规则 U10）。

使用 `by` 时，上下文对象是第三个参数：

```kotlin
qb.insert(Archive::class.java).from(Order::class.java).where { it.status == 9 }
    .select { a, o, by -> by[a.id] = o.id; by[a.customerName] = o.customer?.name }
    .execute()
```

## 带聚合的 Insert ... select

使用 `groupBy` 以及 [6. 查询列、聚合与分组](06-select-group.md) 中的 `Agg` 函数：

```kotlin
qb.insert(Summary::class.java).from(Order::class.java).groupBy { it.customer?.id }
    .select { s, o -> s.customerId = o.customer?.id; s.total = Agg.sum(o.price); s.cnt = Agg.count() }
    .execute()
```

---

---

[7. 更新、插入、删除（Kotlin）](07-dml.md) | [7.2 更新](07-2-update.md) | [7.4 删除](07-4-delete.md) | **7.5 插入** | [Java](../java/07-5-insert.md)
