<a id="1-overview"></a>
# 1. 개요

[매뉴얼 홈](README.md) | 트랙: [Java](java/README.md) | [Kotlin](kotlin/README.md) | 다음: 2. 시작하기 ([Java](java/02-getting-started.md) | [Kotlin](kotlin/02-getting-started.md))

> [!NOTE]
> **상태:** 분석 코어(람다 로딩과 조건 디코딩)와 결과 함수 `toList()`, `toSet()`, `first()`, `one()`, `count()`, `exists()`를 포함한 `where`(HQL 렌더링과 쿼리 실행)가 구현되어 있습니다. H2, MariaDB, Oracle에서 Java 람다로 테스트했습니다.
> 정렬과 페이징(`orderBy`, `limit`, `offset`, `SortSpec`)의 핵심 기능도 구현되어 테스트했습니다.
> Spring Data JPA 모듈(`LambdaQlExecutor<T>`와 Spring Boot 자동 구성)도 구현되어 Spring Boot 3.4.2에서 테스트했습니다.
> 수정, 삽입, 삭제, 업서트도 `limit`과 1행 보호 `executeOne()`을 포함해 구현되어 테스트했으며, Spring Data JPA 모듈에서도 사용할 수 있습니다(단축 메서드 `deleteAll`, `updateOne` 등). Spring Data JPA 모듈에서는 호출자의 트랜잭션이 필요합니다.
> `where` 안의 서브쿼리와 `select`(`groupBy`, `having`, `Agg` 함수 포함)도 구현되어 테스트했습니다.
> 명시적 조인(`join`, `leftJoin`, `crossJoin`), 파생 테이블과 CTE, 저장 프로시저와 DB 함수, 4개 언어 메시지와 오류 코드도 구현되어 테스트했습니다.
> Kotlin 람다는 일부 형태(`?.`, `!!`, `in`, 서브쿼리, 데이터 클래스, `groupBy`, update, insert, delete)를 테스트했습니다.
> 이 페이지의 HQL은 설계가 정의한 출력입니다. 예제는 설계 예제입니다.

이 페이지는 두 언어 모두를 위한 것입니다. 코드는 **Java**와 **Kotlin** 섹션으로 나누어 보여 줍니다.

<a id="what-is-lambdaql"></a>
## lambdaQL이란?

lambdaQL은 JPA와 Hibernate 6.6 위에서 동작하는 Java와 Kotlin용 쿼리 라이브러리입니다.

- 쿼리를 **람다**로 작성합니다.
- lambdaQL은 각 람다의 **바이트코드를 읽습니다**. 람다를 **절대 실행하지 않습니다**.
- lambdaQL은 바이트코드로부터 **HQL**(Hibernate Query Language)을 만듭니다.
- 람다에 직접 쓴 상수(`1`, `100.0`, `"Kim"`)는 HQL에 그대로 씁니다. 람다가 캡처한 값은 **바인드 파라미터**가 됩니다.
- Hibernate가 데이터베이스에서 HQL을 실행합니다.

### Java

```java
long minPrice = 100;

List<Order> orders = qb.from(Order.class)
    .where(o -> o.getStatus() == 1 || o.getCustomer().getName().equals("Kim"))
    .where(o -> o.getPrice() > minPrice)
    .toList();
```

### Kotlin

```kotlin
val minPrice = 100.0

val orders = qb.from(Order::class.java)
    .where { it.status == 1 || it.customer?.name == "Kim" }
    .where { it.price!! > minPrice }
    .toList()
```

<a id="hql-the-same-for-both"></a>
### HQL (두 언어 모두 동일)

```
select e0 from Order e0 join e0.customer j1
where (e0.status = 1 or j1.name = 'Kim') and e0.price > :p1
```

**SQL 블록 읽는 법.** `…`는 줄여 쓴 select 목록(엔티티의 모든 컬럼)이고, `?`는 바인드 파라미터입니다. 각 줄은 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)에서 나옵니다. 이 테스트는 각 쿼리를 Hibernate 6.6.5로 H2 2.3, MariaDB 10.11, Oracle 23.26에서 실행하며, Hibernate의 SQL이 바뀌면 실패합니다.

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `overview-first`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where (o1_0.status=1 or c1_0.name='Kim') and o1_0.price>?
```

두 `where` 호출은 `AND`로 결합됩니다.
상수 `"Kim"`은 리터럴이고, 캡처한 `minPrice`는 바인드 파라미터(`:p1`)입니다.
`where`의 `o.getCustomer().getName()` 같은 경로는 기본적으로 inner `join`입니다. 고객이 없는 주문은 반환되지 않습니다.
이 예제도 마찬가지입니다. Java라면 status가 1인 주문은 고객이 없어도 조건에 맞지만, lambdaQL은 SQL 의미를 사용합니다(결정 D48).
이런 주문을 유지하려면 `leftJoin`을 사용하십시오.

람다는 평범한 Java나 Kotlin 코드처럼 보입니다.
하지만 람다는 Java나 Kotlin 문법으로 쓴 **쿼리 언어**입니다.
`o.getCustomer().getName()`(Kotlin에서는 `it.customer?.name`)은 getter를 호출하지 않습니다. **컬럼을 가리킵니다**.

<a id="why-lambdaql"></a>
## 왜 lambdaQL인가?

| 목표 | lambdaQL의 방법 |
|---|---|
| 타입 안전성 | 컴파일러가 람다를 검사합니다. IDE에서 속성 이름을 바꾸면 쿼리의 이름도 함께 바뀝니다. |
| 생성 코드 없음 | 메타모델 생성기나 Q-클래스가 필요 없습니다. lambdaQL은 Hibernate가 실행 시점에 이미 가지고 있는 JPA Metamodel을 사용합니다. |
| 익숙한 문법 | 이미 알고 있는 `==`, `&&`, `\|\|`, `!`, getter나 속성, 메서드 호출을 사용합니다. |
| 안전한 값 | 캡처 값과 계산한 값은 항상 바인드 파라미터가 됩니다. 코드에 컴파일된 상수만 쿼리 텍스트에 씁니다(작은따옴표는 escape함). |
| 람다를 실행하지 않음 | 엔티티 객체를 만들지 않습니다. getter를 호출하지 않습니다. 람다에서 `NullPointerException`이 발생하지 않습니다. |
| 명확한 오류 | lambdaQL이 번역할 수 없는 형태의 람다가 있으면 코드가 붙은 오류를 던집니다. 추측하지 않습니다. |

<a id="how-it-works-5-steps"></a>
## 동작 방식 (5단계)

```
 your lambda ──► 1. find body ──► 2. read bytecode ──► 3. rebuild condition
                                                              │
     result ◄── 5. run with Hibernate ◄── 4. build HQL ◄──────┘
```

| 단계 | 하는 일 | 상태 |
|---|---|---|
| 1. 람다 본문 찾기 | 람다는 `Serializable`입니다. lambdaQL은 직렬화 훅을 통해 `SerializedLambda`를 얻습니다(`setAccessible` 없음). 이를 통해 어떤 메서드가 람다 본문을 담고 있는지, 어떤 값이 캡처되었는지 알 수 있습니다. | 구현됨 |
| 2. 바이트코드 읽기 | lambdaQL은 ASM으로 그 메서드 하나만 읽습니다. 클래스 파일을 읽을 뿐 코드를 실행하지 않습니다. | 구현됨 |
| 3. 조건 다시 만들기 | Java와 Kotlin은 `&&`, `\|\|`, `!`를 점프로 컴파일합니다. lambdaQL은 이 점프로부터 원래의 논리 트리를 다시 만듭니다. | Java 람다는 구현됨. Kotlin은 `?.`, `!!`, `in`을 테스트함. 나머지 Kotlin 전용 형태(`?:`, 범위, `when`)는 설계됨. |
| 4. HQL 만들기 | getter와 속성은 JPA 속성 경로가 됩니다(`o.getPrice()` 또는 `it.price` → `e0.price`). 람다 안의 상수는 리터럴이 되고, 캡처 값은 파라미터(`:p1`)가 됩니다. | `where`와 `orderBy`에 대해 구현됨, H2·MariaDB·Oracle에서 테스트함 |
| 5. 실행 | lambdaQL은 파라미터를 바인딩하고 Hibernate로 HQL을 실행합니다. 각 람다의 분석 결과는 캐시되므로 바이트코드는 한 번만 읽습니다. | `toList()`와 `toSet()`(`limit` / `offset` 포함), `first()`, `one()`, `count()`, `exists()`에 대해 구현됨 |

람다 하나를 읽는 데는 몇 마이크로초가 걸립니다. 캐시 덕분에 lambdaQL은 람다의 바이트코드를 한 번만 읽습니다.

lambdaQL에 전달하는 Kotlin 람다 리터럴은 Java 람다와 같은 종류의 직렬화 가능한 람다로 컴파일됩니다.
그래서 하나의 엔진으로 두 언어를 모두 읽을 수 있습니다.

<a id="design-principles"></a>
## 설계 원칙

<a id="one-lambda-per-clause"></a>
### 절마다 람다 하나

각 절은 람다 **하나**를 받습니다. 컬럼마다 람다를 하나씩 쓰지 않습니다.

#### Java

```java
.where(o -> o.getStatus() == 1 && o.isActive())                 // 조건 람다 하나
.orderBy((o, by) -> by.desc(o.getPrice()).asc(o.getId()))     // 모든 키를 위한 정렬 람다 하나
.select(o -> new OrderView(o.getId(), o.getPrice()))           // 조회 람다 하나
```

#### Kotlin

```kotlin
.where { it.status == 1 && it.active }                          // 조건 람다 하나
.orderBy { o, by -> by.desc(o.price).asc(o.id) }                // 모든 키를 위한 정렬 람다 하나
.select { OrderView(it.id, it.price) }                          // 조회 람다 하나
```

정렬 빌더 `by`나 서브쿼리 팩토리 `sub` 같은 헬퍼는 **람다 인자**입니다.
이름은 마음대로 지을 수 있습니다. 이 매뉴얼에서는 `by`와 `sub`를 사용합니다.

<a id="no-static-helpers-except-agg"></a>
### `Agg`와 `Dml`을 제외하면 정적 헬퍼가 없음

정적 헬퍼 클래스는 두 개뿐입니다. 집계(`count`, `sum`, `avg`, `min`, `max`, `countDistinct`)를 위한 `org.lambdaql.Agg`와, setter가 없는 엔티티에 값을 대입하는 `org.lambdaql.Dml`(`Dml.value(column, value)`, 결정 D49)입니다.
두 클래스의 정적 임포트는 선택 사항입니다.

#### Java

```java
.having(o -> Agg.sum(o.getPrice()) > 1000)
```

#### Kotlin

```kotlin
.having { Agg.sum(it.price)!! > 1000 }
```

직접 만든 데이터베이스 함수도 정적 메서드이지만, 선언은 직접 합니다
(8. 프로시저와 DB 함수 참고: [Java](java/08-procedures-functions.md) | [Kotlin](kotlin/08-procedures-functions.md)).

<a id="a-lambda-is-a-query-not-code"></a>
### 람다는 코드가 아니라 쿼리

lambdaQL은 Java나 Kotlin의 실행 규칙이 아니라 문서화된 자체 규칙을 따릅니다.

#### Java

| 작성한 코드 | lambdaQL의 의미 | 이유 |
|---|---|---|
| `o.getId() == id`(두 `Long` 객체) | 값 동등성: `e0.id = :p1` | 쿼리에서 객체에 대한 `==`는 "같은 객체"가 아니라 "같은 값"을 뜻합니다. 캡처 값 `null`은 NULL로 바인딩되어 어떤 행에도 일치하지 않습니다(`WARN` 수준 `LQL-4045`). NULL을 찾으려면 `o.getId() == null`이라고 쓰십시오. |
| `Objects.equals(o.getDescription(), o.getCustName())`, `!text.equals(o.getDescription())` | `e0.description = e0.custName`, `e0.description <> :p1`(SQL 의미) | 엔티티 값 두 개를 비교하거나 값과 `!=`로 비교하면, 값이 NULL인 행은 일치하지 않습니다. Java라면 이 중 일부 행을 반환합니다. 이런 행을 유지하려면 `o.getDescription() == null \|\| ...`이라고 쓰십시오. |
| `o.getCustomer().getName()` | 조인한 고객의 `name` 컬럼(`where`에서는 기본적으로 inner `join`) | 경로는 컬럼을 가리킵니다. lambdaQL은 customer가 null인지 검사하지 않습니다. `where`에서는 고객이 없는 주문이 반환되지 않습니다. 그런 주문을 찾으려면 `o.getCustomer() == null`이라고 쓰십시오. null 가드(`o.getCustomer() == null \|\| ...`)를 써도 마찬가지입니다(SQL 의미, D48). 이런 주문을 유지하려면 `leftJoin`을 사용하십시오. |
| `status`가 NULL일 때 `o.getStatus() == 1` | 행이 일치하지 않음(SQL 의미) | Java라면 `NullPointerException`을 던집니다. SQL은 UNKNOWN을 반환합니다. |
| `o.getDescription() == null` | `e0.description is null` | null 검사를 직접 작성했으므로 lambdaQL이 이를 사용합니다. |

> [!TIP]
> IntelliJ는 `o.getId() == id`에 "Number objects compared using `==`" 경고를 표시할 수 있습니다.
> lambdaQL 람다에서는 의도한 동작입니다. `@SuppressWarnings("NumberEquality")`를 사용할 수 있습니다.

#### Kotlin

| 작성한 코드 | lambdaQL의 의미 | 이유 |
|---|---|---|
| `it.id == id`(`id: Long?`, nullable 변수) | 값 동등성: `e0.id = :p1` | Kotlin의 `==`는 원래 "같은 값"을 뜻합니다. 캡처 값 `null`은 NULL로 바인딩되어 어떤 행에도 일치하지 않습니다(`WARN` 수준 `LQL-4045`). |
| `it.description == it.custName`, `it.description != text` | `e0.description = e0.custName`, `e0.description <> :p1`(SQL 의미) | 엔티티 값 두 개를 비교하거나 값과 `!=`로 비교하면, 값이 NULL인 행은 일치하지 않습니다. 이런 행을 유지하려면 `it.description == null \|\| ...`이라고 쓰십시오. |
| `it.customer?.name` 또는 `it.customer!!.name` | 조인한 고객의 `name` 컬럼(`where`에서는 기본적으로 inner `join`) | 경로는 컬럼을 가리킵니다. `?.`와 `!!`는 컬럼을 바꾸지 않습니다. `!!`는 람다를 컴파일하기 위해 있을 뿐입니다. `where`에서는 고객이 없는 주문이 반환되지 않습니다. 그런 주문을 찾으려면 `it.customer == null`이라고 쓰십시오. null 가드(`it.customer == null \|\| ...`)를 써도 마찬가지입니다(SQL 의미, D48). 이런 주문을 유지하려면 `leftJoin`을 사용하십시오. |
| `price`가 NULL일 때 `it.price!! > 100.0` | 행이 일치하지 않음(SQL 의미) | Kotlin이라면 `!!`에서 `NullPointerException`을 던집니다. lambdaQL은 `!!`를 무시하고, SQL은 UNKNOWN을 반환합니다. |
| `it.description == null` | `e0.description is null` | null 검사를 직접 작성했으므로 lambdaQL이 이를 사용합니다. |

lambdaQL은 **직접 작성한 곳에서만** null을 처리합니다.
람다 안의 모든 비교와 메서드는 SQL 의미를 따릅니다(결정 D48). 캡처 값과 연관 경로도 마찬가지입니다.
결과가 Java나 Kotlin과 다를 수 있으면 lambdaQL은 `LQL-5009`를 `DEBUG` 수준으로 기록합니다. `=`나 `<>`에 캡처 값 `null`이 쓰이면 `LQL-4045`를 `WARN` 수준으로 기록합니다.
3. 조건([Java](java/03-where.md) | [Kotlin](kotlin/03-where.md))과
10. 규칙과 제한([Java](java/10-rules-and-limits.md) | [Kotlin](kotlin/10-rules-and-limits.md))을 참고하십시오.

### SARGable SQL

lambdaQL은 인덱스를 사용할 수 있는("SARGable") SQL을 만듭니다.

- 컬럼 쪽에 함수, 연산, `CASE`를 두지 않습니다.
- 값을 바꿔야 하면(예: 소문자로) lambdaQL은 바인딩하기 전에 Java에서 **파라미터**를 바꿉니다.
- 규칙상 컬럼에 함수가 필요하고 인덱스가 도움이 되지 않으면 lambdaQL은 경고를 로그로 남깁니다.

#### Java

| 작성한 코드 | lambdaQL이 만들지 **않는** 것 | lambdaQL이 만드는 것 |
|---|---|---|
| `o.getId() != null && o.getId() == id` | `e0.id is not null and e0.id = :p1` | `e0.id = :p1`(id는 NULL이 될 수 없음) |
| `o.getDescription() == text` | `e0.description is not distinct from :p1`(Oracle에서는 `decode(...)`, 인덱스 없음) | `e0.description = :p1` |

#### Kotlin

| 작성한 코드 | lambdaQL이 만들지 **않는** 것 | lambdaQL이 만드는 것 |
|---|---|---|
| `(it.description ?: "") == "x"` | `coalesce(e0.description, '') = 'x'` | `e0.description = 'x'` |
| `it.description.isNullOrEmpty()` | `(e0.description is null or length(e0.description) = 0)` | `(e0.description is null or e0.description = :p1)` |
| `it.id == id`(`it.id`는 `Long?`) | `not (e0.id is null or e0.id <> :p1)` | `e0.id = :p1`(id는 NULL이 될 수 없음) |

<a id="java-and-kotlin"></a>
### Java와 Kotlin

Java와 Kotlin은 **같은 엔진**을 사용합니다.
의미가 같은 Kotlin 람다와 Java 람다는 같은 HQL을 만듭니다.
일부 Kotlin 형태는 지원하지 않습니다. 이런 형태에 대해 lambdaQL은 명확한 오류를 던집니다([9. Kotlin 노트](kotlin/09-kotlin-notes.md) 참고).
Java 전용 세부 사항은 [9. Java 노트](java/09-java-notes.md)에 있습니다.

<a id="fail-loudly"></a>
### 조용히 넘어가지 않기

lambdaQL은 어떤 형태의 의미를 모르면 추측하지 않습니다.
`[LQL-2004]` 같은 코드가 붙은 예외를 던집니다([11. 오류와 언어](11-errors-and-languages.md) 참고).

거부되는 형태의 예:

| 형태 | Java | Kotlin |
|---|---|---|
| 조건에 따라 선택되는 값 | `o.getStatus() == (flag ? 1 : 2)` | `it.status == if (flag) 1 else 2` |
| 람다 안의 반복문이나 지역 변수 | 예, 거부됨 | 예, 거부됨 |
| 람다가 아닌 것 | 익명 클래스 | `object : ...` 식, 또는 Kotlin 함수 타입 값(`val f: (Order) -> Boolean`) |
| SQL에서 의미가 없는 메서드 호출 | 예, 거부됨 | 예, 거부됨(직접 만든 non-inline 확장 함수도 포함) |

<a id="comparison-with-other-approaches"></a>
## 다른 방식과의 비교

이 표는 짧고 중립적인 요약입니다. 각 도구는 서로 다른 요구에 적합합니다.

| | JPQL / HQL 문자열 | JPA Criteria API | QueryDSL | lambdaQL |
|---|---|---|---|---|
| 쿼리 작성 방식 | 문자열 안의 텍스트 | Java 빌더 객체 | 생성된 Q-클래스 | 람다 |
| 컴파일러 검사 | 아니요(실행 시점에 Hibernate가 검사) | 예 | 예 | 예 |
| 생성 코드 필요 | 아니요 | 선택(정적 메타모델) | 예(Q-클래스) | 아니요 |
| IDE 이름 변경에 안전 | 아니요 | 정적 메타모델 사용 시 | 예 | 예 |
| 모양 | SQL | Java 빌더 코드 | SQL 같은 Java | 평범한 Java / Kotlin 식 |
| Kotlin | 문자열 | 동작하지만 장황함 | 동작함 | 지원(같은 엔진) |
| 성숙도 | 표준 | 표준 | 성숙, 널리 사용됨 | 초기 개발, 미출시 |

<a id="next"></a>
## 다음

- 2. 시작하기: lambdaQL을 빌드하고 첫 쿼리를 작성합니다. [Java](java/02-getting-started.md) | [Kotlin](kotlin/02-getting-started.md)
- [매뉴얼 홈](README.md)
