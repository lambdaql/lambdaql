<a id="2-getting-started-kotlin"></a>
# 2. 시작하기 (Kotlin)

> [!NOTE]
> **상태:** 현재 lambdaQL을 소스에서 빌드하고 테스트를 실행할 수 있습니다.
> `where` 조건과 `toList()`는 처음부터 끝까지 동작합니다. H2, MariaDB, Oracle에서 **Java** 람다로 테스트했습니다. Kotlin 람다는 아직 테스트하지 않았습니다.
> 정렬과 페이징(`orderBy`, `limit`, `offset`)의 핵심 기능도 동작합니다(이것도 Java 람다로만 테스트함).
> 서브쿼리, `select`, update/insert/delete, 프로시저, Spring 모듈은 **설계되었지만 아직 구현되지 않았습니다**.
> 아직 배포된 아티팩트는 없습니다. 아래의 아티팩트 이름은 임시입니다.

[Kotlin 트랙](README.md) | [매뉴얼 홈](../README.md) | 이전: [1. 개요](../01-overview.md) | 다음: [3. 조건](03-where.md) | [이 페이지의 Java 버전](../java/02-getting-started.md)

공통 페이지: [1. 개요](../01-overview.md) · [11. 오류와 언어](../11-errors-and-languages.md)

---

<a id="requirements"></a>
## 요구 사항

| 항목 | 버전 | 참고 |
|---|---|---|
| Kotlin | 2.x | 설계는 kotlinc 1.7.22, 2.1.10, 2.2.21, 2.3.0, 2.3.21로 확인했습니다. |
| JVM | Java 21 | lambdaQL을 빌드하는 데 필요합니다. |
| `-jvm-target` | 1.8, 11, 17 또는 21 | 네 가지 모두 쿼리 람다를 같은 방식으로 컴파일합니다. |
| JPA 구현체 | Hibernate ORM 6.6 | lambdaQL은 Hibernate 6 HQL을 만듭니다. 설계는 6.6.5로 검증했습니다. 다른 JPA 구현체는 지원하지 않습니다. |
| Jakarta Persistence | 3.1 | Hibernate 6.6에 포함되어 있습니다. |
| 데이터베이스 | H2 2.3, MariaDB 10.11, Oracle 23.26 (23ai) | 이 세 가지를 검증했습니다. 다른 데이터베이스도 동작할 수 있지만 테스트하지 않았습니다. |
| Spring Boot | 3.4 | 선택 사항입니다. 프로젝트 테스트는 Spring Boot 3.4를 사용합니다. |
| Kotlin 런타임 | – | lambdaQL은 의존성에 `kotlin-stdlib`나 `kotlin-reflect`를 추가하지 않습니다. |

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

[`SelectQueryTest.java`](../../../../query/src/test/java/org/lambdaql/query/SelectQueryTest.java)의 테스트는 lambdaQL이 현재 할 수 있는 일을 보여 줍니다: `qb.from(...).where(...)`, HQL, 파라미터, 그리고 H2·MariaDB·Oracle에서 나온 행. [`ConditionDecodeTest.java`](../../../../query/src/test/java/org/lambdaql/engine/decode/ConditionDecodeTest.java)는 내부 조건 디코더를 테스트합니다. 이 테스트는 Java로 작성되어 있습니다. Kotlin 테스트는 계획 중입니다(`query/src/test/kotlin`).

<a id="use-the-local-build-in-your-project"></a>
### 로컬 빌드를 프로젝트에서 사용하기

jar를 로컬 Maven 저장소에 배포합니다.

```bash
./gradlew :query:publishToMavenLocal
```

그다음 프로젝트에 추가합니다(Gradle Kotlin DSL).

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
> 이 좌표는 **임시**입니다.
> `lambadaql`이라는 이름에는 오타가 있으며 첫 릴리스 전에 고칠 예정입니다.
> 그룹, 이름, 버전은 바뀔 수 있습니다. lambdaQL이 배포되면 이 페이지를 다시 확인하십시오.

<a id="kotlin-compiler-settings"></a>
## Kotlin 컴파일러 설정

| 설정 | 할 일 | 이유 |
|---|---|---|
| `-Xsam-conversions` | 기본값(`indy`)을 유지하십시오. `-Xsam-conversions=class`를 설정하지 **마십시오**. | `class`로 설정하면 Kotlin은 직렬화 가능한 람다 대신 클래스를 만듭니다. lambdaQL은 이를 읽을 수 없습니다(제한 KC1). |
| `-java-parameters` | 저장 프로시저를 호출한다면 권장합니다. | 그러면 lambdaQL이 파라미터 이름을 데이터베이스와 비교할 수 있습니다([8.4](08-procedures-functions.md#84-warning-argument-order) 참고). |
| JPA 플러그인 | `kotlin("plugin.jpa")`와 all-open(또는 `kotlin("plugin.spring")`)을 사용하십시오. | JPA에는 인자 없는 생성자와 open 클래스가 필요합니다. Kotlin JPA 엔티티에서는 일반적인 설정입니다. |

예(`build.gradle.kts`):

```kotlin
plugins {
    kotlin("jvm")
    kotlin("plugin.jpa")
    kotlin("plugin.spring")   // 또는 all-open 플러그인
}

kotlin {
    compilerOptions {
        javaParameters = true   // -java-parameters와 같음
    }
}
```

플러그인 버전은 사용하는 Kotlin 버전에 맞추십시오.

<a id="example-entities"></a>
## 예제 엔티티

Kotlin 트랙의 모든 예제는 다음 엔티티를 사용합니다.

```kotlin
@Entity
@Table(name = "orders")
class Order(
    @Id @GeneratedValue
    var id: Long? = null,
    var price: Double? = null,
    var status: Int? = null,
    var active: Boolean = false,          // Kotlin Boolean: JVM 기본형, 절대 NULL이 아님
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
    var qty: Int = 0,                     // Kotlin Int: JVM 기본형, 절대 NULL이 아님
)
```

엔티티 규칙:

| 규칙 | 이유 |
|---|---|
| 일반적인 public `var`나 `val` 속성을 사용하십시오. | lambdaQL은 getter를 읽고(`it.price`는 getter `getPrice()`) JPA 속성에 매핑합니다. |
| NULL일 수 있는 컬럼에는 nullable 타입(`Double?`, `String?`)을 사용하십시오. | Kotlin의 non-null 타입은 데이터베이스에서 오는 NULL을 막지 못합니다. Hibernate는 검사 없이 컬럼 값을 필드에 씁니다. |
| 쿼리에서 `@JvmField`, `internal` 속성, `value class` 속성, `@get:JvmName`을 사용하지 마십시오. | lambdaQL은 이들을 JPA 속성에 매핑할 수 없습니다(제한 KC15, KC16). |
| `Boolean`, `Int`, `Long`, `Double`(nullable 아님)은 JVM 기본형입니다. | 절대 NULL을 담지 않습니다. lambdaQL은 이를 알고 더 단순한 SQL을 만들 수 있습니다. |

<a id="create-a-querybuilder"></a>
## QueryBuilder 만들기

`QueryBuilder`는 진입점입니다. JPA `EntityManagerFactory`가 필요합니다.

```kotlin
val qb = QueryBuilder(entityManagerFactory)
```

`QueryBuilder`는 하나만 만들어 재사용하십시오. 쿼리마다 새로 만들지 마십시오.

<a id="with-plain-jpa"></a>
### 일반 JPA에서

```kotlin
val emf = Persistence.createEntityManagerFactory("my-unit")
val qb = QueryBuilder(emf)
```

<a id="with-spring-boot"></a>
### Spring Boot에서

Spring Boot는 이미 `EntityManagerFactory`를 만듭니다. 이를 주입받아 `QueryBuilder` 빈을 만드십시오.

```kotlin
@Configuration
class LambdaQlConfig {

    @Bean
    fun queryBuilder(entityManagerFactory: EntityManagerFactory) = QueryBuilder(entityManagerFactory)
}
```

그다음 필요한 곳에 `QueryBuilder`를 주입합니다.

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

- `lambdaql-spring-data-jpa` 모듈이 배포되기 전까지는 빈을 직접 만듭니다. 이 모듈이 나오면 빈을 대신 만들어 줍니다.
- Spring 트랜잭션 안에서는 lambdaQL이 트랜잭션의 공유 `EntityManager`를 사용합니다.
- Spring Data 리포지토리 안에서 lambdaQL 쿼리를 작성하려면 [12. Spring Data JPA](12-spring-data-jpa.md)를 참고하십시오.

<a id="your-first-query"></a>
## 첫 쿼리

```kotlin
val orders = qb.from(Order::class.java)
    .where { it.status == 1 && it.price!! > 100.0 }
    .toList()
// HQL: select e0 from Order e0 where e0.status = 1 and e0.price > 100.0
```

**SQL 블록 읽는 법.** `…`는 줄여 쓴 select 목록(엔티티의 모든 컬럼)이고, `?`는 바인드 파라미터입니다. 각 줄은 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)에서 나옵니다. 이 테스트는 각 쿼리를 Hibernate 6.6.5로 H2 2.3, MariaDB 10.11, Oracle 23.26에서 실행하며, Hibernate의 SQL이 바뀌면 실패합니다. 이 테스트는 Java 람다를 사용합니다. 의미가 같은 Kotlin 람다는 같은 HQL을 만들므로 SQL도 같습니다.

**데이터베이스별 SQL (Java 테스트 실행 결과, Kotlin도 같은 HQL을 만듦)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `to-hql`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>100.0
```

- `qb.from(Order::class.java)`는 `Order`에 대한 쿼리를 시작합니다.
- `where { ... }`는 조건 람다 하나를 받습니다. 후행 람다와 `it`을 사용하십시오.
- `toList()`는 쿼리를 실행하고 `List<Order>`를 반환합니다.
- 현재 확정된 결과 메서드는 `toList()`뿐입니다. 다른 결과 메서드는 계획 중입니다.

왜 `!!`를 쓸까요? `price`는 `Double?`이고, Kotlin은 nullable 값에 대해 `it.price > 100.0`을 컴파일하지 않습니다. `!!`를 쓰면 컴파일됩니다. lambdaQL은 `!!`를 **무시합니다**. `price`가 NULL인 행은 그냥 반환되지 않습니다(SQL 의미).

왜 `1`과 `100.0`은 바인드 파라미터가 아니라 HQL에 그대로 쓰일까요? 둘 다 **람다에 직접 쓴 상수**이기 때문입니다. lambdaQL은 상수(숫자, 문자열, boolean, `null`, enum 상수, `const val`)를 HQL에 리터럴로 씁니다. 람다 밖에서 온 값은 다릅니다. 항상 바인드 파라미터가 됩니다(다음 절과 [3.3](03-where.md#33-basic-form) 참고).

<a id="use-values-from-outside-the-lambda"></a>
### 람다 밖의 값 사용하기

람다는 지역 값, 속성, 함수 파라미터를 사용할 수 있습니다. 이를 **캡처 값**이라고 합니다.
lambdaQL은 캡처 값을 바인드 파라미터로 바꿉니다. 쿼리가 실행될 때마다 새 값을 읽습니다.

```kotlin
fun expensive(status: Int, minPrice: Double): List<Order> =
    qb.from(Order::class.java)
        .where { it.status == status && it.price!! >= minPrice }
        .toList()
// HQL: select e0 from Order e0 where e0.status = :p1 and e0.price >= :p2
```

**데이터베이스별 SQL (Java 테스트 실행 결과, Kotlin도 같은 HQL을 만듦)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `start-captured`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=? and o1_0.price>=?
```

<a id="many-where-calls"></a>
### 여러 번의 `where` 호출

`where`는 여러 번 호출할 수 있습니다. lambdaQL은 조건들을 `AND`로 결합합니다.

```kotlin
qb.from(Order::class.java)
    .where { it.active }
    .where { it.status == 1 || it.status == 2 }
    .toList()
// HQL: select e0 from Order e0
//      where e0.active = true and (e0.status = 1 or e0.status = 2)
```

**데이터베이스별 SQL (Java 테스트 실행 결과, Kotlin도 같은 HQL을 만듦)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `start-many-where`:

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

```kotlin
var query = qb.from(Order::class.java).where { it.active }
if (minPrice != null) {
    query = query.where { it.price!! >= minPrice }
}
val orders = query.toList()
```

<a id="paths-to-other-entities"></a>
### 다른 엔티티로 가는 경로

`it.customer?.name` 같은 경로는 조인한 엔티티의 컬럼을 가리킵니다.
`where`에서 lambdaQL은 기본적으로 inner `join`을 추가합니다. 고객이 없는 주문은 결과에 **없습니다**.

```kotlin
qb.from(Order::class.java)
    .where { it.customer?.name == name }
    .toList()
// HQL: select e0 from Order e0 join e0.customer j1 where j1.name = :p1
//      (여기서 name은 non-null String이므로 lambdaQL은 단순한 =를 씀. 3.6 참고)
```

**데이터베이스별 SQL (Java 테스트 실행 결과, Kotlin도 같은 HQL을 만듦)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `inner-join`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name=?
```

`it.customer!!.name`도 같은 HQL을 만듭니다. 마음에 드는 형태를 사용하십시오.
고객이 없는 주문을 찾으려면 연관을 비교하십시오. `it.customer == null`은 `e0.customer is null`이 됩니다(조인 없음).
`it.customer?.name == null`로는 그런 주문이 포함되지 **않습니다**.
null 가드가 고객 없는 주문을 남기면(`it.customer == null || ...`) lambdaQL은 `left join`을 사용합니다. [3.10](03-where.md#310-association-paths)을 참고하십시오.

<a id="things-to-know"></a>
## 알아 둘 점

| 규칙 | 예 |
|---|---|
| 람다 리터럴이나 속성 참조를 전달하십시오. | `where { it.active }`, `where(Order::active)` |
| 람다를 먼저 Kotlin 함수 타입에 저장하지 마십시오. | `val f: (Order) -> Boolean = { it.active }; where(f)`는 거부됩니다(KC4). |
| 쿼리 람다를 `inline fun` 안에서 만들지 마십시오. | 거부됩니다(KC2). 헬퍼를 non-inline으로 만들거나, 호출하는 쪽에서 람다를 전달하십시오. |
| 람다는 식 하나여야 합니다(조건, 정렬, 조회). | 람다 안의 반복문과 지역 `val`/`var`는 거부됩니다. |
| 반드시 실행되어야 하는 코드를 람다에 넣지 마십시오. 람다는 실행되지 않습니다. | `println(...)`처럼 부수 효과가 있는 호출은 거부됩니다. |
| 새 JDK 버전에는 그에 맞는 ASM 버전이 필요합니다. | lambdaQL은 ASM 9.11.1을 사용합니다(Java 28까지의 클래스 파일을 읽음). |

Kotlin의 모든 제한은 [9. Kotlin 노트](09-kotlin-notes.md#914-kotlin-limits-kc1-to-kc21)에 있습니다.

<a id="next"></a>
## 다음

- [3. 조건(`where`)](03-where.md): 모든 연산자와 null 규칙.
- [4. 정렬과 페이징](04-order-paging.md)

---

[Kotlin 트랙](README.md) | 이전: [1. 개요](../01-overview.md) | 다음: [3. 조건](03-where.md) | [이 페이지의 Java 버전](../java/02-getting-started.md)
