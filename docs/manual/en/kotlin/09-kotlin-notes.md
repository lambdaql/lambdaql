<a id="9-kotlin-notes"></a>
# 9. Kotlin notes

> [!IMPORTANT]
> This page is older than the Korean manual. See the [Korean page](../../ko/kotlin/09-kotlin-notes.md) for the latest rules.

[Kotlin track](README.md) | [Manual home](../README.md) | Previous: [8. Procedures and DB functions](08-procedures-functions.md) | Next: [10. Rules and limits](10-rules-and-limits.md) | [Java version of this page: 9. Java notes](../java/09-java-notes.md)

Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md)

lambdaQL supports Kotlin and Java with **the same engine** and the same API.
A Kotlin lambda and a Java lambda with the same meaning give the same HQL.
This chapter collects what is special in Kotlin, in one place.

<a id="contents"></a>
## Contents

- [9.1 Setup](#91-setup)
- [9.2 Lambda forms that work](#92-lambda-forms-that-work)
- [9.3 Only your null checks count](#93-only-your-null-checks-count)
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

- The comment under an example shows the HQL that lambdaQL makes, when the design states it.
- Constants written in the lambda (numbers, strings, booleans, `null`, enum constants, `const val`) are written into the HQL as literals. Captured and computed values are always bind parameters (`:p1`, `:p2`, ...) (D37).
- `e0` is the alias of the root entity, and `j1` is the alias of the join that lambdaQL adds for an association path. In `where` it is always an inner `join` (D48); when the path is used only in `orderBy` or `select`, it is a `left join` (D36). A subquery root is `s1`, `s2`, ... (see [5. Subqueries](05-subquery.md)). Examples of planned features (derived tables, CTEs, procedures) still use the planned aliases `o` and `c1`.
- Entities are the ones from [2. Getting started](02-getting-started.md#example-entities). Some examples use two extra properties on `Order`: `var flag: Boolean? = null` and `var kind: OrderKind? = null` (with `enum class OrderKind { NORMAL, EXPRESS, GIFT }`).

---

<a id="91-setup"></a>
## 9.1 Setup

| Item | Value |
|---|---|
| Kotlin | 2.x. The design was checked with kotlinc 1.7.22, 2.1.10, 2.2.21, 2.3.0 and 2.3.21 |
| JVM | Java 21 |
| Compiler option | Keep the default `-Xsam-conversions=indy`. Do **not** use `-Xsam-conversions=class` (KC1) |
| `-jvm-target` | 1.8, 11, 17 and 21 all work |
| Kotlin runtime | lambdaQL does not add `kotlin-stdlib` or `kotlin-reflect` to your dependencies |
| JPA entities | Normal Kotlin JPA entities (annotations on fields are fine). Use the usual `kotlin("plugin.jpa")` and all-open plugins |

Details: [2. Getting started](02-getting-started.md#kotlin-compiler-settings).

<a id="92-lambda-forms-that-work"></a>
## 9.2 Lambda forms that work

lambdaQL functions take Java `Serializable` functional interfaces.
In Kotlin you pass a **lambda literal**. Use the trailing lambda and `it`.

```kotlin
val list = qb.from(Order::class.java)
    .where { it.status == 1 }
    .where { it.price!! > 100.0 }        // several where() calls are joined with AND
    .toList()
// select e0 from Order e0 where e0.status = 1 and e0.price > 100.0
```

**About the SQL blocks.** `…` is the shortened select list (all entity columns), and `?` is a bind parameter. The lines come from [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java). It runs each query on H2 2.3, MariaDB 10.11 and Oracle 23.26 with Hibernate 6.6.5, and it fails if Hibernate's SQL changes. The test uses Java lambdas. A Kotlin lambda with the same meaning gives the same HQL, so it gets the same SQL.

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `to-hql`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>100.0
```

Lambdas with two or more parameters use names. Use `_` for a parameter you do not need.

```kotlin
.where { o, sub -> sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
.orderBy { o, by -> by.desc(o.price).asc(o.id) }
```

A Kotlin property is read with its getter. `it.price` is the same bytecode as the Java call `getPrice()`.
lambdaQL maps the getter to the JPA attribute (rule R10).
A boolean property `active` has the getter `getActive()`. lambdaQL maps it to `active`.

| Form | Works? |
|---|---|
| `where { it.active }` (lambda literal) | yes |
| A lambda inside `run { }`, `let { }`, `forEach { }`, a non-inline function, a `suspend` function | yes |
| A lambda passed from the caller to an `inline fun` parameter of a lambdaQL type | yes |
| A SAM constructor, also stored in a `val` first | yes |
| A property reference: `where(Order::active)`, `orderBy(Order::price)` | yes (R23). In `orderBy` not implemented yet |
| A Java getter reference for a Java entity: `orderBy(JOrder::getPrice)` | yes |
| `val f: (Order) -> Boolean = { it.active }; where(f)` | **no** (KC4) |
| `object : ...` (an object expression instead of a lambda) | **no** (KC5) |
| A lambda created inside the body of an `inline fun` | **no** (KC2) |
| Compiled with `-Xsam-conversions=class` | **no** (KC1) |

<a id="93-only-your-null-checks-count"></a>
## 9.3 Only your null checks count

lambdaQL **never runs your lambda**. It reads the bytecode and writes HQL.
So some Kotlin rules work differently. These four decisions are the base:

| Decision | Rule |
|---|---|
| D20, D36 | A path like `it.customer?.name` is **a column**, not a call chain. In `where`, a to-one association is an inner join by default: an order without a customer is not returned. To find those orders, write `it.customer == null`. A null guard like `it.customer == null \|\| ...` does not change the inner join (D48). To keep those orders, use `leftJoin`. |
| D22 | lambdaQL uses **only the null handling you wrote**: `== null`, `!= null`, `?.`, `?:`. The null checks that the Kotlin compiler adds by itself are ignored. Any other NULL follows SQL: the condition is UNKNOWN and the row is not returned. |
| D23 | `!!` is ignored. It is only there so that the code compiles. `it.customer!!.name` and `it.customer?.name` are the same column. |
| D12 | `==` between objects (and `===`) means **value equality**, not object identity. |

How can lambdaQL tell "your" null checks from the compiler's? The bytecode is different:

| Source | Bytecode shape | lambdaQL |
|---|---|---|
| You write `it.status == null` or `it.status != null` | `getStatus; ifnull / ifnonnull` | uses it: `is null`, `is not null` |
| You write `it.status == 1` with `status: Int?` | the compiler adds `dup; ifnonnull; pop; goto`, then unboxes | ignores the hidden check: `e0.status = 1` |
| You write `it.price!!` | `Intrinsics.checkNotNull(...)` | ignores it |

The result: Kotlin and Java give the same SQL. `it.status == 1` is `e0.status = 1`, and the same condition written in Java is also `e0.status = 1`.

<a id="94-equality----and-areequal"></a>
## 9.4 Equality: `==`, `!=`, `===` and `areEqual`

<a id="how-kotlin-compiles-"></a>
### How Kotlin compiles `==`

| Kotlin | Compiles to | lambdaQL rule | HQL |
|---|---|---|---|
| Two non-null primitives: `it.qty == 5` | a compare branch (like Java) | R1 | `i.qty = 5` |
| Nullable primitive and a constant: `it.status == 1` (`Int?`) | hidden null check + compare | R12 (hidden check ignored, D22) | `e0.status = 1` |
| `it.status != 1` (`Int?`) | the same | R12 | `e0.status <> 1` (NULL rows **not** returned) |
| References or nullable on both sides: `it.description == text`, `it.status == q` (`q: Int?`) | a call `Intrinsics.areEqual(a, b)` | R11: value equality | `e0.description = :p1` (a captured `null`: no row matches, `LQL-4045`) |
| `it.description != text` | `!Intrinsics.areEqual(a, b)` | R11, SQL meaning | `e0.description <> :p1` (NULL rows **not** returned) |
| Two values of the entity: `it.description == it.custName` | `Intrinsics.areEqual(a, b)` | R11, SQL meaning | `e0.description = e0.custName` (a NULL on either side: no match) |
| `it.id === q`, `!==` | `if_acmpeq` / `if_acmpne` | R17: value equality | the same as `==` |
| enum: `it.kind == OrderKind.GIFT` | `if_acmpne` | R2, R17 | `e0.kind = com.example.OrderKind.GIFT` (a literal with the full class name) |

`Intrinsics.areEqual` is the Kotlin runtime function behind `==`. lambdaQL recognizes it by name and reads it as **value equality**.
Values of the entity follow the SQL meaning (decision D48). A NULL on either side does not match, and NULL and NULL do not match either.
A captured `null` is bound as NULL and matches no row (`LQL-4045` at `WARN` level). To find NULL, write `x == null`.
When the result can differ from Kotlin, lambdaQL logs `LQL-5009` at `DEBUG` level.

<a id="plain--and-"></a>
### Plain `=` and `<>`

lambdaQL always writes a plain `=` and `<>` (decision D48).
It never writes `is [not] distinct from`. That form becomes `decode(...)` on Oracle and cannot use an index.

```kotlin
.where { it.description == text }    // text is "abc" -> e0.description = :p1
.where { it.description != "x" }     // e0.description <> 'x'   (a NULL description does not match)
.where { it.description == null || it.description != "x" }   // NULL rows too
```

> [!WARNING]
> **A Kotlin non-null type is not proof.** A `var code: String` property can still hold NULL from the database.
> This is because Hibernate writes the column value into the field without a check.
> lambdaQL looks at JPA metadata, not at the Kotlin type.

<a id="-and-"></a>
### `===` and `!==`

`===` means **value equality** in lambdaQL, the same as `==` (R17).
A query has no object identity. Kotlin itself gives `false` for two different `Long` boxes with the value 1000, but lambdaQL gives `true`.

### `BigDecimal`

In Kotlin, `==` on `BigDecimal` calls `equals`, which also compares the scale (`1.0 != 1.00`).
SQL `=` compares only the value. **lambdaQL uses value equality.**
`<`, `>`, `<=`, `>=` on `BigDecimal`, `String`, `LocalDate` and `BigInteger` are supported (they compile to `compareTo`).

<a id="booleans"></a>
### Booleans

```kotlin
.where { it.active }                       // e0.active = true
.where { !it.active }                      // not (e0.active = true)
.where { it.flag == true }                 // e0.flag = true
.where { it.flag != true }                 // e0.flag <> true   (NULL rows not returned: SQL meaning)
.where { it.active == (it.status!! > 1) }  // (e0.active = true and e0.status > 1) or (e0.active = false and e0.status <= 1)
```

Comparing two boolean expressions is supported (D25). lambdaQL expands it to `AND`/`OR` (no `CASE`), so the database can use indexes.
If the two sides share the same condition (for example `it.active == it.active`), it is rejected.

<a id="java-entities-from-kotlin-platform-types"></a>
### Java entities from Kotlin (platform types)

If your entity is a Java class, Kotlin sees its getters as platform types (`Long!`).
The rules are the same. `j.id == 1L` is `e0.id = 1`, and `j.customer.name` is the joined column (an inner join in `where` by default).

<a id="95-null-operators---and-"></a>
## 9.5 Null operators: `?.`, `?:` and `!!`

<a id="summary"></a>
### Summary

| Kotlin | Meaning in lambdaQL | Example HQL |
|---|---|---|
| `it.customer?.name` | The column `customer.name`. In `where` it is an inner join by default, so an order without a customer is not returned | `j1.name` + `join e0.customer j1` |
| `it.customer!!.name` | The same as `?.`. `!!` is ignored (D23) | `j1.name` + `join e0.customer j1` |
| `it.customer?.id` | Path ends with the target `@Id`: the FK column, no join | `e0.customer.id` |
| `it.customer == null` | Only orders without a customer | `e0.customer is null` (FK column, no join) |
| `it.customer != null` | Only orders that have a customer | `e0.customer is not null` (FK column, no join) |
| `it.price!! > 10.0` | `!!` is ignored. It is only for the compiler | `e0.price > 10.0` |
| `it.description ?: ""` (a value) | default value | `coalesce(e0.description, '')` |
| `it.flag ?: false` (a condition) | NULL counts as false | `(e0.flag is not null and e0.flag = true)` |
| `lo!!` (a captured value) | `!!` is ignored. A null `lo` binds NULL, so no row matches (`LQL-4045` at `DEBUG` level, see [9.8](#98-captured-values)) | `:p1` |

<a id="-and--on-association-paths"></a>
### `?.` and `!!` on association paths

```kotlin
.where { it.customer?.name == "Kim" }
// select e0 from Order e0 join e0.customer j1 where j1.name = 'Kim'

.where { it.customer!!.name == "Kim" }
// the same HQL

.where { it.customer?.id == 5L }
// select e0 from Order e0 where e0.customer.id = 5      (FK column, no join)
```

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `path-literal`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name='Kim'
```

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `fk-literal`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id=5
```

A path is **a column**, not a call chain (D20). In `where`, lambdaQL uses an inner join by default (D36).
So an order without a customer is not returned, and you do not need `it.customer != null &&` before the path.

**`?.` does not bring back orders without a customer.** In Kotlin, `it.customer?.name == null` is `true` when there is no customer.
In lambdaQL it returns only orders whose customer has no name. A null guard does not change this, because the path is an inner join (D48).

```kotlin
.where { it.customer == null }
// select e0 from Order e0 where e0.customer is null      (FK column, no join)

.where { it.customer == null || it.customer?.name == null }
// only orders whose customer has no name: the inner join leaves out orders without a customer
// select e0 from Order e0 join e0.customer j1 where e0.customer is null or j1.name is null
```

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `no-association`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id is null
```

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `null-guard-name-null`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.id is null or c1_0.name is null
```

`?.` also works with a function at the end: `it.description?.length` is `length(e0.description)`.

<a id="-is-just-for-the-compiler"></a>
### `!!` is just for the compiler

Kotlin does not compile `it.price > 10.0` when `price` is `Double?`.
Write `it.price!! > 10.0`. lambdaQL ignores `!!`. There is no NPE, because the lambda never runs.
Rows where `price` is NULL are not returned (SQL rule).

<a id="-elvis"></a>
### `?:` (elvis)

In values (select, DML `set`, sort keys), `?:` becomes `coalesce` (rule R15):

```kotlin
.select { it.description ?: "" }                 // coalesce(o.description, '')
.select { it.customer?.name ?: "none" }          // coalesce(c1.name, 'none') + left join
.select { (it.price ?: 0.0) * 2 }                // coalesce(o.price, 0.0) * 2
qb.update(Order::class.java).set { it.price = (it.price ?: 0.0) + 1 }.all().execute()
// set o.price = coalesce(o.price, 0.0) + 1.0
```

In a **string** condition, lambdaQL keeps the column free of functions (SARGable rule):

```kotlin
.where { (it.description ?: "") == "x" }   // e0.description = 'x'
.where { (it.description ?: "") == "" }    // (e0.description = '' or e0.description is null)
```

In a **number** condition, the design writes `coalesce(column, default)`:

```kotlin
.where { (it.price ?: 0.0) > 3.0 }            // coalesce(e0.price, 0.0) > 3.0
.where { (it.description?.length ?: 0) > 3 }  // coalesce(length(e0.description), 0) > 3
```

A function on the column means a normal index on `price` is not used.
Tip: in the first example NULL becomes 0, and `0 > 3` is false. So `it.price!! > 3.0` returns the same rows
and gives `e0.price > 3.0`, which can use the index.

**Boolean elvis** is supported **in conditions**:

```kotlin
.where { it.flag ?: false }       // (e0.flag is not null and e0.flag = true)
.where { !(it.flag ?: false) }    // (e0.flag is null or not (e0.flag = true))
```

Boolean elvis as a **value or sort key** (`select { it.flag ?: false }`) is not supported (KC21).
`coalesce` on a boolean fails on Oracle (ORA-00932). For sorting, use the key `{ it.flag }` and choose
the NULL position with `by.asc(key, nullsFirst)` or `by.desc(key, nullsFirst)` ([4. Sorting and paging](04-order-paging.md#43-direction)).

<a id="smart-casts-and-let"></a>
### Smart casts and `?.let`

A null check before use is a normal condition:

```kotlin
.where { it.description != null && it.description!!.length > 3 }
// (e0.description is not null and length(e0.description) > 3)

.where { it.description?.let { d -> d.length > 3 } == true }
// (e0.description is not null and length(e0.description) > 3)
```

<a id="-in-an-assignment"></a>
### `?.` in an assignment

In `set { }` and `values { }`, `it.customer?.id = cid` **always assigns**. The `?.` is ignored (D23).

<a id="96-in-ranges-and-when"></a>
## 9.6 `in`, ranges and `when`

All forms in this section are supported in v0.1 (decisions D21 and D29). Details and more examples: [3.9](03-where.md#39-in-with-a-captured-collection), [3.14](03-where.md#314-ranges-kotlin-only), [3.15](03-where.md#315-when-kotlin-only).

| Kotlin | HQL | Note |
|---|---|---|
| `it.id in ids` (captured collection) | `e0.id in (:p1)` | empty: FALSE. `null` element: error |
| `it.id !in ids` | `e0.id not in (:p1)`. The same for a column that can be NULL (NULL rows are not returned) | empty: TRUE |
| `it.status in 1..5` | `e0.status between 1 and 5` | |
| `it.status !in 1..5` | `not (e0.status between 1 and 5)` | NULL rows are not returned |
| `it.status in 1 until 5`, `in 1..<5` | `e0.status >= 1 and e0.status < 5` | |
| `it.status in lo..hi` | `e0.status between :p1 and :p2` | |
| `it.status in 5 downTo 1` | supported | same rows as `1..5` |
| `... step 2` | **rejected** | |
| `when (it.status) { 1, 2 -> true; else -> false }` | `e0.status in (1, 2)` | `Int`, `String` and enum subjects |
| `when { a -> true; b -> true; else -> false }` | `a or b` | |
| `it.customer?.id in sub.from(...).select { ... }` | `o.customer.id in (select ...)` | subquery, see [5.4](05-subquery.md#54-in) |
| `x !in sub.from(...).select { ... }` | `x not in (select ...)` (SQL meaning) | see [5.5](05-subquery.md#55-not-in-is-written-as-not-exists) |
| `it.status in listOf(1, 2)` (list made in the lambda) | **rejected** (KC8) | make the list outside |

**Nullable subjects get no null guard** (D22). Kotlin defines `null in 1..5` as false, and `null !in 1..5` as true.
lambdaQL does not add a guard. A NULL subject is not returned by `in` **and** not returned by `!in` (SQL rule).
If you want NULL rows, write `it.status == null || it.status !in 1..5`.

`!in` with a captured collection is the same. `x not in (:p1)` does not return NULL rows (SQL meaning, decision D48).
If you want NULL rows, write `it.status == null || it.status !in ids`.

<a id="97-strings"></a>
## 9.7 Strings

These Kotlin string forms have a design (rule R21). They follow the SARGable rule where they can.

| Kotlin | HQL | Note |
|---|---|---|
| `it.description!!.startsWith("A")` | `e0.description like :p1 escape '\'` | lambdaQL escapes `%`, `_` and `\` in your value |
| `it.description!!.endsWith(s)` | `e0.description like :p1 escape '\'` | |
| `it.description!!.contains(s)` | `e0.description like :p1 escape '\'` | |
| `it.description?.startsWith("A") == true` | the same `like` | also supported |
| `startsWith(s, ignoreCase = true)`, `equals(s, ignoreCase = true)` | depends on the column (see below) | D24 |
| `it.description.equals(text)` | the same as `==` | |
| `it.description!!.lowercase() == "abc"` | `lower(e0.description) = 'abc'` | a function on the column: a normal index is not used |
| `it.description!!.length > 3` | `length(e0.description) > 3` | the length unit can differ by DB |
| `it.description.isNullOrEmpty()` | `(e0.description is null or e0.description = :p1)` | on Oracle `''` is NULL |
| `isBlank()`, `isNullOrBlank()` | **rejected** (KC9) | use `isNullOrEmpty()` or a direct comparison |
| `trim()`, `coerceAtLeast()`, `toLocalDate()` and other stdlib calls on a column | **rejected** (KC20) | planned (function mapping) |
| `"$a-$b" == it.description` (only captured values in the template) | `:p1 = e0.description` | designed: the string is built in the application. The analysis core rejects all string joining today, so build the string before the lambda |
| `"${it.description}-x"` (entity value in a template) | **rejected** (KC17) | planned |

**`ignoreCase = true` (D24).** lambdaQL does not write `lower(col) = lower(:p)`, because that cannot use an index.
At start, it reads DB metadata for the column and picks a form the DB can use:

| What the DB has | HQL |
|---|---|
| Case-insensitive collation (MariaDB `_ci`) | `col = :p` / `col like :p` |
| Oracle function index on `lower(col)` | `lower(col) = :p` (the value is lowercased in the application) |
| Oracle linguistic index | `col collate binary_ci = :p` |
| Nothing | WARN at start (strict mode: error) |

Unicode case rules (for example `ſ`, `İ`) follow the DB collation, not Kotlin.

<a id="98-captured-values"></a>
## 9.8 Captured values

| Captured | How lambdaQL uses it |
|---|---|
| `val` (also `Long?`, `String`, ...) | bind parameter |
| `var` | bind parameter. The value is read **each time the query runs** |
| A property of the enclosing class (`this.minStatus`, also `private`) | read each time the query runs |
| `companion object` `val`, top-level `val`, `object` `val` | bind parameter |
| `const val` | written into the HQL as a literal (D37) |
| Writing a captured variable inside the lambda (`n++`) | **rejected** (KC12) |

```kotlin
class OrderRepo(private val qb: QueryBuilder) {
    private val minStatus = 2

    fun open(lo: Int?) = qb.from(Order::class.java)
        .where { it.status!! >= minStatus }
        .where { lo == null || it.status!! > lo }   // lo == null: no filter; otherwise e0.status > :p2
        .toList()
}
```

A captured `null` in `==` and `!=` is bound as NULL, so no row matches (`LQL-4045` at `WARN` level).
A captured `null` is allowed under a null guard on the same value (`lo == null || ...`). The guard is decided in the application when the query runs, so with `lo == null` this `where` adds no filter.
A size comparison without a guard (`it.status!! > lo!!` with `lo == null`) binds NULL, so no row matches (`LQL-4045` at `DEBUG` level). `lo!!` and the unboxing are not run.
Only an expression that calls a method on a captured value (`text.trim()`, `lo.toLong()` and so on) throws an error (`LQL-4012`) when the value is `null`.
A captured `NaN` or `Infinity` is always an error.

<a id="99-property-references-and-scope-functions"></a>
## 9.9 Property references and scope functions

<a id="property-references"></a>
### Property references

```kotlin
.orderBy(Order::price)       // the same as orderBy { it.price } (not implemented yet)
.where(Order::active)        // the same as where { it.active }
```

| Form | Works? |
|---|---|
| Kotlin `Order::price` passed to a lambdaQL function | yes (R23). **Not implemented yet** in `orderBy`: use `orderBy { it.price }` for now |
| Java getter reference `JOrder::getPrice` | yes |
| Java synthetic property `JOrder::price` | no. Kotlin itself does not compile it (KC18). Use `JOrder::getPrice` |
| `Order::price.getter` and other `kotlin-reflect` APIs | no (KC19) |
| A reference passed to your own Kotlin `fun interface` | no (KC3). Pass a lambda literal |

<a id="scope-functions-and-inline-functions-in-conditions"></a>
### Scope functions and inline functions in conditions

These are supported in conditions. The compiler puts the body inline, so lambdaQL sees a normal condition.

```kotlin
.where { it.description?.let { d -> d.length > 3 } == true }
.where { it.run { status == 1 && active } }
.where { with(it) { status == 1 } }
.where { it.isOpenInline() }   // your own function: inline fun Order.isOpenInline() = status == 1
```

| Form | Supported |
|---|---|
| `?.let { }`, `run { }`, `with(it) { }`, `also { }` | yes, in conditions |
| Your own `inline fun` | yes |
| Your own non-inline extension function or property | no (KC11). Make it `inline`, or write the condition directly |
| `takeIf { }` | no (KC6) |

In update and insert lambdas, `apply`, `with` and `also` are not supported in v0.1 (see [9.12](#912-update-insert-delete-property-assignment-and-dmlvalue)).

<a id="910-kotlin-operators"></a>
## 9.10 Kotlin operators

This table comes from a compiler check of 126 operator forms (kotlinc 2.3.21, design KOTLIN.md section 9).

| Operator | In a lambdaQL lambda |
|---|---|
| `< > <= >=` on numbers (with `!!` for nullable) | supported |
| `< > <= >=` on `BigDecimal`, `String`, `LocalDate`, `BigInteger` | supported (they compile to `compareTo`) |
| `x.compareTo(y) > 0` | supported, read as `x > y` |
| `==`, `!=` | supported (value equality with the SQL meaning, [9.4](#94-equality----and-areequal)) |
| `===`, `!==` | supported, value equality |
| enum `==` | supported |
| enum `<`, `>` | supported. lambdaQL expands it to `in (...)` of the enum constants, so it works with both ORDINAL and STRING mapping |
| entity `==` entity | **rejected** (KC13). Compare ids |
| value object (`@Embedded`) `==`, `!=` | supported. Expanded column by column (B6, see [3.10](03-where.md#310-association-paths)). A size comparison is `LQL-3012` |
| `+ - *`, unary `-` | supported |
| `%` | supported, rendered as `mod` |
| integer `/` | **take care.** Databases give different results (MariaDB `5/2` is `2.5`, Kotlin gives `2`). The final rule is not decided; it may be rejected in v0.1. Under a guard (`qty != 0 && total / qty > 2`) it is always rejected |
| `BigDecimal` / `BigInteger` `+ - * %`, unary `-` | supported, rendered as SQL operators |
| `BigDecimal` `/` | supported (Kotlin uses `divide(x, HALF_EVEN)`). The result scale follows the DB. Other `divide` forms are rejected |
| `in a..b`, `until`, `..<`, `downTo` | supported ([9.6](#96-in-ranges-and-when)). `step` is not |
| `x in list`, `x !in list` | supported ([9.6](#96-in-ranges-and-when)) |
| String `+`, string templates with entity values | not supported (planned). Note: for NULL, Kotlin gives `"null"`, SQL gives NULL |
| Boolean infix `and`, `or`, `xor` | **rejected**. Use `&&` and `\|\|` (they short-circuit; `and`/`or` do not) |
| `arr[0]` on a captured array | treated as a captured value, or a clear error |
| `it.tags["k"]`, `it.lines[0]` on entity paths | **rejected** |
| `operator` functions on your entity (`plus`, `compareTo`), your own non-inline `infix` | **rejected** (KC11) |
| `@JvmInline value class` property | **rejected** (KC16) |

<a id="no-kotlin-only-operator-api"></a>
### No Kotlin-only operator API

lambdaQL has **no Kotlin-only operator API** (decided). The normal Java method names already work as Kotlin operators:

| Kotlin form | Why it works | Status |
|---|---|---|
| `x in sub.from(...).select { ... }` | Kotlin turns `x in v` into `v.contains(x)` | kept |
| `Agg.sum(x)!! + 1 > 3` | normal arithmetic on the placeholder value | kept |
| sort operators (`by + key`, `-key`) | would add strange methods for Java users and need Kotlin-only extensions | rejected |
| `invoke` operator for DB functions | clashes with D31 (interface static functions) | rejected |
| `o["price"]` | a string path; no type safety | rejected |
| `infix` functions | Java methods cannot be `infix` | not possible |
| `between` | write `x in a..b` (nullable needs `!!`, `LocalDate?` needs `!!`) | no API needed |

<a id="911-results-data-classes-pair-and-triple"></a>
## 9.11 Results: data classes, `Pair` and `Triple`

```kotlin
data class OrderView(val id: Long?, val customerName: String?, val price: Double?)
data class Stat(val customerId: Long?, val cnt: Long, val total: Double?)

qb.from(Order::class.java).select { o -> OrderView(o.id, o.customer?.name, o.price) }   // data class
qb.from(Order::class.java).select { o -> o.customer?.name to o.price }                  // Pair

qb.from(Order::class.java)
    .groupBy { it.customer?.id }
    .having { Agg.sum(it.price)!! > 1000 }
    .select { Stat(it.customer?.id, Agg.count(), Agg.sum(it.price)) }

qb.from(Order::class.java).groupBy { listOf(it.customer?.id, it.status) }   // several keys
qb.from(Order::class.java).groupBy { it.customer?.id to it.status }         // Pair is also fine
```

| Rule | Note |
|---|---|
| Use nullable types in data classes | `sum`, `avg`, `min`, `max` can be NULL. A NULL in a non-null parameter throws NPE when the object is built (J4) |
| `Pair`, `Triple` | allowed as results and as group keys |
| `listOf(...)` in `groupBy` | allowed (this is the only place where a list made in the lambda is allowed) |
| `arrayOf(...)`, `setOf(...)` in `groupBy` | not allowed |
| Row type of a derived table or CTE | Kotlin data class (or Java record) only. Columns follow the `componentN` order (J5) |
| `Agg` functions | `import org.lambdaql.Agg.sum` works. No clash with Kotlin `sum()` / `count()` on collections |

Details: [6. Select, aggregates and group by](06-select-group.md).

<a id="912-update-insert-delete-property-assignment-and-dmlvalue"></a>
## 9.12 Update, insert, delete: property assignment and `Dml.value`

A Kotlin property assignment compiles to the same setter call as Java, so the rules are the same.

```kotlin
qb.update(Order::class.java).set { it.status = st; it.price = it.price!! * 1.1 }.where { it.status == 1 }.execute()
qb.update(Order::class.java).set { o -> value(o.status, 1) }.where { it.id == id }.execute()     // for val properties (Dml.value)
qb.update(Order::class.java).set { it.customer?.id = cid }.where { it.id == id }.execute()       // ?. assignment = normal assignment
qb.update(Order::class.java).set { it.price = (it.price ?: 0.0) + 1 }.all().execute()            // ?: -> coalesce

qb.delete(Order::class.java).where { it.customer?.name == null }.execute()

qb.insert(Order::class.java).values { it.id = id; it.price = p; it.status = 1 }.execute()
qb.insert(Order::class.java).values(lines) { o, l -> o.id = l.id; o.price = l.price; o.status = 1 }.execute()
```

| Kotlin form | Meaning |
|---|---|
| `value(o.status, 1)` | the function imported with `import org.lambdaql.Dml.value` (D49). Use it for entities with `val` properties (no setter). It can be mixed with property assignments in one lambda (tested) |
| `value(o.cnt, o.cnt + 1)` | supported |
| `it.cnt += 1` | supported |
| `it.cnt++`, `--` | not in v0.1. Write `it.cnt += 1` |
| `apply { }`, `with(it) { }`, `also { }` in `set` | not in v0.1. Write direct assignments |
| `set { }` and `set { o, sub -> }` | different overloads; Kotlin picks by the number of parameters. The second argument `sub` is the starting point for subqueries (D49) |

Details: [7. Update, insert, delete](07-dml.md).

<a id="913-procedures-and-db-functions"></a>
## 9.13 Procedures and DB functions

<a id="procedures"></a>
### Procedures

```kotlin
interface OrderProcs {
    @SqlProcedure("SQ_CALC") fun calc(id: Long, rate: Double): Double?
    @SqlProcedure("SP_RECENT") fun recent(custId: Long): List<OrderView>
    @SqlProcedure("SP_PURGE") fun purge(days: Int)
}

val d = qb.call(OrderProcs::class.java) { it.calc(id, rate) }.execute()
val d2 = qb.call(OrderProcs::class.java) { it.calc(rate = rate, id = id) }.execute()   // named arguments
qb.exec(OrderProcs::class.java) { it.purge(30) }.execute()                            // no return value
```

| Rule | Why |
|---|---|
| Use a nullable return type (`Double?`) | A NULL OUT value in a non-null type fails. lambdaQL throws a named error |
| No default arguments in a stub function | Kotlin changes the call to `...$default` and fills in the Kotlin default. lambdaQL reports an error |
| Named arguments only with simple values (variables, constants) | With an expression, Kotlin creates hidden local variables, which lambdaQL does not accept in v0.1 |
| Use `exec` (not `call`) for a procedure with no return value | `call` for both would be ambiguous in Java, and `run` would clash with Kotlin's `run { }` |
| Compile with `-java-parameters` | lambdaQL can then check swapped arguments of the same type ([8.4](08-procedures-functions.md#84-warning-argument-order)) |

<a id="db-functions-interface-companion-object--jvmstatic"></a>
### DB functions: interface `companion object` + `@JvmStatic`

Kotlin interfaces have no static functions. Use a `companion object` and `@JvmStatic`:

```kotlin
interface OrderFns {
    companion object {
        @JvmStatic @SqlFunction("FN_DISCOUNT")
        fun discount(price: Double?): Double? = throw DbOnlyException()
    }
}

qb.from(Order::class.java).where { OrderFns.discount(it.price)!! > 10 }
// where function('FN_DISCOUNT' as Double, o.price) > 10
val v = qb.call { OrderFns.discount(price) }.execute()     // call the function alone
```

The body is never run. It only throws if someone calls it outside a lambda.
When Kotlin calls a companion function, the bytecode first loads the `Companion` object. lambdaQL skips that step and reads the annotation of the function.

| Declaration | Supported |
|---|---|
| Interface `companion object` + `@JvmStatic` | **yes (recommended)** |
| Kotlin `object` + `@JvmStatic` | yes |
| Top-level function | yes, but not recommended: if you forget the import and the name is also a stdlib name (`maxOf`), Kotlin silently calls the stdlib function |
| Extension function | no (KC11) |
| `@SqlFunction` with default arguments | no |
| `inline` `@SqlFunction` | no (the call disappears) |
| `vararg` `@SqlFunction` | no in v0.1 |
| Function type property / functional interface constant | no |

A function on a column (`OrderFns.discount(it.price)!! > 10`) cannot use a normal index.
lambdaQL logs a WARN for it (see [8.9](08-procedures-functions.md#89-warning-functions-on-columns-and-indexes-sargable)).

<a id="914-kotlin-limits-kc1-to-kc21"></a>
## 9.14 Kotlin limits (KC1 to KC21)

lambdaQL does not guess. When it cannot analyze a form, it throws a clear error with a code
`[LQL-xxxx]`, the source line, and a hint (see [11. Errors and languages](../11-errors-and-languages.md)).
KC1 to KC5 are found when the lambda is loaded. lambdaQL stops at once in these cases.

| # | Do not write | Why | Write this instead |
|---|---|---|---|
| KC1 | Compile with `-Xsam-conversions=class` | No `SerializedLambda` is created; the lambda becomes a class | Keep the default `-Xsam-conversions=indy` |
| KC2 | A query lambda created inside an `inline fun` body (plain, `reified` or `crossinline`) | The lambda becomes a class, not a lambda | Make the helper non-inline, or pass the lambda literal from the caller |
| KC3 | A callable reference to your own Kotlin `fun interface` (`kkey(Order::price)`) | It becomes an adapter class | A lambda literal `{ it.price }`, or pass `Order::price` to the lambdaQL (Java) function |
| KC4 | A Kotlin function type value: `val f: (Order) -> Boolean = {...}; where(f)` | lambdaQL cannot read inside `Function1` | `where { ... }` with the literal |
| KC5 | `object : ...` (object expression), a functional interface that is not `Serializable` | Not a serializable lambda | A lambda literal |
| KC6 | `if`/`when`/`takeIf` that choose a value; a general `if (a) x else y` | Value branches are not in v0.1 | `&&` / `\|\|`. Value `CASE WHEN` is planned |
| KC7 | *(removed by D25)* | Comparing two boolean expressions is supported now | – |
| KC8 | `in listOf(1, 2, 3)` or `setOf(...)` inside the lambda | A collection built in the lambda | `val ids = listOf(1, 2, 3)` outside, then `it.status in ids` |
| KC9 | `isBlank()`, `isNullOrBlank()` | Unicode whitespace has no portable SQL form | `isNullOrEmpty()` or a direct comparison |
| KC10 | *(removed by D21)* | Ranges and `when` are supported now. Only if these forms are late, the error tells you to write `==` / `\|\|` chains for a while | – |
| KC11 | Your own non-inline extension function or property; entity `operator` functions | lambdaQL cannot see inside the call | Make it `inline fun`, or write the condition directly |
| KC12 | `n++` on a captured variable | The lambda writes a variable | Compute the value outside the lambda |
| KC13 | Entity `==` entity (`it.customer == c`) | Not in v0.1 (planned: identifier equality) | `it.customer?.id == c.id` |
| KC14 | `isNaN()` | Not supported | Do not store NaN; see [10.5](10-rules-and-limits.md#105-kotlin-and-sql-differences) |
| KC15 | `@JvmField` entity property | Field access, not a getter | A normal `var` / `val` |
| KC16 | Mangled getter names: `internal` property, `value class`, `@get:JvmName` | The getter cannot be mapped to a JPA attribute | A public normal property |
| KC17 | Entity values in a string template | String concatenation is not supported yet | Compare the columns separately |
| KC18 | Java synthetic property reference `JOrder::price` | Kotlin compile error | `JOrder::getPrice` |
| KC19 | `Order::price.getter` and other `kotlin-reflect` APIs | `kotlin-reflect` is not used | `Order::price` |
| KC20 | Other stdlib functions on columns (`coerceAtLeast`, `trim`, `toLocalDate`, ...) | Not in the allowed list | Planned (function mapping), or a DB function with `@SqlFunction` |
| KC21 | Boolean elvis as a value or sort key (`{ it.flag ?: false }`) | `coalesce` on a boolean fails on Oracle (ORA-00932) | Sort by `{ it.flag }` with a NULL position. In conditions `it.flag ?: false` is fine |

<a id="915-where-lambdaql-differs-from-running-kotlin-code"></a>
## 9.15 Where lambdaQL differs from running Kotlin code

These differences are part of the design. They are not bugs.

| Kotlin code | lambdaQL (SQL) |
|---|---|
| `!!` throws NPE | `!!` is ignored. NULL follows SQL rules |
| `it.status != 1` with `status == null` is `true` | The hidden null check is ignored: `e0.status <> 1` is UNKNOWN for NULL, so the row is not returned. Write `it.status == null \|\| ...` to include it |
| `it.customer?.name == null` is `true` when there is no customer | In `where` the path is an inner join by default, so an order without a customer is not returned. To include it, use `leftJoin` with `c == null \|\| c.name == null`, or run a second query with `it.customer == null` |
| A `when` or range with a NULL subject goes to `else`, and `!in` is `true` | The row is not returned, also under `NOT` |
| `===` is object identity | Value equality |
| `BigDecimal` `==` compares scale (`1.0 != 1.00`) | Value equality |
| `ignoreCase` uses Kotlin Unicode rules | Uses the DB collation (`ſ`, `İ` can differ) |
| `String.length` counts UTF-16 units | H2 counts UTF-16 units, MariaDB counts code points |
| `Double.NaN` comparisons | A captured NaN is an error. A stored NaN follows the DB |
| A non-null type `String` is never null | A column can still be NULL; lambdaQL uses JPA metadata |
| Integer `/` drops the fraction | Can differ by DB (MariaDB `5/2 = 2.5`) |
| String templates turn NULL into `"null"` | Not supported yet. SQL concatenation with NULL gives NULL |

More rules: [10. Rules and limits](10-rules-and-limits.md).

---

[Kotlin track](README.md) | Previous: [8. Procedures and DB functions](08-procedures-functions.md) | Next: [10. Rules and limits](10-rules-and-limits.md) | [Java version of this page: 9. Java notes](../java/09-java-notes.md)

