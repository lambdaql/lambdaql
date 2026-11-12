<a id="75-insert"></a>
# 7.5 Insert (Java)

> [!NOTE]
> **Status: designed, not implemented yet.**
> This chapter is a decided design (decision D28, milestone M10).
> The syntax compiles in Java, and the SQL was checked on H2, MariaDB and Oracle with Hibernate 6.6.5.
> It does **not** run in lambdaQL today. Today only the analysis core and `where` + `toList()` work
> (tested on H2, MariaDB and Oracle). See the [Java track README](README.md#status) for the full status.

[7. Update, insert, delete (Java)](07-dml.md) | [7.2 Update](07-2-update.md) | [7.4 Delete](07-4-delete.md) | **7.5 Insert** | [Kotlin](../kotlin/07-5-insert.md)

---


## One row

```java
qb.insert(Order.class).values(o -> { o.setId(id); o.setPrice(p); o.setStatus(1); }).execute();
```

## Many rows from a list

One row for each element of the list. The second lambda argument is the element.

```java
qb.insert(Order.class)
  .values(lines, (o, l) -> { o.setId(l.id()); o.setPrice(l.price()); o.setStatus(1); })
  .execute();
```

lambdaQL writes one statement with many VALUES rows. If the list is large, it splits the work by the **number of bind values** (see [7.7](07-dml.md#batching-for-many-rows)).

## Generated id

If the id uses IDENTITY or SEQUENCE, you can leave out the id (rule U12):

```java
qb.insert(IdOrder.class).values(o -> { o.setPrice(p); o.setStatus(1); }).execute();
```

## Insert ... select

Copy rows from another entity. The `select` lambda gets the target (`a`) and the source (`o`).

```java
qb.insert(Archive.class).from(Order.class).where(o -> o.getStatus() == 9)
  .select((a, o) -> { a.setId(o.getId()); a.setPrice(o.getPrice()); a.setCustomerName(o.getCustomer().getName()); })
  .execute();
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

With `by.set`, the context object is the third argument:

```java
qb.insert(Archive.class).from(Order.class).where(o -> o.getStatus() == 9)
  .select((a, o, by) -> by.set(a.getId(), o.getId()).set(a.getCustomerName(), o.getCustomer().getName()))
  .execute();
```

## Insert ... select with aggregates

Use `groupBy` and the `Agg` functions from [6. Select, aggregates and group by](06-select-group.md):

```java
qb.insert(Summary.class).from(Order.class).groupBy(o -> o.getCustomer().getId())
  .select((s, o) -> { s.setCustomerId(o.getCustomer().getId()); s.setTotal(Agg.sum(o.getPrice())); s.setCnt(Agg.count()); })
  .execute();
```

---

---

[7. Update, insert, delete (Java)](07-dml.md) | [7.2 Update](07-2-update.md) | [7.4 Delete](07-4-delete.md) | **7.5 Insert** | [Kotlin](../kotlin/07-5-insert.md)
