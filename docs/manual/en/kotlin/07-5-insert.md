<a id="75-insert"></a>
# 7.5 Insert (Kotlin)

[7. Update, insert, delete (Kotlin)](07-dml.md) | [7.2 Update](07-2-update.md) | [7.4 Delete](07-4-delete.md) | **7.5 Insert** | [7.6 Upsert](07-6-upsert.md) | [Java](../java/07-5-insert.md)

---


<a id="one-row"></a>
## One row

```kotlin
val i = qb.insert(Archive::class.java)
    .values { it.id = 100L; it.price = 9.5; it.customerName = "Kim" }
// HQL: insert into Archive (id, price, customerName) values (100L, 9.5, 'Kim')
val inserted = i.execute()
// a generated id (IDENTITY) can be left out
val generated = qb.insert(IdOrder::class.java).values { it.price = 1.0; it.status = 1 }.execute()
```

**About the SQL blocks.** `…` is the shortened select list (all entity columns), and `?` is a bind parameter. The lines come from [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java). It runs each query on H2 2.3, MariaDB 10.11 and Oracle 23.26 with Hibernate 7.4, and it fails if Hibernate's SQL changes. The test uses Java lambdas. A Kotlin lambda with the same meaning gives the same HQL, so it gets the same SQL.

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `insert`:

```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price,customer_name)
values (100,9.5,'Kim')
```

[`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt), region `insert-delete`:

```kotlin
val i = qb.insert(Archive::class.java).values { it.id = 100L; it.price = 9.5; it.customerName = "Kim" }
assertEquals(1, i.execute())
val d = qb.delete(Order::class.java).where { o, sub -> !sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
assertEquals("delete from Order e0 where not exists (select 1 from Item s1 where s1.order.id = e0.id)", d.toHql().hql())
```

The first two lines insert one row with a Kotlin lambda. The delete part is explained in [7.4](07-4-delete.md).
More statements written in Java are tested in [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) (H2, MariaDB and Oracle). See [the Java version of this page](../java/07-5-insert.md) for those examples.

The constants in the lambda stay literals (decision D37). A value cannot read the new row. `it.price = it.price` is an error.

A `val` property is written in a single row with `Dml.value` (decision D49, see [7.3](07-dml.md#73-two-ways-to-assign-property-or-dmlvalue)).

```kotlin
qb.insert(Order::class.java).values { o -> value(o.id, id); value(o.status, 1) }.execute()
```

<a id="a-subquery-in-a-value"></a>
### A subquery in a value

With a two-argument lambda such as `values { a, sub -> ... }`, the second argument `sub` makes a subquery (decision D49).
The same holds for `values { a, sub -> ... }` and `onConflict { ... }.update { a, sub -> ... }` of an upsert (see [7.6](07-6-upsert.md#your-own-change-onconflictkeyupdate)).

[`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt), region `insert-values-sub`:

```kotlin
// values { a, sub -> ... }: a subquery can read captured values, not the new row (D49)
val i = qb.insert(Archive::class.java)
    .values { a, sub -> a.id = 100L; a.price = sub.from(Item::class.java).sum { it.price } }
assertEquals("insert into Archive (id, price) values (100L, (select sum(s1.price) from Item s1))", i.toHql()[0].hql())
```

- An aggregate over another table (with or without captured parameters), many rows, and a subquery that reads the target table itself all work (measured on H2, MariaDB 10.11 and Oracle 23.26; no MariaDB error 1093).
- The subquery can read captured values, but it cannot read the new row being inserted.
- The list form can also use a subquery (see [Many rows from a list](#many-rows-from-a-list)).
- A subquery in the insert values of an upsert and in `do update set` also works. On Oracle, Hibernate runs it as a `merge` (measured). The upsert form is checked by a Java test ([`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java), region `insert-values-sub`).

<a id="many-rows-from-a-list"></a>
## Many rows from a list

lambdaQL inserts one row for each element of the list. The second lambda argument is the element.

```kotlin
val lines = listOf(Line(201L, 1.0), Line(202L, 2.0))
val i = qb.insert(Archive::class.java).values(lines) { a, l -> a.id = l.id; a.price = l.price }
// HQL: insert into Archive (id, price) values (:p1, :p2), (:p3, :p4)
```

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `insert-list`:

```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price)
values (?,?), (?,?)
```

lambdaQL reads the properties of the element in Java, so every value is a bind parameter.
It writes one statement with many VALUES rows. A long list is split so that one statement has at most 500 values (see [7.8](07-dml.md#batching-for-many-rows)). An empty list runs no SQL and returns 0.

**A subquery in the list form.** A third argument makes a subquery, as in `values(list) { a, l, sub -> ... }` (decision D49). The same holds for `qb.upsert(X::class.java).values(list) { a, l, sub -> ... }`.
The subquery can read the values of the element. lambdaQL reads those values before the query and binds them as one parameter per row. The subquery cannot read the new row being inserted (measured on three databases).
The HQL is `values (:p1, (select sum(s1.price) from Item s1 where s1.order.id = :p2)), (:p3, ...)`. A Java test ([`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java), region `insert-list-sub`) checks it.

<a id="generated-id"></a>
## Generated id

If the id uses IDENTITY or SEQUENCE, you can leave out the id (rule U12).
The `IdOrder` line in the first example is this case. `IdOrder` has a generated id.

<a id="insert--select"></a>
## Insert ... select

Copy rows from another entity. The `select` lambda gets the target (`a`) and the source (`o`).

```kotlin
val i = qb.insert(Archive::class.java)
    .from(Order::class.java)
    .where { it.status == 1 }
    .select { a, o -> a.id = o.id; a.price = o.price; a.customerName = o.customer?.name }
// a path in the select list: left join, so orders without a customer stay (U10)
// HQL: insert into Archive (id, price, customerName) select e0.id, e0.price, j1.name from Order e0 left join e0.customer j1 where e0.status = 1
```

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `insert-select`:

```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price,customer_name)
select o1_0.id,o1_0.price,c1_0.name
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where o1_0.status=1
```

lambdaQL uses an explicit `left join` for source paths, so orders without a customer are not lost (rule U10). The SQL is the same on all three databases.

For a `val` property of the target, use `Dml.value`. The first argument is a property of the target `a`.

```kotlin
qb.insert(Archive::class.java).from(Order::class.java).where { it.status == 9 }
    .select { a, o -> value(a.id, o.id); value(a.customerName, o.customer?.name) }
    .execute()
```

**Subqueries.** The source `where` gets `sub`, the starting point for subqueries, as its second argument, and `select` gets it as its third argument (decision D49). A Java test ([`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java), region `insert-select-sub`) checks this. The following is a design example.

```kotlin
qb.insert(Archive::class.java).from(Order::class.java)
    .where { o, sub -> sub.from(Item::class.java).where { x -> x.order?.id == o.id }.exists() }
    .select { a, o, sub -> a.id = o.id; a.price = sub.from(Item::class.java).where { x -> x.order?.id == o.id }.sum { it.price } }
    .execute()
```

<a id="insert--select-with-aggregates"></a>
## Insert ... select with aggregates

Use `groupBy`, `having` and the `Agg` functions from [6. Select, aggregates and group by](06-select-group.md).
The call order is `from(...)`, `where { }`, `groupBy { }`, `having { }`, `select { }`. `where`, `groupBy` and `having` are optional.

```kotlin
// INSERT ... SELECT with groupBy: one summary row per customer
val i = qb.insert(Summary::class.java)
    .from(Order::class.java)
    .where { it.customer != null }
    .groupBy { it.customer?.id }
    .having { Agg.count() >= 2 }
    .select { s, o -> s.customerId = o.customer?.id; s.total = Agg.sum(o.price); s.cnt = Agg.count() }
// HQL: insert into Summary (customerId, total, cnt) select e0.customer.id, sum(e0.price), count(*) from Order e0
//      where e0.customer is not null group by e0.customer.id having count(*) >= 2L
```

The same statement is tested in Java in [`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java) (region `insert-group`).

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `insert-group`:

```sql
-- H2, MariaDB, Oracle (same)
insert into summary(customer_id,total,cnt)
select o1_0.customer_id,sum(o1_0.price),count(*)
from orders o1_0
where o1_0.customer_id is not null
group by o1_0.customer_id
having count(*)>=2
```

- Every value without an aggregate must be a `groupBy` key (rule J3). Otherwise lambdaQL throws `TranslationException` (`LQL-3035`) at analysis time.
- `Agg` is not allowed in `where`. Use `having`.

---

---

[7. Update, insert, delete (Kotlin)](07-dml.md) | [7.2 Update](07-2-update.md) | [7.4 Delete](07-4-delete.md) | **7.5 Insert** | [7.6 Upsert](07-6-upsert.md) | [Java](../java/07-5-insert.md)
