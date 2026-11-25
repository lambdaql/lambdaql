<a id="9-kotlin-notes"></a>
# 9. Kotlin 노트

[Kotlin 트랙](README.md) | [매뉴얼 홈](../README.md) | 이전: [8. 프로시저와 DB 함수](08-procedures-functions.md) | 다음: [10. 규칙과 제한](10-rules-and-limits.md) | [이 페이지의 Java 버전: 9. Java 노트](../java/09-java-notes.md)

공통 페이지: [1. 개요](../01-overview.md) · [11. 오류와 언어](../11-errors-and-languages.md)

lambdaQL은 **같은 엔진**과 같은 API로 Kotlin과 Java를 지원합니다.
의미가 같은 Kotlin 람다 표현식과 Java 람다 표현식은 같은 HQL을 만듭니다.
이 장은 Kotlin에서 특별한 점을 한곳에 모았습니다.

<a id="contents"></a>
## 목차

- [9.1 설정](#91-setup)
- [9.2 동작하는 람다 표현식 형태](#92-lambda-forms-that-work)
- [9.3 직접 작성한 null 검사와 컴파일러의 null 검사](#93-only-your-null-checks-count)
- [9.4 같음 비교: `==`, `!=`, `===`와 `areEqual`](#94-equality----and-areequal)
- [9.5 null 연산자: `?.`, `?:`와 `!!`](#95-null-operators---and-)
- [9.6 `in`, 범위와 `when`](#96-in-ranges-and-when)
- [9.7 문자열](#97-strings)
- [9.8 캡처 값](#98-captured-values)
- [9.9 속성 참조와 스코프 함수](#99-property-references-and-scope-functions)
- [9.10 Kotlin 연산자](#910-kotlin-operators)
- [9.11 결과: 데이터 클래스, `Pair`와 `Triple`](#911-results-data-classes-pair-and-triple)
- [9.12 수정, 삽입, 삭제: 속성 대입과 `Dml.value`](#912-update-insert-delete-property-assignment-and-dmlvalue)
- [9.13 프로시저와 DB 함수](#913-procedures-and-db-functions)
- [9.14 Kotlin 제한(KC1 ~ KC21)](#914-kotlin-limits-kc1-to-kc21)
- [9.15 lambdaQL이 Kotlin 코드 실행과 다른 점](#915-where-lambdaql-differs-from-running-kotlin-code)

예제를 읽는 방법:

- 예제의 `assertEquals` 문자열이 lambdaQL이 만드는 HQL입니다.
- 람다 표현식에 직접 쓴 상수(숫자, 문자열, boolean, `null`, enum 상수, `const val`)는 HQL에 리터럴로 씁니다. 캡처 값과 계산한 값은 항상 바인드 파라미터(`:p1`, `:p2`, ...)입니다.
- `e0`은 루트 엔티티의 별칭이고, `j1`은 lambdaQL이 연관 경로에 대해 추가하는 조인의 별칭입니다. `where`에서는 연관이 없는 행이 조건에 맞을 수 있으면 `left join`, 맞을 수 없으면 inner `join`입니다. 경로를 `orderBy`나 `select`에만 쓰면 `left join`입니다. 서브쿼리 루트는 `s1`, `s2`, ...입니다([5. 서브쿼리](05-subquery.md) 참고).
- 엔티티는 Java로 작성한 예제 엔티티(`Order`, `Customer`, `Item`, `Coded`)입니다. Kotlin은 Java getter를 플랫폼 타입 속성으로 봅니다.
  `Order.status`는 `short`이므로 조건은 `it.status.toInt() == 1`로 쓰고, boolean `active`는 `it.isActive`로 읽습니다. NULL이 될 수 있는 Boolean 예제는 `Coded.vip`를 사용합니다.

---

<a id="91-setup"></a>
## 9.1 설정

| 항목 | 값 |
|---|---|
| Kotlin | 2.x. 설계는 kotlinc 1.7.22, 2.1.10, 2.2.21, 2.3.0, 2.3.21로 확인함 |
| JVM | Java 21 이상 |
| 컴파일러 옵션 | 기본값 `-Xsam-conversions=indy`를 유지. `-Xsam-conversions=class`는 사용하지 **마십시오**(KC1) |
| `-jvm-target` | 1.8, 11, 17, 21 모두 동작 |
| Kotlin 런타임 | lambdaQL은 의존성에 `kotlin-stdlib`나 `kotlin-reflect`를 추가하지 않음 |
| JPA 엔티티 | 일반적인 Kotlin JPA 엔티티(필드에 붙인 어노테이션도 괜찮음) |

자세한 내용: [2. 시작하기](02-getting-started.md#kotlin-compiler-settings).

<a id="92-lambda-forms-that-work"></a>
## 9.2 동작하는 람다 표현식 형태

lambdaQL 함수는 Java `Serializable` 함수형 인터페이스를 받습니다.
Kotlin에서는 **람다 표현식 리터럴**을 전달합니다. 후행 람다 표현식과 `it`을 사용하십시오.

<!-- region: ManualKotlinNotesTest.kt#where-chain -->
```kotlin
val q = qb.from(Order::class.java)
    .where { it.status.toInt() == 1 }
    .where { it.price > 100.0 }        // more where() calls are joined with AND
assertEquals("select e0 from Order e0 where e0.status = 1 and e0.price > 100.0", q.toHql().hql())
```

**데이터베이스별 SQL**

<!-- sql: to-hql -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>100.0
```

파라미터가 두 개 이상인 람다 표현식은 이름을 사용합니다. 필요 없는 파라미터에는 `_`를 사용하십시오.

<!-- region: ManualKotlinNotesTest.kt#named-params -->
```kotlin
// lambdas with two parameters use names
val q = qb.from(Order::class.java)
    .where { o, sub -> sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
    .orderBy { o -> desc(o.price).asc(o.id) }
assertEquals("select e0 from Order e0 where exists (select 1 from Item s1 where s1.order.id = e0.id)"
    + " order by e0.price desc, e0.id asc", q.toHql().hql())
```

Kotlin 속성은 getter로 읽습니다. `it.price`는 Java 호출 `getPrice()`와 같은 바이트 opcode입니다.
lambdaQL은 getter를 JPA 속성에 매핑합니다(규칙 R10).
Java 엔티티의 boolean 속성 `active`는 getter가 `isActive()`이므로 Kotlin에서는 `it.isActive`로 읽습니다. lambdaQL은 이를 `active`에 매핑합니다.

| 형태 | 동작하는가? |
|---|---|
| `where { it.isActive }`(람다 표현식 리터럴) | 예 |
| `run { }`, `let { }`, `forEach { }`, inline이 아닌 함수, `suspend` 함수 안의 람다 표현식 | 예 |
| 호출자가 lambdaQL 타입의 `inline fun` 파라미터로 전달한 람다 표현식 | 예 |
| SAM 생성자, 먼저 `val`에 저장한 경우도 포함 | 예 |
| 조건에 전달한 속성 참조나 Java getter 참조: `where(Order::isActive)` | **아니요**(`LQL-1009`). 람다 표현식을 사용 |
| `val f: (Order) -> Boolean = { it.isActive }; where(f)` | **아니요**(KC4) |
| `object : ...`(람다 표현식 대신 객체 식) | **아니요**(KC5) |
| `inline fun` 본문 안에서 만든 람다 표현식 | **아니요**(KC2) |
| `-Xsam-conversions=class`로 컴파일 | **아니요**(KC1) |

예제 코드: [`ManualKotlinNotesTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinNotesTest.kt)(`where-chain`, `named-params`).

<a id="93-only-your-null-checks-count"></a>
## 9.3 직접 작성한 null 검사와 컴파일러의 null 검사

lambdaQL은 람다 표현식을 호출하지 않고 바이트 opcode를 분석해 HQL을 만듭니다.
그래서 몇몇 Kotlin 규칙은 다르게 동작합니다.

| 주제 | 규칙 |
|---|---|
| 연관 경로 | `it.customer?.name` 같은 경로는 호출 체인이 아니라 **컬럼**입니다. `where`에서 to-one 연관은 고객이 없는 주문이 조건에 맞을 수 있으면 `left join`, 맞을 수 없으면 inner join입니다. 고객이 없는 주문만 찾으려면 `it.customer == null`이라고 쓰십시오. |
| `?.`와 조인 | `?.`로 지난 비교는 고객이 없는 주문에서 고객의 컬럼을 NULL로 보고 SQL 의미로 평가합니다. `it.status.toInt() == 1 \|\| it.customer?.name == "Kim"`, `it.customer?.name == null`, null 가드는 고객이 없는 주문이 조건에 맞을 수 있으므로 `left join`입니다. `?.`와 `!!`는 한 조건 안에서 섞어 써도 됩니다. |
| `!!` | `!!`는 값을 바꾸지 않습니다. `it.customer!!.name`과 `it.customer?.name`은 같은 컬럼입니다. 다만 `where`에서 연관 단계를 `!!`로 지난 비교는 고객이 있는 주문에서만 참입니다(`it.customer!!.name == null`은 inner join). |
| `==`, `===` | 객체 사이의 `==`(그리고 `===`)는 객체 동일성이 아니라 **값 비교**를 뜻합니다. |

NULL이 될 수 있는 숫자 속성(`Integer`)을 상수와 비교하면 Kotlin 컴파일러가 언박싱 전에 null 검사를 넣습니다.
lambdaQL은 소스에 쓰지 않은 이 검사를 읽지 않습니다. 그래서 Java와 같은 SQL 의미가 됩니다.
`it.cnt == 1`은 `e0.cnt = 1`이고, `it.cnt != 1`은 `e0.cnt <> 1`이라 `cnt`가 NULL인 행은 반환하지 않습니다. NULL 행이 필요하면 `it.cnt == null || it.cnt != 1`처럼 직접 쓰십시오.

이 동작의 예는 [9.4](#94-equality----and-areequal)에 있습니다.

<a id="94-equality----and-areequal"></a>
## 9.4 같음 비교: `==`, `!=`, `===`와 `areEqual`

<a id="how-kotlin-compiles-"></a>
### Kotlin이 `==`를 컴파일하는 방법

| Kotlin | 컴파일 결과 | HQL |
|---|---|---|
| non-null 기본형 두 개: `it.qty == 5` | 비교 분기(Java와 같음) | `e0.qty = 5` |
| nullable 기본형과 상수: `it.cnt == 1`(`Integer`) | null 검사 + 비교 | `e0.cnt = 1`(null 검사는 읽지 않음) |
| `it.cnt != 1`(`Integer`) | null 검사 + 비교 | `e0.cnt <> 1`(NULL 행은 반환되지 **않음**) |
| 참조이거나 양쪽 모두 nullable: `it.description == text`, `it.cnt == q`(`q: Int?`) | `Intrinsics.areEqual(a, b)` 호출 | `e0.description = :p1`(캡처 값 `null`: 일치하는 행 없음) |
| `it.description != "x"` | `!Intrinsics.areEqual(a, b)` | `e0.description <> 'x'`(NULL 행은 반환되지 **않음**) |
| 엔티티 값 두 개: `it.description == it.custName` | `Intrinsics.areEqual(a, b)` | `e0.description = e0.custName`(어느 한쪽이 NULL이면 불일치) |
| `it.id === q`, `!==` | `if_acmpeq` / `if_acmpne` | `==`와 같음 |
| enum: `it.kind == Order.Kind.RUSH` | `if_acmpne` | `e0.kind = org.lambdaql.query.Order$Kind.RUSH`(전체 클래스 이름을 쓴 리터럴) |

`Intrinsics.areEqual`은 `==` 뒤에 있는 Kotlin 런타임 함수입니다. lambdaQL은 이를 이름으로 인식하고 **값 비교**로 읽습니다(R11).
엔티티 값은 SQL 의미를 따릅니다. 어느 한쪽이 NULL이면 일치하지 않으며, NULL과 NULL도 마찬가지입니다.
캡처 값 `null`은 NULL로 바인딩되어 어떤 행에도 일치하지 않습니다(`WARN` 수준 `LQL-4045`). NULL을 찾으려면 `x == null`이라고 쓰십시오.

<!-- region: ManualKotlinNotesTest.kt#hidden-null-check -->
```kotlin
// cnt is a nullable Integer: the null check Kotlin adds before the unboxing is not read (SQL meaning)
val one = qb.from(Order::class.java).where { it.cnt == 1 }
assertEquals("select e0 from Order e0 where e0.cnt = 1", one.toHql().hql())
val notOne = qb.from(Order::class.java).where { it.cnt != 1 }          // NULL rows are left out, as in SQL
assertEquals("select e0 from Order e0 where e0.cnt <> 1", notOne.toHql().hql())
// both sides nullable: Intrinsics.areEqual, a value comparison
val same = qb.from(Order::class.java).where { it.cnt == q }
assertEquals("select e0 from Order e0 where e0.cnt = :p1", same.toHql().hql())
// two entity values: NULL on either side does not match
val columns = qb.from(Order::class.java).where { it.description == it.custName }
assertEquals("select e0 from Order e0 where e0.description = e0.custName", columns.toHql().hql())
```

<a id="plain--and-"></a>
### 단순한 `=`와 `<>`

`Intrinsics.areEqual`로 컴파일되는 비교에 lambdaQL은 단순한 `=`와 `<>`를 씁니다.
`is [not] distinct from`은 쓰지 않습니다. 이 형태는 Oracle에서 `decode(...)`가 되어 인덱스를 사용할 수 없습니다.

<!-- region: ManualKotlinNotesTest.kt#equality -->
```kotlin
// plain = and <>: a row whose description is NULL matches neither
val same = qb.from(Order::class.java).where { it.description == text }
assertEquals("select e0 from Order e0 where e0.description = :p1", same.toHql().hql())
val other = qb.from(Order::class.java).where { it.description != "x" }
assertEquals("select e0 from Order e0 where e0.description <> 'x'", other.toHql().hql())
// NULL rows too: write the null check yourself
val orNull = qb.from(Order::class.java).where { it.description == null || it.description != "x" }
assertEquals("select e0 from Order e0 where e0.description is null or e0.description <> 'x'", orNull.toHql().hql())
```

> [!WARNING]
> **Kotlin의 non-null 타입은 증거가 되지 않습니다.** `var code: String` 속성에도 데이터베이스에서 온 NULL이 들어 있을 수 있습니다.
> Hibernate가 검사 없이 컬럼 값을 필드에 쓰기 때문입니다.
> lambdaQL은 Kotlin 타입이 아니라 JPA 메타데이터를 봅니다.

<a id="-and-"></a>
### `===`와 `!==`

lambdaQL에서 `===`는 `==`와 마찬가지로 **값 비교**를 뜻합니다(R17).
쿼리에는 객체 동일성이 없습니다. Kotlin 자체는 값이 1000인 서로 다른 두 `Long` 박스에 대해 `false`를 내지만, lambdaQL은 `true`를 냅니다.

<a id="bigdecimal"></a>
### `BigDecimal`

Kotlin에서 `BigDecimal`에 대한 `==`는 `equals`를 호출하며, 이는 스케일까지 비교합니다(`1.0 != 1.00`).
SQL `=`는 값만 비교합니다. **lambdaQL은 값 비교를 사용합니다.**
`BigDecimal`, `String`, `LocalDate`, `BigInteger`에 대한 `<`, `>`, `<=`, `>=`를 지원합니다(`compareTo`로 컴파일됨).

<a id="booleans"></a>
### boolean

<!-- region: ManualKotlinNotesTest.kt#booleans -->
```kotlin
val active = qb.from(Order::class.java).where { it.isActive }
assertEquals("select e0 from Order e0 where e0.active = true", active.toHql().hql())
val inactive = qb.from(Order::class.java).where { !it.isActive }
assertEquals("select e0 from Order e0 where e0.active = false", inactive.toHql().hql())
```

`!it.isActive`는 `e0.active = false`가 됩니다.

<a id="java-entities-from-kotlin-platform-types"></a>
### Kotlin에서 쓰는 Java 엔티티(플랫폼 타입)

엔티티가 Java 클래스이면 Kotlin은 그 getter를 플랫폼 타입(`Long!`)으로 봅니다.
이 장의 예제가 모두 이 경우입니다. 경로 `it.customer?.name`은 조인한 컬럼입니다(`where`의 조인 종류는 [3.10](03-where.md#310-association-paths) 참고).

예제 코드: [`ManualKotlinNotesTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinNotesTest.kt)(`hidden-null-check`, `equality`, `booleans`).

<a id="95-null-operators---and-"></a>
## 9.5 null 연산자: `?.`, `?:`와 `!!`

<a id="summary"></a>
### 요약

| Kotlin | lambdaQL에서의 의미 | HQL 예 |
|---|---|---|
| `it.customer?.name` | 컬럼 `customer.name`. 고객이 없는 주문에서는 NULL 컬럼으로 비교(SQL 의미) | `j1.name` + `join` 또는 `left join e0.customer j1` |
| `it.customer!!.name` | 같은 컬럼. 비교는 고객이 있는 주문에서만 참 | `j1.name` + `join` 또는 `left join e0.customer j1` |
| `it.customer?.id == 1` | 경로가 대상의 `@Id`로 끝남: FK 컬럼, 조인 없음 | `e0.customer is not null and e0.customer.id = 1` |
| `it.customer == null` | 고객이 없는 주문만 | `e0.customer is null`(FK 컬럼, 조인 없음) |
| `it.customer != null` | 고객이 있는 주문만 | `e0.customer is not null`(FK 컬럼, 조인 없음) |
| `it.description ?: ""`(값) | 기본값 | `coalesce(e0.description, '')` |
| `it.vip ?: false`(조건) | NULL은 false로 취급 | `e0.vip is not null and e0.vip = true` |
| `lo!!`(캡처 값) | `!!`는 무시됨. `lo`가 null이면 NULL을 바인딩하므로 일치하는 행이 없음(`DEBUG` 수준 `LQL-4045`, [9.8](#98-captured-values) 참고) | `:p1` |

<a id="-and--on-association-paths"></a>
### 연관 경로의 `?.`와 `!!`

<!-- region: ManualKotlinNotesTest.kt#path-forms -->
```kotlin
// ?. and !! on a path: the same column; no order without a customer can match, so both are an inner join
val safe = qb.from(Order::class.java).where { it.customer?.name == "Kim" }
assertEquals("select e0 from Order e0 join e0.customer j1 where j1.name = 'Kim'", safe.toHql().hql())
val sure = qb.from(Order::class.java).where { it.customer!!.name == "Kim" }
assertEquals("select e0 from Order e0 join e0.customer j1 where j1.name = 'Kim'", sure.toHql().hql())
// a path that ends at the @Id of the target: the FK column, no join
val fk = qb.from(Order::class.java).where { it.customer?.id == 1 }
assertEquals("select e0 from Order e0 where e0.customer is not null and e0.customer.id = 1", fk.toHql().hql())
```

**데이터베이스별 SQL**

<!-- sql: path-literal -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name='Kim'
```

경로는 호출 체인이 아니라 **컬럼**입니다. 위 조건은 고객이 없는 주문이 맞을 수 없으므로 `?.`와 `!!` 모두 inner join입니다.
그래서 경로 앞에 `it.customer != null &&`를 쓸 필요가 없습니다.
`it.customer?.id == 1`은 Kotlin이 넣은 null 검사(`e0.customer is not null`)를 그대로 쓰지만, 조인은 없습니다.

**`?.`로 쓴 조건은 Kotlin처럼 고객 없는 주문을 남깁니다.** Kotlin에서 `it.customer?.name == null`은 고객이 없을 때 `true`입니다.
lambdaQL은 이 조건을 `left join`으로 써서 고객이 없는 주문도 반환합니다. `!!`로 쓴 비교는 고객이 있는 주문에서만 참이므로 inner join이고, 고객의 이름이 없는 주문만 반환합니다.

<!-- region: ManualKotlinNotesTest.kt#null-paths -->
```kotlin
// orders without a customer: the FK column, no join
val none = qb.from(Order::class.java).where { it.customer == null }
assertEquals("select e0 from Order e0 where e0.customer is null", none.toHql().hql())
// orders without a customer, and orders of a customer without a name: a left join
val safe = qb.from(Order::class.java).where { it.customer?.name == null }
assertEquals("select e0 from Order e0 left join e0.customer j1 where j1.name is null", safe.toHql().hql())
// only orders of a customer without a name: the inner join leaves out orders without a customer
val sure = qb.from(Order::class.java).where { it.customer!!.name == null }
assertEquals("select e0 from Order e0 join e0.customer j1 where j1.name is null", sure.toHql().hql())
```

**데이터베이스별 SQL**

<!-- sql: no-association -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id is null
```

<a id="-is-just-for-the-compiler"></a>
### `!!`는 컴파일러를 위한 것일 뿐

속성이 `Double?`이면 Kotlin은 `it.total > 10.0`을 컴파일하지 않습니다.
`it.total!! > 10.0`이라고 쓰십시오. lambdaQL은 `!!`를 무시합니다. lambdaQL은 람다 표현식을 호출하지 않으므로 NPE는 발생하지 않습니다.
값이 NULL인 행은 반환되지 않습니다(SQL 규칙).

<a id="-elvis"></a>
### `?:`(엘비스)

select 값과 문자열 조건에서 `?:`는 `coalesce`가 됩니다(규칙 R15).

<!-- region: ManualKotlinNotesTest.kt#elvis-value -->
```kotlin
// ?: in a select value: coalesce
val text = qb.from(Order::class.java).select { it.description ?: "" }
assertEquals("select coalesce(e0.description, '') from Order e0", text.toHql().hql())
// ?: after ?. in a select value is rejected (J1)
val error = assertThrows(LambdaQLException::class.java) {
    qb.from(Order::class.java).select { it.customer?.name ?: "none" }
}
assertEquals("LQL-2022", error.code())
// ?: with a number: coalesce too
val number = qb.from(Order::class.java).where { (it.total ?: 0.0) > 3.0 }
assertEquals("select e0 from Order e0 where coalesce(e0.total, 0.0) > 3.0", number.toHql().hql())
// in a string condition, ?: is coalesce too
val x = qb.from(Order::class.java).where { (it.description ?: "") == "x" }
assertEquals("select e0 from Order e0 where coalesce(e0.description, '') = 'x'", x.toHql().hql())
```

- 숫자 조건의 `?:`도 `coalesce`입니다: `(it.total ?: 0.0) > 3.0`은 `coalesce(e0.total, 0.0) > 3.0`입니다.
- `?.` 뒤의 `?:`를 select 값으로 쓰면 거부됩니다(`LQL-2022`, J1).
- 컬럼에 `coalesce`가 씌워지므로 `description`의 일반 인덱스는 사용되지 않습니다.
- DML 값의 `?:`는 [7.2](07-2-update.md#add-to-a-value-default-for-null)를 참고하십시오.

**boolean 엘비스**는 **조건에서** 지원합니다.

<!-- region: ManualKotlinNotesTest.kt#boolean-elvis -->
```kotlin
// Coded.vip is a nullable Boolean: in a condition, ?: false treats NULL as false
val vip = qb.from(Coded::class.java).where { it.vip ?: false }
assertEquals("select e0 from Coded e0 where e0.vip is not null and e0.vip = true", vip.toHql().hql())
val notVip = qb.from(Coded::class.java).where { !(it.vip ?: false) }
assertEquals("select e0 from Coded e0 where e0.vip is null or e0.vip = false", notVip.toHql().hql())
```

**값이나 정렬 키**로 쓰는 boolean 엘비스(`select { it.vip ?: false }`)는 지원하지 않습니다(KC21, `LQL-2022`).
정렬에는 키 `it.vip`를 사용하고 `asc(key, Nulls.LAST)`나 `desc(key, Nulls.FIRST)`처럼 `Nulls`로 NULL 위치를 고르십시오([4. 정렬과 페이징](04-order-paging.md#43-direction)).

<a id="-in-an-assignment"></a>
### 대입에서의 `?.`

`set { }`과 `values { }`에서 `it.customer?.id = cid`는 **항상 대입합니다**. `?.`는 무시됩니다. 예제는 [7.2](07-2-update.md#change-an-association-foreign-key)에 있습니다.

예제 코드: [`ManualKotlinNotesTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinNotesTest.kt)(`path-forms`, `null-paths`, `elvis-value`, `boolean-elvis`).

<a id="96-in-ranges-and-when"></a>
## 9.6 `in`, 범위와 `when`

자세한 내용과 더 많은 예제: [3.9](03-where.md#39-in-with-a-captured-collection), [3.14](03-where.md#314-ranges-kotlin-only), [3.15](03-where.md#315-when-kotlin-only).

| Kotlin | HQL | 참고 |
|---|---|---|
| `it.id in ids`(캡처한 컬렉션) | `e0.id in (:p1)` | 비어 있으면 일치하는 행 없음. `null` 요소는 오류(`LQL-4008`) |
| `it.id !in ids` | `e0.id not in (:p1)`. NULL이 될 수 있는 컬럼도 같음(NULL 행은 반환되지 않음) | |
| `it.status.toInt() in 1..5` | `1 <= e0.status and e0.status < 6` | |
| `it.status.toInt() !in 1..5` | `1 > e0.status or e0.status >= 6` | NULL 행은 반환되지 않음 |
| `in 1 until 5`, `in 1..<5` | `1 <= e0.status and e0.status < 5` | |
| `it.status.toInt() in lo..hi` | `:p1 <= e0.status and e0.status <= :p2` | |
| `when { a -> true; b -> true; else -> false }` | `a or b` | |
| `when (it.status.toInt()) { 1, 2 -> true; else -> false }` | **거부됨**(`LQL-2010`) | `\|\|`로 작성 |
| `it.customer?.id in sub.from(...).select { ... }` | `e0.customer.id in (select ...)` | 서브쿼리, [5.4](05-subquery.md#54-in) 참고 |
| `x !in sub.from(...).select { ... }` | `x not in (select ...)`(SQL 의미) | [5.5](05-subquery.md#55-not-in-is-written-as-not-exists) 참고 |
| `it.id in listOf(1L, 2L)`(람다 표현식 안에서 만든 리스트) | **거부됨**(KC8, `LQL-3025`) | 리스트는 밖에서 만들 것 |

<!-- region: ManualKotlinNotesTest.kt#ranges -->
```kotlin
val closed = qb.from(Order::class.java).where { it.status.toInt() in 1..2 }
assertEquals("select e0 from Order e0 where 1 <= e0.status and e0.status < 3", closed.toHql().hql())
val outside = qb.from(Order::class.java).where { it.status.toInt() !in 1..2 }
assertEquals("select e0 from Order e0 where 1 > e0.status or e0.status >= 3", outside.toHql().hql())
val until = qb.from(Order::class.java).where { it.status.toInt() in 1 until 3 }
assertEquals("select e0 from Order e0 where 1 <= e0.status and e0.status < 3", until.toHql().hql())
val captured = qb.from(Order::class.java).where { it.status.toInt() in lo..hi }
assertEquals("select e0 from Order e0 where :p1 <= e0.status and e0.status <= :p2", captured.toHql().hql())
// a captured collection
val inIds = qb.from(Order::class.java).where { it.id in ids }
assertEquals("select e0 from Order e0 where e0.id in (:p1)", inIds.toHql().hql())
// a nullable target gets no null guard: NULL rows are returned by neither in nor !in (SQL)
val notIn = qb.from(Order::class.java).where { it.cnt !in counts }
assertEquals("select e0 from Order e0 where e0.cnt not in (:p1)", notIn.toHql().hql())
// when without a subject, with true / false branches: OR
val anyOf = qb.from(Order::class.java).where { when { it.status.toInt() == 3 -> true; it.price > 200.0 -> true; else -> false } }
assertEquals("select e0 from Order e0 where e0.status = 3 or e0.price > 200.0", anyOf.toHql().hql())
```

**nullable 대상에는 null 가드가 붙지 않습니다**. Kotlin은 `null in 1..5`를 false로, `null !in 1..5`를 true로 정의합니다.
lambdaQL은 가드를 추가하지 않습니다. NULL 대상은 `in`에서도 반환되지 않고 `!in`에서도 반환되지 **않습니다**(SQL 규칙).
NULL 행을 원하면 `it.cnt == null || it.cnt !in 1..5`라고 쓰십시오.

캡처한 컬렉션에 대한 `!in`도 같습니다. 위 예제의 `x not in (:p1)`은 NULL 행을 반환하지 않습니다(SQL 의미).

예제 코드: [`ManualKotlinNotesTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinNotesTest.kt)(`ranges`).

<a id="97-strings"></a>
## 9.7 문자열

엔티티 값에 대한 `startsWith`, `endsWith`, `contains`(`ignoreCase` 포함)는 `like`가 됩니다([3.8](03-where.md#38-strings)).
다른 문자열 함수는 lambdaQL이 람다 표현식을 읽을 때 `TranslationException`(`LQL-3008`)으로 거부합니다. `lowercase`, `length`, `isNullOrEmpty`, `isBlank`, `equals(s, ignoreCase = true)`가 이에 해당합니다.
조건은 값을 직접 비교하는 형태로 쓰십시오.

| Kotlin | lambdaQL |
|---|---|
| `it.description.equals(text)` | `==`와 같음: `e0.description = :p1` |
| `(it.description ?: "") == "x"` | `coalesce(e0.description, '') = 'x'`([9.5](#-elvis)) |
| 위의 문자열 함수 | 거부됨(`LQL-3008`) |
| 문자열 결합과 템플릿(`"${it.description}-x"`, `"$a-$b" == it.description`, `it.description + "x"`) | 거부됨(`LQL-2007`, KC17). 캡처 값만 쓰는 템플릿도 거부되므로 문자열은 람다 표현식 밖에서 만들어 캡처 |

<a id="98-captured-values"></a>
## 9.8 캡처 값

| 캡처 대상 | lambdaQL의 사용 방법 |
|---|---|
| `val`(`Long?`, `String`, ...도 마찬가지) | 바인드 파라미터 |
| `var` | 바인드 파라미터. 값은 **쿼리가 실행될 때마다** 읽음 |
| 바깥 클래스의 속성(`this.minStatus`, `private`도 마찬가지) | 쿼리가 실행될 때마다 읽음 |
| `companion object`의 `val`, 최상위 `val`, `object`의 `val` | 바인드 파라미터 |
| `const val` | HQL에 리터럴로 씀 |
| 람다 표현식 안에서 캡처 변수에 쓰기(`n++`) | **거부됨**(KC12) |

<!-- region: ManualKotlinNotesTest.kt#order-repo -->
```kotlin
class OrderRepo(private val qb: QueryBuilder) {
    private val minStatus = 2

    fun open(lo: Int?) = qb.from(Order::class.java)
        .where { it.status >= minStatus }                // a private property: read each time the query runs
        .where { lo == null || it.status > lo }          // lo == null: no filter
}
```

<!-- region: ManualKotlinNotesTest.kt#order-repo-use -->
```kotlin
val all = repo.open(null)
val above = repo.open(2)
assertEquals("select e0 from Order e0 where e0.status >= :p1", all.toHql().hql())
assertEquals("select e0 from Order e0 where e0.status >= :p1 and e0.status > :p2", above.toHql().hql())
```

캡처 값 `null`은 같은 값에 대한 null 가드 아래(`lo == null || ...`)에서 허용됩니다. 가드는 쿼리를 만들 때 애플리케이션에서 결정합니다.
`lo == null`이면 이 `where`는 필터를 추가하지 않고, 값이 있으면 `e0.status > :p2`가 됩니다.

`==`, `!=`에 쓰인 캡처 값 `null`은 NULL로 바인딩되므로 일치하는 행이 없습니다(`WARN` 수준 `LQL-4045`).
가드 없이 쓴 크기 비교(`lo == null`일 때 `it.status > lo!!`)는 NULL을 바인딩하므로 일치하는 행이 없습니다(`DEBUG` 수준 `LQL-4045`). `lo!!`와 언박싱은 실행하지 않습니다.
캡처 값에 메서드를 호출하는 식(`text!!.trim()` 등)은 값이 `null`이면 값을 계산할 때 오류(`LQL-3027`)를 던집니다.

<!-- region: ManualKotlinNotesTest.kt#captured-null -->
```kotlin
// a captured null in == is bound as NULL: no row matches
val text: String? = null
val none = qb.from(Order::class.java).where { it.description == text }
assertEquals("select e0 from Order e0 where e0.description = :p1", none.toHql().hql())
assertEquals(0, none.toList().size)
// a method call on a captured null fails when the value is computed
val error = assertThrows(LambdaQLException::class.java) {
    qb.from(Order::class.java).where { it.description == text!!.trim() }.toList()
}
assertEquals("LQL-3027", error.code())
```

예제 코드: [`ManualKotlinNotesTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinNotesTest.kt)(`order-repo`, `order-repo-use`, `captured-null`).

<a id="99-property-references-and-scope-functions"></a>
## 9.9 속성 참조와 스코프 함수

<a id="property-references"></a>
### 속성 참조

| 형태 | 동작하는가? |
|---|---|
| 조건에 전달한 Kotlin 속성 참조나 Java getter 참조(`where(Order::isActive)`) | 아니요(`LQL-1009`). 람다 표현식 `where { it.isActive }`를 사용 |
| Java 합성 속성 `JOrder::price` | 아니요. Kotlin 자체가 컴파일하지 않음(KC18). |
| `Order::price.getter` 및 다른 `kotlin-reflect` API | 아니요(KC19) |
| 직접 만든 Kotlin `fun interface`에 전달한 참조 | 아니요(KC3). 람다 표현식 리터럴을 전달 |

<a id="scope-functions-and-inline-functions-in-conditions"></a>
### 조건 안의 스코프 함수와 inline 함수

직접 만든 `inline fun`은 조건에서 동작합니다. 컴파일러가 본문을 람다 표현식 안에 복사하므로 lambdaQL은 일반 조건으로 봅니다.

<!-- region: ManualKotlinNotesTest.kt#inline-fun -->
```kotlin
/** Your own inline function: the compiler copies the body into the lambda, so lambdaQL reads a plain condition. */
private inline fun Order.isOpenInline() = status.toInt() == 1
```

<!-- region: ManualKotlinNotesTest.kt#scope-functions -->
```kotlin
val inline = qb.from(Order::class.java).where { it.isOpenInline() }     // inline fun Order.isOpenInline()
assertEquals("select e0 from Order e0 where e0.status = 1", inline.toHql().hql())
```

| 형태 | 지원 |
|---|---|
| 직접 만든 `inline fun` | 예 |
| `?.let { }`, `run { }`, `with(it) { }`, `also { }` | 아니요(`LQL-2013`). 조건을 직접 작성 |
| 직접 만든 inline이 아닌 확장 함수나 확장 속성 | 아니요(KC11). `inline`으로 만들거나 조건을 직접 작성 |
| `takeIf { }` | 아니요(KC6, `LQL-2021`) |

수정과 삽입 람다 표현식에서 `apply`, `with`, `also`는 거부됩니다(`LQL-2003`, [9.12](#912-update-insert-delete-property-assignment-and-dmlvalue) 참고).

예제 코드: [`ManualKotlinNotesTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinNotesTest.kt)(`inline-fun`, `scope-functions`).

<a id="910-kotlin-operators"></a>
## 9.10 Kotlin 연산자

이 표는 연산자 형태 126개에 대한 컴파일러 확인 결과에서 나왔습니다(kotlinc 2.3.21, 설계 문서 KOTLIN.md 9절).

| 연산자 | lambdaQL 람다 표현식 안에서 |
|---|---|
| 숫자에 대한 `< > <= >=`(nullable에는 `!!` 사용) | 지원 |
| `BigDecimal`, `String`, `LocalDate`, `BigInteger`에 대한 `< > <= >=` | 지원(`compareTo`로 컴파일됨) |
| `x.compareTo(y) > 0` | 지원, `x > y`로 읽음 |
| `==`, `!=` | 지원([9.4](#94-equality----and-areequal)) |
| `===`, `!==` | 지원, 값 비교 |
| enum `==` | 지원 |
| 엔티티 `==` 엔티티 | **거부됨**(KC13, `LQL-3011`). id를 비교할 것 |
| 값 객체(`@Embedded`) `==`, `!=` | 지원. 컬럼마다 펼침(B6, [3.10](03-where.md#310-association-paths) 참고). 크기 비교는 `LQL-3012` |
| `+ - *`, 단항 `-` | 지원 |
| `%` | 지원, `mod`로 출력 |
| 정수 `/` | **주의.** 데이터베이스마다 결과가 다름(MariaDB는 `5/2`가 `2.5`, Kotlin은 `2`). lambdaQL은 `(e0.cnt / 2)`처럼 그대로 씀 |
| `BigDecimal` / `BigInteger`의 `+ - * %`, 단항 `-` | 지원, SQL 연산자로 출력 |
| `BigDecimal` `/` | 지원(Kotlin은 `divide(x, HALF_EVEN)` 사용). 결과 스케일은 DB를 따름. 다른 `divide` 형태는 거부됨 |
| `in a..b`, `until`, `..<` | 지원([9.6](#96-in-ranges-and-when)) |
| `x in list`, `x !in list` | 지원([9.6](#96-in-ranges-and-when)) |
| 문자열 `+`, 문자열 템플릿 | **거부됨**(`LQL-2007`). 참고: NULL에 대해 Kotlin은 `"null"`을, SQL은 NULL을 냄 |
| boolean 중위 함수 `and`, `or`, `xor` | **거부됨**(`LQL-2013`). `&&`와 `\|\|`를 사용(단락 평가를 함. `and`/`or`는 하지 않음) |
| 캡처한 배열에 대한 `arr[0]` | 캡처 값으로 취급하거나 명확한 오류 |
| 엔티티 경로에 대한 `it.tags["k"]`, `it.lines[0]` | **거부됨** |
| 엔티티의 `operator` 함수(`plus`, `compareTo`), 직접 만든 inline이 아닌 `infix` | **거부됨**(KC11) |
| `@JvmInline value class` 속성 | **거부됨**(KC16) |

<a id="no-kotlin-only-operator-api"></a>
### Kotlin 전용 연산자 API는 없음

lambdaQL에는 **Kotlin 전용 연산자 API가 없습니다**(결정됨). 일반적인 Java 메서드 이름이 이미 Kotlin 연산자로 동작합니다.

| Kotlin 형태 | 동작하는 이유 | 상태 |
|---|---|---|
| `x in sub.from(...).select { ... }` | Kotlin은 `x in v`를 `v.contains(x)`로 바꿈 | 유지 |
| `Agg.sum(x)!! + 1 > 3` | 자리표시자 값에 대한 일반 산술 | 유지 |
| 정렬 연산자(`+key`, `-key`) | Java 사용자에게 이상한 메서드가 추가되고 Kotlin 전용 확장이 필요함 | 거부 |
| DB 함수용 `invoke` 연산자 | DB 함수 선언 방식(인터페이스 정적 메서드)과 충돌 | 거부 |
| `o["price"]` | 문자열 경로. 타입 안전성 없음 | 거부 |
| `infix` 함수 | Java 메서드는 `infix`가 될 수 없음 | 불가능 |
| `between` | `x in a..b`로 작성(nullable에는 `!!`, `LocalDate?`에는 `!!` 필요) | API 불필요 |

<a id="911-results-data-classes-pair-and-triple"></a>
## 9.11 결과: 데이터 클래스, `Pair`와 `Triple`

아래 예제의 데이터 클래스는 `data class OrderRow(val id: Long?, val customerName: String?, val price: Double)`, `data class CustomerStat(val customerId: Int?, val count: Long, val total: Double?)`입니다.

<!-- region: KotlinQueryTest.kt#data-class -->
```kotlin
val q = qb.from(Order::class.java).select { OrderRow(it.id, it.customer?.name, it.price) }
assertEquals("select e0.id, j1.name, e0.price from Order e0 left join e0.customer j1", q.toHql().hql())
```

<!-- region: KotlinQueryTest.kt#group-by -->
```kotlin
val q = qb.from(Order::class.java)
    .groupBy { it.customer?.id }
    .having { Agg.sum(it.price) > 200 }
    .select { CustomerStat(it.customer?.id, Agg.count(), Agg.sum(it.price)) }
assertEquals("select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id"
    + " having sum(e0.price) > 200.0", q.toHql().hql())
```

<!-- region: ManualKotlinNotesTest.kt#group-pair -->
```kotlin
// a Pair as the group key: two keys
val q = qb.from(Order::class.java)
    .where { it.customer != null }
    .groupBy { it.customer?.id to it.status }
    .select { Triple(it.customer?.id, it.status, Agg.count()) }
assertEquals("select e0.customer.id, e0.status, count(*) from Order e0 where e0.customer is not null"
    + " group by e0.customer.id, e0.status", q.toHql().hql())
```

`Pair` 결과와 `listOf(...)` 그룹 키의 예는 [6장](06-select-group.md)에 있습니다.

| 규칙 | 참고 |
|---|---|
| 데이터 클래스에는 nullable 타입을 사용 | `sum`, `avg`, `min`, `max`는 NULL일 수 있음. non-null 파라미터에 NULL이 들어가면 객체를 만들 때 NPE가 발생함(J4) |
| `Pair`, `Triple` | 결과와 그룹 키로 허용 |
| `groupBy` 안의 `listOf(...)` | 허용(람다 표현식 안에서 만든 리스트가 허용되는 유일한 곳) |
| `groupBy` 안의 `arrayOf(...)`, `setOf(...)` | 허용 안 됨 |
| 파생 테이블이나 CTE의 행 타입 | Kotlin 데이터 클래스(또는 Java record)만. 컬럼은 `componentN` 순서를 따름(J5) |
| `Agg` 함수 | `import org.lambdaql.Agg.sum`이 동작함. 컬렉션에 대한 Kotlin `sum()` / `count()`와 충돌하지 않음 |

자세한 내용: [6. 조회, 집계, 그룹화](06-select-group.md).

예제 코드: [`KotlinQueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)(`data-class`, `group-by`), [`ManualKotlinNotesTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinNotesTest.kt)(`group-pair`).

<a id="912-update-insert-delete-property-assignment-and-dmlvalue"></a>
## 9.12 수정, 삽입, 삭제: 속성 대입과 `Dml.value`

Kotlin 속성 대입은 Java와 같은 setter 호출로 컴파일되므로 규칙도 같습니다.

<!-- region: ManualKotlinDmlTest.kt#update-fk -->
```kotlin
// the FK column: the ?. assignment always assigns
val fk = qb.update(Order::class.java).set { it.customer?.id = cid }.where { it.id == first }
assertEquals("update Order e0 set e0.customer.id = :p1 where e0.id = :p2", fk.toHql().hql())
// no customer
val none = qb.update(Order::class.java).set { it.customer = null }.where { it.status.toInt() == 3 }
assertEquals("update Order e0 set e0.customer = null where e0.status = 3", none.toHql().hql())
// a captured entity: its @Id is bound (U11)
val entity = qb.update(Order::class.java).set { it.customer = kim }.where { it.id == third }
assertEquals("update Order e0 set e0.customer = :p1 where e0.id = :p2", entity.toHql().hql())
```

| Kotlin 형태 | 의미 |
|---|---|
| `value(o.status, st)` | `import org.lambdaql.Dml.value`로 가져온 함수. `val` 속성(setter 없음)을 가진 엔티티에 사용. 속성 대입과 한 람다 표현식 안에서 섞을 수 있음([7.3](07-dml.md#73-two-ways-to-assign-property-or-dmlvalue) 참고) |
| `it.cnt += 1` | 지원([7.2](07-2-update.md#add-to-a-value-default-for-null) 참고) |
| `it.custName = it.description ?: "none"` | 지원: `coalesce(e0.description, 'none')` |
| `it.cnt++`, `--` | 거부됨(`LQL-2003`). `it.cnt += 1`로 작성 |
| `set` 안의 `apply { }`, `with(it) { }`, `also { }` | 거부됨(`LQL-2003`). 직접 대입으로 작성 |
| `set { }`과 `set { o, sub -> }` | 서로 다른 오버로드. Kotlin은 파라미터 개수로 고름. 두 번째 인자 `sub`는 서브쿼리의 시작점 |

자세한 내용: [7. 수정, 삽입, 삭제](07-dml.md).

예제 코드: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)(`update-fk`).

<a id="913-procedures-and-db-functions"></a>
## 9.13 프로시저와 DB 함수

예제는 [8. 프로시저와 DB 함수](08-procedures-functions.md)에 있습니다. 이 절은 Kotlin에서 알아 둘 규칙만 모았습니다.

<a id="procedures"></a>
### 프로시저

| 규칙 | 이유 |
|---|---|
| nullable 반환 타입(`Double?`)을 사용 | non-null 타입에 NULL OUT 값이 들어가면 실패함. lambdaQL은 이름이 있는 오류를 던짐 |
| 스텁 함수에 기본 인자를 쓰지 않음 | Kotlin은 호출을 `...$default`로 바꾸고 Kotlin 기본값을 채워 넣음. lambdaQL은 오류를 보고함 |
| 이름 있는 인자는 단순한 값(변수, 상수)만 | 식을 쓰면 Kotlin이 숨은 지역 변수를 만드는데, lambdaQL은 이를 받아들이지 않음 |
| 반환 값이 없는 프로시저에는 (`call`이 아니라) `exec`을 사용 | 둘 다 `call`이면 Java에서 모호하고, `run`은 Kotlin의 `run { }`과 충돌함 |
| `-java-parameters`로 컴파일 | 그러면 lambdaQL이 같은 타입의 뒤바뀐 인자를 검사할 수 있음([8.4](08-procedures-functions.md#84-warning-argument-order)) |

<a id="db-functions-interface-companion-object--jvmstatic"></a>
### DB 함수: 인터페이스 `companion object` + `@JvmStatic`

Kotlin 인터페이스에는 정적 함수가 없습니다. `companion object`와 `@JvmStatic`을 사용하십시오.
본문은 호출되지 않습니다. 람다 표현식 밖에서 직접 호출하면 예외를 던집니다.
Kotlin이 companion 함수를 호출하면 바이트 opcode는 먼저 `Companion` 객체를 로드합니다. lambdaQL은 그 단계를 건너뛰고 함수의 어노테이션을 읽습니다.

| 선언 | 지원 |
|---|---|
| 인터페이스 `companion object` + `@JvmStatic` | **예(권장)** |
| Kotlin `object` + `@JvmStatic` | 예 |
| 최상위 함수 | 예, 하지만 권장하지 않음: import를 잊었고 그 이름이 stdlib 이름(`maxOf`)이기도 하면 Kotlin은 조용히 stdlib 함수를 호출함 |
| 확장 함수 | 아니요(KC11) |
| 기본 인자가 있는 `@SqlFunction` | 아니요 |
| `inline` `@SqlFunction` | 아니요(호출이 사라짐) |
| `vararg` `@SqlFunction` | 아니요 |
| 함수 타입 속성 / 함수형 인터페이스 상수 | 아니요 |

컬럼에 적용한 함수(`OrderFns.discount(it.price)!! > 10`)는 일반 인덱스를 사용할 수 없습니다.
lambdaQL은 이에 대해 WARN을 기록합니다([8.9](08-procedures-functions.md#89-warning-functions-on-columns-and-indexes-sargable) 참고).

<a id="914-kotlin-limits-kc1-to-kc21"></a>
## 9.14 Kotlin 제한(KC1 ~ KC21)

lambdaQL은 람다 표현식을 쿼리로 분석하지 못하면 코드
`[LQL-xxxx]`, 소스 줄, 힌트가 담긴 예외를 던집니다([11. 오류와 언어](../11-errors-and-languages.md) 참고).
KC1 ~ KC5는 람다 표현식을 로드할 때 발견됩니다. 이 경우 lambdaQL은 즉시 멈춥니다.

| # | 이렇게 쓰지 마십시오 | 이유 | 대신 이렇게 쓰십시오 |
|---|---|---|---|
| KC1 | `-Xsam-conversions=class`로 컴파일 | `SerializedLambda`가 만들어지지 않음. 람다 표현식이 클래스가 됨 | 기본값 `-Xsam-conversions=indy`를 유지 |
| KC2 | `inline fun` 본문(일반, `reified`, `crossinline`) 안에서 만든 쿼리 람다 표현식 | 람다 표현식이 람다 표현식이 아니라 클래스가 됨 | 헬퍼를 inline이 아닌 함수로 만들거나, 호출자에서 람다 표현식 리터럴을 전달 |
| KC3 | 직접 만든 Kotlin `fun interface`에 대한 호출 가능 참조(`kkey(Order::price)`) | 어댑터 클래스가 됨 | 람다 표현식 리터럴 `{ it.price }` |
| KC4 | Kotlin 함수 타입 값: `val f: (Order) -> Boolean = {...}; where(f)` | lambdaQL은 `Function1` 내부를 읽을 수 없음 | 리터럴로 `where { ... }` |
| KC5 | `object : ...`(객체 식), `Serializable`이 아닌 함수형 인터페이스 | 직렬화 가능한 람다 표현식이 아님 | 람다 표현식 리터럴 |
| KC6 | `takeIf`, 일반적인 boolean `if (a) x else y` | 지원하지 않음(`LQL-2021`). 값을 고르는 `if`/`when`은 지원(캡처 값만 쓰면 바인드 파라미터, 엔티티 값을 쓰면 `case when`) | `&&` / `\|\|` |
| KC7 | *(D25로 삭제됨)* | – | – |
| KC8 | 람다 표현식 안의 `in listOf(1, 2, 3)`이나 `setOf(...)` | 람다 표현식 안에서 만든 컬렉션 | 밖에서 `val ids = listOf(1, 2, 3)`, 그다음 `it.id in ids` |
| KC9 | `isBlank()`, `isNullOrBlank()` | 유니코드 공백에는 이식 가능한 SQL 형태가 없음 | 직접 비교 |
| KC10 | *(D21로 삭제됨)* | – | – |
| KC11 | 직접 만든 inline이 아닌 확장 함수나 확장 속성, 엔티티 `operator` 함수 | lambdaQL은 호출 안을 볼 수 없음 | `inline fun`으로 만들거나 조건을 직접 작성 |
| KC12 | 캡처 변수에 대한 `n++` | 람다 표현식이 변수에 씀 | 람다 표현식 밖에서 값을 계산 |
| KC13 | 엔티티 `==` 엔티티(`it.customer == c`) | 지원하지 않음(`LQL-3011`) | `it.customer?.id == c.id` |
| KC14 | `isNaN()` | 지원하지 않음 | NaN을 저장하지 말 것. [10.5](10-rules-and-limits.md#105-kotlin-and-sql-differences) 참고 |
| KC15 | `@JvmField` 엔티티 속성 | getter가 아닌 필드 접근 | 일반 `var` / `val` |
| KC16 | 변형된 getter 이름: `internal` 속성, `value class`, `@get:JvmName` | getter를 JPA 속성에 매핑할 수 없음 | public 일반 속성 |
| KC17 | 문자열 템플릿 안의 엔티티 값 | 문자열 결합은 지원하지 않음(`LQL-2007`) | 컬럼을 따로 비교 |
| KC18 | Java 합성 속성 참조 `JOrder::price` | Kotlin 컴파일 오류 | 람다 표현식 `{ it.price }` |
| KC19 | `Order::price.getter` 및 다른 `kotlin-reflect` API | `kotlin-reflect`를 사용하지 않음 | 람다 표현식 `{ it.price }` |
| KC20 | 컬럼에 대한 다른 stdlib 함수(`coerceAtLeast`, `trim`, `toLocalDate`, ...) | 허용 목록에 없음(`LQL-3008`) | `@SqlFunction`을 사용한 DB 함수 |
| KC21 | 값이나 정렬 키로 쓰는 boolean 엘비스(`{ it.vip ?: false }`) | boolean에 대한 `coalesce`는 Oracle에서 실패함(ORA-00932) | NULL 위치를 지정하여 `{ it.vip }`로 정렬. 조건에서는 `it.vip ?: false`도 괜찮음 |

<a id="915-where-lambdaql-differs-from-running-kotlin-code"></a>
## 9.15 lambdaQL이 Kotlin 코드 실행과 다른 점

이 차이는 설계의 일부입니다. 버그가 아닙니다.

| Kotlin 코드 | lambdaQL(SQL) |
|---|---|
| `!!`는 NPE를 던짐 | 값의 `!!`는 무시됨. NULL은 SQL 규칙을 따름. `where`에서 연관 단계를 `!!`로 지난 비교는 연관이 있는 행에서만 참 |
| `it.description != "x"`는 `description == null`일 때 `true` | `e0.description <> 'x'`는 NULL에 대해 UNKNOWN이므로 행이 반환되지 않음. 포함하려면 `it.description == null \|\| ...`로 작성 |
| 고객이 없으면 `it.customer?.name == null`은 `true` | 같음: `?.`로 지난 단계는 `left join`이 되어 고객이 없는 주문도 반환됨. `it.customer!!.name == null`은 고객이 있는 주문에서만 참이라 반환되지 않음 |
| 대상이 NULL인 범위는 false이고, `!in`은 `true` | 행이 반환되지 않음 |
| `===`는 객체 동일성 | 값 비교 |
| `BigDecimal` `==`는 스케일을 비교함(`1.0 != 1.00`) | 값 비교 |
| non-null 타입 `String`은 null이 아님 | 컬럼은 여전히 NULL일 수 있음. lambdaQL은 JPA 메타데이터를 사용 |
| 정수 `/`는 소수 부분을 버림 | DB마다 다를 수 있음(MariaDB는 `5/2 = 2.5`) |

nullable 숫자 속성과 상수의 비교(`it.cnt != 1`)는 Kotlin이 넣은 null 검사를 그대로 쓰므로 Kotlin과 같은 행을 반환합니다([9.3](#93-only-your-null-checks-count)).

더 많은 규칙: [10. 규칙과 제한](10-rules-and-limits.md).

---

[Kotlin 트랙](README.md) | 이전: [8. 프로시저와 DB 함수](08-procedures-functions.md) | 다음: [10. 규칙과 제한](10-rules-and-limits.md) | [이 페이지의 Java 버전: 9. Java 노트](../java/09-java-notes.md)
