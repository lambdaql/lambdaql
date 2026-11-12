# 12. Spring Data JPA (Java)

> [!NOTE]
> **Status: designed, not implemented yet.**
> This chapter describes the planned module `lambdaql-spring-data-jpa`.
> The wiring (repository fragment, transactions) was checked with a prototype on Spring Boot 3.4.2 and Spring Data JPA 3.4.2.
> Today only the analysis core and `where` + `toList()` work (tested on H2, MariaDB and Oracle).
> See the [Java track README](README.md#status) for the full status.

[Java track](README.md) | [Manual home](../README.md) | Previous: [11. Errors and languages](../11-errors-and-languages.md) | [Kotlin version of this page](../kotlin/12-spring-data-jpa.md)

---

## 12.1 What you get

With Spring Data JPA you write repository interfaces. lambdaQL adds one more interface, `LambdaQlExecutor<T>`.
Your repository extends it, and you write your own query methods as **default methods** with lambdaQL inside.
You do not need a `...RepositoryImpl` class for these methods.

```java
public interface OrderRepository extends JpaRepository<Order, Long>, LambdaQlExecutor<Order> {

    default List<Order> active(int status) {
        return findAll(o -> o.getStatus() == status && o.isActive());
    }

    default List<Order> recent() {
        return query()
            .where(o -> o.isActive())
            .orderBy((o, by) -> by.desc(o.getUpdateAt()))
            .limit(20)
            .toList();
    }
}
```

```java
@Service
public class OrderService {
    private final OrderRepository orders;

    public OrderService(OrderRepository orders) {
        this.orders = orders;
    }

    @Transactional(readOnly = true)
    public List<Order> activeOrders() {
        return orders.active(1);
    }
}
```

## 12.2 Modules

lambdaQL has two artifacts. The core has no Spring dependency, so you can use it with plain JPA too.

| Artifact | What it has | You need it when |
|---|---|---|
| `lambdaql-core` | The engine, `QueryBuilder`, select, DML, procedures | Always |
| `lambdaql-spring-data-jpa` | `LambdaQlExecutor<T>`, its Spring registration, Spring Boot auto-configuration (`QueryBuilder` bean) | You use Spring Data JPA or Spring Boot |

```groovy
// build.gradle (planned coordinates; nothing is published yet)
dependencies {
    implementation 'org.lambdaql:lambdaql-spring-data-jpa:0.1.0'   // brings lambdaql-core too
}
```

Requirements:

| Item | Version |
|---|---|
| Spring Boot | 3.4 or newer, and Spring Boot 4 |
| Spring Data JPA | 3.4 or newer |
| Hibernate | 6.6 or newer (Spring Boot already brings it) |

Spring Data JPA 3.3 and older are **not supported**. The registration way that lambdaQL uses (see 12.6) exists from 3.4.

## 12.3 `LambdaQlExecutor<T>`

`T` is the entity of the repository.

| Method | What it does | Same as |
|---|---|---|
| `query()` | Starts a select on this repository's entity | `qb.from(Order.class)` |
| `from(Class<E>)` | Starts a select on another entity | `qb.from(Customer.class)` |
| `findAll(Where<T>)` | All rows that match | `query().where(w).toList()` |
| `findOne(Where<T>)` | One row or empty (`Optional`) | |
| `count(Where<T>)` | Number of rows that match | |
| `exists(Where<T>)` | `true` if at least one row matches | |
| `updateQuery()` | Starts an update on this entity | `qb.update(Order.class)` |
| `deleteQuery()` | Starts a delete on this entity | `qb.delete(Order.class)` |
| `insertQuery()` | Starts an insert on this entity | `qb.insert(Order.class)` |

The DML methods end with `Query` because `JpaRepository` already has `delete(T)` and similar methods.
`findAll(Where<T>)` and `count(Where<T>)` do not clash with `findAll(Example)` or `count()`, because the argument types are different.

All chapters of this manual work inside a repository. Some examples:

```java
public interface OrderRepository extends JpaRepository<Order, Long>, LambdaQlExecutor<Order> {

    // 3. conditions, captured values
    default Optional<Order> findPaid(long id) {
        return findOne(o -> o.getId() == id && o.getStatus() == 2);
    }

    // 5. subquery
    default List<Order> withBigItems() {
        return query()
            .where((o, sub) -> sub.from(Item.class)
                                  .where(i -> i.getOrder().getId() == o.getId() && i.getQty() > 10)
                                  .exists())
            .toList();
    }

    // 6. select into a record
    default List<OrderView> views() {
        return query().select(o -> new OrderView(o.getId(), o.getCustomer().getName(), o.getPrice())).toList();
    }

    // 7. update (needs a transaction, see 12.5)
    default int closeOld(LocalDateTime before) {
        return updateQuery()
            .set(o -> o.setStatus(9))
            .where(o -> o.getUpdateAt().isBefore(before))
            .execute();
    }
}
```

## 12.4 How it works

- **Default methods are run by Spring Data itself.** Spring Data does not try to make a query from a default method. It just calls the method body. So lambdaQL does not need to change how Spring Data handles default methods.
- **`LambdaQlExecutor<T>` is a Spring Data "repository fragment".** lambdaQL registers the implementation in `META-INF/spring.factories`. Spring Data 3.4 and newer read this file and add the implementation to every repository that extends `LambdaQlExecutor<T>`. You do not write any configuration.
- **The entity type** comes from the lambda (for `findAll(o -> ...)`) or from the repository (for `query()`).

It also works when your repository already has its own custom implementation:

```java
public interface OrderRepository extends JpaRepository<Order, Long>,
                                         LambdaQlExecutor<Order>,
                                         OrderRepositoryCustom {          // your own fragment
}
class OrderRepositoryCustomImpl implements OrderRepositoryCustom { ... }  // still works
```

## 12.5 Transactions

lambdaQL uses the `EntityManager` that Spring gives to the current transaction.

| Case | What happens |
|---|---|
| Inside `@Transactional` | lambdaQL sees changes that are not flushed yet. Rollback and `readOnly` work as usual. |
| Select methods outside a transaction | They run in their own read-only transaction. |
| `execute()` (update, insert, delete) outside a transaction | Error. Put `@Transactional` on the calling service method. |

```java
@Transactional
public int closeOldOrders() {
    return orders.closeOld(LocalDateTime.now().minusYears(1));
}
```

Bulk DML does not update entities that are already loaded in the same transaction. See [7. Update, insert, delete](07-dml.md).

## 12.6 Rules

| # | Rule |
|---|---|
| 1 | Your repository must extend `LambdaQlExecutor<T>` **directly** (in its own `extends` list). |
| 2 | Do not put `@NoRepositoryBean` on an interface that extends `LambdaQlExecutor<T>` for this purpose. Spring Data then does not add the lambdaQL implementation. |
| 3 | Write your lambdaQL queries in **default methods** or in services. Abstract methods are still Spring Data query methods (`findByName` and so on). |
| 4 | Without Spring Boot, the repository part still works (it is plain Spring Data). Only the `QueryBuilder` bean must be made by you (see [2. Getting started](02-getting-started.md)). |
| 5 | In tests with `@DataJpaTest`, import the lambdaQL auto-configuration (the exact class name will be listed here when the module is released). |

## 12.7 The old `DefaultMethodSupportJpaRepositoryFactoryBean`

Older lambdaQL code has `org.lambdaql.jpa.DefaultMethodSupportJpaRepositoryFactoryBean`. Its goal was to let default methods replace `...Impl` classes.
With Spring Data JPA 3.4, Java default methods already work without it (see 12.4).
This class is **under review**. It will move to `lambdaql-spring-data-jpa`; the details are decided before release.

---

[Java track](README.md) | Previous: [11. Errors and languages](../11-errors-and-languages.md) | [Kotlin version of this page](../kotlin/12-spring-data-jpa.md)
