<a id="2-getting-started-kotlin"></a>
# 2. Getting started (Kotlin)

[Kotlin track](README.md) | [Manual home](../README.md) | Previous: [1. Overview](../01-overview.md) | Next: [3. Conditions](03-where.md) | [Java version of this page](../java/02-getting-started.md)

Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md)

---

<a id="requirements"></a>
## Requirements

| Item | Version | Notes |
|---|---|---|
| Kotlin | 2.x | The design was checked with kotlinc 1.7.22, 2.1.10, 2.2.21, 2.3.0 and 2.3.21. |
| JVM | Java 21 | Needed to build lambdaQL. |
| `-jvm-target` | 1.8, 11, 17 or 21 | All four compile query lambdas the same way. |
| JPA provider | Hibernate ORM 7.4 | lambdaQL builds Hibernate 7 HQL. It is tested with 7.4.13. Other JPA providers are not supported. |
| Jakarta Persistence | 3.2 | Comes with Hibernate 7.4. |
| Database | H2 2.3, MariaDB 10.11, Oracle 23.26 (23ai) | These three were verified. Other databases may work, but they were not tested. |
| Spring Boot | 4.1 | Optional. The project tests use Spring Boot 4.1.2. |
| Kotlin runtime | – | lambdaQL does not add `kotlin-stdlib` or `kotlin-reflect` to your dependencies. |

<a id="build-from-source"></a>
## Build from source

```bash
git clone https://github.com/codebit0/lambdaql.git
cd lambdaql
./gradlew build
```

- The Gradle wrapper is 9.9.1. The repository has `gradle/gradle-daemon-jvm.properties`, so the Gradle daemon uses Java 21.
- The build needs JDK 21 and one **JDK 24 or later**. That JDK compiles the `java.lang.classfile` reader (`src/main/java24`) and runs the tests one more time with that reader. The default version is 25. You can change it, for example `-Plql.classfile.jdk=24`. If Gradle cannot find the JDK, put its path in `org.gradle.java.installations.paths` in `~/.gradle/gradle.properties`.
- In IntelliJ IDEA, set **Settings > Build Tools > Gradle > Gradle JVM** to a Java 21 JDK.
- The build has three modules: `query` (artifact `lambdaql-core`), `spring-data-jpa` (`lambdaql-spring-data-jpa`) and `spring-boot-starter` (`lambdaql-spring-boot-starter`). The Spring modules are explained in [12. Spring Data JPA](12-spring-data-jpa.md).

Run the unit tests:

```bash
./gradlew :query:test
```

The tests in [`SelectQueryTest.java`](../../../../query/src/test/java/org/lambdaql/query/SelectQueryTest.java) show what lambdaQL can do today: `qb.from(...).where(...)`, the HQL, the parameters and the rows on H2, MariaDB and Oracle. [`ConditionDecodeTest.java`](../../../../query/src/test/java/org/lambdaql/engine/decode/ConditionDecodeTest.java) tests the internal condition decoder. These tests are written in Java. Kotlin tests are planned (`query/src/test/kotlin`).

<a id="use-the-local-build-in-your-project"></a>
### Use the local build in your project

Publish the jars of the three modules to your local Maven repository:

```bash
./gradlew publishToMavenLocal
```

Then add `lambdaql-core` to your project (Gradle Kotlin DSL):

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("org.lambdaql:lambdaql-core:1.0.0")
}
```

Maven:

```xml
<dependency>
  <groupId>org.lambdaql</groupId>
  <artifactId>lambdaql-core</artifactId>
  <version>1.0.0</version>
</dependency>
```

With Spring Data JPA or Spring Boot, add `lambdaql-spring-boot-starter` or `lambdaql-spring-data-jpa` instead. Both bring `lambdaql-core` with them (see [12.2](12-spring-data-jpa.md#122-modules)).

> [!WARNING]
> The version `1.0.0` comes from `gradle.properties` of the repository. It can change before the first release.
> Check this page again when lambdaQL is published to Maven Central.

<a id="kotlin-compiler-settings"></a>
## Kotlin compiler settings

| Setting | What to do | Why |
|---|---|---|
| `-Xsam-conversions` | Keep the default (`indy`). Do **not** set `-Xsam-conversions=class`. | With `class`, Kotlin makes a class instead of a serializable lambda. lambdaQL cannot read it (limit KC1). |
| `-java-parameters` | Recommended if you call stored procedures. | lambdaQL can then compare your parameter names with the database (see [8.4](08-procedures-functions.md#84-warning-argument-order)). |
| JPA plugins | Use `kotlin("plugin.jpa")` and all-open (or `kotlin("plugin.spring")`). | JPA needs a no-argument constructor and open classes. This is normal for Kotlin JPA entities. |

Example (`build.gradle.kts`):

```kotlin
plugins {
    kotlin("jvm")
    kotlin("plugin.jpa")
    kotlin("plugin.spring")   // or the all-open plugin
}

kotlin {
    compilerOptions {
        javaParameters = true   // same as -java-parameters
    }
}
```

Use the plugin versions that match your Kotlin version.

<a id="example-entities"></a>
## Example entities

All examples in the Kotlin track use these entities.

```kotlin
@Entity
@Table(name = "orders")
class Order(
    @Id @GeneratedValue
    var id: Long? = null,
    var price: Double? = null,
    var status: Int? = null,
    var active: Boolean = false,          // Kotlin Boolean: a JVM primitive, never NULL
    var description: String? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    var customer: Customer? = null,
    var updateAt: LocalDateTime? = null,
    @OneToMany(mappedBy = "order")
    var items: MutableList<Item> = mutableListOf(),
)

@Entity
class Customer(
    @Id @GeneratedValue
    var id: Long? = null,
    var name: String? = null,
    var grade: Int? = null,
)

@Entity
class Item(
    @Id @GeneratedValue
    var id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    var order: Order? = null,
    var price: Double? = null,
    var qty: Int = 0,                     // Kotlin Int: a JVM primitive, never NULL
)
```

Rules for entities:

| Rule | Why |
|---|---|
| Use normal public `var` or `val` properties. | lambdaQL reads the getter (`it.price` is the getter `getPrice()`) and maps it to the JPA attribute. |
| Use nullable types (`Double?`, `String?`) for columns that can be NULL. | A Kotlin non-null type does not stop NULL from the database. Hibernate writes the column value into the field without a check. |
| Do not use `@JvmField`, `internal` properties, `value class` properties or `@get:JvmName` in queries. | lambdaQL cannot map these to a JPA attribute (limits KC15, KC16). |
| `Boolean`, `Int`, `Long`, `Double` (not nullable) are JVM primitives. | They never hold NULL. lambdaQL knows this and can write simpler SQL. |

<a id="create-a-querybuilder"></a>
## Create a QueryBuilder

`QueryBuilder` is the entry point. It needs a JPA `EntityManagerFactory` or an `EntityManager`.

```kotlin
val qb = QueryBuilder(entityManagerFactory)
```

Create one `QueryBuilder` and reuse it. Do not create a new one for each query.

<a id="with-plain-jpa"></a>
### With plain JPA

```kotlin
val emf = Persistence.createEntityManagerFactory("my-unit")
val qb = QueryBuilder(emf)
```

<a id="with-spring-boot"></a>
### With Spring Boot

With `lambdaql-spring-boot-starter`, Spring Boot makes the `QueryBuilder` bean for you (see [12.8](12-spring-data-jpa.md#128-the-querybuilder-bean)).
Without the starter, make the bean yourself. Use Spring's shared `EntityManager`, as the auto-configuration does.

```kotlin
@Configuration
class LambdaQlConfig {

    @Bean
    fun queryBuilder(entityManagerFactory: EntityManagerFactory) =
        QueryBuilder(SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory))
}
```

Then inject `QueryBuilder` where you need it.

```kotlin
@Service
class OrderService(private val qb: QueryBuilder) {

    @Transactional(readOnly = true)
    fun activeOrders(): List<Order> =
        qb.from(Order::class.java)
            .where { it.active }
            .toList()
}
```

- The shared `EntityManager` is bound to the current transaction. Inside `@Transactional`, lambdaQL runs in the same transaction.
- Do not use the `EntityManagerFactory` constructor in a Spring application. A builder made with it opens its own `EntityManager` for each query, and that `EntityManager` does not take part in the Spring transaction.
- To write lambdaQL queries inside Spring Data repositories, see [12. Spring Data JPA](12-spring-data-jpa.md).

<a id="your-first-query"></a>
## Your first query

```kotlin
val orders = qb.from(Order::class.java)
    .where { it.status == 1 && it.price!! > 100.0 }
    .toList()
// HQL: select e0 from Order e0 where e0.status = 1 and e0.price > 100.0
```

**About the SQL blocks.** `…` is the shortened select list (all entity columns), and `?` is a bind parameter. The lines come from [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java). It runs each query on H2 2.3, MariaDB 10.11 and Oracle 23.26 with Hibernate 7.4, and it fails if Hibernate's SQL changes. The test uses Java lambdas. A Kotlin lambda with the same meaning gives the same HQL, so it gets the same SQL.

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `to-hql`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>100.0
```

- `qb.from(Order::class.java)` starts a query on `Order`.
- `where { ... }` takes one condition lambda. Use the trailing lambda and `it`.
- `toList()` runs the query and returns a `List<Order>`.
- `toList()` returns all rows. The other result functions (`first()`, `one()`, `count()`, `exists()`, `toSet()`) are in [Getting the result](#getting-the-result).

Why `!!`? `price` is `Double?`, and Kotlin does not compile `it.price > 100.0` for a nullable value. `!!` makes it compile. lambdaQL **ignores** `!!`. Rows where `price` is NULL are simply not returned (SQL meaning).

Why are `1` and `100.0` written into the HQL, not bind parameters? Both are **constants written in the lambda**. lambdaQL writes constants (numbers, strings, booleans, `null`, enum constants, `const val`) into the HQL as literals. Values from outside the lambda are different: they are always bind parameters (see the next section and [3.3](03-where.md#33-basic-form)).

<a id="use-values-from-outside-the-lambda"></a>
### Use values from outside the lambda

A lambda can use local values, properties and function parameters. These are **captured values**.
lambdaQL turns them into bind parameters. It reads the new value each time the query runs.

```kotlin
fun expensive(status: Int, minPrice: Double): List<Order> =
    qb.from(Order::class.java)
        .where { it.status == status && it.price!! >= minPrice }
        .toList()
// HQL: select e0 from Order e0 where e0.status = :p1 and e0.price >= :p2
```

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `start-captured`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=? and o1_0.price>=?
```

<a id="many-where-calls"></a>
### Many `where` calls

You can call `where` more than once. lambdaQL joins the conditions with `AND`.

```kotlin
qb.from(Order::class.java)
    .where { it.active }
    .where { it.status == 1 || it.status == 2 }
    .toList()
// HQL: select e0 from Order e0
//      where e0.active = true and (e0.status = 1 or e0.status = 2)
```

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `start-many-where`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.active=true and (o1_0.status=1 or o1_0.status=2)
```

This is useful when you add conditions step by step:

```kotlin
var query = qb.from(Order::class.java).where { it.active }
if (minPrice != null) {
    query = query.where { it.price!! >= minPrice }
}
val orders = query.toList()
```

<a id="paths-to-other-entities"></a>
### Paths to other entities

A path like `it.customer?.name` names a column of the joined entity.
In `where`, lambdaQL adds an inner `join` by default. Orders without a customer are **not** in the result.

```kotlin
qb.from(Order::class.java)
    .where { it.customer?.name == name }
    .toList()
// HQL: select e0 from Order e0 join e0.customer j1 where j1.name = :p1
//      (name is a non-null String here, so lambdaQL writes a plain =; see 3.6)
```

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `inner-join`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name=?
```

`it.customer!!.name` gives the same HQL. Use the form you like.
To find orders without a customer, compare the association: `it.customer == null` gives `e0.customer is null` (no join).
`it.customer?.name == null` does **not** include them.
A null guard (`it.customer == null || ...`) does not keep orders without a customer either, because the path is an inner join (decision D48).
To keep such orders, use `leftJoin` ([6.8](06-select-group.md#68-explicit-joins)). See [3.10](03-where.md#310-association-paths).

<a id="getting-the-result"></a>
## Getting the result

`toList()` is not the only way to run a query. lambdaQL has six result functions (decisions D38 and D42).

| Function | Returns | Where you can call it | What it does |
|---|---|---|---|
| `toList()` | `List<T>` | after `where`, and after `orderBy` / `limit` / `offset` | Returns all rows (or one page). |
| `first()` | `Optional<T>` | after `where`, and after `orderBy` / `limit` / `offset` | Returns the first row. Reads only one row. |
| `one()` | `Optional<T>` | after `where` only | Returns the only row. Throws `NonUniqueResultException` if 2 or more rows match. Reads at most 2 rows. |
| `count()` | `Long` | after `where` only | Returns the number of matching rows. |
| `exists()` | `Boolean` | after `where` only | Returns `true` if a row matches. Reads one row and loads no entity. |
| `toSet()` | `SequencedSet<T>` | after `where`, and after `orderBy` / `limit` / `offset` | Returns all rows (or one page) as a set that keeps the row order. Throws `IllegalStateException` if the set is smaller than the number of rows. |

"After `where`" means the query you get from `qb.from(...)` and `where { }` (the type `SelectQuery`). In this manual it is called the **where stage**. You can also call these functions right after `qb.from(...)`, with no `where`.

After `orderBy`, `limit` or `offset`, the query has the type `OrderedSelectQuery`. Its only result functions are `toList()`, `toSet()` and `first()`. `one()`, `count()` and `exists()` do not compile there.
This is on purpose (decision D38): `limit(10).count()` could mean "all rows" or "at most 10 rows", so lambdaQL does not allow it.

`first()` and `one()` return a Java `Optional`. In Kotlin, `.orElse(null)` turns it into a nullable value (`Order?`).

There are no Kotlin tests for the result functions yet. The same calls written in Java are tested in [`ResultQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ResultQueryTest.java) (H2, MariaDB and Oracle). See [the Java version of this page](../java/02-getting-started.md#getting-the-result) for the tested examples.

<a id="first"></a>
### `first()`

Without `orderBy`, "first" means the row with the smallest id (rule S9). So you always get the same row.
To choose the order, call `orderBy { }` first. Then `first()` returns the first row in that order. An `offset` is kept: `orderBy { ... }.offset(1).first()` returns the second row.
If no row matches, the result is `Optional.empty()`.

```kotlin
val cheapest: Order? = qb.from(Order::class.java)
    .orderBy { o, by -> by.asc(o.price) }
    .first()
    .orElse(null)
// HQL: select e0 from Order e0 order by e0.price asc nulls first, e0.id asc

// without orderBy, "first" is the row with the smallest id
val firstActive = qb.from(Order::class.java).where { it.active }.first()
// HQL: select e0 from Order e0 where e0.active = true order by e0.id asc
```

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `first#2` (`firstActive`):

```sql
-- H2, Oracle
select …
from orders o1_0
where o1_0.active=true
order by o1_0.id
fetch first ? rows only

-- MariaDB
select …
from orders o1_0
where o1_0.active=true
order by o1_0.id
limit ?
```

`first()` reads one row, so it is a paged query. lambdaQL adds `id` as the last sort key (see [4.8](04-order-paging.md#tie-breaker-automatic-id)), and Hibernate writes the paging for each database: `fetch first ? rows only` on H2 and Oracle, `limit ?` on MariaDB. The `?` is 1.
The test for `cheapest` uses a `price` that can never be NULL, so its HQL has no `nulls` part. Here `price` is `Double?`, so the HQL is a little different (see the Java page, example `first`).

<a id="one"></a>
### `one()`

Use `one()` when the condition can match at most one row, for example when you look up a unique key.
No row gives `Optional.empty()`. Two or more rows throw `jakarta.persistence.NonUniqueResultException`. If any matching row is fine, use `first()`.

```kotlin
fun find(id: Long): Order? =
    qb.from(Order::class.java).where { it.id == id }.one().orElse(null)
// HQL: select e0 from Order e0 where e0.id = :p1

// two or more rows: an exception (use first() if any row is fine)
assertThrows<NonUniqueResultException> { qb.from(Order::class.java).where { it.active }.one() }
```

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `one`:

```sql
-- H2, Oracle
select …
from orders o1_0
where o1_0.id=?
fetch first ? rows only

-- MariaDB
select …
from orders o1_0
where o1_0.id=?
limit ?
```

`one()` asks the database for at most 2 rows. That is enough to see whether a second row exists. So the SQL has `fetch first ? rows only` (H2, Oracle) or `limit ?` (MariaDB), and the `?` is 2. There is no `order by`, because the order does not matter here.

<a id="count-and-a-paged-screen"></a>
### `count()` and a paged screen

`count()` returns the number of matching rows. It is only on the where stage, so `limit` and `offset` cannot change the result.

For a paged screen, keep the where-stage query in a value. Call `count()` on it for the total. Then call `orderBy { }.limit(...).toList()` on the same value for the page.

```kotlin
val active = qb.from(Order::class.java).where { it.active }
val total: Long = active.count()                       // every matching row
val page = active.orderBy { o, by -> by.desc(o.price) }.limit(20).toList()
// count HQL: select count(e0) from Order e0 where e0.active = true
```

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `count`:

```sql
-- H2, MariaDB, Oracle (same)
select count(o1_0.id)
from orders o1_0
where o1_0.active=true
```

Hibernate turns `count(e0)` into `select count(o1_0.id)`: it counts the id column. The count query has no paging.

<a id="exists"></a>
### `exists()`

`exists()` returns `true` if at least one row matches. It runs `select 1` and reads at most one row. No entity is loaded.

```kotlin
val anyHighStatus: Boolean = qb.from(Order::class.java).where { it.status == 3 }.exists()
// HQL: select 1 from Order e0 where e0.status = 3
```

The tested example (`exists` in the Java test) uses an enum property that the Kotlin example entity does not have. Its SQL has the same shape: `select 1 from orders o1_0 where … fetch first ? rows only` on H2 and Oracle, and `… limit ?` on MariaDB. The `?` is 1. See [the Java version of this page](../java/02-getting-started.md#exists).

<a id="toset"></a>
### `toSet()`

`toSet()` runs the query like `toList()` and returns a `SequencedSet<T>` (decision D42).
The returned object is a `LinkedHashSet`, so the elements keep the row order. With `orderBy { }`, this is the sort order.

```kotlin
val byPrice: SequencedSet<Order> = qb.from(Order::class.java)
    .orderBy { o, by -> by.desc(o.price) }
    .toSet()
```

The SQL is the same as for `toList()`. lambdaQL does **not** add `distinct`:

- Each entity row has a primary key. So even a query on one table never returns the same row twice.
- lambdaQL joins only to-one associations. A collection becomes an `exists` subquery. So a join never repeats a row.
- So `distinct` would not change the result. It would only add cost. (This is not about a database limit: Oracle 23.26 accepts `distinct` on LOB columns too. This was measured.)

For value results (`select`), `distinct()` and `toSet()` use `select distinct` (see [6.7](06-select-group.md#67-distinct-paging-and-results)).

**Size check.** If the set is smaller than the number of rows, `toSet()` throws `IllegalStateException`. This happens when:

- the entity's `equals` / `hashCode` treats two different rows as equal, or
- its `@Id` is not unique (for example, an entity mapped to a view).

Without the check, rows would be lost without any sign. In these cases, use `toList()`.
A Kotlin `data class` compares only the properties of its primary constructor. If the `@Id` is not in the primary constructor, `equals` can return true for two different rows.

```kotlin
// Tag.equals compares only the name: two different rows would become one element, so toSet() refuses
assertThrows<IllegalStateException> { qb.from(Tag::class.java).toSet() }
val tags: List<Tag> = qb.from(Tag::class.java).toList()   // use toList() instead
```

There are no Kotlin tests for `toSet()` yet. The same calls written in Java are tested in [`ResultQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ResultQueryTest.java) (regions `to-set` and `to-set-check`, H2, MariaDB and Oracle). See [the Java version of this page](../java/02-getting-started.md#toset).

<a id="things-to-know"></a>
## Things to know

| Rule | Example |
|---|---|
| Pass a lambda literal or a property reference. | `where { it.active }`, `where(Order::active)` |
| Do not store the lambda in a Kotlin function type first. | `val f: (Order) -> Boolean = { it.active }; where(f)` is rejected (KC4). |
| Do not create the query lambda inside an `inline fun`. | Rejected (KC2). Make the helper non-inline, or pass the lambda from the caller. |
| The lambda must be one expression (for conditions, sorting and select). | Loops and local `val`/`var` inside the lambda are rejected. |
| Do not put code that must run in the lambda. The lambda is never run. | Calls with side effects, like `println(...)`, are rejected. |
| When you run on Java 21–23, classes compiled with a new JDK need a matching ASM version. | On Java 21–23, lambdaQL uses ASM 9.11.1 (reads class files up to Java 28). On Java 24 or later, lambdaQL reads with the running JDK's `java.lang.classfile`, so the ASM version does not matter. |

All Kotlin limits are in [9. Kotlin notes](09-kotlin-notes.md#914-kotlin-limits-kc1-to-kc21).

<a id="next"></a>
## Next

- [3. Conditions (`where`)](03-where.md): all operators and null rules.
- [4. Sorting and paging](04-order-paging.md)

---

[Kotlin track](README.md) | Previous: [1. Overview](../01-overview.md) | Next: [3. Conditions](03-where.md) | [Java version of this page](../java/02-getting-started.md)
