<a id="75-insert"></a>
# 7.5 삽입 (Kotlin)

> [!NOTE]
> **상태: 구현됨, 테스트함**(H2 2.3, MariaDB 10.11, Oracle 23.26, 마일스톤 M10). `insert ... select`의 `groupBy`, `having`, `Agg`(마일스톤 M9)도 포함합니다. 테스트는 대부분 **Java** 람다를 사용합니다. `values`를 쓰는 insert는 Kotlin 람다로도 테스트했습니다([`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt), **(테스트됨)** 표시).
> **아직 구현되지 않음:** 리스트 형태 `values(list) { a, l -> ... }` 안의 서브쿼리.
> Kotlin 코드는 테스트된 Java 문장을 Kotlin 문법으로 옮긴 것이고, HQL은 [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)와 [`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java)의 Java 테스트에서 가져왔습니다. HQL이 없는 예제는 설계를 따릅니다.
> 전체 상태는 [트랙 README](README.md)를 참고하십시오.

[7. 수정, 삽입, 삭제 (Kotlin)](07-dml.md) | [7.2 수정](07-2-update.md) | [7.4 삭제](07-4-delete.md) | **7.5 삽입** | [7.6 업서트](07-6-upsert.md) | [Java](../java/07-5-insert.md)

---


<a id="one-row"></a>
## 한 행

```kotlin
val i = qb.insert(Archive::class.java)
    .values { it.id = 100L; it.price = 9.5; it.customerName = "Kim" }
// HQL: insert into Archive (id, price, customerName) values (100L, 9.5, 'Kim')
val inserted = i.execute()
// a generated id (IDENTITY) can be left out
val generated = qb.insert(IdOrder::class.java).values { it.price = 1.0; it.status = 1 }.execute()
```

**SQL 블록 읽는 법.** `…`는 줄여 쓴 select 목록(엔티티의 모든 컬럼)이고, `?`는 바인드 파라미터입니다. 각 줄은 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)에서 나옵니다. 이 테스트는 각 쿼리를 Hibernate 6.6.5로 H2 2.3, MariaDB 10.11, Oracle 23.26에서 실행하며, Hibernate의 SQL이 바뀌면 실패합니다. 이 테스트는 Java 람다를 사용합니다. 의미가 같은 Kotlin 람다는 같은 HQL을 만들므로 SQL도 같습니다.

**데이터베이스별 SQL (Java 테스트 실행 결과, Kotlin도 같은 HQL을 만듦)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `insert`:

```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price,customer_name)
values (100,9.5,'Kim')
```

**(테스트됨)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt), 영역 `insert-delete`:

```kotlin
val i = qb.insert(Archive::class.java).values { it.id = 100L; it.price = 9.5; it.customerName = "Kim" }
assertEquals(1, i.execute())
val d = qb.delete(Order::class.java).where { o, sub -> !sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
assertEquals("delete from Order e0 where not exists (select 1 from Item s1 where s1.order.id = e0.id)", d.toHql().hql())
```

앞의 두 줄이 Kotlin 람다로 행 하나를 삽입합니다. 삭제 부분은 [7.4](07-4-delete.md)에서 설명합니다.
더 많은 문장을 Java로 작성한 테스트가 [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)에 있습니다(H2, MariaDB, Oracle). 그 예제는 [이 페이지의 Java 버전](../java/07-5-insert.md)을 참고하십시오.

람다에 적은 상수는 리터럴로 유지됩니다(결정 D37). 값에서 새 행을 읽을 수 없습니다. `it.price = it.price`는 오류입니다.

`val` 속성은 `Dml.value`로 한 행에 씁니다(결정 D49, [7.3](07-dml.md#73-two-ways-to-assign-property-or-dmlvalue) 참고).

```kotlin
qb.insert(Order::class.java).values { o -> value(o.id, id); value(o.status, 1) }.execute()
```

<a id="a-subquery-in-a-value"></a>
### 값 안의 서브쿼리

`values { a, sub -> ... }`처럼 인자가 둘인 람다를 쓰면 두 번째 인자 `sub`로 서브쿼리를 만들 수 있습니다(결정 D49).
upsert의 `values { a, sub -> ... }`와 `onConflict { ... }.update { a, sub -> ... }`도 같습니다([7.6](07-6-upsert.md#your-own-change-onconflictkeyupdate) 참고).

**(테스트됨)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt), 영역 `insert-values-sub`:

```kotlin
// values { a, sub -> ... }: a subquery can read captured values, not the new row (D49)
val i = qb.insert(Archive::class.java)
    .values { a, sub -> a.id = 100L; a.price = sub.from(Item::class.java).sum { it.price } }
assertEquals("insert into Archive (id, price) values (100L, (select sum(s1.price) from Item s1))", i.toHql()[0].hql())
```

- 다른 테이블의 집계(캡처 파라미터가 있든 없든), 여러 행, 대상 테이블 자신을 읽는 서브쿼리(MariaDB 오류 1093 없음)가 모두 동작합니다(H2, MariaDB 10.11, Oracle 23.26에서 실측).
- 서브쿼리는 캡처 값을 읽을 수 있지만 새로 삽입하는 행은 읽을 수 없습니다.
- 리스트 형태 `values(list) {{ a, l -> ... }}` 안의 서브쿼리는 아직 지원하지 않습니다.
- upsert의 삽입 값과 `do update set` 안의 서브쿼리도 동작합니다. Oracle에서는 Hibernate가 `merge`로 실행합니다(실측). upsert 형태는 Java 테스트([`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java), 영역 `insert-values-sub`)가 확인합니다.

<a id="many-rows-from-a-list"></a>
## 리스트에서 여러 행

리스트의 요소마다 한 행을 삽입합니다. 두 번째 람다 인자가 요소입니다.

```kotlin
val lines = listOf(Line(201L, 1.0), Line(202L, 2.0))
val i = qb.insert(Archive::class.java).values(lines) { a, l -> a.id = l.id; a.price = l.price }
// HQL: insert into Archive (id, price) values (:p1, :p2), (:p3, :p4)
```

**데이터베이스별 SQL (Java 테스트 실행 결과, Kotlin도 같은 HQL을 만듦)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `insert-list`:

```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price)
values (?,?), (?,?)
```

lambdaQL은 요소의 속성을 Java에서 읽으므로, 모든 값이 바인드 파라미터가 됩니다.
VALUES 행이 여러 개인 문장 하나를 씁니다. 리스트가 길면 문장 하나에 값이 500개 이하가 되도록 나눕니다([7.8](07-dml.md#batching-for-many-rows) 참고). 빈 리스트는 SQL을 실행하지 않고 0을 반환합니다.

<a id="generated-id"></a>
## 생성되는 id

id가 IDENTITY나 SEQUENCE를 사용하면 id를 생략할 수 있습니다(규칙 U12).
첫 번째 예제의 `IdOrder` 줄이 이 경우에 해당합니다. `IdOrder`의 id는 생성되는 값입니다.

<a id="insert--select"></a>
## Insert ... select

다른 엔티티에서 행을 복사합니다. `select` 람다는 대상(`a`)과 소스(`o`)를 받습니다.

```kotlin
val i = qb.insert(Archive::class.java)
    .from(Order::class.java)
    .where { it.status == 1 }
    .select { a, o -> a.id = o.id; a.price = o.price; a.customerName = o.customer?.name }
// a path in the select list: left join, so orders without a customer stay (U10)
// HQL: insert into Archive (id, price, customerName) select e0.id, e0.price, j1.name from Order e0 left join e0.customer j1 where e0.status = 1
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

```kotlin
qb.insert(Archive::class.java).from(Order::class.java).where { it.status == 9 }
    .select { a, o -> value(a.id, o.id); value(a.customerName, o.customer?.name) }
    .execute()
```

**서브쿼리.** 소스의 `where`는 두 번째 인자로, `select`는 세 번째 인자로 서브쿼리의 시작점 `sub`를 받습니다(결정 D49). Java 테스트([`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java), 영역 `insert-select-sub`)가 이를 확인합니다. 다음은 설계 예제입니다.

```kotlin
qb.insert(Archive::class.java).from(Order::class.java)
    .where { o, sub -> sub.from(Item::class.java).where { x -> x.order?.id == o.id }.exists() }
    .select { a, o, sub -> a.id = o.id; a.price = sub.from(Item::class.java).where { x -> x.order?.id == o.id }.sum { it.price } }
    .execute()
```

<a id="insert--select-with-aggregates"></a>
## 집계를 사용한 insert ... select

[6. 조회, 집계, 그룹화](06-select-group.md)의 `groupBy`, `having`, `Agg` 함수를 사용합니다.
호출 순서는 `from(...)`, `where { }`, `groupBy { }`, `having { }`, `select { }`입니다. `where`, `groupBy`, `having`은 생략할 수 있습니다.

```kotlin
// INSERT ... SELECT with groupBy: one summary row per customer
val i = qb.insert(Summary::class.java)
    .from(Order::class.java)
    .where { it.customer != null }
    .groupBy { it.customer?.id }
    .having { Agg.count() >= 2 }
    .select { s, o -> s.customerId = o.customer?.id; s.total = Agg.sum(o.price); s.cnt = Agg.count() }
// HQL: insert into Summary (customerId, total, cnt) select e0.customer.id, sum(e0.price), count(*) from Order e0
//      where e0.customer is not null group by e0.customer.id having count(*) >= 2L
```

같은 문장을 Java로 작성한 테스트가 [`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java)에 있습니다(영역 `insert-group`).

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
