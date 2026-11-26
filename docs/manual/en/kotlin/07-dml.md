<a id="7-update-insert-delete-kotlin"></a>
# 7. Update, insert, delete (Kotlin)

> [!IMPORTANT]
> This page is older than the Korean manual. See the [Korean page](../../ko/kotlin/07-dml.md) for the latest rules.

[Kotlin track](README.md) | [Manual home](../README.md) | Previous: [6. Select, aggregates and group by](06-select-group.md) | Next: [8. Procedures and DB functions](08-procedures-functions.md) | [Java version of this page](../java/07-dml.md)

Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md)

lambdaQL has five entry points.

| Entry point | Use |
|---|---|
| `qb.from(X::class.java)` | Select (chapters 3 to 6) |
| `qb.update(X::class.java)` | Update rows |
| `qb.insert(X::class.java)` | Insert rows |
| `qb.delete(X::class.java)` | Delete rows |
| `qb.upsert(X::class.java)` | Insert a row. If a row with the same `@Id` exists, update that row |

The basic ideas are the same as for queries.

- You write each part as **one lambda**. For a `val` property, assign the value with `Dml.value` ([7.3](#73-two-ways-to-assign-property-or-dmlvalue)).
- lambdaQL does not call the lambda. It reads the bytecode opcodes. A property assignment such as `it.status = 1` compiles to a setter call and becomes `set e0.status = 1`.
- `where` uses the same rules as [3. Conditions (`where`)](03-where.md). The target rows are the same as the rows selected with the same lambda.
- `execute()` runs the statement and returns the **number of affected rows** (`Int`).
- `executeOne()` runs an update or delete that must change **at most one row** (see [One-row protection](#one-row-protection)).
- `orderBy { ... }.limit(n)` changes only the first `n` rows (see [limit](#limit-change-the-first-rows)).
- `toHql()` returns the HQL and parameters without running the statement.
- Running a statement needs a transaction (see [Transactions](#transactions)).

<a id="contents"></a>
## Contents

- [7.1 Example entities](#71-example-entities)
- [7.2 Update](#72-update)
- [7.3 Two ways to assign: property or `Dml.value`](#73-two-ways-to-assign-property-or-dmlvalue)
- [7.4 Delete](#74-delete)
- [7.5 Insert](#75-insert)
- [7.6 Upsert](#76-upsert)
- [7.7 Kotlin notes for DML](#77-kotlin-notes-for-dml)
- [7.8 Important rules](#78-important-rules)
- [7.9 All rules U1 to U21 (summary)](#79-all-rules-u1-to-u21-summary)
- [7.10 Not in v0.1](#710-not-in-v01)

---

<a id="71-example-entities"></a>
## 7.1 Example entities

This chapter uses `Order`, `Customer` and `Item` (see [2. Getting started](02-getting-started.md#example-entities)).
The examples use the Java example entities in [`query/src/test/java/org/lambdaql/query`](https://github.com/lambdaql/lambdaql/tree/main/query/src/test/java/org/lambdaql/query) from Kotlin as they are.
Kotlin sees Java getters and setters as properties, so you assign as in `it.status = st`.

| Class | Properties used in this chapter |
|---|---|
| `Order` | `id` (IDENTITY), `price` (`double`), `status` (`short`), `active` (`boolean`, `isActive` in Kotlin), `description`, `updateAt`, `kind` (enum `Order.Kind`), `customer`. `total` (`Double`), `cnt` (`Integer`), `custName` and `address` (`@Embedded` `Address`: `city`, `zip`), which can be NULL |
| `VOrder` | `id`, `status` (`Integer`), `version` (`@Version`) |
| `Archive` | `id` (no generator), `price`, `customerName` |
| `Summary` | `customerId` (`Integer`), `total`, `cnt` (`Long`) |
| `IdOrder` | `id` (IDENTITY), `price`, `status` |

`Line` and `Ref`, used for list inserts, are normal data classes (for example, `data class Line(val id: Long, val price: Double)`).
`status` is a `short`, so conditions are written as `it.status.toInt() == 1`.

---

<a id="72-update"></a>
## 7.2 Update

→ [7.2 Update](07-2-update.md)

<a id="73-two-ways-to-assign-property-or-dmlvalue"></a>
## 7.3 Two ways to assign: property or `Dml.value`

| Way | When to use | Example |
|---|---|---|
| Property assignment | When the property is a `var` (it has a setter). The compiler checks the type of the value. | `set { it.status = 1 }` |
| `Dml.value(column, value)` | When the property is a `val` (no setter). For example, DDD-style entities. | `set { o -> value(o.status, 1) }` |

`value` is the function `org.lambdaql.Dml.value`. Import it with `import org.lambdaql.Dml.value` and use it inside a lambda with one argument.

<!-- region: KotlinQueryTest.kt#update-value -->
```kotlin
// entities without setters (val): Dml.value(column, value)
val u = qb.update(Order::class.java)
    .set { o -> value(o.description, "done") }
    .where { it.status.toInt() == 2 }
assertEquals("update Order e0 set e0.description = 'done' where e0.status = 2", u.toHql().hql())
```

The `status` of the example entity is a `short`, so the condition is written as `it.status.toInt() == 2`. The Java form of the same statement is in the [Java version of this page](../java/07-dml.md#73-two-ways-to-assign-setter-or-dmlvalue).

**SQL by database**

<!-- sql: update-value -->
```sql
-- H2, MariaDB
update orders o1_0
set description='done'
where o1_0.status=2

-- Oracle
update orders o1_0
set o1_0.description='done'
where o1_0.status=2
```

Oracle writes the alias in `set` (`set o1_0.status=?`). H2 and MariaDB do not.

Every example in this chapter can also be written with `value`. Write one `value` call per Kotlin statement.

<!-- region: ManualKotlinDmlTest.kt#value-forms -->
```kotlin
// Dml.value(column, value) for properties without a setter (val); one value call per statement
val u = qb.update(Order::class.java)
    .set { o -> value(o.status, st); value(o.price, o.price * 1.1) }
    .where { it.status.toInt() == 1 }
assertEquals("update Order e0 set e0.status = :p1, e0.price = (e0.price * 1.1) where e0.status = 1", u.toHql().hql())
val embedded = qb.update(Order::class.java).set { o -> value(o.address.city, city) }.where { it.id == id }
assertEquals("update Order e0 set e0.address.city = :p1 where e0.id = :p2", embedded.toHql().hql())
val fk = qb.update(Order::class.java).set { o -> value(o.customer?.id, cid) }.where { it.id == id }
assertEquals("update Order e0 set e0.customer.id = :p1 where e0.id = :p2", fk.toHql().hql())
```

Rules for `value`:

- The first argument must be **a property read (getter call) that starts from the target**: a property, an embedded path, or the `@Id` of an association.
  A captured value, a computed expression or a path of the source entity is an analysis-time error (`LQL-3039`).
- The setter rules of the same property apply as they are. `value(o.x, v)` is the same as `o.x = v`, so U1 and U6 apply, and for an association you can assign only the `@Id` (`LQL-3050`).
- You **can mix** property assignments and `value` **in one lambda**.
- `value(column, value)` is generic, so the compiler does not check that the value fits the column.
  lambdaQL compares the column type and the value type at analysis time and gives the error `LQL-3065` if they do not match.
  Numbers fit number columns. But a decimal does not fit an integer column. Values whose type is unknown (`Any`, `Number` of a generic aggregate) are not compared.
- Inside a class that has its own `value(...)` function, the imported `value` is hidden. Write `Dml.value(...)` there.
- Assign a `val` property with `value(...)`.

<a id="subquery-in-a-set-value"></a>
### Subquery in a `set` value

The second argument of the assignment lambda is `sub`, the starting point of subqueries. The notation is the same as `where { o, sub -> ... }`.
In `insert ... select`, the third argument is `sub` (see [7.5](07-5-insert.md)).

<!-- region: KotlinQueryTest.kt#update-sub -->
```kotlin
// the second parameter starts a subquery, as in where { o, sub -> ... }
val u = qb.update(Order::class.java)
    .set { o, sub -> o.price = sub.from(Item::class.java).where { i -> i.order?.id == o.id }.sum { it.price } }
    .where { o, sub -> sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
assertEquals("update Order e0 set e0.price = (select sum(s1.price) from Item s1 where s1.order.id = e0.id)"
        + " where exists (select 1 from Item s1 where s1.order.id = e0.id)", u.toHql().hql())
```

- You can use both an aggregate of another table and an aggregate of the same table as a `set` value (measured on H2, MariaDB 10.11 and Oracle 23.26). In `update`, MariaDB does not give error 1093 either.
- If no row matches, `sum` and the others return NULL. For a NOT NULL column this is a database error, so add `?: 0.0`. It becomes `coalesce(...)` (see [7.2](07-2-update.md#an-aggregate-from-a-subquery)).
- You use subqueries in the same way in insert `values { a, sub -> ... }` and upsert (`onConflict { ... }.update { a, sub -> ... }`) (see [7.5](07-5-insert.md#a-subquery-in-a-value)). In the list form, the third argument is `sub`, as in `values(list) { a, l, sub -> ... }`.

Example code: [`KotlinQueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt) (`update-value`, `update-sub`), [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt) (`value-forms`).

---

<a id="74-delete"></a>
## 7.4 Delete

→ [7.4 Delete](07-4-delete.md)

<a id="75-insert"></a>
## 7.5 Insert

→ [7.5 Insert](07-5-insert.md)

<a id="76-upsert"></a>
## 7.6 Upsert

→ [7.6 Upsert](07-6-upsert.md)

<a id="77-kotlin-notes-for-dml"></a>
## 7.7 Kotlin notes for DML

| Kotlin form | Meaning |
|---|---|
| `it.status = x` | Setter call: `set e0.status = :p1` |
| `a ?: b` (string or number value) | `coalesce(a, b)` |
| `it.customer?.id = x` | Always assigns (`?.` is ignored) |
| `it.cnt += 1` | Supported (`set e0.cnt = e0.cnt + 1`) |
| `value(o.status, x)` | Assignment for a `val` property. One `value` per statement |
| `value(o.cnt, o.cnt + 1)` | Supported (`set e0.cnt = e0.cnt + 1`) |
| `it.cnt++`, `it.cnt--` | Rejected (`LQL-2003`). Use `+= 1` |
| `it.apply { }`, `with(it) { }`, `it.also { }` | Rejected (`LQL-2003`). Assign directly |
| `if` / `when` that picks a value, toggle `!it.isActive` | `case when` ([3.5.1](03-where.md#351-values-chosen-by-a-condition)) |
| `if (c) it.price = x` that picks whether to assign | Rejected (`LQL-2009`). Write the condition in `where`, or call `set()` more than once |
| A local `val` in the lambda | Rejected (`LQL-2003`). It can hide the problem of reading an assigned column again |

<!-- region: KotlinCaseWhenTest.kt#set-toggle -->
```kotlin
// a toggle and a number elvis are values too
val toggle = qb.update(Order::class.java).set { it.isActive = !it.isActive }.all()
assertEquals("update Order e0 set e0.active = case when e0.active = false then true else false end", toggle.toHql().hql())
val zero = qb.update(Order::class.java).set { it.cnt = it.cnt ?: 0 }.all()
assertEquals("update Order e0 set e0.cnt = coalesce(e0.cnt, 0)", zero.toHql().hql())
// an if that chooses whether to assign is rejected (LQL-2009): put that condition in where
val e = assertThrows(LambdaQLException::class.java) {
    qb.update(Order::class.java).set { if (it.isActive) it.price = 1.0 }
}
assertEquals("LQL-2009", e.code())
```

Example code: [`KotlinCaseWhenTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinCaseWhenTest.kt) (`set-toggle`).

More about Kotlin: [9. Kotlin notes](09-kotlin-notes.md).

---

<a id="78-important-rules"></a>
## 7.8 Important rules

<a id="the-right-side-reads-the-old-value"></a>
### The right side reads the old value

In SQL, every column on the right side of SET means the value **before** the update. lambdaQL follows this rule.

<!-- region: ManualKotlinDmlTest.kt#old-value -->
```kotlin
// the right side reads the value before the change
val self = qb.update(Order::class.java).set { it.price = it.price * 1.1 }.all()             // reads itself: allowed
val readFirst = qb.update(Order::class.java).set { it.total = it.price; it.price = 0.0 }.all() // read, then assign: allowed
assertEquals("update Order e0 set e0.total = e0.price, e0.price = 0.0", readFirst.toHql().hql())
// assign, then read again: an error at set(), because MariaDB would read the new value
val error = assertThrows(LambdaQLException::class.java) {
    qb.update(Order::class.java).set { it.price = it.price * 2; it.total = it.price }
}
assertEquals("LQL-3042", error.code())
```

**If you assign a column and later read that column in the same statement**, lambdaQL throws `TranslationException` (`LQL-3042`) when you call `set(...)`.
Why: in this case MariaDB uses the **new** value, and H2 and Oracle use the old value. The results differ (measured: MariaDB 200, H2 and Oracle 100).

The following cases give the same result on all three databases, so they are allowed.

| Form | Allowed |
|---|---|
| `it.price = it.price * 1.1` (reads itself) | Allowed |
| `it.total = it.price; it.price = 0.0` (read first, assign later) | Allowed |
| `it.price = ...; it.total = it.price` (assign first, read later) | **Error** |

This check works per physical column (embedded sub-columns, FK columns), and it also applies across many `set()` calls.

<a id="assign-a-column-only-once"></a>
### Assign a column only once

Assigning the same column twice is an error. This includes `it.address.city = ...` after `it.address = ...`,
and `it.customer?.id = ...` after `it.customer = ...`.

<a id="rules-checked-at-analysis-time"></a>
### Rules checked at analysis time

lambdaQL checks the assignment rules when it reads the lambda, and it throws `LambdaQLException` from `set(...)` or `values(...)` (for example, `LQL-3037`, `LQL-3042`, `LQL-3041`).
The following forms written as Kotlin lambdas are rejected when the lambda is read. Examples of reading an assigned column again and of assigning the same column twice are in [The right side reads the old value](#the-right-side-reads-the-old-value) and [7.2](07-2-update.md#many-set-calls-patch).

<!-- region: ManualKotlinDmlTest.kt#assign-rules -->
```kotlin
// forms that the assignment rules reject when the lambda is read
val increment = assertThrows(LambdaQLException::class.java) {
    qb.update(Order::class.java).set { it.cnt++ }                       // ++: write += 1
}
val scope = assertThrows(LambdaQLException::class.java) {
    qb.update(Order::class.java).set { it.apply { status = 1 } }        // apply: assign directly
}
val other = assertThrows(LambdaQLException::class.java) {
    qb.update(Order::class.java).set { it.customer?.name = "x" }        // a property of another table
}
val noNull = assertThrows(LambdaQLException::class.java) {
    qb.update(Archive::class.java).set { it.id = null }.all()            // null into the @Id
}
```

`++`, scope functions and local variables are `UnsupportedLambdaShapeException` (`LQL-2003`). A property of an association that is not the `@Id` is `LQL-3050`, and `null` assigned to an `@Id` is `LQL-3044`.

<a id="where-or-all-is-required"></a>
### `where` or `all()` is required

To run an update or delete, you must give `where(...)` or `all()`.

<a id="association-paths-same-rows-as-select"></a>
### Association paths: same rows as select

In DML, lambdaQL writes an association path that is not an FK as a **correlated scalar subquery**. This is the same in `where` and on the right side of SET.
Example: `where { it.customer?.name == "Kim" }` becomes `where (select s1.name from Customer s1 where s1.id = fk(e0.customer)) = 'Kim' and e0.customer.id is not null`, and `it.custName = it.customer?.name` becomes `set e0.custName = (select s1.name from Customer s1 where s1.id = fk(e0.customer))`. The example is in [7.2](07-2-update.md#a-value-from-another-table).

The reason is as follows. If lambdaQL passed the path to Hibernate as it is, Hibernate would make different join SQL for each database
(`merge` on H2, `update ... join` on MariaDB, an inline view on Oracle). Also, with a path in SET, **rows without a customer were dropped**
(measured: `.all()` updated only 9 of 10 rows).
lambdaQL writes the subquery itself. Then the statement reads only its own table, so it does not hit MariaDB's limit on reading the target table (error 1093).

For a path that is an inner join in the `where` of a select, lambdaQL adds `e0.customer.id is not null` in DML.
As a result, `update` and `delete` target **exactly the rows** that `qb.from(X::class.java).where { same lambda }` returns (the deleted rows were compared with the select result).
For a path that is a `left join` in a select, such as a null guard (`it.customer == null || it.customer!!.name == ...`) or `it.status == 1 || ...`, this check is not added. It is the same whether you write `?.` or `!!`. Here too, DML targets the same rows as the select (see [7.2](07-2-update.md), [7.4](07-4-delete.md)).

A path that ends with `@Id` (`it.customer?.id`) is the FK column. There is no subquery and no join.

<a id="batching-for-many-rows"></a>
### Batching for many rows

`values(list) { ... }` writes one statement with many VALUES rows.

| Item | Rule |
|---|---|
| Split unit | The number of values (rows x columns), not the number of rows |
| Limit | At most 500 values per statement (fixed) |
| Return value | The sum over all statements |
| Transaction | All statements run in one transaction |
| Empty list | No SQL, returns 0 |

Oracle has a hard limit of 65,535 bind values (ORA-01745). Parsing becomes very slow long before that
(measured in the design phase: about 6 minutes for 21,845 rows). So the limit is kept small.

<a id="limit-change-the-first-rows"></a>
### limit: change the first rows

If you add `orderBy { ... }.limit(n)` after `where { ... }` or `all()`, the update or delete changes at most `n` rows. The targets are the first `n` rows of `qb.from(X::class.java).where { same }.orderBy { same }`.

| Item | Rule |
|---|---|
| Order | The `orderBy` keys, then the id. Without `orderBy`, id order |
| `orderBy` without `limit` | Error (`IllegalStateException`) |
| Rendering | `e0.id in (select t.id from (select x0.id as id from X x0 ... order by ... limit n) t)` |
| Target rows | The inner select follows the select rules, including joins, so the rows are the same as the select |
| MariaDB | The derived table `t` avoids errors 1093 and 1235 |

Examples and SQL by database: [7.4](07-4-delete.md#delete-the-first-rows-limit) (delete), [7.2](07-2-update.md#update-the-first-rows-limit) (update).

<a id="one-row-protection"></a>
### One-row protection

If you call `executeOne()` instead of `execute()`, it changes **at most one row**.

1. lambdaQL reads at most 2 ids of the rows that match the condition.
2. If there are 2 ids, it changes nothing and throws `NonUniqueResultException`.
3. If there is 1 id, it changes that row with `id = :p and (condition)`.

It never changes two or more rows, and it does not depend on a rollback. The result is 0 or 1. Using `executeOne()` with `orderBy` or `limit` is an error (`IllegalStateException`).

Example: [7.4](07-4-delete.md#delete-exactly-one-row-executeone). In a Spring repository, use `deleteOne` and `updateOne` (see [12.4](12-spring-data-jpa.md#one-row-protection-deleteone-and-updateone)).

<a id="transactions"></a>
### Transactions

Bulk statements need a transaction. lambdaQL opens a transaction only for a builder made with the `EntityManagerFactory` constructor.

| Builder | Transaction |
|---|---|
| `QueryBuilder(entityManagerFactory)` | `execute()` and `executeOne()` open and commit their own transaction. On an error, they roll back |
| A builder that uses an `EntityManager`: `QueryBuilder(entityManager)`, the Spring `QueryBuilder` bean, the repository executor | The caller's transaction (`@Transactional`, or the active transaction of that `EntityManager`). Without a transaction, it fails with `TransactionRequiredException` before it sends SQL (Java). In a Spring repository call, it becomes `InvalidDataAccessApiUsageException` (see [12.6](12-spring-data-jpa.md#126-transactions-and-exceptions)) |

<a id="the-persistence-context-is-not-updated"></a>
### The persistence context is not updated

Bulk DML does not go through the JPA persistence context.

- Hibernate flushes pending changes before the statement runs.
- After the statement runs, entities already loaded in the same `EntityManager` have **old values**. If you use those entities again, call `entityManager.clear()`.
- JPA `cascade` and `orphanRemoval` do **not** run. Delete the children first, or use `ON DELETE CASCADE` in the database.

<a id="sqlrestriction-and-softdelete"></a>
### `@SQLRestriction` and `@SoftDelete`

Hibernate adds the `@SQLRestriction` and `@SoftDelete` conditions of the entity to every statement that lambdaQL makes (measured on MariaDB 10.11 and Oracle 23.26. H2 not measured).
This is also true for bulk `update` and `delete`. Example: `where m1_0.order_id=? and (m1_0.hidden = 0)`.

A `delete` of a `@SoftDelete` entity becomes `update … set deleted = 1 where … and deleted = 0`. The rows are not really removed.
lambdaQL does not handle these conditions on its own.

Example code: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt) (`old-value`, `assign-rules`).

---

<a id="79-all-rules-u1-to-u21-summary"></a>
## 7.9 All rules U1 to U21 (summary)

Rules that lambdaQL checks when it reads the lambda: U1, U3, U4, U6, U15.
lambdaQL rejects an assignment to a `@Version` property (`LQL-3068`). U11 and U12 are not checked.

| # | Rule |
|---|---|
| U1 | An assignment lambda has **only assignments (setter calls) and `Dml.value(column, value)`** to the target, an embedded path of the target, or the `@Id` of an association (`LQL-3037`). `if`, `when`, loops, local variables, `++`, scope functions and other calls are errors. A lambda with no assignment is also an error. |
| U2 | The right side follows the value rules of `where` ([3. Conditions (`where`)](03-where.md)). Captured values are bind parameters, and constants written in the lambda are literals. `a ?: b` becomes `coalesce(a, b)`. |
| U3 | The right side reads the **old** value. Assigning a column and then reading it in the same statement is an error. |
| U4 | Assigning the same physical column twice is an error, even in different `set()` calls. |
| U5 | There is no `execute()` without `where()` or `all()`. The compiler checks this. |
| U6 | Assigning `null` to a non-optional column (`@Id`, `nullable=false`, primitive) is an error. Assigning to a collection or to a property of an associated entity that is not the `@Id` is also an error. |
| U7 | A captured value gets its own parameter each time it is used. (If a STRING column and an ORDINAL column share one enum parameter, H2 and MariaDB store a wrong value without an error.) |
| U8 | `LocalDateTime.now()` is computed in the application and bound. Database time (`current_timestamp`) is not supported. |
| U9 | In DML, an association path that is not an FK becomes a correlated scalar subquery, both in `where` and on the right side of SET. In `where`, `customer.id is not null` is added for a path that a select would inner join, and not for a path that it would `left join`. DML targets the same rows as the select. |
| U10 | In `insert ... select`, a source path becomes an explicit `left join`. |
| U11 | `it.customer = entity` binds the `@Id` of the entity. If the id is null, the bind fails. |
| U12 | IDENTITY and SEQUENCE ids can be left out. A TABLE generator works only in `values`, not in `insert ... select`. Without a generator, the id is needed. Other non-optional properties are also needed. |
| U13 | Many rows are split into statements of at most 500 values. The results are added up. All statements run in one transaction. |
| U14 | One `set` lambda can assign many columns, and `set()` can be called more than once. Everything is merged into one SET list, in call order. Property assignments and `Dml.value` can also be mixed in one lambda. |
| U15 | A `@Version` entity uses `update versioned`. Assigning the version directly is `LQL-3068`. Checking `execute() == 0` is your job. |
| U16 | Bulk DML skips the persistence context. Flush before, stale entities after, no cascade. |
| U17 | If a subquery in a DELETE reads the target entity (or the same inheritance hierarchy), lambdaQL first reads the target ids through a derived table: `d0.id in (select t.id from (select e0.id as id from … where <condition>) t)`. Otherwise MariaDB gives error 1093. It also works for correlated subqueries. `update` and entities with a composite id are not wrapped (see [7.4](07-4-delete.md#delete-with-a-subquery-on-the-same-table)). |
| U18 | Inheritance: SINGLE_TABLE is fully supported. Update/delete on JOINED and TABLE_PER_CLASS use temporary tables. Insert into those subclasses is rejected. |
| U19 | The query cache key has the entry point, the target entity, the set lambda, where/all, versioned and the split size. |
| U20 | `Dml.value(column, value)`: the first argument must be a property read that starts from the target (or the target `a` of `insert ... select`) (`LQL-3039`). The setter rules of the same property (U1, U6, only `@Id` for associations) apply as they are. The value type is compared with the column type (`LQL-3065`). It can be mixed with property assignments. |
| U21 | Each lambda is analyzed once and cached. The checks across `set()` calls compare only the cached summaries (about 0.1 to 0.2 µs in the prototype). |

The upsert rules are in [7.6 Upsert](07-6-upsert.md#rules).

---

<a id="710-not-in-v01"></a>
## 7.10 Not in v0.1

| Item | What you can do now |
|---|---|
| `if` in a lambda to choose an assignment: `LQL-2009` | Call `set()` more than once |
| `++`, `--`, `apply`, `with`, `also`: `LQL-2003` | Use `+= 1` or a direct assignment |
| String templates or `+` (`"${it.description}_x"`): `LQL-2007` | Build the string outside the lambda and capture it |
| Explicit joins in `insert ... select` | Use many-to-one paths |
| `returning` | Select again after the DML |
| Database time (`current_timestamp`) | Bind the application time |

If you break a rule, you get a `LambdaQLException` with a code `[LQL-xxxx]`. Most rules give `TranslationException`. A condition or a local variable in an assignment lambda gives `UnsupportedLambdaShapeException`. See [11. Errors and languages](../11-errors-and-languages.md).
To see all rules in one place: [10. Rules and limits](10-rules-and-limits.md).

---

[Kotlin track](README.md) | Previous: [6. Select, aggregates and group by](06-select-group.md) | Next: [8. Procedures and DB functions](08-procedures-functions.md) | [Java version of this page](../java/07-dml.md)
