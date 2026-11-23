<a id="lambdaql-manual-kotlin-track"></a>
# lambdaQL 매뉴얼: Kotlin 트랙

**다른 언어:** [English](../../en/kotlin/README.md) | [한국어](../../ko/kotlin/README.md) | [日本語](../../ja/kotlin/README.md) | [简体中文](../../zh/kotlin/README.md)

[매뉴얼 홈](../README.md) | [Java 트랙](../java/README.md) | 공통 페이지: [1. 개요](../01-overview.md) · [11. 오류와 언어](../11-errors-and-languages.md) · [13. Spring Boot 3과 4](../13-spring-boot-versions.md)

lambdaQL 매뉴얼의 **Kotlin 트랙**입니다. 이 트랙의 모든 페이지에는 **Kotlin 코드만** 있습니다.
쿼리를 Java로 작성한다면 [Java 트랙](../java/README.md)을 읽으십시오. 두 트랙의 장 번호와 파일 이름은 같으므로(9장만 다름) 언제든지 전환할 수 있습니다.

```kotlin
val orders = qb.from(Order::class.java)
    .where { it.status == 1 && it.price!! > minPrice }
    .orderBy { o, by -> by.desc(o.price).asc(o.id) }
    .limit(20)
    .toList()
// HQL: select e0 from Order e0
//      where e0.status = 1 and e0.price > :p1
//      order by e0.price desc nulls last, e0.id asc
```

**SQL 블록 읽는 법.** `…`는 줄여 쓴 select 목록(엔티티의 모든 컬럼)이고, `?`는 바인드 파라미터입니다. 각 줄은 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)에서 나옵니다. 이 테스트는 각 쿼리를 Hibernate 7.4로 H2 2.3, MariaDB 10.11, Oracle 23.26에서 실행하며, Hibernate의 SQL이 바뀌면 실패합니다. 이 테스트는 Java 람다를 사용합니다. 의미가 같은 Kotlin 람다는 같은 HQL을 만들므로 SQL도 같습니다.

**데이터베이스별 SQL (이 HQL에 대한 Hibernate의 SQL, Kotlin도 같은 HQL을 만듦)** — 매뉴얼의 `Order`에는 NULL이 될 수 있는 `price`가 있지만 테스트의 `Order`에는 없으므로, `SqlByDatabaseTest`가 이 HQL을 Hibernate에 직접 넘깁니다. `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `readme-first`:

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
> **예제에 대하여.** 예제는 설계 예제입니다. 기능이 구현되면 해당 예제는 테스트 코드에서 가져온 코드로 바뀌고, 테스트 파일 링크가 붙습니다. Kotlin 테스트에서 가져온 예제에는 **(테스트됨)** 표시가 있습니다(3~7장).

<a id="contents"></a>
## 목차

| # | 페이지 | 배우는 내용 |
|---|---|---|
| 1 | [개요](../01-overview.md) (공통) | lambdaQL이 무엇인지, 왜 필요한지, 어떻게 동작하는지 |
| 2 | [시작하기](02-getting-started.md) | 요구 사항, Gradle 설정, Kotlin 엔티티, 첫 쿼리 |
| 3 | [조건(`where`)](03-where.md) | 연산자, null 규칙, `?.` `?:` `!!`, 범위, `when`, 캡처 값 |
| 4 | [정렬과 페이징](04-order-paging.md) | `orderBy`, `Direction`, `limit`, `offset` |
| 5 | [서브쿼리](05-subquery.md) | `exists()`, `x in sub...`, 스칼라 서브쿼리와 상관 서브쿼리, 서브쿼리 안의 조인 |
| 6 | [조회, 집계, 그룹화](06-select-group.md) | `select`, data class, `Pair`, `groupBy`, `having`, `Agg`, 조인, 파생 테이블, CTE |
| 7 | [수정, 삽입, 삭제](07-dml.md) | 속성 대입, `Dml.value`, `values`, `all()`, `execute()`, `limit`, `executeOne()` |
| 7.6 | [업서트](07-6-upsert.md) | `upsert`, `onConflict { ... }.update { ... }`, `onConflict { ... }.ignore()`, 데이터베이스별 행 수 |
| 8 | [프로시저와 DB 함수](08-procedures-functions.md) | `@SqlProcedure`, `companion object` 안의 `@SqlFunction`, `qb.call`, `qb.exec` |
| 9 | [Kotlin 노트](09-kotlin-notes.md) | Kotlin에서 특별한 점: `?.` `?:` `!!`, `==`, `in`, `when`, 연산자, 제한 KC1 ~ KC21 |
| 10 | [규칙과 제한](10-rules-and-limits.md) | 모든 규칙 표를 한곳에 |
| 11 | [오류와 언어](../11-errors-and-languages.md) (공통) | 오류 코드, 예외, 메시지 언어, 로깅 |
| 12 | [Spring Data JPA](12-spring-data-jpa.md) | `LambdaQlExecutor<T>`, 본문이 있는 리포지토리 메서드, DML 단축 메서드(`deleteAll`, `updateOne` 등), Spring `Sort`와 `Pageable`, 모듈, 트랜잭션, Kotlin 2.2 / `-Xjvm-default=all` |
| 13 | [Spring Boot 3과 4](../13-spring-boot-versions.md) (공통) | 버전 표, 아티팩트(공통 jar와 `-boot3`·`-boot4` 스타터), Hibernate 6.6과 7.4의 SQL 차이, 검증 범위 |

<a id="where-to-start"></a>
## 어디서부터 시작할까

1. 공통 [개요](../01-overview.md)를 읽고 기본 아이디어를 이해하십시오. 람다는 코드가 아니라 **쿼리**입니다. lambdaQL은 람다의 바이트코드를 읽을 뿐 절대 실행하지 않습니다.
2. [2. 시작하기](02-getting-started.md)로 프로젝트를 설정하십시오.
3. [3. 조건](03-where.md)에서 조건을 배우십시오. 가장 중요한 장입니다.
4. [9. Kotlin 노트](09-kotlin-notes.md)를 한 번 읽으십시오. 쿼리에서 다르게 동작하는 Kotlin 형태(`!!`, `?.`, `?:`, `==`, `===`)를 설명합니다.

<a id="example-entities"></a>
## 예제 엔티티

이 트랙의 모든 페이지는 다음 Kotlin 엔티티를 사용합니다. 전체 코드는 [2. 시작하기](02-getting-started.md#example-entities)에 있습니다.

| 엔티티 | 속성 |
|---|---|
| `Order` | `id: Long?`, `price: Double?`, `status: Int?`, `active: Boolean`, `description: String?`, `customer: Customer?`, `updateAt: LocalDateTime?`, `items: MutableList<Item>` |
| `Customer` | `id: Long?`, `name: String?`, `grade: Int?` |
| `Item` | `id: Long?`, `order: Order?`, `price: Double?`, `qty: Int` |

데이터베이스 컬럼은 NULL일 수 있으므로 대부분의 속성은 nullable `var` 속성입니다.
`active`와 `qty`는 Kotlin `Boolean`과 `Int`입니다. 이들은 JVM 기본형이므로 절대 NULL이 될 수 없습니다.

<a id="three-things-to-know-first"></a>
## 먼저 알아 둘 세 가지

| Kotlin | lambdaQL 람다 안에서 |
|---|---|
| `it.price!! > 100.0` | `!!`는 무시됩니다. 코드가 컴파일되도록 있을 뿐입니다. 람다는 실행되지 않으므로 `NullPointerException`은 없습니다. |
| `it.customer?.name == "Kim"` | `it.customer?.name`은 조인한 고객의 컬럼을 가리킵니다. 이 조건은 고객이 없는 주문과 맞을 수 없으므로 inner `join`이고, 고객이 없는 주문은 반환되지 않습니다. `?.`는 고객이 없는 주문이 조건을 참으로 만들 수 있을 때(`it.status == 1 \|\| ...`, `it.customer?.name == null`) `left join`이 됩니다(D54). `it.customer!!.name`도 같은 컬럼이지만 항상 inner join입니다. |
| `status: Int?`일 때 `it.status == 1` | `e0.status = 1`. `status`가 NULL이면 일치하지 않습니다(SQL 의미). lambdaQL은 Kotlin 컴파일러가 추가하는 null 검사를 무시합니다. **직접 작성한** null 검사만 의미가 있습니다. |

자세한 내용: [9. Kotlin 노트](09-kotlin-notes.md).

<a id="supported-environment"></a>
## 지원 환경

| 항목 | 버전 |
|---|---|
| Kotlin | 2.x (설계는 kotlinc 1.7.22, 2.1.10, 2.2.21, 2.3.0, 2.3.21로 확인) |
| Java (JVM) | 21 |
| Hibernate ORM | 6.6 또는 7.4 (6.6.53, 7.4.13으로 테스트) |
| 설계에서 검증한 데이터베이스 | H2 2.3, MariaDB 10.11, Oracle 23.26 (23ai) |
| Spring Boot(선택) | 3.5 또는 4.1(3.5.16, 4.1.2로 테스트, Java 테스트). [13. Spring Boot 3과 4](../13-spring-boot-versions.md) 참고 |

<a id="other-languages"></a>
## 다른 언어

모든 언어 폴더(`en`, `ko`, `ja`, `zh`)는 같은 구성입니다. 다른 언어로 페이지를 읽으려면 주소의 `ko`를 `en`, `ja`, `zh`로 바꾸십시오. 링크는 이 페이지 맨 위에 있습니다.

<a id="help-and-contributions"></a>
## 도움말과 기여

- 소스 코드: <https://github.com/codebit0/lambdaql>
- 한국어 매뉴얼이 원본입니다. 다른 언어는 번역본입니다.
- 잘못된 내용을 발견하면 이슈나 풀 리퀘스트를 열어 주십시오.
- 설계 문서(한국어, 기여자용): [ROADMAP.md](../../../design/ROADMAP.md), [KOTLIN.md](../../../design/KOTLIN.md)
