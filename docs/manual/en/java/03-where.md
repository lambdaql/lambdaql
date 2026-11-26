<a id="3-conditions-where-java"></a>
# 3. Conditions (`where`) (Java)

> [!IMPORTANT]
> This page is older than the Korean manual. See the [Korean page](../../ko/java/03-where.md) for the latest rules.

[Java track](README.md) | [Manual home](../README.md) | Previous: [2. Getting started](02-getting-started.md) | Next: [4. Sorting and paging](04-order-paging.md) | [Kotlin version of this page](../kotlin/03-where.md)

Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md)

---

<a id="31-the-idea-in-one-sentence"></a>
## 3.1 Overview

**A lambdaQL lambda expression is a query expression, not code.** lambdaQL does not call the condition lambda expression. It analyzes the bytecode opcodes of the lambda expression.
Only expressions made of captured values alone are computed first in Java (see [3.11](#311-captured-values)).
So the run-time rules of Java (object identity, the boxing cache) do not apply to the parts that use the entity.
lambdaQL uses its own rules. This chapter explains them.

If lambdaQL cannot analyze a lambda expression into a query, it throws an exception with a code (see [3.13](#313-what-is-rejected)).

<a id="32-example-entities"></a>
## 3.2 Example entities

The examples use the example entities `Order`, `Customer` and `Item`. The property list is in [Example entities in 2. Getting started](02-getting-started.md#example-entities).

These properties are used often in this chapter:

- `Order.price` (`double`) and `Order.status` (`short`) are primitive types, so they cannot be NULL.
- `Order.cnt` (`Integer`), `Order.total` (`Double`) and `Order.description` (`String`) can be NULL.
- `Order.customer` is a `@ManyToOne` association to `Customer`, and `Order.kind` is the enum `Order.Kind` (`NORMAL`, `RUSH`).

<a id="33-basic-form"></a>
## 3.3 Basic form

<!-- region: SelectQueryTest.java#to-list -->
```java
QueryBuilder qb = new QueryBuilder(emf);
List<Order> orders = qb.from(Order.class)
        .where(o -> o.getStatus() == 1 && o.getPrice() > 100)
        .toList();
```

**SQL by database**

<!-- sql: to-hql -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>100.0
```

The examples in this chapter return the same rows on H2, MariaDB and Oracle as the same filter applied in Java.

`toHql()` returns the HQL and the parameters without running the query. The string in `assertEquals` is the HQL that lambdaQL builds.
<!-- region: SelectQueryTest.java#to-hql -->
```java
RenderedQuery q = qb.from(Order.class)
        .where(o -> o.getStatus() == 1 && o.getPrice() > 100)
        .toHql();
// constants written in the lambda are literals
assertEquals("select e0 from Order e0 where e0.status = 1 and e0.price > 100.0", q.hql());
assertEquals(Map.of(), q.parameters());

double minPrice = 100;
RenderedQuery q2 = qb.from(Order.class)
        .where(o -> o.getStatus() == 1 && o.getPrice() > minPrice)
        .toHql();
// captured values are bind parameters
assertEquals("select e0 from Order e0 where e0.status = 1 and e0.price > :p1", q2.hql());
assertEquals(Map.of("p1", 100.0), q2.parameters());
```

**SQL by database**

<!-- sql: to-hql -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>100.0
```

**SQL by database**

<!-- sql: to-hql#2 -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>?
```

You can call `where()` more than once. The conditions are combined with `AND`.
<!-- region: SelectQueryTest.java#where-many -->
```java
RenderedQuery q = qb.from(Order.class)
        .where(o -> o.getStatus() == 1 || o.getStatus() == 3)
        .where(o -> o.isActive())
        .toHql();
assertEquals("select e0 from Order e0 where (e0.status = 1 or e0.status = 3) and e0.active = true", q.hql());
```

**SQL by database**

<!-- sql: where-many -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where (o1_0.status=1 or o1_0.status=3) and o1_0.active=true
```

You cannot use a method reference in `where`. `where(Order::isActive)` is rejected with `LQL-1009`, so write `where(o -> o.isActive())` (see [9.9](09-java-notes.md#99-method-references)).

**Values in the HQL.** The rule depends on where the value comes from.

| Value | In the HQL | Example |
|---|---|---|
| A constant written directly in the lambda expression: numbers (also decimals), strings, `true`/`false`, `null`, enum constants, compile-time `static final` constants | **Literal** | `o.getPrice() > 100` → `e0.price > 100.0` |
| A captured value: local variables, fields, method parameters | **Bind parameter** | `o.getPrice() > minPrice` → `e0.price > :p1` |
| A computed value: `new BigDecimal("1.5")`, `form.getText().trim()`, `LocalDateTime.of(...)` | **Bind parameter** | `:p1` |

- A constant is the same on every call. So the SQL statement does not change, and the database can see the value when it plans the query.
- Single quotes in string constants are escaped: `"O'Brien"` becomes `'O''Brien'`. This string is a constant compiled into the class, not user input, so there is no risk of SQL injection.
- A captured value can come from a user, so it is a bind parameter. Only the value changes on each call.
- There is one exception. A decimal captured value compared with an integer column is written as a decimal literal (`2.5BD`) (see "Integer columns and decimal values" in [3.4](#34-comparisons)). Only the value converted to a number is written, so there is no risk of SQL injection.

Enum constants are written with the full class name.
<!-- region: SelectQueryTest.java#enum -->
```java
RenderedQuery q = qb.from(Order.class).where(o -> o.getKind() == Order.Kind.RUSH).toHql();
assertEquals("select e0 from Order e0 where e0.kind = org.lambdaql.query.Order$Kind.RUSH", q.hql());
```

**SQL by database**

<!-- sql: enum -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.kind='RUSH'
```

Example code: [`SelectQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java) (`to-list`, `to-hql`, `where-many`, `enum`).

<a id="34-comparisons"></a>
## 3.4 Comparisons

| Java | HQL | Notes |
|---|---|---|
| `o.getStatus() == 1` | `e0.status = 1` | |
| `o.getStatus() != 1` | `e0.status <> 1` | |
| `o.getPrice() > p` | `e0.price > :p1` | The same for `<`, `<=`, `>=` |
| `o.getUpdateAt().compareTo(since) > 0` | `e0.updateAt > :p1` | Dates and times are also compared with `compareTo`. `isAfter` and `isBefore` are not supported (`LQL-3008`) |
| `o.getDescription().compareTo("m") < 0` | `e0.description < 'm'` | `compareTo(..) op 0` becomes `op` |

A comparison with a number (one side is a primitive type, like `== 1` or `> 100.0`) follows the **SQL meaning**.
Rows where the column is NULL are not returned.

You can use simple arithmetic with `+`, `-`, `*` and `/`.
Avoid integer `/` with a column. It is computed in SQL, so the result is different on each database (MariaDB gives `5/2 = 2.5`).
A computation between captured values (`n / 2.0` below) is done in Java before the query, so it does not have this difference.

In the examples below, `cnt` is an integer column of `Order` (`Integer cnt`).

**Arithmetic on a column and indexes**. When you put arithmetic on a column, like `o.getCnt() * 2 > n`, an index cannot be used. lambdaQL logs `LQL-5012` at `WARN` level, and in `SargableMode.STRICT` the analysis fails (`LQL-3066`).
When you move it to the value side, divide by a decimal, like `o.getCnt() > n / 2.0`. The integer division `n / 2` gives different rows. If you multiplied by a negative number, reverse the comparison. For details, see [10.2](10-rules-and-limits.md#db-functions-on-a-column).

**Integer columns and decimal values**. When you compare an integer column with a decimal value, lambdaQL compares the value as written. It does not round the value or leave it out.

- A constant written in the lambda expression stays a literal. `o.getCnt() >= 2.5` becomes `e0.cnt >= 2.5`.
- A captured value is also written as a decimal literal. It becomes `e0.cnt >= 2.5BD`, with no bind parameter. `BD` is the HQL suffix for a `BigDecimal` literal.
- An IN list that has such an element writes all elements as decimal literals (`e0.cnt in (2.5BD, 3.0BD)`).

This is an exception to the principle that captured values are sent as bind parameters. Hibernate 7.4 rejects a decimal bound to a parameter compared with an integer column, even when the `BigDecimal` type is given (`QueryArgumentException`, measured).

Before, the value was cut to the column type, so `2.5` became `2` and wrong rows were returned. Hibernate 6.6 rejects a decimal bound with the integer column type (`CoercionException`), and silently cuts the elements of an IN list (measured on three databases).
Changing it into an integer comparison (`>= 3`) was not adopted. The SQL that runs would be different from the lambda expression, and it gives wrong rows when the entity mapping (`Integer`) and the database column type (for example, a `DECIMAL` with decimals) are different.

The results are correct on H2, MariaDB 10.11 and Oracle 23.26 (measured). On all three databases, the SQL keeps the column as it is and compares it with a decimal, like `cnt>=2.5` and `cnt in (2.5,3.0)`.
MariaDB's `EXPLAIN` shows an index range scan (`type=range`). On Oracle, no conversion is put on the column, so the form can use an index. With an index hint it is an `INDEX RANGE SCAN`. On a 3,000-row table without a hint, Oracle chooses a full scan, the same as for an integer literal (`cnt >= 99`) (all measured).

<!-- region: SqlMeaningTest.java#whole-number -->
```java
// cnt is a whole number; 2.5 is compared as written (it is never cut to 2): a decimal literal
double half = 2.5;
RenderedQuery atLeast = qb.from(Order.class).where(o -> o.getCnt() >= half).toHql();
assertEquals("select e0 from Order e0 where e0.cnt >= 2.5BD", atLeast.hql());
assertEquals(Map.of(), atLeast.parameters());                                // written as a decimal literal
// a constant written in the lambda stays a literal
assertEquals("select e0 from Order e0 where e0.cnt >= 2.5", qb.from(Order.class).where(o -> o.getCnt() >= 2.5).toHql().hql());
// an IN list with such a value is written as decimal literals
List<Double> values = List.of(2.5, 3.0);
RenderedQuery in = qb.from(Order.class).where(o -> values.contains(o.getCnt().doubleValue())).toHql();
assertEquals("select e0 from Order e0 where e0.cnt in (2.5BD, 3.0BD)", in.hql());
```

**Floating point:** for `float`/`double` columns, lambdaQL does not promise results that are the same as Java down to the bit.
A captured `NaN` or `Infinity` value is rejected when the query runs.

Example code: [`SqlMeaningTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlMeaningTest.java) (`whole-number`).

<a id="35-and-or-not"></a>
## 3.5 AND, OR, NOT

| Java | HQL |
|---|---|
| `a && b` | `a and b` |
| `a \|\| b` | `a or b` |
| `!a` | `not (a)` |
| Parentheses | Kept as needed |

<!-- region: ManualJavaWhereTest.java#and-or-not -->
```java
double minPrice = 100;
SelectQuery<Order> both = qb.from(Order.class)
        .where(o -> (o.getStatus() == 1 || o.isActive()) && o.getPrice() > minPrice);
assertEquals("select e0 from Order e0 where (e0.status = 1 or e0.active = true) and e0.price > :p1",
        both.toHql().hql());
SelectQuery<Order> not = qb.from(Order.class)
        .where(o -> !(o.getStatus() == 1 && o.getPrice() > minPrice));
// the NOT is pushed into the comparisons: the same rows (status and price are never NULL)
assertEquals("select e0 from Order e0 where e0.status <> 1 or e0.price <= :p1", not.toHql().hql());
```

Rules:

- The result follows **SQL three-valued logic** (TRUE / FALSE / UNKNOWN). lambdaQL checked that this returns the same rows on H2, MariaDB and Oracle.
- javac and lambdaQL can move `!` (De Morgan, `!(x > y)` to `x <= y`). `!(... && ...)` in the example above becomes `e0.status <> 1 or e0.price <= :p1`. The two columns cannot be NULL, so the result is the same as `not (...)`.
- lambdaQL **does not simplify conditions**. If you write `o.getPrice() > p || o.getPrice() <= p`, it is sent as written. Rows where `price` is NULL are not returned. This is normal SQL behavior.
- Use `&&` and `||`. The operators `&`, `|` and `^`, which do not short-circuit, are rejected.

**Boolean ternary operators.** Only three short forms are allowed. They are the same as `AND`, `OR` and `NOT`.

| Java | Same meaning |
|---|---|
| `c ? x : false` | `c && x` |
| `c ? true : x` | `c \|\| x` |
| `c ? false : true` | `!c` |

A ternary operator that chooses a value only from captured values and constants (`o.getStatus() == (flag ? 1 : 2)`) is computed in Java when the query runs and passed as a bind parameter.
A ternary operator that chooses a value by entity values (`o.getStatus() == (o.isActive() ? 1 : 2)`) becomes an SQL `case when … end` (see [3.5.1](#351-values-chosen-by-a-condition)).
Other boolean ternary operators (`c ? x : y`) are rejected (`LQL-2021`).

<!-- region: ManualJavaWhereTest.java#ternary-outside -->
```java
// a ternary of captured values and constants: computed in Java, bound as :p1
SelectQuery<Order> query = qb.from(Order.class).where(o -> o.getStatus() == (flag ? 1 : 2));
assertEquals("select e0 from Order e0 where e0.status = :p1", query.toHql().hql());
// a ternary that reads the entity becomes case when
SelectQuery<Order> byEntity = qb.from(Order.class).where(o -> o.getStatus() == (o.isActive() ? 1 : 2));
assertEquals("select e0 from Order e0 where e0.status = case when e0.active = true then 1 else 2 end",
        byEntity.toHql().hql());
```

Example code: [`ManualJavaWhereTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaWhereTest.java) (`and-or-not`, `ternary-outside`).

<a id="351-values-chosen-by-a-condition"></a>
### 3.5.1 Values chosen by a condition (`case when`)

A ternary operator that chooses a value by entity values becomes one SQL `case when … end` expression.
You can mix `&&`, `||` and `!` in the condition. Chained ternary operators (`a ? x : b ? y : z`) become one `case` with several branches.

You can use `case` as a compared value in `where`, a selected value in `select`, a `groupBy` key, a sort key, and an assigned value in `update`.

<!-- region: CaseWhenTest.java#case-where -->
```java
SelectQuery<Order> q = qb.from(Order.class).where(o -> o.getStatus() == (o.isActive() ? 1 : 2));
assertEquals("select e0 from Order e0 where e0.status = case when e0.active = true then 1 else 2 end", q.toHql().hql());
```

<!-- region: CaseWhenTest.java#case-select -->
```java
ValueQuery<String> q = qb.from(Order.class).select(o -> o.getPrice() > 100 ? "big" : "small");
assertEquals("select case when e0.price > 100.0 then 'big' else 'small' end from Order e0", q.toHql().hql());
```

<!-- region: CaseWhenTest.java#case-group -->
```java
// orders per price band: the case is the group key and the selected value
ValueQuery<String> q = qb.from(Order.class)
        .groupBy(o -> o.getPrice() > 100 ? "big" : "small")
        .select(o -> o.getPrice() > 100 ? "big" : "small");
assertEquals("select case when e0.price > 100.0 then 'big' else 'small' end from Order e0"
        + " group by case when e0.price > 100.0 then 'big' else 'small' end", q.toHql().hql());
```

<!-- region: CaseWhenTest.java#case-sort -->
```java
// active orders first, then by price
OrderedSelectQuery<Order> q = qb.from(Order.class).orderBy(o -> asc(o.isActive() ? 0 : 1).asc(o.getPrice())).limit(10);
assertEquals("select e0 from Order e0 order by case when e0.active = true then 0 else 1 end asc, e0.price asc, e0.id asc",
        q.toHql().hql());
```

<!-- region: CaseWhenTest.java#case-set -->
```java
// 10 % off the orders above 100
ExecutableUpdate<Order> u = qb.update(Order.class).set(o -> o.setPrice(o.getPrice() > 100 ? o.getPrice() * 0.9 : o.getPrice())).all();
assertEquals("update Order e0 set e0.price = case when e0.price > 100.0 then (e0.price * 0.9) else e0.price end", u.toHql().hql());
```

Rules:

- `case` checks the branches in order from the top. So the conditions of the second and later branches are the same as the conditions written in the lambda expression.
- A `case` compared in `where` is computed for each row, so the database cannot use the indexes of the columns in it. In this case lambdaQL logs `LQL-5013` at `WARN` level.
- A form that chooses the sort function chain itself by a condition (`flag ? desc(o.getPrice()) : asc(o.getId())`) is rejected (`LQL-2023`). Write the condition inside the key value.

Example code: [`CaseWhenTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/CaseWhenTest.java) (`case-where`, `case-select`, `case-group`, `case-sort`, `case-set`).

<a id="36-null-rules"></a>
## 3.6 NULL rules

This is the most important part. There are two simple principles.

1. **Entity values are compared with SQL meaning**. `==`, `equals`, `Objects.equals` and `!=` between objects become `=` and `<>`.
   Rows where the value is NULL match neither.
   When the result can differ from Java, lambdaQL logs `LQL-5009` at `DEBUG` level.
2. **Captured values are computed first in Java, and the comparison also follows SQL meaning.** A captured value `null` is bound as NULL, so `= :p1` and `<> :p1` match no row.
   In this case lambdaQL logs `LQL-4045` at `WARN` level at run time. To find NULL, write `x == null` (it becomes `is null`).
   An expression made of captured values alone (`p.trim()`, `min.intValue()` that converts to another primitive type, and so on) is a value computed in Java. So if the receiver is `null`, it throws an exception (`LQL-4012`), as in Java.
   An unboxing to its own primitive type (`min.doubleValue()`) has the same bytecode opcodes as javac's automatic unboxing, so it is not run, and the boxed value is bound as it is (see [3.6.4](#364-captured-null-values)).

lambdaQL **handles null only when you write it yourself** (`== null`, `!= null`).
It does not add hidden null checks for you.

<a id="361-rule-table"></a>
### 3.6.1 Rule table

| # | Java form | HQL | NULL behavior |
|---|---|---|---|
| R1 | One side is a primitive type: `o.getStatus() == 1`, `o.getPrice() > p` | `e0.status = 1`, `e0.price > :p1` | SQL meaning. If the column is NULL, the row is not returned |
| R2 | Both sides are objects: `o.getCnt() == wanted` (`Integer == Integer`), `String == String`, enum | `e0.cnt = :p1` | **Value comparison.** If the column is NULL, the row is not returned. Captured value `null`: no row matches (`LQL-4045`) |
| R2 | `o.getCnt() != wanted` (objects) | `e0.cnt <> :p1` | SQL meaning. If the column is NULL, the row is **not** returned (Java would return it). Captured value `null`: no row matches (`LQL-4045`) |
| R2 | Two entity values: `o.getDescription() == o.getCustName()` | `e0.description = e0.custName` | SQL meaning. If either side is NULL, the row is not returned (also for NULL and NULL) |
| R3 | `x == null`, `x != null` | `x is null`, `x is not null` | |
| R4 | `o.getDescription().equals(p)` | `e0.description = :p1` | SQL meaning. If the column is NULL, the row is not returned. If `p == null`, no row matches (`LQL-4045`) |
| R5 | `Objects.equals(a, b)`, `"x".equals(a)`, `Boolean.TRUE.equals(a)` | Same as R2: `a = b`, `a = :p1` | Same as R2 |
| R9 | Captured value | Bind parameter | See [3.6.4](#364-captured-null-values) |

Which Java types become R1 and which become R2 is explained in [9. Java notes](09-java-notes.md#93-equality-of-boxed-values).

<a id="362--on-objects-compares-values"></a>
### 3.6.2 `==` on objects compares values

In Java, `==` on two `Integer`, `Long` or `String` objects checks **object identity**.
In lambdaQL, it is a **value comparison**. `Long` values greater than 127 and `new String(...)` do not matter.
A lambda expression is a query, so the memory rules of Java do not apply.

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

To find rows where cnt is NULL, write a null check, like the last query in the example above.

The column is not wrapped in a function, so the database can use an index.

lambdaQL binds the captured value when the query runs. `null` is bound as NULL, and no row can match, so lambdaQL logs `LQL-4045` at `WARN` level.
<!-- region: SelectQueryTest.java#null-safe -->
```java
// the SQL meaning: = NULL matches no row; lambdaQL logs LQL-4045 at WARN when it runs with a null
String d = null;
RenderedQuery q = qb.from(Order.class).where(o -> Objects.equals(o.getDescription(), d)).toHql();
assertEquals("select e0 from Order e0 where e0.description = :p1", q.hql());
// to find NULL, write the null check
RenderedQuery q3 = qb.from(Order.class).where(o -> o.getDescription() == null).toHql();
assertEquals("select e0 from Order e0 where e0.description is null", q3.hql());

String d2 = "a";
RenderedQuery q2 = qb.from(Order.class).where(o -> Objects.equals(o.getDescription(), d2)).toHql();
assertEquals("select e0 from Order e0 where e0.description = :p1", q2.hql());
```

**SQL by database**

<!-- sql: null-safe -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.description=?
```

**SQL by database**

<!-- sql: null-safe#2 -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.description=?
```

> [!TIP]
> **IDE warning.** IntelliJ shows "Number objects compared using `==`" for this form.
> Inside a lambdaQL lambda expression, this is the intended behavior. Declare `@SuppressWarnings("NumberEquality")` on the method to remove the warning.

**Columns that cannot be NULL.** The id, primitive columns and `nullable = false` columns cannot be NULL.
`is null` on such a column is always false, so lambdaQL folds this check into a constant and removes it.

- `o.getId() == null` becomes `1=0` (no rows).
- `o.getId() != null && o.getId() == id` becomes `e0.id = :p1`. The null check disappears, so the database can use an index.

Kotlin's `it.id == id` on a `Long?` id compiles into the same bytecode opcodes, so the Kotlin form also becomes the simple `e0.id = :p1`.

This rule applies **only when the path has no association step**. There are orders without a customer, so `o.getCustomer().getId()` can be NULL, and the null check on this path is kept.

<!-- region: SelectQueryTest.java#never-null -->
```java
// the id can never be NULL, so the null check is dropped (this is what Kotlin's it.id == id compiles to)
assertEquals("select e0 from Order e0 where e0.id = :p1",
        qb.from(Order.class).where(o -> o.getId() != null && o.getId() == id).toHql().hql());
assertEquals("select e0 from Order e0 where 1=0", qb.from(Order.class).where(o -> o.getId() == null).toHql().hql());
// through an association the row may have no customer, so the check stays
assertEquals("select e0 from Order e0 where e0.customer.id is null",
        qb.from(Order.class).where(o -> Objects.equals(o.getCustomer().getId(), null)).toHql().hql());
```

<a id="363-write-your-null-checks-yourself"></a>
### 3.6.3 Write your null checks yourself

<!-- region: ManualJavaWhereTest.java#null-check -->
```java
// only rows where cnt is not NULL and not 1
SelectQuery<Order> notOne = qb.from(Order.class).where(o -> o.getCnt() != 1);
assertEquals("select e0 from Order e0 where e0.cnt <> 1", notOne.toHql().hql());
// rows where cnt is NULL too
SelectQuery<Order> notOneOrNull = qb.from(Order.class).where(o -> o.getCnt() == null || o.getCnt() != 1);
assertEquals("select e0 from Order e0 where e0.cnt is null or e0.cnt <> 1", notOneOrNull.toHql().hql());
```

`equals()` and `Objects.equals()` become the same comparison (`=`). The negated `!Objects.equals(...)` becomes `<>`, and rows where the value is NULL are not returned.

<!-- region: ManualJavaWhereTest.java#equals-forms -->
```java
String text = "a";
assertEquals("select e0 from Order e0 where e0.description = :p1",
        qb.from(Order.class).where(o -> o.getDescription().equals(text)).toHql().hql());
// the same comparison; when text is null, no row matches
assertEquals("select e0 from Order e0 where e0.description = :p1",
        qb.from(Order.class).where(o -> Objects.equals(o.getDescription(), text)).toHql().hql());
// rows where cnt is NULL are not returned (the SQL meaning)
SelectQuery<Order> notOne = qb.from(Order.class).where(o -> !Objects.equals(o.getCnt(), 1));
assertEquals("select e0 from Order e0 where e0.cnt <> 1", notOne.toHql().hql());
```

**SQL meaning for entity values**. A comparison of two entity values, and a comparison of an entity value with a value using `!=`, follow SQL. Rows where the value is NULL match neither `=` nor `<>`.

In Java, `!text.equals(null)` is true and `Objects.equals(null, null)` is also true, so Java would return these rows.
When such a difference can happen, lambdaQL logs `LQL-5009` at `DEBUG` level, once per condition. To keep NULL rows, write the null check in the lambda expression yourself.

H2, MariaDB, Oracle. The rows are the description/custName pairs `a/a`, `a/b`, `null/a`, `null/null`, `b/null`, and `text` is `"a"`:

<!-- region: SqlMeaningTest.java#sql-meaning -->
```java
// two entity values: = and <> as in SQL; a row with a NULL on either side matches neither
SelectQuery<Order> same = qb.from(Order.class).where(o -> Objects.equals(o.getDescription(), o.getCustName()));
assertEquals("select e0 from Order e0 where e0.description = e0.custName", same.toHql().hql());
assertEquals(List.of("a/a"), pairs(same.toList()));                    // null/null is not "equal" in SQL
SelectQuery<Order> different = qb.from(Order.class).where(o -> !Objects.equals(o.getDescription(), o.getCustName()));
assertEquals(List.of("a/b"), pairs(different.toList()));               // rows with a NULL are left out
// an entity value and a value: a NULL column does not match <>
SelectQuery<Order> notA = qb.from(Order.class).where(o -> !text.equals(o.getDescription()));
assertEquals("select e0 from Order e0 where e0.description <> :p1", notA.toHql().hql());
assertEquals(List.of("b/null"), pairs(notA.toList()));
// to keep the NULL rows, say so in the lambda
SelectQuery<Order> notAOrNull = qb.from(Order.class).where(o -> o.getDescription() == null || !text.equals(o.getDescription()));
assertEquals(List.of("b/null", "null/a", "null/null"), pairs(notAOrNull.toList()));
// a NULL Boolean is not false
assertEquals(List.of("f"), qb.from(Coded.class).where(c -> !c.getVip()).toList().stream().map(Coded::getCode).toList());
```

<a id="364-captured-null-values"></a>
### 3.6.4 null captured values

| Form | When the captured value is `null` |
|---|---|
| `==`, `!=`, `Objects.equals(...)`, `o.getDescription().equals(p)` with a captured value | Runs: `= :p1` / `<> :p1` with NULL bound, so **no** row matches. `LQL-4045` at `WARN` level |
| `p.equals(o.getDescription())` (`p` is the receiver) | Same as above: `e0.description = :p1` (NULL), **no** row matches. `LQL-4045` at `WARN` level. No exception |
| A comparison where Java unboxes the value: `o.getPrice() > p` (`Double p`), `i.getQty() == q` (`int qty`, `Integer q`) | Runs: NULL bound to `e0.price > :p1`, **no** row matches. `LQL-4045` at `DEBUG` level. The unboxing to its own primitive type (`p.doubleValue()`) is not run |
| An unboxing that converts to another primitive type (`p.intValue()`, `Double p`) and method calls (`p.trim()`) | **Exception** when the query runs (`LQL-4012`), because the value is computed in Java |

**The form `p.equals(o.getDescription())` is not recommended.** In SQL it is the same comparison as `o.getDescription().equals(p)` (`e0.description = :p1`), so there is no exception even when `p` is `null`.
But when you read it as Java code, `null.equals(...)` looks like a NullPointerException. So write `o.getDescription().equals(p)`, with the entity value first.

Why: comparisons inside a lambda expression follow SQL meaning. In SQL, `=` and `<>` with NULL are never true.
Whether a captured value is null is known only when the query runs, so lambdaQL logs the warning at run time.

An expression made of captured values alone is a value computed in Java before the query. Method calls like `p.trim()` and unboxings that convert to another primitive type (`p.intValue()`, `Double p`) belong here. If the value is `null`, they throw an exception (`LQL-4012`), as in Java.
But an unboxing to its own primitive type (`Double.doubleValue()`, `Integer.intValue()`, `Long.longValue()` and so on, including a widening conversion after it) is not computed.
javac's automatic unboxing and a call written by hand have the same bytecode opcodes, so lambdaQL treats it as automatic unboxing and binds the boxed value. If it is `null`, no row matches, and `LQL-4045` is logged at `DEBUG` level.
To find NULL rows, write `x == null`.

**Skip a filter with a null guard.** If you null-check the same captured value, the condition disappears without a filter when the value is null.
<!-- region: SelectQueryTest.java#captured-null-guard -->
```java
Double min = null;                                       // for example an empty search field
RenderedQuery q = qb.from(Order.class).where(o -> min == null || o.getPrice() > min).toHql();
// the guard is decided in Java when the query runs: no filter at all
assertEquals("select e0 from Order e0", q.hql());

Double min2 = 100.0;
RenderedQuery q2 = qb.from(Order.class).where(o -> min2 == null || o.getPrice() > min2).toHql();
assertEquals("select e0 from Order e0 where e0.price > :p1", q2.hql());
assertEquals(Map.of("p1", 100.0), q2.parameters());
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

- The parts that use only captured values and constants are decided in Java when the query runs. They are handled left to right with short-circuit evaluation, as in Java.
- If `min == null`, the guard is true, so the whole condition is true. There is no filter. The comparison `o.getPrice() > min` is not written into the HQL (Java does not run this comparison either).
- If `min = 100.0`, the guard is false, so only `e0.price > :p1` is left.
- The lambda expression is still analyzed only once. Only the HQL text depends on the value.
- In this case the position of the guard does not change the result. When `min == null`, `o.getPrice() > min || min == null` also has no filter, because the unboxing in the comparison is not run in Java (Java would throw a NullPointerException).
- A comparison that calls a method on a captured value (`o.getDescription().equals(p.trim())`) is different. If the guard comes after it, `p.trim()` is computed first, so it throws an exception (`LQL-4012`) when `p == null`.
- With `&&`, the guard works the other way. When `min == null`, `min != null && o.getPrice() > min` returns no rows (`where 1=0`).

<a id="365-converted-attributes-convert"></a>
### 3.6.5 Converted attributes (`@Convert`)

Attributes with a JPA `AttributeConverter` are compared with Java values. Hibernate (7.4, and also the earlier 6.6) converts the literals compared with such attributes, and it converts bind parameters too.

So you find the stored rows even when you write the condition with Java values (checked on H2, MariaDB and Oracle).

<!-- region: ConvertTest.java#convert -->
```java
// code is stored reversed ("abc" as "cba"); vip is stored as 'Y'/'N' (YesNoConverter)
SelectQuery<Coded> literal = qb.from(Coded.class).where(c -> c.getCode().equals("abc"));
assertEquals("select e0 from Coded e0 where e0.code = 'abc'", literal.toHql().hql());
assertEquals(List.of("abc"), codes(literal.toList()));       // Hibernate converts the literal
```

Example code: [`ManualJavaWhereTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaWhereTest.java) (`boxed-equals`, `null-check`, `equals-forms`), [`SelectQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java) (`null-safe`, `never-null`, `captured-null-guard`), [`SqlMeaningTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlMeaningTest.java) (`sql-meaning`), [`ConvertTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ConvertTest.java) (`convert`).

<a id="37-boolean-columns"></a>
## 3.7 boolean columns

In the example entity, `active` is a primitive `boolean` (getter `isActive()`).
The rows with `getActive()` are for a nullable `Boolean` column.

| Java | HQL | NULL rows |
|---|---|---|
| `o.isActive()` | `e0.active = true` | – (a primitive `boolean` cannot be NULL) |
| `!o.isActive()` | `e0.active = false` | – (a primitive `boolean` cannot be NULL) |
| `Boolean.TRUE.equals(o.getActive())` (nullable `Boolean`) | `e0.active = :p1` (`:p1` is `true`) | Not returned |
| `!Boolean.TRUE.equals(o.getActive())` | `e0.active <> :p1` | **Not returned** (SQL meaning. Java would return them) |
| `!o.getActive()` (nullable `Boolean`) | `e0.active = false` | **Not returned** (SQL meaning: NULL is not false) |

<!-- region: SelectQueryTest.java#boolean -->
```java
assertEquals("select e0 from Order e0 where e0.active = true",
        qb.from(Order.class).where(o -> o.isActive()).toHql().hql());
assertEquals("select e0 from Order e0 where e0.active = false",       // primitive boolean: never NULL
        qb.from(Order.class).where(o -> !o.isActive()).toHql().hql());
```

**SQL by database**

<!-- sql: boolean -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.active=true
```

**SQL by database**

<!-- sql: boolean#2 -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.active=false
```

Hibernate 7.4 writes `true` / `false` in the SQL on all three databases. Hibernate 6.6 wrote `1` / `0` on MariaDB and Oracle.

Notes:

- `!o.getActive()` on a nullable `Boolean` column is an unboxing call in Java (a NullPointerException if NULL). lambdaQL uses SQL meaning. NULL rows are not returned.
- lambdaQL does not use `is not true` or `coalesce(active, false)`. They fail on Oracle (ORA-00932).
- The negation of a larger condition is always written as `not (...)`. lambdaQL does not wrap a condition in `(...) = false` or `(...) is null`, because Oracle 23 evaluates them wrongly in `WHERE`.

**In a comparison of a boolean column with a condition** (`o.isActive() == (o.getStatus() > s)`), the condition side becomes `case when … then true else false end`.
This `case` is computed for each row, so the database cannot use the `status` index (`LQL-5013` at `WARN` level).
If you need the index, split the two cases with `&&` and `||`. This form is written with `AND`/`OR`.

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

Example code: [`SelectQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java) (`boolean`), [`ManualJavaWhereTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaWhereTest.java) (`boolean-compare`).

<a id="38-strings"></a>
## 3.8 Strings

- `equals`, `Objects.equals` and `==` on strings use the **database collation**.
  - MariaDB's `_ci` collations ignore case and trailing spaces.
  - Oracle stores `''` (the empty string) as NULL. So on Oracle, `"".equals(o.getDescription())` behaves differently from Java.
- `==` on two strings is a value comparison, not an identity comparison. See [9.4](09-java-notes.md#94-strings).
- String constants in a lambda expression are written as literals. Single quotes are escaped (`''`).

<!-- region: SelectQueryTest.java#string-literal -->
```java
RenderedQuery q = qb.from(Order.class).where(o -> o.getDescription().equals("O'Brien")).toHql();
assertEquals("select e0 from Order e0 where e0.description = 'O''Brien'", q.hql());
```

**SQL by database**

<!-- sql: string-literal -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.description='O''Brien'
```

**`startsWith`, `endsWith` and `contains`** become `like`. `%` and `_` in the search text are matched as plain characters, not as wildcards.

<!-- region: LikeTest.java#like -->
```java
// % and _ in the text are not wildcards: only "50%_off sale" matches, not "50x off"
SelectQuery<Order> q = qb.from(Order.class).where(o -> o.getDescription().startsWith(p));
assertEquals("select e0 from Order e0 where e0.description like :p1 escape '!'", q.toHql().hql());
assertEquals("50!%!_%", q.toHql().parameters().get("p1"));
// a constant is written in the HQL
SelectQuery<Order> ends = qb.from(Order.class).where(o -> o.getDescription().endsWith("all"));
assertEquals("select e0 from Order e0 where e0.description like '%all' escape '!'", ends.toHql().hql());
```

- Constants are written into the HQL as they are, and captured values become bind parameters.
- `endsWith` and `contains` have `%` at the start of the pattern, so they cannot use a normal index on the column.
- On MariaDB and MySQL, `like` is case-insensitive with the default collation. So the result can differ from Java's `startsWith`.
- If a captured value used as the search text is `null`, it is an error (`LQL-4047`).
- Other string methods, such as `trim()` and `toUpperCase()`, are not supported (`LQL-3008`).
- String `+` in a lambda expression is rejected (`LQL-2007`). This is also true when only captured values are joined. Build the string before the lambda expression.

<!-- region: ManualJavaWhereTest.java#rejected-forms -->
```java
// a String method on an entity value other than startsWith, endsWith and contains
assertEquals("LQL-3008", assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(o -> o.getDescription().trim().equals("a"))).code());
// string concatenation inside the lambda, even of captured values only
assertEquals("LQL-2007", assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(o -> o.getDescription().equals("A" + code))).code());
// an entity compared with an entity: compare the ids
assertEquals("LQL-3011", assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(o -> o.getCustomer() == c)).code());
assertEquals("select e0 from Order e0 where e0.customer.id = :p1",
        qb.from(Order.class).where(o -> o.getCustomer().getId() == c.getId()).toHql().hql());
// operators without short-circuit
assertEquals("LQL-2013", assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(o -> o.isActive() & o.getStatus() == 1)).code());
```

Example code: [`SelectQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java) (`string-literal`), [`ManualJavaWhereTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaWhereTest.java) (`rejected-forms`), [`LikeTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/LikeTest.java) (`like`).

<a id="39-in-with-a-captured-collection"></a>
## 3.9 IN with a captured collection

Build the collection **outside** the lambda expression and call `contains` on it. The collection becomes one bind parameter.
The status of the example entity is a `short`:

<!-- region: SelectQueryTest.java#in -->
```java
List<Short> statuses = List.of((short) 2, (short) 3);
RenderedQuery q = qb.from(Order.class).where(o -> statuses.contains(o.getStatus())).toHql();
assertEquals("select e0 from Order e0 where e0.status in (:p1)", q.hql());
```

**SQL by database**

<!-- sql: in -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status in (?,?)
```

`!contains` becomes `not in`. A column that can be NULL also gets a simple `not in` (SQL meaning).
Rows where the value is NULL do not match `not in`, and lambdaQL logs `LQL-5009` at `DEBUG` level. Java's `!contains` is true for a NULL value, so to keep these rows, write a null check like `o.getDescription() == null || !descs.contains(o.getDescription())`.
<!-- region: SelectQueryTest.java#not-in -->
```java
List<Long> ids = List.of(1L, 2L);
// the id can never be NULL: a plain NOT IN
assertEquals("select e0 from Order e0 where e0.id not in (:p1)",
        qb.from(Order.class).where(o -> !ids.contains(o.getId())).toHql().hql());
// a column that can be NULL: the SQL meaning, a NULL value is not "not in" the list (DEBUG LQL-5009);
// to keep those rows, write o.getDescription() == null || !descs.contains(o.getDescription())
List<String> descs = Arrays.asList("a", "b");
assertEquals("select e0 from Order e0 where e0.description not in (:p1)",
        qb.from(Order.class).where(o -> !descs.contains(o.getDescription())).toHql().hql());
```

**SQL by database**

<!-- sql: not-in -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.id not in (?,?)
```

**SQL by database**

<!-- sql: not-in#2 -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.description not in (?,?)
```

| Case | Result |
|---|---|
| The receiver is a captured `Collection`, `List` or `Set` | Supported |
| The collection has a `null` element | Exception when the query runs |
| Empty collection | `contains` is FALSE, `!contains` is TRUE (Hibernate writes `1=0` / `1=1`) |
| 1,500 elements | Works as one statement on H2, MariaDB and Oracle 23ai (older Oracle versions have a limit of 1,000. Not checked on older versions) |
| The receiver is an entity collection: `c.getOrders().contains(order)` | **Rejected**. Use an `exists` subquery ([5. Subqueries](05-subquery.md)) |
| A collection built inside the lambda expression: `List.of(1, 2).contains(o.getStatus())` | **Rejected**. Build it outside and capture it |

Example code: [`SelectQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java) (`in`, `not-in`).

<a id="310-association-paths"></a>
## 3.10 Association paths

lambdaQL **does not call** `o.getCustomer().getName()`. This expression points to "the `name` column of the joined customer".
lambdaQL checks each step with the JPA Metamodel.

| Path step | HQL | Example |
|---|---|---|
| A to-one association (`@ManyToOne`, `@OneToOne`) | A `join` or `left join` with an alias (see below) | `o.getCustomer().getName()` -> `join e0.customer j1` ... `j1.name` |
| Embedded (`@Embedded`) | The same table, no join. An equality comparison of the whole value object is expanded per column (see below) | `o.getAddress().getCity()` -> `e0.address.city` |
| A path that ends with the `@Id` of the target | The foreign key column, no join | `o.getCustomer().getId()` -> `e0.customer.id` |
| The association itself compared with `null` | The foreign key column, no join | `o.getCustomer() == null` -> `e0.customer is null` |
| A collection (`@OneToMany`, `@ManyToMany`) | **Rejected** | `c.getOrders()...` -> use an `exists` subquery |

**Comparing a whole value object.** When you compare a whole embedded value object (`@Embedded`) for equality, lambdaQL expands it into one comparison per column.
`o.getAddress().equals(addr)`, `Objects.equals(o.getAddress(), addr)` and `addr.equals(o.getAddress())` all become `e0.address.city = :p1 and e0.address.zip = :p2`.

- The negation (`!o.getAddress().equals(addr)`) is `e0.address.city <> :p1 or e0.address.zip <> :p2`.
- `o.getAddress() == null` is `e0.address.city is null and e0.address.zip is null`, and `!= null` is `e0.address.city is not null or e0.address.zip is not null`.
- Each column follows SQL meaning. A NULL column matches neither `=` nor `<>`. For example, a (Seoul, NULL) row matches neither the equality comparison nor its negation (measured on three databases).
- Each property of the captured value object is read and bound when the query runs. If the captured object is `null`, NULL is bound to all columns, so no row matches, and `LQL-4045` is logged at `WARN` level for each column.
- Nested value objects are expanded too.
- Size comparisons (`<` and others) and comparisons with other types are still the error `LQL-3012`. Compare the fields.

<!-- region: MappingRulesTest.java#embedded-compare -->
```java
// address is @Embedded (city, zip): comparing the whole value object compares each column
Address addr = new Address("Seoul", "04524");
SelectQuery<Order> same = qb.from(Order.class).where(o -> o.getAddress().equals(addr));
assertEquals("select e0 from Order e0 where e0.address.city = :p1 and e0.address.zip = :p2", same.toHql().hql());
assertEquals(Map.of("p1", "Seoul", "p2", "04524"), same.toHql().parameters());
// Objects.equals and addr.equals(...) are the same comparison
assertEquals(same.toHql().hql(), qb.from(Order.class).where(o -> Objects.equals(o.getAddress(), addr)).toHql().hql());
// null checks each column: "no address" means every column is NULL
SelectQuery<Order> none = qb.from(Order.class).where(o -> o.getAddress() == null);
assertEquals("select e0 from Order e0 where e0.address.city is null and e0.address.zip is null", none.toHql().hql());
SelectQuery<Order> some = qb.from(Order.class).where(o -> o.getAddress() != null);
assertEquals("select e0 from Order e0 where e0.address.city is not null or e0.address.zip is not null",
        some.toHql().hql());
// each column follows the SQL meaning: a NULL column matches neither = nor <>
SelectQuery<Order> other = qb.from(Order.class).where(o -> !o.getAddress().equals(addr));
assertEquals("select e0 from Order e0 where e0.address.city <> :p1 or e0.address.zip <> :p2", other.toHql().hql());
```

In `where`, the join type of an association path is decided by the whole condition.
If an order without a customer can match the condition, it is a `left join`. If it cannot, it is an **inner join**.
The result is the same as running the same lambda expression on a list of orders.
A comparison through `o.getCustomer().getName()` is true only for orders that have a customer. The condition below has only that comparison, so it is an inner join.
<!-- region: SelectQueryTest.java#inner-join -->
```java
String name = "Kim";
RenderedQuery q = qb.from(Order.class).where(o -> o.getCustomer().getName().equals(name)).toHql();
assertEquals("select e0 from Order e0 join e0.customer j1 where j1.name = :p1", q.hql());
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

- **Orders without a customer** are not in the result, because they cannot match the condition.
- lambdaQL does not add a null check for you. You do not need to write `o.getCustomer() != null &&` before the path.

**A null guard keeps orders without a customer.** With `o.getCustomer() == null || ...`, orders without a customer also match the condition, so it becomes a `left join`.
With `o.getCustomer() != null && ...`, orders without a customer cannot match, so it is an inner join.
<!-- region: SelectQueryTest.java#null-guard -->
```java
String name = "Kim";
// the null guard keeps orders without a customer, as in Java: a left join
RenderedQuery q = qb.from(Order.class)
        .where(o -> o.getCustomer() == null || o.getCustomer().getName().equals(name))
        .toHql();
assertEquals("select e0 from Order e0 left join e0.customer j1 where e0.customer is null or j1.name = :p1", q.hql());

// no order without a customer can match: an inner join
RenderedQuery q2 = qb.from(Order.class)
        .where(o -> o.getCustomer() != null && o.getCustomer().getName().equals(name))
        .toHql();
assertEquals("select e0 from Order e0 join e0.customer j1 where e0.customer is not null and j1.name = :p1", q2.hql());
```

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

Hibernate writes `e0.customer is null` as `c1_0.id is null`.

- The same is true for other parts of `||`. `o.getStatus() == 1 || o.getCustomer().getName().equals(name)` matches when status is 1 even without a customer, so it is a `left join`, and those orders are returned too.
- When a comparison that can be true on a NULL column (`is null`, `coalesce`) goes through a path, lambdaQL adds `e0.customer is not null` only to that comparison. So this comparison is also true only for orders that have a customer.
  `o.getCustomer() == null || o.getCustomer().getName() == null` becomes `left join ... where e0.customer is null or (e0.customer is not null and j1.name is null)`.
- A comparison with a value follows SQL meaning. `!o.getCustomer().getName().equals(name)` does not return orders without a customer.
- A required association mapped with `optional = false` is always an inner join.

To find orders **without** a customer, compare the association itself. This checks the foreign key column without a join.
<!-- region: SelectQueryTest.java#no-association -->
```java
RenderedQuery q = qb.from(Order.class).where(o -> o.getCustomer() == null).toHql();
assertEquals("select e0 from Order e0 where e0.customer is null", q.hql());
```

**SQL by database**

<!-- sql: no-association -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id is null
```

A path that ends with the `@Id` of the associated entity is the foreign key column. No join is needed.
<!-- region: SelectQueryTest.java#fk-path -->
```java
RenderedQuery q = qb.from(Order.class).where(o -> o.getCustomer().getId() == 1).toHql();
assertEquals("select e0 from Order e0 where e0.customer.id = 1", q.hql());
```

**SQL by database**

<!-- sql: fk-path -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id=1
```

More:

- The same path gets one alias. This alias is shared by `where`, `orderBy` and `select`.
- A path used **only** in `orderBy` or `select` is a `left join`, so rows are not lost there. If the same path is also used in `where`, `orderBy` and `select` use the join of `where`.
- Comparing two entities (`o.getCustomer() == c`) is rejected (`LQL-3011`). Compare the ids: `o.getCustomer().getId() == c.getId()` (here `c` is a captured object, see the `rejected-forms` example in [3.8](#38-strings)).

Example code: [`MappingRulesTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/MappingRulesTest.java) (`embedded-compare`), [`SelectQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java) (`inner-join`, `null-guard`, `no-association`, `fk-path`).

<a id="311-captured-values"></a>
## 3.11 Captured values

A lambda expression can use values from outside: local variables, fields, and method calls on captured objects.

<!-- region: ManualJavaWhereTest.java#captured-forms -->
```java
LocalDateTime since = LocalDateTime.of(2026, 1, 1, 0, 0);
Filter filter = new Filter(60.0);
Form form = new Form(" a ");
SelectQuery<Order> query = qb.from(Order.class)
        .where(o -> o.getUpdateAt().compareTo(since) > 0)            // a local variable
        .where(o -> o.getStatus() == this.defaultStatus)             // a field of this
        .where(o -> o.getPrice() > filter.minPrice())                // a method of a captured object
        .where(o -> o.getDescription().equals(form.text().trim()));  // computed in Java
assertEquals("select e0 from Order e0 where e0.updateAt > :p1 and e0.status = :p2"
        + " and e0.price > :p3 and e0.description = :p4", query.toHql().hql());
```

Rules:

- The parts that do not use the entity are computed in Java **each time the query runs**. The result becomes a bind parameter.
- A compile-time constant (`static final int MAX = 5;`) is not a captured value. javac copies the value into the lambda expression, so it becomes a literal like `5` (see [3.3](#33-basic-form)).
- A lambda expression is analyzed once, and the result is cached. New captured values are read on each call.
- Only the entity parts become SQL. `form.text().trim()` is computed in Java. `o.getDescription().trim()` on an entity value is rejected (`LQL-3008`).
- In Java, a captured local variable must be effectively final. If the value changes, compute it into a new variable before the lambda expression.
- A lambda expression that uses a field captures `this`. See [9.8](09-java-notes.md#98-lambdas-in-instance-methods-capture-this).

Example code: [`ManualJavaWhereTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaWhereTest.java) (`captured-forms`).

<a id="312-errors-that-depend-on-order-of-evaluation"></a>
## 3.12 Errors that depend on the order of evaluation

SQL does not promise to evaluate `AND`/`OR` from left to right. So a "guard" like the one below is not safe in SQL.
lambdaQL moves the guard into the HQL as written. If the database computes the division first, a row where `qty` is 0 can give a division-by-zero error.

<!-- region: ManualJavaWhereTest.java#guard -->
```java
// the guard is written as it is; SQL does not promise to check "qty <> 0" before the division
assertEquals("select e0 from Item e0 where e0.qty <> 0 and (e0.price / e0.qty) > 2.0",
        qb.from(Item.class).where(i -> i.getQty() != 0 && i.getPrice() / i.getQty() > 2).toHql().hql());
// compare without the division
SelectQuery<Item> query = qb.from(Item.class).where(i -> i.getQty() > 0 && i.getPrice() > 2 * i.getQty());
assertEquals("select e0 from Item e0 where e0.qty > 0 and e0.price > (2.0 * e0.qty)", query.toHql().hql());
```

Do not rely on the guard. Compare without division: `i.getQty() > 0 && i.getPrice() > 2 * i.getQty()`.

Example code: [`ManualJavaWhereTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaWhereTest.java) (`guard`).

<a id="313-what-is-rejected"></a>
## 3.13 What is rejected

When the analysis fails, lambdaQL throws an exception with the code `[LQL-xxxx]` and the source line number.
Most shapes are found when the lambda expression is analyzed (on first use). Wrong captured values (a `null` with a method call, NaN, a collection with a `null` element) are found when the query runs.
See [11. Errors and languages](../11-errors-and-languages.md).

| Form | Example | How to fix |
|---|---|---|
| A loop, `switch` or local variable in a block lambda expression | `o -> { int x = 1; return ...; }` | Write one expression |
| A general boolean ternary operator | `c ? x : y` | Use an allowed form from [3.5](#35-and-or-not) |
| Operators that do not short-circuit (`LQL-2013`) | `a & b`, `a \| b`, `a ^ b` | Use `&&`, `\|\|` |
| String `+` (`LQL-2007`) | `o.getDescription().equals("A" + code)` | Build the string before the lambda expression |
| A lambda expression or stream inside the lambda expression | `o -> list.stream().anyMatch(...)` | Use `contains` ([3.9](#39-in-with-a-captured-collection)) or a subquery |
| String methods on entity values (`LQL-3008`) | `o.getDescription().trim()` (`startsWith`, `endsWith` and `contains` are supported, [3.8](#38-strings)) | Not supported |
| Other methods of the entity | `o.calcSomething()` | Use only getters |
| An entity collection path | `c.getOrders().size() > 0` (`Customer.orders`) | An `exists` subquery |
| Comparing an entity with an entity (`LQL-3011`) | `o.getCustomer() == c` | Compare the ids |
| A size comparison of value objects, a comparison with another type (`LQL-3012`) | `o.getAddress().compareTo(a) > 0` (when `Address` is `Comparable`) | Compare the fields. Equality comparison is supported |
| A null captured value with a method call | `o.getDescription().equals(p.trim())` and `o.getStatus() > p.intValue()` (`Double p`) when `p == null` | Put a null guard first (`p == null \|\| ...`), or check `p` before the query |
| A `NaN` / `Infinity` captured value | `o.getPrice() > Double.NaN` | Do not send NaN |
| An anonymous class, a lambda expression that is not `Serializable` | `new Where<Order>() { ... }`, `java.util.function.Predicate` | Use a lambda expression |

Exception types (all extend `LambdaQLException`):

| Exception | When |
|---|---|
| `LambdaAnalysisException` | The lambda expression cannot be read (for example, it is not a serializable lambda expression) |
| `UnsupportedLambdaShapeException` | The shape is not supported (loop, `switch`, ...) |
| `TranslationException` | The meaning cannot be translated (unknown method, collection path, ...) |

Java-only details are in [9. Java notes](09-java-notes.md). To see all rules in one place: [10. Rules and limits](10-rules-and-limits.md).

---

[Java track](README.md) | Previous: [2. Getting started](02-getting-started.md) | Next: [4. Sorting and paging](04-order-paging.md) | [Kotlin version of this page](../kotlin/03-where.md)
