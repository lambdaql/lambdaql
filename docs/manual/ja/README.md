<a id="lambdaql-user-manual-english"></a>
# lambdaQL ユーザーマニュアル（日本語）

**他の言語:** [English](../en/README.md) | [한국어](../ko/README.md) | [日本語](../ja/README.md) | [简体中文](../zh/README.md)

lambdaQL は、JPA と Hibernate の上で動く Java・Kotlin 用のクエリライブラリです。
クエリの条件とソートをラムダ式で書くと、lambdaQL がラムダ式のバイトコードを解析して HQL クエリを作ります。

<a id="a-first-look"></a>
## 例

### Java

<!-- region: ManualSharedTest.java#readme-first -->
```java
List<Order> orders = qb.from(Order.class)
        .where(o -> o.getStatus() == 1 && o.getPrice() > minPrice)
        .orderBy(o -> desc(o.getPrice()).asc(o.getId()))
        .limit(20)
        .toList();
```

### Kotlin

<!-- region: ManualKotlinSharedTest.kt#readme-first -->
```kotlin
val orders = qb.from(Order::class.java)
    .where { it.status.toInt() == 1 && it.price > minPrice }
    .orderBy { o -> desc(o.price).asc(o.id) }
    .limit(20)
    .toList()
```

<a id="hql-the-same-for-both"></a>
### HQL

```
select e0 from Order e0
where e0.status = 1 and e0.price > :p1
order by e0.price desc, e0.id asc
```

コード例: [`ManualSharedTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualSharedTest.java)（`readme-first`）、[`ManualKotlinSharedTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSharedTest.kt)（`readme-first`）。

<a id="choose-your-track"></a>
## トラックを選ぶ

このマニュアルには **2 つのトラック** があります。Java 用と Kotlin 用です。
各トラックには、その言語のコードだけが載っています。クエリを書く言語を選んでください。

| トラック | 開始ページ | こんな人向け |
|---|---|---|
| **Java** | [Java トラック](java/README.md) | クエリを Java で書きます。 |
| **Kotlin** | [Kotlin トラック](kotlin/README.md) | クエリを Kotlin で書きます。 |

2 つのトラックは同じエンジンを使うので、同じクエリはどちらの言語でも同じ HQL になります。

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
| 13 | Spring Boot 3 と 4（共通）: バージョン、アーティファクト、Hibernate 6.6 と 7.4 の SQL の違い | [13-spring-boot-versions.md](13-spring-boot-versions.md) | [13-spring-boot-versions.md](13-spring-boot-versions.md) |

<a id="about-the-examples"></a>
## 例について

コード例は、実行結果と HQL を比較する検証コードです。各節の末尾にある「コード例」のリンクからコード全体を見られます。

**データベースごとの SQL ブロックの読み方。** `…` は省略した select リスト（エンティティのすべてのカラム）で、`?` はバインドパラメーターです。

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
| Java | 21 以上 |
| Kotlin | 2.x 以上 |
| Hibernate ORM | 6.6 または 7.4（6.6.53、7.4.13 でテスト） |
| 検証したデータベース | H2 2.3、MariaDB 10.11、MySQL 8.4、PostgreSQL 17、Oracle 23.26 (23ai) |
| Spring Boot（任意） | 3.5 または 4.1（3.5.16、4.1.2 でテスト）。[13. Spring Boot 3 と 4](13-spring-boot-versions.md) を参照 |

<a id="help-and-contributions"></a>
## ヘルプと貢献

- ソースコード: <https://github.com/lambdaql/lambdaql>
- どの言語でも、間違いを見つけたら issue か pull request を作成してください。翻訳の手伝いも歓迎します。
- 設計ドキュメント（韓国語、コントリビューター向け）: [ROADMAP.md](../../design/ROADMAP.md)、[KOTLIN.md](../../design/KOTLIN.md)
