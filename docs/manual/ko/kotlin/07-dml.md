<a id="7-update-insert-delete-kotlin"></a>
# 7. 수정, 삽입, 삭제 (Kotlin)

> [!NOTE]
> **상태: 설계됨, 아직 구현되지 않음.**
> 이 장은 확정된 설계입니다(결정 D28, 마일스톤 M10).
> Kotlin 문법은 컴파일되며(kotlinc 2.3.21), SQL은 Hibernate 6.6.5와 함께 H2, MariaDB, Oracle에서 확인했습니다.
> 지금 lambdaQL에서는 동작하지 **않습니다**. 현재는 분석 코어와 `where` + `toList()`만 동작합니다
> (H2, MariaDB, Oracle에서 테스트함). 전체 상태는 [트랙 README](README.md)를 참고하십시오.

[Kotlin 트랙](README.md) | [매뉴얼 홈](../README.md) | 이전: [6. 조회, 집계, 그룹화](06-select-group.md) | 다음: [8. 프로시저와 DB 함수](08-procedures-functions.md) | [이 페이지의 Java 버전](../java/07-dml.md)

공통 페이지: [1. 개요](../01-overview.md) · [11. 오류와 언어](../11-errors-and-languages.md)

lambdaQL의 진입점은 네 개입니다.

| 진입점 | 용도 |
|---|---|
| `qb.from(X::class.java)` | 조회(3 ~ 6장) |
| `qb.update(X::class.java)` | 행 수정 |
| `qb.insert(X::class.java)` | 행 삽입 |
| `qb.delete(X::class.java)` | 행 삭제 |

기본 아이디어는 쿼리와 같습니다.

- 각 부분을 **람다 하나**로 작성합니다. 정적 헬퍼는 없습니다.
- 람다는 **절대 실행되지 않습니다**. lambdaQL은 람다의 바이트코드를 읽습니다. `it.status = 1` 같은 속성 대입은 setter 호출로 컴파일되고, `set o.status = 1`이 됩니다.
- `where`는 [3. 조건(`where`)](03-where.md)과 같은 규칙을 사용합니다. 서브쿼리는 [5. 서브쿼리](05-subquery.md)처럼 `sub`를 사용합니다.
- `execute()`는 문장을 실행하고 **영향을 받은 행 수**(`Int`)를 반환합니다.

<a id="contents"></a>
## 목차

- [7.1 예제 엔티티](#71-example-entities)
- [7.2 수정](#72-update)
- [7.3 대입하는 두 가지 방법: 속성 또는 `by[column] = value`](#73-two-ways-to-assign-property-or-bycolumn--value)
- [7.4 삭제](#74-delete)
- [7.5 삽입](#75-insert)
- [7.6 DML에 대한 Kotlin 노트](#76-kotlin-notes-for-dml)
- [7.7 중요한 규칙](#77-important-rules)
- [7.8 모든 규칙 U1 ~ U21(요약)](#78-all-rules-u1-to-u21-summary)
- [7.9 v0.1에 없는 것](#79-not-in-v01)

---

<a id="71-example-entities"></a>
## 7.1 예제 엔티티

이 장은 `Order`, `Customer`, `Item`을 사용합니다([2. 시작하기](02-getting-started.md#example-entities) 참고).
이 장에서 `Order`에는 **추가 속성**이 몇 개 있습니다.

```kotlin
@Entity
@Table(name = "orders")
class Order(
    // ... id, price, status, active, description, customer, updateAt, items는 평소와 같음
    var total: Double? = null,                 // 품목 가격의 합계
    var cnt: Int = 0,                          // 카운터(+=가 컴파일되도록 Int)
    var custName: String? = null,              // 고객 이름의 사본
    @Embedded var address: Address = Address(),
)

@Embeddable
class Address(var city: String? = null, var zip: String? = null)
```

여기서 사용하는 다른 클래스:

```kotlin
@Entity class VOrder(@Id var id: Long? = null, var status: Int? = null, @Version var version: Long? = null)
@Entity class Archive(@Id var id: Long? = null, var price: Double? = null, var customerName: String? = null)
@Entity class Summary(@Id var customerId: Long? = null, var total: Double? = null, var cnt: Long? = null)
@Entity class IdOrder(@Id @GeneratedValue var id: Long? = null, var price: Double? = null, var status: Int? = null)

data class Line(val id: Long, val price: Double)   // insert의 입력 행
```

---

<a id="72-update"></a>
## 7.2 수정

→ [7.2 수정](07-2-update.md)

<a id="73-two-ways-to-assign-property-or-bycolumn--value"></a>
## 7.3 대입하는 두 가지 방법: 속성 또는 `by[column] = value`

| 방식 | 사용 시점 | 예 |
|---|---|---|
| 속성 대입 | 속성이 `var`일 때(setter가 있음). 컴파일러가 값의 타입을 검사합니다. | `set { it.status = 1 }` |
| `by[column] = value` | 속성이 `val`일 때(setter가 없음). 예: DDD 스타일 엔티티. | `set { o, by -> by[o.status] = 1 }` |

`by[o.status] = 1`은 메서드 `by.set(o.status, 1)`에 대한 Kotlin 인덱스 연산자입니다. `by.set(...)`을 직접 써도 됩니다. 의미는 같습니다.

위의 모든 예는 `by`로도 쓸 수 있습니다.

```kotlin
qb.update(Order::class.java)
    .set { o, by -> by[o.status] = st; by[o.price] = o.price!! * 1.1 }
    .where { it.status == 1 }
    .execute()

qb.update(Order::class.java)
    .set { o, by -> by.set(o.status, st).set(o.price, o.price!! * 1.1) }   // 같은 내용을 체인으로
    .where { it.status == 1 }
    .execute()

qb.update(Order::class.java).set { o, by -> by[o.address.city] = city }.where { it.id == id }.execute()  // 임베디드
qb.update(Order::class.java).set { o, by -> by[o.customer?.id] = cid }.where { it.id == id }.execute()   // FK
```

두 번째 람다 인자는 **컨텍스트 객체 하나**입니다. 여기에는 `set(column, value)`와 서브쿼리 함수 `from(...)`이 모두 있습니다.
이름은 원하는 대로 `by`나 `sub`라고 지으십시오. 이름은 중요하지 않습니다.

```kotlin
qb.update(Order::class.java)
    .set { o, by ->
        by[o.total] = by.from(Item::class.java).where { i -> i.order?.id == o.id }.sum { i -> i.price }
    }
    .all()
    .execute()
```

`by[column] = value`의 규칙(규칙 U20):

- 컬럼은 **대상에서 시작하는 속성 경로**여야 합니다: 속성, 임베디드 경로, 또는 연관의 `@Id`.
  캡처 값, 계산식, 소스 엔티티의 경로는 오류입니다.
- `by.set(column, value)`는 제네릭이므로 컴파일러는 값이 컬럼에 맞는지 검사하지 않습니다.
  lambdaQL이 분석할 때 컬럼 타입과 값 타입을 검사합니다.
- `by[o.cnt] += 1`은 **컴파일되지 않습니다**(의도한 것). `by[o.cnt] = o.cnt + 1`이라고 쓰십시오.
- **한 람다 안에서** 속성 대입과 `by[...]`를 섞지 마십시오. 서로 다른 `set()` 호출에서는 섞어도 됩니다.

---

<a id="74-delete"></a>
## 7.4 삭제

→ [7.4 삭제](07-4-delete.md)

<a id="75-insert"></a>
## 7.5 삽입

→ [7.5 삽입](07-5-insert.md)

<a id="76-kotlin-notes-for-dml"></a>
## 7.6 DML에 대한 Kotlin 노트

| Kotlin 형태 | 뜻 |
|---|---|
| `it.status = x` | setter 호출: `set o.status = :p1` |
| 오른쪽의 `!!` | 무시됨. 코드가 컴파일되도록 할 뿐임(D23) |
| `a ?: b` | `coalesce(a, b)` |
| `it.customer?.id = x` | 항상 대입함(`?.`는 무시됨) |
| `it.cnt += 1` | 지원(`set o.cnt = o.cnt + 1`) |
| `by[o.status] = x` | `by.set(o.status, x)`와 같음 |
| `by[o.cnt] += 1` | 컴파일되지 않음(의도한 것). `by[o.cnt] = o.cnt + 1`이라고 쓰십시오 |
| `it.cnt++`, `it.cnt--` | **v0.1에 없음**. `+= 1`을 사용하십시오. 오류 메시지가 이를 알려 줍니다 |
| `it.apply { }`, `with(it) { }`, `it.also { }` | **v0.1에 없음**. 직접 대입하십시오. 오류 메시지가 이를 알려 줍니다 |
| 람다 안의 `if` / `when` | **v0.1에 없음**. 대신 `set()`을 여러 번 호출하십시오(U14) |
| 람다 안의 지역 `val` | 거부됨. U3 문제를 숨길 수 있음 |

Kotlin에 대한 자세한 내용: [9. Kotlin 노트](09-kotlin-notes.md).

---

<a id="77-important-rules"></a>
## 7.7 중요한 규칙

<a id="the-right-side-reads-the-old-value"></a>
### 오른쪽은 이전 값을 읽음

SQL에서 SET 오른쪽의 모든 컬럼은 update **이전**의 값을 뜻합니다. lambdaQL은 이 규칙을 따릅니다.

```kotlin
.set { it.price = it.price!! * 2; it.total = it.price }
// 오류: price를 먼저 대입한 뒤 다시 읽음
```

**한 문장 안에서 컬럼에 대입한 뒤 나중에 그 컬럼을 읽으면** lambdaQL은 분석할 때 오류로 멈춥니다(규칙 U3).
이유: 이 경우 MariaDB는 **새** 값을 사용하고, H2와 Oracle은 이전 값을 사용합니다. 결과가 다릅니다(확인함: MariaDB 200, H2와 Oracle 100).

다음 경우는 세 데이터베이스가 모두 같은 결과를 내므로 괜찮습니다.

| 형태 | 괜찮은가? |
|---|---|
| `it.price = it.price!! * 1.1`(자신을 읽음) | 예 |
| `it.total = it.price; it.price = 0.0`(먼저 읽고 나중에 대입) | 예 |
| `it.price = ...; it.total = it.price`(먼저 대입하고 나중에 읽음) | **오류** |

이 검사는 물리 컬럼(임베디드 하위 컬럼, FK 컬럼) 단위로, 여러 `set()` 호출에 걸쳐, 그리고 중첩된 `sub` 람다 안에서도 동작합니다.

<a id="assign-a-column-only-once"></a>
### 컬럼에는 한 번만 대입

같은 컬럼에 두 번 대입하면 오류입니다(규칙 U4). `it.address = ...` 뒤의 `it.address.city = ...`,
또는 `it.customer = ...` 뒤의 `it.customer?.id = ...`도 마찬가지입니다.

<a id="where-or-all-is-required"></a>
### `where`나 `all()`이 필요함

`set()`은 `execute()`가 **없는** 타입을 반환합니다. `where()`와 `all()`만 `execute()`가 있는 타입을 반환합니다.
그래서 `where`를 빠뜨리면 컴파일 오류가 납니다(규칙 U5). `delete`도 마찬가지입니다.

<a id="association-paths-same-rows-as-select"></a>
### 연관 경로: 조회와 같은 행

`where`에서 FK가 아닌 연관 경로는 **상관 `exists`**가 됩니다(규칙 U9).

```kotlin
.where { it.customer?.name == "Kim" }
// → where exists (select 1 from Customer c where c.id = o.customer.id and c.name = 'Kim')
```

SET 오른쪽에서는 **상관 스칼라 서브쿼리**가 됩니다.

```kotlin
it.custName = it.customer?.name
// → set o.custName = (select c.name from Customer c where c.id = o.customer.id)
```

왜일까요? lambdaQL이 경로를 Hibernate에 그대로 넘기면 Hibernate는 데이터베이스마다 다른 조인 SQL을 만들고
(H2는 `merge`, MariaDB는 `update ... join`, Oracle은 인라인 뷰), SET에 경로가 있으면 **고객이 없는 행이 빠졌습니다**
(확인함: `.all()`로 10행 중 9행만 수정됨).
lambdaQL은 서브쿼리를 직접 씁니다. 그래서 `update`와 `delete`는 같은 `where` 람다가 조회에서 반환하는 **바로 그 행**만 건드립니다.
조회는 `where`의 경로에 기본적으로 inner join을 사용하므로(결정 D36), 고객 경로에 대한 `where`는 고객이 없는 주문을 건드리지 않습니다. null 가드가 그런 주문을 남기는 경우는 예외입니다.

`@Id`로 끝나는 경로(`it.customer?.id`)는 FK 컬럼입니다. 서브쿼리도 조인도 없습니다.

<a id="batching-for-many-rows"></a>
### 여러 행의 배치 처리

`values(list) { ... }`는 VALUES 행이 여러 개인 문장 하나를 씁니다(규칙 U13).

| 항목 | 규칙 |
|---|---|
| 분할 단위 | 행 수가 아니라 바인드 값의 개수(행 x 컬럼) |
| 기본 한도 | 문장당 바인드 값 1000개 |
| 반환 값 | 모든 부분의 합계 |
| 트랜잭션 | 분할은 트랜잭션 안에서만 허용 |
| 빈 리스트 | SQL 없음, 0 반환 |

Oracle에는 바인드 값 65,535개라는 절대 한도가 있습니다(ORA-01745). 그 전에 파싱이 매우 느려집니다
(테스트에서 21,845행에 약 6분). 그러니 기본 한도를 유지하십시오.

<a id="the-persistence-context-is-not-updated"></a>
### 영속성 컨텍스트는 갱신되지 않음

벌크 DML은 JPA 영속성 컨텍스트를 거치지 않습니다(규칙 U16).

- Hibernate는 문장 전에 대기 중인 변경을 flush합니다.
- 문장 실행 후, 같은 세션에서 이미 로드한 엔티티에는 **이전 값**이 남아 있습니다. 그 엔티티를 다시 사용한다면 `entityManager.clear()`를 호출하십시오.
- JPA `cascade`와 `orphanRemoval`은 실행되지 **않습니다**. 자식을 먼저 삭제하거나 데이터베이스에서 `ON DELETE CASCADE`를 사용하십시오.

---

<a id="78-all-rules-u1-to-u21-summary"></a>
## 7.8 모든 규칙 U1 ~ U21(요약)

| # | 규칙 |
|---|---|
| U1 | 대입 람다에는 대상, 대상의 임베디드 경로, 또는 연관의 `@Id`에 대한 **대입(setter 호출)만** 들어 있습니다. `if`, `when`, 반복문, 지역 변수, `++`, 스코프 함수와 그 밖의 호출은 오류입니다. 대입이 없는 람다도 오류입니다. |
| U2 | 오른쪽은 `where`의 값 규칙([3. 조건(`where`)](03-where.md))을 따릅니다. 캡처 값은 바인드 파라미터입니다. `a ?: b`는 `coalesce(a, b)`가 됩니다. |
| U3 | 오른쪽은 **이전** 값을 읽습니다. 같은 문장에서 컬럼에 대입한 뒤 그 컬럼을 읽으면 오류입니다. |
| U4 | 같은 물리 컬럼에 두 번 대입하면 오류입니다. `set()` 호출이 달라도 마찬가지입니다. |
| U5 | `where()`나 `all()` 없이는 `execute()`가 없습니다. 컴파일러가 검사합니다. |
| U6 | 선택적이지 않은 컬럼(`@Id`, `nullable=false`, 기본형)에 `null`을 대입하면 오류입니다. 컬렉션이나 연관 엔티티의 `@Id`가 아닌 속성에 대입해도 오류입니다. |
| U7 | 캡처 값은 사용할 때마다 자체 파라미터를 받습니다. (테스트에서 STRING 컬럼과 ORDINAL 컬럼이 enum 파라미터 하나를 공유하자 잘못된 값이 조용히 저장되었습니다.) |
| U8 | `LocalDateTime.now()`는 애플리케이션에서 계산하여 바인드합니다. 데이터베이스 시간(`current_timestamp`)은 지원하지 않습니다. |
| U9 | DML에서 FK가 아닌 연관 경로: `where`에서는 상관 `exists`(조회와 같은 행, D36), SET 오른쪽에서는 상관 스칼라 서브쿼리. DML은 조회와 같은 행을 다룹니다. |
| U10 | `insert ... select`에서 소스 경로는 명시적인 `left join`이 됩니다. |
| U11 | `it.customer = entity`는 엔티티의 `@Id`를 바인드합니다. id가 null이면 바인드가 실패합니다. |
| U12 | IDENTITY와 SEQUENCE id는 생략할 수 있습니다. TABLE 생성기는 `values`에서만 동작하고 `insert ... select`에서는 동작하지 않습니다. 생성기가 없으면 id가 필요합니다. 그 밖의 선택적이지 않은 속성도 필요합니다. |
| U13 | 여러 행은 바인드 개수(기본 1000)로 나눕니다. 결과는 합산합니다. 트랜잭션 안에서만 가능합니다. |
| U14 | `set` 람다 하나로 여러 컬럼에 대입할 수 있고, `set()`은 여러 번 호출할 수 있습니다. 모두 호출 순서대로 SET 목록 하나로 합쳐집니다. |
| U15 | `@Version` 엔티티는 `update versioned`를 사용합니다. 버전에 직접 대입하지 마십시오. `execute() == 0` 확인은 사용자가 할 일입니다. |
| U16 | 벌크 DML은 영속성 컨텍스트를 건너뜁니다. 실행 전 flush, 실행 후 오래된 엔티티, cascade 없음. |
| U17 | DELETE 서브쿼리가 같은 엔티티를 읽으면 lambdaQL은 이를 파생 테이블로 감쌉니다. (그렇지 않으면 MariaDB 오류 1093.) |
| U18 | 상속: SINGLE_TABLE은 완전히 지원합니다. JOINED와 TABLE_PER_CLASS의 update/delete는 임시 테이블을 사용합니다. 그 하위 클래스에 대한 insert는 거부됩니다. |
| U19 | 쿼리 캐시 키에는 진입점, 대상 엔티티, set 람다, where/all, versioned, 분할 크기가 들어갑니다. |
| U20 | `by[column] = value` / `by.set(column, value)`: 컬럼은 대상의 속성 경로여야 합니다. 타입은 분석할 때 검사합니다. 한 람다 안에서 속성 대입과 `by`를 섞지 마십시오. |
| U21 | 각 람다는 한 번 분석되고 캐시됩니다. `set()` 호출 사이의 검사는 캐시된 요약만 비교합니다(프로토타입에서 약 0.1 ~ 0.2 µs). |

---

<a id="79-not-in-v01"></a>
## 7.9 v0.1에 없는 것

| 항목 | 계획 | 지금 할 수 있는 방법 |
|---|---|---|
| 조건부 값(`if (c) a else b`, `when`, 값으로 쓴 토글 `!it.active`) | v0.2(CASE WHEN) | `where`가 다른 update 두 개 |
| 대입을 고르기 위한 람다 안의 `if` | v0.2 | `set()`을 여러 번 호출(U14) |
| `++`, `--`, `apply`, `with`, `also` | v0.2 | `+= 1`이나 직접 대입 사용 |
| 문자열 템플릿이나 `+`(`"${it.description}_x"`) | v0.2 | – |
| `insert ... select`의 명시적 조인 | v0.2 | 다대일 경로 사용 |
| Upsert(`on conflict`) | v0.2 | – |
| `order by ... limit`이 있는 DML | 거부됨 | 먼저 id를 조회한 뒤 id 목록으로 삭제 |
| `returning` | 거부됨 | DML 후 다시 조회 |
| 데이터베이스 시간(`current_timestamp`) | 거부됨 | 애플리케이션 시간을 바인드(U8) |

이 장의 오류는 `[LQL-xxxx]` 코드가 붙은 `LambdaQLException` 하위 클래스입니다. [11. 오류와 언어](../11-errors-and-languages.md)를 참고하십시오.
모든 규칙을 한곳에: [10. 규칙과 제한](10-rules-and-limits.md).

---

[Kotlin 트랙](README.md) | 이전: [6. 조회, 집계, 그룹화](06-select-group.md) | 다음: [8. 프로시저와 DB 함수](08-procedures-functions.md) | [이 페이지의 Java 버전](../java/07-dml.md)
