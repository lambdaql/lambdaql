<a id="5-subqueries-kotlin"></a>
# 5. 서브쿼리 (Kotlin)

> [!NOTE]
> **상태**
> - **구현됨**(마일스톤 M8-1). H2 2.3, MariaDB 10.11, Oracle 23.26(Hibernate 6.6.5)에서 테스트했습니다. 대상은 조회, 수정, 삭제의 `where { o, sub -> ... }` 안의 서브쿼리입니다. `exists` / `!exists`, `in`, `!in`(`NOT IN`으로 작성), 스칼라 집계, top-1 형태, 상관 서브쿼리와 중첩 서브쿼리, 캡처 값을 포함합니다. 테스트는 대부분 **Java** 람다를 사용합니다.
> - **Kotlin 람다로 테스트함**([`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt), 빌드에 포함, H2, MariaDB, Oracle): `exists`, `in`, 스칼라 `avg`. 이 예제에는 **(테스트됨)** 표시가 있습니다. 그 밖의 Kotlin 형태(`!in`, top-1, 중첩)는 빌드 밖에서 kotlinc 2.3.21로 컴파일했고, Java 형태와 같은 HQL을 만듭니다.
> - **아직 구현되지 않음:** MariaDB `lateral` 재작성. (파생 테이블과 CTE는 다른 문법으로 구현했습니다. [6. 조회, 집계, 그룹화](06-select-group.md#69-derived-table-subquery-in-from)를 참고하십시오.)
> - 그 밖의 Kotlin 코드는 테스트된 Java 쿼리를 Kotlin 문법으로 옮긴 것입니다. 테스트된 Java 예제는 [이 페이지의 Java 버전](../java/05-subquery.md)에 있습니다.

[Kotlin 트랙](README.md) | [매뉴얼 홈](../README.md) | 이전: [4. 정렬과 페이징](04-order-paging.md) | 다음: [6. 조회, 집계, 그룹화](06-select-group.md) | [이 페이지의 Java 버전](../java/05-subquery.md)

공통 페이지: [1. 개요](../01-overview.md) · [11. 오류와 언어](../11-errors-and-languages.md)

---

<a id="51-the-idea-a-second-lambda-argument-sub"></a>
## 5.1 아이디어: 두 번째 람다 인자 `sub`

`where` 람다는 **두 번째 인자**로 서브쿼리 팩토리를 받을 수 있습니다. 이 매뉴얼에서는 이를 `sub`라고 부릅니다(어떤 이름이든 사용할 수 있습니다).
그 안에서 일반 쿼리와 **같은 람다 문법**으로 서브쿼리를 작성합니다.

```kotlin
.where { ... }                // 서브쿼리 없음: 인자 하나(it)
.where { o, sub -> ... }      // 서브쿼리 있음: 인자 둘
```

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

> [!NOTE]
> 반환 값(`Boolean`, `Double`, ...)은 코드가 컴파일되도록 하는 자리 표시자일 뿐입니다.
> 람다는 절대 실행되지 않습니다. 이 함수들은 lambdaQL 람다 안에서만 의미가 있습니다.

Kotlin에서 `y in values`는 `values.contains(y)`로 컴파일됩니다. 그래서 Kotlin `in` 연산자는 추가 코드 없이 서브쿼리 API와 함께 동작합니다.

안쪽 람다(`sub.from(...).where { }` 안의 `{ i -> ... }`)는 따로 `Serializable`일 필요가 없습니다. lambdaQL은 바깥 람다의 바이트코드에서 이를 찾습니다.

HQL에서 서브쿼리 루트의 별칭은 `s1`, `s2`, ...입니다(바깥 루트는 `e0`). 서브쿼리 안의 조인은 `s1j1` 같은 별칭을 받습니다.

**(테스트됨)** 표시가 있는 예제는 [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)에서 가져온 것입니다. 이 테스트는 Java 테스트 엔티티를 사용합니다. `price`는 기본형 `double`이고 `qty`는 `int`입니다.
더 많은 쿼리를 Java로 작성한 테스트가 [`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java)에 있습니다(H2, MariaDB, Oracle). 그 예제는 [이 페이지의 Java 버전](../java/05-subquery.md)을 참고하십시오.

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
// HQL: select e0 from Order e0
//      where exists (select 1 from Item s1 where s1.order.id = e0.id and s1.qty > 10)
```

**(테스트됨)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt), 영역 `exists`:

```kotlin
val q = qb.from(Order::class.java)
    .where { o, sub -> sub.from(Item::class.java).where { i -> i.order?.id == o.id && i.qty > 10 }.exists() }
assertEquals("select e0 from Order e0 where exists (select 1 from Item s1 where s1.order.id = e0.id and s1.qty > 10)",
    q.toHql().hql())
```

**SQL 블록 읽는 법.** `…`는 줄여 쓴 select 목록(엔티티의 모든 컬럼)이고, `?`는 바인드 파라미터입니다. 각 줄은 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)에서 나옵니다. 이 테스트는 각 쿼리를 Hibernate 6.6.5로 H2 2.3, MariaDB 10.11, Oracle 23.26에서 실행하며, Hibernate의 SQL이 바뀌면 실패합니다. 이 테스트는 Java 람다를 사용합니다. 의미가 같은 Kotlin 람다는 같은 HQL을 만들므로 SQL도 같습니다.

**데이터베이스별 SQL (Java 테스트 실행 결과, Kotlin도 같은 HQL을 만듦)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `sub-exists`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id and i1_0.qty>10)
```

이는 **상관** 서브쿼리입니다. 안쪽 람다가 바깥의 `o`를 사용합니다([5.6](#56-correlated-subqueries) 참고).

## 5.3 NOT EXISTS

`!`를 사용합니다. 다른 조건과 결합할 수 있습니다.

```kotlin
// status가 1인 주문, 또는 품목이 없는 주문
qb.from(Order::class.java)
    .where { o, sub ->
        o.status == 1 ||
            !sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists()
    }
    .toList()
// HQL: select e0 from Order e0
//      where e0.status = 1 or not exists (select 1 from Item s1 where s1.order.id = e0.id)
```

**데이터베이스별 SQL (Java 테스트 실행 결과, Kotlin도 같은 HQL을 만듦)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `sub-not-exists`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 or not exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id)
```

## 5.4 IN

`grade > 3`인 고객의 주문. `in`을 사용합니다.

```kotlin
qb.from(Order::class.java)
    .where { o, sub ->
        o.customer?.id in sub.from(Customer::class.java)
            .where { c -> c.grade!! > 3 }
            .select { c -> c.id }
    }
    .toList()
// HQL: select e0 from Order e0
//      where e0.customer.id in (select s1.id from Customer s1 where s1.grade > 3)
```

**(테스트됨)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt), 영역 `in`:

```kotlin
val q = qb.from(Order::class.java)
    .where { o, sub -> o.customer?.id in sub.from(Customer::class.java).where { c -> c.grade!! > 3 }.select { c -> c.id } }
assertEquals("select e0 from Order e0 where e0.customer.id in (select s1.id from Customer s1 where s1.grade > 3)",
    q.toHql().hql())
```

**데이터베이스별 SQL (이 HQL에 대한 Hibernate의 SQL)** — [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java)가 같은 HQL을 Hibernate에 직접 넘겨 기록했습니다. Java 테스트(영역 `in`)에는 null 검사 `c.getGrade() != null`이 더 있으며, 그 SQL은 예제 `sub-in`입니다. `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)의 예제 `05-in-subquery`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id in (select c2_0.id from customers c2_0 where c2_0.grade>3)
```

`o.customer?.id`는 고객의 `@Id`로 끝나므로 외래 키 컬럼 `e0.customer.id`입니다. 조인이 필요 없습니다.
`!!`는 무시됩니다(결정 D23). `c.grade!! > 3`은 `s1.grade > 3`입니다.

<a id="55-not-in-is-written-as-not-exists"></a>
## 5.5 NOT IN과 NULL

`x !in values`는 SQL 의미 그대로 `NOT IN`으로 출력됩니다(결정 D48).

```kotlin
// status가 어느 고객의 등급과도 같지 않은 주문
qb.from(Order::class.java)
    .where { o, sub -> o.status !in sub.from(Customer::class.java).select { c -> c.grade } }
    .toList()
// HQL: select e0 from Order e0
//      where e0.status not in (select s1.grade from Customer s1)
```

**데이터베이스별 SQL (Java 테스트 실행 결과, Kotlin도 같은 HQL을 만듦)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `sub-not-in`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status not in (select c2_0.grade from customers c2_0)
```

이유:

- `x NOT IN (subquery)`는 서브쿼리가 NULL을 하나라도 반환하면 **행을 하나도 반환하지 않습니다**. lambdaQL은 이 SQL 의미를 유지하고(결정 D48) `LQL-5009`를 `DEBUG` 수준으로 기록합니다.
- NULL을 건너뛰려면 `sub.from(Customer::class.java).where { c -> c.grade != null }.select { c -> c.grade }`처럼 서브쿼리에 null 검사를 쓰십시오.
  Java 테스트는 NULL 등급이 있는 데이터로 두 쿼리를 모두 확인합니다.
- `!sub...exists()`는 여전히 `NOT EXISTS`로 작성됩니다.

<a id="56-correlated-subqueries"></a>
## 5.6 상관 서브쿼리

안쪽 람다가 바깥 엔티티를 사용하면 서브쿼리는 **상관** 서브쿼리가 됩니다. 특별한 문법은 필요 없습니다. 바깥 변수를 그대로 사용합니다.

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
| 바깥 엔티티(`o`) | 바깥 별칭에 대한 참조(`e0.id`) |
| 그 밖의 값(지역 `val`, 속성, ...) | [3.11](03-where.md#311-captured-values)과 같은 바인드 파라미터 |

```kotlin
val minQty = 10
.where { o, sub ->
    sub.from(Item::class.java)
        .where { i -> i.order?.id == o.id && i.qty > minQty }
        .exists()
}
// HQL: select e0 from Order e0
//      where exists (select 1 from Item s1 where s1.order.id = e0.id and s1.qty > :p1)
// 파라미터: {p1=10}
```

[`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java)의 Java 테스트 메서드 `capturedValueInside`가 이 HQL, 파라미터, 행을 확인합니다.

**데이터베이스별 SQL (이 HQL에 대한 Hibernate의 SQL)** — [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java)가 같은 HQL을 Hibernate에 직접 넘겨 기록했습니다. `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)의 예제 `05-correlated-param`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id and i1_0.qty>?)
```

**상관 조건의 `==`.** `i.order?.id == o.id`는 두 `Long?` 값을 비교합니다. 엔티티 값 두 개는 SQL 의미로 비교하므로(결정 D48, [3.6](03-where.md#36-null-rules) 참고) lambdaQL은 단순한 `=`를 씁니다(`!=`는 `<>`).
데이터베이스는 인덱스를 사용할 수 있습니다.

<a id="57-scalar-subqueries"></a>
## 5.7 스칼라 서브쿼리

**값 하나**를 내는 서브쿼리는 비교에 사용할 수 있습니다.

평균 가격보다 비싼 주문:

```kotlin
qb.from(Order::class.java)
    .where { o, sub -> o.price!! > sub.from(Order::class.java).avg { x -> x.price } }
    .toList()
// HQL: select e0 from Order e0
//      where e0.price > (select avg(s1.price) from Order s1)
```

**(테스트됨)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt), 영역 `scalar`(테스트의 `price`는 `double`이므로 `!!`가 없음):

```kotlin
val q = qb.from(Order::class.java).where { o, sub -> o.price > sub.from(Order::class.java).avg { x -> x.price } }
assertEquals("select e0 from Order e0 where e0.price > (select avg(s1.price) from Order s1)", q.toHql().hql())
```

**데이터베이스별 SQL (Java 테스트 실행 결과, Kotlin도 같은 HQL을 만듦)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `sub-scalar`:

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

```kotlin
qb.from(Order::class.java)
    .where { o, sub -> sub.from(Item::class.java).where { i -> i.order?.id == o.id }.count() >= 2 }
    .toList()
// HQL: select e0 from Order e0
//      where (select count(s1) from Item s1 where s1.order.id = e0.id) >= 2L
```

**데이터베이스별 SQL (Java 테스트 실행 결과, Kotlin도 같은 HQL을 만듦)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `sub-count`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where (select count(i1_0.id) from items i1_0 where i1_0.order_id=o1_0.id)>=2
```

top-1 예: 같은 고객의 가장 비싼 주문.

```kotlin
qb.from(Order::class.java)
    .where { o, sub ->
        o.id == sub.from(Order::class.java)
            .where { x -> x.customer?.id == o.customer?.id }
            .orderBy { x, by -> by.desc(x.price) }
            .limit(1)
            .select { x -> x.id }
            .first()
    }
    .toList()
```

같은 쿼리를 Java로 테스트했습니다(영역 `top-one`, 예제 `sub-top-one`). lambdaQL은 서브쿼리 안에서 마지막 정렬 키로 `id`를 더합니다(규칙 S9).
Java 테스트에서 `price`와 `Customer.id`는 기본형입니다. Kotlin 엔티티의 nullable `price: Double?`와 `customer?.id`에는 [3.6](03-where.md#36-null-rules)과 [4.4](04-order-paging.md#44-null-ordering-is-the-same-on-every-database)의 NULL 규칙이 적용됩니다. 그래서 HQL의 `nulls` 부분과 비교 방식이 다를 수 있습니다.
상관 조건은 고객 엔티티가 아니라 외래 키(`customer?.id`)를 비교합니다. [5.10](#510-paths-and-null-inside-subqueries)을 참고하십시오.

집계의 NULL은 SQL을 따릅니다. `sum`, `avg`, `min`, `max`는 행이 없으면 NULL을 냅니다. `count`는 0을 냅니다.

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
// HQL: select e0 from Customer e0
//      where exists (select 1 from Order s1 where s1.customer.id = e0.id
//                    and exists (select 1 from Item s2 where s2.order.id = s1.id and s2.qty > 10))
```

**데이터베이스별 SQL (Java 테스트 실행 결과, Kotlin도 같은 HQL을 만듦)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `sub-nested`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from customers c1_0
where exists(select 1 from orders o1_0 where o1_0.customer_id=c1_0.id and exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id and i1_0.qty>10))
```

단계마다 새 이름(`sub2`, `o`, `i`)을 사용하십시오. Kotlin은 같은 이름을 다시 쓰는 것을 허용하지만 이름 가림(shadowing) 경고를 내고, 코드도 읽기 어려워집니다.

<a id="59-sorting-inside-a-subquery"></a>
## 5.9 서브쿼리 안의 정렬

| 경우 | lambdaQL의 동작 |
|---|---|
| `limit`과 함께 쓴 `orderBy`(top-1, top-N) | `id` 동순위 처리와 함께 유지 |
| `limit` 없는 `orderBy` | **제거**하고 경고를 로그에 남김(결과를 바꾸지 않음. Oracle에서는 문법 오류 ORA-00907까지 남) |
| `limit`이 있는 서브쿼리에 대한 `in`(MariaDB는 `IN (... limit n)`을 지원하지 않음, 오류 1235) | 모든 데이터베이스에서 파생 테이블로 감쌈: `in (select t1.v from (select s1.id as v ... limit n) t1)` |
| MariaDB: `lateral`(MariaDB에서 잘못된 결과) | `row_number() over (partition by ... order by ...)`로 재작성: **아직 구현되지 않음** |

[4. 정렬과 페이징](04-order-paging.md)의 NULL 정책과 자동 `id` 동순위 처리는 서브쿼리 안에서도 적용됩니다.

<a id="510-paths-and-null-inside-subqueries"></a>
## 5.10 서브쿼리 안의 경로와 NULL

서브쿼리 안에서 조건, 경로, NULL은 **바깥 쿼리와 똑같이** 동작합니다([3. 조건(`where`)](03-where.md)).

- 엔티티 값 사이의 `==`는 SQL 의미의 값 비교입니다(D48). 캡처 값 `null`은 어떤 행에도 일치하지 않습니다(`LQL-4045`).
- `!!`는 무시됩니다. 경로의 `?.`는 같은 컬럼을 가리킵니다.
- 서브쿼리 자신의 엔티티에 대한 연관 경로는 바깥 `where`와 같은 조인 규칙을 따릅니다. 항상 inner `join`입니다(결정 D48).
- `@Id`로 끝나는 경로는 외래 키 컬럼입니다(조인 없음).
- **바깥 엔티티**는 자신의 컬럼과 외래 키 id(`o.id`, `o.customer?.id`)만 사용할 수 있습니다. 조인이 필요한 바깥 엔티티의 경로(`o.customer?.name`)는 분석 시점 오류입니다. 대신 외래 키 id를 비교합니다.
- 컬렉션 경로(`o.items`)는 거부됩니다. 컬렉션에 대해 물으려면 서브쿼리를 사용합니다.
- 엔티티의 `@SQLRestriction`과 `@SoftDelete` 조건은 서브쿼리(`exists`, `in`) 안에도 Hibernate가 붙입니다(MariaDB 10.11, Oracle 23.26에서 실측). lambdaQL은 이 조건을 따로 처리하지 않습니다.

```kotlin
// it.items.size > 0 대신(거부됨):
.where { o, sub -> sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
```

<a id="511-subqueries-in-other-places"></a>
## 5.11 다른 곳의 서브쿼리

- **update / delete의 서브쿼리**: `where`에서 같은 `sub` 문법을 사용합니다. 구현되어 있습니다(Java 람다로 테스트, 영역 `dml-subquery`). [7.4 삭제(delete)](07-4-delete.md)와 [7.2 수정(update)](07-2-update.md)을 참고하십시오.
- **`set` 값 안의 서브쿼리**(`o.total = sub.from(...).sum { ... }`): 대입 람다의 두 번째 인자 `sub`로 씁니다(결정 D49). [7.2 수정(update)](07-2-update.md#an-aggregate-from-a-subquery)을 참고하십시오.
- **`from`의 서브쿼리(파생 테이블)와 CTE**: 구현됨(마일스톤 M8-2). `sub`를 쓰지 않고 값 쿼리를 `qb.from(...)`이나 `qb.with(...)`에 전달합니다. [6.9 파생 테이블](06-select-group.md#69-derived-table-subquery-in-from)과 [6.10 CTE](06-select-group.md#610-cte-with)를 참고하십시오.

<a id="512-rules-summary-q1-to-q6"></a>
## 5.12 규칙 요약(Q1 ~ Q6)

| # | 규칙 |
|---|---|
| Q1 | 서브쿼리 안의 조건, 경로, NULL 규칙은 바깥과 같습니다. 조건 안의 연관 경로도 같은 조인 규칙(기본은 inner `join`)을 따릅니다. 조인이 필요한 바깥 엔티티의 경로는 오류이고, 외래 키 id는 허용됩니다 |
| Q2 | **NOT IN은 SQL 의미를 유지합니다**(D48): `x !in values`는 `x not in (select c.sel from C c where <condition>)`이 됩니다. 서브쿼리에 NULL이 있으면 행이 반환되지 않습니다(`DEBUG` 수준 `LQL-5009`). NULL을 건너뛰려면 서브쿼리에 `c.sel != null`을 추가합니다 |
| Q3 | 스칼라 서브쿼리는 행 하나를 내야 합니다: 집계(`max`, `min`, `avg`, `sum`, `count`) 또는 `orderBy { }.limit(1)...first()`. 그 밖의 `first()`는 오류입니다 |
| Q4 | 서브쿼리 정렬: `limit`이 있으면 유지, 없으면 경고와 함께 제거. `IN (... limit)`은 파생 테이블로 감쌉니다. `row_number()`를 쓰는 MariaDB `lateral` 재작성은 아직 구현되지 않았습니다 |
| Q5 | 상관 조건에서 `==`는 `=`로, `!=`는 `<>`로 씁니다(SQL 의미, D48). 어느 한쪽이 NULL이면 일치하지 않습니다. 인덱스를 계속 사용할 수 있습니다 |
| Q6 | 서브쿼리의 `where`는 자체 `sub` 인자를 받을 수 있습니다: `where { i, sub2 -> ... }`. 모든 단계에서 같은 규칙이 적용됩니다 |

---

[Kotlin 트랙](README.md) | 이전: [4. 정렬과 페이징](04-order-paging.md) | 다음: [6. 조회, 집계, 그룹화](06-select-group.md) | [이 페이지의 Java 버전](../java/05-subquery.md)
