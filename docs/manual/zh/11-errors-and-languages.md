<a id="11-errors-and-languages"></a>
# 11. 错误与语言

[手册首页](README.md) | 路线：[Java](java/README.md) | [Kotlin](kotlin/README.md) | 上一章：10. 规则与限制（[Java](java/10-rules-and-limits.md) | [Kotlin](kotlin/10-rules-and-limits.md)） | 下一章：12. Spring Data JPA（[Java](java/12-spring-data-jpa.md) | [Kotlin](kotlin/12-spring-data-jpa.md)）

本页适用于两种语言。代码分别展示在 **Java** 和 **Kotlin** 两个小节中。

<a id="error-codes"></a>
## 错误码

每条 lambdaQL 消息都以 `[LQL-xxxx]` 形式的错误码开头。
错误码不随语言改变。只翻译错误码后面的文本。

<!-- region: LqlMessagesTest.java#format -->
```java
LqlMessages.setLocale(Locale.ENGLISH);
assertEquals("[LQL-4014] limit must be 0 or more: -1", LqlMessage.LIMIT_NEGATIVE.format(-1));
// the same message in another language: the code stays
assertEquals("[LQL-4014] limit은 0 이상이어야 합니다: -1", LqlMessage.LIMIT_NEGATIVE.format(Locale.KOREAN, -1));
```

所有消息都是枚举 `org.lambdaql.i18n.LqlMessage` 的值。每个值都有错误码和参数个数。

| 范围 | 领域 | 目前的错误码 | 异常 |
|---|---|---|---|
| `LQL-1xxx` | 输入与 Lambda 表达式加载 | `LQL-1001`~`LQL-1013` | `LambdaAnalysisException` |
| `LQL-2xxx` | 字节码 opcode 解码 | `LQL-2001`~`LQL-2025` | `UnsupportedLambdaShapeException` |
| `LQL-3xxx` | 语义解析与在 Java 中计算的值 | `LQL-3001`~`LQL-3069` | `TranslationException`（`LQL-3066` 是子类 `NonSargableException`） |
| `LQL-4xxx` | 渲染、绑定、执行 | `LQL-4001`~`LQL-4047`、`LQL-4999`（内部错误） | `LambdaQLException` 本身，或 JDK/JPA 标准异常（见[异常](#exceptions)）。`LQL-4045` 是 `WARN` 日志（只有拆箱为自身基本类型的比较为 `DEBUG`） |
| `LQL-5xxx` | 警告（记录在日志中） | `LQL-5001`~`LQL-5013` | –（一行日志。级别为 `WARN`，`LQL-5004`~`LQL-5006`、`LQL-5008` 以及默认排序模式下的 `LQL-5003` 为 `INFO`，`LQL-5009`~`LQL-5011`、`LQL-5013` 为 `DEBUG`） |

实际错误码的示例：

| 错误码 | 异常 | 消息（中文） |
|---|---|---|
| `LQL-1002` | `LambdaAnalysisException` | 只能分析 Serializable 的 lambda。请使用 lambdaQL 的 lambda 类型，而不是 java.util.function：{0} |
| `LQL-2004` | `UnsupportedLambdaShapeException` | lambda 中不支持循环。请在 lambda 外构建列表并捕获它。 |
| `LQL-3005` | `TranslationException` | where 中不能使用 Agg 聚合函数。请使用 having：Agg.{0} |
| `LQL-3035` | `TranslationException` | 与聚合一起使用的值必须是 groupBy 键：{0} |
| `LQL-3058` | `TranslationException` | 数据库函数 {0} 有单独的 SQL 名称，因此必须注册。请把其类加入 lambdaql.functions.classes 配置。 |
| `LQL-3064` | `TranslationException` | 派生表或 CTE 的行类型必须是 Java record 或 Kotlin data class：{0} |
| `LQL-3065` | `TranslationException` | Dml.value：值的类型 {2} 与列 {0}（{1}）不匹配 |
| `LQL-3066` | `NonSargableException` | 该条件无法使用索引，且 SARGable 模式为 STRICT：{0} |
| `LQL-3067` | `TranslationException` | 派生表或 CTE 的行没有 id，不能与 null 比较。请比较其中的列（例如 x.customerId() == null）：{0} |
| `LQL-3068` | `TranslationException` | 不能给 @Version 列赋值；Hibernate 会在每次 update 时将其加 1：{0} |
| `LQL-3069` | `TranslationException` | 与 Direction 参数一起使用时，排序 Lambda 必须是一个键，而不是 By 的函数：请写成 orderBy(o -> sort(o.getPrice(), direction))。 |
| `LQL-4009` | `LambdaQLException` | null 值不能用 <、<=、>、>= 比较（Java 中会抛出 NullPointerException）：{0} |
| `LQL-4014` | `IllegalArgumentException` | limit 必须大于或等于 0：{0} |
| `LQL-4025` | `NonUniqueResultException` | 符合 one() 条件的行有两行或以上。请缩小条件或使用 first()：{0} |
| `LQL-4034` | `DbOnlyException` | {0} 是数据库函数。它只能在 lambdaQL 的 lambda 中使用，不会在 Java 中执行。 |
| `LQL-4036` | `LambdaQLException` | 存根与数据库存储过程 {0} 不一致：{1} |
| `LQL-4040` | `SortNotAllowedException` | {0} 不允许按此名称排序：{1}。请用 SortRules 的 allow 或 alias 允许它。 |
| `LQL-4043` | `LambdaQLException` | {0} 的存根参数与数据库参数名称相同但顺序不同。存根：{1}，数据库：{2} |
| `LQL-4044` | `LambdaQLException` | Oracle 包中有多个同名的存储过程（重载），lambdaQL 无法区分要调用哪一个：{0} |
| `LQL-4045` | –（执行时 `WARN` 日志，经过拆箱为自身基本类型的比较为 `DEBUG`） | {0} 中捕获的值为 null。在 SQL 中，= NULL 和 <> NULL 不匹配任何行；请在 lambda 中使用 x == null，或在查询前检查该值。 |
| `LQL-4046` | `IllegalStateException` | By 的排序函数 {0} 只用于排序 Lambda；lambdaQL 只读取它，不会执行它。 |
| `LQL-4047` | `LambdaQLException` | 用于 startsWith、endsWith、contains 的字符串为 null（Java 中会抛出 NullPointerException）：{0} |
| `LQL-5001` | –（`WARN` 日志） | 函数 {0} 作用在列 {1} 上，因此无法使用该列的普通索引，查询可能全表扫描。请改为把列与值比较，或（Oracle、PostgreSQL）创建相同表达式的基于函数的索引：{2} |
| `LQL-5002` | –（`WARN` 日志） | 没有 limit 的子查询排序不影响结果，已被移除：{0} |
| `LQL-5003` | –（`INFO` 或 `WARN` 日志） | 外部排序不在 {0} 的排序规则中：{1}。现在会执行，但在 SortMode.STRICT 下会被拒绝。 |
| `LQL-5004` | –（`INFO` 日志） | 类文件中没有存根参数名称，因此不检查 {0} 的参数顺序。请用 -parameters（Kotlin：-java-parameters）编译，或使用 @DbParam。 |
| `LQL-5005` | –（`INFO` 日志） | 无法从数据库元数据读取 {0} 的参数（{1}）。不检查存根；IN、INOUT 参数按存根顺序，其后为 OUT 参数。 |
| `LQL-5006` | –（`INFO` 日志） | 无法检查 {0} 的重载（{1}）；不做检查继续调用。 |
| `LQL-5007` | –（`WARN` 日志） | 函数 {0} 是 NOT DETERMINISTIC（MariaDB、MySQL）或 VOLATILE（PostgreSQL），因此会逐行计算，比较列上的索引无法使用。如果其结果只取决于参数，请声明为 DETERMINISTIC（MariaDB、MySQL）或 STABLE、IMMUTABLE（PostgreSQL）：{1} |
| `LQL-5008` | –（`INFO` 日志） | 无法读取 {0} 的索引或函数元数据（{1}）；在没有元数据的情况下继续 SARGable 检查。 |
| `LQL-5009` | –（`DEBUG` 日志） | SQL 语义：在 {0} 中，值为 NULL 的行不满足条件（与 Java 的 null 处理不同）。要包含这些行，请写出 null 检查，例如 `x == null \|\| ...`。 |
| `LQL-5010` | –（`DEBUG` 日志） | 列 {0} 是 @Lob 列；一些数据库（例如 Oracle）不能在 {1} 中使用 LOB。 |
| `LQL-5011` | –（`DEBUG` 日志） | 枚举列 {0} 以文本存储（@Enumerated(STRING)），因此数据库按名称排序，而不是按枚举的声明顺序。 |
| `LQL-5012` | –（`WARN` 日志） | 对列 {0} 做运算会使条件无法使用索引。请在保持含义的前提下把运算移到值的一侧：用小数相除（用 o.getQty() > n / 2.0 代替 o.getQty() * 2 > n；n / 2 是整数除法），乘以负数时反转比较方向：{1} |
| `LQL-5013` | –（`DEBUG` 日志） | 条件对列 {0} 进行计算，因此无法使用这些列上的索引：{1} |

`{0}`、`{1}` 是参数。例如表达式或存储过程名称会填入其中。
完整列表在消息文件中（见[致译者](#for-translators)）。

错误码规则：

- 错误码在发布后不会再次使用。按错误码搜索，或在代码中检查错误码的值都是安全的。
- 所有错误码都由 `LQL-` 和四位数字组成，且不重复。

示例代码：[`LqlMessagesTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/i18n/LqlMessagesTest.java)（`format`）。

<a id="exceptions"></a>
## 异常

所有 lambdaQL 异常都继承 `org.lambdaql.LambdaQLException`。它是 `RuntimeException`，因此在 Kotlin 和 Java 中以相同方式处理。
异常类由错误码范围决定。

| 异常 | 错误码 | 发生时机 | 示例 |
|---|---|---|---|
| `LambdaQLException` | `LQL-4xxx` | 所有 lambdaQL 异常的基类。渲染、绑定、执行问题直接抛出该类本身 | 与数据库不一致的存储过程存根（`LQL-4036`）。名称与数据库参数相同但顺序不同的存根参数（`LQL-4043`，见 [8.4](java/08-procedures-functions.md#84-warning-argument-order) 的 `@DbParam`）。 |
| `LambdaAnalysisException` | `LQL-1xxx` | lambdaQL 无法加载 Lambda 表达式时 | 用匿名类（Kotlin `object : ...`）代替 Lambda 表达式。不可序列化的 Lambda 表达式（`LQL-1002`）。 |
| `UnsupportedLambdaShapeException` | `LQL-2xxx` | 字节码 opcode 的形态是 lambdaQL 不支持的形态时 | 循环（`LQL-2004`）。`switch` 和 Kotlin 带对象的 `when (x)`（`LQL-2010`）。一般的 boolean 三元运算符 `c ? x : y`。 |
| `TranslationException` | `LQL-3xxx` | Lambda 表达式在 HQL 中没有定义的含义时 | 不是 getter 的实体方法。用 `==` 比较两个实体（尚不支持）。值对象的大小比较（`LQL-3012`）。不是 `groupBy` 键的非聚合值（`LQL-3035`）。 |
| `DbOnlyException` | `LQL-4034` | 在 lambdaQL Lambda 表达式之外的 Java 中调用自定义的 `@SqlFunction` 方法时 | `OrderFns.discount(1.0)`（见[在 Lambda 表达式之外调用的占位方法](#placeholders-called-outside-a-lambda)） |
| `NonSargableException` | `LQL-3066` | 在 `SargableMode.STRICT` 下分析无法使用索引的条件（对应 `LQL-5001`、`LQL-5007`、`LQL-5012`）时。`TranslationException` 的子类 | `OrderFns.discount(o.getTotal()) > 100`、`o.getCnt() * 2 > n` |
| `SortNotAllowedException` | `LQL-4040` | 在 `SortMode.STRICT` 下外部排序（Spring `Sort`、`SortSpec.ofPath`）使用了排序规则中没有的名称时 | 只允许 `name` 时的 `Sort.by("grade")`（见 [4.10 排序策略](java/04-order-paging.md#sort-policy)）。Web 应用通常以 HTTP 400 响应（[12.5](java/12-spring-data-jpa.md#sort-policy-with-spring)） |

这些类都在 `org.lambdaql` 包中。

**标准异常保持其类型。** Java 代码期望 JDK 或 JPA 异常时，lambdaQL 抛出该类型，只在消息中加上错误码。

| 异常 | 示例 | 错误码 |
|---|---|---|
| `IllegalArgumentException` | `limit(-1)` | `LQL-4014` |
| `IllegalStateException` | 传给 primitive 构造函数参数的 NULL 值。在 Lambda 表达式之外调用的 `Agg.sum(...)`。在排序 Lambda 表达式之外调用的 `By` 排序函数（`desc(...)` 等）。元素数少于行数的 `toSet()` | `LQL-4021`、`LQL-4032`、`LQL-4046`、`LQL-4019` |
| `jakarta.persistence.NonUniqueResultException` | `one()` 的结果有 2 行以上。符合 `executeOne()` 条件的行有 2 行以上 | `LQL-4025`、`LQL-4023` |
| `jakarta.persistence.TransactionRequiredException` | 没有事务的 update、insert、delete | `LQL-4024` |

`LambdaQLException` 的方法：

| 方法 | 返回值 |
|---|---|
| `code()` | 固定的错误码，例如 `"LQL-2004"` |
| `key()` | 消息键（`LqlMessage` 值） |
| `args()` | 消息参数 |
| `where()` | Lambda 表达式的位置（包含 Lambda 表达式主体的方法，可知时附带行号），没有则为 `null` |
| `getMessage()` | 异常创建时所选 lambdaQL 语言的消息 |
| `getMessage(Locale locale)` | 同一条消息的其他语言版本，例如英文用 `Locale.ROOT` |

<!-- region: LqlMessagesTest.java#exception -->
```java
LqlMessages.setLocale(Locale.KOREAN);
LambdaQLException e = LqlMessage.LOOP_NOT_SUPPORTED.exceptionAt("app.Repo.lambda$find$0 (line 12)");
// check the code in tests, not the text
assertEquals("LQL-2004", e.code());
assertInstanceOf(UnsupportedLambdaShapeException.class, e);
assertTrue(e.getMessage().startsWith("[LQL-2004] 람다 안의 반복문은"), e.getMessage());
// the same message in English, with the place of the lambda at the end
assertEquals("[LQL-2004] Loops are not supported in a lambda. Build the list outside the lambda and capture it."
        + " — app.Repo.lambda$find$0 (line 12)", e.getMessage(Locale.ROOT));
```

此示例直接创建异常。在实际代码中，lambdaQL 读取 Lambda 表达式时创建异常。
下面的示例捕获使用了 `switch`（Kotlin 为带对象的 `when`）的条件，并把英文消息生成为用于记录的字符串。该条件在调用 `where` 时以 `LQL-2010`（`UnsupportedLambdaShapeException`）失败。

### Java

<!-- region: ManualSharedTest.java#catch -->
```java
String logged;
try {
    qb.from(Order.class)
            .where(o -> switch (o.getStatus()) { case 1, 2 -> true; default -> false; })   // a switch: not supported
            .toList();
    logged = null;
} catch (LambdaQLException e) {
    // for example log.error("lambdaQL error {}: {}", e.code(), e.getMessage(Locale.ROOT))
    logged = "lambdaQL error " + e.code() + ": " + e.getMessage(Locale.ROOT);
}
```

### Kotlin

<!-- region: ManualKotlinErrorsTest.kt#catch -->
```kotlin
val logged = try {
    qb.from(Order::class.java)
        .where { when (it.status.toInt()) { 1, 2 -> true; else -> false } }   // a when on a value: not supported
        .toList()
    null
} catch (e: LambdaQLException) {
    // for example log.error("lambdaQL error {}: {}", e.code(), e.getMessage(Locale.ROOT))
    "lambdaQL error ${e.code()}: ${e.getMessage(Locale.ROOT)}"
}
```

<a id="what-a-message-has"></a>
### 消息包含的内容

- 错误码，然后是所选语言的文本。
- 末尾以固定格式附上 Lambda 表达式的位置。` — ` 之后是包含 Lambda 表达式主体的方法和**源代码行号**（也可以通过 `where()` 获取）。
- 解码错误可能还会附加一行字节码 opcode 转储。这一附加行不翻译。
- 大多数消息还会说明修正方法。例如 Kotlin 消息会提示在 Lambda 表达式外构建列表并捕获它。

<a id="in-tests-check-the-code"></a>
### 在测试中检查错误码

消息文本可能改变，并且随语言而不同。错误码不会改变。

#### Java

<!-- region: ManualSharedTest.java#check-code -->
```java
var e = assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(o -> switch (o.getStatus()) { case 1, 2 -> true; default -> false; }));
assertEquals("LQL-2010", e.code());                 // good
assertInstanceOf(UnsupportedLambdaShapeException.class, e);
// assertEquals("Could not ...", e.getMessage());   // do not do this
```

#### Kotlin

<!-- region: ManualKotlinErrorsTest.kt#check-code -->
```kotlin
// org.junit.jupiter.api.assertThrows
val e = assertThrows<LambdaQLException> { qb.from(Order::class.java).where { when (it.status.toInt()) { 1, 2 -> true; else -> false } } }
assertEquals("LQL-2010", e.code())                           // good
assertInstanceOf(UnsupportedLambdaShapeException::class.java, e)
// assertEquals("Could not ...", e.message)                 // do not do this
```

示例代码：[`LqlMessagesTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/i18n/LqlMessagesTest.java)（`exception`）、[`ManualSharedTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualSharedTest.java)（`catch`、`check-code`）、[`ManualKotlinErrorsTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinErrorsTest.kt)（`catch`、`check-code`）。

<a id="when-errors-happen"></a>
## 错误发生的时机

lambdaQL 在两个时机发现问题。

| 时机 | 检查内容 | 示例 |
|---|---|---|
| **分析时** | 在 SQL 发往数据库之前，lambdaQL 读取 Lambda 表达式的字节码 opcode。 | 循环、`switch`、不支持的方法调用、既不是单个键也不是 `By` 函数简单链的排序 Lambda 表达式、与排序函数一起使用的 `Direction` 参数（`LQL-3069`）、不是 `groupBy` 键的 `select` 值。 |
| **执行时（绑定）** | 每次执行查询时，lambdaQL 绑定捕获的值。 | 规则上不允许 null 的位置出现的 `null` 捕获值（见下文）。`NaN` 或 `Infinity` 参数。用于 `IN` 的捕获集合中的 `null` 元素。 |

分析时的错误与数据无关。同一个 Lambda 表达式总是以同样的方式失败。
lambdaQL 也会缓存失败的结果，因此错误的 Lambda 表达式下次会很快失败。

<a id="analysis-time-errors-that-only-kotlin-has"></a>
### 仅 Kotlin 才有的分析时错误

部分 Kotlin 写法不会生成 lambdaQL 能读取的 Lambda 表达式。lambdaQL 以明确的错误拒绝这些写法。
完整列表见 [9. Kotlin 说明](kotlin/09-kotlin-notes.md)。

| Kotlin 写法 | 改写为 |
|---|---|
| `val f: (Order) -> Boolean = { ... }; where(f)` | 用 Lambda 表达式字面量写 `where { ... }` |
| 在 `inline fun` 主体中创建的查询 Lambda 表达式 | 把辅助函数改为非 inline，或在调用方传入 Lambda 表达式字面量 |
| `it.status in listOf(1, 2, 3)`（在 Lambda 表达式中创建的列表） | 在外部写 `val ids = listOf(1, 2, 3)`，然后写 `it.status in ids` |
| 用 `-Xsam-conversions=class` 编译 | 保持默认值 `-Xsam-conversions=indy` |

<a id="null-captured-values"></a>
### null 捕获值

有些写法接受 `null` 捕获值，有些不接受。
用于 `=` 或 `<>` 的捕获值 `null` 不匹配任何行（SQL 语义）。此时 lambdaQL 在执行时以 `WARN` 级别记录 `LQL-4045`。像大小比较那样经过拆箱为自身基本类型（`min.doubleValue()`）时为 `DEBUG` 级别。
要匹配 NULL，请直接写出 null 检查。
完整的 null 规则见 3. 条件（[Java](java/03-where.md) | [Kotlin](kotlin/03-where.md)）。

#### Java

| 写法 | `name` 为 `null` 时 | 结果 |
|---|---|---|
| `o.getDescription() == name`（引用 `==`） | 执行 | 绑定 NULL 的 `e0.description = :p1`：没有匹配的行。`WARN` 级别 `LQL-4045` |
| `Objects.equals(o.getDescription(), name)` | 执行 | 同上 |
| `o.getDescription().equals(name)` | 执行 | 同上 |
| `name.equals(o.getDescription())` | 执行 | 同上 |
| `o.getStatus() > minStatus`（`minStatus` 为 `null`） | 执行 | 绑定 NULL 的 `e0.status > :p1`：没有匹配的行。不执行拆箱为自身基本类型（`minStatus.intValue()`）。`DEBUG` 级别 `LQL-4045` |
| `name.trim()`、`minStatus.longValue()`（把 `Integer` 转为其他基本类型）这类对捕获值的方法调用 | 错误（`LQL-4012`） | 这是在查询前于 Java 中准备值的步骤，因此与 Java 一样相当于 `NullPointerException`。 |

要查找 NULL，请写 `o.getDescription() == null`。
要在 `name` 为 null 时省略过滤，请写 `name == null || o.getDescription().equals(name)`（守卫在查询执行前决定）。

#### Kotlin

| 写法 | 捕获值为 `null` 时 | 结果 |
|---|---|---|
| `it.description == name` | 执行 | 绑定 NULL 的 `e0.description = :p1`：没有匹配的行。`WARN` 级别 `LQL-4045` |
| `minPrice == null \|\| it.price!! > minPrice` | 允许 | null 检查是直接写出的，所以 lambdaQL 使用它。 |
| `it.price!! > minPrice!!`（`minPrice` 为 `null`） | 执行 | 绑定 NULL 的 `e0.price > :p1`：没有匹配的行。不执行 `!!` 和拆箱。`DEBUG` 级别 `LQL-4045` |
| `it.id in ids`（`ids` 中有 `null` 元素） | 绑定时报错 | 含 null 元素的 `IN` 是 SQL 的陷阱。 |

<a id="placeholders-called-outside-a-lambda"></a>
### 在 Lambda 表达式之外调用的占位方法

有些方法只为在 Lambda 表达式中被读取而存在。其返回值是占位值。

| 方法 | 在查询 Lambda 表达式之外调用时 |
|---|---|
| `Agg.count()`、`Agg.sum(...)` 及其他 `Agg` 方法 | 抛出 `IllegalStateException`（`LQL-4032`） |
| `By.asc(...)`、`By.desc(...)`、`By.sort(...)` | 在排序 Lambda 表达式之外调用时抛出 `IllegalStateException`（`LQL-4046`） |
| 自定义的 `@SqlFunction` 方法 | 主体不会被调用。在 Lambda 表达式之外直接调用时抛出 `DbOnlyException`（`LQL-4034`）。主体只能抛出该异常，计算值的主体会被拒绝（`LQL-3052`）。 |

#### Java

<!-- region: ManualSharedTest.java#db-only -->
```java
// the body never runs in Java
assertEquals("LQL-4034", assertThrows(DbOnlyException.class, () -> OrderFns.discount(1.0)).code());
```

`OrderFns` 的声明见 8. 存储过程与数据库函数（[Java](java/08-procedures-functions.md#86-declare-db-functions) | [Kotlin](kotlin/08-procedures-functions.md#86-declare-db-functions)）。

#### Kotlin

<!-- region: ManualKotlinErrorsTest.kt#db-only -->
```kotlin
// the body never runs in Kotlin
assertEquals("LQL-4034", assertThrows<DbOnlyException> { OrderFns.discount(1.0) }.code())
```

示例代码：[`ManualSharedTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualSharedTest.java)（`db-only`）、[`ManualKotlinErrorsTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinErrorsTest.kt)（`db-only`）。

<a id="message-language"></a>
## 消息语言

lambdaQL 以**四种语言**提供消息。

| 语言 | 区域设置 |
|---|---|
| 英文 | 默认（root） |
| 日文 | `ja` |
| 中文（简体） | `zh` |
| 韩文 | `ko` |

<a id="how-lambdaql-chooses-the-language"></a>
### lambdaQL 选择语言的方式

lambdaQL 按以下顺序检查三个位置。使用第一个已设置的值。

| 顺序 | 设置 | 示例 |
|---|---|---|
| 1 | 代码中的 `LqlMessages.setLocale(...)` | 见下面的代码 |
| 2 | 系统属性 `lambdaql.locale` | `java -Dlambdaql.locale=ja -jar app.jar` |
| 3 | JVM 默认区域设置 | `Locale.getDefault()` |

然后 lambdaQL 只看该区域设置的**语言**。

| 区域设置的语言 | 消息语言 |
|---|---|
| `ja`（例如 `ja_JP`） | 日文 |
| `zh`（包括 `zh_CN` 和 `zh_TW`） | 中文（简体） |
| `ko`（例如 `ko_KR`） | 韩文 |
| 其他所有语言（`en`、`fr`、`de`、...） | 英文 |

<!-- region: LqlMessagesTest.java#locale -->
```java
// 1. setLocale wins, 2. then -Dlambdaql.locale, 3. then the JVM default
LqlMessages.setLocale(Locale.JAPANESE);
assertEquals(Locale.JAPANESE, LqlMessages.locale());
LqlMessages.setLocale(null);
System.setProperty(LqlMessages.LOCALE_PROPERTY, "ko_KR");
assertEquals(Locale.KOREAN, LqlMessages.bundleLocale(LqlMessages.locale()));
// only the language counts; other languages get English (the root bundle)
assertEquals(Locale.CHINESE, LqlMessages.bundleLocale(Locale.TRADITIONAL_CHINESE));
assertEquals(Locale.ROOT, LqlMessages.bundleLocale(Locale.FRENCH));
```

`LqlMessages.LOCALE_PROPERTY` 是名称 `"lambdaql.locale"`。`LqlMessages.bundleLocale(locale)` 返回该区域设置使用的消息文件。

在应用程序中这样写：

#### Java

<!-- region: ManualSharedTest.java#set-locale -->
```java
// at application start
LqlMessages.setLocale(Locale.JAPANESE);   // Japanese messages
Locale current = LqlMessages.locale();    // the locale lambdaQL uses now
LqlMessages.setLocale(null);              // back to the default: the system property, then the JVM default
```

#### Kotlin

<!-- region: ManualKotlinErrorsTest.kt#set-locale -->
```kotlin
// at application start
LqlMessages.setLocale(Locale.JAPANESE)       // Japanese messages
val current: Locale = LqlMessages.locale()   // the locale lambdaQL uses now
LqlMessages.setLocale(null)                  // back to the default: the system property, then the JVM default
```

说明：

- lambdaQL 自行选择语言，不使用一般的 `ResourceBundle` 回退（fallback）。因此不会出现请求英文却因 JVM 是韩文而输出韩文消息的情况。
- 消息在异常创建时确定。之后需要其他语言时，请调用 `e.getMessage(locale)`。
- 服务器可能以非英文的 JVM 区域设置运行。需要英文日志时，请调用 `LqlMessages.setLocale(Locale.ROOT)` 或使用 `-Dlambdaql.locale=en`。
- 如有用户需要，可以添加繁体中文消息。
- 日文和中文文本由作者先行编写，而非母语者。欢迎帮助改进。

<a id="for-translators"></a>
### 致译者

消息文件是 jar 中的 UTF-8 properties 文件。

```
org/lambdaql/i18n/LqlMessages.properties      英文（root）
org/lambdaql/i18n/LqlMessages_ja.properties   日文
org/lambdaql/i18n/LqlMessages_zh.properties   中文（简体）
org/lambdaql/i18n/LqlMessages_ko.properties   韩文
```

- 键是 `LqlMessage` 值的名称，例如 `LOOP_NOT_SUPPORTED`。
- 参数写作 `{0}`、`{1}`、...。
- lambdaQL 不使用 `MessageFormat`，因此单引号（`can't`）可以原样书写。数字不做格式化（行号 `1234567` 仍为 `1234567`）。
- 所有键都必须以相同的参数出现在全部四个文件中。新消息必须以全部四种语言添加。

<!-- region: LqlMessagesTest.java#fill -->
```java
// no MessageFormat: quotes and big numbers stay as they are (rule I4)
assertEquals("can't read line 1234567", LqlMessages.fill("can't read line {0}", 1234567));
assertEquals("a null b", LqlMessages.fill("a {0} b", (Object) null));
assertEquals("{x} {9}", LqlMessages.fill("{x} {9}", "unused"));
```

示例代码：[`LqlMessagesTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/i18n/LqlMessagesTest.java)（`locale`、`fill`）、[`ManualSharedTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualSharedTest.java)（`set-locale`）、[`ManualKotlinErrorsTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinErrorsTest.kt)（`set-locale`）。

<a id="logging"></a>
## 日志

lambdaQL 通过 **SLF4J API** 记录日志。

- lambdaQL 只依赖 `slf4j-api`。日志后端（Logback、Log4j 2 等）自行选择。
- Spring Boot 已包含 SLF4J 和 Logback，因此无需添加任何内容。
- 警告消息以错误码开头。错误码大多在 `LQL-5xxx` 范围内，`LQL-4045` 也只记录在日志中。
- 级别（`WARN`、`INFO`、`DEBUG`）因错误码而异。下表列出各错误码的级别。
- 该级别关闭时，lambdaQL 不生成消息文本。

lambdaQL 目前记录的警告：

| 错误码 | 发生条件 |
|---|---|
| `LQL-4045` | `=`、`<>`（`==`、`!=`、`equals`、`Objects.equals`）的捕获值为 `null`，因此没有匹配的行时（SQL 语义）。只有执行时才能知道。以 `WARN` 级别记录。像大小比较那样经过拆箱为自身基本类型（`min.doubleValue()`）时为 `DEBUG` 级别。要查找 NULL，请写 `x == null`（见 3.6：[Java](java/03-where.md#36-null-rules) \| [Kotlin](kotlin/03-where.md#36-null-rules)） |
| `LQL-5001` | 条件中数据库函数包裹列时（`OrderFns.discount(o.getPrice()) > 10`、作用于列的 Boolean 函数、`IN` 或 `IS NULL` 中的函数）。无法使用普通索引。每个 `where` Lambda 表达式以 `WARN` 级别记录一次。在 Oracle 或 PostgreSQL 中存在相同表达式的基于函数（表达式）的索引时不记录（见 [8.9](java/08-procedures-functions.md#89-warning-functions-on-columns-and-indexes-sargable)） |
| `LQL-5002` | 子查询有 `orderBy` 但没有 `limit`，lambdaQL 移除排序时（排序不影响结果） |
| `LQL-5003` | 外部排序（Spring `Sort`、`SortSpec.ofPath`）使用了排序规则中没有的名称，且模式为 `SortMode.INFO`（默认值，以 `INFO` 级别记录）或 `SortMode.WARN`（以 `WARN` 级别记录）时。排序会执行。每个实体和名称记录一次（见 [4.10 排序策略](java/04-order-paging.md#sort-policy)） |
| `LQL-5004` | 类文件中没有存储过程存根的参数名称（未使用 `-parameters`、Kotlin `-java-parameters` 编译，也没有 `@DbParam`），且相邻两个参数类型相同时。每个存储过程以 `INFO` 级别记录一次（见 [8.4](java/08-procedures-functions.md#84-warning-argument-order)） |
| `LQL-5005` | 无法读取存储过程元数据时（例如没有字典视图或 `information_schema` 的权限而查询失败）。不检查存根，参数布局取自存根。每个存储过程以 `INFO` 级别记录一次（见 [8.4](java/08-procedures-functions.md#when-the-metadata-cannot-be-read)） |
| `LQL-5006` | 只有 Oracle 重载查询（`all_arguments`）失败时。不检查重载直接调用。以 `INFO` 级别记录 |
| `LQL-5007` | 值一侧的数据库函数在 MariaDB、MySQL 中为 `NOT DETERMINISTIC`，在 PostgreSQL 中为 `VOLATILE`，并把该值与列比较时（`o.getPrice() > OrderFns.nondet(base)`）。函数逐行计算，比较列的索引无法使用（已通过三种数据库的执行计划确认）。以 `WARN` 级别记录 |
| `LQL-5008` | 无法读取 SARGable 检查用的索引或函数元数据时（例如没有权限）。以 `INFO` 级别记录。`LQL-5001` 照常记录，`LQL-5007` 不检查 |
| `LQL-5009` | 条件以 SQL 语义比较实体值，在 Java、Kotlin 中会匹配的 NULL 行可能被排除时：实体值之间的 `==`/`equals`、与值的 `!=`、可为 NULL 的 `Boolean` 的 `!`、子查询 `NOT IN`。每个条件语句以 `DEBUG` 级别记录一次（见 3.6.3：[Java](java/03-where.md#363-write-your-null-checks-yourself) \| [Kotlin](kotlin/03-where.md#363-write-your-null-checks-yourself)） |
| `LQL-5010` | 在 `order by`、`distinct`、`group by` 中使用 `@Lob` 列时。H2 和 MariaDB 会执行，但 Oracle 拒绝（ORA-22848、ORA-22849）。`=` 比较在三种数据库中都可用。lambdaQL 不改写查询。每个列和用途以 `DEBUG` 级别记录一次（见 [4.5](java/04-order-paging.md#45-what-a-sort-key-can-be)） |
| `LQL-5011` | 按 `@Enumerated(EnumType.STRING)` 枚举列排序时。数据库按名称排序，Java、Kotlin 按声明顺序排序，因此顺序可能不同。lambdaQL 不改写查询。每个列以 `DEBUG` 级别记录一次。`ORDINAL` 不记录（见 [4.5](java/04-order-paging.md#45-what-a-sort-key-can-be)） |
| `LQL-5012` | 只由一个列和值构成的算术运算（`+ - * /`）位于条件的列一侧时（`o.getCnt() * 2 > n`）。可以移到值一侧。以 `WARN` 级别记录。在 `SargableMode.STRICT` 下为 `LQL-3066` 错误（见 [8.9](java/08-procedures-functions.md#89-warning-functions-on-columns-and-indexes-sargable)） |
| `LQL-5013` | 条件中有列之间的算术运算或 `%` 时（`o.getPrice() * o.getCnt() > n`）。无法移动，因此只以 `DEBUG` 级别记录。在 `STRICT` 下也允许 |

```
WARN  [LQL-5002] 没有 limit 的子查询排序不影响结果，已被移除（Q4）：...
```

计划中的警告（尚未实现）：

- 不区分大小写的比较在该列上没有匹配的索引或排序规则（collation）时

把所有警告一次性变为错误的通用严格模式尚未实现。
取而代之，两个领域各有自己的 `STRICT` 模式。

- 排序策略：在 `SortMode.STRICT` 下，`LQL-5003` 的情况会抛出 `SortNotAllowedException`（`LQL-4040`）。
- SARGable：在 `SargableMode.STRICT` 下，`LQL-5001`、`LQL-5007`、`LQL-5012` 的情况会抛出 `NonSargableException`（`LQL-3066`）。

> [!NOTE]
> `lambdaql` 不依赖 Spring。日志依赖只有 `slf4j-api`，因此日志实现由用户选择。`lambdaql-spring-data-jpa` 直接使用项目中的 Spring Data JPA。starter（`lambdaql-spring-boot-starter-boot3`、`-boot4`）还会一并带入 `spring-boot-starter-data-jpa` 和 Logback。

<a id="summary"></a>
## 总结

| 问题 | 回答 |
|---|---|
| 如何识别 lambdaQL 错误？ | 它是 `LambdaQLException`，消息以 `[LQL-xxxx]` 开头。 |
| 如何在代码或测试中检查错误？ | 使用 `e.code()`。不要比较文本。 |
| 如何在非英文服务器上获得英文文本？ | `LqlMessages.setLocale(Locale.ROOT)`、`-Dlambdaql.locale=en`，或 `e.getMessage(Locale.ROOT)`。 |
| 警告输出到哪里？ | 输出到 SLF4J 日志。级别因错误码而异，是 `WARN`、`INFO`、`DEBUG` 之一。 |

[手册首页](README.md) | [Java 路线](java/README.md) | [Kotlin 路线](kotlin/README.md)
