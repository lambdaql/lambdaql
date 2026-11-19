<a id="9-kotlin-notes"></a>
# 9. Kotlin 노트

[Kotlin 트랙](README.md) | [매뉴얼 홈](../README.md) | 이전: [8. 프로시저와 DB 함수](08-procedures-functions.md) | 다음: [10. 규칙과 제한](10-rules-and-limits.md) | [이 페이지의 Java 버전: 9. Java 노트](../java/09-java-notes.md)

공통 페이지: [1. 개요](../01-overview.md) · [11. 오류와 언어](../11-errors-and-languages.md)

lambdaQL은 **같은 엔진**과 같은 API로 Kotlin과 Java를 지원합니다.
의미가 같은 Kotlin 람다와 Java 람다는 같은 HQL을 만듭니다.
이 장은 Kotlin에서 특별한 점을 한곳에 모았습니다.

<a id="contents"></a>
## 목차

- [9.1 설정](#91-setup)
- [9.2 동작하는 람다 형태](#92-lambda-forms-that-work)
- [9.3 직접 작성한 null 검사만 의미가 있음](#93-only-your-null-checks-count)
- [9.4 같음 비교: `==`, `!=`, `===`와 `areEqual`](#94-equality----and-areequal)
- [9.5 null 연산자: `?.`, `?:`와 `!!`](#95-null-operators---and-)
- [9.6 `in`, 범위와 `when`](#96-in-ranges-and-when)
- [9.7 문자열](#97-strings)
- [9.8 캡처 값](#98-captured-values)
- [9.9 속성 참조와 스코프 함수](#99-property-references-and-scope-functions)
- [9.10 Kotlin 연산자](#910-kotlin-operators)
- [9.11 결과: 데이터 클래스, `Pair`와 `Triple`](#911-results-data-classes-pair-and-triple)
- [9.12 수정, 삽입, 삭제: 속성 대입과 `Dml.value`](#912-update-insert-delete-property-assignment-and-dmlvalue)
- [9.13 프로시저와 DB 함수](#913-procedures-and-db-functions)
- [9.14 Kotlin 제한(KC1 ~ KC21)](#914-kotlin-limits-kc1-to-kc21)
- [9.15 lambdaQL이 Kotlin 코드 실행과 다른 점](#915-where-lambdaql-differs-from-running-kotlin-code)

예제를 읽는 방법:

- 예제 아래의 주석은 설계에 명시된 경우 lambdaQL이 만드는 HQL을 보여 줍니다.
- 람다에 직접 쓴 상수(숫자, 문자열, boolean, `null`, enum 상수, `const val`)는 HQL에 리터럴로 씁니다. 캡처 값과 계산한 값은 항상 바인드 파라미터(`:p1`, `:p2`, ...)입니다(D37).
- `e0`은 루트 엔티티의 별칭이고, `j1`은 lambdaQL이 연관 경로에 대해 추가하는 조인의 별칭입니다. `where`에서는 항상 inner `join`(D48), 경로를 `orderBy`나 `select`에만 쓰면 `left join`입니다(D36). 서브쿼리 루트는 `s1`, `s2`, ...입니다([5. 서브쿼리](05-subquery.md) 참고). 예정된 기능(파생 테이블, CTE, 프로시저)의 예제는 아직 예정된 별칭 `o`와 `c1`을 사용합니다.
- 엔티티는 [2. 시작하기](02-getting-started.md#example-entities)의 엔티티입니다. 몇몇 예제는 `Order`에 추가 속성 두 개를 사용합니다: `var flag: Boolean? = null`과 `var kind: OrderKind? = null`(`enum class OrderKind { NORMAL, EXPRESS, GIFT }`와 함께).

---

<a id="91-setup"></a>
## 9.1 설정

| 항목 | 값 |
|---|---|
| Kotlin | 2.x. 설계는 kotlinc 1.7.22, 2.1.10, 2.2.21, 2.3.0, 2.3.21로 확인함 |
| JVM | Java 21 |
| 컴파일러 옵션 | 기본값 `-Xsam-conversions=indy`를 유지. `-Xsam-conversions=class`는 사용하지 **마십시오**(KC1) |
| `-jvm-target` | 1.8, 11, 17, 21 모두 동작 |
| Kotlin 런타임 | lambdaQL은 의존성에 `kotlin-stdlib`나 `kotlin-reflect`를 추가하지 않음 |
| JPA 엔티티 | 일반적인 Kotlin JPA 엔티티(필드에 붙인 어노테이션도 괜찮음). 평소처럼 `kotlin("plugin.jpa")`와 all-open 플러그인을 사용 |

자세한 내용: [2. 시작하기](02-getting-started.md#kotlin-compiler-settings).

<a id="92-lambda-forms-that-work"></a>
## 9.2 동작하는 람다 형태

lambdaQL 함수는 Java `Serializable` 함수형 인터페이스를 받습니다.
Kotlin에서는 **람다 리터럴**을 전달합니다. 후행 람다와 `it`을 사용하십시오.

```kotlin
val list = qb.from(Order::class.java)
    .where { it.status == 1 }
    .where { it.price!! > 100.0 }        // 여러 where() 호출은 AND로 결합됨
    .toList()
// select e0 from Order e0 where e0.status = 1 and e0.price > 100.0
```

**SQL 블록 읽는 법.** `…`는 줄여 쓴 select 목록(엔티티의 모든 컬럼)이고, `?`는 바인드 파라미터입니다. 각 줄은 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)에서 나옵니다. 이 테스트는 각 쿼리를 Hibernate 7.4로 H2 2.3, MariaDB 10.11, Oracle 23.26에서 실행하며, Hibernate의 SQL이 바뀌면 실패합니다. 이 테스트는 Java 람다를 사용합니다. 의미가 같은 Kotlin 람다는 같은 HQL을 만들므로 SQL도 같습니다.

**데이터베이스별 SQL (Java 테스트 실행 결과, Kotlin도 같은 HQL을 만듦)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `to-hql`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>100.0
```

파라미터가 두 개 이상인 람다는 이름을 사용합니다. 필요 없는 파라미터에는 `_`를 사용하십시오.

```kotlin
.where { o, sub -> sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
.orderBy { o, by -> by.desc(o.price).asc(o.id) }
```

Kotlin 속성은 getter로 읽습니다. `it.price`는 Java 호출 `getPrice()`와 같은 바이트코드입니다.
lambdaQL은 getter를 JPA 속성에 매핑합니다(규칙 R10).
boolean 속성 `active`의 getter는 `getActive()`입니다. lambdaQL은 이를 `active`에 매핑합니다.

| 형태 | 동작하는가? |
|---|---|
| `where { it.active }`(람다 리터럴) | 예 |
| `run { }`, `let { }`, `forEach { }`, inline이 아닌 함수, `suspend` 함수 안의 람다 | 예 |
| 호출자가 lambdaQL 타입의 `inline fun` 파라미터로 전달한 람다 | 예 |
| SAM 생성자, 먼저 `val`에 저장한 경우도 포함 | 예 |
| 속성 참조: `where(Order::active)`, `orderBy(Order::price)` | 예(R23). `orderBy`에서는 아직 구현되지 않음 |
| Java 엔티티의 Java getter 참조: `orderBy(JOrder::getPrice)` | 예 |
| `val f: (Order) -> Boolean = { it.active }; where(f)` | **아니요**(KC4) |
| `object : ...`(람다 대신 객체 식) | **아니요**(KC5) |
| `inline fun` 본문 안에서 만든 람다 | **아니요**(KC2) |
| `-Xsam-conversions=class`로 컴파일 | **아니요**(KC1) |

<a id="93-only-your-null-checks-count"></a>
## 9.3 직접 작성한 null 검사만 의미가 있음

lambdaQL은 **람다를 절대 실행하지 않습니다**. 바이트코드를 읽고 HQL을 씁니다.
그래서 몇몇 Kotlin 규칙은 다르게 동작합니다. 다음 네 가지 결정이 기반입니다.

| 결정 | 규칙 |
|---|---|
| D20, D36 | `it.customer?.name` 같은 경로는 호출 체인이 아니라 **컬럼**입니다. `where`에서 to-one 연관은 기본적으로 inner join입니다. 고객이 없는 주문은 반환되지 않습니다. 그런 주문을 찾으려면 `it.customer == null`이라고 쓰십시오. `it.customer == null \|\| ...` 같은 null 가드를 써도 inner join은 바뀌지 않습니다(D48). 그런 주문을 유지하려면 `leftJoin`을 사용하십시오. |
| D22 | lambdaQL은 **직접 작성한 null 처리만** 사용합니다: `== null`, `!= null`, `?.`, `?:`. Kotlin 컴파일러가 스스로 추가하는 null 검사는 무시합니다. 그 밖의 NULL은 SQL을 따릅니다. 조건은 UNKNOWN이 되고 행은 반환되지 않습니다. |
| D23 | `!!`는 무시됩니다. 코드가 컴파일되도록 있을 뿐입니다. `it.customer!!.name`과 `it.customer?.name`은 같은 컬럼입니다. |
| D12 | 객체 사이의 `==`(그리고 `===`)는 객체 동일성이 아니라 **값 비교**를 뜻합니다. |

lambdaQL은 "직접 작성한" null 검사와 컴파일러의 null 검사를 어떻게 구별할까요? 바이트코드가 다릅니다.

| 소스 | 바이트코드 모양 | lambdaQL |
|---|---|---|
| `it.status == null`이나 `it.status != null`을 작성함 | `getStatus; ifnull / ifnonnull` | 사용함: `is null`, `is not null` |
| `status: Int?`에 대해 `it.status == 1`을 작성함 | 컴파일러가 `dup; ifnonnull; pop; goto`를 추가한 뒤 언박싱함 | 숨은 검사를 무시함: `e0.status = 1` |
| `it.price!!`를 작성함 | `Intrinsics.checkNotNull(...)` | 무시함 |

결과적으로 Kotlin과 Java는 같은 SQL을 만듭니다. `it.status == 1`은 `e0.status = 1`이고, Java로 작성한 같은 조건도 `e0.status = 1`입니다.

<a id="94-equality----and-areequal"></a>
## 9.4 같음 비교: `==`, `!=`, `===`와 `areEqual`

<a id="how-kotlin-compiles-"></a>
### Kotlin이 `==`를 컴파일하는 방법

| Kotlin | 컴파일 결과 | lambdaQL 규칙 | HQL |
|---|---|---|---|
| non-null 기본형 두 개: `it.qty == 5` | 비교 분기(Java와 같음) | R1 | `i.qty = 5` |
| nullable 기본형과 상수: `it.status == 1`(`Int?`) | 숨은 null 검사 + 비교 | R12(숨은 검사 무시, D22) | `e0.status = 1` |
| `it.status != 1`(`Int?`) | 위와 같음 | R12 | `e0.status <> 1`(NULL 행은 반환되지 **않음**) |
| 참조이거나 양쪽 모두 nullable: `it.description == text`, `it.status == q`(`q: Int?`) | `Intrinsics.areEqual(a, b)` 호출 | R11: 값 비교 | `e0.description = :p1`(캡처 값 `null`: 일치하는 행 없음, `LQL-4045`) |
| `it.description != text` | `!Intrinsics.areEqual(a, b)` | R11, SQL 의미 | `e0.description <> :p1`(NULL 행은 반환되지 **않음**) |
| 엔티티 값 두 개: `it.description == it.custName` | `Intrinsics.areEqual(a, b)` | R11, SQL 의미 | `e0.description = e0.custName`(어느 한쪽이 NULL이면 불일치) |
| `it.id === q`, `!==` | `if_acmpeq` / `if_acmpne` | R17: 값 비교 | `==`와 같음 |
| enum: `it.kind == OrderKind.GIFT` | `if_acmpne` | R2, R17 | `e0.kind = com.example.OrderKind.GIFT`(전체 클래스 이름을 쓴 리터럴) |

`Intrinsics.areEqual`은 `==` 뒤에 있는 Kotlin 런타임 함수입니다. lambdaQL은 이를 이름으로 인식하고 **값 비교**로 읽습니다.
엔티티 값은 SQL 의미를 따릅니다(결정 D48). 어느 한쪽이 NULL이면 일치하지 않으며, NULL과 NULL도 마찬가지입니다.
캡처 값 `null`은 NULL로 바인딩되어 어떤 행에도 일치하지 않습니다(`WARN` 수준 `LQL-4045`). NULL을 찾으려면 `x == null`이라고 쓰십시오.
결과가 Kotlin과 다를 수 있으면 lambdaQL은 `LQL-5009`를 `DEBUG` 수준으로 기록합니다.

<a id="plain--and-"></a>
### 단순한 `=`와 `<>`

lambdaQL은 항상 단순한 `=`와 `<>`를 씁니다(결정 D48).
`is [not] distinct from`은 쓰지 않습니다. 이 형태는 Oracle에서 `decode(...)`가 되어 인덱스를 사용할 수 없습니다.

```kotlin
.where { it.description == text }    // text가 "abc"이면 -> e0.description = :p1
.where { it.description != "x" }     // e0.description <> 'x'   (description이 NULL이면 일치하지 않음)
.where { it.description == null || it.description != "x" }   // NULL 행도 포함
```

> [!WARNING]
> **Kotlin의 non-null 타입은 증거가 되지 않습니다.** `var code: String` 속성에도 데이터베이스에서 온 NULL이 들어 있을 수 있습니다.
> Hibernate가 검사 없이 컬럼 값을 필드에 쓰기 때문입니다.
> lambdaQL은 Kotlin 타입이 아니라 JPA 메타데이터를 봅니다.

<a id="-and-"></a>
### `===`와 `!==`

lambdaQL에서 `===`는 `==`와 마찬가지로 **값 비교**를 뜻합니다(R17).
쿼리에는 객체 동일성이 없습니다. Kotlin 자체는 값이 1000인 서로 다른 두 `Long` 박스에 대해 `false`를 내지만, lambdaQL은 `true`를 냅니다.

<a id="bigdecimal"></a>
### `BigDecimal`

Kotlin에서 `BigDecimal`에 대한 `==`는 `equals`를 호출하며, 이는 스케일까지 비교합니다(`1.0 != 1.00`).
SQL `=`는 값만 비교합니다. **lambdaQL은 값 비교를 사용합니다.**
`BigDecimal`, `String`, `LocalDate`, `BigInteger`에 대한 `<`, `>`, `<=`, `>=`를 지원합니다(`compareTo`로 컴파일됨).

<a id="booleans"></a>
### boolean

```kotlin
.where { it.active }                       // e0.active = true
.where { !it.active }                      // not (e0.active = true)
.where { it.flag == true }                 // e0.flag = true
.where { it.flag != true }                 // e0.flag <> true   (NULL 행은 반환되지 않음: SQL 의미)
.where { it.active == (it.status!! > 1) }  // (e0.active = true and e0.status > 1) or (e0.active = false and e0.status <= 1)
```

두 boolean 식의 비교를 지원합니다(D25). lambdaQL은 이를 `AND`/`OR`로 펼치므로(`CASE` 없음) 데이터베이스가 인덱스를 사용할 수 있습니다.
양쪽이 같은 조건을 공유하면(예: `it.active == it.active`) 거부됩니다.

<a id="java-entities-from-kotlin-platform-types"></a>
### Kotlin에서 쓰는 Java 엔티티(플랫폼 타입)

엔티티가 Java 클래스이면 Kotlin은 그 getter를 플랫폼 타입(`Long!`)으로 봅니다.
규칙은 같습니다. `j.id == 1L`은 `e0.id = 1`이고, `j.customer.name`은 조인한 컬럼입니다(`where`에서는 기본적으로 inner join).

<a id="95-null-operators---and-"></a>
## 9.5 null 연산자: `?.`, `?:`와 `!!`

<a id="summary"></a>
### 요약

| Kotlin | lambdaQL에서의 의미 | HQL 예 |
|---|---|---|
| `it.customer?.name` | 컬럼 `customer.name`. `where`에서는 기본적으로 inner join이므로 고객이 없는 주문은 반환되지 않음 | `j1.name` + `join e0.customer j1` |
| `it.customer!!.name` | `?.`와 같음. `!!`는 무시됨(D23) | `j1.name` + `join e0.customer j1` |
| `it.customer?.id` | 경로가 대상의 `@Id`로 끝남: FK 컬럼, 조인 없음 | `e0.customer.id` |
| `it.customer == null` | 고객이 없는 주문만 | `e0.customer is null`(FK 컬럼, 조인 없음) |
| `it.customer != null` | 고객이 있는 주문만 | `e0.customer is not null`(FK 컬럼, 조인 없음) |
| `it.price!! > 10.0` | `!!`는 무시됨. 컴파일러를 위한 것일 뿐 | `e0.price > 10.0` |
| `it.description ?: ""`(값) | 기본값 | `coalesce(e0.description, '')` |
| `it.flag ?: false`(조건) | NULL은 false로 취급 | `(e0.flag is not null and e0.flag = true)` |
| `lo!!`(캡처 값) | `!!`는 무시됨. `lo`가 null이면 NULL을 바인딩하므로 일치하는 행이 없음(`DEBUG` 수준 `LQL-4045`, [9.8](#98-captured-values) 참고) | `:p1` |

<a id="-and--on-association-paths"></a>
### 연관 경로의 `?.`와 `!!`

```kotlin
.where { it.customer?.name == "Kim" }
// select e0 from Order e0 join e0.customer j1 where j1.name = 'Kim'

.where { it.customer!!.name == "Kim" }
// 같은 HQL

.where { it.customer?.id == 5L }
// select e0 from Order e0 where e0.customer.id = 5      (FK 컬럼, 조인 없음)
```

**데이터베이스별 SQL (Java 테스트 실행 결과, Kotlin도 같은 HQL을 만듦)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `path-literal`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name='Kim'
```

**데이터베이스별 SQL (Java 테스트 실행 결과, Kotlin도 같은 HQL을 만듦)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `fk-literal`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id=5
```

경로는 호출 체인이 아니라 **컬럼**입니다(D20). `where`에서 lambdaQL은 기본적으로 inner join을 사용합니다(D36).
그래서 고객이 없는 주문은 반환되지 않고, 경로 앞에 `it.customer != null &&`를 쓸 필요도 없습니다.

**`?.`를 써도 고객 없는 주문은 돌아오지 않습니다.** Kotlin에서 `it.customer?.name == null`은 고객이 없을 때 `true`입니다.
lambdaQL에서는 고객의 이름이 없는 주문만 반환합니다. 경로가 inner join이므로 null 가드를 써도 마찬가지입니다(D48).

```kotlin
.where { it.customer == null }
// select e0 from Order e0 where e0.customer is null      (FK 컬럼, 조인 없음)

.where { it.customer == null || it.customer?.name == null }
// 고객의 이름이 없는 주문만: inner join이 고객 없는 주문을 제외함
// select e0 from Order e0 join e0.customer j1 where e0.customer is null or j1.name is null
```

**데이터베이스별 SQL (Java 테스트 실행 결과, Kotlin도 같은 HQL을 만듦)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `no-association`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id is null
```

**데이터베이스별 SQL (Java 테스트 실행 결과, Kotlin도 같은 HQL을 만듦)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `null-guard-name-null`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.id is null or c1_0.name is null
```

`?.`는 끝에 함수가 있어도 동작합니다. `it.description?.length`는 `length(e0.description)`입니다.

<a id="-is-just-for-the-compiler"></a>
### `!!`는 컴파일러를 위한 것일 뿐

`price`가 `Double?`이면 Kotlin은 `it.price > 10.0`을 컴파일하지 않습니다.
`it.price!! > 10.0`이라고 쓰십시오. lambdaQL은 `!!`를 무시합니다. 람다가 실행되지 않으므로 NPE는 없습니다.
`price`가 NULL인 행은 반환되지 않습니다(SQL 규칙).

<a id="-elvis"></a>
### `?:`(엘비스)

값(select, DML `set`, 정렬 키)에서 `?:`는 `coalesce`가 됩니다(규칙 R15).

```kotlin
.select { it.description ?: "" }                 // coalesce(o.description, '')
.select { it.customer?.name ?: "none" }          // coalesce(c1.name, 'none') + left join
.select { (it.price ?: 0.0) * 2 }                // coalesce(o.price, 0.0) * 2
qb.update(Order::class.java).set { it.price = (it.price ?: 0.0) + 1 }.all().execute()
// set o.price = coalesce(o.price, 0.0) + 1.0
```

**문자열** 조건에서는 lambdaQL이 컬럼에 함수를 씌우지 않습니다(SARGable 규칙).

```kotlin
.where { (it.description ?: "") == "x" }   // e0.description = 'x'
.where { (it.description ?: "") == "" }    // (e0.description = '' or e0.description is null)
```

**숫자** 조건에서는 설계상 `coalesce(column, default)`를 씁니다.

```kotlin
.where { (it.price ?: 0.0) > 3.0 }            // coalesce(e0.price, 0.0) > 3.0
.where { (it.description?.length ?: 0) > 3 }  // coalesce(length(e0.description), 0) > 3
```

컬럼에 함수가 있으면 `price`의 일반 인덱스가 사용되지 않습니다.
팁: 첫 번째 예제에서 NULL은 0이 되고 `0 > 3`은 false입니다. 그래서 `it.price!! > 3.0`은 같은 행을 반환하고
인덱스를 사용할 수 있는 `e0.price > 3.0`을 만듭니다.

**boolean 엘비스**는 **조건에서** 지원합니다.

```kotlin
.where { it.flag ?: false }       // (e0.flag is not null and e0.flag = true)
.where { !(it.flag ?: false) }    // (e0.flag is null or not (e0.flag = true))
```

**값이나 정렬 키**로 쓰는 boolean 엘비스(`select { it.flag ?: false }`)는 지원하지 않습니다(KC21).
boolean에 대한 `coalesce`는 Oracle에서 실패합니다(ORA-00932). 정렬에는 키 `{ it.flag }`를 사용하고
`by.asc(key, nullsFirst)`나 `by.desc(key, nullsFirst)`로 NULL 위치를 고르십시오([4. 정렬과 페이징](04-order-paging.md#43-direction)).

<a id="smart-casts-and-let"></a>
### 스마트 캐스트와 `?.let`

사용하기 전의 null 검사는 일반 조건입니다.

```kotlin
.where { it.description != null && it.description!!.length > 3 }
// (e0.description is not null and length(e0.description) > 3)

.where { it.description?.let { d -> d.length > 3 } == true }
// (e0.description is not null and length(e0.description) > 3)
```

<a id="-in-an-assignment"></a>
### 대입에서의 `?.`

`set { }`과 `values { }`에서 `it.customer?.id = cid`는 **항상 대입합니다**. `?.`는 무시됩니다(D23).

<a id="96-in-ranges-and-when"></a>
## 9.6 `in`, 범위와 `when`

이 절의 모든 형태는 v0.1에서 지원합니다(결정 D21, D29). 자세한 내용과 더 많은 예제: [3.9](03-where.md#39-in-with-a-captured-collection), [3.14](03-where.md#314-ranges-kotlin-only), [3.15](03-where.md#315-when-kotlin-only).

| Kotlin | HQL | 참고 |
|---|---|---|
| `it.id in ids`(캡처한 컬렉션) | `e0.id in (:p1)` | 비어 있으면 FALSE. `null` 요소는 오류 |
| `it.id !in ids` | `e0.id not in (:p1)`. NULL이 될 수 있는 컬럼도 같음(NULL 행은 반환되지 않음) | 비어 있으면 TRUE |
| `it.status in 1..5` | `e0.status between 1 and 5` | |
| `it.status !in 1..5` | `not (e0.status between 1 and 5)` | NULL 행은 반환되지 않음 |
| `it.status in 1 until 5`, `in 1..<5` | `e0.status >= 1 and e0.status < 5` | |
| `it.status in lo..hi` | `e0.status between :p1 and :p2` | |
| `it.status in 5 downTo 1` | 지원함 | `1..5`와 같은 행 |
| `... step 2` | **거부됨** | |
| `when (it.status) { 1, 2 -> true; else -> false }` | `e0.status in (1, 2)` | `Int`, `String`, enum 대상 |
| `when { a -> true; b -> true; else -> false }` | `a or b` | |
| `it.customer?.id in sub.from(...).select { ... }` | `o.customer.id in (select ...)` | 서브쿼리, [5.4](05-subquery.md#54-in) 참고 |
| `x !in sub.from(...).select { ... }` | `x not in (select ...)`(SQL 의미) | [5.5](05-subquery.md#55-not-in-is-written-as-not-exists) 참고 |
| `it.status in listOf(1, 2)`(람다 안에서 만든 리스트) | **거부됨**(KC8) | 리스트는 밖에서 만들 것 |

**nullable 대상에는 null 가드가 붙지 않습니다**(D22). Kotlin은 `null in 1..5`를 false로, `null !in 1..5`를 true로 정의합니다.
lambdaQL은 가드를 추가하지 않습니다. NULL 대상은 `in`에서도 반환되지 않고 `!in`에서도 반환되지 **않습니다**(SQL 규칙).
NULL 행을 원하면 `it.status == null || it.status !in 1..5`라고 쓰십시오.

캡처한 컬렉션에 대한 `!in`도 같습니다. `x not in (:p1)`은 NULL 행을 반환하지 않습니다(SQL 의미, 결정 D48).
NULL 행을 원하면 `it.status == null || it.status !in ids`라고 쓰십시오.

<a id="97-strings"></a>
## 9.7 문자열

다음 Kotlin 문자열 형태에는 설계가 있습니다(규칙 R21). 가능한 곳에서는 SARGable 규칙을 따릅니다.

| Kotlin | HQL | 참고 |
|---|---|---|
| `it.description!!.startsWith("A")` | `e0.description like :p1 escape '\'` | lambdaQL은 값 안의 `%`, `_`, `\`를 이스케이프함 |
| `it.description!!.endsWith(s)` | `e0.description like :p1 escape '\'` | |
| `it.description!!.contains(s)` | `e0.description like :p1 escape '\'` | |
| `it.description?.startsWith("A") == true` | 같은 `like` | 이것도 지원함 |
| `startsWith(s, ignoreCase = true)`, `equals(s, ignoreCase = true)` | 컬럼에 따라 다름(아래 참고) | D24 |
| `it.description.equals(text)` | `==`와 같음 | |
| `it.description!!.lowercase() == "abc"` | `lower(e0.description) = 'abc'` | 컬럼에 적용한 함수: 일반 인덱스가 사용되지 않음 |
| `it.description!!.length > 3` | `length(e0.description) > 3` | 길이 단위는 DB마다 다를 수 있음 |
| `it.description.isNullOrEmpty()` | `(e0.description is null or e0.description = :p1)` | Oracle에서 `''`는 NULL |
| `isBlank()`, `isNullOrBlank()` | **거부됨**(KC9) | `isNullOrEmpty()`나 직접 비교를 사용 |
| 컬럼에 대한 `trim()`, `coerceAtLeast()`, `toLocalDate()` 및 다른 stdlib 호출 | **거부됨**(KC20) | 계획됨(함수 매핑) |
| `"$a-$b" == it.description`(템플릿에 캡처 값만 사용) | `:p1 = e0.description` | 설계됨: 문자열은 애플리케이션에서 만듦. 현재 분석 코어는 모든 문자열 결합을 거부하므로 람다 전에 문자열을 만들 것 |
| `"${it.description}-x"`(템플릿 안의 엔티티 값) | **거부됨**(KC17) | 계획됨 |

**`ignoreCase = true`(D24).** lambdaQL은 인덱스를 사용할 수 없는 `lower(col) = lower(:p)`를 쓰지 않습니다.
시작할 때 컬럼에 대한 DB 메타데이터를 읽고 DB가 사용할 수 있는 형태를 고릅니다.

| DB에 있는 것 | HQL |
|---|---|
| 대소문자를 구분하지 않는 콜레이션(MariaDB `_ci`) | `col = :p` / `col like :p` |
| `lower(col)`에 대한 Oracle 함수 인덱스 | `lower(col) = :p`(값은 애플리케이션에서 소문자로 바꿈) |
| Oracle 언어 인덱스 | `col collate binary_ci = :p` |
| 없음 | 시작할 때 WARN(strict 모드: 오류) |

유니코드 대소문자 규칙(예: `ſ`, `İ`)은 Kotlin이 아니라 DB 콜레이션을 따릅니다.

<a id="98-captured-values"></a>
## 9.8 캡처 값

| 캡처 대상 | lambdaQL의 사용 방법 |
|---|---|
| `val`(`Long?`, `String`, ...도 마찬가지) | 바인드 파라미터 |
| `var` | 바인드 파라미터. 값은 **쿼리가 실행될 때마다** 읽음 |
| 바깥 클래스의 속성(`this.minStatus`, `private`도 마찬가지) | 쿼리가 실행될 때마다 읽음 |
| `companion object`의 `val`, 최상위 `val`, `object`의 `val` | 바인드 파라미터 |
| `const val` | HQL에 리터럴로 씀(D37) |
| 람다 안에서 캡처 변수에 쓰기(`n++`) | **거부됨**(KC12) |

```kotlin
class OrderRepo(private val qb: QueryBuilder) {
    private val minStatus = 2

    fun open(lo: Int?) = qb.from(Order::class.java)
        .where { it.status!! >= minStatus }
        .where { lo == null || it.status!! > lo }   // lo == null: 필터 없음, 그 외에는 e0.status > :p2
        .toList()
}
```

`==`, `!=`에 쓰인 캡처 값 `null`은 NULL로 바인딩되므로 일치하는 행이 없습니다(`WARN` 수준 `LQL-4045`).
캡처 값 `null`은 같은 값에 대한 null 가드 아래(`lo == null || ...`)에서 허용됩니다. 가드는 쿼리가 실행될 때 애플리케이션에서 결정하므로 `lo == null`이면 이 `where`는 필터를 추가하지 않습니다.
가드 없이 쓴 크기 비교(`lo == null`일 때 `it.status!! > lo!!`)는 NULL을 바인딩하므로 일치하는 행이 없습니다(`DEBUG` 수준 `LQL-4045`). `lo!!`와 언박싱은 실행하지 않습니다.
캡처 값에 메서드를 호출하는 식(`text.trim()`, `lo.toLong()` 등)만 `null`이면 오류(`LQL-4012`)를 던집니다.
캡처한 `NaN`이나 `Infinity`는 항상 오류입니다.

<a id="99-property-references-and-scope-functions"></a>
## 9.9 속성 참조와 스코프 함수

<a id="property-references"></a>
### 속성 참조

```kotlin
.orderBy(Order::price)       // orderBy { it.price }와 같음(아직 구현되지 않음)
.where(Order::active)        // where { it.active }와 같음
```

| 형태 | 동작하는가? |
|---|---|
| lambdaQL 함수에 전달한 Kotlin `Order::price` | 예(R23). `orderBy`에서는 **아직 구현되지 않음**: 지금은 `orderBy { it.price }`를 사용하십시오 |
| Java getter 참조 `JOrder::getPrice` | 예 |
| Java 합성 속성 `JOrder::price` | 아니요. Kotlin 자체가 컴파일하지 않음(KC18). `JOrder::getPrice`를 사용 |
| `Order::price.getter` 및 다른 `kotlin-reflect` API | 아니요(KC19) |
| 직접 만든 Kotlin `fun interface`에 전달한 참조 | 아니요(KC3). 람다 리터럴을 전달 |

<a id="scope-functions-and-inline-functions-in-conditions"></a>
### 조건 안의 스코프 함수와 inline 함수

다음은 조건에서 지원합니다. 컴파일러가 본문을 인라인하므로 lambdaQL은 일반 조건으로 봅니다.

```kotlin
.where { it.description?.let { d -> d.length > 3 } == true }
.where { it.run { status == 1 && active } }
.where { with(it) { status == 1 } }
.where { it.isOpenInline() }   // 직접 만든 함수: inline fun Order.isOpenInline() = status == 1
```

| 형태 | 지원 |
|---|---|
| `?.let { }`, `run { }`, `with(it) { }`, `also { }` | 예, 조건에서 |
| 직접 만든 `inline fun` | 예 |
| 직접 만든 inline이 아닌 확장 함수나 확장 속성 | 아니요(KC11). `inline`으로 만들거나 조건을 직접 작성 |
| `takeIf { }` | 아니요(KC6) |

수정과 삽입 람다에서 `apply`, `with`, `also`는 v0.1에서 지원하지 않습니다([9.12](#912-update-insert-delete-property-assignment-and-dmlvalue) 참고).

<a id="910-kotlin-operators"></a>
## 9.10 Kotlin 연산자

이 표는 연산자 형태 126개에 대한 컴파일러 확인 결과에서 나왔습니다(kotlinc 2.3.21, 설계 문서 KOTLIN.md 9절).

| 연산자 | lambdaQL 람다 안에서 |
|---|---|
| 숫자에 대한 `< > <= >=`(nullable에는 `!!` 사용) | 지원 |
| `BigDecimal`, `String`, `LocalDate`, `BigInteger`에 대한 `< > <= >=` | 지원(`compareTo`로 컴파일됨) |
| `x.compareTo(y) > 0` | 지원, `x > y`로 읽음 |
| `==`, `!=` | 지원(SQL 의미의 값 비교, [9.4](#94-equality----and-areequal)) |
| `===`, `!==` | 지원, 값 비교 |
| enum `==` | 지원 |
| enum `<`, `>` | 지원. lambdaQL은 이를 enum 상수의 `in (...)`으로 펼치므로 ORDINAL과 STRING 매핑 모두에서 동작함 |
| 엔티티 `==` 엔티티 | **거부됨**(KC13). id를 비교할 것 |
| 값 객체(`@Embedded`) `==`, `!=` | 지원. 컬럼마다 펼침(B6, [3.10](03-where.md#310-association-paths) 참고). 크기 비교는 `LQL-3012` |
| `+ - *`, 단항 `-` | 지원 |
| `%` | 지원, `mod`로 출력 |
| 정수 `/` | **주의.** 데이터베이스마다 결과가 다름(MariaDB는 `5/2`가 `2.5`, Kotlin은 `2`). 최종 규칙은 정해지지 않았으며 v0.1에서 거부될 수 있음. 가드 아래(`qty != 0 && total / qty > 2`)에서는 항상 거부됨 |
| `BigDecimal` / `BigInteger`의 `+ - * %`, 단항 `-` | 지원, SQL 연산자로 출력 |
| `BigDecimal` `/` | 지원(Kotlin은 `divide(x, HALF_EVEN)` 사용). 결과 스케일은 DB를 따름. 다른 `divide` 형태는 거부됨 |
| `in a..b`, `until`, `..<`, `downTo` | 지원([9.6](#96-in-ranges-and-when)). `step`은 지원하지 않음 |
| `x in list`, `x !in list` | 지원([9.6](#96-in-ranges-and-when)) |
| 문자열 `+`, 엔티티 값을 사용하는 문자열 템플릿 | 지원하지 않음(계획됨). 참고: NULL에 대해 Kotlin은 `"null"`을, SQL은 NULL을 냄 |
| boolean 중위 함수 `and`, `or`, `xor` | **거부됨**. `&&`와 `\|\|`를 사용(단락 평가를 함. `and`/`or`는 하지 않음) |
| 캡처한 배열에 대한 `arr[0]` | 캡처 값으로 취급하거나 명확한 오류 |
| 엔티티 경로에 대한 `it.tags["k"]`, `it.lines[0]` | **거부됨** |
| 엔티티의 `operator` 함수(`plus`, `compareTo`), 직접 만든 inline이 아닌 `infix` | **거부됨**(KC11) |
| `@JvmInline value class` 속성 | **거부됨**(KC16) |

<a id="no-kotlin-only-operator-api"></a>
### Kotlin 전용 연산자 API는 없음

lambdaQL에는 **Kotlin 전용 연산자 API가 없습니다**(결정됨). 일반적인 Java 메서드 이름이 이미 Kotlin 연산자로 동작합니다.

| Kotlin 형태 | 동작하는 이유 | 상태 |
|---|---|---|
| `x in sub.from(...).select { ... }` | Kotlin은 `x in v`를 `v.contains(x)`로 바꿈 | 유지 |
| `Agg.sum(x)!! + 1 > 3` | 자리표시자 값에 대한 일반 산술 | 유지 |
| 정렬 연산자(`by + key`, `-key`) | Java 사용자에게 이상한 메서드가 추가되고 Kotlin 전용 확장이 필요함 | 거부 |
| DB 함수용 `invoke` 연산자 | D31(인터페이스 정적 함수)과 충돌 | 거부 |
| `o["price"]` | 문자열 경로. 타입 안전성 없음 | 거부 |
| `infix` 함수 | Java 메서드는 `infix`가 될 수 없음 | 불가능 |
| `between` | `x in a..b`로 작성(nullable에는 `!!`, `LocalDate?`에는 `!!` 필요) | API 불필요 |

<a id="911-results-data-classes-pair-and-triple"></a>
## 9.11 결과: 데이터 클래스, `Pair`와 `Triple`

```kotlin
data class OrderView(val id: Long?, val customerName: String?, val price: Double?)
data class Stat(val customerId: Long?, val cnt: Long, val total: Double?)

qb.from(Order::class.java).select { o -> OrderView(o.id, o.customer?.name, o.price) }   // 데이터 클래스
qb.from(Order::class.java).select { o -> o.customer?.name to o.price }                  // Pair

qb.from(Order::class.java)
    .groupBy { it.customer?.id }
    .having { Agg.sum(it.price)!! > 1000 }
    .select { Stat(it.customer?.id, Agg.count(), Agg.sum(it.price)) }

qb.from(Order::class.java).groupBy { listOf(it.customer?.id, it.status) }   // 여러 키
qb.from(Order::class.java).groupBy { it.customer?.id to it.status }         // Pair도 괜찮음
```

| 규칙 | 참고 |
|---|---|
| 데이터 클래스에는 nullable 타입을 사용 | `sum`, `avg`, `min`, `max`는 NULL일 수 있음. non-null 파라미터에 NULL이 들어가면 객체를 만들 때 NPE가 발생함(J4) |
| `Pair`, `Triple` | 결과와 그룹 키로 허용 |
| `groupBy` 안의 `listOf(...)` | 허용(람다 안에서 만든 리스트가 허용되는 유일한 곳) |
| `groupBy` 안의 `arrayOf(...)`, `setOf(...)` | 허용 안 됨 |
| 파생 테이블이나 CTE의 행 타입 | Kotlin 데이터 클래스(또는 Java record)만. 컬럼은 `componentN` 순서를 따름(J5) |
| `Agg` 함수 | `import org.lambdaql.Agg.sum`이 동작함. 컬렉션에 대한 Kotlin `sum()` / `count()`와 충돌하지 않음 |

자세한 내용: [6. 조회, 집계, 그룹화](06-select-group.md).

<a id="912-update-insert-delete-property-assignment-and-dmlvalue"></a>
## 9.12 수정, 삽입, 삭제: 속성 대입과 `Dml.value`

Kotlin 속성 대입은 Java와 같은 setter 호출로 컴파일되므로 규칙도 같습니다.

```kotlin
qb.update(Order::class.java).set { it.status = st; it.price = it.price!! * 1.1 }.where { it.status == 1 }.execute()
qb.update(Order::class.java).set { o -> value(o.status, 1) }.where { it.id == id }.execute()     // val 속성용(Dml.value)
qb.update(Order::class.java).set { it.customer?.id = cid }.where { it.id == id }.execute()       // ?. 대입 = 일반 대입
qb.update(Order::class.java).set { it.price = (it.price ?: 0.0) + 1 }.all().execute()            // ?: -> coalesce

qb.delete(Order::class.java).where { it.customer?.name == null }.execute()

qb.insert(Order::class.java).values { it.id = id; it.price = p; it.status = 1 }.execute()
qb.insert(Order::class.java).values(lines) { o, l -> o.id = l.id; o.price = l.price; o.status = 1 }.execute()
```

| Kotlin 형태 | 의미 |
|---|---|
| `value(o.status, 1)` | `import org.lambdaql.Dml.value`로 가져온 함수(D49). `val` 속성(setter 없음)을 가진 엔티티에 사용. 속성 대입과 한 람다 안에서 섞을 수 있음(테스트함) |
| `value(o.cnt, o.cnt + 1)` | 지원 |
| `it.cnt += 1` | 지원 |
| `it.cnt++`, `--` | v0.1에는 없음. `it.cnt += 1`로 작성 |
| `set` 안의 `apply { }`, `with(it) { }`, `also { }` | v0.1에는 없음. 직접 대입으로 작성 |
| `set { }`과 `set { o, sub -> }` | 서로 다른 오버로드. Kotlin은 파라미터 개수로 고름. 두 번째 인자 `sub`는 서브쿼리의 시작점(D49) |

자세한 내용: [7. 수정, 삽입, 삭제](07-dml.md).

<a id="913-procedures-and-db-functions"></a>
## 9.13 프로시저와 DB 함수

<a id="procedures"></a>
### 프로시저

```kotlin
interface OrderProcs {
    @SqlProcedure("SQ_CALC") fun calc(id: Long, rate: Double): Double?
    @SqlProcedure("SP_RECENT") fun recent(custId: Long): List<OrderView>
    @SqlProcedure("SP_PURGE") fun purge(days: Int)
}

val d = qb.call(OrderProcs::class.java) { it.calc(id, rate) }.execute()
val d2 = qb.call(OrderProcs::class.java) { it.calc(rate = rate, id = id) }.execute()   // 이름 있는 인자
qb.exec(OrderProcs::class.java) { it.purge(30) }.execute()                            // 반환 값 없음
```

| 규칙 | 이유 |
|---|---|
| nullable 반환 타입(`Double?`)을 사용 | non-null 타입에 NULL OUT 값이 들어가면 실패함. lambdaQL은 이름이 있는 오류를 던짐 |
| 스텁 함수에 기본 인자를 쓰지 않음 | Kotlin은 호출을 `...$default`로 바꾸고 Kotlin 기본값을 채워 넣음. lambdaQL은 오류를 보고함 |
| 이름 있는 인자는 단순한 값(변수, 상수)만 | 식을 쓰면 Kotlin이 숨은 지역 변수를 만드는데, lambdaQL은 v0.1에서 이를 받아들이지 않음 |
| 반환 값이 없는 프로시저에는 (`call`이 아니라) `exec`을 사용 | 둘 다 `call`이면 Java에서 모호하고, `run`은 Kotlin의 `run { }`과 충돌함 |
| `-java-parameters`로 컴파일 | 그러면 lambdaQL이 같은 타입의 뒤바뀐 인자를 검사할 수 있음([8.4](08-procedures-functions.md#84-warning-argument-order)) |

<a id="db-functions-interface-companion-object--jvmstatic"></a>
### DB 함수: 인터페이스 `companion object` + `@JvmStatic`

Kotlin 인터페이스에는 정적 함수가 없습니다. `companion object`와 `@JvmStatic`을 사용하십시오.

```kotlin
interface OrderFns {
    companion object {
        @JvmStatic @SqlFunction("FN_DISCOUNT")
        fun discount(price: Double?): Double? = throw DbOnlyException()
    }
}

qb.from(Order::class.java).where { OrderFns.discount(it.price)!! > 10 }
// where function('FN_DISCOUNT' as Double, o.price) > 10
val v = qb.call { OrderFns.discount(price) }.execute()     // 함수 단독 호출
```

본문은 절대 실행되지 않습니다. 누군가 람다 밖에서 호출할 때 예외를 던질 뿐입니다.
Kotlin이 companion 함수를 호출하면 바이트코드는 먼저 `Companion` 객체를 로드합니다. lambdaQL은 그 단계를 건너뛰고 함수의 어노테이션을 읽습니다.

| 선언 | 지원 |
|---|---|
| 인터페이스 `companion object` + `@JvmStatic` | **예(권장)** |
| Kotlin `object` + `@JvmStatic` | 예 |
| 최상위 함수 | 예, 하지만 권장하지 않음: import를 잊었고 그 이름이 stdlib 이름(`maxOf`)이기도 하면 Kotlin은 조용히 stdlib 함수를 호출함 |
| 확장 함수 | 아니요(KC11) |
| 기본 인자가 있는 `@SqlFunction` | 아니요 |
| `inline` `@SqlFunction` | 아니요(호출이 사라짐) |
| `vararg` `@SqlFunction` | v0.1에서는 아니요 |
| 함수 타입 속성 / 함수형 인터페이스 상수 | 아니요 |

컬럼에 적용한 함수(`OrderFns.discount(it.price)!! > 10`)는 일반 인덱스를 사용할 수 없습니다.
lambdaQL은 이에 대해 WARN을 기록합니다([8.9](08-procedures-functions.md#89-warning-functions-on-columns-and-indexes-sargable) 참고).

<a id="914-kotlin-limits-kc1-to-kc21"></a>
## 9.14 Kotlin 제한(KC1 ~ KC21)

lambdaQL은 추측하지 않습니다. 어떤 형태를 분석할 수 없으면 코드
`[LQL-xxxx]`, 소스 줄, 힌트가 담긴 명확한 오류를 던집니다([11. 오류와 언어](../11-errors-and-languages.md) 참고).
KC1 ~ KC5는 람다를 로드할 때 발견됩니다. 이 경우 lambdaQL은 즉시 멈춥니다.

| # | 이렇게 쓰지 마십시오 | 이유 | 대신 이렇게 쓰십시오 |
|---|---|---|---|
| KC1 | `-Xsam-conversions=class`로 컴파일 | `SerializedLambda`가 만들어지지 않음. 람다가 클래스가 됨 | 기본값 `-Xsam-conversions=indy`를 유지 |
| KC2 | `inline fun` 본문(일반, `reified`, `crossinline`) 안에서 만든 쿼리 람다 | 람다가 람다가 아니라 클래스가 됨 | 헬퍼를 inline이 아닌 함수로 만들거나, 호출자에서 람다 리터럴을 전달 |
| KC3 | 직접 만든 Kotlin `fun interface`에 대한 호출 가능 참조(`kkey(Order::price)`) | 어댑터 클래스가 됨 | 람다 리터럴 `{ it.price }`, 또는 `Order::price`를 lambdaQL(Java) 함수에 전달 |
| KC4 | Kotlin 함수 타입 값: `val f: (Order) -> Boolean = {...}; where(f)` | lambdaQL은 `Function1` 내부를 읽을 수 없음 | 리터럴로 `where { ... }` |
| KC5 | `object : ...`(객체 식), `Serializable`이 아닌 함수형 인터페이스 | 직렬화 가능한 람다가 아님 | 람다 리터럴 |
| KC6 | 값을 고르는 `if`/`when`/`takeIf`, 일반적인 `if (a) x else y` | 값 분기는 v0.1에 없음 | `&&` / `\|\|`. 값 `CASE WHEN`은 계획됨 |
| KC7 | *(D25로 삭제됨)* | 이제 두 boolean 식의 비교를 지원함 | – |
| KC8 | 람다 안의 `in listOf(1, 2, 3)`이나 `setOf(...)` | 람다 안에서 만든 컬렉션 | 밖에서 `val ids = listOf(1, 2, 3)`, 그다음 `it.status in ids` |
| KC9 | `isBlank()`, `isNullOrBlank()` | 유니코드 공백에는 이식 가능한 SQL 형태가 없음 | `isNullOrEmpty()`나 직접 비교 |
| KC10 | *(D21로 삭제됨)* | 이제 범위와 `when`을 지원함. 이 형태들이 늦어지는 경우에만, 당분간 `==` / `\|\|` 체인으로 쓰라고 오류가 알려 줌 | – |
| KC11 | 직접 만든 inline이 아닌 확장 함수나 확장 속성, 엔티티 `operator` 함수 | lambdaQL은 호출 안을 볼 수 없음 | `inline fun`으로 만들거나 조건을 직접 작성 |
| KC12 | 캡처 변수에 대한 `n++` | 람다가 변수에 씀 | 람다 밖에서 값을 계산 |
| KC13 | 엔티티 `==` 엔티티(`it.customer == c`) | v0.1에 없음(계획됨: 식별자 비교) | `it.customer?.id == c.id` |
| KC14 | `isNaN()` | 지원하지 않음 | NaN을 저장하지 말 것. [10.5](10-rules-and-limits.md#105-kotlin-and-sql-differences) 참고 |
| KC15 | `@JvmField` 엔티티 속성 | getter가 아닌 필드 접근 | 일반 `var` / `val` |
| KC16 | 변형된 getter 이름: `internal` 속성, `value class`, `@get:JvmName` | getter를 JPA 속성에 매핑할 수 없음 | public 일반 속성 |
| KC17 | 문자열 템플릿 안의 엔티티 값 | 문자열 결합은 아직 지원하지 않음 | 컬럼을 따로 비교 |
| KC18 | Java 합성 속성 참조 `JOrder::price` | Kotlin 컴파일 오류 | `JOrder::getPrice` |
| KC19 | `Order::price.getter` 및 다른 `kotlin-reflect` API | `kotlin-reflect`를 사용하지 않음 | `Order::price` |
| KC20 | 컬럼에 대한 다른 stdlib 함수(`coerceAtLeast`, `trim`, `toLocalDate`, ...) | 허용 목록에 없음 | 계획됨(함수 매핑), 또는 `@SqlFunction`을 사용한 DB 함수 |
| KC21 | 값이나 정렬 키로 쓰는 boolean 엘비스(`{ it.flag ?: false }`) | boolean에 대한 `coalesce`는 Oracle에서 실패함(ORA-00932) | NULL 위치를 지정하여 `{ it.flag }`로 정렬. 조건에서는 `it.flag ?: false`도 괜찮음 |

<a id="915-where-lambdaql-differs-from-running-kotlin-code"></a>
## 9.15 lambdaQL이 Kotlin 코드 실행과 다른 점

이 차이는 설계의 일부입니다. 버그가 아닙니다.

| Kotlin 코드 | lambdaQL(SQL) |
|---|---|
| `!!`는 NPE를 던짐 | `!!`는 무시됨. NULL은 SQL 규칙을 따름 |
| `status == null`일 때 `it.status != 1`은 `true` | 숨은 null 검사는 무시됨: NULL에 대해 `e0.status <> 1`은 UNKNOWN이므로 행이 반환되지 않음. 포함하려면 `it.status == null \|\| ...`로 작성 |
| 고객이 없으면 `it.customer?.name == null`은 `true` | `where`에서 경로는 기본적으로 inner join이므로 고객이 없는 주문은 반환되지 않음. 포함하려면 `leftJoin`과 `c == null \|\| c.name == null`을 쓰거나, `it.customer == null`로 쿼리를 한 번 더 실행 |
| 대상이 NULL인 `when`이나 범위는 `else`로 가고, `!in`은 `true` | 행이 반환되지 않음. `NOT` 아래에서도 마찬가지 |
| `===`는 객체 동일성 | 값 비교 |
| `BigDecimal` `==`는 스케일을 비교함(`1.0 != 1.00`) | 값 비교 |
| `ignoreCase`는 Kotlin 유니코드 규칙을 사용 | DB 콜레이션을 사용(`ſ`, `İ`는 다를 수 있음) |
| `String.length`는 UTF-16 단위를 셈 | H2는 UTF-16 단위를, MariaDB는 코드 포인트를 셈 |
| `Double.NaN` 비교 | 캡처한 NaN은 오류. 저장된 NaN은 DB를 따름 |
| non-null 타입 `String`은 절대 null이 아님 | 컬럼은 여전히 NULL일 수 있음. lambdaQL은 JPA 메타데이터를 사용 |
| 정수 `/`는 소수 부분을 버림 | DB마다 다를 수 있음(MariaDB는 `5/2 = 2.5`) |
| 문자열 템플릿은 NULL을 `"null"`로 바꿈 | 아직 지원하지 않음. NULL과의 SQL 결합은 NULL이 됨 |

더 많은 규칙: [10. 규칙과 제한](10-rules-and-limits.md).

---

[Kotlin 트랙](README.md) | 이전: [8. 프로시저와 DB 함수](08-procedures-functions.md) | 다음: [10. 규칙과 제한](10-rules-and-limits.md) | [이 페이지의 Java 버전: 9. Java 노트](../java/09-java-notes.md)
