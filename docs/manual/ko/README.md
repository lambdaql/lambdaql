<a id="lambdaql-user-manual-english"></a>
# lambdaQL 사용자 매뉴얼 (한국어)

**다른 언어:** [English](../en/README.md) | [한국어](../ko/README.md) | [日本語](../ja/README.md) | [简体中文](../zh/README.md)

> [!IMPORTANT]
> **상태: lambdaQL은 초기 개발 단계입니다.**
> 현재 **분석 코어**, **결과 함수를 포함한 `where`**(`toList()`, `toSet()`, `first()`, `one()`, `count()`, `exists()`), 그리고 **정렬과 페이징**(`orderBy`, `limit`, `offset`, `SortSpec`)의 핵심 기능이 구현되어 있습니다. lambdaQL은 람다를 로드하고, 디코딩하고, HQL을 쓰고, 실행합니다. 이 부분은 H2, MariaDB, Oracle에서 테스트했습니다(Java 람다 사용).
> **수정, 삽입, 삭제, 업서트**도 `limit`과 1행 보호 `executeOne()`을 포함해 구현되어 있습니다(H2, MariaDB, Oracle에서 Java 람다로 테스트함).
> **Spring Data JPA 모듈**도 구현되어 있습니다. 리포지토리 실행기 `LambdaQlExecutor<T>`(Spring `Sort`, `Pageable`, `deleteAll(where)`·`updateOne(where, set)` 같은 DML 단축 메서드, 수정·삭제·삽입·업서트 빌더 포함)와 Spring Boot 자동 구성이 대상이며, Spring Boot 3.4.2에서 테스트했습니다. DML에는 호출자의 트랜잭션이 필요합니다.
> **서브쿼리**(`where((o, sub) -> ...)`, 수정·삭제 포함)와 **`select`**(레코드와 DTO, `groupBy`, `having`, `Agg` 집계, `insert ... select` 포함)도 구현되어 있습니다(H2, MariaDB, Oracle에서 Java 람다로 테스트함).
> **명시적 조인**(`join`, `leftJoin`, `crossJoin`), **파생 테이블과 CTE**(`qb.from(valueQuery)`, `qb.with(valueQuery)`), **저장 프로시저와 DB 함수**(`@SqlProcedure`, `@SqlFunction`, `qb.call`, `qb.exec`), **4개 언어 메시지와 오류 코드**(`[LQL-xxxx]`)도 구현되어 있습니다(Java 람다로 테스트함. 프로시저는 MariaDB와 Oracle에서 테스트함).
> **Kotlin 람다**는 일부 형태(`?.`, `!!`, `in`, 서브쿼리, 데이터 클래스, `groupBy`, update, insert, delete)를 빌드 안에서 테스트했습니다. 나머지 Kotlin 전용 형태는 **설계** 단계입니다. 문법과 규칙은 확정되었고 프로토타입으로 확인했지만, 빌드 안의 테스트는 아직 없습니다.
> 아직 Maven Central에 배포된 아티팩트는 없습니다. 사용해 보려면 소스에서 빌드하십시오([Java](java/02-getting-started.md#build-from-source) | [Kotlin](kotlin/02-getting-started.md#build-from-source)).

lambdaQL은 Java와 Kotlin용 쿼리 라이브러리입니다.
평소처럼 람다를 작성하면 lambdaQL이 람다의 바이트코드를 읽어 Hibernate 6.6 HQL 쿼리를 만듭니다.
람다는 절대 실행되지 않습니다.

<a id="a-first-look"></a>
## 첫인상

### Java

```java
List<Order> orders = qb.from(Order.class)
    .where(o -> o.getStatus() == 1 && o.getPrice() > minPrice)
    .orderBy((o, by) -> by.desc(o.getPrice()).asc(o.getId()))
    .limit(20)
    .toList();
```

### Kotlin

```kotlin
val orders = qb.from(Order::class.java)
    .where { it.status == 1 && it.price!! > minPrice }
    .orderBy { o, by -> by.desc(o.price).asc(o.id) }
    .limit(20)
    .toList()
```

<a id="hql-the-same-for-both"></a>
### HQL (두 언어 모두 동일)

```
select e0 from Order e0
where e0.status = 1 and e0.price > :p1
order by e0.price desc nulls last, e0.id asc
```

**SQL 블록 읽는 법.** `…`는 줄여 쓴 select 목록(엔티티의 모든 컬럼)이고, `?`는 바인드 파라미터입니다. 각 줄은 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)에서 나옵니다. 이 테스트는 각 쿼리를 Hibernate 6.6.5로 H2 2.3, MariaDB 10.11, Oracle 23.26에서 실행하며, Hibernate의 SQL이 바뀌면 실패합니다.

**데이터베이스별 SQL (이 HQL에 대한 Hibernate의 SQL)** — 매뉴얼의 `Order`에는 NULL이 될 수 있는 `Double price`가 있지만 테스트의 `Order`에는 없으므로, `SqlByDatabaseTest`가 이 HQL을 Hibernate에 직접 넘깁니다. `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `readme-first`:

```sql
-- H2
-- (this HQL is given to Hibernate directly)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>?
order by o1_0.price desc,o1_0.id
fetch first ? rows only

-- MariaDB
-- (this HQL is given to Hibernate directly)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>?
order by o1_0.price desc,o1_0.id
limit ?

-- Oracle
-- (this HQL is given to Hibernate directly)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>?
order by o1_0.price desc nulls last,o1_0.id
fetch first ? rows only
```

<a id="choose-your-track"></a>
## 트랙 선택

이 매뉴얼에는 **두 개의 트랙**이 있습니다. 하나는 Java용, 하나는 Kotlin용입니다.
각 트랙에는 해당 언어의 코드만 있습니다. 쿼리를 작성하는 언어를 고르십시오.

| 트랙 | 시작 페이지 | 이런 분께 |
|---|---|---|
| **Java** | [Java 트랙](java/README.md) | 쿼리를 Java로 작성합니다. |
| **Kotlin** | [Kotlin 트랙](kotlin/README.md) | 쿼리를 Kotlin으로 작성합니다. |

두 트랙은 같은 엔진을 사용하므로, 같은 쿼리는 두 언어에서 같은 HQL을 만듭니다.
두 트랙의 장 번호와 파일 이름은 같습니다. 9장만 다릅니다(Java 노트 또는 Kotlin 노트).
모든 트랙 페이지에는 다른 트랙의 같은 페이지로 가는 링크가 있어 언제든지 전환할 수 있습니다.

<a id="common-pages"></a>
## 공통 페이지

다음 페이지는 두 언어 모두를 위한 것입니다. 코드는 Java와 Kotlin 섹션으로 나누어 보여 줍니다.

| 페이지 | 내용 |
|---|---|
| [1. 개요](01-overview.md) | lambdaQL이 무엇인지, 왜 만들었는지, 어떻게 동작하는지, 설계 원칙 |
| [11. 오류와 언어](11-errors-and-languages.md) | 오류 코드, 예외, 메시지 언어, 로깅 |

<a id="table-of-contents"></a>
## 목차

| # | 장 | Java | Kotlin |
|---|---|---|---|
| 1 | 개요 (공통) | [01-overview.md](01-overview.md) | [01-overview.md](01-overview.md) |
| 2 | 시작하기: 요구 사항, 소스에서 빌드, 첫 쿼리 | [Java](java/02-getting-started.md) | [Kotlin](kotlin/02-getting-started.md) |
| 3 | 조건(`where`): 연산자, null 규칙, 캡처 값 | [Java](java/03-where.md) | [Kotlin](kotlin/03-where.md) |
| 4 | 정렬과 페이징: `orderBy`, `Direction`, `limit`, `offset` | [Java](java/04-order-paging.md) | [Kotlin](kotlin/04-order-paging.md) |
| 5 | 서브쿼리: `exists`, `contains`, 스칼라 서브쿼리와 상관 서브쿼리 | [Java](java/05-subquery.md) | [Kotlin](kotlin/05-subquery.md) |
| 6 | 조회, 집계, 그룹화: `select`, `groupBy`, `having`, `Agg`, 조인(`join`, `leftJoin`, `crossJoin`), 파생 테이블과 CTE(`with`) | [Java](java/06-select-group.md) | [Kotlin](kotlin/06-select-group.md) |
| 7 | 수정, 삽입, 삭제: `set`, `values`, `all()`, `execute()`, `limit`, `executeOne()` | [Java](java/07-dml.md) | [Kotlin](kotlin/07-dml.md) |
| 7.6 | 업서트: `upsert`, `onConflict(...).update(...)`, `onConflict(...).ignore()` | [Java](java/07-6-upsert.md) | [Kotlin](kotlin/07-6-upsert.md) |
| 8 | 프로시저와 DB 함수: `@SqlProcedure`, `@SqlFunction`, `qb.call`, `qb.exec` | [Java](java/08-procedures-functions.md) | [Kotlin](kotlin/08-procedures-functions.md) |
| 9 | 언어별 노트 | [Java 노트](java/09-java-notes.md) | [Kotlin 노트](kotlin/09-kotlin-notes.md) |
| 10 | 규칙과 제한: 전체 규칙 표 | [Java](java/10-rules-and-limits.md) | [Kotlin](kotlin/10-rules-and-limits.md) |
| 11 | 오류와 언어 (공통) | [11-errors-and-languages.md](11-errors-and-languages.md) | [11-errors-and-languages.md](11-errors-and-languages.md) |
| 12 | Spring Data JPA: `LambdaQlExecutor<T>`, DML 단축 메서드, Spring `Sort`와 `Pageable`, 모듈, 트랜잭션, `QueryBuilder` 빈 | [Java](java/12-spring-data-jpa.md) | [Kotlin](kotlin/12-spring-data-jpa.md) |

<a id="status"></a>
## 상태

| 기능 | 상태 | Java | Kotlin |
|---|---|---|---|
| 람다 로딩(람다 본문을 찾고 바이트코드를 읽음) | 구현됨, 단위 테스트 있음 | [01](01-overview.md#how-it-works-5-steps) | [01](01-overview.md#how-it-works-5-steps) |
| 조건 디코딩(`&&`, `\|\|`, `!`, 비교, 캡처 값) | 구현됨, 단위 테스트 있음(Java 람다) | [03](java/03-where.md) | [03](kotlin/03-where.md) |
| getter를 JPA 속성에 매핑, 캡처 값 계산 | 구현됨, H2·MariaDB·Oracle에서 테스트함 | [03](java/03-where.md) | [03](kotlin/03-where.md) |
| HQL 렌더링과 쿼리 실행(`where`, `toList()`) | 구현됨, H2·MariaDB·Oracle에서 테스트함 | [03](java/03-where.md) | [03](kotlin/03-where.md) |
| 결과 함수(`first()`, `one()`, `count()`, `exists()`, `toSet()`) | 구현됨, H2·MariaDB·Oracle에서 테스트함(Java 람다) | [02](java/02-getting-started.md#getting-the-result) | [02](kotlin/02-getting-started.md#getting-the-result) |
| 정렬과 페이징(`orderBy`, `limit`, `offset`) | 핵심 기능 구현됨, H2·MariaDB·Oracle에서 테스트함. `SortSpec`, 조인 정렬 `(o, c, by)`, 외부 정렬의 정렬 정책(코드, Spring Boot 프로퍼티, `@Sortable` 규칙) 포함. `SortSpec`의 조인 형태: 설계됨 | [04](java/04-order-paging.md) | [04](kotlin/04-order-paging.md) |
| 서브쿼리(`exists`, `contains`, 스칼라, 상관) | 구현됨, H2·MariaDB·Oracle에서 테스트함(Java 람다). MariaDB `lateral` 재작성: 아직 구현되지 않음 | [05](java/05-subquery.md) | [05](kotlin/05-subquery.md) |
| 프로젝션, `groupBy`, `having`, 집계(`Agg`) | 구현됨, H2·MariaDB·Oracle에서 테스트함(Java 람다). `distinct()`와 결과 함수 포함. 집계 기준 정렬: v0.2 후보 | [06](java/06-select-group.md) | [06](kotlin/06-select-group.md) |
| 명시적 조인(`join`, `leftJoin`, `crossJoin`, 엔티티 3개까지) | 구현됨, H2·MariaDB·Oracle에서 테스트함(Java 람다). 모든 엔티티에 대한 `where`, `orderBy`, `select`, 페이징 포함. DML과 서브쿼리 안의 조인: 이후 | [06](java/06-select-group.md#68-explicit-joins) | [06](kotlin/06-select-group.md#68-explicit-joins) |
| 파생 테이블과 CTE(`qb.from(valueQuery)`, `qb.with(valueQuery)`) | 구현됨, H2·MariaDB·Oracle에서 테스트함(Java 람다). 파생 테이블과의 조인, 같은 CTE의 중복 사용: 이후 | [06](java/06-select-group.md#69-derived-table-subquery-in-from) | [06](kotlin/06-select-group.md#69-derived-table-subquery-in-from) |
| `update`, `insert`, `delete`, upsert | 구현됨. H2, MariaDB, Oracle에서 테스트(Java 람다). `limit`과 `executeOne()` 포함. 수정·삭제 `where` 안의 서브쿼리(`sub`)와 `insert ... select`의 집계: 구현됨. `set` 값 안의 서브쿼리: 구현됨 | [07](java/07-dml.md) | [07](kotlin/07-dml.md) |
| 저장 프로시저와 DB 함수 | 구현됨. 쿼리 안의 DB 함수와 단독 호출은 H2·MariaDB·Oracle에서 테스트함. 프로시저(`call`, `exec`, OUT, INOUT, 결과 집합)는 MariaDB와 Oracle에서 테스트함(H2에는 OUT 파라미터가 없음). 인자 이름 대조(MariaDB, Oracle에서 테스트함)와 Oracle 패키지 오버로드 검출(Oracle에서 테스트함): 구현됨. DB 함수의 SARGable 경고(`LQL-5001`, `LQL-5007`): 구현됨. 프로시저 파라미터의 타입 대조: 아직 구현되지 않음 | [08](java/08-procedures-functions.md) | [08](kotlin/08-procedures-functions.md) |
| Kotlin 형태(`?.`, `?:`, `!!`, 범위, `when`, `in`) | `?.`, `!!`, 캡처 컬렉션의 `in`, 데이터 클래스, `Pair`/`Triple`, 속성 대입: H2·MariaDB·Oracle에서 Kotlin 람다로 테스트함. `?:`, 범위, `when`, 속성 참조: 설계됨 | – | [09](kotlin/09-kotlin-notes.md) |
| 4개 언어의 오류 코드와 메시지 | 구현됨, 단위 테스트함: `[LQL-xxxx]` 코드, `LambdaQLException`과 하위 클래스, 영어·일본어·중국어·한국어 메시지, `lambdaql.locale`. 엄격 모드: 아직 구현되지 않음 | [11](11-errors-and-languages.md) | [11](11-errors-and-languages.md) |
| Spring Data JPA(`LambdaQlExecutor<T>`, Spring `Sort` / `Pageable`, Spring Boot 자동 구성) | 구현됨. H2, MariaDB, Oracle에서 Spring Boot 3.4.2로 테스트(Java). DML 단축 메서드(`deleteAll`, `updateOne` 등)와 수정·삭제·삽입·업서트 빌더 포함. Spring Boot 4: 아직 검증하지 않음 | [12](java/12-spring-data-jpa.md) | [12](kotlin/12-spring-data-jpa.md) |

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
| [SelectQueryTest.java](../../../query/src/test/java/org/lambdaql/query/SelectQueryTest.java) | `qb.from(...).where(...)`: H2, MariaDB, Oracle에서의 HQL, 파라미터, 행 |
| [SortQueryTest.java](../../../query/src/test/java/org/lambdaql/query/SortQueryTest.java) | `orderBy`, `limit`, `offset`: H2, MariaDB, Oracle에서의 HQL과 행 순서 |
| [SubqueryTest.java](../../../query/src/test/java/org/lambdaql/query/SubqueryTest.java) | `where` 안의 서브쿼리: `exists`, `contains`(IN), NOT EXISTS, 스칼라, top-1, 중첩, 서브쿼리를 쓰는 삭제. H2, MariaDB, Oracle에서의 HQL과 행 |
| [ProjectionTest.java](../../../query/src/test/java/org/lambdaql/query/ProjectionTest.java) | `select`, 레코드, `groupBy`, `having`, `Agg`, `distinct`, 페이징, `groupBy`를 쓰는 `insert ... select`. H2, MariaDB, Oracle에서의 HQL과 값 |
| [JoinQueryTest.java](../../../query/src/test/java/org/lambdaql/query/JoinQueryTest.java) | 엔티티 3개까지의 `join`, `leftJoin`, `crossJoin`: H2, MariaDB, Oracle에서의 조건, 정렬, `select`, 반복되는 행, `count`, 모든 엔티티에 대한 페이징 |
| [DerivedQueryTest.java](../../../query/src/test/java/org/lambdaql/query/DerivedQueryTest.java) | 파생 테이블과 CTE(`qb.from(valueQuery)`, `qb.with(valueQuery)`): H2, MariaDB, Oracle에서의 HQL과 행, 안쪽 `limit`, 행 타입 규칙 J5 |
| [DmlQueryTest.java](../../../query/src/test/java/org/lambdaql/query/DmlQueryTest.java) | `update`, `delete`, `insert`, upsert: H2, MariaDB, Oracle에서의 HQL과 실행 후의 행. 같은 조건의 조회 결과와 비교 |
| [FunctionTest.java](../../../query/src/test/java/org/lambdaql/query/FunctionTest.java) | `where`, `select`, `orderBy`, update `set` 안의 `@SqlFunction` DB 함수, Boolean 함수, 등록한 함수, 규칙 검사: H2, MariaDB, Oracle |
| [ProcedureTest.java](../../../query/src/test/java/org/lambdaql/query/ProcedureTest.java) | MariaDB와 Oracle에서 `qb.call`과 `qb.exec`로 하는 `@SqlProcedure` 호출(OUT, OUT 여러 개, INOUT, 결과 집합과 REF CURSOR)과 스텁 대조. H2, MariaDB, Oracle에서 DB 함수 단독 호출 |
| [KotlinSelectTest.kt](../../../query/src/test/kotlin/org/lambdaql/query/KotlinSelectTest.kt) | `where`의 Kotlin 람다: `?.` 경로, 캡처 값, 캡처 컬렉션의 `in` |
| [KotlinQueryTest.kt](../../../query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt) | Kotlin 람다: 정렬, 서브쿼리, 데이터 클래스·`Pair`·`Triple` 결과, `groupBy`와 `having`, update(속성 대입, `Dml.value`, `set` 값 안의 서브쿼리), insert, delete |
| [LqlMessagesTest.java](../../../query/src/test/java/org/lambdaql/i18n/LqlMessagesTest.java) | 메시지 코드와 4개 메시지 언어: 모든 번들의 같은 키와 인자, 코드 중복 없음, 로캘 선택, 코드 범위별 예외 클래스 |
| [ExecutorTest.java](../../../spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java) | Spring Boot 애플리케이션의 `LambdaQlExecutor<T>`: 리포지토리 메서드, Spring `Sort`와 `Pageable`, 트랜잭션 |
| [AutoConfigurationTest.java](../../../spring-data-jpa/src/test/java/org/lambdaql/spring/AutoConfigurationTest.java) | Spring Boot 자동 구성의 `QueryBuilder` 빈 |
| [LoadExamplesTest.java](../../../query/src/test/java/org/lambdaql/engine/load/LoadExamplesTest.java) | 람다 본문 찾기, 캡처 값 읽기, 직렬화할 수 없는 람다 거부 |
| [ConditionDecodeTest.java](../../../query/src/test/java/org/lambdaql/engine/decode/ConditionDecodeTest.java) | 바이트코드에서 `&&`, `\|\|`, `!`와 비교를 다시 만들기, 값 삼항 연산자 거부 |
| [IrExamplesTest.java](../../../query/src/test/java/org/lambdaql/engine/ir/IrExamplesTest.java) | 내부 조건 트리(비교 연산자, AND, OR, NOT) |
| [NegationMinimizerTest.java](../../../query/src/test/java/org/lambdaql/engine/decode/NegationMinimizerTest.java) | 결과를 바꾸지 않고 `!`를 비교 안으로 밀어 넣기(드 모르간) |
| [HostEvaluatorTest.java](../../../query/src/test/java/org/lambdaql/engine/sem/HostEvaluatorTest.java) | 바인딩 전에 캡처 변수로 Java에서 값 계산 |
| [JpaEntityModelTest.java](../../../query/src/test/java/org/lambdaql/engine/sem/JpaEntityModelTest.java) | JPA Metamodel에서 엔티티 속성 읽기(getter 매핑을 위한 초기 코드) |
| [AssignmentReaderTest.java](../../../query/src/test/java/org/lambdaql/engine/sem/AssignmentReaderTest.java) | 대입 람다(setter, `Dml.value`)를 컬럼과 값의 쌍으로 읽기, 규칙 위반(U1, U3, U6) 거부 |

<a id="about-the-examples"></a>
## 예제에 대하여

예제는 설계 예제입니다. 기능이 구현되면 해당 예제는 테스트 코드에서 가져온 코드로 바뀌고, 테스트 파일 링크가 붙습니다. 3. 조건, 4. 정렬과 페이징, 5. 서브쿼리, 6. 조회, 집계, 그룹화, 7. 수정, 삽입, 삭제, 8. 프로시저와 DB 함수, 11. 오류와 언어, 12. Spring Data JPA(Java)에서는 이런 예제에 **(테스트됨)** 표시가 있습니다. Kotlin 트랙도 Kotlin 테스트에서 가져온 예제에 같은 표시를 붙입니다.

<a id="example-entities"></a>
## 예제 엔티티

이 매뉴얼의 모든 예제는 다음 세 엔티티를 사용합니다.

| 엔티티 | 필드 |
|---|---|
| `Order` | `id`, `price`, `status`, `active`, `description`, `customer`(`Customer`로), `updateAt`, `items`(여러 `Item`으로) |
| `Customer` | `id`, `name`, `grade` |
| `Item` | `order`(`Order`로), `price`, `qty` |

엔티티 전체 코드는 시작하기에 있습니다: [Java](java/02-getting-started.md#example-entities) | [Kotlin](kotlin/02-getting-started.md#example-entities).

<a id="supported-environment"></a>
## 지원 환경

| 항목 | 버전 |
|---|---|
| Java | 21 |
| Kotlin | 2.x |
| Hibernate ORM | 6.6 (설계는 6.6.5로 검증) |
| 검증한 데이터베이스 | H2 2.3, MariaDB 10.11, Oracle 23.26 (23ai) |
| Spring Boot(선택) | 3.4(3.4.2로 테스트). Spring Boot 4는 아직 검증하지 않음 |

<a id="folder-layout"></a>
## 폴더 구성

```
manual/ko/
├── README.md                      이 페이지
├── 01-overview.md                 공통
├── 11-errors-and-languages.md     공통
├── java/                          Java 트랙 (Java 코드만)
│   ├── README.md
│   ├── 02-getting-started.md ... 08-procedures-functions.md
│   ├── 09-java-notes.md
│   └── 10-rules-and-limits.md
└── kotlin/                        Kotlin 트랙 (Kotlin 코드만)
    ├── README.md
    ├── 02-getting-started.md ... 08-procedures-functions.md
    ├── 09-kotlin-notes.md
    └── 10-rules-and-limits.md
```

모든 언어 폴더(`en`, `ko`, `ja`, `zh`)의 파일 이름은 같습니다. 언어를 바꾸려면 링크의 폴더 이름을 바꾸십시오.

<a id="help-and-contributions"></a>
## 도움말과 기여

- 소스 코드: <https://github.com/codebit0/lambdaql>
- 영어 매뉴얼이 원본입니다. 한국어, 일본어, 중국어 매뉴얼은 번역본입니다.
- 어느 언어에서든 잘못된 내용을 발견하면 이슈나 풀 리퀘스트를 열어 주십시오. 번역 도움도 환영합니다.
- 설계 문서(한국어, 기여자용): [ROADMAP.md](../../design/ROADMAP.md), [KOTLIN.md](../../design/KOTLIN.md)
