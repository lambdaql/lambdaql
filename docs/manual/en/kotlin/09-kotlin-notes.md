<a id="9-kotlin-notes"></a>
# 9. Kotlin notes

> [!IMPORTANT]
> This page is older than the Korean manual. See the [Korean page](../../ko/kotlin/09-kotlin-notes.md) for the latest rules.

[Kotlin track](README.md) | [Manual home](../README.md) | Previous: [8. Procedures and DB functions](08-procedures-functions.md) | Next: [10. Rules and limits](10-rules-and-limits.md) | [Java version of this page: 9. Java notes](../java/09-java-notes.md)

Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md)

lambdaQL supports Kotlin and Java with **the same engine** and the same API.
A Kotlin lambda and a Java lambda with the same meaning make the same HQL.
This chapter puts the things that are special in Kotlin in one place.

<a id="contents"></a>
## Contents

- [9.1 Setup](#91-setup)
- [9.2 Lambda forms that work](#92-lambda-forms-that-work)
- [9.3 Your own null checks and the compiler's null checks](#93-only-your-null-checks-count)
- [9.4 Equality: `==`, `!=`, `===` and `areEqual`](#94-equality----and-areequal)
- [9.5 Null operators: `?.`, `?:` and `!!`](#95-null-operators---and-)
- [9.6 `in`, ranges and `when`](#96-in-ranges-and-when)
- [9.7 Strings](#97-strings)
- [9.8 Captured values](#98-captured-values)
- [9.9 Property references and scope functions](#99-property-references-and-scope-functions)
- [9.10 Kotlin operators](#910-kotlin-operators)
- [9.11 Results: data classes, `Pair` and `Triple`](#911-results-data-classes-pair-and-triple)
- [9.12 Update, insert, delete: property assignment and `Dml.value`](#912-update-insert-delete-property-assignment-and-dmlvalue)
- [9.13 Procedures and DB functions](#913-procedures-and-db-functions)
- [9.14 Kotlin limits (KC1 to KC21)](#914-kotlin-limits-kc1-to-kc21)
- [9.15 Where lambdaQL differs from running Kotlin code](#915-where-lambdaql-differs-from-running-kotlin-code)

How to read the examples:

- The `assertEquals` string in an example is the HQL that lambdaQL makes.
- Constants written directly in the lambda (numbers, strings, booleans, `null`, enum constants, `const val`) are written into the HQL as literals. Captured values and computed values are always bind parameters (`:p1`, `:p2`, ...).
- `e0` is the alias of the root entity, and `j1` is the alias of a join that lambdaQL adds for an association path. In `where`, it is a `left join` if a row without the association can match the condition, and an inner `join` if it cannot. A path used only in `orderBy` or `select` is a `left join`. Subquery roots are `s1`, `s2`, ... (see [5. Subqueries](05-subquery.md)).
- The entities are the example entities written in Java (`Order`, `Customer`, `Item`, `Coded`). Kotlin sees Java getters as properties of platform types.
  `Order.status` is a `short`, so conditions are written as `it.status.toInt() == 1`, and the boolean `active` is read as `it.isActive`. The examples with a Boolean that can be NULL use `Coded.vip`.

---

<a id="91-setup"></a>
## 9.1 Setup

| Item | Value |
|---|---|
| Kotlin | 2.x. The design was checked with kotlinc 1.7.22, 2.1.10, 2.2.21, 2.3.0 and 2.3.21 |
| JVM | Java 21 or later |
| Compiler option | Keep the default `-Xsam-conversions=indy`. Do **not** use `-Xsam-conversions=class` |
| `-jvm-target` | 1.8, 11, 17 and 21 all work |
| Kotlin runtime | lambdaQL does not add `kotlin-stdlib` or `kotlin-reflect` to your dependencies |
| JPA entities | Normal Kotlin JPA entities (annotations on fields are fine too) |

Details: [2. Getting started](02-getting-started.md#kotlin-compiler-settings).

<a id="92-lambda-forms-that-work"></a>
## 9.2 Lambda forms that work

lambdaQL functions take Java `Serializable` functional interfaces.
In Kotlin, you pass a **lambda literal**. Use a trailing lambda and `it`.

<!-- region: ManualKotlinNotesTest.kt#where-chain -->
```kotlin
val q = qb.from(Order::class.java)
    .where { it.status.toInt() == 1 }
    .where { it.price > 100.0 }        // more where() calls are joined with AND
assertEquals("select e0 from Order e0 where e0.status = 1 and e0.price > 100.0", q.toHql().hql())
```

**SQL by database**

<!-- sql: to-hql -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>100.0
```

A lambda with two or more parameters uses names. Use `_` for a parameter that you do not need.

<!-- region: ManualKotlinNotesTest.kt#named-params -->
```kotlin
// lambdas with two parameters use names
val q = qb.from(Order::class.java)
    .where { o, sub -> sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
    .orderBy { o -> desc(o.price).asc(o.id) }
assertEquals("select e0 from Order e0 where exists (select 1 from Item s1 where s1.order.id = e0.id)"
    + " order by e0.price desc, e0.id asc", q.toHql().hql())
```

Kotlin properties are read through getters. `it.price` is the same bytecode opcode as the Java call `getPrice()`.
lambdaQL maps the getter to a JPA attribute.
The getter of the boolean property `active` of the Java entity is `isActive()`, so in Kotlin you read it as `it.isActive`. lambdaQL maps it to `active`.

| Form | Does it work? |
|---|---|
| `where { it.isActive }` (lambda literal) | Yes |
| Lambdas inside `run { }`, `let { }`, `forEach { }`, non-inline functions, `suspend` functions | Yes |
| A lambda that the caller passes to an `inline fun` parameter of a lambdaQL type | Yes |
| SAM constructors, also when stored in a `val` first | Yes |
| A property reference or Java getter reference passed to a condition: `where(Order::isActive)` | **No** (`LQL-1009`). Use a lambda |
| `val f: (Order) -> Boolean = { it.isActive }; where(f)` | **No** |
| `object : ...` (an object expression instead of a lambda) | **No** |
| A lambda made inside the body of an `inline fun` | **No** |
| Compiled with `-Xsam-conversions=class` | **No** |

Example code: [`ManualKotlinNotesTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinNotesTest.kt) (`where-chain`, `named-params`).

<a id="93-only-your-null-checks-count"></a>
## 9.3 Your own null checks and the compiler's null checks

lambdaQL does not call the lambda. It reads the bytecode opcodes and makes HQL.
So some Kotlin rules work differently.

| Topic | Rule |
|---|---|
| Association paths | A path such as `it.customer?.name` is a **column**, not a call chain. In `where`, a to-one association is a `left join` if an order without a customer can match the condition, and an inner join if it cannot. To find only orders without a customer, write `it.customer == null`. |
| `?.` and joins | For an order without a customer, a comparison that passes through `?.` treats the customer's columns as NULL and is evaluated with the SQL meaning. `it.status.toInt() == 1 \|\| it.customer?.name == "Kim"`, `it.customer?.name == null` and null guards are `left join`s, because an order without a customer can match the condition. You can mix `?.` and `!!` in one condition. |
| `!!` | `!!` does not change the value. `it.customer!!.name` and `it.customer?.name` are the same column. But in `where`, a comparison that passes an association step through `!!` is true only for orders that have a customer (`it.customer!!.name == null` is an inner join). |
| `==`, `===` | `==` (and `===`) between objects means a **value comparison**, not object identity. |

When you compare a number property that can be NULL (`Integer`) with a constant, the Kotlin compiler adds a null check before unboxing.
lambdaQL does not read this check, which is not in your source. So you get the same SQL meaning as in Java.
`it.cnt == 1` is `e0.cnt = 1`, and `it.cnt != 1` is `e0.cnt <> 1`, so rows where `cnt` is NULL are not returned. If you need the NULL rows, write it yourself, as in `it.cnt == null || it.cnt != 1`.

Examples of this behavior are in [9.4](#94-equality----and-areequal).

<a id="94-equality----and-areequal"></a>
## 9.4 Equality: `==`, `!=`, `===` and `areEqual`

<a id="how-kotlin-compiles-"></a>
### How Kotlin compiles `==`

| Kotlin | Compiled to | HQL |
|---|---|---|
| Two non-null primitives: `it.qty == 5` | A compare branch (same as Java) | `e0.qty = 5` |
| A nullable primitive and a constant: `it.cnt == 1` (`Integer`) | Null check + comparison | `e0.cnt = 1` (the null check is not read) |
| `it.cnt != 1` (`Integer`) | Null check + comparison | `e0.cnt <> 1` (NULL rows are **not** returned) |
| References, or both sides nullable: `it.description == text`, `it.cnt == q` (`q: Int?`) | A call to `Intrinsics.areEqual(a, b)` | `e0.description = :p1` (captured `null`: no row matches) |
| `it.description != "x"` | `!Intrinsics.areEqual(a, b)` | `e0.description <> 'x'` (NULL rows are **not** returned) |
| Two entity values: `it.description == it.custName` | `Intrinsics.areEqual(a, b)` | `e0.description = e0.custName` (no match if either side is NULL) |
| `it.id === q`, `!==` | `if_acmpeq` / `if_acmpne` | Same as `==` |
| enum: `it.kind == Order.Kind.RUSH` | `if_acmpne` | `e0.kind = org.lambdaql.query.Order$Kind.RUSH` (a literal with the full class name) |

`Intrinsics.areEqual` is the Kotlin runtime function behind `==`. lambdaQL recognizes it by name and reads it as a **value comparison**.
Entity values follow the SQL meaning. If either side is NULL, they do not match, and this is also true for NULL and NULL.
A captured `null` is bound as NULL and matches no row (`LQL-4045` at `WARN` level). To find NULL, write `x == null`.

<!-- region: ManualKotlinNotesTest.kt#hidden-null-check -->
```kotlin
// cnt is a nullable Integer: the null check Kotlin adds before the unboxing is not read (SQL meaning)
val one = qb.from(Order::class.java).where { it.cnt == 1 }
assertEquals("select e0 from Order e0 where e0.cnt = 1", one.toHql().hql())
val notOne = qb.from(Order::class.java).where { it.cnt != 1 }          // NULL rows are left out, as in SQL
assertEquals("select e0 from Order e0 where e0.cnt <> 1", notOne.toHql().hql())
// both sides nullable: Intrinsics.areEqual, a value comparison
val same = qb.from(Order::class.java).where { it.cnt == q }
assertEquals("select e0 from Order e0 where e0.cnt = :p1", same.toHql().hql())
// two entity values: NULL on either side does not match
val columns = qb.from(Order::class.java).where { it.description == it.custName }
assertEquals("select e0 from Order e0 where e0.description = e0.custName", columns.toHql().hql())
```

<a id="plain--and-"></a>
### Plain `=` and `<>`

For comparisons compiled to `Intrinsics.areEqual`, lambdaQL writes a plain `=` and `<>`.
It does not write `is [not] distinct from`. On Oracle this form becomes `decode(...)`, which cannot use an index.

<!-- region: ManualKotlinNotesTest.kt#equality -->
```kotlin
// plain = and <>: a row whose description is NULL matches neither
val same = qb.from(Order::class.java).where { it.description == text }
assertEquals("select e0 from Order e0 where e0.description = :p1", same.toHql().hql())
val other = qb.from(Order::class.java).where { it.description != "x" }
assertEquals("select e0 from Order e0 where e0.description <> 'x'", other.toHql().hql())
// NULL rows too: write the null check yourself
val orNull = qb.from(Order::class.java).where { it.description == null || it.description != "x" }
assertEquals("select e0 from Order e0 where e0.description is null or e0.description <> 'x'", orNull.toHql().hql())
```

> [!WARNING]
> **A Kotlin non-null type is not proof.** A `var code: String` property can also hold a NULL that came from the database.
> This is because Hibernate writes the column value to the field without a check.
> lambdaQL looks at the JPA metadata, not at the Kotlin type.

<a id="-and-"></a>
### `===` and `!==`

In lambdaQL, `===` means a **value comparison**, the same as `==`.
A query has no object identity. Kotlin itself gives `false` for two different `Long` boxes with the value 1000, but lambdaQL gives `true`.

<a id="bigdecimal"></a>
### `BigDecimal`

In Kotlin, `==` on `BigDecimal` calls `equals`, which also compares the scale (`1.0 != 1.00`).
SQL `=` compares only the value. **lambdaQL uses the value comparison.**
`<`, `>`, `<=` and `>=` on `BigDecimal`, `String`, `LocalDate` and `BigInteger` are supported (they compile to `compareTo`).

<a id="booleans"></a>
### Booleans

<!-- region: ManualKotlinNotesTest.kt#booleans -->
```kotlin
val active = qb.from(Order::class.java).where { it.isActive }
assertEquals("select e0 from Order e0 where e0.active = true", active.toHql().hql())
val inactive = qb.from(Order::class.java).where { !it.isActive }
assertEquals("select e0 from Order e0 where e0.active = false", inactive.toHql().hql())
```

`!it.isActive` becomes `e0.active = false`.

<a id="java-entities-from-kotlin-platform-types"></a>
### Java entities from Kotlin (platform types)

If an entity is a Java class, Kotlin sees its getters as platform types (`Long!`).
All examples in this chapter are this case. The path `it.customer?.name` is a joined column (for the join type in `where`, see [3.10](03-where.md#310-association-paths)).

Example code: [`ManualKotlinNotesTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinNotesTest.kt) (`hidden-null-check`, `equality`, `booleans`).

<a id="95-null-operators---and-"></a>
## 9.5 Null operators: `?.`, `?:` and `!!`

<a id="summary"></a>
### Summary

| Kotlin | Meaning in lambdaQL | HQL example |
|---|---|---|
| `it.customer?.name` | The column `customer.name`. For an order without a customer, compared as a NULL column (SQL meaning) | `j1.name` + `join` or `left join e0.customer j1` |
| `it.customer!!.name` | The same column. The comparison is true only for orders that have a customer | `j1.name` + `join` or `left join e0.customer j1` |
| `it.customer?.id == 1` | The path ends with the target's `@Id`: FK column, no join | `e0.customer is not null and e0.customer.id = 1` |
| `it.customer == null` | Only orders without a customer | `e0.customer is null` (FK column, no join) |
| `it.customer != null` | Only orders that have a customer | `e0.customer is not null` (FK column, no join) |
| `it.description ?: ""` (value) | Default value | `coalesce(e0.description, '')` |
| `it.vip ?: false` (condition) | NULL is treated as false | `e0.vip is not null and e0.vip = true` |
| `lo!!` (captured value) | `!!` is ignored. If `lo` is null, NULL is bound, so no row matches (`LQL-4045` at `DEBUG` level, see [9.8](#98-captured-values)) | `:p1` |

<a id="-and--on-association-paths"></a>
### `?.` and `!!` on association paths

<!-- region: ManualKotlinNotesTest.kt#path-forms -->
```kotlin
// ?. and !! on a path: the same column; no order without a customer can match, so both are an inner join
val safe = qb.from(Order::class.java).where { it.customer?.name == "Kim" }
assertEquals("select e0 from Order e0 join e0.customer j1 where j1.name = 'Kim'", safe.toHql().hql())
val sure = qb.from(Order::class.java).where { it.customer!!.name == "Kim" }
assertEquals("select e0 from Order e0 join e0.customer j1 where j1.name = 'Kim'", sure.toHql().hql())
// a path that ends at the @Id of the target: the FK column, no join
val fk = qb.from(Order::class.java).where { it.customer?.id == 1 }
assertEquals("select e0 from Order e0 where e0.customer is not null and e0.customer.id = 1", fk.toHql().hql())
```

**SQL by database**

<!-- sql: path-literal -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name='Kim'
```

A path is a **column**, not a call chain. The condition above cannot match an order without a customer, so both `?.` and `!!` are inner joins.
So you do not need to write `it.customer != null &&` in front of the path.
`it.customer?.id == 1` writes the null check that Kotlin added (`e0.customer is not null`) as it is, but there is no join.

**A condition written with `?.` keeps orders without a customer, as in Kotlin.** In Kotlin, `it.customer?.name == null` is `true` when there is no customer.
lambdaQL writes this condition as a `left join`, so it also returns orders without a customer. A comparison written with `!!` is true only for orders that have a customer, so it is an inner join, and it returns only orders whose customer has no name.

<!-- region: ManualKotlinNotesTest.kt#null-paths -->
```kotlin
// orders without a customer: the FK column, no join
val none = qb.from(Order::class.java).where { it.customer == null }
assertEquals("select e0 from Order e0 where e0.customer is null", none.toHql().hql())
// orders without a customer, and orders of a customer without a name: a left join
val safe = qb.from(Order::class.java).where { it.customer?.name == null }
assertEquals("select e0 from Order e0 left join e0.customer j1 where j1.name is null", safe.toHql().hql())
// only orders of a customer without a name: the inner join leaves out orders without a customer
val sure = qb.from(Order::class.java).where { it.customer!!.name == null }
assertEquals("select e0 from Order e0 join e0.customer j1 where j1.name is null", sure.toHql().hql())
```

**SQL by database**

<!-- sql: no-association -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id is null
```

<a id="-is-just-for-the-compiler"></a>
### `!!` is just for the compiler

If a property is `Double?`, Kotlin does not compile `it.total > 10.0`.
Write `it.total!! > 10.0`. lambdaQL ignores `!!`. lambdaQL does not call the lambda, so there is no NPE.
Rows whose value is NULL are not returned (SQL rule).

<a id="-elvis"></a>
### `?:` (elvis)

In select values and string conditions, `?:` becomes `coalesce`.

<!-- region: ManualKotlinNotesTest.kt#elvis-value -->
```kotlin
// ?: in a select value: coalesce
val text = qb.from(Order::class.java).select { it.description ?: "" }
assertEquals("select coalesce(e0.description, '') from Order e0", text.toHql().hql())
// ?: after ?. in a select value is rejected
val error = assertThrows(LambdaQLException::class.java) {
    qb.from(Order::class.java).select { it.customer?.name ?: "none" }
}
assertEquals("LQL-2022", error.code())
// ?: with a number: coalesce too
val number = qb.from(Order::class.java).where { (it.total ?: 0.0) > 3.0 }
assertEquals("select e0 from Order e0 where coalesce(e0.total, 0.0) > 3.0", number.toHql().hql())
// in a string condition, ?: is coalesce too
val x = qb.from(Order::class.java).where { (it.description ?: "") == "x" }
assertEquals("select e0 from Order e0 where coalesce(e0.description, '') = 'x'", x.toHql().hql())
```

- `?:` in a number condition is also `coalesce`: `(it.total ?: 0.0) > 3.0` is `coalesce(e0.total, 0.0) > 3.0`.
- `?:` after `?.` used as a select value is rejected (`LQL-2022`).
- `coalesce` wraps the column, so the normal index on `description` is not used.
- For `?:` in DML values, see [7.2](07-2-update.md#add-to-a-value-default-for-null).

**Boolean elvis** is supported **in conditions**.

<!-- region: ManualKotlinNotesTest.kt#boolean-elvis -->
```kotlin
// Coded.vip is a nullable Boolean: in a condition, ?: false treats NULL as false
val vip = qb.from(Coded::class.java).where { it.vip ?: false }
assertEquals("select e0 from Coded e0 where e0.vip is not null and e0.vip = true", vip.toHql().hql())
val notVip = qb.from(Coded::class.java).where { !(it.vip ?: false) }
assertEquals("select e0 from Coded e0 where e0.vip is null or e0.vip = false", notVip.toHql().hql())
```

Boolean elvis used **as a value or a sort key** (`select { it.vip ?: false }`) is not supported (`LQL-2022`).
For sorting, use the key `it.vip` and choose the NULL position with `Nulls`, as in `asc(key, Nulls.LAST)` or `desc(key, Nulls.FIRST)` ([4. Sorting and paging](04-order-paging.md#43-direction)).

<a id="-in-an-assignment"></a>
### `?.` in an assignment

In `set { }` and `values { }`, `it.customer?.id = cid` **always assigns**. `?.` is ignored. The example is in [7.2](07-2-update.md#change-an-association-foreign-key).

Example code: [`ManualKotlinNotesTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinNotesTest.kt) (`path-forms`, `null-paths`, `elvis-value`, `boolean-elvis`).

<a id="96-in-ranges-and-when"></a>
## 9.6 `in`, ranges and `when`

Details and more examples: [3.9](03-where.md#39-in-with-a-captured-collection), [3.14](03-where.md#314-ranges-kotlin-only), [3.15](03-where.md#315-when-kotlin-only).

| Kotlin | HQL | Notes |
|---|---|---|
| `it.id in ids` (captured collection) | `e0.id in (:p1)` | Empty: no row matches. A `null` element is an error (`LQL-4008`) |
| `it.id !in ids` | `e0.id not in (:p1)`. The same for a column that can be NULL (NULL rows are not returned) | |
| `it.status.toInt() in 1..5` | `1 <= e0.status and e0.status < 6` | |
| `it.status.toInt() !in 1..5` | `1 > e0.status or e0.status >= 6` | NULL rows are not returned |
| `in 1 until 5`, `in 1..<5` | `1 <= e0.status and e0.status < 5` | |
| `it.status.toInt() in lo..hi` | `:p1 <= e0.status and e0.status <= :p2` | |
| `when { a -> true; b -> true; else -> false }` | `a or b` | |
| `when (it.status.toInt()) { 1, 2 -> true; else -> false }` | **Rejected** (`LQL-2010`) | Write it with `\|\|` |
| `it.customer?.id in sub.from(...).select { ... }` | `e0.customer.id in (select ...)` | Subquery, see [5.4](05-subquery.md#54-in) |
| `x !in sub.from(...).select { ... }` | `x not in (select ...)` (SQL meaning) | See [5.5](05-subquery.md#55-not-in-is-written-as-not-exists) |
| `it.id in listOf(1L, 2L)` (a list built inside the lambda) | **Rejected** (`LQL-3025`) | Build the list outside |

<!-- region: ManualKotlinNotesTest.kt#ranges -->
```kotlin
val closed = qb.from(Order::class.java).where { it.status.toInt() in 1..2 }
assertEquals("select e0 from Order e0 where 1 <= e0.status and e0.status < 3", closed.toHql().hql())
val outside = qb.from(Order::class.java).where { it.status.toInt() !in 1..2 }
assertEquals("select e0 from Order e0 where 1 > e0.status or e0.status >= 3", outside.toHql().hql())
val until = qb.from(Order::class.java).where { it.status.toInt() in 1 until 3 }
assertEquals("select e0 from Order e0 where 1 <= e0.status and e0.status < 3", until.toHql().hql())
val captured = qb.from(Order::class.java).where { it.status.toInt() in lo..hi }
assertEquals("select e0 from Order e0 where :p1 <= e0.status and e0.status <= :p2", captured.toHql().hql())
// a captured collection
val inIds = qb.from(Order::class.java).where { it.id in ids }
assertEquals("select e0 from Order e0 where e0.id in (:p1)", inIds.toHql().hql())
// a nullable target gets no null guard: NULL rows are returned by neither in nor !in (SQL)
val notIn = qb.from(Order::class.java).where { it.cnt !in counts }
assertEquals("select e0 from Order e0 where e0.cnt not in (:p1)", notIn.toHql().hql())
// when without a subject, with true / false branches: OR
val anyOf = qb.from(Order::class.java).where { when { it.status.toInt() == 3 -> true; it.price > 200.0 -> true; else -> false } }
assertEquals("select e0 from Order e0 where e0.status = 3 or e0.price > 200.0", anyOf.toHql().hql())
```

**A nullable subject gets no null guard**. Kotlin defines `null in 1..5` as false and `null !in 1..5` as true.
lambdaQL does not add a guard. A NULL subject is returned neither by `in` nor by `!in` (SQL rule).
If you want the NULL rows, write `it.cnt == null || it.cnt !in 1..5`.

The same is true for `!in` on a captured collection. `x not in (:p1)` in the example above does not return NULL rows (SQL meaning).

Example code: [`ManualKotlinNotesTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinNotesTest.kt) (`ranges`).

<a id="97-strings"></a>
## 9.7 Strings

`startsWith`, `endsWith` and `contains` (also with `ignoreCase`) on entity values become `like` ([3.8](03-where.md#38-strings)).
lambdaQL rejects other string functions with `TranslationException` (`LQL-3008`) when it reads the lambda. This covers `lowercase`, `length`, `isNullOrEmpty`, `isBlank` and `equals(s, ignoreCase = true)`.
Write the condition so that it compares the values directly.

| Kotlin | lambdaQL |
|---|---|
| `it.description.equals(text)` | Same as `==`: `e0.description = :p1` |
| `(it.description ?: "") == "x"` | `coalesce(e0.description, '') = 'x'` ([9.5](#-elvis)) |
| The string functions above | Rejected (`LQL-3008`) |
| String concatenation and templates (`"${it.description}-x"`, `"$a-$b" == it.description`, `it.description + "x"`) | Rejected (`LQL-2007`). A template that uses only captured values is also rejected, so build the string outside the lambda and capture it |

<a id="98-captured-values"></a>
## 9.8 Captured values

| What is captured | How lambdaQL uses it |
|---|---|
| `val` (also `Long?`, `String`, ...) | Bind parameter |
| `var` | Bind parameter. The value is read **each time the query runs** |
| A property of the outer class (`this.minStatus`, also `private`) | Read each time the query runs |
| A `val` of a `companion object`, a top-level `val`, a `val` of an `object` | Bind parameter |
| `const val` | Written into the HQL as a literal |
| Writing to a captured variable inside the lambda (`n++`) | **Rejected** |

<!-- region: ManualKotlinNotesTest.kt#order-repo -->
```kotlin
class OrderRepo(private val qb: QueryBuilder) {
    private val minStatus = 2

    fun open(lo: Int?) = qb.from(Order::class.java)
        .where { it.status >= minStatus }                // a private property: read each time the query runs
        .where { lo == null || it.status > lo }          // lo == null: no filter
}
```

<!-- region: ManualKotlinNotesTest.kt#order-repo-use -->
```kotlin
val all = repo.open(null)
val above = repo.open(2)
assertEquals("select e0 from Order e0 where e0.status >= :p1", all.toHql().hql())
assertEquals("select e0 from Order e0 where e0.status >= :p1 and e0.status > :p2", above.toHql().hql())
```

A captured `null` is allowed under a null guard on the same value (`lo == null || ...`). The guard is decided in the application when the query is built.
If `lo == null`, this `where` adds no filter. If there is a value, it becomes `e0.status > :p2`.

A captured `null` used in `==` or `!=` is bound as NULL, so no row matches (`LQL-4045` at `WARN` level).
An ordering comparison without a guard (`it.status > lo!!` when `lo == null`) binds NULL, so no row matches (`LQL-4045` at `DEBUG` level). `lo!!` and unboxing are not run.
An expression that calls a method on a captured value (`text!!.trim()` and so on) throws an error (`LQL-3027`) when the value is computed, if the value is `null`.

<!-- region: ManualKotlinNotesTest.kt#captured-null -->
```kotlin
// a captured null in == is bound as NULL: no row matches
val text: String? = null
val none = qb.from(Order::class.java).where { it.description == text }
assertEquals("select e0 from Order e0 where e0.description = :p1", none.toHql().hql())
assertEquals(0, none.toList().size)
// a method call on a captured null fails when the value is computed
val error = assertThrows(LambdaQLException::class.java) {
    qb.from(Order::class.java).where { it.description == text!!.trim() }.toList()
}
assertEquals("LQL-3027", error.code())
```

Example code: [`ManualKotlinNotesTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinNotesTest.kt) (`order-repo`, `order-repo-use`, `captured-null`).

<a id="99-property-references-and-scope-functions"></a>
## 9.9 Property references and scope functions

<a id="property-references"></a>
### Property references

| Form | Does it work? |
|---|---|
| A Kotlin property reference or Java getter reference passed to a condition (`where(Order::isActive)`) | No (`LQL-1009`). Use the lambda `where { it.isActive }` |
| The Java synthetic property `JOrder::price` | No. Kotlin itself does not compile it. |
| `Order::price.getter` and other `kotlin-reflect` APIs | No |
| A reference passed to your own Kotlin `fun interface` | No. Pass a lambda literal |

<a id="scope-functions-and-inline-functions-in-conditions"></a>
### Scope functions and inline functions in conditions

Your own `inline fun` works in conditions. The compiler copies the body into the lambda, so lambdaQL sees a normal condition.

<!-- region: ManualKotlinNotesTest.kt#inline-fun -->
```kotlin
/** Your own inline function: the compiler copies the body into the lambda, so lambdaQL reads a plain condition. */
private inline fun Order.isOpenInline() = status.toInt() == 1
```

<!-- region: ManualKotlinNotesTest.kt#scope-functions -->
```kotlin
val inline = qb.from(Order::class.java).where { it.isOpenInline() }     // inline fun Order.isOpenInline()
assertEquals("select e0 from Order e0 where e0.status = 1", inline.toHql().hql())
```

| Form | Supported |
|---|---|
| Your own `inline fun` | Yes |
| `?.let { }`, `run { }`, `with(it) { }`, `also { }` | No (`LQL-2013`). Write the condition directly |
| Your own non-inline extension function or extension property | No. Make it `inline` or write the condition directly |
| `takeIf { }` | No (`LQL-2021`) |

In update and insert lambdas, `apply`, `with` and `also` are rejected (`LQL-2003`, see [9.12](#912-update-insert-delete-property-assignment-and-dmlvalue)).

Example code: [`ManualKotlinNotesTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinNotesTest.kt) (`inline-fun`, `scope-functions`).

<a id="910-kotlin-operators"></a>
## 9.10 Kotlin operators

This table comes from compiler checks of 126 operator forms (kotlinc 2.3.21, section 9 of the design document KOTLIN.md).

| Operator | In a lambdaQL lambda |
|---|---|
| `< > <= >=` on numbers (use `!!` for nullable) | Supported |
| `< > <= >=` on `BigDecimal`, `String`, `LocalDate`, `BigInteger` | Supported (compiles to `compareTo`) |
| `x.compareTo(y) > 0` | Supported, read as `x > y` |
| `==`, `!=` | Supported ([9.4](#94-equality----and-areequal)) |
| `===`, `!==` | Supported, value comparison |
| enum `==` | Supported |
| entity `==` entity | **Rejected** (`LQL-3011`). Compare the ids |
| Value object (`@Embedded`) `==`, `!=` | Supported. Expanded per column (see [3.10](03-where.md#310-association-paths)). Ordering comparisons are `LQL-3012` |
| `+ - *`, unary `-` | Supported |
| `%` | Supported, written as `mod` |
| Integer `/` | **Careful.** The result differs by database (on MariaDB `5/2` is `2.5`, in Kotlin `2`). lambdaQL writes it as it is, as in `(e0.cnt / 2)` |
| `+ - * %` and unary `-` on `BigDecimal` / `BigInteger` | Supported, written as SQL operators |
| `BigDecimal` `/` | Supported (Kotlin uses `divide(x, HALF_EVEN)`). The result scale follows the DB. Other `divide` forms are rejected |
| `in a..b`, `until`, `..<` | Supported ([9.6](#96-in-ranges-and-when)) |
| `x in list`, `x !in list` | Supported ([9.6](#96-in-ranges-and-when)) |
| String `+`, string templates | **Rejected** (`LQL-2007`). Note: for NULL, Kotlin gives `"null"`, and SQL gives NULL |
| Boolean infix functions `and`, `or`, `xor` | **Rejected** (`LQL-2013`). Use `&&` and `\|\|` (they short-circuit, `and`/`or` do not) |
| `arr[0]` on a captured array | Treated as a captured value, or a clear error |
| `it.tags["k"]`, `it.lines[0]` on entity paths | **Rejected** |
| `operator` functions of entities (`plus`, `compareTo`), your own non-inline `infix` | **Rejected** |
| `@JvmInline value class` properties | **Rejected** |

<a id="no-kotlin-only-operator-api"></a>
### No Kotlin-only operator API

lambdaQL has **no Kotlin-only operator API** (decided). Normal Java method names already work as Kotlin operators.

| Kotlin form | Why it works | Status |
|---|---|---|
| `x in sub.from(...).select { ... }` | Kotlin turns `x in v` into `v.contains(x)` | Kept |
| `Agg.sum(x)!! + 1 > 3` | Normal arithmetic on a placeholder value | Kept |
| Sort operators (`+key`, `-key`) | Adds strange methods for Java users and needs Kotlin-only extensions | Rejected |
| An `invoke` operator for DB functions | Clashes with the way DB functions are declared (interface static methods) | Rejected |
| `o["price"]` | A string path. No type safety | Rejected |
| `infix` functions | Java methods cannot be `infix` | Not possible |
| `between` | Write `x in a..b` (needs `!!` for nullable and for `LocalDate?`) | No API needed |

<a id="911-results-data-classes-pair-and-triple"></a>
## 9.11 Results: data classes, `Pair` and `Triple`

The data classes in the examples below are `data class OrderRow(val id: Long?, val customerName: String?, val price: Double)` and `data class CustomerStat(val customerId: Int?, val count: Long, val total: Double?)`.

<!-- region: KotlinQueryTest.kt#data-class -->
```kotlin
val q = qb.from(Order::class.java).select { OrderRow(it.id, it.customer?.name, it.price) }
assertEquals("select e0.id, j1.name, e0.price from Order e0 left join e0.customer j1", q.toHql().hql())
```

<!-- region: KotlinQueryTest.kt#group-by -->
```kotlin
val q = qb.from(Order::class.java)
    .groupBy { it.customer?.id }
    .having { Agg.sum(it.price) > 200 }
    .select { CustomerStat(it.customer?.id, Agg.count(), Agg.sum(it.price)) }
assertEquals("select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id"
    + " having sum(e0.price) > 200.0", q.toHql().hql())
```

<!-- region: ManualKotlinNotesTest.kt#group-pair -->
```kotlin
// a Pair as the group key: two keys
val q = qb.from(Order::class.java)
    .where { it.customer != null }
    .groupBy { it.customer?.id to it.status }
    .select { Triple(it.customer?.id, it.status, Agg.count()) }
assertEquals("select e0.customer.id, e0.status, count(*) from Order e0 where e0.customer is not null"
    + " group by e0.customer.id, e0.status", q.toHql().hql())
```

Examples of `Pair` results and `listOf(...)` group keys are in [chapter 6](06-select-group.md).

| Rule | Notes |
|---|---|
| Use nullable types in data classes | `sum`, `avg`, `min` and `max` can be NULL. If NULL goes into a non-null parameter, an NPE happens when the object is made |
| `Pair`, `Triple` | Allowed as results and group keys |
| `listOf(...)` in `groupBy` | Allowed (the only place where a list built inside a lambda is allowed) |
| `arrayOf(...)`, `setOf(...)` in `groupBy` | Not allowed |
| Row type of a derived table or CTE | Only a Kotlin data class (or a Java record). The columns follow the `componentN` order |
| `Agg` functions | `import org.lambdaql.Agg.sum` works. It does not clash with Kotlin `sum()` / `count()` on collections |

Details: [6. Select, aggregates and group by](06-select-group.md).

Example code: [`KotlinQueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt) (`data-class`, `group-by`), [`ManualKotlinNotesTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinNotesTest.kt) (`group-pair`).

<a id="912-update-insert-delete-property-assignment-and-dmlvalue"></a>
## 9.12 Update, insert, delete: property assignment and `Dml.value`

A Kotlin property assignment compiles to the same setter call as in Java, so the rules are the same.

<!-- region: ManualKotlinDmlTest.kt#update-fk -->
```kotlin
// the FK column: the ?. assignment always assigns
val fk = qb.update(Order::class.java).set { it.customer?.id = cid }.where { it.id == first }
assertEquals("update Order e0 set e0.customer.id = :p1 where e0.id = :p2", fk.toHql().hql())
// no customer
val none = qb.update(Order::class.java).set { it.customer = null }.where { it.status.toInt() == 3 }
assertEquals("update Order e0 set e0.customer = null where e0.status = 3", none.toHql().hql())
// a captured entity: its @Id is bound
val entity = qb.update(Order::class.java).set { it.customer = kim }.where { it.id == third }
assertEquals("update Order e0 set e0.customer = :p1 where e0.id = :p2", entity.toHql().hql())
```

| Kotlin form | Meaning |
|---|---|
| `value(o.status, st)` | The function imported with `import org.lambdaql.Dml.value`. Use it for entities with `val` properties (no setter). It can be mixed with property assignments in one lambda (see [7.3](07-dml.md#73-two-ways-to-assign-property-or-dmlvalue)) |
| `it.cnt += 1` | Supported (see [7.2](07-2-update.md#add-to-a-value-default-for-null)) |
| `it.custName = it.description ?: "none"` | Supported: `coalesce(e0.description, 'none')` |
| `it.cnt++`, `--` | Rejected (`LQL-2003`). Write `it.cnt += 1` |
| `apply { }`, `with(it) { }`, `also { }` in `set` | Rejected (`LQL-2003`). Write direct assignments |
| `set { }` and `set { o, sub -> }` | Different overloads. Kotlin picks one by the number of parameters. The second argument `sub` is the starting point of subqueries |

Details: [7. Update, insert, delete](07-dml.md).

Example code: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt) (`update-fk`).

<a id="913-procedures-and-db-functions"></a>
## 9.13 Procedures and DB functions

The examples are in [8. Procedures and DB functions](08-procedures-functions.md). This section collects only the rules to know in Kotlin.

<a id="procedures"></a>
### Procedures

| Rule | Why |
|---|---|
| Use nullable return types (`Double?`) | A NULL OUT value fails for a non-null type. lambdaQL throws a named error |
| Do not use default arguments in stub functions | Kotlin turns the call into `...$default` and fills in the Kotlin default values. lambdaQL reports an error |
| Named arguments only with simple values (variables, constants) | With an expression, Kotlin makes a hidden local variable, and lambdaQL does not accept it |
| Use `exec` (not `call`) for a procedure with no return value | If both were `call`, it would be ambiguous in Java, and `run` clashes with Kotlin's `run { }` |
| Compile with `-java-parameters` | Then lambdaQL can check for swapped arguments of the same type ([8.4](08-procedures-functions.md#84-warning-argument-order)) |

<a id="db-functions-interface-companion-object--jvmstatic"></a>
### DB functions: interface `companion object` + `@JvmStatic`

A Kotlin interface has no static functions. Use `companion object` and `@JvmStatic`.
The body is never called. If you call it directly outside a lambda, it throws an exception.
When Kotlin calls a companion function, the bytecode opcodes first load the `Companion` object. lambdaQL skips that step and reads the annotation of the function.

| Declaration | Supported |
|---|---|
| Interface `companion object` + `@JvmStatic` | **Yes (recommended)** |
| Kotlin `object` + `@JvmStatic` | Yes |
| Top-level function | Yes, but not recommended: if you forget the import and the name is also a stdlib name (`maxOf`), Kotlin quietly calls the stdlib function |
| Extension function | No |
| `@SqlFunction` with default arguments | No |
| `inline` `@SqlFunction` | No (the call disappears) |
| `vararg` `@SqlFunction` | No |
| Function type property / functional interface constant | No |

A function on a column (`OrderFns.discount(it.price)!! > 10`) cannot use a normal index.
lambdaQL logs a WARN for it (see [8.9](08-procedures-functions.md#89-warning-functions-on-columns-and-indexes-sargable)).

<a id="914-kotlin-limits-kc1-to-kc21"></a>
## 9.14 Kotlin limits (KC1 to KC21)

If lambdaQL cannot read a lambda as a query, it throws an exception with a code
`[LQL-xxxx]`, the source line and a hint (see [11. Errors and languages](../11-errors-and-languages.md)).
KC1 to KC5 are found when the lambda is loaded. In this case lambdaQL stops at once.

| # | Do not write | Why | Write this instead |
|---|---|---|---|
| KC1 | Compiling with `-Xsam-conversions=class` | No `SerializedLambda` is made. The lambda becomes a class | Keep the default `-Xsam-conversions=indy` |
| KC2 | A query lambda made inside the body of an `inline fun` (normal, `reified`, `crossinline`) | The lambda becomes a class, not a lambda | Make the helper a non-inline function, or pass a lambda literal from the caller |
| KC3 | A callable reference to your own Kotlin `fun interface` (`kkey(Order::price)`) | It becomes an adapter class | The lambda literal `{ it.price }` |
| KC4 | A Kotlin function type value: `val f: (Order) -> Boolean = {...}; where(f)` | lambdaQL cannot read the inside of `Function1` | `where { ... }` with a literal |
| KC5 | `object : ...` (object expression), a functional interface that is not `Serializable` | Not a serializable lambda | A lambda literal |
| KC6 | `takeIf`, a general boolean `if (a) x else y` | Not supported (`LQL-2021`). An `if`/`when` that picks a value is supported (a bind parameter if it uses only captured values, `case when` if it uses entity values) | `&&` / `\|\|` |
| KC7 | *(removed by D25)* | – | – |
| KC8 | `in listOf(1, 2, 3)` or `setOf(...)` inside a lambda | A collection built inside the lambda | `val ids = listOf(1, 2, 3)` outside, then `it.id in ids` |
| KC9 | `isBlank()`, `isNullOrBlank()` | There is no portable SQL form for Unicode whitespace | Compare directly |
| KC10 | *(removed by D21)* | – | – |
| KC11 | Your own non-inline extension function or extension property, entity `operator` functions | lambdaQL cannot see inside the call | Make it an `inline fun` or write the condition directly |
| KC12 | `n++` on a captured variable | The lambda writes to a variable | Compute the value outside the lambda |
| KC13 | entity `==` entity (`it.customer == c`) | Not supported (`LQL-3011`) | `it.customer?.id == c.id` |
| KC14 | `isNaN()` | Not supported | Do not store NaN. See [10.5](10-rules-and-limits.md#105-kotlin-and-sql-differences) |
| KC15 | `@JvmField` entity properties | Field access, not a getter | A normal `var` / `val` |
| KC16 | Changed getter names: `internal` properties, `value class`, `@get:JvmName` | The getter cannot be mapped to a JPA attribute | A normal public property |
| KC17 | Entity values in string templates | String concatenation is not supported (`LQL-2007`) | Compare the columns separately |
| KC18 | The Java synthetic property reference `JOrder::price` | Kotlin compile error | The lambda `{ it.price }` |
| KC19 | `Order::price.getter` and other `kotlin-reflect` APIs | `kotlin-reflect` is not used | The lambda `{ it.price }` |
| KC20 | Other stdlib functions on columns (`coerceAtLeast`, `trim`, `toLocalDate`, ...) | Not in the allow list (`LQL-3008`) | A DB function with `@SqlFunction` |
| KC21 | Boolean elvis used as a value or sort key (`{ it.vip ?: false }`) | `coalesce` on a boolean fails on Oracle (ORA-00932) | Sort by `{ it.vip }` with a NULL position. In a condition, `it.vip ?: false` is fine |

<a id="915-where-lambdaql-differs-from-running-kotlin-code"></a>
## 9.15 Where lambdaQL differs from running Kotlin code

These differences are part of the design. They are not bugs.

| Kotlin code | lambdaQL (SQL) |
|---|---|
| `!!` throws an NPE | `!!` on a value is ignored. NULL follows the SQL rules. In `where`, a comparison that passes an association step through `!!` is true only for rows that have the association |
| `it.description != "x"` is `true` when `description == null` | `e0.description <> 'x'` is UNKNOWN for NULL, so the row is not returned. To include it, write `it.description == null \|\| ...` |
| `it.customer?.name == null` is `true` when there is no customer | The same: a step passed with `?.` becomes a `left join`, so orders without a customer are also returned. `it.customer!!.name == null` is true only for orders that have a customer, so they are not returned |
| A range with a NULL subject is false, and `!in` is `true` | The row is not returned |
| `===` is object identity | Value comparison |
| `BigDecimal` `==` compares the scale (`1.0 != 1.00`) | Value comparison |
| A non-null `String` type is never null | The column can still be NULL. lambdaQL uses the JPA metadata |
| Integer `/` drops the fraction | Can differ by DB (on MariaDB `5/2 = 2.5`) |

A comparison of a nullable number property with a constant (`it.cnt != 1`) also becomes `e0.cnt <> 1`, so rows where `cnt` is NULL are not returned. The result can differ from Kotlin ([9.3](#93-only-your-null-checks-count)).

More rules: [10. Rules and limits](10-rules-and-limits.md).

---

[Kotlin track](README.md) | Previous: [8. Procedures and DB functions](08-procedures-functions.md) | Next: [10. Rules and limits](10-rules-and-limits.md) | [Java version of this page: 9. Java notes](../java/09-java-notes.md)
