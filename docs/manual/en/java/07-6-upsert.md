<a id="76-upsert"></a>
# 7.6 Upsert (Java)

[7. Update, insert, delete (Java)](07-dml.md) | [7.2 Update](07-2-update.md) | [7.4 Delete](07-4-delete.md) | [7.5 Insert](07-5-insert.md) | **7.6 Upsert** | [Kotlin](../kotlin/07-6-upsert.md)

---

An upsert inserts a row. If a row with the same key already exists, it updates that row instead of inserting, or leaves that row as it is.
lambdaQL has three forms.

| Form | Key | When the key already exists |
|---|---|---|
| `qb.upsert(X.class).values(...)` | `@Id` | Updates the other assigned columns with the new values |
| `qb.insert(X.class).values(...).onConflict(key).update(...)` | A column with a primary key or unique constraint | Runs the assignments of `update(...)` on the existing row |
| `qb.insert(X.class).values(...).onConflict(key).ignore()` | Same as above | No change: keeps the existing row |

<a id="short-form-upsert"></a>
## Short form: `upsert`

<!-- region: DmlQueryTest.java#upsert -->
```java
// the key is the @Id; on conflict, the other assigned columns get the new values
int inserted = qb.upsert(Archive.class).values(a -> { a.setId(100L); a.setPrice(1.0); }).execute();
ExecutableInsert<Archive> again = qb.upsert(Archive.class).values(a -> { a.setId(100L); a.setPrice(2.0); });
assertEquals("insert into Archive e0 (id, price) values (100L, 2.0) on conflict(id) do update set price = excluded.price",
        again.toHql().get(0).hql());
int updated = again.execute();                     // MariaDB and MySQL count an updated row as 2, the others as 1
```

**SQL by database**

<!-- sql: upsert -->
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

The SQL is different on each database. H2 and Oracle take a `merge` statement. H2 reads the new row from `values (...)`, and Oracle from `select 100 id,2.0 price`. MariaDB takes `insert ... on duplicate key update` and reads the new value with `values(price)`.

The row must have an `@Id` assignment. If only the `@Id` is assigned, there is no column to update, so lambdaQL writes it in the `ignore()` form below.

Example code: [`DmlQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) (`upsert`).

<a id="your-own-change-onconflictkeyupdate"></a>
## Your own change: `onConflict(key).update(...)`

Use this form when the change is not "replace with the new value" (for example, a counter).

<!-- region: DmlQueryTest.java#on-conflict -->
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

**SQL by database**

<!-- sql: on-conflict -->
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

The getters inside `update(...)` read the **existing** row. `a.getPrice() + 5.0` is the old price plus 5. In the SQL, H2 and Oracle use the alias (`a1_0.price`), and MariaDB uses the table name (`archive.price`).

Give the key as a getter lambda expression (`a -> a.getId()`) or a getter method reference (`Archive::getId`). The key must be a column of the entity itself, not an association path, and the database must have a primary key or unique constraint on that column.

If you use lambda expressions with two arguments in `values` and `update`, you can build a subquery with the second argument `sub` (measured on three databases). For an example, see [7.5](07-5-insert.md#a-subquery-in-a-value).

Example code: [`DmlQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) (`on-conflict`).

<a id="keep-the-existing-row-onconflictkeyignore"></a>
## Keep the existing row: `onConflict(key).ignore()`

<!-- region: DmlQueryTest.java#insert-ignore -->
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

**SQL by database**

<!-- sql: insert-ignore -->
```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price)
select 300,1.0
where not exists(select 1 from archive a2_0 where a2_0.id=300)
```

Hibernate's own `on conflict ... do nothing` runs as a plain insert on H2 and Oracle and fails with a duplicate key error.
So lambdaQL writes `insert ... select ... where not exists (...)` itself. The SQL is the same on the three databases.

> [!WARNING]
> **`ignore()` is not atomic.** If two transactions insert the same key at the same time, both can pass the `not exists` check.
> Then one of them gets a duplicate key error. If this can happen in your application, handle this error.

Example code: [`DmlQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) (`insert-ignore`).

<a id="the-row-count"></a>
## The row count

`execute()` returns the row count that the database reports, as it is. The values by database are (measured):

| Database | Insert | Update | Ignore |
|---|---|---|---|
| H2 | 1 | 1 | 0 |
| MariaDB | 1 | **2** | 0 |
| Oracle | 1 | 1 | 0 |

So on MariaDB, `execute() == 1` does not mean "updated". To check that a row was written, use `execute() > 0`.

<a id="many-rows"></a>
## Many rows

`qb.upsert(X.class).values(list, (a, l) -> ...)` and `onConflict(...)` after `values(list, ...)` run **one statement per row**. `execute()` returns the sum. If you run it again with the same keys, no rows are added; only the values change.
The list form also takes a subquery with the third argument, like `values(list, (a, l, sub) -> ...)` (see [7.5](07-5-insert.md#many-rows-from-a-list)).

<!-- region: ManualJavaDmlTest.java#upsert-list -->
```java
// one statement per row; execute() returns the sum
List<Line> lines = List.of(new Line(501, 1.0), new Line(502, 2.0));
int n = qb.upsert(Archive.class)
        .values(lines, (a, l) -> { a.setId(l.id()); a.setPrice(l.price()); })
        .execute();
```

Example code: [`ManualJavaDmlTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaDmlTest.java) (`upsert-list`).

<a id="rules"></a>
## Rules

- The row must have a key assignment. `upsert(X.class)` uses the `@Id` as the key.
- The key must be a column of the entity itself. An association path is an error.
- The values of `values(...)` cannot read the row (because it is a new row). The existing row is read only in `update(...)`.
- The assignment rules of update and insert apply (see [7.9](07-dml.md#79-all-rules-u1-to-u21-summary)).
- There is no upsert with `insert ... select`.
- Like other DML statements, an upsert needs a transaction and is not reflected in entities that are already loaded (see [7.8](07-dml.md#transactions)).

---

[7. Update, insert, delete (Java)](07-dml.md) | [7.2 Update](07-2-update.md) | [7.4 Delete](07-4-delete.md) | [7.5 Insert](07-5-insert.md) | **7.6 Upsert** | [Kotlin](../kotlin/07-6-upsert.md)
