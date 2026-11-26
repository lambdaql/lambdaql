<a id="1-overview"></a>
# 1. 概述

> [!IMPORTANT]
> 本页比韩文手册旧。最新规则请参见[韩文页面](../ko/01-overview.md)。

[手册首页](README.md) | 路线：[Java](java/README.md) | [Kotlin](kotlin/README.md) | 下一章：2. 入门（[Java](java/02-getting-started.md) | [Kotlin](kotlin/02-getting-started.md)）

本页适用于两种语言。代码分别展示在 **Java** 和 **Kotlin** 两个小节中。

<a id="what-is-lambdaql"></a>
## lambdaQL 是什么？

lambdaQL 是运行在 JPA 和 Hibernate 之上、面向 Java 和 Kotlin 的查询库。

- lambdaQL 利用 **Lambda 表达式**来表达和编写 JPA 查询。
- 不执行 Lambda 表达式部分，而是**分析并转换为查询**。
- lambdaQL 把方法和 Lambda 表达式的执行结果生成为 **HQL（JPQL）** 查询。

### Java

<!-- region: ManualSharedTest.java#overview-first -->
```java
long minPrice = 100;

SelectQuery<Order> q = qb.from(Order.class)
        .where(o -> o.getStatus() == 1 || o.getCustomer().getName().equals("Kim"))
        .where(o -> o.getPrice() > minPrice);
assertEquals("select e0 from Order e0 left join e0.customer j1"
        + " where (e0.status = 1 or j1.name = 'Kim') and e0.price > :p1", q.toHql().hql());
List<Order> rows = q.toList();
```

### Kotlin

<!-- region: ManualKotlinSharedTest.kt#overview-first -->
```kotlin
val minPrice = 100.0

val q = qb.from(Order::class.java)
    .where { it.status.toInt() == 1 || it.customer!!.name == "Kim" }
    .where { it.price > minPrice }
assertEquals("select e0 from Order e0 left join e0.customer j1"
    + " where (e0.status = 1 or j1.name = 'Kim') and e0.price > :p1", q.toHql().hql())
val rows = q.toList()
```

<a id="hql-the-same-for-both"></a>
### HQL（两种语言相同）

```
select e0 from Order e0 left join e0.customer j1
where (e0.status = 1 or j1.name = 'Kim') and e0.price > :p1
```

**各数据库的 SQL**

<!-- sql: overview-first -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where (o1_0.status=1 or c1_0.name='Kim') and o1_0.price>?
```

- 两次 `where` 调用以 `AND` 连接。
- 常量 `"Kim"` 作为字面量写入查询，捕获的 `minPrice` 作为绑定参数（`:p1`）处理。
- 在 `where` 中像 `o.getCustomer().getName()` 这样使用关联实体的属性时，lambdaQL 会连接客户表。
  没有客户的订单可能满足条件时为 `left join`，不可能满足时为 inner `join`。
  - 上面的示例中，status 为 1 的订单即使没有客户也满足条件，因此为 `left join`。
  - 如果 Where 条件只有 `o.getCustomer().getName().equals("Kim")` 一个，则为 inner `join`。

Lambda 表达式看起来像普通的 Java 或 Kotlin 代码。
但在 lambdaQL 中，Lambda 表达式是用 Java 或 Kotlin 语法编写的**查询语言**。
`o.getCustomer().getName()`（Kotlin 中为 `it.customer?.name`）不会调用 getter，而是**作为列名求值**。

示例代码：[`ManualSharedTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualSharedTest.java)（`overview-first`）、[`ManualKotlinSharedTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSharedTest.kt)（`overview-first`）。

<a id="why-lambdaql"></a>
## 为什么选择 lambdaQL？

| 目标 | lambdaQL 的做法 |
|---|---|
| 类型安全 | 编译器检查 Lambda 表达式。在 IDE 中重命名属性时，查询中的名称也随之改变。 |
| 无需生成代码 | 不需要元模型生成器或 Q 类。lambdaQL 使用 Hibernate 在运行时已有的 JPA Metamodel。 |
| 熟悉的语法 | 使用已经熟悉的 `==`、`&&`、`\|\|`、`!`、getter 或属性，以及方法调用。 |
| 安全的值 | 捕获的值和计算出的值始终成为绑定参数。只有编译进代码的常量才写入查询文本（单引号会被转义）。 |
| 分析并转换 Lambda 表达式 | 不执行 Lambda 表达式部分，而是进行分析。不创建实体对象，也不调用 getter，因此能生成直观的查询语句。 |
| 分析失败即错误 | lambdaQL 无法把 Lambda 表达式分析为查询时，会抛出带错误码的异常。 |

<a id="how-it-works-5-steps"></a>
## 工作原理（5 个步骤）

```
 your lambda ──► 1. find body ──► 2. read bytecode ──► 3. rebuild condition
                                                              │
     result ◄── 5. run with Hibernate ◄── 4. build HQL ◄──────┘
```

| 步骤 | 内容 |
|---|---|
| 1. 找到 Lambda 表达式主体 | lambdaQL 找到 Lambda 表达式主体被编译到的位置，以及 Lambda 表达式捕获的值。 |
| 2. 读取字节码 opcode | lambdaQL 读取第 1 步找到的 Lambda 表达式主体的编译代码（字节码 opcode）。不执行代码。在 Java 21~23 上使用 ASM，在 Java 24 及以上版本上使用 JDK 的 `java.lang.classfile`。 |
| 3. 重建条件 | Java 和 Kotlin 把 `&&`、`\|\|`、`!` 编译为跳转 opcode。lambdaQL 根据这些跳转 opcode 重建原始的逻辑树。 |
| 4. 构建 HQL | getter 和属性变成 JPA 属性路径（`o.getPrice()` 或 `it.price` → `e0.price`）。Lambda 表达式中的常量变成字面量，捕获的值变成参数（`:p1`）。 |
| 5. 执行 | lambdaQL 绑定参数，并通过 Hibernate 执行 HQL。每个 Lambda 表达式的分析结果都会被缓存，因此字节码 opcode 只读取一次。 |

lambdaQL 按 Lambda 表达式缓存分析结果，因此字节码 opcode 只读取和分析一次。  
新 JVM 的首次调用因分析 Lambda 表达式比 JPQL 多花约 26ms，此后每次查询增加的开销约为 0.5~3µs，非常小（H2 实测，[基准测试](../../design/BENCHMARK.md)）。


传给 lambdaQL 的 Kotlin Lambda 表达式字面量，会被编译为与 Java Lambda 表达式同类的可序列化 Lambda 表达式。
因此一个引擎可以同时读取两种语言。

<a id="design-principles"></a>
## 设计原则

<a id="one-lambda-per-clause"></a>
### 每个查询子句一个 Lambda 表达式

`where`、`orderBy`、`select` 等查询子句各自接收**一个** Lambda 表达式。
要查询的列或排序键全部写在各子句的这一个 Lambda 表达式中。

#### Java

<!-- region: ManualSharedTest.java#one-lambda-per-clause -->
```java
ValueQuery<OrderView> q = qb.from(Order.class)
        .where(o -> o.getStatus() == 1 && o.isActive())                 // one condition lambda
        .orderBy(o -> desc(o.getPrice()).asc(o.getId()))     // one sort lambda for all keys
        .select(o -> new OrderView(o.getId(), o.getPrice()));          // one select lambda
assertEquals("select e0.id, e0.price from Order e0 where e0.status = 1 and e0.active = true"
        + " order by e0.price desc, e0.id asc", q.toHql().hql());
```

#### Kotlin

<!-- region: ManualKotlinSharedTest.kt#one-lambda-per-clause -->
```kotlin
val q = qb.from(Order::class.java)
    .where { it.status.toInt() == 1 && it.isActive }               // one condition lambda
    .orderBy { o -> desc(o.price).asc(o.id) }               // one sort lambda for all keys
    .select { OrderView(it.id, it.price) }                         // one select lambda
assertEquals("select e0.id, e0.price from Order e0 where e0.status = 1 and e0.active = true"
    + " order by e0.price desc, e0.id asc", q.toHql().hql())
```

<a id="no-static-helpers-except-agg"></a>
### 静态辅助类 `By`、`Agg`、`Dml`

| 类 | 用途 |
|---|---|
| `org.lambdaql.query.By` | 排序函数（`asc`、`desc`、`sort`） |
| `org.lambdaql.Agg` | 聚合（`count`、`sum`、`avg`、`min`、`max`、`countDistinct`） |
| `org.lambdaql.Dml` | 为没有 setter 的实体赋值（`Dml.value(column, value)`） |

#### Java

<!-- region: ManualSharedTest.java#agg -->
```java
ValueQuery<CustomerTotal> q = qb.from(Order.class)
        .groupBy(o -> o.getCustomer().getId())
        .having(o -> Agg.sum(o.getPrice()) > 1000)
        .select(o -> new CustomerTotal(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice())));
assertEquals("select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id"
        + " having sum(e0.price) > 1000.0", q.toHql().hql());
```

#### Kotlin

<!-- region: ManualKotlinSharedTest.kt#agg -->
```kotlin
val q = qb.from(Order::class.java)
    .groupBy { it.customer?.id }
    .having { Agg.sum(it.price) > 1000 }
    .select { CustomerTotal(it.customer?.id, Agg.count(), Agg.sum(it.price)) }
assertEquals("select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id"
    + " having sum(e0.price) > 1000.0", q.toHql().hql())
```

<a id="a-lambda-is-a-query-not-code"></a>
### Lambda 表达式是查询表达式，不是代码

lambdaQL 遵循自身有文档说明的规则，而不是 Java 或 Kotlin 的执行规则。

#### Java

| 编写的代码 | lambdaQL 的含义 | 原因 |
|---|---|---|
| `o.getId() == id`（两个 `Long` 对象） | 值相等：`e0.id = :p1` | 在查询中，对象上的 `==` 表示“值相同”，而不是“同一个对象”。捕获的值 `null` 绑定为 NULL，不匹配任何行（`WARN` 级别 `LQL-4045`）。要查找 NULL，请写 `o.getId() == null`。 |
| `Objects.equals(o.getDescription(), o.getCustName())`、`!text.equals(o.getDescription())` | `e0.description = e0.custName`、`e0.description <> :p1`（SQL 语义） | 比较两个实体值，或用 `!=` 与一个值比较时，值为 NULL 的行不匹配。Java 会返回其中一些行。要保留这些行，请写 `o.getDescription() == null \|\| ...`。 |
| `o.getCustomer().getName()` | 所连接客户的 `name` 列 | 以 inner `join` 或 `left join` 连接客户表。详细规则见 [3.10 关联路径](java/03-where.md#310-association-paths)。 |
| `status` 列为 NULL 时的 `o.getStatus() == 1` | 不返回该行（SQL 语义） | `status` 列为 NULL 的行不在结果中。 |
| `o.getDescription() == null` | `e0.description is null` | null 检查是直接写出的，所以 lambdaQL 使用它。 |

> [!TIP]
> IntelliJ 可能会对 `o.getId() == id` 显示警告 "Number objects compared using `==`"。
> 在 lambdaQL 的 Lambda 表达式中这是预期行为。在方法上声明 `@SuppressWarnings("NumberEquality")` 即可消除警告。

#### Kotlin

| 编写的代码 | lambdaQL 的含义 | 原因 |
|---|---|---|
| `it.id == id`（`id: Long?`，可空变量） | 值相等：`e0.id = :p1` | Kotlin 的 `==` 本来就表示“值相同”。捕获的值 `null` 绑定为 NULL，不匹配任何行（`WARN` 级别 `LQL-4045`）。 |
| `it.description == it.custName`、`it.description != text` | `e0.description = e0.custName`、`e0.description <> :p1`（SQL 语义） | 比较两个实体值，或用 `!=` 与一个值比较时，值为 NULL 的行不匹配。要保留这些行，请写 `it.description == null \|\| ...`。 |
| `it.customer?.name` 或 `it.customer!!.name` | 所连接客户的 `name` 列 | 以 inner `join` 或 `left join` 连接客户表。详细规则见 [3.10 关联路径](kotlin/03-where.md#310-association-paths)。 |
| `price` 列为 NULL 时的 `it.price!! > 100.0` | 不返回该行（SQL 语义） | `price` 列为 NULL 的行不在结果中。 |
| `it.description == null` | `e0.description is null` | null 检查是直接写出的，所以 lambdaQL 使用它。 |

**NULL 的处理与 SQL 相同。**  
所比较的列为 NULL 的行不满足 NULL 比较条件，因此不在结果中。
例如 `where(o -> !text.equals(o.getDescription()))`（Kotlin 为 `where { it.description != text }`）会成为 HQL `e0.description <> :p1`。  
`description` 列为 NULL 的订单不满足此条件，因此不在结果中。这与 Java 或 Kotlin 的列表不同：在列表中 NULL 与 `text` 不同，因此会被包含。
要同时得到为 NULL 的订单，请像 `o.getDescription() == null || !text.equals(o.getDescription())` 这样直接写出 null 检查。  
详细规则见 3.6 NULL 规则（[Java](java/03-where.md#36-null-rules) | [Kotlin](kotlin/03-where.md#36-null-rules)）。

### SARGable SQL

lambdaQL 生成可以使用索引的 SQL（“SARGable”）。

- 不在列一侧使用函数、运算或 `CASE`。
- 需要转换值时（例如转为小写），lambdaQL 在绑定前在 Java 中转换**参数**。
- 如果规则要求在列上使用函数且索引无法起作用，lambdaQL 会记录警告日志。

#### Java

| 编写的代码 | lambdaQL **不会**生成 | lambdaQL 生成 |
|---|---|---|
| `o.getId() != null && o.getId() == id` | `e0.id is not null and e0.id = :p1` | `e0.id = :p1`（id 不会是 NULL） |
| `o.getDescription() == text` | `e0.description is not distinct from :p1`（Oracle 上为 `decode(...)`，无法使用索引） | `e0.description = :p1` |

#### Kotlin

| 编写的代码 | lambdaQL **不会**生成 | lambdaQL 生成 |
|---|---|---|
| `it.id == id`（`it.id` 是 `Long?`） | `not (e0.id is null or e0.id <> :p1)` | `e0.id = :p1`（id 不会是 NULL） |

<a id="java-and-kotlin"></a>
### Java 与 Kotlin

Java 和 Kotlin 使用同一个分析引擎。因此含义相同的 Kotlin Lambda 表达式和 Java Lambda 表达式生成相同的 HQL。
例如 Java 的 `o.getPrice() > 100` 和 Kotlin 的 `it.price > 100.0` 都会成为 `e0.price > 100.0`。
但并非所有语法都能转换为查询。使用循环、`switch` 和 Kotlin 带对象的 `when (x)`、作用于列的大部分字符串函数（`trim()` 等）这类语法时，lambdaQL 会在执行查询之前报错。
各语言的差异见第 9 章（[Java 说明](java/09-java-notes.md) | [Kotlin 说明](kotlin/09-kotlin-notes.md)）。

<a id="fail-loudly"></a>
### 无法分析的 Lambda 表达式

lambdaQL 无法把 Lambda 表达式分析为查询时，会抛出带有类似 `[LQL-2004]` 错误码的异常（见 [11. 错误与语言](11-errors-and-languages.md)）。

会被拒绝的写法示例：

| 写法 | Java | Kotlin |
|---|---|---|
| 由条件选择的值 | `o.getStatus() == (flag ? 1 : 2)` | `it.status == if (flag) 1 else 2` |
| Lambda 表达式内部的循环或局部变量 | 是，会被拒绝 | 是，会被拒绝 |
| 不是 Lambda 表达式 | 匿名类 | `object : ...` 表达式，或 Kotlin 函数类型的值（`val f: (Order) -> Boolean`） |
| 在 SQL 中没有意义的方法调用 | 是，会被拒绝 | 是，会被拒绝（包括自定义的非 inline 扩展函数） |

示例代码：[`ManualSharedTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualSharedTest.java)（`one-lambda-per-clause`、`agg`）、[`ManualKotlinSharedTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSharedTest.kt)（`one-lambda-per-clause`、`agg`）。

<a id="comparison-with-other-approaches"></a>
## 与其他方式的比较

| | JPQL / HQL 字符串 | JPA Criteria API | QueryDSL | lambdaQL |
|---|---|---|---|---|
| 查询的编写方式 | 字符串中的文本 | Java 构建器对象 | 生成的 Q 类 | Lambda 表达式 |
| 编译器检查 | ❌（由 Hibernate 在运行时检查） | ✅ | ✅ | ✅ |
| 需要生成代码 | ❌ | 可选（静态元模型） | ✅（Q 类） | ❌ |
| IDE 中重命名安全 | ❌ | 使用静态元模型时 | ✅ | ✅ |
| 外观 | SQL | Java 构建器代码 | 类似 SQL 的 Java 方法 | 类似 SQL 的 Java / Kotlin Lambda 表达式 |
| Kotlin | 字符串 | 可用，但冗长 | 可用 | 支持 |

<a id="next"></a>
## 下一步

- 2. 入门：构建 lambdaQL 并编写第一个查询。[Java](java/02-getting-started.md) | [Kotlin](kotlin/02-getting-started.md)
- [手册首页](README.md)
