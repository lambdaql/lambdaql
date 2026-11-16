<a id="72-update"></a>
# 7.2 更新（Java）

> [!NOTE]
> **状态：已实现并测试**，在 H2 2.3、MariaDB 10.11 和 Oracle 23.26 上（里程碑 M10），包括 `limit` 和 `executeOne()`（决定 D41）。
> **尚未实现：** `set` 和 `where` 中的 `sub` 子查询（里程碑 M8），以及复合键实体上的 `limit` / `executeOne()`。
> 标记为 **(已测试)** 的示例是从 [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) 中复制的。其他示例遵循设计，还没有测试覆盖。
> 完整状态见 [Java 路线 README](README.md#status)。

[7. 更新、插入、删除（Java）](07-dml.md) | **7.2 更新** | [7.4 删除](07-4-delete.md) | [7.5 插入](07-5-insert.md) | [7.6 Upsert](07-6-upsert.md) | [Kotlin](../kotlin/07-2-update.md)

---


<a id="basic-form"></a>
## 基本形式

**(已测试)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)，region `update`：

```java
short st = 5;
ExecutableUpdate<Order> u = qb.update(Order.class)
        .set(o -> { o.setStatus(st); o.setPrice(o.getPrice() * 2); })
        .where(o -> o.getStatus() == 1);
// the price on the right is the value before the change
assertEquals("update Order e0 set e0.status = :p1, e0.price = (e0.price * 2.0) where e0.status = 1", u.toHql().hql());
int changed = u.execute();
```

`st` 是捕获的变量，所以它是绑定参数（`:p1`）。常量 `2` 写在 Lambda 中，所以它仍是字面量（`2.0`，决定 D37）。
`toHql()` 不执行语句，只给出 HQL 和参数。`execute()` 执行语句并返回修改的行数。

**关于 SQL 块。** `…` 是缩短的 select 列表（实体的所有列），`?` 是绑定参数。这些行来自 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)。该测试使用 Hibernate 6.6.5 在 H2 2.3、MariaDB 10.11 和 Oracle 23.26 上运行每个查询，如果 Hibernate 的 SQL 发生变化，测试就会失败。 标记为 **（计划中）** 的块来自 [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java)。由于 lambdaQL 目前还不会生成这些语句，该测试把计划中的 HQL 直接交给 Hibernate。

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `update`：

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

Oracle 在 `set` 中写出别名（`set o1_0.status=?`）；H2 和 MariaDB 不写。

<a id="values-null-date-boolean"></a>
## 值：null、日期、布尔

```java
qb.update(Order.class)
  .set(o -> { o.setDescription(null); o.setUpdateAt(now); o.setActive(true); })
  .where(o -> o.getId() == id)
  .execute();
```

枚举值也以同样的方式工作。每个捕获值都有**自己的参数**，即使同一个值使用了两次（规则 U7）。

<a id="add-to-a-value-default-for-null"></a>
## 在原值上累加，以及 NULL 的默认值

```java
qb.update(Order.class).set(o -> o.setCnt(o.getCnt() + 1)).where(o -> o.getStatus() == 1).execute();

qb.update(Order.class)
  .set(o -> o.setPrice(Objects.requireNonNullElse(o.getPrice(), 0.0) + 1))
  .all()
  .execute();
// → set o.price = coalesce(o.price, 0.0) + 1
```

<a id="embedded-fields"></a>
## 嵌入字段

```java
qb.update(Order.class).set(o -> o.getAddress().setCity(city)).where(o -> o.getId() == id).execute();
// → set o.address.city = :p1

qb.update(Order.class).set(o -> o.setAddress(addr)).where(o -> o.getId() == id).execute();
// → sets all columns of the embedded value (city, zip)
```

<a id="change-an-association-foreign-key"></a>
## 修改关联（外键）

```java
qb.update(Order.class).set(o -> o.getCustomer().setId(cid)).where(o -> o.getId() == id).execute();
// → set o.customer.id = :p1      (the FK column)

qb.update(Order.class).set(o -> o.setCustomer(null)).where(o -> o.getId() == id).execute();
// → set o.customer = null

qb.update(Order.class).set(o -> o.setCustomer(customer)).where(o -> o.getId() == id).execute();
// → binds the @Id of the captured customer (rule U11)
```

对关联实体，只能设置它的 `@Id`（外键）。
`o.getCustomer().setName(x)` 是错误：它会修改另一张表（规则 U6，在 [7.8](07-dml.md#rules-checked-at-analysis-time) 中测试）。

<a id="a-value-from-another-table"></a>
## 来自另一张表的值

通过关联路径读取值。lambdaQL 把它渲染为**关联标量子查询**（规则 U9）。
`where` 中的路径也会变成同样的子查询。

**(已测试)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)，region `update-path`：

```java
// a path in the condition: a subquery, plus "customer exists" (the same rows as the select, D36, U9)
ExecutableUpdate<Order> u = qb.update(Order.class)
        .set(o -> o.setCustName("K"))
        .where(o -> o.getCustomer().getName().equals("Kim"));
assertEquals("update Order e0 set e0.custName = 'K' where (select s1.name from Customer s1 where s1.id = e0.customer.id) = 'Kim'"
        + " and e0.customer.id is not null", u.toHql().hql());
// a path in a value: a subquery; NULL when there is no customer
ExecutableUpdate<Order> copy = qb.update(Order.class).set(o -> o.setCustName(o.getCustomer().getName())).all();
assertEquals("update Order e0 set e0.custName = (select s1.name from Customer s1 where s1.id = e0.customer.id)", copy.toHql().hql());
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `update-path`：

```sql
-- H2, MariaDB
update orders o1_0
set cust_name='K'
where (select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id)='Kim' and o1_0.customer_id is not null

-- Oracle
update orders o1_0
set o1_0.cust_name='K'
where (select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id)='Kim' and o1_0.customer_id is not null
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `update-path#2`：

```sql
-- H2, MariaDB
update orders o1_0
set cust_name=(select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id)

-- Oracle
update orders o1_0
set o1_0.cust_name=(select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id)
```

如果订单没有客户，`custName` 会变成 NULL。
在 `where` 中，lambdaQL 会加上 `e0.customer.id is not null`，因此更新修改的行与 `qb.from(Order.class).where(同一个 Lambda)` 返回的行相同（决策 D36）。测试会检查这一点。

<a id="an-aggregate-from-a-subquery"></a>
## 来自子查询的聚合

使用两个参数的 Lambda。第二个参数是上下文对象（`sub`）。
这里不需要 `Agg`；子查询本身有 `sum`、`avg` 等方法，与 [5. 子查询](05-subquery.md)相同。
这种写法**尚未实现**（里程碑 M8）。

```java
qb.update(Order.class)
  .set((o, sub) -> o.setTotal(
      sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).sum(i -> i.getPrice())))
  .where(o -> o.getStatus() == 1)
  .execute();
```

**各数据库的 SQL（计划中）** — Hibernate 对计划中 HQL 的翻译，由 `PlannedSqlTest` 记录；lambdaQL 目前还不会生成此查询。来自 `expected-sql/` 的 [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)，示例 `07-update-sum`：

```sql
-- H2, MariaDB
update orders o1_0
set total=(select sum(i1_0.price) from items i1_0 where i1_0.order_id=o1_0.id)
where o1_0.status=1

-- Oracle
update orders o1_0
set o1_0.total=(select sum(i1_0.price) from items i1_0 where i1_0.order_id=o1_0.id)
where o1_0.status=1
```

如果订单没有明细，`total` 会变成 NULL。这是 SQL 的行为。

<a id="where-with-paths-and-subqueries"></a>
## 使用路径和子查询的 where

`where` 与查询中的相同：

```java
qb.update(Order.class).set(o -> o.setStatus(0)).where(o -> o.getCustomer().getGrade() > 3).execute();

qb.update(Order.class).set(o -> o.setStatus(9))
  .where((o, sub) -> !sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).exists())
  .execute();
```

第一个示例现在就能工作：路径会变成标量子查询，与上面已测试的示例相同。
第二个示例需要 `sub`（尚未实现，里程碑 M8）。

**各数据库的 SQL（计划中）** — Hibernate 对计划中 HQL 的翻译，由 `PlannedSqlTest` 记录；lambdaQL 目前还不会生成此查询。来自 `expected-sql/` 的 [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)，示例 `07-update-not-exists`：

```sql
-- H2, MariaDB
update orders o1_0
set status=9
where not exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id)

-- Oracle
update orders o1_0
set o1_0.status=9
where not exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id)
```

<a id="optimistic-locking-version"></a>
## 乐观锁（`@Version`）

如果实体有 `@Version` 字段，lambdaQL 会渲染 `update versioned`。数据库会把版本号加 1。

**(已测试)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)，region `update-versioned`：

```java
// an entity with a version field (Version annotation): the version goes up too
ExecutableUpdate<VOrder> u = qb.update(VOrder.class).set(v -> v.setStatus(2)).where(v -> v.getId() == 1L);
assertEquals("update versioned VOrder e0 set e0.status = 2 where e0.id = 1L", u.toHql().hql());
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `update-versioned`：

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

Hibernate 会在 SET 列表中加上 `version=(v1_0.version+1)`。要把版本号用作乐观锁，请把它放进 `where` 并检查结果：

```java
int n = qb.update(VOrder.class)
    .set(o -> o.setStatus(8))
    .where(o -> o.getId() == id && o.getVersion() == v)
    .execute();
if (n == 0) throw new OptimisticLockException();   // checking the result is your job
```

不要自己给 `@Version` 字段赋值（规则 U15）。

<a id="many-set-calls-patch"></a>
## 多次调用 `set()`（PATCH）

`set()` 可以调用多次。所有赋值按调用顺序合并成一个 SET 列表（规则 U14）。
规则 U3 和 U4 也跨 `set()` 调用生效。

**(已测试)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)，region `update-patch`：

```java
// PATCH: assign only the fields that have a value; every set() adds assignments
String memo = "checked";
Short newStatus = null;
UpdateSet<Order> u = qb.update(Order.class).set(o -> o.setDescription(memo));
if (newStatus != null) u = u.set(o -> o.setStatus(newStatus));
int changed = u.where(o -> o.getId() == id).execute();

// reading a column that an earlier assignment wrote is an error (U3): the databases disagree on its value
assertThrows(LambdaAnalysisException.class,
        () -> qb.update(Order.class).set(o -> { o.setPrice(1); o.setTex((float) o.getPrice()); }));
// the same column twice is an error (U4), also across set() calls
assertThrows(LambdaAnalysisException.class,
        () -> qb.update(Order.class).set(o -> o.setStatus((short) 1)).set(o -> o.setStatus((short) 2)));
```

`set()` 返回 `UpdateSet`。它有 `set`、`where` 和 `all`，但没有 `execute()`。

<a id="update-all-rows-all"></a>
## 更新所有行：`all()`

没有 `where` 的更新必须写 `all()`。既没有 `where` 也没有 `all()` 时，代码**无法编译**（规则 U5）。

```java
qb.update(Order.class).set(o -> o.setStatus(0)).all().execute();       // OK
qb.update(Order.class).set(o -> o.setStatus(0)).execute();             // compile error: no execute()
```

更新需要事务（见 [7.8](07-dml.md#transactions)）。

<a id="update-the-first-rows-limit"></a>
## 更新前几行：`limit`

在 `where(...)` 或 `all()` 之后写 `orderBy(...)` 和 `limit(n)`，最多更新 `n` 行：即 `qb.from(X.class).where(same).orderBy(same)` 的前 `n` 行。
规则与删除相同（见 [7.4](07-4-delete.md#delete-the-first-rows-limit)）：没有 `orderBy` 时按 id 排序，id 总是最后一个排序键，没有 `limit` 的 `orderBy` 是错误。

**(已测试)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)，region `update-limit`：

```java
// a path in the condition: the nested select joins, so the rows are the same as the select
ExecutableUpdate<Order> u = qb.update(Order.class)
        .set(o -> o.setDescription("top"))
        .where(o -> o.getCustomer().getName().equals("Kim"))
        .orderBy((o, by) -> by.desc(o.getPrice()))
        .limit(1);
assertEquals("update Order e0 set e0.description = 'top' where e0.id in (select t.id from (select x0.id as id from Order x0"
        + " join x0.customer k1 where k1.name = 'Kim' order by x0.price desc, x0.id asc limit 1) t)", u.toHql().hql());
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `update-limit`：

```sql
-- H2
update orders o1_0
set description='top'
where o1_0.id in (select t1_0.id from (select o2_0.id from orders o2_0 join customers c1_0 on c1_0.id=o2_0.customer_id where c1_0.name='Kim' order by o2_0.price desc,o2_0.id fetch first 1 rows only) t1_0(id))

-- MariaDB
update orders o1_0
set description='top'
where o1_0.id in (select t1_0.id from (select o2_0.id id from orders o2_0 join customers c1_0 on c1_0.id=o2_0.customer_id where c1_0.name='Kim' order by o2_0.price desc,o2_0.id limit 1) t1_0)

-- Oracle
update orders o1_0
set o1_0.description='top'
where o1_0.id in (select t1_0.id from (select o2_0.id id from orders o2_0 join customers c1_0 on c1_0.id=o2_0.customer_id where c1_0.name='Kim' order by o2_0.price desc,o2_0.id fetch first 1 rows only) t1_0)
```

路径 `o.getCustomer().getName()` 在内层 select 中是连接，与查询相同（决定 D36）。更新本身不读取其他表。

<a id="update-exactly-one-row-executeone"></a>
## 只更新一行：`executeOne()`

`executeOne()` 以单行保护执行更新。它读取匹配的 id（最多 2 个）。有 2 个 id 时，它什么都不改变，并抛出 `NonUniqueResultException`。有 1 个 id 时，它用 `id = :p and (你的条件)` 更新该行。步骤与删除相同（见 [7.4](07-4-delete.md#delete-exactly-one-row-executeone)）。

```java
int one = qb.update(Order.class)
        .set(o -> o.setDescription("checked"))
        .where(o -> o.getId() == id)
        .executeOne();                             // 0 or 1; 2 or more rows: NonUniqueResultException
```

`execute-one` region 所在的测试方法（`DmlQueryTest` 中的 `executeOneProtects`）也检查了更新上的 `executeOne()`：有 2 行或更多时，什么都不改变。
在 Spring 仓库中，`updateOne(where, set)` 做同样的事（见 [12.4](12-spring-data-jpa.md#one-row-protection-deleteone-and-updateone)）。

---

---

[7. 更新、插入、删除（Java）](07-dml.md) | **7.2 更新** | [7.4 删除](07-4-delete.md) | [7.5 插入](07-5-insert.md) | [7.6 Upsert](07-6-upsert.md) | [Kotlin](../kotlin/07-2-update.md)
