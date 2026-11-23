<a id="72-update"></a>
# 7.2 수정(update) (Java)

[7. 수정, 삽입, 삭제 (Java)](07-dml.md) | **7.2 수정(update)** | [7.4 삭제(delete)](07-4-delete.md) | [7.5 삽입(insert)](07-5-insert.md) | [7.6 업서트(upsert)](07-6-upsert.md) | [Kotlin](../kotlin/07-2-update.md)

---


<a id="basic-form"></a>
## 기본 형태

[`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java), 영역 `update`:

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

**SQL 블록 읽는 법.** `…`는 줄여 쓴 select 목록(엔티티의 모든 컬럼)이고, `?`는 바인드 파라미터입니다. 각 줄은 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)에서 나옵니다. 이 테스트는 각 쿼리를 Hibernate 7.4로 H2 2.3, MariaDB 10.11, Oracle 23.26에서 실행하며, Hibernate의 SQL이 바뀌면 실패합니다.

**데이터베이스별 SQL** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `update`:

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

[`ManualJavaDmlTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaDmlTest.java), 영역 `update-values`:

```java
ExecutableUpdate<Order> u = qb.update(Order.class)
        .set(o -> { o.setDescription(null); o.setUpdateAt(now); o.setKind(Order.Kind.RUSH); o.setActive(false); })
        .where(o -> o.getId() == id);
assertEquals("update Order e0 set e0.description = null, e0.updateAt = :p1,"
        + " e0.kind = org.lambdaql.query.Order$Kind.RUSH, e0.active = false where e0.id = :p2", u.toHql().hql());
int changed = u.execute();
```

`null`, 람다에 적은 enum 상수와 boolean 상수는 리터럴이고, 캡처 값(`now`, `id`)은 바인드 파라미터입니다. 같은 값을 두 번 사용하더라도 각 캡처 값은 **자기 파라미터**를 받습니다(규칙 U7).

<a id="add-to-a-value-default-for-null"></a>
## 값에 더하기, NULL의 기본값

[`ManualJavaDmlTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaDmlTest.java), 영역 `update-add`:

```java
ExecutableUpdate<Order> plusOne = qb.update(Order.class)
        .set(o -> o.setCnt(o.getCnt() + 1))
        .where(o -> o.getStatus() == 1);
assertEquals("update Order e0 set e0.cnt = (e0.cnt + 1) where e0.status = 1", plusOne.toHql().hql());
// a default for NULL: Objects.requireNonNullElse becomes coalesce
ExecutableUpdate<Order> orZero = qb.update(Order.class)
        .set(o -> o.setTotal(Objects.requireNonNullElse(o.getTotal(), 0.0) + 1))
        .all();
assertEquals("update Order e0 set e0.total = (coalesce(e0.total, 0.0) + 1.0)", orZero.toHql().hql());
```

`cnt`가 NULL인 행에서 `cnt + 1`은 NULL로 남습니다(SQL 의미). NULL을 다른 값으로 바꾸려면 `Objects.requireNonNullElse`를 쓰십시오.

<a id="embedded-fields"></a>
## 임베디드 필드

[`ManualJavaDmlTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaDmlTest.java), 영역 `update-embedded`:

```java
ExecutableUpdate<Order> oneColumn = qb.update(Order.class)
        .set(o -> o.getAddress().setCity(city)).where(o -> o.getId() == id);
assertEquals("update Order e0 set e0.address.city = :p1 where e0.id = :p2", oneColumn.toHql().hql());
// the whole value: every column of the embedded value (city, zip)
ExecutableUpdate<Order> whole = qb.update(Order.class)
        .set(o -> o.setAddress(addr)).where(o -> o.getId() == id);
assertEquals("update Order e0 set e0.address = :p1 where e0.id = :p2", whole.toHql().hql());
```

값 객체 전체를 할당하면 HQL은 `e0.address = :p1` 하나이고, Hibernate가 임베디드 값의 모든 컬럼(`city`, `zip`)을 SQL의 SET 목록에 씁니다.

<a id="change-an-association-foreign-key"></a>
## 연관 변경(외래 키)

[`ManualJavaDmlTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaDmlTest.java), 영역 `update-fk`:

```java
ExecutableUpdate<Order> byId = qb.update(Order.class)
        .set(o -> o.getCustomer().setId(cid)).where(o -> o.getId() == id);
assertEquals("update Order e0 set e0.customer.id = :p1 where e0.id = :p2", byId.toHql().hql());
ExecutableUpdate<Order> none = qb.update(Order.class)
        .set(o -> o.setCustomer(null)).where(o -> o.getId() == id);
assertEquals("update Order e0 set e0.customer = null where e0.id = :p1", none.toHql().hql());
// a captured entity: its @Id is bound (rule U11)
ExecutableUpdate<Order> entity = qb.update(Order.class)
        .set(o -> o.setCustomer(customer)).where(o -> o.getId() == other);
assertEquals("update Order e0 set e0.customer = :p1 where e0.id = :p2", entity.toHql().hql());
```

연관 엔티티에서는 `@Id`(FK)만 설정할 수 있습니다.
`o.getCustomer().setName(x)`는 오류입니다. 다른 테이블을 변경하게 되기 때문입니다(규칙 U6, [7.8](07-dml.md#rules-checked-at-analysis-time) 참고).

<a id="a-value-from-another-table"></a>
## 다른 테이블의 값

연관 경로로 값을 읽으십시오. lambdaQL은 이를 **상관 스칼라 서브쿼리**로 렌더링합니다(규칙 U9).
`where`의 경로도 같은 형태의 서브쿼리가 됩니다.

[`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java), 영역 `update-path`:

```java
// a path in the condition: a subquery, plus "customer exists" (the same rows as the select, D36, U9)
ExecutableUpdate<Order> u = qb.update(Order.class)
        .set(o -> o.setCustName("K"))
        .where(o -> o.getCustomer().getName().equals("Kim"));
assertEquals("update Order e0 set e0.custName = 'K' where (select s1.name from Customer s1 where s1.id = fk(e0.customer)) = 'Kim'"
        + " and e0.customer.id is not null", u.toHql().hql());
// a path in a value: a subquery; NULL when there is no customer
ExecutableUpdate<Order> copy = qb.update(Order.class).set(o -> o.setCustName(o.getCustomer().getName())).all();
assertEquals("update Order e0 set e0.custName = (select s1.name from Customer s1 where s1.id = fk(e0.customer))", copy.toHql().hql());
```

**데이터베이스별 SQL** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `update-path`:

```sql
-- H2
update orders o1_0
set cust_name='K'
where (select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id)='Kim' and o1_0.customer_id is not null

-- MariaDB
update orders
set cust_name='K'
where (select c1_0.name from customers c1_0 where c1_0.id=orders.customer_id)='Kim' and customer_id is not null

-- Oracle
update orders o1_0
set o1_0.cust_name='K'
where (select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id)='Kim' and o1_0.customer_id is not null
```

**데이터베이스별 SQL** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `update-path#2`:

```sql
-- H2
update orders o1_0
set cust_name=(select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id)

-- MariaDB
update orders
set cust_name=(select c1_0.name from customers c1_0 where c1_0.id=orders.customer_id)

-- Oracle
update orders o1_0
set o1_0.cust_name=(select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id)
```

주문에 고객이 없으면 `custName`은 NULL이 됩니다.
`where`에서는 lambdaQL이 `e0.customer.id is not null`을 더합니다. 그래서 수정 대상 행은 `qb.from(Order.class).where(같은 람다)`의 결과와 같습니다(결정 D36). 테스트가 이를 확인합니다.

<a id="an-aggregate-from-a-subquery"></a>
## 서브쿼리의 집계

인자가 둘인 람다 `set((o, sub) -> ...)`를 사용하십시오. 두 번째 인자는 서브쿼리의 시작점 `sub`입니다(결정 D49).
여기서는 `Agg`가 필요 없습니다. 서브쿼리에는 [5. 서브쿼리](05-subquery.md)처럼 `sum`, `avg`, ...가 있습니다.

[`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java), 영역 `update-sub`:

```java
// the second parameter starts a subquery, as in where((o, sub) -> ...) (D49)
ExecutableUpdate<Order> u = qb.update(Order.class)
        .set((o, sub) -> o.setPrice(sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).sum(i -> i.getPrice())))
        .where((o, sub) -> sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).exists());
assertEquals("update Order e0 set e0.price = (select sum(s1.price) from Item s1 where s1.order.id = e0.id)"
        + " where exists (select 1 from Item s1 where s1.order.id = e0.id)", u.toHql().hql());
// sum gives NULL when there is no row; coalesce keeps a NOT NULL column valid for every row
ExecutableUpdate<Order> all = qb.update(Order.class)
        .set((o, sub) -> value(o.getPrice(), Objects.requireNonNullElse(
                sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).sum(i -> i.getPrice()), 0.0)))
        .all();
assertEquals("update Order e0 set e0.price = coalesce((select sum(s1.price) from Item s1 where s1.order.id = e0.id), 0.0)",
        all.toHql().hql());
```

- 다른 테이블의 집계와 같은 테이블의 집계를 모두 쓸 수 있습니다(H2, MariaDB 10.11, Oracle 23.26에서 실측). `update`에서는 MariaDB도 오류 1093을 내지 않습니다.
- 일치하는 행이 없으면 집계는 NULL입니다. NOT NULL 컬럼이면 DB 오류가 나므로 `Objects.requireNonNullElse(…, 0.0)`로 감싸 `coalesce(...)`로 만드십시오.

<a id="where-with-paths-and-subqueries"></a>
## 경로와 서브쿼리를 사용한 where

`where`는 조회 쿼리와 같습니다.

[`ManualJavaDmlTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaDmlTest.java), 영역 `update-where`:

```java
// a path in the condition: a scalar subquery (U9)
ExecutableUpdate<Order> byGrade = qb.update(Order.class)
        .set(o -> o.setStatus((short) 0))
        .where(o -> o.getCustomer().getGrade() > 3);
assertEquals("update Order e0 set e0.status = 0 where (select s1.grade from Customer s1 where s1.id = fk(e0.customer)) > 3"
        + " and e0.customer.id is not null", byGrade.toHql().hql());
// a subquery with sub
ExecutableUpdate<Order> noItems = qb.update(Order.class)
        .set(o -> o.setStatus((short) 9))
        .where((o, sub) -> !sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).exists());
assertEquals("update Order e0 set e0.status = 9 where not exists (select 1 from Item s1 where s1.order.id = e0.id)",
        noItems.toHql().hql());
```

첫 번째 문장에서는 경로가 스칼라 서브쿼리가 되고, 고객이 있는 주문만 대상이 됩니다(규칙 U9).
두 번째 문장은 [5. 서브쿼리](05-subquery.md)처럼 `sub`를 사용합니다.

**데이터베이스별 SQL (이 HQL에 대한 Hibernate의 SQL)** — [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java)가 같은 문장을 Hibernate에 직접 넘겨 기록했습니다. `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)의 예제 `07-update-not-exists`:

```sql
-- H2
update orders o1_0
set status=9
where not exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id)

-- MariaDB
update orders
set status=9
where not exists(select 1 from items i1_0 where i1_0.order_id=orders.id)

-- Oracle
update orders o1_0
set o1_0.status=9
where not exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id)
```

<a id="optimistic-locking-version"></a>
## 낙관적 잠금(`@Version`)

엔티티에 `@Version` 필드가 있으면 lambdaQL은 `update versioned`를 렌더링합니다. 데이터베이스가 버전에 1을 더합니다.

[`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java), 영역 `update-versioned`:

```java
// an entity with a version field (Version annotation): the version goes up too
ExecutableUpdate<VOrder> u = qb.update(VOrder.class).set(v -> v.setStatus(2)).where(v -> v.getId() == 1L);
assertEquals("update versioned VOrder e0 set e0.status = 2 where e0.id = 1L", u.toHql().hql());
```

**데이터베이스별 SQL** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `update-versioned`:

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

[`ManualJavaDmlTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaDmlTest.java), 영역 `update-version`:

```java
// put the version in the condition and check the result
ExecutableUpdate<VOrder> u = qb.update(VOrder.class)
        .set(o -> o.setStatus(8))
        .where(o -> o.getId() == id && o.getVersion() == v);
assertEquals("update versioned VOrder e0 set e0.status = 8 where e0.id = :p1 and e0.version = :p2", u.toHql().hql());
int n = u.execute();
if (n == 0) throw new OptimisticLockException();   // checking the result is your job
```

테스트는 같은 문장을 한 번 더 실행해 0을 받는지 확인합니다. 첫 실행에서 버전이 올라갔으므로 이전 버전과 일치하는 행이 없습니다.

`@Version` 필드에는 값을 할당할 수 없습니다(규칙 U15). 할당하면 `LQL-3068`로 거부됩니다.

<a id="many-set-calls-patch"></a>
## 여러 번의 `set()` 호출(PATCH)

`set()`은 여러 번 호출할 수 있습니다. 모든 할당은 호출 순서대로 SET 목록 하나로 합쳐집니다(규칙 U14).
규칙 U3과 U4는 여러 `set()` 호출 사이에도 적용됩니다.

[`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java), 영역 `update-patch`:

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

[`ManualJavaDmlTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaDmlTest.java), 영역 `update-all`:

```java
// without where, all() is required; without both, there is no execute() (a compile error)
ExecutableUpdate<Order> u = qb.update(Order.class).set(o -> o.setStatus((short) 0)).all();
assertEquals("update Order e0 set e0.status = 0", u.toHql().hql());
int changed = u.execute();
```

update에는 트랜잭션이 필요합니다([7.8](07-dml.md#transactions) 참고).

<a id="update-the-first-rows-limit"></a>
## 앞쪽 행 수정: `limit`

`where(...)` 또는 `all()` 뒤에 `orderBy(...)`와 `limit(n)`을 붙이면 최대 `n`개 행을 수정합니다. 대상은 `qb.from(X.class).where(same).orderBy(same)`의 앞쪽 `n`개 행입니다.

규칙은 delete와 같습니다([7.4](07-4-delete.md#delete-the-first-rows-limit) 참고). `orderBy`가 없으면 id 순서이고, id는 항상 마지막 정렬 키이며, `limit` 없는 `orderBy`는 오류입니다.

[`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java), 영역 `update-limit`:

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

**데이터베이스별 SQL** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `update-limit`:

```sql
-- H2
update orders o1_0
set description='top'
where o1_0.id in (select t1_0.c0 from (select o2_0.id from orders o2_0 join customers c1_0 on c1_0.id=o2_0.customer_id where c1_0.name='Kim' order by o2_0.price desc,o2_0.id fetch first 1 rows only) t1_0(c0))

-- MariaDB
update orders o1_0
set description='top'
where o1_0.id in (select * from (select t1_0.c0 from (select * from (select o2_0.id c0 from orders o2_0 join customers c1_0 on c1_0.id=o2_0.customer_id where c1_0.name='Kim' order by o2_0.price desc,o2_0.id limit 1) _sub_) t1_0) _sub_)

-- Oracle
update orders o1_0
set o1_0.description='top'
where o1_0.id in (select t1_0.c0 from (select o2_0.id c0 from orders o2_0 join customers c1_0 on c1_0.id=o2_0.customer_id where c1_0.name='Kim' order by o2_0.price desc,o2_0.id fetch first 1 rows only) t1_0)
```

경로 `o.getCustomer().getName()`는 조회와 마찬가지로 안쪽 select에서 조인이 됩니다(결정 D36). update 문장 자체는 다른 테이블을 읽지 않습니다.

<a id="update-exactly-one-row-executeone"></a>
## 정확히 한 행 수정: `executeOne()`

`executeOne()`은 1행 보호를 적용해 update를 실행합니다. 조건에 맞는 id를 최대 2개 읽습니다.

id가 2개이면 아무것도 변경하지 않고 `NonUniqueResultException`을 던집니다. id가 1개이면 `id = :p and (조건)`으로 그 행을 수정합니다. 절차는 delete와 같습니다([7.4](07-4-delete.md#delete-exactly-one-row-executeone) 참고).

[`ManualJavaDmlTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaDmlTest.java), 영역 `update-one`:

```java
int one = qb.update(Order.class)
        .set(o -> o.setDescription("checked"))
        .where(o -> o.getId() == id)
        .executeOne();                             // 0 or 1
// two or more rows match: nothing changes
assertThrows(NonUniqueResultException.class, () -> qb.update(Order.class)
        .set(o -> o.setDescription("checked"))
        .where(o -> o.getStatus() == 1)
        .executeOne());
```

테스트는 두 번째 호출 뒤에 `checked`인 행이 하나뿐인지 확인합니다. 2행 이상이 조건에 맞으면 아무것도 변경하지 않습니다.

Spring 리포지토리에서는 `updateOne(where, set)`이 같은 동작을 합니다([12.4](12-spring-data-jpa.md#one-row-protection-deleteone-and-updateone) 참고).

---

---

[7. 수정, 삽입, 삭제 (Java)](07-dml.md) | **7.2 수정(update)** | [7.4 삭제(delete)](07-4-delete.md) | [7.5 삽입(insert)](07-5-insert.md) | [7.6 업서트(upsert)](07-6-upsert.md) | [Kotlin](../kotlin/07-2-update.md)
