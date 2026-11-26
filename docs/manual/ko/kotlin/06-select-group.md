<a id="6-select-aggregates-and-group-by-kotlin"></a>
# 6. 조회, 집계, 그룹화 (Kotlin)

[Kotlin 트랙](README.md) | [매뉴얼 홈](../README.md) | 이전: [5. 서브쿼리](05-subquery.md) | 다음: [7. 수정, 삽입, 삭제](07-dml.md) | [이 페이지의 Java 버전](../java/06-select-group.md)

공통 페이지: [1. 개요](../01-overview.md) · [11. 오류와 언어](../11-errors-and-languages.md)

---

이 장에서는 쿼리가 **무엇을** 반환할지 고르는 방법을 설명합니다.

- 행마다 값 하나(`select { it.price }`)
- Kotlin 데이터 클래스, `Pair`, `Triple`
- `groupBy`, `having`과 함께 쓰는 집계(`count`, `sum`, `avg`, ...)
- 값 쿼리의 `distinct`, 페이징, 결과 함수
- 다른 엔티티와의 명시적 조인(`join`, `leftJoin`, `crossJoin`)
- FROM 절의 서브쿼리(파생 테이블)와 CTE(`with`)

<a id="contents"></a>
## 목차

- [6.1 예제 엔티티와 결과 타입](#61-example-entities-and-result-types)
- [6.2 값 하나 조회](#62-select-one-value)
- [6.3 데이터 클래스, Pair, Triple 조회](#63-select-a-data-class-pair-or-triple)
- [6.4 집계 함수: `Agg`](#64-aggregate-functions-agg)
- [6.5 그룹화(group by)](#65-group-by)
- [6.6 having](#66-having)
- [6.7 distinct, 페이징, 결과](#67-distinct-paging-and-results)
- [6.8 명시적 조인](#68-explicit-joins)
- [6.9 파생 테이블(FROM 안의 서브쿼리)](#69-derived-table-subquery-in-from)
- [6.10 CTE(`with`)](#610-cte-with)
- [6.11 규칙 J1 ~ J8](#611-rules-j1-to-j8)
- [6.12 v0.1에 없는 것](#612-not-in-v01)

---

<a id="61-example-entities-and-result-types"></a>
## 6.1 예제 엔티티와 결과 타입

예제는 다음 엔티티를 사용합니다([2. 시작하기](02-getting-started.md#example-entities) 참고).

| 엔티티 | 여기서 사용하는 속성 |
|---|---|
| `Order` | `id`, `price`, `status`, `active`, `customer`(many-to-one) |
| `Customer` | `id`, `name`, `grade` |
| `Item` | `order`, `price`, `qty` |

예제는 Java로 작성한 예제 엔티티(`price`는 기본형 `double`, `status`는 `short`, `Customer.id`는 `int`)와 다음 결과 타입을 사용합니다.

<!-- region: ManualKotlinSelectTest.kt#result-types -->
```kotlin
data class OrderView(val id: Long?, val customerName: String?, val price: Double?)
data class Stat(val customerId: Int?, val count: Long, val total: Double?)
data class KeyCount(val customerId: Int?, val status: Short?, val count: Long)
data class Total(val count: Long, val average: Double?)
```

Kotlin은 타입을 엄격히 검사하므로 생성자 파라미터 타입은 엔티티 속성의 타입에 맞춥니다(`Customer.id`는 `Int?`, `status`는 `Short?`).

> [!TIP]
> 결과에는 nullable 타입(`Double?`, `String?`)을 사용하십시오.
> 컬럼이나 `sum` 같은 집계는 NULL일 수 있습니다. non-null `Double`이나 `Long` 파라미터는 JVM 기본형이고, non-null `String` 파라미터도 NULL을 받지 않습니다.
> 이런 파라미터에 NULL이 들어가면 lambdaQL은 `IllegalStateException`을 던집니다. 기본형이면 메시지에 해당 인자와 래퍼 타입을 쓰라는 안내가 들어 있습니다.
> `count`는 NULL을 내지 않으므로 `count: Long`은 괜찮습니다.

예제 코드: [`ManualKotlinSelectTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSelectTest.kt)(`result-types`).

---

<a id="62-select-one-value"></a>
## 6.2 값 하나 조회

`select`는 람다 표현식 하나를 받습니다. 람다 표현식은 값 하나를 반환합니다. 결과는 그 값의 `ValueQuery`입니다.
`select`는 `where` 뒤와 `orderBy` 뒤에서 호출할 수 있습니다. 정렬, `limit`, `offset`은 그대로 유지됩니다.

<!-- region: ManualKotlinSelectTest.kt#value -->
```kotlin
val q = qb.from(Order::class.java)
    .where { it.status.toInt() == 1 }
    .orderBy { o -> asc(o.price) }
    .select { it.price }
assertEquals("select e0.price from Order e0 where e0.status = 1 order by e0.price asc", q.toHql().hql())
val prices: List<Double> = q.toList()
```

`price`는 NULL이 될 수 없으므로 정렬에 `nulls` 부분이 없습니다. NULL이 될 수 있는 컬럼이면 `nulls first`가 붙습니다([4.4](04-order-paging.md#44-null-ordering-is-the-same-on-every-database) 참고).

**데이터베이스별 SQL**

<!-- sql: value -->
```sql
-- H2, MariaDB, Oracle (same)
select o1_0.price
from orders o1_0
where o1_0.status=1
order by o1_0.price
```

연관 경로는 `where`에서와 마찬가지로 컬럼입니다. `select`에서는 lambdaQL이 `left join`을 추가하므로 고객이 없는 주문도 빠지지 않습니다(`where`에서는 고객이 없는 주문이 조건에 맞을 수 없으면 같은 경로가 inner join입니다).
`optional = false`로 매핑한 필수 연관은 행을 잃지 않으므로 `select`에서도 inner join입니다([4.5](04-order-paging.md#45-what-a-sort-key-can-be) 참고).

<!-- region: ManualKotlinSelectTest.kt#value-path -->
```kotlin
// a path in select is a left join: the order without a customer stays (its value is null)
val names = qb.from(Order::class.java).select { it.customer?.name }
assertEquals("select j1.name from Order e0 left join e0.customer j1", names.toHql().hql())
// !! gives the same query
assertEquals(names.toHql().hql(), qb.from(Order::class.java).select { it.customer!!.name }.toHql().hql())
// a path that ends with the @Id reads the foreign key column: no join
assertEquals("select e0.customer.id from Order e0", qb.from(Order::class.java).select { it.customer?.id }.toHql().hql())
```

`it.customer!!.name`도 같은 HQL을 만듭니다. [6.3](#63-select-a-data-class-pair-or-triple)의 데이터 클래스 예제에서도 이 `left join`을 볼 수 있습니다.
경로가 연관 엔티티의 `@Id`로 끝나면 조인이 없습니다. `it.customer?.id`는 외래 키 컬럼 `customer_id`를 읽습니다.

**엘비스 연산자.** boolean이 아닌 컬럼의 `?:`는 `coalesce`가 됩니다. `?.` 경로 뒤의 `?:`(`it.customer?.name ?: "none"`)는 조건으로 읽혀 `LQL-2022`로 거부됩니다.

<!-- region: ManualKotlinSelectTest.kt#value-elvis -->
```kotlin
// ?: on a column that is not a boolean: coalesce
val q = qb.from(Order::class.java).select { it.description ?: "" }
assertEquals("select coalesce(e0.description, '') from Order e0", q.toHql().hql())
// ?: after a ?. path is rejected (LQL-2022)
val afterPath = assertThrows(LambdaQLException::class.java) {
    qb.from(Order::class.java).select { it.customer?.name ?: "none" }.toHql()
}
assertEquals("LQL-2022", afterPath.code())
```

예제 코드: [`ManualKotlinSelectTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSelectTest.kt)(`value`, `value-path`, `value-elvis`).

---

<a id="63-select-a-data-class-pair-or-triple"></a>
## 6.3 데이터 클래스, Pair, Triple 조회

람다 표현식 안에서 생성자를 호출하십시오. 생성자의 각 인자는 값 식입니다.

<!-- region: ManualKotlinSelectTest.kt#data-class -->
```kotlin
// only the columns are selected; lambdaQL calls the constructor
val views = qb.from(Order::class.java).select { OrderView(it.id, it.customer?.name, it.price) }
assertEquals("select e0.id, j1.name, e0.price from Order e0 left join e0.customer j1", views.toHql().hql())
// Pair (a to b) and Triple
val pairs = qb.from(Order::class.java).select { it.customer?.name to it.price }
assertEquals("select j1.name, e0.price from Order e0 left join e0.customer j1", pairs.toHql().hql())
val triples = qb.from(Order::class.java).select { Triple(it.id, it.status, it.isActive) }
assertEquals("select e0.id, e0.status, e0.active from Order e0", triples.toHql().hql())
```

**데이터베이스별 SQL**

<!-- sql: record -->
```sql
-- H2, MariaDB, Oracle (same)
select o1_0.id,c1_0.name,o1_0.price
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
```

lambdaQL은 `select new com.acme.OrderView(...)`를 쓰지 **않습니다**.
컬럼을 조회하고 생성자는 lambdaQL이 직접 호출합니다. 데이터 클래스, `Pair`, `Triple` 모두 같습니다.
그래서 긴 클래스 이름이나 중첩 클래스 이름 때문에 생기는 문제를 피할 수 있습니다. 위 예제의 `OrderView`도 다른 클래스 안에 중첩된 데이터 클래스입니다.

반환할 수 있는 것:

| 결과 타입 | 단순 `select` | 파생 테이블이나 CTE의 행 타입([6.9](#69-derived-table-subquery-in-from)) |
|---|---|---|
| 값 하나(`it.price`) | 예 | – |
| Kotlin 데이터 클래스 | 예 | 예 |
| 생성자가 있는 일반 클래스 | 예 | **아니요** |
| `Pair` / `Triple` | 예 | 아니요 |
| Java record(Java 모듈에서) | 예 | 예 |

예제 코드: [`ManualKotlinSelectTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSelectTest.kt)(`data-class`).

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

`Agg.sum`은 `xs.sum()` 같은 Kotlin 표준 라이브러리 확장과 충돌하지 않습니다. 한 파일에서 두 `sum`을 함께 쓸 수 있습니다.

중요한 점:

- 반환 값은 **자리 표시자**일 뿐입니다. lambdaQL은 람다 표현식을 호출하지 않고 바이트 opcode를 분석하므로 그 값은 사용되지 않습니다.
- lambdaQL 람다 표현식 **밖에서** `Agg.sum(...)`을 호출하면 `IllegalStateException`을 던집니다.
- `Agg`는 `select`, `having`, `insert ... select`의 값([7.5](07-5-insert.md))에서 사용할 수 있습니다.
- `where`에서는 `Agg`를 사용할 수 없습니다. 집계에 대한 조건은 `having`을 사용하십시오. `where`에서 `Agg`를 사용하면 분석할 때 오류가 납니다.

<a id="aggregate-over-all-rows-no-group-by"></a>
### 모든 행에 대한 집계(group by 없음)

<!-- region: ManualKotlinSelectTest.kt#total -->
```kotlin
// aggregates without groupBy: always one row
val q = qb.from(Order::class.java)
    .where { it.status.toInt() == 1 }
    .select { Total(Agg.count(), Agg.avg(it.price)) }
assertEquals("select count(*), avg(e0.price) from Order e0 where e0.status = 1", q.toHql().hql())
val t: Total? = q.one().orElse(null)
```

**데이터베이스별 SQL**

<!-- sql: total -->
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

예제 코드: [`ManualKotlinSelectTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSelectTest.kt)(`total`).

---

<a id="65-group-by"></a>
## 6.5 그룹화(group by)

그룹 키는 `groupBy`로 작성합니다. `select`의 값으로 그룹 키를 자동으로 정하지 않습니다.
`groupBy`는 그룹 쿼리를 반환합니다. `having { }`(선택)을 호출한 다음 `select { }`를 호출하십시오.

<a id="one-key"></a>
### 키 하나

<!-- region: ManualKotlinSelectTest.kt#group-one -->
```kotlin
// the key ends with the @Id: no join. The orders without a customer are one group with the key NULL
val q = qb.from(Order::class.java)
    .groupBy { it.customer?.id }
    .select { Stat(it.customer?.id, Agg.count(), Agg.sum(it.price)) }
assertEquals("select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id", q.toHql().hql())
```

키가 `@Id`로 끝나므로 조인이 없습니다. 고객이 없는 주문은 키가 NULL인 그룹 하나가 됩니다.
`having`을 쓰는 예제는 [6.6](#66-having)에 있습니다.

<a id="many-keys"></a>
### 여러 키

`listOf(...)`로 키 리스트를 반환하거나, `Pair`(`a to b`)나 `Triple`을 사용하십시오.

<!-- region: ManualKotlinSelectTest.kt#group-many -->
```kotlin
val q = qb.from(Order::class.java)
    .where { it.customer != null }
    .groupBy { listOf(it.customer?.id, it.status) }
    .select { KeyCount(it.customer?.id, it.status, Agg.count()) }
assertEquals("select e0.customer.id, e0.status, count(*) from Order e0 where e0.customer is not null"
    + " group by e0.customer.id, e0.status", q.toHql().hql())
// a Pair (a to b) gives the same keys; a Triple gives three
val pair = qb.from(Order::class.java)
    .where { it.customer != null }
    .groupBy { it.customer?.id to it.status }
    .select { KeyCount(it.customer?.id, it.status, Agg.count()) }
assertEquals(q.toHql().hql(), pair.toHql().hql())
val three = qb.from(Order::class.java)
    .groupBy { Triple(it.customer?.id, it.status, it.isActive) }
    .select { Triple(it.customer?.id, it.status, Agg.count()) }
assertEquals("select e0.customer.id, e0.status, count(*) from Order e0 group by e0.customer.id, e0.status, e0.active",
    three.toHql().hql())
```

**데이터베이스별 SQL (첫 번째 쿼리)**

<!-- sql: group-many -->
```sql
-- H2, MariaDB, Oracle (same)
select o1_0.customer_id,o1_0.status,count(*)
from orders o1_0
where o1_0.customer_id is not null
group by o1_0.customer_id,o1_0.status
```

키는 작성한 순서대로 사용됩니다.
`groupBy`의 `listOf(...)`는 람다 표현식 안에서 만든 리스트가 허용되는 **유일한** 곳입니다.

키 리스트로 허용되지 않는 것:

| 형태 | 이유 |
|---|---|
| `setOf(a, b)` | 집합은 중복을 없애고 순서가 없음 |

<a id="every-non-aggregate-value-must-be-a-key-rule-j3"></a>
### 집계가 아닌 값은 모두 키여야 함(규칙 J3)

쿼리에 집계가 있으면 `select`와 `having`에서 집계가 **아닌** 값은 모두 `groupBy` 키여야 합니다. 이는 SQL 규칙입니다. lambdaQL은 분석 시점에 이를 검사합니다.

<!-- region: ManualKotlinSelectTest.kt#group-key-error -->
```kotlin
// status is not a group key
val e = assertThrows(LambdaQLException::class.java) {
    qb.from(Order::class.java)
        .groupBy { it.customer?.id }
        .select { KeyCount(it.customer?.id, it.status, Agg.count()) }
}
assertEquals("LQL-3035", e.code())
```

오류는 `select`를 호출할 때 발생합니다. 그 밖의 규칙 검사는 [6.11](#611-rules-j1-to-j8)을 참고하십시오.

예제 코드: [`ManualKotlinSelectTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSelectTest.kt)(`group-one`, `group-many`, `group-key-error`).

---

<a id="66-having"></a>
## 6.6 having

`having`은 그룹에 대한 조건입니다. `where`와 같은 람다 표현식 규칙([3. 조건(`where`)](03-where.md))을 사용하며, `Agg`를 사용할 수 있습니다.
`having`은 여러 번 호출할 수 있습니다. 조건은 `AND`로 연결됩니다.

<!-- region: ManualKotlinSelectTest.kt#having -->
```kotlin
// sum is imported (import org.lambdaql.Agg.sum); having can be called more than once (AND)
val q = qb.from(Order::class.java)
    .groupBy { it.customer?.id }
    .having { sum(it.price) > 200 }
    .having { Agg.count() >= 2 }
    .select { Stat(it.customer?.id, Agg.count(), sum(it.price)) }
assertEquals("select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id"
    + " having sum(e0.price) > 200.0 and count(*) >= 2L", q.toHql().hql())
```

`Agg.sum`은 Java 메서드이므로 결과가 플랫폼 타입입니다. 그래서 `sum(it.price) > 200`은 `!!` 없이 컴파일됩니다. `!!`를 붙여도 lambdaQL은 이를 무시합니다.

예제 코드: [`ManualKotlinSelectTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSelectTest.kt)(`having`).

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

<!-- region: ManualKotlinSelectTest.kt#distinct -->
```kotlin
val q = qb.from(Order::class.java)
    .orderBy { o -> desc(o.price) }
    .select { it.price }
    .distinct()
assertEquals("select distinct e0.price from Order e0 order by e0.price desc", q.toHql().hql())
// toSet() always uses select distinct
val prices = q.toSet()
```

**데이터베이스별 SQL**

<!-- sql: distinct -->
```sql
-- H2, MariaDB, Oracle (same)
select distinct o1_0.price
from orders o1_0
order by o1_0.price desc
```

- `distinct()`를 쓰면 모든 정렬 키가 select 값이어야 합니다. 그렇지 않으면 lambdaQL은 분석 시점에 `TranslationException`(`LQL-3046`)을 던집니다([6.11](#611-rules-j1-to-j8) 참고).
- 값 쿼리의 `toSet()`은 항상 `select distinct`를 사용합니다. 여기서는 값의 반복이 정상이므로 크기 검사를 하지 않습니다(엔티티의 `toSet()`과 다름, [2. 시작하기](02-getting-started.md#toset) 참고).
- `@Lob` 컬럼을 `distinct`나 `groupBy` 키로 쓰면 Oracle은 거부합니다(ORA-22848, ORA-22849). H2와 MariaDB는 실행합니다. lambdaQL은 쿼리를 바꾸지 않고 `LQL-5010`을 `DEBUG` 수준으로 기록합니다([4.5](04-order-paging.md#45-what-a-sort-key-can-be) 참고).

**페이징.** `limit`과 `offset`은 [4. 정렬과 페이징](04-order-paging.md)과 같게 동작합니다.
페이징을 쓰면 lambdaQL이 마지막 정렬 키로 `id`를 더하므로 페이지가 겹치지 않습니다. 정렬 키가 유일하고 NULL이 될 수 없는 컬럼이면 더하지 않습니다([4.8](04-order-paging.md#tie-breaker-automatic-id) 참고).
`first()`도 한 행만 읽으므로 같은 보조 정렬을 더합니다. 정렬 키 값이 같은 행이 여러 개이면 매번 `id`가 가장 작은 행의 값을 반환합니다.
그룹 쿼리와 `distinct` 쿼리에는 더하지 않습니다. 이 쿼리들의 select 목록에는 `id`가 없기 때문입니다.

<!-- region: ManualKotlinSelectTest.kt#value-page -->
```kotlin
// paging adds the id as the last key, so the pages do not overlap
val page = qb.from(Order::class.java)
    .orderBy { o -> desc(o.price) }
    .select { it.price }
    .limit(2).offset(1)
assertEquals("select e0.price from Order e0 order by e0.price desc, e0.id asc", page.toHql().hql())
```

**데이터베이스별 SQL**

<!-- sql: value-page -->
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

예제 코드: [`ManualKotlinSelectTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSelectTest.kt)(`distinct`, `value-page`).

---

<a id="68-explicit-joins"></a>
## 6.8 명시적 조인

연관 경로(`it.customer?.name`)는 to-one 연관을 자동으로 조인합니다([3.10](03-where.md#310-association-paths) 참고).
다른 엔티티를 직접 정한 조건으로 조인하거나 조건 없이 조인하려면 `join`, `leftJoin`, `crossJoin`을 사용하십시오.

| 함수 | HQL | 행 |
|---|---|---|
| `join(Customer::class.java) { o, c -> ... }` | `join Customer e1 on ...` | inner join: 짝이 있는 행만 |
| `leftJoin(Customer::class.java) { o, c -> ... }` | `left join Customer e1 on ...` | 짝이 없는 주 엔티티의 행도 남음 |
| `crossJoin(Customer::class.java)` | `cross join Customer e1` | 모든 행의 조합. 조건은 `where`에 작성 |

조인 뒤의 모든 람다 표현식은 조인 순서대로 모든 엔티티를 받습니다.
`where { o, c -> ... }`, `orderBy { o, c -> asc(c.name) }`, `select { o, c -> ... }`입니다.
조인한 엔티티의 별칭은 `e1`, `e2`입니다.

<!-- region: ManualKotlinSelectTest.kt#join -->
```kotlin
val q = qb.from(Order::class.java)
    .join(Customer::class.java) { o, c -> o.customer?.id == c.id }
    .where { _, c -> c.grade != null && c.grade >= 5 }
assertEquals("select e0 from Order e0 join Customer e1 on e0.customer is not null and e0.customer.id = e1.id"
    + " where e1.grade is not null and e1.grade >= 5", q.toHql().hql())
// without select, the result is the main entity
val result: List<Order> = q.toList()
```

Kotlin의 `o.customer?.id == c.id`는 NULL이 될 수 있는 값의 비교이므로 lambdaQL이 `e0.customer is not null and`를 덧붙입니다. 결과 행은 같습니다.

**결과는 주 엔티티입니다.** `select`가 없으면 `toList()`는 `List<Order>`를 반환합니다.
조인한 엔티티는 조건, 정렬, `select`에만 사용합니다.
조인한 엔티티의 값을 읽으려면 `select { o, c -> ... }`를 사용하십시오(아래 참고).

<a id="left-join-and-null"></a>
### left join과 NULL

<!-- region: ManualKotlinSelectTest.kt#left-join-null -->
```kotlin
// c is missing for an order without a customer: c == null finds those rows
val q = qb.from(Order::class.java)
    .leftJoin(Customer::class.java) { o, c -> o.customer?.id == c.id }
    .where { _, c -> c == null || c.name == "Kim" }
assertEquals("select e0 from Order e0 left join Customer e1 on e0.customer is not null and e0.customer.id = e1.id"
    + " where e1.id is null or e1.name = 'Kim'", q.toHql().hql())
```

- `c == null`은 "짝이 없음"을 뜻합니다. lambdaQL은 이를 `e1.id is null`(조인한 엔티티의 `@Id`)로 씁니다.
  람다 표현식 파라미터 `c`는 non-null 타입이므로 Kotlin 컴파일러가 `c == null`이 항상 false라는 경고를 낼 수 있습니다. lambdaQL은 이 식을 "짝이 없음"으로 읽습니다.
- `leftJoin` 뒤에 조인한 엔티티의 컬럼에 거는 조건은 **SQL의 의미**를 따릅니다. 짝이 없으면 컬럼이 NULL이므로 조건이 참이 아니고, 그 행은 결과에서 빠집니다.
  짝이 없는 행을 남기려면 예제처럼 `c == null || ...`을 작성하십시오.

<a id="sort-and-select"></a>
### 정렬과 select

예제의 `Row`는 다음 데이터 클래스입니다.

<!-- region: ManualKotlinSelectTest.kt#row-type -->
```kotlin
data class Row(val orderId: Long?, val customerName: String?)
```

<!-- region: ManualKotlinSelectTest.kt#join-sort-select -->
```kotlin
val sorted = qb.from(Order::class.java)
    .leftJoin(Customer::class.java) { o, c -> o.customer?.id == c.id }
    .orderBy { o, c -> asc(c.name).asc(o.id) }
assertEquals("select e0 from Order e0 left join Customer e1 on e0.customer is not null and e0.customer.id = e1.id"
    + " order by e1.name asc nulls first, e0.id asc", sorted.toHql().hql())
// the order without a customer has customerName = null
val rows = qb.from(Order::class.java)
    .leftJoin(Customer::class.java) { o, c -> o.customer?.id == c.id }
    .orderBy { o, _ -> asc(o.id) }
    .select { o, c -> Row(o.id, c.name) }
assertEquals("select e0.id, e1.name from Order e0 left join Customer e1 on e0.customer is not null and e0.customer.id = e1.id"
    + " order by e0.id asc", rows.toHql().hql())
```

고객이 없는 주문은 `customerName`이 `null`입니다.
`select`는 `ValueQuery`를 반환하므로 `distinct()`, `limit`, `offset`, [6.7](#67-distinct-paging-and-results)의 결과 함수를 사용할 수 있습니다.

<a id="select-entities"></a>
### 엔티티 선택

`select`에는 엔티티 자체도 쓸 수 있습니다. 조인한 엔티티를 고르거나, 여러 엔티티를 data class나 `Pair` 하나에 담을 수 있습니다.

<!-- region: KotlinSelectEntityTest.kt#select-joined -->
```kotlin
// the customers that have an order, through an explicit join; Hibernate returns each entity once
val q = qb.from(Order::class.java)
    .join(Customer::class.java) { o, c -> o.customer?.id == c.id }
    .select { _, c -> c }
```

엔티티 하나를 고르면 Hibernate가 같은 엔티티를 한 번만 돌려줍니다. HQL은 `select e1 from Order e0 join Customer e1 on e0.customer is not null and e0.customer.id = e1.id`입니다.

<!-- region: KotlinSelectEntityTest.kt#select-entities -->
```kotlin
// an order and its customer together; with leftJoin an order without a customer has null
val q = qb.from(Order::class.java)
    .leftJoin(Customer::class.java) { o, c -> o.customer?.id == c.id }
    .select { o, c -> OrderWithCustomer(o, c) }
```

`OrderWithCustomer`는 `data class OrderWithCustomer(val order: Order, val customer: Customer?)`입니다. HQL은 `select e0, e1 from Order e0 left join Customer e1 on ...`이고, 주문마다 한 행입니다. `select { o, c -> o to c }`처럼 `Pair`로 받아도 됩니다.

엔티티를 담은 행은 파생 테이블이나 CTE의 행으로 쓸 수 없습니다(`LQL-3071`). 엔티티의 id나 컬럼을 고르십시오.

예제 코드: [`KotlinSelectEntityTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinSelectEntityTest.kt)(`select-joined`, `select-entities`).

<a id="three-entities-and-repeated-rows"></a>
### 엔티티 여러 개와 반복되는 행

엔티티는 10개까지 조인할 수 있습니다(주 엔티티와 조인 엔티티 9개). 조인할 때마다 람다 표현식이 받는 엔티티가 하나씩 늘어납니다: `{ o, c, i -> ... }`.

<!-- region: ManualKotlinSelectTest.kt#join-three -->
```kotlin
// orders of a customer with an item of quantity > 10 (an order with two such items is two SQL rows)
val q = qb.from(Order::class.java)
    .join(Customer::class.java) { o, c -> o.customer?.id == c.id }
    .join(Item::class.java) { o, _, i -> i.order?.id == o.id }
    .where { _, _, i -> i.qty > 10 }
assertEquals("select e0 from Order e0 join Customer e1 on e0.customer is not null and e0.customer.id = e1.id"
    + " join Item e2 on e2.order.id = e0.id where e2.qty > 10", q.toHql().hql())
```

엔티티 10개를 조인한 예입니다.

<!-- region: KotlinSelectEntityTest.kt#ten -->
```kotlin
// up to 10 entities: each lambda gets all of them in join order
val q = qb.from(Order::class.java)
    .join(Customer::class.java) { o, c -> o.customer?.id == c.id }
    .join(Order::class.java) { o, _, a -> a.id == o.id }
    .join(Order::class.java) { _, _, a, b -> b.id == a.id }
    .join(Order::class.java) { _, _, _, b, d -> d.id == b.id }
    .join(Order::class.java) { _, _, _, _, d, e -> e.id == d.id }
    .join(Order::class.java) { _, _, _, _, _, e, f -> f.id == e.id }
    .join(Order::class.java) { _, _, _, _, _, _, f, g -> g.id == f.id }
    .join(Order::class.java) { _, _, _, _, _, _, _, g, h -> h.id == g.id }
    .leftJoin(Order::class.java) { _, _, _, _, _, _, _, _, h, k -> k.id == h.id }
    .where { _, c, _, _, _, _, _, _, _, k -> c.name == "Kim" && k.price > 0.0 }
    .select { _, c, _, _, _, _, _, _, _, k -> c.name to k.id }
```

> [!WARNING]
> 조인 조건에 맞는 행이 여러 개이면 SQL에서는 **주 엔티티의 행이 짝마다 반복됩니다**. `toList()`와 `toSet()`은 Hibernate가 같은 엔티티를 한 번만 돌려주므로 반복이 없습니다.
> 하지만 `count()`와 `limit`은 반복된 SQL 행을 셉니다. 주문 수를 세거나 페이지를 정확히 채우려면 조인 대신 `exists()` 서브쿼리([5. 서브쿼리](05-subquery.md))를 사용하거나, 조인 쿼리에 `distinct()`를 적용하십시오([조인 결과의 `distinct()`](#distinct-on-a-join) 참고).

<a id="cross-join"></a>
### cross join

`crossJoin`에는 조인 조건이 없습니다. 함수 이름으로 모든 행의 조합을 원한다는 의도를 나타냅니다. 조건은 `where`에 작성하십시오.

<!-- region: ManualKotlinSelectTest.kt#cross-join -->
```kotlin
// every pair of rows; the conditions go in where
val q = qb.from(Order::class.java)
    .crossJoin(Customer::class.java)
    .where { o, c -> c.grade != null && o.price > 100 }
assertEquals("select e0 from Order e0 cross join Customer e1 where e1.grade is not null and e0.price > 100.0",
    q.toHql().hql())
```

lambdaQL에는 `from(Order::class.java, Customer::class.java)`(콤마 조인)가 없습니다. 조건을 빠뜨리면 실수로 모든 조합을 얻고, 어느 엔티티가 결과인지도 불분명하기 때문입니다. 그런 조회에는 `crossJoin`을 사용하십시오.

<a id="paging"></a>
### 페이징

페이징하면 lambdaQL은 **모든** 엔티티의 id를 마지막 정렬 키로 덧붙입니다. 그래서 페이지가 겹치지 않습니다.

<!-- region: ManualKotlinSelectTest.kt#join-paging -->
```kotlin
// with paging, the id of every entity is added to the order, so the pages do not overlap
val pairs = qb.from(Order::class.java)
    .crossJoin(Customer::class.java)
    .orderBy { o, _ -> desc(o.price) }
    .select { o, c -> Row(o.id, c.name) }
assertEquals("select e0.id, e1.name from Order e0 cross join Customer e1 order by e0.price desc, e0.id asc, e1.id asc",
    pairs.limit(5).toHql().hql())
```

페이지를 차례로 읽으면 모든 조합이 한 번씩, 정렬 순서대로 나옵니다.

<a id="distinct-on-a-join"></a>
### 조인 결과의 `distinct()`

조인 쿼리의 `distinct()`는 주 엔티티의 행마다 SQL 행을 하나로 만듭니다. HQL은 `select distinct e0`이고, `count()`는 `count(distinct e0)`입니다.
`toList()`와 `toSet()`은 `distinct()` 없이도 주 엔티티를 한 번씩 돌려줍니다. `count()`와 `limit`이 주 엔티티 수를 세게 하려면 `distinct()`가 필요합니다.

- 정렬 키는 주 엔티티 자신의 컬럼이어야 합니다(`LQL-3046`). 조인한 엔티티의 컬럼이나 연관 경로로 정렬하면 오류입니다.
- 페이징하면 주 엔티티의 id만 동순위 키로 덧붙입니다. 조인한 엔티티의 id는 붙이지 않습니다.
- `distinct()` 뒤에 `select(...)`를 쓰면 값을 `select distinct`로 조회합니다.
- 먼저 `exists` 서브쿼리([5. 서브쿼리](05-subquery.md))를 검토하십시오. 반복 없이 같은 행을 주고, 더 빠른 경우가 많습니다.

<!-- region: KotlinQueryTest.kt#join-distinct -->
```kotlin
// an order with two matching items is two SQL rows; distinct() makes it one row in SQL
val q = qb.from(Order::class.java)
    .join(Item::class.java) { o, i -> i.order?.id == o.id }
    .where { _, i -> i.qty > 0 }
    .distinct()
assertEquals("select distinct e0 from Order e0 join Item e1 on e1.order.id = e0.id where e1.qty > 0", q.toHql().hql())
```

<a id="join-a-derived-table-or-cte"></a>
### 파생 테이블과 CTE의 조인

[6.9](#69-derived-table-subquery-in-from)의 파생 테이블과 [6.10](#610-cte-with)의 CTE를 엔티티처럼 조인할 수 있습니다.
형태는 `join(cte) { c, x -> ... }`, `leftJoin(cte) { ... }`, `join(qb.from(...)...select { Row(...) }) { ... }`, `leftJoin(...) { ... }`이고, 두 번째나 세 번째 루트로 쓸 수 있습니다.

- HQL은 `with t as (...) ... join t e1 on ...` 또는 `join (select ...) e1 on ...`입니다.
- 행 타입은 Kotlin data class(또는 Java 레코드)입니다. 속성(`x.total`)이 컬럼을 읽습니다.
- 같은 `Cte` 객체를 한 문장에서 두 번 읽으면 정의를 한 번만 쓰고, 두 조인이 같은 이름을 읽습니다: `join t e1 on ... left join t e2 on ...`. 서로 다른 `Cte` 객체는 `with t as (...), t2 as (...)`처럼 이름을 따로 받습니다(세 DB에서 실측).
- 파생 행에는 id가 없으므로 `x == null`은 오류(`LQL-3067`)입니다. `x.customerId == null`처럼 컬럼을 비교하십시오.
- Hibernate 6.6과 7.4는 모두 엔티티의 `select distinct`, 파생 테이블과 CTE의 inner/left join, 서브쿼리 안의 엔티티 조인을 받아들입니다(세 DB에서 실측).

<!-- region: KotlinQueryTest.kt#join-cte -->
```kotlin
// the total of each customer, joined to the customer row
val t = qb.with(qb.from(Order::class.java)
    .where { it.customer != null }
    .groupBy { it.customer?.id }
    .select { CustTotal(it.customer?.id, Agg.sum(it.price)) })
val rows = qb.from(Customer::class.java)
    .join(t) { c, x -> x.customerId == c.id }
    .where { _, x -> x.total!! > 100 }
    .select { c, x -> c.name to x.total }
assertEquals("with t as (select d0.customer.id as customerId, sum(d0.price) as total from Order d0"
        + " where d0.customer is not null group by d0.customer.id) select e0.name, e1.total from Customer e0"
        + " join t e1 on e1.customerId = e0.id where e1.total > 100.0", rows.toHql().hql())
```

`x.customerId`가 nullable `Int?`여도 `x.customerId == c.id`는 `e1.customerId = e0.id`입니다. Kotlin이 넣는 null 검사는 읽지 않습니다.

이름 없는 파생 테이블의 조인(`CustomerTotal`은 [6.9](#69-derived-table-subquery-in-from)의 행 타입):

<!-- region: ManualKotlinSelectTest.kt#join-derived -->
```kotlin
// a derived table without a name; a customer without orders has no row in it (left join)
val small = qb.from(Customer::class.java)
    .leftJoin(qb.from(Order::class.java)
        .where { it.customer != null }
        .groupBy { it.customer?.id }
        .select { CustomerTotal(it.customer?.id, Agg.count(), Agg.sum(it.price)) }) { c, x -> x.customerId == c.id }
    .where { _, x -> x.customerId == null || x.total!! < 210 }
assertEquals("select e0 from Customer e0 left join (select d0.customer.id as customerId, count(*) as cnt,"
    + " sum(d0.price) as total from Order d0 where d0.customer is not null group by d0.customer.id) e1"
    + " on e1.customerId = e0.id where e1.customerId is null or e1.total < 210.0", small.toHql().hql())
```

같은 CTE를 두 번 읽는 형태:

<!-- region: ManualKotlinSelectTest.kt#cte-reuse -->
```kotlin
// the same CTE read twice is written once
val reused = qb.from(Customer::class.java)
    .join(t) { c, x -> x.customerId == c.id }
    .leftJoin(t) { c, _, y -> y.customerId == c.id && y.total!! > 210 }
    .select { c, _, y -> c.name to y.total }
assertEquals("with t as (select d0.customer.id as customerId, count(*) as cnt, sum(d0.price) as total from Order d0"
    + " where d0.customer is not null group by d0.customer.id)"
    + " select e0.name, e2.total from Customer e0 join t e1 on e1.customerId = e0.id"
    + " left join t e2 on e2.customerId = e0.id and e2.total > 210.0", reused.toHql().hql())
```

<a id="what-a-join-query-has"></a>
### 조인 쿼리의 함수

| 호출 뒤 | 함수 |
|---|---|
| `join`, `leftJoin`, `crossJoin` | `where`, `join` / `leftJoin` / `crossJoin`(엔티티 10개까지, 파생 테이블과 CTE 포함), `distinct`, `orderBy`, `select`, `limit`, `toList`, `toSet`, `first`, `count`, `exists`, `toHql` |
| `orderBy { o, c -> ... }` | `select`, `limit`, `offset`, `toList`, `first`, 그 밖의 정렬된 쿼리 함수([4. 정렬과 페이징](04-order-paging.md)) |

서브쿼리 안의 조인은 [5. 서브쿼리](05-subquery.md#joins-inside-a-subquery)를 참고하십시오.
지원하지 않는 것: insert의 조인, 엔티티 11개 이상, `SortSpec`의 조인 형태.
update와 delete의 조인은 추가하지 않기로 했습니다. 연관 경로나 `sub`를 쓰십시오.

예제 코드: [`ManualKotlinSelectTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSelectTest.kt)(`join`, `left-join-null`, `row-type`, `join-sort-select`, `join-three`, `cross-join`, `join-paging`, `join-derived`, `cte-reuse`), [`KotlinQueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)(`join-distinct`, `join-cte`), [`KotlinSelectEntityTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinSelectEntityTest.kt)(`ten`).

---

<a id="69-derived-table-subquery-in-from"></a>
## 6.9 파생 테이블(FROM 안의 서브쿼리)

`select`로 값 쿼리(데이터 클래스, [6.3](#63-select-a-data-class-pair-or-triple) 참고)를 만든 뒤 `qb.from(...)`에 전달하십시오.
그 행이 바깥 쿼리의 테이블이 됩니다.
결과는 `DerivedQuery<R>`이며, `R`은 행 타입입니다.

이 절과 [6.10](#610-cte-with)의 행 타입:

<!-- region: ManualKotlinSelectTest.kt#derived-types -->
```kotlin
/** A row of a derived table or CTE: the properties name the columns (J5). */
data class CustomerTotal(val customerId: Int?, val cnt: Long, val total: Double?)
data class Priced(val id: Long?, val price: Double?)
```

<!-- region: ManualKotlinSelectTest.kt#derived -->
```kotlin
// the rows of a group query are the table of the outer query; r.total reads the column "total"
val q = qb.from(qb.from(Order::class.java)
        .where { it.customer != null }
        .groupBy { it.customer?.id }
        .select { CustomerTotal(it.customer?.id, Agg.count(), Agg.sum(it.price)) })
    .where { r -> r.total!! > 100 }
    .orderBy { r -> desc(r.total) }
assertEquals("select e0.customerId, e0.cnt, e0.total from (select d0.customer.id as customerId, count(*) as cnt,"
    + " sum(d0.price) as total from Order d0 where d0.customer is not null group by d0.customer.id) e0"
    + " where e0.total > 100.0 order by e0.total desc nulls last", q.toHql().hql())
```

- 안쪽 쿼리의 별칭은 `d0`(조인은 `dj1`, ...), 바깥 쿼리의 별칭은 `e0`입니다.
- 데이터 클래스의 속성마다 주 생성자의 순서대로 컬럼 이름이 정해집니다: `as customerId`, `as cnt`, `as total`.
- 바깥 람다 표현식에서는 속성으로 컬럼을 읽습니다. `r.total`은 `e0.total`입니다.
- 바깥 쿼리에는 `where`, `orderBy`, `limit`, `offset`, `select`, `toList`, `first`, `count`, `toHql`이 있습니다.

행의 값 하나를 select할 수도 있습니다(`.select { r -> r.customerId }`). 안쪽과 바깥의 캡처 값은 순서대로 바인딩합니다.
안쪽 쿼리의 `limit`과 `offset`은 안쪽 쿼리에 남습니다(HQL `limit`, `offset`). 그래서 상위 행을 다시 정렬할 수 있습니다.

<!-- region: ManualKotlinSelectTest.kt#derived-limit -->
```kotlin
// the three most expensive orders, then the cheapest of them
val cheapestOfTop = qb.from(qb.from(Order::class.java)
        .orderBy { o -> desc(o.price) }
        .limit(3)
        .select { Priced(it.id, it.price) })
    .orderBy { r -> asc(r.price) }
    .first()
```

Hibernate 7.4는 파생 테이블의 컬럼에 `c0`, `c1`, `c2` 같은 이름을 붙입니다. H2는 컬럼 목록을 별칭에 붙입니다(`r1_0(c0,c1,c2)`). MariaDB와 Oracle은 서브쿼리 안에 컬럼 별칭을 씁니다.

규칙:

- 행 타입은 **Kotlin 데이터 클래스**(또는 Java record)여야 합니다.
  lambdaQL은 데이터 클래스 속성에서 주 생성자의 순서대로 컬럼 이름을 가져옵니다.
- 일반 클래스, `Pair`, `Triple`, 값 하나는 여기서 사용할 수 없습니다. lambdaQL이 컬럼 이름을 정할 수 없기 때문입니다. 이 경우 `TranslationException`(`LQL-3064`)이 발생합니다.

<!-- region: ManualKotlinSelectTest.kt#derived-row-type -->
```kotlin
// a single value (or a Pair, or a class that is not a data class) has no column names
val e = assertThrows(LambdaQLException::class.java) { qb.from(qb.from(Order::class.java).select { it.price }) }
assertEquals("LQL-3064", e.code())
```

엔티티 쿼리에 파생 테이블을 조인하는 형태는 [파생 테이블과 CTE의 조인](#join-a-derived-table-or-cte)을 참고하십시오.

지원하지 않는 것: 파생 테이블끼리의 조인, update, delete, insert 안의 파생 테이블.

엔티티의 `@SQLRestriction`과 `@SoftDelete` 조건은 엔티티 조인의 `on`과 파생 테이블 안에도 Hibernate가 붙입니다(MariaDB 10.11, Oracle 23.26에서 실측). lambdaQL은 이 조건을 따로 처리하지 않습니다.

예제 코드: [`ManualKotlinSelectTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSelectTest.kt)(`derived-types`, `derived`, `derived-limit`, `derived-row-type`).

---

<a id="610-cte-with"></a>
## 6.10 CTE(`with`)

`qb.with(valueQuery)`로 `Cte<R>` 타입의 핸들을 만드십시오. 그런 다음 `qb.from(handle)`로 읽습니다.
안쪽 쿼리는 파생 테이블(6.9)과 같습니다.

<!-- region: ManualKotlinSelectTest.kt#cte -->
```kotlin
val perCustomer = qb.from(Order::class.java)
    .where { it.customer != null }
    .groupBy { it.customer?.id }
    .select { CustomerTotal(it.customer?.id, Agg.count(), Agg.sum(it.price)) }
val t: Cte<CustomerTotal> = qb.with(perCustomer)
val q = qb.from(t).where { r -> r.cnt >= 2 }
assertEquals("with t as (select d0.customer.id as customerId, count(*) as cnt, sum(d0.price) as total"
    + " from Order d0 where d0.customer is not null group by d0.customer.id)"
    + " select e0.customerId, e0.cnt, e0.total from t e0 where e0.cnt >= 2L", q.toHql().hql())
```

MariaDB와 Oracle은 진짜 `with`를 실행합니다. 컬럼 목록은 Hibernate가 직접 씁니다: `with t (customerId,cnt,total) as (...)`. H2는 CTE를 파생 테이블로 바꿉니다.

참고:

- CTE의 이름은 `t`입니다. 바깥 쿼리는 항상 별칭 `e0`을 붙입니다(`from t e0`). HQL에서 별칭이 필요합니다.
- CTE의 컬럼 이름은 select 별칭(`as customerId`)으로 정합니다. HQL은 CTE 이름 뒤의 컬럼 목록을 받지 않으며, 컬럼 목록은 Hibernate가 SQL에 추가합니다.
- MariaDB와 Oracle은 실제 `with`를 실행합니다. H2에서는 Hibernate가 이를 파생 테이블로 펼칩니다. 결과는 같습니다.
- 행 타입 규칙은 파생 테이블과 같습니다: 데이터 클래스나 record.
- 엔티티 쿼리에 CTE를 조인하는 형태는 [파생 테이블과 CTE의 조인](#join-a-derived-table-or-cte)을 참고하십시오.
- 같은 CTE를 한 쿼리에서 두 번 읽을 수 있습니다. 정의는 한 번만 씁니다([파생 테이블과 CTE의 조인](#join-a-derived-table-or-cte) 참고).
- 지원하지 않는 것: update, delete, insert 안의 CTE.

예제 코드: [`ManualKotlinSelectTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSelectTest.kt)(`cte`).

---

<a id="611-rules-j1-to-j8"></a>
## 6.11 규칙 J1 ~ J8

| # | 규칙 | 상태 |
|---|---|---|
| J1 | `select` 람다 표현식은 **값 식 하나**나 **생성자 호출 하나**를 반환합니다. 생성자의 각 인자는 값 식입니다([3. 조건(`where`)](03-where.md)의 규칙). 엔티티 자체도 값이 될 수 있습니다([엔티티 선택](#select-entities)). 값을 고르는 `if`와 `when`은 `case when`이 됩니다([3.5.1](03-where.md#351-values-chosen-by-a-condition)). `select`에만 쓰인 연관 경로는 `left join` 별칭이 되므로 행을 잃지 않습니다. 필수 연관(`optional = false`)은 inner join입니다(A4). 같은 경로를 `where`에서 쓰면 `select`는 `where`의 조인을 사용합니다(R24). | 구현됨 |
| J2 | 생성자의 경우 lambdaQL은 컬럼만 조회하고 생성자를 직접 호출합니다. `select new`는 쓰지 않습니다. `Pair`/`Triple`도 같은 방식으로 만듭니다. | 구현됨 |
| J3 | 집계가 있으면 `select`와 `having`(그리고 `insert ... select`의 값)에서 집계가 아닌 값은 모두 `groupBy` 키여야 합니다. 그렇지 않으면 분석 시점에 오류가 발생합니다. | 구현됨. 오류는 `TranslationException`(`LQL-3035`, [11](../11-errors-and-languages.md) 참고) |
| J4 | `sum`/`avg`/`min`/`max`는 행이 없거나 모든 값이 NULL이면 NULL을 냅니다. `count`는 0을 냅니다. 생성자 파라미터는 nullable로 쓰십시오. non-null 파라미터에 NULL이 오면 `IllegalStateException`이 발생합니다. | 구현됨 |
| J5 | 파생 테이블이나 CTE의 행 타입: **Kotlin 데이터 클래스나 Java record만**. 일반 클래스는 단순 `select`에서만 허용됩니다. 그렇지 않으면 `LQL-3064`. 조인한 파생 테이블과 CTE의 행에도 적용합니다. 파생 행은 `null`과 비교할 수 없습니다(`LQL-3067`). | 구현됨 |
| J6 | `qb.with(...)`로 만든 CTE는 select 별칭으로 컬럼 이름을 정합니다: `with t as (select d0.customer.id as customerId, ...) select e0.customerId ... from t e0`. HQL은 이름 뒤의 컬럼 목록을 받지 않습니다. Hibernate는 `with t (col list) as (...)`를 `SyntaxException`으로 거부합니다. 컬럼 목록은 Hibernate가 SQL에 직접 씁니다(MariaDB와 Oracle에서 `with t (customerId,cnt,total) as (...)`). H2는 CTE를 파생 테이블로 바꿉니다. 바깥 쿼리는 항상 별칭을 사용합니다(`from t e0`). | 구현됨 |
| J7 | `distinct()`는 `select distinct`를 출력합니다. 정렬과 함께 쓰면 정렬 키가 select 목록에 있어야 합니다(DB 규칙). lambdaQL은 분석 시점에 이를 검사합니다. 조인 결과의 `distinct()`에서는 정렬 키가 주 엔티티 자신의 컬럼이어야 합니다. | 구현됨 |
| J8 | 조회는 정렬([4. 정렬과 페이징](04-order-paging.md)), 페이징, 서브쿼리([5. 서브쿼리](05-subquery.md))와 함께 동작합니다. 그룹 쿼리의 집계 결과로 정렬하는 기능(`desc(s.total)`)은 없습니다. | 구현됨 |

J3의 예는 [6.5](#every-non-aggregate-value-must-be-a-key-rule-j3)에 있습니다. `where` 안의 `Agg`, J7, J4:

<!-- region: ManualKotlinSelectTest.kt#rules -->
```kotlin
// aggregates belong in having, not in where
val where = assertThrows(LambdaQLException::class.java) { qb.from(Order::class.java).where { Agg.count() > 1 } }
assertEquals("LQL-3005", where.code())
// with distinct, the sort key must be selected
val j7 = assertThrows(LambdaQLException::class.java) {
    qb.from(Order::class.java).orderBy { o -> asc(o.id) }.select { it.price }.distinct().toHql()
}
assertEquals("LQL-3046", j7.code())
// sum over no rows is NULL; the non-null parameter of StrictTotal(val total: Double) cannot take it
assertThrows(IllegalStateException::class.java) {
    qb.from(Order::class.java).where { it.status.toInt() == 9 }.select { StrictTotal(Agg.sum(it.price)) }.toList()
}
```

예제 코드: [`ManualKotlinSelectTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSelectTest.kt)(`rules`).

---

<a id="612-not-in-v01"></a>
## 6.12 v0.1에 없는 것

이 장의 기능에는 다음이 없습니다.

| 항목 | 비고 |
|---|---|
| 집계 결과나 select 별칭으로 정렬(예: `orderBy { s -> desc(s.total) }`), 그룹 쿼리의 `orderBy` | |
| 사용자 집계 함수와 윈도 함수 | |
| insert의 조인, 엔티티 11개 이상, 서브쿼리 안의 엔티티 3개 이상 | |
| update, delete의 조인 | 추가하지 않기로 함. 연관 경로나 `sub` 사용 |
| DML 안의 파생 테이블과 CTE | |

이 장의 오류는 코드가 붙은 `TranslationException`입니다. 잘못된 그룹 키는 `LQL-3035`, `where` 안의 `Agg`는 `LQL-3005`, `distinct`에서 select하지 않은 정렬 키는 `LQL-3046`, 파생 테이블이나 CTE의 행 타입은 `LQL-3064`입니다. [11. 오류와 언어](../11-errors-and-languages.md)를 참고하십시오.

---

[Kotlin 트랙](README.md) | 이전: [5. 서브쿼리](05-subquery.md) | 다음: [7. 수정, 삽입, 삭제](07-dml.md) | [이 페이지의 Java 버전](../java/06-select-group.md)
