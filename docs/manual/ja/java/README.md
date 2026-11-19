<a id="lambdaql-manual-java-track"></a>
# lambdaQL マニュアル: Java トラック

**他の言語:** [English](../../en/java/README.md) | [한국어](../../ko/java/README.md) | [日本語](../../ja/java/README.md) | [简体中文](../../zh/java/README.md)

[マニュアルのホーム](../README.md) | [Kotlin トラック](../kotlin/README.md) | 共通ページ: [1. 概要](../01-overview.md) · [11. エラーと言語](../11-errors-and-languages.md)

これは lambdaQL マニュアルの **Java トラック** です。このトラックのページには **Java のコードだけ** が載っています。
クエリを Kotlin で書く場合は、[Kotlin トラック](../kotlin/README.md) を読んでください。同じ番号の同じ章があります。

lambdaQL は Java と Kotlin のためのクエリライブラリです。
普通のラムダを書くと、lambdaQL がそのラムダのバイトコードを読み、Hibernate 7 の HQL クエリを組み立てます。
ラムダが実行されることはありません。

```java
List<Order> orders = qb.from(Order.class)
    .where(o -> o.getStatus() == 1 && o.getPrice() > minPrice)
    .orderBy((o, by) -> by.desc(o.getPrice()).asc(o.getId()))
    .limit(20)
    .toList();
// HQL: select e0 from Order e0
//      where e0.status = 1 and e0.price > :p1
//      order by e0.price desc nulls last, e0.id asc
```

**SQL ブロックの読み方。** `…` は省略した select リスト（エンティティのすべてのカラム）で、`?` はバインドパラメーターです。各行は [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java) から取っています。このテストは各クエリを Hibernate 7.4 で H2 2.3、MariaDB 10.11、Oracle 23.26 に対して実行し、Hibernate の SQL が変わると失敗します。

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

> [!NOTE]
> **例について。**
> 例は設計上の例です。機能が実装されると、その例はテストスイートから取ったコードに置き換えられ、テストファイルへのリンクが付きます。[3. 条件](03-where.md)、[4. ソートとページング](04-order-paging.md)、[5. サブクエリ](05-subquery.md)、[6. 選択、集計、グループ化](06-select-group.md)、[7. 更新、挿入、削除](07-dml.md)、[8. プロシージャと DB 関数](08-procedures-functions.md)、[11. エラーと言語](../11-errors-and-languages.md)、[12. Spring Data JPA](12-spring-data-jpa.md) では、それらの例に **(テスト済み)** と書いてあります。

<a id="contents"></a>
## 目次

「共通」と書いたページは、Java トラックと Kotlin トラックで共有しています。両方の言語の小さな例が、別々のセクションに載っています。

| # | ページ | 学べること |
|---|---|---|
| 1 | [概要](../01-overview.md)（共通） | lambdaQL とは何か、なぜ作ったか、どう動くか |
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

<a id="example-entities"></a>
## 例で使うエンティティ

例では、すべて次の 3 つのエンティティを使います。コード全体は [はじめよう](02-getting-started.md#example-entities) にあります。

| エンティティ | フィールド |
|---|---|
| `Order` | `id`、`price`、`status`、`active`、`description`、`customer`（`Customer` へ）、`updateAt`、`items`（複数の `Item` へ） |
| `Customer` | `id`、`name`、`grade` |
| `Item` | `order`（`Order` へ）、`price`、`qty` |

<a id="supported-environment"></a>
## 対応環境

| 項目 | バージョン |
|---|---|
| Java | 21 |
| コンパイラー | javac（Eclipse のコンパイラーである ECJ は保証しません） |
| Hibernate ORM | 7.4（Spring Boot 4.1.2 の 7.4.13 でテスト済み） |
| 検証したデータベース | H2 2.3、MariaDB 10.11、Oracle 23.26 (23ai) |
| Spring Boot（任意） | 4.1（4.1.2 でテスト済み）。Spring Boot 3 はこの版では検証していません |

<a id="help-and-contributions"></a>
## ヘルプと貢献

- ソースコード: <https://github.com/codebit0/lambdaql>
- 韓国語のマニュアルが原本です。英語、日本語、中国語のマニュアルは翻訳です。
- 間違いを見つけたら、issue か pull request を作成してください。
- 設計ドキュメント（韓国語、コントリビューター向け）: [ROADMAP.md](../../../design/ROADMAP.md)、[KOTLIN.md](../../../design/KOTLIN.md)
