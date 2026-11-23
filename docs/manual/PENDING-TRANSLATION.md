# 번역 대기 목록

## 원본 언어

매뉴얼의 원본은 한국어(`docs/manual/ko`)입니다.
영어(`en`), 일본어(`ja`), 중국어(`zh`)는 한국어 원본을 옮긴 번역본입니다.

규칙이 바뀌면 한국어 페이지를 먼저 고칩니다.
번역본에 아직 옮기지 못한 내용은 이 문서에 페이지와 절 단위로 적습니다.

이 문서에 적힌 번역본 페이지에는 맨 위에 안내를 둡니다.
안내는 그 페이지가 한국어 원본보다 오래되었다는 사실과 같은 한국어 페이지의 링크를 담습니다.
번역을 마치면 해당 페이지의 안내와 이 문서의 항목을 함께 지웁니다.

## 진행 현황

| 언어 | 상태 |
|---|---|
| 영어(`en`) | 2026-11-19 반영 완료(커밋 `c7157d3`) |
| 일본어(`ja`) | 2026-11-19 반영 완료(커밋 `256cb57`) |
| 중국어(`zh`) | 2026-11-19 반영 완료(이 커밋) |

세 번역본 모두 33쪽 전체를 한국어 원본에 맞췄습니다. 검사는 `python3 .claude/tools/docs/check_translation.py <언어>`(문제 0)입니다.
이전 대기 항목(D48~D51 등)은 모두 반영되어 지웠습니다. 내용은 git 기록에 있습니다.
앞으로 한국어 원본이 바뀌면 이 문서에 다시 항목을 적습니다.

## 대기 항목

아래 페이지의 번역본(en, ja, zh)에는 맨 위에 낡음 안내를 두었습니다.
코드 블록 안의 주석은 언어마다 번역되어 있으므로, 한국어 블록을 통째로 복사하지 말고 바뀐 줄만 옮깁니다.

### 1. D54: Kotlin `?.`·`!!`에 따른 where 조인 구분(2026-11-19)

- `01-overview.md`: 첫 예제의 "HQL (두 언어 모두 동일)" 절을 Java HQL과 Kotlin HQL(`left join`)로 나눔(앵커 `hql-the-same-for-both`는 유지). 아래 설명 문단과 "lambdaQL이 정한 의미" Kotlin 표의 `it.customer?.name` 행. Java 표는 그대로
- `kotlin/03-where.md` 3.10: `?.`와 `!!`의 조인 구분 표, 영역 `safe-call-and`·`safe-call-or`·`safe-call-null`·`not-null-assert`·`mixed-warn`과 SQL 블록(`safe-call-or`, `safe-call-null`), null 가드 설명과 HQL, 경로 안 섞임 규칙. 예전의 "`?.`를 써도 고객 없는 주문은 돌아오지 않습니다" 경고와 "`\|\|`의 다른 부분도 inner join" 문장은 지움
- `kotlin/07-2-update.md`: 영역 `update-safe-call`과 SQL 블록, 설명 문단
- `kotlin/07-4-delete.md`: 예제를 `it.customer!!.name == null`로 바꾸고 `?.`로 쓰면 고객 없는 주문도 삭제된다는 문단
- `kotlin/07-dml.md`: DML 가드 설명 문단, U9 표 행
- `kotlin/02-getting-started.md`(다른 엔티티로 가는 경로), `kotlin/04-order-paging.md`(정렬이 where 조인을 쓰는 문장), `kotlin/09-kotlin-notes.md`(예제 읽는 법, D20·D36·D54·D23 표, 요약 표, `?.` 설명과 예제, Kotlin과의 차이 표 두 행), `kotlin/10-rules-and-limits.md`(1번 원칙, 경로 표 네 행, R24), `kotlin/README.md`(`it.customer?.name == "Kim"` 행)
- `11-errors-and-languages.md`: 5xxx 범위(`LQL-5014`까지), 메시지 표에 `LQL-5014`, 설명 표에 `LQL-5014`

### 2. PostgreSQL·MySQL 반영으로 바뀐 메시지와 설명(2026-11-19)

- `11-errors-and-languages.md`: 메시지 표의 `LQL-5001`(PostgreSQL 함수 기반 인덱스), `LQL-5007`(MySQL `NOT DETERMINISTIC`, PostgreSQL `VOLATILE`), 설명 표의 `LQL-5001`·`LQL-5007`
- `java/07-6-upsert.md`, `kotlin/07-6-upsert.md`: 영역 `upsert`의 주석 한 줄(`MariaDB and MySQL count an updated row as 2, the others as 1`)

## Boot 4 브랜치 반영(2026-11-19, `feature/boot4`)

다음 변경을 한국어 원본과 영어, 일본어, 중국어에 함께 반영했습니다. 남은 대기 항목은 없습니다.

1. U9 연관 경로 서브쿼리의 FK 표기 `fk(e0.customer)`
2. Oracle 함수 기반 인덱스의 숫자 변환(`FN(TO_NUMBER(COLUMN))`)
3. 클래스 파일 읽기: Java 24 이상은 `java.lang.classfile`(D52)
4. B8: 정수 컬럼과 소수 캡처 값은 소수 리터럴(`2.5BD`)
5. 매뉴얼을 Boot 4판 기준으로(D53): DB별 SQL 블록, 버전 표기, 빌드 안내, 미측정 표기, boolean과 파생 테이블 별칭 설명

코드 블록 안의 주석은 언어마다 번역되어 있습니다. 한국어 블록을 그대로 복사하면 번역한 주석이 지워지므로, 한글 주석이 있는 블록은 바뀐 줄만 옮깁니다.

## 한국어 원본 다듬기(2026-11-19 네 언어에 반영 완료)

번역 중 발견한 문구 문제 9건을 한국어 원본과 영어, 일본어, 중국어에 함께 고쳤습니다. 남은 대기 항목은 없습니다.

고치지 않은 것과 이유는 다음과 같습니다.

- 08장 `pass_procedure_paramater_names`: Hibernate 6.6.5의 설정 이름(`hibernate.query.pass_procedure_paramater_names`, `QuerySettings`)이 이 철자여서 문서를 그대로 둠
- README 첫 앵커 `lambdaql-user-manual-english`: 링크가 쓸 수 있어 이름을 바꾸지 않음
- 영어 번역본: 영어 제목은 `Insert ... select`, `Group by`, `Having`처럼 문장형 대문자이고 Java와 Kotlin이 이미 같아 제목을 바꾸지 않음
- 3.5, 6.6, `BigDecimal` 제목의 앵커: 번역본은 제목 자체가 같은 id를 만들어 명시 앵커를 넣지 않음(한국어 원본에만 명시 앵커 추가). 일본어와 중국어 Kotlin 06의 6.5는 제목을 Java에 맞추면서 id가 달라져 명시 앵커를 추가함
