<a id="74-delete"></a>
# 7.4 删除（delete）（Java）

[7. 更新、插入、删除（Java）](07-dml.md) | [7.2 更新（update）](07-2-update.md) | **7.4 删除（delete）** | [7.5 插入（insert）](07-5-insert.md) | [7.6 Upsert（upsert）](07-6-upsert.md) | [Kotlin](../kotlin/07-4-delete.md)

---

<!-- region: DmlQueryTest.java#delete -->
```java
ExecutableDelete<Order> d = qb.delete(Order.class).where(o -> o.getStatus() == 3);
assertEquals("delete from Order e0 where e0.status = 3", d.toHql().hql());
int deleted = d.execute();
// a path: the same rows as qb.from(Order.class).where(same lambda) - orders without a customer stay
ExecutableDelete<Order> byName = qb.delete(Order.class).where(o -> o.getCustomer().getName() == null);
assertEquals("delete from Order e0 where (select s1.name from Customer s1 where s1.id = fk(e0.customer)) is null"
        + " and e0.customer.id is not null", byName.toHql().hql());
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

MariaDB 写作 `delete o1_0 from orders o1_0`，H2 和 Oracle 写作 `delete from orders o1_0`。

第二条语句删除的行与 `qb.from(Order.class).where(o -> o.getCustomer().getName() == null)` 返回的**行相同**，即客户没有名字的订单。
**没有**客户的订单不符合该条件，因此不会被删除。在查询中，该路径也是 inner join。
若要同时删除没有客户的订单，请一并使用 null 守卫（`o.getCustomer() == null || ...`）。与查询一样，没有客户的订单也会成为目标。

删除所有行时需要 `all()`。

<!-- region: ManualJavaDmlTest.java#delete-all -->
```java
ExecutableDelete<Order> d = qb.delete(Order.class).all();
assertEquals("delete from Order e0", d.toHql().hql());
int deleted = d.execute();
```

按 id 列表删除（捕获的集合）：

<!-- region: ManualJavaDmlTest.java#delete-in -->
```java
List<Long> ids = all;                               // ids read before, for example from a request
ExecutableDelete<Item> d = qb.delete(Item.class).where(i -> ids.contains(i.getId()));
assertEquals("delete from Item e0 where e0.id in (:p1)", d.toHql().hql());
int deleted = d.execute();
```

`where` 中的子查询与 [5. 子查询](05-subquery.md) 一样，使用第二个 Lambda 表达式参数 `sub`。
删除没有订单项的订单的示例：

<!-- region: SubqueryTest.java#dml-subquery -->
```java
ExecutableDelete<Order> d = qb.delete(Order.class)
        .where((o, sub) -> !sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).exists());
assertEquals("delete from Order e0 where not exists (select 1 from Item s1 where s1.order.id = e0.id)", d.toHql().hql());
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

与所有 JPA 批量语句一样，delete 不会执行 JPA 的 `cascade` 或 `orphanRemoval`，也不会反映到已加载的实体上。
delete 需要事务（见 [7.8](07-dml.md#transactions)）。

示例代码：[`DmlQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)（`delete`）、[`ManualJavaDmlTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaDmlTest.java)（`delete-all`、`delete-in`）、[`SubqueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java)（`dml-subquery`）。

<a id="delete-with-a-subquery-on-the-same-table"></a>
## 读取同一张表的子查询

`where` 中的子查询可以读取删除目标实体（或同一继承层次中的实体）。子查询自身的 `from`、子查询中的连接、U9 的关联标量子查询都属于这种情况。
这时 lambdaQL 先通过派生表读取目标 id，再按这些 id 删除。

```
delete from Order d0 where d0.id in (select t.id from (select e0.id as id from Order e0 where <condition>) t)
```

- 原因：子查询读取目标表时，MariaDB 会拒绝 Hibernate 的 `delete t from …`（错误 1093，"specified twice"）。
- 同样的形式也适用于关联子查询。只包装子查询的方式不能用于关联子查询。
- 已在 H2、MariaDB 10.11、Oracle 23.26 上实测（非关联 `avg`，关联 `exists`、`max`，子查询中的连接）。三种数据库删除的行数相同，MariaDB 也不会失败。
- 读取其他表的子查询保持原有形式（上面的 `not exists` 示例）。
- `update` 不包装。带有读取自身表的子查询的 `update` 在三种数据库上都能运行（在 MariaDB 10.11 上实测）。
- 复合 id 实体不包装。带 `limit` 的 delete 本来就使用下面的包装形式（别名 `x0`）。

<!-- region: DmlQueryTest.java#delete-self-subquery -->
```java
// the subquery reads the target table: lambdaQL reads the matching ids through a derived table first,
// because MariaDB cannot delete from a table that a subquery reads (error 1093)
ExecutableDelete<Order> d = qb.delete(Order.class)
        .where((o, sub) -> o.getPrice() < sub.from(Order.class).avg(x -> x.getPrice()));
assertEquals("delete from Order d0 where d0.id in (select t.id from (select e0.id as id from Order e0"
        + " where e0.price < (select avg(s1.price) from Order s1)) t)", d.toHql().hql());
// a subquery on another table keeps the plain form
ExecutableDelete<Order> unused = qb.delete(Order.class)
        .where((o, sub) -> !sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).exists());
assertEquals("delete from Order e0 where not exists (select 1 from Item s1 where s1.order.id = e0.id)",
        unused.toHql().hql());
```

示例代码：[`DmlQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)（`delete-self-subquery`）。

<a id="delete-the-first-rows-limit"></a>
## 删除前几行：`limit`

在 `where(...)` 或 `all()` 之后加上 `orderBy(...)` 和 `limit(n)`，最多删除 `n` 行。目标是 `qb.from(X.class).where(same).orderBy(same)` 的前 `n` 行。
用于把大量行分成小批处理。

- 没有 `orderBy` 时按 id 顺序。
- lambdaQL 把 id 加为最后一个排序键，因此顺序总是相同（见[并列处理](04-order-paging.md#tie-breaker-automatic-id)）。
- `orderBy` 中与查询一样指定排序 Lambda 表达式或 `SortSpec`（见 [4. 排序与分页](04-order-paging.md)）。
- 没有 `limit` 的 `orderBy` 是错误（`IllegalStateException`），因为这时顺序不起任何作用。

<!-- region: DmlQueryTest.java#delete-limit -->
```java
// the first rows of qb.from(Order.class).where(same).orderBy(same); the id is the last sort key
ExecutableDelete<Order> d = qb.delete(Order.class)
        .where(o -> o.getStatus() == 1)
        .orderBy(o -> asc(o.getPrice()))
        .limit(1);
assertEquals("delete from Order e0 where e0.id in (select t.id from (select x0.id as id from Order x0"
        + " where x0.status = 1 order by x0.price asc, x0.id asc limit 1) t)", d.toHql().hql());
int deleted = d.execute();
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

- 内层 select 遵循包括连接在内的查询规则。因此该语句删除的正是查询返回的行。
- 派生表 `t` 是为 MariaDB 准备的。没有派生表时，MariaDB 会拒绝 `in` 子查询中的 `limit`（错误 1235）和读取目标表的子查询（错误 1093）。
- H2 和 Oracle 写作 `fetch first 1 rows only`，MariaDB 写作 `limit 1`。

示例代码：[`DmlQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)（`delete-limit`）。

<a id="delete-exactly-one-row-executeone"></a>
## 只删除一行：`executeOne()`

`executeOne()` 在执行 delete 时应用**单行保护**。条件应当只匹配一行时（例如唯一键），请用它代替 `execute()`。

1. lambdaQL 最多读取 2 个符合条件的行的 id。
2. 没有 id 时，不做任何修改，返回 0。
3. 有 2 个 id 时，不做任何修改，抛出 JPA `NonUniqueResultException`。
4. 有 1 个 id 时，用 `id = :p and (条件)` 删除该行并返回 1。若该行在此期间已不再符合条件，则返回 0。

语句指定了一个 id，因此不会修改 2 行以上。这种保护不依赖回滚。

<!-- region: DmlQueryTest.java#execute-one -->
```java
// one row matches: it is deleted
int one = qb.delete(Order.class).where(o -> o.getId() == id).executeOne();
// two or more rows match: nothing is deleted
assertThrows(jakarta.persistence.NonUniqueResultException.class,
        () -> qb.delete(Order.class).where(o -> o.getPrice() > 100).executeOne());
```

- `executeOne()` 与 `orderBy` 或 `limit` 一起使用是错误（`IllegalStateException`）。
- `limit` 和 `executeOne()` 不支持复合键实体。
- 在 Spring repository 中，`deleteOne(where)` 行为相同，抛出 `IncorrectResultSizeDataAccessException`（见 [12.4](12-spring-data-jpa.md#one-row-protection-deleteone-and-updateone)）。

---

[7. 更新、插入、删除（Java）](07-dml.md) | [7.2 更新（update）](07-2-update.md) | **7.4 删除（delete）** | [7.5 插入（insert）](07-5-insert.md) | [7.6 Upsert（upsert）](07-6-upsert.md) | [Kotlin](../kotlin/07-4-delete.md)

示例代码：[`DmlQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)（`execute-one`）。
