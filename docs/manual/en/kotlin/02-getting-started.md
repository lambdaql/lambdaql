# 2. Getting started (Kotlin)

> [!NOTE]
> **Status:** You can build lambdaQL from source and run its tests today.
> `where` conditions and `toList()` work end to end. They are tested on H2, MariaDB and Oracle with **Java** lambdas. Kotlin lambdas are not tested yet.
> The core of sorting and paging (`orderBy`, `limit`, `offset`) works too (also tested with Java lambdas only).
> Subqueries, `select`, update/insert/delete, procedures and the Spring module are **designed, not implemented yet**.
> No artifact is published yet. The artifact name below is temporary.

[Kotlin track](README.md) | [Manual home](../README.md) | Previous: [1. Overview](../01-overview.md) | Next: [3. Conditions](03-where.md) | [Java version of this page](../java/02-getting-started.md)

Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md)

---

## Requirements

| Item | Version | Notes |
|---|---|---|
| Kotlin | 2.x | The design was checked with kotlinc 1.7.22, 2.1.10, 2.2.21, 2.3.0 and 2.3.21. |
| JVM | Java 21 | Needed to build lambdaQL. |
| `-jvm-target` | 1.8, 11, 17 or 21 | All four compile query lambdas the same way. |
| JPA provider | Hibernate ORM 6.6 | lambdaQL builds Hibernate 6 HQL. The design was verified with 6.6.5. Other JPA providers are not supported. |
| Jakarta Persistence | 3.1 | Comes with Hibernate 6.6. |
| Database | H2 2.3, MariaDB 10.11, Oracle 23.26 (23ai) | These three were verified. Other databases may work, but they were not tested. |
| Spring Boot | 3.4 | Optional. The project tests use Spring Boot 3.4. |
| Kotlin runtime | – | lambdaQL does not add `kotlin-stdlib` or `kotlin-reflect` to your dependencies. |

## Build from source

```bash
git clone https://github.com/codebit0/lambdaql.git
cd lambdaql
./gradlew :query:build
```

- The Gradle wrapper (8.10) runs only on **JDK 23 or lower**. Use JDK 21.
- The repository has `gradle/gradle-daemon-jvm.properties`, so the Gradle daemon uses Java 21.
- In IntelliJ IDEA, set **Settings > Build Tools > Gradle > Gradle JVM** to a Java 21 JDK.

Run the unit tests:

```bash
./gradlew :query:test
```

The tests in [`SelectQueryTest.java`](../../../../query/src/test/java/org/lambdaql/query/SelectQueryTest.java) show what lambdaQL can do today: `qb.from(...).where(...)`, the HQL, the parameters and the rows on H2, MariaDB and Oracle. [`ConditionDecodeTest.java`](../../../../query/src/test/java/org/lambdaql/engine/decode/ConditionDecodeTest.java) tests the internal condition decoder. These tests are written in Java. Kotlin tests are planned (`query/src/test/kotlin`).

### Use the local build in your project

Publish the jar to your local Maven repository:

```bash
./gradlew :query:publishToMavenLocal
```

Then add it to your project (Gradle Kotlin DSL):

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("org.lambdaql:lambadaql-query:1.0.0")
}
```

Maven:

```xml
<dependency>
  <groupId>org.lambdaql</groupId>
  <artifactId>lambadaql-query</artifactId>
  <version>1.0.0</version>
</dependency>
```

> [!WARNING]
> These coordinates are **temporary**.
> The name `lambadaql` has a typo that will be fixed before the first release.
> The group, name and version can change. Check this page again when lambdaQL is published.

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

## Create a QueryBuilder

`QueryBuilder` is the entry point. It needs a JPA `EntityManagerFactory`.

```kotlin
val qb = QueryBuilder(entityManagerFactory)
```

Create one `QueryBuilder` and reuse it. Do not create a new one for each query.

### With plain JPA

```kotlin
val emf = Persistence.createEntityManagerFactory("my-unit")
val qb = QueryBuilder(emf)
```

### With Spring Boot

Spring Boot already creates an `EntityManagerFactory`. Inject it and make a `QueryBuilder` bean.

```kotlin
@Configuration
class LambdaQlConfig {

    @Bean
    fun queryBuilder(entityManagerFactory: EntityManagerFactory) = QueryBuilder(entityManagerFactory)
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

- Until the module `lambdaql-spring-data-jpa` is released, you create the bean yourself. That module will create it for you.
- Inside a Spring transaction, lambdaQL uses the transaction's shared `EntityManager`.
- To write lambdaQL queries inside Spring Data repositories, see [12. Spring Data JPA](12-spring-data-jpa.md).

## Your first query

```kotlin
val orders = qb.from(Order::class.java)
    .where { it.status == 1 && it.price!! > 100.0 }
    .toList()
// HQL: select e0 from Order e0 where e0.status = 1 and e0.price > 100.0
```

**About the SQL blocks.** `…` is the shortened select list (all entity columns), and `?` is a bind parameter. The lines come from [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java). It runs each query on H2 2.3, MariaDB 10.11 and Oracle 23.26 with Hibernate 6.6.5, and it fails if Hibernate's SQL changes. The test uses Java lambdas. A Kotlin lambda with the same meaning gives the same HQL, so it gets the same SQL.

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
- `toList()` is the only result method that is decided now. Other result methods are planned.

Why `!!`? `price` is `Double?`, and Kotlin does not compile `it.price > 100.0` for a nullable value. `!!` makes it compile. lambdaQL **ignores** `!!`. Rows where `price` is NULL are simply not returned (SQL meaning).

Why are `1` and `100.0` written into the HQL, not bind parameters? They are **constants written in the lambda**. lambdaQL writes constants (numbers, strings, booleans, `null`, enum constants, `const val`) into the HQL as literals. Values from outside the lambda are different: they are always bind parameters (see the next section and [3.3](03-where.md#33-basic-form)).

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
-- H2
select …
from orders o1_0
where o1_0.active=true and (o1_0.status=1 or o1_0.status=2)

-- MariaDB, Oracle
select …
from orders o1_0
where o1_0.active=1 and (o1_0.status=1 or o1_0.status=2)
```

This is useful when you add conditions step by step:

```kotlin
var query = qb.from(Order::class.java).where { it.active }
if (minPrice != null) {
    query = query.where { it.price!! >= minPrice }
}
val orders = query.toList()
```

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
If a null guard keeps orders without a customer (`it.customer == null || ...`), lambdaQL uses a `left join`. See [3.10](03-where.md#310-association-paths).

## Things to know

| Rule | Example |
|---|---|
| Pass a lambda literal or a property reference. | `where { it.active }`, `where(Order::active)` |
| Do not store the lambda in a Kotlin function type first. | `val f: (Order) -> Boolean = { it.active }; where(f)` is rejected (KC4). |
| Do not create the query lambda inside an `inline fun`. | Rejected (KC2). Make the helper non-inline, or pass the lambda from the caller. |
| The lambda must be one expression (for conditions, sorting and select). | Loops and local `val`/`var` inside the lambda are rejected. |
| Do not put code that must run in the lambda. The lambda is never run. | Calls with side effects, like `println(...)`, are rejected. |
| New JDK versions need a matching ASM version. | lambdaQL uses ASM 9.11.1 (reads class files up to Java 28). |

All Kotlin limits are in [9. Kotlin notes](09-kotlin-notes.md#914-kotlin-limits-kc1-to-kc21).

## Next

- [3. Conditions (`where`)](03-where.md): all operators and null rules.
- [4. Sorting and paging](04-order-paging.md)

---

[Kotlin track](README.md) | Previous: [1. Overview](../01-overview.md) | Next: [3. Conditions](03-where.md) | [Java version of this page](../java/02-getting-started.md)
