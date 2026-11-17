<a id="74-delete"></a>
# 7.4 删除（Kotlin）

> [!NOTE]
> **状态：已实现并测试**，在 H2 2.3、MariaDB 10.11 和 Oracle 23.26 上（里程碑 M10）。大多数测试使用 **Java** Lambda。带 `not exists` 子查询的 delete 也已用 Kotlin Lambda 测试（[`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)，标记为 **(已测试)**）。 这也包括 `limit` 和 `executeOne()`（决定 D41）。
> `where` 中的子查询（`sub`）同样已实现，并已用 Java Lambda 测试（里程碑 M8）。
> **尚未实现：** 复合键实体上的 `limit` / `executeOne()`。
> Kotlin 代码用 Kotlin 语法展示已测试的 Java 语句，HQL 来自 [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) 和 [`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java) 中的 Java 测试。没有 HQL 的示例遵循设计。
> 完整状态见[路线 README](README.md)。

[7. 更新、插入、删除（Kotlin）](07-dml.md) | [7.2 更新](07-2-update.md) | **7.4 删除** | [7.5 插入](07-5-insert.md) | [7.6 Upsert](07-6-upsert.md) | [Java](../java/07-4-delete.md)

---


```kotlin
val d = qb.delete(Order::class.java).where { it.status == 3 }
// HQL: delete from Order e0 where e0.status = 3
val deleted = d.execute()

// a path: the same rows as qb.from(Order::class.java).where { same lambda } - orders without a customer stay
val byName = qb.delete(Order::class.java).where { it.customer?.name == null }
// HQL: delete from Order e0 where (select s1.name from Customer s1 where s1.id = e0.customer.id) is null and e0.customer.id is not null
```

**关于 SQL 块。** `…` 是缩短的 select 列表（实体的所有列），`?` 是绑定参数。这些行来自 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)。该测试使用 Hibernate 6.6.5 在 H2 2.3、MariaDB 10.11 和 Oracle 23.26 上运行每个查询，如果 Hibernate 的 SQL 发生变化，测试就会失败。 该测试使用 Java Lambda。含义相同的 Kotlin Lambda 生成相同的 HQL，因此 SQL 也相同。

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `delete`：

```sql
-- H2, Oracle
delete from orders o1_0
where o1_0.status=3

-- MariaDB
delete o1_0 from orders o1_0
where o1_0.status=3
```

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `delete#2`：

```sql
-- H2, Oracle
delete from orders o1_0
where (select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id) is null and o1_0.customer_id is not null

-- MariaDB
delete o1_0 from orders o1_0
where (select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id) is null and o1_0.customer_id is not null
```

MariaDB 写成 `delete o1_0 from orders o1_0`。H2 和 Oracle 写成 `delete from orders o1_0`。

**(已测试)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)，region `insert-delete`：

```kotlin
val i = qb.insert(Archive::class.java).values { it.id = 100L; it.price = 9.5; it.customerName = "Kim" }
assertEquals(1, i.execute())
val d = qb.delete(Order::class.java).where { o, sub -> !sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
assertEquals("delete from Order e0 where not exists (select 1 from Item s1 where s1.order.id = e0.id)", d.toHql().hql())
```

测试先插入一行 `Archive`，然后删除没有商品的订单，并把剩下的行与用 Kotlin 进行的同样过滤作比较。
更多用 Java 写的语句在 [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) 中测试（H2、MariaDB 和 Oracle）。这些示例见[本页的 Java 版本](../java/07-4-delete.md)。

第二条语句删除的行与 `qb.from(Order::class.java).where { it.customer?.name == null }` 返回的**行相同**：客户没有名字的订单。Java 测试会把删除的行与这个查询进行比较。
**没有**客户的订单不会被删除（规则 U9，决策 D36）。要把它们也删除，请写 `it.customer == null || it.customer?.name == null`。有了这个 null 守卫，lambdaQL 不会加上 `e0.customer.id is not null`。

删除所有行：必须写 `all()`（规则 U5）。

```kotlin
qb.delete(Order::class.java).all().execute()     // delete all rows
qb.delete(Order::class.java).execute()           // compile error: no execute()
```

按 id 列表删除（捕获的集合，决定 D29）：

```kotlin
qb.delete(Item::class.java).where { it.id in ids }.execute()
```

`where` 中的子查询使用第二个 Lambda 参数 `sub`，与 [5. 子查询](05-subquery.md) 相同。
删除没有任何订单项的订单：

```kotlin
qb.delete(Order::class.java)
    .where { o, sub -> !sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
    .execute()
// HQL: delete from Order e0 where not exists (select 1 from Item s1 where s1.order.id = e0.id)
```

同样的语句已在 [`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java) 中用 Java 测试（region `dml-subquery`）。

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `dml-subquery`：

```sql
-- H2, Oracle
delete from orders o1_0
where not exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id)

-- MariaDB
delete o1_0 from orders o1_0
where not exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id)
```

与所有 JPA 批量语句一样，删除不会执行 JPA 的 `cascade` 或 `orphanRemoval`，也不会修改已加载的实体（规则 U16）。
删除需要事务（见 [7.8](07-dml.md#transactions)）。

<a id="delete-the-first-rows-limit"></a>
## 删除前几行：`limit`

在 `where { ... }` 或 `all()` 之后写 `orderBy { ... }` 和 `limit(n)`，最多删除 `n` 行：即 `qb.from(X::class.java).where { same }.orderBy { same }` 的前 `n` 行。
可以用它把大量行分成小批逐步处理。

- 没有 `orderBy` 时，按 id 排序。
- lambdaQL 把 id 加为最后一个排序键，因此顺序总是相同（规则 S9，见[决胜键](04-order-paging.md#tie-breaker-automatic-id)）。
- `orderBy` 接受排序 Lambda 或 `SortSpec`，与查询相同（见 [4. 排序与分页](04-order-paging.md)）。
- 没有 `limit` 的 `orderBy` 是错误（`IllegalStateException`），因为这时排序没有作用。

```kotlin
// the first rows of qb.from(Order::class.java).where { same }.orderBy { same }; the id is the last sort key
val d = qb.delete(Order::class.java)
    .where { it.status == 1 }
    .orderBy { o, by -> by.asc(o.price) }
    .limit(1)
// HQL: delete from Order e0 where e0.id in (select t.id from (select x0.id as id from Order x0 where x0.status = 1 order by x0.price asc, x0.id asc limit 1) t)
val deleted = d.execute()
```

用 Java 写的相同语句在 [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) 中测试（region `delete-limit`）。

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `delete-limit`：

```sql
-- H2
delete from orders o1_0
where o1_0.id in (select t1_0.id from (select o2_0.id from orders o2_0 where o2_0.status=1 order by o2_0.price,o2_0.id fetch first 1 rows only) t1_0(id))

-- MariaDB
delete o1_0 from orders o1_0
where o1_0.id in (select t1_0.id from (select o2_0.id id from orders o2_0 where o2_0.status=1 order by o2_0.price,o2_0.id limit 1) t1_0)

-- Oracle
delete from orders o1_0
where o1_0.id in (select t1_0.id from (select o2_0.id id from orders o2_0 where o2_0.status=1 order by o2_0.price,o2_0.id fetch first 1 rows only) t1_0)
```

lambdaQL 把 limit 写成 `e0.id in (select t.id from (select x0.id as id from X x0 ... order by ... limit n) t)`：

- 内层 select 遵循查询的规则，包括连接（决定 D36）。因此语句删除的正是查询返回的行。
- 派生表 `t` 是为 MariaDB 准备的。没有它，MariaDB 会拒绝 `in` 子查询中的 `limit`（错误 1235）和读取目标表的子查询（错误 1093）。
- H2 和 Oracle 写 `fetch first 1 rows only`，MariaDB 写 `limit 1`。

<a id="delete-exactly-one-row-executeone"></a>
## 只删除一行：`executeOne()`

`executeOne()` 以**单行保护**执行删除。当条件应只匹配一行时（例如唯一键），用它代替 `execute()`。

1. lambdaQL 读取匹配行的 id，最多 2 个。
2. 没有 id：什么都不改变。结果为 0。
3. 2 个 id：什么都不改变。`executeOne()` 抛出 JPA 的 `NonUniqueResultException`。
4. 1 个 id：lambdaQL 用 `id = :p and (你的条件)` 删除该行。结果为 1；如果该行在此期间不再匹配，则为 0。

语句只指定一个 id，因此绝不会改变超过一行。这种保护不依赖回滚。

```kotlin
// one row matches: it is deleted
val one = qb.delete(Order::class.java).where { it.id == id }.executeOne()
// two or more rows match: nothing is deleted, NonUniqueResultException
qb.delete(Order::class.java).where { it.price!! > 100.0 }.executeOne()
```

用 Java 写的相同语句在 [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) 中测试（region `execute-one`）。`it.id == id` 成为普通的 `e0.id = :p1`，因为 id 永远不会是 NULL（见 [3.6.2](03-where.md#362--compares-values)）。

- `executeOne()` 与 `orderBy` 或 `limit` 一起使用是错误（`IllegalStateException`）。
- `limit` 和 `executeOne()` 目前还不支持复合键实体。
- 在 Spring 仓库中，`deleteOne { ... }` 做同样的事，并抛出 `IncorrectResultSizeDataAccessException`（见 [12.4](12-spring-data-jpa.md#one-row-protection-deleteone-and-updateone)）。

---

---

[7. 更新、插入、删除（Kotlin）](07-dml.md) | [7.2 更新](07-2-update.md) | **7.4 删除** | [7.5 插入](07-5-insert.md) | [7.6 Upsert](07-6-upsert.md) | [Java](../java/07-4-delete.md)
