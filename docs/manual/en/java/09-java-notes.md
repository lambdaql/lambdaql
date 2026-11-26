<a id="9-java-notes"></a>
# 9. Java notes

[Java track](README.md) | [Manual home](../README.md) | Previous: [8. Procedures and DB functions](08-procedures-functions.md) | Next: [10. Rules and limits](10-rules-and-limits.md) | [Kotlin version of this page](../kotlin/09-kotlin-notes.md)

Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md)

---

This page collects what is special about **Java** code inside lambdaQL lambda expressions.
The key point is always the same: **a lambdaQL lambda expression is a query expression, not code.**
lambdaQL does not call the lambda expression. It analyzes its bytecode opcodes. So some run-time rules of Java do not apply.

<a id="contents"></a>
## Contents

- [9.1 Lambda expressions must be serializable lambda expressions](#91-lambdas-must-be-serializable-lambdas)
- [9.2 Boxing and unboxing](#92-boxing-and-unboxing)
- [9.3 Equality of boxed values](#93-equality-of-boxed-values)
- [9.4 Strings](#94-strings)
- [9.5 null-safe helpers](#95-null-safe-helpers)
- [9.6 Small types: char, short, byte and boolean](#96-small-types-char-short-byte-and-boolean)
- [9.7 float, double and BigDecimal](#97-float-double-and-bigdecimal)
- [9.8 Lambda expressions in instance methods capture this](#98-lambdas-in-instance-methods-capture-this)
- [9.9 Method references](#99-method-references)
- [9.10 Static helpers you should not expect](#910-static-helpers-you-should-not-expect)
- [9.11 Other Java forms](#911-other-java-forms)
- [9.12 Compiler and build](#912-compiler-and-build)

---

<a id="91-lambdas-must-be-serializable-lambdas"></a>
## 9.1 Lambda expressions must be serializable lambda expressions

lambdaQL finds the body of a lambda expression through Java serialization (`SerializedLambda`).
It does not use `setAccessible`. It does not really serialize the captured values either.

| What you pass | Result |
|---|---|
| A lambda expression written directly in the call: `where(o -> o.isActive())` | OK. The parameter type of `where` is a `Serializable` functional interface |
| A method reference as a condition: `where(Order::isActive)` | **Rejected** (`LQL-1009`). Use a lambda expression (see [9.9](#99-method-references)) |
| A lambda expression kept in a variable of a lambdaQL parameter type | OK |
| `java.util.function.Predicate` (not `Serializable`) | **Rejected** (`LambdaAnalysisException`) |
| An anonymous class: `new Where<Order>() { ... }` | **Rejected** |

Notes:

- Captured values (and a captured `this`) do **not** need to be `Serializable`. lambdaQL only reads those values. It does not serialize them.
- The inner lambda expression of a subquery (`sub.from(Item.class).where(i -> ...)`) does not need to be `Serializable` on its own. lambdaQL finds it in the bytecode opcodes of the outer lambda expression.

<a id="92-boxing-and-unboxing"></a>
## 9.2 Boxing and unboxing

The example entity `Order` has both boxed properties (`Long id`, `Integer cnt`, `Double total`) and primitive properties (`double price`, `short status`).
When you compare a boxed value with a primitive type, javac adds an **unboxing** call (`intValue()`, `doubleValue()`).

<!-- region: ManualJavaNotesTest.java#unboxing -->
```java
// cnt is an Integer column: javac writes o.getCnt().intValue() == 1; lambdaQL keeps only the meaning
SelectQuery<Order> q = qb.from(Order.class).where(o -> o.getCnt() == 1);
assertEquals("select e0 from Order e0 where e0.cnt = 1", q.toHql().hql());
// a NULL cnt: no NullPointerException, the row is simply not returned (the SQL meaning)
```

lambdaQL **ignores** boxing and unboxing calls (`valueOf`, `intValue`, `doubleValue`, ...). It keeps only the meaning.

What this means for NULL:

| Java form | In Java (if it ran) | In lambdaQL |
|---|---|---|
| `o.getCnt() == 1`, cnt is NULL | NullPointerException | SQL meaning: UNKNOWN, the row is not returned |
| `o.getTotal() > 100`, total is NULL | NullPointerException | The row is not returned |
| `!c.getVip()` (`Boolean`, example entity `Coded`), vip is NULL | NullPointerException | The row is not returned (see [3.6.3](03-where.md#363-write-your-null-checks-yourself)) |
| `o.getPrice() > p`, the captured `Double p` is `null` | NullPointerException | NULL bound to `e0.price > :p1`, no row matches (`LQL-4045` at `DEBUG` level) |
| `i.getQty() == q`, `qty` is `int`, the captured `Integer q` is `null` | NullPointerException | NULL bound to `i.qty = :p1`, no row matches (`LQL-4045` at `DEBUG` level) |
| `o.getStatus() > p.intValue()`, the captured `Double p` is `null` | NullPointerException | **Exception** when the query runs (`LQL-4012`) |

So a NULL entity value follows SQL. A call that unboxes a captured value to its own primitive type (`Double.doubleValue()`, `Integer.intValue()` and so on, including a widening conversion after it) has the same bytecode opcodes as javac's automatic unboxing, so it is not run, and the boxed value is bound.
A call that converts to another primitive type (`intValue()` of a `Double`) is a computation you can only write by hand, so it runs in Java, and it is an error for `null`, as in Java.

If you want NULL rows, write the check yourself.

<!-- region: ManualJavaWhereTest.java#null-check -->
```java
// only rows where cnt is not NULL and not 1
SelectQuery<Order> notOne = qb.from(Order.class).where(o -> o.getCnt() != 1);
assertEquals("select e0 from Order e0 where e0.cnt <> 1", notOne.toHql().hql());
// rows where cnt is NULL too
SelectQuery<Order> notOneOrNull = qb.from(Order.class).where(o -> o.getCnt() == null || o.getCnt() != 1);
assertEquals("select e0 from Order e0 where e0.cnt is null or e0.cnt <> 1", notOneOrNull.toHql().hql());
```

Example code: [`ManualJavaNotesTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaNotesTest.java) (`unboxing`), [`ManualJavaWhereTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaWhereTest.java) (`null-check`).

<a id="93-equality-of-boxed-values"></a>
## 9.3 Equality of boxed values

In Java, `==` between two **objects** checks identity (whether they are the same object in memory).
In lambdaQL, `==` between two objects is a **value comparison**.
Both entity values and captured values follow SQL meaning (see [3.6](03-where.md#36-null-rules)).
The boxing cache (`Integer` and `Long` from -128 to 127) and `new String(...)` do not matter.

The comparison that javac builds depends on the **Java types** of both sides.

| Left | Right | What javac builds | HQL |
|---|---|---|---|
| `Integer` column | `int` constant or variable | Unboxing, number comparison | `e0.cnt = 1` |
| `Integer` column | `Integer` variable | Object comparison | `e0.cnt = :p1` |
| `Long` `@Id` column | `Long` variable | Object comparison | `e0.id = :p1` |
| `Long` column | `long` variable | Unboxing | `e0.id = :p1` |
| `String` | `String` | Object comparison | `e0.description = :p1` |
| `String` column | `String` column | Object comparison | `e0.description = e0.custName` (no match if either side is NULL) |
| enum | enum | Object comparison | `x = :p1` |
| `Boolean` column | `Boolean.TRUE` | Object comparison | `e0.active = :p1` |
| `Boolean` column | `true` | Unboxing | `e0.active = true` |
| Value object (`@Embedded`) | A captured value object of the same type | `equals` / object comparison | `e0.address.city = :p1 and e0.address.zip = :p2` (see [3.10](03-where.md#310-association-paths)) |
| Anything | `null` | Null check | `x is null` |
| Entity | Entity (`o.getCustomer() == c`) | Object comparison | – (compare the ids instead) |

When you compare with a captured value:

| Column | Captured value | HQL | Result |
|---|---|---|---|
| 3 | 3 | `e0.cnt = :p1` | true |
| NULL | `null` | `e0.cnt = :p1` | **false**: `= NULL` is never true (`LQL-4045` at `WARN` level) |
| NULL | 3 | `e0.cnt = :p1` | false |
| 3 | `null` | `e0.cnt = :p1` | false |

<!-- region: ManualJavaWhereTest.java#boxed-equals -->
```java
// cnt is an Integer column; Integer == Integer compares values, not objects
Integer wanted = 3;
SelectQuery<Order> three = qb.from(Order.class).where(o -> o.getCnt() == wanted);
assertEquals("select e0 from Order e0 where e0.cnt = :p1", three.toHql().hql());
// a captured null is bound as NULL: "= NULL" matches no row (WARN LQL-4045)
Integer none = null;
SelectQuery<Order> noRow = qb.from(Order.class).where(o -> o.getCnt() == none);
assertEquals("select e0 from Order e0 where e0.cnt = :p1", noRow.toHql().hql());
// to find NULL, write the null check
SelectQuery<Order> nullCnt = qb.from(Order.class).where(o -> o.getCnt() == null);
assertEquals("select e0 from Order e0 where e0.cnt is null", nullCnt.toHql().hql());
```

The column is not wrapped in a function, so the database can use an index.

`!=` between objects becomes `<>`. Rows where the value is NULL do not match `<>` (SQL meaning). Java would return these rows.
To keep these rows, write `o.getCnt() == null || o.getCnt() != wanted`.

Two entity values (`o.getDescription() == o.getCustName()`) become `=` and `<>`. If either side is NULL, they do not match, and this is also true for NULL and NULL.
When the result can differ from Java, lambdaQL logs `LQL-5009` at `DEBUG` level (see [3.6.3](03-where.md#363-write-your-null-checks-yourself)).

Example code: [`ManualJavaWhereTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaWhereTest.java) (`boxed-equals`).

<a id="94-strings"></a>
## 9.4 Strings

| Java form | HQL | NULL column | `p` is `null` |
|---|---|---|---|
| `o.getDescription() == p` | `e0.description = :p1` | The row is not returned | Runs, no row matches (`LQL-4045`) |
| `o.getDescription().equals(p)` | `e0.description = :p1` | The row is not returned | Runs, no row matches (`LQL-4045`) |
| `p.equals(o.getDescription())` | `e0.description = :p1` | The row is not returned | Runs, no row matches (`LQL-4045`) |
| `"abc".equals(o.getDescription())` (constant receiver) | `e0.description = 'abc'` (a constant cannot be NULL, so a simple `=`) | The row is not returned | – |
| `Objects.equals(o.getDescription(), p)` | `e0.description = :p1` | The row is not returned | Runs, no row matches (`LQL-4045`) |
| `o.getDescription().compareTo(p) < 0` | `e0.description < :p1` | The row is not returned | Runs, no row matches (`LQL-4045`) |
| `o.getDescription() == null` | `e0.description is null` | Returned | – |

Remember:

- In normal Java, `==` on strings is a bug. Inside a lambdaQL lambda expression, it is a correct value comparison.
- `==`, `equals` and `Objects.equals` behave the same for NULL. If the column is NULL or `p` is `null`, no row matches. If you want NULL rows, write `o.getDescription() == null` yourself.
- The comparison follows the **database collation**, not Java. MariaDB `_ci` collations ignore case and trailing spaces. Oracle stores `''` as NULL.
- A string constant written directly in the lambda expression becomes an HQL literal, and single quotes are escaped (`'O''Brien'`). A captured string is always a bind parameter (see [3.3](03-where.md#33-basic-form)).
- `startsWith`, `endsWith` and `contains` on a column become `like` ([3.8](03-where.md#38-strings)). Other string methods such as `trim` and `toUpperCase` are not supported (`LQL-3008`).
- String `+` in a lambda expression is rejected (`LQL-2007`). This is true even with only captured values (javac compiles `+` into `invokedynamic`). Build the string before the lambda expression.

<!-- region: ManualJavaNotesTest.java#string-outside -->
```java
String code = "A" + suffix;                                  // outside: plain Java
SelectQuery<Order> q = qb.from(Order.class).where(o -> o.getDescription() == code);
assertEquals("select e0 from Order e0 where e0.description = :p1", q.toHql().hql());
```

Example code: [`ManualJavaNotesTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaNotesTest.java) (`string-outside`).

<a id="95-null-safe-helpers"></a>
## 9.5 null-safe helpers

These Java helpers follow the same rule as `==`, that is, SQL meaning. A captured value `null` matches no row.

| Java | HQL | Notes |
|---|---|---|
| `Objects.equals(o.getCnt(), s)` | `e0.cnt = :p1` | If `s` is `null`, no row matches (`LQL-4045`) |
| `!Objects.equals(o.getCnt(), 1)` | `e0.cnt <> 1` | NULL rows are **not** returned (SQL meaning. Java would return them) |
| `Objects.equals(o.getDescription(), o.getCustomer().getName())` | `e0.description = j1.name` + `join e0.customer j1` | Two columns: no match if either side is NULL |
| `Boolean.TRUE.equals(c.getVip())` | `e0.vip = :p1` | |
| `!Boolean.TRUE.equals(c.getVip())` | `e0.vip <> :p1` | NULL rows are **not** returned (SQL meaning) |
| `"x".equals(o.getDescription())` | `e0.description = 'x'` | Constant receiver: it cannot be NULL, so a simple `=` |
| `Objects.requireNonNullElse(o.getTotal(), 0.0)` in a `set` value | `coalesce(e0.total, 0.0)` | See [7. Update, insert, delete](07-dml.md) |

Comparing with `!=`:

<!-- region: ManualJavaNotesTest.java#not-equals -->
```java
SelectQuery<Order> notOne = qb.from(Order.class).where(o -> o.getCnt() != 1);
assertEquals("select e0 from Order e0 where e0.cnt <> 1", notOne.toHql().hql());              // no NULL rows
SelectQuery<Order> notEquals = qb.from(Order.class).where(o -> !Objects.equals(o.getCnt(), 1));
assertEquals("select e0 from Order e0 where e0.cnt <> 1", notEquals.toHql().hql());           // no NULL rows either
SelectQuery<Order> withNull = qb.from(Order.class)
        .where(o -> o.getCnt() == null || !Objects.equals(o.getCnt(), 1));
assertEquals("select e0 from Order e0 where e0.cnt is null or e0.cnt <> 1", withNull.toHql().hql());  // NULL rows too
```

Example code: [`ManualJavaNotesTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaNotesTest.java) (`not-equals`).

<a id="96-small-types-char-short-byte-and-boolean"></a>
## 9.6 Small types: char, short, byte and boolean

The JVM has no separate compare instructions for `char`, `short`, `byte` and `boolean`. javac compiles them as `int`.
lambdaQL knows this.

- Comparisons of `long`, `int`, `short`, `byte`, `char`, `float` and `double` values are supported.
- Constants are converted to the type of the column. For example, the `int` constant `10` becomes a `short` value for a `short` column.
- A `boolean` getter is a condition on its own: `o.isActive()` becomes `e0.active = true`.
- In a comparison of a boolean column with a condition, `o.isActive() == (o.getStatus() > s)`, the condition side becomes `case when`. If you need an index, split it with `&&` and `||` (see [3.7](03-where.md#37-boolean-columns)).
- Primitive properties (`boolean active`, `int qty`) are treated as "cannot be NULL", like `@Id` or `nullable = false` columns. So lambdaQL can leave out null checks on these properties, and `o.getId() == null` becomes `1=0` ([3.6.2](03-where.md#362--on-objects-compares-values)).

<a id="97-float-double-and-bigdecimal"></a>
## 9.7 float, double and BigDecimal

| Topic | Rule |
|---|---|
| `float` / `double` constants (`100.0`, `1.5f`) | Written into the HQL as literals, like other constants in the lambda expression (`100.0`, `1.5F`). A `BigDecimal` built in the lambda expression (`new BigDecimal("1.5")`) is a computed value, so it is a bind parameter |
| Results the same down to the bit | **Not promised**. Each database stores and compares `float` and `double` differently. lambdaQL does not add `CAST`. Choose the column mapping yourself (for example, Oracle `binary_float` / `binary_double`) |
| Captured `NaN`, `Infinity` | **Exception** when the query runs |
| `NaN` stored in the database | Follows the database (H2 sorts it as the largest value, MariaDB cannot store it) |
| `BigDecimal` equality | Java `equals` compares the scale (`1.0` is not `1.00`). lambdaQL compares the **value** |
| Integer `/` | The result differs by database (MariaDB `5/2 = 2.5`). The final rule is not decided. Avoid it |

<a id="98-lambdas-in-instance-methods-capture-this"></a>
## 9.8 Lambda expressions in instance methods capture this

When a lambda expression in an instance method uses a field, javac makes the lambda expression body an **instance method** and captures `this`.

<!-- region: ManualJavaNotesTest.java#this-capture -->
```java
@Service
public static class OrderService {

    private final QueryBuilder qb;
    private int defaultStatus = 1;

    public OrderService(QueryBuilder qb) {
        this.qb = qb;
    }

    public List<Order> defaults() {
        return qb.from(Order.class)
                 .where(o -> o.getStatus() == defaultStatus)   // means this.defaultStatus
                 .toList();
    }
}
```

**SQL by database**

<!-- sql: this-capture -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=?
```

What lambdaQL does:

- It knows that the first captured value is `this`.
- `this.defaultStatus` does not use the entity, so it is computed in Java and bound as a parameter.
- The field is read **each time the query runs**. The analysis result is cached per lambda expression, not per object.
- The class does not need to be `Serializable`.
- A method call on `this` that does not use the entity (`this.minPrice()`) is also computed in Java each time the query runs. Keep it free of side effects.

Local variables are different. Java copies their values when the lambda expression is created. Java requires local variables to be effectively final.

<!-- region: ManualJavaNotesTest.java#local-copy -->
```java
int st = defaultStatus;                                   // copied now
SelectQuery<Order> q = qb.from(Order.class).where(o -> o.getStatus() == st);
// the same HQL as the lambda that reads the field
assertEquals("select e0 from Order e0 where e0.status = :p1", q.toHql().hql());
assertEquals(q.toHql().hql(), qb.from(Order.class).where(o -> o.getStatus() == defaultStatus).toHql().hql());
```

Both forms build the same HQL.

Example code: [`ManualJavaNotesTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaNotesTest.java) (`this-capture`, `local-copy`).

<a id="99-method-references"></a>
## 9.9 Method references

| Method reference | Same meaning | Status |
|---|---|---|
| `where(Order::isActive)` | `where(o -> o.isActive())` | **Rejected** (`LQL-1009`). Use a lambda expression |
| `orderBy(Order::getPrice)` | `orderBy(o -> o.getPrice())` | Implemented |
| `orderBy(Order::getPrice, Direction.DESC)` | `orderBy(o -> o.getPrice(), Direction.DESC)` | Implemented |
| `SortSpec.of(Order.class, Order::getPrice)` | `SortSpec.of(Order.class, o -> o.getPrice())` | Implemented. Usable in every sort form that takes one key |

lambdaQL detects a method reference when it loads it. It does not read the body of the referenced method. For a sort key, it builds the `o.getPrice()` call itself. In a condition, it rejects it.

<!-- region: ManualJavaNotesTest.java#method-ref -->
```java
// a method reference as a sort key: the same as o -> o.getPrice()
assertEquals("select e0 from Order e0 order by e0.price asc",
        qb.from(Order.class).orderBy(Order::getPrice).toHql().hql());
// a method reference as a condition is rejected: write a lambda
assertEquals("LQL-1009", assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(Order::isActive)).code());
```

Some method references compile but have a wrong meaning. lambdaQL rejects them at analysis time:

- an instance method with one argument used as a lambda expression with two arguments (the second argument becomes the method argument),
- a static method with two arguments used as a lambda expression with two arguments.

In `orderBy`, only getters of the entity are allowed. Static methods, methods with arguments and references bound to an object are rejected.

If it is not a simple getter, use a lambda expression.

Example code: [`ManualJavaNotesTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaNotesTest.java) (`method-ref`).

<a id="910-static-helpers-you-should-not-expect"></a>
## 9.10 Static helpers you should not expect

The static helper classes of lambdaQL, `By` (sort functions), `Agg` (aggregates) and `Dml` (value assignment), are used only inside lambda expressions.
The subquery factory comes in as a **lambda expression argument** (`sub`). Everything else is written in normal Java.

| What you might expect | The lambdaQL way |
|---|---|
| `orderBy(asc(...), desc(...))` | `orderBy(o -> asc(...).desc(...))`: chain many keys in one lambda expression ([4](04-order-paging.md)) |
| `exists(...)`, `in(...)`, `notIn(...)` | `(o, sub) -> sub.from(X.class).where(...).exists()` / `.select(...).contains(x)` ([5](05-subquery.md)) |
| `set(path, value)` | `set(o -> o.setX(v))` or `set(o -> value(o.getX(), v))` (`Dml.value`) ([7](07-dml.md)) |
| Q-classes, `Order_.price` | Getters: `o.getPrice()` |
| `count()`, `sum(...)`, ... | `Agg.count()`, `Agg.sum(...)` ([6](06-select-group.md)) |
| `coalesce(a, b)` | `Objects.requireNonNullElse(a, b)` in a `set` value |
| `like(...)`, `lower(...)`, `upper(...)` | Not supported (string functions on columns) |
| `currentTimestamp()` | `LocalDateTime.now()`. Computed in the application and bound |
| Your own static utility methods on entity values | **Rejected**. DB functions with `@SqlFunction` are the exception ([8](08-procedures-functions.md)) |

Static methods that use **only captured values** are fine. They run in Java each time the query runs.

<!-- region: ManualJavaNotesTest.java#static-helper -->
```java
// a static method on captured values only: computed in Java when the query runs
SelectQuery<Order> q = qb.from(Order.class)
        .where(o -> o.getUpdateAt().compareTo(LocalDateTime.of(2026, 1, 1, 0, 0)) > 0);
assertEquals("select e0 from Order e0 where e0.updateAt > :p1", q.toHql().hql());
// your own static method on an entity value: rejected
assertEquals("LQL-3008", assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(o -> normalize(o.getDescription()) == text)).code());
```

Example code: [`ManualJavaNotesTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaNotesTest.java) (`static-helper`).

<a id="911-other-java-forms"></a>
## 9.11 Other Java forms

| Java form | Inside a lambdaQL lambda expression |
|---|---|
| One expression `o -> ...` | The normal form |
| A block lambda expression with local variables `o -> { ...; return ...; }` | Rejected in `where`, `orderBy` and `select` |
| A block lambda expression with only setter calls | Allowed in `set`, `values` and `insert ... select` ([7](07-dml.md)) |
| `if`, `for`, `while`, `switch` (statement or expression), `try` | Rejected |
| Ternary operator `c ? x : y` | Only `c ? x : false`, `c ? true : x`, `c ? false : true` ([3.5](03-where.md#35-and-or-not)) |
| `&`, `\|`, `^` on booleans | Rejected. Use `&&`, `\|\|` |
| Streams, nested lambda expressions (except `sub` subqueries) | Rejected |
| `List.of(...)` in a lambda expression | Rejected, except as `groupBy` keys |
| `compareTo(...) < 0` | Becomes `<`, `>`. `isBefore` and `isAfter` of `LocalDateTime` are rejected (`LQL-3008`) |
| `captured.length` (an array as a captured value) | Computed in Java |
| Records | As result types (`select`), and as row types of derived tables and CTEs (read with `r.total()`) |
| `var` | Can be used for builder variables. The types are `SelectQuery<T>`, `OrderedSelectQuery<T>`, `ValueQuery<R>` and so on |
| Generic `value(o.getStatus(), "abc")` | Compiles in Java. Compared with the column type at analysis time, error `LQL-3065` |
| `value(...)` inside a class with its own `value(...)` method (for example, a record with a `value` component) | The static import is hidden. Write `Dml.value(...)` |
| The same parameter name in nested lambda expressions | Java does not allow it. Use `sub2`, `i`, `x` |

<a id="912-compiler-and-build"></a>
## 9.12 Compiler and build

| Topic | What to know |
|---|---|
| Compiler | lambdaQL works on **javac** output. Lambda expressions compiled with **ECJ** (the Eclipse compiler) are not guaranteed. The Eclipse IDE and other tools based on Eclipse JDT use ECJ. Run tests with Gradle or Maven (javac) |
| JDK version | Java 21 or later |
| Reading class files (ASM) | On Java 21 to 23, class files are read with ASM 9.11.1 (up to Java 28 class files). If you compile with a newer JDK, update ASM. lambdaQL does not shade ASM, so you can update it yourself. On Java 24 or later, lambdaQL reads with the `java.lang.classfile` of the running JDK, so the ASM version does not matter. The lambdaQL jar is a Multi-Release JAR, and the JVM picks the part that matches the running version |
| `-parameters` | Recommended. lambdaQL can check procedure argument names ([8.4](08-procedures-functions.md#84-warning-argument-order)) |
| Debug information | Keep line numbers (the javac default). lambdaQL puts the source line in error messages |
| IntelliJ "Number objects compared using `==`" | Inside a lambdaQL lambda expression, this is the intended behavior. Declare `@SuppressWarnings("NumberEquality")` on the method to remove the warning |
| IntelliJ warning for string `==` | Inside a lambdaQL lambda expression, this is also the intended behavior |

---

[Java track](README.md) | Previous: [8. Procedures and DB functions](08-procedures-functions.md) | Next: [10. Rules and limits](10-rules-and-limits.md) | [Kotlin version of this page](../kotlin/09-kotlin-notes.md)
