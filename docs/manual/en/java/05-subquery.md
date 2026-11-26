<a id="5-subqueries-java"></a>
# 5. Subqueries (Java)

[Java track](README.md) | [Manual home](../README.md) | Previous: [4. Sorting and paging](04-order-paging.md) | Next: [6. Select, aggregates and grouping](06-select-group.md) | [Kotlin version of this page](../kotlin/05-subquery.md)

Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md)

---

<a id="51-the-idea-a-second-lambda-argument-sub"></a>
## 5.1 The idea: a second lambda expression argument `sub`

A `where` lambda expression can take a subquery factory as its **second argument**. This manual calls it `sub` (you can use any name).
Inside it, you write the subquery with the **same lambda expression syntax** as a normal query.

Without a subquery there is one argument, like `.where(o -> ...)`. With a subquery there are two arguments, like `.where((o, sub) -> ...)`.
lambdaQL tells the two forms apart by the number of arguments.
You can freely mix subqueries with `&&`, `||` and `!`.

What you can do with `sub`:

| Subquery | Use | HQL |
|---|---|---|
| `sub.from(X.class).where(...).exists()` | Condition | `exists (select 1 from X x where ...)` |
| `!sub.from(X.class).where(...).exists()` | Condition | `not exists (...)` |
| `sub.from(X.class).where(...).select(x -> v).contains(y)` | Condition | `y in (select x.v from X x where ...)` |
| `!... .contains(y)` | Condition | `y not in (select x.v from X x where ...)` |
| `sub.from(X.class).avg(x -> v)` (also `sum`, `min`, `max`, `count()`) | Value | `(select avg(x.v) from X x)` |
| `sub.from(X.class)...orderBy(...).limit(1).select(x -> v).first()` | Value | A top-1 subquery |
| `sub.from(A.class).join(B.class, (a, b) -> ...).where((a, b) -> ...).exists()` | Condition | `exists (select 1 from A a join B b on ... where ...)` ([Joins inside a subquery](#joins-inside-a-subquery)) |

> [!NOTE]
> The return values (`boolean`, `Double`, ...) are only placeholders that let the code compile.
> These methods are used only inside lambdaQL lambda expressions. lambdaQL does not call the lambda expression. It analyzes its bytecode opcodes.

The inner lambda expressions (`i -> ...` inside `sub.from(...).where(...)`) do not need to be `Serializable` on their own. lambdaQL finds them in the bytecode opcodes of the outer lambda expression.

In the HQL, the aliases of subquery roots are `s1`, `s2`, ... (the outer root is `e0`). Joins inside a subquery get aliases like `s1j1`.

The examples use the [example entities](02-getting-started.md#example-entities). `price` is a `double`, `status` is a `short`, `Customer.id` is an `int`, and `Customer.grade` is an `Integer`.
The example data has the grades 5, 2 and NULL, and one order without a customer.
The examples in this chapter return the same rows on H2, MariaDB and Oracle as the same filter applied in Java.

## 5.2 EXISTS

Orders that have at least one item with `qty > 10`:

<!-- region: SubqueryTest.java#exists -->
```java
// correlated: the inner lambda uses the outer order o
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId() && i.getQty() > 10).exists());
assertEquals("select e0 from Order e0 where exists (select 1 from Item s1 where s1.order.id = e0.id and s1.qty > 10)",
        q.toHql().hql());
```

**SQL by database**

<!-- sql: sub-exists -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id and i1_0.qty>10)
```

This is a **correlated** subquery. The inner lambda expression uses the outer `o` (see [5.6](#56-correlated-subqueries)).

Example code: [`SubqueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java) (`exists`).

## 5.3 NOT EXISTS

Use `!`. You can combine it with other conditions.
Orders with status 1, or orders without items:

<!-- region: SubqueryTest.java#not-exists -->
```java
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> o.getStatus() == 1 || !sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).exists());
assertEquals("select e0 from Order e0 where e0.status = 1 or not exists (select 1 from Item s1 where s1.order.id = e0.id)",
        q.toHql().hql());
```

**SQL by database**

<!-- sql: sub-not-exists -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 or not exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id)
```

Example code: [`SubqueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java) (`not-exists`).

## 5.4 IN

Orders of customers with `grade > 3`:

<!-- region: SubqueryTest.java#in -->
```java
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> sub.from(Customer.class).where(c -> c.getGrade() != null && c.getGrade() > 3)
                .select(c -> c.getId()).contains(o.getCustomer().getId()));
assertEquals("select e0 from Order e0 where e0.customer.id in"
        + " (select s1.id from Customer s1 where s1.grade is not null and s1.grade > 3)", q.toHql().hql());
```

**SQL by database**

<!-- sql: sub-in -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id in (select c2_0.id from customers c2_0 where c2_0.grade is not null and c2_0.grade>3)
```

`o.getCustomer().getId()` ends with the `@Id` of the customer, so it is the foreign key column `e0.customer.id`. No join is needed.
The null check that you wrote (`c.getGrade() != null`) stays in the HQL as `s1.grade is not null`.

Example code: [`SubqueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java) (`in`).

<a id="55-not-in-is-written-as-not-exists"></a>
## 5.5 NOT IN and NULL

`!values.contains(x)` is rendered as `NOT IN`, with its SQL meaning as it is.

<!-- region: SubqueryTest.java#not-in -->
```java
// the SQL meaning: NOT IN. The grades are 5, 2 and NULL, so no row matches: x NOT IN (5, 2, NULL)
// is never true. To skip the NULL, say so in the subquery: where(c -> c.getGrade() != null)
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> !sub.from(Customer.class).select(c -> c.getGrade()).contains((int) o.getStatus()));
assertEquals("select e0 from Order e0 where e0.status not in (select s1.grade from Customer s1)", q.toHql().hql());
SelectQuery<Order> withoutNull = qb.from(Order.class)
        .where((o, sub) -> !sub.from(Customer.class).where(c -> c.getGrade() != null).select(c -> c.getGrade())
                .contains((int) o.getStatus()));
```

**SQL by database**

<!-- sql: sub-not-in -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status not in (select c2_0.grade from customers c2_0)
```

Why:

- `x NOT IN (subquery)` returns **no rows at all** if the subquery returns even one NULL. lambdaQL keeps this SQL meaning and logs `LQL-5009` at `DEBUG` level.
- To skip NULLs, write `where(c -> c.getGrade() != null)` in the subquery, like the second query in the example. Then the result is the same as Java's `!grades.contains(status)` without the NULL grades.
- `!sub...exists()` is still written as `NOT EXISTS`.

Example code: [`SubqueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java) (`not-in`).

<a id="56-correlated-subqueries"></a>
## 5.6 Correlated subqueries

When the inner lambda expression uses the outer entity, the subquery becomes a **correlated** subquery. No special syntax is needed. Just use the outer variable (`o` in the example below).

How lambdaQL reads the captured values of the inner lambda expression:

| Captured in the inner lambda expression | Result |
|---|---|
| The outer entity (`o`) | A reference to the outer alias (`e0.id`) |
| Other values (local variables, fields, ...) | Bind parameters, as in [3.11](03-where.md#311-captured-values) |

<!-- region: ManualJavaSubqueryTest.java#correlated -->
```java
int minQty = 10;
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> sub.from(Item.class)
                .where(i -> i.getOrder().getId() == o.getId() && i.getQty() > minQty)   // o comes from outside
                .exists());
assertEquals("select e0 from Order e0"
        + " where exists (select 1 from Item s1 where s1.order.id = e0.id and s1.qty > :p1)", q.toHql().hql());
assertEquals(Map.of("p1", 10), q.toHql().parameters());
```

**`==` in the correlation condition.** `i.getOrder().getId() == o.getId()` compares two `Long` objects. Two entity values are compared with SQL meaning (see [3.6](03-where.md#36-null-rules)), so lambdaQL writes a simple `=` (`<>` for `!=`).
The database can use an index.

Example code: [`ManualJavaSubqueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaSubqueryTest.java) (`correlated`).

<a id="57-scalar-subqueries"></a>
## 5.7 Scalar subqueries

A subquery that gives **one value** can be used in a comparison.

Orders more expensive than the average price:

<!-- region: SubqueryTest.java#scalar -->
```java
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> o.getPrice() > sub.from(Order.class).avg(x -> x.getPrice()));
assertEquals("select e0 from Order e0 where e0.price > (select avg(s1.price) from Order s1)", q.toHql().hql());
```

**SQL by database**

<!-- sql: sub-scalar -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.price>(select avg(o2_0.price) from orders o2_0)
```

A scalar subquery **must always give one row**. Two forms are allowed.

| Form | Example |
|---|---|
| Aggregate: `count()`, `sum`, `avg`, `min`, `max` | `sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).count()` |
| Top-1: `orderBy(...).limit(1)...first()` | See below |

Any other `first()` is an error at analysis time (`TranslationException`, `LQL-3017`).

Orders with two or more items (`count()`):

<!-- region: SubqueryTest.java#count -->
```java
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).count() >= 2);
assertEquals("select e0 from Order e0 where (select count(s1) from Item s1 where s1.order.id = e0.id) >= 2L", q.toHql().hql());
```

**SQL by database**

<!-- sql: sub-count -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where (select count(i1_0.id) from items i1_0 where i1_0.order_id=o1_0.id)>=2
```

Top-1: the most expensive order of each customer.

<!-- region: SubqueryTest.java#top-one -->
```java
// the most expensive order of each customer: a correlated scalar subquery with order + limit 1
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> o.getId().equals(sub.from(Order.class)
                .where(x -> x.getCustomer().getId() == o.getCustomer().getId())
                .orderBy(x -> desc(x.getPrice()))
                .limit(1)
                .select(x -> x.getId())
                .first()));
assertEquals("select e0 from Order e0 where e0.id = (select s1.id from Order s1 where s1.customer.id = e0.customer.id"
        + " order by s1.price desc, s1.id asc limit 1)", q.toHql().hql());
// first() without limit(1) is rejected
assertThrows(LambdaQLException.class, () -> qb.from(Order.class)
        .where((o, sub) -> o.getId().equals(sub.from(Order.class).select(x -> x.getId()).first())));
```

**SQL by database**

<!-- sql: sub-top-one -->
```sql
-- H2, Oracle
select …
from orders o1_0
where o1_0.id=(select o2_0.id from orders o2_0 where o2_0.customer_id=o1_0.customer_id order by o2_0.price desc,o2_0.id fetch first 1 rows only)

-- MariaDB
select …
from orders o1_0
where o1_0.id=(select o2_0.id from orders o2_0 where o2_0.customer_id=o1_0.customer_id order by o2_0.price desc,o2_0.id limit 1)
```

A sort inside a subquery also uses the sort functions of `By`, as in the outer query: `orderBy(x -> desc(x.getPrice()))` (see [4.1](04-order-paging.md#41-one-lambda-for-the-whole-sort)).
lambdaQL adds `id` (`s1.id asc`) as the last sort key, so the top row is always the same.
The correlation condition compares the foreign key (`getCustomer().getId()`), not the customer entity. See [5.10](#510-paths-and-null-inside-subqueries).

NULL in aggregates follows SQL. `sum`, `avg`, `min` and `max` give NULL when there are no rows. `count` gives 0.

Example code: [`SubqueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java) (`scalar`, `count`, `top-one`).

<a id="58-nested-subqueries"></a>
## 5.8 Nested subqueries

The `where` of a subquery can also take a `sub` argument. The same rules apply again at every level.

Customers who have an order with a large item:

<!-- region: SubqueryTest.java#nested -->
```java
// customers that have an order with an item of quantity > 10: a subquery inside a subquery
SelectQuery<Customer> q = qb.from(Customer.class)
        .where((c, sub) -> sub.from(Order.class).where((o, sub2) -> o.getCustomer().getId() == c.getId()
                && sub2.from(Item.class).where(i -> i.getOrder().getId() == o.getId() && i.getQty() > 10).exists()).exists());
assertEquals("select e0 from Customer e0 where exists (select 1 from Order s1 where s1.customer.id = e0.id"
        + " and exists (select 1 from Item s2 where s2.order.id = s1.id and s2.qty > 10))", q.toHql().hql());
```

**SQL by database**

<!-- sql: sub-nested -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from customers c1_0
where exists(select 1 from orders o1_0 where o1_0.customer_id=c1_0.id and exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id and i1_0.qty>10))
```

Java does not allow the same name twice in nested lambda expressions. Use new names at each level (`sub2`, `o`, `i`).

Example code: [`SubqueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java) (`nested`).

<a id="59-sorting-inside-a-subquery"></a>
## 5.9 Sorting inside a subquery

| Case | What lambdaQL does |
|---|---|
| `orderBy` with `limit` (top-1, top-N) | Kept, with the `id` tie-breaker |
| `orderBy` without `limit` | **Removed**, with a warning in the log (it does not change the result; on Oracle it even gives the syntax error ORA-00907) |
| `contains` on a subquery with `limit` (MariaDB does not support `IN (... limit n)`, error 1235) | Wrapped in a derived table on all databases: `in (select t1.v from (select s1.id as v ... limit n) t1)` |

The NULL policy and the automatic `id` tie-breaker of [4. Sorting and paging](04-order-paging.md) also apply inside subqueries.

<a id="510-paths-and-null-inside-subqueries"></a>
## 5.10 Paths and NULL inside subqueries

Inside a subquery, conditions, paths and NULL work **exactly as in the outer query** ([3. Conditions (`where`)](03-where.md)).

- `==` on objects is a value comparison with SQL meaning. A captured value `null` matches no row (`LQL-4045`).
- Association paths on the subquery's own entity follow the same join rule as the outer `where`. If a row without the association can match the condition, it is a `left join`. If it cannot, it is an inner `join`.
- A path that ends with `@Id` is the foreign key column (no join).
- The **outer entity** can use only its own columns and foreign key ids (`o.getId()`, `o.getCustomer().getId()`). A path of the outer entity that needs a join (`o.getCustomer().getName()`) is an error at analysis time. Compare the foreign key id instead.
- Collection paths (`c.getOrders()`) are rejected (`LQL-3010`). To ask about a collection, use a subquery.
- Hibernate also adds the `@SQLRestriction` and `@SoftDelete` conditions of an entity inside subqueries (`exists`, `in`) (measured on MariaDB 10.11 and Oracle 23.26). lambdaQL does not handle these conditions separately.

The next example finds "customers who have orders" with a subquery instead of the `Customer.orders` collection.

<!-- region: ManualJavaSubqueryTest.java#collection-path -->
```java
// a collection path is rejected
LambdaQLException e = assertThrows(LambdaQLException.class,
        () -> qb.from(Customer.class).where(c -> c.getOrders().size() > 0));
assertEquals("LQL-3010", e.code());
// ask with a subquery instead
SelectQuery<Customer> withOrders = qb.from(Customer.class)
        .where((c, sub) -> sub.from(Order.class).where(o -> o.getCustomer().getId() == c.getId()).exists());
assertEquals("select e0 from Customer e0"
        + " where exists (select 1 from Order s1 where s1.customer.id = e0.id)", withOrders.toHql().hql());
```

<a id="joins-inside-a-subquery"></a>
### Joins inside a subquery

You can also join two entities inside a subquery. The forms are `sub.from(A.class).join(B.class, (a, b) -> ...)` and `leftJoin(...)`, and the result is `From2<A, B>`.
Every lambda expression of `From2` takes two rows: `where((a, b) -> ...)`, `exists()`, `count()`, `contains(x)` or `first()` after `select((a, b) -> v)`, and `max`, `min`, `avg`, `sum((a, b) -> v)`.

- The HQL looks like `exists (select 1 from Order s1 join Item s2 on s2.order.id = s1.id where ...)`.
- An association path inside the ON condition becomes a `left join`. A path join is placed right after the root where the path starts.
- A subquery can have up to two entities. The join form cannot use `orderBy` and `limit`.
- `exists`, `sum` of joined rows, and `in` with a `left join` and an association path in ON were measured on three databases.

<!-- region: SubqueryTest.java#sub-join -->
```java
// customers with an order that has an item of quantity > 10: the subquery joins Order and Item
SelectQuery<Customer> q = qb.from(Customer.class).where((c, sub) -> sub.from(Order.class)
        .join(Item.class, (o, i) -> i.getOrder().getId() == o.getId())
        .where((o, i) -> o.getCustomer().getId() == c.getId() && i.getQty() > 10)
        .exists());
assertEquals("select e0 from Customer e0 where exists (select 1 from Order s1 join Item s2 on s2.order.id = s1.id"
        + " where s1.customer.id = e0.id and s2.qty > 10)", q.toHql().hql());
// an aggregate over the joined rows
SelectQuery<Customer> total = qb.from(Customer.class).where((c, sub) -> sub.from(Order.class)
        .join(Item.class, (o, i) -> i.getOrder().getId() == o.getId())
        .where((o, i) -> o.getCustomer().getId() == c.getId())
        .sum((o, i) -> i.getQty()) > 30);
assertEquals("select e0 from Customer e0 where (select sum(s2.qty) from Order s1 join Item s2 on s2.order.id = s1.id"
        + " where s1.customer.id = e0.id) > 30", total.toHql().hql());
```

Example code: [`ManualJavaSubqueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaSubqueryTest.java) (`collection-path`), [`SubqueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java) (`sub-join`).

<a id="511-subqueries-in-other-places"></a>
## 5.11 Subqueries in other places

- **Subqueries in UPDATE / DELETE**: use the same `sub` syntax in `where`. See [7.4 Delete](07-4-delete.md) and [7.2 Update](07-2-update.md).
- **Subqueries in a `set` value** (`o.setTotal(sub.from(...).sum(...))`): write them with `sub`, the second argument of the assignment lambda expression. See [7.2 Update](07-2-update.md#an-aggregate-from-a-subquery).
- **Subqueries in `from` (derived tables) and CTE**: implemented. Pass a value query to `qb.from(...)` or `qb.with(...)` without `sub`. See [6.9 Derived table](06-select-group.md#69-derived-table-subquery-in-from) and [6.10 CTE](06-select-group.md#610-cte-with).

<a id="512-rules-summary-q1-to-q6"></a>
## 5.12 Rules summary (Q1 to Q6)

| # | Rule |
|---|---|
| Q1 | The rules for conditions, paths and NULL inside a subquery are the same as outside. Association paths in conditions follow the same join rule (a `left join` if a row without the association can match the condition, otherwise an inner `join`). A path of the outer entity that needs a join is an error. Foreign key ids are allowed |
| Q2 | **NOT IN keeps its SQL meaning**: `!values.contains(x)` becomes `x not in (select c.sel from C c where <condition>)`. If the subquery has a NULL, no rows are returned (`LQL-5009` at `DEBUG` level). To skip NULLs, add `c.sel != null` to the subquery |
| Q3 | A scalar subquery must give one row: an aggregate (`max`, `min`, `avg`, `sum`, `count`) or `orderBy(...).limit(1)...first()`. Any other `first()` is an error |
| Q4 | Subquery sort: kept with `limit`, removed with a warning without it. `IN (... limit)` is wrapped in a derived table |
| Q5 | In a correlation condition, `==` is written as `=` and `!=` as `<>` (SQL meaning). If either side is NULL, it does not match. Index use is kept |
| Q6 | The `where` of a subquery can take its own `sub` argument: `where((i, sub2) -> ...)`. The same rules apply at every level |

---

[Java track](README.md) | Previous: [4. Sorting and paging](04-order-paging.md) | Next: [6. Select, aggregates and grouping](06-select-group.md) | [Kotlin version of this page](../kotlin/05-subquery.md)
