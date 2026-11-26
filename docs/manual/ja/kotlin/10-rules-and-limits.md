<a id="10-rules-and-limits-kotlin"></a>
# 10. ルールと制限（Kotlin）

[Kotlin トラック](README.md) | [マニュアルのホーム](../README.md) | 前: [9. Kotlin の注意点](09-kotlin-notes.md) | 次: [11. エラーと言語](../11-errors-and-languages.md) | [このページの Java 版](../java/10-rules-and-limits.md)

共通ページ: [1. 概要](../01-overview.md) · [11. エラーと言語](../11-errors-and-languages.md)

<a id="contents"></a>
## 目次

- [10.1 基本のルール: ラムダ式はクエリである](#101-the-base-rule-a-lambda-is-a-query)
- [10.2 SARGable のルール（インデックスに優しい SQL）](#102-sargable-rule-index-friendly-sql)
- [10.3 NULL のルール](#103-null-rules)
- [10.4 演算子のルール（R1〜R24）](#104-operator-rules-r1-to-r24)
- [10.5 Kotlin と SQL の違い](#105-kotlin-and-sql-differences)
- [10.6 データベースごとの違い](#106-database-differences)
- [10.7 サポートしていないものと代わりの書き方](#107-not-supported-and-what-to-write-instead)
- [10.8 キャッシュ: ラムダ式は 1 回だけ解析される](#108-caching-a-lambda-is-analyzed-once)
- [10.9 対応環境](#109-tested-environment)

例の読み方:

- 例の `assertEquals` の文字列が、lambdaQL の作る HQL です。
- ラムダ式に直接書いた定数（数値、文字列、boolean、`null`、enum 定数）は、HQL にリテラルとして書きます。
  キャプチャした値と計算した値は、常にバインドパラメーター（`:p1`、`:p2`、...）です。
- エンティティ: `Order`（`id`、`price`、`status`、`active`、`description`、`customer`、`updateAt`、`items`）、
  `Customer`（`id`、`name`、`grade`）、`Item`（`order`、`price`、`qty`）。[2. はじめよう](02-getting-started.md#example-entities) を参照してください。
  10.3 の行は、`Order` のプロパティ `cnt`（`Integer`）、`custName`（`String`）、`address`（`@Embedded`、`city` と `zip`）も使います。
  NULL になりうる Boolean の例は、例のエンティティ `Coded` の `vip` を使います。例のエンティティは Java クラスなので、boolean の `active` は Kotlin では `it.isActive` で読みます。

---

<a id="101-the-base-rule-a-lambda-is-a-query"></a>
## 10.1 基本のルール: ラムダ式はクエリである

lambdaQL はラムダ式を呼び出さず、バイトコードの opcode を解析して HQL を作ります。
ラムダ式は、Kotlin の書き方で書いたクエリです。そのため、次のようになります。

1. `it.customer?.name` のようなパスは **カラムを指します**。呼び出しの連鎖ではありません。
   lambdaQL は顧客が null かどうかをチェックしません。`where` で、顧客のない注文が条件に一致しうるなら `left join`、一致しえないなら inner join です。
   そのため、`it.status == 1 || it.customer?.name == "Kim"`、`it.customer?.name == null`、null ガードは、Kotlin と同じく顧客のない注文を残します。
   `!!` か印なしで通った比較は、顧客のある注文でだけ真になります。`?.` と `!!` は混ぜて使ってもかまいません。
   顧客のない注文だけを探すには、関連そのものを比較してください: `it.customer == null`。
2. NULL は **自分で書いた場所でだけ** 扱われます（`== null`、`!= null`、`?.`、`?:`）。
   Kotlin のコンパイラーが追加する null チェックは無視されます。その他の NULL は SQL のルールに従います。比較する値が NULL の行は返されません。
3. `!!` は例外を投げません（関連の段階の `!!` は 1 を参照）。Kotlin のメモリのルール（オブジェクトの同一性、ボクシング）は適用されません。`==` と `===` は **値の等価性** を意味します。
4. **解析できないラムダ式はエラーです。** lambdaQL はクエリとして解析できないと、コード `[LQL-xxxx]` とソースの行番号を含む例外を投げます。

<a id="102-sargable-rule-index-friendly-sql"></a>
## 10.2 SARGable のルール（インデックスに優しい SQL）

「SARGable」とは、データベースが条件にインデックスを使えるという意味です。
これは lambdaQL の基本のルールです（設計の 3.9 節）。

| # | ルール |
|---|---|
| 1 | **カラムの側に関数、演算子、`CASE` を置きません。** 値を変える必要があるなら、バインドの前にアプリケーションでパラメーターを変えます（例: `Locale.ROOT` での小文字変換） |
| 2 | 意味が同じ形が 2 つあるなら、単純な比較と `AND` / `OR` / `NOT` だけを使う形を選びます。正しい 3 値論理が先で、その中で SARGable な形を選びます |
| 3 | 各出力のルールは、H2、MariaDB、Oracle で `EXPLAIN` によって確認します |

<a id="examples"></a>
### 例

**単純な `=` と `<>` を使う等値比較**

同じ例が [9.4](09-kotlin-notes.md#plain--and-) にあります。

<!-- region: ManualKotlinNotesTest.kt#equality -->
```kotlin
// plain = and <>: a row whose description is NULL matches neither
val same = qb.from(Order::class.java).where { it.description == text }
assertEquals("select e0 from Order e0 where e0.description = :p1", same.toHql().hql())
val other = qb.from(Order::class.java).where { it.description != "x" }
assertEquals("select e0 from Order e0 where e0.description <> 'x'", other.toHql().hql())
// NULL rows too: write the null check yourself
val orNull = qb.from(Order::class.java).where { it.description == null || it.description != "x" }
assertEquals("select e0 from Order e0 where e0.description is null or e0.description <> 'x'", orNull.toHql().hql())
```

lambdaQL は `is [not] distinct from` を書きません。この形は Oracle で `decode(...)` になり、インデックスを使えません。
エンティティの値は SQL の意味に従うので、単純な `=` や `<>` が常に正しいです。キャプチャした `null` は NULL としてバインドされ、どの行にも一致しません（`WARN` レベルの `LQL-4045`）。

**エルビス**

<!-- region: ManualKotlinNotesTest.kt#elvis-value -->
```kotlin
// ?: in a select value: coalesce
val text = qb.from(Order::class.java).select { it.description ?: "" }
assertEquals("select coalesce(e0.description, '') from Order e0", text.toHql().hql())
// ?: after ?. in a select value is rejected
val error = assertThrows(LambdaQLException::class.java) {
    qb.from(Order::class.java).select { it.customer?.name ?: "none" }
}
assertEquals("LQL-2022", error.code())
// ?: with a number: coalesce too
val number = qb.from(Order::class.java).where { (it.total ?: 0.0) > 3.0 }
assertEquals("select e0 from Order e0 where coalesce(e0.total, 0.0) > 3.0", number.toHql().hql())
// in a string condition, ?: is coalesce too
val x = qb.from(Order::class.java).where { (it.description ?: "") == "x" }
assertEquals("select e0 from Order e0 where coalesce(e0.description, '') = 'x'", x.toHql().hql())
```

文字列の条件の `?:` は `coalesce(e0.description, '') = 'x'` になります。カラムに関数がかかるので、`description` の通常のインデックスは使われません。
インデックスが必要なら、`it.description == "x"` のように直接比較してください。`"x"` と同じ行は NULL ではないので、結果は同じです。

**サブクエリを使った NOT IN** は `NOT IN` として書きます（SQL の意味）。サブクエリに NULL があると、行は返されません。NULL を飛ばすには、サブクエリに null チェックを追加してください（[10.3](#103-null-rules) を参照）。

<a id="what-the-measurements-showed"></a>
### 測定の結果

MariaDB 10.11 と Oracle 23ai、20,000〜50,000 行:

| 形 | MariaDB | Oracle | 問題ないか |
|---|---|---|---|
| `_ci` のカラムに対する `name = 'abc'` | インデックスの検索 | – | はい |
| `LOWER(name) = LOWER('abc')` | インデックス全体のスキャン（19,791 行） | テーブル全体のスキャン（関数インデックスなし） | **いいえ** |
| `lower(name) = :p_lowered` + 関数インデックス | – | インデックスの範囲スキャン（`LIKE 'ab%'` も同じ） | はい |
| `name COLLATE BINARY_CI = :p` + 言語別のインデックス | – | インデックスの範囲スキャン | はい |
| `AND`/`OR` で書いた boolean の `==` | 範囲（350 行） | インデックスの範囲スキャン 2 回 + ビットマップ OR | はい |
| `CASE` で書いた boolean の `==` | インデックス全体のスキャン（49,648 行） | テーブル全体のスキャン | **いいえ** |

<a id="db-functions-on-a-column"></a>
### カラムに対する DB 関数

カラムの側の DB 関数（`OrderFns.discount(it.price)!! > 10`）は、通常のインデックスを使えません。
lambdaQL は **WARN**（`LQL-5001`）をログに出します。例外: Oracle で同じ式の関数ベースのインデックスがあれば、ログに出しません。
MariaDB では、`NOT DETERMINISTIC`（デフォルト）の関数は引数がパラメーターだけでもフルスキャンになるので（`it.price!! > OrderFns.discount(rate)!!`）、この場合も警告します（`LQL-5007`）。
`SargableMode.STRICT` では、警告の代わりに解析が失敗します（`LQL-3066`）。

**SARGable モード**。`SargableMode` には 3 つの値があります。

- `OFF`: チェックせず、メタデータも照会しません。
- `WARN`（デフォルト）: 警告をログに出します。
- `STRICT`: `WARN` なら警告をログに出す場合（`LQL-5001`、`LQL-5007`、`LQL-5012`）に、解析が `org.lambdaql.NonSargableException`（`LQL-3066`）で失敗します。

永続化ユニットごとに `SargablePolicy.register(emf, SargableMode.STRICT)` で指定し、`SargablePolicy.unregister(emf)` で解除します。Spring では `lambdaql.sargable.mode=STRICT` を使います（[12](12-spring-data-jpa.md) を参照）。
チェックはラムダ式ごとに 1 回、解析の結果を共有キャッシュに入れるときに実行されます。そのため、最初のクエリの前にモードを登録してください。

**カラムに対する算術**。

- カラム 1 つと値だけからなる `+ - * /`（`it.cnt * 2 > n`）は、値の側に移せます。lambdaQL は `LQL-5012` を `WARN` レベルでログに出し、`STRICT` ではエラーです。
- カラムどうしの算術（`it.price!! * it.cnt > n`）と `%` は移せないので、`LQL-5013` を `DEBUG` レベルでだけログに出します。`STRICT` でも許可します。
- 移した形は意味が同じでなければなりません。`it.cnt > n / 2.0` のように小数で割ってください。整数の割り算 `n / 2` は、`>=`、`=`、`<` と負の `n` で別の行を返します。負の数を掛けたときは比較の向きを逆にしてください。`LQL-5012` のメッセージがこれを案内します。
- インデックスがあるかを照会して警告を減らす方法は採用しませんでした。権限とシノニムのために「インデックスなし」と「不明」が混ざり、カラムの算術はまれな形だからです。
[8. プロシージャと DB 関数](08-procedures-functions.md) を参照してください。

コード例: [`ManualKotlinNotesTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinNotesTest.kt)（`equality`、`elvis-value`）。

<a id="103-null-rules"></a>
## 10.3 NULL のルール

| ラムダ式 | SQL | NULL の動作 |
|---|---|---|
| `it.cnt == 1`（`Integer` と定数） | `e0.cnt = 1` | NULL: 行は返されない。コンパイラーが入れた null チェックは読まない |
| `it.cnt != 1` | `e0.cnt <> 1` | NULL: 行は返されない（SQL の意味）。必要なら `it.cnt == null \|\|` を自分で書く |
| `it.price!! > p` | `e0.price > :p1` | NULL: 行は返されない。`!!` は無視される |
| `it.status == q`（両方 nullable: `q: Int?`、`String?`、enum） | `e0.status = :p1` | カラムが NULL: 返されない。キャプチャした値が `null`: 一致する行なし（`= NULL`、`WARN` レベルの `LQL-4045`） |
| `it.status != q`（両方 nullable） | `e0.status <> :p1` | カラムが NULL: 返され **ない**（SQL の意味。Kotlin なら返される）。キャプチャした値が `null`: 一致する行なし |
| `it.description == it.custName`（エンティティの値 2 つ） | `e0.description = e0.custName` | どちらかが NULL: 返されない。NULL と NULL も同じ（SQL の意味） |
| `it.description.equals(text)`（`String?`） | `==` と同じ | `==` と同じ |
| `x == null`、`x != null` | `is null`、`is not null` | 2 値 |
| `it.cnt >= 2.5`（整数のカラム、小数の値） | 書いた値のまま比較: 定数はリテラル（`e0.cnt >= 2.5`）、キャプチャした値と IN リストも小数リテラル（`e0.cnt >= 2.5BD`、`in (2.5BD, 3.0BD)`） | インデックスの範囲スキャンを維持（MariaDB で実測）。以前のように値を切り捨てて誤った行を返すことはない |
| 値オブジェクト（`@Embedded`）全体: `it.address == addr`、`it.address != addr` | カラムごとに展開: `e0.address.city = :p1 and e0.address.zip = :p2`、否定は `<> ... or ...`。`== null` はすべてのカラムが `is null`（and）、`!= null` は `is not null`（or） | カラムごとに SQL の意味: NULL のカラムは `=` にも `<>` にも不一致。キャプチャしたオブジェクトが `null` なら一致する行なし（`WARN` レベルの `LQL-4045`） |
| `it.id == id`（`it.id` は `Long?`、`id` はキャプチャした `Long`）、`it.id == null`: NULL になれないカラム（id、プリミティブ型、`nullable = false`、パスに関連の段階なし） | `not (e0.id is null or e0.id <> :p1)` の代わりに `e0.id = :p1`。`it.id == null` は `1=0` | カラムに NULL はない。関連を通るパス（`it.customer?.id`）はチェックを残す |
| `!`、`&&`、`\|\|` | `NOT`、`AND`、`OR` | SQL の 3 値論理 |
| `it.vip ?: false`（条件） | `e0.vip is not null and e0.vip = true` | NULL は false として扱う |
| `where` の中の `it.customer!!.name`（または印のないパス） | `j1.name`。`it.customer!!.name == "Kim"`、`it.customer!!.name == null` は `join e0.customer j1` | 顧客なし: 比較は真にならない。`it.status == 1 \|\| it.customer!!.name == "Kim"` は `left join` で、status 1 の注文は返される |
| `where` の中の `it.customer?.name` | `j1.name`。`it.customer?.name == "Kim"` は `join`、`it.customer?.name == null` は `left join e0.customer j1` | `it.customer?.name == "Kim"`: 返されない（`j1.name` が NULL）。`it.status == 1 \|\| it.customer?.name == "Kim"`、`it.customer?.name == null`: 返される |
| `it.customer == null \|\| it.customer?.name == p` | `e0.customer is null or j1.name = :p1` + `left join e0.customer j1` | 顧客なし: 返される。`!!` で書いても同じ |
| `?.` と `!!` を混ぜる: `it.customer?.name == null \|\| it.customer!!.grade == null` | `j1.name is null or (e0.customer is not null and j1.grade is null)` + `left join e0.customer j1` | 顧客なし: `?.` の側の比較で返される。警告なし |
| 同じパスを `orderBy` や `select` にだけ使う | `j1.name` + `left join e0.customer j1`。必須の関連（`optional = false`）は `join` | 顧客なし: カラムが NULL |
| `it.customer == null`、`it.customer != null` | `e0.customer is null`、`e0.customer is not null`（外部キーのカラム、結合なし） | 顧客のない注文 / 顧客のある注文 |
| `it.customer?.id` | `e0.customer.id`（外部キーのカラム、結合なし） | 顧客なし: NULL |
| `a ?: b` | select の値、条件、`set` の値で `coalesce(a, b)`。Boolean の条件の `?: false` は null チェックとして読む | [9.5](09-kotlin-notes.md#95-null-operators---and-) を参照 |
| `it.id in ids` | `e0.id in (:p1)` | NULL の id: 返されない |
| `it.id !in ids` | `e0.id not in (:p1)`。NULL になりうるカラムも同じ | NULL の値: 返され **ない**（SQL の意味。`DEBUG` レベルの `LQL-5009`。Kotlin なら返される） |
| `it.cnt in 1..5`、`it.cnt !in 1..5` | `1 <= e0.cnt and e0.cnt < 6`、`1 > e0.cnt or e0.cnt >= 6` | 対象が NULL: 返されない。`!in` でも同じ（ガードなし） |
| `x !in sub...` の形のサブクエリ | `x not in (select ...)` | サブクエリに NULL があると、どの行も返されない（SQL の意味） |
| `Agg.sum`、`avg`、`min`、`max` | `sum(...)` ... | 行がないか、すべての値が NULL なら NULL |
| `Agg.count()` | `count(*)` | 行がなければ 0 |

<a id="captured-null-values"></a>
### null のキャプチャした値

| キャプチャした値のある場所 | `null` を許可するか |
|---|---|
| 2 つの nullable な値の間の `==` / `!=` | はい。ただし一致する行はない（`= NULL`）。クエリの実行時に `LQL-4045` を `WARN` レベルでログに出す |
| 同じ値に対する null ガードの下: `p == null \|\| it.status!! > p` | はい。ガードはクエリの実行時にアプリケーションで決めます。`p == null` なら絞り込みなし、別の値なら `e0.status > :p1`。`!!` と自身のプリミティブ型へのアンボクシングだけの比較は、ガードの位置に関係ない |
| `!!` と一緒に使うか、大小の比較（`<` `<=` `>` `>=`）に使う: `it.price!! > p!!` | はい。ただし一致する行はない（`> NULL`）。`!!` と自身のプリミティブ型へのアンボクシングは実行しない。クエリの実行時に `LQL-4045` を `DEBUG` レベルでログに出す |
| キャプチャした値に対するメソッドの呼び出し（`p!!.trim()`） | **いいえ。** 値を計算するときにエラー（`LQL-3027`）。[9.8](09-kotlin-notes.md#98-captured-values) を参照 |
| `in` に使うコレクションの中の `null` の要素 | **いいえ。** バインドするときにエラー（`LQL-4008`） |

<a id="104-operator-rules-r1-to-r24"></a>
## 10.4 演算子のルール（R1〜R24）

設計（3.6 節と KOTLIN.md の 5 節）にあるルール表を、Kotlin の形で短くまとめたものです。

| # | ラムダ式の形 | 意味 |
|---|---|---|
| R1 | non-null のプリミティブ型の比較、または `!!` の後の比較（`it.qty > 5`、`it.price!! > p`） | `=`、`<>`、`<`、... SQL の NULL のルール |
| R2 | 参照の比較にコンパイルされる `==` / `!=`（enum の `==`。`===` は R17 を参照） | オブジェクトの同一性ではなく値の等価性。SQL の意味の `=` / `<>` |
| R3 | `x == null`、`x != null` | `IS NULL`、`IS NOT NULL` |
| R6 | `!`、`&&`、`\|\|` | `NOT`、`AND`、`OR`。SQL の 3 値論理 |
| R7 | 文字列の値の比較 | **DB の照合順序** に従う（大文字と小文字、末尾の空白）。Oracle は `''` を NULL として扱う |
| R8 | エンティティ `==` エンティティ | サポートしない（KC13、`LQL-3011`） |
| R9 | キャプチャした値 | 実行時の値を持つバインドパラメーター |
| R10 | プロパティへのアクセス（`it.price`） | getter の呼び出し。JPA の属性にマッピングされる |
| R11 | `Intrinsics.areEqual` にコンパイルされる Kotlin の `==`、`!=`（参照型、または両側とも nullable） | 値の等価性: SQL の意味の `=` / `<>`。キャプチャした `null` はどの行にも一致しない |
| R12 | nullable なプリミティブ型とプリミティブ型（`it.cnt == 1`、`cnt: Integer`） | コンパイラーが入れた null チェックは読まない: `e0.cnt = 1`、`e0.cnt <> 1` |
| R13 | `!!` | 無視される |
| R14 | `?.` | NULL が伝わるパスまたは値。`?.` がない場合と同じカラム |
| R15 | `?:` | 値では `coalesce(a, b)`。条件の中の boolean のエルビスは `(x is not null and x = true)` になる。値として使う boolean のエルビスは拒否される（KC21） |
| R16 | 関連の結合 | R24 に置き換えられた |
| R17 | `===`、`!==` | `==` と同じく値の等価性 |
| R18 | 範囲 `in a..b`、`until`、`..<`、`!in` | `a <= x and x < b + 1`（定数）、`:p1 <= x and x <= :p2`（キャプチャした値）、`a > x or x >= b + 1`（`!in`）。自動の null ガードなし |
| R19 | コレクション `x in c`、`x !in c`（キャプチャした `c`） | `x in (:p)`、`x not in (:p)`（SQL の意味）。null の要素: エラー。空のコレクション: FALSE / TRUE |
| R20 | `true`/`false` で終わる分岐を持つ、対象のない `when { }` | 条件の `OR`。対象のある `when (subject)` は拒否（`LQL-2010`） |
| R21 | Kotlin の文字列関数 | `startsWith`、`endsWith`、`contains`（`ignoreCase` を含む）は `like`。`equals(ignoreCase)`、`lowercase`、`length`、`isNullOrEmpty` は拒否（`LQL-3008`）。`equals(text)` は `==` と同じ |
| R22 | Kotlin のホストの値（キャプチャした `var`、private プロパティ、`companion` の値） | 実行するたびに読む。書き込みは拒否される |
| R23 | プロパティ参照 `Order::price` | 条件では拒否（`LQL-1009`）。ラムダ式 `{ it.price }` を使ってください |
| R24 | 関連のパス（`it.customer?.name`、`it.customer!!.name`、Java のエンティティの `it.customer.name`） | カラム。to-one の関連: where、order by、select が共有する別名。`where` では、関連のない行が条件に一致しうるなら `left join`、一致しえないなら inner join（`!!` か印なしで通った比較は関連のある行でだけ真、`?.` と `!!` は混ぜて使ってよい）、order by や select にだけ使うと `left join`（必須の関連 `optional = false` は inner join、A4）。埋め込み: 同じテーブル。対象の `@Id` で終わるか、関連を `null` と比較: 外部キーのカラム、結合なし。コレクション: エラー |

Java 専用のルール R4（エンティティの値に対する `equals`）と R5（Java の null 安全なヘルパー）は、[Java トラック](../java/10-rules-and-limits.md) にあります。Kotlin では `==` が R5 と同じ意味を持ちます。

その他のルール:

- `x.compareTo(y) > 0` は `x > y` になります。`isBefore`、`isAfter` は拒否されます（`LQL-3008`）。
- ラムダ式に直接書いた定数（`const val` を含む）はリテラルです: `it.price > 100.0` は `e0.price > 100.0`、`"O'Brien"` は `'O''Brien'`。キャプチャした値と計算した値（`BigDecimal("1.5")`）はバインドパラメーターです。例外: 整数のカラムと比較する小数のキャプチャした値は小数リテラル（`2.5BD`）。
- コンパイラーはときどき `!` の位置を動かします（ド・モルガン、二重否定）。それでも結果は変わりません。
  lambdaQL は各条件をちょうど 1 回ずつ残し、書いた論理を「単純化」しません。
- Kotlin で常に真になる条件（`x > 5 || !(x > 5)`）を書いても、単純化しません。
  SQL では、`x` が NULL の行が抜けます。これが SQL のルールです。

<a id="105-kotlin-and-sql-differences"></a>
## 10.5 Kotlin と SQL の違い

この違いは SQL そのものから来ます。コンパイラーの問題ではありません。
「G」の番号は設計（2.7 節）の ID です。

| # | 違い | lambdaQL の扱い |
|---|---|---|
| G1 | Kotlin では `null != "x"` は true だが、SQL の `description <> 'x'` は description が NULL の行を返さない | `Intrinsics.areEqual` にコンパイルされる `!=` は `<>` として出力: SQL の意味。NULL の行は除かれ、`LQL-5009` を `DEBUG` レベルでログに出す。このような行を残すには `it.description == null \|\| ...` と書いてください。nullable な数値と定数の `!=`（`it.cnt != 1`）も `e0.cnt <> 1` になるので、NULL の行は除かれる |
| G3 | NULL のパラメーターで `name = ?` を実行すると 0 行を返す | そのまま（SQL の意味）。実行時に `LQL-4045` を `WARN` レベルでログに出す。NULL を探すには `x == null` と書いてください |
| G4 | `status NOT IN (1, NULL)` は常に 0 行を返す | キャプチャしたコレクションの中の `null` はエラー。サブクエリの `!in` は SQL の意味を保つ: サブクエリに null チェックを追加してください |
| G5 | エンティティのカラムの値が NULL の場合（`!!`、アンボクシングを含む） | 文書化: その行は SQL のルールに従って返されない |
| G6 | `NaN`: H2 は NaN をいちばん大きい値としてソートし `NaN = NaN` が true、MariaDB は保存できない、Oracle は `BINARY_DOUBLE` にだけ保存できる | 文書化: NaN を含む比較は DB に従う |
| G7 | `Float` のカラムと `Double` の値の比較: DB によって結果が違う | `Float`/`Double` について、**Kotlin とビット単位で同じ結果は約束しない**。lambdaQL は `CAST` を追加しない。カラムのマッピングを自分で選ぶ（例: Oracle の `binary_float` / `binary_double`） |
| G9 | `x > 5 \|\| !(x > 5)` で NULL の行が抜ける | 単純化せず、書いたとおりに出力 |
| G10 | SQL は評価の順序を約束しない: `qty != 0 && total / qty > 2` でもゼロ除算が起こりうる | 文書化: ガードの下の割り算もそのまま書く。割り算なしで比較してください（`it.qty > 0 && it.price > 2.0 * it.qty`） |
| G11 | 文字列: MariaDB の `_ci` は大文字と小文字と末尾の空白を無視、Oracle は `''` を NULL として保存、Oracle の `NLS_SORT` は JVM のロケールに従う | 文書化: 文字列の比較とソートは DB に従う。Oracle では `NLS_SORT=BINARY` に設定（セッションの初期化か `-Duser.language=en`）。Oracle では `it.description == ""` と `isEmpty()` が違う動きをする |
| G12 | Kotlin の `===` は同一性（値が 1000 の 2 つの `Long` のボックスは false） | lambdaQL は `===` と `==` を値の等価性と定義 |
| G13 | 0/1 以外の値を持つレガシーな boolean のカラム（MariaDB の `TINYINT(1)`、Oracle の `NUMBER(1)`）は違って読まれる | Hibernate 自身の DDL（`bit(1)`、Oracle 23 の `boolean`）は問題ない。レガシーなスキーマには注意 |
| G14 | Oracle 23 のバグ: 複合条件に対する `WHERE (a AND b) = false` / `IS FALSE` / `IS NULL` で行が抜ける | lambdaQL は複合条件を boolean のテストで包まない。否定は常に `NOT(...)` |
| G15 | Oracle の `CHAR` のカラムは `String` のバインドと一致しない | 文書化: エンティティのマッピングに注意 |

その他の違い:

| 違い | Kotlin | SQL | lambdaQL |
|---|---|---|---|
| `BigDecimal` の等価性 | `==` はスケールまで比較（`1.0` と `1.00` は false） | `=` は値を比較 | 値の等価性 |
| 整数の割り算 | `5 / 2` は `2` | MariaDB は `2.5` | DB によって違う |
| `BigDecimal` の割り算と関数の結果 | Kotlin のスケールのルール（`HALF_EVEN`） | スケールは DB が決める（例: 関数の結果に対して H2 は 1、MariaDB と Oracle は 0） | 文書化 |
| Unicode の大文字と小文字の無視（`ſ`、`İ`） | Kotlin のルール | DB の照合順序 | DB に従う |
| 文字列の長さ | UTF-16 の単位 | H2 は UTF-16 の単位、MariaDB はコードポイント | 文書化 |
| NULL を含む文字列テンプレート | `"null"` というテキスト | NULL（Oracle: `NULL \|\| 'x'` は `'x'`） | 文字列の連結は拒否（`LQL-2007`） |
| nullable な範囲の対象 | false。`!in` は true | 返されない。`!in` でも同じ | SQL の意味 |
| non-null 型の `String` | null ではない | カラムは NULL になりうる | lambdaQL は JPA のメタデータを使う |
| ラムダ式の中の `LocalDateTime.now()` | アプリケーションの時刻 | – | **アプリケーションの時刻** でバインド。DB の時刻（`current_timestamp`）はサポートしない |
| `Set` の同じ要素 | `equals` / `hashCode` が決める（`data class` はプライマリコンストラクターのプロパティ） | 主キーで行を区別 | Set のサイズが行数より小さいと、`toSet()` と `findSet()` が `IllegalStateException` を投げる（エンティティの `equals` / `hashCode` が違う行を等しいと判断するか、`@Id` が一意でない場合、例: ビュー）。`toList()` を使う |

<a id="106-database-differences"></a>
## 10.6 データベースごとの違い

lambdaQL は多くの違いを隠します。いくつかは知っておく必要があります。

<a id="sorting-and-paging"></a>
### ソートとページング

| 項目 | H2 | MariaDB | Oracle | lambdaQL |
|---|---|---|---|---|
| NULL の位置、`ASC` | 最初 | 最初 | **最後** | すべての DB で `ASC` は NULLS FIRST |
| NULL の位置、`DESC` | 最後 | 最後 | **最初** | すべての DB で `DESC` は NULLS LAST |
| `NULLS FIRST/LAST` の構文 | あり | **なし**（構文エラー） | あり | MariaDB では Hibernate が `CASE` でまねる: ソートに **インデックスを使えない** |
| ページングの SQL | `offset ? rows fetch first ? rows only` | `limit ?,?` | `offset ? rows fetch first ? rows only` | Hibernate に渡す（`setFirstResult` / `setMaxResults`） |
| 同じ値を持つ行の順序 | – | – | ランダム | ページングするとき、キーに id がなければ lambdaQL がルートの `@Id asc` を追加。一意で NULL になれないキー（`unique = true` かつ `nullable = false`、`@NaturalId`）があれば追加しない |
| 文字列の順序 | 大文字と小文字を区別 | `_ci` | `NLS_SORT`（JVM のロケール） | DB に従う |
| `order by` の暗黙のパス | inner join（行が抜ける） | 同じ | 同じ | lambdaQL は `left join` を使う（必須の関連は inner join、または同じパスの `where` の結合） |
| `order by`、`distinct`、`group by` の `@Lob` のカラム | 動く | 動く | **ORA-22848 / ORA-22849** | クエリを変えず、`LQL-5010` を `DEBUG` レベルでログに出す |
| `@Enumerated(EnumType.STRING)` の enum のカラムのソート | 名前の順 | 名前の順 | 名前の順 | クエリを変えず、`LQL-5011` を `DEBUG` レベルでログに出す。宣言の順は `ORDINAL` のマッピングでだけ |

lambdaQL は、NULL になりうるすべてのキーについて HQL に `nulls first` / `nulls last` を書きます。必須の関連だけを通り NULL になれないパスには書きません。データベースのデフォルトと同じなら、Hibernate がこれを省きます。
デフォルト（`ASC` = NULLS FIRST、`DESC` = NULLS LAST）は H2、MariaDB と同じなので、これらの DB では追加の SQL は
要りません。Oracle にだけ `NULLS` 句が付きます。NULL の位置を変えるには、
`asc(key, Nulls.LAST)`（NULL が最後）や `desc(key, Nulls.FIRST)`（NULL が最初）を使ってください。MariaDB では、これらは `CASE` によるまねを使います。

<a id="conditions-and-values"></a>
### 条件と値

| 項目 | H2 | MariaDB | Oracle |
|---|---|---|---|
| `is not distinct from` | 標準でサポート | `<=>`（インデックスを使う） | `decode(...)`（インデックスを使えない）。lambdaQL はこの形を書かない |
| `is [not] true`、`coalesce(boolean, false)` | 動く | 動く | **ORA-00932**。lambdaQL は書かない |
| 空文字列 `''` | 値 | 値 | **NULL** |
| 大文字と小文字、末尾の空白 | 正確に比較 | `_ci`: 無視 | 正確に比較（ソートには `NLS_SORT`） |
| `NaN` | 保存される、いちばん大きい値 | 保存できない | `BINARY_DOUBLE` にだけ |
| `Float` のカラム | REAL のリテラルのルール | double に広げる | Hibernate は `Float` を `float(24)`（10 進）にマッピング |
| 整数の `/` | 整数 | **小数**（`5/2 = 2.5`） | – |
| 大きな `in` のリスト（値 1,500 個） | 動く | 動く | 23ai で動く（以前の Oracle のバージョンの 1,000 個の制限は測定していない） |

<a id="subqueries"></a>
### サブクエリ

| 項目 | 起きること | lambdaQL |
|---|---|---|
| `limit` のないサブクエリの `order by` | Oracle: 構文エラー（ORA-00907）。MariaDB: 無視される | WARN とともに取り除く |
| MariaDB の `IN (... limit n)` | サポートしない（エラー 1235） | 自動で包む: `in (select x.id from (... limit n) x)` |

<a id="update-insert-delete"></a>
### 更新、挿入、削除

| 項目 | 起きること | lambdaQL |
|---|---|---|
| `SET a = ..., b = a + 1`（設定した後でそのカラムを読む） | MariaDB は **新しい** 値を、H2 と Oracle は古い値を読む | 解析するときにエラー |
| DML の中の関連のパス（`it.customer?.name`） | Hibernate が DB ごとに違う結合を作り、顧客のない **行を取りこぼす** | lambdaQL は相関スカラーサブクエリを書く（`where` と SET）。`where` で、選択なら inner join するパスには `customer.id is not null` も加える。そのため、DML がクエリと同じ行に適用される |
| 同じテーブルに対するサブクエリのある `DELETE` | MariaDB のエラー 1093 | 対象の id を派生テーブルで先に読む: `d0.id in (select t.id from (select e0.id as id … where <condition>) t)`。相関サブクエリでも動き、3 つの DB で同じ行を削除（実測）。`update` は包まない（MariaDB でも動く） |
| 同じ enum の値を STRING のカラムと ORDINAL のカラムに 1 回だけバインド | H2 と MariaDB が **エラーなしで誤った値** を保存 | 場所ごとにパラメーター 1 つ。共有しない |
| `values(list) { }` の多くの行 | Oracle: バインド 65,535 個の上限（ORA-01745）、そのずっと前からとても遅い | 1 文あたり値 500 個以下に分け、すべての文を 1 つのトランザクションで実行 |
| `order by ... limit` のある `update` / `delete` | MariaDB は `in` サブクエリの `limit`（エラー 1235）と、対象のテーブルを読むサブクエリ（エラー 1093）を拒否 | `e0.id in (select t.id from (select x0.id as id ... order by ... limit n) t)`: 派生テーブル `t` で 3 つのデータベースすべてで動く。内側の select が選択の結合を使うので、選択と同じ行を対象にする |
| `on conflict` / upsert | H2 と Oracle は `merge`。MariaDB は `insert ... on duplicate key update` で、更新した行を 2 と数える。Hibernate の `do nothing` は H2 と Oracle で失敗 | `upsert` と `onConflict(...).update(...)` は Hibernate の形を使う。`ignore()` は `insert ... select ... where not exists` で書く。行数はそのまま返す |

<a id="procedures-and-functions"></a>
### プロシージャと関数

| 項目 | H2 | MariaDB | Oracle |
|---|---|---|---|
| OUT / INOUT パラメーター | **なし** | あり | あり |
| 複数の結果セット | なし | あり | あり（REF CURSOR） |
| プロシージャが影響を与えた行数 | – | – | 常に -1 なので lambdaQL は提供しない。OUT パラメーターを使う |
| boolean のパラメーターと結果 | – | あり | 23ai 以上 |
| 仮想カラム / 関数インデックスの中のストアド関数 | – | 不可能（エラー 1901） | 可能（関数ベースのインデックス） |

H2 には OUT パラメーターがないので、プロシージャの機能は MariaDB と Oracle で確認します。

<a id="107-not-supported-and-what-to-write-instead"></a>
## 10.7 サポートしていないものと代わりの書き方

lambdaQL は、このリストのすべての形について明確なエラーを投げます。

<a id="lambda-form-and-loading"></a>
### ラムダ式の形と読み込み

| サポートしていない | 代わりの書き方 |
|---|---|
| オブジェクト式（`object : ...`）、`Serializable` でない関数型インターフェース | ラムダ式のリテラル |
| Kotlin の関数型として保存したラムダ式（`val f: (Order) -> Boolean`） | ラムダ式のリテラルを渡す |
| `-Xsam-conversions=class`、`inline fun` の本体の中で作ったラムダ式 | [9.14](09-kotlin-notes.md#914-kotlin-limits-kc1-to-kc21) を参照 |
| 自分で作った Kotlin の `fun interface` に対する呼び出し可能参照 | ラムダ式のリテラル |
| 条件、ソート、select のラムダ式の中のローカルの `val`/`var`、ループ、複数の文 | 1 つの式。値はラムダ式の外で計算する |
| キャプチャした変数への書き込み（`n++`） | ラムダ式の外で計算する |
| `@JvmField`、`internal`、`value class`、`@get:JvmName` のプロパティ | public の普通のプロパティ |
| Java 21〜23 で実行するとき、同梱の ASM が知らないより新しい JDK 向けにコンパイルされたラムダ式 | ASM を更新（lambdaQL は ASM をシェーディングしない）。Java 24 以上で実行するなら関係ない（`java.lang.classfile` を使う） |

<a id="conditions"></a>
### 条件

| サポートしていない | 代わりの書き方 |
|---|---|
| boolean の `if (c) x else y`、`takeIf`（`LQL-2021`）、対象のある `when (subject)`（`LQL-2010`） | `&&` / `\|\|`。値を選ぶ `if`/`when` はサポート: キャプチャした値と定数だけならバインドパラメーター、エンティティの値なら `case when`（[3.5.1](03-where.md#351-values-chosen-by-a-condition)） |
| （サポート: `if (c) x else false`、`if (c) true else x`、`when { a -> true; ...; else -> false }`） | それぞれ `AND`、`OR` を意味する |
| boolean の中置関数 `and`、`or`、`xor` | `&&`、`\|\|` |
| エンティティ `==` エンティティ（`it.customer == c`）（`LQL-3011`） | `it.customer?.id == c.id` |
| 値オブジェクト（`@Embedded`）の大小の比較、別の型との比較（`LQL-3012`） | フィールドを比較する。同じ型との `==`、`!=`、`null` との比較はサポート |
| コレクションのパス（`it.items...`、`@OneToMany`、`@ManyToMany`） | `exists()` サブクエリか結合 |
| `item in it.items` | `exists()` サブクエリ |
| ラムダ式の中で作ったコレクション（`listOf(1, 2)`、`setOf(...)`） | 外で作ってキャプチャする。（`groupBy` のキーは例外） |
| 範囲の `step` | 2 つの比較か、キャプチャしたコレクション |
| キャプチャしたコレクションの中の `null` の要素 | null を取り除く。NULL の行が必要なら `\|\| it.x == null` を追加 |
| キャプチャした `NaN` / `Infinity` | クエリの前に値をチェックする |
| `isNaN()` | – |
| エンティティの値に対するその他の関数呼び出し（自分で作った inline でない関数、`operator` 関数） | プロパティで条件を書くか、関数を `inline` にする |
| 文字列テンプレートや `+`（`LQL-2007`） | 文字列はラムダ式の外で作ってキャプチャする |
| エンティティのパスに対する `it.tags["k"]`、`it.lines[0]` | – |

<a id="functions-and-strings"></a>
### 関数と文字列

| サポートしていない | 代わりの書き方 |
|---|---|
| エンティティの値に対する文字列関数: `lowercase`、`length`、`isNullOrEmpty`、`isBlank`、`equals(s, ignoreCase = true)`（`LQL-3008`） | 直接比較する。`startsWith`、`endsWith`、`contains` はサポート |
| カラムに対する `trim`、`coerceAtLeast`、`toLocalDate` とその他の stdlib の関数 | 直接比較するか、DB 関数（`@SqlFunction`） |
| 日付の関数（`YEAR(col) == 2024`） | 範囲: `it.updateAt!! >= from && it.updateAt!! < to` |
| ジェネリックな戻り値の型（`fun <X> nvl(a: X, b: X): X`）、エンティティ、コレクション、`Unit` を返す `@SqlFunction` | 次のどれか: `Double`、`Integer`（`Int`）、`Long`、`BigDecimal`、`String`、`LocalDate`、`LocalDateTime`、`Boolean` |
| Hibernate の組み込み関数と同じ名前の `@SqlFunction`（`COALESCE`、`ROUND`、`SYSDATE`、...） | 接頭辞かスキーマ名を使う（`PUBLIC.SQ_FN`） |
| デフォルト引数、`inline`、`vararg` のある `@SqlFunction` | パラメーターが固定の普通の関数 |
| DB 関数としての拡張関数、関数型インターフェースの定数、関数型のプロパティ | インターフェースの `companion object` + `@JvmStatic` |
| ユーザー定義の集約関数やウィンドウ関数 | – |
| クエリのラムダ式の外や `where` の中での `Agg.*` の呼び出し | `select` / `having` で使う |

<a id="sort-and-paging"></a>
### ソートとページング

| サポートしていない | 代わりの書き方 |
|---|---|
| ソートのラムダ式の中の条件やループ（`if (x) asc(...)`） | クエリを 2 つ作るか、`Direction` をキャプチャした値として渡す: `sort(o.price, dir)` |
| 結合のソートのラムダ式の中のプロパティ参照 | ラムダ式 |
| `orderBy` のプロパティ参照（`orderBy(Order::price)`） | ラムダ式: `orderBy { it.price }` |
| ソートの名前と方向を一緒に持つ 1 つの文字列（`"price,desc_nulls_last"`） | 方向は `Direction.parse(text)`、名前は `SortSpec.ofPath`。名前はソートポリシーがチェック（[4.10](04-order-paging.md#sort-policy)） |
| 結合結果の `distinct()` で、結合したエンティティのカラムや関連のパスでソート（`LQL-3046`） | 主エンティティ自身のカラムでソートするか、`exists` サブクエリを使う |
| `limit` / `offset` と一緒に使うコレクションの fetch join | 先にページングし、それからコレクションを読み込む |
| Spring の `Sort` の `ignoreCase()`（[12.5](12-spring-data-jpa.md#125-spring-sort-and-pageable)） | `ignoreCase()` のないソート |
| 関数のキー（`lower(name)`） | – |
| 集約の別名でのソート（`desc(s.total)`） | – |
| ソートキーとして使う boolean のエルビス | キー `o.flag` に NULL の位置を指定: `asc(o.flag, Nulls.LAST)`（NULL が最後） |

<a id="limits-subqueries"></a>
### サブクエリ

| サポートしていない | 代わりの書き方 |
|---|---|
| 1 行でないスカラーの比較での `first()` | 集約（`max`、`min`、`avg`、`sum`、`count`）か `orderBy { }.limit(1)...first()` を使う |
| `limit` のないサブクエリの中の `orderBy` | 効果がないので取り除かれる（WARN） |

<a id="select-and-group-by"></a>
### 選択とグループ化

| サポートしていない | 代わりの書き方 |
|---|---|
| `select` / `having` の中で、`groupBy` のキーでない集約でない値 | `groupBy` に追加する |
| 派生テーブルや CTE の行の型として使う普通のクラス | Kotlin の data class（または Java の record） |
| `groupBy` のキーとして使う `arrayOf(...)` / `setOf(...)` | `listOf(...)`、`Pair`、`Triple` |
| select の値として使う boolean のエルビス（`LQL-2022`） | `it.vip` を選択し、NULL は Kotlin で処理する |

<a id="limits-update-insert-delete"></a>
### 更新、挿入、削除

| サポートしていない | 代わりの書き方 |
|---|---|
| `where` のない `update` / `delete` | `.all()` を追加する |
| 同じ文で設定した後でそのカラムを読む | 意図して古い値を使うか、update を 2 回実行する |
| 同じカラムを 2 回設定する | 代入は 1 つ |
| 設定するカラムを選ぶための `set` のラムダ式の中の `if` | `set()` を何回も呼ぶ |
| `set` の中の `++`、`--`、`apply`、`with`、`also` | `it.cnt += 1`、直接の代入 |
| 代入のラムダ式の中のローカルの `val` | 各場所に式を書く |
| 関連の `@Id` でないプロパティの設定（`it.customer?.name = x`） | `Customer` を直接更新する |
| 任意でないカラム（`@Id`、`nullable = false`、プリミティブ型）に `null` | – |
| `@Version` プロパティへの直接の書き込み | lambdaQL が代わりに `update versioned` を使う |
| 値の中の文字列テンプレート（`LQL-2007`） | 文字列はラムダ式の外で作ってキャプチャする |
| update や delete の明示的な結合 | 追加しない。関連のパスか `sub` を使う |
| 明示的な結合のある `insert ... select` | 多対一のパスを使う |
| 同時の挿入でアトミックな `onConflict { ... }.ignore()` | キーの重複エラーを処理する（`ignore()` は `not exists` を使う。[7.6](07-6-upsert.md) を参照） |
| `insert ... select` で行う upsert | `values(list) { ... }` で行ごとに upsert |
| `limit(n)` なしで update や delete に `orderBy { ... }` を指定 | `limit(n)` を追加するか、`orderBy` を取り除く（それ以外は `IllegalStateException`） |
| 1 行だけ変更すべき update や delete を `execute() == 1` とロールバックでチェック | `executeOne()`: 先に id を最大 2 つ読む。2 つなら何も変更せず `NonUniqueResultException`。1 つなら `id = :p and (条件)` でその行を変更。2 行以上を変更しない |
| `executeOne()` と `orderBy` または `limit` を一緒に使う | どちらか 1 つだけを使う（それ以外は `IllegalStateException`） |
| update や delete の `offset` | 計画なし。DML には `limit(n)` しかない。`where { }`、`orderBy { }`、`limit(n)` で対象の行を指定する |
| 複合キーのエンティティの `limit` または `executeOne()` | まだサポートしていない。先に id を選択してから `delete ... where { it.id in ids }` |
| Spring の共有 `EntityManager` でトランザクションなしに DML を実行（lambdaQL には `@Transactional` がない） | `@Transactional` か `TransactionTemplate` の中で呼ぶ |
| リポジトリーのメソッド `deleteAll { ... }`（一括削除）で cascade や `@PreRemove` を期待 | Spring Data の `deleteAll(Iterable)` か `delete(entity)` を使う（[12.4](12-spring-data-jpa.md#deleteallwhere-is-not-spring-datas-deleteall) を参照） |
| `returning` | DML の後にもう一度選択する |
| DB の時刻（`current_timestamp`） | アプリケーションの時刻を渡す（`LocalDateTime.now()` は値としてバインドされる） |
| `TABLE` の id ジェネレーターを使う `insert ... select` | `values { }` か IDENTITY / SEQUENCE を使う |
| JOINED または TABLE_PER_CLASS のサブクラスへの `insert` | サポートしていない（Hibernate 6.6.5 が失敗、7.4 は未測定） |

DML についてもう 1 つ知っておくこと: 一括の DML は永続コンテキストを通りません。Hibernate が先に flush しますが、
同じセッションの管理エンティティは、その後も古い値を持ち続けます。`clear()`（または `refresh`）を呼んでください。
JPA の cascade と `orphanRemoval` は適用されません。
DML の文にはトランザクションが必要です。`QueryBuilder(EntityManagerFactory)` は自分のトランザクションを開きます。`EntityManager` を使うビルダー（Spring の共有 `EntityManager` を含む）は呼び出し側のトランザクションを使い、トランザクションがなければ SQL を送る前に失敗します。

<a id="procedures"></a>
### プロシージャ

| サポートしていない | 代わりの書き方 |
|---|---|
| ラムダ式の中で、スタブ関数の呼び出し 1 つ以外のすべて（ログ、入れ子の呼び出し、分岐） | 呼び出し 1 つ。引数は先に計算する |
| 計算を含む名前付き引数（`calc(rate = r * 2, id = id)`） | 位置引数を使うか、値を先に計算する |
| OUT パラメーターのあるプロシージャに `exec` | `call` |
| 引数に対する DB のデフォルト値 | すべての引数を渡す |
| スタブ関数の Kotlin のデフォルト引数 | すべての引数を渡し、デフォルト値を取り除く |
| Oracle のパッケージのプロシージャのオーバーロードを 1 つ選んで呼ぶ（オーバーロードのある名前は検出する: `LQL-4044`）、動的なプロシージャ名 | – |
| Oracle の暗黙の結果（`DBMS_SQL.RETURN_RESULT`）、MariaDB のパッケージ、プロシージャの結果のページング | サポートしていない |

<a id="108-caching-a-lambda-is-analyzed-once"></a>
## 10.8 キャッシュ: ラムダ式は 1 回だけ解析される

バイトコードの opcode の読み取りは遅い部分なので、lambdaQL はこれを **ラムダ式ごとに 1 回だけ** 行います。

| 項目 | ルール |
|---|---|
| キャッシュするもの | 解析の結果: HQL のテンプレートとパラメーターの出所のリスト。失敗もキャッシュする |
| キャッシュのキー | ラムダ式の実装クラスとメソッド（名前、シグネチャ、種類）、ラムダ式の型、エンティティクラス、**ClassLoader** |
| サイズ | 制限あり（LRU）。そのため際限なく大きくならず、クラスローダーをリークしない |
| キャッシュしないもの | バイトコードの opcode そのもの（読み直しは安く、保持するには大きい） |
| キャプチャした値 | **キャッシュしない。** クエリを実行するたびに読んでバインドする |
| キャプチャしたオブジェクトに対する呼び出し（`filter.minPrice`、`this.minStatus`、`LocalDateTime.of(...)`） | クエリを実行する **たびに** アプリケーションで実行してから、パラメーター 1 つとしてバインド |
| キャプチャした `var` | クエリを実行するたびに読む |
| キャプチャした値だけを使う null ガード（`lo == null \|\| ...`） | クエリを作るときに値で決める。そのため HQL が値によって変わる（[9.8](09-kotlin-notes.md#98-captured-values) を参照） |
| キャプチャした値として使う `Direction` | バインドの値で、毎回読む（構造はキャッシュされたまま） |
| DML | ラムダ式のクラスごとに 1 回解析。`set()` の呼び出しの間のチェックは小さな要約を使う |

Kotlin のラムダ式の名前（`...$lambda$0`）は、コンパイラーのバージョンやソースの修正で変わることがあります。
問題ありません。キャッシュのキーにはクラス、シグネチャ、クラスローダーも入っていて、lambdaQL は名前の形式に依存しません。

プロトタイプで測定した結果（JDK 21）: 新しいラムダ式の最初の解析には約 **0.2 ms** かかります。
その後、`set()` の呼び出しが 2 つある update は、キャッシュの検索、値の取り出し、チェックに約 **0.1〜0.2 µs** かかり、
200 万回実行した後も同じです。目標: 同じラムダ式を 1,000 回呼んでも、バイトコードの opcode は 1 回だけ読みます。
Hibernate も、HQL の文字列をキーにしてクエリプランをキャッシュします。

利用者にとっての意味:

<!-- region: ManualKotlinRulesTest.kt#cache-choose -->
```kotlin
// good: one lambda, only the value changes (the lambda is analyzed once)
val byStatus = qb.from(Order::class.java).where { it.status.toInt() == status }
assertEquals("select e0 from Order e0 where e0.status = :p1", byStatus.toHql().hql())

// not possible: the shape of the query changes inside the lambda
val error = assertThrows(LambdaQLException::class.java) {
    qb.from(Order::class.java).where { if (flag) it.status.toInt() == 1 else it.isActive }
}
assertEquals("LQL-2021", error.code())

// instead: choose the lambda outside (two lambdas, each analyzed once)
val list = if (flag) {
    qb.from(Order::class.java).where { it.status.toInt() == 1 }.toList()
} else {
    qb.from(Order::class.java).where { it.isActive }.toList()
}
```

コード例: [`ManualKotlinRulesTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinRulesTest.kt)（`cache-choose`）。

<a id="109-tested-environment"></a>
## 10.9 対応環境

| 項目 | バージョン |
|---|---|
| Kotlin | 2.x（設計は kotlinc 1.7.22、2.1.10、2.2.21、2.3.0、2.3.21 で確認） |
| Java（JVM） | 21 以上 |
| Hibernate | 6.6 または 7.4。HQL の拡張（例: `function(...)`、`nulls first`）を使うので、他の JPA 実装は保証しない |
| データベース | H2 2.3、MariaDB 10.11、MySQL 8.4、PostgreSQL 17、Oracle 23.26 (23ai) |
| Spring | Spring Boot 3.5（Spring Data JPA 3.5）と 4.1（Spring Data JPA 4.1）（Spring モジュール、[12](12-spring-data-jpa.md)、[13](../13-spring-boot-versions.md)） |
| 未確認 | 2.3 より新しい Kotlin、Kotlin Multiplatform、SQL Server、Oracle 19c 以下（例: PL/SQL の `BOOLEAN` パラメーター） |

---

[Kotlin トラック](README.md) | 前: [9. Kotlin の注意点](09-kotlin-notes.md) | 次: [11. エラーと言語](../11-errors-and-languages.md) | [このページの Java 版](../java/10-rules-and-limits.md)
