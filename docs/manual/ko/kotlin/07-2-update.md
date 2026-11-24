<a id="72-update"></a>
# 7.2 수정 (Kotlin)

[7. 수정, 삽입, 삭제 (Kotlin)](07-dml.md) | **7.2 수정** | [7.4 삭제](07-4-delete.md) | [7.5 삽입](07-5-insert.md) | [7.6 업서트](07-6-upsert.md) | [Java](../java/07-2-update.md)

---

<a id="basic-form"></a>
## 기본 형태

<!-- region: ManualKotlinDmlTest.kt#update -->
```kotlin
val st: Short = 5
val u = qb.update(Order::class.java)
    .set { it.status = st; it.price = it.price * 2.0 }
    .where { it.status.toInt() == 1 }
// the price on the right is the value before the change
assertEquals("update Order e0 set e0.status = :p1, e0.price = (e0.price * 2.0) where e0.status = 1", u.toHql().hql())
val changed = u.execute()                      // number of updated rows
```

`st`는 캡처 값이므로 바인드 파라미터(`:p1`)가 됩니다. 상수 `2.0`은 리터럴로 유지됩니다.
예제 엔티티 `Order`는 Java 클래스이고 `status`는 `short`이므로, 조건은 `it.status.toInt() == 1`로 씁니다.
`toHql()`은 문장을 실행하지 않고 HQL과 파라미터를 반환합니다.

**데이터베이스별 SQL**

<!-- sql: update -->
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

예제 코드: [`ManualKotlinDmlTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)(`update`).

<a id="values-null-date-boolean"></a>
## 값: null, 날짜, boolean

<!-- region: ManualKotlinDmlTest.kt#update-values -->
```kotlin
// null, a captured date, a boolean and an enum constant
val u = qb.update(Order::class.java)
    .set { it.description = null; it.updateAt = now; it.isActive = false; it.kind = Order.Kind.RUSH }
    .where { it.id == id }
assertEquals("update Order e0 set e0.description = null, e0.updateAt = :p1, e0.active = false,"
    + " e0.kind = org.lambdaql.query.Order\$Kind.RUSH where e0.id = :p2", u.toHql().hql())
```

`null`, boolean, enum 상수는 리터럴이 됩니다. enum 상수는 전체 클래스 이름(`org.lambdaql.query.Order$Kind.RUSH`)으로 씁니다.
boolean 속성 `active`의 Java getter는 `isActive()`이므로 Kotlin에서는 `it.isActive`로 대입합니다.
캡처 값은 같은 값을 두 번 사용하더라도 각각 **자체 파라미터**를 받습니다(규칙 U7).

예제 코드: [`ManualKotlinDmlTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)(`update-values`).

<a id="add-to-a-value-default-for-null"></a>
## 값에 더하기, NULL의 기본값

<!-- region: ManualKotlinDmlTest.kt#update-add -->
```kotlin
// += on a property: the old value plus 1
val add = qb.update(Order::class.java).set { it.cnt += 1 }.where { it.status.toInt() == 1 }
assertEquals("update Order e0 set e0.cnt = (e0.cnt + 1) where e0.status = 1", add.toHql().hql())
// ?: gives a default for NULL: coalesce
val name = qb.update(Order::class.java).set { it.custName = it.description ?: "none" }.all()
assertEquals("update Order e0 set e0.custName = coalesce(e0.description, 'none')", name.toHql().hql())
```

예제 엔티티의 `cnt`는 Java `Integer`이므로 Kotlin은 플랫폼 타입으로 보고 `+=`를 컴파일합니다. `cnt`가 NULL인 행은 SQL 규칙에 따라 NULL로 남습니다.
`?:`는 문자열과 숫자 모두 `coalesce(...)`가 됩니다. `it.total = (it.total ?: 0.0) + 1`은 `set e0.total = (coalesce(e0.total, 0.0) + 1.0)`입니다.

예제 코드: [`ManualKotlinDmlTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)(`update-add`).

<a id="embedded-fields"></a>
## 임베디드 필드

<!-- region: ManualKotlinDmlTest.kt#update-embedded -->
```kotlin
// one column of the embedded value
val one = qb.update(Order::class.java).set { it.address.city = city }.where { it.id == id }
assertEquals("update Order e0 set e0.address.city = :p1 where e0.id = :p2", one.toHql().hql())
// the whole embedded value: every column (city, zip)
val all = qb.update(Order::class.java).set { it.address = addr }.where { it.status.toInt() == 1 }
assertEquals("update Order e0 set e0.address = :p1 where e0.status = 1", all.toHql().hql())
```

임베디드 값 전체를 대입하면 HQL은 `set e0.address = :p1`이고, Hibernate가 모든 컬럼(`city`, `zip`)을 씁니다.

예제 코드: [`ManualKotlinDmlTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)(`update-embedded`).

<a id="change-an-association-foreign-key"></a>
## 연관 변경(외래 키)

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

연관 엔티티에서는 `@Id`(FK)만 설정할 수 있습니다.
`it.customer?.name = x`는 오류(`LQL-3050`)입니다. 다른 테이블을 변경하게 되기 때문입니다(규칙 U6, [7.8](07-dml.md#rules-checked-at-analysis-time) 참고).

예제 코드: [`ManualKotlinDmlTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)(`update-fk`).

<a id="a-value-from-another-table"></a>
## 다른 테이블의 값

연관 경로로 값을 읽으십시오. lambdaQL은 이를 **상관 스칼라 서브쿼리**로 출력합니다(규칙 U9).
`where`의 경로도 같은 형태의 서브쿼리가 됩니다.

<!-- region: ManualKotlinDmlTest.kt#update-path -->
```kotlin
// a path in the condition: a subquery, plus "customer exists" (the same rows as the select, U9)
val u = qb.update(Order::class.java)
    .set { it.custName = "K" }
    .where { it.customer?.name == "Kim" }
assertEquals("update Order e0 set e0.custName = 'K' where (select s1.name from Customer s1 where s1.id = fk(e0.customer)) = 'Kim'"
    + " and e0.customer.id is not null", u.toHql().hql())
// a path in a value: a subquery; NULL when there is no customer
val copy = qb.update(Order::class.java).set { it.custName = it.customer?.name }.all()
assertEquals("update Order e0 set e0.custName = (select s1.name from Customer s1 where s1.id = fk(e0.customer))",
    copy.toHql().hql())
```

**데이터베이스별 SQL**

<!-- sql: update-path -->
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

**데이터베이스별 SQL**

<!-- sql: update-path#2 -->
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
조회에서 inner join이 되는 경로에는 DML도 고객 존재 검사(`e0.customer.id is not null`)를 더합니다. 위 `where { it.customer?.name == "Kim" }`는 고객이 없는 주문이 조건에 맞을 수 없으므로 inner join이고, 이 검사가 붙습니다.
조회에서 `left join`이 되는 경로에는 이 검사를 더하지 않습니다. 고객이 없는 주문에서 서브쿼리는 NULL이 되므로 `= 'Kim'` 같은 비교에는 맞지 않습니다. 어느 쪽이든 수정 대상 행은 `qb.from(Order::class.java).where { 같은 람다 표현식 }`의 결과와 같습니다.

**`?.`와 `||`.** `it.status == 1 || it.customer?.name == "Kim"`처럼 고객이 없는 주문이 조건을 참으로 만들 수 있으면 그런 주문도 조회와 똑같이 수정 대상이 됩니다([3.10](03-where.md#310-association-paths) 참고).

<!-- region: KotlinQueryTest.kt#update-safe-call -->
```kotlin
// UPDATE: the left join of ?. drops the "customer exists" check, so the order without a customer is changed too
val u = qb.update(Order::class.java)
    .set { it.status = st }
    .where { it.status.toInt() == 1 || it.customer?.name == "Kim" }
assertEquals("update Order e0 set e0.status = :p1"
    + " where e0.status = 1 or (select s1.name from Customer s1 where s1.id = fk(e0.customer)) = 'Kim'", u.toHql().hql())
```

**데이터베이스별 SQL**

<!-- sql: update-safe-call -->
```sql
-- H2
update orders o1_0
set status=?
where o1_0.status=1 or (select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id)='Kim'

-- MariaDB
update orders
set status=?
where status=1 or (select c1_0.name from customers c1_0 where c1_0.id=orders.customer_id)='Kim'

-- Oracle
update orders o1_0
set o1_0.status=?
where o1_0.status=1 or (select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id)='Kim'
```

예제 코드: [`ManualKotlinDmlTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)(`update-path`), [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)(`update-safe-call`).

<a id="an-aggregate-from-a-subquery"></a>
## 서브쿼리의 집계

인자가 둘인 람다 표현식 `set { o, sub -> ... }`를 사용하십시오. 두 번째 인자는 서브쿼리의 시작점 `sub`입니다.
여기서는 `Agg`가 필요 없습니다. 서브쿼리에는 [5. 서브쿼리](05-subquery.md)처럼 `sum`, `avg`, ...가 있습니다.

<!-- region: KotlinQueryTest.kt#update-sub -->
```kotlin
// the second parameter starts a subquery, as in where { o, sub -> ... }
val u = qb.update(Order::class.java)
    .set { o, sub -> o.price = sub.from(Item::class.java).where { i -> i.order?.id == o.id }.sum { it.price } }
    .where { o, sub -> sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
assertEquals("update Order e0 set e0.price = (select sum(s1.price) from Item s1 where s1.order.id = e0.id)"
        + " where exists (select 1 from Item s1 where s1.order.id = e0.id)", u.toHql().hql())
```

- 다른 테이블의 집계와 같은 테이블의 집계를 모두 쓸 수 있습니다(H2, MariaDB 10.11, Oracle 23.26에서 실측). `update`에서는 MariaDB도 오류 1093을 내지 않습니다.
- 일치하는 행이 없으면 집계는 NULL입니다. NOT NULL 컬럼이면 DB 오류가 나므로 `?: 0.0`을 붙여 `coalesce(...)`로 만드십시오.

<!-- region: ManualKotlinDmlTest.kt#update-sub-coalesce -->
```kotlin
// sum gives NULL when there is no row; ?: becomes coalesce and keeps the NOT NULL price valid
val u = qb.update(Order::class.java)
    .set { o, sub ->
        o.price = sub.from(Item::class.java).where { i -> i.order?.id == o.id }.sum { it.price } ?: 0.0
    }
    .all()
assertEquals("update Order e0 set e0.price = coalesce((select sum(s1.price) from Item s1 where s1.order.id = e0.id), 0.0)",
    u.toHql().hql())
```

품목이 없는 주문의 가격은 `0.0`이 됩니다.

인자가 하나인 후행 람다 표현식(`set { it... }`)와 인자가 둘인 후행 람다 표현식(`set { o, sub -> ... }`)는 서로 다른 오버로드입니다. Kotlin은 알맞은 것을 고릅니다.

예제 코드: [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)(`update-sub`), [`ManualKotlinDmlTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)(`update-sub-coalesce`).

<a id="where-with-paths-and-subqueries"></a>
## 경로와 서브쿼리를 사용한 where

`where`는 조회 쿼리와 같습니다.

<!-- region: ManualKotlinDmlTest.kt#update-where -->
```kotlin
// a path: a subquery, as above
val gold = qb.update(Order::class.java).set { it.status = 0 }.where { it.customer?.grade!! > 3 }
assertEquals("update Order e0 set e0.status = 0 where (select s1.grade from Customer s1 where s1.id = fk(e0.customer)) > 3"
    + " and e0.customer.id is not null", gold.toHql().hql())
// a subquery with sub, as in chapter 5
val noItems = qb.update(Order::class.java).set { it.status = 9 }
    .where { o, sub -> !sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
assertEquals("update Order e0 set e0.status = 9 where not exists (select 1 from Item s1 where s1.order.id = e0.id)",
    noItems.toHql().hql())
```

첫 번째 문장의 경로는 위의 규칙대로 스칼라 서브쿼리가 됩니다. 두 번째 문장은 [5. 서브쿼리](05-subquery.md)처럼 `sub`를 사용합니다.

예제 코드: [`ManualKotlinDmlTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)(`update-where`).

<a id="optimistic-locking-version"></a>
## 낙관적 잠금(`@Version`)

엔티티에 `@Version` 속성이 있으면 lambdaQL은 `update versioned`를 출력합니다. 데이터베이스가 버전에 1을 더합니다.

<!-- region: ManualKotlinDmlTest.kt#update-versioned -->
```kotlin
// an entity with a version property (@Version): the version goes up too
val u = qb.update(VOrder::class.java).set { it.status = 2 }.where { it.id == 1L }
assertEquals("update versioned VOrder e0 set e0.status = 2 where e0.id = 1L", u.toHql().hql())
```

**데이터베이스별 SQL**

<!-- sql: update-versioned -->
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

<!-- region: ManualKotlinDmlTest.kt#update-version-check -->
```kotlin
val n = qb.update(VOrder::class.java)
    .set { it.status = 8 }
    .where { it.id == id && it.version == v }
    .execute()
if (n == 0) throw OptimisticLockException()   // checking the result is your job
```

같은 문장을 다시 실행하면 `v`가 이전 버전이므로 0행이 수정되고 `OptimisticLockException`이 발생합니다.

`@Version` 속성에는 대입할 수 없습니다(규칙 U15). 대입하면 `LQL-3068`로 거부됩니다.

예제 코드: [`ManualKotlinDmlTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)(`update-versioned`, `update-version-check`).

<a id="many-set-calls-patch"></a>
## 여러 번의 `set()` 호출(PATCH)

`set()`은 여러 번 호출할 수 있습니다. 모든 대입은 호출 순서대로 SET 목록 하나로 합쳐집니다(규칙 U14).
규칙 U3과 U4는 여러 `set()` 호출 사이에도 적용됩니다.

<!-- region: ManualKotlinDmlTest.kt#update-patch -->
```kotlin
// PATCH: assign only the properties that have a value; every set() adds assignments
val memo = "checked"
val newStatus: Short? = null
var u = qb.update(Order::class.java).set { it.description = memo }
if (newStatus != null) u = u.set { it.status = newStatus }
val changed = u.where { it.id == id }.execute()

// reading a column that an earlier assignment wrote is an error (U3)
val read = assertThrows(LambdaQLException::class.java) {
    qb.update(Order::class.java).set { it.price = 1.0; it.total = it.price }
}
assertEquals("LQL-3042", read.code())
// the same column twice is an error (U4), also across set() calls
val twice = assertThrows(LambdaQLException::class.java) {
    qb.update(Order::class.java).set { it.status = 1 }.set { it.status = 2 }
}
assertEquals("LQL-3041", twice.code())
```

`set()`은 `UpdateSet`을 반환합니다. 이 타입에는 `set`, `where`, `all`이 있고 `execute()`는 없습니다.

예제 코드: [`ManualKotlinDmlTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)(`update-patch`).

<a id="update-all-rows-all"></a>
## 모든 행 수정: `all()`

`where`가 없는 update는 `all()`을 명시해야 합니다. `where`나 `all()`이 없으면 코드가 **컴파일되지 않습니다**(규칙 U5).

<!-- region: ManualKotlinDmlTest.kt#update-all -->
```kotlin
// no where: all() is required, without it there is no execute() (U5)
val u = qb.update(Order::class.java).set { it.status = 0 }.all()
assertEquals("update Order e0 set e0.status = 0", u.toHql().hql())
val changed = u.execute()
```

`qb.update(Order::class.java).set { it.status = 0 }.execute()`처럼 `all()` 없이 쓰면 `execute()`가 없으므로 컴파일 오류입니다.

update에는 트랜잭션이 필요합니다([7.8](07-dml.md#transactions) 참고).

예제 코드: [`ManualKotlinDmlTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)(`update-all`).

<a id="update-the-first-rows-limit"></a>
## 앞쪽 행 수정: `limit`

`where { ... }` 또는 `all()` 뒤에 `orderBy { ... }`와 `limit(n)`을 붙이면 최대 `n`개 행을 수정합니다. 대상은 `qb.from(X::class.java).where { same }.orderBy { same }`의 앞쪽 `n`개 행입니다.

규칙은 delete와 같습니다([7.4](07-4-delete.md#delete-the-first-rows-limit) 참고). `orderBy`가 없으면 id 순서이고, id는 항상 마지막 정렬 키이며, `limit` 없는 `orderBy`는 오류입니다.

<!-- region: ManualKotlinDmlTest.kt#update-limit -->
```kotlin
// a path in the condition: the nested select joins, so the rows are the same as the select
val u = qb.update(Order::class.java)
    .set { it.description = "top" }
    .where { it.customer?.name == "Kim" }
    .orderBy { o -> desc(o.price) }
    .limit(1)
assertEquals("update Order e0 set e0.description = 'top' where e0.id in (select t.id from (select x0.id as id from Order x0"
    + " join x0.customer k1 where k1.name = 'Kim' order by x0.price desc, x0.id asc limit 1) t)", u.toHql().hql())
```

**데이터베이스별 SQL**

<!-- sql: update-limit -->
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

경로 `it.customer?.name`는 조회와 마찬가지로 안쪽 select에서 조인이 됩니다. update 문장 자체는 다른 테이블을 읽지 않습니다.

예제 코드: [`ManualKotlinDmlTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)(`update-limit`).

<a id="update-exactly-one-row-executeone"></a>
## 정확히 한 행 수정: `executeOne()`

`executeOne()`은 1행 보호를 적용해 update를 실행합니다. 조건에 맞는 id를 최대 2개 읽습니다.

id가 2개이면 아무것도 변경하지 않고 `NonUniqueResultException`을 던집니다. id가 1개이면 `id = :p and (조건)`으로 그 행을 수정합니다. 절차는 delete와 같습니다([7.4](07-4-delete.md#delete-exactly-one-row-executeone) 참고).

<!-- region: ManualKotlinDmlTest.kt#update-one -->
```kotlin
// one row matches: it is changed
val one = qb.update(Order::class.java)
    .set { it.description = "checked" }
    .where { it.id == id }
    .executeOne()                             // 0 or 1
// two or more rows match: nothing is changed
assertThrows(NonUniqueResultException::class.java) {
    qb.update(Order::class.java).set { it.description = "x" }.where { it.isActive }.executeOne()
}
```

두 번째 문장은 모든 주문이 조건에 맞으므로 아무 행도 변경하지 않습니다.

Spring 리포지토리에서는 `updateOne({ ... }) { ... }`가 같은 동작을 합니다([12.4](12-spring-data-jpa.md#one-row-protection-deleteone-and-updateone) 참고).

---

[7. 수정, 삽입, 삭제 (Kotlin)](07-dml.md) | **7.2 수정** | [7.4 삭제](07-4-delete.md) | [7.5 삽입](07-5-insert.md) | [7.6 업서트](07-6-upsert.md) | [Java](../java/07-2-update.md)

예제 코드: [`ManualKotlinDmlTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)(`update-one`).

