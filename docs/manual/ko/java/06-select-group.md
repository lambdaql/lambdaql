<a id="6-select-aggregates-and-group-by-java"></a>
# 6. 조회, 집계, 그룹화 (Java)

[Java 트랙](README.md) | [매뉴얼 홈](../README.md) | 이전: [5. 서브쿼리](05-subquery.md) | 다음: [7. 수정, 삽입, 삭제](07-dml.md) | [이 페이지의 Kotlin 버전](../kotlin/06-select-group.md)

공통 페이지: [1. 개요](../01-overview.md) · [11. 오류와 언어](../11-errors-and-languages.md)

---

이 장에서는 쿼리가 **무엇을** 반환할지 고르는 방법을 설명합니다.

- 행마다 값 하나(`select(o -> o.getPrice())`)
- Java 레코드나 DTO 클래스
- `groupBy`, `having`과 함께 쓰는 집계(`count`, `sum`, `avg`, ...)
- 값 쿼리의 `distinct`, 페이징, 결과 메서드
- 다른 엔티티와의 명시적 조인(`join`, `leftJoin`, `crossJoin`)
- FROM 절의 서브쿼리(파생 테이블)와 CTE(`with`)

<a id="contents"></a>
## 목차

- [6.1 예제 엔티티와 결과 타입](#61-example-entities-and-result-types)
- [6.2 값 하나 조회](#62-select-one-value)
- [6.3 레코드나 DTO 조회](#63-select-a-record-or-dto)
- [6.4 집계 함수: `Agg`](#64-aggregate-functions-agg)
- [6.5 그룹화(group by)](#65-group-by)
- [6.6 having](#66-having)
- [6.7 distinct, 페이징, 결과](#67-distinct-paging-and-results)
- [6.8 명시적 조인](#68-explicit-joins)
- [6.9 파생 테이블(FROM 안의 서브쿼리)](#69-derived-table-subquery-in-from)
- [6.10 CTE(`with`)](#610-cte-with)
- [6.11 규칙 J1~J8](#611-rules-j1-to-j8)
- [6.12 v0.1에 없는 것](#612-not-in-v01)

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
`optional = false`로 매핑한 필수 연관은 행을 잃지 않으므로 `select`에서도 inner join입니다([4.5](04-order-paging.md#45-what-a-sort-key-can-be) 참고).

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

| 결과 타입 | 단순 `select` | 파생 테이블이나 CTE의 행 타입([6.9](#69-derived-table-subquery-in-from)) |
|---|---|---|
| 값 하나(`o.getPrice()`) | 가능 | – |
| Java 레코드 | 가능 | 가능 |
| 일반 DTO 클래스(생성자) | 가능 | **불가**(J5) |

---

<a id="64-aggregate-functions-agg"></a>
## 6.4 집계 함수: `Agg`

집계 함수는 **`org.lambdaql.Agg`의 정적 메서드**입니다.
SQL 표준 함수이므로 lambdaQL이 제공합니다. (직접 만든 DB 함수는 [8. 프로시저와 DB 함수](08-procedures-functions.md)를 참고하십시오.)
static 헬퍼 클래스는 `Agg`와 DML 대입용 `Dml`(결정 D49) 두 개뿐입니다.

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
NULL이 기본형 생성자 인자(`double`, `long`)에 들어가면 lambdaQL은 `IllegalStateException`을 던집니다. 메시지에는 해당 인자와 래퍼 타입(`Double`, `Long`)을 쓰라는 안내가 들어 있습니다. 테스트된 예제는 [6.11](#611-rules-j1-to-j8)에 있습니다.

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
// → TranslationException(LQL-3035)
```

테스트된 예제는 [6.11](#611-rules-j1-to-j8)에 있습니다.

---

<a id="66-having"></a>
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

- `distinct()`를 쓰면 모든 정렬 키가 select 값이어야 합니다(규칙 J7). 그렇지 않으면 lambdaQL은 분석 시점에 `TranslationException`(`LQL-3046`)을 던집니다.
- 값 쿼리의 `toSet()`은 항상 `select distinct`를 사용합니다. 여기서는 값의 반복이 정상이므로 크기 검사를 하지 않습니다(엔티티의 `toSet()`과 다름, [2. 시작하기](02-getting-started.md#toset) 참고).
- `@Lob` 컬럼을 `distinct`나 `groupBy` 키로 쓰면 Oracle은 거부합니다(ORA-22848, ORA-22849). H2와 MariaDB는 실행합니다. lambdaQL은 쿼리를 바꾸지 않고 `LQL-5010`을 `DEBUG` 수준으로 기록합니다([4.5](04-order-paging.md#45-what-a-sort-key-can-be) 참고).

**페이징.** `limit`과 `offset`은 [4. 정렬과 페이징](04-order-paging.md)과 같게 동작합니다.
페이징을 쓰면 lambdaQL이 마지막 정렬 키로 `id`를 더하므로 페이지가 겹치지 않습니다(규칙 S9). 정렬 키가 유일하고 NULL이 될 수 없는 컬럼이면 더하지 않습니다([4.8](04-order-paging.md#tie-breaker-automatic-id) 참고).
`first()`도 한 행만 읽으므로 같은 보조 정렬을 더합니다. 정렬 키 값이 같은 행이 여러 개이면 매번 `id`가 가장 작은 행의 값을 반환합니다.
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

<a id="68-explicit-joins"></a>
## 6.8 명시적 조인

연관 경로(`o.getCustomer().getName()`)는 to-one 연관을 자동으로 조인합니다([3.10](03-where.md#310-association-paths) 참고).
다른 엔티티를 직접 정한 조건으로 조인하거나 조건 없이 조인하려면 `join`, `leftJoin`, `crossJoin`을 사용하십시오(결정 D44, D45).

| 메서드 | HQL | 행 |
|---|---|---|
| `join(Customer.class, (o, c) -> ...)` | `join Customer e1 on ...` | inner join: 짝이 있는 행만 |
| `leftJoin(Customer.class, (o, c) -> ...)` | `left join Customer e1 on ...` | 짝이 없는 주 엔티티의 행도 남음 |
| `crossJoin(Customer.class)` | `cross join Customer e1` | 모든 행의 조합. 조건은 `where`에 작성 |

조인 뒤의 모든 람다는 조인 순서대로 모든 엔티티를 받습니다.
`where((o, c) -> ...)`, `orderBy((o, c, by) -> ...)`(`by`는 마지막, 규칙 S12), `select((o, c) -> ...)`입니다.
조인한 엔티티의 별칭은 `e1`, `e2`입니다.

**(테스트됨)** 표시가 있는 예제는 [`JoinQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java)에서 그대로 가져온 것입니다.
테스트는 각 쿼리를 H2, MariaDB, Oracle에서 실행하고, 결과 행을 Java로 같은 계산을 한 결과와 비교합니다.

**(테스트됨)** [`JoinQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java), 영역 `join`:

```java
SelectJoinQuery<Order, Customer> q = qb.from(Order.class)
        .join(Customer.class, (o, c) -> o.getCustomer().getId() == c.getId())
        .where((o, c) -> c.getGrade() != null && c.getGrade() >= 5);
assertEquals("select e0 from Order e0 join Customer e1 on e0.customer.id = e1.id"
        + " where e1.grade is not null and e1.grade >= 5", q.toHql().hql());
```

**결과는 주 엔티티입니다.** `select`가 없으면 `toList()`는 `List<Order>`를 반환합니다.
조인한 엔티티는 조건, 정렬, `select`에만 사용합니다.
조인한 엔티티의 값을 읽으려면 `select((o, c) -> ...)`를 사용하십시오(아래 참고).

<a id="left-join-and-null"></a>
### left join과 NULL

**(테스트됨)** [`JoinQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java), 영역 `left-join`:

```java
// c is missing for an order without a customer: c == null finds those rows
SelectJoinQuery<Order, Customer> q = qb.from(Order.class)
        .leftJoin(Customer.class, (o, c) -> o.getCustomer().getId() == c.getId())
        .where((o, c) -> c == null || c.getName().equals("Kim"));
assertEquals("select e0 from Order e0 left join Customer e1 on e0.customer.id = e1.id"
        + " where e1.id is null or e1.name = 'Kim'", q.toHql().hql());
```

- `c == null`은 "짝이 없음"을 뜻합니다. lambdaQL은 이를 `e1.id is null`(조인한 엔티티의 `@Id`)로 씁니다.
- `leftJoin` 뒤에 조인한 엔티티의 컬럼에 거는 조건은 **SQL의 의미**를 따릅니다. 짝이 없으면 컬럼이 NULL이므로 조건이 참이 아니고, 그 행은 결과에서 빠집니다.
  Java에서는 없는 `c`에 `c.getName()`을 호출하면 `NullPointerException`이 발생하지만, lambdaQL은 이 동작을 따르지 않습니다.
  짝이 없는 행을 남기려면 예제처럼 `c == null || ...`을 작성하십시오.

<a id="sort-and-select"></a>
### 정렬과 select

**(테스트됨)** [`JoinQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java), 영역 `sort`:

```java
OrderedJoinQuery<Order, Customer> q = qb.from(Order.class)
        .leftJoin(Customer.class, (o, c) -> o.getCustomer().getId() == c.getId())
        .orderBy((o, c, by) -> by.asc(c.getName()).asc(o.getId()));
assertEquals("select e0 from Order e0 left join Customer e1 on e0.customer.id = e1.id"
        + " order by e1.name asc nulls first, e0.id asc", q.toHql().hql());
```

[4.4](04-order-paging.md#44-null-ordering-is-the-same-on-every-database)의 NULL 정렬 규칙이 적용됩니다. 고객이 없는 주문은 `e1.name`이 NULL이므로 `asc`에서 앞에 옵니다.

**(테스트됨)** [`JoinQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java), 영역 `select`:

```java
ValueQuery<Row> rows = qb.from(Order.class)
        .leftJoin(Customer.class, (o, c) -> o.getCustomer().getId() == c.getId())
        .orderBy((o, c, by) -> by.asc(o.getId()))
        .select((o, c) -> new Row(o.getId(), c.getName()));
assertEquals("select e0.id, e1.name from Order e0 left join Customer e1 on e0.customer.id = e1.id"
        + " order by e0.id asc", rows.toHql().hql());
```

`Row`는 `record Row(Long orderId, String customerName)`입니다. 고객이 없는 주문은 `customerName`이 `null`입니다.
`select`는 `ValueQuery`를 반환하므로 `distinct()`, `limit`, `offset`, [6.7](#67-distinct-paging-and-results)의 결과 메서드를 사용할 수 있습니다.

<a id="three-entities-and-repeated-rows"></a>
### 엔티티 3개와 반복되는 행

엔티티는 3개까지 조인할 수 있습니다. 세 번째 조인 람다는 세 엔티티를 모두 받습니다: `(o, c, i) -> ...`.

**(테스트됨)** [`JoinQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java), 영역 `three`:

```java
// orders of a customer with an item of quantity > 10; an order repeats once per matching item
SelectJoin3Query<Order, Customer, Item> q = qb.from(Order.class)
        .join(Customer.class, (o, c) -> o.getCustomer().getId() == c.getId())
        .join(Item.class, (o, c, i) -> i.getOrder().getId() == o.getId())
        .where((o, c, i) -> i.getQty() > 10);
assertEquals("select e0 from Order e0 join Customer e1 on e0.customer.id = e1.id"
        + " join Item e2 on e2.order.id = e0.id where e2.qty > 10", q.toHql().hql());
```

> [!WARNING]
> 조인 조건에 맞는 행이 여러 개이면 **주 엔티티의 행이 반복됩니다**. 짝 하나마다 한 번씩입니다. `toList()`와 `count()`는 반복된 행을 모두 셉니다(테스트함).
> 주문마다 한 번만 필요하면 조인 대신 `exists()` 서브쿼리([5. 서브쿼리](05-subquery.md))를 사용하거나, 조인 쿼리에 `distinct()`를 적용하십시오([조인 결과의 `distinct()`](#distinct-on-a-join) 참고).

<a id="cross-join"></a>
### cross join

`crossJoin`에는 조인 조건이 없습니다. 메서드 이름으로 모든 행의 조합을 원한다는 의도를 나타냅니다. 조건은 `where`에 작성하십시오.

**(테스트됨)** [`JoinQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java), 영역 `cross-join`:

```java
// every pair of rows; the conditions go in where
SelectJoinQuery<Order, Customer> q = qb.from(Order.class)
        .crossJoin(Customer.class)
        .where((o, c) -> c.getGrade() != null && o.getPrice() > 100);
assertEquals("select e0 from Order e0 cross join Customer e1 where e1.grade is not null and e0.price > 100.0",
        q.toHql().hql());
```

lambdaQL에는 `from(Order.class, Customer.class)`(콤마 조인)가 없습니다. 조건을 빠뜨리면 실수로 모든 조합을 얻고, 어느 엔티티가 결과인지도 불분명하기 때문입니다(결정 D45). 그런 조회에는 `crossJoin`을 사용하십시오.

<a id="paging"></a>
### 페이징

페이징하면 lambdaQL은 **모든** 엔티티의 id를 마지막 정렬 키로 덧붙입니다. 그래서 페이지가 겹치지 않습니다(규칙 S9).

**(테스트됨)** [`JoinQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java), 영역 `cross-join-paging`:

```java
// with paging, the id of every entity is added to the order, so pages never overlap (S9)
ValueQuery<Row> pairs = qb.from(Order.class)
        .crossJoin(Customer.class)
        .orderBy((o, c, by) -> by.desc(o.getPrice()))
        .select((o, c) -> new Row(o.getId(), c.getName()));
assertEquals("select e0.id, e1.name from Order e0 cross join Customer e1 order by e0.price desc, e0.id asc, e1.id asc",
        pairs.limit(5).toHql().hql());
```

<a id="distinct-on-a-join"></a>
### 조인 결과의 `distinct()`

`SelectJoinQuery.distinct()`와 `SelectJoin3Query.distinct()`는 조인 때문에 반복되는 주 엔티티의 행을 한 번만 돌려줍니다(결정 D51). HQL은 `select distinct e0`이고, `count()`는 `count(distinct e0)`입니다.

- 정렬 키는 주 엔티티 자신의 컬럼이어야 합니다(J7, `LQL-3046`). 조인한 엔티티의 컬럼이나 연관 경로로 정렬하면 오류입니다.
- 페이징하면 주 엔티티의 id만 동순위 키로 덧붙입니다. 조인한 엔티티의 id는 붙이지 않습니다.
- `distinct()` 뒤에 `select(...)`를 쓰면 값을 `select distinct`로 조회합니다.
- 먼저 `exists` 서브쿼리([5. 서브쿼리](05-subquery.md))를 검토하십시오. 반복 없이 같은 행을 주고, 더 빠른 경우가 많습니다.

**(테스트됨)** [`JoinQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java), 영역 `join-distinct`:

```java
// an order with two matching items would come twice; distinct() returns it once
SelectJoinQuery<Order, Item> q = qb.from(Order.class)
        .join(Item.class, (o, i) -> i.getOrder().getId() == o.getId())
        .where((o, i) -> i.getQty() > 0)
        .distinct();
assertEquals("select distinct e0 from Order e0 join Item e1 on e1.order.id = e0.id where e1.qty > 0", q.toHql().hql());
// a sort key must be a column of the main entity (J7)
OrderedJoinQuery<Order, Item> sorted = q.orderBy((o, i, by) -> by.desc(o.getPrice()));
assertEquals("select distinct e0 from Order e0 join Item e1 on e1.order.id = e0.id where e1.qty > 0"
        + " order by e0.price desc", sorted.toHql().hql());
```

<a id="join-a-derived-table-or-cte"></a>
### 파생 테이블과 CTE의 조인

[6.9](#69-derived-table-subquery-in-from)의 파생 테이블과 [6.10](#610-cte-with)의 CTE를 엔티티처럼 조인할 수 있습니다(결정 D51).
형태는 `join(cte, (c, x) -> ...)`, `leftJoin(cte, ...)`, `join(qb.from(...)...select(new Row(...)), ...)`, `leftJoin(...)`이고, 두 번째나 세 번째 루트로 쓸 수 있습니다.

- HQL은 `with t as (...) ... join t e1 on ...` 또는 `join (select ...) e1 on ...`입니다.
- 행 타입은 레코드입니다(J5). 접근자(`x.total()`)가 컬럼을 읽습니다.
- 같은 `Cte` 객체를 한 문장에서 두 번 읽으면 정의를 한 번만 쓰고, 두 조인이 같은 이름을 읽습니다: `join t e1 on ... left join t e2 on ...`. 서로 다른 `Cte` 객체는 `with t as (...), t2 as (...)`처럼 이름을 따로 받습니다(세 DB에서 실측).
- 파생 행에는 id가 없으므로 `x == null`은 오류(`LQL-3067`)입니다. `x.customerId() == null`처럼 컬럼을 비교하십시오.
- Hibernate 6.6은 엔티티의 `select distinct`, 파생 테이블과 CTE의 inner/left join, 서브쿼리 안의 엔티티 조인을 받아들입니다(세 DB에서 실측).

**(테스트됨)** [`JoinQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java), 영역 `join-cte`:

```java
// the total of each customer, joined to the customer row
Cte<Total> t = qb.with(qb.from(Order.class)
        .where(o -> o.getCustomer() != null)
        .groupBy(o -> o.getCustomer().getId())
        .select(o -> new Total(o.getCustomer().getId(), Agg.sum(o.getPrice()))));
ValueQuery<NameTotal> rows = qb.from(Customer.class)
        .join(t, (c, x) -> x.customerId().equals(c.getId()))
        .where((c, x) -> x.total() > 100)
        .select((c, x) -> new NameTotal(c.getName(), x.total()));
assertEquals("with t as (select d0.customer.id as customerId, sum(d0.price) as total from Order d0"
        + " where d0.customer is not null group by d0.customer.id)"
        + " select e0.name, e1.total from Customer e0 join t e1 on e1.customerId = e0.id where e1.total > 100.0",
        rows.toHql().hql());
```

**(테스트됨)** [`JoinQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java), 영역 `join-derived`:

```java
// a derived table without a name; a customer without orders has no row in it (left join)
SelectJoinQuery<Customer, Total> small = qb.from(Customer.class)
        .leftJoin(totals(), (c, x) -> x.customerId().equals(c.getId()))
        .where((c, x) -> x.customerId() == null || x.total() < 210);
assertEquals("select e0 from Customer e0 left join (select d0.customer.id as customerId, sum(d0.price) as total"
        + " from Order d0 where d0.customer is not null group by d0.customer.id) e1 on e1.customerId = e0.id"
        + " where e1.customerId is null or e1.total < 210.0", small.toHql().hql());
```

**(테스트됨)** [`JoinQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java), 영역 `cte-reuse`:

```java
// the same CTE read twice is written once
ValueQuery<NameTotal> reused = qb.from(Customer.class)
        .join(t, (c, x) -> x.customerId().equals(c.getId()))
        .leftJoin(t, (c, x, y) -> y.customerId().equals(c.getId()) && y.total() > 210)
        .select((c, x, y) -> new NameTotal(c.getName(), y.total()));
assertEquals("with t as (select d0.customer.id as customerId, sum(d0.price) as total from Order d0"
        + " where d0.customer is not null group by d0.customer.id)"
        + " select e0.name, e2.total from Customer e0 join t e1 on e1.customerId = e0.id"
        + " left join t e2 on e2.customerId = e0.id and e2.total > 210.0", reused.toHql().hql());
```

<a id="what-a-join-query-has"></a>
### 조인 쿼리의 메서드

| 호출 뒤 | 메서드 |
|---|---|
| `join`, `leftJoin`, `crossJoin` | `where`, `join` / `leftJoin` / `crossJoin`(엔티티 3개까지, 파생 테이블과 CTE 포함), `distinct`, `orderBy`, `select`, `limit`, `toList`, `toSet`, `first`, `count`, `exists`, `toHql` |
| `orderBy((o, c, by) -> ...)` | `select`, `limit`, `offset`, `toList`, `first`, 그 밖의 정렬된 쿼리 메서드([4. 정렬과 페이징](04-order-paging.md)) |

서브쿼리 안의 조인은 [5. 서브쿼리](05-subquery.md#joins-inside-a-subquery)를 참고하십시오(D51).
v0.1에 아직 없는 것: insert의 조인, 엔티티 4개 이상, `SortSpec`의 조인 형태.
update와 delete의 조인은 추가하지 않기로 했습니다(D51). 연관 경로(U9)나 `sub`(D49)를 쓰십시오.

---

<a id="69-derived-table-subquery-in-from"></a>
## 6.9 파생 테이블(FROM 안의 서브쿼리)

`select`로 값 쿼리(레코드, [6.3](#63-select-a-record-or-dto) 참고)를 만든 뒤 `qb.from(...)`에 전달하십시오.
그 행이 바깥 쿼리의 테이블이 됩니다(결정 D46).
결과는 `DerivedQuery<R>`이며, `R`은 행 타입입니다.

**(테스트됨)** [`DerivedQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DerivedQueryTest.java), 영역 `derived`:

```java
// the rows of a group query are the table of the outer query; r.total() reads the column "total"
DerivedQuery<Stat> q = qb.from(qb.from(Order.class)
                .where(o -> o.getCustomer() != null)
                .groupBy(o -> o.getCustomer().getId())
                .select(o -> new Stat(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice()))))
        .where(r -> r.total() > 100)
        .orderBy((r, by) -> by.desc(r.total()));
assertEquals("select e0.customerId, e0.cnt, e0.total from (select d0.customer.id as customerId, count(*) as cnt,"
        + " sum(d0.price) as total from Order d0 where d0.customer is not null group by d0.customer.id) e0"
        + " where e0.total > 100.0 order by e0.total desc nulls last", q.toHql().hql());
```

이 테스트에서 `Stat`은 `record Stat(Integer customerId, Long cnt, Double total)`입니다.
테스트는 H2, MariaDB, Oracle에서 쿼리를 실행하고, 결과 행과 `count()`를 Java로 같은 계산을 한 결과와 비교합니다.

- 안쪽 쿼리의 별칭은 `d0`(조인은 `dj1`, ...), 바깥 쿼리의 별칭은 `e0`입니다.
- 레코드 컴포넌트마다 컬럼 이름이 정해집니다: `as customerId`, `as cnt`, `as total`(규칙 J5).
- 바깥 람다에서는 레코드 접근자로 컬럼을 읽습니다. `r.total()`은 `e0.total`입니다.
- 바깥 쿼리에는 `where`, `orderBy`, `limit`, `offset`, `select`, `toList`, `first`, `count`, `toHql`이 있습니다.

행의 값 하나를 select할 수도 있습니다. 안쪽과 바깥의 캡처 값은 순서대로 바인딩합니다.

**(테스트됨)** [`DerivedQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DerivedQueryTest.java), 영역 `select`:

```java
// a value of the row; captured values inside and outside are bound in order
ValueQuery<Integer> ids = qb.from(qb.from(Order.class)
                .where(o -> o.getCustomer() != null && o.getPrice() > min)
                .groupBy(o -> o.getCustomer().getId())
                .select(o -> new Stat(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice()))))
        .where(r -> r.total() > min * 2)
        .select(r -> r.customerId());
```

안쪽 쿼리의 `limit`과 `offset`은 안쪽 쿼리에 남습니다(HQL `limit`, `offset`). 그래서 상위 행을 다시 정렬할 수 있습니다.

**(테스트됨)** [`DerivedQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DerivedQueryTest.java), 영역 `inner-limit`:

```java
// the three most expensive orders, then the cheapest of them
Optional<Priced> cheapestOfTop = qb.from(qb.from(Order.class)
                .orderBy((o, by) -> by.desc(o.getPrice()))
                .limit(3)
                .select(o -> new Priced(o.getId(), o.getPrice())))
        .orderBy((r, by) -> by.asc(r.price()))
        .first();
```

`Priced`는 `record Priced(Long id, Double price)`입니다.

**SQL 블록 읽는 법.** [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java)는 같은 형태의 더 단순한 HQL을 Hibernate에 직접 넘기고, H2 2.3, MariaDB 10.11, Oracle 23.26(Hibernate 6.6.5)에서 나온 SQL을 기록합니다. Hibernate의 SQL이 바뀌면 테스트가 실패합니다. 이 블록에서 데이터베이스마다 파생 테이블을 쓰는 방식을 확인할 수 있습니다.

**데이터베이스별 SQL(이 형태의 HQL에 대한 Hibernate SQL)** — `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)의 예제 `06-derived`:

```sql
-- HQL: select r.customerId, r.cnt, r.total from (select o.customer.id as customerId, count(*) as cnt, sum(o.price) as total from Order o group by o.customer.id) r where r.total > 100

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

- 행 타입은 **Java 레코드**여야 합니다(Kotlin data class도 가능, 규칙 J5).
  lambdaQL은 레코드 컴포넌트에서 순서대로 컬럼 이름을 가져옵니다.
- 일반 DTO 클래스나 값 하나는 여기서 사용할 수 없습니다. lambdaQL이 컬럼 이름을 정할 수 없기 때문입니다. 이 경우 `TranslationException`(`LQL-3064`)이 발생합니다.

**(테스트됨)** [`DerivedQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DerivedQueryTest.java), 영역 `rules`:

```java
// J5: the row type must be a record or Kotlin data class (its components name the columns)
assertEquals("LQL-3064", assertThrows(LambdaQLException.class,
        () -> qb.from(qb.from(Order.class).select(o -> o.getPrice()))).code());
```

엔티티 쿼리에 파생 테이블을 조인하는 형태는 [파생 테이블과 CTE의 조인](#join-a-derived-table-or-cte)을 참고하십시오(D51).

v0.1에 아직 없는 것: 파생 테이블끼리의 조인, update, delete, insert 안의 파생 테이블.

엔티티의 `@SQLRestriction`과 `@SoftDelete` 조건은 엔티티 조인의 `on`과 파생 테이블 안에도 Hibernate가 붙입니다(MariaDB 10.11, Oracle 23.26에서 실측). lambdaQL은 이 조건을 따로 처리하지 않습니다.

---

<a id="610-cte-with"></a>
## 6.10 CTE(`with`)

`qb.with(valueQuery)`로 `Cte<R>` 타입의 핸들을 만드십시오. 그런 다음 `qb.from(handle)`로 읽습니다.
안쪽 쿼리는 파생 테이블(6.9)과 같습니다.

**(테스트됨)** [`DerivedQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DerivedQueryTest.java), 영역 `cte`:

```java
Cte<Stat> t = qb.with(perCustomer());
DerivedQuery<Stat> q = qb.from(t).where(r -> r.cnt() >= 2);
assertEquals("with t as (select d0.customer.id as customerId, count(*) as cnt, sum(d0.price) as total"
        + " from Order d0 where d0.customer is not null group by d0.customer.id)"
        + " select e0.customerId, e0.cnt, e0.total from t e0 where e0.cnt >= 2L", q.toHql().hql());
```

`perCustomer()`는 테스트의 헬퍼 메서드입니다. [6.9](#69-derived-table-subquery-in-from)의 안쪽 쿼리와 같은 값 쿼리를 반환합니다.

```java
private ValueQuery<Stat> perCustomer() {
    return qb.from(Order.class)
            .where(o -> o.getCustomer() != null)
            .groupBy(o -> o.getCustomer().getId())
            .select(o -> new Stat(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice())));
}
```

**데이터베이스별 SQL(이 형태의 HQL에 대한 Hibernate SQL)** — `PlannedSqlTest`가 기록했습니다. `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)의 예제 `06-cte`:

```sql
-- HQL: with t as (select o.customer.id as customerId, count(*) as cnt, sum(o.price) as total from Order o group by o.customer.id) select x.customerId, x.cnt, x.total from t x where x.total > 100

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

- CTE의 이름은 `t`입니다. 바깥 쿼리는 항상 별칭 `e0`을 붙입니다(`from t e0`). HQL에서 별칭이 필요합니다(규칙 J6).
- CTE의 컬럼 이름은 select 별칭(`as customerId`)으로 정합니다. HQL은 CTE 이름 뒤의 컬럼 목록을 받지 않으며, 컬럼 목록은 Hibernate가 SQL에 추가합니다(규칙 J6).
- MariaDB와 Oracle은 실제 `with`를 실행합니다. H2에서는 Hibernate가 이를 파생 테이블로 펼칩니다. 결과는 같습니다(세 데이터베이스에서 테스트함).
- 행 타입 규칙은 파생 테이블과 같습니다: 레코드(J5).
- 엔티티 쿼리에 CTE를 조인하는 형태는 [파생 테이블과 CTE의 조인](#join-a-derived-table-or-cte)을 참고하십시오(D51).
- 같은 CTE를 한 쿼리에서 두 번 읽을 수 있습니다. 정의는 한 번만 씁니다([파생 테이블과 CTE의 조인](#join-a-derived-table-or-cte) 참고).
- v0.1에 아직 없는 것: update, delete, insert 안의 CTE.

---

<a id="611-rules-j1-to-j8"></a>
## 6.11 규칙 J1~J8

| # | 규칙 | 상태 |
|---|---|---|
| J1 | `select` 람다는 **값 식 하나**나 **생성자 호출 하나**를 반환합니다. 생성자의 각 인자는 값 식입니다([3. 조건(`where`)](03-where.md)의 규칙). `select`에만 쓰인 연관 경로는 `left join` 별칭이 되므로 행을 잃지 않습니다. 필수 연관(`optional = false`)은 inner join입니다(A4). 같은 경로를 `where`에서 쓰면 `select`는 `where`의 조인을 사용합니다(R24). | 구현됨(테스트함) |
| J2 | 생성자의 경우 lambdaQL은 컬럼만 조회하고 생성자는 Java에서 호출합니다. `select new`는 쓰지 않습니다. | 구현됨(테스트함) |
| J3 | 집계가 있으면 `select`와 `having`(그리고 `insert ... select`의 값)에서 집계가 아닌 값은 모두 `groupBy` 키여야 합니다. 그렇지 않으면 분석 시점에 오류가 발생합니다. | 구현됨(테스트함). 오류는 `TranslationException`(`LQL-3035`, [11](../11-errors-and-languages.md) 참고) |
| J4 | `sum`/`avg`/`min`/`max`는 행이 없거나 모든 값이 NULL이면 NULL을 줍니다. `count`는 0을 줍니다. NULL이 될 수 있는 값에는 박싱 타입을 사용하십시오. 기본형 생성자 인자에 NULL이 오면 해당 인자를 알려 주는 `IllegalStateException`이 발생합니다. | 구현됨(테스트함) |
| J5 | 파생 테이블이나 CTE의 행 타입: **Java 레코드**(Kotlin 코드는 data class도 사용 가능). 일반 DTO 클래스는 단순 `select`에서만 허용됩니다. 그렇지 않으면 `LQL-3064`. 조인한 파생 테이블과 CTE의 행에도 적용합니다(D51). 파생 행은 `null`과 비교할 수 없습니다(`LQL-3067`). | 구현됨(테스트함) |
| J6 | `qb.with(...)`로 만든 CTE는 select 별칭으로 컬럼 이름을 정합니다: `with t as (select d0.customer.id as customerId, ...) select e0.customerId ... from t e0`. HQL은 이름 뒤의 컬럼 목록을 받지 않습니다. Hibernate는 `with t (col list) as (...)`를 `SyntaxException`으로 거부합니다. 컬럼 목록은 Hibernate가 SQL에 직접 씁니다(MariaDB와 Oracle에서 `with t (customerId,cnt,total) as (...)`). H2는 CTE를 파생 테이블로 바꿉니다. 바깥 쿼리는 항상 별칭을 사용합니다(`from t e0`). | 구현됨(테스트함) |
| J7 | `distinct()`는 `select distinct`를 렌더링합니다. 정렬과 함께 쓰면 정렬 키가 select 목록에 있어야 합니다(DB 규칙). lambdaQL은 분석 시점에 이를 검사합니다. 조인 결과의 `distinct()`에서는 정렬 키가 주 엔티티 자신의 컬럼이어야 합니다(D51). | 구현됨(테스트함) |
| J8 | 조회는 정렬([4. 정렬과 페이징](04-order-paging.md)), 페이징, 서브쿼리([5. 서브쿼리](05-subquery.md))와 함께 동작합니다. 집계 기준 정렬(`by.desc(s.total())`)은 v0.2 후보입니다. | 구현됨. 집계 기준 정렬은 제외 |

**(테스트됨)** [`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java), 영역 `rules`:

```java
// J3: a value without an aggregate must be a group key
LambdaQLException j3 = assertThrows(LambdaQLException.class, () -> qb.from(Order.class)
        .groupBy(o -> o.getCustomer().getId())
        .select(o -> new KeyCount(o.getCustomer().getId(), o.getStatus(), Agg.count())));
assertEquals("LQL-3035", j3.code());
// aggregates belong in having, not in where
LambdaQLException where = assertThrows(LambdaQLException.class, () -> qb.from(Order.class).where(o -> Agg.count() > 1));
assertEquals("LQL-3005", where.code());
// J7: with distinct, the sort key must be selected
LambdaQLException j7 = assertThrows(LambdaQLException.class, () -> qb.from(Order.class)
        .orderBy((o, by) -> by.asc(o.getId())).select(o -> o.getPrice()).distinct().toHql());
assertEquals("LQL-3046", j7.code());
// J4: the group without orders gives NULL; a primitive argument cannot take it
assertThrows(IllegalStateException.class, () -> qb.from(Order.class)
        .where(o -> o.getStatus() == 9)
        .select(o -> new Bad(null, Agg.sum(o.getPrice()))).toList());
```

`Bad`는 `record Bad(Integer customerId, double total)`입니다. status가 9인 주문이 없으므로 `sum`은 NULL이고, 기본형 `double`은 이 값을 받을 수 없습니다.

---

<a id="612-not-in-v01"></a>
## 6.12 v0.1에 없는 것

| 항목 | 상태 |
|---|---|
| 집계 결과나 select 별칭으로 정렬(예: `orderBy((s, by) -> by.desc(s.total()))`), 그룹 쿼리의 `orderBy` | v0.2 후보 |
| 사용자 집계 함수와 윈도 함수 | v0.2 후보 |
| insert의 조인, 엔티티 4개 이상, 서브쿼리 안의 엔티티 3개 이상 | 이후 |
| update, delete의 조인 | 추가하지 않음(D51). 연관 경로(U9)나 `sub`(D49) 사용 |
| DML 안의 파생 테이블과 CTE | 이후 |

이 장의 오류는 코드가 붙은 `TranslationException`입니다. 잘못된 그룹 키(J3)는 `LQL-3035`, `where` 안의 `Agg`는 `LQL-3005`, `distinct`에서 select하지 않은 정렬 키(J7)는 `LQL-3046`, 파생 테이블이나 CTE의 행 타입(J5)은 `LQL-3064`입니다. [11. 오류와 언어](../11-errors-and-languages.md)를 참고하십시오.

---

[Java 트랙](README.md) | 이전: [5. 서브쿼리](05-subquery.md) | 다음: [7. 수정, 삽입, 삭제](07-dml.md) | [이 페이지의 Kotlin 버전](../kotlin/06-select-group.md)
