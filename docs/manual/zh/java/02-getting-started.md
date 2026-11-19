<a id="2-getting-started-java"></a>
# 2. 入门（Java）

> [!IMPORTANT]
> 本页比韩文手册旧。最新规则请参见[韩文页面](../../ko/java/02-getting-started.md)。

[Java 路线](README.md) | [手册首页](../README.md) | 上一章：[1. 概述](../01-overview.md) | 下一章：[3. 条件](03-where.md) | [本页的 Kotlin 版本](../kotlin/02-getting-started.md)

公共页面：[1. 概述](../01-overview.md) · [11. 错误与语言](../11-errors-and-languages.md)

---

<a id="requirements"></a>
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
./gradlew build
```

- Gradle wrapper（8.10）只能在 **JDK 23 或更低版本**上运行。请使用 JDK 21。
- 仓库中有 `gradle/gradle-daemon-jvm.properties`，因此 Gradle 守护进程会使用 Java 21。
- 在 IntelliJ IDEA 中，把 **Settings > Build Tools > Gradle > Gradle JVM** 设置为 Java 21 JDK。
- 构建包含三个模块：`query`（构件 `lambdaql-core`）、`spring-data-jpa`（`lambdaql-spring-data-jpa`）和 `spring-boot-starter`（`lambdaql-spring-boot-starter`）。Spring 模块的说明见 [12. Spring Data JPA](12-spring-data-jpa.md)。

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

<a id="use-the-local-build-in-your-project"></a>
### 在你的项目中使用本地构建

把三个模块的 jar 发布到本地 Maven 仓库：

```bash
./gradlew publishToMavenLocal
```

然后把 `lambdaql-core` 添加到你的项目中。

Gradle（Groovy DSL）：

```groovy
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation 'org.lambdaql:lambdaql-core:1.0.0'
}
```

Maven：

```xml
<dependency>
  <groupId>org.lambdaql</groupId>
  <artifactId>lambdaql-core</artifactId>
  <version>1.0.0</version>
</dependency>
```

使用 Spring Data JPA 或 Spring Boot 时，请改用 `lambdaql-spring-boot-starter` 或 `lambdaql-spring-data-jpa`。它们会带入 `lambdaql-core`（见 [12.2](12-spring-data-jpa.md#122-modules)）。

> [!WARNING]
> 版本 `1.0.0` 是仓库的 `gradle.properties` 中定义的值，在首次发布前可能改变。
> lambdaQL 发布到 Maven Central 后，请再次查看本页。

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
    private boolean active;              // getter: isActive()
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

<a id="create-a-querybuilder"></a>
## 创建 QueryBuilder

`QueryBuilder` 是入口。它需要一个 JPA `EntityManagerFactory` 或一个 `EntityManager`。

```java
QueryBuilder qb = new QueryBuilder(entityManagerFactory);
```

创建一个 `QueryBuilder` 并重复使用它。不要为每个查询都创建一个新的。

<a id="with-plain-jpa"></a>
### 使用纯 JPA

```java
EntityManagerFactory emf = Persistence.createEntityManagerFactory("my-unit");
QueryBuilder qb = new QueryBuilder(emf);
```

<a id="with-spring-boot"></a>
### 使用 Spring Boot

使用 `lambdaql-spring-boot-starter` 时，Spring Boot 会为你创建 `QueryBuilder` bean（见 [12.8](12-spring-data-jpa.md#128-the-querybuilder-bean)）。
不使用它时，请自己创建这个 bean。像自动配置那样，使用 Spring 的共享 `EntityManager`。

```java
@Configuration
public class LambdaQlConfig {

    @Bean
    public QueryBuilder queryBuilder(EntityManagerFactory entityManagerFactory) {
        return new QueryBuilder(SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory));
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

- 共享的 `EntityManager` 绑定到当前事务。在 `@Transactional` 内部，lambdaQL 在同一个事务中运行。
- 在 Spring 应用中不要使用接收 `EntityManagerFactory` 的构造函数。那样创建的 builder 会为每个查询打开自己的 `EntityManager`，位于 Spring 事务之外。
- 如果要在 Spring Data 仓库中编写 lambdaQL 查询，请参阅 [12. Spring Data JPA](12-spring-data-jpa.md)。

<a id="your-first-query"></a>
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
- `toList()` 返回所有行。其他结果函数（`first()`、`one()`、`count()`、`exists()`、`toSet()`）见[获取结果](#getting-the-result)。

为什么 `1` 和 `100.0` 被直接写入 HQL，而不是绑定参数？
它们是**写在 Lambda 中的常量**。lambdaQL 会把常量（数字、字符串、布尔值、`null`、enum 常量）作为字面量写入 HQL。
`price` 是 `Double`，所以 javac 把 `100` 编译为小数 `100.0`。
来自 Lambda 外部的值则不同：它们始终是绑定参数（见下一节和 [3.3](03-where.md#33-basic-form)）。

<a id="use-values-from-outside-the-lambda"></a>
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

<a id="many-where-calls"></a>
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

<a id="paths-to-other-entities"></a>
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
即使写了 null 守卫（`o.getCustomer() == null || ...`），没有客户的订单也不会保留，因为路径是内连接（决策 D48）。
要保留这些订单，请使用 `leftJoin`（[6.8](06-select-group.md#68-explicit-joins)）。
见 [3.10](03-where.md#310-association-paths)。

<a id="getting-the-result"></a>
## 获取结果

`toList()` 并不是执行查询的唯一方式。lambdaQL 有六个结果函数（决策 D38 和 D42）：

| 函数 | 返回 | 可以在哪里调用 | 作用 |
|---|---|---|---|
| `toList()` | `List<T>` | `where` 之后，以及 `orderBy` / `limit` / `offset` 之后 | 返回所有行（或一页）。 |
| `first()` | `Optional<T>` | `where` 之后，以及 `orderBy` / `limit` / `offset` 之后 | 返回第一行。只读取一行。 |
| `one()` | `Optional<T>` | 只能在 `where` 之后 | 返回唯一的一行。如果有 2 行或更多行匹配，抛出 `NonUniqueResultException`。最多读取 2 行。 |
| `count()` | `long` | 只能在 `where` 之后 | 返回匹配的行数。 |
| `exists()` | `boolean` | 只能在 `where` 之后 | 如果有行匹配，返回 `true`。只读取一行，不加载任何实体。 |
| `toSet()` | `SequencedSet<T>` | `where` 之后，以及 `orderBy` / `limit` / `offset` 之后 | 以保持行顺序的 Set 返回所有行（或一页）。如果 Set 比行数小，抛出 `IllegalStateException`。 |

"`where` 之后" 指的是通过 `qb.from(...)` 和 `where(...)` 得到的查询（类型为 `SelectQuery`）。本手册把它称为 **where 阶段**。也可以不写 `where`，直接在 `qb.from(...)` 之后调用这些函数。

在 `orderBy`、`limit` 或 `offset` 之后，查询的类型是 `OrderedSelectQuery`。它的结果函数只有 `toList()`、`toSet()` 和 `first()`。在那里 `one()`、`count()` 和 `exists()` 无法编译。
这是有意的设计（决策 D38）：`limit(10).count()` 可能表示"所有行"，也可能表示"最多 10 行"，因此 lambdaQL 不允许这种写法。

下面的示例复制自 [`ResultQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ResultQueryTest.java)。该测试在 H2、MariaDB 和 Oracle 上运行这些示例，并把结果与在 Java 中计算出的相同答案进行比较。
它使用 [3.3](03-where.md#33-basic-form) 中的测试实体：`price` 是 `double`，因此它的排序没有 `nulls` 部分；`Order` 还有一个枚举 `kind`。

<a id="first"></a>
### `first()`

没有 `orderBy` 时，"第一行"指的是 id 最小的那一行（规则 S9）。因此每次得到的都是同一行。
要选择顺序，请先调用 `orderBy(...)`。之后 `first()` 返回按该顺序排列的第一行。`offset` 会被保留：`orderBy(...).offset(1).first()` 返回第二行。
如果没有匹配的行，结果是 `Optional.empty()`。

**(已测试)** [`ResultQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ResultQueryTest.java)，region `first`：

```java
Optional<Order> cheapest = qb.from(Order.class)
        .orderBy((o, by) -> by.asc(o.getPrice()))
        .first();
// without orderBy, "first" is the row with the smallest id
Optional<Order> firstActive = qb.from(Order.class).where(o -> o.isActive()).first();
Optional<Order> none = qb.from(Order.class).where(o -> o.getStatus() == 9).first();
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `first`（`cheapest`）：

```sql
-- H2, Oracle
select …
from orders o1_0
order by o1_0.price,o1_0.id
fetch first ? rows only

-- MariaDB
select …
from orders o1_0
order by o1_0.price,o1_0.id
limit ?
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `first#2`（`firstActive`）：

```sql
-- H2
select …
from orders o1_0
where o1_0.active=true
order by o1_0.id
fetch first ? rows only

-- MariaDB
select …
from orders o1_0
where o1_0.active=1
order by o1_0.id
limit ?

-- Oracle
select …
from orders o1_0
where o1_0.active=1
order by o1_0.id
fetch first ? rows only
```

`first()` 只读取一行，因此它是一个分页查询。lambdaQL 会把 `id` 添加为最后一个排序键（见 [4.8](04-order-paging.md#tie-breaker-automatic-id)），Hibernate 会为每个数据库写出分页 SQL：H2 和 Oracle 上是 `fetch first ? rows only`，MariaDB 上是 `limit ?`。这里的 `?` 是 1。

<a id="one"></a>
### `one()`

当条件最多只能匹配一行时（例如唯一键），请使用 `one()`。
没有行时得到 `Optional.empty()`。两行或更多行时抛出 `jakarta.persistence.NonUniqueResultException`。如果返回任意一个匹配的行都可以，请使用 `first()`。

**(已测试)** [`ResultQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ResultQueryTest.java)，region `one`：

```java
long id = knownId;
Optional<Order> order = qb.from(Order.class).where(o -> o.getId() == id).one();
Optional<Order> missing = qb.from(Order.class).where(o -> o.getId() == -1L).one();
// two or more rows: an exception (use first() if any row is fine)
assertThrows(NonUniqueResultException.class, () -> qb.from(Order.class).where(o -> o.isActive()).one());
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `one`：

```sql
-- H2, Oracle
select …
from orders o1_0
where o1_0.id=?
fetch first ? rows only

-- MariaDB
select …
from orders o1_0
where o1_0.id=?
limit ?
```

`one()` 最多向数据库请求 2 行。这足以发现是否存在第二行。因此 SQL 中有 `fetch first ? rows only`（H2、Oracle）或 `limit ?`（MariaDB），这里的 `?` 是 2。没有 `order by`，因为这里顺序无关紧要。

<a id="count-and-a-paged-screen"></a>
### `count()` 与分页画面

`count()` 返回匹配的行数。它只存在于 where 阶段，因此 `limit` 和 `offset` 永远不会改变它。

对于分页画面，请把 where 阶段的查询保存在一个变量中。在它上面调用 `count()` 得到总数。然后在同一个变量上调用 `orderBy(...).limit(...).toList()` 得到这一页。

**(已测试)** [`ResultQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ResultQueryTest.java)，region `count`：

```java
SelectQuery<Order> active = qb.from(Order.class).where(o -> o.isActive());
long total = active.count();                                   // every matching row
List<Order> page = active.orderBy((o, by) -> by.desc(o.getPrice())).limit(2).toList();
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `count`：

```sql
-- H2
select count(o1_0.id)
from orders o1_0
where o1_0.active=true

-- MariaDB, Oracle
select count(o1_0.id)
from orders o1_0
where o1_0.active=1
```

HQL 是 `select count(e0) from Order e0 where e0.active = true`。Hibernate 把 `count(e0)` 变成 `select count(o1_0.id)`：它统计 id 列。计数查询没有分页。

<a id="exists"></a>
### `exists()`

如果至少有一行匹配，`exists()` 返回 `true`。它执行 `select 1`，最多读取一行。不会加载任何实体。

**(已测试)** [`ResultQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ResultQueryTest.java)，region `exists`：

```java
boolean anyRush = qb.from(Order.class).where(o -> o.getKind() == Order.Kind.RUSH).exists();
boolean anyStatus9 = qb.from(Order.class).where(o -> o.getStatus() == 9).exists();
```

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `exists`（`anyRush`）：

```sql
-- H2, Oracle
select 1
from orders o1_0
where o1_0.kind='RUSH'
fetch first ? rows only

-- MariaDB
select 1
from orders o1_0
where o1_0.kind='RUSH'
limit ?
```

HQL 是 `select 1 from Order e0 where e0.kind = org.lambdaql.query.Order$Kind.RUSH`。SQL 只 select 常量 `1`，因此不读取任何列，而分页（`?` 是 1）会在第一行之后停止。

<a id="toset"></a>
### `toSet()`

`toSet()` 像 `toList()` 一样执行查询，但返回 `SequencedSet<T>`（决策 D42）。
它是 `LinkedHashSet`，所以元素保持行的顺序。有 `orderBy` 时，就是排序的顺序。

**(已测试)** [`ResultQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ResultQueryTest.java)，region `to-set`：

```java
java.util.SequencedSet<Order> byPrice = qb.from(Order.class)
        .orderBy((o, by) -> by.desc(o.getPrice()))
        .toSet();
```

SQL 与 `toList()` 相同。lambdaQL **不会**添加 `distinct`：

- 每个实体行都有主键。因此即使只查询一张表，同一行也不会返回两次。
- lambdaQL 只 join to-one 关联。集合会变成 `exists` 子查询。因此 join 永远不会让行重复。
- 所以 `distinct` 不会改变结果，只会增加开销。（这与数据库的限制无关：Oracle 23.26 也接受 LOB 列上的 `distinct`。这是实测结果。）

对于值查询（`select`），`distinct()` 和 `toSet()` 使用 `select distinct`（见 [6.7](06-select-group.md#67-distinct-paging-and-results)）。

**大小检查。** 如果 Set 比行数小，`toSet()` 抛出 `IllegalStateException`。以下情况会发生这种问题：

- 实体的 `equals` / `hashCode` 把两个不同的行视为相等
- 它的 `@Id` 不唯一（例如映射到视图的实体）

如果没有这个检查，行会在没有任何迹象的情况下丢失。这些情况请使用 `toList()`。

在下面的测试中，`Tag.equals` 只比较名称，而表中有两行名称相同。

**(已测试)** [`ResultQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ResultQueryTest.java)，region `to-set-check`：

```java
// Tag.equals compares only the name: two different rows would become one element, so toSet() refuses
assertThrows(IllegalStateException.class, () -> qb.from(Tag.class).toSet());
assertEquals(2, qb.from(Tag.class).toList().size());
```

<a id="things-to-know"></a>
## 需要知道的事

| 规则 | 示例 |
|---|---|
| 使用 Lambda 或方法引用。匿名类会被拒绝。 | `where(o -> o.isActive())`、`where(Order::isActive)` |
| Lambda 必须是一个表达式（用于条件、排序和 select 时）。 | Lambda 内部的循环和局部变量会被拒绝。 |
| 不要在 Lambda 中放必须执行的代码。Lambda 永远不会被执行。 | 有副作用的调用，例如 `System.out.println(...)`，会被拒绝。 |
| 使用 javac 编译。 | 不保证支持 ECJ 的输出。 |
| 新的 JDK 版本需要匹配的 ASM 版本。 | lambdaQL 使用 ASM 9.11.1（可读取最高到 Java 28 的 class 文件）。如果你使用更新的 JDK 编译，请升级 ASM。 |

更多 Java 细节见 [9. Java 说明](09-java-notes.md)。

<a id="next"></a>
## 下一步

- [3. 条件（`where`）](03-where.md)：所有运算符和 NULL 规则。
- [4. 排序与分页](04-order-paging.md)

---

[Java 路线](README.md) | 上一章：[1. 概述](../01-overview.md) | 下一章：[3. 条件](03-where.md) | [本页的 Kotlin 版本](../kotlin/02-getting-started.md)
