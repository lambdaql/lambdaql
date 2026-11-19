<a id="7-update-insert-delete-java"></a>
# 7. 更新、插入、删除（Java）

> [!IMPORTANT]
> 本页比韩文手册旧。最新规则请参见[韩文页面](../../ko/java/07-dml.md)。

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

- 每个部分写**一个 Lambda**。唯一的静态辅助方法是供没有 setter 的实体使用的 `Dml.value`（[7.3](#73-two-ways-to-assign-setter-or-dmlvalue)）。
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
- [7.3 两种赋值方式：setter 或 `Dml.value`](#73-two-ways-to-assign-setter-or-dmlvalue)
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

<a id="73-two-ways-to-assign-setter-or-dmlvalue"></a>
## 7.3 两种赋值方式：setter 或 `Dml.value`

| 方式 | 何时使用 | 示例 |
|---|---|---|
| Setter | 实体有 setter。编译器会检查值的类型。 | `set(o -> o.setStatus(1))` |
| `Dml.value(column, value)` | 实体**没有 setter**（例如 DDD 风格的实体）。 | `set(o -> value(o.getStatus(), 1))` |

`value` 是静态函数 `org.lambdaql.Dml.value`（决策 D49）。用 `import static org.lambdaql.Dml.value;` 导入，在单参数 Lambda 中使用。

**(已测试)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)，region `update-value`：

```java
// entities without setters: Dml.value(column, value), the column written as a getter call
ExecutableUpdate<Order> u = qb.update(Order.class)
        .set(o -> value(o.getDescription(), "done"))
        .where(o -> o.getStatus() == 2);
assertEquals("update Order e0 set e0.description = 'done' where e0.status = 2", u.toHql().hql());
```

**关于 SQL 块。** `…` 是缩短的 select 列表（实体的所有列），`?` 是绑定参数。这些行来自 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)。该测试使用 Hibernate 6.6.5 在 H2 2.3、MariaDB 10.11 和 Oracle 23.26 上运行每个查询，如果 Hibernate 的 SQL 发生变化，测试就会失败。

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `update-value`：

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

本章的每个 setter 示例也都可以用 `value` 来写。一条语句写一个 `value` 调用。

```java
import static org.lambdaql.Dml.value;

qb.update(Order.class)
  .set(o -> { value(o.getStatus(), st); value(o.getPrice(), o.getPrice() * 1.1); })
  .where(o -> o.getStatus() == 1)
  .execute();

qb.update(Order.class).set(o -> value(o.getAddress().getCity(), city)).where(o -> o.getId() == id).execute(); // embedded
qb.update(Order.class).set(o -> value(o.getCustomer().getId(), cid)).where(o -> o.getId() == id).execute();  // FK
```

`value` 的规则（规则 U20）：

- 第一个参数必须是**从目标开始的 getter 调用**：字段、嵌入路径或关联的 `@Id`。
  捕获值、计算式或源实体的路径是分析时错误（`LQL-3039`）。
- 同一属性的 setter 规则原样适用。`value(o.getX(), v)` 与 `o.setX(v)` 相同，因此适用 U1 和 U6；关联只能赋值 `@Id`（`LQL-3050`）。
- setter 和 `value` **可以在同一个 Lambda 中混用**。
- 在 Java 中，`value(o.getStatus(), "abc")` 可以编译，因为类型是泛型的。
  lambdaQL 在分析时对照列类型检查值的类型，不匹配时报 `LQL-3065` 错误。
  数字与数字列匹配。但小数与整数列不匹配（小数部分会丢失）。类型未知的值（`Object`、泛型聚合的 `Number`）不做检查。
- 在自身带有 `value(...)` 方法的类（例如带有 `value` 组件的 record）中，静态 import 会被遮蔽。在那里请写 `Dml.value(...)`。
- `o.getTotal() = 1` 这样的写法在 Java 中是编译错误，不能使用。

**(已测试)** [`AssignmentReaderTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/sem/AssignmentReaderTest.java)，region `value-type`：

```java
// value(V, V) compiles with any two types, so lambdaQL checks the value type against the column (U20)
assertEquals("[col0.status := 2, col0.price := 1]", setters(o -> { value(o.getStatus(), 2); value(o.getPrice(), 1); }));
String text = "abc";
LambdaQLException wrong = assertThrows(LambdaQLException.class, () -> setters(o -> value(o.getStatus(), text)));
assertEquals("LQL-3065", wrong.code(), wrong.getMessage());
// a decimal into a whole number column would lose the fraction
LambdaQLException fraction = assertThrows(LambdaQLException.class, () -> setters(o -> value(o.getStatus(), 1.5)));
assertEquals("LQL-3065", fraction.code(), fraction.getMessage());
```

**(已测试)** [`AssignmentReaderTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/sem/AssignmentReaderTest.java)，region `read-value`：

```java
// Dml.value(column, value) for entities without setters; constants stay literals, also when they are boxed
// for the generic value(V, V) (D37, D49)
assertEquals("[col0.status := 2, col0.price := 1.0]",
        setters(o -> { value(o.getStatus(), (short) 2); value(o.getPrice(), 1.0); }));
// setters and value can be mixed in one lambda
assertEquals("[col0.status := 2, col0.customer.id := 1]",
        setters(o -> { o.setStatus((short) 2); value(o.getCustomer().getId(), 1); }));
```

<a id="subquery-in-a-set-value"></a>
### `set` 值中的子查询

赋值 Lambda 的第二个参数是子查询的起点 `sub`，写法与 `where((o, sub) -> ...)` 相同（决策 D49）。
在 `insert ... select` 中，第三个参数是 `sub`（见 [7.5](07-5-insert.md)）。

**(已测试)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)，region `update-sub`：

```java
// the second parameter starts a subquery, as in where((o, sub) -> ...) (D49)
ExecutableUpdate<Order> u = qb.update(Order.class)
        .set((o, sub) -> o.setPrice(sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).sum(i -> i.getPrice())))
        .where((o, sub) -> sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).exists());
assertEquals("update Order e0 set e0.price = (select sum(s1.price) from Item s1 where s1.order.id = e0.id)"
        + " where exists (select 1 from Item s1 where s1.order.id = e0.id)", u.toHql().hql());
// sum gives NULL when there is no row; coalesce keeps a NOT NULL column valid for every row
ExecutableUpdate<Order> all = qb.update(Order.class)
        .set((o, sub) -> value(o.getPrice(), Objects.requireNonNullElse(
                sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).sum(i -> i.getPrice()), 0.0)))
        .all();
assertEquals("update Order e0 set e0.price = coalesce((select sum(s1.price) from Item s1 where s1.order.id = e0.id), 0.0)",
        all.toHql().hql());
```

- 其他表的聚合和同一张表的聚合都可以作为 `set` 的值（在 H2、MariaDB 10.11、Oracle 23.26 上实测）。在 `update` 中，MariaDB 也不会报错误 1093。
- 没有匹配行时，`sum` 等返回 NULL。如果是 NOT NULL 列，会出现数据库错误，请用 `Objects.requireNonNullElse(…, 0.0)` 包裹。它会变成 `coalesce(...)`。
- insert 的 `values((a, sub) -> ...)` 和 upsert（`onConflict(...).update((a, sub) -> ...)`）也以同样方式使用子查询（见 [7.5](07-5-insert.md#a-subquery-in-a-value)）。列表形式像 `values(list, (a, l, sub) -> ...)` 这样，第三个参数是 `sub`。

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

如果你**在同一条语句中先给某列赋值、之后又读取它**，lambdaQL 会在你调用 `set(...)` 时抛出 `TranslationException`（`LQL-3042`）（规则 U3）。
原因：这种情况下 MariaDB 使用**新**值，而 H2 和 Oracle 使用旧值。结果不同（实测：MariaDB 为 200，H2 和 Oracle 为 100）。

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

lambdaQL 在读取 Lambda 时检查规则 U1、U3、U4 和 U6。下面的测试调用的是内部类 `AssignmentReader`，而不是用户 API。用户 API 会从 `set(...)` 和 `values(...)` 抛出同样的 `LambdaQLException`（例如 U1 为 `LQL-3037`，U3 为 `LQL-3042`，U4 为 `LQL-3041`）。

**(已测试)** [`AssignmentReaderTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/sem/AssignmentReaderTest.java)，region `rules`：

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
（实测：使用 `.all()` 时，10 行中只有 9 行被更新）。
lambdaQL 自己编写这些子查询。这样语句只读取自己的表，所以 MariaDB 对读取目标表的限制（错误 1093）不会生效。

查询对 `where` 中的路径使用内连接（决策 D36）。因此在 DML 中，lambdaQL 会为这样的路径加上 `e0.customer.id is not null`。
这样 `update` 和 `delete` 处理的**正好是** `qb.from(X.class).where(同一个 Lambda)` 返回的那些行（已测试：把删除的行与查询结果进行比较）。
有 `o.getCustomer() == null || ...` 这样的 null 守卫时也一样。查询对该路径同样使用内连接（决策 D48），所以检查保留。

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

<a id="sqlrestriction-and-softdelete"></a>
### `@SQLRestriction` 和 `@SoftDelete`

Hibernate 会把实体的 `@SQLRestriction` 条件和 `@SoftDelete` 条件附加到 lambdaQL 生成的每条语句上（在 MariaDB 10.11、Oracle 23.26 上实测，H2 未测）。
批量 `update` 和 `delete` 也是如此。例如：`where m1_0.order_id=? and (m1_0.hidden = 0)`。

`@SoftDelete` 实体的 `delete` 会变成 `update … set deleted = 1 where … and deleted = 0`，行不会被真正删除。
lambdaQL 不单独处理这些条件。

---

<a id="79-all-rules-u1-to-u21-summary"></a>
## 7.9 全部规则 U1 至 U21（汇总）

lambdaQL 读取 Lambda 时检查：U1、U3、U4、U6（已测试）。由编译器检查：U5。
U11、U12、U15 的 lambdaQL 检查尚未实现。

| # | 规则 |
|---|---|
| U1 | 赋值 Lambda **只包含**对目标、其嵌入路径或关联的 `@Id` 的 **setter 调用和 `Dml.value(column, value)`**（`LQL-3037`）。`if`、三元运算 `c ? a : b`、循环、局部变量和其他方法调用都是错误。没有任何赋值的 Lambda 是错误。 |
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
| U14 | 一个 `set` Lambda 可以给多列赋值，`set()` 也可以调用多次。所有赋值按调用顺序合并成一个 SET 列表。setter 和 `Dml.value` 在同一个 Lambda 中也可以混用（D49）。 |
| U15 | `@Version` 实体使用 `update versioned`。不要自己给版本号赋值。检查 `execute() == 0` 是你的工作。 |
| U16 | 批量 DML 跳过持久化上下文。执行前刷新，执行后实体是过期的，没有级联。 |
| U17 | 如果 DELETE 的子查询读取目标实体（或同一继承层次），lambdaQL 会先通过派生表读取目标 id：`d0.id in (select t.id from (select e0.id as id from … where <condition>) t)`。否则会出现 MariaDB 错误 1093。这也适用于关联子查询；`update` 和复合 id 实体不包装（见 [7.4](07-4-delete.md#delete-with-a-subquery-on-the-same-table)）。 |
| U18 | 继承：完全支持 SINGLE_TABLE。JOINED 和 TABLE_PER_CLASS 的更新/删除使用临时表。拒绝向它们的子类插入。 |
| U19 | 查询缓存的键包括入口、目标实体、set Lambda、where/all、versioned 以及拆分大小。 |
| U20 | `Dml.value(column, value)`（D49）：第一个参数必须是从目标（或 `insert ... select` 的目标 `a`）开始的 getter 调用（`LQL-3039`）。同一属性的 setter 规则（U1、U6，关联只能赋值 `@Id`）原样适用。值的类型对照列类型检查（`LQL-3065`）。可以与 setter 混用。 |
| U21 | 每个 Lambda 只分析一次并缓存。跨 `set()` 调用的检查只比较缓存的摘要（原型中约 0.1 到 0.2 µs）。 |

Upsert 的规则（决策 D40）见 [7.6 Upsert](07-6-upsert.md#rules)。

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

违反规则时会抛出带 `[LQL-xxxx]` 错误码的 `LambdaQLException`。大多数规则为 `TranslationException`，赋值 Lambda 中的条件或局部变量为 `UnsupportedLambdaShapeException`。见 [11. 错误与语言](../11-errors-and-languages.md)。
所有规则汇总在一处：[10. 规则与限制](10-rules-and-limits.md)。

---

[Java 路线](README.md) | 上一章：[6. 查询列、聚合与分组](06-select-group.md) | 下一章：[8. 存储过程与数据库函数](08-procedures-functions.md) | [本页的 Kotlin 版本](../kotlin/07-dml.md)
