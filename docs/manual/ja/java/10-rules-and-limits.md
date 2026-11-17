<a id="10-rules-and-limits-java"></a>
# 10. ルールと制限（Java）

> [!NOTE]
> **状況: 一部実装済み。**
> この章には lambdaQL の設計のルールをまとめています。ラムダがどのように SQL になるか、NULL がどう扱われるか、
> Java と SQL がどこで違うか、データベースごとにどこが違うか、何がサポートされないか、です。
> ルールは H2 2.3、MariaDB 10.11、Oracle 23ai（Hibernate 6.6.5）でプロトタイプを使って確認しました。
> 現在動くのは解析コア、`where`、結果の関数（`toList()`、`toSet()`、`first()`、`one()`、`count()`、`exists()`）、ソートとページングの中核部分、`SortSpec`、update/insert/delete/upsert（[7](07-dml.md)）、そして Spring Data JPA モジュール（[12](12-spring-data-jpa.md)）です（H2、MariaDB、Oracle でテスト済み）。
> サブクエリ（[5](05-subquery.md)）と集計を含む `select`（[6](06-select-group.md)）のルールも実装済みで、DML の中でも使えます。明示的な結合、派生テーブル、CTE（[6](06-select-group.md)）、プロシージャと DB 関数（[8](08-procedures-functions.md)）のルールも実装済みです。カラムに対する DB 関数の警告（[10.2](#db-functions-on-a-column)）は **まだ実装されていません**。ソートのルールにも、まだ実装されていないものがあります（[4.12](04-order-paging.md#412-rules-summary-s1-to-s14) を参照）。
> 全体の状況は [Java トラックの README](README.md#status) を参照してください。

[Java トラック](README.md) | [マニュアルのホーム](../README.md) | 前: [9. Java の注意点](09-java-notes.md) | 次: [11. エラーと言語](../11-errors-and-languages.md) | [このページの Kotlin 版](../kotlin/10-rules-and-limits.md)

共通ページ: [1. 概要](../01-overview.md) · [11. エラーと言語](../11-errors-and-languages.md)

---

<a id="contents"></a>
## 目次

- [10.1 基本ルール: ラムダはクエリである](#101-the-base-rule-a-lambda-is-a-query)
- [10.2 SARGable ルール（インデックスを使える SQL）](#102-sargable-rule-index-friendly-sql)
- [10.3 NULL のルール](#103-null-rules)
- [10.4 演算子のルール（R1〜R24）](#104-operator-rules-r1-to-r24)
- [10.5 Java と SQL の違い](#105-java-and-sql-differences)
- [10.6 データベースごとの違い](#106-database-differences)
- [10.7 サポートしないものと、代わりの書き方](#107-not-supported-and-what-to-write-instead)
- [10.8 キャッシュ: ラムダの解析は 1 回だけ](#108-caching-a-lambda-is-analyzed-once)
- [10.9 テストした環境](#109-tested-environment)

例の読み方:

- 例の下のコメントは、設計で決まっている場合に、lambdaQL が作る HQL を示します。
- ラムダの中に書いた定数（数値、文字列、boolean、`null`、enum 定数）は、リテラルとして HQL に書かれます。
  キャプチャした値と計算した値は常にバインドパラメーター（`:p1`、`:p2`、...）になります（D37）。
- エンティティ: `Order`（`id`、`price`、`status`、`active`、`description`、`customer`、`updateAt`、`items`）、
  `Customer`（`id`、`name`、`grade`）、`Item`（`order`、`price`、`qty`）。

---

<a id="101-the-base-rule-a-lambda-is-a-query"></a>
## 10.1 基本ルール: ラムダはクエリである

lambdaQL は **ラムダを決して実行しません**。バイトコードを読んで HQL を書きます。
ラムダは Java の構文で書いたクエリです。そのため:

1. `o.getCustomer().getName()` のようなパスは **カラムを指します**。呼び出しの連鎖ではありません。
   lambdaQL は customer が null かどうかを確認しません（D20）。`where` の中ではパスはデフォルトで内部結合なので、customer のない注文は選ばれません（D36）。
   customer がなくても条件が true になりうるなら（null ガード）、パスは `left join` になるので、結果は Java と同じになります。
   それらの注文を探すには、関連そのものを比較します: `o.getCustomer() == null`。
2. NULL は **書いた場所でだけ** 扱われます（`== null`、`!= null`）（D22）。
   それ以外の NULL は SQL のルールに従います。条件は UNKNOWN になり、その行は選ばれません。
3. Java のメモリのルール（オブジェクトの同一性、ボクシングのキャッシュ、文字列のインターン、NPE）は当てはまりません。
   オブジェクト同士の `==` は **値の等価** を意味します（D12）。
4. **ルールがない書き方について、lambdaQL は推測しません。** コード `[LQL-xxxx]` とソースの行を含む
   分かりやすいエラーを投げます。「たぶん正しい」SQL を作ることはありません。

<a id="102-sargable-rule-index-friendly-sql"></a>
## 10.2 SARGable ルール（インデックスを使える SQL）

「SARGable」とは、データベースがその条件にインデックスを使えるという意味です。
これは lambdaQL の基本ルールです（設計のセクション 3.9）。

| # | ルール |
|---|---|
| 1 | **カラム側に関数、演算子、`CASE` を付けません。** 値を変える必要があるなら、バインドする前に Java でパラメーターのほうを変えます（たとえば `Locale.ROOT` で小文字にする） |
| 2 | 2 つの書き方が同じ意味なら、単純な比較と `AND` / `OR` / `NOT` だけの書き方を選びます。正しい 3 値論理が最優先で、その中で SARGable な書き方を選びます |
| 3 | 意味の上でどうしてもカラムに関数が必要な場合（たとえば大文字小文字の無視）、lambdaQL は起動時に DB のメタデータを読み、DB がすでに持っている手段を使います。カラムの照合順序、関数インデックス、言語インデックスです。手段がなければ、起動時にカラムごとに WARN をログに出します（strict モード: エラー） |
| 4 | 各出力ルールは H2、MariaDB、Oracle で `EXPLAIN` を使って確認します |

<a id="examples"></a>
### 例

**大文字小文字の無視**（D24）。設計では、このルールを Kotlin の `ignoreCase` 関数に使います。カラムに対する Java の文字列関数は予定で、同じルールに従います。

| DB にあるもの | lambdaQL が書くもの | こうは書かない |
|---|---|---|
| 大文字小文字を区別しない照合順序（MariaDB の `_ci`） | `e0.name = :p1` | `lower(e0.name) = lower(:p1)` |
| Oracle の関数インデックス `LOWER(name)` | `lower(e0.name) = :p1`（`:p1` は Java で小文字にする） | `lower(e0.name) = lower(:p1)` |
| Oracle の言語インデックス `NLSSORT(... BINARY_CI)` | `e0.name collate binary_ci = :p1` | |
| 手段がない | 起動時に WARN（strict モード: エラー） | |

**片方が NOT NULL のときの null-safe 比較**

```java
.where(o -> o.getDescription() == text)   // クエリの実行時に text は null ではない
// e0.description = :p1                       （"is not distinct from" ではない）
.where(o -> o.getDescription() != text)
// (e0.description <> :p1 or e0.description is null)
```

Oracle では `is not distinct from` は `decode(...)` になり、インデックスを使えません。
そのため、片方が NOT NULL だと分かっている場合（`@Id`、`nullable = false`、プリミティブのプロパティ、
null ではないバインド値）、lambdaQL は普通の `=` を書きます。

**2 つの boolean 式の比較**（D25）

```java
.where(o -> o.isActive() == (o.getStatus() > c))
// (e0.active = true and e0.status > :p1) or (e0.active = false and e0.status <= :p1)
```

`CASE` の書き方（`e0.active = case when ... end`）は正しい行を返しますが、フルスキャンが必要になります。
そのため lambdaQL はこれを使いません。

**サブクエリを使う NOT IN** は `NOT EXISTS` として書かれます（アンチジョインで、NULL に対して安全です。10.3 を参照）。

<a id="what-the-measurements-showed"></a>
### 測定で分かったこと

MariaDB 10.11 と Oracle 23ai、20,000〜50,000 行:

| 書き方 | MariaDB | Oracle | OK? |
|---|---|---|---|
| `_ci` カラムに対する `name = 'abc'` | インデックス検索 | – | はい |
| `LOWER(name) = LOWER('abc')` | インデックスのフルスキャン（19,791 行） | テーブルのフルスキャン（関数インデックスなし） | **いいえ** |
| `lower(name) = :p_lowered` + 関数インデックス | – | インデックスの範囲スキャン（`LIKE 'ab%'` も） | はい |
| `name COLLATE BINARY_CI = :p` + 言語インデックス | – | インデックスの範囲スキャン | はい |
| boolean の `==` を `AND`/`OR` で | 範囲（350 行） | インデックスの範囲スキャン 2 回 + ビットマップ OR | はい |
| boolean の `==` を `CASE` で | インデックスのフルスキャン（49,648 行） | テーブルのフルスキャン | **いいえ** |

<a id="planned-items-under-the-same-rule"></a>
### 同じルールに従う予定の項目

| 書き方 | 予定の SQL |
|---|---|
| 「`updateAt` の年が 2024」のような日付関数 | `e0.updateAt >= :from and e0.updateAt < :to` |
| 関数のキー（`lower(name)`）でのソート | 先にアクセス手段を確認し、なければ WARN |

<a id="db-functions-on-a-column"></a>
### カラムに対する DB 関数

カラム側の DB 関数（`OrderFns.discount(o.getPrice()) > 10`）は通常のインデックスを使えません。
lambdaQL は **WARN** をログに出す予定です（strict モード: エラー）。例外: Oracle では、関数が
`DETERMINISTIC` で、同じ式の関数ベースのインデックスがあれば WARN は出ません。MariaDB では、
`NOT DETERMINISTIC`（デフォルト）の関数は、引数がパラメーターだけでもフルスキャンになります
（`o.getPrice() > OrderFns.discount(rate)`）。そのため、その場合も lambdaQL は警告します。
[8. プロシージャと DB 関数](08-procedures-functions.md) を参照してください。
この警告は **まだ実装されていません**。

<a id="103-null-rules"></a>
## 10.3 NULL のルール

| ラムダ | SQL | NULL の動作 |
|---|---|---|
| `o.getId() == 10L`（アンボクシング、片方がプリミティブ） | `e0.id = 10` | NULL: UNKNOWN で、行は選ばれない（Java なら NPE を投げる） |
| `o.getId() == p`（2 つのオブジェクト: `Long`、`String`、enum、...） | null-safe の `is not distinct from`、または片方が NOT NULL なら `=` | `null == null` は true |
| `o.getDescription().equals(p)` | `e0.description = :p1` | description が NULL: UNKNOWN（Java なら NPE を投げる） |
| `Objects.equals(o.getDescription(), p)`、`"x".equals(o.getDescription())`、`Boolean.TRUE.equals(o.getActive())` | null-safe | Java と同じ |
| `!Boolean.TRUE.equals(o.getActive())` | `e0.active is distinct from true` | Java と同じ。3 つの DB すべてで動く |
| `x == null`、`x != null` | `is null`、`is not null` | 2 値 |
| NULL になれないカラム（id、プリミティブ、`nullable = false`、パスに関連のステップがない）での `o.getId() == null` | `1=0`。比較と並ぶとチェックは取り除かれる: `o.getId() != null && o.getId() == id` は `e0.id = :p1`（テスト済み） | カラムに NULL はない。関連を通るパス（`o.getCustomer().getId()`）ではチェックが残る |
| `!`、`&&`、`\|\|` | `NOT`、`AND`、`OR` | SQL の 3 値論理 |
| `where` の中の `o.getCustomer().getName()` | `j1.name` + `join e0.customer j1`（デフォルトで内部結合） | customer がない: 注文は選ばれない |
| `o.getCustomer() == null \|\| o.getCustomer().getName().equals(p)` | `e0.customer is null or j1.name = :p1` + `left join e0.customer j1` | customer がない: Java と同じく選ばれる |
| `orderBy` か `select` だけで使う `o.getCustomer().getName()` | `j1.name` + `left join e0.customer j1` | customer がない: カラムは NULL |
| `o.getCustomer() == null`、`o.getCustomer() != null` | `e0.customer is null`、`e0.customer is not null`（FK カラム、結合なし） | customer のない注文 / customer がある注文 |
| `o.getCustomer().getId()` | `e0.customer.id`（FK カラム、結合なし） | customer がない: NULL |
| `ids.contains(o.getId())` | `e0.id in (:p1)` | id が NULL: 選ばれない |
| `!ids.contains(o.getId())` | `e0.id not in (:p1)`（id は NULL にならない）。NULL になりうるカラム: `(x is null or x not in (:p1))` | NULL の値（NULL になりうるカラム）: 選択される。Java と同じ |
| `!...contains(x)` を使うサブクエリ | `not exists (...)` | サブクエリが NULL を返しても正しい |
| `Agg.sum`、`avg`、`min`、`max` | `sum(...)` ... | 行がない場合、またはすべての値が NULL の場合は NULL |
| `Agg.count()` | `count(*)` | 行がない場合は 0 |

<a id="captured-null-values"></a>
### キャプチャした null 値

| キャプチャ値の場所 | `null` を使えるか |
|---|---|
| null-safe の書き方: オブジェクト同士の `==` / `!=`、`Objects.equals`、`"x".equals(col)` | はい |
| 同じ値に対する null ガードの下: `p == null \|\| o.getStatus() > p` | はい。ガードはクエリの実行時に Java の中で決まる: `p == null` なら絞り込みなし、それ以外の値なら `e0.status > :p1` |
| `p.equals(col)` のレシーバー、`col.equals(p)` の引数、アンボクシングの比較、`<` `<=` `>` `>=` | **いいえ。** 値をバインドするときにエラー（Java なら NPE を投げるか、常に false） |
| `in` で使う、`null` 要素を含むコレクション | **いいえ。** バインドするときにエラー |
| `NaN`、`Infinity` | **いいえ。** 常にエラー |

<a id="104-operator-rules-r1-to-r24"></a>
## 10.4 演算子のルール（R1〜R24）

これは設計にあるルール表（セクション 3.6）の短縮版です。

| # | ラムダの書き方 | 意味 |
|---|---|---|
| R1 | プリミティブの比較、または片方がプリミティブ（`o.getId() == 10L`、`o.getStatus() > p`） | `=`、`<>`、`<`、... SQL の NULL のルール |
| R2 | オブジェクトの `==` / `!=`（`Long`、`String`、`Boolean`、enum） | null-safe の値の等価。オブジェクトの同一性ではない |
| R3 | `x == null`、`x != null` | `IS NULL`、`IS NOT NULL` |
| R4 | `a.equals(b)` | `a = b`、SQL の NULL のルール |
| R5 | `Objects.equals(a, b)`、`"x".equals(a)`、`Boolean.TRUE.equals(a)` | null-safe の値の等価 |
| R6 | `!`、`&&`、`\|\|` | `NOT`、`AND`、`OR`。SQL の 3 値論理 |
| R7 | 文字列の値の比較 | **DB の照合順序** に従う（大文字小文字、末尾の空白）。Oracle は `''` を NULL として扱う |
| R8 | エンティティ `==` エンティティ | 予定: 識別子の等価。v0.1 にはない |
| R9 | キャプチャ値 | 実行時の値を持つバインドパラメーター |
| R10 | プロパティへのアクセス | getter の呼び出し。JPA 属性にマッピングされる |
| R11〜R23 | Kotlin のルール | [このページの Kotlin 版](../kotlin/10-rules-and-limits.md) を参照 |
| R24 | 関連のパス（`o.getCustomer().getName()`） | カラム。to-one の関連: where、order by、select で共有するエイリアス。`where` ではデフォルトで内部結合、null ガードが関連のない行を残すときは `left join`（D36）。order by か select だけで使うなら `left join`。Embedded: 同じテーブル。ターゲットの `@Id` で終わる、または関連を `null` と比較する: FK カラム、結合なし。コレクション: エラー |

その他のルール:

- `x.compareTo(y) > 0`、`isBefore`、`isAfter` は `x > y`、... になります。
- ラムダの中に書いた定数（コンパイル時の `static final` 定数も）はリテラルです: `o.getPrice() > 100` は `e0.price > 100.0`、`"O'Brien"` は `'O''Brien'` になります。キャプチャした値と計算した値（`new BigDecimal("1.5")`）はバインドパラメーターです（D37）。
- javac は `!` の位置を動かすことがあります（ド・モルガン、二重否定）。これで結果が変わることはありません。
  lambdaQL は各条件をちょうど 1 回だけ残し、ロジックを勝手に「単純化」しません。
- Java では常に true になる条件（`x > 5 || !(x > 5)`）も単純化しません。
  SQL では `x` が NULL の行が落ちます。これが SQL のルールです。
- IntelliJ は `o.getId() == p` について「Number objects compared using `==`」と警告します。lambdaQL のラムダでは、
  これは意図した書き方（値の等価）です。必要なら `@SuppressWarnings("NumberEquality")` を使ってください。

どの Java の型が R1 になり、どれが R2 になるか: [9.3](09-java-notes.md#93-equality-of-boxed-values) を参照してください。

<a id="105-java-and-sql-differences"></a>
## 10.5 Java と SQL の違い

これらの違いは SQL そのものから来ています。コンパイラーの問題ではありません。
「G」の番号は設計（セクション 2.7）での ID です。

| # | 違い | lambdaQL の動作 |
|---|---|---|
| G1 | `status` が NULL のとき、Java では `!Objects.equals(status, 1)` は true。SQL では `status <> 1` は UNKNOWN | null-safe（`is distinct from`）で出力し、Java と同じ行になる |
| G2 | `!Boolean.TRUE.equals(active)` と `NOT active` | `active is distinct from true` を出力する（3 つの DB すべてで動く） |
| G3 | NULL パラメーターでの `name = ?` は 0 行を返す | null-safe の書き方は `is [not] distinct from` を使う |
| G4 | `status NOT IN (1, NULL)` は常に 0 行を返す | キャプチャしたコレクションの `null` はエラー。サブクエリの `NOT IN` は `NOT EXISTS` |
| G5 | Java は NPE を投げる（アンボクシング、`o.getName().startsWith(...)`）が、SQL はただ行を返す | ドキュメントに記載: エンティティの NULL は SQL のルールに従う |
| G6 | `NaN`: H2 は NaN を最大の値としてソートし、`NaN = NaN` は true。MariaDB は保存できない。Oracle は `BINARY_DOUBLE` でだけ保存できる | キャプチャした NaN や Infinity はエラー。保存された NaN は DB に従う |
| G7 | `float` カラムと `double` の値の比較: 結果が DB ごとに違う | float/double について **Java とビット単位で同じ結果は約束しません**。lambdaQL は `CAST` を追加しません。カラムのマッピングを選んでください（たとえば Oracle の `binary_float` / `binary_double`） |
| G9 | `x > 5 \|\| !(x > 5)` は NULL の行を落とす | 単純化せず、書いたとおりに出力する |
| G10 | SQL は評価の順序を約束しない: `qty != 0 && total / qty > 2` でもゼロ除算が起こりうる | ガードの下の `/`、`%`、`CAST` は v0.1 では拒否する（D13） |
| G11 | 文字列: MariaDB の `_ci` は大文字小文字と末尾の空白を無視する。Oracle は `''` を NULL として保存する。Oracle の `NLS_SORT` は JVM のロケールに従う | ドキュメントに記載: 文字列の比較とソートは DB に従う。Oracle では `NLS_SORT=BINARY` を設定する（セッションの初期化、または `-Duser.language=en`）。Oracle では `"".equals(x)` と `isEmpty()` の結果が違う |
| G12 | オブジェクトに対する Java の `==` は同一性（`Long` の 128 以上や `new String` では false） | lambdaQL は `==` を値の等価と定義する |
| G13 | 0/1 以外の値を持つ古い boolean カラム（MariaDB の `TINYINT(1)`、Oracle の `NUMBER(1)`）は読み方が違う | Hibernate 自身の DDL（`bit(1)`、Oracle 23 の `boolean`）なら問題ない。古いスキーマには注意する |
| G14 | Oracle 23 のバグ: 複合条件に対する `WHERE (a AND b) = false` / `IS FALSE` / `IS NULL` で行が失われる | lambdaQL は複合条件を boolean のテストで包まない。否定は常に `NOT(...)` |
| G15 | Oracle の `CHAR` カラムは `String` のバインドと一致しない | ドキュメントに記載: エンティティのマッピングに注意する |

その他の違い:

| 違い | Java | SQL | lambdaQL |
|---|---|---|---|
| `BigDecimal` の等価 | `equals` はスケールも比べる（`1.0` と `1.00` は false） | `=` は値を比べる | 値の等価 |
| 整数の除算 | `5 / 2` は `2` | MariaDB は `2.5` を返す | DB に依存。最終的なルールは未定で、v0.1 では拒否するかもしれない |
| `BigDecimal` の除算と関数の結果 | Java のスケールのルール | DB がスケールを決める（たとえば関数の結果では H2 は 1、MariaDB と Oracle は 0） | ドキュメントに記載 |
| Unicode の大文字小文字の無視（`ſ`、`İ`） | Java のルール | DB の照合順序 | DB に従う |
| 文字列の長さ | UTF-16 の単位 | H2 は UTF-16 の単位、MariaDB はコードポイント | ドキュメントに記載 |
| NULL との文字列の連結 | `"null"` というテキスト | NULL（Oracle: `NULL \|\| 'x'` は `'x'`） | 文字列の連結は予定で、v0.1 にはない |
| ラムダの中の `LocalDateTime.now()` | アプリの時刻 | – | **アプリの時刻** としてバインドする。DB の時刻（`current_timestamp`）はサポートしない |
| `Set` の中の等しい要素 | `equals` / `hashCode` が決める | 主キーで行を区別する | Set が行数より小さいと、`toSet()` と `findSet()` は `IllegalStateException` を投げる（エンティティの `equals` / `hashCode` が違う行を等しいと判断する場合や、`@Id` が一意でない場合。たとえばビュー）。`toList()` を使う（D42、テスト済み） |

<a id="106-database-differences"></a>
## 10.6 データベースごとの違い

lambdaQL は多くの違いを隠します。知っておく必要があるものもあります。

<a id="sorting-and-paging"></a>
### ソートとページング

| 項目 | H2 | MariaDB | Oracle | lambdaQL |
|---|---|---|---|---|
| NULL の位置、`ASC` | 先頭 | 先頭 | **末尾** | すべての DB で `ASC` は NULLS FIRST（D16） |
| NULL の位置、`DESC` | 末尾 | 末尾 | **先頭** | すべての DB で `DESC` は NULLS LAST（D16） |
| `NULLS FIRST/LAST` の構文 | あり | **なし**（構文エラー） | あり | MariaDB では Hibernate が `CASE` でエミュレートする: ソートに **インデックスを使えない** |
| ページングの SQL | `offset ? rows fetch first ? rows only` | `limit ?,?` | `offset ? rows fetch first ? rows only` | Hibernate に渡す（`setFirstResult` / `setMaxResults`） |
| 等しい行の順序 | – | – | ランダム | ページングでキーに id が含まれていない場合、lambdaQL はルートの `@Id asc` を追加する |
| 文字列の順序 | 大文字小文字を区別 | `_ci` | `NLS_SORT`（JVM のロケール） | DB に従う |
| `order by` の暗黙のパス | inner join（行が失われる） | 同じ | 同じ | lambdaQL は `left join`（または同じパスの `where` の結合）を使う |

lambdaQL は、NULL になりうるすべてのキーについて HQL に `nulls first` / `nulls last` を書きます。それがデータベースのデフォルトと同じなら、Hibernate が省きます。
デフォルト（`ASC` = NULLS FIRST、`DESC` = NULLS LAST）は H2 と MariaDB と同じなので、そこでは SQL を追加する
必要はありません。`NULLS` 句が付くのは Oracle だけです。NULL の位置を変えるには、
`by.asc(key, false)`（NULL が末尾）か `by.desc(key, true)`（NULL が先頭）を使います（D35）。MariaDB ではこれらは `CASE` のエミュレーションを使います。

<a id="conditions-and-values"></a>
### 条件と値

| 項目 | H2 | MariaDB | Oracle |
|---|---|---|---|
| `is not distinct from` | ネイティブ | `<=>`（インデックスを使う） | `decode(...)`（インデックスなし）。そのため lambdaQL は可能なら `=` を優先する |
| `is [not] true`、`coalesce(boolean, false)` | 動く | 動く | **ORA-00932**。lambdaQL はこれらを書かない |
| 空文字列 `''` | 値 | 値 | **NULL** |
| 大文字小文字と末尾の空白 | 厳密 | `_ci`: 無視 | 厳密（ソートには `NLS_SORT`） |
| `NaN` | 保存できる、最大の値 | 保存できない | `BINARY_DOUBLE` のみ |
| `float` カラム | REAL リテラルのルール | double に拡張 | Hibernate は `Float` を `float(24)`（10 進数）にマッピングする |
| 整数の `/` | 整数 | **小数**（`5/2 = 2.5`） | – |
| 大きな `IN` リスト（1,500 個の値） | 動く | 動く | 23ai では動く（Oracle 22 以前の 1,000 個の制限は測定していない） |

<a id="subqueries"></a>
### サブクエリ

| 項目 | 何が起こるか | lambdaQL |
|---|---|---|
| `limit` のないサブクエリの `order by` | Oracle: 構文エラー（ORA-00907）。MariaDB: 無視される | WARN を出して取り除く |
| MariaDB の `IN (... limit n)` | サポートされない（エラー 1235） | 自動的に包む: `in (select x.id from (... limit n) x)` |
| MariaDB の `lateral` | 結果が間違う（エミュレーション） | `row_number() over (...)` で書き直す（まだ実装されていない） |

<a id="update-insert-delete"></a>
### 更新、挿入、削除

| 項目 | 何が起こるか | lambdaQL |
|---|---|---|
| `SET a = ..., b = a + 1`（設定した後にカラムを読む） | MariaDB は **新しい** 値を読み、H2 と Oracle は古い値を読む | 解析時にエラー（U3） |
| DML での関連のパス（`o.getCustomer().getName()`） | Hibernate は DB ごとに違う結合を作り、customer がない行が **失われる** | lambdaQL は相関スカラーサブクエリ（`where` の中と SET の中）を書く。`where` では、選択が内部結合するパスに `customer.id is not null` も加えるので、DML はクエリと同じ行に当たる（U9、テスト済み） |
| 同じテーブルへのサブクエリを使う `DELETE` | MariaDB のエラー 1093 | 派生テーブルで包む（U17。まだ実装されていない） |
| 同じ enum 値を STRING と ORDINAL のカラムに 1 回だけバインドする | H2 と MariaDB は **エラーを出さずに間違った値** を保存する | 場所ごとに 1 つのパラメーターを使い、共有しない（U7） |
| `values(list, ...)` の多数の行 | Oracle: バインド数の上限 65,535（ORA-01745）。それよりずっと前からとても遅い | 分割する: 1 文あたり最大 500 個の値、すべて 1 つのトランザクションで実行（U13） |
| `order by ... limit` を使う `update` / `delete` | MariaDB は `in` サブクエリの中の `limit`（エラー 1235）と、対象テーブルへのサブクエリ（エラー 1093）を拒否する | `e0.id in (select t.id from (select x0.id as id ... order by ... limit n) t)`: 派生テーブル `t` は 3 つのデータベースすべてで動く。内側の select は select の結合を使う（D36）ので、行は select と同じ（テスト済み） |
| `on conflict` / upsert | H2 と Oracle: `merge`。MariaDB: `insert ... on duplicate key update` で、更新した行は 2 と数える。Hibernate の `do nothing` は H2 と Oracle で失敗する | `upsert` と `onConflict(...).update(...)` は Hibernate の形を使う。`ignore()` は `insert ... select ... where not exists` として書く。行数はそのまま返す（D40、テスト済み） |

<a id="procedures-and-functions"></a>
### プロシージャと関数

| 項目 | H2 | MariaDB | Oracle |
|---|---|---|---|
| OUT / INOUT パラメーター | **なし** | あり | あり |
| 複数の結果セット | なし | あり | あり（REF CURSOR） |
| プロシージャが影響した行数 | – | – | 常に -1。そのため lambdaQL は提供しない。OUT パラメーターを使う |
| boolean のパラメーターと結果 | – | あり | 23ai 以降 |
| 仮想カラム / 関数インデックスでのストアドファンクション | – | 不可（エラー 1901） | 可能（関数ベースのインデックス） |

H2 には OUT パラメーターがないので、プロシージャの機能は MariaDB と Oracle で確認します。

<a id="107-not-supported-and-what-to-write-instead"></a>
## 10.7 サポートしないものと、代わりの書き方

このリストのすべての書き方について、lambdaQL は分かりやすいエラーを投げます。「予定」は今後のバージョンという意味です。

<a id="lambda-form-and-loading"></a>
### ラムダの書き方と読み込み

| サポートしない | 代わりの書き方 |
|---|---|
| 匿名クラス、`Serializable` でない関数型インターフェース（`java.util.function.Predicate`） | ラムダリテラルかメソッド参照 |
| ローカル変数、ループ、`switch`、`try` を含むブロックラムダ | 1 つの式。値はラムダの外で計算する |
| キャプチャした変数への書き込み | ラムダの外で計算する（Java でも、キャプチャするローカル変数は実質的に final である必要がある） |
| ECJ（Eclipse のコンパイラー）でコンパイルしたラムダ | 保証しない。テストは javac だけ |
| 同梱の ASM が知らない、より新しい JDK 向けにコンパイルしたラムダ | ASM を更新する（lambdaQL は ASM をシェーディングしない） |

<a id="conditions"></a>
### 条件

| サポートしない | 代わりの書き方 |
|---|---|
| 一般的な三項演算子 `c ? x : y`、値の三項演算子 `o.getId() == (c ? p1 : p2)` | `&&` / `\|\|`。値はラムダの外で選ぶ。`CASE WHEN` は予定 |
| （サポートするもの: `c ? x : false`、`c ? true : x`、`c ? false : true`） | これらは `AND`、`OR`、`NOT` を意味する |
| 短絡評価しない `&`、`\|`、`^` | `&&`、`\|\|` |
| ガードの下の `/`、`%`、`CAST`（`qty != 0 && total / qty > 2`） | 除算を使わずに比較する: `qty > 0 && total > 2 * qty` |
| エンティティ `==` エンティティ（`o.getCustomer() == c`） | `o.getCustomer().getId() == c.getId()`（予定: 識別子の等価） |
| コレクションのパス（`o.getItems()...`、`@OneToMany`、`@ManyToMany`） | `exists()` サブクエリ、または結合 |
| `o.getItems().contains(x)` | `exists()` サブクエリ |
| ラムダの中で作るコレクション（`List.of(1, 2)`） | 外で作ってキャプチャする（`groupBy` のキーは例外） |
| キャプチャしたコレクションの `null` 要素 | null を取り除く。NULL の行が必要なら `\|\| x == null` を追加する |
| キャプチャした `NaN` / `Infinity` | クエリの前に値を確認する |
| `isNaN()` | – |
| エンティティの値に対するその他のメソッド呼び出し（`o.items(...)`、自分で作ったメソッド） | カラムから条件を書く |
| 文字列の連結（`o.getDescription() + "_x"`、`"A" + code` も） | 文字列はラムダの外で組み立てる。カラムとの連結は予定 |
| ラムダの中のストリームや他のラムダ | キャプチャしたコレクションに対する `contains`、またはサブクエリ |

<a id="functions-and-strings"></a>
### 関数と文字列

| サポートしない（v0.1） | 代わりの書き方 |
|---|---|
| エンティティの値に対する Java の `String` メソッド（`startsWith`、`contains`、`trim`、`toUpperCase`） | 予定 |
| 日付関数（`YEAR(col) == 2024`） | 範囲: `o.getUpdateAt() >= from && o.getUpdateAt() < to` |
| 戻り値の型がジェネリック（`<X> X nvl(X, X)`）、エンティティ、コレクション、`void` の `@SqlFunction` | 次のどれか: `Double`、`Integer`、`Long`、`BigDecimal`、`String`、`LocalDate`、`LocalDateTime`、`Boolean` |
| Hibernate の組み込み関数（`COALESCE`、`ROUND`、`SYSDATE`、...）と同じ名前の `@SqlFunction` | 接頭辞かスキーマ名を使う（`PUBLIC.SQ_FN`） |
| DB 関数としての関数型インターフェースの定数、ミックスイン | インターフェースの `static` メソッド |
| `@SqlFunction` のない、エンティティの値に対する自分の static メソッド | `@SqlFunction` を付けた DB 関数、またはカラムから書いた条件 |
| ユーザー定義の集約関数やウィンドウ関数 | 予定 |
| クエリのラムダの外や `where` での `Agg.*` の呼び出し | `select` / `having` で使う |

<a id="sort-and-paging"></a>
### ソートとページング

| サポートしない | 代わりの書き方 |
|---|---|
| ソートのラムダの中の条件やループ（`if (x) by.asc(...)`） | 2 つのクエリを作るか、`Direction` をキャプチャ値として渡す: `by.sort(o.getPrice(), dir)` |
| `orderBy` を 2 回、`orderBy` の後の `where`、`limit` の後の `orderBy` | すべてのキーを持つ 1 つの `orderBy`（これらはコンパイルできません） |
| ソートキーとしてのエンティティ（`by.desc(o.getCustomer())`） | カラム: `by.desc(o.getCustomer().getName())`（コンパイルできません） |
| 結合のソートのラムダでのメソッド参照 | ラムダ |
| `SortWhitelist`（まだ実装されていない） | `Direction dir = Direction.parse(text)` と `by.sort(key, dir)` |
| `limit` / `offset` を使うコレクションのフェッチ結合 | 先にページングし、その後でコレクションを読み込む |
| Spring の `Sort` の `ignoreCase()`（[12.5](12-spring-data-jpa.md#125-spring-sort-and-pageable)）、まだサポートしていない | `ignoreCase()` を使わないソート |
| 関数のキー（`lower(name)`） | 予定 |
| 集約のエイリアスでのソート（`by.desc(s.total())`） | 予定 |

<a id="subqueries"></a>
### サブクエリ

| サポートしない | 代わりの書き方 |
|---|---|
| 1 行ではないスカラー比較での `first()` | 集約（`max`、`min`、`avg`、`sum`、`count`）か `orderBy(...).limit(1)...first()` を使う |
| `limit` のないサブクエリでの `orderBy` | 効果がないので取り除かれる（WARN） |

<a id="select-and-group-by"></a>
### 選択とグループ化

| サポートしない | 代わりの書き方 |
|---|---|
| `select` / `having` の中の、`groupBy` のキーではない集約以外の値 | `groupBy` に追加する |
| 派生テーブルや CTE の行の型としての普通の DTO クラス | Java の record |
| `groupBy` のキーとしての `Set.of(...)` | `List.of(...)` |

<a id="update-insert-delete"></a>
### 更新、挿入、削除

| サポートしない（v0.1） | 代わりの書き方 |
|---|---|
| `where` のない `update` / `delete` | `.all()` を追加する（ないと `execute()` はコンパイルできません） |
| 同じ文の中で、設定した後にカラムを読む | 意図して古い値を使うか、更新を 2 回実行する |
| 同じカラムを 2 回設定する | 代入は 1 回 |
| 条件付きの値（`c ? a : b`、切り替えの `!o.isActive()`） | 予定（`CASE WHEN`）。`where` を変えた 2 回の更新を使う |
| どのカラムを設定するかを選ぶための、`set` ラムダの中の `if` | `set()` を複数回呼ぶ |
| 関連の `@Id` ではないプロパティの設定（`o.getCustomer().setName(x)`） | `Customer` を直接更新する |
| 省略できないカラム（`@Id`、`nullable = false`、プリミティブ）への `null` | – |
| `@Version` のプロパティを自分で書く | lambdaQL が代わりに `update versioned` を使う |
| 値での文字列の連結 | 予定 |
| 明示的な結合を使う `insert ... select` | 予定。v0.1 では many-to-one のパスを使う |
| 同時の挿入に対してアトミックな `onConflict(...).ignore()` | キー重複のエラーを捕捉する（`ignore()` は `not exists` を使う。[7.6](07-6-upsert.md) を参照） |
| `insert ... select` からの upsert | `values(list, ...)` で 1 行ずつ upsert する |
| `limit(n)` のない update や delete での `orderBy(...)` | `limit(n)` を加えるか、`orderBy` を取り除く（そうしないと `IllegalStateException`） |
| 1 行だけを変更すべき update や delete を、`execute() == 1` とロールバックで確認する | `executeOne()`: 先に id を読む（最大 2 つ）。2 つなら何も変更せずに `NonUniqueResultException` を投げる。1 つならその行を `id = :p and (条件)` で変更する。2 行以上を変更することはない（D41、テスト済み） |
| `executeOne()` を `orderBy` や `limit` と一緒に使う | どちらか一方を使う（そうしないと `IllegalStateException`） |
| update や delete での `offset` | 予定なし（D42）。DML にあるのは `limit(n)` だけ。`where`、`orderBy(...)`、`limit(n)` で対象の行を選ぶ |
| 複合キーのエンティティでの `limit` や `executeOne()` | まだ対応していない。先に id を取得し、その後 `delete ... where ids.contains(id)` |
| トランザクションなしで Spring の共有 `EntityManager` を使う DML（lambdaQL は `@Transactional` を付けない、D41） | `@Transactional` か `TransactionTemplate` の中で呼ぶ |
| リポジトリのメソッド `deleteAll(where)`（一括削除）での cascade や `@PreRemove` | Spring Data の `deleteAll(Iterable)` か `delete(entity)`（[12.4](12-spring-data-jpa.md#deleteallwhere-is-not-spring-datas-deleteall) を参照） |
| `returning` | DML の後にもう一度クエリを実行する |
| DB の時刻（`current_timestamp`） | アプリの時刻を渡す（`LocalDateTime.now()` は値としてバインドされる） |
| `TABLE` の ID ジェネレーターを使う `insert ... select` | `values(...)`、または IDENTITY / SEQUENCE を使う |
| JOINED や TABLE_PER_CLASS のサブクラスへの `insert` | サポートしない（Hibernate 6.6.5 が失敗する） |

DML についての注意: 一括 DML は永続化コンテキストを通りません。Hibernate は先にフラッシュしますが、
同じセッションの管理されたエンティティは、その後も古い値を持ったままです。`clear()`（または `refresh`）を呼んでください。
JPA のカスケードと `orphanRemoval` は適用されません。
DML の文にはトランザクションが必要です。`QueryBuilder(EntityManagerFactory)` は自分でトランザクションを開きます。`EntityManager` を使うビルダー（Spring の共有のものも含む）では呼び出し側のトランザクションが必要で、それがなければ SQL を送る前に失敗します。

<a id="procedures"></a>
### プロシージャ

| サポートしない | 代わりの書き方 |
|---|---|
| ラムダの中の、スタブメソッドの呼び出し 1 つ以外のもの（ログ、入れ子の呼び出し、分岐） | 呼び出しは 1 つ。引数は先に計算する |
| OUT パラメーターを持つプロシージャへの `exec` | `call` |
| 引数の DB のデフォルト値 | すべての引数を渡す |
| Oracle のパッケージのオーバーロード（まだ検出しません）、動的なプロシージャ名 | 予定 |
| Oracle の暗黙の結果（`DBMS_SQL.RETURN_RESULT`）、MariaDB のパッケージ、プロシージャの結果のページング | サポートしない |

<a id="108-caching-a-lambda-is-analyzed-once"></a>
## 10.8 キャッシュ: ラムダの解析は 1 回だけ

バイトコードの読み込みは遅い部分なので、lambdaQL はこれを **ラムダごとに 1 回** だけ行います。

| 項目 | ルール |
|---|---|
| キャッシュするもの | 解析の結果: HQL のテンプレートとパラメーターの取得元のリスト。失敗もキャッシュする |
| キャッシュのキー | ラムダの実装クラスとメソッド（名前、シグネチャ、種類）、その型、エンティティのクラス、そして **ClassLoader** |
| サイズ | 上限あり（LRU）。そのため無限に大きくならず、クラスローダーをリークしない |
| キャッシュしないもの | バイトコードそのもの（もう一度読むのは安く、保持すると大きい） |
| キャプチャ値 | **キャッシュしない。** クエリを実行するたびに読み、バインドする |
| キャプチャしたオブジェクトに対する呼び出し（`order.getDescription().trim()`、`this.field`、`LocalDateTime.of(...)`） | クエリを実行する **たびに** Java で実行し、1 つのパラメーターとしてバインドする |
| キャプチャ値だけの条件（`p != null && ...`） | 定数に畳み込まず `:p1 is not null` として残す。そのためキャッシュが正しいままになる |
| キャプチャ値としての `Direction` | バインド値で、毎回読む（構造はキャッシュされたまま） |
| DML | ラムダのクラスごとに 1 回解析する。`set()` の呼び出し間のチェックには小さな要約を使う |

プロトタイプ（JDK 21）での測定: 新しいラムダの最初の解析は約 **0.2 ms** かかります。
その後は、`set()` を 2 回呼ぶ更新でも、キャッシュの検索、値の取り出し、チェックにかかるのは
200 万回実行した後でも約 **0.1〜0.2 µs** です。目標: 同じラムダを 1,000 回呼んでも、バイトコードを読むのは
1 回だけにすることです。Hibernate もクエリプランを HQL 文字列ごとにキャッシュします。

これがどういう意味か:

```java
// 良い例: ラムダは 1 つで、値が変わる
.where(o -> o.getStatus() == status)

// v0.1 では不可: ラムダの中でクエリの形が変わる
.where(o -> flag ? o.getStatus() == 1 : o.isActive())

// 代わりに: ラムダを外で選ぶ（2 つのラムダで、それぞれ 1 回だけ解析される）
List<Order> list = flag
    ? qb.from(Order.class).where(o -> o.getStatus() == 1).toList()
    : qb.from(Order.class).where(o -> o.isActive()).toList();
```

<a id="109-tested-environment"></a>
## 10.9 テストした環境

| 項目 | バージョン |
|---|---|
| Java | 21（javac）。ECJ は保証しない |
| Hibernate | 6.6（設計は 6.6.5 で確認）。HQL の拡張（たとえば `is distinct from`）を使う。他の JPA プロバイダーは保証しない |
| データベース | H2 2.3、MariaDB 10.11、Oracle 23.26 (23ai) |
| Spring | Spring Boot 3.4.2 と Spring Data JPA 3.4.2（Spring モジュール、[12](12-spring-data-jpa.md)）。Spring Boot 4 はまだ検証していません |
| テストしていない | PostgreSQL、SQL Server、Oracle 19c 以前（たとえば PL/SQL の `BOOLEAN` パラメーター） |

---

[Java トラック](README.md) | 前: [9. Java の注意点](09-java-notes.md) | 次: [11. エラーと言語](../11-errors-and-languages.md) | [このページの Kotlin 版](../kotlin/10-rules-and-limits.md)
