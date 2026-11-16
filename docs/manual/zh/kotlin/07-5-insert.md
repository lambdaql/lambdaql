<a id="75-insert"></a>
# 7.5 插入（Kotlin）

> [!NOTE]
> **状态：已实现并测试**，在 H2 2.3、MariaDB 10.11 和 Oracle 23.26 上（里程碑 M10）。测试使用 **Java** Lambda。**DML 中的 Kotlin Lambda 尚未测试。**
> **尚未实现：** `insert ... select` 中的聚合（里程碑 M9）以及 `insert ... select` 的 `by.set` 写法。
> Kotlin 代码用 Kotlin 语法展示已测试的 Java 语句，HQL 来自 [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) 中的 Java 测试。没有 HQL 的示例遵循设计。
> 完整状态见[路线 README](README.md)。

[7. 更新、插入、删除（Kotlin）](07-dml.md) | [7.2 更新](07-2-update.md) | [7.4 删除](07-4-delete.md) | **7.5 插入** | [7.6 Upsert](07-6-upsert.md) | [Java](../java/07-5-insert.md)

---


<a id="one-row"></a>
## 一行

```kotlin
val i = qb.insert(Archive::class.java)
    .values { it.id = 100L; it.price = 9.5; it.customerName = "Kim" }
// HQL: insert into Archive (id, price, customerName) values (100L, 9.5, 'Kim')
val inserted = i.execute()
// a generated id (IDENTITY) can be left out
val generated = qb.insert(IdOrder::class.java).values { it.price = 1.0; it.status = 1 }.execute()
```

**关于 SQL 块。** `…` 是缩短的 select 列表（实体的所有列），`?` 是绑定参数。这些行来自 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)。该测试使用 Hibernate 6.6.5 在 H2 2.3、MariaDB 10.11 和 Oracle 23.26 上运行每个查询，如果 Hibernate 的 SQL 发生变化，测试就会失败。 该测试使用 Java Lambda。含义相同的 Kotlin Lambda 生成相同的 HQL，因此 SQL 也相同。 标记为 **（计划中）** 的块来自 [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java)。由于 lambdaQL 目前还不会生成这些语句，该测试把计划中的 HQL 直接交给 Hibernate。

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `insert`：

```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price,customer_name)
values (100,9.5,'Kim')
```

目前还没有 Kotlin 测试。用 Java 写的相同语句在 [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) 中测试（H2、MariaDB 和 Oracle）。已测试的示例见[本页的 Java 版本](../java/07-5-insert.md)。

Lambda 中的常量仍是字面量（决定 D37）。值不能读取新行：`it.price = it.price` 是错误。

对于 `val` 属性，一行也可以使用 `by.set` 写法：

```kotlin
qb.insert(Order::class.java).values { o, by -> by.set(o.id, id).set(o.status, 1) }.execute()
```

<a id="many-rows-from-a-list"></a>
## 从列表插入多行

列表的每个元素对应一行。第二个 Lambda 参数是元素。

```kotlin
val lines = listOf(Line(201L, 1.0), Line(202L, 2.0))
val i = qb.insert(Archive::class.java).values(lines) { a, l -> a.id = l.id; a.price = l.price }
// HQL: insert into Archive (id, price) values (:p1, :p2), (:p3, :p4)
```

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `insert-list`：

```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price)
values (?,?), (?,?)
```

lambdaQL 在 Java 中读取元素的属性，所以每个值都是绑定参数。
它会写出一条带多个 VALUES 行的语句。较长的列表会被拆分，使每条语句最多有 500 个值（见 [7.8](07-dml.md#batching-for-many-rows)）。空列表不执行 SQL，返回 0。

<a id="generated-id"></a>
## 生成的 id

如果 id 使用 IDENTITY 或 SEQUENCE，可以省略 id（规则 U12）。
第一个示例中的 `IdOrder` 那一行就是这样做的：`IdOrder` 的 id 是自动生成的。

<a id="insert--select"></a>
## Insert ... select

从另一个实体复制行。`select` Lambda 接收目标（`a`）和源（`o`）。

```kotlin
val i = qb.insert(Archive::class.java)
    .from(Order::class.java)
    .where { it.status == 1 }
    .select { a, o -> a.id = o.id; a.price = o.price; a.customerName = o.customer?.name }
// a path in the select list: left join, so orders without a customer stay (U10)
// HQL: insert into Archive (id, price, customerName) select e0.id, e0.price, j1.name from Order e0 left join e0.customer j1 where e0.status = 1
```

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `insert-select`：

```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price,customer_name)
select o1_0.id,o1_0.price,c1_0.name
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where o1_0.status=1
```

lambdaQL 对源路径使用显式的 `left join`，因此没有客户的订单不会丢失（规则 U10）。三种数据库上的 SQL 相同。

设计中还有一种 `by.set` 写法，上下文对象是第三个参数。它**尚未实现**：

```kotlin
qb.insert(Archive::class.java).from(Order::class.java).where { it.status == 9 }
    .select { a, o, by -> by.set(a.id, o.id).set(a.customerName, o.customer?.name) }
    .execute()
```

<a id="insert--select-with-aggregates"></a>
## 带聚合的 Insert ... select

使用 `groupBy` 以及 [6. 查询列、聚合与分组](06-select-group.md) 中的 `Agg` 函数。
这种写法**尚未实现**（里程碑 M9）：

```kotlin
qb.insert(Summary::class.java).from(Order::class.java).groupBy { it.customer?.id }
    .select { s, o -> s.customerId = o.customer?.id; s.total = Agg.sum(o.price); s.cnt = Agg.count() }
    .execute()
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

[7. 更新、插入、删除（Kotlin）](07-dml.md) | [7.2 更新](07-2-update.md) | [7.4 删除](07-4-delete.md) | **7.5 插入** | [7.6 Upsert](07-6-upsert.md) | [Java](../java/07-5-insert.md)
