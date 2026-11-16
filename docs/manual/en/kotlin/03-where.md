# 3. Conditions (`where`) (Kotlin)

> [!NOTE]
> **Status**
> - **Implemented and tested on H2, MariaDB and Oracle:** `where` conditions and `toList()` (mapping to columns, HQL output, running the query). The tests use **Java** lambdas. **Kotlin lambdas are not tested yet.**
> - **Designed, not implemented yet:** the Kotlin-only forms in this chapter (`?.`, `?:`, `!!`, ranges, `when`, `in`). Their bytecode was compiled and checked with prototypes (kotlinc 1.7.22 to 2.3.21), and many conditions were checked row by row on H2. Subqueries, `select` and procedures are also designed only. The core of sorting and paging ([4. Sorting and paging](04-order-paging.md)), update/insert/delete/upsert ([7. Update, insert, delete](07-dml.md)) and the Spring Data JPA module ([12. Spring Data JPA](12-spring-data-jpa.md)) are implemented.
> - The HQL in this chapter is the **planned** output. Small details (for example alias names like `e0`, `j1`) can still change.

[Kotlin track](README.md) | [Manual home](../README.md) | Previous: [2. Getting started](02-getting-started.md) | Next: [4. Sorting and paging](04-order-paging.md) | [Java version of this page](../java/03-where.md)

Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md)

---

## 3.1 The idea in one sentence

**A lambdaQL lambda is a query, not code.** lambdaQL reads the bytecode of the lambda. It never runs it.
So Kotlin run-time rules (`NullPointerException` from `!!`, object identity for `===`, the null checks that the compiler adds) do not apply.
lambdaQL uses its own rules. This chapter explains them.

If a form has no rule, lambdaQL does not guess. It throws an exception with a clear message (see [3.13](#313-what-is-rejected)).

## 3.2 Example entities

All examples use these entities (full code in [2. Getting started](02-getting-started.md#example-entities)):

```kotlin
@Entity class Order(
    @Id var id: Long? = null,
    var price: Double? = null,
    var status: Int? = null,
    var active: Boolean = false,          // primitive: never NULL
    var description: String? = null,
    @ManyToOne var customer: Customer? = null,
    var updateAt: LocalDateTime? = null,
    @OneToMany(mappedBy = "order") var items: MutableList<Item> = mutableListOf(),
)
@Entity class Customer(@Id var id: Long? = null, var name: String? = null, var grade: Int? = null)
@Entity class Item(@Id var id: Long? = null, @ManyToOne var order: Order? = null, var price: Double? = null, var qty: Int = 0)
```

A few examples need one more property. They say so, for example "`flag: Boolean?`".

## 3.3 Basic form

```kotlin
val qb = QueryBuilder(entityManagerFactory)

val list = qb.from(Order::class.java)
    .where { it.status == 1 && it.price!! > 100.0 }
    .toList()
// HQL: select e0 from Order e0 where e0.status = 1 and e0.price > 100.0
```

**About the SQL blocks.** `…` is the shortened select list (all entity columns), and `?` is a bind parameter. The lines come from [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java). It runs each query on H2 2.3, MariaDB 10.11 and Oracle 23.26 with Hibernate 6.6.5, and it fails if Hibernate's SQL changes. The test uses Java lambdas. A Kotlin lambda with the same meaning gives the same HQL, so it gets the same SQL.

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `to-hql`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>100.0
```

There are no Kotlin tests yet. The same queries written in Java are tested in [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java) (H2, MariaDB and Oracle). See [the Java version of this page](../java/03-where.md) for the tested examples.

You can call `where()` many times. The conditions are joined with `AND`:

```kotlin
qb.from(Order::class.java)
    .where { it.status == 1 }
    .where { it.active }
    .toList()
// HQL: select e0 from Order e0 where e0.status = 1 and e0.active = true
```

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `many-where`:

```sql
-- H2
select …
from orders o1_0
where o1_0.status=1 and o1_0.active=true

-- MariaDB, Oracle
select …
from orders o1_0
where o1_0.status=1 and o1_0.active=1
```

**Values in the HQL.** The rule depends on where the value comes from (decision D37):

| Value | In the HQL | Example |
|---|---|---|
| A constant written in the lambda: a number (also a decimal), a string, `true`/`false`, `null`, an enum constant, a `const val` | a **literal** | `it.price!! > 100.0` → `e0.price > 100.0` |
| A captured value: a local `val` or `var`, a property, a function parameter | a **bind parameter** | `it.price!! > minPrice` → `e0.price > :p1` |
| A computed value: `BigDecimal("1.5")`, `form.text.trim()`, `LocalDateTime.of(...)` | a **bind parameter** | `:p1` |

- A constant is the same on every call. So the SQL text does not change, and the database can see the value when it plans the query.
- A quote in a string constant is escaped: `"O'Brien"` becomes `'O''Brien'`. There is no SQL injection risk, because the string is a constant compiled into your class, not user input.
- A captured value can come from a user, so it is **always** a bind parameter. Only the value changes from call to call.
- An enum constant is written with its full class name, for example `e0.kind = com.example.OrderKind.GIFT`.

**`!!` is only for the compiler.** Kotlin does not compile `it.price > 100.0` when `price` is `Double?`. Write `it.price!! > 100.0`. lambdaQL ignores `!!` (decision D23). Rows where `price` is NULL are not returned.

## 3.4 Comparisons

| Kotlin | HQL | Note |
|---|---|---|
| `it.status == 1` | `e0.status = 1` | |
| `it.status != 1` | `e0.status <> 1` | NULL rows are not returned (see [3.6](#36-null-rules)) |
| `it.price!! > p` | `e0.price > :p1` | also `<`, `<=`, `>=` |
| `it.updateAt!! > since` | `e0.updateAt > :p1` | dates compare with `<`, `>` in Kotlin |
| `it.updateAt!!.isAfter(since)` | `e0.updateAt > :p1` | `isBefore` gives `<` |
| `it.description!! < "m"` | `e0.description < 'm'` | also `BigDecimal`, `LocalDate`, `BigInteger` |
| `it.status!!.compareTo(3) > 0` | `e0.status > 3` | `compareTo(..) op 0` becomes `op` |

A comparison with a nullable column follows **SQL meaning**:
if the column is NULL, the result is UNKNOWN and the row is not returned. (Running the Kotlin code, `!!` would throw a `NullPointerException`.)

**Arithmetic.** `+`, `-`, `*` and unary `-` are supported. `%` is written as `mod`.
Avoid integer `/`: databases do not agree on the result (MariaDB gives `5/2 = 2.5`, Kotlin gives `2`). The final rule for `/` is not decided yet; it may be rejected in the first version.

**Floating point:** for `Float`/`Double` columns, lambdaQL does not promise the same bit-exact result as Kotlin.
Captured `NaN` or `Infinity` values are rejected when the query runs.

## 3.5 AND, OR, NOT

| Kotlin | HQL |
|---|---|
| `a && b` | `a and b` |
| `a \|\| b` | `a or b` |
| `!a` | `not (a)` |
| parentheses | kept as needed |

```kotlin
.where { (it.status == 1 || it.active) && it.price!! > minPrice }
// HQL: (e0.status = 1 or e0.active = true) and e0.price > :p1

.where { !(it.status == 1 && it.price!! > minPrice) }
// HQL: not (e0.status = 1 and e0.price > :p1)
```

Rules:

- The result follows **SQL three-valued logic** (TRUE / FALSE / UNKNOWN). lambdaQL has checked that this gives the same rows on H2, MariaDB and Oracle.
- The compiler sometimes moves `!` around (De Morgan, `!(x > y)` to `x <= y`). This does not change the result.
- lambdaQL **does not simplify your condition**. If you write `it.price!! > p || it.price!! <= p`, it is sent as written. Rows where `price` is NULL are not returned. This is normal SQL behavior.
- Use `&&` and `||`. The infix functions `and`, `or`, `xor` are rejected (they do not short-circuit).

**`if` and `when` that give `true`/`false`.** Only these short forms are allowed. They are the same as `AND`, `OR`:

| Kotlin | Same as |
|---|---|
| `if (c) x else false` | `c && x` |
| `if (c) true else x` | `c \|\| x` |
| `when { a -> true; b -> true; else -> false }` | `a \|\| b` |

Any other `if (c) x else y`, and any `if` or `when` that picks a **value** (`it.status == (if (flag) 1 else 2)`), is rejected (limit KC6).
If the choice only depends on captured values, compute the value before the lambda:

```kotlin
val wanted = if (flag) 1 else 2
.where { it.status == wanted }
```

## 3.6 NULL rules

This is the most important part. There are two simple principles:

1. **Where you wrote a null check, lambdaQL uses it.** (`== null`, `!= null`, `?.`, `?:`, and `==` between two nullable values)
2. **Where you did not, lambdaQL uses SQL meaning.** A NULL column makes the comparison UNKNOWN, and the row is not returned.

The Kotlin compiler adds **hidden** null checks to the bytecode (for example for `it.status == 1` when `status` is `Int?`). lambdaQL **ignores** them (decision D22). Only the null checks in your source code count.
So a Kotlin lambda and a Java lambda with the same meaning give the same SQL.

### 3.6.1 Rule table

| # | Kotlin form | HQL | NULL behavior |
|---|---|---|---|
| R12 | Nullable column and a constant or a non-null value: `it.status == 1`, `it.id == id` (`id: Long`) | `e0.status = 1`, `e0.id = :p1` | SQL meaning. NULL column: row not returned |
| R12 | `it.status != 1` | `e0.status <> 1` | SQL meaning. NULL column: row **not** returned |
| R1 | Order compare: `it.price!! > p`, `it.status!! <= 3` | `e0.price > :p1`, `e0.status <= 3` | SQL meaning |
| R11 | Two values that can both be NULL: `it.status == q` (`q: Int?`), `it.description == text` (`String?`), enum `==` | `e0.status is not distinct from :p1` | **null-safe value compare.** `null == null` is true. Only one side null: false |
| R11 | `it.status != q` (both nullable) | `e0.status is distinct from :p1` | null-safe. NULL rows **are** returned when `q` is not null |
| R3 | `x == null`, `x != null` | `x is null`, `x is not null` | |
| R17 | `===`, `!==` | the same as `==`, `!=` | value compare, not object identity |
| R9 | Captured values | bind parameters | see [3.6.4](#364-captured-null-values) |

### 3.6.2 `==` compares values

Kotlin `==` already compares values. In lambdaQL, `===` also compares **values** (rule R17).
A query has no object identity. Two different `Long` boxes with the same value are equal.

```kotlin
val status: Int? = request.status    // may be null
.where { it.status == status }
// HQL: e0.status is not distinct from :p1
//   status = 3    -> rows with status 3
//   status = null -> rows with status NULL
```

How Hibernate writes `is not distinct from` for each database (checked):

| Database | SQL |
|---|---|
| H2 | `status is not distinct from ?` |
| MariaDB | `status <=> ?` |
| Oracle | `decode(status, ?, 0, 1) = 0` |

When lambdaQL knows that one side can never be NULL, it writes a simple `=` instead. The result is the same, and the database can use an index (see [10.2](10-rules-and-limits.md#102-sargable-rule-index-friendly-sql)).
"Never NULL" means: an `@Id`, a `nullable = false` column, a primitive property (`Int`, `Long`, `Double`, `Boolean`), or a captured value that is not null when the query runs.

```kotlin
.where { it.description == text }    // text is not null when the query runs
// HQL: e0.description = :p1
.where { it.description != text }
// HQL: (e0.description <> :p1 or e0.description is null)
```

**A column that can never be NULL: `it.id == id`.** In the example entity, `id` is a `Long?`. Kotlin compiles `it.id == id` (with a captured `id: Long`) to a null check and a compare.
A plain translation would be `not (e0.id is null or e0.id <> :p1)`. That form is correct, but the database cannot use the index well.
The id can never be NULL, so `is null` on it is always false. lambdaQL folds it away and writes `e0.id = :p1`.

The same rule holds for a primitive property and a `nullable = false` column, but only when the path has **no association step**. `it.customer?.id` can be NULL, because an order can have no customer, so its null check stays.

```kotlin
.where { it.id == id }              // id: Long (captured)
// HQL: e0.id = :p1
.where { it.id == null }
// HQL: 1=0
.where { it.customer?.id == null }
// HQL: e0.customer.id is null
```

The same HQL is tested with Java lambdas in [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java) (region `never-null`, see [the Java version of this page](../java/03-where.md#362--on-objects-compares-values)). The Java test writes the condition that Kotlin's `it.id == id` compiles to.

> [!WARNING]
> **A Kotlin non-null type is not proof.** A property `var code: String` can still hold NULL from the database,
> because Hibernate writes the column value into the field without a check.
> lambdaQL looks at the JPA metadata, not at the Kotlin type.

### 3.6.3 Write your null checks yourself

```kotlin
// Only rows where status is not NULL and not 1:
.where { it.status != 1 }
// HQL: e0.status <> 1

// Also include rows where status is NULL:
.where { it.status == null || it.status != 1 }
// HQL: e0.status is null or e0.status <> 1
```

> [!IMPORTANT]
> This is different from running the Kotlin code. In Kotlin, `null != 1` is `true`.
> In lambdaQL, `it.status != 1` does **not** return NULL rows, because you did not write a null check.
> A constant like `1` is not nullable. The same condition written in Java gives the same SQL.

Compare a constant and a nullable variable:

```kotlin
val q: Int? = 3
.where { it.status != 3 }    // e0.status <> 3                                 NULL rows not returned
.where { it.status != q }    // (e0.status <> :p1 or e0.status is null)        NULL rows returned
```

The second form compares two values that can both be NULL. Kotlin defines the result for null, so lambdaQL keeps it (null-safe).

`it.description.equals(text)` on a `String?` is the same as `==` (null-safe).

More about `?.`, `?:` and `!!`: [9. Kotlin notes](09-kotlin-notes.md).

### 3.6.4 Captured null values

| Form | Captured value is `null` |
|---|---|
| null-safe forms: `it.status == q`, `it.description != text` | allowed |
| order compare: `it.price!! > min!!`, every `<`, `<=`, `>`, `>=` | **exception** when the query runs |
| a null element in a collection used with `in` | **exception** when the query runs |

Why: Kotlin would throw a `NullPointerException` here (`min!!`). Turning it into `IS NULL` would return rows that Kotlin never returns.

**Exception to the rule: a null guard.** If the same captured value is checked for null first, null is allowed:

```kotlin
val min: Double? = request.minPrice   // may be null
.where { min == null || it.price!! > min }
// min == null:  select e0 from Order e0                        (no filter)
// min = 100.0:  select e0 from Order e0 where e0.price > :p1
```

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `captured-null-guard`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
```

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `captured-null-guard#2`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.price>?
```

How it works:

- The parts that use only captured values and constants are decided in the application when the query runs. This is done from left to right, with short-circuit, like Kotlin.
- With `min == null`, the guard is true, so the whole condition is true. There is no filter. The compare with `min` is never written (Kotlin never runs it either).
- With `min = 100.0`, the guard is false, so only `e0.price > :p1` is left.
- The lambda is still analyzed only once. Only the HQL text depends on the value.
- The guard must come first. `it.price!! > min!! || min == null` with `min == null` throws when the query runs, the same as Kotlin's `NullPointerException`.

There are no Kotlin tests for this yet. The same guard written in Java is tested; see [the Java version of this page](../java/03-where.md#364-captured-null-values).

## 3.7 Boolean columns

In the example entity, `active` is a Kotlin `Boolean` (a JVM primitive, never NULL).
The rows with `flag` are for a nullable column `var flag: Boolean? = null`.

| Kotlin | HQL | NULL rows |
|---|---|---|
| `it.active` | `e0.active = true` | – (never NULL) |
| `!it.active` | `e0.active = false` | – |
| `it.flag == true` | `e0.flag is not distinct from true` | not returned |
| `it.flag != true` | `e0.flag is distinct from true` | **returned** (like Kotlin) |
| `it.flag ?: false` | `(e0.flag is not null and e0.flag = true)` | not returned |
| `!(it.flag ?: false)` | `(e0.flag is null or not (e0.flag = true))` | **returned** |
| `it.flag!!` | `e0.flag = true` | not returned (`!!` is ignored) |

Notes:

- lambdaQL does not use `is not true` or `coalesce(flag, false)`. They fail on Oracle (ORA-00932).
- Negation of a bigger condition is always written as `not (...)`. lambdaQL never writes `(...) = false` or `(...) is null` around a condition, because Oracle 23 evaluates those wrong in `WHERE`.
- `it.flag ?: false` is fine **in a condition**. As a value or a sort key it is rejected (limit KC21).

**Comparing two boolean values** (`==`/`!=`) is supported (decision D25):

```kotlin
.where { it.active == (it.status!! > s) }
// HQL: (e0.active = true and e0.status > :p1) or (e0.active = false and e0.status <= :p1)
```

lambdaQL writes it with `AND`/`OR` (not `CASE`), so the database can use indexes.
If the two sides share the same condition (for example `it.active == it.active`), the form is rejected.

## 3.8 Strings

- `==` and `equals` on strings use the **database collation**.
  - MariaDB `_ci` collations ignore upper/lower case and trailing spaces.
  - Oracle stores `''` (empty string) as NULL. So `it.description == ""` is different from Kotlin on Oracle.
- Kotlin string functions on a column (`startsWith`, `endsWith`, `contains`, `lowercase()`, `length`, `isNullOrEmpty()`, `ignoreCase = true`) have a Kotlin design (rule R21). See [9. Kotlin notes](09-kotlin-notes.md#97-strings).
- `isBlank()`, `isNullOrBlank()`, `trim()` and other stdlib functions on a column are rejected (limits KC9, KC20).
- A string template that uses **only captured values** is designed to work: `"$a-$b" == it.description` gives `:p1 = e0.description`, and the string is built in the application. The analysis core still rejects all string joining inside a lambda today, so for now build the string before the lambda.
- A string template or `+` with **entity values** (`"${it.description}-x"`) is not supported yet (KC17, planned).

## 3.9 IN with a captured collection

Build a collection **outside** the lambda and use `in`:

```kotlin
val ids = listOf(1L, 2L, 3L)

qb.from(Order::class.java).where { it.id in ids }.toList()
// HQL: e0.id in (:p1)

qb.from(Order::class.java).where { it.id !in ids }.toList()
// HQL: e0.id not in (:p1)
```

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `not-in`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.id not in (?,?)
```

`!in` gives `not in`. The id can never be NULL, so there is no `is null` part. For a column that can be NULL, lambdaQL adds `x is null or`, because Kotlin's `!in` is true for a NULL value (when the list has no `null`).

| Case | Result |
|---|---|
| The collection is a captured `List`, `Set` or other `Collection` | supported |
| The collection has a `null` element | exception when the query runs |
| Empty collection | `in` is FALSE, `!in` is TRUE (Hibernate writes `1=0` / `1=1`) |
| 1,500 elements | worked in one statement on H2, MariaDB and Oracle 23ai (older Oracle versions have a 1,000 limit; not tested) |
| The collection is an entity collection: `item in it.items` | **rejected**. Use an `exists()` subquery ([5. Subqueries](05-subquery.md)) |
| The collection is made inside the lambda: `it.status in listOf(1, 2)` | **rejected** (KC8). Make it outside and capture it |

Kotlin also has ranges (`it.status in 1..5`) and `when` with constants. See [3.14](#314-ranges-kotlin-only) and [3.15](#315-when-kotlin-only).

## 3.10 Association paths

`it.customer?.name` is **not run**. It only names a column: "the `name` column of the joined customer".
lambdaQL checks each step with the JPA Metamodel:

| Path step | HQL | Example |
|---|---|---|
| to-one association (`@ManyToOne`, `@OneToOne`) | `join` with an alias (inner by default, see below) | `it.customer?.name` -> `join e0.customer j1` ... `j1.name` |
| embedded (`@Embedded`) | same table, no join | if `Order` had an embedded `address`: `it.address.city` -> `e0.address.city` |
| path that ends with the target `@Id` | foreign key column, no join | `it.customer?.id` -> `e0.customer.id` |
| the association itself, compared with `null` | foreign key column, no join | `it.customer == null` -> `e0.customer is null` |
| collection (`@OneToMany`, `@ManyToMany`) | **rejected** | `it.items...` -> use an `exists()` subquery |

`it.customer?.name`, `it.customer!!.name` and (for a Java entity) `it.customer.name` all mean the **same column** `j1.name`.

In `where`, an association path is an **inner join** by default (decision D36):

```kotlin
qb.from(Order::class.java)
    .where { it.customer?.name == name }
    .toList()
// HQL: select e0 from Order e0 join e0.customer j1 where j1.name = :p1
```

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `inner-join`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name=?
```

- An order **without a customer** is not in the result. There is no customer row to join.
- lambdaQL does not add a null check for you. You do not need `it.customer != null &&` before the path.

**A null guard keeps orders without a customer.** If the condition can be true for an order without a customer, lambdaQL uses a `left join` for that path.
With an inner join, Kotlin and SQL would give different rows: Kotlin returns the order, but the inner join would lose it (decision D36).

```kotlin
.where { it.customer == null || it.customer?.name == name }
// HQL: select e0 from Order e0 left join e0.customer j1 where e0.customer is null or j1.name = :p1

.where { it.customer != null && it.customer?.name == name }
// HQL: select e0 from Order e0 join e0.customer j1 where e0.customer is not null and j1.name = :p1
```

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `null-guard`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.id is null or c1_0.name=?
```

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `null-guard#2`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.id is not null and c1_0.name=?
```

After the `left join`, Hibernate writes `c1_0.id is null` for `e0.customer is null`. The meaning is the same: the order has no customer.

- Other parts of an `||` can keep the order too. `it.status == 1 || it.customer?.name == name` also uses a `left join`, because in Kotlin an order with status 1 matches even without a customer.
- If another `where` call removes those orders anyway (for example `.where { it.customer?.name != null }`), the join stays inner.
- There are no Kotlin tests for this yet. The same conditions written in Java are tested; see [the Java version of this page](../java/03-where.md#310-association-paths).

To find orders **without** a customer, compare the association itself. This checks the foreign key column, with no join:

```kotlin
.where { it.customer == null }
// HQL: select e0 from Order e0 where e0.customer is null
```

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `no-association`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id is null
```

> [!WARNING]
> **`?.` does not bring back orders without a customer.** In Kotlin, `it.customer?.name == null` is `true` when there is no customer.
> In lambdaQL, the path is an inner join here, so `it.customer?.name == null` returns only orders whose customer has no name.
> To include orders without a customer, write `it.customer == null || it.customer?.name == null`. This null guard makes the path a `left join` (see above).

More facts:

- The same path gets one alias. It is shared by `where`, `orderBy` and `select`.
- `it.customer?.id == 5L` uses the foreign key column `e0.customer.id`. No join is needed.
- A path that is used **only** in `orderBy` or `select` is a `left join`, so no rows are lost there (rules S8 and J1). If `where` uses the same path too, `orderBy` and `select` use the `where` join. It is an inner join unless a null guard made it a `left join`.
- Comparing two entities (`it.customer == c`) is **planned** (KC13). For now compare ids: `it.customer?.id == c.id` (here `c` is a captured object).

## 3.11 Captured values

A lambda can use values from outside: local values, properties, and properties of captured objects.

```kotlin
val since = LocalDateTime.of(2026, 1, 1, 0, 0)
.where { it.updateAt!! > since }                 // local val
.where { it.status == defaultStatus }            // property of the enclosing class
.where { it.price!! > filter.minPrice!! }        // property of a captured object
.where { it.updateAt!! > LocalDateTime.of(2026, 1, 1, 0, 0) }   // a call that does not use the entity
```

| Captured | How lambdaQL uses it |
|---|---|
| `val` (also `Long?`, `String`, ...) | bind parameter |
| `var` | bind parameter. The value is read **each time the query runs** |
| A property of the enclosing class (also `private`) | read each time the query runs |
| `companion object` `val`, top-level `val`, `object` `val` | bind parameter |
| `const val` | written into the HQL as a literal (D37) |
| Writing a captured variable inside the lambda (`n++`) | **rejected** (KC12) |

Rules:

- The part that does not use the entity is computed in the application **each time the query runs**. The result becomes a bind parameter.
- The lambda is analyzed once and the result is cached. New captured values are read on every call.
- Only the entity part becomes SQL.

## 3.12 Errors that depend on order of evaluation

SQL does not promise to evaluate `AND`/`OR` from left to right. So a "guard" like this is not safe in SQL:

```kotlin
qb.from(Item::class.java)
    .where { it.qty != 0 && it.price!! / it.qty > 2 }   // rejected
```

`/`, `%` and casts under a guard are rejected. Write the condition without division, for example `it.qty > 0 && it.price!! > 2.0 * it.qty` (the same rows when `qty` is positive).

## 3.13 What is rejected

lambdaQL throws an exception with a code `[LQL-xxxx]` and the source line. It does not guess.
Most shapes are found when the lambda is analyzed (first use). Bad captured values (null, NaN) are found when the query runs.
See [11. Errors and languages](../11-errors-and-languages.md).

| Form | Example | What to do |
|---|---|---|
| Local `val`/`var`, loops, a block with many statements | `{ val x = 1; it.status == x }` | Write one expression |
| Value `if`/`when` (KC6) | `it.status == (if (flag) 1 else 2)` | Compute the value before the lambda |
| General `if (c) x else y` (KC6) | | Use the allowed forms in [3.5](#35-and-or-not) |
| Infix `and`, `or`, `xor` | `it.active and (it.status == 1)` | Use `&&`, `\|\|` |
| String templates or `+` with entity values (KC17) | `it.description == "A" + it.status` | Compare the columns separately (planned) |
| Collection made in the lambda (KC8) | `it.status in listOf(1, 2)` | Make it outside and capture it |
| Lambdas inside the lambda | `ids.any { id -> id == it.id }` | Use `in` ([3.9](#39-in-with-a-captured-collection)) or a subquery |
| Stdlib functions that are not supported (KC9, KC20) | `it.description?.trim()`, `isBlank()` | `isNullOrEmpty()` or a direct comparison |
| Your own non-inline extension function (KC11) | `it.isOpen()` | Make it `inline fun`, or write the condition |
| Other methods on the entity | `it.calcSomething()` | Use properties only |
| Entity collection paths | `it.items.size > 0` | `exists()` subquery |
| Entity compared to entity (KC13) | `it.customer == c` | Compare ids (planned: entity compare) |
| `/`, `%`, cast under a guard | see [3.12](#312-errors-that-depend-on-order-of-evaluation) | Remove the guard or change the condition |
| Null captured value in an order compare | `it.price!! > min!!` with `min == null` | Add a null guard |
| `NaN` / `Infinity` captured value | `it.price!! > Double.NaN` | Do not send NaN |
| `isNaN()` (KC14) | `it.price!!.isNaN()` | – |
| Writing a captured variable (KC12) | `{ n++; it.active }` | Compute outside the lambda |
| Anonymous object, function type value (KC4, KC5) | `where(f)` with `val f: (Order) -> Boolean` | Pass the lambda literal |

Exception types (all extend `LambdaQLException`):

| Exception | When |
|---|---|
| `LambdaAnalysisException` | The lambda cannot be read (not a serializable lambda, compiled as a class, and so on) |
| `UnsupportedLambdaShapeException` | The shape is not supported (loop, value `if`, ...) |
| `TranslationException` | The meaning cannot be translated (unknown function, collection path, ...) |

All Kotlin limits: [9. Kotlin notes](09-kotlin-notes.md#914-kotlin-limits-kc1-to-kc21). All rules: [10. Rules and limits](10-rules-and-limits.md).

## 3.14 Ranges (Kotlin only)

All these forms are supported (decision D21).

```kotlin
.where { it.status in 1..5 }          // e0.status between 1 and 5
.where { it.status !in 1..5 }         // not (e0.status between 1 and 5)
.where { it.status in 1 until 5 }     // e0.status >= 1 and e0.status < 5
.where { it.status in 1..<5 }         // e0.status >= 1 and e0.status < 5
.where { it.status in lo..hi }        // e0.status between :p1 and :p2   (lo, hi captured)
.where { it.price!! in 1.0..10.0 }    // e0.price between 1.0 and 10.0
.where { it.status in 5 downTo 1 }    // supported (same rows as 1..5)
```

| Range form | Supported |
|---|---|
| `a..b`, `a until b`, `a..<b`, `a downTo b` | yes |
| `!in` | yes, `not (...)` |
| `LocalDate`, `LocalDateTime`, `BigDecimal`, `String` ranges | yes (they compile to `compareTo`) |
| `step` | no |

**Nullable subjects.** Kotlin accepts `in` for `Int?` and `Long?`. lambdaQL does **not** add a null guard (D22).
Rows with NULL are not returned, and they are also not returned under `!in` (SQL rule).
For other nullable types (for example `LocalDateTime?`) Kotlin does not compile `in`; add `!!`:

```kotlin
.where { it.updateAt!! in from..to }  // e0.updateAt between :p1 and :p2
```

## 3.15 `when` (Kotlin only)

`when` is supported **as a condition**: every branch must end with `true` or `false` (or a condition).
lambdaQL turns it into `IN`, `OR` or `AND`.

```kotlin
.where { when (it.status) { 1, 2 -> true; else -> false } }
// e0.status in (1, 2)

.where { when (it.description) { "a", "b", "c" -> true; else -> false } }
// e0.description in ('a', 'b', 'c')

.where { when (it.status) { in 1..5 -> true; else -> false } }
// e0.status between 1 and 5

.where { when { it.status == 1 -> true; it.active -> true; else -> false } }
// e0.status = 1 or e0.active = true

.where { when (it.status) { 1 -> it.active; else -> false } }
// e0.status = 1 and e0.active = true
```

An enum subject also works (here with an extra property `kind: OrderKind?`, and `OrderKind` in the package `com.example`):

```kotlin
.where { when (it.kind) { OrderKind.EXPRESS, OrderKind.GIFT -> true; else -> false } }
// e0.kind in (com.example.OrderKind.EXPRESS, com.example.OrderKind.GIFT)
```

A nullable subject gets no null guard (D22). Rows where the subject is NULL are not returned,
also under `NOT`. Running the Kotlin code would go to `else`; lambdaQL uses SQL meaning. This is the intended rule.

A `when` (or `if`) that **chooses a value** is not supported (KC6):

```kotlin
// Not supported
.where { (if (it.active) it.price else 0.0)!! > 10.0 }
```

Write the condition with `&&` and `||` instead.

---

[Kotlin track](README.md) | Previous: [2. Getting started](02-getting-started.md) | Next: [4. Sorting and paging](04-order-paging.md) | [Java version of this page](../java/03-where.md)
