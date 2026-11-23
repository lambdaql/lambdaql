<a id="5-subqueries-java"></a>
# 5. Subqueries (Java)

[Java track](README.md) | [Manual home](../README.md) | Previous: [4. Sorting and paging](04-order-paging.md) | Next: [6. Select, aggregates and group by](06-select-group.md) | [Kotlin version of this page](../kotlin/05-subquery.md)

Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md)

---

<a id="51-the-idea-a-second-lambda-argument-sub"></a>
## 5.1 The idea: a second lambda argument `sub`

A `where` lambda can take a subquery factory as a **second argument**. In this manual we call it `sub` (you can use any name).
Inside it, you write the subquery with **the same lambda syntax** as a normal query.

```java
.where(o -> ...)              // no subquery: one argument
.where((o, sub) -> ...)       // with a subquery: two arguments
```

lambdaQL tells the two forms apart by the number of arguments.
You can mix subqueries with `&&`, `||` and `!` freely. No static import is needed.

What you can do with `sub`:

| Subquery | Use it as | HQL |
|---|---|---|
| `sub.from(X.class).where(...).exists()` | condition | `exists (select 1 from X x where ...)` |
| `!sub.from(X.class).where(...).exists()` | condition | `not exists (...)` |
| `sub.from(X.class).where(...).select(x -> v).contains(y)` | condition | `y in (select x.v from X x where ...)` |
| `!... .contains(y)` | condition | `y not in (select x.v from X x where ...)` |
| `sub.from(X.class).avg(x -> v)` (also `sum`, `min`, `max`, `count()`) | value | `(select avg(x.v) from X x)` |
| `sub.from(X.class)...orderBy(...).limit(1).select(x -> v).first()` | value | top-1 subquery |
| `sub.from(A.class).join(B.class, (a, b) -> ...).where((a, b) -> ...).exists()` | condition | `exists (select 1 from A a join B b on ... where ...)` ([joins inside a subquery](#joins-inside-a-subquery)) |

> [!NOTE]
> The return values (`boolean`, `Double`, ...) are only placeholders so the code compiles.
> The lambda is never run. These methods only have meaning inside a lambdaQL lambda.

The inner lambdas (`i -> ...` inside `sub.from(...).where(...)`) do not need to be `Serializable` on their own. lambdaQL finds them in the bytecode of the outer lambda.

In the HQL, the subquery root has the alias `s1`, `s2`, ... (the outer root is `e0`). A join inside a subquery gets an alias like `s1j1`.

Examples are copied from [`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java).
The test checks the HQL, runs the queries and compares the rows with the same filter in Java. It passes on H2, MariaDB and Oracle.
It uses the test entity from [3.3](03-where.md#33-basic-form): `price` is `double`, `status` is `short` and `Customer.id` is `int`. `Customer.grade` is an `Integer`. The test data has the grades 5, 2 and NULL, and one order without a customer.

## 5.2 EXISTS

Orders that have at least one item with `qty > 10`:

[`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java), region `exists`:

```java
// correlated: the inner lambda uses the outer order o
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId() && i.getQty() > 10).exists());
assertEquals("select e0 from Order e0 where exists (select 1 from Item s1 where s1.order.id = e0.id and s1.qty > 10)",
        q.toHql().hql());
```

**About the SQL blocks.** `…` is the shortened select list (all entity columns), and `?` is a bind parameter. The lines come from [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java). It runs each query on H2 2.3, MariaDB 10.11 and Oracle 23.26 with Hibernate 7.4, and it fails if Hibernate's SQL changes.

**SQL by database** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `sub-exists`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id and i1_0.qty>10)
```

This is a **correlated** subquery. The inner lambda uses the outer `o` (see [5.6](#56-correlated-subqueries)).

## 5.3 NOT EXISTS

Use `!`. You can combine it with other conditions.
Orders with status 1, or orders that have no items:

[`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java), region `not-exists`:

```java
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> o.getStatus() == 1 || !sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).exists());
assertEquals("select e0 from Order e0 where e0.status = 1 or not exists (select 1 from Item s1 where s1.order.id = e0.id)",
        q.toHql().hql());
```

**SQL by database** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `sub-not-exists`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 or not exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id)
```

## 5.4 IN

Orders of customers with `grade > 3`:

[`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java), region `in`:

```java
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> sub.from(Customer.class).where(c -> c.getGrade() != null && c.getGrade() > 3)
                .select(c -> c.getId()).contains(o.getCustomer().getId()));
assertEquals("select e0 from Order e0 where e0.customer.id in"
        + " (select s1.id from Customer s1 where s1.grade is not null and s1.grade > 3)", q.toHql().hql());
```

**SQL by database** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `sub-in`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id in (select c2_0.id from customers c2_0 where c2_0.grade is not null and c2_0.grade>3)
```

`o.getCustomer().getId()` ends with the customer `@Id`, so it is the foreign key column `e0.customer.id`. No join is needed.
The null check you write (`c.getGrade() != null`) stays in the HQL as `s1.grade is not null`.

<a id="55-not-in-is-written-as-not-exists"></a>
## 5.5 NOT IN and NULL

`!values.contains(x)` is rendered as `NOT IN`, with the SQL meaning as it is (decision D48).

[`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java), region `not-in`:

```java
// the SQL meaning (D48): NOT IN. The grades are 5, 2 and NULL, so no row matches: x NOT IN (5, 2, NULL)
// is never true. To skip the NULL, say so in the subquery: where(c -> c.getGrade() != null)
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> !sub.from(Customer.class).select(c -> c.getGrade()).contains((int) o.getStatus()));
assertEquals("select e0 from Order e0 where e0.status not in (select s1.grade from Customer s1)", q.toHql().hql());
SelectQuery<Order> withoutNull = qb.from(Order.class)
        .where((o, sub) -> !sub.from(Customer.class).where(c -> c.getGrade() != null).select(c -> c.getGrade())
                .contains((int) o.getStatus()));
```

**SQL by database** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `sub-not-in`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status not in (select c2_0.grade from customers c2_0)
```

Why:

- `x NOT IN (subquery)` returns **no rows at all** if the subquery returns even one NULL. lambdaQL keeps this SQL meaning (decision D48) and logs `LQL-5009` at `DEBUG` level.
- To skip the NULLs, write `where(c -> c.getGrade() != null)` in the subquery, as in the second query of the test. Then the result is the same as `!grades.contains(status)` in Java with the NULL grades left out.
- `!sub...exists()` is still written as `NOT EXISTS`.

<a id="56-correlated-subqueries"></a>
## 5.6 Correlated subqueries

When the inner lambda uses the outer entity, the subquery is **correlated**. You do not need any special syntax. Just use the outer variable.

```java
.where((o, sub) -> sub.from(Item.class)
                      .where(i -> i.getOrder().getId() == o.getId())   // o comes from outside
                      .exists())
```

How lambdaQL reads the captured values of the inner lambda:

| Captured in the inner lambda | Becomes |
|---|---|
| The outer entity (`o`) | a reference to the outer alias (`e0.id`) |
| Any other value (local variable, field, ...) | a bind parameter, as in [3.11](03-where.md#311-captured-values) |

```java
int minQty = 10;
.where((o, sub) -> sub.from(Item.class)
                      .where(i -> i.getOrder().getId() == o.getId() && i.getQty() > minQty)
                      .exists())
// HQL: select e0 from Order e0
//      where exists (select 1 from Item s1 where s1.order.id = e0.id and s1.qty > :p1)
// parameters: {p1=10}
```

The test method `capturedValueInside` in [`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java) checks this HQL, the parameter and the rows.

**SQL by database (Hibernate's SQL for this HQL)** — recorded by [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java), which gives the same HQL to Hibernate directly. From `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql), example `05-correlated-param`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id and i1_0.qty>?)
```

**`==` in the correlation.** `i.getOrder().getId() == o.getId()` compares two `Long` objects. Two values of the entity are compared with the SQL meaning (decision D48, see [3.6](03-where.md#36-null-rules)), so lambdaQL writes a plain `=` (and `<>` for `!=`).
The database can use an index.

<a id="57-scalar-subqueries"></a>
## 5.7 Scalar subqueries

A subquery that gives **one value** can be used in a comparison.

Orders that cost more than the average price:

[`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java), region `scalar`:

```java
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> o.getPrice() > sub.from(Order.class).avg(x -> x.getPrice()));
assertEquals("select e0 from Order e0 where e0.price > (select avg(s1.price) from Order s1)", q.toHql().hql());
```

**SQL by database** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `sub-scalar`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.price>(select avg(o2_0.price) from orders o2_0)
```

A scalar subquery must **always give one row**. Two shapes are allowed (rule Q3).

| Shape | Example |
|---|---|
| An aggregate: `count()`, `sum`, `avg`, `min`, `max` | `sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).count()` |
| Top-1: `orderBy(...).limit(1)...first()` | see below |

Any other `first()` is an error at analysis time (`TranslationException`, `LQL-3017`).

Orders with two or more items (`count()`):

[`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java), region `count`:

```java
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).count() >= 2);
assertEquals("select e0 from Order e0 where (select count(s1) from Item s1 where s1.order.id = e0.id) >= 2L", q.toHql().hql());
```

**SQL by database** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `sub-count`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where (select count(i1_0.id) from items i1_0 where i1_0.order_id=o1_0.id)>=2
```

Top-1: the most expensive order of each customer.

[`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java), region `top-one`:

```java
// the most expensive order of each customer: a correlated scalar subquery with order + limit 1 (rule Q3)
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> o.getId().equals(sub.from(Order.class)
                .where(x -> x.getCustomer().getId() == o.getCustomer().getId())
                .orderBy((x, by) -> by.desc(x.getPrice()))
                .limit(1)
                .select(x -> x.getId())
                .first()));
assertEquals("select e0 from Order e0 where e0.id = (select s1.id from Order s1 where s1.customer.id = e0.customer.id"
        + " order by s1.price desc, s1.id asc limit 1)", q.toHql().hql());
// first() without limit(1) is rejected
assertThrows(LambdaQLException.class, () -> qb.from(Order.class)
        .where((o, sub) -> o.getId().equals(sub.from(Order.class).select(x -> x.getId()).first())));
```

**SQL by database** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `sub-top-one`:

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

lambdaQL adds the `id` as the last sort key (`s1.id asc`), so the top row is always the same (rule S9).
The correlation compares the foreign keys (`getCustomer().getId()`), not the customer entities. See [5.10](#510-paths-and-null-inside-subqueries).

Aggregate NULLs follow SQL. `sum`, `avg`, `min` and `max` give NULL when there are no rows. `count` gives 0.

<a id="58-nested-subqueries"></a>
## 5.8 Nested subqueries

The `where` of a subquery can also take a `sub` argument. The same rules apply again, at every level.

Customers who have an order with a big item:

[`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java), region `nested`:

```java
// customers that have an order with an item of quantity > 10: a subquery inside a subquery
SelectQuery<Customer> q = qb.from(Customer.class)
        .where((c, sub) -> sub.from(Order.class).where((o, sub2) -> o.getCustomer().getId() == c.getId()
                && sub2.from(Item.class).where(i -> i.getOrder().getId() == o.getId() && i.getQty() > 10).exists()).exists());
assertEquals("select e0 from Customer e0 where exists (select 1 from Order s1 where s1.customer.id = e0.id"
        + " and exists (select 1 from Item s2 where s2.order.id = s1.id and s2.qty > 10))", q.toHql().hql());
```

**SQL by database** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `sub-nested`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from customers c1_0
where exists(select 1 from orders o1_0 where o1_0.customer_id=c1_0.id and exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id and i1_0.qty>10))
```

Java does not allow the same name twice in nested lambdas. Use new names (`sub2`, `o`, `i`) at each level.

<a id="59-sorting-inside-a-subquery"></a>
## 5.9 Sorting inside a subquery

| Case | What lambdaQL does |
|---|---|
| `orderBy` with `limit` (top-1, top-N) | keeps it, with the `id` tie-breaker |
| `orderBy` without `limit` | **removes it** and logs a warning (it does not change the result; Oracle even gives the syntax error ORA-00907) |
| `contains` on a subquery with `limit` (MariaDB does not support `IN (... limit n)`, error 1235) | wraps it in a derived table on every database: `in (select t1.v from (select s1.id as v ... limit n) t1)` |
| MariaDB: `lateral` (wrong results on MariaDB) | rewrite with `row_number() over (partition by ... order by ...)`: **not implemented yet** |

The NULL policy and the automatic `id` tie-breaker from [4. Sorting and paging](04-order-paging.md) also apply inside subqueries.

<a id="510-paths-and-null-inside-subqueries"></a>
## 5.10 Paths and NULL inside subqueries

Inside a subquery, conditions, paths and NULL work **exactly like the outer query** ([3. Conditions (`where`)](03-where.md)).

- `==` on objects is a value comparison with the SQL meaning (D48). A captured `null` matches no row (`LQL-4045`).
- Association paths of the subquery's own entity follow the same join rule as the outer `where`. They are always an inner `join` (decision D48).
- A path that ends with `@Id` is a foreign key column (no join).
- **The outer entity** can be used only with its own columns and its foreign key ids (`o.getId()`, `o.getCustomer().getId()`). A path of the outer entity that needs a join (`o.getCustomer().getName()`) is an error at analysis time. Compare the foreign key ids instead.
- Collection paths (`o.getItems()`) are rejected. To ask about a collection, use a subquery.
- Hibernate also adds the `@SQLRestriction` and `@SoftDelete` conditions of an entity inside a subquery (`exists`, `in`) (measured on MariaDB 10.11 and Oracle 23.26). lambdaQL does not handle these conditions itself.

```java
// instead of o.getItems().size() > 0 (rejected):
.where((o, sub) -> sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).exists())
```

<a id="joins-inside-a-subquery"></a>
### Joins inside a subquery

You can also join two entities inside a subquery (decision D51). The forms are `sub.from(A.class).join(B.class, (a, b) -> ...)` and `leftJoin(...)`, and the result is a `From2<A, B>`.
Every lambda of `From2` gets the two rows: `where((a, b) -> ...)`, `exists()`, `count()`, `contains(x)` or `first()` after `select((a, b) -> v)`, and `max`, `min`, `avg`, `sum((a, b) -> v)`.

- The HQL looks like `exists (select 1 from Order s1 join Item s2 on s2.order.id = s1.id where ...)`.
- An association path inside the ON condition becomes a `left join`. A path join is placed right after the root where the path starts.
- A subquery can have at most two entities. The join form cannot use `orderBy` or `limit`.
- `exists`, `sum` of the joined rows, and `in` with a `left join` and an association path in ON are measured on the three databases.

[`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java), region `sub-join`:

```java
// customers with an order that has an item of quantity > 10: the subquery joins Order and Item (D51)
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

<a id="511-subqueries-in-other-places"></a>
## 5.11 Subqueries in other places

- **Subqueries in UPDATE / DELETE**: the same `sub` syntax in `where`. This is implemented and tested (region `dml-subquery`). See [7.4 Delete](07-4-delete.md) and [7.2 Update](07-2-update.md).
- **Subqueries in the values of `set`** (`o.setTotal(sub.from(...).sum(...))`): write them with `sub`, the second argument of the assignment lambda (decision D49). See [7.2 Update](07-2-update.md#an-aggregate-from-a-subquery).
- **Subqueries in `from` (derived tables) and CTEs**: implemented (milestone M8-2). They do not use `sub`. Pass a value query to `qb.from(...)` or `qb.with(...)`. See [6.9 Derived table](06-select-group.md#69-derived-table-subquery-in-from) and [6.10 CTE](06-select-group.md#610-cte-with).

<a id="512-rules-summary-q1-to-q6"></a>
## 5.12 Rules summary (Q1 to Q6)

| # | Rule |
|---|---|
| Q1 | Conditions, paths and NULL rules inside a subquery are the same as outside. Association paths in conditions also follow the same join rule (inner `join` by default). A path of the outer entity that needs a join is an error; foreign key ids are allowed |
| Q2 | **NOT IN keeps the SQL meaning** (D48): `!values.contains(x)` becomes `x not in (select c.sel from C c where <condition>)`. If the subquery has a NULL, no rows are returned (`LQL-5009` at `DEBUG` level). To skip NULLs, add `c.sel != null` to the subquery |
| Q3 | A scalar subquery must give one row: an aggregate (`max`, `min`, `avg`, `sum`, `count`) or `orderBy(...).limit(1)...first()`. Any other `first()` is an error |
| Q4 | Subquery sorts: kept with `limit`, removed with a warning without `limit`. `IN (... limit)` is wrapped in a derived table. The MariaDB `lateral` rewrite with `row_number()` is not implemented yet |
| Q5 | In a correlation, `==` is written as `=` and `!=` as `<>` (the SQL meaning, D48). A NULL on either side does not match. Index use is kept |
| Q6 | The `where` of a subquery can take its own `sub` argument: `where((i, sub2) -> ...)`. The same rules apply at every level |

---

[Java track](README.md) | Previous: [4. Sorting and paging](04-order-paging.md) | Next: [6. Select, aggregates and group by](06-select-group.md) | [Kotlin version of this page](../kotlin/05-subquery.md)
