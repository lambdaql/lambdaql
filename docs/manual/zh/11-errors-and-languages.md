# 11. 错误与语言

[手册首页](README.md) | 路线：[Java](java/README.md) | [Kotlin](kotlin/README.md) | 上一章：10. 规则与限制（[Java](java/10-rules-and-limits.md) | [Kotlin](kotlin/10-rules-and-limits.md)） | 下一章：12. Spring Data JPA（[Java](java/12-spring-data-jpa.md) | [Kotlin](kotlin/12-spring-data-jpa.md)）

> [!NOTE]
> **状态：** 已实现并测试（决定 D33）。
> 每条消息都有错误码 `[LQL-xxxx]`，消息提供英文、日文、中文（简体）和韩文四种语言。
> 异常是 `LambdaQLException` 及其子类。警告输出到 SLF4J。
> 标记为 **(已测试)** 的示例是从测试代码（[`LqlMessagesTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/i18n/LqlMessagesTest.java)、[`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java)）中复制的。本页的其他代码为设计示例。
> 尚未实现：非 SARGable 警告 `LQL-5001`，以及把警告变为错误的严格模式。

本页适用于两种语言。代码分别展示在 **Java** 和 **Kotlin** 两个小节中。

<a id="error-codes"></a>
## 错误码

每条 lambdaQL 消息都以 `[LQL-xxxx]` 形式的错误码开头。
错误码与语言无关。错误码后面的文本会被翻译。

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
| `LQL-3xxx` | 语义与在 Java 中计算的值 | `LQL-3001` 至 `LQL-3064` | `TranslationException` |
| `LQL-4xxx` | 生成、绑定与执行 | `LQL-4001` 至 `LQL-4042`、`LQL-4999`（内部错误） | `LambdaQLException` 本身，或 JDK/JPA 标准异常（见[异常](#exceptions)） |
| `LQL-5xxx` | 警告（记录在日志中） | `LQL-5002`、`LQL-5003` | –（一条 `WARN` 日志） |

一些实际的错误码：

| 错误码 | 异常 | 中文文本 |
|---|---|---|
| `LQL-1002` | `LambdaAnalysisException` | 只能分析 Serializable 的 lambda。请使用 lambdaQL 的 lambda 类型，而不是 java.util.function：{0} |
| `LQL-2004` | `UnsupportedLambdaShapeException` | lambda 中不支持循环。请在 lambda 外构建列表并捕获它。 |
| `LQL-3005` | `TranslationException` | where 中不能使用 Agg 聚合函数。请使用 having（J3）：Agg.{0} |
| `LQL-3035` | `TranslationException` | 与聚合一起使用的值必须是 groupBy 键（J3）：{0} |
| `LQL-3058` | `TranslationException` | 数据库函数 {0} 有单独的 SQL 名称，因此必须注册。请把其类加入 lambdaql.functions.classes 配置。 |
| `LQL-3064` | `TranslationException` | 派生表或 CTE 的行类型必须是 Java record 或 Kotlin data class（J5）：{0} |
| `LQL-4009` | `LambdaQLException` | null 值不能用 <、<=、>、>= 比较（Java 中会抛出 NullPointerException）：{0} |
| `LQL-4014` | `IllegalArgumentException` | limit 必须大于或等于 0：{0} |
| `LQL-4025` | `NonUniqueResultException` | 符合 one() 条件的行有两行或以上。请缩小条件或使用 first()：{0} |
| `LQL-4034` | `DbOnlyException` | {0} 是数据库函数。它只能在 lambdaQL 的 lambda 中使用，不会在 Java 中执行。 |
| `LQL-4036` | `LambdaQLException` | 存根与数据库存储过程 {0} 不一致：{1} |
| `LQL-4040` | `SortNotAllowedException` | {0} 不允许按此名称排序：{1}。请用 SortRules 的 allow 或 alias 允许它。 |
| `LQL-5002` | –（`WARN` 日志） | 没有 limit 的子查询排序不影响结果，已被移除（Q4）：{0} |
| `LQL-5003` | –（`WARN` 日志） | 外部排序不在 {0} 的排序规则中：{1}。现在会执行，但在 SortMode.STRICT 下会被拒绝。 |

`{0}` 和 `{1}` 是参数，例如表达式或存储过程名。
完整列表见消息文件（见[致译者](#for-translators)）。

错误码规则：

- 错误码在发布后永远不会被重复使用。你可以放心地搜索某个错误码，或在代码中检查它。
- 有一个单元测试检查每个错误码都是唯一的，并且形式为 `LQL-` 加四位数字。

<a id="exceptions"></a>
## 异常

所有 lambdaQL 异常都继承自 `org.lambdaql.LambdaQLException`。它是一个 `RuntimeException`，因此 Kotlin 和 Java 以相同的方式处理它。
异常类由错误码范围决定。

| 异常 | 错误码 | 何时抛出 | 示例 |
|---|---|---|---|
| `LambdaQLException` | `LQL-4xxx` | 所有 lambdaQL 异常的基类。对于生成、绑定和执行中的问题，lambdaQL 直接抛出这个类 | 在大小比较中捕获了 `null` 值（`LQL-4009`）。存储过程存根与数据库不一致（`LQL-4036`）。 |
| `LambdaAnalysisException` | `LQL-1xxx` | lambdaQL 无法加载 Lambda | 用匿名类（Kotlin 中为 `object : ...`）代替了 Lambda。不可序列化的 Lambda（`LQL-1002`）。 |
| `UnsupportedLambdaShapeException` | `LQL-2xxx` | 字节码的结构是 lambdaQL 不支持的 | 循环（`LQL-2004`）。值三元表达式，或作为值的 `if`/`when`。一般的布尔三元表达式 `c ? x : y`。 |
| `TranslationException` | `LQL-3xxx` | Lambda 在 HQL 中没有定义的含义 | 不是 getter 的实体方法。用 `==` 比较两个实体（暂不支持）。不是 `groupBy` 键的非聚合值（`LQL-3035`）。 |
| `DbOnlyException` | `LQL-4034` | 在 lambdaQL Lambda 之外，用 Java 调用了你的 `@SqlFunction` 方法 | `OrderFns.discount(1.0)`（见[在 Lambda 外部调用的占位方法](#placeholders-called-outside-a-lambda)） |
| `SortNotAllowedException` | `LQL-4040` | 在 `SortMode.STRICT` 下，外部排序（Spring `Sort`、`SortSpec.ofPath`）使用了排序规则不允许的名称 | 只允许 `name` 时的 `Sort.by("grade")`（见 [4.10 排序策略](java/04-order-paging.md#sort-policy)）。Web 应用通常用 HTTP 400 响应它（[12.5](java/12-spring-data-jpa.md#sort-policy-with-spring)） |

这些类都在 `org.lambdaql` 包中。

**标准异常保持原来的类型。** 在某些情况下，Java 代码期望的是 JDK 或 JPA 异常。这时 lambdaQL 抛出该类型，只有消息带有错误码：

| 异常 | 示例 | 错误码 |
|---|---|---|
| `IllegalArgumentException` | `limit(-1)` | `LQL-4014` |
| `IllegalStateException` | 原始类型构造函数参数遇到 NULL 值（规则 J4）。在 Lambda 外部调用 `Agg.sum(...)`。`toSet()` 得到的元素少于行数 | `LQL-4021`、`LQL-4032`、`LQL-4019` |
| `jakarta.persistence.NonUniqueResultException` | `one()` 找到两行或以上。`executeOne()` 匹配两行或以上 | `LQL-4025`、`LQL-4023` |
| `jakarta.persistence.TransactionRequiredException` | 没有事务的 update、insert 或 delete | `LQL-4024` |

`LambdaQLException` 的方法：

| 方法 | 返回值 |
|---|---|
| `code()` | 稳定的错误码，例如 `"LQL-2004"` |
| `key()` | 消息键（一个 `LqlMessage` 值） |
| `args()` | 消息参数 |
| `where()` | Lambda 的位置（包含 Lambda 主体的方法，已知时还有行号），或 `null` |
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

该测试直接创建异常。在你的代码中，lambdaQL 在读取 Lambda 时创建它。可以这样捕获（设计示例）：

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

### 消息包含什么

- 先是错误码，然后是所选语言的文本。
- 最后以固定格式给出 Lambda 的位置：` — ` 后面是包含 Lambda 主体的方法以及**源代码行号**（也可通过 `where()` 获取）。
- 对于解码错误，后面可能还有包含字节码转储的额外几行。这些额外的行不会被翻译。
- 大多数消息还会告诉你如何解决问题。例如，一条 Kotlin 消息会提示你在 Lambda 外部构建列表并捕获它。

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

## 错误何时发生

lambdaQL 会在三个时机发现问题。

| 时机 | 检查内容 | 示例 |
|---|---|---|
| **编译时** | Java 或 Kotlin 编译器检查 lambdaQL API 的类型。 | 把实体用作排序键。调用两次 `orderBy`。在 `orderBy` 之后调用 `where`。对没有 `where(...)` 或 `all()` 的 update 或 delete 调用 `execute()`。 |
| **分析时** | lambdaQL 读取 Lambda 字节码，此时还没有任何 SQL 发送到数据库。 | 循环、值三元表达式、不支持的方法调用、不是简单 `by` 链的排序 Lambda、不是 `groupBy` 键的 `select` 值。 |
| **运行时（绑定）** | 每次执行查询时，lambdaQL 都会绑定捕获的值。 | 在规则不允许 null 的地方捕获了 `null` 值（见下文）。`NaN` 或 `Infinity` 参数。用于 `IN` 的捕获集合中有 `null` 元素。 |

分析时的错误与你的数据无关。同一个 Lambda 总是以相同的方式失败。
lambdaQL 也会缓存失败的结果，所以有问题的 Lambda 下次会很快失败。

### 编译时示例

#### Java

```java
.orderBy((o, by) -> by.desc(o.getCustomer()))   // 编译错误：实体不能作为排序键
```

#### Kotlin

```kotlin
.orderBy { o, by -> by.desc(o.customer) }        // 编译错误：实体不能作为排序键
```

### 只有 Kotlin 才有的分析时错误

有些 Kotlin 写法无法给 lambdaQL 提供可读取的 Lambda。lambdaQL 会以清晰的错误拒绝它们。
完整列表见 [9. Kotlin 说明](kotlin/09-kotlin-notes.md)。

| Kotlin 写法 | 应改为 |
|---|---|
| `val f: (Order) -> Boolean = { ... }; where(f)` | 使用 Lambda 字面量的 `where { ... }` |
| 在 `inline fun` 主体内创建的查询 Lambda | 把辅助函数改为非 inline，或从调用方传入 Lambda 字面量 |
| `it.status in listOf(1, 2, 3)`（在 Lambda 内构建列表） | 在外部写 `val ids = listOf(1, 2, 3)`，然后写 `it.status in ids` |
| 使用 `-Xsam-conversions=class` 编译 | 保持默认的 `-Xsam-conversions=indy` |

### 捕获的 null 值

有些写法接受捕获的 `null` 值，有些则不接受。
如果你想要"也匹配 NULL"，请自己编写 null 检查。
所有 null 规则见 3. 条件（[Java](java/03-where.md) | [Kotlin](kotlin/03-where.md)）。

#### Java

| 写法 | `name` 为 `null` | 结果 |
|---|---|---|
| `o.getDescription() == name`（引用 `==`） | 允许 | `e0.description is not distinct from :p1`（null 安全） |
| `Objects.equals(o.getDescription(), name)` | 允许 | null 安全，与上面相同 |
| `o.getDescription().equals(name)` | 绑定时报错 | 在 Java 中这永远不会为 true。lambdaQL 不会把它转换为 `is null`。 |
| `name.equals(o.getDescription())` | 绑定时报错 | 在 Java 中这会抛出 `NullPointerException`。 |
| `o.getStatus() > minStatus`（`minStatus` 为 `null`） | 绑定时报错 | 与 null 进行大小比较在 Java 中没有意义。 |

"也匹配 NULL"：`o.getDescription() == null || o.getDescription().equals(name)`。

#### Kotlin

| 写法 | 捕获的值为 `null` | 结果 |
|---|---|---|
| `it.description == name` | 允许 | `e0.description is not distinct from :p1`（null 安全） |
| `minPrice == null \|\| it.price!! > minPrice` | 允许 | 你写了 null 检查，所以 lambdaQL 使用它。 |
| `it.price!! > minPrice!!`（`minPrice` 为 `null`） | 绑定时报错 | 与 null 进行大小比较没有意义。 |
| `it.id in ids`（`ids` 中有 `null` 元素） | 绑定时报错 | 带有 null 元素的 `IN` 在 SQL 中是一个陷阱。 |

<a id="placeholders-called-outside-a-lambda"></a>
### 在 Lambda 外部调用的占位方法

有些方法只是为了在 Lambda 内部被读取而存在。它们的返回值是占位值。

| 方法 | 在查询 Lambda 外部调用时 |
|---|---|
| `Agg.count()`、`Agg.sum(...)` 以及其他 `Agg` 方法 | 抛出 `IllegalStateException`（`LQL-4032`） |
| 你的 `@SqlFunction` 方法 | lambdaQL 从不执行其方法体。方法体只能抛出 `DbOnlyException`（`LQL-4034`）。计算值的方法体会被拒绝（`LQL-3052`）。 |

#### Java

**(已测试)** [`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java)，region `rules`（开头几行）：

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

## 消息语言

lambdaQL 提供**四种语言**的消息：

| 语言 | Locale |
|---|---|
| 英文 | 默认（root） |
| 日文 | `ja` |
| 中文（简体） | `zh` |
| 韩文 | `ko` |

### lambdaQL 如何选择语言

lambdaQL 按以下顺序检查这三个位置。第一个已设置的生效。

| 顺序 | 设置 | 示例 |
|---|---|---|
| 1 | 代码中的 `LqlMessages.setLocale(...)` | 见下面的代码 |
| 2 | 系统属性 `lambdaql.locale` | `java -Dlambdaql.locale=ja -jar app.jar` |
| 3 | JVM 默认 locale | `Locale.getDefault()` |

然后 lambdaQL 只看该 locale 的**语言**部分：

| Locale 的语言 | 消息语言 |
|---|---|
| `ja`（例如 `ja_JP`） | 日文 |
| `zh`（`zh_CN`，以及 `zh_TW`） | 中文（简体） |
| `ko`（例如 `ko_KR`） | 韩文 |
| 其他任何语言（`en`、`fr`、`de` 等） | 英文 |

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

`LqlMessages.LOCALE_PROPERTY` 是名称 `"lambdaql.locale"`。`LqlMessages.bundleLocale(locale)` 告诉你某个 locale 使用哪个消息文件。

在你的应用中（设计示例）：

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

- lambdaQL 自己选择语言，不使用常规的 `ResourceBundle` 回退机制。因此，当你要求英文时，韩文 JVM 不会显示韩文消息。
- 消息在异常创建时就已确定。如果之后需要其他语言，请调用 `e.getMessage(locale)`。
- 服务器可能以非英文的 JVM locale 运行。如果你想要英文日志，请调用 `LqlMessages.setLocale(Locale.ROOT)` 或使用 `-Dlambdaql.locale=en`。
- 如果用户有需要，可以添加繁体中文消息。
- 日文和中文文本最初由作者编写，而不是由母语人士编写。欢迎帮助改进。

<a id="for-translators"></a>
### 致译者

消息文件是 jar 中的 UTF-8 properties 文件：

```
org/lambdaql/i18n/LqlMessages.properties      English (root)
org/lambdaql/i18n/LqlMessages_ja.properties   Japanese
org/lambdaql/i18n/LqlMessages_zh.properties   Chinese (Simplified)
org/lambdaql/i18n/LqlMessages_ko.properties   Korean
```

- 键是 `LqlMessage` 值的名称，例如 `LOOP_NOT_SUPPORTED`。
- 参数写作 `{0}`、`{1}` 等。
- lambdaQL 不使用 `MessageFormat`。因此单引号（`can't`）可以按原样书写。数字不会被格式化（行号 `1234567` 仍然是 `1234567`）。
- 每个键都必须存在于全部四个文件中，并且参数相同。有一个单元测试会检查这一点。新消息必须以全部四种语言添加。

**(已测试)** [`LqlMessagesTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/i18n/LqlMessagesTest.java)，region `fill`：

```java
// no MessageFormat: quotes and big numbers stay as they are (rule I4)
assertEquals("can't read line 1234567", LqlMessages.fill("can't read line {0}", 1234567));
assertEquals("a null b", LqlMessages.fill("a {0} b", (Object) null));
assertEquals("{x} {9}", LqlMessages.fill("{x} {9}", "unused"));
```

## 日志

lambdaQL 通过 **SLF4J API** 记录日志。

- lambdaQL 只依赖 `slf4j-api`。日志后端由你选择（Logback、Log4j 2 等）。
- Spring Boot 已经包含 SLF4J 和 Logback，因此你不需要添加任何东西。
- 警告使用 `WARN` 级别，并以 `LQL-5xxx` 范围内的错误码开头。
- 如果 `WARN` 级别关闭，lambdaQL 不会构建消息文本。

lambdaQL 目前提供的警告：

| 错误码 | 何时 |
|---|---|
| `LQL-5002` | 子查询有 `orderBy` 但没有 `limit`，因此 lambdaQL 会移除排序（它不起作用，规则 Q4） |
| `LQL-5003` | 外部排序（Spring `Sort`、`SortSpec.ofPath`）使用了不在排序规则中的名称，并且模式是 `SortMode.WARN`（默认值）。排序会执行。按实体和名称各记录一次（见 [4.10 排序策略](java/04-order-paging.md#sort-policy)） |

```
WARN  [LQL-5002] 没有 limit 的子查询排序不影响结果，已被移除（Q4）：...
```

计划中的警告（尚未实现）：

- `LQL-5001`：在条件的列一侧使用了数据库函数（Java `OrderFns.discount(o.getPrice()) > 10`，Kotlin `OrderFns.discount(it.price)!! > 10`），导致数据库无法使用索引；
- 不区分大小写的比较在该列上没有匹配的索引或排序规则（collation）。

计划提供一种把这些警告变为错误的"严格模式"。对于外部排序已经有了：在 `SortMode.STRICT` 下，`LQL-5003` 的情况会抛出 `SortNotAllowedException`（`LQL-4040`）。

> [!NOTE]
> `lambdaql-core` 不依赖 Spring。在日志方面它只带入 `slf4j-api`，因此日志后端由你选择。Spring 模块（`lambdaql-spring-data-jpa`、`lambdaql-spring-boot-starter`）会带入 Spring Data JPA，starter 还会带入 `spring-boot-starter-data-jpa` 以及 Logback。

## 总结

| 问题 | 回答 |
|---|---|
| 如何识别 lambdaQL 错误？ | 它是一个 `LambdaQLException`，消息以 `[LQL-xxxx]` 开头。 |
| 如何在代码或测试中检查错误？ | 使用 `e.code()`。不要比较文本。 |
| 如何在非英文服务器上获得英文文本？ | `LqlMessages.setLocale(Locale.ROOT)`、`-Dlambdaql.locale=en` 或 `e.getMessage(Locale.ROOT)`。 |
| 警告输出到哪里？ | 输出到 SLF4J，级别为 `WARN`。 |

[手册首页](README.md) | [Java 路线](java/README.md) | [Kotlin 路线](kotlin/README.md)
