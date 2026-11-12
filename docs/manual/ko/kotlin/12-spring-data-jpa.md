# 12. Spring Data JPA (Kotlin)

> [!NOTE]
> **상태:** 설계됨, **아직 구현되지 않았습니다**.
> 이 장은 계획된 모듈 `lambdaql-spring-data-jpa`를 설명합니다.
> 연결 방식(리포지토리 프래그먼트, 트랜잭션, Kotlin 인터페이스 메서드)은 Spring Boot 3.4.2, Spring Data JPA 3.4.2, kotlinc 2.3.21에서 프로토타입으로 확인했습니다.
> 현재는 분석 코어와 `where` + `toList()`만 동작합니다(H2, MariaDB, Oracle에서 테스트함).
> 전체 상태는 [Kotlin 트랙 README](README.md)를 참고하십시오.

[Kotlin 트랙](README.md) | [매뉴얼 홈](../README.md) | 이전: [11. 오류와 언어](../11-errors-and-languages.md) | [이 페이지의 Java 버전](../java/12-spring-data-jpa.md)

---

<a id="121-what-you-get"></a>
## 12.1 얻을 수 있는 것

Spring Data JPA에서는 리포지토리 인터페이스를 작성합니다. lambdaQL은 인터페이스 `LambdaQlExecutor<T>`를 하나 더 추가합니다.
리포지토리가 이 인터페이스를 상속하고, 직접 만드는 쿼리 메서드는 lambdaQL을 사용하는 **본문이 있는 인터페이스 메서드**로 작성합니다.
이 메서드들을 위한 `...RepositoryImpl` 클래스는 필요 없습니다.

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

<a id="122-modules"></a>
## 12.2 모듈

lambdaQL에는 아티팩트가 두 개 있습니다. 코어는 Spring에 의존하지 않으므로 일반 JPA에서도 사용할 수 있습니다.

| 아티팩트 | 들어 있는 것 | 필요한 경우 |
|---|---|---|
| `lambdaql-core` | 엔진, `QueryBuilder`, 조회, DML, 프로시저 | 항상 |
| `lambdaql-spring-data-jpa` | `LambdaQlExecutor<T>`, 그 Spring 등록, Spring Boot 자동 설정(`QueryBuilder` 빈) | Spring Data JPA나 Spring Boot를 사용할 때 |

```kotlin
// build.gradle.kts (계획된 좌표이며, 아직 배포된 것은 없음)
dependencies {
    implementation("org.lambdaql:lambdaql-spring-data-jpa:0.1.0")   // lambdaql-core도 함께 가져옴
    implementation("org.jetbrains.kotlin:kotlin-reflect")           // Spring Data가 Kotlin에 필요로 함
}
```

요구 사항:

| 항목 | 버전 |
|---|---|
| Spring Boot | 3.4 이상, Spring Boot 4 |
| Spring Data JPA | 3.4 이상 |
| Hibernate | 6.6 이상(Spring Boot가 이미 가져옴) |
| Kotlin | 2.2 이상. 이전 Kotlin은 12.6 참고 |
| `kotlin-reflect` | Spring Data가 Kotlin에 필요로 함 |

Spring Data JPA 3.3 이하는 **지원하지 않습니다**. lambdaQL이 사용하는 등록 방식(12.6 참고)은 3.4부터 있습니다.

## 12.3 `LambdaQlExecutor<T>`

`T`는 리포지토리의 엔티티입니다.

| 메서드 | 하는 일 | 같은 것 |
|---|---|---|
| `query()` | 이 리포지토리의 엔티티에 대한 조회를 시작 | `qb.from(Order::class.java)` |
| `from(Class<E>)` | 다른 엔티티에 대한 조회를 시작 | `qb.from(Customer::class.java)` |
| `findAll(Where<T>)` | 일치하는 모든 행 | `query().where(w).toList()` |
| `findOne(Where<T>)` | 한 행 또는 빈 값(`Optional`) | |
| `count(Where<T>)` | 일치하는 행의 수 | |
| `exists(Where<T>)` | 일치하는 행이 하나라도 있으면 `true` | |
| `updateQuery()` | 이 엔티티에 대한 수정을 시작 | `qb.update(Order::class.java)` |
| `deleteQuery()` | 이 엔티티에 대한 삭제를 시작 | `qb.delete(Order::class.java)` |
| `insertQuery()` | 이 엔티티에 대한 삽입을 시작 | `qb.insert(Order::class.java)` |

`JpaRepository`에 이미 `delete(T)` 같은 메서드가 있으므로 DML 메서드는 `Query`로 끝납니다.
`findAll { ... }`과 `count { ... }`는 인수 타입이 다르므로 `findAll(Example)`이나 `count()`와 충돌하지 않습니다.

이 매뉴얼의 모든 장은 리포지토리 안에서도 동작합니다. 몇 가지 예:

```kotlin
interface OrderRepository : JpaRepository<Order, Long>, LambdaQlExecutor<Order> {

    // 3. 조건, 캡처 값
    fun findPaid(id: Long): Optional<Order> =
        findOne { it.id == id && it.status == 2 }

    // 5. 서브쿼리
    fun withBigItems(): List<Order> =
        query()
            .where { o, sub -> sub.from(Item::class.java)
                                  .where { i -> i.order?.id == o.id && i.qty > 10 }
                                  .exists() }
            .toList()

    // 6. data class로 조회
    fun views(): List<OrderView> =
        query().select { OrderView(it.id, it.customer?.name, it.price) }.toList()

    // 7. 수정(트랜잭션이 필요함, 12.5 참고)
    fun closeOld(before: LocalDateTime): Int =
        updateQuery()
            .set { it.status = 9 }
            .where { it.updateAt!! < before }
            .execute()
}
```

<a id="124-how-it-works"></a>
## 12.4 동작 방식

- **본문이 있는 인터페이스 메서드는 Spring Data가 직접 실행합니다.** Spring Data는 이런 메서드로 쿼리를 만들려고 하지 않습니다. 그냥 본문을 호출합니다. 그래서 lambdaQL은 Spring Data가 이 메서드들을 처리하는 방식을 바꿀 필요가 없습니다.
- **`LambdaQlExecutor<T>`는 Spring Data의 "리포지토리 프래그먼트"입니다.** lambdaQL은 구현체를 `META-INF/spring.factories`에 등록합니다. Spring Data 3.4 이상은 이 파일을 읽고, `LambdaQlExecutor<T>`를 상속하는 모든 리포지토리에 구현체를 추가합니다. 설정은 전혀 작성하지 않아도 됩니다.
- **엔티티 타입**은 람다(`findAll { ... }`의 경우)나 리포지토리(`query()`의 경우)에서 가져옵니다.

리포지토리에 이미 자체 커스텀 구현이 있어도 동작합니다.

```kotlin
interface OrderRepository : JpaRepository<Order, Long>,
                            LambdaQlExecutor<Order>,
                            OrderRepositoryCustom          // 직접 만든 프래그먼트

class OrderRepositoryCustomImpl : OrderRepositoryCustom { /* ... */ }   // 그대로 동작함
```

<a id="125-transactions"></a>
## 12.5 트랜잭션

lambdaQL은 Spring이 현재 트랜잭션에 제공하는 `EntityManager`를 사용합니다.

| 경우 | 일어나는 일 |
|---|---|
| `@Transactional` 안 | lambdaQL은 아직 flush되지 않은 변경도 봅니다. 롤백과 `readOnly`는 평소처럼 동작합니다. |
| 트랜잭션 밖의 조회 메서드 | 자체 읽기 전용 트랜잭션에서 실행됩니다. |
| 트랜잭션 밖의 `execute()`(수정, 삽입, 삭제) | 오류. 호출하는 서비스 메서드에 `@Transactional`을 붙이십시오. |

```kotlin
@Transactional
fun closeOldOrders(): Int = orders.closeOld(LocalDateTime.now().minusYears(1))
```

벌크 DML은 같은 트랜잭션에서 이미 로드된 엔티티를 갱신하지 않습니다. [7. 수정, 삽입, 삭제](07-dml.md)를 참고하십시오.

<a id="126-rules"></a>
## 12.6 규칙

| # | 규칙 |
|---|---|
| 1 | 리포지토리는 `LambdaQlExecutor<T>`를 **직접**(자신의 상위 타입 목록에서) 상속해야 합니다. |
| 2 | 이 목적으로 `LambdaQlExecutor<T>`를 상속하는 인터페이스에 `@NoRepositoryBean`을 붙이지 마십시오. 그러면 Spring Data가 lambdaQL 구현체를 추가하지 않습니다. |
| 3 | lambdaQL 쿼리는 **본문이 있는 인터페이스 메서드**나 서비스에 작성하십시오. 본문이 없는 메서드는 여전히 Spring Data 쿼리 메서드(`findByName` 등)입니다. |
| 4 | **Kotlin 2.2 이상:** 본문이 있는 메서드는 Java default 메서드가 되며 동작합니다. **Kotlin 2.2 미만:** 컴파일러 옵션 `-Xjvm-default=all`을 추가하십시오. 이 옵션이 없으면 인터페이스의 메서드에 본문이 없게 되고, Spring Data가 메서드 이름으로 쿼리를 만들려고 하여 애플리케이션이 시작되지 않습니다(`No property ... found for type ...`). |
| 5 | `kotlin-reflect`를 추가하십시오. 없으면 Spring Data가 `ClassNotFoundException: kotlin.reflect.jvm.ReflectJvmMapping`으로 실패합니다. |
| 6 | Spring Boot가 없어도 리포지토리 부분은 동작합니다(일반 Spring Data입니다). `QueryBuilder` 빈만 직접 만들어야 합니다([2. 시작하기](02-getting-started.md) 참고). |
| 7 | `@DataJpaTest`를 사용하는 테스트에서는 lambdaQL 자동 설정을 import하십시오(정확한 클래스 이름은 모듈이 출시되면 여기에 적습니다). |

```kotlin
// build.gradle.kts, Kotlin 2.2 미만에서만
kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}
```

<a id="127-the-old-defaultmethodsupportjparepositoryfactorybean"></a>
## 12.7 이전의 `DefaultMethodSupportJpaRepositoryFactoryBean`

이전 lambdaQL 코드에는 `org.lambdaql.jpa.DefaultMethodSupportJpaRepositoryFactoryBean`이 있습니다. 그 목적은 본문이 있는 인터페이스 메서드로 `...Impl` 클래스를 대체하는 것이었습니다.
Spring Data JPA 3.4와 Kotlin 2.2에서는 이 클래스 없이도 이미 동작합니다(12.4 참고).
이 클래스는 **검토 중**입니다. `-Xjvm-default=all` 없이 Kotlin 2.2 미만을 지원하는 방법이 될 수도 있습니다. `lambdaql-spring-data-jpa`로 옮겨질 예정이며, 세부 사항은 출시 전에 정합니다.

---

[Kotlin 트랙](README.md) | 이전: [11. 오류와 언어](../11-errors-and-languages.md) | [이 페이지의 Java 버전](../java/12-spring-data-jpa.md)
