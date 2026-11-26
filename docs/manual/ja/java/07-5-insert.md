<a id="75-insert"></a>
# 7.5 挿入（insert）（Java）

[7. 更新、挿入、削除（Java）](07-dml.md) | [7.2 更新（update）](07-2-update.md) | [7.4 削除（delete）](07-4-delete.md) | **7.5 挿入（insert）** | [7.6 Upsert](07-6-upsert.md) | [Kotlin](../kotlin/07-5-insert.md)

---

<a id="one-row"></a>
## 1 行

<!-- region: DmlQueryTest.java#insert -->
```java
ExecutableInsert<Archive> i = qb.insert(Archive.class)
        .values(a -> { a.setId(100L); a.setPrice(9.5); a.setCustomerName("Kim"); });
assertEquals("insert into Archive (id, price, customerName) values (100L, 9.5, 'Kim')", i.toHql().get(0).hql());
int inserted = i.execute();
// a generated id (IDENTITY) can be left out
int generated = qb.insert(IdOrder.class).values(o -> { o.setPrice(1.0); o.setStatus(1); }).execute();
```

**データベースごとの SQL**

<!-- sql: insert -->
```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price,customer_name)
values (100,9.5,'Kim')
```

ラムダ式に書いた定数はリテラルのまま残ります。値から新しい行を読むことはできません。`a.setPrice(a.getPrice())` はエラーです。

setter のないエンティティは `Dml.value` で 1 行を挿入します（[7.3](07-dml.md#73-two-ways-to-assign-setter-or-dmlvalue) を参照）。例の `Archive` には setter がありますが、同じ形を setter のないエンティティにも使います。

<!-- region: ManualJavaDmlTest.java#insert-value -->
```java
ExecutableInsert<Archive> i = qb.insert(Archive.class)
        .values(a -> { value(a.getId(), id); value(a.getPrice(), 1.0); });
assertEquals("insert into Archive (id, price) values (:p1, 1.0)", i.toHql().get(0).hql());
int inserted = i.execute();
```

<a id="a-subquery-in-a-value"></a>
### 値の中のサブクエリ

`values((a, sub) -> ...)` のように引数が 2 つのラムダ式を使うと、2 番目の引数 `sub` でサブクエリを作れます。
upsert の `values((a, sub) -> ...)` と `onConflict(...).update((a, sub) -> ...)` も同じです（[7.6](07-6-upsert.md#your-own-change-onconflictkeyupdate) を参照）。

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

- 別のテーブルの集約（キャプチャパラメーターの有無を問わず）、複数行、対象テーブル自身を読むサブクエリ（MariaDB のエラー 1093 なし）がすべて動きます（H2、MariaDB 10.11、Oracle 23.26 で実測）。
- サブクエリはキャプチャ値を読めますが、新しく挿入する行は読めません。
- リストの形でもサブクエリを使えます（[リストから複数行](#many-rows-from-a-list) を参照）。
- upsert の挿入値と `do update set` の中のサブクエリも動きます。Oracle では Hibernate が `merge` で実行します（実測）。

コード例: [`DmlQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)（`insert`、`insert-values-sub`）、[`ManualJavaDmlTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaDmlTest.java)（`insert-value`）。

<a id="many-rows-from-a-list"></a>
## リストから複数行

リストの要素ごとに 1 行を挿入します。ラムダ式の 2 番目の引数が要素です。

<!-- region: DmlQueryTest.java#insert-list -->
```java
List<Line> lines = List.of(new Line(201, 1.0), new Line(202, 2.0));
ExecutableInsert<Archive> i = qb.insert(Archive.class).values(lines, (a, l) -> { a.setId(l.id()); a.setPrice(l.price()); });
assertEquals("insert into Archive (id, price) values (:p1, :p2), (:p3, :p4)", i.toHql().get(0).hql());
```

**データベースごとの SQL**

<!-- sql: insert-list -->
```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price)
values (?,?), (?,?)
```

lambdaQL は要素の getter を Java で読むので、すべての値がバインドパラメーターになります。
VALUES 行を複数持つ 1 つの文を書きます。リストが長い場合は、1 つの文の値が 500 個以下になるように分割します（[7.8](07-dml.md#batching-for-many-rows) を参照）。空のリストでは SQL を実行せず、0 を返します。

**リストの形のサブクエリ。** `values(list, (a, l, sub) -> ...)` のように、3 番目の引数でサブクエリを作れます。`qb.upsert(X.class).values(list, (a, l, sub) -> ...)` も同じです。
サブクエリは要素の値を読めます。その値は Java で読み、行ごとに 1 つのパラメーターとしてバインドします。新しく挿入する行は読めません（3 つの DB で実測）。

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

コード例: [`DmlQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)（`insert-list`、`insert-list-sub`）。

<a id="generated-id"></a>
## 生成される id

id が IDENTITY か SEQUENCE を使う場合、id を省略できます。
最初の例の `IdOrder` の行がこれにあたります。`IdOrder` の id は IDENTITY 方式です。

<a id="insert--select"></a>
## insert ... select

別のエンティティから行をコピーします。`select` のラムダ式は対象（`a`）とソース（`o`）を受け取ります。

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

**データベースごとの SQL**

<!-- sql: insert-select -->
```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price,customer_name)
select o1_0.id,o1_0.price,c1_0.name
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where o1_0.status=1
```

lambdaQL はソースのパスに明示的な `left join` を使うので、顧客のない注文も抜け落ちません。SQL は 3 つのデータベースで同じです。

setter のない対象エンティティには `Dml.value` を使います。1 番目の引数は対象 `a` の getter 呼び出しです。

<!-- region: ManualJavaDmlTest.java#insert-select-value -->
```java
ExecutableInsert<Archive> i = qb.insert(Archive.class).from(Order.class).where(o -> o.getStatus() == 1)
        .select((a, o) -> { value(a.getId(), o.getId()); value(a.getCustomerName(), o.getCustomer().getName()); });
assertEquals("insert into Archive (id, customerName) select e0.id, j1.name"
        + " from Order e0 left join e0.customer j1 where e0.status = 1", i.toHql().get(0).hql());
int inserted = i.execute();
```

**サブクエリ。** ソースの `where` は 2 番目の引数で、`select` は 3 番目の引数で、サブクエリの起点 `sub` を受け取ります。

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

コード例: [`DmlQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)（`insert-select`、`insert-select-sub`）、[`ManualJavaDmlTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaDmlTest.java)（`insert-select-value`）。

<a id="insert--select-with-aggregates"></a>
## 集約を使う insert ... select

[6. 選択、集計、グループ化](06-select-group.md) の `groupBy`、`having`、`Agg` 関数を使います。
呼び出しの順序は `from(...)`、`where(...)`、`groupBy(...)`、`having(...)`、`select(...)` です。`where`、`groupBy`、`having` は省略できます。

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

**データベースごとの SQL**

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

- 集約でない値はすべて `groupBy` のキーでなければなりません。そうでなければ、lambdaQL は解析時に `TranslationException`（`LQL-3035`）を投げます。
- `where` では `Agg` を使えません。`having` を使ってください。
- 挿入された `Summary` の行は、Java で同じ基準でグループ化した値と一致します。

---

[7. 更新、挿入、削除（Java）](07-dml.md) | [7.2 更新（update）](07-2-update.md) | [7.4 削除（delete）](07-4-delete.md) | **7.5 挿入（insert）** | [7.6 Upsert](07-6-upsert.md) | [Kotlin](../kotlin/07-5-insert.md)

コード例: [`ProjectionTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java)（`insert-group`）。
