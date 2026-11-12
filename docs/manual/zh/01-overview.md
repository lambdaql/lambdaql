# 1. 概述

[手册首页](README.md) | 路线：[Java](java/README.md) | [Kotlin](kotlin/README.md) | 下一章：2. 入门（[Java](java/02-getting-started.md) | [Kotlin](kotlin/02-getting-started.md)）

> [!NOTE]
> **状态：** 分析核心（Lambda 加载和条件解码）以及 `where` + `toList()`（HQL 生成和查询执行）已实现。它们已在 H2、MariaDB 和 Oracle 上用 Java Lambda 测试。
> 排序与分页的核心部分（`orderBy`、`limit`、`offset`）也已实现并测试。
> 子查询、`select`、update/insert/delete、存储过程和 Spring 模块已设计，但**尚未实现**。
> 本页展示的 HQL 是设计所定义的输出。示例均为设计示例。

本页适用于两种语言。代码分别展示在 **Java** 和 **Kotlin** 两个小节中。

## lambdaQL 是什么？

lambdaQL 是一个构建在 JPA 和 Hibernate 6.6 之上、面向 Java 和 Kotlin 的查询库。

- 你用 **Lambda 表达式**编写查询。
- lambdaQL **读取**每个 Lambda 的**字节码**。它**从不执行** Lambda。
- lambdaQL 根据字节码构建 **HQL**（Hibernate Query Language）。
- 写在 Lambda 中的常量（`1`、`100.0`、`"Kim"`）会被写入 HQL。Lambda 捕获的值会成为**绑定参数**。
- Hibernate 在你的数据库上执行 HQL。

### Java

```java
long minPrice = 100;

List<Order> orders = qb.from(Order.class)
    .where(o -> o.getStatus() == 1 || o.getCustomer().getName().equals("Kim"))
    .where(o -> o.getPrice() > minPrice)
    .toList();
```

### Kotlin

```kotlin
val minPrice = 100.0

val orders = qb.from(Order::class.java)
    .where { it.status == 1 || it.customer?.name == "Kim" }
    .where { it.price!! > minPrice }
    .toList()
```

### HQL（两种语言相同）

```
select e0 from Order e0 left join e0.customer j1
where (e0.status = 1 or j1.name = 'Kim') and e0.price > :p1
```

**关于 SQL 块。** `…` 是缩短的 select 列表（实体的所有列），`?` 是绑定参数。这些行来自 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)。该测试使用 Hibernate 6.6.5 在 H2 2.3、MariaDB 10.11 和 Oracle 23.26 上运行每个查询，如果 Hibernate 的 SQL 发生变化，测试就会失败。

**各数据库的 SQL（已测试）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `overview-first`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where (o1_0.status=1 or c1_0.name='Kim') and o1_0.price>?
```

两次 `where` 调用以 `AND` 连接。
常量 `"Kim"` 是字面量；捕获的 `minPrice` 是绑定参数（`:p1`）。
`where` 中像 `o.getCustomer().getName()` 这样的路径默认是内连接 `join`：没有客户的订单不会被返回。
这里它是 `left join`，因为状态为 1 的订单即使没有客户也会匹配（Java 也会返回它）。

这些 Lambda 看起来像普通的 Java 或 Kotlin 代码。
但它们其实是用 Java 或 Kotlin 语法编写的**查询语言**。
`o.getCustomer().getName()`（Kotlin 中为 `it.customer?.name`）并不会调用 getter，它**指代一个列**。

## 为什么选择 lambdaQL？

| 目标 | lambdaQL 的做法 |
|---|---|
| 类型安全 | 编译器会检查 Lambda。在 IDE 中重命名属性时，查询也会随之被重命名。 |
| 无需生成代码 | 你不需要元模型生成器或 Q 类。lambdaQL 使用 Hibernate 在运行时已有的 JPA Metamodel。 |
| 常规语法 | 你使用已经熟悉的 `==`、`&&`、`\|\|`、`!`、getter 或属性，以及方法调用。 |
| 值的安全 | 捕获的值和计算出的值始终是绑定参数。只有编译进你的代码的常量才会被写入查询文本（引号会被转义）。 |
| 不执行 Lambda | 不会创建实体对象。不会调用 getter。Lambda 不会抛出 `NullPointerException`。 |
| 清晰的错误 | 如果 Lambda 中有 lambdaQL 无法翻译的写法，它会抛出带错误码的错误，而不会去猜测。 |

<a id="how-it-works-5-steps"></a>
## 工作原理（5 个步骤）

```
 your lambda ──► 1. find body ──► 2. read bytecode ──► 3. rebuild condition
                                                              │
     result ◄── 5. run with Hibernate ◄── 4. build HQL ◄──────┘
```

| 步骤 | 发生了什么 | 状态 |
|---|---|---|
| 1. 找到 Lambda 主体 | Lambda 是 `Serializable` 的。lambdaQL 通过序列化钩子获取其 `SerializedLambda`（不使用 `setAccessible`）。由此 lambdaQL 知道哪个方法包含 Lambda 主体，以及捕获了哪些值。 | 已实现 |
| 2. 读取字节码 | lambdaQL 使用 ASM 只读取这一个方法。它读取 class 文件，不执行代码。 | 已实现 |
| 3. 重建条件 | Java 和 Kotlin 会把 `&&`、`\|\|` 和 `!` 编译为跳转指令。lambdaQL 根据这些跳转重建原始的逻辑树。 | Java Lambda 已实现。Kotlin 特有的写法已设计。 |
| 4. 构建 HQL | getter 和属性变成 JPA 属性路径（`o.getPrice()` 或 `it.price` → `e0.price`）。Lambda 中的常量变成字面量；捕获的值变成参数（`:p1`）。 | 已为 `where` 和 `orderBy` 实现，已在 H2、MariaDB、Oracle 上测试 |
| 5. 执行 | lambdaQL 绑定参数，并通过 Hibernate 执行 HQL。每个 Lambda 的分析结果都会被缓存，因此字节码只读取一次。 | 已为 `toList()` 实现（也包括 `limit` / `offset`） |

读取一个 Lambda 只需几微秒。有了缓存，lambdaQL 对每个 Lambda 的字节码只读取一次。

传给 lambdaQL 的 Kotlin Lambda 字面量，会被编译为与 Java Lambda 同类的可序列化 Lambda。
这就是一个引擎能同时读取两种语言的原因。

## 设计原则

### 每个子句一个 Lambda

每个子句只接收**一个** Lambda。你不需要为每一列写一个 Lambda。

#### Java

```java
.where(o -> o.getStatus() == 1 && o.isActive())                 // 一个条件 Lambda
.orderBy((o, by) -> by.desc(o.getPrice()).asc(o.getId()))     // 一个排序 Lambda 包含所有排序键
.select(o -> new OrderView(o.getId(), o.getPrice()))           // 一个查询列 Lambda
```

#### Kotlin

```kotlin
.where { it.status == 1 && it.active }                          // 一个条件 Lambda
.orderBy { o, by -> by.desc(o.price).asc(o.id) }                // 一个排序 Lambda 包含所有排序键
.select { OrderView(it.id, it.price) }                          // 一个查询列 Lambda
```

像排序构建器 `by` 和子查询工厂 `sub` 这样的辅助对象是 **Lambda 参数**。
你可以给它们取任何名字。本手册使用 `by` 和 `sub`。

### 除 `Agg` 外没有静态辅助方法

使用 lambdaQL 不需要静态导入。
唯一的静态辅助类是用于聚合的 `org.lambdaql.Agg`（`count`、`sum`、`avg`、`min`、`max`、`countDistinct`）。
静态导入 `Agg` 是可选的。

#### Java

```java
.having(o -> Agg.sum(o.getPrice()) > 1000)
```

#### Kotlin

```kotlin
.having { Agg.sum(it.price)!! > 1000 }
```

你自己的数据库函数也是静态方法，但需要你自己声明
（见 8. 存储过程与数据库函数：[Java](java/08-procedures-functions.md) | [Kotlin](kotlin/08-procedures-functions.md)）。

### Lambda 是查询，不是代码

lambdaQL 遵循它自己有文档说明的规则，而不是 Java 或 Kotlin 的运行时规则。

#### Java

| 你写的 | lambdaQL 的含义 | 原因 |
|---|---|---|
| `o.getId() == id`（两个 `Long` 对象） | 值相等，null 安全 | 在查询中，对象上的 `==` 表示"值相同"，而不是"同一个对象"。 |
| `o.getCustomer().getName()` | 关联客户的 `name` 列（在 `where` 中：默认是内连接 `join`） | 路径指代一个列。lambdaQL 不检查 customer 是否为 null。在 `where` 中，没有客户的订单不会被返回；要查找它们，请写 `o.getCustomer() == null`。null 守卫（`o.getCustomer() == null \|\| ...`）会让路径变成 `left join`，因此 Java 和 SQL 给出相同的行。 |
| `status` 为 NULL 时的 `o.getStatus() == 1` | 该行不匹配（SQL 语义） | Java 会抛出 `NullPointerException`。SQL 返回 UNKNOWN。 |
| `o.getDescription() == null` | `e0.description is null` | 你写了 null 检查，所以 lambdaQL 使用它。 |

> [!TIP]
> IntelliJ 可能会对 `o.getId() == id` 发出警告 "Number objects compared using `==`"。
> 在 lambdaQL 的 Lambda 中这是有意为之。你可以使用 `@SuppressWarnings("NumberEquality")`。

#### Kotlin

| 你写的 | lambdaQL 的含义 | 原因 |
|---|---|---|
| `it.id == id`（`id: Long?`，可空变量） | 值相等，null 安全 | Kotlin 的 `==` 本来就表示"值相同"。lambdaQL 保留这个含义。 |
| `it.customer?.name` 或 `it.customer!!.name` | 关联客户的 `name` 列（在 `where` 中：默认是内连接 `join`） | 路径指代一个列。`?.` 和 `!!` 不会改变列。`!!` 只是为了让 Lambda 能够编译。在 `where` 中，没有客户的订单不会被返回；要查找它们，请写 `it.customer == null`。null 守卫（`it.customer == null \|\| ...`）会让路径变成 `left join`。 |
| `price` 为 NULL 时的 `it.price!! > 100.0` | 该行不匹配（SQL 语义） | Kotlin 会在 `!!` 处抛出 `NullPointerException`。lambdaQL 忽略 `!!`，SQL 返回 UNKNOWN。 |
| `it.description == null` | `e0.description is null` | 你写了 null 检查，所以 lambdaQL 使用它。 |

lambdaQL **只在你写出 null 处理的地方**处理 null。
见 3. 条件（[Java](java/03-where.md) | [Kotlin](kotlin/03-where.md)）
和 10. 规则与限制（[Java](java/10-rules-and-limits.md) | [Kotlin](kotlin/10-rules-and-limits.md)）。

### SARGable SQL

lambdaQL 生成可以使用索引的 SQL（"SARGable"）。

- 它不会在列一侧使用函数、算术运算或 `CASE`。
- 如果某个值必须转换（例如转为小写），lambdaQL 会在绑定前在 Java 中转换**参数**。
- 如果某条规则需要在列上使用函数且索引无法起作用，lambdaQL 会记录一条警告日志。

#### Java

| 你写的 | lambdaQL **不会**生成 | lambdaQL 生成 |
|---|---|---|
| `!sub.from(Customer.class).where(c -> c.getGrade() > 3).select(c -> c.getId()).contains(o.getCustomer().getId())` | `o.customer.id not in (select ...)` | `not exists (select 1 ...)` |

#### Kotlin

| 你写的 | lambdaQL **不会**生成 | lambdaQL 生成 |
|---|---|---|
| `o.customer?.id !in sub.from(Customer::class.java).where { c -> c.grade!! > 3 }.select { c -> c.id }` | `o.customer.id not in (select ...)` | `not exists (select 1 ...)` |
| `(it.description ?: "") == "x"` | `coalesce(e0.description, '') = 'x'` | `e0.description = 'x'` |
| `it.description.isNullOrEmpty()` | `(e0.description is null or length(e0.description) = 0)` | `(e0.description is null or e0.description = :p1)` |

### Java 与 Kotlin

Java 和 Kotlin 使用**同一个引擎**。
含义相同的 Kotlin Lambda 和 Java Lambda 会生成相同的 HQL。
有些 Kotlin 写法不受支持。对于这些写法，lambdaQL 会抛出清晰的错误（见 [9. Kotlin 说明](kotlin/09-kotlin-notes.md)）。
Java 特有的细节见 [9. Java 说明](java/09-java-notes.md)。

### 明确报错

如果 lambdaQL 不知道某种写法的含义，它不会猜测。
它会抛出带有类似 `[LQL-2004]` 错误码的异常（见 [11. 错误与语言](11-errors-and-languages.md)）。

会被拒绝的写法示例：

| 写法 | Java | Kotlin |
|---|---|---|
| 由条件选择的值 | `o.getStatus() == (flag ? 1 : 2)` | `it.status == if (flag) 1 else 2` |
| Lambda 内部的循环或局部变量 | 是，会被拒绝 | 是，会被拒绝 |
| 不是 Lambda | 匿名类 | `object : ...` 表达式，或 Kotlin 函数类型的值（`val f: (Order) -> Boolean`） |
| 在 SQL 中没有意义的方法调用 | 是，会被拒绝 | 是，会被拒绝（你自己写的非 inline 扩展函数也是如此） |

## 与其他方式的比较

此表是简短、中立的总结。每种工具适合不同的需求。

| | JPQL / HQL 字符串 | JPA Criteria API | QueryDSL | lambdaQL |
|---|---|---|---|---|
| 如何编写查询 | 字符串中的文本 | Java 构建器对象 | 生成的 Q 类 | Lambda 表达式 |
| 由编译器检查 | 否（由 Hibernate 在运行时检查） | 是 | 是 | 是 |
| 需要生成代码 | 否 | 可选（静态元模型） | 是（Q 类） | 否 |
| IDE 中重命名安全 | 否 | 使用静态元模型时 | 是 | 是 |
| 看起来像 | SQL | Java 构建器代码 | 类 SQL 的 Java | 普通的 Java / Kotlin 表达式 |
| Kotlin | 字符串 | 可用，但冗长 | 可用 | 支持（同一引擎） |
| 成熟度 | 标准 | 标准 | 成熟，广泛使用 | 早期开发，未发布 |

## 下一步

- 2. 入门：构建 lambdaQL 并编写你的第一个查询。[Java](java/02-getting-started.md) | [Kotlin](kotlin/02-getting-started.md)
- [手册首页](README.md)
