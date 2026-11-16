# DEVNOTE — lambdaQL 개발 인계 노트

> 최종 갱신: 2026-11-10 · 기준 커밋 `70c5c01`(미커밋 변경 있음, 아래 7절)
>
> 이 문서는 **다른 에이전트/개발자가 작업을 바로 이어받기 위한 요약**입니다. 설계의 근거와 상세는 다음 문서에 있습니다.
>
> - [`docs/design/ROADMAP.md`](docs/design/ROADMAP.md): 현재 상태, 방식 비교, 핵심 설계, 결정 사항(D1~D25), 마일스톤 M0~M8, 지원 범위, 리스크
> - [`docs/design/KOTLIN.md`](docs/design/KOTLIN.md): Kotlin 지원 설계(바이트코드 패턴, 지원 매트릭스, 제약 KC1~KC21)
>
> 문서와 코드가 다르면 **문서의 결정 사항이 우선**입니다(코드는 아직 M0 수준).

---

## 1. 이 프로젝트가 하는 일

Java·Kotlin의 Serializable 람다를 **실행하지 않고 바이트코드(ASM)로 분석**해 Hibernate 6 HQL(JPQL 상위 집합)로 번역하는 쿼리 라이브러리입니다.

```java
qb.from(Order.class)
  .where(o -> o.getStatus() == 1 || o.getCustomer().getName().equals("x"))
  .orderBy((o, by) -> by.desc(o.getPrice()).asc(o.getId()))
  .limit(20).offset(40)
  .toList();
// → select o from Order o left join o.customer c1
//   where o.status = 1 or c1.name = :p1 order by o.price desc, o.id asc
```

```kotlin
qb.from(Order::class.java)
  .where { it.status == 1 || it.customer?.name == "x" }
  .orderBy { o, by -> by.desc(o.price).asc(o.id) }
```

**최상위 원칙**(ROADMAP 3.6): 람다는 실행 코드가 아니라 **컬럼과 조건을 지정하는 DSL**입니다. Java 실행 규칙(객체 동일성, 박싱 캐시, NPE)을 따르지 않고, 문서화된 규칙표(R1~R24)를 따릅니다. **null은 개발자가 소스에 명시했을 때만 다룹니다**(D20·D22·D23).

## 2. 현재 상태 (2026-11-10)

- **코드는 초기 프로토타입 + M0 일부**입니다. 핵심 분석기 `query/src/main/java/org/lambdaql/analyzer/LambdaPredicateVisitor.java`는 **설계상 대부분 다시 작성될 예정**입니다(ROADMAP M2·M3).
- 현재 분석기의 주요 결함(실측, ROADMAP 1.4):
  - 비교를 1개만 기억(`ComparisonStateManager`) → `a||b||c`, `x>=0&&x<=1`이 항 하나로 줄어듦
  - 분기 label 미사용, IF_ICMP 극성 미반전, FCMPx/DCMPx·IF_ACMP·IFNULL·NEW·ARRAYLENGTH·BIPUSH 미처리
  - 메서드 체이닝(`MethodStack.beforeStack`) 오류, 상수 인자 CCE
- SQL 렌더링(`Renderer.toSql`), 실행(`toList`), Update/Delete/Join은 스텁입니다.
- **빌드**: `./gradlew clean build` → BUILD SUCCESSFUL. 테스트 15개 중 **통과 5, `@Disabled` 10, 실패 0**. `@Disabled`마다 실제 원인과 담당 마일스톤이 적혀 있습니다(`QueryBuilderTest.java`).

> **2026-11-12 구현 현황**: 조회 `qb.from(X).where(…).toList()`가 동작한다(`SelectQuery`, `QueryContext`, `engine.sem.Normalizer`, `engine.render.HqlRenderer`). 정렬(`orderBy`, `SortSpec`, `limit/offset`)과 결과 함수(`first/one/count/exists`, D38)도 동작한다(`SortReader`, `OrderedSelectQuery`, `JoinPlanner`). 캡처 값만 쓰는 조건 부분(`min == null || …`)은 렌더링 직전에 Java 순서로 결정한다(`HqlRenderer.fold`). `SelectQueryTest`·`SortQueryTest`·`ResultQueryTest`가 H2·MariaDB·Oracle에서 통과한다. 다른 DB로 돌리려면 `export LQL_DB_PASSWORD=…`(docker inspect로 읽기) 후 `./gradlew :query:test --tests '*SelectQueryTest' --tests '*SortQueryTest' --tests '*DatabaseSelectionTest' -Dlql.db=mariadb|oracle --rerun`을 실행한다. `-Dlql.db`는 `application-test.yaml`이 `lql-db-<이름>.yaml`을 `spring.config.import`로 읽게 한다(`spring.profiles.include`는 `test` 프로필에 밀려 H2로 연결됐음 — 2026-11-12 발견·수정). `DatabaseSelectionTest`가 실제 연결 DB가 다르면 실패한다. 매뉴얼의 DB별 SQL은 `SqlByDatabaseTest`(구현된 예제)·`PlannedSqlTest`(계획 HQL을 Hibernate에 직접 넣은 결과)가 만든 `query/src/test/resources/expected-sql/{h2,mariadb,oracle}.sql`, `planned-<db>.sql`에서 옮긴다. 테스트는 이 파일과 비교하므로 SQL이 바뀌면 실패한다. 바뀐 SQL을 확인한 뒤 `-Dlql.sql.update=true`(DB마다 `-Dlql.db=…`)로 다시 쓰고 매뉴얼도 고친다. 이때 테이블은 `SqPrefixNamingStrategy`로 `SQ_` 접두가 붙고 create-drop된다. Oracle은 끝나면 `purge recyclebin`. 결정 D35(정렬 `asc/desc(key, boolean nullsFirst)`, `by.sort(key, Direction)`), D36(where 연관 경로는 INNER JOIN, 단 null guard로 연관 없는 행이 남을 수 있으면 LEFT, 정렬·select 전용 경로는 LEFT), D37(람다에 적힌 상수는 리터럴, 캡처·계산 값은 바인딩)이 반영됐다. 메서드 레퍼런스는 M7까지 명확한 오류로 거부한다. Spring 연동(D34, D39)은 `spring-data-jpa` 모듈의 `LambdaQlExecutor`(조회 실행기, `Pageable` 포함)와 Boot 자동 구성, 의존성 묶음 `spring-boot-starter` 모듈로 구현했다. 테스트는 `./gradlew :spring-data-jpa:test -Dlql.db=…`. DML(M10, D28, D40)은 `qb.update/delete/insert/upsert`와 실행기의 `updateQuery()` 등으로 구현했다(`AssignmentReader`, `Decoder.decodeStatements`, `HqlRenderer.update/delete/insert*`). 옛 `org.lambdaql.analyzer` 패키지는 `LambdaAnalysisException`만 남기고 삭제했다. 다음 순서: M8 서브쿼리(DML의 `sub` 포함), M9 select·집계, M5.5 나머지(SortWhitelist·조인형 정렬), M11 프로시저·함수, D33 i18n 메시지 → M8 서브쿼리 → M9 select·집계 → M10 DML → M11 프로시저·함수 → D33 i18n 메시지 → M12 Spring 모듈.

## 3. 빌드·개발 환경

| 항목 | 값 / 주의 |
|---|---|
| JDK | **21** (로컬 `JAVA_HOME=~/.jenv/versions/21`). JDK 26도 설치되어 있음 |
| Gradle | wrapper **8.10** — **Java 23 이하에서만 실행 가능** |
| 데몬 JVM | `gradle/gradle-daemon-jvm.properties`(`toolchainVersion=21`, `updateDaemonJvm`으로 생성). JDK 26으로 Gradle을 띄워도 데몬은 21 |
| IntelliJ | Gradle JVM이 JDK 26으로 잡히면 "Gradle 8.10 is incompatible…" 오류. **Settings > Build Tools > Gradle > Gradle JVM = `temurin-21`**(이 PC는 `.idea/gradle.xml`에 설정함, `.idea`는 git 제외) |
| ASM | **9.11.1**(`api` 의존). 9.7은 Java 23(major 67)까지만 읽어 JDK 24+로 컴파일된 람다를 못 읽음. **새 JDK 릴리스마다 최신 ASM으로 갱신**(ROADMAP 2.6.2, D9) |
| 테스트 DB 스키마 | `application-test.yaml`의 `ddl-auto: create-drop`(엔티티 기준). 예전 `table.sql` + `@BeforeEach` DDL은 삭제함 |
| 개별 테스트 | `./gradlew :query:test --tests "org.lambdaql.query.QueryBuilderTest.<name>"` |

## 4. 확정된 설계 요약 (상세: ROADMAP 2.5절 결정 표)

### 4.1 분석 엔진
- **D1 (B) 자체 구현**: 각 조건 jump를 원자로 기록 → 분기 그래프를 **구조 축약(Cifuentes R1~R4)**으로 `&&`/`||`/`!` 트리 복원(ROADMAP 2.3.5, 3.3).
- javac의 NOT 재배치(드모르간, 이중 부정, 비교 반전)는 **SQL 3값 논리에서 결과를 바꾸지 않음**을 전수 검증(원자 4개까지 동치 쌍 2,159만 개, 반례 0)과 H2·MariaDB·Oracle 행 단위 실측으로 확인(ROADMAP 2.3.7).
- **금지**: 원자를 중복시키는 재작성(흡수 `X∨(¬X∧Y)→X∨Y`, ternary를 `(c∧x)∨(¬c∧y)`로 전개 등) — NULL에서 결과가 달라짐.
- IR: `org.lambdaql.analyzer.expr`의 sealed `Expr` record(ColumnPath, MethodCall, Literal, CapturedArg, HostExpr, Compare, …). `Expr`는 ASM `Value`를 구현해 나중에 `Frame` 기반 엔진으로 옮길 수 있게 함(D2).
- 바이트코드 리더는 `BytecodeSource` 인터페이스 뒤에 둠. 대상 메서드의 `MethodNode`만 만들고 `ClassNode`는 만들거나 캐시하지 않음(성능 실측, ROADMAP 2.6.1).

### 4.2 의미 규칙 (ROADMAP 3.6 R1~R24)
| 형태 | 번역 |
|---|---|
| 기본형 비교 / 한쪽이 기본형(`o.getId() == 10L`) | `=`, `<` … (NULL이면 SQL 의미) |
| **참조끼리 `==`**(`Long == Long`, `String == String`), Kotlin `==`(`Intrinsics.areEqual`) | **null-safe 값 동등** `is [not] distinct from` (D12, R2, R11) |
| `Objects.equals`, `"x".equals(a)` | null-safe 값 동등 (R5) |
| `a.equals(b)` | `a = b` (SQL 의미) |
| 연관 경로 `o.getCustomer().getName()` | **컬럼 지정**: Metamodel로 판별 → 연관은 `left join` 별칭, embedded는 같은 테이블, `@Id`로 끝나면 FK, 컬렉션은 거부. Customer null 여부는 평가하지 않음 (D20, R24) |
| 컴파일러가 넣은 null 확인(Kotlin `dup; ifnonnull; pop`) | **무시** → SQL 의미. 개발자가 쓴 `!= null`, `== null`, `?.`, `?:`만 반영 (D22) |
| Kotlin `!!` | 무시 (D23) |
| 캡처 값 | 바인드 파라미터. 문자열·날짜·float는 인라인하지 않음 |

### 4.3 렌더링 (D14)
- Hibernate 6 HQL 확장을 기본으로 사용: null-safe 비교는 `is [not] distinct from`(H2 네이티브, MariaDB `<=>`, Oracle `decode`로 렌더링됨 — 세 DB 실측 일치).
- boolean null-safe 부정은 `active is distinct from true`. **`is [not] true/false`, `coalesce(bool,…)`는 OracleDialect에서 ORA-00932라 사용 금지.**
- **복합 식에 `= true/false`, `IS [NOT] TRUE/FALSE`, `IS [NOT] NULL` 래퍼를 씌우지 말 것** — Oracle 23.26.3이 WHERE에서 결과를 틀리게 냄(직접 재현, G14). 부정은 항상 `NOT(...)`.
- float/double 컬럼의 Java 비트 단위 일치는 보장하지 않음(D11). NaN/Infinity 파라미터는 거부.
- **SARGable 렌더링이 기본 규칙**(ROADMAP 3.9): 컬럼 쪽에 함수·연산·CASE를 씌우지 않는다. 변환은 파라미터 쪽에서 Java로 미리 한다. 대소문자 무시(D24)는 DB 메타데이터로 컬럼별 접근 경로(CI collation, Oracle 함수·언어 인덱스)를 확인해 렌더링하고, 경로가 없으면 WARN. boolean 식끼리 `==`는 DNF 전개(D25, CASE 금지).

### 4.4 정렬 (D15, ROADMAP 3.7)
```java
.orderBy((o, by) -> by.desc(o.getPrice()).asc(o.getCustomer().getName()).asc(o.getId()))
.orderBy((o, by) -> by.desc(o.getDescription(), false))   // NULL 위치: false = 나중(D35)
.orderBy((o, by) -> by.sort(o.getPrice(), dir))            // 실행 시점 방향(캡처한 Direction)
.orderBy(o -> o.getPrice())                      // by 생략형(키 1개)
.orderBy((o, c, by) -> by.asc(c.getName()))      // 조인(D18)
```
- 람다 **하나**, 마지막 인자 `by` 빌더(정적 헬퍼·static import 없음). 키 타입은 `Comparable<?>`(Kotlin nullable 허용, 엔티티 키는 컴파일 오류).
- `Direction`: `ASC, DESC, ASC_NULLS_FIRST, ASC_NULLS_LAST, DESC_NULLS_FIRST, DESC_NULLS_LAST`. `ASC`/`DESC`는 **SMALLEST**(ASC=NULL 먼저, DESC=NULL 나중, D16).
- 연관 경로 정렬은 정렬 전용 `left join`, 페이징 시 유일 키가 없으면 `@Id asc` 자동 추가(Oracle은 동순위 순서가 임의).
- 서브쿼리 정렬: `limit` 없으면 제거 + WARN, MariaDB `IN (… limit)`는 파생 테이블로 자동 래핑, MariaDB `lateral`은 `row_number()`로 재작성(S14).

### 4.5 Kotlin (D17, 상세 `docs/design/KOTLIN.md`)
- Java와 **같은 엔진으로 동시 지원**. kotlinc 1.7.22~2.3.21 모두 Java `Serializable` SAM에 리터럴 람다를 넘기면 indy로 컴파일되어 `SerializedLambda` 사용 가능.
- Kotlin impl 메서드는 `main$lambda$0` 형식, **synthetic 아님**, `private static final`(진입부 판별에 주의).
- 범위 `in a..b`, `until`, `when`(Int/String/enum) 모두 v0.1 지원(D21).
- 제약 KC1~KC21: `-Xsam-conversions=class`, `inline fun` 안의 쿼리 람다, Kotlin `fun interface`에 콜러블 레퍼런스, 값 `if/when`, 람다 안 `listOf`, `@JvmField`, 맹글링된 getter 등.

### 4.6 지원 DB / 검증 DB
H2 2.3.230, MariaDB 10.11.18, Oracle Free 23.26.3. DB별 차이 표는 ROADMAP 2.7·2.8.

## 5. 다음 작업 (우선순위 순)

1. **미확정 결정 확인**: D8 라이선스(FluentJPA "Patent Pending" 표기) 법무 확인만 남음. D1~D25는 모두 확정(ROADMAP 2.5절 결정 표).
2. **M0 나머지**: `SelectQuery`의 매 호출 `createEntityManager()` 제거와 package-private `condition()` 추가, `BytecodeDump`(Textifier)·Spring 없는 `AnalyzerTestSupport`, `System.out`·이모지 로그 → slf4j.
3. **M1**: `SerializedLambda`를 `replaceObject`로 획득(`setAccessible` 제거), `BytecodeSource`/`AsmBytecodeSource`, descriptor 기반 슬롯 계산, 메서드 레퍼런스 판별(ACC_SYNTHETIC + implMethodKind; Kotlin impl은 synthetic 아님 주의).
4. **M2~M7**: ROADMAP 4장 순서. Kotlin 작업은 각 마일스톤에 포함(KOTLIN.md 7절).
5. **M8 서브쿼리**(D19: 통상 서브쿼리 전부 v0.1): 문법 확정(ROADMAP M8, D26). 문법은 `where((o, sub) -> sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).exists())` — 람다 인자 `sub` 팩토리, 상관은 안쪽 람다의 바깥 엔티티 캡처, 안쪽 람다는 `invokedynamic` bootstrap 인자로 찾아 재귀 분석, NOT IN은 NOT EXISTS로 렌더링. Java·Kotlin 컴파일 실측: `/tmp/lql-subq/a/{Sq.java,K.kt}`. FROM 서브쿼리·CTE(M8-2)는 M9 projection의 record·data class를 행 타입으로 사용.
6. **M9 projection·집계**: D27 확정(2026-11-10). `select(o -> new Dto(…))`, `groupBy(o -> key)` / 다중 키 `groupBy(o -> List.of(a, b))`(Kotlin `groupBy { listOf(a, b) }`, `by.key` 체인은 폐기), 집계는 **라이브러리 정적 함수 `org.lambdaql.Agg`**: `having(o -> Agg.sum(o.getPrice()) > 1000)`, `select(o -> new Stat(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice())))`(static import 선택). 비집계 식은 groupBy 키여야 함(분석 시점 검사). 생성자 projection은 `select new` 대신 열 + typed query로 렌더링. 파생 테이블·CTE는 세 DB 실측 완료(CTE는 HQL에서 바깥 별칭 필수). 실측: `/tmp/lql-proj/{a,hib}`, `/tmp/lql-agg`.
7. **M10 UPDATE·INSERT·DELETE**: 진입점 이름은 확정: `qb.from(X)`(조회, 코드에서 `selectFrom`을 `from`으로 바꿈) / `update` / `insert` / `delete`. DML 문법 확정(2026-11-10): 대입 표기는 setter 방식과 `by.set(열, 값)` 방식 둘 다(setter 없는 엔티티용, 타입은 분석 시점에 Metamodel로 검사 — U20), 전체 행은 `all()` 필수(U5). `set()`은 한 람다 안 여러 대입과 여러 번 호출을 모두 허용(U14). 대입은 setter 호출 람다(`update(Order.class).set(o -> { o.setStatus(st); o.setPrice(o.getPrice() * 1.1); }).where(…)`), Kotlin은 `set { it.status = st }`(같은 setter 바이트코드). 꼭 지킬 규칙: (1) 앞에서 대입한 열을 뒤에서 읽으면 거부(MariaDB는 SET을 순서대로 적용, U3) (2) **DML의 비FK 연관 경로는 상관 스칼라 서브쿼리 `(select c.x from C c where c.id = o.fk)`로 렌더링**(Hibernate에 경로를 넘기면 inner join이 되어 연관 없는 행이 빠지고, EXISTS는 `is null`·NOT에서 조회와 결과가 달라짐 — 세 DB 실측 `/tmp/lql-dml/sem`, U9) (3) 캡처 값은 위치마다 별도 파라미터(enum STRING/ORDINAL 공유 시 조용히 잘못된 값 저장, U7) (4) `where`/`all()` 없는 `execute()`는 타입으로 금지(U5) (5) 다중 행 values는 바인드 수 기준으로 나눔(Oracle 65,535 한계, U13) (6) DELETE 자기 참조 서브쿼리는 파생 테이블로 감쌈(MariaDB 1093, U17). 실측: `/tmp/lql-dml/{a,b,hib,wf-base,wf-gapcompile,wf-db,sem}`.
8. **D29 확정**: Java `ids.contains(o.getId())`(캡처한 컬렉션 IN)는 v0.1. 의미는 Kotlin R19와 같음(부정은 `(x is null or x not in (:p))`, null 원소는 바인딩 예외, 빈 목록 FALSE). M4·M5 범위에 넣는다.
9. **M11 프로시저·DB 함수**: D30·D31 확정(2026-11-10). D32(DDL)는 폐기 — DDL은 범위 밖. 프로시저는 `interface Procs { @SqlProcedure("SQ_CALC") Double calc(Long id, Double rate); }` + `qb.call(Procs.class, p -> p.calc(id, rate)).execute()`(void는 `qb.exec`). **DB 함수는 인터페이스 static 메서드** `interface OrderFns { @SqlFunction("FN_DISCOUNT") static Double discount(Double p) { throw new DbOnlyException(); } }` → `where(o -> OrderFns.discount(o.getPrice()) > 10)`, 단독 호출 `qb.call(() -> OrderFns.discount(p))`, Kotlin은 인터페이스 companion `@JvmStatic`. 렌더링은 항상 `function('키' as T, …)`(T 허용 목록 8종), 부팅 시 선택 등록(ServiceLoader + 설정 `lambdaql.functions.classes`). 함정(실측): Hibernate는 이름 등록도 위치 바인딩이라 순서가 바뀌면 MariaDB·H2에서 조용한 오답, ProcedureCall은 호출 전 flush 안 함, record 결과 클래스 불가(JDBC 직접 매핑), Oracle DML 함수는 HQL select에서 ORA-14551, 내장 함수 이름(COALESCE 등)으로 등록하면 조용히 무시됨, H2는 OUT·INOUT·여러 결과 집합 불가. 실측: `/tmp/lql-proc/{probe,syntax,review}`, `/tmp/lql-fn/{forms,hib,review2,f2}`.
10. **i18n·로깅(D33, ROADMAP 3.10)**: 엔진 예외 메시지 37개를 `LqlMessage` enum(코드 `[LQL-xxxx]`) + 번들 `org/lambdaql/i18n/LqlMessages(_ja|_zh|_ko).properties`로 옮긴다(영어·일본어·중국어 간체·한국어 기본 제공). 언어는 설정 → `-Dlambdaql.locale` → JVM 기본 로케일(ja·zh·ko면 해당 언어, 그 외 영어). 새 메시지는 네 언어를 함께 추가(키 누락 테스트). `MessageFormat` 금지(자체 `{n}` 치환). 예외 부모 `LambdaQLException`(`code()`, `key()`, `args()`)을 엔진 공개 패키지에 두고 `LambdaAnalysisException`을 analyzer 패키지에서 옮긴다. 로깅은 SLF4J API, Spring starter는 compileOnly로. 테스트는 `code()`로 검사. 프로토타입: `/tmp/lql-i18n/c/final`.
11. **M12 Spring Data JPA 연동(D34)**: 모듈을 `lambdaql-core`(Spring 의존 0, 지금의 `query` 모듈)와 `lambdaql-spring-data-jpa`로 나눈다. repository는 `LambdaQlExecutor<T>`를 상속하고 default 메서드 안에서 `findAll(o -> …)`·`query().where(…)` 등을 부른다(default 메서드는 Spring Data가 직접 호출). 등록은 Spring Data 3.4+ `META-INF/spring.factories` fragment(구현 생성자는 하나, `@NoRepositoryBean` 금지). 트랜잭션 공유 EntityManager 사용. Kotlin은 2.2+ 또는 `-Xjvm-default=all`, `kotlin-reflect` 필요. 기존 `org.lambdaql.jpa` 패키지(default 메서드로 Impl 대체)는 작성자가 사용 중인 코드라 삭제하지 않고 재검토 중(ROADMAP M12 SD9). 루트 프로젝트 이름 `lambadaql` 오타도 모듈 분리 때 고친다. 실측: `/tmp/lql-spring/{a,b,c}`.

## 6. 검증 환경과 재현 방법

### 6.1 DB (로컬 docker)
| DB | 접속 | 사용 범위 |
|---|---|---|
| MariaDB 10.11.18 | 컨테이너 `mariadb`, `127.0.0.1:3306`, user `root` | **스키마 `lql_verify`만** 사용. 다른 스키마·전역 변수 변경 금지 |
| Oracle Free 23.26.3 | 컨테이너 `lql-oracle`(이 작업에서 생성), `jdbc:oracle:thin:@127.0.0.1:1521/FREEPDB1`, user `lql` | 스키마 `LQL`만 사용 |
| H2 2.3.230 | 인메모리 | jar: Gradle 캐시 또는 `/tmp/lql-notlogic/db/h2-2.3.230.jar` |

**비밀번호 취급 규칙**: 컨테이너 환경변수에서 실행 시에만 읽고, **출력·로그·파일에 남기지 않습니다.**
```bash
MPW=$(docker inspect mariadb    --format '{{range .Config.Env}}{{println .}}{{end}}' | sed -n 's/^MARIADB_ROOT_PASSWORD=//p')
OPW=$(docker inspect lql-oracle --format '{{range .Config.Env}}{{println .}}{{end}}' | sed -n 's/^APP_USER_PASSWORD=//p')
```
- 검증 에이전트 보고: MariaDB root 비밀번호가 흔한 사전 단어라 교체 권장(작성자에게 전달됨).
- `lql-oracle`은 약 2GB 메모리 사용. 필요 없으면 `docker stop lql-oracle`.

### 6.2 Kotlin 컴파일러 (설치 없이 Gradle 캐시의 embeddable 사용)
```bash
# 클래스패스 파일: /tmp/lql-kt-cp.txt (kotlin-compiler-embeddable 2.3.21 + deps), /tmp/lql-kt-std.txt (kotlin-stdlib 2.3.21)
JH=/opt/homebrew/Cellar/openjdk@21/21.0.7/libexec/openjdk.jdk/Contents/Home
$JH/bin/java -cp "$(cat /tmp/lql-kt-cp.txt)" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -cp "<java-classes>:$(cat /tmp/lql-kt-std.txt)" -jvm-target 21 -d <out> <files.kt>
```
`/tmp` 파일이 사라졌으면 `~/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/` 아래에서 `kotlin-compiler-embeddable`, `kotlin-stdlib`, `kotlin-script-runtime`, `kotlin-reflect`, `kotlin-daemon-embeddable`과 `kotlinx-coroutines-core-jvm`, `trove4j`, `org.jetbrains:annotations`를 다시 모아 클래스패스를 만듭니다. 다른 버전(1.7.22, 2.1.10, 2.2.21, 2.3.0)도 캐시에 있음.

### 6.3 실측 근거 위치 (⚠️ 모두 `/tmp`, 재부팅 시 사라질 수 있음)
| 주제 | 경로 |
|---|---|
| 초기 테스트 로그(ASM 상향 전·후, M0 후) | `/tmp/lql/*.log`, `/tmp/lql/after/`, `/tmp/lql/m0-run*.log` |
| ExTree·FluentJPA·Jinq 소스(얕은 clone, **이 안에서 빌드 실행 금지**) | `/tmp/lql-research/` |
| ExTree 실측 probe | `/tmp/lql-probe/` |
| ASM vs `java.lang.classfile` 벤치마크, ASM 버전 호환 | `/tmp/lql-bench/` |
| NOT/3값 논리 검증(javac 코퍼스 41그룹, Kleene 전수 검사, H2) | `/tmp/lql-notlogic/{javac,src,logic,db}`, 종합 `/tmp/lql-notlogic/final.md` |
| MariaDB 검증 / Oracle 검증 | `/tmp/lql-notlogic/mariadb/`, `/tmp/lql-notlogic/oracle/` |
| 정렬 문법 조사·실측 | `/tmp/lql-sort/` (`final.md`, `one/`, `omit/`, `join`, `rev`, `subq`) |
| Kotlin 패턴 조사(166 조건 람다 등) | `/tmp/lql-kt/` (`pred/`, `value/`, `sem/`, `explicit/`, `final/kotlin.md`) |
| D20(연관 null) 비교 실측 | `/tmp/lql-d20/D20.java` |

장기 보존이 필요하면 이 중 소스(`*.java`, `*.kt`, `*.py`)와 요약(`*.md`)만 저장소의 `docs/evidence/` 등으로 옮기기를 권합니다(덤프·jar 제외).

## 7. 미커밋 변경 (2026-11-10 기준)

| 파일 | 내용 |
|---|---|
| `query/build.gradle` | ASM 9.7 → 9.11.1, `asm`을 `api`로 |
| `query/src/main/java/org/lambdaql/analyzer/LambdaAnalysisException.java` (신규) | 원인 예외를 cause로 보존 |
| `LambdaWhereAnalyzer.java` | 원인을 가리던 fallback `ClassReader`(→ `IOException: Class not found`) 제거, `LambdaAnalysisException` 사용 |
| `LambdaFieldExtractor.java` | AspectJ 셰이딩 ASM(`aj.org.objectweb.asm`) import → `org.objectweb.asm` |
| `QueryBuilderTest.java`, `application-test.yaml`, `table.sql`(삭제) | `ddl-auto: create-drop`, 반복 DDL 제거, 미해결 테스트 `@Disabled`(사유 포함), 익명 클래스 테스트는 `assertThrows`로 |
| `gradle/gradle-daemon-jvm.properties` (신규) | 데몬 JVM 21 고정 |
| `docs/design/ROADMAP.md`, `docs/design/KOTLIN.md`, `DEVNOTE.md` (신규) | 설계 문서 |

## 8. 작업 규칙과 함정

- Oracle 검증 하네스는 테이블을 `drop`만 하므로 LQL 스키마 휴지통(recyclebin)에 객체가 쌓인다. 끝나면 LQL 계정으로 `purge recyclebin`(자기 스키마만 영향).

- **예시는 테스트 코드에서 가져온다(작성자 규칙, 2026-11-10).** Javadoc 예시는 `{@snippet class="org.lambdaql…Test" region="이름"}`으로 테스트 파일의 구간을 넣고, 테스트 파일 GitHub 링크(`https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/…`)를 붙인다. 테스트에서는 `// @start region="이름"` … `// @end`로 구간을 표시한다. `query/build.gradle`의 `javadoc`이 `--snippet-path src/test/java`를 넘기므로, 구간이 없으면 javadoc 빌드가 실패한다. 테스트 클래스 주석에 `// @start` 같은 표시 문자열을 그대로 쓰면 snippet 처리기가 잘못 읽는다(spurious markup 경고). 매뉴얼 예시도 기능이 구현되면 테스트 구간에서 가져오고 테스트 파일로 링크한다(그 전까지는 설계 예시로 표시).
- **매뉴얼은 Java와 Kotlin을 분리한다**: `docs/manual/<언어>/java/`, `docs/manual/<언어>/kotlin/`(장 번호·파일 이름 같음), 공통 페이지는 `docs/manual/<언어>/`. 영어(`en`)가 원본이고 `ko`·`ja`·`zh`는 번역.
- **커밋·푸시는 작성자 요청이 있을 때만.** 결정이 필요한 사항(D 항목)은 작성자에게 확인 후 ROADMAP 결정 표에 "확정(날짜, 작성자 결정)"으로 기록합니다.
- 설계 문서(`docs/`, 이 파일)와 작성자와의 대화는 **한국어**. **코드 주석과 Javadoc은 쉬운 영어**로, 모든 클래스·메서드·필드에 설명, `@param`, `@return`, `@throws`, Example 코드를 단다(작성자 규칙, 2026-11-10). 식별자·기술 용어는 영어.
- **라이선스**: ExTree(LGPL)·FluentJPA(AGPL/LGPL) 코드는 **복사 금지**, 개념만 참고(clean-room). Jinq(BSD-2) 구조를 차용하면 고지.
- 외부에서 받은 저장소(`/tmp/lql-research`) 안에서 Maven/Gradle을 실행하지 않습니다.
- 검증용 DB 작업은 지정 스키마(`lql_verify`, `LQL`)에서만, 임시 테이블은 끝나면 삭제.
- **javac/Kotlin 바이트코드 함정**
  - float/double 비교는 `cmpl`(`>`, `>=`, `==`, `!=`)과 `cmpg`(`<`, `<=`)로 소스 연산자를 복원할 수 있음 — bias를 버리지 말 것.
  - 상수 접기로 퇴화 jump(`B && false` → T·F 후속이 같은 원자), 원자 소멸(`false && B`), 단독 goto가 생김.
  - Kotlin은 조건 jump 시점에 값 스택이 비어 있지 않은 패턴(`?.`, `?:`, 자동 null 확인)이 흔함 — "값 ternary 거부" 규칙의 예외 처리 필요.
  - Kotlin 속성 레퍼런스 `Order::price`는 getter 직접 참조가 아니라 `KProperty` 싱글톤을 캡처한 합성 람다.
- **DB 함정**: MariaDB는 `NULLS FIRST/LAST` 미지원(Hibernate가 CASE로 흉내), `IS DISTINCT FROM` 없음(`<=>`), 기본 collation `_ci`는 대소문자·끝 공백 무시, NaN 저장 불가. Oracle은 `''`를 NULL로 저장, `NLS_SORT`가 JVM locale을 따라감(`-Duser.language=en` 권장), WHERE 복합 boolean 래퍼 버그(G14).
- **H2만으로는 부족**: ORDER BY NULL 위치, `''`=NULL, Oracle boolean 의미, 평가 순서(오류 guard)는 H2로 검증할 수 없음 — M5·M6 완료 기준은 세 DB 기준.
