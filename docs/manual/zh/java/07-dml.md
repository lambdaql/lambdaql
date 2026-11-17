# 7. 更新、插入、删除（Java）

> [!NOTE]
> **状态**
> - **已实现并测试**，在 H2 2.3、MariaDB 10.11 和 Oracle 23.26 上（Hibernate 6.6.5，里程碑 M10，决定 D28 和 D40）：更新（setter、`by.set`、多次调用 `set()`、`@Version`）、删除、插入（一行、列表、自动生成的 id、`insert ... select`）、upsert、规则 U1、U3、U4 和 U6 的检查，以及（决定 D41）更新和删除的 `limit` 与单行保护 `executeOne()`。
> - **同样已实现并测试：** update 和 delete 的 `where` 中的子查询（`sub`）（里程碑 M8，见 [7.4](07-4-delete.md)），以及 `insert ... select` 中的 `groupBy`、`having` 和 `Agg`（里程碑 M9，见 [7.5](07-5-insert.md)）。
> - **尚未实现：** `set` 值中的子查询（上下文对象的 `by.from(...)`），以及复合键实体上的 `limit` / `executeOne()`。
> - 标记为 **(已测试)** 的示例是从测试代码中复制的。其他示例遵循设计。它们还没有测试覆盖，其 HQL 在小细节上可能不同（例如 `e0` 这样的别名）。
> - 完整状态见 [Java 路线 README](README.md#status)。

[Java 路线](README.md) | [手册首页](../README.md) | 上一章：[6. 查询列、聚合与分组](06-select-group.md) | 下一章：[8. 存储过程与数据库函数](08-procedures-functions.md) | [本页的 Kotlin 版本](../kotlin/07-dml.md)

公共页面：[1. 概述](../01-overview.md) · [11. 错误与语言](../11-errors-and-languages.md)

---

lambdaQL 有五个入口：

| 入口 | 用途 |
|---|---|
| `qb.from(X.class)` | 查询（第 3 章至第 6 章） |
| `qb.update(X.class)` | 更新行 |
| `qb.insert(X.class)` | 插入行 |
| `qb.delete(X.class)` | 删除行 |
| `qb.upsert(X.class)` | 插入一行；如果已有相同 `@Id` 的行，则修改该行 |

思路与查询相同：

- 每个部分写**一个 Lambda**。没有静态辅助方法。
- Lambda **从不执行**。lambdaQL 读取它的字节码。像 `o.setStatus(1)` 这样的 setter 调用会变成 `set e0.status = 1`。
- `where` 使用与 [3. 条件](03-where.md)相同的规则，它修改的行与使用同一个 Lambda 的查询返回的行相同。
- `execute()` 执行语句并返回**受影响的行数**（`int`）。
- `executeOne()` 执行**最多只能改变一行**的更新或删除（见[单行保护](#one-row-protection)）。
- `orderBy(...).limit(n)` 只改变前 `n` 行（见 [Limit](#limit-change-the-first-rows)）。
- `toHql()` 不执行语句，只给出 HQL 和参数。
- 语句需要事务（见[事务](#transactions)）。

<a id="contents"></a>
## 目录

- [7.1 示例实体](#71-example-entities)
- [7.2 更新](#72-update)
- [7.3 两种赋值方式：setter 或 `by.set`](#73-two-ways-to-assign-setter-or-byset)
- [7.4 删除](#74-delete)
- [7.5 插入](#75-insert)
- [7.6 Upsert](#76-upsert)
- [7.7 DML 的 Java 说明](#77-java-notes-for-dml)
- [7.8 重要规则](#78-important-rules)
- [7.9 全部规则 U1 至 U21（汇总）](#79-all-rules-u1-to-u21-summary)
- [7.10 v0.1 中不包含的内容](#710-not-in-v01)

---

<a id="71-example-entities"></a>
## 7.1 示例实体

本章使用 `Order`、`Customer` 和 `Item`（见 [2. 入门](02-getting-started.md#example-entities)）。
这里的 `Order` 还多了几个字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `id`、`price`、`status`、`active`、`description`、`updateAt`、`customer` | | 与其他章节相同 |
| `total` | `Double` | 明细价格之和 |
| `cnt` | `Integer` | 计数器 |
| `custName` | `String` | 客户名的副本 |
| `address` | `Address`（嵌入：`city`、`zip`） | 嵌入值 |

其他实体：`VOrder`（带有 `@Version` 字段 `version` 的订单）、`IdOrder`（id 自动生成的订单）、`Archive`（`id`、`price`、`customerName`）和 `Summary`（`customerId`、`total`、`cnt`）。

已测试的示例使用 [`query/src/test/java/org/lambdaql/query`](https://github.com/codebit0/lambdaql/tree/main/query/src/test/java/org/lambdaql/query) 中的测试实体。它们稍有不同：测试用的 `Order` 中，`price` 是 `double`，`status` 是 `short`，并且还有一个 `float` 字段 `tex`。`IdOrder` 的 id 使用 IDENTITY。

---

<a id="72-update"></a>
## 7.2 更新

→ [7.2 更新](07-2-update.md)

<a id="73-two-ways-to-assign-setter-or-byset"></a>
## 7.3 两种赋值方式：setter 或 `by.set`

| 方式 | 何时使用 | 示例 |
|---|---|---|
| Setter | 实体有 setter。编译器会检查值的类型。 | `set(o -> o.setStatus(1))` |
| `by.set(column, value)` | 实体**没有 setter**（例如 DDD 风格的实体）。 | `set((o, by) -> by.set(o.getStatus(), 1))` |

**(已测试)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)，region `update-by`：

```java
ExecutableUpdate<Order> u = qb.update(Order.class)
        .set((o, by) -> by.set(o.getDescription(), "done"))
        .where(o -> o.getStatus() == 2);
assertEquals("update Order e0 set e0.description = 'done' where e0.status = 2", u.toHql().hql());
```

**关于 SQL 块。** `…` 是缩短的 select 列表（实体的所有列），`?` 是绑定参数。这些行来自 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)。该测试使用 Hibernate 6.6.5 在 H2 2.3、MariaDB 10.11 和 Oracle 23.26 上运行每个查询，如果 Hibernate 的 SQL 发生变化，测试就会失败。

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `update-by`：

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

Oracle 在 `set` 中写出别名（`set o1_0.status=?`）；H2 和 MariaDB 不写。

本章的每个 setter 示例也都可以用 `by.set` 来写。把调用串起来：Lambda 返回最后一个 `by.set(...)`。

```java
qb.update(Order.class)
  .set((o, by) -> by.set(o.getStatus(), st).set(o.getPrice(), o.getPrice() * 1.1))
  .where(o -> o.getStatus() == 1)
  .execute();

qb.update(Order.class).set((o, by) -> by.set(o.getAddress().getCity(), city)).where(o -> o.getId() == id).execute(); // embedded
qb.update(Order.class).set((o, by) -> by.set(o.getCustomer().getId(), cid)).where(o -> o.getId() == id).execute();  // FK
```

在设计中，第二个 Lambda 参数是**一个上下文对象**。它既有 `set(column, value)`，也有子查询方法 `from(...)`。
你可以随意把它命名为 `by` 或 `sub`。名字无关紧要。
这个对象的子查询部分**尚未实现**。目前这个对象只有 `set`。（`where` 中的子查询可以使用：`where((o, sub) -> ...)`，见 [5. 子查询](05-subquery.md)。）

```java
qb.update(Order.class)
  .set((o, by) -> by.set(o.getTotal(),
        by.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).sum(i -> i.getPrice())))
  .all()
  .execute();
```

`by.set` 的规则（规则 U20）：

- 第一个参数必须是**从目标开始的列路径**：字段、嵌入路径或关联的 `@Id`。
  捕获值、计算式或源实体的路径都是错误。
- Lambda 必须是一条 `by.set(...)` 调用链。其他语句都是错误。
- 在 Java 中，`by.set(o.getStatus(), "abc")` 可以编译，因为类型是泛型的。
  设计中，会在分析时对照列类型检查值的类型。这项检查尚未实现。
- 不要在**同一个 Lambda 中**混用 setter 和 `by.set`。可以在不同的 `set()` 调用中混用。

---

<a id="74-delete"></a>
## 7.4 删除

→ [7.4 删除](07-4-delete.md)

<a id="75-insert"></a>
## 7.5 插入

→ [7.5 插入](07-5-insert.md)

<a id="76-upsert"></a>
## 7.6 Upsert

→ [7.6 Upsert](07-6-upsert.md)

<a id="77-java-notes-for-dml"></a>
## 7.7 DML 的 Java 说明

| Java 写法 | 含义 |
|---|---|
| 块 Lambda `o -> { o.setA(...); o.setB(...); }` | 可用于 `set`、`values`、`insert ... select` 和 `onConflict(...).update(...)`，但**只能包含 setter 调用**。不能有局部变量、`if` 或循环（规则 U1） |
| `Objects.requireNonNullElse(a, b)` | `coalesce(a, b)`（规则 U2） |
| `o.setCnt(o.getCnt() + 1)` | 支持。javac 会对值拆箱和装箱；lambdaQL 会忽略这些 |
| `o.setStatus((short) 1)` | javac 对 `short` 或 `byte` 字段要求的类型转换。常量仍是字面量 |
| `o.setActive(!o.isActive())`（取反切换） | **v0.1 中不支持**（这是条件值）。请使用 `where` 不同的两次更新 |
| `o.setDescription(o.getDescription() + "_x")` | **v0.1 中不支持**（字符串拼接） |
| 以 `LocalDateTime.now()` 作为值 | 在应用程序中计算后绑定（规则 U8） |
| `UpdateSet<Order> u = qb.update(...).set(...)`（或 `var`） | 逐步构建更新（见 [PATCH](07-2-update.md#many-set-calls-patch)） |

---

<a id="78-important-rules"></a>
## 7.8 重要规则

<a id="the-right-side-reads-the-old-value"></a>
### 右侧读取的是旧值

在 SQL 中，SET 右侧的每一列都表示更新**之前**的值。lambdaQL 遵循这条规则。

```java
.set(o -> { o.setPrice(o.getPrice() * 2); o.setTotal(o.getPrice()); })
// error: price is assigned first, then read again
```

如果你**在同一条语句中先给某列赋值、之后又读取它**，lambdaQL 会在你调用 `set(...)` 时抛出 `LambdaAnalysisException`（规则 U3）。
原因：这种情况下 MariaDB 使用**新**值，而 H2 和 Oracle 使用旧值。结果不同（已检查：MariaDB 为 200，H2 和 Oracle 为 100）。

以下情况没有问题，因为三种数据库的结果一致：

| 写法 | 可以吗？ |
|---|---|
| `o.setPrice(o.getPrice() * 1.1)`（读取自身） | 可以 |
| `o.setTotal(o.getPrice()); o.setPrice(0.0);`（先读取，后赋值） | 可以 |
| `o.setPrice(...); o.setTotal(o.getPrice());`（先赋值，后读取） | **错误** |

该检查针对物理列（嵌入的子列、外键列），也跨多个 `set()` 调用进行（在 [PATCH](07-2-update.md#many-set-calls-patch) 中测试）。

<a id="assign-a-column-only-once"></a>
### 每列只能赋值一次

对同一列赋值两次是错误（规则 U4）。这包括先 `setAddress(...)` 再 `getAddress().setCity(...)`，
或者先 `setCustomer(...)` 再 `getCustomer().setId(...)`。

<a id="rules-checked-at-analysis-time"></a>
### 在分析时检查的规则

lambdaQL 在读取 Lambda 时检查规则 U1、U3、U4 和 U6。下面的测试调用的是内部类 `AssignmentReader`，而不是用户 API。用户 API 会从 `set(...)` 和 `values(...)` 抛出同样的 `LambdaAnalysisException`。

**(已测试)** [`AssignmentReaderTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/sem/AssignmentReaderTest.java)，region `rules`：

```java
// U1: only setters; no condition, no local variable
assertThrows(LambdaAnalysisException.class, () -> setters(o -> { if (o.isActive()) o.setStatus((short) 1); }));
// U6: an association's other properties belong to another table
assertThrows(LambdaAnalysisException.class, () -> setters(o -> o.getCustomer().setName("x")));
// U3: a value reads a column written before
assertThrows(LambdaAnalysisException.class, () -> AssignmentReader.checkRules(
        AssignmentReader.readSetters(SerializedLambdaExtractor.extract((Assign<Order>) o -> {
            o.setPrice(1);
            o.setTex((float) o.getPrice());
        }), List.of(Order.class), -1, new JpaEntityModel(emf.getMetamodel())), "test"));
```

<a id="where-or-all-is-required"></a>
### 必须有 `where` 或 `all()`

`set()` 返回的类型**没有** `execute()`（`UpdateSet`）。只有 `where()` 和 `all()` 返回带有 `execute()` 的类型（`ExecutableUpdate`）。
所以忘记写 `where` 会导致编译错误（规则 U5）。`delete` 也是如此。

<a id="association-paths-same-rows-as-select"></a>
### 关联路径：与查询相同的行

lambdaQL 把 DML 中非外键的关联路径写成**关联标量子查询**，在 `where` 中和 SET 的右侧都是如此（规则 U9）。下面几行来自 [7.2](07-2-update.md#a-value-from-another-table) 中已测试的示例：

```java
.where(o -> o.getCustomer().getName().equals("Kim"))
// → where (select s1.name from Customer s1 where s1.id = e0.customer.id) = 'Kim' and e0.customer.id is not null

o.setCustName(o.getCustomer().getName())
// → set e0.custName = (select s1.name from Customer s1 where s1.id = e0.customer.id)
```

为什么？如果 lambdaQL 直接把路径交给 Hibernate，Hibernate 会为每种数据库生成不同的 join SQL
（H2 用 `merge`，MariaDB 用 `update ... join`，Oracle 用内联视图），而且当路径出现在 SET 中时，**没有客户的行会丢失**
（已检查：使用 `.all()` 时，10 行中只有 9 行被更新）。
lambdaQL 自己编写这些子查询。这样语句只读取自己的表，所以 MariaDB 对读取目标表的限制（错误 1093）不会生效。

查询对 `where` 中的路径使用内连接（决策 D36）。因此在 DML 中，lambdaQL 会为这样的路径加上 `e0.customer.id is not null`。
这样 `update` 和 `delete` 处理的**正好是** `qb.from(X.class).where(同一个 Lambda)` 返回的那些行（已测试：把删除的行与查询结果进行比较）。
像 `o.getCustomer() == null || ...` 这样的 null 守卫会在查询中保留没有客户的行，所以该路径不会加上 `is not null` 检查。

以 `@Id` 结尾的路径（`o.getCustomer().getId()`）是外键列。没有子查询，也没有 join。

<a id="batching-for-many-rows"></a>
### 多行的分批

`values(list, ...)` 写出一条带多个 VALUES 行的语句（规则 U13）。

| 要点 | 规则 |
|---|---|
| 拆分单位 | 值的数量（行数 x 列数），而不是行数 |
| 上限 | 每条语句最多 500 个值（固定） |
| 返回值 | 所有语句的总和 |
| 事务 | 所有语句在一个事务中执行 |
| 空列表 | 不执行 SQL，返回 0（已测试） |

Oracle 有 65,535 个绑定值的硬性上限（ORA-01745）。远在达到上限之前，解析就会变得非常慢
（设计阶段的测试中 21,845 行大约需要 6 分钟）。所以上限设得较小。

<a id="limit-change-the-first-rows"></a>
### Limit：改变前几行

在 `where(...)` 或 `all()` 之后写 `orderBy(...).limit(n)`，更新或删除最多改变 `n` 行：即 `qb.from(X.class).where(same).orderBy(same)` 的前 `n` 行。

```java
qb.delete(Order.class).where(o -> o.getStatus() == 1).orderBy((o, by) -> by.asc(o.getPrice())).limit(100).execute();
```

| 要点 | 规则 |
|---|---|
| 顺序 | `orderBy` 的键，然后是 id（规则 S9）。没有 `orderBy` 时按 id |
| 没有 `limit` 的 `orderBy` | 错误（`IllegalStateException`） |
| 渲染 | `e0.id in (select t.id from (select x0.id as id from X x0 ... order by ... limit n) t)` |
| 行 | 内层 select 遵循查询的规则，包括连接（D36），因此行与查询相同 |
| MariaDB | 派生表 `t` 避免错误 1093 和 1235 |

已测试的示例和各数据库的 SQL：[7.4](07-4-delete.md#delete-the-first-rows-limit)（删除）和 [7.2](07-2-update.md#update-the-first-rows-limit)（更新）。

<a id="one-row-protection"></a>
### 单行保护

用 `executeOne()` 代替 `execute()`，**最多只改变一行**：

1. lambdaQL 读取匹配行的 id，最多 2 个。
2. 有 2 个 id 时，什么都不改变，并抛出 `NonUniqueResultException`。
3. 有 1 个 id 时，用 `id = :p and (你的条件)` 改变该行。

它绝不会改变超过一行，也不依赖回滚。结果为 0 或 1。`executeOne()` 与 `orderBy` 或 `limit` 一起使用是错误（`IllegalStateException`）。
已测试的示例：[7.4](07-4-delete.md#delete-exactly-one-row-executeone)。在 Spring 仓库中：`deleteOne` 和 `updateOne`（见 [12.4](12-spring-data-jpa.md#one-row-protection-deleteone-and-updateone)）。

<a id="transactions"></a>
### 事务

批量语句需要事务。只有使用 `EntityManagerFactory` 构造函数时，lambdaQL 才会自己打开事务。

| 构建器 | 事务 |
|---|---|
| `new QueryBuilder(entityManagerFactory)` | `execute()` 和 `executeOne()` 打开自己的事务并提交。出错时回滚 |
| 使用 `EntityManager` 的构建器：`new QueryBuilder(entityManager)`、Spring 的 `QueryBuilder` bean、repository 执行器 | 使用调用方的事务（`@Transactional`，或该 `EntityManager` 中处于活动状态的事务）。没有事务时，语句在发送任何 SQL 之前以 `TransactionRequiredException` 失败（已测试）。在 Spring 的 repository 调用中，它会变为 `InvalidDataAccessApiUsageException`（见 [12.6](12-spring-data-jpa.md#126-transactions-and-exceptions)） |

<a id="the-persistence-context-is-not-updated"></a>
### 持久化上下文不会被更新

批量 DML 不经过 JPA 持久化上下文（规则 U16）：

- Hibernate 会在语句执行前刷新（flush）待处理的更改。
- 语句执行后，同一个 `EntityManager` 中已加载的实体保留的是**旧值**。如果还要使用它们，请调用 `entityManager.clear()`。
- JPA 的 `cascade` 和 `orphanRemoval` **不会**执行。请先删除子行，或在数据库中使用 `ON DELETE CASCADE`。

---

<a id="79-all-rules-u1-to-u21-summary"></a>
## 7.9 全部规则 U1 至 U21（汇总）

lambdaQL 读取 Lambda 时检查：U1、U3、U4、U6（已测试）。由编译器检查：U5。
U11、U12、U15 和 U20 的 lambdaQL 检查尚未实现。U17（MariaDB 中对同一张表的子查询的包装）尚未实现。

| # | 规则 |
|---|---|
| U1 | 赋值 Lambda **只包含**对目标、其嵌入路径或关联的 `@Id` 的 **setter 调用**。`if`、三元运算 `c ? a : b`、循环、局部变量和其他方法调用都是错误。没有任何赋值的 Lambda 是错误。 |
| U2 | 右侧遵循 `where` 的值规则（[3. 条件](03-where.md)）。捕获值是绑定参数，Lambda 中的常量是字面量（D37）。`Objects.requireNonNullElse(a, b)` 变成 `coalesce(a, b)`。 |
| U3 | 右侧读取的是**旧**值。在同一条语句中给某列赋值后再读取它是错误。 |
| U4 | 对同一物理列赋值两次是错误，跨 `set()` 调用也是如此。 |
| U5 | 没有 `where()` 或 `all()` 就没有 `execute()`。由编译器检查。 |
| U6 | 给不可为空的列（`@Id`、`nullable=false`、基本类型）赋 `null` 是错误。给集合赋值，或给关联实体的非 `@Id` 字段赋值，都是错误。 |
| U7 | 捕获值的每次使用都有自己的参数。（测试中，STRING 列和 ORDINAL 列共享一个枚举参数时，会悄无声息地存入错误的值。） |
| U8 | `LocalDateTime.now()` 在应用程序中求值并绑定。不支持数据库时间（`current_timestamp`）。 |
| U9 | DML 中非外键的关联路径会变成关联标量子查询，在 `where` 中和 SET 的右侧都是如此。在 `where` 中，查询会做内连接的路径（D36）还会加上 `customer.id is not null`。DML 处理的行与查询相同。 |
| U10 | 在 `insert ... select` 中，源路径会变成显式的 `left join`。 |
| U11 | `o.setCustomer(entity)` 绑定该实体的 `@Id`。如果 id 为 null，绑定会失败。 |
| U12 | IDENTITY 和 SEQUENCE 的 id 可以省略。TABLE 生成器只适用于 `values`，不适用于 `insert ... select`。没有生成器时，id 是必需的。其他不可为空的字段也是必需的。 |
| U13 | 多行会被拆分：每条语句最多 500 个值。结果会相加。所有语句在一个事务中执行。 |
| U14 | 一个 `set` Lambda 可以给多列赋值，`set()` 也可以调用多次。所有赋值按调用顺序合并成一个 SET 列表。 |
| U15 | `@Version` 实体使用 `update versioned`。不要自己给版本号赋值。检查 `execute() == 0` 是你的工作。 |
| U16 | 批量 DML 跳过持久化上下文。执行前刷新，执行后实体是过期的，没有级联。 |
| U17 | 如果 DELETE 的子查询读取同一个实体，lambdaQL 会把它包装在派生表中。（否则会出现 MariaDB 错误 1093。） |
| U18 | 继承：完全支持 SINGLE_TABLE。JOINED 和 TABLE_PER_CLASS 的更新/删除使用临时表。拒绝向它们的子类插入。 |
| U19 | 查询缓存的键包括入口、目标实体、set Lambda、where/all、versioned 以及拆分大小。 |
| U20 | `by.set(column, value)`：列必须是目标上的 getter 路径。类型在分析时检查。不要在同一个 Lambda 中混用 setter 和 `by.set`。 |
| U21 | 每个 Lambda 只分析一次并缓存。跨 `set()` 调用的检查只比较缓存的摘要（原型中约 0.1 到 0.2 µs）。 |

Upsert 的规则（决定 D40）见 [7.6 Upsert](07-6-upsert.md#rules)。

---

<a id="710-not-in-v01"></a>
## 7.10 v0.1 中不包含的内容

| 项目 | 计划 | 现在该怎么做 |
|---|---|---|
| 条件值（`c ? a : b`、作为值的 `!o.isActive()`） | v0.2（CASE WHEN） | – |
| 在 Lambda 内部用 `if` 选择赋值 | v0.2 | 多次调用 `set()`（U14） |
| 字符串拼接（`o.getDescription() + "_x"`） | v0.2 | – |
| `insert ... select` 中的显式 join | v0.2 | 使用多对一路径 |
| `returning` | 不支持 | – |
| 数据库时间（`current_timestamp`） | 不支持 | 绑定应用程序时间（U8） |

目前违反规则时会抛出带普通消息的 `LambdaAnalysisException`（目前消息是韩语）。计划中的 `[LQL-xxxx]` 代码和 `LambdaQLException` 基类见 [11. 错误与语言](../11-errors-and-languages.md)。
所有规则汇总在一处：[10. 规则与限制](10-rules-and-limits.md)。

---

[Java 路线](README.md) | 上一章：[6. 查询列、聚合与分组](06-select-group.md) | 下一章：[8. 存储过程与数据库函数](08-procedures-functions.md) | [本页的 Kotlin 版本](../kotlin/07-dml.md)
