<a id="9-kotlin-notes"></a>
# 9. Kotlin の注意点

> [!IMPORTANT]
> このページは韓国語版のマニュアルより古い内容です。最新のルールは[韓国語のページ](../../ko/kotlin/09-kotlin-notes.md)を参照してください。

[Kotlin トラック](README.md) | [マニュアルのホーム](../README.md) | 前: [8. プロシージャと DB 関数](08-procedures-functions.md) | 次: [10. ルールと制限](10-rules-and-limits.md) | [このページの Java 版: 9. Java の注意点](../java/09-java-notes.md)

共通ページ: [1. 概要](../01-overview.md) · [11. エラーと言語](../11-errors-and-languages.md)

lambdaQL は Kotlin と Java を **同じエンジン**、同じ API でサポートします。
同じ意味の Kotlin のラムダと Java のラムダは、同じ HQL になります。
この章では、Kotlin で特別なことを 1 か所にまとめます。

<a id="contents"></a>
## 目次

- [9.1 設定](#91-setup)
- [9.2 使えるラムダの形](#92-lambda-forms-that-work)
- [9.3 意味を持つのはあなたが書いた null チェックだけ](#93-only-your-null-checks-count)
- [9.4 等価: `==`、`!=`、`===` と `areEqual`](#94-equality----and-areequal)
- [9.5 null 演算子: `?.`、`?:`、`!!`](#95-null-operators---and-)
- [9.6 `in`、範囲、`when`](#96-in-ranges-and-when)
- [9.7 文字列](#97-strings)
- [9.8 キャプチャ値](#98-captured-values)
- [9.9 プロパティ参照とスコープ関数](#99-property-references-and-scope-functions)
- [9.10 Kotlin の演算子](#910-kotlin-operators)
- [9.11 結果: data class、`Pair`、`Triple`](#911-results-data-classes-pair-and-triple)
- [9.12 更新、挿入、削除: プロパティへの代入と `by.set`](#912-update-insert-delete-property-assignment-and-byset)
- [9.13 プロシージャと DB 関数](#913-procedures-and-db-functions)
- [9.14 Kotlin の制限（KC1 から KC21）](#914-kotlin-limits-kc1-to-kc21)
- [9.15 lambdaQL と Kotlin コードの実行との違い](#915-where-lambdaql-differs-from-running-kotlin-code)

例の読み方:

- 例の下のコメントは、設計で決まっている場合に、lambdaQL が作る HQL を示します。
- ラムダの中に書いた定数（数値、文字列、真偽値、`null`、enum 定数、`const val`）は、リテラルとして HQL に書き込まれます。キャプチャした値と計算した値は常にバインドパラメーター（`:p1`、`:p2`、...）になります（D37）。
- `e0` はルートエンティティのエイリアス、`j1` は関連のパスのために lambdaQL が追加する結合のエイリアスです。`where` の中ではデフォルトで内部結合の `join`（null ガードが関連のない行を残すときは `left join`）、パスを `orderBy` か `select` だけで使うときは `left join` です（D36）。サブクエリのルートは `s1`、`s2`、... です（[5. サブクエリ](05-subquery.md) を参照）。予定の機能（派生テーブル、CTE、プロシージャ）の例は、まだ予定のエイリアス `o` と `c1` を使います。
- エンティティは [2. はじめよう](02-getting-started.md#example-entities) のものです。一部の例では `Order` に 2 つのプロパティを追加して使います: `var flag: Boolean? = null` と `var kind: OrderKind? = null`（`enum class OrderKind { NORMAL, EXPRESS, GIFT }`）。

---

<a id="91-setup"></a>
## 9.1 設定

| 項目 | 値 |
|---|---|
| Kotlin | 2.x。設計は kotlinc 1.7.22、2.1.10、2.2.21、2.3.0、2.3.21 で確認しました |
| JVM | Java 21 |
| コンパイラーオプション | デフォルトの `-Xsam-conversions=indy` のままにしてください。`-Xsam-conversions=class` は使わ **ないで** ください（KC1） |
| `-jvm-target` | 1.8、11、17、21 のどれでも動きます |
| Kotlin ランタイム | lambdaQL は依存関係に `kotlin-stdlib` や `kotlin-reflect` を追加しません |
| JPA エンティティ | 普通の Kotlin の JPA エンティティ（フィールドへのアノテーションも問題ありません）。いつもの `kotlin("plugin.jpa")` と all-open プラグインを使います |

詳細: [2. はじめよう](02-getting-started.md#kotlin-compiler-settings)。

<a id="92-lambda-forms-that-work"></a>
## 9.2 使えるラムダの形

lambdaQL の関数は、Java の `Serializable` な関数型インターフェースを受け取ります。
Kotlin では **ラムダリテラル** を渡します。末尾のラムダと `it` を使ってください。

```kotlin
val list = qb.from(Order::class.java)
    .where { it.status == 1 }
    .where { it.price!! > 100.0 }        // 複数の where() 呼び出しは AND で結ばれます
    .toList()
// select e0 from Order e0 where e0.status = 1 and e0.price > 100.0
```

**SQL ブロックの読み方。** `…` は省略した select リスト（エンティティのすべてのカラム）で、`?` はバインドパラメーターです。各行は [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java) から取っています。このテストは各クエリを Hibernate 6.6.5 で H2 2.3、MariaDB 10.11、Oracle 23.26 に対して実行し、Hibernate の SQL が変わると失敗します。 このテストは Java のラムダを使います。意味が同じ Kotlin のラムダは同じ HQL になるので、SQL も同じです。

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `to-hql`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>100.0
```

パラメーターが 2 つ以上あるラムダでは名前を使います。使わないパラメーターには `_` を使います。

```kotlin
.where { o, sub -> sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
.orderBy { o, by -> by.desc(o.price).asc(o.id) }
```

Kotlin のプロパティは getter で読まれます。`it.price` は、Java の `getPrice()` 呼び出しと同じバイトコードです。
lambdaQL は getter を JPA 属性にマッピングします（ルール R10）。
真偽値のプロパティ `active` の getter は `getActive()` です。lambdaQL はこれを `active` にマッピングします。

| 形 | 使えるか |
|---|---|
| `where { it.active }`（ラムダリテラル） | はい |
| `run { }`、`let { }`、`forEach { }`、inline でない関数、`suspend` 関数の中のラムダ | はい |
| 呼び出し側から、lambdaQL の型を持つ `inline fun` のパラメーターに渡したラムダ | はい |
| SAM コンストラクター（先に `val` に入れた場合も） | はい |
| プロパティ参照: `where(Order::active)`、`orderBy(Order::price)` | はい（R23）。`orderBy` ではまだ実装されていません |
| Java のエンティティに対する Java の getter 参照: `orderBy(JOrder::getPrice)` | はい |
| `val f: (Order) -> Boolean = { it.active }; where(f)` | **いいえ**（KC4） |
| `object : ...`（ラムダの代わりのオブジェクト式） | **いいえ**（KC5） |
| `inline fun` の本体の中で作ったラムダ | **いいえ**（KC2） |
| `-Xsam-conversions=class` でコンパイルしたもの | **いいえ**（KC1） |

<a id="93-only-your-null-checks-count"></a>
## 9.3 意味を持つのはあなたが書いた null チェックだけ

lambdaQL は **ラムダを実行しません**。バイトコードを読んで HQL を書きます。
そのため、Kotlin のいくつかのルールは違う動きになります。基本となるのは次の 4 つの決定です。

| 決定 | ルール |
|---|---|
| D20、D36 | `it.customer?.name` のようなパスは、呼び出しの連鎖ではなく **カラム** です。`where` の中では、to-one の関連はデフォルトで内部結合です。customer のない注文は返されません。それらの注文を探すには `it.customer == null` と書きます。customer がなくても条件が true になりうるなら（`it.customer == null \|\| ...` のような null ガード）、パスは `left join` になります。 |
| D22 | lambdaQL は **あなたが書いた null の扱いだけ** を使います: `== null`、`!= null`、`?.`、`?:`、2 つの nullable な値の間の `==`。Kotlin コンパイラーが自分で追加する null チェックは無視します。それ以外の NULL は SQL に従います。条件は UNKNOWN になり、その行は返されません。 |
| D23 | `!!` は無視されます。コードをコンパイルするためだけにあります。`it.customer!!.name` と `it.customer?.name` は同じカラムです。 |
| D12 | オブジェクト同士の `==`（と `===`）は、オブジェクトの同一性ではなく **値の等価** を意味します。 |

lambdaQL は「あなたの」null チェックとコンパイラーのものをどう見分けるのでしょうか。バイトコードが違います。

| ソース | バイトコードの形 | lambdaQL |
|---|---|---|
| `it.status == null` または `it.status != null` と書いた | `getStatus; ifnull / ifnonnull` | 使います: `is null`、`is not null` |
| `status: Int?` で `it.status == 1` と書いた | コンパイラーが `dup; ifnonnull; pop; goto` を追加し、その後アンボクシングします | 隠れたチェックを無視します: `e0.status = 1` |
| `it.price!!` と書いた | `Intrinsics.checkNotNull(...)` | 無視します |

結果として、Kotlin と Java は同じ SQL になります。`it.status == 1` は `e0.status = 1` で、同じ条件を Java で書いても `e0.status = 1` です。

<a id="94-equality----and-areequal"></a>
## 9.4 等価: `==`、`!=`、`===` と `areEqual`

<a id="how-kotlin-compiles-"></a>
### Kotlin が `==` をどうコンパイルするか

| Kotlin | コンパイル結果 | lambdaQL のルール | HQL |
|---|---|---|---|
| 2 つの non-null のプリミティブ: `it.qty == 5` | 比較の分岐（Java と同じ） | R1 | `i.qty = 5` |
| nullable なプリミティブと定数: `it.status == 1`（`Int?`） | 隠れた null チェック + 比較 | R12（隠れたチェックは無視、D22） | `e0.status = 1` |
| `it.status != 1`（`Int?`） | 同じ | R12 | `e0.status <> 1`（NULL の行は返され **ません**） |
| 参照型、または両側が nullable: `it.description == text`、`it.status == q`（`q: Int?`） | `Intrinsics.areEqual(a, b)` の呼び出し | R11: 値の等価 | `e0.description = :p1`（キャプチャした `null`: `is null`） |
| `it.description != text` | `!Intrinsics.areEqual(a, b)` | R11、SQL の意味 | `e0.description <> :p1`（NULL の行は返され **ません**） |
| エンティティの値どうし: `it.description == it.custName` | `Intrinsics.areEqual(a, b)` | R11、SQL の意味 | `e0.description = e0.custName`（どちらかが NULL: 一致しない） |
| `it.id === q`、`!==` | `if_acmpeq` / `if_acmpne` | R17: 値の等価 | `==` と同じ |
| enum: `it.kind == OrderKind.GIFT` | `if_acmpne` | R2、R17 | `e0.kind = com.example.OrderKind.GIFT`（完全なクラス名を使ったリテラル） |

`Intrinsics.areEqual` は、`==` の裏にある Kotlin ランタイムの関数です。lambdaQL はこれを名前で見分け、**値の等価** として読みます。エンティティの値は SQL の意味に従います（決定 D48）。どちらかが NULL なら一致せず、NULL と NULL も一致しません。キャプチャした `null` は Kotlin に従い、`is null` になります。結果が Kotlin と異なりうるとき、lambdaQL は `LQL-5009` を `DEBUG` レベルでログに出します。

<a id="plain--and-"></a>
### 普通の `=` と `<>`

lambdaQL は常に普通の `=` と `<>` を書きます（決定 D48）。`is [not] distinct from` は書きません。これは Oracle では `decode(...)` になり、インデックスを使えないからです。

```kotlin
.where { it.description == text }    // text が "abc" -> e0.description = :p1
.where { it.description != "x" }     // e0.description <> 'x'   （description が NULL の行は一致しない）
.where { it.description == null || it.description != "x" }   // NULL の行も返る
```

> [!WARNING]
> **Kotlin の non-null 型は証明になりません。** `var code: String` にも、データベースから NULL が入ることがあります。
> Hibernate はチェックせずにカラムの値をフィールドに書き込むからです。
> lambdaQL は Kotlin の型ではなく、JPA のメタデータを見ます。

<a id="-and-"></a>
### `===` と `!==`

lambdaQL では、`===` は `==` と同じく **値の等価** を意味します（R17）。
クエリにはオブジェクトの同一性がありません。Kotlin 自体は、値が 1000 の 2 つの異なる `Long` のボックスに対して `false` を返しますが、lambdaQL は `true` を返します。

### `BigDecimal`

Kotlin では、`BigDecimal` の `==` は `equals` を呼び、スケールも比較します（`1.0 != 1.00`）。
SQL の `=` は値だけを比較します。**lambdaQL は値の等価を使います。**
`BigDecimal`、`String`、`LocalDate`、`BigInteger` の `<`、`>`、`<=`、`>=` はサポートしています（`compareTo` にコンパイルされます）。

<a id="booleans"></a>
### 真偽値

```kotlin
.where { it.active }                       // e0.active = true
.where { !it.active }                      // not (e0.active = true)
.where { it.flag == true }                 // e0.flag = true
.where { it.flag != true }                 // e0.flag <> true   （NULL の行は返されない: SQL の意味）
.where { it.active == (it.status!! > 1) }  // (e0.active = true and e0.status > 1) or (e0.active = false and e0.status <= 1)
```

2 つの真偽値の式の比較はサポートしています（D25）。lambdaQL はこれを `AND`/`OR` に展開する（`CASE` は使わない）ので、データベースはインデックスを使えます。
両側が同じ条件を共有している場合（たとえば `it.active == it.active`）は拒否されます。

<a id="java-entities-from-kotlin-platform-types"></a>
### Kotlin から使う Java のエンティティ（プラットフォーム型）

エンティティが Java のクラスの場合、Kotlin はその getter をプラットフォーム型（`Long!`）として見ます。
ルールは同じです: `j.id == 1L` は `e0.id = 1` で、`j.customer.name` は結合したカラムです（`where` の中ではデフォルトで内部結合）。

<a id="95-null-operators---and-"></a>
## 9.5 null 演算子: `?.`、`?:`、`!!`

<a id="summary"></a>
### まとめ

| Kotlin | lambdaQL での意味 | HQL の例 |
|---|---|---|
| `it.customer?.name` | カラム `customer.name`。`where` の中ではデフォルトで内部結合なので、customer のない注文は返されません | `j1.name` + `join e0.customer j1` |
| `it.customer!!.name` | `?.` と同じ。`!!` は無視されます（D23） | `j1.name` + `join e0.customer j1` |
| `it.customer?.id` | パスが対象の `@Id` で終わる: FK のカラムで、join なし | `e0.customer.id` |
| `it.customer == null` | customer のない注文だけ | `e0.customer is null`（FK のカラム、join なし） |
| `it.customer != null` | customer がある注文だけ | `e0.customer is not null`（FK のカラム、join なし） |
| `it.price!! > 10.0` | `!!` は無視されます。コンパイラーのためだけのものです | `e0.price > 10.0` |
| `it.description ?: ""`（値） | デフォルト値 | `coalesce(e0.description, '')` |
| `it.flag ?: false`（条件） | NULL は false として扱われます | `(e0.flag is not null and e0.flag = true)` |
| `lo!!`（キャプチャ値） | `!!` は無視されます。`lo` が null だと、クエリの実行時にエラーになります | `:p1` |

<a id="-and--on-association-paths"></a>
### 関連のパスでの `?.` と `!!`

```kotlin
.where { it.customer?.name == "Kim" }
// select e0 from Order e0 join e0.customer j1 where j1.name = 'Kim'

.where { it.customer!!.name == "Kim" }
// 同じ HQL

.where { it.customer?.id == 5L }
// select e0 from Order e0 where e0.customer.id = 5      （FK のカラム、join なし）
```

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `path-literal`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name='Kim'
```

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `fk-literal`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id=5
```

パスは呼び出しの連鎖ではなく **カラム** です（D20）。`where` の中では、lambdaQL はデフォルトで内部結合を使います（D36）。
そのため customer のない注文は返されず、パスの前に `it.customer != null &&` を書く必要はありません。

**`?.` を使っても customer のない注文は戻りません。** Kotlin では、customer がないとき `it.customer?.name == null` は `true` です。
lambdaQL では、customer に名前がない注文だけを返します。customer のない注文も含めるには、そう書いてください。

```kotlin
.where { it.customer == null }
// select e0 from Order e0 where e0.customer is null      （FK のカラム、join なし）

.where { it.customer == null || it.customer?.name == null }
// customer のない注文と、customer に名前がない注文
// select e0 from Order e0 join e0.customer j1 where e0.customer is null or j1.name is null
```

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `no-association`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id is null
```

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `null-guard-name-null`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.id is null or c1_0.name is null
```

`?.` は最後に関数があっても使えます: `it.description?.length` は `length(e0.description)` です。

<a id="-is-just-for-the-compiler"></a>
### `!!` はコンパイラーのためだけのもの

`price` が `Double?` の場合、Kotlin は `it.price > 10.0` をコンパイルしません。
`it.price!! > 10.0` と書いてください。lambdaQL は `!!` を無視します。ラムダは実行されないので、NPE は起きません。
`price` が NULL の行は返されません（SQL のルール）。

<a id="-elvis"></a>
### `?:`（エルビス）

値（select、DML の `set`、ソートキー）では、`?:` は `coalesce` になります（ルール R15）。

```kotlin
.select { it.description ?: "" }                 // coalesce(o.description, '')
.select { it.customer?.name ?: "none" }          // coalesce(c1.name, 'none') + left join
.select { (it.price ?: 0.0) * 2 }                // coalesce(o.price, 0.0) * 2
qb.update(Order::class.java).set { it.price = (it.price ?: 0.0) + 1 }.all().execute()
// set o.price = coalesce(o.price, 0.0) + 1.0
```

**文字列** の条件では、lambdaQL はカラムに関数をかけないようにします（SARGable のルール）。

```kotlin
.where { (it.description ?: "") == "x" }   // e0.description = 'x'
.where { (it.description ?: "") == "" }    // (e0.description = '' or e0.description is null)
```

**数値** の条件では、設計では `coalesce(column, default)` を書きます。

```kotlin
.where { (it.price ?: 0.0) > 3.0 }            // coalesce(e0.price, 0.0) > 3.0
.where { (it.description?.length ?: 0) > 3 }  // coalesce(length(e0.description), 0) > 3
```

カラムに関数がかかると、`price` の普通のインデックスは使われません。
ヒント: 最初の例では NULL は 0 になり、`0 > 3` は false です。そのため `it.price!! > 3.0` は同じ行を返し、
インデックスを使える `e0.price > 3.0` になります。

**真偽値のエルビス** は **条件の中で** サポートしています。

```kotlin
.where { it.flag ?: false }       // (e0.flag is not null and e0.flag = true)
.where { !(it.flag ?: false) }    // (e0.flag is null or not (e0.flag = true))
```

**値やソートキー** としての真偽値のエルビス（`select { it.flag ?: false }`）はサポートしていません（KC21）。
真偽値への `coalesce` は Oracle で失敗します（ORA-00932）。ソートには、キー `{ it.flag }` を使い、
`by.asc(key, nullsFirst)` か `by.desc(key, nullsFirst)` で NULL の位置を選んでください（[4. ソートとページング](04-order-paging.md#43-direction)）。

<a id="smart-casts-and-let"></a>
### スマートキャストと `?.let`

使う前の null チェックは普通の条件です。

```kotlin
.where { it.description != null && it.description!!.length > 3 }
// (e0.description is not null and length(e0.description) > 3)

.where { it.description?.let { d -> d.length > 3 } == true }
// (e0.description is not null and length(e0.description) > 3)
```

<a id="-in-an-assignment"></a>
### 代入の中の `?.`

`set { }` と `values { }` の中では、`it.customer?.id = cid` は **常に代入します**。`?.` は無視されます（D23）。

<a id="96-in-ranges-and-when"></a>
## 9.6 `in`、範囲、`when`

この節のすべての書き方は v0.1 でサポートしています（決定 D21 と D29）。詳細とその他の例: [3.9](03-where.md#39-in-with-a-captured-collection)、[3.14](03-where.md#314-ranges-kotlin-only)、[3.15](03-where.md#315-when-kotlin-only)。

| Kotlin | HQL | 注 |
|---|---|---|
| `it.id in ids`（キャプチャしたコレクション） | `e0.id in (:p1)` | 空: FALSE。`null` の要素: エラー |
| `it.id !in ids` | `e0.id not in (:p1)`（id は NULL にならない）。NULL になりうるカラム: `(x is null or x not in (:p1))` | 空: TRUE |
| `it.status in 1..5` | `e0.status between 1 and 5` | |
| `it.status !in 1..5` | `not (e0.status between 1 and 5)` | NULL の行は返されません |
| `it.status in 1 until 5`、`in 1..<5` | `e0.status >= 1 and e0.status < 5` | |
| `it.status in lo..hi` | `e0.status between :p1 and :p2` | |
| `it.status in 5 downTo 1` | サポート | `1..5` と同じ行 |
| `... step 2` | **拒否** | |
| `when (it.status) { 1, 2 -> true; else -> false }` | `e0.status in (1, 2)` | `Int`、`String`、enum の対象 |
| `when { a -> true; b -> true; else -> false }` | `a or b` | |
| `it.customer?.id in sub.from(...).select { ... }` | `o.customer.id in (select ...)` | サブクエリ。[5.4](05-subquery.md#54-in) を参照 |
| `x !in sub.from(...).select { ... }` | `not exists (...)` | [5.5](05-subquery.md#55-not-in-is-written-as-not-exists) を参照 |
| `it.status in listOf(1, 2)`（ラムダの中で作ったリスト） | **拒否**（KC8） | リストは外で作ってください |

**nullable な対象には null ガードが付きません**（D22）。Kotlin は `null in 1..5` を false、`null !in 1..5` を true と定義しています。
lambdaQL はガードを追加しません。NULL の対象は `in` でも `!in` でも返され **ません**（SQL のルール）。
NULL の行が欲しい場合は、`it.status == null || it.status !in 1..5` と書いてください。

唯一の例外は、キャプチャしたコレクションに対する `!in` です。この場合、lambdaQL は Kotlin の結果（`x is null or ...`）を保ちます。キャプチャしたコレクションに対する Java のルールと同じです。

<a id="97-strings"></a>
## 9.7 文字列

次の Kotlin の文字列の書き方には設計があります（ルール R21）。可能な場合は SARGable のルールに従います。

| Kotlin | HQL | 注 |
|---|---|---|
| `it.description!!.startsWith("A")` | `e0.description like :p1 escape '\'` | lambdaQL は値の中の `%`、`_`、`\` をエスケープします |
| `it.description!!.endsWith(s)` | `e0.description like :p1 escape '\'` | |
| `it.description!!.contains(s)` | `e0.description like :p1 escape '\'` | |
| `it.description?.startsWith("A") == true` | 同じ `like` | これもサポート |
| `startsWith(s, ignoreCase = true)`、`equals(s, ignoreCase = true)` | カラムによって変わります（下を参照） | D24 |
| `it.description.equals(text)` | `==` と同じ | |
| `it.description!!.lowercase() == "abc"` | `lower(e0.description) = 'abc'` | カラムに関数がかかる: 普通のインデックスは使われません |
| `it.description!!.length > 3` | `length(e0.description) > 3` | 長さの単位は DB によって違うことがあります |
| `it.description.isNullOrEmpty()` | `(e0.description is null or e0.description = :p1)` | Oracle では `''` は NULL です |
| `isBlank()`、`isNullOrBlank()` | **拒否**（KC9） | `isNullOrEmpty()` か直接の比較を使ってください |
| カラムに対する `trim()`、`coerceAtLeast()`、`toLocalDate()` などの stdlib の呼び出し | **拒否**（KC20） | 予定（関数のマッピング） |
| `"$a-$b" == it.description`（テンプレートの中はキャプチャ値だけ） | `:p1 = e0.description` | 設計済み: 文字列はアプリケーションで組み立てられます。現在の解析コアは文字列の連結をすべて拒否します。文字列はラムダの前に組み立ててください |
| `"${it.description}-x"`（テンプレートの中のエンティティの値） | **拒否**（KC17） | 予定 |

**`ignoreCase = true`（D24）。** lambdaQL は `lower(col) = lower(:p)` を書きません。インデックスを使えないからです。
起動時にそのカラムの DB メタデータを読み、DB が使える形を選びます。

| DB にあるもの | HQL |
|---|---|
| 大文字と小文字を区別しない照合順序（MariaDB の `_ci`） | `col = :p` / `col like :p` |
| `lower(col)` に対する Oracle の関数インデックス | `lower(col) = :p`（値はアプリケーションで小文字にします） |
| Oracle の言語インデックス | `col collate binary_ci = :p` |
| 何もない | 起動時に WARN（strict モード: エラー） |

Unicode の大文字・小文字のルール（たとえば `ſ`、`İ`）は、Kotlin ではなく DB の照合順序に従います。

<a id="98-captured-values"></a>
## 9.8 キャプチャ値

| キャプチャしたもの | lambdaQL での使い方 |
|---|---|
| `val`（`Long?`、`String`、... も） | バインドパラメーター |
| `var` | バインドパラメーター。値は **クエリを実行するたびに** 読まれます |
| 外側のクラスのプロパティ（`this.minStatus`、`private` も） | クエリを実行するたびに読まれます |
| `companion object` の `val`、トップレベルの `val`、`object` の `val` | バインドパラメーター |
| `const val` | リテラルとして HQL に書き込まれます（D37） |
| ラムダの中でキャプチャした変数に書き込む（`n++`） | **拒否**（KC12） |

```kotlin
class OrderRepo(private val qb: QueryBuilder) {
    private val minStatus = 2

    fun open(lo: Int?) = qb.from(Order::class.java)
        .where { it.status!! >= minStatus }
        .where { lo == null || it.status!! > lo }   // lo == null なら絞り込みなし、それ以外は o.status > :p2
        .toList()
}
```

キャプチャした `null` は、`==` と `!=`（`is null` / `is not null` になる）と、同じ値に対する null ガードの下（`lo == null || ...`）で使えます。ガードはクエリの実行時にアプリケーションの中で決まるので、`lo == null` ならこの `where` は絞り込みを追加しません。
それ以外の場所（`lo == null` のときの `it.status!! > lo!!`）では、lambdaQL は値をバインドするときにエラーを投げます。
キャプチャした `NaN` や `Infinity` は常にエラーです。

<a id="99-property-references-and-scope-functions"></a>
## 9.9 プロパティ参照とスコープ関数

<a id="property-references"></a>
### プロパティ参照

```kotlin
.orderBy(Order::price)       // orderBy { it.price } と同じ（まだ実装されていません）
.where(Order::active)        // where { it.active } と同じ
```

| 形 | 使えるか |
|---|---|
| lambdaQL の関数に渡した Kotlin の `Order::price` | はい（R23）。`orderBy` では**まだ実装されていません**。今は `orderBy { it.price }` を使ってください |
| Java の getter 参照 `JOrder::getPrice` | はい |
| Java の合成プロパティ `JOrder::price` | いいえ。Kotlin 自体がコンパイルしません（KC18）。`JOrder::getPrice` を使ってください |
| `Order::price.getter` などの `kotlin-reflect` の API | いいえ（KC19） |
| 自分で作った Kotlin の `fun interface` に渡した参照 | いいえ（KC3）。ラムダリテラルを渡してください |

<a id="scope-functions-and-inline-functions-in-conditions"></a>
### 条件の中のスコープ関数と inline 関数

これらは条件の中でサポートしています。コンパイラーが本体をインラインで展開するので、lambdaQL には普通の条件に見えます。

```kotlin
.where { it.description?.let { d -> d.length > 3 } == true }
.where { it.run { status == 1 && active } }
.where { with(it) { status == 1 } }
.where { it.isOpenInline() }   // 自分で作ったもの: inline fun Order.isOpenInline() = status == 1
```

| 形 | サポート |
|---|---|
| `?.let { }`、`run { }`、`with(it) { }`、`also { }` | はい、条件の中で |
| 自分で作った `inline fun` | はい |
| 自分で作った inline でない拡張関数や拡張プロパティ | いいえ（KC11）。`inline` にするか、条件を直接書いてください |
| `takeIf { }` | いいえ（KC6） |

更新と挿入のラムダでは、`apply`、`with`、`also` は v0.1 ではサポートしていません（[9.12](#912-update-insert-delete-property-assignment-and-byset) を参照）。

<a id="910-kotlin-operators"></a>
## 9.10 Kotlin の演算子

この表は、126 個の演算子の書き方をコンパイラーで確認した結果に基づいています（kotlinc 2.3.21、設計 KOTLIN.md の 9 節）。

| 演算子 | lambdaQL のラムダの中で |
|---|---|
| 数値の `< > <= >=`（nullable には `!!` を付けて） | サポート |
| `BigDecimal`、`String`、`LocalDate`、`BigInteger` の `< > <= >=` | サポート（`compareTo` にコンパイルされます） |
| `x.compareTo(y) > 0` | サポート。`x > y` として読みます |
| `==`、`!=` | サポート（SQL の意味による値の等価、[9.4](#94-equality----and-areequal)） |
| `===`、`!==` | サポート。値の等価 |
| enum の `==` | サポート |
| enum の `<`、`>` | サポート。lambdaQL は enum 定数の `in (...)` に展開するので、ORDINAL と STRING のどちらのマッピングでも動きます |
| エンティティ `==` エンティティ | **拒否**（KC13）。id を比較してください |
| `+ - *`、単項の `-` | サポート |
| `%` | サポート。`mod` として出力します |
| 整数の `/` | **注意してください。** データベースによって結果が違います（MariaDB の `5/2` は `2.5`、Kotlin は `2`）。最終的なルールは決まっていません。v0.1 では拒否されるかもしれません。ガードの下（`qty != 0 && total / qty > 2`）では常に拒否されます |
| `BigDecimal` / `BigInteger` の `+ - * %`、単項の `-` | サポート。SQL の演算子として出力します |
| `BigDecimal` の `/` | サポート（Kotlin は `divide(x, HALF_EVEN)` を使います）。結果のスケールは DB に従います。その他の `divide` の形は拒否されます |
| `in a..b`、`until`、`..<`、`downTo` | サポート（[9.6](#96-in-ranges-and-when)）。`step` はサポートしません |
| `x in list`、`x !in list` | サポート（[9.6](#96-in-ranges-and-when)） |
| 文字列の `+`、エンティティの値を含む文字列テンプレート | サポートしていません（予定）。注: NULL に対して、Kotlin は `"null"` を、SQL は NULL を返します |
| 真偽値の中置 `and`、`or`、`xor` | **拒否**。`&&` と `\|\|` を使ってください（これらは短絡評価します。`and`/`or` はしません） |
| キャプチャした配列に対する `arr[0]` | キャプチャ値として扱うか、分かりやすいエラーになります |
| エンティティのパスに対する `it.tags["k"]`、`it.lines[0]` | **拒否** |
| エンティティの `operator` 関数（`plus`、`compareTo`）、自分で作った inline でない `infix` | **拒否**（KC11） |
| `@JvmInline value class` のプロパティ | **拒否**（KC16） |

<a id="no-kotlin-only-operator-api"></a>
### Kotlin 専用の演算子 API はありません

lambdaQL には **Kotlin 専用の演算子 API はありません**（決定済み）。普通の Java のメソッド名が、そのまま Kotlin の演算子として使えます。

| Kotlin の書き方 | 使える理由 | 状況 |
|---|---|---|
| DML の `by[o.status] = st` | Kotlin は `a[i] = b` を `a.set(i, b)` に変え、代入ラムダは値を返しません | 採用（kotlinc 2.3.21 で確認） |
| `x in sub.from(...).select { ... }` | Kotlin は `x in v` を `v.contains(x)` に変えます | 採用 |
| `Agg.sum(x)!! + 1 > 3` | プレースホルダーの値に対する普通の算術 | 採用 |
| ソートの演算子（`by + key`、`-key`） | Java のユーザーにとって奇妙なメソッドが増え、Kotlin 専用の拡張が必要になります | 不採用 |
| DB 関数のための `invoke` 演算子 | D31（インターフェースの static 関数）と衝突します | 不採用 |
| `o["price"]` | 文字列のパスで、型安全性がありません | 不採用 |
| `infix` 関数 | Java のメソッドは `infix` にできません | 不可能 |
| `between` | `x in a..b` と書きます（nullable には `!!`、`LocalDate?` にも `!!` が必要） | API は不要 |

<a id="911-results-data-classes-pair-and-triple"></a>
## 9.11 結果: data class、`Pair`、`Triple`

```kotlin
data class OrderView(val id: Long?, val customerName: String?, val price: Double?)
data class Stat(val customerId: Long?, val cnt: Long, val total: Double?)

qb.from(Order::class.java).select { o -> OrderView(o.id, o.customer?.name, o.price) }   // data class
qb.from(Order::class.java).select { o -> o.customer?.name to o.price }                  // Pair

qb.from(Order::class.java)
    .groupBy { it.customer?.id }
    .having { Agg.sum(it.price)!! > 1000 }
    .select { Stat(it.customer?.id, Agg.count(), Agg.sum(it.price)) }

qb.from(Order::class.java).groupBy { listOf(it.customer?.id, it.status) }   // 複数のキー
qb.from(Order::class.java).groupBy { it.customer?.id to it.status }         // Pair でも構いません
```

| ルール | 注 |
|---|---|
| data class には nullable な型を使ってください | `sum`、`avg`、`min`、`max` は NULL になることがあります。non-null のパラメーターに NULL が来ると、オブジェクトを作るときに NPE が投げられます（J4） |
| `Pair`、`Triple` | 結果としても、グループのキーとしても使えます |
| `groupBy` の中の `listOf(...)` | 使えます（ラムダの中で作ったリストが使える唯一の場所です） |
| `groupBy` の中の `arrayOf(...)`、`setOf(...)` | 使えません |
| 派生テーブルや CTE の行の型 | Kotlin の data class（または Java の record）だけ。カラムは `componentN` の順に従います（J5） |
| `Agg` の関数 | `import org.lambdaql.Agg.sum` が使えます。コレクションに対する Kotlin の `sum()` / `count()` とは衝突しません |

詳細: [6. 選択、集計、グループ化](06-select-group.md)。

<a id="912-update-insert-delete-property-assignment-and-byset"></a>
## 9.12 更新、挿入、削除: プロパティへの代入と `by.set`

Kotlin のプロパティへの代入は Java と同じ setter の呼び出しにコンパイルされるので、ルールも同じです。

```kotlin
qb.update(Order::class.java).set { it.status = st; it.price = it.price!! * 1.1 }.where { it.status == 1 }.execute()
qb.update(Order::class.java).set { o, by -> by[o.status] = 1 }.where { it.id == id }.execute()   // val プロパティ用
qb.update(Order::class.java).set { it.customer?.id = cid }.where { it.id == id }.execute()       // ?. の代入 = 普通の代入
qb.update(Order::class.java).set { it.price = (it.price ?: 0.0) + 1 }.all().execute()            // ?: -> coalesce

qb.delete(Order::class.java).where { it.customer?.name == null }.execute()

qb.insert(Order::class.java).values { it.id = id; it.price = p; it.status = 1 }.execute()
qb.insert(Order::class.java).values(lines) { o, l -> o.id = l.id; o.price = l.price; o.status = 1 }.execute()
```

| Kotlin の書き方 | 意味 |
|---|---|
| `by[o.status] = 1` | `by.set(o.status, 1)` のための Kotlin のインデックス演算子。`val` プロパティ（setter なし）を持つエンティティに使います。kotlinc 2.3.21 で確認（ビルド内のテストはまだありません） |
| `by[o.cnt] += 1` | コンパイルできません（意図的です。`by` に `get` はありません）。`by[o.cnt] = o.cnt + 1` と書いてください |
| `it.cnt += 1` | サポート |
| `it.cnt++`、`--` | v0.1 ではサポートしません。`it.cnt += 1` と書いてください |
| `set` の中の `apply { }`、`with(it) { }`、`also { }` | v0.1 ではサポートしません。直接代入を書いてください |
| `set { }` と `set { o, sub -> }` | 別のオーバーロードです。Kotlin はパラメーターの数で選びます |

詳細: [7. 更新、挿入、削除](07-dml.md)。

<a id="913-procedures-and-db-functions"></a>
## 9.13 プロシージャと DB 関数

<a id="procedures"></a>
### プロシージャ

```kotlin
interface OrderProcs {
    @SqlProcedure("SQ_CALC") fun calc(id: Long, rate: Double): Double?
    @SqlProcedure("SP_RECENT") fun recent(custId: Long): List<OrderView>
    @SqlProcedure("SP_PURGE") fun purge(days: Int)
}

val d = qb.call(OrderProcs::class.java) { it.calc(id, rate) }.execute()
val d2 = qb.call(OrderProcs::class.java) { it.calc(rate = rate, id = id) }.execute()   // 名前付き引数
qb.exec(OrderProcs::class.java) { it.purge(30) }.execute()                            // 戻り値なし
```

| ルール | 理由 |
|---|---|
| nullable な戻り値の型（`Double?`）を使ってください | non-null の型に NULL の OUT 値が来ると失敗します。lambdaQL は名前付きのエラーを投げます |
| スタブ関数にデフォルト引数を付けないでください | Kotlin は呼び出しを `...$default` に変え、Kotlin のデフォルト値を埋めます。lambdaQL はエラーを報告します |
| 名前付き引数は単純な値（変数、定数）でだけ使ってください | 式を使うと Kotlin が隠れたローカル変数を作りますが、v0.1 の lambdaQL はそれを受け付けません |
| 戻り値のないプロシージャには（`call` ではなく）`exec` を使ってください | 両方に `call` を使うと Java で曖昧になり、`run` だと Kotlin の `run { }` と衝突します |
| `-java-parameters` でコンパイルしてください | すると lambdaQL は、同じ型の引数の取り違えをチェックできます（[8.4](08-procedures-functions.md#84-warning-argument-order)） |

<a id="db-functions-interface-companion-object--jvmstatic"></a>
### DB 関数: インターフェースの `companion object` + `@JvmStatic`

Kotlin のインターフェースには static 関数がありません。`companion object` と `@JvmStatic` を使ってください。

```kotlin
interface OrderFns {
    companion object {
        @JvmStatic @SqlFunction("FN_DISCOUNT")
        fun discount(price: Double?): Double? = throw DbOnlyException()
    }
}

qb.from(Order::class.java).where { OrderFns.discount(it.price)!! > 10 }
// where function('FN_DISCOUNT' as Double, o.price) > 10
val v = qb.call { OrderFns.discount(price) }.execute()     // 関数だけを呼び出す
```

本体が実行されることはありません。ラムダの外で誰かが呼び出したときに例外を投げるだけです。
Kotlin が companion の関数を呼ぶとき、バイトコードは最初に `Companion` オブジェクトを読み込みます。lambdaQL はその手順を飛ばし、関数のアノテーションを読みます。

| 宣言 | サポート |
|---|---|
| インターフェースの `companion object` + `@JvmStatic` | **はい（推奨）** |
| Kotlin の `object` + `@JvmStatic` | はい |
| トップレベル関数 | はい。ただし推奨しません: import を忘れて、名前が stdlib の名前（`maxOf`）と同じ場合、Kotlin は黙って stdlib の関数を呼びます |
| 拡張関数 | いいえ（KC11） |
| デフォルト引数のある `@SqlFunction` | いいえ |
| `inline` の `@SqlFunction` | いいえ（呼び出しが消えます） |
| `vararg` の `@SqlFunction` | v0.1 ではいいえ |
| 関数型のプロパティ / 関数型インターフェースの定数 | いいえ |

カラムにかけた関数（`OrderFns.discount(it.price)!! > 10`）は、普通のインデックスを使えません。
lambdaQL はこれに対して WARN をログに出します（[8.9](08-procedures-functions.md#89-warning-functions-on-columns-and-indexes-sargable) を参照）。

<a id="914-kotlin-limits-kc1-to-kc21"></a>
## 9.14 Kotlin の制限（KC1 から KC21）

lambdaQL は推測しません。ある書き方を解析できない場合は、コード
`[LQL-xxxx]`、ソースの行、ヒントを含む分かりやすいエラーを投げます（[11. エラーと言語](../11-errors-and-languages.md) を参照）。
KC1 から KC5 は、ラムダを読み込むときに見つかります。この場合、lambdaQL はすぐに止まります。

| # | 書いてはいけないもの | 理由 | 代わりに書くもの |
|---|---|---|---|
| KC1 | `-Xsam-conversions=class` でコンパイルする | `SerializedLambda` が作られず、ラムダがクラスになります | デフォルトの `-Xsam-conversions=indy` のままにする |
| KC2 | `inline fun` の本体の中で作ったクエリのラムダ（普通、`reified`、`crossinline`） | ラムダがラムダではなくクラスになります | ヘルパーを inline でなくするか、呼び出し側からラムダリテラルを渡す |
| KC3 | 自分で作った Kotlin の `fun interface` への呼び出し可能参照（`kkey(Order::price)`） | アダプタークラスになります | ラムダリテラル `{ it.price }`、または lambdaQL（Java）の関数に `Order::price` を渡す |
| KC4 | Kotlin の関数型の値: `val f: (Order) -> Boolean = {...}; where(f)` | lambdaQL は `Function1` の中を読めません | リテラルで `where { ... }` |
| KC5 | `object : ...`（オブジェクト式）、`Serializable` でない関数型インターフェース | シリアライズ可能なラムダではありません | ラムダリテラル |
| KC6 | 値を選ぶ `if`/`when`/`takeIf`、一般的な `if (a) x else y` | 値の分岐は v0.1 にはありません | `&&` / `\|\|`。値の `CASE WHEN` は予定 |
| KC7 | *（D25 で削除）* | 2 つの真偽値の式の比較は、いまはサポートしています | – |
| KC8 | ラムダの中の `in listOf(1, 2, 3)` や `setOf(...)` | ラムダの中で作ったコレクション | 外で `val ids = listOf(1, 2, 3)` とし、`it.status in ids` |
| KC9 | `isBlank()`、`isNullOrBlank()` | Unicode の空白には、移植性のある SQL の形がありません | `isNullOrEmpty()` か直接の比較 |
| KC10 | *（D21 で削除）* | 範囲と `when` は、いまはサポートしています。これらの書き方が遅れた場合に限り、しばらくの間 `==` / `\|\|` の連鎖で書くようエラーが案内します | – |
| KC11 | 自分で作った inline でない拡張関数や拡張プロパティ、エンティティの `operator` 関数 | lambdaQL は呼び出しの中を見られません | `inline fun` にするか、条件を直接書く |
| KC12 | キャプチャした変数に対する `n++` | ラムダが変数に書き込みます | 値はラムダの外で計算する |
| KC13 | エンティティ `==` エンティティ（`it.customer == c`） | v0.1 にはありません（予定: 識別子の等価） | `it.customer?.id == c.id` |
| KC14 | `isNaN()` | サポートしていません | NaN を保存しない。[10.5](10-rules-and-limits.md#105-kotlin-and-sql-differences) を参照 |
| KC15 | `@JvmField` のエンティティのプロパティ | getter ではなくフィールドアクセスになります | 普通の `var` / `val` |
| KC16 | マングルされた getter 名: `internal` プロパティ、`value class`、`@get:JvmName` | getter を JPA 属性にマッピングできません | public な普通のプロパティ |
| KC17 | 文字列テンプレートの中のエンティティの値 | 文字列の連結はまだサポートしていません | カラムを別々に比較する |
| KC18 | Java の合成プロパティの参照 `JOrder::price` | Kotlin のコンパイルエラー | `JOrder::getPrice` |
| KC19 | `Order::price.getter` などの `kotlin-reflect` の API | `kotlin-reflect` は使いません | `Order::price` |
| KC20 | カラムに対するその他の stdlib 関数（`coerceAtLeast`、`trim`、`toLocalDate`、...） | 許可リストにありません | 予定（関数のマッピング）、または `@SqlFunction` の DB 関数 |
| KC21 | 値やソートキーとしての真偽値のエルビス（`{ it.flag ?: false }`） | 真偽値への `coalesce` は Oracle で失敗します（ORA-00932） | `{ it.flag }` で NULL の位置を指定してソートする。条件の中の `it.flag ?: false` は問題ありません |

<a id="915-where-lambdaql-differs-from-running-kotlin-code"></a>
## 9.15 lambdaQL と Kotlin コードの実行との違い

これらの違いは設計の一部です。バグではありません。

| Kotlin のコード | lambdaQL（SQL） |
|---|---|
| `!!` は NPE を投げる | `!!` は無視されます。NULL は SQL のルールに従います |
| `status == null` のとき `it.status != 1` は `true` | 隠れた null チェックは無視されます: NULL に対して `e0.status <> 1` は UNKNOWN なので、その行は返されません。含めるには `it.status == null \|\| ...` と書いてください |
| customer がないとき `it.customer?.name == null` は `true` | `where` の中ではパスはデフォルトで内部結合なので、customer のない注文は返されません。含めるには `it.customer == null \|\| it.customer?.name == null` と書いてください（この null ガードでパスは `left join` になります） |
| 対象が NULL の `when` や範囲は `else` に進み、`!in` は `true` | その行は返されません。`NOT` の下でも同じです |
| `===` はオブジェクトの同一性 | 値の等価 |
| `BigDecimal` の `==` はスケールを比較する（`1.0 != 1.00`） | 値の等価 |
| `ignoreCase` は Kotlin の Unicode のルールを使う | DB の照合順序を使います（`ſ`、`İ` は違うことがあります） |
| `String.length` は UTF-16 の単位を数える | H2 は UTF-16 の単位を、MariaDB はコードポイントを数えます |
| `Double.NaN` の比較 | キャプチャした NaN はエラーです。保存された NaN は DB に従います |
| non-null 型の `String` は決して null にならない | カラムは NULL になることがあります。lambdaQL は JPA のメタデータを使います |
| 整数の `/` は小数部を切り捨てる | DB によって違うことがあります（MariaDB の `5/2 = 2.5`） |
| 文字列テンプレートは NULL を `"null"` にする | まだサポートしていません。SQL では NULL との連結は NULL になります |

その他のルール: [10. ルールと制限](10-rules-and-limits.md)。

---

[Kotlin トラック](README.md) | 前: [8. プロシージャと DB 関数](08-procedures-functions.md) | 次: [10. ルールと制限](10-rules-and-limits.md) | [このページの Java 版: 9. Java の注意点](../java/09-java-notes.md)
