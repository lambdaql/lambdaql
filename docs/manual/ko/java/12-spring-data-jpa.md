<a id="12-spring-data-jpa-java"></a>
# 12. Spring Data JPA (Java)

[Java 트랙](README.md) | [매뉴얼 홈](../README.md) | 이전: [11. 오류와 언어](../11-errors-and-languages.md) | 다음: [13. Spring Boot 3과 4](../13-spring-boot-versions.md) | [이 페이지의 Kotlin 버전](../kotlin/12-spring-data-jpa.md)

---

<a id="121-what-you-get"></a>
## 12.1 제공 기능

Spring Data JPA에서는 리포지토리 인터페이스를 작성합니다. lambdaQL은 여기에 인터페이스 `LambdaQlExecutor<T>` 하나를 더합니다.

리포지토리가 이 인터페이스를 상속하고, 직접 만드는 쿼리 메서드는 lambdaQL을 사용하는 **default 메서드**로 작성합니다.
이 메서드들을 위한 `...RepositoryImpl` 클래스는 작성하지 않습니다.

| 구성 요소 | 기능 | 절 |
|---|---|---|
| `LambdaQlExecutor<T>` | 모든 리포지토리에서 람다 조건으로 `findAll`, `findSet`, `findFirst`, `findOne`, `count`, `exists`, `query()` 실행 | [12.3](#123-repository-setup), [12.4](#124-executor-methods) |
| Spring `Sort`와 `Pageable` | `findAll(where, sort)`, `findSet(where, sort)`, Spring `Page`를 반환하는 `findAll(where, pageable)` | [12.5](#125-spring-sort-and-pageable) |
| 수정, 삭제, 삽입, 업서트 | `deleteAll(where)`, `updateOne(where, set)` 같은 단축 메서드가 호출 한 번으로 문장을 실행. `updateQuery()`, `deleteQuery()`, `insertQuery()`, `upsertQuery()`가 리포지토리 엔티티에 대해 [7. 수정, 삽입, 삭제](07-dml.md)의 문장을 시작. 모두 호출자의 트랜잭션 필요 | [12.4](#124-executor-methods), [12.6](#126-transactions-and-exceptions) |
| `QueryBuilder` 빈 | 서비스에서 사용할 `QueryBuilder` 빈을 Spring Boot가 생성 | [12.8](#128-the-querybuilder-bean) |

<a id="122-modules"></a>
## 12.2 모듈

코어는 Spring에 의존하지 않으므로 일반 JPA에서도 사용할 수 있습니다. `lambdaql-core`와 `lambdaql-spring-data-jpa`는 Spring Boot 3·4 공통이고, 스타터만 Spring Boot 버전마다 따로 있습니다(이름 끝 `-boot3`, `-boot4`, [13. Spring Boot 3과 4](../13-spring-boot-versions.md)).

| 아티팩트 | 포함 내용 | 필요한 경우 |
|---|---|---|
| `lambdaql-core` | 엔진과 `QueryBuilder`. Spring 의존성 없음. Spring Boot 3·4 공통 | 항상. 아래 아티팩트가 함께 가져옴 |
| `lambdaql-spring-data-jpa` | `LambdaQlExecutor<T>`, Spring Data 등록, Spring Boot 자동 구성(`QueryBuilder` 빈). Spring Data JPA 3.5·4.1 공통. Spring Data JPA는 프로젝트의 것을 씀 | Spring Boot 없이 Spring Data JPA를 사용할 때 |
| `lambdaql-spring-boot-starter-boot4`, `lambdaql-spring-boot-starter-boot3` | 코드 없음. `lambdaql-spring-data-jpa`와 그 Boot 버전의 `spring-boot-starter-data-jpa`를 가져옴 | Spring Boot를 사용할 때(프로젝트의 Boot 버전에 맞춤) |

아티팩트는 **아직 Maven Central에 배포되지 않았습니다**. 먼저 소스에서 빌드하여 로컬 Maven 저장소에 배포하십시오([2. 시작하기](02-getting-started.md#build-from-source) 참고). 버전은 `1.0.0`입니다.

Spring Boot 4에서 사용할 때의 Gradle(Groovy DSL). Spring Boot 3이면 이름 끝을 `-boot3`으로 바꾸십시오:

```groovy
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation 'org.lambdaql:lambdaql-spring-boot-starter-boot4:1.0.0'
}
```

Maven:

```xml
<dependency>
  <groupId>org.lambdaql</groupId>
  <artifactId>lambdaql-spring-boot-starter-boot4</artifactId>
  <version>1.0.0</version>
</dependency>
```

Spring Boot 없이 사용할 때는 기존 Spring Data JPA 구성(Spring Data JPA, Hibernate, JDBC 드라이버)에 `lambdaql-spring-data-jpa`를 추가하십시오. 이 아티팩트는 `lambdaql-core`를 함께 가져오고, Spring Data JPA는 프로젝트에 있는 것(3.5 또는 4.1)을 씁니다.

```groovy
dependencies {
    implementation 'org.lambdaql:lambdaql-spring-data-jpa:1.0.0'
}
```

```xml
<dependency>
  <groupId>org.lambdaql</groupId>
  <artifactId>lambdaql-spring-data-jpa</artifactId>
  <version>1.0.0</version>
</dependency>
```

요구 사항:

| 항목 | 버전 |
|---|---|
| Java | 21 |
| Spring Boot | 3.5 또는 4.1(3.5.16, 4.1.2로 테스트) |
| Spring Data JPA | 3.5 또는 4.1(3.5.13, 4.1.2로 테스트) |
| Hibernate | 6.6 또는 7.4(Spring Boot에 포함) |

Spring Data JPA 3.3 이하는 **지원하지 않습니다**. lambdaQL은 실행기를 `META-INF/spring.factories`에 등록하며, Spring Data는 3.4부터 이 파일에서 리포지토리 프래그먼트를 읽습니다.

<a id="123-repository-setup"></a>
## 12.3 리포지토리 설정

리포지토리의 `extends` 목록에 `LambdaQlExecutor<T>`를 추가합니다. `T`는 리포지토리의 엔티티입니다.
별도 구성과 `Impl` 클래스는 작성하지 않습니다.

이 장의 예제는 테스트 엔티티 `Member`(`id`, `name`, `active`, `grade`, `team`)와 `Team`(`id`, `name`)을 사용합니다. `grade`는 `Integer`이므로 NULL일 수 있습니다.

[`MemberRepository.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/sample/MemberRepository.java), 영역 `repository`:

```java
/** No Impl class: Spring Data finds the LambdaQlExecutor implementation by itself. */
public interface MemberRepository extends JpaRepository<Member, Long>, LambdaQlExecutor<Member>,
        JpaSpecificationExecutor<Member>, MemberRepositoryCustom {

    /** @return the active members */
    default List<Member> activeMembers() {
        return findAll(m -> m.isActive());
    }

    /**
     * @param name a name
     * @return the member with that name, or empty
     */
    default Optional<Member> byName(String name) {
        return findOne(m -> name.equals(m.getName()));
    }

    /**
     * @param n how many
     * @return the active members with the highest grades
     */
    default List<Member> topGraded(int n) {
        return query()
                .where(m -> m.isActive())
                .orderBy((m, by) -> by.desc(m.getGrade()).asc(m.getId()))
                .limit(n)
                .toList();
    }

    /**
     * @param grade the new grade
     * @return how many inactive members changed (call inside a transaction)
     */
    default int gradeInactive(int grade) {
        return updateQuery().set(m -> m.setGrade(grade)).where(m -> !m.isActive()).execute();
    }

    /** @return how many members without a team were deleted (call inside a transaction) */
    default int deleteWithoutTeam() {
        return deleteQuery().where(m -> m.getTeam() == null).execute();
    }

    /**
     * @param name a name
     * @return 1 (call inside a transaction)
     */
    default int addGuest(String name) {
        return insertQuery().values(m -> { m.setName(name); m.setActive(false); }).execute();
    }
}
```

마지막 세 메서드는 수정, 삭제, 삽입 문장을 실행합니다. 이 메서드에는 트랜잭션이 필요합니다([12.6](#126-transactions-and-exceptions) 참고).

이 리포지토리는 `JpaSpecificationExecutor<Member>`와 직접 작성한 프래그먼트 `MemberRepositoryCustom`도 상속합니다.
둘 다 lambdaQL에는 필요하지 않으며, 함께 사용할 때의 동작을 확인하기 위한 구성입니다([12.7](#127-other-fragments-and-jpaspecificationexecutor) 참고).

동작 방식:

- **default 메서드는 Spring Data가 직접 실행합니다.** Spring Data는 default 메서드에서 쿼리를 만들지 않고 메서드 본문을 호출합니다. lambdaQL은 이 동작을 변경하지 않습니다.
- **`LambdaQlExecutor<T>`는 Spring Data 리포지토리 프래그먼트입니다.** 구현 클래스는 `lambdaql-spring-data-jpa`의 `META-INF/spring.factories`에 등록되어 있습니다. Spring Data는 이 파일을 읽고, `LambdaQlExecutor<T>`를 상속하는 모든 리포지토리에 구현을 추가합니다.
- **실행기 객체 하나를 모든 리포지토리가 공유합니다.** 호출마다 해당 리포지토리의 엔티티를 사용합니다. 예를 들어 `TeamRepository`에서 `teams.count(t -> ...)`는 `Team` 행의 수를 반환합니다([`AutoConfigurationTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/AutoConfigurationTest.java) 참고).

규칙:

| # | 규칙 |
|---|---|
| 1 | 리포지토리는 `LambdaQlExecutor<T>`를 **직접**(자신의 `extends` 목록에서) 상속해야 합니다. |
| 2 | 이 목적으로 `LambdaQlExecutor<T>`를 상속하는 인터페이스에 `@NoRepositoryBean`을 붙이지 마십시오. 붙이면 Spring Data가 lambdaQL 구현을 추가하지 않습니다. |
| 3 | lambdaQL 쿼리는 **default 메서드**나 서비스에 작성하십시오. 추상 메서드는 계속 Spring Data 쿼리 메서드(`findByName` 등)로 동작합니다. |
| 4 | Spring Boot 없이도 리포지토리 부분은 일반 Spring Data이므로 동작합니다. `QueryBuilder` 빈만 자동으로 생성되지 않습니다([12.8](#128-the-querybuilder-bean) 참고). |

<a id="124-executor-methods"></a>
## 12.4 실행기 메서드

`Where<T>`는 `where(...)`와 같은 조건 람다입니다([3. 조건](03-where.md) 참고). `SortSpec<T>`는 lambdaQL 정렬입니다([4.10](04-order-paging.md#410-reusable-and-external-sorts) 참고).
`Sort`, `Pageable`, `Page`는 Spring Data 타입입니다.

| 메서드 | 반환 | 설명 |
|---|---|---|
| `query()` | `SelectQuery<T>` | 리포지토리 엔티티에 대한 쿼리 시작. 짧은 메서드로 표현할 수 없는 쿼리(`where` 여러 번, `limit`, `offset`, 실행 시점의 `Direction`)에 사용 |
| `from(Class<E>)` | `SelectQuery<E>` | 같은 영속성 유닛의 다른 엔티티에 대한 쿼리 시작 |
| `findAll(Where<T>)` | `List<T>` | 조건에 맞는 모든 행. 순서는 정해지지 않음 |
| `findAll(Where<T>, SortSpec<T>)` | `List<T>` | 조건에 맞는 모든 행을 정렬하여 반환 |
| `findAll(Where<T>, Sort)` | `List<T>` | Spring `Sort`로 정렬([12.5](#125-spring-sort-and-pageable) 참고). `Sort.unsorted()`는 정렬 없음 |
| `findAll(Where<T>, Pageable)` | `Page<T>` | 한 페이지. count 쿼리는 필요할 때만 실행. `Pageable.unpaged()`는 모든 행 |
| `findSet(Where<T>)` | `SequencedSet<T>` | 조건에 맞는 모든 행을 Set으로 반환. 순서는 정해지지 않음(`findAll(Where<T>)`과 같음). `distinct` 없음. Set의 크기가 행 수보다 작으면 `IllegalStateException`([`toSet()`](02-getting-started.md#toset) 참고) |
| `findSet(Where<T>, SortSpec<T>)` | `SequencedSet<T>` | 조건에 맞는 모든 행을 정렬 순서대로 Set으로 반환 |
| `findSet(Where<T>, Sort)` | `SequencedSet<T>` | 위와 같음. Spring `Sort`로 정렬([12.5](#125-spring-sort-and-pageable) 참고) |
| `findFirst(Where<T>)` | `Optional<T>` | 조건에 맞는 행 중 id가 가장 작은 행 |
| `findFirst(Where<T>, SortSpec<T>)` | `Optional<T>` | 지정한 순서의 첫 행. 한 행만 읽음 |
| `findFirst(Where<T>, Sort)` | `Optional<T>` | Spring `Sort` 순서의 첫 행 |
| `findOne(Where<T>)` | `Optional<T>` | 조건에 맞는 유일한 행. 2행 이상이면 `IncorrectResultSizeDataAccessException` |
| `count(Where<T>)` | `long` | 조건에 맞는 행의 수 |
| `exists(Where<T>)` | `boolean` | 조건에 맞는 행이 있으면 `true`. 최대 한 행만 읽고 엔티티는 로드하지 않음 |
| `deleteAll(Where<T>)` | `int` | 조건에 맞는 모든 행을 SQL 문장 하나로 삭제(벌크). Spring Data의 `deleteAll()`과 다름([아래](#deleteallwhere-is-not-spring-datas-deleteall) 참고). 트랜잭션 필요 |
| `deleteOne(Where<T>)` | `int` | 조건에 맞는 유일한 행을 [1행 보호](#one-row-protection-deleteone-and-updateone)를 적용해 삭제. 0 또는 1 반환. 트랜잭션 필요 |
| `updateAll(Where<T>, Assign<T>)` | `int` | 조건에 맞는 모든 행을 수정. 조건이 먼저 옴. 트랜잭션 필요 |
| `updateOne(Where<T>, Assign<T>)` | `int` | 조건에 맞는 유일한 행을 1행 보호를 적용해 수정. 0 또는 1 반환. 트랜잭션 필요 |
| `insertOne(Assign<T>)` | `int` | 한 행 삽입. 1 반환. 트랜잭션 필요 |
| `insertAll(Collection<L>, AssignEach<T, L>)` | `int` | 요소마다 한 행 삽입(문장당 값 500개 이하, 규칙 U13). 트랜잭션 필요 |
| `upsertOne(Assign<T>)` | `int` | 한 행 삽입. 같은 `@Id`의 행이 있으면 그 행을 수정. 데이터베이스가 보고한 행 수 반환. 트랜잭션 필요 |
| `upsertAll(Collection<L>, AssignEach<T, L>)` | `int` | 요소마다 한 행 업서트(행마다 문장 하나). 트랜잭션 필요 |
| `updateQuery()` | `UpdateQuery<T>` | 수정 시작: `updateQuery().set(...).where(...).execute()`([7.2](07-2-update.md) 참고). 트랜잭션 필요 |
| `deleteQuery()` | `DeleteQuery<T>` | 삭제 시작: `deleteQuery().where(...).execute()`([7.4](07-4-delete.md) 참고). 트랜잭션 필요 |
| `insertQuery()` | `InsertQuery<T>` | 삽입 시작: `insertQuery().values(...).execute()`([7.5](07-5-insert.md) 참고). 트랜잭션 필요 |
| `upsertQuery()` | `UpsertQuery<T>` | `@Id`를 키로 하는 업서트 시작([7.6](07-6-upsert.md) 참고). 트랜잭션 필요 |

짧은 메서드는 `query()`에 결과 함수를 붙인 형태와 같습니다. `findAll(w)`는 `query().where(w).toList()`, `findSet(w)`는 `query().where(w).toSet()`, `findFirst(w)`는 `query().where(w).first()`, `findOne(w)`는 `query().where(w).one()`에 해당합니다. 결과 함수는 [결과 가져오기](02-getting-started.md#getting-the-result)에서 설명합니다.
`findAll(Where<T>)`와 `count(Where<T>)`는 인자 타입이 다르므로 `JpaRepository`의 `findAll(Sort)`, `findAll(Example)`, `count()`와 충돌하지 않습니다.

**`query()`: 전체 쿼리.** [`MemberRepository.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/sample/MemberRepository.java), 영역 `query`(12.3 리포지토리의 일부):

```java
/**
 * @param n how many
 * @return the active members with the highest grades
 */
default List<Member> topGraded(int n) {
    return query()
            .where(m -> m.isActive())
            .orderBy((m, by) -> by.desc(m.getGrade()).asc(m.getId()))
            .limit(n)
            .toList();
}
```

다음 예제에서 `repository`는 `MemberRepository`입니다.

[`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java), 영역 `find-all-sort`:

```java
SortSpec<Member> byGrade = SortSpec.of(Member.class, (m, by) -> by.desc(m.getGrade()).asc(m.getId()));
List<Member> active = repository.findAll(m -> m.isActive(), byGrade);
```

[`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java), 영역 `find-first`(등급이 가장 높은 활성 회원):

```java
Optional<Member> best = repository.findFirst(m -> m.isActive(),
        SortSpec.of(Member.class, (m, by) -> by.desc(m.getGrade())));
```

[`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java), 영역 `find-one`:

```java
Optional<Member> kim = repository.findOne(m -> m.getName().equals("Kim"));
// two or more rows: Spring's IncorrectResultSizeDataAccessException
assertThrows(IncorrectResultSizeDataAccessException.class, () -> repository.findOne(m -> m.isActive()));
```

[`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java), 영역 `count-exists`:

```java
long active = repository.count(m -> m.isActive());
boolean anyInTeamB = repository.exists(m -> m.getTeam().getName().equals("B"));
```

[`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java), 영역 `find-set`:

```java
java.util.SequencedSet<Member> active = repository.findSet(m -> m.isActive(), Sort.by(Sort.Order.desc("grade"), Sort.Order.asc("id")));
java.util.SequencedSet<Member> any = repository.findSet(m -> m.isActive());
```

`active`는 `Sort`의 순서를 유지합니다. `any`는 `findAll(where)`처럼 순서가 정해지지 않습니다.

<a id="dml-shortcuts"></a>
### DML 단축 메서드

다음 8개 메서드는 수정, 삭제, 삽입, 업서트를 호출 한 번으로 실행합니다.

| 단축 메서드 | 같은 문장 |
|---|---|
| `deleteAll(where)` | `deleteQuery().where(where).execute()` |
| `deleteOne(where)` | `deleteQuery().where(where).executeOne()` |
| `updateAll(where, set)` | `updateQuery().set(set).where(where).execute()` |
| `updateOne(where, set)` | `updateQuery().set(set).where(where).executeOne()` |
| `insertOne(values)` | `insertQuery().values(values).execute()` |
| `insertAll(rows, values)` | `insertQuery().values(rows, values).execute()` |
| `upsertOne(values)` | `upsertQuery().values(values).execute()` |
| `upsertAll(rows, values)` | `upsertQuery().values(rows, values).execute()` |

- **조건이 먼저**, 대입이 다음에 옵니다: `updateAll(m -> !m.isActive(), m -> m.setGrade(7))`.
- **`All`** 은 조건에 맞는 모든 행(삭제, 수정) 또는 목록의 모든 요소(삽입, 업서트)를 뜻합니다.
- **`One`** 은 삭제와 수정에서 1행 보호(다음 절 참고), 삽입과 업서트에서 한 행을 뜻합니다.
- 호출자의 트랜잭션이 필요합니다([12.6](#126-transactions-and-exceptions) 참고). 테스트는 `TransactionTemplate` 안에서 실행합니다.

[`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java), 영역 `dml-shortcuts`:

```java
int changed = repository.updateAll(m -> !m.isActive(), m -> m.setGrade(7));      // condition first
int deleted = repository.deleteAll(m -> m.getTeam() == null);                     // bulk: no cascade
int one = repository.insertOne(m -> { m.setName("Guest"); m.setActive(false); });
int many = repository.insertAll(List.of(new NewMember("A", 1), new NewMember("B", 2)),
        (m, r) -> { m.setName(r.name()); m.setGrade(r.grade()); m.setActive(true); });
```

`NewMember`는 테스트의 레코드 `record NewMember(String name, int grade)`입니다.

<a id="one-row-protection-deleteone-and-updateone"></a>
### 1행 보호: `deleteOne`과 `updateOne`

`deleteOne(where)`와 `updateOne(where, set)`은 2행 이상을 변경하지 않습니다.

1. lambdaQL이 조건에 맞는 행의 id를 최대 2개 읽습니다.
2. id가 없으면 아무것도 변경하지 않고 0을 반환합니다.
3. id가 2개이면 아무것도 변경하지 않고 `IncorrectResultSizeDataAccessException`을 던집니다.
4. id가 1개이면 `id = :p and (조건)`으로 그 행만 변경하고 1을 반환합니다. 그사이 그 행이 조건에서 벗어났으면 0을 반환합니다.

문장 자체에 id 하나를 지정하므로, 그사이 다른 트랜잭션이 조건에 맞는 행을 추가해도 두 번째 행은 변경하지 않습니다. 이 보호는 롤백에 의존하지 않습니다.

코어 API에도 같은 보호가 있습니다: `executeOne()`([7.4](07-4-delete.md#delete-exactly-one-row-executeone) 참고).

[`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java), 영역 `dml-one`:

```java
int renamed = repository.updateOne(m -> m.getId() == kimId, m -> m.setName("Kim2"));   // exactly one row
// two or more rows match: nothing changes
assertThrows(org.springframework.dao.IncorrectResultSizeDataAccessException.class,
        () -> repository.deleteOne(m -> m.isActive()));
```

<a id="deleteallwhere-is-not-spring-datas-deleteall"></a>
### `deleteAll(where)`와 Spring Data `deleteAll()`의 차이

| 메서드 | 소속 | 삭제 방식 | cascade, `@PreRemove` |
|---|---|---|---|
| `deleteAll()` | `CrudRepository` | 모든 엔티티를 로드한 뒤 하나씩 삭제 | 실행 |
| `deleteAll(Iterable)` | `CrudRepository` | 전달한 엔티티를 하나씩 삭제 | 실행 |
| `deleteAll(Where<T>)` | `LambdaQlExecutor<T>` | SQL `delete` 문장 하나(벌크) | **실행하지 않음** |

`deleteAll(where)`는 엔티티를 로드하지 않습니다. 다른 벌크 문장과 마찬가지로 이미 로드된 엔티티에는 반영되지 않습니다(규칙 U16).

많은 행을 빠르게 삭제할 때 사용하십시오. cascade나 `@PreRemove`가 필요하면 Spring Data의 메서드를 사용하십시오.

컴파일러는 인자(인자 없음, `Iterable`, 람다)에 따라 메서드를 선택합니다. javac와 kotlinc 2.3.21의 바이트코드로 확인했습니다(실측).

<a id="builders-for-complex-statements"></a>
### 복잡한 문장을 위한 빌더

`updateQuery()`, `deleteQuery()`, `insertQuery()`, `upsertQuery()`는 완전한 문장을 시작합니다. 단축 메서드로 표현할 수 없는 경우에 사용하십시오: `set()` 여러 번, `all()`, `orderBy(...).limit(n)`([7.4](07-4-delete.md#delete-the-first-rows-limit) 참고), `toHql()`, `onConflict(...)`.

메서드 이름이 `Query`로 끝나므로 `delete(...)` 같은 `JpaRepository` 메서드와 충돌하지 않습니다. 문장은 `QueryBuilder`로 만드는 문장과 같습니다([7. 수정, 삽입, 삭제](07-dml.md) 참고). 대상 행은 같은 `where` 람다로 조회한 행과 같습니다.

단축 메서드와 마찬가지로 호출자의 트랜잭션이 필요합니다. 모든 JPA 벌크 문장과 마찬가지로, 트랜잭션 안에 이미 로드된 엔티티에는 반영되지 않습니다.

[`MemberRepository.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/sample/MemberRepository.java), 영역 `dml`(12.3 리포지토리의 일부):

```java
/**
 * @param grade the new grade
 * @return how many inactive members changed (call inside a transaction)
 */
default int gradeInactive(int grade) {
    return updateQuery().set(m -> m.setGrade(grade)).where(m -> !m.isActive()).execute();
}

/** @return how many members without a team were deleted (call inside a transaction) */
default int deleteWithoutTeam() {
    return deleteQuery().where(m -> m.getTeam() == null).execute();
}

/**
 * @param name a name
 * @return 1 (call inside a transaction)
 */
default int addGuest(String name) {
    return insertQuery().values(m -> { m.setName(name); m.setActive(false); }).execute();
}
```

업서트도 트랜잭션 안에서 실행합니다. `teams`는 `TeamRepository`이고, `tx`는 Spring `TransactionTemplate`입니다.

[`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java), 영역 `upsert`:

```java
int changed = tx.execute(s -> teams.upsertQuery().values(t -> { t.setId(teamId); t.setName("A2"); }).execute());
```

업서트는 데이터베이스가 보고한 행 수를 반환합니다. 삽입이면 1이고, 수정이면 H2와 Oracle은 1, MariaDB는 2입니다([7.6](07-6-upsert.md#the-row-count) 참고).

<a id="125-spring-sort-and-pageable"></a>
## 12.5 Spring `Sort`와 `Pageable`

`findAll(where, sort)`, `findSet(where, sort)`, `findFirst(where, sort)`, `findAll(where, pageable)`은 Spring Data 타입을 받습니다.
lambdaQL은 각 `Sort.Order`를 `SortSpec.ofPath`로 정렬 키 하나로 변환합니다([4.10](04-order-paging.md#410-reusable-and-external-sorts) 참고).

| Spring | lambdaQL의 처리 |
|---|---|
| 속성 이름(`"grade"`, `"team.name"`) | Spring Data 리포지토리와 같이 Spring Data `PropertyPath`로 검증. 없는 이름이면 `PropertyReferenceException`(패키지: Spring Data 3은 `org.springframework.data.mapping`, 4는 `org.springframework.data.core`). 경로의 끝은 엔티티가 아닌 기본 값이어야 함. `team.name` 같은 to-one 경로는 `left join`을 사용하므로 팀이 없는 행도 결과에 포함됨. 이어서 정렬 정책이 이름을 검사함([아래](#sort-policy-with-spring) 참고) |
| 정렬 정책의 별칭(`"level"`) | `PropertyPath` 검사 없이 그대로 전달. 정렬 정책이 경로로 바꿈 |
| `NullHandling.NATIVE`(기본값)의 `ASC` / `DESC` | lambdaQL NULL 규칙 적용. 오름차순은 NULL 먼저, 내림차순은 NULL 나중. 모든 데이터베이스에서 같은 순서([4.4](04-order-paging.md#44-null-ordering-is-the-same-on-every-database) 참고) |
| `NullHandling.NULLS_FIRST` / `NULLS_LAST` | 지정한 그대로 사용(`ASC_NULLS_FIRST`, `DESC_NULLS_LAST` 등) |
| `ignoreCase()` | 아직 지원하지 않음. `UnsupportedOperationException` 발생 |
| `Sort.unsorted()` | `order by` 없음 |

Spring Data JPA 자체 리포지토리는 `NATIVE`의 NULL 위치를 데이터베이스에 맡기므로 데이터베이스마다 순서가 다를 수 있습니다. lambdaQL은 자체 규칙을 적용합니다.

[`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java), 영역 `find-all-spring-sort`:

```java
List<Member> rows = repository.findAll(m -> m.isActive(),
        Sort.by(Sort.Order.desc("grade"), Sort.Order.asc("team.name"), Sort.Order.asc("id")));
// a property that does not exist: the same error as Spring Data's own repositories
RuntimeException unknown = assertThrows(RuntimeException.class,
        () -> repository.findAll(m -> m.isActive(), Sort.by("nickname")));
assertEquals("PropertyReferenceException", unknown.getClass().getSimpleName());
```

`findAll(where, pageable)`의 페이징:

- 페이지 번호는 Spring Data와 같이 0부터 시작합니다. lambdaQL은 `limit`과 `offset`으로 페이지를 읽습니다.
- 전체 개수는 `count` 쿼리로 구합니다. 페이지만으로 전체 개수가 확정되면(예: 첫 페이지의 행 수가 페이지 크기보다 적은 경우) Spring은 count 쿼리를 생략합니다.
- 정렬 키에 id가 없으면 lambdaQL이 id를 마지막 키로 추가하므로, 행이 페이지 사이에서 이동하지 않습니다([동순위 처리](04-order-paging.md#tie-breaker-automatic-id) 참고). 정렬이 없는 페이지는 id 순서입니다.
- `Pageable.unpaged()`는 모든 행을 한 페이지로 반환합니다.

[`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java), 영역 `page`:

```java
Page<Member> page = repository.findAll(m -> m.isActive(), PageRequest.of(1, 2, Sort.by("name")));
// page 1 (the second page) of the active members by name; the total comes from a count query
```

**요청 파라미터로 받는 정렬.** Spring MVC는 `?page=1&size=20&sort=name,desc` 같은 요청 파라미터로 `Pageable`을 만들 수 있습니다.
정렬 정책도 `@Sortable` 필드도 없으면 영속 속성은 모두 받으므로 사용자가 어느 컬럼으로든 정렬할 수 있습니다. 이때 lambdaQL은 엔티티와 이름마다 한 번 `LQL-5003`을 `INFO` 레벨로 기록합니다.
일부 이름만 허용하려면 아래의 정렬 정책을 사용하십시오.

<a id="sort-policy-with-spring"></a>
### Spring에서의 정렬 정책

정렬 정책(결정 D43)은 허용하는 외부 정렬 이름을 정합니다. `SortRules`, `SortMode`와 규칙은 [4.10](04-order-paging.md#sort-policy)에서 설명합니다.
Spring `Sort`나 `Pageable`은 항상 외부 정렬이므로 정책이 그 안의 이름을 모두 검사합니다. 정렬 람다는 검사하지 않습니다.

**규칙의 출처.** Spring Boot에서는 자동 구성이 `EntityManagerFactory`의 정책을 만들고, 모든 싱글턴 빈을 만든 뒤 등록합니다.
자동 구성이 읽는 곳은 세 가지입니다.

| 출처 | 제공 내용 |
|---|---|
| `SortPolicy` 빈 | 모드와 람다로 작성한 `SortRules` |
| 프로퍼티 `lambdaql.sort.*` | 모드와 문자열로 작성한 규칙 |
| `@Sortable` 필드([4.10](04-order-paging.md#rules-from-sortable)) | 빈이나 프로퍼티에 규칙이 없는 엔티티의 규칙 |

- 엔티티 하나의 규칙은 표의 순서에 따라 한 곳에서만 가져옵니다. 두 곳의 규칙을 합치지 않습니다.
- `lambdaql.sort.mode`는 빈의 모드보다 우선합니다. 둘 다 없으면 모드는 `INFO`입니다.
- 등록 조건은 `EntityManagerFactory`가 정확히 하나일 때이며, `QueryBuilder` 빈과 같습니다([12.8](#128-the-querybuilder-bean) 참고). 그 밖의 경우에는 `SortPolicy.register(emf, policy)`를 직접 호출하십시오.
- 빈도 프로퍼티도 없으면 아무것도 등록하지 않으므로 기본값을 사용합니다: `INFO`, 규칙은 `@Sortable`에서만 가져옴.

**프로퍼티.**

| 프로퍼티 | 의미 |
|---|---|
| `lambdaql.sort.mode` | `OFF`, `INFO`(기본값), `WARN`, `STRICT` |
| `lambdaql.sort.allow.<Entity>` | 허용 경로를 쉼표로 구분. 예: `name,team.name` |
| `lambdaql.sort.alias.<Entity>.<외부 이름>` | 별칭의 경로. 예: `lambdaql.sort.alias.Member.level=grade` |

**SARGable 모드 프로퍼티.** `lambdaql.sargable.mode`는 `OFF`, `WARN`(기본값), `STRICT` 중 하나입니다(결정 D50). 자동 구성이 `lambdaql.sort.mode`와 같은 방식으로 등록합니다. `STRICT`이면 인덱스를 쓸 수 없는 조건의 분석이 `NonSargableException`(`LQL-3066`)으로 실패합니다([8.9](08-procedures-functions.md#89-warning-functions-on-columns-and-indexes-sargable) 참고).

`<Entity>`에는 JPA 엔티티 이름을 씁니다. 기본값은 단순 클래스 이름이고, `@Entity(name = ...)`로 바꿀 수 있습니다.

엔티티 이름은 persistence unit 안에서 유일합니다(같은 이름이 둘이면 Hibernate가 시작 단계에서 중단). 그래서 패키지가 다른 같은 이름의 클래스도 섞이지 않습니다. 없는 이름이면 애플리케이션 시작이 `IllegalStateException`(LQL-4042)으로 중단됩니다.
경로는 규칙을 처음 사용할 때 엔티티와 대조합니다(4.10의 [문자열 규칙](04-order-paging.md#rules-as-strings) 참고).

[`SortPropertiesTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/SortPropertiesTest.java), 영역 `properties`(테스트의 프로퍼티. 애플리케이션에서는 `application.properties`나 `application.yaml`에 작성):

```java
@SpringBootTest(classes = SampleApplication.class, properties = {
        "lambdaql.sort.mode=STRICT",
        "lambdaql.sort.allow.Member=name,team.name",
        "lambdaql.sort.alias.Member.level=grade"})
```

[`SortPropertiesTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/SortPropertiesTest.java), 영역 `properties-use`:

```java
List<Member> byLevel = repository.findAll(m -> m.isActive(), Sort.by(Sort.Order.desc("level")));
List<Member> byTeam = repository.findAll(m -> m.isActive(), Sort.by("team.name", "name"));
assertThrows(SortNotAllowedException.class, () -> repository.findAll(m -> m.isActive(), Sort.by("grade")));
```

이 테스트는 자동 구성의 정책 등록도 확인합니다.

**`SortPolicy` 빈.** 컴파일러가 검사하는 규칙을 쓰려면 `SortPolicy` 빈을 정의하십시오. 같은 자동 구성 단계가 이 빈을 등록합니다.
다음 빈은 설계 예제입니다. 아래 테스트는 같은 정책을 직접 등록합니다.

```java
@Bean
public SortPolicy sortPolicy() {
    return SortPolicy.of(SortMode.STRICT,
            SortRules.of(Member.class)
                    .allow(m -> m.getName())
                    .alias("level", m -> m.getGrade()));
}
```

[`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java), 영역 `sort-policy`(영역은 `try` 안에서 끝나며, `finally` 블록에서 `SortPolicy.unregister(emf)`를 호출함):

```java
// usually a @Bean SortPolicy; the Boot auto-configuration registers it for the EntityManagerFactory
org.lambdaql.query.SortPolicy.register(emf, org.lambdaql.query.SortPolicy.of(org.lambdaql.query.SortMode.STRICT,
        org.lambdaql.query.SortRules.of(Member.class)
                .allow(m -> m.getName())
                .alias("level", m -> m.getGrade())));
try {
    // the client sends "level"; lambdaQL sorts by grade
    List<Member> rows = repository.findAll(m -> m.isActive(), Sort.by(Sort.Order.desc("level"), Sort.Order.asc("name")));
    // a property that is not in the rules
    assertThrows(org.lambdaql.SortNotAllowedException.class,
            () -> repository.findAll(m -> m.isActive(), Sort.by("grade")));
```

- `level` 같은 별칭은 `Member`의 속성이 아닙니다. lambdaQL은 Spring `PropertyPath` 검사 없이 그대로 전달하고, 정책이 `grade`로 바꿉니다.
- 별칭도 속성도 아닌 이름이면 정책 검사 전에 `PropertyReferenceException`이 발생합니다.
- `STRICT` 모드에서 규칙에 없는 속성을 지정하면 `SortNotAllowedException`(`LQL-4040`)이 발생합니다. Spring은 이 예외를 변환하지 않습니다([12.6](#126-transactions-and-exceptions) 참고).

**HTTP 400 응답.** lambdaQL은 웹 의존성이 없으므로 HTTP 상태를 정하지 않습니다. 웹 애플리케이션은 보통 `SortNotAllowedException`에 400 Bad Request로 응답합니다.
다음 핸들러는 예시입니다(테스트하지 않음).

```java
@RestControllerAdvice
public class SortErrorHandler {

    @ExceptionHandler(SortNotAllowedException.class)
    public ProblemDetail sortNotAllowed(SortNotAllowedException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setProperty("code", e.code());      // "LQL-4040"
        return problem;
    }
}
```

메시지에는 엔티티 클래스와 정렬 이름이 들어 있습니다. 클라이언트에 엔티티 이름을 노출하지 않으려면 `e.code()`만 보내십시오.

<a id="126-transactions-and-exceptions"></a>
## 12.6 트랜잭션과 예외

실행기는 현재 트랜잭션에 연결되는 Spring의 공유 `EntityManager`를 사용합니다.

**lambdaQL은 메서드에 `@Transactional`을 붙이지 않습니다.** 의도한 설계입니다(결정 D41).
수정, 삭제, 삽입, 업서트에는 호출자의 트랜잭션이 필요합니다. 단축 메서드(`deleteAll`, `updateOne` 등), 빌더(`updateQuery()` 등), `QueryBuilder` 빈 모두 같습니다.

서비스 메서드에 `@Transactional`을 붙이거나 `TransactionTemplate`을 사용하십시오. 트랜잭션이 없으면 SQL을 보내기 전에 호출이 실패합니다.

| 경우 | 동작 |
|---|---|
| `@Transactional` 안의 조회 | lambdaQL은 같은 트랜잭션에서 실행되며, 아직 flush되지 않은 변경 사항도 조회합니다. 롤백 후에는 lambdaQL에서도 그 변경 사항이 조회되지 않습니다 |
| 트랜잭션 밖의 조회 | Spring이 호출마다 새 `EntityManager`를 사용하고 호출이 끝나면 닫으므로, 반환된 엔티티는 준영속(detached) 상태입니다. Spring의 일반 동작이며 lambdaQL에서는 테스트하지 않았습니다 |
| `@Transactional` 또는 `TransactionTemplate` 안의 수정, 삭제, 삽입, 업서트 | 그 트랜잭션에서 실행되고, 트랜잭션과 함께 롤백됩니다 |
| 트랜잭션 밖의 수정, 삭제, 삽입, 업서트 | SQL을 보내기 전에 실패합니다. 아무것도 변경하지 않습니다(다음 표 참고) |

DML을 자체 트랜잭션에서 실행하는 빌더는 `new QueryBuilder(entityManagerFactory)` 하나뿐입니다([7.8](07-dml.md#transactions) 참고). Spring 애플리케이션에서는 이 빌더를 사용하지 마십시오([12.8](#128-the-querybuilder-bean) 참고).

오류는 다른 리포지토리 메서드와 마찬가지로 Spring의 예외 변환을 거칩니다.

| 경우 | 예외 |
|---|---|
| `findOne`의 조건에 맞는 행이 2개 이상 | JPA `NonUniqueResultException` 대신 Spring `IncorrectResultSizeDataAccessException` |
| `deleteOne` 또는 `updateOne`의 조건에 맞는 행이 2개 이상 | `IncorrectResultSizeDataAccessException`. 아무것도 변경하지 않음 |
| Spring `Sort`에 없는 속성 | `PropertyReferenceException` |
| Spring `Sort`의 `ignoreCase()` | `UnsupportedOperationException` |
| `STRICT` 모드에서 정렬 정책이 허용하지 않는 정렬 이름([12.5](#sort-policy-with-spring)) | `SortNotAllowedException`(`LQL-4040`), 변환하지 않음 |
| 리포지토리 호출(단축 메서드, 또는 default 메서드 안의 `execute()`)에서 트랜잭션 없이 수정, 삭제, 삽입, 업서트 실행 | `InvalidDataAccessApiUsageException`. Spring이 JPA `TransactionRequiredException`을 변환한 예외입니다. 리포지토리 메서드 밖(`QueryBuilder` 빈)에서는 JPA `TransactionRequiredException` |
| 규칙(U1, U3, U4, U6)을 어긴 대입 람다 | `LambdaQLException`(예: `TranslationException`, `LQL-3042`). 변환하지 않음([7.8](07-dml.md#rules-checked-at-analysis-time) 참고) |

`QueryBuilder` 빈(12.8)은 리포지토리가 아니므로 Spring의 예외 변환을 거치지 않습니다. 이 빈에서 `one()`과 `executeOne()`의 조건에 맞는 행이 2개 이상이면 `NonUniqueResultException`이 발생합니다.

<a id="127-other-fragments-and-jpaspecificationexecutor"></a>
## 12.7 다른 프래그먼트와 `JpaSpecificationExecutor`

`LambdaQlExecutor<T>`는 여러 프래그먼트 중 하나입니다. 12.3의 리포지토리는 다음 구성을 모두 함께 사용합니다.

| 함께 사용하는 대상 | 동작 여부 | 설명 |
|---|---|---|
| 직접 작성한 프래그먼트(`MemberRepositoryCustom`과 `MemberRepositoryImpl`) | 동작 | Spring Data가 평소와 같이 `Impl` 클래스를 찾음 |
| `JpaSpecificationExecutor<T>` | 동작 | lambdaQL 람다는 인자가 하나, `Specification` 람다는 인자가 셋이므로 Java가 항상 올바른 `findAll`을 선택 |
| `JpaRepository` 메서드(`findAll()`, `count()`, `save(...)`) | 동작 | 인자 타입이 다름 |

다음 호출은 `ExecutorTest`의 테스트 메서드 `worksWithOtherFragments`에서 가져왔습니다.

```java
repository.countByHand();                                                  // hand-written fragment
repository.findAll((root, query, cb) -> cb.isTrue(root.get("active")));    // Specification: three arguments
repository.findAll(m -> m.isActive());                                     // lambdaQL Where: one argument
repository.count();                                                        // Spring Data's own count()
```

<a id="128-the-querybuilder-bean"></a>
## 12.8 `QueryBuilder` 빈

Spring Boot에서는 자동 구성 `org.lambdaql.spring.boot.LambdaQlAutoConfiguration`이 `QueryBuilder` 빈을 만듭니다. 리포지토리 하나에 속하지 않는 쿼리는 이 빈을 서비스에 주입하여 작성합니다.

- 빈은 트랜잭션에 연결되는 Spring의 공유 `EntityManager`를 사용합니다. 따라서 리포지토리 메서드와 마찬가지로 쿼리가 현재 트랜잭션에 참여합니다.
- 빈은 `EntityManagerFactory`가 정확히 하나이고, 사용자가 `QueryBuilder` 빈을 따로 정의하지 않은 경우에만 생성됩니다.
- 리포지토리 메서드(`LambdaQlExecutor<T>`)에는 이 빈이 필요하지 않습니다.
- 이 빈으로 만든 수정, 삭제, 삽입, 업서트 문장에도 리포지토리 메서드와 마찬가지로 호출자의 트랜잭션이 필요합니다. 트랜잭션이 없으면 SQL을 보내기 전에 `TransactionRequiredException`이 발생합니다.

[`AutoConfigurationTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/AutoConfigurationTest.java), 영역 `query-builder-bean`(`qb`는 주입받은 빈):

```java
// inject it into a service: it joins the current transaction
TransactionTemplate tx = new TransactionTemplate(txManager);
long seen = tx.execute(status -> {
    members.save(new Member("Kim", true, 3, null));                       // not flushed yet
    return qb.from(Member.class).where(m -> m.getName().equals("Kim")).count();
});
```

Spring Boot 없이 사용하거나 `EntityManagerFactory` 빈이 둘 이상이면 빈을 직접 만드십시오. 자동 구성과 마찬가지로 공유 `EntityManager`를 사용합니다.

```java
@Bean
public QueryBuilder queryBuilder(EntityManagerFactory entityManagerFactory) {
    // the same as the auto-configuration: a shared, transaction-bound EntityManager
    return new QueryBuilder(SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory));
}
```

`SharedEntityManagerCreator`는 Spring 클래스(`org.springframework.orm.jpa`)입니다.
Spring 애플리케이션에서는 `new QueryBuilder(entityManagerFactory)`를 사용하지 마십시오. 이 빌더는 쿼리마다 자체 `EntityManager`를 열며, 이 `EntityManager`는 Spring 트랜잭션에 참여하지 않습니다.

**`@DataJpaTest` 테스트.** `@DataJpaTest` 슬라이스 테스트는 일부 자동 구성만 로드합니다. 리포지토리 메서드는 동작하지만 `QueryBuilder` 빈은 생성되지 않습니다.
테스트에 이 빈이 필요하면 테스트 클래스에 `@ImportAutoConfiguration(LambdaQlAutoConfiguration.class)`를 추가하십시오(아직 테스트하지 않음).

<a id="129-kotlin-repositories"></a>
## 12.9 Kotlin 리포지토리

Kotlin으로 작성한 리포지토리에는 컴파일러 설정이 필요합니다. Kotlin 2.2 이상은 기본 설정으로 동작하고, 2.2 미만은 `-Xjvm-default=all`이 필요합니다. Spring Data에는 `kotlin-reflect`도 필요합니다.
자세한 내용은 [이 페이지의 Kotlin 버전](../kotlin/12-spring-data-jpa.md#129-kotlin-compile-settings)을 참고하십시오.

<a id="1210-not-implemented-yet"></a>
## 12.10 미구현 기능

- **Spring Boot 버전.** Spring Boot 3.5.16과 4.1.2로 테스트합니다. 같은 테스트를 두 버전에서 실행합니다([13. Spring Boot 3과 4](../13-spring-boot-versions.md)).
- Spring `Sort`의 **`ignoreCase()`**.
- **Kotlin 리포지토리** 테스트.

---

[Java 트랙](README.md) | 이전: [11. 오류와 언어](../11-errors-and-languages.md) | 다음: [13. Spring Boot 3과 4](../13-spring-boot-versions.md) | [이 페이지의 Kotlin 버전](../kotlin/12-spring-data-jpa.md)
