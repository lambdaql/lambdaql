<a id="3-conditions-where-java"></a>
# 3. 조건(`where`) (Java)

[Java 트랙](README.md) | [매뉴얼 홈](../README.md) | 이전: [2. 시작하기](02-getting-started.md) | 다음: [4. 정렬과 페이징](04-order-paging.md) | [이 페이지의 Kotlin 버전](../kotlin/03-where.md)

공통 페이지: [1. 개요](../01-overview.md) · [11. 오류와 언어](../11-errors-and-languages.md)

---

<a id="31-the-idea-in-one-sentence"></a>
## 3.1 한 문장으로 말하면

**lambdaQL 람다는 코드가 아니라 쿼리입니다.** lambdaQL은 람다의 바이트코드를 읽습니다. 절대 실행하지 않습니다.
그래서 Java의 실행 규칙(NullPointerException, 객체 동일성, 박싱 캐시)은 적용되지 않습니다.
lambdaQL은 자체 규칙을 사용합니다. 이 장에서 그 규칙을 설명합니다.

어떤 형태에 대한 규칙이 없으면 lambdaQL은 추측하지 않습니다. 명확한 메시지와 함께 예외를 던집니다([3.13](#313-what-is-rejected) 참고).

<a id="32-example-entities"></a>
## 3.2 예제 엔티티

모든 예제는 다음 엔티티를 사용합니다(getter와 setter는 생략).

```java
@Entity class Order {
    @Id Long id;
    Double price;
    Integer status;
    boolean active;            // getter: isActive()
    String description;
    @ManyToOne Customer customer;
    LocalDateTime updateAt;
    @OneToMany(mappedBy = "order") List<Item> items;
}
@Entity class Customer { @Id Long id; String name; Integer grade; }
@Entity class Item     { @Id Long id; @ManyToOne Order order; Double price; int qty; }
```

<a id="33-basic-form"></a>
## 3.3 기본 형태

```java
QueryBuilder qb = new QueryBuilder(entityManagerFactory);

List<Order> list = qb.from(Order.class)
    .where(o -> o.getStatus() == 1 && o.getPrice() > 100.0)
    .toList();
// HQL: select e0 from Order e0 where e0.status = 1 and e0.price > 100.0
```

**SQL 블록 읽는 법.** `…`는 줄여 쓴 select 목록(엔티티의 모든 컬럼)이고, `?`는 바인드 파라미터입니다. 각 줄은 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)에서 나옵니다. 이 테스트는 각 쿼리를 Hibernate 6.6.5로 H2 2.3, MariaDB 10.11, Oracle 23.26에서 실행하며, Hibernate의 SQL이 바뀌면 실패합니다.

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `to-hql`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>100.0
```

**(테스트됨)** 표시가 있는 예제는 [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java)에서 그대로 가져온 것입니다.
이 테스트는 HQL을 확인하고, 쿼리를 실행하고, 같은 필터를 Java로 적용한 결과와 행을 비교합니다. H2, MariaDB, Oracle에서 통과합니다.
테스트 엔티티는 3.2와 조금 다릅니다. `price`는 `double`, `status`는 `short`, `Customer.id`는 `int`이고, `Order`에 enum `kind`(`Order.Kind`: `NORMAL`, `RUSH`)가 있습니다.

`toHql()`은 쿼리를 실행하지 않고 HQL과 파라미터를 돌려줍니다.
**(테스트됨)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java), 영역 `to-hql`:

```java
RenderedQuery q = qb.from(Order.class)
        .where(o -> o.getStatus() == 1 && o.getPrice() > 100)
        .toHql();
// constants written in the lambda are literals
assertEquals("select e0 from Order e0 where e0.status = 1 and e0.price > 100.0", q.hql());
assertEquals(Map.of(), q.parameters());

double minPrice = 100;
RenderedQuery q2 = qb.from(Order.class)
        .where(o -> o.getStatus() == 1 && o.getPrice() > minPrice)
        .toHql();
// captured values are bind parameters
assertEquals("select e0 from Order e0 where e0.status = 1 and e0.price > :p1", q2.hql());
assertEquals(Map.of("p1", 100.0), q2.parameters());
```

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `to-hql`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>100.0
```

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `to-hql#2`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>?
```

`where()`는 여러 번 호출할 수 있습니다. 조건들은 `AND`로 결합됩니다.
**(테스트됨)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java), 영역 `where-many`:

```java
RenderedQuery q = qb.from(Order.class)
        .where(o -> o.getStatus() == 1 || o.getStatus() == 3)
        .where(o -> o.isActive())
        .toHql();
assertEquals("select e0 from Order e0 where (e0.status = 1 or e0.status = 3) and e0.active = true", q.hql());
```

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `where-many`:

```sql
-- H2
select …
from orders o1_0
where (o1_0.status=1 or o1_0.status=3) and o1_0.active=true

-- MariaDB, Oracle
select …
from orders o1_0
where (o1_0.status=1 or o1_0.status=3) and o1_0.active=1
```

메서드 참조도 동작합니다. `where(Order::isActive)`는 `where(o -> o.isActive())`와 같습니다.

**HQL 안의 값.** 값이 어디에서 왔는지에 따라 규칙이 정해집니다(결정 D37).

| 값 | HQL에서 | 예 |
|---|---|---|
| 람다에 직접 쓴 상수: 숫자(소수 포함), 문자열, `true`/`false`, `null`, enum 상수, 컴파일 시점 `static final` 상수 | **리터럴** | `o.getPrice() > 100` → `e0.price > 100.0` |
| 캡처 값: 지역 변수, 필드, 메서드 파라미터 | **바인드 파라미터** | `o.getPrice() > minPrice` → `e0.price > :p1` |
| 계산한 값: `new BigDecimal("1.5")`, `form.getText().trim()`, `LocalDateTime.of(...)` | **바인드 파라미터** | `:p1` |

- 상수는 호출할 때마다 같습니다. 그래서 SQL 문장이 바뀌지 않고, 데이터베이스는 쿼리 계획을 세울 때 값을 볼 수 있습니다.
- 문자열 상수 안의 작은따옴표는 escape됩니다: `"O'Brien"`은 `'O''Brien'`이 됩니다. 이 문자열은 사용자 입력이 아니라 클래스에 컴파일된 상수이므로 SQL 주입 위험이 없습니다.
- 캡처 값은 사용자에게서 올 수 있으므로 **항상** 바인드 파라미터입니다. 호출마다 값만 바뀝니다.

enum 상수는 전체 클래스 이름으로 씁니다.
**(테스트됨)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java), 영역 `enum`:

```java
RenderedQuery q = qb.from(Order.class).where(o -> o.getKind() == Order.Kind.RUSH).toHql();
assertEquals("select e0 from Order e0 where e0.kind = org.lambdaql.query.Order$Kind.RUSH", q.hql());
```

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `enum`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.kind='RUSH'
```

<a id="34-comparisons"></a>
## 3.4 비교

| Java | HQL | 참고 |
|---|---|---|
| `o.getStatus() == 1` | `e0.status = 1` | |
| `o.getStatus() != 1` | `e0.status <> 1` | |
| `o.getPrice() > p` | `e0.price > :p1` | `<`, `<=`, `>=`도 마찬가지 |
| `o.getUpdateAt().isAfter(since)` | `e0.updateAt > :p1` | `isBefore`는 `<` |
| `o.getDescription().compareTo("m") < 0` | `e0.description < 'm'` | `compareTo(..) op 0`은 `op`가 됨 |

숫자와의 비교(`== 1`이나 `> 100.0`처럼 한쪽이 기본형)는 **SQL 의미**를 따릅니다.
컬럼이 NULL이면 결과는 UNKNOWN이고 행은 반환되지 않습니다. (Java라면 NullPointerException이 발생합니다.)

`+`, `-`, `*`를 사용한 단순 산술은 설계에 포함되어 있습니다.
정수 `/`는 피하십시오. 데이터베이스마다 결과가 다릅니다(MariaDB는 `5/2 = 2.5`). `/`의 최종 규칙은 아직 정해지지 않았습니다.

**컬럼에 대한 산술과 인덱스**(결정 D50). `o.getQty() * 2 > n`처럼 컬럼에 산술을 걸면 인덱스를 쓸 수 없습니다. lambdaQL은 `LQL-5012`를 `WARN` 수준으로 기록하고, `SargableMode.STRICT`에서는 분석이 실패합니다(`LQL-3066`).
값 쪽으로 옮길 때는 `o.getQty() > n / 2.0`처럼 소수로 나누십시오. 정수 나눗셈 `n / 2`는 다른 행을 냅니다. 음수를 곱했다면 비교 방향을 바꾸십시오. 자세한 내용은 [10.2](10-rules-and-limits.md#db-functions-on-a-column)를 참고하십시오.

**정수 컬럼과 소수 값**(결정 D48 B8). 정수 컬럼을 소수 값과 비교하면 lambdaQL은 값을 적은 그대로 비교합니다. 반올림이나 값 제외는 하지 않습니다.

- 람다에 적은 상수는 리터럴로 남습니다. `o.getCnt() >= 2.5`는 `e0.cnt >= 2.5`가 됩니다.
- 캡처 값은 `BigDecimal`(Hibernate 타입 `BIG_DECIMAL`)로 바인딩합니다. `e0.cnt >= :p1`이고 `p1`은 `2.5`입니다.
- 그런 요소가 있는 IN 목록은 모든 요소를 `BigDecimal`로 바인딩합니다(`[2.5, 3.0]`).

이전에는 값을 컬럼 타입으로 잘라 `2.5`가 `2`가 되었고, 잘못된 행을 반환했습니다. Hibernate는 정수 컬럼 타입으로 바인딩한 소수를 거부하고(`CoercionException`), IN 목록의 요소는 조용히 자릅니다(세 DB에서 실측).
정수 비교로 바꾸는 방식(`>= 3`)은 채택하지 않았습니다. 실행되는 SQL이 람다와 달라지고, 엔티티 매핑(`Integer`)과 DB 컬럼 타입(예: 소수가 있는 `DECIMAL`)이 다르면 잘못된 행을 냅니다.

결과는 H2, MariaDB 10.11, Oracle 23.26에서 올바릅니다(실측). MariaDB의 `EXPLAIN`은 int, `BigDecimal`, double 파라미터 모두 인덱스 범위 스캔(`type=range`)을 보여 줍니다(실측). Oracle의 실행 계획은 미측정입니다.

**(테스트됨)** [`SqlMeaningTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlMeaningTest.java), 영역 `whole-number`:

```java
// cnt is a whole number; 2.5 is compared as written (it is never cut to 2)
double half = 2.5;
RenderedQuery atLeast = qb.from(Order.class).where(o -> o.getCnt() >= half).toHql();
assertEquals("select e0 from Order e0 where e0.cnt >= :p1", atLeast.hql());
assertEquals(Map.of("p1", new BigDecimal("2.5")), atLeast.parameters());    // bound as a decimal
// a constant written in the lambda stays a literal
assertEquals("select e0 from Order e0 where e0.cnt >= 2.5", qb.from(Order.class).where(o -> o.getCnt() >= 2.5).toHql().hql());
// an IN list with such a value is bound as decimals
List<Double> values = List.of(2.5, 3.0);
RenderedQuery in = qb.from(Order.class).where(o -> values.contains(o.getCnt().doubleValue())).toHql();
assertEquals("select e0 from Order e0 where e0.cnt in (:p1)", in.hql());
assertEquals(Map.of("p1", List.of(new BigDecimal("2.5"), new BigDecimal("3.0"))), in.parameters());
```

**부동소수점:** `float`/`double` 컬럼에 대해 lambdaQL은 Java와 비트 단위까지 같은 결과를 약속하지 않습니다.
캡처한 `NaN`이나 `Infinity` 값은 쿼리가 실행될 때 거부됩니다.

## 3.5 AND, OR, NOT

| Java | HQL |
|---|---|
| `a && b` | `a and b` |
| `a \|\| b` | `a or b` |
| `!a` | `not (a)` |
| 괄호 | 필요한 만큼 유지 |

```java
.where(o -> (o.getStatus() == 1 || o.isActive()) && o.getPrice() > minPrice)
// HQL: (e0.status = 1 or e0.active = true) and e0.price > :p1

.where(o -> !(o.getStatus() == 1 && o.getPrice() > minPrice))
// HQL: not (e0.status = 1 and e0.price > :p1)
```

규칙:

- 결과는 **SQL 3값 논리**(TRUE / FALSE / UNKNOWN)를 따릅니다. lambdaQL은 이 방식이 H2, MariaDB, Oracle에서 같은 행을 반환하는지 확인했습니다.
- javac는 가끔 `!`의 위치를 옮깁니다(드 모르간, `!(x > y)`를 `x <= y`로). 그래도 결과는 바뀌지 않습니다.
- lambdaQL은 **조건을 단순화하지 않습니다**. `o.getPrice() > p || o.getPrice() <= p`라고 쓰면 쓴 그대로 보냅니다. `price`가 NULL인 행은 반환되지 않습니다. 이는 정상적인 SQL 동작입니다.
- `&&`와 `||`를 사용하십시오. 단락 평가를 하지 않는 연산자 `&`, `|`, `^`는 거부됩니다.

**boolean 삼항 연산자.** 짧은 형태 세 가지만 허용합니다. 각각 `AND`, `OR`, `NOT`과 같습니다.

| Java | 같은 의미 |
|---|---|
| `c ? x : false` | `c && x` |
| `c ? true : x` | `c \|\| x` |
| `c ? false : true` | `!c` |

그 밖의 삼항 연산자(`c ? x : y`)와 값을 고르는 삼항 연산자(`o.getStatus() == (flag ? 1 : 2)`)는 모두 거부됩니다.
선택이 캡처 값에만 달려 있다면 람다 전에 값을 계산하십시오.

```java
int wanted = flag ? 1 : 2;
qb.from(Order.class).where(o -> o.getStatus() == wanted).toList();
```

<a id="36-null-rules"></a>
## 3.6 NULL 규칙

가장 중요한 부분입니다. 단순한 원칙 두 가지가 있습니다.

1. **엔티티 값은 SQL 의미로 비교합니다**(결정 D48). 객체 사이의 `==`, `equals`, `Objects.equals`, `!=`는 `=`와 `<>`가 됩니다.
   값이 NULL인 행은 어느 쪽에도 일치하지 않습니다.
   결과가 Java와 다를 수 있으면 lambdaQL은 `LQL-5009`를 `DEBUG` 수준으로 기록합니다.
2. **캡처 값은 Java에서 먼저 계산하고, 비교는 역시 SQL 의미를 따릅니다.** 캡처 값 `null`은 NULL로 바인딩되므로 `= :p1`과 `<> :p1`은 어떤 행에도 일치하지 않습니다.
   이 경우 lambdaQL은 실행 시점에 `LQL-4045`를 `WARN` 수준으로 기록합니다. NULL을 찾으려면 `x == null`이라고 쓰십시오(`is null`이 됨).
   캡처 값만으로 된 식(`p.trim()`, 다른 기본형으로 바꾸는 `min.intValue()` 등)은 Java에서 계산한 값이므로, 수신 객체가 `null`이면 Java처럼 예외(`LQL-4012`)를 던집니다.
   자기 기본형으로의 언박싱(`min.doubleValue()`)은 javac의 자동 언박싱과 같은 바이트코드이므로 실행하지 않고 박싱 값을 그대로 바인딩합니다([3.6.4](#364-captured-null-values) 참고).

lambdaQL은 **직접 작성한 경우에만 null을 처리합니다**(`== null`, `!= null`).
숨겨진 null 검사를 절대 대신 추가하지 않습니다.

<a id="361-rule-table"></a>
### 3.6.1 규칙 표

| # | Java 형태 | HQL | NULL 동작 |
|---|---|---|---|
| R1 | 한쪽이 기본형: `o.getStatus() == 1`, `o.getPrice() > p` | `e0.status = 1`, `e0.price > :p1` | SQL 의미. 컬럼이 NULL이면 행이 반환되지 않음 |
| R2 | 양쪽이 객체: `o.getStatus() == status`(`Integer == Integer`), `String == String`, enum | `e0.status = :p1` | **값 비교.** 컬럼이 NULL이면 행이 반환되지 않음. 캡처 값 `null`: 일치하는 행 없음(`LQL-4045`) |
| R2 | `o.getStatus() != status`(객체) | `e0.status <> :p1` | SQL 의미. 컬럼이 NULL이면 행이 반환**되지 않음**(Java라면 반환). 캡처 값 `null`: 일치하는 행 없음(`LQL-4045`) |
| R2 | 엔티티 값 두 개: `o.getDescription() == o.getCustName()` | `e0.description = e0.custName` | SQL 의미. 어느 한쪽이 NULL이면 행이 반환되지 않음(NULL과 NULL도 마찬가지) |
| R3 | `x == null`, `x != null` | `x is null`, `x is not null` | |
| R4 | `o.getDescription().equals(p)` | `e0.description = :p1` | SQL 의미. 컬럼이 NULL이면 행이 반환되지 않음. `p == null`이면 일치하는 행 없음(`LQL-4045`) |
| R5 | `Objects.equals(a, b)`, `"x".equals(a)`, `Boolean.TRUE.equals(a)` | R2와 같음: `a = b`, `a = :p1` | R2와 같음 |
| R9 | 캡처 값 | 바인드 파라미터 | [3.6.4](#364-captured-null-values) 참고 |

어떤 Java 타입이 R1이 되고 어떤 타입이 R2가 되는지는 [9. Java 노트](09-java-notes.md#93-equality-of-boxed-values)에서 설명합니다.

<a id="362--on-objects-compares-values"></a>
### 3.6.2 객체에 대한 `==`는 값을 비교

Java에서 두 `Integer`, `Long`, `String` 객체에 대한 `==`는 **객체 동일성**을 검사합니다.
lambdaQL에서는 **값 비교**입니다. 127보다 큰 `Long` 값이나 `new String(...)`도 상관없습니다.
람다는 쿼리이므로 Java의 메모리 규칙은 적용되지 않습니다.

```java
Integer status = request.getStatus();    // null일 수 있음
qb.from(Order.class).where(o -> o.getStatus() == status).toList();
//   status = 3    -> HQL: e0.status = :p1    status가 3인 행
//   status = null -> HQL: e0.status = :p1    일치하는 행 없음: = NULL은 참이 되지 않음(WARN 수준 LQL-4045)
```

status가 NULL인 행을 찾으려면 null 검사를 작성하십시오. `o.getStatus() == null`은 `e0.status is null`이 됩니다.

컬럼을 함수로 감싸지 않으므로 데이터베이스는 인덱스를 사용할 수 있습니다.

lambdaQL은 쿼리가 실행될 때 캡처 값을 바인딩합니다. `null`은 NULL로 바인딩되고, 일치할 수 있는 행이 없으므로 lambdaQL은 `LQL-4045`를 `WARN` 수준으로 기록합니다.
**(테스트됨)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java), 영역 `null-safe`:

```java
// the SQL meaning (D48): = NULL matches no row; lambdaQL logs LQL-4045 at WARN when it runs with a null
String d = null;
RenderedQuery q = qb.from(Order.class).where(o -> Objects.equals(o.getDescription(), d)).toHql();
assertEquals("select e0 from Order e0 where e0.description = :p1", q.hql());
// to find NULL, write the null check
RenderedQuery q3 = qb.from(Order.class).where(o -> o.getDescription() == null).toHql();
assertEquals("select e0 from Order e0 where e0.description is null", q3.hql());

String d2 = "a";
RenderedQuery q2 = qb.from(Order.class).where(o -> Objects.equals(o.getDescription(), d2)).toHql();
assertEquals("select e0 from Order e0 where e0.description = :p1", q2.hql());
```

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `null-safe`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.description=?
```

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `null-safe#2`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.description=?
```

> [!TIP]
> **IDE 경고.** IntelliJ는 이 형태에 "Number objects compared using `==`"를 표시합니다.
> lambdaQL 람다 안에서는 의도한 동작입니다. `@SuppressWarnings("NumberEquality")`를 사용할 수 있습니다.

**NULL이 될 수 없는 컬럼.** id, 기본형 컬럼, `nullable = false` 컬럼은 NULL이 될 수 없습니다.
이런 컬럼의 `is null`은 항상 거짓이므로 lambdaQL은 이 검사를 상수로 접어 제거합니다.

- `o.getId() == null`은 `1=0`(행 없음)이 됩니다.
- `o.getId() != null && o.getId() == id`는 `e0.id = :p1`이 됩니다. null 검사가 사라지므로 데이터베이스가 인덱스를 사용할 수 있습니다.

`Long?` id에 대한 Kotlin `it.id == id`도 같은 바이트코드로 컴파일되므로, Kotlin 형태도 단순한 `e0.id = :p1`이 됩니다.

이 규칙은 경로에 **연관 단계가 없을 때만** 적용합니다. 고객이 없는 주문이 있으므로 `o.getCustomer().getId()`는 NULL이 될 수 있고, 이 경로의 null 검사는 유지합니다.

**(테스트됨)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java), 영역 `never-null`:

```java
// the id can never be NULL, so the null check is dropped (this is what Kotlin's it.id == id compiles to)
assertEquals("select e0 from Order e0 where e0.id = :p1",
        qb.from(Order.class).where(o -> o.getId() != null && o.getId() == id).toHql().hql());
assertEquals("select e0 from Order e0 where 1=0", qb.from(Order.class).where(o -> o.getId() == null).toHql().hql());
// through an association the row may have no customer, so the check stays
assertEquals("select e0 from Order e0 where e0.customer.id is null",
        qb.from(Order.class).where(o -> Objects.equals(o.getCustomer().getId(), null)).toHql().hql());
```

<a id="363-write-your-null-checks-yourself"></a>
### 3.6.3 null 검사는 직접 작성

```java
// status가 NULL이 아니고 1도 아닌 행만:
.where(o -> o.getStatus() != 1)
// HQL: e0.status <> 1

// status가 NULL인 행도 포함:
.where(o -> o.getStatus() == null || o.getStatus() != 1)
// HQL: e0.status is null or e0.status <> 1
```

`equals()`와 `Objects.equals()`는 다릅니다.

```java
.where(o -> o.getDescription().equals(text))     // HQL: e0.description = :p1
.where(o -> Objects.equals(o.getDescription(), text))
                                                 // HQL: e0.description = :p1 (text가 null이면 일치하는 행 없음)
.where(o -> !Objects.equals(o.getStatus(), 1))   // HQL: e0.status <> 1
                                                 //      (NULL 행이 반환되지 않음: SQL 의미)
```

**엔티티 값에 대한 SQL 의미**(결정 D48). 엔티티 값 두 개의 비교와, 엔티티 값을 값과 `!=`로 비교하는 경우는 SQL을 따릅니다. 값이 NULL인 행은 `=`에도 `<>`에도 일치하지 않습니다.

Java에서는 `!text.equals(null)`이 true이고 `Objects.equals(null, null)`도 true이므로 Java라면 이런 행을 반환합니다.
이런 차이가 생길 수 있으면 lambdaQL은 `LQL-5009`를 `DEBUG` 수준으로 조건마다 한 번 기록합니다. NULL 행을 유지하려면 람다에 null 검사를 직접 작성하십시오.

**(테스트됨)** [`SqlMeaningTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlMeaningTest.java), 영역 `sql-meaning`(H2, MariaDB, Oracle. 행은 description/custName 쌍 `a/a`, `a/b`, `null/a`, `null/null`, `b/null`이고 `text`는 `"a"`):

```java
// two entity values: = and <> as in SQL; a row with a NULL on either side matches neither
SelectQuery<Order> same = qb.from(Order.class).where(o -> Objects.equals(o.getDescription(), o.getCustName()));
assertEquals("select e0 from Order e0 where e0.description = e0.custName", same.toHql().hql());
assertEquals(List.of("a/a"), pairs(same.toList()));                    // null/null is not "equal" in SQL
SelectQuery<Order> different = qb.from(Order.class).where(o -> !Objects.equals(o.getDescription(), o.getCustName()));
assertEquals(List.of("a/b"), pairs(different.toList()));               // rows with a NULL are left out
// an entity value and a value: a NULL column does not match <>
SelectQuery<Order> notA = qb.from(Order.class).where(o -> !text.equals(o.getDescription()));
assertEquals("select e0 from Order e0 where e0.description <> :p1", notA.toHql().hql());
assertEquals(List.of("b/null"), pairs(notA.toList()));
// to keep the NULL rows, say so in the lambda
SelectQuery<Order> notAOrNull = qb.from(Order.class).where(o -> o.getDescription() == null || !text.equals(o.getDescription()));
assertEquals(List.of("b/null", "null/a", "null/null"), pairs(notAOrNull.toList()));
// a NULL Boolean is not false
assertEquals(List.of("f"), qb.from(Coded.class).where(c -> !c.getVip()).toList().stream().map(Coded::getCode).toList());
```

<a id="364-captured-null-values"></a>
### 3.6.4 null 캡처 값

| 형태 | 캡처 값이 `null`일 때 |
|---|---|
| 캡처 값과의 `==`, `!=`, `Objects.equals(...)`, `o.getDescription().equals(p)` | 실행됨: NULL을 바인딩한 `= :p1` / `<> :p1`이므로 일치하는 행이 **없음**. `WARN` 수준 `LQL-4045` |
| `p.equals(o.getDescription())`(`p`가 수신 객체) | 위와 같음: `e0.description = :p1`(NULL), 일치하는 행 **없음**. `WARN` 수준 `LQL-4045`. 예외는 없음 |
| Java가 값을 언박싱하는 비교: `o.getPrice() > p`(`Double p`), `i.getQty() == q`(`int qty`, `Integer q`) | 실행됨: `e0.price > :p1`에 NULL 바인딩, 일치하는 행 **없음**. `DEBUG` 수준 `LQL-4045`. 자기 기본형으로의 언박싱(`p.doubleValue()`)은 실행하지 않음 |
| 다른 기본형으로 바꾸는 언박싱(`p.intValue()`, `Double p`)과 메서드 호출(`p.trim()`) | 쿼리가 실행될 때 **예외**(`LQL-4012`). Java에서 값을 계산하기 때문 |

**`p.equals(o.getDescription())` 형태는 권장하지 않습니다.** SQL로는 `o.getDescription().equals(p)`와 같은 비교(`e0.description = :p1`)이므로 `p`가 `null`이어도 예외가 나지 않습니다.
Java 코드로 읽으면 `null.equals(...)`가 NullPointerException처럼 보이므로, 엔티티 값을 앞에 두는 `o.getDescription().equals(p)`로 쓰십시오.

이유: 람다 안의 비교는 SQL 의미를 따릅니다(결정 D48). SQL에서 NULL과의 `=`, `<>`는 참이 되지 않습니다.
캡처 값이 null인지는 쿼리가 실행될 때만 알 수 있으므로, lambdaQL은 실행할 때 경고를 기록합니다.

캡처 값만으로 된 식은 쿼리 전에 Java에서 계산하는 값입니다. `p.trim()` 같은 메서드 호출과 다른 기본형으로 바꾸는 언박싱(`p.intValue()`, `Double p`)이 이에 해당하며, `null`이면 Java처럼 예외(`LQL-4012`)를 던집니다.
다만 자기 기본형으로의 언박싱(`Double.doubleValue()`, `Integer.intValue()`, `Long.longValue()` 등, 뒤의 확대 변환 포함)은 계산하지 않습니다.
javac의 자동 언박싱과 손으로 쓴 호출은 같은 바이트코드이므로, lambdaQL은 이를 자동 언박싱으로 보고 박싱 값을 바인딩합니다. `null`이면 일치하는 행이 없고 `LQL-4045`를 `DEBUG` 수준으로 기록합니다.
NULL 행을 찾으려면 `x == null`이라고 쓰십시오.

**null 가드로 필터 생략.** 같은 캡처 값을 null 검사하면, 값이 null일 때 그 조건이 필터 없이 사라집니다.
**(테스트됨)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java), 영역 `captured-null-guard`:

```java
Double min = null;                                       // for example an empty search field
RenderedQuery q = qb.from(Order.class).where(o -> min == null || o.getPrice() > min).toHql();
// the guard is decided in Java when the query runs: no filter at all
assertEquals("select e0 from Order e0", q.hql());

Double min2 = 100.0;
RenderedQuery q2 = qb.from(Order.class).where(o -> min2 == null || o.getPrice() > min2).toHql();
assertEquals("select e0 from Order e0 where e0.price > :p1", q2.hql());
assertEquals(Map.of("p1", 100.0), q2.parameters());
```

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `captured-null-guard`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
```

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `captured-null-guard#2`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.price>?
```

동작 방식:

- 캡처 값과 상수만 사용하는 부분은 쿼리가 실행될 때 Java에서 결정합니다. Java처럼 왼쪽에서 오른쪽으로, 단락 평가(short-circuit)로 처리합니다.
- `min == null`이면 가드가 참이므로 조건 전체가 참입니다. 필터가 없습니다. 비교 `o.getPrice() > min`은 HQL에 쓰지 않습니다(Java도 이 비교를 실행하지 않습니다).
- `min = 100.0`이면 가드가 거짓이므로 `e0.price > :p1`만 남습니다.
- 람다는 여전히 한 번만 분석합니다. 값에 따라 달라지는 것은 HQL 텍스트뿐입니다.
- 이 경우 가드의 위치는 결과에 영향을 주지 않습니다. `min == null`일 때 `o.getPrice() > min || min == null`도 필터가 없습니다. 비교 부분의 언박싱을 Java에서 실행하지 않기 때문입니다(Java라면 NullPointerException).
- 캡처 값에 메서드를 호출하는 비교(`o.getDescription().equals(p.trim())`)는 다릅니다. 가드가 뒤에 있으면 `p.trim()`을 먼저 계산하므로 `p == null`일 때 예외(`LQL-4012`)를 던집니다.
- `&&`에서는 가드가 반대로 동작합니다. `min == null`일 때 `min != null && o.getPrice() > min`은 행을 반환하지 않습니다(`where 1=0`).

<a id="365-converted-attributes-convert"></a>
### 3.6.5 변환 속성(`@Convert`)

JPA `AttributeConverter`가 붙은 속성은 Java 값으로 비교합니다. Hibernate 6.6은 이런 속성과 비교하는 리터럴을 변환하고, 바인드 파라미터도 변환합니다.

따라서 조건을 Java 값으로 작성해도 저장된 행을 찾습니다(H2, MariaDB, Oracle에서 확인함).

**(테스트됨)** [`ConvertTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ConvertTest.java), 영역 `convert`:

```java
// code is stored reversed ("abc" as "cba"); vip is stored as 'Y'/'N' (YesNoConverter)
SelectQuery<Coded> literal = qb.from(Coded.class).where(c -> c.getCode().equals("abc"));
assertEquals("select e0 from Coded e0 where e0.code = 'abc'", literal.toHql().hql());
assertEquals(List.of("abc"), codes(literal.toList()));       // Hibernate converts the literal
```

<a id="37-boolean-columns"></a>
## 3.7 boolean 컬럼

예제 엔티티에서 `active`는 기본형 `boolean`입니다(getter `isActive()`).
`getActive()`가 나오는 행은 nullable `Boolean` 컬럼의 경우입니다.

| Java | HQL | NULL 행 |
|---|---|---|
| `o.isActive()` | `e0.active = true` | –(기본형 `boolean`은 NULL이 될 수 없음) |
| `!o.isActive()` | `e0.active = false` | –(기본형 `boolean`은 NULL이 될 수 없음) |
| `Boolean.TRUE.equals(o.getActive())`(nullable `Boolean`) | `e0.active = :p1`(`:p1`은 `true`) | 반환 안 됨 |
| `!Boolean.TRUE.equals(o.getActive())` | `e0.active <> :p1` | **반환 안 됨**(SQL 의미, D48. Java라면 반환) |
| `!o.getActive()`(nullable `Boolean`) | `e0.active = false` | **반환 안 됨**(SQL 의미: NULL은 false가 아님) |

**(테스트됨)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java), 영역 `boolean`:

```java
assertEquals("select e0 from Order e0 where e0.active = true",
        qb.from(Order.class).where(o -> o.isActive()).toHql().hql());
assertEquals("select e0 from Order e0 where e0.active = false",       // primitive boolean: never NULL
        qb.from(Order.class).where(o -> !o.isActive()).toHql().hql());
```

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `boolean`:

```sql
-- H2
select …
from orders o1_0
where o1_0.active=true

-- MariaDB, Oracle
select …
from orders o1_0
where o1_0.active=1
```

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `boolean#2`:

```sql
-- H2
select …
from orders o1_0
where o1_0.active=false

-- MariaDB, Oracle
select …
from orders o1_0
where o1_0.active=0
```

H2에는 진짜 boolean 타입이 있으므로 SQL에 `true` / `false`가 나옵니다. MariaDB와 Oracle은 boolean을 숫자로 저장하므로 Hibernate가 `1` / `0`을 씁니다.

참고:

- nullable `Boolean` 컬럼에 대한 `!o.getActive()`는 Java에서 언박싱 호출입니다(NULL이면 NullPointerException). lambdaQL은 SQL 의미를 사용합니다. NULL 행은 반환되지 않습니다.
- lambdaQL은 `is not true`나 `coalesce(active, false)`를 사용하지 않습니다. Oracle에서 실패합니다(ORA-00932).
- 더 큰 조건의 부정은 항상 `not (...)`로 씁니다. lambdaQL은 조건을 `(...) = false`나 `(...) is null`로 감싸지 않습니다. Oracle 23이 `WHERE`에서 이를 잘못 평가하기 때문입니다.

**두 boolean 값의 비교**(`==`/`!=`)를 지원합니다.

```java
.where(o -> o.isActive() == (o.getStatus() > s))
// HQL: (e0.active = true and e0.status > :p1) or (e0.active = false and e0.status <= :p1)
```

lambdaQL은 이를 `CASE`가 아닌 `AND`/`OR`로 쓰므로 데이터베이스가 인덱스를 사용할 수 있습니다.
양쪽이 같은 조건을 공유하면(예: `o.isActive() == o.isActive()`) 그 형태는 거부됩니다.

<a id="38-strings"></a>
## 3.8 문자열

- 문자열에 대한 `equals`, `Objects.equals`, `==`는 **데이터베이스 콜레이션**을 사용합니다.
  - MariaDB의 `_ci` 콜레이션은 대소문자와 끝의 공백을 무시합니다.
  - Oracle은 `''`(빈 문자열)을 NULL로 저장합니다. 그래서 Oracle에서 `"".equals(o.getDescription())`는 Java와 다르게 동작합니다.
- 두 문자열에 대한 `==`는 동일성 비교가 아니라 값 비교입니다(규칙 R2). [9.4](09-java-notes.md#94-strings)를 참고하십시오.
- 람다 안의 문자열 상수는 리터럴로 씁니다. 작은따옴표는 escape됩니다(`''`).

**(테스트됨)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java), 영역 `string-literal`:

```java
RenderedQuery q = qb.from(Order.class).where(o -> o.getDescription().equals("O'Brien")).toHql();
assertEquals("select e0 from Order e0 where e0.description = 'O''Brien'", q.hql());
```

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `string-literal`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.description='O''Brien'
```

- `startsWith`, `endsWith`, `contains`(LIKE)와 `trim()`, `toUpperCase()` 같은 컬럼 함수는 **계획 중**이며 첫 버전에는 없습니다.
- 문자열 `+`는 **계획 중**입니다. 분석 코어는 이미 람다 안의 `+`를 거부합니다. 캡처 값만 이어 붙이는 경우도 마찬가지입니다. 문자열은 람다 전에 만드십시오.

<a id="39-in-with-a-captured-collection"></a>
## 3.9 캡처한 컬렉션을 사용한 IN

컬렉션을 람다 **밖에서** 만들고 그 컬렉션의 `contains`를 호출하십시오.

```java
List<Long> ids = List.of(1L, 2L, 3L);
qb.from(Order.class).where(o -> ids.contains(o.getId())).toList();
// HQL: e0.id in (:p1)

qb.from(Order.class).where(o -> !ids.contains(o.getId())).toList();
// HQL: e0.id not in (:p1)
```

컬렉션은 바인드 파라미터 하나가 됩니다.
**(테스트됨)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java), 영역 `in`(테스트 엔티티의 status는 `short`):

```java
List<Short> statuses = List.of((short) 2, (short) 3);
RenderedQuery q = qb.from(Order.class).where(o -> statuses.contains(o.getStatus())).toHql();
assertEquals("select e0 from Order e0 where e0.status in (:p1)", q.hql());
```

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `in`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status in (?,?)
```

`!contains`는 `not in`이 됩니다. NULL이 될 수 있는 컬럼도 단순한 `not in`입니다(SQL 의미, 결정 D48).
값이 NULL인 행은 `not in`에 일치하지 않으며, lambdaQL은 `LQL-5009`를 `DEBUG` 수준으로 기록합니다. Java의 `!contains`는 NULL 값에 대해 true이므로, 이런 행을 유지하려면 `o.getDescription() == null || !descs.contains(o.getDescription())`처럼 null 검사를 작성하십시오.
**(테스트됨)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java), 영역 `not-in`:

```java
List<Long> ids = List.of(1L, 2L);
// the id can never be NULL: a plain NOT IN
assertEquals("select e0 from Order e0 where e0.id not in (:p1)",
        qb.from(Order.class).where(o -> !ids.contains(o.getId())).toHql().hql());
// a column that can be NULL: the SQL meaning (D48), a NULL value is not "not in" the list (DEBUG LQL-5009);
// to keep those rows, write o.getDescription() == null || !descs.contains(o.getDescription())
List<String> descs = Arrays.asList("a", "b");
assertEquals("select e0 from Order e0 where e0.description not in (:p1)",
        qb.from(Order.class).where(o -> !descs.contains(o.getDescription())).toHql().hql());
```

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `not-in`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.id not in (?,?)
```

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `not-in#2`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.description not in (?,?)
```

| 경우 | 결과 |
|---|---|
| 수신 객체가 캡처한 `Collection`, `List`, `Set` | 지원 |
| 컬렉션에 `null` 요소가 있음 | 쿼리가 실행될 때 예외 |
| 빈 컬렉션 | `contains`는 FALSE, `!contains`는 TRUE(Hibernate는 `1=0` / `1=1`을 씀) |
| 요소 1,500개 | H2, MariaDB, Oracle 23ai에서 문장 하나로 동작함(이전 Oracle 버전은 1,000개 제한이 있음. 테스트하지 않음) |
| 수신 객체가 엔티티 컬렉션: `o.getItems().contains(item)` | **거부됨**. `exists` 서브쿼리를 사용하십시오([5. 서브쿼리](05-subquery.md)) |
| 람다 안에서 만든 컬렉션: `List.of(1, 2).contains(o.getStatus())` | **거부됨**. 밖에서 만들어 캡처하십시오 |

<a id="310-association-paths"></a>
## 3.10 연관 경로

`o.getCustomer().getName()`은 **실행되지 않습니다**. "조인한 고객의 `name` 컬럼"이라는 컬럼을 가리킬 뿐입니다.
lambdaQL은 JPA Metamodel로 각 단계를 확인합니다.

| 경로 단계 | HQL | 예 |
|---|---|---|
| to-one 연관(`@ManyToOne`, `@OneToOne`) | 별칭이 붙은 `join`(기본은 inner, 아래 참고) | `o.getCustomer().getName()` -> `join e0.customer j1` ... `j1.name` |
| 임베디드(`@Embedded`) | 같은 테이블, 조인 없음. 값 객체 전체의 같음 비교는 컬럼마다 펼침(아래 참고) | `Order`에 임베디드 `address`가 있다면: `o.getAddress().getCity()` -> `e0.address.city` |
| 대상의 `@Id`로 끝나는 경로 | 외래 키 컬럼, 조인 없음 | `o.getCustomer().getId()` -> `e0.customer.id` |
| 연관 자체를 `null`과 비교 | 외래 키 컬럼, 조인 없음 | `o.getCustomer() == null` -> `e0.customer is null` |
| 컬렉션(`@OneToMany`, `@ManyToMany`) | **거부됨** | `o.getItems()...` -> `exists` 서브쿼리 사용 |

**값 객체 전체의 비교.** 임베디드 값 객체(`@Embedded`) 전체를 같음으로 비교하면 lambdaQL은 컬럼마다 비교 하나로 펼칩니다(B6).
`o.getAddress().equals(addr)`, `Objects.equals(o.getAddress(), addr)`, `addr.equals(o.getAddress())`는 모두 `e0.address.city = :p1 and e0.address.zip = :p2`가 됩니다.

- 부정(`!o.getAddress().equals(addr)`)은 `e0.address.city <> :p1 or e0.address.zip <> :p2`입니다.
- `o.getAddress() == null`은 `e0.address.city is null and e0.address.zip is null`이고, `!= null`은 `e0.address.city is not null or e0.address.zip is not null`입니다.
- 각 컬럼은 SQL 의미를 따릅니다(D48). NULL 컬럼은 `=`에도 `<>`에도 일치하지 않습니다. 예를 들어 (Seoul, NULL) 행은 같음 비교에도 부정에도 일치하지 않습니다(세 DB에서 실측).
- 캡처한 값 객체의 각 속성은 쿼리가 실행될 때 읽어 바인딩합니다. 캡처한 객체가 `null`이면 모든 컬럼에 NULL을 바인딩하므로 일치하는 행이 없고, 컬럼마다 `LQL-4045`를 `WARN` 수준으로 기록합니다.
- 중첩된 값 객체도 펼칩니다.
- 크기 비교(`<` 등)와 다른 타입과의 비교는 여전히 `LQL-3012` 오류입니다. 필드를 비교하십시오.

**(테스트됨)** [`MappingRulesTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/MappingRulesTest.java), 영역 `embedded-compare`:

```java
// address is @Embedded (city, zip): comparing the whole value object compares each column (D48)
Address addr = new Address("Seoul", "04524");
SelectQuery<Order> same = qb.from(Order.class).where(o -> o.getAddress().equals(addr));
assertEquals("select e0 from Order e0 where e0.address.city = :p1 and e0.address.zip = :p2", same.toHql().hql());
assertEquals(Map.of("p1", "Seoul", "p2", "04524"), same.toHql().parameters());
// Objects.equals and addr.equals(...) are the same comparison
assertEquals(same.toHql().hql(), qb.from(Order.class).where(o -> Objects.equals(o.getAddress(), addr)).toHql().hql());
// null checks each column: "no address" means every column is NULL
SelectQuery<Order> none = qb.from(Order.class).where(o -> o.getAddress() == null);
assertEquals("select e0 from Order e0 where e0.address.city is null and e0.address.zip is null", none.toHql().hql());
SelectQuery<Order> some = qb.from(Order.class).where(o -> o.getAddress() != null);
assertEquals("select e0 from Order e0 where e0.address.city is not null or e0.address.zip is not null",
        some.toHql().hql());
// each column follows the SQL meaning: a NULL column matches neither = nor <>
SelectQuery<Order> other = qb.from(Order.class).where(o -> !o.getAddress().equals(addr));
assertEquals("select e0 from Order e0 where e0.address.city <> :p1 or e0.address.zip <> :p2", other.toHql().hql());
```

`where`에서 연관 경로는 기본적으로 **inner join**입니다(결정 D36).
**(테스트됨)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java), 영역 `inner-join`:

```java
String name = "Kim";
RenderedQuery q = qb.from(Order.class).where(o -> o.getCustomer().getName().equals(name)).toHql();
assertEquals("select e0 from Order e0 join e0.customer j1 where j1.name = :p1", q.hql());
```

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `inner-join`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name=?
```

- **고객이 없는 주문**은 결과에 없습니다. 조인할 고객 행이 없기 때문입니다.
- lambdaQL은 null 검사를 대신 추가하지 않습니다. 경로 앞에 `o.getCustomer() != null &&`를 쓸 필요가 없습니다.

**null 가드로는 고객이 없는 주문이 남지 않습니다.** `where`의 경로는 항상 inner join입니다(SQL 의미, 결정 D48).
그래서 `o.getCustomer() == null || ...`은 고객이 없는 주문과 일치하지 않습니다. 그 주문에는 조인할 고객 행이 없기 때문입니다. 이런 조건에 대해 lambdaQL은 `LQL-5009`를 `DEBUG` 수준으로 기록합니다.
고객이 없는 주문을 남기려면 명시적 `leftJoin`(결정 D44, [6.8](06-select-group.md#68-explicit-joins) 참고)을 사용하고 `c == null || ...`이라고 쓰십시오.
**(테스트됨)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java), 영역 `null-guard`:

```java
String name = "Kim";
// the SQL meaning (D48): a where path is an inner join, so "customer is null" never matches here
// (lambdaQL logs LQL-5009 at DEBUG); to keep orders without a customer, use leftJoin (D44)
RenderedQuery q = qb.from(Order.class)
        .where(o -> o.getCustomer() == null || o.getCustomer().getName().equals(name))
        .toHql();
assertEquals("select e0 from Order e0 join e0.customer j1 where e0.customer is null or j1.name = :p1", q.hql());

// the guard removes those orders, so an inner join is right
RenderedQuery q2 = qb.from(Order.class)
        .where(o -> o.getCustomer() != null && o.getCustomer().getName().equals(name))
        .toHql();
assertEquals("select e0 from Order e0 join e0.customer j1 where e0.customer is not null and j1.name = :p1", q2.hql());
```

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `null-guard`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.id is null or c1_0.name=?
```

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `null-guard#2`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.id is not null and c1_0.name=?
```

Hibernate는 `e0.customer is null`을 `c1_0.id is null`로 씁니다. inner join 뒤에서는 이 조건이 참이 되지 않습니다.

- `||`의 다른 부분도 마찬가지입니다. `o.getStatus() == 1 || o.getCustomer().getName().equals(name)`도 inner join을 사용하므로, status가 1이지만 고객이 없는 주문은 반환되지 않습니다(Java라면 반환).

고객이 **없는** 주문을 찾으려면 연관 자체를 비교하십시오. 조인 없이 외래 키 컬럼을 검사합니다.
**(테스트됨)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java), 영역 `no-association`:

```java
RenderedQuery q = qb.from(Order.class).where(o -> o.getCustomer() == null).toHql();
assertEquals("select e0 from Order e0 where e0.customer is null", q.hql());
```

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `no-association`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id is null
```

연관 엔티티의 `@Id`로 끝나는 경로는 외래 키 컬럼입니다. 조인이 필요 없습니다.
**(테스트됨)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java), 영역 `fk-path`:

```java
RenderedQuery q = qb.from(Order.class).where(o -> o.getCustomer().getId() == 1).toHql();
assertEquals("select e0 from Order e0 where e0.customer.id = 1", q.hql());
```

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `fk-path`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id=1
```

추가 사항:

- 같은 경로는 별칭 하나를 받습니다. 이 별칭은 `where`, `orderBy`, `select`가 함께 사용합니다.
- `orderBy`나 `select`에**만** 쓰인 경로는 `left join`이므로 그곳에서는 행을 잃지 않습니다(규칙 S8, J1). 같은 경로를 `where`에서도 쓰면 `orderBy`와 `select`는 `where`의 조인, 즉 inner join을 사용합니다.
- 두 엔티티의 비교(`o.getCustomer() == c`)는 **계획 중**입니다. 지금은 id를 비교하십시오: `o.getCustomer().getId() == c.getId()`(여기서 `c`는 캡처한 객체).

<a id="311-captured-values"></a>
## 3.11 캡처 값

람다는 밖의 값을 사용할 수 있습니다. 지역 변수, 필드, 캡처한 객체의 메서드 호출입니다.

```java
LocalDateTime since = LocalDateTime.of(2026, 1, 1, 0, 0);
.where(o -> o.getUpdateAt().isAfter(since))              // 지역 변수
.where(o -> o.getStatus() == this.defaultStatus)          // this의 필드
.where(o -> o.getPrice() > filter.getMinPrice())           // 캡처한 객체의 메서드
.where(o -> o.getDescription().equals(form.getText().trim()))
```

규칙:

- 엔티티를 사용하지 않는 부분은 **쿼리가 실행될 때마다** Java에서 계산합니다. 그 결과가 바인드 파라미터가 됩니다.
- 컴파일 시점 상수(`static final int MAX = 5;`)는 캡처 값이 아닙니다. javac가 람다 안에 값을 복사하므로 `5`처럼 리터럴이 됩니다([3.3](#33-basic-form) 참고).
- 람다는 한 번 분석되고 결과는 캐시됩니다. 새 캡처 값은 호출할 때마다 읽습니다.
- 엔티티 부분만 SQL이 됩니다. `form.getText().trim()`은 Java이고, `o.getDescription().trim()`이라면 데이터베이스 함수가 됩니다(계획 중).
- Java에서 캡처한 지역 변수는 사실상 final이어야 합니다. 값이 바뀐다면 람다 전에 새 변수에 계산해 두십시오.
- 필드를 사용하는 람다는 `this`를 캡처합니다. [9.8](09-java-notes.md#98-lambdas-in-instance-methods-capture-this)을 참고하십시오.

<a id="312-errors-that-depend-on-order-of-evaluation"></a>
## 3.12 평가 순서에 따라 달라지는 오류

SQL은 `AND`/`OR`를 왼쪽에서 오른쪽으로 평가한다고 약속하지 않습니다. 그래서 다음과 같은 "가드"는 SQL에서 안전하지 않습니다.

```java
qb.from(Item.class)
  .where(i -> i.getQty() != 0 && i.getPrice() / i.getQty() > 2)   // 거부됨
```

가드 아래의 `/`, `%`, 캐스트는 거부됩니다. 대신 나눗셈 없이 비교하십시오: `i.getQty() > 0 && i.getPrice() > 2 * i.getQty()`.

<a id="313-what-is-rejected"></a>
## 3.13 거부되는 것

lambdaQL은 코드 `[LQL-xxxx]`와 소스 줄 번호가 담긴 예외를 던집니다. 추측하지 않습니다.
대부분의 모양은 람다를 분석할 때(처음 사용할 때) 발견됩니다. 잘못된 캡처 값(메서드를 호출한 `null`, NaN, `null` 요소가 있는 컬렉션)은 쿼리가 실행될 때 발견됩니다.
[11. 오류와 언어](../11-errors-and-languages.md)를 참고하십시오.

| 형태 | 예 | 해결 방법 |
|---|---|---|
| 블록 람다 안의 반복문, `switch`, 지역 변수 | `o -> { int x = 1; return ...; }` | 식 하나로 작성 |
| 값 삼항 연산자 | `o.getStatus() == (flag ? 1 : 2)` | 람다 전에 값을 계산 |
| 일반 boolean 삼항 연산자 | `c ? x : y` | [3.5](#35-and-or-not)의 허용된 형태 사용 |
| 단락 평가를 하지 않는 연산자 | `a & b`, `a \| b`, `a ^ b` | `&&`, `\|\|` 사용 |
| 문자열 `+` | `o.getDescription() == "A" + code` | 람다 전에 문자열을 만듦(컬럼용은 계획 중) |
| 람다 안의 람다나 스트림 | `o -> list.stream().anyMatch(...)` | `contains`([3.9](#39-in-with-a-captured-collection))나 서브쿼리 사용 |
| 컬럼에 대한 문자열 함수 | `o.getDescription().startsWith("A")` | 계획 중 |
| 엔티티의 다른 메서드 | `o.calcSomething()` | getter만 사용 |
| 엔티티 컬렉션 경로 | `o.getItems().size() > 0` | `exists` 서브쿼리 |
| 엔티티와 엔티티 비교 | `o.getCustomer() == c` | id 비교(엔티티 비교는 계획 중) |
| 값 객체의 크기 비교, 다른 타입과의 비교(`LQL-3012`) | `o.getAddress().compareTo(a) > 0`(`Address`가 `Comparable`일 때) | 필드를 비교. 같음 비교는 지원 |
| 가드 아래의 `/`, `%`, 캐스트 | [3.12](#312-errors-that-depend-on-order-of-evaluation) 참고 | 가드를 없애거나 조건을 바꿈 |
| 메서드를 호출하는 null 캡처 값 | `p == null`일 때 `o.getDescription().equals(p.trim())`, `o.getStatus() > p.intValue()`(`Double p`) | 앞에 null 가드(`p == null \|\| ...`)를 쓰거나 쿼리 전에 `p`를 확인 |
| `NaN` / `Infinity` 캡처 값 | `o.getPrice() > Double.NaN` | NaN을 보내지 않음 |
| 익명 클래스, `Serializable`이 아닌 람다 | `new Where<Order>() { ... }`, `java.util.function.Predicate` | 람다 사용 |

예외 타입(모두 `LambdaQLException`을 상속):

| 예외 | 발생 시점 |
|---|---|
| `LambdaAnalysisException` | 람다를 읽을 수 없을 때(직렬화 가능한 람다가 아닌 경우 등) |
| `UnsupportedLambdaShapeException` | 지원하지 않는 모양일 때(반복문, 값 삼항 연산자, ...) |
| `TranslationException` | 의미를 번역할 수 없을 때(알 수 없는 메서드, 컬렉션 경로, ...) |

Java 전용 세부 사항은 [9. Java 노트](09-java-notes.md)에 있습니다. 모든 규칙을 한곳에서 보려면: [10. 규칙과 제한](10-rules-and-limits.md).

---

[Java 트랙](README.md) | 이전: [2. 시작하기](02-getting-started.md) | 다음: [4. 정렬과 페이징](04-order-paging.md) | [이 페이지의 Kotlin 버전](../kotlin/03-where.md)
