<a id="10-rules-and-limits-kotlin"></a>
# 10. Rules and limits (Kotlin)

> [!IMPORTANT]
> This page is older than the Korean manual. See the [Korean page](../../ko/kotlin/10-rules-and-limits.md) for the latest rules.

[Kotlin track](README.md) | [Manual home](../README.md) | Previous: [9. Kotlin notes](09-kotlin-notes.md) | Next: [11. Errors and languages](../11-errors-and-languages.md) | [Java version of this page](../java/10-rules-and-limits.md)

Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md)

<a id="contents"></a>
## Contents

- [10.1 The base rule: a lambda is a query](#101-the-base-rule-a-lambda-is-a-query)
- [10.2 SARGable rule (index friendly SQL)](#102-sargable-rule-index-friendly-sql)
- [10.3 NULL rules](#103-null-rules)
- [10.4 Operator rules (R1 to R24)](#104-operator-rules-r1-to-r24)
- [10.5 Kotlin and SQL differences](#105-kotlin-and-sql-differences)
- [10.6 Database differences](#106-database-differences)
- [10.7 Not supported, and what to write instead](#107-not-supported-and-what-to-write-instead)
- [10.8 Caching: a lambda is analyzed once](#108-caching-a-lambda-is-analyzed-once)
- [10.9 Supported environment](#109-tested-environment)

How to read the examples:

- The `assertEquals` string in an example is the HQL that lambdaQL makes.
- Constants written directly in the lambda (numbers, strings, booleans, `null`, enum constants) are written into the HQL as literals.
  Captured values and computed values are always bind parameters (`:p1`, `:p2`, ...).
- Entities: `Order` (`id`, `price`, `status`, `active`, `description`, `customer`, `updateAt`, `items`),
  `Customer` (`id`, `name`, `grade`), `Item` (`order`, `price`, `qty`). See [2. Getting started](02-getting-started.md#example-entities).
  The rows in 10.3 also use the `Order` properties `cnt` (`Integer`), `custName` (`String`) and `address` (`@Embedded`, `city` and `zip`).
  The examples with a Boolean that can be NULL use `vip` of the example entity `Coded`. The example entities are Java classes, so the boolean `active` is read as `it.isActive` in Kotlin.

---

<a id="101-the-base-rule-a-lambda-is-a-query"></a>
## 10.1 The base rule: a lambda is a query

lambdaQL does not call the lambda. It reads the bytecode opcodes and makes HQL.
A lambda is a query written in Kotlin syntax. So:

1. A path such as `it.customer?.name` **names a column**. It is not a chain of calls.
   lambdaQL does not check if the customer is null. In `where`, it is a `left join` if an order without a customer can match the condition, and an inner join if it cannot.
   So `it.status == 1 || it.customer?.name == "Kim"`, `it.customer?.name == null` and null guards keep orders without a customer, as in Kotlin.
   A comparison passed with `!!` or with no mark is true only for orders that have a customer. You can mix `?.` and `!!`.
   To find only orders without a customer, compare the association itself: `it.customer == null`.
2. NULL is handled **only where you write it** (`== null`, `!= null`, `?.`, `?:`).
   The null checks that the Kotlin compiler adds are ignored. Any other NULL follows the SQL rules. Rows where the compared value is NULL are not returned.
3. `!!` never throws (for `!!` on an association step, see item 1). Kotlin memory rules (object identity, boxing) do not apply. `==` and `===` mean **value equality**.
4. **A lambda that cannot be analyzed is an error.** If lambdaQL cannot read a lambda as a query, it throws an exception with a code `[LQL-xxxx]` and the source line number.

<a id="102-sargable-rule-index-friendly-sql"></a>
## 10.2 SARGable rule (index friendly SQL)

"SARGable" means that the database can use an index for the condition.
This is a basic rule of lambdaQL (design section 3.9).

| # | Rule |
|---|---|
| 1 | **No function, operator or `CASE` on the column side.** If a value must change, the application changes the parameter before binding (for example, lower case with `Locale.ROOT`) |
| 2 | If two forms have the same meaning, pick the one that uses only simple comparisons and `AND` / `OR` / `NOT`. Correct three-valued logic comes first, and within it the SARGable form is chosen |
| 3 | Each rendering rule is checked with `EXPLAIN` on H2, MariaDB and Oracle |

<a id="examples"></a>
### Examples

**Equality with plain `=` and `<>`**

The same example is in [9.4](09-kotlin-notes.md#plain--and-).

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

lambdaQL does not write `is [not] distinct from`. On Oracle this form becomes `decode(...)`, which cannot use an index.
Entity values follow the SQL meaning, so a plain `=` or `<>` is always correct. A captured `null` is bound as NULL and matches no row (`LQL-4045` at `WARN` level).

**Elvis**

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

`?:` in a string condition becomes `coalesce(e0.description, '') = 'x'`. A function wraps the column, so the normal index on `description` is not used.
If you need the index, compare directly, as in `it.description == "x"`. Rows equal to `"x"` are not NULL, so the result is the same.

**NOT IN with a subquery** is written as `NOT IN` (SQL meaning). If the subquery has a NULL, no rows are returned. To skip NULL, add a null check to the subquery (see [10.3](#103-null-rules)).

<a id="what-the-measurements-showed"></a>
### What the measurements showed

MariaDB 10.11 and Oracle 23ai, 20,000 to 50,000 rows:

| Form | MariaDB | Oracle | OK? |
|---|---|---|---|
| `name = 'abc'` on a `_ci` column | Index lookup | – | Yes |
| `LOWER(name) = LOWER('abc')` | Full index scan (19,791 rows) | Full table scan (no function index) | **No** |
| `lower(name) = :p_lowered` + function index | – | Index range scan (also `LIKE 'ab%'`) | Yes |
| `name COLLATE BINARY_CI = :p` + linguistic index | – | Index range scan | Yes |
| Boolean `==` written with `AND`/`OR` | Range (350 rows) | 2 index range scans + bitmap OR | Yes |
| Boolean `==` written with `CASE` | Full index scan (49,648 rows) | Full table scan | **No** |

<a id="db-functions-on-a-column"></a>
### DB functions on a column

A DB function on the column side (`OrderFns.discount(it.price)!! > 10`) cannot use a normal index.
lambdaQL logs a **WARN** (`LQL-5001`). Exception: on Oracle, it is not logged if there is a function-based index with the same expression.
On MariaDB, a `NOT DETERMINISTIC` (default) function becomes a full scan even when its arguments are only parameters (`it.price!! > OrderFns.discount(rate)!!`), so it also warns in this case (`LQL-5007`).
With `SargableMode.STRICT`, the analysis fails instead of warning (`LQL-3066`).

**SARGable mode**. `SargableMode` has three values.

- `OFF`: no check and no metadata query.
- `WARN` (default): logs warnings.
- `STRICT`: in the cases where `WARN` would log a warning (`LQL-5001`, `LQL-5007`, `LQL-5012`), the analysis fails with `org.lambdaql.NonSargableException` (`LQL-3066`).

Set it per persistence unit with `SargablePolicy.register(emf, SargableMode.STRICT)`, and remove it with `SargablePolicy.unregister(emf)`. In Spring, use `lambdaql.sargable.mode=STRICT` (see [12](12-spring-data-jpa.md)).
The check runs once per lambda, when the analysis result is put into the shared cache. So register the mode before the first query.

**Arithmetic on a column**.

- `+ - * /` with one column and values only (`it.cnt * 2 > n`) can be moved to the value side. lambdaQL logs `LQL-5012` at `WARN` level, and it is an error with `STRICT`.
- Arithmetic between columns (`it.price!! * it.cnt > n`) and `%` cannot be moved, so `LQL-5013` is logged only at `DEBUG` level. It is allowed even with `STRICT`.
- The moved form must have the same meaning. Divide by a decimal, as in `it.cnt > n / 2.0`. The integer division `n / 2` gives different rows for `>=`, `=`, `<` and a negative `n`. If you multiplied by a negative number, turn the comparison around. The `LQL-5012` message explains this.
- Reducing warnings by checking whether an index exists was not chosen. Because of permissions and synonyms, "no index" and "unknown" get mixed, and column arithmetic is a rare form.
See [8. Procedures and DB functions](08-procedures-functions.md).

Example code: [`ManualKotlinNotesTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinNotesTest.kt) (`equality`, `elvis-value`).

<a id="103-null-rules"></a>
## 10.3 NULL rules

| Lambda | SQL | NULL behavior |
|---|---|---|
| `it.cnt == 1` (`Integer` and a constant) | `e0.cnt = 1` | NULL: the row is not returned. The null check that the compiler added is not read |
| `it.cnt != 1` | `e0.cnt <> 1` | NULL: the row is not returned (SQL meaning). If needed, write `it.cnt == null \|\|` yourself |
| `it.price!! > p` | `e0.price > :p1` | NULL: the row is not returned. `!!` is ignored |
| `it.status == q` (both nullable: `q: Int?`, `String?`, enum) | `e0.status = :p1` | Column is NULL: not returned. Captured `null`: no row matches (`= NULL`, `LQL-4045` at `WARN` level) |
| `it.status != q` (both nullable) | `e0.status <> :p1` | Column is NULL: **not** returned (SQL meaning. Kotlin would return it). Captured `null`: no row matches |
| `it.description == it.custName` (two entity values) | `e0.description = e0.custName` | Either side is NULL: not returned. Also NULL and NULL (SQL meaning) |
| `it.description.equals(text)` (`String?`) | Same as `==` | Same as `==` |
| `x == null`, `x != null` | `is null`, `is not null` | Two-valued |
| `it.cnt >= 2.5` (integer column, decimal value) | The value is compared as written: a constant is a literal (`e0.cnt >= 2.5`), and captured values and IN lists are also decimal literals (`e0.cnt >= 2.5BD`, `in (2.5BD, 3.0BD)`) | The index range scan is kept (measured on MariaDB). The value is not cut as before, so no wrong rows are returned |
| A whole value object (`@Embedded`): `it.address == addr`, `it.address != addr` | Expanded per column: `e0.address.city = :p1 and e0.address.zip = :p2`, and the negation is `<> ... or ...`. `== null` is `is null` on all columns (and), `!= null` is `is not null` (or) | SQL meaning per column: a NULL column matches neither `=` nor `<>`. If the captured object is `null`, no row matches (`LQL-4045` at `WARN` level) |
| `it.id == id` (`it.id` is `Long?`, `id` is a captured `Long`), `it.id == null`: a column that can never be NULL (id, primitive, `nullable = false`, no association step in the path) | `e0.id = :p1` instead of `not (e0.id is null or e0.id <> :p1)`. `it.id == null` is `1=0` | The column has no NULL. A path through an association (`it.customer?.id`) keeps the check |
| `!`, `&&`, `\|\|` | `NOT`, `AND`, `OR` | SQL three-valued logic |
| `it.vip ?: false` (condition) | `e0.vip is not null and e0.vip = true` | NULL is treated as false |
| `it.customer!!.name` in `where` (or a path with no mark) | `j1.name`. `it.customer!!.name == "Kim"` and `it.customer!!.name == null` are `join e0.customer j1` | No customer: the comparison is not true. `it.status == 1 \|\| it.customer!!.name == "Kim"` is a `left join`, and orders with status 1 are returned |
| `it.customer?.name` in `where` | `j1.name`. `it.customer?.name == "Kim"` is `join`, `it.customer?.name == null` is `left join e0.customer j1` | `it.customer?.name == "Kim"`: not returned (`j1.name` is NULL). `it.status == 1 \|\| it.customer?.name == "Kim"`, `it.customer?.name == null`: returned |
| `it.customer == null \|\| it.customer?.name == p` | `e0.customer is null or j1.name = :p1` + `left join e0.customer j1` | No customer: returned. The same with `!!` |
| Mixed `?.` and `!!`: `it.customer?.name == null \|\| it.customer!!.grade == null` | `j1.name is null or (e0.customer is not null and j1.grade is null)` + `left join e0.customer j1` | No customer: returned by the `?.` comparison. No warning |
| The same path used only in `orderBy` or `select` | `j1.name` + `left join e0.customer j1`. A required association (`optional = false`) is `join` | No customer: the column is NULL |
| `it.customer == null`, `it.customer != null` | `e0.customer is null`, `e0.customer is not null` (foreign key column, no join) | Orders without a customer / orders with a customer |
| `it.customer?.id` | `e0.customer.id` (foreign key column, no join) | No customer: NULL |
| `a ?: b` | `coalesce(a, b)` in select values, conditions and `set` values. `?: false` in a Boolean condition is read as a null check | See [9.5](09-kotlin-notes.md#95-null-operators---and-) |
| `it.id in ids` | `e0.id in (:p1)` | NULL id: not returned |
| `it.id !in ids` | `e0.id not in (:p1)`. The same for a column that can be NULL | NULL value: **not** returned (SQL meaning. `LQL-5009` at `DEBUG` level. Kotlin would return it) |
| `it.cnt in 1..5`, `it.cnt !in 1..5` | `1 <= e0.cnt and e0.cnt < 6`, `1 > e0.cnt or e0.cnt >= 6` | NULL subject: not returned. The same with `!in` (no guard) |
| A subquery of the form `x !in sub...` | `x not in (select ...)` | If the subquery has a NULL, no row is returned (SQL meaning) |
| `Agg.sum`, `avg`, `min`, `max` | `sum(...)` ... | NULL when there are no rows or all values are NULL |
| `Agg.count()` | `count(*)` | 0 when there are no rows |

<a id="captured-null-values"></a>
### Captured null values

| Where the captured value is | `null` allowed? |
|---|---|
| `==` / `!=` between two nullable values | Yes. But no row matches (`= NULL`). `LQL-4045` is logged at `WARN` level when the query runs |
| Under a null guard on the same value: `p == null \|\| it.status!! > p` | Yes. The guard is decided in the application when the query runs. If `p == null`, no filter. Otherwise `e0.status > :p1`. For a comparison with only `!!` and unboxing to its own primitive type, the position of the guard does not matter |
| Used with `!!` or in an ordering comparison (`<` `<=` `>` `>=`): `it.price!! > p!!` | Yes. But no row matches (`> NULL`). `!!` and unboxing to its own primitive type are not run. `LQL-4045` is logged at `DEBUG` level when the query runs |
| A method call on a captured value (`p!!.trim()`) | **No.** An error when the value is computed (`LQL-3027`). See [9.8](09-kotlin-notes.md#98-captured-values) |
| A `null` element in a collection used with `in` | **No.** An error when it is bound (`LQL-4008`) |

<a id="104-operator-rules-r1-to-r24"></a>
## 10.4 Operator rules (R1 to R24)

This is a short version, in Kotlin forms, of the rule table in the design (section 3.6 and KOTLIN.md section 5).

| # | Lambda form | Meaning |
|---|---|---|
| R1 | A comparison of non-null primitives, or a comparison after `!!` (`it.qty > 5`, `it.price!! > p`) | `=`, `<>`, `<`, ... SQL NULL rules |
| R2 | `==` / `!=` compiled to a reference comparison (enum `==`; for `===`, see R17) | Value equality, not object identity. `=` / `<>` with the SQL meaning |
| R3 | `x == null`, `x != null` | `IS NULL`, `IS NOT NULL` |
| R6 | `!`, `&&`, `\|\|` | `NOT`, `AND`, `OR`. SQL three-valued logic |
| R7 | String value comparison | Follows the **DB collation** (case, trailing spaces). Oracle treats `''` as NULL |
| R8 | entity `==` entity | Not supported (KC13, `LQL-3011`) |
| R9 | Captured value | A bind parameter with the value at run time |
| R10 | Property access (`it.price`) | A getter call. Mapped to a JPA attribute |
| R11 | Kotlin `==`, `!=` compiled to `Intrinsics.areEqual` (references, or both sides nullable) | Value equality: `=` / `<>` with the SQL meaning. A captured `null` matches no row |
| R12 | A nullable primitive and a primitive (`it.cnt == 1`, `cnt: Integer`) | The null check that the compiler added is not read: `e0.cnt = 1`, `e0.cnt <> 1` |
| R13 | `!!` | Ignored |
| R14 | `?.` | A path or value where NULL propagates. The same column as without `?.` |
| R15 | `?:` | `coalesce(a, b)` for values. Boolean elvis in a condition becomes `(x is not null and x = true)`. Boolean elvis as a value is rejected (KC21) |
| R16 | Association join | Replaced by R24 |
| R17 | `===`, `!==` | Value equality, like `==` |
| R18 | Ranges `in a..b`, `until`, `..<`, `!in` | `a <= x and x < b + 1` (constants), `:p1 <= x and x <= :p2` (captured values), `a > x or x >= b + 1` (`!in`). No automatic null guard |
| R19 | Collections `x in c`, `x !in c` (captured `c`) | `x in (:p)`, `x not in (:p)` (SQL meaning). Null element: error. Empty collection: FALSE / TRUE |
| R20 | `when { }` without a subject, with branches that end with `true`/`false` | `OR` of the conditions. `when (subject)` with a subject is rejected (`LQL-2010`) |
| R21 | Kotlin string functions | `startsWith`, `endsWith` and `contains` (also with `ignoreCase`) are `like`. `equals(ignoreCase)`, `lowercase`, `length` and `isNullOrEmpty` are rejected (`LQL-3008`). `equals(text)` is the same as `==` |
| R22 | Kotlin host values (captured `var`, private properties, `companion` values) | Read on each run. Writing is rejected |
| R23 | Property reference `Order::price` | Rejected in conditions (`LQL-1009`). Use the lambda `{ it.price }` |
| R24 | Association paths (`it.customer?.name`, `it.customer!!.name`, `it.customer.name` of a Java entity) | A column. To-one association: an alias shared by where, order by and select. In `where`, a `left join` if a row without the association can match the condition, otherwise an inner join (a comparison passed with `!!` or no mark is true only for rows that have the association, and `?.` and `!!` can be mixed). If used only in order by or select, a `left join` (a required association `optional = false` is an inner join, A4). Embedded: the same table. Ending with the target's `@Id`, or comparing the association with `null`: foreign key column, no join. Collection: error |

The Java-only rules R4 (`equals` on entity values) and R5 (Java null-safe helpers) are in the [Java track](../java/10-rules-and-limits.md). In Kotlin, `==` has the same meaning as R5.

Other rules:

- `x.compareTo(y) > 0` becomes `x > y`. `isBefore` and `isAfter` are rejected (`LQL-3008`).
- Constants written directly in the lambda (including `const val`) are literals: `it.price > 100.0` is `e0.price > 100.0`, and `"O'Brien"` is `'O''Brien'`. Captured values and computed values (`BigDecimal("1.5")`) are bind parameters. Exception: a captured decimal value compared with an integer column is a decimal literal (`2.5BD`).
- The compiler sometimes moves `!` (De Morgan, double negation). The result still does not change.
  lambdaQL keeps each condition exactly once and does not "simplify" the logic you wrote.
- If you write a condition that is always true in Kotlin (`x > 5 || !(x > 5)`), it is not simplified.
  In SQL, rows where `x` is NULL are dropped. This is the SQL rule.

<a id="105-kotlin-and-sql-differences"></a>
## 10.5 Kotlin and SQL differences

These differences come from SQL itself. They are not compiler problems.
The "G" numbers are IDs from the design (section 2.7).

| # | Difference | How lambdaQL handles it |
|---|---|---|
| G1 | In Kotlin, `null != "x"` is true, but SQL `description <> 'x'` does not return rows where description is NULL | `!=` compiled to `Intrinsics.areEqual` is rendered as `<>`: SQL meaning. NULL rows are left out, and `LQL-5009` is logged at `DEBUG` level. To keep such rows, write `it.description == null \|\| ...`. `!=` between a nullable number and a constant (`it.cnt != 1`) also becomes `e0.cnt <> 1`, so NULL rows are left out |
| G3 | `name = ?` with a NULL parameter returns 0 rows | Kept as it is (SQL meaning). `LQL-4045` is logged at `WARN` level at run time. To find NULL, write `x == null` |
| G4 | `status NOT IN (1, NULL)` always returns 0 rows | A `null` in a captured collection is an error. A subquery `!in` keeps the SQL meaning: add a null check to the subquery |
| G5 | A column value of an entity is NULL (including `!!` and unboxing) | Documented: the row is not returned, by the SQL rules |
| G6 | `NaN`: H2 sorts NaN as the largest value and `NaN = NaN` is true, MariaDB cannot store it, Oracle can store it only in `BINARY_DOUBLE` | Documented: a comparison with NaN follows the DB |
| G7 | Comparing a `Float` column with a `Double` value: the result differs by DB | For `Float`/`Double`, **results that are the same as Kotlin down to the bit are not promised**. lambdaQL does not add `CAST`. Choose the column mapping yourself (for example, Oracle `binary_float` / `binary_double`) |
| G9 | `x > 5 \|\| !(x > 5)` drops NULL rows | Rendered as written, without simplification |
| G10 | SQL does not promise an order of evaluation: `qty != 0 && total / qty > 2` can also divide by zero | Documented: a division under a guard is also written as it is. Compare without division (`it.qty > 0 && it.price > 2.0 * it.qty`) |
| G11 | Strings: MariaDB `_ci` ignores case and trailing spaces, Oracle stores `''` as NULL, Oracle `NLS_SORT` follows the JVM locale | Documented: string comparison and sorting follow the DB. On Oracle, set `NLS_SORT=BINARY` (session init or `-Duser.language=en`). On Oracle, `it.description == ""` and `isEmpty()` work differently |
| G12 | Kotlin `===` is identity (two `Long` boxes with the value 1000 are false) | lambdaQL defines `===` and `==` as value equality |
| G13 | Legacy boolean columns with values other than 0/1 (MariaDB `TINYINT(1)`, Oracle `NUMBER(1)`) are read differently | Hibernate's own DDL (`bit(1)`, Oracle 23 `boolean`) has no problem. Be careful with legacy schemas |
| G14 | Oracle 23 bug: `WHERE (a AND b) = false` / `IS FALSE` / `IS NULL` on a compound condition loses rows | lambdaQL does not wrap compound conditions in boolean tests. A negation is always `NOT(...)` |
| G15 | An Oracle `CHAR` column does not match a `String` bind | Documented: be careful with the entity mapping |

Other differences:

| Difference | Kotlin | SQL | lambdaQL |
|---|---|---|---|
| `BigDecimal` equality | `==` also compares the scale (`1.0` and `1.00` are false) | `=` compares the value | Value equality |
| Integer division | `5 / 2` is `2` | MariaDB gives `2.5` | Depends on the DB |
| `BigDecimal` division and function results | Kotlin scale rules (`HALF_EVEN`) | The DB decides the scale (for example, for function results H2 gives 1, MariaDB and Oracle give 0) | Documented |
| Unicode case-insensitive matching (`ſ`, `İ`) | Kotlin rules | DB collation | Follows the DB |
| String length | UTF-16 units | H2 uses UTF-16 units, MariaDB uses code points | Documented |
| String template with NULL | The text `"null"` | NULL (Oracle: `NULL \|\| 'x'` is `'x'`) | String concatenation is rejected (`LQL-2007`) |
| Nullable range subject | false. `!in` is true | Not returned. The same with `!in` | SQL meaning |
| A non-null `String` type | Never null | The column can be NULL | lambdaQL uses the JPA metadata |
| `LocalDateTime.now()` in a lambda | Application time | – | Bound as **application time**. DB time (`current_timestamp`) is not supported |
| Equal elements of a `Set` | Decided by `equals` / `hashCode` (for a `data class`, the properties of the primary constructor) | Rows are told apart by the primary key | If the Set is smaller than the number of rows, `toSet()` and `findSet()` throw `IllegalStateException` (when the entity's `equals` / `hashCode` treats different rows as equal, or the `@Id` is not unique, for example a view). Use `toList()` |

<a id="106-database-differences"></a>
## 10.6 Database differences

lambdaQL hides many differences. You should know some of them.

<a id="sorting-and-paging"></a>
### Sorting and paging

| Item | H2 | MariaDB | Oracle | lambdaQL |
|---|---|---|---|---|
| NULL position, `ASC` | First | First | **Last** | `ASC` is NULLS FIRST on every DB |
| NULL position, `DESC` | Last | Last | **First** | `DESC` is NULLS LAST on every DB |
| `NULLS FIRST/LAST` syntax | Yes | **No** (syntax error) | Yes | On MariaDB, Hibernate emulates it with `CASE`: the sort **cannot use an index** |
| Paging SQL | `offset ? rows fetch first ? rows only` | `limit ?,?` | `offset ? rows fetch first ? rows only` | Passed to Hibernate (`setFirstResult` / `setMaxResults`) |
| Order of rows with the same value | – | – | Random | With paging, lambdaQL adds the root `@Id asc` if the keys have no id. Not added if there is a unique key that can never be NULL (`unique = true` and `nullable = false`, `@NaturalId`) |
| String order | Case-sensitive | `_ci` | `NLS_SORT` (JVM locale) | Follows the DB |
| Implicit path in `order by` | Inner join (rows lost) | Same | Same | lambdaQL uses a `left join` (an inner join for a required association, or the `where` join of the same path) |
| `@Lob` columns in `order by`, `distinct`, `group by` | Works | Works | **ORA-22848 / ORA-22849** | The query is not changed, and `LQL-5010` is logged at `DEBUG` level |
| Sorting an `@Enumerated(EnumType.STRING)` enum column | By name | By name | By name | The query is not changed, and `LQL-5011` is logged at `DEBUG` level. Declaration order only with an `ORDINAL` mapping |

lambdaQL writes `nulls first` / `nulls last` in the HQL for every key that can be NULL. It does not write it for a path that passes only required associations and cannot be NULL. If it is the same as the database default, Hibernate leaves it out.
The defaults (`ASC` = NULLS FIRST, `DESC` = NULLS LAST) are the same as H2 and MariaDB, so these DBs need no extra
SQL. Only Oracle gets a `NULLS` clause. To change the NULL position, use
`asc(key, Nulls.LAST)` (NULL last) or `desc(key, Nulls.FIRST)` (NULL first). On MariaDB these use the `CASE` emulation.

<a id="conditions-and-values"></a>
### Conditions and values

| Item | H2 | MariaDB | Oracle |
|---|---|---|---|
| `is not distinct from` | Native | `<=>` (uses an index) | `decode(...)` (cannot use an index). lambdaQL does not use this form |
| `is [not] true`, `coalesce(boolean, false)` | Works | Works | **ORA-00932**. lambdaQL does not use them |
| Empty string `''` | Value | Value | **NULL** |
| Case and trailing spaces | Compared exactly | `_ci`: ignored | Compared exactly (`NLS_SORT` for sorting) |
| `NaN` | Stored, largest value | Cannot be stored | Only in `BINARY_DOUBLE` |
| `Float` column | REAL literal rules | Widened to double | Hibernate maps `Float` to `float(24)` (decimal) |
| Integer `/` | Integer | **Decimal** (`5/2 = 2.5`) | – |
| Large `in` list (1,500 values) | Works | Works | Works on 23ai (the 1,000 limit of older Oracle versions was not measured) |

<a id="subqueries"></a>
### Subqueries

| Item | What happens | lambdaQL |
|---|---|---|
| Subquery `order by` without `limit` | Oracle: syntax error (ORA-00907). MariaDB: ignored | Removed with a WARN |
| MariaDB `IN (... limit n)` | Not supported (error 1235) | Wrapped automatically: `in (select x.id from (... limit n) x)` |

<a id="update-insert-delete"></a>
### Update, insert, delete

| Item | What happens | lambdaQL |
|---|---|---|
| `SET a = ..., b = a + 1` (reads a column after setting it) | MariaDB reads the **new** value, H2 and Oracle read the old value | Error at analysis time |
| Association paths in DML (`it.customer?.name`) | Hibernate makes a different join for each DB and **misses rows** without a customer | lambdaQL writes a correlated scalar subquery (`where` and SET). For a path that a select would inner join in `where`, it also adds `customer.id is not null`. So the DML applies to the same rows as the query |
| `DELETE` with a subquery on the same table | MariaDB error 1093 | Reads the target ids through a derived table first: `d0.id in (select t.id from (select e0.id as id … where <condition>) t)`. It also works for correlated subqueries and deletes the same rows on three DBs (measured). `update` is not wrapped (it works on MariaDB too) |
| Binding the same enum value only once to a STRING column and an ORDINAL column | H2 and MariaDB store a **wrong value with no error** | One parameter per position. Not shared |
| Many rows in `values(list) { }` | Oracle: a limit of 65,535 binds (ORA-01745), and very slow long before that | Split into statements of at most 500 values, all run in one transaction |
| `update` / `delete` with `order by ... limit` | MariaDB rejects `limit` in an `in` subquery (error 1235) and a subquery that reads the target table (error 1093) | `e0.id in (select t.id from (select x0.id as id ... order by ... limit n) t)`: works on all three databases with the derived table `t`. The inner select uses the joins of the select, so it targets the same rows as the select |
| `on conflict` / upsert | H2 and Oracle use `merge`. MariaDB uses `insert ... on duplicate key update` and counts an updated row as 2. Hibernate's `do nothing` fails on H2 and Oracle | `upsert` and `onConflict(...).update(...)` use the Hibernate form. `ignore()` is written as `insert ... select ... where not exists`. The row count is returned as it is |

<a id="procedures-and-functions"></a>
### Procedures and functions

| Item | H2 | MariaDB | Oracle |
|---|---|---|---|
| OUT / INOUT parameters | **No** | Yes | Yes |
| Many result sets | No | Yes | Yes (REF CURSOR) |
| Rows affected by a procedure | – | – | Always -1, so lambdaQL does not provide it. Use an OUT parameter |
| Boolean parameters and results | – | Yes | 23ai or later |
| Stored functions in virtual columns / function indexes | – | Not possible (error 1901) | Possible (function-based index) |

H2 has no OUT parameters, so the procedure features are checked on MariaDB and Oracle.

<a id="107-not-supported-and-what-to-write-instead"></a>
## 10.7 Not supported, and what to write instead

lambdaQL throws a clear error for every form in this list.

<a id="lambda-form-and-loading"></a>
### Lambda form and loading

| Not supported | Write instead |
|---|---|
| Object expressions (`object : ...`), functional interfaces that are not `Serializable` | A lambda literal |
| A lambda stored as a Kotlin function type (`val f: (Order) -> Boolean`) | Pass a lambda literal |
| `-Xsam-conversions=class`, a lambda made inside the body of an `inline fun` | See [9.14](09-kotlin-notes.md#914-kotlin-limits-kc1-to-kc21) |
| A callable reference to your own Kotlin `fun interface` | A lambda literal |
| Local `val`/`var`, loops, several statements in a condition, sort or select lambda | One expression. Compute values outside the lambda |
| Writing to a captured variable (`n++`) | Compute it outside the lambda |
| `@JvmField`, `internal`, `value class`, `@get:JvmName` properties | A normal public property |
| On Java 21 to 23, a lambda compiled for a newer JDK that the bundled ASM does not know | Update ASM (lambdaQL does not shade ASM). Not a problem on Java 24 or later (uses `java.lang.classfile`) |

<a id="conditions"></a>
### Conditions

| Not supported | Write instead |
|---|---|
| Boolean `if (c) x else y`, `takeIf` (`LQL-2021`), `when (subject)` with a subject (`LQL-2010`) | `&&` / `\|\|`. An `if`/`when` that picks a value is supported: a bind parameter if it uses only captured values and constants, `case when` if it uses entity values ([3.5.1](03-where.md#351-values-chosen-by-a-condition)) |
| (Supported: `if (c) x else false`, `if (c) true else x`, `when { a -> true; ...; else -> false }`) | They mean `AND` and `OR` |
| Boolean infix functions `and`, `or`, `xor` | `&&`, `\|\|` |
| entity `==` entity (`it.customer == c`) (`LQL-3011`) | `it.customer?.id == c.id` |
| Ordering comparison of a value object (`@Embedded`), comparison with another type (`LQL-3012`) | Compare the fields. `==`, `!=` with the same type and comparison with `null` are supported |
| Collection paths (`it.items...`, `@OneToMany`, `@ManyToMany`) | An `exists()` subquery or a join |
| `item in it.items` | An `exists()` subquery |
| A collection built inside the lambda (`listOf(1, 2)`, `setOf(...)`) | Build it outside and capture it. (`groupBy` keys are the exception) |
| `step` in a range | Two comparisons or a captured collection |
| A `null` element in a captured collection | Remove the null. If you need NULL rows, add `\|\| it.x == null` |
| A captured `NaN` / `Infinity` | Check the value before the query |
| `isNaN()` | – |
| Other function calls on entity values (your own non-inline functions, `operator` functions) | Write the condition with properties, or make the function `inline` |
| String templates or `+` (`LQL-2007`) | Build the string outside the lambda and capture it |
| `it.tags["k"]`, `it.lines[0]` on entity paths | – |

<a id="functions-and-strings"></a>
### Functions and strings

| Not supported | Write instead |
|---|---|
| String functions on entity values: `lowercase`, `length`, `isNullOrEmpty`, `isBlank`, `equals(s, ignoreCase = true)` (`LQL-3008`) | Compare directly. `startsWith`, `endsWith` and `contains` are supported |
| `trim`, `coerceAtLeast`, `toLocalDate` and other stdlib functions on columns | Compare directly, or use a DB function (`@SqlFunction`) |
| Date functions (`YEAR(col) == 2024`) | A range: `it.updateAt!! >= from && it.updateAt!! < to` |
| A `@SqlFunction` that returns a generic type (`fun <X> nvl(a: X, b: X): X`), an entity, a collection or `Unit` | One of: `Double`, `Integer` (`Int`), `Long`, `BigDecimal`, `String`, `LocalDate`, `LocalDateTime`, `Boolean` |
| A `@SqlFunction` with the same name as a Hibernate built-in function (`COALESCE`, `ROUND`, `SYSDATE`, ...) | Use a prefix or a schema name (`PUBLIC.SQ_FN`) |
| A `@SqlFunction` with default arguments, `inline` or `vararg` | A normal function with fixed parameters |
| Extension functions, functional interface constants, function type properties as DB functions | Interface `companion object` + `@JvmStatic` |
| User-defined aggregate functions or window functions | – |
| Calling `Agg.*` outside a query lambda or in `where` | Use it in `select` / `having` |

<a id="sort-and-paging"></a>
### Sort and paging

| Not supported | Write instead |
|---|---|
| Conditions or loops in a sort lambda (`if (x) asc(...)`) | Build two queries, or pass a `Direction` as a captured value: `sort(o.price, dir)` |
| Property references in a join sort lambda | A lambda |
| A property reference in `orderBy` (`orderBy(Order::price)`) | A lambda: `orderBy { it.price }` |
| One string with both the sort name and direction (`"price,desc_nulls_last"`) | `Direction.parse(text)` for the direction and `SortSpec.ofPath` for the name. The sort policy checks the name ([4.10](04-order-paging.md#sort-policy)) |
| With `distinct()` on a join, sorting by a column of a joined entity or by an association path (`LQL-3046`) | Sort by a column of the main entity itself, or use an `exists` subquery |
| A collection fetch join with `limit` / `offset` | Page first, then load the collection |
| `ignoreCase()` of Spring `Sort` ([12.5](12-spring-data-jpa.md#125-spring-sort-and-pageable)) | A sort without `ignoreCase()` |
| Function keys (`lower(name)`) | – |
| Sorting by an aggregate alias (`desc(s.total)`) | – |
| Boolean elvis as a sort key | Give a NULL position to the key `o.flag`: `asc(o.flag, Nulls.LAST)` (NULL last) |

<a id="limits-subqueries"></a>
### Subqueries

| Not supported | Write instead |
|---|---|
| `first()` in a scalar comparison that is not one row | Use an aggregate (`max`, `min`, `avg`, `sum`, `count`) or `orderBy { }.limit(1)...first()` |
| `orderBy` in a subquery without `limit` | Removed because it has no effect (WARN) |

<a id="select-and-group-by"></a>
### Select and group by

| Not supported | Write instead |
|---|---|
| A non-aggregate value in `select` / `having` that is not a `groupBy` key | Add it to `groupBy` |
| A normal class as the row type of a derived table or CTE | A Kotlin data class (or a Java record) |
| `arrayOf(...)` / `setOf(...)` as `groupBy` keys | `listOf(...)`, `Pair`, `Triple` |
| Boolean elvis as a select value (`LQL-2022`) | Select `it.vip` and handle NULL in Kotlin |

<a id="limits-update-insert-delete"></a>
### Update, insert, delete

| Not supported | Write instead |
|---|---|
| `update` / `delete` without `where` | Add `.all()` |
| Reading a column after setting it in the same statement | Use the old value on purpose, or run two updates |
| Setting the same column twice | One assignment |
| `if` in a `set` lambda to choose the column to set | Call `set()` more than once |
| `++`, `--`, `apply`, `with`, `also` in `set` | `it.cnt += 1`, direct assignments |
| A local `val` in an assignment lambda | Write the expression in each place |
| Setting a property of an association that is not the `@Id` (`it.customer?.name = x`) | Update `Customer` directly |
| `null` for a non-optional column (`@Id`, `nullable = false`, primitive) | – |
| Writing the `@Version` property directly | lambdaQL uses `update versioned` instead |
| String templates in values (`LQL-2007`) | Build the string outside the lambda and capture it |
| Explicit joins in update or delete | Not added. Use association paths or `sub` |
| `insert ... select` with explicit joins | Use many-to-one paths |
| An atomic `onConflict { ... }.ignore()` for concurrent inserts | Handle the duplicate key error (`ignore()` uses `not exists`. See [7.6](07-6-upsert.md)) |
| Upsert with `insert ... select` | Upsert row by row with `values(list) { ... }` |
| `orderBy { ... }` on an update or delete without `limit(n)` | Add `limit(n)` or remove `orderBy` (otherwise `IllegalStateException`) |
| Checking an update or delete that must change one row with `execute() == 1` and a rollback | `executeOne()`: it first reads at most 2 ids. With 2 ids, it changes nothing and throws `NonUniqueResultException`. With 1 id, it changes that row with `id = :p and (condition)`. It never changes two or more rows |
| Using `executeOne()` together with `orderBy` or `limit` | Use only one of them (otherwise `IllegalStateException`) |
| `offset` in update or delete | Not planned. DML has only `limit(n)`. Choose the target rows with `where { }`, `orderBy { }` and `limit(n)` |
| `limit` or `executeOne()` for entities with a composite key | Not supported yet. Select the ids first, then `delete ... where { it.id in ids }` |
| Running DML without a transaction with the Spring shared `EntityManager` (lambdaQL has no `@Transactional`) | Call it inside `@Transactional` or a `TransactionTemplate` |
| Expecting cascade or `@PreRemove` from the repository method `deleteAll { ... }` (bulk delete) | Use Spring Data's `deleteAll(Iterable)` or `delete(entity)` (see [12.4](12-spring-data-jpa.md#deleteallwhere-is-not-spring-datas-deleteall)) |
| `returning` | Select again after the DML |
| DB time (`current_timestamp`) | Pass the application time (`LocalDateTime.now()` is bound as a value) |
| `insert ... select` with a `TABLE` id generator | Use `values { }` or IDENTITY / SEQUENCE |
| `insert` into a JOINED or TABLE_PER_CLASS subclass | Not supported (Hibernate 6.6.5 fails, 7.4 not measured) |

One more thing to know about DML: bulk DML does not go through the persistence context. Hibernate flushes first, but
managed entities of the same session keep their old values after that. Call `clear()` (or `refresh`).
JPA cascade and `orphanRemoval` do not apply.
DML statements need a transaction. `QueryBuilder(EntityManagerFactory)` opens its own transaction. A builder that uses an `EntityManager` (including Spring's shared `EntityManager`) uses the caller's transaction, and without a transaction it fails before it sends SQL.

<a id="procedures"></a>
### Procedures

| Not supported | Write instead |
|---|---|
| Anything other than one stub function call in the lambda (logging, nested calls, branches) | One call. Compute the arguments first |
| Named arguments with a computation (`calc(rate = r * 2, id = id)`) | Use positional arguments, or compute the value first |
| `exec` for a procedure with OUT parameters | `call` |
| DB default values for arguments | Pass every argument |
| Kotlin default arguments in stub functions | Pass every argument and remove the default values |
| Picking one overload of an Oracle package procedure to call (a name with overloads is detected: `LQL-4044`), dynamic procedure names | – |
| Oracle implicit results (`DBMS_SQL.RETURN_RESULT`), MariaDB packages, paging of procedure results | Not supported |

<a id="108-caching-a-lambda-is-analyzed-once"></a>
## 10.8 Caching: a lambda is analyzed once

Reading bytecode opcodes is the slow part, so lambdaQL does it **only once per lambda**.

| Item | Rule |
|---|---|
| What is cached | The analysis result: the HQL template and the list of parameter sources. Failures are cached too |
| Cache key | The implementation class and method of the lambda (name, signature, kind), the lambda type, the entity class, the **ClassLoader** |
| Size | Limited (LRU). So it does not grow without end and does not leak class loaders |
| What is not cached | The bytecode opcodes themselves (reading them again is cheap, and keeping them is big) |
| Captured values | **Not cached.** Read and bound each time the query runs |
| Calls on captured objects (`filter.minPrice`, `this.minStatus`, `LocalDateTime.of(...)`) | Run in the application **each time** the query runs, then bound as one parameter |
| A captured `var` | Read each time the query runs |
| A null guard that uses only captured values (`lo == null \|\| ...`) | Decided by the value when the query is built. So the HQL depends on the value (see [9.8](09-kotlin-notes.md#98-captured-values)) |
| A `Direction` used as a captured value | A bind value read each time (the structure stays cached) |
| DML | Analyzed once per lambda class. The checks across `set()` calls use a small summary |

Kotlin lambda names (`...$lambda$0`) can change with the compiler version or when you edit the source.
This is not a problem. The cache key also has the class, the signature and the class loader, and lambdaQL does not depend on the name format.

Measured with a prototype (JDK 21): the first analysis of a new lambda takes about **0.2 ms**.
After that, an update with two `set()` calls takes about **0.1 to 0.2 µs** for the cache lookup, value extraction and checks,
and it is the same after 2 million runs. The goal: even if the same lambda is called 1,000 times, the bytecode opcodes are read only once.
Hibernate also caches the query plan with the HQL string as the key.

What this means for you:

<!-- region: ManualKotlinRulesTest.kt#cache-choose -->
```kotlin
// good: one lambda, only the value changes (the lambda is analyzed once)
val byStatus = qb.from(Order::class.java).where { it.status.toInt() == status }
assertEquals("select e0 from Order e0 where e0.status = :p1", byStatus.toHql().hql())

// not possible: the shape of the query changes inside the lambda
val error = assertThrows(LambdaQLException::class.java) {
    qb.from(Order::class.java).where { if (flag) it.status.toInt() == 1 else it.isActive }
}
assertEquals("LQL-2021", error.code())

// instead: choose the lambda outside (two lambdas, each analyzed once)
val list = if (flag) {
    qb.from(Order::class.java).where { it.status.toInt() == 1 }.toList()
} else {
    qb.from(Order::class.java).where { it.isActive }.toList()
}
```

Example code: [`ManualKotlinRulesTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinRulesTest.kt) (`cache-choose`).

<a id="109-tested-environment"></a>
## 10.9 Supported environment

| Item | Version |
|---|---|
| Kotlin | 2.x (the design was checked with kotlinc 1.7.22, 2.1.10, 2.2.21, 2.3.0 and 2.3.21) |
| Java (JVM) | 21 or later |
| Hibernate | 6.6 or 7.4. lambdaQL uses HQL extensions (for example, `function(...)`, `nulls first`), so other JPA providers are not guaranteed |
| Databases | H2 2.3, MariaDB 10.11, MySQL 8.4, PostgreSQL 17, Oracle 23.26 (23ai) |
| Spring | Spring Boot 3.5 (Spring Data JPA 3.5) and 4.1 (Spring Data JPA 4.1) (Spring modules, [12](12-spring-data-jpa.md), [13](../13-spring-boot-versions.md)) |
| Not checked | Kotlin newer than 2.3, Kotlin Multiplatform, SQL Server, Oracle 19c or older (for example, PL/SQL `BOOLEAN` parameters) |

---

[Kotlin track](README.md) | Previous: [9. Kotlin notes](09-kotlin-notes.md) | Next: [11. Errors and languages](../11-errors-and-languages.md) | [Java version of this page](../java/10-rules-and-limits.md)
