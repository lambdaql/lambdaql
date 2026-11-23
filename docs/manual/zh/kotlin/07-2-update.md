<a id="72-update"></a>
# 7.2 更新（Kotlin）

[7. 更新、插入、删除（Kotlin）](07-dml.md) | **7.2 更新** | [7.4 删除](07-4-delete.md) | [7.5 插入](07-5-insert.md) | [7.6 Upsert](07-6-upsert.md) | [Java](../java/07-2-update.md)

---


<a id="basic-form"></a>
## 基本形式

```kotlin
val st = 5
val u = qb.update(Order::class.java)
    .set { it.status = st; it.price = it.price!! * 2.0 }
    .where { it.status == 1 }
// the price on the right is the value before the change
// HQL: update Order e0 set e0.status = :p1, e0.price = (e0.price * 2.0) where e0.status = 1
val changed = u.execute()                      // number of updated rows
```

`st` 是捕获值，所以它是绑定参数（`:p1`）。`!!` 会被忽略（D23）。常量 `2.0` 仍是字面量（决策 D37）。
`toHql()` 不执行语句，只给出 HQL 和参数。

**关于 SQL 块。** `…` 是缩短的 select 列表（实体的所有列），`?` 是绑定参数。这些行来自 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)。该测试使用 Hibernate 7.4 在 H2 2.3、MariaDB 10.11 和 Oracle 23.26 上运行每个查询，如果 Hibernate 的 SQL 发生变化，测试就会失败。该测试使用 Java Lambda。含义相同的 Kotlin Lambda 生成相同的 HQL，因此 SQL 也相同。

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `update`：

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

[`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)，region `update`：

```kotlin
val u = qb.update(Order::class.java)
    .set { it.status = st; it.price = it.price * 2 }
    .where { it.customer?.name == "Kim" }
assertEquals("update Order e0 set e0.status = :p1, e0.price = (e0.price * 2.0)"
    + " where (select s1.name from Customer s1 where s1.id = fk(e0.customer)) = 'Kim' and e0.customer.id is not null",
    u.toHql().hql())
```

`st` 是捕获的 `Short`（`7`）。测试实体的 `price` 是基本类型 `double`，所以没有 `!!`。update 的 `where` 中的路径 `it.customer?.name` 会变成子查询（见下面的[带路径和子查询的 where](#where-with-paths-and-subqueries)）。测试执行语句并读回结果行。
更多用 Java 写的语句在 [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) 中测试（H2、MariaDB 和 Oracle）。这些示例见[本页的 Java 版本](../java/07-2-update.md)。

<a id="values-null-date-boolean"></a>
## 值：null、日期、布尔值

```kotlin
qb.update(Order::class.java)
    .set { it.description = null; it.updateAt = now; it.active = true }
    .where { it.id == id }
    .execute()
```

枚举值的处理方式相同。每个捕获值都会得到**自己的参数**，即使同一个值使用了两次（规则 U7）。

<a id="add-to-a-value-default-for-null"></a>
## 在值上累加，为 NULL 提供默认值

```kotlin
qb.update(Order::class.java).set { it.cnt += 1 }.where { it.status == 1 }.execute()
// → set o.cnt = o.cnt + 1

qb.update(Order::class.java)
    .set { it.price = (it.price ?: 0.0) + 1 }
    .all()
    .execute()
// → set o.price = coalesce(o.price, 0.0) + 1.0
```

在 Kotlin 中，`+=` 需要非空属性（`cnt: Int`）。对于可空属性，请写 `it.total = it.total!! + 1`，或者像上面那样使用 `?:`。

<a id="embedded-fields"></a>
## 嵌入字段

```kotlin
qb.update(Order::class.java).set { it.address.city = city }.where { it.id == id }.execute()
// → set o.address.city = :p1

qb.update(Order::class.java).set { it.address = addr }.where { it.id == id }.execute()
// → sets all columns of the embedded value (city, zip)
```

<a id="change-an-association-foreign-key"></a>
## 修改关联（外键）

```kotlin
qb.update(Order::class.java).set { it.customer?.id = cid }.where { it.id == id }.execute()
// → set o.customer.id = :p1      (the FK column; the ?. assignment always assigns)

qb.update(Order::class.java).set { it.customer = null }.where { it.id == id }.execute()
// → set o.customer = null

qb.update(Order::class.java).set { it.customer = customer }.where { it.id == id }.execute()
// → binds the @Id of the captured customer (rule U11)
```

对于关联实体，你只能设置它的 `@Id`（外键）。
`it.customer?.name = x` 是错误：它会修改另一张表（规则 U6）。

<a id="a-value-from-another-table"></a>
## 来自另一张表的值

通过关联路径读取值。lambdaQL 把它输出为**关联标量子查询**（规则 U9）。
`where` 中的路径也会变成同样的子查询。

```kotlin
// a path in the condition: a subquery, plus "customer exists" (the same rows as the select, D36, U9)
val u = qb.update(Order::class.java)
    .set { it.custName = "K" }
    .where { it.customer?.name == "Kim" }
// HQL: update Order e0 set e0.custName = 'K' where (select s1.name from Customer s1 where s1.id = fk(e0.customer)) = 'Kim' and e0.customer.id is not null

// a path in a value: a subquery; NULL when there is no customer
val copy = qb.update(Order::class.java).set { it.custName = it.customer?.name }.all()
// HQL: update Order e0 set e0.custName = (select s1.name from Customer s1 where s1.id = fk(e0.customer))
```

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `update-path`：

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

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `update-path#2`：

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

如果订单没有客户，`custName` 会变成 NULL。
在 `where` 中，lambdaQL 会加上 `e0.customer.id is not null`，因此更新修改的行与 `qb.from(Order::class.java).where { 同一个 Lambda }` 返回的行相同（决策 D36）。Java 测试会检查这一点。

**`?.` 与 `||`。** 如果条件中用 `?.` 经过的步骤在查询中成为 `left join`（决策 D54，见 [3.10](03-where.md#310-association-paths)），DML 不会添加 `e0.customer.id is not null`。
因此没有客户的订单也会与查询完全一样成为更新对象。像上面的示例那样，只有 `?.` 而没有客户的行无法使条件为真时，检查会保留。

[`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)，region `update-safe-call`：

```kotlin
// UPDATE: the left join of ?. drops the "customer exists" check, so the order without a customer is changed too
val u = qb.update(Order::class.java)
    .set { it.status = st }
    .where { it.status.toInt() == 1 || it.customer?.name == "Kim" }
assertEquals("update Order e0 set e0.status = :p1"
    + " where e0.status = 1 or (select s1.name from Customer s1 where s1.id = fk(e0.customer)) = 'Kim'", u.toHql().hql())
```

**各数据库的 SQL（直接执行 Kotlin region 中 HQL 的结果）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `update-safe-call`：

```sql
-- H2
update orders o1_0
set status=?
where o1_0.status=1 or (select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id)='Kim'

-- MariaDB
update orders
set status=?
where status=1 or (select c1_0.name from customers c1_0 where c1_0.id=orders.customer_id)='Kim'

-- Oracle
update orders o1_0
set o1_0.status=?
where o1_0.status=1 or (select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id)='Kim'
```

<a id="an-aggregate-from-a-subquery"></a>
## 来自子查询的聚合

使用带两个参数的 Lambda `set { o, sub -> ... }`。第二个参数是子查询的起点 `sub`（决策 D49）。
这里不需要 `Agg`；子查询有 `sum`、`avg` 等，与 [5. 子查询](05-subquery.md) 中相同。

[`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)，region `update-sub`：

```kotlin
// the second parameter starts a subquery, as in where { o, sub -> ... } (D49)
val u = qb.update(Order::class.java)
    .set { o, sub -> o.price = sub.from(Item::class.java).where { i -> i.order?.id == o.id }.sum { it.price } }
    .where { o, sub -> sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
assertEquals("update Order e0 set e0.price = (select sum(s1.price) from Item s1 where s1.order.id = e0.id)"
        + " where exists (select 1 from Item s1 where s1.order.id = e0.id)", u.toHql().hql())
```

- 读取其他表的聚合和读取同一张表的聚合都可以使用（在 H2、MariaDB 10.11、Oracle 23.26 上实测）。在 `update` 中，MariaDB 也不会报错误 1093。
- 没有匹配的行时，聚合为 NULL。如果是 NOT NULL 列，数据库会报错，因此请用 `?: 0.0` 或 `Objects.requireNonNullElse(…, 0.0)` 包装，使其变成 `coalesce(...)`。

一个参数的尾随 Lambda（`set { it... }`）和两个参数的尾随 Lambda（`set { o, sub -> ... }`）是不同的重载。Kotlin 会选择正确的那一个。

<a id="where-with-paths-and-subqueries"></a>
## 带路径和子查询的 where

`where` 与查询中的相同。

```kotlin
qb.update(Order::class.java).set { it.status = 0 }.where { it.customer?.grade!! > 3 }.execute()

qb.update(Order::class.java).set { it.status = 9 }
    .where { o, sub -> !sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
    .execute()
```

两个示例现在都能工作。
第一个示例遵循上面已测试的规则：路径会变成标量子查询。
第二个示例使用 `sub`，与 [5. 子查询](05-subquery.md) 相同。它的 HQL 是 `update Order e0 set e0.status = 9 where not exists (select 1 from Item s1 where s1.order.id = e0.id)`。
同样的 `where` 在 delete 上已用 Java Lambda 测试（region `dml-subquery`，见 [7.4](07-4-delete.md)）。update 写法还没有自己的测试。

**各数据库的 SQL（Hibernate 为此 HQL 生成的 SQL）** — 由 [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java) 记录，它把同样的语句直接交给 Hibernate。来自 `expected-sql/` 的 [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)，示例 `07-update-not-exists`：

```sql
-- H2
update orders o1_0
set status=9
where not exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id)

-- MariaDB
update orders
set status=9
where not exists(select 1 from items i1_0 where i1_0.order_id=orders.id)

-- Oracle
update orders o1_0
set o1_0.status=9
where not exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id)
```

<a id="optimistic-locking-version"></a>
## 乐观锁（`@Version`）

如果实体有 `@Version` 属性，lambdaQL 会输出 `update versioned`。数据库会把版本加 1。

```kotlin
// an entity with a version property (@Version): the version goes up too
val u = qb.update(VOrder::class.java).set { it.status = 2 }.where { it.id == 1L }
// HQL: update versioned VOrder e0 set e0.status = 2 where e0.id = 1L
```

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `update-versioned`：

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

Hibernate 会在 SET 列表中加上 `version=(v1_0.version+1)`。要把版本用作乐观锁，请把它放进 `where` 并检查结果：

```kotlin
val n = qb.update(VOrder::class.java)
    .set { it.status = 8 }
    .where { it.id == id && it.version == v }
    .execute()
if (n == 0) throw OptimisticLockException()   // checking the result is your job
```

不要自己给 `@Version` 属性赋值（规则 U15）。

<a id="many-set-calls-patch"></a>
## 多次调用 `set()`（PATCH）

你可以多次调用 `set()`。所有赋值会按调用顺序合并为一个 SET 列表（规则 U14）。
规则 U3 和 U4 也跨 `set()` 调用生效。

```kotlin
// PATCH: assign only the properties that have a value; every set() adds assignments
val memo = "checked"
val newStatus: Int? = null
var u = qb.update(Order::class.java).set { it.description = memo }
if (newStatus != null) u = u.set { it.status = newStatus }
val changed = u.where { it.id == id }.execute()

// reading a column that an earlier assignment wrote is an error (U3): TranslationException (LQL-3042)
qb.update(Order::class.java).set { it.price = 1.0; it.total = it.price }
// the same column twice is an error (U4), also across set() calls: TranslationException (LQL-3041)
qb.update(Order::class.java).set { it.status = 1 }.set { it.status = 2 }
```

`set()` 返回 `UpdateSet`。它有 `set`、`where` 和 `all`，但没有 `execute()`。

<a id="update-all-rows-all"></a>
## 更新所有行：`all()`

没有 `where` 的更新必须写 `all()`。既没有 `where` 也没有 `all()` 时，代码**无法编译**（规则 U5）。

```kotlin
qb.update(Order::class.java).set { it.status = 0 }.all().execute()   // OK
qb.update(Order::class.java).set { it.status = 0 }.execute()         // compile error: no execute()
```

更新需要事务（见 [7.8](07-dml.md#transactions)）。

<a id="update-the-first-rows-limit"></a>
## 更新前几行：`limit`

在 `where { ... }` 或 `all()` 之后写 `orderBy { ... }` 和 `limit(n)`，最多更新 `n` 行：即 `qb.from(X::class.java).where { same }.orderBy { same }` 的前 `n` 行。

规则与删除相同（见 [7.4](07-4-delete.md#delete-the-first-rows-limit)）：没有 `orderBy` 时按 id 排序，id 总是最后一个排序键，没有 `limit` 的 `orderBy` 是错误。

```kotlin
// a path in the condition: the nested select joins, so the rows are the same as the select
val u = qb.update(Order::class.java)
    .set { it.description = "top" }
    .where { it.customer?.name == "Kim" }
    .orderBy { o, by -> by.desc(o.price) }
    .limit(1)
// HQL: update Order e0 set e0.description = 'top' where e0.id in (select t.id from (select x0.id as id from Order x0 join x0.customer k1 where k1.name = 'Kim' order by x0.price desc, x0.id asc limit 1) t)
```

用 Java 写的相同语句在 [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) 中测试（region `update-limit`）。

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `update-limit`：

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

路径 `it.customer?.name` 在内层 select 中是连接，与查询相同（决策 D36）。更新本身不读取其他表。

<a id="update-exactly-one-row-executeone"></a>
## 只更新一行：`executeOne()`

`executeOne()` 以单行保护执行更新。它读取匹配的 id（最多 2 个）。

有 2 个 id 时，它什么都不改变，并抛出 `NonUniqueResultException`。有 1 个 id 时，它用 `id = :p and (你的条件)` 更新该行。步骤与删除相同（见 [7.4](07-4-delete.md#delete-exactly-one-row-executeone)）。

```kotlin
val one = qb.update(Order::class.java)
    .set { it.description = "checked" }
    .where { it.id == id }
    .executeOne()                             // 0 or 1; 2 or more rows: NonUniqueResultException
```

`execute-one` region 所在的 Java 测试方法（`DmlQueryTest` 中的 `executeOneProtects`）也检查了更新上的 `executeOne()`：有 2 行或更多匹配时，什么都不改变。

在 Spring 仓库中，`updateOne({ ... }) { ... }` 做同样的事（见 [12.4](12-spring-data-jpa.md#one-row-protection-deleteone-and-updateone)）。

---

---

[7. 更新、插入、删除（Kotlin）](07-dml.md) | **7.2 更新** | [7.4 删除](07-4-delete.md) | [7.5 插入](07-5-insert.md) | [7.6 Upsert](07-6-upsert.md) | [Java](../java/07-2-update.md)
