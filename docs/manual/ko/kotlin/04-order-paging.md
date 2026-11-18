<a id="4-sorting-and-paging-kotlin"></a>
# 4. 정렬과 페이징 (Kotlin)

> [!NOTE]
> **상태**
> - **핵심 기능 구현됨**, Hibernate 6.6.5로 H2, MariaDB 10.11, Oracle 23.26에서 테스트함: `by.asc`, `by.desc`, `by.sort`를 사용하는 `orderBy { o, by -> ... }`, 키 하나의 형태 `orderBy { it.key }`와 `orderBy({ it.key }, Direction)`, `Direction.parse` / `reversed`, `limit` / `offset`, `orderBy` 뒤의 `first()`, 그리고 재사용 정렬 `SortSpec`(`of`, `ofPath`, `then`, `reversed`, `orderBy(spec)`). Spring `Sort`와 `Pageable`은 Spring Data JPA 모듈에서 동작합니다([12.5](12-spring-data-jpa.md#125-spring-sort-and-pageable)). 테스트는 대부분 **Java** 람다를 사용합니다. `limit`이 있는 Kotlin 정렬도 테스트했습니다([`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt), **(테스트됨)** 표시).
> - **추가로 구현됨(Java 람다로 테스트함):** `join`, `leftJoin`, `crossJoin` 뒤의 조인 형태 `{ o, c, by -> ... }`([6.8 명시적 조인](06-select-group.md#68-explicit-joins) 참고), 외부 정렬의 정렬 정책(`SortRules`, `SortPolicy`, `SortMode`, [4.10](#sort-policy) 참고).
> - **아직 구현되지 않음:** Kotlin 속성 참조(`orderBy(Order::price)`), `SortSpec`의 조인 형태.
> - Kotlin 문법은 컴파일됩니다(프로토타입으로 확인, kotlinc 2.3.21). nullable 키, `?.` 키, 속성 참조는 컴파일되며, 엔티티 키는 컴파일되지 않습니다.
> - 이 장의 다른 HQL은 라이브러리의 규칙을 따르지만, Kotlin 람다로 테스트하지는 않았습니다.

[Kotlin 트랙](README.md) | [매뉴얼 홈](../README.md) | 이전: [3. 조건(`where`)](03-where.md) | 다음: [5. 서브쿼리](05-subquery.md) | [이 페이지의 Java 버전](../java/04-order-paging.md)

공통 페이지: [1. 개요](../01-overview.md) · [11. 오류와 언어](../11-errors-and-languages.md)

---

<a id="41-one-lambda-for-the-whole-sort"></a>
## 4.1 정렬 전체를 람다 하나로

정렬은 **람다 하나**로 작성합니다. 람다는 엔티티와 정렬 빌더 `by`를 받습니다. `by`는 마지막 인자입니다.
`by.asc(...)`와 `by.desc(...)`로 키와 방향을 추가합니다.

```kotlin
qb.from(Order::class.java)
    .where { it.active }
    .orderBy { o, by -> by.desc(o.price).asc(o.customer?.name).asc(o.id) }
    .limit(20).offset(40)
    .toList()
// HQL: select e0 from Order e0 left join e0.customer j1
//      where e0.active = true
//      order by e0.price desc nulls last, j1.name asc nulls first, e0.id asc
```

**SQL 블록 읽는 법.** `…`는 줄여 쓴 select 목록(엔티티의 모든 컬럼)이고, `?`는 바인드 파라미터입니다. 각 줄은 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)에서 나옵니다. 이 테스트는 각 쿼리를 Hibernate 6.6.5로 H2 2.3, MariaDB 10.11, Oracle 23.26에서 실행하며, Hibernate의 SQL이 바뀌면 실패합니다. 이 테스트는 Java 람다를 사용합니다. 의미가 같은 Kotlin 람다는 같은 HQL을 만들므로 SQL도 같습니다.

**데이터베이스별 SQL (이 HQL에 대한 Hibernate의 SQL, Kotlin도 같은 HQL을 만듦)** — 매뉴얼의 `Order`에는 NULL이 될 수 있는 `price`가 있지만 테스트의 `Order`에는 없으므로, `SqlByDatabaseTest`가 이 HQL을 Hibernate에 직접 넘깁니다. `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `sort-overview`:

```sql
-- H2
-- (this HQL is given to Hibernate directly)
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where o1_0.active=true
order by o1_0.price desc,c1_0.name,o1_0.id
offset ? rows
fetch first ? rows only

-- MariaDB
-- (this HQL is given to Hibernate directly)
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where o1_0.active=1
order by o1_0.price desc,c1_0.name,o1_0.id
limit ?,?

-- Oracle
-- (this HQL is given to Hibernate directly)
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where o1_0.active=1
order by o1_0.price desc nulls last,c1_0.name asc nulls first,o1_0.id
offset ? rows
fetch first ? rows only
```

- `by`라는 이름은 이 매뉴얼의 관례일 뿐입니다. 어떤 이름이든 쓸 수 있습니다.
- `by`에는 import가 필요 없습니다. 람다 인자이기 때문입니다.
- 키와 방향을 함께 작성합니다. 그래서 둘의 순서가 어긋날 일이 없습니다.
- lambdaQL은 람다를 읽기만 합니다. 실행하지 않습니다. `by` 메서드는 순서를 설명할 뿐입니다.

**(테스트됨)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt), 영역 `sort`:

```kotlin
val q = qb.from(Order::class.java)
    .where { it.isActive }
    .orderBy { o, by -> by.desc(o.price).asc(o.customer?.name) }
    .limit(3)
assertEquals("select e0 from Order e0 left join e0.customer j1 where e0.active = true"
    + " order by e0.price desc, j1.name asc nulls first, e0.id asc", q.toHql().hql())
```

이 테스트는 Java 테스트 엔티티를 사용합니다. `price`는 기본형 `double`이므로 `nulls` 부분이 없고, `active`의 getter는 `isActive()`입니다(Kotlin `it.isActive`).
테스트는 H2, MariaDB, Oracle에서 쿼리를 실행하고, 결과 행을 Kotlin으로 같은 정렬을 한 결과와 비교합니다.
그 밖의 정렬은 [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java)에서 Java로 테스트합니다. 그 예제는 [이 페이지의 Java 버전](../java/04-order-paging.md)에서 볼 수 있습니다.

<a id="42-the-short-forms-one-key"></a>
## 4.2 짧은 형태(키 하나)

키가 하나뿐이면 `by`를 생략할 수 있습니다.

| Kotlin | HQL |
|---|---|
| `orderBy { it.price }` | `order by e0.price asc nulls first` |
| `orderBy({ it.updateAt }, Direction.DESC)` | `order by e0.updateAt desc nulls last` |
| `orderBy(Order::price)` | `order by e0.price asc nulls first`(아직 구현되지 않음) |

`Direction`이 없으면 짧은 형태는 오름차순(`ASC`)으로 정렬합니다.
lambdaQL은 람다 인자의 개수(하나 또는 둘)로 형태를 구분합니다. Kotlin은 모호함 없이 알맞은 오버로드를 고릅니다.

`Direction`을 쓸 때는 람다가 첫 번째 인자이므로 괄호 안에 넣습니다: `orderBy({ it.updateAt }, Direction.DESC)`.

Kotlin 속성 참조(`Order::price`)는 아직 구현되지 않았습니다. 지금은 람다(`{ it.price }`)를 사용하십시오.

## 4.3 `Direction`

`Direction`에는 값이 여섯 개 있습니다.

| 값 | 뜻 | NULL 위치 |
|---|---|---|
| `ASC` | 오름차순 | NULL이 **처음**(기본 정책) |
| `DESC` | 내림차순 | NULL이 **마지막**(기본 정책) |
| `ASC_NULLS_FIRST` | 오름차순 | NULL이 처음 |
| `ASC_NULLS_LAST` | 오름차순 | NULL이 마지막 |
| `DESC_NULLS_FIRST` | 내림차순 | NULL이 처음 |
| `DESC_NULLS_LAST` | 내림차순 | NULL이 마지막 |

`Direction`에는 도우미 메서드도 두 개 있습니다.

- `Direction.parse(text)`는 요청 파라미터 같은 텍스트에서 방향을 읽습니다. 대소문자는 상관없고 `-`, `_`, 공백은 같게 취급합니다: `Direction.parse("desc-nulls-last")`는 `DESC_NULLS_LAST`입니다. 알 수 없는 텍스트는 `IllegalArgumentException`을 던집니다.
- `reversed()`는 반대 방향을 돌려줍니다. NULL 위치도 함께 뒤집히므로 전체 순서가 거꾸로 됩니다: `Direction.ASC.reversed()`는 `DESC`이고, `Direction.ASC_NULLS_LAST.reversed()`는 `DESC_NULLS_FIRST`입니다.

**NULL 위치를 고르려면** 두 번째 `Boolean` 인자를 쓰십시오: `by.asc(key, nullsFirst)`, `by.desc(key, nullsFirst)`(결정 D35).
`true`는 "NULL이 처음", `false`는 "NULL이 마지막"이라는 뜻입니다.
두 번째 인자가 없으면 `asc(key)`와 `desc(key)`는 기본 NULL 정책을 사용합니다(결정 D16, [4.4](#44-null-ordering-is-the-same-on-every-database) 참고).

| 작성한 것 | 같은 의미 |
|---|---|
| `by.asc(key)` | `ASC`(NULL이 처음, 기본 정책) |
| `by.desc(key)` | `DESC`(NULL이 마지막, 기본 정책) |
| `by.asc(key, true)` | `ASC_NULLS_FIRST` |
| `by.asc(key, false)` | `ASC_NULLS_LAST` |
| `by.desc(key, true)` | `DESC_NULLS_FIRST` |
| `by.desc(key, false)` | `DESC_NULLS_LAST` |

**방향이 변수에서 올 때는** 캡처한 `Direction`과 함께 `by.sort(key, direction)`을 사용하십시오.

```kotlin
.orderBy { o, by -> by.desc(o.description, false).asc(o.id) }   // nulls last
// order by e0.description desc nulls last, e0.id asc

val dir = if (request.newestFirst) Direction.DESC else Direction.ASC
.orderBy { o, by -> by.sort(o.updateAt, dir).asc(o.id) }     // dir은 호출할 때마다 읽음

.orderBy({ it.customer?.name }, Direction.DESC_NULLS_LAST)  // NULL 위치를 지정한 짧은 형태
```

람다는 한 번만 분석합니다. `dir`은 쿼리를 실행할 때마다 읽습니다(규칙 S10). 방향이 `null`이면 쿼리를 실행할 때 예외가 발생합니다.

> [!TIP]
> `false`만 있으면 뜻이 잘 드러나지 않습니다. Kotlin은 Java 메서드에 이름 있는 인자(`nullsFirst = false`)를 쓸 수 없고, `by` 빌더는 Java 클래스입니다.
> 그래서 Kotlin도 Java와 똑같이 `by.desc(x, false)`라고 씁니다. `// nulls last` 같은 짧은 주석을 다는 습관이 좋습니다.

`dir`은 람다 **밖에서** 고르십시오. 정렬 람다 안의 `if`는 거부됩니다([4.6](#46-rules-for-the-lambda-body) 참고).

<a id="44-null-ordering-is-the-same-on-every-database"></a>
## 4.4 NULL 정렬은 모든 데이터베이스에서 같음

NULL의 위치는 데이터베이스마다 다릅니다.

| | H2 | MariaDB | Oracle |
|---|---|---|---|
| `ASC` 기본값 | NULL이 처음 | NULL이 처음 | NULL이 **마지막** |
| `DESC` 기본값 | NULL이 마지막 | NULL이 마지막 | NULL이 **처음** |
| SQL의 `NULLS FIRST/LAST` | 지원 | **미지원(문법 오류)** | 지원 |

lambdaQL은 정책 하나로 이를 해결합니다: **NULL은 가장 작은 값입니다**(결정 D16).

- `ASC` = `ASC_NULLS_FIRST`
- `DESC` = `DESC_NULLS_LAST`

이는 Kotlin 비교자 `nullsFirst(naturalOrder())`와 같습니다.
이 정책을 쓰면 세 데이터베이스 모두 같은 순서를 반환합니다(숫자와 날짜의 경우).

**lambdaQL이 쓰는 방법.** NULL이 될 수 있는 키에는 lambdaQL이 항상 HQL에 NULL 위치를 씁니다. 예: `e0.description asc nulls first`.
NULL이 될 수 없는 키(`active: Boolean`이나 `qty: Int` 같은 JVM 기본형, 또는 `@Id`)에는 `nulls` 부분이 없습니다.
lambdaQL은 Kotlin 타입이 아니라 JPA 메타데이터를 봅니다. 그래서 `@Id`가 붙은 `id: Long?`에는 `nulls` 부분이 없고, `price: Double?`에는 붙습니다.
연관 뒤의 컬럼은 연관이 없으면 NULL이 되므로(고객이 없는 주문이 있을 수 있음) 붙습니다. 경로의 모든 연관 단계가 필수(`optional = false`)이고 값이 NULL이 될 수 없으면 붙지 않습니다.

그다음 Hibernate가 데이터베이스별 SQL을 씁니다. 데이터베이스 기본값과 같으면 이 절을 뺍니다.
MariaDB에는 `NULLS` 구문이 없으므로, 기본값이 아닌 위치는 Hibernate가 `CASE` 식으로 씁니다.

만들어지는 SQL:

| 작성한 것 | H2 | MariaDB | Oracle |
|---|---|---|---|
| `asc(key)`, `desc(key)`(`ASC`, `DESC`) | 추가 절 없음(이미 기본값) | 추가 절 없음 | `nulls first` / `nulls last` 추가 |
| DB 기본값과 같은 NULL 위치(`asc(key, b)`, `desc(key, b)`, `*_NULLS_*`) | 추가 절 없음 | 추가 절 없음 | 추가 절 없음 |
| DB 기본값이 **아닌** NULL 위치 | `nulls first/last` | Hibernate가 `case when x is null ...`을 씀 | `nulls first/last` |
| NULL이 될 수 없는 키(방향과 관계없음) | 추가 절 없음 | 추가 절 없음 | 추가 절 없음 |

> [!TIP]
> MariaDB에서 `by.asc(key, false)`(`ASC_NULLS_LAST`)와 `by.desc(key, true)`(`DESC_NULLS_FIRST`)는 `CASE` 식이 됩니다. 데이터베이스는 이 정렬에 인덱스를 사용할 수 없습니다. 가능하면 `asc(key)` / `desc(key)`를 사용하십시오.

<a id="45-what-a-sort-key-can-be"></a>
## 4.5 정렬 키가 될 수 있는 것

| 키 | 허용 | 참고 |
|---|---|---|
| 컬럼: `o.price` | 예 | nullable 키(`Double?`, `String?`)도 괜찮음 |
| to-one 경로: `o.customer?.name`, `o.customer!!.name` | 예 | `left join`(필수 연관은 inner join), 아래 참고 |
| `@Lob` 컬럼 | 예 | Oracle은 거부함. `DEBUG` 수준 `LQL-5010`, 아래 참고 |
| 산술: `o.price!! * 2`, `+`, `-` | 예 | |
| 확대 변환: `o.status!!.toLong()` | 예 | |
| boolean이 아닌 값의 엘비스: `o.description ?: ""` | 예 | `coalesce(e0.description, '')`. NULL 정책은 적용되지 않음 |
| 엔티티: `by.desc(o.customer)` | **컴파일 오류** | 엔티티는 `Comparable`이 아님 |
| 엔티티를 쓰지 않는 값: `by.asc(1)`, 캡처한 값 | **거부됨** | [4.6](#46-rules-for-the-lambda-body) 참고 |
| 컬럼에 대한 함수: `lower(name)` | 계획됨 | |
| boolean 엘비스: `{ it.flag ?: false }` | **거부됨**(KC21) | `{ it.flag }`와 NULL 위치를 사용 |
| 값 `if` / `when` | **거부됨**(KC6) | |

키는 `Comparable`이어야 합니다. Kotlin nullable 키는 괜찮습니다. 엔티티 키는 Kotlin이 컴파일할 때 거부합니다.

**연관 경로는 `left join`을 사용합니다.** HQL에서 단순 경로 `order by o.customer.name`은 세 데이터베이스 모두에서 inner join이 됩니다. 고객이 없는 주문은 사라집니다.
lambdaQL은 대신 `left join`을 추가합니다. 고객이 없는 주문도 결과에 남습니다. 그런 주문의 `j1.name`은 NULL이므로 `asc`에서 처음에 옵니다.

```kotlin
.orderBy { o, by -> by.asc(o.customer?.name).asc(o.id) }
// HQL: select e0 from Order e0 left join e0.customer j1 order by j1.name asc nulls first, e0.id asc
```

**데이터베이스별 SQL (Java 테스트 실행 결과, Kotlin도 같은 HQL을 만듦)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `sort-path`:

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

**필수 연관은 inner join을 사용합니다.** `optional = false`로 매핑한 연관(예: `@ManyToOne(optional = false)`)은 대상이 항상 있으므로 inner join으로 행을 잃지 않습니다(A4).
그래서 정렬이나 select에만 쓴 경로라도 이런 연관은 `join`을 사용합니다. 그 밖의 연관은 위처럼 `left join`을 사용합니다(규칙 S8, J1).
경로의 모든 연관 단계가 필수이고 값이 NULL이 될 수 없으면 정렬에 `nulls first/last`도 붙지 않습니다.

이에 대한 Kotlin 테스트는 아직 없습니다.
같은 규칙을 Java로 테스트했습니다([`MappingRulesTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/MappingRulesTest.java), 영역 `required-association`, [이 페이지의 Java 버전](../java/04-order-paging.md#45-what-a-sort-key-can-be) 참고).
다음은 설계 예제입니다.

```kotlin
// OrderLine.order is optional = false: a sort or select path through it is an inner join
qb.from(OrderLine::class.java).orderBy { l, by -> by.asc(l.order.price) }
// HQL: select e0 from OrderLine e0 join e0.order j1 order by j1.price asc
qb.from(OrderLine::class.java).select { it.order.price }
// HQL: select j1.price from OrderLine e0 join e0.order j1
```

**`@Lob` 컬럼의 정렬.** `@Lob` 컬럼을 `orderBy`, `distinct`, `groupBy`에 쓰면 H2와 MariaDB는 실행하지만 Oracle은 거부합니다(ORA-22848, ORA-22849, 실측). `=` 비교는 세 데이터베이스 모두에서 동작합니다.
lambdaQL은 쿼리를 바꾸지 않고, 컬럼과 사용처마다 한 번 `LQL-5010`을 `DEBUG` 수준으로 기록합니다(B3).

Java 테스트([`MappingRulesTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/MappingRulesTest.java), 영역 `lob`)가 세 사용처의 기록을 확인합니다. 다음은 설계 예제입니다.

```kotlin
// memo is @Lob: Oracle rejects it in order by, group by and distinct; lambdaQL logs LQL-5010 at DEBUG
qb.from(OrderLine::class.java).orderBy { l, by -> by.asc(l.memo) }
qb.from(OrderLine::class.java).select { it.memo }.distinct()
```

별칭은 `where`와 함께 사용합니다. `where`가 같은 경로를 이미 조인했다면 정렬은 그 조인을 사용합니다.
이 조인은 inner join입니다. `where`의 경로는 항상 inner join이므로 고객 없는 주문은 이미 빠져 있습니다(결정 D36, D48, [3.10](03-where.md#310-association-paths) 참고).

<a id="46-rules-for-the-lambda-body"></a>
## 4.6 람다 본문의 규칙

본문은 **`by` 호출을 곧게 이은 체인**이어야 합니다.

```kotlin
// 허용
.orderBy { o, by -> by.desc(o.price).asc(o.id) }

// 거부됨: 조건에 따라 바뀌는 키
.orderBy { o, by -> if (flag) by.desc(o.price) else by.asc(o.id) }

// 거부됨: 엔티티를 쓰지 않는 키
.orderBy { o, by -> by.asc(1) }

// 거부됨: 반복문, 지역 val/var, 여러 문장
```

lambdaQL은 `orderBy`를 호출할 때 이를 확인하고 `LambdaQLException`을 던집니다(예: 조건은 `LQL-2023`, 엔티티를 쓰지 않는 키는 `LQL-3031`).
모든 키는 엔티티를 사용해야 합니다. 상수나 캡처한 값만으로 된 키는 거부됩니다.

실행 중에 방향을 바꿔야 한다면 변수로 전달하십시오: `by.sort(key, dir)`. 키 자체를 바꿔야 한다면 일반 Kotlin 코드의 두 분기에서 각각 쿼리를 만드십시오.

<a id="47-order-of-calls"></a>
## 4.7 호출 순서

빌더 타입이 호출 순서를 제어합니다. 순서가 틀리면 컴파일되지 않습니다.

| 호출 | 허용 |
|---|---|
| `from(...).where { }.orderBy { }.limit(...).offset(...)` | 예 |
| `from(...).where { }.limit(...).offset(...)`(`orderBy` 없음) | 예. 행은 id로 정렬됩니다([4.8](#48-paging) 참고) |
| `orderBy { }` 두 번 | 아니요(컴파일 오류) |
| `orderBy { }` 뒤의 `where { }` | 아니요(컴파일 오류) |
| `limit(...)` 뒤의 `orderBy { }` | 아니요(컴파일 오류) |

`orderBy`, `limit`, `offset` 뒤의 쿼리 타입은 `OrderedSelectQuery`입니다. 여기에는 `limit`, `offset`, `toHql()`, `toList()`, `toSet()`, `first()`만 있습니다. `one()`, `count()`, `exists()`는 `orderBy`, `limit`, `offset` 전에만 있습니다([결과 가져오기](02-getting-started.md#getting-the-result) 참고).

<a id="48-paging"></a>
## 4.8 페이징

```kotlin
qb.from(Order::class.java)
    .where { it.status == 1 }
    .orderBy { o, by -> by.desc(o.price) }
    .limit(20)      // 페이지 크기
    .offset(40)     // 40행 건너뜀(3페이지)
    .toList()
// HQL: select e0 from Order e0 where e0.status = 1 order by e0.price desc nulls last, e0.id asc
// limit과 offset은 Hibernate에 전달됨(setMaxResults / setFirstResult)
```

**데이터베이스별 SQL (이 HQL에 대한 Hibernate의 SQL, Kotlin도 같은 HQL을 만듦)** — 매뉴얼의 `Order`에는 NULL이 될 수 있는 `price`가 있지만 테스트의 `Order`에는 없으므로, `SqlByDatabaseTest`가 이 HQL을 Hibernate에 직접 넘깁니다. `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `page-design`:

```sql
-- H2
-- (this HQL is given to Hibernate directly)
select …
from orders o1_0
where o1_0.status=1
order by o1_0.price desc,o1_0.id
offset ? rows
fetch first ? rows only

-- MariaDB
-- (this HQL is given to Hibernate directly)
select …
from orders o1_0
where o1_0.status=1
order by o1_0.price desc,o1_0.id
limit ?,?

-- Oracle
-- (this HQL is given to Hibernate directly)
select …
from orders o1_0
where o1_0.status=1
order by o1_0.price desc nulls last,o1_0.id
offset ? rows
fetch first ? rows only
```

`limit`과 `offset`은 0 이상의 값을 받습니다. 음수를 넘기면 `IllegalArgumentException`이 발생합니다.

lambdaQL은 HQL에 `LIMIT`을 쓰지 않습니다. Hibernate가 데이터베이스마다 알맞은 SQL을 씁니다.

| 데이터베이스 | SQL |
|---|---|
| H2 | `offset ? rows fetch first ? rows only` |
| MariaDB | `limit ?, ?` |
| Oracle | `offset ? rows fetch first ? rows only` |

**`orderBy` 없는 페이징.** `orderBy` 없이 `limit`과 `offset`을 호출할 수 있습니다. 이때 lambdaQL은 행을 id로 정렬하므로 페이지가 안정적입니다.

```kotlin
qb.from(Order::class.java)
    .where { it.active }
    .limit(2)
    .toList()
// HQL: select e0 from Order e0 where e0.active = true order by e0.id asc
```

**데이터베이스별 SQL (Java 테스트 실행 결과, Kotlin도 같은 HQL을 만듦)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `page-only`:

```sql
-- H2
select …
from orders o1_0
where o1_0.active=true
order by o1_0.id
fetch first ? rows only

-- MariaDB
select …
from orders o1_0
where o1_0.active=1
order by o1_0.id
limit ?

-- Oracle
select …
from orders o1_0
where o1_0.active=1
order by o1_0.id
fetch first ? rows only
```

페이징 SQL은 데이터베이스마다 다릅니다. H2와 Oracle은 `fetch first ? rows only`, MariaDB는 `limit ?`를 받습니다. offset이 있으면 H2와 Oracle은 `offset ? rows`를 더하고, MariaDB는 `limit ?,?`를 씁니다.

**첫 행과 전체 수.**

- `orderBy { ... }.first()`는 그 순서의 첫 행을 `Optional`로 반환합니다. 한 행만 읽습니다. `offset`은 유지됩니다: `orderBy { ... }.offset(1).first()`는 두 번째 행을 반환합니다.
- `count()`는 where 단계(`orderBy`, `limit`, `offset` 전)에만 있으므로 항상 조건에 맞는 모든 행을 셉니다. 페이징 화면에서는 where 단계의 쿼리를 값에 담아 두십시오. 그 값에서 `count()`를 호출해 전체 수를 얻고, 이어서 `orderBy { ... }.limit(...).toList()`를 호출해 페이지를 얻습니다.

[결과 가져오기](02-getting-started.md#getting-the-result)를 참고하십시오.

<a id="tie-breaker-automatic-id"></a>
### 동순위 처리(자동 `id`)

위 HQL을 보십시오. lambdaQL이 끝에 `e0.id asc`를 추가했습니다. 왜일까요?

- 여러 행이 같은 `price`를 가질 수 있습니다. 데이터베이스는 그런 행을 **아무 순서로나** 반환할 수 있습니다. Oracle은 실제로 그렇게 합니다(측정함).
- 그러면 2페이지와 3페이지에 같은 행이 두 번 나오거나 어떤 행이 빠질 수 있습니다.

규칙: **페이징을 사용하고 정렬 키에 id가 없으면, lambdaQL은 루트 `@Id asc`를 마지막 키로 추가합니다.**

- 한 번만 추가합니다. 키에 이미 id가 있으면(예: `by.desc(o.id)`) 추가하지 않습니다.
- `limit`과 `offset`이 없으면 아무것도 추가하지 않습니다.
- 명시적 조인(`join`, `leftJoin`, `crossJoin`)이 있으면 조인한 모든 엔티티의 id도 조인 순서대로 추가합니다(Java 람다로 테스트함, [6.8](06-select-group.md#68-explicit-joins) 참고).
- 아직 구현되지 않음: 컬렉션 fetch join과 `limit`/`offset`을 함께 쓰면 거부합니다.

<a id="49-strings-and-special-values"></a>
## 4.9 문자열과 특수 값

- 문자열 순서는 데이터베이스 콜레이션을 따릅니다. MariaDB `_ci`는 대소문자를 무시합니다. Oracle은 JVM 로캘을 따르는 `NLS_SORT`를 사용합니다(예: `de_DE`이면 독일어 순서). Oracle에서는 `NLS_SORT=BINARY`를 권장합니다.
- Oracle은 `''`를 NULL로 저장하므로 빈 문자열은 NULL처럼 정렬됩니다.
- `NaN`의 순서는 데이터베이스마다 다릅니다.

<a id="410-reusable-and-external-sorts"></a>
## 4.10 재사용 정렬과 외부 정렬

**`SortSpec`: 보관해 둘 수 있는 정렬.** `SortSpec`은 값에 보관해 두고 여러 쿼리에서 쓰는 정렬입니다.
`SortSpec.of(Order::class.java) { o, by -> ... }`로 만듭니다. 람다는 `orderBy { o, by -> ... }`의 람다와 같으므로 같은 규칙이 적용됩니다([4.6](#46-rules-for-the-lambda-body) 참고).
그다음 `orderBy(spec)`에 넘깁니다.

```kotlin
// for example at the top level or in a companion object
val NEWEST: SortSpec<Order> = SortSpec.of(Order::class.java) { o, by -> by.desc(o.updateAt).asc(o.id) }

qb.from(Order::class.java).where { it.active }.orderBy(NEWEST).limit(20).toList()
// HQL: select e0 from Order e0 where e0.active = true order by e0.updateAt desc nulls last, e0.id asc
```

- `SortSpec`은 바꿀 수 없습니다. `then()`과 `reversed()`는 새 spec을 반환합니다. 그래서 spec 하나를 여러 쿼리와 스레드가 안전하게 함께 쓸 수 있습니다. 예를 들어 최상위 `val`로 두어도 됩니다.
- lambdaQL은 spec이 `orderBy`에서 쓰일 때 람다를 검사합니다. 각 람다는 한 번만 분석됩니다.
- 캡처한 값은 람다가 만들어진 순간의 값입니다. 최상위 `val`의 spec은 그 값을 계속 유지합니다. 실행 시점에 방향을 바꿔야 한다면 `orderBy { o, by -> by.sort(key, dir) }`를 쓰십시오([4.3](#43-direction) 참고).
- `orderBy(spec)` 뒤에는 평소처럼 페이징할 수 있습니다: `limit`, `offset`, `toList()`, `first()`.

`SortSpec`에 대한 Kotlin 테스트는 아직 없습니다. 같은 spec을 Java로 쓴 것은 [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java)에서 테스트합니다(H2, MariaDB, Oracle). 테스트한 예제는 [이 페이지의 Java 버전](../java/04-order-paging.md#410-reusable-and-external-sorts)에서 볼 수 있습니다.

```kotlin
val byDescription = SortSpec.of(Order::class.java) { o, by -> by.desc(o.description).asc(o.id) }
qb.from(Order::class.java).where { it.active }.orderBy(byDescription).toList()
// HQL: select e0 from Order e0 where e0.active = true order by e0.description desc nulls last, e0.id asc
```

**데이터베이스별 SQL (Java 테스트 실행 결과, Kotlin도 같은 HQL을 만듦)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `sort-spec`:

```sql
-- H2
select …
from orders o1_0
where o1_0.active=true
order by o1_0.description desc,o1_0.id

-- MariaDB
select …
from orders o1_0
where o1_0.active=1
order by o1_0.description desc,o1_0.id

-- Oracle
select …
from orders o1_0
where o1_0.active=1
order by o1_0.description desc nulls last,o1_0.id
```

SQL은 같은 정렬을 `orderBy { o, by -> ... }`로 썼을 때와 같습니다. Oracle은 `desc`의 기본값이 반대이므로 `nulls last`를 붙입니다([4.4](#44-null-ordering-is-the-same-on-every-database) 참고).

**`then()`: 기존 키 뒤에 키 추가.** `a.then(b)`는 먼저 `a`의 키로 정렬합니다. 값이 같은 행은 이어서 `b`의 키로 정렬합니다.
`then()`은 다른 `SortSpec`이나 정렬 람다를 받습니다.

```kotlin
val byStatus = SortSpec.of(Order::class.java) { o, by -> by.asc(o.status) }
val byPrice = SortSpec.of(Order::class.java) { o, by -> by.desc(o.price) }
qb.from(Order::class.java).orderBy(byStatus.then(byPrice)).toList()
// HQL: select e0 from Order e0 order by e0.status asc nulls first, e0.price desc nulls last
qb.from(Order::class.java).orderBy(byStatus.then { o, by -> by.desc(o.id) }).toList()
// HQL: select e0 from Order e0 order by e0.status asc nulls first, e0.id desc
```

여기서 `status`와 `price`는 NULL이 될 수 있으므로(`Int?`, `Double?`) HQL에 `nulls` 부분이 있습니다. Java 테스트 엔티티에서는 NULL이 될 수 없으므로, 테스트한 HQL과 SQL(예제 `sort-spec-then`)에는 `nulls` 부분이 없습니다. [이 페이지의 Java 버전](../java/04-order-paging.md#410-reusable-and-external-sorts)을 참고하십시오.

**`reversed()`: 모든 키를 반대로.** `spec.reversed()`는 모든 키를 뒤집습니다. NULL 위치도 함께 뒤집힙니다: `asc`(NULL 먼저)는 `desc`(NULL 나중)가 됩니다. 그래서 행 순서 전체가 정확히 거꾸로 됩니다.
이것은 `Direction.reversed()`와 같습니다([4.3](#43-direction) 참고). `spec.reversed().reversed()`는 원래 순서로 돌아옵니다.

```kotlin
val spec = SortSpec.of(Order::class.java) { o, by -> by.asc(o.description).asc(o.id) }
qb.from(Order::class.java).orderBy(spec.reversed()).toList()
// HQL: select e0 from Order e0 order by e0.description desc nulls last, e0.id desc
```

**데이터베이스별 SQL (Java 테스트 실행 결과, Kotlin도 같은 HQL을 만듦)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `sort-spec-reversed`:

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
이 형태에는 람다가 없으므로 Kotlin에서도 Java와 같게 동작합니다. Java 테스트는 [이 페이지의 Java 버전](../java/04-order-paging.md#410-reusable-and-external-sorts)에 있습니다(영역 `sort-spec-path`).

```kotlin
val byName = SortSpec.ofPath(Order::class.java, "customer.name", Direction.DESC)
    .then(SortSpec.ofPath(Order::class.java, "id", Direction.ASC))
qb.from(Order::class.java).orderBy(byName).toList()
// HQL: select e0 from Order e0 left join e0.customer j1 order by j1.name desc nulls last, e0.id asc
```

<a id="sort-policy"></a>
### 정렬 정책: 허용하는 외부 이름

**구현됨**(결정 D43). H2, MariaDB 10.11, Oracle 23.26에서 Java 람다로 테스트함.

코드 밖에서 들어온 정렬 이름(요청 파라미터, Spring `Sort`나 `Pageable`)은 어느 영속 속성이든 가리킬 수 있습니다.
**정렬 정책**은 허용하는 이름을 정합니다.

- `SortRules<T>`는 엔티티 하나의 허용 이름 목록입니다. 각 열을 람다로 지정하므로 컴파일러가 검사합니다.
- `SortPolicy`는 엔티티별 규칙과 모드(`SortMode`)를 담습니다. 정책은 `EntityManagerFactory`, 곧 persistence unit마다 하나를 등록합니다. 그 팩토리를 쓰는 모든 `QueryBuilder`와 리포지토리가 이 정책을 사용합니다.
- 엔티티 필드의 `@Sortable`로 코드 없이 규칙을 지정할 수 있습니다(아래 [`@Sortable` 규칙](#rules-from-sortable) 참고).

엔티티의 규칙은 다음 순서로 **한 곳**에서만 가져옵니다: 정책의 `SortRules`(코드나 Spring `SortPolicy` 빈), Spring Boot 프로퍼티 `lambdaql.sort.*`([12.5](12-spring-data-jpa.md#sort-policy-with-spring) 참고), 엔티티의 `@Sortable` 필드.
두 곳의 규칙을 합치지 않습니다.

**검사 대상.** 문자열에서 만든 정렬만 검사합니다: `SortSpec.ofPath`, 그리고 Spring `Sort`나 `Pageable`(Spring 모듈은 `ofPath`를 사용함, [12.5](12-spring-data-jpa.md#sort-policy-with-spring) 참고).
정렬 람다(`orderBy { o, by -> ... }`, `SortSpec.of`)는 개발자 코드이므로 검사하지 않습니다.

| 규칙 | 허용하는 외부 이름 |
|---|---|
| `allow { it.price }` | `price`(열의 경로) |
| `allow { it.customer?.name }` | `customer.name`. 연관을 거치는 경로는 이렇게 명시한 경우에만 허용합니다 |
| `alias("newest") { it.updateAt }` | `newest`. 정렬 기준은 `updateAt`입니다. 이때 경로 `updateAt` 자체는 외부 이름이 **아니므로** 클라이언트에 속성 이름이 드러나지 않습니다 |

규칙은 엔티티의 열이어야 합니다.
그 밖의 키는 규칙을 처음 사용할 때 `LQL-4041`로 거부합니다(아직 테스트하지 않음).

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

정렬 정책에 대한 Kotlin 테스트는 아직 없습니다.
같은 규칙을 Java로 쓴 것은 [`SortPolicyTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortPolicyTest.java)에서 테스트합니다(H2, MariaDB, Oracle). 테스트한 예제는 [이 페이지의 Java 버전](../java/04-order-paging.md#sort-policy)을 참고하십시오.
다음은 설계 예제입니다.

```kotlin
val orderRules = SortRules.of(Order::class.java)
    .allow { it.price }                      // the name "price"
    .allow { it.customer?.name }             // an association path: allowed only when listed
    .alias("newest") { it.updateAt }         // the outside name "newest" sorts by updateAt
SortPolicy.register(emf, SortPolicy.of(SortMode.STRICT, orderRules))

qb.from(Order::class.java).orderBy(SortSpec.ofPath(Order::class.java, "newest", Direction.DESC)).toList()
// HQL: select e0 from Order e0 order by e0.updateAt desc nulls last
qb.from(Order::class.java).orderBy(SortSpec.ofPath(Order::class.java, "updateAt", Direction.ASC)).toList()
// SortNotAllowedException (LQL-4040): the path behind an alias is not an outside name
qb.from(Order::class.java).orderBy { o, by -> by.asc(o.description) }.toList()
// a sort lambda is your code: never checked
```

<a id="rules-from-sortable"></a>
**`@Sortable` 규칙.** 엔티티의 필드나 getter에 `@org.lambdaql.Sortable`을 붙여도 규칙이 됩니다.

| 애너테이션 | 허용하는 외부 이름 |
|---|---|
| 속성 `name`의 `@field:Sortable` | `name` |
| 속성 `grade`의 `@field:Sortable("level")` | `level`. 정렬 기준은 `grade`(별칭) |
| 연관 속성 `customer`의 `@field:Sortable(path = "name")` | `customer.name` |

- lambdaQL은 `from(...)`에 지정한 엔티티의 애너테이션만 읽습니다. 다른 엔티티의 애너테이션으로는 연관 경로가 허용되지 않으므로, 연관 필드에 `path`를 지정하십시오.
- 애너테이션은 정책에 그 엔티티의 규칙(코드, Spring 빈, Spring Boot 프로퍼티)이 없을 때만 사용합니다.
- 정책을 등록하지 않은 기본 `INFO` 모드에서도 동작합니다.

Kotlin에서는 애너테이션이 JVM 필드에 붙도록 `@field:Sortable`로 씁니다.
같은 규칙을 Java로 테스트했습니다([`SortPolicyTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortPolicyTest.java), 영역 `annotation`, [이 페이지의 Java 버전](../java/04-order-paging.md#rules-from-sortable) 참고). 다음은 설계 예제입니다.

```kotlin
@Entity
class Customer(
    @Id @GeneratedValue
    var id: Long? = null,
    @field:Sortable                     // the outside name "name"
    var name: String? = null,
    @field:Sortable("level")            // the outside name "level" sorts by grade
    var grade: Int? = null,
)

// Customer has no SortRules in the policy, so its @Sortable fields are the rules
qb.from(Customer::class.java).orderBy(SortSpec.ofPath(Customer::class.java, "level", Direction.DESC)).toList()
// HQL: select e0 from Customer e0 order by e0.grade desc nulls last
```

<a id="rules-as-strings"></a>
**문자열 규칙.** `allowPath(String...)`와 `aliasPath(name, path)`는 속성 경로를 문자열로 받습니다. 설정 파일용이며, [12.5](12-spring-data-jpa.md#sort-policy-with-spring)의 Spring Boot 프로퍼티가 이 형태를 사용합니다.
경로는 규칙을 처음 사용할 때 엔티티와 대조합니다. 코드에서는 컴파일러가 검사하는 람다 형태 `allow`와 `alias`를 쓰십시오.

Java로 테스트했습니다([`SortPolicyTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortPolicyTest.java), 영역 `string-paths`). 람다가 없으므로 Kotlin에서도 같게 동작합니다.

```kotlin
// the string forms used by configuration files (lambdaql.sort.allow / lambdaql.sort.alias)
SortPolicy.register(emf, SortPolicy.of(SortMode.STRICT,
    SortRules.of(Order::class.java).allowPath("price", "customer.name").aliasPath("newest", "updateAt")))
```

- 검사 시점은 spec을 `orderBy`에 전달할 때이며, 경로를 읽기 전입니다.
  따라서 `STRICT` 모드에서는 규칙에 없는 이름이 속성이 아니어도 `SortNotAllowedException`이 발생합니다.
- 규칙은 엔티티 클래스 하나에 속합니다. 같은 클래스의 `SortRules`를 두 번 전달하면 마지막 것을 사용합니다.
- `SortNotAllowedException`은 `LambdaQLException`입니다([11. 오류와 언어](../11-errors-and-languages.md#exceptions) 참고).
  lambdaQL은 웹 의존성이 없습니다. HTTP 400 응답 예시는 [12.5](12-spring-data-jpa.md#sort-policy-with-spring)를 참고하십시오.

**아직 구현되지 않음:**

- 이름과 방향을 함께 담은 문자열 하나(`"price,desc_nulls_last"`, 규칙 S11).
  방향은 `Direction.parse`([4.3](#43-direction) 참고), 이름은 `SortSpec.ofPath`로 읽으십시오.
- **계획됨:** `SortSpec`의 조인 형태, 키셋 페이징. (조인의 람다 형태 `{ o, c, by -> ... }`는 구현됨, [6.8](06-select-group.md#68-explicit-joins) 참고.)

<a id="411-sorting-inside-subqueries"></a>
## 4.11 서브쿼리 안의 정렬

**구현됨**(마일스톤 M8-1, [5. 서브쿼리](05-subquery.md)의 서브쿼리와 함께). MariaDB `lateral` 재작성은 아직 구현되지 않았습니다.

서브쿼리 안의 정렬은 `limit`과 함께 쓸 때(top-1 또는 top-N)만 유지됩니다.
`limit`이 없으면 결과를 바꾸지 않으므로 lambdaQL은 정렬을 제거하고 경고를 로그에 남깁니다.
[5. 서브쿼리](05-subquery.md#59-sorting-inside-a-subquery)를 참고하십시오.

<a id="412-rules-summary-s1-to-s14"></a>
## 4.12 규칙 요약(S1 ~ S14)

| # | 규칙 | 상태 |
|---|---|---|
| S1 | 정렬은 람다 하나입니다: `{ o, by -> by... }`. 키가 하나면 `{ it.key }`와 선택적인 `Direction`. 조인이면 `{ o, c, by -> by... }`(`by`는 항상 마지막) | 구현됨. 조인 형태 `{ o, c, by -> }`도 구현됨(Java 람다로 테스트함) |
| S2 | 키는 `Comparable`입니다(Kotlin nullable 키 허용). 엔티티는 키가 될 수 없습니다(컴파일 오류). 키는 엔티티를 사용해야 합니다 | 구현됨 |
| S3 | 방향은 `asc` / `desc`나 `sort(key, Direction)`으로 정합니다. `asc(key)`/`desc(key)`(`ASC`/`DESC`)는 NULL 정책을 사용하고, `asc(key, nullsFirst)`, `desc(key, nullsFirst)`, `*_NULLS_*`는 쓴 그대로 사용합니다 | 구현됨 |
| S4 | 본문은 `by` 호출을 곧게 이은 체인뿐입니다. `if`, `when`, 반복문은 안 됩니다 | 구현됨 |
| S5 | `orderBy`는 쿼리당 한 번. `orderBy` 뒤에 `where` 불가. `limit` 뒤에 `orderBy` 불가(컴파일 오류) | 구현됨 |
| S6 | 재사용은 `SortSpec`(`of`, `ofPath`, `then`, `reversed`, `orderBy(spec)`)으로. 외부 문자열은 persistence unit의 정렬 정책(`SortRules`, `SortPolicy`, `SortMode`, [4.10](#sort-policy) 참고)이 검사 | `SortSpec`과 정렬 정책 구현됨(Java 람다로 테스트함). `SortSpec`의 조인 형태는 아직 구현되지 않음 |
| S7 | 키: 컬럼, to-one 경로, `+ - *`, 확대 변환. 값 분기는 안 됩니다. Kotlin `?.`는 경로로 읽습니다 | 구현됨(Kotlin 람다로는 테스트하지 않음) |
| S8 | `orderBy`에만 쓰인 연관 경로는 `left join`을 사용하므로 연관이 없는 행도 유지됩니다. 필수 연관(`optional = false`)은 행을 잃지 않으므로 inner join입니다(A4). 같은 경로를 `where`에서 쓰면 정렬은 `where`의 조인을 사용합니다 | 구현됨 |
| S9 | 페이징을 쓰면 키에 id가 없을 때 루트 `@Id asc`를 추가합니다. 컬렉션 fetch join + 페이징은 거부됩니다 | 루트 id와 명시적으로 조인한 엔티티의 id는 구현됨(Java 람다로 테스트함). fetch join 검사는 아직 구현되지 않음 |
| S10 | 분석 결과는 람다별로 캐시됩니다. 캡처한 `Direction`은 호출할 때마다 읽습니다 | 구현됨 |
| S11 | Spring `Sort.Order`는 `Direction`에 1:1로 대응합니다(Spring 모듈, [12.5](12-spring-data-jpa.md#125-spring-sort-and-pageable) 참고). 속성 이름은 정렬 정책의 검사를 거치고, 별칭은 그대로 전달합니다 | Spring `Sort`는 구현되고 Java로 테스트됨(`ignoreCase`는 아직 지원하지 않음). 정렬 정책과 함께 쓰는 경우도 테스트함. `"price,desc_nulls_last"` 같은 문자열 하나의 해석은 아직 구현되지 않음 |
| S12 | 조인 쿼리도 같은 모양을 사용합니다: `{ o, c, by -> }`, `{ o, c, i, by -> }` | 구현됨(Java 람다로 테스트함, [6.8](06-select-group.md#68-explicit-joins) 참고) |
| S13 | 속성 참조는 키 하나의 짧은 형태(`orderBy(Order::price)`)에서만 사용합니다. 컴파일되지만 의미가 틀린 참조는 거부됩니다 | 아직 구현되지 않음(Kotlin 속성 참조) |
| S14 | 서브쿼리 정렬: `limit`이 있으면 유지, 없으면 경고와 함께 제거. 데이터베이스 차이는 lambdaQL이 처리합니다(5장 참고) | 구현됨(테스트함). MariaDB `lateral` 재작성은 아직 구현되지 않음 |

---

[Kotlin 트랙](README.md) | 이전: [3. 조건(`where`)](03-where.md) | 다음: [5. 서브쿼리](05-subquery.md) | [이 페이지의 Java 버전](../java/04-order-paging.md)
