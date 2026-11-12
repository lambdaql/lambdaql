<a id="72-update"></a>
# 7.2 수정(update) (Java)

> [!NOTE]
> **상태: 설계됨, 아직 구현되지 않음.**
> 이 장은 확정된 설계입니다(결정 D28, 마일스톤 M10).
> 문법은 Java에서 컴파일되며, SQL은 Hibernate 6.6.5로 H2, MariaDB, Oracle에서 확인했습니다.
> 지금의 lambdaQL에서는 동작하지 **않습니다**. 현재는 분석 코어와 `where` + `toList()`만 동작합니다
> (H2, MariaDB, Oracle에서 테스트함). 전체 상태는 [Java 트랙 README](README.md#status)를 참고하십시오.

[7. 수정, 삽입, 삭제 (Java)](07-dml.md) | **7.2 수정(update)** | [7.4 삭제(delete)](07-4-delete.md) | [7.5 삽입(insert)](07-5-insert.md) | [Kotlin](../kotlin/07-2-update.md)

---


<a id="basic-form"></a>
## 기본 형태

```java
int n = qb.update(Order.class)
    .set(o -> { o.setStatus(st); o.setPrice(o.getPrice() * 1.1); })
    .where(o -> o.getStatus() == 1)
    .execute();                    // 수정된 행 수
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

`st`는 캡처한 변수입니다. lambdaQL은 이를 바인드 파라미터로 보냅니다.

<a id="values-null-date-boolean"></a>
## 값: null, 날짜, boolean

```java
qb.update(Order.class)
  .set(o -> { o.setDescription(null); o.setUpdateAt(now); o.setActive(true); })
  .where(o -> o.getId() == id)
  .execute();
```

enum 값도 같은 방식으로 동작합니다. 같은 값을 두 번 사용하더라도 각 캡처 값은 **자기 파라미터**를 받습니다(규칙 U7).

<a id="add-to-a-value-default-for-null"></a>
## 값에 더하기, NULL의 기본값

```java
qb.update(Order.class).set(o -> o.setCnt(o.getCnt() + 1)).where(o -> o.getStatus() == 1).execute();

qb.update(Order.class)
  .set(o -> o.setPrice(Objects.requireNonNullElse(o.getPrice(), 0.0) + 1))
  .all()
  .execute();
// → set o.price = coalesce(o.price, 0.0) + 1
```

**데이터베이스별 SQL (예정)** — 예정된 HQL을 Hibernate가 번역한 것으로, `PlannedSqlTest`가 기록했습니다. lambdaQL은 아직 이 쿼리를 만들지 않습니다. `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)의 예제 `07-update-coalesce`:

```sql
-- H2, MariaDB
update orders o1_0
set price=(coalesce(o1_0.price,0.0)+1)

-- Oracle
update orders o1_0
set o1_0.price=(coalesce(o1_0.price,0.0)+1)
```

<a id="embedded-fields"></a>
## 임베디드 필드

```java
qb.update(Order.class).set(o -> o.getAddress().setCity(city)).where(o -> o.getId() == id).execute();
// → set o.address.city = :p1

qb.update(Order.class).set(o -> o.setAddress(addr)).where(o -> o.getId() == id).execute();
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

```java
qb.update(Order.class).set(o -> o.getCustomer().setId(cid)).where(o -> o.getId() == id).execute();
// → set o.customer.id = :p1      (FK 컬럼)

qb.update(Order.class).set(o -> o.setCustomer(null)).where(o -> o.getId() == id).execute();
// → set o.customer = null

qb.update(Order.class).set(o -> o.setCustomer(customer)).where(o -> o.getId() == id).execute();
// → 캡처한 customer의 @Id를 바인딩(규칙 U11)
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
`o.getCustomer().setName(x)`는 오류입니다. 다른 테이블을 바꾸게 되기 때문입니다.

<a id="a-value-from-another-table"></a>
## 다른 테이블의 값

연관 경로로 값을 읽으십시오. lambdaQL은 이를 **상관 스칼라 서브쿼리**로 렌더링합니다(규칙 U9).

```java
qb.update(Order.class).set(o -> o.setCustName(o.getCustomer().getName())).all().execute();
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

```java
qb.update(Order.class)
  .set((o, sub) -> o.setTotal(
      sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).sum(i -> i.getPrice())))
  .where(o -> o.getStatus() == 1)
  .execute();
```

주문에 품목이 없으면 `total`은 NULL이 됩니다. 이는 SQL 동작입니다.

<a id="where-with-paths-and-subqueries"></a>
## 경로와 서브쿼리를 사용한 where

`where`는 조회 쿼리와 같습니다.

```java
qb.update(Order.class).set(o -> o.setStatus(0)).where(o -> o.getCustomer().getGrade() > 3).execute();

qb.update(Order.class).set(o -> o.setStatus(9))
  .where((o, sub) -> !sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).exists())
  .execute();
```

<a id="optimistic-locking-version"></a>
## 낙관적 잠금(`@Version`)

엔티티에 `@Version` 필드가 있으면 lambdaQL은 `update versioned`를 렌더링합니다. 데이터베이스가 버전에 1을 더합니다.

```java
int n = qb.update(VOrder.class)
    .set(o -> o.setStatus(8))
    .where(o -> o.getId() == id && o.getVersion() == v)
    .execute();
if (n == 0) throw new OptimisticLockException();   // 결과 확인은 사용자의 몫
```

`@Version` 필드에 직접 값을 할당하지 마십시오. 오류가 됩니다(규칙 U15).

<a id="many-set-calls-patch"></a>
## 여러 번의 `set()` 호출(PATCH)

`set()`은 여러 번 호출할 수 있습니다. 모든 할당은 호출 순서대로 SET 목록 하나로 합쳐집니다(규칙 U14).

```java
var u = qb.update(Order.class).set(o -> o.setStatus(dto.status()));
if (dto.description() != null) {
    u = u.set(o -> o.setDescription(dto.description()));
}
u.where(o -> o.getId() == id).execute();
```

<a id="update-all-rows-all"></a>
## 모든 행 수정: `all()`

`where`가 없는 update는 `all()`을 명시해야 합니다. `where`나 `all()`이 없으면 코드가 **컴파일되지 않습니다**(규칙 U5).

```java
qb.update(Order.class).set(o -> o.setStatus(0)).all().execute();       // 허용
qb.update(Order.class).set(o -> o.setStatus(0)).execute();             // 컴파일 오류: execute() 없음
```

---

---

[7. 수정, 삽입, 삭제 (Java)](07-dml.md) | **7.2 수정(update)** | [7.4 삭제(delete)](07-4-delete.md) | [7.5 삽입(insert)](07-5-insert.md) | [Kotlin](../kotlin/07-2-update.md)
