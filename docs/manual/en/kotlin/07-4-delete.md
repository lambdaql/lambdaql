<a id="74-delete"></a>
# 7.4 Delete (Kotlin)

[7. Update, insert, delete (Kotlin)](07-dml.md) | [7.2 Update](07-2-update.md) | **7.4 Delete** | [7.5 Insert](07-5-insert.md) | [7.6 Upsert](07-6-upsert.md) | [Java](../java/07-4-delete.md)

---

<!-- region: ManualKotlinDmlTest.kt#delete -->
```kotlin
val d = qb.delete(Order::class.java).where { it.status.toInt() == 3 }
assertEquals("delete from Order e0 where e0.status = 3", d.toHql().hql())
val deleted = d.execute()

// a path: the same rows as qb.from(Order::class.java).where { same lambda } - with !! orders without a customer stay
val byName = qb.delete(Order::class.java).where { it.customer!!.name == null }
assertEquals("delete from Order e0 where (select s1.name from Customer s1 where s1.id = fk(e0.customer)) is null"
    + " and e0.customer.id is not null", byName.toHql().hql())
```

**SQL by database**

<!-- sql: delete -->
```sql
-- H2, Oracle
delete from orders o1_0
where o1_0.status=3

-- MariaDB
delete o1_0 from orders o1_0
where o1_0.status=3
```

**SQL by database**

<!-- sql: delete#2 -->
```sql
-- H2, Oracle
delete from orders o1_0
where (select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id) is null and o1_0.customer_id is not null

-- MariaDB
delete from orders
where (select c1_0.name from customers c1_0 where c1_0.id=orders.customer_id) is null and customer_id is not null
```

MariaDB writes `delete o1_0 from orders o1_0`. H2 and Oracle write `delete from orders o1_0`.

The second statement deletes **the same rows** that `qb.from(Order::class.java).where { it.customer!!.name == null }` returns, that is, orders whose customer has no name.
Orders **without** a customer are not deleted, because a comparison that passes through `!!` is true only for orders that have a customer.

If you write `?.` (`it.customer?.name == null`), orders without a customer also match the condition, so the select is a `left join`, and DML does not add `e0.customer.id is not null`. So orders without a customer are deleted too.
<!-- region: ManualKotlinDmlTest.kt#delete-safe-call -->
```kotlin
// ?. : no "customer exists" check, so orders without a customer are deleted too, as the select returns them
val d = qb.delete(Order::class.java).where { it.customer?.name == null }
assertEquals("delete from Order e0 where (select s1.name from Customer s1 where s1.id = fk(e0.customer)) is null",
    d.toHql().hql())
```

For an UPDATE example of the same kind, see [7.2](07-2-update.md). To delete only orders without a customer, write `it.customer == null`.

To delete all rows, you need `all()`.
To delete by a list of ids, use a captured collection.

<!-- region: ManualKotlinDmlTest.kt#delete-all -->
```kotlin
// ids in a captured collection
val byIds = qb.delete(Item::class.java).where { it.id in ids }
assertEquals("delete from Item e0 where e0.id in (:p1)", byIds.toHql().hql())
byIds.execute()
// every row: all() is required
val all = qb.delete(Order::class.java).all()
assertEquals("delete from Order e0", all.toHql().hql())
all.execute()
```

A subquery in `where` uses `sub`, the second lambda argument, as in [5. Subqueries](05-subquery.md).
An example that deletes orders without items:

<!-- region: ManualKotlinDmlTest.kt#delete-not-exists -->
```kotlin
// orders without items: the subquery starts with the second lambda parameter sub
val d = qb.delete(Order::class.java)
    .where { o, sub -> !sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
assertEquals("delete from Order e0 where not exists (select 1 from Item s1 where s1.order.id = e0.id)", d.toHql().hql())
val deleted = d.execute()
```

**SQL by database**

<!-- sql: dml-subquery -->
```sql
-- H2, Oracle
delete from orders o1_0
where not exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id)

-- MariaDB
delete from orders
where not exists(select 1 from items i1_0 where i1_0.order_id=orders.id)
```

Like all JPA bulk statements, a delete does not run JPA `cascade` or `orphanRemoval`, and it is not reflected in entities that are already loaded.
A delete needs a transaction (see [7.8](07-dml.md#transactions)).

Example code: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt) (`delete`, `delete-safe-call`, `delete-all`, `delete-not-exists`).

<a id="delete-with-a-subquery-on-the-same-table"></a>
## Subquery that reads the same table

A subquery in `where` can read the entity being deleted (or an entity in the same inheritance hierarchy). This covers the subquery's own `from`, a join inside the subquery, and the correlated scalar subquery of U9.
In this case, lambdaQL first reads the target ids through a derived table, and then deletes by those ids.

```
delete from Order d0 where d0.id in (select t.id from (select e0.id as id from Order e0 where <condition>) t)
```

- Why: if a subquery reads the target table, MariaDB rejects Hibernate's `delete t from …` (error 1093, "specified twice").
- The same form works for correlated subqueries. Wrapping only the subquery cannot be used for correlated subqueries.
- Measured on H2, MariaDB 10.11 and Oracle 23.26 (uncorrelated `avg`, correlated `exists`, `max`, a join inside the subquery). The number of deleted rows is the same on the three databases, and MariaDB does not fail either.
- A subquery that reads another table keeps the usual form (the `not exists` example above).
- `update` is not wrapped. An `update` with a subquery that reads its own table works on all three databases (measured on MariaDB 10.11).
- Entities with a composite id are not wrapped. A delete with `limit` already uses the wrapped form below (alias `x0`).

<!-- region: KotlinQueryTest.kt#delete-self-subquery -->
```kotlin
// the subquery reads the target table: the matching ids are read through a derived table first (U17, MariaDB 1093)
val d = qb.delete(Order::class.java).where { o, sub -> o.price < sub.from(Order::class.java).avg { x -> x.price } }
assertEquals("delete from Order d0 where d0.id in (select t.id from (select e0.id as id from Order e0"
        + " where e0.price < (select avg(s1.price) from Order s1)) t)", d.toHql().hql())
```

Example code: [`KotlinQueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt) (`delete-self-subquery`).

<a id="delete-the-first-rows-limit"></a>
## Delete the first rows: `limit`

If you add `orderBy { ... }` and `limit(n)` after `where { ... }` or `all()`, at most `n` rows are deleted. The targets are the first `n` rows of `qb.from(X::class.java).where { same }.orderBy { same }`.
Use it to process many rows in small batches.

- Without `orderBy`, it is id order.
- lambdaQL adds the id as the last sort key, so the order is always the same (see [Tie-breaker](04-order-paging.md#tie-breaker-automatic-id)).
- In `orderBy`, give a sort lambda or a `SortSpec`, the same as in a select (see [4. Sorting and paging](04-order-paging.md)).
- `orderBy` without `limit` is an error (`IllegalStateException`), because the order does nothing in that case.

<!-- region: ManualKotlinDmlTest.kt#delete-limit -->
```kotlin
// the first rows of qb.from(Order::class.java).where { same }.orderBy { same }; the id is the last sort key
val d = qb.delete(Order::class.java)
    .where { it.status.toInt() == 1 }
    .orderBy { o -> asc(o.price) }
    .limit(1)
assertEquals("delete from Order e0 where e0.id in (select t.id from (select x0.id as id from Order x0"
    + " where x0.status = 1 order by x0.price asc, x0.id asc limit 1) t)", d.toHql().hql())
val deleted = d.execute()
```

**SQL by database**

<!-- sql: delete-limit -->
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

- The inner select follows the select rules, including joins. So this statement deletes exactly the rows that the select returns.
- The derived table `t` is for MariaDB. Without it, MariaDB rejects `limit` in an `in` subquery (error 1235) and a subquery that reads the target table (error 1093).
- H2 and Oracle write `fetch first 1 rows only`, and MariaDB writes `limit 1`.

Example code: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt) (`delete-limit`).

<a id="delete-exactly-one-row-executeone"></a>
## Delete exactly one row: `executeOne()`

`executeOne()` runs the delete with **one-row protection**. Use it instead of `execute()` when the condition must match one row (for example, a unique key).

1. lambdaQL reads at most 2 ids of the rows that match the condition.
2. If there is no id, it changes nothing and returns 0.
3. If there are 2 ids, it changes nothing and throws JPA `NonUniqueResultException`.
4. If there is 1 id, it deletes that row with `id = :p and (condition)` and returns 1. If the row no longer matches the condition by then, it returns 0.

The statement names one id, so it never changes two or more rows. This protection does not depend on a rollback.

<!-- region: ManualKotlinDmlTest.kt#execute-one -->
```kotlin
// one row matches: it is deleted
val one = qb.delete(Order::class.java).where { it.id == id }.executeOne()
// two or more rows match: nothing is deleted
assertThrows(NonUniqueResultException::class.java) {
    qb.delete(Order::class.java).where { it.price > 100.0 }.executeOne()
}
```

The second statement deletes nothing, because more than one order has a price greater than 100.
An id can never be NULL, so `it.id == id` becomes a simple `e0.id = :p1` (see [3.6.2](03-where.md#362--compares-values)).

- Using `executeOne()` with `orderBy` or `limit` is an error (`IllegalStateException`).
- `limit` and `executeOne()` do not support entities with a composite key yet.
- In a Spring repository, `deleteOne { ... }` does the same and throws `IncorrectResultSizeDataAccessException` (see [12.4](12-spring-data-jpa.md#one-row-protection-deleteone-and-updateone)).

---

[7. Update, insert, delete (Kotlin)](07-dml.md) | [7.2 Update](07-2-update.md) | **7.4 Delete** | [7.5 Insert](07-5-insert.md) | [7.6 Upsert](07-6-upsert.md) | [Java](../java/07-4-delete.md)

Example code: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt) (`execute-one`).
