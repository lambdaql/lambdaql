<a id="9-java-notes"></a>
# 9. Java notes

> [!IMPORTANT]
> This page is older than the Korean manual. See the [Korean page](../../ko/java/09-java-notes.md) for the latest rules.

[Java track](README.md) | [Manual home](../README.md) | Previous: [8. Procedures and DB functions](08-procedures-functions.md) | Next: [10. Rules and limits](10-rules-and-limits.md) | [Kotlin version of this page](../kotlin/09-kotlin-notes.md)

Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md)

---

This page collects the things that are special about **Java** code in lambdaQL lambdas.
The main idea is always the same: **a lambdaQL lambda is a query, not code.**
lambdaQL reads what you wrote. It does not run it. So some Java run-time rules do not apply.

<a id="contents"></a>
## Contents

- [9.1 Lambdas must be serializable lambdas](#91-lambdas-must-be-serializable-lambdas)
- [9.2 Boxing and unboxing](#92-boxing-and-unboxing)
- [9.3 Equality of boxed values](#93-equality-of-boxed-values)
- [9.4 Strings](#94-strings)
- [9.5 Null-safe helpers](#95-null-safe-helpers)
- [9.6 Small types: char, short, byte and boolean](#96-small-types-char-short-byte-and-boolean)
- [9.7 float, double and BigDecimal](#97-float-double-and-bigdecimal)
- [9.8 Lambdas in instance methods capture this](#98-lambdas-in-instance-methods-capture-this)
- [9.9 Method references](#99-method-references)
- [9.10 Static helpers you should not expect](#910-static-helpers-you-should-not-expect)
- [9.11 Other Java forms](#911-other-java-forms)
- [9.12 Compiler and build](#912-compiler-and-build)
- [9.13 What you can check today](#913-what-you-can-check-today)

---

<a id="91-lambdas-must-be-serializable-lambdas"></a>
## 9.1 Lambdas must be serializable lambdas

lambdaQL finds the body of your lambda through Java serialization (`SerializedLambda`).
It does not use `setAccessible`. It does not really serialize your captured values.

| You pass | Result |
|---|---|
| A lambda written directly in the call: `where(o -> o.isActive())` | OK. The parameter type of `where` is a `Serializable` functional interface |
| A method reference: `where(Order::isActive)` | OK (see [9.9](#99-method-references)) |
| A lambda kept in a variable of the lambdaQL parameter type | OK |
| A `java.util.function.Predicate` (not `Serializable`) | **rejected** (`LambdaAnalysisException`) |
| An anonymous class: `new Where<Order>() { ... }` | **rejected** |

Notes:

- Captured values (and a captured `this`) do **not** need to be `Serializable`. lambdaQL reads them. It does not serialize them.
- Inner lambdas of subqueries (`sub.from(Item.class).where(i -> ...)`) do not need to be `Serializable` on their own. lambdaQL finds them in the bytecode of the outer lambda.
- The names of the lambdaQL functional interfaces are not final yet. Pass the lambda directly when you can.

<a id="92-boxing-and-unboxing"></a>
## 9.2 Boxing and unboxing

The example entities use boxed types: `Long id`, `Double price`, `Integer status`.
When you compare a boxed value with a primitive, javac adds an **unboxing** call (`intValue()`, `doubleValue()`).

```java
.where(o -> o.getStatus() == 1)     // javac: o.getStatus().intValue() == 1
// HQL: e0.status = 1
```

lambdaQL **ignores** boxing and unboxing calls (`valueOf`, `intValue`, `doubleValue`, ...). It only keeps the meaning.

What this means for NULL:

| Java form | In Java (if run) | In lambdaQL |
|---|---|---|
| `o.getStatus() == 1`, status is NULL | NullPointerException | SQL meaning: UNKNOWN, row not returned (rule R1) |
| `o.getPrice() > 100`, price is NULL | NullPointerException | row not returned (R1) |
| `!o.getActive()` (`Boolean`), active is NULL | NullPointerException | row not returned |
| `o.getPrice() > p`, captured `Double p` is `null` | NullPointerException | NULL bound to `e0.price > :p1`, no row matches (`LQL-4045` at `DEBUG` level) |
| `i.getQty() == q`, `qty` is `int`, captured `Integer q` is `null` | NullPointerException | NULL bound to `i.qty = :p1`, no row matches (`LQL-4045` at `DEBUG` level) |
| `o.getStatus() > p.intValue()`, captured `Double p` is `null` | NullPointerException | **exception** when the query runs (`LQL-4012`) |

So an entity value that is NULL follows SQL. A call that unboxes a captured value to its own primitive type (`Double.doubleValue()`, `Integer.intValue()` and others, including a widening conversion after it) is the same bytecode as javac's automatic unboxing. So lambdaQL does not run it and binds the boxed value.
A call that converts to a different primitive type (`intValue()` on a `Double`) can only be written by hand, so it is a computation. lambdaQL runs it in Java, and a `null` is an error, as in Java.

If you want NULL rows, write the check yourself.

```java
.where(o -> o.getStatus() == null || o.getStatus() != 1)
// HQL: e0.status is null or e0.status <> 1
```

<a id="93-equality-of-boxed-values"></a>
## 9.3 Equality of boxed values

In Java, `==` between two **objects** checks identity (the same object in memory).
In lambdaQL, `==` between two objects is a **value compare** (decision D12).
Values of the entity and captured values both follow the SQL meaning (decision D48, see [3.6](03-where.md#36-null-rules)).
The boxing cache (`Integer` and `Long` from -128 to 127) and `new String(...)` do not matter.

Which rule you get depends on the **Java types** of the two sides.

| Left side | Right side | javac makes | lambdaQL rule | HQL |
|---|---|---|---|---|
| `Integer` column | `int` constant or variable | unboxing, number compare | R1, SQL meaning | `e0.status = 1` |
| `Integer` column | `Integer` variable | object compare | R2 | `e0.status = :p1` |
| `Long` `@Id` column | `Long` variable | object compare | R2, but one side is never NULL | `e0.id = :p1` |
| `Long` column | `long` variable | unboxing | R1 | `e0.id = :p1` |
| `String` | `String` | object compare | R2 | `e0.description = :p1` |
| `String` column | `String` column | object compare | R2, SQL meaning | `e0.description = e0.custName` (a NULL on either side: no match) |
| enum | enum | object compare | R2 | `x = :p1` |
| `Boolean` column | `Boolean.TRUE` | object compare | R2 | `e0.active = :p1` |
| `Boolean` column | `true` | unboxing | R1 | `e0.active = true` |
| value object (`@Embedded`) | captured value object of the same type | `equals` / object compare | expanded per column (B6) | `e0.address.city = :p1 and e0.address.zip = :p2` (see [3.10](03-where.md#310-association-paths)) |
| anything | `null` | null check | R3 | `x is null` |
| entity | entity (`o.getCustomer() == c`) | object compare | **not in v0.1** | – (compare ids instead) |

With a captured value:

| Column | Captured value | HQL | Result |
|---|---|---|---|
| 3 | 3 | `e0.status = :p1` | true |
| NULL | `null` | `e0.status = :p1` | **false**: `= NULL` is never true (`LQL-4045` at `WARN` level) |
| NULL | 3 | `e0.status = :p1` | false |
| 3 | `null` | `e0.status = :p1` | false |

```java
Integer wanted = request.getStatus();        // may be null
qb.from(Order.class).where(o -> o.getStatus() == wanted).toList();
// wanted = 3    -> rows with status 3
// wanted = null -> no matching row (LQL-4045). To find NULL, use o.getStatus() == null
```

The column is never wrapped in a function, so the database can use an index.

`!=` between objects gives `<>`. A row whose value is NULL does not match `<>` (SQL meaning, D48). Java would return it.
To keep such rows, write `o.getStatus() == null || o.getStatus() != wanted`.

Two values of the entity (`o.getDescription() == o.getCustName()`) give `=` and `<>`. A NULL on either side does not match, and NULL and NULL do not match either.
lambdaQL logs `LQL-5009` at `DEBUG` level when the result can differ from Java (see [3.6.3](03-where.md#363-write-your-null-checks-yourself)).

<a id="94-strings"></a>
## 9.4 Strings

| Java form | HQL | NULL column | `p` is `null` |
|---|---|---|---|
| `o.getDescription() == p` | `e0.description = :p1` | row not returned | runs, no matching row (`LQL-4045`) |
| `o.getDescription().equals(p)` | `e0.description = :p1` | row not returned | runs, no matching row (`LQL-4045`) |
| `p.equals(o.getDescription())` | `e0.description = :p1` | row not returned | runs, no matching row (`LQL-4045`) |
| `"abc".equals(o.getDescription())` (constant receiver) | `e0.description = 'abc'` (the constant is never NULL, so a plain `=`) | row not returned | – |
| `Objects.equals(o.getDescription(), p)` | `e0.description = :p1` | row not returned | runs, no matching row (`LQL-4045`) |
| `o.getDescription().compareTo(p) < 0` | `e0.description < :p1` | row not returned | runs, no matching row (`LQL-4045`) |
| `o.getDescription() == null` | `e0.description is null` | returned | – |

Points to remember:

- In normal Java, `==` on strings is a bug. In a lambdaQL lambda, it is a valid value compare.
- `==`, `equals` and `Objects.equals` behave the same for NULL. If the column is NULL or `p` is `null`, no row matches. If you want NULL rows, write `o.getDescription() == null` yourself.
- The compare follows the **database collation**, not Java. MariaDB `_ci` collations ignore case and trailing spaces. Oracle stores `''` as NULL.
- A string constant written in the lambda becomes an HQL literal, and a quote is escaped (`'O''Brien'`). Captured strings are always bind parameters (see [3.3](03-where.md#33-basic-form)).
- String methods on a column (`startsWith`, `contains`, `trim`, `toUpperCase`) are planned, not in v0.1.
- String `+` is rejected in v0.1, even with only captured values (javac compiles `+` with `invokedynamic`). Build the string before the lambda.

```java
String code = "A" + suffix;                                  // outside: normal Java
qb.from(Order.class).where(o -> o.getDescription() == code).toList();
```

<a id="95-null-safe-helpers"></a>
## 9.5 Null-safe helpers

These Java helpers follow the same rules as `==`, that is, the SQL meaning (rule R5, D48). A captured `null` matches no row.

| Java | HQL | Note |
|---|---|---|
| `Objects.equals(o.getStatus(), s)` | `e0.status = :p1` | `s` is `null`: no matching row (`LQL-4045`) |
| `!Objects.equals(o.getStatus(), 1)` | `e0.status <> 1` | NULL rows **not returned** (SQL meaning; Java would return them) |
| `Objects.equals(o.getDescription(), o.getCustomer().getName())` | `e0.description = j1.name` + `join e0.customer j1` | two columns: a NULL on either side does not match |
| `Boolean.TRUE.equals(o.getActive())` | `e0.active = :p1` | |
| `!Boolean.TRUE.equals(o.getActive())` | `e0.active <> :p1` | NULL rows **not returned** (SQL meaning) |
| `"x".equals(o.getDescription())` | `e0.description = 'x'` | constant receiver: never NULL, so a plain `=` |
| `Objects.requireNonNullElse(o.getPrice(), 0.0)` in a `set` value | `coalesce(o.price, 0.0)` | see [7. Update, insert, delete](07-dml.md) |

Compare with `!=`:

```java
.where(o -> o.getStatus() != 1)                 // e0.status <> 1           (NULL rows not returned)
.where(o -> !Objects.equals(o.getStatus(), 1))  // e0.status <> 1           (NULL rows not returned either)
.where(o -> o.getStatus() == null || !Objects.equals(o.getStatus(), 1))   // NULL rows returned
```

<a id="96-small-types-char-short-byte-and-boolean"></a>
## 9.6 Small types: char, short, byte and boolean

The JVM has no separate compare instructions for `char`, `short`, `byte` and `boolean`. javac compiles them as `int`.
lambdaQL knows this.

- Compares of `long`, `int`, `short`, `byte`, `char`, `float` and `double` values are supported (rule R1).
- A constant is converted to the type of its column. For example, the `int` constant `10` becomes a `short` value for a `short` column.
- A `boolean` getter alone is a condition: `o.isActive()` becomes `e0.active = true`.
- `o.isActive() == (o.getStatus() > s)` (two boolean expressions) is supported. It becomes `AND`/`OR` (see [3.7](03-where.md#37-boolean-columns)).
- A primitive property (`boolean active`, `int qty`) counts as "never NULL", like an `@Id` or a `nullable = false` column. So lambdaQL can drop a null check on it, and `o.getId() == null` becomes `1=0` ([3.6.2](03-where.md#362--on-objects-compares-values)).

<a id="97-float-double-and-bigdecimal"></a>
## 9.7 float, double and BigDecimal

| Topic | Rule |
|---|---|
| `float` / `double` constants (`100.0`, `1.5f`) | written into the HQL as literals (`100.0`, `1.5F`), like other constants in the lambda (D37). A `BigDecimal` made in the lambda (`new BigDecimal("1.5")`) is a computed value, so it is a bind parameter |
| Bit-exact results | **not promised**. Databases store and compare `float` and `double` differently. lambdaQL adds no `CAST`. Choose your column mapping yourself (for example Oracle `binary_float` / `binary_double`) |
| Captured `NaN`, `Infinity` | **exception** when the query runs |
| `NaN` stored in the database | follows the database (H2 sorts it as the largest value; MariaDB cannot store it) |
| `BigDecimal` equality | Java `equals` compares the scale (`1.0` is not `1.00`). lambdaQL compares the **value** |
| Integer `/` | databases do not agree (MariaDB `5/2 = 2.5`). The final rule is not decided. Avoid it |

<a id="98-lambdas-in-instance-methods-capture-this"></a>
## 9.8 Lambdas in instance methods capture this

When a lambda in an instance method uses a field, javac makes the lambda body an **instance method** and captures `this`.

```java
@Service
public class OrderService {

    private final QueryBuilder qb;
    private int defaultStatus = 1;

    public OrderService(QueryBuilder qb) {
        this.qb = qb;
    }

    public List<Order> defaults() {
        return qb.from(Order.class)
                 .where(o -> o.getStatus() == defaultStatus)   // means this.defaultStatus
                 .toList();
        // HQL: select e0 from Order e0 where e0.status = :p1
    }
}
```

**About the SQL blocks.** `…` is the shortened select list (all entity columns), and `?` is a bind parameter. The lines come from [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java). It runs each query on H2 2.3, MariaDB 10.11 and Oracle 23.26 with Hibernate 6.6.5, and it fails if Hibernate's SQL changes.

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `this-capture`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=?
```

What lambdaQL does:

- It knows that the first captured value is `this`. (This part is implemented and tested.)
- `this.defaultStatus` does not use the entity, so it is computed in Java and bound as a parameter.
- The field is read **each time the query runs**. The analysis result is cached per lambda, not per object.
- Your class does not need to be `Serializable`.
- Method calls on `this` that do not use the entity (`this.minPrice()`) are also computed in Java each time the query runs. Keep them free of side effects.

A local variable is different. Java copies its value when the lambda is created. Java needs a local variable to be effectively final.

```java
int st = defaultStatus;                                   // copied now
qb.from(Order.class).where(o -> o.getStatus() == st).toList();
```

Both forms give the same HQL.

<a id="99-method-references"></a>
## 9.9 Method references

| Method reference | Same as | Status |
|---|---|---|
| `where(Order::isActive)` | `where(o -> o.isActive())` | designed (detection is implemented) |
| `orderBy(Order::getPrice)` | `orderBy(o -> o.getPrice())` | implemented, tested |
| Method reference in the `by` sort form `orderBy((o, by) -> ...)` | – | **not allowed**. Use a lambda (rule S13) |
| Method reference in a join sort lambda | – | **not allowed**. Use a lambda (rule S13) |

lambdaQL detects a method reference when it loads it. It does not read the body of the referenced method. It builds the call `o.isActive()` itself.

Some method references compile but have the wrong meaning. lambdaQL rejects them at analysis time (rule S13).

- a one-argument instance method used as a two-argument lambda (the second argument would become the method argument),
- a two-argument static method used as a two-argument lambda.

In `orderBy`, only a getter of the entity is allowed. A static method, a method with arguments, or a reference bound to an object is rejected.

Use a lambda for anything that is not a simple getter.

<a id="910-static-helpers-you-should-not-expect"></a>
## 9.10 Static helpers you should not expect

lambdaQL has **no static DSL**. Except for the optional import of `Agg`, you do not need `import static` for lambdaQL.
Helpers come in as **lambda arguments** (`by`, `sub`), or you write normal Java.

| You might expect | The lambdaQL way |
|---|---|
| `orderBy(asc(...), desc(...))` | `orderBy((o, by) -> by.asc(...).desc(...))` ([4](04-order-paging.md)) |
| `exists(...)`, `in(...)`, `notIn(...)` | `(o, sub) -> sub.from(X.class).where(...).exists()` / `.select(...).contains(x)` ([5](05-subquery.md)) |
| `set(path, value)` | `set(o -> o.setX(v))` or `set(o -> value(o.getX(), v))` (`Dml.value`, D49) ([7](07-dml.md)) |
| Q-classes, `Order_.price` | getters: `o.getPrice()` |
| `count()`, `sum(...)`, ... | `Agg.count()`, `Agg.sum(...)`: the only static helper classes are `Agg` and `Dml` ([6](06-select-group.md)) |
| `coalesce(a, b)` | `Objects.requireNonNullElse(a, b)` in a `set` value |
| `like(...)`, `lower(...)`, `upper(...)` | planned (string functions on columns) |
| `currentTimestamp()` | `LocalDateTime.now()`. It is computed in the application and bound |
| Your own static utility methods on entity values | **rejected**, except DB functions with `@SqlFunction` ([8](08-procedures-functions.md)) |

Static methods that use **only captured values** are fine. They run in Java each time the query runs.

```java
.where(o -> o.getUpdateAt().isAfter(LocalDateTime.of(2026, 1, 1, 0, 0)))   // OK: computed in Java
.where(o -> MyUtil.normalize(o.getDescription()) == text)                   // rejected: uses the entity
```

<a id="911-other-java-forms"></a>
## 9.11 Other Java forms

| Java form | In a lambdaQL lambda |
|---|---|
| One expression `o -> ...` | the normal form |
| Block lambda `o -> { ...; return ...; }` with local variables | rejected in `where`, `orderBy`, `select` |
| Block lambda with setter calls only | allowed in `set`, `values`, `insert ... select` ([7](07-dml.md)) |
| `if`, `for`, `while`, `switch` (statement or expression), `try` | rejected |
| Ternary `c ? x : y` | only `c ? x : false`, `c ? true : x`, `c ? false : true` ([3.5](03-where.md#35-and-or-not)) |
| `&`, `\|`, `^` on booleans | rejected. Use `&&`, `\|\|` |
| Streams, nested lambdas (except `sub` subqueries) | rejected |
| `List.of(...)` inside the lambda | rejected, except as `groupBy` keys |
| `compareTo(...) < 0`, `isBefore`, `isAfter` | become `<`, `>` |
| `captured.length` (array of a captured value) | computed in Java |
| Records | as result types (`select`), and as row types of derived tables and CTE (read with `r.total()`) |
| `var` | use it for builder variables. The builder type names are not final |
| Generic `value(o.getStatus(), "abc")` | compiles in Java. At analysis time lambdaQL checks it against the column type and reports the error `LQL-3065` |
| `value(...)` inside a class that has its own `value(...)` method (for example a record with a `value` component) | the static import is hidden. Write `Dml.value(...)` |
| The same parameter name in nested lambdas | not allowed by Java. Use `sub2`, `i`, `x` |

<a id="912-compiler-and-build"></a>
## 9.12 Compiler and build

| Topic | What to know |
|---|---|
| Compiler | lambdaQL is tested with **javac**. Lambdas compiled by **ECJ** (the Eclipse compiler) are not guaranteed. The Eclipse IDE and other tools based on Eclipse JDT use ECJ. Run your tests with Gradle or Maven (javac) |
| JDK version | Java 21 |
| ASM | lambdaQL reads class files with ASM 9.11.1 (up to Java 28 class files). If you compile with a newer JDK, update ASM. lambdaQL does not shade ASM, so you can update it yourself |
| `-parameters` | recommended. It lets lambdaQL check procedure argument names ([8.4](08-procedures-functions.md#84-warning-argument-order)) |
| Debug information | keep line numbers (the javac default). lambdaQL puts the source line into error messages |
| IntelliJ "Number objects compared using `==`" | intended in a lambdaQL lambda. You can use `@SuppressWarnings("NumberEquality")` |
| IntelliJ warning for `==` on strings | also intended in a lambdaQL lambda |

<a id="913-what-you-can-check-today"></a>
## 9.13 What you can check today

The analysis core is implemented. Most of these tests use internal classes, not the user API, but they show the Java behavior on this page.

| Test | Shows |
|---|---|
| [`LoadExamplesTest.java`](../../../../query/src/test/java/org/lambdaql/engine/load/LoadExamplesTest.java) `instanceLambda` | a lambda that uses a field captures `this` as the first captured value ([9.8](#98-lambdas-in-instance-methods-capture-this)) |
| [`LoadExamplesTest.java`](../../../../query/src/test/java/org/lambdaql/engine/load/LoadExamplesTest.java) `methodReference` | `Order::isActive` is detected as a method reference ([9.9](#99-method-references)) |
| [`LoadExamplesTest.java`](../../../../query/src/test/java/org/lambdaql/engine/load/LoadExamplesTest.java) `notSerializable` | a `java.util.function.Predicate` is rejected ([9.1](#91-lambdas-must-be-serializable-lambdas)) |
| [`ConditionDecodeTest.java`](../../../../query/src/test/java/org/lambdaql/engine/decode/ConditionDecodeTest.java) | unboxing in compares (`o.getId() > minId`), `&&`, `\|\|`, `!` |
| [`SortQueryTest.java`](../../../../query/src/test/java/org/lambdaql/query/SortQueryTest.java) region `sort-key` | `Order::getPrice` works as a one-key sort, the same as `o -> o.getPrice()` ([9.9](#99-method-references)) |
| [`HostEvaluatorTest.java`](../../../../query/src/test/java/org/lambdaql/engine/sem/HostEvaluatorTest.java) `convertNumbers` | a number is converted to the column type (for example `int` to `short`) ([9.6](#96-small-types-char-short-byte-and-boolean)) |

---

[Java track](README.md) | Previous: [8. Procedures and DB functions](08-procedures-functions.md) | Next: [10. Rules and limits](10-rules-and-limits.md) | [Kotlin version of this page](../kotlin/09-kotlin-notes.md)
