<a id="75-insert"></a>
# 7.5 Insert (Kotlin)

[7. Update, insert, delete (Kotlin)](07-dml.md) | [7.2 Update](07-2-update.md) | [7.4 Delete](07-4-delete.md) | **7.5 Insert** | [7.6 Upsert](07-6-upsert.md) | [Java](../java/07-5-insert.md)

---

<a id="one-row"></a>
## One row

<!-- region: ManualKotlinDmlTest.kt#insert -->
```kotlin
val i = qb.insert(Archive::class.java)
    .values { it.id = 100L; it.price = 9.5; it.customerName = "Kim" }
assertEquals("insert into Archive (id, price, customerName) values (100L, 9.5, 'Kim')", i.toHql()[0].hql())
val inserted = i.execute()
// a generated id (IDENTITY) can be left out
val generated = qb.insert(IdOrder::class.java).values { it.price = 1.0; it.status = 1 }.execute()
// the values cannot read the new row
val error = assertThrows(LambdaQLException::class.java) {
    qb.insert(Archive::class.java).values { it.id = 1L; it.price = it.price }
}
```

**SQL by database**

<!-- sql: insert -->
```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price,customer_name)
values (100,9.5,'Kim')
```

Constants written in the lambda stay literals. A value cannot read the new row. `it.price = it.price` is an error.

For a `val` property, write one row with `Dml.value` (see [7.3](07-dml.md#73-two-ways-to-assign-property-or-dmlvalue)).

<!-- region: ManualKotlinDmlTest.kt#insert-value -->
```kotlin
// properties without a setter (val): Dml.value
val i = qb.insert(Archive::class.java).values { a -> value(a.id, id); value(a.price, 1.5) }
assertEquals("insert into Archive (id, price) values (:p1, 1.5)", i.toHql()[0].hql())
```

<a id="a-subquery-in-a-value"></a>
### A subquery in a value

If you use a two-argument lambda, as in `values { a, sub -> ... }`, you can build a subquery with the second argument `sub`.
The same is true for `values { a, sub -> ... }` and `onConflict { ... }.update { a, sub -> ... }` of upsert (see [7.6](07-6-upsert.md#your-own-change-onconflictkeyupdate)).

<!-- region: KotlinQueryTest.kt#insert-values-sub -->
```kotlin
// values { a, sub -> ... }: a subquery can read captured values, not the new row
val i = qb.insert(Archive::class.java)
    .values { a, sub -> a.id = 100L; a.price = sub.from(Item::class.java).sum { it.price } }
assertEquals("insert into Archive (id, price) values (100L, (select sum(s1.price) from Item s1))", i.toHql()[0].hql())
```

- An aggregate of another table (with or without captured parameters), many rows, and a subquery that reads the target table itself (no MariaDB error 1093) all work (measured on H2, MariaDB 10.11 and Oracle 23.26).
- A subquery can read captured values, but it cannot read the row being inserted.
- You can also use subqueries in the list form (see [Many rows from a list](#many-rows-from-a-list)).
- Subqueries in the insert values of an upsert and inside `do update set` also work. On Oracle, Hibernate runs them as `merge` (measured). The example is in [7.6](07-6-upsert.md#your-own-change-onconflictkeyupdate).

Example code: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt) (`insert`, `insert-value`), [`KotlinQueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt) (`insert-values-sub`).

<a id="many-rows-from-a-list"></a>
## Many rows from a list

Inserts one row for each element of the list. The second lambda argument is the element.

<!-- region: ManualKotlinDmlTest.kt#insert-list -->
```kotlin
val lines = listOf(Line(201L, 1.0), Line(202L, 2.0))      // data class Line(val id: Long, val price: Double)
val i = qb.insert(Archive::class.java).values(lines) { a, l -> a.id = l.id; a.price = l.price }
assertEquals("insert into Archive (id, price) values (:p1, :p2), (:p3, :p4)", i.toHql()[0].hql())
```

**SQL by database**

<!-- sql: insert-list -->
```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price)
values (?,?), (?,?)
```

lambdaQL reads the properties of the element in Java, so every value becomes a bind parameter.
`Line` is a normal data class. It writes one statement with many VALUES rows. If the list is long, it is split so that one statement has at most 500 values (see [7.8](07-dml.md#batching-for-many-rows)). An empty list runs no SQL and returns 0.

**Subqueries in the list form.** You can build a subquery with the third argument, as in `values(list) { a, l, sub -> ... }`.
A subquery can read values of the element. Those values are read before the query and bound as one parameter per row. It cannot read the row being inserted (measured on three databases).

<!-- region: ManualKotlinDmlTest.kt#insert-list-sub -->
```kotlin
// one row per element; the subquery reads the element's value
val i = qb.insert(Archive::class.java).values(refs) { a, r, sub ->
    a.id = r.id
    a.price = sub.from(Item::class.java).where { x -> x.order?.id == r.orderId }.sum { x -> x.price }
}
assertEquals("insert into Archive (id, price) values (:p1, (select sum(s1.price) from Item s1 where s1.order.id = :p2)),"
    + " (:p3, (select sum(s1.price) from Item s1 where s1.order.id = :p4))", i.toHql()[0].hql())
```

`Ref` is `data class Ref(val id: Long, val orderId: Long?)`.

Example code: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt) (`insert-list`, `insert-list-sub`).

<a id="generated-id"></a>
## Generated id

If the id uses IDENTITY or SEQUENCE, you can leave out the id.
The `IdOrder` line in the first example is this case. The id of `IdOrder` is a generated value.

<a id="insert--select"></a>
## Insert ... select

Copies rows from another entity. The `select` lambda takes the target (`a`) and the source (`o`).

<!-- region: ManualKotlinDmlTest.kt#insert-select -->
```kotlin
val i = qb.insert(Archive::class.java)
    .from(Order::class.java)
    .where { it.status.toInt() == 1 }
    .select { a, o -> a.id = o.id; a.price = o.price; a.customerName = o.customer?.name }
// a path in the select list: left join, so orders without a customer stay
assertEquals("insert into Archive (id, price, customerName) select e0.id, e0.price, j1.name"
    + " from Order e0 left join e0.customer j1 where e0.status = 1", i.toHql()[0].hql())
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

lambdaQL uses an explicit `left join` for source paths, so orders without a customer are not dropped. The SQL is the same on the three databases.

For a `val` property of the target, use `Dml.value`. The first argument is a property of the target `a`.

<!-- region: ManualKotlinDmlTest.kt#insert-select-value -->
```kotlin
// Dml.value in insert ... select: the first argument is a property of the target a
val i = qb.insert(Archive::class.java).from(Order::class.java).where { it.status.toInt() == 3 }
    .select { a, o -> value(a.id, o.id); value(a.customerName, o.customer?.name) }
assertEquals("insert into Archive (id, customerName) select e0.id, j1.name from Order e0 left join e0.customer j1"
    + " where e0.status = 3", i.toHql()[0].hql())
```

**Subqueries.** The `where` of the source takes `sub`, the starting point of subqueries, as its second argument, and `select` takes it as its third argument.

<!-- region: ManualKotlinDmlTest.kt#insert-select-sub -->
```kotlin
// where { o, sub -> ... } on the source, and select { a, o, sub -> ... }: the third parameter starts a subquery
val i = qb.insert(Archive::class.java).from(Order::class.java)
    .where { o, sub -> sub.from(Item::class.java).where { x -> x.order?.id == o.id }.exists() }
    .select { a, o, sub -> a.id = o.id; a.price = sub.from(Item::class.java).where { x -> x.order?.id == o.id }.sum { x -> x.price } }
assertEquals("insert into Archive (id, price) select e0.id, (select sum(s2.price) from Item s2 where s2.order.id = e0.id)"
    + " from Order e0 where exists (select 1 from Item s1 where s1.order.id = e0.id)", i.toHql()[0].hql())
```

Example code: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt) (`insert-select`, `insert-select-value`, `insert-select-sub`).

<a id="insert--select-with-aggregates"></a>
## Insert ... select with aggregates

Use `groupBy`, `having` and the `Agg` functions from [6. Select, aggregates and group by](06-select-group.md).
The call order is `from(...)`, `where { }`, `groupBy { }`, `having { }`, `select { }`. You can leave out `where`, `groupBy` and `having`.

<!-- region: ManualKotlinDmlTest.kt#insert-group -->
```kotlin
// INSERT ... SELECT with groupBy: one summary row per customer
val i = qb.insert(Summary::class.java)
    .from(Order::class.java)
    .where { it.customer != null }
    .groupBy { it.customer?.id }
    .having { Agg.count() >= 2 }
    .select { s, o -> s.customerId = o.customer?.id; s.total = Agg.sum(o.price); s.cnt = Agg.count() }
assertEquals("insert into Summary (customerId, total, cnt) select e0.customer.id, sum(e0.price), count(*) from Order e0"
    + " where e0.customer is not null group by e0.customer.id having count(*) >= 2L", i.toHql()[0].hql())
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

- Every non-aggregate value must be a `groupBy` key. If not, lambdaQL throws `TranslationException` (`LQL-3035`) at analysis time.
- You cannot use `Agg` in `where`. Use `having`.

---

[7. Update, insert, delete (Kotlin)](07-dml.md) | [7.2 Update](07-2-update.md) | [7.4 Delete](07-4-delete.md) | **7.5 Insert** | [7.6 Upsert](07-6-upsert.md) | [Java](../java/07-5-insert.md)

Example code: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt) (`insert-group`).
