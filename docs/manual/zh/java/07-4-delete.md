<a id="74-delete"></a>
# 7.4 删除（Java）

[7. 更新、插入、删除（Java）](07-dml.md) | [7.2 更新](07-2-update.md) | **7.4 删除** | [7.5 插入](07-5-insert.md) | [7.6 Upsert](07-6-upsert.md) | [Kotlin](../kotlin/07-4-delete.md)

---


**(已测试)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)，region `delete`：

```java
ExecutableDelete<Order> d = qb.delete(Order.class).where(o -> o.getStatus() == 3);
assertEquals("delete from Order e0 where e0.status = 3", d.toHql().hql());
int deleted = d.execute();
// a path: the same rows as qb.from(Order.class).where(same lambda) - orders without a customer stay
ExecutableDelete<Order> byName = qb.delete(Order.class).where(o -> o.getCustomer().getName() == null);
assertEquals("delete from Order e0 where (select s1.name from Customer s1 where s1.id = fk(e0.customer)) is null"
        + " and e0.customer.id is not null", byName.toHql().hql());
```

**关于 SQL 块。** `…` 是缩短的 select 列表（实体的所有列），`?` 是绑定参数。这些行来自 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)。该测试使用 Hibernate 7.4 在 H2 2.3、MariaDB 10.11 和 Oracle 23.26 上运行每个查询，如果 Hibernate 的 SQL 发生变化，测试就会失败。

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `delete`：

```sql
-- H2, Oracle
delete from orders o1_0
where o1_0.status=3

-- MariaDB
delete o1_0 from orders o1_0
where o1_0.status=3
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `delete#2`：

```sql
-- H2, Oracle
delete from orders o1_0
where (select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id) is null and o1_0.customer_id is not null

-- MariaDB
delete from orders
where (select c1_0.name from customers c1_0 where c1_0.id=orders.customer_id) is null and customer_id is not null
```

MariaDB 写成 `delete o1_0 from orders o1_0`。H2 和 Oracle 写成 `delete from orders o1_0`。

第二条语句删除的行与 `qb.from(Order.class).where(o -> o.getCustomer().getName() == null)` 返回的**行相同**：客户没有名字的订单。测试会把删除的行与这个查询进行比较。
**没有**客户的订单不会被删除（规则 U9，决策 D36）。使用 null 守卫（`o.getCustomer() == null || ...`）时也一样，因为在查询中该路径也是内连接（决策 D48）。
要删除没有客户的订单，请以 `o.getCustomer() == null` 为条件再执行一次删除语句。

删除所有行时必须写 `all()`（规则 U5）。

```java
qb.delete(Order.class).all().execute();     // delete all rows
qb.delete(Order.class).execute();           // compile error: no execute()
```

按 id 列表删除（捕获的集合，决策 D29）：

```java
qb.delete(Item.class).where(i -> ids.contains(i.getId())).execute();
```

`where` 中的子查询使用第二个 Lambda 参数 `sub`，与 [5. 子查询](05-subquery.md) 相同。
删除没有明细的订单的示例：

**(已测试)** [`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java)，region `dml-subquery`：

```java
ExecutableDelete<Order> d = qb.delete(Order.class)
        .where((o, sub) -> !sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).exists());
assertEquals("delete from Order e0 where not exists (select 1 from Item s1 where s1.order.id = e0.id)", d.toHql().hql());
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `dml-subquery`：

```sql
-- H2, Oracle
delete from orders o1_0
where not exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id)

-- MariaDB
delete from orders
where not exists(select 1 from items i1_0 where i1_0.order_id=orders.id)
```

与所有 JPA 批量语句一样，删除不会执行 JPA 的 `cascade` 或 `orphanRemoval`，也不会修改已加载的实体（规则 U16）。
删除需要事务（见 [7.8](07-dml.md#transactions)）。

<a id="delete-with-a-subquery-on-the-same-table"></a>
## 读取同一张表的子查询

`where` 中的子查询可以读取删除目标实体（或同一继承层次中的实体）。子查询自身的 `from`、子查询中的连接、U9 的关联标量子查询都属于这种情况。
这时 lambdaQL 先通过派生表读取目标 id，再按这些 id 删除（规则 U17）。

```
delete from Order d0 where d0.id in (select t.id from (select e0.id as id from Order e0 where <condition>) t)
```

- 原因：子查询读取目标表时，MariaDB 会拒绝 Hibernate 的 `delete t from …`（错误 1093，"specified twice"）。
- 同样的形式也适用于关联子查询。只包装子查询的方式不能用于关联子查询。
- 已在 H2、MariaDB 10.11、Oracle 23.26 上实测（非关联 `avg`，关联 `exists`、`max`，子查询中的连接）。三种数据库删除的行数相同，MariaDB 也不会失败。
- 读取其他表的子查询保持原有形式（上面的 `not exists` 示例）。
- `update` 不包装。读取自身表的子查询的 `update` 在三种数据库上都能运行（在 MariaDB 10.11 上实测）。
- 复合 id 实体不包装。带 `limit` 的删除本来就使用下面的包装形式（别名 `x0`）。

**(已测试)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)，region `delete-self-subquery`：

```java
// the subquery reads the target table: lambdaQL reads the matching ids through a derived table first (U17),
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

<a id="delete-the-first-rows-limit"></a>
## 删除前几行：`limit`

在 `where(...)` 或 `all()` 之后写 `orderBy(...)` 和 `limit(n)`，最多删除 `n` 行：即 `qb.from(X.class).where(same).orderBy(same)` 的前 `n` 行。
可以用它把大量行分成小批逐步处理。

- 没有 `orderBy` 时，按 id 排序。
- lambdaQL 把 id 加为最后一个排序键，因此顺序总是相同（规则 S9，见[决胜键](04-order-paging.md#tie-breaker-automatic-id)）。
- `orderBy` 接受排序 Lambda 或 `SortSpec`，与查询相同（见 [4. 排序与分页](04-order-paging.md)）。
- 没有 `limit` 的 `orderBy` 是错误（`IllegalStateException`），因为这时排序没有作用。

**(已测试)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)，region `delete-limit`：

```java
// the first rows of qb.from(Order.class).where(same).orderBy(same); the id is the last sort key
ExecutableDelete<Order> d = qb.delete(Order.class)
        .where(o -> o.getStatus() == 1)
        .orderBy((o, by) -> by.asc(o.getPrice()))
        .limit(1);
assertEquals("delete from Order e0 where e0.id in (select t.id from (select x0.id as id from Order x0"
        + " where x0.status = 1 order by x0.price asc, x0.id asc limit 1) t)", d.toHql().hql());
int deleted = d.execute();
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `delete-limit`：

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

lambdaQL 把 limit 写成 `e0.id in (select t.id from (select x0.id as id from X x0 ... order by ... limit n) t)`。

- 内层 select 遵循查询的规则，包括连接（决策 D36）。因此语句删除的正是查询返回的行。
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

**(已测试)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)，region `execute-one`：

```java
// one row matches: it is deleted
int one = qb.delete(Order.class).where(o -> o.getId() == id).executeOne();
// two or more rows match: nothing is deleted
assertThrows(jakarta.persistence.NonUniqueResultException.class,
        () -> qb.delete(Order.class).where(o -> o.getPrice() > 100).executeOne());
```

- `executeOne()` 与 `orderBy` 或 `limit` 一起使用是错误（`IllegalStateException`）。
- `limit` 和 `executeOne()` 目前还不支持复合键实体。
- 在 Spring 仓库中，`deleteOne(where)` 做同样的事，并抛出 `IncorrectResultSizeDataAccessException`（见 [12.4](12-spring-data-jpa.md#one-row-protection-deleteone-and-updateone)）。

---

---

[7. 更新、插入、删除（Java）](07-dml.md) | [7.2 更新](07-2-update.md) | **7.4 删除** | [7.5 插入](07-5-insert.md) | [7.6 Upsert](07-6-upsert.md) | [Kotlin](../kotlin/07-4-delete.md)
