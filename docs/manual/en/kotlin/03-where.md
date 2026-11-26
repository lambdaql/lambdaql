<a id="3-conditions-where-kotlin"></a>
# 3. Conditions (`where`) (Kotlin)

> [!IMPORTANT]
> This page is older than the Korean manual. See the [Korean page](../../ko/kotlin/03-where.md) for the latest rules.

[Kotlin track](README.md) | [Manual home](../README.md) | Previous: [2. Getting started](02-getting-started.md) | Next: [4. Sorting and paging](04-order-paging.md) | [Java version of this page](../java/03-where.md)

Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md)

---

<a id="31-the-idea-in-one-sentence"></a>
## 3.1 Overview

**A lambdaQL lambda is a query expression, not code.** lambdaQL does not call the lambda. It reads the bytecode opcodes.
So the Kotlin run-time rules (object identity for `===`, null checks that the compiler adds) do not apply to the parts that use entities.
An expression made only of captured values is computed in Kotlin first (see [3.11](#311-captured-values)).
lambdaQL uses its own rules. This chapter explains them.

If lambdaQL cannot read a lambda as a query, it throws an exception with a code (see [3.13](#313-what-is-rejected)).

<a id="32-example-entities"></a>
## 3.2 Example entities

All examples use the example entities (Java classes). The details are in [2. Getting started](02-getting-started.md#example-entities).

| Entity | Properties used in this chapter (Java types) |
|---|---|
| `Order` | `Long id`, `double price`, `short status`, `boolean active` (`isActive` in Kotlin), `String description`, `Integer cnt`, `String custName`, `LocalDateTime updateAt`, `Kind kind`, `Customer customer`, `Address address` |
| `Customer` | `int id`, `String name`, `Integer grade` |
| `Item` | `Long id`, `Order order`, `Double price`, `int qty` |
| `Coded` | `Long id`, `String code` (stored reversed by `@Convert`), `Boolean vip` (can be NULL, `YesNoConverter`) |

`price`, `status`, `active` and `qty` are primitive types, so they can never be NULL. Properties of reference types can be NULL, and in Kotlin they are platform types.
`status` is a `Short`, so the examples write `it.status.toInt() == 1`. lambdaQL ignores this conversion.

<a id="33-basic-form"></a>
## 3.3 Basic form

<!-- region: ManualKotlinStartTest.kt#first-query -->
```kotlin
val q = qb.from(Order::class.java).where { it.status.toInt() == 1 && it.price > 100.0 }
assertEquals("select e0 from Order e0 where e0.status = 1 and e0.price > 100.0", q.toHql().hql())
val orders: List<Order> = q.toList()
```

`qb` is a `QueryBuilder` (see [2. Getting started](02-getting-started.md#create-a-querybuilder)).

**SQL by database**

<!-- sql: to-hql -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>100.0
```

<!-- region: KotlinSelectTest.kt#captured -->
```kotlin
val min = 100.0
val q = qb.from(Order::class.java).where { it.price > min && it.status.toInt() == 1 }
assertEquals("select e0 from Order e0 where e0.price > :p1 and e0.status = 1", q.toHql().hql())
```

`min` is a captured local `val`, so it becomes a bind parameter. `price` is a primitive `double`, so it does not need `!!`.
The same query written in Java is in the [Java version of this page](../java/03-where.md).

You can call `where()` more than once. The conditions are joined with `AND`.

<!-- region: ManualKotlinWhereTest.kt#many-where -->
```kotlin
val q = qb.from(Order::class.java)
    .where { it.status.toInt() == 1 }
    .where { it.isActive }
assertEquals("select e0 from Order e0 where e0.status = 1 and e0.active = true", q.toHql().hql())
```

**SQL by database**

<!-- sql: many-where -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.active=true
```

**Values in the HQL.** The rule depends on where the value comes from.

| Value | In the HQL | Example |
|---|---|---|
| A constant written in the lambda: a number (also with decimals), a string, `true`/`false`, `null`, an enum constant, a `const val` | **Literal** | `it.price > 100.0` → `e0.price > 100.0` |
| A captured value: a local `val` or `var`, a property, a function parameter | **Bind parameter** | `it.price > minPrice` → `e0.price > :p1` |
| A computed value: `BigDecimal("1.5")`, `form.text.trim()`, `LocalDateTime.of(...)` | **Bind parameter** | `:p1` |

- A constant is the same on every call. So the SQL statement does not change, and the database can see the value when it makes the query plan.
- A single quote in a string constant is escaped: `"O'Brien"` becomes `'O''Brien'`. This string is a constant compiled into the class, not user input, so there is no SQL injection risk.
- A captured value can come from a user, so it is a bind parameter. Only the value changes on each call.
- There is one exception. A captured decimal value compared with an integer column is written as a decimal literal (`2.5BD`) (see "Integer columns and decimal values" in [3.4](#34-comparisons)). Only the value turned into a number is written, so there is no SQL injection risk.
- An enum constant is written with its full class name. Example: `e0.kind = org.lambdaql.query.Order$Kind.RUSH`.

**`!!` is for the compiler.** If a property of a Kotlin entity is nullable, such as `Double?`, Kotlin does not compile `it.price > 100.0`, so you write `it.price!! > 100.0`. lambdaQL ignores `!!`. Rows whose value is NULL are not returned.

<!-- region: ManualKotlinWhereTest.kt#bang -->
```kotlin
// !! is only for the compiler: lambdaQL ignores it, and rows with a NULL cnt are not returned
val q = qb.from(Order::class.java).where { it.cnt!! > 1 }
assertEquals("select e0 from Order e0 where e0.cnt > 1", q.toHql().hql())
```

Example code: [`ManualKotlinStartTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinStartTest.kt) (`first-query`), [`KotlinSelectTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinSelectTest.kt) (`captured`), [`ManualKotlinWhereTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt) (`many-where`, `bang`).

<a id="34-comparisons"></a>
## 3.4 Comparisons

| Kotlin | HQL | Notes |
|---|---|---|
| `it.status == 1` | `e0.status = 1` | |
| `it.status != 1` | `e0.status <> 1` | NULL rows are not returned (see [3.6](#36-null-rules)) |
| `it.price!! > p` | `e0.price > :p1` | Also `<`, `<=`, `>=` |
| `it.updateAt!! > since` | `e0.updateAt > :p1` | In Kotlin, dates are compared with `<` and `>` |
| `it.updateAt!!.isAfter(since)` | `e0.updateAt > :p1` | `isBefore` is `<` |
| `it.description!! < "m"` | `e0.description < 'm'` | Also `BigDecimal`, `LocalDate`, `BigInteger` |
| `it.status!!.compareTo(3) > 0` | `e0.status > 3` | `compareTo(..) op 0` becomes `op` |

A comparison with a nullable column follows the **SQL meaning**.
Rows where the column is NULL are not returned.

**Arithmetic.** `+`, `-`, `*`, `/` and unary `-` are supported. `%` is written as `mod`.
You can use `/`, but avoid integer `/` that involves a column. It is computed in SQL, so the result differs by database (MariaDB gives `5/2 = 2.5`, Kotlin gives `2`). A rule that makes the result of `/` on the column side match is not decided yet.
Arithmetic between captured values (`n / 2.0` below) is done before the query, so it does not have this difference.

In the examples below, `cnt` is an integer column of `Order` (`Integer cnt`).

**Arithmetic on a column and indexes**. If you put arithmetic on a column, as in `it.cnt * 2 > n`, the index cannot be used. lambdaQL logs `LQL-5012` at `WARN` level, and with `SargableMode.STRICT` the analysis fails (`LQL-3066`).
When you move the arithmetic to the value side, divide by a decimal, as in `it.cnt > n / 2.0`. The integer division `n / 2` gives different rows. If you multiplied by a negative number, turn the comparison around. For details, see [10.2](10-rules-and-limits.md#db-functions-on-a-column).

**Integer columns and decimal values**. When you compare an integer column with a decimal value, lambdaQL compares the value as written. It does not round the value or drop it.

- A constant written in the lambda stays a literal. `it.cnt >= 2.5` becomes `e0.cnt >= 2.5`.
- A captured value is also written as a decimal literal. It becomes `e0.cnt >= 2.5BD`, with no bind parameter. `BD` is the HQL suffix for a `BigDecimal` literal.
- An IN list with such an element writes all elements as decimal literals (`e0.cnt in (2.5BD, 3.0BD)`).

This is an exception to the principle that captured values go as bind parameters. Hibernate 7 rejects a decimal bound to a parameter compared with an integer column, even if you give the `BigDecimal` type (`QueryArgumentException`, measured).

Before, the value was cut to the column type, so `2.5` became `2`, and wrong rows were returned. Hibernate 6.6 rejects a decimal bound with the integer column type (`CoercionException`), and it quietly cuts the elements of an IN list (measured on three databases).
Turning it into an integer comparison (`>= 3`) was not chosen. The SQL that runs would be different from the lambda, and if the entity mapping (`Integer`) and the database column type (for example, a `DECIMAL` with decimals) differ, it gives wrong rows.

The results are correct on H2, MariaDB 10.11 and Oracle 23.26 (measured). On all three databases, the SQL keeps the column as it is and compares it with a decimal, as in `cnt>=2.5` and `cnt in (2.5,3.0)`.
MariaDB's `EXPLAIN` shows an index range scan (`type=range`). On Oracle, the column has no conversion, so the form can use an index. With an index hint it is an `INDEX RANGE SCAN`. On a 3000-row table without a hint, it picks a full scan, the same as for an integer literal (`cnt >= 99`) (all measured).

<!-- region: ManualKotlinWhereTest.kt#whole-number -->
```kotlin
// cnt is an integer column: a decimal value is compared as it is, not cut to 2
val literal = qb.from(Order::class.java).where { it.cnt >= 2.5 }
assertEquals("select e0 from Order e0 where e0.cnt >= 2.5", literal.toHql().hql())

val v = 2.5
val captured = qb.from(Order::class.java).where { it.cnt >= v }
assertEquals("select e0 from Order e0 where e0.cnt >= 2.5BD", captured.toHql().hql())
```

**Floating point:** for `Float`/`Double` columns, lambdaQL does not promise results that are the same as Kotlin down to the bit.
A captured `NaN` or `Infinity` value is rejected when the query runs.

Example code: [`ManualKotlinWhereTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt) (`whole-number`).

<a id="35-and-or-not"></a>
## 3.5 AND, OR, NOT

| Kotlin | HQL |
|---|---|
| `a && b` | `a and b` |
| `a \|\| b` | `a or b` |
| `!a` | `not (a)` |
| Parentheses | Kept as needed |

<!-- region: ManualKotlinWhereTest.kt#and-or-not -->
```kotlin
val both = qb.from(Order::class.java).where { (it.status.toInt() == 1 || it.isActive) && it.price > minPrice }
assertEquals("select e0 from Order e0 where (e0.status = 1 or e0.active = true) and e0.price > :p1", both.toHql().hql())

// the compiler moves the ! inside (De Morgan): the rows are the same
val not = qb.from(Order::class.java).where { !(it.status.toInt() == 1 && it.price > minPrice) }
assertEquals("select e0 from Order e0 where e0.status <> 1 or e0.price <= :p1", not.toHql().hql())
```

Rules:

- The result follows **SQL three-valued logic** (TRUE / FALSE / UNKNOWN). lambdaQL checked that this returns the same rows on H2, MariaDB and Oracle.
- The compiler sometimes moves `!` (De Morgan, `!(x > y)` to `x <= y`). The result still does not change.
- lambdaQL **does not simplify conditions**. If you write `it.price!! > p || it.price!! <= p`, it sends it as written. Rows where `price` is NULL are not returned. This is normal SQL behavior.
- Use `&&` and `||`. The infix functions `and`, `or` and `xor` are rejected (they do not short-circuit).

**`if` and `when` that give `true`/`false`.** Only these short forms are allowed. They are the same as `AND` and `OR`.

| Kotlin | Same meaning |
|---|---|
| `if (c) x else false` | `c && x` |
| `if (c) true else x` | `c \|\| x` |
| `when { a -> true; b -> true; else -> false }` | `a \|\| b` |

<!-- region: ManualKotlinWhereTest.kt#if-when -->
```kotlin
val ifAnd = qb.from(Order::class.java).where { if (it.isActive) it.price > 100.0 else false }
assertEquals("select e0 from Order e0 where e0.active = true and e0.price > 100.0", ifAnd.toHql().hql())

val ifOr = qb.from(Order::class.java).where { if (it.isActive) true else it.price > 100.0 }
assertEquals("select e0 from Order e0 where e0.active = true or e0.price > 100.0", ifOr.toHql().hql())

val whenOr = qb.from(Order::class.java).where { when { it.status.toInt() == 3 -> true; it.price > 200.0 -> true; else -> false } }
assertEquals("select e0 from Order e0 where e0.status = 3 or e0.price > 200.0", whenOr.toHql().hql())
```

An `if` that picks a value only from captured values and constants (`it.status.toInt() == (if (flag) 1 else 2)`) is computed in Kotlin when the query runs, and it is passed as a bind parameter.
An `if` or `when` that picks a value from entity values becomes an SQL `case when … end` (see [3.5.1](#351-values-chosen-by-a-condition)).
Any other boolean `if (c) x else y` is rejected.

<!-- region: ManualKotlinWhereTest.kt#value-before -->
```kotlin
// an if of captured values and constants: computed in Kotlin, bound as :p1
val q = qb.from(Order::class.java).where { it.status.toInt() == (if (flag) 1 else 2) }
assertEquals("select e0 from Order e0 where e0.status = :p1", q.toHql().hql())

// an if that reads the entity becomes case when
val c = qb.from(Order::class.java).where { it.status.toInt() == (if (it.isActive) 1 else 2) }
assertEquals("select e0 from Order e0 where e0.status = case when e0.active = true then 1 else 2 end", c.toHql().hql())
```

Example code: [`ManualKotlinWhereTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt) (`and-or-not`, `if-when`, `value-before`).

<a id="351-values-chosen-by-a-condition"></a>
### 3.5.1 Values chosen by a condition (`case when`)

An `if (c) x else y`, an `if … else if … else` or a `when { }` without a subject that picks a value from entity values becomes one SQL `case when … end` expression.
You can mix `&&`, `||` and `!` in the conditions.

You can use `case` as a compared value in `where`, a selected value in `select`, a `groupBy` key, a sort key, and an assigned value in `update`.

<!-- region: KotlinCaseWhenTest.kt#case-where -->
```kotlin
val q = qb.from(Order::class.java).where { it.status.toInt() == if (it.isActive) 1 else 2 }
assertEquals("select e0 from Order e0 where e0.status = case when e0.active = true then 1 else 2 end", q.toHql().hql())
```

<!-- region: KotlinCaseWhenTest.kt#case-when-select -->
```kotlin
val q = qb.from(Order::class.java).select {
    when {
        it.price > 200.0 -> "big"
        it.price > 100.0 -> "medium"
        else -> "small"
    }
}
assertEquals("select case when e0.price > 200.0 then 'big' when e0.price > 100.0 then 'medium' else 'small' end from Order e0",
    q.toHql().hql())
```

<!-- region: KotlinCaseWhenTest.kt#case-group -->
```kotlin
// orders per price band: the when is the group key and the selected value
val q = qb.from(Order::class.java)
    .groupBy { if (it.price > 100.0) "big" else "small" }
    .select { if (it.price > 100.0) "big" else "small" }
assertEquals("select case when e0.price > 100.0 then 'big' else 'small' end from Order e0"
    + " group by case when e0.price > 100.0 then 'big' else 'small' end", q.toHql().hql())
```

<!-- region: KotlinCaseWhenTest.kt#case-sort -->
```kotlin
// active orders first, then by price
val q = qb.from(Order::class.java).orderBy { o -> asc(if (o.isActive) 0 else 1).asc(o.price) }.limit(10)
assertEquals("select e0 from Order e0 order by case when e0.active = true then 0 else 1 end asc, e0.price asc, e0.id asc",
    q.toHql().hql())
```

<!-- region: KotlinCaseWhenTest.kt#case-set -->
```kotlin
// 10 % off the orders above 100
val u = qb.update(Order::class.java).set { it.price = if (it.price > 100.0) it.price * 0.9 else it.price }.all()
assertEquals("update Order e0 set e0.price = case when e0.price > 100.0 then (e0.price * 0.9) else e0.price end", u.toHql().hql())
```

Rules:

- `case` checks the branches in order from the top. So the condition of the second and later branches is the condition as written in the lambda.
- A `case` compared in `where` is computed for each row, so the database cannot use the index of a column inside it. lambdaQL logs `LQL-5013` at `WARN` level in this case.
- A form that picks the sort function chain itself by a condition (`if (flag) desc(o.price) else asc(o.id)`) is rejected (`LQL-2023`). Write the condition inside the key value.
- A `when (x)` with a subject follows the rules in [3.15](#315-when-kotlin-only). `takeIf` is not supported.

Example code: [`KotlinCaseWhenTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinCaseWhenTest.kt) (`case-where`, `case-when-select`, `case-group`, `case-sort`, `case-set`).

<a id="36-null-rules"></a>
## 3.6 NULL rules

This is the most important part. There are three simple principles.

1. **Where you write a null check, lambdaQL uses it.** (`== null`, `!= null`, `?.`, `?:`)
2. **Where you do not write one, lambdaQL uses the SQL meaning.** If the column is NULL, the comparison is UNKNOWN and the row is not returned.
   The same rule applies to `==` and `!=` between entity values.
   If the result can differ from Kotlin, lambdaQL logs `LQL-5009` at `DEBUG` level.
3. **Captured values are computed first, and the comparison also follows the SQL meaning.** A captured `null` is bound as NULL, so `= :p1` and `<> :p1` match no row.
   In this case lambdaQL logs `LQL-4045` at `WARN` level when the query runs. To find NULL, write `x == null` (it becomes `is null`).
   An expression that calls a method on a captured value (`text.trim()`, `min.toInt()` that turns it into another primitive type, and so on) is computed first, so if the value is `null`, it throws an exception (`LQL-4012`), as in Kotlin.
   `!!` on a captured value and unboxing to its own primitive type (`min!!`) are not run. If the value is `null`, NULL is bound and `LQL-4045` is logged at `DEBUG` level.

The Kotlin compiler adds **hidden** null checks to the bytecode opcodes (for example, `it.id == id` compared with a captured `id: Long`). Only the null checks in the source code have meaning. See [3.6.2](#362--compares-values).

<a id="361-rule-table"></a>
### 3.6.1 Rule table

| # | Kotlin form | HQL | NULL behavior |
|---|---|---|---|
| R12 | A column that can never be NULL and a non-null value: `it.id == id` (`id: Long`) | `e0.id = :p1` | The null check that the compiler added is removed |
| R1 | Ordering comparison: `it.price > p`, `it.cnt!! > 1` | `e0.price > :p1`, `e0.cnt > 1` | SQL meaning |
| R11 | A nullable column and a nullable captured value: `it.cnt == n` (`n: Int?`), `it.description == text` (`String?`), enum `==` | `e0.cnt = :p1` | Value comparison. If the column is NULL, the row is not returned. Captured `null`: no row matches (`LQL-4045`) |
| R11 | `it.cnt != c` | `e0.cnt <> :p1` | SQL meaning. If the column is NULL, the row is **not** returned (Kotlin would return it). Captured `null`: no row matches (`LQL-4045`) |
| R11 | Two entity values: `it.description == it.custName` | `e0.description = e0.custName` | SQL meaning. If either side is NULL, the row is not returned (also NULL and NULL) |
| R3 | `x == null`, `x != null` | `x is null`, `x is not null` | |
| R17 | `===`, `!==` | Same as `==`, `!=` | Value comparison, not object identity |
| R9 | Captured value | Bind parameter | See [3.6.4](#364-captured-null-values) |

<a id="362--compares-values"></a>
### 3.6.2 `==` compares values

In Kotlin, `==` compares values anyway. In lambdaQL, `===` also compares **values**.
A query has no object identity. Two different `Long` boxes with the same value are equal.

<!-- region: ManualKotlinWhereTest.kt#captured-eq -->
```kotlin
val n: Int? = 3
val q = qb.from(Order::class.java).where { it.cnt == n }
assertEquals("select e0 from Order e0 where e0.cnt = :p1", q.toHql().hql())

// a captured null matches no row: "= NULL" is never true (WARN LQL-4045)
val none: Int? = null
assertEquals(0, qb.from(Order::class.java).where { it.cnt == none }.toList().size)
```

To find rows where `cnt` is NULL, write a null check. `it.cnt == null` becomes `e0.cnt is null`.

The column is not wrapped in a function, so the database can use an index (see [10.2](10-rules-and-limits.md#102-sargable-rule-index-friendly-sql)).

<!-- region: ManualKotlinWhereTest.kt#string-eq -->
```kotlin
val eq = qb.from(Order::class.java).where { it.description == text }
assertEquals("select e0 from Order e0 where e0.description = :p1", eq.toHql().hql())

// a NULL description matches neither = nor <> (SQL meaning)
val ne = qb.from(Order::class.java).where { it.description != text }
assertEquals("select e0 from Order e0 where e0.description <> :p1", ne.toHql().hql())
```

**A column that can never be NULL: `it.id == id`.** The `id` of the example entity is a `Long`, and in Kotlin it is a platform type. Kotlin compiles `it.id == id` (captured `id: Long`) into a null check and a comparison.

If lambdaQL copied these bytecode opcodes as they are, it would get `not (e0.id is null or e0.id <> :p1)`. The result is correct, but it is hard for the database to use the index well.
An id can never be NULL, so `is null` on the id is always false. lambdaQL removes this check and writes `e0.id = :p1`.

The same rule applies to primitive properties and `nullable = false` columns. But it applies **only when the path has no association step**. There are orders without a customer, so `it.customer?.id` can be NULL, and the null check on this path is kept.

<!-- region: ManualKotlinWhereTest.kt#never-null -->
```kotlin
// the id can never be NULL: the null check that Kotlin adds is dropped
val byId = qb.from(Order::class.java).where { it.id == id }
assertEquals("select e0 from Order e0 where e0.id = :p1", byId.toHql().hql())

val noId = qb.from(Order::class.java).where { it.id == null }
assertEquals("select e0 from Order e0 where 1=0", noId.toHql().hql())

// a path through an association can be NULL: the check stays
val noCustomerId = qb.from(Order::class.java).where { it.customer?.id == null }
assertEquals("select e0 from Order e0 where e0.customer.id is null", noCustomerId.toHql().hql())
```

> [!WARNING]
> **A Kotlin non-null type is not proof.** A `var code: String` property can also hold a NULL that came from the database.
> This is because Hibernate writes the column value to the field without a check.
> lambdaQL looks at the JPA metadata, not at the Kotlin type.

<a id="363-write-your-null-checks-yourself"></a>
### 3.6.3 Write your null checks yourself

In Kotlin, `null != 3` is `true`. In lambdaQL it is different. `it.cnt != c` below does **not** return rows where `cnt` is NULL, because you did not write a null check.

<!-- region: ManualKotlinWhereTest.kt#null-check -->
```kotlin
val c: Int? = 3
// rows with a NULL cnt are left out (SQL meaning); Kotlin would keep them
val ne = qb.from(Order::class.java).where { it.cnt != c }
assertEquals("select e0 from Order e0 where e0.cnt <> :p1", ne.toHql().hql())

// to keep the NULL rows, write the null check
val keep = qb.from(Order::class.java).where { it.cnt == null || it.cnt != c }
assertEquals("select e0 from Order e0 where e0.cnt is null or e0.cnt <> :p1", keep.toHql().hql())
```

> [!IMPORTANT]
> This is different from running Kotlin code. To keep NULL rows, write the null check yourself, as in `it.cnt == null || it.cnt != c`.

When such a difference can happen, lambdaQL logs `LQL-5009` at `DEBUG` level, once per condition.

`it.description.equals(text)` on a `String?` is the same as `==`.

**SQL meaning for entity values**. A comparison of two entity values and a comparison of a `Boolean` that can be NULL also follow the SQL meaning.

<!-- region: ManualKotlinWhereTest.kt#sql-meaning -->
```kotlin
// two entity values: = as in SQL; a row with a NULL on either side does not match
val same = qb.from(Order::class.java).where { it.description == it.custName }
assertEquals("select e0 from Order e0 where e0.description = e0.custName", same.toHql().hql())

// to keep the NULL rows, say so in the lambda
val keep = qb.from(Order::class.java).where { it.description == null || it.description != text }
assertEquals("select e0 from Order e0 where e0.description is null or e0.description <> :p1", keep.toHql().hql())

// a NULL Boolean is not false
val notVip = qb.from(Coded::class.java).where { it.vip == false }
assertEquals("select e0 from Coded e0 where e0.vip = false", notVip.toHql().hql())
```

Details on `?.`, `?:` and `!!`: [9. Kotlin notes](09-kotlin-notes.md).

<a id="364-captured-null-values"></a>
### 3.6.4 Captured null values

| Form | When the captured value is `null` |
|---|---|
| `==`, `!=` with a captured value: `it.cnt == n`, `it.description != text` | Runs: `= :p1` / `<> :p1` with NULL bound, so **no** row matches. `LQL-4045` at `WARN` level |
| Ordering comparison: `it.price!! > min!!`, all of `<`, `<=`, `>`, `>=` | Runs: NULL bound to `e0.price > :p1`, **no** row matches. `LQL-4045` at `DEBUG` level. `min!!` and unboxing to its own primitive type are not run |
| A method call on a captured value: `text.trim()`, `min.toInt()` that turns it into another primitive type | **Exception** when the query runs (`LQL-4012`), because it is computed first |
| A null element in a collection used with `in` | **Exception** when the query runs |

Why: a comparison inside a lambda follows the SQL meaning. In SQL, `=` and `<>` with NULL are never true.
lambdaQL can know if a captured value is null only when the query runs, so it logs the warning when the query runs.

An expression that calls a method on a captured value is a value computed before the query. This covers `text.trim()` and `min.toInt()` that turns it into another primitive type. If the value is `null`, it throws an exception (`LQL-4012`), as in Kotlin.
`!!` on a captured value and unboxing to its own primitive type are not run, and the boxed value is bound. If the value is `null`, no row matches and `LQL-4045` is logged at `DEBUG` level.
To find NULL rows, write `x == null`.

**Skip a filter with a null guard.** If you null-check the same captured value, the condition disappears with no filter when the value is null.

<!-- region: ManualKotlinWhereTest.kt#captured-null-guard -->
```kotlin
fun byMinPrice(min: Double?) = qb.from(Order::class.java).where { min == null || it.price > min }

val all = byMinPrice(null)
assertEquals("select e0 from Order e0", all.toHql().hql())               // no filter
val some = byMinPrice(100.0)
assertEquals("select e0 from Order e0 where e0.price > :p1", some.toHql().hql())
```

**SQL by database**

<!-- sql: captured-null-guard -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
```

**SQL by database**

<!-- sql: captured-null-guard#2 -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.price>?
```

How it works:

- The parts that use only captured values and constants are decided in the application when the query runs. They are handled from left to right, with short-circuit evaluation, as in Kotlin.
- If `min == null`, the guard is true, so the whole condition is true. There is no filter. The comparison with `min` is not written into the HQL (Kotlin does not run this comparison either).
- If `min = 100.0`, the guard is false, so only `e0.price > :p1` stays.
- The lambda is still read only once. Only the HQL text depends on the value.
- In this case, the position of the guard does not change the result. When `min == null`, `it.price > min!! || min == null` also has no filter, because `min!!` and unboxing are not run (Kotlin would throw `NullPointerException`).
- A comparison that calls a method on a captured value (`it.description == text.trim()`) is different. If the guard comes after it, `text.trim()` is computed first, so it throws an exception (`LQL-4012`) when `text == null`.

<a id="365-converted-attributes-convert"></a>
### 3.6.5 Converted attributes (`@Convert`)

An attribute with a JPA `AttributeConverter` is compared with Kotlin values. Hibernate (7.4, and also the earlier 6.6) converts literals compared with such an attribute, and it also converts bind parameters (checked on H2, MariaDB and Oracle).

<!-- region: ManualKotlinWhereTest.kt#convert -->
```kotlin
// code is stored reversed ("abc" as "cba"): Hibernate converts the literal
val q = qb.from(Coded::class.java).where { it.code == "abc" }
assertEquals("select e0 from Coded e0 where e0.code = 'abc'", q.toHql().hql())
```

Example code: [`ManualKotlinWhereTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt) (`captured-eq`, `string-eq`, `never-null`, `null-check`, `sql-meaning`, `captured-null-guard`, `convert`).

<a id="37-boolean-columns"></a>
## 3.7 Boolean columns

`active` of `Order` is a primitive `boolean` (not NULL, Kotlin property name `isActive`).
The rows with `vip` use the `Boolean vip` column of `Coded`, which can be NULL.

| Kotlin | HQL | NULL rows |
|---|---|---|
| `it.isActive` | `e0.active = true` | – (not NULL) |
| `it.vip == true` | `e0.vip = true` | Not returned |
| `it.vip != true` | `e0.vip <> true` | **Not returned** (SQL meaning. Kotlin would return them) |
| `it.vip == false` | `e0.vip = false` | Not returned (NULL is not false) |
| `it.vip ?: false` | `e0.vip is not null and e0.vip = true` | Not returned |
| `!(it.vip ?: false)` | `e0.vip is null or e0.vip = false` | **Returned** |

Notes:

- lambdaQL does not use `is not true` or `coalesce(flag, false)`. They fail on Oracle (ORA-00932).
- The negation of a bigger condition is always written as `not (...)`. lambdaQL does not wrap a condition as `(...) = false` or `(...) is null`, because Oracle 23 evaluates this wrongly in `WHERE`.
- `it.vip ?: false` is fine **inside a condition**. As a value or a sort key, it is rejected.

<!-- region: ManualKotlinWhereTest.kt#nullable-boolean -->
```kotlin
// vip is a Boolean column that can be NULL
val yes = qb.from(Coded::class.java).where { it.vip == true }
assertEquals("select e0 from Coded e0 where e0.vip = true", yes.toHql().hql())

val notYes = qb.from(Coded::class.java).where { it.vip != true }
assertEquals("select e0 from Coded e0 where e0.vip <> true", notYes.toHql().hql())

val elvis = qb.from(Coded::class.java).where { it.vip ?: false }
assertEquals("select e0 from Coded e0 where e0.vip is not null and e0.vip = true", elvis.toHql().hql())

val notElvis = qb.from(Coded::class.java).where { !(it.vip ?: false) }
assertEquals("select e0 from Coded e0 where e0.vip is null or e0.vip = false", notElvis.toHql().hql())
```

Example code: [`ManualKotlinWhereTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt) (`nullable-boolean`).

<a id="38-strings"></a>
## 3.8 Strings

- `==` and `equals` on strings use the **database collation**.
  - MariaDB's `_ci` collations ignore case and trailing spaces.
  - Oracle stores `''` (the empty string) as NULL. So on Oracle, `it.description == ""` works differently from Kotlin.
**`startsWith`, `endsWith`, `contains`** become `like`. `%` and `_` in the search text are matched as plain characters, not as wildcards.

<!-- region: KotlinLikeTest.kt#like -->
```kotlin
// % and _ in the text are not wildcards: only "50%_off sale" matches, not "50x off"
val starts = qb.from(Order::class.java).where { it.description.startsWith(p) }
assertEquals("select e0 from Order e0 where e0.description like :p1 escape '!'", starts.toHql().hql())
// ignoreCase = true: both sides in lower case
val any = qb.from(Order::class.java).where { it.description.contains("all", ignoreCase = true) }
assertEquals("select e0 from Order e0 where lower(e0.description) like '%all%' escape '!'", any.toHql().hql())
```

- A constant is written into the HQL as it is, and a captured value becomes a bind parameter.
- `ignoreCase = true` wraps both sides in `lower(...)`. The normal index of the column cannot be used, so `LQL-5013` is logged at `WARN` level.
- `endsWith` and `contains` put `%` in front of the pattern, so the normal index of the column cannot be used.
- On MariaDB and MySQL, `like` does not tell upper and lower case apart with the default collation. So the result can differ from Kotlin's `startsWith`.
- A captured value used as search text that is `null` is an error (`LQL-4047`).
- Other string functions, such as `lowercase()`, `isBlank()` and `trim()`, are not supported (`LQL-3008`).
- String templates and `+` in a lambda are rejected (`LQL-2007`). This also applies to a template that uses only captured values (`"$a-$b"`), so build the string before the lambda and capture it.

<!-- region: ManualKotlinWhereTest.kt#string-rejected -->
```kotlin
// string functions on a column other than startsWith, endsWith and contains are not translated
val f = assertThrows<LambdaQLException> {
    qb.from(Order::class.java).where { it.description.lowercase() == "a" }.toHql()
}
assertTrue(f.message!!.startsWith("[LQL-3008]"))

// string building in the lambda is rejected: build the string before the lambda
val a = "x"
val b = "y"
val t = assertThrows<LambdaQLException> {
    qb.from(Order::class.java).where { "$a-$b" == it.description }.toHql()
}
assertTrue(t.message!!.startsWith("[LQL-2007]"))
```

Example code: [`ManualKotlinWhereTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt) (`string-rejected`), [`KotlinLikeTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinLikeTest.kt) (`like`).

<a id="39-in-with-a-captured-collection"></a>
## 3.9 IN with a captured collection

Build the collection **outside** the lambda and use `in`.

<!-- region: ManualKotlinWhereTest.kt#in-ids -->
```kotlin
val ids = seeded.toList()        // two order ids, built outside the lambda

val inList = qb.from(Order::class.java).where { it.id in ids }
assertEquals("select e0 from Order e0 where e0.id in (:p1)", inList.toHql().hql())

val notInList = qb.from(Order::class.java).where { it.id !in ids }
assertEquals("select e0 from Order e0 where e0.id not in (:p1)", notInList.toHql().hql())
```

`seeded` holds the ids of two orders saved in advance.

**SQL by database**

<!-- sql: not-in -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.id not in (?,?)
```

<!-- region: KotlinSelectTest.kt#in -->
```kotlin
val q = qb.from(Order::class.java).where { it.status in statuses }
assertEquals("select e0 from Order e0 where e0.status in (:p1)", q.toHql().hql())
```

`statuses` is a captured `listOf<Short>(1, 3)`.

`!in` becomes `not in`. A column that can be NULL also gets a simple `not in` (SQL meaning).
Rows whose value is NULL do not match `not in`, and lambdaQL logs `LQL-5009` at `DEBUG` level. Kotlin's `!in` is true for a NULL value, so to keep such rows, write a null check, as in `it.description == null || it.description !in descs`.

| Case | Result |
|---|---|
| The collection is a captured `List`, `Set` or other `Collection` | Supported |
| The collection has a `null` element | Exception when the query runs |
| Empty collection | `in` is FALSE, `!in` is TRUE (Hibernate writes `1=0` / `1=1`) |
| 1,500 elements | Works as one statement on H2, MariaDB and Oracle 23ai (older Oracle versions have a limit of 1,000. Not verified) |
| The collection is an entity collection: `item in it.items` | **Rejected**. Use an `exists()` subquery ([5. Subqueries](05-subquery.md)) |
| A collection built inside the lambda: `it.status in listOf(1, 2)` | **Rejected**. Build it outside and capture it |

Kotlin also has ranges (`it.status in 1..5`) and `when` with constants. See [3.14](#314-ranges-kotlin-only) and [3.15](#315-when-kotlin-only).

Example code: [`ManualKotlinWhereTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt) (`in-ids`), [`KotlinSelectTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinSelectTest.kt) (`in`).

<a id="310-association-paths"></a>
## 3.10 Association paths

lambdaQL does not run `it.customer?.name`. This path only names "the `name` column of the joined customer".
lambdaQL checks each step with the JPA Metamodel.

| Path step | HQL | Example |
|---|---|---|
| To-one association (`@ManyToOne`, `@OneToOne`) | `join` or `left join` with an alias (see below) | `it.customer?.name` -> `join e0.customer j1` ... `j1.name` |
| Embedded (`@Embedded`) | Same table, no join. An equality comparison of a whole value object is expanded per column (see below) | If `Order` has an embedded `address`: `it.address.city` -> `e0.address.city` |
| A path that ends with the target's `@Id` | Foreign key column, no join | `it.customer?.id` -> `e0.customer.id` |
| The association itself compared with `null` | Foreign key column, no join | `it.customer == null` -> `e0.customer is null` |
| Collection (`@OneToMany`, `@ManyToMany`) | **Rejected** | `it.items...` -> use an `exists()` subquery |

**Comparing a whole value object.** If you compare a whole embedded value object (`@Embedded`) for equality, lambdaQL expands it into one comparison per column.
`it.address == addr` becomes `e0.address.city = :p1 and e0.address.zip = :p2`.

- The negation (`it.address != addr`) is `e0.address.city <> :p1 or e0.address.zip <> :p2`.
- `it.address == null` is `e0.address.city is null and e0.address.zip is null`, and `!= null` is `e0.address.city is not null or e0.address.zip is not null`.
- Each column follows the SQL meaning. A NULL column matches neither `=` nor `<>`. For example, a (Seoul, NULL) row matches neither the equality nor the negation (measured on three databases).
- Each property of a captured value object is read and bound when the query runs. If the captured object is `null`, NULL is bound to all columns, so no row matches, and `LQL-4045` is logged at `WARN` level for each column.
- Nested value objects are expanded too.
- Ordering comparisons (`<` and so on) and comparisons with other types are still the error `LQL-3012`. Compare the fields.

<!-- region: KotlinQueryTest.kt#embedded-compare -->
```kotlin
// address is @Embedded (city, zip): == with a value object compares each column
val addr = Address("Seoul", "04524")
val same = qb.from(Order::class.java).where { it.address == addr }
assertEquals("select e0 from Order e0 where e0.address.city = :p1 and e0.address.zip = :p2", same.toHql().hql())
val none = qb.from(Order::class.java).where { it.address == null }
assertEquals("select e0 from Order e0 where e0.address.city is null and e0.address.zip is null", none.toHql().hql())
```

`it.customer?.name`, `it.customer!!.name` and (for a Java entity) `it.customer.name` all mean the **same column** `j1.name`.
In `where`, the whole condition decides the join type. If an order without a customer can match the condition, it is a `left join`. If it cannot, it is an inner `join`.
The result is the same as running the same lambda on a list. It also reflects the short-circuit evaluation of `&&` and `||`.

For an order without a customer, each comparison is evaluated as follows, depending on how it passes the association step.

| How the association step is passed in `where` | Comparison for an order without a customer |
|---|---|
| `!!`, or no mark (a non-null type `customer: Customer`, the platform type of a Java entity) | Not true. True only for orders that have a customer |
| `?.` | The customer's columns are treated as NULL and compared with the SQL meaning. `j1.name = 'Kim'` is not true, and `j1.name is null` is true |

The value comparison follows the SQL meaning in both cases. For example, `it.customer?.name != "Kim"` does not match an order without a customer.
You can mix `?.` and `!!` (or no mark) in one condition.

**When it becomes an inner join.** If an order without a customer cannot match the condition, it is an inner join even if you write `?.`. The result is the same.

<!-- region: ManualKotlinStartTest.kt#join-path -->
```kotlin
val name = "Kim"
val q = qb.from(Order::class.java).where { it.customer?.name == name }
assertEquals("select e0 from Order e0 join e0.customer j1 where j1.name = :p1", q.toHql().hql())
```

**SQL by database**

<!-- sql: inner-join -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name=?
```

- **Orders without a customer** are not in the result, because there is no customer row to join.
- lambdaQL does not add a null check for you. You do not need to write `it.customer != null &&` in front of the path.

<!-- region: KotlinQueryTest.kt#safe-call-and -->
```kotlin
// no order without a customer can match: an inner join, also with ?.
val q = qb.from(Order::class.java).where { it.customer?.name == "Kim" && it.price > 100.0 }
assertEquals("select e0 from Order e0 join e0.customer j1 where j1.name = 'Kim' and e0.price > 100.0", q.toHql().hql())
```

**`?.` and `||`.** An order with status 1 matches the condition even without a customer, so it is a `left join`.
Orders with status 1 and no customer also stay in the result. This is the same as running the same condition in Kotlin.

<!-- region: KotlinQueryTest.kt#safe-call-or -->
```kotlin
// ?. says the customer may be missing: an order without a customer stays when the rest of the OR is true
val q = qb.from(Order::class.java).where { it.status.toInt() == 1 || it.customer?.name == "Kim" }
assertEquals("select e0 from Order e0 left join e0.customer j1 where e0.status = 1 or j1.name = 'Kim'", q.toHql().hql())
```

**SQL by database**

<!-- sql: safe-call-or -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where o1_0.status=1 or c1_0.name='Kim'
```

**`?.` and `== null`.** `it.customer?.name == null` finds both orders without a customer and orders whose customer has no name, as in Kotlin.
`it.customer!!.name == null`, written with `!!`, is true only for orders that have a customer, so it is an inner join, and it finds only orders whose customer has no name.

<!-- region: KotlinQueryTest.kt#safe-call-null -->
```kotlin
// "no customer, or a customer without a name": a left join, as in Kotlin
val q = qb.from(Order::class.java).where { it.customer?.name == null }
assertEquals("select e0 from Order e0 left join e0.customer j1 where j1.name is null", q.toHql().hql())
```

**SQL by database**

<!-- sql: safe-call-null -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name is null
```

**Null guard.** With `it.customer == null || ...`, orders without a customer match the condition, so it is a `left join`, and orders without a customer stay. It is the same whether you write `?.` or `!!`.
With `it.customer != null && ...`, orders without a customer cannot match the condition, so it is an inner join.

<!-- region: ManualKotlinWhereTest.kt#null-guard -->
```kotlin
// the null guard keeps orders without a customer: a left join (?. or !! give the same)
val safe = qb.from(Order::class.java).where { it.customer == null || it.customer?.name == name }
assertEquals("select e0 from Order e0 left join e0.customer j1 where e0.customer is null or j1.name = :p1",
    safe.toHql().hql())
val sure = qb.from(Order::class.java).where { it.customer == null || it.customer!!.name == name }
assertEquals("select e0 from Order e0 left join e0.customer j1 where e0.customer is null or j1.name = :p1",
    sure.toHql().hql())

// no order without a customer can match: an inner join
val and = qb.from(Order::class.java).where { it.customer != null && it.customer?.name == name }
assertEquals("select e0 from Order e0 join e0.customer j1 where e0.customer is not null and j1.name = :p1",
    and.toHql().hql())
```

The first SQL below comes from the second form, written with `!!`. The second SQL comes from the third form, written with `&&`.

**SQL by database**

<!-- sql: null-guard -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.id is null or c1_0.name=?
```

**SQL by database**

<!-- sql: null-guard#2 -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.id is not null and c1_0.name=?
```

Hibernate writes `e0.customer is null` as `c1_0.id is null`. After an inner join, this condition is never true.

**`!!` and `||`.** A comparison that passes through `!!` is true only for orders that have a customer.
In Kotlin, a row where `||` ends before it reaches `!!` (status 1, no customer) stays. This row matches the condition, so lambdaQL also uses a `left join` and keeps the row.

<!-- region: KotlinQueryTest.kt#not-null-assert -->
```kotlin
// !! counts only for orders with a customer; || stops before it, so the status 1 order without a customer
// stays, as in Kotlin: a left join
val q = qb.from(Order::class.java).where { it.status.toInt() == 1 || it.customer!!.name == "Kim" }
assertEquals("select e0 from Order e0 left join e0.customer j1 where e0.status = 1 or j1.name = 'Kim'", q.toHql().hql())
```

**Mixing `?.` and `!!`.** You can pass the same association step with `?.` in one place and with `!!` or no mark in another. The evaluation in the table above applies to each comparison.
In the first example, neither side can be true for an order without a customer, so it is an inner join. In the second example, the `?.` side is true for an order without a customer, so it is a `left join`.
In a `left join`, if you write a comparison that can be true on a NULL column (`is null`, `coalesce`), such as `j1.grade is null`, with `!!` or no mark, lambdaQL adds `e0.customer is not null` to that comparison only.

<!-- region: KotlinQueryTest.kt#safe-and-assert -->
```kotlin
// the same association with ?. in one place and !! in another: neither side can match an order without
// a customer, so an inner join (no warning)
val q = qb.from(Order::class.java).where { it.customer?.name == "Kim" || it.customer!!.grade!! > 3 }
assertEquals("select e0 from Order e0 join e0.customer j1 where j1.name = 'Kim' or j1.grade > 3", q.toHql().hql())
// ?. side true without a customer: a left join, and the !! side counts only with a customer
val keep = qb.from(Order::class.java).where { it.customer?.name == null || it.customer!!.grade == null }
assertEquals("select e0 from Order e0 left join e0.customer j1"
    + " where j1.name is null or (e0.customer is not null and j1.grade is null)", keep.toHql().hql())
```

- If the path has several steps (`it.order?.customer?.name` of `Item`), the join type of each step is decided by the same rule.

To find orders **without** a customer, compare the association itself. This checks the foreign key column, with no join.

<!-- region: ManualKotlinWhereTest.kt#no-customer -->
```kotlin
val q = qb.from(Order::class.java).where { it.customer == null }
assertEquals("select e0 from Order e0 where e0.customer is null", q.toHql().hql())
```

**SQL by database**

<!-- sql: no-association -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id is null
```

More notes:

- The same path gets one alias. `where`, `orderBy` and `select` share this alias.
- `it.customer?.id == 5L` uses the foreign key column `e0.customer.id`. No join is needed.
- A path used **only** in `orderBy` or `select` is a `left join`, so no rows are lost there. This is also true with `!!`. If the same path is also used in `where`, `orderBy` and `select` use the join of `where`.
- This join rule applies to `where` in selects, `count`, subqueries and join queries. A path inside the `on` of an explicit join is a `left join`, as before. A required association (`optional = false`) is always an inner join.
- Do not compare an association with an entity object (`it.customer == c`). Compare the id: `it.customer?.id == c.id` (here `c` is a captured object).

Example code: [`KotlinQueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt) (`embedded-compare`, `safe-call-and`, `safe-call-or`, `safe-call-null`, `not-null-assert`, `safe-and-assert`), [`ManualKotlinStartTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinStartTest.kt) (`join-path`), [`ManualKotlinWhereTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt) (`null-guard`, `no-customer`).

<a id="311-captured-values"></a>
## 3.11 Captured values

A lambda can use values from outside: local values, properties, and properties of a captured object.

<!-- region: ManualKotlinWhereTest.kt#captured-kinds -->
```kotlin
val since = LocalDateTime.of(2026, 1, 1, 0, 0)
val local = qb.from(Order::class.java).where { it.updateAt > since }                     // a local val
assertEquals("select e0 from Order e0 where e0.updateAt > :p1", local.toHql().hql())

val property = qb.from(Order::class.java).where { it.status == defaultStatus }           // a property of this class
assertEquals("select e0 from Order e0 where e0.status = :p1", property.toHql().hql())

val objectProperty = qb.from(Order::class.java).where { it.price > filter.minPrice!! }   // a property of a captured object
assertEquals("select e0 from Order e0 where e0.price > :p1", objectProperty.toHql().hql())

val call = qb.from(Order::class.java).where { it.updateAt > LocalDateTime.of(2026, 1, 1, 0, 0) }   // a call without the entity
assertEquals("select e0 from Order e0 where e0.updateAt > :p1", call.toHql().hql())
```

`defaultStatus` is a property (`Short`) of the class that builds the query, and `filter` is a captured `PriceFilter` object.

| What is captured | How lambdaQL uses it |
|---|---|
| `val` (also `Long?`, `String`, ...) | Bind parameter |
| `var` | Bind parameter. The value is read **each time the query runs** |
| A property of the outer class (also `private`) | Read each time the query runs |
| A `val` of a `companion object`, a top-level `val`, a `val` of an `object` | Bind parameter |
| `const val` | Written into the HQL as a literal |
| Writing to a captured variable inside the lambda (`n++`) | **Rejected** |

Rules:

- The parts that do not use entities are computed in the application **each time the query runs**. The result becomes a bind parameter.
- The lambda is read once and the result is cached. New captured values are read on each call.
- Only the entity parts become SQL.

Example code: [`ManualKotlinWhereTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt) (`captured-kinds`).

<a id="312-errors-that-depend-on-order-of-evaluation"></a>
## 3.12 Errors that depend on order of evaluation

SQL does not promise to evaluate `AND`/`OR` from left to right. So a "guard" like the one below is not safe in SQL.
lambdaQL does not reject this guard. It writes it into the HQL as written. If the database computes the division before `e0.qty <> 0`, a division-by-zero error can happen.

<!-- region: ManualKotlinWhereTest.kt#division-guard -->
```kotlin
// lambdaQL sends the guard as written; SQL does not promise to check qty <> 0 first
val q = qb.from(Item::class.java).where { it.qty != 0 && it.price!! / it.qty > 2 }
assertEquals("select e0 from Item e0 where e0.qty <> 0 and (e0.price / e0.qty) > 2.0", q.toHql().hql())

// the same rows when qty is positive, without a division
val safe = qb.from(Item::class.java).where { it.qty > 0 && it.price!! > 2.0 * it.qty }
assertEquals("select e0 from Item e0 where e0.qty > 0 and e0.price > (2.0 * e0.qty)", safe.toHql().hql())
```

Write the condition without division. The second query returns the same rows as the first query when `qty` is positive.

Example code: [`ManualKotlinWhereTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt) (`division-guard`).

<a id="313-what-is-rejected"></a>
## 3.13 What is rejected

If the analysis fails, lambdaQL throws an exception with a code `[LQL-xxxx]` and the source line number.
Most shapes are found when the lambda is analyzed (on first use). Bad captured values (a `null` with a method call, NaN, a collection with a `null` element) are found when the query runs.
See [11. Errors and languages](../11-errors-and-languages.md).

| Form | Example | How to fix |
|---|---|---|
| Local `val`/`var`, loops, a block with several statements | `{ val x = 1; it.status == x }` | Write one expression |
| A general `if (c) x else y` | | Use the allowed forms in [3.5](#35-and-or-not) |
| Infix `and`, `or`, `xor` | `it.isActive and (it.price > 100.0)` | Use `&&`, `\|\|` |
| A string template or `+` in the lambda (`LQL-2007`) | `it.description == "A" + it.status` | Build the string before the lambda and capture it |
| A collection built inside the lambda | `it.status in listOf(1, 2)` | Build it outside and capture it |
| A lambda inside the lambda | `ids.any { id -> id == it.id }` | Use `in` ([3.9](#39-in-with-a-captured-collection)) or a subquery |
| Kotlin string functions and other stdlib functions (`LQL-3008`). `startsWith`, `endsWith`, `contains` are supported ([3.8](#38-strings)) | `it.description?.trim()`, `isBlank()`, `lowercase()` | Compare directly |
| A `when` compiled to a `switch` (`LQL-2010`) | `when (it.status.toInt()) { 1, 2 -> true; else -> false }` | Comparisons joined with `\|\|` ([3.15](#315-when-kotlin-only)) |
| A range that needs a range object (`LQL-3008`) | `it.customer?.grade in 1..5`, a `LocalDateTime` range | Write two comparisons ([3.14](#314-ranges-kotlin-only)) |
| Your own non-inline extension function | `it.isOpen()` | Make it an `inline fun` or write the condition directly |
| Other methods of the entity | `it.calcSomething()` | Use properties only |
| An entity collection path | `it.items.size > 0` | An `exists()` subquery |
| Entity compared with entity | `it.customer == c` | Compare ids |
| Ordering comparison of a value object, comparison with another type (`LQL-3012`) | `it.address > a` (when `Address` is `Comparable`) | Compare the fields. `==` and `!=` are supported |
| A `NaN` / `Infinity` captured value | `it.price!! > Double.NaN` | Do not send NaN |
| `isNaN()` | `it.price!!.isNaN()` | – |
| Writing to a captured variable | `{ n++; it.isActive }` | Compute it outside the lambda |
| Anonymous objects, function type values | `where(f)` with `val f: (Order) -> Boolean` | Pass a lambda literal |

Exception types (all extend `LambdaQLException`):

| Exception | When |
|---|---|
| `LambdaAnalysisException` | The lambda cannot be read (not a serializable lambda, compiled to a class, and so on) |
| `UnsupportedLambdaShapeException` | The shape is not supported (loops, the `switch` of a `when` with a subject, ...) |
| `TranslationException` | The meaning cannot be translated (unknown function, collection path, ...) |

All Kotlin limits: [9. Kotlin notes](09-kotlin-notes.md#914-kotlin-limits-kc1-to-kc21). All rules: [10. Rules and limits](10-rules-and-limits.md).

<a id="314-ranges-kotlin-only"></a>
## 3.14 Ranges (Kotlin only)

The Kotlin compiler compiles a range on a number or string subject that can never be NULL into two comparisons. lambdaQL writes these comparisons as they are (not `between`).
For `a..b` with a constant upper bound, the compiler changes it to `< b+1`. For example, `in 1..2` becomes `e0.status < 3`.

<!-- region: ManualKotlinWhereTest.kt#ranges -->
```kotlin
val closed = qb.from(Order::class.java).where { it.status.toInt() in 1..2 }
assertEquals("select e0 from Order e0 where 1 <= e0.status and e0.status < 3", closed.toHql().hql())

val outside = qb.from(Order::class.java).where { it.status.toInt() !in 1..2 }
assertEquals("select e0 from Order e0 where 1 > e0.status or e0.status >= 3", outside.toHql().hql())

val until = qb.from(Order::class.java).where { it.status.toInt() in 1 until 3 }
assertEquals("select e0 from Order e0 where 1 <= e0.status and e0.status < 3", until.toHql().hql())

val open = qb.from(Order::class.java).where { it.status.toInt() in 1..<3 }
assertEquals("select e0 from Order e0 where 1 <= e0.status and e0.status < 3", open.toHql().hql())

val captured = qb.from(Order::class.java).where { it.cnt in lo..hi }                  // lo, hi: captured values
assertEquals("select e0 from Order e0 where :p1 <= e0.cnt and e0.cnt <= :p2", captured.toHql().hql())

val prices = qb.from(Order::class.java).where { it.price in 100.0..200.0 }
assertEquals("select e0 from Order e0 where 100.0 <= e0.price and e0.price <= 200.0", prices.toHql().hql())

val texts = qb.from(Order::class.java).where { it.description in "a".."m" }
assertEquals("select e0 from Order e0 where e0.description >= 'a' and e0.description <= 'm'", texts.toHql().hql())
```

| Range form | Supported |
|---|---|
| `a..b`, `a until b`, `a..<b` (on `Int`, `Double`, `String`) | Yes. Two comparisons |
| `!in` | Yes. The comparisons are reversed and joined with `or` |
| Captured bounds (`lo..hi`) | Yes. Bind parameters |
| A nullable subject (`Int?`), `LocalDate` and `LocalDateTime` ranges | No (`LQL-3008`) |
| `downTo`, `step` | Yes. The values are expanded and bound as `in (:p1)` |

**A subject that can be NULL.** An `Integer` property of a Java entity (`cnt`) is a platform type, so the compiler compiles it into two comparisons. lambdaQL does **not** add a null guard, so rows where `cnt` is NULL are not returned (SQL meaning).
An `Int?` subject from `?.` and date ranges are compiled into range objects, so they are rejected. Write them as two comparisons.

<!-- region: ManualKotlinWhereTest.kt#range-rejected -->
```kotlin
// a range object is not translated: a nullable subject, or a range of dates
val nullable = assertThrows<LambdaQLException> {
    qb.from(Order::class.java).where { it.customer?.grade in 1..5 }.toHql()
}
assertTrue(nullable.message!!.startsWith("[LQL-3008]"))

val dates = assertThrows<LambdaQLException> {
    qb.from(Order::class.java).where { it.updateAt in from..to }.toHql()
}
assertTrue(dates.message!!.startsWith("[LQL-3008]"))

// write the two comparisons instead
val q = qb.from(Order::class.java).where { it.updateAt >= from && it.updateAt <= to }
assertEquals("select e0 from Order e0 where e0.updateAt >= :p1 and e0.updateAt <= :p2", q.toHql().hql())
```

Example code: [`ManualKotlinWhereTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt) (`ranges`, `range-rejected`).

<a id="315-when-kotlin-only"></a>
## 3.15 `when` (Kotlin only)

`when` is supported **as a condition**. Every branch must end with `true` or `false` (or a condition).
lambdaQL turns it into comparisons, `OR` and `AND`.

<!-- region: ManualKotlinWhereTest.kt#when -->
```kotlin
val twoTexts = qb.from(Order::class.java).where { when (it.description) { "a", "b" -> true; else -> false } }
assertEquals("select e0 from Order e0 where e0.description = 'a' or e0.description = 'b'", twoTexts.toHql().hql())

val oneValue = qb.from(Order::class.java).where { when (it.status.toInt()) { 1 -> true; else -> false } }
assertEquals("select e0 from Order e0 where e0.status = 1", oneValue.toHql().hql())

val range = qb.from(Order::class.java).where { when (it.status.toInt()) { in 1..2 -> true; else -> false } }
assertEquals("select e0 from Order e0 where 1 <= e0.status and e0.status < 3", range.toHql().hql())

val condition = qb.from(Order::class.java).where { when (it.status.toInt()) { 1 -> it.isActive; else -> false } }
assertEquals("select e0 from Order e0 where e0.status = 1 and e0.active = true", condition.toHql().hql())
```

Rows where the subject is NULL are not returned. If you ran the Kotlin code, they would go to `else`, but lambdaQL uses the SQL meaning.

If the compiler compiles the `when` into a `switch`, it is not supported yet (`LQL-2010`). This covers an `Int` subject with two or more constants and a `String` subject with three or more constants. An enum subject is not supported either.
Write such conditions as comparisons joined with `||`, or as `in` with a captured collection ([3.9](#39-in-with-a-captured-collection)).
A `when { }` without a subject that picks a value from entity values becomes a `case when` (see [3.5.1](#351-values-chosen-by-a-condition)).

<!-- region: ManualKotlinWhereTest.kt#when-rejected -->
```kotlin
// when compiled to a switch is not supported yet (LQL-2010): two or more Int values, three or more strings
val ints = assertThrows<LambdaQLException> {
    qb.from(Order::class.java).where { when (it.status.toInt()) { 1, 2 -> true; else -> false } }.toHql()
}
assertTrue(ints.message!!.startsWith("[LQL-2010]"))

val texts = assertThrows<LambdaQLException> {
    qb.from(Order::class.java).where { when (it.description) { "a", "b", "c" -> true; else -> false } }.toHql()
}
assertTrue(texts.message!!.startsWith("[LQL-2010]"))

// an enum subject is not supported either
assertThrows<LambdaQLException> {
    qb.from(Order::class.java).where { when (it.kind) { Order.Kind.RUSH -> true; else -> false } }.toHql()
}
```

Write the condition with `&&` and `||` instead.

Example code: [`ManualKotlinWhereTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt) (`when`, `when-rejected`).

---

[Kotlin track](README.md) | Previous: [2. Getting started](02-getting-started.md) | Next: [4. Sorting and paging](04-order-paging.md) | [Java version of this page](../java/03-where.md)
