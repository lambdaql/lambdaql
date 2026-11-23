<a id="3-conditions-where-kotlin"></a>
# 3. 조건(`where`) (Kotlin)

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

모든 예제는 lambdaQL 저장소의 테스트 엔티티(Java 클래스)를 사용합니다. 자세한 내용은 [2. 시작하기](02-getting-started.md#example-entities)에 있습니다.

| 엔티티 | 이 장에서 쓰는 속성(Java 타입) |
|---|---|
| `Order` | `Long id`, `double price`, `short status`, `boolean active`(Kotlin에서 `isActive`), `String description`, `Integer cnt`, `String custName`, `LocalDateTime updateAt`, `Kind kind`, `Customer customer`, `Address address` |
| `Customer` | `int id`, `String name`, `Integer grade` |
| `Item` | `Long id`, `Order order`, `Double price`, `int qty` |
| `Coded` | `Long id`, `String code`(`@Convert`로 뒤집어 저장), `Boolean vip`(NULL 가능, `YesNoConverter`) |

`price`, `status`, `active`, `qty`는 기본형이라 NULL이 될 수 없습니다. 참조 타입 속성은 NULL일 수 있고, Kotlin에서는 플랫폼 타입입니다.
`status`는 `Short`이므로 예제는 `it.status.toInt() == 1`로 씁니다. lambdaQL은 이 변환을 무시합니다.

<a id="33-basic-form"></a>
## 3.3 기본 형태

[`ManualKotlinStartTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinStartTest.kt), 영역 `first-query`:

```kotlin
val q = qb.from(Order::class.java).where { it.status.toInt() == 1 && it.price > 100.0 }
assertEquals("select e0 from Order e0 where e0.status = 1 and e0.price > 100.0", q.toHql().hql())
val orders: List<Order> = q.toList()
```

`qb`는 `QueryBuilder`입니다([2. 시작하기](02-getting-started.md#create-a-querybuilder) 참고).

**SQL 블록 읽는 법.** `…`는 줄여 쓴 select 목록(엔티티의 모든 컬럼)이고, `?`는 바인드 파라미터입니다. 각 줄은 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)에서 나옵니다. 이 테스트는 각 쿼리를 Hibernate 7.4로 H2 2.3, MariaDB 10.11, Oracle 23.26에서 실행하며, Hibernate의 SQL이 바뀌면 실패합니다. 이 테스트는 Java 람다를 사용합니다. 의미가 같은 Kotlin 람다는 같은 HQL을 만들므로 SQL도 같습니다.

**데이터베이스별 SQL (Java 테스트 실행 결과, Kotlin도 같은 HQL을 만듦)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `to-hql`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>100.0
```

[`KotlinSelectTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinSelectTest.kt), 영역 `captured`:

```kotlin
val q = qb.from(Order::class.java).where { it.price > min && it.status.toInt() == 1 }
assertEquals("select e0 from Order e0 where e0.price > :p1 and e0.status = 1", q.toHql().hql())
```

`min`은 캡처한 지역 `val`(`100.0`)이므로 바인드 파라미터가 됩니다. `price`는 기본형 `double`이므로 `!!`가 필요 없습니다.
더 많은 쿼리를 Java로 쓴 것은 [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java)에서 테스트합니다(H2, MariaDB, Oracle). 그 예제는 [이 페이지의 Java 버전](../java/03-where.md)에서 볼 수 있습니다.

`where()`는 여러 번 호출할 수 있습니다. 조건들은 `AND`로 결합됩니다.

[`ManualKotlinWhereTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt), 영역 `many-where`:

```kotlin
val q = qb.from(Order::class.java)
    .where { it.status.toInt() == 1 }
    .where { it.isActive }
assertEquals("select e0 from Order e0 where e0.status = 1 and e0.active = true", q.toHql().hql())
```

**데이터베이스별 SQL (Java 테스트 실행 결과, Kotlin도 같은 HQL을 만듦)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `many-where`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.active=true
```

**HQL 안의 값.** 값이 어디에서 왔는지에 따라 규칙이 정해집니다(결정 D37).

| 값 | HQL에서 | 예 |
|---|---|---|
| 람다에 직접 쓴 상수: 숫자(소수 포함), 문자열, `true`/`false`, `null`, enum 상수, `const val` | **리터럴** | `it.price > 100.0` → `e0.price > 100.0` |
| 캡처 값: 지역 `val`이나 `var`, 속성, 함수 파라미터 | **바인드 파라미터** | `it.price > minPrice` → `e0.price > :p1` |
| 계산한 값: `BigDecimal("1.5")`, `form.text.trim()`, `LocalDateTime.of(...)` | **바인드 파라미터** | `:p1` |

- 상수는 호출할 때마다 같습니다. 그래서 SQL 문장이 바뀌지 않고, 데이터베이스는 쿼리 계획을 세울 때 값을 볼 수 있습니다.
- 문자열 상수 안의 작은따옴표는 escape됩니다: `"O'Brien"`은 `'O''Brien'`이 됩니다. 이 문자열은 사용자 입력이 아니라 클래스에 컴파일된 상수이므로 SQL 주입 위험이 없습니다.
- 캡처 값은 사용자에게서 올 수 있으므로 바인드 파라미터입니다. 호출마다 값만 바뀝니다.
- 예외는 하나입니다. 정수 컬럼과 비교하는 소수 캡처 값은 소수 리터럴(`2.5BD`)로 씁니다([3.4](#34-comparisons)의 "정수 컬럼과 소수 값"). 숫자로 바꾼 값만 쓰므로 SQL 주입 위험은 없습니다.
- enum 상수는 전체 클래스 이름으로 씁니다. 예: `e0.kind = org.lambdaql.query.Order$Kind.RUSH`.

**`!!`는 컴파일러를 위한 것입니다.** Kotlin 엔티티의 속성이 `Double?`처럼 nullable이면 Kotlin은 `it.price > 100.0`을 컴파일하지 않으므로 `it.price!! > 100.0`이라고 씁니다. lambdaQL은 `!!`를 무시합니다(결정 D23). 값이 NULL인 행은 반환되지 않습니다.

[`ManualKotlinWhereTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt), 영역 `bang`:

```kotlin
// !! is only for the compiler: lambdaQL ignores it, and rows with a NULL cnt are not returned
val q = qb.from(Order::class.java).where { it.cnt!! > 1 }
assertEquals("select e0 from Order e0 where e0.cnt > 1", q.toHql().hql())
```

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

**산술.** `+`, `-`, `*`, `/`, 단항 `-`를 지원합니다. `%`는 `mod`로 씁니다.
`/`도 쓸 수 있지만, 컬럼이 들어간 정수 `/`는 피하십시오. SQL에서 계산하므로 데이터베이스마다 결과가 다릅니다(MariaDB는 `5/2 = 2.5`, Kotlin은 `2`). 컬럼 쪽 `/`의 결과를 맞추는 규칙은 아직 정해지지 않았습니다.
캡처 값끼리의 계산(아래의 `n / 2.0`)은 쿼리 전에 하므로 이 차이가 없습니다.

아래 예의 `cnt`는 `Order`의 정수 컬럼(`Integer cnt`)입니다.

**컬럼에 대한 산술과 인덱스**(결정 D50). `it.cnt * 2 > n`처럼 컬럼에 산술을 걸면 인덱스를 쓸 수 없습니다. lambdaQL은 `LQL-5012`를 `WARN` 수준으로 기록하고, `SargableMode.STRICT`에서는 분석이 실패합니다(`LQL-3066`).
값 쪽으로 옮길 때는 `it.cnt > n / 2.0`처럼 소수로 나누십시오. 정수 나눗셈 `n / 2`는 다른 행을 냅니다. 음수를 곱했다면 비교 방향을 바꾸십시오. 자세한 내용은 [10.2](10-rules-and-limits.md#db-functions-on-a-column)를 참고하십시오.

**정수 컬럼과 소수 값**(결정 D48 B8). 정수 컬럼을 소수 값과 비교하면 lambdaQL은 값을 적은 그대로 비교합니다. 반올림이나 값 제외는 하지 않습니다.

- 람다에 적은 상수는 리터럴로 남습니다. `it.cnt >= 2.5`는 `e0.cnt >= 2.5`가 됩니다.
- 캡처 값도 소수 리터럴로 씁니다. `e0.cnt >= 2.5BD`가 되고 바인드 파라미터는 없습니다. `BD`는 HQL의 `BigDecimal` 리터럴 접미사입니다.
- 그런 요소가 있는 IN 목록은 모든 요소를 소수 리터럴로 씁니다(`e0.cnt in (2.5BD, 3.0BD)`).

캡처 값을 바인드 파라미터로 보내는 원칙(D37)의 예외입니다. Hibernate 7은 정수 컬럼과 비교하는 파라미터에 소수를 바인딩하면 `BigDecimal` 타입을 지정해도 거부합니다(`QueryArgumentException`, 실측).

이전에는 값을 컬럼 타입으로 잘라 `2.5`가 `2`가 되었고, 잘못된 행을 반환했습니다. Hibernate 6.6은 정수 컬럼 타입으로 바인딩한 소수를 거부하고(`CoercionException`), IN 목록의 요소는 조용히 자릅니다(세 DB에서 실측).
정수 비교로 바꾸는 방식(`>= 3`)은 채택하지 않았습니다. 실행되는 SQL이 람다와 달라지고, 엔티티 매핑(`Integer`)과 DB 컬럼 타입(예: 소수가 있는 `DECIMAL`)이 다르면 잘못된 행을 냅니다.

결과는 H2, MariaDB 10.11, Oracle 23.26에서 올바릅니다(실측). 세 DB 모두 SQL은 `cnt>=2.5`, `cnt in (2.5,3.0)`처럼 컬럼을 그대로 두고 소수와 비교합니다.
MariaDB의 `EXPLAIN`은 인덱스 범위 스캔(`type=range`)을 보여 줍니다. Oracle은 컬럼에 변환이 붙지 않아 인덱스를 쓸 수 있는 형태입니다. 인덱스 힌트를 주면 `INDEX RANGE SCAN`이고, 3000행 테이블에서 힌트가 없으면 정수 리터럴(`cnt >= 99`)과 같게 전체 스캔을 고릅니다(모두 실측).

[`ManualKotlinWhereTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt), 영역 `whole-number`:

```kotlin
// cnt is an integer column: a decimal value is compared as it is, not cut to 2
val literal = qb.from(Order::class.java).where { it.cnt >= 2.5 }
assertEquals("select e0 from Order e0 where e0.cnt >= 2.5", literal.toHql().hql())

val v = 2.5
val captured = qb.from(Order::class.java).where { it.cnt >= v }
assertEquals("select e0 from Order e0 where e0.cnt >= 2.5BD", captured.toHql().hql())
```

**부동소수점:** `Float`/`Double` 컬럼에 대해 lambdaQL은 Kotlin과 비트 단위까지 같은 결과를 약속하지 않습니다.
캡처한 `NaN`이나 `Infinity` 값은 쿼리가 실행될 때 거부됩니다.

<a id="35-and-or-not"></a>
## 3.5 AND, OR, NOT

| Kotlin | HQL |
|---|---|
| `a && b` | `a and b` |
| `a \|\| b` | `a or b` |
| `!a` | `not (a)` |
| 괄호 | 필요한 만큼 유지 |

[`ManualKotlinWhereTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt), 영역 `and-or-not`:

```kotlin
val both = qb.from(Order::class.java).where { (it.status.toInt() == 1 || it.isActive) && it.price > minPrice }
assertEquals("select e0 from Order e0 where (e0.status = 1 or e0.active = true) and e0.price > :p1", both.toHql().hql())

// the compiler moves the ! inside (De Morgan): the rows are the same
val not = qb.from(Order::class.java).where { !(it.status.toInt() == 1 && it.price > minPrice) }
assertEquals("select e0 from Order e0 where e0.status <> 1 or e0.price <= :p1", not.toHql().hql())
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

[`ManualKotlinWhereTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt), 영역 `if-when`:

```kotlin
val ifAnd = qb.from(Order::class.java).where { if (it.isActive) it.price > 100.0 else false }
assertEquals("select e0 from Order e0 where e0.active = true and e0.price > 100.0", ifAnd.toHql().hql())

val ifOr = qb.from(Order::class.java).where { if (it.isActive) true else it.price > 100.0 }
assertEquals("select e0 from Order e0 where e0.active = true or e0.price > 100.0", ifOr.toHql().hql())

val whenOr = qb.from(Order::class.java).where { when { it.status.toInt() == 3 -> true; it.price > 200.0 -> true; else -> false } }
assertEquals("select e0 from Order e0 where e0.status = 3 or e0.price > 200.0", whenOr.toHql().hql())
```

그 밖의 `if (c) x else y`, 그리고 **값**을 고르는 `if`나 `when`(`it.status.toInt() == (if (flag) 1 else 2)`)은 거부됩니다(제한 KC6).
선택이 캡처 값에만 달려 있다면 람다 전에 값을 계산하십시오.

[`ManualKotlinWhereTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt), 영역 `value-before`:

```kotlin
val wanted: Short = if (flag) 1 else 2
val q = qb.from(Order::class.java).where { it.status == wanted }
assertEquals("select e0 from Order e0 where e0.status = :p1", q.toHql().hql())

// an if that picks a value inside the lambda is rejected (KC6)
val e = assertThrows<LambdaQLException> {
    qb.from(Order::class.java).where { it.status.toInt() == (if (flag) 1 else 2) }.toHql()
}
assertTrue(e.message!!.startsWith("[LQL-2021]"))
```

<a id="36-null-rules"></a>
## 3.6 NULL 규칙

가장 중요한 부분입니다. 단순한 원칙 세 가지가 있습니다.

1. **null 검사를 직접 작성한 곳에서는 lambdaQL이 그 검사를 사용합니다.** (`== null`, `!= null`, `?.`, `?:`)
2. **작성하지 않은 곳에서는 lambdaQL이 SQL 의미를 사용합니다.** 컬럼이 NULL이면 비교는 UNKNOWN이 되고 행은 반환되지 않습니다.
   엔티티 값 사이의 `==`, `!=`에도 같은 규칙을 적용합니다(결정 D48).
   결과가 Kotlin과 다를 수 있으면 lambdaQL은 `LQL-5009`를 `DEBUG` 수준으로 기록합니다.
3. **캡처 값은 먼저 계산하고, 비교는 역시 SQL 의미를 따릅니다.** 캡처 값 `null`은 NULL로 바인딩되므로 `= :p1`과 `<> :p1`은 어떤 행에도 일치하지 않습니다.
   이 경우 lambdaQL은 실행 시점에 `LQL-4045`를 `WARN` 수준으로 기록합니다. NULL을 찾으려면 `x == null`이라고 쓰십시오(`is null`이 됨).
   캡처 값에 메서드를 호출하는 식(`text.trim()`, 다른 기본형으로 바꾸는 `min.toInt()` 등)은 먼저 계산하므로 `null`이면 Kotlin처럼 예외(`LQL-4012`)를 던집니다.
   캡처 값의 `!!`와 자기 기본형으로의 언박싱(`min!!`)은 실행하지 않습니다. `null`이면 NULL을 바인딩하고 `LQL-4045`를 `DEBUG` 수준으로 기록합니다.

Kotlin 컴파일러는 바이트코드에 **숨겨진** null 검사를 추가합니다(예: 캡처한 `id: Long`과 비교하는 `it.id == id`). 소스 코드에 있는 null 검사만 의미가 있습니다(결정 D22). [3.6.2](#362--compares-values)를 참고하십시오.

<a id="361-rule-table"></a>
### 3.6.1 규칙 표

| # | Kotlin 형태 | HQL | NULL 동작 |
|---|---|---|---|
| R12 | NULL이 될 수 없는 컬럼과 non-null 값: `it.id == id`(`id: Long`) | `e0.id = :p1` | 컴파일러가 추가한 null 검사를 지움 |
| R1 | 크기 비교: `it.price > p`, `it.cnt!! > 1` | `e0.price > :p1`, `e0.cnt > 1` | SQL 의미 |
| R11 | nullable 컬럼과 nullable 캡처 값: `it.cnt == n`(`n: Int?`), `it.description == text`(`String?`), enum `==` | `e0.cnt = :p1` | 값 비교. 컬럼이 NULL이면 행이 반환되지 않음. 캡처 값 `null`: 일치하는 행 없음(`LQL-4045`) |
| R11 | `it.cnt != c` | `e0.cnt <> :p1` | SQL 의미(D48). 컬럼이 NULL이면 행이 반환되지 **않음**(Kotlin이라면 반환). 캡처 값 `null`: 일치하는 행 없음(`LQL-4045`) |
| R11 | 엔티티 값 두 개: `it.description == it.custName` | `e0.description = e0.custName` | SQL 의미(D48). 어느 한쪽이 NULL이면 행이 반환되지 않음(NULL과 NULL도 마찬가지) |
| R3 | `x == null`, `x != null` | `x is null`, `x is not null` | |
| R17 | `===`, `!==` | `==`, `!=`와 같음 | 객체 동일성이 아니라 값 비교 |
| R9 | 캡처 값 | 바인드 파라미터 | [3.6.4](#364-captured-null-values) 참고 |

<a id="362--compares-values"></a>
### 3.6.2 `==`는 값을 비교

Kotlin의 `==`는 원래 값을 비교합니다. lambdaQL에서는 `===`도 **값**을 비교합니다(규칙 R17).
쿼리에는 객체 동일성이 없습니다. 값이 같은 서로 다른 두 `Long` 박스는 같습니다.

[`ManualKotlinWhereTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt), 영역 `captured-eq`:

```kotlin
val n: Int? = 3
val q = qb.from(Order::class.java).where { it.cnt == n }
assertEquals("select e0 from Order e0 where e0.cnt = :p1", q.toHql().hql())

// a captured null matches no row: "= NULL" is never true (WARN LQL-4045)
val none: Int? = null
assertEquals(0, qb.from(Order::class.java).where { it.cnt == none }.toList().size)
```

`cnt`가 NULL인 행을 찾으려면 null 검사를 작성하십시오. `it.cnt == null`은 `e0.cnt is null`이 됩니다.

컬럼을 함수로 감싸지 않으므로 데이터베이스는 인덱스를 사용할 수 있습니다([10.2](10-rules-and-limits.md#102-sargable-rule-index-friendly-sql) 참고).

[`ManualKotlinWhereTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt), 영역 `string-eq`:

```kotlin
val eq = qb.from(Order::class.java).where { it.description == text }
assertEquals("select e0 from Order e0 where e0.description = :p1", eq.toHql().hql())

// a NULL description matches neither = nor <> (SQL meaning)
val ne = qb.from(Order::class.java).where { it.description != text }
assertEquals("select e0 from Order e0 where e0.description <> :p1", ne.toHql().hql())
```

**NULL이 될 수 없는 컬럼: `it.id == id`.** 테스트 엔티티의 `id`는 `Long`이고 Kotlin에서는 플랫폼 타입입니다. Kotlin은 `it.id == id`(캡처한 `id: Long`)를 null 검사와 비교로 컴파일합니다.

이 바이트코드를 그대로 옮기면 `not (e0.id is null or e0.id <> :p1)`이 됩니다. 결과는 맞지만 데이터베이스가 인덱스를 제대로 사용하기 어렵습니다.
id는 NULL이 될 수 없으므로 id의 `is null`은 항상 거짓입니다. lambdaQL은 이 검사를 제거하고 `e0.id = :p1`로 씁니다.

기본형 속성과 `nullable = false` 컬럼에도 같은 규칙을 적용합니다. 단, 경로에 **연관 단계가 없을 때만** 적용합니다. 고객이 없는 주문이 있으므로 `it.customer?.id`는 NULL이 될 수 있고, 이 경로의 null 검사는 유지합니다.

[`ManualKotlinWhereTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt), 영역 `never-null`:

```kotlin
// the id can never be NULL: the null check that Kotlin adds is dropped
val byId = qb.from(Order::class.java).where { it.id == id }
assertEquals("select e0 from Order e0 where e0.id = :p1", byId.toHql().hql())

val noId = qb.from(Order::class.java).where { it.id == null }
assertEquals("select e0 from Order e0 where 1=0", noId.toHql().hql())

// a path through an association can be NULL: the check stays
val noCustomerId = qb.from(Order::class.java).where { it.customer?.id == null }
assertEquals("select e0 from Order e0 where e0.customer.id is null", noCustomerId.toHql().hql())
```

> [!WARNING]
> **Kotlin의 non-null 타입은 증거가 되지 않습니다.** `var code: String` 속성에도 데이터베이스에서 온 NULL이 들어 있을 수 있습니다.
> Hibernate가 검사 없이 컬럼 값을 필드에 쓰기 때문입니다.
> lambdaQL은 Kotlin 타입이 아니라 JPA 메타데이터를 봅니다.

<a id="363-write-your-null-checks-yourself"></a>
### 3.6.3 null 검사는 직접 작성

Kotlin에서 `null != 3`은 `true`입니다. lambdaQL에서는 다릅니다. 아래의 `it.cnt != c`는 `cnt`가 NULL인 행을 반환하지 **않습니다**. null 검사를 작성하지 않았기 때문입니다.

[`ManualKotlinWhereTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt), 영역 `null-check`:

```kotlin
val c: Int? = 3
// rows with a NULL cnt are left out (SQL meaning, D48); Kotlin would keep them
val ne = qb.from(Order::class.java).where { it.cnt != c }
assertEquals("select e0 from Order e0 where e0.cnt <> :p1", ne.toHql().hql())

// to keep the NULL rows, write the null check
val keep = qb.from(Order::class.java).where { it.cnt == null || it.cnt != c }
assertEquals("select e0 from Order e0 where e0.cnt is null or e0.cnt <> :p1", keep.toHql().hql())
```

> [!IMPORTANT]
> Kotlin 코드를 실행하는 것과는 다릅니다. NULL 행을 유지하려면 `it.cnt == null || it.cnt != c`처럼 null 검사를 직접 작성하십시오.

이런 차이가 생길 수 있으면 lambdaQL은 `LQL-5009`를 `DEBUG` 수준으로 조건마다 한 번 기록합니다.

`String?`에 대한 `it.description.equals(text)`는 `==`와 같습니다.

**엔티티 값에 대한 SQL 의미**(결정 D48). 엔티티 값 두 개의 비교와 NULL이 될 수 있는 `Boolean` 비교도 SQL 의미를 따릅니다.

[`ManualKotlinWhereTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt), 영역 `sql-meaning`:

```kotlin
// two entity values: = as in SQL; a row with a NULL on either side does not match
val same = qb.from(Order::class.java).where { it.description == it.custName }
assertEquals("select e0 from Order e0 where e0.description = e0.custName", same.toHql().hql())

// to keep the NULL rows, say so in the lambda
val keep = qb.from(Order::class.java).where { it.description == null || it.description != text }
assertEquals("select e0 from Order e0 where e0.description is null or e0.description <> :p1", keep.toHql().hql())

// a NULL Boolean is not false
val notVip = qb.from(Coded::class.java).where { it.vip == false }
assertEquals("select e0 from Coded e0 where e0.vip = false", notVip.toHql().hql())
```

`?.`, `?:`, `!!`에 대한 자세한 내용: [9. Kotlin 노트](09-kotlin-notes.md).

<a id="364-captured-null-values"></a>
### 3.6.4 null 캡처 값

| 형태 | 캡처 값이 `null`일 때 |
|---|---|
| 캡처 값과의 `==`, `!=`: `it.cnt == n`, `it.description != text` | 실행됨: NULL을 바인딩한 `= :p1` / `<> :p1`이므로 일치하는 행이 **없음**. `WARN` 수준 `LQL-4045` |
| 크기 비교: `it.price!! > min!!`, 모든 `<`, `<=`, `>`, `>=` | 실행됨: `e0.price > :p1`에 NULL 바인딩, 일치하는 행 **없음**. `DEBUG` 수준 `LQL-4045`. `min!!`(D23)과 자기 기본형으로의 언박싱은 실행하지 않음 |
| 캡처 값의 메서드 호출: `text.trim()`, 다른 기본형으로 바꾸는 `min.toInt()` | 쿼리가 실행될 때 **예외**(`LQL-4012`). 먼저 계산하기 때문 |
| `in`에 사용하는 컬렉션 안의 null 요소 | 쿼리가 실행될 때 **예외** |

이유: 람다 안의 비교는 SQL 의미를 따릅니다(결정 D48). SQL에서 NULL과의 `=`, `<>`는 참이 되지 않습니다.
캡처 값이 null인지는 쿼리가 실행될 때만 알 수 있으므로, lambdaQL은 실행할 때 경고를 기록합니다.

캡처 값에 메서드를 호출하는 식은 쿼리 전에 계산하는 값입니다. `text.trim()`, 다른 기본형으로 바꾸는 `min.toInt()`가 이에 해당하며, `null`이면 Kotlin처럼 예외(`LQL-4012`)를 던집니다.
캡처 값의 `!!`(D23)와 자기 기본형으로의 언박싱은 실행하지 않고 박싱 값을 바인딩합니다. `null`이면 일치하는 행이 없고 `LQL-4045`를 `DEBUG` 수준으로 기록합니다.
NULL 행을 찾으려면 `x == null`이라고 쓰십시오.

**null 가드로 필터 생략.** 같은 캡처 값을 null 검사하면, 값이 null일 때 그 조건이 필터 없이 사라집니다.

[`ManualKotlinWhereTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt), 영역 `captured-null-guard`:

```kotlin
fun byMinPrice(min: Double?) = qb.from(Order::class.java).where { min == null || it.price > min }

val all = byMinPrice(null)
assertEquals("select e0 from Order e0", all.toHql().hql())               // no filter
val some = byMinPrice(100.0)
assertEquals("select e0 from Order e0 where e0.price > :p1", some.toHql().hql())
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
- 이 경우 가드의 위치는 결과에 영향을 주지 않습니다. `min == null`일 때 `it.price > min!! || min == null`도 필터가 없습니다. `min!!`과 언박싱을 실행하지 않기 때문입니다(Kotlin이라면 `NullPointerException`).
- 캡처 값에 메서드를 호출하는 비교(`it.description == text.trim()`)는 다릅니다. 가드가 뒤에 있으면 `text.trim()`을 먼저 계산하므로 `text == null`일 때 예외(`LQL-4012`)를 던집니다.

<a id="365-converted-attributes-convert"></a>
### 3.6.5 변환 속성(`@Convert`)

JPA `AttributeConverter`가 붙은 속성은 Kotlin 값으로 비교합니다. Hibernate(7.4, 이전 6.6도)는 이런 속성과 비교하는 리터럴을 변환하고, 바인드 파라미터도 변환합니다(H2, MariaDB, Oracle에서 확인함).

[`ManualKotlinWhereTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt), 영역 `convert`:

```kotlin
// code is stored reversed ("abc" as "cba"): Hibernate converts the literal
val q = qb.from(Coded::class.java).where { it.code == "abc" }
assertEquals("select e0 from Coded e0 where e0.code = 'abc'", q.toHql().hql())
```

<a id="37-boolean-columns"></a>
## 3.7 boolean 컬럼

`Order`의 `active`는 기본형 `boolean`입니다(절대 NULL이 아님, Kotlin 속성 이름 `isActive`).
`vip`이 나오는 행은 `Coded`의 NULL이 될 수 있는 `Boolean vip` 컬럼입니다.

| Kotlin | HQL | NULL 행 |
|---|---|---|
| `it.isActive` | `e0.active = true` | –(절대 NULL이 아님) |
| `it.vip == true` | `e0.vip = true` | 반환 안 됨 |
| `it.vip != true` | `e0.vip <> true` | **반환 안 됨**(SQL 의미, D48. Kotlin이라면 반환) |
| `it.vip == false` | `e0.vip = false` | 반환 안 됨(NULL은 false가 아님) |
| `it.vip ?: false` | `e0.vip is not null and e0.vip = true` | 반환 안 됨 |
| `!(it.vip ?: false)` | `e0.vip is null or e0.vip = false` | **반환됨** |

참고:

- lambdaQL은 `is not true`나 `coalesce(flag, false)`를 사용하지 않습니다. Oracle에서 실패합니다(ORA-00932).
- 더 큰 조건의 부정은 항상 `not (...)`로 씁니다. lambdaQL은 조건을 `(...) = false`나 `(...) is null`로 감싸지 않습니다. Oracle 23이 `WHERE`에서 이를 잘못 평가하기 때문입니다.
- `it.vip ?: false`는 **조건 안에서는** 괜찮습니다. 값이나 정렬 키로 쓰면 거부됩니다(제한 KC21).

[`ManualKotlinWhereTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt), 영역 `nullable-boolean`:

```kotlin
// vip is a Boolean column that can be NULL
val yes = qb.from(Coded::class.java).where { it.vip == true }
assertEquals("select e0 from Coded e0 where e0.vip = true", yes.toHql().hql())

val notYes = qb.from(Coded::class.java).where { it.vip != true }
assertEquals("select e0 from Coded e0 where e0.vip <> true", notYes.toHql().hql())

val elvis = qb.from(Coded::class.java).where { it.vip ?: false }
assertEquals("select e0 from Coded e0 where e0.vip is not null and e0.vip = true", elvis.toHql().hql())

val notElvis = qb.from(Coded::class.java).where { !(it.vip ?: false) }
assertEquals("select e0 from Coded e0 where e0.vip is null or e0.vip = false", notElvis.toHql().hql())
```

<a id="38-strings"></a>
## 3.8 문자열

- 문자열에 대한 `==`와 `equals`는 **데이터베이스 콜레이션**을 사용합니다.
  - MariaDB의 `_ci` 콜레이션은 대소문자와 끝의 공백을 무시합니다.
  - Oracle은 `''`(빈 문자열)을 NULL로 저장합니다. 그래서 Oracle에서 `it.description == ""`는 Kotlin과 다르게 동작합니다.
- 컬럼에 대한 Kotlin 문자열 함수(예: `startsWith`)는 아직 SQL로 바뀌지 않고 거부됩니다(`LQL-3008`). `isBlank()`, `trim()` 같은 다른 stdlib 함수도 거부됩니다(제한 KC9, KC20).
- 람다 안의 문자열 템플릿과 `+`는 거부됩니다(`LQL-2007`, 제한 KC17). 캡처 값만 쓰는 템플릿(`"$a-$b"`)도 마찬가지이므로 람다 전에 문자열을 만들어 캡처하십시오.

[`ManualKotlinWhereTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt), 영역 `string-rejected`:

```kotlin
// Kotlin string functions on a column are not translated yet
val f = assertThrows<LambdaQLException> {
    qb.from(Order::class.java).where { it.description.startsWith("a") }.toHql()
}
assertTrue(f.message!!.startsWith("[LQL-3008]"))

// string building in the lambda is rejected: build the string before the lambda
val a = "x"
val b = "y"
val t = assertThrows<LambdaQLException> {
    qb.from(Order::class.java).where { "$a-$b" == it.description }.toHql()
}
assertTrue(t.message!!.startsWith("[LQL-2007]"))
```

<a id="39-in-with-a-captured-collection"></a>
## 3.9 캡처한 컬렉션을 사용한 IN

컬렉션을 람다 **밖에서** 만들고 `in`을 사용하십시오.

[`ManualKotlinWhereTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt), 영역 `in-ids`:

```kotlin
val ids = seeded.toList()        // two order ids, built outside the lambda

val inList = qb.from(Order::class.java).where { it.id in ids }
assertEquals("select e0 from Order e0 where e0.id in (:p1)", inList.toHql().hql())

val notInList = qb.from(Order::class.java).where { it.id !in ids }
assertEquals("select e0 from Order e0 where e0.id not in (:p1)", notInList.toHql().hql())
```

`seeded`는 테스트가 넣은 주문의 id 두 개입니다.

**데이터베이스별 SQL (Java 테스트 실행 결과, Kotlin도 같은 HQL을 만듦)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `not-in`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.id not in (?,?)
```

[`KotlinSelectTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinSelectTest.kt), 영역 `in`:

```kotlin
val q = qb.from(Order::class.java).where { it.status in statuses }
assertEquals("select e0 from Order e0 where e0.status in (:p1)", q.toHql().hql())
```

`statuses`는 캡처한 `listOf<Short>(1, 3)`입니다.

`!in`은 `not in`이 됩니다. NULL이 될 수 있는 컬럼도 단순한 `not in`입니다(SQL 의미, 결정 D48).
값이 NULL인 행은 `not in`에 일치하지 않으며, lambdaQL은 `LQL-5009`를 `DEBUG` 수준으로 기록합니다. Kotlin의 `!in`은 NULL 값에 대해 true이므로, 이런 행을 유지하려면 `it.description == null || it.description !in descs`처럼 null 검사를 작성하십시오.

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
| 임베디드(`@Embedded`) | 같은 테이블, 조인 없음. 값 객체 전체의 같음 비교는 컬럼마다 펼침(아래 참고) | `Order`에 임베디드 `address`가 있다면: `it.address.city` -> `e0.address.city` |
| 대상의 `@Id`로 끝나는 경로 | 외래 키 컬럼, 조인 없음 | `it.customer?.id` -> `e0.customer.id` |
| 연관 자체를 `null`과 비교 | 외래 키 컬럼, 조인 없음 | `it.customer == null` -> `e0.customer is null` |
| 컬렉션(`@OneToMany`, `@ManyToMany`) | **거부됨** | `it.items...` -> `exists()` 서브쿼리 사용 |

**값 객체 전체의 비교.** 임베디드 값 객체(`@Embedded`) 전체를 같음으로 비교하면 lambdaQL은 컬럼마다 비교 하나로 펼칩니다(B6).
`it.address == addr`는 `e0.address.city = :p1 and e0.address.zip = :p2`가 됩니다.

- 부정(`it.address != addr`)은 `e0.address.city <> :p1 or e0.address.zip <> :p2`입니다.
- `it.address == null`은 `e0.address.city is null and e0.address.zip is null`이고, `!= null`은 `e0.address.city is not null or e0.address.zip is not null`입니다.
- 각 컬럼은 SQL 의미를 따릅니다(D48). NULL 컬럼은 `=`에도 `<>`에도 일치하지 않습니다. 예를 들어 (Seoul, NULL) 행은 같음 비교에도 부정에도 일치하지 않습니다(세 DB에서 실측).
- 캡처한 값 객체의 각 속성은 쿼리가 실행될 때 읽어 바인딩합니다. 캡처한 객체가 `null`이면 모든 컬럼에 NULL을 바인딩하므로 일치하는 행이 없고, 컬럼마다 `LQL-4045`를 `WARN` 수준으로 기록합니다.
- 중첩된 값 객체도 펼칩니다.
- 크기 비교(`<` 등)와 다른 타입과의 비교는 여전히 `LQL-3012` 오류입니다. 필드를 비교하십시오.

[`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt), 영역 `embedded-compare`:

```kotlin
// address is @Embedded (city, zip): == with a value object compares each column (D48)
val addr = Address("Seoul", "04524")
val same = qb.from(Order::class.java).where { it.address == addr }
assertEquals("select e0 from Order e0 where e0.address.city = :p1 and e0.address.zip = :p2", same.toHql().hql())
val none = qb.from(Order::class.java).where { it.address == null }
assertEquals("select e0 from Order e0 where e0.address.city is null and e0.address.zip is null", none.toHql().hql())
```

`it.customer?.name`, `it.customer!!.name`, 그리고 (Java 엔티티라면) `it.customer.name`은 모두 **같은 컬럼** `j1.name`을 뜻합니다.
다만 `where`에서는 연관 단계를 어떻게 지났는지에 따라 조인 종류가 달라집니다(결정 D54).

| `where`에서 연관 단계를 지난 방법 | 조인 | 고객이 없는 주문 |
|---|---|---|
| `!!`, 또는 표시 없음(non-null 타입 `customer: Customer`, Java 엔티티의 플랫폼 타입) | inner `join` | 결과에 없음 |
| `?.` | 고객이 없는 주문이 조건 전체를 참으로 만들 수 있으면 `left join`, 아니면 inner `join` | Kotlin에서 조건이 참이면 결과에 남음 |

`?.`는 "연관이 없을 수 있다"를 소스에 적는 문법입니다. `!!`는 "연관이 반드시 있다"는 선언으로 봅니다.
값의 비교는 어느 경우든 SQL 의미를 따릅니다(결정 D48). 예를 들어 `it.customer?.name != "Kim"`은 고객이 없는 주문과 맞지 않습니다.

**inner join으로 충분한 경우.** `where`에서 연관 경로는 기본적으로 inner join입니다(결정 D36).
`?.`로 지난 단계라도 고객이 없는 주문이 조건을 참으로 만들 수 없으면 inner join으로 씁니다. 결과는 같습니다.

[`ManualKotlinStartTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinStartTest.kt), 영역 `join-path`:

```kotlin
val name = "Kim"
val q = qb.from(Order::class.java).where { it.customer?.name == name }
assertEquals("select e0 from Order e0 join e0.customer j1 where j1.name = :p1", q.toHql().hql())
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

[`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt), 영역 `safe-call-and`:

```kotlin
// a row without a customer can never match: lambdaQL keeps the inner join (the same rows)
val q = qb.from(Order::class.java).where { it.customer?.name == "Kim" && it.price > 100.0 }
assertEquals("select e0 from Order e0 join e0.customer j1 where j1.name = 'Kim' and e0.price > 100.0", q.toHql().hql())
```

**`?.`와 `||`.** 고객이 없는 주문이 조건을 참으로 만들 수 있으면 `?.`로 지난 단계는 `left join`입니다.
status가 1이고 고객이 없는 주문도 결과에 남습니다. Kotlin에서 같은 조건을 실행한 결과와 같습니다.

[`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt), 영역 `safe-call-or`:

```kotlin
// ?. says the customer may be missing: an order without a customer stays when the rest of the OR is true
val q = qb.from(Order::class.java).where { it.status.toInt() == 1 || it.customer?.name == "Kim" }
assertEquals("select e0 from Order e0 left join e0.customer j1 where e0.status = 1 or j1.name = 'Kim'", q.toHql().hql())
```

**데이터베이스별 SQL (Kotlin 영역의 HQL을 그대로 실행한 결과)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `safe-call-or`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where o1_0.status=1 or c1_0.name='Kim'
```

**`?.`와 `== null`.** `it.customer?.name == null`은 Kotlin처럼 고객이 없는 주문과 이름이 없는 고객의 주문을 모두 찾습니다.

[`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt), 영역 `safe-call-null`:

```kotlin
// "no customer, or a customer without a name": a left join, as in Kotlin
val q = qb.from(Order::class.java).where { it.customer?.name == null }
assertEquals("select e0 from Order e0 left join e0.customer j1 where j1.name is null", q.toHql().hql())
```

**데이터베이스별 SQL (Kotlin 영역의 HQL을 그대로 실행한 결과)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `safe-call-null`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name is null
```

**null 가드.** `?.`로 쓴 null 가드도 `left join`이 되어 고객이 없는 주문이 남습니다.
`!!`로 쓰면 inner join이라 남지 않습니다. 이 경우 lambdaQL은 `LQL-5009`를 `DEBUG` 수준으로 기록합니다.

[`ManualKotlinWhereTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt), 영역 `null-guard`:

```kotlin
// ?. : orders without a customer stay (left join)
val safe = qb.from(Order::class.java).where { it.customer == null || it.customer?.name == name }
assertEquals("select e0 from Order e0 left join e0.customer j1 where e0.customer is null or j1.name = :p1",
    safe.toHql().hql())

// !! : an inner join, so orders without a customer are left out (DEBUG LQL-5009)
val sure = qb.from(Order::class.java).where { it.customer == null || it.customer!!.name == name }
assertEquals("select e0 from Order e0 join e0.customer j1 where e0.customer is null or j1.name = :p1",
    sure.toHql().hql())

val and = qb.from(Order::class.java).where { it.customer != null && it.customer?.name == name }
assertEquals("select e0 from Order e0 join e0.customer j1 where e0.customer is not null and j1.name = :p1",
    and.toHql().hql())
```

아래 SQL은 같은 조건을 Java로 쓴 테스트의 결과입니다. Kotlin에서는 `!!`로 쓴 두 번째 형태와 `&&`로 쓴 세 번째 형태가 이 SQL을 만듭니다.

**데이터베이스별 SQL (Java 테스트 실행 결과, Kotlin도 같은 HQL을 만듦)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `null-guard`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
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

Hibernate는 `e0.customer is null`을 `c1_0.id is null`로 씁니다. inner join 뒤에서는 이 조건이 참이 되지 않습니다.

**`!!`: 연관이 반드시 있다는 선언.** `!!`로 지난 단계는 항상 inner join입니다.
Kotlin에서는 `||`가 `!!`에 닿기 전에 끝나는 행(status 1, 고객 없음)이 남지만, lambdaQL은 그 행을 뺍니다. 그런 행을 남기려면 `?.`를 쓰십시오.

[`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt), 영역 `not-null-assert`:

```kotlin
// !! says the customer is always there: an inner join, so orders without a customer are left out
// (Kotlin itself would keep the status 1 order without a customer, because || stops before the !!)
val q = qb.from(Order::class.java).where { it.status.toInt() == 1 || it.customer!!.name == "Kim" }
assertEquals("select e0 from Order e0 join e0.customer j1 where e0.status = 1 or j1.name = 'Kim'", q.toHql().hql())
```

**`?.`와 `!!`를 섞은 경우.** 조건 전체(같은 쿼리의 모든 `where`)에서 같은 연관 단계를 한 곳에서는 `?.`로, 다른 곳에서는 `!!`나 표시 없이 지나면 inner join입니다.
lambdaQL은 `LQL-5014`를 `WARN` 수준으로 기록합니다(조건마다 한 번). 고객이 없는 주문을 남기려면 모두 `?.`로 쓰십시오.

[`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt), 영역 `mixed-warn`:

```kotlin
// the same association with ?. in one place and !! in another: an inner join, and WARN LQL-5014
val q = qb.from(Order::class.java).where { it.customer?.name == "Kim" || it.customer!!.grade!! > 3 }
assertEquals("select e0 from Order e0 join e0.customer j1 where j1.name = 'Kim' or j1.grade > 3", q.toHql().hql())
```

- 한 경로 안에서 뒤 단계가 `!!`이면 앞 단계도 inner join입니다. 예를 들어 `Item`의 `it.order?.customer!!.name`은 `join e0.order j1 join j1.customer j2`입니다. Kotlin에서도 주문이 없으면 `NullPointerException`이 나는 경로라 `LQL-5014`를 기록하지 않습니다.
- 두 단계를 모두 `?.`로 지나면(`it.order?.customer?.name`) 필요할 때 두 단계 모두 `left join`입니다.

고객이 **없는** 주문을 찾으려면 연관 자체를 비교하십시오. 조인 없이 외래 키 컬럼을 검사합니다.

[`ManualKotlinWhereTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt), 영역 `no-customer`:

```kotlin
val q = qb.from(Order::class.java).where { it.customer == null }
assertEquals("select e0 from Order e0 where e0.customer is null", q.toHql().hql())
```

**데이터베이스별 SQL (Java 테스트 실행 결과, Kotlin도 같은 HQL을 만듦)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `no-association`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id is null
```

추가 사항:

- 같은 경로는 별칭 하나를 받습니다. 이 별칭은 `where`, `orderBy`, `select`가 함께 사용합니다.
- `it.customer?.id == 5L`은 외래 키 컬럼 `e0.customer.id`를 사용합니다. 조인이 필요 없습니다.
- `orderBy`나 `select`에**만** 쓰인 경로는 `left join`이므로 그곳에서는 행을 잃지 않습니다(규칙 S8, J1). `!!`가 있어도 마찬가지입니다. 같은 경로를 `where`에서도 쓰면 `orderBy`와 `select`는 `where`의 조인을 사용합니다.
- `?.`에 따른 조인 구분은 조회, `count`, 서브쿼리, 조인 쿼리의 `where`에 적용합니다. 명시적 조인의 `on` 안의 경로는 지금처럼 `left join`입니다. 필수 연관(`optional = false`)은 항상 inner join입니다.
- 연관을 엔티티 객체와 비교하지 말고(`it.customer == c`, 제한 KC13) id를 비교하십시오: `it.customer?.id == c.id`(여기서 `c`는 캡처한 객체).

<a id="311-captured-values"></a>
## 3.11 캡처 값

람다는 밖의 값을 사용할 수 있습니다. 지역 값, 속성, 캡처한 객체의 속성입니다.

[`ManualKotlinWhereTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt), 영역 `captured-kinds`:

```kotlin
val since = LocalDateTime.of(2026, 1, 1, 0, 0)
val local = qb.from(Order::class.java).where { it.updateAt > since }                     // a local val
assertEquals("select e0 from Order e0 where e0.updateAt > :p1", local.toHql().hql())

val property = qb.from(Order::class.java).where { it.status == defaultStatus }           // a property of this class
assertEquals("select e0 from Order e0 where e0.status = :p1", property.toHql().hql())

val objectProperty = qb.from(Order::class.java).where { it.price > filter.minPrice!! }   // a property of a captured object
assertEquals("select e0 from Order e0 where e0.price > :p1", objectProperty.toHql().hql())

val call = qb.from(Order::class.java).where { it.updateAt > LocalDateTime.of(2026, 1, 1, 0, 0) }   // a call without the entity
assertEquals("select e0 from Order e0 where e0.updateAt > :p1", call.toHql().hql())
```

`defaultStatus`는 테스트 클래스의 속성(`Short`)이고, `filter`는 캡처한 `PriceFilter` 객체입니다.

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
lambdaQL은 이 가드를 거부하지 않고 쓴 그대로 HQL에 씁니다. 데이터베이스가 `e0.qty <> 0`보다 나눗셈을 먼저 계산하면 0으로 나누기 오류가 날 수 있습니다.

[`ManualKotlinWhereTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt), 영역 `division-guard`:

```kotlin
// lambdaQL sends the guard as written; SQL does not promise to check qty <> 0 first
val q = qb.from(Item::class.java).where { it.qty != 0 && it.price!! / it.qty > 2 }
assertEquals("select e0 from Item e0 where e0.qty <> 0 and (e0.price / e0.qty) > 2.0", q.toHql().hql())

// the same rows when qty is positive, without a division
val safe = qb.from(Item::class.java).where { it.qty > 0 && it.price!! > 2.0 * it.qty }
assertEquals("select e0 from Item e0 where e0.qty > 0 and e0.price > (2.0 * e0.qty)", safe.toHql().hql())
```

나눗셈 없이 조건을 작성하십시오. 두 번째 쿼리는 `qty`가 양수일 때 첫 번째 쿼리와 같은 행을 반환합니다.

<a id="313-what-is-rejected"></a>
## 3.13 거부되는 것

lambdaQL은 코드 `[LQL-xxxx]`와 소스 줄 번호가 담긴 예외를 던집니다. 추측하지 않습니다.
대부분의 모양은 람다를 분석할 때(처음 사용할 때) 발견됩니다. 잘못된 캡처 값(메서드를 호출한 `null`, NaN, `null` 요소가 있는 컬렉션)은 쿼리가 실행될 때 발견됩니다.
[11. 오류와 언어](../11-errors-and-languages.md)를 참고하십시오.

| 형태 | 예 | 해결 방법 |
|---|---|---|
| 지역 `val`/`var`, 반복문, 여러 문장이 있는 블록 | `{ val x = 1; it.status == x }` | 식 하나로 작성 |
| 값 `if`/`when`(KC6) | `it.status == (if (flag) 1 else 2)` | 람다 전에 값을 계산 |
| 일반 `if (c) x else y`(KC6) | | [3.5](#35-and-or-not)의 허용된 형태 사용 |
| 중위 `and`, `or`, `xor` | `it.isActive and (it.price > 100.0)` | `&&`, `\|\|` 사용 |
| 람다 안의 문자열 템플릿이나 `+`(KC17, `LQL-2007`) | `it.description == "A" + it.status` | 람다 전에 문자열을 만들어 캡처 |
| 람다 안에서 만든 컬렉션(KC8) | `it.status in listOf(1, 2)` | 밖에서 만들어 캡처 |
| 람다 안의 람다 | `ids.any { id -> id == it.id }` | `in`([3.9](#39-in-with-a-captured-collection))이나 서브쿼리 사용 |
| Kotlin 문자열 함수와 다른 stdlib 함수(KC9, KC20, `LQL-3008`) | `it.description.startsWith("a")`, `it.description?.trim()`, `isBlank()` | 직접 비교 |
| `switch`로 컴파일되는 `when`(`LQL-2010`) | `when (it.status.toInt()) { 1, 2 -> true; else -> false }` | `\|\|`로 이은 비교([3.15](#315-when-kotlin-only)) |
| 범위 객체가 필요한 범위(`LQL-3008`) | `it.customer?.grade in 1..5`, `LocalDateTime` 범위 | 두 비교로 작성([3.14](#314-ranges-kotlin-only)) |
| 직접 만든 non-inline 확장 함수(KC11) | `it.isOpen()` | `inline fun`으로 만들거나 조건을 직접 작성 |
| 엔티티의 다른 메서드 | `it.calcSomething()` | 속성만 사용 |
| 엔티티 컬렉션 경로 | `it.items.size > 0` | `exists()` 서브쿼리 |
| 엔티티와 엔티티 비교(KC13) | `it.customer == c` | id 비교 |
| 값 객체의 크기 비교, 다른 타입과의 비교(`LQL-3012`) | `it.address > a`(`Address`가 `Comparable`일 때) | 필드를 비교. `==`, `!=`는 지원 |
| `NaN` / `Infinity` 캡처 값 | `it.price!! > Double.NaN` | NaN을 보내지 않음 |
| `isNaN()`(KC14) | `it.price!!.isNaN()` | – |
| 캡처 변수에 쓰기(KC12) | `{ n++; it.isActive }` | 람다 밖에서 계산 |
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

Kotlin 컴파일러는 NULL이 될 수 없는 숫자와 문자열 대상의 범위를 비교 두 개로 컴파일합니다. lambdaQL은 그 비교를 그대로 씁니다(`between`이 아님).
상한이 상수인 `a..b`는 컴파일러가 `< b+1`로 바꿉니다. 예를 들어 `in 1..2`는 `e0.status < 3`이 됩니다.

[`ManualKotlinWhereTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt), 영역 `ranges`:

```kotlin
val closed = qb.from(Order::class.java).where { it.status.toInt() in 1..2 }
assertEquals("select e0 from Order e0 where 1 <= e0.status and e0.status < 3", closed.toHql().hql())

val outside = qb.from(Order::class.java).where { it.status.toInt() !in 1..2 }
assertEquals("select e0 from Order e0 where 1 > e0.status or e0.status >= 3", outside.toHql().hql())

val until = qb.from(Order::class.java).where { it.status.toInt() in 1 until 3 }
assertEquals("select e0 from Order e0 where 1 <= e0.status and e0.status < 3", until.toHql().hql())

val open = qb.from(Order::class.java).where { it.status.toInt() in 1..<3 }
assertEquals("select e0 from Order e0 where 1 <= e0.status and e0.status < 3", open.toHql().hql())

val captured = qb.from(Order::class.java).where { it.cnt in lo..hi }                  // lo, hi: captured values
assertEquals("select e0 from Order e0 where :p1 <= e0.cnt and e0.cnt <= :p2", captured.toHql().hql())

val prices = qb.from(Order::class.java).where { it.price in 100.0..200.0 }
assertEquals("select e0 from Order e0 where 100.0 <= e0.price and e0.price <= 200.0", prices.toHql().hql())

val texts = qb.from(Order::class.java).where { it.description in "a".."m" }
assertEquals("select e0 from Order e0 where e0.description >= 'a' and e0.description <= 'm'", texts.toHql().hql())
```

| 범위 형태 | 지원 |
|---|---|
| `a..b`, `a until b`, `a..<b`(`Int`, `Double`, `String` 대상) | 예. 비교 두 개 |
| `!in` | 예. 비교를 뒤집어 `or`로 이음 |
| 캡처 값 경계(`lo..hi`) | 예. 바인드 파라미터 |
| nullable 대상(`Int?`), `LocalDate`·`LocalDateTime` 범위 | 아니요(`LQL-3008`) |
| `downTo`, `step` | 예. 값을 펼쳐 `in (:p1)`로 바인딩 |

**NULL일 수 있는 대상.** Java 엔티티의 `Integer` 속성(`cnt`)은 플랫폼 타입이라 컴파일러가 비교 두 개로 컴파일합니다. lambdaQL은 null 가드를 추가하지 **않으므로**(D22) `cnt`가 NULL인 행은 반환되지 않습니다(SQL 의미).
`?.`로 얻은 `Int?` 대상과 날짜 범위는 범위 객체로 컴파일되므로 거부됩니다. 비교 두 개로 쓰십시오.

[`ManualKotlinWhereTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt), 영역 `range-rejected`:

```kotlin
// a range object is not translated: a nullable subject, or a range of dates
val nullable = assertThrows<LambdaQLException> {
    qb.from(Order::class.java).where { it.customer?.grade in 1..5 }.toHql()
}
assertTrue(nullable.message!!.startsWith("[LQL-3008]"))

val dates = assertThrows<LambdaQLException> {
    qb.from(Order::class.java).where { it.updateAt in from..to }.toHql()
}
assertTrue(dates.message!!.startsWith("[LQL-3008]"))

// write the two comparisons instead
val q = qb.from(Order::class.java).where { it.updateAt >= from && it.updateAt <= to }
assertEquals("select e0 from Order e0 where e0.updateAt >= :p1 and e0.updateAt <= :p2", q.toHql().hql())
```

<a id="315-when-kotlin-only"></a>
## 3.15 `when`(Kotlin 전용)

`when`은 **조건으로서** 지원합니다. 모든 분기는 `true`나 `false`(또는 조건)로 끝나야 합니다.
lambdaQL은 이를 비교, `OR`, `AND`로 바꿉니다.

[`ManualKotlinWhereTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt), 영역 `when`:

```kotlin
val twoTexts = qb.from(Order::class.java).where { when (it.description) { "a", "b" -> true; else -> false } }
assertEquals("select e0 from Order e0 where e0.description = 'a' or e0.description = 'b'", twoTexts.toHql().hql())

val oneValue = qb.from(Order::class.java).where { when (it.status.toInt()) { 1 -> true; else -> false } }
assertEquals("select e0 from Order e0 where e0.status = 1", oneValue.toHql().hql())

val range = qb.from(Order::class.java).where { when (it.status.toInt()) { in 1..2 -> true; else -> false } }
assertEquals("select e0 from Order e0 where 1 <= e0.status and e0.status < 3", range.toHql().hql())

val condition = qb.from(Order::class.java).where { when (it.status.toInt()) { 1 -> it.isActive; else -> false } }
assertEquals("select e0 from Order e0 where e0.status = 1 and e0.active = true", condition.toHql().hql())
```

대상이 NULL인 행은 반환되지 않습니다. Kotlin 코드를 실행한다면 `else`로 가겠지만, lambdaQL은 SQL 의미를 사용합니다(D22).

컴파일러가 `when`을 `switch`로 컴파일하면 아직 지원하지 않습니다(`LQL-2010`). 상수가 두 개 이상인 `Int` 대상, 상수가 세 개 이상인 `String` 대상이 이에 해당합니다. enum 대상도 지원하지 않습니다.
이런 조건은 `||`로 이은 비교나 캡처한 컬렉션의 `in`([3.9](#39-in-with-a-captured-collection))으로 쓰십시오.
**값을 고르는** `when`(또는 `if`)은 지원하지 않습니다(KC6).

[`ManualKotlinWhereTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt), 영역 `when-rejected`:

```kotlin
// when compiled to a switch is not supported yet (LQL-2010): two or more Int values, three or more strings
val ints = assertThrows<LambdaQLException> {
    qb.from(Order::class.java).where { when (it.status.toInt()) { 1, 2 -> true; else -> false } }.toHql()
}
assertTrue(ints.message!!.startsWith("[LQL-2010]"))

val texts = assertThrows<LambdaQLException> {
    qb.from(Order::class.java).where { when (it.description) { "a", "b", "c" -> true; else -> false } }.toHql()
}
assertTrue(texts.message!!.startsWith("[LQL-2010]"))

// an enum subject is not supported either
assertThrows<LambdaQLException> {
    qb.from(Order::class.java).where { when (it.kind) { Order.Kind.RUSH -> true; else -> false } }.toHql()
}

// an if or when that picks a value is rejected (KC6)
val value = assertThrows<LambdaQLException> {
    qb.from(Order::class.java).where { (if (it.isActive) it.price else 0.0) > 10.0 }.toHql()
}
assertTrue(value.message!!.startsWith("[LQL-2021]"))
```

대신 `&&`와 `||`로 조건을 작성하십시오.

---

[Kotlin 트랙](README.md) | 이전: [2. 시작하기](02-getting-started.md) | 다음: [4. 정렬과 페이징](04-order-paging.md) | [이 페이지의 Java 버전](../java/03-where.md)
