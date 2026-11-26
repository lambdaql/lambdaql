<a id="13-spring-boot-3-and-4"></a>
# 13. Spring Boot 3 与 4

[手册首页](README.md) | 路线：[Java](java/README.md) | [Kotlin](kotlin/README.md) | 上一章：12. Spring Data JPA（[Java](java/12-spring-data-jpa.md) | [Kotlin](kotlin/12-spring-data-jpa.md)）

本页适用于两种语言。

lambdaQL 同时支持 Spring Boot 3（Hibernate 6.6）和 Spring Boot 4（Hibernate 7.4）。
核心 `lambdaql` 和 Spring Data JPA 模块 `lambdaql-spring-data-jpa` 是两个版本共用的 jar。只有 starter 按 Spring Boot 版本分开提供，名称以 `-boot3` 或 `-boot4` 结尾。
两个共用 jar 用 Spring Boot 4 编译，同一个 jar 也能在 Spring Boot 3 运行时上工作。

<a id="131-versions"></a>
## 13.1 版本

| 项目 | Spring Boot 3 | Spring Boot 4 |
|---|---|---|
| 测试的 Spring Boot | 3.5.16 | 4.1.2 |
| Hibernate ORM | 6.6（已用 6.6.53 测试） | 7.4（已用 7.4.13 测试） |
| Jakarta Persistence | 3.1 | 3.2 |
| Spring Data JPA | 3.5（已用 3.5.13 测试） | 4.1（已用 4.1.2 测试） |
| 核心 | `lambdaql` | `lambdaql`（同一个 jar） |
| Spring Data JPA 模块 | `lambdaql-spring-data-jpa` | `lambdaql-spring-data-jpa`（同一个 jar） |
| starter | `lambdaql-spring-boot-starter-boot3` | `lambdaql-spring-boot-starter-boot4` |
| Java | 21 及以上 | 21 及以上 |

- 在 Java 24 及以上版本中，两个版本都用 JDK 的 `java.lang.classfile` 读取 Lambda 表达式。在 Java 21~23 中使用 ASM。
- 不支持 Spring Data JPA 3.3 及更早版本。lambdaQL 在 `META-INF/spring.factories` 中注册执行器，而 Spring Data 从 3.4 起才从这个文件读取 repository fragment。
- 在 Spring Boot 3.4.2（Hibernate 6.6.5）上也确认了运行（2026-11-19 实测）。3.4 是已结束支持的版本线，因此正式支持对象是 3.5。

<a id="132-dependencies"></a>
## 13.2 添加依赖

构件发布在 Maven Central 上（groupId `io.github.lambdaql`）。

Spring Boot 4，Gradle（Groovy DSL）：

```groovy
dependencies {
    implementation 'io.github.lambdaql:lambdaql-spring-boot-starter-boot4:0.0.2'
}
```

Spring Boot 3，Gradle（Kotlin DSL）：

```gradle-kts
dependencies {
    implementation("io.github.lambdaql:lambdaql-spring-boot-starter-boot3:0.0.2")
}
```

Maven：

```xml
<dependency>
  <groupId>io.github.lambdaql</groupId>
  <artifactId>lambdaql-spring-boot-starter-boot4</artifactId>
  <version>0.0.2</version>
</dependency>
```

- 不使用 Spring Boot 而只使用 Spring Data JPA 时，请添加 `lambdaql-spring-data-jpa`。它在 Spring Data JPA 3.5 和 4.1 上都能工作，Spring Data JPA 使用项目中已有的版本（这个构件不改变版本）。
- 不使用 Spring 而只使用 JPA 时，只需要 `lambdaql`。它在 Hibernate 6.6 和 7.4 上都能工作。
- 请让 starter 与项目的 Spring Boot 版本对应。starter 会带入该版本的 `spring-boot-starter-data-jpa`，因此在 Spring Boot 4 项目中加入 `-boot3` starter 时，Spring Data 和 Hibernate 的版本可能混在一起。

<a id="133-what-is-the-same"></a>
## 13.3 相同之处

- API、Lambda 表达式的编写规则、错误码和配置（`lambdaql.*` 属性）都相同。
- lambdaQL 生成的 **HQL 相同**。因为两个版本使用同一个 `lambdaql`。
- 查询返回的**行相同**。验证范围整理在 13.5 中。

Hibernate 把 HQL 转换为 SQL 的方式在各版本之间略有不同。手册中各数据库的 SQL 块来自 Hibernate 7.4。差异整理在 13.4 中。

<a id="134-sql-differences"></a>
## 13.4 Hibernate 6.6 与 7.4 的 SQL 差异

对同一 HQL 比较了两个版本生成的 SQL（2026-11-23 实测）。差异有五种。

| 差异 | 数据库 | Hibernate 6.6 | Hibernate 7.4 |
|---|---|---|---|
| boolean 常量 | MariaDB、MySQL、Oracle | `o1_0.active=1` | `o1_0.active=true` |
| 派生表的列名 | 五种数据库 | `select t1_0.id from (select o2_0.id id ...) t1_0` | `select t1_0.c0 from (select o2_0.id c0 ...) t1_0` |
| UPDATE、DELETE 的表别名 | MariaDB、MySQL | `update orders o1_0 ... o1_0.customer_id` | `update orders ... orders.customer_id` |
| UPDATE 中的嵌入列 | Oracle | `set o1_0.city=?,o1_0.zip=?` | `set city=?,zip=?` |
| UPDATE 中读取目标表的子查询 | MySQL | Hibernate 原样写出，导致 MySQL 错误 1093 | Hibernate 加上派生表 |

最后一项差异由 lambdaQL 在 HQL 阶段处理。UPDATE 的子查询读取目标表且不使用外层行时，通过派生表读取。
因此两个版本更新的行相同。读取外层行的关联子查询在 MySQL 与 Hibernate 6.6 的组合下仍会出现错误 1093。

各版本的完整 SQL 见以下位置。

- Hibernate 7.4：[`query/src/test/resources/expected-sql/`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/resources/expected-sql/)
- Hibernate 6.6：[`query/src/test/resources/expected-sql/hibernate6/`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/resources/expected-sql/hibernate6/)

<a id="135-what-is-tested"></a>
## 13.5 验证范围

| 项目 | 值 |
|---|---|
| Spring Boot | 3.5.16、4.1.2 |
| 数据库 | H2 2.3、MariaDB 10.11、MySQL 8.4、PostgreSQL 17、Oracle 23.26 |
| JDK | 21（用 ASM 读取，两个 Spring Boot 版本）、25（用 `java.lang.classfile` 读取，jar 中的 `META-INF/versions/24`，Spring Boot 4） |

JDK 25 的 `java.lang.classfile` 读取器在 Spring Boot 4 上确认。读取器与 Hibernate 无关。
未确认在 JDK 24 上的运行（未验证）。

---

[手册首页](README.md) | 上一章：12. Spring Data JPA（[Java](java/12-spring-data-jpa.md) | [Kotlin](kotlin/12-spring-data-jpa.md)）
