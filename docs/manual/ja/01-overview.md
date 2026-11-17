<a id="1-overview"></a>
# 1. 概要

[マニュアルのホーム](README.md) | トラック: [Java](java/README.md) | [Kotlin](kotlin/README.md) | 次: 2. はじめよう（[Java](java/02-getting-started.md) | [Kotlin](kotlin/02-getting-started.md)）

> [!NOTE]
> **状況:** 解析コア（ラムダの読み込みと条件のデコード）と、結果の関数 `toList()`、`toSet()`、`first()`、`one()`、`count()`、`exists()` を持つ `where`（HQL の生成とクエリの実行）は実装済みです。H2、MariaDB、Oracle で Java のラムダを使ってテストしています。
> ソートとページング（`orderBy`、`limit`、`offset`、`SortSpec`）の中核部分も実装済みで、テストしています。
> Spring Data JPA モジュール（`LambdaQlExecutor<T>` と Spring Boot の自動構成）も実装済みで、Spring Boot 3.4.2 でテストしています。
> 更新、挿入、削除、upsert も実装済みで、テストしています。`limit` と 1 行保護 `executeOne()` も使えます。Spring Data JPA モジュールでも使えます（短縮メソッド `deleteAll`、`updateOne` など）。そこでは呼び出し側のトランザクションが必要です。
> `where` の中のサブクエリと `select`（`groupBy`、`having`、`Agg` 関数を含む）も実装済みで、テスト済みです。
> 明示的な結合（`join`、`leftJoin`、`crossJoin`）、派生テーブルと CTE、ストアドプロシージャと DB 関数、4 つの言語のメッセージを持つエラーコードも実装済みで、テスト済みです。
> Kotlin のラムダは、書き方の一部（`?.`、`!!`、`in`、サブクエリ、data class、`groupBy`、更新、挿入、削除）をテストしています。
> このページの HQL は、設計で定めた出力です。例は設計上の例です。

このページは両方の言語向けです。コードは **Java** と **Kotlin** の別々のセクションに分けて載せています。

<a id="what-is-lambdaql"></a>
## lambdaQL とは

lambdaQL は、JPA と Hibernate 6.6 の上で動く、Java と Kotlin のためのクエリライブラリです。

- クエリは **ラムダ** で書きます。
- lambdaQL は各ラムダの **バイトコードを読みます**。ラムダを **実行することはありません**。
- lambdaQL はバイトコードから **HQL**（Hibernate Query Language）を組み立てます。
- ラムダの中に書いた定数（`1`、`100.0`、`"Kim"`）は HQL に直接書かれます。ラムダがキャプチャした値は **バインドパラメーター** になります。
- Hibernate がその HQL をデータベースで実行します。

### Java

```java
long minPrice = 100;

List<Order> orders = qb.from(Order.class)
    .where(o -> o.getStatus() == 1 || o.getCustomer().getName().equals("Kim"))
    .where(o -> o.getPrice() > minPrice)
    .toList();
```

### Kotlin

```kotlin
val minPrice = 100.0

val orders = qb.from(Order::class.java)
    .where { it.status == 1 || it.customer?.name == "Kim" }
    .where { it.price!! > minPrice }
    .toList()
```

<a id="hql-the-same-for-both"></a>
### HQL（どちらも同じ）

```
select e0 from Order e0 left join e0.customer j1
where (e0.status = 1 or j1.name = 'Kim') and e0.price > :p1
```

**SQL ブロックの読み方。** `…` は省略した select リスト（エンティティのすべてのカラム）で、`?` はバインドパラメーターです。各行は [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java) から取っています。このテストは各クエリを Hibernate 6.6.5 で H2 2.3、MariaDB 10.11、Oracle 23.26 に対して実行し、Hibernate の SQL が変わると失敗します。

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `overview-first`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where (o1_0.status=1 or c1_0.name='Kim') and o1_0.price>?
```

2 つの `where` 呼び出しは `AND` でつながります。
定数 `"Kim"` はリテラルになり、キャプチャした `minPrice` はバインドパラメーター（`:p1`）になります。
`where` の中の `o.getCustomer().getName()` のようなパスは、デフォルトで内部結合の `join` になります。customer のない注文は返されません。
ここでは `left join` になります。status が 1 の注文は、customer がなくても条件に合うからです（Java でもその注文を返します）。

ラムダは普通の Java や Kotlin のコードに見えます。
しかし実際は、Java や Kotlin の構文で書く **クエリ言語** です。
`o.getCustomer().getName()`（Kotlin では `it.customer?.name`）は getter を呼び出しません。**カラムを指定** しています。

<a id="why-lambdaql"></a>
## なぜ lambdaQL なのか

| 目的 | lambdaQL の方法 |
|---|---|
| 型安全 | コンパイラーがラムダをチェックします。IDE でプロパティの名前を変えると、クエリの中の名前も変わります。 |
| コード生成なし | メタモデルジェネレーターや Q クラスは不要です。lambdaQL は、Hibernate が実行時にすでに持っている JPA Metamodel を使います。 |
| いつもの構文 | `==`、`&&`、`\|\|`、`!`、getter やプロパティ、知っているメソッド呼び出しをそのまま使います。 |
| 安全な値 | キャプチャした値と計算した値は必ずバインドパラメーターになります。クエリのテキストに書かれるのは、コードにコンパイルされた定数だけです（引用符はエスケープされます）。 |
| ラムダは実行されない | エンティティのオブジェクトは作られません。getter も呼ばれません。ラムダから `NullPointerException` が出ることもありません。 |
| 分かりやすいエラー | lambdaQL が変換できない書き方のラムダには、コード付きのエラーを投げます。推測はしません。 |

<a id="how-it-works-5-steps"></a>
## 動作のしくみ（5 ステップ）

```
 your lambda ──► 1. find body ──► 2. read bytecode ──► 3. rebuild condition
                                                              │
     result ◄── 5. run with Hibernate ◄── 4. build HQL ◄──────┘
```

| ステップ | 何が起きるか | 状況 |
|---|---|---|
| 1. ラムダ本体を見つける | ラムダは `Serializable` です。lambdaQL はシリアライズのフックを使って `SerializedLambda` を取得します（`setAccessible` は使いません）。これで、ラムダ本体がどのメソッドにあるか、どの値がキャプチャされているかが分かります。 | 実装済み |
| 2. バイトコードを読む | lambdaQL は ASM でその 1 つのメソッドだけを読みます。クラスファイルを読むだけで、コードは実行しません。 | 実装済み |
| 3. 条件を組み立て直す | Java と Kotlin は `&&`、`\|\|`、`!` をジャンプ命令にコンパイルします。lambdaQL はこのジャンプから元の論理ツリーを組み立て直します。 | Java のラムダは実装済み。Kotlin: `?.`、`!!`、`in` はテスト済み。そのほかの Kotlin だけの書き方（`?:`、範囲、`when`）は設計済み。 |
| 4. HQL を組み立てる | getter やプロパティは JPA の属性パスになります（`o.getPrice()` や `it.price` → `e0.price`）。ラムダの中の定数はリテラルに、キャプチャした値はパラメーター（`:p1`）になります。 | `where` と `orderBy` について実装済み、H2・MariaDB・Oracle でテスト済み |
| 5. 実行する | lambdaQL はパラメーターをバインドし、Hibernate で HQL を実行します。各ラムダの解析結果はキャッシュされるので、バイトコードを読むのは 1 回だけです。 | `toList()` と `toSet()`（`limit` / `offset` を使う場合も）、`first()`、`one()`、`count()`、`exists()` について実装済み |

1 つのラムダを読むのにかかる時間は数マイクロ秒です。キャッシュがあるので、lambdaQL があるラムダのバイトコードを読むのは 1 回だけです。

lambdaQL に渡す Kotlin のラムダリテラルは、Java のラムダと同じ種類のシリアライズ可能なラムダにコンパイルされます。
そのため、1 つのエンジンで両方の言語を読めます。

<a id="design-principles"></a>
## 設計方針

<a id="one-lambda-per-clause"></a>
### 1 つの句に 1 つのラムダ

各句は **1 つの** ラムダを受け取ります。カラムごとに 1 つのラムダを書く必要はありません。

#### Java

```java
.where(o -> o.getStatus() == 1 && o.isActive())                 // 条件のラムダは 1 つ
.orderBy((o, by) -> by.desc(o.getPrice()).asc(o.getId()))     // すべてのキーを 1 つのソートラムダで
.select(o -> new OrderView(o.getId(), o.getPrice()))           // 選択のラムダは 1 つ
```

#### Kotlin

```kotlin
.where { it.status == 1 && it.active }                          // 条件のラムダは 1 つ
.orderBy { o, by -> by.desc(o.price).asc(o.id) }                // すべてのキーを 1 つのソートラムダで
.select { OrderView(it.id, it.price) }                          // 選択のラムダは 1 つ
```

ソートビルダーの `by` やサブクエリファクトリーの `sub` のようなヘルパーは **ラムダの引数** です。
名前は自由に付けられます。このマニュアルでは `by` と `sub` を使います。

<a id="no-static-helpers-except-agg"></a>
### static ヘルパーは使わない（`Agg` を除く）

lambdaQL のために static import をする必要はありません。
static ヘルパークラスは、集計（`count`、`sum`、`avg`、`min`、`max`、`countDistinct`）用の `org.lambdaql.Agg` だけです。
`Agg` の static import は任意です。

#### Java

```java
.having(o -> Agg.sum(o.getPrice()) > 1000)
```

#### Kotlin

```kotlin
.having { Agg.sum(it.price)!! > 1000 }
```

自分のデータベース関数も static メソッドですが、それは自分で宣言します
（8. プロシージャと DB 関数を参照: [Java](java/08-procedures-functions.md) | [Kotlin](kotlin/08-procedures-functions.md)）。

<a id="a-lambda-is-a-query-not-code"></a>
### ラムダはコードではなくクエリ

lambdaQL は Java や Kotlin の実行時のルールではなく、ドキュメントに書かれた独自のルールに従います。

#### Java

| 書いたもの | lambdaQL での意味 | 理由 |
|---|---|---|
| `o.getId() == id`（2 つの `Long` オブジェクト） | 値の等価比較、null 安全 | クエリでは、オブジェクトの `==` は「同じオブジェクト」ではなく「同じ値」を意味します。 |
| `o.getCustomer().getName()` | 結合した customer の `name` カラム（`where` の中ではデフォルトで内部結合の `join`） | パスはカラムを指定します。lambdaQL は customer が null かどうかをチェックしません。`where` の中では、customer のない注文は返されません。それらを探すには `o.getCustomer() == null` と書きます。null ガード（`o.getCustomer() == null \|\| ...`）があるとパスは `left join` になるので、Java と SQL で同じ行が返ります。 |
| `status` が NULL のときの `o.getStatus() == 1` | その行は一致しない（SQL の意味） | Java なら `NullPointerException` を投げます。SQL は UNKNOWN を返します。 |
| `o.getDescription() == null` | `e0.description is null` | null チェックを書いたので、lambdaQL はそれを使います。 |

> [!TIP]
> IntelliJ は `o.getId() == id` に対して「Number objects compared using `==`」という警告を出すことがあります。
> lambdaQL のラムダでは、これは意図した書き方です。`@SuppressWarnings("NumberEquality")` を使えます。

#### Kotlin

| 書いたもの | lambdaQL での意味 | 理由 |
|---|---|---|
| `it.id == id`（`id: Long?`、nullable な変数） | 値の等価比較、null 安全 | Kotlin の `==` はもともと「同じ値」を意味します。lambdaQL はこの意味をそのまま使います。 |
| `it.customer?.name` または `it.customer!!.name` | 結合した customer の `name` カラム（`where` の中ではデフォルトで内部結合の `join`） | パスはカラムを指定します。`?.` や `!!` でカラムは変わりません。`!!` はラムダをコンパイルするためだけにあります。`where` の中では、customer のない注文は返されません。それらを探すには `it.customer == null` と書きます。null ガード（`it.customer == null \|\| ...`）があるとパスは `left join` になります。 |
| `price` が NULL のときの `it.price!! > 100.0` | その行は一致しない（SQL の意味） | Kotlin なら `!!` で `NullPointerException` を投げます。lambdaQL は `!!` を無視し、SQL は UNKNOWN を返します。 |
| `it.description == null` | `e0.description is null` | null チェックを書いたので、lambdaQL はそれを使います。 |

lambdaQL が null を扱うのは **あなたが書いた場所だけ** です。
3. 条件（[Java](java/03-where.md) | [Kotlin](kotlin/03-where.md)）
と 10. ルールと制限（[Java](java/10-rules-and-limits.md) | [Kotlin](kotlin/10-rules-and-limits.md)）を参照してください。

<a id="sargable-sql"></a>
### SARGable な SQL

lambdaQL はインデックスを使える SQL（「SARGable」）を書きます。

- カラム側に関数、計算、`CASE` を置きません。
- 値を変える必要があるとき（たとえば小文字にする）は、lambdaQL はバインドする前に Java 側で **パラメーター** を変えます。
- ルール上カラムに関数が必要で、インデックスが役に立たない場合、lambdaQL は警告をログに出します。

#### Java

| 書いたもの | lambdaQL が書かない SQL | lambdaQL が書く SQL |
|---|---|---|
| `!sub.from(Customer.class).where(c -> c.getGrade() > 3).select(c -> c.getId()).contains(o.getCustomer().getId())` | `o.customer.id not in (select ...)` | `not exists (select 1 ...)` |

#### Kotlin

| 書いたもの | lambdaQL が書かない SQL | lambdaQL が書く SQL |
|---|---|---|
| `o.customer?.id !in sub.from(Customer::class.java).where { c -> c.grade!! > 3 }.select { c -> c.id }` | `o.customer.id not in (select ...)` | `not exists (select 1 ...)` |
| `(it.description ?: "") == "x"` | `coalesce(e0.description, '') = 'x'` | `e0.description = 'x'` |
| `it.description.isNullOrEmpty()` | `(e0.description is null or length(e0.description) = 0)` | `(e0.description is null or e0.description = :p1)` |
| `it.id == id`（`it.id` は `Long?`） | `not (e0.id is null or e0.id <> :p1)` | `e0.id = :p1`（id は NULL になれない） |

<a id="java-and-kotlin"></a>
### Java と Kotlin

Java と Kotlin は **同じエンジン** を使います。
同じ意味の Kotlin のラムダと Java のラムダは、同じ HQL になります。
Kotlin の一部の書き方はサポートしていません。その場合、lambdaQL は分かりやすいエラーを投げます（[9. Kotlin の注意点](kotlin/09-kotlin-notes.md) を参照）。
Java だけの詳細は [9. Java の注意点](java/09-java-notes.md) にあります。

<a id="fail-loudly"></a>
### はっきり失敗する

lambdaQL は、意味が分からない書き方を推測しません。
`[LQL-2004]` のようなコード付きの例外を投げます（[11. エラーと言語](11-errors-and-languages.md) を参照）。

拒否される書き方の例:

| 書き方 | Java | Kotlin |
|---|---|---|
| 条件によって選ばれる値 | `o.getStatus() == (flag ? 1 : 2)` | `it.status == if (flag) 1 else 2` |
| ラムダの中のループやローカル変数 | はい、拒否されます | はい、拒否されます |
| ラムダではないもの | 匿名クラス | `object : ...` 式、または Kotlin の関数型の値（`val f: (Order) -> Boolean`） |
| SQL で意味を持たないメソッド呼び出し | はい、拒否されます | はい、拒否されます（自分で書いた inline でない拡張関数も） |

<a id="comparison-with-other-approaches"></a>
## ほかの方法との比較

この表は短い中立的なまとめです。どのツールにも、それぞれ向いている用途があります。

| | JPQL / HQL 文字列 | JPA Criteria API | QueryDSL | lambdaQL |
|---|---|---|---|---|
| クエリの書き方 | 文字列のテキスト | Java のビルダーオブジェクト | 生成された Q クラス | ラムダ |
| コンパイラーによるチェック | なし（実行時に Hibernate がチェック） | あり | あり | あり |
| コード生成が必要 | いいえ | 任意（静的メタモデル） | はい（Q クラス） | いいえ |
| IDE での名前変更に強い | いいえ | 静的メタモデルを使えば | はい | はい |
| 見た目 | SQL | Java のビルダーコード | SQL 風の Java | 普通の Java / Kotlin の式 |
| Kotlin | 文字列 | 使えるが冗長 | 使える | サポート（同じエンジン） |
| 成熟度 | 標準 | 標準 | 成熟、広く使われている | 開発初期、未リリース |

<a id="next"></a>
## 次へ

- 2. はじめよう: lambdaQL をビルドして最初のクエリを書きます。[Java](java/02-getting-started.md) | [Kotlin](kotlin/02-getting-started.md)
- [マニュアルのホーム](README.md)
