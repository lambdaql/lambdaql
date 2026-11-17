<a id="6-select-aggregates-and-group-by-kotlin"></a>
# 6. 조회, 집계, 그룹화 (Kotlin)

> [!NOTE]
> **상태**
> - **구현됨**(마일스톤 M9, 결정 D27). H2 2.3, MariaDB 10.11, Oracle 23.26(Hibernate 6.6.5)에서 테스트했습니다. 대상은 값 하나, 데이터 클래스, `Pair`, `Triple`의 `select`, 키 하나나 여러 키(`listOf`, `a to b`, `Triple`)의 `groupBy`, `having`, `Agg` 함수, `distinct()`, `limit`, `offset`, `toList`, `toSet`, `first`, `one`, `orderBy { }.select { }`, 규칙 J3, J4, J7의 검사입니다. 테스트는 **Java** 람다를 사용합니다. **Kotlin 람다는 아직 빌드 안에서 테스트하지 않았습니다.**
> - 이 장의 Kotlin 형태(데이터 클래스, `to`, `it.customer?.id`를 포함한 `listOf` 키, `Triple`, `select`와 `having` 안의 `Agg`)는 빌드 밖에서 kotlinc 2.3.21로 컴파일했습니다. lambdaQL은 이 형태에 대해 Java 형태와 같은 HQL을 만듭니다.
> - **설계됨, 아직 구현되지 않음:** 파생 테이블과 CTE(6.8, 6.9, 마일스톤 M8-2), 집계 기준 정렬과 그룹 쿼리의 `orderBy`(v0.2 후보), 조인 프로젝션 `{ o, c -> ... }`.
> - Kotlin 코드는 테스트된 Java 쿼리를 Kotlin 문법으로 옮긴 것입니다. 테스트된 Java 예제는 [이 페이지의 Java 버전](../java/06-select-group.md)에 있습니다. 전체 상태는 [트랙 README](README.md)를 참고하십시오.

[Kotlin 트랙](README.md) | [매뉴얼 홈](../README.md) | 이전: [5. 서브쿼리](05-subquery.md) | 다음: [7. 수정, 삽입, 삭제](07-dml.md) | [이 페이지의 Java 버전](../java/06-select-group.md)

공통 페이지: [1. 개요](../01-overview.md) · [11. 오류와 언어](../11-errors-and-languages.md)

---

이 장에서는 쿼리가 **무엇을** 반환할지 고르는 방법을 설명합니다.

- 행마다 값 하나(`select { it.price }`)
- Kotlin 데이터 클래스, `Pair`, `Triple`
- `groupBy`, `having`과 함께 쓰는 집계(`count`, `sum`, `avg`, ...)
- 값 쿼리의 `distinct`, 페이징, 결과 함수
- FROM 절의 서브쿼리(파생 테이블)와 CTE(`with`): 설계만 됨

<a id="contents"></a>
## 목차

- [6.1 예제 엔티티와 결과 타입](#61-example-entities-and-result-types)
- [6.2 값 하나 조회](#62-select-one-value)
- [6.3 데이터 클래스, Pair, Triple 조회](#63-select-a-data-class-pair-or-triple)
- [6.4 집계 함수: `Agg`](#64-aggregate-functions-agg)
- [6.5 Group by](#65-group-by)
- [6.6 Having](#66-having)
- [6.7 distinct, 페이징, 결과](#67-distinct-paging-and-results)
- [6.8 파생 테이블(FROM의 서브쿼리)](#68-derived-table-subquery-in-from)
- [6.9 CTE(`with`)](#69-cte-with)
- [6.10 규칙 J1 ~ J8](#610-rules-j1-to-j8)
- [6.11 v0.1에 없는 것](#611-not-in-v01)

---

<a id="61-example-entities-and-result-types"></a>
## 6.1 예제 엔티티와 결과 타입

예제는 다음 엔티티를 사용합니다([2. 시작하기](02-getting-started.md#example-entities) 참고).

| 엔티티 | 여기서 사용하는 속성 |
|---|---|
| `Order` | `id`, `price`, `status`, `active`, `customer`(many-to-one) |
| `Customer` | `id`, `name`, `grade` |
| `Item` | `order`, `price`, `qty` |

그리고 다음 결과 타입을 사용합니다.

```kotlin
data class OrderView(val id: Long?, val customerName: String?, val price: Double?)
data class Stat(val customerId: Long?, val count: Long, val total: Double?)
data class KeyCount(val customerId: Long?, val status: Int?, val count: Long)
data class Total(val count: Long, val average: Double?)
```

> [!TIP]
> 결과에는 nullable 타입(`Double?`, `String?`)을 사용하십시오.
> 컬럼이나 `sum` 같은 집계는 NULL일 수 있습니다(규칙 J4). non-null `Double`이나 `Long` 파라미터는 JVM 기본형이고, non-null `String` 파라미터도 NULL을 받지 않습니다.
> 이런 파라미터에 NULL이 들어가면 lambdaQL은 `IllegalStateException`을 던집니다. 기본형이면 메시지에 해당 인자와 래퍼 타입을 쓰라는 안내가 들어 있습니다.
> `count`는 절대 NULL을 내지 않으므로 `count: Long`은 괜찮습니다.

아직 Kotlin 테스트는 없습니다. 같은 쿼리를 Java로 작성한 테스트가 [`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java)에 있습니다(H2, MariaDB, Oracle). 테스트된 예제는 [이 페이지의 Java 버전](../java/06-select-group.md)을 참고하십시오.

---

<a id="62-select-one-value"></a>
## 6.2 값 하나 조회

`select`는 람다 하나를 받습니다. 람다는 값 하나를 반환합니다. 결과는 그 값의 `ValueQuery`입니다.
`select`는 `where` 뒤와 `orderBy` 뒤에서 호출할 수 있습니다. 정렬, `limit`, `offset`은 그대로 유지됩니다.

```kotlin
val prices: List<Double?> = qb.from(Order::class.java)
    .where { it.status == 1 }
    .orderBy { o, by -> by.asc(o.price) }
    .select { it.price }
    .toList()
// HQL: select e0.price from Order e0 where e0.status = 1 order by e0.price asc nulls first
```

여기서 `price`는 `Double?`이므로 정렬에 `nulls first`가 붙습니다([4.4](04-order-paging.md#44-null-ordering-is-the-same-on-every-database) 참고). Java 테스트에서는 `price`가 `double`이므로 그 HQL(예제 `value`)에는 `nulls` 부분이 없습니다.

**SQL 블록 읽는 법.** `?`는 바인드 파라미터입니다. 각 줄은 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)에서 나옵니다. 이 테스트는 각 쿼리를 Hibernate 6.6.5로 H2 2.3, MariaDB 10.11, Oracle 23.26에서 실행하며, Hibernate의 SQL이 바뀌면 실패합니다. 이 장에서는 select 목록이 핵심이므로 줄여 쓰지 않습니다. 이 테스트는 Java 람다를 사용합니다. 의미가 같은 Kotlin 람다는 같은 HQL을 만들므로 SQL도 같습니다.

**데이터베이스별 SQL (Java 테스트 실행 결과)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `value`:

```sql
-- H2, MariaDB, Oracle (same)
select o1_0.price
from orders o1_0
where o1_0.status=1
order by o1_0.price
```

연관 경로는 `where`에서와 마찬가지로 컬럼입니다(규칙 R24). `select`에서는 lambdaQL이 `left join`을 추가하므로 고객이 없는 주문도 빠지지 않습니다(`where`에서는 같은 경로가 기본적으로 inner join입니다).

```kotlin
.select { it.customer?.name }
// HQL: select j1.name from Order e0 left join e0.customer j1
```

`it.customer!!.name`도 같은 HQL을 만듭니다. [6.3](#63-select-a-data-class-pair-or-triple)의 데이터 클래스 예제에서 이 `left join`을 볼 수 있습니다.
경로가 연관 엔티티의 `@Id`로 끝나면 조인이 없습니다.
`it.customer?.id`는 외래 키 컬럼 `customer_id`를 읽습니다.

그 밖의 값 형태(Kotlin 전용 형태, 설계됨. [9. Kotlin 노트](09-kotlin-notes.md) 참고):

```kotlin
.select { it.description ?: "" }              // coalesce(e0.description, '')
.select { it.customer?.name ?: "none" }       // coalesce(j1.name, 'none') + left join
.select { (it.price ?: 0.0) * 2 }             // coalesce(e0.price, 0.0) * 2
```

boolean 엘비스(`select { it.flag ?: false }`)와 값 `if`/`when`은 거부됩니다(제한 KC21, KC6).

---

<a id="63-select-a-data-class-pair-or-triple"></a>
## 6.3 데이터 클래스, Pair, Triple 조회

람다 안에서 생성자를 호출하십시오. 생성자의 각 인자는 값 식입니다.

```kotlin
// 데이터 클래스
val list: List<OrderView> = qb.from(Order::class.java)
    .select { OrderView(it.id, it.customer?.name, it.price) }
    .toList()
// HQL: select e0.id, j1.name, e0.price from Order e0 left join e0.customer j1

// Pair(Triple도 가능)
val pairs: List<Pair<String?, Double?>> = qb.from(Order::class.java)
    .select { it.customer?.name to it.price }
    .toList()
// HQL: select j1.name, e0.price from Order e0 left join e0.customer j1
```

**데이터베이스별 SQL (Java 테스트 실행 결과, Kotlin도 같은 HQL을 만듦)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `record`(데이터 클래스 예제):

```sql
-- H2, MariaDB, Oracle (same)
select o1_0.id,c1_0.name,o1_0.price
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
```

lambdaQL은 `select new com.acme.OrderView(...)`를 쓰지 **않습니다**(규칙 J2).
컬럼을 조회하고 생성자는 lambdaQL이 직접 호출합니다. 데이터 클래스, `Pair`, `Triple` 모두 같습니다.
그래서 긴 클래스 이름이나 중첩 클래스 이름 때문에 생기는 문제를 피할 수 있습니다.
lambdaQL은 각 값을 생성자 파라미터의 타입으로 변환합니다(예: Hibernate의 `Integer`를 `Long?` 파라미터로).

반환할 수 있는 것:

| 결과 타입 | 단순 `select` | 파생 테이블이나 CTE의 행 타입(설계됨) |
|---|---|---|
| 값 하나(`it.price`) | 예 | – |
| Kotlin 데이터 클래스 | 예 | 예 |
| 생성자가 있는 일반 클래스 | 예 | **아니요**(J5) |
| `Pair` / `Triple` | 예 | 아니요 |
| Java record(Java 모듈에서) | 예 | 예 |

---

<a id="64-aggregate-functions-agg"></a>
## 6.4 집계 함수: `Agg`

집계 함수는 **`org.lambdaql.Agg`의 정적 메서드**입니다.
SQL 표준 함수이므로 lambdaQL이 제공합니다. (직접 만든 DB 함수는 [8. 프로시저와 DB 함수](08-procedures-functions.md)를 참고하십시오.)

| 함수 | SQL |
|---|---|
| `Agg.count()` | `count(*)` |
| `Agg.count(x)` | `count(x)` |
| `Agg.countDistinct(x)` | `count(distinct x)` |
| `Agg.sum(x)` | `sum(x)` |
| `Agg.avg(x)` | `avg(x)` |
| `Agg.min(x)` | `min(x)` |
| `Agg.max(x)` | `max(x)` |

Kotlin에서 보이는 타입:

| 함수 | 반환 |
|---|---|
| `count()`, `count(x)`, `countDistinct(x)` | `Long` |
| `sum(x)` | `x`의 타입(플랫폼 타입) |
| `avg(x)` | `Double`(SQL에서는 NULL일 수 있으므로 `Double?`로 다루십시오) |
| `min(x)`, `max(x)` | `x`의 타입(모든 `Comparable`, nullable도 괜찮음) |

import는 선택 사항입니다. 함수 하나를 import할 수 있습니다.

```kotlin
import org.lambdaql.Agg.sum

.having { sum(it.price) > 200 }
```

이는 `xs.sum()`이나 `xs.count()` 같은 Kotlin 표준 라이브러리 확장과 충돌하지 않습니다(컴파일러로 확인함).

중요한 점:

- 반환 값은 **자리 표시자**일 뿐입니다. 람다는 절대 실행되지 않으므로 그 값은 사용되지 않습니다.
- lambdaQL 람다 **밖에서** `Agg.sum(...)`을 호출하면 `IllegalStateException`을 던집니다.
- `Agg`는 `select`, `having`, `insert ... select`의 값([7.5](07-5-insert.md))에서 사용할 수 있습니다.
- `where`에서는 `Agg`를 사용할 수 없습니다. 집계에 대한 조건은 `having`을 사용하십시오. `where`에서 `Agg`를 사용하면 분석할 때 오류가 납니다.

<a id="aggregate-over-all-rows-no-group-by"></a>
### 모든 행에 대한 집계(group by 없음)

```kotlin
val t: Total? = qb.from(Order::class.java)
    .where { it.status == 1 }
    .select { Total(Agg.count(), Agg.avg(it.price)) }
    .one()
    .orElse(null)
// HQL: select count(*), avg(e0.price) from Order e0 where e0.status = 1
```

**데이터베이스별 SQL (Java 테스트 실행 결과, Kotlin도 같은 HQL을 만듦)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `total`:

```sql
-- H2, MariaDB, Oracle (same)
select count(*),avg(o1_0.price)
from orders o1_0
where o1_0.status=1
```

이 쿼리는 항상 한 행을 냅니다.

<a id="null-in-aggregates-rule-j4"></a>
### 집계의 NULL(규칙 J4)

| 함수 | 행이 없거나 모든 값이 NULL일 때 |
|---|---|
| `sum`, `avg`, `min`, `max` | `NULL` |
| `count` | `0` |

이는 정상적인 SQL 동작입니다. 이런 결과에는 nullable 파라미터(`Double?`)를 사용하십시오([6.1](#61-example-entities-and-result-types)의 팁 참고).

---

## 6.5 Group by

그룹 키는 `groupBy`로 작성합니다. lambdaQL은 키를 추측하지 않습니다.
`groupBy`는 그룹 쿼리를 반환합니다. `having { }`(선택)을 호출한 다음 `select { }`를 호출하십시오.

<a id="one-key"></a>
### 키 하나

```kotlin
val stats: List<Stat> = qb.from(Order::class.java)
    .groupBy { it.customer?.id }
    .select { Stat(it.customer?.id, Agg.count(), Agg.sum(it.price)) }
    .toList()
// HQL: select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id
```

키가 `@Id`로 끝나므로 조인이 없습니다. 고객이 없는 주문은 키가 NULL인 그룹 하나가 됩니다.
`having`을 쓰는 예제는 [6.6](#66-having)에 있습니다.

<a id="many-keys"></a>
### 여러 키

`listOf(...)`로 키 리스트를 반환하거나, `Pair`(`a to b`)나 `Triple`을 사용하십시오.

```kotlin
val counts: List<KeyCount> = qb.from(Order::class.java)
    .where { it.customer != null }
    .groupBy { listOf(it.customer?.id, it.status) }
    .select { KeyCount(it.customer?.id, it.status, Agg.count()) }
    .toList()
// HQL: select e0.customer.id, e0.status, count(*) from Order e0 where e0.customer is not null
//      group by e0.customer.id, e0.status

.groupBy { it.customer?.id to it.status }                     // Pair: 같은 결과
.groupBy { Triple(it.customer?.id, it.status, it.active) }    // 키 세 개
```

**데이터베이스별 SQL (Java 테스트 실행 결과, Kotlin도 같은 HQL을 만듦)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `group-many`:

```sql
-- H2, MariaDB, Oracle (same)
select o1_0.customer_id,o1_0.status,count(*)
from orders o1_0
where o1_0.customer_id is not null
group by o1_0.customer_id,o1_0.status
```

키는 작성한 순서대로 사용됩니다.
`groupBy`의 `listOf(...)`는 람다 안에서 만든 리스트가 허용되는 **유일한** 곳입니다.

키 리스트로 허용되지 않는 것:

| 형태 | 이유 |
|---|---|
| `arrayOf(a, b)` | 타입이 섞여 있으면 컴파일되지 않음 |
| `setOf(a, b)` | 집합은 중복을 없애고 순서가 없음 |

<a id="every-non-aggregate-value-must-be-a-key-rule-j3"></a>
### 집계가 아닌 값은 모두 키여야 함(규칙 J3)

쿼리에 집계가 있으면 `select`와 `having`에서 집계가 **아닌** 값은 모두 `groupBy` 키여야 합니다. 이는 SQL 규칙입니다. lambdaQL은 분석 시점에 이를 검사합니다.

```kotlin
// 오류: it.status는 그룹 키가 아님
qb.from(Order::class.java)
    .groupBy { it.customer?.id }
    .select { KeyCount(it.customer?.id, it.status, Agg.count()) }
// → LambdaAnalysisException
```

같은 검사를 Java로 테스트했습니다(영역 `rules`, [6.10](#610-rules-j1-to-j8) 참고).

---

## 6.6 Having

`having`은 그룹에 대한 조건입니다. `where`와 같은 람다 규칙([3. 조건(`where`)](03-where.md))을 사용하며, `Agg`를 사용할 수 있습니다.
`having`은 여러 번 호출할 수 있습니다. 조건은 `AND`로 연결됩니다.

```kotlin
val big: List<Stat> = qb.from(Order::class.java)
    .groupBy { it.customer?.id }
    .having { Agg.sum(it.price) > 200 }
    .select { Stat(it.customer?.id, Agg.count(), Agg.sum(it.price)) }
    .toList()
// HQL: select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id
//      having sum(e0.price) > 200.0
```

**데이터베이스별 SQL (Java 테스트 실행 결과, Kotlin도 같은 HQL을 만듦)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `group-by`:

```sql
-- H2, MariaDB, Oracle (same)
select o1_0.customer_id,count(*),sum(o1_0.price)
from orders o1_0
group by o1_0.customer_id
having sum(o1_0.price)>200.0
```

`Agg.sum`은 Java 메서드이므로 결과가 플랫폼 타입입니다. 그래서 `Agg.sum(it.price) > 200`은 `!!` 없이 컴파일됩니다. `!!`를 붙여도 lambdaQL은 이를 무시합니다(결정 D23).

---

<a id="67-distinct-paging-and-results"></a>
## 6.7 distinct, 페이징, 결과

`select { }`는 `ValueQuery`를 반환합니다. `ValueQuery`의 함수는 다음과 같습니다.

| 함수 | 동작 |
|---|---|
| `distinct()` | `select distinct` |
| `limit(n)`, `offset(n)` | 페이징 |
| `toList()` | 쿼리를 실행하고 값을 순서대로 반환 |
| `toSet()` | `select distinct`로 쿼리를 실행하고 순서가 있는 `SequencedSet`을 반환 |
| `first()` | 첫 번째 값(Java `Optional`) |
| `one()` | 유일한 값(Java `Optional`). 행이 2개 이상이면 `NonUniqueResultException` |
| `toHql()` | 쿼리를 실행하지 않고 HQL과 파라미터를 반환 |

```kotlin
val q = qb.from(Order::class.java)
    .orderBy { o, by -> by.desc(o.price) }
    .select { it.price }
    .distinct()
// HQL: select distinct e0.price from Order e0 order by e0.price desc nulls last
val prices = q.toSet()      // always select distinct
```

**데이터베이스별 SQL (Java 테스트 실행 결과)** — Java 테스트의 `price`는 `double`이므로 그 HQL에는 `nulls` 부분이 없습니다. `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `distinct`:

```sql
-- H2, MariaDB, Oracle (same)
select distinct o1_0.price
from orders o1_0
order by o1_0.price desc
```

- `distinct()`를 쓰면 모든 정렬 키가 select 값이어야 합니다(규칙 J7). 그렇지 않으면 lambdaQL은 분석 시점에 `LambdaAnalysisException`을 던집니다.
- 값 쿼리의 `toSet()`은 항상 `select distinct`를 사용합니다. 여기서는 값의 반복이 정상이므로 크기 검사를 하지 않습니다(엔티티의 `toSet()`과 다름, [2. 시작하기](02-getting-started.md#toset) 참고).

**페이징.** `limit`과 `offset`은 [4. 정렬과 페이징](04-order-paging.md)과 같게 동작합니다.
페이징을 쓰면 lambdaQL이 마지막 정렬 키로 `id`를 더하므로 페이지가 겹치지 않습니다(규칙 S9).
그룹 쿼리와 `distinct` 쿼리에는 더하지 않습니다. 이 쿼리들의 select 목록에는 `id`가 없기 때문입니다.

```kotlin
val page = qb.from(Order::class.java)
    .orderBy { o, by -> by.desc(o.price) }
    .select { it.price }
    .limit(2).offset(1)
    .toList()
// HQL: select e0.price from Order e0 order by e0.price desc nulls last, e0.id asc
```

**데이터베이스별 SQL (Java 테스트 실행 결과)** — Java 테스트의 `price`는 `double`이므로 그 HQL에는 `nulls` 부분이 없습니다. `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `value-page`:

```sql
-- H2, Oracle
select o1_0.price
from orders o1_0
order by o1_0.price desc,o1_0.id
offset ? rows
fetch first ? rows only

-- MariaDB
select o1_0.price
from orders o1_0
order by o1_0.price desc,o1_0.id
limit ?,?
```

---

<a id="68-derived-table-subquery-in-from"></a>
## 6.8 파생 테이블(FROM의 서브쿼리)

> [!NOTE]
> **설계됨, 아직 구현되지 않음**(마일스톤 M8-2). 아래 HQL은 **예정된** 출력입니다.

`qb.from`에 람다를 넘깁니다. 람다는 서브쿼리 팩토리(`sub`)를 받아 안쪽 쿼리를 만듭니다.
안쪽 `select`가 행 타입을 정합니다. 바깥 쿼리에서는 그 행을 엔티티처럼 사용합니다.

```kotlin
data class CustomerTotal(val customerId: Long?, val cnt: Long, val total: Double?)

val rows: List<CustomerTotal> = qb.from { sub ->
        sub.from(Order::class.java)
            .groupBy { it.customer?.id }
            .select { CustomerTotal(it.customer?.id, Agg.count(), Agg.sum(it.price)) }
    }
    .where { r -> r.total!! > 100 }
    .toList()
```

HQL(select 별칭 `customerId`, `cnt`, `total`이 컬럼 이름이 됨, 규칙 J5):

```sql
select r.customerId, r.cnt, r.total
from (select o.customer.id as customerId, count(*) as cnt, sum(o.price) as total
      from Order o group by o.customer.id) r
where r.total > 100
```

**예정 SQL 블록 읽는 법.** [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java)는 이 절과 6.9의 예정된 HQL을 Hibernate에 직접 넘기고, H2 2.3, MariaDB 10.11, Oracle 23.26(Hibernate 6.6.5)에서 나온 SQL을 기록합니다. lambdaQL은 아직 이 쿼리들을 만들지 않습니다. Hibernate의 SQL이 바뀌면 테스트가 실패합니다.

**데이터베이스별 SQL (예정)** — 예정된 HQL을 Hibernate가 번역한 것으로, `PlannedSqlTest`가 기록했습니다. lambdaQL은 아직 이 쿼리를 만들지 않습니다. `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)의 예제 `06-derived`:

```sql
-- H2
select r1_0.customerId,r1_0.cnt,r1_0.total
from (select o1_0.customer_id,count(*),sum(o1_0.price) from orders o1_0 group by o1_0.customer_id) r1_0(customerId,cnt,total)
where r1_0.total>100

-- MariaDB, Oracle
select r1_0.customerId,r1_0.cnt,r1_0.total
from (select o1_0.customer_id customerId,count(*) cnt,sum(o1_0.price) total from orders o1_0 group by o1_0.customer_id) r1_0
where r1_0.total>100
```

H2는 컬럼 목록을 별칭에 붙입니다(`r1_0(customerId,cnt,total)`). MariaDB와 Oracle은 서브쿼리 안에 컬럼 별칭을 씁니다.

규칙:

- 행 타입은 **Kotlin 데이터 클래스**(또는 Java record)여야 합니다(규칙 J5).
  lambdaQL은 데이터 클래스의 `componentN` 순서에서 컬럼 별칭을 가져옵니다.
- 여기서는 일반 클래스를 사용할 수 없습니다. lambdaQL이 생성자 파라미터의 이름을 알 수 없기 때문입니다.
- 바깥 람다에서는 행의 속성(`r.total`)으로 값을 읽습니다.

---

<a id="69-cte-with"></a>
## 6.9 CTE(`with`)

> [!NOTE]
> **설계됨, 아직 구현되지 않음**(마일스톤 M8-2). 아래 HQL은 **예정된** 출력입니다.

`qb.with { ... }`로 핸들을 만듭니다. 그런 다음 `qb.from(...)`에서 그 핸들을 사용합니다.
같은 핸들을 여러 번 사용할 수 있습니다.

```kotlin
val t = qb.with { sub ->
    sub.from(Order::class.java)
        .groupBy { it.customer?.id }
        .select { CustomerTotal(it.customer?.id, Agg.count(), Agg.sum(it.price)) }
}

val rows = qb.from(t).where { r -> r.total!! > 100 }.toList()
```

HQL(select 별칭 `customerId`, `cnt`, `total`이 컬럼 이름이 됨, 규칙 J5):

```sql
with t as (select o.customer.id as customerId, count(*) as cnt, sum(o.price) as total
           from Order o group by o.customer.id)
select x.customerId, x.cnt, x.total from t x where x.total > 100
```

**데이터베이스별 SQL (예정)** — 예정된 HQL을 Hibernate가 번역한 것으로, `PlannedSqlTest`가 기록했습니다. lambdaQL은 아직 이 쿼리를 만들지 않습니다. `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)의 예제 `06-cte`:

```sql
-- H2
select x1_0.customerId,x1_0.cnt,x1_0.total
from (select o1_0.customer_id,count(*),sum(o1_0.price) from orders o1_0 group by o1_0.customer_id) x1_0(customerId,cnt,total)
where x1_0.total>100

-- MariaDB, Oracle
with t (customerId,cnt,total) as (select o1_0.customer_id,count(*),sum(o1_0.price) from orders o1_0 group by o1_0.customer_id)
select x1_0.customerId,x1_0.cnt,x1_0.total
from t x1_0
where x1_0.total>100
```

MariaDB와 Oracle은 진짜 `with`를 실행합니다. 컬럼 목록은 Hibernate가 직접 씁니다: `with t (customerId,cnt,total) as (...)`. H2는 CTE를 파생 테이블로 바꿉니다.

참고:

- lambdaQL은 바깥 쿼리에서 CTE에 항상 별칭을 붙입니다(`from t x`). HQL에서 필요합니다(규칙 J6).
- CTE의 컬럼 이름은 select 별칭(`as customerId`)으로 정합니다. HQL은 CTE 이름 뒤의 컬럼 목록을 받지 않으며, 컬럼 목록은 Hibernate가 SQL에 추가합니다(규칙 J6).
- MariaDB와 Oracle은 실제 `with`를 실행합니다. H2에서는 Hibernate가 이를 파생 테이블로 펼칩니다. 결과는 같습니다(확인함).
- 행 타입 규칙은 파생 테이블과 같습니다: 데이터 클래스나 record(J5).

---

<a id="610-rules-j1-to-j8"></a>
## 6.10 규칙 J1 ~ J8

| # | 규칙 | 상태 |
|---|---|---|
| J1 | `select` 람다는 **값 식 하나**나 **생성자 호출 하나**를 반환합니다. 생성자의 각 인자는 값 식입니다([3. 조건(`where`)](03-where.md)의 규칙). `select`에만 쓰인 연관 경로는 `left join` 별칭이 되므로 행을 잃지 않습니다. 같은 경로를 `where`에서 쓰면 `select`는 `where`의 조인을 사용합니다(R24). | 구현됨(Java 람다로 테스트함) |
| J2 | 생성자의 경우 lambdaQL은 컬럼만 조회하고 생성자를 직접 호출합니다. `select new`는 쓰지 않습니다. `Pair`/`Triple`도 같은 방식으로 만듭니다. | 구현됨(Java 람다로 테스트함) |
| J3 | 집계가 있으면 `select`와 `having`(그리고 `insert ... select`의 값)에서 집계가 아닌 값은 모두 `groupBy` 키여야 합니다. 그렇지 않으면 분석 시점에 오류가 발생합니다. | 구현됨(Java 람다로 테스트함). 현재 오류는 `LambdaAnalysisException`이며, `TranslationException`은 설계 단계입니다([11](../11-errors-and-languages.md)) |
| J4 | `sum`/`avg`/`min`/`max`는 행이 없거나 모든 값이 NULL이면 NULL을 냅니다. `count`는 0을 냅니다. 생성자 파라미터는 nullable로 쓰십시오. non-null 파라미터에 NULL이 오면 `IllegalStateException`이 발생합니다. | 구현됨(Java 람다로 테스트함) |
| J5 | 파생 테이블이나 CTE의 행 타입: **Kotlin 데이터 클래스나 Java record만**. 일반 클래스는 단순 `select`에서만 허용됩니다. | 설계됨(M8-2) |
| J6 | `qb.with { }`로 만든 CTE는 select 별칭으로 컬럼 이름을 정합니다: `with t as (select o.customer.id as customerId, ...) select x.customerId ... from t x`. HQL은 이름 뒤의 컬럼 목록을 받지 않습니다. Hibernate는 `with t (col list) as (...)`를 `SyntaxException`으로 거부합니다. 컬럼 목록은 Hibernate가 SQL에 직접 씁니다(MariaDB와 Oracle에서 `with t (customerId,cnt,total) as (...)`). H2는 CTE를 파생 테이블로 바꿉니다. 바깥 쿼리는 항상 별칭을 사용합니다(`from t x`). | 설계됨(M8-2) |
| J7 | `distinct()`는 `select distinct`를 출력합니다. 정렬과 함께 쓰면 정렬 키가 select 목록에 있어야 합니다(DB 규칙). lambdaQL은 분석 시점에 이를 검사합니다. | 구현됨(Java 람다로 테스트함) |
| J8 | 조회는 정렬([4. 정렬과 페이징](04-order-paging.md)), 페이징, 서브쿼리([5. 서브쿼리](05-subquery.md))와 함께 동작합니다. 집계 기준 정렬(`by.desc(s.total)`)은 v0.2 후보입니다. | 구현됨. 집계 기준 정렬은 제외 |

[`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java)의 Java 테스트 영역 `rules`가 J3, `where` 안의 `Agg`, J7, J4를 확인합니다. [이 페이지의 Java 버전](../java/06-select-group.md#610-rules-j1-to-j8)을 참고하십시오.

---

<a id="611-not-in-v01"></a>
## 6.11 v0.1에 없는 것

| 항목 | 상태 |
|---|---|
| 집계 결과나 select 별칭으로 정렬(예: `orderBy { s, by -> by.desc(s.total) }`), 그룹 쿼리의 `orderBy` | v0.2 후보 |
| 파생 테이블과 CTE(6.8, 6.9) | 설계됨, 아직 구현되지 않음(마일스톤 M8-2) |
| 사용자 집계 함수와 윈도 함수 | v0.2 후보 |
| 조인에 대한 프로젝션, `select { o, c -> OrderView(o.id, c.name, o.price) }` | 계획됨. 조인 문법은 아직 확정되지 않음 |

현재 이 장의 오류(잘못된 그룹 키, `where` 안의 `Agg`, `distinct`에서 select하지 않은 정렬 키)는 `LambdaAnalysisException`입니다. `[LQL-xxxx]` 코드와 `LambdaQLException` 하위 클래스는 설계 단계입니다. [11. 오류와 언어](../11-errors-and-languages.md)를 참고하십시오.

---

[Kotlin 트랙](README.md) | 이전: [5. 서브쿼리](05-subquery.md) | 다음: [7. 수정, 삽입, 삭제](07-dml.md) | [이 페이지의 Java 버전](../java/06-select-group.md)
