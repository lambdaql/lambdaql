# 2. Getting started (Java)

> [!IMPORTANT]
> This page is older than the Korean manual. See the [Korean page](../../ko/java/02-getting-started.md) for the latest rules.

[Java track](README.md) | [Manual home](../README.md) | Previous: [1. Overview](../01-overview.md) | Next: [3. Conditions](03-where.md) | [Kotlin version of this page](../kotlin/02-getting-started.md)

Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md)

---

## Requirements

| Item | Version | Notes |
|---|---|---|
| Java | 21 | Needed to build and to use lambdaQL. |
| JPA provider | Hibernate ORM 6.6 | lambdaQL builds Hibernate 6 HQL. The design was verified with 6.6.5. Other JPA providers are not supported. |
| Jakarta Persistence | 3.1 | Comes with Hibernate 6.6. |
| Database | H2 2.3, MariaDB 10.11, Oracle 23.26 (23ai) | These three were verified. Other databases may work, but they were not tested. |
| Spring Boot | 3.4 | Optional. The project tests use Spring Boot 3.4. |
| Compiler | javac | Lambdas compiled by ECJ (the Eclipse compiler) are not guaranteed. See [9. Java notes](09-java-notes.md#912-compiler-and-build). |

## Build from source

```bash
git clone https://github.com/codebit0/lambdaql.git
cd lambdaql
./gradlew build
```

- The Gradle wrapper (8.10) runs only on **JDK 23 or lower**. Use JDK 21.
- The repository has `gradle/gradle-daemon-jvm.properties`, so the Gradle daemon uses Java 21.
- In IntelliJ IDEA, set **Settings > Build Tools > Gradle > Gradle JVM** to a Java 21 JDK.
- The build has three modules: `query` (artifact `lambdaql-core`), `spring-data-jpa` (`lambdaql-spring-data-jpa`) and `spring-boot-starter` (`lambdaql-spring-boot-starter`). The Spring modules are explained in [12. Spring Data JPA](12-spring-data-jpa.md).

Run the unit tests:

```bash
./gradlew :query:test
```

These tests show what lambdaQL can do today. `SelectQueryTest` uses the user API; the other tests check internal classes:

| Test file | What it shows |
|---|---|
| [`SelectQueryTest.java`](../../../../query/src/test/java/org/lambdaql/query/SelectQueryTest.java) | `qb.from(...).where(...)`: the HQL, the parameters and the rows. Runs on H2 by default; add `-Dlql.db=mariadb` or `-Dlql.db=oracle` for the other databases |
| [`LoadExamplesTest.java`](../../../../query/src/test/java/org/lambdaql/engine/load/LoadExamplesTest.java) | Loading a lambda: captured values, a lambda that captures `this`, method references, a lambda that is not `Serializable` |
| [`ConditionDecodeTest.java`](../../../../query/src/test/java/org/lambdaql/engine/decode/ConditionDecodeTest.java) | Decoding conditions: comparisons, captured values, `&&`, `\|\|`, `!` |
| [`IrExamplesTest.java`](../../../../query/src/test/java/org/lambdaql/engine/ir/IrExamplesTest.java) | The internal condition tree |
| [`HostEvaluatorTest.java`](../../../../query/src/test/java/org/lambdaql/engine/sem/HostEvaluatorTest.java) | Computing captured values in Java, number conversion |

For example, `ConditionDecodeTest` checks that `o -> o.getStatus() == 1 || o.getStatus() == 2 || o.getStatus() == 3` is decoded into one `OR` with three comparisons, in source order.

### Use the local build in your project

Publish the jars of the three modules to your local Maven repository:

```bash
./gradlew publishToMavenLocal
```

Then add `lambdaql-core` to your project.

Gradle (Groovy DSL):

```groovy
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation 'org.lambdaql:lambdaql-core:1.0.0'
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

With Spring Data JPA or Spring Boot, use `lambdaql-spring-boot-starter` or `lambdaql-spring-data-jpa` instead. They bring `lambdaql-core` (see [12.2](12-spring-data-jpa.md#122-modules)).

> [!WARNING]
> The artifacts are **not published to Maven Central yet**.
> The version `1.0.0` comes from `gradle.properties` of the repository. It can change before the first release. Check this page again when lambdaQL is published.

### Recommended compiler option: `-parameters`

Compile with `-parameters`. Then lambdaQL can see the parameter names of your procedure stub methods and find swapped arguments (see [8.4](08-procedures-functions.md#84-warning-argument-order)).

Gradle (Groovy DSL):

```groovy
tasks.withType(JavaCompile).configureEach {
    options.compilerArgs << '-parameters'
}
```

Maven (`maven-compiler-plugin`):

```xml
<configuration>
  <parameters>true</parameters>
</configuration>
```

## Example entities

All examples in the Java track use these entities.

```java
@Entity
@Table(name = "orders")
public class Order {
    @Id @GeneratedValue
    private Long id;
    private Double price;
    private Integer status;
    private boolean active;              // getter: isActive()
    private String description;
    @ManyToOne(fetch = FetchType.LAZY)
    private Customer customer;
    private LocalDateTime updateAt;
    @OneToMany(mappedBy = "order")
    private List<Item> items;

    // getters and setters
}

@Entity
public class Customer {
    @Id @GeneratedValue
    private Long id;
    private String name;
    private Integer grade;

    // getters and setters
}

@Entity
public class Item {
    @Id @GeneratedValue
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    private Order order;
    private Double price;
    private int qty;

    // getters and setters
}
```

Rules for entities:

- lambdaQL reads **getters** (`o.getPrice()`, `o.isActive()`). It maps them to JPA attributes with the JPA Metamodel. It does not read fields directly.
- You do not need generated code (no Q-classes, no static metamodel).
- Most fields are boxed types (`Long`, `Double`, `Integer`). This matters for `==`. See [9. Java notes](09-java-notes.md#92-boxing-and-unboxing).

## Create a QueryBuilder

`QueryBuilder` is the entry point. It needs a JPA `EntityManagerFactory` or an `EntityManager`.

```java
QueryBuilder qb = new QueryBuilder(entityManagerFactory);
```

Create one `QueryBuilder` and reuse it. Do not create a new one for each query.

### With plain JPA

```java
EntityManagerFactory emf = Persistence.createEntityManagerFactory("my-unit");
QueryBuilder qb = new QueryBuilder(emf);
```

### With Spring Boot

With `lambdaql-spring-boot-starter`, Spring Boot makes the `QueryBuilder` bean for you (see [12.8](12-spring-data-jpa.md#128-the-querybuilder-bean)).
Without it, make the bean yourself. Use Spring's shared `EntityManager`, as the auto-configuration does.

```java
@Configuration
public class LambdaQlConfig {

    @Bean
    public QueryBuilder queryBuilder(EntityManagerFactory entityManagerFactory) {
        return new QueryBuilder(SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory));
    }
}
```

Then inject `QueryBuilder` where you need it.

```java
@Service
public class OrderService {

    private final QueryBuilder qb;

    public OrderService(QueryBuilder qb) {
        this.qb = qb;
    }

    @Transactional(readOnly = true)
    public List<Order> activeOrders() {
        return qb.from(Order.class)
                 .where(o -> o.isActive())
                 .toList();
    }
}
```

- The shared `EntityManager` is bound to the current transaction. Inside `@Transactional`, lambdaQL runs in the same transaction.
- Do not use the `EntityManagerFactory` constructor in a Spring application. That builder opens its own `EntityManager` for each query, outside the Spring transaction.
- To write lambdaQL queries inside Spring Data repositories, see [12. Spring Data JPA](12-spring-data-jpa.md).

## Your first query

```java
List<Order> orders = qb.from(Order.class)
    .where(o -> o.getStatus() == 1 && o.getPrice() > 100)
    .toList();
// HQL: select e0 from Order e0 where e0.status = 1 and e0.price > 100.0
```

**About the SQL blocks.** `…` is the shortened select list (all entity columns), and `?` is a bind parameter. The lines come from [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java). It runs each query on H2 2.3, MariaDB 10.11 and Oracle 23.26 with Hibernate 6.6.5, and it fails if Hibernate's SQL changes.

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `to-hql`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>100.0
```

- `qb.from(Order.class)` starts a query on `Order`.
- `where(...)` takes one condition lambda.
- `toList()` runs the query and returns a `List<Order>`.
- `toList()` returns all rows. The other result functions (`first()`, `one()`, `count()`, `exists()`, `toSet()`) are in [Getting the result](#getting-the-result).

Why are `1` and `100.0` written into the HQL, not bind parameters?
They are **constants written in the lambda**. lambdaQL writes constants (numbers, strings, booleans, `null`, enum constants) into the HQL as literals.
`price` is a `Double`, so javac compiles `100` as the decimal number `100.0`.
Values from outside the lambda are different: they are always bind parameters (see the next section and [3.3](03-where.md#33-basic-form)).

### Use values from outside the lambda

A lambda can use local variables, fields and method parameters. These are **captured values**.
lambdaQL turns them into bind parameters. It reads the new value each time the query runs.

```java
public List<Order> expensive(int status, double minPrice) {
    return qb.from(Order.class)
             .where(o -> o.getStatus() == status && o.getPrice() >= minPrice)
             .toList();
    // HQL: select e0 from Order e0 where e0.status = :p1 and e0.price >= :p2
}
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `start-captured`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=? and o1_0.price>=?
```

### Many `where` calls

You can call `where` more than once. lambdaQL joins the conditions with `AND`.

```java
qb.from(Order.class)
  .where(o -> o.isActive())
  .where(o -> o.getStatus() == 1 || o.getStatus() == 2)
  .toList();
// HQL: select e0 from Order e0
//      where e0.active = true and (e0.status = 1 or e0.status = 2)
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `start-many-where`:

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

```java
var query = qb.from(Order.class).where(o -> o.isActive());
if (minPrice != null) {
    query = query.where(o -> o.getPrice() >= minPrice);
}
List<Order> orders = query.toList();
```

Use `var` for the builder variable. The names of the builder types are not final.

### Paths to other entities

A path like `o.getCustomer().getName()` names a column of the joined entity.
In `where`, lambdaQL adds an inner `join` by default. Orders without a customer are **not** in the result.

```java
qb.from(Order.class)
  .where(o -> o.getCustomer().getName().equals(name))
  .toList();
// HQL: select e0 from Order e0 join e0.customer j1 where j1.name = :p1
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `inner-join`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name=?
```

To find orders without a customer, compare the association: `o.getCustomer() == null` gives `e0.customer is null` (no join).
A null guard (`o.getCustomer() == null || ...`) does not keep orders without a customer: the path is an inner join (decision D48). Use `leftJoin` for that ([6.8](06-select-group.md#68-explicit-joins)).
See [3.10](03-where.md#310-association-paths).

## Getting the result

`toList()` is not the only way to run a query. lambdaQL has six result functions (decisions D38 and D42):

| Function | Returns | Where you can call it | What it does |
|---|---|---|---|
| `toList()` | `List<T>` | after `where`, and after `orderBy` / `limit` / `offset` | Returns all rows (or one page). |
| `first()` | `Optional<T>` | after `where`, and after `orderBy` / `limit` / `offset` | Returns the first row. Reads only one row. |
| `one()` | `Optional<T>` | after `where` only | Returns the only row. Throws `NonUniqueResultException` if 2 or more rows match. Reads at most 2 rows. |
| `count()` | `long` | after `where` only | Returns the number of matching rows. |
| `exists()` | `boolean` | after `where` only | Returns `true` if a row matches. Reads one row and loads no entity. |
| `toSet()` | `SequencedSet<T>` | after `where`, and after `orderBy` / `limit` / `offset` | Returns all rows (or one page) as a set that keeps the row order. Throws `IllegalStateException` if the set is smaller than the number of rows. |

"After `where`" means the query you get from `qb.from(...)` and `where(...)` (the type `SelectQuery`). In this manual it is called the **where stage**. You can also call the functions right after `qb.from(...)`, with no `where`.

After `orderBy`, `limit` or `offset`, the query has the type `OrderedSelectQuery`. Its only result functions are `toList()`, `toSet()` and `first()`. `one()`, `count()` and `exists()` do not compile there.
This is on purpose (decision D38): `limit(10).count()` could mean "all rows" or "at most 10 rows", so lambdaQL does not allow it.

The examples below are copied from [`ResultQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ResultQueryTest.java). The test runs them on H2, MariaDB and Oracle and compares the result with the same answer computed in Java.
It uses the test entity from [3.3](03-where.md#33-basic-form): `price` is `double`, so its sort has no `nulls` part, and `Order` has an enum `kind`.

### `first()`

Without `orderBy`, "first" means the row with the smallest id (rule S9). So you always get the same row.
To choose the order, call `orderBy(...)` first. Then `first()` returns the first row in that order. An `offset` is kept: `orderBy(...).offset(1).first()` returns the second row.
If no row matches, the result is `Optional.empty()`.

**(tested)** [`ResultQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ResultQueryTest.java), region `first`:

```java
Optional<Order> cheapest = qb.from(Order.class)
        .orderBy((o, by) -> by.asc(o.getPrice()))
        .first();
// without orderBy, "first" is the row with the smallest id
Optional<Order> firstActive = qb.from(Order.class).where(o -> o.isActive()).first();
Optional<Order> none = qb.from(Order.class).where(o -> o.getStatus() == 9).first();
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `first` (`cheapest`):

```sql
-- H2, Oracle
select …
from orders o1_0
order by o1_0.price,o1_0.id
fetch first ? rows only

-- MariaDB
select …
from orders o1_0
order by o1_0.price,o1_0.id
limit ?
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `first#2` (`firstActive`):

```sql
-- H2
select …
from orders o1_0
where o1_0.active=true
order by o1_0.id
fetch first ? rows only

-- MariaDB
select …
from orders o1_0
where o1_0.active=1
order by o1_0.id
limit ?

-- Oracle
select …
from orders o1_0
where o1_0.active=1
order by o1_0.id
fetch first ? rows only
```

`first()` reads one row, so it is a paged query. lambdaQL adds `id` as the last sort key (see [4.8](04-order-paging.md#tie-breaker-automatic-id)), and Hibernate writes the paging for each database: `fetch first ? rows only` on H2 and Oracle, `limit ?` on MariaDB. The `?` is 1.

### `one()`

Use `one()` when the condition can match at most one row, for example a unique key.
No row gives `Optional.empty()`. Two or more rows throw `jakarta.persistence.NonUniqueResultException`. If any matching row is fine, use `first()`.

**(tested)** [`ResultQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ResultQueryTest.java), region `one`:

```java
long id = knownId;
Optional<Order> order = qb.from(Order.class).where(o -> o.getId() == id).one();
Optional<Order> missing = qb.from(Order.class).where(o -> o.getId() == -1L).one();
// two or more rows: an exception (use first() if any row is fine)
assertThrows(NonUniqueResultException.class, () -> qb.from(Order.class).where(o -> o.isActive()).one());
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `one`:

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

`one()` asks the database for at most 2 rows. That is enough to see a second row. So the SQL has `fetch first ? rows only` (H2, Oracle) or `limit ?` (MariaDB), and the `?` is 2. There is no `order by`, because the order does not matter here.

### `count()` and a paged screen

`count()` returns the number of matching rows. It is only on the where stage, so `limit` and `offset` can never change it.

For a paged screen, keep the where-stage query in a variable. Call `count()` on it for the total. Then call `orderBy(...).limit(...).toList()` on the same variable for the page.

**(tested)** [`ResultQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ResultQueryTest.java), region `count`:

```java
SelectQuery<Order> active = qb.from(Order.class).where(o -> o.isActive());
long total = active.count();                                   // every matching row
List<Order> page = active.orderBy((o, by) -> by.desc(o.getPrice())).limit(2).toList();
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `count`:

```sql
-- H2
select count(o1_0.id)
from orders o1_0
where o1_0.active=true

-- MariaDB, Oracle
select count(o1_0.id)
from orders o1_0
where o1_0.active=1
```

The HQL is `select count(e0) from Order e0 where e0.active = true`. Hibernate turns `count(e0)` into `select count(o1_0.id)`: it counts the id column. The count query has no paging.

### `exists()`

`exists()` returns `true` if at least one row matches. It runs `select 1` and reads at most one row. No entity is loaded.

**(tested)** [`ResultQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ResultQueryTest.java), region `exists`:

```java
boolean anyRush = qb.from(Order.class).where(o -> o.getKind() == Order.Kind.RUSH).exists();
boolean anyStatus9 = qb.from(Order.class).where(o -> o.getStatus() == 9).exists();
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `exists` (`anyRush`):

```sql
-- H2, Oracle
select 1
from orders o1_0
where o1_0.kind='RUSH'
fetch first ? rows only

-- MariaDB
select 1
from orders o1_0
where o1_0.kind='RUSH'
limit ?
```

The HQL is `select 1 from Order e0 where e0.kind = org.lambdaql.query.Order$Kind.RUSH`. The SQL selects only the constant `1`, so no column is read, and the paging (`?` is 1) stops after the first row.

### `toSet()`

`toSet()` runs the query like `toList()`, but returns a `SequencedSet<T>` (decision D42).
It is a `LinkedHashSet`, so the elements keep the row order. With `orderBy`, this is the sort order.

**(tested)** [`ResultQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ResultQueryTest.java), region `to-set`:

```java
java.util.SequencedSet<Order> byPrice = qb.from(Order.class)
        .orderBy((o, by) -> by.desc(o.getPrice()))
        .toSet();
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

In the next test, `Tag.equals` compares only the name, and the table has two rows with the same name.

**(tested)** [`ResultQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ResultQueryTest.java), region `to-set-check`:

```java
// Tag.equals compares only the name: two different rows would become one element, so toSet() refuses
assertThrows(IllegalStateException.class, () -> qb.from(Tag.class).toSet());
assertEquals(2, qb.from(Tag.class).toList().size());
```

## Things to know

| Rule | Example |
|---|---|
| Use a lambda or a method reference. Anonymous classes are rejected. | `where(o -> o.isActive())`, `where(Order::isActive)` |
| The lambda must be one expression (for conditions, sorting and select). | Loops and local variables inside the lambda are rejected. |
| Do not put code that must run in the lambda. The lambda is never run. | Calls with side effects, like `System.out.println(...)`, are rejected. |
| Compile with javac. | ECJ output is not guaranteed. |
| New JDK versions need a matching ASM version. | lambdaQL uses ASM 9.11.1 (reads class files up to Java 28). If you compile with a newer JDK, update ASM. |

More Java details are in [9. Java notes](09-java-notes.md).

## Next

- [3. Conditions (`where`)](03-where.md): all operators and NULL rules.
- [4. Sorting and paging](04-order-paging.md)

---

[Java track](README.md) | Previous: [1. Overview](../01-overview.md) | Next: [3. Conditions](03-where.md) | [Kotlin version of this page](../kotlin/02-getting-started.md)
