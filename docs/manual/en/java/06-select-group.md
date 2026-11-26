<a id="6-select-aggregates-and-group-by-java"></a>
# 6. Select, aggregates and grouping (Java)

> [!IMPORTANT]
> This page is older than the Korean manual. See the [Korean page](../../ko/java/06-select-group.md) for the latest rules.

[Java track](README.md) | [Manual home](../README.md) | Previous: [5. Subqueries](05-subquery.md) | Next: [7. Update, insert, delete](07-dml.md) | [Kotlin version of this page](../kotlin/06-select-group.md)

Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md)

---

This chapter explains how to choose **what** a query returns.

- One value per row (`select(o -> o.getPrice())`)
- A Java record or a DTO class
- Aggregates (`count`, `sum`, `avg`, ...) with `groupBy` and `having`
- `distinct`, paging and result methods of value queries
- Explicit joins with other entities (`join`, `leftJoin`, `crossJoin`)
- Subqueries in the FROM clause (derived tables) and CTE (`with`)

<a id="contents"></a>
## Contents

- [6.1 Example entities and result types](#61-example-entities-and-result-types)
- [6.2 Select one value](#62-select-one-value)
- [6.3 Select a record or DTO](#63-select-a-record-or-dto)
- [6.4 Aggregate functions: `Agg`](#64-aggregate-functions-agg)
- [6.5 Group by](#65-group-by)
- [6.6 having](#66-having)
- [6.7 distinct, paging and results](#67-distinct-paging-and-results)
- [6.8 Explicit joins](#68-explicit-joins)
- [6.9 Derived table (subquery in FROM)](#69-derived-table-subquery-in-from)
- [6.10 CTE (`with`)](#610-cte-with)
- [6.11 Rules J1 to J8](#611-rules-j1-to-j8)
- [6.12 What is not supported](#612-not-in-v01)

---

<a id="61-example-entities-and-result-types"></a>
## 6.1 Example entities and result types

The examples use these entities (see [2. Getting started](02-getting-started.md#example-entities)).

| Entity | Fields used here |
|---|---|
| `Order` | `id`, `price`, `status`, `active`, `customer` (many-to-one) |
| `Customer` | `id`, `name`, `grade` |
| `Item` | `order`, `price`, `qty` |

`price` is a `double`, `status` is a `short`, and `Customer.id` is an `int`. The example data has one order without a customer.
The examples in this chapter return the same values on H2, MariaDB and Oracle as the same computation done in Java.

The examples use these result types:

- `record OrderView(Long id, String customerName, double price)`
- `record Stat(Integer customerId, long count, Double total)`
- `record KeyCount(Integer customerId, short status, long count)`
- `record Total(long count, Double average)`

Some examples ([`ManualJavaSelectTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaSelectTest.java)) declare and use their own `Stat` and `Total` records with the same names.

> [!TIP]
> Use boxed types (`Double`, `Long`, `Integer`) for values that can be NULL.
> Columns and aggregates such as `sum` can be NULL. A primitive `double` argument cannot hold NULL, and in that case lambdaQL throws `IllegalStateException` (see [6.4](#null-in-aggregates-rule-j4)).
> `count` is never NULL, so `long count` is fine.

---

<a id="62-select-one-value"></a>
## 6.2 Select one value

`select` takes one lambda expression. The lambda expression returns one value. The result is a `ValueQuery` of that value.
You can call `select` after `where` and after `orderBy`. The sort, `limit` and `offset` are kept.

<!-- region: ProjectionTest.java#value -->
```java
ValueQuery<Double> q = qb.from(Order.class)
        .where(o -> o.getStatus() == 1)
        .orderBy(o -> asc(o.getPrice()))
        .select(o -> o.getPrice());
assertEquals("select e0.price from Order e0 where e0.status = 1 order by e0.price asc", q.toHql().hql());
```

**SQL by database**

<!-- sql: value -->
```sql
-- H2, MariaDB, Oracle (same)
select o1_0.price
from orders o1_0
where o1_0.status=1
order by o1_0.price
```

An association path is a column, as in `where`. In `select`, lambdaQL adds a `left join`, so orders without a customer are not left out (in `where`, the same path is an inner join if orders without a customer cannot match the condition).
A required association mapped with `optional = false` does not lose rows, so it is an inner join also in `select` (see [4.5](04-order-paging.md#45-what-a-sort-key-can-be)).

<!-- region: ManualJavaSelectTest.java#select-path -->
```java
ValueQuery<String> names = qb.from(Order.class).select(o -> o.getCustomer().getName());
// a path used only in select is a left join: the order without a customer gives null
assertEquals("select j1.name from Order e0 left join e0.customer j1", names.toHql().hql());
```

The record example in [6.3](#63-select-a-record-or-dto) also uses this `left join`.
If the path ends with the `@Id` of the associated entity, there is no join.
`o.getCustomer().getId()` reads the foreign key column `customer_id`.

Example code: [`ProjectionTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java) (`value`), [`ManualJavaSelectTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaSelectTest.java) (`select-path`).

---

<a id="63-select-a-record-or-dto"></a>
## 6.3 Select a record or DTO

Call a constructor inside the lambda expression. Each argument of the constructor is a value expression.
You can use both Java records and normal DTO classes with a constructor.

<!-- region: ProjectionTest.java#record -->
```java
// only the columns are selected; the record is made in Java. The path uses a left join,
// so the order without a customer stays (customerName is null)
ValueQuery<OrderView> q = qb.from(Order.class)
        .select(o -> new OrderView(o.getId(), o.getCustomer().getName(), o.getPrice()));
assertEquals("select e0.id, j1.name, e0.price from Order e0 left join e0.customer j1", q.toHql().hql());
```

**SQL by database**

<!-- sql: record -->
```sql
-- H2, MariaDB, Oracle (same)
select o1_0.id,c1_0.name,o1_0.price
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
```

lambdaQL does **not** write `select new com.acme.OrderView(...)`.
It selects the columns and calls the constructor in Java.
This avoids problems with long class names and nested class names.
lambdaQL converts each value to the type of the constructor argument (for example, Hibernate's `Long` to an `Integer` argument).

What you can return:

| Result type | Simple `select` | Row type of a derived table or CTE ([6.9](#69-derived-table-subquery-in-from)) |
|---|---|---|
| One value (`o.getPrice()`) | Yes | – |
| Java record | Yes | Yes |
| Normal DTO class (constructor) | Yes | **No** |

Example code: [`ProjectionTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java) (`record`).

---

<a id="64-aggregate-functions-agg"></a>
## 6.4 Aggregate functions: `Agg`

Aggregate functions are **static methods of `org.lambdaql.Agg`**.
They are standard SQL functions, so lambdaQL provides them. (For your own DB functions, see [8. Procedures and DB functions](08-procedures-functions.md).)

| Method | SQL |
|---|---|
| `Agg.count()` | `count(*)` |
| `Agg.count(x)` | `count(x)` |
| `Agg.countDistinct(x)` | `count(distinct x)` |
| `Agg.sum(x)` | `sum(x)` |
| `Agg.avg(x)` | `avg(x)` |
| `Agg.min(x)` | `min(x)` |
| `Agg.max(x)` | `max(x)` |

Java types:

- `Agg.count()`: `long`
- `Agg.sum(x)`: `<N extends Number> N` (the same type as the argument)
- `Agg.avg(x)`: `Double`
- `Agg.min(x)`, `Agg.max(x)`: `<R extends Comparable<?>> R` (the same type as the argument)

Important:

- The return value is only a **placeholder**. lambdaQL does not run the `Agg` call. It moves it into an SQL aggregate, so this value is never used.
- If you call `Agg.sum(...)` **outside** a lambdaQL lambda expression, it throws `IllegalStateException`.
- You can use `Agg` in `select`, `having`, and the values of `insert ... select` ([7.5](07-5-insert.md)).
- You cannot use `Agg` in `where`. For conditions on aggregates, use `having`. `Agg` in `where` is an error at analysis time.

<a id="aggregate-over-all-rows-no-group-by"></a>
### Aggregate over all rows (no group by)

<!-- region: ProjectionTest.java#total -->
```java
// aggregates without groupBy: one row over all matching orders
ValueQuery<Total> q = qb.from(Order.class)
        .where(o -> o.getStatus() == 1)
        .select(o -> new Total(Agg.count(), Agg.avg(o.getPrice())));
assertEquals("select count(*), avg(e0.price) from Order e0 where e0.status = 1", q.toHql().hql());
```

**SQL by database**

<!-- sql: total -->
```sql
-- H2, MariaDB, Oracle (same)
select count(*),avg(o1_0.price)
from orders o1_0
where o1_0.status=1
```

This query always returns one row. You can read this row with `q.one()`.

<a id="null-in-aggregates-rule-j4"></a>
### NULL in aggregates (rule J4)

| Function | When there are no rows or all values are NULL |
|---|---|
| `sum`, `avg`, `min`, `max` | `NULL` |
| `count` | `0` |

This is normal SQL behavior.
If NULL goes into a primitive constructor argument (`double`, `long`), lambdaQL throws `IllegalStateException`. The message names the argument and tells you to use the wrapper type (`Double`, `Long`). The example is in [6.11](#611-rules-j1-to-j8).

Example code: [`ProjectionTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java) (`total`).

---

<a id="65-group-by"></a>
## 6.5 Group by

Write the group keys with `groupBy`. The group keys are not decided automatically from the values of `select`.
`groupBy` returns a group query. Call `having(...)` (optional), then `select(...)`.

<a id="one-key"></a>
### One key

<!-- region: ManualJavaSelectTest.java#group-one -->
```java
ValueQuery<Stat> q = qb.from(Order.class)
        .groupBy(o -> o.getCustomer().getId())
        .select(o -> new Stat(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice())));
assertEquals("select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id",
        q.toHql().hql());
List<Stat> stats = q.toList();
```

The key ends with `@Id`, so there is no join. Orders without a customer become one group whose key is NULL.
An example with `having` is in [6.6](#66-having).

<a id="many-keys"></a>
### Many keys

Return a list of keys with `List.of(...)`.

<!-- region: ProjectionTest.java#group-many -->
```java
// many keys: List.of(...) (Kotlin listOf(...), a to b, Triple)
ValueQuery<KeyCount> q = qb.from(Order.class)
        .where(o -> o.getCustomer() != null)
        .groupBy(o -> List.of(o.getCustomer().getId(), o.getStatus()))
        .select(o -> new KeyCount(o.getCustomer().getId(), o.getStatus(), Agg.count()));
assertEquals("select e0.customer.id, e0.status, count(*) from Order e0 where e0.customer is not null"
        + " group by e0.customer.id, e0.status", q.toHql().hql());
```

**SQL by database**

<!-- sql: group-many -->
```sql
-- H2, MariaDB, Oracle (same)
select o1_0.customer_id,o1_0.status,count(*)
from orders o1_0
where o1_0.customer_id is not null
group by o1_0.customer_id,o1_0.status
```

The keys are used in the order you write them.
This is the only place where a collection built inside the lambda expression is allowed.

Not allowed as a key list:

| Form | Why |
|---|---|
| `Set.of(a, b)` | A set removes duplicates and has no order |

<a id="every-non-aggregate-value-must-be-a-key-rule-j3"></a>
### Every non-aggregate value must be a key (rule J3)

If the query has an aggregate, every value in `select` and `having` that is **not** an aggregate must be a `groupBy` key. This is an SQL rule. lambdaQL checks it at analysis time.

For example, if you write `o.getStatus()`, which is not a group key, in `select` after `groupBy(o -> o.getCustomer().getId())`, you get a `TranslationException` (`LQL-3035`).

The example is in [6.11](#611-rules-j1-to-j8).

Example code: [`ManualJavaSelectTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaSelectTest.java) (`group-one`), [`ProjectionTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java) (`group-many`).

---

<a id="66-having"></a>
## 6.6 having

`having` is a condition on groups. It uses the same lambda expression rules as `where` ([3. Conditions (`where`)](03-where.md)), and you can use `Agg` in it.
You can call `having` more than once. The conditions are joined with `AND`.

<!-- region: ProjectionTest.java#group-by -->
```java
ValueQuery<Stat> q = qb.from(Order.class)
        .groupBy(o -> o.getCustomer().getId())
        .having(o -> Agg.sum(o.getPrice()) > 200)
        .select(o -> new Stat(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice())));
assertEquals("select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id"
        + " having sum(e0.price) > 200.0", q.toHql().hql());
```

**SQL by database**

<!-- sql: group-by -->
```sql
-- H2, MariaDB, Oracle (same)
select o1_0.customer_id,count(*),sum(o1_0.price)
from orders o1_0
group by o1_0.customer_id
having sum(o1_0.price)>200.0
```

Here `Agg.sum(o.getPrice())` is a `double`, so javac compares it with `200.0`. The constant stays a literal.

Example code: [`ProjectionTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java) (`group-by`).

---

<a id="67-distinct-paging-and-results"></a>
## 6.7 distinct, paging and results

`select(...)` returns a `ValueQuery`. `ValueQuery` has these methods:

| Method | What it does |
|---|---|
| `distinct()` | `select distinct` |
| `limit(n)`, `offset(n)` | Paging |
| `toList()` | Runs the query and returns the values in order |
| `toSet()` | Runs the query with `select distinct` and returns an ordered `SequencedSet` |
| `first()` | The first value, or `Optional.empty()` |
| `one()` | The only value, or `Optional.empty()`. `NonUniqueResultException` if there are two or more rows |
| `toHql()` | Returns the HQL and the parameters without running the query |

<!-- region: ProjectionTest.java#distinct -->
```java
ValueQuery<Double> q = qb.from(Order.class)
        .orderBy(o -> desc(o.getPrice()))
        .select(o -> o.getPrice())
        .distinct();
assertEquals("select distinct e0.price from Order e0 order by e0.price desc", q.toHql().hql());
// toSet() always uses select distinct
List<Double> prices = q.toSet().stream().toList();
```

**SQL by database**

<!-- sql: distinct -->
```sql
-- H2, MariaDB, Oracle (same)
select distinct o1_0.price
from orders o1_0
order by o1_0.price desc
```

- With `distinct()`, every sort key must be a select value. If not, lambdaQL throws a `TranslationException` (`LQL-3046`) at analysis time.
- `toSet()` of a value query always uses `select distinct`. Repeated values are normal here, so there is no size check (unlike `toSet()` of entities, see [2. Getting started](02-getting-started.md#toset)).
- If you use a `@Lob` column in `distinct` or as a `groupBy` key, Oracle rejects it (ORA-22848, ORA-22849). H2 and MariaDB run it. lambdaQL does not change the query and logs `LQL-5010` at `DEBUG` level (see [4.5](04-order-paging.md#45-what-a-sort-key-can-be)).

**Paging.** `limit` and `offset` work the same as in [4. Sorting and paging](04-order-paging.md).
With paging, lambdaQL adds `id` as the last sort key, so pages do not overlap. If a sort key is a unique column that cannot be NULL, it is not added (see [4.8](04-order-paging.md#tie-breaker-automatic-id)).
`first()` also reads only one row, so it adds the same extra sort key. If many rows have the same sort key value, it always returns the value of the row with the smallest `id`.
It is not added to group queries and `distinct` queries, because their select lists have no `id`.

<!-- region: ManualJavaSelectTest.java#value-page -->
```java
ValueQuery<Double> paged = qb.from(Order.class)
        .orderBy(o -> desc(o.getPrice()))
        .select(o -> o.getPrice())
        .limit(2).offset(1);
// paging adds the id as the last sort key
assertEquals("select e0.price from Order e0 order by e0.price desc, e0.id asc", paged.toHql().hql());
List<Double> page = paged.toList();
```

**SQL by database**

<!-- sql: value-page -->
```sql
-- H2, Oracle
select o1_0.price
from orders o1_0
order by o1_0.price desc,o1_0.id
offset ? rows
fetch first ? rows only

-- MariaDB
select o1_0.price
from orders o1_0
order by o1_0.price desc,o1_0.id
limit ?,?
```

Example code: [`ProjectionTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java) (`distinct`), [`ManualJavaSelectTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaSelectTest.java) (`value-page`).

---

<a id="68-explicit-joins"></a>
## 6.8 Explicit joins

An association path (`o.getCustomer().getName()`) joins a to-one association automatically (see [3.10](03-where.md#310-association-paths)).
To join another entity with a condition that you choose, or without a condition, use `join`, `leftJoin` and `crossJoin`.

| Method | HQL | Rows |
|---|---|---|
| `join(Customer.class, (o, c) -> ...)` | `join Customer e1 on ...` | Inner join: only rows that have a match |
| `leftJoin(Customer.class, (o, c) -> ...)` | `left join Customer e1 on ...` | Rows of the main entity without a match are also kept |
| `crossJoin(Customer.class)` | `cross join Customer e1` | Every combination of rows. Write the condition in `where` |

After a join, every lambda expression takes all entities in join order:
`where((o, c) -> ...)`, `orderBy((o, c) -> asc(c.getName()).desc(o.getPrice()))`, `select((o, c) -> ...)`.
The aliases of the joined entities are `e1`, `e2`.

The examples below return the same rows on H2, MariaDB and Oracle as the same computation done in Java.

<!-- region: JoinQueryTest.java#join -->
```java
SelectJoinQuery<Order, Customer> q = qb.from(Order.class)
        .join(Customer.class, (o, c) -> o.getCustomer().getId() == c.getId())
        .where((o, c) -> c.getGrade() != null && c.getGrade() >= 5);
assertEquals("select e0 from Order e0 join Customer e1 on e0.customer.id = e1.id"
        + " where e1.grade is not null and e1.grade >= 5", q.toHql().hql());
```

**The result is the main entity.** Without `select`, `toList()` returns a `List<Order>`.
The joined entity is used only in conditions, sorts and `select`.
To read values of the joined entity, use `select((o, c) -> ...)` (see below).

<a id="left-join-and-null"></a>
### left join and NULL

<!-- region: JoinQueryTest.java#left-join -->
```java
// c is missing for an order without a customer: c == null finds those rows
SelectJoinQuery<Order, Customer> q = qb.from(Order.class)
        .leftJoin(Customer.class, (o, c) -> o.getCustomer().getId() == c.getId())
        .where((o, c) -> c == null || c.getName().equals("Kim"));
assertEquals("select e0 from Order e0 left join Customer e1 on e0.customer.id = e1.id"
        + " where e1.id is null or e1.name = 'Kim'", q.toHql().hql());
```

- `c == null` means "no match". lambdaQL writes it as `e1.id is null` (the `@Id` of the joined entity).
- After `leftJoin`, a condition on a column of the joined entity follows **SQL meaning**. Without a match the column is NULL, so the condition is not true, and the row is left out of the result.
  In Java, calling `c.getName()` on a missing `c` throws a `NullPointerException`, but lambdaQL does not follow this behavior.
  To keep rows without a match, write `c == null || ...`, as in the example.

<a id="sort-and-select"></a>
### Sort and select

<!-- region: JoinQueryTest.java#sort -->
```java
OrderedJoinQuery<Order, Customer> q = qb.from(Order.class)
        .leftJoin(Customer.class, (o, c) -> o.getCustomer().getId() == c.getId())
        .orderBy((o, c) -> asc(c.getName()).asc(o.getId()));
assertEquals("select e0 from Order e0 left join Customer e1 on e0.customer.id = e1.id"
        + " order by e1.name asc nulls first, e0.id asc", q.toHql().hql());
```

The NULL ordering rule of [4.4](04-order-paging.md#44-null-ordering-is-the-same-on-every-database) applies. For orders without a customer, `e1.name` is NULL, so they come first with `asc`.

<!-- region: JoinQueryTest.java#select -->
```java
ValueQuery<Row> rows = qb.from(Order.class)
        .leftJoin(Customer.class, (o, c) -> o.getCustomer().getId() == c.getId())
        .orderBy((o, c) -> asc(o.getId()))
        .select((o, c) -> new Row(o.getId(), c.getName()));
assertEquals("select e0.id, e1.name from Order e0 left join Customer e1 on e0.customer.id = e1.id"
        + " order by e0.id asc", rows.toHql().hql());
```

`Row` is `record Row(Long orderId, String customerName)`. For orders without a customer, `customerName` is `null`.
`select` returns a `ValueQuery`, so you can use `distinct()`, `limit`, `offset` and the result methods of [6.7](#67-distinct-paging-and-results).

<a id="select-entities"></a>
### Selecting entities

You can also write an entity itself in `select`. You can choose the joined entity, or put several entities into one record.

<!-- region: SelectEntityTest.java#select-joined -->
```java
// the customers that have an order, through an explicit join; Hibernate returns each entity once
ValueQuery<Customer> q = qb.from(Order.class)
        .join(Customer.class, (o, c) -> o.getCustomer().getId() == c.getId())
        .select((o, c) -> c);
```

When you choose one entity, Hibernate returns the same entity only once. The HQL is `select e1 from Order e0 join Customer e1 on e0.customer.id = e1.id`.

<!-- region: SelectEntityTest.java#select-entities -->
```java
// an order and its customer together; with leftJoin an order without a customer has null
ValueQuery<OrderWithCustomer> q = qb.from(Order.class)
        .leftJoin(Customer.class, (o, c) -> o.getCustomer().getId() == c.getId())
        .select((o, c) -> new OrderWithCustomer(o, c));
```

`OrderWithCustomer` is `record OrderWithCustomer(Order order, Customer customer)`. The HQL is `select e0, e1 from Order e0 left join Customer e1 on e0.customer.id = e1.id`, with one row per order.

<!-- region: SelectEntityTest.java#select-self -->
```java
// o -> o selects the entity itself: the same rows as toList()
ValueQuery<Order> q = qb.from(Order.class).where(o -> o.getPrice() > 150).select(o -> o);
```

- `select(o -> o)` gives the same result as calling `toList()` without `select`.
- A row that holds entities cannot be used as the row of a derived table or CTE (`LQL-3071`). Choose the id or columns of the entity.

Put only values into the CTE, then join the entity in the outer query and choose the entity.

<!-- region: SelectEntityTest.java#select-entity-cte -->
```java
// the CTE has values only (customer id, total); the outer query joins it to the entity and selects the entity
Cte<Total> totals = qb.with(qb.from(Order.class)
        .where(o -> o.getCustomer() != null)
        .groupBy(o -> o.getCustomer().getId())
        .select(o -> new Total(o.getCustomer().getId(), Agg.sum(o.getPrice()))));
ValueQuery<Customer> big = qb.from(Customer.class)
        .join(totals, (c, t) -> t.customerId().equals(c.getId()))
        .where((c, t) -> t.total() > 250)
        .select((c, t) -> c);
```

Example code: [`SelectEntityTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectEntityTest.java) (`select-joined`, `select-entities`, `select-self`, `select-entity-cte`).

<a id="three-entities-and-repeated-rows"></a>
### Many entities and repeated rows

You can join up to 10 entities (the main entity and 9 joined entities). With each join, the lambda expression takes one more entity: `(o, c, i) -> ...`.

<!-- region: JoinQueryTest.java#three -->
```java
// orders of a customer with an item of quantity > 10 (an order with two such items is two SQL rows)
SelectJoin3Query<Order, Customer, Item> q = qb.from(Order.class)
        .join(Customer.class, (o, c) -> o.getCustomer().getId() == c.getId())
        .join(Item.class, (o, c, i) -> i.getOrder().getId() == o.getId())
        .where((o, c, i) -> i.getQty() > 10);
assertEquals("select e0 from Order e0 join Customer e1 on e0.customer.id = e1.id"
        + " join Item e2 on e2.order.id = e0.id where e2.qty > 10", q.toHql().hql());
```

An example that joins 10 entities:

<!-- region: JoinQueryTest.java#ten -->
```java
// up to 10 entities: each lambda gets all of them in join order
SelectJoin10Query<Order, Customer, Item, Order, Order, Order, Order, Order, Order, Order> q = qb.from(Order.class)
        .join(Customer.class, (o, c) -> o.getCustomer().getId() == c.getId())
        .join(Item.class, (o, c, i) -> i.getOrder().getId() == o.getId())
        .join(Order.class, (o, c, i, a) -> a.getId() == o.getId())
        .join(Order.class, (o, c, i, a, b) -> b.getId() == a.getId())
        .join(Order.class, (o, c, i, a, b, d) -> d.getId() == b.getId())
        .join(Order.class, (o, c, i, a, b, d, e) -> e.getId() == d.getId())
        .join(Order.class, (o, c, i, a, b, d, e, f) -> f.getId() == e.getId())
        .join(Order.class, (o, c, i, a, b, d, e, f, g) -> g.getId() == f.getId())
        .leftJoin(Order.class, (o, c, i, a, b, d, e, f, g, h) -> h.getId() == g.getId())
        .where((o, c, i, a, b, d, e, f, g, h) -> i.getQty() > 10 && h.getPrice() > 0);
```

> [!WARNING]
> If many rows match the join condition, **the rows of the main entity are repeated in SQL, once per match**. `toList()` and `toSet()` have no repeats, because Hibernate returns the same entity only once.
> But `count()` and `limit` count the repeated SQL rows. To count orders or to fill a page exactly, use an `exists()` subquery ([5. Subqueries](05-subquery.md)) instead of a join, or apply `distinct()` to the join query (see [`distinct()` on a join result](#distinct-on-a-join)).

<a id="cross-join"></a>
### cross join

`crossJoin` has no join condition. The method name shows that you want every combination of rows. Write the condition in `where`.

<!-- region: JoinQueryTest.java#cross-join -->
```java
// every pair of rows; the conditions go in where
SelectJoinQuery<Order, Customer> q = qb.from(Order.class)
        .crossJoin(Customer.class)
        .where((o, c) -> c.getGrade() != null && o.getPrice() > 100);
assertEquals("select e0 from Order e0 cross join Customer e1 where e1.grade is not null and e0.price > 100.0",
        q.toHql().hql());
```

lambdaQL has no `from(Order.class, Customer.class)` (comma join). If you forget the condition, you get every combination by mistake, and it is not clear which entity is the result. For such queries, use `crossJoin`.

<a id="paging"></a>
### Paging

With paging, lambdaQL adds the ids of **all** entities as the last sort keys. So pages do not overlap.

<!-- region: JoinQueryTest.java#cross-join-paging -->
```java
// with paging, the id of every entity is added to the order, so pages never overlap
ValueQuery<Row> pairs = qb.from(Order.class)
        .crossJoin(Customer.class)
        .orderBy((o, c) -> desc(o.getPrice()))
        .select((o, c) -> new Row(o.getId(), c.getName()));
assertEquals("select e0.id, e1.name from Order e0 cross join Customer e1 order by e0.price desc, e0.id asc, e1.id asc",
        pairs.limit(5).toHql().hql());
```

<a id="distinct-on-a-join"></a>
### `distinct()` on a join result

`SelectJoinQuery.distinct()` and `SelectJoin3Query.distinct()` make one SQL row for each row of the main entity. The HQL is `select distinct e0`, and `count()` is `count(distinct e0)`.
`toList()` and `toSet()` return each main entity once even without `distinct()`. You need `distinct()` so that `count()` and `limit` count the main entities.

- The sort keys must be columns of the main entity itself (`LQL-3046`). Sorting by a column of a joined entity or by an association path is an error.
- With paging, only the id of the main entity is added as the tie-breaker key. The ids of the joined entities are not added.
- `select(...)` after `distinct()` selects the values with `select distinct`.
- First consider an `exists` subquery ([5. Subqueries](05-subquery.md)). It gives the same rows without repeats and is often faster.

<!-- region: JoinQueryTest.java#join-distinct -->
```java
// an order with two matching items is two SQL rows; distinct() makes it one row in SQL
SelectJoinQuery<Order, Item> q = qb.from(Order.class)
        .join(Item.class, (o, i) -> i.getOrder().getId() == o.getId())
        .where((o, i) -> i.getQty() > 0)
        .distinct();
assertEquals("select distinct e0 from Order e0 join Item e1 on e1.order.id = e0.id where e1.qty > 0", q.toHql().hql());
// a sort key must be a column of the main entity
OrderedJoinQuery<Order, Item> sorted = q.orderBy((o, i) -> desc(o.getPrice()));
assertEquals("select distinct e0 from Order e0 join Item e1 on e1.order.id = e0.id where e1.qty > 0"
        + " order by e0.price desc", sorted.toHql().hql());
```

If you use `toSet()` without `distinct()`, you can also sort by columns of the joined entity. But `count()` counts SQL rows.

<!-- region: JoinQueryTest.java#join-set -->
```java
SelectJoinQuery<Order, Item> joined = qb.from(Order.class)
        .join(Item.class, (o, i) -> i.getOrder().getId() == o.getId());
// the order with two items is two SQL rows, but Hibernate returns it once: toList() and toSet() have no repeats
SequencedSet<Order> orders = joined.orderBy((o, i) -> desc(i.getQty()).asc(o.getId())).toSet();
// count() and limit count the SQL rows; distinct() makes them count the orders
long rows = joined.count();
long distinctOrders = qb.from(Order.class)
        .join(Item.class, (o, i) -> i.getOrder().getId() == o.getId())
        .distinct()
        .count();
```

<a id="join-a-derived-table-or-cte"></a>
### Joining a derived table or CTE

You can join the derived table of [6.9](#69-derived-table-subquery-in-from) and the CTE of [6.10](#610-cte-with) like an entity.
The forms are `join(cte, (c, x) -> ...)`, `leftJoin(cte, ...)`, `join(qb.from(...)...select(new Row(...)), ...)` and `leftJoin(...)`, and you can use them as the second or third root.

- The HQL is `with t as (...) ... join t e1 on ...` or `join (select ...) e1 on ...`.
- The row type is a record. The accessors (`x.total()`) read the columns.
- If one statement reads the same `Cte` object twice, the definition is written once, and both joins read the same name: `join t e1 on ... left join t e2 on ...`. Different `Cte` objects get their own names, like `with t as (...), t2 as (...)` (measured on three databases).
- A derived row has no id, so `x == null` is an error (`LQL-3067`). Compare a column, like `x.customerId() == null`.
- Hibernate 7.4 (and also the earlier 6.6) accepts `select distinct` of entities, inner/left joins of derived tables and CTEs, and entity joins inside subqueries (measured on three databases).

<!-- region: JoinQueryTest.java#join-cte -->
```java
// the total of each customer, joined to the customer row
Cte<Total> t = qb.with(qb.from(Order.class)
        .where(o -> o.getCustomer() != null)
        .groupBy(o -> o.getCustomer().getId())
        .select(o -> new Total(o.getCustomer().getId(), Agg.sum(o.getPrice()))));
ValueQuery<NameTotal> rows = qb.from(Customer.class)
        .join(t, (c, x) -> x.customerId().equals(c.getId()))
        .where((c, x) -> x.total() > 100)
        .select((c, x) -> new NameTotal(c.getName(), x.total()));
assertEquals("with t as (select d0.customer.id as customerId, sum(d0.price) as total from Order d0"
        + " where d0.customer is not null group by d0.customer.id)"
        + " select e0.name, e1.total from Customer e0 join t e1 on e1.customerId = e0.id where e1.total > 100.0",
        rows.toHql().hql());
```

<!-- region: JoinQueryTest.java#join-derived -->
```java
// a derived table without a name; a customer without orders has no row in it (left join)
SelectJoinQuery<Customer, Total> small = qb.from(Customer.class)
        .leftJoin(totals(), (c, x) -> x.customerId().equals(c.getId()))
        .where((c, x) -> x.customerId() == null || x.total() < 210);
assertEquals("select e0 from Customer e0 left join (select d0.customer.id as customerId, sum(d0.price) as total"
        + " from Order d0 where d0.customer is not null group by d0.customer.id) e1 on e1.customerId = e0.id"
        + " where e1.customerId is null or e1.total < 210.0", small.toHql().hql());
```

<!-- region: JoinQueryTest.java#cte-reuse -->
```java
// the same CTE read twice is written once
ValueQuery<NameTotal> reused = qb.from(Customer.class)
        .join(t, (c, x) -> x.customerId().equals(c.getId()))
        .leftJoin(t, (c, x, y) -> y.customerId().equals(c.getId()) && y.total() > 210)
        .select((c, x, y) -> new NameTotal(c.getName(), y.total()));
assertEquals("with t as (select d0.customer.id as customerId, sum(d0.price) as total from Order d0"
        + " where d0.customer is not null group by d0.customer.id)"
        + " select e0.name, e2.total from Customer e0 join t e1 on e1.customerId = e0.id"
        + " left join t e2 on e2.customerId = e0.id and e2.total > 210.0", reused.toHql().hql());
```

<a id="what-a-join-query-has"></a>
### Methods of a join query

| After | Methods |
|---|---|
| `join`, `leftJoin`, `crossJoin` | `where`, `join` / `leftJoin` / `crossJoin` (up to 10 entities, including derived tables and CTEs), `distinct`, `orderBy`, `select`, `limit`, `toList`, `toSet`, `first`, `count`, `exists`, `toHql` |
| `orderBy((o, c) -> ...)` | `select`, `limit`, `offset`, `toList`, `first` and the other sorted query methods ([4. Sorting and paging](04-order-paging.md)) |

For joins inside a subquery, see [5. Subqueries](05-subquery.md#joins-inside-a-subquery).
Not supported: joins in insert, 11 or more entities, and the join form of `SortSpec`.
Joins in update and delete will not be added. Use association paths or `sub`.

Example code: [`JoinQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java) (`join`, `left-join`, `sort`, `select`, `three`, `cross-join`, `cross-join-paging`, `join-distinct`, `join-set`, `join-cte`, `join-derived`, `cte-reuse`, `ten`).

---

<a id="69-derived-table-subquery-in-from"></a>
## 6.9 Derived table (subquery in FROM)

Build a value query (a record, see [6.3](#63-select-a-record-or-dto)) with `select`, then pass it to `qb.from(...)`.
Its rows become the table of the outer query.
The result is a `DerivedQuery<R>`, where `R` is the row type.

<!-- region: DerivedQueryTest.java#derived -->
```java
// the rows of a group query are the table of the outer query; r.total() reads the column "total"
DerivedQuery<Stat> q = qb.from(qb.from(Order.class)
                .where(o -> o.getCustomer() != null)
                .groupBy(o -> o.getCustomer().getId())
                .select(o -> new Stat(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice()))))
        .where(r -> r.total() > 100)
        .orderBy(r -> desc(r.total()));
assertEquals("select e0.customerId, e0.cnt, e0.total from (select d0.customer.id as customerId, count(*) as cnt,"
        + " sum(d0.price) as total from Order d0 where d0.customer is not null group by d0.customer.id) e0"
        + " where e0.total > 100.0 order by e0.total desc nulls last", q.toHql().hql());
```

In this example, `Stat` is `record Stat(Integer customerId, Long cnt, Double total)`.
The result rows and `count()` are the same on H2, MariaDB and Oracle as the same computation done in Java.

- The alias of the inner query is `d0` (joins are `dj1`, ...), and the alias of the outer query is `e0`.
- Each record component gives a column name: `as customerId`, `as cnt`, `as total`.
- In the outer lambda expression, you read the columns with the record accessors. `r.total()` is `e0.total`.
- The outer query has `where`, `orderBy`, `limit`, `offset`, `select`, `toList`, `first`, `count` and `toHql`.

You can also select one value of the row. The captured values of the inner and outer queries are bound in order.

<!-- region: DerivedQueryTest.java#select -->
```java
// a value of the row; captured values inside and outside are bound in order
ValueQuery<Integer> ids = qb.from(qb.from(Order.class)
                .where(o -> o.getCustomer() != null && o.getPrice() > min)
                .groupBy(o -> o.getCustomer().getId())
                .select(o -> new Stat(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice()))))
        .where(r -> r.total() > min * 2)
        .select(r -> r.customerId());
```

`limit` and `offset` of the inner query stay in the inner query (HQL `limit`, `offset`). So you can sort the top rows again.

<!-- region: DerivedQueryTest.java#inner-limit -->
```java
// the three most expensive orders, then the cheapest of them
Optional<Priced> cheapestOfTop = qb.from(qb.from(Order.class)
                .orderBy(o -> desc(o.getPrice()))
                .limit(3)
                .select(o -> new Priced(o.getId(), o.getPrice())))
        .orderBy(r -> asc(r.price()))
        .first();
```

`Priced` is `record Priced(Long id, Double price)`.

Hibernate 7.4 names the columns of a derived table `c0`, `c1`, `c2` and so on. H2 adds the column list to the alias (`r1_0(c0,c1,c2)`). MariaDB and Oracle write the column aliases inside the subquery.

Rules:

- The row type must be a **Java record** (a Kotlin data class also works).
  lambdaQL takes the column names from the record components, in order.
- A normal DTO class or a single value cannot be used here, because lambdaQL cannot decide the column names. In that case you get a `TranslationException` (`LQL-3064`).

<!-- region: DerivedQueryTest.java#rules -->
```java
// the row type must be a record or Kotlin data class (its components name the columns)
assertEquals("LQL-3064", assertThrows(LambdaQLException.class,
        () -> qb.from(qb.from(Order.class).select(o -> o.getPrice()))).code());
```

For joining a derived table to an entity query, see [Joining a derived table or CTE](#join-a-derived-table-or-cte).

Not supported: joins between derived tables, and derived tables in update, delete and insert.

Hibernate also adds the `@SQLRestriction` and `@SoftDelete` conditions of an entity to the `on` of entity joins and inside derived tables (measured on MariaDB 10.11 and Oracle 23.26). lambdaQL does not handle these conditions separately.

Example code: [`DerivedQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DerivedQueryTest.java) (`derived`, `select`, `inner-limit`, `rules`).

---

<a id="610-cte-with"></a>
## 6.10 CTE (`with`)

Create a handle of type `Cte<R>` with `qb.with(valueQuery)`. Then read it with `qb.from(handle)`.
The inner query is the same as for a derived table (6.9).

<!-- region: DerivedQueryTest.java#cte -->
```java
Cte<Stat> t = qb.with(perCustomer());
DerivedQuery<Stat> q = qb.from(t).where(r -> r.cnt() >= 2);
assertEquals("with t as (select d0.customer.id as customerId, count(*) as cnt, sum(d0.price) as total"
        + " from Order d0 where d0.customer is not null group by d0.customer.id)"
        + " select e0.customerId, e0.cnt, e0.total from t e0 where e0.cnt >= 2L", q.toHql().hql());
```

`perCustomer()` is a helper method of the example. It returns the same value query as the inner query of [6.9](#69-derived-table-subquery-in-from) (a group query that groups orders with a customer by customer id and builds `Stat`).

MariaDB and Oracle run a real `with`. Hibernate writes the column list itself: `with t (customerId,cnt,total) as (...)`. H2 turns the CTE into a derived table.

Notes:

- The name of the CTE is `t`. The outer query always adds the alias `e0` (`from t e0`). HQL needs the alias.
- The column names of the CTE come from the select aliases (`as customerId`). HQL does not take a column list after the CTE name, and Hibernate adds the column list to the SQL.
- MariaDB and Oracle run a real `with`. On H2, Hibernate expands it into a derived table. The result is the same.
- The row type rule is the same as for derived tables: a record.
- For joining a CTE to an entity query, see [Joining a derived table or CTE](#join-a-derived-table-or-cte).
- You can read the same CTE twice in one query. The definition is written only once (see [Joining a derived table or CTE](#join-a-derived-table-or-cte)).
- CTEs in update, delete and insert are not supported.

Example code: [`DerivedQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DerivedQueryTest.java) (`cte`).

---

<a id="611-rules-j1-to-j8"></a>
## 6.11 Rules J1 to J8

| # | Rule | Status |
|---|---|---|
| J1 | A `select` lambda expression returns **one value expression** or **one constructor call**. Each argument of the constructor is a value expression (the rules of [3. Conditions (`where`)](03-where.md)). An entity itself can also be a value ([Selecting entities](#select-entities)). A ternary operator that chooses a value becomes `case when` ([3.5.1](03-where.md#351-values-chosen-by-a-condition)). An association path used only in `select` becomes a `left join` alias, so rows are not lost. A required association (`optional = false`) is an inner join (A4). If the same path is used in `where`, `select` uses the join of `where` (R24). | Implemented |
| J2 | For a constructor, lambdaQL selects only the columns and calls the constructor in Java. It does not write `select new`. | Implemented |
| J3 | If there is an aggregate, every non-aggregate value in `select` and `having` (and in the values of `insert ... select`) must be a `groupBy` key. If not, it is an error at analysis time. | Implemented. The error is a `TranslationException` (`LQL-3035`, see [11](../11-errors-and-languages.md)) |
| J4 | `sum`/`avg`/`min`/`max` give NULL when there are no rows or all values are NULL. `count` gives 0. Use boxed types for values that can be NULL. NULL in a primitive constructor argument raises an `IllegalStateException` that names the argument. | Implemented |
| J5 | Row type of a derived table or CTE: a **Java record** (Kotlin code can also use a data class). A normal DTO class is allowed only in a simple `select`. Otherwise `LQL-3064`. This also applies to the rows of joined derived tables and CTEs. A derived row cannot be compared with `null` (`LQL-3067`). | Implemented |
| J6 | A CTE built with `qb.with(...)` names its columns with select aliases: `with t as (select d0.customer.id as customerId, ...) select e0.customerId ... from t e0`. HQL does not take a column list after the name. Hibernate rejects `with t (col list) as (...)` with a `SyntaxException`. Hibernate writes the column list into the SQL itself (`with t (customerId,cnt,total) as (...)` on MariaDB and Oracle). H2 turns the CTE into a derived table. The outer query always uses an alias (`from t e0`). | Implemented |
| J7 | `distinct()` renders `select distinct`. With a sort, the sort keys must be in the select list (a database rule). lambdaQL checks this at analysis time. For `distinct()` on a join result, the sort keys must be columns of the main entity itself. | Implemented |
| J8 | Select works with sorting ([4. Sorting and paging](04-order-paging.md)), paging and subqueries ([5. Subqueries](05-subquery.md)). Sorting by an aggregate (`desc(s.total())`) is not supported. | Implemented, except sorting by an aggregate |

<!-- region: ProjectionTest.java#rules -->
```java
// a value without an aggregate must be a group key
LambdaQLException j3 = assertThrows(LambdaQLException.class, () -> qb.from(Order.class)
        .groupBy(o -> o.getCustomer().getId())
        .select(o -> new KeyCount(o.getCustomer().getId(), o.getStatus(), Agg.count())));
assertEquals("LQL-3035", j3.code());
// aggregates belong in having, not in where
LambdaQLException where = assertThrows(LambdaQLException.class, () -> qb.from(Order.class).where(o -> Agg.count() > 1));
assertEquals("LQL-3005", where.code());
// with distinct, the sort key must be selected
LambdaQLException j7 = assertThrows(LambdaQLException.class, () -> qb.from(Order.class)
        .orderBy(o -> asc(o.getId())).select(o -> o.getPrice()).distinct().toHql());
assertEquals("LQL-3046", j7.code());
// the group without orders gives NULL; a primitive argument cannot take it
assertThrows(IllegalStateException.class, () -> qb.from(Order.class)
        .where(o -> o.getStatus() == 9)
        .select(o -> new Bad(null, Agg.sum(o.getPrice()))).toList());
```

`Bad` is `record Bad(Integer customerId, double total)`. There is no order with status 9, so `sum` is NULL, and a primitive `double` cannot take this value.

Example code: [`ProjectionTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java) (`rules`).

---

<a id="612-not-in-v01"></a>
## 6.12 What is not supported

| Item | Status |
|---|---|
| Sorting by an aggregate result or a select alias (for example `orderBy(s -> desc(s.total()))`), `orderBy` of a group query | Not supported |
| User aggregate functions and window functions | Not supported |
| Joins in insert, 11 or more entities, 3 or more entities inside a subquery | Not supported |
| Joins in update and delete | Will not be added. Use association paths or `sub` |
| Derived tables and CTEs in DML | Not supported |

The errors in this chapter are `TranslationException`s with codes: `LQL-3035` for a wrong group key, `LQL-3005` for `Agg` in `where`, `LQL-3046` for a sort key that is not selected with `distinct`, and `LQL-3064` for the row type of a derived table or CTE. See [11. Errors and languages](../11-errors-and-languages.md).

---

[Java track](README.md) | Previous: [5. Subqueries](05-subquery.md) | Next: [7. Update, insert, delete](07-dml.md) | [Kotlin version of this page](../kotlin/06-select-group.md)
