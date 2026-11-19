<a id="76-upsert"></a>
# 7.6 업서트(upsert) (Java)

[7. 수정, 삽입, 삭제 (Java)](07-dml.md) | [7.2 수정(update)](07-2-update.md) | [7.4 삭제(delete)](07-4-delete.md) | [7.5 삽입(insert)](07-5-insert.md) | **7.6 업서트(upsert)** | [Kotlin](../kotlin/07-6-upsert.md)

---


업서트는 행을 삽입합니다. 같은 키의 행이 이미 있으면 삽입 대신 그 행을 수정하거나, 그 행을 그대로 둡니다.
lambdaQL은 세 가지 형태를 제공합니다.

| 형태 | 키 | 키가 이미 있을 때 |
|---|---|---|
| `qb.upsert(X.class).values(...)` | `@Id` | 할당한 나머지 컬럼을 새 값으로 수정 |
| `qb.insert(X.class).values(...).onConflict(key).update(...)` | 기본 키나 유니크 제약이 있는 컬럼 | `update(...)`의 할당을 기존 행에 실행 |
| `qb.insert(X.class).values(...).onConflict(key).ignore()` | 위와 같음 | 변경 없음: 기존 행을 유지 |

<a id="short-form-upsert"></a>
## 단축 형태: `upsert`

**(테스트됨)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java), 영역 `upsert`:

```java
// the key is the @Id; on conflict, the other assigned columns get the new values
int inserted = qb.upsert(Archive.class).values(a -> { a.setId(100L); a.setPrice(1.0); }).execute();
ExecutableInsert<Archive> again = qb.upsert(Archive.class).values(a -> { a.setId(100L); a.setPrice(2.0); });
assertEquals("insert into Archive e0 (id, price) values (100L, 2.0) on conflict(id) do update set price = excluded.price",
        again.toHql().get(0).hql());
int updated = again.execute();                     // MariaDB counts an updated row as 2, H2 and Oracle as 1
```

**SQL 블록 읽는 법.** `…`는 줄여 쓴 select 목록(엔티티의 모든 컬럼)이고, `?`는 바인드 파라미터입니다. 각 줄은 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)에서 나옵니다. 이 테스트는 각 쿼리를 Hibernate 7.4로 H2 2.3, MariaDB 10.11, Oracle 23.26에서 실행하며, Hibernate의 SQL이 바뀌면 실패합니다.

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `upsert`:

```sql
-- H2
merge into archive a1_0 using (values (100,2.0)) excluded(id,price) on (a1_0.id=excluded.id) when matched then update
set price=excluded.price when not matched then insert (id,price)
values (excluded.id,excluded.price)

-- MariaDB
insert into archive(id,price)
values (100,2.0) on duplicate key update price=values(price)

-- Oracle
merge into archive a1_0 using (select 100 id,2.0 price) excluded on (a1_0.id=excluded.id) when matched then update
set a1_0.price=excluded.price when not matched then insert (id,price)
values (excluded.id,excluded.price)
```

SQL은 데이터베이스마다 다릅니다. H2와 Oracle은 `merge` 문을 받습니다. H2는 `values (...)`에서, Oracle은 `select 100 id,2.0 price`에서 새 행을 읽습니다. MariaDB는 `insert ... on duplicate key update`를 받고, 새 값을 `values(price)`로 읽습니다.

행에는 `@Id` 할당이 있어야 합니다. `@Id`만 할당하면 수정할 컬럼이 없으므로, lambdaQL은 아래의 `ignore()` 형태로 씁니다.

<a id="your-own-change-onconflictkeyupdate"></a>
## 직접 지정하는 변경: `onConflict(key).update(...)`

변경 내용이 "새 값으로 교체"가 아닐 때(예: 카운터) 이 형태를 사용합니다.

**(테스트됨)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java), 영역 `on-conflict`:

```java
// on conflict, change the existing row: a getter here reads the existing row
ExecutableInsert<Archive> add = qb.insert(Archive.class)
        .values(a -> { a.setId(100L); a.setPrice(5.0); })
        .onConflict(a -> a.getId())
        .update(a -> a.setPrice(a.getPrice() + 5.0));
assertEquals("insert into Archive e0 (id, price) values (100L, 5.0) on conflict(id) do update set price = (e0.price + 5.0)",
        add.toHql().get(0).hql());
add.execute();                                     // inserted: 5.0
add.execute();                                     // conflict: 5.0 + 5.0
```

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `on-conflict`:

```sql
-- H2
merge into archive a1_0 using (values (100,5.0)) excluded(id,price) on (a1_0.id=excluded.id) when matched then update
set price=(a1_0.price+5.0) when not matched then insert (id,price)
values (excluded.id,excluded.price)

-- MariaDB
insert into archive(id,price)
values (100,5.0) on duplicate key update price=(archive.price+5.0)

-- Oracle
merge into archive a1_0 using (select 100 id,5.0 price) excluded on (a1_0.id=excluded.id) when matched then update
set a1_0.price=(a1_0.price+5.0) when not matched then insert (id,price)
values (excluded.id,excluded.price)
```

`update(...)` 안의 getter는 **기존** 행을 읽습니다. `a.getPrice() + 5.0`은 이전 가격에 5를 더한 값입니다. SQL에서 H2와 Oracle은 별칭(`a1_0.price`)을, MariaDB는 테이블 이름(`archive.price`)을 씁니다.

키는 getter 람다(`a -> a.getId()`)나 getter 메서드 레퍼런스(`Archive::getId`)로 지정합니다. 키는 연관 경로가 아니라 엔티티 자신의 컬럼이어야 하고, 데이터베이스에 그 컬럼의 기본 키나 유니크 제약이 있어야 합니다.

`values`와 `update`에 인자가 둘인 람다를 쓰면 두 번째 인자 `sub`로 서브쿼리를 만들 수 있습니다(결정 D49, 세 DB에서 실측). 예제는 [7.5](07-5-insert.md#a-subquery-in-a-value)를 참고하십시오.

<a id="keep-the-existing-row-onconflictkeyignore"></a>
## 기존 행 유지: `onConflict(key).ignore()`

**(테스트됨)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java), 영역 `insert-ignore`:

```java
ExecutableInsert<Archive> once = qb.insert(Archive.class)
        .values(a -> { a.setId(300L); a.setPrice(1.0); })
        .onConflict(a -> a.getId())
        .ignore();
assertEquals("insert into Archive (id, price) select 300L, 1.0 where not exists (select 1 from Archive x where x.id = 300L)",
        once.toHql().get(0).hql());
int first = once.execute();                        // 1: inserted
int second = qb.insert(Archive.class).values(a -> { a.setId(300L); a.setPrice(2.0); })
        .onConflict(a -> a.getId()).ignore().execute();   // 0: the row exists, nothing changes
```

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `insert-ignore`:

```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price)
select 300,1.0
where not exists(select 1 from archive a2_0 where a2_0.id=300)
```

Hibernate 자체의 `on conflict ... do nothing`은 H2와 Oracle에서 일반 insert로 실행되어 키 중복 오류로 실패합니다.
그래서 lambdaQL은 `insert ... select ... where not exists (...)`를 직접 씁니다. SQL은 세 데이터베이스에서 같습니다.

> [!WARNING]
> **`ignore()`는 원자적이지 않습니다.** 두 트랜잭션이 같은 키를 동시에 삽입하면 둘 다 `not exists` 검사를 통과할 수 있습니다.
> 그러면 한쪽에서 키 중복 오류가 발생합니다. 애플리케이션에서 이런 상황이 생길 수 있다면 이 오류를 처리하십시오.

<a id="the-row-count"></a>
## 영향 행 수

`execute()`는 데이터베이스가 보고한 행 수를 그대로 반환합니다(결정 D40). 테스트가 다음 값을 확인합니다(실측).

| 데이터베이스 | 삽입 | 수정 | 무시 |
|---|---|---|---|
| H2 | 1 | 1 | 0 |
| MariaDB | 1 | **2** | 0 |
| Oracle | 1 | 1 | 0 |

따라서 MariaDB에서 `execute() == 1`은 "수정됨"을 뜻하지 않습니다. 행이 기록되었는지 확인하려면 `execute() > 0`을 사용하십시오.

<a id="many-rows"></a>
## 여러 행

`qb.upsert(X.class).values(list, (a, l) -> ...)`와 `values(list, ...)` 뒤의 `onConflict(...)`는 **행마다 문장 하나**를 실행합니다. `execute()`는 합계를 반환합니다. 이 형태는 아직 테스트가 없습니다(미측정).
리스트 형태도 `values(list, (a, l, sub) -> ...)`처럼 세 번째 인자로 서브쿼리를 받습니다([7.5](07-5-insert.md#many-rows-from-a-list) 참고).

```java
int n = qb.upsert(Archive.class)
    .values(lines, (a, l) -> { a.setId(l.id()); a.setPrice(l.price()); })
    .execute();
```

<a id="rules"></a>
## 규칙

- 행에는 키 할당이 있어야 합니다. `upsert(X.class)`는 `@Id`를 키로 사용합니다.
- 키는 엔티티 자신의 컬럼이어야 합니다. 연관 경로는 오류입니다.
- `values(...)`의 값에서는 행을 읽을 수 없습니다(새 행이기 때문입니다). 기존 행은 `update(...)`에서만 읽습니다.
- update와 insert의 할당 규칙을 적용합니다(U1, U3, U4, U6. [7.9](07-dml.md#79-all-rules-u1-to-u21-summary) 참고).
- `insert ... select`로 하는 업서트는 없습니다.
- 다른 DML 문장과 마찬가지로 업서트에는 트랜잭션이 필요하고, 이미 로드된 엔티티에는 반영되지 않습니다([7.8](07-dml.md#transactions) 참고).

---

---

[7. 수정, 삽입, 삭제 (Java)](07-dml.md) | [7.2 수정(update)](07-2-update.md) | [7.4 삭제(delete)](07-4-delete.md) | [7.5 삽입(insert)](07-5-insert.md) | **7.6 업서트(upsert)** | [Kotlin](../kotlin/07-6-upsert.md)
