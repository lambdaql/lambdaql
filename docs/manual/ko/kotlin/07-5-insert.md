<a id="75-insert"></a>
# 7.5 삽입 (Kotlin)

> [!NOTE]
> **상태: 설계됨, 아직 구현되지 않음.**
> 이 장은 확정된 설계입니다(결정 D28, 마일스톤 M10).
> Kotlin 문법은 컴파일되며(kotlinc 2.3.21), SQL은 Hibernate 6.6.5와 함께 H2, MariaDB, Oracle에서 확인했습니다.
> 지금 lambdaQL에서는 동작하지 **않습니다**. 현재는 분석 코어와 `where` + `toList()`만 동작합니다
> (H2, MariaDB, Oracle에서 테스트함). 전체 상태는 [트랙 README](README.md)를 참고하십시오.

[7. 수정, 삽입, 삭제 (Kotlin)](07-dml.md) | [7.2 수정](07-2-update.md) | [7.4 삭제](07-4-delete.md) | **7.5 삽입** | [Java](../java/07-5-insert.md)

---


<a id="one-row"></a>
## 한 행

```kotlin
qb.insert(Order::class.java).values { it.id = id; it.price = p; it.status = 1 }.execute()

// val 속성에는 by 사용
qb.insert(Order::class.java).values { o, by -> by[o.id] = id; by[o.status] = 1 }.execute()
```

<a id="many-rows-from-a-list"></a>
## 리스트에서 여러 행

리스트의 요소마다 한 행입니다. 두 번째 람다 인자가 요소입니다.

```kotlin
val lines = listOf(Line(1L, 10.0), Line(2L, 20.0))

qb.insert(Order::class.java)
    .values(lines) { o, l -> o.id = l.id; o.price = l.price; o.status = 1 }
    .execute()
```

lambdaQL은 VALUES 행이 여러 개인 문장 하나를 씁니다. 리스트가 크면 **바인드 값의 개수**로 작업을 나눕니다([7.7](07-dml.md#batching-for-many-rows) 참고).

<a id="generated-id"></a>
## 생성되는 id

id가 IDENTITY나 SEQUENCE를 사용하면 id를 생략할 수 있습니다(규칙 U12).

```kotlin
qb.insert(IdOrder::class.java).values { it.price = p; it.status = 1 }.execute()
```

## Insert ... select

다른 엔티티에서 행을 복사합니다. `select` 람다는 대상(`a`)과 소스(`o`)를 받습니다.

```kotlin
qb.insert(Archive::class.java).from(Order::class.java).where { it.status == 9 }
    .select { a, o -> a.id = o.id; a.price = o.price; a.customerName = o.customer?.name }
    .execute()
```

```sql
insert into Archive (id, price, customerName)
select o.id, o.price, c.name from Order o left join o.customer c where o.status = 9
```

**SQL 블록 읽는 법.** `…`는 줄여 쓴 select 목록(엔티티의 모든 컬럼)이고, `?`는 바인드 파라미터입니다. [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java)는 이 페이지의 HQL을 Hibernate에 직접 넘기고, H2 2.3, MariaDB 10.11, Oracle 23.26(Hibernate 6.6.5)에서 나온 SQL을 기록합니다. lambdaQL은 아직 이 쿼리들을 만들지 않습니다. Hibernate의 SQL이 바뀌면 테스트가 실패합니다.

**데이터베이스별 SQL (예정)** — 예정된 HQL을 Hibernate가 번역한 것으로, `PlannedSqlTest`가 기록했습니다. lambdaQL은 아직 이 쿼리를 만들지 않습니다. `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)의 예제 `07-insert-select`:

```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price,customer_name)
select o1_0.id,o1_0.price,c1_0.name
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where o1_0.status=9
```

lambdaQL은 소스 경로에 명시적인 `left join`을 사용하므로 고객이 없는 주문도 빠지지 않습니다(규칙 U10).

`by`를 쓰면 컨텍스트 객체는 세 번째 인자입니다.

```kotlin
qb.insert(Archive::class.java).from(Order::class.java).where { it.status == 9 }
    .select { a, o, by -> by[a.id] = o.id; by[a.customerName] = o.customer?.name }
    .execute()
```

<a id="insert--select-with-aggregates"></a>
## 집계를 사용한 Insert ... select

[6. 조회, 집계, 그룹화](06-select-group.md)의 `groupBy`와 `Agg` 함수를 사용하십시오.

```kotlin
qb.insert(Summary::class.java).from(Order::class.java).groupBy { it.customer?.id }
    .select { s, o -> s.customerId = o.customer?.id; s.total = Agg.sum(o.price); s.cnt = Agg.count() }
    .execute()
```

---

---

[7. 수정, 삽입, 삭제 (Kotlin)](07-dml.md) | [7.2 수정](07-2-update.md) | [7.4 삭제](07-4-delete.md) | **7.5 삽입** | [Java](../java/07-5-insert.md)
