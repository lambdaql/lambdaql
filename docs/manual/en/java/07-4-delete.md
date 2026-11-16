<a id="74-delete"></a>
# 7.4 Delete (Java)

> [!NOTE]
> **Status: implemented and tested** on H2 2.3, MariaDB 10.11 and Oracle 23.26 (milestone M10), including `limit` and `executeOne()` (decision D41).
> **Not implemented yet:** `sub` subqueries in `where` (milestone M8), and `limit` / `executeOne()` on entities with a composite key.
> Examples marked **(tested)** are copied from [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java). The other examples follow the design and are not covered by a test yet.
> See the [Java track README](README.md#status) for the full status.

[7. Update, insert, delete (Java)](07-dml.md) | [7.2 Update](07-2-update.md) | **7.4 Delete** | [7.5 Insert](07-5-insert.md) | [7.6 Upsert](07-6-upsert.md) | [Kotlin](../kotlin/07-4-delete.md)

---


**(tested)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java), region `delete`:

```java
ExecutableDelete<Order> d = qb.delete(Order.class).where(o -> o.getStatus() == 3);
assertEquals("delete from Order e0 where e0.status = 3", d.toHql().hql());
int deleted = d.execute();
// a path: the same rows as qb.from(Order.class).where(same lambda) - orders without a customer stay
ExecutableDelete<Order> byName = qb.delete(Order.class).where(o -> o.getCustomer().getName() == null);
assertEquals("delete from Order e0 where (select s1.name from Customer s1 where s1.id = e0.customer.id) is null"
        + " and e0.customer.id is not null", byName.toHql().hql());
```

**About the SQL blocks.** `…` is the shortened select list (all entity columns), and `?` is a bind parameter. The lines come from [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java). It runs each query on H2 2.3, MariaDB 10.11 and Oracle 23.26 with Hibernate 6.6.5, and it fails if Hibernate's SQL changes. Blocks marked **(planned)** come from [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java) instead. It gives the planned HQL to Hibernate directly, because lambdaQL does not make these statements yet.

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `delete`:

```sql
-- H2, Oracle
delete from orders o1_0
where o1_0.status=3

-- MariaDB
delete o1_0 from orders o1_0
where o1_0.status=3
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `delete#2`:

```sql
-- H2, Oracle
delete from orders o1_0
where (select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id) is null and o1_0.customer_id is not null

-- MariaDB
delete o1_0 from orders o1_0
where (select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id) is null and o1_0.customer_id is not null
```

MariaDB writes `delete o1_0 from orders o1_0`. H2 and Oracle write `delete from orders o1_0`.

The second statement deletes **the same rows** that `qb.from(Order.class).where(o -> o.getCustomer().getName() == null)` returns: orders whose customer has no name. The test compares the deleted rows with this select.
Orders **without** a customer are not deleted (rule U9, decision D36). To delete them too, write `o.getCustomer() == null || o.getCustomer().getName() == null`. With this null guard, lambdaQL does not add `e0.customer.id is not null`.

Delete all rows: `all()` is required (rule U5).

```java
qb.delete(Order.class).all().execute();     // delete all rows
qb.delete(Order.class).execute();           // compile error: no execute()
```

Delete by a list of ids (captured collection, decision D29):

```java
qb.delete(Item.class).where(i -> ids.contains(i.getId())).execute();
```

A subquery in `where` needs `sub` (not implemented yet, milestone M8):

```java
qb.delete(Order.class)
  .where((o, sub) -> !sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).exists())
  .execute();
```

**SQL by database (planned)** — Hibernate's translation of the planned HQL, recorded by `PlannedSqlTest`; lambdaQL does not make this query yet. From `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql), example `07-delete-not-exists`:

```sql
-- H2, Oracle
delete from orders o1_0
where not exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id)

-- MariaDB
delete o1_0 from orders o1_0
where not exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id)
```

Like every JPA bulk statement, a delete does not run JPA `cascade` or `orphanRemoval`, and it does not change entities already loaded (rule U16).
A delete needs a transaction (see [7.8](07-dml.md#transactions)).

## Delete the first rows: `limit`

`orderBy(...)` and `limit(n)` after `where(...)` or `all()` delete at most `n` rows: the first `n` rows of `qb.from(X.class).where(same).orderBy(same)`.
Use it to work through many rows in small steps.

- Without `orderBy`, the order is by the id.
- lambdaQL adds the id as the last sort key, so the order is always the same (rule S9, see [Tie-breaker](04-order-paging.md#tie-breaker-automatic-id)).
- `orderBy` takes a sort lambda or a `SortSpec`, as in a select (see [4. Sorting and paging](04-order-paging.md)).
- `orderBy` without `limit` is an error (`IllegalStateException`), because the order has no use then.

**(tested)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java), region `delete-limit`:

```java
// the first rows of qb.from(Order.class).where(same).orderBy(same); the id is the last sort key
ExecutableDelete<Order> d = qb.delete(Order.class)
        .where(o -> o.getStatus() == 1)
        .orderBy((o, by) -> by.asc(o.getPrice()))
        .limit(1);
assertEquals("delete from Order e0 where e0.id in (select t.id from (select x0.id as id from Order x0"
        + " where x0.status = 1 order by x0.price asc, x0.id asc limit 1) t)", d.toHql().hql());
int deleted = d.execute();
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `delete-limit`:

```sql
-- H2
delete from orders o1_0
where o1_0.id in (select t1_0.id from (select o2_0.id from orders o2_0 where o2_0.status=1 order by o2_0.price,o2_0.id fetch first 1 rows only) t1_0(id))

-- MariaDB
delete o1_0 from orders o1_0
where o1_0.id in (select t1_0.id from (select o2_0.id id from orders o2_0 where o2_0.status=1 order by o2_0.price,o2_0.id limit 1) t1_0)

-- Oracle
delete from orders o1_0
where o1_0.id in (select t1_0.id from (select o2_0.id id from orders o2_0 where o2_0.status=1 order by o2_0.price,o2_0.id fetch first 1 rows only) t1_0)
```

lambdaQL writes the limit as `e0.id in (select t.id from (select x0.id as id from X x0 ... order by ... limit n) t)`:

- The inner select follows the select rules, joins included (decision D36). So the statement deletes exactly the rows that the select returns.
- The derived table `t` is there for MariaDB. Without it, MariaDB rejects `limit` in an `in` subquery (error 1235) and a subquery that reads the target table (error 1093).
- H2 and Oracle write `fetch first 1 rows only`, MariaDB writes `limit 1`.

## Delete exactly one row: `executeOne()`

`executeOne()` runs the delete with the **one-row protection**. Use it instead of `execute()` when the condition should match one row, for example a unique key.

1. lambdaQL reads the ids of the rows that match, at most 2.
2. No id: nothing changes. The result is 0.
3. 2 ids: nothing changes. `executeOne()` throws JPA's `NonUniqueResultException`.
4. 1 id: lambdaQL deletes that row with `id = :p and (your condition)`. The result is 1, or 0 if the row stopped matching in the meantime.

The statement names one id, so it never changes more than one row. The protection does not rely on a rollback.

**(tested)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java), region `execute-one`:

```java
// one row matches: it is deleted
int one = qb.delete(Order.class).where(o -> o.getId() == id).executeOne();
// two or more rows match: nothing is deleted
assertThrows(jakarta.persistence.NonUniqueResultException.class,
        () -> qb.delete(Order.class).where(o -> o.getPrice() > 100).executeOne());
```

- `executeOne()` together with `orderBy` or `limit` is an error (`IllegalStateException`).
- `limit` and `executeOne()` do not support entities with a composite key yet.
- In a Spring repository, `deleteOne(where)` does the same and throws `IncorrectResultSizeDataAccessException` (see [12.4](12-spring-data-jpa.md#one-row-protection-deleteone-and-updateone)).

---

---

[7. Update, insert, delete (Java)](07-dml.md) | [7.2 Update](07-2-update.md) | **7.4 Delete** | [7.5 Insert](07-5-insert.md) | [7.6 Upsert](07-6-upsert.md) | [Kotlin](../kotlin/07-4-delete.md)
