<a id="2-getting-started-java"></a>
# 2. 시작하기 (Java)

> [!NOTE]
> **상태:** 현재 lambdaQL을 소스에서 빌드하고 테스트를 실행할 수 있습니다.
> `where` 조건과 `toList()`는 처음부터 끝까지 동작합니다. H2, MariaDB, Oracle에서 테스트했습니다.
> 정렬과 페이징(`orderBy`, `limit`, `offset`)의 핵심 기능도 동작합니다.
> 서브쿼리, `select`, update/insert/delete, 프로시저, Spring 모듈은 **설계되었지만 아직 구현되지 않았습니다**.
> 아직 배포된 아티팩트는 없습니다. 아래의 아티팩트 이름은 임시입니다.

[Java 트랙](README.md) | [매뉴얼 홈](../README.md) | 이전: [1. 개요](../01-overview.md) | 다음: [3. 조건](03-where.md) | [이 페이지의 Kotlin 버전](../kotlin/02-getting-started.md)

공통 페이지: [1. 개요](../01-overview.md) · [11. 오류와 언어](../11-errors-and-languages.md)

---

<a id="requirements"></a>
## 요구 사항

| 항목 | 버전 | 참고 |
|---|---|---|
| Java | 21 | lambdaQL을 빌드하고 사용하는 데 필요합니다. |
| JPA 구현체 | Hibernate ORM 6.6 | lambdaQL은 Hibernate 6 HQL을 만듭니다. 설계는 6.6.5로 검증했습니다. 다른 JPA 구현체는 지원하지 않습니다. |
| Jakarta Persistence | 3.1 | Hibernate 6.6에 포함되어 있습니다. |
| 데이터베이스 | H2 2.3, MariaDB 10.11, Oracle 23.26 (23ai) | 이 세 가지를 검증했습니다. 다른 데이터베이스도 동작할 수 있지만 테스트하지 않았습니다. |
| Spring Boot | 3.4 | 선택 사항입니다. 프로젝트 테스트는 Spring Boot 3.4를 사용합니다. |
| 컴파일러 | javac | ECJ(Eclipse 컴파일러)로 컴파일한 람다는 보장하지 않습니다. [9. Java 노트](09-java-notes.md#912-compiler-and-build)를 참고하십시오. |

<a id="build-from-source"></a>
## 소스에서 빌드

```bash
git clone https://github.com/codebit0/lambdaql.git
cd lambdaql
./gradlew :query:build
```

- Gradle 래퍼(8.10)는 **JDK 23 이하**에서만 실행됩니다. JDK 21을 사용하십시오.
- 저장소에 `gradle/gradle-daemon-jvm.properties`가 있으므로 Gradle 데몬은 Java 21을 사용합니다.
- IntelliJ IDEA에서는 **Settings > Build Tools > Gradle > Gradle JVM**을 Java 21 JDK로 설정하십시오.

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

jar를 로컬 Maven 저장소에 배포합니다.

```bash
./gradlew :query:publishToMavenLocal
```

그다음 프로젝트에 추가합니다.

Gradle(Groovy DSL):

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
> 이 좌표는 **임시**입니다.
> `lambadaql`이라는 이름에는 오타가 있으며 첫 릴리스 전에 고칠 예정입니다.
> 그룹, 이름, 버전은 바뀔 수 있습니다. lambdaQL이 배포되면 이 페이지를 다시 확인하십시오.

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

`QueryBuilder`는 진입점입니다. JPA `EntityManagerFactory`가 필요합니다.

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

Spring Boot는 이미 `EntityManagerFactory`를 만듭니다. 이를 주입받아 `QueryBuilder` 빈을 만드십시오.

```java
@Configuration
public class LambdaQlConfig {

    @Bean
    public QueryBuilder queryBuilder(EntityManagerFactory entityManagerFactory) {
        return new QueryBuilder(entityManagerFactory);
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

- `lambdaql-spring-data-jpa` 모듈이 배포되기 전까지는 빈을 직접 만듭니다. 이 모듈이 나오면 빈을 대신 만들어 줍니다.
- Spring 트랜잭션 안에서는 lambdaQL이 트랜잭션의 공유 `EntityManager`를 사용합니다.
- Spring Data 리포지토리 안에서 lambdaQL 쿼리를 작성하려면 [12. Spring Data JPA](12-spring-data-jpa.md)를 참고하십시오.

<a id="your-first-query"></a>
## 첫 쿼리

```java
List<Order> orders = qb.from(Order.class)
    .where(o -> o.getStatus() == 1 && o.getPrice() > 100)
    .toList();
// HQL: select e0 from Order e0 where e0.status = 1 and e0.price > 100.0
```

**SQL 블록 읽는 법.** `…`는 줄여 쓴 select 목록(엔티티의 모든 컬럼)이고, `?`는 바인드 파라미터입니다. 각 줄은 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)에서 나옵니다. 이 테스트는 각 쿼리를 Hibernate 6.6.5로 H2 2.3, MariaDB 10.11, Oracle 23.26에서 실행하며, Hibernate의 SQL이 바뀌면 실패합니다.

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `to-hql`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>100.0
```

- `qb.from(Order.class)`는 `Order`에 대한 쿼리를 시작합니다.
- `where(...)`는 조건 람다 하나를 받습니다.
- `toList()`는 쿼리를 실행하고 `List<Order>`를 반환합니다.
- 현재 확정된 결과 메서드는 `toList()`뿐입니다. 다른 결과 메서드는 계획 중입니다.

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

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `start-captured`:

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

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `start-many-where`:

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

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `inner-join`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name=?
```

고객이 없는 주문을 찾으려면 연관을 비교하십시오. `o.getCustomer() == null`은 `e0.customer is null`이 됩니다(조인 없음).
null 가드가 고객 없는 주문을 남기면(`o.getCustomer() == null || ...`) lambdaQL은 `left join`을 사용합니다.
[3.10](03-where.md#310-association-paths)을 참고하십시오.

<a id="things-to-know"></a>
## 알아 둘 점

| 규칙 | 예 |
|---|---|
| 람다나 메서드 참조를 사용하십시오. 익명 클래스는 거부됩니다. | `where(o -> o.isActive())`, `where(Order::isActive)` |
| 람다는 식 하나여야 합니다(조건, 정렬, 조회). | 람다 안의 반복문과 지역 변수는 거부됩니다. |
| 반드시 실행되어야 하는 코드를 람다에 넣지 마십시오. 람다는 실행되지 않습니다. | `System.out.println(...)`처럼 부수 효과가 있는 호출은 거부됩니다. |
| javac로 컴파일하십시오. | ECJ 출력은 보장하지 않습니다. |
| 새 JDK 버전에는 그에 맞는 ASM 버전이 필요합니다. | lambdaQL은 ASM 9.11.1을 사용합니다(Java 28까지의 클래스 파일을 읽음). 더 새로운 JDK로 컴파일한다면 ASM을 업데이트하십시오. |

Java에 관한 더 자세한 내용은 [9. Java 노트](09-java-notes.md)에 있습니다.

<a id="next"></a>
## 다음

- [3. 조건(`where`)](03-where.md): 모든 연산자와 NULL 규칙.
- [4. 정렬과 페이징](04-order-paging.md)

---

[Java 트랙](README.md) | 이전: [1. 개요](../01-overview.md) | 다음: [3. 조건](03-where.md) | [이 페이지의 Kotlin 버전](../kotlin/02-getting-started.md)
