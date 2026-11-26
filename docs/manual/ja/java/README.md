<a id="lambdaql-manual-java-track"></a>
# lambdaQL マニュアル: Java トラック

**他の言語:** [English](../../en/java/README.md) | [한국어](../../ko/java/README.md) | [日本語](../../ja/java/README.md) | [简体中文](../../zh/java/README.md)

[マニュアルのホーム](../README.md) | [Kotlin トラック](../kotlin/README.md) | 共通ページ: [1. 概要](../01-overview.md) · [11. エラーと言語](../11-errors-and-languages.md) · [13. Spring Boot 3 と 4](../13-spring-boot-versions.md)

lambdaQL マニュアルの **Java トラック** です。このトラックのすべてのページには **Java のコードだけ** があります。
クエリを Kotlin で書く場合は [Kotlin トラック](../kotlin/README.md) を読んでください。同じ番号の同じ章で構成されています。

lambdaQL は JPA と Hibernate の上で動作する、Java・Kotlin 用のクエリライブラリです。
クエリの条件とソートをラムダ式で書くと、lambdaQL がラムダ式のバイトコードを解析して HQL クエリを作ります。

<!-- region: ManualJavaStartTest.java#readme -->
```java
import static org.lambdaql.query.By.desc;

double minPrice = 100;
OrderedSelectQuery<Order> query = qb.from(Order.class)
        .where(o -> o.getStatus() == 1 && o.getPrice() > minPrice)
        .orderBy(o -> desc(o.getPrice()).asc(o.getId()))
        .limit(20);
assertEquals("select e0 from Order e0 where e0.status = 1 and e0.price > :p1"
        + " order by e0.price desc, e0.id asc", query.toHql().hql());
List<Order> orders = query.toList();
```

`toHql()` はクエリを実行せずに HQL を返します。`assertEquals` の文字列が、lambdaQL が作る HQL です。
`price` と `id` は NULL になれないカラムなので、ソートに `nulls` の部分がありません。
`desc` と `asc` はクラス `By` のソート関数です（[4.1](04-order-paging.md#41-one-lambda-for-the-whole-sort) を参照）。

> [!NOTE]
> **例について。**
> コード例はすべて、リポジトリの実行可能な Java コードからそのまま写したものです。
> 各節の末尾にある「コード例:」の行に、元のファイルへのリンクを載せます。
> データベースごとの SQL ブロックでは、`…` は省略した select リスト（エンティティのすべてのカラム）で、`?` はバインドパラメーターです。

コード例: [`ManualJavaStartTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaStartTest.java)（`readme`）。

<a id="contents"></a>
## 目次

「共通」と書いたページは、Java トラックと Kotlin トラックで共有します。両方の言語の小さな例が、セクションごとに分かれています。

| # | ページ | 学べること |
|---|---|---|
| 1 | [概要](../01-overview.md)（共通） | lambdaQL とは何か、なぜ必要か、どう動くか |
| 2 | [はじめよう](02-getting-started.md) | 必要な環境、ソースからのビルド、エンティティ、`QueryBuilder`、最初のクエリ |
| 3 | [条件（`where`）](03-where.md) | 演算子、NULL のルール、キャプチャした値、関連のパス |
| 4 | [ソートとページング](04-order-paging.md) | `orderBy`、`Direction`、NULL の順序、`limit`、`offset` |
| 5 | [サブクエリ](05-subquery.md) | `exists`、`contains`（IN）、NOT EXISTS、スカラーサブクエリと相関サブクエリ |
| 6 | [選択、集計、グループ化](06-select-group.md) | `select`、record と DTO、`Agg`、`groupBy`、`having`、結合（`join`、`leftJoin`、`crossJoin`）、派生テーブル、CTE |
| 7 | [更新、挿入、削除](07-dml.md) | `set`、`Dml.value`、`values`、`all()`、`execute()`、`limit`、`executeOne()` |
| 7.6 | [Upsert](07-6-upsert.md) | `upsert`、`onConflict(...).update(...)`、`onConflict(...).ignore()`、データベースごとの行数 |
| 8 | [プロシージャと DB 関数](08-procedures-functions.md) | `@SqlProcedure`、`@SqlFunction`、`qb.call`、`qb.exec` |
| 9 | [Java の注意点](09-java-notes.md) | ボクシング、オブジェクトの `==`、`String` の比較、`Objects.equals`、メソッド参照、`this` のキャプチャ、コンパイラーの注意 |
| 10 | [ルールと制限](10-rules-and-limits.md) | すべてのルール表、Java と SQL の違い、データベースごとの違い、サポートしないもの |
| 11 | [エラーと言語](../11-errors-and-languages.md)（共通） | エラーコード、例外、メッセージの言語、ログ |
| 12 | [Spring Data JPA](12-spring-data-jpa.md) | `LambdaQlExecutor<T>`、リポジトリの default メソッド、DML の短縮メソッド（`deleteAll`、`updateOne` など）、Spring の `Sort` と `Pageable`、モジュール、トランザクション、`QueryBuilder` の Bean |
| 13 | [Spring Boot 3 と 4](../13-spring-boot-versions.md)（共通） | バージョン表、アーティファクト（共通の jar と `-boot3`・`-boot4` のスターター）、Hibernate 6.6 と 7.4 の SQL の違い、検証の範囲 |

<a id="example-entities"></a>
## 例で使うエンティティ

例では主に、次の 3 つのエンティティを使います。プロパティの型と元のファイルは [はじめよう](02-getting-started.md#example-entities) にあります。

| エンティティ | フィールド |
|---|---|
| `Order` | `id`、`price`、`status`、`active`、`description`、`kind`、`customer`（`Customer` へ）、`updateAt`、`total`、`cnt`、`custName`、`address` |
| `Customer` | `id`、`name`、`grade`、`orders`（複数の `Order` へ） |
| `Item` | `id`、`order`（`Order` へ）、`price`、`qty` |

<a id="supported-environment"></a>
## 対応環境

| 項目 | バージョン |
|---|---|
| Java | 21 以上 |
| コンパイラー | javac（Eclipse のコンパイラーである ECJ は保証しません） |
| Hibernate ORM | 6.6 または 7.4（6.6.53、7.4.13 でテスト済み） |
| 検証したデータベース | H2 2.3、MariaDB 10.11、MySQL 8.4、PostgreSQL 17、Oracle 23.26 (23ai) |
| Spring Boot（任意） | 3.5 または 4.1（3.5.16、4.1.2 でテスト済み）。[13. Spring Boot 3 と 4](../13-spring-boot-versions.md) を参照 |

<a id="help-and-contributions"></a>
## ヘルプと貢献

- ソースコード: <https://github.com/lambdaql/lambdaql>
- 韓国語のマニュアルが原本です。英語、日本語、中国語のマニュアルは翻訳です。
- 間違いを見つけたら、issue か pull request を作成してください。
- 設計ドキュメント（韓国語、コントリビューター向け）: [ROADMAP.md](../../../design/ROADMAP.md)、[KOTLIN.md](../../../design/KOTLIN.md)
