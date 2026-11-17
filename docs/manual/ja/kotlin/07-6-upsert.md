<a id="76-upsert"></a>
# 7.6 Upsert（Kotlin）

> [!NOTE]
> **状況: 実装済み、テスト済み**（H2 2.3、MariaDB 10.11、Oracle 23.26、マイルストーン M10、決定 D40）。テストは **Java** のラムダを使っています。**upsert での Kotlin のラムダはまだテストしていません**（update、insert、delete はテスト済みです。[7. 更新、挿入、削除](07-dml.md) を参照）。
> Kotlin コードはテスト済みの Java の文を Kotlin の構文で書いたもので、HQL は [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) の Java のテストから取っています。HQL のない例は設計に従っています。
> 全体の状況は [トラックの README](README.md) を見てください。

[7. 更新、挿入、削除（Kotlin）](07-dml.md) | [7.2 更新](07-2-update.md) | [7.4 削除](07-4-delete.md) | [7.5 挿入](07-5-insert.md) | **7.6 Upsert** | [Java](../java/07-6-upsert.md)

---


upsert は行を挿入します。同じキーの行がすでにある場合は、代わりにその行を変更するか、その行をそのまま残します。
lambdaQL には 3 つの書き方があります。

| 書き方 | キー | キーがすでにあるとき |
|---|---|---|
| `qb.upsert(X::class.java).values { ... }` | `@Id` | 代入したその他のカラムが新しい値になります |
| `qb.insert(X::class.java).values { ... }.onConflict { key }.update { ... }` | 主キーまたは一意制約のあるカラム | `update { ... }` の代入を既存の行に対して実行します |
| `qb.insert(X::class.java).values { ... }.onConflict { key }.ignore()` | 同上 | 何もしません。既存の行はそのまま残ります |

<a id="short-form-upsert"></a>
## 短い書き方: `upsert`

```kotlin
// the key is the @Id; on conflict, the other assigned columns get the new values
val inserted = qb.upsert(Archive::class.java).values { it.id = 100L; it.price = 1.0 }.execute()
val again = qb.upsert(Archive::class.java).values { it.id = 100L; it.price = 2.0 }
// HQL: insert into Archive e0 (id, price) values (100L, 2.0) on conflict(id) do update set price = excluded.price
val updated = again.execute()                 // MariaDB counts an updated row as 2, H2 and Oracle as 1
```

**SQL ブロックの読み方。** `…` は省略した select リスト（エンティティのすべてのカラム）で、`?` はバインドパラメーターです。各行は [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java) から取っています。このテストは各クエリを Hibernate 6.6.5 で H2 2.3、MariaDB 10.11、Oracle 23.26 に対して実行し、Hibernate の SQL が変わると失敗します。 このテストは Java のラムダを使います。意味が同じ Kotlin のラムダは同じ HQL になるので、SQL も同じです。

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `upsert`:

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

Kotlin のテストはまだありません。Java で書いた同じ文は [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) でテストしています（H2、MariaDB、Oracle）。テスト済みの例は [このページの Java 版](../java/07-6-upsert.md) を見てください。

SQL はデータベースごとに違います。H2 と Oracle は `merge` 文になります。H2 は新しい行を `values (...)` から、Oracle は `select 100 id,2.0 price` から読みます。MariaDB は `insert ... on duplicate key update` になり、新しい値を `values(price)` で読みます。

行は `@Id` に代入しなければなりません。`@Id` だけに代入した場合は変更するものがないので、lambdaQL は下の `ignore()` の形を書きます。

<a id="your-own-change-onconflictkeyupdate"></a>
## 自分で書く変更: `onConflict(key).update(...)`

変更が「新しい値を使う」ではない場合、たとえばカウンターには、この書き方を使います。

```kotlin
// on conflict, change the existing row: a property read here reads the existing row
val add = qb.insert(Archive::class.java)
    .values { it.id = 100L; it.price = 5.0 }
    .onConflict { it.id }
    .update { it.price = it.price!! + 5.0 }
// HQL: insert into Archive e0 (id, price) values (100L, 5.0) on conflict(id) do update set price = (e0.price + 5.0)
add.execute()                                 // inserted: 5.0
add.execute()                                 // conflict: 5.0 + 5.0
```

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `on-conflict`:

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

`update { ... }` の中のプロパティの読み取りは、**既存の** 行を読みます。`it.price!! + 5.0` は古い価格に 5 を足した値です。SQL では、H2 と Oracle はエイリアス（`a1_0.price`）を、MariaDB はテーブル名（`archive.price`）を書きます。

キーはラムダ（`{ it.id }`）です。[4. ソートとページング](04-order-paging.md) と同じく、Kotlin のプロパティ参照（`Archive::id`）はまだ実装されていません。キーは関連のパスではなく、エンティティ自身のカラムでなければならず、データベースではそのカラムに主キーか一意制約が必要です。

<a id="keep-the-existing-row-onconflictkeyignore"></a>
## 既存の行を残す: `onConflict(key).ignore()`

```kotlin
val once = qb.insert(Archive::class.java)
    .values { it.id = 300L; it.price = 1.0 }
    .onConflict { it.id }
    .ignore()
// HQL: insert into Archive (id, price) select 300L, 1.0 where not exists (select 1 from Archive x where x.id = 300L)
val first = once.execute()                    // 1: inserted
val second = qb.insert(Archive::class.java).values { it.id = 300L; it.price = 2.0 }
    .onConflict { it.id }.ignore().execute()  // 0: the row exists, nothing changes
```

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `insert-ignore`:

```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price)
select 300,1.0
where not exists(select 1 from archive a2_0 where a2_0.id=300)
```

Hibernate 自身の `on conflict ... do nothing` は、H2 と Oracle では普通の insert として実行され、キー重複のエラーで失敗します。
そのため lambdaQL は `insert ... select ... where not exists (...)` を自分で書きます。SQL は 3 つのデータベースで同じです。

> [!WARNING]
> **`ignore()` はアトミックではありません。** 2 つのトランザクションが同じキーを同時に挿入すると、両方が `not exists` の確認を通ることがあります。
> その場合、一方がキー重複のエラーになります。アプリケーションでこれが起こりうるなら、エラーを捕捉してください。

<a id="the-row-count"></a>
## 行数

`execute()` は、データベースが報告した行数をそのまま返します（決定 D40）。Java のテストでは次の数を確認しています。

| データベース | 挿入 | 更新 | 無視 |
|---|---|---|---|
| H2 | 1 | 1 | 0 |
| MariaDB | 1 | **2** | 0 |
| Oracle | 1 | 1 | 0 |

そのため MariaDB では、`execute() == 1` は「更新した」という意味になりません。行を書き込んだかどうかは `execute() > 0` で確認してください。

<a id="many-rows"></a>
## 複数行

`qb.upsert(X::class.java).values(list) { a, l -> ... }` と、`values(list) { ... }` のあとの `onConflict { ... }` は、**1 行ごとに 1 つの文** を実行します。`execute()` は合計を返します。これはまだテストで確認していません。

```kotlin
val n = qb.upsert(Archive::class.java)
    .values(lines) { a, l -> a.id = l.id; a.price = l.price }
    .execute()
```

<a id="rules"></a>
## ルール

- 行はキーに代入しなければなりません。`upsert(X::class.java)` は `@Id` をキーとして使います。
- キーはエンティティ自身のカラムでなければなりません。関連のパスはエラーです。
- `values { ... }` の中の値は行を読めません（新しい行だからです）。既存の行を読むのは `update { ... }` だけです。
- 更新と挿入の代入ルールが適用されます（U1、U3、U4、U6。[7.9](07-dml.md#79-all-rules-u1-to-u21-summary) を参照）。
- `insert ... select` からの upsert はありません。
- ほかの DML 文と同じように、upsert にはトランザクションが必要で、すでに読み込まれたエンティティは変更しません（[7.8](07-dml.md#transactions) を参照）。

---

---

[7. 更新、挿入、削除（Kotlin）](07-dml.md) | [7.2 更新](07-2-update.md) | [7.4 削除](07-4-delete.md) | [7.5 挿入](07-5-insert.md) | **7.6 Upsert** | [Java](../java/07-6-upsert.md)
