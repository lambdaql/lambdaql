<a id="13-spring-boot-3-and-4"></a>
# 13. Spring Boot 3 and 4

[Manual home](README.md) | Tracks: [Java](java/README.md) | [Kotlin](kotlin/README.md) | Previous: 12. Spring Data JPA ([Java](java/12-spring-data-jpa.md) | [Kotlin](kotlin/12-spring-data-jpa.md))

This page is for both languages.

lambdaQL supports both Spring Boot 3 (Hibernate 6.6) and Spring Boot 4 (Hibernate 7.4).
The core `lambdaql` and the Spring Data JPA module `lambdaql-spring-data-jpa` are jars shared by both versions. Only the starter is separate for each Spring Boot version, and its name ends with `-boot3` or `-boot4`.
The two shared jars are compiled with Spring Boot 4, and the same jars also work on a Spring Boot 3 runtime.

<a id="131-versions"></a>
## 13.1 Versions

| Item | Spring Boot 3 | Spring Boot 4 |
|---|---|---|
| Tested Spring Boot | 3.5.16 | 4.1.2 |
| Hibernate ORM | 6.6 (tested with 6.6.53) | 7.4 (tested with 7.4.13) |
| Jakarta Persistence | 3.1 | 3.2 |
| Spring Data JPA | 3.5 (tested with 3.5.13) | 4.1 (tested with 4.1.2) |
| Core | `lambdaql` | `lambdaql` (the same jar) |
| Spring Data JPA module | `lambdaql-spring-data-jpa` | `lambdaql-spring-data-jpa` (the same jar) |
| Starter | `lambdaql-spring-boot-starter-boot3` | `lambdaql-spring-boot-starter-boot4` |
| Java | 21 or later | 21 or later |

- On Java 24 or later, both versions read lambda expressions with the JDK's `java.lang.classfile`. On Java 21 to 23, they use ASM.
- Spring Data JPA 3.3 or earlier is not supported. lambdaQL registers its executor in `META-INF/spring.factories`, and Spring Data reads repository fragments from this file since 3.4.
- It was also confirmed to work on Spring Boot 3.4.2 (Hibernate 6.6.5) (measured on 2026-11-19). The 3.4 line is out of support, so the officially supported version is 3.5.

<a id="132-dependencies"></a>
## 13.2 Adding the dependencies

The artifacts are on Maven Central (groupId `io.github.lambdaql`).

Spring Boot 4, Gradle (Groovy DSL):

```groovy
dependencies {
    implementation 'io.github.lambdaql:lambdaql-spring-boot-starter-boot4:0.0.2'
}
```

Spring Boot 3, Gradle (Kotlin DSL):

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

- If you use only Spring Data JPA without Spring Boot, add `lambdaql-spring-data-jpa`. It works with both Spring Data JPA 3.5 and 4.1, and it uses the Spring Data JPA that your project has (this artifact does not change the version).
- If you use only JPA without Spring, `lambdaql` alone is enough. It works with both Hibernate 6.6 and 7.4.
- Match the starter to the Spring Boot version of your project. The starter brings in the `spring-boot-starter-data-jpa` of its version, so if you put the `-boot3` starter into a Spring Boot 4 project, the Spring Data and Hibernate versions can get mixed.

<a id="133-what-is-the-same"></a>
## 13.3 What is the same

- The API, the rules for writing lambda expressions, the error codes and the configuration (`lambdaql.*` properties) are the same.
- **The HQL that lambdaQL builds is the same**, because both versions use the same `lambdaql`.
- **The rows that a query returns are the same**. The verified scope is listed in 13.5.

The way Hibernate turns HQL into SQL is a little different in each version. The SQL blocks by database in this manual come from Hibernate 7.4. The differences are listed in 13.4.

<a id="134-sql-differences"></a>
## 13.4 SQL differences between Hibernate 6.6 and 7.4

We compared the SQL that the two versions build for the same HQL (measured on 2026-11-23). There are five differences.

| Difference | Databases | Hibernate 6.6 | Hibernate 7.4 |
|---|---|---|---|
| Boolean constants | MariaDB, MySQL, Oracle | `o1_0.active=1` | `o1_0.active=true` |
| Column names of a derived table | All five databases | `select t1_0.id from (select o2_0.id id ...) t1_0` | `select t1_0.c0 from (select o2_0.id c0 ...) t1_0` |
| Table alias in UPDATE and DELETE | MariaDB, MySQL | `update orders o1_0 ... o1_0.customer_id` | `update orders ... orders.customer_id` |
| Embedded columns in UPDATE | Oracle | `set o1_0.city=?,o1_0.zip=?` | `set city=?,zip=?` |
| A subquery in UPDATE that reads the target table | MySQL | Hibernate writes it as it is, and MySQL gives error 1093 | Hibernate adds a derived table |

lambdaQL handles the last difference at the HQL step. If a subquery in UPDATE reads the target table and does not use the outer row, lambdaQL reads it through a derived table.
So both versions update the same rows. A correlated subquery that reads the outer row still gives error 1093 with MySQL and Hibernate 6.6.

The full SQL for each version is here:

- Hibernate 7.4: [`query/src/test/resources/expected-sql/`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/resources/expected-sql/)
- Hibernate 6.6: [`query/src/test/resources/expected-sql/hibernate6/`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/resources/expected-sql/hibernate6/)

<a id="135-what-is-tested"></a>
## 13.5 Verified scope

| Item | Value |
|---|---|
| Spring Boot | 3.5.16, 4.1.2 |
| Databases | H2 2.3, MariaDB 10.11, MySQL 8.4, PostgreSQL 17, Oracle 23.26 |
| JDK | 21 (read with ASM, both Spring Boot versions), 25 (read with `java.lang.classfile`, `META-INF/versions/24` of the jar, Spring Boot 4) |

The `java.lang.classfile` reader on JDK 25 was confirmed with Spring Boot 4. The reader does not depend on Hibernate.
Running on JDK 24 was not checked (not verified).

---

[Manual home](README.md) | Previous: 12. Spring Data JPA ([Java](java/12-spring-data-jpa.md) | [Kotlin](kotlin/12-spring-data-jpa.md))
