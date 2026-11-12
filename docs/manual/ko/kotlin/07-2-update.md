<a id="72-update"></a>
# 7.2 수정 (Kotlin)

> [!NOTE]
> **상태: 설계됨, 아직 구현되지 않음.**
> 이 장은 확정된 설계입니다(결정 D28, 마일스톤 M10).
> Kotlin 문법은 컴파일되며(kotlinc 2.3.21), SQL은 Hibernate 6.6.5와 함께 H2, MariaDB, Oracle에서 확인했습니다.
> 지금 lambdaQL에서는 동작하지 **않습니다**. 현재는 분석 코어와 `where` + `toList()`만 동작합니다
> (H2, MariaDB, Oracle에서 테스트함). 전체 상태는 [트랙 README](README.md)를 참고하십시오.

[7. 수정, 삽입, 삭제 (Kotlin)](07-dml.md) | **7.2 수정** | [7.4 삭제](07-4-delete.md) | [7.5 삽입](07-5-insert.md) | [Java](../java/07-2-update.md)

---


<a id="basic-form"></a>
## 기본 형태

```kotlin
val n = qb.update(Order::class.java)
    .set { it.status = st; it.price = it.price!! * 1.1 }
    .where { it.status == 1 }
    .execute()                     // 수정된 행 수
```

HQL:

```sql
update Order o set o.status = :p1, o.price = o.price * 1.1 where o.status = 1
```

**SQL 블록 읽는 법.** `…`는 줄여 쓴 select 목록(엔티티의 모든 컬럼)이고, `?`는 바인드 파라미터입니다. [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java)는 이 페이지의 HQL을 Hibernate에 직접 넘기고, H2 2.3, MariaDB 10.11, Oracle 23.26(Hibernate 6.6.5)에서 나온 SQL을 기록합니다. lambdaQL은 아직 이 쿼리들을 만들지 않습니다. Hibernate의 SQL이 바뀌면 테스트가 실패합니다.

**데이터베이스별 SQL (예정)** — 예정된 HQL을 Hibernate가 번역한 것으로, `PlannedSqlTest`가 기록했습니다. lambdaQL은 아직 이 쿼리를 만들지 않습니다. `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)의 예제 `07-update`:

```sql
-- H2, MariaDB
update orders o1_0
set status=?,price=(o1_0.price*1.1)
where o1_0.status=1

-- Oracle
update orders o1_0
set o1_0.status=?,o1_0.price=(o1_0.price*1.1)
where o1_0.status=1
```

Oracle은 `set`에 별칭을 씁니다(`set o1_0.status=?`). H2와 MariaDB는 쓰지 않습니다.

`st`는 캡처 값입니다. lambdaQL은 이를 바인드 파라미터로 보냅니다. `!!`는 무시됩니다(D23).

<a id="values-null-date-boolean"></a>
## 값: null, 날짜, boolean

```kotlin
qb.update(Order::class.java)
    .set { it.description = null; it.updateAt = now; it.active = true }
    .where { it.id == id }
    .execute()
```

enum 값도 같은 방식으로 동작합니다. 캡처 값은 같은 값을 두 번 사용하더라도 각각 **자체 파라미터**를 받습니다(규칙 U7).

<a id="add-to-a-value-default-for-null"></a>
## 값에 더하기, NULL의 기본값

```kotlin
qb.update(Order::class.java).set { it.cnt += 1 }.where { it.status == 1 }.execute()
// → set o.cnt = o.cnt + 1

qb.update(Order::class.java)
    .set { it.price = (it.price ?: 0.0) + 1 }
    .all()
    .execute()
// → set o.price = coalesce(o.price, 0.0) + 1.0
```

Kotlin에서 `+=`에는 non-null 속성(`cnt: Int`)이 필요합니다. nullable 속성이라면 `it.total = it.total!! + 1`이라고 쓰거나 위처럼 `?:`를 사용하십시오.

<a id="embedded-fields"></a>
## 임베디드 필드

```kotlin
qb.update(Order::class.java).set { it.address.city = city }.where { it.id == id }.execute()
// → set o.address.city = :p1

qb.update(Order::class.java).set { it.address = addr }.where { it.id == id }.execute()
// → 임베디드 값의 모든 컬럼(city, zip)을 설정
```

**데이터베이스별 SQL (예정)** — 예정된 HQL을 Hibernate가 번역한 것으로, `PlannedSqlTest`가 기록했습니다. lambdaQL은 아직 이 쿼리를 만들지 않습니다. `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)의 예제 `07-update-embedded`:

```sql
-- H2, MariaDB
update orders o1_0
set city=?
where o1_0.id=?

-- Oracle
update orders o1_0
set o1_0.city=?
where o1_0.id=?
```

<a id="change-an-association-foreign-key"></a>
## 연관 변경(외래 키)

```kotlin
qb.update(Order::class.java).set { it.customer?.id = cid }.where { it.id == id }.execute()
// → set o.customer.id = :p1      (FK 컬럼. ?. 대입은 항상 대입함)

qb.update(Order::class.java).set { it.customer = null }.where { it.id == id }.execute()
// → set o.customer = null

qb.update(Order::class.java).set { it.customer = customer }.where { it.id == id }.execute()
// → 캡처한 customer의 @Id를 바인드(규칙 U11)
```

**데이터베이스별 SQL (예정)** — 예정된 HQL을 Hibernate가 번역한 것으로, `PlannedSqlTest`가 기록했습니다. lambdaQL은 아직 이 쿼리를 만들지 않습니다. `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)의 예제 `07-update-fk`:

```sql
-- H2, MariaDB
update orders o1_0
set customer_id=?
where o1_0.id=?

-- Oracle
update orders o1_0
set o1_0.customer_id=?
where o1_0.id=?
```

**데이터베이스별 SQL (예정)** — 예정된 HQL을 Hibernate가 번역한 것으로, `PlannedSqlTest`가 기록했습니다. lambdaQL은 아직 이 쿼리를 만들지 않습니다. `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)의 예제 `07-update-fk-null`:

```sql
-- H2, MariaDB
update orders o1_0
set customer_id=null
where o1_0.id=?

-- Oracle
update orders o1_0
set o1_0.customer_id=null
where o1_0.id=?
```

연관 엔티티에서는 `@Id`(FK)만 설정할 수 있습니다.
`it.customer?.name = x`는 오류입니다. 다른 테이블을 바꾸게 되기 때문입니다.

<a id="a-value-from-another-table"></a>
## 다른 테이블의 값

연관 경로로 값을 읽으십시오. lambdaQL은 이를 **상관 스칼라 서브쿼리**로 출력합니다(규칙 U9).

```kotlin
qb.update(Order::class.java).set { it.custName = it.customer?.name }.all().execute()
```

```sql
update Order o
set o.custName = (select c.name from Customer c where c.id = o.customer.id)
```

**데이터베이스별 SQL (예정)** — 예정된 HQL을 Hibernate가 번역한 것으로, `PlannedSqlTest`가 기록했습니다. lambdaQL은 아직 이 쿼리를 만들지 않습니다. `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)의 예제 `07-update-other-table`:

```sql
-- H2, MariaDB
update orders o1_0
set cust_name=(select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id)

-- Oracle
update orders o1_0
set o1_0.cust_name=(select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id)
```

주문에 고객이 없으면 `custName`은 NULL이 됩니다.

<a id="an-aggregate-from-a-subquery"></a>
## 서브쿼리의 집계

인자가 둘인 람다를 사용하십시오. 두 번째 인자는 컨텍스트 객체(`sub`)입니다.
여기서는 `Agg`가 필요 없습니다. 서브쿼리에는 [5. 서브쿼리](05-subquery.md)처럼 `sum`, `avg`, ...가 있습니다.

```kotlin
qb.update(Order::class.java)
    .set { o, sub ->
        o.total = sub.from(Item::class.java).where { i -> i.order?.id == o.id }.sum { i -> i.price }
    }
    .where { it.status == 1 }
    .execute()
```

주문에 품목이 없으면 `total`은 NULL이 됩니다. SQL 동작입니다.

인자가 하나인 후행 람다(`set { it... }`)와 인자가 둘인 후행 람다(`set { o, sub -> ... }`)는 서로 다른 오버로드입니다. Kotlin은 알맞은 것을 고릅니다.

<a id="where-with-paths-and-subqueries"></a>
## 경로와 서브쿼리를 사용한 where

`where`는 조회 쿼리와 같습니다.

```kotlin
qb.update(Order::class.java).set { it.status = 0 }.where { it.customer?.grade!! > 3 }.execute()

qb.update(Order::class.java).set { it.status = 9 }
    .where { o, sub -> !sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
    .execute()
```

<a id="optimistic-locking-version"></a>
## 낙관적 잠금(`@Version`)

엔티티에 `@Version` 속성이 있으면 lambdaQL은 `update versioned`를 출력합니다. 데이터베이스가 버전에 1을 더합니다.

```kotlin
val n = qb.update(VOrder::class.java)
    .set { it.status = 8 }
    .where { it.id == id && it.version == v }
    .execute()
if (n == 0) throw OptimisticLockException()   // 결과 확인은 사용자가 할 일
```

`@Version` 속성에 직접 대입하지 마십시오. 오류입니다(규칙 U15).

<a id="many-set-calls-patch"></a>
## 여러 번의 `set()` 호출(PATCH)

`set()`은 여러 번 호출할 수 있습니다. 모든 대입은 호출 순서대로 SET 목록 하나로 합쳐집니다(규칙 U14).

```kotlin
var u = qb.update(Order::class.java).set { it.status = dto.status }
if (dto.description != null) {
    u = u.set { it.description = dto.description }
}
u.where { it.id == id }.execute()
```

<a id="update-all-rows-all"></a>
## 모든 행 수정: `all()`

`where`가 없는 update는 `all()`을 명시해야 합니다. `where`도 `all()`도 없으면 코드가 **컴파일되지 않습니다**(규칙 U5).

```kotlin
qb.update(Order::class.java).set { it.status = 0 }.all().execute()   // 허용
qb.update(Order::class.java).set { it.status = 0 }.execute()         // 컴파일 오류: execute() 없음
```

---

---

[7. 수정, 삽입, 삭제 (Kotlin)](07-dml.md) | **7.2 수정** | [7.4 삭제](07-4-delete.md) | [7.5 삽입](07-5-insert.md) | [Java](../java/07-2-update.md)
