# 7. Update, insert, delete (Java)

> [!NOTE]
> **Status: designed, not implemented yet.**
> This chapter is a decided design (decision D28, milestone M10).
> The syntax compiles in Java, and the SQL was checked on H2, MariaDB and Oracle with Hibernate 6.6.5.
> It does **not** run in lambdaQL today. Today only the analysis core and `where` + `toList()` work
> (tested on H2, MariaDB and Oracle). See the [Java track README](README.md#status) for the full status.

[Java track](README.md) | [Manual home](../README.md) | Previous: [6. Select, aggregates and group by](06-select-group.md) | Next: [8. Procedures and DB functions](08-procedures-functions.md) | [Kotlin version of this page](../kotlin/07-dml.md)

Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md)

---

lambdaQL has four entry points:

| Entry point | Use |
|---|---|
| `qb.from(X.class)` | select (chapters 3 to 6) |
| `qb.update(X.class)` | update rows |
| `qb.insert(X.class)` | insert rows |
| `qb.delete(X.class)` | delete rows |

The ideas are the same as for queries:

- You write **one lambda** for each part. No static helpers.
- The lambda **never runs**. lambdaQL reads its bytecode. A setter call like `o.setStatus(1)` becomes `set o.status = 1`.
- `where` uses the same rules as in [3. Conditions](03-where.md). Subqueries use `sub` as in [5. Subqueries](05-subquery.md).
- `execute()` runs the statement and returns the **number of affected rows** (`int`).

## Contents

- [7.1 Example entities](#71-example-entities)
- [7.2 Update](#72-update)
- [7.3 Two ways to assign: setter or `by.set`](#73-two-ways-to-assign-setter-or-byset)
- [7.4 Delete](#74-delete)
- [7.5 Insert](#75-insert)
- [7.6 Java notes for DML](#76-java-notes-for-dml)
- [7.7 Important rules](#77-important-rules)
- [7.8 All rules U1 to U21 (summary)](#78-all-rules-u1-to-u21-summary)
- [7.9 Not in v0.1](#79-not-in-v01)

---

## 7.1 Example entities

This chapter uses `Order`, `Customer` and `Item` (see [2. Getting started](02-getting-started.md#example-entities)).
`Order` also has a few extra fields here:

| Field | Type | Note |
|---|---|---|
| `id`, `price`, `status`, `active`, `description`, `updateAt`, `customer` | | as in other chapters |
| `total` | `Double` | sum of item prices |
| `cnt` | `Integer` | a counter |
| `custName` | `String` | copy of the customer name |
| `address` | `Address` (embedded: `city`, `zip`) | embedded value |

Other entities: `VOrder` (an order with a `@Version` field `version`), `IdOrder` (an order with a generated id), `Archive` (`id`, `price`, `customerName`) and `Summary` (`customerId`, `total`, `cnt`).

---

## 7.2 Update

→ [7.2 Update](07-2-update.md)

## 7.3 Two ways to assign: setter or `by.set`

| Style | When to use | Example |
|---|---|---|
| Setter | The entity has setters. The compiler checks the value type. | `set(o -> o.setStatus(1))` |
| `by.set(column, value)` | The entity has **no setters** (for example DDD style entities). | `set((o, by) -> by.set(o.getStatus(), 1))` |

Every setter example above can also be written with `by.set`:

```java
qb.update(Order.class)
  .set((o, by) -> by.set(o.getStatus(), st).set(o.getPrice(), o.getPrice() * 1.1))
  .where(o -> o.getStatus() == 1)
  .execute();

qb.update(Order.class).set((o, by) -> by.set(o.getAddress().getCity(), city)).where(o -> o.getId() == id).execute(); // embedded
qb.update(Order.class).set((o, by) -> by.set(o.getCustomer().getId(), cid)).where(o -> o.getId() == id).execute();  // FK
```

The second lambda argument is **one context object**. It has both `set(column, value)` and the subquery method `from(...)`.
Name it `by` or `sub` as you like. The name does not matter.

```java
qb.update(Order.class)
  .set((o, by) -> by.set(o.getTotal(),
        by.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).sum(i -> i.getPrice())))
  .all()
  .execute();
```

Rules for `by.set` (rule U20):

- The first argument must be a **column path that starts at the target**: a field, an embedded path, or the `@Id` of an association.
  A captured value, a calculation, or a path of the source entity is an error.
- In Java, `by.set(o.getStatus(), "abc")` compiles, because the types are generic.
  lambdaQL checks the column type against the value type at analysis time.
- Do not mix setters and `by.set` **in one lambda**. You can mix them in different `set()` calls.

---

## 7.4 Delete

→ [7.4 Delete](07-4-delete.md)

## 7.5 Insert

→ [7.5 Insert](07-5-insert.md)

## 7.6 Java notes for DML

| Java form | Meaning |
|---|---|
| Block lambda `o -> { o.setA(...); o.setB(...); }` | allowed in `set`, `values` and `insert ... select`, but **only setter calls**. No local variables, `if` or loops (rule U1) |
| `Objects.requireNonNullElse(a, b)` | `coalesce(a, b)` (rule U2) |
| `o.setCnt(o.getCnt() + 1)` | supported. javac unboxes and boxes the value; lambdaQL ignores that |
| `o.setActive(!o.isActive())` (toggle) | **not in v0.1** (it is a conditional value). Use two updates with different `where` |
| `o.setDescription(o.getDescription() + "_x")` | **not in v0.1** (string concatenation) |
| `LocalDateTime.now()` as a value | computed in the application and bound (rule U8) |
| `var u = qb.update(...).set(...)` | use `var` to build an update step by step |

---

## 7.7 Important rules

### The right side reads the old value

In SQL, every column on the right side of SET means the value **before** the update. lambdaQL follows this rule.

```java
.set(o -> { o.setPrice(o.getPrice() * 2); o.setTotal(o.getPrice()); })
// error: price is assigned first, then read again
```

If you **assign a column and then read it later in the same statement**, lambdaQL stops with an error at analysis time (rule U3).
The reason: MariaDB uses the **new** value in this case, while H2 and Oracle use the old value. The results differ (checked: MariaDB 200, H2 and Oracle 100).

These cases are fine, because all three databases agree:

| Form | OK? |
|---|---|
| `o.setPrice(o.getPrice() * 1.1)` (read itself) | yes |
| `o.setTotal(o.getPrice()); o.setPrice(0.0);` (read first, assign later) | yes |
| `o.setPrice(...); o.setTotal(o.getPrice());` (assign first, read later) | **error** |

The check works on physical columns (embedded sub-columns, FK columns), across many `set()` calls, and inside nested `sub` lambdas.

### Assign a column only once

Assigning the same column twice is an error (rule U4). This includes `setAddress(...)` and then `getAddress().setCity(...)`,
or `setCustomer(...)` and then `getCustomer().setId(...)`.

### `where` or `all()` is required

`set()` returns a type **without** `execute()`. Only `where()` and `all()` return a type with `execute()`.
So a forgotten `where` is a compile error (rule U5). The same is true for `delete`.

### Association paths: same rows as select

In `where`, a non-FK association path becomes a **correlated `exists`** (rule U9):

```java
.where(o -> o.getCustomer().getName().equals("Kim"))
// → where exists (select 1 from Customer c where c.id = o.customer.id and c.name = 'Kim')
```

On the right side of SET, it becomes a **correlated scalar subquery**:

```java
o.setCustName(o.getCustomer().getName())
// → set o.custName = (select c.name from Customer c where c.id = o.customer.id)
```

Why? If lambdaQL gave the path to Hibernate directly, Hibernate would make a different join SQL for each database
(H2 `merge`, MariaDB `update ... join`, Oracle an inline view), and with a path in SET **rows without a customer were lost**
(checked: with `.all()`, only 9 of 10 rows were updated).
lambdaQL writes the subqueries itself. So `update` and `delete` touch **exactly the rows** that the same `where` lambda returns in a select.
A select uses an inner join for a path in `where` by default (decision D36), so a `where` on a customer path does not touch orders without a customer, unless a null guard keeps them.

A path that ends with the `@Id` (`o.getCustomer().getId()`) is the FK column. No subquery, no join.

### Batching for many rows

`values(list, ...)` writes one statement with many VALUES rows (rule U13).

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
| U1 | An assignment lambda contains **only setter calls** on the target, its embedded paths, or the `@Id` of an association. `if`, the ternary `c ? a : b`, loops, local variables and other method calls are errors. A lambda with no assignment is an error. |
| U2 | The right side follows the value rules of `where` ([3. Conditions](03-where.md)). Captured values are bind parameters. `Objects.requireNonNullElse(a, b)` becomes `coalesce(a, b)`. |
| U3 | The right side reads the **old** value. Reading a column after assigning it in the same statement is an error. |
| U4 | Assigning the same physical column twice is an error, also across `set()` calls. |
| U5 | No `execute()` without `where()` or `all()`. Checked by the compiler. |
| U6 | Assigning `null` to a non-optional column (`@Id`, `nullable=false`, primitive) is an error. Assigning a collection, or a non-`@Id` field of an associated entity, is an error. |
| U7 | Each use of a captured value gets its own parameter. (Sharing one enum parameter between a STRING column and an ORDINAL column silently stored a wrong value in tests.) |
| U8 | `LocalDateTime.now()` is evaluated in the application and bound. The database time (`current_timestamp`) is not supported. |
| U9 | Non-FK association paths in DML: in `where`, a correlated `exists` (the same rows as the select, D36); on the right side of SET, a correlated scalar subquery. DML touches the same rows as select. |
| U10 | In `insert ... select`, source paths become an explicit `left join`. |
| U11 | `o.setCustomer(entity)` binds the `@Id` of the entity. If the id is null, binding fails. |
| U12 | IDENTITY and SEQUENCE ids can be left out. A TABLE generator works only with `values`, not with `insert ... select`. Without a generator, the id is required. Other non-optional fields are required. |
| U13 | Many rows are split by bind count (default 1000). Results are added. Only inside a transaction. |
| U14 | One `set` lambda can assign many columns, and `set()` can be called many times. All join into one SET list in call order. |
| U15 | `@Version` entities use `update versioned`. Do not assign the version yourself. Checking `execute() == 0` is your job. |
| U16 | Bulk DML skips the persistence context. Flush before, stale entities after, no cascade. |
| U17 | If a DELETE subquery reads the same entity, lambdaQL wraps it in a derived table. (MariaDB error 1093 otherwise.) |
| U18 | Inheritance: SINGLE_TABLE is fully supported. JOINED and TABLE_PER_CLASS update/delete use temporary tables. Insert into their subclasses is rejected. |
| U19 | The query cache key includes the entry point, the target entity, the set lambdas, where/all, versioned, and the split size. |
| U20 | `by.set(column, value)`: the column must be a getter path on the target. Types are checked at analysis time. Do not mix setters and `by.set` in one lambda. |
| U21 | Each lambda is analyzed once and cached. Checks across `set()` calls only compare cached summaries (about 0.1 to 0.2 µs in a prototype). |

---

## 7.9 Not in v0.1

| Item | Plan | What to do now |
|---|---|---|
| Conditional value (`c ? a : b`, `!o.isActive()` as a value) | v0.2 (CASE WHEN) | – |
| `if` inside the lambda to choose an assignment | v0.2 | call `set()` many times (U14) |
| String concatenation (`o.getDescription() + "_x"`) | v0.2 | – |
| Explicit join in `insert ... select` | v0.2 | use many-to-one paths |
| Upsert (`on conflict`) | v0.2 | – |
| DML with `order by ... limit` | rejected | select the ids first, then delete by the id list |
| `returning` | rejected | – |
| Database time (`current_timestamp`) | rejected | bind an application time (U8) |

Errors from this chapter are `LambdaQLException` subclasses with a `[LQL-xxxx]` code. See [11. Errors and languages](../11-errors-and-languages.md).
All rules in one place: [10. Rules and limits](10-rules-and-limits.md).

---

[Java track](README.md) | Previous: [6. Select, aggregates and group by](06-select-group.md) | Next: [8. Procedures and DB functions](08-procedures-functions.md) | [Kotlin version of this page](../kotlin/07-dml.md)
