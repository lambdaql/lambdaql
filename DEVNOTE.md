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
| 의존성 | `feature/boot4`: Spring Boot BOM 4.1.2(Hibernate ORM 7.4.13), Kotlin 2.3.21(테스트만), ASM 9.11.1(`implementation`: 사용자에게는 런타임 전용, 2026-11-19) |
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

### 6.2 실측 근거의 위치(⚠️ `/tmp`, 재부팅하면 사라질 수 있음)

| 주제 | 경로 |
|---|---|
| 초기 조사(ExTree·FluentJPA·Jinq 소스, **이 안에서 빌드 실행 금지**) | `/tmp/lql-research/` |
| NOT·3값 논리, 정렬 문법, Kotlin 패턴, D20 실측 | `/tmp/lql-notlogic/`, `/tmp/lql-sort/`, `/tmp/lql-kt/`, `/tmp/lql-d20/` |
| DML 의미 실측 | `/tmp/lql-dml/` |

장기 보존이 필요하면 소스(`*.java`, `*.kt`, `*.py`)와 요약(`*.md`)만 저장소로 옮긴다.

### 6.3 개발 도구(`.claude/tools/`, 로컬 전용)

| 도구 | 용도 |
|---|---|
| `i18n/catalog.py`, `i18n/gen.py` | 메시지 카탈로그 생성기(7절) |
| `docs/check_ko.py` | 한국어 매뉴얼 검사(링크, 앵커, 일부 테스트 구간) |
| `docs/check_translation.py <lang>` | 번역 검사(낡음 안내, 앵커, 코드 블록, 남은 한글, 링크) |

`.claude/`는 전역 gitignore(`~/.gitignore_global`)로 git에서 제외하므로 이 도구들은 저장소에 없고 작업 PC에만 있다. 실행 방법은 `.claude/tools/README.md`에 있다. 다른 PC에서 작업하려면 이 폴더를 따로 복사한다.

---

## 7. 메시지 카탈로그 생성기

`LqlMessage.java`와 `LqlMessages{,_ja,_ko,_zh}.properties`는 생성 결과다. 원본은 로컬 전용 `.claude/tools/i18n/catalog.py`(항목: 키, 코드, 인자 수, en, ko, ja, zh)이고 `python3 .claude/tools/i18n/gen.py`로 다시 쓴다. 생성 결과를 직접 고치지 않는다.

- 코드를 추가할 때 3xxx는 `# ---- 4xxx rendering and running` 앞, 4xxx는 `("INTERNAL_ERROR"` 앞, 5xxx는 목록 끝에 넣는다.
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

## 10. Spring Boot 4 브랜치(`feature/boot4`, 2026-11-19 진행 중)

`feature/engine-core`(커밋 `3e671bc`)에서 갈라졌다. 작성자 지시: Boot 4를 지원한 뒤 JDK를 올리고, ASM 대신 JVM 기본 기능(Class-File API, `java.lang.classfile`)으로 구현해 **Boot 4판은 ASM판과 JVM판 두 가지**를 둔다.

### 10.1 Boot 4.1.2로 올린 내용(실측)

- BOM `spring-boot-dependencies:4.1.2`: Hibernate 7.4.13, Spring Framework 7.0.10, Spring Data 2026.0, Jakarta Persistence 3.2, JUnit 6.0.3. 코어 메인 코드는 수정 없이 컴파일됨.
- Spring Data 4: `PropertyPath`, `PropertyReferenceException`이 `org.springframework.data.core`로 이동.
- Boot 4: `@EntityScan`이 `org.springframework.boot.persistence.autoconfigure`로 이동(테스트 25개 파일).
- JUnit 6: `testRuntimeOnly 'org.junit.platform:junit-platform-launcher'`를 명시해야 함(Gradle 내장 런처는 버전이 맞지 않아 테스트 발견 실패).
- Hibernate 7 SQL 차이: 파생 테이블 열 별칭 `t1_0(id)` → `t1_0(c0)`. SQL 정답 파일 6개를 다시 만듦.
- B8(정수 열과 2.5): Hibernate 7은 BigDecimal 타입을 지정한 바인딩도 거부(`QueryArgumentException`). 숫자 리터럴(`e0.cnt >= 2.5BD`, IN은 `(2.5BD, 3.0BD)`)로 바꿈. Hibernate 6.6에서도 동작하는 형태. D37(캡처 값은 바인딩)의 예외다. 매뉴얼의 B8 설명(“BigDecimal로 바인딩”)은 아직 고치지 않았다.
- `HibernateFunctions.tableAndColumn`: Hibernate 7에 없는 `getPropertyColumnNames` 대신 `findAttributeMapping` + `SelectableMapping`(6·7 공통)으로 바꿈.

테스트 결과(2026-11-19): H2 241개 통과, MariaDB 3개 실패, Oracle 1개 실패.

### 10.2 남은 실패

1. **MariaDB UPDATE의 연관 경로(U9)**: `update … where (select s1.name from Customer s1 where s1.id = e0.customer.id) = 'Kim'`를 Hibernate 7이 `from SQ_customers c1_0, SQ_customers c2_0 where c1_0.id = SQ_orders.customer_id`로 만들어 쓰지 않는 `c2_0`가 곱해짐 → "Subquery returns more than 1 row". `DmlQueryTest`(2건)와 `KotlinQueryTest.update`. H2·Oracle은 통과. 후보: 서브쿼리 안의 외부 FK 참조를 `fk(e0.customer)` 등으로 바꿔 실측.
2. **Oracle `FunctionTest.sargableWarnings`**: 함수 기반 인덱스가 있는데도 LQL-5001 경고. `tableAndColumn`을 고친 뒤에도 실패. `user_ind_expressions` 조회나 함수 SQL 이름(`fn.sql()`) 쪽을 Hibernate 7에서 확인해야 함.

### 10.3 ASM판·JVM판: jar 하나(Multi-Release JAR) 결정(D52)

- **결정**: jar 하나. 기본 클래스는 Java 21 + ASM, `META-INF/versions/24/`에 `java.lang.classfile` 리더. JDK 24 이상에서 실행하면 ClassFile 리더를 쓴다.
- **근거**: 성능 차이는 µs 단위라 선택 요인이 아니고(ROADMAP 2.6.1), 실제 문제는 ASM의 버전 추적(2.6.2)이다. ClassFile API는 실행 중인 JDK의 클래스 파일을 항상 읽는다.
- **구현 순서**:
  1. Decoder의 입력을 ASM 방문자에서 떼어 내고, ClassFile 명령어를 같은 입력으로 옮기는 어댑터를 만든다(해석 규칙은 하나).
  2. `query/build.gradle`에 JDK 24 툴체인으로 컴파일하는 `java24` 소스 세트를 두고 jar의 `META-INF/versions/24`에 넣는다(`Multi-Release: true`).
  3. 테스트를 JDK 21(ASM)과 JDK 24 이상(ClassFile)에서 모두 돌린다. 이 PC에는 JDK 21과 26만 있다.
- **최소 JDK**: ClassFile API는 JDK 24에서 정식(JEP 484). 25와의 차이는 확인하지 못해 24부터 지원(미측정).
- **ASM 의존 범위**: `api` → `implementation`(작성자 지시, 2026-11-19). 배포 POM에서 `runtime` 범위라 사용자의 컴파일 클래스패스에는 없다. 새 ASM이 필요하면 사용자가 자기 의존성으로 올릴 수 있다.
- **Gradle**: 9.9.1로 올렸다(2026-11-19). Gradle 9에서 프로젝트 수준 `sourceCompatibility`가 없어져 `java { }` 안으로 옮겼다.

---

## 9. 기능별 구현·테스트 현황 (2026-11-19)

매뉴얼에 있던 상태 노트와 README의 상태 표를 옮겨 현재 기준으로 합쳤다. 매뉴얼은 사용 방법만 다루고, 현황은 이 표에서 관리한다. 옮기기 전 원문은 커밋 `15f691e`, `ad202ed`에 있다.

"세 DB"는 H2 2.3.230, MariaDB 10.11.18, Oracle Free 23.26.3이다. 테스트는 Java 람다가 기본이고, Kotlin 열은 Kotlin 람다로 테스트한 범위다(`KotlinSelectTest.kt`, `KotlinQueryTest.kt`).

| 기능 | 상태 | Kotlin 람다 테스트 | 근거(결정·규칙) |
|---|---|---|---|
| `where`, 열 매핑, HQL 렌더링과 실행 | 구현됨, 세 DB 테스트 | `?.` 경로, 캡처 값, `in`, `!!`, 값 객체 `==` | D36, D37, D48 |
| 결과 함수(`toList`, `toSet`, `first`, `one`, `count`, `exists`) | 구현됨, 세 DB 테스트. 값 쿼리 `first()`의 id 보조 정렬 포함 | 일부 | D38, S9 |
| 정렬과 페이징(`orderBy`, `limit`, `offset`, `SortSpec`, 조인 정렬) | 구현됨, 세 DB 테스트 | 정렬 하나 | D15, D35, S1~S10, A4, A5 |
| 외부 정렬 정책(`SortRules`, `SortPolicy`, `@Sortable`, `lambdaql.sort.*`) | 구현됨, 세 DB 테스트 | 아니오 | D43 |
| 서브쿼리(`exists`, `contains`, 스칼라, 상관, 서브쿼리 안 조인) | 구현됨, 세 DB 테스트 | `exists`, `in`, 스칼라, 서브쿼리 안 조인 | M8, Q1~Q5, D51 |
| `select`, `groupBy`, `having`, `Agg`, `distinct()` | 구현됨, 세 DB 테스트 | data class, `Pair`, `groupBy` | M9, J1~J7 |
| 명시적 조인(`join`, `leftJoin`, `crossJoin`, 엔티티 3개), 조인 결과 `distinct()` | 구현됨, 세 DB 테스트 | 조인 `distinct()` | D44, D45, D51 |
| 파생 테이블·CTE(`qb.from(valueQuery)`, `qb.with`), 엔티티 쿼리에 조인, CTE 재사용 | 구현됨, 세 DB 테스트 | CTE 조인 | M8-2, J5, J6, D51 |
| update·insert·delete, `limit`, `executeOne()` | 구현됨, 세 DB 테스트 | 속성 대입, `Dml.value`, `set` 서브쿼리, insert values 서브쿼리, 자기 테이블 서브쿼리 delete | M10, U1~U21, D41 |
| 대입 표기 `Dml.value`, 대입 람다의 `sub`(set 값, insert values·목록·select, upsert) | 구현됨, 세 DB 테스트. 값 타입 대조(LQL-3065) 포함 | 일부 | D49, U20 |
| upsert(`onConflict(...).update/ignore`) | 구현됨, 세 DB 테스트 | 아니오 | D40 |
| DELETE의 자기 테이블 서브쿼리(MariaDB 1093 회피) | 구현됨, 세 DB 테스트 | 예 | U17 |
| 저장 프로시저(`call`, `exec`, OUT, INOUT, 결과 집합), 인자 이름·타입 종류 대조, Oracle 패키지 오버로드 검출 | 구현됨, MariaDB·Oracle 테스트(H2에는 OUT 파라미터 없음) | 아니오 | M11, D30, PR1~PR8 |
| DB 함수(`@SqlFunction`, 쿼리 안 함수, 단독 호출) | 구현됨, 세 DB 테스트 | 아니오 | D31, FN1~FN11 |
| SARGable 경고와 모드(`SargableMode`, `lambdaql.sargable.mode`), 열 산술 검출 | 구현됨, 세 DB 테스트 | 아니오 | PR8, D50 |
| SQL 의미 원칙(엔티티 값 비교, null 캡처 값, 정수 열과 소수 값) | 구현됨, 세 DB 테스트 | 일부 | D48(B1~B8) |
| 메시지와 오류 코드(`[LQL-xxxx]`, 4개 언어, `lambdaql.locale`) | 구현됨, 단위 테스트 | – | D33 |
| 분석 캐시(EntityManagerFactory별 공유) | 구현됨 | – | D47 |
| Spring Data JPA 모듈(`LambdaQlExecutor<T>`, `Sort`, `Pageable`, DML 단축 메서드, 자동 구성) | 구현됨, 세 DB·Spring Boot 3.4.2 테스트. DML에는 호출자 트랜잭션 필요 | Kotlin 리포지토리는 아니오 | D34, D39, M12 |

### 미구현과 후보

| 항목 | 상태 |
|---|---|
| 모든 경고를 오류로 바꾸는 일반 엄격 모드 | 미구현(정렬 정책과 SARGable에는 STRICT가 있음) |
| MariaDB `lateral` 쿼리의 `row_number()` 재작성 | 미구현(Q4) |
| `SortSpec`의 조인 형태 | 설계됨 |
| 집계 기준 정렬, Oracle 오버로드 선택, 조건부 값(CASE WHEN), 일반 `LIKE`(`startsWith` 등) | v0.2 후보 |
| insert의 조인, 엔티티 4개 이상 조인, 서브쿼리 안 엔티티 3개 이상 | 이후 |
| UPDATE·DELETE의 명시적 조인 | 추가하지 않음(D51, 연관 경로 U9와 `sub` 사용) |
| 복합 키 엔티티의 `limit`·`executeOne()` | 미구현 |
| U11, U12, U15 검사 | 미구현 |
| Kotlin 전용 형태 `?:`, 범위, `when` | 설계됨, 프로토타입으로 바이트코드 확인. 빌드 안의 Kotlin 테스트 없음 |
| Spring Boot 4 | 미착수(별도 브랜치 예정) |
| Maven Central 배포 | 미배포 |
