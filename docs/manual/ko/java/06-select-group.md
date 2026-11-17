<a id="6-select-aggregates-and-group-by-java"></a>
# 6. 조회, 집계, 그룹화 (Java)

> [!NOTE]
> **상태**
> - **구현됨, 테스트함**(H2 2.3, MariaDB 10.11, Oracle 23.26, Hibernate 6.6.5, 마일스톤 M9, 결정 D27): 값 하나, Java 레코드, DTO의 `select`, 키 하나나 여러 개의 `groupBy`, `having`, `Agg` 함수, `distinct()`, `limit`, `offset`, `toList`, `toSet`, `first`, `one`, `orderBy(...).select(...)`, 규칙 J3, J4, J7의 검사.
> - **설계됨, 아직 구현되지 않음:** 파생 테이블과 CTE(6.8, 6.9, 마일스톤 M8-2), 집계 기준 정렬과 그룹 쿼리의 `orderBy`(v0.2 후보), 조인 프로젝션 `(o, c) -> ...`.
> - **(테스트됨)** 표시가 있는 예제는 테스트 코드에서 그대로 가져온 것입니다. 전체 상태는 [Java 트랙 README](README.md#status)를 참고하십시오.

[Java 트랙](README.md) | [매뉴얼 홈](../README.md) | 이전: [5. 서브쿼리](05-subquery.md) | 다음: [7. 수정, 삽입, 삭제](07-dml.md) | [이 페이지의 Kotlin 버전](../kotlin/06-select-group.md)

공통 페이지: [1. 개요](../01-overview.md) · [11. 오류와 언어](../11-errors-and-languages.md)

---

이 장에서는 쿼리가 **무엇을** 반환할지 고르는 방법을 설명합니다.

- 행마다 값 하나(`select(o -> o.getPrice())`)
- Java 레코드나 DTO 클래스
- `groupBy`, `having`과 함께 쓰는 집계(`count`, `sum`, `avg`, ...)
- 값 쿼리의 `distinct`, 페이징, 결과 메서드
- FROM 절의 서브쿼리(파생 테이블)와 CTE(`with`): 설계만 됨

<a id="contents"></a>
## 목차

- [6.1 예제 엔티티와 결과 타입](#61-example-entities-and-result-types)
- [6.2 값 하나 조회](#62-select-one-value)
- [6.3 레코드나 DTO 조회](#63-select-a-record-or-dto)
- [6.4 집계 함수: `Agg`](#64-aggregate-functions-agg)
- [6.5 그룹화(group by)](#65-group-by)
- [6.6 having](#66-having)
- [6.7 distinct, 페이징, 결과](#67-distinct-paging-and-results)
- [6.8 파생 테이블(FROM 안의 서브쿼리)](#68-derived-table-subquery-in-from)
- [6.9 CTE(`with`)](#69-cte-with)
- [6.10 규칙 J1~J8](#610-rules-j1-to-j8)
- [6.11 v0.1에 없는 것](#611-not-in-v01)

---

<a id="61-example-entities-and-result-types"></a>
## 6.1 예제 엔티티와 결과 타입

예제는 다음 엔티티를 사용합니다([2. 시작하기](02-getting-started.md#example-entities) 참고).

| 엔티티 | 여기서 사용하는 필드 |
|---|---|
| `Order` | `id`, `price`, `status`, `active`, `customer`(many-to-one) |
| `Customer` | `id`, `name`, `grade` |
| `Item` | `order`, `price`, `qty` |

**(테스트됨)** 표시가 있는 예제는 [`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java)에서 그대로 가져온 것입니다.
이 테스트는 HQL을 확인하고, 쿼리를 실행하고, 같은 계산을 Java로 한 결과와 값을 비교합니다. H2, MariaDB, Oracle에서 통과합니다.
테스트는 [3.3](03-where.md#33-basic-form)의 테스트 엔티티를 사용합니다. `price`는 `double`, `status`는 `short`, `Customer.id`는 `int`입니다. 테스트 데이터에는 고객이 없는 주문이 하나 있습니다.

테스트는 다음 결과 타입을 사용합니다.

```java
record OrderView(Long id, String customerName, double price) {}
record Stat(Integer customerId, long count, Double total) {}
record KeyCount(Integer customerId, short status, long count) {}
record Total(long count, Double average) {}
```

> [!TIP]
> NULL이 될 수 있는 값에는 박싱 타입(`Double`, `Long`, `Integer`)을 사용하십시오.
> 컬럼이나 `sum` 같은 집계는 NULL이 될 수 있습니다. 기본형 `double` 인자는 NULL을 담을 수 없으며, 이때 lambdaQL은 `IllegalStateException`을 던집니다(규칙 J4, [6.4](#null-in-aggregates-rule-j4) 참고).
> `count`는 절대 NULL이 아니므로 `long count`는 괜찮습니다.

---

<a id="62-select-one-value"></a>
## 6.2 값 하나 조회

`select`는 람다 하나를 받습니다. 람다는 값 하나를 반환합니다. 결과는 그 값의 `ValueQuery`입니다.
`select`는 `where` 뒤와 `orderBy` 뒤에서 호출할 수 있습니다. 정렬, `limit`, `offset`은 그대로 유지됩니다.

**(테스트됨)** [`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java), 영역 `value`:

```java
ValueQuery<Double> q = qb.from(Order.class)
        .where(o -> o.getStatus() == 1)
        .orderBy((o, by) -> by.asc(o.getPrice()))
        .select(o -> o.getPrice());
assertEquals("select e0.price from Order e0 where e0.status = 1 order by e0.price asc", q.toHql().hql());
```

**SQL 블록 읽는 법.** `?`는 바인드 파라미터입니다. 각 줄은 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)에서 나옵니다. 이 테스트는 각 쿼리를 Hibernate 6.6.5로 H2 2.3, MariaDB 10.11, Oracle 23.26에서 실행하며, Hibernate의 SQL이 바뀌면 실패합니다. 이 장에서는 select 목록이 핵심이므로 줄여 쓰지 않습니다.

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `value`:

```sql
-- H2, MariaDB, Oracle (same)
select o1_0.price
from orders o1_0
where o1_0.status=1
order by o1_0.price
```

연관 경로는 `where`에서와 마찬가지로 컬럼입니다(규칙 R24). `select`에서는 lambdaQL이 `left join`을 추가하므로 고객이 없는 주문도 빠지지 않습니다(`where`에서는 같은 경로가 기본적으로 inner join입니다).

```java
.select(o -> o.getCustomer().getName())
// HQL: select j1.name from Order e0 left join e0.customer j1
```

[6.3](#63-select-a-record-or-dto)의 레코드 예제가 이 `left join`을 테스트합니다.
경로가 연관 엔티티의 `@Id`로 끝나면 조인이 없습니다.
`o.getCustomer().getId()`는 외래 키 컬럼 `customer_id`를 읽습니다.

---

<a id="63-select-a-record-or-dto"></a>
## 6.3 레코드나 DTO 조회

람다 안에서 생성자를 호출하십시오. 생성자의 각 인자는 값 식입니다.
Java 레코드와 생성자가 있는 일반 DTO 클래스 모두 사용할 수 있습니다.

**(테스트됨)** [`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java), 영역 `record`:

```java
// only the columns are selected; the record is made in Java. The path uses a left join,
// so the order without a customer stays (customerName is null)
ValueQuery<OrderView> q = qb.from(Order.class)
        .select(o -> new OrderView(o.getId(), o.getCustomer().getName(), o.getPrice()));
assertEquals("select e0.id, j1.name, e0.price from Order e0 left join e0.customer j1", q.toHql().hql());
```

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `record`:

```sql
-- H2, MariaDB, Oracle (same)
select o1_0.id,c1_0.name,o1_0.price
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
```

lambdaQL은 `select new com.acme.OrderView(...)`를 쓰지 **않습니다**(규칙 J2).
컬럼을 조회하고 생성자는 Java에서 호출합니다.
이렇게 하면 긴 클래스 이름이나 중첩 클래스 이름의 문제를 피할 수 있습니다.
lambdaQL은 각 값을 생성자 인자의 타입으로 변환합니다(예: Hibernate의 `Long`을 `Integer` 인자로).

반환할 수 있는 것:

| 결과 타입 | 단순 `select` | 파생 테이블이나 CTE의 행 타입(설계됨) |
|---|---|---|
| 값 하나(`o.getPrice()`) | 가능 | – |
| Java 레코드 | 가능 | 가능 |
| 일반 DTO 클래스(생성자) | 가능 | **불가**(J5) |

---

<a id="64-aggregate-functions-agg"></a>
## 6.4 집계 함수: `Agg`

집계 함수는 **`org.lambdaql.Agg`의 static 메서드**입니다.
SQL 표준 함수이므로 lambdaQL이 제공합니다. (직접 만든 DB 함수는 [8. 프로시저와 DB 함수](08-procedures-functions.md)를 참고하십시오.)
`Agg`는 lambdaQL의 유일한 static 헬퍼 클래스입니다.

| 메서드 | SQL |
|---|---|
| `Agg.count()` | `count(*)` |
| `Agg.count(x)` | `count(x)` |
| `Agg.countDistinct(x)` | `count(distinct x)` |
| `Agg.sum(x)` | `sum(x)` |
| `Agg.avg(x)` | `avg(x)` |
| `Agg.min(x)` | `min(x)` |
| `Agg.max(x)` | `max(x)` |

Java 타입:

```java
long count();
<N extends Number> N sum(N x);
Double avg(Number x);
<R extends Comparable<?>> R min(R x);   // max도 같음
```

static import는 선택 사항입니다. 다음 두 줄은 같습니다.

```java
.select(o -> new Total(Agg.count(), Agg.avg(o.getPrice())))

import static org.lambdaql.Agg.*;
.select(o -> new Total(count(), avg(o.getPrice())))
```

중요한 점:

- 반환 값은 **자리 표시자**일 뿐입니다. 람다는 절대 실행되지 않으므로 값은 사용되지 않습니다.
- lambdaQL 람다 **밖에서** `Agg.sum(...)`을 호출하면 `IllegalStateException`을 던집니다.
- `Agg`는 `select`, `having`, `insert ... select`의 값([7.5](07-5-insert.md))에서 사용할 수 있습니다.
- `where`에서는 `Agg`를 사용할 수 없습니다. 집계에 대한 조건에는 `having`을 사용하십시오. `where`에서 `Agg`를 사용하면 분석 시점에 오류가 됩니다.

<a id="aggregate-over-all-rows-no-group-by"></a>
### 모든 행에 대한 집계(group by 없음)

**(테스트됨)** [`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java), 영역 `total`:

```java
// aggregates without groupBy: one row over all matching orders
ValueQuery<Total> q = qb.from(Order.class)
        .where(o -> o.getStatus() == 1)
        .select(o -> new Total(Agg.count(), Agg.avg(o.getPrice())));
assertEquals("select count(*), avg(e0.price) from Order e0 where e0.status = 1", q.toHql().hql());
```

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `total`:

```sql
-- H2, MariaDB, Oracle (same)
select count(*),avg(o1_0.price)
from orders o1_0
where o1_0.status=1
```

이 쿼리는 항상 한 행을 줍니다. 테스트는 `q.one()`으로 이 행을 읽습니다.

<a id="null-in-aggregates-rule-j4"></a>
### 집계의 NULL(규칙 J4)

| 함수 | 행이 없거나 모든 값이 NULL일 때 |
|---|---|
| `sum`, `avg`, `min`, `max` | `NULL` |
| `count` | `0` |

이는 정상적인 SQL 동작입니다.
NULL이 기본형 생성자 인자(`double`, `long`)에 들어가면 lambdaQL은 `IllegalStateException`을 던집니다. 메시지에는 해당 인자와 래퍼 타입(`Double`, `Long`)을 쓰라는 안내가 들어 있습니다. 테스트된 예제는 [6.10](#610-rules-j1-to-j8)에 있습니다.

---

<a id="65-group-by"></a>
## 6.5 그룹화(group by)

그룹 키는 `groupBy`로 작성합니다. lambdaQL은 키를 추측하지 않습니다.
`groupBy`는 그룹 쿼리를 반환합니다. `having(...)`(선택)을 호출한 다음 `select(...)`를 호출하십시오.

<a id="one-key"></a>
### 키 하나

```java
List<Stat> stats = qb.from(Order.class)
    .groupBy(o -> o.getCustomer().getId())
    .select(o -> new Stat(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice())))
    .toList();
// HQL: select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id
```

키가 `@Id`로 끝나므로 조인이 없습니다. 고객이 없는 주문은 키가 NULL인 그룹 하나가 됩니다.
`having`을 쓰는 테스트된 예제는 [6.6](#66-having)에 있습니다.

<a id="many-keys"></a>
### 키 여러 개

`List.of(...)`로 키 리스트를 반환하십시오.

**(테스트됨)** [`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java), 영역 `group-many`:

```java
// many keys: List.of(...) (Kotlin listOf(...), a to b, Triple)
ValueQuery<KeyCount> q = qb.from(Order.class)
        .where(o -> o.getCustomer() != null)
        .groupBy(o -> List.of(o.getCustomer().getId(), o.getStatus()))
        .select(o -> new KeyCount(o.getCustomer().getId(), o.getStatus(), Agg.count()));
assertEquals("select e0.customer.id, e0.status, count(*) from Order e0 where e0.customer is not null"
        + " group by e0.customer.id, e0.status", q.toHql().hql());
```

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `group-many`:

```sql
-- H2, MariaDB, Oracle (same)
select o1_0.customer_id,o1_0.status,count(*)
from orders o1_0
where o1_0.customer_id is not null
group by o1_0.customer_id,o1_0.status
```

키는 작성한 순서대로 사용됩니다.
람다 안에서 만든 컬렉션이 허용되는 유일한 곳입니다.

키 리스트로 허용되지 않는 것:

| 형태 | 이유 |
|---|---|
| `Set.of(a, b)` | 집합은 중복을 제거하고 순서가 없음 |

<a id="every-non-aggregate-value-must-be-a-key-rule-j3"></a>
### 집계가 아닌 값은 모두 키여야 함(규칙 J3)

쿼리에 집계가 있으면 `select`와 `having`에서 집계가 **아닌** 값은 모두 `groupBy` 키여야 합니다. 이는 SQL 규칙입니다. lambdaQL은 분석 시점에 이를 검사합니다.

```java
// 오류: o.getStatus()는 그룹 키가 아님
qb.from(Order.class)
  .groupBy(o -> o.getCustomer().getId())
  .select(o -> new KeyCount(o.getCustomer().getId(), o.getStatus(), Agg.count()));
// → LambdaAnalysisException
```

테스트된 예제는 [6.10](#610-rules-j1-to-j8)에 있습니다.

---

## 6.6 having

`having`은 그룹에 대한 조건입니다. `where`와 같은 람다 규칙([3. 조건(`where`)](03-where.md))을 사용하며, `Agg`를 사용할 수 있습니다.
`having`은 여러 번 호출할 수 있습니다. 조건은 `AND`로 연결됩니다.

**(테스트됨)** [`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java), 영역 `group-by`:

```java
ValueQuery<Stat> q = qb.from(Order.class)
        .groupBy(o -> o.getCustomer().getId())
        .having(o -> Agg.sum(o.getPrice()) > 200)
        .select(o -> new Stat(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice())));
assertEquals("select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id"
        + " having sum(e0.price) > 200.0", q.toHql().hql());
```

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `group-by`:

```sql
-- H2, MariaDB, Oracle (same)
select o1_0.customer_id,count(*),sum(o1_0.price)
from orders o1_0
group by o1_0.customer_id
having sum(o1_0.price)>200.0
```

여기서 `Agg.sum(o.getPrice())`는 `double`이므로 javac는 이를 `200.0`과 비교합니다. 상수는 리터럴로 남습니다(결정 D37).

---

<a id="67-distinct-paging-and-results"></a>
## 6.7 distinct, 페이징, 결과

`select(...)`는 `ValueQuery`를 반환합니다. `ValueQuery`의 메서드는 다음과 같습니다.

| 메서드 | 동작 |
|---|---|
| `distinct()` | `select distinct` |
| `limit(n)`, `offset(n)` | 페이징 |
| `toList()` | 쿼리를 실행하고 값을 순서대로 반환 |
| `toSet()` | `select distinct`로 쿼리를 실행하고 순서가 있는 `SequencedSet`을 반환 |
| `first()` | 첫 번째 값, 또는 `Optional.empty()` |
| `one()` | 유일한 값, 또는 `Optional.empty()`. 행이 2개 이상이면 `NonUniqueResultException` |
| `toHql()` | 쿼리를 실행하지 않고 HQL과 파라미터를 반환 |

**(테스트됨)** [`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java), 영역 `distinct`:

```java
ValueQuery<Double> q = qb.from(Order.class)
        .orderBy((o, by) -> by.desc(o.getPrice()))
        .select(o -> o.getPrice())
        .distinct();
assertEquals("select distinct e0.price from Order e0 order by e0.price desc", q.toHql().hql());
// toSet() always uses select distinct
List<Double> prices = q.toSet().stream().toList();
```

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `distinct`:

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

```java
ValueQuery<Double> paged = qb.from(Order.class)
        .orderBy((o, by) -> by.desc(o.getPrice()))
        .select(o -> o.getPrice())
        .limit(2).offset(1);
// HQL: select e0.price from Order e0 order by e0.price desc, e0.id asc
```

[`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java)의 테스트 메서드 `pagingAndFirst`가 이 HQL, `first()`, 페이지를 확인합니다.

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `value-page`:

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
## 6.8 파생 테이블(FROM 안의 서브쿼리)

> [!NOTE]
> **설계됨, 아직 구현되지 않음**(마일스톤 M8-2). 아래 HQL은 **예정된** 출력입니다.

`qb.from`에 람다를 전달하십시오. 람다는 서브쿼리 팩토리(`sub`)를 받아 안쪽 쿼리를 만듭니다.
안쪽 `select`가 행 타입을 정합니다. 바깥 쿼리에서는 그 행을 엔티티처럼 사용합니다.

```java
record CustomerTotal(Long customerId, long cnt, Double total) {}

List<CustomerTotal> rows = qb.from(sub -> sub.from(Order.class)
        .groupBy(o -> o.getCustomer().getId())
        .select(o -> new CustomerTotal(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice()))))
    .where(r -> r.total() > 100)
    .toList();
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

- 행 타입은 **Java 레코드**여야 합니다(규칙 J5).
  lambdaQL은 레코드 컴포넌트의 순서에서 컬럼 별칭을 가져옵니다.
- 여기서는 일반 DTO 클래스를 사용할 수 없습니다. lambdaQL이 생성자 파라미터의 이름을 알 수 없기 때문입니다.
- 바깥 람다에서는 레코드 접근자로 행을 읽으십시오: `r.total()`.

---

<a id="69-cte-with"></a>
## 6.9 CTE(`with`)

> [!NOTE]
> **설계됨, 아직 구현되지 않음**(마일스톤 M8-2). 아래 HQL은 **예정된** 출력입니다.

`qb.with(...)`로 핸들을 만드십시오. 그런 다음 `qb.from(...)`에서 그 핸들을 사용하십시오.
같은 핸들을 여러 번 사용할 수 있습니다.

```java
var t = qb.with(sub -> sub.from(Order.class)
        .groupBy(o -> o.getCustomer().getId())
        .select(o -> new CustomerTotal(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice()))));

List<CustomerTotal> rows = qb.from(t).where(r -> r.total() > 100).toList();
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
- 행 타입 규칙은 파생 테이블과 같습니다: 레코드(J5).
- 핸들에는 `var`를 사용하십시오. 핸들의 타입 이름은 확정되지 않았습니다.

---

<a id="610-rules-j1-to-j8"></a>
## 6.10 규칙 J1~J8

| # | 규칙 | 상태 |
|---|---|---|
| J1 | `select` 람다는 **값 식 하나**나 **생성자 호출 하나**를 반환합니다. 생성자의 각 인자는 값 식입니다([3. 조건(`where`)](03-where.md)의 규칙). `select`에만 쓰인 연관 경로는 `left join` 별칭이 되므로 행을 잃지 않습니다. 같은 경로를 `where`에서 쓰면 `select`는 `where`의 조인을 사용합니다(R24). | 구현됨(테스트함) |
| J2 | 생성자의 경우 lambdaQL은 컬럼만 조회하고 생성자는 Java에서 호출합니다. `select new`는 쓰지 않습니다. | 구현됨(테스트함) |
| J3 | 집계가 있으면 `select`와 `having`(그리고 `insert ... select`의 값)에서 집계가 아닌 값은 모두 `groupBy` 키여야 합니다. 그렇지 않으면 분석 시점에 오류가 발생합니다. | 구현됨(테스트함). 현재 오류는 `LambdaAnalysisException`이며, `TranslationException`은 설계 단계입니다([11](../11-errors-and-languages.md)) |
| J4 | `sum`/`avg`/`min`/`max`는 행이 없거나 모든 값이 NULL이면 NULL을 줍니다. `count`는 0을 줍니다. NULL이 될 수 있는 값에는 박싱 타입을 사용하십시오. 기본형 생성자 인자에 NULL이 오면 해당 인자를 알려 주는 `IllegalStateException`이 발생합니다. | 구현됨(테스트함) |
| J5 | 파생 테이블이나 CTE의 행 타입: **Java 레코드**(Kotlin 코드는 data class도 사용 가능). 일반 DTO 클래스는 단순 `select`에서만 허용됩니다. | 설계됨(M8-2) |
| J6 | `qb.with(...)`로 만든 CTE는 select 별칭으로 컬럼 이름을 정합니다: `with t as (select o.customer.id as customerId, ...) select x.customerId ... from t x`. HQL은 이름 뒤의 컬럼 목록을 받지 않습니다. Hibernate는 `with t (col list) as (...)`를 `SyntaxException`으로 거부합니다. 컬럼 목록은 Hibernate가 SQL에 직접 씁니다(MariaDB와 Oracle에서 `with t (customerId,cnt,total) as (...)`). H2는 CTE를 파생 테이블로 바꿉니다. 바깥 쿼리는 항상 별칭을 사용합니다(`from t x`). | 설계됨(M8-2) |
| J7 | `distinct()`는 `select distinct`를 렌더링합니다. 정렬과 함께 쓰면 정렬 키가 select 목록에 있어야 합니다(DB 규칙). lambdaQL은 분석 시점에 이를 검사합니다. | 구현됨(테스트함) |
| J8 | 조회는 정렬([4. 정렬과 페이징](04-order-paging.md)), 페이징, 서브쿼리([5. 서브쿼리](05-subquery.md))와 함께 동작합니다. 집계 기준 정렬(`by.desc(s.total())`)은 v0.2 후보입니다. | 구현됨. 집계 기준 정렬은 제외 |

**(테스트됨)** [`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java), 영역 `rules`:

```java
// J3: a value without an aggregate must be a group key
assertThrows(LambdaAnalysisException.class, () -> qb.from(Order.class)
        .groupBy(o -> o.getCustomer().getId())
        .select(o -> new KeyCount(o.getCustomer().getId(), o.getStatus(), Agg.count())));
// aggregates belong in having, not in where
assertThrows(LambdaAnalysisException.class, () -> qb.from(Order.class).where(o -> Agg.count() > 1));
// J7: with distinct, the sort key must be selected
assertThrows(LambdaAnalysisException.class, () -> qb.from(Order.class)
        .orderBy((o, by) -> by.asc(o.getId())).select(o -> o.getPrice()).distinct().toHql());
// J4: the group without orders gives NULL; a primitive argument cannot take it
assertThrows(IllegalStateException.class, () -> qb.from(Order.class)
        .where(o -> o.getStatus() == 9)
        .select(o -> new Bad(null, Agg.sum(o.getPrice()))).toList());
```

`Bad`는 `record Bad(Integer customerId, double total)`입니다. status가 9인 주문이 없으므로 `sum`은 NULL이고, 기본형 `double`은 이 값을 받을 수 없습니다.

---

<a id="611-not-in-v01"></a>
## 6.11 v0.1에 없는 것

| 항목 | 상태 |
|---|---|
| 집계 결과나 select 별칭으로 정렬(예: `orderBy((s, by) -> by.desc(s.total()))`), 그룹 쿼리의 `orderBy` | v0.2 후보 |
| 파생 테이블과 CTE(6.8, 6.9) | 설계됨, 아직 구현되지 않음(마일스톤 M8-2) |
| 사용자 집계 함수와 윈도 함수 | v0.2 후보 |
| 조인에 대한 프로젝션, `select((o, c) -> new OrderView(o.getId(), c.getName(), o.getPrice()))` | 계획됨. 조인 문법은 아직 확정되지 않음 |

현재 이 장의 오류(잘못된 그룹 키, `where` 안의 `Agg`, `distinct`에서 select하지 않은 정렬 키)는 `LambdaAnalysisException`입니다. `[LQL-xxxx]` 코드와 `LambdaQLException` 하위 클래스는 설계 단계입니다. [11. 오류와 언어](../11-errors-and-languages.md)를 참고하십시오.

---

[Java 트랙](README.md) | 이전: [5. 서브쿼리](05-subquery.md) | 다음: [7. 수정, 삽입, 삭제](07-dml.md) | [이 페이지의 Kotlin 버전](../kotlin/06-select-group.md)
