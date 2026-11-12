<a id="72-update"></a>
# 7.2 Update (Kotlin)

> [!NOTE]
> **Status: designed, not implemented yet.**
> This chapter is a decided design (decision D28, milestone M10).
> The Kotlin syntax compiles (kotlinc 2.3.21), and the SQL was checked on H2, MariaDB and Oracle with Hibernate 6.6.5.
> It does **not** run in lambdaQL today. Today only the analysis core and `where` + `toList()` work
> (tested on H2, MariaDB and Oracle). See the [track README](README.md) for the full status.

[7. Update, insert, delete (Kotlin)](07-dml.md) | **7.2 Update** | [7.4 Delete](07-4-delete.md) | [7.5 Insert](07-5-insert.md) | [Java](../java/07-2-update.md)

---


## Basic form

```kotlin
val n = qb.update(Order::class.java)
    .set { it.status = st; it.price = it.price!! * 1.1 }
    .where { it.status == 1 }
    .execute()                     // number of updated rows
```

HQL:

```sql
update Order o set o.status = :p1, o.price = o.price * 1.1 where o.status = 1
```

**About the SQL blocks.** `…` is the shortened select list (all entity columns), and `?` is a bind parameter. [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java) gives the HQL of this page to Hibernate directly and records the SQL on H2 2.3, MariaDB 10.11 and Oracle 23.26 (Hibernate 6.6.5). lambdaQL does not make these queries yet. The test fails if Hibernate's SQL changes.

**SQL by database (planned)** — Hibernate's translation of the planned HQL, recorded by `PlannedSqlTest`; lambdaQL does not make this query yet. From `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql), example `07-update`:

```sql
-- H2, MariaDB
update orders o1_0
set status=?,price=(o1_0.price*1.1)
where o1_0.status=1

-- Oracle
update orders o1_0
set o1_0.status=?,o1_0.price=(o1_0.price*1.1)
where o1_0.status=1
```

Oracle writes the alias in `set` (`set o1_0.status=?`); H2 and MariaDB do not.

`st` is a captured value. lambdaQL sends it as a bind parameter. `!!` is ignored (D23).

## Values: null, date, boolean

```kotlin
qb.update(Order::class.java)
    .set { it.description = null; it.updateAt = now; it.active = true }
    .where { it.id == id }
    .execute()
```

An enum value works the same way. Each captured value gets **its own parameter**, even when the same value is used twice (rule U7).

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

`+=` needs a non-null property in Kotlin (`cnt: Int`). For a nullable property, write `it.total = it.total!! + 1` or use `?:` as above.

## Embedded fields

```kotlin
qb.update(Order::class.java).set { it.address.city = city }.where { it.id == id }.execute()
// → set o.address.city = :p1

qb.update(Order::class.java).set { it.address = addr }.where { it.id == id }.execute()
// → sets all columns of the embedded value (city, zip)
```

**SQL by database (planned)** — Hibernate's translation of the planned HQL, recorded by `PlannedSqlTest`; lambdaQL does not make this query yet. From `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql), example `07-update-embedded`:

```sql
-- H2, MariaDB
update orders o1_0
set city=?
where o1_0.id=?

-- Oracle
update orders o1_0
set o1_0.city=?
where o1_0.id=?
```

## Change an association (foreign key)

```kotlin
qb.update(Order::class.java).set { it.customer?.id = cid }.where { it.id == id }.execute()
// → set o.customer.id = :p1      (the FK column; the ?. assignment always assigns)

qb.update(Order::class.java).set { it.customer = null }.where { it.id == id }.execute()
// → set o.customer = null

qb.update(Order::class.java).set { it.customer = customer }.where { it.id == id }.execute()
// → binds the @Id of the captured customer (rule U11)
```

**SQL by database (planned)** — Hibernate's translation of the planned HQL, recorded by `PlannedSqlTest`; lambdaQL does not make this query yet. From `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql), example `07-update-fk`:

```sql
-- H2, MariaDB
update orders o1_0
set customer_id=?
where o1_0.id=?

-- Oracle
update orders o1_0
set o1_0.customer_id=?
where o1_0.id=?
```

**SQL by database (planned)** — Hibernate's translation of the planned HQL, recorded by `PlannedSqlTest`; lambdaQL does not make this query yet. From `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql), example `07-update-fk-null`:

```sql
-- H2, MariaDB
update orders o1_0
set customer_id=null
where o1_0.id=?

-- Oracle
update orders o1_0
set o1_0.customer_id=null
where o1_0.id=?
```

You can only set the `@Id` of an associated entity (the FK).
`it.customer?.name = x` is an error: it would change another table.

## A value from another table

Read the value through the association path. lambdaQL renders it as a **correlated scalar subquery** (rule U9).

```kotlin
qb.update(Order::class.java).set { it.custName = it.customer?.name }.all().execute()
```

```sql
update Order o
set o.custName = (select c.name from Customer c where c.id = o.customer.id)
```

**SQL by database (planned)** — Hibernate's translation of the planned HQL, recorded by `PlannedSqlTest`; lambdaQL does not make this query yet. From `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql), example `07-update-other-table`:

```sql
-- H2, MariaDB
update orders o1_0
set cust_name=(select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id)

-- Oracle
update orders o1_0
set o1_0.cust_name=(select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id)
```

If an order has no customer, `custName` becomes NULL.

## An aggregate from a subquery

Use a two-argument lambda. The second argument is the context object (`sub`).
`Agg` is not needed here; the subquery has `sum`, `avg`, ... as in [5. Subqueries](05-subquery.md).

```kotlin
qb.update(Order::class.java)
    .set { o, sub ->
        o.total = sub.from(Item::class.java).where { i -> i.order?.id == o.id }.sum { i -> i.price }
    }
    .where { it.status == 1 }
    .execute()
```

If an order has no items, `total` becomes NULL. This is SQL behavior.

A trailing lambda with one argument (`set { it... }`) and with two arguments (`set { o, sub -> ... }`) are different overloads. Kotlin picks the right one.

## Where with paths and subqueries

`where` is the same as in select queries:

```kotlin
qb.update(Order::class.java).set { it.status = 0 }.where { it.customer?.grade!! > 3 }.execute()

qb.update(Order::class.java).set { it.status = 9 }
    .where { o, sub -> !sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
    .execute()
```

## Optimistic locking (`@Version`)

If the entity has a `@Version` property, lambdaQL renders `update versioned`. The database adds 1 to the version.

```kotlin
val n = qb.update(VOrder::class.java)
    .set { it.status = 8 }
    .where { it.id == id && it.version == v }
    .execute()
if (n == 0) throw OptimisticLockException()   // checking the result is your job
```

Do not assign the `@Version` property yourself. That is an error (rule U15).

## Many `set()` calls (PATCH)

You can call `set()` many times. All assignments join into one SET list, in call order (rule U14).

```kotlin
var u = qb.update(Order::class.java).set { it.status = dto.status }
if (dto.description != null) {
    u = u.set { it.description = dto.description }
}
u.where { it.id == id }.execute()
```

## Update all rows: `all()`

An update without `where` must say `all()`. Without `where` or `all()`, the code **does not compile** (rule U5).

```kotlin
qb.update(Order::class.java).set { it.status = 0 }.all().execute()   // OK
qb.update(Order::class.java).set { it.status = 0 }.execute()         // compile error: no execute()
```

---

---

[7. Update, insert, delete (Kotlin)](07-dml.md) | **7.2 Update** | [7.4 Delete](07-4-delete.md) | [7.5 Insert](07-5-insert.md) | [Java](../java/07-2-update.md)
