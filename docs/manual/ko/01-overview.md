<a id="1-overview"></a>
# 1. 개요

[매뉴얼 홈](README.md) | 트랙: [Java](java/README.md) | [Kotlin](kotlin/README.md) | 다음: 2. 시작하기 ([Java](java/02-getting-started.md) | [Kotlin](kotlin/02-getting-started.md))

이 페이지는 두 언어 모두를 위한 것입니다. 코드는 **Java**와 **Kotlin** 섹션으로 나누어 보여 줍니다.

<a id="what-is-lambdaql"></a>
## lambdaQL이란?

lambdaQL은 JPA와 Hibernate 위에서 동작하는 Java·Kotlin용 쿼리 라이브러리입니다.

- lambdaQL은 JPA 쿼리를 **람다 표현식**을 활용하여 표현하고 작성합니다.
- 람다 표현식 부분을 실행하지 않고 **분석하여 쿼리로 변환합니다**.
- lambdaQL은 메서드와 람다 표현식 실행 결과를 **HQL(JPQL)** 쿼리로 만듭니다.

### Java

<!-- region: ManualSharedTest.java#overview-first -->
```java
long minPrice = 100;

SelectQuery<Order> q = qb.from(Order.class)
        .where(o -> o.getStatus() == 1 || o.getCustomer().getName().equals("Kim"))
        .where(o -> o.getPrice() > minPrice);
assertEquals("select e0 from Order e0 left join e0.customer j1"
        + " where (e0.status = 1 or j1.name = 'Kim') and e0.price > :p1", q.toHql().hql());
List<Order> rows = q.toList();
```

### Kotlin

<!-- region: ManualKotlinSharedTest.kt#overview-first -->
```kotlin
val minPrice = 100.0

val q = qb.from(Order::class.java)
    .where { it.status.toInt() == 1 || it.customer!!.name == "Kim" }
    .where { it.price > minPrice }
assertEquals("select e0 from Order e0 left join e0.customer j1"
    + " where (e0.status = 1 or j1.name = 'Kim') and e0.price > :p1", q.toHql().hql())
val rows = q.toList()
```

<a id="hql-the-same-for-both"></a>
### HQL (두 언어 모두 동일)

```
select e0 from Order e0 left join e0.customer j1
where (e0.status = 1 or j1.name = 'Kim') and e0.price > :p1
```

**데이터베이스별 SQL**

<!-- sql: overview-first -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where (o1_0.status=1 or c1_0.name='Kim') and o1_0.price>?
```

- 두 `where` 호출은 `AND`로 결합됩니다.
- 상수 `"Kim"`은 리터럴로 쿼리에 기록되고, 캡처한 `minPrice`는 바인드 파라미터(`:p1`)로 처리됩니다.
- `where`에서 `o.getCustomer().getName()`처럼 연관 엔티티의 속성을 쓰면, lambdaQL은 고객 테이블을 조인합니다.
  고객이 없는 주문이 조건에 맞을 수 있으면 `left join`, 맞을 수 없으면 inner `join`입니다.
  - 위 예제는 status가 1인 주문이면 고객이 없어도 조건에 맞으므로 `left join`입니다. 
  - Where 조건이 `o.getCustomer().getName().equals("Kim")` 하나뿐이라면 inner `join`입니다.

람다 표현식은 평범한 Java나 Kotlin 코드처럼 보입니다.
하지만 lambdaQL에서 람다 표현식은 Java나 Kotlin 문법으로 쓴 **쿼리 언어**입니다.
`o.getCustomer().getName()`(Kotlin에서는 `it.customer?.name`)은 getter를 호출하지 않으며 **컬럼명으로 평가됩니다.**.

예제 코드: [`ManualSharedTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualSharedTest.java)(`overview-first`), [`ManualKotlinSharedTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSharedTest.kt)(`overview-first`).

<a id="why-lambdaql"></a>
## 왜 lambdaQL인가?

| 목표 | lambdaQL의 방법 |
|---|---|
| 타입 안전성 | 컴파일러가 람다 표현식을 검사합니다. IDE에서 속성 이름을 바꾸면 쿼리의 이름도 함께 바뀝니다. |
| 생성 코드 없음 | 메타모델 생성기나 Q-클래스가 필요 없습니다. lambdaQL은 Hibernate가 실행 시점에 이미 가지고 있는 JPA Metamodel을 사용합니다. |
| 익숙한 문법 | 이미 알고 있는 `==`, `&&`, `\|\|`, `!`, getter나 속성, 메서드 호출을 사용합니다. |
| 안전한 값 | 캡처 값과 계산한 값은 항상 바인드 파라미터가 됩니다. 코드에 컴파일된 상수만 쿼리 텍스트에 씁니다(작은따옴표는 escape함). |
| 람다 표현식을 분석하여 변환 | 람다 표현식 부분을 실행하지 않고 분석합니다. 엔티티 객체를 만들지 않고 getter를 호출하지 않으므로 직관적인 쿼리문을 만들 수 있습니다. |
| 분석 실패는 오류 | lambdaQL은 람다 표현식을 쿼리로 분석하지 못하면 코드가 붙은 예외를 던집니다. |

<a id="how-it-works-5-steps"></a>
## 동작 방식 (5단계)

```
 your lambda ──► 1. find body ──► 2. read bytecode ──► 3. rebuild condition
                                                              │
     result ◄── 5. run with Hibernate ◄── 4. build HQL ◄──────┘
```

| 단계 | 하는 일 |
|---|---|
| 1. 람다 표현식 본문 찾기 | lambdaQL은 람다 표현식의 본문이 컴파일된 위치와 람다 표현식이 캡처한 값을 찾습니다. |
| 2. 바이트 opcode 읽기 | lambdaQL은 1단계에서 찾은 람다 표현식 본문의 컴파일된 코드(바이트 opcode)를 읽습니다. 코드를 실행하지는 않습니다. Java 21~23에서는 ASM을, Java 24 이상에서는 JDK의 `java.lang.classfile`을 씁니다. |
| 3. 조건 다시 만들기 | Java와 Kotlin은 `&&`, `\|\|`, `!`를 점프 opcode로 컴파일합니다. lambdaQL은 이 점프 opcode로부터 원래의 논리 트리를 다시 만듭니다. |
| 4. HQL 만들기 | getter와 속성은 JPA 속성 경로가 됩니다(`o.getPrice()` 또는 `it.price` → `e0.price`). 람다 표현식 안의 상수는 리터럴이 되고, 캡처 값은 파라미터(`:p1`)가 됩니다. |
| 5. 실행 | lambdaQL은 파라미터를 바인딩하고 Hibernate로 HQL을 실행합니다. 각 람다 표현식의 분석 결과는 캐시되므로 바이트 opcode는 한 번만 읽습니다. |

lambdaQL은 람다 표현식마다 분석 결과를 캐시하므로 바이트 opcode는 한 번만 읽고 분석합니다.   
새 JVM의 첫 호출은 람다 표현식 분석 때문에 JPQL보다 약 26ms 더 걸리지만, 그 이후 쿼리마다 더해지는 비용은 약 0.5~3µs로 매우 적습니다(H2 실측, [벤치마크](../../design/BENCHMARK.md)).


lambdaQL에 전달하는 Kotlin 람다 표현식 리터럴은 Java 람다 표현식과 같은 종류의 직렬화 가능한 람다 표현식으로 컴파일됩니다.
그래서 하나의 엔진으로 두 언어를 모두 읽을 수 있습니다.

<a id="design-principles"></a>
## 설계 원칙

<a id="one-lambda-per-clause"></a>
### 쿼리 절마다 람다 표현식 하나

`where`, `orderBy`, `select` 같은 쿼리 절은 각각 람다 표현식 **하나**를 받습니다.
각 절의 람다 표현식 하나에 조회할 컬럼이나 정렬 키를 모두 적습니다.

#### Java

<!-- region: ManualSharedTest.java#one-lambda-per-clause -->
```java
ValueQuery<OrderView> q = qb.from(Order.class)
        .where(o -> o.getStatus() == 1 && o.isActive())                 // one condition lambda
        .orderBy(o -> desc(o.getPrice()).asc(o.getId()))     // one sort lambda for all keys
        .select(o -> new OrderView(o.getId(), o.getPrice()));          // one select lambda
assertEquals("select e0.id, e0.price from Order e0 where e0.status = 1 and e0.active = true"
        + " order by e0.price desc, e0.id asc", q.toHql().hql());
```

#### Kotlin

<!-- region: ManualKotlinSharedTest.kt#one-lambda-per-clause -->
```kotlin
val q = qb.from(Order::class.java)
    .where { it.status.toInt() == 1 && it.isActive }               // one condition lambda
    .orderBy { o -> desc(o.price).asc(o.id) }               // one sort lambda for all keys
    .select { OrderView(it.id, it.price) }                         // one select lambda
assertEquals("select e0.id, e0.price from Order e0 where e0.status = 1 and e0.active = true"
    + " order by e0.price desc, e0.id asc", q.toHql().hql())
```

<a id="no-static-helpers-except-agg"></a>
### 정적 헬퍼 `By`, `Agg`, `Dml`

| 클래스 | 용도 |
|---|---|
| `org.lambdaql.query.By` | 정렬 함수(`asc`, `desc`, `sort`) |
| `org.lambdaql.Agg` | 집계(`count`, `sum`, `avg`, `min`, `max`, `countDistinct`) |
| `org.lambdaql.Dml` | setter가 없는 엔티티에 값 대입(`Dml.value(column, value)`) |

#### Java

<!-- region: ManualSharedTest.java#agg -->
```java
ValueQuery<CustomerTotal> q = qb.from(Order.class)
        .groupBy(o -> o.getCustomer().getId())
        .having(o -> Agg.sum(o.getPrice()) > 1000)
        .select(o -> new CustomerTotal(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice())));
assertEquals("select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id"
        + " having sum(e0.price) > 1000.0", q.toHql().hql());
```

#### Kotlin

<!-- region: ManualKotlinSharedTest.kt#agg -->
```kotlin
val q = qb.from(Order::class.java)
    .groupBy { it.customer?.id }
    .having { Agg.sum(it.price) > 1000 }
    .select { CustomerTotal(it.customer?.id, Agg.count(), Agg.sum(it.price)) }
assertEquals("select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id"
    + " having sum(e0.price) > 1000.0", q.toHql().hql())
```

<a id="a-lambda-is-a-query-not-code"></a>
### 람다 표현식은 코드가 아니라 쿼리 표현식

lambdaQL은 Java나 Kotlin의 실행 규칙이 아니라 문서화된 자체 규칙을 따릅니다.

#### Java

| 작성한 코드 | lambdaQL의 의미 | 이유 |
|---|---|---|
| `o.getId() == id`(두 `Long` 객체) | 값 동등성: `e0.id = :p1` | 쿼리에서 객체에 대한 `==`는 "같은 객체"가 아니라 "같은 값"을 뜻합니다. 캡처 값 `null`은 NULL로 바인딩되어 어떤 행에도 일치하지 않습니다(`WARN` 수준 `LQL-4045`). NULL을 찾으려면 `o.getId() == null`이라고 쓰십시오. |
| `Objects.equals(o.getDescription(), o.getCustName())`, `!text.equals(o.getDescription())` | `e0.description = e0.custName`, `e0.description <> :p1`(SQL 의미) | 엔티티 값 두 개를 비교하거나 값과 `!=`로 비교하면, 값이 NULL인 행은 일치하지 않습니다. Java라면 이 중 일부 행을 반환합니다. 이런 행을 유지하려면 `o.getDescription() == null \|\| ...`이라고 쓰십시오. |
| `o.getCustomer().getName()` | 조인한 고객의 `name` 컬럼 | 고객 테이블을 inner `join` 또는 `left join`으로 조인합니다. 자세한 규칙은 [3.10 연관 경로](java/03-where.md#310-association-paths)를 참고하십시오. |
| `status` 컬럼이 NULL일 때 `o.getStatus() == 1` | 행이 반환되지 않음(SQL 의미) | `status` 컬럼이 NULL인 행은 결과에 없습니다. |
| `o.getDescription() == null` | `e0.description is null` | null 검사를 직접 작성했으므로 lambdaQL이 이를 사용합니다. |

> [!TIP]
> IntelliJ는 `o.getId() == id`에 "Number objects compared using `==`" 경고를 표시할 수 있습니다.
> lambdaQL 람다 표현식에서는 의도한 동작입니다. 메서드에 `@SuppressWarnings("NumberEquality")`를 선언하여 경고를 없앨 수 있습니다.

#### Kotlin

| 작성한 코드 | lambdaQL의 의미 | 이유 |
|---|---|---|
| `it.id == id`(`id: Long?`, nullable 변수) | 값 동등성: `e0.id = :p1` | Kotlin의 `==`는 원래 "같은 값"을 뜻합니다. 캡처 값 `null`은 NULL로 바인딩되어 어떤 행에도 일치하지 않습니다(`WARN` 수준 `LQL-4045`). |
| `it.description == it.custName`, `it.description != text` | `e0.description = e0.custName`, `e0.description <> :p1`(SQL 의미) | 엔티티 값 두 개를 비교하거나 값과 `!=`로 비교하면, 값이 NULL인 행은 일치하지 않습니다. 이런 행을 유지하려면 `it.description == null \|\| ...`이라고 쓰십시오. |
| `it.customer?.name` 또는 `it.customer!!.name` | 조인한 고객의 `name` 컬럼 | 고객 테이블을 inner `join` 또는 `left join`으로 조인합니다. 자세한 규칙은 [3.10 연관 경로](kotlin/03-where.md#310-association-paths)를 참고하십시오. |
| `price` 컬럼이 NULL일 때 `it.price!! > 100.0` | 행이 반환되지 않음(SQL 의미) | `price` 컬럼이 NULL인 행은 결과에 없습니다. |
| `it.description == null` | `e0.description is null` | null 검사를 직접 작성했으므로 lambdaQL이 이를 사용합니다. |

**NULL은 SQL과 동일하게 다룹니다.**   
비교하는 컬럼이 NULL인 행은 NULL 비교 조건에 맞지 않아 결과에 없습니다.
예를 들어 `where(o -> !text.equals(o.getDescription()))`(Kotlin은 `where { it.description != text }`)는 HQL `e0.description <> :p1`이 됩니다.   
`description` 컬럼이 NULL인 주문은 이 조건에 맞지 않아 결과에 없습니다. Java나 Kotlin의 리스트에서는 NULL이 `text`와 다르므로 포함되는 것과 다릅니다.
NULL인 주문도 받으려면 `o.getDescription() == null || !text.equals(o.getDescription())`처럼 null 검사를 직접 적으십시오.   
자세한 규칙은 3.6 NULL 규칙([Java](java/03-where.md#36-null-rules) | [Kotlin](kotlin/03-where.md#36-null-rules))을 참고하십시오.

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
| `it.id == id`(`it.id`는 `Long?`) | `not (e0.id is null or e0.id <> :p1)` | `e0.id = :p1`(id는 NULL이 될 수 없음) |

<a id="java-and-kotlin"></a>
### Java와 Kotlin

Java와 Kotlin은 같은 분석 엔진을 사용합니다. 그러므로 의미가 같은 Kotlin 람다 표현식과 Java 람다 표현식은 같은 HQL을 만듭니다.
예를 들어 Java의 `o.getPrice() > 100`과 Kotlin의 `it.price > 100.0`은 둘 다 `e0.price > 100.0`이 됩니다.
다만 모든 문법을 쿼리로 변경할 수는 없습니다. 반복문, `switch`와 Kotlin의 대상이 있는 `when (x)`, 같은 의미의 SQL 함수가 없는 문자열 함수(`strip()` 등) 같은 문법을 쓰면 lambdaQL은 쿼리를 실행하기 전에 오류를 냅니다.
언어별 차이는 9장([Java 노트](java/09-java-notes.md) | [Kotlin 노트](kotlin/09-kotlin-notes.md))을 참고하십시오.

<a id="fail-loudly"></a>
### 분석할 수 없는 람다 표현식

lambdaQL은 람다 표현식을 쿼리로 분석하지 못하면 `[LQL-2004]` 같은 코드가 붙은 예외를 던집니다([11. 오류와 언어](11-errors-and-languages.md) 참고).

거부되는 형태의 예:

| 형태 | Java | Kotlin |
|---|---|---|
| 조건에 따라 선택되는 값 | `o.getStatus() == (flag ? 1 : 2)` | `it.status == if (flag) 1 else 2` |
| 람다 표현식 안의 반복문이나 지역 변수 | 예, 거부됨 | 예, 거부됨 |
| 람다 표현식이 아닌 것 | 익명 클래스 | `object : ...` 식, 또는 Kotlin 함수 타입 값(`val f: (Order) -> Boolean`) |
| SQL에서 의미가 없는 메서드 호출 | 예, 거부됨 | 예, 거부됨(직접 만든 non-inline 확장 함수도 포함) |

예제 코드: [`ManualSharedTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualSharedTest.java)(`one-lambda-per-clause`, `agg`), [`ManualKotlinSharedTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSharedTest.kt)(`one-lambda-per-clause`, `agg`).

<a id="comparison-with-other-approaches"></a>
## 다른 방식과의 비교

| | JPQL / HQL 문자열 | JPA Criteria API | QueryDSL | lambdaQL |
|---|---|---|---|---|
| 쿼리 작성 방식 | 문자열 안의 텍스트 | Java 빌더 객체 | 생성된 Q-클래스 | 람다 표현식 |
| 컴파일러 검사 | ❌(실행 시점에 Hibernate가 검사) | ✅ | ✅ | ✅ |
| 생성 코드 필요 | ❌ | 선택(정적 메타모델) | ✅(Q-클래스) | ❌ |
| IDE 이름 변경에 안전 | ❌ | 정적 메타모델 사용 시 | ✅ | ✅ |
| 모양 | SQL | Java 빌더 코드 | SQL 유사한 Java 메소드 | SQL과 유사한 Java / Kotlin 람다 표현식 |
| Kotlin | 문자열 | 동작하지만 장황함 | 동작함 | 지원 |

<a id="next"></a>
## 다음

- 2. 시작하기: lambdaQL을 빌드하고 첫 쿼리를 작성합니다. [Java](java/02-getting-started.md) | [Kotlin](kotlin/02-getting-started.md)
- [매뉴얼 홈](README.md)
