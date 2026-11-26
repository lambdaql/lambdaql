<a id="75-insert"></a>
# 7.5 插入（Kotlin）

[7. 更新、插入、删除（Kotlin）](07-dml.md) | [7.2 更新](07-2-update.md) | [7.4 删除](07-4-delete.md) | **7.5 插入** | [7.6 Upsert](07-6-upsert.md) | [Java](../java/07-5-insert.md)

---

<a id="one-row"></a>
## 一行

<!-- region: ManualKotlinDmlTest.kt#insert -->
```kotlin
val i = qb.insert(Archive::class.java)
    .values { it.id = 100L; it.price = 9.5; it.customerName = "Kim" }
assertEquals("insert into Archive (id, price, customerName) values (100L, 9.5, 'Kim')", i.toHql()[0].hql())
val inserted = i.execute()
// a generated id (IDENTITY) can be left out
val generated = qb.insert(IdOrder::class.java).values { it.price = 1.0; it.status = 1 }.execute()
// the values cannot read the new row
val error = assertThrows(LambdaQLException::class.java) {
    qb.insert(Archive::class.java).values { it.id = 1L; it.price = it.price }
}
```

**各数据库的 SQL**

<!-- sql: insert -->
```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price,customer_name)
values (100,9.5,'Kim')
```

写在 Lambda 表达式中的常量保持为字面量。值不能读取新行，`it.price = it.price` 是错误。

`val` 属性用 `Dml.value` 写入一行（见 [7.3](07-dml.md#73-two-ways-to-assign-property-or-dmlvalue)）。

<!-- region: ManualKotlinDmlTest.kt#insert-value -->
```kotlin
// properties without a setter (val): Dml.value
val i = qb.insert(Archive::class.java).values { a -> value(a.id, id); value(a.price, 1.5) }
assertEquals("insert into Archive (id, price) values (:p1, 1.5)", i.toHql()[0].hql())
```

<a id="a-subquery-in-a-value"></a>
### 值中的子查询

使用像 `values { a, sub -> ... }` 这样带两个参数的 Lambda 表达式时，可以用第二个参数 `sub` 构造子查询。
upsert 的 `values { a, sub -> ... }` 和 `onConflict { ... }.update { a, sub -> ... }` 也一样（见 [7.6](07-6-upsert.md#your-own-change-onconflictkeyupdate)）。

<!-- region: KotlinQueryTest.kt#insert-values-sub -->
```kotlin
// values { a, sub -> ... }: a subquery can read captured values, not the new row
val i = qb.insert(Archive::class.java)
    .values { a, sub -> a.id = 100L; a.price = sub.from(Item::class.java).sum { it.price } }
assertEquals("insert into Archive (id, price) values (100L, (select sum(s1.price) from Item s1))", i.toHql()[0].hql())
```

- 读取其他表的聚合（无论有没有捕获参数）、多行、读取目标表自身的子查询（没有 MariaDB 错误 1093）都可以运行（在 H2、MariaDB 10.11、Oracle 23.26 上实测）。
- 子查询可以读取捕获的值，但不能读取新插入的行。
- 列表形式也可以使用子查询（见[从列表插入多行](#many-rows-from-a-list)）。
- upsert 的插入值和 `do update set` 中的子查询也可以运行。在 Oracle 上，Hibernate 以 `merge` 执行（实测）。示例见 [7.6](07-6-upsert.md#your-own-change-onconflictkeyupdate)。

示例代码：[`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`insert`、`insert-value`）、[`KotlinQueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)（`insert-values-sub`）。

<a id="many-rows-from-a-list"></a>
## 从列表插入多行

为列表的每个元素插入一行。第二个 Lambda 表达式参数是元素。

<!-- region: ManualKotlinDmlTest.kt#insert-list -->
```kotlin
val lines = listOf(Line(201L, 1.0), Line(202L, 2.0))      // data class Line(val id: Long, val price: Double)
val i = qb.insert(Archive::class.java).values(lines) { a, l -> a.id = l.id; a.price = l.price }
assertEquals("insert into Archive (id, price) values (:p1, :p2), (:p3, :p4)", i.toHql()[0].hql())
```

**各数据库的 SQL**

<!-- sql: insert-list -->
```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price)
values (?,?), (?,?)
```

lambdaQL 在 Java 中读取元素的属性，因此所有值都成为绑定参数。
`Line` 是普通的数据类。写出的是一条带多个 VALUES 行的语句。列表很长时，会拆分为每条语句不超过 500 个值（见 [7.8](07-dml.md#batching-for-many-rows)）。空列表不执行 SQL，返回 0。

**列表形式的子查询。** 可以像 `values(list) { a, l, sub -> ... }` 这样用第三个参数构造子查询。
子查询可以读取元素的值。这些值在查询前读取，并按每行一个参数绑定。不能读取新插入的行（在三种数据库上实测）。

<!-- region: ManualKotlinDmlTest.kt#insert-list-sub -->
```kotlin
// one row per element; the subquery reads the element's value
val i = qb.insert(Archive::class.java).values(refs) { a, r, sub ->
    a.id = r.id
    a.price = sub.from(Item::class.java).where { x -> x.order?.id == r.orderId }.sum { x -> x.price }
}
assertEquals("insert into Archive (id, price) values (:p1, (select sum(s1.price) from Item s1 where s1.order.id = :p2)),"
    + " (:p3, (select sum(s1.price) from Item s1 where s1.order.id = :p4))", i.toHql()[0].hql())
```

`Ref` 是 `data class Ref(val id: Long, val orderId: Long?)`。

示例代码：[`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`insert-list`、`insert-list-sub`）。

<a id="generated-id"></a>
## 生成的 id

id 使用 IDENTITY 或 SEQUENCE 时，可以省略 id。
第一个示例中的 `IdOrder` 一行就是这种情况。`IdOrder` 的 id 是生成的值。

<a id="insert--select"></a>
## insert ... select

从另一个实体复制行。`select` Lambda 表达式接收目标（`a`）和源（`o`）。

<!-- region: ManualKotlinDmlTest.kt#insert-select -->
```kotlin
val i = qb.insert(Archive::class.java)
    .from(Order::class.java)
    .where { it.status.toInt() == 1 }
    .select { a, o -> a.id = o.id; a.price = o.price; a.customerName = o.customer?.name }
// a path in the select list: left join, so orders without a customer stay
assertEquals("insert into Archive (id, price, customerName) select e0.id, e0.price, j1.name"
    + " from Order e0 left join e0.customer j1 where e0.status = 1", i.toHql()[0].hql())
```

**各数据库的 SQL**

<!-- sql: insert-select -->
```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price,customer_name)
select o1_0.id,o1_0.price,c1_0.name
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where o1_0.status=1
```

lambdaQL 对源路径使用显式的 `left join`，因此没有客户的订单也不会遗漏。三种数据库上的 SQL 相同。

目标为 `val` 属性时使用 `Dml.value`。第一个参数是目标 `a` 的属性。

<!-- region: ManualKotlinDmlTest.kt#insert-select-value -->
```kotlin
// Dml.value in insert ... select: the first argument is a property of the target a
val i = qb.insert(Archive::class.java).from(Order::class.java).where { it.status.toInt() == 3 }
    .select { a, o -> value(a.id, o.id); value(a.customerName, o.customer?.name) }
assertEquals("insert into Archive (id, customerName) select e0.id, j1.name from Order e0 left join e0.customer j1"
    + " where e0.status = 3", i.toHql()[0].hql())
```

**子查询。** 源的 `where` 用第二个参数、`select` 用第三个参数接收子查询的起点 `sub`。

<!-- region: ManualKotlinDmlTest.kt#insert-select-sub -->
```kotlin
// where { o, sub -> ... } on the source, and select { a, o, sub -> ... }: the third parameter starts a subquery
val i = qb.insert(Archive::class.java).from(Order::class.java)
    .where { o, sub -> sub.from(Item::class.java).where { x -> x.order?.id == o.id }.exists() }
    .select { a, o, sub -> a.id = o.id; a.price = sub.from(Item::class.java).where { x -> x.order?.id == o.id }.sum { x -> x.price } }
assertEquals("insert into Archive (id, price) select e0.id, (select sum(s2.price) from Item s2 where s2.order.id = e0.id)"
    + " from Order e0 where exists (select 1 from Item s1 where s1.order.id = e0.id)", i.toHql()[0].hql())
```

示例代码：[`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`insert-select`、`insert-select-value`、`insert-select-sub`）。

<a id="insert--select-with-aggregates"></a>
## 使用聚合的 insert ... select

使用 [6. 查询列、聚合与分组](06-select-group.md) 中的 `groupBy`、`having` 和 `Agg` 函数。
调用顺序是 `from(...)`、`where { }`、`groupBy { }`、`having { }`、`select { }`。`where`、`groupBy`、`having` 可以省略。

<!-- region: ManualKotlinDmlTest.kt#insert-group -->
```kotlin
// INSERT ... SELECT with groupBy: one summary row per customer
val i = qb.insert(Summary::class.java)
    .from(Order::class.java)
    .where { it.customer != null }
    .groupBy { it.customer?.id }
    .having { Agg.count() >= 2 }
    .select { s, o -> s.customerId = o.customer?.id; s.total = Agg.sum(o.price); s.cnt = Agg.count() }
assertEquals("insert into Summary (customerId, total, cnt) select e0.customer.id, sum(e0.price), count(*) from Order e0"
    + " where e0.customer is not null group by e0.customer.id having count(*) >= 2L", i.toHql()[0].hql())
```

**各数据库的 SQL**

<!-- sql: insert-group -->
```sql
-- H2, MariaDB, Oracle (same)
insert into summary(customer_id,total,cnt)
select o1_0.customer_id,sum(o1_0.price),count(*)
from orders o1_0
where o1_0.customer_id is not null
group by o1_0.customer_id
having count(*)>=2
```

- 所有非聚合值都必须是 `groupBy` 键。否则 lambdaQL 会在分析时抛出 `TranslationException`（`LQL-3035`）。
- `where` 中不能使用 `Agg`。请使用 `having`。

---

[7. 更新、插入、删除（Kotlin）](07-dml.md) | [7.2 更新](07-2-update.md) | [7.4 删除](07-4-delete.md) | **7.5 插入** | [7.6 Upsert](07-6-upsert.md) | [Java](../java/07-5-insert.md)

示例代码：[`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`insert-group`）。
