<a id="1-overview"></a>
# 1. Overview

[Manual home](README.md) | Tracks: [Java](java/README.md) | [Kotlin](kotlin/README.md) | Next: 2. Getting started ([Java](java/02-getting-started.md) | [Kotlin](kotlin/02-getting-started.md))

This page is for both languages. Code is shown in separate **Java** and **Kotlin** sections.

<a id="what-is-lambdaql"></a>
## What is lambdaQL?

lambdaQL is a query library for Java and Kotlin on top of JPA and Hibernate 7.4.

- You write a query with **lambdas**.
- lambdaQL **reads the bytecode** of each lambda. It **never runs** the lambda.
- lambdaQL builds **HQL** (Hibernate Query Language) from the bytecode.
- Constants written in the lambda (`1`, `100.0`, `"Kim"`) are written into the HQL. Values that the lambda captures become **bind parameters**.
- Hibernate runs the HQL on your database.

### Java

```java
long minPrice = 100;

List<Order> orders = qb.from(Order.class)
    .where(o -> o.getStatus() == 1 || o.getCustomer().getName().equals("Kim"))
    .where(o -> o.getPrice() > minPrice)
    .toList();
```

### Kotlin

```kotlin
val minPrice = 100.0

val orders = qb.from(Order::class.java)
    .where { it.status == 1 || it.customer?.name == "Kim" }
    .where { it.price!! > minPrice }
    .toList()
```

<a id="hql-the-same-for-both"></a>
### HQL (the same for both)

```
select e0 from Order e0 join e0.customer j1
where (e0.status = 1 or j1.name = 'Kim') and e0.price > :p1
```

**About the SQL blocks.** `…` is the shortened select list (all entity columns), and `?` is a bind parameter. The lines come from [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java). It runs each query on H2 2.3, MariaDB 10.11 and Oracle 23.26 with Hibernate 7.4, and it fails if Hibernate's SQL changes.

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `overview-first`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where (o1_0.status=1 or c1_0.name='Kim') and o1_0.price>?
```

Two `where` calls are joined with `AND`.
The constant `"Kim"` is a literal; the captured `minPrice` is a bind parameter (`:p1`).
A path like `o.getCustomer().getName()` in `where` is an inner `join` by default. Orders without a customer are not returned.
This example is the same. In Java, an order with status 1 would match even without a customer, but lambdaQL uses the SQL meaning (decision D48).
To keep such orders, use `leftJoin`.

The lambdas look like normal Java or Kotlin code.
But they are a **query language** written with Java or Kotlin syntax.
`o.getCustomer().getName()` (Kotlin `it.customer?.name`) does not call a getter. It **names a column**.

<a id="why-lambdaql"></a>
## Why lambdaQL?

| Goal | How lambdaQL does it |
|---|---|
| Type safety | The compiler checks the lambda. If you rename a property in the IDE, your queries are renamed too. |
| No generated code | You do not need a metamodel generator or Q-classes. lambdaQL uses the JPA Metamodel that Hibernate already has at run time. |
| Normal syntax | You use `==`, `&&`, `\|\|`, `!`, getters or properties, and method calls that you already know. |
| Safe values | Captured and computed values are always bind parameters. Only constants that are compiled into your code are written into the query text (quotes are escaped). |
| Lambdas are not run | No entity objects are created. No getters are called. There is no `NullPointerException` from the lambda. |
| Clear errors | If a lambda has a form that lambdaQL cannot translate, it throws an error with a code. It does not guess. |

<a id="how-it-works-5-steps"></a>
## How it works (5 steps)

```
 your lambda ──► 1. find body ──► 2. read bytecode ──► 3. rebuild condition
                                                              │
     result ◄── 5. run with Hibernate ◄── 4. build HQL ◄──────┘
```

| Step | What happens | Status |
|---|---|---|
| 1. Find the lambda body | The lambda is `Serializable`. lambdaQL gets its `SerializedLambda` through a serialization hook (no `setAccessible`). This tells lambdaQL which method holds the lambda body and which values are captured. | Implemented |
| 2. Read the bytecode | lambdaQL reads only that one method. It uses ASM on Java 21–23 and the JDK's `java.lang.classfile` on Java 24 or later. It reads the class file. It does not run the code. | Implemented |
| 3. Rebuild the condition | Java and Kotlin compile `&&`, `\|\|` and `!` into jumps. lambdaQL rebuilds the original logic tree from these jumps. | Implemented for Java lambdas. Kotlin: `?.`, `!!` and `in` are tested; the other Kotlin-only forms (`?:`, ranges, `when`) are designed. |
| 4. Build HQL | Getters and properties become JPA attribute paths (`o.getPrice()` or `it.price` → `e0.price`). Constants in the lambda become literals; captured values become parameters (`:p1`). | Implemented for `where` and `orderBy`, tested on H2, MariaDB, Oracle |
| 5. Run | lambdaQL binds the parameters and runs the HQL with Hibernate. The analysis result of each lambda is cached, so the bytecode is read only once. | Implemented for `toList()` and `toSet()` (also with `limit` / `offset`), `first()`, `one()`, `count()` and `exists()` |

Reading one lambda takes a few microseconds. With the cache, lambdaQL reads the bytecode of a lambda only once.

A Kotlin lambda literal that you pass to lambdaQL compiles to the same kind of serializable lambda as a Java lambda.
This is why one engine can read both languages.

<a id="design-principles"></a>
## Design principles

<a id="one-lambda-per-clause"></a>
### One lambda per clause

Each clause takes **one** lambda. You do not write one lambda per column.

#### Java

```java
.where(o -> o.getStatus() == 1 && o.isActive())                 // one condition lambda
.orderBy((o, by) -> by.desc(o.getPrice()).asc(o.getId()))     // one sort lambda for all keys
.select(o -> new OrderView(o.getId(), o.getPrice()))           // one select lambda
```

#### Kotlin

```kotlin
.where { it.status == 1 && it.active }                          // one condition lambda
.orderBy { o, by -> by.desc(o.price).asc(o.id) }                // one sort lambda for all keys
.select { OrderView(it.id, it.price) }                          // one select lambda
```

Helpers like the sort builder `by` and the subquery factory `sub` are **lambda arguments**.
You can give them any name. This manual uses `by` and `sub`.

<a id="no-static-helpers-except-agg"></a>
### No static helpers, except `Agg` and `Dml`

There are only two static helper classes: `org.lambdaql.Agg` for aggregates (`count`, `sum`, `avg`, `min`, `max`, `countDistinct`), and `org.lambdaql.Dml` (`Dml.value(column, value)`, decision D49), which assigns a value to an entity without setters.
A static import of either class is optional.

#### Java

```java
.having(o -> Agg.sum(o.getPrice()) > 1000)
```

#### Kotlin

```kotlin
.having { Agg.sum(it.price)!! > 1000 }
```

Your own database functions are also static methods, but you declare them yourself
(see 8. Procedures and DB functions: [Java](java/08-procedures-functions.md) | [Kotlin](kotlin/08-procedures-functions.md)).

<a id="a-lambda-is-a-query-not-code"></a>
### A lambda is a query, not code

lambdaQL follows its own documented rules, not the run-time rules of Java or Kotlin.

#### Java

| You write | lambdaQL means | Why |
|---|---|---|
| `o.getId() == id` (two `Long` objects) | value equality: `e0.id = :p1` | In a query, `==` on objects means "same value", not "same object". A captured `null` is bound as NULL and matches no row (`LQL-4045` at `WARN`); write `o.getId() == null` to find NULL. |
| `Objects.equals(o.getDescription(), o.getCustName())`, `!text.equals(o.getDescription())` | `e0.description = e0.custName`, `e0.description <> :p1` (SQL meaning) | Two values of the entity, or `!=` against a value: a row whose value is NULL does not match. Java would return some of these rows. To keep them, write `o.getDescription() == null \|\| ...`. |
| `o.getCustomer().getName()` | the `name` column of the joined customer (in `where`: inner `join` by default) | A path names a column. lambdaQL does not check if the customer is null. In `where`, orders without a customer are not returned; to find them, write `o.getCustomer() == null`. A null guard (`o.getCustomer() == null \|\| ...`) does not change this (SQL meaning, D48). To keep these orders, use `leftJoin`. |
| `o.getStatus() == 1` when `status` is NULL | the row does not match (SQL meaning) | Java would throw `NullPointerException`. SQL returns UNKNOWN. |
| `o.getDescription() == null` | `e0.description is null` | You wrote the null check, so lambdaQL uses it. |

> [!TIP]
> IntelliJ may warn "Number objects compared using `==`" on `o.getId() == id`.
> In a lambdaQL lambda this is intended. You can use `@SuppressWarnings("NumberEquality")`.

#### Kotlin

| You write | lambdaQL means | Why |
|---|---|---|
| `it.id == id` (`id: Long?`, a nullable variable) | value equality: `e0.id = :p1` | Kotlin `==` already means "same value". A captured `null` is bound as NULL and matches no row (`LQL-4045` at `WARN`). |
| `it.description == it.custName`, `it.description != text` | `e0.description = e0.custName`, `e0.description <> :p1` (SQL meaning) | Two values of the entity, or `!=` against a value: a row whose value is NULL does not match. To keep such rows, write `it.description == null \|\| ...`. |
| `it.customer?.name` or `it.customer!!.name` | the `name` column of the joined customer (in `where`: inner `join` by default) | A path names a column. `?.` and `!!` do not change the column. `!!` is only there so the lambda compiles. In `where`, orders without a customer are not returned; to find them, write `it.customer == null`. A null guard (`it.customer == null \|\| ...`) does not change this (SQL meaning, D48). To keep these orders, use `leftJoin`. |
| `it.price!! > 100.0` when `price` is NULL | the row does not match (SQL meaning) | Kotlin would throw `NullPointerException` at `!!`. lambdaQL ignores `!!`, and SQL returns UNKNOWN. |
| `it.description == null` | `e0.description is null` | You wrote the null check, so lambdaQL uses it. |

lambdaQL handles null **only where you write it**.
All comparisons and methods in a lambda have the SQL meaning (decision D48). This also holds for captured values and association paths.
When the result can differ from Java or Kotlin, lambdaQL logs `LQL-5009` at `DEBUG` level. When a captured `null` is used in `=` or `<>`, it logs `LQL-4045` at `WARN` level.
See 3. Conditions ([Java](java/03-where.md) | [Kotlin](kotlin/03-where.md))
and 10. Rules and limits ([Java](java/10-rules-and-limits.md) | [Kotlin](kotlin/10-rules-and-limits.md)).

### SARGable SQL

lambdaQL writes SQL that can use indexes ("SARGable").

- It does not put functions, math or `CASE` on the column side.
- If a value must change (for example to lower case), lambdaQL changes the **parameter** in Java before it binds it.
- If a rule needs a function on a column and no index can help, lambdaQL logs a warning.

#### Java

| You write | lambdaQL does **not** write | lambdaQL writes |
|---|---|---|
| `o.getId() != null && o.getId() == id` | `e0.id is not null and e0.id = :p1` | `e0.id = :p1` (the id can never be NULL) |
| `o.getDescription() == text` | `e0.description is not distinct from :p1` (on Oracle `decode(...)`, no index) | `e0.description = :p1` |

#### Kotlin

| You write | lambdaQL does **not** write | lambdaQL writes |
|---|---|---|
| `(it.description ?: "") == "x"` | `coalesce(e0.description, '') = 'x'` | `e0.description = 'x'` |
| `it.description.isNullOrEmpty()` | `(e0.description is null or length(e0.description) = 0)` | `(e0.description is null or e0.description = :p1)` |
| `it.id == id` (`it.id` is a `Long?`) | `not (e0.id is null or e0.id <> :p1)` | `e0.id = :p1` (the id can never be NULL) |

<a id="java-and-kotlin"></a>
### Java and Kotlin

Java and Kotlin use **the same engine**.
A Kotlin lambda and a Java lambda with the same meaning give the same HQL.
Some Kotlin forms are not supported. lambdaQL throws a clear error for them (see [9. Kotlin notes](kotlin/09-kotlin-notes.md)).
Java-only details are in [9. Java notes](java/09-java-notes.md).

<a id="fail-loudly"></a>
### Fail loudly

If lambdaQL does not know the meaning of a form, it does not guess.
It throws an exception with a code like `[LQL-2004]` (see [11. Errors and languages](11-errors-and-languages.md)).

Examples of forms that are rejected:

| Form | Java | Kotlin |
|---|---|---|
| A value that is chosen by a condition | `o.getStatus() == (flag ? 1 : 2)` | `it.status == if (flag) 1 else 2` |
| Loops or local variables inside the lambda | yes, rejected | yes, rejected |
| Not a lambda | an anonymous class | an `object : ...` expression, or a Kotlin function-type value (`val f: (Order) -> Boolean`) |
| A method call that has no meaning in SQL | yes, rejected | yes, rejected (also your own non-inline extension functions) |

<a id="comparison-with-other-approaches"></a>
## Comparison with other approaches

This table is a short, neutral summary. Each tool is good for different needs.

| | JPQL / HQL strings | JPA Criteria API | QueryDSL | lambdaQL |
|---|---|---|---|---|
| How you write a query | Text in a string | Java builder objects | Generated Q-classes | Lambdas |
| Checked by the compiler | No (checked by Hibernate at run time) | Yes | Yes | Yes |
| Generated code needed | No | Optional (static metamodel) | Yes (Q-classes) | No |
| Rename-safe in the IDE | No | With static metamodel | Yes | Yes |
| Looks like | SQL | Java builder code | SQL-like Java | Normal Java / Kotlin expressions |
| Kotlin | Strings | Works, verbose | Works | Supported (same engine) |
| Maturity | Standard | Standard | Mature, widely used | Early development, not released |

<a id="next"></a>
## Next

- 2. Getting started: build lambdaQL and write your first query. [Java](java/02-getting-started.md) | [Kotlin](kotlin/02-getting-started.md)
- [Manual home](README.md)
