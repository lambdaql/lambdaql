<a id="76-upsert"></a>
# 7.6 Upsert (Kotlin)

[7. Update, insert, delete (Kotlin)](07-dml.md) | [7.2 Update](07-2-update.md) | [7.4 Delete](07-4-delete.md) | [7.5 Insert](07-5-insert.md) | **7.6 Upsert** | [Java](../java/07-6-upsert.md)

---


An upsert inserts a row. If a row with the same key already exists, it changes that row instead, or it keeps the row as it is.
lambdaQL has three forms:

| Form | Key | When the key exists |
|---|---|---|
| `qb.upsert(X::class.java).values { ... }` | the `@Id` | the other assigned columns get the new values |
| `qb.insert(X::class.java).values { ... }.onConflict { key }.update { ... }` | a column with a primary key or unique constraint | the assignments in `update { ... }` run on the existing row |
| `qb.insert(X::class.java).values { ... }.onConflict { key }.ignore()` | the same | nothing: the existing row stays |

## Short form: `upsert`

```kotlin
// the key is the @Id; on conflict, the other assigned columns get the new values
val inserted = qb.upsert(Archive::class.java).values { it.id = 100L; it.price = 1.0 }.execute()
val again = qb.upsert(Archive::class.java).values { it.id = 100L; it.price = 2.0 }
// HQL: insert into Archive e0 (id, price) values (100L, 2.0) on conflict(id) do update set price = excluded.price
val updated = again.execute()                 // MariaDB counts an updated row as 2, H2 and Oracle as 1
```

**About the SQL blocks.** `…` is the shortened select list (all entity columns), and `?` is a bind parameter. The lines come from [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java). It runs each query on H2 2.3, MariaDB 10.11 and Oracle 23.26 with Hibernate 6.6.5, and it fails if Hibernate's SQL changes. The test uses Java lambdas. A Kotlin lambda with the same meaning gives the same HQL, so it gets the same SQL.

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `upsert`:

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

There are no Kotlin tests yet. The same statements written in Java are tested in [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) (H2, MariaDB and Oracle). See [the Java version of this page](../java/07-6-upsert.md) for the tested examples.

The SQL is different on each database. H2 and Oracle get a `merge` statement: H2 reads the new row from `values (...)`, Oracle from `select 100 id,2.0 price`. MariaDB gets `insert ... on duplicate key update` and reads the new value with `values(price)`.

The row must assign the `@Id`. If it assigns only the `@Id`, there is nothing to change, and lambdaQL writes the `ignore()` form below.

## Your own change: `onConflict(key).update(...)`

Use this form when the change is not "take the new values", for example a counter.

```kotlin
// on conflict, change the existing row: a property read here reads the existing row
val add = qb.insert(Archive::class.java)
    .values { it.id = 100L; it.price = 5.0 }
    .onConflict { it.id }
    .update { it.price = it.price!! + 5.0 }
// HQL: insert into Archive e0 (id, price) values (100L, 5.0) on conflict(id) do update set price = (e0.price + 5.0)
add.execute()                                 // inserted: 5.0
add.execute()                                 // conflict: 5.0 + 5.0
```

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `on-conflict`:

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

In `update { ... }`, a property read reads the **existing** row: `it.price!! + 5.0` is the old price plus 5. In the SQL, H2 and Oracle write the alias (`a1_0.price`), and MariaDB writes the table name (`archive.price`).

The key is a lambda (`{ it.id }`). Kotlin property references (`Archive::id`) are not implemented yet, as in [4. Sorting and paging](04-order-paging.md). The key must be a column of the entity itself, not an association path, and the database needs a primary key or unique constraint on it.

## Keep the existing row: `onConflict(key).ignore()`

```kotlin
val once = qb.insert(Archive::class.java)
    .values { it.id = 300L; it.price = 1.0 }
    .onConflict { it.id }
    .ignore()
// HQL: insert into Archive (id, price) select 300L, 1.0 where not exists (select 1 from Archive x where x.id = 300L)
val first = once.execute()                    // 1: inserted
val second = qb.insert(Archive::class.java).values { it.id = 300L; it.price = 2.0 }
    .onConflict { it.id }.ignore().execute()  // 0: the row exists, nothing changes
```

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `insert-ignore`:

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
> Then one of them gets a duplicate key error. If this can happen in your application, catch the error.

## The row count

`execute()` returns the row count that the database reports, as it is (decision D40). The Java test checks these numbers:

| Database | Inserted | Updated | Ignored |
|---|---|---|---|
| H2 | 1 | 1 | 0 |
| MariaDB | 1 | **2** | 0 |
| Oracle | 1 | 1 | 0 |

So on MariaDB, `execute() == 1` does not mean "updated". Use `execute() > 0` to check that a row was written.

## Many rows

`qb.upsert(X::class.java).values(list) { a, l -> ... }` and `onConflict { ... }` after `values(list) { ... }` run **one statement per row**. `execute()` returns the sum. This is not covered by a test yet.

```kotlin
val n = qb.upsert(Archive::class.java)
    .values(lines) { a, l -> a.id = l.id; a.price = l.price }
    .execute()
```

## Rules

- The row must assign the key. `upsert(X::class.java)` uses the `@Id` as the key.
- The key must be a column of the entity itself. An association path is an error.
- A value in `values { ... }` cannot read the row (it is a new row). Only `update { ... }` reads the existing row.
- The assignment rules of update and insert apply (U1, U3, U4, U6; see [7.9](07-dml.md#79-all-rules-u1-to-u21-summary)).
- There is no upsert from `insert ... select`.
- Like the other DML statements, an upsert needs a transaction and does not change entities already loaded (see [7.8](07-dml.md#transactions)).

---

---

[7. Update, insert, delete (Kotlin)](07-dml.md) | [7.2 Update](07-2-update.md) | [7.4 Delete](07-4-delete.md) | [7.5 Insert](07-5-insert.md) | **7.6 Upsert** | [Java](../java/07-6-upsert.md)
