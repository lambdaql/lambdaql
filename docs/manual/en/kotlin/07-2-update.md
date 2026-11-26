<a id="72-update"></a>
# 7.2 Update (Kotlin)

[7. Update, insert, delete (Kotlin)](07-dml.md) | **7.2 Update** | [7.4 Delete](07-4-delete.md) | [7.5 Insert](07-5-insert.md) | [7.6 Upsert](07-6-upsert.md) | [Java](../java/07-2-update.md)

---

<a id="basic-form"></a>
## Basic form

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

`st` is a captured value, so it becomes a bind parameter (`:p1`). The constant `2.0` stays a literal.
The example entity `Order` is a Java class and `status` is a `short`, so the condition is written as `it.status.toInt() == 1`.
`toHql()` returns the HQL and parameters without running the statement.

**SQL by database**

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

Oracle writes the alias in `set` (`set o1_0.status=?`). H2 and MariaDB do not.

Example code: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt) (`update`).

<a id="values-null-date-boolean"></a>
## Values: null, date, boolean

<!-- region: ManualKotlinDmlTest.kt#update-values -->
```kotlin
// null, a captured date, a boolean and an enum constant
val u = qb.update(Order::class.java)
    .set { it.description = null; it.updateAt = now; it.isActive = false; it.kind = Order.Kind.RUSH }
    .where { it.id == id }
assertEquals("update Order e0 set e0.description = null, e0.updateAt = :p1, e0.active = false,"
    + " e0.kind = org.lambdaql.query.Order\$Kind.RUSH where e0.id = :p2", u.toHql().hql())
```

`null`, booleans and enum constants become literals. An enum constant is written with its full class name (`org.lambdaql.query.Order$Kind.RUSH`).
The Java getter of the boolean property `active` is `isActive()`, so in Kotlin you assign `it.isActive`.
A captured value gets **its own parameter** each time, even if you use the same value twice.

Example code: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt) (`update-values`).

<a id="add-to-a-value-default-for-null"></a>
## Add to a value, default for NULL

<!-- region: ManualKotlinDmlTest.kt#update-add -->
```kotlin
// += on a property: the old value plus 1
val add = qb.update(Order::class.java).set { it.cnt += 1 }.where { it.status.toInt() == 1 }
assertEquals("update Order e0 set e0.cnt = (e0.cnt + 1) where e0.status = 1", add.toHql().hql())
// ?: gives a default for NULL: coalesce
val name = qb.update(Order::class.java).set { it.custName = it.description ?: "none" }.all()
assertEquals("update Order e0 set e0.custName = coalesce(e0.description, 'none')", name.toHql().hql())
```

The `cnt` of the example entity is a Java `Integer`, so Kotlin sees a platform type and compiles `+=`. Rows where `cnt` is NULL stay NULL, by the SQL rules.
`?:` becomes `coalesce(...)` for both strings and numbers. `it.total = (it.total ?: 0.0) + 1` is `set e0.total = (coalesce(e0.total, 0.0) + 1.0)`.

Example code: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt) (`update-add`).

<a id="embedded-fields"></a>
## Embedded fields

<!-- region: ManualKotlinDmlTest.kt#update-embedded -->
```kotlin
// one column of the embedded value
val one = qb.update(Order::class.java).set { it.address.city = city }.where { it.id == id }
assertEquals("update Order e0 set e0.address.city = :p1 where e0.id = :p2", one.toHql().hql())
// the whole embedded value: every column (city, zip)
val all = qb.update(Order::class.java).set { it.address = addr }.where { it.status.toInt() == 1 }
assertEquals("update Order e0 set e0.address = :p1 where e0.status = 1", all.toHql().hql())
```

If you assign a whole embedded value, the HQL is `set e0.address = :p1`, and Hibernate writes all columns (`city`, `zip`).

Example code: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt) (`update-embedded`).

<a id="change-an-association-foreign-key"></a>
## Change an association (foreign key)

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

On an associated entity, you can set only the `@Id` (FK).
`it.customer?.name = x` is an error (`LQL-3050`), because it would change another table (see [7.8](07-dml.md#rules-checked-at-analysis-time)).

Example code: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt) (`update-fk`).

<a id="a-value-from-another-table"></a>
## A value from another table

Read the value through an association path. lambdaQL writes it as a **correlated scalar subquery**.
A path in `where` also becomes a subquery of the same form.

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

**SQL by database**

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

**SQL by database**

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

If an order has no customer, `custName` becomes NULL.
For a path that is an inner join in a select, DML also adds a customer check (`e0.customer.id is not null`). `where { it.customer?.name == "Kim" }` above cannot match an order without a customer, so it is an inner join, and this check is added.
For a path that is a `left join` in a select, this check is not added. For an order without a customer, the subquery is NULL, so it does not match a comparison such as `= 'Kim'`. In both cases, the updated rows are the same as the result of `qb.from(Order::class.java).where { same lambda }`.

**`?.` and `||`.** If an order without a customer can make the condition true, as in `it.status == 1 || it.customer?.name == "Kim"`, such orders are also updated, exactly as in a select (see [3.10](03-where.md#310-association-paths)).

<!-- region: KotlinQueryTest.kt#update-safe-call -->
```kotlin
// UPDATE: the left join of ?. drops the "customer exists" check, so the order without a customer is changed too
val u = qb.update(Order::class.java)
    .set { it.status = st }
    .where { it.status.toInt() == 1 || it.customer?.name == "Kim" }
assertEquals("update Order e0 set e0.status = :p1"
    + " where e0.status = 1 or (select s1.name from Customer s1 where s1.id = fk(e0.customer)) = 'Kim'", u.toHql().hql())
```

**SQL by database**

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

Example code: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt) (`update-path`), [`KotlinQueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt) (`update-safe-call`).

<a id="an-aggregate-from-a-subquery"></a>
## An aggregate from a subquery

Use the two-argument lambda `set { o, sub -> ... }`. The second argument is `sub`, the starting point of subqueries.
You do not need `Agg` here. Subqueries have `sum`, `avg`, ..., as in [5. Subqueries](05-subquery.md).

<!-- region: KotlinQueryTest.kt#update-sub -->
```kotlin
// the second parameter starts a subquery, as in where { o, sub -> ... }
val u = qb.update(Order::class.java)
    .set { o, sub -> o.price = sub.from(Item::class.java).where { i -> i.order?.id == o.id }.sum { it.price } }
    .where { o, sub -> sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
assertEquals("update Order e0 set e0.price = (select sum(s1.price) from Item s1 where s1.order.id = e0.id)"
        + " where exists (select 1 from Item s1 where s1.order.id = e0.id)", u.toHql().hql())
```

- You can use both an aggregate of another table and an aggregate of the same table (measured on H2, MariaDB 10.11 and Oracle 23.26). In `update`, MariaDB does not give error 1093 either.
- If no row matches, the aggregate is NULL. For a NOT NULL column this is a database error, so add `?: 0.0` to make it `coalesce(...)`.

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

The price of an order without items becomes `0.0`.

A trailing lambda with one argument (`set { it... }`) and a trailing lambda with two arguments (`set { o, sub -> ... }`) are different overloads. Kotlin picks the right one.

Example code: [`KotlinQueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt) (`update-sub`), [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt) (`update-sub-coalesce`).

<a id="where-with-paths-and-subqueries"></a>
## Where with paths and subqueries

`where` is the same as in a select query.

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

The path in the first statement becomes a scalar subquery, by the rule above. The second statement uses `sub`, as in [5. Subqueries](05-subquery.md).

Example code: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt) (`update-where`).

<a id="optimistic-locking-version"></a>
## Optimistic locking (`@Version`)

If the entity has a `@Version` property, lambdaQL writes `update versioned`. The database adds 1 to the version.

<!-- region: ManualKotlinDmlTest.kt#update-versioned -->
```kotlin
// an entity with a version property (@Version): the version goes up too
val u = qb.update(VOrder::class.java).set { it.status = 2 }.where { it.id == 1L }
assertEquals("update versioned VOrder e0 set e0.status = 2 where e0.id = 1L", u.toHql().hql())
```

**SQL by database**

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

Hibernate adds `version=(v1_0.version+1)` to the SET list. To use the version for optimistic locking, put the version in `where` and check the result.

<!-- region: ManualKotlinDmlTest.kt#update-version-check -->
```kotlin
val n = qb.update(VOrder::class.java)
    .set { it.status = 8 }
    .where { it.id == id && it.version == v }
    .execute()
if (n == 0) throw OptimisticLockException()   // checking the result is your job
```

If you run the same statement again, `v` is the old version, so 0 rows are updated and `OptimisticLockException` is thrown.

You cannot assign the `@Version` property. An assignment is rejected with `LQL-3068`.

Example code: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt) (`update-versioned`, `update-version-check`).

<a id="many-set-calls-patch"></a>
## Many `set()` calls (PATCH)

You can call `set()` more than once. All assignments are merged into one SET list, in call order.
The rule that you cannot read a column assigned earlier and the rule that you cannot assign the same column twice also apply across many `set()` calls.

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

`set()` returns `UpdateSet`. This type has `set`, `where` and `all`, but no `execute()`.

Example code: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt) (`update-patch`).

<a id="update-all-rows-all"></a>
## Update all rows: `all()`

An update without `where` must say `all()`.

<!-- region: ManualKotlinDmlTest.kt#update-all -->
```kotlin
// no where: all() is required, without it there is no execute()
val u = qb.update(Order::class.java).set { it.status = 0 }.all()
assertEquals("update Order e0 set e0.status = 0", u.toHql().hql())
val changed = u.execute()
```


An update needs a transaction (see [7.8](07-dml.md#transactions)).

Example code: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt) (`update-all`).

<a id="update-the-first-rows-limit"></a>
## Update the first rows: `limit`

If you add `orderBy { ... }` and `limit(n)` after `where { ... }` or `all()`, at most `n` rows are updated. The targets are the first `n` rows of `qb.from(X::class.java).where { same }.orderBy { same }`.

The rules are the same as for delete (see [7.4](07-4-delete.md#delete-the-first-rows-limit)). Without `orderBy`, it is id order. The id is always the last sort key, and `orderBy` without `limit` is an error.

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

**SQL by database**

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

The path `it.customer?.name` becomes a join in the inner select, the same as in a select. The update statement itself does not read another table.

Example code: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt) (`update-limit`).

<a id="update-exactly-one-row-executeone"></a>
## Update exactly one row: `executeOne()`

`executeOne()` runs the update with one-row protection. It reads at most 2 matching ids.

If there are 2 ids, it changes nothing and throws `NonUniqueResultException`. If there is 1 id, it updates that row with `id = :p and (condition)`. The steps are the same as for delete (see [7.4](07-4-delete.md#delete-exactly-one-row-executeone)).

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

In the second statement, all orders match the condition, so no row is changed.

In a Spring repository, `updateOne({ ... }) { ... }` does the same (see [12.4](12-spring-data-jpa.md#one-row-protection-deleteone-and-updateone)).

---

[7. Update, insert, delete (Kotlin)](07-dml.md) | **7.2 Update** | [7.4 Delete](07-4-delete.md) | [7.5 Insert](07-5-insert.md) | [7.6 Upsert](07-6-upsert.md) | [Java](../java/07-2-update.md)

Example code: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt) (`update-one`).
