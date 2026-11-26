<a id="5-subqueries-kotlin"></a>
# 5. Subqueries (Kotlin)

[Kotlin track](README.md) | [Manual home](../README.md) | Previous: [4. Sorting and paging](04-order-paging.md) | Next: [6. Select, aggregates and group by](06-select-group.md) | [Java version of this page](../java/05-subquery.md)

Common pages: [1. Overview](../01-overview.md) · [11. Errors and languages](../11-errors-and-languages.md)

---

<a id="51-the-idea-a-second-lambda-argument-sub"></a>
## 5.1 The idea: a second lambda argument `sub`

A `where` lambda can take a subquery factory as its **second argument**. In this manual it is called `sub` (you can use any name).
Inside it, you write subqueries with the **same lambda syntax** as normal queries.

`where { ... }` with one argument (`it`) has no subqueries, and `where { o, sub -> ... }` with two arguments has subqueries.
lambdaQL tells the two forms apart by the number of arguments. Kotlin picks the right overload.
You can mix subqueries freely with `&&`, `||` and `!`. No import is needed.

What you can do with `sub`:

| Subquery | Use | HQL |
|---|---|---|
| `sub.from(X::class.java).where { ... }.exists()` | Condition | `exists (select 1 from X x where ...)` |
| `!sub.from(X::class.java).where { ... }.exists()` | Condition | `not exists (...)` |
| `y in sub.from(X::class.java).where { ... }.select { x -> v }` | Condition | `y in (select x.v from X x where ...)` |
| `y !in sub.from(...)...select { ... }` | Condition | `y not in (select x.v from X x where ...)` |
| `sub.from(X::class.java).avg { x -> v }` (also `sum`, `min`, `max`, `count()`) | Value | `(select avg(x.v) from X x)` |
| `sub.from(X::class.java)...orderBy { ... }.limit(1).select { x -> v }.first()` | Value | Top-1 subquery |
| `sub.from(A::class.java).join(B::class.java) { a, b -> ... }.where { a, b -> ... }.exists()` | Condition | `exists (select 1 from A a join B b on ... where ...)` ([Joins inside a subquery](#joins-inside-a-subquery)) |

> [!NOTE]
> The return values (`Boolean`, `Double`, ...) are only placeholders so that the code compiles.
> These functions are used only inside a lambdaQL lambda. lambdaQL does not call the lambda. It reads the bytecode opcodes.

In Kotlin, `y in values` compiles to `values.contains(y)`. So the Kotlin `in` operator works with the subquery API with no extra code.

An inner lambda (`{ i -> ... }` inside `sub.from(...).where { }`) does not need to be `Serializable` on its own. lambdaQL finds it in the bytecode opcodes of the outer lambda.

In the HQL, the aliases of subquery roots are `s1`, `s2`, ... (the outer root is `e0`). Joins inside a subquery get aliases such as `s1j1`.

The examples use the example entities written in Java. `price` is a primitive `double`, `qty` is an `int`, `status` is a `short`, and `Customer.id` is an `int`.

## 5.2 EXISTS

Orders that have at least one item with `qty > 10`:

<!-- region: KotlinQueryTest.kt#exists -->
```kotlin
val q = qb.from(Order::class.java)
    .where { o, sub -> sub.from(Item::class.java).where { i -> i.order?.id == o.id && i.qty > 10 }.exists() }
assertEquals("select e0 from Order e0 where exists (select 1 from Item s1 where s1.order.id = e0.id and s1.qty > 10)",
    q.toHql().hql())
```

**SQL by database**

<!-- sql: sub-exists -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id and i1_0.qty>10)
```

This is a **correlated** subquery. The inner lambda uses the outer `o` (see [5.6](#56-correlated-subqueries)).

Example code: [`KotlinQueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt) (`exists`).

## 5.3 NOT EXISTS

Use `!`. You can combine it with other conditions.

<!-- region: ManualKotlinSubqueryTest.kt#not-exists -->
```kotlin
// orders with status 1, or orders without items
val q = qb.from(Order::class.java)
    .where { o, sub -> o.status.toInt() == 1 || !sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
assertEquals("select e0 from Order e0 where e0.status = 1 or not exists (select 1 from Item s1 where s1.order.id = e0.id)",
    q.toHql().hql())
```

**SQL by database**

<!-- sql: sub-not-exists -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 or not exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id)
```

Example code: [`ManualKotlinSubqueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSubqueryTest.kt) (`not-exists`).

## 5.4 IN

Orders of customers with `grade > 3`. Use `in`.

<!-- region: KotlinQueryTest.kt#in -->
```kotlin
val q = qb.from(Order::class.java)
    .where { o, sub -> o.customer?.id in sub.from(Customer::class.java).where { c -> c.grade!! > 3 }.select { c -> c.id } }
assertEquals("select e0 from Order e0 where e0.customer.id in (select s1.id from Customer s1 where s1.grade > 3)",
    q.toHql().hql())
```

`o.customer?.id` ends with the customer's `@Id`, so it is the foreign key column `e0.customer.id`. No join is needed.
`!!` is ignored. `c.grade!! > 3` is `s1.grade > 3`.

Example code: [`KotlinQueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt) (`in`).

<a id="55-not-in-is-written-as-not-exists"></a>
## 5.5 NOT IN and NULL

`x !in values` is written as `NOT IN`, with the SQL meaning as it is. The example finds orders whose status is not the same as the grade of any customer.

<!-- region: ManualKotlinSubqueryTest.kt#not-in -->
```kotlin
// the SQL meaning: NOT IN. The grades are 5, 2 and NULL, so no row matches: x NOT IN (5, 2, NULL)
// is never true. To skip the NULL, say so in the subquery: where { c -> c.grade != null }
val q = qb.from(Order::class.java)
    .where { o, sub -> o.status.toInt() !in sub.from(Customer::class.java).select { c -> c.grade } }
assertEquals("select e0 from Order e0 where e0.status not in (select s1.grade from Customer s1)", q.toHql().hql())
val withoutNull = qb.from(Order::class.java)
    .where { o, sub -> o.status.toInt() !in sub.from(Customer::class.java).where { c -> c.grade != null }.select { c -> c.grade } }
assertEquals("select e0 from Order e0 where e0.status not in (select s1.grade from Customer s1 where s1.grade is not null)",
    withoutNull.toHql().hql())
```

`status` is a `short` and `grade` is an `Int?`, so `o.status.toInt()` makes them match. lambdaQL ignores this conversion.

**SQL by database (first query)**

<!-- sql: sub-not-in -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status not in (select c2_0.grade from customers c2_0)
```

Why:

- `x NOT IN (subquery)` returns **no rows at all** if the subquery returns even one NULL. lambdaQL keeps this SQL meaning and logs `LQL-5009` at `DEBUG` level.
- To skip NULL, write a null check in the subquery (`where { c -> c.grade != null }`), like `withoutNull` in the example above.
  The example data has a NULL grade, so the first query returns no rows.
- `!sub...exists()` is still written as `NOT EXISTS`.

Example code: [`ManualKotlinSubqueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSubqueryTest.kt) (`not-in`).

<a id="56-correlated-subqueries"></a>
## 5.6 Correlated subqueries

If the inner lambda uses the outer entity, the subquery is a **correlated** subquery. No special syntax is needed. Use the outer variable as it is.

How lambdaQL reads the captured values of the inner lambda:

| Captured in the inner lambda | Result |
|---|---|
| The outer entity (`o`) | A reference to the outer alias (`e0.id`) |
| Any other value (local `val`, property, ...) | A bind parameter, as in [3.11](03-where.md#311-captured-values) |

<!-- region: ManualKotlinSubqueryTest.kt#correlated-param -->
```kotlin
// o comes from the outer query; minQty is a captured value: a bind parameter
val minQty = 10
val q = qb.from(Order::class.java)
    .where { o, sub -> sub.from(Item::class.java).where { i -> i.order?.id == o.id && i.qty > minQty }.exists() }
assertEquals("select e0 from Order e0 where exists (select 1 from Item s1 where s1.order.id = e0.id and s1.qty > :p1)",
    q.toHql().hql())
assertEquals(mapOf("p1" to 10), q.toHql().parameters())
```

**`==` in the correlation condition.** `i.order?.id == o.id` compares two `Long?` values. Two entity values are compared with the SQL meaning (see [3.6](03-where.md#36-null-rules)), so lambdaQL writes a simple `=` (`<>` for `!=`).
The database can use an index.

Example code: [`ManualKotlinSubqueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSubqueryTest.kt) (`correlated-param`).

<a id="57-scalar-subqueries"></a>
## 5.7 Scalar subqueries

A subquery that gives **one value** can be used in a comparison.

Orders more expensive than the average price:

<!-- region: KotlinQueryTest.kt#scalar -->
```kotlin
val q = qb.from(Order::class.java).where { o, sub -> o.price > sub.from(Order::class.java).avg { x -> x.price } }
assertEquals("select e0 from Order e0 where e0.price > (select avg(s1.price) from Order s1)", q.toHql().hql())
```

**SQL by database**

<!-- sql: sub-scalar -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.price>(select avg(o2_0.price) from orders o2_0)
```

A scalar subquery must **always give one row**. Two forms are allowed.

| Form | Example |
|---|---|
| Aggregate: `count()`, `sum`, `avg`, `min`, `max` | `sub.from(Item::class.java).where { i -> i.order?.id == o.id }.count()` |
| Top-1: `orderBy { ... }.limit(1)...first()` | See below |

Any other `first()` is an analysis-time error (`TranslationException`, `LQL-3017`).

Orders with two or more items (`count()`):

<!-- region: ManualKotlinSubqueryTest.kt#count -->
```kotlin
// orders with two or more items
val q = qb.from(Order::class.java)
    .where { o, sub -> sub.from(Item::class.java).where { i -> i.order?.id == o.id }.count() >= 2 }
assertEquals("select e0 from Order e0 where (select count(s1) from Item s1 where s1.order.id = e0.id) >= 2L", q.toHql().hql())
```

**SQL by database**

<!-- sql: sub-count -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where (select count(i1_0.id) from items i1_0 where i1_0.order_id=o1_0.id)>=2
```

Top-1 example: the most expensive order of the same customer.

<!-- region: ManualKotlinSubqueryTest.kt#top-one -->
```kotlin
// the most expensive order of each customer: a correlated scalar subquery with orderBy and limit(1)
val q = qb.from(Order::class.java)
    .where { o, sub ->
        o.id == sub.from(Order::class.java)
            .where { x -> x.customer?.id == o.customer?.id }
            .orderBy { x -> desc(x.price) }
            .limit(1)
            .select { x -> x.id }
            .first()
    }
assertEquals("select e0 from Order e0 where e0.id = (select s1.id from Order s1 where s1.customer.id = e0.customer.id"
    + " order by s1.price desc, s1.id asc limit 1)", q.toHql().hql())
// first() without orderBy and limit(1) is rejected
val e = assertThrows(LambdaQLException::class.java) {
    qb.from(Order::class.java).where { o, sub -> o.id == sub.from(Order::class.java).select { x -> x.id }.first() }
}
assertEquals("LQL-3017", e.code())
```

lambdaQL adds `id` as the last sort key inside the subquery.
The correlation condition compares the foreign key (`customer?.id`), not the customer entity. For an order without a customer, `s1.customer.id = e0.customer.id` is not true, so the order is left out of the result. See [5.10](#510-paths-and-null-inside-subqueries).

NULL in aggregates follows SQL. `sum`, `avg`, `min` and `max` give NULL when there are no rows. `count` gives 0.

Example code: [`KotlinQueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt) (`scalar`), [`ManualKotlinSubqueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSubqueryTest.kt) (`count`, `top-one`).

<a id="58-nested-subqueries"></a>
## 5.8 Nested subqueries

The `where` of a subquery can also take a `sub` argument. The same rules apply again at every level.

Customers who have an order with a large item:

<!-- region: ManualKotlinSubqueryTest.kt#nested -->
```kotlin
// customers that have an order with an item of quantity > 10: a subquery inside a subquery
val q = qb.from(Customer::class.java)
    .where { c, sub ->
        sub.from(Order::class.java)
            .where { o, sub2 ->
                o.customer?.id == c.id &&
                    sub2.from(Item::class.java).where { i -> i.order?.id == o.id && i.qty > 10 }.exists()
            }
            .exists()
    }
assertEquals("select e0 from Customer e0 where exists (select 1 from Order s1 where s1.customer is not null"
    + " and s1.customer.id = e0.id and exists (select 1 from Item s2 where s2.order.id = s1.id and s2.qty > 10))", q.toHql().hql())
```

Kotlin's `o.customer?.id == c.id` adds `s1.customer is not null and ...`. The SQL below comes from HQL with the same meaning without this condition.

Use a new name at each level (`sub2`, `o`, `i`). Kotlin allows you to use the same name again, but it gives a shadowing warning, and the code is harder to read.

Example code: [`ManualKotlinSubqueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSubqueryTest.kt) (`nested`).

<a id="59-sorting-inside-a-subquery"></a>
## 5.9 Sorting inside a subquery

| Case | What lambdaQL does |
|---|---|
| `orderBy` with `limit` (top-1, top-N) | Kept, with the `id` tie-breaker |
| `orderBy` without `limit` | **Removed**, and a warning is logged (it does not change the result. On Oracle it is even the syntax error ORA-00907) |
| `in` on a subquery with `limit` (MariaDB does not support `IN (... limit n)`, error 1235) | Wrapped in a derived table on every database: `in (select t1.v from (select s1.id as v ... limit n) t1)` |

The NULL policy and the automatic `id` tie-breaker from [4. Sorting and paging](04-order-paging.md) also apply inside a subquery.

<a id="510-paths-and-null-inside-subqueries"></a>
## 5.10 Paths and NULL inside subqueries

Inside a subquery, conditions, paths and NULL work **exactly as in the outer query** ([3. Conditions (`where`)](03-where.md)).

- `==` between entity values is a value comparison with the SQL meaning. A captured `null` matches no row (`LQL-4045`).
- `!!` does not change the value. `?.` and `!!` in a path name the same column.
- An association path on the subquery's own entity follows the same join rule as the outer `where`. If a row without the association can match the condition, it is a `left join`. If it cannot, it is an inner `join`.
- A path that ends with `@Id` is the foreign key column (no join).
- The **outer entity** can use only its own columns and foreign key ids (`o.id`, `o.customer?.id`). A path of the outer entity that needs a join (`o.customer?.name`) is an analysis-time error (`LQL-3047`). Compare the foreign key id instead.
- Collection paths (`@OneToMany` such as `Customer.orders`) are rejected (`LQL-3010`). To ask about a collection, use a subquery.
- Hibernate also adds the `@SQLRestriction` and `@SoftDelete` conditions of the entity inside subqueries (`exists`, `in`) (measured on MariaDB 10.11 and Oracle 23.26). lambdaQL does not handle these conditions on its own.

<!-- region: ManualKotlinSubqueryTest.kt#rejected-paths -->
```kotlin
// a collection path is rejected: ask about the collection with a subquery
val collection = assertThrows(LambdaQLException::class.java) {
    qb.from(Customer::class.java).where { it.orders.size > 0 }.toHql()
}
assertEquals("LQL-3010", collection.code())
val withOrders = qb.from(Customer::class.java)
    .where { c, sub -> sub.from(Order::class.java).where { o -> o.customer?.id == c.id }.exists() }
assertEquals("select e0 from Customer e0 where exists (select 1 from Order s1 where s1.customer is not null and s1.customer.id = e0.id)",
    withOrders.toHql().hql())
// a path of the outer entity that needs a join is rejected: compare the foreign key id instead
val outerJoin = assertThrows(LambdaQLException::class.java) {
    qb.from(Order::class.java)
        .where { o, sub -> sub.from(Customer::class.java).where { c -> c.name == o.customer?.name }.exists() }.toHql()
}
assertEquals("LQL-3047", outerJoin.code())
```

<a id="joins-inside-a-subquery"></a>
### Joins inside a subquery

You can also join two entities inside a subquery. The forms are `sub.from(A::class.java).join(B::class.java) { a, b -> ... }` and `leftJoin(...) { ... }`, and the result is a `From2<A, B>`.
Every lambda of `From2` takes two rows: `where { a, b -> ... }`, `exists()`, `count()`, `contains(x)` or `first()` after `select { a, b -> v }`, and `max`, `min`, `avg`, `sum { a, b -> v }`.

- The HQL looks like `exists (select 1 from Order s1 join Item s2 on s2.order.id = s1.id where ...)`.
- An association path inside the ON condition becomes a `left join`. A path join is placed right after the root where the path starts.
- A subquery can have at most two entities. The join form cannot use `orderBy` or `limit`.
- `exists`, `sum` of joined rows, and `in` with a `left join` and an association path in ON were measured on three databases.

<!-- region: KotlinQueryTest.kt#sub-join -->
```kotlin
// customers with an order that has an item of quantity > 10: the subquery joins Order and Item
val q = qb.from(Customer::class.java).where { c, sub ->
    sub.from(Order::class.java)
        .join(Item::class.java) { o, i -> i.order?.id == o.id }
        .where { o, i -> o.customer?.id == c.id && i.qty > 10 }
        .exists()
}
assertEquals("select e0 from Customer e0 where exists (select 1 from Order s1 join Item s2 on s2.order.id = s1.id"
        + " where s1.customer is not null and s1.customer.id = e0.id and s2.qty > 10)", q.toHql().hql())
```

Kotlin's `o.customer?.id == c.id` adds `s1.customer is not null and ...` (see the example above).

An aggregate over joined rows (`sum { a, b -> v }`):

<!-- region: ManualKotlinSubqueryTest.kt#sub-join-sum -->
```kotlin
// an aggregate over the joined rows: customers whose items have a total quantity over 30
val q = qb.from(Customer::class.java).where { c, sub ->
    sub.from(Order::class.java)
        .join(Item::class.java) { o, i -> i.order?.id == o.id }
        .where { o, _ -> o.customer?.id == c.id }
        .sum { _, i -> i.qty } > 30
}
assertEquals("select e0 from Customer e0 where (select sum(s2.qty) from Order s1 join Item s2 on s2.order.id = s1.id"
    + " where s1.customer is not null and s1.customer.id = e0.id) > 30", q.toHql().hql())
```

Example code: [`ManualKotlinSubqueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSubqueryTest.kt) (`rejected-paths`, `sub-join-sum`), [`KotlinQueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt) (`sub-join`).

<a id="511-subqueries-in-other-places"></a>
## 5.11 Subqueries in other places

- **Subqueries in update / delete**: use the same `sub` syntax in `where`. See [7.4 Delete](07-4-delete.md) and [7.2 Update](07-2-update.md).
- **A subquery inside a `set` value** (`o.total = sub.from(...).sum { ... }`): write it with `sub`, the second argument of the assignment lambda. See [7.2 Update](07-2-update.md#an-aggregate-from-a-subquery).
- **A subquery in `from` (derived table) and CTE**: implemented. You do not use `sub`. Pass a value query to `qb.from(...)` or `qb.with(...)`. See [6.9 Derived table](06-select-group.md#69-derived-table-subquery-in-from) and [6.10 CTE](06-select-group.md#610-cte-with).

<a id="512-rules-summary-q1-to-q6"></a>
## 5.12 Rules summary (Q1 to Q6)

| # | Rule |
|---|---|
| Q1 | The rules for conditions, paths and NULL inside a subquery are the same as outside. An association path in a condition also follows the same join rule (`left join` if a row without the association can match the condition, otherwise inner `join`). A path of the outer entity that needs a join is an error. Foreign key ids are allowed |
| Q2 | **NOT IN keeps the SQL meaning**: `x !in values` becomes `x not in (select c.sel from C c where <condition>)`. If the subquery has a NULL, no rows are returned (`LQL-5009` at `DEBUG` level). To skip NULL, add `c.sel != null` to the subquery |
| Q3 | A scalar subquery must give one row: an aggregate (`max`, `min`, `avg`, `sum`, `count`) or `orderBy { }.limit(1)...first()`. Any other `first()` is an error |
| Q4 | Subquery sort: kept with `limit`, removed with a warning without it. `IN (... limit)` is wrapped in a derived table |
| Q5 | In a correlation condition, `==` is written as `=` and `!=` as `<>` (SQL meaning). If either side is NULL, it does not match. Indexes can still be used |
| Q6 | The `where` of a subquery can take its own `sub` argument: `where { i, sub2 -> ... }`. The same rules apply at every level |

---

[Kotlin track](README.md) | Previous: [4. Sorting and paging](04-order-paging.md) | Next: [6. Select, aggregates and group by](06-select-group.md) | [Java version of this page](../java/05-subquery.md)
