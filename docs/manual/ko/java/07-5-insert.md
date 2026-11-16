<a id="75-insert"></a>
# 7.5 삽입(insert) (Java)

> [!NOTE]
> **상태: 구현됨, 테스트함**(H2 2.3, MariaDB 10.11, Oracle 23.26, 마일스톤 M10).
> **아직 구현되지 않음:** `insert ... select`의 집계(마일스톤 M9), `insert ... select`의 `by.set` 형태.
> **(테스트됨)** 표시가 있는 예제는 [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)에서 그대로 가져온 것입니다. 나머지 예제는 설계를 따르며 아직 테스트가 없습니다.
> 전체 상태는 [Java 트랙 README](README.md#status)를 참고하십시오.

[7. 수정, 삽입, 삭제 (Java)](07-dml.md) | [7.2 수정(update)](07-2-update.md) | [7.4 삭제(delete)](07-4-delete.md) | **7.5 삽입(insert)** | [7.6 업서트(upsert)](07-6-upsert.md) | [Kotlin](../kotlin/07-5-insert.md)

---


<a id="one-row"></a>
## 한 행

**(테스트됨)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java), 영역 `insert`:

```java
ExecutableInsert<Archive> i = qb.insert(Archive.class)
        .values(a -> { a.setId(100L); a.setPrice(9.5); a.setCustomerName("Kim"); });
assertEquals("insert into Archive (id, price, customerName) values (100L, 9.5, 'Kim')", i.toHql().get(0).hql());
int inserted = i.execute();
// a generated id (IDENTITY) can be left out
int generated = qb.insert(IdOrder.class).values(o -> { o.setPrice(1.0); o.setStatus(1); }).execute();
```

**SQL 블록 읽는 법.** `…`는 줄여 쓴 select 목록(엔티티의 모든 컬럼)이고, `?`는 바인드 파라미터입니다. 각 줄은 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)에서 나옵니다. 이 테스트는 각 쿼리를 Hibernate 6.6.5로 H2 2.3, MariaDB 10.11, Oracle 23.26에서 실행하며, Hibernate의 SQL이 바뀌면 실패합니다. **(예정)** 표시가 있는 블록은 [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java)에서 나옵니다. lambdaQL이 아직 이 문장을 만들지 않으므로, 이 테스트가 예정된 HQL을 Hibernate에 직접 넘깁니다.

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `insert`:

```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price,customer_name)
values (100,9.5,'Kim')
```

람다에 적은 상수는 리터럴로 유지됩니다(결정 D37). 값에서 새 행을 읽을 수 없습니다. `a.setPrice(a.getPrice())`는 오류입니다.

한 행에도 `by.set` 형태를 쓸 수 있습니다.

```java
qb.insert(Order.class).values((o, by) -> by.set(o.getId(), id).set(o.getStatus(), 1)).execute();
```

<a id="many-rows-from-a-list"></a>
## 리스트에서 여러 행

리스트의 요소마다 한 행을 삽입합니다. 두 번째 람다 인자가 요소입니다.

**(테스트됨)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java), 영역 `insert-list`:

```java
List<Line> lines = List.of(new Line(201, 1.0), new Line(202, 2.0));
ExecutableInsert<Archive> i = qb.insert(Archive.class).values(lines, (a, l) -> { a.setId(l.id()); a.setPrice(l.price()); });
assertEquals("insert into Archive (id, price) values (:p1, :p2), (:p3, :p4)", i.toHql().get(0).hql());
```

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `insert-list`:

```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price)
values (?,?), (?,?)
```

lambdaQL은 요소의 getter를 Java에서 읽으므로, 모든 값이 바인드 파라미터가 됩니다.
VALUES 행이 여러 개인 문장 하나를 씁니다. 리스트가 길면 문장 하나에 값이 500개 이하가 되도록 나눕니다([7.8](07-dml.md#batching-for-many-rows) 참고). 빈 리스트는 SQL을 실행하지 않고 0을 반환합니다.

<a id="generated-id"></a>
## 생성되는 id

id가 IDENTITY나 SEQUENCE를 사용하면 id를 생략할 수 있습니다(규칙 U12).
첫 번째 예제의 `IdOrder` 줄이 이 경우에 해당합니다. `IdOrder`의 id는 IDENTITY 방식입니다.

<a id="insert--select"></a>
## insert ... select

다른 엔티티에서 행을 복사합니다. `select` 람다는 대상(`a`)과 원본(`o`)을 받습니다.

**(테스트됨)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java), 영역 `insert-select`:

```java
ExecutableInsert<Archive> i = qb.insert(Archive.class)
        .from(Order.class)
        .where(o -> o.getStatus() == 1)
        .select((a, o) -> { a.setId(o.getId()); a.setPrice(o.getPrice()); a.setCustomerName(o.getCustomer().getName()); });
// a path in the select list: left join, so orders without a customer stay (U10)
assertEquals("insert into Archive (id, price, customerName) select e0.id, e0.price, j1.name"
        + " from Order e0 left join e0.customer j1 where e0.status = 1", i.toHql().get(0).hql());
```

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `insert-select`:

```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price,customer_name)
select o1_0.id,o1_0.price,c1_0.name
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where o1_0.status=1
```

lambdaQL은 원본 경로에 명시적인 `left join`을 사용하므로 고객이 없는 주문도 빠지지 않습니다(규칙 U10). SQL은 세 데이터베이스에서 같습니다.

설계에는 컨텍스트 객체를 세 번째 인자로 받는 `by.set` 형태도 있습니다. 이 형태는 **아직 구현되지 않았습니다**.

```java
qb.insert(Archive.class).from(Order.class).where(o -> o.getStatus() == 9)
  .select((a, o, by) -> by.set(a.getId(), o.getId()).set(a.getCustomerName(), o.getCustomer().getName()))
  .execute();
```

<a id="insert--select-with-aggregates"></a>
## 집계를 사용한 insert ... select

[6. 조회, 집계, 그룹화](06-select-group.md)의 `groupBy`와 `Agg` 함수를 사용합니다.
이 형태는 **아직 구현되지 않았습니다**(마일스톤 M9).

```java
qb.insert(Summary.class).from(Order.class).groupBy(o -> o.getCustomer().getId())
  .select((s, o) -> { s.setCustomerId(o.getCustomer().getId()); s.setTotal(Agg.sum(o.getPrice())); s.setCnt(Agg.count()); })
  .execute();
```

**데이터베이스별 SQL (예정)** — 예정된 HQL을 Hibernate가 번역한 것으로, `PlannedSqlTest`가 기록했습니다. lambdaQL은 아직 이 쿼리를 만들지 않습니다. `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)의 예제 `07-insert-group`:

```sql
-- H2, MariaDB, Oracle (same)
insert into summary(customer_id,total,cnt)
select o1_0.customer_id,sum(o1_0.price),count(*)
from orders o1_0
group by o1_0.customer_id
```

---

---

[7. 수정, 삽입, 삭제 (Java)](07-dml.md) | [7.2 수정(update)](07-2-update.md) | [7.4 삭제(delete)](07-4-delete.md) | **7.5 삽입(insert)** | [7.6 업서트(upsert)](07-6-upsert.md) | [Kotlin](../kotlin/07-5-insert.md)
