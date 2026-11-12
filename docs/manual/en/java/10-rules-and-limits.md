# 10. Rules and limits (Java)

> [!NOTE]
> **Status: partly implemented.**
> This chapter collects the rules of the lambdaQL design: how lambdas become SQL, how NULL works,
> where Java and SQL differ, where databases differ, and what is not supported.
> The rules were checked with prototypes on H2 2.3, MariaDB 10.11 and Oracle 23ai (Hibernate 6.6.5).
> Today the analysis core, `where` + `toList()` and the core of sorting and paging work (tested on H2, MariaDB and Oracle).
> The rules for subqueries, `select`, update/insert/delete and procedures below are **not implemented yet**. Some sorting rules are not implemented yet either (see [4.12](04-order-paging.md#412-rules-summary-s1-to-s14)).
> See the [Java track README](README.md#status) for the full status.

[Java track](README.md) | [Manual home](../README.md) | Previous: [9. Java notes](09-java-notes.md) | Next: [11. Errors and languages](../11-errors-and-languages.md) | [Kotlin version of this page](../kotlin/10-rules-and-limits.md)

Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md)

---

## Contents

- [10.1 The base rule: a lambda is a query](#101-the-base-rule-a-lambda-is-a-query)
- [10.2 SARGable rule (index friendly SQL)](#102-sargable-rule-index-friendly-sql)
- [10.3 NULL rules](#103-null-rules)
- [10.4 Operator rules (R1 to R24)](#104-operator-rules-r1-to-r24)
- [10.5 Java and SQL differences](#105-java-and-sql-differences)
- [10.6 Database differences](#106-database-differences)
- [10.7 Not supported, and what to write instead](#107-not-supported-and-what-to-write-instead)
- [10.8 Caching: a lambda is analyzed once](#108-caching-a-lambda-is-analyzed-once)
- [10.9 Tested environment](#109-tested-environment)

How to read the examples:

- The comment under an example shows the HQL that lambdaQL makes, when the design states it.
- Constants written in the lambda (numbers, strings, booleans, `null`, enum constants) are written into the HQL as literals.
  Captured and computed values are always bind parameters (`:p1`, `:p2`, ...) (D37).
- Entities: `Order` (`id`, `price`, `status`, `active`, `description`, `customer`, `updateAt`, `items`),
  `Customer` (`id`, `name`, `grade`), `Item` (`order`, `price`, `qty`).

---

## 10.1 The base rule: a lambda is a query

lambdaQL **never runs your lambda**. It reads the bytecode and writes HQL.
A lambda is a query written with Java syntax. So:

1. A path like `o.getCustomer().getName()` **names a column**. It is not a chain of calls.
   lambdaQL does not check if the customer is null (D20). In `where`, the path is an inner join by default, so an order without a customer is not selected (D36).
   If the condition can be true without a customer (a null guard), the path is a `left join`, so the result is the same as in Java.
   To find those orders, compare the association itself: `o.getCustomer() == null`.
2. NULL is handled **only where you wrote it** (`== null`, `!= null`) (D22).
   Other NULLs follow SQL rules: the condition is UNKNOWN and the row is not selected.
3. Java memory rules (object identity, boxing cache, string intern, NPE) do not apply.
   `==` between objects means **value equality** (D12).
4. **When there is no rule for a form, lambdaQL does not guess.** It throws a clear error
   with a code `[LQL-xxxx]` and the source line. It never makes SQL that is "probably right".

## 10.2 SARGable rule (index friendly SQL)

"SARGable" means: the database can use an index for the condition.
This is a base rule of lambdaQL (design section 3.9).

| # | Rule |
|---|---|
| 1 | **Do not put a function, an operator or a `CASE` on the column side.** If a value must change, change the parameter, in Java, before binding (for example lowercase with `Locale.ROOT`) |
| 2 | If two forms mean the same, choose the one with only simple comparisons and `AND` / `OR` / `NOT`. Correct 3-valued logic comes first; inside that, choose the SARGable form |
| 3 | If the meaning really needs a function on the column (for example ignore case), lambdaQL reads DB metadata at start and uses a path the DB already has: column collation, function index, linguistic index. If there is no path, it logs a WARN per column at start (strict mode: error) |
| 4 | Each rendering rule is checked with `EXPLAIN` on H2, MariaDB and Oracle |

### Examples

**Ignore case** (D24). The design uses this rule for the Kotlin `ignoreCase` functions. Java string functions on columns are planned and will follow the same rule.

| What the DB has | lambdaQL writes | Not this |
|---|---|---|
| Case-insensitive collation (MariaDB `_ci`) | `e0.name = :p1` | `lower(e0.name) = lower(:p1)` |
| Oracle function index `LOWER(name)` | `lower(e0.name) = :p1` (`:p1` lowercased in Java) | `lower(e0.name) = lower(:p1)` |
| Oracle linguistic index `NLSSORT(... BINARY_CI)` | `e0.name collate binary_ci = :p1` | |
| No path | WARN at start (strict mode: error) | |

**Null-safe equality when one side is NOT NULL**

```java
.where(o -> o.getDescription() == text)   // text is not null when the query runs
// e0.description = :p1                       (not "is not distinct from")
.where(o -> o.getDescription() != text)
// (e0.description <> :p1 or e0.description is null)
```

On Oracle, `is not distinct from` becomes `decode(...)`, which cannot use an index.
So when one side is known to be NOT NULL (`@Id`, `nullable = false`, a primitive property,
or a bound value that is not null), lambdaQL writes a plain `=`.

**Comparing two boolean expressions** (D25)

```java
.where(o -> o.isActive() == (o.getStatus() > c))
// (e0.active = true and e0.status > :p1) or (e0.active = false and e0.status <= :p1)
```

The `CASE` form (`e0.active = case when ... end`) gives the right rows but needs a full scan,
so lambdaQL does not use it.

**NOT IN with a subquery** is written as `NOT EXISTS` (an anti-join, and NULL-safe; see 10.3).

### What the measurements showed

MariaDB 10.11 and Oracle 23ai, 20,000 to 50,000 rows:

| Form | MariaDB | Oracle | OK? |
|---|---|---|---|
| `name = 'abc'` on a `_ci` column | index lookup | – | Yes |
| `LOWER(name) = LOWER('abc')` | full index scan (19,791 rows) | full table scan (no function index) | **No** |
| `lower(name) = :p_lowered` + function index | – | index range scan (also `LIKE 'ab%'`) | Yes |
| `name COLLATE BINARY_CI = :p` + linguistic index | – | index range scan | Yes |
| Boolean `==` as `AND`/`OR` | range (350 rows) | 2 index range scans + bitmap OR | Yes |
| Boolean `==` as `CASE` | full index scan (49,648 rows) | full table scan | **No** |

### Planned items under the same rule

| Form | Planned SQL |
|---|---|
| Date functions like "year of `updateAt` is 2024" | `e0.updateAt >= :from and e0.updateAt < :to` |
| Sort by a function key (`lower(name)`) | Check the access path first, WARN if none |

### DB functions on a column

A DB function on the column side (`OrderFns.discount(o.getPrice()) > 10`) cannot use a normal index.
lambdaQL logs a **WARN** (strict mode: error). Exception: on Oracle, no WARN when the function is
`DETERMINISTIC` and a function-based index with the same expression exists. On MariaDB, a function
that is `NOT DETERMINISTIC` (the default) gives a full scan even when its arguments are only
parameters (`o.getPrice() > OrderFns.discount(rate)`), so lambdaQL also warns there.
See [8. Procedures and DB functions](08-procedures-functions.md).

## 10.3 NULL rules

| Lambda | SQL | NULL behavior |
|---|---|---|
| `o.getId() == 10L` (unboxing, a primitive on one side) | `e0.id = 10` | NULL: UNKNOWN, row not selected (Java would throw NPE) |
| `o.getId() == p` (two objects: `Long`, `String`, enum, ...) | null-safe `is not distinct from`, or `=` when one side is NOT NULL | `null == null` is true |
| `o.getDescription().equals(p)` | `e0.description = :p1` | NULL description: UNKNOWN (Java would throw NPE) |
| `Objects.equals(o.getDescription(), p)`, `"x".equals(o.getDescription())`, `Boolean.TRUE.equals(o.getActive())` | null-safe | Like Java |
| `!Boolean.TRUE.equals(o.getActive())` | `e0.active is distinct from true` | Like Java. Works on all three DBs |
| `x == null`, `x != null` | `is null`, `is not null` | 2-valued |
| `!`, `&&`, `\|\|` | `NOT`, `AND`, `OR` | SQL 3-valued logic |
| `o.getCustomer().getName()` in `where` | `j1.name` + `join e0.customer j1` (inner join by default) | No customer: the order is not selected |
| `o.getCustomer() == null \|\| o.getCustomer().getName().equals(p)` | `e0.customer is null or j1.name = :p1` + `left join e0.customer j1` | No customer: selected, like Java |
| `o.getCustomer().getName()` only in `orderBy` or `select` | `j1.name` + `left join e0.customer j1` | No customer: the column is NULL |
| `o.getCustomer() == null`, `o.getCustomer() != null` | `e0.customer is null`, `e0.customer is not null` (FK column, no join) | Orders without a customer / with a customer |
| `o.getCustomer().getId()` | `e0.customer.id` (FK column, no join) | No customer: NULL |
| `ids.contains(o.getId())` | `e0.id in (:p1)` | NULL id: not selected |
| `!ids.contains(o.getId())` | `e0.id not in (:p1)` (the id is never NULL). For a column that can be NULL: `(x is null or x not in (:p1))` | NULL value (column that can be NULL): selected, like Java |
| A subquery with `!...contains(x)` | `not exists (...)` | Correct even when the subquery returns NULL |
| `Agg.sum`, `avg`, `min`, `max` | `sum(...)` ... | NULL when there are no rows or all values are NULL |
| `Agg.count()` | `count(*)` | 0 when there are no rows |

### Captured null values

| Where the captured value is | `null` allowed? |
|---|---|
| Null-safe forms: `==` / `!=` between objects, `Objects.equals`, `"x".equals(col)` | Yes |
| Under a null guard on the same value: `p == null \|\| o.getStatus() > p` | Yes. The guard is decided in Java when the query runs: `p == null` gives no filter, any other value gives `e0.status > :p1` |
| Receiver of `p.equals(col)`, argument of `col.equals(p)`, unboxing compare, `<` `<=` `>` `>=` | **No.** Error when the value is bound (Java would throw NPE or always be false) |
| A collection with a `null` element in `in` | **No.** Error when bound |
| `NaN`, `Infinity` | **No.** Always an error |

## 10.4 Operator rules (R1 to R24)

This is the short form of the rule table in the design (section 3.6).

| # | Lambda form | Meaning |
|---|---|---|
| R1 | Primitive compare, or one side primitive (`o.getId() == 10L`, `o.getStatus() > p`) | `=`, `<>`, `<`, ... SQL NULL rules |
| R2 | Object `==` / `!=` (`Long`, `String`, `Boolean`, enum) | Null-safe value equality. Not object identity |
| R3 | `x == null`, `x != null` | `IS NULL`, `IS NOT NULL` |
| R4 | `a.equals(b)` | `a = b`, SQL NULL rules |
| R5 | `Objects.equals(a, b)`, `"x".equals(a)`, `Boolean.TRUE.equals(a)` | Null-safe value equality |
| R6 | `!`, `&&`, `\|\|` | `NOT`, `AND`, `OR`. SQL 3-valued logic |
| R7 | String value compare | Follows the **DB collation** (case, trailing spaces). Oracle treats `''` as NULL |
| R8 | Entity `==` entity | Planned: identifier equality. Not in v0.1 |
| R9 | Captured value | Bind parameter with the value at run time |
| R10 | Property access | Getter call, mapped to the JPA attribute |
| R11 to R23 | Kotlin rules | See the [Kotlin version of this page](../kotlin/10-rules-and-limits.md) |
| R24 | Association path (`o.getCustomer().getName()`) | A column. To-one association: an alias shared by where, order by and select. In `where` it is an inner join by default, and a `left join` when a null guard keeps rows without the association (D36); used only in order by or select, a `left join`. Embedded: same table. Ends with target `@Id`, or the association compared with `null`: FK column, no join. Collection: error |

Other rules:

- `x.compareTo(y) > 0`, `isBefore`, `isAfter` become `x > y`, ...
- Constants written in the lambda (also `static final` compile-time constants) are literals: `o.getPrice() > 100` is `e0.price > 100.0`, `"O'Brien"` is `'O''Brien'`. Captured and computed values (`new BigDecimal("1.5")`) are bind parameters (D37).
- javac sometimes moves `!` around (De Morgan, double negation). This never changes the result.
  lambdaQL keeps each condition exactly once and never "simplifies" your logic.
- A condition you wrote that is always true in Java (`x > 5 || !(x > 5)`) is not simplified.
  In SQL it drops rows where `x` is NULL. This is the SQL rule.
- IntelliJ warns "Number objects compared using `==`" for `o.getId() == p`. In a lambdaQL lambda this is
  the intended form (value equality). Use `@SuppressWarnings("NumberEquality")` if you want.

Which Java types give R1 and which give R2: see [9.3](09-java-notes.md#93-equality-of-boxed-values).

## 10.5 Java and SQL differences

These differences come from SQL itself. They are not compiler problems.
"G" numbers are the IDs in the design (section 2.7).

| # | Difference | What lambdaQL does |
|---|---|---|
| G1 | `!Objects.equals(status, 1)` is true in Java when `status` is NULL; `status <> 1` is UNKNOWN in SQL | Renders null-safe (`is distinct from`), same rows as Java |
| G2 | `!Boolean.TRUE.equals(active)` vs `NOT active` | Renders `active is distinct from true` (works on all three DBs) |
| G3 | `name = ?` with a NULL parameter returns 0 rows | Null-safe forms use `is [not] distinct from` |
| G4 | `status NOT IN (1, NULL)` always returns 0 rows | A `null` in a captured collection is an error. Subquery `NOT IN` is `NOT EXISTS` |
| G5 | Java throws NPE (unboxing, `o.getName().startsWith(...)`), SQL just returns rows | Documented: an entity NULL follows SQL rules |
| G6 | `NaN`: H2 sorts NaN as the largest value and `NaN = NaN` is true; MariaDB cannot store it; Oracle can store it only in `BINARY_DOUBLE` | A captured NaN or Infinity is an error. A stored NaN follows the DB |
| G7 | `float` column compared with a `double` value: results differ by DB | **No bit-exact Java result is promised** for float/double. lambdaQL adds no `CAST`. Choose your column mapping (for example Oracle `binary_float` / `binary_double`) |
| G9 | `x > 5 \|\| !(x > 5)` drops NULL rows | Not simplified, rendered as written |
| G10 | SQL does not promise evaluation order: `qty != 0 && total / qty > 2` can still divide by zero | `/`, `%`, `CAST` under a guard are rejected in v0.1 (D13) |
| G11 | Strings: MariaDB `_ci` ignores case and trailing spaces; Oracle stores `''` as NULL; Oracle `NLS_SORT` follows the JVM locale | Documented: string compare and sort follow the DB. On Oracle, set `NLS_SORT=BINARY` (session init or `-Duser.language=en`). `"".equals(x)` and `isEmpty()` differ on Oracle |
| G12 | Java `==` on objects is identity (false for `Long` 128 and up, `new String`) | lambdaQL defines `==` as value equality |
| G13 | Legacy boolean columns (MariaDB `TINYINT(1)`, Oracle `NUMBER(1)`) holding values other than 0/1 are read differently | Hibernate's own DDL (`bit(1)`, Oracle 23 `boolean`) is fine. Take care with legacy schemas |
| G14 | Oracle 23 bug: `WHERE (a AND b) = false` / `IS FALSE` / `IS NULL` on a compound condition loses rows | lambdaQL never wraps a compound condition in a boolean test. Negation is always `NOT(...)` |
| G15 | Oracle `CHAR` columns do not match a `String` bind | Documented: take care with the entity mapping |

More differences:

| Difference | Java | SQL | lambdaQL |
|---|---|---|---|
| `BigDecimal` equality | `equals` compares scale (`1.0` vs `1.00` is false) | `=` compares value | Value equality |
| Integer division | `5 / 2` is `2` | MariaDB gives `2.5` | DB-dependent. The final rule is not decided; it may be rejected in v0.1 |
| `BigDecimal` division and function results | Java scale rules | The DB decides the scale (for example H2 1, MariaDB and Oracle 0 for a function result) | Documented |
| Unicode ignore-case (`ſ`, `İ`) | Java rules | DB collation | Follows the DB |
| String length | UTF-16 units | H2 UTF-16 units, MariaDB code points | Documented |
| String concatenation with NULL | `"null"` text | NULL (Oracle: `NULL \|\| 'x'` is `'x'`) | String concatenation is planned, not in v0.1 |
| `LocalDateTime.now()` in a lambda | App time | – | Bound as **app time**. DB time (`current_timestamp`) is not supported |

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
| `float` columns | REAL literal rules | widened to double | Hibernate maps `Float` to `float(24)` (decimal) |
| Integer `/` | integer | **decimal** (`5/2 = 2.5`) | – |
| Large `IN` list (1,500 values) | works | works | works on 23ai (the 1,000 limit of Oracle 22 and lower was not measured) |

### Subqueries

| Item | What happens | lambdaQL |
|---|---|---|
| Subquery `order by` without `limit` | Oracle: syntax error (ORA-00907). MariaDB: ignored | Removed, with a WARN |
| MariaDB `IN (... limit n)` | Not supported (error 1235) | Wrapped automatically: `in (select x.id from (... limit n) x)` |
| MariaDB `lateral` | Wrong results (emulation) | Rewritten with `row_number() over (...)` |

### Update, insert, delete

| Item | What happens | lambdaQL |
|---|---|---|
| `SET a = ..., b = a + 1` (read a column after you set it) | MariaDB reads the **new** value, H2 and Oracle the old value | Error at analysis time (U3) |
| Association path in DML (`o.getCustomer().getName()`) | Hibernate makes different joins per DB and **loses rows** with no customer | lambdaQL writes a correlated `exists` (in `where`) or a correlated scalar subquery (in SET), so DML hits the same rows as the query (U9) |
| `DELETE` with a subquery on the same table | MariaDB error 1093 | Wrapped in a derived table (U17) |
| The same enum value bound once for a STRING and an ORDINAL column | H2 and MariaDB store a **wrong value without error** | One parameter per place, never shared (U7) |
| Many rows in `values(list, ...)` | Oracle: hard limit 65,535 binds (ORA-01745), very slow long before | Split by bind count (default 1,000 binds) inside a transaction (U13) |
| `on conflict` / upsert | Different on each DB | Not in v0.1 |

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
| Anonymous class, non-`Serializable` functional interface (`java.util.function.Predicate`) | A lambda literal or a method reference |
| Block lambda with local variables, loops, `switch`, `try` | One expression. Compute values outside the lambda |
| Writing a captured variable | Compute outside the lambda (Java also needs captured locals to be effectively final) |
| Lambdas compiled by ECJ (Eclipse compiler) | Not guaranteed. Tested with javac only |
| Lambdas compiled for a newer JDK than the bundled ASM knows | Update ASM (lambdaQL does not shade it) |

### Conditions

| Not supported | Write instead |
|---|---|
| General ternary `c ? x : y`, value ternary `o.getId() == (c ? p1 : p2)` | `&&` / `\|\|`. Choose the value outside the lambda. `CASE WHEN` is planned |
| (Supported: `c ? x : false`, `c ? true : x`, `c ? false : true`) | They mean `AND`, `OR`, `NOT` |
| Non-short-circuit `&`, `\|`, `^` | `&&`, `\|\|` |
| `/`, `%`, `CAST` under a guard (`qty != 0 && total / qty > 2`) | Compare without division: `qty > 0 && total > 2 * qty` |
| Entity `==` entity (`o.getCustomer() == c`) | `o.getCustomer().getId() == c.getId()` (planned: identifier equality) |
| Collection path (`o.getItems()...`, `@OneToMany`, `@ManyToMany`) | An `exists()` subquery, or a join |
| `o.getItems().contains(x)` | An `exists()` subquery |
| Collection built inside the lambda (`List.of(1, 2)`) | Build it outside and capture it. (`groupBy` keys are the exception) |
| A `null` element in a captured collection | Remove nulls; add `\|\| x == null` if you want NULL rows |
| Captured `NaN` / `Infinity` | Check the value before the query |
| `isNaN()` | – |
| Any other method call on an entity value (`o.items(...)`, your own methods) | Write the condition from columns |
| String concatenation (`o.getDescription() + "_x"`, also `"A" + code`) | Build the string outside the lambda. Concatenation with columns is planned |
| Streams and other lambdas inside the lambda | `contains` on a captured collection, or a subquery |

### Functions and strings

| Not supported (v0.1) | Write instead |
|---|---|
| Java `String` methods on entity values (`startsWith`, `contains`, `trim`, `toUpperCase`) | Planned |
| Date functions (`YEAR(col) == 2024`) | A range: `o.getUpdateAt() >= from && o.getUpdateAt() < to` |
| `@SqlFunction` with a generic return type (`<X> X nvl(X, X)`), entity, collection or `void` return | One of: `Double`, `Integer`, `Long`, `BigDecimal`, `String`, `LocalDate`, `LocalDateTime`, `Boolean` |
| `@SqlFunction` with the same name as a Hibernate built-in function (`COALESCE`, `ROUND`, `SYSDATE`, ...) | Use a prefix or a schema name (`PUBLIC.SQ_FN`) |
| Functional interface constants as DB functions, mixins | Interface `static` method |
| Your own static methods on entity values without `@SqlFunction` | A DB function with `@SqlFunction`, or a condition from columns |
| User aggregate or window functions | Planned |
| Calling `Agg.*` outside a query lambda, or in `where` | Use it in `select` / `having` |

### Sort and paging

| Not supported | Write instead |
|---|---|
| A condition or loop in the sort lambda (`if (x) by.asc(...)`) | Build two queries, or pass the `Direction` as a captured value: `by.sort(o.getPrice(), dir)` |
| `orderBy` twice, `where` after `orderBy`, `orderBy` after `limit` | One `orderBy` with all keys (these do not compile) |
| An entity as a sort key (`by.desc(o.getCustomer())`) | A column: `by.desc(o.getCustomer().getName())` (does not compile) |
| Method references in join sort lambdas | A lambda |
| `SortSpec`, `SortWhitelist` (not implemented yet) | `Direction dir = Direction.parse(text)` and `by.sort(key, dir)` |
| Collection fetch join with `limit` / `offset` | Page first, then load the collection |
| Function keys (`lower(name)`) | Planned |
| Sort by an aggregate alias (`by.desc(s.total())`) | Planned |

### Subqueries

| Not supported | Write instead |
|---|---|
| `first()` in a scalar compare that is not one row | Use an aggregate (`max`, `min`, `avg`, `sum`, `count`) or `orderBy(...).limit(1)...first()` |
| `orderBy` in a subquery without `limit` | It has no effect, so it is removed (WARN) |

### Select and group by

| Not supported | Write instead |
|---|---|
| A non-aggregate value in `select` / `having` that is not a `groupBy` key | Add it to `groupBy` |
| A plain DTO class as the row type of a derived table or CTE | A Java record |
| `Set.of(...)` as `groupBy` keys | `List.of(...)` |

### Update, insert, delete

| Not supported (v0.1) | Write instead |
|---|---|
| `update` / `delete` without `where` | Add `.all()` (without it, `execute()` does not compile) |
| Reading a column after you set it in the same statement | Use the old value on purpose, or run two updates |
| Setting the same column twice | One assignment |
| Conditional values (`c ? a : b`, toggle `!o.isActive()`) | Planned (`CASE WHEN`). Use two updates with different `where` |
| `if` inside the `set` lambda to choose which column to set | Call `set()` several times |
| Setting a non-`@Id` property of an association (`o.getCustomer().setName(x)`) | Update `Customer` directly |
| `null` into a non-optional column (`@Id`, `nullable = false`, primitive) | – |
| Writing the `@Version` property yourself | lambdaQL uses `update versioned` for you |
| String concatenation in values | Planned |
| `insert ... select` with an explicit join | Planned. In v0.1, use many-to-one paths |
| Upsert (`on conflict`) | Planned |
| DML with `order by ... limit` | Query the ids first, then `delete ... where ids.contains(id)` |
| `returning` | Query again after the DML |
| DB time (`current_timestamp`) | Pass app time (`LocalDateTime.now()` is bound as a value) |
| `insert ... select` with a `TABLE` id generator | Use `values(...)`, or IDENTITY / SEQUENCE |
| `insert` into a JOINED or TABLE_PER_CLASS subclass | Not supported (Hibernate 6.6.5 fails) |

Also note for DML: bulk DML does not go through the persistence context. Hibernate flushes first, but
managed entities in the same session keep old values afterwards. Call `clear()` (or `refresh`).
JPA cascade and `orphanRemoval` do not apply.

### Procedures

| Not supported | Write instead |
|---|---|
| Anything except one stub method call in the lambda (logs, nested calls, branches) | One call. Compute arguments before |
| `exec` for a procedure that has OUT parameters | `call` |
| DB default values for arguments | Pass every argument |
| Oracle package overloads, dynamic procedure names | Planned |
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
| Calls on captured objects (`order.getDescription().trim()`, `this.field`, `LocalDateTime.of(...)`) | Run in Java **each time** the query runs, then bound as one parameter |
| Captured-only conditions (`p != null && ...`) | Kept as `:p1 is not null`, not folded into a constant, so the cache stays correct |
| `Direction` as a captured value | A bind value, read each time (the structure stays cached) |
| DML | Analyzed once per lambda class; checks between `set()` calls use small summaries |

Measured with prototypes (JDK 21): the first analysis of a new lambda takes about **0.2 ms**.
After that, an update with two `set()` calls costs about **0.1 to 0.2 µs** for cache lookup, value
extraction and checks, even after 2 million runs. The goal: 1,000 calls of the same lambda read the
bytecode only once. Hibernate also caches its query plan by HQL string.

What this means for you:

```java
// Good: one lambda, the value changes
.where(o -> o.getStatus() == status)

// Not possible in v0.1: the shape of the query changes inside the lambda
.where(o -> flag ? o.getStatus() == 1 : o.isActive())

// Instead: choose the lambda outside (two lambdas, each analyzed once)
List<Order> list = flag
    ? qb.from(Order.class).where(o -> o.getStatus() == 1).toList()
    : qb.from(Order.class).where(o -> o.isActive()).toList();
```

## 10.9 Tested environment

| Item | Version |
|---|---|
| Java | 21 (javac). ECJ is not guaranteed |
| Hibernate | 6.6 (design checked with 6.6.5). HQL extensions are used (for example `is distinct from`); other JPA providers are not guaranteed |
| Databases | H2 2.3, MariaDB 10.11, Oracle 23.26 (23ai) |
| Not tested | PostgreSQL, SQL Server, Oracle 19c and older (for example PL/SQL `BOOLEAN` parameters) |

---

[Java track](README.md) | Previous: [9. Java notes](09-java-notes.md) | Next: [11. Errors and languages](../11-errors-and-languages.md) | [Kotlin version of this page](../kotlin/10-rules-and-limits.md)
