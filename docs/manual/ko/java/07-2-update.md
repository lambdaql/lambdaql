<a id="72-update"></a>
# 7.2 수정(update) (Java)

> [!NOTE]
> **상태: 구현됨, 테스트함**(H2 2.3, MariaDB 10.11, Oracle 23.26, 마일스톤 M10). `limit`과 `executeOne()`(결정 D41)도 포함합니다.
> `where` 안의 서브쿼리(`sub`)도 구현되었습니다(마일스톤 M8). 삭제 형태는 테스트했습니다. [7.4](07-4-delete.md)를 참고하십시오.
> **아직 구현되지 않음:** `set` 값 안의 `sub` 서브쿼리, 복합 키 엔티티의 `limit` / `executeOne()`.
> **(테스트됨)** 표시가 있는 예제는 [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)에서 그대로 가져온 것입니다. 나머지 예제는 설계를 따르며 아직 테스트가 없습니다.
> 전체 상태는 [Java 트랙 README](README.md#status)를 참고하십시오.

[7. 수정, 삽입, 삭제 (Java)](07-dml.md) | **7.2 수정(update)** | [7.4 삭제(delete)](07-4-delete.md) | [7.5 삽입(insert)](07-5-insert.md) | [7.6 업서트(upsert)](07-6-upsert.md) | [Kotlin](../kotlin/07-2-update.md)

---


<a id="basic-form"></a>
## 기본 형태

**(테스트됨)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java), 영역 `update`:

```java
short st = 5;
ExecutableUpdate<Order> u = qb.update(Order.class)
        .set(o -> { o.setStatus(st); o.setPrice(o.getPrice() * 2); })
        .where(o -> o.getStatus() == 1);
// the price on the right is the value before the change
assertEquals("update Order e0 set e0.status = :p1, e0.price = (e0.price * 2.0) where e0.status = 1", u.toHql().hql());
int changed = u.execute();
```

`st`는 캡처한 변수이므로 바인드 파라미터(`:p1`)가 됩니다. 상수 `2`는 람다에 적은 값이므로 리터럴(`2.0`)로 유지됩니다(결정 D37).
`toHql()`은 문장을 실행하지 않고 HQL과 파라미터를 반환합니다. `execute()`는 문장을 실행하고 수정된 행 수를 반환합니다.

**SQL 블록 읽는 법.** `…`는 줄여 쓴 select 목록(엔티티의 모든 컬럼)이고, `?`는 바인드 파라미터입니다. 각 줄은 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)에서 나옵니다. 이 테스트는 각 쿼리를 Hibernate 6.6.5로 H2 2.3, MariaDB 10.11, Oracle 23.26에서 실행하며, Hibernate의 SQL이 바뀌면 실패합니다. **(예정)** 표시가 있는 블록은 [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java)에서 나옵니다. lambdaQL이 아직 이 문장을 만들지 않으므로, 이 테스트가 예정된 HQL을 Hibernate에 직접 넘깁니다.

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `update`:

```sql
-- H2, MariaDB
update orders o1_0
set status=?,price=(o1_0.price*2.0)
where o1_0.status=1

-- Oracle
update orders o1_0
set o1_0.status=?,o1_0.price=(o1_0.price*2.0)
where o1_0.status=1
```

Oracle은 `set`에 별칭을 씁니다(`set o1_0.status=?`). H2와 MariaDB는 쓰지 않습니다.

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

<a id="embedded-fields"></a>
## 임베디드 필드

```java
qb.update(Order.class).set(o -> o.getAddress().setCity(city)).where(o -> o.getId() == id).execute();
// → set o.address.city = :p1

qb.update(Order.class).set(o -> o.setAddress(addr)).where(o -> o.getId() == id).execute();
// → sets all columns of the embedded value (city, zip)
```

<a id="change-an-association-foreign-key"></a>
## 연관 변경(외래 키)

```java
qb.update(Order.class).set(o -> o.getCustomer().setId(cid)).where(o -> o.getId() == id).execute();
// → set o.customer.id = :p1      (the FK column)

qb.update(Order.class).set(o -> o.setCustomer(null)).where(o -> o.getId() == id).execute();
// → set o.customer = null

qb.update(Order.class).set(o -> o.setCustomer(customer)).where(o -> o.getId() == id).execute();
// → binds the @Id of the captured customer (rule U11)
```

연관 엔티티에서는 `@Id`(FK)만 설정할 수 있습니다.
`o.getCustomer().setName(x)`는 오류입니다. 다른 테이블을 변경하게 되기 때문입니다(규칙 U6, [7.8](07-dml.md#rules-checked-at-analysis-time)에서 테스트함).

<a id="a-value-from-another-table"></a>
## 다른 테이블의 값

연관 경로로 값을 읽으십시오. lambdaQL은 이를 **상관 스칼라 서브쿼리**로 렌더링합니다(규칙 U9).
`where`의 경로도 같은 형태의 서브쿼리가 됩니다.

**(테스트됨)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java), 영역 `update-path`:

```java
// a path in the condition: a subquery, plus "customer exists" (the same rows as the select, D36, U9)
ExecutableUpdate<Order> u = qb.update(Order.class)
        .set(o -> o.setCustName("K"))
        .where(o -> o.getCustomer().getName().equals("Kim"));
assertEquals("update Order e0 set e0.custName = 'K' where (select s1.name from Customer s1 where s1.id = e0.customer.id) = 'Kim'"
        + " and e0.customer.id is not null", u.toHql().hql());
// a path in a value: a subquery; NULL when there is no customer
ExecutableUpdate<Order> copy = qb.update(Order.class).set(o -> o.setCustName(o.getCustomer().getName())).all();
assertEquals("update Order e0 set e0.custName = (select s1.name from Customer s1 where s1.id = e0.customer.id)", copy.toHql().hql());
```

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `update-path`:

```sql
-- H2, MariaDB
update orders o1_0
set cust_name='K'
where (select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id)='Kim' and o1_0.customer_id is not null

-- Oracle
update orders o1_0
set o1_0.cust_name='K'
where (select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id)='Kim' and o1_0.customer_id is not null
```

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `update-path#2`:

```sql
-- H2, MariaDB
update orders o1_0
set cust_name=(select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id)

-- Oracle
update orders o1_0
set o1_0.cust_name=(select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id)
```

주문에 고객이 없으면 `custName`은 NULL이 됩니다.
`where`에서는 lambdaQL이 `e0.customer.id is not null`을 더합니다. 그래서 수정 대상 행은 `qb.from(Order.class).where(같은 람다)`의 결과와 같습니다(결정 D36). 테스트가 이를 확인합니다.

<a id="an-aggregate-from-a-subquery"></a>
## 서브쿼리의 집계

인자가 둘인 람다를 사용하십시오. 두 번째 인자는 컨텍스트 객체(`sub`)입니다.
여기서는 `Agg`가 필요 없습니다. 서브쿼리에는 [5. 서브쿼리](05-subquery.md)처럼 `sum`, `avg`, ...가 있습니다.
이 형태는 **아직 구현되지 않았습니다**. 현재는 `where` 안의 서브쿼리만 동작합니다.

```java
qb.update(Order.class)
  .set((o, sub) -> o.setTotal(
      sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).sum(i -> i.getPrice())))
  .where(o -> o.getStatus() == 1)
  .execute();
```

**데이터베이스별 SQL (예정)** — 예정된 HQL을 Hibernate가 번역한 것으로, `PlannedSqlTest`가 기록했습니다. lambdaQL은 아직 이 쿼리를 만들지 않습니다. `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)의 예제 `07-update-sum`:

```sql
-- H2, MariaDB
update orders o1_0
set total=(select sum(i1_0.price) from items i1_0 where i1_0.order_id=o1_0.id)
where o1_0.status=1

-- Oracle
update orders o1_0
set o1_0.total=(select sum(i1_0.price) from items i1_0 where i1_0.order_id=o1_0.id)
where o1_0.status=1
```

주문에 품목이 없으면 `total`은 NULL이 됩니다. 이는 SQL의 동작입니다.

<a id="where-with-paths-and-subqueries"></a>
## 경로와 서브쿼리를 사용한 where

`where`는 조회 쿼리와 같습니다.

```java
qb.update(Order.class).set(o -> o.setStatus(0)).where(o -> o.getCustomer().getGrade() > 3).execute();

qb.update(Order.class).set(o -> o.setStatus(9))
  .where((o, sub) -> !sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).exists())
  .execute();
```

두 예제 모두 현재 동작합니다.
첫 번째 예제에서는 위의 테스트된 예제처럼 경로가 스칼라 서브쿼리가 됩니다.
두 번째 예제는 [5. 서브쿼리](05-subquery.md)처럼 `sub`를 사용합니다. HQL은 `update Order e0 set e0.status = 9 where not exists (select 1 from Item s1 where s1.order.id = e0.id)`입니다.
같은 `where`를 쓰는 삭제는 테스트했습니다(영역 `dml-subquery`, [7.4](07-4-delete.md) 참고). 수정 형태에는 아직 별도 테스트가 없습니다.

**데이터베이스별 SQL (이 HQL에 대한 Hibernate의 SQL)** — [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java)가 같은 문장을 Hibernate에 직접 넘겨 기록했습니다. `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)의 예제 `07-update-not-exists`:

```sql
-- H2, MariaDB
update orders o1_0
set status=9
where not exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id)

-- Oracle
update orders o1_0
set o1_0.status=9
where not exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id)
```

<a id="optimistic-locking-version"></a>
## 낙관적 잠금(`@Version`)

엔티티에 `@Version` 필드가 있으면 lambdaQL은 `update versioned`를 렌더링합니다. 데이터베이스가 버전에 1을 더합니다.

**(테스트됨)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java), 영역 `update-versioned`:

```java
// an entity with a version field (Version annotation): the version goes up too
ExecutableUpdate<VOrder> u = qb.update(VOrder.class).set(v -> v.setStatus(2)).where(v -> v.getId() == 1L);
assertEquals("update versioned VOrder e0 set e0.status = 2 where e0.id = 1L", u.toHql().hql());
```

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `update-versioned`:

```sql
-- H2, MariaDB
update v_orders v1_0
set status=2,version=(v1_0.version+1)
where v1_0.id=1

-- Oracle
update v_orders v1_0
set v1_0.status=2,v1_0.version=(v1_0.version+1)
where v1_0.id=1
```

Hibernate는 SET 목록에 `version=(v1_0.version+1)`을 더합니다. 버전을 낙관적 잠금으로 사용하려면 `where`에 버전을 넣고 결과를 확인하십시오.

```java
int n = qb.update(VOrder.class)
    .set(o -> o.setStatus(8))
    .where(o -> o.getId() == id && o.getVersion() == v)
    .execute();
if (n == 0) throw new OptimisticLockException();   // checking the result is your job
```

`@Version` 필드에 직접 값을 할당하지 마십시오(규칙 U15).

<a id="many-set-calls-patch"></a>
## 여러 번의 `set()` 호출(PATCH)

`set()`은 여러 번 호출할 수 있습니다. 모든 할당은 호출 순서대로 SET 목록 하나로 합쳐집니다(규칙 U14).
규칙 U3과 U4는 여러 `set()` 호출 사이에도 적용됩니다.

**(테스트됨)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java), 영역 `update-patch`:

```java
// PATCH: assign only the fields that have a value; every set() adds assignments
String memo = "checked";
Short newStatus = null;
UpdateSet<Order> u = qb.update(Order.class).set(o -> o.setDescription(memo));
if (newStatus != null) u = u.set(o -> o.setStatus(newStatus));
int changed = u.where(o -> o.getId() == id).execute();

// reading a column that an earlier assignment wrote is an error (U3): the databases disagree on its value
assertThrows(LambdaQLException.class,
        () -> qb.update(Order.class).set(o -> { o.setPrice(1); o.setTex((float) o.getPrice()); }));
// the same column twice is an error (U4), also across set() calls
assertThrows(LambdaQLException.class,
        () -> qb.update(Order.class).set(o -> o.setStatus((short) 1)).set(o -> o.setStatus((short) 2)));
```

`set()`은 `UpdateSet`을 반환합니다. 이 타입에는 `set`, `where`, `all`이 있고 `execute()`는 없습니다.

<a id="update-all-rows-all"></a>
## 모든 행 수정: `all()`

`where`가 없는 update는 `all()`을 명시해야 합니다. `where`나 `all()`이 없으면 코드가 **컴파일되지 않습니다**(규칙 U5).

```java
qb.update(Order.class).set(o -> o.setStatus(0)).all().execute();       // OK
qb.update(Order.class).set(o -> o.setStatus(0)).execute();             // compile error: no execute()
```

update에는 트랜잭션이 필요합니다([7.8](07-dml.md#transactions) 참고).

<a id="update-the-first-rows-limit"></a>
## 앞쪽 행 수정: `limit`

`where(...)` 또는 `all()` 뒤에 `orderBy(...)`와 `limit(n)`을 붙이면 최대 `n`개 행을 수정합니다. 대상은 `qb.from(X.class).where(same).orderBy(same)`의 앞쪽 `n`개 행입니다.

규칙은 delete와 같습니다([7.4](07-4-delete.md#delete-the-first-rows-limit) 참고). `orderBy`가 없으면 id 순서이고, id는 항상 마지막 정렬 키이며, `limit` 없는 `orderBy`는 오류입니다.

**(테스트됨)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java), 영역 `update-limit`:

```java
// a path in the condition: the nested select joins, so the rows are the same as the select
ExecutableUpdate<Order> u = qb.update(Order.class)
        .set(o -> o.setDescription("top"))
        .where(o -> o.getCustomer().getName().equals("Kim"))
        .orderBy((o, by) -> by.desc(o.getPrice()))
        .limit(1);
assertEquals("update Order e0 set e0.description = 'top' where e0.id in (select t.id from (select x0.id as id from Order x0"
        + " join x0.customer k1 where k1.name = 'Kim' order by x0.price desc, x0.id asc limit 1) t)", u.toHql().hql());
```

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `update-limit`:

```sql
-- H2
update orders o1_0
set description='top'
where o1_0.id in (select t1_0.id from (select o2_0.id from orders o2_0 join customers c1_0 on c1_0.id=o2_0.customer_id where c1_0.name='Kim' order by o2_0.price desc,o2_0.id fetch first 1 rows only) t1_0(id))

-- MariaDB
update orders o1_0
set description='top'
where o1_0.id in (select t1_0.id from (select o2_0.id id from orders o2_0 join customers c1_0 on c1_0.id=o2_0.customer_id where c1_0.name='Kim' order by o2_0.price desc,o2_0.id limit 1) t1_0)

-- Oracle
update orders o1_0
set o1_0.description='top'
where o1_0.id in (select t1_0.id from (select o2_0.id id from orders o2_0 join customers c1_0 on c1_0.id=o2_0.customer_id where c1_0.name='Kim' order by o2_0.price desc,o2_0.id fetch first 1 rows only) t1_0)
```

경로 `o.getCustomer().getName()`는 조회와 마찬가지로 안쪽 select에서 조인이 됩니다(결정 D36). update 문장 자체는 다른 테이블을 읽지 않습니다.

<a id="update-exactly-one-row-executeone"></a>
## 정확히 한 행 수정: `executeOne()`

`executeOne()`은 1행 보호를 적용해 update를 실행합니다. 조건에 맞는 id를 최대 2개 읽습니다.

id가 2개이면 아무것도 변경하지 않고 `NonUniqueResultException`을 던집니다. id가 1개이면 `id = :p and (조건)`으로 그 행을 수정합니다. 절차는 delete와 같습니다([7.4](07-4-delete.md#delete-exactly-one-row-executeone) 참고).

```java
int one = qb.update(Order.class)
        .set(o -> o.setDescription("checked"))
        .where(o -> o.getId() == id)
        .executeOne();                             // 0 or 1; 2 or more rows: NonUniqueResultException
```

영역 `execute-one`이 속한 테스트 메서드(`DmlQueryTest`의 `executeOneProtects`)는 update의 `executeOne()`도 검사합니다. 2행 이상이 조건에 맞으면 아무것도 변경하지 않습니다.

Spring 리포지토리에서는 `updateOne(where, set)`이 같은 동작을 합니다([12.4](12-spring-data-jpa.md#one-row-protection-deleteone-and-updateone) 참고).

---

---

[7. 수정, 삽입, 삭제 (Java)](07-dml.md) | **7.2 수정(update)** | [7.4 삭제(delete)](07-4-delete.md) | [7.5 삽입(insert)](07-5-insert.md) | [7.6 업서트(upsert)](07-6-upsert.md) | [Kotlin](../kotlin/07-2-update.md)
