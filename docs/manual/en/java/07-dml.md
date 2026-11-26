<a id="7-update-insert-delete-java"></a>
# 7. Update, insert, delete (Java)

> [!IMPORTANT]
> This page is older than the Korean manual. See the [Korean page](../../ko/java/07-dml.md) for the latest rules.

[Java track](README.md) | [Manual home](../README.md) | Previous: [6. Select, aggregates and grouping](06-select-group.md) | Next: [8. Procedures and DB functions](08-procedures-functions.md) | [Kotlin version of this page](../kotlin/07-dml.md)

Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md)

---

lambdaQL has five entry points.

| Entry point | Use |
|---|---|
| `qb.from(X.class)` | Select (chapters 3 to 6) |
| `qb.update(X.class)` | Update rows |
| `qb.insert(X.class)` | Insert rows |
| `qb.delete(X.class)` | Delete rows |
| `qb.upsert(X.class)` | Insert rows. If a row with the same `@Id` exists, update that row |

The basic ideas are the same as for queries.

- You write each part as **one lambda expression**. For an entity without setters, assign values with `Dml.value`. `orderBy` uses the sort functions of `By`, as in select ([7.3](#73-two-ways-to-assign-setter-or-dmlvalue)).
- lambdaQL **does not call** the assignment lambda expressions and condition lambda expressions. It analyzes their bytecode opcodes. A setter call such as `o.setStatus(1)` becomes `set e0.status = 1`.
- `where` uses the same rules as [3. Conditions (`where`)](03-where.md). The target rows are the same as the rows selected with the same lambda expression.
- `execute()` runs the statement and returns the **number of affected rows** (`int`).
- `executeOne()` runs an update or delete that must change **at most one row** (see [One-row protection](#one-row-protection)).
- `orderBy(...).limit(n)` changes only the first `n` rows (see [limit](#limit-change-the-first-rows)).
- `toHql()` returns the HQL and the parameters without running the statement.
- Running a statement needs a transaction (see [Transactions](#transactions)).

<a id="contents"></a>
## Contents

- [7.1 Example entities](#71-example-entities)
- [7.2 Update](#72-update)
- [7.3 Two ways to assign values: setter or `Dml.value`](#73-two-ways-to-assign-setter-or-dmlvalue)
- [7.4 Delete](#74-delete)
- [7.5 Insert](#75-insert)
- [7.6 Upsert](#76-upsert)
- [7.7 Java notes for DML](#77-java-notes-for-dml)
- [7.8 Important rules](#78-important-rules)
- [7.9 All rules U1 to U21 (summary)](#79-all-rules-u1-to-u21-summary)
- [7.10 What is not supported](#710-not-in-v01)

---

<a id="71-example-entities"></a>
## 7.1 Example entities

This chapter uses `Order`, `Customer` and `Item` (see [2. Getting started](02-getting-started.md#example-entities)).
Here `Order` has a few more fields.

| Field | Type | Notes |
|---|---|---|
| `id`, `price`, `status`, `active`, `description`, `updateAt`, `customer` | | Same as in the other chapters |
| `total` | `Double` | Sum of the item prices |
| `cnt` | `Integer` | A counter |
| `custName` | `String` | A copy of the customer name |
| `address` | `Address` (embedded: `city`, `zip`) | An embedded value |

Other entities: `VOrder` (an order with the `@Version` field `version`), `IdOrder` (an order with a generated id), `Archive` (`id`, `price`, `customerName`), `Summary` (`customerId`, `total`, `cnt`).

The entities of the example code ([`query/src/test/java/org/lambdaql/query`](https://github.com/lambdaql/lambdaql/tree/main/query/src/test/java/org/lambdaql/query)) are a little different from the table above.
In `Order`, `price` is a `double`, `status` is a `short`, and there is a `float` field `tex`. The id of `IdOrder` uses IDENTITY.

---

<a id="72-update"></a>
## 7.2 Update

→ [7.2 Update](07-2-update.md)

<a id="73-two-ways-to-assign-setter-or-dmlvalue"></a>
## 7.3 Two ways to assign values: setter or `Dml.value`

| Way | When to use it | Example |
|---|---|---|
| Setter | The entity has setters. The compiler checks the value type. | `set(o -> o.setStatus(1))` |
| `Dml.value(column, value)` | The entity has **no setters** (for example, a DDD-style entity). | `set(o -> value(o.getStatus(), 1))` |

`value` is the static function `org.lambdaql.Dml.value`. Import it with `import static org.lambdaql.Dml.value;` and use it inside a lambda expression with one argument.

<!-- region: DmlQueryTest.java#update-value -->
```java
// entities without setters: Dml.value(column, value), the column written as a getter call
ExecutableUpdate<Order> u = qb.update(Order.class)
        .set(o -> value(o.getDescription(), "done"))
        .where(o -> o.getStatus() == 2);
assertEquals("update Order e0 set e0.description = 'done' where e0.status = 2", u.toHql().hql());
```

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

Every setter example in this chapter can also be written with `value`. Write one `value` call per statement.

<!-- region: ManualJavaDmlTest.java#value-forms -->
```java
ExecutableUpdate<Order> u = qb.update(Order.class)
        .set(o -> { value(o.getStatus(), st); value(o.getPrice(), o.getPrice() * 1.1); })
        .where(o -> o.getStatus() == 1);
assertEquals("update Order e0 set e0.status = :p1, e0.price = (e0.price * 1.1) where e0.status = 1", u.toHql().hql());
// an embedded column and a foreign key
ExecutableUpdate<Order> embedded = qb.update(Order.class)
        .set(o -> value(o.getAddress().getCity(), city)).where(o -> o.getId() == id);
assertEquals("update Order e0 set e0.address.city = :p1 where e0.id = :p2", embedded.toHql().hql());
ExecutableUpdate<Order> fk = qb.update(Order.class)
        .set(o -> value(o.getCustomer().getId(), cid)).where(o -> o.getId() == id);
assertEquals("update Order e0 set e0.customer.id = :p1 where e0.id = :p2", fk.toHql().hql());
```

Rules of `value`:

- The first argument must be **a getter call that starts from the target**: a field, an embedded path, or the `@Id` of an association.
  A captured value, a computed expression or a path of the source entity is an error at analysis time (`LQL-3039`).
- The setter rules of the same property apply as they are. `value(o.getX(), v)` is the same as `o.setX(v)`, so U1 and U6 apply, and for an association only the `@Id` can be assigned (`LQL-3050`).
- You **can mix** setters and `value` **in one lambda expression**.
- In Java, `value(o.getStatus(), "abc")` compiles, because the type is generic.
  lambdaQL compares the column type and the value type at analysis time, and gives the error `LQL-3065` if they do not match.
  Numbers fit number columns. But a decimal does not fit an integer column (the decimal part would be lost). Values of unknown type (`Object`, `Number` of a generic aggregate) are not compared.
- Inside a class that has its own `value(...)` method (for example, a record with a `value` component), the static import is hidden. Write `Dml.value(...)` there.

<!-- region: AssignmentReaderTest.java#value-type -->
```java
// value(V, V) compiles with any two types, so lambdaQL checks the value type against the column
assertEquals("[col0.status := 2, col0.price := 1]", setters(o -> { value(o.getStatus(), 2); value(o.getPrice(), 1); }));
String text = "abc";
LambdaQLException wrong = assertThrows(LambdaQLException.class, () -> setters(o -> value(o.getStatus(), text)));
assertEquals("LQL-3065", wrong.code(), wrong.getMessage());
// a decimal into a whole number column would lose the fraction
LambdaQLException fraction = assertThrows(LambdaQLException.class, () -> setters(o -> value(o.getStatus(), 1.5)));
assertEquals("LQL-3065", fraction.code(), fraction.getMessage());
```

<!-- region: AssignmentReaderTest.java#read-value -->
```java
// Dml.value(column, value) for entities without setters; constants stay literals, also when they are boxed
// for the generic value(V, V)
assertEquals("[col0.status := 2, col0.price := 1.0]",
        setters(o -> { value(o.getStatus(), (short) 2); value(o.getPrice(), 1.0); }));
// setters and value can be mixed in one lambda
assertEquals("[col0.status := 2, col0.customer.id := 1]",
        setters(o -> { o.setStatus((short) 2); value(o.getCustomer().getId(), 1); }));
```

<a id="subquery-in-a-set-value"></a>
### A subquery in a `set` value

The second argument of the assignment lambda expression is `sub`, the starting point of a subquery. It is the same notation as `where((o, sub) -> ...)`.
In `insert ... select`, the third argument is `sub` (see [7.5](07-5-insert.md)).

<!-- region: DmlQueryTest.java#update-sub -->
```java
// the second parameter starts a subquery, as in where((o, sub) -> ...)
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

- You can use both an aggregate of another table and an aggregate of the same table as a `set` value (measured on H2, MariaDB 10.11 and Oracle 23.26). In `update`, MariaDB does not give error 1093 either.
- If no row matches, `sum` and the others return NULL. For a NOT NULL column this is a database error, so wrap it in `Objects.requireNonNullElse(…, 0.0)`. It becomes `coalesce(...)`.
- You use subqueries in the same way in the `values((a, sub) -> ...)` of insert and in upsert (`onConflict(...).update((a, sub) -> ...)`) (see [7.5](07-5-insert.md#a-subquery-in-a-value)). In the list form, the third argument is `sub`, like `values(list, (a, l, sub) -> ...)`.

Example code: [`DmlQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) (`update-value`, `update-sub`), [`ManualJavaDmlTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaDmlTest.java) (`value-forms`), [`AssignmentReaderTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/sem/AssignmentReaderTest.java) (`value-type`, `read-value`).

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
| Block lambda expression `o -> { o.setA(...); o.setB(...); }` | Allowed in `set`, `values`, `insert ... select` and `onConflict(...).update(...)`, but **only setter calls**. No local variables, `if` or loops |
| `Objects.requireNonNullElse(a, b)` | `coalesce(a, b)` |
| `o.setCnt(o.getCnt() + 1)` | Supported. javac unboxes and boxes the value, but lambdaQL ignores this |
| `o.setStatus((short) 1)` | The cast that javac needs for a `short` or `byte` field. The constant stays a literal |
| `o.setActive(!o.isActive())` (toggle) | **Not supported** (a value that depends on a condition). Use two updates with different `where` |
| `o.setDescription(o.getDescription() + "_x")` | **Not supported** (string concatenation) |
| `LocalDateTime.now()` as a value | Computed in the application and bound |
| `UpdateSet<Order> u = qb.update(...).set(...)` (or `var`) | Build an update step by step (see [PATCH](07-2-update.md#many-set-calls-patch)) |

---

<a id="78-important-rules"></a>
## 7.8 Important rules

<a id="the-right-side-reads-the-old-value"></a>
### The right side reads the old value

In SQL, every column on the right side of SET means the value **before** the update. lambdaQL follows this rule.

<!-- region: ManualJavaDmlTest.java#old-value -->
```java
// price is assigned first and read again: rejected (MariaDB would read the new value)
LambdaQLException e = assertThrows(LambdaQLException.class,
        () -> qb.update(Order.class).set(o -> { o.setPrice(o.getPrice() * 2); o.setTotal(o.getPrice()); }));
assertEquals("LQL-3042", e.code());
// read first, assign later: allowed, total gets the old price
ExecutableUpdate<Order> u = qb.update(Order.class)
        .set(o -> { o.setTotal(o.getPrice()); o.setPrice(0.0); })
        .where(o -> o.getStatus() == 3);
assertEquals("update Order e0 set e0.total = e0.price, e0.price = 0.0 where e0.status = 3", u.toHql().hql());
```

**If you assign a column and later read that column in the same statement**, lambdaQL throws a `TranslationException` (`LQL-3042`) when `set(...)` is called.
Why: in this case MariaDB uses the **new** value, and H2 and Oracle use the old value. The results differ (measured: MariaDB 200, H2 and Oracle 100).

These cases give the same result on the three databases, so they are allowed:

| Form | Allowed |
|---|---|
| `o.setPrice(o.getPrice() * 1.1)` (reads itself) | Allowed |
| `o.setTotal(o.getPrice()); o.setPrice(0.0);` (read first, assign later) | Allowed |
| `o.setPrice(...); o.setTotal(o.getPrice());` (assign first, read later) | **Error** |

This check works per physical column (embedded sub-columns, FK columns), and it also applies across many `set()` calls (see [PATCH](07-2-update.md#many-set-calls-patch)).

<a id="assign-a-column-only-once"></a>
### Assign a column only once

Assigning the same column twice is an error. This includes `getAddress().setCity(...)` after `setAddress(...)`,
and `getCustomer().setId(...)` after `setCustomer(...)`.

<a id="rules-checked-at-analysis-time"></a>
### Rules checked at analysis time

lambdaQL checks the assignment rules when it reads the lambda expression. The example below calls the internal class `AssignmentReader`, not the user API. In the user API, `set(...)` and `values(...)` throw the same `LambdaQLException` (for example, `LQL-3037`, `LQL-3042`, `LQL-3041`).

<!-- region: AssignmentReaderTest.java#rules -->
```java
// only setters; no condition, no local variable
assertThrows(LambdaQLException.class, () -> setters(o -> { if (o.isActive()) o.setStatus((short) 1); }));
// an association's other properties belong to another table
assertThrows(LambdaQLException.class, () -> setters(o -> o.getCustomer().setName("x")));
// a value reads a column written before
assertThrows(LambdaQLException.class, () -> AssignmentReader.checkRules(
        AssignmentReader.readSetters(SerializedLambdaExtractor.extract((Assign<Order>) o -> {
            o.setPrice(1);
            o.setTex((float) o.getPrice());
        }), List.of(Order.class), -1, new JpaEntityModel(emf.getMetamodel())), "test"));
```

<a id="where-or-all-is-required"></a>
### `where` or `all()` is required

To run an update or delete, you must give `where(...)` or `all()`.

<a id="association-paths-same-rows-as-select"></a>
### Association paths: the same rows as select

In DML, lambdaQL writes association paths that are not FKs as **correlated scalar subqueries**. This is the same for `where` and for the right side of SET. The example below shows paths in `where` and on the right side of SET.

<!-- region: DmlQueryTest.java#update-path -->
```java
// a path in the condition: a subquery, plus "customer exists" (the same rows as the select, U9)
ExecutableUpdate<Order> u = qb.update(Order.class)
        .set(o -> o.setCustName("K"))
        .where(o -> o.getCustomer().getName().equals("Kim"));
assertEquals("update Order e0 set e0.custName = 'K' where (select s1.name from Customer s1 where s1.id = fk(e0.customer)) = 'Kim'"
        + " and e0.customer.id is not null", u.toHql().hql());
// a path in a value: a subquery; NULL when there is no customer
ExecutableUpdate<Order> copy = qb.update(Order.class).set(o -> o.setCustName(o.getCustomer().getName())).all();
assertEquals("update Order e0 set e0.custName = (select s1.name from Customer s1 where s1.id = fk(e0.customer))", copy.toHql().hql());
```

The reasons are these. If lambdaQL passed the path to Hibernate as it is, Hibernate would build different join SQL for each database
(`merge` on H2, `update ... join` on MariaDB, an inline view on Oracle). Also, when SET had a path, **rows without a customer were left out**
(measured: with `.all()`, only 9 of 10 rows were updated).
lambdaQL writes the subquery itself. Then the statement reads only its own table, so it does not hit MariaDB's limit on reading the target table (error 1093).

For a path that becomes an inner join in select, lambdaQL adds `e0.customer.id is not null` in DML.
For a path that becomes a `left join` in select (for example, a path under the null guard `o.getCustomer() == null || ...`), this check is not added.
As a result, `update` and `delete` target **exactly the rows** that `qb.from(X.class).where(same lambda expression)` returns (the deleted rows were compared with the select result).

A path that ends with `@Id` (`o.getCustomer().getId()`) is the FK column. There is no subquery and no join.

<a id="batching-for-many-rows"></a>
### Batching for many rows

`values(list, ...)` writes one statement with many VALUES rows.

| Item | Rule |
|---|---|
| Split unit | The number of values (rows x columns), not the number of rows |
| Limit | 500 values or fewer per statement (fixed) |
| Return value | The sum of all statements |
| Transaction | All statements run in one transaction |
| Empty list | No SQL, returns 0 |

Oracle has a strict limit of 65,535 bind values (ORA-01745). Parsing becomes very slow long before that
(measured at the design stage: about 6 minutes for 21,845 rows). So the limit is kept small.

<a id="limit-change-the-first-rows"></a>
### limit: change the first rows

If you add `orderBy(...).limit(n)` after `where(...)` or `all()`, the update or delete changes at most `n` rows. The targets are the first `n` rows of `qb.from(X.class).where(same).orderBy(same)`.

<!-- region: DmlQueryTest.java#delete-limit -->
```java
// the first rows of qb.from(Order.class).where(same).orderBy(same); the id is the last sort key
ExecutableDelete<Order> d = qb.delete(Order.class)
        .where(o -> o.getStatus() == 1)
        .orderBy(o -> asc(o.getPrice()))
        .limit(1);
assertEquals("delete from Order e0 where e0.id in (select t.id from (select x0.id as id from Order x0"
        + " where x0.status = 1 order by x0.price asc, x0.id asc limit 1) t)", d.toHql().hql());
int deleted = d.execute();
```

| Item | Rule |
|---|---|
| Order | The `orderBy` keys, then the id. Without `orderBy`, id order |
| `orderBy` without `limit` | Error (`IllegalStateException`) |
| Rendering | `e0.id in (select t.id from (select x0.id as id from X x0 ... order by ... limit n) t)` |
| Target rows | The inner select follows the select rules, including joins, so the rows are the same as in select |
| MariaDB | The derived table `t` avoids errors 1093 and 1235 |

Examples and SQL by database: [7.4](07-4-delete.md#delete-the-first-rows-limit) (delete), [7.2](07-2-update.md#update-the-first-rows-limit) (update).

<a id="one-row-protection"></a>
### One-row protection

If you call `executeOne()` instead of `execute()`, **at most one row** is changed.

1. lambdaQL reads at most 2 ids of the rows that match the condition.
2. If there are 2 ids, it changes nothing and throws `NonUniqueResultException`.
3. If there is 1 id, it changes that row with `id = :p and (condition)`.

It never changes two or more rows and does not rely on a rollback. The result is 0 or 1. Using `executeOne()` with `orderBy` or `limit` is an error (`IllegalStateException`).

Example: [7.4](07-4-delete.md#delete-exactly-one-row-executeone). In a Spring repository, use `deleteOne` and `updateOne` (see [12.4](12-spring-data-jpa.md#one-row-protection-deleteone-and-updateone)).

<a id="transactions"></a>
### Transactions

Bulk statements need a transaction. lambdaQL opens a transaction only for a builder created with the `EntityManagerFactory` constructor.

| Builder | Transaction |
|---|---|
| `new QueryBuilder(entityManagerFactory)` | `execute()` and `executeOne()` open their own transaction and commit it. On an error, they roll it back |
| Builders that use an `EntityManager`: `new QueryBuilder(entityManager)`, the Spring `QueryBuilder` bean, the repository executor | The caller's transaction (`@Transactional`, or the active transaction of that `EntityManager`). Without a transaction, it fails with `TransactionRequiredException` before any SQL is sent. In a Spring repository call, it becomes `InvalidDataAccessApiUsageException` (see [12.6](12-spring-data-jpa.md#126-transactions-and-exceptions)) |

<a id="the-persistence-context-is-not-updated"></a>
### The persistence context is not updated

Bulk DML does not go through the JPA persistence context.

- Hibernate flushes pending changes before the statement runs.
- After the statement, entities already loaded in the same `EntityManager` have the **old values**. If you use those entities again, call `entityManager.clear()`.
- JPA `cascade` and `orphanRemoval` do **not** run. Delete the children first, or use `ON DELETE CASCADE` in the database.

<a id="sqlrestriction-and-softdelete"></a>
### `@SQLRestriction` and `@SoftDelete`

Hibernate adds the `@SQLRestriction` and `@SoftDelete` conditions of an entity to every statement that lambdaQL builds (measured on MariaDB 10.11 and Oracle 23.26. Not measured on H2).
This is also true for bulk `update` and `delete`. Example: `where m1_0.order_id=? and (m1_0.hidden = 0)`.

`delete` of a `@SoftDelete` entity becomes `update … set deleted = 1 where … and deleted = 0`. The rows are not really removed.
lambdaQL does not handle these conditions separately.

Example code: [`ManualJavaDmlTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaDmlTest.java) (`old-value`), [`AssignmentReaderTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/sem/AssignmentReaderTest.java) (`rules`), [`DmlQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) (`update-path`, `delete-limit`).

---

<a id="79-all-rules-u1-to-u21-summary"></a>
## 7.9 All rules U1 to U21 (summary)

Rules that lambdaQL checks when it reads a lambda expression: U1, U3, U4, U6, U15.
lambdaQL rejects an assignment to a `@Version` field (`LQL-3068`). U11 and U12 are not checked separately.

| # | Rule |
|---|---|
| U1 | An assignment lambda expression holds **only setter calls and `Dml.value(column, value)`** on the target, embedded paths of the target, and the `@Id` of associations (`LQL-3037`). An `if` that chooses whether to assign (`LQL-2009`), loops, local variables and other method calls are errors. A ternary operator that chooses a value is supported. With only captured values and constants (`flag ? 9 : 8`), it is computed as a value. With entity values, it becomes `case when` ([3.5.1](03-where.md#351-values-chosen-by-a-condition)). A lambda expression with no assignment is also an error. |
| U2 | The right side follows the value rules of `where` ([3. Conditions (`where`)](03-where.md)). Captured values are bind parameters, and constants written in the lambda expression are literals. `Objects.requireNonNullElse(a, b)` becomes `coalesce(a, b)`. |
| U3 | The right side reads the **old** value. Assigning a column and then reading it in the same statement is an error. |
| U4 | Assigning the same physical column twice is an error. This also covers many `set()` calls. |
| U5 | Without `where()` or `all()`, there is no `execute()`. The compiler checks this. |
| U6 | Assigning `null` to a non-optional column (`@Id`, `nullable=false`, primitive) is an error. Assigning to a collection or to a field of an associated entity that is not its `@Id` is also an error. |
| U7 | Each use of a captured value gets its own parameter. (If a STRING column and an ORDINAL column share one enum parameter, H2 and MariaDB store a wrong value without an error.) |
| U8 | `LocalDateTime.now()` is evaluated in the application and bound. Database time (`current_timestamp`) is not supported. |
| U9 | In DML, association paths that are not FKs become correlated scalar subqueries, both in `where` and on the right side of SET. In `where`, `customer.id is not null` is added for a path that a select would inner join, and not for a path that it would `left join`. DML targets the same rows as select. |
| U10 | In `insert ... select`, source paths become explicit `left join`s. |
| U11 | `o.setCustomer(entity)` binds the `@Id` of the entity. If the id is null, binding fails. |
| U12 | IDENTITY and SEQUENCE ids can be left out. A TABLE generator works only in `values`, not in `insert ... select`. Without a generator, the id is required. Other non-optional fields are also required. |
| U13 | Many rows are split into statements of 500 values or fewer. The results are added up. All statements run in one transaction. |
| U14 | One `set` lambda expression can assign many columns, and `set()` can be called many times. All are merged into one SET list in call order. Setters and `Dml.value` can be mixed even in one lambda expression. |
| U15 | A `@Version` entity uses `update versioned`. Assigning the version directly is `LQL-3068`. Checking `execute() == 0` is up to you. |
| U16 | Bulk DML skips the persistence context. Flush before running, stale entities after, no cascade. |
| U17 | If a subquery of a DELETE reads the target entity (or the same inheritance hierarchy), lambdaQL first reads the target ids through a derived table: `d0.id in (select t.id from (select e0.id as id from … where <condition>) t)`. Otherwise MariaDB gives error 1093. It also works for correlated subqueries. `update` and entities with composite ids are not wrapped (see [7.4](07-4-delete.md#delete-with-a-subquery-on-the-same-table)). |
| U18 | Inheritance: SINGLE_TABLE is fully supported. update/delete for JOINED and TABLE_PER_CLASS use temporary tables. Inserts into their subclasses are rejected. |
| U19 | The query cache key has the entry point, the target entity, the set lambda expressions, where/all, whether it is versioned, and the split size. |
| U20 | `Dml.value(column, value)`: the first argument must be a getter call that starts from the target (or the target `a` of `insert ... select`) (`LQL-3039`). The setter rules of the same property (U1, U6, only the `@Id` for associations) apply as they are. The value type is compared with the column type (`LQL-3065`). It can be mixed with setters. |
| U21 | Each lambda expression is analyzed once and cached. Checks across many `set()` calls compare only the cached summaries (about 0.1 to 0.2 µs in the prototype). |

The upsert rules are in [7.6 Upsert](07-6-upsert.md#rules).

---

<a id="710-not-in-v01"></a>
## 7.10 What is not supported

| Item | What you can do instead |
|---|---|
| Values that depend on a condition (`c ? a : b`, `!o.isActive()` as a value) | – |
| `if` inside a lambda expression to choose assignments | Call `set()` many times |
| String concatenation (`o.getDescription() + "_x"`) | – |
| Explicit joins in `insert ... select` | Use many-to-one paths |
| `returning` | – |
| Database time (`current_timestamp`) | Bind the application time |

If you break a rule, you get a `LambdaQLException` with the code `[LQL-xxxx]`. Most rules give a `TranslationException`. A condition or local variable inside an assignment lambda expression gives an `UnsupportedLambdaShapeException`. See [11. Errors and languages](../11-errors-and-languages.md).
To see all rules in one place: [10. Rules and limits](10-rules-and-limits.md).

---

[Java track](README.md) | Previous: [6. Select, aggregates and grouping](06-select-group.md) | Next: [8. Procedures and DB functions](08-procedures-functions.md) | [Kotlin version of this page](../kotlin/07-dml.md)
