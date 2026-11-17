# 10. Rules and limits (Kotlin)

> [!NOTE]
> **Status: partly implemented.**
> This chapter collects the rules of the lambdaQL design for Kotlin: how lambdas become SQL, how NULL works,
> where Kotlin and SQL differ, where databases differ, and what is not supported.
> The rules were checked with prototypes on H2 2.3, MariaDB 10.11 and Oracle 23ai (Hibernate 6.6.5), and the Kotlin bytecode with kotlinc 1.7.22 to 2.3.21.
> Today the analysis core, `where`, the result functions (`toList()`, `toSet()`, `first()`, `one()`, `count()`, `exists()`), the core of sorting and paging, `SortSpec`, update/insert/delete/upsert ([7](07-dml.md)) and the Spring Data JPA module ([12](12-spring-data-jpa.md)) work (tested on H2, MariaDB and Oracle with Java lambdas).
> The rules for subqueries ([5](05-subquery.md)) and `select` with aggregates ([6](06-select-group.md)) are implemented too, also in DML. The rules for explicit joins, derived tables, CTEs ([6](06-select-group.md)), procedures and DB functions ([8](08-procedures-functions.md)) are implemented too. The warnings for DB functions on a column ([10.2](#db-functions-on-a-column)) are **not implemented yet**. Some sorting rules are not implemented yet either (see [4.12](04-order-paging.md#412-rules-summary-s1-to-s14)).
> See the [track README](README.md) for the full status.

[Kotlin track](README.md) | [Manual home](../README.md) | Previous: [9. Kotlin notes](09-kotlin-notes.md) | Next: [11. Errors and languages](../11-errors-and-languages.md) | [Java version of this page](../java/10-rules-and-limits.md)

Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md)

## Contents

- [10.1 The base rule: a lambda is a query](#101-the-base-rule-a-lambda-is-a-query)
- [10.2 SARGable rule (index friendly SQL)](#102-sargable-rule-index-friendly-sql)
- [10.3 NULL rules](#103-null-rules)
- [10.4 Operator rules (R1 to R24)](#104-operator-rules-r1-to-r24)
- [10.5 Kotlin and SQL differences](#105-kotlin-and-sql-differences)
- [10.6 Database differences](#106-database-differences)
- [10.7 Not supported, and what to write instead](#107-not-supported-and-what-to-write-instead)
- [10.8 Caching: a lambda is analyzed once](#108-caching-a-lambda-is-analyzed-once)
- [10.9 Tested environment](#109-tested-environment)

How to read the examples:

- The comment under an example shows the HQL that lambdaQL makes, when the design states it.
- Constants written in the lambda (numbers, strings, booleans, `null`, enum constants) are written into the HQL as literals.
  Captured and computed values are always bind parameters (`:p1`, `:p2`, ...) (D37).
- Entities: `Order` (`id`, `price`, `status`, `active`, `description`, `customer`, `updateAt`, `items`),
  `Customer` (`id`, `name`, `grade`), `Item` (`order`, `price`, `qty`). See [2. Getting started](02-getting-started.md#example-entities).
  Some rows use an extra nullable property `flag: Boolean?`.

---

## 10.1 The base rule: a lambda is a query

lambdaQL **never runs your lambda**. It reads the bytecode and writes HQL.
A lambda is a query written with Kotlin syntax. So:

1. A path like `it.customer?.name` **names a column**. It is not a chain of calls.
   lambdaQL does not check if the customer is null (D20). In `where`, the path is an inner join by default, so an order without a customer is not returned (D36).
   If the condition can be true without a customer (a null guard), the path is a `left join`, so the result is the same as in Kotlin.
   To find those orders, compare the association itself: `it.customer == null`. `it.customer?.name == null` does not return them.
2. NULL is handled **only where you wrote it** (`== null`, `!= null`, `?.`, `?:`, `==` between two nullable values) (D22).
   The null checks that the Kotlin compiler adds are ignored. Other NULLs follow SQL rules: the condition is UNKNOWN and the row is not returned.
3. `!!` is ignored (D23). Kotlin memory rules (object identity, boxing) do not apply. `==` and `===` mean **value equality** (D12, R17).
4. **When there is no rule for a form, lambdaQL does not guess.** It throws a clear error
   with a code `[LQL-xxxx]` and the source line. It never makes SQL that is "probably right".

## 10.2 SARGable rule (index friendly SQL)

"SARGable" means: the database can use an index for the condition.
This is a base rule of lambdaQL (design section 3.9).

| # | Rule |
|---|---|
| 1 | **Do not put a function, an operator or a `CASE` on the column side.** If a value must change, change the parameter in the application before binding (for example lowercase with `Locale.ROOT`) |
| 2 | If two forms mean the same, choose the one with only simple comparisons and `AND` / `OR` / `NOT`. Correct 3-valued logic comes first; inside that, choose the SARGable form |
| 3 | If the meaning really needs a function on the column (for example ignore case), lambdaQL reads DB metadata at start and uses a path the DB already has: column collation, function index, linguistic index. If there is no path, it logs a WARN per column at start (strict mode: error) |
| 4 | Each rendering rule is checked with `EXPLAIN` on H2, MariaDB and Oracle |

### Examples

**Ignore case** (`equals(s, ignoreCase = true)`, `startsWith(s, ignoreCase = true)`; D24)

| What the DB has | lambdaQL writes | Not this |
|---|---|---|
| Case-insensitive collation (MariaDB `_ci`) | `e0.description = :p1` | `lower(e0.description) = lower(:p1)` |
| Oracle function index `LOWER(description)` | `lower(e0.description) = :p1` (`:p1` lowercased in the application) | `lower(e0.description) = lower(:p1)` |
| Oracle linguistic index `NLSSORT(... BINARY_CI)` | `e0.description collate binary_ci = :p1` | |
| No path | WARN at start (strict mode: error) | |

**Null-safe equality when one side is NOT NULL**

```kotlin
.where { it.description == text }   // text is not null when the query runs
// e0.description = :p1                (not "is not distinct from")
.where { it.description != text }
// (e0.description <> :p1 or e0.description is null)
```

On Oracle, `is not distinct from` becomes `decode(...)`, which cannot use an index.
So when one side is known to be NOT NULL (`@Id`, `nullable = false`, a primitive property,
or a bound value that is not null), lambdaQL writes a plain `=`.

**Comparing two boolean expressions** (D25)

```kotlin
.where { it.active == (it.status!! > c) }
// (e0.active = true and e0.status > :p1) or (e0.active = false and e0.status <= :p1)
```

The `CASE` form (`e0.active = case when ... end`) gives the right rows but needs a full scan,
so lambdaQL does not use it.

**Elvis and empty strings**

```kotlin
.where { (it.description ?: "") == "x" }     // e0.description = :p1
.where { (it.description ?: "") == "" }      // (e0.description = :p1 or o.description is null)
.where { it.description.isNullOrEmpty() }    // (o.description is null or e0.description = :p1)
```

**NOT IN with a subquery** is written as `NOT EXISTS` (an anti-join, and NULL-safe; see [10.3](#103-null-rules)).

### What the measurements showed

MariaDB 10.11 and Oracle 23ai, 20,000 to 50,000 rows:

| Form | MariaDB | Oracle | OK? |
|---|---|---|---|
| `name = 'abc'` on a `_ci` column | index lookup | – | yes |
| `LOWER(name) = LOWER('abc')` | full index scan (19,791 rows) | full table scan (no function index) | **no** |
| `lower(name) = :p_lowered` + function index | – | index range scan (also `LIKE 'ab%'`) | yes |
| `name COLLATE BINARY_CI = :p` + linguistic index | – | index range scan | yes |
| Boolean `==` as `AND`/`OR` | range (350 rows) | 2 index range scans + bitmap OR | yes |
| Boolean `==` as `CASE` | full index scan (49,648 rows) | full table scan | **no** |

### Planned items under the same rule

| Form | Planned SQL |
|---|---|
| Date functions like "year of `updateAt` is 2024" | `e0.updateAt >= :from and e0.updateAt < :to` |
| Sort by a function key (`lower(name)`) | Check the access path first, WARN if none |

### DB functions on a column

A DB function on the column side (`OrderFns.discount(it.price)!! > 10`) cannot use a normal index.
lambdaQL will log a **WARN** (strict mode: error). Exception: on Oracle, no WARN when the function is
`DETERMINISTIC` and a function-based index with the same expression exists. On MariaDB, a function
that is `NOT DETERMINISTIC` (the default) gives a full scan even when its arguments are only
parameters (`it.price!! > OrderFns.discount(rate)!!`), so lambdaQL also warns there.
See [8. Procedures and DB functions](08-procedures-functions.md).
This warning is **not implemented yet**.

## 10.3 NULL rules

| Lambda | SQL | NULL behavior |
|---|---|---|
| `it.status == 1` (`Int?` and a constant) | `e0.status = 1` | NULL: UNKNOWN, row not returned. The hidden compiler null check is ignored (D22) |
| `it.status != 1` | `e0.status <> 1` | NULL: row **not** returned |
| `it.price!! > p` | `e0.price > :p1` | NULL: row not returned. `!!` is ignored (D23) |
| `it.status == q` (both nullable: `q: Int?`, `String?`, enum) | null-safe `is not distinct from`, or `=` when one side is NOT NULL | `null == null` is true (Kotlin meaning) |
| `it.status != q` (both nullable) | `is distinct from`, or `(a <> b or a is null)` | NULL rows returned when `q` is not null (Kotlin meaning) |
| `it.description.equals(text)` (`String?`) | the same as `==` | null-safe |
| `x == null`, `x != null` | `is null`, `is not null` | 2-valued |
| `it.id == id` (`it.id` is a `Long?`, `id` a captured `Long`), `it.id == null`: a column that can never be NULL (the id, a primitive, `nullable = false`, no association step on the path) | `e0.id = :p1` instead of `not (e0.id is null or e0.id <> :p1)`; `it.id == null` gives `1=0` (tested with Java) | The column has no NULL. A path through an association (`it.customer?.id`) keeps the check |
| `!`, `&&`, `\|\|` | `NOT`, `AND`, `OR` | SQL 3-valued logic |
| `it.flag == true` / `it.flag != true` | `is not distinct from true` / `is distinct from true` | like Kotlin. Works on all three DBs |
| `it.flag ?: false` (condition) | `(e0.flag is not null and e0.flag = true)` | NULL counts as false |
| `it.customer?.name`, `it.customer!!.name` in `where` | `j1.name` + `join e0.customer j1` (inner join by default) | no customer: the order is not returned, also for `it.customer?.name == null` |
| `it.customer == null \|\| it.customer?.name == p` | `e0.customer is null or j1.name = :p1` + `left join e0.customer j1` | no customer: returned, like Kotlin |
| the same path only in `orderBy` or `select` | `j1.name` + `left join e0.customer j1` | no customer: the column is NULL |
| `it.customer == null`, `it.customer != null` | `e0.customer is null`, `e0.customer is not null` (FK column, no join) | orders without a customer / with a customer |
| `it.customer?.id` | `e0.customer.id` (FK column, no join) | no customer: NULL |
| `a ?: b` (value) | `coalesce(a, b)` in values; SARGable forms in string conditions | see [9.5](09-kotlin-notes.md#95-null-operators---and-) |
| `it.id in ids` | `e0.id in (:p1)` | NULL id: not returned |
| `it.id !in ids` | `e0.id not in (:p1)` (the id is never NULL). For a column that can be NULL: `(x is null or x not in (:p1))` | NULL value (column that can be NULL): returned, like Kotlin |
| `it.status in 1..5`, `!in 1..5`, `when (it.status) { ... }` | `between`, `not (... between ...)`, `in (...)` | NULL subject: not returned, also under `NOT` (no guard, D22) |
| A subquery with `x !in sub...` | `not exists (...)` | correct even when the subquery returns NULL |
| `Agg.sum`, `avg`, `min`, `max` | `sum(...)` ... | NULL when there are no rows or all values are NULL |
| `Agg.count()` | `count(*)` | 0 when there are no rows |

### Captured null values

| Where the captured value is | `null` allowed? |
|---|---|
| Null-safe forms: `==` / `!=` between two nullable values | yes |
| Under a null guard on the same value: `p == null \|\| it.status!! > p` | yes. The guard is decided in the application when the query runs: `p == null` gives no filter, any other value gives `e0.status > :p1` |
| With `!!` or in an order compare (`<` `<=` `>` `>=`): `it.price!! > p!!` | **no.** Error when the value is bound (Kotlin would throw NPE) |
| A collection with a `null` element in `in` | **no.** Error when bound |
| `NaN`, `Infinity` | **no.** Always an error |

## 10.4 Operator rules (R1 to R24)

This is the short form of the rule table in the design (section 3.6 and KOTLIN.md section 5), with Kotlin forms.

| # | Lambda form | Meaning |
|---|---|---|
| R1 | Compare of non-null primitives, or a compare after `!!` (`it.qty > 5`, `it.price!! > p`) | `=`, `<>`, `<`, ... SQL NULL rules |
| R2 | `==` / `!=` that compile to a reference compare (enum `==`; `===`, see R17) | Null-safe value equality. Not object identity |
| R3 | `x == null`, `x != null` | `IS NULL`, `IS NOT NULL` |
| R6 | `!`, `&&`, `\|\|` | `NOT`, `AND`, `OR`. SQL 3-valued logic |
| R7 | String value compare | Follows the **DB collation** (case, trailing spaces). Oracle treats `''` as NULL |
| R8 | Entity `==` entity | Planned: identifier equality. Not in v0.1 (KC13) |
| R9 | Captured value | Bind parameter with the value at run time |
| R10 | Property access (`it.price`) | Getter call, mapped to the JPA attribute |
| R11 | Kotlin `==`, `!=` that compile to `Intrinsics.areEqual` (references, or nullable on both sides) | Null-safe value equality. `=` only when one side is NOT NULL by the schema or the bound value |
| R12 | Nullable primitive and a primitive (`it.id == 1L`, `id: Long?`) | The hidden null check is ignored: `e0.id = 1`, `e0.id <> 1` |
| R13 | `!!` and other compiler null checks | Ignored (D23) |
| R14 | `?.` | A path or a value with NULL propagation; the same column as without `?.` |
| R15 | `?:` | `coalesce(a, b)` in values. Boolean elvis in conditions becomes `(x is not null and x = true)`. Boolean elvis as a value is rejected (KC21) |
| R16 | Association join | Replaced by R24 |
| R17 | `===`, `!==` | Value equality, like `==` |
| R18 | Ranges `in a..b`, `until`, `..<`, `downTo`, `!in` | `between`, `>= and <`, `not (...)`. No automatic null guard |
| R19 | Collections `x in c`, `x !in c` (captured `c`) | `x in (:p)`, `(x is null or x not in (:p))`. Null element: error. Empty: FALSE / TRUE |
| R20 | `when (subject)` with constant branches that end in `true`/`false` | `in (...)` or `OR` of compares |
| R21 | Kotlin string functions (`startsWith`, `endsWith`, `contains`, `equals(ignoreCase)`, `lowercase`, `length`, `isNullOrEmpty`) | `like ... escape '\'`, D24 ignore-case paths, `lower`, `length`, `(x is null or x = :p)` |
| R22 | Kotlin host values (captured `var`, private properties, `companion` values) | Read on every run. Writing them is rejected |
| R23 | Property references `Order::price` | The same as the getter. **Not implemented yet** in `orderBy` (`orderBy(Order::price)`): use `orderBy { it.price }` |
| R24 | Association path (`it.customer?.name`, `it.customer!!.name`, Java entity `it.customer.name`) | A column. To-one association: an alias shared by where, order by and select. In `where` it is an inner join by default, and a `left join` when a null guard keeps rows without the association (D36); used only in order by or select, a `left join`. Embedded: same table. Ends with target `@Id`, or the association compared with `null`: FK column, no join. Collection: error |

The Java-only rules R4 (`equals` on an entity value) and R5 (Java null-safe helpers) are in the [Java track](../java/10-rules-and-limits.md). In Kotlin, `==` already gives the null-safe meaning.

Other rules:

- `x.compareTo(y) > 0`, `isBefore`, `isAfter` become `x > y`, ...
- Constants written in the lambda (also `const val`) are literals: `it.price!! > 100.0` is `e0.price > 100.0`, `"O'Brien"` is `'O''Brien'`. Captured and computed values (`BigDecimal("1.5")`) are bind parameters (D37).
- The compiler sometimes moves `!` around (De Morgan, double negation). This never changes the result.
  lambdaQL keeps each condition exactly once and never "simplifies" your logic.
- A condition you wrote that is always true in Kotlin (`x > 5 || !(x > 5)`) is not simplified.
  In SQL it drops rows where `x` is NULL. This is the SQL rule.

## 10.5 Kotlin and SQL differences

These differences come from SQL itself. They are not compiler problems.
"G" numbers are the IDs in the design (section 2.7).

| # | Difference | What lambdaQL does |
|---|---|---|
| G1 | Null-safe inequality: in Kotlin `null != 3` is true; SQL `status <> 3` is UNKNOWN | `!=` between two nullable values is null-safe (`is distinct from`), the same rows as Kotlin. `!=` with a constant uses SQL meaning (D22) |
| G2 | `it.flag != true` vs `NOT flag` | Renders `e0.flag is distinct from true` (works on all three DBs) |
| G3 | `name = ?` with a NULL parameter returns 0 rows | Null-safe forms use `is [not] distinct from` |
| G4 | `status NOT IN (1, NULL)` always returns 0 rows | A `null` in a captured collection is an error. Subquery `!in` is `NOT EXISTS` |
| G5 | Kotlin throws NPE (`!!`, unboxing), SQL just compares | Documented: an entity NULL follows SQL rules |
| G6 | `NaN`: H2 sorts NaN as the largest value and `NaN = NaN` is true; MariaDB cannot store it; Oracle can store it only in `BINARY_DOUBLE` | A captured NaN or Infinity is an error. A stored NaN follows the DB |
| G7 | `Float` column compared with a `Double` value: results differ by DB | **No bit-exact Kotlin result is promised** for `Float`/`Double`. lambdaQL adds no `CAST`. Choose your column mapping (for example Oracle `binary_float` / `binary_double`) |
| G9 | `x > 5 \|\| !(x > 5)` drops NULL rows | Not simplified, rendered as written |
| G10 | SQL does not promise evaluation order: `qty != 0 && total / qty > 2` can still divide by zero | `/`, `%`, `CAST` under a guard are rejected in v0.1 (D13) |
| G11 | Strings: MariaDB `_ci` ignores case and trailing spaces; Oracle stores `''` as NULL; Oracle `NLS_SORT` follows the JVM locale | Documented: string compare and sort follow the DB. On Oracle, set `NLS_SORT=BINARY` (session init or `-Duser.language=en`). `it.description == ""` and `isEmpty()` differ on Oracle |
| G12 | Kotlin `===` is identity (false for two `Long` boxes of 1000) | lambdaQL defines `===` and `==` as value equality |
| G13 | Legacy boolean columns (MariaDB `TINYINT(1)`, Oracle `NUMBER(1)`) holding values other than 0/1 are read differently | Hibernate's own DDL (`bit(1)`, Oracle 23 `boolean`) is fine. Take care with legacy schemas |
| G14 | Oracle 23 bug: `WHERE (a AND b) = false` / `IS FALSE` / `IS NULL` on a compound condition loses rows | lambdaQL never wraps a compound condition in a boolean test. Negation is always `NOT(...)` |
| G15 | Oracle `CHAR` columns do not match a `String` bind | Documented: take care with the entity mapping |

More differences:

| Difference | Kotlin | SQL | lambdaQL |
|---|---|---|---|
| `BigDecimal` equality | `==` compares scale (`1.0` vs `1.00` is false) | `=` compares value | Value equality |
| Integer division | `5 / 2` is `2` | MariaDB gives `2.5` | DB-dependent. The final rule is not decided; it may be rejected in v0.1 |
| `BigDecimal` division and function results | Kotlin scale rules (`HALF_EVEN`) | The DB decides the scale (for example H2 1, MariaDB and Oracle 0 for a function result) | Documented |
| Unicode ignore-case (`ſ`, `İ`) | Kotlin rules | DB collation | Follows the DB |
| String length | UTF-16 units | H2 UTF-16 units, MariaDB code points | Documented |
| String templates with NULL | `"null"` text | NULL (Oracle: `NULL \|\| 'x'` is `'x'`) | String concatenation is planned, not in v0.1 |
| Nullable `when` / range subject | goes to `else`; `!in` is true | not returned, also under `NOT` | SQL meaning (D22) |
| Non-null type `String` | never null | the column can be NULL | lambdaQL uses JPA metadata |
| `LocalDateTime.now()` in a lambda | app time | – | Bound as **app time**. DB time (`current_timestamp`) is not supported |
| Equal elements in a `Set` | `equals` / `hashCode` decide (a `data class`: the primary constructor properties) | The primary key tells rows apart | `toSet()` and `findSet()` throw `IllegalStateException` if the set is smaller than the number of rows (the entity's `equals` / `hashCode` treats different rows as equal, or its `@Id` is not unique, for example a view). Use `toList()` (D42, tested with Java) |

## 10.6 Database differences

lambdaQL hides many differences. Some you must know.

### Sorting and paging

| Item | H2 | MariaDB | Oracle | lambdaQL |
|---|---|---|---|---|
| NULL position, `ASC` | first | first | **last** | `ASC` means NULLS FIRST on all DBs (D16) |
| NULL position, `DESC` | last | last | **first** | `DESC` means NULLS LAST on all DBs (D16) |
| `NULLS FIRST/LAST` syntax | yes | **no** (syntax error) | yes | Hibernate emulates it with `CASE` on MariaDB: **no index** for the sort |
| Paging SQL | `offset ? rows fetch first ? rows only` | `limit ?,?` | `offset ? rows fetch first ? rows only` | Passed to Hibernate (`setFirstResult` / `setMaxResults`) |
| Order of equal rows | – | – | random | With paging, lambdaQL adds the root `@Id asc` when the keys do not contain the id |
| String order | case-sensitive | `_ci` | `NLS_SORT` (JVM locale) | Follows the DB |
| Implicit path in `order by` | inner join (rows lost) | same | same | lambdaQL uses `left join` (or the `where` join of the same path) |

lambdaQL writes `nulls first` / `nulls last` into the HQL for every key that can be NULL. Hibernate drops it when it is the database default.
The default (`ASC` = NULLS FIRST, `DESC` = NULLS LAST) is the same as H2 and MariaDB, so no extra SQL is
needed there; only Oracle gets a `NULLS` clause. To change the NULL position, use
`by.asc(key, false)` (nulls last) or `by.desc(key, true)` (nulls first) (D35). On MariaDB these use the `CASE` emulation.

### Conditions and values

| Item | H2 | MariaDB | Oracle |
|---|---|---|---|
| `is not distinct from` | native | `<=>` (uses an index) | `decode(...)` (no index), so lambdaQL prefers `=` when it can |
| `is [not] true`, `coalesce(boolean, false)` | works | works | **ORA-00932**. lambdaQL never writes them |
| Empty string `''` | a value | a value | **NULL** |
| Case and trailing spaces | exact | `_ci`: ignored | exact (`NLS_SORT` for sorting) |
| `NaN` | stored, largest value | cannot be stored | `BINARY_DOUBLE` only |
| `Float` columns | REAL literal rules | widened to double | Hibernate maps `Float` to `float(24)` (decimal) |
| Integer `/` | integer | **decimal** (`5/2 = 2.5`) | – |
| Large `in` list (1,500 values) | works | works | works on 23ai (the 1,000 limit of older Oracle versions was not measured) |

### Subqueries

| Item | What happens | lambdaQL |
|---|---|---|
| Subquery `order by` without `limit` | Oracle: syntax error (ORA-00907). MariaDB: ignored | Removed, with a WARN |
| MariaDB `IN (... limit n)` | Not supported (error 1235) | Wrapped automatically: `in (select x.id from (... limit n) x)` |
| MariaDB `lateral` | Wrong results (emulation) | Rewritten with `row_number() over (...)` (not implemented yet) |

### Update, insert, delete

| Item | What happens | lambdaQL |
|---|---|---|
| `SET a = ..., b = a + 1` (read a column after you set it) | MariaDB reads the **new** value, H2 and Oracle the old value | Error at analysis time (U3) |
| Association path in DML (`it.customer?.name`) | Hibernate makes different joins per DB and **loses rows** with no customer | lambdaQL writes a correlated scalar subquery (in `where` and in SET). In `where`, a path that a select would inner join also gets `customer.id is not null`, so DML hits the same rows as the query (U9, tested) |
| `DELETE` with a subquery on the same table | MariaDB error 1093 | Wrapped in a derived table (U17; not implemented yet) |
| The same enum value bound once for a STRING and an ORDINAL column | H2 and MariaDB store a **wrong value without error** | One parameter per place, never shared (U7) |
| Many rows in `values(list) { }` | Oracle: hard limit 65,535 binds (ORA-01745), very slow long before | Split: at most 500 values per statement, all in one transaction (U13) |
| `update` / `delete` with `order by ... limit` | MariaDB rejects `limit` in an `in` subquery (error 1235) and a subquery on the target table (error 1093) | `e0.id in (select t.id from (select x0.id as id ... order by ... limit n) t)`: the derived table `t` works on all three databases. The inner select uses the select joins (D36), so the rows are the same as the select (tested with Java) |
| `on conflict` / upsert | H2 and Oracle: `merge`. MariaDB: `insert ... on duplicate key update`, and an updated row counts as 2. Hibernate's `do nothing` fails on H2 and Oracle | `upsert` and `onConflict(...).update(...)` use Hibernate's form. `ignore()` is written as `insert ... select ... where not exists`. The row count is returned as it is (D40, tested) |

### Procedures and functions

| Item | H2 | MariaDB | Oracle |
|---|---|---|---|
| OUT / INOUT parameters | **no** | yes | yes |
| Several result sets | no | yes | yes (REF CURSOR) |
| Affected row count of a procedure | – | – | always -1, so lambdaQL does not offer it; use an OUT parameter |
| Boolean parameters and results | – | yes | 23ai and later |
| Stored function in a virtual column / function index | – | not possible (error 1901) | possible (function-based index) |

Because H2 has no OUT parameters, procedure features are checked on MariaDB and Oracle.

## 10.7 Not supported, and what to write instead

lambdaQL throws a clear error for every form in this list. "Planned" means a later version.

### Lambda form and loading

| Not supported | Write instead |
|---|---|
| Object expression (`object : ...`), a functional interface that is not `Serializable` (KC5) | A lambda literal |
| A lambda stored as a Kotlin function type (`val f: (Order) -> Boolean`) (KC4) | Pass the lambda literal |
| `-Xsam-conversions=class` (KC1), a lambda created in an `inline fun` body (KC2) | See [9.14](09-kotlin-notes.md#914-kotlin-limits-kc1-to-kc21) |
| A callable reference to your own Kotlin `fun interface` (KC3) | A lambda literal |
| Local `val`/`var`, loops, many statements in a condition, sort or select lambda | One expression. Compute values outside the lambda |
| Writing a captured variable (`n++`) (KC12) | Compute outside the lambda |
| `@JvmField`, `internal`, `value class`, `@get:JvmName` properties (KC15, KC16) | A public normal property |
| Lambdas compiled for a newer JDK than the bundled ASM knows | Update ASM (lambdaQL does not shade it) |

### Conditions

| Not supported | Write instead |
|---|---|
| Value `if` / `when` / `takeIf`, general `if (c) x else y` (KC6) | `&&` / `\|\|`. Choose the value outside the lambda. `CASE WHEN` is planned |
| (Supported: `if (c) x else false`, `if (c) true else x`, `when { a -> true; ...; else -> false }`) | They mean `AND`, `OR` |
| Boolean infix `and`, `or`, `xor` | `&&`, `\|\|` |
| `/`, `%`, `CAST` under a guard (`it.qty != 0 && it.price!! / it.qty > 2`) | Compare without division: `it.qty > 0 && it.price!! > 2.0 * it.qty` |
| Entity `==` entity (`it.customer == c`) (KC13) | `it.customer?.id == c.id` (planned: identifier equality) |
| Collection path (`it.items...`, `@OneToMany`, `@ManyToMany`) | An `exists()` subquery, or a join |
| `item in it.items` | An `exists()` subquery |
| Collection built inside the lambda (`listOf(1, 2)`, `setOf(...)`) (KC8) | Build it outside and capture it. (`groupBy` keys are the exception) |
| `step` in a range | Two compares, or a captured collection |
| A `null` element in a captured collection | Remove nulls; add `\|\| it.x == null` if you want NULL rows |
| Captured `NaN` / `Infinity` | Check the value before the query |
| `isNaN()` (KC14) | – |
| Other function calls on an entity value (your own non-inline functions, `operator` functions) (KC11) | Write the condition from properties, or make your function `inline` |
| String templates or `+` with entity values (KC17) | Planned |
| `it.tags["k"]`, `it.lines[0]` on entity paths | – |

### Functions and strings

| Not supported (v0.1) | Write instead |
|---|---|
| `isBlank`, `isNullOrBlank` (KC9) | `isNullOrEmpty()` or a direct comparison |
| `trim`, `coerceAtLeast`, `toLocalDate` and other stdlib functions on columns (KC20) | A direct comparison, or a DB function (`@SqlFunction`). Function mapping is planned |
| Date functions (`YEAR(col) == 2024`) | A range: `it.updateAt!! >= from && it.updateAt!! < to` |
| `@SqlFunction` with a generic return type (`fun <X> nvl(a: X, b: X): X`), entity, collection or `Unit` return | One of: `Double`, `Integer` (`Int`), `Long`, `BigDecimal`, `String`, `LocalDate`, `LocalDateTime`, `Boolean` |
| `@SqlFunction` with the same name as a Hibernate built-in function (`COALESCE`, `ROUND`, `SYSDATE`, ...) | Use a prefix or a schema name (`PUBLIC.SQ_FN`) |
| `@SqlFunction` with default arguments, `inline`, `vararg` | A plain function with fixed parameters |
| Extension functions, functional interface constants, function type properties as DB functions | Interface `companion object` + `@JvmStatic` |
| User aggregate or window functions | Planned |
| Calling `Agg.*` outside a query lambda, or in `where` | Use it in `select` / `having` |

### Sort and paging

| Not supported | Write instead |
|---|---|
| A condition or loop in the sort lambda (`if (x) by.asc(...)`) | Build two queries, or pass the `Direction` as a captured value: `by.sort(o.price, dir)` |
| `orderBy` twice, `where` after `orderBy`, `orderBy` after `limit` | One `orderBy` with all keys (these do not compile) |
| An entity as a sort key (`by.desc(o.customer)`) | A column: `by.desc(o.customer?.name)` (does not compile) |
| Property references in join sort lambdas | A lambda |
| Property references in `orderBy` (`orderBy(Order::price)`, not implemented yet) | A lambda: `orderBy { it.price }` |
| `SortWhitelist` (not implemented yet) | `val dir = Direction.parse(text)` and `by.sort(key, dir)` |
| Collection fetch join with `limit` / `offset` | Page first, then load the collection |
| `ignoreCase()` in a Spring `Sort` ([12.5](12-spring-data-jpa.md#125-spring-sort-and-pageable)), not supported yet | A sort without `ignoreCase()` |
| Function keys (`lower(name)`) | Planned |
| Sort by an aggregate alias (`by.desc(s.total)`) | Planned |
| Boolean elvis as a sort key (KC21) | The key `o.flag` with a NULL position: `by.asc(o.flag, false)` (nulls last) |

### Subqueries

| Not supported | Write instead |
|---|---|
| `first()` in a scalar compare that is not one row | Use an aggregate (`max`, `min`, `avg`, `sum`, `count`) or `orderBy { }.limit(1)...first()` |
| `orderBy` in a subquery without `limit` | It has no effect, so it is removed (WARN) |

### Select and group by

| Not supported | Write instead |
|---|---|
| A non-aggregate value in `select` / `having` that is not a `groupBy` key | Add it to `groupBy` |
| A normal class as the row type of a derived table or CTE | A Kotlin data class (or a Java record) |
| `arrayOf(...)` / `setOf(...)` as `groupBy` keys | `listOf(...)`, `Pair`, `Triple` |
| Boolean elvis as a select value (KC21) | Select `it.flag` and handle NULL in Kotlin |

### Update, insert, delete

| Not supported (v0.1) | Write instead |
|---|---|
| `update` / `delete` without `where` | Add `.all()` (without it, `execute()` does not compile) |
| Reading a column after you set it in the same statement | Use the old value on purpose, or run two updates |
| Setting the same column twice | One assignment |
| Conditional values (`if (c) a else b`, `when`, a toggle `!it.active` as a value) | Planned (`CASE WHEN`). Use two updates with different `where` |
| `if` inside the `set` lambda to choose which column to set | Call `set()` several times |
| `++`, `--`, `apply`, `with`, `also` in `set` | `it.cnt += 1`, direct assignment |
| `by[o.cnt] += 1` | `by[o.cnt] = o.cnt + 1` or `by.set(o.cnt, o.cnt + 1)` |
| Local `val` in an assignment lambda | Write the expression in each place |
| Setting a non-`@Id` property of an association (`it.customer?.name = x`) | Update `Customer` directly |
| `null` into a non-optional column (`@Id`, `nullable = false`, primitive) | – |
| Writing the `@Version` property yourself | lambdaQL uses `update versioned` for you |
| String templates in values | Planned |
| `insert ... select` with an explicit join | Planned. In v0.1, use many-to-one paths |
| An atomic `onConflict { ... }.ignore()` under concurrent inserts | Catch the duplicate key error (`ignore()` uses `not exists`, see [7.6](07-6-upsert.md)) |
| Upsert from `insert ... select` | Upsert row by row with `values(list) { ... }` |
| `orderBy { ... }` on update or delete without `limit(n)` | Add `limit(n)`, or remove `orderBy` (`IllegalStateException` otherwise) |
| An update or delete that must change one row, checked with `execute() == 1` and a rollback | `executeOne()`: it reads the ids first (at most 2). With 2 it changes nothing and throws `NonUniqueResultException`. With 1 it changes the row by `id = :p and (condition)`. It never changes more than one row (D41, tested with Java) |
| `executeOne()` together with `orderBy` or `limit` | Use one of them (`IllegalStateException` otherwise) |
| `offset` on update or delete | Not planned (D42). DML has only `limit(n)`. Choose the rows with `where { }`, `orderBy { }` and `limit(n)` |
| `limit` or `executeOne()` on an entity with a composite key | Not supported yet. Query the ids first, then `delete ... where { it.id in ids }` |
| DML with a Spring shared `EntityManager` and no transaction (no `@Transactional` in lambdaQL, D41) | Call it inside `@Transactional` or a `TransactionTemplate` |
| Cascade or `@PreRemove` with the repository method `deleteAll { ... }` (a bulk delete) | Spring Data's `deleteAll(Iterable)` or `delete(entity)` (see [12.4](12-spring-data-jpa.md#deleteallwhere-is-not-spring-datas-deleteall)) |
| `returning` | Query again after the DML |
| DB time (`current_timestamp`) | Pass app time (`LocalDateTime.now()` is bound as a value) |
| `insert ... select` with a `TABLE` id generator | Use `values { }`, or IDENTITY / SEQUENCE |
| `insert` into a JOINED or TABLE_PER_CLASS subclass | Not supported (Hibernate 6.6.5 fails) |

Also note for DML: bulk DML does not go through the persistence context. Hibernate flushes first, but
managed entities in the same session keep old values afterwards. Call `clear()` (or `refresh`).
JPA cascade and `orphanRemoval` do not apply.
A DML statement needs a transaction: `QueryBuilder(EntityManagerFactory)` opens its own; a builder with an `EntityManager` (also Spring's shared one) needs the caller's, and without it fails before any SQL is sent.

### Procedures

| Not supported | Write instead |
|---|---|
| Anything except one stub function call in the lambda (logs, nested calls, branches) | One call. Compute arguments before |
| Named arguments with calculations (`calc(rate = r * 2, id = id)`) | Positional arguments, or compute the value before |
| `exec` for a procedure that has OUT parameters | `call` |
| DB default values for arguments | Pass every argument |
| Kotlin default arguments in a stub function | Pass every argument; remove the default |
| Oracle package overloads (not detected yet), dynamic procedure names | Planned |
| Oracle implicit results (`DBMS_SQL.RETURN_RESULT`), MariaDB packages, paging of procedure results | Not supported |

## 10.8 Caching: a lambda is analyzed once

Reading bytecode is the slow part, so lambdaQL does it **once per lambda**.

| Item | Rule |
|---|---|
| What is cached | The analysis result: an HQL template and the list of parameter sources. Failures are cached too |
| Cache key | The lambda's implementation class and method (name, signature, kind), its type, the entity classes, and the **ClassLoader** |
| Size | Limited (LRU), so it cannot grow forever and does not leak class loaders |
| Not cached | The bytecode itself (it is cheap to read again and large to keep) |
| Captured values | **Never cached.** They are read and bound each time the query runs |
| Calls on captured objects (`filter.minPrice`, `this.minStatus`, `LocalDateTime.of(...)`) | Run in the application **each time** the query runs, then bound as one parameter |
| Captured `var` | Read each time the query runs |
| Captured-only conditions (`p != null && ...`) | Kept as `:p1 is not null`, not folded into a constant, so the cache stays correct |
| `Direction` as a captured value | A bind value, read each time (the structure stays cached) |
| DML | Analyzed once per lambda class; checks between `set()` calls use small summaries |

Kotlin lambda names (`...$lambda$0`) can change with the compiler version or when you edit the source.
This is fine: the cache key also has the class, the signature and the class loader, and lambdaQL does not depend on the name format.

Measured with prototypes (JDK 21): the first analysis of a new lambda takes about **0.2 ms**.
After that, an update with two `set()` calls costs about **0.1 to 0.2 µs** for cache lookup, value
extraction and checks, even after 2 million runs. The goal: 1,000 calls of the same lambda read the
bytecode only once. Hibernate also caches its query plan by HQL string.

What this means for you:

```kotlin
// Good: one lambda, the value changes
.where { it.status == status }

// Not possible in v0.1: the shape of the query changes inside the lambda
.where { if (flag) it.status == 1 else it.active }

// Instead: choose the lambda outside (two lambdas, each analyzed once)
val list = if (flag) {
    qb.from(Order::class.java).where { it.status == 1 }.toList()
} else {
    qb.from(Order::class.java).where { it.active }.toList()
}
```

## 10.9 Tested environment

| Item | Version |
|---|---|
| Kotlin | 2.x (design checked with kotlinc 1.7.22, 2.1.10, 2.2.21, 2.3.0, 2.3.21) |
| Java (JVM) | 21 |
| Hibernate | 6.6 (design checked with 6.6.5). HQL extensions are used (for example `is distinct from`); other JPA providers are not guaranteed |
| Databases | H2 2.3, MariaDB 10.11, Oracle 23.26 (23ai) |
| Spring | Spring Boot 3.4.2 and Spring Data JPA 3.4.2 (the Spring module, [12](12-spring-data-jpa.md); Java repositories only). Spring Boot 4 is not verified yet |
| Kotlin row-by-row checks | H2 only so far. MariaDB and Oracle runs of the Kotlin cases are planned |
| Not tested | Kotlin later than 2.3, Kotlin Multiplatform, PostgreSQL, SQL Server, Oracle 19c and older (for example PL/SQL `BOOLEAN` parameters) |

---

[Kotlin track](README.md) | Previous: [9. Kotlin notes](09-kotlin-notes.md) | Next: [11. Errors and languages](../11-errors-and-languages.md) | [Java version of this page](../java/10-rules-and-limits.md)
