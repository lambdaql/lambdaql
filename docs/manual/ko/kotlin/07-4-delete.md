<a id="74-delete"></a>
# 7.4 삭제 (Kotlin)

[7. 수정, 삽입, 삭제 (Kotlin)](07-dml.md) | [7.2 수정](07-2-update.md) | **7.4 삭제** | [7.5 삽입](07-5-insert.md) | [7.6 업서트](07-6-upsert.md) | [Java](../java/07-4-delete.md)

---

<!-- region: ManualKotlinDmlTest.kt#delete -->
```kotlin
val d = qb.delete(Order::class.java).where { it.status.toInt() == 3 }
assertEquals("delete from Order e0 where e0.status = 3", d.toHql().hql())
val deleted = d.execute()

// a path: the same rows as qb.from(Order::class.java).where { same lambda } - with !! orders without a customer stay
val byName = qb.delete(Order::class.java).where { it.customer!!.name == null }
assertEquals("delete from Order e0 where (select s1.name from Customer s1 where s1.id = fk(e0.customer)) is null"
    + " and e0.customer.id is not null", byName.toHql().hql())
```

**데이터베이스별 SQL**

<!-- sql: delete -->
```sql
-- H2, Oracle
delete from orders o1_0
where o1_0.status=3

-- MariaDB
delete o1_0 from orders o1_0
where o1_0.status=3
```

**데이터베이스별 SQL**

<!-- sql: delete#2 -->
```sql
-- H2, Oracle
delete from orders o1_0
where (select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id) is null and o1_0.customer_id is not null

-- MariaDB
delete from orders
where (select c1_0.name from customers c1_0 where c1_0.id=orders.customer_id) is null and customer_id is not null
```

MariaDB는 `delete o1_0 from orders o1_0`로 씁니다. H2와 Oracle은 `delete from orders o1_0`로 씁니다.

두 번째 문장은 `qb.from(Order::class.java).where { it.customer!!.name == null }`이 반환하는 것과 **같은 행**, 즉 고객의 이름이 없는 주문을 삭제합니다.
고객이 **없는** 주문은 삭제되지 않습니다(규칙 U9). `!!`로 지난 비교는 고객이 있는 주문에서만 참이기 때문입니다.

`?.`로 쓰면(`it.customer?.name == null`) 고객이 없는 주문도 조건에 맞으므로 조회는 `left join`이 되고, DML은 `e0.customer.id is not null`을 더하지 않습니다. 그래서 고객이 없는 주문도 삭제됩니다.
<!-- region: ManualKotlinDmlTest.kt#delete-safe-call -->
```kotlin
// ?. : no "customer exists" check, so orders without a customer are deleted too, as the select returns them
val d = qb.delete(Order::class.java).where { it.customer?.name == null }
assertEquals("delete from Order e0 where (select s1.name from Customer s1 where s1.id = fk(e0.customer)) is null",
    d.toHql().hql())
```

같은 방식의 UPDATE 예제는 [7.2](07-2-update.md)를 참고하십시오. 고객이 없는 주문만 삭제하려면 `it.customer == null`이라고 쓰십시오.

모든 행을 삭제할 때는 `all()`이 필요합니다(규칙 U5). `qb.delete(Order::class.java).execute()`처럼 `all()` 없이 쓰면 `execute()`가 없으므로 컴파일 오류입니다.
id 목록으로 삭제할 때는 캡처한 컬렉션을 씁니다.

<!-- region: ManualKotlinDmlTest.kt#delete-all -->
```kotlin
// ids in a captured collection
val byIds = qb.delete(Item::class.java).where { it.id in ids }
assertEquals("delete from Item e0 where e0.id in (:p1)", byIds.toHql().hql())
byIds.execute()
// every row: all() is required (U5)
val all = qb.delete(Order::class.java).all()
assertEquals("delete from Order e0", all.toHql().hql())
all.execute()
```

`where` 안의 서브쿼리는 [5. 서브쿼리](05-subquery.md)처럼 두 번째 람다 표현식 인자 `sub`를 사용합니다.
품목이 없는 주문을 삭제하는 예:

<!-- region: ManualKotlinDmlTest.kt#delete-not-exists -->
```kotlin
// orders without items: the subquery starts with the second lambda parameter sub
val d = qb.delete(Order::class.java)
    .where { o, sub -> !sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
assertEquals("delete from Order e0 where not exists (select 1 from Item s1 where s1.order.id = e0.id)", d.toHql().hql())
val deleted = d.execute()
```

**데이터베이스별 SQL**

<!-- sql: dml-subquery -->
```sql
-- H2, Oracle
delete from orders o1_0
where not exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id)

-- MariaDB
delete from orders
where not exists(select 1 from items i1_0 where i1_0.order_id=orders.id)
```

모든 JPA 벌크 문장과 마찬가지로, delete는 JPA `cascade`나 `orphanRemoval`을 실행하지 않고 이미 로드된 엔티티에도 반영되지 않습니다(규칙 U16).
delete에는 트랜잭션이 필요합니다([7.8](07-dml.md#transactions) 참고).

예제 코드: [`ManualKotlinDmlTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)(`delete`, `delete-safe-call`, `delete-all`, `delete-not-exists`).

<a id="delete-with-a-subquery-on-the-same-table"></a>
## 같은 테이블을 읽는 서브쿼리

`where`의 서브쿼리가 삭제 대상 엔티티(또는 같은 상속 계층의 엔티티)를 읽을 수 있습니다. 서브쿼리 자신의 `from`, 서브쿼리 안의 조인, U9의 상관 스칼라 서브쿼리가 이에 해당합니다.
이때 lambdaQL은 먼저 대상 id를 파생 테이블로 읽고 그 id로 삭제합니다(규칙 U17).

```
delete from Order d0 where d0.id in (select t.id from (select e0.id as id from Order e0 where <condition>) t)
```

- 이유: 서브쿼리가 대상 테이블을 읽으면 MariaDB는 Hibernate의 `delete t from …`을 거부합니다(오류 1093, "specified twice").
- 상관 서브쿼리에도 같은 형태가 동작합니다. 서브쿼리만 감싸는 방식은 상관 서브쿼리에 쓸 수 없습니다.
- H2, MariaDB 10.11, Oracle 23.26에서 실측했습니다(비상관 `avg`, 상관 `exists`, `max`, 서브쿼리 안의 조인). 세 DB에서 삭제한 행 수가 같고, MariaDB도 실패하지 않습니다.
- 다른 테이블을 읽는 서브쿼리는 기존 형태를 유지합니다(위의 `not exists` 예).
- `update`는 감싸지 않습니다. 자기 테이블을 읽는 서브쿼리가 있는 `update`는 세 DB 모두에서 동작합니다(MariaDB 10.11에서 실측).
- 복합 id 엔티티는 감싸지 않습니다. `limit`이 있는 delete는 원래 아래의 감싼 형태(별칭 `x0`)를 사용합니다.

<!-- region: KotlinQueryTest.kt#delete-self-subquery -->
```kotlin
// the subquery reads the target table: the matching ids are read through a derived table first (U17, MariaDB 1093)
val d = qb.delete(Order::class.java).where { o, sub -> o.price < sub.from(Order::class.java).avg { x -> x.price } }
assertEquals("delete from Order d0 where d0.id in (select t.id from (select e0.id as id from Order e0"
        + " where e0.price < (select avg(s1.price) from Order s1)) t)", d.toHql().hql())
```

예제 코드: [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)(`delete-self-subquery`).

<a id="delete-the-first-rows-limit"></a>
## 앞쪽 행 삭제: `limit`

`where { ... }` 또는 `all()` 뒤에 `orderBy { ... }`와 `limit(n)`을 붙이면 최대 `n`개 행을 삭제합니다. 대상은 `qb.from(X::class.java).where { same }.orderBy { same }`의 앞쪽 `n`개 행입니다.
많은 행을 작은 단위로 나누어 처리할 때 사용합니다.

- `orderBy`가 없으면 id 순서입니다.
- lambdaQL은 id를 마지막 정렬 키로 추가하므로 순서가 항상 같습니다([동순위 처리](04-order-paging.md#tie-breaker-automatic-id) 참고).
- `orderBy`에는 조회와 마찬가지로 정렬 람다 표현식이나 `SortSpec`을 지정합니다([4. 정렬과 페이징](04-order-paging.md) 참고).
- `limit` 없는 `orderBy`는 오류(`IllegalStateException`)입니다. 이 경우 순서가 아무 역할을 하지 않기 때문입니다.

<!-- region: ManualKotlinDmlTest.kt#delete-limit -->
```kotlin
// the first rows of qb.from(Order::class.java).where { same }.orderBy { same }; the id is the last sort key
val d = qb.delete(Order::class.java)
    .where { it.status.toInt() == 1 }
    .orderBy { o -> asc(o.price) }
    .limit(1)
assertEquals("delete from Order e0 where e0.id in (select t.id from (select x0.id as id from Order x0"
    + " where x0.status = 1 order by x0.price asc, x0.id asc limit 1) t)", d.toHql().hql())
val deleted = d.execute()
```

**데이터베이스별 SQL**

<!-- sql: delete-limit -->
```sql
-- H2
delete from orders o1_0
where o1_0.id in (select t1_0.c0 from (select o2_0.id from orders o2_0 where o2_0.status=1 order by o2_0.price,o2_0.id fetch first 1 rows only) t1_0(c0))

-- MariaDB
delete o1_0 from orders o1_0
where o1_0.id in (select * from (select t1_0.c0 from (select * from (select o2_0.id c0 from orders o2_0 where o2_0.status=1 order by o2_0.price,o2_0.id limit 1) _sub_) t1_0) _sub_)

-- Oracle
delete from orders o1_0
where o1_0.id in (select t1_0.c0 from (select o2_0.id c0 from orders o2_0 where o2_0.status=1 order by o2_0.price,o2_0.id fetch first 1 rows only) t1_0)
```

lambdaQL은 limit을 `e0.id in (select t.id from (select x0.id as id from X x0 ... order by ... limit n) t)`로 렌더링합니다.

- 안쪽 select는 조인을 포함해 조회 규칙을 따릅니다. 따라서 이 문장은 조회가 반환하는 행과 정확히 같은 행을 삭제합니다.
- 파생 테이블 `t`는 MariaDB를 위한 것입니다. 파생 테이블이 없으면 MariaDB는 `in` 서브쿼리의 `limit`(오류 1235)과 대상 테이블을 읽는 서브쿼리(오류 1093)를 거부합니다.
- H2와 Oracle은 `fetch first 1 rows only`, MariaDB는 `limit 1`로 씁니다.

예제 코드: [`ManualKotlinDmlTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)(`delete-limit`).

<a id="delete-exactly-one-row-executeone"></a>
## 정확히 한 행 삭제: `executeOne()`

`executeOne()`은 **1행 보호**를 적용해 delete를 실행합니다. 조건이 한 행에 맞아야 하는 경우(예: 유일 키)에 `execute()` 대신 사용하십시오.

1. lambdaQL이 조건에 맞는 행의 id를 최대 2개 읽습니다.
2. id가 없으면 아무것도 변경하지 않고 0을 반환합니다.
3. id가 2개이면 아무것도 변경하지 않고 JPA `NonUniqueResultException`을 던집니다.
4. id가 1개이면 `id = :p and (조건)`으로 그 행을 삭제하고 1을 반환합니다. 그사이 그 행이 조건에서 벗어났으면 0을 반환합니다.

문장에 id 하나를 지정하므로 2행 이상을 변경하지 않습니다. 이 보호는 롤백에 의존하지 않습니다.

<!-- region: ManualKotlinDmlTest.kt#execute-one -->
```kotlin
// one row matches: it is deleted
val one = qb.delete(Order::class.java).where { it.id == id }.executeOne()
// two or more rows match: nothing is deleted
assertThrows(NonUniqueResultException::class.java) {
    qb.delete(Order::class.java).where { it.price > 100.0 }.executeOne()
}
```

두 번째 문장은 가격이 100보다 큰 주문이 둘 이상이므로 아무것도 삭제하지 않습니다.
id는 NULL이 될 수 없으므로 `it.id == id`는 단순한 `e0.id = :p1`이 됩니다([3.6.2](03-where.md#362--compares-values) 참고).

- `executeOne()`을 `orderBy`나 `limit`과 함께 쓰면 오류(`IllegalStateException`)입니다.
- `limit`과 `executeOne()`은 아직 복합 키 엔티티를 지원하지 않습니다.
- Spring 리포지토리에서는 `deleteOne { ... }`이 같은 동작을 하며, `IncorrectResultSizeDataAccessException`을 던집니다([12.4](12-spring-data-jpa.md#one-row-protection-deleteone-and-updateone) 참고).

---

[7. 수정, 삽입, 삭제 (Kotlin)](07-dml.md) | [7.2 수정](07-2-update.md) | **7.4 삭제** | [7.5 삽입](07-5-insert.md) | [7.6 업서트](07-6-upsert.md) | [Java](../java/07-4-delete.md)

예제 코드: [`ManualKotlinDmlTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)(`execute-one`).

