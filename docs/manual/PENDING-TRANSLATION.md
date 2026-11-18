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
| B3 `@Lob` | 정렬·distinct·group by에 쓰면 Oracle은 ORA-22848/22849(실측), H2·MariaDB는 동작. 쿼리는 그대로 두고 DEBUG `LQL-5010` | `MappingRulesTest` `lob` |

대상 페이지: `java/04`·`kotlin/04`(S8), `java/06`·`kotlin/06`(J1, distinct·group by), `java/10`·`kotlin/10`(R24·S8·J1 행), `11-errors-and-languages.md`(5xxx 범위 `LQL-5010`까지, 코드 표·로그 표).

### 참고 사항

- `ja/kotlin/09-kotlin-notes.md`의 명시 앵커를 `plain--and-`로 바꿨습니다. 이 앵커를 가리키는 링크는 없습니다.
- 일본어·중국어 `kotlin/03-where.md`는 원칙이 세 가지인데 "두 가지"로 적혀 있습니다.
