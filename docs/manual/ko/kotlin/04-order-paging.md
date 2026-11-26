<a id="4-sorting-and-paging-kotlin"></a>
# 4. 정렬과 페이징 (Kotlin)

[Kotlin 트랙](README.md) | [매뉴얼 홈](../README.md) | 이전: [3. 조건(`where`)](03-where.md) | 다음: [5. 서브쿼리](05-subquery.md) | [이 페이지의 Java 버전](../java/04-order-paging.md)

공통 페이지: [1. 개요](../01-overview.md) · [11. 오류와 언어](../11-errors-and-languages.md)

---

<a id="41-one-lambda-for-the-whole-sort"></a>
## 4.1 람다 표현식으로 정렬 표현 

정렬은 **람다 표현식 하나**로 작성합니다. 람다 표현식은 엔티티를 받고, 클래스 `By`의 정렬 함수로 키와 방향을 기술합니다.
`desc(...)`, `asc(...)`를 이어 붙여 키를 추가합니다.

<!-- region: KotlinQueryTest.kt#sort -->
```kotlin
import org.lambdaql.query.By.desc

val q = qb.from(Order::class.java)
    .where { it.isActive }
    .orderBy { o -> desc(o.price).asc(o.customer?.name) }
    .limit(3)
assertEquals("select e0 from Order e0 left join e0.customer j1 where e0.active = true"
    + " order by e0.price desc, j1.name asc nulls first, e0.id asc", q.toHql().hql())
```

예제는 Java로 작성한 예제 엔티티(`Order`, `Customer`, `Item`, `OrderLine`)를 사용합니다. `price`는 기본형 `double`이므로 `nulls` 부분이 없고, `active`의 getter는 `isActive()`입니다(Kotlin `it.isActive`).
`limit`을 쓰면 lambdaQL이 마지막 정렬 키로 `e0.id asc`를 덧붙입니다([동순위 처리](#tie-breaker-automatic-id) 참고).

- 정렬 함수는 `By`의 static 메서드입니다: `asc(key)`, `desc(key)`, `asc(key, Nulls.FIRST)`, `desc(key, Nulls.LAST)`, `sort(key, Direction)`.
- lambdaQL은 람다 표현식을 호출하지 않고 바이트 opcode를 분석합니다. 정렬 함수는 순서를 기술할 뿐입니다. 정렬 람다 표현식 밖에서 호출하면 `IllegalStateException`(`LQL-4046`)이 발생합니다.

예제 코드: [`KotlinQueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)(`sort`).

<a id="42-the-short-forms-one-key"></a>
## 4.2 짧은 형태(키 하나)

키가 하나뿐이면 정렬 함수 없이 키만 반환해도 됩니다.

| Kotlin | HQL |
|---|---|
| `orderBy { it.price }` | `order by e0.price asc` |
| `orderBy({ it.updateAt }, Direction.DESC)` | `order by e0.updateAt desc nulls last` |

<!-- region: ManualKotlinSortTest.kt#short-form -->
```kotlin
// one key: the key alone, ascending unless a Direction is given
val byPrice = qb.from(Order::class.java).orderBy { it.price }
assertEquals("select e0 from Order e0 order by e0.price asc", byPrice.toHql().hql())
val newest = qb.from(Order::class.java).orderBy({ it.updateAt }, Direction.DESC)
assertEquals("select e0 from Order e0 order by e0.updateAt desc nulls last", newest.toHql().hql())
```

`Direction`이 없으면 짧은 형태는 오름차순(`ASC`)으로 정렬합니다.
`Direction` 인자는 키 하나에만 씁니다. `orderBy({ o -> desc(o.price) }, Direction.ASC)`처럼 정렬 함수와 `Direction` 인자를 함께 쓰면 `LQL-3069`로 거부됩니다. 방향을 인자로 받아야 한다면 `orderBy { o -> sort(o.price, direction) }`를 쓰십시오.

예제 코드: [`ManualKotlinSortTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSortTest.kt)(`short-form`).

## 4.3 `Direction`

`Direction`에는 값이 여섯 개 있습니다.

| 값 | 뜻 | NULL 위치 |
|---|---|---|
| `ASC` | 오름차순 | NULL이 **처음**(기본 정책) |
| `DESC` | 내림차순 | NULL이 **마지막**(기본 정책) |
| `ASC_NULLS_FIRST` | 오름차순 | NULL이 처음. `ASC`와 같음 |
| `ASC_NULLS_LAST` | 오름차순 | NULL이 마지막 |
| `DESC_NULLS_FIRST` | 내림차순 | NULL이 처음 |
| `DESC_NULLS_LAST` | 내림차순 | NULL이 마지막. `DESC`와 같음 |

`Direction`에는 도우미 메서드도 두 개 있습니다.

- `Direction.parse(text)`는 요청 파라미터 같은 텍스트에서 방향을 읽습니다. 대소문자는 상관없고 `-`, `_`, 공백은 같게 취급합니다: `Direction.parse("desc-nulls-last")`는 `DESC_NULLS_LAST`입니다. 알 수 없는 텍스트는 `IllegalArgumentException`을 던집니다.
- `reversed()`는 반대 방향을 돌려줍니다. NULL 위치도 함께 뒤집히므로 전체 순서가 거꾸로 됩니다: `Direction.ASC.reversed()`는 `DESC`이고, `Direction.ASC_NULLS_LAST.reversed()`는 `DESC_NULLS_FIRST`입니다.

**NULL 위치를 고르려면** 두 번째 인자로 enum `Nulls`를 쓰십시오: `asc(key, Nulls.LAST)`, `desc(key, Nulls.FIRST)`.
`Nulls.FIRST`는 "NULL이 처음", `Nulls.LAST`는 "NULL이 마지막"이라는 뜻입니다.
두 번째 인자가 없으면 `asc(key)`와 `desc(key)`는 기본 NULL 정책을 사용합니다([4.4](#44-null-ordering-is-the-same-on-every-database) 참고).

| 작성한 것 | 같은 의미 |
|---|---|
| `asc(key)` | `ASC`(NULL이 처음, 기본 정책) |
| `desc(key)` | `DESC`(NULL이 마지막, 기본 정책) |
| `asc(key, Nulls.FIRST)` | `ASC_NULLS_FIRST` |
| `asc(key, Nulls.LAST)` | `ASC_NULLS_LAST` |
| `desc(key, Nulls.FIRST)` | `DESC_NULLS_FIRST` |
| `desc(key, Nulls.LAST)` | `DESC_NULLS_LAST` |

**방향이 변수에서 올 때는** 캡처한 `Direction`과 함께 `sort(key, direction)`을 사용하십시오.

<!-- region: ManualKotlinSortTest.kt#nulls-and-direction -->
```kotlin
// the second argument: true = NULL first, false = NULL last
val nullsLast = qb.from(Order::class.java).orderBy { o -> asc(o.description, Nulls.LAST).asc(o.id) }
assertEquals("select e0 from Order e0 order by e0.description asc nulls last, e0.id asc", nullsLast.toHql().hql())
// a direction from a variable: choose it outside the lambda
val dir = if (newestFirst) Direction.DESC else Direction.ASC
val byDate = qb.from(Order::class.java).orderBy { o -> sort(o.updateAt, dir).asc(o.id) }
assertEquals("select e0 from Order e0 order by e0.updateAt desc nulls last, e0.id asc", byDate.toHql().hql())
// the short form with a NULL position
val byName = qb.from(Order::class.java).orderBy({ it.customer?.name }, Direction.DESC_NULLS_LAST)
assertEquals("select e0 from Order e0 left join e0.customer j1 order by j1.name desc nulls last", byName.toHql().hql())
```

람다 표현식은 한 번만 분석합니다. `dir`은 쿼리를 실행할 때마다 읽습니다. 같은 람다 표현식에 다른 방향을 넘기면 HQL과 결과 행이 그 방향을 따릅니다. 방향이 `null`이면 쿼리를 실행할 때 예외가 발생합니다.

`dir`은 람다 표현식 **밖에서** 고르십시오. 정렬 람다 표현식 안의 `if`는 거부됩니다([4.6](#46-rules-for-the-lambda-body) 참고).

예제 코드: [`ManualKotlinSortTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSortTest.kt)(`nulls-and-direction`).

<a id="44-null-ordering-is-the-same-on-every-database"></a>
## 4.4 NULL 정렬은 모든 데이터베이스에서 같음

NULL의 위치는 데이터베이스마다 다릅니다.

| | H2 | MariaDB | Oracle |
|---|---|---|---|
| `ASC` 기본값 | NULL이 처음 | NULL이 처음 | NULL이 **마지막** |
| `DESC` 기본값 | NULL이 마지막 | NULL이 마지막 | NULL이 **처음** |
| SQL의 `NULLS FIRST/LAST` | 지원 | **미지원(문법 오류)** | 지원 |

lambdaQL은 정책 하나로 이를 해결합니다: **NULL은 가장 작은 값입니다**.

- `ASC` = `ASC_NULLS_FIRST`
- `DESC` = `DESC_NULLS_LAST`

이는 Kotlin 비교자 `nullsFirst(naturalOrder())`와 같습니다.
이 정책을 쓰면 세 데이터베이스 모두 같은 순서를 반환합니다(숫자와 날짜의 경우).

**lambdaQL이 쓰는 방법.** NULL이 될 수 있는 키에는 lambdaQL이 항상 HQL에 NULL 위치를 씁니다. 예: `e0.description asc nulls first`.
NULL이 될 수 없는 키(`active: Boolean`이나 `qty: Int` 같은 JVM 기본형, 또는 `@Id`)에는 `nulls` 부분이 없습니다.
lambdaQL은 Kotlin 타입이 아니라 JPA 메타데이터를 봅니다. 그래서 `@Id`인 `id`(Kotlin에서 `Long?`)에는 `nulls` 부분이 없고, `description`(`String?`)에는 붙습니다([4.10](#410-reusable-and-external-sorts) 참고).
연관 뒤의 컬럼은 연관이 없으면 NULL이 되므로(고객이 없는 주문이 있을 수 있음) 붙습니다. 경로의 모든 연관 단계가 필수(`optional = false`)이고 값이 NULL이 될 수 없으면 붙지 않습니다.

그다음 Hibernate가 데이터베이스별 SQL을 씁니다. 데이터베이스 기본값과 같으면 이 절을 뺍니다.
MariaDB에는 `NULLS` 구문이 없으므로, 기본값이 아닌 위치는 Hibernate가 `CASE` 식으로 씁니다.

만들어지는 SQL:

| 작성한 것 | H2 | MariaDB | Oracle |
|---|---|---|---|
| `asc(key)`, `desc(key)`(`ASC`, `DESC`) | 추가 절 없음(이미 기본값) | 추가 절 없음 | `nulls first` / `nulls last` 추가 |
| DB 기본값과 같은 NULL 위치(`asc(key, nulls)`, `desc(key, nulls)`, `*_NULLS_*`) | 추가 절 없음 | 추가 절 없음 | 추가 절 없음 |
| DB 기본값이 **아닌** NULL 위치 | `nulls first/last` | Hibernate가 `case when x is null ...`을 씀 | `nulls first/last` |
| NULL이 될 수 없는 키(방향과 관계없음) | 추가 절 없음 | 추가 절 없음 | 추가 절 없음 |

> [!TIP]
> MariaDB에서 `asc(key, Nulls.LAST)`(`ASC_NULLS_LAST`)와 `desc(key, Nulls.FIRST)`(`DESC_NULLS_FIRST`)는 `CASE` 식이 됩니다. 데이터베이스는 이 정렬에 인덱스를 사용할 수 없습니다. 가능하면 `asc(key)` / `desc(key)`를 사용하십시오.

<a id="45-what-a-sort-key-can-be"></a>
## 4.5 정렬 키가 될 수 있는 것

| 키 | 허용 | 참고 |
|---|---|---|
| 컬럼: `o.price` | 예 | nullable 키(`Double?`, `String?`)도 괜찮음 |
| to-one 경로: `o.customer?.name`, `o.customer!!.name` | 예 | `left join`(필수 연관은 inner join), 아래 참고 |
| `@Lob` 컬럼 | 예 | Oracle은 거부함. `DEBUG` 수준 `LQL-5010`, 아래 참고 |
| `@Enumerated(EnumType.STRING)` enum 컬럼 | 예 | 이름 순으로 정렬됨(선언 순서가 아님). `DEBUG` 수준 `LQL-5011`, 아래 참고 |
| 산술: `o.price * 2`, `+`, `-` | 예 | `(e0.price * 2.0)` |
| 확대 변환: `o.status.toLong()` | 예 | 변환은 HQL에 나타나지 않음 |
| boolean이 아닌 값의 엘비스: `o.description ?: ""` | 예 | `coalesce(e0.description, '') asc nulls first` |
| 엔티티를 쓰지 않는 값: `asc(1)`, 캡처한 값 | **거부됨** | [4.6](#46-rules-for-the-lambda-body) 참고 |
| 키 값 안의 `if`: `asc(if (o.isActive) 0 else 1)` | 예 | `case when`([3.5.1](03-where.md#351-values-chosen-by-a-condition)) |
| 정렬 함수를 고르는 `if` | **거부됨**(`LQL-2023`) | [4.6](#46-rules-for-the-lambda-body) 참고 |

<!-- region: ManualKotlinSortTest.kt#sort-keys -->
```kotlin
// arithmetic, a widening conversion and the Elvis operator of a value
assertEquals("select e0 from Order e0 order by (e0.price * 2.0) asc",
    qb.from(Order::class.java).orderBy { o -> asc(o.price * 2) }.toHql().hql())
assertEquals("select e0 from Order e0 order by e0.status asc",
    qb.from(Order::class.java).orderBy { o -> asc(o.status.toLong()) }.toHql().hql())
val elvis = qb.from(Order::class.java).orderBy { o -> asc(o.description ?: "").asc(o.id) }
assertEquals("select e0 from Order e0 order by coalesce(e0.description, '') asc nulls first, e0.id asc", elvis.toHql().hql())
```

**연관 경로는 `left join`을 사용합니다.** HQL에서 단순 경로 `order by o.customer.name`은 세 데이터베이스 모두에서 inner join이 됩니다. 고객이 없는 주문은 사라집니다.
lambdaQL은 대신 `left join`을 추가합니다. 고객이 없는 주문도 결과에 남습니다. 그런 주문의 `j1.name`은 NULL이므로 `asc`에서 처음에 옵니다.

<!-- region: ManualKotlinSortTest.kt#sort-path -->
```kotlin
// a path used only for sorting is a left join: the orders without a customer stay
val q = qb.from(Order::class.java).orderBy { o -> asc(o.customer?.name).asc(o.id) }
assertEquals("select e0 from Order e0 left join e0.customer j1 order by j1.name asc nulls first, e0.id asc", q.toHql().hql())
```

**데이터베이스별 SQL**

<!-- sql: sort-path -->
```sql
-- H2, MariaDB
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
order by c1_0.name,o1_0.id

-- Oracle
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
order by c1_0.name asc nulls first,o1_0.id
```

SQL에서 NULL 정책을 볼 수 있습니다. H2와 MariaDB는 자신의 기본값과 같으면 절을 생략합니다. Oracle은 자신의 기본값이 반대이므로 `asc nulls first` / `desc nulls last`를 씁니다. MariaDB에는 `NULLS` 문법이 없으므로, 기본값이 아닌 위치는 먼저 `case when … is null then … end`로 정렬합니다.

**필수 연관은 inner join을 사용합니다.** `optional = false`로 매핑한 연관(예: `@ManyToOne(optional = false)`)은 대상이 항상 있으므로 inner join으로 행을 잃지 않습니다.
그래서 정렬이나 select에만 쓴 경로라도 이런 연관은 `join`을 사용합니다. 그 밖의 연관은 위처럼 `left join`을 사용합니다.
경로의 모든 연관 단계가 필수이고 값이 NULL이 될 수 없으면 정렬에 `nulls first/last`도 붙지 않습니다.

예제 엔티티 `OrderLine`의 `order`는 `@ManyToOne(optional = false)`입니다.

<!-- region: ManualKotlinSortTest.kt#required-association -->
```kotlin
// order is optional = false: a sort or select path through it cannot lose rows, so it is an inner join
val sorted = qb.from(OrderLine::class.java).orderBy { l -> asc(l.order.price) }
assertEquals("select e0 from OrderLine e0 join e0.order j1 order by j1.price asc", sorted.toHql().hql())
val prices = qb.from(OrderLine::class.java).select { it.order.price }
assertEquals("select j1.price from OrderLine e0 join e0.order j1", prices.toHql().hql())
```

**`@Lob` 컬럼의 정렬.** `@Lob` 컬럼을 `orderBy`, `distinct`, `groupBy`에 쓰면 H2와 MariaDB는 실행하지만 Oracle은 거부합니다(ORA-22848, ORA-22849, 실측). `=` 비교는 세 데이터베이스 모두에서 동작합니다.
lambdaQL은 쿼리를 바꾸지 않고, 컬럼과 사용처마다 한 번 `LQL-5010`을 `DEBUG` 수준으로 기록합니다.

아래 세 쿼리는 각각 `LQL-5010`을 한 번 기록합니다.

<!-- region: ManualKotlinSortTest.kt#lob -->
```kotlin
// memo is @Lob: H2 and MariaDB sort it, group it and make it distinct, Oracle rejects it (ORA-22848, ORA-22849).
// lambdaQL keeps the query and logs LQL-5010 at DEBUG
qb.from(OrderLine::class.java).orderBy { l -> asc(l.memo) }.toHql()
qb.from(OrderLine::class.java).groupBy { it.memo }.select { it.memo }.toHql()
qb.from(OrderLine::class.java).select { it.memo }.distinct().toHql()
```

**`EnumType.STRING` enum의 정렬.** `@Enumerated(EnumType.STRING)`으로 매핑한 enum 컬럼은 데이터베이스가 상수 **이름**의 문자열 순서로 정렬합니다. Java의 enum 비교는 선언 순서를 따르므로 결과 순서가 다를 수 있습니다.
lambdaQL은 쿼리를 바꾸지 않고, 컬럼마다 한 번 `LQL-5011`을 `DEBUG` 수준으로 기록합니다. `ORDINAL`(또는 `@Enumerated` 없음)은 선언 순서로 정렬하므로 기록하지 않습니다.

아래 쿼리는 `LQL-5011`을 한 번 기록합니다.

<!-- region: ManualKotlinSortTest.kt#enum-string-sort -->
```kotlin
// kind is @Enumerated(EnumType.STRING): the database sorts by name, Kotlin by declaration order (LQL-5011, DEBUG)
val q = qb.from(Order::class.java).orderBy { o -> asc(o.kind) }
assertEquals("select e0 from Order e0 order by e0.kind asc nulls first", q.toHql().hql())
```

별칭은 `where`와 함께 사용합니다. `where`가 같은 경로를 이미 조인했다면 정렬은 그 조인을 사용합니다.
`where`가 이 경로를 inner join으로 썼다면 고객 없는 주문은 이미 빠져 있습니다. `where`가 `left join`으로 쓴 경로라면 정렬도 그 `left join`을 사용합니다([3.10](03-where.md#310-association-paths) 참고).

예제 코드: [`ManualKotlinSortTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSortTest.kt)(`sort-keys`, `sort-path`, `required-association`, `lob`, `enum-string-sort`).

<a id="46-rules-for-the-lambda-body"></a>
## 4.6 람다 표현식 본문의 규칙

본문은 **키 하나**이거나 **`By` 함수를 곧게 이은 체인**이어야 합니다.

<!-- region: ManualKotlinSortTest.kt#lambda-rules -->
```kotlin
// allowed: a straight chain of by calls
qb.from(Order::class.java).orderBy { o -> desc(o.price).asc(o.id) }
// rejected when orderBy is called: sort functions chosen by a condition (a condition inside one key is allowed)
val branch = assertThrows(LambdaQLException::class.java) {
    qb.from(Order::class.java).orderBy { o -> if (flag) desc(o.price) else asc(o.id) }
}
assertEquals("LQL-2023", branch.code())
// rejected: a key that does not use the entity
val constant = assertThrows(LambdaQLException::class.java) {
    qb.from(Order::class.java).orderBy { _ -> asc(1) }
}
assertEquals("LQL-3031", constant.code())
```

lambdaQL은 `orderBy`를 호출할 때 이를 확인하고 `LambdaQLException`을 던집니다(조건으로 고른 정렬 함수는 `LQL-2023`, 엔티티를 쓰지 않는 키는 `LQL-3031`).
모든 키는 엔티티를 사용해야 합니다. 상수나 캡처한 값만으로 된 키는 거부됩니다.

실행 중에 방향을 바꿔야 한다면 변수로 전달하십시오: `sort(key, dir)`. 키 자체를 바꿔야 한다면 일반 Kotlin 코드의 두 분기에서 각각 쿼리를 만드십시오.

예제 코드: [`ManualKotlinSortTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSortTest.kt)(`lambda-rules`).

<a id="47-order-of-calls"></a>
## 4.7 호출 순서

쿼리는 `from(...).where { }.orderBy { }.limit(...).offset(...)` 순서로 작성합니다.
`orderBy` 없이 `limit`이나 `offset`만 쓰면 `order by`가 붙지 않습니다([4.8](#48-paging) 참고).

<a id="48-paging"></a>
## 4.8 페이징

<!-- region: ManualKotlinSortTest.kt#page -->
```kotlin
val page = qb.from(Order::class.java)
    .where { it.status.toInt() == 1 }
    .orderBy { o -> desc(o.price) }
    .limit(2)      // page size
    .offset(2)     // skip 2 rows (page 2)
// limit and offset go to Hibernate; the id is added as the last key
assertEquals("select e0 from Order e0 where e0.status = 1 order by e0.price desc, e0.id asc", page.toHql().hql())
```

`limit`과 `offset`은 Hibernate에 전달됩니다(`setMaxResults`, `setFirstResult`). 예제 엔티티의 `status`는 `short`이므로 `it.status.toInt() == 1`로 비교합니다. lambdaQL은 이 변환을 무시합니다.

`limit`과 `offset`은 0 이상의 값을 받습니다. 음수를 넘기면 `IllegalArgumentException`이 발생합니다.

lambdaQL은 HQL에 `LIMIT`을 쓰지 않습니다. Hibernate가 데이터베이스마다 알맞은 SQL을 씁니다.

| 데이터베이스 | SQL |
|---|---|
| H2 | `offset ? rows fetch first ? rows only` |
| MariaDB | `limit ?, ?` |
| Oracle | `offset ? rows fetch first ? rows only` |

**`orderBy` 없는 페이징.** `orderBy` 없이 `limit`과 `offset`을 호출하면 `order by`를 붙이지 않습니다. 어떤 행이 어느 페이지에 오는지는 데이터베이스가 정합니다. 페이지를 안정적으로 나누려면 `orderBy`를 쓰십시오.

<!-- region: ManualKotlinSortTest.kt#page-only -->
```kotlin
// paging without orderBy: no order by, so which rows come is up to the database; use orderBy for stable pages
val q = qb.from(Order::class.java).where { it.isActive }.limit(2)
assertEquals("select e0 from Order e0 where e0.active = true", q.toHql().hql())
```

**데이터베이스별 SQL**

<!-- sql: page-only -->
```sql
-- H2, Oracle
select …
from orders o1_0
where o1_0.active=true
order by o1_0.id
fetch first ? rows only

-- MariaDB
select …
from orders o1_0
where o1_0.active=true
order by o1_0.id
limit ?
```

페이징 SQL은 데이터베이스마다 다릅니다. H2와 Oracle은 `fetch first ? rows only`, MariaDB는 `limit ?`를 받습니다. offset이 있으면 H2와 Oracle은 `offset ? rows`를 더하고, MariaDB는 `limit ?,?`를 씁니다.

**첫 행과 전체 수.**

- `orderBy { ... }.first()`는 그 순서의 첫 행을 `Optional`로 반환합니다. 한 행만 읽습니다. `offset`은 유지됩니다: `orderBy { ... }.offset(1).first()`는 두 번째 행을 반환합니다.
- `count()`는 조건에 맞는 모든 행을 셉니다. 페이징 화면에서는 where 단계의 쿼리를 값에 담아 두십시오. 그 값에서 `count()`를 호출해 전체 수를 얻고, 이어서 `orderBy { ... }.limit(...).toList()`를 호출해 페이지를 얻습니다.

<!-- region: ManualKotlinSortTest.kt#first-and-count -->
```kotlin
// keep the where step in a value: count() gives the number of all rows, orderBy ... limit gives one page
val active = qb.from(Order::class.java).where { it.isActive }
val total = active.count()
val page = active.orderBy { o -> desc(o.price) }.limit(2).toList()
// first() reads one row; offset(1).first() is the second row
val second = active.orderBy { o -> desc(o.price) }.offset(1).first()
```

[결과 가져오기](02-getting-started.md#getting-the-result)를 참고하십시오.

<a id="tie-breaker-automatic-id"></a>
### 동순위 처리(자동 `id`)

위 HQL의 끝에는 lambdaQL이 추가한 `e0.id asc`가 있습니다. 이유는 다음과 같습니다.

- 여러 행이 같은 `price`를 가질 수 있습니다. 데이터베이스는 그런 행을 **아무 순서로나** 반환할 수 있습니다. Oracle은 실제로 그렇게 합니다(측정함).
- 그러면 2페이지와 3페이지에 같은 행이 두 번 나오거나 어떤 행이 빠질 수 있습니다.

규칙: **`orderBy`와 페이징을 함께 쓰고 정렬 키에 id가 없으면, lambdaQL은 루트 `@Id asc`를 마지막 키로 추가합니다.**

- 한 번만 추가합니다. 키에 이미 id가 있으면(예: `desc(o.id)`) 추가하지 않습니다.
- 정렬 키에 주 엔티티의 유일하고 NULL이 될 수 없는 컬럼이 있으면 추가하지 않습니다. `nullable = false`인 `@Column(unique = true)`, Hibernate `@NaturalId`, `@Id`가 이에 해당하며, 이 키만으로 모든 행의 순서가 정해지기 때문입니다.
  NULL이 될 수 있는 유일 컬럼은 해당하지 않습니다. NULL은 여러 행에 있을 수 있기 때문입니다.
- `orderBy`가 없거나 `limit`과 `offset`이 없으면 아무것도 추가하지 않습니다.
- 명시적 조인(`join`, `leftJoin`, `crossJoin`)이 있으면 조인한 모든 엔티티의 id도 조인 순서대로 추가합니다([6.8](06-select-group.md#68-explicit-joins) 참고).

예제 엔티티 `OrderLine`의 `code`는 `@Column(unique = true, nullable = false)`입니다.

<!-- region: ManualKotlinSortTest.kt#unique-key -->
```kotlin
// code is @Column(unique = true, nullable = false): it orders every row, so paging adds no id tie-breaker
assertEquals("select e0 from OrderLine e0 order by e0.code asc",
    qb.from(OrderLine::class.java).orderBy { l -> asc(l.code) }.limit(2).toHql().hql())
// a key that is not unique still gets the id tie-breaker
assertEquals("select e0 from OrderLine e0 join e0.order j1 order by j1.price asc, e0.id asc",
    qb.from(OrderLine::class.java).orderBy { l -> asc(l.order.price) }.limit(2).toHql().hql())
```

예제 코드: [`ManualKotlinSortTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSortTest.kt)(`page`, `page-only`, `first-and-count`, `unique-key`).

<a id="49-strings-and-special-values"></a>
## 4.9 문자열과 특수 값

- 문자열 순서는 데이터베이스 콜레이션을 따릅니다. MariaDB `_ci`는 대소문자를 무시합니다. Oracle은 JVM 로캘을 따르는 `NLS_SORT`를 사용합니다(예: `de_DE`이면 독일어 순서). Oracle에서는 `NLS_SORT=BINARY`를 권장합니다.
- Oracle은 `''`를 NULL로 저장하므로 빈 문자열은 NULL처럼 정렬됩니다.
- `NaN`의 순서는 데이터베이스마다 다릅니다.

<a id="410-reusable-and-external-sorts"></a>
## 4.10 재사용 정렬과 외부 정렬

**`SortSpec`: 보관해 둘 수 있는 정렬.** `SortSpec`은 값에 보관해 두고 여러 쿼리에서 쓰는 정렬입니다.
`SortSpec.of(Order::class.java) { o -> desc(o.id) }`처럼 만듭니다. 람다 표현식은 `orderBy { o -> ... }`의 람다 표현식과 같으므로 같은 규칙이 적용됩니다([4.6](#46-rules-for-the-lambda-body) 참고).
그다음 `orderBy(spec)`에 넘깁니다.

<!-- region: ManualKotlinSortTest.kt#sort-spec -->
```kotlin
// keep it in a value (for example at the top level or in a companion object) and use it in many queries
val byDescription = SortSpec.of(Order::class.java) { o -> desc(o.description).asc(o.id) }
val q = qb.from(Order::class.java).where { it.isActive }.orderBy(byDescription)
assertEquals("select e0 from Order e0 where e0.active = true order by e0.description desc nulls last, e0.id asc",
    q.toHql().hql())
```

- `SortSpec`은 바꿀 수 없습니다. `then()`과 `reversed()`는 새 spec을 반환합니다. 그래서 spec 하나를 여러 쿼리와 스레드가 안전하게 함께 쓸 수 있습니다. 예를 들어 최상위 `val`로 두어도 됩니다.
- lambdaQL은 spec이 `orderBy`에서 쓰일 때 람다 표현식을 검사합니다. 각 람다 표현식은 한 번만 분석됩니다.
- 캡처한 값은 람다 표현식이 만들어진 순간의 값입니다. 최상위 `val`의 spec은 그 값을 계속 유지합니다. 실행 시점에 방향을 바꿔야 한다면 `orderBy { o -> sort(key, dir) }`를 쓰십시오([4.3](#43-direction) 참고).
- `orderBy(spec)` 뒤에도 페이징할 수 있습니다: `limit`, `offset`, `toList()`, `first()`.

**데이터베이스별 SQL**

<!-- sql: sort-spec -->
```sql
-- H2, MariaDB
select …
from orders o1_0
where o1_0.active=true
order by o1_0.description desc,o1_0.id

-- Oracle
select …
from orders o1_0
where o1_0.active=true
order by o1_0.description desc nulls last,o1_0.id
```

SQL은 같은 정렬을 `orderBy { o -> ... }`로 썼을 때와 같습니다. Oracle은 `desc`의 기본값이 반대이므로 `nulls last`를 붙입니다([4.4](#44-null-ordering-is-the-same-on-every-database) 참고).

**`then()`: 기존 키 뒤에 키 추가.** `a.then(b)`는 먼저 `a`의 키로 정렬합니다. 값이 같은 행은 이어서 `b`의 키로 정렬합니다.
`then()`은 다른 `SortSpec`이나 정렬 람다 표현식을 받습니다.

<!-- region: ManualKotlinSortTest.kt#sort-spec-then -->
```kotlin
val byStatus = SortSpec.of(Order::class.java) { o -> asc(o.status) }
val byPrice = SortSpec.of(Order::class.java) { o -> desc(o.price) }
val q = qb.from(Order::class.java).orderBy(byStatus.then(byPrice))
assertEquals("select e0 from Order e0 order by e0.status asc, e0.price desc", q.toHql().hql())
// then() also takes a sort lambda
assertEquals("select e0 from Order e0 order by e0.status asc, e0.id desc",
    qb.from(Order::class.java).orderBy(byStatus.then { o -> desc(o.id) }).toHql().hql())
```

`status`(`short`)와 `price`(`double`)는 NULL이 될 수 없으므로 HQL에 `nulls` 부분이 없습니다.

**`reversed()`: 모든 키를 반대로.** `spec.reversed()`는 모든 키를 뒤집습니다. NULL 위치도 함께 뒤집힙니다: `asc`(NULL 먼저)는 `desc`(NULL 나중)가 됩니다. 그래서 행 순서 전체가 정확히 거꾸로 됩니다.
이것은 `Direction.reversed()`와 같습니다([4.3](#43-direction) 참고). `spec.reversed().reversed()`는 원래 순서로 돌아옵니다.

<!-- region: ManualKotlinSortTest.kt#sort-spec-reversed -->
```kotlin
val spec = SortSpec.of(Order::class.java) { o -> asc(o.description).asc(o.id) }
// every key is reversed, the NULL position too: asc nulls first -> desc nulls last
val q = qb.from(Order::class.java).orderBy(spec.reversed())
assertEquals("select e0 from Order e0 order by e0.description desc nulls last, e0.id desc", q.toHql().hql())
```

`reversed()`의 결과 행은 원래 순서를 정확히 뒤집은 순서입니다.

**데이터베이스별 SQL**

<!-- sql: sort-spec-reversed -->
```sql
-- H2, MariaDB
select …
from orders o1_0
order by o1_0.description desc,o1_0.id desc

-- Oracle
select …
from orders o1_0
order by o1_0.description desc nulls last,o1_0.id desc
```

H2와 MariaDB는 `desc`에서 이미 NULL을 나중에 두므로 추가 구문을 쓰지 않습니다. Oracle은 `nulls last`를 붙입니다.

**`SortSpec.ofPath`: 속성 이름으로 만드는 정렬.** `SortSpec.ofPath(Order::class.java, "customer.name", Direction.DESC)`는 속성 경로로 spec을 만듭니다. 코드 밖에서 문자열로 들어오는 정렬 키에 사용하며, Spring 모듈은 Spring `Sort`를 이 방식으로 변환합니다([12.5](12-spring-data-jpa.md#125-spring-sort-and-pageable) 참고).
경로의 각 부분은 영속 속성이어야 하고, 마지막 부분은 기본 값이어야 합니다. 그 밖의 경로는 오류입니다.
허용하는 이름은 정렬 정책이 정합니다(아래 [정렬 정책](#sort-policy) 참고). 정책도 `@Sortable` 필드도 없으면 영속 속성은 어느 것이든 받고, lambdaQL이 `LQL-5003`을 기록합니다(기본 레벨은 `INFO`).
<!-- region: ManualKotlinSortTest.kt#sort-spec-path -->
```kotlin
// for sorts chosen outside the code (the Spring module uses it); only real properties are accepted
val byName = SortSpec.ofPath(Order::class.java, "customer.name", Direction.DESC)
    .then(SortSpec.ofPath(Order::class.java, "id", Direction.ASC))
val q = qb.from(Order::class.java).orderBy(byName)
assertEquals("select e0 from Order e0 left join e0.customer j1 order by j1.name desc nulls last, e0.id asc", q.toHql().hql())
assertThrows(IllegalArgumentException::class.java) {
    qb.from(Order::class.java).orderBy(SortSpec.ofPath(Order::class.java, "price; drop table x", Direction.ASC))
}
```

<a id="sort-policy"></a>
### 정렬 정책: 허용하는 외부 이름

**구현됨**.

코드 밖에서 들어온 정렬 이름(요청 파라미터, Spring `Sort`나 `Pageable`)은 어느 영속 속성이든 가리킬 수 있습니다.
**정렬 정책**은 허용하는 이름을 정합니다.

- `SortRules<T>`는 엔티티 하나의 허용 이름 목록입니다. 각 열을 람다 표현식으로 지정합니다.
- `SortPolicy`는 엔티티별 규칙과 모드(`SortMode`)를 담습니다. 정책은 `EntityManagerFactory`, 곧 persistence unit마다 하나를 등록합니다. 그 팩토리를 쓰는 모든 `QueryBuilder`와 리포지토리가 이 정책을 사용합니다.
- 엔티티 필드의 `@Sortable`로 코드 없이 규칙을 지정할 수 있습니다(아래 [`@Sortable` 규칙](#rules-from-sortable) 참고).

엔티티의 규칙은 다음 순서로 **한 곳**에서만 가져옵니다: 정책의 `SortRules`(코드나 Spring `SortPolicy` 빈), Spring Boot 프로퍼티 `lambdaql.sort.*`([12.5](12-spring-data-jpa.md#sort-policy-with-spring) 참고), 엔티티의 `@Sortable` 필드.
두 곳의 규칙을 합치지 않습니다.

**검사 대상.** 문자열에서 만든 정렬만 검사합니다: `SortSpec.ofPath`, 그리고 Spring `Sort`나 `Pageable`(Spring 모듈은 `ofPath`를 사용함, [12.5](12-spring-data-jpa.md#sort-policy-with-spring) 참고).
정렬 람다 표현식(`orderBy { o -> ... }`, `SortSpec.of`)는 개발자 코드이므로 검사하지 않습니다.

| 규칙 | 허용하는 외부 이름 |
|---|---|
| `allow { it.price }` | `price`(열의 경로) |
| `allow { it.customer?.name }` | `customer.name`. 연관을 거치는 경로는 이렇게 명시한 경우에만 허용합니다 |
| `alias("newest") { it.updateAt }` | `newest`. 정렬 기준은 `updateAt`입니다. 이때 경로 `updateAt` 자체는 외부 이름이 **아니므로** 클라이언트에 속성 이름이 드러나지 않습니다 |

규칙은 엔티티의 열이어야 합니다.
그 밖의 키(예: `allow { it.price * 2 }`)는 규칙을 처음 사용할 때 `LQL-4041`로 거부합니다.

| `SortMode` | 규칙이 허용하지 않는 이름의 처리 |
|---|---|
| `OFF` | 정렬을 실행합니다. 검사하지 않습니다 |
| `INFO` | 정렬을 실행하고, 엔티티와 이름마다 한 번 `LQL-5003`을 `INFO` 레벨로 기록합니다. **기본값입니다:** 등록한 정책이 없으면 `INFO`를 사용하고, 규칙은 `@Sortable`에서만 가져옵니다 |
| `WARN` | `INFO`와 같으며, 로그 레벨만 `WARN`입니다 |
| `STRICT` | `SortNotAllowedException`(`LQL-4040`)으로 거부합니다. 규칙도 `@Sortable` 필드도 없는 엔티티는 모든 이름을 거부합니다 |

따라서 정책을 등록하기 전에는 `@Sortable`이 허용하지 않는 외부 정렬 이름마다 엔티티별로 `LQL-5003`이 한 번 기록됩니다.
모르는 이름을 거부하려면 `STRICT`로, 검사를 끄려면 `OFF`로 정책을 등록하십시오.

| 메서드 | 기능 |
|---|---|
| `SortPolicy.of(mode, rules...)` | 모드와 엔티티별 규칙으로 정책 생성 |
| `SortPolicy.register(emf, policy)` | persistence unit의 정책 등록. 이전 정책을 대체 |
| `SortPolicy.unregister(emf)` | 정책 제거. 기본값(`INFO`, `@Sortable` 규칙만 사용)으로 복귀 |
| `SortPolicy.find(emf)` | 등록한 정책 반환(`Optional`) |
| `SortPolicy.effective(emf)` | 적용 중인 정책 반환. 등록한 정책이 없으면 기본값 |

Spring Boot에서는 `register`를 호출하는 대신 `SortPolicy` 빈을 정의하십시오([12.5](12-spring-data-jpa.md#sort-policy-with-spring) 참고).

등록한 정책은 그 persistence unit의 모든 쿼리에 계속 적용됩니다. 정책이 더 이상 필요 없으면 `SortPolicy.unregister(emf)`로 제거하십시오.

<!-- region: ManualKotlinSortTest.kt#policy -->
```kotlin
val orderRules = SortRules.of(Order::class.java)
    .allow { it.price }                      // the name "price"
    .allow { it.customer?.name }             // an association path: allowed only when listed
    .alias("newest") { it.updateAt }         // the outside name "newest" sorts by updateAt
SortPolicy.register(emf, SortPolicy.of(SortMode.STRICT, orderRules))

val newest = qb.from(Order::class.java).orderBy(SortSpec.ofPath(Order::class.java, "newest", Direction.DESC))
assertEquals("select e0 from Order e0 order by e0.updateAt desc nulls last", newest.toHql().hql())
// not in the rules, and the path behind an alias: SortNotAllowedException when orderBy is called
for (name in listOf("description", "updateAt")) {
    val e = assertThrows(SortNotAllowedException::class.java) {
        qb.from(Order::class.java).orderBy(SortSpec.ofPath(Order::class.java, name, Direction.ASC))
    }
    assertEquals("LQL-4040", e.code())
}
// a sort lambda is your code: never checked
assertEquals("select e0 from Order e0 order by e0.description asc nulls first",
    qb.from(Order::class.java).orderBy { o -> asc(o.description) }.toHql().hql())
```

<a id="rules-from-sortable"></a>
**`@Sortable` 규칙.** 엔티티의 필드나 getter에 `@org.lambdaql.Sortable`을 붙여도 규칙이 됩니다.

| 애너테이션 | 허용하는 외부 이름 |
|---|---|
| 필드 `name`의 `@Sortable` | `name` |
| 필드 `grade`의 `@Sortable("level")` | `level`. 정렬 기준은 `grade`(별칭) |
| 연관 필드 `customer`의 `@Sortable(path = "name")` | `customer.name` |

- lambdaQL은 `from(...)`에 지정한 엔티티의 애너테이션만 읽습니다. 다른 엔티티의 애너테이션으로는 연관 경로가 허용되지 않으므로, 연관 필드에 `path`를 지정하십시오.
- 애너테이션은 정책에 그 엔티티의 규칙(코드, Spring 빈, Spring Boot 프로퍼티)이 없을 때만 사용합니다.
- 정책을 등록하지 않은 기본 `INFO` 모드에서도 동작합니다.

예제 엔티티 `Customer`(Java 클래스)의 `name`에는 `@Sortable`, `grade`에는 `@Sortable("level")`이 붙어 있습니다. 예제는 `Order`의 규칙만 담은 `STRICT` 정책이 등록된 상태에서 실행합니다.

<!-- region: ManualKotlinSortTest.kt#annotation -->
```kotlin
// Customer has no rules in the policy, so its @Sortable fields are the rules:
//   @Sortable private String name;   @Sortable("level") private Integer grade;
val byLevel = qb.from(Customer::class.java).orderBy(SortSpec.ofPath(Customer::class.java, "level", Direction.DESC))
assertEquals("select e0 from Customer e0 order by e0.grade desc nulls last", byLevel.toHql().hql())
assertThrows(SortNotAllowedException::class.java) {
    qb.from(Customer::class.java).orderBy(SortSpec.ofPath(Customer::class.java, "id", Direction.ASC))
}
```

<a id="rules-as-strings"></a>
**문자열 규칙.** `allowPath(String...)`와 `aliasPath(name, path)`는 속성 경로를 문자열로 받습니다. 설정 파일용이며, [12.5](12-spring-data-jpa.md#sort-policy-with-spring)의 Spring Boot 프로퍼티가 이 형태를 사용합니다.
경로는 규칙을 처음 사용할 때 엔티티와 대조합니다. 코드에서는 람다 표현식 형태 `allow`와 `alias`를 쓰십시오.

<!-- region: ManualKotlinSortTest.kt#string-paths -->
```kotlin
// the string forms used by configuration files (lambdaql.sort.allow / lambdaql.sort.alias)
SortPolicy.register(emf, SortPolicy.of(SortMode.STRICT,
    SortRules.of(Order::class.java).allowPath("price", "customer.name").aliasPath("newest", "updateAt")))
assertEquals("select e0 from Order e0 order by e0.updateAt desc nulls last",
    qb.from(Order::class.java).orderBy(SortSpec.ofPath(Order::class.java, "newest", Direction.DESC)).toHql().hql())
// in STRICT mode a name that is not in the rules is rejected, even when it is not a property
assertThrows(SortNotAllowedException::class.java) {
    qb.from(Order::class.java).orderBy(SortSpec.ofPath(Order::class.java, "noSuchName", Direction.ASC))
}
```

- 검사 시점은 spec을 `orderBy`에 전달할 때이며, 경로를 읽기 전입니다.
  따라서 `STRICT` 모드에서는 규칙에 없는 이름이 속성이 아니어도 `SortNotAllowedException`이 발생합니다.
- 규칙은 엔티티 클래스 하나에 속합니다. 같은 클래스의 `SortRules`를 두 번 전달하면 마지막 것을 사용합니다.
- `SortNotAllowedException`은 `LambdaQLException`입니다([11. 오류와 언어](../11-errors-and-languages.md#exceptions) 참고).
  lambdaQL은 웹 의존성이 없습니다. HTTP 400 응답 예시는 [12.5](12-spring-data-jpa.md#sort-policy-with-spring)를 참고하십시오.
- 요청에서 이름과 방향을 받으면 방향은 `Direction.parse`([4.3](#43-direction) 참고), 이름은 `SortSpec.ofPath`로 읽으십시오.

예제 코드: [`ManualKotlinSortTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSortTest.kt)(`sort-spec`, `sort-spec-then`, `sort-spec-reversed`, `sort-spec-path`, `policy`, `annotation`, `string-paths`).

<a id="411-sorting-inside-subqueries"></a>
## 4.11 서브쿼리 안의 정렬

서브쿼리 안의 정렬은 `limit`과 함께 쓸 때(top-1 또는 top-N)만 유지됩니다.
`limit`이 없으면 결과를 바꾸지 않으므로 lambdaQL은 정렬을 제거하고 경고를 로그에 남깁니다.
[5. 서브쿼리](05-subquery.md#59-sorting-inside-a-subquery)를 참고하십시오.

<a id="412-rules-summary-s1-to-s14"></a>
## 4.12 규칙 요약(S1 ~ S14)

| # | 규칙 | 상태 |
|---|---|---|
| S1 | 정렬은 람다 표현식 하나입니다: 키 하나(`{ it.price }`, 선택적인 `Direction`) 또는 `By`의 함수로 만든 키(`{ o -> desc(...) }`). 조인이면 `{ o, c -> ... }` | 구현됨. 조인 형태 `{ o, c -> }`도 구현됨 |
| S2 | 키는 엔티티의 값을 사용해야 합니다(Kotlin nullable 키 허용). 엔티티 자체는 키가 될 수 없습니다 | 구현됨 |
| S3 | 방향은 `asc` / `desc`나 `sort(key, Direction)`으로 정합니다. `asc(key)`/`desc(key)`(`ASC`/`DESC`)는 NULL 정책을 사용하고, `asc(key, nulls)`, `desc(key, nulls)`, `*_NULLS_*`는 쓴 그대로 사용합니다 | 구현됨 |
| S4 | 본문은 키 하나이거나 `By` 함수를 곧게 이은 체인뿐입니다. 정렬 함수를 고르는 `if`와 `when`, 반복문은 안 됩니다. 키 값 안의 `if`는 `case when`이 됩니다 | 구현됨 |
| S5 | `orderBy`는 쿼리당 한 번. `orderBy` 뒤에 `where` 불가. `limit` 뒤에 `orderBy` 불가 | 구현됨 |
| S6 | 재사용은 `SortSpec`(`of`, `ofPath`, `then`, `reversed`, `orderBy(spec)`)으로. 외부 문자열은 persistence unit의 정렬 정책(`SortRules`, `SortPolicy`, `SortMode`, [4.10](#sort-policy) 참고)이 검사 | 구현됨 |
| S7 | 키: 컬럼, to-one 경로, `+ - *`, 확대 변환. 키 값 안의 `if`는 `case when`이 됩니다. Kotlin `?.`는 경로로 읽습니다 | 구현됨 |
| S8 | `orderBy`에만 쓰인 연관 경로는 `left join`을 사용하므로 연관이 없는 행도 유지됩니다. 필수 연관(`optional = false`)은 행을 잃지 않으므로 inner join입니다(A4). 같은 경로를 `where`에서 쓰면 정렬은 `where`의 조인을 사용합니다 | 구현됨 |
| S9 | `orderBy`와 페이징을 함께 쓰면 키에 id가 없을 때 루트 `@Id asc`를 추가합니다. `orderBy`가 없으면 `order by`를 붙이지 않습니다. 명시적으로 조인한 엔티티의 id도 추가합니다. 키에 유일하고 NULL이 될 수 없는 컬럼(`unique = true`이고 `nullable = false`, `@NaturalId`)이 있으면 추가하지 않습니다(A5) | 구현됨 |
| S10 | 분석 결과는 람다 표현식별로 캐시됩니다. 캡처한 `Direction`은 호출할 때마다 읽습니다 | 구현됨 |
| S11 | Spring `Sort.Order`는 `Direction`에 1:1로 대응합니다(Spring 모듈, [12.5](12-spring-data-jpa.md#125-spring-sort-and-pageable) 참고). 속성 이름은 정렬 정책의 검사를 거치고, 별칭은 그대로 전달합니다 | 구현됨(`ignoreCase`는 지원하지 않음). 정렬 정책과 함께 써도 동작함 |
| S12 | 조인 쿼리도 같은 모양을 사용합니다: `{ o, c -> }`부터 엔티티 10개까지 | 구현됨([6.8](06-select-group.md#68-explicit-joins) 참고) |
| S14 | 서브쿼리 정렬: `limit`이 있으면 유지, 없으면 경고와 함께 제거(5장 참고) | 구현됨 |

---

[Kotlin 트랙](README.md) | 이전: [3. 조건(`where`)](03-where.md) | 다음: [5. 서브쿼리](05-subquery.md) | [이 페이지의 Java 버전](../java/04-order-paging.md)
