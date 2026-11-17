<a id="lambdaql-manual-kotlin-track"></a>
# lambdaQL マニュアル: Kotlin トラック

**他の言語:** [English](../../en/kotlin/README.md) | [한국어](../../ko/kotlin/README.md) | [日本語](../../ja/kotlin/README.md) | [简体中文](../../zh/kotlin/README.md)

> [!IMPORTANT]
> **状況:** lambdaQL は開発の初期段階です。
> 実装されているのは **解析コア**、**結果の関数付きの `where`**（`toList()`、`toSet()`、`first()`、`one()`、`count()`、`exists()`）、そして **ソートとページング**（`orderBy`、`limit`、`offset`、`SortSpec`）の中核部分です。lambdaQL はラムダを読み込み、それをデコードし、HQL を書いて実行します。これは H2、MariaDB、Oracle でテスト済みで、ほとんどは **Java** のラムダを使っています。
> **Kotlin のラムダはビルドの中でテストしています**（kotlinc 2.3.21、[`KotlinSelectTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinSelectTest.kt)、[`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)）。対象は `?.` のパス、`!!`、キャプチャ値、`in`、ソート、サブクエリ、data class・`Pair`・`Triple` を使う `select`、`groupBy` と `having`、更新、挿入、削除です。そのほかの Kotlin だけの書き方（`?:`、範囲、`when`、プロパティ参照）は **設計済み** です。そのバイトコードはコンパイルしてプロトタイプで確認しました（kotlinc 1.7.22 から 2.3.21）。
> **サブクエリ**、**`select`**（`groupBy`、`having`、`Agg` を含む）、**更新、挿入、削除、upsert**（`limit` と 1 行保護 `executeOne()` を含む）、**明示的な結合**、**派生テーブルと CTE**、**ストアドプロシージャと DB 関数**、**4 つの言語のエラーコード** は実装済みです。結合、派生テーブル、CTE、プロシージャ、DB 関数、upsert は Java のラムダでのみテストしています。
> **Spring Data JPA モジュール**（`LambdaQlExecutor<T>`、Spring の `Sort` と `Pageable`、`deleteAll { }` や `updateOne({ }) { }` のような DML の短縮メソッド、更新、削除、挿入、upsert のビルダー、Spring Boot の自動構成）は実装済みで、Java のリポジトリでテストしています（Spring Boot 3.4.2）。DML には呼び出し側のトランザクションが必要です。Kotlin のリポジトリはまだテストしていません。
> 成果物（アーティファクト）はまだ Maven Central に公開していません。試すには、[ソースからビルド](02-getting-started.md#build-from-source)してください。

[マニュアルのホーム](../README.md) | [Java トラック](../java/README.md) | 共通ページ: [1. 概要](../01-overview.md) · [11. エラーと言語](../11-errors-and-languages.md)

これは lambdaQL マニュアルの **Kotlin トラック** です。このトラックのページには **Kotlin のコードだけ** が載っています。
クエリを Java で書く場合は、[Java トラック](../java/README.md) を読んでください。どちらのトラックも章番号とファイル名が同じなので（違うのは 9 章だけ）、いつでも切り替えられます。

```kotlin
val orders = qb.from(Order::class.java)
    .where { it.status == 1 && it.price!! > minPrice }
    .orderBy { o, by -> by.desc(o.price).asc(o.id) }
    .limit(20)
    .toList()
// HQL: select e0 from Order e0
//      where e0.status = 1 and e0.price > :p1
//      order by e0.price desc nulls last, e0.id asc
```

**SQL ブロックの読み方。** `…` は省略した select リスト（エンティティのすべてのカラム）で、`?` はバインドパラメーターです。各行は [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java) から取っています。このテストは各クエリを Hibernate 6.6.5 で H2 2.3、MariaDB 10.11、Oracle 23.26 に対して実行し、Hibernate の SQL が変わると失敗します。 このテストは Java のラムダを使います。意味が同じ Kotlin のラムダは同じ HQL になるので、SQL も同じです。

**データベースごとの SQL（この HQL に対する Hibernate の SQL。Kotlin も同じ HQL になります）** — マニュアルの `Order` には NULL になりうる `price` がありますが、テストの `Order` にはないので、`SqlByDatabaseTest` はこの HQL を Hibernate に直接渡します。`expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `readme-first`:

```sql
-- H2
-- (this HQL is given to Hibernate directly)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>?
order by o1_0.price desc,o1_0.id
fetch first ? rows only

-- MariaDB
-- (this HQL is given to Hibernate directly)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>?
order by o1_0.price desc,o1_0.id
limit ?

-- Oracle
-- (this HQL is given to Hibernate directly)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>?
order by o1_0.price desc nulls last,o1_0.id
fetch first ? rows only
```

> [!NOTE]
> **例について。** 例は設計上の例です。機能が実装されると、その例はテストスイートから取ったコードに置き換えられ、テストファイルへのリンクが付きます。Kotlin のテストから取った例には **(テスト済み)** と書いてあります（3 章から 7 章）。

<a id="contents"></a>
## 目次

| # | ページ | 学べること | 状況 |
|---|---|---|---|
| 1 | [概要](../01-overview.md)（共通） | lambdaQL とは何か、なぜ作ったか、どう動くか | – |
| 2 | [はじめよう](02-getting-started.md) | 必要な環境、Gradle の設定、Kotlin のエンティティ、最初のクエリ | ソースからのビルドは動作します。`where`、`toList()`、結果の関数 `first()`、`one()`、`count()`、`exists()`、`toSet()`: 実装済み、H2・MariaDB・Oracle でテスト済み（`where` と `toList()` は Kotlin のラムダでも。結果の関数は Java のラムダでのみ） |
| 3 | [条件（`where`）](03-where.md) | 演算子、null のルール、`?.` `?:` `!!`、範囲、`when`、キャプチャした値 | getter から JPA 属性へのマッピング、HQL の生成とクエリの実行（`where`、`toList()`）: 実装済み、H2・MariaDB・Oracle でテスト済み。`?.`、`!!`、キャプチャ値、`in`: Kotlin のラムダでテスト済み。`?:`、範囲、`when`: 設計済み |
| 4 | [ソートとページング](04-order-paging.md) | `orderBy`、`Direction`、`limit`、`offset` | 中核部分は実装済み、H2・MariaDB・Oracle でテスト済み（Kotlin のテスト 1 つ、そのほかは Java のテスト）。`SortSpec` と結合のソート `{ o, c, by -> }` も含みます。プロパティ参照、`SortWhitelist`、`SortSpec` の結合の書き方: 設計済み |
| 5 | [サブクエリ](05-subquery.md) | `exists()`、`x in sub...`、スカラーサブクエリと相関サブクエリ | 実装済み、H2・MariaDB・Oracle でテスト済み（`exists`、`in`、スカラーは Kotlin のラムダでも）。MariaDB の `lateral` の書き換え: まだ実装されていません |
| 6 | [選択、集計、グループ化](06-select-group.md) | `select`、data class、`Pair`、`groupBy`、`having`、`Agg`、結合、派生テーブル、CTE | `select`、`groupBy`、`having`、`Agg`、`distinct()`: 実装済み、H2・MariaDB・Oracle でテスト済み（data class、`Pair`、`Triple`、`groupBy`、`having` は Kotlin のラムダでも）。明示的な結合、派生テーブル、CTE: 実装済み（Java のテスト。Kotlin はまだテストしていません） |
| 7 | [更新、挿入、削除](07-dml.md) | プロパティへの代入、`by.set`、`values`、`all()`、`execute()`、`limit`、`executeOne()` | 実装済み、H2・MariaDB・Oracle でテスト済み。`limit` と `executeOne()` も含みます（プロパティへの代入、`by[column] = value`、insert、delete は Kotlin のラムダでも）。update と delete の `where` の中のサブクエリ（`sub`）と `insert ... select` の集計: 実装済み。`set` の値の中のサブクエリ: 設計済み |
| 7.6 | [Upsert](07-6-upsert.md) | `upsert`、`onConflict { ... }.update { ... }`、`onConflict { ... }.ignore()`、データベースごとの行数 | 実装済み、H2・MariaDB・Oracle でテスト済み（Java のテスト。Kotlin はまだテストしていません） |
| 8 | [プロシージャと DB 関数](08-procedures-functions.md) | `@SqlProcedure`、`companion object` の中の `@SqlFunction`、`qb.call`、`qb.exec` | 実装済み（Java のテスト。Kotlin はまだテストしていません）: DB 関数は H2・MariaDB・Oracle、プロシージャは MariaDB と Oracle。SARGable 警告、プロシージャのパラメーターの型と名前の照合: まだ実装されていません |
| 9 | [Kotlin の注意点](09-kotlin-notes.md) | Kotlin で特別なこと: `?.` `?:` `!!`、`==`、`in`、`when`、演算子、制限 KC1 から KC21 | 一部テスト済み（`?.`、`!!`、`in`、data class、プロパティへの代入）。そのほかの書き方: 設計済み |
| 10 | [ルールと制限](10-rules-and-limits.md) | すべてのルール表を 1 か所に | 一部実装済み |
| 11 | [エラーと言語](../11-errors-and-languages.md)（共通） | エラーコード、例外、メッセージの言語、ログ | 実装済み、ユニットテスト済み。警告 `LQL-5001` と厳格モード: まだ実装されていません |
| 12 | [Spring Data JPA](12-spring-data-jpa.md) | `LambdaQlExecutor<T>`、本体を持つリポジトリのメソッド、DML の短縮メソッド（`deleteAll`、`updateOne` など）、Spring の `Sort` と `Pageable`、モジュール、トランザクション、Kotlin 2.2 / `-Xjvm-default=all` | 実装済み、Spring Boot 3.4.2 で H2・MariaDB・Oracle でテスト済み。DML の短縮メソッドと、更新、削除、挿入、upsert のビルダーも含みます（Java のテスト。Kotlin のリポジトリはまだテストしていません） |

用語の意味:

| 用語 | 意味 |
|---|---|
| 実装済み | コードがリポジトリにあり、ユニットテストがあります。 |
| 設計済み | 構文とルールは決まっています。プロトタイプでテストしました。ライブラリにはまだコードがありません。 |
| 予定 | アイデアはありますが、詳細は決まっていません。 |

<a id="where-to-start"></a>
## どこから始めるか

1. 共通の [概要](../01-overview.md) を読んで、考え方を理解してください。ラムダはコードではなく **クエリ** です。lambdaQL はそのバイトコードを読み、ラムダを実行することはありません。
2. [2. はじめよう](02-getting-started.md) でプロジェクトを設定します。
3. [3. 条件](03-where.md) で条件を学びます。これがいちばん重要な章です。
4. [9. Kotlin の注意点](09-kotlin-notes.md) を一度読んでください。クエリの中では動きが違う Kotlin の書き方（`!!`、`?.`、`?:`、`==`、`===`）を説明しています。

<a id="example-entities"></a>
## 例で使うエンティティ

このトラックのページでは、すべて次の Kotlin のエンティティを使います。コード全体は [2. はじめよう](02-getting-started.md#example-entities) にあります。

| エンティティ | プロパティ |
|---|---|
| `Order` | `id: Long?`、`price: Double?`、`status: Int?`、`active: Boolean`、`description: String?`、`customer: Customer?`、`updateAt: LocalDateTime?`、`items: MutableList<Item>` |
| `Customer` | `id: Long?`、`name: String?`、`grade: Int?` |
| `Item` | `id: Long?`、`order: Order?`、`price: Double?`、`qty: Int` |

ほとんどのプロパティは nullable な `var` プロパティです。データベースのカラムは NULL になることがあるからです。
`active` と `qty` は Kotlin の `Boolean` と `Int` です。これらは JVM のプリミティブなので、NULL になることはありません。

<a id="three-things-to-know-first"></a>
## 最初に知っておく 3 つのこと

| Kotlin | lambdaQL のラムダの中では |
|---|---|
| `it.price!! > 100.0` | `!!` は無視されます。コードをコンパイルするためだけにあります。ラムダは実行されないので、`NullPointerException` は起きません。 |
| `it.customer?.name == "Kim"` | `it.customer?.name` は結合した customer のカラムを指定します。`where` の中ではデフォルトで内部結合の `join` なので、customer のない注文は返されません。null ガード（`it.customer == null \|\| ...`）があると `left join` になります。`it.customer!!.name` も同じカラムを意味します。 |
| `status: Int?` のときの `it.status == 1` | `e0.status = 1`。`status` が NULL の行は一致しません（SQL の意味）。lambdaQL は Kotlin コンパイラーが追加する null チェックを無視します。意味を持つのは **あなたが書いた** null チェックだけです。 |

詳細: [9. Kotlin の注意点](09-kotlin-notes.md)。

<a id="supported-environment"></a>
## 対応環境

| 項目 | バージョン |
|---|---|
| Kotlin | 2.x（設計は kotlinc 1.7.22、2.1.10、2.2.21、2.3.0、2.3.21 で確認） |
| Java（JVM） | 21 |
| Hibernate ORM | 6.6（設計は 6.6.5 で検証） |
| 設計で検証したデータベース | H2 2.3、MariaDB 10.11、Oracle 23.26 (23ai) |
| Spring Boot（任意） | 3.4（3.4.2 でテスト済み、Java のテスト）。Spring Boot 4 はまだ検証していません |

<a id="other-languages"></a>
## 他の言語

どの言語フォルダー（`en`、`ko`、`ja`、`zh`）も同じ構成です。ページを別の言語で読むには、アドレスの `ja` を `en`、`ko`、`zh` のどれかに変えてください。リンクはこのページの上にあります。

<a id="help-and-contributions"></a>
## ヘルプと貢献

- ソースコード: <https://github.com/codebit0/lambdaql>
- 英語のマニュアルが原本です。他の言語は翻訳です。
- 間違いを見つけたら、issue か pull request を作成してください。
- 設計ドキュメント（韓国語、コントリビューター向け）: [ROADMAP.md](../../../design/ROADMAP.md)、[KOTLIN.md](../../../design/KOTLIN.md)
