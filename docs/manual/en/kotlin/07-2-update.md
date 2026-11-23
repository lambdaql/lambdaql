<a id="72-update"></a>
# 7.2 Update (Kotlin)

[7. Update, insert, delete (Kotlin)](07-dml.md) | **7.2 Update** | [7.4 Delete](07-4-delete.md) | [7.5 Insert](07-5-insert.md) | [7.6 Upsert](07-6-upsert.md) | [Java](../java/07-2-update.md)

---


<a id="basic-form"></a>
## Basic form

```kotlin
val st = 5
val u = qb.update(Order::class.java)
    .set { it.status = st; it.price = it.price!! * 2.0 }
    .where { it.status == 1 }
// the price on the right is the value before the change
// HQL: update Order e0 set e0.status = :p1, e0.price = (e0.price * 2.0) where e0.status = 1
val changed = u.execute()                      // number of updated rows
```

`st` is a captured value, so it is a bind parameter (`:p1`). `!!` is ignored (D23). The constant `2.0` stays a literal (decision D37).
`toHql()` gives the HQL and the parameters without running the statement.

**About the SQL blocks.** `…` is the shortened select list (all entity columns), and `?` is a bind parameter. The lines come from [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java). It runs each query on H2 2.3, MariaDB 10.11 and Oracle 23.26 with Hibernate 7.4, and it fails if Hibernate's SQL changes. The test uses Java lambdas. A Kotlin lambda with the same meaning gives the same HQL, so it gets the same SQL.

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `update`:

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

[`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt), region `update`:

```kotlin
val u = qb.update(Order::class.java)
    .set { it.status = st; it.price = it.price * 2 }
    .where { it.customer?.name == "Kim" }
assertEquals("update Order e0 set e0.status = :p1, e0.price = (e0.price * 2.0)"
    + " where (select s1.name from Customer s1 where s1.id = fk(e0.customer)) = 'Kim' and e0.customer.id is not null",
    u.toHql().hql())
```

`st` is a captured `Short` (`7`). The test entity's `price` is a primitive `double`, so there is no `!!`. The path `it.customer?.name` in the `where` of an update becomes a subquery (see [Where with paths and subqueries](#where-with-paths-and-subqueries) below). The test runs the statement and reads the rows back.
More statements written in Java are tested in [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) (H2, MariaDB and Oracle). See [the Java version of this page](../java/07-2-update.md) for those examples.

<a id="values-null-date-boolean"></a>
## Values: null, date, boolean

```kotlin
qb.update(Order::class.java)
    .set { it.description = null; it.updateAt = now; it.active = true }
    .where { it.id == id }
    .execute()
```

An enum value works the same way. Each captured value gets **its own parameter**, even when the same value is used twice (rule U7).

<a id="add-to-a-value-default-for-null"></a>
## Add to a value, default for NULL

```kotlin
qb.update(Order::class.java).set { it.cnt += 1 }.where { it.status == 1 }.execute()
// → set o.cnt = o.cnt + 1

qb.update(Order::class.java)
    .set { it.price = (it.price ?: 0.0) + 1 }
    .all()
    .execute()
// → set o.price = coalesce(o.price, 0.0) + 1.0
```

In Kotlin, `+=` needs a non-null property (`cnt: Int`). For a nullable property, write `it.total = it.total!! + 1` or use `?:` as above.

<a id="embedded-fields"></a>
## Embedded fields

```kotlin
qb.update(Order::class.java).set { it.address.city = city }.where { it.id == id }.execute()
// → set o.address.city = :p1

qb.update(Order::class.java).set { it.address = addr }.where { it.id == id }.execute()
// → sets all columns of the embedded value (city, zip)
```

<a id="change-an-association-foreign-key"></a>
## Change an association (foreign key)

```kotlin
qb.update(Order::class.java).set { it.customer?.id = cid }.where { it.id == id }.execute()
// → set o.customer.id = :p1      (the FK column; the ?. assignment always assigns)

qb.update(Order::class.java).set { it.customer = null }.where { it.id == id }.execute()
// → set o.customer = null

qb.update(Order::class.java).set { it.customer = customer }.where { it.id == id }.execute()
// → binds the @Id of the captured customer (rule U11)
```

You can only set the `@Id` of an associated entity (the FK).
`it.customer?.name = x` is an error, because it would change another table (rule U6).

<a id="a-value-from-another-table"></a>
## A value from another table

Read the value through the association path. lambdaQL renders it as a **correlated scalar subquery** (rule U9).
A path in `where` becomes the same kind of subquery.

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

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `update-path`:

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

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `update-path#2`:

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
In `where`, lambdaQL adds `e0.customer.id is not null`. So the update changes the same rows as `qb.from(Order::class.java).where { same lambda }` returns (decision D36). The Java test checks this.

**`?.` and `||`.** When a step reached with `?.` becomes a `left join` in the select (decision D54, see [3.10](03-where.md#310-association-paths)), DML does not add `e0.customer.id is not null`.
So orders without a customer are changed too, exactly as the select returns them. When there is only `?.` and a row without a customer cannot make the condition true, as in the example above, the check stays.

[`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt), region `update-safe-call`:

```kotlin
// UPDATE: the left join of ?. drops the "customer exists" check, so the order without a customer is changed too
val u = qb.update(Order::class.java)
    .set { it.status = st }
    .where { it.status.toInt() == 1 || it.customer?.name == "Kim" }
assertEquals("update Order e0 set e0.status = :p1"
    + " where e0.status = 1 or (select s1.name from Customer s1 where s1.id = fk(e0.customer)) = 'Kim'", u.toHql().hql())
```

**SQL by database (the HQL of the Kotlin region, run as it is)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `update-safe-call`:

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
## An aggregate from a subquery

Use a two-argument lambda `set { o, sub -> ... }`. The second argument is `sub`, the starting point for subqueries (decision D49).
`Agg` is not needed here. The subquery has `sum`, `avg`, ... as in [5. Subqueries](05-subquery.md).

[`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt), region `update-sub`:

```kotlin
// the second parameter starts a subquery, as in where { o, sub -> ... } (D49)
val u = qb.update(Order::class.java)
    .set { o, sub -> o.price = sub.from(Item::class.java).where { i -> i.order?.id == o.id }.sum { it.price } }
    .where { o, sub -> sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
assertEquals("update Order e0 set e0.price = (select sum(s1.price) from Item s1 where s1.order.id = e0.id)"
        + " where exists (select 1 from Item s1 where s1.order.id = e0.id)", u.toHql().hql())
```

- An aggregate over another table and an aggregate over the same table both work (measured on H2, MariaDB 10.11 and Oracle 23.26). In an `update`, MariaDB does not raise error 1093 either.
- If no row matches, the aggregate is NULL. For a NOT NULL column this is a database error, so wrap it in `?: 0.0` or `Objects.requireNonNullElse(…, 0.0)` to get `coalesce(...)`.

A trailing lambda with one argument (`set { it... }`) and a trailing lambda with two arguments (`set { o, sub -> ... }`) are different overloads. Kotlin picks the right one.

<a id="where-with-paths-and-subqueries"></a>
## Where with paths and subqueries

`where` is the same as in select queries.

```kotlin
qb.update(Order::class.java).set { it.status = 0 }.where { it.customer?.grade!! > 3 }.execute()

qb.update(Order::class.java).set { it.status = 9 }
    .where { o, sub -> !sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
    .execute()
```

Both examples work today.
The first example follows the tested rule above: the path becomes a scalar subquery.
The second example uses `sub`, as in [5. Subqueries](05-subquery.md). Its HQL is `update Order e0 set e0.status = 9 where not exists (select 1 from Item s1 where s1.order.id = e0.id)`.
The same `where` on a delete is tested with Java lambdas (region `dml-subquery`, see [7.4](07-4-delete.md)). The update form has no test of its own yet.

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

If the entity has a `@Version` property, lambdaQL renders `update versioned`. The database adds 1 to the version.

```kotlin
// an entity with a version property (@Version): the version goes up too
val u = qb.update(VOrder::class.java).set { it.status = 2 }.where { it.id == 1L }
// HQL: update versioned VOrder e0 set e0.status = 2 where e0.id = 1L
```

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `update-versioned`:

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

```kotlin
val n = qb.update(VOrder::class.java)
    .set { it.status = 8 }
    .where { it.id == id && it.version == v }
    .execute()
if (n == 0) throw OptimisticLockException()   // checking the result is your job
```

Do not assign the `@Version` property yourself (rule U15).

<a id="many-set-calls-patch"></a>
## Many `set()` calls (PATCH)

You can call `set()` many times. All assignments join into one SET list, in call order (rule U14).
Rules U3 and U4 also apply across `set()` calls.

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

`set()` returns an `UpdateSet`. It has `set`, `where` and `all`, but no `execute()`.

<a id="update-all-rows-all"></a>
## Update all rows: `all()`

An update without `where` must say `all()`. Without `where` or `all()`, the code **does not compile** (rule U5).

```kotlin
qb.update(Order::class.java).set { it.status = 0 }.all().execute()   // OK
qb.update(Order::class.java).set { it.status = 0 }.execute()         // compile error: no execute()
```

An update needs a transaction (see [7.8](07-dml.md#transactions)).

<a id="update-the-first-rows-limit"></a>
## Update the first rows: `limit`

`orderBy { ... }` and `limit(n)` after `where { ... }` or `all()` change at most `n` rows. The target is the first `n` rows of `qb.from(X::class.java).where { same }.orderBy { same }`.

The rules are the same as for delete (see [7.4](07-4-delete.md#delete-the-first-rows-limit)). Without `orderBy`, the order is by the id. The id is always the last sort key. `orderBy` without `limit` is an error.

```kotlin
// a path in the condition: the nested select joins, so the rows are the same as the select
val u = qb.update(Order::class.java)
    .set { it.description = "top" }
    .where { it.customer?.name == "Kim" }
    .orderBy { o, by -> by.desc(o.price) }
    .limit(1)
// HQL: update Order e0 set e0.description = 'top' where e0.id in (select t.id from (select x0.id as id from Order x0 join x0.customer k1 where k1.name = 'Kim' order by x0.price desc, x0.id asc limit 1) t)
```

The same statement is tested in Java in [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) (region `update-limit`).

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `update-limit`:

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

The path `it.customer?.name` becomes a join in the inner select, as in a select (decision D36). The update statement itself reads no other table.

<a id="update-exactly-one-row-executeone"></a>
## Update exactly one row: `executeOne()`

`executeOne()` runs the update with the one-row protection. It reads at most 2 matching ids.

With 2 ids it changes nothing and throws `NonUniqueResultException`. With 1 id it changes that row with `id = :p and (condition)`. The steps are the same as for delete (see [7.4](07-4-delete.md#delete-exactly-one-row-executeone)).

```kotlin
val one = qb.update(Order::class.java)
    .set { it.description = "checked" }
    .where { it.id == id }
    .executeOne()                             // 0 or 1; 2 or more rows: NonUniqueResultException
```

The Java test method `executeOneProtects` (in `DmlQueryTest`, the method of the `execute-one` region) also checks `executeOne()` on an update. When 2 or more rows match, nothing changes.

In a Spring repository, `updateOne({ ... }) { ... }` does the same (see [12.4](12-spring-data-jpa.md#one-row-protection-deleteone-and-updateone)).

---

---

[7. Update, insert, delete (Kotlin)](07-dml.md) | **7.2 Update** | [7.4 Delete](07-4-delete.md) | [7.5 Insert](07-5-insert.md) | [7.6 Upsert](07-6-upsert.md) | [Java](../java/07-2-update.md)
