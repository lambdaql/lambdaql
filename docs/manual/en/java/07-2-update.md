<a id="72-update"></a>
# 7.2 Update (Java)

> [!NOTE]
> **Status: designed, not implemented yet.**
> This chapter is a decided design (decision D28, milestone M10).
> The syntax compiles in Java, and the SQL was checked on H2, MariaDB and Oracle with Hibernate 6.6.5.
> It does **not** run in lambdaQL today. Today only the analysis core and `where` + `toList()` work
> (tested on H2, MariaDB and Oracle). See the [Java track README](README.md#status) for the full status.

[7. Update, insert, delete (Java)](07-dml.md) | **7.2 Update** | [7.4 Delete](07-4-delete.md) | [7.5 Insert](07-5-insert.md) | [Kotlin](../kotlin/07-2-update.md)

---


## Basic form

```java
int n = qb.update(Order.class)
    .set(o -> { o.setStatus(st); o.setPrice(o.getPrice() * 1.1); })
    .where(o -> o.getStatus() == 1)
    .execute();                    // number of updated rows
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

`st` is a captured variable. lambdaQL sends it as a bind parameter.

## Values: null, date, boolean

```java
qb.update(Order.class)
  .set(o -> { o.setDescription(null); o.setUpdateAt(now); o.setActive(true); })
  .where(o -> o.getId() == id)
  .execute();
```

An enum value works the same way. Each captured value gets **its own parameter**, even when the same value is used twice (rule U7).

## Add to a value, default for NULL

```java
qb.update(Order.class).set(o -> o.setCnt(o.getCnt() + 1)).where(o -> o.getStatus() == 1).execute();

qb.update(Order.class)
  .set(o -> o.setPrice(Objects.requireNonNullElse(o.getPrice(), 0.0) + 1))
  .all()
  .execute();
// → set o.price = coalesce(o.price, 0.0) + 1
```

**SQL by database (planned)** — Hibernate's translation of the planned HQL, recorded by `PlannedSqlTest`; lambdaQL does not make this query yet. From `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql), example `07-update-coalesce`:

```sql
-- H2, MariaDB
update orders o1_0
set price=(coalesce(o1_0.price,0.0)+1)

-- Oracle
update orders o1_0
set o1_0.price=(coalesce(o1_0.price,0.0)+1)
```

## Embedded fields

```java
qb.update(Order.class).set(o -> o.getAddress().setCity(city)).where(o -> o.getId() == id).execute();
// → set o.address.city = :p1

qb.update(Order.class).set(o -> o.setAddress(addr)).where(o -> o.getId() == id).execute();
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

```java
qb.update(Order.class).set(o -> o.getCustomer().setId(cid)).where(o -> o.getId() == id).execute();
// → set o.customer.id = :p1      (the FK column)

qb.update(Order.class).set(o -> o.setCustomer(null)).where(o -> o.getId() == id).execute();
// → set o.customer = null

qb.update(Order.class).set(o -> o.setCustomer(customer)).where(o -> o.getId() == id).execute();
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
`o.getCustomer().setName(x)` is an error: it would change another table.

## A value from another table

Read the value through the association path. lambdaQL renders it as a **correlated scalar subquery** (rule U9).

```java
qb.update(Order.class).set(o -> o.setCustName(o.getCustomer().getName())).all().execute();
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

```java
qb.update(Order.class)
  .set((o, sub) -> o.setTotal(
      sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).sum(i -> i.getPrice())))
  .where(o -> o.getStatus() == 1)
  .execute();
```

If an order has no items, `total` becomes NULL. This is SQL behavior.

## Where with paths and subqueries

`where` is the same as in select queries:

```java
qb.update(Order.class).set(o -> o.setStatus(0)).where(o -> o.getCustomer().getGrade() > 3).execute();

qb.update(Order.class).set(o -> o.setStatus(9))
  .where((o, sub) -> !sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).exists())
  .execute();
```

## Optimistic locking (`@Version`)

If the entity has a `@Version` field, lambdaQL renders `update versioned`. The database adds 1 to the version.

```java
int n = qb.update(VOrder.class)
    .set(o -> o.setStatus(8))
    .where(o -> o.getId() == id && o.getVersion() == v)
    .execute();
if (n == 0) throw new OptimisticLockException();   // checking the result is your job
```

Do not assign the `@Version` field yourself. That is an error (rule U15).

## Many `set()` calls (PATCH)

You can call `set()` many times. All assignments join into one SET list, in call order (rule U14).

```java
var u = qb.update(Order.class).set(o -> o.setStatus(dto.status()));
if (dto.description() != null) {
    u = u.set(o -> o.setDescription(dto.description()));
}
u.where(o -> o.getId() == id).execute();
```

## Update all rows: `all()`

An update without `where` must say `all()`. Without `where` or `all()`, the code **does not compile** (rule U5).

```java
qb.update(Order.class).set(o -> o.setStatus(0)).all().execute();       // OK
qb.update(Order.class).set(o -> o.setStatus(0)).execute();             // compile error: no execute()
```

---

---

[7. Update, insert, delete (Java)](07-dml.md) | **7.2 Update** | [7.4 Delete](07-4-delete.md) | [7.5 Insert](07-5-insert.md) | [Kotlin](../kotlin/07-2-update.md)
