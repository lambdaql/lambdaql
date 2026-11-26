<a id="10-rules-and-limits-java"></a>
# 10. Rules and limits (Java)

> [!IMPORTANT]
> This page is older than the Korean manual. See the [Korean page](../../ko/java/10-rules-and-limits.md) for the latest rules.

[Java track](README.md) | [Manual home](../README.md) | Previous: [9. Java notes](09-java-notes.md) | Next: [11. Errors and languages](../11-errors-and-languages.md) | [Kotlin version of this page](../kotlin/10-rules-and-limits.md)

Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md)

---

<a id="contents"></a>
## Contents

- [10.1 The base rule: a lambda expression is a query](#101-the-base-rule-a-lambda-is-a-query)
- [10.2 SARGable rule (index-friendly SQL)](#102-sargable-rule-index-friendly-sql)
- [10.3 NULL rules](#103-null-rules)
- [10.4 Operator rules (R1 to R24)](#104-operator-rules-r1-to-r24)
- [10.5 Java and SQL differences](#105-java-and-sql-differences)
- [10.6 Database differences](#106-database-differences)
- [10.7 Not supported, and what to write instead](#107-not-supported-and-what-to-write-instead)
- [10.8 Caching: a lambda expression is analyzed once](#108-caching-a-lambda-is-analyzed-once)
- [10.9 Supported environment](#109-tested-environment)

How to read the examples:

- The string in `assertEquals` is the HQL that lambdaQL builds.
- Constants written directly in the lambda expression (numbers, strings, booleans, `null`, enum constants) are written into the HQL as literals.
  Captured values and computed values are always bind parameters (`:p1`, `:p2`, ...).
- Entities: `Order` (`id`, `price`, `status`, `active`, `description`, `customer`, `updateAt`),
  `Customer` (`id`, `name`, `grade`, `orders`), `Item` (`order`, `price`, `qty`).
  The rows of 10.3 also use the `Order` properties `cnt` (`Integer`), `custName` (`String`) and `address` (`@Embedded`, `city` and `zip`).
  In these rows, `active` is a `Boolean` that can be NULL, and its getter is `getActive()`.

---

<a id="101-the-base-rule-a-lambda-is-a-query"></a>
## 10.1 The base rule: a lambda expression is a query

lambdaQL **does not call** condition, sort and assignment lambda expressions. It analyzes their bytecode opcodes and writes HQL.
Expressions made only of captured values, and constructor calls in `select`, are computed in Java.
A lambda expression is a query written in Java syntax. So:

1. A path such as `o.getCustomer().getName()` **points to a column**. It is not a chain of calls.
   No getter is called, so there is no `NullPointerException`. A comparison on this path is true only for orders that have a customer.
   In `where`, if an order without a customer can match the condition, it is a `left join`. If it cannot, it is an inner join. The result is the same as running the lambda expression on a list.
   With a null guard (`o.getCustomer() == null || ...`), orders without a customer are also selected.
   To find such orders, compare the association itself: `o.getCustomer() == null`.
2. NULL is handled **only where you write it** (`== null`, `!= null`).
   Other NULLs follow the SQL rules. Rows where the compared value is NULL are not returned.
3. Java memory rules (object identity, the boxing cache, string interning) do not apply.
   `==` between objects means **value equality**.
4. **A lambda expression that cannot be analyzed is an error.** If lambdaQL cannot analyze it into a query, it throws an exception with the code `[LQL-xxxx]` and the source line.

<a id="102-sargable-rule-index-friendly-sql"></a>
## 10.2 SARGable rule (index-friendly SQL)

"SARGable" means that the database can use an index for the condition.
This is a base rule of lambdaQL (design section 3.9).

| # | Rule |
|---|---|
| 1 | **No functions, operators or `CASE` on the column side.** If a value must be changed, change the parameter in Java before binding (for example, to lower case with `Locale.ROOT`) |
| 2 | When two forms have the same meaning, choose the form with only simple comparisons and `AND` / `OR` / `NOT`. Correct three-valued logic comes first, and within that, the SARGable form is chosen |
| 3 | The rendering rules are based on the `EXPLAIN` results of H2, MariaDB and Oracle |

<a id="examples"></a>
### Examples

**Equality comparisons with simple `=` and `<>`**

<!-- region: ManualJavaRulesTest.java#plain-equals -->
```java
// when the query runs, text is not null
SelectQuery<Order> same = qb.from(Order.class).where(o -> o.getDescription() == text);
assertEquals("select e0 from Order e0 where e0.description = :p1", same.toHql().hql());
SelectQuery<Order> other = qb.from(Order.class).where(o -> o.getDescription() != text);
// a NULL description matches neither
assertEquals("select e0 from Order e0 where e0.description <> :p1", other.toHql().hql());
```

lambdaQL does not write `is [not] distinct from`. On Oracle this form becomes `decode(...)`, which cannot use an index.
Entity values follow SQL meaning, so a simple `=` or `<>` is always correct. A captured value `null` is bound as NULL and matches no row (`LQL-4045` at `WARN` level).

**In a comparison of a boolean column with a condition**, the condition side becomes `case when`, which cannot use an index (`LQL-5013` at `WARN` level). If you split the two cases with `&&` and `||`, it becomes `AND`/`OR`.

<!-- region: ManualJavaWhereTest.java#boolean-compare -->
```java
int s = 1;
// a boolean column compared with a condition: the condition becomes case when
SelectQuery<Order> compared = qb.from(Order.class).where(o -> o.isActive() == (o.getStatus() > s));
assertEquals("select e0 from Order e0 where e0.active = case when e0.status > :p1 then true else false end",
        compared.toHql().hql());
// the same rows with && and ||: plain comparisons that an index can serve
SelectQuery<Order> query = qb.from(Order.class)
        .where(o -> (o.isActive() && o.getStatus() > s) || (!o.isActive() && o.getStatus() <= s));
assertEquals("select e0 from Order e0 where (e0.active = true and e0.status > :p1)"
        + " or (e0.active = false and e0.status <= :p2)", query.toHql().hql());
```

The `CASE` form (`e0.active = case when ... end`) gives the correct rows but needs a full scan, so
lambdaQL does not use it.

**NOT IN with a subquery** is written as `NOT IN` (SQL meaning). If the subquery has a NULL, no rows are returned. To skip NULLs, add a null check to the subquery (see 10.3).

<a id="what-the-measurements-showed"></a>
### What the measurements showed

MariaDB 10.11 and Oracle 23ai, 20,000 to 50,000 rows:

| Form | MariaDB | Oracle | OK? |
|---|---|---|---|
| `name = 'abc'` on a `_ci` column | Index lookup | – | Yes |
| `LOWER(name) = LOWER('abc')` | Full index scan (19,791 rows) | Full table scan (no function index) | **No** |
| `lower(name) = :p_lowered` + function index | – | Index range scan (also `LIKE 'ab%'`) | Yes |
| `name COLLATE BINARY_CI = :p` + linguistic index | – | Index range scan | Yes |
| Boolean `==` written with `AND`/`OR` | range (350 rows) | 2 index range scans + bitmap OR | Yes |
| Boolean `==` written with `CASE` | Full index scan (49,648 rows) | Full table scan | **No** |

<a id="db-functions-on-a-column"></a>
### DB functions on a column

A DB function on the column side (`OrderFns.discount(o.getPrice()) > 10`) cannot use a normal index.
lambdaQL logs a **WARN** (`LQL-5001`). Exception: on Oracle, it is not logged when there is a function-based index on the same expression.
On MariaDB, a `NOT DETERMINISTIC` (the default) function causes a full scan even when its arguments are only parameters (`o.getPrice() > OrderFns.discount(rate)`), so lambdaQL also warns in this case (`LQL-5007`).
In `SargableMode.STRICT`, the analysis fails instead of warning (`LQL-3066`).

**SARGable mode**. `SargableMode` has three values.

- `OFF`: no check, and no metadata query.
- `WARN` (default): logs warnings.
- `STRICT`: in the cases where `WARN` would log a warning (`LQL-5001`, `LQL-5007`, `LQL-5012`), the analysis fails with `org.lambdaql.NonSargableException` (`LQL-3066`).

Set it per persistence unit with `SargablePolicy.register(emf, SargableMode.STRICT)`, and remove it with `SargablePolicy.unregister(emf)`. In Spring, use `lambdaql.sargable.mode=STRICT` (see [12](12-spring-data-jpa.md)).
The check runs once per lambda expression, when the analysis result is put into the shared cache. So register the mode before the first query.

**Arithmetic on a column**.

- `+ - * /` with only one column and values (`o.getCnt() * 2 > n`) can be moved to the value side. lambdaQL logs `LQL-5012` at `WARN` level, and it is an error in `STRICT`.
- Arithmetic between columns (`o.getPrice() * o.getCnt() > n`) and `%` cannot be moved, so `LQL-5013` is logged only at `DEBUG` level. It is allowed also in `STRICT`.
- The moved form must have the same meaning. Divide by a decimal, like `o.getCnt() > n / 2.0`. The integer division `n / 2` gives different rows for `>=`, `=`, `<` and a negative `n`. If you multiplied by a negative number, reverse the comparison. The `LQL-5012` message explains this.
- Reducing warnings by looking up whether an index exists was not adopted. Because of permissions and synonyms, "no index" and "unknown" get mixed, and column arithmetic is a rare form.
See [8. Procedures and DB functions](08-procedures-functions.md).

Example code: [`ManualJavaRulesTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaRulesTest.java) (`plain-equals`), [`ManualJavaWhereTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaWhereTest.java) (`boolean-compare`).

<a id="103-null-rules"></a>
## 10.3 NULL rules

| Lambda expression | SQL | NULL behavior |
|---|---|---|
| `o.getId() == 10L` (unboxing, one side is a primitive type) | `e0.id = 10` | NULL: the row is not returned |
| `o.getId() == p` (two objects: `Long`, `String`, enum, ...) | `e0.id = :p1` | Column is NULL: not selected. Captured value `null`: no row matches (`= NULL`, `LQL-4045` at `WARN` level) |
| `o.getDescription() != p` | `e0.description <> :p1` | Column is NULL: **not** selected (SQL meaning. Java would select it). Captured value `null`: no row matches |
| `o.getDescription() == o.getCustName()` (two entity values) | `e0.description = e0.custName` | Either side is NULL: not selected. Also for NULL and NULL (SQL meaning) |
| `o.getDescription().equals(p)` | `e0.description = :p1` | description is NULL: the row is not returned |
| `Objects.equals(o.getDescription(), p)`, `"x".equals(o.getDescription())`, `Boolean.TRUE.equals(o.getActive())` | Same as `==` | Same as `==` |
| `!Boolean.TRUE.equals(o.getActive())` | `e0.active <> :p1` | NULL: not selected (SQL meaning) |
| `!o.getActive()` (nullable `Boolean`) | `e0.active = false` | NULL: not selected (NULL is not false) |
| `x == null`, `x != null` | `is null`, `is not null` | Two-valued |
| `o.getCnt() >= 2.5` (integer column, decimal value) | Compared as written: constants are literals (`e0.cnt >= 2.5`), and captured values and IN lists are also decimal literals (`e0.cnt >= 2.5BD`, `in (2.5BD, 3.0BD)`) | Keeps the index range scan (measured on MariaDB). The value is no longer cut, which returned wrong rows before |
| A whole value object (`@Embedded`): `o.getAddress().equals(addr)`, `Objects.equals(o.getAddress(), addr)`, `!o.getAddress().equals(addr)` | Expanded per column: `e0.address.city = :p1 and e0.address.zip = :p2`, and the negation is `<> ... or ...`. `== null` is `is null` on all columns (and), `!= null` is `is not null` (or) | SQL meaning per column: a NULL column matches neither `=` nor `<>`. If the captured object is `null`, no row matches (`LQL-4045` at `WARN` level) |
| `o.getId() == null` on a column that cannot be NULL (id, primitive, `nullable = false`, no association step in the path) | `1=0`. Used with a comparison, the check is removed: `o.getId() != null && o.getId() == id` is `e0.id = :p1` | The column has no NULL. A path through an association (`o.getCustomer().getId()`) keeps the check |
| `!`, `&&`, `\|\|` | `NOT`, `AND`, `OR` | SQL three-valued logic |
| `o.getCustomer().getName().equals(p)` in `where` | `j1.name = :p1` + `join e0.customer j1` | Without a customer, the order is not selected |
| `o.getStatus() == 1 \|\| o.getCustomer().getName().equals(p)` | `e0.status = 1 or j1.name = :p1` + `left join e0.customer j1` | Selected when status is 1, even without a customer |
| `o.getCustomer() == null \|\| o.getCustomer().getName().equals(p)` | `e0.customer is null or j1.name = :p1` + `left join e0.customer j1` | Selected without a customer (same as Java) |
| `o.getCustomer() == null \|\| o.getCustomer().getName() == null` | `e0.customer is null or (e0.customer is not null and j1.name is null)` + `left join e0.customer j1` | `e0.customer is not null` is added to an `is null` comparison through a path |
| `o.getCustomer().getName()` used only in `orderBy` or `select` | `j1.name` + `left join e0.customer j1`. A required association (`optional = false`) uses `join` | Without a customer, the column is NULL |
| `o.getCustomer() == null`, `o.getCustomer() != null` | `e0.customer is null`, `e0.customer is not null` (FK column, no join) | Orders without a customer / orders with a customer |
| `o.getCustomer().getId()` | `e0.customer.id` (FK column, no join) | NULL without a customer |
| `ids.contains(o.getId())` | `e0.id in (:p1)` | id is NULL: not selected |
| `!ids.contains(o.getId())` | `e0.id not in (:p1)`. The same for a column that can be NULL | NULL value: **not** selected (SQL meaning. `LQL-5009` at `DEBUG` level. Java would select it) |
| A subquery with `!...contains(x)` | `x not in (select ...)` | If the subquery has a NULL, no row is selected (SQL meaning) |
| `Agg.sum`, `avg`, `min`, `max` | `sum(...)` ... | NULL when there are no rows or all values are NULL |
| `Agg.count()` | `count(*)` | 0 when there are no rows |

<a id="captured-null-values"></a>
### null captured values

| Where the captured value is | `null` allowed? |
|---|---|
| `==` / `!=` between objects, `Objects.equals`, `col.equals(p)`, `p.equals(col)` | Yes. But no row matches (`= NULL`). `LQL-4045` is logged at `WARN` level when the query runs |
| Under a null guard on the same value: `p == null \|\| o.getStatus() > p` | Yes. The guard is decided in Java when the query runs. If `p == null`, there is no filter. Otherwise it is `e0.status > :p1`. For a comparison with only an unboxing to its own primitive type, the position of the guard does not matter |
| An unboxing comparison (`o.getPrice() > p`, `Double p`) | Yes. But no row matches (`> NULL`). The unboxing to its own primitive type is not run. `LQL-4045` is logged at `DEBUG` level when the query runs |
| A method call on a captured value (`p.trim()`), an unboxing that converts to another primitive type (`p.intValue()`, `Double p`) | **No.** An error when the value is computed in Java (`LQL-4012`) |
| A `null` element in a collection used for `in` | **No.** An error at binding |
| `NaN`, `Infinity` | **No.** Always an error |

<a id="104-operator-rules-r1-to-r24"></a>
## 10.4 Operator rules (R1 to R24)

A short form of the rule table in the design (section 3.6).

| # | Lambda expression form | Meaning |
|---|---|---|
| R1 | Primitive comparison, or one side is a primitive type (`o.getId() == 10L`, `o.getStatus() > p`) | `=`, `<>`, `<`, ... SQL NULL rules |
| R2 | Object `==` / `!=` (`Long`, `String`, `Boolean`, enum) | Value equality, not object identity. `=` / `<>` with SQL meaning. A captured value `null` matches no row |
| R3 | `x == null`, `x != null` | `IS NULL`, `IS NOT NULL` |
| R4 | `a.equals(b)` | `a = b`, SQL NULL rules |
| R5 | `Objects.equals(a, b)`, `"x".equals(a)`, `Boolean.TRUE.equals(a)` | Same as R2 |
| R6 | `!`, `&&`, `\|\|` | `NOT`, `AND`, `OR`. SQL three-valued logic |
| R7 | String value comparison | Follows the **database collation** (case, trailing spaces). Oracle treats `''` as NULL |
| R8 | Entity `==` entity | Rejected (`LQL-3011`). Compare the ids |
| R9 | Captured value | A bind parameter with the value at run time |
| R10 | Property access | Getter call, mapped to a JPA property |
| R11 to R23 | Kotlin rules | See the [Kotlin version of this page](../kotlin/10-rules-and-limits.md) |
| R24 | Association path (`o.getCustomer().getName()`) | A column. To-one association: an alias shared by where, order by and select. In `where`, a `left join` if a row without the association can match the condition, otherwise an inner join (the result is the same as running the lambda expression on a list). Used only in order by or select: a `left join` (an inner join for a required association `optional = false`, A4). Embedded: the same table. Ending with the target `@Id`, or comparing the association with `null`: the FK column, no join. Collection: error |

Other rules:

- `x.compareTo(y) > 0`, `isBefore` and `isAfter` become `x > y` and so on.
- Constants written directly in the lambda expression (including compile-time `static final` constants) are literals: `o.getPrice() > 100` is `e0.price > 100.0`, and `"O'Brien"` is `'O''Brien'`. Captured values and computed values (`new BigDecimal("1.5")`) are bind parameters. Exception: a decimal captured value compared with an integer column is a decimal literal (`2.5BD`).
- javac sometimes moves `!` (De Morgan, double negation). The result does not change.
  lambdaQL keeps each condition exactly once and does not "simplify" the logic.
- Even if you write a condition that is always true in Java (`x > 5 || !(x > 5)`), it is not simplified.
  In SQL, rows where `x` is NULL are left out. This is the SQL rule.
- IntelliJ warns "Number objects compared using `==`" on `o.getId() == p`. Inside a lambdaQL lambda expression,
  this is the intended form (value equality). Declare `@SuppressWarnings("NumberEquality")` on the method to remove the warning.

For which Java types become R1 and which become R2, see [9.3](09-java-notes.md#93-equality-of-boxed-values).

<a id="105-java-and-sql-differences"></a>
## 10.5 Java and SQL differences

These differences come from SQL itself. They are not compiler problems.
The "G" numbers are IDs from the design (section 2.7).

| # | Difference | What lambdaQL does |
|---|---|---|
| G1 | When `status` is NULL, `!Objects.equals(status, 1)` is true in Java, but SQL `status <> 1` does not return that row | Rendered as `status <> 1`: SQL meaning. NULL rows are left out, and `LQL-5009` is logged at `DEBUG` level. To keep these rows, write `status == null \|\| ...` |
| G2 | `!Boolean.TRUE.equals(active)`, `!active` (nullable) and Java | Rendered as `active <> :p1`, `active = false`: a NULL Boolean is not false (SQL meaning) |
| G3 | `name = ?` with a NULL parameter returns 0 rows | Kept as it is (SQL meaning). `LQL-4045` is logged at `WARN` level at run time. To find NULL, write `x == null` |
| G4 | `status NOT IN (1, NULL)` always returns 0 rows | `null` in a captured collection is an error. A subquery `NOT IN` keeps the SQL meaning: add a null check to the subquery |
| G5 | A column value of the entity is NULL | Documented: that row is not returned, by the SQL rules |
| G6 | `NaN`: H2 sorts NaN as the largest value and `NaN = NaN` is true, MariaDB cannot store it, Oracle can store it only in `BINARY_DOUBLE` | A captured NaN or Infinity is an error. A stored NaN follows the database |
| G7 | Comparing a `float` column with a `double` value: the result differs by database | For float/double, **results the same as Java down to the bit are not promised**. lambdaQL does not add `CAST`. Choose the column mapping yourself (for example, Oracle `binary_float` / `binary_double`) |
| G9 | `x > 5 \|\| !(x > 5)` leaves out NULL rows | Rendered as written, without simplification |
| G10 | SQL does not promise an evaluation order: even `qty != 0 && total / qty > 2` can divide by zero | Rendered as written. Do not rely on the guard; compare without division ([3.12](03-where.md#312-errors-that-depend-on-order-of-evaluation)) |
| G11 | Strings: MariaDB `_ci` ignores case and trailing spaces, Oracle stores `''` as NULL, Oracle `NLS_SORT` follows the JVM locale | Documented: string comparison and sorting follow the database. On Oracle, set `NLS_SORT=BINARY` (session initialization or `-Duser.language=en`). On Oracle, `"".equals(x)` and `isEmpty()` behave differently |
| G12 | Java `==` on objects is identity (false for `Long` 128 or more, and `new String`) | lambdaQL defines `==` as value equality |
| G13 | Legacy boolean columns with values other than 0/1 (MariaDB `TINYINT(1)`, Oracle `NUMBER(1)`) are read differently | DDL built by Hibernate (`bit(1)`, Oracle 23 `boolean`) is fine. Take care with legacy schemas |
| G14 | Oracle 23 bug: `WHERE (a AND b) = false` / `IS FALSE` / `IS NULL` on a compound condition loses rows | lambdaQL does not wrap compound conditions in boolean tests. Negation is always `NOT(...)` |
| G15 | An Oracle `CHAR` column does not match a `String` bind | Documented: take care with the entity mapping |

More differences:

| Difference | Java | SQL | lambdaQL |
|---|---|---|---|
| `BigDecimal` equality | `equals` compares the scale (`1.0` and `1.00` is false) | `=` compares the value | Value equality |
| Integer division | `5 / 2` is `2` | MariaDB gives `2.5` | Depends on the database. Avoid integer `/` with a column |
| `BigDecimal` division and function results | Java scale rules | The database sets the scale (for example, for function results H2 gives 1, MariaDB and Oracle give 0) | Documented |
| Unicode case-insensitivity (`ſ`, `İ`) | Java rules | Database collation | Follows the database |
| String length | UTF-16 units | H2 uses UTF-16 units, MariaDB uses code points | Documented |
| String concatenation with NULL | The text `"null"` | NULL (Oracle: `NULL \|\| 'x'` is `'x'`) | String concatenation in a lambda expression is rejected (`LQL-2007`) |
| `LocalDateTime.now()` in a lambda expression | App time | – | Bound as **app time**. Database time (`current_timestamp`) is not supported |
| Equal elements of a `Set` | Decided by `equals` / `hashCode` | Rows are told apart by the primary key | If the Set is smaller than the number of rows, `toSet()` and `findSet()` throw `IllegalStateException` (when the entity's `equals` / `hashCode` treats different rows as equal, or the `@Id` is not unique, for example a view). Use `toList()` |

<a id="106-database-differences"></a>
## 10.6 Database differences

lambdaQL hides many differences. Some you need to know.

<a id="sorting-and-paging"></a>
### Sorting and paging

| Item | H2 | MariaDB | Oracle | lambdaQL |
|---|---|---|---|---|
| NULL position, `ASC` | First | First | **Last** | `ASC` is NULLS FIRST on all databases |
| NULL position, `DESC` | Last | Last | **First** | `DESC` is NULLS LAST on all databases |
| `NULLS FIRST/LAST` syntax | Yes | **No** (syntax error) | Yes | Hibernate emulates it with `CASE` on MariaDB: the sort **cannot use an index** |
| Paging SQL | `offset ? rows fetch first ? rows only` | `limit ?,?` | `offset ? rows fetch first ? rows only` | Passed to Hibernate (`setFirstResult` / `setMaxResults`) |
| Order of rows with the same value | – | – | Random | When paging and the keys have no id, lambdaQL adds the root `@Id asc`. Not added when there is a unique key that cannot be NULL (`unique = true` and `nullable = false`, `@NaturalId`) |
| String order | Case-sensitive | `_ci` | `NLS_SORT` (JVM locale) | Follows the database |
| Implicit path in `order by` | Inner join (loses rows) | Same | Same | lambdaQL uses a `left join` (an inner join for a required association, or the `where` join of the same path) |
| `@Lob` column in `order by`, `distinct`, `group by` | Works | Works | **ORA-22848 / ORA-22849** | Does not change the query and logs `LQL-5010` at `DEBUG` level |
| Sorting by an enum column with `@Enumerated(EnumType.STRING)` | By name | By name | By name | Does not change the query and logs `LQL-5011` at `DEBUG` level. Declaration order only with the `ORDINAL` mapping |

lambdaQL writes `nulls first` / `nulls last` in the HQL for every key that can be NULL. It does not write it for a path that goes only through required associations and cannot be NULL. Hibernate leaves it out when it is the same as the database default.
The defaults (`ASC` = NULLS FIRST, `DESC` = NULLS LAST) are the same as on H2 and MariaDB, so no extra SQL is
needed there. Only Oracle gets a `NULLS` clause. To change the NULL position, use
`asc(key, Nulls.LAST)` (NULL last) or `desc(key, Nulls.FIRST)` (NULL first). On MariaDB, these use the `CASE` emulation.

<a id="conditions-and-values"></a>
### Conditions and values

| Item | H2 | MariaDB | Oracle |
|---|---|---|---|
| `is not distinct from` | Native | `<=>` (uses an index) | `decode(...)` (no index). lambdaQL does not use this form |
| `is [not] true`, `coalesce(boolean, false)` | Works | Works | **ORA-00932**. lambdaQL does not use them |
| Empty string `''` | A value | A value | **NULL** |
| Case and trailing spaces | Exact match | `_ci`: ignored | Exact match (sorting with `NLS_SORT`) |
| `NaN` | Stored, the largest value | Cannot be stored | Only `BINARY_DOUBLE` |
| `float` column | REAL literal rules | Widened to double | Hibernate maps `Float` to `float(24)` (decimal) |
| Integer `/` | Integer | **Decimal** (`5/2 = 2.5`) | – |
| Large `IN` list (1,500 values) | Works | Works | Works on 23ai (the 1,000 limit of Oracle 22 and earlier was not measured) |

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
| `SET a = ..., b = a + 1` (reading a column after setting it) | MariaDB reads the **new** value, H2 and Oracle read the old value | Error at analysis time |
| Association paths in DML (`o.getCustomer().getName()`) | Hibernate builds different joins for each database and **loses rows** without a customer | lambdaQL writes correlated scalar subqueries (`where` and SET). In `where`, it also adds `customer.id is not null` for a path that a select would inner join (not for a path it would `left join`). So DML applies to the same rows as the query |
| `DELETE` with a subquery on the same table | MariaDB error 1093 | Reads the target ids first through a derived table: `d0.id in (select t.id from (select e0.id as id … where <condition>) t)`. It also works for correlated subqueries and deletes the same rows on three databases (measured). `update` is not wrapped (it works on MariaDB too) |
| Binding the same enum value only once for a STRING column and an ORDINAL column | H2 and MariaDB store a **wrong value without an error** | One parameter per position, never shared |
| Many rows in `values(list, ...)` | Oracle: a fixed limit of 65,535 binds (ORA-01745), and very slow long before that | Split into 500 values or fewer per statement, and all statements run in one transaction |
| `update` / `delete` with `order by ... limit` | MariaDB rejects `limit` in an `in` subquery (error 1235) and a subquery that reads the target table (error 1093) | `e0.id in (select t.id from (select x0.id as id ... order by ... limit n) t)`: the derived table `t` makes it work on all three databases. The inner select uses the joins of select, so it targets the same rows as select |
| `on conflict` / upsert | H2 and Oracle use `merge`. MariaDB uses `insert ... on duplicate key update` and counts an updated row as 2. Hibernate's `do nothing` fails on H2 and Oracle | `upsert` and `onConflict(...).update(...)` use Hibernate's form. `ignore()` is written as `insert ... select ... where not exists`. The row count is returned as it is |

<a id="procedures-and-functions"></a>
### Procedures and functions

| Item | H2 | MariaDB | Oracle |
|---|---|---|---|
| OUT / INOUT parameters | **No** | Yes | Yes |
| Multiple result sets | No | Yes | Yes (REF CURSOR) |
| Affected row count of a procedure | – | – | Always -1. So lambdaQL does not provide it. Use an OUT parameter |
| Boolean parameters and results | – | Yes | 23ai or later |
| Stored functions in virtual columns / function indexes | – | Not possible (error 1901) | Possible (function-based index) |

H2 has no OUT parameters, so check procedure features on MariaDB or Oracle.

<a id="107-not-supported-and-what-to-write-instead"></a>
## 10.7 Not supported, and what to write instead

For most forms in this list, lambdaQL throws a clear error.

<a id="lambda-form-and-loading"></a>
### Lambda expression form and loading

| Not supported | Write this instead |
|---|---|
| Anonymous classes, functional interfaces that are not `Serializable` (`java.util.function.Predicate`) | A lambda expression literal (getter method references are also possible for sort keys) |
| A method reference as a condition (`where(Order::isActive)`, `LQL-1009`) | A lambda expression: `where(o -> o.isActive())` |
| Block lambda expressions with local variables, loops, `switch` or `try` | One expression. Compute values outside the lambda expression |
| Writing to captured variables | Compute outside the lambda expression (Java also requires captured local variables to be effectively final) |
| Lambda expressions compiled with ECJ (the Eclipse compiler) | Not guaranteed. Use javac |
| On Java 21 to 23, lambda expressions compiled for a newer JDK that the bundled ASM does not know | Update ASM (lambdaQL does not shade ASM). Not relevant when running on Java 24 or later (`java.lang.classfile` is used) |

<a id="conditions"></a>
### Conditions

| Not supported | Write this instead |
|---|---|
| Boolean ternary operator `c ? x : y` | `&&` / `\|\|`. A ternary operator that chooses a value is supported: a bind parameter with only captured values and constants, and `case when` with entity values ([3.5.1](03-where.md#351-values-chosen-by-a-condition)) |
| (Supported: `c ? x : false`, `c ? true : x`, `c ? false : true`) | They mean `AND`, `OR` and `NOT` |
| `&`, `\|`, `^`, which do not short-circuit | `&&`, `\|\|` |
| Entity `==` entity (`o.getCustomer() == c`) | `o.getCustomer().getId() == c.getId()` (`LQL-3011`) |
| A size comparison of value objects (`@Embedded`), a comparison with another type (`LQL-3012`) | Compare the fields. Equality comparison with the same type (`equals`, `==`, `!=`, `null`) is supported |
| Collection paths (`c.getOrders()...`, `@OneToMany`, `@ManyToMany`, `LQL-3010`) | An `exists()` subquery or a join |
| `c.getOrders().contains(x)` | An `exists()` subquery |
| A collection built inside the lambda expression (`List.of(1, 2)`) | Build it outside and capture it. (`groupBy` keys are the exception) |
| `null` elements in a captured collection | Remove the nulls. If you want NULL rows, add `\|\| x == null` |
| Captured `NaN` / `Infinity` | Check the value before the query |
| `isNaN()` | – |
| Other method calls on entity values (`o.items(...)`, your own methods) | Write the condition with columns |
| String concatenation (`o.getDescription() + "_x"`, also `"A" + code`) | Build the string outside the lambda expression (`LQL-2007`) |
| Streams and other lambda expressions inside the lambda expression | `contains` on a captured collection, or a subquery |

<a id="functions-and-strings"></a>
### Functions and strings

| Not supported | Write this instead |
|---|---|
| Java `String` methods on entity values (`trim`, `toUpperCase` and others, `LQL-3008`). `startsWith`, `endsWith` and `contains` are supported as `like` | – |
| Date functions (`YEAR(col) == 2024`) | A range: `o.getUpdateAt() >= from && o.getUpdateAt() < to` |
| `@SqlFunction` that returns a generic type (`<X> X nvl(X, X)`), an entity, a collection or `void` | One of: `Double`, `Integer`, `Long`, `BigDecimal`, `String`, `LocalDate`, `LocalDateTime`, `Boolean` |
| `@SqlFunction` with the same name as a Hibernate built-in function (`COALESCE`, `ROUND`, `SYSDATE`, ...) | Use a prefix or a schema name (`PUBLIC.SQ_FN`) |
| Functional interface constants or mixins used as DB functions | Interface `static` methods |
| Your own static methods on entity values without `@SqlFunction` | DB functions with `@SqlFunction`, or conditions built from columns |
| User aggregate functions or window functions | – |
| Calling `Agg.*` outside a query lambda expression or in `where` | Use it in `select` / `having` |
| Calling a sort function of `By` outside a sort lambda expression (`IllegalStateException`, `LQL-4046`) | Use it inside the lambda expression of `orderBy`, `SortSpec.of` or `then` |

<a id="sort-and-paging"></a>
### Sorting and paging

| Not supported | Write this instead |
|---|---|
| Conditions or loops in a sort lambda expression (`if (x) asc(...)`) | Build two queries, or pass `Direction` as a captured value: `sort(o.getPrice(), dir)` |
| A `Direction` argument used with sort functions (`orderBy(o -> desc(o.getPrice()), dir)`, `LQL-3069`) | `orderBy(o -> sort(o.getPrice(), dir))` or one key: `orderBy(o -> o.getPrice(), dir)` |
| One string with both a sort name and a direction (`"price,desc_nulls_last"`) | `Direction.parse(text)` for the direction, `SortSpec.ofPath` for the name. The sort policy checks the name ([4.10](04-order-paging.md#sort-policy)) |
| Sorting by a column of a joined entity or an association path with `distinct()` on a join result (`LQL-3046`) | Sort by a column of the main entity itself, or use an `exists` subquery |
| A collection fetch join with `limit` / `offset` | Page first, then load the collection |
| `ignoreCase()` of Spring `Sort` ([12.5](12-spring-data-jpa.md#125-spring-sort-and-pageable)) | A sort without `ignoreCase()` |
| Function keys (`lower(name)`) | – |
| Sorting by an aggregate alias (`desc(s.total())`) | – |

<a id="limits-subqueries"></a>
### Subqueries

| Not supported | Write this instead |
|---|---|
| `first()` in a scalar comparison that is not one row | Use an aggregate (`max`, `min`, `avg`, `sum`, `count`) or `orderBy(...).limit(1)...first()` |
| `orderBy` in a subquery without `limit` | Removed because it has no effect (WARN) |

<a id="select-and-group-by"></a>
### Select and group by

| Not supported | Write this instead |
|---|---|
| A non-aggregate value in `select` / `having` that is not a `groupBy` key | Add it to `groupBy` |
| A normal DTO class as the row type of a derived table or CTE | A Java record |
| `Set.of(...)` as `groupBy` keys | `List.of(...)` |

<a id="limits-update-insert-delete"></a>
### Update, insert, delete

| Not supported | Write this instead |
|---|---|
| `update` / `delete` without `where` | Add `.all()` |
| Reading a column after setting it in the same statement | Use the old value on purpose, or run two updates |
| Setting the same column twice | One assignment |
| Conditional values (`c ? a : b`, the toggle `!o.isActive()`) | Use two updates with different `where` |
| An `if` in a `set` lambda expression that chooses which column to set | Call `set()` many times |
| Setting a property of an association that is not its `@Id` (`o.getCustomer().setName(x)`) | Update `Customer` directly |
| Putting `null` into a required column (`@Id`, `nullable = false`, primitive) | – |
| Writing the `@Version` property directly | lambdaQL uses `update versioned` instead |
| String concatenation in values | – |
| Explicit joins in update or delete | Will not be added. Use association paths or `sub` |
| `insert ... select` with explicit joins | Use many-to-one paths |
| `onConflict(...).ignore()` that is atomic for concurrent inserts | Handle the duplicate key error (`ignore()` uses `not exists`. See [7.6](07-6-upsert.md)) |
| Upsert with `insert ... select` | Upsert row by row with `values(list, ...)` |
| `orderBy(...)` on an update or delete without `limit(n)` | Add `limit(n)` or remove `orderBy` (otherwise `IllegalStateException`) |
| Checking an update or delete that must change one row with `execute() == 1` and a rollback | `executeOne()`: reads at most 2 ids first. With 2, it changes nothing and throws `NonUniqueResultException`. With 1, it changes that row with `id = :p and (condition)`. It never changes two or more rows |
| `executeOne()` together with `orderBy` or `limit` | Use only one of them (otherwise `IllegalStateException`) |
| `offset` on update or delete | Not planned. DML has only `limit(n)`. Choose the target rows with `where`, `orderBy(...)` and `limit(n)` |
| `limit` or `executeOne()` on an entity with a composite key | Select the ids first, then `delete ... where ids.contains(id)` |
| Running DML without a transaction with Spring's shared `EntityManager` (lambdaQL has no `@Transactional`) | Call it inside `@Transactional` or `TransactionTemplate` |
| Expecting cascade or `@PreRemove` from the repository method `deleteAll(where)` (bulk delete) | Use Spring Data's `deleteAll(Iterable)` or `delete(entity)` (see [12.4](12-spring-data-jpa.md#deleteallwhere-is-not-spring-datas-deleteall)) |
| `returning` | Select again after the DML |
| Database time (`current_timestamp`) | Pass the app time (`LocalDateTime.now()` is bound as a value) |
| `insert ... select` with a `TABLE` id generator | Use `values(...)` or IDENTITY / SEQUENCE |
| `insert` into JOINED or TABLE_PER_CLASS subclasses | Not supported (Hibernate 6.6.5 fails; 7.4 not measured) |

One more point about DML: bulk DML does not go through the persistence context. Hibernate flushes first, but
managed entities in the same session keep their old values after that. Call `clear()` (or `refresh`).
JPA cascade and `orphanRemoval` do not apply.
DML statements need a transaction. `QueryBuilder(EntityManagerFactory)` opens its own transaction. Builders that use an `EntityManager` (including Spring's shared `EntityManager`) use the caller's transaction, and without a transaction they fail before any SQL is sent.

<a id="procedures"></a>
### Procedures

| Not supported | Write this instead |
|---|---|
| Anything other than one stub method call in the lambda expression (logs, nested calls, branches) | One call. Compute the arguments beforehand |
| `exec` on a procedure with OUT parameters | `call` |
| Database default values of arguments | Pass all arguments |
| Choosing and calling one overload of an Oracle package procedure (names with overloads are detected: `LQL-4044`), dynamic procedure names | – |
| Oracle implicit results (`DBMS_SQL.RETURN_RESULT`), MariaDB packages, paging of procedure results | Not supported |

<a id="108-caching-a-lambda-is-analyzed-once"></a>
## 10.8 Caching: a lambda expression is analyzed once

Reading bytecode opcodes is the slow part, so lambdaQL does it **once per lambda expression**.

| Item | Rule |
|---|---|
| What is cached | The analysis result: the HQL template and the list of parameter sources. Failures are cached too |
| Cache key | The implementation class and method of the lambda expression (name, signature, kind), the type of the lambda expression, the entity class, and the **ClassLoader** |
| Size | Limited (LRU). So it does not grow without end and does not leak class loaders |
| What is not cached | The bytecode opcodes themselves (reading them again is cheap, and keeping them is large) |
| Captured values | **Not cached.** Read and bound each time the query runs |
| Calls on captured objects (`order.getDescription().trim()`, `this.field`, `LocalDateTime.of(...)`) | Run in Java **each time** the query runs, then bound as one parameter |
| Conditions with only captured values (`p != null && ...`) | Not folded into a constant; kept as `:p1 is not null`. So the cache stays correct |
| `Direction` as a captured value | A bind value, read each time (the structure stays cached) |
| DML | Analyzed once per lambda expression class. Checks between `set()` calls use small summaries |

Measured with a prototype (JDK 21): the first analysis of a new lambda expression takes about **0.2 ms**.
After that, an update with two `set()` calls costs about **0.1 to 0.2 µs** for the cache lookup, value extraction and checks.
This is the same even after 2 million runs. Goal: even if the same lambda expression is called 1,000 times, the bytecode opcodes are
read only once. Hibernate also caches query plans by HQL string.

What this means for you:

<!-- region: ManualJavaRulesTest.java#cache-shape -->
```java
// good: one lambda; only the value changes
int status = 1;
SelectQuery<Order> one = qb.from(Order.class).where(o -> o.getStatus() == status);
assertEquals("select e0 from Order e0 where e0.status = :p1", one.toHql().hql());
// rejected: the shape of the query changes inside the lambda
assertEquals("LQL-2021", assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(o -> flag ? o.getStatus() == 1 : o.isActive())).code());
// instead: choose the lambda outside (two lambdas, each analyzed once)
List<Order> list = flag
        ? qb.from(Order.class).where(o -> o.getStatus() == 1).toList()
        : qb.from(Order.class).where(o -> o.isActive()).toList();
```

Example code: [`ManualJavaRulesTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaRulesTest.java) (`cache-shape`).

<a id="109-tested-environment"></a>
## 10.9 Supported environment

| Item | Version |
|---|---|
| Java | 21 (javac). ECJ is not guaranteed |
| Hibernate | 6.6 or 7.4. lambdaQL uses HQL extensions (for example `function(...)`, `nulls first`), so other JPA implementations are not guaranteed |
| Databases | H2 2.3, MariaDB 10.11, MySQL 8.4, PostgreSQL 17, Oracle 23.26 (23ai) |
| Spring | Spring Boot 3.5 (Spring Data JPA 3.5) and 4.1 (Spring Data JPA 4.1) (Spring module, [12](12-spring-data-jpa.md), [13](../13-spring-boot-versions.md)) |
| Not checked | SQL Server, Oracle 19c or earlier (for example, PL/SQL `BOOLEAN` parameters) |

---

[Java track](README.md) | Previous: [9. Java notes](09-java-notes.md) | Next: [11. Errors and languages](../11-errors-and-languages.md) | [Kotlin version of this page](../kotlin/10-rules-and-limits.md)
