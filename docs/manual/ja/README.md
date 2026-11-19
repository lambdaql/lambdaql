<a id="lambdaql-user-manual-english"></a>
# lambdaQL ユーザーマニュアル（日本語）

**他の言語:** [English](../en/README.md) | [한국어](../ko/README.md) | [日本語](../ja/README.md) | [简体中文](../zh/README.md)

lambdaQL は Java と Kotlin のためのクエリライブラリです。
普通のラムダを書くと、lambdaQL がそのラムダのバイトコードを読み、Hibernate 6.6 の HQL クエリを組み立てます。
ラムダが実行されることはありません。

<a id="a-first-look"></a>
## はじめに

### Java

```java
List<Order> orders = qb.from(Order.class)
    .where(o -> o.getStatus() == 1 && o.getPrice() > minPrice)
    .orderBy((o, by) -> by.desc(o.getPrice()).asc(o.getId()))
    .limit(20)
    .toList();
```

### Kotlin

```kotlin
val orders = qb.from(Order::class.java)
    .where { it.status == 1 && it.price!! > minPrice }
    .orderBy { o, by -> by.desc(o.price).asc(o.id) }
    .limit(20)
    .toList()
```

<a id="hql-the-same-for-both"></a>
### HQL（どちらも同じ）

```
select e0 from Order e0
where e0.status = 1 and e0.price > :p1
order by e0.price desc nulls last, e0.id asc
```

**SQL ブロックの読み方。** `…` は省略した select リスト（エンティティのすべてのカラム）で、`?` はバインドパラメーターです。各行は [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java) から取っています。このテストは各クエリを Hibernate 6.6.5 で H2 2.3、MariaDB 10.11、Oracle 23.26 に対して実行し、Hibernate の SQL が変わると失敗します。

**データベースごとの SQL（この HQL に対する Hibernate の SQL）** — マニュアルの `Order` には NULL になりうる `Double price` がありますが、テストの `Order` にはないので、`SqlByDatabaseTest` はこの HQL を Hibernate に直接渡します。`expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `readme-first`:

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

<a id="choose-your-track"></a>
## トラックを選ぶ

このマニュアルには **2 つのトラック** があります。Java 用と Kotlin 用です。
各トラックには、その言語のコードだけが載っています。クエリを書く言語を選んでください。

| トラック | ここから始める | こんな人向け |
|---|---|---|
| **Java** | [Java トラック](java/README.md) | クエリを Java で書く人 |
| **Kotlin** | [Kotlin トラック](kotlin/README.md) | クエリを Kotlin で書く人 |

どちらのトラックも同じエンジンを使います。そのため、同じクエリはどちらの言語でも同じ HQL になります。
2 つのトラックは章番号とファイル名が同じです。違うのは 9 章だけです（Java の注意点、または Kotlin の注意点）。
トラックのどのページにも、もう一方のトラックの同じページへのリンクがあります。いつでも切り替えられます。

<a id="common-pages"></a>
## 共通ページ

次のページは両方の言語向けです。コードは Java と Kotlin の別々のセクションに分けて載せています。

| ページ | 内容 |
|---|---|
| [1. 概要](01-overview.md) | lambdaQL とは何か、なぜ作ったか、どう動くか、設計方針 |
| [11. エラーと言語](11-errors-and-languages.md) | エラーコード、例外、メッセージの言語、ログ |

<a id="table-of-contents"></a>
## 目次

| # | 章 | Java | Kotlin |
|---|---|---|---|
| 1 | 概要（共通） | [01-overview.md](01-overview.md) | [01-overview.md](01-overview.md) |
| 2 | はじめよう: 必要な環境、ソースからのビルド、最初のクエリ | [Java](java/02-getting-started.md) | [Kotlin](kotlin/02-getting-started.md) |
| 3 | 条件（`where`）: 演算子、null のルール、キャプチャした値 | [Java](java/03-where.md) | [Kotlin](kotlin/03-where.md) |
| 4 | ソートとページング: `orderBy`、`Direction`、`limit`、`offset` | [Java](java/04-order-paging.md) | [Kotlin](kotlin/04-order-paging.md) |
| 5 | サブクエリ: `exists`、`contains`、スカラーサブクエリと相関サブクエリ | [Java](java/05-subquery.md) | [Kotlin](kotlin/05-subquery.md) |
| 6 | 選択、集計、グループ化: `select`、`groupBy`、`having`、`Agg`、結合（`join`、`leftJoin`、`crossJoin`）、派生テーブルと CTE（`with`） | [Java](java/06-select-group.md) | [Kotlin](kotlin/06-select-group.md) |
| 7 | 更新、挿入、削除: `set`、`values`、`all()`、`execute()`、`limit`、`executeOne()` | [Java](java/07-dml.md) | [Kotlin](kotlin/07-dml.md) |
| 7.6 | Upsert: `upsert`、`onConflict(...).update(...)`、`onConflict(...).ignore()` | [Java](java/07-6-upsert.md) | [Kotlin](kotlin/07-6-upsert.md) |
| 8 | プロシージャと DB 関数: `@SqlProcedure`、`@SqlFunction`、`qb.call`、`qb.exec` | [Java](java/08-procedures-functions.md) | [Kotlin](kotlin/08-procedures-functions.md) |
| 9 | 言語ごとの注意点 | [Java の注意点](java/09-java-notes.md) | [Kotlin の注意点](kotlin/09-kotlin-notes.md) |
| 10 | ルールと制限: ルール表の全体 | [Java](java/10-rules-and-limits.md) | [Kotlin](kotlin/10-rules-and-limits.md) |
| 11 | エラーと言語（共通） | [11-errors-and-languages.md](11-errors-and-languages.md) | [11-errors-and-languages.md](11-errors-and-languages.md) |
| 12 | Spring Data JPA: `LambdaQlExecutor<T>`、DML の短縮メソッド、Spring の `Sort` と `Pageable`、モジュール、トランザクション、`QueryBuilder` の Bean | [Java](java/12-spring-data-jpa.md) | [Kotlin](kotlin/12-spring-data-jpa.md) |

<a id="status"></a>
## 状況

| 機能 | 状況 | Java | Kotlin |
|---|---|---|---|
| ラムダの読み込み（ラムダ本体を見つけ、そのバイトコードを読む） | 実装済み、ユニットテストあり | [01](01-overview.md#how-it-works-5-steps) | [01](01-overview.md#how-it-works-5-steps) |
| 条件のデコード（`&&`、`\|\|`、`!`、比較、キャプチャした値） | 実装済み、ユニットテストあり（Java のラムダ） | [03](java/03-where.md) | [03](kotlin/03-where.md) |
| getter から JPA 属性へのマッピング、キャプチャした値の評価 | 実装済み、H2・MariaDB・Oracle でテスト済み | [03](java/03-where.md) | [03](kotlin/03-where.md) |
| HQL の生成とクエリの実行（`where`、`toList()`） | 実装済み、H2・MariaDB・Oracle でテスト済み | [03](java/03-where.md) | [03](kotlin/03-where.md) |
| 結果の関数（`first()`、`one()`、`count()`、`exists()`、`toSet()`） | 実装済み、H2・MariaDB・Oracle でテスト済み（Java のラムダ） | [02](java/02-getting-started.md#getting-the-result) | [02](kotlin/02-getting-started.md#getting-the-result) |
| ソートとページング（`orderBy`、`limit`、`offset`） | 中核部分は実装済み、H2・MariaDB・Oracle でテスト済み。`SortSpec`、結合のソート `(o, c, by)`、外部のソートのためのソートポリシー（コード、Spring Boot のプロパティ、`@Sortable` によるルール）も含みます。`SortSpec` の結合の書き方: 設計済み | [04](java/04-order-paging.md) | [04](kotlin/04-order-paging.md) |
| サブクエリ（`exists`、`contains`、スカラー、相関） | 実装済み、H2・MariaDB・Oracle でテスト済み（Java のラムダ）。MariaDB の `lateral` の書き換え: まだ実装されていない | [05](java/05-subquery.md) | [05](kotlin/05-subquery.md) |
| 射影、`groupBy`、`having`、集計（`Agg`） | 実装済み、H2・MariaDB・Oracle でテスト済み（Java のラムダ）。`distinct()` と結果の関数も含みます。集約によるソート: v0.2 の候補 | [06](java/06-select-group.md) | [06](kotlin/06-select-group.md) |
| 明示的な結合（`join`、`leftJoin`、`crossJoin`、エンティティ 3 つまで） | 実装済み、H2・MariaDB・Oracle でテスト済み（Java のラムダ）。すべてのエンティティに対する `where`、`orderBy`、`select`、ページングを含みます。DML とサブクエリの中の結合: 今後 | [06](java/06-select-group.md#68-explicit-joins) | [06](kotlin/06-select-group.md#68-explicit-joins) |
| 派生テーブルと CTE（`qb.from(valueQuery)`、`qb.with(valueQuery)`） | 実装済み、H2・MariaDB・Oracle でテスト済み（Java のラムダ）。派生テーブルとの結合と、同じ CTE の 2 回の使用: 今後 | [06](java/06-select-group.md#69-derived-table-subquery-in-from) | [06](kotlin/06-select-group.md#69-derived-table-subquery-in-from) |
| `update`、`insert`、`delete`、upsert | 実装済み、H2・MariaDB・Oracle でテスト済み（Java のラムダ）。`limit` と `executeOne()` も含みます。更新と削除の `where` の中のサブクエリ（`sub`）と `insert ... select` の集計: 実装済み。`set` の値の中のサブクエリ: 設計済み | [07](java/07-dml.md) | [07](kotlin/07-dml.md) |
| ストアドプロシージャと DB 関数 | 実装済み: クエリの中の DB 関数と単独呼び出しは H2・MariaDB・Oracle でテスト済み。プロシージャ（`call`、`exec`、OUT、INOUT、結果セット）は MariaDB と Oracle でテスト済み（H2 には OUT パラメーターがありません）。引数名の照合（MariaDB と Oracle でテスト済み）と Oracle パッケージのオーバーロードの検出（Oracle でテスト済み）: 実装済み。DB 関数の SARGable 警告（`LQL-5001`、`LQL-5007`）: 実装済み。プロシージャのパラメーターの型の照合: まだ実装されていません | [08](java/08-procedures-functions.md) | [08](kotlin/08-procedures-functions.md) |
| Kotlin の書き方（`?.`、`?:`、`!!`、範囲、`when`、`in`） | `?.`、`!!`、キャプチャしたコレクションに対する `in`、data class、`Pair`/`Triple`、プロパティへの代入: H2・MariaDB・Oracle で Kotlin のラムダを使ってテスト済み。`?:`、範囲、`when`、プロパティ参照: 設計済み | – | [09](kotlin/09-kotlin-notes.md) |
| 4 言語のエラーコードとメッセージ | 実装済み、ユニットテスト済み: `[LQL-xxxx]` コード、`LambdaQLException` とそのサブクラス、英語・日本語・中国語・韓国語のメッセージ、`lambdaql.locale`。厳格モード: まだ実装されていません | [11](11-errors-and-languages.md) | [11](11-errors-and-languages.md) |
| Spring Data JPA（`LambdaQlExecutor<T>`、Spring の `Sort` / `Pageable`、Spring Boot の自動構成） | 実装済み、Spring Boot 3.4.2 で H2・MariaDB・Oracle でテスト済み（Java）。DML の短縮メソッド（`deleteAll`、`updateOne` など）と、更新、削除、挿入、upsert のビルダーも含みます。Spring Boot 4: まだ検証していません | [12](java/12-spring-data-jpa.md) | [12](kotlin/12-spring-data-jpa.md) |

用語の意味:

| 用語 | 意味 |
|---|---|
| 実装済み | コードがリポジトリにあり、ユニットテストがあります。 |
| 作業中 | 一部のコードはありますが、まだ完成していません。 |
| 設計済み | 構文とルールは決まっています。プロトタイプでテストしました。ライブラリにはまだコードがありません。 |
| 予定 | アイデアはありますが、詳細は決まっていません。 |

テストは次のとおりです。`SelectQueryTest`、`SortQueryTest`、`SubqueryTest`、`ProjectionTest`、`JoinQueryTest`、`DerivedQueryTest`、`DmlQueryTest`、`FunctionTest`、`ProcedureTest`、`KotlinSelectTest`、`KotlinQueryTest`、`LqlMessagesTest`、`ExecutorTest`、`AutoConfigurationTest` はユーザー向け API を使い、その他のテストは内部クラスをチェックします。

| テスト | 確認すること |
|---|---|
| [SelectQueryTest.java](../../../query/src/test/java/org/lambdaql/query/SelectQueryTest.java) | `qb.from(...).where(...)`: H2、MariaDB、Oracle での HQL、パラメーター、行 |
| [SortQueryTest.java](../../../query/src/test/java/org/lambdaql/query/SortQueryTest.java) | `orderBy`、`limit`、`offset`: H2、MariaDB、Oracle での HQL と行の順序 |
| [SubqueryTest.java](../../../query/src/test/java/org/lambdaql/query/SubqueryTest.java) | `where` の中のサブクエリ: `exists`、`contains`（IN）、NOT EXISTS、スカラー、top-1、入れ子、サブクエリ付きの削除。H2、MariaDB、Oracle での HQL と行 |
| [ProjectionTest.java](../../../query/src/test/java/org/lambdaql/query/ProjectionTest.java) | `select`、record、`groupBy`、`having`、`Agg`、`distinct`、ページング、`groupBy` 付きの `insert ... select`。H2、MariaDB、Oracle での HQL と値 |
| [JoinQueryTest.java](../../../query/src/test/java/org/lambdaql/query/JoinQueryTest.java) | エンティティ 3 つまでの `join`、`leftJoin`、`crossJoin`: 条件、ソート、`select`、繰り返される行、`count`、すべてのエンティティに対するページング（H2、MariaDB、Oracle） |
| [DerivedQueryTest.java](../../../query/src/test/java/org/lambdaql/query/DerivedQueryTest.java) | 派生テーブルと CTE（`qb.from(valueQuery)`、`qb.with(valueQuery)`）: H2、MariaDB、Oracle での HQL と行、内側の `limit`、行の型のルール J5 |
| [DmlQueryTest.java](../../../query/src/test/java/org/lambdaql/query/DmlQueryTest.java) | `update`、`delete`、`insert`、upsert: H2、MariaDB、Oracle での HQL と実行後の行。同じ条件の選択と比べます |
| [FunctionTest.java](../../../query/src/test/java/org/lambdaql/query/FunctionTest.java) | `where`、`select`、`orderBy`、update の `set` の中の `@SqlFunction` の DB 関数、Boolean の関数、登録した関数、ルールのチェック（H2、MariaDB、Oracle） |
| [ProcedureTest.java](../../../query/src/test/java/org/lambdaql/query/ProcedureTest.java) | `qb.call` と `qb.exec` による `@SqlProcedure` の呼び出し（OUT、複数の OUT、INOUT、結果セットと REF CURSOR）とスタブの照合（MariaDB と Oracle）。DB 関数の単独呼び出し（H2、MariaDB、Oracle） |
| [KotlinSelectTest.kt](../../../query/src/test/kotlin/org/lambdaql/query/KotlinSelectTest.kt) | `where` の中の Kotlin のラムダ: `?.` のパス、キャプチャ値、キャプチャしたコレクションに対する `in` |
| [KotlinQueryTest.kt](../../../query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt) | Kotlin のラムダ: ソート、サブクエリ、data class・`Pair`・`Triple` の結果、`groupBy` と `having`、update（プロパティへの代入と `by[column] = value`）、insert、delete |
| [LqlMessagesTest.java](../../../query/src/test/java/org/lambdaql/i18n/LqlMessagesTest.java) | メッセージのコードと 4 つのメッセージ言語: すべてのバンドルで同じキーと引数、コードの重複なし、ロケールの選び方、コードの範囲による例外クラス |
| [ExecutorTest.java](../../../spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java) | Spring Boot アプリケーションの中の `LambdaQlExecutor<T>`: リポジトリのメソッド、Spring の `Sort` と `Pageable`、トランザクション |
| [AutoConfigurationTest.java](../../../spring-data-jpa/src/test/java/org/lambdaql/spring/AutoConfigurationTest.java) | Spring Boot の自動構成が作る `QueryBuilder` の Bean |
| [LoadExamplesTest.java](../../../query/src/test/java/org/lambdaql/engine/load/LoadExamplesTest.java) | ラムダ本体を見つける、キャプチャした値を読む、シリアライズ可能でないラムダを拒否する |
| [ConditionDecodeTest.java](../../../query/src/test/java/org/lambdaql/engine/decode/ConditionDecodeTest.java) | バイトコードから `&&`、`\|\|`、`!` と比較を組み立て直す、値の三項演算子を拒否する |
| [IrExamplesTest.java](../../../query/src/test/java/org/lambdaql/engine/ir/IrExamplesTest.java) | 内部の条件ツリー（比較演算子、AND、OR、NOT） |
| [NegationMinimizerTest.java](../../../query/src/test/java/org/lambdaql/engine/decode/NegationMinimizerTest.java) | 結果を変えずに `!` を比較の中へ押し込む（ド・モルガン） |
| [HostEvaluatorTest.java](../../../query/src/test/java/org/lambdaql/engine/sem/HostEvaluatorTest.java) | バインドの前に、キャプチャした変数から Java 側で値を計算する |
| [JpaEntityModelTest.java](../../../query/src/test/java/org/lambdaql/engine/sem/JpaEntityModelTest.java) | JPA Metamodel からエンティティのプロパティを読む（getter マッピングの最初のコード） |
| [AssignmentReaderTest.java](../../../query/src/test/java/org/lambdaql/engine/sem/AssignmentReaderTest.java) | 代入のラムダ（setter と `by.set`）をカラムと値の組に読み、ルール違反（U1、U3、U6）を拒否する |

<a id="about-the-examples"></a>
## 例について

例は設計上の例です。機能が実装されると、その例はテストスイートから取ったコードに置き換えられ、テストファイルへのリンクが付きます。3. 条件、4. ソートとページング、5. サブクエリ、6. 選択、集計、グループ化、7. 更新、挿入、削除、8. プロシージャと DB 関数、11. エラーと言語、12. Spring Data JPA（Java）では、それらの例に **(テスト済み)** と書いてあります。Kotlin トラックも、Kotlin のテストから取った例に同じように書いています。

<a id="example-entities"></a>
## 例で使うエンティティ

このマニュアルの例では、すべて次の 3 つのエンティティを使います。

| エンティティ | フィールド |
|---|---|
| `Order` | `id`、`price`、`status`、`active`、`description`、`customer`（`Customer` へ）、`updateAt`、`items`（複数の `Item` へ） |
| `Customer` | `id`、`name`、`grade` |
| `Item` | `order`（`Order` へ）、`price`、`qty` |

エンティティのコード全体は「はじめよう」にあります: [Java](java/02-getting-started.md#example-entities) | [Kotlin](kotlin/02-getting-started.md#example-entities)。

<a id="supported-environment"></a>
## 対応環境

| 項目 | バージョン |
|---|---|
| Java | 21 |
| Kotlin | 2.x |
| Hibernate ORM | 6.6（設計は 6.6.5 で検証） |
| 検証したデータベース | H2 2.3、MariaDB 10.11、Oracle 23.26 (23ai) |
| Spring Boot（任意） | 3.4（3.4.2 でテスト済み）。Spring Boot 4 はまだ検証していません |

<a id="folder-layout"></a>
## フォルダー構成

```
manual/ja/
├── README.md                      このページ
├── 01-overview.md                 共通
├── 11-errors-and-languages.md     共通
├── java/                          Java トラック（Java のコードだけ）
│   ├── README.md
│   ├── 02-getting-started.md ... 08-procedures-functions.md
│   ├── 09-java-notes.md
│   └── 10-rules-and-limits.md
└── kotlin/                        Kotlin トラック（Kotlin のコードだけ）
    ├── README.md
    ├── 02-getting-started.md ... 08-procedures-functions.md
    ├── 09-kotlin-notes.md
    └── 10-rules-and-limits.md
```

どの言語フォルダー（`en`、`ko`、`ja`、`zh`）も同じファイル名です。言語を切り替えるには、リンクのフォルダー名を変えてください。

<a id="help-and-contributions"></a>
## ヘルプと貢献

- ソースコード: <https://github.com/codebit0/lambdaql>
- 英語のマニュアルが原本です。韓国語、日本語、中国語のマニュアルは翻訳です。
- どの言語でも、間違いを見つけたら issue か pull request を作成してください。翻訳の手伝いも歓迎します。
- 設計ドキュメント（韓国語、コントリビューター向け）: [ROADMAP.md](../../design/ROADMAP.md)、[KOTLIN.md](../../design/KOTLIN.md)
