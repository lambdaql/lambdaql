<a id="75-insert"></a>
# 7.5 挿入（Kotlin）

[7. 更新、挿入、削除（Kotlin）](07-dml.md) | [7.2 更新](07-2-update.md) | [7.4 削除](07-4-delete.md) | **7.5 挿入** | [7.6 Upsert](07-6-upsert.md) | [Java](../java/07-5-insert.md)

---

<a id="one-row"></a>
## 1 行

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

**データベースごとの SQL**

<!-- sql: insert -->
```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price,customer_name)
values (100,9.5,'Kim')
```

ラムダ式に書いた定数はリテラルのまま残ります。値から新しい行を読むことはできません。`it.price = it.price` はエラーです。

`val` プロパティは、`Dml.value` で 1 行に書きます（[7.3](07-dml.md#73-two-ways-to-assign-property-or-dmlvalue) を参照）。

<!-- region: ManualKotlinDmlTest.kt#insert-value -->
```kotlin
// properties without a setter (val): Dml.value
val i = qb.insert(Archive::class.java).values { a -> value(a.id, id); value(a.price, 1.5) }
assertEquals("insert into Archive (id, price) values (:p1, 1.5)", i.toHql()[0].hql())
```

<a id="a-subquery-in-a-value"></a>
### 値の中のサブクエリ

`values { a, sub -> ... }` のように引数が 2 つのラムダ式を使うと、2 つ目の引数 `sub` でサブクエリを作れます。
upsert の `values { a, sub -> ... }` と `onConflict { ... }.update { a, sub -> ... }` も同じです（[7.6](07-6-upsert.md#your-own-change-onconflictkeyupdate) を参照）。

<!-- region: KotlinQueryTest.kt#insert-values-sub -->
```kotlin
// values { a, sub -> ... }: a subquery can read captured values, not the new row
val i = qb.insert(Archive::class.java)
    .values { a, sub -> a.id = 100L; a.price = sub.from(Item::class.java).sum { it.price } }
assertEquals("insert into Archive (id, price) values (100L, (select sum(s1.price) from Item s1))", i.toHql()[0].hql())
```

- 別のテーブルの集約（キャプチャしたパラメーターの有無にかかわらず）、複数行、対象のテーブル自身を読むサブクエリ（MariaDB のエラー 1093 なし）が、すべて動きます（H2、MariaDB 10.11、Oracle 23.26 で実測）。
- サブクエリはキャプチャした値を読めますが、新しく挿入する行は読めません。
- リストの形でもサブクエリを使えます（[リストから複数行](#many-rows-from-a-list) を参照）。
- upsert の挿入の値と `do update set` の中のサブクエリも動きます。Oracle では Hibernate が `merge` で実行します（実測）。例は [7.6](07-6-upsert.md#your-own-change-onconflictkeyupdate) にあります。

コード例: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`insert`、`insert-value`）、[`KotlinQueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)（`insert-values-sub`）。

<a id="many-rows-from-a-list"></a>
## リストから複数行

リストの要素ごとに 1 行を挿入します。2 つ目のラムダ式の引数が要素です。

<!-- region: ManualKotlinDmlTest.kt#insert-list -->
```kotlin
val lines = listOf(Line(201L, 1.0), Line(202L, 2.0))      // data class Line(val id: Long, val price: Double)
val i = qb.insert(Archive::class.java).values(lines) { a, l -> a.id = l.id; a.price = l.price }
assertEquals("insert into Archive (id, price) values (:p1, :p2), (:p3, :p4)", i.toHql()[0].hql())
```

**データベースごとの SQL**

<!-- sql: insert-list -->
```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price)
values (?,?), (?,?)
```

lambdaQL は要素のプロパティを Java で読むので、すべての値がバインドパラメーターになります。
`Line` は普通の data class です。VALUES の行が複数ある 1 つの文を書きます。リストが長ければ、1 つの文の値が 500 個以下になるように分けます（[7.8](07-dml.md#batching-for-many-rows) を参照）。空のリストは SQL を実行せず、0 を返します。

**リストの形のサブクエリ。** `values(list) { a, l, sub -> ... }` のように、3 つ目の引数でサブクエリを作れます。
サブクエリは要素の値を読めます。その値はクエリの前に読み、行ごとにパラメーター 1 つとしてバインドします。新しく挿入する行は読めません（3 つの DB で実測）。

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

`Ref` は `data class Ref(val id: Long, val orderId: Long?)` です。

コード例: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`insert-list`、`insert-list-sub`）。

<a id="generated-id"></a>
## 生成される id

id が IDENTITY や SEQUENCE を使う場合は、id を省略できます。
1 つ目の例の `IdOrder` の行がこの場合にあたります。`IdOrder` の id は生成される値です。

<a id="insert--select"></a>
## insert ... select

別のエンティティから行をコピーします。`select` のラムダ式は、対象（`a`）とソース（`o`）を受け取ります。

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

lambdaQL はソースのパスに明示的な `left join` を使うので、顧客のない注文も抜けません。SQL は 3 つのデータベースで同じです。

`val` プロパティの対象には `Dml.value` を使います。1 つ目の引数は対象 `a` のプロパティです。

<!-- region: ManualKotlinDmlTest.kt#insert-select-value -->
```kotlin
// Dml.value in insert ... select: the first argument is a property of the target a
val i = qb.insert(Archive::class.java).from(Order::class.java).where { it.status.toInt() == 3 }
    .select { a, o -> value(a.id, o.id); value(a.customerName, o.customer?.name) }
assertEquals("insert into Archive (id, customerName) select e0.id, j1.name from Order e0 left join e0.customer j1"
    + " where e0.status = 3", i.toHql()[0].hql())
```

**サブクエリ。** ソースの `where` は 2 つ目の引数で、`select` は 3 つ目の引数で、サブクエリの起点 `sub` を受け取ります。

<!-- region: ManualKotlinDmlTest.kt#insert-select-sub -->
```kotlin
// where { o, sub -> ... } on the source, and select { a, o, sub -> ... }: the third parameter starts a subquery
val i = qb.insert(Archive::class.java).from(Order::class.java)
    .where { o, sub -> sub.from(Item::class.java).where { x -> x.order?.id == o.id }.exists() }
    .select { a, o, sub -> a.id = o.id; a.price = sub.from(Item::class.java).where { x -> x.order?.id == o.id }.sum { x -> x.price } }
assertEquals("insert into Archive (id, price) select e0.id, (select sum(s2.price) from Item s2 where s2.order.id = e0.id)"
    + " from Order e0 where exists (select 1 from Item s1 where s1.order.id = e0.id)", i.toHql()[0].hql())
```

コード例: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`insert-select`、`insert-select-value`、`insert-select-sub`）。

<a id="insert--select-with-aggregates"></a>
## 集約を使う insert ... select

[6. 選択、集計、グループ化](06-select-group.md) の `groupBy`、`having`、`Agg` の関数を使います。
呼び出しの順は `from(...)`、`where { }`、`groupBy { }`、`having { }`、`select { }` です。`where`、`groupBy`、`having` は省略できます。

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
- `where` には `Agg` を使えません。`having` を使ってください。

---

[7. 更新、挿入、削除（Kotlin）](07-dml.md) | [7.2 更新](07-2-update.md) | [7.4 削除](07-4-delete.md) | **7.5 挿入** | [7.6 Upsert](07-6-upsert.md) | [Java](../java/07-5-insert.md)

コード例: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`insert-group`）。
