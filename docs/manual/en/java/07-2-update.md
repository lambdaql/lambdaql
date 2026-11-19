<a id="72-update"></a>
# 7.2 Update (Java)

[7. Update, insert, delete (Java)](07-dml.md) | **7.2 Update** | [7.4 Delete](07-4-delete.md) | [7.5 Insert](07-5-insert.md) | [7.6 Upsert](07-6-upsert.md) | [Kotlin](../kotlin/07-2-update.md)

---


<a id="basic-form"></a>
## Basic form

**(tested)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java), region `update`:

```java
short st = 5;
ExecutableUpdate<Order> u = qb.update(Order.class)
        .set(o -> { o.setStatus(st); o.setPrice(o.getPrice() * 2); })
        .where(o -> o.getStatus() == 1);
// the price on the right is the value before the change
assertEquals("update Order e0 set e0.status = :p1, e0.price = (e0.price * 2.0) where e0.status = 1", u.toHql().hql());
int changed = u.execute();
```

`st` is a captured variable, so it is a bind parameter (`:p1`). The constant `2` is written in the lambda, so it stays a literal (`2.0`, decision D37).
`toHql()` gives the HQL and the parameters without running the statement. `execute()` runs it and returns the number of changed rows.

**About the SQL blocks.** `…` is the shortened select list (all entity columns), and `?` is a bind parameter. The lines come from [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java). It runs each query on H2 2.3, MariaDB 10.11 and Oracle 23.26 with Hibernate 7.4, and it fails if Hibernate's SQL changes.

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `update`:

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

<a id="values-null-date-boolean"></a>
## Values: null, date, boolean

```java
qb.update(Order.class)
  .set(o -> { o.setDescription(null); o.setUpdateAt(now); o.setActive(true); })
  .where(o -> o.getId() == id)
  .execute();
```

An enum value works the same way. Each captured value gets **its own parameter**, even when the same value is used twice (rule U7).

<a id="add-to-a-value-default-for-null"></a>
## Add to a value, default for NULL

```java
qb.update(Order.class).set(o -> o.setCnt(o.getCnt() + 1)).where(o -> o.getStatus() == 1).execute();

qb.update(Order.class)
  .set(o -> o.setPrice(Objects.requireNonNullElse(o.getPrice(), 0.0) + 1))
  .all()
  .execute();
// → set o.price = coalesce(o.price, 0.0) + 1
```

<a id="embedded-fields"></a>
## Embedded fields

```java
qb.update(Order.class).set(o -> o.getAddress().setCity(city)).where(o -> o.getId() == id).execute();
// → set o.address.city = :p1

qb.update(Order.class).set(o -> o.setAddress(addr)).where(o -> o.getId() == id).execute();
// → sets all columns of the embedded value (city, zip)
```

<a id="change-an-association-foreign-key"></a>
## Change an association (foreign key)

```java
qb.update(Order.class).set(o -> o.getCustomer().setId(cid)).where(o -> o.getId() == id).execute();
// → set o.customer.id = :p1      (the FK column)

qb.update(Order.class).set(o -> o.setCustomer(null)).where(o -> o.getId() == id).execute();
// → set o.customer = null

qb.update(Order.class).set(o -> o.setCustomer(customer)).where(o -> o.getId() == id).execute();
// → binds the @Id of the captured customer (rule U11)
```

You can only set the `@Id` of an associated entity (the FK).
`o.getCustomer().setName(x)` is an error, because it would change another table (rule U6, tested in [7.8](07-dml.md#rules-checked-at-analysis-time)).

<a id="a-value-from-another-table"></a>
## A value from another table

Read the value through the association path. lambdaQL renders it as a **correlated scalar subquery** (rule U9).
A path in `where` becomes the same kind of subquery.

**(tested)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java), region `update-path`:

```java
// a path in the condition: a subquery, plus "customer exists" (the same rows as the select, D36, U9)
ExecutableUpdate<Order> u = qb.update(Order.class)
        .set(o -> o.setCustName("K"))
        .where(o -> o.getCustomer().getName().equals("Kim"));
assertEquals("update Order e0 set e0.custName = 'K' where (select s1.name from Customer s1 where s1.id = fk(e0.customer)) = 'Kim'"
        + " and e0.customer.id is not null", u.toHql().hql());
// a path in a value: a subquery; NULL when there is no customer
ExecutableUpdate<Order> copy = qb.update(Order.class).set(o -> o.setCustName(o.getCustomer().getName())).all();
assertEquals("update Order e0 set e0.custName = (select s1.name from Customer s1 where s1.id = fk(e0.customer))", copy.toHql().hql());
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `update-path`:

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

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `update-path#2`:

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
In `where`, lambdaQL adds `e0.customer.id is not null`. So the update changes the same rows as `qb.from(Order.class).where(same lambda)` (decision D36). The test checks this.

<a id="an-aggregate-from-a-subquery"></a>
## An aggregate from a subquery

Use a two-argument lambda `set((o, sub) -> ...)`. The second argument is `sub`, the starting point of a subquery (decision D49).
`Agg` is not needed here. The subquery has `sum`, `avg`, ... as in [5. Subqueries](05-subquery.md).

**(tested)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java), region `update-sub`:

```java
// the second parameter starts a subquery, as in where((o, sub) -> ...) (D49)
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

- You can use an aggregate of another table and an aggregate of the same table (measured on H2, MariaDB 10.11 and Oracle 23.26). In an `update`, MariaDB does not give error 1093 either.
- If no row matches, the aggregate is NULL. A NOT NULL column then gives a database error. Wrap the value in `Objects.requireNonNullElse(…, 0.0)` to make it `coalesce(...)`.

<a id="where-with-paths-and-subqueries"></a>
## Where with paths and subqueries

`where` is the same as in select queries.

```java
qb.update(Order.class).set(o -> o.setStatus(0)).where(o -> o.getCustomer().getGrade() > 3).execute();

qb.update(Order.class).set(o -> o.setStatus(9))
  .where((o, sub) -> !sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).exists())
  .execute();
```

Both examples work today.
In the first one, the path becomes a scalar subquery, as in the tested example above.
The second one uses `sub`, as in [5. Subqueries](05-subquery.md). Its HQL is `update Order e0 set e0.status = 9 where not exists (select 1 from Item s1 where s1.order.id = e0.id)`.
The same `where` on a delete is tested (region `dml-subquery`, see [7.4](07-4-delete.md)). The update form has no test of its own yet.

**SQL by database (Hibernate's SQL for this HQL)** — recorded by [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java), which gives the same statement to Hibernate directly. From `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql), example `07-update-not-exists`:

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
## Optimistic locking (`@Version`)

If the entity has a `@Version` field, lambdaQL renders `update versioned`. The database adds 1 to the version.

**(tested)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java), region `update-versioned`:

```java
// an entity with a version field (Version annotation): the version goes up too
ExecutableUpdate<VOrder> u = qb.update(VOrder.class).set(v -> v.setStatus(2)).where(v -> v.getId() == 1L);
assertEquals("update versioned VOrder e0 set e0.status = 2 where e0.id = 1L", u.toHql().hql());
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `update-versioned`:

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

Hibernate adds `version=(v1_0.version+1)` to the SET list. To use the version as an optimistic lock, put it in `where` and check the result.

```java
int n = qb.update(VOrder.class)
    .set(o -> o.setStatus(8))
    .where(o -> o.getId() == id && o.getVersion() == v)
    .execute();
if (n == 0) throw new OptimisticLockException();   // checking the result is your job
```

Do not assign the `@Version` field yourself (rule U15).

<a id="many-set-calls-patch"></a>
## Many `set()` calls (PATCH)

You can call `set()` many times. All assignments join into one SET list, in call order (rule U14).
Rules U3 and U4 also apply across `set()` calls.

**(tested)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java), region `update-patch`:

```java
// PATCH: assign only the fields that have a value; every set() adds assignments
String memo = "checked";
Short newStatus = null;
UpdateSet<Order> u = qb.update(Order.class).set(o -> o.setDescription(memo));
if (newStatus != null) u = u.set(o -> o.setStatus(newStatus));
int changed = u.where(o -> o.getId() == id).execute();

// reading a column that an earlier assignment wrote is an error (U3): the databases disagree on its value
assertThrows(LambdaQLException.class,
        () -> qb.update(Order.class).set(o -> { o.setPrice(1); o.setTex((float) o.getPrice()); }));
// the same column twice is an error (U4), also across set() calls
assertThrows(LambdaQLException.class,
        () -> qb.update(Order.class).set(o -> o.setStatus((short) 1)).set(o -> o.setStatus((short) 2)));
```

`set()` returns an `UpdateSet`. This type has `set`, `where` and `all`, but no `execute()`.

<a id="update-all-rows-all"></a>
## Update all rows: `all()`

An update without `where` must say `all()`. Without `where` or `all()`, the code **does not compile** (rule U5).

```java
qb.update(Order.class).set(o -> o.setStatus(0)).all().execute();       // OK
qb.update(Order.class).set(o -> o.setStatus(0)).execute();             // compile error: no execute()
```

An update needs a transaction (see [7.8](07-dml.md#transactions)).

<a id="update-the-first-rows-limit"></a>
## Update the first rows: `limit`

`orderBy(...)` and `limit(n)` after `where(...)` or `all()` change at most `n` rows. The target is the first `n` rows of `qb.from(X.class).where(same).orderBy(same)`.

The rules are the same as for delete (see [7.4](07-4-delete.md#delete-the-first-rows-limit)). Without `orderBy` the order is by the id, the id is always the last sort key, and `orderBy` without `limit` is an error.

**(tested)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java), region `update-limit`:

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

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `update-limit`:

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

The path `o.getCustomer().getName()` becomes a join in the inner select, as in a select (decision D36). The update statement itself reads no other table.

<a id="update-exactly-one-row-executeone"></a>
## Update exactly one row: `executeOne()`

`executeOne()` runs the update with the one-row protection. It reads the matching ids (at most 2).

With 2 ids it changes nothing and throws `NonUniqueResultException`. With 1 id it changes that row with `id = :p and (condition)`. The steps are the same as for delete (see [7.4](07-4-delete.md#delete-exactly-one-row-executeone)).

```java
int one = qb.update(Order.class)
        .set(o -> o.setDescription("checked"))
        .where(o -> o.getId() == id)
        .executeOne();                             // 0 or 1; 2 or more rows: NonUniqueResultException
```

The test method of the `execute-one` region (`executeOneProtects` in `DmlQueryTest`) also checks `executeOne()` on an update. If 2 or more rows match, nothing changes.

In a Spring repository, `updateOne(where, set)` does the same (see [12.4](12-spring-data-jpa.md#one-row-protection-deleteone-and-updateone)).

---

---

[7. Update, insert, delete (Java)](07-dml.md) | **7.2 Update** | [7.4 Delete](07-4-delete.md) | [7.5 Insert](07-5-insert.md) | [7.6 Upsert](07-6-upsert.md) | [Kotlin](../kotlin/07-2-update.md)
