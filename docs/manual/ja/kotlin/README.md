<a id="lambdaql-manual-kotlin-track"></a>
# lambdaQL マニュアル: Kotlin トラック

**他の言語:** [English](../../en/kotlin/README.md) | [한국어](../../ko/kotlin/README.md) | [日本語](../../ja/kotlin/README.md) | [简体中文](../../zh/kotlin/README.md)

[マニュアルのホーム](../README.md) | [Java トラック](../java/README.md) | 共通ページ: [1. 概要](../01-overview.md) · [11. エラーと言語](../11-errors-and-languages.md) · [13. Spring Boot 3 と 4](../13-spring-boot-versions.md)

これは lambdaQL マニュアルの **Kotlin トラック** です。このトラックのすべてのページには **Kotlin のコードだけ** が載っています。
クエリを Java で書く場合は、[Java トラック](../java/README.md) を読んでください。どちらのトラックも章番号とファイル名が同じなので（違うのは 9 章だけ）、いつでも切り替えられます。

<!-- region: ManualKotlinStartTest.kt#readme -->
```kotlin
val q = qb.from(Order::class.java)
    .where { it.status.toInt() == 1 && it.price > minPrice }
    .orderBy { o -> desc(o.price).asc(o.id) }
    .limit(20)
assertEquals("select e0 from Order e0 where e0.status = 1 and e0.price > :p1"
    + " order by e0.price desc, e0.id asc", q.toHql().hql())
val orders: List<Order> = q.toList()
```

> [!NOTE]
> **例について。** コード例はすべて、実行される Kotlin コードの一部分をそのまま写したものです。節の終わりの「コード例:」の行に、元のファイルと部分の名前を書きます。`assertEquals` の文字列が lambdaQL の作る HQL です。データベースごとの SQL ブロックでは、`…` は省略した select リスト（エンティティのすべてのカラム）で、`?` はバインドパラメーターです。

コード例: [`ManualKotlinStartTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinStartTest.kt)（`readme`）。

<a id="contents"></a>
## 目次

| # | ページ | 学べること |
|---|---|---|
| 1 | [概要](../01-overview.md)（共通） | lambdaQL とは何か、なぜ必要か、どう動くか |
| 2 | [はじめよう](02-getting-started.md) | 要件、Gradle の設定、例のエンティティ、最初のクエリ、結果関数 |
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

1. 共通の [概要](../01-overview.md) を読んで、基本的な考え方を理解してください。ラムダ式はコードではなく **クエリ** です。lambdaQL はラムダ式を呼び出さず、バイトコードの opcode を解析して HQL を作ります。
2. [2. はじめよう](02-getting-started.md) でプロジェクトを設定します。
3. [3. 条件](03-where.md) で条件を学びます。これがいちばん重要な章です。
4. [9. Kotlin の注意点](09-kotlin-notes.md) を一度読んでください。クエリの中では動きが違う Kotlin の書き方（`!!`、`?.`、`?:`、`==`、`===`）を説明しています。

<a id="example-entities"></a>
## 例で使うエンティティ

このトラックの例は、Java クラスで書いた例のエンティティ（`query/src/test/java/org/lambdaql/query/`）を使います。詳しくは [2. はじめよう](02-getting-started.md#example-entities) にあります。

| エンティティ | 主なプロパティ（Java の型） |
|---|---|
| `Order` | `Long id`、`double price`、`short status`、`boolean active`（Kotlin では `isActive`）、`String description`、`Integer cnt`、`String custName`、`LocalDateTime updateAt`、`Customer customer`、`Address address` |
| `Customer` | `int id`、`String name`、`Integer grade` |
| `Item` | `Long id`、`Order order`、`Double price`、`int qty` |

`price`、`status`、`active`、`qty` は Java のプリミティブ型なので、NULL になれません。
参照型のプロパティは NULL になることがあり、Kotlin ではプラットフォーム型として見えます。

<a id="three-things-to-know-first"></a>
## 最初に知っておく 3 つのこと

| Kotlin | lambdaQL のラムダ式の中では |
|---|---|
| `it.price!! > 100.0` | `!!` は無視されます。コードをコンパイルするためだけにあります。lambdaQL はラムダ式を呼び出さずにバイトコードの opcode を解析するので、`NullPointerException` は起きません。 |
| `it.customer?.name == "Kim"` | `it.customer?.name` は結合した顧客のカラムを指します。この条件は顧客のない注文と一致しえないので内部結合の `join` になり、顧客のない注文は返されません。顧客のない注文が条件に一致しうる場合（`it.status == 1 \|\| ...`、`it.customer?.name == null`）は `left join` になります。`it.customer!!.name` も同じカラムで、同じルールに従います。ただし `!!` を通った比較は、顧客のある注文でだけ真になります。 |
| `n: Int?` のときの `it.cnt == n` | `e0.cnt = :p1`。`cnt` が NULL の行は一致しません（SQL の意味）。NULL の行を探すには、`it.cnt == null` のように null の検査を **自分で書いて** ください。 |

詳細: [9. Kotlin の注意点](09-kotlin-notes.md)。

<a id="supported-environment"></a>
## 対応環境

| 項目 | バージョン |
|---|---|
| Kotlin | 2.x（設計は kotlinc 1.7.22、2.1.10、2.2.21、2.3.0、2.3.21 で確認） |
| Java（JVM） | 21 以上 |
| Hibernate ORM | 6.6 または 7.4（6.6.53、7.4.13 でテスト済み） |
| 設計で検証したデータベース | H2 2.3、MariaDB 10.11、Oracle 23.26 (23ai) |
| Spring Boot（任意） | 3.5 または 4.1（3.5.16、4.1.2 でテスト済み）。[13. Spring Boot 3 と 4](../13-spring-boot-versions.md) を参照 |

<a id="other-languages"></a>
## 他の言語

どの言語フォルダー（`en`、`ko`、`ja`、`zh`）も同じ構成です。ページを別の言語で読むには、アドレスの `ja` を `en`、`ko`、`zh` のどれかに変えてください。リンクはこのページの上にあります。

<a id="help-and-contributions"></a>
## ヘルプと貢献

- ソースコード: <https://github.com/lambdaql/lambdaql>
- 韓国語のマニュアルが原本です。他の言語は翻訳です。
- 間違いを見つけたら、issue か pull request を作成してください。
- 設計ドキュメント（韓国語、コントリビューター向け）: [ROADMAP.md](../../../design/ROADMAP.md)、[KOTLIN.md](../../../design/KOTLIN.md)
