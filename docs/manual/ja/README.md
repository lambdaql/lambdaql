<a id="lambdaql-user-manual-english"></a>
# lambdaQL ユーザーマニュアル（日本語）

> [!IMPORTANT]
> このページは韓国語版のマニュアルより古い内容です。最新のルールは[韓国語のページ](../ko/README.md)を参照してください。

**他の言語:** [English](../en/README.md) | [한국어](../ko/README.md) | [日本語](../ja/README.md) | [简体中文](../zh/README.md)

lambdaQL は Java と Kotlin のためのクエリライブラリです。
普通のラムダを書くと、lambdaQL がそのラムダのバイトコードを読み、Hibernate 7 の HQL クエリを組み立てます。
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
| Hibernate ORM | 7.4（Spring Boot 4.1.2 の 7.4.13 でテスト済み） |
| 検証したデータベース | H2 2.3、MariaDB 10.11、Oracle 23.26 (23ai) |
| Spring Boot（任意） | 4.1（4.1.2 でテスト済み）。Spring Boot 3 はこの版では検証していません |

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
- 韓国語のマニュアルが原本です。英語、日本語、中国語のマニュアルは翻訳です。
- どの言語でも、間違いを見つけたら issue か pull request を作成してください。翻訳の手伝いも歓迎します。
- 設計ドキュメント（韓国語、コントリビューター向け）: [ROADMAP.md](../../design/ROADMAP.md)、[KOTLIN.md](../../design/KOTLIN.md)
