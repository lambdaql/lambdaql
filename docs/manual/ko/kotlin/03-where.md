<a id="3-conditions-where-kotlin"></a>
# 3. 조건(`where`) (Kotlin)

> [!NOTE]
> **상태**
> - **구현됨, H2·MariaDB·Oracle에서 테스트함:** `where` 조건과 `toList()`(컬럼 매핑, HQL 출력, 쿼리 실행). 테스트는 **Java** 람다를 사용합니다. **Kotlin 람다는 아직 테스트하지 않았습니다.**
> - **설계됨, 아직 구현되지 않음:** 이 장의 Kotlin 전용 형태(`?.`, `?:`, `!!`, 범위, `when`, `in`). 이 형태들의 바이트코드는 프로토타입으로 컴파일하여 확인했고(kotlinc 1.7.22 ~ 2.3.21), 많은 조건을 H2에서 행 단위로 확인했습니다. 서브쿼리, `select`, 프로시저도 아직 설계 단계입니다. 정렬과 페이징의 핵심 기능([4. 정렬과 페이징](04-order-paging.md)), update/insert/delete/upsert([7. 수정, 삽입, 삭제](07-dml.md)), Spring Data JPA 모듈([12. Spring Data JPA](12-spring-data-jpa.md))은 구현되었습니다.
> - 이 장의 HQL은 **예정된** 출력입니다. 세부 사항(예: `e0`, `j1` 같은 별칭 이름)은 아직 조금 바뀔 수 있습니다.

[Kotlin 트랙](README.md) | [매뉴얼 홈](../README.md) | 이전: [2. 시작하기](02-getting-started.md) | 다음: [4. 정렬과 페이징](04-order-paging.md) | [이 페이지의 Java 버전](../java/03-where.md)

공통 페이지: [1. 개요](../01-overview.md) · [11. 오류와 언어](../11-errors-and-languages.md)

---

<a id="31-the-idea-in-one-sentence"></a>
## 3.1 한 문장으로 말하면

**lambdaQL 람다는 코드가 아니라 쿼리입니다.** lambdaQL은 람다의 바이트코드를 읽습니다. 절대 실행하지 않습니다.
그래서 Kotlin의 실행 규칙(`!!`에서 나오는 `NullPointerException`, `===`의 객체 동일성, 컴파일러가 추가하는 null 검사)은 적용되지 않습니다.
lambdaQL은 자체 규칙을 사용합니다. 이 장에서 그 규칙을 설명합니다.

어떤 형태에 대한 규칙이 없으면 lambdaQL은 추측하지 않습니다. 명확한 메시지와 함께 예외를 던집니다([3.13](#313-what-is-rejected) 참고).

<a id="32-example-entities"></a>
## 3.2 예제 엔티티

모든 예제는 다음 엔티티를 사용합니다(전체 코드는 [2. 시작하기](02-getting-started.md#example-entities)에 있음).

```kotlin
@Entity class Order(
    @Id var id: Long? = null,
    var price: Double? = null,
    var status: Int? = null,
    var active: Boolean = false,          // 기본형: 절대 NULL이 아님
    var description: String? = null,
    @ManyToOne var customer: Customer? = null,
    var updateAt: LocalDateTime? = null,
    @OneToMany(mappedBy = "order") var items: MutableList<Item> = mutableListOf(),
)
@Entity class Customer(@Id var id: Long? = null, var name: String? = null, var grade: Int? = null)
@Entity class Item(@Id var id: Long? = null, @ManyToOne var order: Order? = null, var price: Double? = null, var qty: Int = 0)
```

몇몇 예제에는 속성이 하나 더 필요합니다. 그런 예제에는 "`flag: Boolean?`"처럼 표시합니다.

<a id="33-basic-form"></a>
## 3.3 기본 형태

```kotlin
val qb = QueryBuilder(entityManagerFactory)

val list = qb.from(Order::class.java)
    .where { it.status == 1 && it.price!! > 100.0 }
    .toList()
// HQL: select e0 from Order e0 where e0.status = 1 and e0.price > 100.0
```

**SQL 블록 읽는 법.** `…`는 줄여 쓴 select 목록(엔티티의 모든 컬럼)이고, `?`는 바인드 파라미터입니다. 각 줄은 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)에서 나옵니다. 이 테스트는 각 쿼리를 Hibernate 6.6.5로 H2 2.3, MariaDB 10.11, Oracle 23.26에서 실행하며, Hibernate의 SQL이 바뀌면 실패합니다. 이 테스트는 Java 람다를 사용합니다. 의미가 같은 Kotlin 람다는 같은 HQL을 만들므로 SQL도 같습니다.

**데이터베이스별 SQL (Java 테스트 실행 결과, Kotlin도 같은 HQL을 만듦)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `to-hql`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>100.0
```

Kotlin 테스트는 아직 없습니다. 같은 쿼리를 Java로 쓴 것은 [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java)에서 테스트합니다(H2, MariaDB, Oracle). 테스트한 예제는 [이 페이지의 Java 버전](../java/03-where.md)에서 볼 수 있습니다.

`where()`는 여러 번 호출할 수 있습니다. 조건들은 `AND`로 결합됩니다.

```kotlin
qb.from(Order::class.java)
    .where { it.status == 1 }
    .where { it.active }
    .toList()
// HQL: select e0 from Order e0 where e0.status = 1 and e0.active = true
```

**데이터베이스별 SQL (Java 테스트 실행 결과, Kotlin도 같은 HQL을 만듦)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `many-where`:

```sql
-- H2
select …
from orders o1_0
where o1_0.status=1 and o1_0.active=true

-- MariaDB, Oracle
select …
from orders o1_0
where o1_0.status=1 and o1_0.active=1
```

**HQL 안의 값.** 값이 어디에서 왔는지에 따라 규칙이 정해집니다(결정 D37).

| 값 | HQL에서 | 예 |
|---|---|---|
| 람다에 직접 쓴 상수: 숫자(소수 포함), 문자열, `true`/`false`, `null`, enum 상수, `const val` | **리터럴** | `it.price!! > 100.0` → `e0.price > 100.0` |
| 캡처 값: 지역 `val`이나 `var`, 속성, 함수 파라미터 | **바인드 파라미터** | `it.price!! > minPrice` → `e0.price > :p1` |
| 계산한 값: `BigDecimal("1.5")`, `form.text.trim()`, `LocalDateTime.of(...)` | **바인드 파라미터** | `:p1` |

- 상수는 호출할 때마다 같습니다. 그래서 SQL 문장이 바뀌지 않고, 데이터베이스는 쿼리 계획을 세울 때 값을 볼 수 있습니다.
- 문자열 상수 안의 작은따옴표는 escape됩니다: `"O'Brien"`은 `'O''Brien'`이 됩니다. 이 문자열은 사용자 입력이 아니라 클래스에 컴파일된 상수이므로 SQL 주입 위험이 없습니다.
- 캡처 값은 사용자에게서 올 수 있으므로 **항상** 바인드 파라미터입니다. 호출마다 값만 바뀝니다.
- enum 상수는 전체 클래스 이름으로 씁니다. 예: `e0.kind = com.example.OrderKind.GIFT`.

**`!!`는 컴파일러를 위한 것입니다.** `price`가 `Double?`이면 Kotlin은 `it.price > 100.0`을 컴파일하지 않습니다. `it.price!! > 100.0`이라고 쓰십시오. lambdaQL은 `!!`를 무시합니다(결정 D23). `price`가 NULL인 행은 반환되지 않습니다.

<a id="34-comparisons"></a>
## 3.4 비교

| Kotlin | HQL | 참고 |
|---|---|---|
| `it.status == 1` | `e0.status = 1` | |
| `it.status != 1` | `e0.status <> 1` | NULL 행은 반환되지 않음([3.6](#36-null-rules) 참고) |
| `it.price!! > p` | `e0.price > :p1` | `<`, `<=`, `>=`도 마찬가지 |
| `it.updateAt!! > since` | `e0.updateAt > :p1` | Kotlin에서 날짜는 `<`, `>`로 비교 |
| `it.updateAt!!.isAfter(since)` | `e0.updateAt > :p1` | `isBefore`는 `<` |
| `it.description!! < "m"` | `e0.description < 'm'` | `BigDecimal`, `LocalDate`, `BigInteger`도 마찬가지 |
| `it.status!!.compareTo(3) > 0` | `e0.status > 3` | `compareTo(..) op 0`은 `op`가 됨 |

nullable 컬럼과의 비교는 **SQL 의미**를 따릅니다.
컬럼이 NULL이면 결과는 UNKNOWN이고 행은 반환되지 않습니다. (Kotlin 코드를 실행한다면 `!!`가 `NullPointerException`을 던집니다.)

**산술.** `+`, `-`, `*`, 단항 `-`를 지원합니다. `%`는 `mod`로 씁니다.
정수 `/`는 피하십시오. 데이터베이스마다 결과가 다릅니다(MariaDB는 `5/2 = 2.5`, Kotlin은 `2`). `/`의 최종 규칙은 아직 정해지지 않았으며, 첫 버전에서는 거부될 수 있습니다.

**부동소수점:** `Float`/`Double` 컬럼에 대해 lambdaQL은 Kotlin과 비트 단위까지 같은 결과를 약속하지 않습니다.
캡처한 `NaN`이나 `Infinity` 값은 쿼리가 실행될 때 거부됩니다.

## 3.5 AND, OR, NOT

| Kotlin | HQL |
|---|---|
| `a && b` | `a and b` |
| `a \|\| b` | `a or b` |
| `!a` | `not (a)` |
| 괄호 | 필요한 만큼 유지 |

```kotlin
.where { (it.status == 1 || it.active) && it.price!! > minPrice }
// HQL: (e0.status = 1 or e0.active = true) and e0.price > :p1

.where { !(it.status == 1 && it.price!! > minPrice) }
// HQL: not (e0.status = 1 and e0.price > :p1)
```

규칙:

- 결과는 **SQL 3값 논리**(TRUE / FALSE / UNKNOWN)를 따릅니다. lambdaQL은 이 방식이 H2, MariaDB, Oracle에서 같은 행을 반환하는지 확인했습니다.
- 컴파일러는 가끔 `!`의 위치를 옮깁니다(드 모르간, `!(x > y)`를 `x <= y`로). 그래도 결과는 바뀌지 않습니다.
- lambdaQL은 **조건을 단순화하지 않습니다**. `it.price!! > p || it.price!! <= p`라고 쓰면 쓴 그대로 보냅니다. `price`가 NULL인 행은 반환되지 않습니다. 이는 정상적인 SQL 동작입니다.
- `&&`와 `||`를 사용하십시오. 중위 함수 `and`, `or`, `xor`는 거부됩니다(단락 평가를 하지 않음).

**`true`/`false`를 내는 `if`와 `when`.** 다음의 짧은 형태만 허용합니다. 각각 `AND`, `OR`와 같습니다.

| Kotlin | 같은 의미 |
|---|---|
| `if (c) x else false` | `c && x` |
| `if (c) true else x` | `c \|\| x` |
| `when { a -> true; b -> true; else -> false }` | `a \|\| b` |

그 밖의 `if (c) x else y`, 그리고 **값**을 고르는 `if`나 `when`(`it.status == (if (flag) 1 else 2)`)은 거부됩니다(제한 KC6).
선택이 캡처 값에만 달려 있다면 람다 전에 값을 계산하십시오.

```kotlin
val wanted = if (flag) 1 else 2
.where { it.status == wanted }
```

<a id="36-null-rules"></a>
## 3.6 NULL 규칙

가장 중요한 부분입니다. 단순한 원칙 두 가지가 있습니다.

1. **null 검사를 직접 작성한 곳에서는 lambdaQL이 그 검사를 사용합니다.** (`== null`, `!= null`, `?.`, `?:`, 그리고 두 nullable 값 사이의 `==`)
2. **작성하지 않은 곳에서는 lambdaQL이 SQL 의미를 사용합니다.** 컬럼이 NULL이면 비교는 UNKNOWN이 되고 행은 반환되지 않습니다.

Kotlin 컴파일러는 바이트코드에 **숨겨진** null 검사를 추가합니다(예: `status`가 `Int?`일 때 `it.status == 1`). lambdaQL은 이를 **무시합니다**(결정 D22). 소스 코드에 있는 null 검사만 의미가 있습니다.
그래서 의미가 같은 Kotlin 람다와 Java 람다는 같은 SQL을 만듭니다.

<a id="361-rule-table"></a>
### 3.6.1 규칙 표

| # | Kotlin 형태 | HQL | NULL 동작 |
|---|---|---|---|
| R12 | nullable 컬럼과 상수 또는 non-null 값: `it.status == 1`, `it.id == id`(`id: Long`) | `e0.status = 1`, `e0.id = :p1` | SQL 의미. 컬럼이 NULL이면 행이 반환되지 않음 |
| R12 | `it.status != 1` | `e0.status <> 1` | SQL 의미. 컬럼이 NULL이면 행이 반환되지 **않음** |
| R1 | 크기 비교: `it.price!! > p`, `it.status!! <= 3` | `e0.price > :p1`, `e0.status <= 3` | SQL 의미 |
| R11 | 둘 다 NULL일 수 있는 두 값: `it.status == q`(`q: Int?`), `it.description == text`(`String?`), enum `==` | `e0.status is not distinct from :p1` | **null-safe 값 비교.** `null == null`은 true. 한쪽만 null이면 false |
| R11 | `it.status != q`(둘 다 nullable) | `e0.status is distinct from :p1` | null-safe. `q`가 null이 아니면 NULL 행이 반환**됨** |
| R3 | `x == null`, `x != null` | `x is null`, `x is not null` | |
| R17 | `===`, `!==` | `==`, `!=`와 같음 | 객체 동일성이 아니라 값 비교 |
| R9 | 캡처 값 | 바인드 파라미터 | [3.6.4](#364-captured-null-values) 참고 |

<a id="362--compares-values"></a>
### 3.6.2 `==`는 값을 비교

Kotlin의 `==`는 원래 값을 비교합니다. lambdaQL에서는 `===`도 **값**을 비교합니다(규칙 R17).
쿼리에는 객체 동일성이 없습니다. 값이 같은 서로 다른 두 `Long` 박스는 같습니다.

```kotlin
val status: Int? = request.status    // null일 수 있음
.where { it.status == status }
// HQL: e0.status is not distinct from :p1
//   status = 3    -> status가 3인 행
//   status = null -> status가 NULL인 행
```

Hibernate가 데이터베이스별로 `is not distinct from`을 쓰는 방법(확인함):

| 데이터베이스 | SQL |
|---|---|
| H2 | `status is not distinct from ?` |
| MariaDB | `status <=> ?` |
| Oracle | `decode(status, ?, 0, 1) = 0` |

한쪽이 절대 NULL이 될 수 없다는 것을 lambdaQL이 알 때는 대신 단순한 `=`를 씁니다. 결과는 같고, 데이터베이스는 인덱스를 사용할 수 있습니다([10.2](10-rules-and-limits.md#102-sargable-rule-index-friendly-sql) 참고).
"절대 NULL이 아님"은 `@Id`, `nullable = false` 컬럼, 기본형 속성(`Int`, `Long`, `Double`, `Boolean`), 또는 쿼리가 실행될 때 null이 아닌 캡처 값을 뜻합니다.

```kotlin
.where { it.description == text }    // 쿼리가 실행될 때 text는 null이 아님
// HQL: e0.description = :p1
.where { it.description != text }
// HQL: (e0.description <> :p1 or e0.description is null)
```

**NULL이 될 수 없는 컬럼: `it.id == id`.** 예제 엔티티의 `id`는 `Long?`입니다. Kotlin은 `it.id == id`(캡처한 `id: Long`)를 null 검사와 비교로 컴파일합니다.

이 바이트코드를 그대로 옮기면 `not (e0.id is null or e0.id <> :p1)`이 됩니다. 결과는 맞지만 데이터베이스가 인덱스를 제대로 사용하기 어렵습니다.
id는 NULL이 될 수 없으므로 id의 `is null`은 항상 거짓입니다. lambdaQL은 이 검사를 제거하고 `e0.id = :p1`로 씁니다.

기본형 속성과 `nullable = false` 컬럼에도 같은 규칙을 적용합니다. 단, 경로에 **연관 단계가 없을 때만** 적용합니다. 고객이 없는 주문이 있으므로 `it.customer?.id`는 NULL이 될 수 있고, 이 경로의 null 검사는 유지합니다.

```kotlin
.where { it.id == id }              // id: Long (captured)
// HQL: e0.id = :p1
.where { it.id == null }
// HQL: 1=0
.where { it.customer?.id == null }
// HQL: e0.customer.id is null
```

같은 HQL을 Java 람다로 [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java)에서 테스트했습니다(영역 `never-null`, [이 페이지의 Java 버전](../java/03-where.md#362--on-objects-compares-values) 참고). Java 테스트는 Kotlin의 `it.id == id`가 컴파일된 형태의 조건을 사용합니다.

> [!WARNING]
> **Kotlin의 non-null 타입은 증거가 되지 않습니다.** `var code: String` 속성에도 데이터베이스에서 온 NULL이 들어 있을 수 있습니다.
> Hibernate가 검사 없이 컬럼 값을 필드에 쓰기 때문입니다.
> lambdaQL은 Kotlin 타입이 아니라 JPA 메타데이터를 봅니다.

<a id="363-write-your-null-checks-yourself"></a>
### 3.6.3 null 검사는 직접 작성

```kotlin
// status가 NULL이 아니고 1도 아닌 행만:
.where { it.status != 1 }
// HQL: e0.status <> 1

// status가 NULL인 행도 포함:
.where { it.status == null || it.status != 1 }
// HQL: e0.status is null or e0.status <> 1
```

> [!IMPORTANT]
> Kotlin 코드를 실행하는 것과는 다릅니다. Kotlin에서 `null != 1`은 `true`입니다.
> lambdaQL에서 `it.status != 1`은 NULL 행을 반환하지 **않습니다**. null 검사를 작성하지 않았기 때문입니다.
> `1` 같은 상수는 nullable이 아닙니다. 같은 조건을 Java로 써도 같은 SQL이 나옵니다.

상수와 nullable 변수를 비교해 봅니다.

```kotlin
val q: Int? = 3
.where { it.status != 3 }    // e0.status <> 3                                 NULL 행 반환 안 됨
.where { it.status != q }    // (e0.status <> :p1 or e0.status is null)        NULL 행 반환됨
```

두 번째 형태는 둘 다 NULL일 수 있는 두 값을 비교합니다. Kotlin이 null에 대한 결과를 정의하므로 lambdaQL은 그 결과를 유지합니다(null-safe).

`String?`에 대한 `it.description.equals(text)`는 `==`와 같습니다(null-safe).

`?.`, `?:`, `!!`에 대한 자세한 내용: [9. Kotlin 노트](09-kotlin-notes.md).

<a id="364-captured-null-values"></a>
### 3.6.4 null 캡처 값

| 형태 | 캡처 값이 `null`일 때 |
|---|---|
| null-safe 형태: `it.status == q`, `it.description != text` | 허용 |
| 크기 비교: `it.price!! > min!!`, 모든 `<`, `<=`, `>`, `>=` | 쿼리가 실행될 때 **예외** |
| `in`에 사용하는 컬렉션 안의 null 요소 | 쿼리가 실행될 때 **예외** |

이유: Kotlin이라면 여기서 `NullPointerException`을 던집니다(`min!!`). 이를 `IS NULL`로 바꾸면 Kotlin이라면 절대 반환하지 않을 행을 반환하게 됩니다.

**규칙의 예외: null 가드.** 같은 캡처 값을 먼저 null 검사하면 null을 허용합니다.

```kotlin
val min: Double? = request.minPrice   // null일 수 있음
.where { min == null || it.price!! > min }
// min == null:  select e0 from Order e0                        (필터 없음)
// min = 100.0:  select e0 from Order e0 where e0.price > :p1
```

**데이터베이스별 SQL (Java 테스트 실행 결과, Kotlin도 같은 HQL을 만듦)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `captured-null-guard`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
```

**데이터베이스별 SQL (Java 테스트 실행 결과, Kotlin도 같은 HQL을 만듦)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `captured-null-guard#2`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.price>?
```

동작 방식:

- 캡처 값과 상수만 사용하는 부분은 쿼리가 실행될 때 애플리케이션에서 결정합니다. Kotlin처럼 왼쪽에서 오른쪽으로, 단락 평가(short-circuit)로 처리합니다.
- `min == null`이면 가드가 참이므로 조건 전체가 참입니다. 필터가 없습니다. `min`과의 비교는 HQL에 쓰지 않습니다(Kotlin도 이 비교를 실행하지 않습니다).
- `min = 100.0`이면 가드가 거짓이므로 `e0.price > :p1`만 남습니다.
- 람다는 여전히 한 번만 분석합니다. 값에 따라 달라지는 것은 HQL 텍스트뿐입니다.
- 가드가 먼저 와야 합니다. `min == null`일 때 `it.price!! > min!! || min == null`은 쿼리가 실행될 때 예외를 던집니다. Kotlin의 `NullPointerException`과 같습니다.

이에 대한 Kotlin 테스트는 아직 없습니다. 같은 가드를 Java로 쓴 것은 테스트했습니다. [이 페이지의 Java 버전](../java/03-where.md#364-captured-null-values)을 참고하십시오.

<a id="37-boolean-columns"></a>
## 3.7 boolean 컬럼

예제 엔티티에서 `active`는 Kotlin `Boolean`입니다(JVM 기본형, 절대 NULL이 아님).
`flag`가 나오는 행은 nullable 컬럼 `var flag: Boolean? = null`의 경우입니다.

| Kotlin | HQL | NULL 행 |
|---|---|---|
| `it.active` | `e0.active = true` | –(절대 NULL이 아님) |
| `!it.active` | `e0.active = false` | – |
| `it.flag == true` | `e0.flag is not distinct from true` | 반환 안 됨 |
| `it.flag != true` | `e0.flag is distinct from true` | **반환됨**(Kotlin과 같음) |
| `it.flag ?: false` | `(e0.flag is not null and e0.flag = true)` | 반환 안 됨 |
| `!(it.flag ?: false)` | `(e0.flag is null or not (e0.flag = true))` | **반환됨** |
| `it.flag!!` | `e0.flag = true` | 반환 안 됨(`!!`는 무시됨) |

참고:

- lambdaQL은 `is not true`나 `coalesce(flag, false)`를 사용하지 않습니다. Oracle에서 실패합니다(ORA-00932).
- 더 큰 조건의 부정은 항상 `not (...)`로 씁니다. lambdaQL은 조건을 `(...) = false`나 `(...) is null`로 감싸지 않습니다. Oracle 23이 `WHERE`에서 이를 잘못 평가하기 때문입니다.
- `it.flag ?: false`는 **조건 안에서는** 괜찮습니다. 값이나 정렬 키로 쓰면 거부됩니다(제한 KC21).

**두 boolean 값의 비교**(`==`/`!=`)를 지원합니다(결정 D25).

```kotlin
.where { it.active == (it.status!! > s) }
// HQL: (e0.active = true and e0.status > :p1) or (e0.active = false and e0.status <= :p1)
```

lambdaQL은 이를 `CASE`가 아닌 `AND`/`OR`로 쓰므로 데이터베이스가 인덱스를 사용할 수 있습니다.
양쪽이 같은 조건을 공유하면(예: `it.active == it.active`) 그 형태는 거부됩니다.

<a id="38-strings"></a>
## 3.8 문자열

- 문자열에 대한 `==`와 `equals`는 **데이터베이스 콜레이션**을 사용합니다.
  - MariaDB의 `_ci` 콜레이션은 대소문자와 끝의 공백을 무시합니다.
  - Oracle은 `''`(빈 문자열)을 NULL로 저장합니다. 그래서 Oracle에서 `it.description == ""`는 Kotlin과 다르게 동작합니다.
- 컬럼에 대한 Kotlin 문자열 함수(`startsWith`, `endsWith`, `contains`, `lowercase()`, `length`, `isNullOrEmpty()`, `ignoreCase = true`)에는 Kotlin용 설계가 있습니다(규칙 R21). [9. Kotlin 노트](09-kotlin-notes.md#97-strings)를 참고하십시오.
- 컬럼에 대한 `isBlank()`, `isNullOrBlank()`, `trim()` 및 다른 stdlib 함수는 거부됩니다(제한 KC9, KC20).
- **캡처 값만** 사용하는 문자열 템플릿은 동작하도록 설계되어 있습니다. `"$a-$b" == it.description`은 `:p1 = e0.description`이 되고, 문자열은 애플리케이션에서 만듭니다. 하지만 현재 분석 코어는 람다 안의 모든 문자열 결합을 거부하므로, 지금은 람다 전에 문자열을 만드십시오.
- **엔티티 값**을 사용하는 문자열 템플릿이나 `+`(`"${it.description}-x"`)는 아직 지원하지 않습니다(KC17, 계획 중).

<a id="39-in-with-a-captured-collection"></a>
## 3.9 캡처한 컬렉션을 사용한 IN

컬렉션을 람다 **밖에서** 만들고 `in`을 사용하십시오.

```kotlin
val ids = listOf(1L, 2L, 3L)

qb.from(Order::class.java).where { it.id in ids }.toList()
// HQL: e0.id in (:p1)

qb.from(Order::class.java).where { it.id !in ids }.toList()
// HQL: e0.id not in (:p1)
```

**데이터베이스별 SQL (Java 테스트 실행 결과, Kotlin도 같은 HQL을 만듦)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `not-in`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.id not in (?,?)
```

`!in`은 `not in`이 됩니다. id는 NULL이 될 수 없으므로 `is null` 부분이 없습니다. NULL이 될 수 있는 컬럼에는 lambdaQL이 `x is null or`를 추가합니다. 목록에 `null`이 없으면 Kotlin의 `!in`은 NULL 값에 대해 true이기 때문입니다.

| 경우 | 결과 |
|---|---|
| 컬렉션이 캡처한 `List`, `Set` 또는 다른 `Collection` | 지원 |
| 컬렉션에 `null` 요소가 있음 | 쿼리가 실행될 때 예외 |
| 빈 컬렉션 | `in`은 FALSE, `!in`은 TRUE(Hibernate는 `1=0` / `1=1`을 씀) |
| 요소 1,500개 | H2, MariaDB, Oracle 23ai에서 문장 하나로 동작함(이전 Oracle 버전은 1,000개 제한이 있음. 테스트하지 않음) |
| 컬렉션이 엔티티 컬렉션: `item in it.items` | **거부됨**. `exists()` 서브쿼리를 사용하십시오([5. 서브쿼리](05-subquery.md)) |
| 람다 안에서 만든 컬렉션: `it.status in listOf(1, 2)` | **거부됨**(KC8). 밖에서 만들어 캡처하십시오 |

Kotlin에는 범위(`it.status in 1..5`)와 상수를 사용한 `when`도 있습니다. [3.14](#314-ranges-kotlin-only)와 [3.15](#315-when-kotlin-only)를 참고하십시오.

<a id="310-association-paths"></a>
## 3.10 연관 경로

`it.customer?.name`은 **실행되지 않습니다**. "조인한 고객의 `name` 컬럼"이라는 컬럼을 가리킬 뿐입니다.
lambdaQL은 JPA Metamodel로 각 단계를 확인합니다.

| 경로 단계 | HQL | 예 |
|---|---|---|
| to-one 연관(`@ManyToOne`, `@OneToOne`) | 별칭이 붙은 `join`(기본은 inner, 아래 참고) | `it.customer?.name` -> `join e0.customer j1` ... `j1.name` |
| 임베디드(`@Embedded`) | 같은 테이블, 조인 없음 | `Order`에 임베디드 `address`가 있다면: `it.address.city` -> `e0.address.city` |
| 대상의 `@Id`로 끝나는 경로 | 외래 키 컬럼, 조인 없음 | `it.customer?.id` -> `e0.customer.id` |
| 연관 자체를 `null`과 비교 | 외래 키 컬럼, 조인 없음 | `it.customer == null` -> `e0.customer is null` |
| 컬렉션(`@OneToMany`, `@ManyToMany`) | **거부됨** | `it.items...` -> `exists()` 서브쿼리 사용 |

`it.customer?.name`, `it.customer!!.name`, 그리고 (Java 엔티티라면) `it.customer.name`은 모두 **같은 컬럼** `j1.name`을 뜻합니다.

`where`에서 연관 경로는 기본적으로 **inner join**입니다(결정 D36).

```kotlin
qb.from(Order::class.java)
    .where { it.customer?.name == name }
    .toList()
// HQL: select e0 from Order e0 join e0.customer j1 where j1.name = :p1
```

**데이터베이스별 SQL (Java 테스트 실행 결과, Kotlin도 같은 HQL을 만듦)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `inner-join`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name=?
```

- **고객이 없는 주문**은 결과에 없습니다. 조인할 고객 행이 없기 때문입니다.
- lambdaQL은 null 검사를 대신 추가하지 않습니다. 경로 앞에 `it.customer != null &&`를 쓸 필요가 없습니다.

**null 가드는 고객이 없는 주문을 남깁니다.** 고객이 없는 주문에서 조건이 참이 될 수 있으면 lambdaQL은 그 경로에 `left join`을 사용합니다.
inner join을 쓰면 Kotlin과 SQL의 결과 행이 달라집니다. Kotlin은 그 주문을 반환하지만 inner join은 그 주문을 잃습니다(결정 D36).

```kotlin
.where { it.customer == null || it.customer?.name == name }
// HQL: select e0 from Order e0 left join e0.customer j1 where e0.customer is null or j1.name = :p1

.where { it.customer != null && it.customer?.name == name }
// HQL: select e0 from Order e0 join e0.customer j1 where e0.customer is not null and j1.name = :p1
```

**데이터베이스별 SQL (Java 테스트 실행 결과, Kotlin도 같은 HQL을 만듦)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `null-guard`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.id is null or c1_0.name=?
```

**데이터베이스별 SQL (Java 테스트 실행 결과, Kotlin도 같은 HQL을 만듦)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `null-guard#2`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.id is not null and c1_0.name=?
```

`left join` 뒤에서 Hibernate는 `e0.customer is null`을 `c1_0.id is null`로 씁니다. 의미는 같습니다. 고객이 없는 주문입니다.

- `||`의 다른 부분도 주문을 남길 수 있습니다. `it.status == 1 || it.customer?.name == name`도 `left join`을 사용합니다. Kotlin에서 status가 1인 주문은 고객이 없어도 조건에 맞기 때문입니다.
- 다른 `where` 호출이 어차피 그런 주문을 걸러 낸다면(예: `.where { it.customer?.name != null }`) 조인은 inner로 남습니다.
- 이에 대한 Kotlin 테스트는 아직 없습니다. 같은 조건을 Java로 쓴 것은 테스트했습니다. [이 페이지의 Java 버전](../java/03-where.md#310-association-paths)을 참고하십시오.

고객이 **없는** 주문을 찾으려면 연관 자체를 비교하십시오. 조인 없이 외래 키 컬럼을 검사합니다.

```kotlin
.where { it.customer == null }
// HQL: select e0 from Order e0 where e0.customer is null
```

**데이터베이스별 SQL (Java 테스트 실행 결과, Kotlin도 같은 HQL을 만듦)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `no-association`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id is null
```

> [!WARNING]
> **`?.`를 써도 고객 없는 주문은 돌아오지 않습니다.** Kotlin에서 `it.customer?.name == null`은 고객이 없을 때 `true`입니다.
> lambdaQL에서는 여기서 경로가 inner join이므로 `it.customer?.name == null`은 고객의 이름이 없는 주문만 반환합니다.
> 고객 없는 주문도 포함하려면 `it.customer == null || it.customer?.name == null`이라고 쓰십시오. 이 null 가드가 경로를 `left join`으로 만듭니다(위 참고).

추가 사항:

- 같은 경로는 별칭 하나를 받습니다. 이 별칭은 `where`, `orderBy`, `select`가 함께 사용합니다.
- `it.customer?.id == 5L`은 외래 키 컬럼 `e0.customer.id`를 사용합니다. 조인이 필요 없습니다.
- `orderBy`나 `select`에**만** 쓰인 경로는 `left join`이므로 그곳에서는 행을 잃지 않습니다(규칙 S8, J1). 같은 경로를 `where`에서도 쓰면 `orderBy`와 `select`는 `where`의 조인을 사용합니다. null 가드가 `left join`으로 만들지 않았다면 inner join입니다.
- 두 엔티티의 비교(`it.customer == c`)는 **계획 중**입니다(KC13). 지금은 id를 비교하십시오: `it.customer?.id == c.id`(여기서 `c`는 캡처한 객체).

<a id="311-captured-values"></a>
## 3.11 캡처 값

람다는 밖의 값을 사용할 수 있습니다. 지역 값, 속성, 캡처한 객체의 속성입니다.

```kotlin
val since = LocalDateTime.of(2026, 1, 1, 0, 0)
.where { it.updateAt!! > since }                 // 지역 val
.where { it.status == defaultStatus }            // 바깥 클래스의 속성
.where { it.price!! > filter.minPrice!! }        // 캡처한 객체의 속성
.where { it.updateAt!! > LocalDateTime.of(2026, 1, 1, 0, 0) }   // 엔티티를 사용하지 않는 호출
```

| 캡처한 것 | lambdaQL의 사용 방법 |
|---|---|
| `val`(`Long?`, `String`, ...도 마찬가지) | 바인드 파라미터 |
| `var` | 바인드 파라미터. 값은 **쿼리가 실행될 때마다** 읽음 |
| 바깥 클래스의 속성(`private`도 마찬가지) | 쿼리가 실행될 때마다 읽음 |
| `companion object`의 `val`, 최상위 `val`, `object`의 `val` | 바인드 파라미터 |
| `const val` | HQL에 리터럴로 씀(D37) |
| 람다 안에서 캡처 변수에 쓰기(`n++`) | **거부됨**(KC12) |

규칙:

- 엔티티를 사용하지 않는 부분은 **쿼리가 실행될 때마다** 애플리케이션에서 계산합니다. 그 결과가 바인드 파라미터가 됩니다.
- 람다는 한 번 분석되고 결과는 캐시됩니다. 새 캡처 값은 호출할 때마다 읽습니다.
- 엔티티 부분만 SQL이 됩니다.

<a id="312-errors-that-depend-on-order-of-evaluation"></a>
## 3.12 평가 순서에 따라 달라지는 오류

SQL은 `AND`/`OR`를 왼쪽에서 오른쪽으로 평가한다고 약속하지 않습니다. 그래서 다음과 같은 "가드"는 SQL에서 안전하지 않습니다.

```kotlin
qb.from(Item::class.java)
    .where { it.qty != 0 && it.price!! / it.qty > 2 }   // 거부됨
```

가드 아래의 `/`, `%`, 캐스트는 거부됩니다. 나눗셈 없이 조건을 작성하십시오. 예: `it.qty > 0 && it.price!! > 2.0 * it.qty`(`qty`가 양수일 때 같은 행).

<a id="313-what-is-rejected"></a>
## 3.13 거부되는 것

lambdaQL은 코드 `[LQL-xxxx]`와 소스 줄 번호가 담긴 예외를 던집니다. 추측하지 않습니다.
대부분의 모양은 람다를 분석할 때(처음 사용할 때) 발견됩니다. 잘못된 캡처 값(null, NaN)은 쿼리가 실행될 때 발견됩니다.
[11. 오류와 언어](../11-errors-and-languages.md)를 참고하십시오.

| 형태 | 예 | 해결 방법 |
|---|---|---|
| 지역 `val`/`var`, 반복문, 여러 문장이 있는 블록 | `{ val x = 1; it.status == x }` | 식 하나로 작성 |
| 값 `if`/`when`(KC6) | `it.status == (if (flag) 1 else 2)` | 람다 전에 값을 계산 |
| 일반 `if (c) x else y`(KC6) | | [3.5](#35-and-or-not)의 허용된 형태 사용 |
| 중위 `and`, `or`, `xor` | `it.active and (it.status == 1)` | `&&`, `\|\|` 사용 |
| 엔티티 값을 사용하는 문자열 템플릿이나 `+`(KC17) | `it.description == "A" + it.status` | 컬럼을 따로 비교(계획 중) |
| 람다 안에서 만든 컬렉션(KC8) | `it.status in listOf(1, 2)` | 밖에서 만들어 캡처 |
| 람다 안의 람다 | `ids.any { id -> id == it.id }` | `in`([3.9](#39-in-with-a-captured-collection))이나 서브쿼리 사용 |
| 지원하지 않는 stdlib 함수(KC9, KC20) | `it.description?.trim()`, `isBlank()` | `isNullOrEmpty()`나 직접 비교 |
| 직접 만든 non-inline 확장 함수(KC11) | `it.isOpen()` | `inline fun`으로 만들거나 조건을 직접 작성 |
| 엔티티의 다른 메서드 | `it.calcSomething()` | 속성만 사용 |
| 엔티티 컬렉션 경로 | `it.items.size > 0` | `exists()` 서브쿼리 |
| 엔티티와 엔티티 비교(KC13) | `it.customer == c` | id 비교(엔티티 비교는 계획 중) |
| 가드 아래의 `/`, `%`, 캐스트 | [3.12](#312-errors-that-depend-on-order-of-evaluation) 참고 | 가드를 없애거나 조건을 바꿈 |
| 크기 비교의 null 캡처 값 | `min == null`일 때 `it.price!! > min!!` | null 가드 추가 |
| `NaN` / `Infinity` 캡처 값 | `it.price!! > Double.NaN` | NaN을 보내지 않음 |
| `isNaN()`(KC14) | `it.price!!.isNaN()` | – |
| 캡처 변수에 쓰기(KC12) | `{ n++; it.active }` | 람다 밖에서 계산 |
| 익명 객체, 함수 타입 값(KC4, KC5) | `val f: (Order) -> Boolean`으로 `where(f)` | 람다 리터럴 전달 |

예외 타입(모두 `LambdaQLException`을 상속):

| 예외 | 발생 시점 |
|---|---|
| `LambdaAnalysisException` | 람다를 읽을 수 없을 때(직렬화 가능한 람다가 아님, 클래스로 컴파일됨 등) |
| `UnsupportedLambdaShapeException` | 지원하지 않는 모양일 때(반복문, 값 `if`, ...) |
| `TranslationException` | 의미를 번역할 수 없을 때(알 수 없는 함수, 컬렉션 경로, ...) |

Kotlin의 모든 제한: [9. Kotlin 노트](09-kotlin-notes.md#914-kotlin-limits-kc1-to-kc21). 모든 규칙: [10. 규칙과 제한](10-rules-and-limits.md).

<a id="314-ranges-kotlin-only"></a>
## 3.14 범위(Kotlin 전용)

다음 형태를 모두 지원합니다(결정 D21).

```kotlin
.where { it.status in 1..5 }          // e0.status between 1 and 5
.where { it.status !in 1..5 }         // not (e0.status between 1 and 5)
.where { it.status in 1 until 5 }     // e0.status >= 1 and e0.status < 5
.where { it.status in 1..<5 }         // e0.status >= 1 and e0.status < 5
.where { it.status in lo..hi }        // e0.status between :p1 and :p2   (lo, hi는 캡처 값)
.where { it.price!! in 1.0..10.0 }    // e0.price between 1.0 and 10.0
.where { it.status in 5 downTo 1 }    // 지원함(1..5와 같은 행)
```

| 범위 형태 | 지원 |
|---|---|
| `a..b`, `a until b`, `a..<b`, `a downTo b` | 예 |
| `!in` | 예, `not (...)` |
| `LocalDate`, `LocalDateTime`, `BigDecimal`, `String` 범위 | 예(`compareTo`로 컴파일됨) |
| `step` | 아니요 |

**nullable 대상.** Kotlin은 `Int?`와 `Long?`에 대해 `in`을 받아들입니다. lambdaQL은 null 가드를 추가하지 **않습니다**(D22).
NULL인 행은 반환되지 않으며, `!in`에서도 반환되지 않습니다(SQL 규칙).
다른 nullable 타입(예: `LocalDateTime?`)에 대해서는 Kotlin이 `in`을 컴파일하지 않으므로 `!!`를 붙이십시오.

```kotlin
.where { it.updateAt!! in from..to }  // e0.updateAt between :p1 and :p2
```

<a id="315-when-kotlin-only"></a>
## 3.15 `when`(Kotlin 전용)

`when`은 **조건으로서** 지원합니다. 모든 분기는 `true`나 `false`(또는 조건)로 끝나야 합니다.
lambdaQL은 이를 `IN`, `OR`, `AND`로 바꿉니다.

```kotlin
.where { when (it.status) { 1, 2 -> true; else -> false } }
// e0.status in (1, 2)

.where { when (it.description) { "a", "b", "c" -> true; else -> false } }
// e0.description in ('a', 'b', 'c')

.where { when (it.status) { in 1..5 -> true; else -> false } }
// e0.status between 1 and 5

.where { when { it.status == 1 -> true; it.active -> true; else -> false } }
// e0.status = 1 or e0.active = true

.where { when (it.status) { 1 -> it.active; else -> false } }
// e0.status = 1 and e0.active = true
```

enum 대상도 동작합니다(여기서는 추가 속성 `kind: OrderKind?`를 쓰고, `OrderKind`는 패키지 `com.example`에 있음).

```kotlin
.where { when (it.kind) { OrderKind.EXPRESS, OrderKind.GIFT -> true; else -> false } }
// e0.kind in (com.example.OrderKind.EXPRESS, com.example.OrderKind.GIFT)
```

nullable 대상에는 null 가드가 붙지 않습니다(D22). 대상이 NULL인 행은 반환되지 않으며,
`NOT` 아래에서도 마찬가지입니다. Kotlin 코드를 실행한다면 `else`로 가겠지만, lambdaQL은 SQL 의미를 사용합니다. 의도한 규칙입니다.

**값을 고르는** `when`(또는 `if`)은 지원하지 않습니다(KC6).

```kotlin
// 지원하지 않음
.where { (if (it.active) it.price else 0.0)!! > 10.0 }
```

대신 `&&`와 `||`로 조건을 작성하십시오.

---

[Kotlin 트랙](README.md) | 이전: [2. 시작하기](02-getting-started.md) | 다음: [4. 정렬과 페이징](04-order-paging.md) | [이 페이지의 Java 버전](../java/03-where.md)
