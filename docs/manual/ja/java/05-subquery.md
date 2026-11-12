<a id="5-subqueries-java"></a>
# 5. サブクエリ（Java）

> [!NOTE]
> **状況**
> - **設計済み、まだ実装されていません**（マイルストーン M8）。
> - 構文は Java でコンパイルでき、バイトコードはプロトタイプで確認しました。SQL の形は、Hibernate 6.6.5 を使って H2 2.3、MariaDB 10.11、Oracle 23ai で確認しました。
> - この章の HQL は **予定の** 出力です。

[Java トラック](README.md) | [マニュアルのホーム](../README.md) | 前: [4. ソートとページング](04-order-paging.md) | 次: [6. 選択、集計、グループ化](06-select-group.md) | [このページの Kotlin 版](../kotlin/05-subquery.md)

共通ページ: [1. 概要](../01-overview.md) · [11. エラーと言語](../11-errors-and-languages.md)

---

<a id="51-the-idea-a-second-lambda-argument-sub"></a>
## 5.1 考え方: 2 つ目のラムダ引数 `sub`

`where` のラムダは **2 つ目の引数** としてサブクエリのファクトリーを受け取れます。このマニュアルではこれを `sub` と呼びます（どんな名前でも使えます）。
その中では、通常のクエリと **同じラムダの構文** でサブクエリを書きます。

```java
.where(o -> ...)              // サブクエリなし: 引数 1 つ
.where((o, sub) -> ...)       // サブクエリあり: 引数 2 つ
```

lambdaQL は引数の数で 2 つの形を区別します。
サブクエリは `&&`、`||`、`!` と自由に組み合わせられます。static import は必要ありません。

`sub` でできること:

| サブクエリ | 使い方 | HQL |
|---|---|---|
| `sub.from(X.class).where(...).exists()` | 条件 | `exists (select 1 from X x where ...)` |
| `!sub.from(X.class).where(...).exists()` | 条件 | `not exists (...)` |
| `sub.from(X.class).where(...).select(x -> v).contains(y)` | 条件 | `y in (select x.v from X x where ...)` |
| `!... .contains(y)` | 条件 | `not exists (... and x.v = y)` |
| `sub.from(X.class).avg(x -> v)`（`sum`、`min`、`max`、`count()` も） | 値 | `(select avg(x.v) from X x)` |
| `sub.from(X.class)...orderBy(...).limit(1).select(x -> v).first()` | 値 | top-1 サブクエリ |

> [!NOTE]
> 戻り値（`boolean`、`Double`、...）は、コードをコンパイルできるようにするための仮の値にすぎません。
> ラムダが実行されることはありません。これらのメソッドは lambdaQL のラムダの中でだけ意味を持ちます。

内側のラムダ（`sub.from(...).where(...)` の中の `i -> ...`）は、それ自体が `Serializable` である必要はありません。lambdaQL は外側のラムダのバイトコードの中からそれらを見つけます。

## 5.2 EXISTS

`qty > 10` の品目を 1 つ以上持つ注文:

```java
qb.from(Order.class)
  .where((o, sub) -> sub.from(Item.class)
                        .where(i -> i.getOrder().getId() == o.getId() && i.getQty() > 10)
                        .exists())
  .toList();
// HQL: select o from Order o
//      where exists (select 1 from Item i where i.order.id = o.id and i.qty > 10)
```

**SQL ブロックの読み方。** `…` は省略した select リスト（エンティティのすべてのカラム）で、`?` はバインドパラメーターです。[`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java) はこのページの HQL を Hibernate に直接渡し、H2 2.3、MariaDB 10.11、Oracle 23.26（Hibernate 6.6.5）での SQL を記録します。lambdaQL はまだこれらのクエリを作りません。Hibernate の SQL が変わるとテストは失敗します。

**データベースごとの SQL（予定）** — 予定の HQL を Hibernate が変換したもので、`PlannedSqlTest` が記録しました。lambdaQL はまだこのクエリを作りません。`expected-sql/` の [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)、例 `05-exists`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id and i1_0.qty>10)
```

これは **相関** サブクエリです。内側のラムダが外側の `o` を使っています（[5.6](#56-correlated-subqueries) を参照）。

## 5.3 NOT EXISTS

`!` を使います。ほかの条件と組み合わせることもできます。

```java
// ステータスが 1 の注文、または品目を持たない注文
qb.from(Order.class)
  .where((o, sub) -> o.getStatus() == 1
         || !sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).exists())
  .toList();
// HQL: select o from Order o
//      where o.status = 1
//         or not exists (select 1 from Item i where i.order.id = o.id)
```

**データベースごとの SQL（予定）** — 予定の HQL を Hibernate が変換したもので、`PlannedSqlTest` が記録しました。lambdaQL はまだこのクエリを作りません。`expected-sql/` の [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)、例 `05-not-exists-or`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 or not exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id)
```

## 5.4 IN

`grade > 3` の顧客の注文:

```java
qb.from(Order.class)
  .where((o, sub) -> sub.from(Customer.class)
                        .where(c -> c.getGrade() > 3)
                        .select(c -> c.getId())
                        .contains(o.getCustomer().getId()))
  .toList();
// HQL: select o from Order o
//      where o.customer.id in (select c.id from Customer c where c.grade > 3)
```

**データベースごとの SQL（予定）** — 予定の HQL を Hibernate が変換したもので、`PlannedSqlTest` が記録しました。lambdaQL はまだこのクエリを作りません。`expected-sql/` の [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)、例 `05-in-subquery`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id in (select c2_0.id from customers c2_0 where c2_0.grade>3)
```

`o.getCustomer().getId()` は顧客の `@Id` で終わるので、外部キーのカラム `o.customer.id` になります。結合は必要ありません。

<a id="55-not-in-is-written-as-not-exists"></a>
## 5.5 NOT IN は NOT EXISTS として書かれる

`!values.contains(x)` は `NOT IN` として出力され **ません**。lambdaQL は `NOT EXISTS` を書きます。

```java
qb.from(Order.class)
  .where((o, sub) -> !sub.from(Customer.class)
                         .where(c -> c.getGrade() > 3)
                         .select(c -> c.getId())
                         .contains(o.getCustomer().getId()))
  .toList();
// HQL: select o from Order o
//      where not exists (select 1 from Customer c
//                        where c.grade > 3 and c.id = o.customer.id)
```

**データベースごとの SQL（予定）** — 予定の HQL を Hibernate が変換したもので、`PlannedSqlTest` が記録しました。lambdaQL はまだこのクエリを作りません。`expected-sql/` の [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)、例 `05-not-in`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where not exists(select 1 from customers c2_0 where c2_0.grade>3 and c2_0.id=o1_0.customer_id)
```

理由:

- `x NOT IN (subquery)` は、サブクエリが NULL を 1 つでも返すと **1 行も返しません**。これは SQL のよくある落とし穴です。
- `NOT EXISTS` にはこの問題がありません。データベースも（アンチ結合として）効率よく実行します。
- 結果: 顧客のない注文は、どの顧客とも一致しないので、上のクエリで返されます。

<a id="56-correlated-subqueries"></a>
## 5.6 相関サブクエリ

内側のラムダが外側のエンティティを使うと、サブクエリは **相関** サブクエリになります。特別な構文は必要ありません。外側の変数をそのまま使うだけです。

```java
.where((o, sub) -> sub.from(Item.class)
                      .where(i -> i.getOrder().getId() == o.getId())   // o は外側から来る
                      .exists())
```

lambdaQL が内側のラムダのキャプチャ値をどう読むか:

| 内側のラムダでキャプチャしたもの | 変換後 |
|---|---|
| 外側のエンティティ（`o`） | 外側のエイリアスへの参照（`o.id`） |
| それ以外の値（ローカル変数、フィールド、...） | バインドパラメーター（[3.11](03-where.md#311-captured-values) と同じ） |

```java
int minQty = 10;
.where((o, sub) -> sub.from(Item.class)
                      .where(i -> i.getOrder().getId() == o.getId() && i.getQty() > minQty)
                      .exists())
// HQL: exists (select 1 from Item i where i.order.id = o.id and i.qty > :p1)
```

**データベースごとの SQL（予定）** — 予定の HQL を Hibernate が変換したもので、`PlannedSqlTest` が記録しました。lambdaQL はまだこのクエリを作りません。`expected-sql/` の [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)、例 `05-correlated-param`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id and i1_0.qty>?)
```

**相関での `==`。** `i.getOrder().getId() == o.getId()` は 2 つの `Long` オブジェクトを比較します。通常のルールでは、これは null-safe 比較（`is not distinct from`、[3.6](03-where.md#36-null-rules) を参照）です。
しかし `o.id` は `@Id` なので、NULL になることはありません。この場合、lambdaQL は単純な `=` を書きます（`!=` には `(a <> b or a is null)`）。
結果は同じで、データベースはインデックスを使えます。Oracle では `is not distinct from` は `decode(...)` になり、インデックスを使えません。

<a id="57-scalar-subqueries"></a>
## 5.7 スカラーサブクエリ

**1 つの値** を返すサブクエリは、比較の中で使えます。

価格が平均より高い注文:

```java
qb.from(Order.class)
  .where((o, sub) -> o.getPrice() > sub.from(Order.class).avg(x -> x.getPrice()))
  .toList();
// HQL: select o from Order o
//      where o.price > (select avg(x.price) from Order x)
```

**データベースごとの SQL（予定）** — 予定の HQL を Hibernate が変換したもので、`PlannedSqlTest` が記録しました。lambdaQL はまだこのクエリを作りません。`expected-sql/` の [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)、例 `05-scalar-avg`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.price>(select avg(o2_0.price) from orders o2_0)
```

スカラーサブクエリは **常に 1 行を返さなければなりません**。使える形は 2 つです。

| 形 | 例 |
|---|---|
| 集計: `count()`、`sum`、`avg`、`min`、`max` | `sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).count()` |
| top-1: `orderBy(...).limit(1)...first()` | 下記を参照 |

それ以外の `first()` は解析時にエラーになります。

top-1 の例: 同じ顧客の最新の注文。

```java
qb.from(Order.class)
  .where((o, sub) -> o.getId().equals(
          sub.from(Order.class)
             .where(x -> x.getCustomer().getId() == o.getCustomer().getId())
             .orderBy((x, by) -> by.desc(x.getUpdateAt()))
             .limit(1)
             .select(x -> x.getId())
             .first()))
  .toList();
```

集計の NULL は SQL に従います。行がないとき、`sum`、`avg`、`min`、`max` は NULL を返します。`count` は 0 を返します。

<a id="58-nested-subqueries"></a>
## 5.8 入れ子のサブクエリ

サブクエリの `where` も `sub` 引数を受け取れます。どの階層でも同じルールがもう一度適用されます。

大きな品目を含む注文を持つ顧客:

```java
qb.from(Customer.class)
  .where((c, sub) -> sub.from(Order.class)
      .where((o, sub2) -> o.getCustomer().getId() == c.getId()
             && sub2.from(Item.class)
                    .where(i -> i.getOrder().getId() == o.getId() && i.getQty() > 10)
                    .exists())
      .exists())
  .toList();
// HQL: select c from Customer c
//      where exists (select 1 from Order o
//                    where o.customer.id = c.id
//                      and exists (select 1 from Item i
//                                  where i.order.id = o.id and i.qty > 10))
```

**データベースごとの SQL（予定）** — 予定の HQL を Hibernate が変換したもので、`PlannedSqlTest` が記録しました。lambdaQL はまだこのクエリを作りません。`expected-sql/` の [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)、例 `05-nested`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from customers c1_0
where exists(select 1 from orders o1_0 where o1_0.customer_id=c1_0.id and exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id and i1_0.qty>10))
```

Java では、入れ子のラムダで同じ名前を 2 回使えません。階層ごとに新しい名前（`sub2`、`o`、`i`）を使ってください。

<a id="59-sorting-inside-a-subquery"></a>
## 5.9 サブクエリの中のソート

| ケース | lambdaQL の動作 |
|---|---|
| `limit` 付きの `orderBy`（top-1、top-N） | 残す |
| `limit` なしの `orderBy` | **削除し**、警告をログに出す（結果は変わらない。Oracle では構文エラー ORA-00907 にもなる） |
| MariaDB: `IN (... limit n)`（MariaDB はサポートしない） | 自動的に包む: `in (select x.id from (... limit n) x)` |
| MariaDB: `lateral`（MariaDB では結果が間違う） | `row_number() over (partition by ... order by ...)` で書き換える |

[4. ソートとページング](04-order-paging.md) の NULL のポリシーと `id` の自動タイブレーカーは、サブクエリの中でも適用されます。

<a id="510-paths-and-null-inside-subqueries"></a>
## 5.10 サブクエリの中のパスと NULL

サブクエリの中では、条件、パス、NULL は **外側のクエリとまったく同じように** 動きます（[3. 条件](03-where.md)）。

- オブジェクトに対する `==` は null-safe な値の比較です。
- サブクエリの条件の中の関連のパスは、外側の `where` と同じ結合のルールに従います（決定 D36）。デフォルトは内部結合の `join` で、null ガードが関連のない行を残す場合は `left join` です。
- `@Id` で終わるパスは外部キーのカラムです（結合なし）。
- コレクションのパス（`o.getItems()`）は拒否されます。コレクションについて問い合わせるにはサブクエリを使います。

```java
// o.getItems().size() > 0（拒否される）の代わりに:
.where((o, sub) -> sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).exists())
```

<a id="511-subqueries-in-other-places"></a>
## 5.11 ほかの場所でのサブクエリ

- **`from` の中のサブクエリ（派生テーブル）と CTE**（`qb.from(sub -> ...)`、`qb.with(sub -> ...)`）: [6. 選択、集計、グループ化](06-select-group.md) を参照してください。
- **UPDATE / DELETE の中のサブクエリ**: 同じ `sub` の構文です。[7. 更新、挿入、削除](07-dml.md) を参照してください。

<a id="512-rules-summary-q1-to-q6"></a>
## 5.12 ルールのまとめ（Q1〜Q6）

| # | ルール |
|---|---|
| Q1 | サブクエリの中の条件、パス、NULL のルールは外側と同じ。条件の中の関連のパスも同じ結合のルールを使う（デフォルトは内部結合の `join`） |
| Q2 | **NOT IN は NOT EXISTS として出力される**: `!values.contains(x)` は `not exists (select 1 from C c where <condition> and c.sel = x)` になる。これで `NOT IN` + NULL の落とし穴を避ける |
| Q3 | スカラーサブクエリは 1 行を返さなければならない: 集計（`max`、`min`、`avg`、`sum`、`count`）または `orderBy(...).limit(1)...first()`。それ以外の `first()` はエラー |
| Q4 | サブクエリのソート: `limit` があれば残し、`limit` がなければ警告を出して削除。MariaDB の `IN (... limit)` は包み、`lateral` は `row_number()` で書き換える |
| Q5 | 相関では、片方が決して NULL にならない（`@Id`、`nullable = false`、プリミティブ）とき、`==` を `=` として書く。`!=` は `(a <> b or a is null)` になる。これでインデックスを使える |
| Q6 | サブクエリの `where` は自分の `sub` 引数を受け取れる: `where((i, sub2) -> ...)`。どの階層でも同じルールが適用される |

---

[Java トラック](README.md) | 前: [4. ソートとページング](04-order-paging.md) | 次: [6. 選択、集計、グループ化](06-select-group.md) | [このページの Kotlin 版](../kotlin/05-subquery.md)
