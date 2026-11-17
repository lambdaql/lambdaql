# 4. Sorting and paging (Java)

> [!NOTE]
> **Status**
> - **Core implemented and tested** on H2, MariaDB 10.11 and Oracle 23.26 (Hibernate 6.6.5): `orderBy((o, by) -> ...)` with `by.asc`, `by.desc` and `by.sort`, the one-key forms `orderBy(o -> key)` and `orderBy(key, Direction)` (also with a getter method reference like `Order::getPrice`), `Direction.parse` / `reversed`, `limit` / `offset`, `first()` after `orderBy`, and the reusable sort `SortSpec` (`of`, `ofPath`, `then`, `reversed`, `orderBy(spec)`). Spring `Sort` and `Pageable` work in the Spring Data JPA module ([12.5](12-spring-data-jpa.md#125-spring-sort-and-pageable)).
> - **Also implemented and tested:** the join form `(o, c, by) -> ...` after `join`, `leftJoin` or `crossJoin` (see [6.8 Explicit joins](06-select-group.md#68-explicit-joins)), and the sort policy for outside sorts (`SortRules`, `SortPolicy`, `SortMode`, see [4.10](#sort-policy)).
> - **Not implemented yet:** the join form of `SortSpec`.
> - Examples marked **(tested)** are copied from the test suite. The other HQL in this chapter follows the same rules, but it is not tested.

[Java track](README.md) | [Manual home](../README.md) | Previous: [3. Conditions](03-where.md) | Next: [5. Subqueries](05-subquery.md) | [Kotlin version of this page](../kotlin/04-order-paging.md)

Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md)

---

## 4.1 One lambda for the whole sort

You write the sort as **one lambda**. The lambda gets the entity and a sort builder `by` as the last argument.
You add keys and directions with `by.asc(...)` and `by.desc(...)`.

```java
qb.from(Order.class)
  .where(o -> o.isActive())
  .orderBy((o, by) -> by.desc(o.getPrice()).asc(o.getCustomer().getName()).asc(o.getId()))
  .limit(20).offset(40)
  .toList();
// HQL: select e0 from Order e0 left join e0.customer j1
//      where e0.active = true
//      order by e0.price desc nulls last, j1.name asc nulls first, e0.id asc
```

**About the SQL blocks.** `…` is the shortened select list (all entity columns), and `?` is a bind parameter. The lines come from [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java). It runs each query on H2 2.3, MariaDB 10.11 and Oracle 23.26 with Hibernate 6.6.5, and it fails if Hibernate's SQL changes.

**SQL by database (Hibernate's SQL for this HQL)** — the manual's `Order` has a nullable `Double price`, but the test `Order` does not, so `SqlByDatabaseTest` gives this HQL to Hibernate directly. From `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `sort-overview`:

```sql
-- H2
-- (this HQL is given to Hibernate directly)
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where o1_0.active=true
order by o1_0.price desc,c1_0.name,o1_0.id
offset ? rows
fetch first ? rows only

-- MariaDB
-- (this HQL is given to Hibernate directly)
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where o1_0.active=1
order by o1_0.price desc,c1_0.name,o1_0.id
limit ?,?

-- Oracle
-- (this HQL is given to Hibernate directly)
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where o1_0.active=1
order by o1_0.price desc nulls last,c1_0.name asc nulls first,o1_0.id
offset ? rows
fetch first ? rows only
```

- The name `by` is only a habit in this manual. You can use any name.
- No static import is needed. `by` is a lambda argument.
- The key and its direction are written together. They can never get out of order.
- lambdaQL reads the lambda. It never runs it. The `by` methods only describe the order.

Examples marked **(tested)** are copied from [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java).
The test checks the HQL, runs the queries and compares the row order with the same sort in Java. It passes on H2, MariaDB and Oracle.
It uses the test entity from [3.3](03-where.md#33-basic-form): `price` is `double` and `status` is `short`. These keys can never be NULL, so their HQL has no `nulls` part.
In the other examples, `price` is a `Double` that can be NULL (see [3.2](03-where.md#32-example-entities)), so it gets `nulls first` or `nulls last` (see [4.4](#44-null-ordering-is-the-same-on-every-database)).

**(tested)** [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java), region `sort-basic`:

```java
RenderedQuery q = qb.from(Order.class)
        .where(o -> o.isActive())
        .orderBy((o, by) -> by.desc(o.getPrice()).asc(o.getId()))
        .toHql();
// price and id can never be NULL, so there is no "nulls" part
assertEquals("select e0 from Order e0 where e0.active = true order by e0.price desc, e0.id asc", q.hql());
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `sort-basic`:

```sql
-- H2
select …
from orders o1_0
where o1_0.active=true
order by o1_0.price desc,o1_0.id

-- MariaDB, Oracle
select …
from orders o1_0
where o1_0.active=1
order by o1_0.price desc,o1_0.id
```

`toHql()` gives the HQL without running the query. `toList()` runs it.

## 4.2 The short forms (one key)

If you have only one key, you can leave out `by`:

| Java | HQL |
|---|---|
| `orderBy(o -> o.getPrice())` | `order by e0.price asc nulls first` |
| `orderBy(o -> o.getUpdateAt(), Direction.DESC)` | `order by e0.updateAt desc nulls last` |
| `orderBy(Order::getPrice)` | `order by e0.price asc nulls first` |
| `orderBy(Order::getPrice, Direction.DESC)` | `order by e0.price desc nulls last` |

Without a `Direction`, the short form sorts ascending (`ASC`).
lambdaQL tells the forms apart by the number of lambda arguments (one or two). The Java compiler picks the right overload with no ambiguity.

A method reference must be a getter of the entity, like `Order::getPrice`. A static method, a method with arguments, or a reference bound to an object is rejected (rule S13).
The `by` form (two arguments) needs a lambda. A method reference is rejected there.

**(tested)** [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java), region `sort-key` (in the test entity, `price` is a `double`, so there is no `nulls` part):

```java
assertEquals("select e0 from Order e0 order by e0.price asc",
        qb.from(Order.class).orderBy(o -> o.getPrice()).toHql().hql());
assertEquals("select e0 from Order e0 order by e0.price desc",
        qb.from(Order.class).orderBy(Order::getPrice, Direction.DESC).toHql().hql());
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `sort-key`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
order by o1_0.price
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `sort-key#2`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
order by o1_0.price desc
```

## 4.3 `Direction`

`Direction` has six values:

| Value | Meaning | NULL position |
|---|---|---|
| `ASC` | ascending | NULLs **first** (default policy) |
| `DESC` | descending | NULLs **last** (default policy) |
| `ASC_NULLS_FIRST` | ascending | NULLs first |
| `ASC_NULLS_LAST` | ascending | NULLs last |
| `DESC_NULLS_FIRST` | descending | NULLs first |
| `DESC_NULLS_LAST` | descending | NULLs last |

`Direction` also has two helpers:

- `Direction.parse(text)` reads a direction from text, for example a request parameter. Case does not matter, and `-`, `_` and a space are the same: `Direction.parse("desc-nulls-last")` is `DESC_NULLS_LAST`. Unknown text throws `IllegalArgumentException`.
- `reversed()` gives the opposite direction. The NULL position flips too, so the whole order is reversed: `ASC.reversed()` is `DESC`, and `ASC_NULLS_LAST.reversed()` is `DESC_NULLS_FIRST`.

**Choose the NULL position** with a second `boolean` argument: `by.asc(key, nullsFirst)` and `by.desc(key, nullsFirst)` (decision D35).
`true` means "NULLs first", `false` means "NULLs last".
Without the second argument, `asc(key)` and `desc(key)` use the default NULL policy (decision D16, see [4.4](#44-null-ordering-is-the-same-on-every-database)).

| You write | Same as |
|---|---|
| `by.asc(key)` | `ASC` (NULLs first, default policy) |
| `by.desc(key)` | `DESC` (NULLs last, default policy) |
| `by.asc(key, true)` | `ASC_NULLS_FIRST` |
| `by.asc(key, false)` | `ASC_NULLS_LAST` |
| `by.desc(key, true)` | `DESC_NULLS_FIRST` |
| `by.desc(key, false)` | `DESC_NULLS_LAST` |

**(tested)** [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java), region `sort-nulls` (`description` is a `String` that can be NULL; `id` can never be NULL):

```java
// default: asc puts NULL first, desc puts NULL last (the same on every database)
assertEquals("select e0 from Order e0 order by e0.description asc nulls first, e0.id asc",
        qb.from(Order.class).orderBy((o, by) -> by.asc(o.getDescription()).asc(o.getId())).toHql().hql());
// the second argument chooses: true = NULL first, false = NULL last
assertEquals("select e0 from Order e0 order by e0.description asc nulls last, e0.id asc",
        qb.from(Order.class).orderBy((o, by) -> by.asc(o.getDescription(), false).asc(o.getId())).toHql().hql());
assertEquals("select e0 from Order e0 order by e0.description desc nulls first, e0.id asc",
        qb.from(Order.class).orderBy((o, by) -> by.desc(o.getDescription(), true).asc(o.getId())).toHql().hql());
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `sort-nulls`:

```sql
-- H2, MariaDB
select …
from orders o1_0
order by o1_0.description,o1_0.id

-- Oracle
select …
from orders o1_0
order by o1_0.description asc nulls first,o1_0.id
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `sort-nulls#2`:

```sql
-- H2
select …
from orders o1_0
order by o1_0.description asc nulls last,o1_0.id

-- MariaDB
select …
from orders o1_0
order by case when (o1_0.description) is null then 1 else 0 end,o1_0.description,o1_0.id

-- Oracle
select …
from orders o1_0
order by o1_0.description,o1_0.id
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `sort-nulls#3`:

```sql
-- H2
select …
from orders o1_0
order by o1_0.description desc nulls first,o1_0.id

-- MariaDB
select …
from orders o1_0
order by case when (o1_0.description) is null then 0 else 1 end,o1_0.description desc,o1_0.id

-- Oracle
select …
from orders o1_0
order by o1_0.description desc,o1_0.id
```

You can see the NULL policy in the SQL. H2 and MariaDB drop the clause when it is their default. Oracle writes `asc nulls first` / `desc nulls last`, because its own default is the opposite. MariaDB has no `NULLS` syntax, so for a position that is not its default it sorts by `case when … is null then … end` first.

**When the direction comes from a variable**, use `by.sort(key, direction)` with a captured `Direction`:

```java
.orderBy((o, by) -> by.desc(o.getDescription(), false).asc(o.getId()))   // nulls last

Direction dir = request.isNewestFirst() ? Direction.DESC : Direction.ASC;
.orderBy((o, by) -> by.sort(o.getUpdateAt(), dir).asc(o.getId()))   // dir is read on every call
```

The ternary `request.isNewestFirst() ? ... : ...` is fine here, because it runs **outside** the lambda.

**(tested)** [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java), region `sort-dynamic`:

```java
Direction dir = Direction.parse(text);           // for example from a request parameter
OrderedSelectQuery<Order> q = qb.from(Order.class)
        .orderBy((o, by) -> by.sort(o.getDescription(), dir).asc(o.getId()));
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `sort-dynamic`:

```sql
-- H2, MariaDB
select …
from orders o1_0
order by o1_0.description desc,o1_0.id

-- Oracle
select …
from orders o1_0
order by o1_0.description desc nulls last,o1_0.id
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `sort-dynamic#2`:

```sql
-- H2
select …
from orders o1_0
order by o1_0.description asc nulls last,o1_0.id

-- MariaDB
select …
from orders o1_0
order by case when (o1_0.description) is null then 1 else 0 end,o1_0.description,o1_0.id

-- Oracle
select …
from orders o1_0
order by o1_0.description,o1_0.id
```

The test runs this code two times, with `text` set to `"desc"` and to `"asc_nulls_last"`.
The HQL is `... order by e0.description desc nulls last, e0.id asc` and then `... order by e0.description asc nulls last, e0.id asc`.
The lambda is analyzed only once. `dir` is read each time the query runs (rule S10). A `null` direction throws when the query runs.

> [!TIP]
> A bare `false` does not say much. Java and Kotlin both write `by.desc(x, false)`: Kotlin cannot use named arguments (`nullsFirst = false`) on Java methods.
> So a short comment like `// nulls last` is a good habit.

## 4.4 NULL ordering is the same on every database

Databases do not agree on where NULLs go:

| | H2 | MariaDB | Oracle |
|---|---|---|---|
| `ASC` default | NULLs first | NULLs first | NULLs **last** |
| `DESC` default | NULLs last | NULLs last | NULLs **first** |
| `NULLS FIRST/LAST` in SQL | yes | **no (syntax error)** | yes |

lambdaQL fixes this with one policy: **NULL is the smallest value.**

- `ASC` = `ASC_NULLS_FIRST`
- `DESC` = `DESC_NULLS_LAST`

This is the same as Java `Comparator.nullsFirst(Comparator.naturalOrder())`.
With this policy, all three databases return the same order (for numbers and dates).

**How lambdaQL writes it.** For every key that can be NULL, lambdaQL writes the NULL position into the HQL, for example `e0.description asc nulls first`.
A key that can never be NULL (a primitive column like `double`, or the `@Id`) gets no `nulls` part.
A column behind an association can always be NULL (an order may have no customer), so it always gets one.

Then Hibernate writes the SQL for each database. It drops the clause when it is the database default.
MariaDB has no `NULLS` syntax, so there Hibernate writes a `CASE` expression for a position that is not the default.

What SQL you get:

| You write | H2 | MariaDB | Oracle |
|---|---|---|---|
| `asc(key)`, `desc(key)` (`ASC`, `DESC`) | no extra clause (already the default) | no extra clause | `nulls first` / `nulls last` added |
| A NULL position (`asc(key, b)`, `desc(key, b)`, `*_NULLS_*`) that is the DB default | no extra clause | no extra clause | no extra clause |
| A NULL position that is **not** the DB default | `nulls first/last` | Hibernate writes `case when x is null ...` | `nulls first/last` |
| Any direction on a key that can never be NULL | no extra clause | no extra clause | no extra clause |

> [!TIP]
> On MariaDB, `by.asc(key, false)` (`ASC_NULLS_LAST`) and `by.desc(key, true)` (`DESC_NULLS_FIRST`) become a `CASE` expression. The database cannot use an index for that sort. Use `asc(key)` / `desc(key)` when you can.

## 4.5 What a sort key can be

| Key | Allowed | Note |
|---|---|---|
| Column: `o.getPrice()` | yes | tested |
| To-one path: `o.getCustomer().getName()` | yes | `left join`, see below (tested) |
| Arithmetic: `o.getPrice() * 2`, `+`, `-` | yes | not tested yet |
| Widening conversion (`int` to `long`) | yes | not tested yet |
| An entity: `by.desc(o.getCustomer())` | **compile error** | an entity is not `Comparable` |
| A value without the entity: `by.asc(1)`, a captured value | **rejected** | see [4.6](#46-rules-for-the-lambda-body) (tested) |
| Function on the column: `lower(name)` | planned | |

Keys must be `Comparable`. Boxed types (`Double`, `Integer`, `Long`), `String` and the `java.time` types are `Comparable`, so they work. The Java compiler refuses an entity key.
If your entity class implements `Comparable`, lambdaQL rejects it as a key when it reads the lambda.

**Association paths use `left join`.** A plain path in HQL `order by o.customer.name` becomes an inner join on all three databases. Orders without a customer would disappear.
lambdaQL adds a `left join` instead. Orders without a customer stay in the result. Their `j1.name` is NULL, so they come first with `asc`.

**(tested)** [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java), region `sort-path`:

```java
RenderedQuery q = qb.from(Order.class)
        .orderBy((o, by) -> by.asc(o.getCustomer().getName()).asc(o.getId()))
        .toHql();
// a path used only for sorting is a LEFT JOIN: the order without a customer stays
assertEquals("select e0 from Order e0 left join e0.customer j1 order by j1.name asc nulls first, e0.id asc", q.hql());
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `sort-path`:

```sql
-- H2, MariaDB
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
order by c1_0.name,o1_0.id

-- Oracle
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
order by c1_0.name asc nulls first,o1_0.id
```

The alias is shared with `where`. If `where` already joined the same path, the sort uses that join.
In most cases it is an inner join: the `where` condition already removed the orders without a customer (decision D36, see [3.10](03-where.md#310-association-paths)).

**(tested)** [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java), region `sort-path-where`:

```java
String name = "Kim";
RenderedQuery q = qb.from(Order.class)
        .where(o -> o.getCustomer().getName().equals(name))
        .orderBy((o, by) -> by.desc(o.getCustomer().getName()).desc(o.getPrice()))
        .toHql();
// the condition already joined the customer, so the sort uses the same inner join
assertEquals("select e0 from Order e0 join e0.customer j1 where j1.name = :p1"
        + " order by j1.name desc nulls last, e0.price desc", q.hql());
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `sort-path-where`:

```sql
-- H2, MariaDB
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name=?
order by c1_0.name desc,o1_0.price desc

-- Oracle
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name=?
order by c1_0.name desc nulls last,o1_0.price desc
```

## 4.6 Rules for the lambda body

The body must be **a straight chain of `by` calls**:

```java
// OK
.orderBy((o, by) -> by.desc(o.getPrice()).asc(o.getId()))

// Rejected: conditional key
.orderBy((o, by) -> flag ? by.desc(o.getPrice()) : by.asc(o.getId()))

// Rejected: loop, if, local variables
```

lambdaQL checks this when you call `orderBy` and throws a `LambdaQLException` (for example `LQL-2023` for a condition, `LQL-3031` for a key without the entity).
Every key must use the entity. A key with only a constant or a captured value is rejected.

**(tested)** [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java), region `sort-rejected`:

```java
// a condition inside the sort lambda (rule S4)
assertThrows(LambdaQLException.class, () -> qb.from(Order.class)
        .orderBy((o, by) -> o.isActive() ? by.asc(o.getPrice()) : by.desc(o.getPrice())));
// a key without the entity
assertThrows(LambdaQLException.class, () -> qb.from(Order.class).orderBy((o, by) -> by.asc(1)));
```

If the direction must change at run time, pass it as a variable: `by.sort(key, dir)`. If the key itself must change, build the query in two branches of normal Java code.

## 4.7 Order of calls

The builder types control the order. Wrong order does not compile:

| Call | Allowed |
|---|---|
| `from(...).where(...).orderBy(...).limit(...).offset(...)` | yes |
| `from(...).where(...).limit(...).offset(...)` (no `orderBy`) | yes. The rows are sorted by the id (see [4.8](#48-paging)) |
| `orderBy(...)` two times | no (compile error) |
| `where(...)` after `orderBy(...)` | no (compile error) |
| `orderBy(...)` after `limit(...)` | no (compile error) |

After `orderBy`, `limit` or `offset`, the query has the type `OrderedSelectQuery`. It has only `limit`, `offset`, `toHql()`, `toList()`, `toSet()` and `first()`. `one()`, `count()` and `exists()` are only before `orderBy`, `limit` and `offset` (see [Getting the result](02-getting-started.md#getting-the-result)).

## 4.8 Paging

```java
qb.from(Order.class)
  .where(o -> o.getStatus() == 1)
  .orderBy((o, by) -> by.desc(o.getPrice()))
  .limit(20)      // page size
  .offset(40)     // skip 40 rows (page 3)
  .toList();
// HQL: select e0 from Order e0 where e0.status = 1 order by e0.price desc nulls last, e0.id asc
// limit and offset are passed to Hibernate (setMaxResults / setFirstResult)
```

**SQL by database (Hibernate's SQL for this HQL)** — the manual's `Order` has a nullable `Double price`, but the test `Order` does not, so `SqlByDatabaseTest` gives this HQL to Hibernate directly. From `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `page-design`:

```sql
-- H2
-- (this HQL is given to Hibernate directly)
select …
from orders o1_0
where o1_0.status=1
order by o1_0.price desc,o1_0.id
offset ? rows
fetch first ? rows only

-- MariaDB
-- (this HQL is given to Hibernate directly)
select …
from orders o1_0
where o1_0.status=1
order by o1_0.price desc,o1_0.id
limit ?,?

-- Oracle
-- (this HQL is given to Hibernate directly)
select …
from orders o1_0
where o1_0.status=1
order by o1_0.price desc nulls last,o1_0.id
offset ? rows
fetch first ? rows only
```

**(tested)** [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java), region `sort-page`:

```java
OrderedSelectQuery<Order> page = qb.from(Order.class)
        .orderBy((o, by) -> by.asc(o.getStatus()))
        .limit(2)
        .offset(2);
// status has ties, so the id is added as the last key and rows do not move between pages
assertEquals("select e0 from Order e0 order by e0.status asc, e0.id asc", page.toHql().hql());
List<Order> rows = page.toList();
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `sort-page`:

```sql
-- H2, Oracle
select …
from orders o1_0
order by o1_0.status,o1_0.id
offset ? rows
fetch first ? rows only

-- MariaDB
select …
from orders o1_0
order by o1_0.status,o1_0.id
limit ?,?
```

Paging SQL is different on each database: H2 and Oracle get `offset ? rows fetch first ? rows only`, and MariaDB gets `limit ?,?`. Without an offset, only `fetch first ? rows only` or `limit ?` is written (see `page-only` below).

`limit` and `offset` take 0 or more. A negative value throws `IllegalArgumentException`.

lambdaQL does not write `LIMIT` into the HQL. Hibernate writes the right SQL for each database:

| Database | SQL |
|---|---|
| H2 | `offset ? rows fetch first ? rows only` |
| MariaDB | `limit ?, ?` |
| Oracle | `offset ? rows fetch first ? rows only` |

**Paging without `orderBy`.** You can call `limit` and `offset` without `orderBy`. Then lambdaQL sorts the rows by the id, so the pages are stable.

**(tested)** [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java), region `page-only`:

```java
RenderedQuery q = qb.from(Order.class).where(o -> o.isActive()).limit(2).toHql();
assertEquals("select e0 from Order e0 where e0.active = true order by e0.id asc", q.hql());
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `page-only`:

```sql
-- H2
select …
from orders o1_0
where o1_0.active=true
order by o1_0.id
fetch first ? rows only

-- MariaDB
select …
from orders o1_0
where o1_0.active=1
order by o1_0.id
limit ?

-- Oracle
select …
from orders o1_0
where o1_0.active=1
order by o1_0.id
fetch first ? rows only
```

**The first row and the total count.**

- `orderBy(...).first()` returns the first row in that order, as an `Optional`. It reads only one row. An `offset` is kept: `orderBy(...).offset(1).first()` returns the second row.
- `count()` is only on the where stage (before `orderBy`, `limit` and `offset`), so it always counts all matching rows. For a paged screen, keep the where-stage query in a variable. Call `count()` on it for the total, then call `orderBy(...).limit(...).toList()` on it for the page.

Both are tested. See [Getting the result](02-getting-started.md#getting-the-result).

### Tie-breaker (automatic `id`)

Look at the HQL above: lambdaQL added `e0.id asc` at the end. Why?

- Many rows can have the same `status` or `price`. The database may return those rows in **any order**. Oracle really does this (measured).
- Then page 2 and page 3 can show the same row twice, or skip a row.

Rule: **when you use paging and the sort keys do not contain the id, lambdaQL adds the root `@Id asc` as the last key.**

- It is added only once. If your keys already contain the id (for example `by.desc(o.getId())`), it is not added.
- Without `limit` and `offset`, nothing is added.
- With explicit joins (`join`, `leftJoin`, `crossJoin`), the id of every joined entity is also added, in join order (tested, see [6.8](06-select-group.md#68-explicit-joins)).
- Not implemented yet: a collection fetch join together with `limit`/`offset` is rejected.

## 4.9 Strings and special values

- String order follows the database collation. MariaDB `_ci` ignores case. Oracle uses `NLS_SORT`, which follows the JVM locale (for example `de_DE` gives German order). For Oracle we recommend `NLS_SORT=BINARY`.
- Oracle stores `''` as NULL, so empty strings sort as NULLs.
- `NaN` is ordered differently by each database.

## 4.10 Reusable and external sorts

**`SortSpec`: a sort you can keep.** A `SortSpec` is a sort that you keep in a variable or a field and use in many queries.
Make it with `SortSpec.of(Order.class, (o, by) -> ...)`. The lambda is the same as in `orderBy((o, by) -> ...)`, so the same rules apply (see [4.6](#46-rules-for-the-lambda-body)).
Then pass it to `orderBy(spec)`.

```java
public static final SortSpec<Order> NEWEST =
        SortSpec.of(Order.class, (o, by) -> by.desc(o.getUpdateAt()).asc(o.getId()));

qb.from(Order.class).where(o -> o.isActive()).orderBy(NEWEST).limit(20).toList();
// HQL: select e0 from Order e0 where e0.active = true order by e0.updateAt desc nulls last, e0.id asc
```

- A `SortSpec` cannot be changed. `then()` and `reversed()` return a new spec. So one spec is safe to share between many queries and threads, for example as a `static final` field.
- lambdaQL checks the lambda when the spec is used in `orderBy`. Each lambda is analyzed only once.
- Captured values are the values at the moment the lambda was made. A spec in a `static final` field keeps them forever. If the direction must change at run time, use `orderBy((o, by) -> by.sort(key, dir))` (see [4.3](#43-direction)).
- After `orderBy(spec)` you can page as usual: `limit`, `offset`, `toList()`, `first()`.

**(tested)** [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java), region `sort-spec`:

```java
// keep it, for example as a static final field, and use it in many queries
SortSpec<Order> byDescription = SortSpec.of(Order.class, (o, by) -> by.desc(o.getDescription()).asc(o.getId()));
RenderedQuery q = qb.from(Order.class).where(o -> o.isActive()).orderBy(byDescription).toHql();
assertEquals("select e0 from Order e0 where e0.active = true order by e0.description desc nulls last, e0.id asc", q.hql());
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `sort-spec`:

```sql
-- H2
select …
from orders o1_0
where o1_0.active=true
order by o1_0.description desc,o1_0.id

-- MariaDB
select …
from orders o1_0
where o1_0.active=1
order by o1_0.description desc,o1_0.id

-- Oracle
select …
from orders o1_0
where o1_0.active=1
order by o1_0.description desc nulls last,o1_0.id
```

The SQL is the same as for the same sort written in `orderBy((o, by) -> ...)`. Oracle adds `nulls last`, because its own default for `desc` is the opposite (see [4.4](#44-null-ordering-is-the-same-on-every-database)).

**`then()`: more keys after the existing ones.** `a.then(b)` sorts by the keys of `a` first. For rows that are equal, it then sorts by the keys of `b`.
`then()` takes another `SortSpec` or a sort lambda.

**(tested)** [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java), region `sort-spec-then` (in the test entity, `status` and `price` can never be NULL, so there is no `nulls` part):

```java
SortSpec<Order> byStatus = SortSpec.of(Order.class, (o, by) -> by.asc(o.getStatus()));
SortSpec<Order> byPrice = SortSpec.of(Order.class, (o, by) -> by.desc(o.getPrice()));
assertEquals("select e0 from Order e0 order by e0.status asc, e0.price desc",
        qb.from(Order.class).orderBy(byStatus.then(byPrice)).toHql().hql());
// then() also takes a lambda
assertEquals("select e0 from Order e0 order by e0.status asc, e0.id desc",
        qb.from(Order.class).orderBy(byStatus.then((o, by) -> by.desc(o.getId()))).toHql().hql());
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `sort-spec-then` (the first query):

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
order by o1_0.status,o1_0.price desc
```

**`reversed()`: every key the other way.** `spec.reversed()` flips every key. The NULL position flips too: `asc` (NULLs first) becomes `desc` (NULLs last). So the whole row order is exactly backwards.
This is the same as `Direction.reversed()` (see [4.3](#43-direction)). `spec.reversed().reversed()` gives the first order again.

**(tested)** [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java), region `sort-spec-reversed`:

```java
SortSpec<Order> spec = SortSpec.of(Order.class, (o, by) -> by.asc(o.getDescription()).asc(o.getId()));
// every key is reversed, the NULL position too: asc nulls first -> desc nulls last
assertEquals("select e0 from Order e0 order by e0.description desc nulls last, e0.id desc",
        qb.from(Order.class).orderBy(spec.reversed()).toHql().hql());
```

**SQL by database (tested)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `sort-spec-reversed`:

```sql
-- H2, MariaDB
select …
from orders o1_0
order by o1_0.description desc,o1_0.id desc

-- Oracle
select …
from orders o1_0
order by o1_0.description desc nulls last,o1_0.id desc
```

H2 and MariaDB already put NULLs last for `desc`, so they write no extra clause. Oracle adds `nulls last`.

**`SortSpec.ofPath`: a sort from a property name.** Sometimes the sort key comes as text from outside the code, for example from a Spring `Sort` (the Spring module uses `ofPath` for it, see [12.5](12-spring-data-jpa.md#125-spring-sort-and-pageable)).
`SortSpec.ofPath(Order.class, "customer.name", Direction.DESC)` makes a spec from a property path and a `Direction`.

- The path is property names joined with `.`. Every part must be a persistent property: to-one associations or embedded values in the middle, and a basic value at the end.
- Anything else is an error (`IllegalArgumentException` when the spec is used in `orderBy`). So the text can never become other HQL.
- Which names are allowed is decided by the sort policy of the persistence unit (see [the sort policy](#sort-policy) below). Without a policy and without `@Sortable` fields, **any** persistent property is accepted, and lambdaQL logs `LQL-5003` (at `INFO` level by default).
- An association path uses a `left join`, the same as in a sort lambda (rule S8). `then()` joins `ofPath` specs and lambda specs.

**(tested)** [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java), region `sort-spec-path`:

```java
// for sorts chosen outside the code (Spring Sort uses this); only real properties are accepted
SortSpec<Order> byName = SortSpec.ofPath(Order.class, "customer.name", Direction.DESC)
        .then(SortSpec.ofPath(Order.class, "id", Direction.ASC));
assertEquals("select e0 from Order e0 left join e0.customer j1 order by j1.name desc nulls last, e0.id asc",
        qb.from(Order.class).orderBy(byName).toHql().hql());
assertThrows(IllegalArgumentException.class,
        () -> qb.from(Order.class).orderBy(SortSpec.ofPath(Order.class, "price; drop table x", Direction.ASC)));
```

<a id="sort-policy"></a>
### The sort policy: which outside names are allowed

**Implemented and tested** on H2, MariaDB 10.11 and Oracle 23.26 (decision D43).

A sort name that comes from outside the code (a request parameter, a Spring `Sort` or `Pageable`) can name any persistent property. The **sort policy** decides which names are allowed.

- `SortRules<T>` lists the allowed names of one entity. You give each column as a lambda, so the compiler checks it.
- `SortPolicy` holds the rules of the entities and a mode (`SortMode`). You register one policy for each `EntityManagerFactory`, that is, for each persistence unit. Every `QueryBuilder` and repository on that factory uses it.
- `@Sortable` on the fields of an entity gives rules without code (see [Rules from `@Sortable`](#rules-from-sortable) below).

The rules of an entity come from **one** place, in this order: the `SortRules` in the policy (from code or a Spring `SortPolicy` bean), the Spring Boot properties `lambdaql.sort.*` (see [12.5](12-spring-data-jpa.md#sort-policy-with-spring)), the `@Sortable` fields of the entity. Rules from two places are not merged.

**Which sorts are checked.** Only sorts made from strings: `SortSpec.ofPath`, and a Spring `Sort` or `Pageable` (the Spring module uses `ofPath`, see [12.5](12-spring-data-jpa.md#sort-policy-with-spring)).
A sort lambda (`orderBy((o, by) -> ...)`, `SortSpec.of`) is your own code, so it is never checked.

| Rule | Allowed outside name |
|---|---|
| `allow(o -> o.getPrice())` | `price`, the path of the column |
| `allow(o -> o.getCustomer().getName())` | `customer.name`. A path through an association is allowed only when it is listed like this |
| `alias("newest", o -> o.getUpdateAt())` | `newest`, which sorts by `updateAt`. The path `updateAt` itself is then **not** an outside name, so the client never sees the property name |

A rule must be a column of the entity. Any other key is rejected with `LQL-4041` the first time the rules are used (not tested yet).

| `SortMode` | A name that the rules do not allow |
|---|---|
| `OFF` | The sort runs. Nothing is checked |
| `INFO` | The sort runs, and lambdaQL logs `LQL-5003` at `INFO` level once per entity and name. **This is the default:** without a registered policy, lambdaQL uses `INFO`, with rules only from `@Sortable` |
| `WARN` | The same as `INFO`, but the log level is `WARN` |
| `STRICT` | The sort is rejected with `SortNotAllowedException` (`LQL-4040`). An entity that has neither rules nor `@Sortable` fields is rejected for every name |

So, until you register a policy, each outside sort name that no `@Sortable` field allows logs `LQL-5003` once for each entity. Register a policy with `STRICT` to reject unknown names, or with `OFF` to turn the check off.

| Method | What it does |
|---|---|
| `SortPolicy.of(mode, rules...)` | Makes a policy from a mode and the rules of each entity |
| `SortPolicy.register(emf, policy)` | Registers the policy of a persistence unit. It replaces an earlier one |
| `SortPolicy.unregister(emf)` | Removes the policy. The default (`INFO`, rules only from `@Sortable`) applies again |
| `SortPolicy.find(emf)` | Returns the registered policy (`Optional`) |
| `SortPolicy.effective(emf)` | Returns the policy in force: the registered one, else the default |

With Spring Boot, define a `SortPolicy` bean instead of calling `register` (see [12.5](12-spring-data-jpa.md#sort-policy-with-spring)).

**(tested)** [`SortPolicyTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortPolicyTest.java), regions `rules` and `strict`:

```java
SortRules<Order> orderRules = SortRules.of(Order.class)
        .allow(o -> o.getPrice())                        // the name "price"
        .allow(o -> o.getCustomer().getName())           // an association path: allowed only when listed
        .alias("newest", o -> o.getUpdateAt());          // the outside name "newest" sorts by updateAt
SortPolicy.register(emf, SortPolicy.of(SortMode.STRICT, orderRules));
```

In the next examples, `hql(name, direction)` is a helper of the test. It returns `qb.from(Order.class).orderBy(SortSpec.ofPath(Order.class, name, direction)).toHql().hql()`.

**(tested)** [`SortPolicyTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortPolicyTest.java), region `allowed`:

```java
assertEquals("select e0 from Order e0 order by e0.price asc", hql("price", Direction.ASC));
assertEquals("select e0 from Order e0 left join e0.customer j1 order by j1.name asc nulls first", hql("customer.name", Direction.ASC));
// the alias: the client sends "newest", lambdaQL sorts by updateAt
assertEquals("select e0 from Order e0 order by e0.updateAt desc nulls last", hql("newest", Direction.DESC));
```

**(tested)** [`SortPolicyTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortPolicyTest.java), region `rejected` (in `STRICT` mode):

```java
// not in the rules, an unlisted association path, the path behind an alias, an entity without rules
for (String name : new String[]{"description", "customer.grade", "updateAt"}) {
    assertEquals("LQL-4040", assertThrows(SortNotAllowedException.class, () -> hql(name, Direction.ASC)).code());
}
assertThrows(SortNotAllowedException.class,
        () -> qb.from(Item.class).orderBy(SortSpec.ofPath(Item.class, "qty", Direction.ASC)).toHql());
// a sort written as a lambda is code, not outside input: never checked
assertEquals("select e0 from Order e0 order by e0.description asc nulls first",
        qb.from(Order.class).orderBy((o, by) -> by.asc(o.getDescription())).toHql().hql());
```

**(tested)** [`SortPolicyTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortPolicyTest.java), region `warn`:

```java
// INFO (the default) and WARN: the sort runs and LQL-5003 is logged once per entity and name
SortPolicy.register(emf, SortPolicy.of(SortMode.WARN));
assertEquals("select e0 from Order e0 order by e0.description asc nulls first", hql("description", Direction.ASC));
```

<a id="rules-from-sortable"></a>
**Rules from `@Sortable`.** You can also mark the fields or getters of an entity with `@org.lambdaql.Sortable`:

| Annotation | Allowed outside name |
|---|---|
| `@Sortable` on the field `name` | `name` |
| `@Sortable("level")` on the field `grade` | `level`, which sorts by `grade` (an alias) |
| `@Sortable(path = "name")` on the association field `customer` | `customer.name` |

- lambdaQL reads the annotations of the entity in `from(...)`. An annotation on another entity does not allow a path through an association: use `path` on the association field.
- The annotations are used only when the policy has no rules for that entity (from code, a Spring bean or Spring Boot properties).
- They also work without a registered policy, in the default `INFO` mode.

**(tested)** [`SortPolicyTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortPolicyTest.java), region `annotation` (the `STRICT` policy above has rules only for `Order`):

```java
// Customer has no rules in the policy, so its @Sortable fields are the rules:
//   @Sortable private String name;   @Sortable("level") private Integer grade;
assertEquals("select e0 from Customer e0 order by e0.name asc nulls first",
        qb.from(Customer.class).orderBy(SortSpec.ofPath(Customer.class, "name", Direction.ASC)).toHql().hql());
assertEquals("select e0 from Customer e0 order by e0.grade desc nulls last",
        qb.from(Customer.class).orderBy(SortSpec.ofPath(Customer.class, "level", Direction.DESC)).toHql().hql());
assertThrows(SortNotAllowedException.class,
        () -> qb.from(Customer.class).orderBy(SortSpec.ofPath(Customer.class, "id", Direction.ASC)).toHql());
```

<a id="rules-as-strings"></a>
**Rules as strings.** `allowPath(String...)` and `aliasPath(name, path)` take property paths as text. They are meant for configuration: the Spring Boot properties in [12.5](12-spring-data-jpa.md#sort-policy-with-spring) use them.
The paths are checked against the entity the first time the rules are used. In code, prefer `allow` and `alias` with a lambda, because the compiler checks them.

**(tested)** [`SortPolicyTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortPolicyTest.java), region `string-paths`:

```java
// the string forms used by configuration files (lambdaql.sort.allow / lambdaql.sort.alias)
SortPolicy.register(emf, SortPolicy.of(SortMode.STRICT,
        SortRules.of(Order.class).allowPath("price", "customer.name").aliasPath("newest", "updateAt")));
assertEquals("select e0 from Order e0 order by e0.updateAt desc nulls last", hql("newest", Direction.DESC));
assertThrows(SortNotAllowedException.class, () -> hql("description", Direction.ASC));
```

- The check runs when the spec is used in `orderBy`, before the path is read. So in `STRICT` mode, a name that is not in the rules gives `SortNotAllowedException` even when it is not a property at all.
- The rules belong to one entity class. If you pass two `SortRules` for the same class, the last one is used.
- `SortNotAllowedException` is a `LambdaQLException` (see [11. Errors and languages](../11-errors-and-languages.md#exceptions)). lambdaQL has no web dependency. To answer it with HTTP 400, see the example in [12.5](12-spring-data-jpa.md#sort-policy-with-spring).

**Not implemented yet:**

- One text with a name and a direction, such as `"price,desc_nulls_last"` (rule S11). Read the direction with `Direction.parse` (see [4.3](#43-direction)) and the name with `SortSpec.ofPath`.
- **Planned:** the join form of `SortSpec`, keyset paging. (The lambda form `(o, c, by) -> ...` for joins is implemented, see [6.8](06-select-group.md#68-explicit-joins).)

## 4.11 Sorting inside subqueries

**Implemented** (milestone M8-1, with the subqueries of [5. Subqueries](05-subquery.md)). The MariaDB `lateral` rewrite is not implemented yet.

A sort inside a subquery is only kept when it is used with `limit` (top-1 or top-N).
Without `limit`, it does not change the result, so lambdaQL removes it and logs a warning.
See [5. Subqueries](05-subquery.md#59-sorting-inside-a-subquery).

## 4.12 Rules summary (S1 to S14)

| # | Rule | Status |
|---|---|---|
| S1 | A sort is one lambda: `(o, by) -> by...`. For one key: `o -> key` with an optional `Direction`. For joins: `(o, c, by) -> by...` (`by` is always last) | Implemented, also the join form `(o, c, by)` (tested) |
| S2 | A key is `Comparable`. An entity cannot be a key (compile error). A key must use the entity | Implemented |
| S3 | Direction comes from `asc` / `desc` or `sort(key, Direction)`. `asc(key)`/`desc(key)` (`ASC`/`DESC`) use the NULL policy; `asc(key, nullsFirst)`, `desc(key, nullsFirst)` and `*_NULLS_*` are used as written | Implemented |
| S4 | The body is only a straight chain of `by` calls. No `if`, ternary or loop | Implemented |
| S5 | `orderBy` once per query. No `where` after `orderBy`. No `orderBy` after `limit` (compile error) | Implemented |
| S6 | Reuse with `SortSpec` (`of`, `ofPath`, `then`, `reversed`, `orderBy(spec)`). Outside strings are checked by the sort policy of the persistence unit (`SortRules`, `SortPolicy`, `SortMode`, see [4.10](#sort-policy)) | `SortSpec` and the sort policy implemented and tested. The join form of `SortSpec` is not implemented yet |
| S7 | Keys: columns, to-one paths, `+ - *`, widening. No value branches | Implemented. Columns and to-one paths are tested |
| S8 | Association paths that are used only in `orderBy` use a `left join`, so rows without the association are kept. If `where` uses the same path, the sort uses the `where` join | Implemented |
| S9 | With paging, the root `@Id asc` is added if the keys do not contain it. Collection fetch join + paging is rejected | Implemented for the root id and the ids of explicitly joined entities (tested). The fetch join check is not implemented yet |
| S10 | The analysis is cached per lambda. A captured `Direction` is read on every call | Implemented |
| S11 | Spring `Sort.Order` maps 1:1 to `Direction` (Spring module, see [12.5](12-spring-data-jpa.md#125-spring-sort-and-pageable)). Its property names go through the sort policy, and an alias is passed on as it is | Spring `Sort` implemented and tested (`ignoreCase` not supported yet), also with the sort policy. Reading one text such as `"price,desc_nulls_last"` is not implemented yet |
| S12 | Join queries use the same shape: `(o, c, by)`, `(o, c, i, by)` | Implemented (tested, see [6.8](06-select-group.md#68-explicit-joins)) |
| S13 | Method references only in the one-key short form. A method reference that compiles but has the wrong meaning is rejected (see [9. Java notes](09-java-notes.md#99-method-references)) | Implemented (getter references) |
| S14 | Subquery sorts: kept with `limit`, removed with a warning without `limit`. Database differences are handled for you (see chapter 5) | Implemented (tested). The MariaDB `lateral` rewrite is not implemented yet |

---

[Java track](README.md) | Previous: [3. Conditions](03-where.md) | Next: [5. Subqueries](05-subquery.md) | [Kotlin version of this page](../kotlin/04-order-paging.md)
