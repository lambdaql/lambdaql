<a id="76-upsert"></a>
# 7.6 Upsert（Java）

[7. 更新、插入、删除（Java）](07-dml.md) | [7.2 更新](07-2-update.md) | [7.4 删除](07-4-delete.md) | [7.5 插入](07-5-insert.md) | **7.6 Upsert** | [Kotlin](../kotlin/07-6-upsert.md)

---


Upsert 会插入一行。如果已经存在键相同的行，它会改为修改该行，或者让该行保持原样。
lambdaQL 有三种写法：

| 写法 | 键 | 键已存在时 |
|---|---|---|
| `qb.upsert(X.class).values(...)` | `@Id` | 其他被赋值的列取新值 |
| `qb.insert(X.class).values(...).onConflict(key).update(...)` | 带有主键或唯一约束的列 | 在已有行上执行 `update(...)` 中的赋值 |
| `qb.insert(X.class).values(...).onConflict(key).ignore()` | 同上 | 什么也不做：已有行保持不变 |

<a id="short-form-upsert"></a>
## 简短写法：`upsert`

[`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)，region `upsert`：

```java
// the key is the @Id; on conflict, the other assigned columns get the new values
int inserted = qb.upsert(Archive.class).values(a -> { a.setId(100L); a.setPrice(1.0); }).execute();
ExecutableInsert<Archive> again = qb.upsert(Archive.class).values(a -> { a.setId(100L); a.setPrice(2.0); });
assertEquals("insert into Archive e0 (id, price) values (100L, 2.0) on conflict(id) do update set price = excluded.price",
        again.toHql().get(0).hql());
int updated = again.execute();                     // MariaDB and MySQL count an updated row as 2, the others as 1
```

**关于 SQL 块。** `…` 是缩短的 select 列表（实体的所有列），`?` 是绑定参数。这些行来自 [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java)。该测试使用 Hibernate 7.4 在 H2 2.3、MariaDB 10.11 和 Oracle 23.26 上运行每个查询，如果 Hibernate 的 SQL 发生变化，测试就会失败。

**各数据库的 SQL** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `upsert`：

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

各数据库的 SQL 不同。H2 和 Oracle 得到 `merge` 语句：H2 从 `values (...)` 读取新行，Oracle 从 `select 100 id,2.0 price` 读取。MariaDB 得到 `insert ... on duplicate key update`，并用 `values(price)` 读取新值。

这一行必须给 `@Id` 赋值。如果只给 `@Id` 赋值，就没有需要修改的内容，lambdaQL 会写出下面的 `ignore()` 形式。

<a id="your-own-change-onconflictkeyupdate"></a>
## 自定义修改：`onConflict(key).update(...)`

当修改不是“取新值”时使用这种写法，例如计数器。

[`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)，region `on-conflict`：

```java
// on conflict, change the existing row: a getter here reads the existing row
ExecutableInsert<Archive> add = qb.insert(Archive.class)
        .values(a -> { a.setId(100L); a.setPrice(5.0); })
        .onConflict(a -> a.getId())
        .update(a -> a.setPrice(a.getPrice() + 5.0));
assertEquals("insert into Archive e0 (id, price) values (100L, 5.0) on conflict(id) do update set price = (e0.price + 5.0)",
        add.toHql().get(0).hql());
add.execute();                                     // inserted: 5.0
add.execute();                                     // conflict: 5.0 + 5.0
```

**各数据库的 SQL** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `on-conflict`：

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

在 `update(...)` 中，getter 读取的是**已有**行：`a.getPrice() + 5.0` 是旧价格加 5。在 SQL 中，H2 和 Oracle 写出别名（`a1_0.price`），MariaDB 写出表名（`archive.price`）。

键是 getter Lambda（`a -> a.getId()`）或 getter 方法引用（`Archive::getId`）。它必须是实体本身的列，不能是关联路径，并且数据库中该列需要有主键或唯一约束。

`values` 和 `update` 使用带两个参数的 Lambda 时，可以用第二个参数 `sub` 构造子查询（决策 D49，在三种数据库上实测）。示例见 [7.5](07-5-insert.md#a-subquery-in-a-value)。

<a id="keep-the-existing-row-onconflictkeyignore"></a>
## 保留已有行：`onConflict(key).ignore()`

[`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)，region `insert-ignore`：

```java
ExecutableInsert<Archive> once = qb.insert(Archive.class)
        .values(a -> { a.setId(300L); a.setPrice(1.0); })
        .onConflict(a -> a.getId())
        .ignore();
assertEquals("insert into Archive (id, price) select 300L, 1.0 where not exists (select 1 from Archive x where x.id = 300L)",
        once.toHql().get(0).hql());
int first = once.execute();                        // 1: inserted
int second = qb.insert(Archive.class).values(a -> { a.setId(300L); a.setPrice(2.0); })
        .onConflict(a -> a.getId()).ignore().execute();   // 0: the row exists, nothing changes
```

**各数据库的 SQL** — 来自 `expected-sql/` 的 [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)，示例 `insert-ignore`：

```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price)
select 300,1.0
where not exists(select 1 from archive a2_0 where a2_0.id=300)
```

Hibernate 自己的 `on conflict ... do nothing` 在 H2 和 Oracle 上会作为普通 insert 执行，并在那里因键重复错误而失败。
因此 lambdaQL 自己写出 `insert ... select ... where not exists (...)`。三种数据库上的 SQL 相同。

> [!WARNING]
> **`ignore()` 不是原子的。** 两个事务在同一时刻插入相同的键时，可能都会通过 `not exists` 检查。
> 这时其中一个会得到键重复错误。如果你的应用程序中可能发生这种情况，请捕获该错误。

<a id="the-row-count"></a>
## 行数

`execute()` 原样返回数据库报告的行数（决策 D40）。测试会检查以下数字（实测）。

| 数据库 | 插入 | 更新 | 忽略 |
|---|---|---|---|
| H2 | 1 | 1 | 0 |
| MariaDB | 1 | **2** | 0 |
| Oracle | 1 | 1 | 0 |

因此在 MariaDB 上，`execute() == 1` 并不表示“已更新”。要检查是否写入了一行，请使用 `execute() > 0`。

<a id="many-rows"></a>
## 多行

`qb.upsert(X.class).values(list, (a, l) -> ...)` 以及在 `values(list, ...)` 之后的 `onConflict(...)` 会**每行执行一条语句**。`execute()` 返回总和。这种写法还没有测试（未测）。
列表形式也像 `values(list, (a, l, sub) -> ...)` 这样，用第三个参数接收子查询（见 [7.5](07-5-insert.md#many-rows-from-a-list)）。

```java
int n = qb.upsert(Archive.class)
    .values(lines, (a, l) -> { a.setId(l.id()); a.setPrice(l.price()); })
    .execute();
```

<a id="rules"></a>
## 规则

- 这一行必须给键赋值。`upsert(X.class)` 使用 `@Id` 作为键。
- 键必须是实体本身的列。关联路径是错误。
- `values(...)` 中的值不能读取这一行（它是新行）。只有 `update(...)` 读取已有行。
- 更新和插入的赋值规则同样适用（U1、U3、U4、U6；见 [7.9](07-dml.md#79-all-rules-u1-to-u21-summary)）。
- 没有基于 `insert ... select` 的 upsert。
- 与其他 DML 语句一样，upsert 需要事务，也不会修改已加载的实体（见 [7.8](07-dml.md#transactions)）。

---

---

[7. 更新、插入、删除（Java）](07-dml.md) | [7.2 更新](07-2-update.md) | [7.4 删除](07-4-delete.md) | [7.5 插入](07-5-insert.md) | **7.6 Upsert** | [Kotlin](../kotlin/07-6-upsert.md)
