<a id="75-insert"></a>
# 7.5 삽입 (Kotlin)

[7. 수정, 삽입, 삭제 (Kotlin)](07-dml.md) | [7.2 수정](07-2-update.md) | [7.4 삭제](07-4-delete.md) | **7.5 삽입** | [7.6 업서트](07-6-upsert.md) | [Java](../java/07-5-insert.md)

---


<a id="one-row"></a>
## 한 행

[`ManualKotlinDmlTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt), 영역 `insert`:

```kotlin
val i = qb.insert(Archive::class.java)
    .values { it.id = 100L; it.price = 9.5; it.customerName = "Kim" }
assertEquals("insert into Archive (id, price, customerName) values (100L, 9.5, 'Kim')", i.toHql()[0].hql())
val inserted = i.execute()
// a generated id (IDENTITY) can be left out
val generated = qb.insert(IdOrder::class.java).values { it.price = 1.0; it.status = 1 }.execute()
// the values cannot read the new row
val error = assertThrows(LambdaQLException::class.java) {
    qb.insert(Archive::class.java).values { it.id = 1L; it.price = it.price }
}
```

**SQL 블록 읽는 법.** `…`는 줄여 쓴 select 목록(엔티티의 모든 컬럼)이고, `?`는 바인드 파라미터입니다. 각 줄은 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)에서 나옵니다. 이 테스트는 각 쿼리를 Hibernate 7.4로 H2 2.3, MariaDB 10.11, Oracle 23.26에서 실행하며, Hibernate의 SQL이 바뀌면 실패합니다. 이 테스트는 Java 람다를 사용합니다. 의미가 같은 Kotlin 람다는 같은 HQL을 만들므로 SQL도 같습니다.

**데이터베이스별 SQL (Java 테스트 실행 결과, Kotlin도 같은 HQL을 만듦)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `insert`:

```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price,customer_name)
values (100,9.5,'Kim')
```

람다에 적은 상수는 리터럴로 유지됩니다(결정 D37). 값에서 새 행을 읽을 수 없습니다. `it.price = it.price`는 오류입니다.
테스트는 삽입한 행을 다시 읽어 확인합니다.

`val` 속성은 `Dml.value`로 한 행에 씁니다(결정 D49, [7.3](07-dml.md#73-two-ways-to-assign-property-or-dmlvalue) 참고).

[`ManualKotlinDmlTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt), 영역 `insert-value`:

```kotlin
// properties without a setter (val): Dml.value
val i = qb.insert(Archive::class.java).values { a -> value(a.id, id); value(a.price, 1.5) }
assertEquals("insert into Archive (id, price) values (:p1, 1.5)", i.toHql()[0].hql())
```

<a id="a-subquery-in-a-value"></a>
### 값 안의 서브쿼리

`values { a, sub -> ... }`처럼 인자가 둘인 람다를 쓰면 두 번째 인자 `sub`로 서브쿼리를 만들 수 있습니다(결정 D49).
upsert의 `values { a, sub -> ... }`와 `onConflict { ... }.update { a, sub -> ... }`도 같습니다([7.6](07-6-upsert.md#your-own-change-onconflictkeyupdate) 참고).

[`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt), 영역 `insert-values-sub`:

```kotlin
// values { a, sub -> ... }: a subquery can read captured values, not the new row (D49)
val i = qb.insert(Archive::class.java)
    .values { a, sub -> a.id = 100L; a.price = sub.from(Item::class.java).sum { it.price } }
assertEquals("insert into Archive (id, price) values (100L, (select sum(s1.price) from Item s1))", i.toHql()[0].hql())
```

- 다른 테이블의 집계(캡처 파라미터가 있든 없든), 여러 행, 대상 테이블 자신을 읽는 서브쿼리(MariaDB 오류 1093 없음)가 모두 동작합니다(H2, MariaDB 10.11, Oracle 23.26에서 실측).
- 서브쿼리는 캡처 값을 읽을 수 있지만 새로 삽입하는 행은 읽을 수 없습니다.
- 리스트 형태에서도 서브쿼리를 쓸 수 있습니다([리스트에서 여러 행](#many-rows-from-a-list) 참고).
- upsert의 삽입 값과 `do update set` 안의 서브쿼리도 동작합니다. Oracle에서는 Hibernate가 `merge`로 실행합니다(실측). 예제는 [7.6](07-6-upsert.md#your-own-change-onconflictkeyupdate)의 영역 `upsert-sub`에 있습니다.

<a id="many-rows-from-a-list"></a>
## 리스트에서 여러 행

리스트의 요소마다 한 행을 삽입합니다. 두 번째 람다 인자가 요소입니다.

[`ManualKotlinDmlTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt), 영역 `insert-list`:

```kotlin
val lines = listOf(Line(201L, 1.0), Line(202L, 2.0))      // data class Line(val id: Long, val price: Double)
val i = qb.insert(Archive::class.java).values(lines) { a, l -> a.id = l.id; a.price = l.price }
assertEquals("insert into Archive (id, price) values (:p1, :p2), (:p3, :p4)", i.toHql()[0].hql())
```

**데이터베이스별 SQL (Java 테스트 실행 결과, Kotlin도 같은 HQL을 만듦)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `insert-list`:

```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price)
values (?,?), (?,?)
```

lambdaQL은 요소의 속성을 Java에서 읽으므로, 모든 값이 바인드 파라미터가 됩니다.
`Line`은 테스트 클래스 안에 선언한 데이터 클래스입니다. VALUES 행이 여러 개인 문장 하나를 씁니다. 리스트가 길면 문장 하나에 값이 500개 이하가 되도록 나눕니다([7.8](07-dml.md#batching-for-many-rows) 참고). 빈 리스트는 SQL을 실행하지 않고 0을 반환합니다.

**리스트 형태의 서브쿼리.** `values(list) { a, l, sub -> ... }`처럼 세 번째 인자로 서브쿼리를 만들 수 있습니다(결정 D49).
서브쿼리는 요소의 값을 읽을 수 있습니다. 그 값은 쿼리 전에 읽어 행마다 파라미터 하나로 바인딩합니다. 새로 삽입하는 행은 읽을 수 없습니다(세 DB에서 실측).

[`ManualKotlinDmlTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt), 영역 `insert-list-sub`:

```kotlin
// one row per element; the subquery reads the element's value (D49)
val i = qb.insert(Archive::class.java).values(refs) { a, r, sub ->
    a.id = r.id
    a.price = sub.from(Item::class.java).where { x -> x.order?.id == r.orderId }.sum { x -> x.price }
}
assertEquals("insert into Archive (id, price) values (:p1, (select sum(s1.price) from Item s1 where s1.order.id = :p2)),"
    + " (:p3, (select sum(s1.price) from Item s1 where s1.order.id = :p4))", i.toHql()[0].hql())
```

`Ref`는 `data class Ref(val id: Long, val orderId: Long?)`입니다.

<a id="generated-id"></a>
## 생성되는 id

id가 IDENTITY나 SEQUENCE를 사용하면 id를 생략할 수 있습니다(규칙 U12).
첫 번째 예제의 `IdOrder` 줄이 이 경우에 해당합니다. `IdOrder`의 id는 생성되는 값입니다.

<a id="insert--select"></a>
## insert ... select

다른 엔티티에서 행을 복사합니다. `select` 람다는 대상(`a`)과 소스(`o`)를 받습니다.

[`ManualKotlinDmlTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt), 영역 `insert-select`:

```kotlin
val i = qb.insert(Archive::class.java)
    .from(Order::class.java)
    .where { it.status.toInt() == 1 }
    .select { a, o -> a.id = o.id; a.price = o.price; a.customerName = o.customer?.name }
// a path in the select list: left join, so orders without a customer stay (U10)
assertEquals("insert into Archive (id, price, customerName) select e0.id, e0.price, j1.name"
    + " from Order e0 left join e0.customer j1 where e0.status = 1", i.toHql()[0].hql())
```

**데이터베이스별 SQL (Java 테스트 실행 결과, Kotlin도 같은 HQL을 만듦)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `insert-select`:

```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price,customer_name)
select o1_0.id,o1_0.price,c1_0.name
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where o1_0.status=1
```

lambdaQL은 소스 경로에 명시적인 `left join`을 사용하므로 고객이 없는 주문도 빠지지 않습니다(규칙 U10). SQL은 세 데이터베이스에서 같습니다.

`val` 속성의 대상에는 `Dml.value`를 씁니다. 첫 번째 인자는 대상 `a`의 속성입니다.

[`ManualKotlinDmlTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt), 영역 `insert-select-value`:

```kotlin
// Dml.value in insert ... select: the first argument is a property of the target a
val i = qb.insert(Archive::class.java).from(Order::class.java).where { it.status.toInt() == 3 }
    .select { a, o -> value(a.id, o.id); value(a.customerName, o.customer?.name) }
assertEquals("insert into Archive (id, customerName) select e0.id, j1.name from Order e0 left join e0.customer j1"
    + " where e0.status = 3", i.toHql()[0].hql())
```

**서브쿼리.** 소스의 `where`는 두 번째 인자로, `select`는 세 번째 인자로 서브쿼리의 시작점 `sub`를 받습니다(결정 D49).

[`ManualKotlinDmlTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt), 영역 `insert-select-sub`:

```kotlin
// where { o, sub -> ... } on the source, and select { a, o, sub -> ... }: the third parameter starts a subquery
val i = qb.insert(Archive::class.java).from(Order::class.java)
    .where { o, sub -> sub.from(Item::class.java).where { x -> x.order?.id == o.id }.exists() }
    .select { a, o, sub -> a.id = o.id; a.price = sub.from(Item::class.java).where { x -> x.order?.id == o.id }.sum { x -> x.price } }
assertEquals("insert into Archive (id, price) select e0.id, (select sum(s2.price) from Item s2 where s2.order.id = e0.id)"
    + " from Order e0 where exists (select 1 from Item s1 where s1.order.id = e0.id)", i.toHql()[0].hql())
```

<a id="insert--select-with-aggregates"></a>
## 집계를 사용한 insert ... select

[6. 조회, 집계, 그룹화](06-select-group.md)의 `groupBy`, `having`, `Agg` 함수를 사용합니다.
호출 순서는 `from(...)`, `where { }`, `groupBy { }`, `having { }`, `select { }`입니다. `where`, `groupBy`, `having`은 생략할 수 있습니다.

[`ManualKotlinDmlTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt), 영역 `insert-group`:

```kotlin
// INSERT ... SELECT with groupBy: one summary row per customer
val i = qb.insert(Summary::class.java)
    .from(Order::class.java)
    .where { it.customer != null }
    .groupBy { it.customer?.id }
    .having { Agg.count() >= 2 }
    .select { s, o -> s.customerId = o.customer?.id; s.total = Agg.sum(o.price); s.cnt = Agg.count() }
assertEquals("insert into Summary (customerId, total, cnt) select e0.customer.id, sum(e0.price), count(*) from Order e0"
    + " where e0.customer is not null group by e0.customer.id having count(*) >= 2L", i.toHql()[0].hql())
```

**데이터베이스별 SQL (Java 테스트 실행 결과, Kotlin도 같은 HQL을 만듦)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `insert-group`:

```sql
-- H2, MariaDB, Oracle (same)
insert into summary(customer_id,total,cnt)
select o1_0.customer_id,sum(o1_0.price),count(*)
from orders o1_0
where o1_0.customer_id is not null
group by o1_0.customer_id
having count(*)>=2
```

- 집계가 아닌 값은 모두 `groupBy` 키여야 합니다(규칙 J3). 그렇지 않으면 lambdaQL은 분석 시점에 `TranslationException`(`LQL-3035`)을 던집니다.
- `where`에는 `Agg`를 쓸 수 없습니다. `having`을 사용하십시오.

---

---

[7. 수정, 삽입, 삭제 (Kotlin)](07-dml.md) | [7.2 수정](07-2-update.md) | [7.4 삭제](07-4-delete.md) | **7.5 삽입** | [7.6 업서트](07-6-upsert.md) | [Java](../java/07-5-insert.md)
