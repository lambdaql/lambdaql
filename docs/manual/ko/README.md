<a id="lambdaql-user-manual-english"></a>
# lambdaQL 사용자 매뉴얼 (한국어)

**다른 언어:** [English](../en/README.md) | [한국어](../ko/README.md) | [日本語](../ja/README.md) | [简体中文](../zh/README.md)

lambdaQL은 JPA와 Hibernate 위에서 동작하는 Java·Kotlin용 쿼리 라이브러리입니다.
쿼리의 조건과 정렬을 람다 표현식으로 작성하면 lambdaQL이 람다 표현식의 바이트 opcode를 분석해 HQL 쿼리를 만듭니다.

<a id="a-first-look"></a>
## 예제

### Java

<!-- region: ManualSharedTest.java#readme-first -->
```java
List<Order> orders = qb.from(Order.class)
        .where(o -> o.getStatus() == 1 && o.getPrice() > minPrice)
        .orderBy(o -> desc(o.getPrice()).asc(o.getId()))
        .limit(20)
        .toList();
```

### Kotlin

<!-- region: ManualKotlinSharedTest.kt#readme-first -->
```kotlin
val orders = qb.from(Order::class.java)
    .where { it.status.toInt() == 1 && it.price > minPrice }
    .orderBy { o -> desc(o.price).asc(o.id) }
    .limit(20)
    .toList()
```

<a id="hql-the-same-for-both"></a>
### HQL

```
select e0 from Order e0
where e0.status = 1 and e0.price > :p1
order by e0.price desc, e0.id asc
```

예제 코드: [`ManualSharedTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualSharedTest.java)(`readme-first`), [`ManualKotlinSharedTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSharedTest.kt)(`readme-first`).

<a id="choose-your-track"></a>
## 트랙 선택

이 매뉴얼에는 **두 개의 트랙**이 있습니다. 하나는 Java용, 하나는 Kotlin용입니다.  
각 트랙에는 해당 언어의 코드만 있습니다. 쿼리를 작성하는 언어를 고르십시오.

| 트랙 | 시작 페이지 | 이런 분께 |
|---|---|---|
| **Java** | [Java 트랙](java/README.md) | 쿼리를 Java로 작성합니다. |
| **Kotlin** | [Kotlin 트랙](kotlin/README.md) | 쿼리를 Kotlin으로 작성합니다. |

두 트랙은 같은 엔진을 사용하므로, 같은 쿼리는 두 언어에서 같은 HQL을 만듭니다.

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
| 13 | Spring Boot 3과 4 (공통): 버전, 아티팩트, Hibernate 6.6과 7.4의 SQL 차이 | [13-spring-boot-versions.md](13-spring-boot-versions.md) | [13-spring-boot-versions.md](13-spring-boot-versions.md) |

<a id="about-the-examples"></a>
## 예제에 대하여

예제 코드는 실행 결과와 HQL 비교하는 검증 코드입니다. 각 절 끝의 '예제 코드' 링크에서 전체 코드를 볼 수 있습니다.

**데이터베이스별 SQL 블록 읽는 법.** `…`는 줄여 쓴 select 목록(엔티티의 모든 컬럼)이고, `?`는 바인드 파라미터입니다.

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
| Java | 21 이상 |
| Kotlin | 2.x 이상|
| Hibernate ORM | 6.6 또는 7.4 (6.6.53, 7.4.13으로 테스트) |
| 검증한 데이터베이스 | H2 2.3, MariaDB 10.11, MySQL 8.4, PostgreSQL 17, Oracle 23.26 (23ai) |
| Spring Boot(선택) | 3.5 또는 4.1(3.5.16, 4.1.2로 테스트). [13. Spring Boot 3과 4](13-spring-boot-versions.md) 참고 |

<a id="help-and-contributions"></a>
## 도움말과 기여

- 소스 코드: <https://github.com/codebit0/lambdaql>
- 어느 언어에서든 잘못된 내용을 발견하면 이슈나 풀 리퀘스트를 열어 주십시오. 번역 도움도 환영합니다.
- 설계 문서(한국어, 기여자용): [ROADMAP.md](../../design/ROADMAP.md), [KOTLIN.md](../../design/KOTLIN.md)
