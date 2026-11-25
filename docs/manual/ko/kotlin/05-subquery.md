<a id="5-subqueries-kotlin"></a>
# 5. 서브쿼리 (Kotlin)

[Kotlin 트랙](README.md) | [매뉴얼 홈](../README.md) | 이전: [4. 정렬과 페이징](04-order-paging.md) | 다음: [6. 조회, 집계, 그룹화](06-select-group.md) | [이 페이지의 Java 버전](../java/05-subquery.md)

공통 페이지: [1. 개요](../01-overview.md) · [11. 오류와 언어](../11-errors-and-languages.md)

---

<a id="51-the-idea-a-second-lambda-argument-sub"></a>
## 5.1 아이디어: 두 번째 람다 표현식 인자 `sub`

`where` 람다 표현식은 **두 번째 인자**로 서브쿼리 팩토리를 받을 수 있습니다. 이 매뉴얼에서는 이를 `sub`라고 부릅니다(어떤 이름이든 사용할 수 있습니다).
그 안에서 일반 쿼리와 **같은 람다 표현식 문법**으로 서브쿼리를 작성합니다.

인자가 하나인 `where { ... }`(`it`)에는 서브쿼리가 없고, 인자가 둘인 `where { o, sub -> ... }`에는 서브쿼리가 있습니다.
lambdaQL은 인자의 개수로 두 형태를 구분합니다. Kotlin은 알맞은 오버로드를 고릅니다.
서브쿼리는 `&&`, `||`, `!`와 자유롭게 섞어 쓸 수 있습니다. import는 필요 없습니다.

`sub`로 할 수 있는 것:

| 서브쿼리 | 용도 | HQL |
|---|---|---|
| `sub.from(X::class.java).where { ... }.exists()` | 조건 | `exists (select 1 from X x where ...)` |
| `!sub.from(X::class.java).where { ... }.exists()` | 조건 | `not exists (...)` |
| `y in sub.from(X::class.java).where { ... }.select { x -> v }` | 조건 | `y in (select x.v from X x where ...)` |
| `y !in sub.from(...)...select { ... }` | 조건 | `y not in (select x.v from X x where ...)` |
| `sub.from(X::class.java).avg { x -> v }`(`sum`, `min`, `max`, `count()`도) | 값 | `(select avg(x.v) from X x)` |
| `sub.from(X::class.java)...orderBy { ... }.limit(1).select { x -> v }.first()` | 값 | top-1 서브쿼리 |
| `sub.from(A::class.java).join(B::class.java) { a, b -> ... }.where { a, b -> ... }.exists()` | 조건 | `exists (select 1 from A a join B b on ... where ...)`([서브쿼리 안의 조인](#joins-inside-a-subquery)) |

> [!NOTE]
> 반환 값(`Boolean`, `Double`, ...)은 코드가 컴파일되도록 하는 자리 표시자일 뿐입니다.
> 이 함수들은 lambdaQL 람다 표현식 안에서만 쓰입니다. lambdaQL은 람다 표현식을 호출하지 않고 바이트 opcode를 분석합니다.

Kotlin에서 `y in values`는 `values.contains(y)`로 컴파일됩니다. 그래서 Kotlin `in` 연산자는 추가 코드 없이 서브쿼리 API와 함께 동작합니다.

안쪽 람다 표현식(`sub.from(...).where { }` 안의 `{ i -> ... }`)는 따로 `Serializable`일 필요가 없습니다. lambdaQL은 바깥 람다 표현식의 바이트 opcode에서 이를 찾습니다.

HQL에서 서브쿼리 루트의 별칭은 `s1`, `s2`, ...입니다(바깥 루트는 `e0`). 서브쿼리 안의 조인은 `s1j1` 같은 별칭을 받습니다.

예제는 Java로 작성한 예제 엔티티를 사용합니다. `price`는 기본형 `double`, `qty`는 `int`, `status`는 `short`, `Customer.id`는 `int`입니다.

## 5.2 EXISTS

`qty > 10`인 품목이 하나 이상 있는 주문:

<!-- region: KotlinQueryTest.kt#exists -->
```kotlin
val q = qb.from(Order::class.java)
    .where { o, sub -> sub.from(Item::class.java).where { i -> i.order?.id == o.id && i.qty > 10 }.exists() }
assertEquals("select e0 from Order e0 where exists (select 1 from Item s1 where s1.order.id = e0.id and s1.qty > 10)",
    q.toHql().hql())
```

**데이터베이스별 SQL**

<!-- sql: sub-exists -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id and i1_0.qty>10)
```

이는 **상관** 서브쿼리입니다. 안쪽 람다 표현식이 바깥의 `o`를 사용합니다([5.6](#56-correlated-subqueries) 참고).

예제 코드: [`KotlinQueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)(`exists`).

## 5.3 NOT EXISTS

`!`를 사용합니다. 다른 조건과 결합할 수 있습니다.

<!-- region: ManualKotlinSubqueryTest.kt#not-exists -->
```kotlin
// orders with status 1, or orders without items
val q = qb.from(Order::class.java)
    .where { o, sub -> o.status.toInt() == 1 || !sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
assertEquals("select e0 from Order e0 where e0.status = 1 or not exists (select 1 from Item s1 where s1.order.id = e0.id)",
    q.toHql().hql())
```

**데이터베이스별 SQL**

<!-- sql: sub-not-exists -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 or not exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id)
```

예제 코드: [`ManualKotlinSubqueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSubqueryTest.kt)(`not-exists`).

## 5.4 IN

`grade > 3`인 고객의 주문. `in`을 사용합니다.

<!-- region: KotlinQueryTest.kt#in -->
```kotlin
val q = qb.from(Order::class.java)
    .where { o, sub -> o.customer?.id in sub.from(Customer::class.java).where { c -> c.grade!! > 3 }.select { c -> c.id } }
assertEquals("select e0 from Order e0 where e0.customer.id in (select s1.id from Customer s1 where s1.grade > 3)",
    q.toHql().hql())
```

`o.customer?.id`는 고객의 `@Id`로 끝나므로 외래 키 컬럼 `e0.customer.id`입니다. 조인이 필요 없습니다.
`!!`는 무시됩니다. `c.grade!! > 3`은 `s1.grade > 3`입니다.

예제 코드: [`KotlinQueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)(`in`).

<a id="55-not-in-is-written-as-not-exists"></a>
## 5.5 NOT IN과 NULL

`x !in values`는 SQL 의미 그대로 `NOT IN`으로 출력됩니다. 예제는 status가 어느 고객의 등급과도 같지 않은 주문을 찾습니다.

<!-- region: ManualKotlinSubqueryTest.kt#not-in -->
```kotlin
// the SQL meaning: NOT IN. The grades are 5, 2 and NULL, so no row matches: x NOT IN (5, 2, NULL)
// is never true. To skip the NULL, say so in the subquery: where { c -> c.grade != null }
val q = qb.from(Order::class.java)
    .where { o, sub -> o.status.toInt() !in sub.from(Customer::class.java).select { c -> c.grade } }
assertEquals("select e0 from Order e0 where e0.status not in (select s1.grade from Customer s1)", q.toHql().hql())
val withoutNull = qb.from(Order::class.java)
    .where { o, sub -> o.status.toInt() !in sub.from(Customer::class.java).where { c -> c.grade != null }.select { c -> c.grade } }
assertEquals("select e0 from Order e0 where e0.status not in (select s1.grade from Customer s1 where s1.grade is not null)",
    withoutNull.toHql().hql())
```

`status`는 `short`, `grade`는 `Int?`이므로 `o.status.toInt()`로 맞춥니다. lambdaQL은 이 변환을 무시합니다.

**데이터베이스별 SQL (첫 번째 쿼리)**

<!-- sql: sub-not-in -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status not in (select c2_0.grade from customers c2_0)
```

이유:

- `x NOT IN (subquery)`는 서브쿼리가 NULL을 하나라도 반환하면 **행을 하나도 반환하지 않습니다**. lambdaQL은 이 SQL 의미를 유지하고 `LQL-5009`를 `DEBUG` 수준으로 기록합니다.
- NULL을 건너뛰려면 위 예제의 `withoutNull`처럼 서브쿼리에 null 검사(`where { c -> c.grade != null }`)를 쓰십시오.
  예제 데이터에는 NULL 등급이 있으므로 첫 번째 쿼리는 행을 반환하지 않습니다.
- `!sub...exists()`는 여전히 `NOT EXISTS`로 작성됩니다.

예제 코드: [`ManualKotlinSubqueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSubqueryTest.kt)(`not-in`).

<a id="56-correlated-subqueries"></a>
## 5.6 상관 서브쿼리

안쪽 람다 표현식이 바깥 엔티티를 사용하면 서브쿼리는 **상관** 서브쿼리가 됩니다. 특별한 문법은 필요 없습니다. 바깥 변수를 그대로 사용합니다.

lambdaQL이 안쪽 람다 표현식의 캡처 값을 읽는 방법:

| 안쪽 람다 표현식에서 캡처한 것 | 변환 결과 |
|---|---|
| 바깥 엔티티(`o`) | 바깥 별칭에 대한 참조(`e0.id`) |
| 그 밖의 값(지역 `val`, 속성, ...) | [3.11](03-where.md#311-captured-values)과 같은 바인드 파라미터 |

<!-- region: ManualKotlinSubqueryTest.kt#correlated-param -->
```kotlin
// o comes from the outer query; minQty is a captured value: a bind parameter
val minQty = 10
val q = qb.from(Order::class.java)
    .where { o, sub -> sub.from(Item::class.java).where { i -> i.order?.id == o.id && i.qty > minQty }.exists() }
assertEquals("select e0 from Order e0 where exists (select 1 from Item s1 where s1.order.id = e0.id and s1.qty > :p1)",
    q.toHql().hql())
assertEquals(mapOf("p1" to 10), q.toHql().parameters())
```

**상관 조건의 `==`.** `i.order?.id == o.id`는 두 `Long?` 값을 비교합니다. 엔티티 값 두 개는 SQL 의미로 비교하므로([3.6](03-where.md#36-null-rules) 참고) lambdaQL은 단순한 `=`를 씁니다(`!=`는 `<>`).
데이터베이스는 인덱스를 사용할 수 있습니다.

예제 코드: [`ManualKotlinSubqueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSubqueryTest.kt)(`correlated-param`).

<a id="57-scalar-subqueries"></a>
## 5.7 스칼라 서브쿼리

**값 하나**를 내는 서브쿼리는 비교에 사용할 수 있습니다.

평균 가격보다 비싼 주문:

<!-- region: KotlinQueryTest.kt#scalar -->
```kotlin
val q = qb.from(Order::class.java).where { o, sub -> o.price > sub.from(Order::class.java).avg { x -> x.price } }
assertEquals("select e0 from Order e0 where e0.price > (select avg(s1.price) from Order s1)", q.toHql().hql())
```

**데이터베이스별 SQL**

<!-- sql: sub-scalar -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.price>(select avg(o2_0.price) from orders o2_0)
```

스칼라 서브쿼리는 **항상 행 하나를 내야** 합니다. 허용되는 형태는 두 가지입니다(규칙 Q3).

| 형태 | 예 |
|---|---|
| 집계: `count()`, `sum`, `avg`, `min`, `max` | `sub.from(Item::class.java).where { i -> i.order?.id == o.id }.count()` |
| top-1: `orderBy { ... }.limit(1)...first()` | 아래 참고 |

그 밖의 `first()`는 분석 시점 오류(`TranslationException`, `LQL-3017`)입니다.

품목이 두 개 이상인 주문(`count()`):

<!-- region: ManualKotlinSubqueryTest.kt#count -->
```kotlin
// orders with two or more items
val q = qb.from(Order::class.java)
    .where { o, sub -> sub.from(Item::class.java).where { i -> i.order?.id == o.id }.count() >= 2 }
assertEquals("select e0 from Order e0 where (select count(s1) from Item s1 where s1.order.id = e0.id) >= 2L", q.toHql().hql())
```

**데이터베이스별 SQL**

<!-- sql: sub-count -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where (select count(i1_0.id) from items i1_0 where i1_0.order_id=o1_0.id)>=2
```

top-1 예: 같은 고객의 가장 비싼 주문.

<!-- region: ManualKotlinSubqueryTest.kt#top-one -->
```kotlin
// the most expensive order of each customer: a correlated scalar subquery with orderBy and limit(1) (rule Q3)
val q = qb.from(Order::class.java)
    .where { o, sub ->
        o.id == sub.from(Order::class.java)
            .where { x -> x.customer?.id == o.customer?.id }
            .orderBy { x -> desc(x.price) }
            .limit(1)
            .select { x -> x.id }
            .first()
    }
assertEquals("select e0 from Order e0 where e0.id = (select s1.id from Order s1 where s1.customer.id = e0.customer.id"
    + " order by s1.price desc, s1.id asc limit 1)", q.toHql().hql())
// first() without orderBy and limit(1) is rejected
val e = assertThrows(LambdaQLException::class.java) {
    qb.from(Order::class.java).where { o, sub -> o.id == sub.from(Order::class.java).select { x -> x.id }.first() }
}
assertEquals("LQL-3017", e.code())
```

lambdaQL은 서브쿼리 안에서 마지막 정렬 키로 `id`를 더합니다.
상관 조건은 고객 엔티티가 아니라 외래 키(`customer?.id`)를 비교합니다. 고객이 없는 주문은 `s1.customer.id = e0.customer.id`가 참이 아니므로 결과에서 빠집니다. [5.10](#510-paths-and-null-inside-subqueries)을 참고하십시오.

집계의 NULL은 SQL을 따릅니다. `sum`, `avg`, `min`, `max`는 행이 없으면 NULL을 냅니다. `count`는 0을 냅니다.

예제 코드: [`KotlinQueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)(`scalar`), [`ManualKotlinSubqueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSubqueryTest.kt)(`count`, `top-one`).

<a id="58-nested-subqueries"></a>
## 5.8 중첩 서브쿼리

서브쿼리의 `where`도 `sub` 인자를 받을 수 있습니다. 모든 단계에서 같은 규칙이 다시 적용됩니다.

큰 품목이 있는 주문을 가진 고객:

<!-- region: ManualKotlinSubqueryTest.kt#nested -->
```kotlin
// customers that have an order with an item of quantity > 10: a subquery inside a subquery
val q = qb.from(Customer::class.java)
    .where { c, sub ->
        sub.from(Order::class.java)
            .where { o, sub2 ->
                o.customer?.id == c.id &&
                    sub2.from(Item::class.java).where { i -> i.order?.id == o.id && i.qty > 10 }.exists()
            }
            .exists()
    }
assertEquals("select e0 from Customer e0 where exists (select 1 from Order s1 where s1.customer is not null"
    + " and s1.customer.id = e0.id and exists (select 1 from Item s2 where s2.order.id = s1.id and s2.qty > 10))", q.toHql().hql())
```

Kotlin의 `o.customer?.id == c.id`는 `s1.customer is not null and ...`을 더합니다. 아래 SQL은 이 조건이 없는 같은 의미의 HQL에서 나온 것입니다.

단계마다 새 이름(`sub2`, `o`, `i`)을 사용하십시오. Kotlin은 같은 이름을 다시 쓰는 것을 허용하지만 이름 가림(shadowing) 경고를 내고, 코드도 읽기 어려워집니다.

예제 코드: [`ManualKotlinSubqueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSubqueryTest.kt)(`nested`).

<a id="59-sorting-inside-a-subquery"></a>
## 5.9 서브쿼리 안의 정렬

| 경우 | lambdaQL의 동작 |
|---|---|
| `limit`과 함께 쓴 `orderBy`(top-1, top-N) | `id` 동순위 처리와 함께 유지 |
| `limit` 없는 `orderBy` | **제거**하고 경고를 로그에 남김(결과를 바꾸지 않음. Oracle에서는 문법 오류 ORA-00907까지 남) |
| `limit`이 있는 서브쿼리에 대한 `in`(MariaDB는 `IN (... limit n)`을 지원하지 않음, 오류 1235) | 모든 데이터베이스에서 파생 테이블로 감쌈: `in (select t1.v from (select s1.id as v ... limit n) t1)` |

[4. 정렬과 페이징](04-order-paging.md)의 NULL 정책과 자동 `id` 동순위 처리는 서브쿼리 안에서도 적용됩니다.

<a id="510-paths-and-null-inside-subqueries"></a>
## 5.10 서브쿼리 안의 경로와 NULL

서브쿼리 안에서 조건, 경로, NULL은 **바깥 쿼리와 똑같이** 동작합니다([3. 조건(`where`)](03-where.md)).

- 엔티티 값 사이의 `==`는 SQL 의미의 값 비교입니다. 캡처 값 `null`은 어떤 행에도 일치하지 않습니다(`LQL-4045`).
- `!!`는 값을 바꾸지 않습니다. 경로의 `?.`와 `!!`는 같은 컬럼을 가리킵니다.
- 서브쿼리 자신의 엔티티에 대한 연관 경로는 바깥 `where`와 같은 조인 규칙을 따릅니다. 연관이 없는 행이 조건에 맞을 수 있으면 `left join`, 맞을 수 없으면 inner `join`입니다.
- `@Id`로 끝나는 경로는 외래 키 컬럼입니다(조인 없음).
- **바깥 엔티티**는 자신의 컬럼과 외래 키 id(`o.id`, `o.customer?.id`)만 사용할 수 있습니다. 조인이 필요한 바깥 엔티티의 경로(`o.customer?.name`)는 분석 시점 오류(`LQL-3047`)입니다. 대신 외래 키 id를 비교합니다.
- 컬렉션 경로(`Customer.orders` 같은 `@OneToMany`)는 거부됩니다(`LQL-3010`). 컬렉션에 대해 물으려면 서브쿼리를 사용합니다.
- 엔티티의 `@SQLRestriction`과 `@SoftDelete` 조건은 서브쿼리(`exists`, `in`) 안에도 Hibernate가 붙입니다(MariaDB 10.11, Oracle 23.26에서 실측). lambdaQL은 이 조건을 따로 처리하지 않습니다.

<!-- region: ManualKotlinSubqueryTest.kt#rejected-paths -->
```kotlin
// a collection path is rejected: ask about the collection with a subquery
val collection = assertThrows(LambdaQLException::class.java) {
    qb.from(Customer::class.java).where { it.orders.size > 0 }.toHql()
}
assertEquals("LQL-3010", collection.code())
val withOrders = qb.from(Customer::class.java)
    .where { c, sub -> sub.from(Order::class.java).where { o -> o.customer?.id == c.id }.exists() }
assertEquals("select e0 from Customer e0 where exists (select 1 from Order s1 where s1.customer is not null and s1.customer.id = e0.id)",
    withOrders.toHql().hql())
// a path of the outer entity that needs a join is rejected: compare the foreign key id instead
val outerJoin = assertThrows(LambdaQLException::class.java) {
    qb.from(Order::class.java)
        .where { o, sub -> sub.from(Customer::class.java).where { c -> c.name == o.customer?.name }.exists() }.toHql()
}
assertEquals("LQL-3047", outerJoin.code())
```

<a id="joins-inside-a-subquery"></a>
### 서브쿼리 안의 조인

서브쿼리 안에서도 엔티티 두 개를 조인할 수 있습니다. 형태는 `sub.from(A::class.java).join(B::class.java) { a, b -> ... }`와 `leftJoin(...) { ... }`이고, 결과는 `From2<A, B>`입니다.
`From2`의 모든 람다 표현식은 두 행을 받습니다: `where { a, b -> ... }`, `exists()`, `count()`, `select { a, b -> v }` 뒤의 `contains(x)`나 `first()`, `max`, `min`, `avg`, `sum { a, b -> v }`.

- HQL은 `exists (select 1 from Order s1 join Item s2 on s2.order.id = s1.id where ...)`처럼 됩니다.
- ON 조건 안의 연관 경로는 `left join`이 됩니다. 경로 조인은 그 경로가 시작하는 루트 바로 뒤에 둡니다.
- 서브쿼리 안의 엔티티는 두 개까지입니다. 조인 형태에서는 `orderBy`와 `limit`을 쓸 수 없습니다.
- `exists`, 조인한 행의 `sum`, `left join`과 ON 안의 연관 경로를 쓴 `in`을 세 DB에서 실측했습니다.

<!-- region: KotlinQueryTest.kt#sub-join -->
```kotlin
// customers with an order that has an item of quantity > 10: the subquery joins Order and Item
val q = qb.from(Customer::class.java).where { c, sub ->
    sub.from(Order::class.java)
        .join(Item::class.java) { o, i -> i.order?.id == o.id }
        .where { o, i -> o.customer?.id == c.id && i.qty > 10 }
        .exists()
}
assertEquals("select e0 from Customer e0 where exists (select 1 from Order s1 join Item s2 on s2.order.id = s1.id"
        + " where s1.customer is not null and s1.customer.id = e0.id and s2.qty > 10)", q.toHql().hql())
```

Kotlin의 `o.customer?.id == c.id`는 `s1.customer is not null and ...`을 더합니다(위 예제 참고).

조인한 행에 대한 집계(`sum { a, b -> v }`):

<!-- region: ManualKotlinSubqueryTest.kt#sub-join-sum -->
```kotlin
// an aggregate over the joined rows: customers whose items have a total quantity over 30
val q = qb.from(Customer::class.java).where { c, sub ->
    sub.from(Order::class.java)
        .join(Item::class.java) { o, i -> i.order?.id == o.id }
        .where { o, _ -> o.customer?.id == c.id }
        .sum { _, i -> i.qty } > 30
}
assertEquals("select e0 from Customer e0 where (select sum(s2.qty) from Order s1 join Item s2 on s2.order.id = s1.id"
    + " where s1.customer is not null and s1.customer.id = e0.id) > 30", q.toHql().hql())
```

예제 코드: [`ManualKotlinSubqueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSubqueryTest.kt)(`rejected-paths`, `sub-join-sum`), [`KotlinQueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)(`sub-join`).

<a id="511-subqueries-in-other-places"></a>
## 5.11 다른 곳의 서브쿼리

- **update / delete의 서브쿼리**: `where`에서 같은 `sub` 문법을 사용합니다. [7.4 삭제(delete)](07-4-delete.md)와 [7.2 수정(update)](07-2-update.md)을 참고하십시오.
- **`set` 값 안의 서브쿼리**(`o.total = sub.from(...).sum { ... }`): 대입 람다 표현식의 두 번째 인자 `sub`로 씁니다. [7.2 수정(update)](07-2-update.md#an-aggregate-from-a-subquery)을 참고하십시오.
- **`from`의 서브쿼리(파생 테이블)와 CTE**: 구현됨(마일스톤 M8-2). `sub`를 쓰지 않고 값 쿼리를 `qb.from(...)`이나 `qb.with(...)`에 전달합니다. [6.9 파생 테이블](06-select-group.md#69-derived-table-subquery-in-from)과 [6.10 CTE](06-select-group.md#610-cte-with)를 참고하십시오.

<a id="512-rules-summary-q1-to-q6"></a>
## 5.12 규칙 요약(Q1 ~ Q6)

| # | 규칙 |
|---|---|
| Q1 | 서브쿼리 안의 조건, 경로, NULL 규칙은 바깥과 같습니다. 조건 안의 연관 경로도 같은 조인 규칙(연관이 없는 행이 조건에 맞을 수 있으면 `left join`, 아니면 inner `join`)을 따릅니다. 조인이 필요한 바깥 엔티티의 경로는 오류이고, 외래 키 id는 허용됩니다 |
| Q2 | **NOT IN은 SQL 의미를 유지합니다**: `x !in values`는 `x not in (select c.sel from C c where <condition>)`이 됩니다. 서브쿼리에 NULL이 있으면 행이 반환되지 않습니다(`DEBUG` 수준 `LQL-5009`). NULL을 건너뛰려면 서브쿼리에 `c.sel != null`을 추가합니다 |
| Q3 | 스칼라 서브쿼리는 행 하나를 내야 합니다: 집계(`max`, `min`, `avg`, `sum`, `count`) 또는 `orderBy { }.limit(1)...first()`. 그 밖의 `first()`는 오류입니다 |
| Q4 | 서브쿼리 정렬: `limit`이 있으면 유지, 없으면 경고와 함께 제거. `IN (... limit)`은 파생 테이블로 감쌉니다 |
| Q5 | 상관 조건에서 `==`는 `=`로, `!=`는 `<>`로 씁니다(SQL 의미). 어느 한쪽이 NULL이면 일치하지 않습니다. 인덱스를 계속 사용할 수 있습니다 |
| Q6 | 서브쿼리의 `where`는 자체 `sub` 인자를 받을 수 있습니다: `where { i, sub2 -> ... }`. 모든 단계에서 같은 규칙이 적용됩니다 |

---

[Kotlin 트랙](README.md) | 이전: [4. 정렬과 페이징](04-order-paging.md) | 다음: [6. 조회, 집계, 그룹화](06-select-group.md) | [이 페이지의 Java 버전](../java/05-subquery.md)
