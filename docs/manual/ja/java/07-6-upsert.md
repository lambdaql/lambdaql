<a id="76-upsert"></a>
# 7.6 Upsert（Java）

[7. 更新、挿入、削除（Java）](07-dml.md) | [7.2 更新](07-2-update.md) | [7.4 削除](07-4-delete.md) | [7.5 挿入](07-5-insert.md) | **7.6 Upsert** | [Kotlin](../kotlin/07-6-upsert.md)

---

upsert は行を挿入します。同じキーの行がすでにある場合は、挿入の代わりにその行を更新するか、その行をそのまま残します。
lambdaQL は 3 つの形を提供します。

| 形 | キー | キーがすでにあるとき |
|---|---|---|
| `qb.upsert(X.class).values(...)` | `@Id` | 代入した残りのカラムを新しい値に更新 |
| `qb.insert(X.class).values(...).onConflict(key).update(...)` | 主キーまたは一意制約のあるカラム | `update(...)` の代入を既存の行に実行 |
| `qb.insert(X.class).values(...).onConflict(key).ignore()` | 同上 | 変更なし: 既存の行を維持 |

<a id="short-form-upsert"></a>
## 短縮形: `upsert`

<!-- region: DmlQueryTest.java#upsert -->
```java
// the key is the @Id; on conflict, the other assigned columns get the new values
int inserted = qb.upsert(Archive.class).values(a -> { a.setId(100L); a.setPrice(1.0); }).execute();
ExecutableInsert<Archive> again = qb.upsert(Archive.class).values(a -> { a.setId(100L); a.setPrice(2.0); });
assertEquals("insert into Archive e0 (id, price) values (100L, 2.0) on conflict(id) do update set price = excluded.price",
        again.toHql().get(0).hql());
int updated = again.execute();                     // MariaDB and MySQL count an updated row as 2, the others as 1
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

SQL はデータベースごとに違います。H2 と Oracle は `merge` 文になります。H2 は新しい行を `values (...)` から、Oracle は `select 100 id,2.0 price` から読みます。MariaDB は `insert ... on duplicate key update` になり、新しい値を `values(price)` で読みます。

行には `@Id` の代入が必要です。`@Id` だけを代入すると更新するカラムがないので、lambdaQL は下の `ignore()` の形で書きます。

コード例: [`DmlQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)（`upsert`）。

<a id="your-own-change-onconflictkeyupdate"></a>
## 直接指定する変更: `onConflict(key).update(...)`

変更の内容が「新しい値への置き換え」でない場合（例: カウンター）、この形を使います。

<!-- region: DmlQueryTest.java#on-conflict -->
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

`update(...)` の中の getter は **既存の** 行を読みます。`a.getPrice() + 5.0` は以前の価格に 5 を足した値です。SQL では、H2 と Oracle はエイリアス（`a1_0.price`）を、MariaDB はテーブル名（`archive.price`）を書きます。

キーは getter のラムダ式（`a -> a.getId()`）または getter のメソッド参照（`Archive::getId`）で指定します。キーは関連のパスではなくエンティティ自身のカラムでなければならず、データベースにそのカラムの主キーか一意制約が必要です。

`values` と `update` に引数が 2 つのラムダ式を書くと、2 番目の引数 `sub` でサブクエリを作れます（3 つの DB で実測）。例は [7.5](07-5-insert.md#a-subquery-in-a-value) を参照してください。

コード例: [`DmlQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)（`on-conflict`）。

<a id="keep-the-existing-row-onconflictkeyignore"></a>
## 既存の行の維持: `onConflict(key).ignore()`

<!-- region: DmlQueryTest.java#insert-ignore -->
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

**データベースごとの SQL**

<!-- sql: insert-ignore -->
```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price)
select 300,1.0
where not exists(select 1 from archive a2_0 where a2_0.id=300)
```

Hibernate 自身の `on conflict ... do nothing` は、H2 と Oracle では普通の insert として実行され、キー重複のエラーで失敗します。
そのため lambdaQL は `insert ... select ... where not exists (...)` を直接書きます。SQL は 3 つのデータベースで同じです。

> [!WARNING]
> **`ignore()` はアトミックではありません。** 2 つのトランザクションが同じキーを同時に挿入すると、両方が `not exists` の検査を通過することがあります。
> その場合、一方でキー重複のエラーが発生します。アプリケーションでこの状況が起こりうるなら、このエラーを処理してください。

コード例: [`DmlQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)（`insert-ignore`）。

<a id="the-row-count"></a>
## 影響行数

`execute()` は、データベースが報告した行数をそのまま返します。データベースごとの値は次のとおりです（実測）。

| データベース | 挿入 | 更新 | 無視 |
|---|---|---|---|
| H2 | 1 | 1 | 0 |
| MariaDB | 1 | **2** | 0 |
| Oracle | 1 | 1 | 0 |

したがって MariaDB では、`execute() == 1` は「更新された」という意味になりません。行が書き込まれたかどうかを確認するには `execute() > 0` を使ってください。

<a id="many-rows"></a>
## 複数行

`qb.upsert(X.class).values(list, (a, l) -> ...)` と、`values(list, ...)` の後の `onConflict(...)` は、**行ごとに文を 1 つ** 実行します。`execute()` は合計を返します。同じキーでもう一度実行すると、行は増えず値だけが変わります。
リスト形式も `values(list, (a, l, sub) -> ...)` のように 3 番目の引数でサブクエリを受け取ります（[7.5](07-5-insert.md#many-rows-from-a-list) を参照）。

<!-- region: ManualJavaDmlTest.java#upsert-list -->
```java
// one statement per row; execute() returns the sum
List<Line> lines = List.of(new Line(501, 1.0), new Line(502, 2.0));
int n = qb.upsert(Archive.class)
        .values(lines, (a, l) -> { a.setId(l.id()); a.setPrice(l.price()); })
        .execute();
```

コード例: [`ManualJavaDmlTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaDmlTest.java)（`upsert-list`）。

<a id="rules"></a>
## ルール

- 行にはキーの代入が必要です。`upsert(X.class)` は `@Id` をキーとして使います。
- キーはエンティティ自身のカラムでなければなりません。関連のパスはエラーです。
- `values(...)` の値では行を読めません（新しい行だからです）。既存の行は `update(...)` でだけ読みます。
- update と insert の代入ルールを適用します（[7.9](07-dml.md#79-all-rules-u1-to-u21-summary) を参照）。
- `insert ... select` で行う upsert はありません。
- ほかの DML 文と同じように、upsert にはトランザクションが必要で、すでに読み込まれたエンティティには反映されません（[7.8](07-dml.md#transactions) を参照）。

---

[7. 更新、挿入、削除（Java）](07-dml.md) | [7.2 更新](07-2-update.md) | [7.4 削除](07-4-delete.md) | [7.5 挿入](07-5-insert.md) | **7.6 Upsert** | [Kotlin](../kotlin/07-6-upsert.md)
