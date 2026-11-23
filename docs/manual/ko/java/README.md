<a id="lambdaql-manual-java-track"></a>
# lambdaQL 매뉴얼: Java 트랙

**다른 언어:** [English](../../en/java/README.md) | [한국어](../../ko/java/README.md) | [日本語](../../ja/java/README.md) | [简体中文](../../zh/java/README.md)

[매뉴얼 홈](../README.md) | [Kotlin 트랙](../kotlin/README.md) | 공통 페이지: [1. 개요](../01-overview.md) · [11. 오류와 언어](../11-errors-and-languages.md) · [13. Spring Boot 3과 4](../13-spring-boot-versions.md)

lambdaQL 매뉴얼의 **Java 트랙**입니다. 이 트랙의 모든 페이지에는 **Java 코드만** 있습니다.
쿼리를 Kotlin으로 작성한다면 [Kotlin 트랙](../kotlin/README.md)을 읽으십시오. 같은 번호의 같은 장으로 구성되어 있습니다.

lambdaQL은 Java와 Kotlin용 쿼리 라이브러리입니다.
평소처럼 람다를 작성하면 lambdaQL이 람다의 바이트코드를 읽어 Hibernate 7 HQL 쿼리를 만듭니다.
람다는 절대 실행되지 않습니다.

[`ManualJavaStartTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaStartTest.java), 영역 `readme`:

```java
double minPrice = 100;
OrderedSelectQuery<Order> query = qb.from(Order.class)
        .where(o -> o.getStatus() == 1 && o.getPrice() > minPrice)
        .orderBy((o, by) -> by.desc(o.getPrice()).asc(o.getId()))
        .limit(20);
assertEquals("select e0 from Order e0 where e0.status = 1 and e0.price > :p1"
        + " order by e0.price desc, e0.id asc", query.toHql().hql());
List<Order> orders = query.toList();
```

`toHql()`은 쿼리를 실행하지 않고 HQL을 돌려줍니다. 예제의 `assertEquals`는 테스트가 확인하는 HQL입니다.
`price`와 `id`는 NULL이 될 수 없는 컬럼이므로 정렬에 `nulls` 부분이 없습니다.

> [!NOTE]
> **예제에 대하여.**
> 예제 코드는 모두 Java 테스트 코드의 한 구간을 그대로 옮긴 것입니다. 코드 위에 테스트 파일과 구간 이름(영역 `name`)을 적습니다. 테스트는 lambdaQL이 만든 HQL과 반환한 행을 확인합니다.

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
| 13 | [Spring Boot 3과 4](../13-spring-boot-versions.md) (공통) | 버전 표, 아티팩트(공통 jar와 `-boot3`·`-boot4` 스타터), Hibernate 6.6과 7.4의 SQL 차이, 검증 범위 |

<a id="example-entities"></a>
## 예제 엔티티

예제는 주로 다음 세 테스트 엔티티를 사용합니다. 속성의 타입과 원본 파일은 [시작하기](02-getting-started.md#example-entities)에 있습니다.

| 엔티티 | 필드 |
|---|---|
| `Order` | `id`, `price`, `status`, `active`, `description`, `kind`, `customer`(`Customer`로), `updateAt`, `total`, `cnt`, `custName`, `address` |
| `Customer` | `id`, `name`, `grade`, `orders`(여러 `Order`로) |
| `Item` | `id`, `order`(`Order`로), `price`, `qty` |

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
