# 2. 入门（Java）

> [!NOTE]
> **状态：** 目前你可以从源码构建 lambdaQL 并运行它的测试。
> `where` 条件和 `toList()` 可以端到端运行。它们已在 H2、MariaDB 和 Oracle 上测试。
> 排序与分页的核心部分（`orderBy`、`limit`、`offset`）也可以运行。
> 子查询、`select`、update/insert/delete、存储过程和 Spring 模块**已设计，尚未实现**。
> 目前还没有发布任何构件。下面的构件名称是临时的。

[Java 路线](README.md) | [手册首页](../README.md) | 上一章：[1. 概述](../01-overview.md) | 下一章：[3. 条件](03-where.md) | [本页的 Kotlin 版本](../kotlin/02-getting-started.md)

公共页面：[1. 概述](../01-overview.md) · [11. 错误与语言](../11-errors-and-languages.md)

---

## 环境要求

| 项目 | 版本 | 说明 |
|---|---|---|
| Java | 21 | 构建和使用 lambdaQL 都需要。 |
| JPA 提供者 | Hibernate ORM 6.6 | lambdaQL 生成 Hibernate 6 HQL。设计已用 6.6.5 验证。不支持其他 JPA 提供者。 |
| Jakarta Persistence | 3.1 | 随 Hibernate 6.6 一起提供。 |
| 数据库 | H2 2.3、MariaDB 10.11、Oracle 23.26 (23ai) | 这三种已验证。其他数据库可能可用，但未经测试。 |
| Spring Boot | 3.4 | 可选。项目的测试使用 Spring Boot 3.4。 |
| 编译器 | javac | 不保证支持由 ECJ（Eclipse 编译器）编译的 Lambda。见 [9. Java 说明](09-java-notes.md#912-compiler-and-build)。 |

<a id="build-from-source"></a>
## 从源码构建

```bash
git clone https://github.com/codebit0/lambdaql.git
cd lambdaql
./gradlew :query:build
```

- Gradle wrapper（8.10）只能在 **JDK 23 或更低版本**上运行。请使用 JDK 21。
- 仓库中有 `gradle/gradle-daemon-jvm.properties`，因此 Gradle 守护进程会使用 Java 21。
- 在 IntelliJ IDEA 中，把 **Settings > Build Tools > Gradle > Gradle JVM** 设置为 Java 21 JDK。

运行单元测试：

```bash
./gradlew :query:test
```

这些测试展示了 lambdaQL 目前能做什么。`SelectQueryTest` 使用用户 API，其他测试检查内部类：

| 测试文件 | 展示内容 |
|---|---|
| [`SelectQueryTest.java`](../../../../query/src/test/java/org/lambdaql/query/SelectQueryTest.java) | `qb.from(...).where(...)`：HQL、参数和返回的行。默认在 H2 上运行；加上 `-Dlql.db=mariadb` 或 `-Dlql.db=oracle` 可在其他数据库上运行 |
| [`LoadExamplesTest.java`](../../../../query/src/test/java/org/lambdaql/engine/load/LoadExamplesTest.java) | 加载 Lambda：捕获的值、捕获 `this` 的 Lambda、方法引用、不是 `Serializable` 的 Lambda |
| [`ConditionDecodeTest.java`](../../../../query/src/test/java/org/lambdaql/engine/decode/ConditionDecodeTest.java) | 解码条件：比较运算、捕获的值、`&&`、`\|\|`、`!` |
| [`IrExamplesTest.java`](../../../../query/src/test/java/org/lambdaql/engine/ir/IrExamplesTest.java) | 内部条件树 |
| [`HostEvaluatorTest.java`](../../../../query/src/test/java/org/lambdaql/engine/sem/HostEvaluatorTest.java) | 在 Java 中计算捕获的值、数值转换 |

例如，`ConditionDecodeTest` 检查 `o -> o.getStatus() == 1 || o.getStatus() == 2 || o.getStatus() == 3` 是否被解码为一个包含三个比较运算的 `OR`，并保持源代码中的顺序。

### 在你的项目中使用本地构建

把 jar 发布到本地 Maven 仓库：

```bash
./gradlew :query:publishToMavenLocal
```

然后把它添加到你的项目中。

Gradle（Groovy DSL）：

```groovy
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation 'org.lambdaql:lambadaql-query:1.0.0'
}
```

Maven：

```xml
<dependency>
  <groupId>org.lambdaql</groupId>
  <artifactId>lambadaql-query</artifactId>
  <version>1.0.0</version>
</dependency>
```

> [!WARNING]
> 这些坐标是**临时的**。
> 名称 `lambadaql` 有一个拼写错误，会在首次发布前修正。
> group、名称和版本都可能改变。lambdaQL 发布后请再次查看本页。

<a id="recommended-compiler-option--parameters"></a>
### 推荐的编译器选项：`-parameters`

使用 `-parameters` 编译。这样 lambdaQL 就能看到存储过程桩方法的参数名，并发现顺序颠倒的参数（见 [8.4](08-procedures-functions.md#84-warning-argument-order)）。

Gradle（Groovy DSL）：

```groovy
tasks.withType(JavaCompile).configureEach {
    options.compilerArgs << '-parameters'
}
```

Maven（`maven-compiler-plugin`）：

```xml
<configuration>
  <parameters>true</parameters>
</configuration>
```

<a id="example-entities"></a>
## 示例实体

Java 路线中的所有示例都使用这些实体。

```java
@Entity
@Table(name = "orders")
public class Order {
    @Id @GeneratedValue
    private Long id;
    private Double price;
    private Integer status;
    private boolean active;              // getter：isActive()
    private String description;
    @ManyToOne(fetch = FetchType.LAZY)
    private Customer customer;
    private LocalDateTime updateAt;
    @OneToMany(mappedBy = "order")
    private List<Item> items;

    // getter 和 setter
}

@Entity
public class Customer {
    @Id @GeneratedValue
    private Long id;
    private String name;
    private Integer grade;

    // getter 和 setter
}

@Entity
public class Item {
    @Id @GeneratedValue
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    private Order order;
    private Double price;
    private int qty;

    // getter 和 setter
}
```

实体规则：

- lambdaQL 读取 **getter**（`o.getPrice()`、`o.isActive()`）。它通过 JPA Metamodel 把它们映射到 JPA 属性。它不会直接读取字段。
- 你不需要生成的代码（不需要 Q 类，也不需要静态元模型）。
- 大多数字段是装箱类型（`Long`、`Double`、`Integer`）。这对 `==` 很重要。见 [9. Java 说明](09-java-notes.md#92-boxing-and-unboxing)。

## 创建 QueryBuilder

`QueryBuilder` 是入口。它需要一个 JPA `EntityManagerFactory`。

```java
QueryBuilder qb = new QueryBuilder(entityManagerFactory);
```

创建一个 `QueryBuilder` 并重复使用它。不要为每个查询都创建一个新的。

### 使用纯 JPA

```java
EntityManagerFactory emf = Persistence.createEntityManagerFactory("my-unit");
QueryBuilder qb = new QueryBuilder(emf);
```

### 使用 Spring Boot

Spring Boot 已经创建了 `EntityManagerFactory`。注入它并创建一个 `QueryBuilder` bean。

```java
@Configuration
public class LambdaQlConfig {

    @Bean
    public QueryBuilder queryBuilder(EntityManagerFactory entityManagerFactory) {
        return new QueryBuilder(entityManagerFactory);
    }
}
```

然后在需要的地方注入 `QueryBuilder`。

```java
@Service
public class OrderService {

    private final QueryBuilder qb;

    public OrderService(QueryBuilder qb) {
        this.qb = qb;
    }

    @Transactional(readOnly = true)
    public List<Order> activeOrders() {
        return qb.from(Order.class)
                 .where(o -> o.isActive())
                 .toList();
    }
}
```

- 在 `lambdaql-spring-data-jpa` 模块发布之前，你需要自己创建 bean。该模块将为你创建它。
- 在 Spring 事务中，lambdaQL 使用该事务共享的 `EntityManager`。
- 如果要在 Spring Data 仓库中编写 lambdaQL 查询，请参阅 [12. Spring Data JPA](12-spring-data-jpa.md)。

## 你的第一个查询

```java
List<Order> orders = qb.from(Order.class)
    .where(o -> o.getStatus() == 1 && o.getPrice() > 100)
    .toList();
// HQL: select e0 from Order e0 where e0.status = 1 and e0.price > 100.0
```

**关于 SQL 块。** `…` 是缩短的 select 列表（实体的所有列），`?` 是绑定参数。这些行来自 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)。该测试使用 Hibernate 6.6.5 在 H2 2.3、MariaDB 10.11 和 Oracle 23.26 上运行每个查询，如果 Hibernate 的 SQL 发生变化，测试就会失败。

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `to-hql`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>100.0
```

- `qb.from(Order.class)` 开始一个针对 `Order` 的查询。
- `where(...)` 接收一个条件 Lambda。
- `toList()` 执行查询并返回 `List<Order>`。
- `toList()` 是目前唯一已确定的结果方法。其他结果方法还在计划中。

为什么 `1` 和 `100.0` 被直接写入 HQL，而不是绑定参数？
它们是**写在 Lambda 中的常量**。lambdaQL 会把常量（数字、字符串、布尔值、`null`、enum 常量）作为字面量写入 HQL。
`price` 是 `Double`，所以 javac 把 `100` 编译为小数 `100.0`。
来自 Lambda 外部的值则不同：它们始终是绑定参数（见下一节和 [3.3](03-where.md#33-basic-form)）。

### 使用 Lambda 外部的值

Lambda 可以使用局部变量、字段和方法参数。这些是**捕获的值**。
lambdaQL 把它们转换为绑定参数。每次执行查询时，它都会读取新的值。

```java
public List<Order> expensive(int status, double minPrice) {
    return qb.from(Order.class)
             .where(o -> o.getStatus() == status && o.getPrice() >= minPrice)
             .toList();
    // HQL: select e0 from Order e0 where e0.status = :p1 and e0.price >= :p2
}
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `start-captured`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=? and o1_0.price>=?
```

### 多次调用 `where`

你可以多次调用 `where`。lambdaQL 用 `AND` 连接这些条件。

```java
qb.from(Order.class)
  .where(o -> o.isActive())
  .where(o -> o.getStatus() == 1 || o.getStatus() == 2)
  .toList();
// HQL: select e0 from Order e0
//      where e0.active = true and (e0.status = 1 or e0.status = 2)
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `start-many-where`：

```sql
-- H2
select …
from orders o1_0
where o1_0.active=true and (o1_0.status=1 or o1_0.status=2)

-- MariaDB, Oracle
select …
from orders o1_0
where o1_0.active=1 and (o1_0.status=1 or o1_0.status=2)
```

当你逐步添加条件时，这很有用：

```java
var query = qb.from(Order.class).where(o -> o.isActive());
if (minPrice != null) {
    query = query.where(o -> o.getPrice() >= minPrice);
}
List<Order> orders = query.toList();
```

构建器变量请使用 `var`。构建器类型的名称尚未最终确定。

### 指向其他实体的路径

像 `o.getCustomer().getName()` 这样的路径指代关联实体的一个列。
在 `where` 中，lambdaQL 默认会添加内连接 `join`。没有客户的订单**不在**结果中。

```java
qb.from(Order.class)
  .where(o -> o.getCustomer().getName().equals(name))
  .toList();
// HQL: select e0 from Order e0 join e0.customer j1 where j1.name = :p1
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `inner-join`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name=?
```

要查找没有客户的订单，请比较关联本身：`o.getCustomer() == null` 生成 `e0.customer is null`（不需要 join）。
如果 null 守卫保留了没有客户的订单（`o.getCustomer() == null || ...`），lambdaQL 会使用 `left join`。
见 [3.10](03-where.md#310-association-paths)。

## 需要知道的事

| 规则 | 示例 |
|---|---|
| 使用 Lambda 或方法引用。匿名类会被拒绝。 | `where(o -> o.isActive())`、`where(Order::isActive)` |
| Lambda 必须是一个表达式（用于条件、排序和 select 时）。 | Lambda 内部的循环和局部变量会被拒绝。 |
| 不要在 Lambda 中放必须执行的代码。Lambda 永远不会被执行。 | 有副作用的调用，例如 `System.out.println(...)`，会被拒绝。 |
| 使用 javac 编译。 | 不保证支持 ECJ 的输出。 |
| 新的 JDK 版本需要匹配的 ASM 版本。 | lambdaQL 使用 ASM 9.11.1（可读取最高到 Java 28 的 class 文件）。如果你使用更新的 JDK 编译，请升级 ASM。 |

更多 Java 细节见 [9. Java 说明](09-java-notes.md)。

## 下一步

- [3. 条件（`where`）](03-where.md)：所有运算符和 NULL 规则。
- [4. 排序与分页](04-order-paging.md)

---

[Java 路线](README.md) | 上一章：[1. 概述](../01-overview.md) | 下一章：[3. 条件](03-where.md) | [本页的 Kotlin 版本](../kotlin/02-getting-started.md)
