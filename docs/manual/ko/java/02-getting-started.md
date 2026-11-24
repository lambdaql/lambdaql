<a id="2-getting-started-java"></a>
# 2. 시작하기 (Java)

[Java 트랙](README.md) | [매뉴얼 홈](../README.md) | 이전: [1. 개요](../01-overview.md) | 다음: [3. 조건](03-where.md) | [이 페이지의 Kotlin 버전](../kotlin/02-getting-started.md)

공통 페이지: [1. 개요](../01-overview.md) · [11. 오류와 언어](../11-errors-and-languages.md)

---

<a id="requirements"></a>
## 요구 사항

| 항목 | 버전 | 참고 |
|---|---|---|
| Java | 21 이상 | lambdaQL을 빌드하고 실행하는 데 필요합니다. |
| JPA 구현체 | Hibernate ORM 6.6 또는 7.4 | lambdaQL은 Hibernate HQL을 만듭니다. 6.6.53(Spring Boot 3.5.16)과 7.4.13(Spring Boot 4.1.2)으로 테스트했습니다. 다른 JPA 구현체는 지원하지 않습니다. [13. Spring Boot 3과 4](../13-spring-boot-versions.md)를 참고하십시오. |
| Jakarta Persistence | 3.1 또는 3.2 | Hibernate 6.6은 3.1, 7.4는 3.2를 씁니다. |
| 데이터베이스 | H2 2.3, MariaDB 10.11, MySQL 8.4, PostgreSQL 17, Oracle 23.26 (23ai) | 해당 버전의 DB에서 테스트를 진행하였습니다. 다른 데이터베이스는 추가로 진행될 예정입니다. |
| Spring Boot | 3.5 또는 4.1 | 선택 사항입니다. 3.5.16과 4.1.2로 테스트했습니다. |
| 컴파일러 | javac | ECJ(Eclipse 컴파일러)로 컴파일한 람다 표현식은 보장하지 않습니다. [9. Java 노트](09-java-notes.md#912-compiler-and-build)를 참고하십시오. |

<a id="build-from-source"></a>
## 소스에서 빌드

```bash
git clone https://github.com/codebit0/lambdaql.git
cd lambdaql
./gradlew build
```

- Gradle 래퍼 버전은 9.9.1입니다.
- 빌드에는 JDK 두 개가 필요합니다. 설치되어 있지 않으면 Gradle이 자동으로 내려받습니다.
  - **JDK 21**: 컴파일과 기본 테스트에 씁니다. lambdaQL이 지원하는 가장 낮은 Java 버전에서 동작을 확인합니다.
  - **JDK 25**: Gradle을 실행하고(`gradle/gradle-daemon-jvm.properties`), Java 24 이상에서만 쓰는 코드(`src/main/java24`)를 컴파일합니다. 같은 테스트를 Java 25에서 한 번 더 실행합니다. 이 코드의 JDK는 `-Plql.classfile.jdk=24`처럼 다른 버전(24 이상)으로 바꿀 수 있습니다.
- 이미 설치한 JDK를 Gradle이 찾지 못하면 `~/.gradle/gradle.properties`의 `org.gradle.java.installations.paths`에 JDK 경로를 적으십시오.
- IntelliJ IDEA에서는 **Settings > Build Tools > Gradle > Gradle JVM**을 JDK 25로 설정하십시오.

빌드의 주요 모듈은 다음과 같습니다.

| 모듈 | 아티팩트 | 설명 |
|---|---|---|
| `query` | `lambdaql` | 쿼리 엔진. Spring Boot 3·4 공통 |
| `spring-data-jpa` | `lambdaql-spring-data-jpa` | Spring Data JPA 연동. Spring Boot 3·4 공통([12. Spring Data JPA](12-spring-data-jpa.md)) |
| `spring-boot-starter-boot3`, `spring-boot-starter-boot4` | `lambdaql-spring-boot-starter-boot3`, `-boot4` | Spring Boot 버전별 스타터([13. Spring Boot 3과 4](../13-spring-boot-versions.md)) |

단위 테스트를 실행합니다.

```bash
./gradlew :query:test
```

기본은 H2에서 실행합니다. 다른 데이터베이스에서 실행하려면 `-Dlql.db=mariadb`나 `-Dlql.db=oracle`을 추가하십시오.

<a id="use-the-local-build-in-your-project"></a>
### 로컬 빌드를 프로젝트에서 사용하기

세 모듈의 jar를 로컬 Maven 저장소에 배포합니다.

```bash
./gradlew publishToMavenLocal
```

그다음 `lambdaql`을 프로젝트에 추가합니다.

Gradle(Groovy DSL):

```groovy
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation 'io.github.lambdaql:lambdaql:0.0.1'
}
```

Maven:

```xml
<dependency>
  <groupId>io.github.lambdaql</groupId>
  <artifactId>lambdaql</artifactId>
  <version>0.0.1</version>
</dependency>
```

Spring Boot를 사용하면 Boot 버전에 맞는 `lambdaql-spring-boot-starter-boot4`(`-boot3`)를, Spring Boot 없이 Spring Data JPA만 사용하면 `lambdaql-spring-data-jpa`를 대신 추가하십시오. 두 아티팩트는 `lambdaql`을 함께 가져옵니다([12.2](12-spring-data-jpa.md#122-modules) 참고).

<a id="recommended-compiler-option--parameters"></a>
### 권장 컴파일러 옵션: `-parameters`

`-parameters` 옵션으로 컴파일하십시오. 그러면 lambdaQL이 프로시저 스텁 메서드의 파라미터 이름을 보고 순서가 바뀐 인자를 찾을 수 있습니다([8.4](08-procedures-functions.md#84-warning-argument-order) 참고).

Gradle(Groovy DSL):

```groovy
tasks.withType(JavaCompile).configureEach {
    options.compilerArgs << '-parameters'
}
```

Maven(`maven-compiler-plugin`):

```xml
<configuration>
  <parameters>true</parameters>
</configuration>
```

<a id="example-entities"></a>
## 예제 엔티티

Java 트랙의 예제는 다음 예제 엔티티를 사용합니다. 전체 코드는 [`Order.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/Order.java), [`Customer.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/Customer.java), [`Item.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/Item.java)에 있습니다.

| 엔티티 | 테이블 | 주요 속성 |
|---|---|---|
| `Order` | `orders` | `id`(`Long`, 자동 생성), `price`(`double`), `status`(`short`), `active`(`boolean`, getter `isActive()`), `description`(`String`), `kind`(enum `Order.Kind`: `NORMAL`, `RUSH`), `updateAt`(`LocalDateTime`), `customer`(`@ManyToOne` `Customer`), `total`(`Double`), `cnt`(`Integer`), `custName`(`String`), `address`(`@Embedded` `Address`: `city`, `zip`) |
| `Customer` | `customers` | `id`(`int`), `name`(`String`), `grade`(`Integer`), `orders`(`@OneToMany` `Order`) |
| `Item` | `items` | `id`(`Long`, 자동 생성), `order`(`@ManyToOne` `Order`), `price`(`Double`), `qty`(`int`) |

`Order`에는 `Item` 컬렉션이 없습니다. 주문의 항목은 `Item.order`로 찾습니다([5. 서브쿼리](05-subquery.md) 참고).

엔티티 규칙:

- lambdaQL은 **getter**(`o.getPrice()`, `o.isActive()`)를 읽습니다. JPA Metamodel로 getter를 JPA 속성에 매핑합니다. 필드를 직접 읽지 않습니다.
- 생성 코드가 필요 없습니다(Q-클래스도, 정적 메타모델도 없음).
- 기본형 필드(`double price`, `short status`)는 NULL이 될 수 없고, 박싱 타입 필드(`Double total`, `Integer cnt`)는 NULL이 될 수 있습니다. 이는 `==`와 NULL 처리에 영향을 줍니다. [9. Java 노트](09-java-notes.md#92-boxing-and-unboxing)를 참고하십시오.

<a id="create-a-querybuilder"></a>
## QueryBuilder 만들기

`QueryBuilder`는 진입점입니다. JPA `EntityManagerFactory` 또는 `EntityManager`가 필요합니다.

<!-- region: ManualJavaStartTest.java#create -->
```java
// one builder for the whole application; each query opens and closes its own EntityManager
QueryBuilder qb = new QueryBuilder(emf);
// or on an EntityManager that you manage: the queries run on it (and in its transaction)
QueryBuilder qbOnEm = new QueryBuilder(em);
```

`QueryBuilder`는 하나만 만들어 재사용하십시오. 쿼리마다 새로 만들지 마십시오.

<a id="with-plain-jpa"></a>
### 일반 JPA에서

`Persistence.createEntityManagerFactory("my-unit")`로 만든 `EntityManagerFactory`를 위 예제처럼 `new QueryBuilder(emf)`에 넘깁니다.

<a id="with-spring-boot"></a>
### Spring Boot에서

`lambdaql-spring-boot-starter-boot4`(`-boot3`)를 사용하면 Spring Boot가 `QueryBuilder` 빈을 만듭니다([12.8](12-spring-data-jpa.md#128-the-querybuilder-bean) 참고).
스타터 없이 사용하면 빈을 직접 만드십시오. 자동 구성과 마찬가지로 Spring의 공유 `EntityManager`를 사용합니다.
예제의 설정 클래스와 서비스 클래스는 `static` 중첩 클래스입니다. 애플리케이션에서는 보통 최상위 클래스로 둡니다.

<!-- region: ManualJavaStartTest.java#config -->
```java
@Configuration
public static class LambdaQlConfig {

    @Bean
    public QueryBuilder queryBuilder(EntityManagerFactory entityManagerFactory) {
        return new QueryBuilder(SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory));
    }
}
```

그다음 필요한 곳에 `QueryBuilder`를 주입합니다.

<!-- region: ManualJavaStartTest.java#service -->
```java
@Service
public static class OrderService {

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

- 공유 `EntityManager`는 현재 트랜잭션에 연결됩니다. `@Transactional` 안에서 lambdaQL은 같은 트랜잭션으로 실행됩니다.
- Spring 애플리케이션에서는 `EntityManagerFactory` 생성자를 사용하지 마십시오. 이 생성자로 만든 빌더는 쿼리마다 자체 `EntityManager`를 열며, 이 `EntityManager`는 Spring 트랜잭션에 참여하지 않습니다.
- Spring Data 리포지토리 안에서 lambdaQL 쿼리를 작성하려면 [12. Spring Data JPA](12-spring-data-jpa.md)를 참고하십시오.

예제 코드: [`ManualJavaStartTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaStartTest.java)(`create`, `config`, `service`).

<a id="your-first-query"></a>
## 첫 쿼리

<!-- region: ManualJavaStartTest.java#first-query -->
```java
SelectQuery<Order> query = qb.from(Order.class)
        .where(o -> o.getStatus() == 1 && o.getPrice() > 100);
assertEquals("select e0 from Order e0 where e0.status = 1 and e0.price > 100.0", query.toHql().hql());
List<Order> orders = query.toList();
```

**데이터베이스별 SQL**

<!-- sql: to-hql -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>100.0
```

- `qb.from(Order.class)`는 `Order`에 대한 쿼리를 시작합니다.
- `where(...)`는 조건 람다 표현식 하나를 받습니다.
- `toHql()`은 쿼리를 실행하지 않고 HQL과 파라미터를 돌려줍니다. `assertEquals`의 문자열이 lambdaQL이 만드는 HQL입니다.
- `toList()`는 쿼리를 실행하고 `List<Order>`를 반환합니다.
- `toList()`는 모든 행을 반환합니다. 다른 결과 함수(`first()`, `one()`, `count()`, `exists()`, `toSet()`)는 [결과 가져오기](#getting-the-result)에 있습니다.

왜 `1`과 `100.0`은 바인드 파라미터가 아니라 HQL에 그대로 쓰일까요?
둘 다 **람다 표현식에 직접 쓴 상수**이기 때문입니다. lambdaQL은 상수(숫자, 문자열, boolean, `null`, enum 상수)를 HQL에 리터럴로 씁니다.
`price`는 `double`이므로 javac는 `100`을 소수 `100.0`으로 컴파일합니다.
람다 표현식 밖에서 온 값은 다릅니다. 항상 바인드 파라미터가 됩니다(다음 절과 [3.3](03-where.md#33-basic-form) 참고).

<a id="use-values-from-outside-the-lambda"></a>
### 람다 표현식 밖의 값 사용하기

람다 표현식은 지역 변수, 필드, 메서드 파라미터를 사용할 수 있습니다. 이를 **캡처 값**이라고 합니다.
lambdaQL은 캡처 값을 바인드 파라미터로 바꿉니다. 쿼리가 실행될 때마다 새 값을 읽습니다.

<!-- region: ManualJavaStartTest.java#captured -->
```java
int status = 1;
double minPrice = 100;
SelectQuery<Order> query = qb.from(Order.class)
        .where(o -> o.getStatus() == status && o.getPrice() >= minPrice);
assertEquals("select e0 from Order e0 where e0.status = :p1 and e0.price >= :p2", query.toHql().hql());
List<Order> orders = query.toList();
```

**데이터베이스별 SQL**

<!-- sql: start-captured -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=? and o1_0.price>=?
```

<a id="many-where-calls"></a>
### 여러 번의 `where` 호출

`where`는 여러 번 호출할 수 있습니다. lambdaQL은 조건들을 `AND`로 결합합니다.

<!-- region: ManualJavaStartTest.java#many-where -->
```java
SelectQuery<Order> query = qb.from(Order.class)
        .where(o -> o.isActive())
        .where(o -> o.getStatus() == 1 || o.getStatus() == 2);
assertEquals("select e0 from Order e0 where e0.active = true and (e0.status = 1 or e0.status = 2)",
        query.toHql().hql());
```

**데이터베이스별 SQL**

<!-- sql: start-many-where -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.active=true and (o1_0.status=1 or o1_0.status=2)
```

조건을 단계별로 추가할 때 유용합니다.

<!-- region: ManualJavaStartTest.java#step-by-step -->
```java
Double minPrice = input;                         // for example from a search form; may be null
SelectQuery<Order> query = qb.from(Order.class).where(o -> o.isActive());
if (minPrice != null) {
    query = query.where(o -> o.getPrice() >= minPrice);
}
assertEquals("select e0 from Order e0 where e0.active = true and e0.price >= :p1", query.toHql().hql());
List<Order> orders = query.toList();
```

빌더 변수의 타입은 `SelectQuery<Order>`입니다(`var`도 사용할 수 있음). `where`는 같은 쿼리 객체에 조건을 더하고 그 객체를 반환합니다.

<a id="paths-to-other-entities"></a>
### 다른 엔티티로 가는 경로

`o.getCustomer().getName()` 같은 경로는 조인한 엔티티의 컬럼을 가리킵니다.
`where`에서 lambdaQL은 고객 테이블을 조인합니다. 고객이 없는 주문이 조건에 맞을 수 있으면 `left join`, 맞을 수 없으면 inner `join`입니다.
아래 조건은 고객이 있는 주문에서만 참이므로 inner `join`이고, 고객이 없는 주문은 결과에 **없습니다**.

<!-- region: SelectQueryTest.java#inner-join -->
```java
String name = "Kim";
RenderedQuery q = qb.from(Order.class).where(o -> o.getCustomer().getName().equals(name)).toHql();
assertEquals("select e0 from Order e0 join e0.customer j1 where j1.name = :p1", q.hql());
```

**데이터베이스별 SQL**

<!-- sql: inner-join -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name=?
```

고객이 없는 주문을 찾으려면 연관을 비교하십시오. `o.getCustomer() == null`은 `e0.customer is null`이 됩니다(조인 없음).
null 가드(`o.getCustomer() == null || ...`)를 쓰면 고객이 없는 주문도 조건에 맞으므로 `left join`이 되고, 그런 주문도 결과에 남습니다.
[3.10](03-where.md#310-association-paths)을 참고하십시오.

예제 코드: [`ManualJavaStartTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaStartTest.java)(`first-query`, `captured`, `many-where`, `step-by-step`), [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java)(`inner-join`).

<a id="getting-the-result"></a>
## 결과 가져오기

쿼리를 실행하는 방법은 `toList()`만이 아닙니다. lambdaQL에는 결과 함수가 여섯 개 있습니다.

| 함수 | 반환 타입 | 호출할 수 있는 곳 | 하는 일 |
|---|---|---|---|
| `toList()` | `List<T>` | `where` 뒤, 그리고 `orderBy` / `limit` / `offset` 뒤 | 모든 행(또는 한 페이지)을 반환합니다. |
| `first()` | `Optional<T>` | `where` 뒤, 그리고 `orderBy` / `limit` / `offset` 뒤 | 첫 행을 반환합니다. 한 행만 읽습니다. |
| `one()` | `Optional<T>` | `where` 뒤에서만 | 유일한 행을 반환합니다. 2개 이상의 행이 맞으면 `NonUniqueResultException`을 던집니다. 최대 2행을 읽습니다. |
| `count()` | `long` | `where` 뒤에서만 | 조건에 맞는 행의 수를 반환합니다. |
| `exists()` | `boolean` | `where` 뒤에서만 | 맞는 행이 있으면 `true`를 반환합니다. 한 행을 읽고, 엔티티는 로드하지 않습니다. |
| `toSet()` | `SequencedSet<T>` | `where` 뒤, 그리고 `orderBy` / `limit` / `offset` 뒤 | 모든 행(또는 한 페이지)을 행 순서를 유지하는 Set으로 반환합니다. Set의 크기가 행 수보다 작으면 `IllegalStateException`을 던집니다. |

"`where` 뒤"는 `qb.from(...)`과 `where(...)`로 얻은 쿼리(타입 `SelectQuery`)를 말합니다. 이 매뉴얼에서는 이를 **where 단계**라고 부릅니다. `where` 없이 `qb.from(...)` 바로 뒤에서도 이 함수들을 호출할 수 있습니다.

`orderBy`, `limit`, `offset` 뒤의 쿼리 타입은 `OrderedSelectQuery`입니다. 여기의 결과 함수는 `toList()`, `toSet()`, `first()`뿐입니다. `one()`, `count()`, `exists()`는 여기서 컴파일되지 않습니다.
이것은 의도된 것입니다. `limit(10).count()`는 "모든 행"으로도 "최대 10행"으로도 읽힐 수 있으므로 lambdaQL은 이를 허용하지 않습니다.

아래 예제는 [예제 엔티티](#example-entities)를 사용하며 H2, MariaDB, Oracle에서 같은 결과를 냅니다.
`price`는 `double`이므로 정렬에 `nulls` 부분이 없고, `Order`에는 enum `kind`가 있습니다.

<a id="first"></a>
### `first()`

`orderBy`가 없으면 "첫 행"은 id가 가장 작은 행입니다(규칙 S9). 그래서 항상 같은 행을 얻습니다.
순서를 정하려면 먼저 `orderBy(...)`를 호출하십시오. 그러면 `first()`는 그 순서의 첫 행을 반환합니다. `offset`은 유지됩니다: `orderBy(...).offset(1).first()`는 두 번째 행을 반환합니다.
맞는 행이 없으면 결과는 `Optional.empty()`입니다.
`asc(...)`는 클래스 `By`의 정렬 함수이며 `import static org.lambdaql.query.By.*;`로 가져옵니다([4.1](04-order-paging.md#41-one-lambda-for-the-whole-sort) 참고).

<!-- region: ResultQueryTest.java#first -->
```java
Optional<Order> cheapest = qb.from(Order.class)
        .orderBy(o -> asc(o.getPrice()))
        .first();
// without orderBy, "first" is the row with the smallest id
Optional<Order> firstActive = qb.from(Order.class).where(o -> o.isActive()).first();
Optional<Order> none = qb.from(Order.class).where(o -> o.getStatus() == 9).first();
```

**데이터베이스별 SQL**

<!-- sql: first -->
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

**데이터베이스별 SQL**

<!-- sql: first#2 -->
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

`first()`는 한 행을 읽으므로 페이징 쿼리입니다. lambdaQL은 `id`를 마지막 정렬 키로 추가하고([4.8](04-order-paging.md#tie-breaker-automatic-id) 참고), Hibernate는 데이터베이스마다 페이징을 씁니다: H2와 Oracle은 `fetch first ? rows only`, MariaDB는 `limit ?`입니다. `?`는 1입니다.

<a id="one"></a>
### `one()`

조건에 맞는 행이 최대 하나일 때 `one()`을 쓰십시오. 예를 들어 유일 키로 찾을 때입니다.
행이 없으면 `Optional.empty()`입니다. 2개 이상의 행은 `jakarta.persistence.NonUniqueResultException`을 던집니다. 맞는 행 아무거나 괜찮다면 `first()`를 쓰십시오.

<!-- region: ResultQueryTest.java#one -->
```java
long id = knownId;
Optional<Order> order = qb.from(Order.class).where(o -> o.getId() == id).one();
Optional<Order> missing = qb.from(Order.class).where(o -> o.getId() == -1L).one();
// two or more rows: an exception (use first() if any row is fine)
assertThrows(NonUniqueResultException.class, () -> qb.from(Order.class).where(o -> o.isActive()).one());
```

**데이터베이스별 SQL**

<!-- sql: one -->
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

`one()`은 데이터베이스에 최대 2행을 요청합니다. 두 번째 행이 있는지 알기에는 그것으로 충분합니다. 그래서 SQL에 `fetch first ? rows only`(H2, Oracle) 또는 `limit ?`(MariaDB)가 있고, `?`는 2입니다. 여기서는 순서가 중요하지 않으므로 `order by`가 없습니다.

<a id="count-and-a-paged-screen"></a>
### `count()`와 페이징 화면

`count()`는 조건에 맞는 행의 수를 반환합니다. where 단계에만 있으므로 `limit`과 `offset`이 결과를 바꿀 수 없습니다.

페이징 화면에서는 where 단계의 쿼리를 변수에 담아 두십시오. 그 변수에서 `count()`를 호출해 전체 수를 얻습니다. 그다음 같은 변수에서 `orderBy(...).limit(...).toList()`를 호출해 페이지를 얻습니다.

<!-- region: ResultQueryTest.java#count -->
```java
SelectQuery<Order> active = qb.from(Order.class).where(o -> o.isActive());
long total = active.count();                                   // every matching row
List<Order> page = active.orderBy(o -> desc(o.getPrice())).limit(2).toList();
```

**데이터베이스별 SQL**

<!-- sql: count -->
```sql
-- H2, MariaDB, Oracle (same)
select count(o1_0.id)
from orders o1_0
where o1_0.active=true
```

HQL은 `select count(e0) from Order e0 where e0.active = true`입니다. Hibernate는 `count(e0)`를 `select count(o1_0.id)`로 바꿉니다. 즉 id 열을 셉니다. count 쿼리에는 페이징이 없습니다.

<a id="exists"></a>
### `exists()`

`exists()`는 맞는 행이 하나라도 있으면 `true`를 반환합니다. `select 1`을 실행하고 최대 한 행만 읽습니다. 엔티티는 로드하지 않습니다.

<!-- region: ResultQueryTest.java#exists -->
```java
boolean anyRush = qb.from(Order.class).where(o -> o.getKind() == Order.Kind.RUSH).exists();
boolean anyStatus9 = qb.from(Order.class).where(o -> o.getStatus() == 9).exists();
```

**데이터베이스별 SQL**

<!-- sql: exists -->
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

HQL은 `select 1 from Order e0 where e0.kind = org.lambdaql.query.Order$Kind.RUSH`입니다. SQL은 상수 `1`만 선택하므로 열을 읽지 않고, 페이징(`?`는 1)이 첫 행 뒤에서 멈춥니다.

<a id="toset"></a>
### `toSet()`

`toSet()`은 `toList()`와 같은 방식으로 쿼리를 실행하고 `SequencedSet<T>`를 반환합니다.
반환 객체는 `LinkedHashSet`이므로 요소가 행 순서를 유지합니다. `orderBy`가 있으면 정렬 순서를 따릅니다.

<!-- region: ResultQueryTest.java#to-set -->
```java
java.util.SequencedSet<Order> byPrice = qb.from(Order.class)
        .orderBy(o -> desc(o.getPrice()))
        .toSet();
```

SQL은 `toList()`와 같습니다. lambdaQL은 `distinct`를 **추가하지 않습니다**.

- 엔티티 행에는 기본 키가 있습니다. 따라서 테이블 하나만 조회해도 같은 행이 두 번 나오지 않습니다.
- lambdaQL은 to-one 연관만 조인합니다. 컬렉션은 `exists` 서브쿼리로 바뀝니다. 따라서 조인으로 행이 중복되지 않습니다.
- 그러므로 `distinct`는 결과를 바꾸지 못하고 비용만 늘립니다. (데이터베이스 제약과는 무관합니다. Oracle 23.26은 LOB 열의 `distinct`도 허용합니다. 실측 결과입니다.)

값 조회(`select`)에서는 `distinct()`와 `toSet()`이 `select distinct`를 사용합니다([6.7](06-select-group.md#67-distinct-paging-and-results) 참고).

**크기 검사.** Set의 크기가 행 수보다 작으면 `toSet()`은 `IllegalStateException`을 던집니다. 다음 경우가 해당합니다.

- 엔티티의 `equals` / `hashCode`가 서로 다른 두 행을 같다고 판단하는 경우
- `@Id`가 유일하지 않은 경우(예: 뷰에 매핑한 엔티티)

검사가 없으면 행이 아무 표시 없이 유실됩니다. 이런 경우에는 `toList()`를 사용하십시오.

다음 예제의 `Tag.equals`는 이름만 비교하고, 테이블에는 이름이 같은 행이 두 개 있습니다.

<!-- region: ResultQueryTest.java#to-set-check -->
```java
// Tag.equals compares only the name: two different rows would become one element, so toSet() refuses
assertThrows(IllegalStateException.class, () -> qb.from(Tag.class).toSet());
assertEquals(2, qb.from(Tag.class).toList().size());
```

예제 코드: [`ResultQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ResultQueryTest.java)(`first`, `one`, `count`, `exists`, `to-set`, `to-set-check`).

<a id="things-to-know"></a>
## 알아 둘 점

| 규칙 | 예 |
|---|---|
| 람다 표현식을 사용하십시오. 익명 클래스는 거부되고, 메서드 참조는 정렬 키에만 쓸 수 있습니다. | `where(o -> o.isActive())`, `orderBy(Order::getPrice)` |
| 람다 표현식은 식 하나여야 합니다(조건, 정렬, 조회). | 람다 표현식 안의 반복문과 지역 변수는 거부됩니다. |
| 반드시 실행되어야 하는 코드를 람다 표현식에 넣지 마십시오. lambdaQL은 조건, 정렬, 조회 람다 표현식을 호출하지 않고 바이트 opcode를 분석합니다. | `System.out.println(...)`처럼 부수 효과가 있는 호출은 거부됩니다. |
| javac로 컴파일하십시오. | ECJ 출력은 보장하지 않습니다. |
| Java 21~23에서 실행하면 새 JDK로 컴파일한 클래스에 맞는 ASM 버전이 필요합니다. | lambdaQL은 Java 21~23에서 ASM 9.11.1을 사용합니다(Java 28까지의 클래스 파일을 읽음). 더 새로운 JDK로 컴파일한다면 ASM을 업데이트하십시오. Java 24 이상에서는 실행 중인 JDK의 `java.lang.classfile`로 읽으므로 ASM 버전과 관계가 없습니다. |

Java에 관한 더 자세한 내용은 [9. Java 노트](09-java-notes.md)에 있습니다.

<a id="next"></a>
## 다음

- [3. 조건(`where`)](03-where.md): 모든 연산자와 NULL 규칙.
- [4. 정렬과 페이징](04-order-paging.md)

---

[Java 트랙](README.md) | 이전: [1. 개요](../01-overview.md) | 다음: [3. 조건](03-where.md) | [이 페이지의 Kotlin 버전](../kotlin/02-getting-started.md)
