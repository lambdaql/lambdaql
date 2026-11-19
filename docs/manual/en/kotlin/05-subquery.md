<a id="5-subqueries-kotlin"></a>
# 5. Subqueries (Kotlin)

> [!IMPORTANT]
> This page is older than the Korean manual. See the [Korean page](../../ko/kotlin/05-subquery.md) for the latest rules.

[Kotlin track](README.md) | [Manual home](../README.md) | Previous: [4. Sorting and paging](04-order-paging.md) | Next: [6. Select, aggregates and group by](06-select-group.md) | [Java version of this page](../java/05-subquery.md)

Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md)

---

<a id="51-the-idea-a-second-lambda-argument-sub"></a>
## 5.1 The idea: a second lambda argument `sub`

A `where` lambda can take a subquery factory as its **second argument**. In this manual we call it `sub` (you can use any name).
Inside it, you write the subquery with **the same lambda syntax** as a normal query.

```kotlin
.where { ... }                // no subquery: one argument (it)
.where { o, sub -> ... }      // with a subquery: two arguments
```

lambdaQL tells the two forms apart by the number of arguments. Kotlin picks the right overload.
You can mix subqueries with `&&`, `||` and `!` freely. No import is needed.

What you can do with `sub`:

| Subquery | Use | HQL |
|---|---|---|
| `sub.from(X::class.java).where { ... }.exists()` | condition | `exists (select 1 from X x where ...)` |
| `!sub.from(X::class.java).where { ... }.exists()` | condition | `not exists (...)` |
| `y in sub.from(X::class.java).where { ... }.select { x -> v }` | condition | `y in (select x.v from X x where ...)` |
| `y !in sub.from(...)...select { ... }` | condition | `y not in (select x.v from X x where ...)` |
| `sub.from(X::class.java).avg { x -> v }` (also `sum`, `min`, `max`, `count()`) | value | `(select avg(x.v) from X x)` |
| `sub.from(X::class.java)...orderBy { ... }.limit(1).select { x -> v }.first()` | value | top-1 subquery |
| `sub.from(A::class.java).join(B::class.java) { a, b -> ... }.where { a, b -> ... }.exists()` | condition | `exists (select 1 from A a join B b on ... where ...)` ([Joins inside a subquery](#joins-inside-a-subquery)) |

> [!NOTE]
> The return values (`Boolean`, `Double`, ...) are only placeholders so the code compiles.
> The lambda is never run. These functions have meaning only inside a lambdaQL lambda.

In Kotlin, `y in values` compiles to `values.contains(y)`. So the Kotlin `in` operator works with the subquery API with no extra code.

The inner lambdas (`{ i -> ... }` inside `sub.from(...).where { }`) do not need to be `Serializable` on their own. lambdaQL finds them in the bytecode of the outer lambda.

In the HQL, the subquery root has the alias `s1`, `s2`, ... (the outer root is `e0`). A join inside a subquery gets an alias such as `s1j1`.

The examples marked **(tested)** come from [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt). This test uses the Java test entity: `price` is a primitive `double` and `qty` is an `int`.
More queries written in Java are tested in [`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java) (H2, MariaDB and Oracle). See [the Java version of this page](../java/05-subquery.md) for those examples.

## 5.2 EXISTS

Orders that have at least one item with `qty > 10`:

```kotlin
qb.from(Order::class.java)
    .where { o, sub ->
        sub.from(Item::class.java)
            .where { i -> i.order?.id == o.id && i.qty > 10 }
            .exists()
    }
    .toList()
// HQL: select e0 from Order e0
//      where exists (select 1 from Item s1 where s1.order.id = e0.id and s1.qty > 10)
```

**(tested)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt), region `exists`:

```kotlin
val q = qb.from(Order::class.java)
    .where { o, sub -> sub.from(Item::class.java).where { i -> i.order?.id == o.id && i.qty > 10 }.exists() }
assertEquals("select e0 from Order e0 where exists (select 1 from Item s1 where s1.order.id = e0.id and s1.qty > 10)",
    q.toHql().hql())
```

**About the SQL blocks.** `…` is the shortened select list (all entity columns), and `?` is a bind parameter. The lines come from [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java). It runs each query on H2 2.3, MariaDB 10.11 and Oracle 23.26 with Hibernate 6.6.5, and it fails if Hibernate's SQL changes. The test uses Java lambdas. A Kotlin lambda with the same meaning gives the same HQL, so it gets the same SQL.

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `sub-exists`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id and i1_0.qty>10)
```

This is a **correlated** subquery. The inner lambda uses the outer `o` (see [5.6](#56-correlated-subqueries)).

## 5.3 NOT EXISTS

Use `!`. You can combine it with other conditions.

```kotlin
// orders with status 1, or orders with no items
qb.from(Order::class.java)
    .where { o, sub ->
        o.status == 1 ||
            !sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists()
    }
    .toList()
// HQL: select e0 from Order e0
//      where e0.status = 1 or not exists (select 1 from Item s1 where s1.order.id = e0.id)
```

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `sub-not-exists`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 or not exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id)
```

## 5.4 IN

Orders of customers with `grade > 3`. Use `in`.

```kotlin
qb.from(Order::class.java)
    .where { o, sub ->
        o.customer?.id in sub.from(Customer::class.java)
            .where { c -> c.grade!! > 3 }
            .select { c -> c.id }
    }
    .toList()
// HQL: select e0 from Order e0
//      where e0.customer.id in (select s1.id from Customer s1 where s1.grade > 3)
```

**(tested)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt), region `in`:

```kotlin
val q = qb.from(Order::class.java)
    .where { o, sub -> o.customer?.id in sub.from(Customer::class.java).where { c -> c.grade!! > 3 }.select { c -> c.id } }
assertEquals("select e0 from Order e0 where e0.customer.id in (select s1.id from Customer s1 where s1.grade > 3)",
    q.toHql().hql())
```

**SQL by database (Hibernate's SQL for this HQL)** — recorded by [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java), which gives the same HQL to Hibernate directly. The Java test (region `in`) also has the null check `c.getGrade() != null`. Its SQL is example `sub-in`. From `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql), example `05-in-subquery`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id in (select c2_0.id from customers c2_0 where c2_0.grade>3)
```

`o.customer?.id` ends with the customer `@Id`, so it is the foreign key column `e0.customer.id`. No join is needed.
`!!` is ignored (decision D23). `c.grade!! > 3` is `s1.grade > 3`.

<a id="55-not-in-is-written-as-not-exists"></a>
## 5.5 NOT IN and NULL

`x !in values` is written as `NOT IN`, with the SQL meaning (decision D48).

```kotlin
// orders whose status is not equal to the grade of any customer
qb.from(Order::class.java)
    .where { o, sub -> o.status !in sub.from(Customer::class.java).select { c -> c.grade } }
    .toList()
// HQL: select e0 from Order e0
//      where e0.status not in (select s1.grade from Customer s1)
```

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `sub-not-in`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status not in (select c2_0.grade from customers c2_0)
```

Why:

- `x NOT IN (subquery)` returns **no rows at all** if the subquery returns even one NULL. lambdaQL keeps this SQL meaning (decision D48) and logs `LQL-5009` at `DEBUG` level.
- To skip the NULLs, write a null check in the subquery, for example `sub.from(Customer::class.java).where { c -> c.grade != null }.select { c -> c.grade }`.
  The Java test checks both queries with data that has a NULL grade.
- `!sub...exists()` is still written as `NOT EXISTS`.

<a id="56-correlated-subqueries"></a>
## 5.6 Correlated subqueries

When the inner lambda uses the outer entity, the subquery is **correlated**. No special syntax is needed. You use the outer variable as it is.

```kotlin
.where { o, sub ->
    sub.from(Item::class.java)
        .where { i -> i.order?.id == o.id }   // o comes from the outside
        .exists()
}
```

How lambdaQL reads the captured values of the inner lambda:

| Captured in the inner lambda | Becomes |
|---|---|
| The outer entity (`o`) | a reference to the outer alias (`e0.id`) |
| Any other value (local `val`, property, ...) | a bind parameter, as in [3.11](03-where.md#311-captured-values) |

```kotlin
val minQty = 10
.where { o, sub ->
    sub.from(Item::class.java)
        .where { i -> i.order?.id == o.id && i.qty > minQty }
        .exists()
}
// HQL: select e0 from Order e0
//      where exists (select 1 from Item s1 where s1.order.id = e0.id and s1.qty > :p1)
// parameters: {p1=10}
```

The Java test method `capturedValueInside` in [`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java) checks this HQL, the parameter and the rows.

**SQL by database (Hibernate's SQL for this HQL)** — recorded by [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java), which gives the same HQL to Hibernate directly. From `expected-sql/` [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql), [`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql), [`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql), example `05-correlated-param`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id and i1_0.qty>?)
```

**`==` in the correlation.** `i.order?.id == o.id` compares two `Long?` values. Two values of the entity are compared with the SQL meaning (decision D48, see [3.6](03-where.md#36-null-rules)), so lambdaQL writes a plain `=` (and `<>` for `!=`).
The database can use an index.

<a id="57-scalar-subqueries"></a>
## 5.7 Scalar subqueries

A subquery that gives **one value** can be used in a comparison.

Orders that cost more than the average price:

```kotlin
qb.from(Order::class.java)
    .where { o, sub -> o.price!! > sub.from(Order::class.java).avg { x -> x.price } }
    .toList()
// HQL: select e0 from Order e0
//      where e0.price > (select avg(s1.price) from Order s1)
```

**(tested)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt), region `scalar` (the test `price` is a `double`, so there is no `!!`):

```kotlin
val q = qb.from(Order::class.java).where { o, sub -> o.price > sub.from(Order::class.java).avg { x -> x.price } }
assertEquals("select e0 from Order e0 where e0.price > (select avg(s1.price) from Order s1)", q.toHql().hql())
```

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `sub-scalar`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.price>(select avg(o2_0.price) from orders o2_0)
```

A scalar subquery must **always give one row**. Two shapes are allowed (rule Q3).

| Shape | Example |
|---|---|
| An aggregate: `count()`, `sum`, `avg`, `min`, `max` | `sub.from(Item::class.java).where { i -> i.order?.id == o.id }.count()` |
| Top-1: `orderBy { ... }.limit(1)...first()` | see below |

Any other `first()` is an error at analysis time (`TranslationException`, `LQL-3017`).

Orders with two or more items (`count()`):

```kotlin
qb.from(Order::class.java)
    .where { o, sub -> sub.from(Item::class.java).where { i -> i.order?.id == o.id }.count() >= 2 }
    .toList()
// HQL: select e0 from Order e0
//      where (select count(s1) from Item s1 where s1.order.id = e0.id) >= 2L
```

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `sub-count`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where (select count(i1_0.id) from items i1_0 where i1_0.order_id=o1_0.id)>=2
```

Top-1 example: the most expensive order of the same customer.

```kotlin
qb.from(Order::class.java)
    .where { o, sub ->
        o.id == sub.from(Order::class.java)
            .where { x -> x.customer?.id == o.customer?.id }
            .orderBy { x, by -> by.desc(x.price) }
            .limit(1)
            .select { x -> x.id }
            .first()
    }
    .toList()
```

The same query is tested in Java (region `top-one`, example `sub-top-one`). lambdaQL adds the `id` as the last sort key inside the subquery (rule S9).
In the Java test, `price` and `Customer.id` are primitives. The nullable `price: Double?` and `customer?.id` of the Kotlin entities follow the NULL rules of [3.6](03-where.md#36-null-rules) and [4.4](04-order-paging.md#44-null-ordering-is-the-same-on-every-database). So the `nulls` part of the HQL and the way of comparing can be different.
The correlation compares the foreign keys (`customer?.id`), not the customer entities. See [5.10](#510-paths-and-null-inside-subqueries).

NULLs in aggregates follow SQL. `sum`, `avg`, `min` and `max` give NULL when there are no rows. `count` gives 0.

<a id="58-nested-subqueries"></a>
## 5.8 Nested subqueries

The `where` of a subquery can also take a `sub` argument. The same rules apply again at every level.

Customers who have an order with a big item:

```kotlin
qb.from(Customer::class.java)
    .where { c, sub ->
        sub.from(Order::class.java)
            .where { o, sub2 ->
                o.customer?.id == c.id &&
                    sub2.from(Item::class.java)
                        .where { i -> i.order?.id == o.id && i.qty > 10 }
                        .exists()
            }
            .exists()
    }
    .toList()
// HQL: select e0 from Customer e0
//      where exists (select 1 from Order s1 where s1.customer.id = e0.id
//                    and exists (select 1 from Item s2 where s2.order.id = s1.id and s2.qty > 10))
```

**SQL by database (from the Java test run; Kotlin gives the same HQL)** — from `expected-sql/` [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql), [`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql), [`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql), example `sub-nested`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from customers c1_0
where exists(select 1 from orders o1_0 where o1_0.customer_id=c1_0.id and exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id and i1_0.qty>10))
```

Use new names (`sub2`, `o`, `i`) at each level. Kotlin allows the same name again, but it gives a name shadowing warning, and the code is harder to read.

<a id="59-sorting-inside-a-subquery"></a>
## 5.9 Sorting inside a subquery

| Case | What lambdaQL does |
|---|---|
| `orderBy` with `limit` (top-1, top-N) | keeps it, with the `id` tie-breaker |
| `orderBy` without `limit` | **removes it** and logs a warning (it does not change the result; on Oracle it even gives the syntax error ORA-00907) |
| `in` on a subquery with `limit` (MariaDB does not support `IN (... limit n)`, error 1235) | wraps it in a derived table on every database: `in (select t1.v from (select s1.id as v ... limit n) t1)` |
| MariaDB: `lateral` (wrong results on MariaDB) | rewrite with `row_number() over (partition by ... order by ...)`: **not implemented yet** |

The NULL policy and the automatic `id` tie-breaker of [4. Sorting and paging](04-order-paging.md) also apply inside subqueries.

<a id="510-paths-and-null-inside-subqueries"></a>
## 5.10 Paths and NULL inside subqueries

Inside a subquery, conditions, paths and NULL work **exactly like the outer query** ([3. Conditions (`where`)](03-where.md)).

- `==` between two values of the entity is a value comparison with the SQL meaning (D48). A captured `null` matches no row (`LQL-4045`).
- `!!` is ignored. `?.` on a path names the same column.
- Association paths of the subquery's own entity follow the same join rule as the outer `where`. They are always an inner `join` (decision D48).
- A path that ends with `@Id` is a foreign key column (no join).
- **The outer entity** can be used only with its own columns and its foreign key ids (`o.id`, `o.customer?.id`). A path of the outer entity that needs a join (`o.customer?.name`) is an error at analysis time. Compare the foreign key ids instead.
- Collection paths (`o.items`) are rejected. To ask about a collection, use a subquery.
- Hibernate also adds the conditions of `@SQLRestriction` and `@SoftDelete` on an entity inside subqueries (`exists`, `in`) (measured on MariaDB 10.11 and Oracle 23.26). lambdaQL does not handle these conditions itself.

```kotlin
// instead of it.items.size > 0 (rejected):
.where { o, sub -> sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
```

<a id="joins-inside-a-subquery"></a>
### Joins inside a subquery

You can also join two entities inside a subquery (decision D51). The forms are `sub.from(A::class.java).join(B::class.java) { a, b -> ... }` and `leftJoin(...) { ... }`, and the result is a `From2<A, B>`.
Every lambda of `From2` gets the two rows: `where { a, b -> ... }`, `exists()`, `count()`, `contains(x)` or `first()` after `select { a, b -> v }`, and `max`, `min`, `avg`, `sum { a, b -> v }`.

- The HQL looks like `exists (select 1 from Order s1 join Item s2 on s2.order.id = s1.id where ...)`.
- An association path inside the ON condition becomes a `left join`. A path join is placed right after the root where the path starts.
- A subquery can have at most two entities. The join form cannot use `orderBy` or `limit`.
- `exists`, `sum` over the joined rows, and `in` with a `left join` and an association path in ON are measured on the three databases.

**(tested)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt), region `sub-join`:

```kotlin
// customers with an order that has an item of quantity > 10: the subquery joins Order and Item (D51)
val q = qb.from(Customer::class.java).where { c, sub ->
    sub.from(Order::class.java)
        .join(Item::class.java) { o, i -> i.order?.id == o.id }
        .where { o, i -> o.customer?.id == c.id && i.qty > 10 }
        .exists()
}
assertEquals("select e0 from Customer e0 where exists (select 1 from Order s1 join Item s2 on s2.order.id = s1.id"
        + " where s1.customer is not null and s1.customer.id = e0.id and s2.qty > 10)", q.toHql().hql())
```

In Kotlin, `o.customer?.id == c.id` adds `s1.customer is not null and ...` (see the region above). The aggregate forms are checked by the Java test ([`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java), region `sub-join`).

<a id="511-subqueries-in-other-places"></a>
## 5.11 Subqueries in other places

- **Subqueries in update / delete**: the same `sub` syntax in `where`. This is implemented (tested with Java lambdas, region `dml-subquery`). See [7.4 Delete](07-4-delete.md) and [7.2 Update](07-2-update.md).
- **Subqueries in a `set` value** (`o.total = sub.from(...).sum { ... }`): write them with `sub`, the second argument of the assignment lambda (decision D49). See [7.2 Update](07-2-update.md#an-aggregate-from-a-subquery).
- **Subqueries in `from` (derived tables) and CTEs**: implemented (milestone M8-2). They do not use `sub`. Pass a value query to `qb.from(...)` or `qb.with(...)`. See [6.9 Derived table](06-select-group.md#69-derived-table-subquery-in-from) and [6.10 CTE](06-select-group.md#610-cte-with).

<a id="512-rules-summary-q1-to-q6"></a>
## 5.12 Rules summary (Q1 to Q6)

| # | Rule |
|---|---|
| Q1 | Conditions, paths and NULL rules inside a subquery are the same as outside. Association paths in conditions also follow the same join rule (inner `join` by default). A path of the outer entity that needs a join is an error. Foreign key ids are allowed |
| Q2 | **NOT IN keeps the SQL meaning** (D48): `x !in values` becomes `x not in (select c.sel from C c where <condition>)`. If the subquery has a NULL, no rows are returned (`LQL-5009` at `DEBUG` level). To skip NULLs, add `c.sel != null` to the subquery |
| Q3 | A scalar subquery must give one row: an aggregate (`max`, `min`, `avg`, `sum`, `count`) or `orderBy { }.limit(1)...first()`. Any other `first()` is an error |
| Q4 | Subquery sorts: kept with `limit`, removed with a warning without `limit`. `IN (... limit)` is wrapped in a derived table. The MariaDB `lateral` rewrite with `row_number()` is not implemented yet |
| Q5 | In a correlation, `==` is written as `=` and `!=` as `<>` (the SQL meaning, D48). If either side is NULL, it does not match. The index can still be used |
| Q6 | The `where` of a subquery can take its own `sub` argument: `where { i, sub2 -> ... }`. The same rules apply at every level |

---

[Kotlin track](README.md) | Previous: [4. Sorting and paging](04-order-paging.md) | Next: [6. Select, aggregates and group by](06-select-group.md) | [Java version of this page](../java/05-subquery.md)
