<a id="7-update-insert-delete-java"></a>
# 7. Update, insert, delete (Java)

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

- You write **one lambda** for each part. The only static helper is `Dml.value`, for entities without setters ([7.3](#73-two-ways-to-assign-setter-or-dmlvalue)).
- The lambda **never runs**. lambdaQL reads its bytecode. A setter call like `o.setStatus(1)` becomes `set e0.status = 1`.
- `where` uses the same rules as in [3. Conditions (`where`)](03-where.md). It targets the same rows as a select with the same lambda.
- `execute()` runs the statement and returns the **number of affected rows** (`int`).
- `executeOne()` runs an update or delete that must change **at most one row** (see [One-row protection](#one-row-protection)).
- `orderBy(...).limit(n)` changes only the first `n` rows (see [Limit](#limit-change-the-first-rows)).
- `toHql()` gives the HQL and the parameters without running the statement.
- A statement needs a transaction (see [Transactions](#transactions)).

<a id="contents"></a>
## Contents

- [7.1 Example entities](#71-example-entities)
- [7.2 Update](#72-update)
- [7.3 Two ways to assign: setter or `Dml.value`](#73-two-ways-to-assign-setter-or-dmlvalue)
- [7.4 Delete](#74-delete)
- [7.5 Insert](#75-insert)
- [7.6 Upsert](#76-upsert)
- [7.7 Java notes for DML](#77-java-notes-for-dml)
- [7.8 Important rules](#78-important-rules)
- [7.9 All rules U1 to U21 (summary)](#79-all-rules-u1-to-u21-summary)
- [7.10 Not in v0.1](#710-not-in-v01)

---

<a id="71-example-entities"></a>
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

<a id="72-update"></a>
## 7.2 Update

→ [7.2 Update](07-2-update.md)

<a id="73-two-ways-to-assign-setter-or-dmlvalue"></a>
## 7.3 Two ways to assign: setter or `Dml.value`

| Style | When to use | Example |
|---|---|---|
| Setter | The entity has setters. The compiler checks the value type. | `set(o -> o.setStatus(1))` |
| `Dml.value(column, value)` | The entity has **no setters** (for example DDD style entities). | `set(o -> value(o.getStatus(), 1))` |

`value` is the static function `org.lambdaql.Dml.value` (decision D49). Import it with `import static org.lambdaql.Dml.value;` and use it in a lambda with one argument.

**(tested)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java), region `update-value`:

```java
// entities without setters: Dml.value(column, value), the column written as a getter call
ExecutableUpdate<Order> u = qb.update(Order.class)
        .set(o -> value(o.getDescription(), "done"))
        .where(o -> o.getStatus() == 2);
assertEquals("update Order e0 set e0.description = 'done' where e0.status = 2", u.toHql().hql());
```

**About the SQL blocks.** `…` is the shortened select list (all entity columns), and `?` is a bind parameter. The lines come from [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java). It runs each query on H2 2.3, MariaDB 10.11 and Oracle 23.26 with Hibernate 7.4, and it fails if Hibernate's SQL changes.

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `update-value`:

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

Every setter example in this chapter can also be written with `value`. Write one `value` call per statement.

```java
import static org.lambdaql.Dml.value;

qb.update(Order.class)
  .set(o -> { value(o.getStatus(), st); value(o.getPrice(), o.getPrice() * 1.1); })
  .where(o -> o.getStatus() == 1)
  .execute();

qb.update(Order.class).set(o -> value(o.getAddress().getCity(), city)).where(o -> o.getId() == id).execute(); // embedded
qb.update(Order.class).set(o -> value(o.getCustomer().getId(), cid)).where(o -> o.getId() == id).execute();  // FK
```

Rules for `value` (rule U20):

- The first argument must be a **getter call that starts at the target**: a field, an embedded path, or the `@Id` of an association.
  A captured value, a calculation, or a path of the source entity is an error at analysis time (`LQL-3039`).
- The setter rules of the same property apply. `value(o.getX(), v)` is the same as `o.setX(v)`, so U1 and U6 apply, and for an association you can assign only the `@Id` (`LQL-3050`).
- You **can mix setters and `value` in one lambda**.
- In Java, `value(o.getStatus(), "abc")` compiles, because the type is generic.
  lambdaQL checks the value type against the column type at analysis time. If they do not match, it reports `LQL-3065`.
  A number matches a number column. But a decimal does not match a whole-number column (the fraction would be lost). A value of unknown type (`Object`, the `Number` of a generic aggregate) is not checked.
- Inside a class that has its own `value(...)` method (for example a record with a `value` component), that method hides the static import. Write `Dml.value(...)` there.
- A form like `o.getTotal() = 1` is a compile error in Java, so you cannot use it.

**(tested)** [`AssignmentReaderTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/sem/AssignmentReaderTest.java), region `value-type`:

```java
// value(V, V) compiles with any two types, so lambdaQL checks the value type against the column (U20)
assertEquals("[col0.status := 2, col0.price := 1]", setters(o -> { value(o.getStatus(), 2); value(o.getPrice(), 1); }));
String text = "abc";
LambdaQLException wrong = assertThrows(LambdaQLException.class, () -> setters(o -> value(o.getStatus(), text)));
assertEquals("LQL-3065", wrong.code(), wrong.getMessage());
// a decimal into a whole number column would lose the fraction
LambdaQLException fraction = assertThrows(LambdaQLException.class, () -> setters(o -> value(o.getStatus(), 1.5)));
assertEquals("LQL-3065", fraction.code(), fraction.getMessage());
```

**(tested)** [`AssignmentReaderTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/sem/AssignmentReaderTest.java), region `read-value`:

```java
// Dml.value(column, value) for entities without setters; constants stay literals, also when they are boxed
// for the generic value(V, V) (D37, D49)
assertEquals("[col0.status := 2, col0.price := 1.0]",
        setters(o -> { value(o.getStatus(), (short) 2); value(o.getPrice(), 1.0); }));
// setters and value can be mixed in one lambda
assertEquals("[col0.status := 2, col0.customer.id := 1]",
        setters(o -> { o.setStatus((short) 2); value(o.getCustomer().getId(), 1); }));
```

<a id="subquery-in-a-set-value"></a>
### Subquery in a `set` value

The second argument of an assignment lambda is `sub`, the start of a subquery. It is the same form as `where((o, sub) -> ...)` (decision D49).
In `insert ... select`, the third argument is `sub` (see [7.5](07-5-insert.md)).

**(tested)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java), region `update-sub`:

```java
// the second parameter starts a subquery, as in where((o, sub) -> ...) (D49)
ExecutableUpdate<Order> u = qb.update(Order.class)
        .set((o, sub) -> o.setPrice(sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).sum(i -> i.getPrice())))
        .where((o, sub) -> sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).exists());
assertEquals("update Order e0 set e0.price = (select sum(s1.price) from Item s1 where s1.order.id = e0.id)"
        + " where exists (select 1 from Item s1 where s1.order.id = e0.id)", u.toHql().hql());
// sum gives NULL when there is no row; coalesce keeps a NOT NULL column valid for every row
ExecutableUpdate<Order> all = qb.update(Order.class)
        .set((o, sub) -> value(o.getPrice(), Objects.requireNonNullElse(
                sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).sum(i -> i.getPrice()), 0.0)))
        .all();
assertEquals("update Order e0 set e0.price = coalesce((select sum(s1.price) from Item s1 where s1.order.id = e0.id), 0.0)",
        all.toHql().hql());
```

- An aggregate of another table and an aggregate of the same table can both be a `set` value (measured on H2, MariaDB 10.11 and Oracle 23.26). In an `update`, MariaDB does not report error 1093 either.
- When no row matches, `sum` and the other aggregates return NULL. For a NOT NULL column this is a DB error, so wrap the value in `Objects.requireNonNullElse(…, 0.0)`. It becomes `coalesce(...)`.
- Insert `values((a, sub) -> ...)` and upsert (`onConflict(...).update((a, sub) -> ...)`) use subqueries in the same way (see [7.5](07-5-insert.md#a-subquery-in-a-value)). In the list form, the third argument is `sub`, as in `values(list, (a, l, sub) -> ...)`.

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

<a id="77-java-notes-for-dml"></a>
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

<a id="78-important-rules"></a>
## 7.8 Important rules

<a id="the-right-side-reads-the-old-value"></a>
### The right side reads the old value

In SQL, every column on the right side of SET means the value **before** the update. lambdaQL follows this rule.

```java
.set(o -> { o.setPrice(o.getPrice() * 2); o.setTotal(o.getPrice()); })
// error: price is assigned first, then read again
```

If you **assign a column and then read it later in the same statement**, lambdaQL throws `TranslationException` (`LQL-3042`) when you call `set(...)` (rule U3).
The reason: MariaDB uses the **new** value in this case, while H2 and Oracle use the old value. The results differ (measured: MariaDB 200, H2 and Oracle 100).

These cases are allowed, because all three databases give the same result:

| Form | Allowed? |
|---|---|
| `o.setPrice(o.getPrice() * 1.1)` (reads itself) | yes |
| `o.setTotal(o.getPrice()); o.setPrice(0.0);` (read first, assign later) | yes |
| `o.setPrice(...); o.setTotal(o.getPrice());` (assign first, read later) | **error** |

The check works on physical columns (embedded sub-columns, FK columns) and across many `set()` calls (tested in [PATCH](07-2-update.md#many-set-calls-patch)).

<a id="assign-a-column-only-once"></a>
### Assign a column only once

Assigning the same column twice is an error (rule U4). This includes `setAddress(...)` and then `getAddress().setCity(...)`,
or `setCustomer(...)` and then `getCustomer().setId(...)`.

<a id="rules-checked-at-analysis-time"></a>
### Rules checked at analysis time

lambdaQL checks rules U1, U3, U4 and U6 when it reads the lambda. This test calls the internal class `AssignmentReader`, not the user API. In the user API, `set(...)` and `values(...)` throw the same `LambdaQLException` (for example `LQL-3037` for U1, `LQL-3042` for U3, `LQL-3041` for U4).

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

<a id="where-or-all-is-required"></a>
### `where` or `all()` is required

`set()` returns a type **without** `execute()` (`UpdateSet`). Only `where()` and `all()` return a type with `execute()` (`ExecutableUpdate`).
So a forgotten `where` is a compile error (rule U5). The same is true for `delete`.

<a id="association-paths-same-rows-as-select"></a>
### Association paths: same rows as select

lambdaQL writes a non-FK association path in DML as a **correlated scalar subquery**, in `where` and on the right side of SET (rule U9). These lines come from the tested example in [7.2](07-2-update.md#a-value-from-another-table):

```java
.where(o -> o.getCustomer().getName().equals("Kim"))
// → where (select s1.name from Customer s1 where s1.id = fk(e0.customer)) = 'Kim' and e0.customer.id is not null

o.setCustName(o.getCustomer().getName())
// → set e0.custName = (select s1.name from Customer s1 where s1.id = fk(e0.customer))
```

The reason is as follows. If lambdaQL gave the path to Hibernate directly, Hibernate would make a different join SQL for each database
(H2 `merge`, MariaDB `update ... join`, Oracle an inline view). Also, with a path in SET, **rows without a customer were lost**
(measured: with `.all()`, only 9 of 10 rows were updated).
lambdaQL writes the subqueries itself. The statement then reads only its own table, so MariaDB's limit on reading the target table (error 1093) does not apply.

A select uses an inner join for a path in `where` (decision D36). So in DML, lambdaQL adds `e0.customer.id is not null` for such a path.
As a result, `update` and `delete` target **exactly the rows** that `qb.from(X.class).where(same lambda)` returns (tested: the deleted rows are compared with the select).
The same is true with a null guard such as `o.getCustomer() == null || ...`. The select also uses an inner join for this path (decision D48), so the check stays.

A path that ends with the `@Id` (`o.getCustomer().getId()`) is the FK column. No subquery, no join.

<a id="batching-for-many-rows"></a>
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

<a id="limit-change-the-first-rows"></a>
### Limit: change the first rows

With `orderBy(...).limit(n)` after `where(...)` or `all()`, an update or delete changes at most `n` rows. The target is the first `n` rows of `qb.from(X.class).where(same).orderBy(same)`.

```java
qb.delete(Order.class).where(o -> o.getStatus() == 1).orderBy((o, by) -> by.asc(o.getPrice())).limit(100).execute();
```

| Point | Rule |
|---|---|
| Order | the `orderBy` keys, then the id (rule S9). Without `orderBy`, by the id |
| `orderBy` without `limit` | error (`IllegalStateException`) |
| Rendering | `e0.id in (select t.id from (select x0.id as id from X x0 ... order by ... limit n) t)` |
| Target rows | the inner select follows the select rules, joins included (D36), so the rows are the same as the select |
| MariaDB | the derived table `t` avoids errors 1093 and 1235 |

Tested examples and the SQL of each database: [7.4](07-4-delete.md#delete-the-first-rows-limit) (delete) and [7.2](07-2-update.md#update-the-first-rows-limit) (update).

<a id="one-row-protection"></a>
### One-row protection

`executeOne()` instead of `execute()` changes **at most one row**:

1. lambdaQL reads the ids of the rows that match, at most 2.
2. With 2 ids, it changes nothing and throws `NonUniqueResultException`.
3. With 1 id, it changes that row with `id = :p and (your condition)`.

It never changes two or more rows, and it does not rely on a rollback. The result is 0 or 1. `executeOne()` together with `orderBy` or `limit` is an error (`IllegalStateException`).

Tested example: [7.4](07-4-delete.md#delete-exactly-one-row-executeone). In a Spring repository, use `deleteOne` and `updateOne` (see [12.4](12-spring-data-jpa.md#one-row-protection-deleteone-and-updateone)).

<a id="transactions"></a>
### Transactions

A bulk statement needs a transaction. lambdaQL opens one only in a builder made with the `EntityManagerFactory` constructor.

| Builder | Transaction |
|---|---|
| `new QueryBuilder(entityManagerFactory)` | `execute()` and `executeOne()` open their own transaction and commit it. On an error they roll back |
| A builder with an `EntityManager`: `new QueryBuilder(entityManager)`, Spring's `QueryBuilder` bean, the repository executor | the caller's transaction (`@Transactional`, or an active transaction of that `EntityManager`). Without one, the statement fails with `TransactionRequiredException` before any SQL is sent (tested). In Spring repository calls, it becomes `InvalidDataAccessApiUsageException` (see [12.6](12-spring-data-jpa.md#126-transactions-and-exceptions)) |

<a id="the-persistence-context-is-not-updated"></a>
### The persistence context is not updated

Bulk DML does not go through the JPA persistence context (rule U16):

- Hibernate flushes pending changes before the statement.
- After the statement, entities already loaded in the same `EntityManager` have **old values**. Call `entityManager.clear()` if you use them again.
- JPA `cascade` and `orphanRemoval` do **not** run. Delete children first, or use `ON DELETE CASCADE` in the database.

<a id="sqlrestriction-and-softdelete"></a>
### `@SQLRestriction` and `@SoftDelete`

Hibernate adds the `@SQLRestriction` condition and the `@SoftDelete` condition of an entity to every statement that lambdaQL makes (measured on MariaDB 10.11 and Oracle 23.26; not measured on H2).
This includes bulk `update` and `delete`. Example: `where m1_0.order_id=? and (m1_0.hidden = 0)`.

A `delete` of a `@SoftDelete` entity becomes `update … set deleted = 1 where … and deleted = 0`. The row is not really removed.
lambdaQL does not handle these conditions itself.

---

<a id="79-all-rules-u1-to-u21-summary"></a>
## 7.9 All rules U1 to U21 (summary)

Checked when lambdaQL reads the lambda: U1, U3, U4, U6 (tested). Checked by the compiler: U5.
The lambdaQL checks of U11, U12 and U15 are not implemented yet.

| # | Rule |
|---|---|
| U1 | An assignment lambda contains **only setter calls and `Dml.value(column, value)`** on the target, its embedded paths, or the `@Id` of an association (`LQL-3037`). `if`, the ternary `c ? a : b`, loops, local variables and other method calls are errors. A lambda with no assignment is an error. |
| U2 | The right side follows the value rules of `where` ([3. Conditions (`where`)](03-where.md)). Captured values are bind parameters, and constants written in the lambda are literals (D37). `Objects.requireNonNullElse(a, b)` becomes `coalesce(a, b)`. |
| U3 | The right side reads the **old** value. Reading a column after assigning it in the same statement is an error. |
| U4 | Assigning the same physical column twice is an error, also across `set()` calls. |
| U5 | No `execute()` without `where()` or `all()`. Checked by the compiler. |
| U6 | Assigning `null` to a non-optional column (`@Id`, `nullable=false`, primitive) is an error. Assigning a collection, or a non-`@Id` field of an associated entity, is also an error. |
| U7 | Each use of a captured value gets its own parameter. (In a test, a STRING column and an ORDINAL column shared one enum parameter, and a wrong value was stored without an error.) |
| U8 | `LocalDateTime.now()` is evaluated in the application and bound. The database time (`current_timestamp`) is not supported. |
| U9 | Non-FK association paths in DML become correlated scalar subqueries, in `where` and on the right side of SET. In `where`, a path that a select would inner join (D36) also gets `customer.id is not null`. DML targets the same rows as select. |
| U10 | In `insert ... select`, source paths become an explicit `left join`. |
| U11 | `o.setCustomer(entity)` binds the `@Id` of the entity. If the id is null, binding fails. |
| U12 | IDENTITY and SEQUENCE ids can be left out. A TABLE generator works only with `values`, not with `insert ... select`. Without a generator, the id is required. Other non-optional fields are required too. |
| U13 | Many rows are split: at most 500 values per statement. Results are added. All statements run in one transaction. |
| U14 | One `set` lambda can assign many columns, and `set()` can be called many times. All join into one SET list in call order. Setters and `Dml.value` can also be mixed in one lambda (D49). |
| U15 | `@Version` entities use `update versioned`. Do not assign the version yourself. Checking `execute() == 0` is your job. |
| U16 | Bulk DML skips the persistence context. Flush before, stale entities after, no cascade. |
| U17 | If a subquery of a DELETE reads the target entity (or the same inheritance hierarchy), lambdaQL first reads the target ids through a derived table: `d0.id in (select t.id from (select e0.id as id from … where <condition>) t)`. Otherwise MariaDB reports error 1093. It also works for correlated subqueries. It does not wrap `update` or entities with a composite id (see [7.4](07-4-delete.md#delete-with-a-subquery-on-the-same-table)). |
| U18 | Inheritance: SINGLE_TABLE is fully supported. JOINED and TABLE_PER_CLASS update/delete use temporary tables. Insert into their subclasses is rejected. |
| U19 | The query cache key includes the entry point, the target entity, the set lambdas, where/all, versioned, and the split size. |
| U20 | `Dml.value(column, value)` (D49): the first argument must be a getter call that starts at the target (or at the target `a` of `insert ... select`) (`LQL-3039`). The setter rules of the same property apply (U1, U6, only the `@Id` for an association). The value type is checked against the column type (`LQL-3065`). It can be mixed with setters. |
| U21 | Each lambda is analyzed once and cached. Checks across `set()` calls only compare cached summaries (about 0.1 to 0.2 µs in a prototype). |

The upsert rules (decision D40) are in [7.6 Upsert](07-6-upsert.md#rules).

---

<a id="710-not-in-v01"></a>
## 7.10 Not in v0.1

| Item | Plan | What to do now |
|---|---|---|
| Conditional value (`c ? a : b`, `!o.isActive()` as a value) | v0.2 (CASE WHEN) | – |
| `if` inside the lambda to choose an assignment | v0.2 | call `set()` many times (U14) |
| String concatenation (`o.getDescription() + "_x"`) | v0.2 | – |
| Explicit join in `insert ... select` | v0.2 | use many-to-one paths |
| `returning` | rejected | – |
| Database time (`current_timestamp`) | rejected | bind an application time (U8) |

A broken rule throws a `LambdaQLException` with a `[LQL-xxxx]` code. Most rules throw `TranslationException`. A condition or a local variable in an assignment lambda throws `UnsupportedLambdaShapeException`. See [11. Errors and languages](../11-errors-and-languages.md).
All rules in one place: [10. Rules and limits](10-rules-and-limits.md).

---

[Java track](README.md) | Previous: [6. Select, aggregates and group by](06-select-group.md) | Next: [8. Procedures and DB functions](08-procedures-functions.md) | [Kotlin version of this page](../kotlin/07-dml.md)
