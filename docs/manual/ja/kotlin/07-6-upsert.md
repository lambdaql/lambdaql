<a id="76-upsert"></a>
# 7.6 Upsert（Kotlin）

[7. 更新、挿入、削除（Kotlin）](07-dml.md) | [7.2 更新](07-2-update.md) | [7.4 削除](07-4-delete.md) | [7.5 挿入](07-5-insert.md) | **7.6 Upsert** | [Java](../java/07-6-upsert.md)

---

Upsert は行を挿入します。同じキーの行がすでにあれば、挿入の代わりにその行を更新するか、その行をそのまま残します。
lambdaQL は 3 つの形を提供します。

| 形 | キー | キーがすでにあるとき |
|---|---|---|
| `qb.upsert(X::class.java).values { ... }` | `@Id` | 代入した残りのカラムを新しい値で更新 |
| `qb.insert(X::class.java).values { ... }.onConflict { key }.update { ... }` | 主キーかユニーク制約のあるカラム | `update { ... }` の代入を既存の行に実行 |
| `qb.insert(X::class.java).values { ... }.onConflict { key }.ignore()` | 上と同じ | 変更なし: 既存の行を残す |

<a id="short-form-upsert"></a>
## 短い書き方: `upsert`

<!-- region: ManualKotlinDmlTest.kt#upsert -->
```kotlin
// the key is the @Id; on conflict, the other assigned columns get the new values
val inserted = qb.upsert(Archive::class.java).values { it.id = 100L; it.price = 1.0 }.execute()
val again = qb.upsert(Archive::class.java).values { it.id = 100L; it.price = 2.0 }
assertEquals("insert into Archive e0 (id, price) values (100L, 2.0) on conflict(id) do update set price = excluded.price",
    again.toHql()[0].hql())
val updated = again.execute()                 // MariaDB and MySQL count an updated row as 2, the others as 1
```

**データベースごとの SQL**

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

2 回目の実行の後、行の価格は新しい値 `2.0` です。返される行数はデータベースによって違います（[行数](#the-row-count) を参照）。

SQL はデータベースによって違います。H2 と Oracle は `merge` 文を受け取ります。H2 は `values (...)` から、Oracle は `select 100 id,2.0 price` から新しい行を読みます。MariaDB は `insert ... on duplicate key update` を受け取り、新しい値を `values(price)` で読みます。

行には `@Id` への代入がなければなりません。`@Id` だけを代入すると更新するカラムがないので、lambdaQL は下の `ignore()` の形で書きます。

コード例: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`upsert`）。

<a id="your-own-change-onconflictkeyupdate"></a>
## 自分で書く変更: `onConflict(key).update(...)`

変更の内容が「新しい値で置き換える」ではないとき（例: カウンター）に、この形を使います。

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

**データベースごとの SQL**

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

`update { ... }` の中のプロパティの読み取りは、**既存の** 行を読みます。`it.price + 5.0` は、前の価格に 5 を加えた値です。SQL では、H2 と Oracle は別名（`a1_0.price`）を、MariaDB はテーブル名（`archive.price`）を書きます。

キーはラムダ式（`{ it.id }`）で指定します。キーは関連のパスではなくエンティティ自身のカラムでなければならず、データベースにそのカラムの主キーかユニーク制約がなければなりません。

`values` と `update` に引数が 2 つのラムダ式を使うと、2 つ目の引数 `sub` でサブクエリを作れます（3 つの DB で実測）。[7.5](07-5-insert.md#a-subquery-in-a-value) も参照してください。

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

この文を 2 回実行すると、1 回目は品目の価格の合計を挿入し、2 回目はキーが重なるので、いちばん大きい注文の価格で更新します。

コード例: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`on-conflict`、`upsert-sub`）。

<a id="keep-the-existing-row-onconflictkeyignore"></a>
## 既存の行を残す: `onConflict(key).ignore()`

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

**データベースごとの SQL**

<!-- sql: insert-ignore -->
```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price)
select 300,1.0
where not exists(select 1 from archive a2_0 where a2_0.id=300)
```

Hibernate 自身の `on conflict ... do nothing` は、H2 と Oracle では普通の insert として実行され、キーの重複エラーで失敗します。
そのため lambdaQL は `insert ... select ... where not exists (...)` を直接書きます。SQL は 3 つのデータベースで同じです。

> [!WARNING]
> **`ignore()` はアトミックではありません。** 2 つのトランザクションが同じキーを同時に挿入すると、両方が `not exists` のチェックを通ることがあります。
> すると一方でキーの重複エラーが発生します。アプリケーションでこのような状況が起こりうるなら、このエラーを処理してください。

コード例: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`insert-ignore`）。

<a id="the-row-count"></a>
## 行数

`execute()` は、データベースが報告した行数をそのまま返します。データベースごとの値は次のとおりです（実測）。

| データベース | 挿入 | 更新 | 無視 |
|---|---|---|---|
| H2 | 1 | 1 | 0 |
| MariaDB | 1 | **2** | 0 |
| Oracle | 1 | 1 | 0 |

そのため、MariaDB では `execute() == 1` は「更新された」という意味ではありません。行が書き込まれたかを確認するには、`execute() > 0` を使ってください。

<a id="many-rows"></a>
## 複数行

`qb.upsert(X::class.java).values(list) { a, l -> ... }` と、`values(list) { ... }` の後の `onConflict { ... }` は、**行ごとに 1 つの文** を実行します。`execute()` は合計を返します。
リストの形も、`values(list) { a, l, sub -> ... }` のように 3 つ目の引数でサブクエリを受け取ります（[7.5](07-5-insert.md#many-rows-from-a-list) を参照）。

<!-- region: ManualKotlinDmlTest.kt#upsert-list -->
```kotlin
// one statement per element; execute() returns the sum
val lines = listOf(Line(201L, 1.0), Line(202L, 2.0))
val first = qb.upsert(Archive::class.java).values(lines) { a, l -> a.id = l.id; a.price = l.price }.execute()
val changed = listOf(Line(202L, 20.0), Line(203L, 3.0))
val second = qb.upsert(Archive::class.java).values(changed) { a, l -> a.id = l.id; a.price = l.price }.execute()
```

2 回目の呼び出しは `202` を更新し、`203` を挿入します。合計は H2 では 2、MariaDB と MySQL では 3 です（更新した行を 2 と数える）。

コード例: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`upsert-list`）。

<a id="rules"></a>
## ルール

- 行にはキーへの代入がなければなりません。`upsert(X::class.java)` は `@Id` をキーとして使います。
- キーはエンティティ自身のカラムでなければなりません。関連のパスはエラーです。
- `values { ... }` の値では行を読めません（新しい行だからです）。既存の行は `update { ... }` でだけ読みます。
- update と insert の代入のルールを適用します（[7.9](07-dml.md#79-all-rules-u1-to-u21-summary) を参照）。
- `insert ... select` で行う upsert はありません。
- 他の DML の文と同じく、upsert にはトランザクションが必要で、すでに読み込まれたエンティティには反映されません（[7.8](07-dml.md#transactions) を参照）。

---

[7. 更新、挿入、削除（Kotlin）](07-dml.md) | [7.2 更新](07-2-update.md) | [7.4 削除](07-4-delete.md) | [7.5 挿入](07-5-insert.md) | **7.6 Upsert** | [Java](../java/07-6-upsert.md)
