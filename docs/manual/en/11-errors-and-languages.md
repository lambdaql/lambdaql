<a id="11-errors-and-languages"></a>
# 11. Errors and languages

[Manual home](README.md) | Tracks: [Java](java/README.md) | [Kotlin](kotlin/README.md) | Previous: 10. Rules and limits ([Java](java/10-rules-and-limits.md) | [Kotlin](kotlin/10-rules-and-limits.md)) | Next: 12. Spring Data JPA ([Java](java/12-spring-data-jpa.md) | [Kotlin](kotlin/12-spring-data-jpa.md))

This page is for both languages. It shows code in separate **Java** and **Kotlin** sections.

<a id="error-codes"></a>
## Error codes

Every lambdaQL message starts with a code in the form `[LQL-xxxx]`.
The code does not change with the language. Only the text after the code is translated.

<!-- region: LqlMessagesTest.java#format -->
```java
LqlMessages.setLocale(Locale.ENGLISH);
assertEquals("[LQL-4014] limit must be 0 or more: -1", LqlMessage.LIMIT_NEGATIVE.format(-1));
// the same message in another language: the code stays
assertEquals("[LQL-4014] limit은 0 이상이어야 합니다: -1", LqlMessage.LIMIT_NEGATIVE.format(Locale.KOREAN, -1));
```

Every message is a value of the enum `org.lambdaql.i18n.LqlMessage`. Each value has a code and a number of arguments.

| Range | Area | Current codes | Exception |
|---|---|---|---|
| `LQL-1xxx` | Input and lambda expression loading | `LQL-1001` to `LQL-1013` | `LambdaAnalysisException` |
| `LQL-2xxx` | Bytecode opcode decoding | `LQL-2001` to `LQL-2025` | `UnsupportedLambdaShapeException` |
| `LQL-3xxx` | Meaning and values computed in Java | `LQL-3001` to `LQL-3069` | `TranslationException` (`LQL-3066` is the subclass `NonSargableException`) |
| `LQL-4xxx` | Rendering, binding and running | `LQL-4001` to `LQL-4047`, `LQL-4999` (internal error) | `LambdaQLException` itself, or a JDK/JPA standard exception (see [Exceptions](#exceptions)). `LQL-4045` is a `WARN` log (`DEBUG` for a comparison that has only an unboxing to its own primitive type) |
| `LQL-5xxx` | Warnings (in the log) | `LQL-5001` to `LQL-5013` | – (a log line. The level is `WARN`; `INFO` for `LQL-5004` to `LQL-5006`, `LQL-5008` and for `LQL-5003` in the default sort mode; `DEBUG` for `LQL-5009` to `LQL-5011` and `LQL-5013`) |

Some real codes:

| Code | Exception | Message (English) |
|---|---|---|
| `LQL-1002` | `LambdaAnalysisException` | Only Serializable lambdas can be analyzed. Use a lambdaQL lambda type, not java.util.function: {0} |
| `LQL-2004` | `UnsupportedLambdaShapeException` | Loops are not supported in a lambda. Build the list outside the lambda and capture it. |
| `LQL-3005` | `TranslationException` | Aggregate functions cannot be used in where. Use having: Agg.{0} |
| `LQL-3035` | `TranslationException` | A value used with aggregates must be a groupBy key: {0} |
| `LQL-3058` | `TranslationException` | The DB function {0} has its own SQL name, so it must be registered. Add its class to the setting lambdaql.functions.classes. |
| `LQL-3064` | `TranslationException` | The row type of a derived table or CTE must be a Java record or a Kotlin data class: {0} |
| `LQL-3065` | `TranslationException` | Dml.value: the value type {2} does not fit the column {0} ({1}) |
| `LQL-3066` | `NonSargableException` | The condition cannot use an index, and the SARGable mode is STRICT: {0} |
| `LQL-3067` | `TranslationException` | A row of a derived table or CTE has no id, so it cannot be compared with null. Compare one of its columns, for example x.customerId() == null: {0} |
| `LQL-3068` | `TranslationException` | The @Version column cannot be assigned; Hibernate increases it in every update: {0} |
| `LQL-3069` | `TranslationException` | With a Direction argument, the sort lambda must be one key, not the functions of By: write orderBy(o -> sort(o.getPrice(), direction)) instead. |
| `LQL-4009` | `LambdaQLException` | A null value cannot be compared with <, <=, > or >= (Java would throw NullPointerException): {0} |
| `LQL-4014` | `IllegalArgumentException` | limit must be 0 or more: {0} |
| `LQL-4025` | `NonUniqueResultException` | Two or more rows match one(). Narrow the condition or use first(): {0} |
| `LQL-4034` | `DbOnlyException` | {0} is a database function. It works only inside a lambdaQL lambda and is never run in Java. |
| `LQL-4036` | `LambdaQLException` | The stub does not match the database procedure {0}: {1} |
| `LQL-4040` | `SortNotAllowedException` | Sorting by this name is not allowed for {0}: {1}. Allow it with SortRules (allow or alias). |
| `LQL-4043` | `LambdaQLException` | The stub arguments of {0} have the database parameter names in another order. Stub: {1}, database: {2} |
| `LQL-4044` | `LambdaQLException` | The Oracle package has several procedures with this name (overloads); lambdaQL cannot tell which one to call: {0} |
| `LQL-4045` | – (a `WARN` log at run time; `DEBUG` for a comparison that goes through an unboxing to its own primitive type) | A captured value is null in {0}. In SQL, = NULL and <> NULL match no row; use x == null in the lambda, or check the value before the query. |
| `LQL-4046` | `IllegalStateException` | The sort function {0} of By is only for sort lambdas; lambdaQL reads it and never runs it. |
| `LQL-4047` | `LambdaQLException` | The text for startsWith, endsWith or contains is null (Java would throw NullPointerException): {0} |
| `LQL-5001` | – (`WARN` log) | The function {0} is applied to the column {1}, so a normal index on the column cannot be used and the query may scan the whole table. Compare the column with a value instead, or (Oracle, PostgreSQL) create a function-based index on the same expression: {2} |
| `LQL-5002` | – (`WARN` log) | A subquery sort without limit does not change the result, so it was removed: {0} |
| `LQL-5003` | – (`INFO` or `WARN` log) | An outside sort is not in the sort rules of {0}: {1}. It runs now; with SortMode.STRICT it would be rejected. |
| `LQL-5004` | – (`INFO` log) | The stub argument names are not in the class file, so the argument order of {0} is not checked. Compile with -parameters (Kotlin: -java-parameters) or use @DbParam. |
| `LQL-5005` | – (`INFO` log) | The parameters of {0} could not be read from the database metadata ({1}). The stub is not checked; IN and INOUT parameters are taken in stub order, then the OUT parameters. |
| `LQL-5006` | – (`INFO` log) | The overloads of {0} could not be checked ({1}); the call goes on without the check. |
| `LQL-5007` | – (`WARN` log) | The function {0} is NOT DETERMINISTIC (MariaDB, MySQL) or VOLATILE (PostgreSQL), so it is computed for every row and an index on the compared column is not used. If its result depends only on its arguments, declare it DETERMINISTIC (MariaDB, MySQL) or STABLE or IMMUTABLE (PostgreSQL): {1} |
| `LQL-5008` | – (`INFO` log) | The index or function metadata for {0} could not be read ({1}); the SARGable check goes on without it. |
| `LQL-5009` | – (`DEBUG` log) | SQL meaning: in {0}, a row whose value is NULL does not match (Java would treat null differently). Add a null check such as `x == null \|\| ...` to include it. |
| `LQL-5010` | – (`DEBUG` log) | The column {0} is a @Lob column; some databases (for example Oracle) cannot use a LOB in {1}. |
| `LQL-5011` | – (`DEBUG` log) | The enum column {0} is stored as text (@Enumerated(STRING)), so the database sorts it by name, not by the declaration order of the enum. |
| `LQL-5012` | – (`WARN` log) | Arithmetic on the column {0} keeps the condition from using an index. Move it to the value side with the same meaning: divide by a decimal (o.getQty() > n / 2.0 for o.getQty() * 2 > n; n / 2 would be integer division), and reverse the comparison for a negative factor: {1} |
| `LQL-5013` | – (`DEBUG` log) | The condition computes on the columns {0}, so it cannot use an index on them: {1} |

`{0}` and `{1}` are arguments. For example, an expression or a procedure name goes there.
The full list is in the message files (see [For translators](#for-translators)).

Code rules:

- A code is never reused after a release. It is safe to search by code or to check the code value in your code.
- Every code is `LQL-` and four digits, and no code is used twice.

Example code: [`LqlMessagesTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/i18n/LqlMessagesTest.java) (`format`).

<a id="exceptions"></a>
## Exceptions

Every lambdaQL exception extends `org.lambdaql.LambdaQLException`. It is a `RuntimeException`, so you handle it the same way in Kotlin and Java.
The exception class depends on the code range.

| Exception | Codes | When | Examples |
|---|---|---|---|
| `LambdaQLException` | `LQL-4xxx` | The base class of every lambdaQL exception. This class itself is thrown for rendering, binding and running problems | A procedure stub that does not match the database (`LQL-4036`). Stub arguments in a different order from the database argument names (`LQL-4043`, see `@DbParam` in [8.4](java/08-procedures-functions.md#84-warning-argument-order)). |
| `LambdaAnalysisException` | `LQL-1xxx` | lambdaQL cannot load the lambda expression | An anonymous class (Kotlin `object : ...`) instead of a lambda expression. A lambda expression that cannot be serialized (`LQL-1002`). |
| `UnsupportedLambdaShapeException` | `LQL-2xxx` | The bytecode opcodes have a shape that lambdaQL does not support | A loop (`LQL-2004`). `switch` and Kotlin's `when (x)` with a subject (`LQL-2010`). A general boolean ternary operator `c ? x : y`. |
| `TranslationException` | `LQL-3xxx` | The lambda expression has no defined meaning in HQL | An entity method that is not a getter. Comparing two entities with `==` (not supported yet). Comparing the size of value objects (`LQL-3012`). A non-aggregate value that is not a `groupBy` key (`LQL-3035`). |
| `DbOnlyException` | `LQL-4034` | Your own `@SqlFunction` method is called in Java outside a lambdaQL lambda expression | `OrderFns.discount(1.0)` (see [Placeholders called outside a lambda expression](#placeholders-called-outside-a-lambda)) |
| `NonSargableException` | `LQL-3066` | In `SargableMode.STRICT`, lambdaQL analyzes a condition that cannot use an index (the cases of `LQL-5001`, `LQL-5007` and `LQL-5012`). A subclass of `TranslationException` | `OrderFns.discount(o.getTotal()) > 100`, `o.getCnt() * 2 > n` |
| `SortNotAllowedException` | `LQL-4040` | In `SortMode.STRICT`, an outside sort (Spring `Sort`, `SortSpec.ofPath`) uses a name that is not in the sort rules | `Sort.by("grade")` when only `name` is allowed (see [4.10 Sort policy](java/04-order-paging.md#sort-policy)). A web application usually answers with HTTP 400 ([12.5](java/12-spring-data-jpa.md#sort-policy-with-spring)) |

All these classes are in the package `org.lambdaql`.

**Standard exceptions keep their type.** When Java code expects a JDK or JPA exception, lambdaQL throws that type and adds the code only to the message.

| Exception | Example | Code |
|---|---|---|
| `IllegalArgumentException` | `limit(-1)` | `LQL-4014` |
| `IllegalStateException` | A NULL value for a primitive constructor argument. `Agg.sum(...)` called outside a lambda expression. A sort function of `By` (`desc(...)` and others) called outside a sort lambda expression. `toSet()` with fewer elements than rows | `LQL-4021`, `LQL-4032`, `LQL-4046`, `LQL-4019` |
| `jakarta.persistence.NonUniqueResultException` | `one()` returns two or more rows. Two or more rows match the condition of `executeOne()` | `LQL-4025`, `LQL-4023` |
| `jakarta.persistence.TransactionRequiredException` | update, insert or delete without a transaction | `LQL-4024` |

Methods of `LambdaQLException`:

| Method | Returns |
|---|---|
| `code()` | The fixed code, for example `"LQL-2004"` |
| `key()` | The message key (an `LqlMessage` value) |
| `args()` | The message arguments |
| `where()` | The place of the lambda expression (the method that holds the lambda expression body, and the line number if known), or `null` |
| `getMessage()` | The message in the lambdaQL language that was selected when the exception was created |
| `getMessage(Locale locale)` | The same message in another language, for example `Locale.ROOT` for English |

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

This example creates the exception directly. In real code, lambdaQL creates the exception when it reads the lambda expression.
The next example catches a condition that uses `switch` (in Kotlin, `when` with a subject) and turns the English message into a string for the log. This condition fails with `LQL-2010` (`UnsupportedLambdaShapeException`) when `where` is called.

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
### What a message has

- The code, then the text in the selected language.
- At the end, the place of the lambda expression follows in a fixed format. After ` — ` come the method that holds the lambda expression body and the **source line number** (you can also get it with `where()`).
- A decoding error can have more lines with a dump of the bytecode opcodes. These extra lines are not translated.
- Most messages also tell you how to fix the problem. For example, the Kotlin message tells you to build the list outside the lambda expression and capture it.

<a id="in-tests-check-the-code"></a>
### In tests, check the code

The message text can change, and it depends on the language. The code does not change.

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

Example code: [`LqlMessagesTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/i18n/LqlMessagesTest.java) (`exception`), [`ManualSharedTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualSharedTest.java) (`catch`, `check-code`), [`ManualKotlinErrorsTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinErrorsTest.kt) (`catch`, `check-code`).

<a id="when-errors-happen"></a>
## When errors happen

lambdaQL finds problems at two times.

| Time | What is checked | Examples |
|---|---|---|
| **Analysis time** | lambdaQL reads the bytecode opcodes of the lambda expression before the SQL goes to the database. | Loops, `switch`, unsupported method calls, a sort lambda expression that is not one key or a simple chain of `By` functions, a `Direction` argument used with sort functions (`LQL-3069`), a `select` value that is not a `groupBy` key. |
| **Run time (binding)** | lambdaQL binds the captured values each time the query runs. | A `null` captured value where the rules do not allow null (see below). A `NaN` or `Infinity` parameter. A `null` element in a captured collection used for `IN`. |

Errors at analysis time do not depend on the data. The same lambda expression always fails in the same way.
lambdaQL also caches failed results, so a wrong lambda expression fails fast the next time.

<a id="analysis-time-errors-that-only-kotlin-has"></a>
### Analysis-time errors that only Kotlin has

Some Kotlin forms do not create a lambda expression that lambdaQL can read. lambdaQL rejects them with a clear error.
The full list is in [9. Kotlin notes](kotlin/09-kotlin-notes.md).

| Kotlin form | Write this instead |
|---|---|
| `val f: (Order) -> Boolean = { ... }; where(f)` | A lambda expression literal: `where { ... }` |
| A query lambda expression created inside the body of an `inline fun` | Make the helper non-inline, or pass a lambda expression literal from the caller |
| `it.status in listOf(1, 2, 3)` (a list created inside the lambda expression) | `val ids = listOf(1, 2, 3)` outside, then `it.status in ids` |
| Compiling with `-Xsam-conversions=class` | Keep the default `-Xsam-conversions=indy` |

<a id="null-captured-values"></a>
### null captured values

Some forms accept a `null` captured value, and some do not.
A captured value `null` used in `=` or `<>` matches no row (SQL meaning). In this case lambdaQL logs `LQL-4045` at `WARN` level at run time. When the value goes through an unboxing to its own primitive type (`min.doubleValue()`), as in a size comparison, the level is `DEBUG`.
To match NULL, write the null check yourself.
The full null rules are in 3. Conditions ([Java](java/03-where.md) | [Kotlin](kotlin/03-where.md)).

#### Java

| Form | When `name` is `null` | Result |
|---|---|---|
| `o.getDescription() == name` (reference `==`) | Runs | `e0.description = :p1` with NULL bound: no row matches. `LQL-4045` at `WARN` level |
| `Objects.equals(o.getDescription(), name)` | Runs | Same as above |
| `o.getDescription().equals(name)` | Runs | Same as above |
| `name.equals(o.getDescription())` | Runs | Same as above |
| `o.getStatus() > minStatus` (`minStatus` is `null`) | Runs | `e0.status > :p1` with NULL bound: no row matches. The unboxing to its own primitive type (`minStatus.intValue()`) is not run. `LQL-4045` at `DEBUG` level |
| A method call on a captured value, such as `name.trim()` or `minStatus.longValue()` (an `Integer` to another primitive type) | Error (`LQL-4012`) | This step prepares the value in Java before the query, so it is like a `NullPointerException` in Java. |

To find NULL, write `o.getDescription() == null`.
To skip the filter when `name` is null, write `name == null || o.getDescription().equals(name)` (the guard is decided before the query runs).

#### Kotlin

| Form | When the captured value is `null` | Result |
|---|---|---|
| `it.description == name` | Runs | `e0.description = :p1` with NULL bound: no row matches. `LQL-4045` at `WARN` level |
| `minPrice == null \|\| it.price!! > minPrice` | Allowed | You wrote the null check yourself, so lambdaQL uses it. |
| `it.price!! > minPrice!!` (`minPrice` is `null`) | Runs | `e0.price > :p1` with NULL bound: no row matches. `!!` and the unboxing are not run. `LQL-4045` at `DEBUG` level |
| `it.id in ids` (`ids` has a `null` element) | Error at binding | `IN` with a null element is an SQL trap. |

<a id="placeholders-called-outside-a-lambda"></a>
### Placeholders called outside a lambda expression

Some methods exist only to be read inside a lambda expression. Their return value is a placeholder.

| Method | When called outside a query lambda expression |
|---|---|
| `Agg.count()`, `Agg.sum(...)` and the other `Agg` methods | Throws `IllegalStateException` (`LQL-4032`) |
| `By.asc(...)`, `By.desc(...)`, `By.sort(...)` | Throws `IllegalStateException` when called outside a sort lambda expression (`LQL-4046`) |
| Your own `@SqlFunction` methods | The body is not called. A direct call outside a lambda expression throws `DbOnlyException` (`LQL-4034`). The body must only throw this exception; a body that computes a value is rejected (`LQL-3052`). |

#### Java

<!-- region: ManualSharedTest.java#db-only -->
```java
// the body never runs in Java
assertEquals("LQL-4034", assertThrows(DbOnlyException.class, () -> OrderFns.discount(1.0)).code());
```

The declaration of `OrderFns` is in 8. Procedures and DB functions ([Java](java/08-procedures-functions.md#86-declare-db-functions) | [Kotlin](kotlin/08-procedures-functions.md#86-declare-db-functions)).

#### Kotlin

<!-- region: ManualKotlinErrorsTest.kt#db-only -->
```kotlin
// the body never runs in Kotlin
assertEquals("LQL-4034", assertThrows<DbOnlyException> { OrderFns.discount(1.0) }.code())
```

Example code: [`ManualSharedTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualSharedTest.java) (`db-only`), [`ManualKotlinErrorsTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinErrorsTest.kt) (`db-only`).

<a id="message-language"></a>
## Message language

lambdaQL has messages in **four languages**.

| Language | Locale |
|---|---|
| English | Default (root) |
| Japanese | `ja` |
| Chinese (Simplified) | `zh` |
| Korean | `ko` |

<a id="how-lambdaql-chooses-the-language"></a>
### How lambdaQL chooses the language

lambdaQL checks these three places in this order. The first value that is set is used.

| Order | Setting | Example |
|---|---|---|
| 1 | `LqlMessages.setLocale(...)` in code | See the code below |
| 2 | System property `lambdaql.locale` | `java -Dlambdaql.locale=ja -jar app.jar` |
| 3 | JVM default locale | `Locale.getDefault()` |

Then lambdaQL looks only at the **language** of that locale.

| Language of the locale | Message language |
|---|---|
| `ja` (for example `ja_JP`) | Japanese |
| `zh` (also `zh_CN` and `zh_TW`) | Chinese (Simplified) |
| `ko` (for example `ko_KR`) | Korean |
| Any other language (`en`, `fr`, `de`, ...) | English |

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

`LqlMessages.LOCALE_PROPERTY` is the name `"lambdaql.locale"`. `LqlMessages.bundleLocale(locale)` tells you which message file a locale uses.

In an application, write it like this:

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

Notes:

- lambdaQL chooses the language itself. It does not use the usual `ResourceBundle` fallback. So you never get Korean messages when you asked for English just because the JVM is Korean.
- The message is fixed when the exception is created. If you need another language later, call `e.getMessage(locale)`.
- A server can run with a JVM locale that is not English. If you want English logs, call `LqlMessages.setLocale(Locale.ROOT)` or use `-Dlambdaql.locale=en`.
- Traditional Chinese messages can be added if users ask for them.
- The Japanese and Chinese texts were first written by the author, not by native speakers. Help to improve them is welcome.

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
- Arguments are written as `{0}`, `{1}`, ....
- lambdaQL does not use `MessageFormat`. So you can write single quotes (`can't`) as they are. Numbers are not formatted (the line number `1234567` stays `1234567`).
- Every key must be in all four files with the same arguments. A new message must be added in all four languages.

<!-- region: LqlMessagesTest.java#fill -->
```java
// no MessageFormat: quotes and big numbers stay as they are (rule I4)
assertEquals("can't read line 1234567", LqlMessages.fill("can't read line {0}", 1234567));
assertEquals("a null b", LqlMessages.fill("a {0} b", (Object) null));
assertEquals("{x} {9}", LqlMessages.fill("{x} {9}", "unused"));
```

Example code: [`LqlMessagesTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/i18n/LqlMessagesTest.java) (`locale`, `fill`), [`ManualSharedTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualSharedTest.java) (`set-locale`), [`ManualKotlinErrorsTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinErrorsTest.kt) (`set-locale`).

<a id="logging"></a>
## Logging

lambdaQL logs with the **SLF4J API**.

- lambdaQL depends only on `slf4j-api`. You choose the logging backend (Logback, Log4j 2 and others).
- Spring Boot already has SLF4J and Logback, so you do not need to add anything.
- Warning messages start with a code. Most codes are in the `LQL-5xxx` range, and `LQL-4045` is also only logged.
- The level (`WARN`, `INFO`, `DEBUG`) is different for each code. The table below shows the level of each code.
- If that level is off, lambdaQL does not build the message text.

Warnings that lambdaQL logs now:

| Code | When |
|---|---|
| `LQL-4045` | A captured value of `=` or `<>` (`==`, `!=`, `equals`, `Objects.equals`) is `null`, so no row matches (SQL meaning). It is known only at run time. Logged at `WARN` level. When the value goes through an unboxing to its own primitive type (`min.doubleValue()`), as in a size comparison, the level is `DEBUG`. To find NULL, write `x == null` (see 3.6: [Java](java/03-where.md#36-null-rules) \| [Kotlin](kotlin/03-where.md#36-null-rules)) |
| `LQL-5001` | A DB function wraps a column in a condition (`OrderFns.discount(o.getPrice()) > 10`, a Boolean function on a column, a function inside `IN` or `IS NULL`). A normal index cannot be used. Logged at `WARN` level, once per `where` lambda expression. Not logged when Oracle or PostgreSQL has a function-based (expression) index on the same expression (see [8.9](java/08-procedures-functions.md#89-warning-functions-on-columns-and-indexes-sargable)) |
| `LQL-5002` | A subquery has `orderBy` but no `limit`, so lambdaQL removes the sort (the sort does not change the result) |
| `LQL-5003` | An outside sort (Spring `Sort`, `SortSpec.ofPath`) uses a name that is not in the sort rules, and the mode is `SortMode.INFO` (the default, logged at `INFO` level) or `SortMode.WARN` (logged at `WARN` level). The sort runs. Logged once per entity and name (see [4.10 Sort policy](java/04-order-paging.md#sort-policy)) |
| `LQL-5004` | The class file does not have the argument names of a procedure stub (compiled without `-parameters` or Kotlin `-java-parameters`, and no `@DbParam`), and two neighboring arguments have the same type. Logged at `INFO` level, once per procedure (see [8.4](java/08-procedures-functions.md#84-warning-argument-order)) |
| `LQL-5005` | The procedure metadata cannot be read (for example, the query fails because there is no permission on the dictionary views or `information_schema`). The stub is not checked, and the argument layout is taken from the stub. Logged at `INFO` level, once per procedure (see [8.4](java/08-procedures-functions.md#when-the-metadata-cannot-be-read)) |
| `LQL-5006` | Only the Oracle overload lookup (`all_arguments`) fails. The call goes on without the overload check. Logged at `INFO` level |
| `LQL-5007` | A DB function on the value side is `NOT DETERMINISTIC` on MariaDB and MySQL, or `VOLATILE` on PostgreSQL, and its value is compared with a column (`o.getPrice() > OrderFns.nondet(base)`). The function is computed for every row, and the index on the compared column is not used (confirmed with the execution plans of the three databases). Logged at `WARN` level |
| `LQL-5008` | The index or function metadata for the SARGable check cannot be read (for example, no permission). Logged at `INFO` level. `LQL-5001` is still logged, and `LQL-5007` is not checked |
| `LQL-5009` | A condition compares entity values with SQL meaning, so NULL rows that match in Java and Kotlin can be left out: `==`/`equals` between entity values, `!=` with a value, `!` on a nullable `Boolean`, a subquery `NOT IN`. Logged at `DEBUG` level, once per condition statement (see 3.6.3: [Java](java/03-where.md#363-write-your-null-checks-yourself) \| [Kotlin](kotlin/03-where.md#363-write-your-null-checks-yourself)) |
| `LQL-5010` | A `@Lob` column is used in `order by`, `distinct` or `group by`. H2 and MariaDB run it, but Oracle rejects it (ORA-22848, ORA-22849). An `=` comparison works on all three databases. lambdaQL does not change the query. Logged at `DEBUG` level, once per column and use (see [4.5](java/04-order-paging.md#45-what-a-sort-key-can-be)) |
| `LQL-5011` | A query sorts by an enum column with `@Enumerated(EnumType.STRING)`. The database sorts by name, and Java and Kotlin sort by declaration order, so the order can differ. lambdaQL does not change the query. Logged at `DEBUG` level, once per column. Not logged for `ORDINAL` (see [4.5](java/04-order-paging.md#45-what-a-sort-key-can-be)) |
| `LQL-5012` | Arithmetic (`+ - * /`) with only one column and values is on the column side of a condition (`o.getCnt() * 2 > n`). It can be moved to the value side. Logged at `WARN` level. In `SargableMode.STRICT` it is the error `LQL-3066` (see [8.9](java/08-procedures-functions.md#89-warning-functions-on-columns-and-indexes-sargable)) |
| `LQL-5013` | A condition has arithmetic between columns, or `%` (`o.getPrice() * o.getCnt() > n`). It cannot be moved, so it is logged only at `DEBUG` level. Allowed also in `STRICT` |

```
WARN  [LQL-5002] A subquery sort without limit does not change the result, so it was removed (Q4): ...
```

Planned warnings (not implemented yet):

- A case-insensitive comparison when the column has no matching index or collation

A general strict mode that turns all warnings into errors at once is not implemented.
Instead, two areas have their own `STRICT` mode.

- Sort policy: in `SortMode.STRICT`, the case of `LQL-5003` raises `SortNotAllowedException` (`LQL-4040`).
- SARGable: in `SargableMode.STRICT`, the cases of `LQL-5001`, `LQL-5007` and `LQL-5012` raise `NonSargableException` (`LQL-3066`).

> [!NOTE]
> `lambdaql` does not depend on Spring. Its only logging dependency is `slf4j-api`, so you choose the logging implementation. `lambdaql-spring-data-jpa` uses the Spring Data JPA of your project as it is. The starters (`lambdaql-spring-boot-starter-boot3` and `-boot4`) also bring in `spring-boot-starter-data-jpa` and Logback.

<a id="summary"></a>
## Summary

| Question | Answer |
|---|---|
| How do I recognize a lambdaQL error? | It is a `LambdaQLException`, and its message starts with `[LQL-xxxx]`. |
| How do I check an error in code or tests? | Use `e.code()`. Do not compare the text. |
| How do I get English text on a non-English server? | `LqlMessages.setLocale(Locale.ROOT)`, `-Dlambdaql.locale=en`, or `e.getMessage(Locale.ROOT)`. |
| Where do warnings go? | To the SLF4J log. The level depends on the code and is one of `WARN`, `INFO` and `DEBUG`. |

[Manual home](README.md) | [Java track](java/README.md) | [Kotlin track](kotlin/README.md)
