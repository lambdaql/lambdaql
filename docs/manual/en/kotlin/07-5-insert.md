<a id="75-insert"></a>
# 7.5 Insert (Kotlin)

> [!NOTE]
> **Status: designed, not implemented yet.**
> This chapter is a decided design (decision D28, milestone M10).
> The Kotlin syntax compiles (kotlinc 2.3.21), and the SQL was checked on H2, MariaDB and Oracle with Hibernate 6.6.5.
> It does **not** run in lambdaQL today. Today only the analysis core and `where` + `toList()` work
> (tested on H2, MariaDB and Oracle). See the [track README](README.md) for the full status.

[7. Update, insert, delete (Kotlin)](07-dml.md) | [7.2 Update](07-2-update.md) | [7.4 Delete](07-4-delete.md) | **7.5 Insert** | [Java](../java/07-5-insert.md)

---


## One row

```kotlin
qb.insert(Order::class.java).values { it.id = id; it.price = p; it.status = 1 }.execute()

// with by, for val properties
qb.insert(Order::class.java).values { o, by -> by[o.id] = id; by[o.status] = 1 }.execute()
```

## Many rows from a list

One row for each element of the list. The second lambda argument is the element.

```kotlin
val lines = listOf(Line(1L, 10.0), Line(2L, 20.0))

qb.insert(Order::class.java)
    .values(lines) { o, l -> o.id = l.id; o.price = l.price; o.status = 1 }
    .execute()
```

lambdaQL writes one statement with many VALUES rows. If the list is large, it splits the work by the **number of bind values** (see [7.7](07-dml.md#batching-for-many-rows)).

## Generated id

If the id uses IDENTITY or SEQUENCE, you can leave out the id (rule U12):

```kotlin
qb.insert(IdOrder::class.java).values { it.price = p; it.status = 1 }.execute()
```

## Insert ... select

Copy rows from another entity. The `select` lambda gets the target (`a`) and the source (`o`).

```kotlin
qb.insert(Archive::class.java).from(Order::class.java).where { it.status == 9 }
    .select { a, o -> a.id = o.id; a.price = o.price; a.customerName = o.customer?.name }
    .execute()
```

```sql
insert into Archive (id, price, customerName)
select o.id, o.price, c.name from Order o left join o.customer c where o.status = 9
```

**About the SQL blocks.** `…` is the shortened select list (all entity columns), and `?` is a bind parameter. [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java) gives the HQL of this page to Hibernate directly and records the SQL on H2 2.3, MariaDB 10.11 and Oracle 23.26 (Hibernate 6.6.5). lambdaQL does not make these queries yet. The test fails if Hibernate's SQL changes.

**SQL by database (planned)** — Hibernate's translation of the planned HQL, recorded by `PlannedSqlTest`; lambdaQL does not make this query yet. From `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql), example `07-insert-select`:

```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price,customer_name)
select o1_0.id,o1_0.price,c1_0.name
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where o1_0.status=9
```

lambdaQL uses an explicit `left join` for source paths, so orders without a customer are not lost (rule U10).

With `by`, the context object is the third argument:

```kotlin
qb.insert(Archive::class.java).from(Order::class.java).where { it.status == 9 }
    .select { a, o, by -> by[a.id] = o.id; by[a.customerName] = o.customer?.name }
    .execute()
```

## Insert ... select with aggregates

Use `groupBy` and the `Agg` functions from [6. Select, aggregates and group by](06-select-group.md):

```kotlin
qb.insert(Summary::class.java).from(Order::class.java).groupBy { it.customer?.id }
    .select { s, o -> s.customerId = o.customer?.id; s.total = Agg.sum(o.price); s.cnt = Agg.count() }
    .execute()
```

---

---

[7. Update, insert, delete (Kotlin)](07-dml.md) | [7.2 Update](07-2-update.md) | [7.4 Delete](07-4-delete.md) | **7.5 Insert** | [Java](../java/07-5-insert.md)
