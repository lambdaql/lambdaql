<a id="72-update"></a>
# 7.2 更新（Kotlin）

[7. 更新、插入、删除（Kotlin）](07-dml.md) | **7.2 更新** | [7.4 删除](07-4-delete.md) | [7.5 插入](07-5-insert.md) | [7.6 Upsert](07-6-upsert.md) | [Java](../java/07-2-update.md)

---

<a id="basic-form"></a>
## 基本形式

<!-- region: ManualKotlinDmlTest.kt#update -->
```kotlin
val st: Short = 5
val u = qb.update(Order::class.java)
    .set { it.status = st; it.price = it.price * 2.0 }
    .where { it.status.toInt() == 1 }
// the price on the right is the value before the change
assertEquals("update Order e0 set e0.status = :p1, e0.price = (e0.price * 2.0) where e0.status = 1", u.toHql().hql())
val changed = u.execute()                      // number of updated rows
```

`st` 是捕获的值，因此成为绑定参数（`:p1`）。常量 `2.0` 保持为字面量。
示例实体 `Order` 是 Java 类，`status` 是 `short`，因此条件写成 `it.status.toInt() == 1`。
`toHql()` 不执行语句，而是返回 HQL 和参数。

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

Oracle 在 `set` 中使用别名（`set o1_0.status=?`）。H2 和 MariaDB 不使用。

示例代码：[`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`update`）。

<a id="values-null-date-boolean"></a>
## 值：null、日期、布尔值

<!-- region: ManualKotlinDmlTest.kt#update-values -->
```kotlin
// null, a captured date, a boolean and an enum constant
val u = qb.update(Order::class.java)
    .set { it.description = null; it.updateAt = now; it.isActive = false; it.kind = Order.Kind.RUSH }
    .where { it.id == id }
assertEquals("update Order e0 set e0.description = null, e0.updateAt = :p1, e0.active = false,"
    + " e0.kind = org.lambdaql.query.Order\$Kind.RUSH where e0.id = :p2", u.toHql().hql())
```

`null`、布尔值和 enum 常量成为字面量。enum 常量用完整类名（`org.lambdaql.query.Order$Kind.RUSH`）写出。
布尔属性 `active` 的 Java getter 是 `isActive()`，因此在 Kotlin 中用 `it.isActive` 赋值。
即使同一个值使用两次，每个捕获的值也会得到**各自的参数**。

示例代码：[`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`update-values`）。

<a id="add-to-a-value-default-for-null"></a>
## 在值上累加，为 NULL 提供默认值

<!-- region: ManualKotlinDmlTest.kt#update-add -->
```kotlin
// += on a property: the old value plus 1
val add = qb.update(Order::class.java).set { it.cnt += 1 }.where { it.status.toInt() == 1 }
assertEquals("update Order e0 set e0.cnt = (e0.cnt + 1) where e0.status = 1", add.toHql().hql())
// ?: gives a default for NULL: coalesce
val name = qb.update(Order::class.java).set { it.custName = it.description ?: "none" }.all()
assertEquals("update Order e0 set e0.custName = coalesce(e0.description, 'none')", name.toHql().hql())
```

示例实体的 `cnt` 是 Java `Integer`，因此 Kotlin 把它视为平台类型并编译 `+=`。`cnt` 为 NULL 的行按 SQL 规则保持为 NULL。
`?:` 对字符串和数字都会成为 `coalesce(...)`。`it.total = (it.total ?: 0.0) + 1` 即 `set e0.total = (coalesce(e0.total, 0.0) + 1.0)`。

示例代码：[`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`update-add`）。

<a id="embedded-fields"></a>
## 嵌入字段

<!-- region: ManualKotlinDmlTest.kt#update-embedded -->
```kotlin
// one column of the embedded value
val one = qb.update(Order::class.java).set { it.address.city = city }.where { it.id == id }
assertEquals("update Order e0 set e0.address.city = :p1 where e0.id = :p2", one.toHql().hql())
// the whole embedded value: every column (city, zip)
val all = qb.update(Order::class.java).set { it.address = addr }.where { it.status.toInt() == 1 }
assertEquals("update Order e0 set e0.address = :p1 where e0.status = 1", all.toHql().hql())
```

为整个嵌入值赋值时，HQL 是 `set e0.address = :p1`，Hibernate 写入所有列（`city`、`zip`）。

示例代码：[`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`update-embedded`）。

<a id="change-an-association-foreign-key"></a>
## 修改关联（外键）

<!-- region: ManualKotlinDmlTest.kt#update-fk -->
```kotlin
// the FK column: the ?. assignment always assigns
val fk = qb.update(Order::class.java).set { it.customer?.id = cid }.where { it.id == first }
assertEquals("update Order e0 set e0.customer.id = :p1 where e0.id = :p2", fk.toHql().hql())
// no customer
val none = qb.update(Order::class.java).set { it.customer = null }.where { it.status.toInt() == 3 }
assertEquals("update Order e0 set e0.customer = null where e0.status = 3", none.toHql().hql())
// a captured entity: its @Id is bound
val entity = qb.update(Order::class.java).set { it.customer = kim }.where { it.id == third }
assertEquals("update Order e0 set e0.customer = :p1 where e0.id = :p2", entity.toHql().hql())
```

对于关联实体，只能设置 `@Id`（外键）。
`it.customer?.name = x` 是错误（`LQL-3050`），因为它会修改另一张表（见 [7.8](07-dml.md#rules-checked-at-analysis-time)）。

示例代码：[`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`update-fk`）。

<a id="a-value-from-another-table"></a>
## 来自另一张表的值

请通过关联路径读取值。lambdaQL 把它输出为**关联标量子查询**。
`where` 中的路径也会成为同样形式的子查询。

<!-- region: ManualKotlinDmlTest.kt#update-path -->
```kotlin
// a path in the condition: a subquery, plus "customer exists" (the same rows as the select, U9)
val u = qb.update(Order::class.java)
    .set { it.custName = "K" }
    .where { it.customer?.name == "Kim" }
assertEquals("update Order e0 set e0.custName = 'K' where (select s1.name from Customer s1 where s1.id = fk(e0.customer)) = 'Kim'"
    + " and e0.customer.id is not null", u.toHql().hql())
// a path in a value: a subquery; NULL when there is no customer
val copy = qb.update(Order::class.java).set { it.custName = it.customer?.name }.all()
assertEquals("update Order e0 set e0.custName = (select s1.name from Customer s1 where s1.id = fk(e0.customer))",
    copy.toHql().hql())
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

如果订单没有客户，`custName` 会成为 NULL。
对于在查询中成为 inner join 的路径，DML 也会加上客户存在检查（`e0.customer.id is not null`）。上面的 `where { it.customer?.name == "Kim" }` 不可能与没有客户的订单匹配，因此是 inner join，会加上这个检查。
对于在查询中成为 `left join` 的路径，不会加上这个检查。对没有客户的订单，子查询为 NULL，因此不会匹配 `= 'Kim'` 这样的比较。无论哪种情况，被更新的行都与 `qb.from(Order::class.java).where { 同一个 Lambda 表达式 }` 的结果相同。

**`?.` 与 `||`。** 像 `it.status == 1 || it.customer?.name == "Kim"` 这样，没有客户的订单也可能使条件为真时，这些订单也会与查询完全一样成为更新对象（见 [3.10](03-where.md#310-association-paths)）。

<!-- region: KotlinQueryTest.kt#update-safe-call -->
```kotlin
// UPDATE: the left join of ?. drops the "customer exists" check, so the order without a customer is changed too
val u = qb.update(Order::class.java)
    .set { it.status = st }
    .where { it.status.toInt() == 1 || it.customer?.name == "Kim" }
assertEquals("update Order e0 set e0.status = :p1"
    + " where e0.status = 1 or (select s1.name from Customer s1 where s1.id = fk(e0.customer)) = 'Kim'", u.toHql().hql())
```

**各数据库的 SQL**

<!-- sql: update-safe-call -->
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

示例代码：[`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`update-path`）、[`KotlinQueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)（`update-safe-call`）。

<a id="an-aggregate-from-a-subquery"></a>
## 来自子查询的聚合

请使用带两个参数的 Lambda 表达式 `set { o, sub -> ... }`。第二个参数是子查询的起点 `sub`。
这里不需要 `Agg`。子查询有 `sum`、`avg` 等，与 [5. 子查询](05-subquery.md) 相同。

<!-- region: KotlinQueryTest.kt#update-sub -->
```kotlin
// the second parameter starts a subquery, as in where { o, sub -> ... }
val u = qb.update(Order::class.java)
    .set { o, sub -> o.price = sub.from(Item::class.java).where { i -> i.order?.id == o.id }.sum { it.price } }
    .where { o, sub -> sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
assertEquals("update Order e0 set e0.price = (select sum(s1.price) from Item s1 where s1.order.id = e0.id)"
        + " where exists (select 1 from Item s1 where s1.order.id = e0.id)", u.toHql().hql())
```

- 读取其他表的聚合和读取同一张表的聚合都可以使用（在 H2、MariaDB 10.11、Oracle 23.26 上实测）。在 `update` 中，MariaDB 也不会报错误 1093。
- 没有匹配的行时，聚合为 NULL。如果是 NOT NULL 列会发生数据库错误，因此请加上 `?: 0.0`，使其成为 `coalesce(...)`。

<!-- region: ManualKotlinDmlTest.kt#update-sub-coalesce -->
```kotlin
// sum gives NULL when there is no row; ?: becomes coalesce and keeps the NOT NULL price valid
val u = qb.update(Order::class.java)
    .set { o, sub ->
        o.price = sub.from(Item::class.java).where { i -> i.order?.id == o.id }.sum { it.price } ?: 0.0
    }
    .all()
assertEquals("update Order e0 set e0.price = coalesce((select sum(s1.price) from Item s1 where s1.order.id = e0.id), 0.0)",
    u.toHql().hql())
```

没有商品的订单，价格会成为 `0.0`。

一个参数的尾随 Lambda 表达式（`set { it... }`）和两个参数的尾随 Lambda 表达式（`set { o, sub -> ... }`）是不同的重载。Kotlin 会选择合适的那一个。

示例代码：[`KotlinQueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)（`update-sub`）、[`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`update-sub-coalesce`）。

<a id="where-with-paths-and-subqueries"></a>
## 带路径和子查询的 where

`where` 与查询相同。

<!-- region: ManualKotlinDmlTest.kt#update-where -->
```kotlin
// a path: a subquery, as above
val gold = qb.update(Order::class.java).set { it.status = 0 }.where { it.customer?.grade!! > 3 }
assertEquals("update Order e0 set e0.status = 0 where (select s1.grade from Customer s1 where s1.id = fk(e0.customer)) > 3"
    + " and e0.customer.id is not null", gold.toHql().hql())
// a subquery with sub, as in chapter 5
val noItems = qb.update(Order::class.java).set { it.status = 9 }
    .where { o, sub -> !sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
assertEquals("update Order e0 set e0.status = 9 where not exists (select 1 from Item s1 where s1.order.id = e0.id)",
    noItems.toHql().hql())
```

第一条语句中的路径按上面的规则成为标量子查询。第二条语句像 [5. 子查询](05-subquery.md) 一样使用 `sub`。

示例代码：[`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`update-where`）。

<a id="optimistic-locking-version"></a>
## 乐观锁（`@Version`）

实体有 `@Version` 属性时，lambdaQL 输出 `update versioned`。数据库会把版本加 1。

<!-- region: ManualKotlinDmlTest.kt#update-versioned -->
```kotlin
// an entity with a version property (@Version): the version goes up too
val u = qb.update(VOrder::class.java).set { it.status = 2 }.where { it.id == 1L }
assertEquals("update versioned VOrder e0 set e0.status = 2 where e0.id = 1L", u.toHql().hql())
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

Hibernate 在 SET 列表中加上 `version=(v1_0.version+1)`。要把版本用作乐观锁，请把版本放进 `where` 并检查结果。

<!-- region: ManualKotlinDmlTest.kt#update-version-check -->
```kotlin
val n = qb.update(VOrder::class.java)
    .set { it.status = 8 }
    .where { it.id == id && it.version == v }
    .execute()
if (n == 0) throw OptimisticLockException()   // checking the result is your job
```

再次执行同一条语句时，`v` 是旧版本，因此更新 0 行并发生 `OptimisticLockException`。

不能给 `@Version` 属性赋值。赋值会以 `LQL-3068` 被拒绝。

示例代码：[`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`update-versioned`、`update-version-check`）。

<a id="many-set-calls-patch"></a>
## 多次调用 `set()`（PATCH）

`set()` 可以调用多次。所有赋值按调用顺序合并为一个 SET 列表。
不能再次读取之前已赋值的列、不能对同一列赋值两次这两条规则，在多次 `set()` 调用之间同样适用。

<!-- region: ManualKotlinDmlTest.kt#update-patch -->
```kotlin
// PATCH: assign only the properties that have a value; every set() adds assignments
val memo = "checked"
val newStatus: Short? = null
var u = qb.update(Order::class.java).set { it.description = memo }
if (newStatus != null) u = u.set { it.status = newStatus }
val changed = u.where { it.id == id }.execute()

// reading a column that an earlier assignment wrote is an error
val read = assertThrows(LambdaQLException::class.java) {
    qb.update(Order::class.java).set { it.price = 1.0; it.total = it.price }
}
assertEquals("LQL-3042", read.code())
// the same column twice is an error, also across set() calls
val twice = assertThrows(LambdaQLException::class.java) {
    qb.update(Order::class.java).set { it.status = 1 }.set { it.status = 2 }
}
assertEquals("LQL-3041", twice.code())
```

`set()` 返回 `UpdateSet`。这个类型有 `set`、`where`、`all`，没有 `execute()`。

示例代码：[`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`update-patch`）。

<a id="update-all-rows-all"></a>
## 更新所有行：`all()`

没有 `where` 的 update 必须显式写出 `all()`。

<!-- region: ManualKotlinDmlTest.kt#update-all -->
```kotlin
// no where: all() is required, without it there is no execute()
val u = qb.update(Order::class.java).set { it.status = 0 }.all()
assertEquals("update Order e0 set e0.status = 0", u.toHql().hql())
val changed = u.execute()
```

update 需要事务（见 [7.8](07-dml.md#transactions)）。

示例代码：[`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`update-all`）。

<a id="update-the-first-rows-limit"></a>
## 更新前面的行：`limit`

在 `where { ... }` 或 `all()` 之后加上 `orderBy { ... }` 和 `limit(n)`，最多更新 `n` 行。对象是 `qb.from(X::class.java).where { same }.orderBy { same }` 的前 `n` 行。

规则与 delete 相同（见 [7.4](07-4-delete.md#delete-the-first-rows-limit)）。没有 `orderBy` 时按 id 顺序，id 始终是最后一个排序键，没有 `limit` 的 `orderBy` 是错误。

<!-- region: ManualKotlinDmlTest.kt#update-limit -->
```kotlin
// a path in the condition: the nested select joins, so the rows are the same as the select
val u = qb.update(Order::class.java)
    .set { it.description = "top" }
    .where { it.customer?.name == "Kim" }
    .orderBy { o -> desc(o.price) }
    .limit(1)
assertEquals("update Order e0 set e0.description = 'top' where e0.id in (select t.id from (select x0.id as id from Order x0"
    + " join x0.customer k1 where k1.name = 'Kim' order by x0.price desc, x0.id asc limit 1) t)", u.toHql().hql())
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

路径 `it.customer?.name` 与查询一样，在内层 select 中成为连接。update 语句本身不读取其他表。

示例代码：[`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`update-limit`）。

<a id="update-exactly-one-row-executeone"></a>
## 恰好更新一行：`executeOne()`

`executeOne()` 在单行保护下执行 update。它最多读取 2 个匹配条件的 id。

如果有 2 个 id，就不做任何修改并抛出 `NonUniqueResultException`。如果有 1 个 id，就用 `id = :p and (条件)` 更新该行。步骤与 delete 相同（见 [7.4](07-4-delete.md#delete-exactly-one-row-executeone)）。

<!-- region: ManualKotlinDmlTest.kt#update-one -->
```kotlin
// one row matches: it is changed
val one = qb.update(Order::class.java)
    .set { it.description = "checked" }
    .where { it.id == id }
    .executeOne()                             // 0 or 1
// two or more rows match: nothing is changed
assertThrows(NonUniqueResultException::class.java) {
    qb.update(Order::class.java).set { it.description = "x" }.where { it.isActive }.executeOne()
}
```

第二条语句中所有订单都匹配条件，因此不修改任何行。

在 Spring 仓库中，`updateOne({ ... }) { ... }` 执行相同的操作（见 [12.4](12-spring-data-jpa.md#one-row-protection-deleteone-and-updateone)）。

---

[7. 更新、插入、删除（Kotlin）](07-dml.md) | **7.2 更新** | [7.4 删除](07-4-delete.md) | [7.5 插入](07-5-insert.md) | [7.6 Upsert](07-6-upsert.md) | [Java](../java/07-2-update.md)

示例代码：[`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`update-one`）。
