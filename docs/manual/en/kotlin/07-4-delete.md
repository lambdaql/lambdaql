<a id="74-delete"></a>
# 7.4 Delete (Kotlin)

[7. Update, insert, delete (Kotlin)](07-dml.md) | [7.2 Update](07-2-update.md) | **7.4 Delete** | [7.5 Insert](07-5-insert.md) | [7.6 Upsert](07-6-upsert.md) | [Java](../java/07-4-delete.md)

---


```kotlin
val d = qb.delete(Order::class.java).where { it.status == 3 }
// HQL: delete from Order e0 where e0.status = 3
val deleted = d.execute()

// a path: the same rows as qb.from(Order::class.java).where { same lambda } - orders without a customer stay
val byName = qb.delete(Order::class.java).where { it.customer?.name == null }
// HQL: delete from Order e0 where (select s1.name from Customer s1 where s1.id = fk(e0.customer)) is null and e0.customer.id is not null
```

**About the SQL blocks.** `…` is the shortened select list (all entity columns), and `?` is a bind parameter. The lines come from [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java). It runs each query on H2 2.3, MariaDB 10.11 and Oracle 23.26 with Hibernate 7.4, and it fails if Hibernate's SQL changes. The test uses Java lambdas. A Kotlin lambda with the same meaning gives the same HQL, so it gets the same SQL.

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `delete`:

```sql
-- H2, Oracle
delete from orders o1_0
where o1_0.status=3

-- MariaDB
delete o1_0 from orders o1_0
where o1_0.status=3
```

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `delete#2`:

```sql
-- H2, Oracle
delete from orders o1_0
where (select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id) is null and o1_0.customer_id is not null

-- MariaDB
delete from orders
where (select c1_0.name from customers c1_0 where c1_0.id=orders.customer_id) is null and customer_id is not null
```

MariaDB writes `delete o1_0 from orders o1_0`. H2 and Oracle write `delete from orders o1_0`.

**(tested)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt), region `insert-delete`:

```kotlin
val i = qb.insert(Archive::class.java).values { it.id = 100L; it.price = 9.5; it.customerName = "Kim" }
assertEquals(1, i.execute())
val d = qb.delete(Order::class.java).where { o, sub -> !sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
assertEquals("delete from Order e0 where not exists (select 1 from Item s1 where s1.order.id = e0.id)", d.toHql().hql())
```

The test first inserts one `Archive` row. Then it deletes the orders without items and compares the rows left with the result of the same condition in Kotlin.
More statements written in Java are tested in [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) (H2, MariaDB and Oracle). See [the Java version of this page](../java/07-4-delete.md) for those examples.

The second statement deletes **the same rows** that `qb.from(Order::class.java).where { it.customer?.name == null }` returns: orders whose customer has no name. The Java test compares the deleted rows with this select.
Orders **without** a customer are not deleted (rule U9, decision D36). A null guard (`it.customer == null || ...`) does not change this, because the path is an inner join in the select too (decision D48).
To delete orders without a customer, run one more delete with the condition `it.customer == null`.

To delete all rows, `all()` is required (rule U5).

```kotlin
qb.delete(Order::class.java).all().execute()     // delete all rows
qb.delete(Order::class.java).execute()           // compile error: no execute()
```

Delete by a list of ids (captured collection, decision D29):

```kotlin
qb.delete(Item::class.java).where { it.id in ids }.execute()
```

A subquery in `where` uses a second lambda argument `sub`, as in [5. Subqueries](05-subquery.md).
An example that deletes the orders that have no items:

```kotlin
qb.delete(Order::class.java)
    .where { o, sub -> !sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
    .execute()
// HQL: delete from Order e0 where not exists (select 1 from Item s1 where s1.order.id = e0.id)
```

The same statement is tested in Java in [`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java) (region `dml-subquery`).

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `dml-subquery`:

```sql
-- H2, Oracle
delete from orders o1_0
where not exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id)

-- MariaDB
delete from orders
where not exists(select 1 from items i1_0 where i1_0.order_id=orders.id)
```

Like every JPA bulk statement, a delete does not run JPA `cascade` or `orphanRemoval`, and it does not change entities already loaded (rule U16).
A delete needs a transaction (see [7.8](07-dml.md#transactions)).

<a id="delete-with-a-subquery-on-the-same-table"></a>
## Subquery that reads the same table

A subquery in `where` can read the entity being deleted (or an entity in the same inheritance hierarchy). This covers the subquery's own `from`, a join inside the subquery, and the correlated scalar subquery of U9.
In this case lambdaQL first reads the target ids as a derived table and then deletes by those ids (rule U17).

```
delete from Order d0 where d0.id in (select t.id from (select e0.id as id from Order e0 where <condition>) t)
```

- Reason: when a subquery reads the target table, MariaDB rejects Hibernate's `delete t from …` (error 1093, "specified twice").
- The same form works for a correlated subquery. Wrapping only the subquery does not work for a correlated subquery.
- Measured on H2, MariaDB 10.11 and Oracle 23.26 (uncorrelated `avg`, correlated `exists`, `max`, a join inside the subquery). The three databases delete the same number of rows, and MariaDB does not fail either.
- A subquery that reads another table keeps the usual form (the `not exists` example above).
- `update` is not wrapped. An `update` with a subquery on its own table works on all three databases (measured on MariaDB 10.11).
- An entity with a composite id is not wrapped. A delete with `limit` already uses the wrapped form below (alias `x0`).

**(tested)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt), region `delete-self-subquery`:

```kotlin
// the subquery reads the target table: the matching ids are read through a derived table first (U17, MariaDB 1093)
val d = qb.delete(Order::class.java).where { o, sub -> o.price < sub.from(Order::class.java).avg { x -> x.price } }
assertEquals("delete from Order d0 where d0.id in (select t.id from (select e0.id as id from Order e0"
        + " where e0.price < (select avg(s1.price) from Order s1)) t)", d.toHql().hql())
```

<a id="delete-the-first-rows-limit"></a>
## Delete the first rows: `limit`

`orderBy { ... }` and `limit(n)` after `where { ... }` or `all()` delete at most `n` rows. The target is the first `n` rows of `qb.from(X::class.java).where { same }.orderBy { same }`.
Use it to work through many rows in small steps.

- Without `orderBy`, the order is by the id.
- lambdaQL adds the id as the last sort key, so the order is always the same (rule S9, see [Tie-breaker](04-order-paging.md#tie-breaker-automatic-id)).
- `orderBy` takes a sort lambda or a `SortSpec`, as in a select (see [4. Sorting and paging](04-order-paging.md)).
- `orderBy` without `limit` is an error (`IllegalStateException`), because the order has no use then.

```kotlin
// the first rows of qb.from(Order::class.java).where { same }.orderBy { same }; the id is the last sort key
val d = qb.delete(Order::class.java)
    .where { it.status == 1 }
    .orderBy { o, by -> by.asc(o.price) }
    .limit(1)
// HQL: delete from Order e0 where e0.id in (select t.id from (select x0.id as id from Order x0 where x0.status = 1 order by x0.price asc, x0.id asc limit 1) t)
val deleted = d.execute()
```

The same statement is tested in Java in [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) (region `delete-limit`).

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `delete-limit`:

```sql
-- H2
delete from orders o1_0
where o1_0.id in (select t1_0.c0 from (select o2_0.id from orders o2_0 where o2_0.status=1 order by o2_0.price,o2_0.id fetch first 1 rows only) t1_0(c0))

-- MariaDB
delete o1_0 from orders o1_0
where o1_0.id in (select * from (select t1_0.c0 from (select * from (select o2_0.id c0 from orders o2_0 where o2_0.status=1 order by o2_0.price,o2_0.id limit 1) _sub_) t1_0) _sub_)

-- Oracle
delete from orders o1_0
where o1_0.id in (select t1_0.c0 from (select o2_0.id c0 from orders o2_0 where o2_0.status=1 order by o2_0.price,o2_0.id fetch first 1 rows only) t1_0)
```

lambdaQL renders the limit as `e0.id in (select t.id from (select x0.id as id from X x0 ... order by ... limit n) t)`.

- The inner select follows the select rules, joins included (decision D36). So the statement deletes exactly the rows that the select returns.
- The derived table `t` is there for MariaDB. Without it, MariaDB rejects `limit` in an `in` subquery (error 1235) and a subquery that reads the target table (error 1093).
- H2 and Oracle write `fetch first 1 rows only`. MariaDB writes `limit 1`.

<a id="delete-exactly-one-row-executeone"></a>
## Delete exactly one row: `executeOne()`

`executeOne()` runs the delete with the **one-row protection**. Use it instead of `execute()` when the condition should match one row, for example a unique key.

1. lambdaQL reads the ids of the matching rows, at most 2.
2. With no id, nothing changes and the result is 0.
3. With 2 ids, nothing changes and `executeOne()` throws JPA's `NonUniqueResultException`.
4. With 1 id, lambdaQL deletes that row with `id = :p and (condition)` and returns 1. If the row stopped matching in the meantime, the result is 0.

The statement names one id, so it never changes more than one row. The protection does not rely on a rollback.

```kotlin
// one row matches: it is deleted
val one = qb.delete(Order::class.java).where { it.id == id }.executeOne()
// two or more rows match: nothing is deleted, NonUniqueResultException
qb.delete(Order::class.java).where { it.price!! > 100.0 }.executeOne()
```

The same statement is tested in Java in [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) (region `execute-one`). The id can never be NULL, so `it.id == id` becomes a plain `e0.id = :p1` (see [3.6.2](03-where.md#362--compares-values)).

- `executeOne()` together with `orderBy` or `limit` is an error (`IllegalStateException`).
- `limit` and `executeOne()` do not support entities with a composite key yet.
- In a Spring repository, `deleteOne { ... }` does the same and throws `IncorrectResultSizeDataAccessException` (see [12.4](12-spring-data-jpa.md#one-row-protection-deleteone-and-updateone)).

---

---

[7. Update, insert, delete (Kotlin)](07-dml.md) | [7.2 Update](07-2-update.md) | **7.4 Delete** | [7.5 Insert](07-5-insert.md) | [7.6 Upsert](07-6-upsert.md) | [Java](../java/07-4-delete.md)
