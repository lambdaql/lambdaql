<a id="74-delete"></a>
# 7.4 삭제(delete) (Java)

> [!NOTE]
> **상태: 설계됨, 아직 구현되지 않음.**
> 이 장은 확정된 설계입니다(결정 D28, 마일스톤 M10).
> 문법은 Java에서 컴파일되며, SQL은 Hibernate 6.6.5로 H2, MariaDB, Oracle에서 확인했습니다.
> 지금의 lambdaQL에서는 동작하지 **않습니다**. 현재는 분석 코어와 `where` + `toList()`만 동작합니다
> (H2, MariaDB, Oracle에서 테스트함). 전체 상태는 [Java 트랙 README](README.md#status)를 참고하십시오.

[7. 수정, 삽입, 삭제 (Java)](07-dml.md) | [7.2 수정(update)](07-2-update.md) | **7.4 삭제(delete)** | [7.5 삽입(insert)](07-5-insert.md) | [Kotlin](../kotlin/07-4-delete.md)

---


```java
qb.delete(Order.class).where(o -> o.getStatus() == 9).execute();

qb.delete(Order.class).where(o -> o.getCustomer().getName() == null).execute();

qb.delete(Order.class)
  .where((o, sub) -> !sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).exists())
  .execute();

qb.delete(Order.class).all().execute();     // 모든 행 삭제: all()이 필요
```

두 번째 예제의 HQL:

```sql
delete from Order o
where exists (select 1 from Customer c where c.id = o.customer.id and c.name is null)
```

**SQL 블록 읽는 법.** `…`는 줄여 쓴 select 목록(엔티티의 모든 컬럼)이고, `?`는 바인드 파라미터입니다. [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java)는 이 페이지의 HQL을 Hibernate에 직접 넘기고, H2 2.3, MariaDB 10.11, Oracle 23.26(Hibernate 6.6.5)에서 나온 SQL을 기록합니다. lambdaQL은 아직 이 쿼리들을 만들지 않습니다. Hibernate의 SQL이 바뀌면 테스트가 실패합니다.

**데이터베이스별 SQL (예정)** — 예정된 HQL을 Hibernate가 번역한 것으로, `PlannedSqlTest`가 기록했습니다. lambdaQL은 아직 이 쿼리를 만들지 않습니다. `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)의 예제 `07-delete-exists`:

```sql
-- H2, Oracle
delete from orders o1_0
where exists(select 1 from customers c1_0 where c1_0.id=o1_0.customer_id and c1_0.name is null)

-- MariaDB
delete o1_0 from orders o1_0
where exists(select 1 from customers c1_0 where c1_0.id=o1_0.customer_id and c1_0.name is null)
```

MariaDB는 `delete o1_0 from orders o1_0`로 씁니다. H2와 Oracle은 `delete from orders o1_0`로 씁니다.

이 문장은 `qb.from(Order.class).where(o -> o.getCustomer().getName() == null)`이 반환하는 것과 **같은 행**, 즉 고객의 이름이 없는 주문을 삭제합니다.
고객이 **없는** 주문은 삭제되지 않습니다(규칙 U9, 결정 D36). 그런 주문도 삭제하려면 `o.getCustomer() == null || o.getCustomer().getName() == null`이라고 쓰십시오.

id 목록으로 삭제(캡처한 컬렉션, 결정 D29):

```java
qb.delete(Item.class).where(i -> ids.contains(i.getId())).execute();
```

---

---

[7. 수정, 삽입, 삭제 (Java)](07-dml.md) | [7.2 수정(update)](07-2-update.md) | **7.4 삭제(delete)** | [7.5 삽입(insert)](07-5-insert.md) | [Kotlin](../kotlin/07-4-delete.md)
