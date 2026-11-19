# DEVNOTE — lambdaQL 개발 인계 노트

> 최종 갱신: 2026-11-19 · 브랜치 `feature/engine-core`
>
> 이 문서는 작업을 이어받는 개발자와 에이전트를 위한 요약이다. 근거와 상세는 다음 문서에 있다.
>
> - [`docs/design/ROADMAP.md`](docs/design/ROADMAP.md): 결정 사항(D1~D51), 마일스톤, 규칙 표(U, J, S, Q, PR, FN 등), 세 DB 실측 기록
> - [`docs/design/KOTLIN.md`](docs/design/KOTLIN.md): Kotlin 지원 설계(바이트코드 패턴, 제약 KC1~KC21)
> - [`docs/manual/ko`](docs/manual/ko): 사용자 매뉴얼. 한국어가 원본이고 `en`·`ja`·`zh`는 번역이다.
> - [`docs/manual/PENDING-TRANSLATION.md`](docs/manual/PENDING-TRANSLATION.md): 번역본에 아직 옮기지 않은 변경 목록
>
> 문서와 코드가 다르면 ROADMAP의 결정 사항이 우선이다.

---

## 1. 이 프로젝트가 하는 일

Java·Kotlin의 Serializable 람다를 **실행하지 않고 바이트코드(ASM)로 분석**해 Hibernate 6 HQL로 옮기는 쿼리 라이브러리다.

```java
double minPrice = 100;
qb.from(Order.class)
  .where(o -> o.getStatus() == 1 && o.getPrice() > minPrice)
  .toList();
// → select e0 from Order e0 where e0.status = 1 and e0.price > :p1   (SelectQueryTest)
```

람다에 적은 상수는 리터럴, 캡처 값은 바인드 파라미터가 된다(D37). 별칭은 루트 `e0`, `e1`…, 경로 조인 `j1`…, 서브쿼리 `s1`…, 파생 테이블 안쪽 `d0`이다.

처리 순서: `SerializedLambda` → `Decoder`(ASM) → `ConditionReducer` → `Normalizer`(열·캡처 값·행 모델) → `NegationMinimizer` → `HqlRenderer` → Hibernate.

---

## 2. 현재 상태 (2026-11-19)

| 모듈 | 아티팩트 | 내용 |
|---|---|---|
| `query` | `lambdaql-core` | 분석 엔진과 `QueryBuilder`. Spring 의존 없음 |
| `spring-data-jpa` | `lambdaql-spring-data-jpa` | 리포지토리 실행기 `LambdaQlExecutor<T>`, Spring Boot 자동 구성 |
| `spring-boot-starter` | `lambdaql-spring-boot-starter` | 의존성 묶음 |

구현된 범위(세부는 9절):

- 조회: `where`와 결과 함수(`toList`, `toSet`, `first`, `one`, `count`, `exists`), 정렬·페이징과 외부 정렬 정책(D43)
- 서브쿼리(`sub`, 서브쿼리 안 조인 포함), `select`·`groupBy`·`having`·`Agg`, 명시적 조인(D44, D45, D51), 파생 테이블·CTE(M8-2, CTE 재사용)
- DML: update·insert·delete·upsert, `limit`·`executeOne()`(D41), 대입 표기 setter와 `Dml.value`(D49), 값 안의 서브쿼리
- 저장 프로시저와 DB 함수(M11), SARGable 경고와 모드(PR8, D50)
- 메시지: `[LQL-xxxx]` 코드, 영어·일본어·중국어·한국어(D33)
- 분석 캐시: EntityManagerFactory별 공유(D47)
- Spring Data JPA 모듈(M12): Spring Boot 3.4.2로 테스트

테스트: 세 DB(H2 2.3.230, MariaDB 10.11.18, Oracle Free 23.26.3)에서 모듈 전체 241개 통과(2026-11-19 실측, 건너뜀은 DB별 H2 16·MariaDB 10·Oracle 9). Kotlin 람다 테스트는 `query/src/test/kotlin`에 별도 소스 세트로 있다.

아직 Maven Central에 배포하지 않았다. 버전 `1.0.0`은 `gradle.properties`의 값이며 첫 릴리스 전에 바뀔 수 있다.

---

## 3. 빌드·개발 환경

| 항목 | 값 / 주의 |
|---|---|
| JDK | **21**(`jvm.version=21`) |
| Gradle | wrapper **8.10**. Java 23 이하에서만 실행된다. 데몬 JVM은 `gradle/gradle-daemon-jvm.properties`(`toolchainVersion=21`)로 고정 |
| IntelliJ | Gradle JVM이 최신 JDK로 잡히면 "Gradle 8.10 is incompatible" 오류. Gradle JVM을 21로 지정 |
| 의존성 | Spring Boot BOM 3.4.2(Hibernate ORM 6.6.5), Kotlin 2.3.21(테스트만), ASM 9.11.1(`api`) |
| ASM | 새 JDK 릴리스마다 최신 ASM으로 갱신한다. 구버전은 새 클래스 파일 버전을 읽지 못한다(D9) |
| 테스트 DB | `application-test.yaml`(H2, `ddl-auto: create-drop`). `-Dlql.db=mariadb|oracle`이면 `lql-db-<이름>.yaml`을 `spring.config.import`로 읽는다. 테이블은 `SqPrefixNamingStrategy`로 `SQ_` 접두가 붙는다 |

자주 쓰는 명령:

```bash
./gradlew -q test -Dlql.db=h2                      # 전체(모든 모듈), DB: h2 | mariadb | oracle
./gradlew -q :query:test --tests '*JoinQueryTest*' -Dlql.db=oracle
./gradlew -q :query:test --tests '*SqlByDatabaseTest*' -Dlql.db=mariadb -Dlql.sql.update=true   # SQL 정답 파일 갱신
```

결과는 `*/build/test-results/test/*.xml`에서 읽는다. SQL 정답 파일은 `query/src/test/resources/expected-sql/{h2,mariadb,oracle}.sql`이고, 매뉴얼의 DB별 SQL은 여기서 옮긴다. SQL이 바뀌면 테스트가 실패하므로 확인한 뒤 DB마다 `-Dlql.sql.update=true`로 다시 쓴다.

Javadoc 빌드는 `--snippet-path src/test/java`를 쓴다. Javadoc의 `{@snippet ... region="이름"}`이 가리키는 테스트 구간이 없으면 빌드가 실패한다.

---

## 4. 핵심 설계 원칙 (상세: ROADMAP 결정 표)

- 람다는 **실행하지 않는다**. 바이트코드만 읽는다. 캡처 값만으로 된 식(`p.trim()`, `min.intValue()`)은 Java에서 계산해 바인딩한다.
- **람다 안의 메서드와 비교는 모두 SQL 의미를 따른다(D48).** 결과가 Java와 다를 수 있으면 DEBUG `LQL-5009`. null 캡처 값과의 비교는 NULL을 바인딩하고 실행 시 WARN `LQL-4045`(일치형 자동 언박싱은 DEBUG).
- where의 연관 경로는 inner join, 정렬·select 전용 경로는 left join(필수 연관은 inner, A4). DML의 비FK 연관 경로는 상관 스칼라 서브쿼리(U9).
- DELETE의 서브쿼리가 대상 테이블을 읽으면 대상 id를 파생 테이블로 먼저 읽는다(U17, MariaDB 1093).
- 정수 열과 정수 형태가 없는 값(2.5)의 비교는 적은 값 그대로(상수는 리터럴, 캡처 값은 BigDecimal 바인딩, D48 B8).
- 대입은 setter 또는 `Dml.value(열, 값)`. 대입 람다의 두 번째 인자(insert … select는 세 번째)는 서브쿼리 `sub`(D49).
- 외부 정렬은 `SortPolicy`(OFF·INFO·WARN·STRICT, 기본 INFO, D43), SARGable은 `SargablePolicy`(OFF·WARN·STRICT, 기본 WARN, D50). 둘 다 EntityManagerFactory별 등록과 Spring 프로퍼티(`lambdaql.sort.*`, `lambdaql.sargable.mode`).
- 분석 결과는 EntityManagerFactory별로 공유하는 캐시에 둔다(D47). 정책 검사(SARGable 등)는 캐시에 넣을 때 한 번 한다.

---

## 5. 다음 작업

1. **번역**: `PENDING-TRANSLATION.md`의 항목을 en → ja → zh 순서로 반영한다(작성자 결정, 언어마다 순서대로). 31쪽이 한국어와 달라져 있어 바뀐 페이지를 통째로 다시 옮기는 방식이 정확하다. 2026-11-19에 en 작업을 시작했다가 작성자 요청으로 중지했다(변경 없음).
2. **Spring Boot 4 지원**: 작성자 요청으로 별도 브랜치에서 진행한다(미착수). Boot 4는 Hibernate 7·Jakarta Persistence 3.2 기반이라 HQL 렌더링과 Hibernate API 사용 부분(바인딩 타입, `unwrap`)의 확인이 필요하다(미측정).
3. **남은 미구현·v0.2 후보**(9절과 ROADMAP 기준): MariaDB `lateral` 재작성(Q4), `SortSpec`의 조인 형태, 집계 기준 정렬, Oracle 오버로드 선택, 조건부 값(CASE WHEN), 일반 `LIKE`, insert의 조인, 엔티티 4개 이상 조인, 복합 키 엔티티의 `limit`·`executeOne()`, U11·U12·U15 검사.
4. **메시지 생성기의 저장소 편입**: 7절 참고.

---

## 6. 검증 환경과 재현 방법

### 6.1 DB(로컬 docker)

| DB | 접속 | 사용 범위 |
|---|---|---|
| MariaDB 10.11.18 | 컨테이너 `mariadb`, `127.0.0.1:3306`, user `root` | **스키마 `lql_verify`만**. 다른 스키마, 전역 변수, `sql_mode`는 건드리지 않는다. 테스트 커넥션 풀은 2(최대 연결 수 초과 방지) |
| Oracle Free 23.26.3 | 컨테이너 `lql-oracle`, `jdbc:oracle:thin:@127.0.0.1:1521/FREEPDB1`, user `lql` | **스키마 `LQL`만**. 끝나면 `purge recyclebin` |
| H2 2.3.230 | 인메모리 | – |

**비밀번호 취급**: 컨테이너 환경 변수에서 실행할 때만 읽어 `LQL_DB_PASSWORD`에 넣고, 출력·로그·파일에 남기지 않으며 끝나면 `unset`한다.

```bash
export LQL_DB_PASSWORD="$(docker inspect mariadb --format '{{range .Config.Env}}{{println .}}{{end}}' | sed -n 's/^MARIADB_ROOT_PASSWORD=//p')"
export LQL_DB_PASSWORD="$(docker inspect lql-oracle --format '{{range .Config.Env}}{{println .}}{{end}}' | sed -n 's/^APP_USER_PASSWORD=//p')"
```

실측용으로 만드는 객체는 `SQ_` 접두를 붙이고 끝나면 지운다. 정리 확인: MariaDB `information_schema`의 `SQ\_%` 테이블·루틴 수, Oracle `user_objects`의 `SQ_%` 수가 0.

**한 번에 한 세션만 테스트한다.** 빌드 폴더(`query/build`)와 두 DB 스키마를 공유하므로 두 세션이 동시에 돌리면 결과 파일이 지워지고 서로의 테이블을 지운다(2026-11-19 발생). 다른 세션이 작업 트리의 미커밋 파일을 함께 커밋한 일도 있었다.

### 6.2 실측 근거와 작업 도구의 위치(⚠️ `/tmp`, 재부팅하면 사라질 수 있음)

| 주제 | 경로 |
|---|---|
| 초기 조사(ExTree·FluentJPA·Jinq 소스, **이 안에서 빌드 실행 금지**) | `/tmp/lql-research/` |
| NOT·3값 논리, 정렬 문법, Kotlin 패턴, D20 실측 | `/tmp/lql-notlogic/`, `/tmp/lql-sort/`, `/tmp/lql-kt/`, `/tmp/lql-d20/` |
| DML 의미 실측 | `/tmp/lql-dml/` |
| 메시지 카탈로그 생성기 | `/tmp/lql-i18n-gen/`(7절) |
| 한국어 매뉴얼 검사(링크, 일부 테스트 구간) | `/tmp/d48/check_ko.py` |
| 번역 검사(언어별 링크, 앵커, 코드 블록, 한글 잔존) | `/tmp/tr/check.py <lang>`, 대상 목록 `/tmp/tr_pages.txt` |

장기 보존이 필요하면 소스(`*.java`, `*.kt`, `*.py`)와 요약(`*.md`)만 저장소로 옮긴다.

---

## 7. 메시지 카탈로그 생성기

`LqlMessage.java`와 `LqlMessages{,_ja,_ko,_zh}.properties`는 생성 결과다. 원본은 `/tmp/lql-i18n-gen/catalog.py`(항목: 키, 코드, 인자 수, en, ko, ja, zh)이고 `python3 /tmp/lql-i18n-gen/gen.py`로 다시 쓴다.

- 코드를 추가할 때 3xxx는 `# ---- 4xxx rendering and running` 앞, 4xxx는 `("INTERNAL_ERROR"` 앞, 5xxx는 목록 끝에 넣는다.
- **생성기가 저장소 밖에 있다.** `/tmp`가 지워지면 메시지를 추가할 수단이 없어진다. 저장소(예: `tools/i18n/`)로 옮기는 것을 권한다(미실시).
- 쓰지 않게 된 코드는 지운다(예: LQL-3040). 같은 번호를 다른 뜻으로 다시 쓰지 않는다.

---

## 8. 작업 규칙과 함정

- **커밋과 푸시는 작성자 요청이 있을 때만.** 결정이 필요한 사항은 작성자에게 확인한 뒤 ROADMAP 결정 표에 "작성자 결정(날짜)"로 기록한다. 작성자에게 묻는 질문은 한국어로 쓴다.
- **매뉴얼은 한국어가 원본**이다. 기능이 바뀌면 한국어 페이지만 고치고, 번역본에 옮길 내용은 `PENDING-TRANSLATION.md`에 적는다. 구현·테스트 현황은 매뉴얼이 아니라 이 문서 9절에 둔다.
- **예제는 테스트 코드에서 가져온다.** 테스트에 `// @start region="이름"` … `// @end`로 구간을 표시하고, Javadoc은 `{@snippet class="…Test" region="이름"}`, 매뉴얼은 같은 구간을 바이트 단위로 옮기고 테스트 파일 링크를 붙인다. Java와 Kotlin 예제는 트랙별로 나눈다(`docs/manual/<언어>/java/`, `.../kotlin/`).
- 문서와 대화는 한국어, **코드 주석과 Javadoc은 쉬운 영어**(모든 공개 요소에 설명, `@param`, `@return`, `@throws`, 예제). 한국어 문서는 korean-docs 문체 규칙을 따른다(명사구 제목, 짧은 문단, 실측·미측정 표기).
- **라이선스**: ExTree(LGPL)·FluentJPA(AGPL/LGPL) 코드는 복사하지 않는다(개념만 참고). ROADMAP에 특허 조사 흔적을 남기지 않는다.
- **바이트코드 함정**
  - float·double 비교의 `cmpl`·`cmpg` bias로 소스 연산자를 복원한다. bias를 버리지 않는다.
  - 상수 접기로 퇴화 jump, 원자 소멸, 단독 goto가 생긴다.
  - Kotlin은 조건 jump 시점에 값 스택이 비어 있지 않은 패턴(`?.`, `?:`, 자동 null 확인)이 흔하다.
  - 자기 기본형으로 꺼내는 언박싱(`Double.doubleValue()`)은 javac의 자동 언박싱과 구분할 수 없어 자동 언박싱으로 본다(D48).
- **Hibernate 함정(6.6.5 실측)**
  - 정수 열의 파라미터에 소수를 바인딩하면 `CoercionException`, IN 목록은 조용히 자른다. BigDecimal 타입을 명시해 바인딩한다(B8).
  - UPDATE·DELETE HQL에 조인 문법이 없다. 엔티티 `select distinct`, 파생 테이블·CTE 조인, 서브쿼리 안 엔티티 조인은 받는다(D51).
- **DB 함정**
  - MariaDB: `NULLS FIRST/LAST` 없음(Hibernate가 CASE로 흉내), DELETE가 자기 테이블을 읽으면 1093(UPDATE는 10.11에서 동작), 기본 collation `_ci`, NaN 저장 불가.
  - Oracle: `''`를 NULL로 저장, `NLS_SORT`가 JVM locale을 따름, `@Lob` 열은 정렬·distinct·group by 불가(ORA-22848/22849).
  - H2만으로는 NULL 정렬 위치, `''`=NULL, 평가 순서 같은 차이를 확인할 수 없다. 완료 기준은 세 DB다.

---

## 9. 기능별 구현·테스트 현황(매뉴얼에서 옮김)

2026-11-19에 매뉴얼 각 장의 맨 위에 있던 상태 노트와 개발 관련 노트를 이곳으로 옮겼다. 매뉴얼은 사용 방법만 다룬다.

아래 내용은 옮긴 시점의 한국어 매뉴얼 기준이다. 언어별로 따로 두지 않는다. 기능이 바뀌면 이 절을 고친다.

### 공통 페이지

#### [README.md](docs/manual/ko/README.md)

**상태: lambdaQL은 초기 개발 단계입니다.**
현재 **분석 코어**, **결과 함수를 포함한 `where`**(`toList()`, `toSet()`, `first()`, `one()`, `count()`, `exists()`), 그리고 **정렬과 페이징**(`orderBy`, `limit`, `offset`, `SortSpec`)의 핵심 기능이 구현되어 있습니다. lambdaQL은 람다를 로드하고, 디코딩하고, HQL을 쓰고, 실행합니다. 이 부분은 H2, MariaDB, Oracle에서 테스트했습니다(Java 람다 사용).
**수정, 삽입, 삭제, 업서트**도 `limit`과 1행 보호 `executeOne()`을 포함해 구현되어 있습니다(H2, MariaDB, Oracle에서 Java 람다로 테스트함).
**Spring Data JPA 모듈**도 구현되어 있습니다. 리포지토리 실행기 `LambdaQlExecutor<T>`(Spring `Sort`, `Pageable`, `deleteAll(where)`·`updateOne(where, set)` 같은 DML 단축 메서드, 수정·삭제·삽입·업서트 빌더 포함)와 Spring Boot 자동 구성이 대상이며, Spring Boot 3.4.2에서 테스트했습니다. DML에는 호출자의 트랜잭션이 필요합니다.
**서브쿼리**(`where((o, sub) -> ...)`, 수정·삭제 포함)와 **`select`**(레코드와 DTO, `groupBy`, `having`, `Agg` 집계, `insert ... select` 포함)도 구현되어 있습니다(H2, MariaDB, Oracle에서 Java 람다로 테스트함).
**명시적 조인**(`join`, `leftJoin`, `crossJoin`), **파생 테이블과 CTE**(`qb.from(valueQuery)`, `qb.with(valueQuery)`), **저장 프로시저와 DB 함수**(`@SqlProcedure`, `@SqlFunction`, `qb.call`, `qb.exec`), **4개 언어 메시지와 오류 코드**(`[LQL-xxxx]`)도 구현되어 있습니다(Java 람다로 테스트함. 프로시저는 MariaDB와 Oracle에서 테스트함).
**Kotlin 람다**는 일부 형태(`?.`, `!!`, `in`, 서브쿼리, 데이터 클래스, `groupBy`, update, insert, delete)를 빌드 안에서 테스트했습니다. 나머지 Kotlin 전용 형태는 **설계** 단계입니다. 문법과 규칙은 확정되었고 프로토타입으로 확인했지만, 빌드 안의 테스트는 아직 없습니다.
아직 Maven Central에 배포된 아티팩트는 없습니다. 사용해 보려면 소스에서 빌드하십시오([Java](docs/manual/ko/java/02-getting-started.md#build-from-source) | [Kotlin](docs/manual/ko/kotlin/02-getting-started.md#build-from-source)).

#### [01-overview.md](docs/manual/ko/01-overview.md)

**상태:** 분석 코어(람다 로딩과 조건 디코딩)와 결과 함수 `toList()`, `toSet()`, `first()`, `one()`, `count()`, `exists()`를 포함한 `where`(HQL 렌더링과 쿼리 실행)가 구현되어 있습니다. H2, MariaDB, Oracle에서 Java 람다로 테스트했습니다.
정렬과 페이징(`orderBy`, `limit`, `offset`, `SortSpec`)의 핵심 기능도 구현되어 테스트했습니다.
Spring Data JPA 모듈(`LambdaQlExecutor<T>`와 Spring Boot 자동 구성)도 구현되어 Spring Boot 3.4.2에서 테스트했습니다.
수정, 삽입, 삭제, 업서트도 `limit`과 1행 보호 `executeOne()`을 포함해 구현되어 테스트했으며, Spring Data JPA 모듈에서도 사용할 수 있습니다(단축 메서드 `deleteAll`, `updateOne` 등). Spring Data JPA 모듈에서는 호출자의 트랜잭션이 필요합니다.
`where` 안의 서브쿼리와 `select`(`groupBy`, `having`, `Agg` 함수 포함)도 구현되어 테스트했습니다.
명시적 조인(`join`, `leftJoin`, `crossJoin`), 파생 테이블과 CTE, 저장 프로시저와 DB 함수, 4개 언어 메시지와 오류 코드도 구현되어 테스트했습니다.
Kotlin 람다는 일부 형태(`?.`, `!!`, `in`, 서브쿼리, 데이터 클래스, `groupBy`, update, insert, delete)를 테스트했습니다.
이 페이지의 HQL은 설계가 정의한 출력입니다. 예제는 설계 예제입니다.

#### [11-errors-and-languages.md](docs/manual/ko/11-errors-and-languages.md)

**상태:** 구현됨, 테스트함(결정 D33).
모든 메시지에 `[LQL-xxxx]` 코드가 붙고, 메시지는 영어, 일본어, 중국어(간체), 한국어로 제공합니다.
예외는 `LambdaQLException`과 그 하위 클래스입니다. 경고는 SLF4J로 기록합니다.
**(테스트됨)** 표시가 있는 예제는 테스트 코드([`LqlMessagesTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/i18n/LqlMessagesTest.java), [`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java))에서 그대로 가져온 것입니다. 이 페이지의 다른 코드는 설계 예제입니다.
아직 구현되지 않음: 경고를 오류로 바꾸는 엄격 모드.

### Java 트랙

#### [java/02-getting-started.md](docs/manual/ko/java/02-getting-started.md)

**상태:** lambdaQL은 아직 Maven Central에 배포되지 않았습니다. [소스에서 빌드](docs/manual/ko/java/02-getting-started.md#build-from-source)해서 사용하십시오.
이 장에서 다루는 `where`, `toList()`, 결과 함수는 H2, MariaDB, Oracle에서 테스트했습니다.
장별 구현·테스트 현황은 [Java 트랙의 상태](docs/manual/ko/java/README.md#status)에 모아 둡니다.

#### [java/03-where.md](docs/manual/ko/java/03-where.md)

**상태**
- **구현됨, H2·MariaDB·Oracle에서 테스트함:** `where` 조건과 `toList()`. lambdaQL이 getter를 컬럼에 매핑하고, HQL을 쓰고, 실행합니다.
- **함께 구현됨:** 정렬과 페이징의 핵심 기능([4. 정렬과 페이징](docs/manual/ko/java/04-order-paging.md)), 서브쿼리([5. 서브쿼리](docs/manual/ko/java/05-subquery.md)), `groupBy`·`having`·`Agg`를 포함한 `select`, 명시적 조인, 파생 테이블, CTE([6. 조회, 집계, 그룹화](docs/manual/ko/java/06-select-group.md)), update/insert/delete/upsert([7. 수정, 삽입, 삭제](docs/manual/ko/java/07-dml.md)), 프로시저와 DB 함수([8. 프로시저와 DB 함수](docs/manual/ko/java/08-procedures-functions.md)), Spring Data JPA 모듈([12. Spring Data JPA](docs/manual/ko/java/12-spring-data-jpa.md)).
- **(테스트됨)** 표시가 있는 예제는 테스트 코드에서 그대로 가져온 것입니다. 이 장의 나머지 HQL은 설계에 따른 **예정된** 출력입니다. 세부 사항(예: `e0`, `j1` 같은 별칭 이름)은 아직 조금 바뀔 수 있습니다.

#### [java/04-order-paging.md](docs/manual/ko/java/04-order-paging.md)

**상태**
- **핵심 기능 구현됨, 테스트함**(Hibernate 6.6.5로 H2, MariaDB 10.11, Oracle 23.26): `by.asc`, `by.desc`, `by.sort`를 사용하는 `orderBy((o, by) -> ...)`, 키 하나의 형태 `orderBy(o -> key)`와 `orderBy(key, Direction)`(`Order::getPrice` 같은 getter 메서드 참조도 가능), `Direction.parse` / `reversed`, `limit` / `offset`, `orderBy` 뒤의 `first()`, 그리고 재사용 정렬 `SortSpec`(`of`, `ofPath`, `then`, `reversed`, `orderBy(spec)`). Spring `Sort`와 `Pageable`은 Spring Data JPA 모듈에서 동작합니다([12.5](docs/manual/ko/java/12-spring-data-jpa.md#125-spring-sort-and-pageable)).
- **추가로 구현됨, 테스트함:** `join`, `leftJoin`, `crossJoin` 뒤의 조인 형태 `(o, c, by) -> ...`([6.8 명시적 조인](docs/manual/ko/java/06-select-group.md#68-explicit-joins) 참고), 외부 정렬의 정렬 정책(`SortRules`, `SortPolicy`, `SortMode`, [4.10](docs/manual/ko/java/04-order-paging.md#sort-policy) 참고).
- **아직 구현되지 않음:** `SortSpec`의 조인 형태.
- **(테스트됨)** 표시가 있는 예제는 테스트 코드에서 그대로 가져온 것입니다. 이 장의 나머지 HQL도 같은 규칙을 따르지만 테스트하지는 않았습니다.

#### [java/05-subquery.md](docs/manual/ko/java/05-subquery.md)

**상태**
- **구현됨, 테스트함**(H2 2.3, MariaDB 10.11, Oracle 23.26, Hibernate 6.6.5, 마일스톤 M8-1): 조회, 수정, 삭제의 `where((o, sub) -> ...)` 안의 서브쿼리. `exists` / `not exists`, `contains`(IN), `!contains`(`NOT IN`으로 작성), 스칼라 집계(`count`, `sum`, `avg`, `min`, `max`), top-1 형태 `orderBy(...).limit(1)...first()`, 상관 서브쿼리와 중첩 서브쿼리, 캡처 값, 서브쿼리 안의 조인(D51)을 포함합니다.
- **아직 구현되지 않음:** MariaDB `lateral` 재작성. (파생 테이블과 CTE는 다른 문법으로 구현했습니다. [6. 조회, 집계, 그룹화](docs/manual/ko/java/06-select-group.md#69-derived-table-subquery-in-from)를 참고하십시오.)
- **(테스트됨)** 표시가 있는 예제는 테스트 코드에서 그대로 가져온 것입니다. 나머지 예제는 같은 규칙을 따르지만 테스트가 없습니다.

#### [java/06-select-group.md](docs/manual/ko/java/06-select-group.md)

**상태**
- **구현됨, 테스트함**(H2 2.3, MariaDB 10.11, Oracle 23.26, Hibernate 6.6.5, 마일스톤 M9, 결정 D27): 값 하나, Java 레코드, DTO의 `select`, 키 하나나 여러 개의 `groupBy`, `having`, `Agg` 함수, `distinct()`, `limit`, `offset`, `toList`, `toSet`, `first`, `one`, `orderBy(...).select(...)`, 규칙 J3, J4, J7의 검사.
- **추가로 구현됨, 테스트함**(결정 D44, D45, D46): 엔티티 3개까지의 `join`, `leftJoin`, `crossJoin` 명시적 조인과 `select((o, c) -> ...)`(6.8), `qb.from(valueQuery)`와 `qb.with(valueQuery)`의 파생 테이블과 CTE(6.9, 6.10, 마일스톤 M8-2). 조인 결과의 `distinct()`와 파생 테이블·CTE의 조인(6.8, 결정 D51).
- **아직 구현되지 않음:** 집계 기준 정렬과 그룹 쿼리의 `orderBy`(v0.2 후보). 서브쿼리 안의 조인은 구현됨([5. 서브쿼리](docs/manual/ko/java/05-subquery.md#joins-inside-a-subquery)). update와 delete의 조인은 추가하지 않습니다(D51).
- **(테스트됨)** 표시가 있는 예제는 테스트 코드에서 그대로 가져온 것입니다. 전체 상태는 [Java 트랙 README](docs/manual/ko/java/README.md#status)를 참고하십시오.

#### [java/07-2-update.md](docs/manual/ko/java/07-2-update.md)

**상태: 구현됨, 테스트함**(H2 2.3, MariaDB 10.11, Oracle 23.26, 마일스톤 M10). `limit`과 `executeOne()`(결정 D41)도 포함합니다.
`where` 안의 서브쿼리(`sub`)도 구현되었습니다(마일스톤 M8). 삭제 형태는 테스트했습니다. [7.4](docs/manual/ko/java/07-4-delete.md)를 참고하십시오.
**아직 구현되지 않음:** `set` 값 안의 `sub` 서브쿼리, 복합 키 엔티티의 `limit` / `executeOne()`.
**(테스트됨)** 표시가 있는 예제는 [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)에서 그대로 가져온 것입니다. 나머지 예제는 설계를 따르며 아직 테스트가 없습니다.
전체 상태는 [Java 트랙 README](docs/manual/ko/java/README.md#status)를 참고하십시오.

#### [java/07-4-delete.md](docs/manual/ko/java/07-4-delete.md)

**상태: 구현됨, 테스트함**(H2 2.3, MariaDB 10.11, Oracle 23.26, 마일스톤 M10). `limit`과 `executeOne()`(결정 D41)도 포함합니다.
`where` 안의 서브쿼리(`sub`)도 구현되어 테스트했습니다(마일스톤 M8).
**아직 구현되지 않음:** 복합 키 엔티티의 `limit` / `executeOne()`.
**(테스트됨)** 표시가 있는 예제는 [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)와 [`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java)에서 그대로 가져온 것입니다. 나머지 예제는 설계를 따르며 아직 테스트가 없습니다.
전체 상태는 [Java 트랙 README](docs/manual/ko/java/README.md#status)를 참고하십시오.

#### [java/07-5-insert.md](docs/manual/ko/java/07-5-insert.md)

**상태: 구현됨, 테스트함**(H2 2.3, MariaDB 10.11, Oracle 23.26, 마일스톤 M10). `insert ... select`의 `groupBy`, `having`, `Agg`(마일스톤 M9)도 포함합니다.
**(테스트됨)** 표시가 있는 예제는 [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)와 [`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java)에서 그대로 가져온 것입니다. 나머지 예제는 설계를 따르며 아직 테스트가 없습니다.
전체 상태는 [Java 트랙 README](docs/manual/ko/java/README.md#status)를 참고하십시오.

#### [java/07-6-upsert.md](docs/manual/ko/java/07-6-upsert.md)

**상태: 구현됨, 테스트함**(H2 2.3, MariaDB 10.11, Oracle 23.26, 마일스톤 M10, 결정 D40).
**(테스트됨)** 표시가 있는 예제는 [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)에서 그대로 가져온 것입니다. 나머지 예제는 설계를 따르며 아직 테스트가 없습니다.
전체 상태는 [Java 트랙 README](docs/manual/ko/java/README.md#status)를 참고하십시오.

#### [java/07-dml.md](docs/manual/ko/java/07-dml.md)

**상태**
- **구현됨, 테스트함:** H2 2.3, MariaDB 10.11, Oracle 23.26(Hibernate 6.6.5, 마일스톤 M10, 결정 D28·D40). 대상은 update(setter, `Dml.value`, 여러 번의 `set()` 호출, `@Version`, `set` 값 안의 서브쿼리), delete, insert(한 행, 리스트, 생성 id, `insert ... select`), upsert, 규칙 U1·U3·U4·U6 검사, update와 delete의 `limit`, 1행 보호 `executeOne()`(결정 D41)입니다.
- **함께 구현됨, 테스트함:** 수정·삭제 `where` 안의 서브쿼리(`sub`)(마일스톤 M8, [7.4](docs/manual/ko/java/07-4-delete.md) 참고), `insert ... select`의 `groupBy`, `having`, `Agg`(마일스톤 M9, [7.5](docs/manual/ko/java/07-5-insert.md) 참고).
- **아직 구현되지 않음:** 복합 키 엔티티의 `limit` / `executeOne()`.
- **(테스트됨)** 표시가 있는 예제는 테스트 코드에서 그대로 가져온 것입니다. 나머지 예제는 설계를 따르며 아직 테스트가 없습니다. 이 예제들의 HQL은 세부 사항(예: `e0` 같은 별칭 이름)이 다를 수 있습니다.
- 전체 상태는 [Java 트랙 README](docs/manual/ko/java/README.md#status)를 참고하십시오.

#### [java/08-procedures-functions.md](docs/manual/ko/java/08-procedures-functions.md)

**상태**
- **구현됨, 테스트함**(결정 D30, D31, 마일스톤 M11, Hibernate 6.6.5):
쿼리 안의 DB 함수(`@SqlFunction`)는 H2 2.3, MariaDB 10.11, Oracle 23.26에서 테스트했습니다([`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java)).
프로시저 호출(`@SqlProcedure`, `@InOut`, `qb.call`, `qb.exec`)은 MariaDB와 Oracle에서, DB 함수 단독 호출은 세 데이터베이스 모두에서 테스트했습니다([`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java)).
H2에는 OUT 파라미터가 없으므로 프로시저 테스트는 H2에서 실행하지 않습니다.
- **추가로 구현됨, 테스트함:** 데이터베이스와의 스텁 인자 **이름** 대조(규칙 PR2의 일부), Oracle 패키지 오버로드 검출.
- **추가로 구현됨, 테스트함:** 조건 안의 DB 함수와 컬럼 산술에 대한 SARGable 경고, 엄격 모드 `SargableMode.STRICT`(규칙 PR8, FN7, 결정 D50, [8.9](docs/manual/ko/java/08-procedures-functions.md#89-warning-functions-on-columns-and-indexes-sargable) 참고).
- **아직 구현되지 않음:** 데이터베이스와의 파라미터 **타입** 대조(규칙 PR2의 일부), Oracle 패키지 프로시저의 오버로드 하나를 골라 호출하는 기능.
- **(테스트됨)** 표시가 있는 예제는 테스트 코드에서 그대로 가져온 것입니다. 다른 예제도 같은 규칙을 따르지만 테스트로 확인하지는 않았습니다. 전체 상태는 [Java 트랙 README](docs/manual/ko/java/README.md#status)를 참고하십시오.

**구현됨, 테스트함**(H2, MariaDB 10.11, Oracle 23.26, 규칙 PR8, FN7). 엄격 모드(`SargableMode.STRICT`)와 컬럼 산술의 경고(`LQL-5012`, `LQL-5013`)도 구현됨(결정 D50).

#### [java/09-java-notes.md](docs/manual/ko/java/09-java-notes.md)

**상태**
- **구현됨(분석 코어):** 직렬화 가능한 람다 로딩, `this`를 캡처하는 람다, 메서드 참조 감지, `Serializable`이 아닌 람다 거부. [9.13](docs/manual/ko/java/09-java-notes.md#913-what-you-can-check-today)의 테스트를 참고하십시오.
- **구현됨:** 키 하나의 정렬에서 getter 메서드 참조, 예: `orderBy(Order::getPrice)`([9.9](docs/manual/ko/java/09-java-notes.md#99-method-references) 참고).
- **설계됨, 아직 구현되지 않음:** 이 페이지의 나머지 전부(`==`의 의미, 박싱 규칙, `String` 규칙, 쿼리 안의 메서드 참조 등).

저장소에는 아직 예전 프로토타입 코드(예: `JpqlFunction` 클래스)가 남아 있습니다.
새 설계의 일부가 아닙니다. 쿼리에서 사용하지 마십시오.

#### [java/10-rules-and-limits.md](docs/manual/ko/java/10-rules-and-limits.md)

**상태: 일부 구현됨.**
이 장은 lambdaQL 설계의 규칙을 모아 둔 곳입니다. 람다가 SQL이 되는 방식, NULL이 동작하는 방식,
Java와 SQL이 다른 점, 데이터베이스마다 다른 점, 그리고 지원하지 않는 것을 다룹니다.
규칙은 H2 2.3, MariaDB 10.11, Oracle 23ai(Hibernate 6.6.5)에서 프로토타입으로 확인했습니다.
현재는 분석 코어, `where`, 결과 함수(`toList()`, `toSet()`, `first()`, `one()`, `count()`, `exists()`), 정렬과 페이징의 핵심 기능, `SortSpec`, update/insert/delete/upsert([7](docs/manual/ko/java/07-dml.md)), Spring Data JPA 모듈([12](docs/manual/ko/java/12-spring-data-jpa.md))이 동작합니다(H2, MariaDB, Oracle에서 테스트함).
서브쿼리([5](docs/manual/ko/java/05-subquery.md))와 집계를 포함한 `select`([6](docs/manual/ko/java/06-select-group.md)) 규칙도 DML을 포함해 구현되었습니다. 명시적 조인, 파생 테이블, CTE([6](docs/manual/ko/java/06-select-group.md)), 프로시저와 DB 함수([8](docs/manual/ko/java/08-procedures-functions.md)) 규칙도 구현되었습니다. 컬럼에 대한 DB 함수의 경고([10.2](docs/manual/ko/java/10-rules-and-limits.md#db-functions-on-a-column))도 구현되었습니다. 정렬 규칙 중에도 아직 구현되지 않은 것이 있습니다([4.12](docs/manual/ko/java/04-order-paging.md#412-rules-summary-s1-to-s14) 참고).
전체 상태는 [Java 트랙 README](docs/manual/ko/java/README.md#status)를 참고하십시오.

#### [java/12-spring-data-jpa.md](docs/manual/ko/java/12-spring-data-jpa.md)

**상태: 조회 실행기와 Spring Boot 자동 구성 구현됨.**
`LambdaQlExecutor<T>`, Spring `Sort` / `Pageable` 지원(정렬 정책 포함), `QueryBuilder` 빈은 H2, MariaDB 10.11, Oracle 23.26에서 Spring Boot 3.4.2로 테스트했습니다.
수정, 삭제, 삽입, 업서트 메서드도 테스트했습니다. 대상은 단축 메서드(`deleteAll`, `deleteOne`, `updateAll`, `updateOne`, `insertOne`, `insertAll`, `upsertOne`, `upsertAll`)와 빌더(`updateQuery()`, `deleteQuery()`, `insertQuery()`, `upsertQuery()`)입니다. 이 메서드에는 호출자의 트랜잭션이 필요합니다. lambdaQL은 `@Transactional`을 붙이지 않습니다(결정 D41).
**아직 검증하지 않음:** Spring Boot 4.
**(테스트됨)** 표시가 있는 예제는 테스트 코드에서 그대로 가져온 것입니다.
전체 상태는 [Java 트랙 README](docs/manual/ko/java/README.md#status)를 참고하십시오.

#### [java/README.md](docs/manual/ko/java/README.md)

**상태:** lambdaQL은 초기 개발 단계입니다.
**분석 코어**, **결과 함수를 포함한 `where`**(`toList()`, `toSet()`, `first()`, `one()`, `count()`, `exists()`), 그리고 **정렬과 페이징**(`orderBy`, `limit`, `offset`, `SortSpec`)의 핵심 기능이 구현되어 있습니다. lambdaQL은 람다를 로드하고, 디코딩하고, HQL을 쓰고, 실행합니다. 이 부분은 H2, MariaDB, Oracle에서 테스트했습니다.
**수정, 삽입, 삭제, 업서트**도 `limit`과 1행 보호 `executeOne()`을 포함해 구현되어 있습니다(H2, MariaDB, Oracle에서 테스트함).
**Spring Data JPA 모듈**도 구현되어 있습니다. 리포지토리 실행기 `LambdaQlExecutor<T>`(Spring `Sort`, `Pageable`, `deleteAll(where)`·`updateOne(where, set)` 같은 DML 단축 메서드, 수정·삭제·삽입·업서트 빌더 포함)와 Spring Boot 자동 구성이 대상이며, Spring Boot 3.4.2에서 테스트했습니다. DML에는 호출자의 트랜잭션이 필요합니다.
**서브쿼리**(`where((o, sub) -> ...)`, 수정·삭제 포함)와 **`select`**(레코드와 DTO, `groupBy`, `having`, `Agg` 집계, `insert ... select` 포함)도 구현되어 있습니다(H2, MariaDB, Oracle에서 테스트함).
**명시적 조인**(`join`, `leftJoin`, `crossJoin`), **파생 테이블과 CTE**(`qb.from(valueQuery)`, `qb.with(valueQuery)`), **저장 프로시저와 DB 함수**(`@SqlProcedure`, `@SqlFunction`, `qb.call`, `qb.exec`), **4개 언어 메시지와 오류 코드**(`[LQL-xxxx]`)도 구현되어 있습니다(H2, MariaDB, Oracle에서 테스트함. 프로시저는 MariaDB와 Oracle에서 테스트함).
외부 정렬의 **정렬 정책**(`SortRules`, `SortPolicy`, `SortMode`, `@Sortable`, Spring Boot 프로퍼티 `lambdaql.sort.*`)도 구현되어 있습니다(H2, MariaDB, Oracle에서 테스트함).
이 트랙의 나머지 내용(예: `SortSpec`의 조인 형태, 엄격 모드)은 **설계** 단계입니다. 문법과 규칙은 확정되었고 프로토타입으로 확인했지만, 코드는 아직 라이브러리에 들어 있지 않습니다.
아직 Maven Central에 배포된 아티팩트는 없습니다. 사용해 보려면 [소스에서 빌드](docs/manual/ko/java/02-getting-started.md#build-from-source)하십시오.

### Kotlin 트랙

#### [kotlin/02-getting-started.md](docs/manual/ko/kotlin/02-getting-started.md)

**상태:** lambdaQL은 아직 Maven Central에 배포되지 않았습니다. [소스에서 빌드](docs/manual/ko/kotlin/02-getting-started.md#build-from-source)해서 사용하십시오.
이 장에서 다루는 `where`, `toList()`, 결과 함수는 H2, MariaDB, Oracle에서 테스트했습니다.
장별 구현·테스트 현황(Kotlin 람다로 테스트한 범위 포함)은 [Kotlin 트랙의 목차](docs/manual/ko/kotlin/README.md#contents)에 모아 둡니다.

#### [kotlin/03-where.md](docs/manual/ko/kotlin/03-where.md)

**상태**
- **구현됨, H2·MariaDB·Oracle에서 테스트함:** `where` 조건과 `toList()`(컬럼 매핑, HQL 출력, 쿼리 실행). 테스트는 대부분 **Java** 람다를 사용합니다.
- **Kotlin 람다로 테스트함**([`KotlinSelectTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinSelectTest.kt), 빌드에 포함, kotlinc 2.3.21): `?.` 경로, 캡처 값, 캡처 컬렉션의 `in`. `!!`는 [5. 서브쿼리](docs/manual/ko/kotlin/05-subquery.md)의 Kotlin 테스트에서 확인합니다. 이 예제에는 **(테스트됨)** 표시가 있습니다.
- **설계됨, 아직 테스트하지 않음:** 이 장의 나머지 Kotlin 전용 형태(`?:`, 범위, `when`). 이 형태들의 바이트코드는 프로토타입으로 컴파일하여 확인했고(kotlinc 1.7.22 ~ 2.3.21), 많은 조건을 H2에서 행 단위로 확인했습니다.
- **추가로 구현됨:** 정렬과 페이징의 핵심 기능([4. 정렬과 페이징](docs/manual/ko/kotlin/04-order-paging.md)), 서브쿼리([5. 서브쿼리](docs/manual/ko/kotlin/05-subquery.md)), `groupBy`·`having`·`Agg`를 포함한 `select`, 조인, 파생 테이블, CTE([6. 조회, 집계, 그룹화](docs/manual/ko/kotlin/06-select-group.md)), update/insert/delete/upsert([7. 수정, 삽입, 삭제](docs/manual/ko/kotlin/07-dml.md)), 프로시저와 DB 함수([8. 프로시저와 DB 함수](docs/manual/ko/kotlin/08-procedures-functions.md)), Spring Data JPA 모듈([12. Spring Data JPA](docs/manual/ko/kotlin/12-spring-data-jpa.md)).
- 이 장의 다른 HQL은 **예정된** 출력입니다. 세부 사항(예: `e0`, `j1` 같은 별칭 이름)은 아직 조금 바뀔 수 있습니다.

#### [kotlin/04-order-paging.md](docs/manual/ko/kotlin/04-order-paging.md)

**상태**
- **핵심 기능 구현됨**, Hibernate 6.6.5로 H2, MariaDB 10.11, Oracle 23.26에서 테스트함: `by.asc`, `by.desc`, `by.sort`를 사용하는 `orderBy { o, by -> ... }`, 키 하나의 형태 `orderBy { it.key }`와 `orderBy({ it.key }, Direction)`, `Direction.parse` / `reversed`, `limit` / `offset`, `orderBy` 뒤의 `first()`, 그리고 재사용 정렬 `SortSpec`(`of`, `ofPath`, `then`, `reversed`, `orderBy(spec)`). Spring `Sort`와 `Pageable`은 Spring Data JPA 모듈에서 동작합니다([12.5](docs/manual/ko/kotlin/12-spring-data-jpa.md#125-spring-sort-and-pageable)). 테스트는 대부분 **Java** 람다를 사용합니다. `limit`이 있는 Kotlin 정렬도 테스트했습니다([`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt), **(테스트됨)** 표시).
- **추가로 구현됨(Java 람다로 테스트함):** `join`, `leftJoin`, `crossJoin` 뒤의 조인 형태 `{ o, c, by -> ... }`([6.8 명시적 조인](docs/manual/ko/kotlin/06-select-group.md#68-explicit-joins) 참고), 외부 정렬의 정렬 정책(`SortRules`, `SortPolicy`, `SortMode`, [4.10](docs/manual/ko/kotlin/04-order-paging.md#sort-policy) 참고).
- **아직 구현되지 않음:** Kotlin 속성 참조(`orderBy(Order::price)`), `SortSpec`의 조인 형태.
- Kotlin 문법은 컴파일됩니다(프로토타입으로 확인, kotlinc 2.3.21). nullable 키, `?.` 키, 속성 참조는 컴파일되며, 엔티티 키는 컴파일되지 않습니다.
- 이 장의 다른 HQL은 라이브러리의 규칙을 따르지만, Kotlin 람다로 테스트하지는 않았습니다.

#### [kotlin/05-subquery.md](docs/manual/ko/kotlin/05-subquery.md)

**상태**
- **구현됨**(마일스톤 M8-1). H2 2.3, MariaDB 10.11, Oracle 23.26(Hibernate 6.6.5)에서 테스트했습니다. 대상은 조회, 수정, 삭제의 `where { o, sub -> ... }` 안의 서브쿼리입니다. `exists` / `!exists`, `in`, `!in`(`NOT IN`으로 작성), 스칼라 집계, top-1 형태, 상관 서브쿼리와 중첩 서브쿼리, 캡처 값, 서브쿼리 안의 조인(D51)을 포함합니다. 테스트는 대부분 **Java** 람다를 사용합니다.
- **Kotlin 람다로 테스트함**([`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt), 빌드에 포함, H2, MariaDB, Oracle): `exists`, `in`, 스칼라 `avg`. 이 예제에는 **(테스트됨)** 표시가 있습니다. 그 밖의 Kotlin 형태(`!in`, top-1, 중첩)는 빌드 밖에서 kotlinc 2.3.21로 컴파일했고, Java 형태와 같은 HQL을 만듭니다.
- **아직 구현되지 않음:** MariaDB `lateral` 재작성. (파생 테이블과 CTE는 다른 문법으로 구현했습니다. [6. 조회, 집계, 그룹화](docs/manual/ko/kotlin/06-select-group.md#69-derived-table-subquery-in-from)를 참고하십시오.)
- 그 밖의 Kotlin 코드는 테스트된 Java 쿼리를 Kotlin 문법으로 옮긴 것입니다. 테스트된 Java 예제는 [이 페이지의 Java 버전](docs/manual/ko/java/05-subquery.md)에 있습니다.

#### [kotlin/06-select-group.md](docs/manual/ko/kotlin/06-select-group.md)

**상태**
- **구현됨**(마일스톤 M9, 결정 D27). H2 2.3, MariaDB 10.11, Oracle 23.26(Hibernate 6.6.5)에서 테스트했습니다. 대상은 값 하나, 데이터 클래스, `Pair`, `Triple`의 `select`, 키 하나나 여러 키(`listOf`, `a to b`, `Triple`)의 `groupBy`, `having`, `Agg` 함수, `distinct()`, `limit`, `offset`, `toList`, `toSet`, `first`, `one`, `orderBy { }.select { }`, 규칙 J3, J4, J7의 검사입니다. 
- **Kotlin 람다로 테스트함**([`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt), 빌드에 포함, H2, MariaDB, Oracle): 데이터 클래스, `Pair`, 키 하나의 `groupBy`와 `having`, `listOf` 키의 `groupBy`와 `Triple` 결과, 조인 결과의 `distinct()`, CTE의 조인. 이 예제에는 **(테스트됨)** 표시가 있습니다. 그 밖의 Kotlin 형태는 빌드 밖에서 kotlinc 2.3.21로 컴파일했고, Java 형태와 같은 HQL을 만듭니다.
- **추가로 구현됨**(결정 D44, D45, D46, Java 람다로 테스트함): 엔티티 3개까지의 `join`, `leftJoin`, `crossJoin` 명시적 조인과 `select { o, c -> ... }`(6.8), `qb.from(valueQuery)`와 `qb.with(valueQuery)`의 파생 테이블과 CTE(6.9, 6.10, 마일스톤 M8-2). 조인 결과의 `distinct()`와 파생 테이블·CTE의 조인(6.8, 결정 D51).
- **아직 구현되지 않음:** 집계 기준 정렬과 그룹 쿼리의 `orderBy`(v0.2 후보). 서브쿼리 안의 조인은 구현됨([5. 서브쿼리](docs/manual/ko/kotlin/05-subquery.md#joins-inside-a-subquery)). update와 delete의 조인은 추가하지 않습니다(D51).
- 그 밖의 Kotlin 코드는 테스트된 Java 쿼리를 Kotlin 문법으로 옮긴 것입니다. 테스트된 Java 예제는 [이 페이지의 Java 버전](docs/manual/ko/java/06-select-group.md)에 있습니다. 전체 상태는 [트랙 README](docs/manual/ko/kotlin/README.md)를 참고하십시오.

#### [kotlin/07-2-update.md](docs/manual/ko/kotlin/07-2-update.md)

**상태: 구현됨, 테스트함**(H2 2.3, MariaDB 10.11, Oracle 23.26, 마일스톤 M10). 테스트는 대부분 **Java** 람다를 사용합니다. 속성 대입, `Dml.value`, `set` 값 안의 서브쿼리는 Kotlin 람다로도 테스트했습니다([`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt), **(테스트됨)** 표시). `limit`과 `executeOne()`(결정 D41)도 같습니다.
`where` 안의 서브쿼리(`sub`)도 구현되었습니다(마일스톤 M8). 삭제 형태는 Java 람다로 테스트했습니다. [7.4](docs/manual/ko/kotlin/07-4-delete.md)를 참고하십시오.
**아직 구현되지 않음:** `set` 값 안의 `sub` 서브쿼리, 복합 키 엔티티의 `limit` / `executeOne()`.
Kotlin 코드는 테스트된 Java 문장을 Kotlin 문법으로 옮긴 것이고, HQL은 [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)의 Java 테스트에서 가져왔습니다. HQL이 없는 예제는 설계를 따릅니다.
전체 상태는 [트랙 README](docs/manual/ko/kotlin/README.md)를 참고하십시오.

#### [kotlin/07-4-delete.md](docs/manual/ko/kotlin/07-4-delete.md)

**상태: 구현됨, 테스트함**(H2 2.3, MariaDB 10.11, Oracle 23.26, 마일스톤 M10). 테스트는 대부분 **Java** 람다를 사용합니다. `not exists` 서브쿼리를 쓰는 delete는 Kotlin 람다로도 테스트했습니다([`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt), **(테스트됨)** 표시). `limit`과 `executeOne()`(결정 D41)도 같습니다.
`where` 안의 서브쿼리(`sub`)도 구현되어 Java 람다로 테스트했습니다(마일스톤 M8).
**아직 구현되지 않음:** 복합 키 엔티티의 `limit` / `executeOne()`.
Kotlin 코드는 테스트된 Java 문장을 Kotlin 문법으로 옮긴 것이고, HQL은 [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)와 [`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java)의 Java 테스트에서 가져왔습니다. HQL이 없는 예제는 설계를 따릅니다.
전체 상태는 [트랙 README](docs/manual/ko/kotlin/README.md)를 참고하십시오.

#### [kotlin/07-5-insert.md](docs/manual/ko/kotlin/07-5-insert.md)

**상태: 구현됨, 테스트함**(H2 2.3, MariaDB 10.11, Oracle 23.26, 마일스톤 M10). `insert ... select`의 `groupBy`, `having`, `Agg`(마일스톤 M9)도 포함합니다. 테스트는 대부분 **Java** 람다를 사용합니다. `values`를 쓰는 insert는 Kotlin 람다로도 테스트했습니다([`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt), **(테스트됨)** 표시).
Kotlin 코드는 테스트된 Java 문장을 Kotlin 문법으로 옮긴 것이고, HQL은 [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)와 [`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java)의 Java 테스트에서 가져왔습니다. HQL이 없는 예제는 설계를 따릅니다.
전체 상태는 [트랙 README](docs/manual/ko/kotlin/README.md)를 참고하십시오.

#### [kotlin/07-6-upsert.md](docs/manual/ko/kotlin/07-6-upsert.md)

**상태: 구현됨, 테스트함**(H2 2.3, MariaDB 10.11, Oracle 23.26, 마일스톤 M10, 결정 D40). 테스트는 **Java** 람다를 사용합니다. **upsert의 Kotlin 람다는 아직 테스트하지 않았습니다**(update, insert, delete는 테스트함, [7. 수정, 삽입, 삭제](docs/manual/ko/kotlin/07-dml.md) 참고).
Kotlin 코드는 테스트된 Java 문장을 Kotlin 문법으로 옮긴 것이고, HQL은 [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)의 Java 테스트에서 가져왔습니다. HQL이 없는 예제는 설계를 따릅니다.
전체 상태는 [트랙 README](docs/manual/ko/kotlin/README.md)를 참고하십시오.

#### [kotlin/07-dml.md](docs/manual/ko/kotlin/07-dml.md)

**상태**
- **구현됨, 테스트함:** H2 2.3, MariaDB 10.11, Oracle 23.26(Hibernate 6.6.5, 마일스톤 M10, 결정 D28·D40). 대상은 update, delete, insert, `insert ... select`, upsert, update와 delete의 `limit`, 1행 보호 `executeOne()`(결정 D41)입니다. 테스트는 대부분 **Java** 람다를 사용합니다.
- **Kotlin 람다로 테스트함**([`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt), 빌드에 포함, H2, MariaDB, Oracle): `set`의 속성 대입, `Dml.value`, `set` 값 안의 서브쿼리, `values`를 쓰는 `insert`, `not exists` 서브쿼리를 쓰는 `delete`. 이 예제에는 **(테스트됨)** 표시가 있습니다.
- **함께 구현됨, 테스트함(Java 람다로):** 수정·삭제 `where` 안의 서브쿼리(`sub`)(마일스톤 M8, [7.4](docs/manual/ko/kotlin/07-4-delete.md) 참고), `insert ... select`의 `groupBy`, `having`, `Agg`(마일스톤 M9, [7.5](docs/manual/ko/kotlin/07-5-insert.md) 참고).
- **아직 구현되지 않음:** 복합 키 엔티티의 `limit` / `executeOne()`.
- 이 장의 Kotlin 코드는 테스트된 Java 문장을 Kotlin 문법으로 옮긴 것입니다. HQL은 Java 테스트에서 가져왔습니다. HQL이 없는 예제는 설계를 따릅니다.
- 전체 상태는 [트랙 README](docs/manual/ko/kotlin/README.md)를 참고하십시오.

#### [kotlin/08-procedures-functions.md](docs/manual/ko/kotlin/08-procedures-functions.md)

**상태**
- **구현됨**(결정 D30, D31, 마일스톤 M11, Hibernate 6.6.5). 테스트는 **Java** 람다로 작성했습니다.
쿼리 안의 DB 함수(`@SqlFunction`)는 H2 2.3, MariaDB 10.11, Oracle 23.26에서 테스트했습니다([`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java)).
프로시저 호출(`@SqlProcedure`, `@InOut`, `qb.call`, `qb.exec`)은 MariaDB와 Oracle에서, DB 함수 단독 호출은 세 데이터베이스 모두에서 테스트했습니다([`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java)).
lambdaQL은 Kotlin 형태(`@JvmStatic`을 붙인 `companion object` 함수, 결과로 쓰는 Kotlin data class)도 읽습니다. 이 장의 Kotlin 테스트는 아직 없습니다.
- **추가로 구현됨(MariaDB, Oracle에서 Java로 테스트함):** 데이터베이스와의 스텁 인자 **이름** 대조(규칙 PR2의 일부), Oracle 패키지 오버로드 검출.
- **추가로 구현됨(Java 람다로 테스트함):** 조건 안의 DB 함수와 컬럼 산술에 대한 SARGable 경고, 엄격 모드 `SargableMode.STRICT`(규칙 PR8, FN7, 결정 D50, [8.9](docs/manual/ko/kotlin/08-procedures-functions.md#89-warning-functions-on-columns-and-indexes-sargable) 참고).
- **아직 구현되지 않음:** 데이터베이스와의 파라미터 **타입** 대조(규칙 PR2의 일부), Oracle 패키지 프로시저의 오버로드 하나를 골라 호출하는 기능.
- 이 장의 Kotlin 코드는 테스트한 Java 호출을 Kotlin 문법으로 옮긴 것입니다. 테스트한 Java 코드는 [이 페이지의 Java 버전](docs/manual/ko/java/08-procedures-functions.md)에 있습니다. 전체 상태는 [트랙 README](docs/manual/ko/kotlin/README.md)를 참고하십시오.

**구현됨, Java 람다로 테스트함**(H2, MariaDB 10.11, Oracle 23.26, 규칙 PR8, FN7). 엄격 모드(`SargableMode.STRICT`)와 컬럼 산술의 경고(`LQL-5012`, `LQL-5013`)도 구현됨(결정 D50).

#### [kotlin/09-kotlin-notes.md](docs/manual/ko/kotlin/09-kotlin-notes.md)

**상태: 일부 구현됨.**
Kotlin 지원은 확정된 설계입니다(결정 D17, D20 ~ D25, D27, D28, D31).
이 장에 나오는 모든 형태의 바이트코드는 kotlinc
1.7.22, 2.1.10, 2.2.21, 2.3.0, 2.3.21로 컴파일하여 확인했습니다. 많은 조건은 H2에서 행 단위로도 확인했습니다.
**빌드 안에서 Kotlin 람다로 테스트함**(kotlinc 2.3.21, H2, MariaDB, Oracle): `?.` 경로, `!!`, 캡처 값, 캡처 컬렉션의 `in`, 서브쿼리(`exists`, `in`, 스칼라), `limit`이 있는 정렬, 데이터 클래스·`Pair`·`Triple`을 쓰는 `select`, `listOf` 키의 `groupBy`, `having`, update의 속성 대입과 `Dml.value`, `set` 값 안의 서브쿼리, insert, delete([`KotlinSelectTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinSelectTest.kt), [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)).
**설계됨, 아직 테스트하지 않음:** 이 장의 나머지 형태(예: `?:`, 범위, `when`, 속성 참조, 문자열 템플릿). 컴파일러 버전 매트릭스(kotlinc 1.7.22 ~ 2.3.0)는 아직 빌드에 없습니다. 전체 상태는 [트랙 README](docs/manual/ko/kotlin/README.md)를 참고하십시오.

로드맵에는 일반적인 `LIKE` 지원(Java `startsWith` 등)이 v0.2로 잡혀 있습니다.
위의 Kotlin 형태는 설계되어 있지만, 그 작업과 함께 다른 Kotlin 형태보다 늦게 출시될 수 있습니다.

#### [kotlin/10-rules-and-limits.md](docs/manual/ko/kotlin/10-rules-and-limits.md)

**상태:** 일부 구현됨.
이 장은 Kotlin용 lambdaQL 설계의 규칙을 모은 것입니다. 람다가 SQL이 되는 방법, NULL이 동작하는 방법,
Kotlin과 SQL이 다른 점, 데이터베이스마다 다른 점, 지원하지 않는 것을 다룹니다.
규칙은 H2 2.3, MariaDB 10.11, Oracle 23ai(Hibernate 6.6.5)에서 프로토타입으로 확인했고, Kotlin 바이트코드는 kotlinc 1.7.22 ~ 2.3.21로 확인했습니다.
현재는 분석 코어, `where`, 결과 함수(`toList()`, `toSet()`, `first()`, `one()`, `count()`, `exists()`), 정렬과 페이징의 핵심 기능, `SortSpec`, update/insert/delete/upsert([7](docs/manual/ko/kotlin/07-dml.md)), Spring Data JPA 모듈([12](docs/manual/ko/kotlin/12-spring-data-jpa.md))이 동작합니다(H2, MariaDB, Oracle에서 Java 람다로 테스트함).
서브쿼리([5](docs/manual/ko/kotlin/05-subquery.md))와 집계를 포함한 `select`([6](docs/manual/ko/kotlin/06-select-group.md)) 규칙도 DML을 포함해 구현되었습니다. 명시적 조인, 파생 테이블, CTE([6](docs/manual/ko/kotlin/06-select-group.md)), 프로시저와 DB 함수([8](docs/manual/ko/kotlin/08-procedures-functions.md)) 규칙도 구현되었습니다. 컬럼에 대한 DB 함수의 경고([10.2](docs/manual/ko/kotlin/10-rules-and-limits.md#db-functions-on-a-column))도 구현되었습니다. 정렬 규칙 중에도 아직 구현되지 않은 것이 있습니다([4.12](docs/manual/ko/kotlin/04-order-paging.md#412-rules-summary-s1-to-s14) 참고).
전체 상태는 [트랙 README](docs/manual/ko/kotlin/README.md)를 참고하십시오.

#### [kotlin/12-spring-data-jpa.md](docs/manual/ko/kotlin/12-spring-data-jpa.md)

**상태: 조회 실행기와 Spring Boot 자동 구성 구현됨.**
`LambdaQlExecutor<T>`, Spring `Sort` / `Pageable` 지원(정렬 정책 포함), `QueryBuilder` 빈은 H2, MariaDB 10.11, Oracle 23.26에서 Spring Boot 3.4.2로 테스트했습니다. 테스트는 **Java**로 작성되어 있습니다. **Kotlin 리포지토리는 아직 테스트하지 않았습니다.**
수정, 삭제, 삽입, 업서트 메서드도 Java로 테스트했습니다. 대상은 단축 메서드(`deleteAll`, `deleteOne`, `updateAll`, `updateOne`, `insertOne`, `insertAll`, `upsertOne`, `upsertAll`)와 빌더(`updateQuery()`, `deleteQuery()`, `insertQuery()`, `upsertQuery()`)입니다. 이 메서드에는 호출자의 트랜잭션이 필요합니다. lambdaQL은 `@Transactional`을 붙이지 않습니다(결정 D41). 단축 메서드의 Kotlin 형태는 kotlinc 2.3.21로 컴파일만 확인했습니다. 빌드 안에 Kotlin 테스트는 아직 없습니다.
**아직 검증하지 않음:** Spring Boot 4.
이 페이지의 Kotlin 코드는 Java 테스트와 같은 호출을 Kotlin 문법으로 옮긴 것입니다. 테스트한 Java 코드는 [이 페이지의 Java 버전](docs/manual/ko/java/12-spring-data-jpa.md)에 있습니다.
전체 상태는 [Kotlin 트랙 README](docs/manual/ko/kotlin/README.md)를 참고하십시오.

#### [kotlin/README.md](docs/manual/ko/kotlin/README.md)

**상태:** lambdaQL은 초기 개발 단계입니다.
**분석 코어**, **결과 함수를 포함한 `where`**(`toList()`, `toSet()`, `first()`, `one()`, `count()`, `exists()`), 그리고 **정렬과 페이징**(`orderBy`, `limit`, `offset`, `SortSpec`)의 핵심 기능이 구현되어 있습니다. lambdaQL은 람다를 로드하고, 디코딩하고, HQL을 쓰고, 실행합니다. 이 부분은 H2, MariaDB, Oracle에서 주로 **Java** 람다로 테스트했습니다.
**Kotlin 람다는 빌드 안에서 테스트합니다**(kotlinc 2.3.21, [`KotlinSelectTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinSelectTest.kt), [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)). 대상은 `?.` 경로, `!!`, 캡처 값, `in`, 정렬, 서브쿼리, 데이터 클래스·`Pair`·`Triple`을 쓰는 `select`, `groupBy`와 `having`, update, insert, delete입니다. 나머지 Kotlin 전용 형태(`?:`, 범위, `when`, 속성 참조)는 **설계** 단계입니다. 이 형태의 바이트코드는 프로토타입으로 컴파일하여 확인했습니다(kotlinc 1.7.22 ~ 2.3.21).
**서브쿼리**, **`select`**(`groupBy`, `having`, `Agg` 포함), **수정, 삽입, 삭제, 업서트**(`limit`, 1행 보호 `executeOne()` 포함), **명시적 조인**, **파생 테이블과 CTE**, **저장 프로시저와 DB 함수**, **4개 언어의 오류 코드**가 구현되어 있습니다. 조인, 파생 테이블, CTE, 프로시저, DB 함수, 업서트는 Java 람다로만 테스트했습니다.
**Spring Data JPA 모듈**(`LambdaQlExecutor<T>`, Spring `Sort`와 `Pageable`, `deleteAll { }`·`updateOne({ }) { }` 같은 DML 단축 메서드, 수정·삭제·삽입·업서트 빌더, Spring Boot 자동 구성)은 구현되어 Java 리포지토리로 테스트했습니다(Spring Boot 3.4.2). DML에는 호출자의 트랜잭션이 필요합니다. Kotlin 리포지토리는 아직 테스트하지 않았습니다.
아직 Maven Central에 배포된 아티팩트는 없습니다. 사용해 보려면 [소스에서 빌드](docs/manual/ko/kotlin/02-getting-started.md#build-from-source)하십시오.

### 트랙 README의 상태 표

2026-11-19에 매뉴얼 홈과 Java 트랙 README의 "상태" 절, Kotlin 트랙 README 목차의 "상태" 열을 옮겼다.

#### [README.md](docs/manual/ko/README.md)


| 기능 | 상태 | Java | Kotlin |
|---|---|---|---|
| 람다 로딩(람다 본문을 찾고 바이트코드를 읽음) | 구현됨, 단위 테스트 있음 | [01](docs/manual/ko/01-overview.md#how-it-works-5-steps) | [01](docs/manual/ko/01-overview.md#how-it-works-5-steps) |
| 조건 디코딩(`&&`, `\|\|`, `!`, 비교, 캡처 값) | 구현됨, 단위 테스트 있음(Java 람다) | [03](docs/manual/ko/java/03-where.md) | [03](docs/manual/ko/kotlin/03-where.md) |
| getter를 JPA 속성에 매핑, 캡처 값 계산 | 구현됨, H2·MariaDB·Oracle에서 테스트함 | [03](docs/manual/ko/java/03-where.md) | [03](docs/manual/ko/kotlin/03-where.md) |
| HQL 렌더링과 쿼리 실행(`where`, `toList()`) | 구현됨, H2·MariaDB·Oracle에서 테스트함 | [03](docs/manual/ko/java/03-where.md) | [03](docs/manual/ko/kotlin/03-where.md) |
| 결과 함수(`first()`, `one()`, `count()`, `exists()`, `toSet()`) | 구현됨, H2·MariaDB·Oracle에서 테스트함(Java 람다) | [02](docs/manual/ko/java/02-getting-started.md#getting-the-result) | [02](docs/manual/ko/kotlin/02-getting-started.md#getting-the-result) |
| 정렬과 페이징(`orderBy`, `limit`, `offset`) | 핵심 기능 구현됨, H2·MariaDB·Oracle에서 테스트함. `SortSpec`, 조인 정렬 `(o, c, by)`, 외부 정렬의 정렬 정책(코드, Spring Boot 프로퍼티, `@Sortable` 규칙) 포함. `SortSpec`의 조인 형태: 설계됨 | [04](docs/manual/ko/java/04-order-paging.md) | [04](docs/manual/ko/kotlin/04-order-paging.md) |
| 서브쿼리(`exists`, `contains`, 스칼라, 상관) | 구현됨, H2·MariaDB·Oracle에서 테스트함(Java 람다). MariaDB `lateral` 재작성: 아직 구현되지 않음 | [05](docs/manual/ko/java/05-subquery.md) | [05](docs/manual/ko/kotlin/05-subquery.md) |
| 프로젝션, `groupBy`, `having`, 집계(`Agg`) | 구현됨, H2·MariaDB·Oracle에서 테스트함(Java 람다). `distinct()`와 결과 함수 포함. 집계 기준 정렬: v0.2 후보 | [06](docs/manual/ko/java/06-select-group.md) | [06](docs/manual/ko/kotlin/06-select-group.md) |
| 명시적 조인(`join`, `leftJoin`, `crossJoin`, 엔티티 3개까지) | 구현됨, H2·MariaDB·Oracle에서 테스트함(Java 람다). 모든 엔티티에 대한 `where`, `orderBy`, `select`, 페이징 포함. 조인 결과의 `distinct()`, 파생 테이블·CTE의 조인: 구현됨(D51). 서브쿼리 안의 조인(엔티티 2개): 구현됨. update와 delete의 조인: 추가하지 않음 | [06](docs/manual/ko/java/06-select-group.md#68-explicit-joins) | [06](docs/manual/ko/kotlin/06-select-group.md#68-explicit-joins) |
| 파생 테이블과 CTE(`qb.from(valueQuery)`, `qb.with(valueQuery)`) | 구현됨, H2·MariaDB·Oracle에서 테스트함(Java 람다). 엔티티 쿼리에 조인, 같은 CTE의 재사용: 구현됨(D51) | [06](docs/manual/ko/java/06-select-group.md#69-derived-table-subquery-in-from) | [06](docs/manual/ko/kotlin/06-select-group.md#69-derived-table-subquery-in-from) |
| `update`, `insert`, `delete`, upsert | 구현됨. H2, MariaDB, Oracle에서 테스트(Java 람다). `limit`과 `executeOne()` 포함. 수정·삭제 `where` 안의 서브쿼리(`sub`)와 `insert ... select`의 집계: 구현됨. `set` 값, insert `values`, upsert 안의 서브쿼리: 구현됨 | [07](docs/manual/ko/java/07-dml.md) | [07](docs/manual/ko/kotlin/07-dml.md) |
| 저장 프로시저와 DB 함수 | 구현됨. 쿼리 안의 DB 함수와 단독 호출은 H2·MariaDB·Oracle에서 테스트함. 프로시저(`call`, `exec`, OUT, INOUT, 결과 집합)는 MariaDB와 Oracle에서 테스트함(H2에는 OUT 파라미터가 없음). 인자 이름 대조(MariaDB, Oracle에서 테스트함)와 Oracle 패키지 오버로드 검출(Oracle에서 테스트함): 구현됨. DB 함수의 SARGable 경고(`LQL-5001`, `LQL-5007`): 구현됨. 프로시저 파라미터의 타입 대조: 구현됨(MariaDB, Oracle에서 테스트함) | [08](docs/manual/ko/java/08-procedures-functions.md) | [08](docs/manual/ko/kotlin/08-procedures-functions.md) |
| Kotlin 형태(`?.`, `?:`, `!!`, 범위, `when`, `in`) | `?.`, `!!`, 캡처 컬렉션의 `in`, 데이터 클래스, `Pair`/`Triple`, 속성 대입: H2·MariaDB·Oracle에서 Kotlin 람다로 테스트함. `?:`, 범위, `when`, 속성 참조: 설계됨 | – | [09](docs/manual/ko/kotlin/09-kotlin-notes.md) |
| 4개 언어의 오류 코드와 메시지 | 구현됨, 단위 테스트함: `[LQL-xxxx]` 코드, `LambdaQLException`과 하위 클래스, 영어·일본어·중국어·한국어 메시지, `lambdaql.locale`. 엄격 모드: 아직 구현되지 않음 | [11](docs/manual/ko/11-errors-and-languages.md) | [11](docs/manual/ko/11-errors-and-languages.md) |
| Spring Data JPA(`LambdaQlExecutor<T>`, Spring `Sort` / `Pageable`, Spring Boot 자동 구성) | 구현됨. H2, MariaDB, Oracle에서 Spring Boot 3.4.2로 테스트(Java). DML 단축 메서드(`deleteAll`, `updateOne` 등)와 수정·삭제·삽입·업서트 빌더 포함. Spring Boot 4: 아직 검증하지 않음 | [12](docs/manual/ko/java/12-spring-data-jpa.md) | [12](docs/manual/ko/kotlin/12-spring-data-jpa.md) |

용어의 뜻:

| 용어 | 뜻 |
|---|---|
| 구현됨 | 코드가 저장소에 있고 단위 테스트가 있습니다. |
| 진행 중 | 일부 코드가 있지만 완성되지 않았습니다. |
| 설계됨 | 문법과 규칙이 확정되었습니다. 프로토타입으로 테스트했습니다. 라이브러리에는 아직 코드가 없습니다. |
| 계획됨 | 아이디어는 있지만 세부 사항은 정해지지 않았습니다. |

테스트는 다음과 같습니다. `SelectQueryTest`, `SortQueryTest`, `SubqueryTest`, `ProjectionTest`, `JoinQueryTest`, `DerivedQueryTest`, `DmlQueryTest`, `FunctionTest`, `ProcedureTest`, `KotlinSelectTest`, `KotlinQueryTest`, `LqlMessagesTest`, `ExecutorTest`, `AutoConfigurationTest`는 사용자 API를 사용하고, 나머지 테스트는 내부 클래스를 확인합니다.

| 테스트 | 확인하는 내용 |
|---|---|
| [SelectQueryTest.java](query/src/test/java/org/lambdaql/query/SelectQueryTest.java) | `qb.from(...).where(...)`: H2, MariaDB, Oracle에서의 HQL, 파라미터, 행 |
| [SortQueryTest.java](query/src/test/java/org/lambdaql/query/SortQueryTest.java) | `orderBy`, `limit`, `offset`: H2, MariaDB, Oracle에서의 HQL과 행 순서 |
| [SubqueryTest.java](query/src/test/java/org/lambdaql/query/SubqueryTest.java) | `where` 안의 서브쿼리: `exists`, `contains`(IN), NOT EXISTS, 스칼라, top-1, 중첩, 서브쿼리를 쓰는 삭제. H2, MariaDB, Oracle에서의 HQL과 행 |
| [ProjectionTest.java](query/src/test/java/org/lambdaql/query/ProjectionTest.java) | `select`, 레코드, `groupBy`, `having`, `Agg`, `distinct`, 페이징, `groupBy`를 쓰는 `insert ... select`. H2, MariaDB, Oracle에서의 HQL과 값 |
| [JoinQueryTest.java](query/src/test/java/org/lambdaql/query/JoinQueryTest.java) | 엔티티 3개까지의 `join`, `leftJoin`, `crossJoin`: H2, MariaDB, Oracle에서의 조건, 정렬, `select`, 반복되는 행, `count`, 모든 엔티티에 대한 페이징 |
| [DerivedQueryTest.java](query/src/test/java/org/lambdaql/query/DerivedQueryTest.java) | 파생 테이블과 CTE(`qb.from(valueQuery)`, `qb.with(valueQuery)`): H2, MariaDB, Oracle에서의 HQL과 행, 안쪽 `limit`, 행 타입 규칙 J5 |
| [DmlQueryTest.java](query/src/test/java/org/lambdaql/query/DmlQueryTest.java) | `update`, `delete`, `insert`, upsert: H2, MariaDB, Oracle에서의 HQL과 실행 후의 행. 같은 조건의 조회 결과와 비교 |
| [FunctionTest.java](query/src/test/java/org/lambdaql/query/FunctionTest.java) | `where`, `select`, `orderBy`, update `set` 안의 `@SqlFunction` DB 함수, Boolean 함수, 등록한 함수, 규칙 검사: H2, MariaDB, Oracle |
| [ProcedureTest.java](query/src/test/java/org/lambdaql/query/ProcedureTest.java) | MariaDB와 Oracle에서 `qb.call`과 `qb.exec`로 하는 `@SqlProcedure` 호출(OUT, OUT 여러 개, INOUT, 결과 집합과 REF CURSOR)과 스텁 대조. H2, MariaDB, Oracle에서 DB 함수 단독 호출 |
| [KotlinSelectTest.kt](query/src/test/kotlin/org/lambdaql/query/KotlinSelectTest.kt) | `where`의 Kotlin 람다: `?.` 경로, 캡처 값, 캡처 컬렉션의 `in` |
| [KotlinQueryTest.kt](query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt) | Kotlin 람다: 정렬, 서브쿼리, 데이터 클래스·`Pair`·`Triple` 결과, `groupBy`와 `having`, update(속성 대입, `Dml.value`, `set` 값 안의 서브쿼리), insert, delete |
| [LqlMessagesTest.java](query/src/test/java/org/lambdaql/i18n/LqlMessagesTest.java) | 메시지 코드와 4개 메시지 언어: 모든 번들의 같은 키와 인자, 코드 중복 없음, 로캘 선택, 코드 범위별 예외 클래스 |
| [ExecutorTest.java](spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java) | Spring Boot 애플리케이션의 `LambdaQlExecutor<T>`: 리포지토리 메서드, Spring `Sort`와 `Pageable`, 트랜잭션 |
| [AutoConfigurationTest.java](spring-data-jpa/src/test/java/org/lambdaql/spring/AutoConfigurationTest.java) | Spring Boot 자동 구성의 `QueryBuilder` 빈 |
| [LoadExamplesTest.java](query/src/test/java/org/lambdaql/engine/load/LoadExamplesTest.java) | 람다 본문 찾기, 캡처 값 읽기, 직렬화할 수 없는 람다 거부 |
| [ConditionDecodeTest.java](query/src/test/java/org/lambdaql/engine/decode/ConditionDecodeTest.java) | 바이트코드에서 `&&`, `\|\|`, `!`와 비교를 다시 만들기, 값 삼항 연산자 거부 |
| [IrExamplesTest.java](query/src/test/java/org/lambdaql/engine/ir/IrExamplesTest.java) | 내부 조건 트리(비교 연산자, AND, OR, NOT) |
| [NegationMinimizerTest.java](query/src/test/java/org/lambdaql/engine/decode/NegationMinimizerTest.java) | 결과를 바꾸지 않고 `!`를 비교 안으로 밀어 넣기(드 모르간) |
| [HostEvaluatorTest.java](query/src/test/java/org/lambdaql/engine/sem/HostEvaluatorTest.java) | 바인딩 전에 캡처 변수로 Java에서 값 계산 |
| [JpaEntityModelTest.java](query/src/test/java/org/lambdaql/engine/sem/JpaEntityModelTest.java) | JPA Metamodel에서 엔티티 속성 읽기(getter 매핑을 위한 초기 코드) |
| [AssignmentReaderTest.java](query/src/test/java/org/lambdaql/engine/sem/AssignmentReaderTest.java) | 대입 람다(setter, `Dml.value`)를 컬럼과 값의 쌍으로 읽기, 규칙 위반(U1, U3, U6) 거부 |


#### [java/README.md](docs/manual/ko/java/README.md)


| 기능 | 상태 | 장 |
|---|---|---|
| 람다 로딩(람다 본문을 찾고 바이트코드를 읽음) | 구현됨, 단위 테스트 있음 | [1](docs/manual/ko/01-overview.md) |
| 조건 디코딩(`&&`, `\|\|`, `!`, 비교, 캡처 값) | 구현됨, 단위 테스트 있음 | [3](docs/manual/ko/java/03-where.md) |
| getter를 JPA 속성에 매핑 | 구현됨, H2·MariaDB·Oracle에서 테스트함 | [3](docs/manual/ko/java/03-where.md) |
| HQL 렌더링과 쿼리 실행(`where`, `toList()`) | 구현됨, H2·MariaDB·Oracle에서 테스트함 | [3](docs/manual/ko/java/03-where.md) |
| 결과 함수(`first()`, `one()`, `count()`, `exists()`, `toSet()`) | 구현됨, H2·MariaDB·Oracle에서 테스트함 | [2](docs/manual/ko/java/02-getting-started.md#getting-the-result) |
| 정렬과 페이징(`orderBy`, `limit`, `offset`) | 핵심 기능 구현됨, H2·MariaDB·Oracle에서 테스트함. `SortSpec`, 조인 정렬 `(o, c, by)`, 외부 정렬의 정렬 정책(코드, Spring Boot 프로퍼티, `@Sortable` 규칙) 포함. `SortSpec`의 조인 형태: 설계됨 | [4](docs/manual/ko/java/04-order-paging.md) |
| 서브쿼리(`exists`, `contains`, 스칼라, 상관) | 구현됨, H2·MariaDB·Oracle에서 테스트함. MariaDB `lateral` 재작성: 아직 구현되지 않음 | [5](docs/manual/ko/java/05-subquery.md) |
| 프로젝션, `groupBy`, `having`, 집계(`Agg`) | 구현됨, H2·MariaDB·Oracle에서 테스트함. `distinct()`와 결과 함수 포함. 집계 기준 정렬: v0.2 후보 | [6](docs/manual/ko/java/06-select-group.md) |
| 명시적 조인(`join`, `leftJoin`, `crossJoin`, 엔티티 3개까지) | 구현됨, H2·MariaDB·Oracle에서 테스트함. 모든 엔티티에 대한 `where`, `orderBy`, `select`, 페이징 포함. 조인 결과의 `distinct()`, 파생 테이블·CTE의 조인: 구현됨(D51). 서브쿼리 안의 조인(엔티티 2개): 구현됨. update와 delete의 조인: 추가하지 않음 | [6](docs/manual/ko/java/06-select-group.md#68-explicit-joins) |
| 파생 테이블과 CTE(`qb.from(valueQuery)`, `qb.with(valueQuery)`) | 구현됨, H2·MariaDB·Oracle에서 테스트함. 엔티티 쿼리에 조인, 같은 CTE의 재사용: 구현됨(D51) | [6](docs/manual/ko/java/06-select-group.md#69-derived-table-subquery-in-from) |
| `update`, `insert`, `delete`, upsert | 구현됨. H2, MariaDB, Oracle에서 테스트. `limit`과 `executeOne()` 포함. 수정·삭제 `where` 안의 서브쿼리(`sub`)와 `insert ... select`의 집계: 구현됨. `set` 값, insert `values`, upsert 안의 서브쿼리: 구현됨 | [7](docs/manual/ko/java/07-dml.md) |
| 저장 프로시저와 DB 함수 | 구현됨. 쿼리 안의 DB 함수와 단독 호출은 H2·MariaDB·Oracle에서 테스트함. 프로시저(`call`, `exec`, OUT, INOUT, 결과 집합)는 MariaDB와 Oracle에서 테스트함(H2에는 OUT 파라미터가 없음). 인자 이름 대조(MariaDB, Oracle에서 테스트함)와 Oracle 패키지 오버로드 검출(Oracle에서 테스트함): 구현됨. DB 함수의 SARGable 경고(`LQL-5001`, `LQL-5007`): 구현됨. 프로시저 파라미터의 타입 대조: 구현됨(MariaDB, Oracle에서 테스트함) | [8](docs/manual/ko/java/08-procedures-functions.md) |
| 4개 언어의 오류 코드와 메시지 | 구현됨, 단위 테스트함: `[LQL-xxxx]` 코드, `LambdaQLException`과 하위 클래스, 영어·일본어·중국어·한국어 메시지, `lambdaql.locale`. 엄격 모드: 아직 구현되지 않음 | [11](docs/manual/ko/11-errors-and-languages.md) |
| Spring Data JPA(`LambdaQlExecutor<T>`, Spring `Sort` / `Pageable`, Spring Boot 자동 구성) | 구현됨. H2, MariaDB, Oracle에서 Spring Boot 3.4.2로 테스트. DML 단축 메서드(`deleteAll`, `updateOne` 등)와 수정·삭제·삽입·업서트 빌더 포함. Spring Boot 4: 아직 검증하지 않음 | [12](docs/manual/ko/java/12-spring-data-jpa.md) |

용어의 뜻:

| 용어 | 뜻 |
|---|---|
| 구현됨 | 코드가 저장소에 있고 단위 테스트가 있습니다. |
| 진행 중 | 일부 코드가 있지만 완성되지 않았습니다. |
| 설계됨 | 문법과 규칙이 확정되었습니다. 프로토타입으로 테스트했습니다. 라이브러리에는 아직 코드가 없습니다. |
| 계획됨 | 아이디어는 있지만 세부 사항은 정해지지 않았습니다. |


#### [kotlin/README.md](docs/manual/ko/kotlin/README.md)

| # | 페이지 | 배우는 내용 | 상태 |
|---|---|---|---|
| 1 | [개요](docs/manual/ko/01-overview.md) (공통) | lambdaQL이 무엇인지, 왜 필요한지, 어떻게 동작하는지 | – |
| 2 | [시작하기](docs/manual/ko/kotlin/02-getting-started.md) | 요구 사항, Gradle 설정, Kotlin 엔티티, 첫 쿼리 | 소스에서 빌드는 동작함. `where`, `toList()`, 결과 함수 `first()`, `one()`, `count()`, `exists()`, `toSet()`: 구현됨, H2·MariaDB·Oracle에서 테스트함(`where`와 `toList()`는 Kotlin 람다로도 테스트함. 결과 함수는 Java 람다로만 테스트함) |
| 3 | [조건(`where`)](docs/manual/ko/kotlin/03-where.md) | 연산자, null 규칙, `?.` `?:` `!!`, 범위, `when`, 캡처 값 | getter를 JPA 속성에 매핑, HQL 렌더링과 쿼리 실행(`where`, `toList()`): 구현됨, H2·MariaDB·Oracle에서 테스트함. `?.`, `!!`, 캡처 값, `in`: Kotlin 람다로 테스트함. `?:`, 범위, `when`: 설계됨 |
| 4 | [정렬과 페이징](docs/manual/ko/kotlin/04-order-paging.md) | `orderBy`, `Direction`, `limit`, `offset` | 핵심 기능 구현됨, H2·MariaDB·Oracle에서 테스트함(Kotlin 테스트 하나, 나머지는 Java 테스트). `SortSpec`, 조인 정렬 `{ o, c, by -> }`, 외부 정렬의 정렬 정책(코드, Spring Boot 프로퍼티, `@Sortable` 규칙) 포함. 속성 참조, `SortSpec`의 조인 형태: 설계됨 |
| 5 | [서브쿼리](docs/manual/ko/kotlin/05-subquery.md) | `exists()`, `x in sub...`, 스칼라 서브쿼리와 상관 서브쿼리, 서브쿼리 안의 조인 | 구현됨, H2·MariaDB·Oracle에서 테스트함(`exists`, `in`, 스칼라, 서브쿼리 안의 조인은 Kotlin 람다로도 테스트함). MariaDB `lateral` 재작성: 아직 구현되지 않음 |
| 6 | [조회, 집계, 그룹화](docs/manual/ko/kotlin/06-select-group.md) | `select`, data class, `Pair`, `groupBy`, `having`, `Agg`, 조인, 파생 테이블, CTE | `select`, `groupBy`, `having`, `Agg`, `distinct()`: 구현됨, H2·MariaDB·Oracle에서 테스트함(데이터 클래스, `Pair`, `Triple`, `groupBy`, `having`은 Kotlin 람다로도 테스트함). 명시적 조인, 파생 테이블, CTE: 구현됨(Java 테스트, Kotlin은 아직 테스트하지 않음) |
| 7 | [수정, 삽입, 삭제](docs/manual/ko/kotlin/07-dml.md) | 속성 대입, `Dml.value`, `values`, `all()`, `execute()`, `limit`, `executeOne()` | 구현됨, H2·MariaDB·Oracle에서 테스트함. `limit`과 `executeOne()` 포함(속성 대입, `Dml.value`, insert, delete는 Kotlin 람다로도 테스트함). update와 delete의 `where` 안 서브쿼리(`sub`), `insert ... select`의 집계: 구현됨. `set` 값, insert `values`, upsert 안의 서브쿼리(`sub`): 구현됨 |
| 7.6 | [업서트](docs/manual/ko/kotlin/07-6-upsert.md) | `upsert`, `onConflict { ... }.update { ... }`, `onConflict { ... }.ignore()`, 데이터베이스별 행 수 | 구현됨. H2, MariaDB, Oracle에서 테스트(Java 테스트, Kotlin은 아직 테스트하지 않음) |
| 8 | [프로시저와 DB 함수](docs/manual/ko/kotlin/08-procedures-functions.md) | `@SqlProcedure`, `companion object` 안의 `@SqlFunction`, `qb.call`, `qb.exec` | 구현됨(Java 테스트, Kotlin은 아직 테스트하지 않음): DB 함수는 H2·MariaDB·Oracle, 프로시저는 MariaDB와 Oracle(인자 이름 대조와 Oracle 패키지 오버로드 검출 포함). DB 함수의 SARGable 경고(`LQL-5001`, `LQL-5007`): 구현됨. 프로시저 파라미터의 타입 대조: 구현됨(MariaDB, Oracle에서 테스트함) |
| 9 | [Kotlin 노트](docs/manual/ko/kotlin/09-kotlin-notes.md) | Kotlin에서 특별한 점: `?.` `?:` `!!`, `==`, `in`, `when`, 연산자, 제한 KC1 ~ KC21 | 일부 테스트함(`?.`, `!!`, `in`, 데이터 클래스, 속성 대입). 나머지 형태: 설계됨 |
| 10 | [규칙과 제한](docs/manual/ko/kotlin/10-rules-and-limits.md) | 모든 규칙 표를 한곳에 | 일부 구현됨 |
| 11 | [오류와 언어](docs/manual/ko/11-errors-and-languages.md) (공통) | 오류 코드, 예외, 메시지 언어, 로깅 | 구현됨, 단위 테스트함. 엄격 모드: 아직 구현되지 않음 |
| 12 | [Spring Data JPA](docs/manual/ko/kotlin/12-spring-data-jpa.md) | `LambdaQlExecutor<T>`, 본문이 있는 리포지토리 메서드, DML 단축 메서드(`deleteAll`, `updateOne` 등), Spring `Sort`와 `Pageable`, 모듈, 트랜잭션, Kotlin 2.2 / `-Xjvm-default=all` | 구현됨. H2, MariaDB, Oracle에서 Spring Boot 3.4.2로 테스트. DML 단축 메서드와 수정·삭제·삽입·업서트 빌더 포함(Java 테스트, Kotlin 리포지토리는 아직 테스트하지 않음) |

용어의 뜻:

| 용어 | 뜻 |
|---|---|
| 구현됨 | 코드가 저장소에 있고 단위 테스트가 있습니다. |
| 설계됨 | 문법과 규칙이 확정되었습니다. 프로토타입으로 테스트했습니다. 라이브러리에는 아직 코드가 없습니다. |
| 계획됨 | 아이디어는 있지만 세부 사항은 정해지지 않았습니다. |
