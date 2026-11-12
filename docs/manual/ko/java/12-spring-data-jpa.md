# 12. Spring Data JPA (Java)

> [!NOTE]
> **상태: 설계됨, 아직 구현되지 않음.**
> 이 장은 계획된 모듈 `lambdaql-spring-data-jpa`를 설명합니다.
> 연결 방식(리포지토리 프래그먼트, 트랜잭션)은 Spring Boot 3.4.2와 Spring Data JPA 3.4.2에서 프로토타입으로 확인했습니다.
> 현재는 분석 코어와 `where` + `toList()`만 동작합니다(H2, MariaDB, Oracle에서 테스트함).
> 전체 상태는 [Java 트랙 README](README.md#status)를 참고하십시오.

[Java 트랙](README.md) | [매뉴얼 홈](../README.md) | 이전: [11. 오류와 언어](../11-errors-and-languages.md) | [이 페이지의 Kotlin 버전](../kotlin/12-spring-data-jpa.md)

---

<a id="121-what-you-get"></a>
## 12.1 제공하는 것

Spring Data JPA에서는 리포지토리 인터페이스를 작성합니다. lambdaQL은 인터페이스 하나, `LambdaQlExecutor<T>`를 더합니다.
리포지토리가 이를 상속하고, 직접 만드는 쿼리 메서드는 lambdaQL을 사용하는 **default 메서드**로 작성합니다.
이 메서드들을 위한 `...RepositoryImpl` 클래스는 필요 없습니다.

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

<a id="122-modules"></a>
## 12.2 모듈

lambdaQL에는 아티팩트가 두 개 있습니다. 코어는 Spring에 의존하지 않으므로 일반 JPA와도 함께 사용할 수 있습니다.

| 아티팩트 | 포함된 것 | 필요한 경우 |
|---|---|---|
| `lambdaql-core` | 엔진, `QueryBuilder`, 조회, DML, 프로시저 | 항상 |
| `lambdaql-spring-data-jpa` | `LambdaQlExecutor<T>`, 그 Spring 등록, Spring Boot 자동 구성(`QueryBuilder` 빈) | Spring Data JPA나 Spring Boot를 사용할 때 |

```groovy
// build.gradle (계획된 좌표. 아직 배포된 것은 없음)
dependencies {
    implementation 'org.lambdaql:lambdaql-spring-data-jpa:0.1.0'   // lambdaql-core도 함께 가져옴
}
```

요구 사항:

| 항목 | 버전 |
|---|---|
| Spring Boot | 3.4 이상, 그리고 Spring Boot 4 |
| Spring Data JPA | 3.4 이상 |
| Hibernate | 6.6 이상(Spring Boot가 이미 가져옴) |

Spring Data JPA 3.3 이하는 **지원하지 않습니다**. lambdaQL이 사용하는 등록 방식(12.6 참고)은 3.4부터 있습니다.

## 12.3 `LambdaQlExecutor<T>`

`T`는 리포지토리의 엔티티입니다.

| 메서드 | 하는 일 | 같은 의미 |
|---|---|---|
| `query()` | 이 리포지토리의 엔티티에 대한 조회를 시작 | `qb.from(Order.class)` |
| `from(Class<E>)` | 다른 엔티티에 대한 조회를 시작 | `qb.from(Customer.class)` |
| `findAll(Where<T>)` | 일치하는 모든 행 | `query().where(w).toList()` |
| `findOne(Where<T>)` | 행 하나 또는 빈 값(`Optional`) | |
| `count(Where<T>)` | 일치하는 행의 수 | |
| `exists(Where<T>)` | 일치하는 행이 하나 이상이면 `true` | |
| `updateQuery()` | 이 엔티티에 대한 수정을 시작 | `qb.update(Order.class)` |
| `deleteQuery()` | 이 엔티티에 대한 삭제를 시작 | `qb.delete(Order.class)` |
| `insertQuery()` | 이 엔티티에 대한 삽입을 시작 | `qb.insert(Order.class)` |

`JpaRepository`에 이미 `delete(T)` 같은 메서드가 있으므로 DML 메서드 이름은 `Query`로 끝납니다.
`findAll(Where<T>)`와 `count(Where<T>)`는 인자 타입이 다르므로 `findAll(Example)`이나 `count()`와 충돌하지 않습니다.

이 매뉴얼의 모든 장은 리포지토리 안에서도 동작합니다. 몇 가지 예:

```java
public interface OrderRepository extends JpaRepository<Order, Long>, LambdaQlExecutor<Order> {

    // 3. 조건, 캡처 값
    default Optional<Order> findPaid(long id) {
        return findOne(o -> o.getId() == id && o.getStatus() == 2);
    }

    // 5. 서브쿼리
    default List<Order> withBigItems() {
        return query()
            .where((o, sub) -> sub.from(Item.class)
                                  .where(i -> i.getOrder().getId() == o.getId() && i.getQty() > 10)
                                  .exists())
            .toList();
    }

    // 6. 레코드로 조회
    default List<OrderView> views() {
        return query().select(o -> new OrderView(o.getId(), o.getCustomer().getName(), o.getPrice())).toList();
    }

    // 7. 수정(트랜잭션이 필요함, 12.5 참고)
    default int closeOld(LocalDateTime before) {
        return updateQuery()
            .set(o -> o.setStatus(9))
            .where(o -> o.getUpdateAt().isBefore(before))
            .execute();
    }
}
```

<a id="124-how-it-works"></a>
## 12.4 동작 방식

- **default 메서드는 Spring Data가 직접 실행합니다.** Spring Data는 default 메서드에서 쿼리를 만들려고 하지 않습니다. 메서드 본문을 호출할 뿐입니다. 그래서 lambdaQL은 Spring Data가 default 메서드를 다루는 방식을 바꿀 필요가 없습니다.
- **`LambdaQlExecutor<T>`는 Spring Data "리포지토리 프래그먼트"입니다.** lambdaQL은 구현을 `META-INF/spring.factories`에 등록합니다. Spring Data 3.4 이상은 이 파일을 읽고 `LambdaQlExecutor<T>`를 상속하는 모든 리포지토리에 구현을 추가합니다. 구성을 따로 작성할 필요가 없습니다.
- **엔티티 타입**은 람다(`findAll(o -> ...)`의 경우)나 리포지토리(`query()`의 경우)에서 옵니다.

리포지토리에 이미 직접 만든 사용자 정의 구현이 있어도 동작합니다.

```java
public interface OrderRepository extends JpaRepository<Order, Long>,
                                         LambdaQlExecutor<Order>,
                                         OrderRepositoryCustom {          // 직접 만든 프래그먼트
}
class OrderRepositoryCustomImpl implements OrderRepositoryCustom { ... }  // 그대로 동작함
```

<a id="125-transactions"></a>
## 12.5 트랜잭션

lambdaQL은 Spring이 현재 트랜잭션에 제공하는 `EntityManager`를 사용합니다.

| 경우 | 일어나는 일 |
|---|---|
| `@Transactional` 안 | lambdaQL은 아직 flush되지 않은 변경 사항도 봅니다. 롤백과 `readOnly`는 평소처럼 동작합니다. |
| 트랜잭션 밖의 조회 메서드 | 자체 읽기 전용 트랜잭션에서 실행됩니다. |
| 트랜잭션 밖의 `execute()`(수정, 삽입, 삭제) | 오류. 호출하는 서비스 메서드에 `@Transactional`을 붙이십시오. |

```java
@Transactional
public int closeOldOrders() {
    return orders.closeOld(LocalDateTime.now().minusYears(1));
}
```

벌크 DML은 같은 트랜잭션에서 이미 로드된 엔티티를 갱신하지 않습니다. [7. 수정, 삽입, 삭제](07-dml.md)를 참고하십시오.

<a id="126-rules"></a>
## 12.6 규칙

| # | 규칙 |
|---|---|
| 1 | 리포지토리는 `LambdaQlExecutor<T>`를 **직접**(자신의 `extends` 목록에서) 상속해야 합니다. |
| 2 | 이 목적으로 `LambdaQlExecutor<T>`를 상속하는 인터페이스에 `@NoRepositoryBean`을 붙이지 마십시오. 그러면 Spring Data가 lambdaQL 구현을 추가하지 않습니다. |
| 3 | lambdaQL 쿼리는 **default 메서드**나 서비스에 작성하십시오. 추상 메서드는 여전히 Spring Data 쿼리 메서드(`findByName` 등)입니다. |
| 4 | Spring Boot 없이도 리포지토리 부분은 동작합니다(일반 Spring Data이므로). `QueryBuilder` 빈만 직접 만들어야 합니다([2. 시작하기](02-getting-started.md) 참고). |
| 5 | `@DataJpaTest`를 쓰는 테스트에서는 lambdaQL 자동 구성을 import하십시오(정확한 클래스 이름은 모듈이 릴리스되면 여기에 적습니다). |

<a id="127-the-old-defaultmethodsupportjparepositoryfactorybean"></a>
## 12.7 예전 `DefaultMethodSupportJpaRepositoryFactoryBean`

예전 lambdaQL 코드에는 `org.lambdaql.jpa.DefaultMethodSupportJpaRepositoryFactoryBean`이 있습니다. default 메서드로 `...Impl` 클래스를 대신하는 것이 목적이었습니다.
Spring Data JPA 3.4에서는 이 클래스 없이도 Java default 메서드가 이미 동작합니다(12.4 참고).
이 클래스는 **검토 중**입니다. `lambdaql-spring-data-jpa`로 옮겨질 예정이며, 세부 사항은 릴리스 전에 정합니다.

---

[Java 트랙](README.md) | 이전: [11. 오류와 언어](../11-errors-and-languages.md) | [이 페이지의 Kotlin 버전](../kotlin/12-spring-data-jpa.md)
