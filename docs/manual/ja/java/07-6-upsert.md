<a id="76-upsert"></a>
# 7.6 Upsert（Java）

> [!IMPORTANT]
> このページは韓国語版のマニュアルより古い内容です。最新のルールは[韓国語のページ](../../ko/java/07-6-upsert.md)を参照してください。

[7. 更新、挿入、削除（Java）](07-dml.md) | [7.2 更新](07-2-update.md) | [7.4 削除](07-4-delete.md) | [7.5 挿入](07-5-insert.md) | **7.6 Upsert** | [Kotlin](../kotlin/07-6-upsert.md)

---


upsert は行を挿入します。同じキーの行がすでにある場合は、代わりにその行を変更するか、その行をそのまま残します。
lambdaQL には 3 つの書き方があります。

| 書き方 | キー | キーがすでにあるとき |
|---|---|---|
| `qb.upsert(X.class).values(...)` | `@Id` | 代入したその他のカラムが新しい値になります |
| `qb.insert(X.class).values(...).onConflict(key).update(...)` | 主キーまたは一意制約のあるカラム | `update(...)` の代入を既存の行に対して実行します |
| `qb.insert(X.class).values(...).onConflict(key).ignore()` | 同上 | 何もしません。既存の行はそのまま残ります |

<a id="short-form-upsert"></a>
## 短い書き方: `upsert`

**(テスト済み)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)、リージョン `upsert`:

```java
// the key is the @Id; on conflict, the other assigned columns get the new values
int inserted = qb.upsert(Archive.class).values(a -> { a.setId(100L); a.setPrice(1.0); }).execute();
ExecutableInsert<Archive> again = qb.upsert(Archive.class).values(a -> { a.setId(100L); a.setPrice(2.0); });
assertEquals("insert into Archive e0 (id, price) values (100L, 2.0) on conflict(id) do update set price = excluded.price",
        again.toHql().get(0).hql());
int updated = again.execute();                     // MariaDB counts an updated row as 2, H2 and Oracle as 1
```

**SQL ブロックの読み方。** `…` は省略した select リスト（エンティティのすべてのカラム）で、`?` はバインドパラメーターです。各行は [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java) から取っています。このテストは各クエリを Hibernate 7.4 で H2 2.3、MariaDB 10.11、Oracle 23.26 に対して実行し、Hibernate の SQL が変わると失敗します。

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `upsert`:

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

行は `@Id` に代入しなければなりません。`@Id` だけに代入した場合は変更するものがないので、lambdaQL は下の `ignore()` の形を書きます。

<a id="your-own-change-onconflictkeyupdate"></a>
## 自分で書く変更: `onConflict(key).update(...)`

変更が「新しい値を使う」ではない場合、たとえばカウンターには、この書き方を使います。

**(テスト済み)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)、リージョン `on-conflict`:

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

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `on-conflict`:

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

`update(...)` の中の getter は **既存の** 行を読みます。`a.getPrice() + 5.0` は古い価格に 5 を足した値です。SQL では、H2 と Oracle はエイリアス（`a1_0.price`）を、MariaDB はテーブル名（`archive.price`）を書きます。

キーは getter のラムダ（`a -> a.getId()`）または getter のメソッド参照（`Archive::getId`）です。関連のパスではなく、エンティティ自身のカラムでなければならず、データベースではそのカラムに主キーか一意制約が必要です。

`values` と `update` に引数 2 つのラムダを書くと、2 番目の引数 `sub` でサブクエリを作れます（決定 D49、3 つのデータベースで実測）。例は [7.5](07-5-insert.md#a-subquery-in-a-value) を参照してください。

<a id="keep-the-existing-row-onconflictkeyignore"></a>
## 既存の行を残す: `onConflict(key).ignore()`

**(テスト済み)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)、リージョン `insert-ignore`:

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

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `insert-ignore`:

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

`execute()` は、データベースが報告した行数をそのまま返します（決定 D40）。テストでは次の値を確認しています（実測）。

| データベース | 挿入 | 更新 | 無視 |
|---|---|---|---|
| H2 | 1 | 1 | 0 |
| MariaDB | 1 | **2** | 0 |
| Oracle | 1 | 1 | 0 |

そのため MariaDB では、`execute() == 1` は「更新した」という意味になりません。行を書き込んだかどうかは `execute() > 0` で確認してください。

<a id="many-rows"></a>
## 複数行

`qb.upsert(X.class).values(list, (a, l) -> ...)` と、`values(list, ...)` のあとの `onConflict(...)` は、**1 行ごとに 1 つの文** を実行します。`execute()` は合計を返します。この書き方のテストはまだありません（未測定）。
リスト形式も `values(list, (a, l, sub) -> ...)` のように 3 番目の引数でサブクエリを受け取ります（[7.5](07-5-insert.md#many-rows-from-a-list) を参照）。

```java
int n = qb.upsert(Archive.class)
    .values(lines, (a, l) -> { a.setId(l.id()); a.setPrice(l.price()); })
    .execute();
```

<a id="rules"></a>
## ルール

- 行はキーに代入しなければなりません。`upsert(X.class)` は `@Id` をキーとして使います。
- キーはエンティティ自身のカラムでなければなりません。関連のパスはエラーです。
- `values(...)` の中の値は行を読めません（新しい行だからです）。既存の行を読むのは `update(...)` だけです。
- 更新と挿入の代入ルールが適用されます（U1、U3、U4、U6。[7.9](07-dml.md#79-all-rules-u1-to-u21-summary) を参照）。
- `insert ... select` からの upsert はありません。
- ほかの DML 文と同じように、upsert にはトランザクションが必要で、すでに読み込まれたエンティティは変更しません（[7.8](07-dml.md#transactions) を参照）。

---

---

[7. 更新、挿入、削除（Java）](07-dml.md) | [7.2 更新](07-2-update.md) | [7.4 削除](07-4-delete.md) | [7.5 挿入](07-5-insert.md) | **7.6 Upsert** | [Kotlin](../kotlin/07-6-upsert.md)
