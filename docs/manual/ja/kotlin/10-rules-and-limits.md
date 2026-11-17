<a id="10-rules-and-limits-kotlin"></a>
# 10. ルールと制限（Kotlin）

> [!NOTE]
> **状況: 一部実装済み。**
> この章には、Kotlin 向けの lambdaQL の設計のルールをまとめています。ラムダがどのように SQL になるか、NULL がどう扱われるか、
> Kotlin と SQL がどこで違うか、データベースがどこで違うか、何がサポートされないか、です。
> ルールは H2 2.3、MariaDB 10.11、Oracle 23ai（Hibernate 6.6.5）のプロトタイプで、Kotlin のバイトコードは kotlinc 1.7.22 から 2.3.21 で確認しました。
> 現在動作するのは解析コア、`where`、結果の関数（`toList()`、`toSet()`、`first()`、`one()`、`count()`、`exists()`）、ソートとページングの中核部分、`SortSpec`、update/insert/delete/upsert（[7](07-dml.md)）、そして Spring Data JPA モジュール（[12](12-spring-data-jpa.md)）です（H2、MariaDB、Oracle で Java のラムダを使ってテスト済み）。
> サブクエリ（[5](05-subquery.md)）と集計を含む `select`（[6](06-select-group.md)）のルールも実装済みで、DML の中でも使えます。以下の派生テーブル、CTE、プロシージャのルールは **まだ実装されていません**。ソートのルールにも、まだ実装されていないものがあります（[4.12](04-order-paging.md#412-rules-summary-s1-to-s14) を参照）。
> 全体の状況は [トラックの README](README.md) を参照してください。

[Kotlin トラック](README.md) | [マニュアルのホーム](../README.md) | 前: [9. Kotlin の注意点](09-kotlin-notes.md) | 次: [11. エラーと言語](../11-errors-and-languages.md) | [このページの Java 版](../java/10-rules-and-limits.md)

共通ページ: [1. 概要](../01-overview.md) · [11. エラーと言語](../11-errors-and-languages.md)

<a id="contents"></a>
## 目次

- [10.1 基本ルール: ラムダはクエリである](#101-the-base-rule-a-lambda-is-a-query)
- [10.2 SARGable のルール（インデックスを使える SQL）](#102-sargable-rule-index-friendly-sql)
- [10.3 NULL のルール](#103-null-rules)
- [10.4 演算子のルール（R1 から R24）](#104-operator-rules-r1-to-r24)
- [10.5 Kotlin と SQL の違い](#105-kotlin-and-sql-differences)
- [10.6 データベースごとの違い](#106-database-differences)
- [10.7 サポートしないものと、代わりの書き方](#107-not-supported-and-what-to-write-instead)
- [10.8 キャッシュ: ラムダの解析は 1 回だけ](#108-caching-a-lambda-is-analyzed-once)
- [10.9 テストした環境](#109-tested-environment)

例の読み方:

- 例の下のコメントは、設計で決まっている場合に、lambdaQL が作る HQL を示します。
- ラムダの中に書いた定数（数値、文字列、boolean、`null`、enum 定数）は、リテラルとして HQL に書かれます。
  キャプチャした値と計算した値は常にバインドパラメーター（`:p1`、`:p2`、...）になります（D37）。
- エンティティ: `Order`（`id`、`price`、`status`、`active`、`description`、`customer`、`updateAt`、`items`）、
  `Customer`（`id`、`name`、`grade`）、`Item`（`order`、`price`、`qty`）。[2. はじめよう](02-getting-started.md#example-entities) を参照してください。
  一部の行では、追加の nullable なプロパティ `flag: Boolean?` を使います。

---

<a id="101-the-base-rule-a-lambda-is-a-query"></a>
## 10.1 基本ルール: ラムダはクエリである

lambdaQL は **ラムダを決して実行しません**。バイトコードを読み、HQL を書きます。
ラムダは、Kotlin の構文で書いたクエリです。したがって:

1. `it.customer?.name` のようなパスは **カラムを指します**。呼び出しの連鎖ではありません。
   lambdaQL は customer が null かどうかを確認しません（D20）。`where` の中ではパスはデフォルトで内部結合なので、customer のない注文は返されません（D36）。
   customer がなくても条件が true になりうるなら（null ガード）、パスは `left join` になるので、結果は Kotlin と同じになります。
   それらの注文を探すには、関連そのものを比較します: `it.customer == null`。`it.customer?.name == null` ではそれらは返されません。
2. NULL は **書いた場所でだけ** 扱われます（`== null`、`!= null`、`?.`、`?:`、2 つの nullable な値の間の `==`）（D22）。
   Kotlin のコンパイラーが追加する null チェックは無視されます。それ以外の NULL は SQL のルールに従います。条件は UNKNOWN になり、その行は返されません。
3. `!!` は無視されます（D23）。Kotlin のメモリのルール（オブジェクトの同一性、ボクシング）は当てはまりません。`==` と `===` は **値の等価** を意味します（D12、R17）。
4. **ルールのない書き方について、lambdaQL は推測しません。** コード `[LQL-xxxx]` とソースの行を含む
   分かりやすいエラーを投げます。「たぶん正しい」SQL を作ることはありません。

<a id="102-sargable-rule-index-friendly-sql"></a>
## 10.2 SARGable のルール（インデックスを使える SQL）

「SARGable」とは、データベースがその条件にインデックスを使えるという意味です。
これは lambdaQL の基本ルールです（設計のセクション 3.9）。

| # | ルール |
|---|---|
| 1 | **カラム側に関数、演算子、`CASE` を置かない。** 値を変える必要がある場合は、バインドする前にアプリケーションでパラメーターのほうを変えます（たとえば `Locale.ROOT` で小文字にする） |
| 2 | 2 つの書き方が同じ意味なら、単純な比較と `AND` / `OR` / `NOT` だけを使うほうを選びます。正しい 3 値論理が最優先で、その範囲で SARGable な形を選びます |
| 3 | 意味のうえでどうしてもカラムに関数が必要な場合（たとえば大文字と小文字を区別しない比較）、lambdaQL は起動時に DB のメタデータを読み、DB がすでに持っている経路を使います。カラムの照合順序（collation）、関数インデックス、言語インデックスです。経路がない場合は、起動時にカラムごとに WARN をログに出します（strict モード: エラー） |
| 4 | 各出力ルールは、H2、MariaDB、Oracle で `EXPLAIN` を使って確認します |

<a id="examples"></a>
### 例

**大文字と小文字を区別しない比較**（`equals(s, ignoreCase = true)`、`startsWith(s, ignoreCase = true)`。D24）

| DB にあるもの | lambdaQL が書く SQL | こうは書かない |
|---|---|---|
| 大文字と小文字を区別しない照合順序（MariaDB の `_ci`） | `e0.description = :p1` | `lower(e0.description) = lower(:p1)` |
| Oracle の関数インデックス `LOWER(description)` | `lower(e0.description) = :p1`（`:p1` はアプリケーションで小文字にする） | `lower(e0.description) = lower(:p1)` |
| Oracle の言語インデックス `NLSSORT(... BINARY_CI)` | `e0.description collate binary_ci = :p1` | |
| 経路がない | 起動時に WARN（strict モード: エラー） | |

**片側が NOT NULL のときの null-safe 比較**

```kotlin
.where { it.description == text }   // クエリの実行時に text は null ではない
// e0.description = :p1                ("is not distinct from" ではない)
.where { it.description != text }
// (e0.description <> :p1 or e0.description is null)
```

Oracle では、`is not distinct from` は `decode(...)` になり、インデックスを使えません。
そのため、片側が NOT NULL だと分かっている場合（`@Id`、`nullable = false`、プリミティブのプロパティ、
または null ではないバインド値）、lambdaQL は普通の `=` を書きます。

**2 つの boolean の式の比較**（D25）

```kotlin
.where { it.active == (it.status!! > c) }
// (e0.active = true and e0.status > :p1) or (e0.active = false and e0.status <= :p1)
```

`CASE` の形（`e0.active = case when ... end`）は正しい行を返しますが、フルスキャンが必要になるため、
lambdaQL は使いません。

**エルビス演算子と空文字列**

```kotlin
.where { (it.description ?: "") == "x" }     // e0.description = :p1
.where { (it.description ?: "") == "" }      // (e0.description = :p1 or o.description is null)
.where { it.description.isNullOrEmpty() }    // (o.description is null or e0.description = :p1)
```

**サブクエリを使う NOT IN** は `NOT EXISTS` として書かれます（アンチジョインで、NULL に対して安全です。[10.3](#103-null-rules) を参照）。

<a id="what-the-measurements-showed"></a>
### 測定で分かったこと

MariaDB 10.11 と Oracle 23ai、20,000 から 50,000 行:

| 形 | MariaDB | Oracle | 良いか |
|---|---|---|---|
| `_ci` カラムでの `name = 'abc'` | インデックス検索 | – | はい |
| `LOWER(name) = LOWER('abc')` | インデックスのフルスキャン（19,791 行） | テーブルのフルスキャン（関数インデックスなし） | **いいえ** |
| `lower(name) = :p_lowered` + 関数インデックス | – | インデックスの範囲スキャン（`LIKE 'ab%'` も） | はい |
| `name COLLATE BINARY_CI = :p` + 言語インデックス | – | インデックスの範囲スキャン | はい |
| boolean の `==` を `AND`/`OR` で | 範囲（350 行） | インデックスの範囲スキャン 2 回 + ビットマップ OR | はい |
| boolean の `==` を `CASE` で | インデックスのフルスキャン（49,648 行） | テーブルのフルスキャン | **いいえ** |

<a id="planned-items-under-the-same-rule"></a>
### 同じルールのもとで予定している項目

| 形 | 予定の SQL |
|---|---|
| 「`updateAt` の年が 2024」のような日付関数 | `e0.updateAt >= :from and e0.updateAt < :to` |
| 関数のキーによるソート（`lower(name)`） | まずアクセス経路を確認し、なければ WARN |

<a id="db-functions-on-a-column"></a>
### カラムに対する DB 関数

カラム側の DB 関数（`OrderFns.discount(it.price)!! > 10`）は、通常のインデックスを使えません。
lambdaQL は **WARN** をログに出します（strict モード: エラー）。例外: Oracle では、関数が
`DETERMINISTIC` で、同じ式の関数ベースのインデックスがある場合は WARN を出しません。MariaDB では、
`NOT DETERMINISTIC`（デフォルト）の関数は、引数がパラメーターだけの場合でもフルスキャンになる
（`it.price!! > OrderFns.discount(rate)!!`）ので、lambdaQL はこの場合も警告します。
[8. プロシージャと DB 関数](08-procedures-functions.md) を参照してください。

<a id="103-null-rules"></a>
## 10.3 NULL のルール

| ラムダ | SQL | NULL の動作 |
|---|---|---|
| `it.status == 1`（`Int?` と定数） | `e0.status = 1` | NULL: UNKNOWN で、行は返されない。コンパイラーが隠れて追加する null チェックは無視される（D22） |
| `it.status != 1` | `e0.status <> 1` | NULL: 行は返され **ない** |
| `it.price!! > p` | `e0.price > :p1` | NULL: 行は返されない。`!!` は無視される（D23） |
| `it.status == q`（両方 nullable: `q: Int?`、`String?`、enum） | null-safe の `is not distinct from`、または片側が NOT NULL なら `=` | `null == null` は true（Kotlin の意味） |
| `it.status != q`（両方 nullable） | `is distinct from`、または `(a <> b or a is null)` | `q` が null でなければ NULL の行も返される（Kotlin の意味） |
| `it.description.equals(text)`（`String?`） | `==` と同じ | null-safe |
| `x == null`、`x != null` | `is null`、`is not null` | 2 値 |
| `it.id == id`（`it.id` は `Long?`、`id` はキャプチャした `Long`）、`it.id == null`: NULL になれないカラム（id、プリミティブ、`nullable = false`、パスに関連のステップがない） | `not (e0.id is null or e0.id <> :p1)` の代わりに `e0.id = :p1`。`it.id == null` は `1=0`（Java でテスト済み） | カラムに NULL はない。関連を通るパス（`it.customer?.id`）ではチェックが残る |
| `!`、`&&`、`\|\|` | `NOT`、`AND`、`OR` | SQL の 3 値論理 |
| `it.flag == true` / `it.flag != true` | `is not distinct from true` / `is distinct from true` | Kotlin と同じ。3 つの DB すべてで動作 |
| `it.flag ?: false`（条件） | `(e0.flag is not null and e0.flag = true)` | NULL は false として扱う |
| `where` の中の `it.customer?.name`、`it.customer!!.name` | `j1.name` + `join e0.customer j1`（デフォルトで内部結合） | customer がない: 注文は返されない。`it.customer?.name == null` でも同じ |
| `it.customer == null \|\| it.customer?.name == p` | `e0.customer is null or j1.name = :p1` + `left join e0.customer j1` | customer がない: Kotlin と同じく返される |
| 同じパスを `orderBy` か `select` だけで使う | `j1.name` + `left join e0.customer j1` | customer がない: カラムは NULL |
| `it.customer == null`、`it.customer != null` | `e0.customer is null`、`e0.customer is not null`（FK カラム、結合なし） | customer のない注文 / customer がある注文 |
| `it.customer?.id` | `e0.customer.id`（FK カラム、結合なし） | customer がない: NULL |
| `a ?: b`（値） | 値では `coalesce(a, b)`。文字列の条件では SARGable な形 | [9.5](09-kotlin-notes.md#95-null-operators---and-) を参照 |
| `it.id in ids` | `e0.id in (:p1)` | NULL の id: 返されない |
| `it.id !in ids` | `e0.id not in (:p1)`（id は NULL にならない）。NULL になりうるカラム: `(x is null or x not in (:p1))` | NULL の値（NULL になりうるカラム）: 返る。Kotlin と同じ |
| `it.status in 1..5`、`!in 1..5`、`when (it.status) { ... }` | `between`、`not (... between ...)`、`in (...)` | NULL の対象: 返されない。`NOT` の下でも同じ（ガードなし、D22） |
| `x !in sub...` を使うサブクエリ | `not exists (...)` | サブクエリが NULL を返しても正しい |
| `Agg.sum`、`avg`、`min`、`max` | `sum(...)` ... | 行がないか、すべての値が NULL のときは NULL |
| `Agg.count()` | `count(*)` | 行がないときは 0 |

<a id="captured-null-values"></a>
### null のキャプチャ値

| キャプチャ値がある場所 | `null` を使えるか |
|---|---|
| null-safe の形: 2 つの nullable な値の間の `==` / `!=` | はい |
| 同じ値に対する null ガードの下: `p == null \|\| it.status!! > p` | はい。ガードはクエリの実行時にアプリケーションの中で決まる: `p == null` なら絞り込みなし、それ以外の値なら `e0.status > :p1` |
| `!!` 付き、または大小比較（`<` `<=` `>` `>=`）の中: `it.price!! > p!!` | **いいえ。** 値をバインドするときにエラー（Kotlin なら NPE を投げる） |
| `in` の中の、`null` の要素を持つコレクション | **いいえ。** バインドするときにエラー |
| `NaN`、`Infinity` | **いいえ。** 常にエラー |

<a id="104-operator-rules-r1-to-r24"></a>
## 10.4 演算子のルール（R1 から R24）

これは、設計のルール表（セクション 3.6 と KOTLIN.md のセクション 5）を短くまとめ、Kotlin の書き方を添えたものです。

| # | ラムダの書き方 | 意味 |
|---|---|---|
| R1 | null でないプリミティブの比較、または `!!` の後の比較（`it.qty > 5`、`it.price!! > p`） | `=`、`<>`、`<`、... SQL の NULL のルール |
| R2 | 参照の比較にコンパイルされる `==` / `!=`（enum の `==`。`===` は R17 を参照） | null-safe な値の等価。オブジェクトの同一性ではない |
| R3 | `x == null`、`x != null` | `IS NULL`、`IS NOT NULL` |
| R6 | `!`、`&&`、`\|\|` | `NOT`、`AND`、`OR`。SQL の 3 値論理 |
| R7 | 文字列の値の比較 | **DB の照合順序** に従う（大文字と小文字、末尾の空白）。Oracle は `''` を NULL として扱う |
| R8 | エンティティ `==` エンティティ | 予定: 識別子の等価。v0.1 にはない（KC13） |
| R9 | キャプチャ値 | 実行時の値を持つバインドパラメーター |
| R10 | プロパティへのアクセス（`it.price`） | getter の呼び出し。JPA の属性にマッピングされる |
| R11 | `Intrinsics.areEqual` にコンパイルされる Kotlin の `==`、`!=`（参照、または両側が nullable） | null-safe な値の等価。スキーマまたはバインド値によって片側が NOT NULL のときだけ `=` |
| R12 | nullable なプリミティブとプリミティブ（`it.id == 1L`、`id: Long?`） | 隠れた null チェックは無視される: `e0.id = 1`、`e0.id <> 1` |
| R13 | `!!` とその他のコンパイラーの null チェック | 無視される（D23） |
| R14 | `?.` | NULL が伝わるパスまたは値。`?.` がない場合と同じカラム |
| R15 | `?:` | 値では `coalesce(a, b)`。条件の中の boolean のエルビスは `(x is not null and x = true)` になる。値としての boolean のエルビスは拒否される（KC21） |
| R16 | 関連の結合 | R24 に置き換え |
| R17 | `===`、`!==` | `==` と同じく値の等価 |
| R18 | 範囲 `in a..b`、`until`、`..<`、`downTo`、`!in` | `between`、`>= and <`、`not (...)`。null ガードは自動で付かない |
| R19 | コレクション `x in c`、`x !in c`（キャプチャした `c`） | `x in (:p)`、`(x is null or x not in (:p))`。null の要素: エラー。空: FALSE / TRUE |
| R20 | 定数の分岐が `true`/`false` で終わる `when (subject)` | `in (...)` または比較の `OR` |
| R21 | Kotlin の文字列関数（`startsWith`、`endsWith`、`contains`、`equals(ignoreCase)`、`lowercase`、`length`、`isNullOrEmpty`） | `like ... escape '\'`、D24 の大文字小文字を区別しない経路、`lower`、`length`、`(x is null or x = :p)` |
| R22 | Kotlin のホストの値（キャプチャした `var`、private プロパティ、`companion` の値） | 実行のたびに読む。書き込みは拒否される |
| R23 | プロパティ参照 `Order::price` | getter と同じ。`orderBy`（`orderBy(Order::price)`）では**まだ実装されていません**。`orderBy { it.price }` を使ってください |
| R24 | 関連のパス（`it.customer?.name`、`it.customer!!.name`、Java のエンティティの `it.customer.name`） | カラム。to-one の関連: where、order by、select で共有するエイリアス。`where` ではデフォルトで内部結合、null ガードが関連のない行を残すときは `left join`（D36）。order by か select だけで使うなら `left join`。Embedded: 同じテーブル。対象の `@Id` で終わる、または関連を `null` と比較する: FK カラム、結合なし。コレクション: エラー |

Java だけのルール R4（エンティティの値に対する `equals`）と R5（Java の null-safe なヘルパー）は [Java トラック](../java/10-rules-and-limits.md) にあります。Kotlin では、`==` がすでに null-safe の意味を持っています。

その他のルール:

- `x.compareTo(y) > 0`、`isBefore`、`isAfter` は `x > y`、... になります。
- ラムダの中に書いた定数（`const val` も）はリテラルです: `it.price!! > 100.0` は `e0.price > 100.0`、`"O'Brien"` は `'O''Brien'` になります。キャプチャした値と計算した値（`BigDecimal("1.5")`）はバインドパラメーターです（D37）。
- コンパイラーは `!` の位置を動かすことがあります（ド・モルガンの法則、二重否定）。これで結果が変わることはありません。
  lambdaQL は各条件を正確に 1 回ずつ保持し、ロジックを「単純化」することはありません。
- Kotlin で常に true になる条件（`x > 5 || !(x > 5)`）を書いても、単純化されません。
  SQL では `x` が NULL の行が除かれます。これは SQL のルールです。

<a id="105-kotlin-and-sql-differences"></a>
## 10.5 Kotlin と SQL の違い

これらの違いは SQL そのものから来ます。コンパイラーの問題ではありません。
「G」の番号は設計での ID です（セクション 2.7）。

| # | 違い | lambdaQL の動作 |
|---|---|---|
| G1 | null-safe な不等価: Kotlin では `null != 3` は true。SQL の `status <> 3` は UNKNOWN | 2 つの nullable な値の間の `!=` は null-safe（`is distinct from`）で、Kotlin と同じ行になる。定数との `!=` は SQL の意味を使う（D22） |
| G2 | `it.flag != true` と `NOT flag` | `e0.flag is distinct from true` を出力する（3 つの DB すべてで動作） |
| G3 | NULL のパラメーターを使う `name = ?` は 0 行を返す | null-safe の形は `is [not] distinct from` を使う |
| G4 | `status NOT IN (1, NULL)` は常に 0 行を返す | キャプチャしたコレクションの中の `null` はエラー。サブクエリの `!in` は `NOT EXISTS` |
| G5 | Kotlin は NPE を投げる（`!!`、アンボクシング）が、SQL は比較するだけ | ドキュメントに記載: エンティティの NULL は SQL のルールに従う |
| G6 | `NaN`: H2 は NaN を最大の値としてソートし、`NaN = NaN` は true。MariaDB は保存できない。Oracle は `BINARY_DOUBLE` にだけ保存できる | キャプチャした NaN や Infinity はエラー。保存された NaN は DB に従う |
| G7 | `Float` のカラムと `Double` の値の比較: 結果が DB によって違う | `Float`/`Double` について **Kotlin とビット単位で同じ結果は約束しない**。lambdaQL は `CAST` を追加しない。カラムのマッピングを選ぶ（たとえば Oracle の `binary_float` / `binary_double`） |
| G9 | `x > 5 \|\| !(x > 5)` は NULL の行を除く | 単純化せず、書いたとおりに出力する |
| G10 | SQL は評価の順序を約束しない: `qty != 0 && total / qty > 2` でもゼロ除算が起きうる | ガードの下の `/`、`%`、`CAST` は v0.1 では拒否される（D13） |
| G11 | 文字列: MariaDB の `_ci` は大文字と小文字、末尾の空白を無視する。Oracle は `''` を NULL として保存する。Oracle の `NLS_SORT` は JVM のロケールに従う | ドキュメントに記載: 文字列の比較とソートは DB に従う。Oracle では `NLS_SORT=BINARY` を設定する（セッションの初期化、または `-Duser.language=en`）。Oracle では `it.description == ""` と `isEmpty()` の結果が違う |
| G12 | Kotlin の `===` は同一性（1000 を持つ 2 つの `Long` のボックスでは false） | lambdaQL は `===` と `==` を値の等価として定義する |
| G13 | 古い boolean のカラム（MariaDB の `TINYINT(1)`、Oracle の `NUMBER(1)`）に 0/1 以外の値が入っていると、読み方が違う | Hibernate 自身の DDL（`bit(1)`、Oracle 23 の `boolean`）なら問題ない。古いスキーマでは注意する |
| G14 | Oracle 23 のバグ: 複合条件に対する `WHERE (a AND b) = false` / `IS FALSE` / `IS NULL` で行が失われる | lambdaQL は複合条件を boolean のテストで包まない。否定は常に `NOT(...)` |
| G15 | Oracle の `CHAR` のカラムは `String` のバインドと一致しない | ドキュメントに記載: エンティティのマッピングに注意する |

その他の違い:

| 違い | Kotlin | SQL | lambdaQL |
|---|---|---|---|
| `BigDecimal` の等価 | `==` はスケールも比較する（`1.0` と `1.00` は false） | `=` は値を比較する | 値の等価 |
| 整数の除算 | `5 / 2` は `2` | MariaDB は `2.5` を返す | DB によって違う。最終的なルールは未定で、v0.1 では拒否されるかもしれない |
| `BigDecimal` の除算と関数の結果 | Kotlin のスケールのルール（`HALF_EVEN`） | スケールは DB が決める（たとえば関数の結果では H2 は 1、MariaDB と Oracle は 0） | ドキュメントに記載 |
| Unicode の大文字小文字を区別しない比較（`ſ`、`İ`） | Kotlin のルール | DB の照合順序 | DB に従う |
| 文字列の長さ | UTF-16 の単位 | H2 は UTF-16 の単位、MariaDB はコードポイント | ドキュメントに記載 |
| NULL を含む文字列テンプレート | `"null"` という文字列 | NULL（Oracle: `NULL \|\| 'x'` は `'x'`） | 文字列の連結は予定。v0.1 にはない |
| nullable な `when` / 範囲の対象 | `else` に進む。`!in` は true | 返されない。`NOT` の下でも同じ | SQL の意味（D22） |
| null にならない型 `String` | 決して null にならない | カラムは NULL になりうる | lambdaQL は JPA のメタデータを使う |
| ラムダの中の `LocalDateTime.now()` | アプリの時刻 | – | **アプリの時刻** としてバインドされる。DB の時刻（`current_timestamp`）はサポートしない |
| `Set` の中の等しい要素 | `equals` / `hashCode` が決める（`data class` ではプライマリコンストラクターのプロパティ） | 主キーで行を区別する | Set が行数より小さいと、`toSet()` と `findSet()` は `IllegalStateException` を投げる（エンティティの `equals` / `hashCode` が違う行を等しいと判断する場合や、`@Id` が一意でない場合。たとえばビュー）。`toList()` を使う（D42、Java でテスト済み） |

<a id="106-database-differences"></a>
## 10.6 データベースごとの違い

lambdaQL は多くの違いを隠します。知っておく必要があるものもあります。

<a id="sorting-and-paging"></a>
### ソートとページング

| 項目 | H2 | MariaDB | Oracle | lambdaQL |
|---|---|---|---|---|
| NULL の位置、`ASC` | 先頭 | 先頭 | **末尾** | `ASC` はすべての DB で NULLS FIRST を意味する（D16） |
| NULL の位置、`DESC` | 末尾 | 末尾 | **先頭** | `DESC` はすべての DB で NULLS LAST を意味する（D16） |
| `NULLS FIRST/LAST` 構文 | あり | **なし**（構文エラー） | あり | MariaDB では Hibernate が `CASE` でエミュレートする: ソートに **インデックスを使えない** |
| ページングの SQL | `offset ? rows fetch first ? rows only` | `limit ?,?` | `offset ? rows fetch first ? rows only` | Hibernate に渡す（`setFirstResult` / `setMaxResults`） |
| 同じ値の行の順序 | – | – | ランダム | ページングでは、キーに id が含まれていない場合に lambdaQL がルートの `@Id asc` を追加する |
| 文字列の順序 | 大文字と小文字を区別 | `_ci` | `NLS_SORT`（JVM のロケール） | DB に従う |
| `order by` の暗黙のパス | inner join（行が失われる） | 同じ | 同じ | lambdaQL は `left join`（または同じパスの `where` の結合）を使う |

lambdaQL は、NULL になりうるすべてのキーについて HQL に `nulls first` / `nulls last` を書きます。それがデータベースのデフォルトと同じなら、Hibernate が省きます。
デフォルト（`ASC` = NULLS FIRST、`DESC` = NULLS LAST）は H2 と MariaDB と同じなので、そこでは追加の SQL は
必要ありません。`NULLS` 句が付くのは Oracle だけです。NULL の位置を変えるには、
`by.asc(key, false)`（NULL が末尾）か `by.desc(key, true)`（NULL が先頭）を使います（D35）。MariaDB では、これらは `CASE` のエミュレーションを使います。

<a id="conditions-and-values"></a>
### 条件と値

| 項目 | H2 | MariaDB | Oracle |
|---|---|---|---|
| `is not distinct from` | ネイティブ | `<=>`（インデックスを使う） | `decode(...)`（インデックスなし）。そのため lambdaQL はできるだけ `=` を使う |
| `is [not] true`、`coalesce(boolean, false)` | 動作する | 動作する | **ORA-00932**。lambdaQL はこれらを書かない |
| 空文字列 `''` | 値 | 値 | **NULL** |
| 大文字と小文字、末尾の空白 | 厳密 | `_ci`: 無視 | 厳密（ソートには `NLS_SORT`） |
| `NaN` | 保存できる、最大の値 | 保存できない | `BINARY_DOUBLE` だけ |
| `Float` のカラム | REAL のリテラルのルール | double に拡大 | Hibernate は `Float` を `float(24)`（10 進数）にマッピングする |
| 整数の `/` | 整数 | **小数**（`5/2 = 2.5`） | – |
| 大きな `in` のリスト（1,500 個の値） | 動作する | 動作する | 23ai では動作する（古い Oracle の 1,000 個の制限は測定していない） |

<a id="subqueries"></a>
### サブクエリ

| 項目 | 何が起きるか | lambdaQL |
|---|---|---|
| `limit` のないサブクエリの `order by` | Oracle: 構文エラー（ORA-00907）。MariaDB: 無視される | WARN を出して削除する |
| MariaDB の `IN (... limit n)` | サポートされない（エラー 1235） | 自動で包む: `in (select x.id from (... limit n) x)` |
| MariaDB の `lateral` | 結果が間違う（エミュレーション） | `row_number() over (...)` で書き換える（まだ実装されていない） |

<a id="update-insert-delete"></a>
### 更新、挿入、削除

| 項目 | 何が起きるか | lambdaQL |
|---|---|---|
| `SET a = ..., b = a + 1`（設定した後でカラムを読む） | MariaDB は **新しい** 値を読み、H2 と Oracle は古い値を読む | 解析時にエラー（U3） |
| DML の中の関連のパス（`it.customer?.name`） | Hibernate は DB ごとに違う結合を作り、customer のない **行が失われる** | lambdaQL は相関スカラーサブクエリ（`where` の中と SET の中）を書く。`where` では、選択が内部結合するパスに `customer.id is not null` も加えるので、DML はクエリと同じ行に当たる（U9、テスト済み） |
| 同じテーブルに対するサブクエリを使う `DELETE` | MariaDB のエラー 1093 | 派生テーブルで包む（U17。まだ実装されていない） |
| 同じ enum の値を STRING と ORDINAL のカラムに 1 回だけバインドする | H2 と MariaDB は **エラーなしで間違った値** を保存する | 場所ごとに 1 つのパラメーター。共有しない（U7） |
| `values(list) { }` の多くの行 | Oracle: バインド数の上限は 65,535（ORA-01745）で、それよりずっと前からとても遅い | 分割する: 1 文あたり最大 500 個の値、すべて 1 つのトランザクションで実行（U13） |
| `order by ... limit` を使う `update` / `delete` | MariaDB は `in` サブクエリの中の `limit`（エラー 1235）と、対象テーブルへのサブクエリ（エラー 1093）を拒否する | `e0.id in (select t.id from (select x0.id as id ... order by ... limit n) t)`: 派生テーブル `t` は 3 つのデータベースすべてで動く。内側の select は select の結合を使う（D36）ので、行は select と同じ（Java でテスト済み） |
| `on conflict` / upsert | H2 と Oracle: `merge`。MariaDB: `insert ... on duplicate key update` で、更新した行は 2 と数える。Hibernate の `do nothing` は H2 と Oracle で失敗する | `upsert` と `onConflict(...).update(...)` は Hibernate の形を使う。`ignore()` は `insert ... select ... where not exists` として書く。行数はそのまま返す（D40、テスト済み） |

<a id="procedures-and-functions"></a>
### プロシージャと関数

| 項目 | H2 | MariaDB | Oracle |
|---|---|---|---|
| OUT / INOUT パラメーター | **なし** | あり | あり |
| 複数の結果セット | なし | あり | あり（REF CURSOR） |
| プロシージャが影響した行数 | – | – | 常に -1 なので lambdaQL は提供しない。OUT パラメーターを使う |
| boolean のパラメーターと結果 | – | あり | 23ai 以降 |
| 仮想カラム / 関数インデックスの中のストアドファンクション | – | 不可（エラー 1901） | 可能（関数ベースのインデックス） |

H2 には OUT パラメーターがないので、プロシージャの機能は MariaDB と Oracle で確認しています。

<a id="107-not-supported-and-what-to-write-instead"></a>
## 10.7 サポートしないものと、代わりの書き方

このリストのすべての書き方について、lambdaQL は分かりやすいエラーを投げます。「予定」は、後のバージョンという意味です。

<a id="lambda-form-and-loading"></a>
### ラムダの形と読み込み

| サポートしない | 代わりの書き方 |
|---|---|
| オブジェクト式（`object : ...`）、`Serializable` でない関数型インターフェース（KC5） | ラムダリテラル |
| Kotlin の関数型として保存したラムダ（`val f: (Order) -> Boolean`）（KC4） | ラムダリテラルを渡す |
| `-Xsam-conversions=class`（KC1）、`inline fun` の本体の中で作られたラムダ（KC2） | [9.14](09-kotlin-notes.md#914-kotlin-limits-kc1-to-kc21) を参照 |
| 独自の Kotlin の `fun interface` への呼び出し可能参照（KC3） | ラムダリテラル |
| 条件、ソート、select のラムダの中のローカルな `val`/`var`、ループ、複数の文 | 1 つの式。値はラムダの外で計算する |
| キャプチャした変数への書き込み（`n++`）（KC12） | ラムダの外で計算する |
| `@JvmField`、`internal`、`value class`、`@get:JvmName` のプロパティ（KC15、KC16） | public な通常のプロパティ |
| 同梱の ASM が知らない、より新しい JDK 向けにコンパイルされたラムダ | ASM を更新する（lambdaQL は ASM をシェーディングしない） |

<a id="conditions"></a>
### 条件

| サポートしない | 代わりの書き方 |
|---|---|
| 値の `if` / `when` / `takeIf`、一般的な `if (c) x else y`（KC6） | `&&` / `\|\|`。値はラムダの外で選ぶ。`CASE WHEN` は予定 |
| （サポートする: `if (c) x else false`、`if (c) true else x`、`when { a -> true; ...; else -> false }`） | これらは `AND`、`OR` を意味する |
| boolean の中置関数 `and`、`or`、`xor` | `&&`、`\|\|` |
| ガードの下の `/`、`%`、`CAST`（`it.qty != 0 && it.price!! / it.qty > 2`） | 除算なしで比較する: `it.qty > 0 && it.price!! > 2.0 * it.qty` |
| エンティティ `==` エンティティ（`it.customer == c`）（KC13） | `it.customer?.id == c.id`（予定: 識別子の等価） |
| コレクションのパス（`it.items...`、`@OneToMany`、`@ManyToMany`） | `exists()` のサブクエリ、または結合 |
| `item in it.items` | `exists()` のサブクエリ |
| ラムダの中で作ったコレクション（`listOf(1, 2)`、`setOf(...)`）（KC8） | 外で作ってキャプチャする（`groupBy` のキーは例外） |
| 範囲の `step` | 2 つの比較、またはキャプチャしたコレクション |
| キャプチャしたコレクションの中の `null` の要素 | null を取り除く。NULL の行がほしい場合は `\|\| it.x == null` を追加する |
| キャプチャした `NaN` / `Infinity` | クエリの前に値を確認する |
| `isNaN()`（KC14） | – |
| エンティティの値に対するその他の関数呼び出し（inline でない独自の関数、`operator` 関数）（KC11） | プロパティから条件を書く。または関数を `inline` にする |
| エンティティの値を使う文字列テンプレートや `+`（KC17） | 予定 |
| エンティティのパスに対する `it.tags["k"]`、`it.lines[0]` | – |

<a id="functions-and-strings"></a>
### 関数と文字列

| サポートしない（v0.1） | 代わりの書き方 |
|---|---|
| `isBlank`、`isNullOrBlank`（KC9） | `isNullOrEmpty()` または直接の比較 |
| カラムに対する `trim`、`coerceAtLeast`、`toLocalDate` などの標準ライブラリの関数（KC20） | 直接の比較、または DB 関数（`@SqlFunction`）。関数のマッピングは予定 |
| 日付関数（`YEAR(col) == 2024`） | 範囲: `it.updateAt!! >= from && it.updateAt!! < to` |
| ジェネリックな戻り値の型を持つ `@SqlFunction`（`fun <X> nvl(a: X, b: X): X`）、エンティティ、コレクション、`Unit` を返すもの | 次のどれか: `Double`、`Integer`（`Int`）、`Long`、`BigDecimal`、`String`、`LocalDate`、`LocalDateTime`、`Boolean` |
| Hibernate の組み込み関数と同じ名前の `@SqlFunction`（`COALESCE`、`ROUND`、`SYSDATE`、...） | 接頭辞かスキーマ名を使う（`PUBLIC.SQ_FN`） |
| デフォルト引数、`inline`、`vararg` を持つ `@SqlFunction` | 固定のパラメーターを持つ普通の関数 |
| DB 関数としての拡張関数、関数型インターフェースの定数、関数型のプロパティ | インターフェースの `companion object` + `@JvmStatic` |
| ユーザー定義の集約関数やウィンドウ関数 | 予定 |
| クエリのラムダの外や `where` の中での `Agg.*` の呼び出し | `select` / `having` の中で使う |

<a id="sort-and-paging"></a>
### ソートとページング

| サポートしない | 代わりの書き方 |
|---|---|
| ソートのラムダの中の条件やループ（`if (x) by.asc(...)`） | クエリを 2 つ作る。または `Direction` をキャプチャ値として渡す: `by.sort(o.price, dir)` |
| `orderBy` を 2 回、`orderBy` の後の `where`、`limit` の後の `orderBy` | すべてのキーを持つ 1 つの `orderBy`（これらはコンパイルできない） |
| ソートのキーとしてのエンティティ（`by.desc(o.customer)`） | カラム: `by.desc(o.customer?.name)`（コンパイルできない） |
| 結合のソートのラムダの中のプロパティ参照 | ラムダ |
| `orderBy` でのプロパティ参照（`orderBy(Order::price)`、まだ実装されていない） | ラムダ: `orderBy { it.price }` |
| `SortWhitelist`（まだ実装されていない） | `val dir = Direction.parse(text)` と `by.sort(key, dir)` |
| `limit` / `offset` と一緒のコレクションの fetch join | 先にページングし、その後でコレクションを読み込む |
| Spring の `Sort` の `ignoreCase()`（[12.5](12-spring-data-jpa.md#125-spring-sort-and-pageable)）、まだサポートしていない | `ignoreCase()` を使わないソート |
| 関数のキー（`lower(name)`） | 予定 |
| 集約のエイリアスによるソート（`by.desc(s.total)`） | 予定 |
| ソートのキーとしての boolean のエルビス（KC21） | キー `o.flag` と NULL の位置: `by.asc(o.flag, false)`（NULL が末尾） |

<a id="subqueries"></a>
### サブクエリ

| サポートしない | 代わりの書き方 |
|---|---|
| 1 行にならないスカラー比較での `first()` | 集約（`max`、`min`、`avg`、`sum`、`count`）か、`orderBy { }.limit(1)...first()` を使う |
| `limit` のないサブクエリの中の `orderBy` | 効果がないので削除される（WARN） |

<a id="select-and-group-by"></a>
### 選択とグループ化

| サポートしない | 代わりの書き方 |
|---|---|
| `select` / `having` の中の、`groupBy` のキーでない非集約の値 | `groupBy` に追加する |
| 派生テーブルや CTE の行の型としての普通のクラス | Kotlin の data class（または Java の record） |
| `groupBy` のキーとしての `arrayOf(...)` / `setOf(...)` | `listOf(...)`、`Pair`、`Triple` |
| select の値としての boolean のエルビス（KC21） | `it.flag` を select して、NULL は Kotlin で扱う |

<a id="update-insert-delete"></a>
### 更新、挿入、削除

| サポートしない（v0.1） | 代わりの書き方 |
|---|---|
| `where` のない `update` / `delete` | `.all()` を追加する（これがないと `execute()` はコンパイルできない） |
| 同じ文の中で設定した後でカラムを読む | 意図して古い値を使うか、update を 2 回実行する |
| 同じカラムを 2 回設定する | 1 回の代入 |
| 条件による値（`if (c) a else b`、`when`、値としてのトグル `!it.active`） | 予定（`CASE WHEN`）。`where` の違う update を 2 回使う |
| どのカラムを設定するかを選ぶための `set` のラムダの中の `if` | `set()` を何回か呼ぶ |
| `set` の中の `++`、`--`、`apply`、`with`、`also` | `it.cnt += 1`、直接の代入 |
| `by[o.cnt] += 1` | `by[o.cnt] = o.cnt + 1` または `by.set(o.cnt, o.cnt + 1)` |
| 代入のラムダの中のローカルな `val` | 式をそれぞれの場所に書く |
| 関連の `@Id` でないプロパティの設定（`it.customer?.name = x`） | `Customer` を直接更新する |
| 省略できないカラム（`@Id`、`nullable = false`、プリミティブ）への `null` | – |
| `@Version` のプロパティを自分で書く | lambdaQL が `update versioned` を使う |
| 値の中の文字列テンプレート | 予定 |
| 明示的な結合を使う `insert ... select` | 予定。v0.1 では many-to-one のパスを使う |
| 同時の挿入に対してアトミックな `onConflict { ... }.ignore()` | キー重複のエラーを捕捉する（`ignore()` は `not exists` を使う。[7.6](07-6-upsert.md) を参照） |
| `insert ... select` からの upsert | `values(list) { ... }` で 1 行ずつ upsert する |
| `limit(n)` のない update や delete での `orderBy { ... }` | `limit(n)` を加えるか、`orderBy` を取り除く（そうしないと `IllegalStateException`） |
| 1 行だけを変更すべき update や delete を、`execute() == 1` とロールバックで確認する | `executeOne()`: 先に id を読む（最大 2 つ）。2 つなら何も変更せずに `NonUniqueResultException` を投げる。1 つならその行を `id = :p and (条件)` で変更する。2 行以上を変更することはない（D41、Java でテスト済み） |
| `executeOne()` を `orderBy` や `limit` と一緒に使う | どちらか一方を使う（そうしないと `IllegalStateException`） |
| update や delete での `offset` | 予定なし（D42）。DML にあるのは `limit(n)` だけ。`where { }`、`orderBy { }`、`limit(n)` で対象の行を選ぶ |
| 複合キーのエンティティでの `limit` や `executeOne()` | まだ対応していない。先に id をクエリし、その後で `delete ... where { it.id in ids }` |
| トランザクションなしで Spring の共有 `EntityManager` を使う DML（lambdaQL は `@Transactional` を付けない、D41） | `@Transactional` か `TransactionTemplate` の中で呼ぶ |
| リポジトリのメソッド `deleteAll { ... }`（一括削除）での cascade や `@PreRemove` | Spring Data の `deleteAll(Iterable)` か `delete(entity)`（[12.4](12-spring-data-jpa.md#deleteallwhere-is-not-spring-datas-deleteall) を参照） |
| `returning` | DML の後でもう一度クエリする |
| DB の時刻（`current_timestamp`） | アプリの時刻を渡す（`LocalDateTime.now()` は値としてバインドされる） |
| `TABLE` の id ジェネレーターを使う `insert ... select` | `values { }`、または IDENTITY / SEQUENCE を使う |
| JOINED または TABLE_PER_CLASS のサブクラスへの `insert` | サポートしない（Hibernate 6.6.5 が失敗する） |

DML について、もう 1 つ注意があります。バルク DML は永続化コンテキストを通りません。Hibernate は先に flush しますが、
同じセッションの管理されているエンティティは、その後も古い値を持ったままです。`clear()`（または `refresh`）を呼んでください。
JPA のカスケードと `orphanRemoval` は適用されません。
DML の文にはトランザクションが必要です。`QueryBuilder(EntityManagerFactory)` は自分でトランザクションを開きます。`EntityManager` を使うビルダー（Spring の共有のものも含む）では呼び出し側のトランザクションが必要で、それがなければ SQL を送る前に失敗します。

<a id="procedures"></a>
### プロシージャ

| サポートしない | 代わりの書き方 |
|---|---|
| ラムダの中の、スタブ関数の 1 回の呼び出し以外のもの（ログ、入れ子の呼び出し、分岐） | 1 回の呼び出し。引数は先に計算する |
| 計算を含む名前付き引数（`calc(rate = r * 2, id = id)`） | 位置引数を使うか、値を先に計算する |
| OUT パラメーターを持つプロシージャへの `exec` | `call` |
| 引数の DB のデフォルト値 | すべての引数を渡す |
| スタブ関数の Kotlin のデフォルト引数 | すべての引数を渡す。デフォルトを削除する |
| Oracle のパッケージのオーバーロード、動的なプロシージャ名 | 予定 |
| Oracle の暗黙の結果（`DBMS_SQL.RETURN_RESULT`）、MariaDB のパッケージ、プロシージャの結果のページング | サポートしない |

<a id="108-caching-a-lambda-is-analyzed-once"></a>
## 10.8 キャッシュ: ラムダの解析は 1 回だけ

バイトコードを読むのは遅い部分なので、lambdaQL はそれを **ラムダごとに 1 回** だけ行います。

| 項目 | ルール |
|---|---|
| キャッシュするもの | 解析の結果: HQL のテンプレートとパラメーターの取得元のリスト。失敗もキャッシュする |
| キャッシュのキー | ラムダの実装クラスとメソッド（名前、シグネチャ、種類）、その型、エンティティのクラス、そして **ClassLoader** |
| サイズ | 上限あり（LRU）。そのため無限に大きくならず、クラスローダーもリークしない |
| キャッシュしないもの | バイトコードそのもの（もう一度読むのは安く、保持すると大きい） |
| キャプチャ値 | **決してキャッシュしない。** クエリを実行するたびに読んでバインドする |
| キャプチャしたオブジェクトに対する呼び出し（`filter.minPrice`、`this.minStatus`、`LocalDateTime.of(...)`） | クエリを実行する **たびに** アプリケーションで実行し、1 つのパラメーターとしてバインドする |
| キャプチャした `var` | クエリを実行するたびに読む |
| キャプチャ値だけの条件（`p != null && ...`） | 定数に畳み込まず、`:p1 is not null` として残すので、キャッシュは正しいまま |
| キャプチャ値としての `Direction` | バインド値で、毎回読む（構造はキャッシュされたまま） |
| DML | ラムダのクラスごとに 1 回解析する。`set()` の呼び出しの間のチェックには小さな要約を使う |

Kotlin のラムダの名前（`...$lambda$0`）は、コンパイラーのバージョンやソースの編集によって変わることがあります。
これは問題ありません。キャッシュのキーにはクラス、シグネチャ、クラスローダーも含まれていて、lambdaQL は名前の形式に依存しません。

プロトタイプでの測定（JDK 21）: 新しいラムダの最初の解析は約 **0.2 ms** かかります。
その後、`set()` を 2 回呼ぶ update のコストは、キャッシュの検索、値の取り出し、チェックを合わせて約 **0.1 から 0.2 µs** で、
200 万回実行した後でも同じです。目標: 同じラムダを 1,000 回呼んでも、バイトコードを読むのは
1 回だけ。Hibernate も HQL の文字列ごとにクエリプランをキャッシュします。

これが意味すること:

```kotlin
// 良い例: ラムダは 1 つで、値が変わる
.where { it.status == status }

// v0.1 ではできない: ラムダの中でクエリの形が変わる
.where { if (flag) it.status == 1 else it.active }

// 代わりに: ラムダを外で選ぶ（2 つのラムダは、それぞれ 1 回ずつ解析される）
val list = if (flag) {
    qb.from(Order::class.java).where { it.status == 1 }.toList()
} else {
    qb.from(Order::class.java).where { it.active }.toList()
}
```

<a id="109-tested-environment"></a>
## 10.9 テストした環境

| 項目 | バージョン |
|---|---|
| Kotlin | 2.x（設計は kotlinc 1.7.22、2.1.10、2.2.21、2.3.0、2.3.21 で確認） |
| Java（JVM） | 21 |
| Hibernate | 6.6（設計は 6.6.5 で確認）。HQL の拡張（たとえば `is distinct from`）を使うので、他の JPA プロバイダーは保証しない |
| データベース | H2 2.3、MariaDB 10.11、Oracle 23.26 (23ai) |
| Spring | Spring Boot 3.4.2 と Spring Data JPA 3.4.2（Spring モジュール、[12](12-spring-data-jpa.md)。Java のリポジトリのみ）。Spring Boot 4 はまだ検証していません |
| Kotlin の 1 行ずつの確認 | 今のところ H2 だけ。Kotlin のケースを MariaDB と Oracle で実行することは予定 |
| テストしていないもの | 2.3 より新しい Kotlin、Kotlin Multiplatform、PostgreSQL、SQL Server、Oracle 19c 以前（たとえば PL/SQL の `BOOLEAN` パラメーター） |

---

[Kotlin トラック](README.md) | 前: [9. Kotlin の注意点](09-kotlin-notes.md) | 次: [11. エラーと言語](../11-errors-and-languages.md) | [このページの Java 版](../java/10-rules-and-limits.md)
