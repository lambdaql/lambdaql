<a id="74-delete"></a>
# 7.4 Delete (Java)

> [!NOTE]
> **Status: designed, not implemented yet.**
> This chapter is a decided design (decision D28, milestone M10).
> The syntax compiles in Java, and the SQL was checked on H2, MariaDB and Oracle with Hibernate 6.6.5.
> It does **not** run in lambdaQL today. Today only the analysis core and `where` + `toList()` work
> (tested on H2, MariaDB and Oracle). See the [Java track README](README.md#status) for the full status.

[7. Update, insert, delete (Java)](07-dml.md) | [7.2 Update](07-2-update.md) | **7.4 Delete** | [7.5 Insert](07-5-insert.md) | [Kotlin](../kotlin/07-4-delete.md)

---


```java
qb.delete(Order.class).where(o -> o.getStatus() == 9).execute();

qb.delete(Order.class).where(o -> o.getCustomer().getName() == null).execute();

qb.delete(Order.class)
  .where((o, sub) -> !sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).exists())
  .execute();

qb.delete(Order.class).all().execute();     // delete all rows: all() is required
```

HQL for the second example:

```sql
delete from Order o
where exists (select 1 from Customer c where c.id = o.customer.id and c.name is null)
```

**About the SQL blocks.** `…` is the shortened select list (all entity columns), and `?` is a bind parameter. [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java) gives the HQL of this page to Hibernate directly and records the SQL on H2 2.3, MariaDB 10.11 and Oracle 23.26 (Hibernate 6.6.5). lambdaQL does not make these queries yet. The test fails if Hibernate's SQL changes.

**SQL by database (planned)** — Hibernate's translation of the planned HQL, recorded by `PlannedSqlTest`; lambdaQL does not make this query yet. From `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql), example `07-delete-exists`:

```sql
-- H2, Oracle
delete from orders o1_0
where exists(select 1 from customers c1_0 where c1_0.id=o1_0.customer_id and c1_0.name is null)

-- MariaDB
delete o1_0 from orders o1_0
where exists(select 1 from customers c1_0 where c1_0.id=o1_0.customer_id and c1_0.name is null)
```

MariaDB writes `delete o1_0 from orders o1_0`. H2 and Oracle write `delete from orders o1_0`.

This deletes **the same rows** that `qb.from(Order.class).where(o -> o.getCustomer().getName() == null)` returns: orders whose customer has no name.
Orders **without** a customer are not deleted (rule U9, decision D36). To delete them too, write `o.getCustomer() == null || o.getCustomer().getName() == null`.

Delete by a list of ids (captured collection, decision D29):

```java
qb.delete(Item.class).where(i -> ids.contains(i.getId())).execute();
```

---

---

[7. Update, insert, delete (Java)](07-dml.md) | [7.2 Update](07-2-update.md) | **7.4 Delete** | [7.5 Insert](07-5-insert.md) | [Kotlin](../kotlin/07-4-delete.md)
