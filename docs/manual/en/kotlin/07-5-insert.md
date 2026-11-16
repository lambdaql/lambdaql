<a id="75-insert"></a>
# 7.5 Insert (Kotlin)

> [!NOTE]
> **Status: implemented and tested** on H2 2.3, MariaDB 10.11 and Oracle 23.26 (milestone M10). The tests use **Java** lambdas. **Kotlin lambdas in DML are not tested yet.**
> **Not implemented yet:** aggregates in `insert ... select` (milestone M9) and the `by.set` form of `insert ... select`.
> The Kotlin code shows the tested Java statements in Kotlin syntax, and the HQL comes from the Java tests in [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java). Examples without HQL follow the design.
> See the [track README](README.md) for the full status.

[7. Update, insert, delete (Kotlin)](07-dml.md) | [7.2 Update](07-2-update.md) | [7.4 Delete](07-4-delete.md) | **7.5 Insert** | [7.6 Upsert](07-6-upsert.md) | [Java](../java/07-5-insert.md)

---


## One row

```kotlin
val i = qb.insert(Archive::class.java)
    .values { it.id = 100L; it.price = 9.5; it.customerName = "Kim" }
// HQL: insert into Archive (id, price, customerName) values (100L, 9.5, 'Kim')
val inserted = i.execute()
// a generated id (IDENTITY) can be left out
val generated = qb.insert(IdOrder::class.java).values { it.price = 1.0; it.status = 1 }.execute()
```

**About the SQL blocks.** `…` is the shortened select list (all entity columns), and `?` is a bind parameter. The lines come from [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java). It runs each query on H2 2.3, MariaDB 10.11 and Oracle 23.26 with Hibernate 6.6.5, and it fails if Hibernate's SQL changes. The test uses Java lambdas. A Kotlin lambda with the same meaning gives the same HQL, so it gets the same SQL. Blocks marked **(planned)** come from [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java) instead. It gives the planned HQL to Hibernate directly, because lambdaQL does not make these statements yet.

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `insert`:

```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price,customer_name)
values (100,9.5,'Kim')
```

There are no Kotlin tests yet. The same statements written in Java are tested in [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) (H2, MariaDB and Oracle). See [the Java version of this page](../java/07-5-insert.md) for the tested examples.

The constants in the lambda stay literals (decision D37). A value cannot read the new row: `it.price = it.price` is an error.

The `by.set` form works for one row too, for `val` properties:

```kotlin
qb.insert(Order::class.java).values { o, by -> by.set(o.id, id).set(o.status, 1) }.execute()
```

## Many rows from a list

One row for each element of the list. The second lambda argument is the element.

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

## Generated id

If the id uses IDENTITY or SEQUENCE, you can leave out the id (rule U12).
The `IdOrder` line in the first example does this: `IdOrder` has a generated id.

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

The design also has a `by.set` form, with the context object as the third argument. It is **not implemented yet**:

```kotlin
qb.insert(Archive::class.java).from(Order::class.java).where { it.status == 9 }
    .select { a, o, by -> by.set(a.id, o.id).set(a.customerName, o.customer?.name) }
    .execute()
```

## Insert ... select with aggregates

Use `groupBy` and the `Agg` functions from [6. Select, aggregates and group by](06-select-group.md).
This form is **not implemented yet** (milestone M9):

```kotlin
qb.insert(Summary::class.java).from(Order::class.java).groupBy { it.customer?.id }
    .select { s, o -> s.customerId = o.customer?.id; s.total = Agg.sum(o.price); s.cnt = Agg.count() }
    .execute()
```

**SQL by database (planned)** — Hibernate's translation of the planned HQL, recorded by `PlannedSqlTest`; lambdaQL does not make this query yet. From `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql), example `07-insert-group`:

```sql
-- H2, MariaDB, Oracle (same)
insert into summary(customer_id,total,cnt)
select o1_0.customer_id,sum(o1_0.price),count(*)
from orders o1_0
group by o1_0.customer_id
```

---

---

[7. Update, insert, delete (Kotlin)](07-dml.md) | [7.2 Update](07-2-update.md) | [7.4 Delete](07-4-delete.md) | **7.5 Insert** | [7.6 Upsert](07-6-upsert.md) | [Java](../java/07-5-insert.md)
