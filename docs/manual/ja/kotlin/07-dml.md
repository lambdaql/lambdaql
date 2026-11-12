<a id="7-update-insert-delete-kotlin"></a>
# 7. 更新、挿入、削除（Kotlin）

> [!NOTE]
> **状況: 設計済み、まだ実装されていません。**
> この章は決定済みの設計です（決定 D28、マイルストーン M10）。
> Kotlin の構文はコンパイルでき（kotlinc 2.3.21）、SQL は Hibernate 6.6.5 を使って H2、MariaDB、Oracle で確認しました。
> 現在の lambdaQL ではまだ **動きません**。今動くのは解析コアと `where` + `toList()` だけです
> （H2、MariaDB、Oracle でテスト済み）。全体の状況は [トラックの README](README.md) を見てください。

[Kotlin トラック](README.md) | [マニュアルのホーム](../README.md) | 前: [6. 選択、集計、グループ化](06-select-group.md) | 次: [8. プロシージャと DB 関数](08-procedures-functions.md) | [このページの Java 版](../java/07-dml.md)

共通ページ: [1. 概要](../01-overview.md) · [11. エラーと言語](../11-errors-and-languages.md)

lambdaQL には 4 つの入口があります。

| 入口 | 用途 |
|---|---|
| `qb.from(X::class.java)` | 選択（3 章から 6 章） |
| `qb.update(X::class.java)` | 行を更新する |
| `qb.insert(X::class.java)` | 行を挿入する |
| `qb.delete(X::class.java)` | 行を削除する |

考え方はクエリと同じです。

- 各部分に **1 つのラムダ** を書きます。static なヘルパーはありません。
- ラムダが **実行されることはありません**。lambdaQL はそのバイトコードを読みます。`it.status = 1` のようなプロパティへの代入は setter の呼び出しにコンパイルされ、`set o.status = 1` になります。
- `where` は [3. 条件](03-where.md) と同じルールを使います。サブクエリは [5. サブクエリ](05-subquery.md) と同じく `sub` を使います。
- `execute()` は文を実行し、**影響を受けた行の数**（`Int`）を返します。

<a id="contents"></a>
## 目次

- [7.1 例で使うエンティティ](#71-example-entities)
- [7.2 更新](#72-update)
- [7.3 2 つの代入方法: プロパティか `by[column] = value` か](#73-two-ways-to-assign-property-or-bycolumn--value)
- [7.4 削除](#74-delete)
- [7.5 挿入](#75-insert)
- [7.6 DML での Kotlin の注意点](#76-kotlin-notes-for-dml)
- [7.7 重要なルール](#77-important-rules)
- [7.8 ルール U1 から U21 のすべて（まとめ）](#78-all-rules-u1-to-u21-summary)
- [7.9 v0.1 にないもの](#79-not-in-v01)

---

<a id="71-example-entities"></a>
## 7.1 例で使うエンティティ

この章では `Order`、`Customer`、`Item` を使います（[2. はじめよう](02-getting-started.md#example-entities) を参照）。
この章の `Order` には、いくつか **追加のプロパティ** があります。

```kotlin
@Entity
@Table(name = "orders")
class Order(
    // ... id、price、status、active、description、customer、updateAt、items はいつもどおり
    var total: Double? = null,                 // item の価格の合計
    var cnt: Int = 0,                          // カウンター（+= がコンパイルできるように Int）
    var custName: String? = null,              // 顧客名のコピー
    @Embedded var address: Address = Address(),
)

@Embeddable
class Address(var city: String? = null, var zip: String? = null)
```

ここで使うその他のクラス:

```kotlin
@Entity class VOrder(@Id var id: Long? = null, var status: Int? = null, @Version var version: Long? = null)
@Entity class Archive(@Id var id: Long? = null, var price: Double? = null, var customerName: String? = null)
@Entity class Summary(@Id var customerId: Long? = null, var total: Double? = null, var cnt: Long? = null)
@Entity class IdOrder(@Id @GeneratedValue var id: Long? = null, var price: Double? = null, var status: Int? = null)

data class Line(val id: Long, val price: Double)   // insert の入力行
```

---

<a id="72-update"></a>
## 7.2 更新

→ [7.2 更新](07-2-update.md)

<a id="73-two-ways-to-assign-property-or-bycolumn--value"></a>
## 7.3 2 つの代入方法: プロパティか `by[column] = value` か

| 書き方 | 使うとき | 例 |
|---|---|---|
| プロパティへの代入 | プロパティが `var`（setter がある）のとき。コンパイラーが値の型を確認します。 | `set { it.status = 1 }` |
| `by[column] = value` | プロパティが `val`（setter がない）のとき。たとえば DDD スタイルのエンティティ。 | `set { o, by -> by[o.status] = 1 }` |

`by[o.status] = 1` は、メソッド `by.set(o.status, 1)` に対する Kotlin のインデックス演算子です。`by.set(...)` と直接書くこともできます。意味は同じです。

上の例はすべて `by` でも書けます。

```kotlin
qb.update(Order::class.java)
    .set { o, by -> by[o.status] = st; by[o.price] = o.price!! * 1.1 }
    .where { it.status == 1 }
    .execute()

qb.update(Order::class.java)
    .set { o, by -> by.set(o.status, st).set(o.price, o.price!! * 1.1) }   // 同じものをチェーンで
    .where { it.status == 1 }
    .execute()

qb.update(Order::class.java).set { o, by -> by[o.address.city] = city }.where { it.id == id }.execute()  // 埋め込み
qb.update(Order::class.java).set { o, by -> by[o.customer?.id] = cid }.where { it.id == id }.execute()   // FK
```

ラムダの 2 つ目の引数は **1 つのコンテキストオブジェクト** です。`set(column, value)` とサブクエリ関数 `from(...)` の両方を持っています。
名前は `by` でも `sub` でも好きに付けてください。名前は関係ありません。

```kotlin
qb.update(Order::class.java)
    .set { o, by ->
        by[o.total] = by.from(Item::class.java).where { i -> i.order?.id == o.id }.sum { i -> i.price }
    }
    .all()
    .execute()
```

`by[column] = value` のルール（ルール U20）:

- カラムは **対象から始まるプロパティのパス** でなければなりません。プロパティ、埋め込みのパス、または関連の `@Id` です。
  キャプチャ値、計算、ソースエンティティのパスはエラーです。
- `by.set(column, value)` はジェネリックなので、値がカラムに合うかどうかをコンパイラーは確認しません。
  lambdaQL が解析時にカラムの型と値の型を確認します。
- `by[o.cnt] += 1` は **コンパイルできません**（意図的です）。`by[o.cnt] = o.cnt + 1` と書いてください。
- **1 つのラムダの中で** プロパティへの代入と `by[...]` を混ぜないでください。別々の `set()` 呼び出しなら混ぜられます。

---

<a id="74-delete"></a>
## 7.4 削除

→ [7.4 削除](07-4-delete.md)

<a id="75-insert"></a>
## 7.5 挿入

→ [7.5 挿入](07-5-insert.md)

<a id="76-kotlin-notes-for-dml"></a>
## 7.6 DML での Kotlin の注意点

| Kotlin の書き方 | 意味 |
|---|---|
| `it.status = x` | setter の呼び出し: `set o.status = :p1` |
| 右辺の `!!` | 無視されます。コードをコンパイルできるようにするためだけのものです（D23） |
| `a ?: b` | `coalesce(a, b)` |
| `it.customer?.id = x` | 常に代入します（`?.` は無視されます） |
| `it.cnt += 1` | サポートしています（`set o.cnt = o.cnt + 1`） |
| `by[o.status] = x` | `by.set(o.status, x)` と同じ |
| `by[o.cnt] += 1` | コンパイルできません（意図的です）。`by[o.cnt] = o.cnt + 1` と書いてください |
| `it.cnt++`、`it.cnt--` | **v0.1 にはありません**。`+= 1` を使ってください。エラーメッセージでもそう案内します |
| `it.apply { }`、`with(it) { }`、`it.also { }` | **v0.1 にはありません**。直接代入してください。エラーメッセージでもそう案内します |
| ラムダの中の `if` / `when` | **v0.1 にはありません**。代わりに `set()` を何度も呼んでください（U14） |
| ラムダの中のローカル `val` | 拒否されます。U3 の問題を隠すおそれがあるからです |

Kotlin についての詳細: [9. Kotlin の注意点](09-kotlin-notes.md)。

---

<a id="77-important-rules"></a>
## 7.7 重要なルール

<a id="the-right-side-reads-the-old-value"></a>
### 右辺は古い値を読む

SQL では、SET の右辺にあるすべてのカラムは、更新 **前** の値を意味します。lambdaQL はこのルールに従います。

```kotlin
.set { it.price = it.price!! * 2; it.total = it.price }
// エラー: price を先に代入し、そのあとで再び読んでいる
```

**同じ文の中でカラムに代入し、そのあとでそのカラムを読む** と、lambdaQL は解析時にエラーで止まります（ルール U3）。
理由: この場合、MariaDB は **新しい** 値を使いますが、H2 と Oracle は古い値を使います。結果が異なります（確認結果: MariaDB は 200、H2 と Oracle は 100）。

次のケースは問題ありません。3 つのデータベースすべてで結果が一致するからです。

| 書き方 | OK? |
|---|---|
| `it.price = it.price!! * 1.1`（自分自身を読む） | はい |
| `it.total = it.price; it.price = 0.0`（先に読み、あとで代入する） | はい |
| `it.price = ...; it.total = it.price`（先に代入し、あとで読む） | **エラー** |

この確認は物理カラム（埋め込みのサブカラム、FK カラム）に対して行われ、複数の `set()` 呼び出しをまたいでも、ネストした `sub` ラムダの中でも働きます。

<a id="assign-a-column-only-once"></a>
### カラムへの代入は 1 回だけ

同じカラムに 2 回代入するとエラーです（ルール U4）。これには、`it.address = ...` のあとに `it.address.city = ...` と書く場合や、
`it.customer = ...` のあとに `it.customer?.id = ...` と書く場合も含まれます。

<a id="where-or-all-is-required"></a>
### `where` か `all()` が必要

`set()` は `execute()` を **持たない** 型を返します。`execute()` を持つ型を返すのは `where()` と `all()` だけです。
そのため、`where` を書き忘れるとコンパイルエラーになります（ルール U5）。`delete` でも同じです。

<a id="association-paths-same-rows-as-select"></a>
### 関連のパス: 選択と同じ行

`where` の中では、FK 以外の関連のパスは **相関 `exists`** になります（ルール U9）。

```kotlin
.where { it.customer?.name == "Kim" }
// → where exists (select 1 from Customer c where c.id = o.customer.id and c.name = 'Kim')
```

SET の右辺では、**相関スカラーサブクエリ** になります。

```kotlin
it.custName = it.customer?.name
// → set o.custName = (select c.name from Customer c where c.id = o.customer.id)
```

なぜでしょうか。lambdaQL がパスをそのまま Hibernate に渡すと、Hibernate はデータベースごとに異なる結合の SQL を作り
（H2 は `merge`、MariaDB は `update ... join`、Oracle はインラインビュー）、SET にパスがあると **顧客のない行が失われました**
（確認結果: `.all()` で 10 行のうち 9 行しか更新されませんでした）。
lambdaQL はサブクエリを自分で書きます。そのため、`update` と `delete` は、同じ `where` ラムダが選択で返す **まったく同じ行** を対象にします。
選択では `where` の中のパスはデフォルトで内部結合なので（決定 D36）、顧客のパスに対する `where` は、null ガードがそれらを残さない限り、顧客のない注文には作用しません。

`@Id` で終わるパス（`it.customer?.id`）は FK カラムです。サブクエリも結合も使いません。

<a id="batching-for-many-rows"></a>
### 多くの行のバッチ処理

`values(list) { ... }` は、多くの VALUES 行を持つ 1 つの文を書きます（ルール U13）。

| 項目 | ルール |
|---|---|
| 分割の単位 | 行数ではなく、バインド値の数（行 x カラム） |
| デフォルトの上限 | 1 文あたり 1000 個のバインド値 |
| 戻り値 | すべての部分の合計 |
| トランザクション | 分割はトランザクションの中でだけ許されます |
| 空のリスト | SQL を実行せず、0 を返します |

Oracle にはバインド値 65,535 個という厳しい上限があります（ORA-01745）。それより前に解析がとても遅くなります
（テストでは 21,845 行で約 6 分）。そのため、デフォルトの上限を使ってください。

<a id="the-persistence-context-is-not-updated"></a>
### 永続化コンテキストは更新されない

一括 DML は JPA の永続化コンテキストを通りません（ルール U16）。

- Hibernate は文の前に保留中の変更をフラッシュします。
- 文のあと、同じセッションですでに読み込まれたエンティティは **古い値** のままです。それらを再び使う場合は `entityManager.clear()` を呼んでください。
- JPA の `cascade` と `orphanRemoval` は **実行されません**。先に子を削除するか、データベースで `ON DELETE CASCADE` を使ってください。

---

<a id="78-all-rules-u1-to-u21-summary"></a>
## 7.8 ルール U1 から U21 のすべて（まとめ）

| # | ルール |
|---|---|
| U1 | 代入のラムダには、対象、その埋め込みのパス、または関連の `@Id` に対する **代入（setter の呼び出し）だけ** を書きます。`if`、`when`、ループ、ローカル変数、`++`、スコープ関数、その他の呼び出しはエラーです。代入のないラムダもエラーです。 |
| U2 | 右辺は `where` の値のルール（[3. 条件](03-where.md)）に従います。キャプチャ値はバインドパラメーターです。`a ?: b` は `coalesce(a, b)` になります。 |
| U3 | 右辺は **古い** 値を読みます。同じ文の中でカラムに代入したあとでそのカラムを読むとエラーです。 |
| U4 | 同じ物理カラムに 2 回代入するとエラーです。`set()` 呼び出しをまたぐ場合も同じです。 |
| U5 | `where()` か `all()` がないと `execute()` はありません。コンパイラーが確認します。 |
| U6 | 省略できないカラム（`@Id`、`nullable=false`、プリミティブ）に `null` を代入するとエラーです。コレクション、または関連エンティティの `@Id` 以外のプロパティへの代入もエラーです。 |
| U7 | キャプチャ値は、使うたびに専用のパラメーターが作られます。（STRING カラムと ORDINAL カラムで 1 つの enum パラメーターを共有すると、テストでは黙って間違った値が保存されました。） |
| U8 | `LocalDateTime.now()` はアプリケーションで評価されてバインドされます。データベースの時刻（`current_timestamp`）はサポートしていません。 |
| U9 | DML の FK 以外の関連のパス: `where` では相関 `exists`（選択と同じ行、D36）、SET の右辺では相関スカラーサブクエリ。DML は選択と同じ行に作用します。 |
| U10 | `insert ... select` では、ソースのパスは明示的な `left join` になります。 |
| U11 | `it.customer = entity` はエンティティの `@Id` をバインドします。id が null だとバインドに失敗します。 |
| U12 | IDENTITY と SEQUENCE の id は省略できます。TABLE ジェネレーターは `values` でだけ使え、`insert ... select` では使えません。ジェネレーターがない場合、id は必須です。その他の省略できないプロパティも必須です。 |
| U13 | 多くの行はバインド数（デフォルト 1000）で分割されます。結果は合計されます。トランザクションの中でだけ使えます。 |
| U14 | 1 つの `set` ラムダで多くのカラムに代入でき、`set()` は何度でも呼べます。すべてが呼び出し順に 1 つの SET リストにまとめられます。 |
| U15 | `@Version` エンティティは `update versioned` を使います。バージョンを自分で代入しないでください。`execute() == 0` の確認はあなたの仕事です。 |
| U16 | 一括 DML は永続化コンテキストを通りません。前にフラッシュ、あとは古いエンティティ、カスケードなし。 |
| U17 | DELETE のサブクエリが同じエンティティを読む場合、lambdaQL はそれを派生テーブルで包みます。（そうしないと MariaDB のエラー 1093 になります。） |
| U18 | 継承: SINGLE_TABLE は完全にサポートしています。JOINED と TABLE_PER_CLASS の update/delete は一時テーブルを使います。それらのサブクラスへの insert は拒否されます。 |
| U19 | クエリキャッシュのキーには、入口、対象エンティティ、set のラムダ、where/all、versioned、分割サイズが含まれます。 |
| U20 | `by[column] = value` / `by.set(column, value)`: カラムは対象のプロパティのパスでなければなりません。型は解析時に確認されます。1 つのラムダの中でプロパティへの代入と `by` を混ぜないでください。 |
| U21 | 各ラムダは 1 回だけ解析され、キャッシュされます。`set()` 呼び出しをまたぐ確認は、キャッシュした要約を比べるだけです（プロトタイプで約 0.1 から 0.2 µs）。 |

---

<a id="79-not-in-v01"></a>
## 7.9 v0.1 にないもの

| 項目 | 予定 | 今できること |
|---|---|---|
| 条件付きの値（`if (c) a else b`、`when`、値としての切り替え `!it.active`） | v0.2（CASE WHEN） | `where` を変えた 2 つの更新 |
| 代入を選ぶためのラムダ内の `if` | v0.2 | `set()` を何度も呼ぶ（U14） |
| `++`、`--`、`apply`、`with`、`also` | v0.2 | `+= 1` か直接の代入を使う |
| 文字列テンプレートや `+`（`"${it.description}_x"`） | v0.2 | – |
| `insert ... select` での明示的な結合 | v0.2 | 多対一のパスを使う |
| Upsert（`on conflict`） | v0.2 | – |
| `order by ... limit` を使う DML | 拒否 | 先に id を選択し、その id のリストで削除する |
| `returning` | 拒否 | DML のあとでもう一度クエリする |
| データベースの時刻（`current_timestamp`） | 拒否 | アプリケーションの時刻をバインドする（U8） |

この章のエラーは、`[LQL-xxxx]` コードを持つ `LambdaQLException` のサブクラスです。[11. エラーと言語](../11-errors-and-languages.md) を参照してください。
すべてのルールを 1 か所にまとめたもの: [10. ルールと制限](10-rules-and-limits.md)。

---

[Kotlin トラック](README.md) | 前: [6. 選択、集計、グループ化](06-select-group.md) | 次: [8. プロシージャと DB 関数](08-procedures-functions.md) | [このページの Java 版](../java/07-dml.md)
