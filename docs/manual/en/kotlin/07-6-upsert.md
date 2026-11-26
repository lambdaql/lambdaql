<a id="76-upsert"></a>
# 7.6 Upsert (Kotlin)

[7. Update, insert, delete (Kotlin)](07-dml.md) | [7.2 Update](07-2-update.md) | [7.4 Delete](07-4-delete.md) | [7.5 Insert](07-5-insert.md) | **7.6 Upsert** | [Java](../java/07-6-upsert.md)

---

An upsert inserts a row. If a row with the same key already exists, it updates that row instead of inserting, or it leaves that row as it is.
lambdaQL has three forms.

| Form | Key | When the key already exists |
|---|---|---|
| `qb.upsert(X::class.java).values { ... }` | `@Id` | Updates the other assigned columns with the new values |
| `qb.insert(X::class.java).values { ... }.onConflict { key }.update { ... }` | A column with a primary key or unique constraint | Runs the assignments of `update { ... }` on the existing row |
| `qb.insert(X::class.java).values { ... }.onConflict { key }.ignore()` | Same as above | No change: keeps the existing row |

<a id="short-form-upsert"></a>
## Short form: `upsert`

<!-- region: ManualKotlinDmlTest.kt#upsert -->
```kotlin
// the key is the @Id; on conflict, the other assigned columns get the new values
val inserted = qb.upsert(Archive::class.java).values { it.id = 100L; it.price = 1.0 }.execute()
val again = qb.upsert(Archive::class.java).values { it.id = 100L; it.price = 2.0 }
assertEquals("insert into Archive e0 (id, price) values (100L, 2.0) on conflict(id) do update set price = excluded.price",
    again.toHql()[0].hql())
val updated = again.execute()                 // MariaDB and MySQL count an updated row as 2, the others as 1
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

After the second run, the price of the row is the new value `2.0`. The returned row count differs by database (see [The row count](#the-row-count)).

The SQL differs by database. H2 and Oracle take a `merge` statement. H2 reads the new row from `values (...)`, and Oracle from `select 100 id,2.0 price`. MariaDB takes `insert ... on duplicate key update` and reads the new value with `values(price)`.

The row must assign the `@Id`. If you assign only the `@Id`, there is no column to update, so lambdaQL writes it in the `ignore()` form below.

Example code: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt) (`upsert`).

<a id="your-own-change-onconflictkeyupdate"></a>
## Your own change: `onConflict(key).update(...)`

Use this form when the change is not "replace with the new value" (for example, a counter).

<!-- region: ManualKotlinDmlTest.kt#on-conflict -->
```kotlin
// on conflict, change the existing row: a property read here reads the existing row
val add = qb.insert(Archive::class.java)
    .values { it.id = 100L; it.price = 5.0 }
    .onConflict { it.id }
    .update { it.price = it.price + 5.0 }
assertEquals("insert into Archive e0 (id, price) values (100L, 5.0) on conflict(id) do update set price = (e0.price + 5.0)",
    add.toHql()[0].hql())
add.execute()                                 // inserted: 5.0
add.execute()                                 // conflict: 5.0 + 5.0
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

A property read inside `update { ... }` reads the **existing** row. `it.price + 5.0` is the old price plus 5. In the SQL, H2 and Oracle write the alias (`a1_0.price`), and MariaDB writes the table name (`archive.price`).

You give the key as a lambda (`{ it.id }`). The key must be a column of the entity itself, not an association path, and the database must have a primary key or unique constraint on that column.

If you use two-argument lambdas in `values` and `update`, you can build subqueries with the second argument `sub` (measured on three databases). See also [7.5](07-5-insert.md#a-subquery-in-a-value).

<!-- region: ManualKotlinDmlTest.kt#upsert-sub -->
```kotlin
// a subquery in the inserted value and in the update on conflict
val up = qb.insert(Archive::class.java)
    .values { a, sub -> a.id = 100L; a.price = sub.from(Item::class.java).sum { x -> x.price } }
    .onConflict { it.id }
    .update { a, sub -> a.price = sub.from(Order::class.java).max { o -> o.price } }
assertEquals("insert into Archive e0 (id, price) values (100L, (select sum(s1.price) from Item s1))"
    + " on conflict(id) do update set price = (select max(s1.price) from Order s1)", up.toHql()[0].hql())
```

If you run this statement twice, the first run inserts the sum of the item prices, and the second run updates the row to the largest order price, because the key already exists.

Example code: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt) (`on-conflict`, `upsert-sub`).

<a id="keep-the-existing-row-onconflictkeyignore"></a>
## Keep the existing row: `onConflict(key).ignore()`

<!-- region: ManualKotlinDmlTest.kt#insert-ignore -->
```kotlin
val once = qb.insert(Archive::class.java)
    .values { it.id = 300L; it.price = 1.0 }
    .onConflict { it.id }
    .ignore()
assertEquals("insert into Archive (id, price) select 300L, 1.0 where not exists (select 1 from Archive x where x.id = 300L)",
    once.toHql()[0].hql())
val first = once.execute()                    // 1: inserted
val second = qb.insert(Archive::class.java).values { it.id = 300L; it.price = 2.0 }
    .onConflict { it.id }.ignore().execute()  // 0: the row exists, nothing changes
```

**SQL by database**

<!-- sql: insert-ignore -->
```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price)
select 300,1.0
where not exists(select 1 from archive a2_0 where a2_0.id=300)
```

Hibernate's own `on conflict ... do nothing` runs as a normal insert on H2 and Oracle, so it fails with a duplicate key error.
So lambdaQL writes `insert ... select ... where not exists (...)` itself. The SQL is the same on the three databases.

> [!WARNING]
> **`ignore()` is not atomic.** If two transactions insert the same key at the same time, both can pass the `not exists` check.
> Then one of them gets a duplicate key error. If this can happen in your application, handle this error.

Example code: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt) (`insert-ignore`).

<a id="the-row-count"></a>
## The row count

`execute()` returns the row count that the database reports, as it is. The values per database are (measured):

| Database | Insert | Update | Ignore |
|---|---|---|---|
| H2 | 1 | 1 | 0 |
| MariaDB | 1 | **2** | 0 |
| Oracle | 1 | 1 | 0 |

So on MariaDB, `execute() == 1` does not mean "updated". To check that a row was written, use `execute() > 0`.

<a id="many-rows"></a>
## Many rows

`qb.upsert(X::class.java).values(list) { a, l -> ... }` and `onConflict { ... }` after `values(list) { ... }` run **one statement per row**. `execute()` returns the total.
The list form also takes a subquery as the third argument, as in `values(list) { a, l, sub -> ... }` (see [7.5](07-5-insert.md#many-rows-from-a-list)).

<!-- region: ManualKotlinDmlTest.kt#upsert-list -->
```kotlin
// one statement per element; execute() returns the sum
val lines = listOf(Line(201L, 1.0), Line(202L, 2.0))
val first = qb.upsert(Archive::class.java).values(lines) { a, l -> a.id = l.id; a.price = l.price }.execute()
val changed = listOf(Line(202L, 20.0), Line(203L, 3.0))
val second = qb.upsert(Archive::class.java).values(changed) { a, l -> a.id = l.id; a.price = l.price }.execute()
```

The second call updates `202` and inserts `203`. The total is 2 on H2, and 3 on MariaDB and MySQL (an updated row counts as 2).

Example code: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt) (`upsert-list`).

<a id="rules"></a>
## Rules

- The row must assign the key. `upsert(X::class.java)` uses the `@Id` as the key.
- The key must be a column of the entity itself. An association path is an error.
- The values of `values { ... }` cannot read the row (it is a new row). Only `update { ... }` reads the existing row.
- The assignment rules of update and insert apply (see [7.9](07-dml.md#79-all-rules-u1-to-u21-summary)).
- There is no upsert with `insert ... select`.
- Like other DML statements, an upsert needs a transaction, and it is not reflected in entities that are already loaded (see [7.8](07-dml.md#transactions)).

---

[7. Update, insert, delete (Kotlin)](07-dml.md) | [7.2 Update](07-2-update.md) | [7.4 Delete](07-4-delete.md) | [7.5 Insert](07-5-insert.md) | **7.6 Upsert** | [Java](../java/07-6-upsert.md)
