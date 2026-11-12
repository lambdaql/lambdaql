# 12. Spring Data JPA (Kotlin)

> [!NOTE]
> **Status: designed, not implemented yet.**
> This chapter describes the planned module `lambdaql-spring-data-jpa`.
> The wiring (repository fragment, transactions, Kotlin interface methods) was checked with a prototype on Spring Boot 3.4.2, Spring Data JPA 3.4.2 and kotlinc 2.3.21.
> Today only the analysis core and `where` + `toList()` work (tested on H2, MariaDB and Oracle).
> See the [Kotlin track README](README.md) for the full status.

[Kotlin track](README.md) | [Manual home](../README.md) | Previous: [11. Errors and languages](../11-errors-and-languages.md) | [Java version of this page](../java/12-spring-data-jpa.md)

---

## 12.1 What you get

With Spring Data JPA you write repository interfaces. lambdaQL adds one more interface, `LambdaQlExecutor<T>`.
Your repository extends it, and you write your own query methods as **interface methods with a body**, with lambdaQL inside.
You do not need a `...RepositoryImpl` class for these methods.

```kotlin
interface OrderRepository : JpaRepository<Order, Long>, LambdaQlExecutor<Order> {

    fun active(status: Int): List<Order> =
        findAll { it.status == status && it.active }

    fun recent(): List<Order> =
        query()
            .where { it.active }
            .orderBy { o, by -> by.desc(o.updateAt) }
            .limit(20)
            .toList()
}
```

```kotlin
@Service
class OrderService(private val orders: OrderRepository) {

    @Transactional(readOnly = true)
    fun activeOrders(): List<Order> = orders.active(1)
}
```

## 12.2 Modules

lambdaQL has two artifacts. The core has no Spring dependency, so you can use it with plain JPA too.

| Artifact | What it has | You need it when |
|---|---|---|
| `lambdaql-core` | The engine, `QueryBuilder`, select, DML, procedures | Always |
| `lambdaql-spring-data-jpa` | `LambdaQlExecutor<T>`, its Spring registration, Spring Boot auto-configuration (`QueryBuilder` bean) | You use Spring Data JPA or Spring Boot |

```kotlin
// build.gradle.kts (planned coordinates; nothing is published yet)
dependencies {
    implementation("org.lambdaql:lambdaql-spring-data-jpa:0.1.0")   // brings lambdaql-core too
    implementation("org.jetbrains.kotlin:kotlin-reflect")           // Spring Data needs it for Kotlin
}
```

Requirements:

| Item | Version |
|---|---|
| Spring Boot | 3.4 or newer, and Spring Boot 4 |
| Spring Data JPA | 3.4 or newer |
| Hibernate | 6.6 or newer (Spring Boot already brings it) |
| Kotlin | 2.2 or newer. For older Kotlin, see 12.6 |
| `kotlin-reflect` | Required by Spring Data for Kotlin |

Spring Data JPA 3.3 and older are **not supported**. The registration way that lambdaQL uses (see 12.6) exists from 3.4.

## 12.3 `LambdaQlExecutor<T>`

`T` is the entity of the repository.

| Method | What it does | Same as |
|---|---|---|
| `query()` | Starts a select on this repository's entity | `qb.from(Order::class.java)` |
| `from(Class<E>)` | Starts a select on another entity | `qb.from(Customer::class.java)` |
| `findAll(Where<T>)` | All rows that match | `query().where(w).toList()` |
| `findOne(Where<T>)` | One row or empty (`Optional`) | |
| `count(Where<T>)` | Number of rows that match | |
| `exists(Where<T>)` | `true` if at least one row matches | |
| `updateQuery()` | Starts an update on this entity | `qb.update(Order::class.java)` |
| `deleteQuery()` | Starts a delete on this entity | `qb.delete(Order::class.java)` |
| `insertQuery()` | Starts an insert on this entity | `qb.insert(Order::class.java)` |

The DML methods end with `Query` because `JpaRepository` already has `delete(T)` and similar methods.
`findAll { ... }` and `count { ... }` do not clash with `findAll(Example)` or `count()`, because the argument types are different.

All chapters of this manual work inside a repository. Some examples:

```kotlin
interface OrderRepository : JpaRepository<Order, Long>, LambdaQlExecutor<Order> {

    // 3. conditions, captured values
    fun findPaid(id: Long): Optional<Order> =
        findOne { it.id == id && it.status == 2 }

    // 5. subquery
    fun withBigItems(): List<Order> =
        query()
            .where { o, sub -> sub.from(Item::class.java)
                                  .where { i -> i.order?.id == o.id && i.qty > 10 }
                                  .exists() }
            .toList()

    // 6. select into a data class
    fun views(): List<OrderView> =
        query().select { OrderView(it.id, it.customer?.name, it.price) }.toList()

    // 7. update (needs a transaction, see 12.5)
    fun closeOld(before: LocalDateTime): Int =
        updateQuery()
            .set { it.status = 9 }
            .where { it.updateAt!! < before }
            .execute()
}
```

## 12.4 How it works

- **Interface methods with a body are run by Spring Data itself.** Spring Data does not try to make a query from them. It just calls the body. So lambdaQL does not need to change how Spring Data handles them.
- **`LambdaQlExecutor<T>` is a Spring Data "repository fragment".** lambdaQL registers the implementation in `META-INF/spring.factories`. Spring Data 3.4 and newer read this file and add the implementation to every repository that extends `LambdaQlExecutor<T>`. You do not write any configuration.
- **The entity type** comes from the lambda (for `findAll { ... }`) or from the repository (for `query()`).

It also works when your repository already has its own custom implementation:

```kotlin
interface OrderRepository : JpaRepository<Order, Long>,
                            LambdaQlExecutor<Order>,
                            OrderRepositoryCustom          // your own fragment

class OrderRepositoryCustomImpl : OrderRepositoryCustom { /* ... */ }   // still works
```

## 12.5 Transactions

lambdaQL uses the `EntityManager` that Spring gives to the current transaction.

| Case | What happens |
|---|---|
| Inside `@Transactional` | lambdaQL sees changes that are not flushed yet. Rollback and `readOnly` work as usual. |
| Select methods outside a transaction | They run in their own read-only transaction. |
| `execute()` (update, insert, delete) outside a transaction | Error. Put `@Transactional` on the calling service method. |

```kotlin
@Transactional
fun closeOldOrders(): Int = orders.closeOld(LocalDateTime.now().minusYears(1))
```

Bulk DML does not update entities that are already loaded in the same transaction. See [7. Update, insert, delete](07-dml.md).

## 12.6 Rules

| # | Rule |
|---|---|
| 1 | Your repository must extend `LambdaQlExecutor<T>` **directly** (in its own supertype list). |
| 2 | Do not put `@NoRepositoryBean` on an interface that extends `LambdaQlExecutor<T>` for this purpose. Spring Data then does not add the lambdaQL implementation. |
| 3 | Write your lambdaQL queries in **interface methods with a body** or in services. Methods without a body are still Spring Data query methods (`findByName` and so on). |
| 4 | **Kotlin 2.2 or newer:** a method with a body becomes a Java default method, and it works. **Kotlin older than 2.2:** add the compiler option `-Xjvm-default=all`. Without it, the method has no body in the interface, Spring Data tries to make a query from its name, and the application does not start (`No property ... found for type ...`). |
| 5 | Add `kotlin-reflect`. Without it Spring Data fails with `ClassNotFoundException: kotlin.reflect.jvm.ReflectJvmMapping`. |
| 6 | Without Spring Boot, the repository part still works (it is plain Spring Data). Only the `QueryBuilder` bean must be made by you (see [2. Getting started](02-getting-started.md)). |
| 7 | In tests with `@DataJpaTest`, import the lambdaQL auto-configuration (the exact class name will be listed here when the module is released). |

```kotlin
// build.gradle.kts, only for Kotlin older than 2.2
kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}
```

## 12.7 The old `DefaultMethodSupportJpaRepositoryFactoryBean`

Older lambdaQL code has `org.lambdaql.jpa.DefaultMethodSupportJpaRepositoryFactoryBean`. Its goal was to let interface methods with a body replace `...Impl` classes.
With Spring Data JPA 3.4 and Kotlin 2.2, they already work without it (see 12.4).
This class is **under review**: it may become the way to support Kotlin older than 2.2 without `-Xjvm-default=all`. It will move to `lambdaql-spring-data-jpa`; the details are decided before release.

---

[Kotlin track](README.md) | Previous: [11. Errors and languages](../11-errors-and-languages.md) | [Java version of this page](../java/12-spring-data-jpa.md)
