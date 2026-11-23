<a id="76-upsert"></a>
# 7.6 Upsert (Java)

[7. Update, insert, delete (Java)](07-dml.md) | [7.2 Update](07-2-update.md) | [7.4 Delete](07-4-delete.md) | [7.5 Insert](07-5-insert.md) | **7.6 Upsert** | [Kotlin](../kotlin/07-6-upsert.md)

---


An upsert inserts a row. If a row with the same key already exists, it changes that row instead, or it keeps the row as it is.
lambdaQL has three forms.

| Form | Key | When the key exists |
|---|---|---|
| `qb.upsert(X.class).values(...)` | the `@Id` | the other assigned columns get the new values |
| `qb.insert(X.class).values(...).onConflict(key).update(...)` | a column with a primary key or unique constraint | the assignments in `update(...)` run on the existing row |
| `qb.insert(X.class).values(...).onConflict(key).ignore()` | the same as above | no change: the existing row stays |

<a id="short-form-upsert"></a>
## Short form: `upsert`

**(tested)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java), region `upsert`:

```java
// the key is the @Id; on conflict, the other assigned columns get the new values
int inserted = qb.upsert(Archive.class).values(a -> { a.setId(100L); a.setPrice(1.0); }).execute();
ExecutableInsert<Archive> again = qb.upsert(Archive.class).values(a -> { a.setId(100L); a.setPrice(2.0); });
assertEquals("insert into Archive e0 (id, price) values (100L, 2.0) on conflict(id) do update set price = excluded.price",
        again.toHql().get(0).hql());
int updated = again.execute();                     // MariaDB and MySQL count an updated row as 2, the others as 1
```

**About the SQL blocks.** `…` is the shortened select list (all entity columns), and `?` is a bind parameter. The lines come from [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java). It runs each query on H2 2.3, MariaDB 10.11 and Oracle 23.26 with Hibernate 7.4, and it fails if Hibernate's SQL changes.

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `upsert`:

```sql
-- H2
merge into archive a1_0 using (values (100,2.0)) excluded(id,price) on (a1_0.id=excluded.id) when matched then update
set price=excluded.price when not matched then insert (id,price)
values (excluded.id,excluded.price)

-- MariaDB
insert into archive(id,price)
values (100,2.0) on duplicate key update price=values(price)

-- Oracle
merge into archive a1_0 using (select 100 id,2.0 price) excluded on (a1_0.id=excluded.id) when matched then update
set a1_0.price=excluded.price when not matched then insert (id,price)
values (excluded.id,excluded.price)
```

The SQL is different on each database. H2 and Oracle get a `merge` statement. H2 reads the new row from `values (...)`, and Oracle reads it from `select 100 id,2.0 price`. MariaDB gets `insert ... on duplicate key update` and reads the new value with `values(price)`.

The row must assign the `@Id`. If it assigns only the `@Id`, there is no column to change, so lambdaQL writes the `ignore()` form below.

<a id="your-own-change-onconflictkeyupdate"></a>
## Your own change: `onConflict(key).update(...)`

Use this form when the change is not "replace with the new values" (for example, a counter).

**(tested)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java), region `on-conflict`:

```java
// on conflict, change the existing row: a getter here reads the existing row
ExecutableInsert<Archive> add = qb.insert(Archive.class)
        .values(a -> { a.setId(100L); a.setPrice(5.0); })
        .onConflict(a -> a.getId())
        .update(a -> a.setPrice(a.getPrice() + 5.0));
assertEquals("insert into Archive e0 (id, price) values (100L, 5.0) on conflict(id) do update set price = (e0.price + 5.0)",
        add.toHql().get(0).hql());
add.execute();                                     // inserted: 5.0
add.execute();                                     // conflict: 5.0 + 5.0
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `on-conflict`:

```sql
-- H2
merge into archive a1_0 using (values (100,5.0)) excluded(id,price) on (a1_0.id=excluded.id) when matched then update
set price=(a1_0.price+5.0) when not matched then insert (id,price)
values (excluded.id,excluded.price)

-- MariaDB
insert into archive(id,price)
values (100,5.0) on duplicate key update price=(archive.price+5.0)

-- Oracle
merge into archive a1_0 using (select 100 id,5.0 price) excluded on (a1_0.id=excluded.id) when matched then update
set a1_0.price=(a1_0.price+5.0) when not matched then insert (id,price)
values (excluded.id,excluded.price)
```

In `update(...)`, a getter reads the **existing** row. `a.getPrice() + 5.0` is the old price plus 5. In the SQL, H2 and Oracle write the alias (`a1_0.price`), and MariaDB writes the table name (`archive.price`).

The key is a getter lambda (`a -> a.getId()`) or a getter method reference (`Archive::getId`). The key must be a column of the entity itself, not an association path, and the database needs a primary key or unique constraint on that column.

With a two-argument lambda in `values` and `update`, you can build a subquery from the second argument `sub` (decision D49, measured on the three databases). See [7.5](07-5-insert.md#a-subquery-in-a-value) for an example.

<a id="keep-the-existing-row-onconflictkeyignore"></a>
## Keep the existing row: `onConflict(key).ignore()`

**(tested)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java), region `insert-ignore`:

```java
ExecutableInsert<Archive> once = qb.insert(Archive.class)
        .values(a -> { a.setId(300L); a.setPrice(1.0); })
        .onConflict(a -> a.getId())
        .ignore();
assertEquals("insert into Archive (id, price) select 300L, 1.0 where not exists (select 1 from Archive x where x.id = 300L)",
        once.toHql().get(0).hql());
int first = once.execute();                        // 1: inserted
int second = qb.insert(Archive.class).values(a -> { a.setId(300L); a.setPrice(2.0); })
        .onConflict(a -> a.getId()).ignore().execute();   // 0: the row exists, nothing changes
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `insert-ignore`:

```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price)
select 300,1.0
where not exists(select 1 from archive a2_0 where a2_0.id=300)
```

Hibernate's own `on conflict ... do nothing` runs as a plain insert on H2 and Oracle, and it fails there with a duplicate key error.
So lambdaQL writes `insert ... select ... where not exists (...)` itself. The SQL is the same on all three databases.

> [!WARNING]
> **`ignore()` is not atomic.** Two transactions that insert the same key at the same moment can both pass the `not exists` check.
> Then one of them gets a duplicate key error. If this can happen in your application, handle this error.

<a id="the-row-count"></a>
## The row count

`execute()` returns the row count that the database reports, as it is (decision D40). The test checks these values (measured).

| Database | Inserted | Updated | Ignored |
|---|---|---|---|
| H2 | 1 | 1 | 0 |
| MariaDB | 1 | **2** | 0 |
| Oracle | 1 | 1 | 0 |

So on MariaDB, `execute() == 1` does not mean "updated". To check that a row was written, use `execute() > 0`.

<a id="many-rows"></a>
## Many rows

`qb.upsert(X.class).values(list, (a, l) -> ...)` and `onConflict(...)` after `values(list, ...)` run **one statement per row**. `execute()` returns the sum. This form has no test yet (not measured).
The list form also takes a subquery from the third argument, as in `values(list, (a, l, sub) -> ...)` (see [7.5](07-5-insert.md#many-rows-from-a-list)).

```java
int n = qb.upsert(Archive.class)
    .values(lines, (a, l) -> { a.setId(l.id()); a.setPrice(l.price()); })
    .execute();
```

<a id="rules"></a>
## Rules

- The row must assign the key. `upsert(X.class)` uses the `@Id` as the key.
- The key must be a column of the entity itself. An association path is an error.
- A value in `values(...)` cannot read the row, because it is a new row. Only `update(...)` reads the existing row.
- The assignment rules of update and insert apply (U1, U3, U4, U6. See [7.9](07-dml.md#79-all-rules-u1-to-u21-summary)).
- There is no upsert with `insert ... select`.
- Like the other DML statements, an upsert needs a transaction, and it does not change entities already loaded (see [7.8](07-dml.md#transactions)).

---

---

[7. Update, insert, delete (Java)](07-dml.md) | [7.2 Update](07-2-update.md) | [7.4 Delete](07-4-delete.md) | [7.5 Insert](07-5-insert.md) | **7.6 Upsert** | [Kotlin](../kotlin/07-6-upsert.md)
