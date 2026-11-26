<a id="4-sorting-and-paging-java"></a>
# 4. Sorting and paging (Java)

[Java track](README.md) | [Manual home](../README.md) | Previous: [3. Conditions (`where`)](03-where.md) | Next: [5. Subqueries](05-subquery.md) | [Kotlin version of this page](../kotlin/04-order-paging.md)

Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md)

---

<a id="41-one-lambda-for-the-whole-sort"></a>
## 4.1 One lambda expression for the whole sort

You write the sort as **one lambda expression**. The lambda expression takes the entity and describes the keys and directions with the sort functions of the class `By`.
Chain `desc(...)` and `asc(...)` to add keys.

<!-- region: ManualJavaSortTest.java#sort-overview -->
```java
import static org.lambdaql.query.By.desc;

OrderedSelectQuery<Order> page = qb.from(Order.class)
        .where(o -> o.isActive())
        .orderBy(o -> desc(o.getPrice()).asc(o.getCustomer().getName()).asc(o.getId()))
        .limit(2).offset(2);
assertEquals("select e0 from Order e0 left join e0.customer j1 where e0.active = true"
        + " order by e0.price desc, j1.name asc nulls first, e0.id asc", page.toHql().hql());
List<Order> rows = page.toList();
```

- The sort functions are static methods of `By`: `asc(key)`, `desc(key)`, `asc(key, Nulls.FIRST)`, `desc(key, Nulls.LAST)`, `sort(key, Direction)`.
- lambdaQL does not call the sort lambda expression. It analyzes its bytecode opcodes. The sort functions only describe the order. If you call them outside a sort lambda expression, you get an `IllegalStateException` (`LQL-4046`).

The string in `assertEquals` is the HQL that lambdaQL builds.
The examples in this chapter return rows on H2, MariaDB and Oracle in the same order as the same sort applied in Java.
`price` (`double`), `status` (`short`) and `id` cannot be NULL, so the HQL has no `nulls` part. Keys that can be NULL (`description`, `updateAt`, the customer `name`) get `nulls first` or `nulls last` (see [4.4](#44-null-ordering-is-the-same-on-every-database)).

<!-- region: SortQueryTest.java#sort-basic -->
```java
RenderedQuery q = qb.from(Order.class)
        .where(o -> o.isActive())
        .orderBy(o -> desc(o.getPrice()).asc(o.getId()))
        .toHql();
// price and id can never be NULL, so there is no "nulls" part
assertEquals("select e0 from Order e0 where e0.active = true order by e0.price desc, e0.id asc", q.hql());
```

**SQL by database**

<!-- sql: sort-basic -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.active=true
order by o1_0.price desc,o1_0.id
```

`toHql()` returns the HQL without running the query. `toList()` runs the query.

Example code: [`ManualJavaSortTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaSortTest.java) (`sort-overview`), [`SortQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java) (`sort-basic`).

<a id="42-the-short-forms-one-key"></a>
## 4.2 The short forms (one key)

If there is only one key, you can return just the key without a sort function.

| Java | HQL |
|---|---|
| `orderBy(o -> o.getPrice())` | `order by e0.price asc nulls first` |
| `orderBy(o -> o.getUpdateAt(), Direction.DESC)` | `order by e0.updateAt desc nulls last` |
| `orderBy(Order::getPrice)` | `order by e0.price asc nulls first` |
| `orderBy(Order::getPrice, Direction.DESC)` | `order by e0.price desc nulls last` |

Without a `Direction`, the short forms sort in ascending order (`ASC`).

Use the `Direction` argument only with one key. If you use a sort function together with a `Direction` argument, like `orderBy(o -> desc(o.getPrice()), Direction.ASC)`, it is rejected with `LQL-3069`. If you need to take the direction as an argument, write `orderBy(o -> sort(o.getPrice(), direction))`.

You can use a method reference anywhere one key is used. The method reference must be a getter of the entity, like `Order::getPrice`. Static methods, methods with arguments and references bound to an object are rejected.

In the example entity, `price` is a `double`, so there is no `nulls` part:

<!-- region: SortQueryTest.java#sort-key -->
```java
assertEquals("select e0 from Order e0 order by e0.price asc",
        qb.from(Order.class).orderBy(o -> o.getPrice()).toHql().hql());
assertEquals("select e0 from Order e0 order by e0.price desc",
        qb.from(Order.class).orderBy(Order::getPrice, Direction.DESC).toHql().hql());
```

**SQL by database**

<!-- sql: sort-key -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
order by o1_0.price
```

**SQL by database**

<!-- sql: sort-key#2 -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
order by o1_0.price desc
```

Example code: [`SortQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java) (`sort-key`).

<a id="43-direction"></a>
## 4.3 `Direction`

`Direction` has six values.

| Value | Meaning | NULL position |
|---|---|---|
| `ASC` | Ascending | NULL **first** (default policy) |
| `DESC` | Descending | NULL **last** (default policy) |
| `ASC_NULLS_FIRST` | Ascending | NULL first. The same as `ASC` |
| `ASC_NULLS_LAST` | Ascending | NULL last |
| `DESC_NULLS_FIRST` | Descending | NULL first |
| `DESC_NULLS_LAST` | Descending | NULL last. The same as `DESC` |

`Direction` also has two helper methods.

- `Direction.parse(text)` reads a direction from text such as a request parameter. Case does not matter, and `-`, `_` and spaces are treated the same: `Direction.parse("desc-nulls-last")` is `DESC_NULLS_LAST`. Unknown text throws `IllegalArgumentException`.
- `reversed()` returns the opposite direction. The NULL position is flipped too, so the whole order is reversed: `ASC.reversed()` is `DESC`, and `ASC_NULLS_LAST.reversed()` is `DESC_NULLS_FIRST`.

**To choose the NULL position**, use the enum `Nulls` as the second argument: `asc(key, Nulls.LAST)`, `desc(key, Nulls.FIRST)`.
`Nulls.FIRST` means "NULL first", and `Nulls.LAST` means "NULL last".
Without the second argument, `asc(key)` and `desc(key)` use the default NULL policy (see [4.4](#44-null-ordering-is-the-same-on-every-database)).

| What you write | Same meaning |
|---|---|
| `asc(key)` | `ASC` (NULL first, default policy) |
| `desc(key)` | `DESC` (NULL last, default policy) |
| `asc(key, Nulls.FIRST)` | `ASC_NULLS_FIRST` |
| `asc(key, Nulls.LAST)` | `ASC_NULLS_LAST` |
| `desc(key, Nulls.FIRST)` | `DESC_NULLS_FIRST` |
| `desc(key, Nulls.LAST)` | `DESC_NULLS_LAST` |

`description` is a `String` that can be NULL, and `id` cannot be NULL:

<!-- region: SortQueryTest.java#sort-nulls -->
```java
// default: asc puts NULL first, desc puts NULL last (the same on every database)
assertEquals("select e0 from Order e0 order by e0.description asc nulls first, e0.id asc",
        qb.from(Order.class).orderBy(o -> asc(o.getDescription()).asc(o.getId())).toHql().hql());
// the second argument chooses: true = NULL first, false = NULL last
assertEquals("select e0 from Order e0 order by e0.description asc nulls last, e0.id asc",
        qb.from(Order.class).orderBy(o -> asc(o.getDescription(), Nulls.LAST).asc(o.getId())).toHql().hql());
assertEquals("select e0 from Order e0 order by e0.description desc nulls first, e0.id asc",
        qb.from(Order.class).orderBy(o -> desc(o.getDescription(), Nulls.FIRST).asc(o.getId())).toHql().hql());
```

**SQL by database**

<!-- sql: sort-nulls -->
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

**SQL by database**

<!-- sql: sort-nulls#2 -->
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

**SQL by database**

<!-- sql: sort-nulls#3 -->
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

You can see the NULL policy in the SQL. H2 and MariaDB leave out the clause when it is the same as their default. Oracle's default is the opposite, so it writes `asc nulls first` / `desc nulls last`. MariaDB has no `NULLS` syntax, so a position that is not the default is sorted first by `case when … is null then … end`.

**When the direction comes from a variable**, use `sort(key, direction)` with a captured `Direction`.

<!-- region: ManualJavaSortTest.java#sort-direction -->
```java
// the second argument: false = NULL last
OrderedSelectQuery<Order> nullsLast = qb.from(Order.class)
        .orderBy(o -> desc(o.getDescription(), Nulls.LAST).asc(o.getId()));
assertEquals("select e0 from Order e0 order by e0.description desc nulls last, e0.id asc",
        nullsLast.toHql().hql());
// a direction from a variable: the ternary runs outside the lambda
Direction dir = newestFirst ? Direction.DESC : Direction.ASC;
OrderedSelectQuery<Order> newest = qb.from(Order.class)
        .orderBy(o -> sort(o.getUpdateAt(), dir).asc(o.getId()));   // dir is read on every call
assertEquals("select e0 from Order e0 order by e0.updateAt desc nulls last, e0.id asc", newest.toHql().hql());
```

The ternary operator `newestFirst ? ... : ...` is fine here, because it runs **outside** the lambda expression.

<!-- region: SortQueryTest.java#sort-dynamic -->
```java
Direction dir = Direction.parse(text);           // for example from a request parameter
OrderedSelectQuery<Order> q = qb.from(Order.class)
        .orderBy(o -> sort(o.getDescription(), dir).asc(o.getId()));
```

**SQL by database**

<!-- sql: sort-dynamic -->
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

**SQL by database**

<!-- sql: sort-dynamic#2 -->
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

If `text` is `"desc"`, the HQL is `... order by e0.description desc nulls last, e0.id asc`.
If `text` is `"asc_nulls_last"`, it is `... order by e0.description asc nulls last, e0.id asc`.
The lambda expression is analyzed only once. `dir` is read each time the query runs. If the direction is `null`, an exception is thrown when the query runs.

Example code: [`SortQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java) (`sort-nulls`, `sort-dynamic`), [`ManualJavaSortTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaSortTest.java) (`sort-direction`).

<a id="44-null-ordering-is-the-same-on-every-database"></a>
## 4.4 NULL ordering is the same on every database

Each database puts NULL in a different place.

| | H2 | MariaDB | Oracle |
|---|---|---|---|
| `ASC` default | NULL first | NULL first | NULL **last** |
| `DESC` default | NULL last | NULL last | NULL **first** |
| `NULLS FIRST/LAST` in SQL | Supported | **Not supported (syntax error)** | Supported |

lambdaQL solves this with one policy: **NULL is the smallest value.**

- `ASC` = `ASC_NULLS_FIRST`
- `DESC` = `DESC_NULLS_LAST`

This is the same as Java's `Comparator.nullsFirst(Comparator.naturalOrder())`.
With this policy, all three databases return the same order (for numbers and dates).

**How lambdaQL writes it.** For keys that can be NULL, lambdaQL always writes the NULL position in the HQL. Example: `e0.description asc nulls first`.
Keys that cannot be NULL (primitive columns such as `double`, or the `@Id`) have no `nulls` part.
A column after an association becomes NULL when there is no association (there can be orders without a customer), so it gets the part. It does not get the part when every association step of the path is required (`optional = false`) and the value cannot be NULL.

Then Hibernate writes the SQL for each database. It leaves out the clause when it is the same as the database default.
MariaDB has no `NULLS` syntax, so Hibernate writes a position that is not the default as a `CASE` expression.

The generated SQL:

| What you write | H2 | MariaDB | Oracle |
|---|---|---|---|
| `asc(key)`, `desc(key)` (`ASC`, `DESC`) | No extra clause (already the default) | No extra clause | Adds `nulls first` / `nulls last` |
| A NULL position that is the same as the database default (`asc(key, Nulls.X)`, `desc(key, Nulls.X)`, `*_NULLS_*`) | No extra clause | No extra clause | No extra clause |
| A NULL position that is **not** the database default | `nulls first/last` | Hibernate writes `case when x is null ...` | `nulls first/last` |
| A key that cannot be NULL (any direction) | No extra clause | No extra clause | No extra clause |

> [!TIP]
> On MariaDB, `asc(key, Nulls.LAST)` (`ASC_NULLS_LAST`) and `desc(key, Nulls.FIRST)` (`DESC_NULLS_FIRST`) become a `CASE` expression. The database cannot use an index for this sort. When possible, use `asc(key)` / `desc(key)`.

<a id="45-what-a-sort-key-can-be"></a>
## 4.5 What a sort key can be

| Key | Allowed | Notes |
|---|---|---|
| A column: `o.getPrice()` | Allowed | |
| A to-one path: `o.getCustomer().getName()` | Allowed | `left join` (an inner join for a required association), see below |
| A `@Lob` column | Allowed | Oracle rejects it. `LQL-5010` at `DEBUG` level, see below |
| An enum column with `@Enumerated(EnumType.STRING)` | Allowed | Sorted by name (not by declaration order). `LQL-5011` at `DEBUG` level, see below |
| Arithmetic: `o.getPrice() * 2`, `+`, `-` | Allowed | See the example below |
| A widening conversion (`short` to `long` and others) | Allowed | Sorted by the column without a conversion. See the example below |
| A value that does not use the entity: `asc(1)`, a captured value | **Rejected** | See [4.6](#46-rules-for-the-lambda-body) |
| A function on a column: `lower(name)` | Not supported | |

Even if an entity class implements `Comparable`, lambdaQL rejects it as a key when it reads the lambda expression.

An arithmetic key is sorted by the expression as it is. A widening conversion (`(long) o.getStatus()`) disappears from the HQL, and the sort is by the column.

<!-- region: ManualJavaSortTest.java#sort-arith -->
```java
// arithmetic on columns
OrderedSelectQuery<Order> doubled = qb.from(Order.class)
        .orderBy(o -> desc(o.getPrice() * 2).asc(o.getId()));
assertEquals("select e0 from Order e0 order by (e0.price * 2.0) desc, e0.id asc", doubled.toHql().hql());
// a widening conversion (short to long)
OrderedSelectQuery<Order> widened = qb.from(Order.class)
        .orderBy(o -> asc((long) o.getStatus()).asc(o.getId()));
assertEquals("select e0 from Order e0 order by e0.status asc, e0.id asc", widened.toHql().hql());
```

**Association paths use `left join`.** The simple HQL path `order by o.customer.name` becomes an inner join on all three databases. Orders without a customer disappear.
lambdaQL adds a `left join` instead. Orders without a customer stay in the result. For those orders `j1.name` is NULL, so they come first with `asc`.

<!-- region: SortQueryTest.java#sort-path -->
```java
RenderedQuery q = qb.from(Order.class)
        .orderBy(o -> asc(o.getCustomer().getName()).asc(o.getId()))
        .toHql();
// a path used only for sorting is a LEFT JOIN: the order without a customer stays
assertEquals("select e0 from Order e0 left join e0.customer j1 order by j1.name asc nulls first, e0.id asc", q.hql());
```

**SQL by database**

<!-- sql: sort-path -->
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

**Required associations use an inner join.** An association mapped with `optional = false` (for example `@ManyToOne(optional = false)`) always has a target, so an inner join does not lose rows.
So such an association uses `join` even in a path used only in the sort or the select. Other associations use `left join` as above.
If every association step of the path is required and the value cannot be NULL, the sort does not get `nulls first/last` either.

`@ManyToOne(optional = false) Order order` of the example entity `OrderLine`:

<!-- region: MappingRulesTest.java#required-association -->
```java
// order is optional = false: a sort or select path through it cannot lose rows, so it is an inner join
OrderedSelectQuery<OrderLine> sorted = qb.from(OrderLine.class).orderBy(l -> asc(l.getOrder().getPrice()));
assertEquals("select e0 from OrderLine e0 join e0.order j1 order by j1.price asc", sorted.toHql().hql());
ValueQuery<Double> values = qb.from(OrderLine.class).select(l -> l.getOrder().getPrice());
assertEquals("select j1.price from OrderLine e0 join e0.order j1", values.toHql().hql());
// an association that can be missing keeps the left join (Order.customer)
assertEquals("select e0 from Order e0 left join e0.customer j1 order by j1.name asc nulls first",
        qb.from(Order.class).orderBy(o -> asc(o.getCustomer().getName())).toHql().hql());
```

**Sorting by a `@Lob` column.** When you use a `@Lob` column in `orderBy`, `distinct` or `groupBy`, H2 and MariaDB run it, but Oracle rejects it (ORA-22848, ORA-22849, measured). An `=` comparison works on all three databases.
lambdaQL does not change the query. It logs `LQL-5010` at `DEBUG` level, once per column and use.

`OrderLine.memo` is a `@Lob`:

<!-- region: MappingRulesTest.java#lob -->
```java
// memo is @Lob: H2 and MariaDB sort and group it, Oracle rejects it (ORA-22848, ORA-22849).
// lambdaQL keeps the query and logs LQL-5010 at DEBUG
qb.from(OrderLine.class).orderBy(l -> asc(l.getMemo())).toHql();
qb.from(OrderLine.class).groupBy(l -> l.getMemo()).select(l -> l.getMemo()).toHql();
qb.from(OrderLine.class).select(l -> l.getMemo()).distinct().toHql();
```

**Sorting by an `EnumType.STRING` enum.** For an enum column mapped with `@Enumerated(EnumType.STRING)`, the database sorts by the string order of the constant **names**. Java's enum comparison follows the declaration order, so the result order can differ.
lambdaQL does not change the query. It logs `LQL-5011` at `DEBUG` level, once per column. `ORDINAL` (or no `@Enumerated`) sorts by declaration order, so it is not logged.

<!-- region: MappingRulesTest.java#enum-string-sort -->
```java
// kind is @Enumerated(EnumType.STRING): the database sorts by name, Java by declaration order (LQL-5011, DEBUG)
assertEquals("select e0 from Order e0 order by e0.kind asc nulls first",
        qb.from(Order.class).orderBy(o -> asc(o.getKind())).toHql().hql());
```

The alias is shared with `where`. If `where` has already joined the same path, the sort uses that join.
The condition below is true only for orders that have a customer, so this join is an inner join, and orders without a customer are already left out (see [3.10](03-where.md#310-association-paths)).

<!-- region: SortQueryTest.java#sort-path-where -->
```java
String name = "Kim";
RenderedQuery q = qb.from(Order.class)
        .where(o -> o.getCustomer().getName().equals(name))
        .orderBy(o -> desc(o.getCustomer().getName()).desc(o.getPrice()))
        .toHql();
// the condition already joined the customer, so the sort uses the same inner join
assertEquals("select e0 from Order e0 join e0.customer j1 where j1.name = :p1"
        + " order by j1.name desc nulls last, e0.price desc", q.hql());
```

**SQL by database**

<!-- sql: sort-path-where -->
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

Example code: [`ManualJavaSortTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaSortTest.java) (`sort-arith`), [`SortQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java) (`sort-path`, `sort-path-where`), [`MappingRulesTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/MappingRulesTest.java) (`required-association`, `lob`, `enum-string-sort`).

<a id="46-rules-for-the-lambda-body"></a>
## 4.6 Rules for the lambda expression body

The body must be **one key or a simple chain of `By` functions**.

<!-- region: ManualJavaSortTest.java#sort-body -->
```java
// allowed: a simple chain of by calls
assertEquals("select e0 from Order e0 order by e0.price desc, e0.id asc",
        qb.from(Order.class).orderBy(o -> desc(o.getPrice()).asc(o.getId())).toHql().hql());
// rejected: sort functions chosen by a condition (a condition inside one key is allowed)
LambdaQLException choice = assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).orderBy(o -> flag ? desc(o.getPrice()) : asc(o.getId())));
assertEquals("LQL-2023", choice.code());
// rejected: a key without the entity
LambdaQLException constant = assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).orderBy(o -> asc(1)));
assertEquals("LQL-3031", constant.code());
```

lambdaQL checks this when `orderBy` is called and throws a `LambdaQLException` (for example, `LQL-2023` for a sort function chosen by a condition, and `LQL-3031` for a key that does not use the entity).
Every key must use the entity. A key made only of constants or captured values is rejected.
A ternary operator inside a key value (`asc(o.isActive() ? 0 : 1)`) becomes `case when` (see [3.5.1](03-where.md#351-values-chosen-by-a-condition)).

If you need to change the direction at run time, pass it as a variable: `sort(key, dir)`. If you need to change the key itself, build the query separately in two branches of normal Java code.

Example code: [`ManualJavaSortTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaSortTest.java) (`sort-body`).

<a id="47-order-of-calls"></a>
## 4.7 Order of calls

Write a query in the order `from(...).where(...).orderBy(...).limit(...).offset(...)`.
If you use only `limit` or `offset` without `orderBy`, no `order by` is added (see [4.8](#48-paging)).

<a id="48-paging"></a>
## 4.8 Paging

<!-- region: ManualJavaSortTest.java#page -->
```java
OrderedSelectQuery<Order> page = qb.from(Order.class)
        .where(o -> o.getStatus() == 1)
        .orderBy(o -> desc(o.getPrice()))
        .limit(2)       // page size
        .offset(2);     // skip 2 rows (page 2)
assertEquals("select e0 from Order e0 where e0.status = 1 order by e0.price desc, e0.id asc",
        page.toHql().hql());
List<Order> rows = page.toList();
```

`limit` and `offset` are not written into the HQL. They are passed to Hibernate (`setMaxResults` / `setFirstResult`). The sort keys have no id, so lambdaQL added `e0.id asc` (see [the next section](#tie-breaker-automatic-id)).

<!-- region: SortQueryTest.java#sort-page -->
```java
OrderedSelectQuery<Order> page = qb.from(Order.class)
        .orderBy(o -> asc(o.getStatus()))
        .limit(2)
        .offset(2);
// status has ties, so the id is added as the last key and rows do not move between pages
assertEquals("select e0 from Order e0 order by e0.status asc, e0.id asc", page.toHql().hql());
List<Order> rows = page.toList();
```

**SQL by database**

<!-- sql: sort-page -->
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

The paging SQL is different on each database. H2 and Oracle take `offset ? rows fetch first ? rows only`, and MariaDB takes `limit ?,?`. Without an offset, only `fetch first ? rows only` or `limit ?` is written (see `page-only` below).

`limit` and `offset` take values of 0 or more. A negative value throws `IllegalArgumentException`.

lambdaQL does not write `LIMIT` in the HQL. Hibernate writes the correct SQL for each database.

| Database | SQL |
|---|---|
| H2 | `offset ? rows fetch first ? rows only` |
| MariaDB | `limit ?, ?` |
| Oracle | `offset ? rows fetch first ? rows only` |

**Paging without `orderBy`.** If you call `limit` and `offset` without `orderBy`, no `order by` is added. The database decides which rows come on which page. To split pages in a stable way, use `orderBy`.

<!-- region: SortQueryTest.java#page-only -->
```java
// paging without orderBy: no order by, so which rows come is up to the database; use orderBy for stable pages
RenderedQuery q = qb.from(Order.class).where(o -> o.isActive()).limit(2).toHql();
assertEquals("select e0 from Order e0 where e0.active = true", q.hql());
```

**SQL by database**

<!-- sql: page-only -->
```sql
-- H2, Oracle
select …
from orders o1_0
where o1_0.active=true
order by o1_0.id
fetch first ? rows only

-- MariaDB
select …
from orders o1_0
where o1_0.active=true
order by o1_0.id
limit ?
```

**The first row and the total count.**

- `orderBy(...).first()` returns the first row in that order as an `Optional`. It reads only one row. The `offset` is kept: `orderBy(...).offset(1).first()` returns the second row.
- `count()` counts all rows that match the condition. For a paged screen, keep the query of the where stage in a variable. Call `count()` on that variable to get the total, then call `orderBy(...).limit(...).toList()` to get the page.

For details, see [Getting the result](02-getting-started.md#getting-the-result).

<a id="tie-breaker-automatic-id"></a>
### Tie-breaker (automatic `id`)

At the end of the HQL above is `e0.id asc`, which lambdaQL added. The reasons are:

- Many rows can have the same `status` or `price`. The database can return such rows **in any order**. Oracle really does this (measured).
- Then the same row can appear twice on pages 2 and 3, or a row can be missing.

Rule: **when you use `orderBy` together with paging and the sort keys have no id, lambdaQL adds the root `@Id asc` as the last key.**

- It is added only once. If the keys already have the id (for example `desc(o.getId())`), it is not added.
- It is not added when the sort keys have a unique column of the main entity that cannot be NULL. This covers `@Column(unique = true)` with `nullable = false`, Hibernate `@NaturalId` and `@Id`, because this key alone sets the order of all rows.
  A unique column that can be NULL does not count, because NULL can be in many rows.
- Without `orderBy`, or without `limit` and `offset`, nothing is added.
- If there are explicit joins (`join`, `leftJoin`, `crossJoin`), the ids of all joined entities are also added, in join order (see [6.8](06-select-group.md#68-explicit-joins)).

`OrderLine.code` is `@Column(unique = true, nullable = false)`:

<!-- region: MappingRulesTest.java#unique-key -->
```java
// code is @Column(unique = true, nullable = false): it orders every row, so paging adds no id tie-breaker
assertEquals("select e0 from OrderLine e0 order by e0.code asc",
        qb.from(OrderLine.class).orderBy(l -> asc(l.getCode())).limit(2).toHql().hql());
// a key that is not unique still gets the id tie-breaker
assertEquals("select e0 from OrderLine e0 join e0.order j1 order by j1.price asc, e0.id asc",
        qb.from(OrderLine.class).orderBy(l -> asc(l.getOrder().getPrice())).limit(2).toHql().hql());
```

Example code: [`ManualJavaSortTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaSortTest.java) (`page`), [`SortQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java) (`sort-page`, `page-only`), [`MappingRulesTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/MappingRulesTest.java) (`unique-key`).

<a id="49-strings-and-special-values"></a>
## 4.9 Strings and special values

- String order follows the database collation. MariaDB `_ci` ignores case. Oracle uses `NLS_SORT`, which follows the JVM locale (for example, `de_DE` gives German order). For Oracle, `NLS_SORT=BINARY` is recommended.
- Oracle stores `''` as NULL, so an empty string sorts like NULL.
- `NaN` sorts differently on each database.

<a id="410-reusable-and-external-sorts"></a>
## 4.10 Reusable and external sorts

**`SortSpec`: a sort you can keep.** A `SortSpec` is a sort that you keep in a variable or field and use in many queries.
Create it with `SortSpec.of(Order.class, o -> desc(...))`. The lambda expression is the same as the lambda expression of `orderBy(o -> ...)`, so the same rules apply (see [4.6](#46-rules-for-the-lambda-body)).
Then pass it to `orderBy(spec)`.

- A `SortSpec` cannot be changed. `then()` and `reversed()` return a new spec. So many queries and threads can safely share one spec. For example, you can keep it in a `static final` field.
- lambdaQL checks the lambda expression when the spec is used in `orderBy`. Each lambda expression is analyzed only once.
- Captured values are the values at the moment the lambda expression was created. A spec in a `static final` field keeps those values. If you need to change the direction at run time, use `orderBy(o -> sort(key, dir))` (see [4.3](#43-direction)).
- You can page after `orderBy(spec)` too: `limit`, `offset`, `toList()`, `first()`.

<!-- region: SortQueryTest.java#sort-spec -->
```java
// keep it, for example as a static final field, and use it in many queries
SortSpec<Order> byDescription = SortSpec.of(Order.class, o -> desc(o.getDescription()).asc(o.getId()));
RenderedQuery q = qb.from(Order.class).where(o -> o.isActive()).orderBy(byDescription).toHql();
assertEquals("select e0 from Order e0 where e0.active = true order by e0.description desc nulls last, e0.id asc", q.hql());
```

**SQL by database**

<!-- sql: sort-spec -->
```sql
-- H2, MariaDB
select …
from orders o1_0
where o1_0.active=true
order by o1_0.description desc,o1_0.id

-- Oracle
select …
from orders o1_0
where o1_0.active=true
order by o1_0.description desc nulls last,o1_0.id
```

The SQL is the same as when you write the same sort with `orderBy(o -> ...)`. Oracle's default for `desc` is the opposite, so it adds `nulls last` (see [4.4](#44-null-ordering-is-the-same-on-every-database)).

**`then()`: add keys after the existing keys.** `a.then(b)` sorts by the keys of `a` first. Rows with the same values are then sorted by the keys of `b`.
`then()` takes another `SortSpec` or a sort lambda expression.

In the example entity, `status` and `price` cannot be NULL, so there is no `nulls` part:

<!-- region: SortQueryTest.java#sort-spec-then -->
```java
SortSpec<Order> byStatus = SortSpec.of(Order.class, o -> asc(o.getStatus()));
SortSpec<Order> byPrice = SortSpec.of(Order.class, o -> desc(o.getPrice()));
assertEquals("select e0 from Order e0 order by e0.status asc, e0.price desc",
        qb.from(Order.class).orderBy(byStatus.then(byPrice)).toHql().hql());
// then() also takes a lambda
assertEquals("select e0 from Order e0 order by e0.status asc, e0.id desc",
        qb.from(Order.class).orderBy(byStatus.then(o -> desc(o.getId()))).toHql().hql());
```

**SQL by database**

<!-- sql: sort-spec-then -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
order by o1_0.status,o1_0.price desc
```

**`reversed()`: reverse all keys.** `spec.reversed()` flips every key. The NULL position is flipped too: `asc` (NULL first) becomes `desc` (NULL last). So the whole row order is exactly reversed.
This is the same as `Direction.reversed()` (see [4.3](#43-direction)). `spec.reversed().reversed()` gives back the original order.

<!-- region: SortQueryTest.java#sort-spec-reversed -->
```java
SortSpec<Order> spec = SortSpec.of(Order.class, o -> asc(o.getDescription()).asc(o.getId()));
// every key is reversed, the NULL position too: asc nulls first -> desc nulls last
assertEquals("select e0 from Order e0 order by e0.description desc nulls last, e0.id desc",
        qb.from(Order.class).orderBy(spec.reversed()).toHql().hql());
```

**SQL by database**

<!-- sql: sort-spec-reversed -->
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

H2 and MariaDB already put NULL last with `desc`, so they write no extra syntax. Oracle adds `nulls last`.

**`SortSpec.ofPath`: a sort built from property names.** Sometimes the sort keys come from outside the code as strings. Spring `Sort` is an example, and the Spring module converts a Spring `Sort` with `ofPath` (see [12.5](12-spring-data-jpa.md#125-spring-sort-and-pageable)).
`SortSpec.ofPath(Order.class, "customer.name", Direction.DESC)` builds a spec from a property path and a `Direction`.

- The path is a string of property names joined with `.`. Each part must be a persistent property. To-one associations or embedded values come in the middle, and a basic value comes at the end.
- Any other path is an error (`IllegalArgumentException` when the spec is passed to `orderBy`). So a string is never turned into different HQL.
- The sort policy of the persistence unit decides which names are allowed (see [Sort policy](#sort-policy) below).
  With no policy and no `@Sortable` field, **any** persistent property is accepted, and lambdaQL logs `LQL-5003` (the default level is `INFO`).
- An association path uses a `left join` (an inner join for a required association), the same as a sort lambda expression. You can chain an `ofPath` spec and a lambda expression spec with `then()`.

<!-- region: SortQueryTest.java#sort-spec-path -->
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
### Sort policy: allowed outside names

**Implemented**.

A sort name that comes from outside the code (a request parameter, a Spring `Sort` or `Pageable`) can point to any persistent property.
The **sort policy** decides which names are allowed.

- `SortRules<T>` is the list of allowed names for one entity. Each column is given with a lambda expression.
- `SortPolicy` holds the rules per entity and a mode (`SortMode`). You register one policy per `EntityManagerFactory`, that is, per persistence unit. Every `QueryBuilder` and repository that uses that factory uses this policy.
- With `@Sortable` on entity fields, you can give rules without code (see [`@Sortable` rules](#rules-from-sortable) below).

The rules of an entity come from **one place only**, in this order: the `SortRules` of the policy (code or a Spring `SortPolicy` bean), the Spring Boot properties `lambdaql.sort.*` (see [12.5](12-spring-data-jpa.md#sort-policy-with-spring)), and the `@Sortable` fields of the entity.
Rules from two places are not merged.

**What is checked.** Only sorts built from strings are checked: `SortSpec.ofPath`, and Spring `Sort` or `Pageable` (the Spring module uses `ofPath`, see [12.5](12-spring-data-jpa.md#sort-policy-with-spring)).
Sort lambda expressions (`orderBy(o -> ...)`, `SortSpec.of`) are developer code, so they are not checked.

| Rule | Allowed outside name |
|---|---|
| `allow(o -> o.getPrice())` | `price` (the path of the column) |
| `allow(o -> o.getCustomer().getName())` | `customer.name`. A path through an association is allowed only when you name it like this |
| `alias("newest", o -> o.getUpdateAt())` | `newest`. The sort is by `updateAt`. The path `updateAt` itself is **not** an outside name here, so the property name is not shown to the client |

A rule must be a column of the entity.
Any other key is rejected with `LQL-4041` when the rule is first used.

<!-- region: ManualJavaSortTest.java#rule-not-column -->
```java
// a rule that is not a column of the entity is rejected when the rules are first used
SortPolicy.register(emf, SortPolicy.of(SortMode.STRICT,
        SortRules.of(Order.class).allow(o -> o.getPrice() * 2)));
LambdaQLException e = assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).orderBy(SortSpec.ofPath(Order.class, "price", Direction.ASC)).toHql());
assertEquals("LQL-4041", e.code());
```

| `SortMode` | What happens to a name that the rules do not allow |
|---|---|
| `OFF` | The sort runs. Nothing is checked |
| `INFO` | The sort runs, and `LQL-5003` is logged at `INFO` level, once per entity and name. **This is the default:** without a registered policy, `INFO` is used, and the rules come only from `@Sortable` |
| `WARN` | The same as `INFO`, but the log level is `WARN` |
| `STRICT` | Rejected with `SortNotAllowedException` (`LQL-4040`). For an entity with no rules and no `@Sortable` fields, every name is rejected |

So before you register a policy, `LQL-5003` is logged once per entity for each outside sort name that `@Sortable` does not allow.
To reject unknown names, register a policy with `STRICT`. To turn off the check, register one with `OFF`.

| Method | What it does |
|---|---|
| `SortPolicy.of(mode, rules...)` | Creates a policy from a mode and rules per entity |
| `SortPolicy.register(emf, policy)` | Registers the policy of a persistence unit. Replaces the previous policy |
| `SortPolicy.unregister(emf)` | Removes the policy. Goes back to the default (`INFO`, only `@Sortable` rules) |
| `SortPolicy.find(emf)` | Returns the registered policy (`Optional`) |
| `SortPolicy.effective(emf)` | Returns the policy in effect. The default if no policy is registered |

In Spring Boot, define a `SortPolicy` bean instead of calling `register` (see [12.5](12-spring-data-jpa.md#sort-policy-with-spring)).

<!-- region: SortPolicyTest.java#rules -->
```java
SortRules<Order> orderRules = SortRules.of(Order.class)
        .allow(o -> o.getPrice())                        // the name "price"
        .allow(o -> o.getCustomer().getName())           // an association path: allowed only when listed
        .alias("newest", o -> o.getUpdateAt());          // the outside name "newest" sorts by updateAt
```

Put the rules into a policy and register it for the persistence unit.

<!-- region: SortPolicyTest.java#strict -->
```java
SortPolicy.register(emf, SortPolicy.of(SortMode.STRICT, orderRules));
```

`hql(name, direction)` in the next example is a helper method of the example.
It returns `qb.from(Order.class).orderBy(SortSpec.ofPath(Order.class, name, direction)).toHql().hql()`.

<!-- region: SortPolicyTest.java#allowed -->
```java
assertEquals("select e0 from Order e0 order by e0.price asc", hql("price", Direction.ASC));
assertEquals("select e0 from Order e0 left join e0.customer j1 order by j1.name asc nulls first", hql("customer.name", Direction.ASC));
// the alias: the client sends "newest", lambdaQL sorts by updateAt
assertEquals("select e0 from Order e0 order by e0.updateAt desc nulls last", hql("newest", Direction.DESC));
```

`STRICT` mode:

<!-- region: SortPolicyTest.java#rejected -->
```java
// not in the rules, an unlisted association path, the path behind an alias, an entity without rules
for (String name : new String[]{"description", "customer.grade", "updateAt"}) {
    assertEquals("LQL-4040", assertThrows(SortNotAllowedException.class, () -> hql(name, Direction.ASC)).code());
}
assertThrows(SortNotAllowedException.class,
        () -> qb.from(Item.class).orderBy(SortSpec.ofPath(Item.class, "qty", Direction.ASC)).toHql());
// a sort written as a lambda is code, not outside input: never checked
assertEquals("select e0 from Order e0 order by e0.description asc nulls first",
        qb.from(Order.class).orderBy(o -> asc(o.getDescription())).toHql().hql());
```

<!-- region: SortPolicyTest.java#warn -->
```java
// INFO (the default) and WARN: the sort runs and LQL-5003 is logged once per entity and name
SortPolicy.register(emf, SortPolicy.of(SortMode.WARN));
assertEquals("select e0 from Order e0 order by e0.description asc nulls first", hql("description", Direction.ASC));
```

<a id="rules-from-sortable"></a>
**`@Sortable` rules.** `@org.lambdaql.Sortable` on a field or getter of the entity also becomes a rule.

| Annotation | Allowed outside name |
|---|---|
| `@Sortable` on the field `name` | `name` |
| `@Sortable("level")` on the field `grade` | `level`. The sort is by `grade` (alias) |
| `@Sortable(path = "name")` on the association field `customer` | `customer.name` |

- lambdaQL reads only the annotations of the entity given to `from(...)`. Annotations on another entity do not allow an association path, so give `path` on the association field.
- The annotations are used only when the policy has no rules for that entity (code, Spring bean, Spring Boot properties).
- They also work in the default `INFO` mode without a registered policy.

The `STRICT` policy above has rules only for `Order`:

<!-- region: SortPolicyTest.java#annotation -->
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
**Rules as strings.** `allowPath(String...)` and `aliasPath(name, path)` take property paths as strings. They are for configuration files, and the Spring Boot properties in [12.5](12-spring-data-jpa.md#sort-policy-with-spring) use this form.
The paths are checked against the entity when the rule is first used. In code, use the lambda expression forms `allow` and `alias`.

<!-- region: SortPolicyTest.java#string-paths -->
```java
// the string forms used by configuration files (lambdaql.sort.allow / lambdaql.sort.alias)
SortPolicy.register(emf, SortPolicy.of(SortMode.STRICT,
        SortRules.of(Order.class).allowPath("price", "customer.name").aliasPath("newest", "updateAt")));
assertEquals("select e0 from Order e0 order by e0.updateAt desc nulls last", hql("newest", Direction.DESC));
assertThrows(SortNotAllowedException.class, () -> hql("description", Direction.ASC));
```

- The check happens when the spec is passed to `orderBy`, before the path is read.
  So in `STRICT` mode, a name that is not in the rules raises `SortNotAllowedException` even if it is not a property.
- Rules belong to one entity class. If you pass `SortRules` for the same class twice, the last one is used.
- `SortNotAllowedException` is a `LambdaQLException` (see [11. Errors and languages](../11-errors-and-languages.md#exceptions)).
  lambdaQL has no web dependency. For an example of an HTTP 400 response, see [12.5](12-spring-data-jpa.md#sort-policy-with-spring).

Read the name and the direction of an outside string separately. Read the direction with `Direction.parse` (see [4.3](#43-direction)), and the name with `SortSpec.ofPath`.

Example code: [`SortQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java) (`sort-spec`, `sort-spec-then`, `sort-spec-reversed`, `sort-spec-path`), [`ManualJavaSortTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaSortTest.java) (`rule-not-column`), [`SortPolicyTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortPolicyTest.java) (`rules`, `strict`, `allowed`, `rejected`, `warn`, `annotation`, `string-paths`).

<a id="411-sorting-inside-subqueries"></a>
## 4.11 Sorting inside subqueries

A sort inside a subquery is kept only when it is used with `limit` (top-1 or top-N).
Without `limit`, it does not change the result, so lambdaQL removes the sort and logs a warning.
See [5. Subqueries](05-subquery.md#59-sorting-inside-a-subquery).

<a id="412-rules-summary-s1-to-s14"></a>
## 4.12 Rules summary (S1 to S14)

| # | Rule | Status |
|---|---|---|
| S1 | A sort is one lambda expression: one key (`o -> o.getPrice()`, with an optional `Direction`) or keys built with the functions of `By` (`o -> desc(...)`). For joins, `(o, c) -> ...` | Implemented. The join form `(o, c) -> ...` is also implemented |
| S2 | A key must use a value of the entity. The entity itself cannot be a key | Implemented |
| S3 | The direction is set with `asc` / `desc` or `sort(key, Direction)`. `asc(key)`/`desc(key)` (`ASC`/`DESC`) use the NULL policy, and `asc(key, Nulls.X)`, `desc(key, Nulls.X)` and `*_NULLS_*` are used as written | Implemented |
| S4 | The body is only one key or a simple chain of `By` functions. No `if` or ternary operator that chooses sort functions, and no loops. A ternary operator inside a key value becomes `case when` | Implemented |
| S5 | `orderBy` once per query. No `where` after `orderBy`. No `orderBy` after `limit` | Implemented |
| S6 | Reuse with `SortSpec` (`of`, `ofPath`, `then`, `reversed`, `orderBy(spec)`). Outside strings are checked by the sort policy of the persistence unit (`SortRules`, `SortPolicy`, `SortMode`, see [4.10](#sort-policy)) | Implemented |
| S7 | Keys: columns, to-one paths, `+ - *`, widening conversions. A ternary operator inside a key value becomes `case when` | Implemented |
| S8 | An association path used only in `orderBy` uses a `left join`, so rows without the association are kept. A required association (`optional = false`) does not lose rows, so it is an inner join (A4). If the same path is used in `where`, the sort uses the join of `where` | Implemented |
| S9 | When `orderBy` is used together with paging and the keys have no id, the root `@Id asc` is added. Without `orderBy`, no `order by` is added. It is not added when the keys have a unique column that cannot be NULL (`unique = true` and `nullable = false`, `@NaturalId`) (A5) | Implemented (the root id and the ids of explicitly joined entities) |
| S10 | The analysis result is cached per lambda expression. A captured `Direction` is read on each call | Implemented |
| S11 | Spring `Sort.Order` maps 1:1 to `Direction` (Spring module, see [12.5](12-spring-data-jpa.md#125-spring-sort-and-pageable)). Property names go through the check of the sort policy, and aliases are passed as they are | Spring `Sort` is implemented (`ignoreCase` is not supported). Also works with the sort policy |
| S12 | Join queries use the same form: from `(o, c) -> ...` up to 10 entities | Implemented (see [6.8](06-select-group.md#68-explicit-joins)) |
| S13 | A method reference can be used as one key in every sort form. Method references that compile but have a wrong meaning are rejected (see [9. Java notes](09-java-notes.md#99-method-references)) | Implemented (getter references) |
| S14 | Subquery sort: kept with `limit`, removed with a warning without it. lambdaQL handles the differences between databases (see chapter 5) | Implemented |

---

[Java track](README.md) | Previous: [3. Conditions (`where`)](03-where.md) | Next: [5. Subqueries](05-subquery.md) | [Kotlin version of this page](../kotlin/04-order-paging.md)
