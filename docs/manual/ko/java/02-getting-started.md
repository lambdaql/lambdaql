<a id="2-getting-started-java"></a>
# 2. 시작하기 (Java)

[Java 트랙](README.md) | [매뉴얼 홈](../README.md) | 이전: [1. 개요](../01-overview.md) | 다음: [3. 조건](03-where.md) | [이 페이지의 Kotlin 버전](../kotlin/02-getting-started.md)

공통 페이지: [1. 개요](../01-overview.md) · [11. 오류와 언어](../11-errors-and-languages.md)

---

<a id="requirements"></a>
## 요구 사항

| 항목 | 버전 | 참고 |
|---|---|---|
| Java | 21 | lambdaQL을 빌드하고 사용하는 데 필요합니다. |
| JPA 구현체 | Hibernate ORM 6.6 또는 7.4 | lambdaQL은 Hibernate HQL을 만듭니다. 6.6.53(Spring Boot 3.5.16)과 7.4.13(Spring Boot 4.1.2)으로 테스트했습니다. 다른 JPA 구현체는 지원하지 않습니다. [13. Spring Boot 3과 4](../13-spring-boot-versions.md)를 참고하십시오. |
| Jakarta Persistence | 3.1 또는 3.2 | Hibernate 6.6은 3.1, 7.4는 3.2를 씁니다. |
| 데이터베이스 | H2 2.3, MariaDB 10.11, MySQL 8.4, PostgreSQL 17, Oracle 23.26 (23ai) | 이 다섯 가지를 검증했습니다. 매뉴얼의 DB별 SQL은 H2, MariaDB, Oracle의 것입니다. 다른 데이터베이스도 동작할 수 있지만 테스트하지 않았습니다. |
| Spring Boot | 3.5 또는 4.1 | 선택 사항입니다. 프로젝트 테스트는 Spring Boot 3.5.16과 4.1.2를 사용합니다. |
| 컴파일러 | javac | ECJ(Eclipse 컴파일러)로 컴파일한 람다는 보장하지 않습니다. [9. Java 노트](09-java-notes.md#912-compiler-and-build)를 참고하십시오. |

<a id="build-from-source"></a>
## 소스에서 빌드

```bash
git clone https://github.com/codebit0/lambdaql.git
cd lambdaql
./gradlew build
```

- Gradle 래퍼는 9.9.1입니다. 저장소에 `gradle/gradle-daemon-jvm.properties`가 있으므로 Gradle 데몬은 Java 21을 사용합니다.
- 빌드에는 JDK 21과 **JDK 24 이상** 하나가 필요합니다. JDK 24 이상은 `java.lang.classfile` 리더(`src/main/java24`)를 컴파일하고 그 리더로 테스트를 한 번 더 돌립니다. 기본 버전은 25이며 `-Plql.classfile.jdk=24`처럼 바꿀 수 있습니다. Gradle이 JDK를 찾지 못하면 `~/.gradle/gradle.properties`의 `org.gradle.java.installations.paths`에 경로를 적으십시오.
- IntelliJ IDEA에서는 **Settings > Build Tools > Gradle > Gradle JVM**을 Java 21 JDK로 설정하십시오.
- 빌드의 주요 모듈은 `query`(아티팩트 `lambdaql-core`), `spring-data-jpa`(`lambdaql-spring-data-jpa`), `spring-boot-starter-boot3`·`spring-boot-starter-boot4`(`lambdaql-spring-boot-starter-boot3`·`-boot4`)입니다. `lambdaql-core`와 `lambdaql-spring-data-jpa`는 Spring Boot 3·4 공통 jar이고, 스타터만 Boot 버전별로 나뉩니다([13. Spring Boot 3과 4](../13-spring-boot-versions.md)). Spring 모듈은 [12. Spring Data JPA](12-spring-data-jpa.md)에서 설명합니다.

단위 테스트를 실행합니다.

```bash
./gradlew :query:test
```

다음 테스트는 lambdaQL이 현재 할 수 있는 일을 보여 줍니다. `SelectQueryTest`는 사용자 API를 사용하고, 나머지 테스트는 내부 클래스를 확인합니다.

| 테스트 파일 | 보여 주는 내용 |
|---|---|
| [`SelectQueryTest.java`](../../../../query/src/test/java/org/lambdaql/query/SelectQueryTest.java) | `qb.from(...).where(...)`: HQL, 파라미터, 행. 기본은 H2에서 실행하며, 다른 데이터베이스는 `-Dlql.db=mariadb`나 `-Dlql.db=oracle`을 추가 |
| [`LoadExamplesTest.java`](../../../../query/src/test/java/org/lambdaql/engine/load/LoadExamplesTest.java) | 람다 로딩: 캡처 값, `this`를 캡처하는 람다, 메서드 참조, `Serializable`이 아닌 람다 |
| [`ConditionDecodeTest.java`](../../../../query/src/test/java/org/lambdaql/engine/decode/ConditionDecodeTest.java) | 조건 디코딩: 비교, 캡처 값, `&&`, `\|\|`, `!` |
| [`IrExamplesTest.java`](../../../../query/src/test/java/org/lambdaql/engine/ir/IrExamplesTest.java) | 내부 조건 트리 |
| [`HostEvaluatorTest.java`](../../../../query/src/test/java/org/lambdaql/engine/sem/HostEvaluatorTest.java) | Java에서 캡처 값 계산, 숫자 변환 |

예를 들어 `ConditionDecodeTest`는 `o -> o.getStatus() == 1 || o.getStatus() == 2 || o.getStatus() == 3`이 세 개의 비교를 소스 순서대로 담은 하나의 `OR`로 디코딩되는지 확인합니다.

<a id="use-the-local-build-in-your-project"></a>
### 로컬 빌드를 프로젝트에서 사용하기

세 모듈의 jar를 로컬 Maven 저장소에 배포합니다.

```bash
./gradlew publishToMavenLocal
```

그다음 `lambdaql-core`를 프로젝트에 추가합니다.

Gradle(Groovy DSL):

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

Spring Boot를 사용하면 Boot 버전에 맞는 `lambdaql-spring-boot-starter-boot4`(`-boot3`)를, Spring Boot 없이 Spring Data JPA만 사용하면 `lambdaql-spring-data-jpa`를 대신 추가하십시오. 두 아티팩트는 `lambdaql-core`를 함께 가져옵니다([12.2](12-spring-data-jpa.md#122-modules) 참고).

> [!WARNING]
> 버전 `1.0.0`은 저장소의 `gradle.properties`에 정의된 값이며, 첫 릴리스 전에 바뀔 수 있습니다.
> lambdaQL이 Maven Central에 배포되면 이 페이지를 다시 확인하십시오.

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

Java 트랙의 모든 예제는 다음 엔티티를 사용합니다.

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

    // getter와 setter
}

@Entity
public class Customer {
    @Id @GeneratedValue
    private Long id;
    private String name;
    private Integer grade;

    // getter와 setter
}

@Entity
public class Item {
    @Id @GeneratedValue
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    private Order order;
    private Double price;
    private int qty;

    // getter와 setter
}
```

엔티티 규칙:

- lambdaQL은 **getter**(`o.getPrice()`, `o.isActive()`)를 읽습니다. JPA Metamodel로 getter를 JPA 속성에 매핑합니다. 필드를 직접 읽지 않습니다.
- 생성 코드가 필요 없습니다(Q-클래스도, 정적 메타모델도 없음).
- 대부분의 필드는 박싱 타입(`Long`, `Double`, `Integer`)입니다. 이는 `==`에 영향을 줍니다. [9. Java 노트](09-java-notes.md#92-boxing-and-unboxing)를 참고하십시오.

<a id="create-a-querybuilder"></a>
## QueryBuilder 만들기

`QueryBuilder`는 진입점입니다. JPA `EntityManagerFactory` 또는 `EntityManager`가 필요합니다.

```java
QueryBuilder qb = new QueryBuilder(entityManagerFactory);
```

`QueryBuilder`는 하나만 만들어 재사용하십시오. 쿼리마다 새로 만들지 마십시오.

<a id="with-plain-jpa"></a>
### 일반 JPA에서

```java
EntityManagerFactory emf = Persistence.createEntityManagerFactory("my-unit");
QueryBuilder qb = new QueryBuilder(emf);
```

<a id="with-spring-boot"></a>
### Spring Boot에서

`lambdaql-spring-boot-starter-boot4`(`-boot3`)를 사용하면 Spring Boot가 `QueryBuilder` 빈을 만듭니다([12.8](12-spring-data-jpa.md#128-the-querybuilder-bean) 참고).
스타터 없이 사용하면 빈을 직접 만드십시오. 자동 구성과 마찬가지로 Spring의 공유 `EntityManager`를 사용합니다.

```java
@Configuration
public class LambdaQlConfig {

    @Bean
    public QueryBuilder queryBuilder(EntityManagerFactory entityManagerFactory) {
        return new QueryBuilder(SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory));
    }
}
```

그다음 필요한 곳에 `QueryBuilder`를 주입합니다.

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

- 공유 `EntityManager`는 현재 트랜잭션에 연결됩니다. `@Transactional` 안에서 lambdaQL은 같은 트랜잭션으로 실행됩니다.
- Spring 애플리케이션에서는 `EntityManagerFactory` 생성자를 사용하지 마십시오. 이 생성자로 만든 빌더는 쿼리마다 자체 `EntityManager`를 열며, 이 `EntityManager`는 Spring 트랜잭션에 참여하지 않습니다.
- Spring Data 리포지토리 안에서 lambdaQL 쿼리를 작성하려면 [12. Spring Data JPA](12-spring-data-jpa.md)를 참고하십시오.

<a id="your-first-query"></a>
## 첫 쿼리

```java
List<Order> orders = qb.from(Order.class)
    .where(o -> o.getStatus() == 1 && o.getPrice() > 100)
    .toList();
// HQL: select e0 from Order e0 where e0.status = 1 and e0.price > 100.0
```

**SQL 블록 읽는 법.** `…`는 줄여 쓴 select 목록(엔티티의 모든 컬럼)이고, `?`는 바인드 파라미터입니다. 각 줄은 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)에서 나옵니다. 이 테스트는 각 쿼리를 Hibernate 7.4로 H2 2.3, MariaDB 10.11, Oracle 23.26에서 실행하며, Hibernate의 SQL이 바뀌면 실패합니다.

**데이터베이스별 SQL** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `to-hql`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>100.0
```

- `qb.from(Order.class)`는 `Order`에 대한 쿼리를 시작합니다.
- `where(...)`는 조건 람다 하나를 받습니다.
- `toList()`는 쿼리를 실행하고 `List<Order>`를 반환합니다.
- `toList()`는 모든 행을 반환합니다. 다른 결과 함수(`first()`, `one()`, `count()`, `exists()`, `toSet()`)는 [결과 가져오기](#getting-the-result)에 있습니다.

왜 `1`과 `100.0`은 바인드 파라미터가 아니라 HQL에 그대로 쓰일까요?
둘 다 **람다에 직접 쓴 상수**이기 때문입니다. lambdaQL은 상수(숫자, 문자열, boolean, `null`, enum 상수)를 HQL에 리터럴로 씁니다.
`price`는 `Double`이므로 javac는 `100`을 소수 `100.0`으로 컴파일합니다.
람다 밖에서 온 값은 다릅니다. 항상 바인드 파라미터가 됩니다(다음 절과 [3.3](03-where.md#33-basic-form) 참고).

<a id="use-values-from-outside-the-lambda"></a>
### 람다 밖의 값 사용하기

람다는 지역 변수, 필드, 메서드 파라미터를 사용할 수 있습니다. 이를 **캡처 값**이라고 합니다.
lambdaQL은 캡처 값을 바인드 파라미터로 바꿉니다. 쿼리가 실행될 때마다 새 값을 읽습니다.

```java
public List<Order> expensive(int status, double minPrice) {
    return qb.from(Order.class)
             .where(o -> o.getStatus() == status && o.getPrice() >= minPrice)
             .toList();
    // HQL: select e0 from Order e0 where e0.status = :p1 and e0.price >= :p2
}
```

**데이터베이스별 SQL** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `start-captured`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=? and o1_0.price>=?
```

<a id="many-where-calls"></a>
### 여러 번의 `where` 호출

`where`는 여러 번 호출할 수 있습니다. lambdaQL은 조건들을 `AND`로 결합합니다.

```java
qb.from(Order.class)
  .where(o -> o.isActive())
  .where(o -> o.getStatus() == 1 || o.getStatus() == 2)
  .toList();
// HQL: select e0 from Order e0
//      where e0.active = true and (e0.status = 1 or e0.status = 2)
```

**데이터베이스별 SQL** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `start-many-where`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.active=true and (o1_0.status=1 or o1_0.status=2)
```

조건을 단계별로 추가할 때 유용합니다.

```java
var query = qb.from(Order.class).where(o -> o.isActive());
if (minPrice != null) {
    query = query.where(o -> o.getPrice() >= minPrice);
}
List<Order> orders = query.toList();
```

빌더 변수에는 `var`를 사용하십시오. 빌더 타입의 이름은 아직 확정되지 않았습니다.

<a id="paths-to-other-entities"></a>
### 다른 엔티티로 가는 경로

`o.getCustomer().getName()` 같은 경로는 조인한 엔티티의 컬럼을 가리킵니다.
`where`에서 lambdaQL은 기본적으로 inner `join`을 추가합니다. 고객이 없는 주문은 결과에 **없습니다**.

```java
qb.from(Order.class)
  .where(o -> o.getCustomer().getName().equals(name))
  .toList();
// HQL: select e0 from Order e0 join e0.customer j1 where j1.name = :p1
```

**데이터베이스별 SQL** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `inner-join`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name=?
```

고객이 없는 주문을 찾으려면 연관을 비교하십시오. `o.getCustomer() == null`은 `e0.customer is null`이 됩니다(조인 없음).
null 가드(`o.getCustomer() == null || ...`)를 써도 고객 없는 주문은 남지 않습니다. 경로는 inner join이기 때문입니다(결정 D48).
그런 주문을 남기려면 `leftJoin`을 사용하십시오([6.8](06-select-group.md#68-explicit-joins)).
[3.10](03-where.md#310-association-paths)을 참고하십시오.

<a id="getting-the-result"></a>
## 결과 가져오기

쿼리를 실행하는 방법은 `toList()`만이 아닙니다. lambdaQL에는 결과 함수가 여섯 개 있습니다(결정 D38, D42).

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
이것은 의도된 것입니다(결정 D38). `limit(10).count()`는 "모든 행"으로도 "최대 10행"으로도 읽힐 수 있으므로 lambdaQL은 이를 허용하지 않습니다.

아래 예제는 [`ResultQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ResultQueryTest.java)에서 그대로 가져온 것입니다. 테스트는 이 예제를 H2, MariaDB, Oracle에서 실행하고, 결과를 Java로 계산한 같은 답과 비교합니다.
테스트는 [3.3](03-where.md#33-basic-form)의 테스트 엔티티를 사용합니다. `price`는 `double`이므로 정렬에 `nulls` 부분이 없고, `Order`에는 enum `kind`가 있습니다.

<a id="first"></a>
### `first()`

`orderBy`가 없으면 "첫 행"은 id가 가장 작은 행입니다(규칙 S9). 그래서 항상 같은 행을 얻습니다.
순서를 정하려면 먼저 `orderBy(...)`를 호출하십시오. 그러면 `first()`는 그 순서의 첫 행을 반환합니다. `offset`은 유지됩니다: `orderBy(...).offset(1).first()`는 두 번째 행을 반환합니다.
맞는 행이 없으면 결과는 `Optional.empty()`입니다.

[`ResultQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ResultQueryTest.java), 영역 `first`:

```java
Optional<Order> cheapest = qb.from(Order.class)
        .orderBy((o, by) -> by.asc(o.getPrice()))
        .first();
// without orderBy, "first" is the row with the smallest id
Optional<Order> firstActive = qb.from(Order.class).where(o -> o.isActive()).first();
Optional<Order> none = qb.from(Order.class).where(o -> o.getStatus() == 9).first();
```

**데이터베이스별 SQL** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `first`(`cheapest`):

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

**데이터베이스별 SQL** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `first#2`(`firstActive`):

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

[`ResultQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ResultQueryTest.java), 영역 `one`:

```java
long id = knownId;
Optional<Order> order = qb.from(Order.class).where(o -> o.getId() == id).one();
Optional<Order> missing = qb.from(Order.class).where(o -> o.getId() == -1L).one();
// two or more rows: an exception (use first() if any row is fine)
assertThrows(NonUniqueResultException.class, () -> qb.from(Order.class).where(o -> o.isActive()).one());
```

**데이터베이스별 SQL** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `one`:

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

[`ResultQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ResultQueryTest.java), 영역 `count`:

```java
SelectQuery<Order> active = qb.from(Order.class).where(o -> o.isActive());
long total = active.count();                                   // every matching row
List<Order> page = active.orderBy((o, by) -> by.desc(o.getPrice())).limit(2).toList();
```

**데이터베이스별 SQL** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `count`:

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

[`ResultQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ResultQueryTest.java), 영역 `exists`:

```java
boolean anyRush = qb.from(Order.class).where(o -> o.getKind() == Order.Kind.RUSH).exists();
boolean anyStatus9 = qb.from(Order.class).where(o -> o.getStatus() == 9).exists();
```

**데이터베이스별 SQL** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `exists`(`anyRush`):

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

`toSet()`은 `toList()`와 같은 방식으로 쿼리를 실행하고 `SequencedSet<T>`를 반환합니다(결정 D42).
반환 객체는 `LinkedHashSet`이므로 요소가 행 순서를 유지합니다. `orderBy`가 있으면 정렬 순서를 따릅니다.

[`ResultQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ResultQueryTest.java), 영역 `to-set`:

```java
java.util.SequencedSet<Order> byPrice = qb.from(Order.class)
        .orderBy((o, by) -> by.desc(o.getPrice()))
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

다음 테스트의 `Tag.equals`는 이름만 비교하고, 테이블에는 이름이 같은 행이 두 개 있습니다.

[`ResultQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ResultQueryTest.java), 영역 `to-set-check`:

```java
// Tag.equals compares only the name: two different rows would become one element, so toSet() refuses
assertThrows(IllegalStateException.class, () -> qb.from(Tag.class).toSet());
assertEquals(2, qb.from(Tag.class).toList().size());
```

<a id="things-to-know"></a>
## 알아 둘 점

| 규칙 | 예 |
|---|---|
| 람다나 메서드 참조를 사용하십시오. 익명 클래스는 거부됩니다. | `where(o -> o.isActive())`, `where(Order::isActive)` |
| 람다는 식 하나여야 합니다(조건, 정렬, 조회). | 람다 안의 반복문과 지역 변수는 거부됩니다. |
| 반드시 실행되어야 하는 코드를 람다에 넣지 마십시오. 람다는 실행되지 않습니다. | `System.out.println(...)`처럼 부수 효과가 있는 호출은 거부됩니다. |
| javac로 컴파일하십시오. | ECJ 출력은 보장하지 않습니다. |
| Java 21~23에서 실행하면 새 JDK로 컴파일한 클래스에 맞는 ASM 버전이 필요합니다. | lambdaQL은 Java 21~23에서 ASM 9.11.1을 사용합니다(Java 28까지의 클래스 파일을 읽음). 더 새로운 JDK로 컴파일한다면 ASM을 업데이트하십시오. Java 24 이상에서는 실행 중인 JDK의 `java.lang.classfile`로 읽으므로 ASM 버전과 관계가 없습니다. |

Java에 관한 더 자세한 내용은 [9. Java 노트](09-java-notes.md)에 있습니다.

<a id="next"></a>
## 다음

- [3. 조건(`where`)](03-where.md): 모든 연산자와 NULL 규칙.
- [4. 정렬과 페이징](04-order-paging.md)

---

[Java 트랙](README.md) | 이전: [1. 개요](../01-overview.md) | 다음: [3. 조건](03-where.md) | [이 페이지의 Kotlin 버전](../kotlin/02-getting-started.md)
