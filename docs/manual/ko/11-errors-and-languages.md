<a id="11-errors-and-languages"></a>
# 11. 오류와 언어

[매뉴얼 홈](README.md) | 트랙: [Java](java/README.md) | [Kotlin](kotlin/README.md) | 이전: 10. 규칙과 제한 ([Java](java/10-rules-and-limits.md) | [Kotlin](kotlin/10-rules-and-limits.md)) | 다음: 12. Spring Data JPA ([Java](java/12-spring-data-jpa.md) | [Kotlin](kotlin/12-spring-data-jpa.md))

> [!NOTE]
> **상태:** 구현됨, 테스트함(결정 D33).
> 모든 메시지에 `[LQL-xxxx]` 코드가 붙고, 메시지는 영어, 일본어, 중국어(간체), 한국어로 제공합니다.
> 예외는 `LambdaQLException`과 그 하위 클래스입니다. 경고는 SLF4J로 기록합니다.
> **(테스트됨)** 표시가 있는 예제는 테스트 코드([`LqlMessagesTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/i18n/LqlMessagesTest.java), [`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java))에서 그대로 가져온 것입니다. 이 페이지의 다른 코드는 설계 예제입니다.
> 아직 구현되지 않음: 경고를 오류로 바꾸는 엄격 모드.

이 페이지는 두 언어 모두를 위한 것입니다. 코드는 **Java**와 **Kotlin** 섹션으로 나누어 보여 줍니다.

<a id="error-codes"></a>
## 오류 코드

모든 lambdaQL 메시지는 `[LQL-xxxx]` 형식의 코드로 시작합니다.
코드는 언어에 따라 바뀌지 않습니다. 코드 뒤의 텍스트만 번역합니다.

**(테스트됨)** [`LqlMessagesTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/i18n/LqlMessagesTest.java), 영역 `format`:

```java
LqlMessages.setLocale(Locale.ENGLISH);
assertEquals("[LQL-4014] limit must be 0 or more: -1", LqlMessage.LIMIT_NEGATIVE.format(-1));
// the same message in another language: the code stays
assertEquals("[LQL-4014] limit은 0 이상이어야 합니다: -1", LqlMessage.LIMIT_NEGATIVE.format(Locale.KOREAN, -1));
```

모든 메시지는 enum `org.lambdaql.i18n.LqlMessage`의 값입니다. 값마다 코드와 인자 개수가 있습니다.

| 범위 | 영역 | 현재 코드 | 예외 |
|---|---|---|---|
| `LQL-1xxx` | 입력과 람다 로딩 | `LQL-1001`~`LQL-1013` | `LambdaAnalysisException` |
| `LQL-2xxx` | 바이트코드 디코딩 | `LQL-2001`~`LQL-2025` | `UnsupportedLambdaShapeException` |
| `LQL-3xxx` | 의미 해석과 Java에서 계산하는 값 | `LQL-3001`~`LQL-3064` | `TranslationException` |
| `LQL-4xxx` | 렌더링, 바인딩, 실행 | `LQL-4001`~`LQL-4045`, `LQL-4999`(내부 오류) | `LambdaQLException` 자체, 또는 JDK/JPA 표준 예외([예외](#exceptions) 참고). `LQL-4045`는 `WARN` 로그 |
| `LQL-5xxx` | 경고(로그에 기록) | `LQL-5001`~`LQL-5009` | –(로그 한 줄. 레벨은 `WARN`이며, `LQL-5004`~`LQL-5006`, `LQL-5008`과 기본 정렬 모드의 `LQL-5003`은 `INFO`, `LQL-5009`는 `DEBUG`) |

실제 코드의 예:

| 코드 | 예외 | 메시지(한국어) |
|---|---|---|
| `LQL-1002` | `LambdaAnalysisException` | Serializable 람다만 분석할 수 있습니다. java.util.function 대신 lambdaQL 람다 타입을 쓰세요: {0} |
| `LQL-2004` | `UnsupportedLambdaShapeException` | 람다 안의 반복문은 지원하지 않습니다. 목록은 람다 밖에서 만들고 캡처하세요. |
| `LQL-3005` | `TranslationException` | Agg 집계 함수는 where에 쓸 수 없습니다. having을 쓰세요(J3): Agg.{0} |
| `LQL-3035` | `TranslationException` | 집계와 함께 쓰는 값은 groupBy 키여야 합니다(J3): {0} |
| `LQL-3058` | `TranslationException` | DB 함수 {0}은 SQL 이름이 따로 있어 등록이 필요합니다. 클래스를 lambdaql.functions.classes 설정에 넣으세요. |
| `LQL-3064` | `TranslationException` | 파생 테이블·CTE의 행 타입은 Java record나 Kotlin data class여야 합니다(J5): {0} |
| `LQL-4009` | `LambdaQLException` | null 값은 <, <=, >, >=로 비교할 수 없습니다(Java에서는 NullPointerException): {0} |
| `LQL-4014` | `IllegalArgumentException` | limit은 0 이상이어야 합니다: {0} |
| `LQL-4025` | `NonUniqueResultException` | one()의 조건에 맞는 행이 2개 이상입니다. 조건을 좁히거나 first()를 쓰세요: {0} |
| `LQL-4034` | `DbOnlyException` | {0}은 DB 함수입니다. lambdaQL 람다 안에서만 쓰며 Java에서는 실행되지 않습니다. |
| `LQL-4036` | `LambdaQLException` | 스텁이 DB 프로시저 {0}와 맞지 않습니다: {1} |
| `LQL-4040` | `SortNotAllowedException` | {0}에서는 이 이름으로 정렬할 수 없습니다: {1}. SortRules의 allow나 alias로 허용하세요. |
| `LQL-4043` | `LambdaQLException` | {0}의 스텁 인자가 DB 인자와 이름은 같고 순서만 다릅니다. 스텁: {1}, DB: {2} |
| `LQL-4044` | `LambdaQLException` | Oracle 패키지에 이 이름의 프로시저가 여러 개 있습니다(오버로드). 어느 것을 호출할지 구분할 수 없습니다: {0} |
| `LQL-4045` | –(실행 시 `WARN` 로그) | {0}에서 캡처 값이 null입니다. SQL에서 = NULL과 <> NULL은 어떤 행과도 맞지 않습니다. 람다에 x == null을 쓰거나 쿼리 전에 값을 확인하세요. |
| `LQL-5001` | –(`WARN` 로그) | 함수 {0}가 열 {1}에 씌워져 있어 열의 일반 인덱스를 쓸 수 없고 전체 스캔이 될 수 있습니다. 열을 값과 비교하도록 바꾸거나 (Oracle) 함수 기반 인덱스를 만드세요: {2} |
| `LQL-5002` | –(`WARN` 로그) | limit 없는 서브쿼리 정렬은 결과에 영향이 없어 제거했습니다(Q4): {0} |
| `LQL-5003` | –(`INFO` 또는 `WARN` 로그) | 외부 정렬이 {0}의 정렬 규칙에 없습니다: {1}. 지금은 실행하지만 SortMode.STRICT에서는 거부됩니다. |
| `LQL-5004` | –(`INFO` 로그) | 클래스 파일에 스텁 인자 이름이 없어 {0}의 인자 순서를 확인하지 않습니다. -parameters(Kotlin: -java-parameters)로 컴파일하거나 @DbParam을 쓰세요. |
| `LQL-5005` | –(`INFO` 로그) | DB 메타데이터에서 {0}의 인자 정보를 읽지 못했습니다({1}). 스텁을 대조하지 않으며, IN·INOUT은 스텁 순서대로, 그 뒤에 OUT 인자가 온다고 봅니다. |
| `LQL-5006` | –(`INFO` 로그) | {0}의 오버로드를 확인하지 못했습니다({1}). 확인 없이 호출합니다. |
| `LQL-5007` | –(`WARN` 로그) | MariaDB 함수 {0}가 NOT DETERMINISTIC이라 행마다 다시 계산되고, 비교하는 열의 인덱스를 범위 검색에 쓰지 못합니다. 결정적 함수라면 DETERMINISTIC으로 선언하세요: {1} |
| `LQL-5008` | –(`INFO` 로그) | {0}의 인덱스·함수 메타데이터를 읽지 못했습니다({1}). 메타데이터 없이 SARGable 검사를 계속합니다. |
| `LQL-5009` | –(`DEBUG` 로그) | SQL 의미: {0}에서 값이 NULL인 행은 조건에 맞지 않습니다(Java의 null 처리와 다름). 포함하려면 x == null || ...처럼 null 검사를 적으세요. |

`{0}`, `{1}`은 인자입니다. 예를 들어 식이나 프로시저 이름이 들어갑니다.
전체 목록은 메시지 파일에 있습니다([번역자를 위한 안내](#for-translators) 참고).

코드 규칙:

- 코드는 릴리스 후 다시 사용하지 않습니다. 코드로 검색하거나 코드에서 코드 값을 검사해도 안전합니다.
- 모든 코드가 중복 없이 `LQL-`와 네 자리 숫자 형식인지 단위 테스트가 검사합니다.

<a id="exceptions"></a>
## 예외

모든 lambdaQL 예외는 `org.lambdaql.LambdaQLException`을 상속합니다. `RuntimeException`이므로 Kotlin과 Java에서 같은 방식으로 처리합니다.
예외 클래스는 코드 범위에 따라 정해집니다.

| 예외 | 코드 | 발생 시점 | 예 |
|---|---|---|---|
| `LambdaQLException` | `LQL-4xxx` | 모든 lambdaQL 예외의 기본 클래스. 렌더링, 바인딩, 실행 문제에는 이 클래스 자체를 던집니다 | DB와 맞지 않는 프로시저 스텁(`LQL-4036`). DB 인자 이름과 순서가 다른 스텁 인자(`LQL-4043`, [8.4](java/08-procedures-functions.md#84-warning-argument-order)의 `@DbParam` 참고). |
| `LambdaAnalysisException` | `LQL-1xxx` | lambdaQL이 람다를 로드할 수 없을 때 | 람다 대신 익명 클래스(Kotlin `object : ...`). 직렬화할 수 없는 람다(`LQL-1002`). |
| `UnsupportedLambdaShapeException` | `LQL-2xxx` | lambdaQL이 지원하지 않는 모양의 바이트코드일 때 | 반복문(`LQL-2004`). 값 삼항 연산자나 값 `if`/`when`. 일반적인 boolean 삼항 연산자 `c ? x : y`. |
| `TranslationException` | `LQL-3xxx` | 람다가 HQL에서 정의된 의미를 갖지 않을 때 | getter가 아닌 엔티티 메서드. 두 엔티티를 `==`로 비교(아직 미지원). `groupBy` 키가 아닌 비집계 값(`LQL-3035`). |
| `DbOnlyException` | `LQL-4034` | 직접 만든 `@SqlFunction` 메서드를 lambdaQL 람다 밖의 Java에서 호출했을 때 | `OrderFns.discount(1.0)`([람다 밖에서 호출한 플레이스홀더](#placeholders-called-outside-a-lambda) 참고) |
| `SortNotAllowedException` | `LQL-4040` | `SortMode.STRICT`에서 외부 정렬(Spring `Sort`, `SortSpec.ofPath`)이 정렬 규칙에 없는 이름을 사용할 때 | `name`만 허용한 상태의 `Sort.by("grade")`([4.10 정렬 정책](java/04-order-paging.md#sort-policy) 참고). 웹 애플리케이션은 보통 HTTP 400으로 응답([12.5](java/12-spring-data-jpa.md#sort-policy-with-spring)) |

이 클래스는 모두 `org.lambdaql` 패키지에 있습니다.

**표준 예외는 타입을 유지합니다.** Java 코드가 JDK나 JPA 예외를 기대하는 경우에는 그 타입을 던지고, 메시지에만 코드를 붙입니다.

| 예외 | 예 | 코드 |
|---|---|---|
| `IllegalArgumentException` | `limit(-1)` | `LQL-4014` |
| `IllegalStateException` | primitive 생성자 인자에 들어가는 NULL 값(규칙 J4). 람다 밖에서 호출한 `Agg.sum(...)`. 행 수보다 요소 수가 적은 `toSet()` | `LQL-4021`, `LQL-4032`, `LQL-4019` |
| `jakarta.persistence.NonUniqueResultException` | `one()`의 결과가 2행 이상. `executeOne()`의 조건에 맞는 행이 2행 이상 | `LQL-4025`, `LQL-4023` |
| `jakarta.persistence.TransactionRequiredException` | 트랜잭션 없는 update, insert, delete | `LQL-4024` |

`LambdaQLException`의 메서드:

| 메서드 | 반환값 |
|---|---|
| `code()` | 고정된 코드, 예: `"LQL-2004"` |
| `key()` | 메시지 키(`LqlMessage` 값) |
| `args()` | 메시지 인자 |
| `where()` | 람다의 위치(람다 본문을 담은 메서드, 알 수 있으면 줄 번호), 없으면 `null` |
| `getMessage()` | 예외가 생성될 때 선택되어 있던 lambdaQL 언어의 메시지 |
| `getMessage(Locale locale)` | 같은 메시지를 다른 언어로, 예: 영어는 `Locale.ROOT` |

**(테스트됨)** [`LqlMessagesTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/i18n/LqlMessagesTest.java), 영역 `exception`:

```java
LqlMessages.setLocale(Locale.KOREAN);
LambdaQLException e = LqlMessage.LOOP_NOT_SUPPORTED.exceptionAt("app.Repo.lambda$find$0 (line 12)");
// check the code in tests, not the text
assertEquals("LQL-2004", e.code());
assertInstanceOf(UnsupportedLambdaShapeException.class, e);
assertTrue(e.getMessage().startsWith("[LQL-2004] 람다 안의 반복문은"), e.getMessage());
// the same message in English, with the place of the lambda at the end
assertEquals("[LQL-2004] Loops are not supported in a lambda. Build the list outside the lambda and capture it."
        + " — app.Repo.lambda$find$0 (line 12)", e.getMessage(Locale.ROOT));
```

이 테스트는 예외를 직접 만듭니다. 실제 코드에서는 lambdaQL이 람다를 읽을 때 예외를 만듭니다.
처리 방법은 다음과 같습니다(설계 예제).

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
- 끝에는 고정 형식으로 람다의 위치가 붙습니다. ` — ` 다음에 람다 본문을 담은 메서드와 **소스 줄 번호**가 옵니다(`where()`로도 얻을 수 있습니다).
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
`=`나 `<>`에 쓰인 캡처 값 `null`은 어떤 행에도 일치하지 않습니다(SQL 의미, 결정 D48). 이때 lambdaQL은 실행 시점에 `LQL-4045`를 `WARN` 수준으로 기록합니다.
NULL과 일치시키려면 null 검사를 직접 작성하십시오.
null 규칙 전체는 3. 조건([Java](java/03-where.md) | [Kotlin](kotlin/03-where.md))에 있습니다.

#### Java

| 형태 | `name`이 `null`일 때 | 결과 |
|---|---|---|
| `o.getDescription() == name`(참조 `==`) | 실행됨 | NULL을 바인딩한 `e0.description = :p1`: 일치하는 행 없음. `WARN` 수준 `LQL-4045` |
| `Objects.equals(o.getDescription(), name)` | 실행됨 | 위와 같음 |
| `o.getDescription().equals(name)` | 실행됨 | 위와 같음 |
| `name.equals(o.getDescription())` | 실행됨 | 위와 같음 |
| `o.getStatus() > minStatus`(`minStatus`가 `null`) | 실행됨 | NULL을 바인딩한 `e0.status > :p1`: 일치하는 행 없음. `WARN` 수준 `LQL-4045` |
| `name.trim()` 같은 캡처 값의 메서드 호출 | 오류(`LQL-4012`) | 쿼리 전에 Java에서 값을 준비하는 단계이므로 Java처럼 `NullPointerException`에 해당합니다. |

NULL을 찾으려면 `o.getDescription() == null`이라고 씁니다.
`name`이 null일 때 필터를 생략하려면 `name == null || o.getDescription().equals(name)`이라고 씁니다(가드는 쿼리 실행 전에 결정됨).

#### Kotlin

| 형태 | 캡처 값이 `null`일 때 | 결과 |
|---|---|---|
| `it.description == name` | 실행됨 | NULL을 바인딩한 `e0.description = :p1`: 일치하는 행 없음. `WARN` 수준 `LQL-4045` |
| `minPrice == null \|\| it.price!! > minPrice` | 허용 | null 검사를 직접 작성했으므로 lambdaQL이 이를 사용합니다. |
| `it.price!! > minPrice!!`(`minPrice`가 `null`) | 실행됨 | NULL을 바인딩한 `e0.price > :p1`: 일치하는 행 없음. `WARN` 수준 `LQL-4045`(`!!`는 무시됨) |
| `it.id in ids`(`ids`에 `null` 요소가 있음) | 바인딩 시 오류 | null 요소가 있는 `IN`은 SQL의 함정입니다. |

<a id="placeholders-called-outside-a-lambda"></a>
### 람다 밖에서 호출한 플레이스홀더

어떤 메서드는 람다 안에서 읽히기 위해서만 존재합니다. 반환값은 플레이스홀더입니다.

| 메서드 | 쿼리 람다 밖에서 호출하면 |
|---|---|
| `Agg.count()`, `Agg.sum(...)` 및 다른 `Agg` 메서드 | `IllegalStateException`을 던짐(`LQL-4032`) |
| 직접 만든 `@SqlFunction` 메서드 | lambdaQL은 본문을 절대 실행하지 않습니다. 본문은 `DbOnlyException`(`LQL-4034`)을 던지기만 해야 합니다. 값을 계산하는 본문은 거부합니다(`LQL-3052`). |

#### Java

**(테스트됨)** [`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java), 영역 `rules`(앞부분):

```java
// the body never runs in Java
assertEquals("LQL-4034", assertThrows(DbOnlyException.class, () -> OrderFns.discount(1.0)).code());
```

`OrderFns`의 선언은 8. 프로시저와 DB 함수([Java](java/08-procedures-functions.md#86-declare-db-functions) | [Kotlin](kotlin/08-procedures-functions.md#86-declare-db-functions))에 있습니다.

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

**(테스트됨)** [`LqlMessagesTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/i18n/LqlMessagesTest.java), 영역 `locale`:

```java
// 1. setLocale wins, 2. then -Dlambdaql.locale, 3. then the JVM default
LqlMessages.setLocale(Locale.JAPANESE);
assertEquals(Locale.JAPANESE, LqlMessages.locale());
LqlMessages.setLocale(null);
System.setProperty(LqlMessages.LOCALE_PROPERTY, "ko_KR");
assertEquals(Locale.KOREAN, LqlMessages.bundleLocale(LqlMessages.locale()));
// only the language counts; other languages get English (the root bundle)
assertEquals(Locale.CHINESE, LqlMessages.bundleLocale(Locale.TRADITIONAL_CHINESE));
assertEquals(Locale.ROOT, LqlMessages.bundleLocale(Locale.FRENCH));
```

`LqlMessages.LOCALE_PROPERTY`는 이름 `"lambdaql.locale"`입니다. `LqlMessages.bundleLocale(locale)`은 로캘이 사용하는 메시지 파일을 알려 줍니다.

애플리케이션에서는 다음과 같이 씁니다(설계 예제).

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

- 키는 `LqlMessage` 값의 이름입니다. 예: `LOOP_NOT_SUPPORTED`.
- 인자는 `{0}`, `{1}`, ...로 씁니다.
- lambdaQL은 `MessageFormat`을 사용하지 않습니다. 그래서 작은따옴표(`can't`)를 그대로 쓸 수 있습니다. 숫자는 서식을 적용하지 않습니다(줄 번호 `1234567`은 `1234567` 그대로입니다).
- 모든 키는 같은 인자로 네 파일 모두에 있어야 합니다. 단위 테스트가 이를 검사합니다. 새 메시지는 네 언어 모두로 추가해야 합니다.

**(테스트됨)** [`LqlMessagesTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/i18n/LqlMessagesTest.java), 영역 `fill`:

```java
// no MessageFormat: quotes and big numbers stay as they are (rule I4)
assertEquals("can't read line 1234567", LqlMessages.fill("can't read line {0}", 1234567));
assertEquals("a null b", LqlMessages.fill("a {0} b", (Object) null));
assertEquals("{x} {9}", LqlMessages.fill("{x} {9}", "unused"));
```

<a id="logging"></a>
## 로깅

lambdaQL은 **SLF4J API**로 로그를 남깁니다.

- lambdaQL은 `slf4j-api`에만 의존합니다. 로깅 백엔드(Logback, Log4j 2 등)는 직접 고릅니다.
- Spring Boot에는 이미 SLF4J와 Logback이 들어 있으므로 아무것도 추가할 필요가 없습니다.
- 경고는 `WARN` 레벨을 사용하며 `LQL-5xxx` 범위의 코드로 시작합니다.
- `WARN` 레벨이 꺼져 있으면 lambdaQL은 메시지 텍스트를 만들지 않습니다.

현재 lambdaQL이 기록하는 경고:

| 코드 | 발생 조건 |
|---|---|
| `LQL-4045` | `=`·`<>`(`==`, `!=`, `equals`, `Objects.equals`)의 캡처 값이 `null`이라 맞는 행이 없을 때(SQL 의미, 결정 D48). 실행할 때만 알 수 있음. `WARN` 레벨로 기록. NULL을 찾으려면 `x == null`(3.6 참고: [Java](java/03-where.md#36-null-rules) \| [Kotlin](kotlin/03-where.md#36-null-rules)) |
| `LQL-5001` | 조건에서 DB 함수가 열을 감쌀 때(`OrderFns.discount(o.getPrice()) > 10`, 열에 적용한 Boolean 함수, `IN`이나 `IS NULL` 안의 함수). 일반 인덱스를 쓸 수 없음. `where` 람다마다 한 번 `WARN` 레벨로 기록. Oracle에서 같은 식의 함수 기반 인덱스가 있으면 기록하지 않음([8.9](java/08-procedures-functions.md#89-warning-functions-on-columns-and-indexes-sargable) 참고) |
| `LQL-5002` | 서브쿼리에 `orderBy`는 있지만 `limit`이 없어 lambdaQL이 정렬을 제거할 때(정렬은 결과에 영향이 없음, 규칙 Q4) |
| `LQL-5003` | 외부 정렬(Spring `Sort`, `SortSpec.ofPath`)이 정렬 규칙에 없는 이름을 사용하고 모드가 `SortMode.INFO`(기본값, `INFO` 레벨로 기록)나 `SortMode.WARN`(`WARN` 레벨로 기록)일 때. 정렬은 실행됨. 엔티티와 이름마다 한 번 기록([4.10 정렬 정책](java/04-order-paging.md#sort-policy) 참고) |
| `LQL-5004` | 클래스 파일에 프로시저 스텁의 인자 이름이 없고(`-parameters`, Kotlin `-java-parameters` 없이 컴파일, `@DbParam`도 없음) 이웃한 두 인자의 타입이 같을 때. 프로시저마다 한 번 `INFO` 레벨로 기록([8.4](java/08-procedures-functions.md#84-warning-argument-order) 참고) |
| `LQL-5005` | 프로시저 메타데이터를 읽을 수 없을 때(예: 딕셔너리 뷰나 `information_schema`에 권한이 없어 조회 실패). 스텁을 대조하지 않고 인자 배치를 스텁에서 가져옴. 프로시저마다 한 번 `INFO` 레벨로 기록([8.4](java/08-procedures-functions.md#when-the-metadata-cannot-be-read) 참고) |
| `LQL-5006` | Oracle 오버로드 조회(`all_arguments`)만 실패할 때. 오버로드 확인 없이 호출함. `INFO` 레벨로 기록 |
| `LQL-5007` | MariaDB 전용: 값 쪽의 `NOT DETERMINISTIC` DB 함수를 열과 비교할 때(`o.getPrice() > OrderFns.nondet(base)`). 인덱스를 범위 검색에 쓰지 못함. `WARN` 레벨로 기록 |
| `LQL-5008` | SARGable 검사용 인덱스·함수 메타데이터를 읽을 수 없을 때(예: 권한 없음). `INFO` 레벨로 기록. `LQL-5001`은 그대로 기록하고 `LQL-5007`은 검사하지 않음 |
| `LQL-5009` | 조건이 엔티티 값을 SQL 의미로 비교해(결정 D48) Java·Kotlin에서는 맞는 NULL 행이 빠질 수 있을 때: 엔티티 값끼리의 `==`/`equals`, 값과의 `!=`, NULL 가능 `Boolean`의 `!`, 연관 경로의 null guard(경로는 INNER JOIN 유지), 서브쿼리 `NOT IN`. 조건 문장마다 한 번 `DEBUG` 레벨로 기록(3.6.3 참고: [Java](java/03-where.md#363-write-your-null-checks-yourself) \| [Kotlin](kotlin/03-where.md#363-write-your-null-checks-yourself)) |

```
WARN  [LQL-5002] limit 없는 서브쿼리 정렬은 결과에 영향이 없어 제거했습니다(Q4): ...
```

계획된 경고(아직 구현되지 않음):

- 대소문자를 구분하지 않는 비교에 맞는 인덱스나 콜레이션이 그 컬럼에 없을 때

이런 경고를 오류로 바꾸는 "엄격 모드"가 계획되어 있습니다.
외부 정렬에는 이미 같은 기능이 있습니다. `SortMode.STRICT`에서는 `LQL-5003`의 경우에 `SortNotAllowedException`(`LQL-4040`)이 발생합니다.

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
