<a id="9-java-notes"></a>
# 9. Java 노트

[Java 트랙](README.md) | [매뉴얼 홈](../README.md) | 이전: [8. 프로시저와 DB 함수](08-procedures-functions.md) | 다음: [10. 규칙과 제한](10-rules-and-limits.md) | [이 페이지의 Kotlin 버전](../kotlin/09-kotlin-notes.md)

공통 페이지: [1. 개요](../01-overview.md) · [11. 오류와 언어](../11-errors-and-languages.md)

---

이 페이지는 lambdaQL 람다 표현식 안의 **Java** 코드에서 특별한 점을 모아 둔 곳입니다.
핵심은 항상 같습니다: **lambdaQL 람다 표현식은 코드가 아니라 쿼리 표현식입니다.**
lambdaQL은 람다 표현식을 호출하지 않고 바이트 opcode를 분석합니다. 그래서 일부 Java 실행 규칙은 적용되지 않습니다.

<a id="contents"></a>
## 목차

- [9.1 람다 표현식은 직렬화 가능한 람다 표현식이어야 함](#91-lambdas-must-be-serializable-lambdas)
- [9.2 박싱과 언박싱](#92-boxing-and-unboxing)
- [9.3 박싱된 값의 동등성](#93-equality-of-boxed-values)
- [9.4 문자열](#94-strings)
- [9.5 null-safe 헬퍼](#95-null-safe-helpers)
- [9.6 작은 타입: char, short, byte, boolean](#96-small-types-char-short-byte-and-boolean)
- [9.7 float, double, BigDecimal](#97-float-double-and-bigdecimal)
- [9.8 인스턴스 메서드 안의 람다 표현식은 this를 캡처함](#98-lambdas-in-instance-methods-capture-this)
- [9.9 메서드 참조](#99-method-references)
- [9.10 기대하지 말아야 할 static 헬퍼](#910-static-helpers-you-should-not-expect)
- [9.11 그 밖의 Java 형태](#911-other-java-forms)
- [9.12 컴파일러와 빌드](#912-compiler-and-build)

---

<a id="91-lambdas-must-be-serializable-lambdas"></a>
## 9.1 람다 표현식은 직렬화 가능한 람다 표현식이어야 함

lambdaQL은 Java 직렬화(`SerializedLambda`)를 통해 람다 표현식의 본문을 찾습니다.
`setAccessible`을 사용하지 않습니다. 캡처 값을 실제로 직렬화하지도 않습니다.

| 전달하는 것 | 결과 |
|---|---|
| 호출에 직접 작성한 람다 표현식: `where(o -> o.isActive())` | OK. `where`의 파라미터 타입은 `Serializable` 함수형 인터페이스 |
| 조건의 메서드 참조: `where(Order::isActive)` | **거부됨**(`LQL-1009`). 람다 표현식을 사용하십시오([9.9](#99-method-references) 참고) |
| lambdaQL 파라미터 타입의 변수에 담아 둔 람다 표현식 | OK |
| `java.util.function.Predicate`(`Serializable`이 아님) | **거부됨**(`LambdaAnalysisException`) |
| 익명 클래스: `new Where<Order>() { ... }` | **거부됨** |

참고:

- 캡처 값(그리고 캡처한 `this`)은 `Serializable`일 필요가 **없습니다**. lambdaQL은 그 값을 읽을 뿐 직렬화하지 않습니다.
- 서브쿼리의 안쪽 람다 표현식(`sub.from(Item.class).where(i -> ...)`)는 그 자체로 `Serializable`일 필요가 없습니다. lambdaQL은 바깥 람다 표현식의 바이트 opcode에서 그것을 찾습니다.

<a id="92-boxing-and-unboxing"></a>
## 9.2 박싱과 언박싱

예제 엔티티 `Order`에는 박싱 타입 속성(`Long id`, `Integer cnt`, `Double total`)과 기본형 속성(`double price`, `short status`)이 함께 있습니다.
박싱된 값을 기본형과 비교하면 javac는 **언박싱** 호출(`intValue()`, `doubleValue()`)을 추가합니다.

<!-- region: ManualJavaNotesTest.java#unboxing -->
```java
// cnt is an Integer column: javac writes o.getCnt().intValue() == 1; lambdaQL keeps only the meaning
SelectQuery<Order> q = qb.from(Order.class).where(o -> o.getCnt() == 1);
assertEquals("select e0 from Order e0 where e0.cnt = 1", q.toHql().hql());
// a NULL cnt: no NullPointerException, the row is simply not returned (the SQL meaning)
```

lambdaQL은 박싱과 언박싱 호출(`valueOf`, `intValue`, `doubleValue`, ...)을 **무시합니다**. 의미만 유지합니다.

NULL에 대해 이것이 뜻하는 바:

| Java 형태 | Java에서(실행한다면) | lambdaQL에서 |
|---|---|---|
| `o.getCnt() == 1`, cnt가 NULL | NullPointerException | SQL 의미: UNKNOWN, 행이 반환되지 않음(규칙 R1) |
| `o.getTotal() > 100`, total이 NULL | NullPointerException | 행이 반환되지 않음(R1) |
| `!c.getVip()`(`Boolean`, 예제 엔티티 `Coded`), vip가 NULL | NullPointerException | 행이 반환되지 않음([3.6.3](03-where.md#363-write-your-null-checks-yourself) 참고) |
| `o.getPrice() > p`, 캡처한 `Double p`가 `null` | NullPointerException | `e0.price > :p1`에 NULL 바인딩, 일치하는 행 없음(`DEBUG` 수준 `LQL-4045`) |
| `i.getQty() == q`, `qty`는 `int`, 캡처한 `Integer q`가 `null` | NullPointerException | `i.qty = :p1`에 NULL 바인딩, 일치하는 행 없음(`DEBUG` 수준 `LQL-4045`) |
| `o.getStatus() > p.intValue()`, 캡처한 `Double p`가 `null` | NullPointerException | 쿼리가 실행될 때 **예외**(`LQL-4012`) |

즉, NULL인 엔티티 값은 SQL을 따릅니다. 캡처 값을 자기 기본형으로 언박싱하는 호출(`Double.doubleValue()`, `Integer.intValue()` 등, 뒤의 확대 변환 포함)은 javac의 자동 언박싱과 같은 바이트 opcode이므로 실행하지 않고 박싱 값을 바인딩합니다.
다른 기본형으로 바꾸는 호출(`Double`의 `intValue()`)은 손으로만 쓸 수 있는 계산이므로 Java에서 실행하고, `null`이면 Java처럼 오류입니다.

NULL 행을 원한다면 검사를 직접 작성하십시오.

<!-- region: ManualJavaWhereTest.java#null-check -->
```java
// only rows where cnt is not NULL and not 1
SelectQuery<Order> notOne = qb.from(Order.class).where(o -> o.getCnt() != 1);
assertEquals("select e0 from Order e0 where e0.cnt <> 1", notOne.toHql().hql());
// rows where cnt is NULL too
SelectQuery<Order> notOneOrNull = qb.from(Order.class).where(o -> o.getCnt() == null || o.getCnt() != 1);
assertEquals("select e0 from Order e0 where e0.cnt is null or e0.cnt <> 1", notOneOrNull.toHql().hql());
```

예제 코드: [`ManualJavaNotesTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaNotesTest.java)(`unboxing`), [`ManualJavaWhereTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaWhereTest.java)(`null-check`).

<a id="93-equality-of-boxed-values"></a>
## 9.3 박싱된 값의 동등성

Java에서 두 **객체** 사이의 `==`는 동일성(메모리 안의 같은 객체인지)을 검사합니다.
lambdaQL에서 두 객체 사이의 `==`는 **값 비교**입니다.
엔티티 값과 캡처 값은 모두 SQL 의미를 따릅니다([3.6](03-where.md#36-null-rules) 참고).
박싱 캐시(-128부터 127까지의 `Integer`와 `Long`)와 `new String(...)`은 상관없습니다.

어떤 규칙이 적용되는지는 양쪽의 **Java 타입**에 따라 달라집니다.

| 왼쪽 | 오른쪽 | javac가 만드는 것 | lambdaQL 규칙 | HQL |
|---|---|---|---|---|
| `Integer` 컬럼 | `int` 상수나 변수 | 언박싱, 숫자 비교 | R1, SQL 의미 | `e0.cnt = 1` |
| `Integer` 컬럼 | `Integer` 변수 | 객체 비교 | R2 | `e0.cnt = :p1` |
| `Long` `@Id` 컬럼 | `Long` 변수 | 객체 비교 | R2, 하지만 한쪽은 NULL이 될 수 없음 | `e0.id = :p1` |
| `Long` 컬럼 | `long` 변수 | 언박싱 | R1 | `e0.id = :p1` |
| `String` | `String` | 객체 비교 | R2 | `e0.description = :p1` |
| `String` 컬럼 | `String` 컬럼 | 객체 비교 | R2, SQL 의미 | `e0.description = e0.custName`(어느 한쪽이 NULL이면 불일치) |
| enum | enum | 객체 비교 | R2 | `x = :p1` |
| `Boolean` 컬럼 | `Boolean.TRUE` | 객체 비교 | R2 | `e0.active = :p1` |
| `Boolean` 컬럼 | `true` | 언박싱 | R1 | `e0.active = true` |
| 값 객체(`@Embedded`) | 같은 타입의 캡처 값 객체 | `equals` / 객체 비교 | 컬럼마다 펼침(B6) | `e0.address.city = :p1 and e0.address.zip = :p2`([3.10](03-where.md#310-association-paths) 참고) |
| 무엇이든 | `null` | null 검사 | R3 | `x is null` |
| 엔티티 | 엔티티(`o.getCustomer() == c`) | 객체 비교 | **거부됨**(`LQL-3011`) | –(대신 id를 비교) |

캡처 값과 비교할 때:

| 컬럼 | 캡처 값 | HQL | 결과 |
|---|---|---|---|
| 3 | 3 | `e0.cnt = :p1` | true |
| NULL | `null` | `e0.cnt = :p1` | **false**: `= NULL`은 참이 되지 않음(`WARN` 수준 `LQL-4045`) |
| NULL | 3 | `e0.cnt = :p1` | false |
| 3 | `null` | `e0.cnt = :p1` | false |

<!-- region: ManualJavaWhereTest.java#boxed-equals -->
```java
// cnt is an Integer column; Integer == Integer compares values, not objects
Integer wanted = 3;
SelectQuery<Order> three = qb.from(Order.class).where(o -> o.getCnt() == wanted);
assertEquals("select e0 from Order e0 where e0.cnt = :p1", three.toHql().hql());
// a captured null is bound as NULL: "= NULL" matches no row (WARN LQL-4045)
Integer none = null;
SelectQuery<Order> noRow = qb.from(Order.class).where(o -> o.getCnt() == none);
assertEquals("select e0 from Order e0 where e0.cnt = :p1", noRow.toHql().hql());
// to find NULL, write the null check
SelectQuery<Order> nullCnt = qb.from(Order.class).where(o -> o.getCnt() == null);
assertEquals("select e0 from Order e0 where e0.cnt is null", nullCnt.toHql().hql());
```

컬럼을 함수로 감싸지 않으므로 데이터베이스는 인덱스를 사용할 수 있습니다.

객체 사이의 `!=`는 `<>`가 됩니다. 값이 NULL인 행은 `<>`에 일치하지 않습니다(SQL 의미). Java라면 이 행을 반환합니다.
이런 행을 유지하려면 `o.getCnt() == null || o.getCnt() != wanted`라고 쓰십시오.

엔티티 값 두 개(`o.getDescription() == o.getCustName()`)는 `=`와 `<>`가 됩니다. 어느 한쪽이 NULL이면 일치하지 않으며, NULL과 NULL도 마찬가지입니다.
결과가 Java와 다를 수 있으면 lambdaQL은 `LQL-5009`를 `DEBUG` 수준으로 기록합니다([3.6.3](03-where.md#363-write-your-null-checks-yourself) 참고).

예제 코드: [`ManualJavaWhereTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaWhereTest.java)(`boxed-equals`).

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

- 일반 Java에서 문자열에 대한 `==`는 버그입니다. lambdaQL 람다 표현식 안에서는 올바른 값 비교입니다.
- `==`, `equals`, `Objects.equals`는 NULL에 대해 같게 동작합니다. 컬럼이 NULL이거나 `p`가 `null`이면 일치하는 행이 없습니다. NULL 행을 원하면 `o.getDescription() == null`을 직접 쓰십시오.
- 비교는 Java가 아니라 **데이터베이스 콜레이션**을 따릅니다. MariaDB `_ci` 콜레이션은 대소문자와 끝의 공백을 무시합니다. Oracle은 `''`를 NULL로 저장합니다.
- 람다 표현식에 직접 쓴 문자열 상수는 HQL 리터럴이 되고, 작은따옴표는 escape됩니다(`'O''Brien'`). 캡처한 문자열은 항상 바인드 파라미터입니다([3.3](03-where.md#33-basic-form) 참고).
- 컬럼에 대한 `startsWith`, `endsWith`, `contains`는 `like`가 됩니다([3.8](03-where.md#38-strings)). `trim`, `toUpperCase` 같은 다른 문자열 메서드는 지원하지 않습니다(`LQL-3008`).
- 람다 표현식 안의 문자열 `+`는 거부됩니다(`LQL-2007`). 캡처 값만 있어도 마찬가지입니다(javac는 `+`를 `invokedynamic`으로 컴파일함). 문자열은 람다 표현식 전에 만드십시오.

<!-- region: ManualJavaNotesTest.java#string-outside -->
```java
String code = "A" + suffix;                                  // outside: plain Java
SelectQuery<Order> q = qb.from(Order.class).where(o -> o.getDescription() == code);
assertEquals("select e0 from Order e0 where e0.description = :p1", q.toHql().hql());
```

예제 코드: [`ManualJavaNotesTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaNotesTest.java)(`string-outside`).

<a id="95-null-safe-helpers"></a>
## 9.5 null-safe 헬퍼

다음 Java 헬퍼는 `==`와 같은 규칙, 즉 SQL 의미를 따릅니다(규칙 R5). 캡처 값 `null`은 어떤 행에도 일치하지 않습니다.

| Java | HQL | 참고 |
|---|---|---|
| `Objects.equals(o.getCnt(), s)` | `e0.cnt = :p1` | `s`가 `null`이면 일치하는 행 없음(`LQL-4045`) |
| `!Objects.equals(o.getCnt(), 1)` | `e0.cnt <> 1` | NULL 행이 **반환되지 않음**(SQL 의미. Java라면 반환) |
| `Objects.equals(o.getDescription(), o.getCustomer().getName())` | `e0.description = j1.name` + `join e0.customer j1` | 두 컬럼: 어느 한쪽이 NULL이면 불일치 |
| `Boolean.TRUE.equals(c.getVip())` | `e0.vip = :p1` | |
| `!Boolean.TRUE.equals(c.getVip())` | `e0.vip <> :p1` | NULL 행이 **반환되지 않음**(SQL 의미) |
| `"x".equals(o.getDescription())` | `e0.description = 'x'` | 상수 수신 객체: NULL이 될 수 없으므로 단순한 `=` |
| `set` 값 안의 `Objects.requireNonNullElse(o.getTotal(), 0.0)` | `coalesce(e0.total, 0.0)` | [7. 수정, 삽입, 삭제](07-dml.md) 참고 |

`!=`와 비교:

<!-- region: ManualJavaNotesTest.java#not-equals -->
```java
SelectQuery<Order> notOne = qb.from(Order.class).where(o -> o.getCnt() != 1);
assertEquals("select e0 from Order e0 where e0.cnt <> 1", notOne.toHql().hql());              // no NULL rows
SelectQuery<Order> notEquals = qb.from(Order.class).where(o -> !Objects.equals(o.getCnt(), 1));
assertEquals("select e0 from Order e0 where e0.cnt <> 1", notEquals.toHql().hql());           // no NULL rows either
SelectQuery<Order> withNull = qb.from(Order.class)
        .where(o -> o.getCnt() == null || !Objects.equals(o.getCnt(), 1));
assertEquals("select e0 from Order e0 where e0.cnt is null or e0.cnt <> 1", withNull.toHql().hql());  // NULL rows too
```

예제 코드: [`ManualJavaNotesTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaNotesTest.java)(`not-equals`).

<a id="96-small-types-char-short-byte-and-boolean"></a>
## 9.6 작은 타입: char, short, byte, boolean

JVM에는 `char`, `short`, `byte`, `boolean`을 위한 별도의 비교 명령어가 없습니다. javac는 이들을 `int`로 컴파일합니다.
lambdaQL은 이를 알고 있습니다.

- `long`, `int`, `short`, `byte`, `char`, `float`, `double` 값의 비교를 지원합니다(규칙 R1).
- 상수는 컬럼의 타입으로 변환됩니다. 예를 들어 `int` 상수 `10`은 `short` 컬럼에 대해 `short` 값이 됩니다.
- `boolean` getter는 단독으로 조건이 됩니다: `o.isActive()`는 `e0.active = true`가 됩니다.
- boolean 컬럼과 조건의 비교 `o.isActive() == (o.getStatus() > s)`에서 조건 쪽은 `case when`이 됩니다. 인덱스가 필요하면 `&&`와 `||`로 나누어 쓰십시오([3.7](03-where.md#37-boolean-columns) 참고).
- 기본형 속성(`boolean active`, `int qty`)은 `@Id`나 `nullable = false` 컬럼처럼 "NULL이 될 수 없음"으로 취급합니다. 그래서 lambdaQL은 이 속성에 대한 null 검사를 생략할 수 있고, `o.getId() == null`은 `1=0`이 됩니다([3.6.2](03-where.md#362--on-objects-compares-values)).

<a id="97-float-double-and-bigdecimal"></a>
## 9.7 float, double, BigDecimal

| 주제 | 규칙 |
|---|---|
| `float` / `double` 상수(`100.0`, `1.5f`) | 람다 표현식 안의 다른 상수처럼 HQL에 리터럴로 씀(`100.0`, `1.5F`). 람다 표현식 안에서 만든 `BigDecimal`(`new BigDecimal("1.5")`)은 계산한 값이므로 바인드 파라미터 |
| 비트 단위까지 같은 결과 | **약속하지 않음**. 데이터베이스마다 `float`와 `double`을 저장하고 비교하는 방식이 다름. lambdaQL은 `CAST`를 추가하지 않음. 컬럼 매핑을 직접 선택하십시오(예: Oracle `binary_float` / `binary_double`) |
| 캡처한 `NaN`, `Infinity` | 쿼리가 실행될 때 **예외** |
| 데이터베이스에 저장된 `NaN` | 데이터베이스를 따름(H2는 가장 큰 값으로 정렬, MariaDB는 저장할 수 없음) |
| `BigDecimal` 동등성 | Java `equals`는 스케일을 비교함(`1.0`은 `1.00`이 아님). lambdaQL은 **값**을 비교함 |
| 정수 `/` | 데이터베이스마다 결과가 다름(MariaDB `5/2 = 2.5`). 최종 규칙은 정해지지 않음. 피하십시오 |

<a id="98-lambdas-in-instance-methods-capture-this"></a>
## 9.8 인스턴스 메서드 안의 람다 표현식은 this를 캡처함

인스턴스 메서드 안의 람다 표현식이 필드를 사용하면, javac는 람다 표현식 본문을 **인스턴스 메서드**로 만들고 `this`를 캡처합니다.

<!-- region: ManualJavaNotesTest.java#this-capture -->
```java
@Service
public static class OrderService {

    private final QueryBuilder qb;
    private int defaultStatus = 1;

    public OrderService(QueryBuilder qb) {
        this.qb = qb;
    }

    public List<Order> defaults() {
        return qb.from(Order.class)
                 .where(o -> o.getStatus() == defaultStatus)   // means this.defaultStatus
                 .toList();
    }
}
```

**데이터베이스별 SQL**

<!-- sql: this-capture -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=?
```

lambdaQL이 하는 일:

- 첫 번째 캡처 값이 `this`라는 것을 압니다.
- `this.defaultStatus`는 엔티티를 사용하지 않으므로 Java에서 계산되어 파라미터로 바인딩됩니다.
- 필드는 **쿼리가 실행될 때마다** 읽습니다. 분석 결과는 객체마다가 아니라 람다 표현식마다 캐시됩니다.
- 클래스가 `Serializable`일 필요는 없습니다.
- 엔티티를 사용하지 않는 `this`의 메서드 호출(`this.minPrice()`)도 쿼리가 실행될 때마다 Java에서 계산됩니다. 부작용이 없도록 유지하십시오.

지역 변수는 다릅니다. Java는 람다 표현식이 만들어질 때 그 값을 복사합니다. Java는 지역 변수가 사실상 final이어야 한다고 요구합니다.

<!-- region: ManualJavaNotesTest.java#local-copy -->
```java
int st = defaultStatus;                                   // copied now
SelectQuery<Order> q = qb.from(Order.class).where(o -> o.getStatus() == st);
// the same HQL as the lambda that reads the field
assertEquals("select e0 from Order e0 where e0.status = :p1", q.toHql().hql());
assertEquals(q.toHql().hql(), qb.from(Order.class).where(o -> o.getStatus() == defaultStatus).toHql().hql());
```

두 형태 모두 같은 HQL을 만듭니다.

예제 코드: [`ManualJavaNotesTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaNotesTest.java)(`this-capture`, `local-copy`).

<a id="99-method-references"></a>
## 9.9 메서드 참조

| 메서드 참조 | 같은 의미 | 상태 |
|---|---|---|
| `where(Order::isActive)` | `where(o -> o.isActive())` | **거부됨**(`LQL-1009`). 람다 표현식을 사용하십시오 |
| `orderBy(Order::getPrice)` | `orderBy(o -> o.getPrice())` | 구현됨 |
| `orderBy(Order::getPrice, Direction.DESC)` | `orderBy(o -> o.getPrice(), Direction.DESC)` | 구현됨 |
| `SortSpec.of(Order.class, Order::getPrice)` | `SortSpec.of(Order.class, o -> o.getPrice())` | 구현됨. 키 하나를 쓰는 정렬 형태에서는 어디서나 쓸 수 있음(규칙 S13) |

lambdaQL은 메서드 참조를 로드할 때 그것을 감지합니다. 참조된 메서드의 본문은 읽지 않습니다. 정렬 키에서는 `o.getPrice()` 호출을 직접 만들고, 조건에서는 거부합니다.

<!-- region: ManualJavaNotesTest.java#method-ref -->
```java
// a method reference as a sort key: the same as o -> o.getPrice()
assertEquals("select e0 from Order e0 order by e0.price asc",
        qb.from(Order.class).orderBy(Order::getPrice).toHql().hql());
// a method reference as a condition is rejected: write a lambda
assertEquals("LQL-1009", assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(Order::isActive)).code());
```

어떤 메서드 참조는 컴파일되지만 의미가 잘못됩니다. lambdaQL은 이를 분석 시점에 거부합니다(규칙 S13).

- 인자가 하나인 인스턴스 메서드를 인자가 둘인 람다 표현식으로 사용한 경우(두 번째 인자가 메서드 인자가 됨),
- 인자가 둘인 static 메서드를 인자가 둘인 람다 표현식으로 사용한 경우.

`orderBy`에서는 엔티티의 getter만 허용합니다. static 메서드, 인자가 있는 메서드, 객체에 묶인 참조는 거부됩니다.

단순한 getter가 아니라면 람다 표현식을 사용하십시오.

예제 코드: [`ManualJavaNotesTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaNotesTest.java)(`method-ref`).

<a id="910-static-helpers-you-should-not-expect"></a>
## 9.10 기대하지 말아야 할 static 헬퍼

lambdaQL의 static 헬퍼 클래스 `By`(정렬 함수), `Agg`(집계), `Dml`(값 대입)는 람다 표현식 안에서만 씁니다.
서브쿼리 팩토리는 **람다 표현식 인자**(`sub`)로 들어옵니다. 그 밖의 것은 일반 Java로 작성합니다.

| 기대할 수 있는 것 | lambdaQL 방식 |
|---|---|
| `orderBy(asc(...), desc(...))` | `orderBy(o -> asc(...).desc(...))`: 키 여러 개를 람다 표현식 하나에서 이어 붙임 ([4](04-order-paging.md)) |
| `exists(...)`, `in(...)`, `notIn(...)` | `(o, sub) -> sub.from(X.class).where(...).exists()` / `.select(...).contains(x)` ([5](05-subquery.md)) |
| `set(path, value)` | `set(o -> o.setX(v))` 또는 `set(o -> value(o.getX(), v))`(`Dml.value`) ([7](07-dml.md)) |
| Q 클래스, `Order_.price` | getter: `o.getPrice()` |
| `count()`, `sum(...)`, ... | `Agg.count()`, `Agg.sum(...)` ([6](06-select-group.md)) |
| `coalesce(a, b)` | `set` 값 안의 `Objects.requireNonNullElse(a, b)` |
| `like(...)`, `lower(...)`, `upper(...)` | 지원하지 않음(컬럼에 대한 문자열 함수) |
| `currentTimestamp()` | `LocalDateTime.now()`. 애플리케이션에서 계산되어 바인딩됨 |
| 엔티티 값에 대한 직접 만든 static 유틸리티 메서드 | **거부됨**. 단, `@SqlFunction`을 붙인 DB 함수는 예외 ([8](08-procedures-functions.md)) |

**캡처 값만** 사용하는 static 메서드는 괜찮습니다. 쿼리가 실행될 때마다 Java에서 실행됩니다.

<!-- region: ManualJavaNotesTest.java#static-helper -->
```java
// a static method on captured values only: computed in Java when the query runs
SelectQuery<Order> q = qb.from(Order.class)
        .where(o -> o.getUpdateAt().compareTo(LocalDateTime.of(2026, 1, 1, 0, 0)) > 0);
assertEquals("select e0 from Order e0 where e0.updateAt > :p1", q.toHql().hql());
// your own static method on an entity value: rejected
assertEquals("LQL-3008", assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(o -> normalize(o.getDescription()) == text)).code());
```

예제 코드: [`ManualJavaNotesTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaNotesTest.java)(`static-helper`).

<a id="911-other-java-forms"></a>
## 9.11 그 밖의 Java 형태

| Java 형태 | lambdaQL 람다 표현식 안에서 |
|---|---|
| 식 하나 `o -> ...` | 일반적인 형태 |
| 지역 변수가 있는 블록 람다 표현식 `o -> { ...; return ...; }` | `where`, `orderBy`, `select`에서 거부됨 |
| setter 호출만 있는 블록 람다 표현식 | `set`, `values`, `insert ... select`에서 허용 ([7](07-dml.md)) |
| `if`, `for`, `while`, `switch`(문 또는 식), `try` | 거부됨 |
| 삼항 연산자 `c ? x : y` | `c ? x : false`, `c ? true : x`, `c ? false : true`만 ([3.5](03-where.md#35-and-or-not)) |
| boolean에 대한 `&`, `\|`, `^` | 거부됨. `&&`, `\|\|`를 사용하십시오 |
| 스트림, 중첩 람다 표현식(`sub` 서브쿼리 제외) | 거부됨 |
| 람다 표현식 안의 `List.of(...)` | 거부됨. `groupBy` 키로 쓰는 경우는 예외 |
| `compareTo(...) < 0` | `<`, `>`가 됨. `LocalDateTime`의 `isBefore`, `isAfter`는 거부됨(`LQL-3008`) |
| `captured.length`(캡처 값의 배열) | Java에서 계산됨 |
| 레코드 | 결과 타입(`select`)으로, 그리고 파생 테이블과 CTE의 행 타입으로(`r.total()`로 읽음) |
| `var` | 빌더 변수에 사용할 수 있음. 타입은 `SelectQuery<T>`, `OrderedSelectQuery<T>`, `ValueQuery<R>` 등 |
| 제네릭 `value(o.getStatus(), "abc")` | Java에서는 컴파일됨. 분석 시점에 컬럼 타입과 대조해 `LQL-3065` 오류 |
| 자기 `value(...)` 메서드가 있는 클래스(예: `value` 컴포넌트가 있는 레코드) 안의 `value(...)` | 정적 import가 가려짐. `Dml.value(...)`로 작성 |
| 중첩 람다 표현식에서 같은 파라미터 이름 | Java가 허용하지 않음. `sub2`, `i`, `x`를 사용하십시오 |

<a id="912-compiler-and-build"></a>
## 9.12 컴파일러와 빌드

| 주제 | 알아 둘 점 |
|---|---|
| 컴파일러 | lambdaQL은 **javac** 출력을 기준으로 동작합니다. **ECJ**(Eclipse 컴파일러)로 컴파일한 람다 표현식은 보장하지 않습니다. Eclipse IDE와 Eclipse JDT 기반의 다른 도구는 ECJ를 사용합니다. 테스트는 Gradle이나 Maven(javac)으로 실행하십시오 |
| JDK 버전 | Java 21 이상 |
| 클래스 파일 읽기(ASM) | Java 21~23에서는 ASM 9.11.1로 클래스 파일을 읽습니다(Java 28 클래스 파일까지). 더 새로운 JDK로 컴파일한다면 ASM을 업데이트하십시오. lambdaQL은 ASM을 셰이딩하지 않으므로 직접 업데이트할 수 있습니다. Java 24 이상에서는 실행 중인 JDK의 `java.lang.classfile`로 읽으므로 ASM 버전과 관계가 없습니다. lambdaQL jar는 Multi-Release JAR이며 JVM이 실행 버전에 맞는 쪽을 고릅니다 |
| `-parameters` | 권장. lambdaQL이 프로시저 인자 이름을 확인할 수 있습니다([8.4](08-procedures-functions.md#84-warning-argument-order)) |
| 디버그 정보 | 줄 번호를 유지하십시오(javac 기본값). lambdaQL은 오류 메시지에 소스 줄을 넣습니다 |
| IntelliJ "Number objects compared using `==`" | lambdaQL 람다 표현식 안에서는 의도한 동작입니다. 메서드에 `@SuppressWarnings("NumberEquality")`를 선언하여 경고를 없앨 수 있습니다 |
| 문자열 `==`에 대한 IntelliJ 경고 | lambdaQL 람다 표현식 안에서는 이것도 의도한 동작입니다 |

---

[Java 트랙](README.md) | 이전: [8. 프로시저와 DB 함수](08-procedures-functions.md) | 다음: [10. 규칙과 제한](10-rules-and-limits.md) | [이 페이지의 Kotlin 버전](../kotlin/09-kotlin-notes.md)
