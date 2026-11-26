<a id="72-update"></a>
# 7.2 Update (Java)

[7. Update, insert, delete (Java)](07-dml.md) | **7.2 Update** | [7.4 Delete](07-4-delete.md) | [7.5 Insert](07-5-insert.md) | [7.6 Upsert](07-6-upsert.md) | [Kotlin](../kotlin/07-2-update.md)

---

<a id="basic-form"></a>
## Basic form

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

`st` is a captured variable, so it becomes a bind parameter (`:p1`). The constant `2` is written in the lambda expression, so it stays a literal (`2.0`).
`toHql()` returns the HQL and the parameters without running the statement. `execute()` runs the statement and returns the number of updated rows.

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

Example code: [`DmlQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) (`update`).

<a id="values-null-date-boolean"></a>
## Values: null, date, boolean

<!-- region: ManualJavaDmlTest.java#update-values -->
```java
ExecutableUpdate<Order> u = qb.update(Order.class)
        .set(o -> { o.setDescription(null); o.setUpdateAt(now); o.setKind(Order.Kind.RUSH); o.setActive(false); })
        .where(o -> o.getId() == id);
assertEquals("update Order e0 set e0.description = null, e0.updateAt = :p1,"
        + " e0.kind = org.lambdaql.query.Order$Kind.RUSH, e0.active = false where e0.id = :p2", u.toHql().hql());
int changed = u.execute();
```

`null` and the enum and boolean constants written in the lambda expression are literals, and the captured values (`now`, `id`) are bind parameters. Each captured value gets **its own parameter**, even when the same value is used twice.

Example code: [`ManualJavaDmlTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaDmlTest.java) (`update-values`).

<a id="add-to-a-value-default-for-null"></a>
## Add to a value, a default for NULL

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

In a row where `cnt` is NULL, `cnt + 1` stays NULL (SQL meaning). To change NULL into another value, use `Objects.requireNonNullElse`.

Example code: [`ManualJavaDmlTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaDmlTest.java) (`update-add`).

<a id="embedded-fields"></a>
## Embedded fields

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

When you assign the whole value object, the HQL is one `e0.address = :p1`, and Hibernate writes all columns of the embedded value (`city`, `zip`) into the SET list of the SQL.

Example code: [`ManualJavaDmlTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaDmlTest.java) (`update-embedded`).

<a id="change-an-association-foreign-key"></a>
## Change an association (foreign key)

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

On an associated entity, you can set only the `@Id` (FK).
`o.getCustomer().setName(x)` is an error, because it would change another table (see [7.8](07-dml.md#rules-checked-at-analysis-time)).

Example code: [`ManualJavaDmlTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaDmlTest.java) (`update-fk`).

<a id="a-value-from-another-table"></a>
## A value from another table

Read the value through an association path. lambdaQL renders it as a **correlated scalar subquery**.
A path in `where` also becomes a subquery of the same form.

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
In `where`, lambdaQL adds `e0.customer.id is not null`. So the updated rows are the same as the result of `qb.from(Order.class).where(same lambda expression)`.

Example code: [`DmlQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) (`update-path`).

<a id="an-aggregate-from-a-subquery"></a>
## An aggregate from a subquery

Use a lambda expression with two arguments, `set((o, sub) -> ...)`. The second argument is `sub`, the starting point of a subquery.
You do not need `Agg` here. A subquery has `sum`, `avg`, ..., as in [5. Subqueries](05-subquery.md).

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

- You can use both an aggregate of another table and an aggregate of the same table (measured on H2, MariaDB 10.11 and Oracle 23.26). In `update`, MariaDB does not give error 1093 either.
- If no row matches, the aggregate is NULL. For a NOT NULL column this is a database error, so wrap it in `Objects.requireNonNullElse(…, 0.0)` to make `coalesce(...)`.

Example code: [`DmlQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) (`update-sub`).

<a id="where-with-paths-and-subqueries"></a>
## where with paths and subqueries

`where` is the same as in a select query.

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

In the first statement, the path becomes a scalar subquery, and only orders that have a customer are targeted.
The second statement uses `sub`, as in [5. Subqueries](05-subquery.md).

Example code: [`ManualJavaDmlTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaDmlTest.java) (`update-where`).

<a id="optimistic-locking-version"></a>
## Optimistic locking (`@Version`)

If the entity has a `@Version` field, lambdaQL renders `update versioned`. The database adds 1 to the version.

<!-- region: DmlQueryTest.java#update-versioned -->
```java
// an entity with a version field (Version annotation): the version goes up too
ExecutableUpdate<VOrder> u = qb.update(VOrder.class).set(v -> v.setStatus(2)).where(v -> v.getId() == 1L);
assertEquals("update versioned VOrder e0 set e0.status = 2 where e0.id = 1L", u.toHql().hql());
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

If you run the same statement again, it returns 0 because the version does not match. The first run raised the version, so no row matches the old version.

You cannot assign a value to a `@Version` field. An assignment is rejected with `LQL-3068`.

Example code: [`DmlQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) (`update-versioned`), [`ManualJavaDmlTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaDmlTest.java) (`update-version`).

<a id="many-set-calls-patch"></a>
## Many `set()` calls (PATCH)

You can call `set()` many times. All assignments are merged into one SET list in call order.
The rule that you cannot read a column assigned earlier and the rule that you cannot assign the same column twice also apply across many `set()` calls.

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

`set()` returns `UpdateSet`. This type has `set`, `where` and `all`, but no `execute()`.

Example code: [`DmlQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) (`update-patch`).

<a id="update-all-rows-all"></a>
## Update all rows: `all()`

An update without `where` must give `all()`.

<!-- region: ManualJavaDmlTest.java#update-all -->
```java
// without where, all() is required; without both, there is no execute() (a compile error)
ExecutableUpdate<Order> u = qb.update(Order.class).set(o -> o.setStatus((short) 0)).all();
assertEquals("update Order e0 set e0.status = 0", u.toHql().hql());
int changed = u.execute();
```

An update needs a transaction (see [7.8](07-dml.md#transactions)).

Example code: [`ManualJavaDmlTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaDmlTest.java) (`update-all`).

<a id="update-the-first-rows-limit"></a>
## Update the first rows: `limit`

If you add `orderBy(...)` and `limit(n)` after `where(...)` or `all()`, at most `n` rows are updated. The targets are the first `n` rows of `qb.from(X.class).where(same).orderBy(same)`.

The rules are the same as for delete (see [7.4](07-4-delete.md#delete-the-first-rows-limit)). Without `orderBy` the order is by id, the id is always the last sort key, and `orderBy` without `limit` is an error.

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

The path `o.getCustomer().getName()` becomes a join in the inner select, as in select. The update statement itself does not read another table.

Example code: [`DmlQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) (`update-limit`).

<a id="update-exactly-one-row-executeone"></a>
## Update exactly one row: `executeOne()`

`executeOne()` runs the update with one-row protection. It reads at most 2 ids that match the condition.

If there are 2 ids, it changes nothing and throws `NonUniqueResultException`. If there is 1 id, it updates that row with `id = :p and (condition)`. The steps are the same as for delete (see [7.4](07-4-delete.md#delete-exactly-one-row-executeone)).

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

If two or more rows match the condition, as in the second call, it throws `NonUniqueResultException` and changes nothing.

In a Spring repository, `updateOne(where, set)` does the same (see [12.4](12-spring-data-jpa.md#one-row-protection-deleteone-and-updateone)).

---

[7. Update, insert, delete (Java)](07-dml.md) | **7.2 Update** | [7.4 Delete](07-4-delete.md) | [7.5 Insert](07-5-insert.md) | [7.6 Upsert](07-6-upsert.md) | [Kotlin](../kotlin/07-2-update.md)

Example code: [`ManualJavaDmlTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaDmlTest.java) (`update-one`).
