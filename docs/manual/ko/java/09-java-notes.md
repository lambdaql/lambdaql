<a id="9-java-notes"></a>
# 9. Java 노트

> [!NOTE]
> **상태**
> - **구현됨(분석 코어):** 직렬화 가능한 람다 로딩, `this`를 캡처하는 람다, 메서드 참조 감지, `Serializable`이 아닌 람다 거부. [9.13](#913-what-you-can-check-today)의 테스트를 참고하십시오.
> - **구현됨:** 키 하나의 정렬에서 getter 메서드 참조, 예: `orderBy(Order::getPrice)`([9.9](#99-method-references) 참고).
> - **설계됨, 아직 구현되지 않음:** 이 페이지의 나머지 전부(`==`의 의미, 박싱 규칙, `String` 규칙, 쿼리 안의 메서드 참조 등).

[Java 트랙](README.md) | [매뉴얼 홈](../README.md) | 이전: [8. 프로시저와 DB 함수](08-procedures-functions.md) | 다음: [10. 규칙과 제한](10-rules-and-limits.md) | [이 페이지의 Kotlin 버전](../kotlin/09-kotlin-notes.md)

공통 페이지: [1. 개요](../01-overview.md) · [11. 오류와 언어](../11-errors-and-languages.md)

---

이 페이지는 lambdaQL 람다 안의 **Java** 코드에서 특별한 점을 모아 둔 곳입니다.
핵심은 항상 같습니다: **lambdaQL 람다는 코드가 아니라 쿼리입니다.**
lambdaQL은 작성한 내용을 읽습니다. 실행하지 않습니다. 그래서 일부 Java 실행 규칙은 적용되지 않습니다.

<a id="contents"></a>
## 목차

- [9.1 람다는 직렬화 가능한 람다여야 함](#91-lambdas-must-be-serializable-lambdas)
- [9.2 박싱과 언박싱](#92-boxing-and-unboxing)
- [9.3 박싱된 값의 동등성](#93-equality-of-boxed-values)
- [9.4 문자열](#94-strings)
- [9.5 null-safe 헬퍼](#95-null-safe-helpers)
- [9.6 작은 타입: char, short, byte, boolean](#96-small-types-char-short-byte-and-boolean)
- [9.7 float, double, BigDecimal](#97-float-double-and-bigdecimal)
- [9.8 인스턴스 메서드 안의 람다는 this를 캡처함](#98-lambdas-in-instance-methods-capture-this)
- [9.9 메서드 참조](#99-method-references)
- [9.10 기대하지 말아야 할 static 헬퍼](#910-static-helpers-you-should-not-expect)
- [9.11 그 밖의 Java 형태](#911-other-java-forms)
- [9.12 컴파일러와 빌드](#912-compiler-and-build)
- [9.13 지금 확인할 수 있는 것](#913-what-you-can-check-today)

---

<a id="91-lambdas-must-be-serializable-lambdas"></a>
## 9.1 람다는 직렬화 가능한 람다여야 함

lambdaQL은 Java 직렬화(`SerializedLambda`)를 통해 람다의 본문을 찾습니다.
`setAccessible`을 사용하지 않습니다. 캡처 값을 실제로 직렬화하지도 않습니다.

| 전달하는 것 | 결과 |
|---|---|
| 호출에 직접 작성한 람다: `where(o -> o.isActive())` | OK. `where`의 파라미터 타입은 `Serializable` 함수형 인터페이스 |
| 메서드 참조: `where(Order::isActive)` | OK([9.9](#99-method-references) 참고) |
| lambdaQL 파라미터 타입의 변수에 담아 둔 람다 | OK |
| `java.util.function.Predicate`(`Serializable`이 아님) | **거부됨**(`LambdaAnalysisException`) |
| 익명 클래스: `new Where<Order>() { ... }` | **거부됨** |

참고:

- 캡처 값(그리고 캡처한 `this`)은 `Serializable`일 필요가 **없습니다**. lambdaQL은 그 값을 읽을 뿐 직렬화하지 않습니다.
- 서브쿼리의 안쪽 람다(`sub.from(Item.class).where(i -> ...)`)는 그 자체로 `Serializable`일 필요가 없습니다. lambdaQL은 바깥 람다의 바이트코드에서 그것을 찾습니다.
- lambdaQL 함수형 인터페이스의 이름은 아직 확정되지 않았습니다. 가능하면 람다를 직접 전달하십시오.

<a id="92-boxing-and-unboxing"></a>
## 9.2 박싱과 언박싱

예제 엔티티는 박싱 타입을 사용합니다: `Long id`, `Double price`, `Integer status`.
박싱된 값을 기본형과 비교하면 javac는 **언박싱** 호출(`intValue()`, `doubleValue()`)을 추가합니다.

```java
.where(o -> o.getStatus() == 1)     // javac: o.getStatus().intValue() == 1
// HQL: e0.status = 1
```

lambdaQL은 박싱과 언박싱 호출(`valueOf`, `intValue`, `doubleValue`, ...)을 **무시합니다**. 의미만 유지합니다.

NULL에 대해 이것이 뜻하는 바:

| Java 형태 | Java에서(실행한다면) | lambdaQL에서 |
|---|---|---|
| `o.getStatus() == 1`, status가 NULL | NullPointerException | SQL 의미: UNKNOWN, 행이 반환되지 않음(규칙 R1) |
| `o.getPrice() > 100`, price가 NULL | NullPointerException | 행이 반환되지 않음(R1) |
| `!o.getActive()`(`Boolean`), active가 NULL | NullPointerException | 행이 반환되지 않음 |
| `o.getPrice() > p`, 캡처한 `Double p`가 `null` | NullPointerException | `e0.price > :p1`에 NULL 바인딩, 일치하는 행 없음(`DEBUG` 수준 `LQL-4045`) |
| `i.getQty() == q`, `qty`는 `int`, 캡처한 `Integer q`가 `null` | NullPointerException | `i.qty = :p1`에 NULL 바인딩, 일치하는 행 없음(`DEBUG` 수준 `LQL-4045`) |
| `o.getStatus() > p.intValue()`, 캡처한 `Double p`가 `null` | NullPointerException | 쿼리가 실행될 때 **예외**(`LQL-4012`) |

즉, NULL인 엔티티 값은 SQL을 따릅니다. 캡처 값을 자기 기본형으로 언박싱하는 호출(`Double.doubleValue()`, `Integer.intValue()` 등, 뒤의 확대 변환 포함)은 javac의 자동 언박싱과 같은 바이트코드이므로 실행하지 않고 박싱 값을 바인딩합니다.
다른 기본형으로 바꾸는 호출(`Double`의 `intValue()`)은 손으로만 쓸 수 있는 계산이므로 Java에서 실행하고, `null`이면 Java처럼 오류입니다.

NULL 행을 원한다면 검사를 직접 작성하십시오.

```java
.where(o -> o.getStatus() == null || o.getStatus() != 1)
// HQL: e0.status is null or e0.status <> 1
```

<a id="93-equality-of-boxed-values"></a>
## 9.3 박싱된 값의 동등성

Java에서 두 **객체** 사이의 `==`는 동일성(메모리 안의 같은 객체인지)을 검사합니다.
lambdaQL에서 두 객체 사이의 `==`는 **값 비교**입니다(결정 D12).
엔티티 값과 캡처 값은 모두 SQL 의미를 따릅니다(결정 D48, [3.6](03-where.md#36-null-rules) 참고).
박싱 캐시(-128부터 127까지의 `Integer`와 `Long`)와 `new String(...)`은 상관없습니다.

어떤 규칙이 적용되는지는 양쪽의 **Java 타입**에 따라 달라집니다.

| 왼쪽 | 오른쪽 | javac가 만드는 것 | lambdaQL 규칙 | HQL |
|---|---|---|---|---|
| `Integer` 컬럼 | `int` 상수나 변수 | 언박싱, 숫자 비교 | R1, SQL 의미 | `e0.status = 1` |
| `Integer` 컬럼 | `Integer` 변수 | 객체 비교 | R2 | `e0.status = :p1` |
| `Long` `@Id` 컬럼 | `Long` 변수 | 객체 비교 | R2, 하지만 한쪽은 절대 NULL이 아님 | `e0.id = :p1` |
| `Long` 컬럼 | `long` 변수 | 언박싱 | R1 | `e0.id = :p1` |
| `String` | `String` | 객체 비교 | R2 | `e0.description = :p1` |
| `String` 컬럼 | `String` 컬럼 | 객체 비교 | R2, SQL 의미 | `e0.description = e0.custName`(어느 한쪽이 NULL이면 불일치) |
| enum | enum | 객체 비교 | R2 | `x = :p1` |
| `Boolean` 컬럼 | `Boolean.TRUE` | 객체 비교 | R2 | `e0.active = :p1` |
| `Boolean` 컬럼 | `true` | 언박싱 | R1 | `e0.active = true` |
| 값 객체(`@Embedded`) | 같은 타입의 캡처 값 객체 | `equals` / 객체 비교 | 컬럼마다 펼침(B6) | `e0.address.city = :p1 and e0.address.zip = :p2`([3.10](03-where.md#310-association-paths) 참고) |
| 무엇이든 | `null` | null 검사 | R3 | `x is null` |
| 엔티티 | 엔티티(`o.getCustomer() == c`) | 객체 비교 | **v0.1에는 없음** | –(대신 id를 비교) |

캡처 값과 비교할 때:

| 컬럼 | 캡처 값 | HQL | 결과 |
|---|---|---|---|
| 3 | 3 | `e0.status = :p1` | true |
| NULL | `null` | `e0.status = :p1` | **false**: `= NULL`은 참이 되지 않음(`WARN` 수준 `LQL-4045`) |
| NULL | 3 | `e0.status = :p1` | false |
| 3 | `null` | `e0.status = :p1` | false |

```java
Integer wanted = request.getStatus();        // null일 수 있음
qb.from(Order.class).where(o -> o.getStatus() == wanted).toList();
// wanted = 3    -> status가 3인 행
// wanted = null -> 일치하는 행 없음(LQL-4045). NULL을 찾으려면 o.getStatus() == null
```

컬럼을 함수로 감싸지 않으므로 데이터베이스는 인덱스를 사용할 수 있습니다.

객체 사이의 `!=`는 `<>`가 됩니다. 값이 NULL인 행은 `<>`에 일치하지 않습니다(SQL 의미, D48). Java라면 이 행을 반환합니다.
이런 행을 유지하려면 `o.getStatus() == null || o.getStatus() != wanted`라고 쓰십시오.

엔티티 값 두 개(`o.getDescription() == o.getCustName()`)는 `=`와 `<>`가 됩니다. 어느 한쪽이 NULL이면 일치하지 않으며, NULL과 NULL도 마찬가지입니다.
결과가 Java와 다를 수 있으면 lambdaQL은 `LQL-5009`를 `DEBUG` 수준으로 기록합니다([3.6.3](03-where.md#363-write-your-null-checks-yourself) 참고).

<a id="94-strings"></a>
## 9.4 문자열

| Java 형태 | HQL | NULL 컬럼 | `p`가 `null` |
|---|---|---|---|
| `o.getDescription() == p` | `e0.description = :p1` | 행이 반환되지 않음 | 실행됨, 일치하는 행 없음(`LQL-4045`) |
| `o.getDescription().equals(p)` | `e0.description = :p1` | 행이 반환되지 않음 | 실행됨, 일치하는 행 없음(`LQL-4045`) |
| `p.equals(o.getDescription())` | `e0.description = :p1` | 행이 반환되지 않음 | 실행됨, 일치하는 행 없음(`LQL-4045`) |
| `"abc".equals(o.getDescription())`(상수 수신 객체) | `e0.description = 'abc'`(상수는 NULL이 될 수 없으므로 단순한 `=`) | 행이 반환되지 않음 | – |
| `Objects.equals(o.getDescription(), p)` | `e0.description = :p1` | 행이 반환되지 않음 | 실행됨, 일치하는 행 없음(`LQL-4045`) |
| `o.getDescription().compareTo(p) < 0` | `e0.description < :p1` | 행이 반환되지 않음 | 실행됨, 일치하는 행 없음(`LQL-4045`) |
| `o.getDescription() == null` | `e0.description is null` | 반환됨 | – |

기억할 점:

- 일반 Java에서 문자열에 대한 `==`는 버그입니다. lambdaQL 람다 안에서는 올바른 값 비교입니다.
- `==`, `equals`, `Objects.equals`는 NULL에 대해 같게 동작합니다. 컬럼이 NULL이거나 `p`가 `null`이면 일치하는 행이 없습니다. NULL 행을 원하면 `o.getDescription() == null`을 직접 쓰십시오.
- 비교는 Java가 아니라 **데이터베이스 콜레이션**을 따릅니다. MariaDB `_ci` 콜레이션은 대소문자와 끝의 공백을 무시합니다. Oracle은 `''`를 NULL로 저장합니다.
- 람다에 직접 쓴 문자열 상수는 HQL 리터럴이 되고, 작은따옴표는 escape됩니다(`'O''Brien'`). 캡처한 문자열은 항상 바인드 파라미터입니다([3.3](03-where.md#33-basic-form) 참고).
- 컬럼에 대한 문자열 메서드(`startsWith`, `contains`, `trim`, `toUpperCase`)는 계획 중이며 v0.1에는 없습니다.
- 문자열 `+`는 v0.1에서 거부됩니다. 캡처 값만 있어도 마찬가지입니다(javac는 `+`를 `invokedynamic`으로 컴파일함). 문자열은 람다 전에 만드십시오.

```java
String code = "A" + suffix;                                  // 밖: 일반 Java
qb.from(Order.class).where(o -> o.getDescription() == code).toList();
```

<a id="95-null-safe-helpers"></a>
## 9.5 null-safe 헬퍼

다음 Java 헬퍼는 `==`와 같은 규칙, 즉 SQL 의미를 따릅니다(규칙 R5, D48). 캡처 값 `null`은 어떤 행에도 일치하지 않습니다.

| Java | HQL | 참고 |
|---|---|---|
| `Objects.equals(o.getStatus(), s)` | `e0.status = :p1` | `s`가 `null`이면 일치하는 행 없음(`LQL-4045`) |
| `!Objects.equals(o.getStatus(), 1)` | `e0.status <> 1` | NULL 행이 **반환되지 않음**(SQL 의미. Java라면 반환) |
| `Objects.equals(o.getDescription(), o.getCustomer().getName())` | `e0.description = j1.name` + `join e0.customer j1` | 두 컬럼: 어느 한쪽이 NULL이면 불일치 |
| `Boolean.TRUE.equals(o.getActive())` | `e0.active = :p1` | |
| `!Boolean.TRUE.equals(o.getActive())` | `e0.active <> :p1` | NULL 행이 **반환되지 않음**(SQL 의미) |
| `"x".equals(o.getDescription())` | `e0.description = 'x'` | 상수 수신 객체: NULL이 될 수 없으므로 단순한 `=` |
| `set` 값 안의 `Objects.requireNonNullElse(o.getPrice(), 0.0)` | `coalesce(o.price, 0.0)` | [7. 수정, 삽입, 삭제](07-dml.md) 참고 |

`!=`와 비교:

```java
.where(o -> o.getStatus() != 1)                 // e0.status <> 1           (NULL 행은 반환되지 않음)
.where(o -> !Objects.equals(o.getStatus(), 1))  // e0.status <> 1           (NULL 행은 역시 반환되지 않음)
.where(o -> o.getStatus() == null || !Objects.equals(o.getStatus(), 1))   // NULL 행이 반환됨
```

<a id="96-small-types-char-short-byte-and-boolean"></a>
## 9.6 작은 타입: char, short, byte, boolean

JVM에는 `char`, `short`, `byte`, `boolean`을 위한 별도의 비교 명령어가 없습니다. javac는 이들을 `int`로 컴파일합니다.
lambdaQL은 이를 알고 있습니다.

- `long`, `int`, `short`, `byte`, `char`, `float`, `double` 값의 비교를 지원합니다(규칙 R1).
- 상수는 컬럼의 타입으로 변환됩니다. 예를 들어 `int` 상수 `10`은 `short` 컬럼에 대해 `short` 값이 됩니다.
- `boolean` getter는 단독으로 조건이 됩니다: `o.isActive()`는 `e0.active = true`가 됩니다.
- `o.isActive() == (o.getStatus() > s)`(두 boolean 식)를 지원합니다. `AND`/`OR`가 됩니다([3.7](03-where.md#37-boolean-columns) 참고).
- 기본형 속성(`boolean active`, `int qty`)은 `@Id`나 `nullable = false` 컬럼처럼 "절대 NULL이 아님"으로 취급합니다. 그래서 lambdaQL은 이 속성에 대한 null 검사를 생략할 수 있고, `o.getId() == null`은 `1=0`이 됩니다([3.6.2](03-where.md#362--on-objects-compares-values)).

<a id="97-float-double-and-bigdecimal"></a>
## 9.7 float, double, BigDecimal

| 주제 | 규칙 |
|---|---|
| `float` / `double` 상수(`100.0`, `1.5f`) | 람다 안의 다른 상수처럼 HQL에 리터럴로 씀(`100.0`, `1.5F`)(D37). 람다 안에서 만든 `BigDecimal`(`new BigDecimal("1.5")`)은 계산한 값이므로 바인드 파라미터 |
| 비트 단위까지 같은 결과 | **약속하지 않음**. 데이터베이스마다 `float`와 `double`을 저장하고 비교하는 방식이 다름. lambdaQL은 `CAST`를 추가하지 않음. 컬럼 매핑을 직접 선택하십시오(예: Oracle `binary_float` / `binary_double`) |
| 캡처한 `NaN`, `Infinity` | 쿼리가 실행될 때 **예외** |
| 데이터베이스에 저장된 `NaN` | 데이터베이스를 따름(H2는 가장 큰 값으로 정렬, MariaDB는 저장할 수 없음) |
| `BigDecimal` 동등성 | Java `equals`는 스케일을 비교함(`1.0`은 `1.00`이 아님). lambdaQL은 **값**을 비교함 |
| 정수 `/` | 데이터베이스마다 결과가 다름(MariaDB `5/2 = 2.5`). 최종 규칙은 정해지지 않음. 피하십시오 |

<a id="98-lambdas-in-instance-methods-capture-this"></a>
## 9.8 인스턴스 메서드 안의 람다는 this를 캡처함

인스턴스 메서드 안의 람다가 필드를 사용하면, javac는 람다 본문을 **인스턴스 메서드**로 만들고 `this`를 캡처합니다.

```java
@Service
public class OrderService {

    private final QueryBuilder qb;
    private int defaultStatus = 1;

    public OrderService(QueryBuilder qb) {
        this.qb = qb;
    }

    public List<Order> defaults() {
        return qb.from(Order.class)
                 .where(o -> o.getStatus() == defaultStatus)   // this.defaultStatus를 뜻함
                 .toList();
        // HQL: select e0 from Order e0 where e0.status = :p1
    }
}
```

**SQL 블록 읽는 법.** `…`는 줄여 쓴 select 목록(엔티티의 모든 컬럼)이고, `?`는 바인드 파라미터입니다. 각 줄은 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)에서 나옵니다. 이 테스트는 각 쿼리를 Hibernate 6.6.5로 H2 2.3, MariaDB 10.11, Oracle 23.26에서 실행하며, Hibernate의 SQL이 바뀌면 실패합니다.

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `this-capture`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=?
```

lambdaQL이 하는 일:

- 첫 번째 캡처 값이 `this`라는 것을 압니다. (이 부분은 구현되어 있고 테스트되었습니다.)
- `this.defaultStatus`는 엔티티를 사용하지 않으므로 Java에서 계산되어 파라미터로 바인딩됩니다.
- 필드는 **쿼리가 실행될 때마다** 읽습니다. 분석 결과는 객체마다가 아니라 람다마다 캐시됩니다.
- 클래스가 `Serializable`일 필요는 없습니다.
- 엔티티를 사용하지 않는 `this`의 메서드 호출(`this.minPrice()`)도 쿼리가 실행될 때마다 Java에서 계산됩니다. 부작용이 없도록 유지하십시오.

지역 변수는 다릅니다. Java는 람다가 만들어질 때 그 값을 복사합니다. Java는 지역 변수가 사실상 final이어야 한다고 요구합니다.

```java
int st = defaultStatus;                                   // 지금 복사됨
qb.from(Order.class).where(o -> o.getStatus() == st).toList();
```

두 형태 모두 같은 HQL을 만듭니다.

<a id="99-method-references"></a>
## 9.9 메서드 참조

| 메서드 참조 | 같은 의미 | 상태 |
|---|---|---|
| `where(Order::isActive)` | `where(o -> o.isActive())` | 설계됨(감지는 구현됨) |
| `orderBy(Order::getPrice)` | `orderBy(o -> o.getPrice())` | 구현됨, 테스트함 |
| `by` 정렬 형태 `orderBy((o, by) -> ...)`의 메서드 참조 | – | **허용 안 됨**. 람다를 사용하십시오(규칙 S13) |
| 조인 정렬 람다 안의 메서드 참조 | – | **허용 안 됨**. 람다를 사용하십시오(규칙 S13) |

lambdaQL은 메서드 참조를 로드할 때 그것을 감지합니다. 참조된 메서드의 본문은 읽지 않습니다. `o.isActive()` 호출을 직접 만듭니다.

어떤 메서드 참조는 컴파일되지만 의미가 잘못됩니다. lambdaQL은 이를 분석 시점에 거부합니다(규칙 S13).

- 인자가 하나인 인스턴스 메서드를 인자가 둘인 람다로 사용한 경우(두 번째 인자가 메서드 인자가 됨),
- 인자가 둘인 static 메서드를 인자가 둘인 람다로 사용한 경우.

`orderBy`에서는 엔티티의 getter만 허용합니다. static 메서드, 인자가 있는 메서드, 객체에 묶인 참조는 거부됩니다.

단순한 getter가 아니라면 람다를 사용하십시오.

<a id="910-static-helpers-you-should-not-expect"></a>
## 9.10 기대하지 말아야 할 static 헬퍼

lambdaQL에는 **static DSL이 없습니다**. 선택 사항인 `Agg` import를 빼면 lambdaQL을 위해 `import static`을 할 필요가 없습니다.
헬퍼는 **람다 인자**(`by`, `sub`)로 들어오거나, 일반 Java로 작성합니다.

| 기대할 수 있는 것 | lambdaQL 방식 |
|---|---|
| `orderBy(asc(...), desc(...))` | `orderBy((o, by) -> by.asc(...).desc(...))` ([4](04-order-paging.md)) |
| `exists(...)`, `in(...)`, `notIn(...)` | `(o, sub) -> sub.from(X.class).where(...).exists()` / `.select(...).contains(x)` ([5](05-subquery.md)) |
| `set(path, value)` | `set(o -> o.setX(v))` 또는 `set(o -> value(o.getX(), v))`(`Dml.value`, D49) ([7](07-dml.md)) |
| Q 클래스, `Order_.price` | getter: `o.getPrice()` |
| `count()`, `sum(...)`, ... | `Agg.count()`, `Agg.sum(...)`: static 헬퍼 클래스는 `Agg`와 `Dml` 둘뿐 ([6](06-select-group.md)) |
| `coalesce(a, b)` | `set` 값 안의 `Objects.requireNonNullElse(a, b)` |
| `like(...)`, `lower(...)`, `upper(...)` | 계획 중(컬럼에 대한 문자열 함수) |
| `currentTimestamp()` | `LocalDateTime.now()`. 애플리케이션에서 계산되어 바인딩됨 |
| 엔티티 값에 대한 직접 만든 static 유틸리티 메서드 | **거부됨**. 단, `@SqlFunction`을 붙인 DB 함수는 예외 ([8](08-procedures-functions.md)) |

**캡처 값만** 사용하는 static 메서드는 괜찮습니다. 쿼리가 실행될 때마다 Java에서 실행됩니다.

```java
.where(o -> o.getUpdateAt().isAfter(LocalDateTime.of(2026, 1, 1, 0, 0)))   // OK: Java에서 계산됨
.where(o -> MyUtil.normalize(o.getDescription()) == text)                   // 거부됨: 엔티티를 사용함
```

> [!NOTE]
> 저장소에는 아직 예전 프로토타입 코드(예: `JpqlFunction` 클래스)가 남아 있습니다.
> 새 설계의 일부가 아닙니다. 쿼리에서 사용하지 마십시오.

<a id="911-other-java-forms"></a>
## 9.11 그 밖의 Java 형태

| Java 형태 | lambdaQL 람다 안에서 |
|---|---|
| 식 하나 `o -> ...` | 일반적인 형태 |
| 지역 변수가 있는 블록 람다 `o -> { ...; return ...; }` | `where`, `orderBy`, `select`에서 거부됨 |
| setter 호출만 있는 블록 람다 | `set`, `values`, `insert ... select`에서 허용 ([7](07-dml.md)) |
| `if`, `for`, `while`, `switch`(문 또는 식), `try` | 거부됨 |
| 삼항 연산자 `c ? x : y` | `c ? x : false`, `c ? true : x`, `c ? false : true`만 ([3.5](03-where.md#35-and-or-not)) |
| boolean에 대한 `&`, `\|`, `^` | 거부됨. `&&`, `\|\|`를 사용하십시오 |
| 스트림, 중첩 람다(`sub` 서브쿼리 제외) | 거부됨 |
| 람다 안의 `List.of(...)` | 거부됨. `groupBy` 키로 쓰는 경우는 예외 |
| `compareTo(...) < 0`, `isBefore`, `isAfter` | `<`, `>`가 됨 |
| `captured.length`(캡처 값의 배열) | Java에서 계산됨 |
| 레코드 | 결과 타입(`select`)으로, 그리고 파생 테이블과 CTE의 행 타입으로(`r.total()`로 읽음) |
| `var` | 빌더 변수에 사용하십시오. 빌더 타입 이름은 확정되지 않음 |
| 제네릭 `value(o.getStatus(), "abc")` | Java에서는 컴파일됨. 분석 시점에 컬럼 타입과 대조해 `LQL-3065` 오류 |
| 자기 `value(...)` 메서드가 있는 클래스(예: `value` 컴포넌트가 있는 레코드) 안의 `value(...)` | 정적 import가 가려짐. `Dml.value(...)`로 작성 |
| 중첩 람다에서 같은 파라미터 이름 | Java가 허용하지 않음. `sub2`, `i`, `x`를 사용하십시오 |

<a id="912-compiler-and-build"></a>
## 9.12 컴파일러와 빌드

| 주제 | 알아 둘 점 |
|---|---|
| 컴파일러 | lambdaQL은 **javac**로 테스트합니다. **ECJ**(Eclipse 컴파일러)로 컴파일한 람다는 보장하지 않습니다. Eclipse IDE와 Eclipse JDT 기반의 다른 도구는 ECJ를 사용합니다. 테스트는 Gradle이나 Maven(javac)으로 실행하십시오 |
| JDK 버전 | Java 21 |
| ASM | lambdaQL은 ASM 9.11.1로 클래스 파일을 읽습니다(Java 28 클래스 파일까지). 더 새로운 JDK로 컴파일한다면 ASM을 업데이트하십시오. lambdaQL은 ASM을 셰이딩하지 않으므로 직접 업데이트할 수 있습니다 |
| `-parameters` | 권장. lambdaQL이 프로시저 인자 이름을 확인할 수 있습니다([8.4](08-procedures-functions.md#84-warning-argument-order)) |
| 디버그 정보 | 줄 번호를 유지하십시오(javac 기본값). lambdaQL은 오류 메시지에 소스 줄을 넣습니다 |
| IntelliJ "Number objects compared using `==`" | lambdaQL 람다 안에서는 의도한 동작입니다. `@SuppressWarnings("NumberEquality")`를 사용할 수 있습니다 |
| 문자열 `==`에 대한 IntelliJ 경고 | lambdaQL 람다 안에서는 이것도 의도한 동작입니다 |

<a id="913-what-you-can-check-today"></a>
## 9.13 지금 확인할 수 있는 것

분석 코어는 구현되어 있습니다. 다음 테스트는 대부분 사용자 API가 아니라 내부 클래스를 사용하지만, 이 페이지의 Java 동작을 보여 줍니다.

| 테스트 | 보여 주는 것 |
|---|---|
| [`LoadExamplesTest.java`](../../../../query/src/test/java/org/lambdaql/engine/load/LoadExamplesTest.java) `instanceLambda` | 필드를 사용하는 람다는 `this`를 첫 번째 캡처 값으로 캡처함([9.8](#98-lambdas-in-instance-methods-capture-this)) |
| [`LoadExamplesTest.java`](../../../../query/src/test/java/org/lambdaql/engine/load/LoadExamplesTest.java) `methodReference` | `Order::isActive`가 메서드 참조로 감지됨([9.9](#99-method-references)) |
| [`LoadExamplesTest.java`](../../../../query/src/test/java/org/lambdaql/engine/load/LoadExamplesTest.java) `notSerializable` | `java.util.function.Predicate`가 거부됨([9.1](#91-lambdas-must-be-serializable-lambdas)) |
| [`ConditionDecodeTest.java`](../../../../query/src/test/java/org/lambdaql/engine/decode/ConditionDecodeTest.java) | 비교 안의 언박싱(`o.getId() > minId`), `&&`, `\|\|`, `!` |
| [`SortQueryTest.java`](../../../../query/src/test/java/org/lambdaql/query/SortQueryTest.java) 영역 `sort-key` | `Order::getPrice`가 `o -> o.getPrice()`와 똑같이 키 하나의 정렬로 동작함([9.9](#99-method-references)) |
| [`HostEvaluatorTest.java`](../../../../query/src/test/java/org/lambdaql/engine/sem/HostEvaluatorTest.java) `convertNumbers` | 숫자가 컬럼 타입으로 변환됨(예: `int`에서 `short`로)([9.6](#96-small-types-char-short-byte-and-boolean)) |

---

[Java 트랙](README.md) | 이전: [8. 프로시저와 DB 함수](08-procedures-functions.md) | 다음: [10. 규칙과 제한](10-rules-and-limits.md) | [이 페이지의 Kotlin 버전](../kotlin/09-kotlin-notes.md)
