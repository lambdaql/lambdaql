<a id="3-conditions-where-java"></a>
# 3. 조건(`where`) (Java)

[Java 트랙](README.md) | [매뉴얼 홈](../README.md) | 이전: [2. 시작하기](02-getting-started.md) | 다음: [4. 정렬과 페이징](04-order-paging.md) | [이 페이지의 Kotlin 버전](../kotlin/03-where.md)

공통 페이지: [1. 개요](../01-overview.md) · [11. 오류와 언어](../11-errors-and-languages.md)

---

<a id="31-the-idea-in-one-sentence"></a>
## 3.1 개요

**lambdaQL 람다 표현식은 코드가 아니라 쿼리 표현식입니다.** lambdaQL은 조건 람다 표현식을 호출하지 않고 람다 표현식의 바이트 opcode를 분석합니다.
캡처 값만으로 된 식만 Java에서 먼저 계산합니다([3.11](#311-captured-values) 참고).
그래서 엔티티를 다루는 부분에는 Java의 실행 규칙(객체 동일성, 박싱 캐시)이 적용되지 않습니다.
lambdaQL은 자체 규칙을 사용합니다. 이 장에서 그 규칙을 설명합니다.

lambdaQL은 람다 표현식을 쿼리로 분석하지 못하면 코드가 붙은 예외를 던집니다([3.13](#313-what-is-rejected) 참고).

<a id="32-example-entities"></a>
## 3.2 예제 엔티티

예제는 예제 엔티티 `Order`, `Customer`, `Item`을 사용합니다. 속성 목록은 [2. 시작하기의 예제 엔티티](02-getting-started.md#example-entities)에 있습니다.

이 장에서 자주 쓰는 속성은 다음과 같습니다.

- `Order.price`(`double`)와 `Order.status`(`short`)는 기본형이므로 NULL이 될 수 없습니다.
- `Order.cnt`(`Integer`), `Order.total`(`Double`), `Order.description`(`String`)은 NULL이 될 수 있습니다.
- `Order.customer`는 `Customer`로 가는 `@ManyToOne` 연관이고, `Order.kind`는 enum `Order.Kind`(`NORMAL`, `RUSH`)입니다.

<a id="33-basic-form"></a>
## 3.3 기본 형태

<!-- region: SelectQueryTest.java#to-list -->
```java
QueryBuilder qb = new QueryBuilder(emf);
List<Order> orders = qb.from(Order.class)
        .where(o -> o.getStatus() == 1 && o.getPrice() > 100)
        .toList();
```

**데이터베이스별 SQL**

<!-- sql: to-hql -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>100.0
```

이 장의 예제는 H2, MariaDB, Oracle에서 Java로 같은 필터를 적용한 결과와 같은 행을 반환합니다.

`toHql()`은 쿼리를 실행하지 않고 HQL과 파라미터를 돌려줍니다. `assertEquals`의 문자열이 lambdaQL이 만드는 HQL입니다.
<!-- region: SelectQueryTest.java#to-hql -->
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

**데이터베이스별 SQL**

<!-- sql: to-hql -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>100.0
```

**데이터베이스별 SQL**

<!-- sql: to-hql#2 -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>?
```

`where()`는 여러 번 호출할 수 있습니다. 조건들은 `AND`로 결합됩니다.
<!-- region: SelectQueryTest.java#where-many -->
```java
RenderedQuery q = qb.from(Order.class)
        .where(o -> o.getStatus() == 1 || o.getStatus() == 3)
        .where(o -> o.isActive())
        .toHql();
assertEquals("select e0 from Order e0 where (e0.status = 1 or e0.status = 3) and e0.active = true", q.hql());
```

**데이터베이스별 SQL**

<!-- sql: where-many -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where (o1_0.status=1 or o1_0.status=3) and o1_0.active=true
```

`where`에는 메서드 참조를 쓸 수 없습니다. `where(Order::isActive)`는 `LQL-1009`로 거부되므로 `where(o -> o.isActive())`로 쓰십시오([9.9](09-java-notes.md#99-method-references) 참고).

**HQL 안의 값.** 값이 어디에서 왔는지에 따라 규칙이 정해집니다.

| 값 | HQL에서 | 예 |
|---|---|---|
| 람다 표현식에 직접 쓴 상수: 숫자(소수 포함), 문자열, `true`/`false`, `null`, enum 상수, 컴파일 시점 `static final` 상수 | **리터럴** | `o.getPrice() > 100` → `e0.price > 100.0` |
| 캡처 값: 지역 변수, 필드, 메서드 파라미터 | **바인드 파라미터** | `o.getPrice() > minPrice` → `e0.price > :p1` |
| 계산한 값: `new BigDecimal("1.5")`, `form.getText().trim()`, `LocalDateTime.of(...)` | **바인드 파라미터** | `:p1` |

- 상수는 호출할 때마다 같습니다. 그래서 SQL 문장이 바뀌지 않고, 데이터베이스는 쿼리 계획을 세울 때 값을 볼 수 있습니다.
- 문자열 상수 안의 작은따옴표는 escape됩니다: `"O'Brien"`은 `'O''Brien'`이 됩니다. 이 문자열은 사용자 입력이 아니라 클래스에 컴파일된 상수이므로 SQL 주입 위험이 없습니다.
- 캡처 값은 사용자에게서 올 수 있으므로 바인드 파라미터입니다. 호출마다 값만 바뀝니다.
- 예외는 하나입니다. 정수 컬럼과 비교하는 소수 캡처 값은 소수 리터럴(`2.5BD`)로 씁니다([3.4](#34-comparisons)의 "정수 컬럼과 소수 값"). 숫자로 바꾼 값만 쓰므로 SQL 주입 위험은 없습니다.

enum 상수는 전체 클래스 이름으로 씁니다.
<!-- region: SelectQueryTest.java#enum -->
```java
RenderedQuery q = qb.from(Order.class).where(o -> o.getKind() == Order.Kind.RUSH).toHql();
assertEquals("select e0 from Order e0 where e0.kind = org.lambdaql.query.Order$Kind.RUSH", q.hql());
```

**데이터베이스별 SQL**

<!-- sql: enum -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.kind='RUSH'
```

예제 코드: [`SelectQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java)(`to-list`, `to-hql`, `where-many`, `enum`).

<a id="34-comparisons"></a>
## 3.4 비교

| Java | HQL | 참고 |
|---|---|---|
| `o.getStatus() == 1` | `e0.status = 1` | |
| `o.getStatus() != 1` | `e0.status <> 1` | |
| `o.getPrice() > p` | `e0.price > :p1` | `<`, `<=`, `>=`도 마찬가지 |
| `o.getUpdateAt().compareTo(since) > 0` | `e0.updateAt > :p1` | 날짜와 시간도 `compareTo`로 비교. `isAfter`, `isBefore`는 지원하지 않음(`LQL-3008`) |
| `o.getDescription().compareTo("m") < 0` | `e0.description < 'm'` | `compareTo(..) op 0`은 `op`가 됨 |

숫자와의 비교(`== 1`이나 `> 100.0`처럼 한쪽이 기본형)는 **SQL 의미**를 따릅니다.
컬럼이 NULL인 행은 반환되지 않습니다.

`+`, `-`, `*`, `/`를 사용한 단순 산술을 쓸 수 있습니다.
컬럼이 들어간 정수 `/`는 피하십시오. SQL에서 계산하므로 데이터베이스마다 결과가 다릅니다(MariaDB는 `5/2 = 2.5`).
캡처 값끼리의 계산(아래의 `n / 2.0`)은 쿼리 전에 Java에서 하므로 이 차이가 없습니다.

아래 예의 `cnt`는 `Order`의 정수 컬럼(`Integer cnt`)입니다.

**컬럼에 대한 산술과 인덱스**. `o.getCnt() * 2 > n`처럼 컬럼에 산술을 걸면 인덱스를 쓸 수 없습니다. lambdaQL은 `LQL-5012`를 `WARN` 수준으로 기록하고, `SargableMode.STRICT`에서는 분석이 실패합니다(`LQL-3066`).
값 쪽으로 옮길 때는 `o.getCnt() > n / 2.0`처럼 소수로 나누십시오. 정수 나눗셈 `n / 2`는 다른 행을 냅니다. 음수를 곱했다면 비교 방향을 바꾸십시오. 자세한 내용은 [10.2](10-rules-and-limits.md#db-functions-on-a-column)를 참고하십시오.

**정수 컬럼과 소수 값**. 정수 컬럼을 소수 값과 비교하면 lambdaQL은 값을 적은 그대로 비교합니다. 반올림이나 값 제외는 하지 않습니다.

- 람다 표현식에 적은 상수는 리터럴로 남습니다. `o.getCnt() >= 2.5`는 `e0.cnt >= 2.5`가 됩니다.
- 캡처 값도 소수 리터럴로 씁니다. `e0.cnt >= 2.5BD`가 되고 바인드 파라미터는 없습니다. `BD`는 HQL의 `BigDecimal` 리터럴 접미사입니다.
- 그런 요소가 있는 IN 목록은 모든 요소를 소수 리터럴로 씁니다(`e0.cnt in (2.5BD, 3.0BD)`).

캡처 값을 바인드 파라미터로 보내는 원칙의 예외입니다. Hibernate 7.4는 정수 컬럼과 비교하는 파라미터에 소수를 바인딩하면 `BigDecimal` 타입을 지정해도 거부합니다(`QueryArgumentException`, 실측).

이전에는 값을 컬럼 타입으로 잘라 `2.5`가 `2`가 되었고, 잘못된 행을 반환했습니다. Hibernate 6.6은 정수 컬럼 타입으로 바인딩한 소수를 거부하고(`CoercionException`), IN 목록의 요소는 조용히 자릅니다(세 DB에서 실측).
정수 비교로 바꾸는 방식(`>= 3`)은 채택하지 않았습니다. 실행되는 SQL이 람다 표현식과 달라지고, 엔티티 매핑(`Integer`)과 DB 컬럼 타입(예: 소수가 있는 `DECIMAL`)이 다르면 잘못된 행을 냅니다.

결과는 H2, MariaDB 10.11, Oracle 23.26에서 올바릅니다(실측). 세 DB 모두 SQL은 `cnt>=2.5`, `cnt in (2.5,3.0)`처럼 컬럼을 그대로 두고 소수와 비교합니다.
MariaDB의 `EXPLAIN`은 인덱스 범위 스캔(`type=range`)을 보여 줍니다. Oracle은 컬럼에 변환이 붙지 않아 인덱스를 쓸 수 있는 형태입니다. 인덱스 힌트를 주면 `INDEX RANGE SCAN`이고, 3000행 테이블에서 힌트가 없으면 정수 리터럴(`cnt >= 99`)과 같게 전체 스캔을 고릅니다(모두 실측).

<!-- region: SqlMeaningTest.java#whole-number -->
```java
// cnt is a whole number; 2.5 is compared as written (it is never cut to 2): a decimal literal
double half = 2.5;
RenderedQuery atLeast = qb.from(Order.class).where(o -> o.getCnt() >= half).toHql();
assertEquals("select e0 from Order e0 where e0.cnt >= 2.5BD", atLeast.hql());
assertEquals(Map.of(), atLeast.parameters());                                // written as a decimal literal
// a constant written in the lambda stays a literal
assertEquals("select e0 from Order e0 where e0.cnt >= 2.5", qb.from(Order.class).where(o -> o.getCnt() >= 2.5).toHql().hql());
// an IN list with such a value is written as decimal literals
List<Double> values = List.of(2.5, 3.0);
RenderedQuery in = qb.from(Order.class).where(o -> values.contains(o.getCnt().doubleValue())).toHql();
assertEquals("select e0 from Order e0 where e0.cnt in (2.5BD, 3.0BD)", in.hql());
```

**부동소수점:** `float`/`double` 컬럼에 대해 lambdaQL은 Java와 비트 단위까지 같은 결과를 약속하지 않습니다.
캡처한 `NaN`이나 `Infinity` 값은 쿼리가 실행될 때 거부됩니다.

예제 코드: [`SqlMeaningTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlMeaningTest.java)(`whole-number`).

<a id="35-and-or-not"></a>
## 3.5 AND, OR, NOT

| Java | HQL |
|---|---|
| `a && b` | `a and b` |
| `a \|\| b` | `a or b` |
| `!a` | `not (a)` |
| 괄호 | 필요한 만큼 유지 |

<!-- region: ManualJavaWhereTest.java#and-or-not -->
```java
double minPrice = 100;
SelectQuery<Order> both = qb.from(Order.class)
        .where(o -> (o.getStatus() == 1 || o.isActive()) && o.getPrice() > minPrice);
assertEquals("select e0 from Order e0 where (e0.status = 1 or e0.active = true) and e0.price > :p1",
        both.toHql().hql());
SelectQuery<Order> not = qb.from(Order.class)
        .where(o -> !(o.getStatus() == 1 && o.getPrice() > minPrice));
// the NOT is pushed into the comparisons: the same rows (status and price are never NULL)
assertEquals("select e0 from Order e0 where e0.status <> 1 or e0.price <= :p1", not.toHql().hql());
```

규칙:

- 결과는 **SQL 3값 논리**(TRUE / FALSE / UNKNOWN)를 따릅니다. lambdaQL은 이 방식이 H2, MariaDB, Oracle에서 같은 행을 반환하는지 확인했습니다.
- javac와 lambdaQL은 `!`의 위치를 옮길 수 있습니다(드 모르간, `!(x > y)`를 `x <= y`로). 위 예제의 `!(... && ...)`는 `e0.status <> 1 or e0.price <= :p1`이 됩니다. 두 컬럼은 NULL이 될 수 없으므로 결과는 `not (...)`과 같습니다.
- lambdaQL은 **조건을 단순화하지 않습니다**. `o.getPrice() > p || o.getPrice() <= p`라고 쓰면 쓴 그대로 보냅니다. `price`가 NULL인 행은 반환되지 않습니다. 이는 정상적인 SQL 동작입니다.
- `&&`와 `||`를 사용하십시오. 단락 평가를 하지 않는 연산자 `&`, `|`, `^`는 거부됩니다.

**boolean 삼항 연산자.** 짧은 형태 세 가지만 허용합니다. 각각 `AND`, `OR`, `NOT`과 같습니다.

| Java | 같은 의미 |
|---|---|
| `c ? x : false` | `c && x` |
| `c ? true : x` | `c \|\| x` |
| `c ? false : true` | `!c` |

캡처 값과 상수만으로 값을 고르는 삼항 연산자(`o.getStatus() == (flag ? 1 : 2)`)는 쿼리를 실행할 때 Java에서 계산해 바인드 파라미터로 넘깁니다.
엔티티 값으로 값을 고르는 삼항 연산자(`o.getStatus() == (o.isActive() ? 1 : 2)`)는 SQL `case when … end`가 됩니다([3.5.1](#351-values-chosen-by-a-condition) 참고).
그 밖의 boolean 삼항 연산자(`c ? x : y`)는 거부됩니다(`LQL-2021`).

<!-- region: ManualJavaWhereTest.java#ternary-outside -->
```java
// a ternary of captured values and constants: computed in Java, bound as :p1
SelectQuery<Order> query = qb.from(Order.class).where(o -> o.getStatus() == (flag ? 1 : 2));
assertEquals("select e0 from Order e0 where e0.status = :p1", query.toHql().hql());
// a ternary that reads the entity becomes case when
SelectQuery<Order> byEntity = qb.from(Order.class).where(o -> o.getStatus() == (o.isActive() ? 1 : 2));
assertEquals("select e0 from Order e0 where e0.status = case when e0.active = true then 1 else 2 end",
        byEntity.toHql().hql());
```

예제 코드: [`ManualJavaWhereTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaWhereTest.java)(`and-or-not`, `ternary-outside`).

<a id="351-values-chosen-by-a-condition"></a>
### 3.5.1 조건으로 고르는 값(`case when`)

엔티티 값으로 값을 고르는 삼항 연산자는 SQL `case when … end` 식 하나가 됩니다.
조건에는 `&&`, `||`, `!`를 섞어 쓸 수 있습니다. 삼항 연산자를 이어 쓰면(`a ? x : b ? y : z`) 분기가 여러 개인 `case` 하나가 됩니다.

`case`는 `where`의 비교 값, `select`의 조회 값, `groupBy` 키, 정렬 키, `update`의 대입 값에 쓸 수 있습니다.

<!-- region: CaseWhenTest.java#case-where -->
```java
SelectQuery<Order> q = qb.from(Order.class).where(o -> o.getStatus() == (o.isActive() ? 1 : 2));
assertEquals("select e0 from Order e0 where e0.status = case when e0.active = true then 1 else 2 end", q.toHql().hql());
```

<!-- region: CaseWhenTest.java#case-select -->
```java
ValueQuery<String> q = qb.from(Order.class).select(o -> o.getPrice() > 100 ? "big" : "small");
assertEquals("select case when e0.price > 100.0 then 'big' else 'small' end from Order e0", q.toHql().hql());
```

<!-- region: CaseWhenTest.java#case-group -->
```java
// orders per price band: the case is the group key and the selected value
ValueQuery<String> q = qb.from(Order.class)
        .groupBy(o -> o.getPrice() > 100 ? "big" : "small")
        .select(o -> o.getPrice() > 100 ? "big" : "small");
assertEquals("select case when e0.price > 100.0 then 'big' else 'small' end from Order e0"
        + " group by case when e0.price > 100.0 then 'big' else 'small' end", q.toHql().hql());
```

<!-- region: CaseWhenTest.java#case-sort -->
```java
// active orders first, then by price
OrderedSelectQuery<Order> q = qb.from(Order.class).orderBy(o -> asc(o.isActive() ? 0 : 1).asc(o.getPrice())).limit(10);
assertEquals("select e0 from Order e0 order by case when e0.active = true then 0 else 1 end asc, e0.price asc, e0.id asc",
        q.toHql().hql());
```

<!-- region: CaseWhenTest.java#case-set -->
```java
// 10 % off the orders above 100
ExecutableUpdate<Order> u = qb.update(Order.class).set(o -> o.setPrice(o.getPrice() > 100 ? o.getPrice() * 0.9 : o.getPrice())).all();
assertEquals("update Order e0 set e0.price = case when e0.price > 100.0 then (e0.price * 0.9) else e0.price end", u.toHql().hql());
```

규칙:

- `case`는 분기를 위에서부터 차례로 검사합니다. 그래서 두 번째 이후 분기의 조건은 람다 표현식에 쓴 조건 그대로입니다.
- `where`에서 비교하는 `case`는 행마다 계산되므로, 데이터베이스는 그 안의 컬럼 인덱스를 쓰지 못합니다. lambdaQL은 이때 `WARN` 수준으로 `LQL-5013`을 기록합니다.
- 정렬 함수 체인 자체를 조건으로 고르는 형태(`flag ? desc(o.getPrice()) : asc(o.getId())`)는 거부됩니다(`LQL-2023`). 조건은 키 값 안에 쓰십시오.

예제 코드: [`CaseWhenTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/CaseWhenTest.java)(`case-where`, `case-select`, `case-group`, `case-sort`, `case-set`).

<a id="36-null-rules"></a>
## 3.6 NULL 규칙

가장 중요한 부분입니다. 단순한 원칙 두 가지가 있습니다.

1. **엔티티 값은 SQL 의미로 비교합니다**. 객체 사이의 `==`, `equals`, `Objects.equals`, `!=`는 `=`와 `<>`가 됩니다.
   값이 NULL인 행은 어느 쪽에도 일치하지 않습니다.
   결과가 Java와 다를 수 있으면 lambdaQL은 `LQL-5009`를 `DEBUG` 수준으로 기록합니다.
2. **캡처 값은 Java에서 먼저 계산하고, 비교는 역시 SQL 의미를 따릅니다.** 캡처 값 `null`은 NULL로 바인딩되므로 `= :p1`과 `<> :p1`은 어떤 행에도 일치하지 않습니다.
   이 경우 lambdaQL은 실행 시점에 `LQL-4045`를 `WARN` 수준으로 기록합니다. NULL을 찾으려면 `x == null`이라고 쓰십시오(`is null`이 됨).
   캡처 값만으로 된 식(`p.trim()`, 다른 기본형으로 바꾸는 `min.intValue()` 등)은 Java에서 계산한 값이므로, 수신 객체가 `null`이면 Java처럼 예외(`LQL-4012`)를 던집니다.
   자기 기본형으로의 언박싱(`min.doubleValue()`)은 javac의 자동 언박싱과 같은 바이트 opcode이므로 실행하지 않고 박싱 값을 그대로 바인딩합니다([3.6.4](#364-captured-null-values) 참고).

lambdaQL은 **직접 작성한 경우에만 null을 처리합니다**(`== null`, `!= null`).
숨겨진 null 검사를 대신 추가하지 않습니다.

<a id="361-rule-table"></a>
### 3.6.1 규칙 표

| # | Java 형태 | HQL | NULL 동작 |
|---|---|---|---|
| R1 | 한쪽이 기본형: `o.getStatus() == 1`, `o.getPrice() > p` | `e0.status = 1`, `e0.price > :p1` | SQL 의미. 컬럼이 NULL이면 행이 반환되지 않음 |
| R2 | 양쪽이 객체: `o.getCnt() == wanted`(`Integer == Integer`), `String == String`, enum | `e0.cnt = :p1` | **값 비교.** 컬럼이 NULL이면 행이 반환되지 않음. 캡처 값 `null`: 일치하는 행 없음(`LQL-4045`) |
| R2 | `o.getCnt() != wanted`(객체) | `e0.cnt <> :p1` | SQL 의미. 컬럼이 NULL이면 행이 반환**되지 않음**(Java라면 반환). 캡처 값 `null`: 일치하는 행 없음(`LQL-4045`) |
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
람다 표현식은 쿼리이므로 Java의 메모리 규칙은 적용되지 않습니다.

<!-- region: ManualJavaWhereTest.java#boxed-equals -->
```java
// cnt is an Integer column; Integer == Integer compares values, not objects
Integer wanted = 3;
SelectQuery<Order> three = qb.from(Order.class).where(o -> o.getCnt() == wanted);
assertEquals("select e0 from Order e0 where e0.cnt = :p1", three.toHql().hql());
// a captured null is bound as NULL: "= NULL" matches no row (WARN LQL-4045)
Integer none = null;
SelectQuery<Order> noRow = qb.from(Order.class).where(o -> o.getCnt() == none);
assertEquals("select e0 from Order e0 where e0.cnt = :p1", noRow.toHql().hql());
// to find NULL, write the null check
SelectQuery<Order> nullCnt = qb.from(Order.class).where(o -> o.getCnt() == null);
assertEquals("select e0 from Order e0 where e0.cnt is null", nullCnt.toHql().hql());
```

cnt가 NULL인 행을 찾으려면 위 예제의 마지막 쿼리처럼 null 검사를 작성하십시오.

컬럼을 함수로 감싸지 않으므로 데이터베이스는 인덱스를 사용할 수 있습니다.

lambdaQL은 쿼리가 실행될 때 캡처 값을 바인딩합니다. `null`은 NULL로 바인딩되고, 일치할 수 있는 행이 없으므로 lambdaQL은 `LQL-4045`를 `WARN` 수준으로 기록합니다.
<!-- region: SelectQueryTest.java#null-safe -->
```java
// the SQL meaning: = NULL matches no row; lambdaQL logs LQL-4045 at WARN when it runs with a null
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

**데이터베이스별 SQL**

<!-- sql: null-safe -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.description=?
```

**데이터베이스별 SQL**

<!-- sql: null-safe#2 -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.description=?
```

> [!TIP]
> **IDE 경고.** IntelliJ는 이 형태에 "Number objects compared using `==`"를 표시합니다.
> lambdaQL 람다 표현식 안에서는 의도한 동작입니다. 메서드에 `@SuppressWarnings("NumberEquality")`를 선언하여 경고를 없앨 수 있습니다.

**NULL이 될 수 없는 컬럼.** id, 기본형 컬럼, `nullable = false` 컬럼은 NULL이 될 수 없습니다.
이런 컬럼의 `is null`은 항상 거짓이므로 lambdaQL은 이 검사를 상수로 접어 제거합니다.

- `o.getId() == null`은 `1=0`(행 없음)이 됩니다.
- `o.getId() != null && o.getId() == id`는 `e0.id = :p1`이 됩니다. null 검사가 사라지므로 데이터베이스가 인덱스를 사용할 수 있습니다.

`Long?` id에 대한 Kotlin `it.id == id`도 같은 바이트 opcode로 컴파일되므로, Kotlin 형태도 단순한 `e0.id = :p1`이 됩니다.

이 규칙은 경로에 **연관 단계가 없을 때만** 적용합니다. 고객이 없는 주문이 있으므로 `o.getCustomer().getId()`는 NULL이 될 수 있고, 이 경로의 null 검사는 유지합니다.

<!-- region: SelectQueryTest.java#never-null -->
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

<!-- region: ManualJavaWhereTest.java#null-check -->
```java
// only rows where cnt is not NULL and not 1
SelectQuery<Order> notOne = qb.from(Order.class).where(o -> o.getCnt() != 1);
assertEquals("select e0 from Order e0 where e0.cnt <> 1", notOne.toHql().hql());
// rows where cnt is NULL too
SelectQuery<Order> notOneOrNull = qb.from(Order.class).where(o -> o.getCnt() == null || o.getCnt() != 1);
assertEquals("select e0 from Order e0 where e0.cnt is null or e0.cnt <> 1", notOneOrNull.toHql().hql());
```

`equals()`와 `Objects.equals()`는 같은 비교(`=`)가 됩니다. 부정한 `!Objects.equals(...)`는 `<>`가 되고, 값이 NULL인 행은 반환되지 않습니다.

<!-- region: ManualJavaWhereTest.java#equals-forms -->
```java
String text = "a";
assertEquals("select e0 from Order e0 where e0.description = :p1",
        qb.from(Order.class).where(o -> o.getDescription().equals(text)).toHql().hql());
// the same comparison; when text is null, no row matches
assertEquals("select e0 from Order e0 where e0.description = :p1",
        qb.from(Order.class).where(o -> Objects.equals(o.getDescription(), text)).toHql().hql());
// rows where cnt is NULL are not returned (the SQL meaning)
SelectQuery<Order> notOne = qb.from(Order.class).where(o -> !Objects.equals(o.getCnt(), 1));
assertEquals("select e0 from Order e0 where e0.cnt <> 1", notOne.toHql().hql());
```

**엔티티 값에 대한 SQL 의미**. 엔티티 값 두 개의 비교와, 엔티티 값을 값과 `!=`로 비교하는 경우는 SQL을 따릅니다. 값이 NULL인 행은 `=`에도 `<>`에도 일치하지 않습니다.

Java에서는 `!text.equals(null)`이 true이고 `Objects.equals(null, null)`도 true이므로 Java라면 이런 행을 반환합니다.
이런 차이가 생길 수 있으면 lambdaQL은 `LQL-5009`를 `DEBUG` 수준으로 조건마다 한 번 기록합니다. NULL 행을 유지하려면 람다 표현식에 null 검사를 직접 작성하십시오.

H2, MariaDB, Oracle. 행은 description/custName 쌍 `a/a`, `a/b`, `null/a`, `null/null`, `b/null`이고 `text`는 `"a"`:

<!-- region: SqlMeaningTest.java#sql-meaning -->
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

이유: 람다 표현식 안의 비교는 SQL 의미를 따릅니다. SQL에서 NULL과의 `=`, `<>`는 참이 되지 않습니다.
캡처 값이 null인지는 쿼리가 실행될 때만 알 수 있으므로, lambdaQL은 실행할 때 경고를 기록합니다.

캡처 값만으로 된 식은 쿼리 전에 Java에서 계산하는 값입니다. `p.trim()` 같은 메서드 호출과 다른 기본형으로 바꾸는 언박싱(`p.intValue()`, `Double p`)이 이에 해당하며, `null`이면 Java처럼 예외(`LQL-4012`)를 던집니다.
다만 자기 기본형으로의 언박싱(`Double.doubleValue()`, `Integer.intValue()`, `Long.longValue()` 등, 뒤의 확대 변환 포함)은 계산하지 않습니다.
javac의 자동 언박싱과 손으로 쓴 호출은 같은 바이트 opcode이므로, lambdaQL은 이를 자동 언박싱으로 보고 박싱 값을 바인딩합니다. `null`이면 일치하는 행이 없고 `LQL-4045`를 `DEBUG` 수준으로 기록합니다.
NULL 행을 찾으려면 `x == null`이라고 쓰십시오.

**null 가드로 필터 생략.** 같은 캡처 값을 null 검사하면, 값이 null일 때 그 조건이 필터 없이 사라집니다.
<!-- region: SelectQueryTest.java#captured-null-guard -->
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

**데이터베이스별 SQL**

<!-- sql: captured-null-guard -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
```

**데이터베이스별 SQL**

<!-- sql: captured-null-guard#2 -->
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
- 람다 표현식은 여전히 한 번만 분석합니다. 값에 따라 달라지는 것은 HQL 텍스트뿐입니다.
- 이 경우 가드의 위치는 결과에 영향을 주지 않습니다. `min == null`일 때 `o.getPrice() > min || min == null`도 필터가 없습니다. 비교 부분의 언박싱을 Java에서 실행하지 않기 때문입니다(Java라면 NullPointerException).
- 캡처 값에 메서드를 호출하는 비교(`o.getDescription().equals(p.trim())`)는 다릅니다. 가드가 뒤에 있으면 `p.trim()`을 먼저 계산하므로 `p == null`일 때 예외(`LQL-4012`)를 던집니다.
- `&&`에서는 가드가 반대로 동작합니다. `min == null`일 때 `min != null && o.getPrice() > min`은 행을 반환하지 않습니다(`where 1=0`).

<a id="365-converted-attributes-convert"></a>
### 3.6.5 변환 속성(`@Convert`)

JPA `AttributeConverter`가 붙은 속성은 Java 값으로 비교합니다. Hibernate(7.4, 이전 6.6도)는 이런 속성과 비교하는 리터럴을 변환하고, 바인드 파라미터도 변환합니다.

따라서 조건을 Java 값으로 작성해도 저장된 행을 찾습니다(H2, MariaDB, Oracle에서 확인함).

<!-- region: ConvertTest.java#convert -->
```java
// code is stored reversed ("abc" as "cba"); vip is stored as 'Y'/'N' (YesNoConverter)
SelectQuery<Coded> literal = qb.from(Coded.class).where(c -> c.getCode().equals("abc"));
assertEquals("select e0 from Coded e0 where e0.code = 'abc'", literal.toHql().hql());
assertEquals(List.of("abc"), codes(literal.toList()));       // Hibernate converts the literal
```

예제 코드: [`ManualJavaWhereTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaWhereTest.java)(`boxed-equals`, `null-check`, `equals-forms`), [`SelectQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java)(`null-safe`, `never-null`, `captured-null-guard`), [`SqlMeaningTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlMeaningTest.java)(`sql-meaning`), [`ConvertTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ConvertTest.java)(`convert`).

<a id="37-boolean-columns"></a>
## 3.7 boolean 컬럼

예제 엔티티에서 `active`는 기본형 `boolean`입니다(getter `isActive()`).
`getActive()`가 나오는 행은 nullable `Boolean` 컬럼의 경우입니다.

| Java | HQL | NULL 행 |
|---|---|---|
| `o.isActive()` | `e0.active = true` | –(기본형 `boolean`은 NULL이 될 수 없음) |
| `!o.isActive()` | `e0.active = false` | –(기본형 `boolean`은 NULL이 될 수 없음) |
| `Boolean.TRUE.equals(o.getActive())`(nullable `Boolean`) | `e0.active = :p1`(`:p1`은 `true`) | 반환 안 됨 |
| `!Boolean.TRUE.equals(o.getActive())` | `e0.active <> :p1` | **반환 안 됨**(SQL 의미. Java라면 반환) |
| `!o.getActive()`(nullable `Boolean`) | `e0.active = false` | **반환 안 됨**(SQL 의미: NULL은 false가 아님) |

<!-- region: SelectQueryTest.java#boolean -->
```java
assertEquals("select e0 from Order e0 where e0.active = true",
        qb.from(Order.class).where(o -> o.isActive()).toHql().hql());
assertEquals("select e0 from Order e0 where e0.active = false",       // primitive boolean: never NULL
        qb.from(Order.class).where(o -> !o.isActive()).toHql().hql());
```

**데이터베이스별 SQL**

<!-- sql: boolean -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.active=true
```

**데이터베이스별 SQL**

<!-- sql: boolean#2 -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.active=false
```

Hibernate 7.4는 세 DB 모두 SQL에 `true` / `false`를 씁니다. Hibernate 6.6은 MariaDB와 Oracle에서 `1` / `0`을 썼습니다.

참고:

- nullable `Boolean` 컬럼에 대한 `!o.getActive()`는 Java에서 언박싱 호출입니다(NULL이면 NullPointerException). lambdaQL은 SQL 의미를 사용합니다. NULL 행은 반환되지 않습니다.
- lambdaQL은 `is not true`나 `coalesce(active, false)`를 사용하지 않습니다. Oracle에서 실패합니다(ORA-00932).
- 더 큰 조건의 부정은 항상 `not (...)`로 씁니다. lambdaQL은 조건을 `(...) = false`나 `(...) is null`로 감싸지 않습니다. Oracle 23이 `WHERE`에서 이를 잘못 평가하기 때문입니다.

**boolean 컬럼과 조건의 비교**(`o.isActive() == (o.getStatus() > s)`)에서 조건 쪽은 `case when … then true else false end`가 됩니다.
이 `case`는 행마다 계산되므로 데이터베이스가 `status` 인덱스를 쓰지 못합니다(`WARN` 수준 `LQL-5013`).
인덱스가 필요하면 두 경우를 `&&`와 `||`로 나누어 쓰십시오. 이 형태는 `AND`/`OR`로 쓰입니다.

<!-- region: ManualJavaWhereTest.java#boolean-compare -->
```java
int s = 1;
// a boolean column compared with a condition: the condition becomes case when
SelectQuery<Order> compared = qb.from(Order.class).where(o -> o.isActive() == (o.getStatus() > s));
assertEquals("select e0 from Order e0 where e0.active = case when e0.status > :p1 then true else false end",
        compared.toHql().hql());
// the same rows with && and ||: plain comparisons that an index can serve
SelectQuery<Order> query = qb.from(Order.class)
        .where(o -> (o.isActive() && o.getStatus() > s) || (!o.isActive() && o.getStatus() <= s));
assertEquals("select e0 from Order e0 where (e0.active = true and e0.status > :p1)"
        + " or (e0.active = false and e0.status <= :p2)", query.toHql().hql());
```

예제 코드: [`SelectQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java)(`boolean`), [`ManualJavaWhereTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaWhereTest.java)(`boolean-compare`).

<a id="38-strings"></a>
## 3.8 문자열

- 문자열에 대한 `equals`, `Objects.equals`, `==`는 **데이터베이스 콜레이션**을 사용합니다.
  - MariaDB의 `_ci` 콜레이션은 대소문자와 끝의 공백을 무시합니다.
  - Oracle은 `''`(빈 문자열)을 NULL로 저장합니다. 그래서 Oracle에서 `"".equals(o.getDescription())`는 Java와 다르게 동작합니다.
- 두 문자열에 대한 `==`는 동일성 비교가 아니라 값 비교입니다. [9.4](09-java-notes.md#94-strings)를 참고하십시오.
- 람다 표현식 안의 문자열 상수는 리터럴로 씁니다. 작은따옴표는 escape됩니다(`''`).

<!-- region: SelectQueryTest.java#string-literal -->
```java
RenderedQuery q = qb.from(Order.class).where(o -> o.getDescription().equals("O'Brien")).toHql();
assertEquals("select e0 from Order e0 where e0.description = 'O''Brien'", q.hql());
```

**데이터베이스별 SQL**

<!-- sql: string-literal -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.description='O''Brien'
```

**`startsWith`, `endsWith`, `contains`**는 `like`가 됩니다. 검색어 안의 `%`와 `_`는 와일드카드가 아니라 글자 그대로 찾습니다.

<!-- region: LikeTest.java#like -->
```java
// % and _ in the text are not wildcards: only "50%_off sale" matches, not "50x off"
SelectQuery<Order> q = qb.from(Order.class).where(o -> o.getDescription().startsWith(p));
assertEquals("select e0 from Order e0 where e0.description like :p1 escape '!'", q.toHql().hql());
assertEquals("50!%!_%", q.toHql().parameters().get("p1"));
// a constant is written in the HQL
SelectQuery<Order> ends = qb.from(Order.class).where(o -> o.getDescription().endsWith("all"));
assertEquals("select e0 from Order e0 where e0.description like '%all' escape '!'", ends.toHql().hql());
```

- 상수는 HQL에 그대로 쓰고, 캡처 값은 바인드 파라미터가 됩니다.
- `endsWith`와 `contains`는 패턴 앞이 `%`이므로 컬럼의 일반 인덱스를 쓰지 못합니다.
- MariaDB와 MySQL은 기본 콜레이션에서 `like`가 대소문자를 구분하지 않습니다. 그래서 Java의 `startsWith`와 결과가 다를 수 있습니다.
- 검색어로 쓴 캡처 값이 `null`이면 오류입니다(`LQL-4047`).
- `trim()`, `toUpperCase()` 같은 다른 문자열 메서드는 지원하지 않습니다(`LQL-3008`).
- 람다 표현식 안의 문자열 `+`는 거부됩니다(`LQL-2007`). 캡처 값만 이어 붙이는 경우도 마찬가지입니다. 문자열은 람다 표현식 전에 만드십시오.

<!-- region: ManualJavaWhereTest.java#rejected-forms -->
```java
// a String method on an entity value other than startsWith, endsWith and contains
assertEquals("LQL-3008", assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(o -> o.getDescription().trim().equals("a"))).code());
// string concatenation inside the lambda, even of captured values only
assertEquals("LQL-2007", assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(o -> o.getDescription().equals("A" + code))).code());
// an entity compared with an entity: compare the ids
assertEquals("LQL-3011", assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(o -> o.getCustomer() == c)).code());
assertEquals("select e0 from Order e0 where e0.customer.id = :p1",
        qb.from(Order.class).where(o -> o.getCustomer().getId() == c.getId()).toHql().hql());
// operators without short-circuit
assertEquals("LQL-2013", assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(o -> o.isActive() & o.getStatus() == 1)).code());
```

예제 코드: [`SelectQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java)(`string-literal`), [`ManualJavaWhereTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaWhereTest.java)(`rejected-forms`), [`LikeTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/LikeTest.java)(`like`).

<a id="39-in-with-a-captured-collection"></a>
## 3.9 캡처한 컬렉션을 사용한 IN

컬렉션을 람다 표현식 **밖에서** 만들고 그 컬렉션의 `contains`를 호출하십시오. 컬렉션은 바인드 파라미터 하나가 됩니다.
예제 엔티티의 status는 `short`:

<!-- region: SelectQueryTest.java#in -->
```java
List<Short> statuses = List.of((short) 2, (short) 3);
RenderedQuery q = qb.from(Order.class).where(o -> statuses.contains(o.getStatus())).toHql();
assertEquals("select e0 from Order e0 where e0.status in (:p1)", q.hql());
```

**데이터베이스별 SQL**

<!-- sql: in -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status in (?,?)
```

`!contains`는 `not in`이 됩니다. NULL이 될 수 있는 컬럼도 단순한 `not in`입니다(SQL 의미).
값이 NULL인 행은 `not in`에 일치하지 않으며, lambdaQL은 `LQL-5009`를 `DEBUG` 수준으로 기록합니다. Java의 `!contains`는 NULL 값에 대해 true이므로, 이런 행을 유지하려면 `o.getDescription() == null || !descs.contains(o.getDescription())`처럼 null 검사를 작성하십시오.
<!-- region: SelectQueryTest.java#not-in -->
```java
List<Long> ids = List.of(1L, 2L);
// the id can never be NULL: a plain NOT IN
assertEquals("select e0 from Order e0 where e0.id not in (:p1)",
        qb.from(Order.class).where(o -> !ids.contains(o.getId())).toHql().hql());
// a column that can be NULL: the SQL meaning, a NULL value is not "not in" the list (DEBUG LQL-5009);
// to keep those rows, write o.getDescription() == null || !descs.contains(o.getDescription())
List<String> descs = Arrays.asList("a", "b");
assertEquals("select e0 from Order e0 where e0.description not in (:p1)",
        qb.from(Order.class).where(o -> !descs.contains(o.getDescription())).toHql().hql());
```

**데이터베이스별 SQL**

<!-- sql: not-in -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.id not in (?,?)
```

**데이터베이스별 SQL**

<!-- sql: not-in#2 -->
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
| 요소 1,500개 | H2, MariaDB, Oracle 23ai에서 문장 하나로 동작함(이전 Oracle 버전에는 1,000개 제한이 있음. 이전 버전에서는 미확인) |
| 수신 객체가 엔티티 컬렉션: `c.getOrders().contains(order)` | **거부됨**. `exists` 서브쿼리를 사용하십시오([5. 서브쿼리](05-subquery.md)) |
| 람다 표현식 안에서 만든 컬렉션: `List.of(1, 2).contains(o.getStatus())` | **거부됨**. 밖에서 만들어 캡처하십시오 |

예제 코드: [`SelectQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java)(`in`, `not-in`).

<a id="310-association-paths"></a>
## 3.10 연관 경로

lambdaQL은 `o.getCustomer().getName()`을 **호출하지 않습니다**. 이 식은 "조인한 고객의 `name` 컬럼"을 가리킵니다.
lambdaQL은 JPA Metamodel로 각 단계를 확인합니다.

| 경로 단계 | HQL | 예 |
|---|---|---|
| to-one 연관(`@ManyToOne`, `@OneToOne`) | 별칭이 붙은 `join` 또는 `left join`(아래 참고) | `o.getCustomer().getName()` -> `join e0.customer j1` ... `j1.name` |
| 임베디드(`@Embedded`) | 같은 테이블, 조인 없음. 값 객체 전체의 같음 비교는 컬럼마다 펼침(아래 참고) | `o.getAddress().getCity()` -> `e0.address.city` |
| 대상의 `@Id`로 끝나는 경로 | 외래 키 컬럼, 조인 없음 | `o.getCustomer().getId()` -> `e0.customer.id` |
| 연관 자체를 `null`과 비교 | 외래 키 컬럼, 조인 없음 | `o.getCustomer() == null` -> `e0.customer is null` |
| 컬렉션(`@OneToMany`, `@ManyToMany`) | **거부됨** | `c.getOrders()...` -> `exists` 서브쿼리 사용 |

**값 객체 전체의 비교.** 임베디드 값 객체(`@Embedded`) 전체를 같음으로 비교하면 lambdaQL은 컬럼마다 비교 하나로 펼칩니다.
`o.getAddress().equals(addr)`, `Objects.equals(o.getAddress(), addr)`, `addr.equals(o.getAddress())`는 모두 `e0.address.city = :p1 and e0.address.zip = :p2`가 됩니다.

- 부정(`!o.getAddress().equals(addr)`)은 `e0.address.city <> :p1 or e0.address.zip <> :p2`입니다.
- `o.getAddress() == null`은 `e0.address.city is null and e0.address.zip is null`이고, `!= null`은 `e0.address.city is not null or e0.address.zip is not null`입니다.
- 각 컬럼은 SQL 의미를 따릅니다. NULL 컬럼은 `=`에도 `<>`에도 일치하지 않습니다. 예를 들어 (Seoul, NULL) 행은 같음 비교에도 부정에도 일치하지 않습니다(세 DB에서 실측).
- 캡처한 값 객체의 각 속성은 쿼리가 실행될 때 읽어 바인딩합니다. 캡처한 객체가 `null`이면 모든 컬럼에 NULL을 바인딩하므로 일치하는 행이 없고, 컬럼마다 `LQL-4045`를 `WARN` 수준으로 기록합니다.
- 중첩된 값 객체도 펼칩니다.
- 크기 비교(`<` 등)와 다른 타입과의 비교는 여전히 `LQL-3012` 오류입니다. 필드를 비교하십시오.

<!-- region: MappingRulesTest.java#embedded-compare -->
```java
// address is @Embedded (city, zip): comparing the whole value object compares each column
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

`where`에서 연관 경로의 조인 종류는 조건 전체로 정합니다.
고객이 없는 주문이 조건에 맞을 수 있으면 `left join`, 맞을 수 없으면 **inner join**입니다.
결과는 같은 람다 표현식을 주문 리스트에 실행한 결과와 같습니다.
`o.getCustomer().getName()`을 거친 비교는 고객이 있는 주문에서만 참입니다. 아래 조건은 그 비교 하나뿐이므로 inner join입니다.
<!-- region: SelectQueryTest.java#inner-join -->
```java
String name = "Kim";
RenderedQuery q = qb.from(Order.class).where(o -> o.getCustomer().getName().equals(name)).toHql();
assertEquals("select e0 from Order e0 join e0.customer j1 where j1.name = :p1", q.hql());
```

**데이터베이스별 SQL**

<!-- sql: inner-join -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name=?
```

- **고객이 없는 주문**은 결과에 없습니다. 조건에 맞을 수 없기 때문입니다.
- lambdaQL은 null 검사를 대신 추가하지 않습니다. 경로 앞에 `o.getCustomer() != null &&`를 쓸 필요가 없습니다.

**null 가드는 고객이 없는 주문을 남깁니다.** `o.getCustomer() == null || ...`은 고객이 없는 주문도 조건에 맞으므로 `left join`이 됩니다.
`o.getCustomer() != null && ...`은 고객이 없는 주문이 맞을 수 없으므로 inner join입니다.
<!-- region: SelectQueryTest.java#null-guard -->
```java
String name = "Kim";
// the null guard keeps orders without a customer, as in Java: a left join
RenderedQuery q = qb.from(Order.class)
        .where(o -> o.getCustomer() == null || o.getCustomer().getName().equals(name))
        .toHql();
assertEquals("select e0 from Order e0 left join e0.customer j1 where e0.customer is null or j1.name = :p1", q.hql());

// no order without a customer can match: an inner join
RenderedQuery q2 = qb.from(Order.class)
        .where(o -> o.getCustomer() != null && o.getCustomer().getName().equals(name))
        .toHql();
assertEquals("select e0 from Order e0 join e0.customer j1 where e0.customer is not null and j1.name = :p1", q2.hql());
```

**데이터베이스별 SQL**

<!-- sql: null-guard -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.id is null or c1_0.name=?
```

**데이터베이스별 SQL**

<!-- sql: null-guard#2 -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.id is not null and c1_0.name=?
```

Hibernate는 `e0.customer is null`을 `c1_0.id is null`로 씁니다.

- `||`의 다른 부분도 마찬가지입니다. `o.getStatus() == 1 || o.getCustomer().getName().equals(name)`은 status가 1이면 고객이 없어도 맞으므로 `left join`이고, 그런 주문도 반환됩니다.
- NULL 컬럼에서 참이 될 수 있는 비교(`is null`, `coalesce`)가 경로를 거치면, lambdaQL은 그 비교에만 `e0.customer is not null`을 더합니다. 그래서 이 비교도 고객이 있는 주문에서만 참입니다.
  `o.getCustomer() == null || o.getCustomer().getName() == null`은 `left join ... where e0.customer is null or (e0.customer is not null and j1.name is null)`이 됩니다.
- 값과의 비교는 SQL 의미를 따릅니다. `!o.getCustomer().getName().equals(name)`은 고객이 없는 주문을 반환하지 않습니다.
- `optional = false`로 매핑한 필수 연관은 항상 inner join입니다.

고객이 **없는** 주문을 찾으려면 연관 자체를 비교하십시오. 조인 없이 외래 키 컬럼을 검사합니다.
<!-- region: SelectQueryTest.java#no-association -->
```java
RenderedQuery q = qb.from(Order.class).where(o -> o.getCustomer() == null).toHql();
assertEquals("select e0 from Order e0 where e0.customer is null", q.hql());
```

**데이터베이스별 SQL**

<!-- sql: no-association -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id is null
```

연관 엔티티의 `@Id`로 끝나는 경로는 외래 키 컬럼입니다. 조인이 필요 없습니다.
<!-- region: SelectQueryTest.java#fk-path -->
```java
RenderedQuery q = qb.from(Order.class).where(o -> o.getCustomer().getId() == 1).toHql();
assertEquals("select e0 from Order e0 where e0.customer.id = 1", q.hql());
```

**데이터베이스별 SQL**

<!-- sql: fk-path -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id=1
```

추가 사항:

- 같은 경로는 별칭 하나를 받습니다. 이 별칭은 `where`, `orderBy`, `select`가 함께 사용합니다.
- `orderBy`나 `select`에**만** 쓰인 경로는 `left join`이므로 그곳에서는 행을 잃지 않습니다. 같은 경로를 `where`에서도 쓰면 `orderBy`와 `select`는 `where`의 조인을 사용합니다.
- 두 엔티티의 비교(`o.getCustomer() == c`)는 거부됩니다(`LQL-3011`). id를 비교하십시오: `o.getCustomer().getId() == c.getId()`(여기서 `c`는 캡처한 객체, [3.8](#38-strings)의 `rejected-forms` 예제 참고).

예제 코드: [`MappingRulesTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/MappingRulesTest.java)(`embedded-compare`), [`SelectQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java)(`inner-join`, `null-guard`, `no-association`, `fk-path`).

<a id="311-captured-values"></a>
## 3.11 캡처 값

람다 표현식은 밖의 값을 사용할 수 있습니다. 지역 변수, 필드, 캡처한 객체의 메서드 호출입니다.

<!-- region: ManualJavaWhereTest.java#captured-forms -->
```java
LocalDateTime since = LocalDateTime.of(2026, 1, 1, 0, 0);
Filter filter = new Filter(60.0);
Form form = new Form(" a ");
SelectQuery<Order> query = qb.from(Order.class)
        .where(o -> o.getUpdateAt().compareTo(since) > 0)            // a local variable
        .where(o -> o.getStatus() == this.defaultStatus)             // a field of this
        .where(o -> o.getPrice() > filter.minPrice())                // a method of a captured object
        .where(o -> o.getDescription().equals(form.text().trim()));  // computed in Java
assertEquals("select e0 from Order e0 where e0.updateAt > :p1 and e0.status = :p2"
        + " and e0.price > :p3 and e0.description = :p4", query.toHql().hql());
```

규칙:

- 엔티티를 사용하지 않는 부분은 **쿼리가 실행될 때마다** Java에서 계산합니다. 그 결과가 바인드 파라미터가 됩니다.
- 컴파일 시점 상수(`static final int MAX = 5;`)는 캡처 값이 아닙니다. javac가 람다 표현식 안에 값을 복사하므로 `5`처럼 리터럴이 됩니다([3.3](#33-basic-form) 참고).
- 람다 표현식은 한 번 분석되고 결과는 캐시됩니다. 새 캡처 값은 호출할 때마다 읽습니다.
- 엔티티 부분만 SQL이 됩니다. `form.text().trim()`은 Java에서 계산합니다. 엔티티 값에 대한 `o.getDescription().trim()`은 거부됩니다(`LQL-3008`).
- Java에서 캡처한 지역 변수는 사실상 final이어야 합니다. 값이 바뀐다면 람다 표현식 전에 새 변수에 계산해 두십시오.
- 필드를 사용하는 람다 표현식은 `this`를 캡처합니다. [9.8](09-java-notes.md#98-lambdas-in-instance-methods-capture-this)을 참고하십시오.

예제 코드: [`ManualJavaWhereTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaWhereTest.java)(`captured-forms`).

<a id="312-errors-that-depend-on-order-of-evaluation"></a>
## 3.12 평가 순서에 따라 달라지는 오류

SQL은 `AND`/`OR`를 왼쪽에서 오른쪽으로 평가한다고 약속하지 않습니다. 그래서 다음과 같은 "가드"는 SQL에서 안전하지 않습니다.
lambdaQL은 가드를 쓴 그대로 HQL에 옮깁니다. 데이터베이스가 나눗셈을 먼저 계산하면 `qty`가 0인 행에서 0으로 나누기 오류가 날 수 있습니다.

<!-- region: ManualJavaWhereTest.java#guard -->
```java
// the guard is written as it is; SQL does not promise to check "qty <> 0" before the division
assertEquals("select e0 from Item e0 where e0.qty <> 0 and (e0.price / e0.qty) > 2.0",
        qb.from(Item.class).where(i -> i.getQty() != 0 && i.getPrice() / i.getQty() > 2).toHql().hql());
// compare without the division
SelectQuery<Item> query = qb.from(Item.class).where(i -> i.getQty() > 0 && i.getPrice() > 2 * i.getQty());
assertEquals("select e0 from Item e0 where e0.qty > 0 and e0.price > (2.0 * e0.qty)", query.toHql().hql());
```

가드에 기대지 말고 나눗셈 없이 비교하십시오: `i.getQty() > 0 && i.getPrice() > 2 * i.getQty()`.

예제 코드: [`ManualJavaWhereTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaWhereTest.java)(`guard`).

<a id="313-what-is-rejected"></a>
## 3.13 거부되는 것

lambdaQL은 분석에 실패하면 코드 `[LQL-xxxx]`와 소스 줄 번호가 담긴 예외를 던집니다.
대부분의 모양은 람다 표현식을 분석할 때(처음 사용할 때) 발견됩니다. 잘못된 캡처 값(메서드를 호출한 `null`, NaN, `null` 요소가 있는 컬렉션)은 쿼리가 실행될 때 발견됩니다.
[11. 오류와 언어](../11-errors-and-languages.md)를 참고하십시오.

| 형태 | 예 | 해결 방법 |
|---|---|---|
| 블록 람다 표현식 안의 반복문, `switch`, 지역 변수 | `o -> { int x = 1; return ...; }` | 식 하나로 작성 |
| 일반 boolean 삼항 연산자 | `c ? x : y` | [3.5](#35-and-or-not)의 허용된 형태 사용 |
| 단락 평가를 하지 않는 연산자(`LQL-2013`) | `a & b`, `a \| b`, `a ^ b` | `&&`, `\|\|` 사용 |
| 문자열 `+`(`LQL-2007`) | `o.getDescription().equals("A" + code)` | 람다 표현식 전에 문자열을 만듦 |
| 람다 표현식 안의 람다 표현식이나 스트림 | `o -> list.stream().anyMatch(...)` | `contains`([3.9](#39-in-with-a-captured-collection))나 서브쿼리 사용 |
| 엔티티 값에 대한 문자열 메서드(`LQL-3008`) | `o.getDescription().trim()` (`startsWith`, `endsWith`, `contains`는 지원, [3.8](#38-strings)) | 지원하지 않음 |
| 엔티티의 다른 메서드 | `o.calcSomething()` | getter만 사용 |
| 엔티티 컬렉션 경로 | `c.getOrders().size() > 0`(`Customer.orders`) | `exists` 서브쿼리 |
| 엔티티와 엔티티 비교(`LQL-3011`) | `o.getCustomer() == c` | id 비교 |
| 값 객체의 크기 비교, 다른 타입과의 비교(`LQL-3012`) | `o.getAddress().compareTo(a) > 0`(`Address`가 `Comparable`일 때) | 필드를 비교. 같음 비교는 지원 |
| 메서드를 호출하는 null 캡처 값 | `p == null`일 때 `o.getDescription().equals(p.trim())`, `o.getStatus() > p.intValue()`(`Double p`) | 앞에 null 가드(`p == null \|\| ...`)를 쓰거나 쿼리 전에 `p`를 확인 |
| `NaN` / `Infinity` 캡처 값 | `o.getPrice() > Double.NaN` | NaN을 보내지 않음 |
| 익명 클래스, `Serializable`이 아닌 람다 표현식 | `new Where<Order>() { ... }`, `java.util.function.Predicate` | 람다 표현식 사용 |

예외 타입(모두 `LambdaQLException`을 상속):

| 예외 | 발생 시점 |
|---|---|
| `LambdaAnalysisException` | 람다 표현식을 읽을 수 없을 때(직렬화 가능한 람다 표현식이 아닌 경우 등) |
| `UnsupportedLambdaShapeException` | 지원하지 않는 모양일 때(반복문, `switch`, ...) |
| `TranslationException` | 의미를 번역할 수 없을 때(알 수 없는 메서드, 컬렉션 경로, ...) |

Java 전용 세부 사항은 [9. Java 노트](09-java-notes.md)에 있습니다. 모든 규칙을 한곳에서 보려면: [10. 규칙과 제한](10-rules-and-limits.md).

---

[Java 트랙](README.md) | 이전: [2. 시작하기](02-getting-started.md) | 다음: [4. 정렬과 페이징](04-order-paging.md) | [이 페이지의 Kotlin 버전](../kotlin/03-where.md)
