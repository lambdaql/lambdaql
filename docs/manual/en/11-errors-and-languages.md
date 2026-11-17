# 11. Errors and languages

[Manual home](README.md) | Tracks: [Java](java/README.md) | [Kotlin](kotlin/README.md) | Previous: 10. Rules and limits ([Java](java/10-rules-and-limits.md) | [Kotlin](kotlin/10-rules-and-limits.md)) | Next: 12. Spring Data JPA ([Java](java/12-spring-data-jpa.md) | [Kotlin](kotlin/12-spring-data-jpa.md))

> [!NOTE]
> **Status:** Implemented and tested (decision D33).
> Every message has a code `[LQL-xxxx]`, and the messages exist in English, Japanese, Chinese (Simplified) and Korean.
> The exceptions are `LambdaQLException` and its subclasses. Warnings go to SLF4J.
> Examples marked **(tested)** are copied from the test suite ([`LqlMessagesTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/i18n/LqlMessagesTest.java), [`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java)). The other code on this page is a design example.
> Not implemented yet: the non-SARGable warning `LQL-5001` and a strict mode that turns warnings into errors.

This page is for both languages. Code is shown in separate **Java** and **Kotlin** sections.

## Error codes

Every lambdaQL message starts with a code in the form `[LQL-xxxx]`.
The code does not depend on the language. The text after the code is translated.

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
| `LQL-3xxx` | Meaning and values computed in Java | `LQL-3001` to `LQL-3064` | `TranslationException` |
| `LQL-4xxx` | Rendering, binding and running | `LQL-4001` to `LQL-4042`, `LQL-4999` (internal error) | `LambdaQLException` itself, or a JDK/JPA standard exception (see [Exceptions](#exceptions)) |
| `LQL-5xxx` | Warnings (in the log) | `LQL-5002`, `LQL-5003` | – (a `WARN` log line) |

Some real codes:

| Code | Exception | English text |
|---|---|---|
| `LQL-1002` | `LambdaAnalysisException` | Only Serializable lambdas can be analyzed. Use a lambdaQL lambda type, not java.util.function: {0} |
| `LQL-2004` | `UnsupportedLambdaShapeException` | Loops are not supported in a lambda. Build the list outside the lambda and capture it. |
| `LQL-3005` | `TranslationException` | Aggregate functions cannot be used in where. Use having (J3): Agg.{0} |
| `LQL-3035` | `TranslationException` | A value used with aggregates must be a groupBy key (J3): {0} |
| `LQL-3058` | `TranslationException` | The DB function {0} has its own SQL name, so it must be registered. Add its class to the setting lambdaql.functions.classes. |
| `LQL-3064` | `TranslationException` | The row type of a derived table or CTE must be a Java record or a Kotlin data class (J5): {0} |
| `LQL-4009` | `LambdaQLException` | A null value cannot be compared with <, <=, > or >= (Java would throw NullPointerException): {0} |
| `LQL-4014` | `IllegalArgumentException` | limit must be 0 or more: {0} |
| `LQL-4025` | `NonUniqueResultException` | Two or more rows match one(). Narrow the condition or use first(): {0} |
| `LQL-4034` | `DbOnlyException` | {0} is a database function. It works only inside a lambdaQL lambda and is never run in Java. |
| `LQL-4036` | `LambdaQLException` | The stub does not match the database procedure {0}: {1} |
| `LQL-4040` | `SortNotAllowedException` | Sorting by this name is not allowed for {0}: {1}. Allow it with SortRules (allow or alias). |
| `LQL-5002` | – (`WARN` log) | A subquery sort without limit does not change the result, so it was removed (Q4): {0} |
| `LQL-5003` | – (`WARN` log) | An outside sort is not in the sort rules of {0}: {1}. It runs now; with SortMode.STRICT it would be rejected. |

`{0}` and `{1}` are the arguments, for example the expression or the procedure name.
The full list is in the message files (see [For translators](#for-translators)).

Rules for codes:

- A code is never reused after a release. You can safely search for a code or check it in code.
- A unit test checks that every code is unique and has the form `LQL-` + four digits.

## Exceptions

All lambdaQL exceptions extend `org.lambdaql.LambdaQLException`. It is a `RuntimeException`, so Kotlin and Java handle it the same way.
The exception class comes from the code range.

| Exception | Codes | When | Example |
|---|---|---|---|
| `LambdaQLException` | `LQL-4xxx` | Base class of all lambdaQL exceptions. lambdaQL throws this class itself for rendering, binding and running problems | A `null` captured value in an order compare (`LQL-4009`). A procedure stub that does not match the database (`LQL-4036`). |
| `LambdaAnalysisException` | `LQL-1xxx` | lambdaQL cannot load the lambda | An anonymous class (Kotlin `object : ...`) instead of a lambda. A non-serializable lambda (`LQL-1002`). |
| `UnsupportedLambdaShapeException` | `LQL-2xxx` | The bytecode has a shape that lambdaQL does not support | A loop (`LQL-2004`). A value ternary or value `if`/`when`. A general boolean ternary `c ? x : y`. |
| `TranslationException` | `LQL-3xxx` | The lambda has no defined meaning in HQL | An entity method that is not a getter. Comparing two entities with `==` (not supported yet). A non-aggregated value that is not a `groupBy` key (`LQL-3035`). |
| `DbOnlyException` | `LQL-4034` | Your `@SqlFunction` method was called in Java, outside a lambdaQL lambda | `OrderFns.discount(1.0)` (see [Placeholders called outside a lambda](#placeholders-called-outside-a-lambda)) |
| `SortNotAllowedException` | `LQL-4040` | In `SortMode.STRICT`, an outside sort (a Spring `Sort`, `SortSpec.ofPath`) uses a name that the sort rules do not allow | `Sort.by("grade")` when only `name` is allowed (see [4.10 the sort policy](java/04-order-paging.md#sort-policy)). A web application usually answers it with HTTP 400 ([12.5](java/12-spring-data-jpa.md#sort-policy-with-spring)) |

All these classes are in the package `org.lambdaql`.

**Standard exceptions keep their type.** In some cases Java code expects a JDK or JPA exception. Then lambdaQL throws that type, and only the message has a code:

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

The test makes the exception directly. In your code, lambdaQL makes it when it reads a lambda. Catch it like this (design examples):

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

### What a message has

- The code, then the text in the selected language.
- At the end, the place of the lambda in a fixed format: ` — ` and then the method that holds the lambda body and the **source line number** (also in `where()`).
- For decode errors, extra lines with a bytecode dump can follow. These extra lines are not translated.
- Most messages also tell you how to fix the problem. For example, a Kotlin message says to build a list outside the lambda and capture it.

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

## When errors happen

lambdaQL finds problems at three moments.

| When | What is checked | Examples |
|---|---|---|
| **Compile time** | The Java or Kotlin compiler checks the types of the lambdaQL API. | An entity used as a sort key. `orderBy` called twice. `where` after `orderBy`. `execute()` on an update or delete without `where(...)` or `all()`. |
| **Analysis time** | lambdaQL reads the lambda bytecode, before any SQL goes to the database. | Loops, value ternaries, unsupported method calls, a sort lambda that is not a plain `by` chain, a `select` value that is not a `groupBy` key. |
| **Run time (binding)** | lambdaQL binds the captured values each time the query runs. | A `null` captured value where the rule does not allow null (see below). A `NaN` or `Infinity` parameter. A `null` element in a captured collection used for `IN`. |

Analysis-time errors do not depend on your data. The same lambda always fails the same way.
lambdaQL also caches failed results, so a bad lambda fails fast the next time.

### Compile-time examples

#### Java

```java
.orderBy((o, by) -> by.desc(o.getCustomer()))   // compile error: an entity is not a sort key
```

#### Kotlin

```kotlin
.orderBy { o, by -> by.desc(o.customer) }        // compile error: an entity is not a sort key
```

### Analysis-time errors that only Kotlin has

Some Kotlin forms do not give lambdaQL a lambda that it can read. lambdaQL rejects them with a clear error.
The full list is in [9. Kotlin notes](kotlin/09-kotlin-notes.md).

| Kotlin form | What to write instead |
|---|---|
| `val f: (Order) -> Boolean = { ... }; where(f)` | `where { ... }` with the lambda literal |
| A query lambda created inside an `inline fun` body | Make the helper non-inline, or pass the lambda literal from the caller |
| `it.status in listOf(1, 2, 3)` (list built inside the lambda) | `val ids = listOf(1, 2, 3)` outside, then `it.status in ids` |
| Compiling with `-Xsam-conversions=class` | Keep the default `-Xsam-conversions=indy` |

### Null captured values

Some forms accept a `null` captured value, some do not.
If you want "match NULL too", write the null check yourself.
All null rules are in 3. Conditions ([Java](java/03-where.md) | [Kotlin](kotlin/03-where.md)).

#### Java

| Form | `name` is `null` | Result |
|---|---|---|
| `o.getDescription() == name` (reference `==`) | allowed | `e0.description is not distinct from :p1` (null-safe) |
| `Objects.equals(o.getDescription(), name)` | allowed | null-safe, the same as above |
| `o.getDescription().equals(name)` | error at binding | In Java this is never true. lambdaQL does not turn it into `is null`. |
| `name.equals(o.getDescription())` | error at binding | In Java this throws `NullPointerException`. |
| `o.getStatus() > minStatus` (`minStatus` is `null`) | error at binding | An order comparison with null has no Java meaning. |

"Match NULL too": `o.getDescription() == null || o.getDescription().equals(name)`.

#### Kotlin

| Form | Captured value is `null` | Result |
|---|---|---|
| `it.description == name` | allowed | `e0.description is not distinct from :p1` (null-safe) |
| `minPrice == null \|\| it.price!! > minPrice` | allowed | You wrote the null check, so lambdaQL uses it. |
| `it.price!! > minPrice!!` (`minPrice` is `null`) | error at binding | An order comparison with null has no meaning. |
| `it.id in ids` (`ids` has a `null` element) | error at binding | `IN` with a null element is a trap in SQL. |

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

## Message language

lambdaQL has messages in **four languages**:

| Language | Locale |
|---|---|
| English | default (root) |
| Japanese | `ja` |
| Chinese (Simplified) | `zh` |
| Korean | `ko` |

### How lambdaQL chooses the language

lambdaQL checks these three places, in this order. The first one that is set wins.

| Order | Setting | Example |
|---|---|---|
| 1 | `LqlMessages.setLocale(...)` in your code | see the code below |
| 2 | The system property `lambdaql.locale` | `java -Dlambdaql.locale=ja -jar app.jar` |
| 3 | The JVM default locale | `Locale.getDefault()` |

Then lambdaQL looks only at the **language** of that locale:

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

### For translators

Message files are UTF-8 properties files in the jar:

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

## Logging

lambdaQL logs through the **SLF4J API**.

- lambdaQL depends only on `slf4j-api`. You choose the logging backend (Logback, Log4j 2, and others).
- Spring Boot already includes SLF4J and Logback, so you do not need to add anything.
- Warnings use the `WARN` level and start with a code from the `LQL-5xxx` range.
- If the `WARN` level is off, lambdaQL does not build the message text.

The warnings that lambdaQL has today:

| Code | When |
|---|---|
| `LQL-5002` | A subquery has `orderBy` but no `limit`, so lambdaQL removes the sort (it has no effect, rule Q4) |
| `LQL-5003` | An outside sort (a Spring `Sort`, `SortSpec.ofPath`) uses a name that is not in the sort rules, and the mode is `SortMode.WARN` (the default). The sort runs. The warning is logged once for each entity and name (see [4.10 the sort policy](java/04-order-paging.md#sort-policy)) |

```
WARN  [LQL-5002] A subquery sort without limit does not change the result, so it was removed (Q4): ...
```

Planned warnings (not implemented yet):

- `LQL-5001`: a database function is used on the column side of a condition (Java `OrderFns.discount(o.getPrice()) > 10`, Kotlin `OrderFns.discount(it.price)!! > 10`), so the database cannot use an index;
- a case-insensitive comparison has no matching index or collation for that column.

A "strict mode" that turns these warnings into errors is planned. For outside sorts there is one already: with `SortMode.STRICT`, the case of `LQL-5003` throws `SortNotAllowedException` (`LQL-4040`).

> [!NOTE]
> `lambdaql-core` has no Spring dependency. For logging it brings only `slf4j-api`, so you choose the logging backend. The Spring modules (`lambdaql-spring-data-jpa`, `lambdaql-spring-boot-starter`) bring Spring Data JPA, and the starter also brings `spring-boot-starter-data-jpa` with Logback.

## Summary

| Question | Answer |
|---|---|
| How do I recognize a lambdaQL error? | It is a `LambdaQLException`, and the message starts with `[LQL-xxxx]`. |
| How do I check an error in code or tests? | Use `e.code()`. Do not compare the text. |
| How do I get English text on a non-English server? | `LqlMessages.setLocale(Locale.ROOT)`, `-Dlambdaql.locale=en`, or `e.getMessage(Locale.ROOT)`. |
| Where do warnings go? | To SLF4J, level `WARN`. |

[Manual home](README.md) | [Java track](java/README.md) | [Kotlin track](kotlin/README.md)
