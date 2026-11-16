<a id="11-errors-and-languages"></a>
# 11. 오류와 언어

[매뉴얼 홈](README.md) | 트랙: [Java](java/README.md) | [Kotlin](kotlin/README.md) | 이전: 10. 규칙과 제한 ([Java](java/10-rules-and-limits.md) | [Kotlin](kotlin/10-rules-and-limits.md)) | 다음: 12. Spring Data JPA ([Java](java/12-spring-data-jpa.md) | [Kotlin](kotlin/12-spring-data-jpa.md))

> [!NOTE]
> **상태:** 설계됨, **아직 구현되지 않았습니다**.
> 현재 분석 코어는 단순한 메시지(지금은 한국어)와 원래 원인을 담은 `LambdaAnalysisException`을 던집니다. 아직 `[LQL-xxxx]` 코드와 메시지 번들은 없습니다.
> 오류 코드, `LambdaQLException` 기본 클래스, 네 가지 메시지 언어, SLF4J 로깅은 설계에서 확정되었습니다. 이 페이지의 코드 예제는 설계된 API를 보여 줍니다. 설계 예제입니다.

이 페이지는 두 언어 모두를 위한 것입니다. 코드는 **Java**와 **Kotlin** 섹션으로 나누어 보여 줍니다.

<a id="what-works-today"></a>
## 현재 동작하는 것

분석 코어는 이미 일부 형태를 거부합니다. 다음 단위 테스트가 이를 보여 줍니다. 이 테스트는 사용자 API가 아니라 내부 클래스를 호출하며 `LambdaAnalysisException`을 기대합니다.

| 테스트 | 거부하는 것 |
|---|---|
| [LoadExamplesTest.java](../../../query/src/test/java/org/lambdaql/engine/load/LoadExamplesTest.java) (`notSerializable`) | 직렬화할 수 없는 람다(`java.util.function.Predicate`) |
| [ConditionDecodeTest.java](../../../query/src/test/java/org/lambdaql/engine/decode/ConditionDecodeTest.java) (`valueTernaryIsRejected`) | 값 삼항 연산자: `o.getStatus() == (flag ? 1 : 2)` |

이 섹션 아래의 내용은 모두 설계입니다.

<a id="error-codes"></a>
## 오류 코드

모든 lambdaQL 메시지는 `[LQL-xxxx]` 형식의 코드로 시작합니다.
코드는 언어에 따라 바뀌지 않습니다. 코드 뒤의 텍스트가 번역됩니다.

```
[LQL-2004] ...선택한 언어로 된 메시지 텍스트...
```

| 범위 | 영역 | 문제의 예 |
|---|---|---|
| `LQL-1xxx` | 입력과 람다 로딩 | 인자가 직렬화 가능한 람다가 아닙니다. 익명 클래스입니다. 클래스 파일을 읽을 수 없습니다. |
| `LQL-2xxx` | 바이트코드 디코딩 | 람다 안의 반복문. 값 삼항 연산자(`a ? 1 : 2`, Kotlin `if (a) 1 else 2`). lambdaQL이 지원하지 않는 명령어. |
| `LQL-3xxx` | 의미와 호스트 값 | SQL에서 의미가 없는 메서드 호출. `groupBy` 키가 아닌 비집계 값. lambdaQL이 캡처 값을 계산할 때 생긴 문제. |
| `LQL-4xxx` | 렌더링과 실행 | lambdaQL이 HQL을 만들거나 실행할 때 생긴 문제. |
| `LQL-5xxx` | 경고(로그에 기록) | 인덱스를 사용할 수 없는 조건(SARGable 아님). |

코드 규칙:

- 코드는 릴리스 후 다시 사용하지 않습니다. 코드로 검색하거나 코드에서 코드 값을 검사해도 안전합니다.
- 정확한 코드 번호는 아직 확정되지 않았습니다. 메시지가 구현되면 여기에 목록을 싣습니다.

<a id="exceptions"></a>
## 예외

모든 lambdaQL 예외는 `LambdaQLException`을 상속합니다. `RuntimeException`이므로 Kotlin과 Java에서 같은 방식으로 처리합니다.

| 예외 | 발생 시점 | 예 |
|---|---|---|
| `LambdaQLException` | 모든 lambdaQL 예외의 기본 클래스 | – |
| `LambdaAnalysisException` | lambdaQL이 람다를 로드할 수 없을 때 | 람다 대신 익명 클래스(Kotlin `object : ...`). 직렬화할 수 없는 람다. |
| `UnsupportedLambdaShapeException` | lambdaQL이 지원하지 않는 모양의 바이트코드일 때 | 반복문. 값 삼항 연산자나 값 `if`/`when`. 일반적인 boolean 삼항 연산자 `c ? x : y`. |
| `TranslationException` | 람다가 HQL에서 정의된 의미를 갖지 않을 때 | getter가 아닌 엔티티 메서드. 두 엔티티를 `==`로 비교(아직 미지원). `groupBy` 키가 아닌 비집계 값. |

`LambdaQLException`의 메서드:

| 메서드 | 반환값 |
|---|---|
| `code()` | 고정된 코드, 예: `"LQL-2004"` |
| `key()` | 메시지 키(`LqlMessage` 값) |
| `args()` | 메시지 인자 |
| `getMessage()` | 예외가 생성될 때 선택되어 있던 lambdaQL 언어의 메시지 |
| `getMessage(Locale locale)` | 같은 메시지를 다른 언어로, 예: 영어는 `Locale.ROOT` |

### Java

```java
try {
    List<Order> orders = qb.from(Order.class)
        .where(o -> o.getStatus() == (flag ? 1 : 2))   // 값 삼항 연산자: 지원하지 않음
        .toList();
} catch (LambdaQLException e) {
    log.error("lambdaQL error {}: {}", e.code(), e.getMessage(Locale.ROOT));
    throw e;
}
```

### Kotlin

```kotlin
try {
    qb.from(Order::class.java)
        .where { it.status == if (flag) 1 else 2 }      // 값 if: 지원하지 않음
        .toList()
} catch (e: LambdaQLException) {
    log.error("lambdaQL error {}: {}", e.code(), e.getMessage(Locale.ROOT))
    throw e
}
```

<a id="what-a-message-has"></a>
### 메시지에 담기는 내용

- 코드, 그다음 선택한 언어의 텍스트.
- 끝에는 고정 형식으로 람다의 위치가 붙습니다. 람다 본문을 담은 메서드와 **소스 줄 번호**입니다.
- 디코딩 오류에는 바이트코드 덤프가 담긴 줄이 더 붙을 수 있습니다. 이 추가 줄은 번역하지 않습니다.
- 대부분의 메시지는 문제를 고치는 방법도 알려 줍니다. 예를 들어 Kotlin 메시지는 리스트를 람다 밖에서 만들고 캡처하라고 알려 줍니다.

<a id="in-tests-check-the-code"></a>
### 테스트에서는 코드를 검사

메시지 텍스트는 바뀔 수 있고 언어에 따라 달라집니다. 코드는 바뀌지 않습니다.

#### Java

```java
var e = assertThrows(LambdaQLException.class, () -> query.toList());
assertEquals("LQL-2004", e.code());     // 좋음
// assertEquals("Loops are ...", e.getMessage());   // 이렇게 하지 마십시오
```

#### Kotlin

```kotlin
val e = assertThrows<LambdaQLException> { query.toList() }   // org.junit.jupiter.api.assertThrows
assertEquals("LQL-2004", e.code())      // 좋음
// assertEquals("Loops are ...", e.message)          // 이렇게 하지 마십시오
```

<a id="when-errors-happen"></a>
## 오류가 발생하는 시점

lambdaQL은 세 시점에 문제를 찾습니다.

| 시점 | 검사하는 것 | 예 |
|---|---|---|
| **컴파일 시점** | Java나 Kotlin 컴파일러가 lambdaQL API의 타입을 검사합니다. | 엔티티를 정렬 키로 사용. `orderBy`를 두 번 호출. `orderBy` 뒤의 `where`. `where(...)`나 `all()` 없이 update나 delete에서 `execute()` 호출. |
| **분석 시점** | SQL이 데이터베이스로 가기 전에 lambdaQL이 람다 바이트코드를 읽습니다. | 반복문, 값 삼항 연산자, 지원하지 않는 메서드 호출, 단순한 `by` 체인이 아닌 정렬 람다, `groupBy` 키가 아닌 `select` 값. |
| **실행 시점(바인딩)** | 쿼리가 실행될 때마다 lambdaQL이 캡처 값을 바인딩합니다. | 규칙상 null을 허용하지 않는 곳의 `null` 캡처 값(아래 참고). `NaN`이나 `Infinity` 파라미터. `IN`에 사용하는 캡처 컬렉션 안의 `null` 요소. |

분석 시점의 오류는 데이터와 상관없습니다. 같은 람다는 항상 같은 방식으로 실패합니다.
lambdaQL은 실패한 결과도 캐시하므로, 잘못된 람다는 다음번에 빠르게 실패합니다.

<a id="compile-time-examples"></a>
### 컴파일 시점의 예

#### Java

```java
.orderBy((o, by) -> by.desc(o.getCustomer()))   // 컴파일 오류: 엔티티는 정렬 키가 아님
```

#### Kotlin

```kotlin
.orderBy { o, by -> by.desc(o.customer) }        // 컴파일 오류: 엔티티는 정렬 키가 아님
```

<a id="analysis-time-errors-that-only-kotlin-has"></a>
### Kotlin에만 있는 분석 시점 오류

일부 Kotlin 형태는 lambdaQL이 읽을 수 있는 람다를 만들지 않습니다. lambdaQL은 명확한 오류로 이를 거부합니다.
전체 목록은 [9. Kotlin 노트](kotlin/09-kotlin-notes.md)에 있습니다.

| Kotlin 형태 | 대신 작성할 것 |
|---|---|
| `val f: (Order) -> Boolean = { ... }; where(f)` | 람다 리터럴로 `where { ... }` |
| `inline fun` 본문 안에서 만든 쿼리 람다 | 헬퍼를 non-inline으로 만들거나, 호출하는 쪽에서 람다 리터럴을 전달 |
| `it.status in listOf(1, 2, 3)`(람다 안에서 만든 리스트) | 밖에서 `val ids = listOf(1, 2, 3)`, 그다음 `it.status in ids` |
| `-Xsam-conversions=class`로 컴파일 | 기본값 `-Xsam-conversions=indy` 유지 |

<a id="null-captured-values"></a>
### null 캡처 값

어떤 형태는 `null` 캡처 값을 받아들이고, 어떤 형태는 받아들이지 않습니다.
"NULL도 일치"를 원하면 null 검사를 직접 작성하십시오.
null 규칙 전체는 3. 조건([Java](java/03-where.md) | [Kotlin](kotlin/03-where.md))에 있습니다.

#### Java

| 형태 | `name`이 `null`일 때 | 결과 |
|---|---|---|
| `o.getDescription() == name`(참조 `==`) | 허용 | `e0.description is not distinct from :p1`(null-safe) |
| `Objects.equals(o.getDescription(), name)` | 허용 | null-safe, 위와 같음 |
| `o.getDescription().equals(name)` | 바인딩 시 오류 | Java에서는 절대 true가 아닙니다. lambdaQL은 이를 `is null`로 바꾸지 않습니다. |
| `name.equals(o.getDescription())` | 바인딩 시 오류 | Java에서는 `NullPointerException`을 던집니다. |
| `o.getStatus() > minStatus`(`minStatus`가 `null`) | 바인딩 시 오류 | null과의 크기 비교는 Java에서 의미가 없습니다. |

"NULL도 일치": `o.getDescription() == null || o.getDescription().equals(name)`.

#### Kotlin

| 형태 | 캡처 값이 `null`일 때 | 결과 |
|---|---|---|
| `it.description == name` | 허용 | `e0.description is not distinct from :p1`(null-safe) |
| `minPrice == null \|\| it.price!! > minPrice` | 허용 | null 검사를 직접 작성했으므로 lambdaQL이 이를 사용합니다. |
| `it.price!! > minPrice!!`(`minPrice`가 `null`) | 바인딩 시 오류 | null과의 크기 비교는 의미가 없습니다. |
| `it.id in ids`(`ids`에 `null` 요소가 있음) | 바인딩 시 오류 | null 요소가 있는 `IN`은 SQL의 함정입니다. |

<a id="placeholders-called-outside-a-lambda"></a>
### 람다 밖에서 호출한 플레이스홀더

어떤 메서드는 람다 안에서 읽히기 위해서만 존재합니다. 반환값은 플레이스홀더입니다.

| 메서드 | 쿼리 람다 밖에서 호출하면 |
|---|---|
| `Agg.count()`, `Agg.sum(...)` 및 다른 `Agg` 메서드 | `IllegalStateException`을 던짐 |
| 직접 만든 `@SqlFunction` 메서드 | lambdaQL은 본문을 절대 실행하지 않습니다. 설계 예제의 본문은 `DbOnlyException`을 던집니다. |

#### Java

```java
public interface OrderFns {
    @SqlFunction("FN_DISCOUNT") static Double discount(Double price) { throw new DbOnlyException(); }
}
```

#### Kotlin

```kotlin
interface OrderFns { companion object {
    @JvmStatic @SqlFunction("FN_DISCOUNT") fun discount(price: Double?): Double? = throw DbOnlyException()
} }
```

<a id="message-language"></a>
## 메시지 언어

lambdaQL은 **네 가지 언어**로 메시지를 제공합니다.

| 언어 | 로캘 |
|---|---|
| 영어 | 기본값(root) |
| 일본어 | `ja` |
| 중국어(간체) | `zh` |
| 한국어 | `ko` |

<a id="how-lambdaql-chooses-the-language"></a>
### lambdaQL이 언어를 고르는 방법

lambdaQL은 다음 세 곳을 이 순서로 확인합니다. 처음으로 설정된 값이 사용됩니다.

| 순서 | 설정 | 예 |
|---|---|---|
| 1 | 코드의 `LqlMessages.setLocale(...)` | 아래 코드 참고 |
| 2 | 시스템 속성 `lambdaql.locale` | `java -Dlambdaql.locale=ja -jar app.jar` |
| 3 | JVM 기본 로캘 | `Locale.getDefault()` |

그다음 lambdaQL은 그 로캘의 **언어**만 봅니다.

| 로캘의 언어 | 메시지 언어 |
|---|---|
| `ja`(예: `ja_JP`) | 일본어 |
| `zh`(`zh_CN`과 `zh_TW`도) | 중국어(간체) |
| `ko`(예: `ko_KR`) | 한국어 |
| 그 밖의 모든 언어(`en`, `fr`, `de`, ...) | 영어 |

#### Java

```java
// 애플리케이션 시작 시
LqlMessages.setLocale(Locale.JAPANESE);   // 일본어 메시지
LqlMessages.setLocale(null);              // 원래대로: 시스템 속성, 그다음 JVM 기본값
Locale current = LqlMessages.locale();    // lambdaQL이 현재 사용하는 로캘
```

#### Kotlin

```kotlin
// 애플리케이션 시작 시
LqlMessages.setLocale(Locale.JAPANESE)    // 일본어 메시지
LqlMessages.setLocale(null)               // 원래대로: 시스템 속성, 그다음 JVM 기본값
val current: Locale = LqlMessages.locale() // lambdaQL이 현재 사용하는 로캘
```

참고:

- lambdaQL은 언어를 직접 고릅니다. 일반적인 `ResourceBundle` 대체(fallback)를 사용하지 않습니다. 그래서 영어를 요청했는데 한국어 JVM이라는 이유로 한국어 메시지가 나오는 일은 없습니다.
- 메시지는 예외가 생성될 때 정해집니다. 나중에 다른 언어가 필요하면 `e.getMessage(locale)`를 호출하십시오.
- 서버가 영어가 아닌 JVM 로캘로 실행될 수 있습니다. 영어 로그를 원하면 `LqlMessages.setLocale(Locale.ROOT)`를 호출하거나 `-Dlambdaql.locale=en`을 사용하십시오.
- 사용자가 요청하면 중국어 번체 메시지를 추가할 수 있습니다.
- 일본어와 중국어 텍스트는 원어민이 아니라 작성자가 먼저 작성했습니다. 개선을 도와주시면 환영합니다.

<a id="for-translators"></a>
### 번역자를 위한 안내

메시지 파일은 jar 안의 UTF-8 properties 파일입니다.

```
org/lambdaql/i18n/LqlMessages.properties      영어(root)
org/lambdaql/i18n/LqlMessages_ja.properties   일본어
org/lambdaql/i18n/LqlMessages_zh.properties   중국어(간체)
org/lambdaql/i18n/LqlMessages_ko.properties   한국어
```

- 인자는 `{0}`, `{1}`, ...로 씁니다.
- lambdaQL은 `MessageFormat`을 사용하지 않습니다. 그래서 작은따옴표(`can't`)를 그대로 쓸 수 있습니다. 숫자는 서식을 적용하지 않습니다(줄 번호 `1234567`은 `1234567` 그대로입니다).
- 모든 키는 네 파일 모두에 있어야 합니다. 단위 테스트가 이를 검사합니다. 새 메시지는 네 언어 모두로 추가해야 합니다.

<a id="logging"></a>
## 로깅

lambdaQL은 **SLF4J API**로 로그를 남깁니다.

- lambdaQL은 `slf4j-api`에만 의존합니다. 로깅 백엔드(Logback, Log4j 2 등)는 직접 고릅니다.
- Spring Boot에는 이미 SLF4J와 Logback이 들어 있으므로 아무것도 추가할 필요가 없습니다.
- 경고는 `WARN` 레벨을 사용하며 `LQL-5xxx` 범위의 코드로 시작합니다.
- `WARN` 레벨이 꺼져 있으면 lambdaQL은 메시지 텍스트를 만들지 않습니다.

경고의 예(코드와 텍스트는 예시이며, 최종 텍스트는 다를 수 있습니다):

```
WARN  [LQL-5001] ...컬럼 "price"에 대한 조건은 인덱스를 사용할 수 없습니다...
```

예를 들어 다음과 같은 경우 이런 경고를 보게 됩니다.

- 조건의 컬럼 쪽에 데이터베이스 함수를 사용해서(Java `OrderFns.discount(o.getPrice()) > 10`, Kotlin `OrderFns.discount(it.price)!! > 10`) 데이터베이스가 인덱스를 사용할 수 없을 때
- 대소문자를 구분하지 않는 비교에 맞는 인덱스나 콜레이션이 그 컬럼에 없을 때
- 서브쿼리에 `orderBy`는 있지만 `limit`이 없어서 lambdaQL이 정렬을 제거할 때(정렬은 효과가 없습니다)

이런 경고를 오류로 바꾸는 "엄격 모드"가 계획되어 있습니다.

> [!NOTE]
> `lambdaql-core`는 Spring에 의존하지 않습니다. 로깅 의존성은 `slf4j-api` 하나이므로 로깅 구현체는 사용자가 선택합니다. Spring 모듈(`lambdaql-spring-data-jpa`, `lambdaql-spring-boot-starter`)은 Spring Data JPA를 가져오고, 스타터는 `spring-boot-starter-data-jpa`와 Logback도 함께 가져옵니다.

<a id="summary"></a>
## 요약

| 질문 | 답 |
|---|---|
| lambdaQL 오류는 어떻게 알아봅니까? | `LambdaQLException`이고, 메시지가 `[LQL-xxxx]`로 시작합니다. |
| 코드나 테스트에서 오류를 어떻게 검사합니까? | `e.code()`를 사용합니다. 텍스트를 비교하지 마십시오. |
| 영어가 아닌 서버에서 영어 텍스트를 얻으려면? | `LqlMessages.setLocale(Locale.ROOT)`, `-Dlambdaql.locale=en`, 또는 `e.getMessage(Locale.ROOT)`. |
| 경고는 어디로 갑니까? | SLF4J의 `WARN` 레벨로 갑니다. |

[매뉴얼 홈](README.md) | [Java 트랙](java/README.md) | [Kotlin 트랙](kotlin/README.md)
