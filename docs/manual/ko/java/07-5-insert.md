<a id="75-insert"></a>
# 7.5 삽입(insert) (Java)

[7. 수정, 삽입, 삭제 (Java)](07-dml.md) | [7.2 수정(update)](07-2-update.md) | [7.4 삭제(delete)](07-4-delete.md) | **7.5 삽입(insert)** | [7.6 업서트(upsert)](07-6-upsert.md) | [Kotlin](../kotlin/07-5-insert.md)

---

<a id="one-row"></a>
## 한 행

<!-- region: DmlQueryTest.java#insert -->
```java
ExecutableInsert<Archive> i = qb.insert(Archive.class)
        .values(a -> { a.setId(100L); a.setPrice(9.5); a.setCustomerName("Kim"); });
assertEquals("insert into Archive (id, price, customerName) values (100L, 9.5, 'Kim')", i.toHql().get(0).hql());
int inserted = i.execute();
// a generated id (IDENTITY) can be left out
int generated = qb.insert(IdOrder.class).values(o -> { o.setPrice(1.0); o.setStatus(1); }).execute();
```

**데이터베이스별 SQL**

<!-- sql: insert -->
```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price,customer_name)
values (100,9.5,'Kim')
```

람다 표현식에 적은 상수는 리터럴로 유지됩니다. 값에서 새 행을 읽을 수 없습니다. `a.setPrice(a.getPrice())`는 오류입니다.

setter가 없는 엔티티는 `Dml.value`로 한 행을 삽입합니다([7.3](07-dml.md#73-two-ways-to-assign-setter-or-dmlvalue) 참고). 예제의 `Archive`에는 setter가 있지만, 같은 형태를 setter 없는 엔티티에도 씁니다.

<!-- region: ManualJavaDmlTest.java#insert-value -->
```java
ExecutableInsert<Archive> i = qb.insert(Archive.class)
        .values(a -> { value(a.getId(), id); value(a.getPrice(), 1.0); });
assertEquals("insert into Archive (id, price) values (:p1, 1.0)", i.toHql().get(0).hql());
int inserted = i.execute();
```

<a id="a-subquery-in-a-value"></a>
### 값 안의 서브쿼리

`values((a, sub) -> ...)`처럼 인자가 둘인 람다 표현식을 쓰면 두 번째 인자 `sub`로 서브쿼리를 만들 수 있습니다.
upsert의 `values((a, sub) -> ...)`와 `onConflict(...).update((a, sub) -> ...)`도 같습니다([7.6](07-6-upsert.md#your-own-change-onconflictkeyupdate) 참고).

<!-- region: DmlQueryTest.java#insert-values-sub -->
```java
// values((a, sub) -> ...): a subquery can read captured values, not the new row
ExecutableInsert<Archive> i = qb.insert(Archive.class)
        .values((a, sub) -> {
            a.setId(100L);
            a.setPrice(sub.from(Item.class).where(x -> x.getOrder().getId() == first).sum(x -> x.getPrice()));
        });
assertEquals("insert into Archive (id, price) values (100L, (select sum(s1.price) from Item s1 where s1.order.id = :p1))",
        i.toHql().get(0).hql());
// upsert: a subquery in the inserted value and in the update on conflict
ExecutableInsert<Archive> up = qb.insert(Archive.class)
        .values((a, sub) -> { a.setId(100L); a.setPrice(sub.from(Item.class).sum(x -> x.getPrice())); })
        .onConflict(a -> a.getId())
        .update((a, sub) -> a.setPrice(sub.from(Order.class).max(o -> o.getPrice())));
assertEquals("insert into Archive e0 (id, price) values (100L, (select sum(s1.price) from Item s1))"
        + " on conflict(id) do update set price = (select max(s1.price) from Order s1)", up.toHql().get(0).hql());
```

- 다른 테이블의 집계(캡처 파라미터가 있든 없든), 여러 행, 대상 테이블 자신을 읽는 서브쿼리(MariaDB 오류 1093 없음)가 모두 동작합니다(H2, MariaDB 10.11, Oracle 23.26에서 실측).
- 서브쿼리는 캡처 값을 읽을 수 있지만 새로 삽입하는 행은 읽을 수 없습니다.
- 리스트 형태에서도 서브쿼리를 쓸 수 있습니다([리스트에서 여러 행](#many-rows-from-a-list) 참고).
- upsert의 삽입 값과 `do update set` 안의 서브쿼리도 동작합니다. Oracle에서는 Hibernate가 `merge`로 실행합니다(실측).

예제 코드: [`DmlQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)(`insert`, `insert-values-sub`), [`ManualJavaDmlTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaDmlTest.java)(`insert-value`).

<a id="many-rows-from-a-list"></a>
## 리스트에서 여러 행

리스트의 요소마다 한 행을 삽입합니다. 두 번째 람다 표현식 인자가 요소입니다.

<!-- region: DmlQueryTest.java#insert-list -->
```java
List<Line> lines = List.of(new Line(201, 1.0), new Line(202, 2.0));
ExecutableInsert<Archive> i = qb.insert(Archive.class).values(lines, (a, l) -> { a.setId(l.id()); a.setPrice(l.price()); });
assertEquals("insert into Archive (id, price) values (:p1, :p2), (:p3, :p4)", i.toHql().get(0).hql());
```

**데이터베이스별 SQL**

<!-- sql: insert-list -->
```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price)
values (?,?), (?,?)
```

lambdaQL은 요소의 getter를 Java에서 읽으므로, 모든 값이 바인드 파라미터가 됩니다.
VALUES 행이 여러 개인 문장 하나를 씁니다. 리스트가 길면 문장 하나에 값이 500개 이하가 되도록 나눕니다([7.8](07-dml.md#batching-for-many-rows) 참고). 빈 리스트는 SQL을 실행하지 않고 0을 반환합니다.

**리스트 형태의 서브쿼리.** `values(list, (a, l, sub) -> ...)`처럼 세 번째 인자로 서브쿼리를 만들 수 있습니다. `qb.upsert(X.class).values(list, (a, l, sub) -> ...)`도 같습니다.
서브쿼리는 요소의 값을 읽을 수 있습니다. 그 값은 Java에서 읽어 행마다 파라미터 하나로 바인딩합니다. 새로 삽입하는 행은 읽을 수 없습니다(세 DB에서 실측).

<!-- region: DmlQueryTest.java#insert-list-sub -->
```java
// one row per element; the subquery reads the element's value
ExecutableInsert<Archive> i = qb.insert(Archive.class).values(refs, (a, r, sub) -> {
    a.setId(r.id());
    a.setPrice(sub.from(Item.class).where(x -> x.getOrder().getId() == r.orderId()).sum(x -> x.getPrice()));
});
assertEquals("insert into Archive (id, price) values (:p1, (select sum(s1.price) from Item s1 where s1.order.id = :p2)),"
        + " (:p3, (select sum(s1.price) from Item s1 where s1.order.id = :p4))", i.toHql().get(0).hql());
```

예제 코드: [`DmlQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)(`insert-list`, `insert-list-sub`).

<a id="generated-id"></a>
## 생성되는 id

id가 IDENTITY나 SEQUENCE를 사용하면 id를 생략할 수 있습니다.
첫 번째 예제의 `IdOrder` 줄이 이 경우에 해당합니다. `IdOrder`의 id는 IDENTITY 방식입니다.

<a id="insert--select"></a>
## insert ... select

다른 엔티티에서 행을 복사합니다. `select` 람다 표현식은 대상(`a`)과 원본(`o`)을 받습니다.

<!-- region: DmlQueryTest.java#insert-select -->
```java
ExecutableInsert<Archive> i = qb.insert(Archive.class)
        .from(Order.class)
        .where(o -> o.getStatus() == 1)
        .select((a, o) -> { a.setId(o.getId()); a.setPrice(o.getPrice()); a.setCustomerName(o.getCustomer().getName()); });
// a path in the select list: left join, so orders without a customer stay
assertEquals("insert into Archive (id, price, customerName) select e0.id, e0.price, j1.name"
        + " from Order e0 left join e0.customer j1 where e0.status = 1", i.toHql().get(0).hql());
```

**데이터베이스별 SQL**

<!-- sql: insert-select -->
```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price,customer_name)
select o1_0.id,o1_0.price,c1_0.name
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where o1_0.status=1
```

lambdaQL은 원본 경로에 명시적인 `left join`을 사용하므로 고객이 없는 주문도 빠지지 않습니다. SQL은 세 데이터베이스에서 같습니다.

setter가 없는 대상 엔티티에는 `Dml.value`를 씁니다. 첫 번째 인자는 대상 `a`의 getter 호출입니다.

<!-- region: ManualJavaDmlTest.java#insert-select-value -->
```java
ExecutableInsert<Archive> i = qb.insert(Archive.class).from(Order.class).where(o -> o.getStatus() == 1)
        .select((a, o) -> { value(a.getId(), o.getId()); value(a.getCustomerName(), o.getCustomer().getName()); });
assertEquals("insert into Archive (id, customerName) select e0.id, j1.name"
        + " from Order e0 left join e0.customer j1 where e0.status = 1", i.toHql().get(0).hql());
int inserted = i.execute();
```

**서브쿼리.** 원본의 `where`는 두 번째 인자로, `select`는 세 번째 인자로 서브쿼리의 시작점 `sub`를 받습니다.

<!-- region: DmlQueryTest.java#insert-select-sub -->
```java
// where((o, sub) -> ...) on the source, and select((a, o, sub) -> ...): the third parameter starts a subquery
ExecutableInsert<Archive> i = qb.insert(Archive.class).from(Order.class)
        .where((o, sub) -> sub.from(Item.class).where(x -> x.getOrder().getId() == o.getId()).exists())
        .select((a, o, sub) -> {
            a.setId(o.getId());
            a.setPrice(sub.from(Item.class).where(x -> x.getOrder().getId() == o.getId()).sum(x -> x.getPrice()));
        });
assertEquals("insert into Archive (id, price) select e0.id, (select sum(s2.price) from Item s2 where s2.order.id = e0.id)"
        + " from Order e0 where exists (select 1 from Item s1 where s1.order.id = e0.id)", i.toHql().get(0).hql());
```

예제 코드: [`DmlQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)(`insert-select`, `insert-select-sub`), [`ManualJavaDmlTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaDmlTest.java)(`insert-select-value`).

<a id="insert--select-with-aggregates"></a>
## 집계를 사용한 insert ... select

[6. 조회, 집계, 그룹화](06-select-group.md)의 `groupBy`, `having`, `Agg` 함수를 사용합니다.
호출 순서는 `from(...)`, `where(...)`, `groupBy(...)`, `having(...)`, `select(...)`입니다. `where`, `groupBy`, `having`은 생략할 수 있습니다.

<!-- region: ProjectionTest.java#insert-group -->
```java
// INSERT ... SELECT with groupBy: one summary row per customer
ExecutableInsert<Summary> i = qb.insert(Summary.class)
        .from(Order.class)
        .where(o -> o.getCustomer() != null)
        .groupBy(o -> o.getCustomer().getId())
        .having(o -> Agg.count() >= 2)
        .select((s, o) -> { s.setCustomerId(o.getCustomer().getId()); s.setTotal(Agg.sum(o.getPrice())); s.setCnt(Agg.count()); });
assertEquals("insert into Summary (customerId, total, cnt) select e0.customer.id, sum(e0.price), count(*) from Order e0"
        + " where e0.customer is not null group by e0.customer.id having count(*) >= 2L", i.toHql().get(0).hql());
```

**데이터베이스별 SQL**

<!-- sql: insert-group -->
```sql
-- H2, MariaDB, Oracle (same)
insert into summary(customer_id,total,cnt)
select o1_0.customer_id,sum(o1_0.price),count(*)
from orders o1_0
where o1_0.customer_id is not null
group by o1_0.customer_id
having count(*)>=2
```

- 집계가 아닌 값은 모두 `groupBy` 키여야 합니다. 그렇지 않으면 lambdaQL은 분석 시점에 `TranslationException`(`LQL-3035`)을 던집니다.
- `where`에는 `Agg`를 쓸 수 없습니다. `having`을 사용하십시오.
- 삽입된 `Summary` 행은 Java에서 같은 기준으로 그룹화한 값과 일치합니다.

---

[7. 수정, 삽입, 삭제 (Java)](07-dml.md) | [7.2 수정(update)](07-2-update.md) | [7.4 삭제(delete)](07-4-delete.md) | **7.5 삽입(insert)** | [7.6 업서트(upsert)](07-6-upsert.md) | [Kotlin](../kotlin/07-5-insert.md)

예제 코드: [`ProjectionTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java)(`insert-group`).

