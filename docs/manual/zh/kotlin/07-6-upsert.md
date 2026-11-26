<a id="76-upsert"></a>
# 7.6 Upsert（Kotlin）

[7. 更新、插入、删除（Kotlin）](07-dml.md) | [7.2 更新](07-2-update.md) | [7.4 删除](07-4-delete.md) | [7.5 插入](07-5-insert.md) | **7.6 Upsert** | [Java](../java/07-6-upsert.md)

---

Upsert 插入一行。如果已经存在键相同的行，就改为更新该行，或者让该行保持原样。
lambdaQL 提供三种形式。

| 形式 | 键 | 键已存在时 |
|---|---|---|
| `qb.upsert(X::class.java).values { ... }` | `@Id` | 把其余被赋值的列更新为新值 |
| `qb.insert(X::class.java).values { ... }.onConflict { key }.update { ... }` | 带有主键或唯一约束的列 | 在已有行上执行 `update { ... }` 中的赋值 |
| `qb.insert(X::class.java).values { ... }.onConflict { key }.ignore()` | 同上 | 不做修改：保留已有行 |

<a id="short-form-upsert"></a>
## 简短形式：`upsert`

<!-- region: ManualKotlinDmlTest.kt#upsert -->
```kotlin
// the key is the @Id; on conflict, the other assigned columns get the new values
val inserted = qb.upsert(Archive::class.java).values { it.id = 100L; it.price = 1.0 }.execute()
val again = qb.upsert(Archive::class.java).values { it.id = 100L; it.price = 2.0 }
assertEquals("insert into Archive e0 (id, price) values (100L, 2.0) on conflict(id) do update set price = excluded.price",
    again.toHql()[0].hql())
val updated = again.execute()                 // MariaDB and MySQL count an updated row as 2, the others as 1
```

**各数据库的 SQL**

<!-- sql: upsert -->
```sql
-- H2
merge into archive a1_0 using (values (100,2.0)) excluded(id,price) on (a1_0.id=excluded.id) when matched then update
set price=excluded.price when not matched then insert (id,price)
values (excluded.id,excluded.price)

-- MariaDB
insert into archive(id,price)
values (100,2.0) on duplicate key update price=values(price)

-- Oracle
merge into archive a1_0 using (select 100 id,2.0 price) excluded on (a1_0.id=excluded.id) when matched then update
set a1_0.price=excluded.price when not matched then insert (id,price)
values (excluded.id,excluded.price)
```

第二次执行后，该行的价格是新值 `2.0`。返回的行数因数据库而异（见[影响行数](#the-row-count)）。

各数据库的 SQL 不同。H2 和 Oracle 得到 `merge` 语句。H2 从 `values (...)` 读取新行，Oracle 从 `select 100 id,2.0 price` 读取新行。MariaDB 得到 `insert ... on duplicate key update`，并用 `values(price)` 读取新值。

这一行必须有 `@Id` 赋值。如果只给 `@Id` 赋值，就没有需要更新的列，lambdaQL 会写成下面的 `ignore()` 形式。

示例代码：[`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`upsert`）。

<a id="your-own-change-onconflictkeyupdate"></a>
## 自定义修改：`onConflict(key).update(...)`

当修改内容不是"替换为新值"时（例如计数器），使用这种形式。

<!-- region: ManualKotlinDmlTest.kt#on-conflict -->
```kotlin
// on conflict, change the existing row: a property read here reads the existing row
val add = qb.insert(Archive::class.java)
    .values { it.id = 100L; it.price = 5.0 }
    .onConflict { it.id }
    .update { it.price = it.price + 5.0 }
assertEquals("insert into Archive e0 (id, price) values (100L, 5.0) on conflict(id) do update set price = (e0.price + 5.0)",
    add.toHql()[0].hql())
add.execute()                                 // inserted: 5.0
add.execute()                                 // conflict: 5.0 + 5.0
```

**各数据库的 SQL**

<!-- sql: on-conflict -->
```sql
-- H2
merge into archive a1_0 using (values (100,5.0)) excluded(id,price) on (a1_0.id=excluded.id) when matched then update
set price=(a1_0.price+5.0) when not matched then insert (id,price)
values (excluded.id,excluded.price)

-- MariaDB
insert into archive(id,price)
values (100,5.0) on duplicate key update price=(archive.price+5.0)

-- Oracle
merge into archive a1_0 using (select 100 id,5.0 price) excluded on (a1_0.id=excluded.id) when matched then update
set a1_0.price=(a1_0.price+5.0) when not matched then insert (id,price)
values (excluded.id,excluded.price)
```

`update { ... }` 中读取属性时读取的是**已有**行。`it.price + 5.0` 是旧价格加 5。在 SQL 中，H2 和 Oracle 使用别名（`a1_0.price`），MariaDB 使用表名（`archive.price`）。

键用 Lambda 表达式（`{ it.id }`）指定。键必须是实体本身的列，而不是关联路径，并且数据库中该列必须有主键或唯一约束。

`values` 和 `update` 使用带两个参数的 Lambda 表达式时，可以用第二个参数 `sub` 构造子查询（在三种数据库上实测）。另请参阅 [7.5](07-5-insert.md#a-subquery-in-a-value)。

<!-- region: ManualKotlinDmlTest.kt#upsert-sub -->
```kotlin
// a subquery in the inserted value and in the update on conflict
val up = qb.insert(Archive::class.java)
    .values { a, sub -> a.id = 100L; a.price = sub.from(Item::class.java).sum { x -> x.price } }
    .onConflict { it.id }
    .update { a, sub -> a.price = sub.from(Order::class.java).max { o -> o.price } }
assertEquals("insert into Archive e0 (id, price) values (100L, (select sum(s1.price) from Item s1))"
    + " on conflict(id) do update set price = (select max(s1.price) from Order s1)", up.toHql()[0].hql())
```

这条语句执行两次时，第一次插入商品价格的合计，第二次因键冲突而更新为最大的订单价格。

示例代码：[`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`on-conflict`、`upsert-sub`）。

<a id="keep-the-existing-row-onconflictkeyignore"></a>
## 保留已有行：`onConflict(key).ignore()`

<!-- region: ManualKotlinDmlTest.kt#insert-ignore -->
```kotlin
val once = qb.insert(Archive::class.java)
    .values { it.id = 300L; it.price = 1.0 }
    .onConflict { it.id }
    .ignore()
assertEquals("insert into Archive (id, price) select 300L, 1.0 where not exists (select 1 from Archive x where x.id = 300L)",
    once.toHql()[0].hql())
val first = once.execute()                    // 1: inserted
val second = qb.insert(Archive::class.java).values { it.id = 300L; it.price = 2.0 }
    .onConflict { it.id }.ignore().execute()  // 0: the row exists, nothing changes
```

**各数据库的 SQL**

<!-- sql: insert-ignore -->
```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price)
select 300,1.0
where not exists(select 1 from archive a2_0 where a2_0.id=300)
```

Hibernate 自己的 `on conflict ... do nothing` 在 H2 和 Oracle 上会作为普通 insert 执行，并因键重复错误而失败。
因此 lambdaQL 自己写出 `insert ... select ... where not exists (...)`。三种数据库上的 SQL 相同。

> [!WARNING]
> **`ignore()` 不是原子的。** 两个事务同时插入相同的键时，可能都会通过 `not exists` 检查。
> 这时其中一方会发生键重复错误。如果应用程序中可能出现这种情况，请处理该错误。

示例代码：[`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`insert-ignore`）。

<a id="the-row-count"></a>
## 影响行数

`execute()` 原样返回数据库报告的行数。各数据库的值如下（实测）。

| 数据库 | 插入 | 更新 | 忽略 |
|---|---|---|---|
| H2 | 1 | 1 | 0 |
| MariaDB | 1 | **2** | 0 |
| Oracle | 1 | 1 | 0 |

因此在 MariaDB 上，`execute() == 1` 并不表示"已更新"。要确认是否写入了行，请使用 `execute() > 0`。

<a id="many-rows"></a>
## 多行

`qb.upsert(X::class.java).values(list) { a, l -> ... }` 以及 `values(list) { ... }` 之后的 `onConflict { ... }` 会**每行执行一条语句**。`execute()` 返回合计。
列表形式也像 `values(list) { a, l, sub -> ... }` 这样，用第三个参数接收子查询（见 [7.5](07-5-insert.md#many-rows-from-a-list)）。

<!-- region: ManualKotlinDmlTest.kt#upsert-list -->
```kotlin
// one statement per element; execute() returns the sum
val lines = listOf(Line(201L, 1.0), Line(202L, 2.0))
val first = qb.upsert(Archive::class.java).values(lines) { a, l -> a.id = l.id; a.price = l.price }.execute()
val changed = listOf(Line(202L, 20.0), Line(203L, 3.0))
val second = qb.upsert(Archive::class.java).values(changed) { a, l -> a.id = l.id; a.price = l.price }.execute()
```

第二次调用更新 `202` 并插入 `203`。合计在 H2 上是 2，在 MariaDB 和 MySQL 上是 3（更新的行计为 2）。

示例代码：[`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`upsert-list`）。

<a id="rules"></a>
## 规则

- 这一行必须有键赋值。`upsert(X::class.java)` 使用 `@Id` 作为键。
- 键必须是实体本身的列。关联路径是错误。
- `values { ... }` 中的值不能读取这一行（因为它是新行）。已有行只在 `update { ... }` 中读取。
- 适用 update 和 insert 的赋值规则（见 [7.9](07-dml.md#79-all-rules-u1-to-u21-summary)）。
- 没有基于 `insert ... select` 的 upsert。
- 与其他 DML 语句一样，upsert 需要事务，并且不会反映到已加载的实体上（见 [7.8](07-dml.md#transactions)）。

---

[7. 更新、插入、删除（Kotlin）](07-dml.md) | [7.2 更新](07-2-update.md) | [7.4 删除](07-4-delete.md) | [7.5 插入](07-5-insert.md) | **7.6 Upsert** | [Java](../java/07-6-upsert.md)
