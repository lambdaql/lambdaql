<a id="7-update-insert-delete-kotlin"></a>
# 7. Update, insert, delete (Kotlin)

> [!IMPORTANT]
> This page is older than the Korean manual. See the [Korean page](../../ko/kotlin/07-dml.md) for the latest rules.

[Kotlin track](README.md) | [Manual home](../README.md) | Previous: [6. Select, aggregates and group by](06-select-group.md) | Next: [8. Procedures and DB functions](08-procedures-functions.md) | [Java version of this page](../java/07-dml.md)

Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md)

lambdaQL has five entry points.

| Entry point | Use |
|---|---|
| `qb.from(X::class.java)` | select (chapters 3 to 6) |
| `qb.update(X::class.java)` | update rows |
| `qb.insert(X::class.java)` | insert rows |
| `qb.delete(X::class.java)` | delete rows |
| `qb.upsert(X::class.java)` | insert a row. If a row with the same `@Id` exists, update that row |

The ideas are the same as for queries.

- You write **one lambda** for each part. The only static helper is `Dml.value`, for `val` properties ([7.3](#73-two-ways-to-assign-property-or-dmlvalue)).
- The lambda **never runs**. lambdaQL reads its bytecode. A property assignment like `it.status = 1` compiles to a setter call, and it becomes `set e0.status = 1`.
- `where` uses the same rules as in [3. Conditions (`where`)](03-where.md). The target rows are the same as the rows that a select with the same lambda returns.
- `execute()` runs the statement and returns the **number of affected rows** (`Int`).
- `executeOne()` runs an update or delete that must change **at most one row** (see [One-row protection](#one-row-protection)).
- `orderBy { ... }.limit(n)` changes only the first `n` rows (see [Limit](#limit-change-the-first-rows)).
- `toHql()` returns the HQL and the parameters without running the statement.
- A statement needs a transaction to run (see [Transactions](#transactions)).

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
In this chapter, `Order` has a few **extra properties**.

```kotlin
@Entity
@Table(name = "orders")
class Order(
    // ... id, price, status, active, description, customer, updateAt, items as usual
    var total: Double? = null,                 // sum of item prices
    var cnt: Int = 0,                          // a counter (Int, so that += compiles)
    var custName: String? = null,              // copy of the customer name
    @Embedded var address: Address = Address(),
)

@Embeddable
class Address(var city: String? = null, var zip: String? = null)
```

Other classes used here:

```kotlin
@Entity class VOrder(@Id var id: Long? = null, var status: Int? = null, @Version var version: Long? = null)
@Entity class Archive(@Id var id: Long? = null, var price: Double? = null, var customerName: String? = null)
@Entity class Summary(@Id var customerId: Long? = null, var total: Double? = null, var cnt: Long? = null)
@Entity class IdOrder(@Id @GeneratedValue var id: Long? = null, var price: Double? = null, var status: Int? = null)

data class Line(val id: Long, val price: Double)   // input rows for insert
```

The Java tests use Java test entities with the same names ([`query/src/test/java/org/lambdaql/query`](https://github.com/codebit0/lambdaql/tree/main/query/src/test/java/org/lambdaql/query)).

---

<a id="72-update"></a>
## 7.2 Update

→ [7.2 Update](07-2-update.md)

<a id="73-two-ways-to-assign-property-or-dmlvalue"></a>
## 7.3 Two ways to assign: property or `Dml.value`

| Style | When to use | Example |
|---|---|---|
| Property assignment | The property is a `var` (it has a setter). The compiler checks the value type. | `set { it.status = 1 }` |
| `Dml.value(column, value)` | The property is a `val` (no setter), for example in a DDD-style entity. | `set { o -> value(o.status, 1) }` |

`value` is the function `org.lambdaql.Dml.value` (decision D49). Import it with `import org.lambdaql.Dml.value` and use it in a lambda with one argument.

**(tested)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt), region `update-value`:

```kotlin
// entities without setters (val): Dml.value(column, value)
val u = qb.update(Order::class.java)
    .set { o -> value(o.description, "done") }
    .where { it.status.toInt() == 2 }
assertEquals("update Order e0 set e0.description = 'done' where e0.status = 2", u.toHql().hql())
```

The test entity's `status` is a `short`, so the test writes `it.status.toInt() == 2`. The same statement written in Java is tested in [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) (region `update-value`).

**About the SQL blocks.** `…` is the shortened select list (all entity columns), and `?` is a bind parameter. The lines come from [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java). It runs each query on H2 2.3, MariaDB 10.11 and Oracle 23.26 with Hibernate 7.4, and it fails if Hibernate's SQL changes. The test uses Java lambdas. A Kotlin lambda with the same meaning gives the same HQL, so it gets the same SQL.

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `update-value`:

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

Every example in this chapter can also be written with `value`. Write one `value` call per statement.

```kotlin
import org.lambdaql.Dml.value

qb.update(Order::class.java)
    .set { o -> value(o.status, st); value(o.price, o.price!! * 1.1) }
    .where { it.status == 1 }
    .execute()

qb.update(Order::class.java).set { o -> value(o.address.city, city) }.where { it.id == id }.execute()  // embedded
qb.update(Order::class.java).set { o -> value(o.customer?.id, cid) }.where { it.id == id }.execute()   // FK
```

Rules for `value` (rule U20):

- The first argument must be a **property read (getter call) that starts at the target**: a property, an embedded path, or the `@Id` of an association.
  A captured value, a calculation, or a path of the source entity is an error at analysis time (`LQL-3039`).
- The setter rules of the same property apply as they are. `value(o.x, v)` is the same as `o.x = v`, so U1 and U6 apply, and for an association you can assign only the `@Id` (`LQL-3050`).
- You can **mix property assignments and `value` in one lambda**.
- `value(column, value)` is generic, so the compiler does not check that the value fits the column.
  lambdaQL compares the column type with the value type at analysis time, and reports the error `LQL-3065` if they do not match.
  A number fits a number column. But a decimal number does not fit an integer column. A value whose type is not known (`Any`, the `Number` of a generic aggregate) is not checked.
- Inside a class that has its own `value(...)` function, the imported `value` is hidden. Write `Dml.value(...)` there.
- For a `val` property, `o.total = 1` is a compile error, so use `value`.

<a id="subquery-in-a-set-value"></a>
### Subquery in a `set` value

The second argument of an assignment lambda is `sub`, the starting point of a subquery. The notation is the same as `where { o, sub -> ... }` (decision D49).
In `insert ... select`, the third argument is `sub` (see [7.5](07-5-insert.md)).

**(tested)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt), region `update-sub`:

```kotlin
// the second parameter starts a subquery, as in where { o, sub -> ... } (D49)
val u = qb.update(Order::class.java)
    .set { o, sub -> o.price = sub.from(Item::class.java).where { i -> i.order?.id == o.id }.sum { it.price } }
    .where { o, sub -> sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
assertEquals("update Order e0 set e0.price = (select sum(s1.price) from Item s1 where s1.order.id = e0.id)"
        + " where exists (select 1 from Item s1 where s1.order.id = e0.id)", u.toHql().hql())
```

- You can use an aggregate of another table and an aggregate of the same table as a `set` value (measured on H2, MariaDB 10.11 and Oracle 23.26). In `update`, MariaDB does not report error 1093 either.
- When no row matches, `sum` and the other aggregates return NULL. For a NOT NULL column this is a DB error, so wrap the value with `?: 0.0` or `Objects.requireNonNullElse(…, 0.0)`. It becomes `coalesce(...)`.
- You write a subquery the same way in the insert `values { a, sub -> ... }` and in upsert (`onConflict { ... }.update { a, sub -> ... }`) (see [7.5](07-5-insert.md#a-subquery-in-a-value)). In the list form, the third argument is `sub`, as in `values(list) { a, l, sub -> ... }`.

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
| `it.status = x` | a setter call: `set e0.status = :p1` |
| `!!` on the right side | ignored. It only makes the code compile (D23) |
| `a ?: b` | `coalesce(a, b)` |
| `it.customer?.id = x` | always assigns (the `?.` is ignored) |
| `it.cnt += 1` | supported (`set e0.cnt = e0.cnt + 1`) |
| `value(o.status, x)` | assignment for a `val` property (D49). One `value` per statement |
| `value(o.cnt, o.cnt + 1)` | supported (`set e0.cnt = e0.cnt + 1`) |
| `it.cnt++`, `it.cnt--` | **not in v0.1**. Use `+= 1`. The error message says this |
| `it.apply { }`, `with(it) { }`, `it.also { }` | **not in v0.1**. Use direct assignments. The error message says this |
| `if` / `when` in the lambda | **not in v0.1**. Call `set()` many times instead (U14) |
| local `val` in the lambda | rejected. It could hide a U3 problem |

More about Kotlin: [9. Kotlin notes](09-kotlin-notes.md).

---

<a id="78-important-rules"></a>
## 7.8 Important rules

<a id="the-right-side-reads-the-old-value"></a>
### The right side reads the old value

In SQL, every column on the right side of SET means the value **before** the update. lambdaQL follows this rule.

```kotlin
.set { it.price = it.price!! * 2; it.total = it.price }
// error: price is assigned first, then read again
```

If you **assign a column and then read it later in the same statement**, lambdaQL throws `TranslationException` (`LQL-3042`) when you call `set(...)` (rule U3).
The reason: MariaDB uses the **new** value in this case, while H2 and Oracle use the old value. The results differ (measured: MariaDB 200, H2 and Oracle 100).

The following cases are allowed, because all three databases give the same result.

| Form | Allowed? |
|---|---|
| `it.price = it.price!! * 1.1` (reads itself) | yes |
| `it.total = it.price; it.price = 0.0` (read first, assign later) | yes |
| `it.price = ...; it.total = it.price` (assign first, read later) | **error** |

The check works on physical columns (embedded sub-columns, FK columns), and it also applies across many `set()` calls.

<a id="assign-a-column-only-once"></a>
### Assign a column only once

Assigning the same column twice is an error (rule U4). This includes `it.address = ...` and then `it.address.city = ...`,
or `it.customer = ...` and then `it.customer?.id = ...`.

<a id="rules-checked-at-analysis-time"></a>
### Rules checked at analysis time

lambdaQL checks rules U1, U3, U4 and U6 when it reads the lambda, and throws a `LambdaQLException` from `set(...)` or `values(...)` (for example `LQL-3037` for U1, `LQL-3042` for U3, `LQL-3041` for U4).
These checks are tested with Java lambdas in [`AssignmentReaderTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/sem/AssignmentReaderTest.java) (region `rules`) and [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java). There are no Kotlin tests for these checks yet.

<a id="where-or-all-is-required"></a>
### `where` or `all()` is required

`set()` returns a type **without** `execute()` (`UpdateSet`). Only `where()` and `all()` return a type with `execute()` (`ExecutableUpdate`).
So a forgotten `where` is a compile error (rule U5). The same is true for `delete`.

<a id="association-paths-same-rows-as-select"></a>
### Association paths: same rows as select

lambdaQL writes a non-FK association path in DML as a **correlated scalar subquery**, both in `where` and on the right side of SET (rule U9). The HQL comes from the Java test in [7.2](07-2-update.md#a-value-from-another-table).

```kotlin
.where { it.customer?.name == "Kim" }
// → where (select s1.name from Customer s1 where s1.id = fk(e0.customer)) = 'Kim' and e0.customer.id is not null

it.custName = it.customer?.name
// → set e0.custName = (select s1.name from Customer s1 where s1.id = fk(e0.customer))
```

The reason is as follows. If lambdaQL gave the path to Hibernate as it is, Hibernate would make a different join SQL for each database
(H2 `merge`, MariaDB `update ... join`, Oracle an inline view). Also, with a path in SET, **rows without a customer were lost**
(measured: with `.all()`, only 9 of 10 rows were updated).
lambdaQL writes the subqueries itself. The statement then reads only its own table, so MariaDB's limit on reading the target table (error 1093) does not apply.

A select uses an inner join for a path in `where` (decision D36). So in DML, lambdaQL adds `e0.customer.id is not null` for such a path.
As a result, `update` and `delete` target **exactly the rows** that `qb.from(X::class.java).where { same lambda }` returns (tested with Java: the deleted rows are compared with the select result).
This is the same with a null guard such as `it.customer == null || ...`. The select also uses an inner join for this path (decision D48), so the check stays.

A path that ends with the `@Id` (`it.customer?.id`) is the FK column. There is no subquery and no join.

<a id="batching-for-many-rows"></a>
### Batching for many rows

`values(list) { ... }` writes one statement with many VALUES rows (rule U13).

| Point | Rule |
|---|---|
| Split unit | the number of values (rows x columns), not the number of rows |
| Limit | at most 500 values per statement (fixed) |
| Return value | the sum of all statements |
| Transaction | all statements run in one transaction |
| Empty list | no SQL, returns 0 (tested with Java) |

Oracle has a hard limit of 65,535 bind values (ORA-01745). Parsing gets very slow long before that
(about 6 minutes for 21,845 rows in a design test). That is why the limit is small.

<a id="limit-change-the-first-rows"></a>
### Limit: change the first rows

Add `orderBy { ... }.limit(n)` after `where { ... }` or `all()`, and an update or delete changes at most `n` rows. The target is the first `n` rows of `qb.from(X::class.java).where { same }.orderBy { same }`.

```kotlin
qb.delete(Order::class.java).where { it.status == 1 }.orderBy { o, by -> by.asc(o.price) }.limit(100).execute()
```

| Point | Rule |
|---|---|
| Order | the `orderBy` keys, then the id (rule S9). Without `orderBy`, by the id |
| `orderBy` without `limit` | error (`IllegalStateException`) |
| Rendering | `e0.id in (select t.id from (select x0.id as id from X x0 ... order by ... limit n) t)` |
| Target rows | the inner select follows the select rules, joins included (D36), so the rows are the same as the select |
| MariaDB | the derived table `t` avoids errors 1093 and 1235 |

Examples and the SQL of each database: [7.4](07-4-delete.md#delete-the-first-rows-limit) (delete) and [7.2](07-2-update.md#update-the-first-rows-limit) (update).

<a id="one-row-protection"></a>
### One-row protection

If you call `executeOne()` instead of `execute()`, the statement changes **at most one row**.

1. lambdaQL reads the ids of the rows that match the condition, at most 2.
2. With 2 ids, it changes nothing and throws `NonUniqueResultException`.
3. With 1 id, it changes that row with `id = :p and (condition)`.

It never changes two or more rows, and it does not rely on a rollback. The result is 0 or 1. Using `executeOne()` together with `orderBy` or `limit` is an error (`IllegalStateException`).

Example: [7.4](07-4-delete.md#delete-exactly-one-row-executeone). In a Spring repository, use `deleteOne` and `updateOne` (see [12.4](12-spring-data-jpa.md#one-row-protection-deleteone-and-updateone)).

<a id="transactions"></a>
### Transactions

A bulk statement needs a transaction. lambdaQL opens a transaction only for a builder made with the `EntityManagerFactory` constructor.

| Builder | Transaction |
|---|---|
| `QueryBuilder(entityManagerFactory)` | `execute()` and `executeOne()` open their own transaction and commit it. On an error they roll back |
| A builder with an `EntityManager`: `QueryBuilder(entityManager)`, Spring's `QueryBuilder` bean, the repository executor | the caller's transaction (`@Transactional`, or an active transaction of that `EntityManager`). Without a transaction, the statement fails with `TransactionRequiredException` before any SQL is sent (tested, Java). In a Spring repository call, it becomes `InvalidDataAccessApiUsageException` (see [12.6](12-spring-data-jpa.md#126-transactions-and-exceptions)) |

<a id="the-persistence-context-is-not-updated"></a>
### The persistence context is not updated

Bulk DML does not go through the JPA persistence context (rule U16).

- Hibernate flushes pending changes before the statement runs.
- After the statement, entities already loaded in the same `EntityManager` have **old values**. If you use them again, call `entityManager.clear()`.
- JPA `cascade` and `orphanRemoval` do **not** run. Delete children first, or use `ON DELETE CASCADE` in the database.

<a id="sqlrestriction-and-softdelete"></a>
### `@SQLRestriction` and `@SoftDelete`

Hibernate adds the `@SQLRestriction` condition and the `@SoftDelete` condition of an entity to every statement that lambdaQL makes (measured on MariaDB 10.11 and Oracle 23.26; H2 not measured).
This includes bulk `update` and `delete`. Example: `where m1_0.order_id=? and (m1_0.hidden = 0)`.

A `delete` of a `@SoftDelete` entity becomes `update … set deleted = 1 where … and deleted = 0`. The rows are not actually removed.
lambdaQL does not handle these conditions separately.

---

<a id="79-all-rules-u1-to-u21-summary"></a>
## 7.9 All rules U1 to U21 (summary)

Rules that lambdaQL checks when it reads the lambda: U1, U3, U4, U6 (tested with Java lambdas). Rule that the compiler checks: U5.
The lambdaQL checks of U11, U12 and U15 are not implemented yet.

| # | Rule |
|---|---|
| U1 | An assignment lambda contains **only assignments (setter calls) and `Dml.value(column, value)`** on the target, its embedded paths, or the `@Id` of an association (`LQL-3037`). `if`, `when`, loops, local variables, `++`, scope functions and other calls are errors. A lambda with no assignment is also an error. |
| U2 | The right side follows the value rules of `where` ([3. Conditions (`where`)](03-where.md)). Captured values are bind parameters, and constants written in the lambda are literals (D37). `a ?: b` becomes `coalesce(a, b)`. |
| U3 | The right side reads the **old** value. Reading a column after assigning it in the same statement is an error. |
| U4 | Assigning the same physical column twice is an error, also across different `set()` calls. |
| U5 | There is no `execute()` without `where()` or `all()`. The compiler checks this. |
| U6 | Assigning `null` to a non-optional column (`@Id`, `nullable=false`, primitive) is an error. Assigning a collection, or a property of an associated entity that is not its `@Id`, is also an error. |
| U7 | Each use of a captured value gets its own parameter. (In a test, a STRING column and an ORDINAL column shared one enum parameter, and a wrong value was stored without an error.) |
| U8 | `LocalDateTime.now()` is evaluated in the application and bound. The database time (`current_timestamp`) is not supported. |
| U9 | In DML, a non-FK association path becomes a correlated scalar subquery, both in `where` and on the right side of SET. In `where`, a path that a select would inner join (D36) also gets `customer.id is not null`. DML targets the same rows as select. |
| U10 | In `insert ... select`, source paths become an explicit `left join`. |
| U11 | `it.customer = entity` binds the `@Id` of the entity. If the id is null, binding fails. |
| U12 | IDENTITY and SEQUENCE ids can be left out. A TABLE generator works only with `values`, not with `insert ... select`. Without a generator, the id is required. Other non-optional properties are also required. |
| U13 | Many rows are split into statements of at most 500 values. The results are added. All statements run in one transaction. |
| U14 | One `set` lambda can assign many columns, and `set()` can be called many times. All assignments join into one SET list in call order. Property assignments and `Dml.value` can also be mixed in one lambda (D49). |
| U15 | `@Version` entities use `update versioned`. Do not assign the version yourself. Checking `execute() == 0` is your job. |
| U16 | Bulk DML skips the persistence context: a flush before the statement, stale entities after it, and no cascade. |
| U17 | When a DELETE subquery reads the target entity (or the same inheritance hierarchy), lambdaQL first reads the target ids with a derived table: `d0.id in (select t.id from (select e0.id as id from … where <condition>) t)`. Otherwise MariaDB reports error 1093. This also works for correlated subqueries. `update` and entities with a composite id are not wrapped (see [7.4](07-4-delete.md#delete-with-a-subquery-on-the-same-table)). |
| U18 | Inheritance: SINGLE_TABLE is fully supported. Update and delete for JOINED and TABLE_PER_CLASS use temporary tables. Insert into their subclasses is rejected. |
| U19 | The query cache key includes the entry point, the target entity, the set lambdas, where/all, versioned, and the split size. |
| U20 | `Dml.value(column, value)` (D49): the first argument must be a property read that starts at the target (or at the target `a` of `insert ... select`) (`LQL-3039`). The setter rules of the same property apply as they are (U1, U6, only the `@Id` for an association). The value type is compared with the column type (`LQL-3065`). It can be mixed with property assignments. |
| U21 | Each lambda is analyzed once and cached. Checks across `set()` calls only compare cached summaries (about 0.1 to 0.2 µs in a prototype). |

The upsert rules (decision D40) are in [7.6 Upsert](07-6-upsert.md#rules).

---

<a id="710-not-in-v01"></a>
## 7.10 Not in v0.1

| Item | Plan | What to do now |
|---|---|---|
| Conditional value (`if (c) a else b`, `when`, a toggle `!it.active` used as a value) | v0.2 (CASE WHEN) | two updates with different `where` |
| `if` inside the lambda to choose an assignment | v0.2 | call `set()` many times (U14) |
| `++`, `--`, `apply`, `with`, `also` | v0.2 | use `+= 1` or a direct assignment |
| String templates or `+` (`"${it.description}_x"`) | v0.2 | – |
| Explicit join in `insert ... select` | v0.2 | use many-to-one paths |
| `returning` | rejected | query again after the DML |
| Database time (`current_timestamp`) | rejected | bind an application time (U8) |

A broken rule throws a `LambdaQLException` with a `[LQL-xxxx]` code: `TranslationException` for most rules, and `UnsupportedLambdaShapeException` for a condition or a local variable in an assignment lambda. See [11. Errors and languages](../11-errors-and-languages.md).
All rules in one place: [10. Rules and limits](10-rules-and-limits.md).

---

[Kotlin track](README.md) | Previous: [6. Select, aggregates and group by](06-select-group.md) | Next: [8. Procedures and DB functions](08-procedures-functions.md) | [Java version of this page](../java/07-dml.md)
