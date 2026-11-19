<a id="10-rules-and-limits-kotlin"></a>
# 10. 규칙과 제한 (Kotlin)

> [!NOTE]
> **상태:** 일부 구현됨.
> 이 장은 Kotlin용 lambdaQL 설계의 규칙을 모은 것입니다. 람다가 SQL이 되는 방법, NULL이 동작하는 방법,
> Kotlin과 SQL이 다른 점, 데이터베이스마다 다른 점, 지원하지 않는 것을 다룹니다.
> 규칙은 H2 2.3, MariaDB 10.11, Oracle 23ai(Hibernate 6.6.5)에서 프로토타입으로 확인했고, Kotlin 바이트코드는 kotlinc 1.7.22 ~ 2.3.21로 확인했습니다.
> 현재는 분석 코어, `where`, 결과 함수(`toList()`, `toSet()`, `first()`, `one()`, `count()`, `exists()`), 정렬과 페이징의 핵심 기능, `SortSpec`, update/insert/delete/upsert([7](07-dml.md)), Spring Data JPA 모듈([12](12-spring-data-jpa.md))이 동작합니다(H2, MariaDB, Oracle에서 Java 람다로 테스트함).
> 서브쿼리([5](05-subquery.md))와 집계를 포함한 `select`([6](06-select-group.md)) 규칙도 DML을 포함해 구현되었습니다. 명시적 조인, 파생 테이블, CTE([6](06-select-group.md)), 프로시저와 DB 함수([8](08-procedures-functions.md)) 규칙도 구현되었습니다. 컬럼에 대한 DB 함수의 경고([10.2](#db-functions-on-a-column))도 구현되었습니다. 정렬 규칙 중에도 아직 구현되지 않은 것이 있습니다([4.12](04-order-paging.md#412-rules-summary-s1-to-s14) 참고).
> 전체 상태는 [트랙 README](README.md)를 참고하십시오.

[Kotlin 트랙](README.md) | [매뉴얼 홈](../README.md) | 이전: [9. Kotlin 노트](09-kotlin-notes.md) | 다음: [11. 오류와 언어](../11-errors-and-languages.md) | [이 페이지의 Java 버전](../java/10-rules-and-limits.md)

공통 페이지: [1. 개요](../01-overview.md) · [11. 오류와 언어](../11-errors-and-languages.md)

<a id="contents"></a>
## 목차

- [10.1 기본 규칙: 람다는 쿼리다](#101-the-base-rule-a-lambda-is-a-query)
- [10.2 SARGable 규칙(인덱스 친화적 SQL)](#102-sargable-rule-index-friendly-sql)
- [10.3 NULL 규칙](#103-null-rules)
- [10.4 연산자 규칙(R1 ~ R24)](#104-operator-rules-r1-to-r24)
- [10.5 Kotlin과 SQL의 차이](#105-kotlin-and-sql-differences)
- [10.6 데이터베이스별 차이](#106-database-differences)
- [10.7 지원하지 않는 것과 대신 작성할 것](#107-not-supported-and-what-to-write-instead)
- [10.8 캐시: 람다는 한 번만 분석됨](#108-caching-a-lambda-is-analyzed-once)
- [10.9 테스트 환경](#109-tested-environment)

예제 읽는 법:

- 예제 아래의 주석은 설계에 명시된 경우 lambdaQL이 만드는 HQL을 보여 줍니다.
- 람다에 직접 쓴 상수(숫자, 문자열, boolean, `null`, enum 상수)는 HQL에 리터럴로 씁니다.
  캡처 값과 계산한 값은 항상 바인드 파라미터(`:p1`, `:p2`, ...)입니다(D37).
- 엔티티: `Order`(`id`, `price`, `status`, `active`, `description`, `customer`, `updateAt`, `items`),
  `Customer`(`id`, `name`, `grade`), `Item`(`order`, `price`, `qty`). [2. 시작하기](02-getting-started.md#example-entities)를 참고하십시오.
  몇몇 행은 nullable 속성 `flag: Boolean?`을 추가로 사용합니다.

---

<a id="101-the-base-rule-a-lambda-is-a-query"></a>
## 10.1 기본 규칙: 람다는 쿼리다

lambdaQL은 **람다를 절대 실행하지 않습니다**. 바이트코드를 읽고 HQL을 씁니다.
람다는 Kotlin 문법으로 작성한 쿼리입니다. 따라서 다음과 같습니다.

1. `it.customer?.name` 같은 경로는 **컬럼을 가리킵니다**. 호출의 연쇄가 아닙니다.
   lambdaQL은 고객이 null인지 검사하지 않습니다(D20). `where`에서 경로는 기본적으로 inner join이므로 고객이 없는 주문은 반환되지 않습니다(D36).
   null 가드(`... == null || ...`)를 써도 마찬가지입니다. 경로는 inner join으로 남습니다(D48). 고객이 없는 주문을 유지하려면 `leftJoin`을 사용하십시오.
   그런 주문을 찾으려면 연관 자체를 비교하십시오: `it.customer == null`. `it.customer?.name == null`로는 반환되지 않습니다.
2. NULL은 **직접 작성한 곳에서만** 처리됩니다(`== null`, `!= null`, `?.`, `?:`)(D22).
   Kotlin 컴파일러가 추가하는 null 검사는 무시됩니다. 그 밖의 NULL은 SQL 규칙을 따릅니다. 조건은 UNKNOWN이 되고 행은 반환되지 않습니다.
3. `!!`는 무시됩니다(D23). Kotlin의 메모리 규칙(객체 동일성, 박싱)은 적용되지 않습니다. `==`와 `===`는 **값 동등성**을 뜻합니다(D12, R17).
4. **어떤 형태에 대한 규칙이 없으면 lambdaQL은 추측하지 않습니다.** 코드 `[LQL-xxxx]`와 소스 줄 번호가 담긴
   명확한 오류를 던집니다. "아마 맞을" SQL은 절대 만들지 않습니다.

<a id="102-sargable-rule-index-friendly-sql"></a>
## 10.2 SARGable 규칙(인덱스 친화적 SQL)

"SARGable"은 데이터베이스가 조건에 인덱스를 사용할 수 있다는 뜻입니다.
이는 lambdaQL의 기본 규칙입니다(설계 3.9절).

| # | 규칙 |
|---|---|
| 1 | **컬럼 쪽에 함수, 연산자, `CASE`를 두지 않습니다.** 값을 바꿔야 한다면 바인딩 전에 애플리케이션에서 파라미터를 바꿉니다(예: `Locale.ROOT`로 소문자 변환) |
| 2 | 의미가 같은 형태가 두 가지라면 단순한 비교와 `AND` / `OR` / `NOT`만 쓰는 형태를 고릅니다. 올바른 3값 논리가 먼저이고, 그 안에서 SARGable 형태를 고릅니다 |
| 3 | 의미상 컬럼에 함수가 꼭 필요하다면(예: 대소문자 무시) lambdaQL은 시작할 때 DB 메타데이터를 읽고 DB에 이미 있는 경로를 사용합니다. 컬럼 콜레이션, 함수 인덱스, 언어별(linguistic) 인덱스입니다. 경로가 없으면 시작할 때 컬럼마다 WARN을 기록합니다(엄격 모드: 오류) |
| 4 | 각 렌더링 규칙은 H2, MariaDB, Oracle에서 `EXPLAIN`으로 확인합니다 |

<a id="examples"></a>
### 예제

**대소문자 무시**(`equals(s, ignoreCase = true)`, `startsWith(s, ignoreCase = true)`; D24)

| DB에 있는 것 | lambdaQL이 쓰는 것 | 쓰지 않는 것 |
|---|---|---|
| 대소문자를 구분하지 않는 콜레이션(MariaDB `_ci`) | `e0.description = :p1` | `lower(e0.description) = lower(:p1)` |
| Oracle 함수 인덱스 `LOWER(description)` | `lower(e0.description) = :p1`(`:p1`은 애플리케이션에서 소문자로 변환) | `lower(e0.description) = lower(:p1)` |
| Oracle 언어별 인덱스 `NLSSORT(... BINARY_CI)` | `e0.description collate binary_ci = :p1` | |
| 경로 없음 | 시작할 때 WARN(엄격 모드: 오류) | |

**단순한 `=`와 `<>`를 쓰는 동등 비교**(결정 D48)

```kotlin
.where { it.description == text }   // 쿼리가 실행될 때 text는 null이 아님
// e0.description = :p1
.where { it.description != text }
// e0.description <> :p1               (description이 NULL이면 일치하지 않음)
```

lambdaQL은 `is [not] distinct from`을 쓰지 않습니다. 이 형태는 Oracle에서 `decode(...)`가 되어 인덱스를 사용할 수 없습니다.
엔티티 값은 SQL 의미를 따르므로 단순한 `=`나 `<>`가 항상 올바릅니다. 캡처 값 `null`은 NULL로 바인딩되어 어떤 행에도 일치하지 않습니다(`WARN` 수준 `LQL-4045`).

**두 boolean 식의 비교**(D25)

```kotlin
.where { it.active == (it.status!! > c) }
// (e0.active = true and e0.status > :p1) or (e0.active = false and e0.status <= :p1)
```

`CASE` 형태(`e0.active = case when ... end`)는 올바른 행을 반환하지만 전체 스캔이 필요하므로
lambdaQL은 이를 사용하지 않습니다.

**엘비스와 빈 문자열**

```kotlin
.where { (it.description ?: "") == "x" }     // e0.description = :p1
.where { (it.description ?: "") == "" }      // (e0.description = :p1 or o.description is null)
.where { it.description.isNullOrEmpty() }    // (o.description is null or e0.description = :p1)
```

**서브쿼리를 사용한 NOT IN**은 `NOT IN`으로 씁니다(SQL 의미, D48). 서브쿼리에 NULL이 있으면 행이 반환되지 않습니다. NULL을 건너뛰려면 서브쿼리에 null 검사를 추가하십시오([10.3](#103-null-rules) 참고).

<a id="what-the-measurements-showed"></a>
### 측정 결과

MariaDB 10.11과 Oracle 23ai, 20,000 ~ 50,000행:

| 형태 | MariaDB | Oracle | 괜찮은가? |
|---|---|---|---|
| `_ci` 컬럼에 대한 `name = 'abc'` | 인덱스 조회 | – | 예 |
| `LOWER(name) = LOWER('abc')` | 전체 인덱스 스캔(19,791행) | 전체 테이블 스캔(함수 인덱스 없음) | **아니요** |
| `lower(name) = :p_lowered` + 함수 인덱스 | – | 인덱스 범위 스캔(`LIKE 'ab%'`도 마찬가지) | 예 |
| `name COLLATE BINARY_CI = :p` + 언어별 인덱스 | – | 인덱스 범위 스캔 | 예 |
| `AND`/`OR`로 쓴 boolean `==` | 범위(350행) | 인덱스 범위 스캔 2번 + 비트맵 OR | 예 |
| `CASE`로 쓴 boolean `==` | 전체 인덱스 스캔(49,648행) | 전체 테이블 스캔 | **아니요** |

<a id="planned-items-under-the-same-rule"></a>
### 같은 규칙에 따라 계획된 항목

| 형태 | 계획된 SQL |
|---|---|
| "`updateAt`의 연도가 2024" 같은 날짜 함수 | `e0.updateAt >= :from and e0.updateAt < :to` |
| 함수 키로 정렬(`lower(name)`) | 먼저 접근 경로를 확인하고, 없으면 WARN |

<a id="db-functions-on-a-column"></a>
### 컬럼에 대한 DB 함수

컬럼 쪽의 DB 함수(`OrderFns.discount(it.price)!! > 10`)는 일반 인덱스를 사용할 수 없습니다.
lambdaQL은 **WARN**(`LQL-5001`)을 기록합니다. 예외: Oracle에서 같은 식의 함수 기반 인덱스가 있으면 기록하지 않습니다.
MariaDB에서 `NOT DETERMINISTIC`(기본값) 함수는 인자가 파라미터뿐이어도 전체 스캔이 되므로(`it.price!! > OrderFns.discount(rate)!!`) 이 경우에도 경고합니다(`LQL-5007`).
엄격 모드(경고 대신 오류)는 아직 구현되지 않았습니다.
[8. 프로시저와 DB 함수](08-procedures-functions.md)를 참고하십시오.

<a id="103-null-rules"></a>
## 10.3 NULL 규칙

| 람다 | SQL | NULL 동작 |
|---|---|---|
| `it.status == 1`(`Int?`와 상수) | `e0.status = 1` | NULL: UNKNOWN, 행이 반환되지 않음. 컴파일러가 숨겨 둔 null 검사는 무시됨(D22) |
| `it.status != 1` | `e0.status <> 1` | NULL: 행이 반환되지 **않음** |
| `it.price!! > p` | `e0.price > :p1` | NULL: 행이 반환되지 않음. `!!`는 무시됨(D23) |
| `it.status == q`(둘 다 nullable: `q: Int?`, `String?`, enum) | `e0.status = :p1` | 컬럼이 NULL: 반환되지 않음. 캡처 값 `null`: 일치하는 행 없음(`= NULL`, `WARN` 수준 `LQL-4045`) |
| `it.status != q`(둘 다 nullable) | `e0.status <> :p1` | 컬럼이 NULL: 반환되지 **않음**(SQL 의미, D48. Kotlin이라면 반환). 캡처 값 `null`: 일치하는 행 없음 |
| `it.description == it.custName`(엔티티 값 두 개) | `e0.description = e0.custName` | 어느 한쪽이 NULL: 반환되지 않음. NULL과 NULL도 마찬가지(SQL 의미, D48) |
| `it.description.equals(text)`(`String?`) | `==`와 같음 | `==`와 같음 |
| `x == null`, `x != null` | `is null`, `is not null` | 2값 |
| 값 객체(`@Embedded`) 전체: `it.address == addr`, `it.address != addr` | 컬럼마다 펼침: `e0.address.city = :p1 and e0.address.zip = :p2`, 부정은 `<> ... or ...`. `== null`은 모든 컬럼 `is null`(and), `!= null`은 `is not null`(or) | 컬럼마다 SQL 의미(D48): NULL 컬럼은 `=`에도 `<>`에도 불일치. 캡처한 객체가 `null`이면 일치하는 행 없음(`WARN` 수준 `LQL-4045`) |
| `it.id == id`(`it.id`는 `Long?`, `id`는 캡처한 `Long`), `it.id == null`: NULL이 될 수 없는 컬럼(id, 기본형, `nullable = false`, 경로에 연관 단계 없음) | `not (e0.id is null or e0.id <> :p1)` 대신 `e0.id = :p1`. `it.id == null`은 `1=0`(Java로 테스트함) | 컬럼에 NULL이 없음. 연관을 거치는 경로(`it.customer?.id`)는 검사 유지 |
| `!`, `&&`, `\|\|` | `NOT`, `AND`, `OR` | SQL 3값 논리 |
| `it.flag == true` / `it.flag != true` | `e0.flag = true` / `e0.flag <> true` | NULL: 반환되지 않음(SQL 의미, D48) |
| `it.flag ?: false`(조건) | `(e0.flag is not null and e0.flag = true)` | NULL은 false로 취급 |
| `where` 안의 `it.customer?.name`, `it.customer!!.name` | `j1.name` + `join e0.customer j1`(기본은 inner join) | 고객 없음: 주문이 반환되지 않음. `it.customer?.name == null`도 마찬가지 |
| `it.customer == null \|\| it.customer?.name == p` | `e0.customer is null or j1.name = :p1` + `join e0.customer j1` | 고객 없음: 반환되지 **않음**(inner join, D48. `DEBUG` 수준 `LQL-5009`). 유지하려면 `leftJoin` 사용 |
| 같은 경로를 `orderBy`나 `select`에만 씀 | `j1.name` + `left join e0.customer j1`. 필수 연관(`optional = false`)은 `join` | 고객 없음: 컬럼이 NULL |
| `it.customer == null`, `it.customer != null` | `e0.customer is null`, `e0.customer is not null`(외래 키 컬럼, 조인 없음) | 고객이 없는 주문 / 고객이 있는 주문 |
| `it.customer?.id` | `e0.customer.id`(외래 키 컬럼, 조인 없음) | 고객 없음: NULL |
| `a ?: b`(값) | 값에서는 `coalesce(a, b)`, 문자열 조건에서는 SARGable 형태 | [9.5](09-kotlin-notes.md#95-null-operators---and-) 참고 |
| `it.id in ids` | `e0.id in (:p1)` | NULL id: 반환 안 됨 |
| `it.id !in ids` | `e0.id not in (:p1)`. NULL이 될 수 있는 컬럼도 같음 | NULL 값: 반환되지 **않음**(SQL 의미, D48. `DEBUG` 수준 `LQL-5009`. Kotlin이라면 반환) |
| `it.status in 1..5`, `!in 1..5`, `when (it.status) { ... }` | `between`, `not (... between ...)`, `in (...)` | 대상이 NULL: 반환 안 됨. `NOT` 아래에서도 마찬가지(가드 없음, D22) |
| `x !in sub...` 형태의 서브쿼리 | `x not in (select ...)` | 서브쿼리에 NULL이 있으면 어떤 행도 반환되지 않음(SQL 의미, D48) |
| `Agg.sum`, `avg`, `min`, `max` | `sum(...)` ... | 행이 없거나 모든 값이 NULL이면 NULL |
| `Agg.count()` | `count(*)` | 행이 없으면 0 |

<a id="captured-null-values"></a>
### null 캡처 값

| 캡처 값이 있는 위치 | `null` 허용? |
|---|---|
| 두 nullable 값 사이의 `==` / `!=` | 예. 단, 일치하는 행이 없음(`= NULL`). 쿼리가 실행될 때 `LQL-4045`를 `WARN` 수준으로 기록 |
| 같은 값에 대한 null 가드 아래: `p == null \|\| it.status!! > p` | 예. 가드는 쿼리가 실행될 때 애플리케이션에서 결정합니다. `p == null`이면 필터 없음, 다른 값이면 `e0.status > :p1`. `!!`와 자기 기본형 언박싱만 있는 비교는 가드 위치와 상관없음 |
| `!!`와 함께 쓰거나 크기 비교(`<` `<=` `>` `>=`)에 사용: `it.price!! > p!!` | 예. 단, 일치하는 행이 없음(`> NULL`). `!!`(D23)와 자기 기본형 언박싱은 실행하지 않음. `LQL-4045`를 `DEBUG` 수준으로 기록 |
| 캡처 값에 대한 메서드 호출(`p.trim()`, 다른 기본형으로 바꾸는 `p.toInt()`) | **아니요.** 값을 계산할 때 오류(`LQL-4012`, Kotlin이라면 NPE) |
| `in`에 사용하는 컬렉션 안의 `null` 요소 | **아니요.** 바인딩할 때 오류 |
| `NaN`, `Infinity` | **아니요.** 항상 오류 |

<a id="104-operator-rules-r1-to-r24"></a>
## 10.4 연산자 규칙(R1 ~ R24)

설계(3.6절과 KOTLIN.md 5절)에 있는 규칙 표를 Kotlin 형태로 짧게 정리한 것입니다.

| # | 람다 형태 | 의미 |
|---|---|---|
| R1 | non-null 기본형의 비교, 또는 `!!` 뒤의 비교(`it.qty > 5`, `it.price!! > p`) | `=`, `<>`, `<`, ... SQL NULL 규칙 |
| R2 | 참조 비교로 컴파일되는 `==` / `!=`(enum `==`; `===`는 R17 참고) | 객체 동일성이 아니라 값 동등성. SQL 의미의 `=` / `<>`(D48) |
| R3 | `x == null`, `x != null` | `IS NULL`, `IS NOT NULL` |
| R6 | `!`, `&&`, `\|\|` | `NOT`, `AND`, `OR`. SQL 3값 논리 |
| R7 | 문자열 값 비교 | **DB 콜레이션**을 따름(대소문자, 끝의 공백). Oracle은 `''`을 NULL로 취급 |
| R8 | 엔티티 `==` 엔티티 | 계획 중: 식별자 동등성. v0.1에는 없음(KC13) |
| R9 | 캡처 값 | 실행 시점의 값을 가진 바인드 파라미터 |
| R10 | 속성 접근(`it.price`) | getter 호출. JPA 속성에 매핑됨 |
| R11 | `Intrinsics.areEqual`로 컴파일되는 Kotlin `==`, `!=`(참조, 또는 양쪽 모두 nullable) | 값 동등성: SQL 의미의 `=` / `<>`(D48). 캡처 값 `null`은 어떤 행에도 일치하지 않음 |
| R12 | nullable 기본형과 기본형(`it.id == 1L`, `id: Long?`) | 숨겨진 null 검사는 무시됨: `e0.id = 1`, `e0.id <> 1` |
| R13 | `!!`와 그 밖의 컴파일러 null 검사 | 무시됨(D23) |
| R14 | `?.` | NULL이 전파되는 경로 또는 값. `?.`가 없을 때와 같은 컬럼 |
| R15 | `?:` | 값에서는 `coalesce(a, b)`. 조건 안의 boolean 엘비스는 `(x is not null and x = true)`가 됨. 값으로 쓰는 boolean 엘비스는 거부됨(KC21) |
| R16 | 연관 조인 | R24로 대체됨 |
| R17 | `===`, `!==` | `==`처럼 값 동등성 |
| R18 | 범위 `in a..b`, `until`, `..<`, `downTo`, `!in` | `between`, `>= and <`, `not (...)`. 자동 null 가드 없음 |
| R19 | 컬렉션 `x in c`, `x !in c`(캡처한 `c`) | `x in (:p)`, `x not in (:p)`(SQL 의미, D48). null 요소: 오류. 빈 컬렉션: FALSE / TRUE |
| R20 | `true`/`false`로 끝나는 상수 분기를 가진 `when (subject)` | `in (...)` 또는 비교의 `OR` |
| R21 | Kotlin 문자열 함수(`startsWith`, `endsWith`, `contains`, `equals(ignoreCase)`, `lowercase`, `length`, `isNullOrEmpty`) | `like ... escape '\'`, D24 대소문자 무시 경로, `lower`, `length`, `(x is null or x = :p)` |
| R22 | Kotlin 호스트 값(캡처한 `var`, private 속성, `companion` 값) | 실행할 때마다 읽음. 쓰기는 거부됨 |
| R23 | 속성 참조 `Order::price` | getter와 같음. `orderBy`(`orderBy(Order::price)`)에서는 **아직 구현되지 않음**: `orderBy { it.price }`를 사용하십시오 |
| R24 | 연관 경로(`it.customer?.name`, `it.customer!!.name`, Java 엔티티의 `it.customer.name`) | 컬럼. to-one 연관: where, order by, select가 함께 사용하는 별칭. `where`에서는 항상 inner join(D48. 연관이 없는 행을 유지하려면 `leftJoin` 사용), order by나 select에만 쓰면 `left join`(필수 연관 `optional = false`는 inner join, A4). 임베디드: 같은 테이블. 대상의 `@Id`로 끝나거나 연관을 `null`과 비교: 외래 키 컬럼, 조인 없음. 컬렉션: 오류 |

Java 전용 규칙 R4(엔티티 값에 대한 `equals`)와 R5(Java null-safe 헬퍼)는 [Java 트랙](../java/10-rules-and-limits.md)에 있습니다. Kotlin에서는 `==`가 R5와 같은 의미를 갖습니다.

그 밖의 규칙:

- `x.compareTo(y) > 0`, `isBefore`, `isAfter`는 `x > y`, ...가 됩니다.
- 람다에 직접 쓴 상수(`const val` 포함)는 리터럴입니다: `it.price!! > 100.0`은 `e0.price > 100.0`, `"O'Brien"`은 `'O''Brien'`. 캡처 값과 계산한 값(`BigDecimal("1.5")`)은 바인드 파라미터입니다(D37).
- 컴파일러는 가끔 `!`의 위치를 옮깁니다(드 모르간, 이중 부정). 그래도 결과는 절대 바뀌지 않습니다.
  lambdaQL은 각 조건을 정확히 한 번씩 유지하며, 작성한 논리를 절대 "단순화"하지 않습니다.
- Kotlin에서 항상 참인 조건(`x > 5 || !(x > 5)`)을 작성해도 단순화하지 않습니다.
  SQL에서는 `x`가 NULL인 행이 빠집니다. 이것이 SQL 규칙입니다.

<a id="105-kotlin-and-sql-differences"></a>
## 10.5 Kotlin과 SQL의 차이

이 차이는 SQL 자체에서 옵니다. 컴파일러 문제가 아닙니다.
"G" 번호는 설계(2.7절)의 ID입니다.

| # | 차이 | lambdaQL의 처리 |
|---|---|---|
| G1 | Kotlin에서 `null != 3`은 true, SQL의 `status <> 3`은 UNKNOWN | `!=`는 `<>`로 렌더링: SQL 의미(D48). NULL 행은 제외되고 `LQL-5009`를 `DEBUG` 수준으로 기록. 이런 행을 유지하려면 `it.status == null \|\| ...`이라고 쓰십시오 |
| G2 | `it.flag != true`와 Kotlin | `e0.flag <> true`로 렌더링: NULL Boolean은 일치하지 않음(SQL 의미) |
| G3 | NULL 파라미터로 `name = ?`를 실행하면 0행 반환 | 그대로 유지(SQL 의미, D48). 실행할 때 `LQL-4045`를 `WARN` 수준으로 기록. NULL을 찾으려면 `x == null`이라고 쓰십시오 |
| G4 | `status NOT IN (1, NULL)`은 항상 0행 반환 | 캡처한 컬렉션 안의 `null`은 오류. 서브쿼리 `!in`은 SQL 의미를 유지(D48): 서브쿼리에 null 검사를 추가하십시오 |
| G5 | Kotlin은 NPE를 던지지만(`!!`, 언박싱) SQL은 그냥 비교 | 문서화: 엔티티의 NULL은 SQL 규칙을 따름 |
| G6 | `NaN`: H2는 NaN을 가장 큰 값으로 정렬하고 `NaN = NaN`이 true, MariaDB는 저장할 수 없음, Oracle은 `BINARY_DOUBLE`에만 저장 가능 | 캡처한 NaN이나 Infinity는 오류. 저장된 NaN은 DB를 따름 |
| G7 | `Float` 컬럼과 `Double` 값의 비교: DB마다 결과가 다름 | `Float`/`Double`에 대해 **Kotlin과 비트 단위까지 같은 결과는 약속하지 않음**. lambdaQL은 `CAST`를 추가하지 않음. 컬럼 매핑을 직접 선택(예: Oracle `binary_float` / `binary_double`) |
| G9 | `x > 5 \|\| !(x > 5)`에서 NULL 행이 빠짐 | 단순화하지 않고 작성한 그대로 렌더링 |
| G10 | SQL은 평가 순서를 약속하지 않음: `qty != 0 && total / qty > 2`도 0으로 나눌 수 있음 | 가드 아래의 `/`, `%`, `CAST`는 v0.1에서 거부(D13) |
| G11 | 문자열: MariaDB `_ci`는 대소문자와 끝의 공백을 무시, Oracle은 `''`을 NULL로 저장, Oracle `NLS_SORT`는 JVM 로캘을 따름 | 문서화: 문자열 비교와 정렬은 DB를 따름. Oracle에서는 `NLS_SORT=BINARY`로 설정(세션 초기화 또는 `-Duser.language=en`). Oracle에서는 `it.description == ""`와 `isEmpty()`가 다르게 동작 |
| G12 | Kotlin의 `===`는 동일성(값이 1000인 두 `Long` 박스는 false) | lambdaQL은 `===`와 `==`를 값 동등성으로 정의 |
| G13 | 0/1 이외의 값을 담은 레거시 boolean 컬럼(MariaDB `TINYINT(1)`, Oracle `NUMBER(1)`)은 다르게 읽힘 | Hibernate 자체 DDL(`bit(1)`, Oracle 23 `boolean`)은 문제없음. 레거시 스키마는 주의 |
| G14 | Oracle 23 버그: 복합 조건에 대한 `WHERE (a AND b) = false` / `IS FALSE` / `IS NULL`에서 행이 누락됨 | lambdaQL은 복합 조건을 boolean 테스트로 절대 감싸지 않음. 부정은 항상 `NOT(...)` |
| G15 | Oracle `CHAR` 컬럼은 `String` 바인드와 일치하지 않음 | 문서화: 엔티티 매핑에 주의 |

그 밖의 차이:

| 차이 | Kotlin | SQL | lambdaQL |
|---|---|---|---|
| `BigDecimal` 동등성 | `==`는 스케일까지 비교(`1.0`과 `1.00`은 false) | `=`는 값을 비교 | 값 동등성 |
| 정수 나눗셈 | `5 / 2`는 `2` | MariaDB는 `2.5` | DB에 따라 다름. 최종 규칙은 정해지지 않았으며 v0.1에서는 거부될 수 있음 |
| `BigDecimal` 나눗셈과 함수 결과 | Kotlin 스케일 규칙(`HALF_EVEN`) | 스케일은 DB가 결정(예: 함수 결과에 대해 H2는 1, MariaDB와 Oracle은 0) | 문서화 |
| 유니코드 대소문자 무시(`ſ`, `İ`) | Kotlin 규칙 | DB 콜레이션 | DB를 따름 |
| 문자열 길이 | UTF-16 단위 | H2는 UTF-16 단위, MariaDB는 코드 포인트 | 문서화 |
| NULL이 들어간 문자열 템플릿 | `"null"` 텍스트 | NULL(Oracle: `NULL \|\| 'x'`는 `'x'`) | 문자열 결합은 계획 중이며 v0.1에는 없음 |
| nullable `when` / 범위 대상 | `else`로 감. `!in`은 true | 반환 안 됨. `NOT` 아래에서도 마찬가지 | SQL 의미(D22) |
| non-null 타입 `String` | 절대 null이 아님 | 컬럼은 NULL일 수 있음 | lambdaQL은 JPA 메타데이터를 사용 |
| 람다 안의 `LocalDateTime.now()` | 애플리케이션 시간 | – | **애플리케이션 시간**으로 바인딩. DB 시간(`current_timestamp`)은 지원하지 않음 |
| `Set`의 같은 요소 | `equals` / `hashCode`가 결정(`data class`는 주 생성자의 속성) | 기본 키로 행을 구분 | Set의 크기가 행 수보다 작으면 `toSet()`과 `findSet()`이 `IllegalStateException`을 던짐(엔티티의 `equals` / `hashCode`가 서로 다른 행을 같다고 판단하거나 `@Id`가 유일하지 않은 경우, 예: 뷰). `toList()` 사용(D42, Java로 테스트함) |

<a id="106-database-differences"></a>
## 10.6 데이터베이스별 차이

lambdaQL은 많은 차이를 감춥니다. 일부는 알아 두어야 합니다.

<a id="sorting-and-paging"></a>
### 정렬과 페이징

| 항목 | H2 | MariaDB | Oracle | lambdaQL |
|---|---|---|---|---|
| NULL 위치, `ASC` | 처음 | 처음 | **마지막** | 모든 DB에서 `ASC`는 NULLS FIRST(D16) |
| NULL 위치, `DESC` | 마지막 | 마지막 | **처음** | 모든 DB에서 `DESC`는 NULLS LAST(D16) |
| `NULLS FIRST/LAST` 문법 | 있음 | **없음**(문법 오류) | 있음 | MariaDB에서 Hibernate가 `CASE`로 흉내 냄: 정렬에 **인덱스 사용 불가** |
| 페이징 SQL | `offset ? rows fetch first ? rows only` | `limit ?,?` | `offset ? rows fetch first ? rows only` | Hibernate에 전달(`setFirstResult` / `setMaxResults`) |
| 같은 값을 가진 행의 순서 | – | – | 무작위 | 페이징할 때 키에 id가 없으면 lambdaQL이 루트의 `@Id asc`를 추가. 유일하고 NULL이 될 수 없는 키(`unique = true`이고 `nullable = false`, `@NaturalId`)가 있으면 추가하지 않음(A5) |
| 문자열 순서 | 대소문자 구분 | `_ci` | `NLS_SORT`(JVM 로캘) | DB를 따름 |
| `order by`의 암시적 경로 | inner join(행 누락) | 같음 | 같음 | lambdaQL은 `left join`을 사용(필수 연관은 inner join)(또는 같은 경로의 `where` 조인) |
| `order by`, `distinct`, `group by`의 `@Lob` 컬럼 | 동작 | 동작 | **ORA-22848 / ORA-22849** | 쿼리를 바꾸지 않고 `LQL-5010`을 `DEBUG` 수준으로 기록(B3) |
| `@Enumerated(EnumType.STRING)` enum 컬럼의 정렬 | 이름 순 | 이름 순 | 이름 순 | 쿼리를 바꾸지 않고 `LQL-5011`을 `DEBUG` 수준으로 기록(B2). 선언 순서는 `ORDINAL` 매핑에서만 |

lambdaQL은 NULL이 될 수 있는 모든 키에 대해 HQL에 `nulls first` / `nulls last`를 씁니다. 필수 연관만 거치고 NULL이 될 수 없는 경로에는 쓰지 않습니다. 데이터베이스 기본값과 같으면 Hibernate가 이를 뺍니다.
기본값(`ASC` = NULLS FIRST, `DESC` = NULLS LAST)은 H2, MariaDB와 같으므로 이 DB들에서는 추가 SQL이
필요 없습니다. Oracle에만 `NULLS` 절이 붙습니다. NULL 위치를 바꾸려면
`by.asc(key, false)`(NULL이 마지막)나 `by.desc(key, true)`(NULL이 처음)를 사용하십시오(D35). MariaDB에서는 이들이 `CASE` 흉내를 사용합니다.

<a id="conditions-and-values"></a>
### 조건과 값

| 항목 | H2 | MariaDB | Oracle |
|---|---|---|---|
| `is not distinct from` | 기본 지원 | `<=>`(인덱스 사용) | `decode(...)`(인덱스 사용 불가). lambdaQL은 이 형태를 쓰지 않음(D48) |
| `is [not] true`, `coalesce(boolean, false)` | 동작 | 동작 | **ORA-00932**. lambdaQL은 절대 쓰지 않음 |
| 빈 문자열 `''` | 값 | 값 | **NULL** |
| 대소문자와 끝의 공백 | 정확히 비교 | `_ci`: 무시 | 정확히 비교(정렬에는 `NLS_SORT`) |
| `NaN` | 저장됨, 가장 큰 값 | 저장할 수 없음 | `BINARY_DOUBLE`에만 |
| `Float` 컬럼 | REAL 리터럴 규칙 | double로 넓힘 | Hibernate는 `Float`를 `float(24)`(십진수)로 매핑 |
| 정수 `/` | 정수 | **소수**(`5/2 = 2.5`) | – |
| 큰 `in` 목록(값 1,500개) | 동작 | 동작 | 23ai에서 동작(이전 Oracle 버전의 1,000개 제한은 측정하지 않음) |

<a id="subqueries"></a>
### 서브쿼리

| 항목 | 일어나는 일 | lambdaQL |
|---|---|---|
| `limit` 없는 서브쿼리 `order by` | Oracle: 문법 오류(ORA-00907). MariaDB: 무시됨 | WARN과 함께 제거 |
| MariaDB `IN (... limit n)` | 지원하지 않음(오류 1235) | 자동으로 감쌈: `in (select x.id from (... limit n) x)` |
| MariaDB `lateral` | 잘못된 결과(흉내 구현) | `row_number() over (...)`로 다시 작성(아직 구현되지 않음) |

<a id="update-insert-delete"></a>
### 수정, 삽입, 삭제

| 항목 | 일어나는 일 | lambdaQL |
|---|---|---|
| `SET a = ..., b = a + 1`(설정한 뒤에 그 컬럼을 읽음) | MariaDB는 **새** 값을, H2와 Oracle은 이전 값을 읽음 | 분석할 때 오류(U3) |
| DML 안의 연관 경로(`it.customer?.name`) | Hibernate가 DB마다 다른 조인을 만들고 고객이 없는 **행을 놓침** | lambdaQL은 상관 스칼라 서브쿼리를 씀(`where`와 SET). `where`에서 조회라면 inner join할 경로에는 `customer.id is not null`도 더함. 그래서 DML이 쿼리와 같은 행에 적용됨(U9, 테스트함) |
| 같은 테이블에 대한 서브쿼리가 있는 `DELETE` | MariaDB 오류 1093 | 대상 id를 파생 테이블로 먼저 읽음: `d0.id in (select t.id from (select e0.id as id … where <condition>) t)`(U17). 상관 서브쿼리에도 동작하고 세 DB에서 같은 행을 삭제(실측). `update`는 감싸지 않음(MariaDB에서도 동작) |
| 같은 enum 값을 STRING 컬럼과 ORDINAL 컬럼에 한 번만 바인딩 | H2와 MariaDB가 **오류 없이 잘못된 값**을 저장 | 위치마다 파라미터 하나. 절대 공유하지 않음(U7) |
| `values(list) { }`의 많은 행 | Oracle: 바인드 65,535개의 절대 제한(ORA-01745), 그 훨씬 전부터 매우 느림 | 문장당 값 500개 이하로 나누고, 모든 문장을 한 트랜잭션에서 실행(U13) |
| `order by ... limit`이 있는 `update` / `delete` | MariaDB는 `in` 서브쿼리의 `limit`(오류 1235)과 대상 테이블을 읽는 서브쿼리(오류 1093)를 거부 | `e0.id in (select t.id from (select x0.id as id ... order by ... limit n) t)`: 파생 테이블 `t`로 세 데이터베이스 모두에서 동작. 안쪽 select가 조회의 조인(D36)을 사용하므로 조회와 같은 행을 대상으로 함(Java로 테스트함) |
| `on conflict` / upsert | H2와 Oracle은 `merge`. MariaDB는 `insert ... on duplicate key update`이고, 수정된 행을 2로 셈. Hibernate의 `do nothing`은 H2와 Oracle에서 실패 | `upsert`와 `onConflict(...).update(...)`는 Hibernate의 형태를 사용. `ignore()`는 `insert ... select ... where not exists`로 씀. 행 수는 그대로 반환(D40, 테스트함) |

<a id="procedures-and-functions"></a>
### 프로시저와 함수

| 항목 | H2 | MariaDB | Oracle |
|---|---|---|---|
| OUT / INOUT 파라미터 | **없음** | 있음 | 있음 |
| 여러 결과 집합 | 없음 | 있음 | 있음(REF CURSOR) |
| 프로시저가 영향을 준 행 수 | – | – | 항상 -1이므로 lambdaQL은 제공하지 않음. OUT 파라미터를 사용 |
| boolean 파라미터와 결과 | – | 있음 | 23ai 이상 |
| 가상 컬럼 / 함수 인덱스 안의 저장 함수 | – | 불가능(오류 1901) | 가능(함수 기반 인덱스) |

H2에는 OUT 파라미터가 없으므로 프로시저 기능은 MariaDB와 Oracle에서 확인합니다.

<a id="107-not-supported-and-what-to-write-instead"></a>
## 10.7 지원하지 않는 것과 대신 작성할 것

lambdaQL은 이 목록의 모든 형태에 대해 명확한 오류를 던집니다. "계획 중"은 이후 버전을 뜻합니다.

<a id="lambda-form-and-loading"></a>
### 람다 형태와 로드

| 지원하지 않음 | 대신 작성할 것 |
|---|---|
| 객체 식(`object : ...`), `Serializable`이 아닌 함수형 인터페이스(KC5) | 람다 리터럴 |
| Kotlin 함수 타입으로 저장한 람다(`val f: (Order) -> Boolean`)(KC4) | 람다 리터럴을 전달 |
| `-Xsam-conversions=class`(KC1), `inline fun` 본문 안에서 만든 람다(KC2) | [9.14](09-kotlin-notes.md#914-kotlin-limits-kc1-to-kc21) 참고 |
| 직접 만든 Kotlin `fun interface`에 대한 호출 가능 참조(KC3) | 람다 리터럴 |
| 조건, 정렬, select 람다 안의 지역 `val`/`var`, 반복문, 여러 문장 | 식 하나. 값은 람다 밖에서 계산 |
| 캡처 변수에 쓰기(`n++`)(KC12) | 람다 밖에서 계산 |
| `@JvmField`, `internal`, `value class`, `@get:JvmName` 속성(KC15, KC16) | public 일반 속성 |
| 번들된 ASM이 모르는 더 새로운 JDK용으로 컴파일된 람다 | ASM 업데이트(lambdaQL은 ASM을 셰이딩하지 않음) |

<a id="conditions"></a>
### 조건

| 지원하지 않음 | 대신 작성할 것 |
|---|---|
| 값 `if` / `when` / `takeIf`, 일반 `if (c) x else y`(KC6) | `&&` / `\|\|`. 값은 람다 밖에서 선택. `CASE WHEN`은 계획 중 |
| (지원됨: `if (c) x else false`, `if (c) true else x`, `when { a -> true; ...; else -> false }`) | 각각 `AND`, `OR`를 뜻함 |
| boolean 중위 함수 `and`, `or`, `xor` | `&&`, `\|\|` |
| 가드 아래의 `/`, `%`, `CAST`(`it.qty != 0 && it.price!! / it.qty > 2`) | 나눗셈 없이 비교: `it.qty > 0 && it.price!! > 2.0 * it.qty` |
| 엔티티 `==` 엔티티(`it.customer == c`)(KC13) | `it.customer?.id == c.id`(계획 중: 식별자 동등성) |
| 값 객체(`@Embedded`)의 크기 비교, 다른 타입과의 비교(`LQL-3012`) | 필드를 비교. 같은 타입과의 `==`, `!=`, `null` 비교는 지원 |
| 컬렉션 경로(`it.items...`, `@OneToMany`, `@ManyToMany`) | `exists()` 서브쿼리 또는 조인 |
| `item in it.items` | `exists()` 서브쿼리 |
| 람다 안에서 만든 컬렉션(`listOf(1, 2)`, `setOf(...)`)(KC8) | 밖에서 만들어 캡처. (`groupBy` 키는 예외) |
| 범위의 `step` | 비교 두 개 또는 캡처한 컬렉션 |
| 캡처한 컬렉션 안의 `null` 요소 | null을 제거. NULL 행이 필요하면 `\|\| it.x == null`을 추가 |
| 캡처한 `NaN` / `Infinity` | 쿼리 전에 값을 검사 |
| `isNaN()`(KC14) | – |
| 엔티티 값에 대한 그 밖의 함수 호출(직접 만든 non-inline 함수, `operator` 함수)(KC11) | 속성으로 조건을 작성하거나 함수를 `inline`으로 만듦 |
| 엔티티 값을 사용한 문자열 템플릿이나 `+`(KC17) | 계획 중 |
| 엔티티 경로에 대한 `it.tags["k"]`, `it.lines[0]` | – |

<a id="functions-and-strings"></a>
### 함수와 문자열

| 지원하지 않음(v0.1) | 대신 작성할 것 |
|---|---|
| `isBlank`, `isNullOrBlank`(KC9) | `isNullOrEmpty()` 또는 직접 비교 |
| 컬럼에 대한 `trim`, `coerceAtLeast`, `toLocalDate` 및 그 밖의 stdlib 함수(KC20) | 직접 비교 또는 DB 함수(`@SqlFunction`). 함수 매핑은 계획 중 |
| 날짜 함수(`YEAR(col) == 2024`) | 범위: `it.updateAt!! >= from && it.updateAt!! < to` |
| 제네릭 반환 타입(`fun <X> nvl(a: X, b: X): X`), 엔티티, 컬렉션, `Unit`을 반환하는 `@SqlFunction` | 다음 중 하나: `Double`, `Integer`(`Int`), `Long`, `BigDecimal`, `String`, `LocalDate`, `LocalDateTime`, `Boolean` |
| Hibernate 내장 함수와 이름이 같은 `@SqlFunction`(`COALESCE`, `ROUND`, `SYSDATE`, ...) | 접두사나 스키마 이름을 사용(`PUBLIC.SQ_FN`) |
| 기본 인수, `inline`, `vararg`가 있는 `@SqlFunction` | 파라미터가 고정된 일반 함수 |
| DB 함수로서의 확장 함수, 함수형 인터페이스 상수, 함수 타입 속성 | 인터페이스 `companion object` + `@JvmStatic` |
| 사용자 정의 집계 함수나 윈도 함수 | 계획 중 |
| 쿼리 람다 밖이나 `where` 안에서 `Agg.*` 호출 | `select` / `having`에서 사용 |

<a id="sort-and-paging"></a>
### 정렬과 페이징

| 지원하지 않음 | 대신 작성할 것 |
|---|---|
| 정렬 람다 안의 조건이나 반복문(`if (x) by.asc(...)`) | 쿼리를 두 개 만들거나 `Direction`을 캡처 값으로 전달: `by.sort(o.price, dir)` |
| `orderBy` 두 번, `orderBy` 뒤의 `where`, `limit` 뒤의 `orderBy` | 모든 키를 담은 `orderBy` 하나(이 형태들은 컴파일되지 않음) |
| 정렬 키로 쓴 엔티티(`by.desc(o.customer)`) | 컬럼: `by.desc(o.customer?.name)`(컴파일되지 않음) |
| 조인 정렬 람다 안의 속성 참조 | 람다 |
| `orderBy`의 속성 참조(`orderBy(Order::price)`, 아직 구현되지 않음) | 람다: `orderBy { it.price }` |
| 정렬 이름과 방향을 함께 담은 문자열 하나(`"price,desc_nulls_last"`), 아직 구현되지 않음 | 방향은 `Direction.parse(text)`, 이름은 `SortSpec.ofPath`. 이름은 정렬 정책이 검사([4.10](04-order-paging.md#sort-policy)) |
| `limit` / `offset`과 함께 쓴 컬렉션 fetch join | 먼저 페이징하고, 그다음 컬렉션을 로드 |
| Spring `Sort`의 `ignoreCase()`([12.5](12-spring-data-jpa.md#125-spring-sort-and-pageable)), 아직 지원하지 않음 | `ignoreCase()` 없는 정렬 |
| 함수 키(`lower(name)`) | 계획 중 |
| 집계 별칭으로 정렬(`by.desc(s.total)`) | 계획 중 |
| 정렬 키로 쓴 boolean 엘비스(KC21) | 키 `o.flag`에 NULL 위치를 지정: `by.asc(o.flag, false)`(NULL이 마지막) |

<a id="subqueries"></a>
### 서브쿼리

| 지원하지 않음 | 대신 작성할 것 |
|---|---|
| 한 행이 아닌 스칼라 비교에서의 `first()` | 집계(`max`, `min`, `avg`, `sum`, `count`) 또는 `orderBy { }.limit(1)...first()` 사용 |
| `limit` 없는 서브쿼리 안의 `orderBy` | 효과가 없으므로 제거됨(WARN) |

<a id="select-and-group-by"></a>
### 조회와 그룹화

| 지원하지 않음 | 대신 작성할 것 |
|---|---|
| `select` / `having` 안에서 `groupBy` 키가 아닌 비집계 값 | `groupBy`에 추가 |
| 파생 테이블이나 CTE의 행 타입으로 쓴 일반 클래스 | Kotlin data class(또는 Java record) |
| `groupBy` 키로 쓴 `arrayOf(...)` / `setOf(...)` | `listOf(...)`, `Pair`, `Triple` |
| select 값으로 쓴 boolean 엘비스(KC21) | `it.flag`를 조회하고 NULL은 Kotlin에서 처리 |

<a id="update-insert-delete"></a>
### 수정, 삽입, 삭제

| 지원하지 않음(v0.1) | 대신 작성할 것 |
|---|---|
| `where` 없는 `update` / `delete` | `.all()`을 추가(없으면 `execute()`가 컴파일되지 않음) |
| 같은 문장에서 설정한 뒤에 그 컬럼을 읽음 | 의도적으로 이전 값을 사용하거나 update를 두 번 실행 |
| 같은 컬럼을 두 번 설정 | 대입 하나 |
| 조건부 값(`if (c) a else b`, `when`, 값으로 쓴 토글 `!it.active`) | 계획 중(`CASE WHEN`). `where`가 다른 update 두 개를 사용 |
| 설정할 컬럼을 고르기 위한 `set` 람다 안의 `if` | `set()`을 여러 번 호출 |
| `set` 안의 `++`, `--`, `apply`, `with`, `also` | `it.cnt += 1`, 직접 대입 |
| 대입 람다 안의 지역 `val` | 각 위치에 식을 작성 |
| 연관의 `@Id`가 아닌 속성을 설정(`it.customer?.name = x`) | `Customer`를 직접 수정 |
| 선택적이지 않은 컬럼(`@Id`, `nullable = false`, 기본형)에 `null` | – |
| `@Version` 속성을 직접 쓰기 | lambdaQL이 대신 `update versioned`를 사용 |
| 값 안의 문자열 템플릿 | 계획 중 |
| 명시적 조인이 있는 `insert ... select` | 계획 중. v0.1에서는 다대일 경로를 사용 |
| 동시 삽입에서 원자적인 `onConflict { ... }.ignore()` | 키 중복 오류를 처리(`ignore()`는 `not exists`를 사용. [7.6](07-6-upsert.md) 참고) |
| `insert ... select`로 하는 업서트 | `values(list) { ... }`로 행마다 업서트 |
| `limit(n)` 없이 update나 delete에 `orderBy { ... }` 지정 | `limit(n)`을 추가하거나 `orderBy`를 제거(그 외에는 `IllegalStateException`) |
| 한 행만 변경해야 하는 update나 delete를 `execute() == 1`과 롤백으로 검사 | `executeOne()`: id를 먼저 최대 2개 읽음. 2개이면 아무것도 변경하지 않고 `NonUniqueResultException`. 1개이면 `id = :p and (조건)`으로 그 행을 변경. 2행 이상을 변경하지 않음(D41, Java로 테스트함) |
| `executeOne()`과 `orderBy` 또는 `limit`을 함께 사용 | 둘 중 하나만 사용(그 외에는 `IllegalStateException`) |
| update나 delete의 `offset` | 계획 없음(D42). DML에는 `limit(n)`만 있음. `where { }`, `orderBy { }`, `limit(n)`으로 대상 행을 지정 |
| 복합 키 엔티티의 `limit` 또는 `executeOne()` | 아직 지원하지 않음. 먼저 id를 조회한 뒤 `delete ... where { it.id in ids }` |
| Spring 공유 `EntityManager`로 트랜잭션 없이 DML 실행(lambdaQL에는 `@Transactional`이 없음, D41) | `@Transactional` 또는 `TransactionTemplate` 안에서 호출 |
| 리포지토리 메서드 `deleteAll { ... }`(벌크 삭제)에서 cascade나 `@PreRemove` 기대 | Spring Data의 `deleteAll(Iterable)` 또는 `delete(entity)` 사용([12.4](12-spring-data-jpa.md#deleteallwhere-is-not-spring-datas-deleteall) 참고) |
| `returning` | DML 후에 다시 조회 |
| DB 시간(`current_timestamp`) | 애플리케이션 시간을 전달(`LocalDateTime.now()`는 값으로 바인딩됨) |
| `TABLE` id 생성기를 사용하는 `insert ... select` | `values { }` 또는 IDENTITY / SEQUENCE 사용 |
| JOINED 또는 TABLE_PER_CLASS 하위 클래스에 `insert` | 지원하지 않음(Hibernate 6.6.5가 실패함) |

DML에 대해 하나 더 알아 둘 점: 벌크 DML은 영속성 컨텍스트를 거치지 않습니다. Hibernate가 먼저 flush하지만,
같은 세션의 관리 엔티티는 그 뒤에도 이전 값을 유지합니다. `clear()`(또는 `refresh`)를 호출하십시오.
JPA cascade와 `orphanRemoval`은 적용되지 않습니다.
DML 문장에는 트랜잭션이 필요합니다. `QueryBuilder(EntityManagerFactory)`는 자체 트랜잭션을 엽니다. `EntityManager`를 쓰는 빌더(Spring의 공유 `EntityManager` 포함)는 호출자의 트랜잭션을 사용하며, 트랜잭션이 없으면 SQL을 보내기 전에 실패합니다.

<a id="procedures"></a>
### 프로시저

| 지원하지 않음 | 대신 작성할 것 |
|---|---|
| 람다 안에서 스텁 함수 호출 하나 외의 모든 것(로그, 중첩 호출, 분기) | 호출 하나. 인수는 미리 계산 |
| 계산이 들어간 이름 있는 인수(`calc(rate = r * 2, id = id)`) | 위치 인수를 사용하거나 값을 미리 계산 |
| OUT 파라미터가 있는 프로시저에 `exec` | `call` |
| 인수에 대한 DB 기본값 | 모든 인수를 전달 |
| 스텁 함수의 Kotlin 기본 인수 | 모든 인수를 전달하고 기본값을 제거 |
| Oracle 패키지 프로시저의 오버로드 하나를 골라 호출(오버로드가 있는 이름은 검출함: `LQL-4044`), 동적 프로시저 이름 | 계획 중 |
| Oracle 암시적 결과(`DBMS_SQL.RETURN_RESULT`), MariaDB 패키지, 프로시저 결과의 페이징 | 지원하지 않음 |

<a id="108-caching-a-lambda-is-analyzed-once"></a>
## 10.8 캐시: 람다는 한 번만 분석됨

바이트코드 읽기는 느린 부분이므로 lambdaQL은 이를 **람다마다 한 번만** 합니다.

| 항목 | 규칙 |
|---|---|
| 캐시하는 것 | 분석 결과: HQL 템플릿과 파라미터 출처 목록. 실패도 캐시함 |
| 캐시 키 | 람다의 구현 클래스와 메서드(이름, 시그니처, 종류), 람다의 타입, 엔티티 클래스, **ClassLoader** |
| 크기 | 제한됨(LRU). 그래서 끝없이 커지지 않고 클래스 로더를 누수하지 않음 |
| 캐시하지 않는 것 | 바이트코드 자체(다시 읽기는 싸고 보관하기에는 큼) |
| 캡처 값 | **절대 캐시하지 않음.** 쿼리가 실행될 때마다 읽어서 바인딩함 |
| 캡처한 객체에 대한 호출(`filter.minPrice`, `this.minStatus`, `LocalDateTime.of(...)`) | 쿼리가 실행될 **때마다** 애플리케이션에서 실행한 다음 파라미터 하나로 바인딩 |
| 캡처한 `var` | 쿼리가 실행될 때마다 읽음 |
| 캡처 값만 쓰는 조건(`p != null && ...`) | 상수로 접지 않고 `:p1 is not null`로 유지하므로 캐시가 올바르게 유지됨 |
| 캡처 값으로 쓴 `Direction` | 바인드 값이며 매번 읽음(구조는 캐시된 상태로 유지) |
| DML | 람다 클래스마다 한 번 분석. `set()` 호출 사이의 검사는 작은 요약을 사용 |

Kotlin 람다 이름(`...$lambda$0`)은 컴파일러 버전이나 소스를 수정할 때 바뀔 수 있습니다.
문제없습니다. 캐시 키에는 클래스, 시그니처, 클래스 로더도 들어 있고, lambdaQL은 이름 형식에 의존하지 않습니다.

프로토타입으로 측정한 결과(JDK 21): 새 람다의 첫 분석에는 약 **0.2 ms**가 걸립니다.
그 뒤로 `set()` 호출이 두 개인 update는 캐시 조회, 값 추출, 검사에 약 **0.1 ~ 0.2 µs**가 들며,
200만 번 실행한 뒤에도 같습니다. 목표: 같은 람다를 1,000번 호출해도 바이트코드는 한 번만 읽습니다.
Hibernate도 HQL 문자열을 키로 쿼리 계획을 캐시합니다.

여러분에게 의미하는 것:

```kotlin
// 좋음: 람다는 하나이고 값만 바뀜
.where { it.status == status }

// v0.1에서는 불가능: 람다 안에서 쿼리의 모양이 바뀜
.where { if (flag) it.status == 1 else it.active }

// 대신: 람다 밖에서 람다를 선택(람다 두 개, 각각 한 번만 분석됨)
val list = if (flag) {
    qb.from(Order::class.java).where { it.status == 1 }.toList()
} else {
    qb.from(Order::class.java).where { it.active }.toList()
}
```

<a id="109-tested-environment"></a>
## 10.9 테스트 환경

| 항목 | 버전 |
|---|---|
| Kotlin | 2.x (설계는 kotlinc 1.7.22, 2.1.10, 2.2.21, 2.3.0, 2.3.21로 확인) |
| Java (JVM) | 21 |
| Hibernate | 6.6 (설계는 6.6.5로 확인). HQL 확장(예: `function(...)`, `nulls first`)을 사용하므로 다른 JPA 구현체는 보장하지 않음 |
| 데이터베이스 | H2 2.3, MariaDB 10.11, Oracle 23.26 (23ai) |
| Spring | Spring Boot 3.4.2, Spring Data JPA 3.4.2(Spring 모듈, [12](12-spring-data-jpa.md); Java 리포지토리만). Spring Boot 4는 아직 검증하지 않음 |
| Kotlin 행 단위 확인 | 지금까지는 H2만. Kotlin 사례의 MariaDB와 Oracle 실행은 계획 중 |
| 테스트하지 않음 | 2.3보다 새로운 Kotlin, Kotlin Multiplatform, PostgreSQL, SQL Server, Oracle 19c 이하(예: PL/SQL `BOOLEAN` 파라미터) |

---

[Kotlin 트랙](README.md) | 이전: [9. Kotlin 노트](09-kotlin-notes.md) | 다음: [11. 오류와 언어](../11-errors-and-languages.md) | [이 페이지의 Java 버전](../java/10-rules-and-limits.md)
