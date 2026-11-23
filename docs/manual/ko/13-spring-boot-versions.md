<a id="13-spring-boot-3-and-4"></a>
# 13. Spring Boot 3과 4

[매뉴얼 홈](README.md) | 트랙: [Java](java/README.md) | [Kotlin](kotlin/README.md) | 이전: 12. Spring Data JPA ([Java](java/12-spring-data-jpa.md) | [Kotlin](kotlin/12-spring-data-jpa.md))

이 페이지는 두 언어 모두를 위한 것입니다.

lambdaQL은 Spring Boot 3(Hibernate 6.6)과 Spring Boot 4(Hibernate 7.4)를 함께 지원합니다(결정 D55).
코어 `lambdaql-core`는 두 버전에 공통인 jar 하나입니다. Spring Data JPA 모듈과 스타터만 Spring Boot 버전마다 따로 있고, 이름 끝이 `-boot3` 또는 `-boot4`입니다.

<a id="131-versions"></a>
## 13.1 버전

| 항목 | Spring Boot 3 | Spring Boot 4 |
|---|---|---|
| 테스트한 Spring Boot | 3.5.16 | 4.1.2 |
| Hibernate ORM | 6.6(6.6.53으로 테스트) | 7.4(7.4.13으로 테스트) |
| Jakarta Persistence | 3.1 | 3.2 |
| Spring Data JPA | 3.5(3.5.13으로 테스트) | 4.1(4.1.2로 테스트) |
| 코어 | `lambdaql-core` | `lambdaql-core`(같은 jar) |
| Spring Data JPA 모듈 | `lambdaql-spring-data-jpa-boot3` | `lambdaql-spring-data-jpa-boot4` |
| 스타터 | `lambdaql-spring-boot-starter-boot3` | `lambdaql-spring-boot-starter-boot4` |
| Java | 21 이상 | 21 이상 |

- Java 24 이상에서는 두 버전 모두 JDK의 `java.lang.classfile`로 람다를 읽습니다(결정 D52). Java 21~23에서는 ASM을 씁니다.
- Spring Data JPA 3.3 이하는 지원하지 않습니다. lambdaQL은 실행기를 `META-INF/spring.factories`에 등록하며, Spring Data는 3.4부터 이 파일에서 리포지토리 프래그먼트를 읽습니다.
- Spring Boot 3.4.2(Hibernate 6.6.5)에서도 같은 테스트가 통과했습니다(2026-11-19 실측). 3.4는 지원이 끝난 라인이라 정식 테스트 대상은 3.5입니다.

<a id="132-dependencies"></a>
## 13.2 의존성 추가

아티팩트는 **아직 Maven Central에 배포되지 않았습니다**. 먼저 소스에서 빌드하여 로컬 Maven 저장소에 배포하십시오.

Spring Boot 4, Gradle(Groovy DSL):

```groovy
dependencies {
    implementation 'org.lambdaql:lambdaql-spring-boot-starter-boot4:1.0.0'
}
```

Spring Boot 3, Gradle(Kotlin DSL):

```kotlin
dependencies {
    implementation("org.lambdaql:lambdaql-spring-boot-starter-boot3:1.0.0")
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

- Spring Boot 없이 Spring Data JPA만 쓰면 Spring Data 버전에 맞는 `lambdaql-spring-data-jpa-boot4`(Spring Data 4) 또는 `lambdaql-spring-data-jpa-boot3`(Spring Data 3)을 추가하십시오.
- Spring 없이 JPA만 쓰면 `lambdaql-core` 하나면 됩니다. Hibernate 6.6과 7.4 어느 쪽에서도 동작합니다.
- 다른 Spring Boot 버전의 모듈을 섞지 마십시오. 예를 들어 Spring Boot 4 프로젝트에 `-boot3` 모듈을 넣으면 Spring Data 클래스를 찾지 못합니다.

<a id="133-what-is-the-same"></a>
## 13.3 같은 것

- API, 람다 작성 규칙, 오류 코드, 설정(`lambdaql.*` 프로퍼티)이 같습니다.
- lambdaQL이 만드는 **HQL이 같습니다**. 두 버전이 같은 `lambdaql-core`를 쓰기 때문입니다.
- 쿼리가 반환하는 **행이 같습니다**. 같은 테스트를 두 버전에서 실행해 행을 비교합니다(13.5).

Hibernate가 HQL을 SQL로 바꾸는 방식은 버전마다 조금 다릅니다. 매뉴얼의 DB별 SQL 블록은 Hibernate 7.4의 것입니다. 차이는 13.4에 정리했습니다.

<a id="134-sql-differences"></a>
## 13.4 Hibernate 6.6과 7.4의 SQL 차이

같은 HQL에 대해 두 버전이 만든 SQL을 비교한 결과입니다(2026-11-23 실측).
비교 대상은 매뉴얼 예제 129개(구현된 쿼리 88개, 계획된 쿼리의 HQL 41개)입니다.

| 데이터베이스 | SQL이 다른 예제 |
|---|---|
| H2 2.3 | 3 |
| PostgreSQL 17 | 3 |
| Oracle 23.26 | 16 |
| MariaDB 10.11 | 28 |
| MySQL 8.4 | 28 |

차이는 다섯 가지입니다.

| 차이 | 데이터베이스 | Hibernate 6.6 | Hibernate 7.4 |
|---|---|---|---|
| boolean 상수 | MariaDB, MySQL, Oracle | `o1_0.active=1` | `o1_0.active=true` |
| 파생 테이블의 열 이름 | 다섯 DB | `select t1_0.id from (select o2_0.id id ...) t1_0` | `select t1_0.c0 from (select o2_0.id c0 ...) t1_0` |
| UPDATE·DELETE의 테이블 별칭 | MariaDB, MySQL | `update orders o1_0 ... o1_0.customer_id` | `update orders ... orders.customer_id` |
| UPDATE의 임베디드 열 | Oracle | `set o1_0.city=?,o1_0.zip=?` | `set city=?,zip=?` |
| UPDATE에서 대상 테이블을 읽는 서브쿼리 | MySQL | Hibernate가 그대로 써서 MySQL 오류 1093 | Hibernate가 파생 테이블을 더함 |

마지막 차이는 lambdaQL이 HQL 단계에서 처리합니다. UPDATE의 서브쿼리가 대상 테이블을 읽고 바깥 행을 쓰지 않으면 파생 테이블로 읽습니다(규칙 U22).
그래서 두 버전 모두에서 같은 행을 수정합니다. 바깥 행을 읽는 상관 서브쿼리는 MySQL과 Hibernate 6.6의 조합에서 아직 오류 1093입니다.

정답 파일의 위치는 다음과 같습니다.

- Hibernate 7.4: [`query/src/test/resources/expected-sql/`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/)
- Hibernate 6.6: [`query-test-boot3/src/test/resources/expected-sql/`](https://github.com/codebit0/lambdaql/blob/main/query-test-boot3/src/test/resources/expected-sql/)

<a id="135-what-is-tested"></a>
## 13.5 검증 범위

| 항목 | 값 |
|---|---|
| Spring Boot | 3.5.16, 4.1.2 |
| 데이터베이스 | H2 2.3, MariaDB 10.11, MySQL 8.4, PostgreSQL 17, Oracle 23.26 |
| JDK | 21(ASM으로 읽음), 25(`java.lang.classfile`로 읽음, jar의 `META-INF/versions/24`) |
| 테스트 | Spring Boot 버전마다 같은 테스트 250개(코어 232개, Spring Data JPA 모듈 18개) |

코어 테스트는 한 벌입니다. Spring Boot 4로는 `query` 모듈에서, Spring Boot 3으로는 테스트 전용 모듈 `query-test-boot3`에서 실행합니다.
JDK 24는 이 저장소의 테스트 환경에 없어 실행을 확인하지 않았습니다.

---

[매뉴얼 홈](README.md) | 이전: 12. Spring Data JPA ([Java](java/12-spring-data-jpa.md) | [Kotlin](kotlin/12-spring-data-jpa.md))
