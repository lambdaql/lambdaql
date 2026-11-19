# 번역 대기 목록

## 원본 언어

매뉴얼의 원본은 한국어(`docs/manual/ko`)입니다.
영어(`en`), 일본어(`ja`), 중국어(`zh`)는 한국어 원본을 옮긴 번역본입니다.

규칙이 바뀌면 한국어 페이지를 먼저 고칩니다.
번역본에 아직 옮기지 못한 내용은 이 문서에 페이지와 절 단위로 적습니다.

아래 목록의 번역본 페이지에는 맨 위에 안내가 있습니다.
안내는 그 페이지가 한국어 원본보다 오래되었다는 사실과 같은 한국어 페이지의 링크를 담습니다.
번역을 마치면 해당 페이지의 안내와 이 문서의 항목을 함께 지웁니다.

## 대기 항목: 결정 D48(람다 안 비교·메서드의 SQL 의미)

결정 내용은 `docs/design/ROADMAP.md`의 D48 행에 있습니다.
람다 안의 메서드와 비교는 모두 SQL 의미를 따릅니다.

### 규칙 변경

| 항목 | 이전 서술 | 현재 규칙 |
|---|---|---|
| 엔티티 값끼리의 비교(`==`, `equals`, `Objects.equals`, Kotlin `==`) | `is [not] distinct from`, `null == null`은 참 | `=` / `<>`. 어느 쪽이든 NULL이면 맞지 않음. DEBUG `LQL-5009` |
| 엔티티 값과 값의 `!=` | `(col <> :p or col is null)` | `col <> :p1`. NULL 행은 맞지 않음. DEBUG `LQL-5009` |
| NULL 가능 `Boolean`의 부정 | `is distinct from true` | `= false`. NULL은 false가 아님 |
| null 캡처 값과 `==`, `!=`, `equals`, `Objects.equals` | `is null` / `is not null`, 또는 바인딩 오류 | `= :p1` / `<> :p1`에 NULL 바인딩, 맞는 행 없음. 실행 시 WARN `LQL-4045` |
| 연관 경로의 null guard(`o.getCustomer() == null \|\| ...`) | `left join`으로 바꿈(D36 보완) | 항상 `join`(INNER). 연관이 없는 행은 맞지 않음. DEBUG `LQL-5009`. 필요하면 `leftJoin`(D44) |
| 서브쿼리 `!contains` / Kotlin `!in`(Q2) | `not exists (...)` | `x not in (select ...)`. 서브쿼리에 NULL이 있으면 행 없음. DEBUG `LQL-5009` |
| DML의 null guard | null guard가 있으면 `is not null` 검사를 생략 | null guard가 있어도 `e0.customer.id is not null` 검사 유지 |
| `@Convert` 속성과 리터럴 비교 | 서술 없음 | Hibernate 6.6이 리터럴도 변환함(새 절 3.6.5) |
| 캡처 컬렉션의 `!contains` / Kotlin `!in` | `(x is null or x not in (...))` | `x not in (:p1)`. NULL 행은 맞지 않음. DEBUG `LQL-5009` |
| null 수신자 `p.equals(col)` | 예외 `LQL-4011` | `col.equals(p)`와 같은 비교. `col = :p1`에 NULL 바인딩, 맞는 행 없음, 실행 시 WARN `LQL-4045`(예외 없음). 이 형태는 권장하지 않음(엔티티 값을 앞에) |
| null 캡처 값과 크기 비교(언박싱 없는 경우, 예: `String.compareTo`) | 예외 `LQL-4009` | NULL 바인딩, 맞는 행 없음. 실행 시 WARN `LQL-4045` |
| 자동 언박싱된 null 캡처 값(`o.getPrice() > min`, `Double min`, Kotlin `min!!`) | 예외(언박싱 NPE) | 일치형 언박싱(`Double.doubleValue()` 등)은 자동 언박싱으로 간주해 박싱 값 바인딩. `> :p1`(NULL), 맞는 행 없음, 실행 시 **DEBUG** `LQL-4045`. 가드 위치와 관계없이 같은 결과 |

다음 규칙은 바뀌지 않았습니다.

- 캡처 값만 쓰는 부분의 사전 결정(`min == null || o.getPrice() > min`은 필터 없음).
- 캡처 값만으로 된 식은 Java에서 계산한 값(`p.trim()`, `" ABC ".trim()`, 다른 기본형으로 꺼내는 `min.intValue()`). null 수신자는 예외 `LQL-4012`. 일치형 언박싱만 예외(위 표).
- null 캡처 값과의 일반 비교(`==`, `!=`, `equals`, `Objects.equals`)는 WARN `LQL-4045`(자동 언박싱만 DEBUG).
- 캡처 값끼리의 크기 비교에서 한쪽이 null이면 예외 `LQL-4009`.
- 캡처 컬렉션의 null 원소, `NaN`·`Infinity` 캡처 값, 가드 아래의 나눗셈 거부(D13).

### 바뀐 테스트 영역

| 테스트 | 영역 | 쓰는 페이지 |
|---|---|---|
| `SelectQueryTest` | `null-safe` | `java/03-where.md` 3.6.2 |
| `SelectQueryTest` | `null-guard` | `java/03-where.md` 3.10 |
| `SelectQueryTest` | `not-in`(캡처 컬렉션) | `java/03-where.md` 3.9 |
| `SubqueryTest` | `not-in` | `java/05-subquery.md` 5.5 |
| `SqlMeaningTest` | `sql-meaning`(새 영역) | `java/03-where.md` 3.6.3 |
| `ConvertTest` | `convert`(새 영역) | `java/03-where.md` 3.6.5 |

SQL 정답 파일(`expected-sql/`)에서 바뀐 예제는 `null-safe`, `null-guard`, `null-guard-name-null`, `sub-not-in`, `overview-first`, `not-in#2`입니다.

### 새 코드

| 코드 | 수준 | 내용 |
|---|---|---|
| `LQL-4045` | WARN(실행 시) | `=` / `<>`의 캡처 값이 null이라 맞는 행이 없음 |
| `LQL-5009` | DEBUG(조건 문장마다 한 번) | SQL 의미 때문에 Java·Kotlin과 달리 NULL 행이 빠질 수 있음 |

## 세 언어 공통으로 반영된 부분

다음 내용은 `en`, `ja`, `zh`에 모두 반영되어 있습니다.

- 위 SQL 정답 예제의 SQL·HQL 코드 블록(`01-overview.md`, `java/03-where.md`, `kotlin/03-where.md`, `java/05-subquery.md`, `kotlin/05-subquery.md`, `kotlin/09-kotlin-notes.md`).
- Java 테스트 영역 `null-safe`, `null-guard`, `not-in`의 코드(바이트 단위 복사).
- `11-errors-and-languages.md`의 코드 범위(4xxx는 `LQL-4045`까지, 5xxx는 `LQL-5001`~`LQL-5009`)와 `LQL-4045`, `LQL-5009`의 코드 표·로그 표 행.

## 영어(en)

D48의 두 단계(엔티티 값의 SQL 의미, 그리고 null 캡처 값·null guard·`NOT IN`)를 모두 반영했습니다.
한국어 원본과 문장·예제를 대조하는 작업이 남았습니다.

대상 페이지는 아래 "페이지별 남은 작업"의 17개 페이지와 같습니다.
영어 페이지는 한국어 원본보다 먼저 고쳤으므로, 한국어 원본을 기준으로 표현과 예제를 맞춥니다.

## 일본어(ja)·중국어(zh)

D48의 1단계(엔티티 값의 SQL 의미)는 아래 페이지에 반영되어 있습니다.
`01-overview.md`, `11-errors-and-languages.md`, `java/03-where.md`, `kotlin/03-where.md`, `java/05-subquery.md`, `kotlin/05-subquery.md`, `java/09-java-notes.md`, `kotlin/09-kotlin-notes.md`, `java/10-rules-and-limits.md`, `kotlin/10-rules-and-limits.md`.

1단계에는 `sql-meaning` 영역, 3.6.5 `@Convert` 절, `LQL-5009` 서술이 포함됩니다.
다만 1단계에서 적은 "null 캡처 값은 `is null`" 서술은 2단계 규칙과 맞지 않으므로 고쳐야 합니다.

2단계(null 캡처 값, null guard, `NOT IN`)의 본문 서술은 반영되지 않았습니다.
코드 블록과 테스트 영역만 갱신되어 있어, 주변 문장과 코드가 서로 다른 규칙을 설명하는 곳이 있습니다.

### 페이지별 남은 작업

| 페이지 | 남은 절과 내용 |
|---|---|
| `01-overview.md` | 첫 예제 아래 문장(`left join`인 이유 → INNER JOIN, `leftJoin` 안내). "람다는 쿼리" 표의 `==` 행(null 캡처 값), 연관 경로 행(null guard). SARGable 표의 `!contains` → `not exists` 행 교체. D48 요약 문장 |
| `11-errors-and-languages.md` | "null 캡처 값" 절의 안내 문장과 Java·Kotlin 표(현재 `is null`로 적힘 → `= :p1`, 맞는 행 없음, `LQL-4045`) |
| `java/02-getting-started.md`, `kotlin/02-getting-started.md` | 연관 경로 절의 null guard 문장(`left join` → INNER JOIN 유지, `leftJoin` 안내) |
| `java/03-where.md` | 3.6 원칙 2(캡처 값도 SQL 의미, `LQL-4045`). 3.6.1 R2·R5 행. 3.6.2 예제 주석과 캡처 값 문장. 3.6.3 `Objects.equals` 주석. 3.6.4 표와 이유. 3.10 null guard 설명과 그 뒤 목록. 마지막 "지원하지 않는 형태" 표의 null 캡처 값 행 |
| `kotlin/03-where.md` | 3.6 원칙 개수(세 가지)와 원칙 3. 3.6.1 R11 행. 3.6.2 예제 주석. 3.6.4 표와 이유. 3.10 null guard 설명, 목록, `?.` 경고 상자 |
| `java/05-subquery.md`, `kotlin/05-subquery.md` | 상태 문구의 `!contains`·`!in`. 5.1 표의 `!contains` 행. 5.5 제목·본문·이유(앵커 `55-not-in-is-written-as-not-exists` 유지). 5.10 연관 경로 규칙과 캡처 null 문구. Q2 행 |
| `java/07-dml.md`, `kotlin/07-dml.md` | DML 경로 절의 null guard 문장(`is not null` 검사 유지) |
| `java/07-4-delete.md`, `kotlin/07-4-delete.md` | 고객이 없는 주문 삭제 안내(null guard 대신 `o.getCustomer() == null`로 별도 삭제) |
| `java/09-java-notes.md` | 9.3 도입 문장, 캡처 값 표와 `!=` 문장. 9.4 표의 `==`, `equals`, `Objects.equals` 행. 9.5 도입 문장과 `Objects.equals` 행 |
| `kotlin/09-kotlin-notes.md` | 별칭(`j1`) 설명. D20·D36 표 행. 9.4 `areEqual` 행과 설명. `?.` 절 설명과 예제 주석. `!in` 서브쿼리 행. 캡처 null 문장. 마지막 표의 `?.` 행 |
| `java/10-rules-and-limits.md`, `kotlin/10-rules-and-limits.md` | 10.1 null guard 문장. 10.2 `NOT IN` 문장과 캡처 null 문장. 10.3 표의 캡처 null 행, null guard 행, 서브쿼리 `!contains` 행. 캡처 null 표. R2·R11, R24. G3·G4 |
| `kotlin/README.md` | 연관 경로 예시 표의 null guard 문장 |

### 3단계: 캡처 컬렉션 `NOT IN`, null 수신자 (en·ja·zh 모두 미반영)

| 페이지 | 남은 절과 내용 |
|---|---|
| `java/03-where.md`, `kotlin/03-where.md` | 3.6 원칙 2(캡처 값만으로 된 식은 Java 계산, `LQL-4012`, 단 일치형 언박싱은 자동으로 간주). 3.6.4 표의 `p.equals` 행(예외 없음)과 그 아래 "`p.equals` 형태 비권장" 문단, 언박싱 비교 행(일치형: 행 없음·DEBUG, 불일치형: `LQL-4012`), 가드 위치 문장. 3.9 `!contains`/`!in` 서술과 `not-in#2` SQL. 3.13의 "수신 객체인 null 캡처 값" 행을 "언박싱하는 null 캡처 값" 행으로 교체 |
| `java/09-java-notes.md`, `kotlin/09-kotlin-notes.md` | 9.4 `p.equals`·`compareTo` 행과 equals 항목. Kotlin 9.6 `!in` 행과 "유일한 예외" 문단 |
| `java/10-rules-and-limits.md`, `kotlin/10-rules-and-limits.md` | 10.3 캡처 컬렉션 `!contains` 행. 캡처 null 표(`p.equals`와 일치형 언박싱·`!!`는 허용, 불일치형 언박싱·메서드 호출은 `LQL-4012`). 가드 위치 행. Kotlin R19 |
| `11-errors-and-languages.md` | `name.equals(...)` 행을 "실행됨"으로, `name.trim()` 행(`LQL-4012`) 추가, 크기 비교·`minPrice!!` 행은 실행됨(행 없음, DEBUG `LQL-4045`), `LQL-4045` 수준 설명(WARN, 자동 언박싱은 DEBUG) |
| `java/04-order-paging.md`, `kotlin/04-order-paging.md` | "대부분은 inner join" → "이 조인은 inner join" |

영어판에서 한국어판과 다르게 남은 문장(에이전트 확인):
- `en/java/03` R4 행의 "`p == null`: exception".
- `en/java/03`, `en/kotlin/03`의 "null guard가 left join으로 만든 경우가 아니면 inner join".
- `en/java/09`의 "wanted = null -> status가 NULL인 행" 주석과 "equals와 ==는 NULL에서만 다름" 문장.
- `en/java/10` 캡처 null 표의 "No" 행에 남은 "argument of col.equals(p)". `en/kotlin/09` D22 행과 `en/kotlin/10` 10.1 항목 2의 "nullable 두 값의 ==".

## 대기 항목: 결정 D48 후속(B3 `@Lob`, A4 필수 연관)

| 항목 | 내용 | 테스트 영역 |
|---|---|---|
| A4 필수 연관(`optional = false`) | 정렬·select 경로가 INNER JOIN. 모든 연관 단계가 필수이고 값이 NULL 불가면 정렬의 `nulls first/last` 생략. 다른 연관은 LEFT JOIN 유지(S8, J1) | `MappingRulesTest` `required-association` |
| A5 유일 정렬 키 | 정렬 키가 유일하고 NULL이 아닌 열(`@Column(unique = true)`, `@NaturalId`)이면 페이징 보조 정렬(S9) `e0.id asc` 생략 | `MappingRulesTest` `unique-key` |
| B2 STRING 열거형 정렬 | `@Enumerated(EnumType.STRING)` 열로 정렬하면 DB는 이름 순, Java는 선언 순. 쿼리는 그대로, DEBUG `LQL-5011` | `MappingRulesTest` `enum-string-sort` |
| B7 `@SQLRestriction`·`@SoftDelete` | Hibernate가 조회·count·서브쿼리·조인 `on`·파생 테이블·벌크 UPDATE/DELETE에 조건을 붙임. `@SoftDelete`의 DELETE는 `update … set deleted = 1`(MariaDB·Oracle 실측) | 없음(실측만) |
| B3 `@Lob` | 정렬·distinct·group by에 쓰면 Oracle은 ORA-22848/22849(실측), H2·MariaDB는 동작. 쿼리는 그대로 두고 DEBUG `LQL-5010` | `MappingRulesTest` `lob` |
| B6 값 객체 비교 | `@Embedded` 값 객체 전체의 같음 비교(`equals`, `Objects.equals`, Kotlin `==`, `!=`)를 컬럼별 비교로 전개. `== null`은 모든 컬럼 `is null`의 `and`. 각 컬럼은 SQL 의미. null 캡처 객체는 컬럼마다 NULL 바인딩과 WARN `LQL-4045`. 그 밖의 비교는 `LQL-3012`(문구 개정) | `MappingRulesTest` `embedded-compare`, `KotlinQueryTest` `embedded-compare` |

대상 페이지: `java/04`·`kotlin/04`(S8), `java/06`·`kotlin/06`(J1, distinct·group by), `java/10`·`kotlin/10`(R24·S8·J1 행), `11-errors-and-languages.md`(5xxx 범위 `LQL-5011`까지, 코드 표·로그 표).
B6 대상 페이지: `java/03`·`kotlin/03`(임베디드 경로 절, 같음 비교 규칙), `java/09`·`kotlin/09`(equals·`==` 표), `java/10`·`kotlin/10`(규칙 표), `11-errors-and-languages.md`(`LQL-3012` 문구).

### 참고 사항

- `ja/kotlin/09-kotlin-notes.md`의 명시 앵커를 `plain--and-`로 바꿨습니다. 이 앵커를 가리키는 링크는 없습니다.
- 일본어·중국어 `kotlin/03-where.md`는 원칙이 세 가지인데 "두 가지"로 적혀 있습니다.

## 대기 항목: U17 DELETE의 자기 테이블 서브쿼리

| 항목 | 내용 | 테스트 영역 |
|---|---|---|
| U17 구현 | DELETE의 서브쿼리(서브쿼리 안 조인, 상관 스칼라 서브쿼리 포함)가 대상과 같은 엔티티를 읽으면 `delete from X d0 where d0.id in (select t.id from (select e0.id as id from X e0 where …) t)`로 렌더링. MariaDB 오류 1093 해소(세 DB 실측). UPDATE는 해당 없음. 복합 키는 감싸지 않음 | `DmlQueryTest` `delete-self-subquery` |

대상 페이지: `java/07-dml.md`·`kotlin/07-dml.md`("아직 구현되지 않았습니다" 문장에서 U17 제외, U17 행), `java/07-4-delete.md`·`kotlin/07-4-delete.md`(새 예제), `java/10-rules-and-limits.md`·`kotlin/10-rules-and-limits.md`(1093 행의 "아직 구현되지 않음" 제거).

## 대기 항목: 값 쿼리 `first()`의 보조 정렬

| 항목 | 내용 | 테스트 |
|---|---|---|
| S9 보완 | 값 쿼리(`select(...)` 뒤)의 `first()`도 id 보조 정렬을 더함. 정렬 키 값이 같은 행이 여러 개이면 `id`가 가장 작은 행의 값 | `ProjectionTest` `pagingAndFirst`(영역 없음) |

대상 페이지: `java/06-select-group.md`·`kotlin/06-select-group.md`의 값 쿼리 페이징 문단(규칙 S9 문장 다음 줄).

## 대기 항목: D49 대입 표기 개정(`Dml.value`, `sub`)

| 항목 | 이전 서술 | 현재 규칙 |
|---|---|---|
| setter 없는 엔티티의 대입 | `(o, by) -> by.set(열, 값)`, Kotlin `by[열] = 값` | 정적 함수 `Dml.value(열, 값)`, 1인자 람다. setter와 한 람다 안에서 섞을 수 있음 |
| 대입 람다의 두 번째 인자 | `by`(대입 도구) | 서브쿼리 시작점 `sub`: `set((o, sub) -> …)` |
| insert … select | `(a, o)`만 | `(a, o, sub)` 추가, `where((o, sub) -> …)` 추가 |
| 오류 코드 | `LQL-3039`(by.set 대상), `LQL-3040`(by.set만) | `LQL-3039`는 `Dml.value` 대상 오류, `LQL-3040` 삭제, `LQL-3037` 문구에 `Dml.value` 추가 |
| 이름 가림 | 서술 없음 | `value()` 메서드가 있는 클래스 안에서는 `Dml.value(…)`로 적음 |

테스트 영역: `DmlQueryTest` `update-value`(이전 `update-by`)·`update-sub`·`insert-select-sub`, `AssignmentReaderTest` `read-value`(이전 `read-by`), `KotlinQueryTest` `update-value`(이전 `update-by`)·`update-sub`. SQL 정답 예제 이름 `update-by` → `update-value`.

대상 페이지: `README.md`, `java/README.md`, `kotlin/README.md`, `java/07-dml.md`·`kotlin/07-dml.md`, `kotlin/07-2-update.md`(와 `java/07-2-update.md`), `java/07-5-insert.md`·`kotlin/07-5-insert.md`, `java/09-java-notes.md`·`kotlin/09-kotlin-notes.md`, `java/10-rules-and-limits.md`·`kotlin/10-rules-and-limits.md`, `11-errors-and-languages.md`(코드 표).

## 대기 항목: U20 `Dml.value` 타입 대조

| 항목 | 내용 | 테스트 영역 |
|---|---|---|
| U20 타입 대조 구현 | `value(열, 값)`의 값 타입을 분석 시점에 열 타입과 대조, 어긋나면 `LQL-3065`. 숫자는 숫자 열에 허용, 소수→정수 열은 거부, 타입을 알 수 없는 값은 대조하지 않음 | `AssignmentReaderTest` `value-type` |

대상 페이지: `java/07-dml.md`·`kotlin/07-dml.md`(7.3 규칙 목록, U20 행, "아직 구현되지 않았습니다" 문장), `java/09-java-notes.md`(제네릭 `value` 행), `11-errors-and-languages.md`(3xxx 범위 `LQL-3065`까지, 코드 표 행).

## 대기 항목: 프로시저 인자 타입 대조(PR2)

| 항목 | 내용 | 테스트 영역 |
|---|---|---|
| PR2 타입 대조 구현 | 첫 호출 때 인자의 Java 타입과 DB 파라미터 타입의 종류(숫자, 문자, 날짜·시간, 불리언, 이진)를 대조, 다르면 `LQL-4036`. 불리언은 숫자 파라미터에도 허용, enum·객체·커서는 건너뜀 | `ProcedureTest` `type-check`, 스텁 `DiffProcs`에 `text` 메서드 추가(`names` 영역) |

대상 페이지: `README.md`, `java/README.md`, `kotlin/README.md`(기능 표의 "타입 대조: 아직 구현되지 않음"), `java/08-procedures-functions.md`(8.4 스텁 코드, "인자 타입 대조" 문단, PR2 행), `kotlin/08-procedures-functions.md`(PR2 행).

## 대기 항목: insert values·upsert의 서브쿼리(D49 보완)

| 항목 | 내용 | 테스트 영역 |
|---|---|---|
| insert values·upsert 서브쿼리 | `values((a, sub) -> …)`, upsert `values((a, sub) -> …)`, `onConflict(…).update((a, sub) -> …)`. 서브쿼리는 캡처 값만 읽음. 세 DB 실측 | `DmlQueryTest` `insert-values-sub`, `KotlinQueryTest` `insert-values-sub` |
| 목록 형태 서브쿼리 | `values(list, (a, l, sub) -> …)`(insert·upsert), 서브쿼리는 원소 값을 읽을 수 있음. 세 DB 실측 | `DmlQueryTest` `insert-list-sub` |

대상 페이지: `java/07-dml.md`·`kotlin/07-dml.md`(미구현 목록과 7.3 서브쿼리 절), `java/07-5-insert.md`·`kotlin/07-5-insert.md`(values·upsert 절).

## 대기 항목: D50 SARGable strict 모드와 열 산술, D48 B8 정수 열 비교

| 항목 | 내용 | 테스트 영역 |
|---|---|---|
| SARGable 모드 | `SargableMode` OFF·WARN(기본)·STRICT, `SargablePolicy.register(emf, mode)`, Spring `lambdaql.sargable.mode`. STRICT면 `NonSargableException`(`LQL-3066`) | `FunctionTest` `sargable-strict` |
| 열 산술 | 열 하나와 값의 `+ - * /`는 WARN `LQL-5012`(STRICT 오류). 열끼리의 산술과 `%`는 DEBUG `LQL-5013`. 옮길 때는 소수로 나눔(`n / 2.0`) | `FunctionTest` `sargable-strict` |
| 정수 열과 소수 값(B8) | 적은 값 그대로 비교: 상수는 리터럴 `cnt >= 2.5`, 캡처 값은 BigDecimal 바인딩(`cnt >= ?`에 2.5), IN 목록은 전체를 BigDecimal로. 이전에는 2.5를 2로 잘라 결과가 틀렸음 | `SqlMeaningTest` `whole-number` |

대상 페이지: `java/08-procedures-functions.md`·`kotlin/08-procedures-functions.md`(SARGable 절), `java/03-where.md`·`kotlin/03-where.md`(3.4 비교: "컬럼에 대한 산술과 인덱스", "정수 컬럼과 소수 값" 문단), `java/12-spring-data-jpa.md`·`kotlin/12-spring-data-jpa.md`("SARGable 모드 프로퍼티" 문단), `11-errors-and-languages.md`(3xxx 범위 `LQL-3066`, 5xxx 범위 `LQL-5013`까지, 코드 표·로그 표), `java/10-rules-and-limits.md`·`kotlin/10-rules-and-limits.md`.

## 대기 항목: D51 조인 확장(distinct, 파생 테이블·CTE 조인)

| 항목 | 내용 | 테스트 영역 |
|---|---|---|
| 조인 결과 `distinct()` | `select distinct e0`, 정렬 키는 루트 열만(J7), 페이징 보조 정렬은 루트 id만. `exists` 서브쿼리를 먼저 권장 | `JoinQueryTest` `join-distinct`, `KotlinQueryTest` `join-distinct` |
| 파생 테이블·CTE 조인 | `join`·`leftJoin(cte, …)`, `join`·`leftJoin(qb.from(…)…select(…), …)`. CTE 여러 개는 `t`, `t2`. 파생 행의 `x == null`은 `LQL-3067` | `JoinQueryTest` `join-cte`·`join-derived`, `KotlinQueryTest` `join-cte` |
| 서브쿼리 안 조인 | `sub.from(A).join(B, (a, b) -> …)`·`leftJoin` → `where`·`exists`·`count`·`select`·집계가 두 행을 받음. 엔티티 2개까지 | `SubqueryTest` `sub-join`, `KotlinQueryTest` `sub-join` |
| CTE 재사용 | 같은 `Cte` 객체를 한 문장에서 여러 번 읽으면 정의는 한 번, 같은 이름으로 읽음. 다른 `Cte` 객체는 `t`, `t2` | `JoinQueryTest` `cte-reuse` |

대상 페이지: 명시적 조인을 다루는 페이지(조인 절), 파생 테이블·CTE 페이지, `11-errors-and-languages.md`(3xxx 범위 `LQL-3067`까지), `java/10-rules-and-limits.md`·`kotlin/10-rules-and-limits.md`(J 규칙).

서브쿼리 안 조인 대상 페이지: `java/05-subquery.md`·`kotlin/05-subquery.md`(새 절, 미구현 목록), `java/06-select-group.md`·`kotlin/06-select-group.md`("서브쿼리 안의 조인: 이후" 문구).

## 대기 항목: 시작하기 페이지의 노트 정리

| 항목 | 내용 |
|---|---|
| 상태 노트 | 기능별 상태 목록을 지우고 세 줄로 줄임: 배포 전이라 소스에서 빌드, 이 장의 기능은 세 DB에서 테스트, 장별 현황은 트랙 README(Java `#status`, Kotlin `#contents`) |
| 배포 경고 | "Maven Central에 배포되지 않음" 중복 문장 삭제, 버전 `1.0.0`이 바뀔 수 있다는 안내만 남김 |

대상 페이지: `java/02-getting-started.md`·`kotlin/02-getting-started.md`.

