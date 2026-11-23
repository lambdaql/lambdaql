<a id="8-stored-procedures-and-db-functions-kotlin"></a>
# 8. 저장 프로시저와 DB 함수 (Kotlin)

[Kotlin 트랙](README.md) | [매뉴얼 홈](../README.md) | 이전: [7. 수정, 삽입, 삭제](07-dml.md) | 다음: [9. Kotlin 노트](09-kotlin-notes.md) | [이 페이지의 Java 버전](../java/08-procedures-functions.md)

공통 페이지: [1. 개요](../01-overview.md) · [11. 오류와 언어](../11-errors-and-languages.md)

---

이 장은 두 부분으로 되어 있습니다.

| 부분 | 대상 | 어노테이션 | 호출 방법 |
|---|---|---|---|
| [8.1 ~ 8.5](#81-declare-procedures) | **저장 프로시저** | 인터페이스의 추상 함수에 `@SqlProcedure` | `qb.call(OrderProcs::class.java) { it.calc(id, rate) }.execute()` |
| [8.6 ~ 8.10](#86-declare-db-functions) | **DB 함수**(스칼라) | 인터페이스 `companion object` 안의 `@JvmStatic` 함수에 `@SqlFunction` | 어떤 람다 안에서든 직접 호출: `where { OrderFns.discount(it.price)!! > 10 }` |

두 경우 모두 시그니처는 Kotlin 인터페이스에 **한 번만** 작성합니다.
lambdaQL은 이 함수들을 **절대 실행하지 않습니다**. 람다 바이트코드에서 호출을 읽고 데이터베이스를 호출합니다.

<a id="contents"></a>
## 목차

- [8.1 프로시저 선언](#81-declare-procedures)
- [8.2 프로시저 호출](#82-call-procedures)
- [8.3 OUT, INOUT과 결과 집합](#83-out-inout-and-result-sets)
- [8.4 주의: 인자 순서](#84-warning-argument-order)
- [8.5 프로시저 규칙 PR1 ~ PR15(요약)](#85-procedure-rules-pr1-to-pr15-summary)
- [8.6 DB 함수 선언](#86-declare-db-functions)
- [8.7 쿼리에서 DB 함수 사용](#87-use-db-functions-in-queries)
- [8.8 DB 함수 단독 호출](#88-call-a-db-function-alone)
- [8.9 주의: 컬럼에 적용한 함수와 인덱스(SARGable)](#89-warning-functions-on-columns-and-indexes-sargable)
- [8.10 함수 규칙 FN1 ~ FN11(요약)](#810-function-rules-fn1-to-fn11-summary)
- [8.11 데이터베이스 지원](#811-database-support)

---

<a id="81-declare-procedures"></a>
## 8.1 프로시저 선언

인터페이스를 작성하십시오. 각 **추상** 함수에 `@SqlProcedure("NAME")`을 붙이십시오.
구현은 작성하지 않습니다. lambdaQL도 구현을 만들지 않습니다.

```kotlin
interface OrderProcs {
    @SqlProcedure("SQ_CALC")     fun calc(id: Long, rate: Double): Double?               // OUT 1개 → 반환 값
    @SqlProcedure("SQ_CALC2")    fun calc2(id: Long, rate: Double): CalcOut              // OUT 여러 개 → 데이터 클래스
    @SqlProcedure("SP_ADJUST")   fun adjust(id: Long, @InOut balance: Int?): AdjOut      // INOUT
    @SqlProcedure("SP_RECENT")   fun recent(custId: Long): List<OrderView>               // 결과 집합 / Oracle REF CURSOR
    @SqlProcedure("SQ_PKG.CALC") fun pkgCalc(id: Long, rate: Double): Double?            // 패키지 또는 스키마 이름
    @SqlProcedure("SP_PURGE")    fun purge(days: Int)                                    // 결과 없음
}

data class CalcOut(val total: Double?, val cnt: Int?)
data class AdjOut(val balance: Int?, val msg: String?)
```

참고:

- `@SqlProcedure`(또는 `@SqlFunction`)는 **필수**입니다. 이 함수가 무엇인지 lambdaQL에 알려 줍니다.
  (`@Procedure`와 `@Function`이라는 이름은 쓰지 않았습니다. `@Procedure`는 Spring Data JPA 어노테이션이기도 하고, `@Function`은 Kotlin의 `kotlin.Function`과 충돌합니다.)
- 값이 비어 있으면 이름은 함수 이름에서 만듭니다: `calcTotal` → `CALC_TOTAL`.
- `@SqlProcedure`에는 `schema`와 `pkg`(Oracle 패키지)도 있습니다: `@SqlProcedure(value = "CALC", schema = "LQL", pkg = "SQ_PKG")`.
- **nullable 반환 타입**(`Double?`)을 사용하십시오. 프로시저가 non-null 타입에 NULL을 반환하면 lambdaQL은 코드 `LQL-4039`가 붙은 `IllegalStateException`을 던집니다.
- 인터페이스와 결과 데이터 클래스는 DB 시그니처의 복사본입니다. 타입 안전성을 위한 대가입니다. lambdaQL은 그 일부를 데이터베이스와 대조하여 검사합니다(규칙 PR2).
- 프로시저(추상 함수)와 DB 함수(`companion object` 안)를 **같은 인터페이스**에 둘 수 있습니다.

---

<a id="82-call-procedures"></a>
## 8.2 프로시저 호출

`qb.call(Interface::class.java) { it.function(args) }`를 사용하십시오. 그다음 `execute()`를 호출합니다.

```kotlin
val d: Double?           = qb.call(OrderProcs::class.java) { it.calc(id, rate) }.execute()
val co: CalcOut          = qb.call(OrderProcs::class.java) { it.calc2(id, rate) }.execute()
val a: AdjOut            = qb.call(OrderProcs::class.java) { it.adjust(id, balance) }.execute()
val r: List<OrderView>   = qb.call(OrderProcs::class.java) { it.recent(custId) }.execute()

// 단순한 값을 사용하는 이름 있는 인자
val d2 = qb.call(OrderProcs::class.java) { it.calc(rate = rate, id = id) }.execute()

// 결과 없음: exec 사용
qb.exec(OrderProcs::class.java) { it.purge(30) }.execute()
```

결과가 없는 함수에는 왜 `call`이 아니라 `exec`을 쓸까요?

- Java에서 둘 다 `call`이면 모호한 오버로드가 됩니다(컴파일 오류). 두 언어는 같은 API를 사용합니다.
- `run`은 선택하지 않았습니다. Kotlin에서 인자를 빠뜨리면 `run { }`이 조용히 표준 라이브러리의 `run`이 됩니다.

<a id="what-the-lambda-may-contain-rule-pr12"></a>
### 람다에 들어갈 수 있는 것(규칙 PR12)

람다 본문은 **스텁 함수 호출 하나**입니다. 그 밖에는 아무것도 없습니다.

| 허용 | 허용 안 됨 |
|---|---|
| `{ it.calc(id, rate) }` | 로깅, 두 번의 호출, `if`, 중첩 호출 |
| 인자: 캡처 값, 상수, 그 값들의 계산(`rate * 2`) | 엔티티 경로(여기에는 엔티티가 없음) |
| **단순한 값**을 사용하는 이름 있는 인자(`calc(rate = rate, id = id)`) | 계산을 사용하는 이름 있는 인자(`calc(rate = r * 2, id = id)`), 아래 참고 |

계산이 있는 이름 있는 인자에 대해 Kotlin은 숨은 지역 변수를 만듭니다. lambdaQL은 v0.1에서 지역 변수를 거부합니다.
이 경우에는 위치 인자를 사용하십시오: `it.calc(id, r * 2)`.

그 밖의 사항:

- OUT 파라미터가 있는 프로시저에 `exec`을 쓰면 오류입니다(`LQL-4037`). 실수로 OUT 값을 잃지 않도록 막습니다.
- `@SqlProcedure`가 없는 함수를 호출하면 오류입니다(`LQL-3060`).
- 스텁 함수의 **기본 인자**(`fun calc(id: Long, rate: Double = 1.0)`)를 쓰는 호출은 오류입니다(`LQL-3062`, 규칙 PR11). Kotlin은 DB 기본값이 아니라 Kotlin 기본값을 채워 넣기 때문입니다.
- 파라미터의 데이터베이스 기본값은 사용할 수 없습니다. 모든 IN 파라미터를 전달하십시오.
- IN 값으로 `null`을 쓰는 것은 괜찮습니다.
- non-null `Long`/`Double` 파라미터와 nullable `Long?`/`Double?` 파라미터 모두 동작합니다(규칙 PR13).

---

<a id="83-out-inout-and-result-sets"></a>
## 8.3 OUT, INOUT과 결과 집합

반환 타입이 프로시저에 대응하는 방식(규칙 PR3, PR4, PR5):

| 프로시저에 있는 것 | 스텁 함수의 반환 타입 |
|---|---|
| 없음 | 반환 타입 없음(`Unit`) → `qb.exec(...)`로 호출 |
| OUT 파라미터 1개 | 그 값(`Double?`, `Int?`, ...) |
| OUT / INOUT 파라미터 여러 개 | Kotlin 데이터 클래스(또는 Java record) |
| 결과 집합 1개(MariaDB `SELECT`, Oracle `SYS_REFCURSOR` OUT, H2 `ResultSet` 함수) | `List<T>` |
| 결과 집합 여러 개 | `List` 속성을 가진 데이터 클래스 |
| OUT 값과 결과 집합(MariaDB) | 스칼라 속성과 `List` 속성을 가진 데이터 클래스 |

데이터 클래스 속성을 채우는 방식:

- **스칼라 속성** ← OUT과 INOUT 파라미터(커서 제외), DB 순서대로.
- **`List` 속성** ← 결과 집합, 순서대로(Oracle: 커서 OUT 파라미터의 순서, MariaDB: 반환된 결과 집합의 순서).
- 개수가 맞지 않으면 lambdaQL은 오류를 던집니다(`LQL-4038`).
- IN, OUT, IN, INOUT처럼 섞인 위치도 괜찮습니다. lambdaQL은 DB 메타데이터에서 위치를 읽습니다.

### INOUT

파라미터에 `@InOut`을 표시하십시오. 전달한 값은 **들어가는** 값입니다. **나오는** 값은 반환된 데이터 클래스의 속성입니다.

```kotlin
@SqlProcedure("SP_ADJUST") fun adjust(id: Long, @InOut balance: Int?): AdjOut

val a = qb.call(OrderProcs::class.java) { it.adjust(id, 100) }.execute()
// a.balance는 새 잔액
```

`@InOut`은 데이터베이스에서 INOUT인 인자에만 정확히 붙여야 합니다. 그렇지 않으면 첫 호출이 실패합니다(`LQL-4036`).

<a id="result-set-element-type-t"></a>
### 결과 집합의 요소 타입 `T`

| `T`가 | lambdaQL이 읽는 방법 |
|---|---|
| 엔티티 | Hibernate 결과 클래스(`StoredProcedureQuery`) |
| Kotlin 데이터 클래스나 Java record | JDBC `CallableStatement`, 컬럼은 **레이블로** 매칭. `_`를 빼고 대소문자를 무시하므로 `customer_id`가 `customerId`에 대응 |
| 값 하나(`Long?`, `String?`, ...) | 각 행의 첫 컬럼 |

일반 클래스는 여기서 행 타입으로 쓸 수 없습니다. 데이터 클래스를 사용하십시오.
MariaDB와 Oracle은 `SELECT *`의 컬럼을 서로 다른 순서로 반환하므로, lambdaQL은 절대 위치로 속성을 매핑하지 않습니다.
흔한 타입은 변환합니다(예: 숫자를 `Long`/`Int`로, 타임스탬프를 `LocalDateTime`으로).

제한:

- 프로시저 결과는 페이징할 수 없습니다.
- Oracle 암시적 결과(`DBMS_SQL.RETURN_RESULT`)는 지원하지 않습니다. `SYS_REFCURSOR` OUT 파라미터를 사용하십시오.

<a id="transactions-rule-pr9"></a>
### 트랜잭션(규칙 PR9)

- 프로시저 안의 DML은 호출한 쪽 트랜잭션의 일부입니다. 함께 롤백됩니다.
- 호출 전에 lambdaQL은 영속성 컨텍스트를 플러시합니다. 단, 트랜잭션이 활성 상태이고 변경이 있을 때만 플러시합니다.
- `QueryBuilder(EntityManagerFactory)`는 호출을 자체 트랜잭션에서 실행합니다. `EntityManager`를 받은 `QueryBuilder`(Spring의 공유 `EntityManager` 포함)는 호출자의 트랜잭션을 사용합니다. 트랜잭션이 없으면 호출은 자동 커밋됩니다.
- 호출 후에는 로드된 엔티티가 오래된 상태일 수 있습니다. 해당 엔티티에 `refresh`나 `evict`를 사용하십시오.
- 프로시저 안의 `COMMIT`이나 DDL은 트랜잭션을 깨뜨립니다. lambdaQL은 이를 막을 수 없습니다.

<a id="affected-rows-rule-pr10"></a>
### 영향받은 행(규칙 PR10)

프로시저에는 공통된 "영향받은 행 수" 결과가 없습니다(Oracle은 -1을 반환).
그 수가 필요하면 OUT 파라미터로 반환하십시오.

---

<a id="84-warning-argument-order"></a>
## 8.4 주의: 인자 순서

> [!WARNING]
> **스텁 함수 파라미터의 순서는 데이터베이스의 순서와 같아야 합니다.**

lambdaQL은 인자를 **위치로** 바인딩합니다(규칙 PR2).
Hibernate 6.6.5는 이름을 등록해도 위치로 바인딩합니다(실측, 7.4는 미측정).
MariaDB와 H2 드라이버는 이름 있는 파라미터를 지원하지 않습니다.
그래서 순서가 틀리면 **아무 오류 없이 잘못된 결과**를 얻을 수 있습니다.

Kotlin의 이름 있는 인자(`calc(rate = rate, id = id)`)는 이를 바꾸지 **않습니다**. 컴파일러가 인자를 스텁 함수의 순서로 되돌려 놓습니다. 스텁 함수의 순서만 중요합니다.

첫 호출 때 lambdaQL이 JDBC 메타데이터(`getProcedureColumns`)로 검사하는 항목입니다. 결과는 프로시저 이름별로 캐시합니다.

| 검사 | 발견 여부 |
|---|---|
| 프로시저의 존재 | 예, 오류 `LQL-4035` |
| IN·INOUT 파라미터 개수 = 스텁 인자 개수 | 예, 오류 `LQL-4036` |
| INOUT 파라미터 = `@InOut`을 붙인 인자 | 예, 오류 `LQL-4036` |
| OUT 값과 결과 집합의 개수 = 반환 타입의 구성 요소 수 | 예, 오류 `LQL-4038` |
| 타입의 종류(숫자, 문자, 날짜·시간, 불리언, 이진) | 예, 오류 `LQL-4036`. 같은 종류 안의 차이는 대조하지 않음(아래 "인자 타입 대조" 참고) |
| 같은 파라미터 이름이 다른 순서로 놓임 | 예, 오류 `LQL-4043`. 스텁 인자 이름을 알 수 있을 때만(아래 참고) |
| Oracle 패키지 프로시저 이름에 오버로드가 여러 개 | 예, 오류 `LQL-4044` |

위험한 경우의 예:

```kotlin
// 데이터베이스: SP_TRANSFER(p_to NUMBER, p_from NUMBER)
@SqlProcedure("SP_TRANSFER") fun transfer(from: Long, to: Long)   // 뒤바뀜! 같은 타입
```

개수, 모드, 타입이 모두 일치합니다. 이름만이 실수를 드러낼 수 있습니다.
그래서 lambdaQL은 첫 호출 때 IN과 INOUT 인자의 이름을 비교합니다.

- 스텁 인자 이름은 클래스 파일에서 읽습니다. `-java-parameters`로 컴파일한 경우에만 이름이 남습니다([Kotlin 컴파일러 설정](02-getting-started.md#kotlin-compiler-settings) 참고). 스텁 인자에 `@org.lambdaql.DbParam("p_to")`를 붙이면 DB 이름을 직접 지정합니다.
- DB 이름에서 **모든** 이름에 공통이고 `_`로 끝나는 접두사(`p_`, `in_`, `v_` 등)를 제거합니다. 정해진 접두사를 가정하지 않습니다. 이어서 대소문자와 `_`를 무시합니다.
- 이름은 같고 순서만 다르면 호출 전에 `LambdaQLException` `LQL-4043`이 발생합니다.
- 이름이 다르면(스텁이 다른 명명 규칙을 따름) 오류가 아닙니다. 검사가 동작하도록 `@DbParam`을 붙이십시오.
- 클래스 파일에 이름이 없고 이웃한 두 인자의 타입이 같으면, 프로시저마다 한 번 `LQL-5004`를 `INFO` 레벨로 기록합니다.
- 타입은 종류만 대조합니다(아래 "인자 타입 대조" 참고). 같은 종류의 인자 두 개가 뒤바뀐 경우는 이름으로만 찾을 수 있습니다.

같은 스텁을 Java로 테스트했습니다([`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java), 영역 `names`, `name-check`, MariaDB와 Oracle. [이 페이지의 Java 버전](../java/08-procedures-functions.md#84-warning-argument-order) 참고).
Kotlin에서 이름을 남기려면 `-java-parameters`가 필요합니다. 다음은 설계 예제입니다.

```kotlin
// Database: SQ_PROC_DIFF(p_to, p_from)
interface DiffProcs {
    @SqlProcedure("SQ_PROC_DIFF")
    fun diff(to: Int?, from: Int?): Int?                             // same order as the database: runs

    @SqlProcedure("SQ_PROC_DIFF")
    fun swapped(from: Int?, to: Int?): Int?                          // same names, other order: LQL-4043

    @SqlProcedure("SQ_PROC_DIFF")
    fun named(@DbParam("p_to") target: Int?, @DbParam("p_from") source: Int?): Int?   // another naming rule
}

qb.call(DiffProcs::class.java) { it.diff(10, 3) }.execute()       // 7
qb.call(DiffProcs::class.java) { it.swapped(3, 10) }.execute()    // LambdaQLException LQL-4043, before the call
```

**권장:** `-java-parameters`로 컴파일하고 DB와 같은 이름을 쓰거나(공통 접두사는 생략 가능) `@DbParam`을 붙이십시오. 그러면 뒤바뀐 순서를 검출합니다.

**인자 타입 대조.** 첫 호출 때 각 인자의 타입과 DB 파라미터 타입의 종류를 대조합니다.
종류는 숫자, 문자, 날짜·시간, 불리언, 이진 다섯 가지이며, 다르면 호출 전에 `LQL-4036`으로 중단합니다.

- 불리언 인자는 숫자 파라미터에도 맞는 것으로 봅니다. MariaDB의 `BOOLEAN`은 `TINYINT(1)`이고, Oracle 23 이전에는 `BOOLEAN` 타입이 없습니다.
- 종류를 정할 수 없는 쪽(enum, 객체 타입, 커서)은 대조하지 않습니다.
- 같은 종류 안의 차이(`Int?` 인자와 `NUMBER(10,2)` 파라미터 등)는 드라이버 변환에 맡깁니다.

Java 테스트([`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java), 영역 `type-check`, MariaDB와 Oracle)가 숫자 파라미터에 문자열을 넘기면 `LQL-4036`으로 중단되는 것을 확인합니다.

**Oracle 패키지 오버로드.** Oracle 패키지에는 같은 이름의 프로시저가 여러 개 있을 수 있습니다.
lambdaQL은 스텁이 어느 것을 가리키는지 구분할 수 없으므로, 그런 이름을 호출하면 `LQL-4044`로 중단합니다. 오버로드 하나를 골라 호출하는 기능은 이후 계획입니다.

Java로 테스트했습니다([`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java), 영역 `overload`, Oracle 전용). 다음은 설계 예제입니다.

```kotlin
interface PkgProcs {
    @SqlProcedure(pkg = "SQ_PKG", value = "TWICE") fun twice(value: Double?): Double?
    @SqlProcedure(pkg = "SQ_PKG", value = "HALF")  fun half(value: Double?): Double?    // HALF has two overloads
}

qb.call(PkgProcs::class.java) { it.twice(2.5) }.execute()     // 5.0
qb.call(PkgProcs::class.java) { it.half(10.0) }.execute()     // LambdaQLException LQL-4044
```

<a id="when-the-metadata-cannot-be-read"></a>
**메타데이터를 읽을 수 없는 경우.** DB 사용자에게 딕셔너리 뷰나 `information_schema`를 읽을 권한이 없을 수 있습니다.
메타데이터 조회 자체가 실패하면(`SQLException`) lambdaQL은 호출을 중단하지 않습니다.

- 프로시저마다 한 번 `LQL-5005`를 `INFO` 레벨로 기록하고, 스텁을 대조하지 않습니다.
- 인자 배치는 스텁에서 가져옵니다. IN과 INOUT 인자는 스텁 순서, 그 뒤에 OUT 파라미터(결과 구성 요소 순서), Oracle에서는 그 뒤에 `List` 구성 요소마다 `REF CURSOR` 하나입니다. 따라서 메타데이터가 없으면 DB 선언에서 OUT 파라미터가 IN 파라미터 뒤에 있어야 합니다.
- 메타데이터가 필요한 검사는 건너뜁니다: 개수와 `@InOut` 검사(`LQL-4036`), 이름 순서 검사(`LQL-4043`).
- 조회는 성공했지만 프로시저가 없으면 여전히 `LQL-4035`입니다.
- Oracle 오버로드 조회(`all_arguments`)만 실패하면 `LQL-5006`을 `INFO` 레벨로 기록하고, 오버로드 확인 없이 호출합니다.

이 대체 동작은 MariaDB와 Oracle에서 Java로 테스트했습니다(`ProcedureTest`의 테스트 메서드 `withoutMetadata`, 실패를 모의함).

Oracle의 실제 이름 바인딩(`pass_procedure_paramater_names=true`와 스텁 파라미터 이름을 함께 사용)도 계획되어 있습니다.

---

<a id="85-procedure-rules-pr1-to-pr15-summary"></a>
## 8.5 프로시저 규칙 PR1 ~ PR15(요약)

| # | 규칙 | 상태 |
|---|---|---|
| PR1 | 이름: 어노테이션 값을 그대로 사용. 값이 비어 있으면 함수 이름을 UPPER_SNAKE로. 패키지나 스키마 이름(`SQ_PKG.CALC`, `LQL.SQ_CALC`)도 동작함. 혼동을 피하려면 `@SqlProcedure(schema = ..., pkg = ..., value = ...)`로 쓸 수도 있음. | 구현됨. 스키마·패키지 이름은 테스트로 확인하지 않음 |
| PR2 | 인자는 **위치로** 바인딩됨. 첫 호출 때 IN/INOUT 파라미터 개수와 `@InOut` 위치를 DB 메타데이터와 대조함. 이름을 알 수 있으면 이름도 대조하며, 같은 이름이 다른 순서면 오류. 인자 타입의 종류(숫자, 문자, 날짜·시간, 불리언, 이진)도 대조하며, 다르면 오류(`LQL-4036`)([8.4](#84-warning-argument-order) 참고). 메타데이터를 읽을 수 없으면 검사를 건너뛰고 인자 배치를 스텁에서 가져옴([8.4](#when-the-metadata-cannot-be-read) 참고). | 개수, `@InOut`, 이름 대조는 구현됨. Oracle 패키지 오버로드 검출도 구현됨. 타입 대조도 구현됨. 메타데이터 없는 대체 동작(`LQL-5005`, `LQL-5006`)은 구현됨(MariaDB). |
| PR3 | OUT 1개 → 반환 값. OUT/INOUT 여러 개 → 데이터 클래스 또는 record. `@InOut`은 INOUT 파라미터를 표시함. | 구현됨 |
| PR4 | 결과 집합 1개 → `List<T>`. 엔티티는 Hibernate로, 데이터 클래스/record는 레이블 매칭을 사용하는 JDBC로. 페이징 없음. | 구현됨 |
| PR5 | 결과 집합 여러 개 → 데이터 클래스의 `List` 속성, 순서대로. Oracle 암시적 결과는 지원하지 않음. | 구현됨, 테스트로 확인하지 않음 |
| PR6 | 단독으로 호출한 DB 함수는 데이터베이스마다 다른 경로를 사용함([8.8](#88-call-a-db-function-alone) 참고). | 구현됨 |
| PR7 | 쿼리 안의 DB 함수: [8.6](#86-declare-db-functions)과 규칙 FN1 ~ FN11 참고. | 구현됨 |
| PR8 | 조건에서 컬럼에 DB 함수를 적용하면 인덱스를 사용할 수 없으므로 WARN을 기록함([8.9](#89-warning-functions-on-columns-and-indexes-sargable) 참고). 코드는 `LQL-5001`. | 구현됨. `SargableMode.STRICT`에서는 오류(`LQL-3066`, D50) |
| PR9 | 프로시저는 호출한 쪽 트랜잭션에서 실행됨. lambdaQL은 필요할 때 호출 전에 플러시함. 호출 후에는 로드된 엔티티가 오래된 상태일 수 있음. | 구현됨 |
| PR10 | 공통된 영향받은 행 수가 없음. OUT 파라미터를 사용. | – |
| PR11 | `null` IN 값은 괜찮음. DB 파라미터 기본값은 사용할 수 없음. 스텁 함수의 Kotlin 기본 인자는 오류(`LQL-3062`). | 구현됨 |
| PR12 | 람다는 스텁 호출 하나. 인자는 캡처 값, 상수, 또는 그 값들의 계산. 이름 있는 인자는 단순한 값만. OUT 파라미터가 있는데 `exec`을 쓰면 오류. | 구현됨. 이름 있는 인자는 테스트로 확인하지 않음 |
| PR13 | non-null `Long`/`Double`과 nullable `Long?`/`Double?` 모두 동작함. Boolean 파라미터는 MariaDB와 Oracle 23ai 이상에서 동작함. | 테스트로 확인하지 않음 |
| PR14 | 각 람다는 한 번만 분석됨. 프로시저 메타데이터는 이름마다 한 번만 읽음. | 구현됨 |
| PR15 | H2에는 OUT, INOUT, 여러 결과 집합, 패키지가 없음. 프로시저는 MariaDB나 Oracle에서 테스트하십시오(예: Testcontainers 사용). | lambdaQL 테스트도 이 방식을 따름 |

---

<a id="86-declare-db-functions"></a>
## 8.6 DB 함수 선언

DB 함수는 데이터베이스에 직접 만든 함수입니다. 예를 들면 `FN_DISCOUNT(price)`입니다.
Kotlin 인터페이스에는 정적 함수가 없으므로 인터페이스의 **`companion object`** 안에 **`@JvmStatic`**과 `@SqlFunction`을 붙여 선언하십시오.
본문은 예외를 던지기만 해야 합니다. lambdaQL 안에서는 절대 실행되지 않습니다.

```kotlin
interface OrderFns {
    companion object {
        @JvmStatic @SqlFunction("FN_DISCOUNT")
        fun discount(price: Double?): Double? = throw DbOnlyException()

        @JvmStatic @SqlFunction("FN_IS_VIP")            // Boolean: Oracle 23ai 이상
        fun isVip(customerId: Long?): Boolean? = throw DbOnlyException()
    }
}
```

lambdaQL 람다 **밖에서** `OrderFns.discount(...)`를 호출하면 `DbOnlyException`(`LQL-4034`)이 발생합니다. 의도한 동작입니다.

`@SqlFunction`의 값은 두 가지입니다.

| 값 | 의미 |
|---|---|
| `value` | 쿼리에 쓰는 함수 이름. 비어 있으면 함수 이름을 UPPER_SNAKE로(`isVip` → `IS_VIP`). 스키마 이름도 허용(`PUBLIC.SQ_FN`) |
| `sql` | `value`와 다를 때의 SQL 이름. 이런 함수는 등록이 필요함([등록](#registration-optional) 참고) |

Java 테스트는 함수를 Java 인터페이스의 static 메서드로 선언합니다([이 페이지의 Java 버전](../java/08-procedures-functions.md#86-declare-db-functions) 참고). Kotlin `companion object` 함수도 같은 방식으로 읽지만, 아직 테스트로 확인하지 않았습니다.

왜 `@JvmStatic`일까요? 그러면 Java 코드가 `OrderFns.discount(...)`를 정적 함수로 호출할 수 있고, Java와 Kotlin 호출자에게 함수가 똑같이 보입니다.
lambdaQL은 `companion object` 함수에 붙은 어노테이션을 읽습니다.

다른 선언 방법:

| 선언 | 상태 |
|---|---|
| 인터페이스 `companion object` + `@JvmStatic` | **권장**(구현됨, 아직 테스트로 확인하지 않음) |
| Kotlin `object` + `@JvmStatic` | 설계됨, 테스트로 확인하지 않음 |
| Java 인터페이스 정적 메서드(Kotlin에서 호출) | 지원 |
| Kotlin 최상위 함수 | 설계됨, **권장하지 않음**: import를 잊으면 같은 이름의 표준 라이브러리 함수(예: `maxOf`)가 조용히 사용될 수 있음 |
| 인터페이스 기본 함수 + 싱글턴 상수 | 설계됨, 테스트로 확인하지 않음 |
| 리포지토리가 함수 인터페이스를 구현(믹스인) | 거부 |
| 함수형 인터페이스 상수(`DISCOUNT.apply(x)`), 함수 타입 속성 | 거부 |
| 확장 함수 | v0.1에서 거부(KC11) |
| 기본 인자, `inline`, `vararg`가 있는 `@SqlFunction` | 거부(FN9: 기본 인자는 `LQL-3056`, `vararg`는 `LQL-3057`) |

함수는 **인터페이스 이름과 함께** 호출하십시오(`OrderFns.discount`). 함수를 import해도 동작하지만, 같은 이름의 멤버 함수가 그 함수를 가릴 수 있습니다.

---

<a id="87-use-db-functions-in-queries"></a>
## 8.7 쿼리에서 DB 함수 사용

함수를 직접 호출하십시오. 컨텍스트 인자는 필요 없습니다.
`where`, `select`, `orderBy`, `set`에서 동작합니다([`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java) 참고). `groupBy`, `having`, 서브쿼리에서도 동작하도록 설계했지만 테스트로 확인하지는 않았습니다.

```kotlin
qb.from(Order::class.java).where { OrderFns.discount(it.price)!! > 10 }.toList()
// → where function('FN_DISCOUNT' as Double, e0.price) > 10

qb.from(Order::class.java).where { OrderFns.isVip(it.customer?.id)!! }.toList()     // Boolean 함수 단독: ... = true

qb.from(Order::class.java).where { it.price!! > OrderFns.discount(base)!! }.toList()  // 컬럼 쪽은 깨끗하게 유지됨

qb.from(Order::class.java).orderBy { o, by -> by.desc(OrderFns.discount(o.price)) }.toList()

qb.from(Order::class.java)
    .select { o -> OrderView(o.id, o.customer?.name, OrderFns.discount(o.price)) }
    .toList()

qb.update(Order::class.java).set { it.price = OrderFns.discount(it.price) }.all().execute()
```

Java 테스트는 같은 형태를 이름 `SQ_FN_DISCOUNT`로 확인합니다. 예: `select e0 from Order e0 where function('SQ_FN_DISCOUNT' as Double, e0.price) > 100.0`. 테스트는 각 쿼리를 실행하고, 결과 행을 Java로 같은 계산을 한 결과와 비교합니다.

**SQL 블록 읽는 법.** `…`는 줄여 쓴 select 목록(엔티티의 모든 컬럼)이고, `?`는 바인드 파라미터입니다. [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java)는 같은 형태의 HQL(이름 `FN_DISCOUNT`)을 Hibernate에 직접 넘기고, H2 2.3, MariaDB 10.11, Oracle 23.26(Hibernate 7.4)에서 나온 SQL을 기록합니다. Hibernate의 SQL이 바뀌면 테스트가 실패합니다.

**데이터베이스별 SQL(이 형태의 HQL에 대한 Hibernate SQL)** — `PlannedSqlTest`가 기록했습니다. `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)의 예제 `08-function`:

```sql
-- H2, MariaDB, Oracle (same)
-- HQL: select o from Order o where function('FN_DISCOUNT' as Double, o.price) > 10
select …
from orders o1_0
where FN_DISCOUNT(o1_0.price)>10
```

테스트 설정에는 `lambdaql.functions.classes` 설정이 있습니다. 이 설정이 있으면 Hibernate는 등록하지 않은 함수에도 이름의 대소문자(`FN_DISCOUNT`)를 유지합니다(세 데이터베이스에서 관찰함, 원인은 아직 확인하지 않음).
등록이 전혀 없으면 Hibernate가 이름을 소문자로 쓸 수 있습니다. 따옴표 없는 이름은 어느 쪽이든 동작합니다.

`!!`는 컴파일러를 위한 것일 뿐입니다(함수가 `Double?`을 반환하므로). lambdaQL은 이를 건너뜁니다(규칙 FN6).

<a id="how-lambdaql-renders-it"></a>
### lambdaQL이 출력하는 형태

lambdaQL은 DB 함수를 **항상** 다음 형태로 출력합니다.

```sql
function('<NAME>' as <Type>, arg1, arg2, ...)
```

`<Type>`은 선언된 반환 타입에서 옵니다. 다음 타입만 허용됩니다.

| 허용되는 반환 타입 |
|---|
| `Double`, `Integer`, `Long`, `BigDecimal`, `String`, `LocalDate`, `LocalDateTime`, `Boolean` |

Kotlin `Double?`, `Int?`, `Long?`, `Boolean?`과 그 non-null 형태는 이 이름들에 대응합니다.
제네릭 반환(`fun <X> nvl(a: X, b: X): X`), 엔티티, 컬렉션, `Unit`은 분석 시점에 거부됩니다.

<a id="registration-optional"></a>
### 등록(선택 사항)

`function('FN_DISCOUNT' as Double, ...)` 형태에는 등록이 필요 없습니다(세 데이터베이스에서 확인함).
함수 인터페이스를 등록하면 Hibernate가 이름의 대소문자도 유지하고 인자 개수와 타입도 검사합니다.

lambdaQL에는 Hibernate `FunctionContributor`인 `org.lambdaql.hibernate.LambdaQlFunctionContributor`가 있습니다(ServiceLoader로 발견함). 인터페이스는 Hibernate 속성에 나열합니다.

```properties
# 일반 Hibernate
lambdaql.functions.classes=com.acme.OrderFns,com.acme.CustomerFns

# Spring Boot
spring.jpa.properties.lambdaql.functions.classes=com.acme.OrderFns
```

속성을 설정하지 않으면 아무것도 등록되지 않습니다. 패키지 스캔은 v0.1에 없습니다.

<a id="a-different-sql-name-needs-registration"></a>
### SQL 이름이 다른 함수의 등록

`value`는 쿼리의 이름이고 `sql`은 SQL의 이름입니다. 두 이름이 다르면 함수를 등록해야 합니다.
따옴표로 감싸 대소문자를 섞어 만든 함수 이름(`"SQ_Fn_Mixed"`)에 유용합니다. 등록하지 않으면 H2와 Oracle에서 이름이 소문자가 될 수 있습니다.

```kotlin
@JvmStatic @SqlFunction(value = "SQ_Fn_Mixed", sql = "\"SQ_Fn_Mixed\"")
fun mixed(v: Double?): Double? = throw DbOnlyException()
```

이런 함수가 등록되어 있지 않으면 lambdaQL은 람다를 읽을 때 실패합니다(`LQL-3058`). Java 테스트는 등록한 함수(`value = "SQ_TAX"`, `sql = "SQ_FN_DISCOUNT"`, 영역 `registered`)를 확인합니다.

---

<a id="88-call-a-db-function-alone"></a>
## 8.8 DB 함수 단독 호출

**인자가 없는 람다**와 함께 `qb.call { ... }`을 사용하십시오.

```kotlin
val v: Double? = qb.call { OrderFns.discount(price) }.execute()
```

Java 형태는 H2, MariaDB, Oracle에서 테스트했습니다(`ProcedureTest`, 영역 `function`). 람다는 `@SqlFunction` 호출 하나여야 합니다(`LQL-3063`).

**데이터베이스별 SQL(이 형태의 HQL에 대한 Hibernate SQL)** — `PlannedSqlTest`가 이름 `FN_DISCOUNT`로 기록했습니다. `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)의 예제 `08-function-alone`:

```sql
-- H2, MariaDB, Oracle (same)
-- HQL: select function('FN_DISCOUNT' as Double, :p1)
select FN_DISCOUNT(?)
```

이 쿼리는 FROM 없이 `select FN_DISCOUNT(?)`로 실행됩니다. Oracle은 23ai부터만 이를 받아들입니다. Oracle에서는 lambdaQL이 대신 JDBC 함수 호출을 사용합니다(아래 표 참고).

인자 개수가 다르므로 `qb.call(OrderProcs::class.java) { ... }`와 충돌하지 않습니다.

lambdaQL이 실행하는 방법(규칙 PR6):

| 데이터베이스 | 경로 |
|---|---|
| Oracle | JDBC 함수 호출 `{? = call FN(?)}`. DML을 수행하는 함수와 커서를 반환하는 함수에도 동작함. |
| MariaDB, H2 | HQL `select function('FN' as T, :p1)` |

lambdaQL은 먼저 대기 중인 변경을 플러시하므로(PR9와 같음) 함수는 최신 변경을 봅니다.

---

<a id="89-warning-functions-on-columns-and-indexes-sargable"></a>
## 8.9 주의: 컬럼에 적용한 함수와 인덱스(SARGable)

lambdaQL은 **인덱스를 사용할 수 있는** SQL을 만들려고 합니다([10. 규칙과 제한](10-rules-and-limits.md#102-sargable-rule-index-friendly-sql) 참고).
컬럼을 감싼 함수는 보통 일반 인덱스를 사용할 수 **없습니다**.

```kotlin
.where { OrderFns.discount(it.price)!! > 10 }
// → function('FN_DISCOUNT' as Double, e0.price) > 10   -- price의 인덱스가 사용되지 않음
```

**데이터베이스별 SQL(이 형태의 HQL에 대한 Hibernate SQL)** — `PlannedSqlTest`가 기록했습니다. `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)의 예제 `08-function`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where FN_DISCOUNT(o1_0.price)>10
```

따라서 lambdaQL은 이 형태에 **WARN**을 기록합니다.
검사는 `where` 람다마다 한 번, 분석 결과를 캐시할 때 수행합니다. `@SqlFunction`이 없는 조건은 DB를 조회하지 않습니다.

| 경우 | 코드 | H2 | MariaDB | Oracle |
|---|---|---|---|---|
| ① 함수가 열을 감쌈: `fn(o.price) > 10`, 열에 적용한 Boolean 함수, `IN`이나 `IS NULL` 안의 함수 | `LQL-5001`(WARN) | WARN | WARN(저장 함수는 인덱스에 쓸 수 없음) | WARN. **단**, `user_ind_expressions`에 같은 식 `FN(COLUMN)`의 함수 기반 인덱스가 그 테이블에 있으면 기록하지 않음. 열 타입이 함수 인자 타입과 달라 Oracle이 넣은 숫자 변환(`FN(TO_NUMBER(COLUMN))` 등)도 같은 식으로 봄 |
| ② 값 쪽의 함수를 열과 비교: `o.price > fn(rate)` | `LQL-5007`(WARN) | 검사하지 않음 | `information_schema.routines.is_deterministic = 'NO'`(기본값!)이면 WARN. 이때 MariaDB는 인덱스 전체를 스캔함 | 검사하지 않음(Oracle은 어느 쪽이든 범위 스캔) |

- Oracle 인덱스 대조에 쓰는 테이블·열 이름은 Hibernate 매핑에서 가져옵니다. 열 인자 하나인 함수만 대조합니다.
- 메타데이터 조회가 실패하면(예: 권한 없음) `LQL-5008`을 `INFO` 레벨로 기록합니다. ①은 그대로 경고하고, ②는 검사하지 않습니다.

같은 검사를 Java로 테스트했습니다([`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java), 영역 `sargable`: H2는 `LQL-5001`, MariaDB는 `LQL-5001`과 `LQL-5007`, 함수 기반 인덱스가 있는 Oracle은 없음). 다음은 설계 예제입니다(`nondet`는 `discount`와 같은 `@SqlFunction`).

```kotlin
// the function wraps the column: an index on price cannot be used (LQL-5001 at WARN),
// unless Oracle has a function-based index on SQ_FN_DISCOUNT(price)
qb.from(Order::class.java).where { OrderFns.discount(it.price)!! > 100 }.toList()
// the function is on the value side: fine, unless MariaDB says NOT DETERMINISTIC (LQL-5007 at WARN)
qb.from(Order::class.java).where { it.price!! > OrderFns.nondet(base)!! }.toList()
```

**SARGable 모드**(결정 D50). `SargableMode`에는 세 값이 있습니다.

- `OFF`: 검사하지 않고 메타데이터도 조회하지 않습니다.
- `WARN`(기본값): 경고를 기록합니다.
- `STRICT`: `WARN`이라면 경고를 기록할 경우(`LQL-5001`, `LQL-5007`, `LQL-5012`)에 분석이 `org.lambdaql.NonSargableException`(`LQL-3066`)으로 실패합니다.

persistence unit마다 `SargablePolicy.register(emf, SargableMode.STRICT)`로 지정하고 `SargablePolicy.unregister(emf)`로 해제합니다. Spring에서는 `lambdaql.sargable.mode=STRICT`를 씁니다([12](12-spring-data-jpa.md) 참고).
검사는 람다마다 한 번, 분석 결과를 공유 캐시에 넣을 때 실행됩니다. 그래서 첫 쿼리 전에 모드를 등록하십시오.

**컬럼에 대한 산술**(결정 D50).

- 컬럼 하나와 값만으로 된 `+ - * /`(`it.cnt * 2 > n`)는 값 쪽으로 옮길 수 있습니다. lambdaQL은 `LQL-5012`를 `WARN` 수준으로 기록하고, `STRICT`에서는 오류입니다.
- 컬럼끼리의 산술(`it.price!! * it.cnt > n`)과 `%`는 옮길 수 없으므로 `LQL-5013`을 `DEBUG` 수준으로만 기록합니다. `STRICT`에서도 허용합니다.
- 옮긴 형태는 의미가 같아야 합니다. `it.cnt > n / 2.0`처럼 소수로 나누십시오. 정수 나눗셈 `n / 2`는 `>=`, `=`, `<`와 음수 `n`에서 다른 행을 냅니다. 음수를 곱했다면 비교 방향을 바꾸십시오. `LQL-5012` 메시지가 이를 안내합니다.
- 인덱스가 있는지 조회해 경고를 줄이는 방식은 채택하지 않았습니다. 권한과 시노님 때문에 "인덱스 없음"과 "알 수 없음"이 섞이고, 컬럼 산술은 드문 형태이기 때문입니다.

이에 대한 Kotlin 테스트는 없습니다. Java 테스트([`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java), 영역 `sargable-strict`)가 `STRICT` 모드를 확인합니다.

**권장:**

- 가능하면 함수를 값 쪽에 두십시오: `it.price!! > OrderFns.discount(base)!!`.
- MariaDB에서는 함수가 실제로 결정적이면 `DETERMINISTIC`으로 선언하십시오.
- Oracle에서는 같은 식의 함수 기반 인덱스가 있으면 경고가 사라집니다(이런 인덱스에는 `DETERMINISTIC` 함수가 필요합니다).

---

<a id="810-function-rules-fn1-to-fn11-summary"></a>
## 8.10 함수 규칙 FN1 ~ FN11(요약)

| # | 규칙 | 상태 |
|---|---|---|
| FN1 | `@SqlFunction`은 함수 어노테이션. 값이 비어 있으면 함수 이름을 UPPER_SNAKE로. | 구현됨 |
| FN2 | lambdaQL은 호출된 함수를 찾아 그 어노테이션을 읽음. Kotlin에서는 `Companion` 수신 객체를 건너뜀. 본문은 예외를 던지기만 해야 함(`= throw ...`). 애플리케이션과 데이터베이스가 다른 값을 낼 수 있으므로 실제 본문이 있는 함수는 오류(`LQL-3052`). | 구현됨 |
| FN3 | 같은 이름의 모든 오버로드는 같은 DB 함수에 대응해야 함. 그렇지 않으면 오류(`LQL-3053`). (Kotlin `Double?` 인자는 `Double?` 오버로드를 고름. Kotlin에서 본 Java 엔티티 getter는 기본형 `double` 오버로드를 고를 수 있음.) | 구현됨 |
| FN4 | 어노테이션이 없는 호출이나 lambdaQL이 식별할 수 없는 호출은 오류. 절대 조용히 건너뛰지 않음. | 구현됨 |
| FN5 | 인자는 [3. 조건](03-where.md)의 값 규칙을 따름. 캡처 값만으로 만든 인자도 바인딩되어 **데이터베이스에서** 평가됨. | 구현됨 |
| FN6 | `!!`와 박싱 호출은 건너뜀. 반환 타입은 선언된 함수에서 옴. `Double`, `Integer`, `Long`, `BigDecimal`, `String`, `LocalDate`, `LocalDateTime`, `Boolean` 중 하나에 대응해야 함(`LQL-3055`). | 구현됨 |
| FN7 | 컬럼에 적용한 함수는 WARN을 냄([8.9](#89-warning-functions-on-columns-and-indexes-sargable) 참고). MariaDB에서는 값 쪽의 `NOT DETERMINISTIC` 함수도 WARN(`LQL-5007`). | 구현됨. `SargableMode.STRICT`에서는 오류(`LQL-3066`, D50) |
| FN8 | Hibernate 내장 함수(`COALESCE`, `ROUND`, `SYSDATE`, ...)와 같은 이름은 오류(`LQL-3054`). Hibernate가 조용히 내장 함수를 사용하기 때문. 접두사나 스키마 이름(`PUBLIC.SQ_FN`)을 사용. | 구현됨 |
| FN9 | Kotlin: 기본 인자(`LQL-3056`), `vararg`(`LQL-3057`), `inline`은 오류. | 기본 인자와 `vararg`는 구현됨. `inline`은 설계됨 |
| FN10 | v0.1은 스칼라 함수만 지원. 사용자 집계 함수와 윈도 함수는 v0.2에 계획됨. 인자가 없는 함수는 `fn()`으로 출력됨. | 스칼라 함수 구현됨 |
| FN11 | 알려진 DB 차이: MariaDB에서 VARCHAR를 반환하는 함수는 문자열 리터럴과 콜레이션 충돌이 날 수 있음. `BigDecimal` 결과의 스케일이 다름(MariaDB와 Oracle은 0, H2는 1). Boolean 결과는 Oracle 23ai 이상이 필요. | 프로토타입으로 실측 |

규칙 검사(`LQL-4034`, `LQL-3052`, `LQL-3054`, `LQL-3058`, `LQL-3055`)는 Java로 테스트했습니다([`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java), 영역 `rules`, [이 페이지의 Java 버전](../java/08-procedures-functions.md#810-function-rules-fn1-to-fn11-summary) 참고).

---

<a id="811-database-support"></a>
## 8.11 데이터베이스 지원

| 기능 | H2 2.3 | MariaDB 10.11 | Oracle 23ai |
|---|---|---|---|
| IN 파라미터 | 예 | 예 | 예 |
| OUT / INOUT | **아니요**(H2 제한) | 예 | 예 |
| 결과 집합 1개 | 예(`ResultSet` 함수) | 예(프로시저 안의 `SELECT`) | 예(`SYS_REFCURSOR` OUT) |
| 결과 집합 여러 개 | 아니요 | 예 | 예(커서 OUT 파라미터 여러 개) |
| OUT 값과 결과 집합을 함께 | 아니요 | 예 | 예 |
| 패키지 이름(`PKG.PROC`) | 아니요 | 아니요(`sql_mode=ORACLE` 필요) | 예 |
| 쿼리 안의 DB 함수 | 예 | 예 | 예 |
| DB 함수 단독 호출 | 예 | 예 | 예 |
| lambdaQL의 프로시저 호출 | – | 예 | 예 |
| Boolean 파라미터 / 결과 | – | 예 | 예(23ai 이상) |

v0.1에 계획되지 않은 것: 동적 프로시저 이름을 위한 문자열 빌더(v0.2 후보), Oracle 패키지 프로시저의 오버로드 하나를 골라 호출하는 기능(오버로드 검출은 구현됨, `LQL-4044`), 테이블 반환 함수, 프로시저 결과 페이징.

이 장의 오류는 `[LQL-xxxx]` 코드가 있는 `LambdaQLException`(또는 그 하위 클래스)입니다. 람다를 읽을 때는 `LQL-3052`~`LQL-3063`과, `SargableMode.STRICT`에서 인덱스를 쓸 수 없는 조건의 `LQL-3066`(`NonSargableException`, [8.9](#89-warning-functions-on-columns-and-indexes-sargable) 참고)입니다. 호출을 실행할 때는 `LQL-4034`~`LQL-4039`, `LQL-4043`, `LQL-4044`입니다. 호출 중에 `LQL-5004`, `LQL-5005`, `LQL-5006`을 `INFO` 레벨로 기록할 수도 있습니다. 조건 안의 DB 함수에 대해서는 `LQL-5001`, `LQL-5007`(`WARN`)과 `LQL-5008`(`INFO`)을 기록할 수 있습니다. [11. 오류와 언어](../11-errors-and-languages.md)를 참고하십시오.

---

[Kotlin 트랙](README.md) | 이전: [7. 수정, 삽입, 삭제](07-dml.md) | 다음: [9. Kotlin 노트](09-kotlin-notes.md) | [이 페이지의 Java 버전](../java/08-procedures-functions.md)
