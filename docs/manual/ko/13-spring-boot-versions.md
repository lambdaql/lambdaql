<a id="13-spring-boot-3-and-4"></a>
# 13. Spring Boot 3과 4

[매뉴얼 홈](README.md) | 트랙: [Java](java/README.md) | [Kotlin](kotlin/README.md) | 이전: 12. Spring Data JPA ([Java](java/12-spring-data-jpa.md) | [Kotlin](kotlin/12-spring-data-jpa.md))

이 페이지는 두 언어 모두를 위한 것입니다.

lambdaQL은 Spring Boot 3(Hibernate 6.6)과 Spring Boot 4(Hibernate 7.4)를 함께 지원합니다.
코어 `lambdaql`과 Spring Data JPA 모듈 `lambdaql-spring-data-jpa`는 두 버전에 공통인 jar입니다. 스타터만 Spring Boot 버전마다 따로 있고, 이름 끝이 `-boot3` 또는 `-boot4`입니다.
두 공통 jar는 Spring Boot 4로 컴파일하며, 같은 jar가 Spring Boot 3 런타임에서도 동작합니다.

<a id="131-versions"></a>
## 13.1 버전

| 항목 | Spring Boot 3 | Spring Boot 4 |
|---|---|---|
| 테스트한 Spring Boot | 3.5.16 | 4.1.2 |
| Hibernate ORM | 6.6(6.6.53으로 테스트) | 7.4(7.4.13으로 테스트) |
| Jakarta Persistence | 3.1 | 3.2 |
| Spring Data JPA | 3.5(3.5.13으로 테스트) | 4.1(4.1.2로 테스트) |
| 코어 | `lambdaql` | `lambdaql`(같은 jar) |
| Spring Data JPA 모듈 | `lambdaql-spring-data-jpa` | `lambdaql-spring-data-jpa`(같은 jar) |
| 스타터 | `lambdaql-spring-boot-starter-boot3` | `lambdaql-spring-boot-starter-boot4` |
| Java | 21 이상 | 21 이상 |

- Java 24 이상에서는 두 버전 모두 JDK의 `java.lang.classfile`로 람다 표현식을 읽습니다. Java 21~23에서는 ASM을 씁니다.
- Spring Data JPA 3.3 이하는 지원하지 않습니다. lambdaQL은 실행기를 `META-INF/spring.factories`에 등록하며, Spring Data는 3.4부터 이 파일에서 리포지토리 프래그먼트를 읽습니다.
- Spring Boot 3.4.2(Hibernate 6.6.5)에서도 동작을 확인했습니다(2026-11-19 실측). 3.4는 지원이 끝난 라인이므로 정식 지원 대상은 3.5입니다.

<a id="132-dependencies"></a>
## 13.2 의존성 추가

아티팩트는 Maven Central에 있습니다(groupId `io.github.lambdaql`).

Spring Boot 4, Gradle(Groovy DSL):

```groovy
dependencies {
    implementation 'io.github.lambdaql:lambdaql-spring-boot-starter-boot4:0.0.2'
}
```

Spring Boot 3, Gradle(Kotlin DSL):

```gradle-kts
dependencies {
    implementation("io.github.lambdaql:lambdaql-spring-boot-starter-boot3:0.0.2")
}
```

Maven:

```xml
<dependency>
  <groupId>io.github.lambdaql</groupId>
  <artifactId>lambdaql-spring-boot-starter-boot4</artifactId>
  <version>0.0.2</version>
</dependency>
```

- Spring Boot 없이 Spring Data JPA만 쓰면 `lambdaql-spring-data-jpa`를 추가하십시오. Spring Data JPA 3.5와 4.1 어느 쪽에서도 동작하며, Spring Data JPA는 프로젝트에 있는 것을 씁니다(이 아티팩트가 버전을 바꾸지 않음).
- Spring 없이 JPA만 쓰면 `lambdaql` 하나면 됩니다. Hibernate 6.6과 7.4 어느 쪽에서도 동작합니다.
- 스타터는 프로젝트의 Spring Boot 버전에 맞추십시오. 스타터는 그 버전의 `spring-boot-starter-data-jpa`를 가져오므로, Spring Boot 4 프로젝트에 `-boot3` 스타터를 넣으면 Spring Data와 Hibernate의 버전이 섞일 수 있습니다.

<a id="133-what-is-the-same"></a>
## 13.3 같은 것

- API, 람다 표현식 작성 규칙, 오류 코드, 설정(`lambdaql.*` 프로퍼티)이 같습니다.
- lambdaQL이 만드는 **HQL이 같습니다**. 두 버전이 같은 `lambdaql`을 쓰기 때문입니다.
- 쿼리가 반환하는 **행이 같습니다**. 검증 범위는 13.5에 정리했습니다.

Hibernate가 HQL을 SQL로 바꾸는 방식은 버전마다 조금 다릅니다. 매뉴얼의 DB별 SQL 블록은 Hibernate 7.4의 것입니다. 차이는 13.4에 정리했습니다.

<a id="134-sql-differences"></a>
## 13.4 Hibernate 6.6과 7.4의 SQL 차이

같은 HQL에 대해 두 버전이 만든 SQL을 비교했습니다(2026-11-23 실측). 차이는 다섯 가지입니다.

| 차이 | 데이터베이스 | Hibernate 6.6 | Hibernate 7.4 |
|---|---|---|---|
| boolean 상수 | MariaDB, MySQL, Oracle | `o1_0.active=1` | `o1_0.active=true` |
| 파생 테이블의 열 이름 | 다섯 DB | `select t1_0.id from (select o2_0.id id ...) t1_0` | `select t1_0.c0 from (select o2_0.id c0 ...) t1_0` |
| UPDATE·DELETE의 테이블 별칭 | MariaDB, MySQL | `update orders o1_0 ... o1_0.customer_id` | `update orders ... orders.customer_id` |
| UPDATE의 임베디드 열 | Oracle | `set o1_0.city=?,o1_0.zip=?` | `set city=?,zip=?` |
| UPDATE에서 대상 테이블을 읽는 서브쿼리 | MySQL | Hibernate가 그대로 써서 MySQL 오류 1093 | Hibernate가 파생 테이블을 더함 |

마지막 차이는 lambdaQL이 HQL 단계에서 처리합니다. UPDATE의 서브쿼리가 대상 테이블을 읽고 바깥 행을 쓰지 않으면 파생 테이블로 읽습니다(규칙 U22).
그래서 두 버전 모두에서 같은 행을 수정합니다. 바깥 행을 읽는 상관 서브쿼리는 MySQL과 Hibernate 6.6의 조합에서 아직 오류 1093입니다.

버전별 전체 SQL은 다음 위치에서 볼 수 있습니다.

- Hibernate 7.4: [`query/src/test/resources/expected-sql/`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/)
- Hibernate 6.6: [`query/src/test/resources/expected-sql/hibernate6/`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/hibernate6/)

<a id="135-what-is-tested"></a>
## 13.5 검증 범위

| 항목 | 값 |
|---|---|
| Spring Boot | 3.5.16, 4.1.2 |
| 데이터베이스 | H2 2.3, MariaDB 10.11, MySQL 8.4, PostgreSQL 17, Oracle 23.26 |
| JDK | 21(ASM으로 읽음, 두 Spring Boot 버전), 25(`java.lang.classfile`로 읽음, jar의 `META-INF/versions/24`, Spring Boot 4) |

JDK 25의 `java.lang.classfile` 리더는 Spring Boot 4에서 확인했습니다. 리더는 Hibernate와 무관합니다.
JDK 24에서의 실행은 확인하지 않았습니다(미검증).

---

[매뉴얼 홈](README.md) | 이전: 12. Spring Data JPA ([Java](java/12-spring-data-jpa.md) | [Kotlin](kotlin/12-spring-data-jpa.md))
