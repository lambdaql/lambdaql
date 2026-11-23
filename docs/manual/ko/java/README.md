<a id="lambdaql-manual-java-track"></a>
# lambdaQL 매뉴얼: Java 트랙

**다른 언어:** [English](../../en/java/README.md) | [한국어](../../ko/java/README.md) | [日本語](../../ja/java/README.md) | [简体中文](../../zh/java/README.md)

[매뉴얼 홈](../README.md) | [Kotlin 트랙](../kotlin/README.md) | 공통 페이지: [1. 개요](../01-overview.md) · [11. 오류와 언어](../11-errors-and-languages.md) · [13. Spring Boot 3과 4](../13-spring-boot-versions.md)

lambdaQL 매뉴얼의 **Java 트랙**입니다. 이 트랙의 모든 페이지에는 **Java 코드만** 있습니다.
쿼리를 Kotlin으로 작성한다면 [Kotlin 트랙](../kotlin/README.md)을 읽으십시오. 같은 번호의 같은 장으로 구성되어 있습니다.

lambdaQL은 Java와 Kotlin용 쿼리 라이브러리입니다.
평소처럼 람다를 작성하면 lambdaQL이 람다의 바이트코드를 읽어 Hibernate 7 HQL 쿼리를 만듭니다.
람다는 절대 실행되지 않습니다.

```java
List<Order> orders = qb.from(Order.class)
    .where(o -> o.getStatus() == 1 && o.getPrice() > minPrice)
    .orderBy((o, by) -> by.desc(o.getPrice()).asc(o.getId()))
    .limit(20)
    .toList();
// HQL: select e0 from Order e0
//      where e0.status = 1 and e0.price > :p1
//      order by e0.price desc nulls last, e0.id asc
```

**SQL 블록 읽는 법.** `…`는 줄여 쓴 select 목록(엔티티의 모든 컬럼)이고, `?`는 바인드 파라미터입니다. 각 줄은 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)에서 나옵니다. 이 테스트는 각 쿼리를 Hibernate 7.4로 H2 2.3, MariaDB 10.11, Oracle 23.26에서 실행하며, Hibernate의 SQL이 바뀌면 실패합니다.

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

> [!NOTE]
> **예제에 대하여.**
> 예제는 설계 예제입니다. 기능이 구현되면 해당 예제는 테스트 코드에서 가져온 코드로 바뀌고, 테스트 파일 링크가 붙습니다. [3. 조건](03-where.md), [4. 정렬과 페이징](04-order-paging.md), [5. 서브쿼리](05-subquery.md), [6. 조회, 집계, 그룹화](06-select-group.md), [7. 수정, 삽입, 삭제](07-dml.md), [8. 프로시저와 DB 함수](08-procedures-functions.md), [11. 오류와 언어](../11-errors-and-languages.md), [12. Spring Data JPA](12-spring-data-jpa.md)에서는 이런 예제에 **(테스트됨)** 표시가 있습니다.

<a id="contents"></a>
## 목차

"공통"으로 표시한 페이지는 Java 트랙과 Kotlin 트랙이 함께 사용합니다. 두 언어의 작은 예제가 섹션별로 나뉘어 있습니다.

| # | 페이지 | 배우는 내용 |
|---|---|---|
| 1 | [개요](../01-overview.md) (공통) | lambdaQL이 무엇인지, 왜 필요한지, 어떻게 동작하는지 |
| 2 | [시작하기](02-getting-started.md) | 요구 사항, 소스에서 빌드, 엔티티, `QueryBuilder`, 첫 쿼리 |
| 3 | [조건(`where`)](03-where.md) | 연산자, NULL 규칙, 캡처 값, 연관 경로 |
| 4 | [정렬과 페이징](04-order-paging.md) | `orderBy`, `Direction`, NULL 순서, `limit`, `offset` |
| 5 | [서브쿼리](05-subquery.md) | `exists`, `contains`(IN), NOT EXISTS, 스칼라 서브쿼리와 상관 서브쿼리 |
| 6 | [조회, 집계, 그룹화](06-select-group.md) | `select`, 레코드와 DTO, `Agg`, `groupBy`, `having`, 조인(`join`, `leftJoin`, `crossJoin`), 파생 테이블, CTE |
| 7 | [수정, 삽입, 삭제](07-dml.md) | `set`, `Dml.value`, `values`, `all()`, `execute()`, `limit`, `executeOne()` |
| 7.6 | [업서트(upsert)](07-6-upsert.md) | `upsert`, `onConflict(...).update(...)`, `onConflict(...).ignore()`, 데이터베이스별 행 수 |
| 8 | [프로시저와 DB 함수](08-procedures-functions.md) | `@SqlProcedure`, `@SqlFunction`, `qb.call`, `qb.exec` |
| 9 | [Java 노트](09-java-notes.md) | 박싱, 객체에 대한 `==`, `String` 비교, `Objects.equals`, 메서드 참조, `this` 캡처, 컴파일러 참고 사항 |
| 10 | [규칙과 제한](10-rules-and-limits.md) | 전체 규칙 표, Java와 SQL의 차이, 데이터베이스별 차이, 지원하지 않는 것 |
| 11 | [오류와 언어](../11-errors-and-languages.md) (공통) | 오류 코드, 예외, 메시지 언어, 로깅 |
| 12 | [Spring Data JPA](12-spring-data-jpa.md) | `LambdaQlExecutor<T>`, 리포지토리 default 메서드, DML 단축 메서드(`deleteAll`, `updateOne` 등), Spring `Sort`와 `Pageable`, 모듈, 트랜잭션, `QueryBuilder` 빈 |
| 13 | [Spring Boot 3과 4](../13-spring-boot-versions.md) (공통) | 버전 표, 아티팩트(`-boot3`, `-boot4`), Hibernate 6.6과 7.4의 SQL 차이, 검증 범위 |

<a id="example-entities"></a>
## 예제 엔티티

모든 예제는 다음 세 엔티티를 사용합니다. 전체 코드는 [시작하기](02-getting-started.md#example-entities)에 있습니다.

| 엔티티 | 필드 |
|---|---|
| `Order` | `id`, `price`, `status`, `active`, `description`, `customer`(`Customer`로), `updateAt`, `items`(여러 `Item`으로) |
| `Customer` | `id`, `name`, `grade` |
| `Item` | `order`(`Order`로), `price`, `qty` |

<a id="supported-environment"></a>
## 지원 환경

| 항목 | 버전 |
|---|---|
| Java | 21 |
| 컴파일러 | javac (Eclipse 컴파일러인 ECJ는 보장하지 않음) |
| Hibernate ORM | 6.6 또는 7.4 (6.6.53, 7.4.13으로 테스트) |
| 검증한 데이터베이스 | H2 2.3, MariaDB 10.11, MySQL 8.4, PostgreSQL 17, Oracle 23.26 (23ai) |
| Spring Boot(선택) | 3.5 또는 4.1(3.5.16, 4.1.2로 테스트). [13. Spring Boot 3과 4](../13-spring-boot-versions.md) 참고 |

<a id="help-and-contributions"></a>
## 도움말과 기여

- 소스 코드: <https://github.com/codebit0/lambdaql>
- 한국어 매뉴얼이 원본입니다. 영어, 일본어, 중국어 매뉴얼은 번역본입니다.
- 잘못된 내용을 발견하면 이슈나 풀 리퀘스트를 열어 주십시오.
- 설계 문서(한국어, 기여자용): [ROADMAP.md](../../../design/ROADMAP.md), [KOTLIN.md](../../../design/KOTLIN.md)
