# 7. Update, insert, delete (Java)

> [!IMPORTANT]
> This page is older than the Korean manual. See the [Korean page](../../ko/java/07-dml.md) for the latest rules.

> [!NOTE]
> **Status**
> - **Implemented and tested** on H2 2.3, MariaDB 10.11 and Oracle 23.26 (Hibernate 6.6.5, milestone M10, decisions D28 and D40): update (setters, `by.set`, many `set()` calls, `@Version`), delete, insert (one row, a list, a generated id, `insert ... select`), upsert, the checks for rules U1, U3, U4 and U6, and (decision D41) `limit` for update and delete and the one-row protection `executeOne()`.
> - **Also implemented and tested:** subqueries (`sub`) in the `where` of update and delete (milestone M8, see [7.4](07-4-delete.md)), and `groupBy`, `having` and `Agg` in `insert ... select` (milestone M9, see [7.5](07-5-insert.md)).
> - **Not implemented yet:** subqueries in the values of `set` (the context object `by.from(...)`), and `limit` / `executeOne()` on entities with a composite key.
> - Examples marked **(tested)** are copied from the test suite. The other examples follow the design. They are not covered by a test yet, and their HQL can differ in small details (for example alias names like `e0`).
> - See the [Java track README](README.md#status) for the full status.

[Java track](README.md) | [Manual home](../README.md) | Previous: [6. Select, aggregates and group by](06-select-group.md) | Next: [8. Procedures and DB functions](08-procedures-functions.md) | [Kotlin version of this page](../kotlin/07-dml.md)

Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md)

---

lambdaQL has five entry points:

| Entry point | Use |
|---|---|
| `qb.from(X.class)` | select (chapters 3 to 6) |
| `qb.update(X.class)` | update rows |
| `qb.insert(X.class)` | insert rows |
| `qb.delete(X.class)` | delete rows |
| `qb.upsert(X.class)` | insert a row, or change it if a row with the same `@Id` exists |

The ideas are the same as for queries:

- You write **one lambda** for each part. No static helpers.
- The lambda **never runs**. lambdaQL reads its bytecode. A setter call like `o.setStatus(1)` becomes `set e0.status = 1`.
- `where` uses the same rules as in [3. Conditions](03-where.md), and it changes the same rows as a select with the same lambda.
- `execute()` runs the statement and returns the **number of affected rows** (`int`).
- `executeOne()` runs an update or delete that must change **at most one row** (see [One-row protection](#one-row-protection)).
- `orderBy(...).limit(n)` changes only the first `n` rows (see [Limit](#limit-change-the-first-rows)).
- `toHql()` gives the HQL and the parameters without running the statement.
- A statement needs a transaction (see [Transactions](#transactions)).

## Contents

- [7.1 Example entities](#71-example-entities)
- [7.2 Update](#72-update)
- [7.3 Two ways to assign: setter or `by.set`](#73-two-ways-to-assign-setter-or-byset)
- [7.4 Delete](#74-delete)
- [7.5 Insert](#75-insert)
- [7.6 Upsert](#76-upsert)
- [7.7 Java notes for DML](#77-java-notes-for-dml)
- [7.8 Important rules](#78-important-rules)
- [7.9 All rules U1 to U21 (summary)](#79-all-rules-u1-to-u21-summary)
- [7.10 Not in v0.1](#710-not-in-v01)

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

The tested examples use the test entities in [`query/src/test/java/org/lambdaql/query`](https://github.com/codebit0/lambdaql/tree/main/query/src/test/java/org/lambdaql/query). They are a little different: in the test `Order`, `price` is `double`, `status` is `short`, and there is a `float` field `tex`. `IdOrder` has an IDENTITY id.

---

## 7.2 Update

→ [7.2 Update](07-2-update.md)

## 7.3 Two ways to assign: setter or `by.set`

| Style | When to use | Example |
|---|---|---|
| Setter | The entity has setters. The compiler checks the value type. | `set(o -> o.setStatus(1))` |
| `by.set(column, value)` | The entity has **no setters** (for example DDD style entities). | `set((o, by) -> by.set(o.getStatus(), 1))` |

**(tested)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java), region `update-by`:

```java
ExecutableUpdate<Order> u = qb.update(Order.class)
        .set((o, by) -> by.set(o.getDescription(), "done"))
        .where(o -> o.getStatus() == 2);
assertEquals("update Order e0 set e0.description = 'done' where e0.status = 2", u.toHql().hql());
```

**About the SQL blocks.** `…` is the shortened select list (all entity columns), and `?` is a bind parameter. The lines come from [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java). It runs each query on H2 2.3, MariaDB 10.11 and Oracle 23.26 with Hibernate 6.6.5, and it fails if Hibernate's SQL changes.

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `update-by`:

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

Every setter example in this chapter can also be written with `by.set`. Chain the calls: the lambda returns the last `by.set(...)`.

```java
qb.update(Order.class)
  .set((o, by) -> by.set(o.getStatus(), st).set(o.getPrice(), o.getPrice() * 1.1))
  .where(o -> o.getStatus() == 1)
  .execute();

qb.update(Order.class).set((o, by) -> by.set(o.getAddress().getCity(), city)).where(o -> o.getId() == id).execute(); // embedded
qb.update(Order.class).set((o, by) -> by.set(o.getCustomer().getId(), cid)).where(o -> o.getId() == id).execute();  // FK
```

In the design, the second lambda argument is **one context object**. It has both `set(column, value)` and the subquery method `from(...)`.
Name it `by` or `sub` as you like. The name does not matter.
The subquery part of this object is **not implemented yet**. Today the object has only `set`. (Subqueries in `where` work: `where((o, sub) -> ...)`, see [5. Subqueries](05-subquery.md).)

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
- The lambda must be one chain of `by.set(...)` calls. Other statements are an error.
- In Java, `by.set(o.getStatus(), "abc")` compiles, because the types are generic.
  The design checks the column type against the value type at analysis time. This check is not implemented yet.
- Do not mix setters and `by.set` **in one lambda**. You can mix them in different `set()` calls.

---

## 7.4 Delete

→ [7.4 Delete](07-4-delete.md)

## 7.5 Insert

→ [7.5 Insert](07-5-insert.md)

## 7.6 Upsert

→ [7.6 Upsert](07-6-upsert.md)

## 7.7 Java notes for DML

| Java form | Meaning |
|---|---|
| Block lambda `o -> { o.setA(...); o.setB(...); }` | allowed in `set`, `values`, `insert ... select` and `onConflict(...).update(...)`, but **only setter calls**. No local variables, `if` or loops (rule U1) |
| `Objects.requireNonNullElse(a, b)` | `coalesce(a, b)` (rule U2) |
| `o.setCnt(o.getCnt() + 1)` | supported. javac unboxes and boxes the value; lambdaQL ignores that |
| `o.setStatus((short) 1)` | a cast that javac needs for a `short` or `byte` field. The constant stays a literal |
| `o.setActive(!o.isActive())` (toggle) | **not in v0.1** (it is a conditional value). Use two updates with different `where` |
| `o.setDescription(o.getDescription() + "_x")` | **not in v0.1** (string concatenation) |
| `LocalDateTime.now()` as a value | computed in the application and bound (rule U8) |
| `UpdateSet<Order> u = qb.update(...).set(...)` (or `var`) | build an update step by step (see [PATCH](07-2-update.md#many-set-calls-patch)) |

---

## 7.8 Important rules

### The right side reads the old value

In SQL, every column on the right side of SET means the value **before** the update. lambdaQL follows this rule.

```java
.set(o -> { o.setPrice(o.getPrice() * 2); o.setTotal(o.getPrice()); })
// error: price is assigned first, then read again
```

If you **assign a column and then read it later in the same statement**, lambdaQL throws `TranslationException` (`LQL-3042`) when you call `set(...)` (rule U3).
The reason: MariaDB uses the **new** value in this case, while H2 and Oracle use the old value. The results differ (checked: MariaDB 200, H2 and Oracle 100).

These cases are fine, because all three databases agree:

| Form | OK? |
|---|---|
| `o.setPrice(o.getPrice() * 1.1)` (read itself) | yes |
| `o.setTotal(o.getPrice()); o.setPrice(0.0);` (read first, assign later) | yes |
| `o.setPrice(...); o.setTotal(o.getPrice());` (assign first, read later) | **error** |

The check works on physical columns (embedded sub-columns, FK columns) and across many `set()` calls (tested in [PATCH](07-2-update.md#many-set-calls-patch)).

### Assign a column only once

Assigning the same column twice is an error (rule U4). This includes `setAddress(...)` and then `getAddress().setCity(...)`,
or `setCustomer(...)` and then `getCustomer().setId(...)`.

### Rules checked at analysis time

lambdaQL checks rules U1, U3, U4 and U6 when it reads the lambda. This test calls the internal class `AssignmentReader`, not the user API. The user API throws the same `LambdaQLException` from `set(...)` and `values(...)` (for example `LQL-3037` for U1, `LQL-3042` for U3, `LQL-3041` for U4).

**(tested)** [`AssignmentReaderTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/sem/AssignmentReaderTest.java), region `rules`:

```java
// U1: only setters; no condition, no local variable
assertThrows(LambdaQLException.class, () -> setters(o -> { if (o.isActive()) o.setStatus((short) 1); }));
// U6: an association's other properties belong to another table
assertThrows(LambdaQLException.class, () -> setters(o -> o.getCustomer().setName("x")));
// U3: a value reads a column written before
assertThrows(LambdaQLException.class, () -> AssignmentReader.checkRules(
        AssignmentReader.readSetters(SerializedLambdaExtractor.extract((Assign<Order>) o -> {
            o.setPrice(1);
            o.setTex((float) o.getPrice());
        }), List.of(Order.class), -1, new JpaEntityModel(emf.getMetamodel())), "test"));
```

### `where` or `all()` is required

`set()` returns a type **without** `execute()` (`UpdateSet`). Only `where()` and `all()` return a type with `execute()` (`ExecutableUpdate`).
So a forgotten `where` is a compile error (rule U5). The same is true for `delete`.

### Association paths: same rows as select

lambdaQL writes a non-FK association path in DML as a **correlated scalar subquery**, in `where` and on the right side of SET (rule U9). These lines come from the tested example in [7.2](07-2-update.md#a-value-from-another-table):

```java
.where(o -> o.getCustomer().getName().equals("Kim"))
// → where (select s1.name from Customer s1 where s1.id = e0.customer.id) = 'Kim' and e0.customer.id is not null

o.setCustName(o.getCustomer().getName())
// → set e0.custName = (select s1.name from Customer s1 where s1.id = e0.customer.id)
```

Why? If lambdaQL gave the path to Hibernate directly, Hibernate would make a different join SQL for each database
(H2 `merge`, MariaDB `update ... join`, Oracle an inline view), and with a path in SET **rows without a customer were lost**
(checked: with `.all()`, only 9 of 10 rows were updated).
lambdaQL writes the subqueries itself. The statement then reads only its own table, so MariaDB's limit on reading the target table (error 1093) does not apply.

A select uses an inner join for a path in `where` (decision D36). So in DML, lambdaQL adds `e0.customer.id is not null` for such a path.
Then `update` and `delete` touch **exactly the rows** that `qb.from(X.class).where(same lambda)` returns (tested: the deleted rows are compared with the select).
A null guard such as `o.getCustomer() == null || ...` does not change this: the select uses an inner join for the path too (decision D48), so the check stays.

A path that ends with the `@Id` (`o.getCustomer().getId()`) is the FK column. No subquery, no join.

### Batching for many rows

`values(list, ...)` writes one statement with many VALUES rows (rule U13).

| Point | Rule |
|---|---|
| Split unit | the number of values (rows x columns), not the number of rows |
| Limit | at most 500 values per statement (fixed) |
| Return value | the sum of all statements |
| Transaction | all statements run in one transaction |
| Empty list | no SQL, returns 0 (tested) |

Oracle has a hard limit of 65,535 bind values (ORA-01745). Parsing gets very slow long before that
(about 6 minutes for 21,845 rows in a design test). That is why the limit is small.

### Limit: change the first rows

`orderBy(...).limit(n)` after `where(...)` or `all()` makes an update or delete change at most `n` rows: the first `n` rows of `qb.from(X.class).where(same).orderBy(same)`.

```java
qb.delete(Order.class).where(o -> o.getStatus() == 1).orderBy((o, by) -> by.asc(o.getPrice())).limit(100).execute();
```

| Point | Rule |
|---|---|
| Order | the `orderBy` keys, then the id (rule S9). Without `orderBy`, by the id |
| `orderBy` without `limit` | error (`IllegalStateException`) |
| Rendering | `e0.id in (select t.id from (select x0.id as id from X x0 ... order by ... limit n) t)` |
| Rows | the inner select follows the select rules, joins included (D36), so the rows are the same as the select |
| MariaDB | the derived table `t` avoids errors 1093 and 1235 |

Tested examples and the SQL of each database: [7.4](07-4-delete.md#delete-the-first-rows-limit) (delete) and [7.2](07-2-update.md#update-the-first-rows-limit) (update).

### One-row protection

`executeOne()` instead of `execute()` changes **at most one row**:

1. lambdaQL reads the ids of the rows that match, at most 2.
2. With 2 ids, it changes nothing and throws `NonUniqueResultException`.
3. With 1 id, it changes that row with `id = :p and (your condition)`.

It never changes more than one row, and it does not rely on a rollback. The result is 0 or 1. `executeOne()` together with `orderBy` or `limit` is an error (`IllegalStateException`).
Tested example: [7.4](07-4-delete.md#delete-exactly-one-row-executeone). In a Spring repository: `deleteOne` and `updateOne` (see [12.4](12-spring-data-jpa.md#one-row-protection-deleteone-and-updateone)).

### Transactions

A bulk statement needs a transaction. lambdaQL opens one only with the `EntityManagerFactory` constructor.

| Builder | Transaction |
|---|---|
| `new QueryBuilder(entityManagerFactory)` | `execute()` and `executeOne()` open their own transaction and commit it. On an error they roll back |
| A builder with an `EntityManager`: `new QueryBuilder(entityManager)`, Spring's `QueryBuilder` bean, the repository executor | the caller's transaction (`@Transactional`, or an active transaction of that `EntityManager`). Without one, the statement fails with `TransactionRequiredException` before any SQL is sent (tested). In Spring repository calls, it becomes `InvalidDataAccessApiUsageException` (see [12.6](12-spring-data-jpa.md#126-transactions-and-exceptions)) |

### The persistence context is not updated

Bulk DML does not go through the JPA persistence context (rule U16):

- Hibernate flushes pending changes before the statement.
- After the statement, entities already loaded in the same `EntityManager` have **old values**. Call `entityManager.clear()` if you use them again.
- JPA `cascade` and `orphanRemoval` do **not** run. Delete children first, or use `ON DELETE CASCADE` in the database.

---

## 7.9 All rules U1 to U21 (summary)

Checked when lambdaQL reads the lambda: U1, U3, U4, U6 (tested). Checked by the compiler: U5.
The lambdaQL checks of U11, U12, U15 and U20 are not implemented yet. U17 (the MariaDB wrap for a subquery on the same table) is not implemented yet.

| # | Rule |
|---|---|
| U1 | An assignment lambda contains **only setter calls** on the target, its embedded paths, or the `@Id` of an association. `if`, the ternary `c ? a : b`, loops, local variables and other method calls are errors. A lambda with no assignment is an error. |
| U2 | The right side follows the value rules of `where` ([3. Conditions](03-where.md)). Captured values are bind parameters, constants in the lambda are literals (D37). `Objects.requireNonNullElse(a, b)` becomes `coalesce(a, b)`. |
| U3 | The right side reads the **old** value. Reading a column after assigning it in the same statement is an error. |
| U4 | Assigning the same physical column twice is an error, also across `set()` calls. |
| U5 | No `execute()` without `where()` or `all()`. Checked by the compiler. |
| U6 | Assigning `null` to a non-optional column (`@Id`, `nullable=false`, primitive) is an error. Assigning a collection, or a non-`@Id` field of an associated entity, is an error. |
| U7 | Each use of a captured value gets its own parameter. (Sharing one enum parameter between a STRING column and an ORDINAL column silently stored a wrong value in tests.) |
| U8 | `LocalDateTime.now()` is evaluated in the application and bound. The database time (`current_timestamp`) is not supported. |
| U9 | Non-FK association paths in DML become correlated scalar subqueries, in `where` and on the right side of SET. In `where`, a path that a select would inner join (D36) also gets `customer.id is not null`. DML touches the same rows as select. |
| U10 | In `insert ... select`, source paths become an explicit `left join`. |
| U11 | `o.setCustomer(entity)` binds the `@Id` of the entity. If the id is null, binding fails. |
| U12 | IDENTITY and SEQUENCE ids can be left out. A TABLE generator works only with `values`, not with `insert ... select`. Without a generator, the id is required. Other non-optional fields are required. |
| U13 | Many rows are split: at most 500 values per statement. Results are added. All statements run in one transaction. |
| U14 | One `set` lambda can assign many columns, and `set()` can be called many times. All join into one SET list in call order. |
| U15 | `@Version` entities use `update versioned`. Do not assign the version yourself. Checking `execute() == 0` is your job. |
| U16 | Bulk DML skips the persistence context. Flush before, stale entities after, no cascade. |
| U17 | If a DELETE subquery reads the same entity, lambdaQL wraps it in a derived table. (MariaDB error 1093 otherwise.) |
| U18 | Inheritance: SINGLE_TABLE is fully supported. JOINED and TABLE_PER_CLASS update/delete use temporary tables. Insert into their subclasses is rejected. |
| U19 | The query cache key includes the entry point, the target entity, the set lambdas, where/all, versioned, and the split size. |
| U20 | `by.set(column, value)`: the column must be a getter path on the target. Types are checked at analysis time. Do not mix setters and `by.set` in one lambda. |
| U21 | Each lambda is analyzed once and cached. Checks across `set()` calls only compare cached summaries (about 0.1 to 0.2 µs in a prototype). |

The upsert rules (decision D40) are in [7.6 Upsert](07-6-upsert.md#rules).

---

## 7.10 Not in v0.1

| Item | Plan | What to do now |
|---|---|---|
| Conditional value (`c ? a : b`, `!o.isActive()` as a value) | v0.2 (CASE WHEN) | – |
| `if` inside the lambda to choose an assignment | v0.2 | call `set()` many times (U14) |
| String concatenation (`o.getDescription() + "_x"`) | v0.2 | – |
| Explicit join in `insert ... select` | v0.2 | use many-to-one paths |
| `returning` | rejected | – |
| Database time (`current_timestamp`) | rejected | bind an application time (U8) |

A broken rule throws a `LambdaQLException` with a `[LQL-xxxx]` code (`TranslationException` for most rules, `UnsupportedLambdaShapeException` for a condition or a local variable in an assignment lambda). See [11. Errors and languages](../11-errors-and-languages.md).
All rules in one place: [10. Rules and limits](10-rules-and-limits.md).

---

[Java track](README.md) | Previous: [6. Select, aggregates and group by](06-select-group.md) | Next: [8. Procedures and DB functions](08-procedures-functions.md) | [Kotlin version of this page](../kotlin/07-dml.md)
