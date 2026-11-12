<a id="5-subqueries-kotlin"></a>
# 5. 서브쿼리 (Kotlin)

> [!NOTE]
> **상태**
> - **설계됨, 아직 구현되지 않음**(마일스톤 M8).
> - Kotlin 문법은 컴파일되며, 그 바이트코드는 프로토타입으로 확인했습니다(kotlinc 2.3.21). SQL 형태는 Hibernate 6.6.5와 함께 H2 2.3, MariaDB 10.11, Oracle 23ai에서 확인했습니다.
> - 이 장의 HQL은 **예정된** 출력입니다.

[Kotlin 트랙](README.md) | [매뉴얼 홈](../README.md) | 이전: [4. 정렬과 페이징](04-order-paging.md) | 다음: [6. 조회, 집계, 그룹화](06-select-group.md) | [이 페이지의 Java 버전](../java/05-subquery.md)

공통 페이지: [1. 개요](../01-overview.md) · [11. 오류와 언어](../11-errors-and-languages.md)

---

<a id="51-the-idea-a-second-lambda-argument-sub"></a>
## 5.1 기본 아이디어: 두 번째 람다 인자 `sub`

`where` 람다는 **두 번째 인자**로 서브쿼리 팩토리를 받을 수 있습니다. 이 매뉴얼에서는 이를 `sub`라고 부릅니다(어떤 이름이든 쓸 수 있음).
그 안에서 일반 쿼리와 **같은 람다 문법**으로 서브쿼리를 작성합니다.

```kotlin
.where { ... }                // 서브쿼리 없음: 인자 하나(it)
.where { o, sub -> ... }      // 서브쿼리 있음: 인자 둘
```

lambdaQL은 인자의 개수로 두 형태를 구분합니다. Kotlin은 알맞은 오버로드를 고릅니다.
서브쿼리는 `&&`, `||`, `!`와 자유롭게 섞어 쓸 수 있습니다. import는 필요 없습니다.

`sub`로 할 수 있는 일:

| 서브쿼리 | 용도 | HQL |
|---|---|---|
| `sub.from(X::class.java).where { ... }.exists()` | 조건 | `exists (select 1 from X x where ...)` |
| `!sub.from(X::class.java).where { ... }.exists()` | 조건 | `not exists (...)` |
| `y in sub.from(X::class.java).where { ... }.select { x -> v }` | 조건 | `y in (select x.v from X x where ...)` |
| `y !in sub.from(...)...select { ... }` | 조건 | `not exists (... and x.v = y)` |
| `sub.from(X::class.java).avg { x -> v }`(`sum`, `min`, `max`, `count()`도 마찬가지) | 값 | `(select avg(x.v) from X x)` |
| `sub.from(X::class.java)...orderBy { ... }.limit(1).select { x -> v }.first()` | 값 | top-1 서브쿼리 |

> [!NOTE]
> 반환 값(`Boolean`, `Double`, ...)은 코드가 컴파일되도록 하는 자리 표시자일 뿐입니다.
> 람다는 절대 실행되지 않습니다. 이 함수들은 lambdaQL 람다 안에서만 의미가 있습니다.

Kotlin에서 `y in values`는 `values.contains(y)`로 컴파일됩니다. 그래서 Kotlin `in` 연산자는 추가 코드 없이 서브쿼리 API와 함께 동작합니다.

안쪽 람다(`sub.from(...).where { }` 안의 `{ i -> ... }`)는 그 자체로 `Serializable`일 필요가 없습니다. lambdaQL은 바깥 람다의 바이트코드에서 안쪽 람다를 찾습니다.

## 5.2 EXISTS

`qty > 10`인 품목이 하나 이상 있는 주문:

```kotlin
qb.from(Order::class.java)
    .where { o, sub ->
        sub.from(Item::class.java)
            .where { i -> i.order?.id == o.id && i.qty > 10 }
            .exists()
    }
    .toList()
// HQL: select o from Order o
//      where exists (select 1 from Item i where i.order.id = o.id and i.qty > 10)
```

**SQL 블록 읽는 법.** `…`는 줄여 쓴 select 목록(엔티티의 모든 컬럼)이고, `?`는 바인드 파라미터입니다. [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java)는 이 페이지의 HQL을 Hibernate에 직접 넘기고, H2 2.3, MariaDB 10.11, Oracle 23.26(Hibernate 6.6.5)에서 나온 SQL을 기록합니다. lambdaQL은 아직 이 쿼리들을 만들지 않습니다. Hibernate의 SQL이 바뀌면 테스트가 실패합니다.

**데이터베이스별 SQL (예정)** — 예정된 HQL을 Hibernate가 번역한 것으로, `PlannedSqlTest`가 기록했습니다. lambdaQL은 아직 이 쿼리를 만들지 않습니다. `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)의 예제 `05-exists`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id and i1_0.qty>10)
```

이것은 **상관** 서브쿼리입니다. 안쪽 람다가 바깥의 `o`를 사용합니다([5.6](#56-correlated-subqueries) 참고).

## 5.3 NOT EXISTS

`!`를 사용하십시오. 다른 조건과 결합할 수 있습니다.

```kotlin
// status가 1인 주문, 또는 품목이 없는 주문
qb.from(Order::class.java)
    .where { o, sub ->
        o.status == 1 ||
            !sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists()
    }
    .toList()
// HQL: select o from Order o
//      where o.status = 1
//         or not exists (select 1 from Item i where i.order.id = o.id)
```

**데이터베이스별 SQL (예정)** — 예정된 HQL을 Hibernate가 번역한 것으로, `PlannedSqlTest`가 기록했습니다. lambdaQL은 아직 이 쿼리를 만들지 않습니다. `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)의 예제 `05-not-exists-or`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 or not exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id)
```

## 5.4 IN

`grade > 3`인 고객의 주문. `in`을 사용하십시오.

```kotlin
qb.from(Order::class.java)
    .where { o, sub ->
        o.customer?.id in sub.from(Customer::class.java)
            .where { c -> c.grade!! > 3 }
            .select { c -> c.id }
    }
    .toList()
// HQL: select o from Order o
//      where o.customer.id in (select c.id from Customer c where c.grade > 3)
```

**데이터베이스별 SQL (예정)** — 예정된 HQL을 Hibernate가 번역한 것으로, `PlannedSqlTest`가 기록했습니다. lambdaQL은 아직 이 쿼리를 만들지 않습니다. `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)의 예제 `05-in-subquery`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id in (select c2_0.id from customers c2_0 where c2_0.grade>3)
```

`o.customer?.id`는 고객의 `@Id`로 끝나므로 외래 키 컬럼 `o.customer.id`입니다. 조인이 필요 없습니다.

<a id="55-not-in-is-written-as-not-exists"></a>
## 5.5 NOT IN은 NOT EXISTS로 작성됨

`x !in values`는 `NOT IN`으로 출력되지 **않습니다**. lambdaQL은 `NOT EXISTS`를 씁니다.

```kotlin
qb.from(Order::class.java)
    .where { o, sub ->
        o.customer?.id !in sub.from(Customer::class.java)
            .where { c -> c.grade!! > 3 }
            .select { c -> c.id }
    }
    .toList()
// HQL: select o from Order o
//      where not exists (select 1 from Customer c
//                        where c.grade > 3 and c.id = o.customer.id)
```

**데이터베이스별 SQL (예정)** — 예정된 HQL을 Hibernate가 번역한 것으로, `PlannedSqlTest`가 기록했습니다. lambdaQL은 아직 이 쿼리를 만들지 않습니다. `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)의 예제 `05-not-in`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where not exists(select 1 from customers c2_0 where c2_0.grade>3 and c2_0.id=o1_0.customer_id)
```

이유:

- `x NOT IN (subquery)`는 서브쿼리가 NULL을 하나라도 반환하면 **행을 하나도 반환하지 않습니다**. 흔한 SQL 함정입니다.
- `NOT EXISTS`에는 이 문제가 없습니다. 데이터베이스도 이를 잘 실행합니다(안티 조인으로).
- 결과: 고객이 없는 주문은 위 쿼리에서 반환됩니다. 그 주문과 일치하는 고객이 없기 때문입니다.

<a id="56-correlated-subqueries"></a>
## 5.6 상관 서브쿼리

안쪽 람다가 바깥 엔티티를 사용하면 서브쿼리는 **상관** 서브쿼리가 됩니다. 특별한 문법은 필요 없습니다. 바깥 변수를 그냥 사용하십시오.

```kotlin
.where { o, sub ->
    sub.from(Item::class.java)
        .where { i -> i.order?.id == o.id }   // o는 바깥에서 옴
        .exists()
}
```

lambdaQL이 안쪽 람다의 캡처 값을 읽는 방법:

| 안쪽 람다에서 캡처한 것 | 변환 결과 |
|---|---|
| 바깥 엔티티(`o`) | 바깥 별칭에 대한 참조(`o.id`) |
| 그 밖의 값(지역 `val`, 속성, ...) | 바인드 파라미터. [3.11](03-where.md#311-captured-values)과 같음 |

```kotlin
val minQty = 10
.where { o, sub ->
    sub.from(Item::class.java)
        .where { i -> i.order?.id == o.id && i.qty > minQty }
        .exists()
}
// HQL: exists (select 1 from Item i where i.order.id = o.id and i.qty > :p1)
```

**데이터베이스별 SQL (예정)** — 예정된 HQL을 Hibernate가 번역한 것으로, `PlannedSqlTest`가 기록했습니다. lambdaQL은 아직 이 쿼리를 만들지 않습니다. `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)의 예제 `05-correlated-param`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id and i1_0.qty>?)
```

**상관 조건의 `==`.** `i.order?.id == o.id`는 두 `Long?` 값을 비교합니다. 일반 규칙에 따르면 이는 null-safe 비교입니다(`is not distinct from`, [3.6](03-where.md#36-null-rules) 참고).
하지만 `o.id`는 `@Id`이므로 절대 NULL이 될 수 없습니다. 이 경우 lambdaQL은 단순한 `=`를 씁니다(`!=`는 `(a <> b or a is null)`).
결과는 같고, 데이터베이스는 인덱스를 사용할 수 있습니다. Oracle에서 `is not distinct from`은 `decode(...)`가 되어 인덱스를 사용할 수 없습니다.

<a id="57-scalar-subqueries"></a>
## 5.7 스칼라 서브쿼리

**값 하나**를 내는 서브쿼리는 비교에 사용할 수 있습니다.

평균 가격보다 비싼 주문:

```kotlin
qb.from(Order::class.java)
    .where { o, sub -> o.price!! > sub.from(Order::class.java).avg { x -> x.price } }
    .toList()
// HQL: select o from Order o
//      where o.price > (select avg(x.price) from Order x)
```

**데이터베이스별 SQL (예정)** — 예정된 HQL을 Hibernate가 번역한 것으로, `PlannedSqlTest`가 기록했습니다. lambdaQL은 아직 이 쿼리를 만들지 않습니다. `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)의 예제 `05-scalar-avg`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.price>(select avg(o2_0.price) from orders o2_0)
```

스칼라 서브쿼리는 **항상 행 하나**를 내야 합니다. 허용되는 모양은 두 가지입니다.

| 모양 | 예 |
|---|---|
| 집계: `count()`, `sum`, `avg`, `min`, `max` | `sub.from(Item::class.java).where { i -> i.order?.id == o.id }.count()` |
| top-1: `orderBy { ... }.limit(1)...first()` | 아래 참고 |

그 밖의 `first()`는 분석할 때 오류가 됩니다.

top-1 예: 같은 고객의 가장 최근 주문.

```kotlin
qb.from(Order::class.java)
    .where { o, sub ->
        o.id == sub.from(Order::class.java)
            .where { x -> x.customer?.id == o.customer?.id }
            .orderBy { x, by -> by.desc(x.updateAt) }
            .limit(1)
            .select { x -> x.id }
            .first()
    }
    .toList()
```

집계의 NULL은 SQL을 따릅니다. 행이 없으면 `sum`, `avg`, `min`, `max`는 NULL을, `count`는 0을 냅니다.

<a id="58-nested-subqueries"></a>
## 5.8 중첩 서브쿼리

서브쿼리의 `where`도 `sub` 인자를 받을 수 있습니다. 모든 단계에서 같은 규칙이 다시 적용됩니다.

큰 품목이 있는 주문을 가진 고객:

```kotlin
qb.from(Customer::class.java)
    .where { c, sub ->
        sub.from(Order::class.java)
            .where { o, sub2 ->
                o.customer?.id == c.id &&
                    sub2.from(Item::class.java)
                        .where { i -> i.order?.id == o.id && i.qty > 10 }
                        .exists()
            }
            .exists()
    }
    .toList()
// HQL: select c from Customer c
//      where exists (select 1 from Order o
//                    where o.customer.id = c.id
//                      and exists (select 1 from Item i
//                                  where i.order.id = o.id and i.qty > 10))
```

**데이터베이스별 SQL (예정)** — 예정된 HQL을 Hibernate가 번역한 것으로, `PlannedSqlTest`가 기록했습니다. lambdaQL은 아직 이 쿼리를 만들지 않습니다. `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)의 예제 `05-nested`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from customers c1_0
where exists(select 1 from orders o1_0 where o1_0.customer_id=c1_0.id and exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id and i1_0.qty>10))
```

단계마다 새 이름(`sub2`, `o`, `i`)을 사용하십시오. Kotlin은 같은 이름을 다시 쓰는 것을 허용하지만 이름 가림(shadowing) 경고를 내고, 코드도 읽기 어려워집니다.

<a id="59-sorting-inside-a-subquery"></a>
## 5.9 서브쿼리 안의 정렬

| 경우 | lambdaQL의 처리 |
|---|---|
| `limit`과 함께 쓴 `orderBy`(top-1, top-N) | 유지 |
| `limit` 없는 `orderBy` | **제거**하고 경고를 로그에 남김(결과를 바꾸지 않음. Oracle에서는 문법 오류 ORA-00907까지 남) |
| MariaDB: `IN (... limit n)`(MariaDB가 지원하지 않음) | 자동으로 감쌈: `in (select x.id from (... limit n) x)` |
| MariaDB: `lateral`(MariaDB에서 잘못된 결과) | `row_number() over (partition by ... order by ...)`로 다시 작성 |

[4. 정렬과 페이징](04-order-paging.md)의 NULL 정책과 자동 `id` 동순위 처리는 서브쿼리 안에서도 적용됩니다.

<a id="510-paths-and-null-inside-subqueries"></a>
## 5.10 서브쿼리 안의 경로와 NULL

서브쿼리 안에서 조건, 경로, NULL은 **바깥 쿼리와 똑같이** 동작합니다([3. 조건(`where`)](03-where.md)).

- 두 nullable 값 사이의 `==`는 null-safe 값 비교입니다.
- `!!`는 무시됩니다. 경로의 `?.`는 같은 컬럼을 가리킵니다.
- 서브쿼리 조건 안의 연관 경로는 바깥 `where`와 같은 조인 규칙을 따릅니다(결정 D36). 기본은 inner `join`이고, null 가드가 연관이 없는 행을 남기면 `left join`입니다.
- `@Id`로 끝나는 경로는 외래 키 컬럼입니다(조인 없음).
- 컬렉션 경로(`o.items`)는 거부됩니다. 컬렉션에 대해 물으려면 서브쿼리를 사용합니다.

```kotlin
// it.items.size > 0 대신(거부됨):
.where { o, sub -> sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
```

<a id="511-subqueries-in-other-places"></a>
## 5.11 다른 곳의 서브쿼리

- **`from`의 서브쿼리(파생 테이블)와 CTE**(`qb.from { sub -> ... }`, `qb.with { sub -> ... }`): [6. 조회, 집계, 그룹화](06-select-group.md)를 참고하십시오.
- **update / delete의 서브쿼리**: 같은 `sub` 문법을 사용합니다. [7. 수정, 삽입, 삭제](07-dml.md)를 참고하십시오.

<a id="512-rules-summary-q1-to-q6"></a>
## 5.12 규칙 요약(Q1 ~ Q6)

| # | 규칙 |
|---|---|
| Q1 | 서브쿼리 안의 조건, 경로, NULL 규칙은 바깥과 같습니다. 조건 안의 연관 경로도 같은 조인 규칙(기본은 inner `join`)을 따릅니다 |
| Q2 | **NOT IN은 NOT EXISTS로 출력됩니다**: `x !in values`는 `not exists (select 1 from C c where <condition> and c.sel = x)`가 됩니다. `NOT IN` + NULL 함정을 피합니다 |
| Q3 | 스칼라 서브쿼리는 행 하나를 내야 합니다: 집계(`max`, `min`, `avg`, `sum`, `count`) 또는 `orderBy { }.limit(1)...first()`. 그 밖의 `first()`는 오류입니다 |
| Q4 | 서브쿼리 정렬: `limit`이 있으면 유지, 없으면 경고와 함께 제거. MariaDB `IN (... limit)`은 감싸고, `lateral`은 `row_number()`로 다시 작성합니다 |
| Q5 | 상관 조건에서 한쪽이 절대 NULL이 될 수 없으면(`@Id`, `nullable = false`, 기본형) `==`는 `=`로 씁니다. `!=`는 `(a <> b or a is null)`이 됩니다. 인덱스를 계속 사용할 수 있습니다 |
| Q6 | 서브쿼리의 `where`는 자체 `sub` 인자를 받을 수 있습니다: `where { i, sub2 -> ... }`. 모든 단계에서 같은 규칙이 적용됩니다 |

---

[Kotlin 트랙](README.md) | 이전: [4. 정렬과 페이징](04-order-paging.md) | 다음: [6. 조회, 집계, 그룹화](06-select-group.md) | [이 페이지의 Java 버전](../java/05-subquery.md)
