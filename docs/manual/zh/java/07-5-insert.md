<a id="75-insert"></a>
# 7.5 插入（insert）（Java）

[7. 更新、插入、删除（Java）](07-dml.md) | [7.2 更新（update）](07-2-update.md) | [7.4 删除（delete）](07-4-delete.md) | **7.5 插入（insert）** | [7.6 Upsert（upsert）](07-6-upsert.md) | [Kotlin](../kotlin/07-5-insert.md)

---

<a id="one-row"></a>
## 一行

<!-- region: DmlQueryTest.java#insert -->
```java
ExecutableInsert<Archive> i = qb.insert(Archive.class)
        .values(a -> { a.setId(100L); a.setPrice(9.5); a.setCustomerName("Kim"); });
assertEquals("insert into Archive (id, price, customerName) values (100L, 9.5, 'Kim')", i.toHql().get(0).hql());
int inserted = i.execute();
// a generated id (IDENTITY) can be left out
int generated = qb.insert(IdOrder.class).values(o -> { o.setPrice(1.0); o.setStatus(1); }).execute();
```

**各数据库的 SQL**

<!-- sql: insert -->
```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price,customer_name)
values (100,9.5,'Kim')
```

Lambda 表达式中写的常量保持为字面量。值中不能读取新行。`a.setPrice(a.getPrice())` 是错误。

没有 setter 的实体用 `Dml.value` 插入一行（见 [7.3](07-dml.md#73-two-ways-to-assign-setter-or-dmlvalue)）。示例中的 `Archive` 有 setter，但没有 setter 的实体也使用同样的形式。

<!-- region: ManualJavaDmlTest.java#insert-value -->
```java
ExecutableInsert<Archive> i = qb.insert(Archive.class)
        .values(a -> { value(a.getId(), id); value(a.getPrice(), 1.0); });
assertEquals("insert into Archive (id, price) values (:p1, 1.0)", i.toHql().get(0).hql());
int inserted = i.execute();
```

<a id="a-subquery-in-a-value"></a>
### 值中的子查询

使用 `values((a, sub) -> ...)` 这样带两个参数的 Lambda 表达式，可以用第二个参数 `sub` 构造子查询。
upsert 的 `values((a, sub) -> ...)` 和 `onConflict(...).update((a, sub) -> ...)` 也一样（见 [7.6](07-6-upsert.md#your-own-change-onconflictkeyupdate)）。

<!-- region: DmlQueryTest.java#insert-values-sub -->
```java
// values((a, sub) -> ...): a subquery can read captured values, not the new row
ExecutableInsert<Archive> i = qb.insert(Archive.class)
        .values((a, sub) -> {
            a.setId(100L);
            a.setPrice(sub.from(Item.class).where(x -> x.getOrder().getId() == first).sum(x -> x.getPrice()));
        });
assertEquals("insert into Archive (id, price) values (100L, (select sum(s1.price) from Item s1 where s1.order.id = :p1))",
        i.toHql().get(0).hql());
// upsert: a subquery in the inserted value and in the update on conflict
ExecutableInsert<Archive> up = qb.insert(Archive.class)
        .values((a, sub) -> { a.setId(100L); a.setPrice(sub.from(Item.class).sum(x -> x.getPrice())); })
        .onConflict(a -> a.getId())
        .update((a, sub) -> a.setPrice(sub.from(Order.class).max(o -> o.getPrice())));
assertEquals("insert into Archive e0 (id, price) values (100L, (select sum(s1.price) from Item s1))"
        + " on conflict(id) do update set price = (select max(s1.price) from Order s1)", up.toHql().get(0).hql());
```

- 其他表的聚合（无论有无捕获参数）、多行、读取目标表自身的子查询（没有 MariaDB 错误 1093）都能运行（已在 H2、MariaDB 10.11、Oracle 23.26 上实测）。
- 子查询可以读取捕获值，但不能读取新插入的行。
- 列表形式中也可以使用子查询（见[从列表插入多行](#many-rows-from-a-list)）。
- upsert 的插入值和 `do update set` 中的子查询也能运行。在 Oracle 上 Hibernate 以 `merge` 执行（实测）。

示例代码：[`DmlQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)（`insert`、`insert-values-sub`）、[`ManualJavaDmlTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaDmlTest.java)（`insert-value`）。

<a id="many-rows-from-a-list"></a>
## 从列表插入多行

列表的每个元素插入一行。第二个 Lambda 表达式参数是元素。

<!-- region: DmlQueryTest.java#insert-list -->
```java
List<Line> lines = List.of(new Line(201, 1.0), new Line(202, 2.0));
ExecutableInsert<Archive> i = qb.insert(Archive.class).values(lines, (a, l) -> { a.setId(l.id()); a.setPrice(l.price()); });
assertEquals("insert into Archive (id, price) values (:p1, :p2), (:p3, :p4)", i.toHql().get(0).hql());
```

**各数据库的 SQL**

<!-- sql: insert-list -->
```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price)
values (?,?), (?,?)
```

lambdaQL 在 Java 中读取元素的 getter，因此所有值都成为绑定参数。
生成一条带多个 VALUES 行的语句。列表较长时，按每条语句不超过 500 个值进行拆分（见 [7.8](07-dml.md#batching-for-many-rows)）。空列表不执行 SQL，返回 0。

**列表形式的子查询。** 像 `values(list, (a, l, sub) -> ...)` 这样，可以用第三个参数构造子查询。`qb.upsert(X.class).values(list, (a, l, sub) -> ...)` 也一样。
子查询可以读取元素的值。这些值在 Java 中读取，每行绑定为一个参数。新插入的行不能读取（已在三种数据库上实测）。

<!-- region: DmlQueryTest.java#insert-list-sub -->
```java
// one row per element; the subquery reads the element's value
ExecutableInsert<Archive> i = qb.insert(Archive.class).values(refs, (a, r, sub) -> {
    a.setId(r.id());
    a.setPrice(sub.from(Item.class).where(x -> x.getOrder().getId() == r.orderId()).sum(x -> x.getPrice()));
});
assertEquals("insert into Archive (id, price) values (:p1, (select sum(s1.price) from Item s1 where s1.order.id = :p2)),"
        + " (:p3, (select sum(s1.price) from Item s1 where s1.order.id = :p4))", i.toHql().get(0).hql());
```

示例代码：[`DmlQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)（`insert-list`、`insert-list-sub`）。

<a id="generated-id"></a>
## 自动生成的 id

id 使用 IDENTITY 或 SEQUENCE 时，可以省略 id。
第一个示例中的 `IdOrder` 行就属于这种情况。`IdOrder` 的 id 采用 IDENTITY 方式。

<a id="insert--select"></a>
## insert ... select

从另一个实体复制行。`select` Lambda 表达式接收目标（`a`）和源（`o`）。

<!-- region: DmlQueryTest.java#insert-select -->
```java
ExecutableInsert<Archive> i = qb.insert(Archive.class)
        .from(Order.class)
        .where(o -> o.getStatus() == 1)
        .select((a, o) -> { a.setId(o.getId()); a.setPrice(o.getPrice()); a.setCustomerName(o.getCustomer().getName()); });
// a path in the select list: left join, so orders without a customer stay
assertEquals("insert into Archive (id, price, customerName) select e0.id, e0.price, j1.name"
        + " from Order e0 left join e0.customer j1 where e0.status = 1", i.toHql().get(0).hql());
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

lambdaQL 对源路径使用显式的 `left join`，因此没有客户的订单也不会遗漏。SQL 在三种数据库上相同。

没有 setter 的目标实体使用 `Dml.value`。第一个参数是对目标 `a` 的 getter 调用。

<!-- region: ManualJavaDmlTest.java#insert-select-value -->
```java
ExecutableInsert<Archive> i = qb.insert(Archive.class).from(Order.class).where(o -> o.getStatus() == 1)
        .select((a, o) -> { value(a.getId(), o.getId()); value(a.getCustomerName(), o.getCustomer().getName()); });
assertEquals("insert into Archive (id, customerName) select e0.id, j1.name"
        + " from Order e0 left join e0.customer j1 where e0.status = 1", i.toHql().get(0).hql());
int inserted = i.execute();
```

**子查询。** 源的 `where` 通过第二个参数、`select` 通过第三个参数接收子查询的起点 `sub`。

<!-- region: DmlQueryTest.java#insert-select-sub -->
```java
// where((o, sub) -> ...) on the source, and select((a, o, sub) -> ...): the third parameter starts a subquery
ExecutableInsert<Archive> i = qb.insert(Archive.class).from(Order.class)
        .where((o, sub) -> sub.from(Item.class).where(x -> x.getOrder().getId() == o.getId()).exists())
        .select((a, o, sub) -> {
            a.setId(o.getId());
            a.setPrice(sub.from(Item.class).where(x -> x.getOrder().getId() == o.getId()).sum(x -> x.getPrice()));
        });
assertEquals("insert into Archive (id, price) select e0.id, (select sum(s2.price) from Item s2 where s2.order.id = e0.id)"
        + " from Order e0 where exists (select 1 from Item s1 where s1.order.id = e0.id)", i.toHql().get(0).hql());
```

示例代码：[`DmlQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)（`insert-select`、`insert-select-sub`）、[`ManualJavaDmlTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaDmlTest.java)（`insert-select-value`）。

<a id="insert--select-with-aggregates"></a>
## 带聚合的 insert ... select

使用 [6. 查询列、聚合与分组](06-select-group.md) 中的 `groupBy`、`having` 和 `Agg` 函数。
调用顺序为 `from(...)`、`where(...)`、`groupBy(...)`、`having(...)`、`select(...)`。`where`、`groupBy`、`having` 可以省略。

<!-- region: ProjectionTest.java#insert-group -->
```java
// INSERT ... SELECT with groupBy: one summary row per customer
ExecutableInsert<Summary> i = qb.insert(Summary.class)
        .from(Order.class)
        .where(o -> o.getCustomer() != null)
        .groupBy(o -> o.getCustomer().getId())
        .having(o -> Agg.count() >= 2)
        .select((s, o) -> { s.setCustomerId(o.getCustomer().getId()); s.setTotal(Agg.sum(o.getPrice())); s.setCnt(Agg.count()); });
assertEquals("insert into Summary (customerId, total, cnt) select e0.customer.id, sum(e0.price), count(*) from Order e0"
        + " where e0.customer is not null group by e0.customer.id having count(*) >= 2L", i.toHql().get(0).hql());
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

- 所有非聚合值都必须是 `groupBy` 键。否则 lambdaQL 在分析时抛出 `TranslationException`（`LQL-3035`）。
- `where` 中不能使用 `Agg`。请使用 `having`。
- 插入的 `Summary` 行与在 Java 中按同一标准分组得到的值一致。

---

[7. 更新、插入、删除（Java）](07-dml.md) | [7.2 更新（update）](07-2-update.md) | [7.4 删除（delete）](07-4-delete.md) | **7.5 插入（insert）** | [7.6 Upsert（upsert）](07-6-upsert.md) | [Kotlin](../kotlin/07-5-insert.md)

示例代码：[`ProjectionTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java)（`insert-group`）。
