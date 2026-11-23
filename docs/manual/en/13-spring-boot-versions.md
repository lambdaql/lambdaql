<a id="13-spring-boot-3-and-4"></a>
# 13. Spring Boot 3 and 4

[Manual home](README.md) | Tracks: [Java](java/README.md) | [Kotlin](kotlin/README.md) | Previous: 12. Spring Data JPA ([Java](java/12-spring-data-jpa.md) | [Kotlin](kotlin/12-spring-data-jpa.md))

This page is for both languages.

lambdaQL supports both Spring Boot 3 (Hibernate 6.6) and Spring Boot 4 (Hibernate 7.4) (decision D55).
The core `lambdaql-core` and the Spring Data JPA module `lambdaql-spring-data-jpa` are one jar each, the same for both versions. Only the starter has one artifact per Spring Boot version; its name ends with `-boot3` or `-boot4`.
The two common jars are compiled with Spring Boot 4, and the same compiled classes are also tested on the Spring Boot 3 runtime.

<a id="131-versions"></a>
## 13.1 Versions

| Item | Spring Boot 3 | Spring Boot 4 |
|---|---|---|
| Spring Boot tested | 3.5.16 | 4.1.2 |
| Hibernate ORM | 6.6 (tested with 6.6.53) | 7.4 (tested with 7.4.13) |
| Jakarta Persistence | 3.1 | 3.2 |
| Spring Data JPA | 3.5 (tested with 3.5.13) | 4.1 (tested with 4.1.2) |
| Core | `lambdaql-core` | `lambdaql-core` (the same jar) |
| Spring Data JPA module | `lambdaql-spring-data-jpa` | `lambdaql-spring-data-jpa` (the same jar) |
| Starter | `lambdaql-spring-boot-starter-boot3` | `lambdaql-spring-boot-starter-boot4` |
| Java | 21 or newer | 21 or newer |

- On Java 24 or newer, both versions read lambdas with the JDK's `java.lang.classfile` (decision D52). On Java 21 to 23, lambdaQL uses ASM.
- Spring Data JPA 3.3 and older are not supported. lambdaQL registers its executor in `META-INF/spring.factories`, and Spring Data reads repository fragments from this file only from 3.4.
- The same tests also passed on Spring Boot 3.4.2 (Hibernate 6.6.5) (measured on 2026-11-19). 3.4 is a line whose support has ended, so the official test target is 3.5.

<a id="132-dependencies"></a>
## 13.2 Adding the dependency

The artifacts are **not published to Maven Central yet**. Build them from source and publish them to your local Maven repository first.

Spring Boot 4, Gradle (Groovy DSL):

```groovy
dependencies {
    implementation 'org.lambdaql:lambdaql-spring-boot-starter-boot4:1.0.0'
}
```

Spring Boot 3, Gradle (Kotlin DSL):

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

- With Spring Data JPA but without Spring Boot, add `lambdaql-spring-data-jpa`. It works with both Spring Data JPA 3.5 and 4.1, and it uses the Spring Data JPA that your project has (this artifact does not change the version).
- With plain JPA and no Spring, `lambdaql-core` alone is enough. It works with both Hibernate 6.6 and 7.4.
- Pick the starter for the Spring Boot version of your project. A starter brings the `spring-boot-starter-data-jpa` of its version, so a `-boot3` starter in a Spring Boot 4 project can mix versions of Spring Data and Hibernate.

<a id="133-what-is-the-same"></a>
## 13.3 What is the same

- The API, the rules for writing lambdas, the error codes and the settings (`lambdaql.*` properties) are the same.
- **The HQL that lambdaQL builds is the same**, because both versions use the same `lambdaql-core`.
- **The rows that a query returns are the same.** The same tests run on both versions and compare the rows (13.5).

The way Hibernate turns HQL into SQL differs a little between versions. The SQL blocks by database in this manual are from Hibernate 7.4. The differences are listed in 13.4.

<a id="134-sql-differences"></a>
## 13.4 SQL differences between Hibernate 6.6 and 7.4

This is the result of comparing the SQL that the two versions make for the same HQL (measured on 2026-11-23).
The comparison covers 129 manual examples (88 implemented queries, and the HQL of 41 planned queries).

| Database | Examples with different SQL |
|---|---|
| H2 2.3 | 3 |
| PostgreSQL 17 | 3 |
| Oracle 23.26 | 16 |
| MariaDB 10.11 | 28 |
| MySQL 8.4 | 28 |

There are five differences.

| Difference | Databases | Hibernate 6.6 | Hibernate 7.4 |
|---|---|---|---|
| Boolean constants | MariaDB, MySQL, Oracle | `o1_0.active=1` | `o1_0.active=true` |
| Column names of a derived table | all five | `select t1_0.id from (select o2_0.id id ...) t1_0` | `select t1_0.c0 from (select o2_0.id c0 ...) t1_0` |
| Table alias in UPDATE and DELETE | MariaDB, MySQL | `update orders o1_0 ... o1_0.customer_id` | `update orders ... orders.customer_id` |
| Embedded columns in UPDATE | Oracle | `set o1_0.city=?,o1_0.zip=?` | `set city=?,zip=?` |
| A subquery in UPDATE that reads the target table | MySQL | Hibernate writes it as it is, and MySQL gives error 1093 | Hibernate adds a derived table |

lambdaQL handles the last difference at the HQL step. When a subquery in an UPDATE reads the target table and does not use the outer row, lambdaQL reads it through a derived table (rule U22).
So the same rows are updated on both versions. A correlated subquery that reads the outer row still gives error 1093 with MySQL and Hibernate 6.6.

The expected SQL files are here:

- Hibernate 7.4: [`query/src/test/resources/expected-sql/`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/)
- Hibernate 6.6: [`query/src/test/resources/expected-sql/hibernate6/`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/hibernate6/)

<a id="135-what-is-tested"></a>
## 13.5 What is tested

| Item | Value |
|---|---|
| Spring Boot | 3.5.16, 4.1.2 |
| Databases | H2 2.3, MariaDB 10.11, MySQL 8.4, PostgreSQL 17, Oracle 23.26 |
| JDK | 21 (read with ASM, both Spring Boot versions), 25 (read with `java.lang.classfile`, `META-INF/versions/24` of the jar, Spring Boot 4) |
| Tests | the same 250 tests for each Spring Boot version (232 in the core, 18 in the Spring Data JPA module) |

There is one set of tests, compiled once. The `test` task of each module runs them on Spring Boot 4, and the `testBoot3` task runs the same compiled classes on the Spring Boot 3 runtime. The `java.lang.classfile` reader (JDK 25) runs on the Spring Boot 4 runtime (the reader does not depend on Hibernate).
JDK 24 is not in the test environment of this repository, so runs on it were not checked.

---

[Manual home](README.md) | Previous: 12. Spring Data JPA ([Java](java/12-spring-data-jpa.md) | [Kotlin](kotlin/12-spring-data-jpa.md))
