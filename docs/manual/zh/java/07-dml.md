<a id="7-update-insert-delete-java"></a>
# 7. 更新、插入、删除（Java）

[Java 路线](README.md) | [手册首页](../README.md) | 上一章：[6. 查询列、聚合与分组](06-select-group.md) | 下一章：[8. 存储过程与数据库函数](08-procedures-functions.md) | [本页的 Kotlin 版本](../kotlin/07-dml.md)

公共页面：[1. 概述](../01-overview.md) · [11. 错误与语言](../11-errors-and-languages.md)

---

lambdaQL 有五个入口。

| 入口 | 用途 |
|---|---|
| `qb.from(X.class)` | 查询（第 3 章至第 6 章） |
| `qb.update(X.class)` | 更新行 |
| `qb.insert(X.class)` | 插入行 |
| `qb.delete(X.class)` | 删除行 |
| `qb.upsert(X.class)` | 插入行。如果已有相同 `@Id` 的行，则更新该行 |

基本思路与查询相同。

- 每个部分写成**一个 Lambda 表达式**。没有 setter 的实体用 `Dml.value` 赋值。`orderBy` 与查询一样使用 `By` 的排序函数（[7.3](#73-two-ways-to-assign-setter-or-dmlvalue)）。
- lambdaQL **不调用**赋值 Lambda 表达式和条件 Lambda 表达式，而是分析其字节码 opcode。像 `o.setStatus(1)` 这样的 setter 调用会变成 `set e0.status = 1`。
- `where` 使用与 [3. 条件（`where`）](03-where.md)相同的规则。目标行与用同一个 Lambda 表达式查询到的行相同。
- `execute()` 执行语句并返回**受影响的行数**（`int`）。
- `executeOne()` 执行**最多只能改变一行**的 update 或 delete（见[单行保护](#one-row-protection)）。
- `orderBy(...).limit(n)` 只改变前 `n` 行（见 [limit](#limit-change-the-first-rows)）。
- `toHql()` 不执行语句，只返回 HQL 和参数。
- 执行语句需要事务（见[事务](#transactions)）。

<a id="contents"></a>
## 目录

- [7.1 示例实体](#71-example-entities)
- [7.2 更新（update）](#72-update)
- [7.3 两种赋值方式：setter 或 `Dml.value`](#73-two-ways-to-assign-setter-or-dmlvalue)
- [7.4 删除（delete）](#74-delete)
- [7.5 插入（insert）](#75-insert)
- [7.6 Upsert](#76-upsert)
- [7.7 DML 的 Java 说明](#77-java-notes-for-dml)
- [7.8 重要规则](#78-important-rules)
- [7.9 全部规则 U1～U21（汇总）](#79-all-rules-u1-to-u21-summary)
- [7.10 不支持的内容](#710-not-in-v01)

---

<a id="71-example-entities"></a>
## 7.1 示例实体

本章使用 `Order`、`Customer`、`Item`（见 [2. 入门](02-getting-started.md#example-entities)）。
这里的 `Order` 多了几个字段。

| 字段 | 类型 | 说明 |
|---|---|---|
| `id`、`price`、`status`、`active`、`description`、`updateAt`、`customer` | | 与其他章节相同 |
| `total` | `Double` | 明细价格之和 |
| `cnt` | `Integer` | 计数器 |
| `custName` | `String` | 客户名的副本 |
| `address` | `Address`（嵌入：`city`、`zip`） | 嵌入值 |

其他实体：`VOrder`（带有 `@Version` 字段 `version` 的订单）、`IdOrder`（id 自动生成的订单）、`Archive`（`id`、`price`、`customerName`）、`Summary`（`customerId`、`total`、`cnt`）。

示例代码中的实体（[`query/src/test/java/org/lambdaql/query`](https://github.com/lambdaql/lambdaql/tree/main/query/src/test/java/org/lambdaql/query)）与上表稍有不同。
`Order` 中 `price` 是 `double`，`status` 是 `short`，并且有一个 `float` 字段 `tex`。`IdOrder` 的 id 使用 IDENTITY 方式。

---

<a id="72-update"></a>
## 7.2 更新（update）

→ [7.2 更新（update）](07-2-update.md)

<a id="73-two-ways-to-assign-setter-or-dmlvalue"></a>
## 7.3 两种赋值方式：setter 或 `Dml.value`

| 方式 | 使用场景 | 示例 |
|---|---|---|
| setter | 实体有 setter 时。编译器检查值的类型。 | `set(o -> o.setStatus(1))` |
| `Dml.value(column, value)` | 实体**没有 setter** 时（例如 DDD 风格的实体）。 | `set(o -> value(o.getStatus(), 1))` |

`value` 是静态函数 `org.lambdaql.Dml.value`。用 `import static org.lambdaql.Dml.value;` 导入，在单参数 Lambda 表达式中使用。

<!-- region: DmlQueryTest.java#update-value -->
```java
// entities without setters: Dml.value(column, value), the column written as a getter call
ExecutableUpdate<Order> u = qb.update(Order.class)
        .set(o -> value(o.getDescription(), "done"))
        .where(o -> o.getStatus() == 2);
assertEquals("update Order e0 set e0.description = 'done' where e0.status = 2", u.toHql().hql());
```

**各数据库的 SQL**

<!-- sql: update-value -->
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

Oracle 在 `set` 中使用别名（`set o1_0.status=?`）。H2 和 MariaDB 不使用。

本章的所有 setter 示例也都可以用 `value` 来写。一条语句写一个 `value` 调用。

<!-- region: ManualJavaDmlTest.java#value-forms -->
```java
ExecutableUpdate<Order> u = qb.update(Order.class)
        .set(o -> { value(o.getStatus(), st); value(o.getPrice(), o.getPrice() * 1.1); })
        .where(o -> o.getStatus() == 1);
assertEquals("update Order e0 set e0.status = :p1, e0.price = (e0.price * 1.1) where e0.status = 1", u.toHql().hql());
// an embedded column and a foreign key
ExecutableUpdate<Order> embedded = qb.update(Order.class)
        .set(o -> value(o.getAddress().getCity(), city)).where(o -> o.getId() == id);
assertEquals("update Order e0 set e0.address.city = :p1 where e0.id = :p2", embedded.toHql().hql());
ExecutableUpdate<Order> fk = qb.update(Order.class)
        .set(o -> value(o.getCustomer().getId(), cid)).where(o -> o.getId() == id);
assertEquals("update Order e0 set e0.customer.id = :p1 where e0.id = :p2", fk.toHql().hql());
```

`value` 的规则：

- 第一个参数必须是**从目标开始的 getter 调用**：字段、嵌入路径、关联的 `@Id`。
  捕获值、计算式、源实体的路径是分析时错误（`LQL-3039`）。
- 同一属性的 setter 规则原样适用。`value(o.getX(), v)` 与 `o.setX(v)` 相同，因此适用 U1 和 U6；关联只能赋值 `@Id`（`LQL-3050`）。
- setter 和 `value` **可以在同一个 Lambda 表达式中混用**。
- 在 Java 中，`value(o.getStatus(), "abc")` 可以编译，因为类型是泛型的。
  lambdaQL 在分析时对照列类型与值类型，不匹配时报 `LQL-3065` 错误。
  数字与数字列匹配。但小数与整数列不匹配（小数部分会丢失）。类型未知的值（`Object`、泛型聚合的 `Number`）不做对照。
- 在自身带有 `value(...)` 方法的类（例如带有 `value` 组件的 record）中，静态 import 会被遮蔽。在那里请写 `Dml.value(...)`。

<!-- region: AssignmentReaderTest.java#value-type -->
```java
// value(V, V) compiles with any two types, so lambdaQL checks the value type against the column
assertEquals("[col0.status := 2, col0.price := 1]", setters(o -> { value(o.getStatus(), 2); value(o.getPrice(), 1); }));
String text = "abc";
LambdaQLException wrong = assertThrows(LambdaQLException.class, () -> setters(o -> value(o.getStatus(), text)));
assertEquals("LQL-3065", wrong.code(), wrong.getMessage());
// a decimal into a whole number column would lose the fraction
LambdaQLException fraction = assertThrows(LambdaQLException.class, () -> setters(o -> value(o.getStatus(), 1.5)));
assertEquals("LQL-3065", fraction.code(), fraction.getMessage());
```

<!-- region: AssignmentReaderTest.java#read-value -->
```java
// Dml.value(column, value) for entities without setters; constants stay literals, also when they are boxed
// for the generic value(V, V)
assertEquals("[col0.status := 2, col0.price := 1.0]",
        setters(o -> { value(o.getStatus(), (short) 2); value(o.getPrice(), 1.0); }));
// setters and value can be mixed in one lambda
assertEquals("[col0.status := 2, col0.customer.id := 1]",
        setters(o -> { o.setStatus((short) 2); value(o.getCustomer().getId(), 1); }));
```

<a id="subquery-in-a-set-value"></a>
### `set` 值中的子查询

赋值 Lambda 表达式的第二个参数是子查询的起点 `sub`，写法与 `where((o, sub) -> ...)` 相同。
在 `insert ... select` 中，第三个参数是 `sub`（见 [7.5](07-5-insert.md)）。

<!-- region: DmlQueryTest.java#update-sub -->
```java
// the second parameter starts a subquery, as in where((o, sub) -> ...)
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
- 没有匹配行时，`sum` 等返回 NULL。如果是 NOT NULL 列会出现数据库错误，因此请用 `Objects.requireNonNullElse(…, 0.0)` 包裹。它会变成 `coalesce(...)`。
- insert 的 `values((a, sub) -> ...)` 和 upsert（`onConflict(...).update((a, sub) -> ...)`）也以同样方式使用子查询（见 [7.5](07-5-insert.md#a-subquery-in-a-value)）。列表形式像 `values(list, (a, l, sub) -> ...)` 这样，第三个参数是 `sub`。

示例代码：[`DmlQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)（`update-value`、`update-sub`）、[`ManualJavaDmlTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaDmlTest.java)（`value-forms`）、[`AssignmentReaderTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/sem/AssignmentReaderTest.java)（`value-type`、`read-value`）。

---

<a id="74-delete"></a>
## 7.4 删除（delete）

→ [7.4 删除（delete）](07-4-delete.md)

<a id="75-insert"></a>
## 7.5 插入（insert）

→ [7.5 插入（insert）](07-5-insert.md)

<a id="76-upsert"></a>
## 7.6 Upsert

→ [7.6 Upsert](07-6-upsert.md)

<a id="77-java-notes-for-dml"></a>
## 7.7 DML 的 Java 说明

| Java 写法 | 含义 |
|---|---|
| 块 Lambda 表达式 `o -> { o.setA(...); o.setB(...); }` | 可用于 `set`、`values`、`insert ... select`、`onConflict(...).update(...)`，但**只能包含 setter 调用**。不能有局部变量、`if`、循环 |
| `Objects.requireNonNullElse(a, b)` | `coalesce(a, b)` |
| `o.setCnt(o.getCnt() + 1)` | 支持。javac 会对值拆箱和装箱，但 lambdaQL 忽略这些 |
| `o.setStatus((short) 1)` | javac 对 `short` 或 `byte` 字段要求的类型转换。常量保持为字面量 |
| `o.setActive(!o.isActive())`（切换） | **不支持**（条件值）。使用 `where` 不同的两次 update |
| `o.setDescription(o.getDescription() + "_x")` | **不支持**（字符串拼接） |
| 作为值的 `LocalDateTime.now()` | 在应用程序中计算后绑定 |
| `UpdateSet<Order> u = qb.update(...).set(...)`（或 `var`） | 分步构建 update（见 [PATCH](07-2-update.md#many-set-calls-patch)） |

---

<a id="78-important-rules"></a>
## 7.8 重要规则

<a id="the-right-side-reads-the-old-value"></a>
### 右侧值的基准：旧值

在 SQL 中，SET 右侧的所有列都表示更新**之前**的值。lambdaQL 遵循这条规则。

<!-- region: ManualJavaDmlTest.java#old-value -->
```java
// price is assigned first and read again: rejected (MariaDB would read the new value)
LambdaQLException e = assertThrows(LambdaQLException.class,
        () -> qb.update(Order.class).set(o -> { o.setPrice(o.getPrice() * 2); o.setTotal(o.getPrice()); }));
assertEquals("LQL-3042", e.code());
// read first, assign later: allowed, total gets the old price
ExecutableUpdate<Order> u = qb.update(Order.class)
        .set(o -> { o.setTotal(o.getPrice()); o.setPrice(0.0); })
        .where(o -> o.getStatus() == 3);
assertEquals("update Order e0 set e0.total = e0.price, e0.price = 0.0 where e0.status = 3", u.toHql().hql());
```

**在同一条语句中给某列赋值后又读取该列**时，lambdaQL 在调用 `set(...)` 时抛出 `TranslationException`（`LQL-3042`）。
原因：这种情况下 MariaDB 使用**新**值，H2 和 Oracle 使用旧值，结果不同（实测：MariaDB 为 200，H2 和 Oracle 为 100）。

以下情况三种数据库的结果相同，因此允许。

| 写法 | 是否允许 |
|---|---|
| `o.setPrice(o.getPrice() * 1.1)`（读取自身） | 允许 |
| `o.setTotal(o.getPrice()); o.setPrice(0.0);`（先读取后赋值） | 允许 |
| `o.setPrice(...); o.setTotal(o.getPrice());`（先赋值后读取） | **错误** |

该检查以物理列（嵌入的子列、FK 列）为单位进行，也适用于多次 `set()` 调用之间（见 [PATCH](07-2-update.md#many-set-calls-patch)）。

<a id="assign-a-column-only-once"></a>
### 每列只赋值一次

对同一列赋值两次是错误。`setAddress(...)` 之后的 `getAddress().setCity(...)`，
以及 `setCustomer(...)` 之后的 `getCustomer().setId(...)` 也包括在内。

<a id="rules-checked-at-analysis-time"></a>
### 分析时的规则检查

lambdaQL 在读取 Lambda 表达式时检查赋值规则。下面的示例调用的是内部类 `AssignmentReader`，而不是用户 API。在用户 API 中，`set(...)` 和 `values(...)` 抛出同样的 `LambdaQLException`（例如 `LQL-3037`、`LQL-3042`、`LQL-3041`）。

<!-- region: AssignmentReaderTest.java#rules -->
```java
// only setters; no condition, no local variable
assertThrows(LambdaQLException.class, () -> setters(o -> { if (o.isActive()) o.setStatus((short) 1); }));
// an association's other properties belong to another table
assertThrows(LambdaQLException.class, () -> setters(o -> o.getCustomer().setName("x")));
// a value reads a column written before
assertThrows(LambdaQLException.class, () -> AssignmentReader.checkRules(
        AssignmentReader.readSetters(SerializedLambdaExtractor.extract((Assign<Order>) o -> {
            o.setPrice(1);
            o.setTex((float) o.getPrice());
        }), List.of(Order.class), -1, new JpaEntityModel(emf.getMetamodel())), "test"));
```

<a id="where-or-all-is-required"></a>
### 必须指定 `where` 或 `all()`

执行 update 和 delete 时，必须指定 `where(...)` 或 `all()`。

<a id="association-paths-same-rows-as-select"></a>
### 关联路径：与查询相同的行

lambdaQL 在 DML 中把非 FK 的关联路径写成**关联标量子查询**，`where` 和 SET 右侧都是如此。下面的示例展示 `where` 和 SET 右侧的路径。

<!-- region: DmlQueryTest.java#update-path -->
```java
// a path in the condition: a subquery, plus "customer exists" (the same rows as the select, U9)
ExecutableUpdate<Order> u = qb.update(Order.class)
        .set(o -> o.setCustName("K"))
        .where(o -> o.getCustomer().getName().equals("Kim"));
assertEquals("update Order e0 set e0.custName = 'K' where (select s1.name from Customer s1 where s1.id = fk(e0.customer)) = 'Kim'"
        + " and e0.customer.id is not null", u.toHql().hql());
// a path in a value: a subquery; NULL when there is no customer
ExecutableUpdate<Order> copy = qb.update(Order.class).set(o -> o.setCustName(o.getCustomer().getName())).all();
assertEquals("update Order e0 set e0.custName = (select s1.name from Customer s1 where s1.id = fk(e0.customer))", copy.toHql().hql());
```

这样做的原因如下。如果 lambdaQL 把路径原样交给 Hibernate，Hibernate 会为每种数据库生成不同的 join SQL
（H2 用 `merge`，MariaDB 用 `update ... join`，Oracle 用内联视图）。而且 SET 中有路径时，**没有客户的行会被漏掉**
（实测：使用 `.all()` 时 10 行中只更新了 9 行）。
lambdaQL 直接编写子查询。这样语句只读取自己的表，因此不会受到 MariaDB 对读取目标表的限制（错误 1093）。

对于在查询中成为 inner join 的路径，lambdaQL 在 DML 中加上 `e0.customer.id is not null`。
对于在查询中成为 `left join` 的路径（例如 null 守卫 `o.getCustomer() == null || ...` 之下的路径），不加这个检查。
结果是 `update` 和 `delete` 只处理 `qb.from(X.class).where(同一个 Lambda 表达式)` 返回的**正是那些**行（已将删除的行与查询结果比较）。

以 `@Id` 结尾的路径（`o.getCustomer().getId()`）是 FK 列。既没有子查询，也没有 join。

<a id="batching-for-many-rows"></a>
### 多行的分批处理

`values(list, ...)` 写出一条带多个 VALUES 行的语句。

| 项目 | 规则 |
|---|---|
| 拆分单位 | 值的数量（行 x 列），而不是行数 |
| 上限 | 每条语句最多 500 个值（固定） |
| 返回值 | 所有语句的总和 |
| 事务 | 所有语句在一个事务中执行 |
| 空列表 | 不执行 SQL，返回 0 |

Oracle 有 65,535 个绑定值的硬性上限（ORA-01745）。远在达到上限之前，解析就会变得非常慢
（设计阶段实测：21,845 行约 6 分钟）。因此上限设得较小。

<a id="limit-change-the-first-rows"></a>
### limit：改变前几行

在 `where(...)` 或 `all()` 之后加上 `orderBy(...).limit(n)`，update 或 delete 最多只改变 `n` 行。目标是 `qb.from(X.class).where(same).orderBy(same)` 的前 `n` 行。

<!-- region: DmlQueryTest.java#delete-limit -->
```java
// the first rows of qb.from(Order.class).where(same).orderBy(same); the id is the last sort key
ExecutableDelete<Order> d = qb.delete(Order.class)
        .where(o -> o.getStatus() == 1)
        .orderBy(o -> asc(o.getPrice()))
        .limit(1);
assertEquals("delete from Order e0 where e0.id in (select t.id from (select x0.id as id from Order x0"
        + " where x0.status = 1 order by x0.price asc, x0.id asc limit 1) t)", d.toHql().hql());
int deleted = d.execute();
```

| 项目 | 规则 |
|---|---|
| 顺序 | `orderBy` 的键，然后是 id。没有 `orderBy` 时按 id 排序 |
| 没有 `limit` 的 `orderBy` | 错误（`IllegalStateException`） |
| 渲染 | `e0.id in (select t.id from (select x0.id as id from X x0 ... order by ... limit n) t)` |
| 目标行 | 内层 select 包括 join 在内都遵循查询的规则，因此与查询的行相同 |
| MariaDB | 通过派生表 `t` 避开错误 1093 和 1235 |

示例和各数据库的 SQL：[7.4](07-4-delete.md#delete-the-first-rows-limit)（delete）、[7.2](07-2-update.md#update-the-first-rows-limit)（update）。

<a id="one-row-protection"></a>
### 单行保护

调用 `executeOne()` 代替 `execute()`，**最多只改变一行**。

1. lambdaQL 读取符合条件的行的 id，最多 2 个。
2. 有 2 个 id 时，不做任何改变，抛出 `NonUniqueResultException`。
3. 有 1 个 id 时，用 `id = :p and (条件)` 改变该行。

不会改变 2 行以上，也不依赖回滚。结果为 0 或 1。`executeOne()` 与 `orderBy` 或 `limit` 一起使用是错误（`IllegalStateException`）。

示例：[7.4](07-4-delete.md#delete-exactly-one-row-executeone)。在 Spring repository 中使用 `deleteOne` 和 `updateOne`（见 [12.4](12-spring-data-jpa.md#one-row-protection-deleteone-and-updateone)）。

<a id="transactions"></a>
### 事务

批量语句需要事务。只有使用 `EntityManagerFactory` 构造函数的构建器，lambdaQL 才会自己打开事务。

| 构建器 | 事务 |
|---|---|
| `new QueryBuilder(entityManagerFactory)` | `execute()` 和 `executeOne()` 打开自己的事务并提交。出错时回滚 |
| 使用 `EntityManager` 的构建器：`new QueryBuilder(entityManager)`、Spring 的 `QueryBuilder` bean、repository 执行器 | 调用方的事务（`@Transactional`，或该 `EntityManager` 的活动事务）。没有事务时，在发送 SQL 之前以 `TransactionRequiredException` 失败。在 Spring repository 调用中变为 `InvalidDataAccessApiUsageException`（见 [12.6](12-spring-data-jpa.md#126-transactions-and-exceptions)） |

<a id="the-persistence-context-is-not-updated"></a>
### 持久化上下文不反映更改

批量 DML 不经过 JPA 持久化上下文。

- Hibernate 在语句执行前 flush 待处理的更改。
- 语句执行后，同一个 `EntityManager` 中已加载的实体保留**旧值**。如果要再次使用这些实体，请调用 `entityManager.clear()`。
- JPA 的 `cascade` 和 `orphanRemoval` **不会**执行。请先删除子行，或在数据库中使用 `ON DELETE CASCADE`。

<a id="sqlrestriction-and-softdelete"></a>
### `@SQLRestriction` 和 `@SoftDelete`

Hibernate 会把实体的 `@SQLRestriction` 条件和 `@SoftDelete` 条件附加到 lambdaQL 生成的所有语句上（在 MariaDB 10.11、Oracle 23.26 上实测，H2 未测）。
批量 `update` 和 `delete` 也是如此。例如：`where m1_0.order_id=? and (m1_0.hidden = 0)`。

`@SoftDelete` 实体的 `delete` 会变成 `update … set deleted = 1 where … and deleted = 0`，行不会被真正删除。
lambdaQL 不单独处理这些条件。

示例代码：[`ManualJavaDmlTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaDmlTest.java)（`old-value`）、[`AssignmentReaderTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/sem/AssignmentReaderTest.java)（`rules`）、[`DmlQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)（`update-path`、`delete-limit`）。

---

<a id="79-all-rules-u1-to-u21-summary"></a>
## 7.9 全部规则 U1～U21（汇总）

lambdaQL 读取 Lambda 表达式时检查的规则：U1、U3、U4、U6、U15。
lambdaQL 拒绝对 `@Version` 字段的赋值（`LQL-3068`）。U11 和 U12 不单独检查。

| # | 规则 |
|---|---|
| U1 | 赋值 Lambda 表达式中只包含对目标、目标的嵌入路径、关联的 `@Id` 的 **setter 调用和 `Dml.value(column, value)`**（`LQL-3037`）。选择是否赋值的 `if`（`LQL-2009`）、循环、局部变量、其他方法调用都是错误。支持选择值的三元运算符。只使用捕获值和常量时（`flag ? 9 : 8`）计算为值，使用实体值时变成 `case when`（[3.5.1](03-where.md#351-values-chosen-by-a-condition)）。没有赋值的 Lambda 表达式也是错误。 |
| U2 | 右侧遵循 `where` 的值规则（[3. 条件（`where`）](03-where.md)）。捕获值是绑定参数，写在 Lambda 表达式中的常量是字面量。`Objects.requireNonNullElse(a, b)` 变成 `coalesce(a, b)`。 |
| U3 | 右侧读取**旧**值。在同一条语句中给某列赋值后再读取该列是错误。 |
| U4 | 对同一物理列赋值两次是错误。跨多次 `set()` 调用也是如此。 |
| U5 | 没有 `where()` 或 `all()` 就没有 `execute()`。由编译器检查。 |
| U6 | 给非可选列（`@Id`、`nullable=false`、基本类型）赋 `null` 是错误。给集合或关联实体的非 `@Id` 字段赋值也是错误。 |
| U7 | 捕获值每次使用都获得自己的参数。（STRING 列和 ORDINAL 列共享一个 enum 参数时，H2 和 MariaDB 会不报错地存入错误的值。） |
| U8 | `LocalDateTime.now()` 在应用程序中求值后绑定。不支持数据库时间（`current_timestamp`）。 |
| U9 | DML 中非 FK 的关联路径在 `where` 和 SET 右侧都变成关联标量子查询。在 `where` 中，对于查询时会 inner join 的路径加上 `customer.id is not null`，对于会 `left join` 的路径不加。DML 处理与查询相同的行。 |
| U10 | 在 `insert ... select` 中，源路径变成显式的 `left join`。 |
| U11 | `o.setCustomer(entity)` 绑定实体的 `@Id`。id 为 null 时绑定失败。 |
| U12 | IDENTITY 和 SEQUENCE 的 id 可以省略。TABLE 生成器只在 `values` 中有效，在 `insert ... select` 中无效。没有生成器时需要 id。其他非可选字段也是必需的。 |
| U13 | 多行按每条语句最多 500 个值拆分。结果相加。所有语句在一个事务中执行。 |
| U14 | 一个 `set` Lambda 表达式可以给多列赋值，`set()` 也可以调用多次。所有赋值按调用顺序合并为一个 SET 列表。setter 和 `Dml.value` 在同一个 Lambda 表达式中也可以混用。 |
| U15 | `@Version` 实体使用 `update versioned`。直接给版本赋值是 `LQL-3068`。检查 `execute() == 0` 由使用者负责。 |
| U16 | 批量 DML 跳过持久化上下文。执行前 flush，执行后实体过期，没有 cascade。 |
| U17 | 如果 DELETE 的子查询读取目标实体（或同一继承层次），lambdaQL 先通过派生表读取目标 id：`d0.id in (select t.id from (select e0.id as id from … where <condition>) t)`。否则会出现 MariaDB 错误 1093。也适用于关联子查询；`update` 和复合 id 实体不包装（见 [7.4](07-4-delete.md#delete-with-a-subquery-on-the-same-table)）。 |
| U18 | 继承：完全支持 SINGLE_TABLE。JOINED 和 TABLE_PER_CLASS 的 update/delete 使用临时表。拒绝向其子类 insert。 |
| U19 | 查询缓存的键包括入口、目标实体、set Lambda 表达式、where/all、是否 versioned、拆分大小。 |
| U20 | `Dml.value(column, value)`：第一个参数必须是从目标（或 `insert ... select` 的目标 `a`）开始的 getter 调用（`LQL-3039`）。同一属性的 setter 规则（U1、U6，关联只能赋值 `@Id`）原样适用。值的类型与列类型对照（`LQL-3065`）。可以与 setter 混用。 |
| U21 | 每个 Lambda 表达式只分析一次并缓存。跨多次 `set()` 调用的检查只比较缓存的摘要（原型中约 0.1～0.2 µs）。 |

Upsert 的规则见 [7.6 Upsert](07-6-upsert.md#rules)。

---

<a id="710-not-in-v01"></a>
## 7.10 不支持的内容

| 项目 | 替代做法 |
|---|---|
| 条件值（`c ? a : b`、作为值的 `!o.isActive()`） | – |
| 在 Lambda 表达式中用 `if` 选择赋值 | 多次调用 `set()` |
| 字符串拼接（`o.getDescription() + "_x"`） | – |
| `insert ... select` 中的显式 join | 使用 many-to-one 路径 |
| `returning` | – |
| 数据库时间（`current_timestamp`） | 绑定应用程序时间 |

违反规则时会抛出带有错误码 `[LQL-xxxx]` 的 `LambdaQLException`。大多数规则为 `TranslationException`，赋值 Lambda 表达式中的条件或局部变量为 `UnsupportedLambdaShapeException`。见 [11. 错误与语言](../11-errors-and-languages.md)。
所有规则汇总在一处：[10. 规则与限制](10-rules-and-limits.md)。

---

[Java 路线](README.md) | 上一章：[6. 查询列、聚合与分组](06-select-group.md) | 下一章：[8. 存储过程与数据库函数](08-procedures-functions.md) | [本页的 Kotlin 版本](../kotlin/07-dml.md)
