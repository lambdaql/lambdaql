# 7. Update, insert, delete (Kotlin)

> [!NOTE]
> **Status**
> - **Implemented and tested** on H2 2.3, MariaDB 10.11 and Oracle 23.26 (Hibernate 6.6.5, milestone M10, decisions D28 and D40): update, delete, insert, `insert ... select`, upsert, and (decision D41) `limit` for update and delete and the one-row protection `executeOne()`. The tests use **Java** lambdas. **Kotlin lambdas in DML are not tested yet.**
> - **Also implemented and tested (with Java lambdas):** subqueries (`sub`) in the `where` of update and delete (milestone M8, see [7.4](07-4-delete.md)), and `groupBy`, `having` and `Agg` in `insert ... select` (milestone M9, see [7.5](07-5-insert.md)).
> - **Not implemented yet:** subqueries in the values of `set` (the context object `by.from(...)`), and `limit` / `executeOne()` on entities with a composite key.
> - The Kotlin code in this chapter shows the tested Java statements in Kotlin syntax. The HQL comes from the Java tests. Examples without HQL follow the design.
> - See the [track README](README.md) for the full status.

[Kotlin track](README.md) | [Manual home](../README.md) | Previous: [6. Select, aggregates and group by](06-select-group.md) | Next: [8. Procedures and DB functions](08-procedures-functions.md) | [Java version of this page](../java/07-dml.md)

Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md)

lambdaQL has five entry points:

| Entry point | Use |
|---|---|
| `qb.from(X::class.java)` | select (chapters 3 to 6) |
| `qb.update(X::class.java)` | update rows |
| `qb.insert(X::class.java)` | insert rows |
| `qb.delete(X::class.java)` | delete rows |
| `qb.upsert(X::class.java)` | insert a row, or change it if a row with the same `@Id` exists |

The ideas are the same as for queries:

- You write **one lambda** for each part. No static helpers.
- The lambda **never runs**. lambdaQL reads its bytecode. A property assignment like `it.status = 1` compiles to a setter call, and it becomes `set e0.status = 1`.
- `where` uses the same rules as in [3. Conditions](03-where.md), and it changes the same rows as a select with the same lambda.
- `execute()` runs the statement and returns the **number of affected rows** (`Int`).
- `executeOne()` runs an update or delete that must change **at most one row** (see [One-row protection](#one-row-protection)).
- `orderBy { ... }.limit(n)` changes only the first `n` rows (see [Limit](#limit-change-the-first-rows)).
- `toHql()` gives the HQL and the parameters without running the statement.
- A statement needs a transaction (see [Transactions](#transactions)).

## Contents

- [7.1 Example entities](#71-example-entities)
- [7.2 Update](#72-update)
- [7.3 Two ways to assign: property or `by.set`](#73-two-ways-to-assign-property-or-byset)
- [7.4 Delete](#74-delete)
- [7.5 Insert](#75-insert)
- [7.6 Upsert](#76-upsert)
- [7.7 Kotlin notes for DML](#77-kotlin-notes-for-dml)
- [7.8 Important rules](#78-important-rules)
- [7.9 All rules U1 to U21 (summary)](#79-all-rules-u1-to-u21-summary)
- [7.10 Not in v0.1](#710-not-in-v01)

---

## 7.1 Example entities

This chapter uses `Order`, `Customer` and `Item` (see [2. Getting started](02-getting-started.md#example-entities)).
`Order` has a few **extra properties** in this chapter:

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

## 7.2 Update

→ [7.2 Update](07-2-update.md)

## 7.3 Two ways to assign: property or `by.set`

| Style | When to use | Example |
|---|---|---|
| Property assignment | The property is a `var` (it has a setter). The compiler checks the value type. | `set { it.status = 1 }` |
| `by.set(column, value)` | The property is a `val` (no setter), for example a DDD-style entity. | `set { o, by -> by.set(o.status, 1) }` |

```kotlin
val u = qb.update(Order::class.java)
    .set { o, by -> by.set(o.description, "done") }
    .where { it.status == 2 }
// HQL: update Order e0 set e0.description = 'done' where e0.status = 2
```

**About the SQL blocks.** `…` is the shortened select list (all entity columns), and `?` is a bind parameter. The lines come from [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java). It runs each query on H2 2.3, MariaDB 10.11 and Oracle 23.26 with Hibernate 6.6.5, and it fails if Hibernate's SQL changes. The test uses Java lambdas. A Kotlin lambda with the same meaning gives the same HQL, so it gets the same SQL.

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `update-by`:

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

Oracle writes the alias in `set` (`set o1_0.status=?`); H2 and MariaDB do not.

There are no Kotlin tests yet. The same statement written in Java is tested in [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) (H2, MariaDB and Oracle). See [the Java version of this page](../java/07-dml.md) for the tested examples.

Every example in this chapter can also be written with `by.set`. Chain the calls: the lambda returns the last `by.set(...)`.

```kotlin
qb.update(Order::class.java)
    .set { o, by -> by.set(o.status, st).set(o.price, o.price!! * 1.1) }
    .where { it.status == 1 }
    .execute()

qb.update(Order::class.java).set { o, by -> by.set(o.address.city, city) }.where { it.id == id }.execute()  // embedded
qb.update(Order::class.java).set { o, by -> by.set(o.customer?.id, cid) }.where { it.id == id }.execute()   // FK
```

The Kotlin index operator `by[o.status] = st` calls the same `set` method. The assignment lambda returns nothing, so this form works too (compiled with kotlinc 2.3.21 and read by the analyzer; not yet a test in the build).
Today lambdaQL reads only a chain of `by.set(...)` calls that the lambda returns. So write the chain.

In the design, the second lambda argument is **one context object**. It has both `set(column, value)` and the subquery function `from(...)`.
Name it `by` or `sub` as you like. The name does not matter.
The subquery part of this object is **not implemented yet**. Today the object has only `set`. (Subqueries in `where` work: `where { o, sub -> ... }`, see [5. Subqueries](05-subquery.md).)

```kotlin
qb.update(Order::class.java)
    .set { o, by ->
        by.set(o.total, by.from(Item::class.java).where { i -> i.order?.id == o.id }.sum { i -> i.price })
    }
    .all()
    .execute()
```

Rules for `by.set` (rule U20):

- The column must be a **property path that starts at the target**: a property, an embedded path, or the `@Id` of an association.
  A captured value, a calculation, or a path of the source entity is an error.
- The lambda must be one chain of `by.set(...)` calls. Other statements are an error.
- `by.set(column, value)` is generic, so the compiler does not check that the value fits the column.
  The design checks the column type against the value type at analysis time. This check is not implemented yet.
- Do not mix property assignments and `by.set` **in one lambda**. You can mix them in different `set()` calls.

---

## 7.4 Delete

→ [7.4 Delete](07-4-delete.md)

## 7.5 Insert

→ [7.5 Insert](07-5-insert.md)

## 7.6 Upsert

→ [7.6 Upsert](07-6-upsert.md)

## 7.7 Kotlin notes for DML

| Kotlin form | Meaning |
|---|---|
| `it.status = x` | a setter call: `set e0.status = :p1` |
| `!!` on the right side | ignored; it only makes the code compile (D23) |
| `a ?: b` | `coalesce(a, b)` |
| `it.customer?.id = x` | always assigns (the `?.` is ignored) |
| `it.cnt += 1` | supported (`set e0.cnt = e0.cnt + 1`) |
| `by.set(o.status, x).set(...)` | the `by` form: a chain, or one `by.set` per statement |
| `by[o.status] = x` | the same as `by.set(o.status, x)` |
| `by[o.cnt] += 1` | does not compile (on purpose: `by` has no `get`). Write `by[o.cnt] = o.cnt + 1` |
| `it.cnt++`, `it.cnt--` | **not in v0.1**. Use `+= 1`. The error message says this |
| `it.apply { }`, `with(it) { }`, `it.also { }` | **not in v0.1**. Use direct assignments. The error message says this |
| `if` / `when` in the lambda | **not in v0.1**. Call `set()` many times instead (U14) |
| local `val` in the lambda | rejected. It could hide a U3 problem |

More about Kotlin: [9. Kotlin notes](09-kotlin-notes.md).

---

## 7.8 Important rules

### The right side reads the old value

In SQL, every column on the right side of SET means the value **before** the update. lambdaQL follows this rule.

```kotlin
.set { it.price = it.price!! * 2; it.total = it.price }
// error: price is assigned first, then read again
```

If you **assign a column and then read it later in the same statement**, lambdaQL throws `LambdaAnalysisException` when you call `set(...)` (rule U3).
The reason: MariaDB uses the **new** value in this case, while H2 and Oracle use the old value. The results differ (checked: MariaDB 200, H2 and Oracle 100).

These cases are fine, because all three databases agree:

| Form | OK? |
|---|---|
| `it.price = it.price!! * 1.1` (read itself) | yes |
| `it.total = it.price; it.price = 0.0` (read first, assign later) | yes |
| `it.price = ...; it.total = it.price` (assign first, read later) | **error** |

The check works on physical columns (embedded sub-columns, FK columns) and across many `set()` calls.

### Assign a column only once

Assigning the same column twice is an error (rule U4). This includes `it.address = ...` and then `it.address.city = ...`,
or `it.customer = ...` and then `it.customer?.id = ...`.

### Rules checked at analysis time

lambdaQL checks rules U1, U3, U4 and U6 when it reads the lambda, and throws `LambdaAnalysisException` from `set(...)` or `values(...)`.
These checks are tested with Java lambdas in [`AssignmentReaderTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/sem/AssignmentReaderTest.java) (region `rules`) and [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java). There are no Kotlin tests yet.

### `where` or `all()` is required

`set()` returns a type **without** `execute()` (`UpdateSet`). Only `where()` and `all()` return a type with `execute()` (`ExecutableUpdate`).
So a forgotten `where` is a compile error (rule U5). The same is true for `delete`.

### Association paths: same rows as select

lambdaQL writes a non-FK association path in DML as a **correlated scalar subquery**, in `where` and on the right side of SET (rule U9). The HQL comes from the Java test in [7.2](07-2-update.md#a-value-from-another-table):

```kotlin
.where { it.customer?.name == "Kim" }
// → where (select s1.name from Customer s1 where s1.id = e0.customer.id) = 'Kim' and e0.customer.id is not null

it.custName = it.customer?.name
// → set e0.custName = (select s1.name from Customer s1 where s1.id = e0.customer.id)
```

Why? If lambdaQL gave the path to Hibernate directly, Hibernate would make a different join SQL for each database
(H2 `merge`, MariaDB `update ... join`, Oracle an inline view), and with a path in SET **rows without a customer were lost**
(checked: with `.all()`, only 9 of 10 rows were updated).
lambdaQL writes the subqueries itself. The statement then reads only its own table, so MariaDB's limit on reading the target table (error 1093) does not apply.

A select uses an inner join for a path in `where` (decision D36). So in DML, lambdaQL adds `e0.customer.id is not null` for such a path.
Then `update` and `delete` touch **exactly the rows** that `qb.from(X::class.java).where { same lambda }` returns (tested with Java: the deleted rows are compared with the select).
A null guard such as `it.customer == null || ...` keeps rows without a customer in the select, so that path gets no `is not null` check.

A path that ends with the `@Id` (`it.customer?.id`) is the FK column. No subquery, no join.

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

### Limit: change the first rows

`orderBy { ... }.limit(n)` after `where { ... }` or `all()` makes an update or delete change at most `n` rows: the first `n` rows of `qb.from(X::class.java).where { same }.orderBy { same }`.

```kotlin
qb.delete(Order::class.java).where { it.status == 1 }.orderBy { o, by -> by.asc(o.price) }.limit(100).execute()
```

| Point | Rule |
|---|---|
| Order | the `orderBy` keys, then the id (rule S9). Without `orderBy`, by the id |
| `orderBy` without `limit` | error (`IllegalStateException`) |
| Rendering | `e0.id in (select t.id from (select x0.id as id from X x0 ... order by ... limit n) t)` |
| Rows | the inner select follows the select rules, joins included (D36), so the rows are the same as the select |
| MariaDB | the derived table `t` avoids errors 1093 and 1235 |

Examples and the SQL of each database: [7.4](07-4-delete.md#delete-the-first-rows-limit) (delete) and [7.2](07-2-update.md#update-the-first-rows-limit) (update).

### One-row protection

`executeOne()` instead of `execute()` changes **at most one row**:

1. lambdaQL reads the ids of the rows that match, at most 2.
2. With 2 ids, it changes nothing and throws `NonUniqueResultException`.
3. With 1 id, it changes that row with `id = :p and (your condition)`.

It never changes more than one row, and it does not rely on a rollback. The result is 0 or 1. `executeOne()` together with `orderBy` or `limit` is an error (`IllegalStateException`).
Example: [7.4](07-4-delete.md#delete-exactly-one-row-executeone). In a Spring repository: `deleteOne` and `updateOne` (see [12.4](12-spring-data-jpa.md#one-row-protection-deleteone-and-updateone)).

### Transactions

A bulk statement needs a transaction. lambdaQL opens one only with the `EntityManagerFactory` constructor.

| Builder | Transaction |
|---|---|
| `QueryBuilder(entityManagerFactory)` | `execute()` and `executeOne()` open their own transaction and commit it. On an error they roll back |
| A builder with an `EntityManager`: `QueryBuilder(entityManager)`, Spring's `QueryBuilder` bean, the repository executor | the caller's transaction (`@Transactional`, or an active transaction of that `EntityManager`). Without one, the statement fails with `TransactionRequiredException` before any SQL is sent (tested with Java). In Spring repository calls, it becomes `InvalidDataAccessApiUsageException` (see [12.6](12-spring-data-jpa.md#126-transactions-and-exceptions)) |

### The persistence context is not updated

Bulk DML does not go through the JPA persistence context (rule U16):

- Hibernate flushes pending changes before the statement.
- After the statement, entities already loaded in the same `EntityManager` have **old values**. Call `entityManager.clear()` if you use them again.
- JPA `cascade` and `orphanRemoval` do **not** run. Delete children first, or use `ON DELETE CASCADE` in the database.

---

## 7.9 All rules U1 to U21 (summary)

Checked when lambdaQL reads the lambda: U1, U3, U4, U6 (tested with Java lambdas). Checked by the compiler: U5.
The lambdaQL checks of U11, U12, U15 and U20 are not implemented yet. U17 (the MariaDB wrap for a subquery on the same table) is not implemented yet.

| # | Rule |
|---|---|
| U1 | An assignment lambda contains **only assignments** (setter calls) on the target, its embedded paths, or the `@Id` of an association. `if`, `when`, loops, local variables, `++`, scope functions and other calls are errors. A lambda with no assignment is an error. |
| U2 | The right side follows the value rules of `where` ([3. Conditions](03-where.md)). Captured values are bind parameters, constants in the lambda are literals (D37). `a ?: b` becomes `coalesce(a, b)`. |
| U3 | The right side reads the **old** value. Reading a column after assigning it in the same statement is an error. |
| U4 | Assigning the same physical column twice is an error, also across `set()` calls. |
| U5 | No `execute()` without `where()` or `all()`. Checked by the compiler. |
| U6 | Assigning `null` to a non-optional column (`@Id`, `nullable=false`, primitive) is an error. Assigning a collection, or a non-`@Id` property of an associated entity, is an error. |
| U7 | Each use of a captured value gets its own parameter. (Sharing one enum parameter between a STRING column and an ORDINAL column silently stored a wrong value in tests.) |
| U8 | `LocalDateTime.now()` is evaluated in the application and bound. The database time (`current_timestamp`) is not supported. |
| U9 | Non-FK association paths in DML become correlated scalar subqueries, in `where` and on the right side of SET. In `where`, a path that a select would inner join (D36) also gets `customer.id is not null`. DML touches the same rows as select. |
| U10 | In `insert ... select`, source paths become an explicit `left join`. |
| U11 | `it.customer = entity` binds the `@Id` of the entity. If the id is null, binding fails. |
| U12 | IDENTITY and SEQUENCE ids can be left out. A TABLE generator works only with `values`, not with `insert ... select`. Without a generator, the id is required. Other non-optional properties are required. |
| U13 | Many rows are split: at most 500 values per statement. Results are added. All statements run in one transaction. |
| U14 | One `set` lambda can assign many columns, and `set()` can be called many times. All join into one SET list in call order. |
| U15 | `@Version` entities use `update versioned`. Do not assign the version yourself. Checking `execute() == 0` is your job. |
| U16 | Bulk DML skips the persistence context. Flush before, stale entities after, no cascade. |
| U17 | If a DELETE subquery reads the same entity, lambdaQL wraps it in a derived table. (MariaDB error 1093 otherwise.) |
| U18 | Inheritance: SINGLE_TABLE is fully supported. JOINED and TABLE_PER_CLASS update/delete use temporary tables. Insert into their subclasses is rejected. |
| U19 | The query cache key includes the entry point, the target entity, the set lambdas, where/all, versioned, and the split size. |
| U20 | `by.set(column, value)`: the column must be a property path on the target. Types are checked at analysis time. Do not mix property assignments and `by.set` in one lambda. |
| U21 | Each lambda is analyzed once and cached. Checks across `set()` calls only compare cached summaries (about 0.1 to 0.2 µs in a prototype). |

The upsert rules (decision D40) are in [7.6 Upsert](07-6-upsert.md#rules).

---

## 7.10 Not in v0.1

| Item | Plan | What to do now |
|---|---|---|
| Conditional value (`if (c) a else b`, `when`, a toggle `!it.active` as a value) | v0.2 (CASE WHEN) | two updates with different `where` |
| `if` inside the lambda to choose an assignment | v0.2 | call `set()` many times (U14) |
| `++`, `--`, `apply`, `with`, `also` | v0.2 | use `+= 1` or a direct assignment |
| String templates or `+` (`"${it.description}_x"`) | v0.2 | – |
| Explicit join in `insert ... select` | v0.2 | use many-to-one paths |
| `returning` | rejected | query again after the DML |
| Database time (`current_timestamp`) | rejected | bind an application time (U8) |

Today a broken rule throws `LambdaAnalysisException` with a plain message (in Korean for now). The planned `[LQL-xxxx]` codes and the `LambdaQLException` base class are in [11. Errors and languages](../11-errors-and-languages.md).
All rules in one place: [10. Rules and limits](10-rules-and-limits.md).

---

[Kotlin track](README.md) | Previous: [6. Select, aggregates and group by](06-select-group.md) | Next: [8. Procedures and DB functions](08-procedures-functions.md) | [Java version of this page](../java/07-dml.md)
