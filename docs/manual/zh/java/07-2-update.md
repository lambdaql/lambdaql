<a id="72-update"></a>
# 7.2 更新（update）（Java）

[7. 更新、插入、删除（Java）](07-dml.md) | **7.2 更新（update）** | [7.4 删除（delete）](07-4-delete.md) | [7.5 插入（insert）](07-5-insert.md) | [7.6 Upsert（upsert）](07-6-upsert.md) | [Kotlin](../kotlin/07-2-update.md)

---

<a id="basic-form"></a>
## 基本形式

<!-- region: DmlQueryTest.java#update -->
```java
short st = 5;
ExecutableUpdate<Order> u = qb.update(Order.class)
        .set(o -> { o.setStatus(st); o.setPrice(o.getPrice() * 2); })
        .where(o -> o.getStatus() == 1);
// the price on the right is the value before the change
assertEquals("update Order e0 set e0.status = :p1, e0.price = (e0.price * 2.0) where e0.status = 1", u.toHql().hql());
int changed = u.execute();
```

`st` 是捕获的变量，因此成为绑定参数（`:p1`）。常量 `2` 是写在 Lambda 表达式中的值，因此保持为字面量（`2.0`）。
`toHql()` 不执行语句，返回 HQL 和参数。`execute()` 执行语句并返回被更新的行数。

**各数据库的 SQL**

<!-- sql: update -->
```sql
-- H2, MariaDB
update orders o1_0
set status=?,price=(o1_0.price*2.0)
where o1_0.status=1

-- Oracle
update orders o1_0
set o1_0.status=?,o1_0.price=(o1_0.price*2.0)
where o1_0.status=1
```

Oracle 在 `set` 中使用别名（`set o1_0.status=?`），H2 和 MariaDB 不使用。

示例代码：[`DmlQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)（`update`）。

<a id="values-null-date-boolean"></a>
## 值：null、日期、boolean

<!-- region: ManualJavaDmlTest.java#update-values -->
```java
ExecutableUpdate<Order> u = qb.update(Order.class)
        .set(o -> { o.setDescription(null); o.setUpdateAt(now); o.setKind(Order.Kind.RUSH); o.setActive(false); })
        .where(o -> o.getId() == id);
assertEquals("update Order e0 set e0.description = null, e0.updateAt = :p1,"
        + " e0.kind = org.lambdaql.query.Order$Kind.RUSH, e0.active = false where e0.id = :p2", u.toHql().hql());
int changed = u.execute();
```

`null` 以及写在 Lambda 表达式中的 enum 常量和 boolean 常量是字面量，捕获值（`now`、`id`）是绑定参数。即使同一个值使用两次，每个捕获值也会得到**自己的参数**。

示例代码：[`ManualJavaDmlTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaDmlTest.java)（`update-values`）。

<a id="add-to-a-value-default-for-null"></a>
## 在原值上累加，以及 NULL 的默认值

<!-- region: ManualJavaDmlTest.java#update-add -->
```java
ExecutableUpdate<Order> plusOne = qb.update(Order.class)
        .set(o -> o.setCnt(o.getCnt() + 1))
        .where(o -> o.getStatus() == 1);
assertEquals("update Order e0 set e0.cnt = (e0.cnt + 1) where e0.status = 1", plusOne.toHql().hql());
// a default for NULL: Objects.requireNonNullElse becomes coalesce
ExecutableUpdate<Order> orZero = qb.update(Order.class)
        .set(o -> o.setTotal(Objects.requireNonNullElse(o.getTotal(), 0.0) + 1))
        .all();
assertEquals("update Order e0 set e0.total = (coalesce(e0.total, 0.0) + 1.0)", orZero.toHql().hql());
```

在 `cnt` 为 NULL 的行中，`cnt + 1` 仍为 NULL（SQL 语义）。要把 NULL 换成其他值，请使用 `Objects.requireNonNullElse`。

示例代码：[`ManualJavaDmlTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaDmlTest.java)（`update-add`）。

<a id="embedded-fields"></a>
## 嵌入字段

<!-- region: ManualJavaDmlTest.java#update-embedded -->
```java
ExecutableUpdate<Order> oneColumn = qb.update(Order.class)
        .set(o -> o.getAddress().setCity(city)).where(o -> o.getId() == id);
assertEquals("update Order e0 set e0.address.city = :p1 where e0.id = :p2", oneColumn.toHql().hql());
// the whole value: every column of the embedded value (city, zip)
ExecutableUpdate<Order> whole = qb.update(Order.class)
        .set(o -> o.setAddress(addr)).where(o -> o.getId() == id);
assertEquals("update Order e0 set e0.address = :p1 where e0.id = :p2", whole.toHql().hql());
```

为整个值对象赋值时，HQL 只有一个 `e0.address = :p1`，Hibernate 会把嵌入值的所有列（`city`、`zip`）写入 SQL 的 SET 列表。

示例代码：[`ManualJavaDmlTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaDmlTest.java)（`update-embedded`）。

<a id="change-an-association-foreign-key"></a>
## 修改关联（外键）

<!-- region: ManualJavaDmlTest.java#update-fk -->
```java
ExecutableUpdate<Order> byId = qb.update(Order.class)
        .set(o -> o.getCustomer().setId(cid)).where(o -> o.getId() == id);
assertEquals("update Order e0 set e0.customer.id = :p1 where e0.id = :p2", byId.toHql().hql());
ExecutableUpdate<Order> none = qb.update(Order.class)
        .set(o -> o.setCustomer(null)).where(o -> o.getId() == id);
assertEquals("update Order e0 set e0.customer = null where e0.id = :p1", none.toHql().hql());
// a captured entity: its @Id is bound
ExecutableUpdate<Order> entity = qb.update(Order.class)
        .set(o -> o.setCustomer(customer)).where(o -> o.getId() == other);
assertEquals("update Order e0 set e0.customer = :p1 where e0.id = :p2", entity.toHql().hql());
```

在关联实体上只能设置 `@Id`（FK）。
`o.getCustomer().setName(x)` 是错误，因为它会修改另一张表（见 [7.8](07-dml.md#rules-checked-at-analysis-time)）。

示例代码：[`ManualJavaDmlTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaDmlTest.java)（`update-fk`）。

<a id="a-value-from-another-table"></a>
## 来自另一张表的值

请通过关联路径读取值。lambdaQL 把它渲染为**关联标量子查询**。
`where` 中的路径也会变成同样形式的子查询。

<!-- region: DmlQueryTest.java#update-path -->
```java
// a path in the condition: a subquery, plus "customer exists" (the same rows as the select, U9)
ExecutableUpdate<Order> u = qb.update(Order.class)
        .set(o -> o.setCustName("K"))
        .where(o -> o.getCustomer().getName().equals("Kim"));
assertEquals("update Order e0 set e0.custName = 'K' where (select s1.name from Customer s1 where s1.id = fk(e0.customer)) = 'Kim'"
        + " and e0.customer.id is not null", u.toHql().hql());
// a path in a value: a subquery; NULL when there is no customer
ExecutableUpdate<Order> copy = qb.update(Order.class).set(o -> o.setCustName(o.getCustomer().getName())).all();
assertEquals("update Order e0 set e0.custName = (select s1.name from Customer s1 where s1.id = fk(e0.customer))", copy.toHql().hql());
```

**各数据库的 SQL**

<!-- sql: update-path -->
```sql
-- H2
update orders o1_0
set cust_name='K'
where (select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id)='Kim' and o1_0.customer_id is not null

-- MariaDB
update orders
set cust_name='K'
where (select c1_0.name from customers c1_0 where c1_0.id=orders.customer_id)='Kim' and customer_id is not null

-- Oracle
update orders o1_0
set o1_0.cust_name='K'
where (select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id)='Kim' and o1_0.customer_id is not null
```

**各数据库的 SQL**

<!-- sql: update-path#2 -->
```sql
-- H2
update orders o1_0
set cust_name=(select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id)

-- MariaDB
update orders
set cust_name=(select c1_0.name from customers c1_0 where c1_0.id=orders.customer_id)

-- Oracle
update orders o1_0
set o1_0.cust_name=(select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id)
```

订单没有客户时，`custName` 为 NULL。
在 `where` 中，lambdaQL 会加上 `e0.customer.id is not null`。因此被更新的行与 `qb.from(Order.class).where(同一个 Lambda 表达式)` 的结果相同。

示例代码：[`DmlQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)（`update-path`）。

<a id="an-aggregate-from-a-subquery"></a>
## 来自子查询的聚合

请使用带两个参数的 Lambda 表达式 `set((o, sub) -> ...)`。第二个参数是子查询的起点 `sub`。
这里不需要 `Agg`。子查询与 [5. 子查询](05-subquery.md) 一样提供 `sum`、`avg` 等方法。

<!-- region: DmlQueryTest.java#update-sub -->
```java
// the second parameter starts a subquery, as in where((o, sub) -> ...)
ExecutableUpdate<Order> u = qb.update(Order.class)
        .set((o, sub) -> o.setPrice(sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).sum(i -> i.getPrice())))
        .where((o, sub) -> sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).exists());
assertEquals("update Order e0 set e0.price = (select sum(s1.price) from Item s1 where s1.order.id = e0.id)"
        + " where exists (select 1 from Item s1 where s1.order.id = e0.id)", u.toHql().hql());
// sum gives NULL when there is no row; coalesce keeps a NOT NULL column valid for every row
ExecutableUpdate<Order> all = qb.update(Order.class)
        .set((o, sub) -> value(o.getPrice(), Objects.requireNonNullElse(
                sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).sum(i -> i.getPrice()), 0.0)))
        .all();
assertEquals("update Order e0 set e0.price = coalesce((select sum(s1.price) from Item s1 where s1.order.id = e0.id), 0.0)",
        all.toHql().hql());
```

- 其他表的聚合和同一张表的聚合都可以使用（已在 H2、MariaDB 10.11、Oracle 23.26 上实测）。在 `update` 中，MariaDB 也不会报错误 1093。
- 没有匹配的行时，聚合为 NULL。若是 NOT NULL 列会产生数据库错误，因此请用 `Objects.requireNonNullElse(…, 0.0)` 包装，使其成为 `coalesce(...)`。

示例代码：[`DmlQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)（`update-sub`）。

<a id="where-with-paths-and-subqueries"></a>
## 使用路径和子查询的 where

`where` 与查询中的相同。

<!-- region: ManualJavaDmlTest.java#update-where -->
```java
// a path in the condition: a scalar subquery
ExecutableUpdate<Order> byGrade = qb.update(Order.class)
        .set(o -> o.setStatus((short) 0))
        .where(o -> o.getCustomer().getGrade() > 3);
assertEquals("update Order e0 set e0.status = 0 where (select s1.grade from Customer s1 where s1.id = fk(e0.customer)) > 3"
        + " and e0.customer.id is not null", byGrade.toHql().hql());
// a subquery with sub
ExecutableUpdate<Order> noItems = qb.update(Order.class)
        .set(o -> o.setStatus((short) 9))
        .where((o, sub) -> !sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).exists());
assertEquals("update Order e0 set e0.status = 9 where not exists (select 1 from Item s1 where s1.order.id = e0.id)",
        noItems.toHql().hql());
```

在第一条语句中，路径变成标量子查询，只有有客户的订单才是目标。
第二条语句与 [5. 子查询](05-subquery.md) 一样使用 `sub`。

示例代码：[`ManualJavaDmlTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaDmlTest.java)（`update-where`）。

<a id="optimistic-locking-version"></a>
## 乐观锁（`@Version`）

实体有 `@Version` 字段时，lambdaQL 渲染 `update versioned`。数据库会把版本加 1。

<!-- region: DmlQueryTest.java#update-versioned -->
```java
// an entity with a version field (Version annotation): the version goes up too
ExecutableUpdate<VOrder> u = qb.update(VOrder.class).set(v -> v.setStatus(2)).where(v -> v.getId() == 1L);
assertEquals("update versioned VOrder e0 set e0.status = 2 where e0.id = 1L", u.toHql().hql());
```

**各数据库的 SQL**

<!-- sql: update-versioned -->
```sql
-- H2, MariaDB
update v_orders v1_0
set status=2,version=(v1_0.version+1)
where v1_0.id=1

-- Oracle
update v_orders v1_0
set v1_0.status=2,v1_0.version=(v1_0.version+1)
where v1_0.id=1
```

Hibernate 会在 SET 列表中加上 `version=(v1_0.version+1)`。要把版本用作乐观锁，请把版本放进 `where` 并检查结果。

<!-- region: ManualJavaDmlTest.java#update-version -->
```java
// put the version in the condition and check the result
ExecutableUpdate<VOrder> u = qb.update(VOrder.class)
        .set(o -> o.setStatus(8))
        .where(o -> o.getId() == id && o.getVersion() == v);
assertEquals("update versioned VOrder e0 set e0.status = 8 where e0.id = :p1 and e0.version = :p2", u.toHql().hql());
int n = u.execute();
if (n == 0) throw new OptimisticLockException();   // checking the result is your job
```

再次执行同一条语句时，版本不匹配，返回 0。第一次执行已经提升了版本，因此没有与旧版本匹配的行。

`@Version` 字段不能赋值。赋值会以 `LQL-3068` 被拒绝。

示例代码：[`DmlQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)（`update-versioned`）、[`ManualJavaDmlTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaDmlTest.java)（`update-version`）。

<a id="many-set-calls-patch"></a>
## 多次调用 `set()`（PATCH）

`set()` 可以调用多次。所有赋值按调用顺序合并为一个 SET 列表。
不能再次读取前面赋值过的列、不能对同一列赋值两次，这两条规则也适用于多次 `set()` 调用之间。

<!-- region: DmlQueryTest.java#update-patch -->
```java
// PATCH: assign only the fields that have a value; every set() adds assignments
String memo = "checked";
Short newStatus = null;
UpdateSet<Order> u = qb.update(Order.class).set(o -> o.setDescription(memo));
if (newStatus != null) u = u.set(o -> o.setStatus(newStatus));
int changed = u.where(o -> o.getId() == id).execute();

// reading a column that an earlier assignment wrote is an error: the databases disagree on its value
assertThrows(LambdaQLException.class,
        () -> qb.update(Order.class).set(o -> { o.setPrice(1); o.setTex((float) o.getPrice()); }));
// the same column twice is an error, also across set() calls
assertThrows(LambdaQLException.class,
        () -> qb.update(Order.class).set(o -> o.setStatus((short) 1)).set(o -> o.setStatus((short) 2)));
```

`set()` 返回 `UpdateSet`。该类型有 `set`、`where`、`all`，没有 `execute()`。

示例代码：[`DmlQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)（`update-patch`）。

<a id="update-all-rows-all"></a>
## 更新所有行：`all()`

没有 `where` 的 update 必须显式写 `all()`。

<!-- region: ManualJavaDmlTest.java#update-all -->
```java
// without where, all() is required; without both, there is no execute() (a compile error)
ExecutableUpdate<Order> u = qb.update(Order.class).set(o -> o.setStatus((short) 0)).all();
assertEquals("update Order e0 set e0.status = 0", u.toHql().hql());
int changed = u.execute();
```

update 需要事务（见 [7.8](07-dml.md#transactions)）。

示例代码：[`ManualJavaDmlTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaDmlTest.java)（`update-all`）。

<a id="update-the-first-rows-limit"></a>
## 更新前几行：`limit`

在 `where(...)` 或 `all()` 之后加上 `orderBy(...)` 和 `limit(n)`，最多更新 `n` 行。目标是 `qb.from(X.class).where(same).orderBy(same)` 的前 `n` 行。

规则与 delete 相同（见 [7.4](07-4-delete.md#delete-the-first-rows-limit)）。没有 `orderBy` 时按 id 顺序，id 总是最后一个排序键，没有 `limit` 的 `orderBy` 是错误。

<!-- region: DmlQueryTest.java#update-limit -->
```java
// a path in the condition: the nested select joins, so the rows are the same as the select
ExecutableUpdate<Order> u = qb.update(Order.class)
        .set(o -> o.setDescription("top"))
        .where(o -> o.getCustomer().getName().equals("Kim"))
        .orderBy(o -> desc(o.getPrice()))
        .limit(1);
assertEquals("update Order e0 set e0.description = 'top' where e0.id in (select t.id from (select x0.id as id from Order x0"
        + " join x0.customer k1 where k1.name = 'Kim' order by x0.price desc, x0.id asc limit 1) t)", u.toHql().hql());
```

**各数据库的 SQL**

<!-- sql: update-limit -->
```sql
-- H2
update orders o1_0
set description='top'
where o1_0.id in (select t1_0.c0 from (select o2_0.id from orders o2_0 join customers c1_0 on c1_0.id=o2_0.customer_id where c1_0.name='Kim' order by o2_0.price desc,o2_0.id fetch first 1 rows only) t1_0(c0))

-- MariaDB
update orders o1_0
set description='top'
where o1_0.id in (select * from (select t1_0.c0 from (select * from (select o2_0.id c0 from orders o2_0 join customers c1_0 on c1_0.id=o2_0.customer_id where c1_0.name='Kim' order by o2_0.price desc,o2_0.id limit 1) _sub_) t1_0) _sub_)

-- Oracle
update orders o1_0
set o1_0.description='top'
where o1_0.id in (select t1_0.c0 from (select o2_0.id c0 from orders o2_0 join customers c1_0 on c1_0.id=o2_0.customer_id where c1_0.name='Kim' order by o2_0.price desc,o2_0.id fetch first 1 rows only) t1_0)
```

路径 `o.getCustomer().getName()` 与查询一样，在内层 select 中成为连接。update 语句本身不读取其他表。

示例代码：[`DmlQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)（`update-limit`）。

<a id="update-exactly-one-row-executeone"></a>
## 只更新一行：`executeOne()`

`executeOne()` 在执行 update 时应用单行保护。它最多读取 2 个符合条件的 id。

有 2 个 id 时，不做任何修改，抛出 `NonUniqueResultException`。有 1 个 id 时，用 `id = :p and (条件)` 更新该行。步骤与 delete 相同（见 [7.4](07-4-delete.md#delete-exactly-one-row-executeone)）。

<!-- region: ManualJavaDmlTest.java#update-one -->
```java
int one = qb.update(Order.class)
        .set(o -> o.setDescription("checked"))
        .where(o -> o.getId() == id)
        .executeOne();                             // 0 or 1
// two or more rows match: nothing changes
assertThrows(NonUniqueResultException.class, () -> qb.update(Order.class)
        .set(o -> o.setDescription("checked"))
        .where(o -> o.getStatus() == 1)
        .executeOne());
```

像第二次调用那样有 2 行以上符合条件时，抛出 `NonUniqueResultException`，不做任何修改。

在 Spring repository 中，`updateOne(where, set)` 行为相同（见 [12.4](12-spring-data-jpa.md#one-row-protection-deleteone-and-updateone)）。

---

[7. 更新、插入、删除（Java）](07-dml.md) | **7.2 更新（update）** | [7.4 删除（delete）](07-4-delete.md) | [7.5 插入（insert）](07-5-insert.md) | [7.6 Upsert（upsert）](07-6-upsert.md) | [Kotlin](../kotlin/07-2-update.md)

示例代码：[`ManualJavaDmlTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaDmlTest.java)（`update-one`）。
