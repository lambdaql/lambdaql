<a id="10-rules-and-limits-java"></a>
# 10. Rules and limits (Java)

[Java track](README.md) | [Manual home](../README.md) | Previous: [9. Java notes](09-java-notes.md) | Next: [11. Errors and languages](../11-errors-and-languages.md) | [Kotlin version of this page](../kotlin/10-rules-and-limits.md)

Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md)

---

<a id="contents"></a>
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
  The rows in 10.3 also use these `Order` properties: `cnt` (`Integer`), `custName` (`String`) and `address` (`@Embedded`, `city` and `zip`).
  In those rows `active` is a nullable `Boolean` with the getter `getActive()`.

---

<a id="101-the-base-rule-a-lambda-is-a-query"></a>
## 10.1 The base rule: a lambda is a query

lambdaQL **never runs your lambda**. It reads the bytecode and writes HQL.
A lambda is a query written with Java syntax. So:

1. A path like `o.getCustomer().getName()` **names a column**. It is not a chain of calls.
   lambdaQL does not check if the customer is null (D20). In `where`, the path is an inner join by default, so an order without a customer is not selected (D36).
   A null guard (`... == null || ...`) does not change this. The path stays an inner join (D48). Use `leftJoin` to keep orders without a customer.
   To find those orders, compare the association itself: `o.getCustomer() == null`.
2. NULL is handled **only where you wrote it** (`== null`, `!= null`) (D22).
   Other NULLs follow SQL rules. The condition is UNKNOWN and the row is not selected.
3. Java memory rules (object identity, boxing cache, string intern, NPE) do not apply.
   `==` between objects means **value equality** (D12).
4. **When there is no rule for a form, lambdaQL does not guess.** It throws a clear error
   with a code `[LQL-xxxx]` and the source line. It never makes SQL that is "probably right".

<a id="102-sargable-rule-index-friendly-sql"></a>
## 10.2 SARGable rule (index friendly SQL)

"SARGable" means that the database can use an index for the condition.
This is a base rule of lambdaQL (design section 3.9).

| # | Rule |
|---|---|
| 1 | **Do not put a function, an operator or a `CASE` on the column side.** If a value must change, change the parameter in Java before binding (for example lowercase with `Locale.ROOT`) |
| 2 | If two forms mean the same, choose the one with only simple comparisons and `AND` / `OR` / `NOT`. Correct 3-valued logic comes first. Inside that, choose the SARGable form |
| 3 | If the meaning really needs a function on the column (for example ignore case), lambdaQL reads DB metadata at start and uses a path the DB already has: column collation, function index, linguistic index. If there is no path, it logs a WARN per column at start (strict mode: error) |
| 4 | Each rendering rule is checked with `EXPLAIN` on H2, MariaDB and Oracle |

<a id="examples"></a>
### Examples

**Ignore case** (D24). The design uses this rule for the Kotlin `ignoreCase` functions. Java string functions on columns are planned and will follow the same rule.

| What the DB has | lambdaQL writes | Not this |
|---|---|---|
| Case-insensitive collation (MariaDB `_ci`) | `e0.name = :p1` | `lower(e0.name) = lower(:p1)` |
| Oracle function index `LOWER(name)` | `lower(e0.name) = :p1` (`:p1` lowercased in Java) | `lower(e0.name) = lower(:p1)` |
| Oracle linguistic index `NLSSORT(... BINARY_CI)` | `e0.name collate binary_ci = :p1` | |
| No path | WARN at start (strict mode: error) | |

**Equality with plain `=` and `<>`** (decision D48)

```java
.where(o -> o.getDescription() == text)   // text is not null when the query runs
// e0.description = :p1
.where(o -> o.getDescription() != text)
// e0.description <> :p1                      (a NULL description does not match)
```

lambdaQL never writes `is [not] distinct from`. On Oracle this form becomes `decode(...)`, which cannot use an index.
Values of the entity follow the SQL meaning, so a plain `=` or `<>` is always correct. A captured `null` is bound as NULL and matches no row (`LQL-4045` at `WARN` level).

**Comparing two boolean expressions** (D25)

```java
.where(o -> o.isActive() == (o.getStatus() > c))
// (e0.active = true and e0.status > :p1) or (e0.active = false and e0.status <= :p1)
```

The `CASE` form (`e0.active = case when ... end`) gives the right rows but needs a full scan,
so lambdaQL does not use it.

**NOT IN with a subquery** is written as `NOT IN` (the SQL meaning, D48). A NULL in the subquery gives no rows. To skip NULLs, add a null check to the subquery (see 10.3).

<a id="what-the-measurements-showed"></a>
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

<a id="planned-items-under-the-same-rule"></a>
### Planned items under the same rule

| Form | Planned SQL |
|---|---|
| Date functions like "year of `updateAt` is 2024" | `e0.updateAt >= :from and e0.updateAt < :to` |
| Sort by a function key (`lower(name)`) | Check the access path first, WARN if none |

<a id="db-functions-on-a-column"></a>
### DB functions on a column

A DB function on the column side (`OrderFns.discount(o.getPrice()) > 10`) cannot use a normal index.
lambdaQL logs a **WARN** (`LQL-5001`). Exception: on Oracle, there is no WARN when a function-based index with the same expression exists.
On MariaDB, a function that is `NOT DETERMINISTIC` (the default) gives a full scan even when its arguments are only parameters (`o.getPrice() > OrderFns.discount(rate)`), so lambdaQL warns in this case too (`LQL-5007`).
With `SargableMode.STRICT`, the analysis fails instead of warning (`LQL-3066`, decision D50).

**SARGable mode** (decision D50). `SargableMode` has three values.

- `OFF`: no checks, and no metadata is read.
- `WARN` (the default): warnings are logged.
- `STRICT`: in the cases where `WARN` would log a warning (`LQL-5001`, `LQL-5007`, `LQL-5012`), the analysis fails with `org.lambdaql.NonSargableException` (`LQL-3066`).

Set the mode per persistence unit with `SargablePolicy.register(emf, SargableMode.STRICT)`, and remove it with `SargablePolicy.unregister(emf)`. In Spring, use `lambdaql.sargable.mode=STRICT` (see [12](12-spring-data-jpa.md)).
The check runs once per lambda, when the analysis result goes into the shared cache. So register the mode before the first query.

**Arithmetic on a column** (decision D50).

- `+ - * /` with one column and values only (`o.getCnt() * 2 > n`) can be moved to the value side. lambdaQL logs `LQL-5012` at `WARN` level, and it is an error with `STRICT`.
- Arithmetic between columns (`o.getPrice() * o.getCnt() > n`) and `%` cannot be moved, so lambdaQL only logs `LQL-5013` at `DEBUG` level. They are allowed with `STRICT` too.
- The moved form must have the same meaning. Divide by a decimal, as in `o.getCnt() > n / 2.0`. The integer division `n / 2` gives different rows for `>=`, `=`, `<` and for a negative `n`. If you multiplied by a negative number, turn the compare around. The `LQL-5012` message explains this.
- lambdaQL does not look up whether an index exists to reduce the warnings. Because of privileges and synonyms, "no index" and "unknown" get mixed up, and arithmetic on a column is a rare form.

See [8. Procedures and DB functions](08-procedures-functions.md).

<a id="103-null-rules"></a>
## 10.3 NULL rules

| Lambda | SQL | NULL behavior |
|---|---|---|
| `o.getId() == 10L` (unboxing, a primitive on one side) | `e0.id = 10` | NULL: UNKNOWN, row not selected (Java would throw NPE) |
| `o.getId() == p` (two objects: `Long`, `String`, enum, ...) | `e0.id = :p1` | NULL column: not selected. A captured `null`: no matching row (`= NULL`, `LQL-4045` at `WARN` level) |
| `o.getDescription() != p` | `e0.description <> :p1` | NULL column: **not** selected (SQL meaning, D48; Java would select it). A captured `null`: no matching row |
| `o.getDescription() == o.getCustName()` (two values of the entity) | `e0.description = e0.custName` | NULL on either side: not selected, and NULL and NULL too (SQL meaning, D48) |
| `o.getDescription().equals(p)` | `e0.description = :p1` | NULL description: UNKNOWN (Java would throw NPE) |
| `Objects.equals(o.getDescription(), p)`, `"x".equals(o.getDescription())`, `Boolean.TRUE.equals(o.getActive())` | the same as `==` | the same as `==` |
| `!Boolean.TRUE.equals(o.getActive())` | `e0.active <> :p1` | NULL: not selected (SQL meaning) |
| `!o.getActive()` (nullable `Boolean`) | `e0.active = false` | NULL: not selected (NULL is not false) |
| `x == null`, `x != null` | `is null`, `is not null` | 2-valued |
| `o.getCnt() >= 2.5` (integer column, decimal value) | compares the value as written: a constant is a literal (`e0.cnt >= 2.5`), a captured value and an IN list are also decimal literals (`e0.cnt >= 2.5BD`, `in (2.5BD, 3.0BD)`, D48 B8) | the index range scan stays (measured on MariaDB). The value is no longer cut, which returned wrong rows before |
| A whole value object (`@Embedded`): `o.getAddress().equals(addr)`, `Objects.equals(o.getAddress(), addr)`, `!o.getAddress().equals(addr)` | expanded per column: `e0.address.city = :p1 and e0.address.zip = :p2`, the negation is `<> ... or ...`. `== null` is `is null` on every column (and), `!= null` is `is not null` (or) | SQL meaning per column (D48): a NULL column matches neither `=` nor `<>`. A captured object that is `null`: no matching row (`LQL-4045` at `WARN` level) |
| `o.getId() == null` on a column that can never be NULL (the id, a primitive, `nullable = false`, no association step on the path) | `1=0`. Next to a compare, the check is dropped: `o.getId() != null && o.getId() == id` gives `e0.id = :p1` (tested) | The column has no NULL. A path through an association (`o.getCustomer().getId()`) keeps the check |
| `!`, `&&`, `\|\|` | `NOT`, `AND`, `OR` | SQL 3-valued logic |
| `o.getCustomer().getName()` in `where` | `j1.name` + `join e0.customer j1` (inner join by default) | No customer: the order is not selected |
| `o.getCustomer() == null \|\| o.getCustomer().getName().equals(p)` | `e0.customer is null or j1.name = :p1` + `join e0.customer j1` | No customer: **not** selected (inner join, D48; `LQL-5009` at `DEBUG` level). Use `leftJoin` to keep them |
| `o.getCustomer().getName()` only in `orderBy` or `select` | `j1.name` + `left join e0.customer j1`. A required association (`optional = false`) gives `join` | No customer: the column is NULL |
| `o.getCustomer() == null`, `o.getCustomer() != null` | `e0.customer is null`, `e0.customer is not null` (FK column, no join) | Orders without a customer / with a customer |
| `o.getCustomer().getId()` | `e0.customer.id` (FK column, no join) | No customer: NULL |
| `ids.contains(o.getId())` | `e0.id in (:p1)` | NULL id: not selected |
| `!ids.contains(o.getId())` | `e0.id not in (:p1)`. The same for a column that can be NULL | NULL value: **not** selected (SQL meaning, D48; `LQL-5009` at `DEBUG` level; Java would select it) |
| A subquery with `!...contains(x)` | `x not in (select ...)` | A NULL in the subquery: no row is selected (SQL meaning, D48) |
| `Agg.sum`, `avg`, `min`, `max` | `sum(...)` ... | NULL when there are no rows or all values are NULL |
| `Agg.count()` | `count(*)` | 0 when there are no rows |

<a id="captured-null-values"></a>
### Captured null values

| Where the captured value is | `null` allowed? |
|---|---|
| `==` / `!=` between objects, `Objects.equals`, `col.equals(p)`, `p.equals(col)` | Yes, but no row matches (`= NULL`). `LQL-4045` at `WARN` level when the query runs |
| Under a null guard on the same value: `p == null \|\| o.getStatus() > p` | Yes. The guard is decided in Java when the query runs: `p == null` gives no filter, any other value gives `e0.status > :p1`. A compare with only an unboxing to the value's own primitive type does not depend on where the guard is |
| Unboxing compare (`o.getPrice() > p`, `Double p`) | Yes, but no row matches (`> NULL`). An unboxing to the value's own primitive type is not run. `LQL-4045` at `DEBUG` level when the query runs |
| A method call on a captured value (`p.trim()`), an unboxing to a different primitive type (`p.intValue()`, `Double p`) | **No.** Error when the value is computed in Java (`LQL-4012`; Java would throw NPE) |
| A collection with a `null` element in `in` | **No.** Error when bound |
| `NaN`, `Infinity` | **No.** Always an error |

<a id="104-operator-rules-r1-to-r24"></a>
## 10.4 Operator rules (R1 to R24)

This is the short form of the rule table in the design (section 3.6).

| # | Lambda form | Meaning |
|---|---|---|
| R1 | Primitive compare, or one side primitive (`o.getId() == 10L`, `o.getStatus() > p`) | `=`, `<>`, `<`, ... SQL NULL rules |
| R2 | Object `==` / `!=` (`Long`, `String`, `Boolean`, enum) | Value equality, not object identity. `=` / `<>` with the SQL meaning (D48). A captured `null` matches no row |
| R3 | `x == null`, `x != null` | `IS NULL`, `IS NOT NULL` |
| R4 | `a.equals(b)` | `a = b`, SQL NULL rules |
| R5 | `Objects.equals(a, b)`, `"x".equals(a)`, `Boolean.TRUE.equals(a)` | The same as R2 |
| R6 | `!`, `&&`, `\|\|` | `NOT`, `AND`, `OR`. SQL 3-valued logic |
| R7 | String value compare | Follows the **DB collation** (case, trailing spaces). Oracle treats `''` as NULL |
| R8 | Entity `==` entity | Planned: identifier equality. Not in v0.1 |
| R9 | Captured value | Bind parameter with the value at run time |
| R10 | Property access | Getter call, mapped to the JPA attribute |
| R11 to R23 | Kotlin rules | See the [Kotlin version of this page](../kotlin/10-rules-and-limits.md) |
| R24 | Association path (`o.getCustomer().getName()`) | A column. To-one association: an alias shared by where, order by and select. In `where` it is always an inner join (D48; use `leftJoin` to keep rows without the association). Used only in order by or select, it is a `left join` (a required association with `optional = false` is an inner join, A4). Embedded: the same table. Ends with the target `@Id`, or the association compared with `null`: FK column, no join. Collection: error |

Other rules:

- `x.compareTo(y) > 0`, `isBefore`, `isAfter` become `x > y` and so on.
- Constants written in the lambda (also `static final` compile-time constants) are literals: `o.getPrice() > 100` is `e0.price > 100.0`, and `"O'Brien"` is `'O''Brien'`. Captured and computed values (`new BigDecimal("1.5")`) are bind parameters (D37). Exception: a decimal captured value compared with an integer column is a decimal literal (`2.5BD`, D48 B8).
- javac sometimes moves `!` around (De Morgan, double negation). This never changes the result.
  lambdaQL keeps each condition exactly once and never "simplifies" your logic.
- A condition you wrote that is always true in Java (`x > 5 || !(x > 5)`) is not simplified.
  In SQL it drops rows where `x` is NULL. This is the SQL rule.
- IntelliJ warns "Number objects compared using `==`" for `o.getId() == p`. In a lambdaQL lambda this is
  the intended form (value equality). Use `@SuppressWarnings("NumberEquality")` if you want.

Which Java types give R1 and which give R2: see [9.3](09-java-notes.md#93-equality-of-boxed-values).

<a id="105-java-and-sql-differences"></a>
## 10.5 Java and SQL differences

These differences come from SQL itself. They are not compiler problems.
"G" numbers are the IDs in the design (section 2.7).

| # | Difference | What lambdaQL does |
|---|---|---|
| G1 | `!Objects.equals(status, 1)` is true in Java when `status` is NULL, but `status <> 1` is UNKNOWN in SQL | Renders `status <> 1`: the SQL meaning (D48). NULL rows are left out, and `LQL-5009` is logged at `DEBUG` level. Write `status == null \|\| ...` to keep them |
| G2 | `!Boolean.TRUE.equals(active)`, `!active` (nullable) and Java | Renders `active <> :p1` and `active = false`: a NULL Boolean is not false (SQL meaning) |
| G3 | `name = ?` with a NULL parameter returns 0 rows | Kept (SQL meaning, D48). lambdaQL logs `LQL-4045` at `WARN` level when it runs. Write `x == null` to find NULL |
| G4 | `status NOT IN (1, NULL)` always returns 0 rows | A `null` in a captured collection is an error. A subquery `NOT IN` keeps the SQL meaning (D48): add a null check to the subquery |
| G5 | Java throws NPE (unboxing, `o.getName().startsWith(...)`), but SQL just returns rows | Documented: an entity NULL follows SQL rules |
| G6 | `NaN`: H2 sorts NaN as the largest value and `NaN = NaN` is true; MariaDB cannot store it; Oracle can store it only in `BINARY_DOUBLE` | A captured NaN or Infinity is an error. A stored NaN follows the DB |
| G7 | `float` column compared with a `double` value: results differ by DB | **No bit-exact Java result is promised** for float/double. lambdaQL adds no `CAST`. Choose your column mapping yourself (for example Oracle `binary_float` / `binary_double`) |
| G9 | `x > 5 \|\| !(x > 5)` drops NULL rows | Not simplified. Rendered as written |
| G10 | SQL does not promise evaluation order: `qty != 0 && total / qty > 2` can still divide by zero | `/`, `%`, `CAST` under a guard are rejected in v0.1 (D13) |
| G11 | Strings: MariaDB `_ci` ignores case and trailing spaces; Oracle stores `''` as NULL; Oracle `NLS_SORT` follows the JVM locale | Documented: string compare and sort follow the DB. On Oracle, set `NLS_SORT=BINARY` (session init or `-Duser.language=en`). `"".equals(x)` and `isEmpty()` behave differently on Oracle |
| G12 | Java `==` on objects is identity (false for `Long` 128 and up, `new String`) | lambdaQL defines `==` as value equality |
| G13 | Legacy boolean columns (MariaDB `TINYINT(1)`, Oracle `NUMBER(1)`) that hold values other than 0/1 are read differently | Hibernate's own DDL (`bit(1)`, Oracle 23 `boolean`) is fine. Take care with legacy schemas |
| G14 | Oracle 23 bug: `WHERE (a AND b) = false` / `IS FALSE` / `IS NULL` on a compound condition loses rows | lambdaQL never wraps a compound condition in a boolean test. Negation is always `NOT(...)` |
| G15 | Oracle `CHAR` columns do not match a `String` bind | Documented: take care with the entity mapping |

More differences:

| Difference | Java | SQL | lambdaQL |
|---|---|---|---|
| `BigDecimal` equality | `equals` compares scale (`1.0` and `1.00` gives false) | `=` compares value | Value equality |
| Integer division | `5 / 2` is `2` | MariaDB gives `2.5` | DB-dependent. The final rule is not decided. It may be rejected in v0.1 |
| `BigDecimal` division and function results | Java scale rules | The DB decides the scale (for example 1 on H2, 0 on MariaDB and Oracle for a function result) | Documented |
| Unicode ignore-case (`ſ`, `İ`) | Java rules | DB collation | Follows the DB |
| String length | UTF-16 units | H2 UTF-16 units, MariaDB code points | Documented |
| String concatenation with NULL | `"null"` text | NULL (Oracle: `NULL \|\| 'x'` is `'x'`) | String concatenation is planned, not in v0.1 |
| `LocalDateTime.now()` in a lambda | App time | – | Bound as **app time**. DB time (`current_timestamp`) is not supported |
| Equal elements in a `Set` | `equals` / `hashCode` decide | The primary key tells rows apart | `toSet()` and `findSet()` throw `IllegalStateException` if the set is smaller than the number of rows (the entity's `equals` / `hashCode` treats different rows as equal, or its `@Id` is not unique, for example a view). Use `toList()` (D42, tested) |

<a id="106-database-differences"></a>
## 10.6 Database differences

lambdaQL hides many differences. Some you must know.

<a id="sorting-and-paging"></a>
### Sorting and paging

| Item | H2 | MariaDB | Oracle | lambdaQL |
|---|---|---|---|---|
| NULL position, `ASC` | first | first | **last** | `ASC` means NULLS FIRST on all DBs (D16) |
| NULL position, `DESC` | last | last | **first** | `DESC` means NULLS LAST on all DBs (D16) |
| `NULLS FIRST/LAST` syntax | yes | **no** (syntax error) | yes | Hibernate emulates it with `CASE` on MariaDB: **no index** for the sort |
| Paging SQL | `offset ? rows fetch first ? rows only` | `limit ?,?` | `offset ? rows fetch first ? rows only` | Passed to Hibernate (`setFirstResult` / `setMaxResults`) |
| Order of equal rows | – | – | random | With paging, lambdaQL adds the root `@Id asc` when the keys do not contain the id. It does not add it when there is a unique key that can never be NULL (`unique = true` and `nullable = false`, `@NaturalId`) (A5) |
| String order | case-sensitive | `_ci` | `NLS_SORT` (JVM locale) | Follows the DB |
| Implicit path in `order by` | inner join (rows lost) | same | same | lambdaQL uses `left join` (an inner join for a required association, or the `where` join of the same path) |
| `@Lob` column in `order by`, `distinct`, `group by` | works | works | **ORA-22848 / ORA-22849** | The query is not changed. `LQL-5010` is logged at `DEBUG` level (B3) |
| Sort on an `@Enumerated(EnumType.STRING)` enum column | name order | name order | name order | The query is not changed. `LQL-5011` is logged at `DEBUG` level (B2). Declaration order only with an `ORDINAL` mapping |

lambdaQL writes `nulls first` / `nulls last` into the HQL for every key that can be NULL. It does not write it for a path that goes only through required associations and can never be NULL. Hibernate drops it when it is the database default.
The default (`ASC` = NULLS FIRST, `DESC` = NULLS LAST) is the same as H2 and MariaDB, so no extra SQL is
needed there. Only Oracle gets a `NULLS` clause. To change the NULL position, use
`by.asc(key, false)` (nulls last) or `by.desc(key, true)` (nulls first) (D35). On MariaDB these use the `CASE` emulation.

<a id="conditions-and-values"></a>
### Conditions and values

| Item | H2 | MariaDB | Oracle |
|---|---|---|---|
| `is not distinct from` | native | `<=>` (uses an index) | `decode(...)` (no index). lambdaQL does not write this form (D48) |
| `is [not] true`, `coalesce(boolean, false)` | works | works | **ORA-00932**. lambdaQL never writes them |
| Empty string `''` | a value | a value | **NULL** |
| Case and trailing spaces | exact | `_ci`: ignored | exact (`NLS_SORT` for sorting) |
| `NaN` | stored, largest value | cannot be stored | `BINARY_DOUBLE` only |
| `float` columns | REAL literal rules | widened to double | Hibernate maps `Float` to `float(24)` (decimal) |
| Integer `/` | integer | **decimal** (`5/2 = 2.5`) | – |
| Large `IN` list (1,500 values) | works | works | works on 23ai (the 1,000 limit of Oracle 22 and lower was not measured) |

<a id="subqueries"></a>
### Subqueries

| Item | What happens | lambdaQL |
|---|---|---|
| Subquery `order by` without `limit` | Oracle: syntax error (ORA-00907). MariaDB: ignored | Removed, with a WARN |
| MariaDB `IN (... limit n)` | Not supported (error 1235) | Wrapped automatically: `in (select x.id from (... limit n) x)` |
| MariaDB `lateral` | Wrong results (emulation) | Rewritten with `row_number() over (...)` (not implemented yet) |

<a id="update-insert-delete"></a>
### Update, insert, delete

| Item | What happens | lambdaQL |
|---|---|---|
| `SET a = ..., b = a + 1` (read a column after you set it) | MariaDB reads the **new** value, H2 and Oracle read the old value | Error at analysis time (U3) |
| Association path in DML (`o.getCustomer().getName()`) | Hibernate makes different joins per DB and **loses rows** with no customer | lambdaQL writes a correlated scalar subquery (in `where` and in SET). In `where`, a path that a select would inner join also gets `customer.id is not null`. So the DML changes the same rows as the query (U9, tested) |
| `DELETE` with a subquery on the same table | MariaDB error 1093 | The target ids are read first through a derived table: `d0.id in (select t.id from (select e0.id as id … where <condition>) t)` (U17). This works with correlated subqueries too, and it deletes the same rows on the three DBs (measured). `update` is not wrapped (it works on MariaDB too) |
| The same enum value bound once for a STRING and an ORDINAL column | H2 and MariaDB store a **wrong value without error** | One parameter per place, never shared (U7) |
| Many rows in `values(list, ...)` | Oracle: hard limit of 65,535 binds (ORA-01745), very slow long before that | Split into statements of at most 500 values, and all statements run in one transaction (U13) |
| `update` / `delete` with `order by ... limit` | MariaDB rejects `limit` in an `in` subquery (error 1235) and a subquery that reads the target table (error 1093) | `e0.id in (select t.id from (select x0.id as id ... order by ... limit n) t)`: the derived table `t` works on all three databases. The inner select uses the joins of the query (D36), so it targets the same rows as the query (tested) |
| `on conflict` / upsert | H2 and Oracle: `merge`. MariaDB: `insert ... on duplicate key update`, and an updated row counts as 2. Hibernate's `do nothing` fails on H2 and Oracle | `upsert` and `onConflict(...).update(...)` use Hibernate's form. `ignore()` is written as `insert ... select ... where not exists`. The row count is returned as it is (D40, tested) |

<a id="procedures-and-functions"></a>
### Procedures and functions

| Item | H2 | MariaDB | Oracle |
|---|---|---|---|
| OUT / INOUT parameters | **no** | yes | yes |
| Several result sets | no | yes | yes (REF CURSOR) |
| Affected row count of a procedure | – | – | always -1, so lambdaQL does not offer it. Use an OUT parameter |
| Boolean parameters and results | – | yes | 23ai and later |
| Stored function in a virtual column / function index | – | not possible (error 1901) | possible (function-based index) |

Because H2 has no OUT parameters, procedure features are checked on MariaDB and Oracle.

<a id="107-not-supported-and-what-to-write-instead"></a>
## 10.7 Not supported, and what to write instead

lambdaQL throws a clear error for every form in this list. "Planned" means a later version.

<a id="lambda-form-and-loading"></a>
### Lambda form and loading

| Not supported | Write instead |
|---|---|
| Anonymous class, non-`Serializable` functional interface (`java.util.function.Predicate`) | A lambda literal or a method reference |
| Block lambda with local variables, loops, `switch`, `try` | One expression. Compute values outside the lambda |
| Writing a captured variable | Compute outside the lambda (Java also needs captured locals to be effectively final) |
| Lambdas compiled by ECJ (Eclipse compiler) | Not guaranteed. Tested with javac only |
| When running on Java 21–23, lambdas compiled for a newer JDK than the bundled ASM knows | Update ASM (lambdaQL does not shade ASM). Does not apply when running on Java 24 or later (`java.lang.classfile` is used) |

<a id="conditions"></a>
### Conditions

| Not supported | Write instead |
|---|---|
| General ternary `c ? x : y`, value ternary `o.getId() == (c ? p1 : p2)` | `&&` / `\|\|`. Choose the value outside the lambda. `CASE WHEN` is planned |
| (Supported: `c ? x : false`, `c ? true : x`, `c ? false : true`) | They mean `AND`, `OR`, `NOT` |
| Non-short-circuit `&`, `\|`, `^` | `&&`, `\|\|` |
| `/`, `%`, `CAST` under a guard (`qty != 0 && total / qty > 2`) | Compare without division: `qty > 0 && total > 2 * qty` |
| Entity `==` entity (`o.getCustomer() == c`) | `o.getCustomer().getId() == c.getId()` (planned: identifier equality) |
| Size compare of a value object (`@Embedded`), compare with a different type (`LQL-3012`) | Compare the fields. An equality compare with the same type (`equals`, `==`, `!=`, `null`) is supported |
| Collection path (`o.getItems()...`, `@OneToMany`, `@ManyToMany`) | An `exists()` subquery, or a join |
| `o.getItems().contains(x)` | An `exists()` subquery |
| Collection built inside the lambda (`List.of(1, 2)`) | Build it outside and capture it. (`groupBy` keys are the exception) |
| A `null` element in a captured collection | Remove nulls. Add `\|\| x == null` if you want NULL rows |
| Captured `NaN` / `Infinity` | Check the value before the query |
| `isNaN()` | – |
| Any other method call on an entity value (`o.items(...)`, your own methods) | Write the condition from columns |
| String concatenation (`o.getDescription() + "_x"`, also `"A" + code`) | Build the string outside the lambda. Concatenation with columns is planned |
| Streams and other lambdas inside the lambda | `contains` on a captured collection, or a subquery |

<a id="functions-and-strings"></a>
### Functions and strings

| Not supported (v0.1) | Write instead |
|---|---|
| Java `String` methods on entity values (`startsWith`, `contains`, `trim`, `toUpperCase`) | Planned |
| Date functions (`YEAR(col) == 2024`) | A range: `o.getUpdateAt() >= from && o.getUpdateAt() < to` |
| `@SqlFunction` with a generic return type (`<X> X nvl(X, X)`), or an entity, collection or `void` return | One of: `Double`, `Integer`, `Long`, `BigDecimal`, `String`, `LocalDate`, `LocalDateTime`, `Boolean` |
| `@SqlFunction` with the same name as a Hibernate built-in function (`COALESCE`, `ROUND`, `SYSDATE`, ...) | Use a prefix or a schema name (`PUBLIC.SQ_FN`) |
| Functional interface constants as DB functions, mixins | Interface `static` method |
| Your own static methods on entity values without `@SqlFunction` | A DB function with `@SqlFunction`, or a condition from columns |
| User aggregate or window functions | Planned |
| Calling `Agg.*` outside a query lambda, or in `where` | Use it in `select` / `having` |

<a id="sort-and-paging"></a>
### Sort and paging

| Not supported | Write instead |
|---|---|
| A condition or loop in the sort lambda (`if (x) by.asc(...)`) | Build two queries, or pass the `Direction` as a captured value: `by.sort(o.getPrice(), dir)` |
| `orderBy` twice, `where` after `orderBy`, `orderBy` after `limit` | One `orderBy` with all keys (these forms do not compile) |
| An entity as a sort key (`by.desc(o.getCustomer())`) | A column: `by.desc(o.getCustomer().getName())` (does not compile) |
| Method references in join sort lambdas | A lambda |
| One text with a sort name and a direction (`"price,desc_nulls_last"`), not implemented yet | `Direction.parse(text)` for the direction and `SortSpec.ofPath` for the name. The sort policy checks the name ([4.10](04-order-paging.md#sort-policy)) |
| Sort by a column of the joined entity or by an association path in `distinct()` on a join result (J7, `LQL-3046`) | Sort by a column of the main entity itself, or use an `exists` subquery |
| Collection fetch join with `limit` / `offset` | Page first, then load the collection |
| `ignoreCase()` in a Spring `Sort` ([12.5](12-spring-data-jpa.md#125-spring-sort-and-pageable)), not supported yet | A sort without `ignoreCase()` |
| Function keys (`lower(name)`) | Planned |
| Sort by an aggregate alias (`by.desc(s.total())`) | Planned |

<a id="limits-subqueries"></a>
### Subqueries

| Not supported | Write instead |
|---|---|
| `first()` in a scalar compare that is not one row | Use an aggregate (`max`, `min`, `avg`, `sum`, `count`) or `orderBy(...).limit(1)...first()` |
| `orderBy` in a subquery without `limit` | It has no effect, so it is removed (WARN) |

<a id="select-and-group-by"></a>
### Select and group by

| Not supported | Write instead |
|---|---|
| A non-aggregate value in `select` / `having` that is not a `groupBy` key | Add it to `groupBy` |
| A plain DTO class as the row type of a derived table or CTE | A Java record |
| `Set.of(...)` as `groupBy` keys | `List.of(...)` |

<a id="limits-update-insert-delete"></a>
### Update, insert, delete

| Not supported (v0.1) | Write instead |
|---|---|
| `update` / `delete` without `where` | Add `.all()` (without it, `execute()` does not compile) |
| Reading a column after you set it in the same statement | Use the old value on purpose, or run two updates |
| Setting the same column twice | One assignment |
| Conditional values (`c ? a : b`, toggle `!o.isActive()`) | Planned (`CASE WHEN`). Use two updates with different `where` |
| `if` inside the `set` lambda to choose which column to set | Call `set()` several times |
| Setting a non-`@Id` property of an association (`o.getCustomer().setName(x)`) | Update `Customer` directly |
| `null` into a required column (`@Id`, `nullable = false`, primitive) | – |
| Writing the `@Version` property yourself | lambdaQL uses `update versioned` for you |
| String concatenation in values | Planned |
| An explicit join in update or delete | Not added (D51). Use an association path (U9) or `sub` (D49) |
| `insert ... select` with an explicit join | Planned. In v0.1, use many-to-one paths |
| An atomic `onConflict(...).ignore()` under concurrent inserts | Handle the duplicate key error (`ignore()` uses `not exists`, see [7.6](07-6-upsert.md)) |
| Upsert from `insert ... select` | Upsert row by row with `values(list, ...)` |
| `orderBy(...)` on update or delete without `limit(n)` | Add `limit(n)`, or remove `orderBy` (`IllegalStateException` otherwise) |
| An update or delete that must change one row, checked with `execute() == 1` and a rollback | `executeOne()`: it reads the ids first (at most 2). With 2 it changes nothing and throws `NonUniqueResultException`. With 1 it changes that row by `id = :p and (condition)`. It never changes more than one row (D41, tested) |
| `executeOne()` together with `orderBy` or `limit` | Use only one of them (`IllegalStateException` otherwise) |
| `offset` on update or delete | Not planned (D42). DML has only `limit(n)`. Choose the target rows with `where`, `orderBy(...)` and `limit(n)` |
| `limit` or `executeOne()` on an entity with a composite key | Not supported yet. Query the ids first, then `delete ... where ids.contains(id)` |
| DML with a Spring shared `EntityManager` and no transaction (lambdaQL has no `@Transactional`, D41) | Call it inside `@Transactional` or a `TransactionTemplate` |
| Cascade or `@PreRemove` expected from the repository method `deleteAll(where)` (a bulk delete) | Use Spring Data's `deleteAll(Iterable)` or `delete(entity)` (see [12.4](12-spring-data-jpa.md#deleteallwhere-is-not-spring-datas-deleteall)) |
| `returning` | Query again after the DML |
| DB time (`current_timestamp`) | Pass app time (`LocalDateTime.now()` is bound as a value) |
| `insert ... select` with a `TABLE` id generator | Use `values(...)`, or IDENTITY / SEQUENCE |
| `insert` into a JOINED or TABLE_PER_CLASS subclass | Not supported (Hibernate 6.6.5 fails, 7.4 not measured) |

One more note for DML: bulk DML does not go through the persistence context. Hibernate flushes first, but
managed entities in the same session keep their old values afterwards. Call `clear()` (or `refresh`).
JPA cascade and `orphanRemoval` do not apply.
A DML statement needs a transaction. `QueryBuilder(EntityManagerFactory)` opens its own transaction. A builder with an `EntityManager` (also Spring's shared `EntityManager`) uses the caller's transaction, and without one it fails before any SQL is sent.

<a id="procedures"></a>
### Procedures

| Not supported | Write instead |
|---|---|
| Anything except one stub method call in the lambda (logs, nested calls, branches) | One call. Compute the arguments before |
| `exec` for a procedure that has OUT parameters | `call` |
| DB default values for arguments | Pass every argument |
| Calling one overload of an Oracle package procedure (an overloaded name is detected: `LQL-4044`), dynamic procedure names | Planned |
| Oracle implicit results (`DBMS_SQL.RETURN_RESULT`), MariaDB packages, paging of procedure results | Not supported |

<a id="108-caching-a-lambda-is-analyzed-once"></a>
## 10.8 Caching: a lambda is analyzed once

Reading bytecode is the slow part, so lambdaQL does it **once per lambda**.

| Item | Rule |
|---|---|
| What is cached | The analysis result: an HQL template and the list of parameter sources. Failures are cached too |
| Cache key | The lambda's implementation class and method (name, signature, kind), the lambda's type, the entity classes, and the **ClassLoader** |
| Size | Limited (LRU), so it cannot grow forever and does not leak class loaders |
| Not cached | The bytecode itself (it is cheap to read again and large to keep) |
| Captured values | **Never cached.** They are read and bound each time the query runs |
| Calls on captured objects (`order.getDescription().trim()`, `this.field`, `LocalDateTime.of(...)`) | Run in Java **each time** the query runs, then bound as one parameter |
| Captured-only conditions (`p != null && ...`) | Kept as `:p1 is not null`, not folded into a constant, so the cache stays correct |
| `Direction` as a captured value | A bind value, read each time (the structure stays cached) |
| DML | Analyzed once per lambda class. Checks between `set()` calls use small summaries |

Measured with prototypes (JDK 21): the first analysis of a new lambda takes about **0.2 ms**.
After that, an update with two `set()` calls costs about **0.1 to 0.2 µs** for cache lookup, value extraction and checks.
This stays the same after 2 million runs. The goal: 1,000 calls of the same lambda read the
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

<a id="109-tested-environment"></a>
## 10.9 Tested environment

| Item | Version |
|---|---|
| Java | 21 (javac). ECJ is not guaranteed |
| Hibernate | 7.4 (tested with 7.4.13). HQL extensions are used (for example `function(...)` and `nulls first`), so other JPA providers are not guaranteed |
| Databases | H2 2.3, MariaDB 10.11, Oracle 23.26 (23ai) |
| Spring | Spring Boot 4.1.2 and Spring Data JPA 4.1.2 (the Spring module, [12](12-spring-data-jpa.md)). Spring Boot 3 is not verified in this edition |
| Not tested | PostgreSQL, SQL Server, Oracle 19c and older (for example PL/SQL `BOOLEAN` parameters) |

---

[Java track](README.md) | Previous: [9. Java notes](09-java-notes.md) | Next: [11. Errors and languages](../11-errors-and-languages.md) | [Kotlin version of this page](../kotlin/10-rules-and-limits.md)
