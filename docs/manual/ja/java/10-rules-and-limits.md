<a id="10-rules-and-limits-java"></a>
# 10. ルールと制限（Java）

[Java トラック](README.md) | [マニュアルのホーム](../README.md) | 前: [9. Java の注意点](09-java-notes.md) | 次: [11. エラーと言語](../11-errors-and-languages.md) | [このページの Kotlin 版](../kotlin/10-rules-and-limits.md)

共通ページ: [1. 概要](../01-overview.md) · [11. エラーと言語](../11-errors-and-languages.md)

---

<a id="contents"></a>
## 目次

- [10.1 基本ルール: ラムダ式はクエリ](#101-the-base-rule-a-lambda-is-a-query)
- [10.2 SARGable ルール（インデックスを使える SQL）](#102-sargable-rule-index-friendly-sql)
- [10.3 NULL のルール](#103-null-rules)
- [10.4 演算子のルール（R1〜R24）](#104-operator-rules-r1-to-r24)
- [10.5 Java と SQL の違い](#105-java-and-sql-differences)
- [10.6 データベースごとの違い](#106-database-differences)
- [10.7 サポートしないものと、代わりの書き方](#107-not-supported-and-what-to-write-instead)
- [10.8 キャッシュ: ラムダ式の解析は 1 回](#108-caching-a-lambda-is-analyzed-once)
- [10.9 サポート環境](#109-tested-environment)

例の読み方:

- `assertEquals` の文字列が、lambdaQL が組み立てる HQL です。
- ラムダ式に直接書いた定数（数値、文字列、boolean、`null`、enum 定数）は、HQL にリテラルとして書きます。
  キャプチャ値と計算した値は、常にバインドパラメーター（`:p1`、`:p2`、...）です。
- エンティティ: `Order`（`id`、`price`、`status`、`active`、`description`、`customer`、`updateAt`）、
  `Customer`（`id`、`name`、`grade`、`orders`）、`Item`（`order`、`price`、`qty`）。
  10.3 の行は、`Order` の属性 `cnt`（`Integer`）、`custName`（`String`）、`address`（`@Embedded`、`city` と `zip`）も使います。
  これらの行では、`active` は NULL になりうる `Boolean` で、getter は `getActive()` です。

---

<a id="101-the-base-rule-a-lambda-is-a-query"></a>
## 10.1 基本ルール: ラムダ式はクエリ

lambdaQL は条件、ソート、代入のラムダ式を **呼び出しません**。バイトコードの opcode を解析して HQL を書きます。
キャプチャ値だけでできた式と、`select` のコンストラクター呼び出しは Java で計算します。
ラムダ式は Java の構文で書いたクエリです。そのため:

1. `o.getCustomer().getName()` のようなパスは **カラムを指します**。呼び出しの連鎖ではありません。
   getter を呼び出さないので、`NullPointerException` は起きません。このパスの比較は、顧客のある注文でだけ真になります。
   `where` では、顧客のない注文が条件に合いうるなら `left join`、合いえないなら inner join です。結果は、ラムダ式をリストに対して実行した結果と同じです。
   null ガード（`o.getCustomer() == null || ...`）を書くと、顧客のない注文も選ばれます。
   そのような注文を探すには、関連そのものを比較してください: `o.getCustomer() == null`。
2. NULL は **直接書いた場所でだけ** 扱います（`== null`、`!= null`）。
   それ以外の NULL は SQL のルールに従います。比較する値が NULL の行は返りません。
3. Java のメモリのルール（オブジェクトの同一性、ボクシングのキャッシュ、文字列の intern）は適用されません。
   オブジェクト同士の `==` は **値の等価性** を意味します。
4. **解析できないラムダ式はエラーです。** lambdaQL はクエリとして解析できないと、コード `[LQL-xxxx]` とソースの行を含む例外を投げます。

<a id="102-sargable-rule-index-friendly-sql"></a>
## 10.2 SARGable ルール（インデックスを使える SQL）

「SARGable」とは、データベースがその条件にインデックスを使えるという意味です。
lambdaQL の基本ルールです（設計の 3.9 節）。

| # | ルール |
|---|---|
| 1 | **カラムの側に関数、演算子、`CASE` を置きません。** 値を変える必要があれば、バインドする前に Java でパラメーターを変えます（例: `Locale.ROOT` で小文字に変換） |
| 2 | 2 つの形が同じ意味なら、単純な比較と `AND` / `OR` / `NOT` だけの形を選びます。正しい 3 値論理が先で、その中で SARGable な形を選びます |
| 3 | レンダリングのルールは、H2、MariaDB、Oracle の `EXPLAIN` の結果を基準に決めます |

<a id="examples"></a>
### 例

**単純な `=` と `<>` を使う等価比較**

<!-- region: ManualJavaRulesTest.java#plain-equals -->
```java
// when the query runs, text is not null
SelectQuery<Order> same = qb.from(Order.class).where(o -> o.getDescription() == text);
assertEquals("select e0 from Order e0 where e0.description = :p1", same.toHql().hql());
SelectQuery<Order> other = qb.from(Order.class).where(o -> o.getDescription() != text);
// a NULL description matches neither
assertEquals("select e0 from Order e0 where e0.description <> :p1", other.toHql().hql());
```

lambdaQL は `is [not] distinct from` を書きません。この形は Oracle ではインデックスを使えない `decode(...)` になります。
エンティティの値は SQL の意味に従うので、単純な `=` や `<>` が常に正しい形です。キャプチャ値の `null` は NULL としてバインドされ、どの行にも一致しません（`WARN` レベルの `LQL-4045`）。

**boolean カラムと条件の比較** では、条件の側が `case when` になり、インデックスを使えません（`WARN` レベルの `LQL-5013`）。2 つの場合を `&&` と `||` に分けて書くと、`AND`/`OR` になります。

<!-- region: ManualJavaWhereTest.java#boolean-compare -->
```java
int s = 1;
// a boolean column compared with a condition: the condition becomes case when
SelectQuery<Order> compared = qb.from(Order.class).where(o -> o.isActive() == (o.getStatus() > s));
assertEquals("select e0 from Order e0 where e0.active = case when e0.status > :p1 then true else false end",
        compared.toHql().hql());
// the same rows with && and ||: plain comparisons that an index can serve
SelectQuery<Order> query = qb.from(Order.class)
        .where(o -> (o.isActive() && o.getStatus() > s) || (!o.isActive() && o.getStatus() <= s));
assertEquals("select e0 from Order e0 where (e0.active = true and e0.status > :p1)"
        + " or (e0.active = false and e0.status <= :p2)", query.toHql().hql());
```

`CASE` の形（`e0.active = case when ... end`）は正しい行を返しますが、フルスキャンが必要なので、
lambdaQL は使いません。

**サブクエリを使う NOT IN** は `NOT IN` として書きます（SQL の意味）。サブクエリに NULL があると、行は返りません。NULL を飛ばすには、サブクエリに null のチェックを追加してください（10.3 を参照）。

<a id="what-the-measurements-showed"></a>
### 測定の結果

MariaDB 10.11 と Oracle 23ai、20,000〜50,000 行:

| 形 | MariaDB | Oracle | OK? |
|---|---|---|---|
| `_ci` カラムに対する `name = 'abc'` | インデックスの検索 | – | はい |
| `LOWER(name) = LOWER('abc')` | インデックスのフルスキャン（19,791 行） | テーブルのフルスキャン（関数インデックスなし） | **いいえ** |
| `lower(name) = :p_lowered` + 関数インデックス | – | インデックスのレンジスキャン（`LIKE 'ab%'` も） | はい |
| `name COLLATE BINARY_CI = :p` + 言語インデックス | – | インデックスのレンジスキャン | はい |
| `AND`/`OR` で書いた boolean の `==` | range（350 行） | インデックスのレンジスキャン 2 回 + ビットマップ OR | はい |
| `CASE` で書いた boolean の `==` | インデックスのフルスキャン（49,648 行） | テーブルのフルスキャン | **いいえ** |

<a id="db-functions-on-a-column"></a>
### カラムに対する DB 関数

カラムの側の DB 関数（`OrderFns.discount(o.getPrice()) > 10`）は、通常のインデックスを使えません。
lambdaQL は **WARN**（`LQL-5001`）を記録します。例外: Oracle で同じ式の関数ベースのインデックスがあれば記録しません。
MariaDB では `NOT DETERMINISTIC`（デフォルト）の関数は、引数がパラメーターだけでもフルスキャンになるので（`o.getPrice() > OrderFns.discount(rate)`）、この場合も警告します（`LQL-5007`）。
`SargableMode.STRICT` では、警告の代わりに解析が失敗します（`LQL-3066`）。

**SARGable モード**。`SargableMode` には 3 つの値があります。

- `OFF`: チェックせず、メタデータも読みません。
- `WARN`（デフォルト）: 警告を記録します。
- `STRICT`: `WARN` なら警告を記録する場合（`LQL-5001`、`LQL-5007`、`LQL-5012`）に、解析が `org.lambdaql.NonSargableException`（`LQL-3066`）で失敗します。

persistence unit ごとに `SargablePolicy.register(emf, SargableMode.STRICT)` で指定し、`SargablePolicy.unregister(emf)` で解除します。Spring では `lambdaql.sargable.mode=STRICT` を使います（[12](12-spring-data-jpa.md) を参照）。
チェックはラムダ式ごとに 1 回、解析結果を共有キャッシュに入れるときに実行されます。そのため、最初のクエリの前にモードを登録してください。

**カラムに対する算術**。

- 1 つのカラムと値だけでできた `+ - * /`（`o.getCnt() * 2 > n`）は、値の側に移せます。lambdaQL は `LQL-5012` を `WARN` レベルで記録し、`STRICT` ではエラーです。
- カラム同士の算術（`o.getPrice() * o.getCnt() > n`）と `%` は移せないので、`LQL-5013` を `DEBUG` レベルで記録するだけです。`STRICT` でも許します。
- 移した形は意味が同じでなければなりません。`o.getCnt() > n / 2.0` のように小数で割ってください。整数の割り算 `n / 2` は、`>=`、`=`、`<` と負の `n` で違う行を返します。負の数を掛けた場合は、比較の向きを逆にしてください。`LQL-5012` のメッセージがこれを案内します。
- インデックスがあるかを調べて警告を減らす方式は採用していません。権限とシノニムのせいで「インデックスなし」と「不明」が混ざり、カラムの算術はまれな形だからです。
[8. プロシージャと DB 関数](08-procedures-functions.md) を参照してください。

コード例: [`ManualJavaRulesTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaRulesTest.java)（`plain-equals`）、[`ManualJavaWhereTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaWhereTest.java)（`boolean-compare`）。

<a id="103-null-rules"></a>
## 10.3 NULL のルール

| ラムダ式 | SQL | NULL のときの動作 |
|---|---|---|
| `o.getId() == 10L`（アンボクシング、片方がプリミティブ型） | `e0.id = 10` | NULL: 行は返らない |
| `o.getId() == p`（2 つのオブジェクト: `Long`、`String`、enum、...） | `e0.id = :p1` | カラムが NULL: 選ばれない。キャプチャ値の `null`: 一致する行なし（`= NULL`、`WARN` レベルの `LQL-4045`） |
| `o.getDescription() != p` | `e0.description <> :p1` | カラムが NULL: 選ばれ **ない**（SQL の意味、. Java なら選ばれる）。キャプチャ値の `null`: 一致する行なし |
| `o.getDescription() == o.getCustName()`（エンティティの値 2 つ） | `e0.description = e0.custName` | どちらかが NULL: 選ばれない。NULL と NULL も同じ（SQL の意味） |
| `o.getDescription().equals(p)` | `e0.description = :p1` | description が NULL: 行は返らない |
| `Objects.equals(o.getDescription(), p)`、`"x".equals(o.getDescription())`、`Boolean.TRUE.equals(o.getActive())` | `==` と同じ | `==` と同じ |
| `!Boolean.TRUE.equals(o.getActive())` | `e0.active <> :p1` | NULL: 選ばれない（SQL の意味） |
| `!o.getActive()`（nullable な `Boolean`） | `e0.active = false` | NULL: 選ばれない（NULL は false ではない） |
| `x == null`、`x != null` | `is null`、`is not null` | 2 値 |
| `o.getCnt() >= 2.5`（整数のカラム、小数の値） | 書いた値のまま比較: 定数はリテラル（`e0.cnt >= 2.5`）、キャプチャ値と IN リストも小数のリテラル（`e0.cnt >= 2.5BD`、`in (2.5BD, 3.0BD)`） | インデックスのレンジスキャンを維持（MariaDB で実測）。以前のように値を切り捨てて誤った行を返すことはない |
| 値オブジェクト（`@Embedded`）全体: `o.getAddress().equals(addr)`、`Objects.equals(o.getAddress(), addr)`、`!o.getAddress().equals(addr)` | カラムごとに展開: `e0.address.city = :p1 and e0.address.zip = :p2`、否定は `<> ... or ...`。`== null` はすべてのカラムが `is null`（and）、`!= null` は `is not null`（or） | カラムごとに SQL の意味: NULL のカラムは `=` にも `<>` にも一致しない。キャプチャしたオブジェクトが `null` なら一致する行なし（`WARN` レベルの `LQL-4045`） |
| NULL になれないカラム（id、プリミティブ型、`nullable = false`、パスに関連の段階がない）の `o.getId() == null` | `1=0`。比較と一緒に書くとチェックを取り除く: `o.getId() != null && o.getId() == id` は `e0.id = :p1` | カラムに NULL がない。関連を通るパス（`o.getCustomer().getId()`）はチェックを維持 |
| `!`、`&&`、`\|\|` | `NOT`、`AND`、`OR` | SQL の 3 値論理 |
| `where` の中の `o.getCustomer().getName().equals(p)` | `j1.name = :p1` + `join e0.customer j1` | 顧客がいなければ注文は選ばれない |
| `o.getStatus() == 1 \|\| o.getCustomer().getName().equals(p)` | `e0.status = 1 or j1.name = :p1` + `left join e0.customer j1` | 顧客がいなくても status が 1 なら選ばれる |
| `o.getCustomer() == null \|\| o.getCustomer().getName().equals(p)` | `e0.customer is null or j1.name = :p1` + `left join e0.customer j1` | 顧客がいなければ選ばれる（Java と同じ） |
| `o.getCustomer() == null \|\| o.getCustomer().getName() == null` | `e0.customer is null or (e0.customer is not null and j1.name is null)` + `left join e0.customer j1` | パスを通る `is null` の比較には `e0.customer is not null` を加える |
| `orderBy` や `select` にだけ書いた `o.getCustomer().getName()` | `j1.name` + `left join e0.customer j1`。必須の関連（`optional = false`）は `join` | 顧客がいなければカラムは NULL |
| `o.getCustomer() == null`、`o.getCustomer() != null` | `e0.customer is null`、`e0.customer is not null`（FK カラム、結合なし） | 顧客のない注文 / 顧客のある注文 |
| `o.getCustomer().getId()` | `e0.customer.id`（FK カラム、結合なし） | 顧客がいなければ NULL |
| `ids.contains(o.getId())` | `e0.id in (:p1)` | id が NULL: 選ばれない |
| `!ids.contains(o.getId())` | `e0.id not in (:p1)`。NULL になりうるカラムも同じ | NULL の値: 選ばれ **ない**（SQL の意味、. `DEBUG` レベルの `LQL-5009`。Java なら選ばれる） |
| `!...contains(x)` のあるサブクエリ | `x not in (select ...)` | サブクエリに NULL があると、どの行も選ばれない（SQL の意味） |
| `Agg.sum`、`avg`、`min`、`max` | `sum(...)` ... | 行がないか、すべての値が NULL なら NULL |
| `Agg.count()` | `count(*)` | 行がなければ 0 |

<a id="captured-null-values"></a>
### null のキャプチャ値

| キャプチャ値の位置 | `null` を許す? |
|---|---|
| オブジェクト同士の `==` / `!=`、`Objects.equals`、`col.equals(p)`、`p.equals(col)` | はい。ただし一致する行はない（`= NULL`）。クエリの実行時に `LQL-4045` を `WARN` レベルで記録 |
| 同じ値に対する null ガードの下: `p == null \|\| o.getStatus() > p` | はい。ガードはクエリの実行時に Java で決まります。`p == null` ならフィルターなし、他の値なら `e0.status > :p1`。自分のプリミティブ型へのアンボクシングだけの比較は、ガードの位置に関係ない |
| アンボクシングの比較（`o.getPrice() > p`、`Double p`） | はい。ただし一致する行はない（`> NULL`）。自分のプリミティブ型へのアンボクシングは実行しない。クエリの実行時に `LQL-4045` を `DEBUG` レベルで記録 |
| キャプチャ値に対するメソッド呼び出し（`p.trim()`）、別のプリミティブ型に変えるアンボクシング（`p.intValue()`、`Double p`） | **いいえ。** Java で値を計算するときにエラー（`LQL-4012`） |
| `in` に使うコレクションの `null` の要素 | **いいえ。** バインドするときにエラー |
| `NaN`、`Infinity` | **いいえ。** 常にエラー |

<a id="104-operator-rules-r1-to-r24"></a>
## 10.4 演算子のルール（R1〜R24）

設計（3.6 節）のルール表を縮めたものです。

| # | ラムダ式の形 | 意味 |
|---|---|---|
| R1 | プリミティブ型の比較、または片方がプリミティブ型（`o.getId() == 10L`、`o.getStatus() > p`） | `=`、`<>`、`<`、... SQL の NULL のルール |
| R2 | オブジェクトの `==` / `!=`（`Long`、`String`、`Boolean`、enum） | オブジェクトの同一性ではなく値の等価性。SQL の意味の `=` / `<>`。キャプチャ値の `null` はどの行にも一致しない |
| R3 | `x == null`、`x != null` | `IS NULL`、`IS NOT NULL` |
| R4 | `a.equals(b)` | `a = b`、SQL の NULL のルール |
| R5 | `Objects.equals(a, b)`、`"x".equals(a)`、`Boolean.TRUE.equals(a)` | R2 と同じ |
| R6 | `!`、`&&`、`\|\|` | `NOT`、`AND`、`OR`。SQL の 3 値論理 |
| R7 | 文字列の値の比較 | **DB の照合順序** に従う（大文字小文字、末尾の空白）。Oracle は `''` を NULL として扱う |
| R8 | エンティティ `==` エンティティ | 拒否される（`LQL-3011`）。id を比較する |
| R9 | キャプチャ値 | 実行時の値を持つバインドパラメーター |
| R10 | 属性へのアクセス | getter の呼び出し、JPA の属性にマッピング |
| R11〜R23 | Kotlin のルール | [このページの Kotlin 版](../kotlin/10-rules-and-limits.md) を参照 |
| R24 | 関連のパス（`o.getCustomer().getName()`） | カラム。to-one の関連: where、order by、select が共有するエイリアス。`where` では、関連のない行が条件に合いうるなら `left join`、合いえないなら inner join（結果はラムダ式をリストに対して実行した結果と同じ）、order by や select にだけ書くと `left join`（必須の関連 `optional = false` は inner join、A4）。Embedded: 同じテーブル。対象の `@Id` で終わるか、関連を `null` と比較: FK カラム、結合なし。コレクション: エラー |

その他のルール:

- `x.compareTo(y) > 0`、`isBefore`、`isAfter` は `x > y` などになります。
- ラムダ式に直接書いた定数（コンパイル時の `static final` 定数を含む）はリテラルです: `o.getPrice() > 100` は `e0.price > 100.0`、`"O'Brien"` は `'O''Brien'`。キャプチャ値と計算した値（`new BigDecimal("1.5")`）はバインドパラメーターです。例外: 整数のカラムと比べる小数のキャプチャ値は、小数のリテラル（`2.5BD`）です。
- javac はときどき `!` の位置を動かします（ド・モルガン、二重否定）。それでも結果は変わりません。
  lambdaQL は各条件をちょうど 1 回ずつ残し、論理を「単純化」しません。
- Java では常に真になる条件（`x > 5 || !(x > 5)`）を書いても、単純化しません。
  SQL では `x` が NULL の行が抜けます。これが SQL のルールです。
- IntelliJ は `o.getId() == p` に「Number objects compared using `==`」の警告を出します。lambdaQL のラムダ式の中では、
  これは意図した形（値の等価性）です。メソッドに `@SuppressWarnings("NumberEquality")` を宣言すると、警告を消せます。

どの Java の型が R1 になり、どの型が R2 になるかは、[9.3](09-java-notes.md#93-equality-of-boxed-values) を参照してください。

<a id="105-java-and-sql-differences"></a>
## 10.5 Java と SQL の違い

これらの違いは SQL そのものから来ます。コンパイラーの問題ではありません。
「G」の番号は設計（2.7 節）の ID です。

| # | 違い | lambdaQL がすること |
|---|---|---|
| G1 | `status` が NULL のとき、Java では `!Objects.equals(status, 1)` は true だが、SQL の `status <> 1` はその行を返さない | `status <> 1` としてレンダリング: SQL の意味。NULL の行は除かれ、`LQL-5009` を `DEBUG` レベルで記録。このような行を残すには `status == null \|\| ...` と書いてください |
| G2 | `!Boolean.TRUE.equals(active)`、`!active`（nullable）と Java | `active <> :p1`、`active = false` としてレンダリング: NULL の Boolean は false ではない（SQL の意味） |
| G3 | NULL のパラメーターを使う `name = ?` は 0 行を返す | そのまま維持（SQL の意味）。実行時に `LQL-4045` を `WARN` レベルで記録。NULL を探すには `x == null` と書いてください |
| G4 | `status NOT IN (1, NULL)` は常に 0 行を返す | キャプチャしたコレクションの `null` はエラー。サブクエリの `NOT IN` は SQL の意味を維持: サブクエリに null のチェックを追加してください |
| G5 | エンティティのカラムの値が NULL の場合 | ドキュメントに記載: その行は SQL のルールに従って返らない |
| G6 | `NaN`: H2 は NaN を最大の値としてソートし `NaN = NaN` が true、MariaDB は保存できない、Oracle は `BINARY_DOUBLE` にだけ保存できる | キャプチャした NaN や Infinity はエラー。保存された NaN は DB に従う |
| G7 | `float` カラムと `double` の値の比較: DB ごとに結果が違う | float/double について **Java とビット単位まで同じ結果は約束しない**。lambdaQL は `CAST` を追加しない。カラムのマッピングを自分で選んでください（例: Oracle の `binary_float` / `binary_double`） |
| G9 | `x > 5 \|\| !(x > 5)` は NULL の行を除く | 単純化せず、書いたとおりにレンダリング |
| G10 | SQL は評価の順序を約束しない: `qty != 0 && total / qty > 2` でもゼロ除算になりうる | 書いたとおりにレンダリング。ガードに頼らず、割り算なしで比較してください（[3.12](03-where.md#312-errors-that-depend-on-order-of-evaluation)） |
| G11 | 文字列: MariaDB の `_ci` は大文字小文字と末尾の空白を無視、Oracle は `''` を NULL として保存、Oracle の `NLS_SORT` は JVM のロケールに従う | ドキュメントに記載: 文字列の比較とソートは DB に従う。Oracle では `NLS_SORT=BINARY` を設定してください（セッションの初期化か `-Duser.language=en`）。Oracle では `"".equals(x)` と `isEmpty()` の動作が違う |
| G12 | オブジェクトに対する Java の `==` は同一性（128 以上の `Long`、`new String` は false） | lambdaQL は `==` を値の等価性と定義 |
| G13 | 0/1 以外の値を入れたレガシーの boolean カラム（MariaDB の `TINYINT(1)`、Oracle の `NUMBER(1)`）は違う読まれ方をする | Hibernate が作った DDL（`bit(1)`、Oracle 23 の `boolean`）は問題ない。レガシーのスキーマには注意してください |
| G14 | Oracle 23 のバグ: 複合条件に対する `WHERE (a AND b) = false` / `IS FALSE` / `IS NULL` は行を失う | lambdaQL は複合条件を boolean のテストで包まない。否定は常に `NOT(...)` |
| G15 | Oracle の `CHAR` カラムは `String` のバインドと一致しない | ドキュメントに記載: エンティティのマッピングに注意してください |

その他の違い:

| 違い | Java | SQL | lambdaQL |
|---|---|---|---|
| `BigDecimal` の等価性 | `equals` はスケールを比べる（`1.0` と `1.00` は false） | `=` は値を比べる | 値の等価性 |
| 整数の割り算 | `5 / 2` は `2` | MariaDB は `2.5` | DB による。カラムを含む整数の `/` は避けてください |
| `BigDecimal` の割り算と関数の結果 | Java のスケールのルール | DB がスケールを決める（例: 関数の結果について H2 は 1、MariaDB と Oracle は 0） | ドキュメントに記載 |
| Unicode の大文字小文字の無視（`ſ`、`İ`） | Java のルール | DB の照合順序 | DB に従う |
| 文字列の長さ | UTF-16 の単位 | H2 は UTF-16 の単位、MariaDB はコードポイント | ドキュメントに記載 |
| NULL と文字列の連結 | `"null"` というテキスト | NULL（Oracle: `NULL \|\| 'x'` は `'x'`） | ラムダ式の中の文字列の連結は拒否される（`LQL-2007`） |
| ラムダ式の中の `LocalDateTime.now()` | アプリの時刻 | – | **アプリの時刻** としてバインド。DB の時刻（`current_timestamp`）はサポートしない |
| `Set` の同じ要素 | `equals` / `hashCode` が決める | 主キーで行を区別する | Set のサイズが行数より小さいと、`toSet()` と `findSet()` が `IllegalStateException` を投げる（エンティティの `equals` / `hashCode` が異なる行を等しいと判断するか、`@Id` が一意でない場合。例: ビュー）。`toList()` を使う |

<a id="106-database-differences"></a>
## 10.6 データベースごとの違い

lambdaQL は多くの違いを隠します。知っておくべきものもあります。

<a id="sorting-and-paging"></a>
### ソートとページング

| 項目 | H2 | MariaDB | Oracle | lambdaQL |
|---|---|---|---|---|
| NULL の位置、`ASC` | 先頭 | 先頭 | **末尾** | すべての DB で `ASC` は NULLS FIRST |
| NULL の位置、`DESC` | 末尾 | 末尾 | **先頭** | すべての DB で `DESC` は NULLS LAST |
| `NULLS FIRST/LAST` の構文 | あり | **なし**（構文エラー） | あり | MariaDB では Hibernate が `CASE` でエミュレート: ソートに **インデックスを使えない** |
| ページングの SQL | `offset ? rows fetch first ? rows only` | `limit ?,?` | `offset ? rows fetch first ? rows only` | Hibernate に渡す（`setFirstResult` / `setMaxResults`） |
| 同じ値を持つ行の順序 | – | – | ランダム | ページングでキーに id がなければ、lambdaQL がルートの `@Id asc` を追加。一意で NULL になれないキー（`unique = true` かつ `nullable = false`、`@NaturalId`）があれば追加しない |
| 文字列の順序 | 大文字小文字を区別 | `_ci` | `NLS_SORT`（JVM のロケール） | DB に従う |
| `order by` の暗黙のパス | inner join（行を失う） | 同じ | 同じ | lambdaQL は `left join` を使う（必須の関連は inner join、または同じパスの `where` の結合） |
| `order by`、`distinct`、`group by` の `@Lob` カラム | 動く | 動く | **ORA-22848 / ORA-22849** | クエリを変えずに `LQL-5010` を `DEBUG` レベルで記録 |
| `@Enumerated(EnumType.STRING)` の enum カラムのソート | 名前順 | 名前順 | 名前順 | クエリを変えずに `LQL-5011` を `DEBUG` レベルで記録。宣言順になるのは `ORDINAL` のマッピングだけ |

lambdaQL は、NULL になりうるすべてのキーについて HQL に `nulls first` / `nulls last` を書きます。必須の関連だけを通り、NULL になれないパスには書きません。データベースのデフォルトと同じなら、Hibernate がこれを省きます。
デフォルト（`ASC` = NULLS FIRST、`DESC` = NULLS LAST）は H2 と MariaDB と同じなので、そちらでは SQL を追加する
必要はありません。`NULLS` 句が付くのは Oracle だけです。NULL の位置を変えるには、
`asc(key, Nulls.LAST)`（NULL が末尾）か `desc(key, Nulls.FIRST)`（NULL が先頭）を使ってください。MariaDB ではこれらは `CASE` のエミュレーションを使います。

<a id="conditions-and-values"></a>
### 条件と値

| 項目 | H2 | MariaDB | Oracle |
|---|---|---|---|
| `is not distinct from` | ネイティブでサポート | `<=>`（インデックスを使う） | `decode(...)`（インデックスなし）。lambdaQL はこの形を書かない |
| `is [not] true`、`coalesce(boolean, false)` | 動く | 動く | **ORA-00932**。lambdaQL は書かない |
| 空文字列 `''` | 値 | 値 | **NULL** |
| 大文字小文字と末尾の空白 | 厳密に一致 | `_ci`: 無視 | 厳密に一致（ソートは `NLS_SORT`） |
| `NaN` | 保存できる、最大の値 | 保存できない | `BINARY_DOUBLE` のみ |
| `float` カラム | REAL リテラルのルール | double に拡張される | Hibernate が `Float` を `float(24)`（10 進数）にマッピング |
| 整数の `/` | 整数 | **小数**（`5/2 = 2.5`） | – |
| 大きな `IN` リスト（値 1,500 個） | 動く | 動く | 23ai では動く（Oracle 22 以前の 1,000 個の制限は測定していない） |

<a id="subqueries"></a>
### サブクエリ

| 項目 | 何が起こるか | lambdaQL |
|---|---|---|
| `limit` のないサブクエリの `order by` | Oracle: 構文エラー（ORA-00907）。MariaDB: 無視される | WARN を出して取り除く |
| MariaDB の `IN (... limit n)` | サポートされない（エラー 1235） | 自動的に包む: `in (select x.id from (... limit n) x)` |

<a id="update-insert-delete"></a>
### 更新、挿入、削除

| 項目 | 何が起こるか | lambdaQL |
|---|---|---|
| `SET a = ..., b = a + 1`（設定した後にカラムを読む） | MariaDB は **新しい** 値を、H2 と Oracle は古い値を読む | 解析時にエラー |
| DML の中の関連のパス（`o.getCustomer().getName()`） | Hibernate が DB ごとに違う結合を作り、顧客のない **行を失う** | lambdaQL は相関スカラーサブクエリを書く（`where` と SET）。`where` では、選択なら inner join になるパスに `customer.id is not null` も加える（`left join` になるパスには加えない）。そのため DML はクエリと同じ行に適用される |
| 同じテーブルに対するサブクエリのある `DELETE` | MariaDB のエラー 1093 | 対象の id を先に派生テーブルとして読む: `d0.id in (select t.id from (select e0.id as id … where <condition>) t)`。相関サブクエリでも動き、3 つの DB で同じ行を削除（実測）。`update` は包まない（MariaDB でも動く） |
| STRING のカラムと ORDINAL のカラムに同じ enum の値を 1 回だけバインド | H2 と MariaDB は **エラーなしで誤った値** を保存 | 位置ごとにパラメーター 1 つ、共有しない |
| `values(list, ...)` の多数の行 | Oracle: バインド 65,535 個の固定の制限（ORA-01745）、それよりずっと前からとても遅い | 1 文あたり値 500 個以下に分割し、すべての文を 1 つのトランザクションで実行 |
| `order by ... limit` のある `update` / `delete` | MariaDB は `in` サブクエリの `limit`（エラー 1235）と、対象テーブルを読むサブクエリ（エラー 1093）を拒否 | `e0.id in (select t.id from (select x0.id as id ... order by ... limit n) t)`: 派生テーブル `t` で 3 つのデータベースすべてで動く。内側の select は選択の結合を使うので、選択と同じ行を対象にする |
| `on conflict` / upsert | H2 と Oracle は `merge`。MariaDB は `insert ... on duplicate key update` で、更新した行を 2 と数える。Hibernate の `do nothing` は H2 と Oracle で失敗 | `upsert` と `onConflict(...).update(...)` は Hibernate の形を使う。`ignore()` は `insert ... select ... where not exists` として書く。行数はそのまま返す |

<a id="procedures-and-functions"></a>
### プロシージャと関数

| 項目 | H2 | MariaDB | Oracle |
|---|---|---|---|
| OUT / INOUT パラメーター | **なし** | あり | あり |
| 複数の結果セット | なし | あり | あり（REF CURSOR） |
| プロシージャの影響を受けた行数 | – | – | 常に -1。そのため lambdaQL は提供しない。OUT パラメーターを使ってください |
| boolean のパラメーターと結果 | – | あり | 23ai 以上 |
| 仮想カラム / 関数インデックスの中のストアド関数 | – | 不可（エラー 1901） | 可（関数ベースのインデックス） |

H2 には OUT パラメーターがないので、プロシージャの機能は MariaDB か Oracle で確認してください。

<a id="107-not-supported-and-what-to-write-instead"></a>
## 10.7 サポートしないものと、代わりの書き方

lambdaQL は、この一覧の形に対してほとんどの場合、はっきりしたエラーを投げます。

<a id="lambda-form-and-loading"></a>
### ラムダ式の形と読み込み

| サポートしない | 代わりの書き方 |
|---|---|
| 匿名クラス、`Serializable` でない関数型インターフェース（`java.util.function.Predicate`） | ラムダ式のリテラル（ソートキーには getter のメソッド参照も可） |
| 条件のメソッド参照（`where(Order::isActive)`、`LQL-1009`） | ラムダ式: `where(o -> o.isActive())` |
| ローカル変数、ループ、`switch`、`try` のあるブロックラムダ式 | 1 つの式。値はラムダ式の外で計算 |
| キャプチャした変数への書き込み | ラムダ式の外で計算（Java でもキャプチャしたローカル変数は実質的に final でなければならない） |
| ECJ（Eclipse のコンパイラー）でコンパイルしたラムダ式 | 保証しない。javac を使う |
| Java 21〜23 で実行するとき、同梱の ASM が知らない新しい JDK 向けにコンパイルしたラムダ式 | ASM を更新（lambdaQL は ASM をシェーディングしない）。Java 24 以上で実行するなら該当しない（`java.lang.classfile` を使う） |

<a id="conditions"></a>
### 条件

| サポートしない | 代わりの書き方 |
|---|---|
| boolean の三項演算子 `c ? x : y` | `&&` / `\|\|`。値を選ぶ三項演算子はサポート: キャプチャ値と定数だけならバインドパラメーター、エンティティの値を使うなら `case when`（[3.5.1](03-where.md#351-values-chosen-by-a-condition)） |
| （サポート: `c ? x : false`、`c ? true : x`、`c ? false : true`） | それぞれ `AND`、`OR`、`NOT` を意味する |
| 短絡評価をしない `&`、`\|`、`^` | `&&`、`\|\|` |
| エンティティ `==` エンティティ（`o.getCustomer() == c`） | `o.getCustomer().getId() == c.getId()`（`LQL-3011`） |
| 値オブジェクト（`@Embedded`）の大小比較、別の型との比較（`LQL-3012`） | フィールドを比較。同じ型との等価比較（`equals`、`==`、`!=`、`null`）はサポート |
| コレクションのパス（`c.getOrders()...`、`@OneToMany`、`@ManyToMany`、`LQL-3010`） | `exists()` サブクエリか結合 |
| `c.getOrders().contains(x)` | `exists()` サブクエリ |
| ラムダ式の中で作ったコレクション（`List.of(1, 2)`） | 外で作ってキャプチャ。（`groupBy` のキーは例外） |
| キャプチャしたコレクションの `null` の要素 | null を取り除く。NULL の行が欲しければ `\|\| x == null` を追加 |
| キャプチャした `NaN` / `Infinity` | クエリの前に値を確認 |
| `isNaN()` | – |
| エンティティの値に対するその他のメソッド呼び出し（`o.items(...)`、自作のメソッド） | カラムで条件を書く |
| 文字列の連結（`o.getDescription() + "_x"`、`"A" + code` も） | 文字列はラムダ式の外で作る（`LQL-2007`） |
| ラムダ式の中のストリームと別のラムダ式 | キャプチャしたコレクションの `contains` かサブクエリ |

<a id="functions-and-strings"></a>
### 関数と文字列

| サポートしない | 代わりの書き方 |
|---|---|
| エンティティの値に対する Java の `String` のメソッド（`trim`、`toUpperCase` など、`LQL-3008`）。`startsWith`、`endsWith`、`contains` は `like` としてサポート | – |
| 日付の関数（`YEAR(col) == 2024`） | 範囲: `o.getUpdateAt() >= from && o.getUpdateAt() < to` |
| ジェネリックな戻り値の型（`<X> X nvl(X, X)`）、エンティティ、コレクション、`void` を返す `@SqlFunction` | 次のどれか: `Double`、`Integer`、`Long`、`BigDecimal`、`String`、`LocalDate`、`LocalDateTime`、`Boolean` |
| Hibernate の組み込み関数（`COALESCE`、`ROUND`、`SYSDATE`、...）と同じ名前の `@SqlFunction` | 接頭辞かスキーマ名（`PUBLIC.SQ_FN`）を使う |
| DB 関数として使う関数型インターフェースの定数、ミックスイン | インターフェースの `static` メソッド |
| `@SqlFunction` なしでエンティティの値に使う自作の static メソッド | `@SqlFunction` を付けた DB 関数か、カラムで作った条件 |
| ユーザー定義の集約関数やウィンドウ関数 | – |
| クエリのラムダ式の外や `where` での `Agg.*` の呼び出し | `select` / `having` で使う |
| ソートのラムダ式の外での `By` のソート関数の呼び出し（`IllegalStateException`、`LQL-4046`） | `orderBy`、`SortSpec.of`、`then` のラムダ式の中で使う |

<a id="sort-and-paging"></a>
### ソートとページング

| サポートしない | 代わりの書き方 |
|---|---|
| ソートのラムダ式の中の条件やループ（`if (x) asc(...)`） | クエリを 2 つ作るか、`Direction` をキャプチャ値として渡す: `sort(o.getPrice(), dir)` |
| ソート関数と一緒に使う `Direction` の引数（`orderBy(o -> desc(o.getPrice()), dir)`、`LQL-3069`） | `orderBy(o -> sort(o.getPrice(), dir))`、またはキー 1 つ: `orderBy(o -> o.getPrice(), dir)` |
| ソートの名前と向きを一緒に入れた 1 つの文字列（`"price,desc_nulls_last"`） | 向きは `Direction.parse(text)`、名前は `SortSpec.ofPath`。名前はソートポリシーがチェック（[4.10](04-order-paging.md#sort-policy)） |
| 結合の結果の `distinct()` で、結合したエンティティのカラムや関連のパスでソート（`LQL-3046`） | 主エンティティ自身のカラムでソートするか、`exists` サブクエリを使う |
| `limit` / `offset` と一緒に使うコレクションの fetch join | 先にページングしてからコレクションを読み込む |
| Spring の `Sort` の `ignoreCase()`（[12.5](12-spring-data-jpa.md#125-spring-sort-and-pageable)） | `ignoreCase()` なしのソート |
| 関数のキー（`lower(name)`） | – |
| 集約のエイリアスでソート（`desc(s.total())`） | – |

<a id="limits-subqueries"></a>
### サブクエリ

| サポートしない | 代わりの書き方 |
|---|---|
| 1 行でないスカラー比較での `first()` | 集約（`max`、`min`、`avg`、`sum`、`count`）か `orderBy(...).limit(1)...first()` を使う |
| `limit` のないサブクエリの `orderBy` | 効果がないので取り除かれる（WARN） |

<a id="select-and-group-by"></a>
### 選択とグループ化

| サポートしない | 代わりの書き方 |
|---|---|
| `select` / `having` の中の、`groupBy` のキーでない非集約の値 | `groupBy` に追加 |
| 派生テーブルや CTE の行の型として使う通常の DTO クラス | Java のレコード |
| `groupBy` のキーとして使う `Set.of(...)` | `List.of(...)` |

<a id="limits-update-insert-delete"></a>
### 更新、挿入、削除

| サポートしない | 代わりの書き方 |
|---|---|
| `where` のない `update` / `delete` | `.all()` を追加 |
| 同じ文で設定した後にカラムを読む | 意図して古い値を使うか、update を 2 回実行 |
| 同じカラムを 2 回設定 | 代入 1 つ |
| 条件付きの値（`c ? a : b`、トグル `!o.isActive()`） | `where` が違う update を 2 つ使う |
| `set` のラムダ式の中で、どのカラムを設定するかを選ぶ `if` | `set()` を複数回呼ぶ |
| 関連の `@Id` でない属性の設定（`o.getCustomer().setName(x)`） | `Customer` を直接更新 |
| 必須のカラム（`@Id`、`nullable = false`、プリミティブ型）に `null` を入れる | – |
| `@Version` の属性に直接書く | lambdaQL が代わりに `update versioned` を使う |
| 値の中の文字列の連結 | – |
| update や delete の明示的な結合 | 追加しない。関連のパスか `sub` を使う |
| 明示的な結合のある `insert ... select` | 多対一のパスを使う |
| 同時の挿入でアトミックな `onConflict(...).ignore()` | キーの重複エラーを処理（`ignore()` は `not exists` を使う。[7.6](07-6-upsert.md) を参照） |
| `insert ... select` による upsert | `values(list, ...)` で行ごとに upsert |
| `limit(n)` なしで update や delete に `orderBy(...)` を指定 | `limit(n)` を追加するか `orderBy` を取り除く（それ以外は `IllegalStateException`） |
| 1 行だけを変更すべき update や delete を、`execute() == 1` とロールバックでチェック | `executeOne()`: 先に id を最大 2 つ読む。2 つなら何も変更せず `NonUniqueResultException`。1 つなら `id = :p and (条件)` でその行を変更。2 行以上を変更しない |
| `executeOne()` と `orderBy` または `limit` を一緒に使う | どちらか一方だけを使う（それ以外は `IllegalStateException`） |
| update や delete の `offset` | 予定なし。DML には `limit(n)` だけがある。`where`、`orderBy(...)`、`limit(n)` で対象の行を指定 |
| 複合キーのエンティティの `limit` または `executeOne()` | 先に id を選択してから `delete ... where ids.contains(id)` |
| Spring の共有 `EntityManager` でトランザクションなしに DML を実行（lambdaQL には `@Transactional` がない） | `@Transactional` か `TransactionTemplate` の中で呼ぶ |
| リポジトリのメソッド `deleteAll(where)`（一括削除）で cascade や `@PreRemove` を期待する | Spring Data の `deleteAll(Iterable)` か `delete(entity)` を使う（[12.4](12-spring-data-jpa.md#deleteallwhere-is-not-spring-datas-deleteall) を参照） |
| `returning` | DML の後にもう一度選択 |
| DB の時刻（`current_timestamp`） | アプリの時刻を渡す（`LocalDateTime.now()` は値としてバインドされる） |
| `TABLE` の id ジェネレーターを使う `insert ... select` | `values(...)` か IDENTITY / SEQUENCE を使う |
| JOINED や TABLE_PER_CLASS のサブクラスへの `insert` | サポートしない（Hibernate 6.6.5 は失敗、7.4 は未測定） |

DML についてもう 1 つ: 一括 DML は永続化コンテキストを通りません。Hibernate は先に flush しますが、
同じセッションの管理されたエンティティは、その後も古い値を持ち続けます。`clear()`（または `refresh`）を呼んでください。
JPA の cascade と `orphanRemoval` は適用されません。
DML の文にはトランザクションが必要です。`QueryBuilder(EntityManagerFactory)` は自分でトランザクションを開きます。`EntityManager` を使うビルダー（Spring の共有 `EntityManager` を含む）は呼び出し側のトランザクションを使い、トランザクションがなければ SQL を送る前に失敗します。

<a id="procedures"></a>
### プロシージャ

| サポートしない | 代わりの書き方 |
|---|---|
| ラムダ式の中の、スタブメソッドの呼び出し 1 つ以外のすべて（ログ、ネストした呼び出し、分岐） | 呼び出し 1 つ。引数は先に計算 |
| OUT パラメーターのあるプロシージャに `exec` | `call` |
| 引数の DB のデフォルト値 | すべての引数を渡す |
| Oracle のパッケージプロシージャのオーバーロードを 1 つ選んで呼ぶ（オーバーロードのある名前は検出する: `LQL-4044`）、動的なプロシージャ名 | – |
| Oracle の暗黙の結果（`DBMS_SQL.RETURN_RESULT`）、MariaDB のパッケージ、プロシージャの結果のページング | サポートしない |

<a id="108-caching-a-lambda-is-analyzed-once"></a>
## 10.8 キャッシュ: ラムダ式の解析は 1 回

バイトコードの opcode の読み取りは遅い部分なので、lambdaQL はこれを **ラムダ式ごとに 1 回** だけ行います。

| 項目 | ルール |
|---|---|
| キャッシュするもの | 解析結果: HQL のテンプレートとパラメーターのソースの一覧。失敗もキャッシュする |
| キャッシュのキー | ラムダ式の実装クラスとメソッド（名前、シグネチャ、種類）、ラムダ式の型、エンティティのクラス、そして **ClassLoader** |
| サイズ | 制限あり（LRU）。そのため際限なく大きくならず、クラスローダーをリークしない |
| キャッシュしないもの | バイトコードの opcode そのもの（読み直しは安く、保持するには大きい） |
| キャプチャ値 | **キャッシュしない。** クエリを実行するたびに読んでバインド |
| キャプチャしたオブジェクトに対する呼び出し（`order.getDescription().trim()`、`this.field`、`LocalDateTime.of(...)`） | クエリを実行する **たびに** Java で実行し、パラメーター 1 つとしてバインド |
| キャプチャ値だけを使う条件（`p != null && ...`） | 定数に畳み込まず `:p1 is not null` として維持。そのためキャッシュが正しく保たれる |
| キャプチャ値として使う `Direction` | バインドする値、毎回読む（構造はキャッシュされたまま） |
| DML | ラムダ式のクラスごとに 1 回解析。`set()` の呼び出し間のチェックは小さな要約を使う |

プロトタイプで測定した結果（JDK 21）: 新しいラムダ式の最初の解析には約 **0.2 ms** かかります。
その後は、`set()` の呼び出しが 2 回ある update で、キャッシュの検索、値の取り出し、チェックに約 **0.1〜0.2 µs** かかります。
200 万回実行した後も同じです。目標: 同じラムダ式を 1,000 回呼んでも、バイトコードの opcode は
1 回だけ読みます。Hibernate も HQL の文字列を基準にクエリプランをキャッシュします。

利用者にとっての意味:

<!-- region: ManualJavaRulesTest.java#cache-shape -->
```java
// good: one lambda; only the value changes
int status = 1;
SelectQuery<Order> one = qb.from(Order.class).where(o -> o.getStatus() == status);
assertEquals("select e0 from Order e0 where e0.status = :p1", one.toHql().hql());
// rejected: the shape of the query changes inside the lambda
assertEquals("LQL-2021", assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(o -> flag ? o.getStatus() == 1 : o.isActive())).code());
// instead: choose the lambda outside (two lambdas, each analyzed once)
List<Order> list = flag
        ? qb.from(Order.class).where(o -> o.getStatus() == 1).toList()
        : qb.from(Order.class).where(o -> o.isActive()).toList();
```

コード例: [`ManualJavaRulesTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaRulesTest.java)（`cache-shape`）。

<a id="109-tested-environment"></a>
## 10.9 サポート環境

| 項目 | バージョン |
|---|---|
| Java | 21（javac）。ECJ は保証しない |
| Hibernate | 6.6 または 7.4。HQL の拡張（例: `function(...)`、`nulls first`）を使うので、他の JPA 実装は保証しない |
| データベース | H2 2.3、MariaDB 10.11、MySQL 8.4、PostgreSQL 17、Oracle 23.26 (23ai) |
| Spring | Spring Boot 3.5（Spring Data JPA 3.5）と 4.1（Spring Data JPA 4.1）（Spring モジュール、[12](12-spring-data-jpa.md)、[13](../13-spring-boot-versions.md)） |
| 未確認 | SQL Server、Oracle 19c 以前（例: PL/SQL の `BOOLEAN` パラメーター） |

---

[Java トラック](README.md) | 前: [9. Java の注意点](09-java-notes.md) | 次: [11. エラーと言語](../11-errors-and-languages.md) | [このページの Kotlin 版](../kotlin/10-rules-and-limits.md)
