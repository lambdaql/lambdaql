<a id="lambdaql-manual-kotlin-track"></a>
# lambdaQL マニュアル: Kotlin トラック

**他の言語:** [English](../../en/kotlin/README.md) | [한국어](../../ko/kotlin/README.md) | [日本語](../../ja/kotlin/README.md) | [简体中文](../../zh/kotlin/README.md)

[マニュアルのホーム](../README.md) | [Java トラック](../java/README.md) | 共通ページ: [1. 概要](../01-overview.md) · [11. エラーと言語](../11-errors-and-languages.md) · [13. Spring Boot 3 と 4](../13-spring-boot-versions.md)

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

**SQL ブロックの読み方。** `…` は省略した select リスト（エンティティのすべてのカラム）で、`?` はバインドパラメーターです。各行は [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java) から取っています。このテストは各クエリを Hibernate 7.4 で H2 2.3、MariaDB 10.11、Oracle 23.26 に対して実行し、Hibernate の SQL が変わると失敗します。 このテストは Java のラムダを使います。意味が同じ Kotlin のラムダは同じ HQL になるので、SQL も同じです。

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
> **例について。** コード例はすべて、Kotlin のテストコードの一部分をそのまま写したものです。コードの上にテストファイルと部分の名前（領域 `name`）を書きます。テストは lambdaQL が作った HQL と、返した行を確認します。

<a id="contents"></a>
## 目次

| # | ページ | 学べること |
|---|---|---|
| 1 | [概要](../01-overview.md)（共通） | lambdaQL とは何か、なぜ作ったか、どう動くか |
| 2 | [はじめよう](02-getting-started.md) | 必要な環境、Gradle の設定、Kotlin のエンティティ、最初のクエリ |
| 3 | [条件（`where`）](03-where.md) | 演算子、null のルール、`?.` `?:` `!!`、範囲、`when`、キャプチャした値 |
| 4 | [ソートとページング](04-order-paging.md) | `orderBy`、`Direction`、`limit`、`offset` |
| 5 | [サブクエリ](05-subquery.md) | `exists()`、`x in sub...`、スカラーサブクエリと相関サブクエリ、サブクエリの中の結合 |
| 6 | [選択、集計、グループ化](06-select-group.md) | `select`、data class、`Pair`、`groupBy`、`having`、`Agg`、結合、派生テーブル、CTE |
| 7 | [更新、挿入、削除](07-dml.md) | プロパティへの代入、`Dml.value`、`values`、`all()`、`execute()`、`limit`、`executeOne()` |
| 7.6 | [Upsert](07-6-upsert.md) | `upsert`、`onConflict { ... }.update { ... }`、`onConflict { ... }.ignore()`、データベースごとの行数 |
| 8 | [プロシージャと DB 関数](08-procedures-functions.md) | `@SqlProcedure`、`companion object` の中の `@SqlFunction`、`qb.call`、`qb.exec` |
| 9 | [Kotlin の注意点](09-kotlin-notes.md) | Kotlin で特別なこと: `?.` `?:` `!!`、`==`、`in`、`when`、演算子、制限 KC1 から KC21 |
| 10 | [ルールと制限](10-rules-and-limits.md) | すべてのルール表を 1 か所に |
| 11 | [エラーと言語](../11-errors-and-languages.md)（共通） | エラーコード、例外、メッセージの言語、ログ |
| 12 | [Spring Data JPA](12-spring-data-jpa.md) | `LambdaQlExecutor<T>`、本体を持つリポジトリのメソッド、DML の短縮メソッド（`deleteAll`、`updateOne` など）、Spring の `Sort` と `Pageable`、モジュール、トランザクション、Kotlin 2.2 / `-Xjvm-default=all` |
| 13 | [Spring Boot 3 と 4](../13-spring-boot-versions.md)（共通） | バージョン表、アーティファクト（共通の jar と `-boot3`・`-boot4` のスターター）、Hibernate 6.6 と 7.4 の SQL の違い、検証の範囲 |

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
| `it.customer?.name == "Kim"` | `it.customer?.name` は結合した customer のカラムを指定します。この条件は customer のない注文と一致しえないので内部結合の `join` になり、customer のない注文は返されません。`?.` は、customer のない注文が条件を真にできるとき（`it.status == 1 \|\| ...`、`it.customer?.name == null`）に `left join` になります（D54）。`it.customer!!.name` も同じカラムですが、常に内部結合です。 |
| `status: Int?` のときの `it.status == 1` | `e0.status = 1`。`status` が NULL の行は一致しません（SQL の意味）。lambdaQL は Kotlin コンパイラーが追加する null チェックを無視します。意味を持つのは **自分で書いた** null チェックだけです。 |

詳細: [9. Kotlin の注意点](09-kotlin-notes.md)。

<a id="supported-environment"></a>
## 対応環境

| 項目 | バージョン |
|---|---|
| Kotlin | 2.x（設計は kotlinc 1.7.22、2.1.10、2.2.21、2.3.0、2.3.21 で確認） |
| Java（JVM） | 21 |
| Hibernate ORM | 6.6 または 7.4（6.6.53、7.4.13 でテスト済み） |
| 設計で検証したデータベース | H2 2.3、MariaDB 10.11、Oracle 23.26 (23ai) |
| Spring Boot（任意） | 3.5 または 4.1（3.5.16、4.1.2 でテスト済み、Java のテスト）。[13. Spring Boot 3 と 4](../13-spring-boot-versions.md) を参照 |

<a id="other-languages"></a>
## 他の言語

どの言語フォルダー（`en`、`ko`、`ja`、`zh`）も同じ構成です。ページを別の言語で読むには、アドレスの `ja` を `en`、`ko`、`zh` のどれかに変えてください。リンクはこのページの上にあります。

<a id="help-and-contributions"></a>
## ヘルプと貢献

- ソースコード: <https://github.com/codebit0/lambdaql>
- 韓国語のマニュアルが原本です。他の言語は翻訳です。
- 間違いを見つけたら、issue か pull request を作成してください。
- 設計ドキュメント（韓国語、コントリビューター向け）: [ROADMAP.md](../../../design/ROADMAP.md)、[KOTLIN.md](../../../design/KOTLIN.md)
