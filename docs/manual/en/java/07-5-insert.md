<a id="75-insert"></a>
# 7.5 Insert (Java)

[7. Update, insert, delete (Java)](07-dml.md) | [7.2 Update](07-2-update.md) | [7.4 Delete](07-4-delete.md) | **7.5 Insert** | [7.6 Upsert](07-6-upsert.md) | [Kotlin](../kotlin/07-5-insert.md)

---

<a id="one-row"></a>
## One row

<!-- region: DmlQueryTest.java#insert -->
```java
ExecutableInsert<Archive> i = qb.insert(Archive.class)
        .values(a -> { a.setId(100L); a.setPrice(9.5); a.setCustomerName("Kim"); });
assertEquals("insert into Archive (id, price, customerName) values (100L, 9.5, 'Kim')", i.toHql().get(0).hql());
int inserted = i.execute();
// a generated id (IDENTITY) can be left out
int generated = qb.insert(IdOrder.class).values(o -> { o.setPrice(1.0); o.setStatus(1); }).execute();
```

**SQL by database**

<!-- sql: insert -->
```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price,customer_name)
values (100,9.5,'Kim')
```

Constants written in the lambda expression stay literals. A value cannot read the new row. `a.setPrice(a.getPrice())` is an error.

For an entity without setters, insert one row with `Dml.value` (see [7.3](07-dml.md#73-two-ways-to-assign-setter-or-dmlvalue)). `Archive` in the example has setters, but you use the same form for an entity without setters.

<!-- region: ManualJavaDmlTest.java#insert-value -->
```java
ExecutableInsert<Archive> i = qb.insert(Archive.class)
        .values(a -> { value(a.getId(), id); value(a.getPrice(), 1.0); });
assertEquals("insert into Archive (id, price) values (:p1, 1.0)", i.toHql().get(0).hql());
int inserted = i.execute();
```

<a id="a-subquery-in-a-value"></a>
### A subquery in a value

With a lambda expression that has two arguments, like `values((a, sub) -> ...)`, you can build a subquery with the second argument `sub`.
The same is true for `values((a, sub) -> ...)` and `onConflict(...).update((a, sub) -> ...)` of upsert (see [7.6](07-6-upsert.md#your-own-change-onconflictkeyupdate)).

<!-- region: DmlQueryTest.java#insert-values-sub -->
```java
// values((a, sub) -> ...): a subquery can read captured values, not the new row
ExecutableInsert<Archive> i = qb.insert(Archive.class)
        .values((a, sub) -> {
            a.setId(100L);
            a.setPrice(sub.from(Item.class).where(x -> x.getOrder().getId() == first).sum(x -> x.getPrice()));
        });
assertEquals("insert into Archive (id, price) values (100L, (select sum(s1.price) from Item s1 where s1.order.id = :p1))",
        i.toHql().get(0).hql());
// upsert: a subquery in the inserted value and in the update on conflict
ExecutableInsert<Archive> up = qb.insert(Archive.class)
        .values((a, sub) -> { a.setId(100L); a.setPrice(sub.from(Item.class).sum(x -> x.getPrice())); })
        .onConflict(a -> a.getId())
        .update((a, sub) -> a.setPrice(sub.from(Order.class).max(o -> o.getPrice())));
assertEquals("insert into Archive e0 (id, price) values (100L, (select sum(s1.price) from Item s1))"
        + " on conflict(id) do update set price = (select max(s1.price) from Order s1)", up.toHql().get(0).hql());
```

- An aggregate of another table (with or without captured parameters), many rows, and a subquery that reads the target table itself (no MariaDB error 1093) all work (measured on H2, MariaDB 10.11 and Oracle 23.26).
- A subquery can read captured values, but it cannot read the row being inserted.
- You can also use subqueries in the list form (see [Many rows from a list](#many-rows-from-a-list)).
- Subqueries in the insert values of upsert and in `do update set` also work. On Oracle, Hibernate runs it as `merge` (measured).

Example code: [`DmlQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) (`insert`, `insert-values-sub`), [`ManualJavaDmlTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaDmlTest.java) (`insert-value`).

<a id="many-rows-from-a-list"></a>
## Many rows from a list

One row is inserted for each element of the list. The second lambda expression argument is the element.

<!-- region: DmlQueryTest.java#insert-list -->
```java
List<Line> lines = List.of(new Line(201, 1.0), new Line(202, 2.0));
ExecutableInsert<Archive> i = qb.insert(Archive.class).values(lines, (a, l) -> { a.setId(l.id()); a.setPrice(l.price()); });
assertEquals("insert into Archive (id, price) values (:p1, :p2), (:p3, :p4)", i.toHql().get(0).hql());
```

**SQL by database**

<!-- sql: insert-list -->
```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price)
values (?,?), (?,?)
```

lambdaQL reads the getters of the element in Java, so all values become bind parameters.
It writes one statement with many VALUES rows. If the list is long, it splits it so that one statement has 500 values or fewer (see [7.8](07-dml.md#batching-for-many-rows)). An empty list runs no SQL and returns 0.

**Subqueries in the list form.** You can build a subquery with the third argument, like `values(list, (a, l, sub) -> ...)`. The same is true for `qb.upsert(X.class).values(list, (a, l, sub) -> ...)`.
A subquery can read values of the element. Those values are read in Java and bound as one parameter per row. It cannot read the row being inserted (measured on three databases).

<!-- region: DmlQueryTest.java#insert-list-sub -->
```java
// one row per element; the subquery reads the element's value
ExecutableInsert<Archive> i = qb.insert(Archive.class).values(refs, (a, r, sub) -> {
    a.setId(r.id());
    a.setPrice(sub.from(Item.class).where(x -> x.getOrder().getId() == r.orderId()).sum(x -> x.getPrice()));
});
assertEquals("insert into Archive (id, price) values (:p1, (select sum(s1.price) from Item s1 where s1.order.id = :p2)),"
        + " (:p3, (select sum(s1.price) from Item s1 where s1.order.id = :p4))", i.toHql().get(0).hql());
```

Example code: [`DmlQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) (`insert-list`, `insert-list-sub`).

<a id="generated-id"></a>
## Generated id

If the id uses IDENTITY or SEQUENCE, you can leave the id out.
The `IdOrder` line in the first example is this case. The id of `IdOrder` uses IDENTITY.

<a id="insert--select"></a>
## insert ... select

Copy rows from another entity. The `select` lambda expression takes the target (`a`) and the source (`o`).

<!-- region: DmlQueryTest.java#insert-select -->
```java
ExecutableInsert<Archive> i = qb.insert(Archive.class)
        .from(Order.class)
        .where(o -> o.getStatus() == 1)
        .select((a, o) -> { a.setId(o.getId()); a.setPrice(o.getPrice()); a.setCustomerName(o.getCustomer().getName()); });
// a path in the select list: left join, so orders without a customer stay
assertEquals("insert into Archive (id, price, customerName) select e0.id, e0.price, j1.name"
        + " from Order e0 left join e0.customer j1 where e0.status = 1", i.toHql().get(0).hql());
```

**SQL by database**

<!-- sql: insert-select -->
```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price,customer_name)
select o1_0.id,o1_0.price,c1_0.name
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where o1_0.status=1
```

lambdaQL uses an explicit `left join` for source paths, so orders without a customer are not left out. The SQL is the same on the three databases.

For a target entity without setters, use `Dml.value`. The first argument is a getter call on the target `a`.

<!-- region: ManualJavaDmlTest.java#insert-select-value -->
```java
ExecutableInsert<Archive> i = qb.insert(Archive.class).from(Order.class).where(o -> o.getStatus() == 1)
        .select((a, o) -> { value(a.getId(), o.getId()); value(a.getCustomerName(), o.getCustomer().getName()); });
assertEquals("insert into Archive (id, customerName) select e0.id, j1.name"
        + " from Order e0 left join e0.customer j1 where e0.status = 1", i.toHql().get(0).hql());
int inserted = i.execute();
```

**Subqueries.** The `where` of the source takes `sub`, the starting point of a subquery, as its second argument, and `select` takes it as its third argument.

<!-- region: DmlQueryTest.java#insert-select-sub -->
```java
// where((o, sub) -> ...) on the source, and select((a, o, sub) -> ...): the third parameter starts a subquery
ExecutableInsert<Archive> i = qb.insert(Archive.class).from(Order.class)
        .where((o, sub) -> sub.from(Item.class).where(x -> x.getOrder().getId() == o.getId()).exists())
        .select((a, o, sub) -> {
            a.setId(o.getId());
            a.setPrice(sub.from(Item.class).where(x -> x.getOrder().getId() == o.getId()).sum(x -> x.getPrice()));
        });
assertEquals("insert into Archive (id, price) select e0.id, (select sum(s2.price) from Item s2 where s2.order.id = e0.id)"
        + " from Order e0 where exists (select 1 from Item s1 where s1.order.id = e0.id)", i.toHql().get(0).hql());
```

Example code: [`DmlQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) (`insert-select`, `insert-select-sub`), [`ManualJavaDmlTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaDmlTest.java) (`insert-select-value`).

<a id="insert--select-with-aggregates"></a>
## insert ... select with aggregates

Use `groupBy`, `having` and the `Agg` functions of [6. Select, aggregates and grouping](06-select-group.md).
The call order is `from(...)`, `where(...)`, `groupBy(...)`, `having(...)`, `select(...)`. You can leave out `where`, `groupBy` and `having`.

<!-- region: ProjectionTest.java#insert-group -->
```java
// INSERT ... SELECT with groupBy: one summary row per customer
ExecutableInsert<Summary> i = qb.insert(Summary.class)
        .from(Order.class)
        .where(o -> o.getCustomer() != null)
        .groupBy(o -> o.getCustomer().getId())
        .having(o -> Agg.count() >= 2)
        .select((s, o) -> { s.setCustomerId(o.getCustomer().getId()); s.setTotal(Agg.sum(o.getPrice())); s.setCnt(Agg.count()); });
assertEquals("insert into Summary (customerId, total, cnt) select e0.customer.id, sum(e0.price), count(*) from Order e0"
        + " where e0.customer is not null group by e0.customer.id having count(*) >= 2L", i.toHql().get(0).hql());
```

**SQL by database**

<!-- sql: insert-group -->
```sql
-- H2, MariaDB, Oracle (same)
insert into summary(customer_id,total,cnt)
select o1_0.customer_id,sum(o1_0.price),count(*)
from orders o1_0
where o1_0.customer_id is not null
group by o1_0.customer_id
having count(*)>=2
```

- Every non-aggregate value must be a `groupBy` key. If not, lambdaQL throws a `TranslationException` (`LQL-3035`) at analysis time.
- You cannot use `Agg` in `where`. Use `having`.
- The inserted `Summary` rows match the values grouped by the same keys in Java.

---

[7. Update, insert, delete (Java)](07-dml.md) | [7.2 Update](07-2-update.md) | [7.4 Delete](07-4-delete.md) | **7.5 Insert** | [7.6 Upsert](07-6-upsert.md) | [Kotlin](../kotlin/07-5-insert.md)

Example code: [`ProjectionTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java) (`insert-group`).
