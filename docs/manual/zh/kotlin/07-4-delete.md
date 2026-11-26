<a id="74-delete"></a>
# 7.4 删除（Kotlin）

[7. 更新、插入、删除（Kotlin）](07-dml.md) | [7.2 更新](07-2-update.md) | **7.4 删除** | [7.5 插入](07-5-insert.md) | [7.6 Upsert](07-6-upsert.md) | [Java](../java/07-4-delete.md)

---

<!-- region: ManualKotlinDmlTest.kt#delete -->
```kotlin
val d = qb.delete(Order::class.java).where { it.status.toInt() == 3 }
assertEquals("delete from Order e0 where e0.status = 3", d.toHql().hql())
val deleted = d.execute()

// a path: the same rows as qb.from(Order::class.java).where { same lambda } - with !! orders without a customer stay
val byName = qb.delete(Order::class.java).where { it.customer!!.name == null }
assertEquals("delete from Order e0 where (select s1.name from Customer s1 where s1.id = fk(e0.customer)) is null"
    + " and e0.customer.id is not null", byName.toHql().hql())
```

**各数据库的 SQL**

<!-- sql: delete -->
```sql
-- H2, Oracle
delete from orders o1_0
where o1_0.status=3

-- MariaDB
delete o1_0 from orders o1_0
where o1_0.status=3
```

**各数据库的 SQL**

<!-- sql: delete#2 -->
```sql
-- H2, Oracle
delete from orders o1_0
where (select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id) is null and o1_0.customer_id is not null

-- MariaDB
delete from orders
where (select c1_0.name from customers c1_0 where c1_0.id=orders.customer_id) is null and customer_id is not null
```

MariaDB 写成 `delete o1_0 from orders o1_0`。H2 和 Oracle 写成 `delete from orders o1_0`。

第二条语句删除的行与 `qb.from(Order::class.java).where { it.customer!!.name == null }` 返回的**行相同**，即客户没有名字的订单。
**没有**客户的订单不会被删除，因为经过 `!!` 的比较只在有客户的订单上为真。

如果写成 `?.`（`it.customer?.name == null`），没有客户的订单也符合条件，因此查询成为 `left join`，DML 不会添加 `e0.customer.id is not null`。所以没有客户的订单也会被删除。
<!-- region: ManualKotlinDmlTest.kt#delete-safe-call -->
```kotlin
// ?. : no "customer exists" check, so orders without a customer are deleted too, as the select returns them
val d = qb.delete(Order::class.java).where { it.customer?.name == null }
assertEquals("delete from Order e0 where (select s1.name from Customer s1 where s1.id = fk(e0.customer)) is null",
    d.toHql().hql())
```

同样方式的 UPDATE 示例见 [7.2](07-2-update.md)。只删除没有客户的订单时，请写 `it.customer == null`。

删除所有行时需要 `all()`。
按 id 列表删除时，使用捕获的集合。

<!-- region: ManualKotlinDmlTest.kt#delete-all -->
```kotlin
// ids in a captured collection
val byIds = qb.delete(Item::class.java).where { it.id in ids }
assertEquals("delete from Item e0 where e0.id in (:p1)", byIds.toHql().hql())
byIds.execute()
// every row: all() is required
val all = qb.delete(Order::class.java).all()
assertEquals("delete from Order e0", all.toHql().hql())
all.execute()
```

`where` 中的子查询使用第二个 Lambda 表达式参数 `sub`，与 [5. 子查询](05-subquery.md) 相同。
删除没有订单项的订单的示例：

<!-- region: ManualKotlinDmlTest.kt#delete-not-exists -->
```kotlin
// orders without items: the subquery starts with the second lambda parameter sub
val d = qb.delete(Order::class.java)
    .where { o, sub -> !sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
assertEquals("delete from Order e0 where not exists (select 1 from Item s1 where s1.order.id = e0.id)", d.toHql().hql())
val deleted = d.execute()
```

**各数据库的 SQL**

<!-- sql: dml-subquery -->
```sql
-- H2, Oracle
delete from orders o1_0
where not exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id)

-- MariaDB
delete from orders
where not exists(select 1 from items i1_0 where i1_0.order_id=orders.id)
```

与所有 JPA 批量语句一样，删除不会执行 JPA 的 `cascade` 或 `orphanRemoval`，也不会反映到已加载的实体上。
删除需要事务（见 [7.8](07-dml.md#transactions)）。

示例代码：[`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`delete`、`delete-safe-call`、`delete-all`、`delete-not-exists`）。

<a id="delete-with-a-subquery-on-the-same-table"></a>
## 读取同一张表的子查询

`where` 中的子查询可以读取要删除的实体（或同一继承层次中的实体）。子查询自己的 `from`、子查询中的连接、U9 的关联标量子查询都属于这种情况。
这时 lambdaQL 先通过派生表读取目标 id，再按这些 id 删除。

```
delete from Order d0 where d0.id in (select t.id from (select e0.id as id from Order e0 where <condition>) t)
```

- 原因：子查询读取目标表时，MariaDB 会拒绝 Hibernate 的 `delete t from …`（错误 1093，“specified twice”）。
- 关联子查询也能使用同样的形式。只包装子查询的方式不能用于关联子查询。
- 已在 H2、MariaDB 10.11、Oracle 23.26 上实测（非关联 `avg`、关联 `exists`、`max`、子查询中的连接）。三种数据库删除的行数相同，MariaDB 也不会失败。
- 读取其他表的子查询保持原来的形式（上面的 `not exists` 示例）。
- `update` 不做包装。带有读取自身表的子查询的 `update` 在三种数据库上都能工作（在 MariaDB 10.11 上实测）。
- 复合 id 实体不做包装。带 `limit` 的删除本来就使用下面的包装形式（别名 `x0`）。

<!-- region: KotlinQueryTest.kt#delete-self-subquery -->
```kotlin
// the subquery reads the target table: the matching ids are read through a derived table first (U17, MariaDB 1093)
val d = qb.delete(Order::class.java).where { o, sub -> o.price < sub.from(Order::class.java).avg { x -> x.price } }
assertEquals("delete from Order d0 where d0.id in (select t.id from (select e0.id as id from Order e0"
        + " where e0.price < (select avg(s1.price) from Order s1)) t)", d.toHql().hql())
```

示例代码：[`KotlinQueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)（`delete-self-subquery`）。

<a id="delete-the-first-rows-limit"></a>
## 删除前几行：`limit`

在 `where { ... }` 或 `all()` 之后加上 `orderBy { ... }` 和 `limit(n)`，最多删除 `n` 行。删除对象是 `qb.from(X::class.java).where { same }.orderBy { same }` 的前 `n` 行。
用于把大量行分成小批处理。

- 没有 `orderBy` 时，按 id 排序。
- lambdaQL 把 id 加为最后一个排序键，因此顺序总是相同（见[并列处理](04-order-paging.md#tie-breaker-automatic-id)）。
- 与查询一样，`orderBy` 中指定排序 Lambda 表达式或 `SortSpec`（见 [4. 排序与分页](04-order-paging.md)）。
- 没有 `limit` 的 `orderBy` 是错误（`IllegalStateException`），因为这时排序不起任何作用。

<!-- region: ManualKotlinDmlTest.kt#delete-limit -->
```kotlin
// the first rows of qb.from(Order::class.java).where { same }.orderBy { same }; the id is the last sort key
val d = qb.delete(Order::class.java)
    .where { it.status.toInt() == 1 }
    .orderBy { o -> asc(o.price) }
    .limit(1)
assertEquals("delete from Order e0 where e0.id in (select t.id from (select x0.id as id from Order x0"
    + " where x0.status = 1 order by x0.price asc, x0.id asc limit 1) t)", d.toHql().hql())
val deleted = d.execute()
```

**各数据库的 SQL**

<!-- sql: delete-limit -->
```sql
-- H2
delete from orders o1_0
where o1_0.id in (select t1_0.c0 from (select o2_0.id from orders o2_0 where o2_0.status=1 order by o2_0.price,o2_0.id fetch first 1 rows only) t1_0(c0))

-- MariaDB
delete o1_0 from orders o1_0
where o1_0.id in (select * from (select t1_0.c0 from (select * from (select o2_0.id c0 from orders o2_0 where o2_0.status=1 order by o2_0.price,o2_0.id limit 1) _sub_) t1_0) _sub_)

-- Oracle
delete from orders o1_0
where o1_0.id in (select t1_0.c0 from (select o2_0.id c0 from orders o2_0 where o2_0.status=1 order by o2_0.price,o2_0.id fetch first 1 rows only) t1_0)
```

lambdaQL 把 limit 渲染为 `e0.id in (select t.id from (select x0.id as id from X x0 ... order by ... limit n) t)`。

- 内层 select 遵循查询的规则，包括连接。因此该语句删除的正是查询返回的行。
- 派生表 `t` 是为 MariaDB 准备的。没有派生表时，MariaDB 会拒绝 `in` 子查询中的 `limit`（错误 1235）和读取目标表的子查询（错误 1093）。
- H2 和 Oracle 写成 `fetch first 1 rows only`，MariaDB 写成 `limit 1`。

示例代码：[`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`delete-limit`）。

<a id="delete-exactly-one-row-executeone"></a>
## 只删除一行：`executeOne()`

`executeOne()` 以**单行保护**执行删除。当条件应只匹配一行时（例如唯一键），请用它代替 `execute()`。

1. lambdaQL 读取匹配条件的行的 id，最多 2 个。
2. 没有 id 时，什么都不改变，返回 0。
3. 有 2 个 id 时，什么都不改变，抛出 JPA 的 `NonUniqueResultException`。
4. 有 1 个 id 时，用 `id = :p and (条件)` 删除该行并返回 1。如果该行在此期间已不再符合条件，则返回 0。

语句只指定一个 id，因此不会改变两行以上。这种保护不依赖回滚。

<!-- region: ManualKotlinDmlTest.kt#execute-one -->
```kotlin
// one row matches: it is deleted
val one = qb.delete(Order::class.java).where { it.id == id }.executeOne()
// two or more rows match: nothing is deleted
assertThrows(NonUniqueResultException::class.java) {
    qb.delete(Order::class.java).where { it.price > 100.0 }.executeOne()
}
```

价格大于 100 的订单不止一个，因此第二条语句什么都不删除。
id 不会是 NULL，因此 `it.id == id` 成为简单的 `e0.id = :p1`（见 [3.6.2](03-where.md#362--compares-values)）。

- `executeOne()` 与 `orderBy` 或 `limit` 一起使用是错误（`IllegalStateException`）。
- `limit` 和 `executeOne()` 目前还不支持复合键实体。
- 在 Spring 仓库中，`deleteOne { ... }` 执行同样的操作，并抛出 `IncorrectResultSizeDataAccessException`（见 [12.4](12-spring-data-jpa.md#one-row-protection-deleteone-and-updateone)）。

---

[7. 更新、插入、删除（Kotlin）](07-dml.md) | [7.2 更新](07-2-update.md) | **7.4 删除** | [7.5 插入](07-5-insert.md) | [7.6 Upsert](07-6-upsert.md) | [Java](../java/07-4-delete.md)

示例代码：[`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`execute-one`）。
