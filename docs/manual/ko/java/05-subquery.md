<a id="5-subqueries-java"></a>
# 5. 서브쿼리 (Java)

[Java 트랙](README.md) | [매뉴얼 홈](../README.md) | 이전: [4. 정렬과 페이징](04-order-paging.md) | 다음: [6. 조회, 집계, 그룹화](06-select-group.md) | [이 페이지의 Kotlin 버전](../kotlin/05-subquery.md)

공통 페이지: [1. 개요](../01-overview.md) · [11. 오류와 언어](../11-errors-and-languages.md)

---

<a id="51-the-idea-a-second-lambda-argument-sub"></a>
## 5.1 아이디어: 두 번째 람다 인자 `sub`

`where` 람다는 **두 번째 인자**로 서브쿼리 팩토리를 받을 수 있습니다. 이 매뉴얼에서는 이를 `sub`라고 부릅니다(어떤 이름이든 사용할 수 있습니다).
그 안에서 일반 쿼리와 **같은 람다 문법**으로 서브쿼리를 작성합니다.

서브쿼리가 없으면 `.where(o -> ...)`처럼 인자가 하나이고, 서브쿼리가 있으면 `.where((o, sub) -> ...)`처럼 인자가 둘입니다.
lambdaQL은 인자의 개수로 두 형태를 구분합니다.
서브쿼리는 `&&`, `||`, `!`와 자유롭게 섞을 수 있습니다. static import가 필요 없습니다.

`sub`로 할 수 있는 것:

| 서브쿼리 | 용도 | HQL |
|---|---|---|
| `sub.from(X.class).where(...).exists()` | 조건 | `exists (select 1 from X x where ...)` |
| `!sub.from(X.class).where(...).exists()` | 조건 | `not exists (...)` |
| `sub.from(X.class).where(...).select(x -> v).contains(y)` | 조건 | `y in (select x.v from X x where ...)` |
| `!... .contains(y)` | 조건 | `y not in (select x.v from X x where ...)` |
| `sub.from(X.class).avg(x -> v)`(`sum`, `min`, `max`, `count()`도) | 값 | `(select avg(x.v) from X x)` |
| `sub.from(X.class)...orderBy(...).limit(1).select(x -> v).first()` | 값 | top-1 서브쿼리 |
| `sub.from(A.class).join(B.class, (a, b) -> ...).where((a, b) -> ...).exists()` | 조건 | `exists (select 1 from A a join B b on ... where ...)`([서브쿼리 안의 조인](#joins-inside-a-subquery)) |

> [!NOTE]
> 반환 값(`boolean`, `Double`, ...)은 코드가 컴파일되도록 하는 자리 표시자일 뿐입니다.
> 람다는 절대 실행되지 않습니다. 이 메서드들은 lambdaQL 람다 안에서만 의미가 있습니다.

안쪽 람다(`sub.from(...).where(...)` 안의 `i -> ...`)는 따로 `Serializable`일 필요가 없습니다. lambdaQL은 바깥 람다의 바이트코드에서 이를 찾습니다.

HQL에서 서브쿼리 루트의 별칭은 `s1`, `s2`, ...입니다(바깥 루트는 `e0`). 서브쿼리 안의 조인은 `s1j1` 같은 별칭을 받습니다.

예제는 [`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java)에서 그대로 가져온 것입니다.
이 테스트는 HQL을 확인하고, 쿼리를 실행하고, 같은 필터를 Java로 적용한 결과와 행을 비교합니다. H2, MariaDB, Oracle에서 통과합니다.
테스트는 [3.3](03-where.md#33-basic-form)의 테스트 엔티티를 사용합니다. `price`는 `double`, `status`는 `short`, `Customer.id`는 `int`이고, `Customer.grade`는 `Integer`입니다. 테스트 데이터에는 등급 5, 2, NULL과 고객이 없는 주문 하나가 있습니다.

## 5.2 EXISTS

`qty > 10`인 품목이 하나 이상 있는 주문:

[`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java), 영역 `exists`:

```java
// correlated: the inner lambda uses the outer order o
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId() && i.getQty() > 10).exists());
assertEquals("select e0 from Order e0 where exists (select 1 from Item s1 where s1.order.id = e0.id and s1.qty > 10)",
        q.toHql().hql());
```

**SQL 블록 읽는 법.** `…`는 줄여 쓴 select 목록(엔티티의 모든 컬럼)이고, `?`는 바인드 파라미터입니다. 각 줄은 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)에서 나옵니다. 이 테스트는 각 쿼리를 Hibernate 7.4로 H2 2.3, MariaDB 10.11, Oracle 23.26에서 실행하며, Hibernate의 SQL이 바뀌면 실패합니다.

**데이터베이스별 SQL** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `sub-exists`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id and i1_0.qty>10)
```

이는 **상관** 서브쿼리입니다. 안쪽 람다가 바깥의 `o`를 사용합니다([5.6](#56-correlated-subqueries) 참고).

## 5.3 NOT EXISTS

`!`를 사용하십시오. 다른 조건과 결합할 수 있습니다.
status가 1인 주문, 또는 품목이 없는 주문:

[`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java), 영역 `not-exists`:

```java
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> o.getStatus() == 1 || !sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).exists());
assertEquals("select e0 from Order e0 where e0.status = 1 or not exists (select 1 from Item s1 where s1.order.id = e0.id)",
        q.toHql().hql());
```

**데이터베이스별 SQL** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `sub-not-exists`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 or not exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id)
```

## 5.4 IN

`grade > 3`인 고객의 주문:

[`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java), 영역 `in`:

```java
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> sub.from(Customer.class).where(c -> c.getGrade() != null && c.getGrade() > 3)
                .select(c -> c.getId()).contains(o.getCustomer().getId()));
assertEquals("select e0 from Order e0 where e0.customer.id in"
        + " (select s1.id from Customer s1 where s1.grade is not null and s1.grade > 3)", q.toHql().hql());
```

**데이터베이스별 SQL** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `sub-in`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id in (select c2_0.id from customers c2_0 where c2_0.grade is not null and c2_0.grade>3)
```

`o.getCustomer().getId()`는 고객의 `@Id`로 끝나므로 외래 키 컬럼 `e0.customer.id`입니다. 조인이 필요 없습니다.
직접 작성한 null 검사(`c.getGrade() != null`)는 HQL에 `s1.grade is not null`로 남습니다.

<a id="55-not-in-is-written-as-not-exists"></a>
## 5.5 NOT IN과 NULL

`!values.contains(x)`는 SQL 의미 그대로 `NOT IN`으로 렌더링됩니다(결정 D48).

[`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java), 영역 `not-in`:

```java
// the SQL meaning (D48): NOT IN. The grades are 5, 2 and NULL, so no row matches: x NOT IN (5, 2, NULL)
// is never true. To skip the NULL, say so in the subquery: where(c -> c.getGrade() != null)
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> !sub.from(Customer.class).select(c -> c.getGrade()).contains((int) o.getStatus()));
assertEquals("select e0 from Order e0 where e0.status not in (select s1.grade from Customer s1)", q.toHql().hql());
SelectQuery<Order> withoutNull = qb.from(Order.class)
        .where((o, sub) -> !sub.from(Customer.class).where(c -> c.getGrade() != null).select(c -> c.getGrade())
                .contains((int) o.getStatus()));
```

**데이터베이스별 SQL** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `sub-not-in`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status not in (select c2_0.grade from customers c2_0)
```

이유:

- `x NOT IN (subquery)`는 서브쿼리가 NULL을 하나라도 반환하면 **행을 하나도 반환하지 않습니다**. lambdaQL은 이 SQL 의미를 유지하고(결정 D48) `LQL-5009`를 `DEBUG` 수준으로 기록합니다.
- NULL을 건너뛰려면 테스트의 두 번째 쿼리처럼 서브쿼리에 `where(c -> c.getGrade() != null)`을 쓰십시오. 그러면 NULL 등급을 뺀 Java의 `!grades.contains(status)`와 같은 결과가 나옵니다.
- `!sub...exists()`는 여전히 `NOT EXISTS`로 작성됩니다.

<a id="56-correlated-subqueries"></a>
## 5.6 상관 서브쿼리

안쪽 람다가 바깥 엔티티를 사용하면 서브쿼리는 **상관** 서브쿼리가 됩니다. 특별한 문법이 필요 없습니다. 바깥 변수를 그냥 사용하십시오(아래 예제의 `o`).

lambdaQL이 안쪽 람다의 캡처 값을 읽는 방법:

| 안쪽 람다에서 캡처한 것 | 변환 결과 |
|---|---|
| 바깥 엔티티(`o`) | 바깥 별칭에 대한 참조(`e0.id`) |
| 그 밖의 값(지역 변수, 필드, ...) | [3.11](03-where.md#311-captured-values)과 같은 바인드 파라미터 |

[`ManualJavaSubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaSubqueryTest.java), 영역 `correlated`:

```java
int minQty = 10;
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> sub.from(Item.class)
                .where(i -> i.getOrder().getId() == o.getId() && i.getQty() > minQty)   // o comes from outside
                .exists());
assertEquals("select e0 from Order e0"
        + " where exists (select 1 from Item s1 where s1.order.id = e0.id and s1.qty > :p1)", q.toHql().hql());
assertEquals(Map.of("p1", 10), q.toHql().parameters());
```

**데이터베이스별 SQL (이 HQL에 대한 Hibernate의 SQL)** — [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java)가 같은 HQL을 Hibernate에 직접 넘겨 기록했습니다. `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)의 예제 `05-correlated-param`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id and i1_0.qty>?)
```

**상관 조건의 `==`.** `i.getOrder().getId() == o.getId()`는 두 `Long` 객체를 비교합니다. 엔티티 값 두 개는 SQL 의미로 비교하므로(결정 D48, [3.6](03-where.md#36-null-rules) 참고) lambdaQL은 단순한 `=`를 씁니다(`!=`는 `<>`).
데이터베이스는 인덱스를 사용할 수 있습니다.

<a id="57-scalar-subqueries"></a>
## 5.7 스칼라 서브쿼리

**값 하나**를 주는 서브쿼리는 비교에 사용할 수 있습니다.

평균 가격보다 비싼 주문:

[`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java), 영역 `scalar`:

```java
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> o.getPrice() > sub.from(Order.class).avg(x -> x.getPrice()));
assertEquals("select e0 from Order e0 where e0.price > (select avg(s1.price) from Order s1)", q.toHql().hql());
```

**데이터베이스별 SQL** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `sub-scalar`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.price>(select avg(o2_0.price) from orders o2_0)
```

스칼라 서브쿼리는 **항상 한 행을 주어야** 합니다. 허용되는 형태는 두 가지입니다(규칙 Q3).

| 형태 | 예 |
|---|---|
| 집계: `count()`, `sum`, `avg`, `min`, `max` | `sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).count()` |
| top-1: `orderBy(...).limit(1)...first()` | 아래 참고 |

그 밖의 `first()`는 분석 시점에 오류(`TranslationException`, `LQL-3017`)가 됩니다.

품목이 두 개 이상인 주문(`count()`):

[`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java), 영역 `count`:

```java
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).count() >= 2);
assertEquals("select e0 from Order e0 where (select count(s1) from Item s1 where s1.order.id = e0.id) >= 2L", q.toHql().hql());
```

**데이터베이스별 SQL** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `sub-count`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where (select count(i1_0.id) from items i1_0 where i1_0.order_id=o1_0.id)>=2
```

top-1: 고객별로 가장 비싼 주문.

[`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java), 영역 `top-one`:

```java
// the most expensive order of each customer: a correlated scalar subquery with order + limit 1 (rule Q3)
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> o.getId().equals(sub.from(Order.class)
                .where(x -> x.getCustomer().getId() == o.getCustomer().getId())
                .orderBy((x, by) -> by.desc(x.getPrice()))
                .limit(1)
                .select(x -> x.getId())
                .first()));
assertEquals("select e0 from Order e0 where e0.id = (select s1.id from Order s1 where s1.customer.id = e0.customer.id"
        + " order by s1.price desc, s1.id asc limit 1)", q.toHql().hql());
// first() without limit(1) is rejected
assertThrows(LambdaQLException.class, () -> qb.from(Order.class)
        .where((o, sub) -> o.getId().equals(sub.from(Order.class).select(x -> x.getId()).first())));
```

**데이터베이스별 SQL** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `sub-top-one`:

```sql
-- H2, Oracle
select …
from orders o1_0
where o1_0.id=(select o2_0.id from orders o2_0 where o2_0.customer_id=o1_0.customer_id order by o2_0.price desc,o2_0.id fetch first 1 rows only)

-- MariaDB
select …
from orders o1_0
where o1_0.id=(select o2_0.id from orders o2_0 where o2_0.customer_id=o1_0.customer_id order by o2_0.price desc,o2_0.id limit 1)
```

lambdaQL은 마지막 정렬 키로 `id`(`s1.id asc`)를 더하므로 맨 위 행이 항상 같습니다(규칙 S9).
상관 조건은 고객 엔티티가 아니라 외래 키(`getCustomer().getId()`)를 비교합니다. [5.10](#510-paths-and-null-inside-subqueries)을 참고하십시오.

집계의 NULL은 SQL을 따릅니다. `sum`, `avg`, `min`, `max`는 행이 없으면 NULL을 줍니다. `count`는 0을 줍니다.

<a id="58-nested-subqueries"></a>
## 5.8 중첩 서브쿼리

서브쿼리의 `where`도 `sub` 인자를 받을 수 있습니다. 모든 단계에서 같은 규칙이 다시 적용됩니다.

큰 품목이 있는 주문을 가진 고객:

[`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java), 영역 `nested`:

```java
// customers that have an order with an item of quantity > 10: a subquery inside a subquery
SelectQuery<Customer> q = qb.from(Customer.class)
        .where((c, sub) -> sub.from(Order.class).where((o, sub2) -> o.getCustomer().getId() == c.getId()
                && sub2.from(Item.class).where(i -> i.getOrder().getId() == o.getId() && i.getQty() > 10).exists()).exists());
assertEquals("select e0 from Customer e0 where exists (select 1 from Order s1 where s1.customer.id = e0.id"
        + " and exists (select 1 from Item s2 where s2.order.id = s1.id and s2.qty > 10))", q.toHql().hql());
```

**데이터베이스별 SQL** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `sub-nested`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from customers c1_0
where exists(select 1 from orders o1_0 where o1_0.customer_id=c1_0.id and exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id and i1_0.qty>10))
```

Java는 중첩된 람다에서 같은 이름을 두 번 쓰는 것을 허용하지 않습니다. 단계마다 새 이름(`sub2`, `o`, `i`)을 사용하십시오.

<a id="59-sorting-inside-a-subquery"></a>
## 5.9 서브쿼리 안의 정렬

| 경우 | lambdaQL의 동작 |
|---|---|
| `limit`이 있는 `orderBy`(top-1, top-N) | `id` 동순위 처리와 함께 유지 |
| `limit`이 없는 `orderBy` | **제거**하고 경고를 로그에 남김(결과에 영향이 없음. Oracle에서는 구문 오류 ORA-00907까지 발생) |
| `limit`이 있는 서브쿼리에 대한 `contains`(MariaDB는 `IN (... limit n)`을 지원하지 않음, 오류 1235) | 모든 데이터베이스에서 파생 테이블로 감쌈: `in (select t1.v from (select s1.id as v ... limit n) t1)` |

[4. 정렬과 페이징](04-order-paging.md)의 NULL 정책과 자동 `id` 동순위 처리도 서브쿼리 안에 적용됩니다.

<a id="510-paths-and-null-inside-subqueries"></a>
## 5.10 서브쿼리 안의 경로와 NULL

서브쿼리 안에서 조건, 경로, NULL은 **바깥 쿼리와 똑같이** 동작합니다([3. 조건(`where`)](03-where.md)).

- 객체에 대한 `==`는 SQL 의미의 값 비교입니다(D48). 캡처 값 `null`은 어떤 행에도 일치하지 않습니다(`LQL-4045`).
- 서브쿼리 자신의 엔티티에 대한 연관 경로는 바깥 `where`와 같은 조인 규칙을 따릅니다. 항상 inner `join`입니다(결정 D48).
- `@Id`로 끝나는 경로는 외래 키 컬럼입니다(조인 없음).
- **바깥 엔티티**는 자신의 컬럼과 외래 키 id(`o.getId()`, `o.getCustomer().getId()`)만 사용할 수 있습니다. 조인이 필요한 바깥 엔티티의 경로(`o.getCustomer().getName()`)는 분석 시점에 오류가 됩니다. 대신 외래 키 id를 비교하십시오.
- 컬렉션 경로(`c.getOrders()`)는 거부됩니다(`LQL-3010`). 컬렉션에 대해 물으려면 서브쿼리를 사용하십시오.
- 엔티티의 `@SQLRestriction`과 `@SoftDelete` 조건은 서브쿼리(`exists`, `in`) 안에도 Hibernate가 붙입니다(MariaDB 10.11, Oracle 23.26에서 실측). lambdaQL은 이 조건을 따로 처리하지 않습니다.

다음 예제는 "주문이 있는 고객"을 `Customer.orders` 컬렉션 대신 서브쿼리로 찾습니다.

[`ManualJavaSubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaSubqueryTest.java), 영역 `collection-path`:

```java
// a collection path is rejected
LambdaQLException e = assertThrows(LambdaQLException.class,
        () -> qb.from(Customer.class).where(c -> c.getOrders().size() > 0));
assertEquals("LQL-3010", e.code());
// ask with a subquery instead
SelectQuery<Customer> withOrders = qb.from(Customer.class)
        .where((c, sub) -> sub.from(Order.class).where(o -> o.getCustomer().getId() == c.getId()).exists());
assertEquals("select e0 from Customer e0"
        + " where exists (select 1 from Order s1 where s1.customer.id = e0.id)", withOrders.toHql().hql());
```

<a id="joins-inside-a-subquery"></a>
### 서브쿼리 안의 조인

서브쿼리 안에서도 엔티티 두 개를 조인할 수 있습니다(결정 D51). 형태는 `sub.from(A.class).join(B.class, (a, b) -> ...)`와 `leftJoin(...)`이고, 결과는 `From2<A, B>`입니다.
`From2`의 모든 람다는 두 행을 받습니다: `where((a, b) -> ...)`, `exists()`, `count()`, `select((a, b) -> v)` 뒤의 `contains(x)`나 `first()`, `max`, `min`, `avg`, `sum((a, b) -> v)`.

- HQL은 `exists (select 1 from Order s1 join Item s2 on s2.order.id = s1.id where ...)`처럼 됩니다.
- ON 조건 안의 연관 경로는 `left join`이 됩니다. 경로 조인은 그 경로가 시작하는 루트 바로 뒤에 둡니다.
- 서브쿼리 안의 엔티티는 두 개까지입니다. 조인 형태에서는 `orderBy`와 `limit`을 쓸 수 없습니다.
- `exists`, 조인한 행의 `sum`, `left join`과 ON 안의 연관 경로를 쓴 `in`을 세 DB에서 실측했습니다.

[`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java), 영역 `sub-join`:

```java
// customers with an order that has an item of quantity > 10: the subquery joins Order and Item (D51)
SelectQuery<Customer> q = qb.from(Customer.class).where((c, sub) -> sub.from(Order.class)
        .join(Item.class, (o, i) -> i.getOrder().getId() == o.getId())
        .where((o, i) -> o.getCustomer().getId() == c.getId() && i.getQty() > 10)
        .exists());
assertEquals("select e0 from Customer e0 where exists (select 1 from Order s1 join Item s2 on s2.order.id = s1.id"
        + " where s1.customer.id = e0.id and s2.qty > 10)", q.toHql().hql());
// an aggregate over the joined rows
SelectQuery<Customer> total = qb.from(Customer.class).where((c, sub) -> sub.from(Order.class)
        .join(Item.class, (o, i) -> i.getOrder().getId() == o.getId())
        .where((o, i) -> o.getCustomer().getId() == c.getId())
        .sum((o, i) -> i.getQty()) > 30);
assertEquals("select e0 from Customer e0 where (select sum(s2.qty) from Order s1 join Item s2 on s2.order.id = s1.id"
        + " where s1.customer.id = e0.id) > 30", total.toHql().hql());
```

<a id="511-subqueries-in-other-places"></a>
## 5.11 다른 위치의 서브쿼리

- **UPDATE / DELETE 안의 서브쿼리**: `where`에서 같은 `sub` 문법을 씁니다. 구현되어 테스트했습니다(영역 `dml-subquery`). [7.4 삭제(delete)](07-4-delete.md)와 [7.2 수정(update)](07-2-update.md)을 참고하십시오.
- **`set` 값 안의 서브쿼리**(`o.setTotal(sub.from(...).sum(...))`): 대입 람다의 두 번째 인자 `sub`로 씁니다(결정 D49). [7.2 수정(update)](07-2-update.md#an-aggregate-from-a-subquery)을 참고하십시오.
- **`from` 안의 서브쿼리(파생 테이블)와 CTE**: 구현됨(마일스톤 M8-2). `sub`를 쓰지 않고 값 쿼리를 `qb.from(...)`이나 `qb.with(...)`에 전달합니다. [6.9 파생 테이블](06-select-group.md#69-derived-table-subquery-in-from)과 [6.10 CTE](06-select-group.md#610-cte-with)를 참고하십시오.

<a id="512-rules-summary-q1-to-q6"></a>
## 5.12 규칙 요약(Q1~Q6)

| # | 규칙 |
|---|---|
| Q1 | 서브쿼리 안의 조건, 경로, NULL 규칙은 바깥과 같습니다. 조건 안의 연관 경로도 같은 조인 규칙(기본은 inner `join`)을 따릅니다. 조인이 필요한 바깥 엔티티의 경로는 오류이고, 외래 키 id는 허용됩니다 |
| Q2 | **NOT IN은 SQL 의미를 유지합니다**(D48): `!values.contains(x)`는 `x not in (select c.sel from C c where <condition>)`이 됩니다. 서브쿼리에 NULL이 있으면 행이 반환되지 않습니다(`DEBUG` 수준 `LQL-5009`). NULL을 건너뛰려면 서브쿼리에 `c.sel != null`을 추가합니다 |
| Q3 | 스칼라 서브쿼리는 한 행을 주어야 합니다: 집계(`max`, `min`, `avg`, `sum`, `count`)나 `orderBy(...).limit(1)...first()`. 그 밖의 `first()`는 오류입니다 |
| Q4 | 서브쿼리 정렬: `limit`이 있으면 유지, 없으면 경고와 함께 제거. `IN (... limit)`은 파생 테이블로 감쌉니다 |
| Q5 | 상관 조건에서 `==`는 `=`로, `!=`는 `<>`로 씁니다(SQL 의미, D48). 어느 한쪽이 NULL이면 일치하지 않습니다. 인덱스 사용을 유지합니다 |
| Q6 | 서브쿼리의 `where`는 자신의 `sub` 인자를 받을 수 있습니다: `where((i, sub2) -> ...)`. 모든 단계에서 같은 규칙이 적용됩니다 |

---

[Java 트랙](README.md) | 이전: [4. 정렬과 페이징](04-order-paging.md) | 다음: [6. 조회, 집계, 그룹화](06-select-group.md) | [이 페이지의 Kotlin 버전](../kotlin/05-subquery.md)
