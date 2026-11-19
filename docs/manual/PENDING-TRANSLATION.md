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

## 대기 항목(Boot 4 브랜치 `feature/boot4`)

아래 페이지의 번역본(en, ja, zh)에는 맨 위에 낡음 안내를 두었습니다.

### 1. U9 연관 경로 서브쿼리의 FK 표기(2026-11-19)

- 페이지: `java/07-dml.md`, `java/07-2-update.md`, `java/07-4-delete.md`, `kotlin/07-dml.md`, `kotlin/07-2-update.md`, `kotlin/07-4-delete.md`
- 내용: 서브쿼리 안의 바깥 행 FK 참조 `s1.id = e0.customer.id`를 `s1.id = fk(e0.customer)`로 바꿈(HQL 예제와 주석). 조건의 `e0.customer.id is not null`은 그대로임
- 옮길 방법: 코드 블록의 HQL 문자열만 바꾸면 됨. 본문 산문은 바뀌지 않음

### 2. Oracle 함수 기반 인덱스의 숫자 변환(2026-11-19)

- 페이지: `java/08-procedures-functions.md`, `kotlin/08-procedures-functions.md`의 SARGable 표 ① 행
- 내용: 열 타입이 함수 인자 타입과 달라 Oracle이 넣은 숫자 변환(`FN(TO_NUMBER(COLUMN))` 등)도 같은 식으로 본다는 문장 추가

### 3. 클래스 파일 읽기: Java 24 이상은 `java.lang.classfile`(2026-11-19, D52)

- 페이지와 위치
  - `01-overview.md`: 처리 단계 표의 "2. 바이트코드 읽기" 행
  - `java/02-getting-started.md`, `kotlin/02-getting-started.md`: "알아 둘 점" 표의 ASM 행
  - `java/10-rules-and-limits.md`, `kotlin/10-rules-and-limits.md`: "지원하지 않음" 표의 ASM 행
  - `java/09-java-notes.md`: 9.12 표의 ASM 행(행 이름이 "클래스 파일 읽기(ASM)"으로 바뀜)
- 내용: ASM 버전 제약은 Java 21~23에서 실행할 때만 해당한다. Java 24 이상에서는 실행 중인 JDK의 `java.lang.classfile`로 읽어 ASM 버전과 관계가 없다(Multi-Release JAR)

## 한국어 원본 다듬기(2026-11-19 네 언어에 반영 완료)

번역 중 발견한 문구 문제 9건을 한국어 원본과 영어, 일본어, 중국어에 함께 고쳤습니다. 남은 대기 항목은 없습니다.

고치지 않은 것과 이유는 다음과 같습니다.

- 08장 `pass_procedure_paramater_names`: Hibernate 6.6.5의 설정 이름(`hibernate.query.pass_procedure_paramater_names`, `QuerySettings`)이 이 철자여서 문서를 그대로 둠
- README 첫 앵커 `lambdaql-user-manual-english`: 링크가 쓸 수 있어 이름을 바꾸지 않음
- 영어 번역본: 영어 제목은 `Insert ... select`, `Group by`, `Having`처럼 문장형 대문자이고 Java와 Kotlin이 이미 같아 제목을 바꾸지 않음
- 3.5, 6.6, `BigDecimal` 제목의 앵커: 번역본은 제목 자체가 같은 id를 만들어 명시 앵커를 넣지 않음(한국어 원본에만 명시 앵커 추가). 일본어와 중국어 Kotlin 06의 6.5는 제목을 Java에 맞추면서 id가 달라져 명시 앵커를 추가함
