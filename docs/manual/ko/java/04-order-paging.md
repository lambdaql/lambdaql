<a id="4-sorting-and-paging-java"></a>
# 4. 정렬과 페이징 (Java)

> [!NOTE]
> **상태**
> - **핵심 기능 구현됨, 테스트함**(Hibernate 6.6.5로 H2, MariaDB 10.11, Oracle 23.26): `by.asc`, `by.desc`, `by.sort`를 사용하는 `orderBy((o, by) -> ...)`, 키 하나의 형태 `orderBy(o -> key)`와 `orderBy(key, Direction)`(`Order::getPrice` 같은 getter 메서드 참조도 가능), `Direction.parse` / `reversed`, 그리고 `limit` / `offset`.
> - **아직 구현되지 않음:** `SortSpec`, `SortWhitelist`, 조인 형태 `(o, c, by)`, 서브쿼리 안의 정렬, Spring `Sort` / `Pageable` 모듈.
> - **(테스트됨)** 표시가 있는 예제는 테스트 코드에서 그대로 가져온 것입니다. 이 장의 나머지 HQL도 같은 규칙을 따르지만 테스트하지는 않았습니다.

[Java 트랙](README.md) | [매뉴얼 홈](../README.md) | 이전: [3. 조건(`where`)](03-where.md) | 다음: [5. 서브쿼리](05-subquery.md) | [이 페이지의 Kotlin 버전](../kotlin/04-order-paging.md)

공통 페이지: [1. 개요](../01-overview.md) · [11. 오류와 언어](../11-errors-and-languages.md)

---

<a id="41-one-lambda-for-the-whole-sort"></a>
## 4.1 정렬 전체를 람다 하나로

정렬은 **람다 하나**로 작성합니다. 람다는 엔티티와, 마지막 인자로 정렬 빌더 `by`를 받습니다.
`by.asc(...)`와 `by.desc(...)`로 키와 방향을 추가합니다.

```java
qb.from(Order.class)
  .where(o -> o.isActive())
  .orderBy((o, by) -> by.desc(o.getPrice()).asc(o.getCustomer().getName()).asc(o.getId()))
  .limit(20).offset(40)
  .toList();
// HQL: select e0 from Order e0 left join e0.customer j1
//      where e0.active = true
//      order by e0.price desc nulls last, j1.name asc nulls first, e0.id asc
```

**SQL 블록 읽는 법.** `…`는 줄여 쓴 select 목록(엔티티의 모든 컬럼)이고, `?`는 바인드 파라미터입니다. 각 줄은 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)에서 나옵니다. 이 테스트는 각 쿼리를 Hibernate 6.6.5로 H2 2.3, MariaDB 10.11, Oracle 23.26에서 실행하며, Hibernate의 SQL이 바뀌면 실패합니다.

**데이터베이스별 SQL (이 HQL에 대한 Hibernate의 SQL)** — 매뉴얼의 `Order`에는 NULL이 될 수 있는 `Double price`가 있지만 테스트의 `Order`에는 없으므로, `SqlByDatabaseTest`가 이 HQL을 Hibernate에 직접 넘깁니다. `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `sort-overview`:

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

- `by`라는 이름은 이 매뉴얼의 관례일 뿐입니다. 어떤 이름이든 사용할 수 있습니다.
- static import가 필요 없습니다. `by`는 람다 인자입니다.
- 키와 방향을 함께 작성합니다. 둘의 순서가 어긋날 일이 없습니다.
- lambdaQL은 람다를 읽기만 합니다. 실행하지 않습니다. `by` 메서드는 순서를 설명할 뿐입니다.

**(테스트됨)** 표시가 있는 예제는 [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java)에서 그대로 가져온 것입니다.
이 테스트는 HQL을 확인하고, 쿼리를 실행하고, 같은 정렬을 Java로 적용한 결과와 행 순서를 비교합니다. H2, MariaDB, Oracle에서 통과합니다.
테스트는 [3.3](03-where.md#33-basic-form)의 테스트 엔티티를 사용합니다. `price`는 `double`, `status`는 `short`입니다. 이 키들은 NULL이 될 수 없으므로 HQL에 `nulls` 부분이 없습니다.
나머지 예제에서 `price`는 NULL이 될 수 있는 `Double`이므로([3.2](03-where.md#32-example-entities) 참고) `nulls first`나 `nulls last`가 붙습니다([4.4](#44-null-ordering-is-the-same-on-every-database) 참고).

**(테스트됨)** [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java), 영역 `sort-basic`:

```java
RenderedQuery q = qb.from(Order.class)
        .where(o -> o.isActive())
        .orderBy((o, by) -> by.desc(o.getPrice()).asc(o.getId()))
        .toHql();
// price and id can never be NULL, so there is no "nulls" part
assertEquals("select e0 from Order e0 where e0.active = true order by e0.price desc, e0.id asc", q.hql());
```

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `sort-basic`:

```sql
-- H2
select …
from orders o1_0
where o1_0.active=true
order by o1_0.price desc,o1_0.id

-- MariaDB, Oracle
select …
from orders o1_0
where o1_0.active=1
order by o1_0.price desc,o1_0.id
```

`toHql()`은 쿼리를 실행하지 않고 HQL을 돌려줍니다. `toList()`는 쿼리를 실행합니다.

<a id="42-the-short-forms-one-key"></a>
## 4.2 짧은 형태(키 하나)

키가 하나뿐이라면 `by`를 생략할 수 있습니다.

| Java | HQL |
|---|---|
| `orderBy(o -> o.getPrice())` | `order by e0.price asc nulls first` |
| `orderBy(o -> o.getUpdateAt(), Direction.DESC)` | `order by e0.updateAt desc nulls last` |
| `orderBy(Order::getPrice)` | `order by e0.price asc nulls first` |
| `orderBy(Order::getPrice, Direction.DESC)` | `order by e0.price desc nulls last` |

`Direction`이 없으면 짧은 형태는 오름차순(`ASC`)으로 정렬합니다.
lambdaQL은 람다 인자의 개수(하나 또는 둘)로 형태를 구분합니다. Java 컴파일러는 모호함 없이 올바른 오버로드를 고릅니다.

메서드 참조는 `Order::getPrice`처럼 엔티티의 getter여야 합니다. static 메서드, 인자가 있는 메서드, 객체에 묶인 참조는 거부됩니다(규칙 S13).
`by` 형태(인자 둘)에는 람다가 필요합니다. 여기서는 메서드 참조가 거부됩니다.

**(테스트됨)** [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java), 영역 `sort-key`(테스트 엔티티에서 `price`는 `double`이므로 `nulls` 부분이 없습니다):

```java
assertEquals("select e0 from Order e0 order by e0.price asc",
        qb.from(Order.class).orderBy(o -> o.getPrice()).toHql().hql());
assertEquals("select e0 from Order e0 order by e0.price desc",
        qb.from(Order.class).orderBy(Order::getPrice, Direction.DESC).toHql().hql());
```

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `sort-key`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
order by o1_0.price
```

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `sort-key#2`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
order by o1_0.price desc
```

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
- `reversed()`는 반대 방향을 돌려줍니다. NULL 위치도 함께 뒤집히므로 전체 순서가 거꾸로 됩니다: `ASC.reversed()`는 `DESC`이고, `ASC_NULLS_LAST.reversed()`는 `DESC_NULLS_FIRST`입니다.

**NULL 위치를 고르려면** 두 번째 `boolean` 인자를 쓰십시오: `by.asc(key, nullsFirst)`, `by.desc(key, nullsFirst)`(결정 D35).
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

**(테스트됨)** [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java), 영역 `sort-nulls`(`description`은 NULL이 될 수 있는 `String`이고, `id`는 NULL이 될 수 없습니다):

```java
// default: asc puts NULL first, desc puts NULL last (the same on every database)
assertEquals("select e0 from Order e0 order by e0.description asc nulls first, e0.id asc",
        qb.from(Order.class).orderBy((o, by) -> by.asc(o.getDescription()).asc(o.getId())).toHql().hql());
// the second argument chooses: true = NULL first, false = NULL last
assertEquals("select e0 from Order e0 order by e0.description asc nulls last, e0.id asc",
        qb.from(Order.class).orderBy((o, by) -> by.asc(o.getDescription(), false).asc(o.getId())).toHql().hql());
assertEquals("select e0 from Order e0 order by e0.description desc nulls first, e0.id asc",
        qb.from(Order.class).orderBy((o, by) -> by.desc(o.getDescription(), true).asc(o.getId())).toHql().hql());
```

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `sort-nulls`:

```sql
-- H2, MariaDB
select …
from orders o1_0
order by o1_0.description,o1_0.id

-- Oracle
select …
from orders o1_0
order by o1_0.description asc nulls first,o1_0.id
```

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `sort-nulls#2`:

```sql
-- H2
select …
from orders o1_0
order by o1_0.description asc nulls last,o1_0.id

-- MariaDB
select …
from orders o1_0
order by case when (o1_0.description) is null then 1 else 0 end,o1_0.description,o1_0.id

-- Oracle
select …
from orders o1_0
order by o1_0.description,o1_0.id
```

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `sort-nulls#3`:

```sql
-- H2
select …
from orders o1_0
order by o1_0.description desc nulls first,o1_0.id

-- MariaDB
select …
from orders o1_0
order by case when (o1_0.description) is null then 0 else 1 end,o1_0.description desc,o1_0.id

-- Oracle
select …
from orders o1_0
order by o1_0.description desc,o1_0.id
```

SQL에서 NULL 정책을 볼 수 있습니다. H2와 MariaDB는 자신의 기본값과 같으면 절을 생략합니다. Oracle은 자신의 기본값이 반대이므로 `asc nulls first` / `desc nulls last`를 씁니다. MariaDB에는 `NULLS` 문법이 없으므로, 기본값이 아닌 위치는 먼저 `case when … is null then … end`로 정렬합니다.

**방향이 변수에서 올 때는** 캡처한 `Direction`과 함께 `by.sort(key, direction)`을 사용하십시오.

```java
.orderBy((o, by) -> by.desc(o.getDescription(), false).asc(o.getId()))   // nulls last

Direction dir = request.isNewestFirst() ? Direction.DESC : Direction.ASC;
.orderBy((o, by) -> by.sort(o.getUpdateAt(), dir).asc(o.getId()))   // dir은 호출할 때마다 읽음
```

여기서 삼항 연산자 `request.isNewestFirst() ? ... : ...`는 괜찮습니다. 람다 **밖에서** 실행되기 때문입니다.

**(테스트됨)** [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java), 영역 `sort-dynamic`:

```java
Direction dir = Direction.parse(text);           // for example from a request parameter
OrderedSelectQuery<Order> q = qb.from(Order.class)
        .orderBy((o, by) -> by.sort(o.getDescription(), dir).asc(o.getId()));
```

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `sort-dynamic`:

```sql
-- H2, MariaDB
select …
from orders o1_0
order by o1_0.description desc,o1_0.id

-- Oracle
select …
from orders o1_0
order by o1_0.description desc nulls last,o1_0.id
```

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `sort-dynamic#2`:

```sql
-- H2
select …
from orders o1_0
order by o1_0.description asc nulls last,o1_0.id

-- MariaDB
select …
from orders o1_0
order by case when (o1_0.description) is null then 1 else 0 end,o1_0.description,o1_0.id

-- Oracle
select …
from orders o1_0
order by o1_0.description,o1_0.id
```

테스트는 이 코드를 `text`를 `"desc"`와 `"asc_nulls_last"`로 바꾸어 두 번 실행합니다.
HQL은 `... order by e0.description desc nulls last, e0.id asc`, 그다음 `... order by e0.description asc nulls last, e0.id asc`입니다.
람다는 한 번만 분석합니다. `dir`은 쿼리를 실행할 때마다 읽습니다(규칙 S10). 방향이 `null`이면 쿼리를 실행할 때 예외가 발생합니다.

> [!TIP]
> `false`만 있으면 뜻이 잘 드러나지 않습니다. Java와 Kotlin 모두 `by.desc(x, false)`라고 씁니다. Kotlin은 Java 메서드에 이름 있는 인자(`nullsFirst = false`)를 쓸 수 없기 때문입니다.
> 그래서 `// nulls last` 같은 짧은 주석을 다는 습관이 좋습니다.

<a id="44-null-ordering-is-the-same-on-every-database"></a>
## 4.4 NULL 순서는 모든 데이터베이스에서 같음

데이터베이스마다 NULL의 위치가 다릅니다.

| | H2 | MariaDB | Oracle |
|---|---|---|---|
| `ASC` 기본값 | NULL이 처음 | NULL이 처음 | NULL이 **마지막** |
| `DESC` 기본값 | NULL이 마지막 | NULL이 마지막 | NULL이 **처음** |
| SQL의 `NULLS FIRST/LAST` | 지원 | **미지원(구문 오류)** | 지원 |

lambdaQL은 정책 하나로 이를 해결합니다. **NULL은 가장 작은 값입니다.**

- `ASC` = `ASC_NULLS_FIRST`
- `DESC` = `DESC_NULLS_LAST`

이는 Java의 `Comparator.nullsFirst(Comparator.naturalOrder())`와 같습니다.
이 정책으로 세 데이터베이스 모두 같은 순서를 반환합니다(숫자와 날짜의 경우).

**lambdaQL이 쓰는 방법.** NULL이 될 수 있는 키에는 lambdaQL이 항상 HQL에 NULL 위치를 씁니다. 예: `e0.description asc nulls first`.
NULL이 될 수 없는 키(`double` 같은 기본형 컬럼이나 `@Id`)에는 `nulls` 부분이 없습니다.
연관 뒤의 컬럼은 언제나 NULL이 될 수 있으므로(고객이 없는 주문이 있을 수 있음) 항상 붙습니다.

그다음 Hibernate가 데이터베이스별 SQL을 씁니다. 데이터베이스 기본값과 같으면 이 절을 뺍니다.
MariaDB에는 `NULLS` 구문이 없으므로, 기본값이 아닌 위치는 Hibernate가 `CASE` 식으로 씁니다.

생성되는 SQL:

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
| 컬럼: `o.getPrice()` | 허용 | 테스트함 |
| to-one 경로: `o.getCustomer().getName()` | 허용 | `left join`, 아래 참고(테스트함) |
| 산술: `o.getPrice() * 2`, `+`, `-` | 허용 | 아직 테스트하지 않음 |
| 확대 변환(`int`에서 `long`으로) | 허용 | 아직 테스트하지 않음 |
| 엔티티: `by.desc(o.getCustomer())` | **컴파일 오류** | 엔티티는 `Comparable`이 아님 |
| 엔티티를 쓰지 않는 값: `by.asc(1)`, 캡처한 값 | **거부됨** | [4.6](#46-rules-for-the-lambda-body) 참고(테스트함) |
| 컬럼에 대한 함수: `lower(name)` | 계획됨 | |

키는 `Comparable`이어야 합니다. 박싱 타입(`Double`, `Integer`, `Long`), `String`, `java.time` 타입은 `Comparable`이므로 동작합니다. Java 컴파일러는 엔티티 키를 거부합니다.
엔티티 클래스가 `Comparable`을 구현하더라도 lambdaQL은 람다를 읽을 때 그것을 키로 쓰지 못하게 거부합니다.

**연관 경로는 `left join`을 사용합니다.** HQL의 단순 경로 `order by o.customer.name`은 세 데이터베이스 모두에서 inner join이 됩니다. 고객이 없는 주문은 사라집니다.
lambdaQL은 대신 `left join`을 추가합니다. 고객이 없는 주문도 결과에 남습니다. 그런 주문의 `j1.name`은 NULL이므로 `asc`에서 처음에 옵니다.

**(테스트됨)** [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java), 영역 `sort-path`:

```java
RenderedQuery q = qb.from(Order.class)
        .orderBy((o, by) -> by.asc(o.getCustomer().getName()).asc(o.getId()))
        .toHql();
// a path used only for sorting is a LEFT JOIN: the order without a customer stays
assertEquals("select e0 from Order e0 left join e0.customer j1 order by j1.name asc nulls first, e0.id asc", q.hql());
```

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `sort-path`:

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

별칭은 `where`와 함께 사용합니다. `where`가 같은 경로를 이미 조인했다면 정렬은 그 조인을 사용합니다.
대부분은 inner join입니다. `where` 조건이 이미 고객 없는 주문을 걸러 냈기 때문입니다(결정 D36, [3.10](03-where.md#310-association-paths) 참고).

**(테스트됨)** [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java), 영역 `sort-path-where`:

```java
String name = "Kim";
RenderedQuery q = qb.from(Order.class)
        .where(o -> o.getCustomer().getName().equals(name))
        .orderBy((o, by) -> by.desc(o.getCustomer().getName()).desc(o.getPrice()))
        .toHql();
// the condition already joined the customer, so the sort uses the same inner join
assertEquals("select e0 from Order e0 join e0.customer j1 where j1.name = :p1"
        + " order by j1.name desc nulls last, e0.price desc", q.hql());
```

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `sort-path-where`:

```sql
-- H2, MariaDB
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name=?
order by c1_0.name desc,o1_0.price desc

-- Oracle
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name=?
order by c1_0.name desc nulls last,o1_0.price desc
```

<a id="46-rules-for-the-lambda-body"></a>
## 4.6 람다 본문의 규칙

본문은 **`by` 호출의 단순한 체인**이어야 합니다.

```java
// 허용
.orderBy((o, by) -> by.desc(o.getPrice()).asc(o.getId()))

// 거부됨: 조건에 따른 키
.orderBy((o, by) -> flag ? by.desc(o.getPrice()) : by.asc(o.getId()))

// 거부됨: 반복문, if, 지역 변수
```

lambdaQL은 `orderBy`를 호출할 때 이를 확인하고 `LambdaAnalysisException`을 던집니다.
모든 키는 엔티티를 사용해야 합니다. 상수나 캡처한 값만으로 된 키는 거부됩니다.

**(테스트됨)** [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java), 영역 `sort-rejected`:

```java
// a condition inside the sort lambda (rule S4)
assertThrows(LambdaAnalysisException.class, () -> qb.from(Order.class)
        .orderBy((o, by) -> o.isActive() ? by.asc(o.getPrice()) : by.desc(o.getPrice())));
// a key without the entity
assertThrows(LambdaAnalysisException.class, () -> qb.from(Order.class).orderBy((o, by) -> by.asc(1)));
```

실행 시점에 방향을 바꿔야 한다면 변수로 전달하십시오: `by.sort(key, dir)`. 키 자체를 바꿔야 한다면 일반 Java 코드의 두 분기에서 쿼리를 따로 만드십시오.

<a id="47-order-of-calls"></a>
## 4.7 호출 순서

빌더 타입이 순서를 제어합니다. 순서가 틀리면 컴파일되지 않습니다.

| 호출 | 허용 |
|---|---|
| `from(...).where(...).orderBy(...).limit(...).offset(...)` | 허용 |
| `from(...).where(...).limit(...).offset(...)`(`orderBy` 없음) | 허용. 행은 id로 정렬됩니다([4.8](#48-paging) 참고) |
| `orderBy(...)` 두 번 | 불가(컴파일 오류) |
| `orderBy(...)` 뒤의 `where(...)` | 불가(컴파일 오류) |
| `limit(...)` 뒤의 `orderBy(...)` | 불가(컴파일 오류) |

`orderBy`, `limit`, `offset` 뒤의 쿼리 타입은 `OrderedSelectQuery`입니다. 여기에는 `limit`, `offset`, `toHql()`, `toList()`만 있습니다.

<a id="48-paging"></a>
## 4.8 페이징

```java
qb.from(Order.class)
  .where(o -> o.getStatus() == 1)
  .orderBy((o, by) -> by.desc(o.getPrice()))
  .limit(20)      // 페이지 크기
  .offset(40)     // 40행 건너뜀(3페이지)
  .toList();
// HQL: select e0 from Order e0 where e0.status = 1 order by e0.price desc nulls last, e0.id asc
// limit과 offset은 Hibernate에 전달됨(setMaxResults / setFirstResult)
```

**데이터베이스별 SQL (이 HQL에 대한 Hibernate의 SQL)** — 매뉴얼의 `Order`에는 NULL이 될 수 있는 `Double price`가 있지만 테스트의 `Order`에는 없으므로, `SqlByDatabaseTest`가 이 HQL을 Hibernate에 직접 넘깁니다. `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `page-design`:

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

**(테스트됨)** [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java), 영역 `sort-page`:

```java
OrderedSelectQuery<Order> page = qb.from(Order.class)
        .orderBy((o, by) -> by.asc(o.getStatus()))
        .limit(2)
        .offset(2);
// status has ties, so the id is added as the last key and rows do not move between pages
assertEquals("select e0 from Order e0 order by e0.status asc, e0.id asc", page.toHql().hql());
List<Order> rows = page.toList();
```

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `sort-page`:

```sql
-- H2, Oracle
select …
from orders o1_0
order by o1_0.status,o1_0.id
offset ? rows
fetch first ? rows only

-- MariaDB
select …
from orders o1_0
order by o1_0.status,o1_0.id
limit ?,?
```

페이징 SQL은 데이터베이스마다 다릅니다. H2와 Oracle은 `offset ? rows fetch first ? rows only`, MariaDB는 `limit ?,?`를 받습니다. offset이 없으면 `fetch first ? rows only` 또는 `limit ?`만 씁니다(아래 `page-only` 참고).

`limit`과 `offset`은 0 이상의 값을 받습니다. 음수를 넘기면 `IllegalArgumentException`이 발생합니다.

lambdaQL은 HQL에 `LIMIT`을 쓰지 않습니다. Hibernate가 데이터베이스별로 올바른 SQL을 씁니다.

| 데이터베이스 | SQL |
|---|---|
| H2 | `offset ? rows fetch first ? rows only` |
| MariaDB | `limit ?, ?` |
| Oracle | `offset ? rows fetch first ? rows only` |

**`orderBy` 없는 페이징.** `orderBy` 없이 `limit`과 `offset`을 호출할 수 있습니다. 이때 lambdaQL은 행을 id로 정렬하므로 페이지가 안정적입니다.

**(테스트됨)** [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java), 영역 `page-only`:

```java
RenderedQuery q = qb.from(Order.class).where(o -> o.isActive()).limit(2).toHql();
assertEquals("select e0 from Order e0 where e0.active = true order by e0.id asc", q.hql());
```

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `page-only`:

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

<a id="tie-breaker-automatic-id"></a>
### 동순위 처리(자동 `id`)

위 HQL을 보십시오. lambdaQL이 끝에 `e0.id asc`를 추가했습니다. 왜일까요?

- 여러 행이 같은 `status`나 `price`를 가질 수 있습니다. 데이터베이스는 그런 행을 **아무 순서로나** 반환할 수 있습니다. Oracle은 실제로 그렇게 합니다(측정함).
- 그러면 2페이지와 3페이지에 같은 행이 두 번 나오거나 행이 빠질 수 있습니다.

규칙: **페이징을 사용하고 정렬 키에 id가 없으면 lambdaQL은 루트 `@Id asc`를 마지막 키로 추가합니다.**

- 한 번만 추가합니다. 키에 이미 id가 있으면(예: `by.desc(o.getId())`) 추가하지 않습니다.
- `limit`과 `offset`이 없으면 아무것도 추가하지 않습니다.
- **아직 구현되지 않음**(조인이 아직 구현되지 않음): to-many 조인이 있으면 조인한 엔티티의 id도 추가합니다. 컬렉션 fetch join과 `limit`/`offset`을 함께 사용하면 거부됩니다.

<a id="49-strings-and-special-values"></a>
## 4.9 문자열과 특수한 값

- 문자열 순서는 데이터베이스 콜레이션을 따릅니다. MariaDB `_ci`는 대소문자를 무시합니다. Oracle은 `NLS_SORT`를 사용하는데, 이는 JVM 로캘을 따릅니다(예: `de_DE`는 독일어 순서). Oracle에는 `NLS_SORT=BINARY`를 권장합니다.
- Oracle은 `''`를 NULL로 저장하므로 빈 문자열은 NULL처럼 정렬됩니다.
- `NaN`은 데이터베이스마다 다르게 정렬됩니다.

<a id="410-reusable-and-external-sorts"></a>
## 4.10 재사용 정렬과 외부 정렬

**아직 구현되지 않음.** 설계에는 다음도 있습니다.

- 재사용할 수 있는 정렬을 위한 `SortSpec`. `then()`과 `reversed()`가 있고 `orderBy(spec)`으로 전달합니다.

  ```java
  var newest = SortSpec.of(Order.class, (o, by) -> by.desc(o.getUpdateAt()).asc(o.getId()));
  qb.from(Order.class).orderBy(newest).limit(20).toList();
  ```

- 외부 문자열(`"price,desc_nulls_last"` 같은 요청 파라미터)을 정렬로 바꾸는 `SortWhitelist<T>`. 외부 문자열은 화이트리스트를 통해서만 받습니다.
- **계획됨:** Spring `Sort` / `Pageable` 모듈(Spring의 각 `Sort.Order`는 `Direction` 값 하나에 대응), `(o, c, by) -> ...`를 사용한 조인, 키셋 페이징.

그때까지는 `Direction.parse`와 `by.sort(key, dir)`로 실행 시점의 방향을 정할 수 있습니다([4.3](#43-direction) 참고). 키는 일반 Java 코드에서 고르십시오.

<a id="411-sorting-inside-subqueries"></a>
## 4.11 서브쿼리 안의 정렬

**아직 구현되지 않음**(서브쿼리가 아직 구현되지 않음).

서브쿼리 안의 정렬은 `limit`과 함께 사용할 때(top-1 또는 top-N)만 유지됩니다.
`limit`이 없으면 결과에 영향을 주지 않으므로 lambdaQL은 정렬을 제거하고 경고를 로그에 남깁니다.
[5. 서브쿼리](05-subquery.md#59-sorting-inside-a-subquery)를 참고하십시오.

<a id="412-rules-summary-s1-to-s14"></a>
## 4.12 규칙 요약(S1~S14)

| # | 규칙 | 상태 |
|---|---|---|
| S1 | 정렬은 람다 하나입니다: `(o, by) -> by...`. 키가 하나면 `o -> key`와 선택적인 `Direction`. 조인이면 `(o, c, by) -> by...`(`by`는 항상 마지막) | 구현됨. 조인 형태는 제외 |
| S2 | 키는 `Comparable`입니다. 엔티티는 키가 될 수 없습니다(컴파일 오류). 키는 엔티티를 사용해야 합니다 | 구현됨 |
| S3 | 방향은 `asc` / `desc`나 `sort(key, Direction)`으로 정합니다. `asc(key)`/`desc(key)`(`ASC`/`DESC`)는 NULL 정책을 사용하고, `asc(key, nullsFirst)`, `desc(key, nullsFirst)`, `*_NULLS_*`는 쓴 그대로 사용합니다 | 구현됨 |
| S4 | 본문은 `by` 호출의 단순한 체인뿐입니다. `if`, 삼항 연산자, 반복문은 안 됩니다 | 구현됨 |
| S5 | `orderBy`는 쿼리당 한 번입니다. `orderBy` 뒤에 `where`는 안 됩니다. `limit` 뒤에 `orderBy`는 안 됩니다(컴파일 오류) | 구현됨 |
| S6 | `SortSpec`으로 재사용합니다. 외부 문자열은 `SortWhitelist`를 통해서만 받습니다 | 아직 구현되지 않음 |
| S7 | 키: 컬럼, to-one 경로, `+ - *`, 확대 변환. 값 분기는 안 됩니다 | 구현됨. 컬럼과 to-one 경로는 테스트함 |
| S8 | `orderBy`에만 쓰인 연관 경로는 `left join`을 사용하므로 연관이 없는 행도 유지됩니다. 같은 경로를 `where`에서 쓰면 정렬은 `where`의 조인을 사용합니다 | 구현됨 |
| S9 | 페이징을 사용할 때 키에 id가 없으면 루트 `@Id asc`를 추가합니다. 컬렉션 fetch join + 페이징은 거부됩니다 | 루트 id는 구현됨. 조인 관련 부분은 아직 구현되지 않음 |
| S10 | 분석 결과는 람다별로 캐시됩니다. 캡처한 `Direction`은 호출할 때마다 읽습니다 | 구현됨 |
| S11 | Spring `Sort.Order`는 `Direction`에 1:1로 대응합니다(계획된 모듈). `SortWhitelist.parse("price,desc_nulls_last")` | 아직 구현되지 않음 |
| S12 | 조인 쿼리도 같은 형태를 사용합니다: `(o, c, by)`, `(o, c, i, by)` | 아직 구현되지 않음 |
| S13 | 메서드 참조는 키 하나의 짧은 형태에서만 사용합니다. 컴파일은 되지만 의미가 틀린 메서드 참조는 거부됩니다([9. Java 노트](09-java-notes.md#99-method-references) 참고) | 구현됨(getter 참조) |
| S14 | 서브쿼리 정렬: `limit`이 있으면 유지, 없으면 경고와 함께 제거. 데이터베이스별 차이는 lambdaQL이 처리합니다(5장 참고) | 아직 구현되지 않음 |

---

[Java 트랙](README.md) | 이전: [3. 조건(`where`)](03-where.md) | 다음: [5. 서브쿼리](05-subquery.md) | [이 페이지의 Kotlin 버전](../kotlin/04-order-paging.md)
