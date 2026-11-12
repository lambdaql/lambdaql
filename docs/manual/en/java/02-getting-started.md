# 2. Getting started (Java)

> [!NOTE]
> **Status:** You can build lambdaQL from source and run its tests today.
> `where` conditions and `toList()` work end to end. They are tested on H2, MariaDB and Oracle.
> The core of sorting and paging (`orderBy`, `limit`, `offset`) works too.
> Subqueries, `select`, update/insert/delete, procedures and the Spring module are **designed, not implemented yet**.
> No artifact is published yet. The artifact name below is temporary.

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
./gradlew :query:build
```

- The Gradle wrapper (8.10) runs only on **JDK 23 or lower**. Use JDK 21.
- The repository has `gradle/gradle-daemon-jvm.properties`, so the Gradle daemon uses Java 21.
- In IntelliJ IDEA, set **Settings > Build Tools > Gradle > Gradle JVM** to a Java 21 JDK.

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

Publish the jar to your local Maven repository:

```bash
./gradlew :query:publishToMavenLocal
```

Then add it to your project.

Gradle (Groovy DSL):

```groovy
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation 'org.lambdaql:lambadaql-query:1.0.0'
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

`QueryBuilder` is the entry point. It needs a JPA `EntityManagerFactory`.

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

Spring Boot already creates an `EntityManagerFactory`. Inject it and make a `QueryBuilder` bean.

```java
@Configuration
public class LambdaQlConfig {

    @Bean
    public QueryBuilder queryBuilder(EntityManagerFactory entityManagerFactory) {
        return new QueryBuilder(entityManagerFactory);
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

- Until the module `lambdaql-spring-data-jpa` is released, you create the bean yourself. That module will create it for you.
- Inside a Spring transaction, lambdaQL uses the transaction's shared `EntityManager`.
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
- `toList()` is the only result method that is decided now. Other result methods are planned.

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
If a null guard keeps orders without a customer (`o.getCustomer() == null || ...`), lambdaQL uses a `left join`.
See [3.10](03-where.md#310-association-paths).

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
