<a id="74-delete"></a>
# 7.4 Delete (Java)

[7. Update, insert, delete (Java)](07-dml.md) | [7.2 Update](07-2-update.md) | **7.4 Delete** | [7.5 Insert](07-5-insert.md) | [7.6 Upsert](07-6-upsert.md) | [Kotlin](../kotlin/07-4-delete.md)

---

<!-- region: DmlQueryTest.java#delete -->
```java
ExecutableDelete<Order> d = qb.delete(Order.class).where(o -> o.getStatus() == 3);
assertEquals("delete from Order e0 where e0.status = 3", d.toHql().hql());
int deleted = d.execute();
// a path: the same rows as qb.from(Order.class).where(same lambda) - orders without a customer stay
ExecutableDelete<Order> byName = qb.delete(Order.class).where(o -> o.getCustomer().getName() == null);
assertEquals("delete from Order e0 where (select s1.name from Customer s1 where s1.id = fk(e0.customer)) is null"
        + " and e0.customer.id is not null", byName.toHql().hql());
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

The second statement deletes **the same rows** that `qb.from(Order.class).where(o -> o.getCustomer().getName() == null)` returns, that is, orders whose customer has no name.
Orders **without** a customer do not match this condition, so they are not deleted. In select, this path is also an inner join.
To also delete orders without a customer, add a null guard (`o.getCustomer() == null || ...`). As in select, orders without a customer are then also targeted.

To delete all rows, you need `all()`.

<!-- region: ManualJavaDmlTest.java#delete-all -->
```java
ExecutableDelete<Order> d = qb.delete(Order.class).all();
assertEquals("delete from Order e0", d.toHql().hql());
int deleted = d.execute();
```

Delete by a list of ids (a captured collection):

<!-- region: ManualJavaDmlTest.java#delete-in -->
```java
List<Long> ids = all;                               // ids read before, for example from a request
ExecutableDelete<Item> d = qb.delete(Item.class).where(i -> ids.contains(i.getId()));
assertEquals("delete from Item e0 where e0.id in (:p1)", d.toHql().hql());
int deleted = d.execute();
```

A subquery in `where` uses `sub`, the second lambda expression argument, as in [5. Subqueries](05-subquery.md).
An example that deletes orders without items:

<!-- region: SubqueryTest.java#dml-subquery -->
```java
ExecutableDelete<Order> d = qb.delete(Order.class)
        .where((o, sub) -> !sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).exists());
assertEquals("delete from Order e0 where not exists (select 1 from Item s1 where s1.order.id = e0.id)", d.toHql().hql());
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

Like every JPA bulk statement, delete does not run JPA `cascade` or `orphanRemoval`, and it is not reflected in entities that are already loaded.
A delete needs a transaction (see [7.8](07-dml.md#transactions)).

Example code: [`DmlQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) (`delete`), [`ManualJavaDmlTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaDmlTest.java) (`delete-all`, `delete-in`), [`SubqueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java) (`dml-subquery`).

<a id="delete-with-a-subquery-on-the-same-table"></a>
## A subquery that reads the same table

A subquery in `where` can read the entity being deleted (or an entity of the same inheritance hierarchy). This covers the subquery's own `from`, joins inside the subquery, and the correlated scalar subqueries of U9.
In this case lambdaQL first reads the target ids through a derived table and deletes by those ids.

```
delete from Order d0 where d0.id in (select t.id from (select e0.id as id from Order e0 where <condition>) t)
```

- Why: when a subquery reads the target table, MariaDB rejects Hibernate's `delete t from …` (error 1093, "specified twice").
- The same form also works for correlated subqueries. Wrapping only the subquery does not work for correlated subqueries.
- Measured on H2, MariaDB 10.11 and Oracle 23.26 (non-correlated `avg`, correlated `exists`, `max`, joins inside a subquery). The three databases deleted the same number of rows, and MariaDB does not fail either.
- A subquery that reads another table keeps the existing form (the `not exists` example above).
- `update` is not wrapped. An `update` with a subquery that reads its own table works on all three databases (measured on MariaDB 10.11).
- Entities with composite ids are not wrapped. A delete with `limit` already uses the wrapped form below (alias `x0`).

<!-- region: DmlQueryTest.java#delete-self-subquery -->
```java
// the subquery reads the target table: lambdaQL reads the matching ids through a derived table first,
// because MariaDB cannot delete from a table that a subquery reads (error 1093)
ExecutableDelete<Order> d = qb.delete(Order.class)
        .where((o, sub) -> o.getPrice() < sub.from(Order.class).avg(x -> x.getPrice()));
assertEquals("delete from Order d0 where d0.id in (select t.id from (select e0.id as id from Order e0"
        + " where e0.price < (select avg(s1.price) from Order s1)) t)", d.toHql().hql());
// a subquery on another table keeps the plain form
ExecutableDelete<Order> unused = qb.delete(Order.class)
        .where((o, sub) -> !sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).exists());
assertEquals("delete from Order e0 where not exists (select 1 from Item s1 where s1.order.id = e0.id)",
        unused.toHql().hql());
```

Example code: [`DmlQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) (`delete-self-subquery`).

<a id="delete-the-first-rows-limit"></a>
## Delete the first rows: `limit`

If you add `orderBy(...)` and `limit(n)` after `where(...)` or `all()`, at most `n` rows are deleted. The targets are the first `n` rows of `qb.from(X.class).where(same).orderBy(same)`.
Use this to process many rows in small batches.

- Without `orderBy`, the order is by id.
- lambdaQL adds the id as the last sort key, so the order is always the same (see [Tie-breaker](04-order-paging.md#tie-breaker-automatic-id)).
- In `orderBy`, give a sort lambda expression or a `SortSpec`, as in select (see [4. Sorting and paging](04-order-paging.md)).
- `orderBy` without `limit` is an error (`IllegalStateException`), because the order has no effect in that case.

<!-- region: DmlQueryTest.java#delete-limit -->
```java
// the first rows of qb.from(Order.class).where(same).orderBy(same); the id is the last sort key
ExecutableDelete<Order> d = qb.delete(Order.class)
        .where(o -> o.getStatus() == 1)
        .orderBy(o -> asc(o.getPrice()))
        .limit(1);
assertEquals("delete from Order e0 where e0.id in (select t.id from (select x0.id as id from Order x0"
        + " where x0.status = 1 order by x0.price asc, x0.id asc limit 1) t)", d.toHql().hql());
int deleted = d.execute();
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

- The inner select follows the select rules, including joins. So this statement deletes exactly the rows that a select returns.
- The derived table `t` is for MariaDB. Without the derived table, MariaDB rejects `limit` in an `in` subquery (error 1235) and a subquery that reads the target table (error 1093).
- H2 and Oracle write `fetch first 1 rows only`, and MariaDB writes `limit 1`.

Example code: [`DmlQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) (`delete-limit`).

<a id="delete-exactly-one-row-executeone"></a>
## Delete exactly one row: `executeOne()`

`executeOne()` runs the delete with **one-row protection**. Use it instead of `execute()` when the condition must match one row (for example, a unique key).

1. lambdaQL reads at most 2 ids of the rows that match the condition.
2. If there is no id, it changes nothing and returns 0.
3. If there are 2 ids, it changes nothing and throws the JPA `NonUniqueResultException`.
4. If there is 1 id, it deletes that row with `id = :p and (condition)` and returns 1. If the row stopped matching the condition in the meantime, it returns 0.

The statement names one id, so it never changes two or more rows. This protection does not rely on a rollback.

<!-- region: DmlQueryTest.java#execute-one -->
```java
// one row matches: it is deleted
int one = qb.delete(Order.class).where(o -> o.getId() == id).executeOne();
// two or more rows match: nothing is deleted
assertThrows(jakarta.persistence.NonUniqueResultException.class,
        () -> qb.delete(Order.class).where(o -> o.getPrice() > 100).executeOne());
```

- Using `executeOne()` with `orderBy` or `limit` is an error (`IllegalStateException`).
- `limit` and `executeOne()` do not support entities with composite keys.
- In a Spring repository, `deleteOne(where)` does the same and throws `IncorrectResultSizeDataAccessException` (see [12.4](12-spring-data-jpa.md#one-row-protection-deleteone-and-updateone)).

---

[7. Update, insert, delete (Java)](07-dml.md) | [7.2 Update](07-2-update.md) | **7.4 Delete** | [7.5 Insert](07-5-insert.md) | [7.6 Upsert](07-6-upsert.md) | [Kotlin](../kotlin/07-4-delete.md)

Example code: [`DmlQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) (`execute-one`).
