<a id="1-overview"></a>
# 1. Overview

[Manual home](README.md) | Tracks: [Java](java/README.md) | [Kotlin](kotlin/README.md) | Next: 2. Getting started ([Java](java/02-getting-started.md) | [Kotlin](kotlin/02-getting-started.md))

This page is for both languages. It shows code in separate **Java** and **Kotlin** sections.

<a id="what-is-lambdaql"></a>
## What is lambdaQL?

lambdaQL is a query library for Java and Kotlin that runs on top of JPA and Hibernate.

- lambdaQL expresses and writes JPA queries with **lambda expressions**.
- It does not run the lambda expression parts. It **analyzes them and converts them into a query**.
- lambdaQL turns the methods and the results of the lambda expressions into an **HQL (JPQL)** query.

### Java

<!-- region: ManualSharedTest.java#overview-first -->
```java
long minPrice = 100;

SelectQuery<Order> q = qb.from(Order.class)
        .where(o -> o.getStatus() == 1 || o.getCustomer().getName().equals("Kim"))
        .where(o -> o.getPrice() > minPrice);
assertEquals("select e0 from Order e0 left join e0.customer j1"
        + " where (e0.status = 1 or j1.name = 'Kim') and e0.price > :p1", q.toHql().hql());
List<Order> rows = q.toList();
```

### Kotlin

<!-- region: ManualKotlinSharedTest.kt#overview-first -->
```kotlin
val minPrice = 100.0

val q = qb.from(Order::class.java)
    .where { it.status.toInt() == 1 || it.customer!!.name == "Kim" }
    .where { it.price > minPrice }
assertEquals("select e0 from Order e0 left join e0.customer j1"
    + " where (e0.status = 1 or j1.name = 'Kim') and e0.price > :p1", q.toHql().hql())
val rows = q.toList()
```

<a id="hql-the-same-for-both"></a>
### HQL (the same for both languages)

```
select e0 from Order e0 left join e0.customer j1
where (e0.status = 1 or j1.name = 'Kim') and e0.price > :p1
```

**SQL by database**

<!-- sql: overview-first -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where (o1_0.status=1 or c1_0.name='Kim') and o1_0.price>?
```

- The two `where` calls are combined with `AND`.
- The constant `"Kim"` is written into the query as a literal, and the captured `minPrice` becomes a bind parameter (`:p1`).
- When you use a property of an associated entity in `where`, like `o.getCustomer().getName()`, lambdaQL joins the customer table.
  If an order without a customer can match the condition, it is a `left join`. If it cannot match, it is an inner `join`.
  - In the example above, an order with status 1 matches even without a customer, so it is a `left join`. 
  - If the only where condition is `o.getCustomer().getName().equals("Kim")`, it is an inner `join`.

A lambda expression looks like normal Java or Kotlin code.
But in lambdaQL, a lambda expression is a **query language** written in Java or Kotlin syntax.
`o.getCustomer().getName()` (`it.customer?.name` in Kotlin) does not call a getter. It is **evaluated as a column name**.

Example code: [`ManualSharedTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualSharedTest.java) (`overview-first`), [`ManualKotlinSharedTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSharedTest.kt) (`overview-first`).

<a id="why-lambdaql"></a>
## Why lambdaQL?

| Goal | How lambdaQL does it |
|---|---|
| Type safety | The compiler checks the lambda expressions. When you rename a property in the IDE, the name in the query changes too. |
| No generated code | You do not need a metamodel generator or Q-classes. lambdaQL uses the JPA Metamodel that Hibernate already has at run time. |
| Familiar syntax | You use what you already know: `==`, `&&`, `\|\|`, `!`, getters or properties, and method calls. |
| Safe values | Captured values and computed values always become bind parameters. Only constants compiled into the code are written into the query text (single quotes are escaped). |
| Analyze and convert the lambda expression | lambdaQL analyzes the lambda expression parts without running them. It does not create entity objects and does not call getters, so it can build intuitive queries. |
| A failed analysis is an error | If lambdaQL cannot analyze a lambda expression into a query, it throws an exception with a code. |

<a id="how-it-works-5-steps"></a>
## How it works (5 steps)

```
 your lambda ──► 1. find body ──► 2. read bytecode ──► 3. rebuild condition
                                                              │
     result ◄── 5. run with Hibernate ◄── 4. build HQL ◄──────┘
```

| Step | What it does |
|---|---|
| 1. Find the lambda expression body | lambdaQL finds where the body of the lambda expression is compiled and the values that the lambda expression captures. |
| 2. Read the bytecode opcodes | lambdaQL reads the compiled code (bytecode opcodes) of the lambda expression body found in step 1. It does not run the code. It uses ASM on Java 21 to 23, and the JDK's `java.lang.classfile` on Java 24 or later. |
| 3. Rebuild the condition | Java and Kotlin compile `&&`, `\|\|` and `!` into jump opcodes. lambdaQL rebuilds the original logic tree from these jump opcodes. |
| 4. Build the HQL | Getters and properties become JPA property paths (`o.getPrice()` or `it.price` → `e0.price`). Constants in the lambda expression become literals, and captured values become parameters (`:p1`). |
| 5. Run | lambdaQL binds the parameters and runs the HQL with Hibernate. The analysis result of each lambda expression is cached, so the bytecode opcodes are read only once. |

lambdaQL caches the analysis result per lambda expression, so it reads and analyzes the bytecode opcodes only once.   
The first call in a new JVM takes about 26 ms longer than JPQL because of the lambda expression analysis. After that, the cost added to each query is very small, about 0.5 to 3 µs (measured on H2, [benchmark](../../design/BENCHMARK.md)).


A Kotlin lambda expression literal that you pass to lambdaQL is compiled into the same kind of serializable lambda expression as a Java lambda expression.
So one engine can read both languages.

<a id="design-principles"></a>
## Design principles

<a id="one-lambda-per-clause"></a>
### One lambda expression per query clause

Each query clause, such as `where`, `orderBy` and `select`, takes **one** lambda expression.
Write all the columns to select or all the sort keys in the one lambda expression of each clause.

#### Java

<!-- region: ManualSharedTest.java#one-lambda-per-clause -->
```java
ValueQuery<OrderView> q = qb.from(Order.class)
        .where(o -> o.getStatus() == 1 && o.isActive())                 // one condition lambda
        .orderBy(o -> desc(o.getPrice()).asc(o.getId()))     // one sort lambda for all keys
        .select(o -> new OrderView(o.getId(), o.getPrice()));          // one select lambda
assertEquals("select e0.id, e0.price from Order e0 where e0.status = 1 and e0.active = true"
        + " order by e0.price desc, e0.id asc", q.toHql().hql());
```

#### Kotlin

<!-- region: ManualKotlinSharedTest.kt#one-lambda-per-clause -->
```kotlin
val q = qb.from(Order::class.java)
    .where { it.status.toInt() == 1 && it.isActive }               // one condition lambda
    .orderBy { o -> desc(o.price).asc(o.id) }               // one sort lambda for all keys
    .select { OrderView(it.id, it.price) }                         // one select lambda
assertEquals("select e0.id, e0.price from Order e0 where e0.status = 1 and e0.active = true"
    + " order by e0.price desc, e0.id asc", q.toHql().hql())
```

<a id="no-static-helpers-except-agg"></a>
### Static helpers `By`, `Agg`, `Dml`

| Class | Use |
|---|---|
| `org.lambdaql.query.By` | Sort functions (`asc`, `desc`, `sort`) |
| `org.lambdaql.Agg` | Aggregates (`count`, `sum`, `avg`, `min`, `max`, `countDistinct`) |
| `org.lambdaql.Dml` | Assign values to an entity without setters (`Dml.value(column, value)`) |

#### Java

<!-- region: ManualSharedTest.java#agg -->
```java
ValueQuery<CustomerTotal> q = qb.from(Order.class)
        .groupBy(o -> o.getCustomer().getId())
        .having(o -> Agg.sum(o.getPrice()) > 1000)
        .select(o -> new CustomerTotal(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice())));
assertEquals("select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id"
        + " having sum(e0.price) > 1000.0", q.toHql().hql());
```

#### Kotlin

<!-- region: ManualKotlinSharedTest.kt#agg -->
```kotlin
val q = qb.from(Order::class.java)
    .groupBy { it.customer?.id }
    .having { Agg.sum(it.price) > 1000 }
    .select { CustomerTotal(it.customer?.id, Agg.count(), Agg.sum(it.price)) }
assertEquals("select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id"
    + " having sum(e0.price) > 1000.0", q.toHql().hql())
```

<a id="a-lambda-is-a-query-not-code"></a>
### A lambda expression is a query expression, not code

lambdaQL follows its own documented rules, not the run-time rules of Java or Kotlin.

#### Java

| What you write | What it means in lambdaQL | Why |
|---|---|---|
| `o.getId() == id` (two `Long` objects) | Value equality: `e0.id = :p1` | In a query, `==` on objects means "the same value", not "the same object". A captured value `null` is bound as NULL and matches no row (`LQL-4045` at `WARN` level). To find NULL, write `o.getId() == null`. |
| `Objects.equals(o.getDescription(), o.getCustName())`, `!text.equals(o.getDescription())` | `e0.description = e0.custName`, `e0.description <> :p1` (SQL meaning) | When you compare two entity values, or compare a value with `!=`, rows where the value is NULL do not match. Java would return some of these rows. To keep these rows, write `o.getDescription() == null \|\| ...`. |
| `o.getCustomer().getName()` | The `name` column of the joined customer | The customer table is joined with an inner `join` or a `left join`. For the detailed rules, see [3.10 Association paths](java/03-where.md#310-association-paths). |
| `o.getStatus() == 1` when the `status` column is NULL | No row is returned (SQL meaning) | Rows where the `status` column is NULL are not in the result. |
| `o.getDescription() == null` | `e0.description is null` | You wrote the null check yourself, so lambdaQL uses it. |

> [!TIP]
> IntelliJ can show the warning "Number objects compared using `==`" on `o.getId() == id`.
> In a lambdaQL lambda expression, this is the intended behavior. Declare `@SuppressWarnings("NumberEquality")` on the method to remove the warning.

#### Kotlin

| What you write | What it means in lambdaQL | Why |
|---|---|---|
| `it.id == id` (`id: Long?`, a nullable variable) | Value equality: `e0.id = :p1` | In Kotlin, `==` already means "the same value". A captured value `null` is bound as NULL and matches no row (`LQL-4045` at `WARN` level). |
| `it.description == it.custName`, `it.description != text` | `e0.description = e0.custName`, `e0.description <> :p1` (SQL meaning) | When you compare two entity values, or compare a value with `!=`, rows where the value is NULL do not match. To keep these rows, write `it.description == null \|\| ...`. |
| `it.customer?.name` or `it.customer!!.name` | The `name` column of the joined customer | The customer table is joined with an inner `join` or a `left join`. For the detailed rules, see [3.10 Association paths](kotlin/03-where.md#310-association-paths). |
| `it.price!! > 100.0` when the `price` column is NULL | No row is returned (SQL meaning) | Rows where the `price` column is NULL are not in the result. |
| `it.description == null` | `e0.description is null` | You wrote the null check yourself, so lambdaQL uses it. |

**NULL is handled the same way as in SQL.**   
Rows where the compared column is NULL do not match a comparison with NULL, so they are not in the result.
For example, `where(o -> !text.equals(o.getDescription()))` (in Kotlin, `where { it.description != text }`) becomes the HQL `e0.description <> :p1`.   
Orders whose `description` column is NULL do not match this condition and are not in the result. This is different from a Java or Kotlin list, where NULL is different from `text` and is included.
To also get the orders with NULL, write the null check yourself, like `o.getDescription() == null || !text.equals(o.getDescription())`.   
For the detailed rules, see 3.6 NULL rules ([Java](java/03-where.md#36-null-rules) | [Kotlin](kotlin/03-where.md#36-null-rules)).

### SARGable SQL

lambdaQL builds SQL that can use indexes ("SARGable").

- It does not put functions, operations or `CASE` on the column side.
- If a value must be changed (for example, to lower case), lambdaQL changes the **parameter** in Java before binding.
- If a rule needs a function on the column and an index does not help, lambdaQL logs a warning.

#### Java

| What you write | What lambdaQL does **not** build | What lambdaQL builds |
|---|---|---|
| `o.getId() != null && o.getId() == id` | `e0.id is not null and e0.id = :p1` | `e0.id = :p1` (id cannot be NULL) |
| `o.getDescription() == text` | `e0.description is not distinct from :p1` (`decode(...)` on Oracle, no index) | `e0.description = :p1` |

#### Kotlin

| What you write | What lambdaQL does **not** build | What lambdaQL builds |
|---|---|---|
| `it.id == id` (`it.id` is `Long?`) | `not (e0.id is null or e0.id <> :p1)` | `e0.id = :p1` (id cannot be NULL) |

<a id="java-and-kotlin"></a>
### Java and Kotlin

Java and Kotlin use the same analysis engine. So a Kotlin lambda expression and a Java lambda expression with the same meaning build the same HQL.
For example, Java's `o.getPrice() > 100` and Kotlin's `it.price > 100.0` both become `e0.price > 100.0`.
But not every syntax can be turned into a query. If you use syntax such as loops, `switch` and Kotlin's `when (x)` with a subject, or most string functions on a column (`trim()` and others), lambdaQL raises an error before it runs the query.
For the differences between the languages, see chapter 9 ([Java notes](java/09-java-notes.md) | [Kotlin notes](kotlin/09-kotlin-notes.md)).

<a id="fail-loudly"></a>
### Lambda expressions that cannot be analyzed

If lambdaQL cannot analyze a lambda expression into a query, it throws an exception with a code such as `[LQL-2004]` (see [11. Errors and languages](11-errors-and-languages.md)).

Examples of rejected forms:

| Form | Java | Kotlin |
|---|---|---|
| A value chosen by a condition | `o.getStatus() == (flag ? 1 : 2)` | `it.status == if (flag) 1 else 2` |
| A loop or a local variable in the lambda expression | Yes, rejected | Yes, rejected |
| Something that is not a lambda expression | An anonymous class | An `object : ...` expression, or a Kotlin function-type value (`val f: (Order) -> Boolean`) |
| A method call that has no meaning in SQL | Yes, rejected | Yes, rejected (including your own non-inline extension functions) |

Example code: [`ManualSharedTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualSharedTest.java) (`one-lambda-per-clause`, `agg`), [`ManualKotlinSharedTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSharedTest.kt) (`one-lambda-per-clause`, `agg`).

<a id="comparison-with-other-approaches"></a>
## Comparison with other approaches

| | JPQL / HQL strings | JPA Criteria API | QueryDSL | lambdaQL |
|---|---|---|---|---|
| How you write a query | Text in a string | Java builder objects | Generated Q-classes | Lambda expressions |
| Compiler checks | ❌ (Hibernate checks at run time) | ✅ | ✅ | ✅ |
| Needs generated code | ❌ | Optional (static metamodel) | ✅ (Q-classes) | ❌ |
| Safe for IDE renames | ❌ | With the static metamodel | ✅ | ✅ |
| Looks like | SQL | Java builder code | SQL-like Java methods | SQL-like Java / Kotlin lambda expressions |
| Kotlin | Strings | Works, but verbose | Works | Supported |

<a id="next"></a>
## Next

- 2. Getting started: build lambdaQL and write your first query. [Java](java/02-getting-started.md) | [Kotlin](kotlin/02-getting-started.md)
- [Manual home](README.md)
