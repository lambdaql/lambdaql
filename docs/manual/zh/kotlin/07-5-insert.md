<a id="75-insert"></a>
# 7.5 插入（Kotlin）

[7. 更新、插入、删除（Kotlin）](07-dml.md) | [7.2 更新](07-2-update.md) | [7.4 删除](07-4-delete.md) | **7.5 插入** | [7.6 Upsert](07-6-upsert.md) | [Java](../java/07-5-insert.md)

---


<a id="one-row"></a>
## 一行

```kotlin
val i = qb.insert(Archive::class.java)
    .values { it.id = 100L; it.price = 9.5; it.customerName = "Kim" }
// HQL: insert into Archive (id, price, customerName) values (100L, 9.5, 'Kim')
val inserted = i.execute()
// a generated id (IDENTITY) can be left out
val generated = qb.insert(IdOrder::class.java).values { it.price = 1.0; it.status = 1 }.execute()
```

**关于 SQL 块。** `…` 是缩短的 select 列表（实体的所有列），`?` 是绑定参数。这些行来自 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)。该测试使用 Hibernate 6.6.5 在 H2 2.3、MariaDB 10.11 和 Oracle 23.26 上运行每个查询，如果 Hibernate 的 SQL 发生变化，测试就会失败。该测试使用 Java Lambda。含义相同的 Kotlin Lambda 生成相同的 HQL，因此 SQL 也相同。

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `insert`：

```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price,customer_name)
values (100,9.5,'Kim')
```

**(已测试)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)，region `insert-delete`：

```kotlin
val i = qb.insert(Archive::class.java).values { it.id = 100L; it.price = 9.5; it.customerName = "Kim" }
assertEquals(1, i.execute())
val d = qb.delete(Order::class.java).where { o, sub -> !sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
assertEquals("delete from Order e0 where not exists (select 1 from Item s1 where s1.order.id = e0.id)", d.toHql().hql())
```

前两行用 Kotlin Lambda 插入一行。delete 部分在 [7.4](07-4-delete.md) 中说明。
更多用 Java 写的语句在 [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) 中测试（H2、MariaDB 和 Oracle）。这些示例见[本页的 Java 版本](../java/07-5-insert.md)。

Lambda 中的常量仍是字面量（决策 D37）。值不能读取新行：`it.price = it.price` 是错误。

`val` 属性在一行中用 `Dml.value` 写入（决策 D49，见 [7.3](07-dml.md#73-two-ways-to-assign-property-or-dmlvalue)）。

```kotlin
qb.insert(Order::class.java).values { o -> value(o.id, id); value(o.status, 1) }.execute()
```

<a id="a-subquery-in-a-value"></a>
### 值中的子查询

使用 `values { a, sub -> ... }` 这样带两个参数的 Lambda 时，可以用第二个参数 `sub` 构造子查询（决策 D49）。
upsert 的 `values { a, sub -> ... }` 和 `onConflict { ... }.update { a, sub -> ... }` 也一样（见 [7.6](07-6-upsert.md#your-own-change-onconflictkeyupdate)）。

**(已测试)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)，region `insert-values-sub`：

```kotlin
// values { a, sub -> ... }: a subquery can read captured values, not the new row (D49)
val i = qb.insert(Archive::class.java)
    .values { a, sub -> a.id = 100L; a.price = sub.from(Item::class.java).sum { it.price } }
assertEquals("insert into Archive (id, price) values (100L, (select sum(s1.price) from Item s1))", i.toHql()[0].hql())
```

- 读取其他表聚合的子查询（无论有没有捕获参数）、返回多行的子查询、读取目标表自身的子查询（MariaDB 不出现错误 1093）都可以工作（在 H2、MariaDB 10.11、Oracle 23.26 上实测）。
- 子查询可以读取捕获值，但不能读取正在插入的新行。
- 列表形式也可以使用子查询（见[从列表插入多行](#many-rows-from-a-list)）。
- upsert 的插入值和 `do update set` 中的子查询也可以工作。在 Oracle 上，Hibernate 以 `merge` 执行（实测）。upsert 形式由 Java 测试（[`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)，region `insert-values-sub`）确认。

<a id="many-rows-from-a-list"></a>
## 从列表插入多行

列表的每个元素对应一行。第二个 Lambda 参数是元素。

```kotlin
val lines = listOf(Line(201L, 1.0), Line(202L, 2.0))
val i = qb.insert(Archive::class.java).values(lines) { a, l -> a.id = l.id; a.price = l.price }
// HQL: insert into Archive (id, price) values (:p1, :p2), (:p3, :p4)
```

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `insert-list`：

```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price)
values (?,?), (?,?)
```

lambdaQL 在 Java 中读取元素的属性，所以每个值都是绑定参数。
它会写出一条带多个 VALUES 行的语句。较长的列表会被拆分，使每条语句最多有 500 个值（见 [7.8](07-dml.md#batching-for-many-rows)）。空列表不执行 SQL，返回 0。

**列表形式中的子查询。** 可以像 `values(list) { a, l, sub -> ... }` 这样，用第三个参数构造子查询（决策 D49）。`qb.upsert(X::class.java).values(list) { a, l, sub -> ... }` 也一样。
子查询可以读取元素的值。这些值在查询之前读取，每行绑定为一个参数。子查询不能读取正在插入的新行（在三种数据库上实测）。
HQL 为 `values (:p1, (select sum(s1.price) from Item s1 where s1.order.id = :p2)), (:p3, ...)`。由 Java 测试（[`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)，region `insert-list-sub`）确认。

<a id="generated-id"></a>
## 生成的 id

如果 id 使用 IDENTITY 或 SEQUENCE，可以省略 id（规则 U12）。
第一个示例中的 `IdOrder` 那一行就是这样做的：`IdOrder` 的 id 是自动生成的。

<a id="insert--select"></a>
## Insert ... select

从另一个实体复制行。`select` Lambda 接收目标（`a`）和源（`o`）。

```kotlin
val i = qb.insert(Archive::class.java)
    .from(Order::class.java)
    .where { it.status == 1 }
    .select { a, o -> a.id = o.id; a.price = o.price; a.customerName = o.customer?.name }
// a path in the select list: left join, so orders without a customer stay (U10)
// HQL: insert into Archive (id, price, customerName) select e0.id, e0.price, j1.name from Order e0 left join e0.customer j1 where e0.status = 1
```

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `insert-select`：

```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price,customer_name)
select o1_0.id,o1_0.price,c1_0.name
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where o1_0.status=1
```

lambdaQL 对源路径使用显式的 `left join`，因此没有客户的订单不会丢失（规则 U10）。三种数据库上的 SQL 相同。

`val` 属性的目标使用 `Dml.value`。第一个参数是目标 `a` 的属性。

```kotlin
qb.insert(Archive::class.java).from(Order::class.java).where { it.status == 9 }
    .select { a, o -> value(a.id, o.id); value(a.customerName, o.customer?.name) }
    .execute()
```

**子查询。** 源的 `where` 用第二个参数、`select` 用第三个参数接收子查询的起点 `sub`（决策 D49）。Java 测试（[`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)，region `insert-select-sub`）确认了这一点。下面是设计示例。

```kotlin
qb.insert(Archive::class.java).from(Order::class.java)
    .where { o, sub -> sub.from(Item::class.java).where { x -> x.order?.id == o.id }.exists() }
    .select { a, o, sub -> a.id = o.id; a.price = sub.from(Item::class.java).where { x -> x.order?.id == o.id }.sum { it.price } }
    .execute()
```

<a id="insert--select-with-aggregates"></a>
## 带聚合的 Insert ... select

使用 [6. 查询列、聚合与分组](06-select-group.md) 中的 `groupBy`、`having` 和 `Agg` 函数。
顺序是 `from(...)`、`where { }`、`groupBy { }`、`having { }`、`select { }`。`where`、`groupBy` 和 `having` 都是可选的。

```kotlin
// INSERT ... SELECT with groupBy: one summary row per customer
val i = qb.insert(Summary::class.java)
    .from(Order::class.java)
    .where { it.customer != null }
    .groupBy { it.customer?.id }
    .having { Agg.count() >= 2 }
    .select { s, o -> s.customerId = o.customer?.id; s.total = Agg.sum(o.price); s.cnt = Agg.count() }
// HQL: insert into Summary (customerId, total, cnt) select e0.customer.id, sum(e0.price), count(*) from Order e0
//      where e0.customer is not null group by e0.customer.id having count(*) >= 2L
```

同样的语句已在 [`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java) 中用 Java 测试（region `insert-group`）。

**各数据库的 SQL（来自 Java 测试运行；Kotlin 生成相同的 HQL）** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `insert-group`：

```sql
-- H2, MariaDB, Oracle (same)
insert into summary(customer_id,total,cnt)
select o1_0.customer_id,sum(o1_0.price),count(*)
from orders o1_0
where o1_0.customer_id is not null
group by o1_0.customer_id
having count(*)>=2
```

- 每个不带聚合的值都必须是 `groupBy` 的键（规则 J3）。否则 lambdaQL 会在分析时抛出 `TranslationException`（`LQL-3035`）。
- `where` 中不允许使用 `Agg`。请使用 `having`。

---

---

[7. 更新、插入、删除（Kotlin）](07-dml.md) | [7.2 更新](07-2-update.md) | [7.4 删除](07-4-delete.md) | **7.5 插入** | [7.6 Upsert](07-6-upsert.md) | [Java](../java/07-5-insert.md)
