<a id="11-errors-and-languages"></a>
# 11. Errors and languages

[Manual home](README.md) | Tracks: [Java](java/README.md) | [Kotlin](kotlin/README.md) | Previous: 10. Rules and limits ([Java](java/10-rules-and-limits.md) | [Kotlin](kotlin/10-rules-and-limits.md)) | Next: 12. Spring Data JPA ([Java](java/12-spring-data-jpa.md) | [Kotlin](kotlin/12-spring-data-jpa.md))

This page is for both languages. Code is shown in separate **Java** and **Kotlin** sections.

<a id="error-codes"></a>
## Error codes

Every lambdaQL message starts with a code in the form `[LQL-xxxx]`.
The code does not depend on the language. Only the text after the code is translated.

**(tested)** [`LqlMessagesTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/i18n/LqlMessagesTest.java), region `format`:

```java
LqlMessages.setLocale(Locale.ENGLISH);
assertEquals("[LQL-4014] limit must be 0 or more: -1", LqlMessage.LIMIT_NEGATIVE.format(-1));
// the same message in another language: the code stays
assertEquals("[LQL-4014] limit은 0 이상이어야 합니다: -1", LqlMessage.LIMIT_NEGATIVE.format(Locale.KOREAN, -1));
```

All messages are the values of the enum `org.lambdaql.i18n.LqlMessage`. Each value has a code and a number of arguments.

| Range | Area | Codes today | Exception |
|---|---|---|---|
| `LQL-1xxx` | Input and lambda loading | `LQL-1001` to `LQL-1013` | `LambdaAnalysisException` |
| `LQL-2xxx` | Decoding the bytecode | `LQL-2001` to `LQL-2025` | `UnsupportedLambdaShapeException` |
| `LQL-3xxx` | Meaning and values computed in Java | `LQL-3001` to `LQL-3067` | `TranslationException` (`LQL-3066` uses the subclass `NonSargableException`) |
| `LQL-4xxx` | Rendering, binding and running | `LQL-4001` to `LQL-4045`, `LQL-4999` (internal error) | `LambdaQLException` itself, or a JDK/JPA standard exception (see [Exceptions](#exceptions)). `LQL-4045` is a `WARN` log (`DEBUG` for a comparison that has only an unboxing to its own primitive type) |
| `LQL-5xxx` | Warnings (in the log) | `LQL-5001` to `LQL-5013` | – (a log line. The level is `WARN`; `INFO` for `LQL-5004` to `LQL-5006`, `LQL-5008` and for `LQL-5003` in the default sort mode; `DEBUG` for `LQL-5009` to `LQL-5011` and `LQL-5013`) |

Some real codes:

| Code | Exception | English text |
|---|---|---|
| `LQL-1002` | `LambdaAnalysisException` | Only Serializable lambdas can be analyzed. Use a lambdaQL lambda type, not java.util.function: {0} |
| `LQL-2004` | `UnsupportedLambdaShapeException` | Loops are not supported in a lambda. Build the list outside the lambda and capture it. |
| `LQL-3005` | `TranslationException` | Aggregate functions cannot be used in where. Use having (J3): Agg.{0} |
| `LQL-3035` | `TranslationException` | A value used with aggregates must be a groupBy key (J3): {0} |
| `LQL-3058` | `TranslationException` | The DB function {0} has its own SQL name, so it must be registered. Add its class to the setting lambdaql.functions.classes. |
| `LQL-3064` | `TranslationException` | The row type of a derived table or CTE must be a Java record or a Kotlin data class (J5): {0} |
| `LQL-3065` | `TranslationException` | Dml.value: the value type {2} does not fit the column {0} ({1}) (U20) |
| `LQL-3066` | `NonSargableException` | The condition cannot use an index, and the SARGable mode is STRICT: {0} |
| `LQL-3067` | `TranslationException` | A row of a derived table or CTE has no id, so it cannot be compared with null. Compare one of its columns, for example x.customerId() == null: {0} |
| `LQL-4009` | `LambdaQLException` | A null value cannot be compared with <, <=, > or >= (Java would throw NullPointerException): {0} |
| `LQL-4014` | `IllegalArgumentException` | limit must be 0 or more: {0} |
| `LQL-4025` | `NonUniqueResultException` | Two or more rows match one(). Narrow the condition or use first(): {0} |
| `LQL-4034` | `DbOnlyException` | {0} is a database function. It works only inside a lambdaQL lambda and is never run in Java. |
| `LQL-4036` | `LambdaQLException` | The stub does not match the database procedure {0}: {1} |
| `LQL-4040` | `SortNotAllowedException` | Sorting by this name is not allowed for {0}: {1}. Allow it with SortRules (allow or alias). |
| `LQL-4043` | `LambdaQLException` | The stub arguments of {0} have the database parameter names in another order. Stub: {1}, database: {2} |
| `LQL-4044` | `LambdaQLException` | The Oracle package has several procedures with this name (overloads); lambdaQL cannot tell which one to call: {0} |
| `LQL-4045` | – (`WARN` log when the query runs; `DEBUG` for a comparison through an unboxing to its own primitive type) | A captured value is null in {0}. In SQL, = NULL and <> NULL match no row; use x == null in the lambda, or check the value before the query. |
| `LQL-5001` | – (`WARN` log) | The function {0} is applied to the column {1}, so a normal index on the column cannot be used and the query may scan the whole table. Compare the column with a value instead, or (Oracle) create a function-based index: {2} |
| `LQL-5002` | – (`WARN` log) | A subquery sort without limit does not change the result, so it was removed (Q4): {0} |
| `LQL-5003` | – (`INFO` or `WARN` log) | An outside sort is not in the sort rules of {0}: {1}. It runs now; with SortMode.STRICT it would be rejected. |
| `LQL-5004` | – (`INFO` log) | The stub argument names are not in the class file, so the argument order of {0} is not checked. Compile with -parameters (Kotlin: -java-parameters) or use @DbParam. |
| `LQL-5005` | – (`INFO` log) | The parameters of {0} could not be read from the database metadata ({1}). The stub is not checked; IN and INOUT parameters are taken in stub order, then the OUT parameters. |
| `LQL-5006` | – (`INFO` log) | The overloads of {0} could not be checked ({1}); the call goes on without the check. |
| `LQL-5007` | – (`WARN` log) | The MariaDB function {0} is NOT DETERMINISTIC, so it is computed for every row and an index on the compared column is not used for a range. Declare it DETERMINISTIC if it is: {1} |
| `LQL-5008` | – (`INFO` log) | The index or function metadata for {0} could not be read ({1}); the SARGable check goes on without it. |
| `LQL-5009` | – (`DEBUG` log) | SQL meaning: in {0}, a row whose value is NULL does not match (Java would treat null differently). Add a null check such as `x == null \|\| ...` to include it. |
| `LQL-5010` | – (`DEBUG` log) | The column {0} is a @Lob column; some databases (for example Oracle) cannot use a LOB in {1}. |
| `LQL-5011` | – (`DEBUG` log) | The enum column {0} is stored as text (@Enumerated(STRING)), so the database sorts it by name, not by the declaration order of the enum. |
| `LQL-5012` | – (`WARN` log) | Arithmetic on the column {0} keeps the condition from using an index. Move it to the value side with the same meaning: divide by a decimal (o.getQty() > n / 2.0 for o.getQty() * 2 > n; n / 2 would be integer division), and reverse the comparison for a negative factor: {1} |
| `LQL-5013` | – (`DEBUG` log) | The condition computes on the columns {0}, so it cannot use an index on them: {1} |

`{0}` and `{1}` are the arguments, for example the expression or the procedure name.
The full list is in the message files (see [For translators](#for-translators)).

Rules for codes:

- A code is never reused after a release. You can safely search for a code or check it in code.
- A unit test checks that every code is unique and has the form `LQL-` + four digits.

<a id="exceptions"></a>
## Exceptions

All lambdaQL exceptions extend `org.lambdaql.LambdaQLException`. It is a `RuntimeException`, so Kotlin and Java handle it the same way.
The exception class comes from the code range.

| Exception | Codes | When | Example |
|---|---|---|---|
| `LambdaQLException` | `LQL-4xxx` | Base class of all lambdaQL exceptions. lambdaQL throws this class itself for rendering, binding and running problems | A procedure stub that does not match the database (`LQL-4036`). Stub arguments with the database names in another order (`LQL-4043`, see `@DbParam` in [8.4](java/08-procedures-functions.md#84-warning-argument-order)). |
| `LambdaAnalysisException` | `LQL-1xxx` | lambdaQL cannot load the lambda | An anonymous class (Kotlin `object : ...`) instead of a lambda. A non-serializable lambda (`LQL-1002`). |
| `UnsupportedLambdaShapeException` | `LQL-2xxx` | The bytecode has a shape that lambdaQL does not support | A loop (`LQL-2004`). A value ternary or value `if`/`when`. A general boolean ternary `c ? x : y`. |
| `TranslationException` | `LQL-3xxx` | The lambda has no defined meaning in HQL | An entity method that is not a getter. Comparing two entities with `==` (not supported yet). An order comparison of value objects (`LQL-3012`). A non-aggregated value that is not a `groupBy` key (`LQL-3035`). |
| `DbOnlyException` | `LQL-4034` | Your `@SqlFunction` method was called in Java, outside a lambdaQL lambda | `OrderFns.discount(1.0)` (see [Placeholders called outside a lambda](#placeholders-called-outside-a-lambda)) |
| `NonSargableException` | `LQL-3066` | In `SargableMode.STRICT`, lambdaQL analyzes a condition that cannot use an index (the cases of `LQL-5001`, `LQL-5007` and `LQL-5012`). A subclass of `TranslationException` | `OrderFns.discount(o.getTotal()) > 100`, `o.getCnt() * 2 > n` (decision D50) |
| `SortNotAllowedException` | `LQL-4040` | In `SortMode.STRICT`, an outside sort (a Spring `Sort`, `SortSpec.ofPath`) uses a name that the sort rules do not allow | `Sort.by("grade")` when only `name` is allowed (see [4.10 the sort policy](java/04-order-paging.md#sort-policy)). A web application usually answers it with HTTP 400 ([12.5](java/12-spring-data-jpa.md#sort-policy-with-spring)) |

All these classes are in the package `org.lambdaql`.

**Standard exceptions keep their type.** When Java code expects a JDK or JPA exception, lambdaQL throws that type, and only the message has a code.

| Exception | Example | Code |
|---|---|---|
| `IllegalArgumentException` | `limit(-1)` | `LQL-4014` |
| `IllegalStateException` | A NULL value for a primitive constructor argument (rule J4). `Agg.sum(...)` called outside a lambda. `toSet()` that gets fewer elements than rows | `LQL-4021`, `LQL-4032`, `LQL-4019` |
| `jakarta.persistence.NonUniqueResultException` | `one()` finds two or more rows. `executeOne()` matches two or more rows | `LQL-4025`, `LQL-4023` |
| `jakarta.persistence.TransactionRequiredException` | An update, insert or delete without a transaction | `LQL-4024` |

Methods of `LambdaQLException`:

| Method | Returns |
|---|---|
| `code()` | The stable code, for example `"LQL-2004"` |
| `key()` | The message key (an `LqlMessage` value) |
| `args()` | The message arguments |
| `where()` | The place of the lambda (the method that holds the lambda body, and the line when it is known), or `null` |
| `getMessage()` | The message in the lambdaQL language that was selected when the exception was created |
| `getMessage(Locale locale)` | The same message in another language, for example `Locale.ROOT` for English |

**(tested)** [`LqlMessagesTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/i18n/LqlMessagesTest.java), region `exception`:

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

The test makes the exception directly. In your code, lambdaQL makes it when it reads a lambda.
You handle it like this (design examples).

### Java

```java
try {
    List<Order> orders = qb.from(Order.class)
        .where(o -> o.getStatus() == (flag ? 1 : 2))   // value ternary: not supported
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
        .where { it.status == if (flag) 1 else 2 }      // value if: not supported
        .toList()
} catch (e: LambdaQLException) {
    log.error("lambdaQL error {}: {}", e.code(), e.getMessage(Locale.ROOT))
    throw e
}
```

<a id="what-a-message-has"></a>
### What a message has

- The code, then the text in the selected language.
- At the end, the place of the lambda in a fixed format: ` — ` and then the method that holds the lambda body and the **source line number** (also available from `where()`).
- For decode errors, extra lines with a bytecode dump can follow. These extra lines are not translated.
- Most messages also tell you how to fix the problem. For example, a Kotlin message says to build a list outside the lambda and capture it.

<a id="in-tests-check-the-code"></a>
### In tests, check the code

The message text can change, and it depends on the language. The code does not change.

#### Java

```java
var e = assertThrows(LambdaQLException.class, () -> query.toList());
assertEquals("LQL-2004", e.code());     // good
// assertEquals("Loops are ...", e.getMessage());   // do not do this
```

#### Kotlin

```kotlin
val e = assertThrows<LambdaQLException> { query.toList() }   // org.junit.jupiter.api.assertThrows
assertEquals("LQL-2004", e.code())      // good
// assertEquals("Loops are ...", e.message)          // do not do this
```

<a id="when-errors-happen"></a>
## When errors happen

lambdaQL finds problems at three moments.

| When | What is checked | Examples |
|---|---|---|
| **Compile time** | The Java or Kotlin compiler checks the types of the lambdaQL API. | An entity used as a sort key. `orderBy` called twice. `where` after `orderBy`. `execute()` on an update or delete without `where(...)` or `all()`. |
| **Analysis time** | lambdaQL reads the lambda bytecode, before any SQL goes to the database. | Loops, value ternaries, unsupported method calls, a sort lambda that is not a plain `by` chain, a `select` value that is not a `groupBy` key. |
| **Run time (binding)** | lambdaQL binds the captured values each time the query runs. | A `null` captured value where the rule does not allow null (see below). A `NaN` or `Infinity` parameter. A `null` element in a captured collection used for `IN`. |

Analysis-time errors do not depend on your data. The same lambda always fails the same way.
lambdaQL also caches failed results, so a bad lambda fails fast the next time.

<a id="compile-time-examples"></a>
### Compile-time examples

#### Java

```java
.orderBy((o, by) -> by.desc(o.getCustomer()))   // compile error: an entity is not a sort key
```

#### Kotlin

```kotlin
.orderBy { o, by -> by.desc(o.customer) }        // compile error: an entity is not a sort key
```

<a id="analysis-time-errors-that-only-kotlin-has"></a>
### Analysis-time errors that only Kotlin has

Some Kotlin forms do not give lambdaQL a lambda that it can read. lambdaQL rejects them with a clear error.
The full list is in [9. Kotlin notes](kotlin/09-kotlin-notes.md).

| Kotlin form | What to write instead |
|---|---|
| `val f: (Order) -> Boolean = { ... }; where(f)` | `where { ... }` with the lambda literal |
| A query lambda created inside an `inline fun` body | Make the helper non-inline, or pass the lambda literal from the caller |
| `it.status in listOf(1, 2, 3)` (list built inside the lambda) | `val ids = listOf(1, 2, 3)` outside, then `it.status in ids` |
| Compiling with `-Xsam-conversions=class` | Keep the default `-Xsam-conversions=indy` |

<a id="null-captured-values"></a>
### Null captured values

Some forms accept a `null` captured value, some do not.
A captured `null` in `=` or `<>` matches no row (the SQL meaning, decision D48). In this case lambdaQL logs `LQL-4045` at `WARN` level when the query runs. When the comparison goes through an unboxing to its own primitive type (`min.doubleValue()`), as in an order comparison, the level is `DEBUG`.
To match NULL, write the null check yourself.
All null rules are in 3. Conditions ([Java](java/03-where.md) | [Kotlin](kotlin/03-where.md)).

#### Java

| Form | `name` is `null` | Result |
|---|---|---|
| `o.getDescription() == name` (reference `==`) | runs | `e0.description = :p1` with NULL: no row matches. `LQL-4045` at `WARN` level |
| `Objects.equals(o.getDescription(), name)` | runs | the same as above |
| `o.getDescription().equals(name)` | runs | the same as above |
| `name.equals(o.getDescription())` | runs | the same as above |
| `o.getStatus() > minStatus` (`minStatus` is `null`) | runs | `e0.status > :p1` with NULL: no row matches. The unboxing to its own primitive type (`minStatus.intValue()`) is not run. `LQL-4045` at `DEBUG` level |
| A method call on a captured value, such as `name.trim()` or `minStatus.longValue()` (an `Integer` to another primitive type) | error (`LQL-4012`) | This step prepares the value in Java before the query, so it is a `NullPointerException` as in Java. |

To find NULL, write `o.getDescription() == null`.
To skip the filter when `name` is null, write `name == null || o.getDescription().equals(name)` (the guard is decided before the query runs).

#### Kotlin

| Form | Captured value is `null` | Result |
|---|---|---|
| `it.description == name` | runs | `e0.description = :p1` with NULL: no row matches. `LQL-4045` at `WARN` level |
| `minPrice == null \|\| it.price!! > minPrice` | allowed | You wrote the null check, so lambdaQL uses it. |
| `it.price!! > minPrice!!` (`minPrice` is `null`) | runs | `e0.price > :p1` with NULL: no row matches. `!!` and the unboxing are not run. `LQL-4045` at `DEBUG` level |
| `it.id in ids` (`ids` has a `null` element) | error at binding | `IN` with a null element is a trap in SQL. |

<a id="placeholders-called-outside-a-lambda"></a>
### Placeholders called outside a lambda

Some methods exist only to be read inside a lambda. Their return values are placeholders.

| Method | Called outside a query lambda |
|---|---|
| `Agg.count()`, `Agg.sum(...)`, and other `Agg` methods | throws `IllegalStateException` (`LQL-4032`) |
| Your `@SqlFunction` methods | The body is never run by lambdaQL. It must only throw `DbOnlyException` (`LQL-4034`). A body that computes a value is rejected (`LQL-3052`). |

#### Java

**(tested)** [`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java), region `rules` (the first lines):

```java
// the body never runs in Java
assertEquals("LQL-4034", assertThrows(DbOnlyException.class, () -> OrderFns.discount(1.0)).code());
```

The declaration of `OrderFns` is in 8. Procedures and DB functions ([Java](java/08-procedures-functions.md#86-declare-db-functions) | [Kotlin](kotlin/08-procedures-functions.md#86-declare-db-functions)).

#### Kotlin

```kotlin
interface OrderFns { companion object {
    @JvmStatic @SqlFunction("FN_DISCOUNT") fun discount(price: Double?): Double? = throw DbOnlyException()
} }
```

<a id="message-language"></a>
## Message language

lambdaQL has messages in **four languages**.

| Language | Locale |
|---|---|
| English | default (root) |
| Japanese | `ja` |
| Chinese (Simplified) | `zh` |
| Korean | `ko` |

<a id="how-lambdaql-chooses-the-language"></a>
### How lambdaQL chooses the language

lambdaQL checks these three places, in this order. The first one that is set is used.

| Order | Setting | Example |
|---|---|---|
| 1 | `LqlMessages.setLocale(...)` in your code | see the code below |
| 2 | The system property `lambdaql.locale` | `java -Dlambdaql.locale=ja -jar app.jar` |
| 3 | The JVM default locale | `Locale.getDefault()` |

Then lambdaQL looks only at the **language** of that locale.

| Language of the locale | Messages in |
|---|---|
| `ja` (for example `ja_JP`) | Japanese |
| `zh` (`zh_CN` and also `zh_TW`) | Chinese (Simplified) |
| `ko` (for example `ko_KR`) | Korean |
| anything else (`en`, `fr`, `de`, ...) | English |

**(tested)** [`LqlMessagesTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/i18n/LqlMessagesTest.java), region `locale`:

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

`LqlMessages.LOCALE_PROPERTY` is the name `"lambdaql.locale"`. `LqlMessages.bundleLocale(locale)` tells which message file a locale uses.

In your application (design examples):

#### Java

```java
// at application start
LqlMessages.setLocale(Locale.JAPANESE);   // Japanese messages
LqlMessages.setLocale(null);              // back to: system property, then JVM default
Locale current = LqlMessages.locale();    // the locale lambdaQL uses now
```

#### Kotlin

```kotlin
// at application start
LqlMessages.setLocale(Locale.JAPANESE)    // Japanese messages
LqlMessages.setLocale(null)               // back to: system property, then JVM default
val current: Locale = LqlMessages.locale() // the locale lambdaQL uses now
```

Notes:

- lambdaQL selects the language itself. It does not use the normal `ResourceBundle` fallback. So a Korean JVM does not show Korean messages when you asked for English.
- The message is fixed when the exception is created. If you need another language later, call `e.getMessage(locale)`.
- A server may run with a non-English JVM locale. If you want English logs, call `LqlMessages.setLocale(Locale.ROOT)` or use `-Dlambdaql.locale=en`.
- Traditional Chinese messages can be added if users ask for them.
- The Japanese and Chinese texts were first written by the authors, not by native speakers. Help to improve them is welcome.

<a id="for-translators"></a>
### For translators

Message files are UTF-8 properties files in the jar.

```
org/lambdaql/i18n/LqlMessages.properties      English (root)
org/lambdaql/i18n/LqlMessages_ja.properties   Japanese
org/lambdaql/i18n/LqlMessages_zh.properties   Chinese (Simplified)
org/lambdaql/i18n/LqlMessages_ko.properties   Korean
```

- The keys are the names of the `LqlMessage` values, for example `LOOP_NOT_SUPPORTED`.
- Arguments are written `{0}`, `{1}`, ...
- lambdaQL does not use `MessageFormat`. So you can write a single quote (`can't`) as is. Numbers are not formatted (line `1234567` stays `1234567`).
- Every key must exist in all four files, with the same arguments. A unit test checks this. A new message must be added in all four languages.

**(tested)** [`LqlMessagesTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/i18n/LqlMessagesTest.java), region `fill`:

```java
// no MessageFormat: quotes and big numbers stay as they are (rule I4)
assertEquals("can't read line 1234567", LqlMessages.fill("can't read line {0}", 1234567));
assertEquals("a null b", LqlMessages.fill("a {0} b", (Object) null));
assertEquals("{x} {9}", LqlMessages.fill("{x} {9}", "unused"));
```

<a id="logging"></a>
## Logging

lambdaQL logs through the **SLF4J API**.

- lambdaQL depends only on `slf4j-api`. You choose the logging backend (Logback, Log4j 2, and others).
- Spring Boot already includes SLF4J and Logback, so you do not need to add anything.
- A warning message starts with a code. Most codes are in the `LQL-5xxx` range. `LQL-4045` is also only logged.
- The level (`WARN`, `INFO`, `DEBUG`) depends on the code. The table below lists the level of each code.
- If that level is off, lambdaQL does not build the message text.

The warnings that lambdaQL logs today:

| Code | When |
|---|---|
| `LQL-4045` | A captured value is `null` in `=` or `<>` (`==`, `!=`, `equals`, `Objects.equals`), so no row can match (the SQL meaning, decision D48). Known only when the query runs. Logged at `WARN` level. When the comparison goes through an unboxing to its own primitive type (`min.doubleValue()`), as in an order comparison, it is logged at `DEBUG` level. Write `x == null` to find NULL (see 3.6: [Java](java/03-where.md#36-null-rules) \| [Kotlin](kotlin/03-where.md#36-null-rules)) |
| `LQL-5001` | A DB function wraps a column in a condition (`OrderFns.discount(o.getPrice()) > 10`, a Boolean function on a column, a function inside `IN` or `IS NULL`), so a normal index cannot be used. Logged at `WARN` level, once per `where` lambda. Not logged on Oracle when a function-based index with the same expression exists (see [8.9](java/08-procedures-functions.md#89-warning-functions-on-columns-and-indexes-sargable)) |
| `LQL-5002` | A subquery has `orderBy` but no `limit`, so lambdaQL removes the sort (it has no effect on the result, rule Q4) |
| `LQL-5003` | An outside sort (a Spring `Sort`, `SortSpec.ofPath`) uses a name that is not in the sort rules, and the mode is `SortMode.INFO` (the default, logged at `INFO` level) or `SortMode.WARN` (logged at `WARN` level). The sort runs. Logged once for each entity and name (see [4.10 the sort policy](java/04-order-paging.md#sort-policy)) |
| `LQL-5004` | A procedure stub has no argument names in the class file (compiled without `-parameters`, Kotlin `-java-parameters`, and without `@DbParam`), and two neighbouring arguments have the same type. Logged at `INFO` level, once per procedure (see [8.4](java/08-procedures-functions.md#84-warning-argument-order)) |
| `LQL-5005` | The procedure metadata cannot be read (the lookup fails, for example without permission on the dictionary views or `information_schema`). The stub is not checked, and the parameter layout comes from the stub. Logged at `INFO` level, once per procedure (see [8.4](java/08-procedures-functions.md#when-the-metadata-cannot-be-read)) |
| `LQL-5006` | Only the Oracle overload lookup (`all_arguments`) fails. The call goes on without the overload check. Logged at `INFO` level |
| `LQL-5007` | MariaDB only: a `NOT DETERMINISTIC` DB function on the value side is compared with a column (`o.getPrice() > OrderFns.nondet(base)`), so the index is not used for a range. Logged at `WARN` level |
| `LQL-5008` | The index or function metadata for the SARGable check cannot be read (for example without permission). Logged at `INFO` level. `LQL-5001` is still logged; `LQL-5007` is not checked |
| `LQL-5009` | A condition compares values of the entity with the SQL meaning (decision D48), so a NULL row that Java or Kotlin would match can be left out: two entity values with `==`/`equals`, `!=` against a value, `!` on a nullable `Boolean`, a null guard on an association path (the path stays an INNER JOIN), `NOT IN` with a subquery. Logged at `DEBUG` level, once per condition text (see 3.6.3: [Java](java/03-where.md#363-write-your-null-checks-yourself) \| [Kotlin](kotlin/03-where.md#363-write-your-null-checks-yourself)) |
| `LQL-5010` | A `@Lob` column is used in `order by`, `distinct` or `group by`. H2 and MariaDB run it, but Oracle rejects it (ORA-22848, ORA-22849). An `=` comparison works on all three databases. lambdaQL does not change the query. Logged at `DEBUG` level, once per column and use (see [4.5](java/04-order-paging.md#45-what-a-sort-key-can-be)) |
| `LQL-5011` | A query sorts by an enum column with `@Enumerated(EnumType.STRING)`. The database sorts by name, and Java and Kotlin sort by declaration order, so the order can differ. lambdaQL does not change the query. Logged at `DEBUG` level, once per column. Not logged for `ORDINAL` (see [4.5](java/04-order-paging.md#45-what-a-sort-key-can-be)) |
| `LQL-5012` | Arithmetic (`+ - * /`) with only one column and values is on the column side of a condition (`o.getCnt() * 2 > n`). It can be moved to the value side. Logged at `WARN` level. In `SargableMode.STRICT` it is the error `LQL-3066` (decision D50, see [8.9](java/08-procedures-functions.md#89-warning-functions-on-columns-and-indexes-sargable)) |
| `LQL-5013` | A condition has arithmetic between columns, or `%` (`o.getPrice() * o.getCnt() > n`). It cannot be moved, so it is logged only at `DEBUG` level. Allowed also in `STRICT` |

```
WARN  [LQL-5002] A subquery sort without limit does not change the result, so it was removed (Q4): ...
```

Planned warnings (not implemented yet):

- A case-insensitive comparison has no matching index or collation for that column.

There is no general strict mode that turns all warnings into errors at once.
Instead, two areas have their own `STRICT` mode.

- Sort policy (decision D43): with `SortMode.STRICT`, the case of `LQL-5003` throws `SortNotAllowedException` (`LQL-4040`).
- SARGable (decision D50): with `SargableMode.STRICT`, the cases of `LQL-5001`, `LQL-5007` and `LQL-5012` throw `NonSargableException` (`LQL-3066`).

> [!NOTE]
> `lambdaql-core` has no Spring dependency. For logging it brings only `slf4j-api`, so you choose the logging backend. The Spring modules (`lambdaql-spring-data-jpa`, `lambdaql-spring-boot-starter`) bring Spring Data JPA, and the starter also brings `spring-boot-starter-data-jpa` with Logback.

<a id="summary"></a>
## Summary

| Question | Answer |
|---|---|
| How do I recognize a lambdaQL error? | It is a `LambdaQLException`, and the message starts with `[LQL-xxxx]`. |
| How do I check an error in code or tests? | Use `e.code()`. Do not compare the text. |
| How do I get English text on a non-English server? | `LqlMessages.setLocale(Locale.ROOT)`, `-Dlambdaql.locale=en`, or `e.getMessage(Locale.ROOT)`. |
| Where do warnings go? | To SLF4J, level `WARN`. |

[Manual home](README.md) | [Java track](java/README.md) | [Kotlin track](kotlin/README.md)
