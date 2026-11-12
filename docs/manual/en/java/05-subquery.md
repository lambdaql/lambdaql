# 5. Subqueries (Java)

> [!NOTE]
> **Status**
> - **Designed, not implemented yet** (milestone M8).
> - The syntax compiles in Java, and the bytecode was checked with prototypes. The SQL forms were checked on H2 2.3, MariaDB 10.11 and Oracle 23ai with Hibernate 6.6.5.
> - The HQL in this chapter is the **planned** output.

[Java track](README.md) | [Manual home](../README.md) | Previous: [4. Sorting and paging](04-order-paging.md) | Next: [6. Select, aggregates and group by](06-select-group.md) | [Kotlin version of this page](../kotlin/05-subquery.md)

Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md)

---

## 5.1 The idea: a second lambda argument `sub`

A `where` lambda can take a **second argument**: a subquery factory. In this manual we call it `sub` (you can use any name).
Inside, you write the subquery with **the same lambda syntax** as a normal query.

```java
.where(o -> ...)              // no subquery: one argument
.where((o, sub) -> ...)       // with subqueries: two arguments
```

lambdaQL tells the two forms apart by the number of arguments.
You can mix subqueries with `&&`, `||` and `!` freely. No static import is needed.

What you can do with `sub`:

| Subquery | Use it as | HQL |
|---|---|---|
| `sub.from(X.class).where(...).exists()` | condition | `exists (select 1 from X x where ...)` |
| `!sub.from(X.class).where(...).exists()` | condition | `not exists (...)` |
| `sub.from(X.class).where(...).select(x -> v).contains(y)` | condition | `y in (select x.v from X x where ...)` |
| `!... .contains(y)` | condition | `not exists (... and x.v = y)` |
| `sub.from(X.class).avg(x -> v)` (also `sum`, `min`, `max`, `count()`) | value | `(select avg(x.v) from X x)` |
| `sub.from(X.class)...orderBy(...).limit(1).select(x -> v).first()` | value | top-1 subquery |

> [!NOTE]
> The return values (`boolean`, `Double`, ...) are only placeholders so the code compiles.
> The lambda is never run. These methods only have meaning inside a lambdaQL lambda.

The inner lambdas (`i -> ...` inside `sub.from(...).where(...)`) do not need to be `Serializable` on their own. lambdaQL finds them in the bytecode of the outer lambda.

## 5.2 EXISTS

Orders that have at least one item with `qty > 10`:

```java
qb.from(Order.class)
  .where((o, sub) -> sub.from(Item.class)
                        .where(i -> i.getOrder().getId() == o.getId() && i.getQty() > 10)
                        .exists())
  .toList();
// HQL: select o from Order o
//      where exists (select 1 from Item i where i.order.id = o.id and i.qty > 10)
```

**About the SQL blocks.** `…` is the shortened select list (all entity columns), and `?` is a bind parameter. [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java) gives the HQL of this page to Hibernate directly and records the SQL on H2 2.3, MariaDB 10.11 and Oracle 23.26 (Hibernate 6.6.5). lambdaQL does not make these queries yet. The test fails if Hibernate's SQL changes.

**SQL by database (planned)** — Hibernate's translation of the planned HQL, recorded by `PlannedSqlTest`; lambdaQL does not make this query yet. From `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql), example `05-exists`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id and i1_0.qty>10)
```

This is a **correlated** subquery: the inner lambda uses the outer `o` (see [5.6](#56-correlated-subqueries)).

## 5.3 NOT EXISTS

Use `!`. You can combine it with other conditions:

```java
// Orders with status 1, or orders that have no items
qb.from(Order.class)
  .where((o, sub) -> o.getStatus() == 1
         || !sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).exists())
  .toList();
// HQL: select o from Order o
//      where o.status = 1
//         or not exists (select 1 from Item i where i.order.id = o.id)
```

**SQL by database (planned)** — Hibernate's translation of the planned HQL, recorded by `PlannedSqlTest`; lambdaQL does not make this query yet. From `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql), example `05-not-exists-or`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 or not exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id)
```

## 5.4 IN

Orders of customers with `grade > 3`:

```java
qb.from(Order.class)
  .where((o, sub) -> sub.from(Customer.class)
                        .where(c -> c.getGrade() > 3)
                        .select(c -> c.getId())
                        .contains(o.getCustomer().getId()))
  .toList();
// HQL: select o from Order o
//      where o.customer.id in (select c.id from Customer c where c.grade > 3)
```

**SQL by database (planned)** — Hibernate's translation of the planned HQL, recorded by `PlannedSqlTest`; lambdaQL does not make this query yet. From `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql), example `05-in-subquery`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id in (select c2_0.id from customers c2_0 where c2_0.grade>3)
```

`o.getCustomer().getId()` ends with the customer `@Id`, so it is the foreign key column `o.customer.id`. No join is needed.

## 5.5 NOT IN is written as NOT EXISTS

`!values.contains(x)` is **not** rendered as `NOT IN`. lambdaQL writes `NOT EXISTS`:

```java
qb.from(Order.class)
  .where((o, sub) -> !sub.from(Customer.class)
                         .where(c -> c.getGrade() > 3)
                         .select(c -> c.getId())
                         .contains(o.getCustomer().getId()))
  .toList();
// HQL: select o from Order o
//      where not exists (select 1 from Customer c
//                        where c.grade > 3 and c.id = o.customer.id)
```

**SQL by database (planned)** — Hibernate's translation of the planned HQL, recorded by `PlannedSqlTest`; lambdaQL does not make this query yet. From `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql), example `05-not-in`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where not exists(select 1 from customers c2_0 where c2_0.grade>3 and c2_0.id=o1_0.customer_id)
```

Why:

- `x NOT IN (subquery)` returns **no rows at all** if the subquery returns even one NULL. This is a common SQL trap.
- `NOT EXISTS` does not have this problem. Databases also run it well (as an anti-join).
- Result: an order with no customer is returned by the query above, because no customer matches it.

## 5.6 Correlated subqueries

When the inner lambda uses the outer entity, the subquery is **correlated**. You do not need any special syntax. Just use the outer variable:

```java
.where((o, sub) -> sub.from(Item.class)
                      .where(i -> i.getOrder().getId() == o.getId())   // o comes from outside
                      .exists())
```

How lambdaQL reads the inner lambda's captured values:

| Captured in the inner lambda | Becomes |
|---|---|
| The outer entity (`o`) | a reference to the outer alias (`o.id`) |
| Any other value (local variable, field, ...) | a bind parameter, like in [3.11](03-where.md#311-captured-values) |

```java
int minQty = 10;
.where((o, sub) -> sub.from(Item.class)
                      .where(i -> i.getOrder().getId() == o.getId() && i.getQty() > minQty)
                      .exists())
// HQL: exists (select 1 from Item i where i.order.id = o.id and i.qty > :p1)
```

**SQL by database (planned)** — Hibernate's translation of the planned HQL, recorded by `PlannedSqlTest`; lambdaQL does not make this query yet. From `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql), example `05-correlated-param`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id and i1_0.qty>?)
```

**`==` in the correlation.** `i.getOrder().getId() == o.getId()` compares two `Long` objects. By the normal rule this is a null-safe compare (`is not distinct from`, see [3.6](03-where.md#36-null-rules)).
But `o.id` is an `@Id`, so it can never be NULL. In this case lambdaQL writes a simple `=` (and `(a <> b or a is null)` for `!=`).
The result is the same, and the database can use an index. On Oracle, `is not distinct from` becomes `decode(...)`, which cannot use an index.

## 5.7 Scalar subqueries

A subquery that gives **one value** can be used in a comparison.

Orders that cost more than the average price:

```java
qb.from(Order.class)
  .where((o, sub) -> o.getPrice() > sub.from(Order.class).avg(x -> x.getPrice()))
  .toList();
// HQL: select o from Order o
//      where o.price > (select avg(x.price) from Order x)
```

**SQL by database (planned)** — Hibernate's translation of the planned HQL, recorded by `PlannedSqlTest`; lambdaQL does not make this query yet. From `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql), example `05-scalar-avg`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.price>(select avg(o2_0.price) from orders o2_0)
```

A scalar subquery must **always give one row**. Two shapes are allowed:

| Shape | Example |
|---|---|
| An aggregate: `count()`, `sum`, `avg`, `min`, `max` | `sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).count()` |
| Top-1: `orderBy(...).limit(1)...first()` | see below |

Any other `first()` is an error at analysis time.

Top-1 example: the latest order of the same customer.

```java
qb.from(Order.class)
  .where((o, sub) -> o.getId().equals(
          sub.from(Order.class)
             .where(x -> x.getCustomer().getId() == o.getCustomer().getId())
             .orderBy((x, by) -> by.desc(x.getUpdateAt()))
             .limit(1)
             .select(x -> x.getId())
             .first()))
  .toList();
```

Aggregate NULLs follow SQL: `sum`, `avg`, `min`, `max` give NULL when there are no rows. `count` gives 0.

## 5.8 Nested subqueries

A subquery's `where` can also take a `sub` argument. The same rules apply again, at every level.

Customers who have an order with a big item:

```java
qb.from(Customer.class)
  .where((c, sub) -> sub.from(Order.class)
      .where((o, sub2) -> o.getCustomer().getId() == c.getId()
             && sub2.from(Item.class)
                    .where(i -> i.getOrder().getId() == o.getId() && i.getQty() > 10)
                    .exists())
      .exists())
  .toList();
// HQL: select c from Customer c
//      where exists (select 1 from Order o
//                    where o.customer.id = c.id
//                      and exists (select 1 from Item i
//                                  where i.order.id = o.id and i.qty > 10))
```

**SQL by database (planned)** — Hibernate's translation of the planned HQL, recorded by `PlannedSqlTest`; lambdaQL does not make this query yet. From `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql), example `05-nested`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from customers c1_0
where exists(select 1 from orders o1_0 where o1_0.customer_id=c1_0.id and exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id and i1_0.qty>10))
```

Java does not allow the same name twice in nested lambdas. Use new names (`sub2`, `o`, `i`) at each level.

## 5.9 Sorting inside a subquery

| Case | What lambdaQL does |
|---|---|
| `orderBy` with `limit` (top-1, top-N) | keeps it |
| `orderBy` without `limit` | **removes it** and logs a warning (it does not change the result; Oracle even gives a syntax error ORA-00907) |
| MariaDB: `IN (... limit n)` (not supported by MariaDB) | wraps it automatically: `in (select x.id from (... limit n) x)` |
| MariaDB: `lateral` (wrong results on MariaDB) | rewrites it with `row_number() over (partition by ... order by ...)` |

The NULL policy and the automatic `id` tie-breaker from [4. Sorting and paging](04-order-paging.md) also apply inside subqueries.

## 5.10 Paths and NULL inside subqueries

Inside a subquery, conditions, paths and NULL work **exactly like the outer query** ([3. Conditions](03-where.md)):

- `==` on objects is a null-safe value compare.
- Association paths in a subquery condition follow the same join rule as the outer `where` (decision D36): an inner `join` by default, and a `left join` when a null guard keeps rows without the association.
- A path that ends with `@Id` is a foreign key column (no join).
- Collection paths (`o.getItems()`) are rejected. A subquery is the way to ask about a collection:

```java
// Instead of o.getItems().size() > 0  (rejected):
.where((o, sub) -> sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).exists())
```

## 5.11 Subqueries in other places

- **Subqueries in `from` (derived tables) and CTEs** (`qb.from(sub -> ...)`, `qb.with(sub -> ...)`): see [6. Select, aggregates and group by](06-select-group.md).
- **Subqueries in UPDATE / DELETE**: the same `sub` syntax. See [7. Update, insert, delete](07-dml.md).

## 5.12 Rules summary (Q1 to Q6)

| # | Rule |
|---|---|
| Q1 | Conditions, paths and NULL rules inside a subquery are the same as outside. Association paths in conditions use the same join rule (inner `join` by default) |
| Q2 | **NOT IN is rendered as NOT EXISTS**: `!values.contains(x)` becomes `not exists (select 1 from C c where <condition> and c.sel = x)`. This avoids the `NOT IN` + NULL trap |
| Q3 | A scalar subquery must give one row: an aggregate (`max`, `min`, `avg`, `sum`, `count`) or `orderBy(...).limit(1)...first()`. Any other `first()` is an error |
| Q4 | Subquery sorts: kept with `limit`, removed with a warning without `limit`. MariaDB `IN (... limit)` is wrapped, `lateral` is rewritten with `row_number()` |
| Q5 | In a correlation, `==` is written as `=` when one side can never be NULL (`@Id`, `nullable = false`, primitive). `!=` becomes `(a <> b or a is null)`. This keeps index use |
| Q6 | A subquery's `where` can take its own `sub` argument: `where((i, sub2) -> ...)`. The same rules apply at every level |

---

[Java track](README.md) | Previous: [4. Sorting and paging](04-order-paging.md) | Next: [6. Select, aggregates and group by](06-select-group.md) | [Kotlin version of this page](../kotlin/05-subquery.md)
