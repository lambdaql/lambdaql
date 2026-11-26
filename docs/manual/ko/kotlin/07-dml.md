<a id="7-update-insert-delete-kotlin"></a>
# 7. 수정, 삽입, 삭제 (Kotlin)

[Kotlin 트랙](README.md) | [매뉴얼 홈](../README.md) | 이전: [6. 조회, 집계, 그룹화](06-select-group.md) | 다음: [8. 프로시저와 DB 함수](08-procedures-functions.md) | [이 페이지의 Java 버전](../java/07-dml.md)

공통 페이지: [1. 개요](../01-overview.md) · [11. 오류와 언어](../11-errors-and-languages.md)

lambdaQL의 진입점은 다섯 개입니다.

| 진입점 | 용도 |
|---|---|
| `qb.from(X::class.java)` | 조회(3 ~ 6장) |
| `qb.update(X::class.java)` | 행 수정 |
| `qb.insert(X::class.java)` | 행 삽입 |
| `qb.delete(X::class.java)` | 행 삭제 |
| `qb.upsert(X::class.java)` | 행 삽입. 같은 `@Id`의 행이 있으면 그 행을 수정 |

기본 개념은 쿼리와 같습니다.

- 각 부분을 **람다 표현식 하나**로 작성합니다. `val` 속성에는 `Dml.value`로 값을 대입합니다([7.3](#73-two-ways-to-assign-property-or-dmlvalue)).
- lambdaQL은 람다 표현식을 호출하지 않고 바이트 opcode를 분석합니다. `it.status = 1` 같은 속성 대입은 setter 호출로 컴파일되고, `set e0.status = 1`이 됩니다.
- `where`는 [3. 조건(`where`)](03-where.md)과 같은 규칙을 사용합니다. 대상 행은 같은 람다 표현식으로 조회한 행과 같습니다.
- `execute()`는 문장을 실행하고 **영향을 받은 행 수**(`Int`)를 반환합니다.
- `executeOne()`은 **최대 한 행**만 변경해야 하는 update나 delete를 실행합니다([1행 보호](#one-row-protection) 참고).
- `orderBy { ... }.limit(n)`는 앞쪽 `n`개 행만 변경합니다([limit](#limit-change-the-first-rows) 참고).
- `toHql()`은 문장을 실행하지 않고 HQL과 파라미터를 반환합니다.
- 문장 실행에는 트랜잭션이 필요합니다([트랜잭션](#transactions) 참고).

<a id="contents"></a>
## 목차

- [7.1 예제 엔티티](#71-example-entities)
- [7.2 수정](#72-update)
- [7.3 대입하는 두 가지 방법: 속성 또는 `Dml.value`](#73-two-ways-to-assign-property-or-dmlvalue)
- [7.4 삭제](#74-delete)
- [7.5 삽입](#75-insert)
- [7.6 업서트](#76-upsert)
- [7.7 DML에 대한 Kotlin 노트](#77-kotlin-notes-for-dml)
- [7.8 중요한 규칙](#78-important-rules)
- [7.9 모든 규칙 U1 ~ U21(요약)](#79-all-rules-u1-to-u21-summary)
- [7.10 v0.1에 없는 것](#710-not-in-v01)

---

<a id="71-example-entities"></a>
## 7.1 예제 엔티티

이 장은 `Order`, `Customer`, `Item`을 사용합니다([2. 시작하기](02-getting-started.md#example-entities) 참고).
예제는 [`query/src/test/java/org/lambdaql/query`](https://github.com/lambdaql/lambdaql/tree/main/query/src/test/java/org/lambdaql/query)의 Java 예제 엔티티를 Kotlin에서 그대로 사용합니다.
Kotlin은 Java getter와 setter를 속성으로 보므로 `it.status = st`처럼 대입합니다.

| 클래스 | 이 장에서 쓰는 속성 |
|---|---|
| `Order` | `id`(IDENTITY), `price`(`double`), `status`(`short`), `active`(`boolean`, Kotlin에서는 `isActive`), `description`, `updateAt`, `kind`(enum `Order.Kind`), `customer`. NULL이 될 수 있는 `total`(`Double`), `cnt`(`Integer`), `custName`, `address`(`@Embedded` `Address`: `city`, `zip`) |
| `VOrder` | `id`, `status`(`Integer`), `version`(`@Version`) |
| `Archive` | `id`(생성기 없음), `price`, `customerName` |
| `Summary` | `customerId`(`Integer`), `total`, `cnt`(`Long`) |
| `IdOrder` | `id`(IDENTITY), `price`, `status` |

리스트 삽입에 쓰는 `Line`과 `Ref`는 일반 데이터 클래스입니다(예: `data class Line(val id: Long, val price: Double)`).
`status`가 `short`이므로 조건은 `it.status.toInt() == 1`로 씁니다.

---

<a id="72-update"></a>
## 7.2 수정

→ [7.2 수정](07-2-update.md)

<a id="73-two-ways-to-assign-property-or-dmlvalue"></a>
## 7.3 대입하는 두 가지 방법: 속성 또는 `Dml.value`

| 방식 | 사용 시점 | 예 |
|---|---|---|
| 속성 대입 | 속성이 `var`일 때(setter가 있음). 컴파일러가 값의 타입을 검사합니다. | `set { it.status = 1 }` |
| `Dml.value(column, value)` | 속성이 `val`일 때(setter가 없음). 예: DDD 스타일 엔티티. | `set { o -> value(o.status, 1) }` |

`value`는 함수 `org.lambdaql.Dml.value`입니다. `import org.lambdaql.Dml.value`로 가져와 인자 하나의 람다 표현식 안에서 씁니다.

<!-- region: KotlinQueryTest.kt#update-value -->
```kotlin
// entities without setters (val): Dml.value(column, value)
val u = qb.update(Order::class.java)
    .set { o -> value(o.description, "done") }
    .where { it.status.toInt() == 2 }
assertEquals("update Order e0 set e0.description = 'done' where e0.status = 2", u.toHql().hql())
```

예제 엔티티의 `status`는 `short`이므로 조건은 `it.status.toInt() == 2`로 씁니다. 같은 문장의 Java 형태는 [이 페이지의 Java 버전](../java/07-dml.md#73-two-ways-to-assign-setter-or-dmlvalue)에 있습니다.

**데이터베이스별 SQL**

<!-- sql: update-value -->
```sql
-- H2, MariaDB
update orders o1_0
set description='done'
where o1_0.status=2

-- Oracle
update orders o1_0
set o1_0.description='done'
where o1_0.status=2
```

Oracle은 `set`에 별칭을 씁니다(`set o1_0.status=?`). H2와 MariaDB는 쓰지 않습니다.

이 장의 모든 예제는 `value`로도 쓸 수 있습니다. Kotlin 문장 하나에 `value` 호출 하나를 씁니다.

<!-- region: ManualKotlinDmlTest.kt#value-forms -->
```kotlin
// Dml.value(column, value) for properties without a setter (val); one value call per statement
val u = qb.update(Order::class.java)
    .set { o -> value(o.status, st); value(o.price, o.price * 1.1) }
    .where { it.status.toInt() == 1 }
assertEquals("update Order e0 set e0.status = :p1, e0.price = (e0.price * 1.1) where e0.status = 1", u.toHql().hql())
val embedded = qb.update(Order::class.java).set { o -> value(o.address.city, city) }.where { it.id == id }
assertEquals("update Order e0 set e0.address.city = :p1 where e0.id = :p2", embedded.toHql().hql())
val fk = qb.update(Order::class.java).set { o -> value(o.customer?.id, cid) }.where { it.id == id }
assertEquals("update Order e0 set e0.customer.id = :p1 where e0.id = :p2", fk.toHql().hql())
```

`value`의 규칙:

- 첫 번째 인자는 **대상에서 시작하는 속성 읽기(getter 호출)**여야 합니다: 속성, 임베디드 경로, 또는 연관의 `@Id`.
  캡처 값, 계산식, 소스 엔티티의 경로는 분석 시점 오류(`LQL-3039`)입니다.
- 같은 속성의 setter 규칙을 그대로 적용합니다. `value(o.x, v)`는 `o.x = v`와 같으므로 U1과 U6이 적용되고, 연관은 `@Id`만 대입할 수 있습니다(`LQL-3050`).
- 속성 대입과 `value`는 **한 람다 표현식 안에서 섞어 쓸 수 있습니다**.
- `value(column, value)`는 제네릭이므로 컴파일러는 값이 컬럼에 맞는지 검사하지 않습니다.
  lambdaQL은 분석 시점에 컬럼 타입과 값 타입을 대조하고, 맞지 않으면 `LQL-3065` 오류를 냅니다.
  숫자는 숫자 컬럼에 맞습니다. 다만 소수는 정수 컬럼에 맞지 않습니다. 타입을 알 수 없는 값(`Any`, 제네릭 집계의 `Number`)은 대조하지 않습니다.
- 자기 `value(...)` 함수가 있는 클래스 안에서는 import한 `value`가 가려집니다. 그곳에서는 `Dml.value(...)`라고 쓰십시오.
- `val` 속성에는 `value(...)`로 대입합니다.

<a id="subquery-in-a-set-value"></a>
### `set` 값 안의 서브쿼리

대입 람다 표현식의 두 번째 인자는 서브쿼리의 시작점 `sub`입니다. `where { o, sub -> ... }`와 같은 표기입니다.
`insert ... select`에서는 세 번째 인자가 `sub`입니다([7.5](07-5-insert.md) 참고).

<!-- region: KotlinQueryTest.kt#update-sub -->
```kotlin
// the second parameter starts a subquery, as in where { o, sub -> ... }
val u = qb.update(Order::class.java)
    .set { o, sub -> o.price = sub.from(Item::class.java).where { i -> i.order?.id == o.id }.sum { it.price } }
    .where { o, sub -> sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
assertEquals("update Order e0 set e0.price = (select sum(s1.price) from Item s1 where s1.order.id = e0.id)"
        + " where exists (select 1 from Item s1 where s1.order.id = e0.id)", u.toHql().hql())
```

- 다른 테이블의 집계와 같은 테이블의 집계를 모두 `set` 값으로 쓸 수 있습니다(H2, MariaDB 10.11, Oracle 23.26에서 실측). `update`에서는 MariaDB도 오류 1093을 내지 않습니다.
- 일치하는 행이 없으면 `sum` 등은 NULL을 돌려줍니다. NOT NULL 컬럼이면 DB 오류가 나므로 `?: 0.0`을 붙이십시오. `coalesce(...)`가 됩니다([7.2](07-2-update.md#an-aggregate-from-a-subquery) 참고).
- insert의 `values { a, sub -> ... }`와 upsert(`onConflict { ... }.update { a, sub -> ... }`)에서도 같은 방식으로 서브쿼리를 씁니다([7.5](07-5-insert.md#a-subquery-in-a-value) 참고). 리스트 형태는 `values(list) { a, l, sub -> ... }`처럼 세 번째 인자가 `sub`입니다.

예제 코드: [`KotlinQueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)(`update-value`, `update-sub`), [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)(`value-forms`).

---

<a id="74-delete"></a>
## 7.4 삭제

→ [7.4 삭제](07-4-delete.md)

<a id="75-insert"></a>
## 7.5 삽입

→ [7.5 삽입](07-5-insert.md)

<a id="76-upsert"></a>
## 7.6 업서트

→ [7.6 업서트](07-6-upsert.md)

<a id="77-kotlin-notes-for-dml"></a>
## 7.7 DML에 대한 Kotlin 노트

| Kotlin 형태 | 뜻 |
|---|---|
| `it.status = x` | setter 호출: `set e0.status = :p1` |
| `a ?: b`(문자열이나 숫자 값) | `coalesce(a, b)` |
| `it.customer?.id = x` | 항상 대입함(`?.`는 무시됨) |
| `it.cnt += 1` | 지원(`set e0.cnt = e0.cnt + 1`) |
| `value(o.status, x)` | `val` 속성용 대입. 문장마다 `value` 하나 |
| `value(o.cnt, o.cnt + 1)` | 지원(`set e0.cnt = e0.cnt + 1`) |
| `it.cnt++`, `it.cnt--` | 거부됨(`LQL-2003`). `+= 1`을 사용하십시오 |
| `it.apply { }`, `with(it) { }`, `it.also { }` | 거부됨(`LQL-2003`). 직접 대입하십시오 |
| 값을 고르는 `if` / `when`, 토글 `!it.isActive` | `case when`([3.5.1](03-where.md#351-values-chosen-by-a-condition)) |
| 대입 여부를 고르는 `if (c) it.price = x` | 거부됨(`LQL-2009`). 조건은 `where`에 쓰거나 `set()`을 여러 번 호출하십시오 |
| 람다 표현식 안의 지역 `val` | 거부됨(`LQL-2003`). 대입한 컬럼을 다시 읽는 문제를 숨길 수 있음 |

<!-- region: KotlinCaseWhenTest.kt#set-toggle -->
```kotlin
// a toggle and a number elvis are values too
val toggle = qb.update(Order::class.java).set { it.isActive = !it.isActive }.all()
assertEquals("update Order e0 set e0.active = case when e0.active = false then true else false end", toggle.toHql().hql())
val zero = qb.update(Order::class.java).set { it.cnt = it.cnt ?: 0 }.all()
assertEquals("update Order e0 set e0.cnt = coalesce(e0.cnt, 0)", zero.toHql().hql())
// an if that chooses whether to assign is rejected (LQL-2009): put that condition in where
val e = assertThrows(LambdaQLException::class.java) {
    qb.update(Order::class.java).set { if (it.isActive) it.price = 1.0 }
}
assertEquals("LQL-2009", e.code())
```

예제 코드: [`KotlinCaseWhenTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinCaseWhenTest.kt)(`set-toggle`).

Kotlin에 대한 자세한 내용: [9. Kotlin 노트](09-kotlin-notes.md).

---

<a id="78-important-rules"></a>
## 7.8 중요한 규칙

<a id="the-right-side-reads-the-old-value"></a>
### 오른쪽 값의 기준: 이전 값

SQL에서 SET 오른쪽의 모든 컬럼은 수정 **이전**의 값을 뜻합니다. lambdaQL은 이 규칙을 따릅니다.

<!-- region: ManualKotlinDmlTest.kt#old-value -->
```kotlin
// the right side reads the value before the change
val self = qb.update(Order::class.java).set { it.price = it.price * 1.1 }.all()             // reads itself: allowed
val readFirst = qb.update(Order::class.java).set { it.total = it.price; it.price = 0.0 }.all() // read, then assign: allowed
assertEquals("update Order e0 set e0.total = e0.price, e0.price = 0.0", readFirst.toHql().hql())
// assign, then read again: an error at set(), because MariaDB would read the new value
val error = assertThrows(LambdaQLException::class.java) {
    qb.update(Order::class.java).set { it.price = it.price * 2; it.total = it.price }
}
assertEquals("LQL-3042", error.code())
```

**같은 문장에서 컬럼에 대입한 뒤 나중에 그 컬럼을 읽으면** lambdaQL은 `set(...)`을 호출하는 시점에 `TranslationException`(`LQL-3042`)을 던집니다.
이유: 이 경우 MariaDB는 **새** 값을 사용하고, H2와 Oracle은 이전 값을 사용합니다. 결과가 다릅니다(실측: MariaDB 200, H2와 Oracle 100).

다음 경우는 세 데이터베이스의 결과가 같으므로 허용합니다.

| 형태 | 허용 여부 |
|---|---|
| `it.price = it.price * 1.1`(자기 자신을 읽음) | 허용 |
| `it.total = it.price; it.price = 0.0`(먼저 읽고 나중에 대입) | 허용 |
| `it.price = ...; it.total = it.price`(먼저 대입하고 나중에 읽음) | **오류** |

이 검사는 물리 컬럼(임베디드 하위 컬럼, FK 컬럼) 단위로 동작하며, 여러 `set()` 호출 사이에도 적용됩니다.

<a id="assign-a-column-only-once"></a>
### 컬럼당 한 번의 대입

같은 컬럼에 두 번 대입하면 오류입니다. `it.address = ...` 뒤의 `it.address.city = ...`,
또는 `it.customer = ...` 뒤의 `it.customer?.id = ...`도 포함됩니다.

<a id="rules-checked-at-analysis-time"></a>
### 분석 시점의 규칙 검사

lambdaQL은 람다 표현식을 읽을 때 대입 규칙을 검사하고, `set(...)`이나 `values(...)`에서 `LambdaQLException`을 던집니다(예: `LQL-3037`, `LQL-3042`, `LQL-3041`).
Kotlin 람다 표현식으로 쓴 다음 형태는 람다 표현식을 읽을 때 거부됩니다. 대입한 컬럼을 다시 읽는 형태와 같은 컬럼을 두 번 대입하는 형태의 예는 [오른쪽 값의 기준: 이전 값](#the-right-side-reads-the-old-value)과 [7.2](07-2-update.md#many-set-calls-patch)에 있습니다.

<!-- region: ManualKotlinDmlTest.kt#assign-rules -->
```kotlin
// forms that the assignment rules reject when the lambda is read
val increment = assertThrows(LambdaQLException::class.java) {
    qb.update(Order::class.java).set { it.cnt++ }                       // ++: write += 1
}
val scope = assertThrows(LambdaQLException::class.java) {
    qb.update(Order::class.java).set { it.apply { status = 1 } }        // apply: assign directly
}
val other = assertThrows(LambdaQLException::class.java) {
    qb.update(Order::class.java).set { it.customer?.name = "x" }        // a property of another table
}
val noNull = assertThrows(LambdaQLException::class.java) {
    qb.update(Archive::class.java).set { it.id = null }.all()            // null into the @Id
}
```

`++`, 스코프 함수, 지역 변수는 `UnsupportedLambdaShapeException`(`LQL-2003`)이고, 연관의 `@Id`가 아닌 속성은 `LQL-3050`, `@Id`에 대입한 `null`은 `LQL-3044`입니다.

<a id="where-or-all-is-required"></a>
### `where`나 `all()`의 필수 지정

update와 delete를 실행하려면 `where(...)`나 `all()`을 지정해야 합니다.

<a id="association-paths-same-rows-as-select"></a>
### 연관 경로: 조회와 같은 행

lambdaQL은 DML에서 FK가 아닌 연관 경로를 **상관 스칼라 서브쿼리**로 씁니다. `where`와 SET 오른쪽 모두 같습니다.
예: `where { it.customer?.name == "Kim" }`은 `where (select s1.name from Customer s1 where s1.id = fk(e0.customer)) = 'Kim' and e0.customer.id is not null`이 되고, `it.custName = it.customer?.name`은 `set e0.custName = (select s1.name from Customer s1 where s1.id = fk(e0.customer))`가 됩니다. 예제는 [7.2](07-2-update.md#a-value-from-another-table)에 있습니다.

이렇게 하는 이유는 다음과 같습니다. lambdaQL이 경로를 Hibernate에 그대로 넘기면 Hibernate는 데이터베이스마다 다른 조인 SQL을 만듭니다
(H2는 `merge`, MariaDB는 `update ... join`, Oracle은 인라인 뷰). 또 SET에 경로가 있으면 **고객이 없는 행이 빠졌습니다**
(실측: `.all()`로 10행 중 9행만 수정됨).
lambdaQL은 서브쿼리를 직접 씁니다. 그러면 문장이 자기 테이블만 읽으므로, 대상 테이블 읽기에 대한 MariaDB의 제약(오류 1093)에 걸리지 않습니다.

조회의 `where`에서 inner join이 되는 경로에는 DML에서 lambdaQL이 `e0.customer.id is not null`을 더합니다.
그 결과 `update`와 `delete`는 `qb.from(X::class.java).where { 같은 람다 표현식 }`가 반환하는 **바로 그 행**만 대상으로 합니다(삭제된 행을 조회 결과와 비교).
null 가드(`it.customer == null || it.customer!!.name == ...`)나 `it.status == 1 || ...`처럼 조회에서 `left join`이 되는 경로에는 이 검사를 더하지 않습니다. `?.`로 쓰든 `!!`로 쓰든 같습니다. 이때도 DML은 조회와 같은 행을 대상으로 합니다([7.2](07-2-update.md), [7.4](07-4-delete.md) 참고).

`@Id`로 끝나는 경로(`it.customer?.id`)는 FK 컬럼입니다. 서브쿼리도 조인도 없습니다.

<a id="batching-for-many-rows"></a>
### 여러 행의 배치 처리

`values(list) { ... }`는 VALUES 행이 여러 개인 문장 하나를 씁니다.

| 항목 | 규칙 |
|---|---|
| 분할 단위 | 행 수가 아니라 값의 개수(행 x 컬럼) |
| 한도 | 문장당 값 500개 이하(고정) |
| 반환 값 | 모든 문장의 합 |
| 트랜잭션 | 모든 문장을 한 트랜잭션에서 실행 |
| 빈 리스트 | SQL 없음, 0 반환 |

Oracle에는 바인드 값 65,535개라는 엄격한 한도가 있습니다(ORA-01745). 파싱은 그보다 훨씬 전부터 매우 느려집니다
(설계 단계 실측: 21,845행에 약 6분). 그래서 한도를 작게 두었습니다.

<a id="limit-change-the-first-rows"></a>
### limit: 앞쪽 행 변경

`where { ... }` 또는 `all()` 뒤에 `orderBy { ... }.limit(n)`을 붙이면 update나 delete가 최대 `n`개 행만 변경합니다. 대상은 `qb.from(X::class.java).where { same }.orderBy { same }`의 앞쪽 `n`개 행입니다.

| 항목 | 규칙 |
|---|---|
| 순서 | `orderBy` 키 다음에 id. `orderBy`가 없으면 id 순서 |
| `limit` 없는 `orderBy` | 오류(`IllegalStateException`) |
| 렌더링 | `e0.id in (select t.id from (select x0.id as id from X x0 ... order by ... limit n) t)` |
| 대상 행 | 안쪽 select가 조인을 포함해 조회 규칙을 따르므로 조회와 같은 행 |
| MariaDB | 파생 테이블 `t`로 오류 1093과 1235를 회피 |

예제와 데이터베이스별 SQL: [7.4](07-4-delete.md#delete-the-first-rows-limit)(delete), [7.2](07-2-update.md#update-the-first-rows-limit)(update).

<a id="one-row-protection"></a>
### 1행 보호

`execute()` 대신 `executeOne()`을 호출하면 **최대 한 행**만 변경합니다.

1. lambdaQL이 조건에 맞는 행의 id를 최대 2개 읽습니다.
2. id가 2개이면 아무것도 변경하지 않고 `NonUniqueResultException`을 던집니다.
3. id가 1개이면 `id = :p and (조건)`으로 그 행을 변경합니다.

2행 이상을 변경하지 않으며 롤백에 의존하지 않습니다. 결과는 0 또는 1입니다. `executeOne()`을 `orderBy`나 `limit`과 함께 쓰면 오류(`IllegalStateException`)입니다.

예제: [7.4](07-4-delete.md#delete-exactly-one-row-executeone). Spring 리포지토리에서는 `deleteOne`과 `updateOne`을 사용합니다([12.4](12-spring-data-jpa.md#one-row-protection-deleteone-and-updateone) 참고).

<a id="transactions"></a>
### 트랜잭션

벌크 문장에는 트랜잭션이 필요합니다. lambdaQL이 트랜잭션을 여는 경우는 `EntityManagerFactory` 생성자를 쓴 빌더뿐입니다.

| 빌더 | 트랜잭션 |
|---|---|
| `QueryBuilder(entityManagerFactory)` | `execute()`와 `executeOne()`이 자체 트랜잭션을 열고 커밋합니다. 오류가 나면 롤백합니다 |
| `EntityManager`를 쓰는 빌더: `QueryBuilder(entityManager)`, Spring의 `QueryBuilder` 빈, 리포지토리 실행기 | 호출자의 트랜잭션(`@Transactional`, 또는 그 `EntityManager`의 활성 트랜잭션). 트랜잭션이 없으면 SQL을 보내기 전에 `TransactionRequiredException`으로 실패합니다(Java). Spring 리포지토리 호출에서는 `InvalidDataAccessApiUsageException`이 됩니다([12.6](12-spring-data-jpa.md#126-transactions-and-exceptions) 참고) |

<a id="the-persistence-context-is-not-updated"></a>
### 영속성 컨텍스트 미반영

벌크 DML은 JPA 영속성 컨텍스트를 거치지 않습니다.

- Hibernate는 문장 실행 전에 대기 중인 변경을 flush합니다.
- 문장 실행 후, 같은 `EntityManager`에 이미 로드된 엔티티는 **이전 값**을 가집니다. 그 엔티티를 다시 사용한다면 `entityManager.clear()`를 호출하십시오.
- JPA `cascade`와 `orphanRemoval`은 실행되지 **않습니다**. 자식을 먼저 삭제하거나 데이터베이스에서 `ON DELETE CASCADE`를 사용하십시오.

<a id="sqlrestriction-and-softdelete"></a>
### `@SQLRestriction`과 `@SoftDelete`

Hibernate는 엔티티의 `@SQLRestriction` 조건과 `@SoftDelete` 조건을 lambdaQL이 만드는 모든 문장에 붙입니다(MariaDB 10.11, Oracle 23.26에서 실측. H2는 미측정).
벌크 `update`와 `delete`도 마찬가지입니다. 예: `where m1_0.order_id=? and (m1_0.hidden = 0)`.

`@SoftDelete` 엔티티의 `delete`는 `update … set deleted = 1 where … and deleted = 0`이 됩니다. 행이 실제로 지워지지 않습니다.
lambdaQL은 이 조건을 따로 처리하지 않습니다.

예제 코드: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)(`old-value`, `assign-rules`).

---

<a id="79-all-rules-u1-to-u21-summary"></a>
## 7.9 모든 규칙 U1 ~ U21(요약)

lambdaQL이 람다 표현식을 읽을 때 검사하는 규칙: U1, U3, U4, U6, U15.
lambdaQL은 `@Version` 속성에 대입하면 거부합니다(`LQL-3068`). U11과 U12는 검사하지 않습니다.

| # | 규칙 |
|---|---|
| U1 | 대입 람다 표현식에는 대상, 대상의 임베디드 경로, 또는 연관의 `@Id`에 대한 **대입(setter 호출)과 `Dml.value(column, value)`만** 들어 있습니다(`LQL-3037`). `if`, `when`, 반복문, 지역 변수, `++`, 스코프 함수와 그 밖의 호출은 오류입니다. 대입이 없는 람다 표현식도 오류입니다. |
| U2 | 오른쪽은 `where`의 값 규칙([3. 조건(`where`)](03-where.md))을 따릅니다. 캡처 값은 바인드 파라미터이고, 람다 표현식에 적은 상수는 리터럴입니다. `a ?: b`는 `coalesce(a, b)`가 됩니다. |
| U3 | 오른쪽은 **이전** 값을 읽습니다. 같은 문장에서 컬럼에 대입한 뒤 그 컬럼을 읽으면 오류입니다. |
| U4 | 같은 물리 컬럼에 두 번 대입하면 오류입니다. `set()` 호출이 달라도 마찬가지입니다. |
| U5 | `where()`나 `all()` 없이는 `execute()`가 없습니다. 컴파일러가 검사합니다. |
| U6 | 선택적이지 않은 컬럼(`@Id`, `nullable=false`, 기본형)에 `null`을 대입하면 오류입니다. 컬렉션이나 연관 엔티티의 `@Id`가 아닌 속성에 대입해도 오류입니다. |
| U7 | 캡처 값은 사용할 때마다 자체 파라미터를 받습니다. (STRING 컬럼과 ORDINAL 컬럼이 enum 파라미터 하나를 공유하면 H2와 MariaDB가 오류 없이 잘못된 값을 저장합니다.) |
| U8 | `LocalDateTime.now()`는 애플리케이션에서 계산하여 바인드합니다. 데이터베이스 시간(`current_timestamp`)은 지원하지 않습니다. |
| U9 | DML에서 FK가 아닌 연관 경로는 `where`와 SET 오른쪽 모두에서 상관 스칼라 서브쿼리가 됩니다. `where`에서 조회라면 inner join할 경로에는 `customer.id is not null`을 더하고, `left join`할 경로에는 더하지 않습니다. DML은 조회와 같은 행을 대상으로 합니다. |
| U10 | `insert ... select`에서 소스 경로는 명시적인 `left join`이 됩니다. |
| U11 | `it.customer = entity`는 엔티티의 `@Id`를 바인드합니다. id가 null이면 바인드가 실패합니다. |
| U12 | IDENTITY와 SEQUENCE id는 생략할 수 있습니다. TABLE 생성기는 `values`에서만 동작하고 `insert ... select`에서는 동작하지 않습니다. 생성기가 없으면 id가 필요합니다. 그 밖의 선택적이지 않은 속성도 필요합니다. |
| U13 | 여러 행은 문장당 값 500개 이하로 나눕니다. 결과는 합산합니다. 모든 문장은 한 트랜잭션에서 실행됩니다. |
| U14 | `set` 람다 표현식 하나로 여러 컬럼에 대입할 수 있고, `set()`은 여러 번 호출할 수 있습니다. 모두 호출 순서대로 SET 목록 하나로 합쳐집니다. 속성 대입과 `Dml.value`는 한 람다 표현식 안에서도 섞을 수 있습니다. |
| U15 | `@Version` 엔티티는 `update versioned`를 사용합니다. 버전에 직접 대입하면 `LQL-3068`입니다. `execute() == 0` 확인은 사용자가 할 일입니다. |
| U16 | 벌크 DML은 영속성 컨텍스트를 건너뜁니다. 실행 전 flush, 실행 후 오래된 엔티티, cascade 없음. |
| U17 | DELETE의 서브쿼리가 대상 엔티티(또는 같은 상속 계층)를 읽으면 lambdaQL은 대상 id를 파생 테이블로 먼저 읽습니다: `d0.id in (select t.id from (select e0.id as id from … where <condition>) t)`. 그렇지 않으면 MariaDB 오류 1093이 납니다. 상관 서브쿼리에도 동작하며, `update`와 복합 id 엔티티는 감싸지 않습니다([7.4](07-4-delete.md#delete-with-a-subquery-on-the-same-table) 참고). |
| U18 | 상속: SINGLE_TABLE은 완전히 지원합니다. JOINED와 TABLE_PER_CLASS의 update/delete는 임시 테이블을 사용합니다. 그 하위 클래스에 대한 insert는 거부됩니다. |
| U19 | 쿼리 캐시 키에는 진입점, 대상 엔티티, set 람다 표현식, where/all, versioned, 분할 크기가 들어갑니다. |
| U20 | `Dml.value(column, value)`: 첫 번째 인자는 대상(또는 `insert ... select`의 대상 `a`)에서 시작하는 속성 읽기여야 합니다(`LQL-3039`). 같은 속성의 setter 규칙(U1, U6, 연관은 `@Id`만)을 그대로 적용합니다. 값의 타입은 컬럼 타입과 대조합니다(`LQL-3065`). 속성 대입과 섞어 쓸 수 있습니다. |
| U21 | 각 람다 표현식은 한 번 분석되고 캐시됩니다. `set()` 호출 사이의 검사는 캐시된 요약만 비교합니다(프로토타입에서 약 0.1 ~ 0.2 µs). |

업서트 규칙은 [7.6 업서트](07-6-upsert.md#rules)에 있습니다.

---

<a id="710-not-in-v01"></a>
## 7.10 v0.1에 없는 것

| 항목 | 지금 할 수 있는 방법 |
|---|---|
| 대입을 고르기 위한 람다 표현식 안의 `if`: `LQL-2009` | `set()`을 여러 번 호출 |
| `++`, `--`, `apply`, `with`, `also`: `LQL-2003` | `+= 1`이나 직접 대입 사용 |
| 문자열 템플릿이나 `+`(`"${it.description}_x"`): `LQL-2007` | 문자열은 람다 표현식 밖에서 만들어 캡처 |
| `insert ... select`의 명시적 조인 | 다대일 경로 사용 |
| `returning` | DML 후 다시 조회 |
| 데이터베이스 시간(`current_timestamp`) | 애플리케이션 시간을 바인드 |

규칙을 어기면 코드 `[LQL-xxxx]`가 붙은 `LambdaQLException`이 발생합니다. 대부분의 규칙은 `TranslationException`, 대입 람다 표현식 안의 조건이나 지역 변수는 `UnsupportedLambdaShapeException`입니다. [11. 오류와 언어](../11-errors-and-languages.md)를 참고하십시오.
모든 규칙을 한곳에서 보려면: [10. 규칙과 제한](10-rules-and-limits.md).

---

[Kotlin 트랙](README.md) | 이전: [6. 조회, 집계, 그룹화](06-select-group.md) | 다음: [8. 프로시저와 DB 함수](08-procedures-functions.md) | [이 페이지의 Java 버전](../java/07-dml.md)
