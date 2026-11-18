<a id="74-delete"></a>
# 7.4 삭제 (Kotlin)

> [!NOTE]
> **상태: 구현됨, 테스트함**(H2 2.3, MariaDB 10.11, Oracle 23.26, 마일스톤 M10). 테스트는 대부분 **Java** 람다를 사용합니다. `not exists` 서브쿼리를 쓰는 delete는 Kotlin 람다로도 테스트했습니다([`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt), **(테스트됨)** 표시). `limit`과 `executeOne()`(결정 D41)도 같습니다.
> `where` 안의 서브쿼리(`sub`)도 구현되어 Java 람다로 테스트했습니다(마일스톤 M8).
> **아직 구현되지 않음:** 복합 키 엔티티의 `limit` / `executeOne()`.
> Kotlin 코드는 테스트된 Java 문장을 Kotlin 문법으로 옮긴 것이고, HQL은 [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)와 [`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java)의 Java 테스트에서 가져왔습니다. HQL이 없는 예제는 설계를 따릅니다.
> 전체 상태는 [트랙 README](README.md)를 참고하십시오.

[7. 수정, 삽입, 삭제 (Kotlin)](07-dml.md) | [7.2 수정](07-2-update.md) | **7.4 삭제** | [7.5 삽입](07-5-insert.md) | [7.6 업서트](07-6-upsert.md) | [Java](../java/07-4-delete.md)

---


```kotlin
val d = qb.delete(Order::class.java).where { it.status == 3 }
// HQL: delete from Order e0 where e0.status = 3
val deleted = d.execute()

// a path: the same rows as qb.from(Order::class.java).where { same lambda } - orders without a customer stay
val byName = qb.delete(Order::class.java).where { it.customer?.name == null }
// HQL: delete from Order e0 where (select s1.name from Customer s1 where s1.id = e0.customer.id) is null and e0.customer.id is not null
```

**SQL 블록 읽는 법.** `…`는 줄여 쓴 select 목록(엔티티의 모든 컬럼)이고, `?`는 바인드 파라미터입니다. 각 줄은 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)에서 나옵니다. 이 테스트는 각 쿼리를 Hibernate 6.6.5로 H2 2.3, MariaDB 10.11, Oracle 23.26에서 실행하며, Hibernate의 SQL이 바뀌면 실패합니다. 이 테스트는 Java 람다를 사용합니다. 의미가 같은 Kotlin 람다는 같은 HQL을 만들므로 SQL도 같습니다.

**데이터베이스별 SQL (Java 테스트 실행 결과, Kotlin도 같은 HQL을 만듦)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `delete`:

```sql
-- H2, Oracle
delete from orders o1_0
where o1_0.status=3

-- MariaDB
delete o1_0 from orders o1_0
where o1_0.status=3
```

**데이터베이스별 SQL (Java 테스트 실행 결과, Kotlin도 같은 HQL을 만듦)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `delete#2`:

```sql
-- H2, Oracle
delete from orders o1_0
where (select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id) is null and o1_0.customer_id is not null

-- MariaDB
delete o1_0 from orders o1_0
where (select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id) is null and o1_0.customer_id is not null
```

MariaDB는 `delete o1_0 from orders o1_0`로 씁니다. H2와 Oracle은 `delete from orders o1_0`로 씁니다.

**(테스트됨)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt), 영역 `insert-delete`:

```kotlin
val i = qb.insert(Archive::class.java).values { it.id = 100L; it.price = 9.5; it.customerName = "Kim" }
assertEquals(1, i.execute())
val d = qb.delete(Order::class.java).where { o, sub -> !sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
assertEquals("delete from Order e0 where not exists (select 1 from Item s1 where s1.order.id = e0.id)", d.toHql().hql())
```

테스트는 먼저 `Archive` 행 하나를 삽입하고, 품목이 없는 주문을 삭제한 뒤, 남은 행을 Kotlin으로 같은 조건을 적용한 결과와 비교합니다.
더 많은 문장을 Java로 작성한 테스트가 [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)에 있습니다(H2, MariaDB, Oracle). 그 예제는 [이 페이지의 Java 버전](../java/07-4-delete.md)을 참고하십시오.

두 번째 문장은 `qb.from(Order::class.java).where { it.customer?.name == null }`이 반환하는 것과 **같은 행**, 즉 고객의 이름이 없는 주문을 삭제합니다. Java 테스트는 삭제된 행을 이 조회 결과와 비교합니다.
고객이 **없는** 주문은 삭제되지 않습니다(규칙 U9, 결정 D36). null 가드(`it.customer == null || ...`)를 써도 마찬가지입니다. 조회에서도 이 경로는 inner join이기 때문입니다(결정 D48).
고객이 없는 주문을 삭제하려면 `it.customer == null` 조건으로 삭제 문장을 한 번 더 실행하십시오.

모든 행을 삭제할 때는 `all()`이 필요합니다(규칙 U5).

```kotlin
qb.delete(Order::class.java).all().execute()     // delete all rows
qb.delete(Order::class.java).execute()           // compile error: no execute()
```

id 목록으로 삭제(캡처한 컬렉션, 결정 D29):

```kotlin
qb.delete(Item::class.java).where { it.id in ids }.execute()
```

`where` 안의 서브쿼리는 [5. 서브쿼리](05-subquery.md)처럼 두 번째 람다 인자 `sub`를 사용합니다.
품목이 없는 주문을 삭제하는 예:

```kotlin
qb.delete(Order::class.java)
    .where { o, sub -> !sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
    .execute()
// HQL: delete from Order e0 where not exists (select 1 from Item s1 where s1.order.id = e0.id)
```

같은 문장을 Java로 작성한 테스트가 [`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java)에 있습니다(영역 `dml-subquery`).

**데이터베이스별 SQL (Java 테스트 실행 결과, Kotlin도 같은 HQL을 만듦)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `dml-subquery`:

```sql
-- H2, Oracle
delete from orders o1_0
where not exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id)

-- MariaDB
delete o1_0 from orders o1_0
where not exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id)
```

모든 JPA 벌크 문장과 마찬가지로, delete는 JPA `cascade`나 `orphanRemoval`을 실행하지 않고 이미 로드된 엔티티에도 반영되지 않습니다(규칙 U16).
delete에는 트랜잭션이 필요합니다([7.8](07-dml.md#transactions) 참고).

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

**(테스트됨)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt), 영역 `delete-self-subquery`:

```kotlin
// the subquery reads the target table: the matching ids are read through a derived table first (U17, MariaDB 1093)
val d = qb.delete(Order::class.java).where { o, sub -> o.price < sub.from(Order::class.java).avg { x -> x.price } }
assertEquals("delete from Order d0 where d0.id in (select t.id from (select e0.id as id from Order e0"
        + " where e0.price < (select avg(s1.price) from Order s1)) t)", d.toHql().hql())
```

<a id="delete-the-first-rows-limit"></a>
## 앞쪽 행 삭제: `limit`

`where { ... }` 또는 `all()` 뒤에 `orderBy { ... }`와 `limit(n)`을 붙이면 최대 `n`개 행을 삭제합니다. 대상은 `qb.from(X::class.java).where { same }.orderBy { same }`의 앞쪽 `n`개 행입니다.
많은 행을 작은 단위로 나누어 처리할 때 사용합니다.

- `orderBy`가 없으면 id 순서입니다.
- lambdaQL은 id를 마지막 정렬 키로 추가하므로 순서가 항상 같습니다(규칙 S9, [동순위 처리](04-order-paging.md#tie-breaker-automatic-id) 참고).
- `orderBy`에는 조회와 마찬가지로 정렬 람다나 `SortSpec`을 지정합니다([4. 정렬과 페이징](04-order-paging.md) 참고).
- `limit` 없는 `orderBy`는 오류(`IllegalStateException`)입니다. 이 경우 순서가 아무 역할을 하지 않기 때문입니다.

```kotlin
// the first rows of qb.from(Order::class.java).where { same }.orderBy { same }; the id is the last sort key
val d = qb.delete(Order::class.java)
    .where { it.status == 1 }
    .orderBy { o, by -> by.asc(o.price) }
    .limit(1)
// HQL: delete from Order e0 where e0.id in (select t.id from (select x0.id as id from Order x0 where x0.status = 1 order by x0.price asc, x0.id asc limit 1) t)
val deleted = d.execute()
```

같은 문장을 Java로 작성한 테스트가 [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)에 있습니다(영역 `delete-limit`).

**데이터베이스별 SQL (Java 테스트 실행 결과, Kotlin도 같은 HQL을 만듦)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `delete-limit`:

```sql
-- H2
delete from orders o1_0
where o1_0.id in (select t1_0.id from (select o2_0.id from orders o2_0 where o2_0.status=1 order by o2_0.price,o2_0.id fetch first 1 rows only) t1_0(id))

-- MariaDB
delete o1_0 from orders o1_0
where o1_0.id in (select t1_0.id from (select o2_0.id id from orders o2_0 where o2_0.status=1 order by o2_0.price,o2_0.id limit 1) t1_0)

-- Oracle
delete from orders o1_0
where o1_0.id in (select t1_0.id from (select o2_0.id id from orders o2_0 where o2_0.status=1 order by o2_0.price,o2_0.id fetch first 1 rows only) t1_0)
```

lambdaQL은 limit을 `e0.id in (select t.id from (select x0.id as id from X x0 ... order by ... limit n) t)`로 렌더링합니다.

- 안쪽 select는 조인을 포함해 조회 규칙을 따릅니다(결정 D36). 따라서 이 문장은 조회가 반환하는 행과 정확히 같은 행을 삭제합니다.
- 파생 테이블 `t`는 MariaDB를 위한 것입니다. 파생 테이블이 없으면 MariaDB는 `in` 서브쿼리의 `limit`(오류 1235)과 대상 테이블을 읽는 서브쿼리(오류 1093)를 거부합니다.
- H2와 Oracle은 `fetch first 1 rows only`, MariaDB는 `limit 1`로 씁니다.

<a id="delete-exactly-one-row-executeone"></a>
## 정확히 한 행 삭제: `executeOne()`

`executeOne()`은 **1행 보호**를 적용해 delete를 실행합니다. 조건이 한 행에 맞아야 하는 경우(예: 유일 키)에 `execute()` 대신 사용하십시오.

1. lambdaQL이 조건에 맞는 행의 id를 최대 2개 읽습니다.
2. id가 없으면 아무것도 변경하지 않고 0을 반환합니다.
3. id가 2개이면 아무것도 변경하지 않고 JPA `NonUniqueResultException`을 던집니다.
4. id가 1개이면 `id = :p and (조건)`으로 그 행을 삭제하고 1을 반환합니다. 그사이 그 행이 조건에서 벗어났으면 0을 반환합니다.

문장에 id 하나를 지정하므로 2행 이상을 변경하지 않습니다. 이 보호는 롤백에 의존하지 않습니다.

```kotlin
// one row matches: it is deleted
val one = qb.delete(Order::class.java).where { it.id == id }.executeOne()
// two or more rows match: nothing is deleted, NonUniqueResultException
qb.delete(Order::class.java).where { it.price!! > 100.0 }.executeOne()
```

같은 문장을 Java로 작성한 테스트가 [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)에 있습니다(영역 `execute-one`). id는 NULL이 될 수 없으므로 `it.id == id`는 단순한 `e0.id = :p1`이 됩니다([3.6.2](03-where.md#362--compares-values) 참고).

- `executeOne()`을 `orderBy`나 `limit`과 함께 쓰면 오류(`IllegalStateException`)입니다.
- `limit`과 `executeOne()`은 아직 복합 키 엔티티를 지원하지 않습니다.
- Spring 리포지토리에서는 `deleteOne { ... }`이 같은 동작을 하며, `IncorrectResultSizeDataAccessException`을 던집니다([12.4](12-spring-data-jpa.md#one-row-protection-deleteone-and-updateone) 참고).

---

---

[7. 수정, 삽입, 삭제 (Kotlin)](07-dml.md) | [7.2 수정](07-2-update.md) | **7.4 삭제** | [7.5 삽입](07-5-insert.md) | [7.6 업서트](07-6-upsert.md) | [Java](../java/07-4-delete.md)
