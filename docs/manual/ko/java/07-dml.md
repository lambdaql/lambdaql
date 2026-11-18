<a id="7-update-insert-delete-java"></a>
# 7. 수정, 삽입, 삭제 (Java)

> [!NOTE]
> **상태**
> - **구현됨, 테스트함:** H2 2.3, MariaDB 10.11, Oracle 23.26(Hibernate 6.6.5, 마일스톤 M10, 결정 D28·D40). 대상은 update(setter, `by.set`, 여러 번의 `set()` 호출, `@Version`), delete, insert(한 행, 리스트, 생성 id, `insert ... select`), upsert, 규칙 U1·U3·U4·U6 검사, update와 delete의 `limit`, 1행 보호 `executeOne()`(결정 D41)입니다.
> - **함께 구현됨, 테스트함:** 수정·삭제 `where` 안의 서브쿼리(`sub`)(마일스톤 M8, [7.4](07-4-delete.md) 참고), `insert ... select`의 `groupBy`, `having`, `Agg`(마일스톤 M9, [7.5](07-5-insert.md) 참고).
> - **아직 구현되지 않음:** `set` 값 안의 서브쿼리(컨텍스트 객체의 `by.from(...)`), 복합 키 엔티티의 `limit` / `executeOne()`.
> - **(테스트됨)** 표시가 있는 예제는 테스트 코드에서 그대로 가져온 것입니다. 나머지 예제는 설계를 따르며 아직 테스트가 없습니다. 이 예제들의 HQL은 세부 사항(예: `e0` 같은 별칭 이름)이 다를 수 있습니다.
> - 전체 상태는 [Java 트랙 README](README.md#status)를 참고하십시오.

[Java 트랙](README.md) | [매뉴얼 홈](../README.md) | 이전: [6. 조회, 집계, 그룹화](06-select-group.md) | 다음: [8. 프로시저와 DB 함수](08-procedures-functions.md) | [이 페이지의 Kotlin 버전](../kotlin/07-dml.md)

공통 페이지: [1. 개요](../01-overview.md) · [11. 오류와 언어](../11-errors-and-languages.md)

---

lambdaQL에는 진입점이 다섯 개 있습니다.

| 진입점 | 용도 |
|---|---|
| `qb.from(X.class)` | 조회(3~6장) |
| `qb.update(X.class)` | 행 수정 |
| `qb.insert(X.class)` | 행 삽입 |
| `qb.delete(X.class)` | 행 삭제 |
| `qb.upsert(X.class)` | 행 삽입. 같은 `@Id`의 행이 있으면 그 행을 수정 |

기본 개념은 쿼리와 같습니다.

- 각 부분을 **람다 하나**로 작성합니다. static 헬퍼는 없습니다.
- 람다는 **절대 실행되지 않습니다**. lambdaQL은 람다의 바이트코드를 읽습니다. `o.setStatus(1)` 같은 setter 호출은 `set e0.status = 1`이 됩니다.
- `where`는 [3. 조건(`where`)](03-where.md)과 같은 규칙을 사용합니다. 대상 행은 같은 람다로 조회한 행과 같습니다.
- `execute()`는 문장을 실행하고 **영향받은 행 수**(`int`)를 반환합니다.
- `executeOne()`은 **최대 한 행**만 변경해야 하는 update나 delete를 실행합니다([1행 보호](#one-row-protection) 참고).
- `orderBy(...).limit(n)`는 앞쪽 `n`개 행만 변경합니다([limit](#limit-change-the-first-rows) 참고).
- `toHql()`은 문장을 실행하지 않고 HQL과 파라미터를 반환합니다.
- 문장 실행에는 트랜잭션이 필요합니다([트랜잭션](#transactions) 참고).

<a id="contents"></a>
## 목차

- [7.1 예제 엔티티](#71-example-entities)
- [7.2 수정(update)](#72-update)
- [7.3 값을 할당하는 두 가지 방법: setter 또는 `by.set`](#73-two-ways-to-assign-setter-or-byset)
- [7.4 삭제(delete)](#74-delete)
- [7.5 삽입(insert)](#75-insert)
- [7.6 업서트(upsert)](#76-upsert)
- [7.7 DML에 대한 Java 노트](#77-java-notes-for-dml)
- [7.8 중요한 규칙](#78-important-rules)
- [7.9 전체 규칙 U1~U21(요약)](#79-all-rules-u1-to-u21-summary)
- [7.10 v0.1에 없는 것](#710-not-in-v01)

---

<a id="71-example-entities"></a>
## 7.1 예제 엔티티

이 장은 `Order`, `Customer`, `Item`을 사용합니다([2. 시작하기](02-getting-started.md#example-entities) 참고).
여기서는 `Order`에 필드가 몇 개 더 있습니다.

| 필드 | 타입 | 참고 |
|---|---|---|
| `id`, `price`, `status`, `active`, `description`, `updateAt`, `customer` | | 다른 장과 같음 |
| `total` | `Double` | 품목 가격의 합계 |
| `cnt` | `Integer` | 카운터 |
| `custName` | `String` | 고객 이름의 복사본 |
| `address` | `Address`(임베디드: `city`, `zip`) | 임베디드 값 |

다른 엔티티: `VOrder`(`@Version` 필드 `version`이 있는 주문), `IdOrder`(id가 생성되는 주문), `Archive`(`id`, `price`, `customerName`), `Summary`(`customerId`, `total`, `cnt`).

테스트된 예제는 [`query/src/test/java/org/lambdaql/query`](https://github.com/codebit0/lambdaql/tree/main/query/src/test/java/org/lambdaql/query)의 테스트 엔티티를 사용합니다. 이 엔티티는 조금 다릅니다. 테스트의 `Order`에서 `price`는 `double`, `status`는 `short`이고, `float` 필드 `tex`가 있습니다. `IdOrder`의 id는 IDENTITY 방식입니다.

---

<a id="72-update"></a>
## 7.2 수정(update)

→ [7.2 수정(update)](07-2-update.md)

<a id="73-two-ways-to-assign-setter-or-byset"></a>
## 7.3 값을 할당하는 두 가지 방법: setter 또는 `by.set`

| 방식 | 사용할 때 | 예 |
|---|---|---|
| setter | 엔티티에 setter가 있을 때. 컴파일러가 값 타입을 검사합니다. | `set(o -> o.setStatus(1))` |
| `by.set(column, value)` | 엔티티에 **setter가 없을 때**(예: DDD 스타일 엔티티). | `set((o, by) -> by.set(o.getStatus(), 1))` |

**(테스트됨)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java), 영역 `update-by`:

```java
ExecutableUpdate<Order> u = qb.update(Order.class)
        .set((o, by) -> by.set(o.getDescription(), "done"))
        .where(o -> o.getStatus() == 2);
assertEquals("update Order e0 set e0.description = 'done' where e0.status = 2", u.toHql().hql());
```

**SQL 블록 읽는 법.** `…`는 줄여 쓴 select 목록(엔티티의 모든 컬럼)이고, `?`는 바인드 파라미터입니다. 각 줄은 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)에서 나옵니다. 이 테스트는 각 쿼리를 Hibernate 6.6.5로 H2 2.3, MariaDB 10.11, Oracle 23.26에서 실행하며, Hibernate의 SQL이 바뀌면 실패합니다.

**데이터베이스별 SQL (테스트됨)** — `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)의 예제 `update-by`:

```sql
-- H2, MariaDB
update orders o1_0
set description='done'
where o1_0.status=2

-- Oracle
update orders o1_0
set o1_0.description='done'
where o1_0.status=2
```

Oracle은 `set`에 별칭을 씁니다(`set o1_0.status=?`). H2와 MariaDB는 쓰지 않습니다.

이 장의 모든 setter 예제는 `by.set`으로도 작성할 수 있습니다. 호출을 이어 쓰십시오. 람다는 마지막 `by.set(...)`의 결과를 반환합니다.

```java
qb.update(Order.class)
  .set((o, by) -> by.set(o.getStatus(), st).set(o.getPrice(), o.getPrice() * 1.1))
  .where(o -> o.getStatus() == 1)
  .execute();

qb.update(Order.class).set((o, by) -> by.set(o.getAddress().getCity(), city)).where(o -> o.getId() == id).execute(); // embedded
qb.update(Order.class).set((o, by) -> by.set(o.getCustomer().getId(), cid)).where(o -> o.getId() == id).execute();  // FK
```

설계에서 두 번째 람다 인자는 **컨텍스트 객체 하나**입니다. 이 객체는 `set(column, value)`와 서브쿼리 메서드 `from(...)`을 모두 가집니다.
이름은 `by`든 `sub`든 자유롭게 붙이십시오. 이름은 동작에 영향이 없습니다.
이 객체의 서브쿼리 부분은 **아직 구현되지 않았습니다**. 현재 이 객체에는 `set`만 있습니다. (`where` 안의 서브쿼리는 동작합니다: `where((o, sub) -> ...)`. [5. 서브쿼리](05-subquery.md) 참고.)

```java
qb.update(Order.class)
  .set((o, by) -> by.set(o.getTotal(),
        by.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).sum(i -> i.getPrice())))
  .all()
  .execute();
```

`by.set`의 규칙(규칙 U20):

- 첫 번째 인자는 **대상에서 시작하는 컬럼 경로**여야 합니다: 필드, 임베디드 경로, 연관의 `@Id`.
  캡처 값, 계산식, 원본 엔티티의 경로는 오류입니다.
- 람다는 `by.set(...)` 호출을 이어 쓴 체인 하나여야 합니다. 다른 문장은 오류입니다.
- Java에서 `by.set(o.getStatus(), "abc")`는 타입이 제네릭이므로 컴파일됩니다.
  설계에서는 분석 시점에 컬럼 타입과 값 타입을 대조합니다. 이 검사는 아직 구현되지 않았습니다.
- **한 람다 안에서** setter와 `by.set`을 섞지 마십시오. 서로 다른 `set()` 호출에서는 섞을 수 있습니다.

---

<a id="74-delete"></a>
## 7.4 삭제(delete)

→ [7.4 삭제(delete)](07-4-delete.md)

<a id="75-insert"></a>
## 7.5 삽입(insert)

→ [7.5 삽입(insert)](07-5-insert.md)

<a id="76-upsert"></a>
## 7.6 업서트(upsert)

→ [7.6 업서트(upsert)](07-6-upsert.md)

<a id="77-java-notes-for-dml"></a>
## 7.7 DML에 대한 Java 노트

| Java 형태 | 의미 |
|---|---|
| 블록 람다 `o -> { o.setA(...); o.setB(...); }` | `set`, `values`, `insert ... select`, `onConflict(...).update(...)`에서 허용되지만 **setter 호출만** 가능. 지역 변수, `if`, 반복문은 불가(규칙 U1) |
| `Objects.requireNonNullElse(a, b)` | `coalesce(a, b)`(규칙 U2) |
| `o.setCnt(o.getCnt() + 1)` | 지원. javac가 값을 언박싱하고 박싱하지만 lambdaQL은 이를 무시함 |
| `o.setStatus((short) 1)` | `short`나 `byte` 필드에 javac가 요구하는 캐스트. 상수는 리터럴로 유지됨 |
| `o.setActive(!o.isActive())`(토글) | **v0.1에 없음**(조건에 따른 값). `where`가 다른 update 두 개를 사용 |
| `o.setDescription(o.getDescription() + "_x")` | **v0.1에 없음**(문자열 연결) |
| 값으로 쓴 `LocalDateTime.now()` | 애플리케이션에서 계산해 바인딩(규칙 U8) |
| `UpdateSet<Order> u = qb.update(...).set(...)`(또는 `var`) | update를 단계별로 구성([PATCH](07-2-update.md#many-set-calls-patch) 참고) |

---

<a id="78-important-rules"></a>
## 7.8 중요한 규칙

<a id="the-right-side-reads-the-old-value"></a>
### 오른쪽 값의 기준: 이전 값

SQL에서 SET 오른쪽의 모든 컬럼은 수정 **이전**의 값을 뜻합니다. lambdaQL은 이 규칙을 따릅니다.

```java
.set(o -> { o.setPrice(o.getPrice() * 2); o.setTotal(o.getPrice()); })
// error: price is assigned first, then read again
```

**같은 문장에서 컬럼에 할당한 뒤 나중에 그 컬럼을 읽으면** lambdaQL은 `set(...)`을 호출하는 시점에 `TranslationException`(`LQL-3042`)을 던집니다(규칙 U3).
이유: 이 경우 MariaDB는 **새** 값을 사용하고, H2와 Oracle은 이전 값을 사용합니다. 결과가 다릅니다(실측: MariaDB 200, H2와 Oracle 100).

다음 경우는 세 데이터베이스의 결과가 같으므로 허용합니다.

| 형태 | 허용 여부 |
|---|---|
| `o.setPrice(o.getPrice() * 1.1)`(자기 자신을 읽음) | 허용 |
| `o.setTotal(o.getPrice()); o.setPrice(0.0);`(먼저 읽고 나중에 할당) | 허용 |
| `o.setPrice(...); o.setTotal(o.getPrice());`(먼저 할당하고 나중에 읽음) | **오류** |

이 검사는 물리 컬럼(임베디드 하위 컬럼, FK 컬럼) 단위로 동작하며, 여러 `set()` 호출 사이에도 적용됩니다([PATCH](07-2-update.md#many-set-calls-patch)에서 테스트함).

<a id="assign-a-column-only-once"></a>
### 컬럼당 한 번의 할당

같은 컬럼에 두 번 할당하면 오류입니다(규칙 U4). `setAddress(...)` 뒤의 `getAddress().setCity(...)`,
또는 `setCustomer(...)` 뒤의 `getCustomer().setId(...)`도 포함됩니다.

<a id="rules-checked-at-analysis-time"></a>
### 분석 시점의 규칙 검사

lambdaQL은 람다를 읽을 때 규칙 U1, U3, U4, U6을 검사합니다. 아래 테스트는 사용자 API가 아니라 내부 클래스 `AssignmentReader`를 호출합니다. 사용자 API에서는 `set(...)`과 `values(...)`가 같은 `LambdaQLException`을 던집니다(예: U1은 `LQL-3037`, U3은 `LQL-3042`, U4는 `LQL-3041`).

**(테스트됨)** [`AssignmentReaderTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/sem/AssignmentReaderTest.java), 영역 `rules`:

```java
// U1: only setters; no condition, no local variable
assertThrows(LambdaQLException.class, () -> setters(o -> { if (o.isActive()) o.setStatus((short) 1); }));
// U6: an association's other properties belong to another table
assertThrows(LambdaQLException.class, () -> setters(o -> o.getCustomer().setName("x")));
// U3: a value reads a column written before
assertThrows(LambdaQLException.class, () -> AssignmentReader.checkRules(
        AssignmentReader.readSetters(SerializedLambdaExtractor.extract((Assign<Order>) o -> {
            o.setPrice(1);
            o.setTex((float) o.getPrice());
        }), List.of(Order.class), -1, new JpaEntityModel(emf.getMetamodel())), "test"));
```

<a id="where-or-all-is-required"></a>
### `where`나 `all()`의 필수 지정

`set()`은 `execute()`가 **없는** 타입(`UpdateSet`)을 반환합니다. `where()`와 `all()`만 `execute()`가 있는 타입(`ExecutableUpdate`)을 반환합니다.
그래서 `where`를 빠뜨리면 컴파일 오류가 납니다(규칙 U5). `delete`도 마찬가지입니다.

<a id="association-paths-same-rows-as-select"></a>
### 연관 경로: 조회와 같은 행

lambdaQL은 DML에서 FK가 아닌 연관 경로를 **상관 스칼라 서브쿼리**로 씁니다. `where`와 SET 오른쪽 모두 같습니다(규칙 U9). 아래 줄은 [7.2](07-2-update.md#a-value-from-another-table)의 테스트된 예제에서 가져왔습니다.

```java
.where(o -> o.getCustomer().getName().equals("Kim"))
// → where (select s1.name from Customer s1 where s1.id = e0.customer.id) = 'Kim' and e0.customer.id is not null

o.setCustName(o.getCustomer().getName())
// → set e0.custName = (select s1.name from Customer s1 where s1.id = e0.customer.id)
```

이렇게 하는 이유는 다음과 같습니다. lambdaQL이 경로를 Hibernate에 그대로 넘기면 Hibernate는 데이터베이스마다 다른 조인 SQL을 만듭니다
(H2는 `merge`, MariaDB는 `update ... join`, Oracle은 인라인 뷰). 또 SET에 경로가 있으면 **고객이 없는 행이 빠졌습니다**
(실측: `.all()`로 10행 중 9행만 수정됨).
lambdaQL은 서브쿼리를 직접 씁니다. 그러면 문장이 자기 테이블만 읽으므로, 대상 테이블 읽기에 대한 MariaDB의 제약(오류 1093)에 걸리지 않습니다.

조회는 `where`의 경로에 inner join을 사용합니다(결정 D36). 그래서 DML에서는 이런 경로에 lambdaQL이 `e0.customer.id is not null`을 더합니다.
그 결과 `update`와 `delete`는 `qb.from(X.class).where(같은 람다)`가 반환하는 **바로 그 행**만 대상으로 합니다(테스트함: 삭제된 행을 조회 결과와 비교).
`o.getCustomer() == null || ...` 같은 null 가드가 있어도 마찬가지입니다. 조회도 이 경로에 inner join을 사용하므로(결정 D48) 검사는 유지됩니다.

`@Id`로 끝나는 경로(`o.getCustomer().getId()`)는 FK 컬럼입니다. 서브쿼리도 조인도 없습니다.

<a id="batching-for-many-rows"></a>
### 여러 행의 배치 처리

`values(list, ...)`는 VALUES 행이 여러 개인 문장 하나를 씁니다(규칙 U13).

| 항목 | 규칙 |
|---|---|
| 분할 단위 | 행 수가 아니라 값의 개수(행 x 컬럼) |
| 한도 | 문장당 값 500개 이하(고정) |
| 반환 값 | 모든 문장의 합 |
| 트랜잭션 | 모든 문장을 한 트랜잭션에서 실행 |
| 빈 리스트 | SQL 없음, 0 반환(테스트함) |

Oracle에는 바인드 값 65,535개라는 엄격한 한도가 있습니다(ORA-01745). 파싱은 그보다 훨씬 전부터 매우 느려집니다
(설계 단계 테스트에서 21,845행에 약 6분). 그래서 한도를 작게 두었습니다.

<a id="limit-change-the-first-rows"></a>
### limit: 앞쪽 행 변경

`where(...)` 또는 `all()` 뒤에 `orderBy(...).limit(n)`을 붙이면 update나 delete가 최대 `n`개 행만 변경합니다. 대상은 `qb.from(X.class).where(same).orderBy(same)`의 앞쪽 `n`개 행입니다.

```java
qb.delete(Order.class).where(o -> o.getStatus() == 1).orderBy((o, by) -> by.asc(o.getPrice())).limit(100).execute();
```

| 항목 | 규칙 |
|---|---|
| 순서 | `orderBy` 키 다음에 id(규칙 S9). `orderBy`가 없으면 id 순서 |
| `limit` 없는 `orderBy` | 오류(`IllegalStateException`) |
| 렌더링 | `e0.id in (select t.id from (select x0.id as id from X x0 ... order by ... limit n) t)` |
| 대상 행 | 안쪽 select가 조인을 포함해 조회 규칙(D36)을 따르므로 조회와 같은 행 |
| MariaDB | 파생 테이블 `t`로 오류 1093과 1235를 회피 |

테스트된 예제와 데이터베이스별 SQL: [7.4](07-4-delete.md#delete-the-first-rows-limit)(delete), [7.2](07-2-update.md#update-the-first-rows-limit)(update).

<a id="one-row-protection"></a>
### 1행 보호

`execute()` 대신 `executeOne()`을 호출하면 **최대 한 행**만 변경합니다.

1. lambdaQL이 조건에 맞는 행의 id를 최대 2개 읽습니다.
2. id가 2개이면 아무것도 변경하지 않고 `NonUniqueResultException`을 던집니다.
3. id가 1개이면 `id = :p and (조건)`으로 그 행을 변경합니다.

2행 이상을 변경하지 않으며 롤백에 의존하지 않습니다. 결과는 0 또는 1입니다. `executeOne()`을 `orderBy`나 `limit`과 함께 쓰면 오류(`IllegalStateException`)입니다.

테스트된 예제: [7.4](07-4-delete.md#delete-exactly-one-row-executeone). Spring 리포지토리에서는 `deleteOne`과 `updateOne`을 사용합니다([12.4](12-spring-data-jpa.md#one-row-protection-deleteone-and-updateone) 참고).

<a id="transactions"></a>
### 트랜잭션

벌크 문장에는 트랜잭션이 필요합니다. lambdaQL이 트랜잭션을 여는 경우는 `EntityManagerFactory` 생성자를 쓴 빌더뿐입니다.

| 빌더 | 트랜잭션 |
|---|---|
| `new QueryBuilder(entityManagerFactory)` | `execute()`와 `executeOne()`이 자체 트랜잭션을 열고 커밋합니다. 오류가 나면 롤백합니다 |
| `EntityManager`를 쓰는 빌더: `new QueryBuilder(entityManager)`, Spring의 `QueryBuilder` 빈, 리포지토리 실행기 | 호출자의 트랜잭션(`@Transactional`, 또는 그 `EntityManager`의 활성 트랜잭션). 트랜잭션이 없으면 SQL을 보내기 전에 `TransactionRequiredException`으로 실패합니다(테스트함). Spring 리포지토리 호출에서는 `InvalidDataAccessApiUsageException`이 됩니다([12.6](12-spring-data-jpa.md#126-transactions-and-exceptions) 참고) |

<a id="the-persistence-context-is-not-updated"></a>
### 영속성 컨텍스트 미반영

벌크 DML은 JPA 영속성 컨텍스트를 거치지 않습니다(규칙 U16).

- Hibernate는 문장 실행 전에 대기 중인 변경을 flush합니다.
- 문장 실행 후, 같은 `EntityManager`에 이미 로드된 엔티티는 **이전 값**을 가집니다. 그 엔티티를 다시 사용한다면 `entityManager.clear()`를 호출하십시오.
- JPA `cascade`와 `orphanRemoval`은 실행되지 **않습니다**. 자식을 먼저 삭제하거나 데이터베이스에서 `ON DELETE CASCADE`를 사용하십시오.

<a id="sqlrestriction-and-softdelete"></a>
### `@SQLRestriction`과 `@SoftDelete`

Hibernate는 엔티티의 `@SQLRestriction` 조건과 `@SoftDelete` 조건을 lambdaQL이 만드는 모든 문장에 붙입니다(MariaDB 10.11, Oracle 23.26에서 실측. H2는 미측정).
벌크 `update`와 `delete`도 마찬가지입니다. 예: `where m1_0.order_id=? and (m1_0.hidden = 0)`.

`@SoftDelete` 엔티티의 `delete`는 `update … set deleted = 1 where … and deleted = 0`이 됩니다. 행이 실제로 지워지지 않습니다.
lambdaQL은 이 조건을 따로 처리하지 않습니다.

---

<a id="79-all-rules-u1-to-u21-summary"></a>
## 7.9 전체 규칙 U1~U21(요약)

lambdaQL이 람다를 읽을 때 검사하는 규칙: U1, U3, U4, U6(테스트함). 컴파일러가 검사하는 규칙: U5.
U11, U12, U15, U20에 대한 lambdaQL의 검사는 아직 구현되지 않았습니다. U17(같은 테이블을 읽는 서브쿼리에 대한 MariaDB용 감싸기)도 아직 구현되지 않았습니다.

| # | 규칙 |
|---|---|
| U1 | 할당 람다에는 대상, 대상의 임베디드 경로, 연관의 `@Id`에 대한 **setter 호출만** 들어갑니다. `if`, 삼항 연산자 `c ? a : b`, 반복문, 지역 변수, 그 밖의 메서드 호출은 오류입니다. 할당이 없는 람다도 오류입니다. |
| U2 | 오른쪽은 `where`의 값 규칙([3. 조건(`where`)](03-where.md))을 따릅니다. 캡처 값은 바인드 파라미터이고, 람다에 적은 상수는 리터럴입니다(D37). `Objects.requireNonNullElse(a, b)`는 `coalesce(a, b)`가 됩니다. |
| U3 | 오른쪽은 **이전** 값을 읽습니다. 같은 문장에서 컬럼에 할당한 뒤 그 컬럼을 읽으면 오류입니다. |
| U4 | 같은 물리 컬럼에 두 번 할당하면 오류입니다. 여러 `set()` 호출에 걸친 경우도 마찬가지입니다. |
| U5 | `where()`나 `all()` 없이는 `execute()`가 없습니다. 컴파일러가 검사합니다. |
| U6 | 선택적이지 않은 컬럼(`@Id`, `nullable=false`, 기본형)에 `null`을 할당하면 오류입니다. 컬렉션이나 연관 엔티티의 `@Id`가 아닌 필드에 할당해도 오류입니다. |
| U7 | 캡처 값은 사용할 때마다 자기 파라미터를 받습니다. (테스트에서 STRING 컬럼과 ORDINAL 컬럼이 enum 파라미터 하나를 공유하자 오류 없이 잘못된 값이 저장되었습니다.) |
| U8 | `LocalDateTime.now()`는 애플리케이션에서 평가해 바인딩합니다. 데이터베이스 시간(`current_timestamp`)은 지원하지 않습니다. |
| U9 | DML에서 FK가 아닌 연관 경로는 `where`와 SET 오른쪽 모두에서 상관 스칼라 서브쿼리가 됩니다. `where`에서 조회라면 inner join할 경로(D36)에는 `customer.id is not null`을 더합니다. DML은 조회와 같은 행을 대상으로 합니다. |
| U10 | `insert ... select`에서 원본 경로는 명시적인 `left join`이 됩니다. |
| U11 | `o.setCustomer(entity)`는 엔티티의 `@Id`를 바인딩합니다. id가 null이면 바인딩이 실패합니다. |
| U12 | IDENTITY와 SEQUENCE id는 생략할 수 있습니다. TABLE 생성기는 `values`에서만 동작하고 `insert ... select`에서는 동작하지 않습니다. 생성기가 없으면 id가 필요합니다. 선택적이지 않은 다른 필드도 필요합니다. |
| U13 | 여러 행은 문장당 값 500개 이하로 나눕니다. 결과는 합산됩니다. 모든 문장은 한 트랜잭션에서 실행됩니다. |
| U14 | `set` 람다 하나로 여러 컬럼에 할당할 수 있고, `set()`은 여러 번 호출할 수 있습니다. 모두 호출 순서대로 SET 목록 하나로 합쳐집니다. |
| U15 | `@Version` 엔티티는 `update versioned`를 사용합니다. 버전에 직접 할당하지 마십시오. `execute() == 0` 확인은 사용자의 몫입니다. |
| U16 | 벌크 DML은 영속성 컨텍스트를 건너뜁니다. 실행 전 flush, 실행 후 오래된 엔티티, cascade 없음. |
| U17 | DELETE 서브쿼리가 같은 엔티티를 읽으면 lambdaQL은 이를 파생 테이블로 감쌉니다. (그렇지 않으면 MariaDB 오류 1093.) |
| U18 | 상속: SINGLE_TABLE은 완전히 지원합니다. JOINED와 TABLE_PER_CLASS의 update/delete는 임시 테이블을 사용합니다. 그 하위 클래스로의 insert는 거부됩니다. |
| U19 | 쿼리 캐시 키에는 진입점, 대상 엔티티, set 람다, where/all, versioned 여부, 분할 크기가 들어갑니다. |
| U20 | `by.set(column, value)`: 컬럼은 대상에 대한 getter 경로여야 합니다. 타입은 분석 시점에 검사합니다. 한 람다 안에서 setter와 `by.set`을 섞지 마십시오. |
| U21 | 각 람다는 한 번 분석되고 캐시됩니다. 여러 `set()` 호출에 걸친 검사는 캐시된 요약만 비교합니다(프로토타입에서 약 0.1~0.2 µs). |

업서트 규칙(결정 D40)은 [7.6 업서트(upsert)](07-6-upsert.md#rules)에 있습니다.

---

<a id="710-not-in-v01"></a>
## 7.10 v0.1에 없는 것

| 항목 | 계획 | 지금 할 수 있는 일 |
|---|---|---|
| 조건에 따른 값(`c ? a : b`, 값으로 쓴 `!o.isActive()`) | v0.2(CASE WHEN) | – |
| 할당을 고르는 람다 안의 `if` | v0.2 | `set()`을 여러 번 호출(U14) |
| 문자열 연결(`o.getDescription() + "_x"`) | v0.2 | – |
| `insert ... select`의 명시적 조인 | v0.2 | many-to-one 경로 사용 |
| `returning` | 거부됨 | – |
| 데이터베이스 시간(`current_timestamp`) | 거부됨 | 애플리케이션 시간을 바인딩(U8) |

규칙을 어기면 코드 `[LQL-xxxx]`가 붙은 `LambdaQLException`이 발생합니다. 대부분의 규칙은 `TranslationException`, 대입 람다 안의 조건이나 지역 변수는 `UnsupportedLambdaShapeException`입니다. [11. 오류와 언어](../11-errors-and-languages.md)를 참고하십시오.
모든 규칙을 한곳에서 보려면: [10. 규칙과 제한](10-rules-and-limits.md).

---

[Java 트랙](README.md) | 이전: [6. 조회, 집계, 그룹화](06-select-group.md) | 다음: [8. 프로시저와 DB 함수](08-procedures-functions.md) | [이 페이지의 Kotlin 버전](../kotlin/07-dml.md)
