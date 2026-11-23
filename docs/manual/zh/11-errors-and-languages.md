<a id="11-errors-and-languages"></a>
# 11. 错误与语言

> [!IMPORTANT]
> 本页比韩文手册旧。最新规则请参见[韩文页面](../ko/11-errors-and-languages.md)。

[手册首页](README.md) | 路线：[Java](java/README.md) | [Kotlin](kotlin/README.md) | 上一章：10. 规则与限制（[Java](java/10-rules-and-limits.md) | [Kotlin](kotlin/10-rules-and-limits.md)） | 下一章：12. Spring Data JPA（[Java](java/12-spring-data-jpa.md) | [Kotlin](kotlin/12-spring-data-jpa.md)）

本页适用于两种语言。代码分别展示在 **Java** 和 **Kotlin** 两个小节中。

<a id="error-codes"></a>
## 错误码

每条 lambdaQL 消息都以 `[LQL-xxxx]` 形式的错误码开头。
错误码与语言无关。只翻译错误码后面的文本。

**(已测试)** [`LqlMessagesTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/i18n/LqlMessagesTest.java)，region `format`：

```java
LqlMessages.setLocale(Locale.ENGLISH);
assertEquals("[LQL-4014] limit must be 0 or more: -1", LqlMessage.LIMIT_NEGATIVE.format(-1));
// the same message in another language: the code stays
assertEquals("[LQL-4014] limit은 0 이상이어야 합니다: -1", LqlMessage.LIMIT_NEGATIVE.format(Locale.KOREAN, -1));
```

所有消息都是枚举 `org.lambdaql.i18n.LqlMessage` 的值。每个值都有一个错误码和参数个数。

| 范围 | 领域 | 目前的错误码 | 异常 |
|---|---|---|---|
| `LQL-1xxx` | 输入与 Lambda 加载 | `LQL-1001` 至 `LQL-1013` | `LambdaAnalysisException` |
| `LQL-2xxx` | 字节码解码 | `LQL-2001` 至 `LQL-2025` | `UnsupportedLambdaShapeException` |
| `LQL-3xxx` | 语义与在 Java 中计算的值 | `LQL-3001` 至 `LQL-3067` | `TranslationException`（`LQL-3066` 是子类 `NonSargableException`） |
| `LQL-4xxx` | 生成、绑定与执行 | `LQL-4001` 至 `LQL-4045`、`LQL-4999`（内部错误） | `LambdaQLException` 本身，或 JDK/JPA 标准异常（见[异常](#exceptions)）。`LQL-4045` 是 `WARN` 日志（只有拆箱为自身基本类型的比较为 `DEBUG`） |
| `LQL-5xxx` | 警告（记录在日志中） | `LQL-5001` 至 `LQL-5013` | –（一条日志。级别为 `WARN`，`LQL-5004` 至 `LQL-5006`、`LQL-5008` 以及默认排序模式下的 `LQL-5003` 为 `INFO`，`LQL-5009` 至 `LQL-5011`、`LQL-5013` 为 `DEBUG`） |

一些实际的错误码：

| 错误码 | 异常 | 消息（中文） |
|---|---|---|
| `LQL-1002` | `LambdaAnalysisException` | 只能分析 Serializable 的 lambda。请使用 lambdaQL 的 lambda 类型，而不是 java.util.function：{0} |
| `LQL-2004` | `UnsupportedLambdaShapeException` | lambda 中不支持循环。请在 lambda 外构建列表并捕获它。 |
| `LQL-3005` | `TranslationException` | where 中不能使用 Agg 聚合函数。请使用 having（J3）：Agg.{0} |
| `LQL-3035` | `TranslationException` | 与聚合一起使用的值必须是 groupBy 键（J3）：{0} |
| `LQL-3058` | `TranslationException` | 数据库函数 {0} 有单独的 SQL 名称，因此必须注册。请把其类加入 lambdaql.functions.classes 配置。 |
| `LQL-3064` | `TranslationException` | 派生表或 CTE 的行类型必须是 Java record 或 Kotlin data class（J5）：{0} |
| `LQL-3065` | `TranslationException` | Dml.value：值的类型 {2} 与列 {0}（{1}）不匹配（U20） |
| `LQL-3066` | `NonSargableException` | 该条件无法使用索引，且 SARGable 模式为 STRICT：{0} |
| `LQL-3067` | `TranslationException` | 派生表或 CTE 的行没有 id，不能与 null 比较。请比较其中的列（例如 x.customerId() == null）：{0} |
| `LQL-4009` | `LambdaQLException` | null 值不能用 <、<=、>、>= 比较（Java 中会抛出 NullPointerException）：{0} |
| `LQL-4014` | `IllegalArgumentException` | limit 必须大于或等于 0：{0} |
| `LQL-4025` | `NonUniqueResultException` | 符合 one() 条件的行有两行或以上。请缩小条件或使用 first()：{0} |
| `LQL-4034` | `DbOnlyException` | {0} 是数据库函数。它只能在 lambdaQL 的 lambda 中使用，不会在 Java 中执行。 |
| `LQL-4036` | `LambdaQLException` | 存根与数据库存储过程 {0} 不一致：{1} |
| `LQL-4040` | `SortNotAllowedException` | {0} 不允许按此名称排序：{1}。请用 SortRules 的 allow 或 alias 允许它。 |
| `LQL-4043` | `LambdaQLException` | {0} 的存根参数与数据库参数名称相同但顺序不同。存根：{1}，数据库：{2} |
| `LQL-4044` | `LambdaQLException` | Oracle 包中有多个同名的存储过程（重载），lambdaQL 无法区分要调用哪一个：{0} |
| `LQL-4045` | –（执行时的 `WARN` 日志，经过拆箱为自身基本类型的比较为 `DEBUG`） | {0} 中捕获的值为 null。在 SQL 中，= NULL 和 <> NULL 不匹配任何行；请在 lambda 中使用 x == null，或在查询前检查该值。 |
| `LQL-5001` | –（`WARN` 日志） | 函数 {0} 作用在列 {1} 上，因此无法使用该列的普通索引，查询可能全表扫描。请改为把列与值比较，或（Oracle）创建基于函数的索引：{2} |
| `LQL-5002` | –（`WARN` 日志） | 没有 limit 的子查询排序不影响结果，已被移除（Q4）：{0} |
| `LQL-5003` | –（`INFO` 或 `WARN` 日志） | 外部排序不在 {0} 的排序规则中：{1}。现在会执行，但在 SortMode.STRICT 下会被拒绝。 |
| `LQL-5004` | –（`INFO` 日志） | 类文件中没有存根参数名称，因此不检查 {0} 的参数顺序。请用 -parameters（Kotlin：-java-parameters）编译，或使用 @DbParam。 |
| `LQL-5005` | –（`INFO` 日志） | 无法从数据库元数据读取 {0} 的参数（{1}）。不检查存根；IN、INOUT 参数按存根顺序，其后为 OUT 参数。 |
| `LQL-5006` | –（`INFO` 日志） | 无法检查 {0} 的重载（{1}）；不做检查继续调用。 |
| `LQL-5007` | –（`WARN` 日志） | MariaDB 函数 {0} 是 NOT DETERMINISTIC，因此会逐行计算，比较列上的索引无法用于范围查找。如果它是确定性的，请声明为 DETERMINISTIC：{1} |
| `LQL-5008` | –（`INFO` 日志） | 无法读取 {0} 的索引或函数元数据（{1}）；在没有元数据的情况下继续 SARGable 检查。 |
| `LQL-5009` | –（`DEBUG` 日志） | SQL 语义：在 {0} 中，值为 NULL 的行不满足条件（与 Java 的 null 处理不同）。要包含这些行，请写出 null 检查，例如 `x == null \|\| ...`。 |
| `LQL-5010` | –（`DEBUG` 日志） | 列 {0} 是 @Lob 列；一些数据库（例如 Oracle）不能在 {1} 中使用 LOB。 |
| `LQL-5011` | –（`DEBUG` 日志） | 枚举列 {0} 以文本存储（@Enumerated(STRING)），因此数据库按名称排序，而不是按枚举的声明顺序。 |
| `LQL-5012` | –（`WARN` 日志） | 对列 {0} 做运算会使条件无法使用索引。请在保持含义的前提下把运算移到值的一侧：用小数相除（用 o.getQty() > n / 2.0 代替 o.getQty() * 2 > n；n / 2 是整数除法），乘以负数时反转比较方向：{1} |
| `LQL-5013` | –（`DEBUG` 日志） | 条件对列 {0} 进行计算，因此无法使用这些列上的索引：{1} |

`{0}` 和 `{1}` 是参数，例如表达式或存储过程名。
完整列表见消息文件（见[致译者](#for-translators)）。

错误码规则：

- 错误码在发布后不会被重复使用。按错误码搜索，或在代码中检查错误码的值，都是安全的。
- 单元测试检查每个错误码都不重复，并且形式为 `LQL-` 加四位数字。

<a id="exceptions"></a>
## 异常

所有 lambdaQL 异常都继承自 `org.lambdaql.LambdaQLException`。它是 `RuntimeException`，因此 Kotlin 和 Java 以相同的方式处理它。
异常类由错误码范围决定。

| 异常 | 错误码 | 何时抛出 | 示例 |
|---|---|---|---|
| `LambdaQLException` | `LQL-4xxx` | 所有 lambdaQL 异常的基类。对于生成、绑定和执行中的问题，直接抛出这个类本身 | 存储过程存根与数据库不一致（`LQL-4036`）。存根参数与数据库参数名称相同但顺序不同（`LQL-4043`，见 [8.4](java/08-procedures-functions.md#84-warning-argument-order) 中的 `@DbParam`）。 |
| `LambdaAnalysisException` | `LQL-1xxx` | lambdaQL 无法加载 Lambda 时 | 用匿名类（Kotlin 中为 `object : ...`）代替了 Lambda。不可序列化的 Lambda（`LQL-1002`）。 |
| `UnsupportedLambdaShapeException` | `LQL-2xxx` | 字节码的结构是 lambdaQL 不支持的 | 循环（`LQL-2004`）。值三元表达式，或作为值的 `if`/`when`。一般的布尔三元表达式 `c ? x : y`。 |
| `TranslationException` | `LQL-3xxx` | Lambda 在 HQL 中没有定义的含义时 | 不是 getter 的实体方法。用 `==` 比较两个实体（暂不支持）。值对象的大小比较（`LQL-3012`）。不是 `groupBy` 键的非聚合值（`LQL-3035`）。 |
| `DbOnlyException` | `LQL-4034` | 在 lambdaQL Lambda 之外，用 Java 调用了自定义的 `@SqlFunction` 方法时 | `OrderFns.discount(1.0)`（见[在 Lambda 外部调用的占位方法](#placeholders-called-outside-a-lambda)） |
| `NonSargableException` | `LQL-3066` | 在 `SargableMode.STRICT` 下分析无法使用索引的条件（对应 `LQL-5001`、`LQL-5007`、`LQL-5012`）时。是 `TranslationException` 的子类 | `OrderFns.discount(o.getTotal()) > 100`、`o.getCnt() * 2 > n`（决策 D50） |
| `SortNotAllowedException` | `LQL-4040` | 在 `SortMode.STRICT` 下，外部排序（Spring `Sort`、`SortSpec.ofPath`）使用了排序规则中没有的名称时 | 只允许 `name` 时的 `Sort.by("grade")`（见 [4.10 排序策略](java/04-order-paging.md#sort-policy)）。Web 应用通常用 HTTP 400 响应（[12.5](java/12-spring-data-jpa.md#sort-policy-with-spring)） |

这些类都在 `org.lambdaql` 包中。

**标准异常保持原来的类型。** Java 代码期望 JDK 或 JPA 异常时，lambdaQL 抛出该类型，只在消息中加上错误码。

| 异常 | 示例 | 错误码 |
|---|---|---|
| `IllegalArgumentException` | `limit(-1)` | `LQL-4014` |
| `IllegalStateException` | 原始类型构造函数参数遇到 NULL 值（规则 J4）。在 Lambda 外部调用 `Agg.sum(...)`。`toSet()` 得到的元素少于行数 | `LQL-4021`、`LQL-4032`、`LQL-4019` |
| `jakarta.persistence.NonUniqueResultException` | `one()` 的结果有两行或以上。符合 `executeOne()` 条件的行有两行或以上 | `LQL-4025`、`LQL-4023` |
| `jakarta.persistence.TransactionRequiredException` | 没有事务的 update、insert 或 delete | `LQL-4024` |

`LambdaQLException` 的方法：

| 方法 | 返回值 |
|---|---|
| `code()` | 固定的错误码，例如 `"LQL-2004"` |
| `key()` | 消息键（一个 `LqlMessage` 值） |
| `args()` | 消息参数 |
| `where()` | Lambda 的位置（包含 Lambda 主体的方法，已知时还有行号），没有时为 `null` |
| `getMessage()` | 使用异常创建时所选 lambdaQL 语言的消息 |
| `getMessage(Locale locale)` | 同一条消息的其他语言版本，例如用 `Locale.ROOT` 获取英文 |

**(已测试)** [`LqlMessagesTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/i18n/LqlMessagesTest.java)，region `exception`：

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

该测试直接创建异常。在实际代码中，lambdaQL 在读取 Lambda 时创建异常。
处理方法如下（设计示例）。

### Java

```java
try {
    List<Order> orders = qb.from(Order.class)
        .where(o -> o.getStatus() == (flag ? 1 : 2))   // 值三元表达式：不支持
        .toList();
} catch (LambdaQLException e) {
    log.error("lambdaQL error {}: {}", e.code(), e.getMessage(Locale.ROOT));
    throw e;
}
```

### Kotlin

```kotlin
try {
    qb.from(Order::class.java)
        .where { it.status == if (flag) 1 else 2 }      // 作为值的 if：不支持
        .toList()
} catch (e: LambdaQLException) {
    log.error("lambdaQL error {}: {}", e.code(), e.getMessage(Locale.ROOT))
    throw e
}
```

<a id="what-a-message-has"></a>
### 消息包含的内容

- 先是错误码，然后是所选语言的文本。
- 末尾以固定格式给出 Lambda 的位置：` — ` 后面是包含 Lambda 主体的方法以及**源代码行号**（也可通过 `where()` 获取）。
- 解码错误后面可能还有包含字节码转储的额外几行。这些额外的行不翻译。
- 大多数消息还会说明解决问题的方法。例如，Kotlin 消息会提示在 Lambda 外部构建列表并捕获它。

<a id="in-tests-check-the-code"></a>
### 在测试中检查错误码

消息文本可能会变化，而且取决于语言。错误码不会变化。

#### Java

```java
var e = assertThrows(LambdaQLException.class, () -> query.toList());
assertEquals("LQL-2004", e.code());     // 推荐
// assertEquals("Loops are ...", e.getMessage());   // 不要这样做
```

#### Kotlin

```kotlin
val e = assertThrows<LambdaQLException> { query.toList() }   // org.junit.jupiter.api.assertThrows
assertEquals("LQL-2004", e.code())      // 推荐
// assertEquals("Loops are ...", e.message)          // 不要这样做
```

<a id="when-errors-happen"></a>
## 错误发生的时机

lambdaQL 在三个时机发现问题。

| 时机 | 检查内容 | 示例 |
|---|---|---|
| **编译时** | Java 或 Kotlin 编译器检查 lambdaQL API 的类型。 | 把实体用作排序键。调用两次 `orderBy`。在 `orderBy` 之后调用 `where`。对没有 `where(...)` 或 `all()` 的 update 或 delete 调用 `execute()`。 |
| **分析时** | 在 SQL 发送到数据库之前，lambdaQL 读取 Lambda 字节码。 | 循环、值三元表达式、不支持的方法调用、不是简单 `by` 链的排序 Lambda、不是 `groupBy` 键的 `select` 值。 |
| **执行时（绑定）** | 每次执行查询时，lambdaQL 都会绑定捕获的值。 | 在规则不允许 null 的地方捕获了 `null` 值（见下文）。`NaN` 或 `Infinity` 参数。用于 `IN` 的捕获集合中有 `null` 元素。 |

分析时的错误与数据无关。同一个 Lambda 总是以相同的方式失败。
lambdaQL 也会缓存失败的结果，所以有问题的 Lambda 下次会很快失败。

<a id="compile-time-examples"></a>
### 编译时示例

#### Java

```java
.orderBy((o, by) -> by.desc(o.getCustomer()))   // 编译错误：实体不能作为排序键
```

#### Kotlin

```kotlin
.orderBy { o, by -> by.desc(o.customer) }        // 编译错误：实体不能作为排序键
```

<a id="analysis-time-errors-that-only-kotlin-has"></a>
### 只有 Kotlin 才有的分析时错误

有些 Kotlin 写法不会生成 lambdaQL 可以读取的 Lambda。lambdaQL 以清晰的错误拒绝这些写法。
完整列表见 [9. Kotlin 说明](kotlin/09-kotlin-notes.md)。

| Kotlin 写法 | 应改为 |
|---|---|
| `val f: (Order) -> Boolean = { ... }; where(f)` | 使用 Lambda 字面量的 `where { ... }` |
| 在 `inline fun` 主体内创建的查询 Lambda | 把辅助函数改为非 inline，或从调用方传入 Lambda 字面量 |
| `it.status in listOf(1, 2, 3)`（在 Lambda 内构建列表） | 在外部写 `val ids = listOf(1, 2, 3)`，然后写 `it.status in ids` |
| 使用 `-Xsam-conversions=class` 编译 | 保持默认的 `-Xsam-conversions=indy` |

<a id="null-captured-values"></a>
### 捕获的 null 值

有些写法接受捕获的 `null` 值，有些则不接受。
`=` 或 `<>` 中使用的捕获值 `null` 不匹配任何行（SQL 语义，决策 D48）。这时 lambdaQL 在执行时以 `WARN` 级别记录 `LQL-4045`。像大小比较这样经过拆箱为自身基本类型（`min.doubleValue()`）的情况为 `DEBUG` 级别。
要匹配 NULL，请直接写出 null 检查。
所有 null 规则见 3. 条件（[Java](java/03-where.md) | [Kotlin](kotlin/03-where.md)）。

#### Java

| 写法 | `name` 为 `null` 时 | 结果 |
|---|---|---|
| `o.getDescription() == name`（引用 `==`） | 执行 | 绑定 NULL 的 `e0.description = :p1`：没有匹配的行。`WARN` 级别 `LQL-4045` |
| `Objects.equals(o.getDescription(), name)` | 执行 | 与上面相同 |
| `o.getDescription().equals(name)` | 执行 | 与上面相同 |
| `name.equals(o.getDescription())` | 执行 | 与上面相同 |
| `o.getStatus() > minStatus`（`minStatus` 为 `null`） | 执行 | 绑定 NULL 的 `e0.status > :p1`：没有匹配的行。不执行拆箱为自身基本类型的操作（`minStatus.intValue()`）。`DEBUG` 级别 `LQL-4045` |
| 捕获值上的方法调用，例如 `name.trim()`、`minStatus.longValue()`（把 `Integer` 转为其他基本类型） | 错误（`LQL-4012`） | 这是在查询前于 Java 中准备值的步骤，因此与 Java 一样相当于 `NullPointerException`。 |

要查找 NULL，请写 `o.getDescription() == null`。
要在 `name` 为 null 时省略过滤，请写 `name == null || o.getDescription().equals(name)`（守卫在查询执行前决定）。

#### Kotlin

| 写法 | 捕获的值为 `null` 时 | 结果 |
|---|---|---|
| `it.description == name` | 执行 | 绑定 NULL 的 `e0.description = :p1`：没有匹配的行。`WARN` 级别 `LQL-4045` |
| `minPrice == null \|\| it.price!! > minPrice` | 允许 | null 检查是直接写出的，所以 lambdaQL 使用它。 |
| `it.price!! > minPrice!!`（`minPrice` 为 `null`） | 执行 | 绑定 NULL 的 `e0.price > :p1`：没有匹配的行。不执行 `!!` 和拆箱。`DEBUG` 级别 `LQL-4045` |
| `it.id in ids`（`ids` 中有 `null` 元素） | 绑定时报错 | 带有 null 元素的 `IN` 在 SQL 中是一个陷阱。 |

<a id="placeholders-called-outside-a-lambda"></a>
### 在 Lambda 外部调用的占位方法

有些方法只是为了在 Lambda 内部被读取而存在。它们的返回值是占位值。

| 方法 | 在查询 Lambda 外部调用时 |
|---|---|
| `Agg.count()`、`Agg.sum(...)` 以及其他 `Agg` 方法 | 抛出 `IllegalStateException`（`LQL-4032`） |
| 自定义的 `@SqlFunction` 方法 | lambdaQL 从不执行其方法体。方法体只能抛出 `DbOnlyException`（`LQL-4034`）。计算值的方法体会被拒绝（`LQL-3052`）。 |

#### Java

**(已测试)** [`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java)，region `rules`（开头部分）：

```java
// the body never runs in Java
assertEquals("LQL-4034", assertThrows(DbOnlyException.class, () -> OrderFns.discount(1.0)).code());
```

`OrderFns` 的声明见 8. 存储过程与数据库函数（[Java](java/08-procedures-functions.md#86-declare-db-functions) | [Kotlin](kotlin/08-procedures-functions.md#86-declare-db-functions)）。

#### Kotlin

```kotlin
interface OrderFns { companion object {
    @JvmStatic @SqlFunction("FN_DISCOUNT") fun discount(price: Double?): Double? = throw DbOnlyException()
} }
```

<a id="message-language"></a>
## 消息语言

lambdaQL 提供**四种语言**的消息。

| 语言 | Locale |
|---|---|
| 英文 | 默认（root） |
| 日文 | `ja` |
| 中文（简体） | `zh` |
| 韩文 | `ko` |

<a id="how-lambdaql-chooses-the-language"></a>
### lambdaQL 选择语言的方式

lambdaQL 按以下顺序检查这三个位置。使用第一个已设置的值。

| 顺序 | 设置 | 示例 |
|---|---|---|
| 1 | 代码中的 `LqlMessages.setLocale(...)` | 见下面的代码 |
| 2 | 系统属性 `lambdaql.locale` | `java -Dlambdaql.locale=ja -jar app.jar` |
| 3 | JVM 默认 locale | `Locale.getDefault()` |

然后 lambdaQL 只看该 locale 的**语言**部分。

| Locale 的语言 | 消息语言 |
|---|---|
| `ja`（例如 `ja_JP`） | 日文 |
| `zh`（也包括 `zh_CN` 和 `zh_TW`） | 中文（简体） |
| `ko`（例如 `ko_KR`） | 韩文 |
| 其他所有语言（`en`、`fr`、`de` 等） | 英文 |

**(已测试)** [`LqlMessagesTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/i18n/LqlMessagesTest.java)，region `locale`：

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

`LqlMessages.LOCALE_PROPERTY` 是名称 `"lambdaql.locale"`。`LqlMessages.bundleLocale(locale)` 返回某个 locale 使用的消息文件。

在应用中这样写（设计示例）。

#### Java

```java
// 在应用启动时
LqlMessages.setLocale(Locale.JAPANESE);   // 日文消息
LqlMessages.setLocale(null);              // 恢复为：先看系统属性，再看 JVM 默认值
Locale current = LqlMessages.locale();    // lambdaQL 当前使用的 locale
```

#### Kotlin

```kotlin
// 在应用启动时
LqlMessages.setLocale(Locale.JAPANESE)    // 日文消息
LqlMessages.setLocale(null)               // 恢复为：先看系统属性，再看 JVM 默认值
val current: Locale = LqlMessages.locale() // lambdaQL 当前使用的 locale
```

说明：

- lambdaQL 自己选择语言，不使用常规的 `ResourceBundle` 回退（fallback）机制。因此不会出现请求英文、却因为 JVM 是韩文而显示韩文消息的情况。
- 消息在异常创建时确定。之后需要其他语言时，请调用 `e.getMessage(locale)`。
- 服务器可能以非英文的 JVM locale 运行。需要英文日志时，请调用 `LqlMessages.setLocale(Locale.ROOT)` 或使用 `-Dlambdaql.locale=en`。
- 如果用户有需要，可以添加繁体中文消息。
- 日文和中文文本最初由作者编写，而不是由母语人士编写。欢迎帮助改进。

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
- 参数写作 `{0}`、`{1}` 等。
- lambdaQL 不使用 `MessageFormat`。因此单引号（`can't`）可以按原样书写。数字不做格式化（行号 `1234567` 仍然是 `1234567`）。
- 每个键都必须以相同的参数存在于全部四个文件中。单元测试会检查这一点。新消息必须以全部四种语言添加。

**(已测试)** [`LqlMessagesTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/i18n/LqlMessagesTest.java)，region `fill`：

```java
// no MessageFormat: quotes and big numbers stay as they are (rule I4)
assertEquals("can't read line 1234567", LqlMessages.fill("can't read line {0}", 1234567));
assertEquals("a null b", LqlMessages.fill("a {0} b", (Object) null));
assertEquals("{x} {9}", LqlMessages.fill("{x} {9}", "unused"));
```

<a id="logging"></a>
## 日志

lambdaQL 通过 **SLF4J API** 记录日志。

- lambdaQL 只依赖 `slf4j-api`。日志后端（Logback、Log4j 2 等）由使用者选择。
- Spring Boot 已经包含 SLF4J 和 Logback，因此不需要添加任何东西。
- 警告消息以错误码开头。错误码大多在 `LQL-5xxx` 范围内，`LQL-4045` 也只记录在日志中。
- 级别（`WARN`、`INFO`、`DEBUG`）因错误码而异。下表列出了每个错误码的级别。
- 如果该级别关闭，lambdaQL 不构建消息文本。

lambdaQL 目前记录的警告：

| 错误码 | 发生条件 |
|---|---|
| `LQL-4045` | `=` 或 `<>`（`==`、`!=`、`equals`、`Objects.equals`）中捕获的值为 `null`，因此没有匹配的行（SQL 语义，决策 D48）。只有执行时才能知道。以 `WARN` 级别记录。像大小比较这样经过拆箱为自身基本类型（`min.doubleValue()`）的情况为 `DEBUG` 级别。要查找 NULL，请写 `x == null`（见 3.6：[Java](java/03-where.md#36-null-rules) \| [Kotlin](kotlin/03-where.md#36-null-rules)） |
| `LQL-5001` | 条件中数据库函数包住了列（`OrderFns.discount(o.getPrice()) > 10`、作用于列的 Boolean 函数、`IN` 或 `IS NULL` 中的函数）。无法使用普通索引。每个 `where` Lambda 以 `WARN` 级别记录一次。在 Oracle 上，如果存在相同表达式的基于函数的索引，则不记录（见 [8.9](java/08-procedures-functions.md#89-warning-functions-on-columns-and-indexes-sargable)） |
| `LQL-5002` | 子查询有 `orderBy` 但没有 `limit`，因此 lambdaQL 移除排序时（排序不影响结果，规则 Q4） |
| `LQL-5003` | 外部排序（Spring `Sort`、`SortSpec.ofPath`）使用了排序规则中没有的名称，并且模式是 `SortMode.INFO`（默认值，以 `INFO` 级别记录）或 `SortMode.WARN`（以 `WARN` 级别记录）时。排序会执行。按实体和名称各记录一次（见 [4.10 排序策略](java/04-order-paging.md#sort-policy)） |
| `LQL-5004` | 类文件中没有存储过程存根的参数名（编译时没有 `-parameters` 或 Kotlin 的 `-java-parameters`，也没有 `@DbParam`），并且两个相邻参数类型相同时。每个存储过程以 `INFO` 级别记录一次（见 [8.4](java/08-procedures-functions.md#84-warning-argument-order)） |
| `LQL-5005` | 无法读取存储过程元数据时（例如没有数据字典视图或 `information_schema` 的权限，查询失败）。不检查存根，参数布局取自存根。每个存储过程以 `INFO` 级别记录一次（见 [8.4](java/08-procedures-functions.md#when-the-metadata-cannot-be-read)） |
| `LQL-5006` | 只有 Oracle 重载查询（`all_arguments`）失败时。不检查重载继续调用。以 `INFO` 级别记录 |
| `LQL-5007` | 仅 MariaDB：值一侧的 `NOT DETERMINISTIC` 数据库函数与列比较时（`o.getPrice() > OrderFns.nondet(base)`）。索引无法用于范围查找。以 `WARN` 级别记录 |
| `LQL-5008` | 无法读取 SARGable 检查所需的索引或函数元数据时（例如没有权限）。以 `INFO` 级别记录。仍会记录 `LQL-5001`，不检查 `LQL-5007` |
| `LQL-5009` | 条件按 SQL 语义比较实体值（决策 D48），因此在 Java 或 Kotlin 中会匹配的 NULL 行可能被排除时：实体值之间的 `==`/`equals`、与值的 `!=`、可空 `Boolean` 的 `!`、关联路径上的 null 守卫（路径仍为内连接）、子查询的 `NOT IN`。每个条件文本以 `DEBUG` 级别记录一次（见 3.6.3：[Java](java/03-where.md#363-write-your-null-checks-yourself) \| [Kotlin](kotlin/03-where.md#363-write-your-null-checks-yourself)） |
| `LQL-5010` | 在 `order by`、`distinct`、`group by` 中使用 `@Lob` 列时。H2 和 MariaDB 会执行，Oracle 拒绝（ORA-22848、ORA-22849）。`=` 比较在三种数据库上都可用。lambdaQL 不修改查询。按列和使用位置各以 `DEBUG` 级别记录一次（见 [4.5](java/04-order-paging.md#45-what-a-sort-key-can-be)） |
| `LQL-5011` | 按 `@Enumerated(EnumType.STRING)` 枚举列排序时。数据库按名称排序，Java 和 Kotlin 按声明顺序排序，因此顺序可能不同。lambdaQL 不修改查询。每列以 `DEBUG` 级别记录一次。`ORDINAL` 不记录（见 [4.5](java/04-order-paging.md#45-what-a-sort-key-can-be)） |
| `LQL-5012` | 条件的列一侧有只由一个列和值组成的算术运算（`+ - * /`）时（`o.getCnt() * 2 > n`）。可以移到值一侧。以 `WARN` 级别记录。在 `SargableMode.STRICT` 下是 `LQL-3066` 错误（决策 D50，见 [8.9](java/08-procedures-functions.md#89-warning-functions-on-columns-and-indexes-sargable)） |
| `LQL-5013` | 条件中有列之间的算术运算或 `%` 时（`o.getPrice() * o.getCnt() > n`）。无法移动，因此只以 `DEBUG` 级别记录。在 `STRICT` 下也允许 |

```
WARN  [LQL-5002] 没有 limit 的子查询排序不影响结果，已被移除（Q4）：...
```

计划中的警告（尚未实现）：

- 不区分大小写的比较在该列上没有匹配的索引或排序规则（collation）时

把所有警告一次性变为错误的通用严格模式尚未实现。
两个领域各有自己的 `STRICT` 模式。

- 排序策略（决策 D43）：在 `SortMode.STRICT` 下，`LQL-5003` 的情况抛出 `SortNotAllowedException`（`LQL-4040`）。
- SARGable（决策 D50）：在 `SargableMode.STRICT` 下，`LQL-5001`、`LQL-5007`、`LQL-5012` 的情况抛出 `NonSargableException`（`LQL-3066`）。

> [!NOTE]
> `lambdaql-core` 不依赖 Spring。日志依赖只有 `slf4j-api`，因此日志实现由使用者选择。Spring 模块（`lambdaql-spring-data-jpa`、`lambdaql-spring-boot-starter`）会带入 Spring Data JPA，starter 还会带入 `spring-boot-starter-data-jpa` 和 Logback。

<a id="summary"></a>
## 总结

| 问题 | 回答 |
|---|---|
| 如何识别 lambdaQL 错误？ | 它是 `LambdaQLException`，消息以 `[LQL-xxxx]` 开头。 |
| 如何在代码或测试中检查错误？ | 使用 `e.code()`。不要比较文本。 |
| 如何在非英文服务器上获得英文文本？ | `LqlMessages.setLocale(Locale.ROOT)`、`-Dlambdaql.locale=en` 或 `e.getMessage(Locale.ROOT)`。 |
| 警告输出到哪里？ | 输出到 SLF4J 日志。级别因代码而异，为 `WARN`、`INFO` 或 `DEBUG` 之一。 |

[手册首页](README.md) | [Java 路线](java/README.md) | [Kotlin 路线](kotlin/README.md)
