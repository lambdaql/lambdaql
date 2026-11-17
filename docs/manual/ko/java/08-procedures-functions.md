<a id="8-stored-procedures-and-db-functions-java"></a>
# 8. 저장 프로시저와 DB 함수 (Java)

> [!NOTE]
> **상태**
> - **구현됨, 테스트함**(결정 D30, D31, 마일스톤 M11, Hibernate 6.6.5):
>   쿼리 안의 DB 함수(`@SqlFunction`)는 H2 2.3, MariaDB 10.11, Oracle 23.26에서 테스트했습니다([`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java)).
>   프로시저 호출(`@SqlProcedure`, `@InOut`, `qb.call`, `qb.exec`)은 MariaDB와 Oracle에서, DB 함수 단독 호출은 세 데이터베이스 모두에서 테스트했습니다([`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java)).
>   H2에는 OUT 파라미터가 없으므로 프로시저 테스트는 H2에서 실행하지 않습니다.
> - **추가로 구현됨, 테스트함:** 데이터베이스와의 스텁 인자 **이름** 대조(규칙 PR2의 일부), Oracle 패키지 오버로드 검출.
> - **추가로 구현됨, 테스트함:** 조건 안의 DB 함수에 대한 SARGable 경고(규칙 PR8, FN7, [8.9](#89-warning-functions-on-columns-and-indexes-sargable) 참고).
> - **아직 구현되지 않음:** 데이터베이스와의 파라미터 **타입** 대조(규칙 PR2의 일부), Oracle 패키지 프로시저의 오버로드 하나를 골라 호출하는 기능.
> - **(테스트됨)** 표시가 있는 예제는 테스트 코드에서 그대로 가져온 것입니다. 다른 예제도 같은 규칙을 따르지만 테스트로 확인하지는 않았습니다. 전체 상태는 [Java 트랙 README](README.md#status)를 참고하십시오.

[Java 트랙](README.md) | [매뉴얼 홈](../README.md) | 이전: [7. 수정, 삽입, 삭제](07-dml.md) | 다음: [9. Java 노트](09-java-notes.md) | [이 페이지의 Kotlin 버전](../kotlin/08-procedures-functions.md)

공통 페이지: [1. 개요](../01-overview.md) · [11. 오류와 언어](../11-errors-and-languages.md)

---

이 장은 두 부분으로 되어 있습니다.

| 부분 | 대상 | 애너테이션 | 호출 방법 |
|---|---|---|---|
| [8.1 ~ 8.5](#81-declare-procedures) | **저장 프로시저** | 인터페이스의 추상 메서드에 `@SqlProcedure` | `qb.call(OrderProcs.class, p -> p.calc(id, rate)).execute()` |
| [8.6 ~ 8.10](#86-declare-db-functions) | **DB 함수**(스칼라) | 인터페이스의 static 메서드에 `@SqlFunction` | 모든 람다 안에서 직접: `where(o -> OrderFns.discount(o.getPrice()) > 10)` |

두 경우 모두 시그니처는 Java 인터페이스에 **한 번만** 작성합니다.
lambdaQL은 이 메서드들을 **절대 실행하지 않습니다**. 람다 바이트코드에서 호출을 읽고 데이터베이스를 호출합니다.

<a id="contents"></a>
## 목차

- [8.1 프로시저 선언](#81-declare-procedures)
- [8.2 프로시저 호출](#82-call-procedures)
- [8.3 OUT, INOUT, 결과 집합](#83-out-inout-and-result-sets)
- [8.4 주의: 인자 순서](#84-warning-argument-order)
- [8.5 프로시저 규칙 PR1 ~ PR15(요약)](#85-procedure-rules-pr1-to-pr15-summary)
- [8.6 DB 함수 선언](#86-declare-db-functions)
- [8.7 쿼리에서 DB 함수 사용](#87-use-db-functions-in-queries)
- [8.8 DB 함수 단독 호출](#88-call-a-db-function-alone)
- [8.9 주의: 컬럼에 대한 함수와 인덱스(SARGable)](#89-warning-functions-on-columns-and-indexes-sargable)
- [8.10 함수 규칙 FN1 ~ FN11(요약)](#810-function-rules-fn1-to-fn11-summary)
- [8.11 데이터베이스 지원](#811-database-support)

---

<a id="81-declare-procedures"></a>
## 8.1 프로시저 선언

인터페이스를 작성합니다. 각 **추상** 메서드에 `@SqlProcedure("NAME")`을 붙이십시오.
구현은 작성하지 않습니다. lambdaQL도 구현을 만들지 않습니다.

**(테스트됨)** [`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java), 영역 `declare`:

```java
/** One OUT value or more: a record in the database order. */
record Stat(Double total, Integer cnt) {
}

/** The INOUT value and the OUT value, in the database order. */
record Adjusted(Integer balance, String msg) {
}

/** A row of a result set: columns are matched by name (customer_id or customerId). */
record Line(Long id, Double price) {
}

/** A stub interface: lambdaQL reads the calls and never implements it. */
interface OrderProcs {
    @SqlProcedure("SQ_PROC_CALC")
    Double calc(Integer customerId, Double rate);                 // IN, IN, OUT

    @SqlProcedure("SQ_PROC_STAT")
    Stat stat(Integer customerId);                                // IN, OUT, OUT

    @SqlProcedure("SQ_PROC_ADJUST")
    Adjusted adjust(Integer add, @InOut Integer balance);         // IN, INOUT, OUT

    @SqlProcedure("SQ_PROC_RECENT")
    List<Line> recent(Integer customerId);                        // a result set (Oracle: a REF CURSOR)

    @SqlProcedure("SQ_PROC_PURGE")
    void purge(Integer status);                                   // no result

    @SqlProcedure("SQ_PROC_CALC")
    Double calcWrong(Integer customerId);                         // does not match the database: 1 argument
}

/** A database function. */
interface MathFns {
    @SqlFunction("SQ_FN_HALF")
    static Double half(Double value) {
        throw new DbOnlyException();
    }
}
```

테스트는 실행 전에 MariaDB와 Oracle에 이 프로시저를 만듭니다. 예(MariaDB):

```sql
CREATE OR REPLACE PROCEDURE SQ_PROC_CALC(IN p_cust INT, IN p_rate DOUBLE, OUT p_total DOUBLE)
  BEGIN SELECT COALESCE(SUM(price), 0) * p_rate INTO p_total FROM SQ_orders WHERE customer_id = p_cust; END
```

`calcWrong`은 일부러 틀리게 선언한 메서드입니다. 테스트는 이 메서드로 규칙 PR2를 확인합니다([8.4](#84-warning-argument-order) 참고).

참고:

- `@SqlProcedure`(또는 `@SqlFunction`)는 **필수**입니다. 이 메서드가 무엇인지 lambdaQL에 알려 줍니다.
  (`@Procedure`와 `@Function`이라는 이름은 사용하지 않았습니다. `@Procedure`는 Spring Data JPA 애너테이션이기도 하고, `@Function`은 Kotlin의 `kotlin.Function`과 충돌합니다.)
- 값이 비어 있으면 이름은 메서드 이름에서 만듭니다: `calcTotal` → `CALC_TOTAL`.
- `@SqlProcedure`에는 `schema`와 `pkg`(Oracle 패키지)도 있습니다: `@SqlProcedure(value = "CALC", schema = "LQL", pkg = "SQ_PKG")`.
- 박싱 타입(`Double`, `Integer`)을 반환 타입으로 사용하십시오. 프로시저는 NULL을 반환할 수 있습니다.
- 인터페이스와 결과 레코드는 DB 시그니처의 복사본입니다. 타입 안전성의 대가입니다. lambdaQL은 그 일부를 데이터베이스와 대조해 확인합니다(규칙 PR2).
- 프로시저(추상 메서드)와 DB 함수(static 메서드)를 **같은 인터페이스**에 둘 수 있습니다.

---

<a id="82-call-procedures"></a>
## 8.2 프로시저 호출

`qb.call(Interface.class, p -> p.method(args))`를 사용합니다. 그다음 `execute()`를 호출합니다.

**(테스트됨)** [`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java), 영역 `call`(MariaDB, Oracle):

```java
Double total = qb.call(OrderProcs.class, p -> p.calc(customerId, rate)).execute();
Stat stat = qb.call(OrderProcs.class, p -> p.stat(customerId)).execute();
Adjusted adjusted = qb.call(OrderProcs.class, p -> p.adjust(5, 10)).execute();
List<Line> recent = qb.call(OrderProcs.class, p -> p.recent(customerId)).execute();
```

`customerId`와 `rate`는 캡처한 지역 값입니다. 테스트는 각 결과를 Java로 계산한 값과 비교합니다. 예를 들어 `adjusted`는 `new Adjusted(15, "ok")`입니다.

결과가 없는(`void`) 프로시저는 `exec`로 호출합니다.

**(테스트됨)** [`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java), 영역 `exec`(MariaDB, Oracle):

```java
qb.exec(OrderProcs.class, p -> p.purge(9)).execute();
```

void에 `call`이 아니라 `exec`를 쓰는 이유는 무엇일까요?

- Java에서 둘 다 `call`로 하면 모호한 오버로드가 됩니다(컴파일 오류).
- `run`은 선택하지 않았습니다. Kotlin에 같은 이름의 표준 라이브러리 함수가 있어서, Kotlin 호출자가 오류 없이 실수로 그 함수를 호출할 수 있습니다.

<a id="what-the-lambda-may-contain-rule-pr12"></a>
### 람다에 들어갈 수 있는 것(규칙 PR12)

람다 본문은 **스텁 메서드 호출 하나**입니다. 그 밖에는 아무것도 없습니다.

| 허용 | 허용 안 됨 |
|---|---|
| `p -> p.calc(id, rate)` | 로깅, 호출 두 번, `if`, 중첩 호출 |
| 인자: 캡처 값, 상수, 그것들의 계산(`rate * 2`) | 엔티티 경로(여기에는 엔티티가 없음) |

그 밖의 사항:

- OUT 파라미터가 있는 프로시저에 `exec`를 쓰면 오류입니다(`LQL-4037`). OUT 값을 실수로 잃지 않도록 막습니다.
- `@SqlProcedure`가 없는 메서드를 호출하면 오류입니다(`LQL-3060`).
- 파라미터의 데이터베이스 기본값은 사용할 수 없습니다. 모든 IN 파라미터를 전달하십시오.
- IN 값으로 `null`을 넘겨도 됩니다.

---

<a id="83-out-inout-and-result-sets"></a>
## 8.3 OUT, INOUT, 결과 집합

반환 타입이 프로시저에 대응하는 방식(규칙 PR3, PR4, PR5):

| 프로시저에 있는 것 | 스텁 메서드의 반환 타입 |
|---|---|
| 없음 | `void` → `qb.exec(...)`로 호출 |
| OUT 파라미터 1개 | 그 값(`Double`, `Integer`, ...) |
| OUT / INOUT 파라미터 여러 개 | Java 레코드(Kotlin: data class) |
| 결과 집합 1개(MariaDB `SELECT`, Oracle `SYS_REFCURSOR` OUT, H2 `ResultSet` 함수) | `List<T>` |
| 결과 집합 여러 개 | `List` 컴포넌트를 가진 레코드 |
| OUT 값과 결과 집합(MariaDB) | 스칼라 컴포넌트와 `List` 컴포넌트를 가진 레코드 |

레코드 컴포넌트를 채우는 방식:

- **스칼라 컴포넌트** ← OUT과 INOUT 파라미터(커서 제외), DB 순서대로.
- **`List` 컴포넌트** ← 결과 집합, 순서대로(Oracle: 커서 OUT 파라미터의 순서, MariaDB: 반환된 결과 집합의 순서).
- 개수가 맞지 않으면 lambdaQL은 오류를 던집니다(`LQL-4038`).
- IN, OUT, IN, INOUT처럼 섞인 위치도 괜찮습니다. lambdaQL은 DB 메타데이터에서 위치를 읽습니다.

### INOUT

파라미터에 `@InOut`을 붙이십시오. 전달한 값은 **들어가는** 값입니다. **나오는** 값은 반환된 레코드의 컴포넌트입니다.

테스트의 `SQ_PROC_ADJUST(p_add IN, p_balance IN OUT, p_msg OUT)`는 `p_balance`에 `p_add`를 더합니다.

```java
@SqlProcedure("SQ_PROC_ADJUST")
Adjusted adjust(Integer add, @InOut Integer balance);         // IN, INOUT, OUT

Adjusted adjusted = qb.call(OrderProcs.class, p -> p.adjust(5, 10)).execute();
// adjusted.balance()는 15(새 잔액), adjusted.msg()는 "ok"
```

`@InOut`은 데이터베이스에서 INOUT인 인자에만 정확히 붙여야 합니다. 그렇지 않으면 첫 호출이 실패합니다(`LQL-4036`).

<a id="result-set-element-type-t"></a>
### 결과 집합의 요소 타입 `T`

| `T`가 | lambdaQL이 읽는 방법 |
|---|---|
| 엔티티 | Hibernate 결과 클래스(`StoredProcedureQuery`) |
| Java 레코드(Kotlin: data class) | JDBC `CallableStatement`, 컬럼을 **레이블로** 대응. `_`를 빼고 대소문자를 무시하므로 `customer_id`가 `customerId`에 대응(`Line`으로 테스트함) |
| 값 하나(`Long`, `String`, ...) | 각 행의 첫 컬럼 |

일반 DTO 클래스는 여기서 행 타입으로 쓸 수 없습니다. 레코드를 사용하십시오.
MariaDB와 Oracle은 `SELECT *`의 컬럼을 서로 다른 순서로 반환하므로, lambdaQL은 레코드 컴포넌트를 위치로 대응하지 않습니다.
흔한 타입은 변환합니다(예: 숫자를 `Long`/`Integer`로, 타임스탬프를 `LocalDateTime`으로).

제한:

- 프로시저 결과는 페이징할 수 없습니다.
- Oracle 암시적 결과(`DBMS_SQL.RETURN_RESULT`)는 지원하지 않습니다. `SYS_REFCURSOR` OUT 파라미터를 사용하십시오.

<a id="transactions-rule-pr9"></a>
### 트랜잭션(규칙 PR9)

- 프로시저 안의 DML은 호출한 트랜잭션의 일부입니다. 함께 롤백됩니다.
- 호출 전에 lambdaQL은 영속성 컨텍스트를 flush합니다. 단, 트랜잭션이 활성 상태이고 변경 사항이 있을 때만입니다.
- `QueryBuilder(EntityManagerFactory)`는 호출을 자체 트랜잭션에서 실행합니다. `EntityManager`를 받은 `QueryBuilder`(Spring의 공유 `EntityManager` 포함)는 호출자의 트랜잭션을 사용합니다. 트랜잭션이 없으면 호출은 자동 커밋됩니다.
- 호출 후에는 로드한 엔티티가 오래된 상태일 수 있습니다. 그 엔티티에 `refresh`나 `evict`를 사용하십시오.
- 프로시저 안의 `COMMIT`이나 DDL은 트랜잭션을 깨뜨립니다. lambdaQL은 이를 막을 수 없습니다.

<a id="affected-rows-rule-pr10"></a>
### 영향받은 행(규칙 PR10)

프로시저에는 공통된 "영향받은 행 수" 결과가 없습니다(Oracle은 -1을 반환).
그 수가 필요하면 OUT 파라미터로 반환하십시오.

---

<a id="84-warning-argument-order"></a>
## 8.4 주의: 인자 순서

> [!WARNING]
> **스텁 메서드 파라미터의 순서는 데이터베이스의 순서와 같아야 합니다.**

lambdaQL은 인자를 **위치로** 바인딩합니다(규칙 PR2).
Hibernate 6.6.5는 이름이 등록되어 있어도 위치로 바인딩합니다.
MariaDB와 H2 드라이버는 이름 있는 파라미터를 지원하지 않습니다.
그래서 순서가 틀리면 **아무 오류 없이 잘못된 결과**를 얻을 수 있습니다.

첫 호출에서 lambdaQL이 JDBC 메타데이터(`getProcedureColumns`)로 확인하는 항목입니다. 결과는 프로시저 이름별로 캐시합니다.

| 확인 항목 | 발견 여부 |
|---|---|
| 프로시저의 존재 | 예, 오류 `LQL-4035` |
| IN·INOUT 파라미터 개수 = 스텁 인자 개수 | 예, 오류 `LQL-4036`(테스트함) |
| INOUT 파라미터 = `@InOut`을 붙인 인자 | 예, 오류 `LQL-4036` |
| OUT 값과 결과 집합의 개수 = 반환 타입의 구성 요소 수 | 예, 오류 `LQL-4038` |
| 타입 | **아직 구현되지 않음** |
| 같은 파라미터 이름이 다른 순서로 놓임 | 예, 오류 `LQL-4043`(테스트함). 스텁 인자 이름을 알 수 있을 때만(아래 참고) |
| Oracle 패키지 프로시저 이름에 오버로드가 여러 개 | 예, 오류 `LQL-4044`(Oracle에서 테스트함) |

**(테스트됨)** [`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java), 영역 `rules`(MariaDB, Oracle):

```java
// PR2: the stub is checked against the database parameters on the first call
assertEquals("LQL-4036", assertThrows(LambdaQLException.class,
        () -> qb.call(OrderProcs.class, p -> p.calcWrong(1)).execute()).code());
// exec cannot drop OUT values
assertEquals("LQL-4037", assertThrows(LambdaQLException.class,
        () -> qb.exec(OrderProcs.class, p -> p.calc(1, 1.0)).execute()).code());
// only @SqlProcedure methods of the stub can be called
assertEquals("LQL-3060", assertThrows(LambdaQLException.class,
        () -> qb.call(OrderProcs.class, p -> p.toString()).execute()).code());
```

마지막 경우는 `@SqlProcedure`가 없는 `toString()`을 호출합니다. 그래서 코드는 `LQL-3060`(메서드가 `@SqlProcedure`가 아님)입니다.

위험한 경우의 예:

```java
// 데이터베이스: SP_TRANSFER(p_to NUMBER, p_from NUMBER)
@SqlProcedure("SP_TRANSFER") void transfer(Long from, Long to);   // 뒤바뀜! 같은 타입
```

개수, 모드, 타입이 모두 일치합니다. 이름만이 실수를 드러낼 수 있습니다.
그래서 lambdaQL은 첫 호출 때 IN과 INOUT 인자의 이름을 비교합니다.

- 스텁 인자 이름은 클래스 파일에서 읽습니다. `-parameters`로 컴파일한 경우에만 이름이 남습니다([2. 시작하기](02-getting-started.md#recommended-compiler-option--parameters) 참고). 스텁 인자에 `@org.lambdaql.DbParam("p_to")`를 붙이면 DB 이름을 직접 지정합니다.
- DB 이름에서 **모든** 이름에 공통이고 `_`로 끝나는 접두사(`p_`, `in_`, `v_` 등)를 제거합니다. 정해진 접두사를 가정하지 않습니다. 이어서 대소문자와 `_`를 무시합니다.
- 이름은 같고 순서만 다르면 호출 전에 `LambdaQLException` `LQL-4043`이 발생합니다.
- 이름이 다르면(스텁이 다른 명명 규칙을 따름) 오류가 아닙니다. 검사가 동작하도록 `@DbParam`을 붙이십시오.
- 클래스 파일에 이름이 없고 이웃한 두 인자의 타입이 같으면, 프로시저마다 한 번 `LQL-5004`를 `INFO` 레벨로 기록합니다.
- 타입은 아직 비교하지 않습니다.

**(테스트됨)** [`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java), 영역 `names`(DB 프로시저는 `SQ_PROC_DIFF(p_to, p_from)`. 테스트 스텁은 `-parameters`로 컴파일함):

```java
/** Argument names: with -parameters lambdaQL compares them with the database names (p_to, p_from). */
interface DiffProcs {
    @SqlProcedure("SQ_PROC_DIFF")
    Integer diff(Integer to, Integer from);                       // same order as the database: runs

    @SqlProcedure("SQ_PROC_DIFF")
    Integer swapped(Integer from, Integer to);                    // same names, other order: LQL-4043

    @SqlProcedure("SQ_PROC_DIFF")
    Integer named(@DbParam("p_to") Integer target, @DbParam("p_from") Integer source);   // another naming rule
}
```

**(테스트됨)** [`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java), 영역 `name-check`(MariaDB, Oracle):

```java
assertEquals(7, qb.call(DiffProcs.class, p -> p.diff(10, 3)).execute());
assertEquals(7, qb.call(DiffProcs.class, p -> p.named(10, 3)).execute());
// the same names in another order: stopped before the call (it would run with the values swapped)
assertEquals("LQL-4043", assertThrows(LambdaQLException.class,
        () -> qb.call(DiffProcs.class, p -> p.swapped(3, 10)).execute()).code());
```

**권장:** `-parameters`로 컴파일하고 DB와 같은 이름을 쓰거나(공통 접두사는 생략 가능) `@DbParam`을 붙이십시오. 그러면 뒤바뀐 순서를 검출합니다.

**Oracle 패키지 오버로드.** Oracle 패키지에는 같은 이름의 프로시저가 여러 개 있을 수 있습니다.
lambdaQL은 스텁이 어느 것을 가리키는지 구분할 수 없으므로, 그런 이름을 호출하면 `LQL-4044`로 중단합니다. 오버로드 하나를 골라 호출하는 기능은 이후 계획입니다.

**(테스트됨)** [`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java), 영역 `overload`(Oracle 전용). 스텁 `PkgProcs`는 `@SqlProcedure(pkg = "SQ_PKG", value = "TWICE")`와 `@SqlProcedure(pkg = "SQ_PKG", value = "HALF")`를 선언합니다:

```java
assertEquals(5.0, qb.call(PkgProcs.class, p -> p.twice(2.5)).execute(), 1e-9);
// HALF has two overloads in the package: lambdaQL cannot tell which one to call
assertEquals("LQL-4044", assertThrows(LambdaQLException.class,
        () -> qb.call(PkgProcs.class, p -> p.half(10.0)).execute()).code());
```

<a id="when-the-metadata-cannot-be-read"></a>
**메타데이터를 읽을 수 없는 경우.** DB 사용자에게 딕셔너리 뷰나 `information_schema`를 읽을 권한이 없을 수 있습니다.
메타데이터 조회 자체가 실패하면(`SQLException`) lambdaQL은 호출을 중단하지 않습니다.

- 프로시저마다 한 번 `LQL-5005`를 `INFO` 레벨로 기록하고, 스텁을 대조하지 않습니다.
- 인자 배치는 스텁에서 가져옵니다. IN과 INOUT 인자는 스텁 순서, 그 뒤에 OUT 파라미터(결과 구성 요소 순서), Oracle에서는 그 뒤에 `List` 구성 요소마다 `REF CURSOR` 하나입니다. 따라서 메타데이터가 없으면 DB 선언에서 OUT 파라미터가 IN 파라미터 뒤에 있어야 합니다.
- 메타데이터가 필요한 검사는 건너뜁니다: 개수와 `@InOut` 검사(`LQL-4036`), 이름 순서 검사(`LQL-4043`).
- 조회는 성공했지만 프로시저가 없으면 여전히 `LQL-4035`입니다.
- Oracle 오버로드 조회(`all_arguments`)만 실패하면 `LQL-5006`을 `INFO` 레벨로 기록하고, 오버로드 확인 없이 호출합니다.

이 대체 동작은 MariaDB와 Oracle에서 테스트했습니다(`ProcedureTest`의 테스트 메서드 `withoutMetadata`, 실패를 모의함).

Oracle의 실제 이름 바인딩(`pass_procedure_paramater_names=true`와 스텁 파라미터 이름을 함께 사용)도 계획되어 있습니다.

---

<a id="85-procedure-rules-pr1-to-pr15-summary"></a>
## 8.5 프로시저 규칙 PR1 ~ PR15(요약)

| # | 규칙 | 상태 |
|---|---|---|
| PR1 | 이름: 애너테이션 값 그대로. 값이 비어 있으면 메서드 이름을 UPPER_SNAKE로. 패키지나 스키마 이름(`SQ_PKG.CALC`, `LQL.SQ_CALC`)도 동작합니다. 혼동을 피하려면 `@SqlProcedure(schema = ..., pkg = ..., value = ...)`로 쓸 수도 있습니다. | 구현됨. 스키마·패키지 이름은 테스트로 확인하지 않음 |
| PR2 | 인자는 **위치로** 바인딩됩니다. 첫 호출에서 IN/INOUT 파라미터 개수와 `@InOut` 위치를 DB 메타데이터와 대조합니다. 이름을 알 수 있으면 이름도 대조하며, 같은 이름이 다른 순서면 오류입니다. 타입 대조는 계획되어 있습니다([8.4](#84-warning-argument-order) 참고). 메타데이터를 읽을 수 없으면 검사를 건너뛰고 인자 배치를 스텁에서 가져옵니다([8.4](#when-the-metadata-cannot-be-read) 참고). | 개수, `@InOut`, 이름 대조는 구현됨(테스트함). Oracle 패키지 오버로드 검출도 구현됨(Oracle에서 테스트함). 타입 대조는 아직 구현되지 않음. 메타데이터 없는 대체 동작(`LQL-5005`, `LQL-5006`)은 구현됨(MariaDB, Oracle에서 테스트함). |
| PR3 | OUT 1개 → 반환 값. OUT/INOUT 여러 개 → 레코드. `@InOut`은 INOUT 파라미터를 표시합니다. | 구현됨(테스트함) |
| PR4 | 결과 집합 1개 → `List<T>`. 엔티티는 Hibernate로, 레코드는 레이블 대응으로 JDBC를 통해 읽습니다. 페이징 없음. | 구현됨(레코드로 테스트함) |
| PR5 | 결과 집합 여러 개 → 레코드의 `List` 컴포넌트, 순서대로. Oracle 암시적 결과는 지원하지 않습니다. | 구현됨, 테스트로 확인하지 않음 |
| PR6 | 단독으로 호출한 DB 함수는 데이터베이스마다 다른 경로를 사용합니다([8.8](#88-call-a-db-function-alone) 참고). | 구현됨(테스트함) |
| PR7 | 쿼리 안의 DB 함수: [8.6](#86-declare-db-functions)과 규칙 FN1 ~ FN11 참고. | 구현됨(테스트함) |
| PR8 | 조건에서 컬럼에 DB 함수를 쓰면 인덱스를 사용할 수 없으므로 WARN을 남깁니다([8.9](#89-warning-functions-on-columns-and-indexes-sargable) 참고). 코드는 `LQL-5001`. | 구현됨(테스트함). 엄격 모드는 아직 구현되지 않음 |
| PR9 | 프로시저는 호출한 트랜잭션 안에서 실행됩니다. lambdaQL은 필요하면 호출 전에 flush합니다. 호출 후에는 로드한 엔티티가 오래된 상태일 수 있습니다. | 구현됨 |
| PR10 | 공통된 영향받은 행 수가 없습니다. OUT 파라미터를 사용하십시오. | – |
| PR11 | `null` IN 값은 괜찮습니다. DB 파라미터 기본값은 사용할 수 없습니다. | 구현됨 |
| PR12 | 람다는 스텁 호출 하나입니다. 인자는 캡처 값, 상수, 또는 그것들의 계산입니다. OUT 파라미터가 있을 때 `exec`는 오류입니다. | 구현됨(테스트함) |
| PR13 | boolean 파라미터는 MariaDB와 Oracle 23ai 이상에서 동작합니다. | 테스트로 확인하지 않음 |
| PR14 | 각 람다는 한 번 분석합니다. 프로시저 메타데이터는 이름마다 한 번 읽습니다. | 구현됨 |
| PR15 | H2에는 OUT, INOUT, 여러 결과 집합, 패키지가 없습니다. 프로시저는 MariaDB나 Oracle에서 테스트하십시오(예: Testcontainers 사용). | lambdaQL 테스트도 이 방식을 따름 |

PR11, PR12, PR13에는 Kotlin 전용 부분(기본 인자, 이름 있는 인자, nullable 타입)이 더 있습니다. [이 페이지의 Kotlin 버전](../kotlin/08-procedures-functions.md)에 있습니다.

---

<a id="86-declare-db-functions"></a>
## 8.6 DB 함수 선언

DB 함수는 데이터베이스에 직접 만든 함수입니다. 예: `FN_DISCOUNT(price)`.
`@SqlFunction`을 붙인 **인터페이스의 static 메서드**로 선언합니다.
본문은 예외를 던지기만 해야 합니다. lambdaQL 안에서 절대 실행되지 않습니다.

**(테스트됨)** [`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java), 영역 `declare`:

```java
/** Database functions: static methods of your own interface. The bodies only throw. */
public interface OrderFns {
    /** {@code SQ_FN_DISCOUNT(price)}: 90% of the price. */
    @SqlFunction("SQ_FN_DISCOUNT")
    static Double discount(Double price) {
        throw new DbOnlyException();
    }

    /** {@code SQ_FN_IS_GOLD(grade)}: a grade of 5 or more. */
    @SqlFunction("SQ_FN_IS_GOLD")
    static Boolean isGold(Integer grade) {
        throw new DbOnlyException();
    }

    /** A query name with its own SQL name; registered with lambdaql.functions.classes. */
    @SqlFunction(value = "SQ_TAX", sql = "SQ_FN_DISCOUNT")
    static Double tax(Double price) {
        throw new DbOnlyException();
    }
}
```

테스트는 실행 전에 데이터베이스에 `SQ_FN_DISCOUNT`와 `SQ_FN_IS_GOLD`를 만들고, 끝나면 삭제합니다. Boolean 결과에는 Oracle 23ai 이상이 필요합니다.

lambdaQL 람다 **밖에서** `OrderFns.discount(...)`를 호출하면 `DbOnlyException`(`LQL-4034`)이 발생합니다. 의도된 동작입니다.

`@SqlFunction`의 값은 두 가지입니다.

| 값 | 의미 |
|---|---|
| `value` | 쿼리에 쓰는 함수 이름. 비어 있으면 메서드 이름을 UPPER_SNAKE로(`isVip` → `IS_VIP`). 스키마 이름도 허용(`PUBLIC.SQ_FN`) |
| `sql` | `value`와 다를 때의 SQL 이름. 이런 함수는 등록이 필요합니다([등록](#registration-optional) 참고) |

다른 선언 방법:

| 선언 | 상태 |
|---|---|
| 인터페이스 static 메서드 | **권장**(테스트함) |
| 클래스 static 메서드 | 설계됨, 테스트로 확인하지 않음 |
| 인터페이스 default 메서드 + 싱글턴 상수 | 설계됨, 테스트로 확인하지 않음 |
| 리포지토리가 함수 인터페이스를 `implements`(믹스인) | 거부됨 |
| 함수형 인터페이스 상수(`DISCOUNT.apply(x)`) | 거부됨 |

함수는 **인터페이스 이름과 함께** 호출하십시오(`OrderFns.discount`). static import도 동작하지만, 같은 이름의 멤버 메서드가 그것을 가릴 수 있습니다.

---

<a id="87-use-db-functions-in-queries"></a>
## 8.7 쿼리에서 DB 함수 사용

함수를 직접 호출하십시오. 컨텍스트 인자는 필요 없습니다.
`where`, `select`, `orderBy`, `set`에서 동작합니다(테스트함). `groupBy`, `having`, 서브쿼리에서도 동작하도록 설계했지만 테스트로 확인하지는 않았습니다.

**(테스트됨)** [`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java), 영역 `where`:

```java
// the function call is written as function('<name>' as <type>, ...); the column is its argument
SelectQuery<Order> q = qb.from(Order.class).where(o -> OrderFns.discount(o.getPrice()) > 100);
assertEquals("select e0 from Order e0 where function('SQ_FN_DISCOUNT' as Double, e0.price) > 100.0", q.toHql().hql());
```

**(테스트됨)** [`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java), 영역 `captured`:

```java
// a function of captured values only is still computed by the database (the value is bound)
SelectQuery<Order> q = qb.from(Order.class).where(o -> o.getPrice() > OrderFns.discount(base));
assertEquals("select e0 from Order e0 where e0.price > function('SQ_FN_DISCOUNT' as Double, :p1)", q.toHql().hql());
```

Boolean 함수는 조건 전체로 쓸 수 있습니다. lambdaQL이 `= true`를 붙입니다.

**(테스트됨)** [`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java), 영역 `boolean`:

```java
SelectQuery<Order> q = qb.from(Order.class).where(o -> OrderFns.isGold(o.getCustomer().getGrade()));
assertEquals("select e0 from Order e0 join e0.customer j1 where function('SQ_FN_IS_GOLD' as Boolean, j1.grade) = true",
        q.toHql().hql());
```

**(테스트됨)** [`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java), 영역 `select`(`select`와 `orderBy`):

```java
ValueQuery<Priced> q = qb.from(Order.class)
        .orderBy((o, by) -> by.desc(OrderFns.discount(o.getPrice())))
        .select(o -> new Priced(o.getId(), OrderFns.discount(o.getPrice())));
assertEquals("select e0.id, function('SQ_FN_DISCOUNT' as Double, e0.price) from Order e0"
        + " order by function('SQ_FN_DISCOUNT' as Double, e0.price) desc nulls last", q.toHql().hql());
```

`Priced`는 `record Priced(Long id, Double discounted)`입니다.

**(테스트됨)** [`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java), 영역 `update`:

```java
ExecutableUpdate<Order> u = qb.update(Order.class)
        .set(o -> o.setPrice(OrderFns.discount(o.getPrice())))
        .where(o -> o.getPrice() > 100);
assertEquals("update Order e0 set e0.price = function('SQ_FN_DISCOUNT' as Double, e0.price) where e0.price > 100.0",
        u.toHql().hql());
```

테스트는 각 쿼리를 실행하고, 결과 행을 Java로 같은 계산(`price * 0.9`)을 한 결과와 비교합니다.

**SQL 블록 읽는 법.** `…`는 줄여 쓴 select 목록(엔티티의 모든 컬럼)이고, `?`는 바인드 파라미터입니다. [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java)는 같은 형태의 HQL(이름 `FN_DISCOUNT`)을 Hibernate에 직접 넘기고, H2 2.3, MariaDB 10.11, Oracle 23.26(Hibernate 6.6.5)에서 나온 SQL을 기록합니다. Hibernate의 SQL이 바뀌면 테스트가 실패합니다.

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

<a id="how-lambdaql-renders-it"></a>
### lambdaQL이 렌더링하는 방식

lambdaQL은 DB 함수를 **항상** 다음과 같이 렌더링합니다.

```sql
function('<NAME>' as <Type>, arg1, arg2, ...)
```

`<Type>`은 선언된 반환 타입에서 옵니다. 다음 타입만 허용됩니다.

| 허용되는 반환 타입 |
|---|
| `Double`, `Integer`, `Long`, `BigDecimal`, `String`, `LocalDate`, `LocalDateTime`, `Boolean` |

제네릭 반환(`<X> X nvl(X, X)`), 엔티티, 컬렉션, `void`는 분석 시점에 거부됩니다.

<a id="registration-optional"></a>
### 등록(선택)

`function('FN_DISCOUNT' as Double, ...)` 형태에는 등록이 필요 없습니다(세 데이터베이스에서 확인).
함수 인터페이스를 등록하면 Hibernate가 이름의 대소문자도 유지하고 인자 개수와 타입도 확인합니다.

lambdaQL에는 Hibernate `FunctionContributor`인 `org.lambdaql.hibernate.LambdaQlFunctionContributor`가 있습니다(ServiceLoader로 찾음). 인터페이스는 Hibernate 프로퍼티에 나열합니다.

```properties
# 일반 Hibernate
lambdaql.functions.classes=com.acme.OrderFns,com.acme.CustomerFns

# Spring Boot
spring.jpa.properties.lambdaql.functions.classes=com.acme.OrderFns
```

프로퍼티를 설정하지 않으면 아무것도 등록되지 않습니다. 패키지 스캔은 v0.1에 없습니다.
중첩 인터페이스는 `$`가 들어간 바이너리 이름으로 적습니다. 테스트 설정은 `lambdaql.functions.classes: org.lambdaql.query.FunctionTest$OrderFns`입니다.

<a id="a-different-sql-name-needs-registration"></a>
### SQL 이름이 다른 함수의 등록

`value`는 쿼리의 이름이고 `sql`은 SQL의 이름입니다. 두 이름이 다르면 함수를 등록해야 합니다.
따옴표로 만든 대소문자 혼합 함수 이름(`"SQ_Fn_Mixed"`)에 유용합니다. 등록하지 않으면 H2와 Oracle에서 이름이 소문자가 될 수 있습니다.

```java
@SqlFunction(value = "SQ_Fn_Mixed", sql = "\"SQ_Fn_Mixed\"")
static Double mixed(Double v) { throw new DbOnlyException(); }
```

**(테스트됨)** [`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java), 영역 `registered`:

```java
// SQ_TAX is registered at start-up (lambdaql.functions.classes) and runs SQ_FN_DISCOUNT in SQL
SelectQuery<Order> q = qb.from(Order.class).where(o -> OrderFns.tax(o.getPrice()) > 100);
assertEquals("select e0 from Order e0 where function('SQ_TAX' as Double, e0.price) > 100.0", q.toHql().hql());
```

그런 함수가 등록되어 있지 않으면 lambdaQL은 람다를 읽을 때 실패합니다(`LQL-3058`, [8.10](#810-function-rules-fn1-to-fn11-summary) 참고).

---

<a id="88-call-a-db-function-alone"></a>
## 8.8 DB 함수 단독 호출

**인자가 없는 람다**로 `qb.call(() -> ...)`를 사용하십시오.

**(테스트됨)** [`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java), 영역 `function`(H2, MariaDB, Oracle):

```java
Double half = qb.call(() -> MathFns.half(price)).execute();
```

`price`는 캡처한 `double`(150)이고, `half`는 75.0입니다. 람다는 `@SqlFunction` 호출 하나여야 합니다(`LQL-3063`).

**데이터베이스별 SQL(이 형태의 HQL에 대한 Hibernate SQL)** — `PlannedSqlTest`가 이름 `FN_DISCOUNT`로 기록했습니다. `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)의 예제 `08-function-alone`:

```sql
-- H2, MariaDB, Oracle (same)
-- HQL: select function('FN_DISCOUNT' as Double, :p1)
select FN_DISCOUNT(?)
```

이 쿼리는 FROM 없이 `select FN_DISCOUNT(?)`로 실행됩니다. Oracle은 23ai부터만 이를 받아들입니다. Oracle에서는 lambdaQL이 대신 JDBC 함수 호출을 사용합니다(아래 표 참고).

인자 개수가 다르므로 `qb.call(OrderProcs.class, p -> ...)`와 충돌하지 않습니다.

lambdaQL이 실행하는 방법(규칙 PR6):

| 데이터베이스 | 경로 |
|---|---|
| Oracle | JDBC 함수 호출 `{? = call FN(?)}`. DML을 하는 함수와 커서를 반환하는 함수에도 동작합니다. |
| MariaDB, H2 | HQL `select function('FN' as T, :p1)` |

lambdaQL은 먼저 대기 중인 변경 사항을 flush합니다(PR9와 같음). 그래서 함수는 최신 변경 사항을 봅니다.

---

<a id="89-warning-functions-on-columns-and-indexes-sargable"></a>
## 8.9 주의: 컬럼에 대한 함수와 인덱스(SARGable)

> [!NOTE]
> **구현됨, 테스트함**(H2, MariaDB 10.11, Oracle 23.26, 규칙 PR8, FN7). 아직 구현되지 않음: 엄격 모드(경고 대신 오류), 함수 없이 열에 적용한 산술 연산의 경고.

lambdaQL은 **인덱스를 사용할 수 있는** SQL을 만들려고 합니다([10. 규칙과 제한](10-rules-and-limits.md#102-sargable-rule-index-friendly-sql) 참고).
컬럼을 감싼 함수는 보통 일반 인덱스를 **사용할 수 없습니다**.

```java
.where(o -> OrderFns.discount(o.getPrice()) > 10)
// → function('FN_DISCOUNT' as Double, e0.price) > 10   -- price의 인덱스를 사용하지 않음
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
| ① 함수가 열을 감쌈: `fn(o.price) > 10`, 열에 적용한 Boolean 함수, `IN`이나 `IS NULL` 안의 함수 | `LQL-5001`(WARN) | WARN | WARN(저장 함수는 인덱스에 쓸 수 없음) | WARN. **단**, `user_ind_expressions`에 같은 식 `FN(COLUMN)`의 함수 기반 인덱스가 그 테이블에 있으면 기록하지 않음 |
| ② 값 쪽의 함수를 열과 비교: `o.price > fn(rate)` | `LQL-5007`(WARN) | 검사하지 않음 | `information_schema.routines.is_deterministic = 'NO'`(기본값!)이면 WARN. 이때 MariaDB는 인덱스 전체를 스캔함 | 검사하지 않음(Oracle은 어느 쪽이든 범위 스캔) |

- Oracle 인덱스 대조에 쓰는 테이블·열 이름은 Hibernate 매핑에서 가져옵니다. 열 인자 하나인 함수만 대조합니다.
- 메타데이터 조회가 실패하면(예: 권한 없음) `LQL-5008`을 `INFO` 레벨로 기록합니다. ①은 그대로 경고하고, ②는 검사하지 않습니다.

**(테스트됨)** [`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java), 영역 `sargable`(테스트는 로그를 수집함: H2는 `LQL-5001`, MariaDB는 `LQL-5001`과 `LQL-5007`, `SQ_FN_DISCOUNT(price)` 함수 기반 인덱스가 있는 Oracle은 없음):

```java
// the function wraps the column: an index on price cannot be used (LQL-5001 at WARN),
// unless Oracle has a function-based index on SQ_FN_DISCOUNT(price)
qb.from(Order.class).where(o -> OrderFns.discount(o.getPrice()) > 100).toHql();
// the function is on the value side: fine, unless MariaDB says NOT DETERMINISTIC (LQL-5007 at WARN)
qb.from(Order.class).where(o -> o.getPrice() > OrderFns.nondet(base)).toHql();
```

**권장:**

- 가능하면 함수를 값 쪽에 두십시오: `o.getPrice() > OrderFns.discount(base)`.
- MariaDB에서는 실제로 결정적인 함수라면 `DETERMINISTIC`으로 선언하십시오.
- Oracle에서는 같은 식의 함수 기반 인덱스가 있으면 경고가 사라집니다(이런 인덱스에는 `DETERMINISTIC` 함수가 필요합니다).

---

<a id="810-function-rules-fn1-to-fn11-summary"></a>
## 8.10 함수 규칙 FN1 ~ FN11(요약)

| # | 규칙 | 상태 |
|---|---|---|
| FN1 | `@SqlFunction`은 메서드 애너테이션입니다. 값이 비어 있으면 메서드 이름을 UPPER_SNAKE로. | 구현됨 |
| FN2 | lambdaQL은 호출된 메서드를 찾아 애너테이션을 읽습니다. 메서드 본문은 예외를 던지기만 해야 합니다(`throw new ...`). 실제 본문이 있는 메서드는 Java와 데이터베이스가 다른 값을 낼 수 있으므로 오류입니다(`LQL-3052`). | 구현됨(테스트함) |
| FN3 | 같은 이름의 모든 오버로드는 같은 DB 함수에 대응해야 합니다. 그렇지 않으면 오류입니다(`LQL-3053`). | 구현됨 |
| FN4 | 애너테이션이 없는 호출이나 lambdaQL이 식별할 수 없는 호출은 오류입니다. 조용히 건너뛰지 않습니다. | 구현됨 |
| FN5 | 인자는 [3. 조건](03-where.md)의 값 규칙을 따릅니다. 캡처 값만으로 만든 인자도 바인딩되어 **데이터베이스에서** 평가됩니다. | 구현됨(테스트함) |
| FN6 | 박싱 호출은 건너뜁니다. 반환 타입은 선언된 메서드에서 옵니다. `Double`, `Integer`, `Long`, `BigDecimal`, `String`, `LocalDate`, `LocalDateTime`, `Boolean` 중 하나여야 합니다(`LQL-3055`). | 구현됨(테스트함) |
| FN7 | 컬럼에 대한 함수는 WARN을 냅니다([8.9](#89-warning-functions-on-columns-and-indexes-sargable) 참고). MariaDB에서는 값 쪽의 `NOT DETERMINISTIC` 함수도 WARN(`LQL-5007`). | 구현됨(테스트함). 엄격 모드는 아직 구현되지 않음 |
| FN8 | Hibernate 내장 함수(`COALESCE`, `ROUND`, `SYSDATE`, ...)와 같은 이름은 오류입니다(`LQL-3054`). Hibernate가 조용히 내장 함수를 사용하기 때문입니다. 접두사나 스키마 이름(`PUBLIC.SQ_FN`)을 사용하십시오. | 구현됨(테스트함) |
| FN9 | Kotlin 전용(기본 인자, `inline`, `vararg`). Kotlin 트랙을 참고하십시오. | 기본 인자와 `vararg`는 구현됨. `inline`은 설계됨 |
| FN10 | v0.1에는 스칼라 함수만 있습니다. 사용자 집계 함수와 윈도 함수는 v0.2에 계획되어 있습니다. 인자가 없는 함수는 `fn()`으로 렌더링됩니다. | 스칼라 함수 구현됨 |
| FN11 | 알려진 DB 차이: MariaDB에서 VARCHAR를 반환하는 함수는 문자열 리터럴과 콜레이션 충돌이 날 수 있습니다. `BigDecimal` 결과의 스케일이 다릅니다(MariaDB와 Oracle은 0, H2는 1). boolean 결과에는 Oracle 23ai 이상이 필요합니다. | 프로토타입으로 실측 |

**(테스트됨)** [`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java), 영역 `rules`:

```java
// the body never runs in Java
assertEquals("LQL-4034", assertThrows(DbOnlyException.class, () -> OrderFns.discount(1.0)).code());
// FN2: a body that computes a value is rejected (Java and the database could give different values)
assertEquals("LQL-3052", assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(o -> BadFns.computes(o.getPrice()) > 1)).code());
// FN8: a Hibernate built-in name would silently run the built-in function
assertEquals("LQL-3054", assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(o -> BadFns.builtin(o.getPrice()) > 1)).code());
// a function with its own SQL name must be registered
assertEquals("LQL-3058", assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(o -> BadFns.unregistered(o.getPrice()) > 1)).code());
// the return type must have an HQL type
assertEquals("LQL-3055", assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(o -> BadFns.anything(o.getPrice()) != null)).code());
```

`BadFns`는 경우마다 잘못된 함수를 하나씩 둔 테스트 안의 인터페이스입니다. `price`를 반환하는 본문, 이름 `COALESCE`, 등록하지 않은 `value = "SQ_OTHER"`·`sql = "SQ_FN_DISCOUNT"`, 반환 타입 `Object`의 네 가지입니다.

---

<a id="811-database-support"></a>
## 8.11 데이터베이스 지원

| 기능 | H2 2.3 | MariaDB 10.11 | Oracle 23ai |
|---|---|---|---|
| IN 파라미터 | 예 | 예 | 예 |
| OUT / INOUT | **아니요**(H2 제한) | 예 | 예 |
| 결과 집합 1개 | 예(`ResultSet` 함수) | 예(프로시저 안의 `SELECT`) | 예(`SYS_REFCURSOR` OUT) |
| 결과 집합 여러 개 | 아니요 | 예 | 예(커서 OUT 파라미터 여러 개) |
| OUT 값과 결과 집합 함께 | 아니요 | 예 | 예 |
| 패키지 이름(`PKG.PROC`) | 아니요 | 아니요(`sql_mode=ORACLE` 필요) | 예 |
| 쿼리 안의 DB 함수 | 예(테스트함) | 예(테스트함) | 예(테스트함) |
| DB 함수 단독 호출 | 예(테스트함) | 예(테스트함) | 예(테스트함) |
| lambdaQL의 프로시저 호출 | – | 예(테스트함) | 예(테스트함) |
| boolean 파라미터 / 결과 | – | 예 | 예(23ai 이상) |

v0.1에 계획되지 않은 것: 동적 프로시저 이름을 위한 문자열 빌더(v0.2 후보), Oracle 패키지 프로시저의 오버로드 하나를 골라 호출하는 기능(오버로드 검출은 구현됨, `LQL-4044`), 테이블 반환 함수, 프로시저 결과 페이징.

이 장의 오류는 `[LQL-xxxx]` 코드를 가진 `LambdaQLException`(또는 그 하위 클래스)입니다. 람다를 읽을 때는 `LQL-3052`~`LQL-3063`, 호출을 실행할 때는 `LQL-4034`~`LQL-4039`, `LQL-4043`, `LQL-4044`입니다. 호출 중에 `LQL-5004`, `LQL-5005`, `LQL-5006`을 `INFO` 레벨로 기록할 수도 있습니다. [11. 오류와 언어](../11-errors-and-languages.md)를 참고하십시오.

---

[Java 트랙](README.md) | 이전: [7. 수정, 삽입, 삭제](07-dml.md) | 다음: [9. Java 노트](09-java-notes.md) | [이 페이지의 Kotlin 버전](../kotlin/08-procedures-functions.md)
