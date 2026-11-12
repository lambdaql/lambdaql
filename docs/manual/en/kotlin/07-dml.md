# 7. Update, insert, delete (Kotlin)

> [!NOTE]
> **Status: designed, not implemented yet.**
> This chapter is a decided design (decision D28, milestone M10).
> The Kotlin syntax compiles (kotlinc 2.3.21), and the SQL was checked on H2, MariaDB and Oracle with Hibernate 6.6.5.
> It does **not** run in lambdaQL today. Today only the analysis core and `where` + `toList()` work
> (tested on H2, MariaDB and Oracle). See the [track README](README.md) for the full status.

[Kotlin track](README.md) | [Manual home](../README.md) | Previous: [6. Select, aggregates and group by](06-select-group.md) | Next: [8. Procedures and DB functions](08-procedures-functions.md) | [Java version of this page](../java/07-dml.md)

Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md)

lambdaQL has four entry points:

| Entry point | Use |
|---|---|
| `qb.from(X::class.java)` | select (chapters 3 to 6) |
| `qb.update(X::class.java)` | update rows |
| `qb.insert(X::class.java)` | insert rows |
| `qb.delete(X::class.java)` | delete rows |

The ideas are the same as for queries:

- You write **one lambda** for each part. No static helpers.
- The lambda **never runs**. lambdaQL reads its bytecode. A property assignment like `it.status = 1` compiles to a setter call, and it becomes `set o.status = 1`.
- `where` uses the same rules as in [3. Conditions](03-where.md). Subqueries use `sub` as in [5. Subqueries](05-subquery.md).
- `execute()` runs the statement and returns the **number of affected rows** (`Int`).

## Contents

- [7.1 Example entities](#71-example-entities)
- [7.2 Update](#72-update)
- [7.3 Two ways to assign: property or `by[column] = value`](#73-two-ways-to-assign-property-or-bycolumn--value)
- [7.4 Delete](#74-delete)
- [7.5 Insert](#75-insert)
- [7.6 Kotlin notes for DML](#76-kotlin-notes-for-dml)
- [7.7 Important rules](#77-important-rules)
- [7.8 All rules U1 to U21 (summary)](#78-all-rules-u1-to-u21-summary)
- [7.9 Not in v0.1](#79-not-in-v01)

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

---

## 7.2 Update

→ [7.2 Update](07-2-update.md)

## 7.3 Two ways to assign: property or `by[column] = value`

| Style | When to use | Example |
|---|---|---|
| Property assignment | The property is a `var` (it has a setter). The compiler checks the value type. | `set { it.status = 1 }` |
| `by[column] = value` | The property is a `val` (no setter), for example a DDD-style entity. | `set { o, by -> by[o.status] = 1 }` |

`by[o.status] = 1` is the Kotlin index operator for the method `by.set(o.status, 1)`. You can also write `by.set(...)` directly; it means the same.

Every example above can also be written with `by`:

```kotlin
qb.update(Order::class.java)
    .set { o, by -> by[o.status] = st; by[o.price] = o.price!! * 1.1 }
    .where { it.status == 1 }
    .execute()

qb.update(Order::class.java)
    .set { o, by -> by.set(o.status, st).set(o.price, o.price!! * 1.1) }   // the same, as a chain
    .where { it.status == 1 }
    .execute()

qb.update(Order::class.java).set { o, by -> by[o.address.city] = city }.where { it.id == id }.execute()  // embedded
qb.update(Order::class.java).set { o, by -> by[o.customer?.id] = cid }.where { it.id == id }.execute()   // FK
```

The second lambda argument is **one context object**. It has both `set(column, value)` and the subquery function `from(...)`.
Name it `by` or `sub` as you like. The name does not matter.

```kotlin
qb.update(Order::class.java)
    .set { o, by ->
        by[o.total] = by.from(Item::class.java).where { i -> i.order?.id == o.id }.sum { i -> i.price }
    }
    .all()
    .execute()
```

Rules for `by[column] = value` (rule U20):

- The column must be a **property path that starts at the target**: a property, an embedded path, or the `@Id` of an association.
  A captured value, a calculation, or a path of the source entity is an error.
- `by.set(column, value)` is generic, so the compiler does not check that the value fits the column.
  lambdaQL checks the column type against the value type at analysis time.
- `by[o.cnt] += 1` does **not compile** (on purpose). Write `by[o.cnt] = o.cnt + 1`.
- Do not mix property assignments and `by[...]` **in one lambda**. You can mix them in different `set()` calls.

---

## 7.4 Delete

→ [7.4 Delete](07-4-delete.md)

## 7.5 Insert

→ [7.5 Insert](07-5-insert.md)

## 7.6 Kotlin notes for DML

| Kotlin form | Meaning |
|---|---|
| `it.status = x` | a setter call: `set o.status = :p1` |
| `!!` on the right side | ignored; it only makes the code compile (D23) |
| `a ?: b` | `coalesce(a, b)` |
| `it.customer?.id = x` | always assigns (the `?.` is ignored) |
| `it.cnt += 1` | supported (`set o.cnt = o.cnt + 1`) |
| `by[o.status] = x` | the same as `by.set(o.status, x)` |
| `by[o.cnt] += 1` | does not compile (on purpose). Write `by[o.cnt] = o.cnt + 1` |
| `it.cnt++`, `it.cnt--` | **not in v0.1**. Use `+= 1`. The error message says this |
| `it.apply { }`, `with(it) { }`, `it.also { }` | **not in v0.1**. Use direct assignments. The error message says this |
| `if` / `when` in the lambda | **not in v0.1**. Call `set()` many times instead (U14) |
| local `val` in the lambda | rejected. It could hide a U3 problem |

More about Kotlin: [9. Kotlin notes](09-kotlin-notes.md).

---

## 7.7 Important rules

### The right side reads the old value

In SQL, every column on the right side of SET means the value **before** the update. lambdaQL follows this rule.

```kotlin
.set { it.price = it.price!! * 2; it.total = it.price }
// error: price is assigned first, then read again
```

If you **assign a column and then read it later in the same statement**, lambdaQL stops with an error at analysis time (rule U3).
The reason: MariaDB uses the **new** value in this case, while H2 and Oracle use the old value. The results differ (checked: MariaDB 200, H2 and Oracle 100).

These cases are fine, because all three databases agree:

| Form | OK? |
|---|---|
| `it.price = it.price!! * 1.1` (read itself) | yes |
| `it.total = it.price; it.price = 0.0` (read first, assign later) | yes |
| `it.price = ...; it.total = it.price` (assign first, read later) | **error** |

The check works on physical columns (embedded sub-columns, FK columns), across many `set()` calls, and inside nested `sub` lambdas.

### Assign a column only once

Assigning the same column twice is an error (rule U4). This includes `it.address = ...` and then `it.address.city = ...`,
or `it.customer = ...` and then `it.customer?.id = ...`.

### `where` or `all()` is required

`set()` returns a type **without** `execute()`. Only `where()` and `all()` return a type with `execute()`.
So a forgotten `where` is a compile error (rule U5). The same is true for `delete`.

### Association paths: same rows as select

In `where`, a non-FK association path becomes a **correlated `exists`** (rule U9):

```kotlin
.where { it.customer?.name == "Kim" }
// → where exists (select 1 from Customer c where c.id = o.customer.id and c.name = 'Kim')
```

On the right side of SET, it becomes a **correlated scalar subquery**:

```kotlin
it.custName = it.customer?.name
// → set o.custName = (select c.name from Customer c where c.id = o.customer.id)
```

Why? If lambdaQL gave the path to Hibernate directly, Hibernate would make a different join SQL for each database
(H2 `merge`, MariaDB `update ... join`, Oracle an inline view), and with a path in SET **rows without a customer were lost**
(checked: with `.all()`, only 9 of 10 rows were updated).
lambdaQL writes the subqueries itself. So `update` and `delete` touch **exactly the rows** that the same `where` lambda returns in a select.
A select uses an inner join for a path in `where` by default (decision D36), so a `where` on a customer path does not touch orders without a customer, unless a null guard keeps them.

A path that ends with the `@Id` (`it.customer?.id`) is the FK column. No subquery, no join.

### Batching for many rows

`values(list) { ... }` writes one statement with many VALUES rows (rule U13).

| Point | Rule |
|---|---|
| Split unit | the number of bind values (rows x columns), not the number of rows |
| Default limit | 1000 bind values per statement |
| Return value | the sum of all parts |
| Transaction | splitting is allowed only inside a transaction |
| Empty list | no SQL, returns 0 |

Oracle has a hard limit of 65,535 bind values (ORA-01745). Parsing gets very slow before that
(about 6 minutes for 21,845 rows in a test). So keep the default limit.

### The persistence context is not updated

Bulk DML does not go through the JPA persistence context (rule U16):

- Hibernate flushes pending changes before the statement.
- After the statement, entities already loaded in the same session have **old values**. Call `entityManager.clear()` if you use them again.
- JPA `cascade` and `orphanRemoval` do **not** run. Delete children first, or use `ON DELETE CASCADE` in the database.

---

## 7.8 All rules U1 to U21 (summary)

| # | Rule |
|---|---|
| U1 | An assignment lambda contains **only assignments** (setter calls) on the target, its embedded paths, or the `@Id` of an association. `if`, `when`, loops, local variables, `++`, scope functions and other calls are errors. A lambda with no assignment is an error. |
| U2 | The right side follows the value rules of `where` ([3. Conditions](03-where.md)). Captured values are bind parameters. `a ?: b` becomes `coalesce(a, b)`. |
| U3 | The right side reads the **old** value. Reading a column after assigning it in the same statement is an error. |
| U4 | Assigning the same physical column twice is an error, also across `set()` calls. |
| U5 | No `execute()` without `where()` or `all()`. Checked by the compiler. |
| U6 | Assigning `null` to a non-optional column (`@Id`, `nullable=false`, primitive) is an error. Assigning a collection, or a non-`@Id` property of an associated entity, is an error. |
| U7 | Each use of a captured value gets its own parameter. (Sharing one enum parameter between a STRING column and an ORDINAL column silently stored a wrong value in tests.) |
| U8 | `LocalDateTime.now()` is evaluated in the application and bound. The database time (`current_timestamp`) is not supported. |
| U9 | Non-FK association paths in DML: in `where`, a correlated `exists` (the same rows as the select, D36); on the right side of SET, a correlated scalar subquery. DML touches the same rows as select. |
| U10 | In `insert ... select`, source paths become an explicit `left join`. |
| U11 | `it.customer = entity` binds the `@Id` of the entity. If the id is null, binding fails. |
| U12 | IDENTITY and SEQUENCE ids can be left out. A TABLE generator works only with `values`, not with `insert ... select`. Without a generator, the id is required. Other non-optional properties are required. |
| U13 | Many rows are split by bind count (default 1000). Results are added. Only inside a transaction. |
| U14 | One `set` lambda can assign many columns, and `set()` can be called many times. All join into one SET list in call order. |
| U15 | `@Version` entities use `update versioned`. Do not assign the version yourself. Checking `execute() == 0` is your job. |
| U16 | Bulk DML skips the persistence context. Flush before, stale entities after, no cascade. |
| U17 | If a DELETE subquery reads the same entity, lambdaQL wraps it in a derived table. (MariaDB error 1093 otherwise.) |
| U18 | Inheritance: SINGLE_TABLE is fully supported. JOINED and TABLE_PER_CLASS update/delete use temporary tables. Insert into their subclasses is rejected. |
| U19 | The query cache key includes the entry point, the target entity, the set lambdas, where/all, versioned, and the split size. |
| U20 | `by[column] = value` / `by.set(column, value)`: the column must be a property path on the target. Types are checked at analysis time. Do not mix property assignments and `by` in one lambda. |
| U21 | Each lambda is analyzed once and cached. Checks across `set()` calls only compare cached summaries (about 0.1 to 0.2 µs in a prototype). |

---

## 7.9 Not in v0.1

| Item | Plan | What to do now |
|---|---|---|
| Conditional value (`if (c) a else b`, `when`, a toggle `!it.active` as a value) | v0.2 (CASE WHEN) | two updates with different `where` |
| `if` inside the lambda to choose an assignment | v0.2 | call `set()` many times (U14) |
| `++`, `--`, `apply`, `with`, `also` | v0.2 | use `+= 1` or a direct assignment |
| String templates or `+` (`"${it.description}_x"`) | v0.2 | – |
| Explicit join in `insert ... select` | v0.2 | use many-to-one paths |
| Upsert (`on conflict`) | v0.2 | – |
| DML with `order by ... limit` | rejected | select the ids first, then delete by the id list |
| `returning` | rejected | query again after the DML |
| Database time (`current_timestamp`) | rejected | bind an application time (U8) |

Errors from this chapter are `LambdaQLException` subclasses with a `[LQL-xxxx]` code. See [11. Errors and languages](../11-errors-and-languages.md).
All rules in one place: [10. Rules and limits](10-rules-and-limits.md).

---

[Kotlin track](README.md) | Previous: [6. Select, aggregates and group by](06-select-group.md) | Next: [8. Procedures and DB functions](08-procedures-functions.md) | [Java version of this page](../java/07-dml.md)
