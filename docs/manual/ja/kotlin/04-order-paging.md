<a id="4-sorting-and-paging-kotlin"></a>
# 4. ソートとページング（Kotlin）

> [!NOTE]
> **状況**
> - **中核部分は実装済み**、H2、MariaDB 10.11、Oracle 23.26（Hibernate 6.6.5）でテスト済み: `by.asc`、`by.desc`、`by.sort` を使う `orderBy { o, by -> ... }`、キーが 1 つの書き方 `orderBy { it.key }` と `orderBy({ it.key }, Direction)`、`Direction.parse` / `reversed`、`limit` / `offset`。テストは **Java** のラムダを使います。**Kotlin のラムダはまだテストされていません。**
> - **まだ実装されていないもの:** Kotlin のプロパティ参照（`orderBy(Order::price)`）、`SortSpec`、`SortWhitelist`、結合の書き方 `{ o, c, by -> }`、サブクエリの中のソート、Spring の `Sort` / `Pageable` モジュール。
> - Kotlin の構文はコンパイルできます（プロトタイプで確認、kotlinc 2.3.21）。nullable なキー、`?.` のキー、プロパティ参照はコンパイルできます。エンティティのキーはコンパイルできません。
> - この章の HQL はライブラリのルールに従いますが、Kotlin のラムダではテストされていません。

[Kotlin トラック](README.md) | [マニュアルのホーム](../README.md) | 前: [3. 条件](03-where.md) | 次: [5. サブクエリ](05-subquery.md) | [このページの Java 版](../java/04-order-paging.md)

共通ページ: [1. 概要](../01-overview.md) · [11. エラーと言語](../11-errors-and-languages.md)

---

<a id="41-one-lambda-for-the-whole-sort"></a>
## 4.1 ソート全体を 1 つのラムダで

ソートは **1 つのラムダ** で書きます。ラムダは、エンティティと、最後の引数としてソートビルダー `by` を受け取ります。
キーと方向は `by.asc(...)` と `by.desc(...)` で追加します。

```kotlin
qb.from(Order::class.java)
    .where { it.active }
    .orderBy { o, by -> by.desc(o.price).asc(o.customer?.name).asc(o.id) }
    .limit(20).offset(40)
    .toList()
// HQL: select e0 from Order e0 left join e0.customer j1
//      where e0.active = true
//      order by e0.price desc nulls last, j1.name asc nulls first, e0.id asc
```

**SQL ブロックの読み方。** `…` は省略した select リスト（エンティティのすべてのカラム）で、`?` はバインドパラメーターです。各行は [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java) から取っています。このテストは各クエリを Hibernate 6.6.5 で H2 2.3、MariaDB 10.11、Oracle 23.26 に対して実行し、Hibernate の SQL が変わると失敗します。 このテストは Java のラムダを使います。意味が同じ Kotlin のラムダは同じ HQL になるので、SQL も同じです。

**データベースごとの SQL（この HQL に対する Hibernate の SQL。Kotlin も同じ HQL になります）** — マニュアルの `Order` には NULL になりうる `price` がありますが、テストの `Order` にはないので、`SqlByDatabaseTest` はこの HQL を Hibernate に直接渡します。`expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `sort-overview`:

```sql
-- H2
-- (this HQL is given to Hibernate directly)
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where o1_0.active=true
order by o1_0.price desc,c1_0.name,o1_0.id
offset ? rows
fetch first ? rows only

-- MariaDB
-- (this HQL is given to Hibernate directly)
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where o1_0.active=1
order by o1_0.price desc,c1_0.name,o1_0.id
limit ?,?

-- Oracle
-- (this HQL is given to Hibernate directly)
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where o1_0.active=1
order by o1_0.price desc nulls last,c1_0.name asc nulls first,o1_0.id
offset ? rows
fetch first ? rows only
```

- `by` という名前は、このマニュアルでの習慣にすぎません。どんな名前でも使えます。
- `by` に import は必要ありません。ラムダの引数だからです。
- キーとその方向は一緒に書きます。順番がずれることはありません。
- lambdaQL はラムダを読み取ります。実行はしません。`by` のメソッドは順序を記述するだけです。

Kotlin のテストはまだありません。同じソートを Java で書いたものは [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java) でテストされています（H2、MariaDB、Oracle）。テスト済みの例は [このページの Java 版](../java/04-order-paging.md) を参照してください。

<a id="42-the-short-forms-one-key"></a>
## 4.2 短い書き方（キーが 1 つ）

キーが 1 つだけなら、`by` を省略できます。

| Kotlin | HQL |
|---|---|
| `orderBy { it.price }` | `order by e0.price asc nulls first` |
| `orderBy({ it.updateAt }, Direction.DESC)` | `order by e0.updateAt desc nulls last` |
| `orderBy(Order::price)` | `order by e0.price asc nulls first`（まだ実装されていない） |

`Direction` がない場合、短い書き方は昇順（`ASC`）でソートします。
lambdaQL は、ラムダの引数の数（1 つか 2 つか）で書き方を区別します。Kotlin はあいまいさなく正しいオーバーロードを選びます。

`Direction` がある場合、ラムダは最初の引数なので、かっこの中に書きます: `orderBy({ it.updateAt }, Direction.DESC)`。

Kotlin のプロパティ参照（`Order::price`）はまだ実装されていません。今はラムダ（`{ it.price }`）を使ってください。

## 4.3 `Direction`

`Direction` には 6 つの値があります。

| 値 | 意味 | NULL の位置 |
|---|---|---|
| `ASC` | 昇順 | NULL が **先頭**（デフォルトのポリシー） |
| `DESC` | 降順 | NULL が **末尾**（デフォルトのポリシー） |
| `ASC_NULLS_FIRST` | 昇順 | NULL が先頭 |
| `ASC_NULLS_LAST` | 昇順 | NULL が末尾 |
| `DESC_NULLS_FIRST` | 降順 | NULL が先頭 |
| `DESC_NULLS_LAST` | 降順 | NULL が末尾 |

`Direction` には補助メソッドが 2 つあります。

- `Direction.parse(text)` は、テキスト（たとえばリクエストパラメーター）から方向を読み取ります。大文字と小文字は区別せず、`-`、`_`、空白は同じものとして扱います: `Direction.parse("desc-nulls-last")` は `DESC_NULLS_LAST` です。知らないテキストは `IllegalArgumentException` を投げます。
- `reversed()` は逆の方向を返します。NULL の位置も反転するので、順序全体が逆になります: `Direction.ASC.reversed()` は `DESC`、`Direction.ASC_NULLS_LAST.reversed()` は `DESC_NULLS_FIRST` です。

**NULL の位置を選ぶ** には、2 番目の `Boolean` 引数を使います: `by.asc(key, nullsFirst)` と `by.desc(key, nullsFirst)`（決定 D35）。
`true` は「NULL が先頭」、`false` は「NULL が末尾」という意味です。
2 番目の引数がなければ、`asc(key)` と `desc(key)` はデフォルトの NULL のポリシーを使います（決定 D16、[4.4](#44-null-ordering-is-the-same-on-every-database) を参照）。

| 書いたもの | 同じ意味 |
|---|---|
| `by.asc(key)` | `ASC`（NULL が先頭、デフォルトのポリシー） |
| `by.desc(key)` | `DESC`（NULL が末尾、デフォルトのポリシー） |
| `by.asc(key, true)` | `ASC_NULLS_FIRST` |
| `by.asc(key, false)` | `ASC_NULLS_LAST` |
| `by.desc(key, true)` | `DESC_NULLS_FIRST` |
| `by.desc(key, false)` | `DESC_NULLS_LAST` |

**方向が変数から来るとき** は、キャプチャした `Direction` と一緒に `by.sort(key, direction)` を使います。

```kotlin
.orderBy { o, by -> by.desc(o.description, false).asc(o.id) }   // nulls last
// order by e0.description desc nulls last, e0.id asc

val dir = if (request.newestFirst) Direction.DESC else Direction.ASC
.orderBy { o, by -> by.sort(o.updateAt, dir).asc(o.id) }     // dir は呼び出しのたびに読まれる

.orderBy({ it.customer?.name }, Direction.DESC_NULLS_LAST)  // NULL の位置を指定した短い書き方
```

ラムダの解析は 1 回だけです。`dir` はクエリを実行するたびに読まれます（ルール S10）。方向が `null` なら、クエリの実行時に例外になります。

> [!TIP]
> `false` だけでは意味が伝わりにくいです。Kotlin は Java のメソッドに名前付き引数（`nullsFirst = false`）を使えず、`by` ビルダーは Java のクラスです。
> そのため Kotlin でも Java と同じく `by.desc(x, false)` と書きます。`// nulls last` のような短いコメントを付ける習慣がおすすめです。

`dir` はラムダの **外** で選んでください。ソートのラムダの中の `if` は拒否されます（[4.6](#46-rules-for-the-lambda-body) を参照）。

<a id="44-null-ordering-is-the-same-on-every-database"></a>
## 4.4 NULL の順序はどのデータベースでも同じ

NULL をどこに置くかは、データベースによって違います。

| | H2 | MariaDB | Oracle |
|---|---|---|---|
| `ASC` のデフォルト | NULL が先頭 | NULL が先頭 | NULL が **末尾** |
| `DESC` のデフォルト | NULL が末尾 | NULL が末尾 | NULL が **先頭** |
| SQL の `NULLS FIRST/LAST` | 使える | **使えない（構文エラー）** | 使える |

lambdaQL は 1 つのポリシーでこれを解決します: **NULL は最も小さい値です**（決定 D16）。

- `ASC` = `ASC_NULLS_FIRST`
- `DESC` = `DESC_NULLS_LAST`

これは Kotlin のコンパレーター `nullsFirst(naturalOrder())` と同じです。
このポリシーにより、3 つのデータベースすべてが同じ順序を返します（数値と日付の場合）。

**lambdaQL の書き方。** NULL になりうるキーにはすべて、lambdaQL が HQL に NULL の位置を書きます。たとえば `e0.description asc nulls first` です。
NULL になることがないキー（`active: Boolean` や `qty: Int` のような JVM のプリミティブ、または `@Id`）には `nulls` の部分を付けません。
lambdaQL は Kotlin の型ではなく JPA のメタデータを見ます。そのため `@Id` の付いた `id: Long?` には `nulls` の部分が付かず、`price: Double?` には付きます。
関連の先にあるカラムは常に NULL になりえます（顧客のない注文がありうるため）。なので常に付けます。

その後、Hibernate がデータベースごとに SQL を書きます。データベースのデフォルトと同じなら、その句を省きます。
MariaDB には `NULLS` 構文がないので、デフォルトと違う位置には Hibernate が `CASE` 式を書きます。

生成される SQL:

| 書いたもの | H2 | MariaDB | Oracle |
|---|---|---|---|
| `asc(key)`、`desc(key)`（`ASC`、`DESC`） | 追加の句なし（すでにデフォルト） | 追加の句なし | `nulls first` / `nulls last` を追加 |
| DB のデフォルトと同じ NULL の位置（`asc(key, b)`、`desc(key, b)`、`*_NULLS_*`） | 追加の句なし | 追加の句なし | 追加の句なし |
| DB のデフォルトと **違う** NULL の位置 | `nulls first/last` | Hibernate が `case when x is null ...` を書く | `nulls first/last` |
| NULL になることがないキー（方向は問わない） | 追加の句なし | 追加の句なし | 追加の句なし |

> [!TIP]
> MariaDB では、`by.asc(key, false)`（`ASC_NULLS_LAST`）と `by.desc(key, true)`（`DESC_NULLS_FIRST`）は `CASE` 式になります。データベースはこのソートにインデックスを使えません。できるだけ `asc(key)` / `desc(key)` を使ってください。

<a id="45-what-a-sort-key-can-be"></a>
## 4.5 ソートキーにできるもの

| キー | 使えるか | 補足 |
|---|---|---|
| カラム: `o.price` | 使える | nullable なキー（`Double?`、`String?`）も問題ありません |
| to-one のパス: `o.customer?.name`、`o.customer!!.name` | 使える | `left join`。下を参照 |
| 算術: `o.price!! * 2`、`+`、`-` | 使える | |
| 拡大変換: `o.status!!.toLong()` | 使える | |
| boolean 以外への elvis: `o.description ?: ""` | 使える | `coalesce(e0.description, '')`。NULL のポリシーは適用されません |
| エンティティ: `by.desc(o.customer)` | **コンパイルエラー** | エンティティは `Comparable` ではありません |
| エンティティを使わない値: `by.asc(1)`、キャプチャした値 | **拒否** | [4.6](#46-rules-for-the-lambda-body) を参照 |
| カラムへの関数: `lower(name)` | 予定 | |
| boolean の elvis: `{ it.flag ?: false }` | **拒否**（KC21） | `{ it.flag }` と NULL の位置を使ってください |
| 値の `if` / `when` | **拒否**（KC6） | |

キーは `Comparable` でなければなりません。Kotlin の nullable なキーは問題ありません。Kotlin はエンティティのキーをコンパイル時に拒否します。

**関連のパスは `left join` を使います。** HQL でただのパス `order by o.customer.name` を書くと、3 つのデータベースすべてで inner join になります。顧客のない注文が消えてしまいます。
lambdaQL は代わりに `left join` を追加します。顧客のない注文も結果に残ります。その `j1.name` は NULL なので、`asc` では先頭に来ます。

```kotlin
.orderBy { o, by -> by.asc(o.customer?.name).asc(o.id) }
// HQL: select e0 from Order e0 left join e0.customer j1 order by j1.name asc nulls first, e0.id asc
```

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `sort-path`:

```sql
-- H2, MariaDB
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
order by c1_0.name,o1_0.id

-- Oracle
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
order by c1_0.name asc nulls first,o1_0.id
```

SQL で NULL のポリシーを確認できます。H2 と MariaDB は、自分のデフォルトと同じなら句を省きます。Oracle は自分のデフォルトが逆なので、`asc nulls first` / `desc nulls last` を書きます。MariaDB には `NULLS` 構文がないので、デフォルトでない位置は先に `case when … is null then … end` で並べます。

エイリアスは `where` と共有されます。同じパスを `where` がすでに結合していれば、ソートはその結合を使います。
ほとんどの場合、それは内部結合です。`where` の条件が、顧客のない注文をすでに除いているからです（決定 D36、[3.10](03-where.md#310-association-paths) を参照）。

<a id="46-rules-for-the-lambda-body"></a>
## 4.6 ラムダ本体のルール

本体は **`by` の呼び出しをまっすぐつなげたもの** でなければなりません。

```kotlin
// OK
.orderBy { o, by -> by.desc(o.price).asc(o.id) }

// 拒否: 条件によって変わるキー
.orderBy { o, by -> if (flag) by.desc(o.price) else by.asc(o.id) }

// 拒否: エンティティを使わないキー
.orderBy { o, by -> by.asc(1) }

// 拒否: ループ、ローカルの val/var、2 つ以上の文
```

lambdaQL は `orderBy` を呼んだときにこれをチェックし、`LambdaAnalysisException` を投げます。
どのキーもエンティティを使わなければなりません。定数やキャプチャした値だけのキーは拒否されます。

実行時に方向を変える必要があるなら、変数として渡します: `by.sort(key, dir)`。キー自体を変える必要があるなら、普通の Kotlin のコードで 2 つの分岐に分けてクエリを組み立ててください。

<a id="47-order-of-calls"></a>
## 4.7 呼び出しの順序

ビルダーの型が順序を決めます。間違った順序はコンパイルできません。

| 呼び出し | 使えるか |
|---|---|
| `from(...).where { }.orderBy { }.limit(...).offset(...)` | 使える |
| `from(...).where { }.limit(...).offset(...)`（`orderBy` なし） | 使える。行は id でソートされる（[4.8](#48-paging) を参照） |
| `orderBy { }` を 2 回 | 使えない（コンパイルエラー） |
| `orderBy { }` の後の `where { }` | 使えない（コンパイルエラー） |
| `limit(...)` の後の `orderBy { }` | 使えない（コンパイルエラー） |

`orderBy`、`limit`、`offset` の後、クエリの型は `OrderedSelectQuery` になります。使えるのは `limit`、`offset`、`toHql()`、`toList()` だけです。

<a id="48-paging"></a>
## 4.8 ページング

```kotlin
qb.from(Order::class.java)
    .where { it.status == 1 }
    .orderBy { o, by -> by.desc(o.price) }
    .limit(20)      // ページのサイズ
    .offset(40)     // 40 行をスキップ（3 ページ目）
    .toList()
// HQL: select e0 from Order e0 where e0.status = 1 order by e0.price desc nulls last, e0.id asc
// limit と offset は Hibernate に渡される（setMaxResults / setFirstResult）
```

**データベースごとの SQL（この HQL に対する Hibernate の SQL。Kotlin も同じ HQL になります）** — マニュアルの `Order` には NULL になりうる `price` がありますが、テストの `Order` にはないので、`SqlByDatabaseTest` はこの HQL を Hibernate に直接渡します。`expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `page-design`:

```sql
-- H2
-- (this HQL is given to Hibernate directly)
select …
from orders o1_0
where o1_0.status=1
order by o1_0.price desc,o1_0.id
offset ? rows
fetch first ? rows only

-- MariaDB
-- (this HQL is given to Hibernate directly)
select …
from orders o1_0
where o1_0.status=1
order by o1_0.price desc,o1_0.id
limit ?,?

-- Oracle
-- (this HQL is given to Hibernate directly)
select …
from orders o1_0
where o1_0.status=1
order by o1_0.price desc nulls last,o1_0.id
offset ? rows
fetch first ? rows only
```

`limit` と `offset` には 0 以上の値を渡します。負の値は `IllegalArgumentException` を投げます。

lambdaQL は HQL に `LIMIT` を書きません。Hibernate がデータベースごとに正しい SQL を書きます。

| データベース | SQL |
|---|---|
| H2 | `offset ? rows fetch first ? rows only` |
| MariaDB | `limit ?, ?` |
| Oracle | `offset ? rows fetch first ? rows only` |

**`orderBy` なしのページング。** `orderBy` を使わずに `limit` と `offset` を呼ぶこともできます。そのときは lambdaQL が行を id でソートするので、ページは安定します。

```kotlin
qb.from(Order::class.java)
    .where { it.active }
    .limit(2)
    .toList()
// HQL: select e0 from Order e0 where e0.active = true order by e0.id asc
```

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `page-only`:

```sql
-- H2
select …
from orders o1_0
where o1_0.active=true
order by o1_0.id
fetch first ? rows only

-- MariaDB
select …
from orders o1_0
where o1_0.active=1
order by o1_0.id
limit ?

-- Oracle
select …
from orders o1_0
where o1_0.active=1
order by o1_0.id
fetch first ? rows only
```

ページングの SQL はデータベースごとに違います。H2 と Oracle は `fetch first ? rows only`、MariaDB は `limit ?` になります。offset があると、H2 と Oracle は `offset ? rows` を加え、MariaDB は `limit ?,?` を書きます。

<a id="tie-breaker-automatic-id"></a>
### タイブレーカー（自動の `id`）

上の HQL を見てください。lambdaQL は最後に `e0.id asc` を追加しました。なぜでしょうか。

- 多くの行が同じ `price` を持つことがあります。データベースはそれらの行を **どんな順序でも** 返す可能性があります。Oracle は実際にそうします（測定済み）。
- すると、2 ページ目と 3 ページ目で同じ行が 2 回表示されたり、行が抜けたりします。

ルール: **ページングを使い、ソートキーに id が含まれていない場合、lambdaQL はルートの `@Id asc` を最後のキーとして追加します。**

- 追加されるのは 1 回だけです。キーにすでに id が含まれていれば（たとえば `by.desc(o.id)`）、追加されません。
- `limit` と `offset` がなければ、何も追加されません。
- **まだ実装されていません**（join がまだ実装されていないため）: to-many の join がある場合は、join したエンティティの id も追加されます。コレクションの fetch join と `limit`/`offset` を一緒に使うと拒否されます。

<a id="49-strings-and-special-values"></a>
## 4.9 文字列と特殊な値

- 文字列の順序はデータベースの照合順序に従います。MariaDB の `_ci` は大文字と小文字を区別しません。Oracle は `NLS_SORT` を使い、これは JVM のロケールに従います（たとえば `de_DE` ではドイツ語の順序になります）。Oracle では `NLS_SORT=BINARY` をおすすめします。
- Oracle は `''` を NULL として保存するので、空文字列は NULL としてソートされます。
- `NaN` の順序はデータベースごとに違います。

<a id="410-reusable-and-external-sorts"></a>
## 4.10 再利用できるソートと外部からのソート

**まだ実装されていません。** 設計には次のものもあります。

- 再利用できるソートのための `SortSpec`: `SortSpec.of(Order::class.java) { o, by -> by.desc(o.updateAt).asc(o.id) }`。`then()` と `reversed()` があり、`orderBy(spec)` に渡します。
- 外部の文字列（`"price,desc_nulls_last"` のようなリクエストパラメーター）をソートに変える `SortWhitelist<T>`。外部の文字列はホワイトリストを通してのみ受け付けます。
- **予定:** Spring の `Sort` / `Pageable` モジュール（Spring の各 `Sort.Order` は 1 つの `Direction` の値に対応）、`{ o, c, by -> ... }` による join、キーセットページング。

それまでは、`Direction.parse` と `by.sort(key, dir)` で実行時に方向を決められます（[4.3](#43-direction) を参照）。キーは普通の Kotlin のコードで選んでください。

<a id="411-sorting-inside-subqueries"></a>
## 4.11 サブクエリの中のソート

**まだ実装されていません**（サブクエリがまだ実装されていないため）。

サブクエリの中のソートは、`limit`（top-1 または top-N）と一緒に使うときだけ残ります。
`limit` がなければ結果は変わらないので、lambdaQL はソートを取り除き、警告をログに出します。
[5. サブクエリ](05-subquery.md#59-sorting-inside-a-subquery) を参照してください。

<a id="412-rules-summary-s1-to-s14"></a>
## 4.12 ルールのまとめ（S1 から S14）

| # | ルール | 状況 |
|---|---|---|
| S1 | ソートは 1 つのラムダ: `{ o, by -> by... }`。キーが 1 つなら `{ it.key }` と、省略可能な `Direction`。join では `{ o, c, by -> by... }`（`by` は常に最後） | 実装済み（結合の書き方を除く） |
| S2 | キーは `Comparable`（Kotlin の nullable なキーは使える）。エンティティはキーにできない（コンパイルエラー）。キーはエンティティを使わなければならない | 実装済み |
| S3 | 方向は `asc` / `desc` または `sort(key, Direction)` で決まる。`asc(key)`/`desc(key)`（`ASC`/`DESC`）は NULL のポリシーを使い、`asc(key, nullsFirst)`、`desc(key, nullsFirst)`、`*_NULLS_*` は書いたとおりに使われる | 実装済み |
| S4 | 本体は `by` の呼び出しをまっすぐつなげたものだけ。`if`、`when`、ループは使えない | 実装済み |
| S5 | `orderBy` はクエリごとに 1 回。`orderBy` の後に `where` は使えない。`limit` の後に `orderBy` は使えない（コンパイルエラー） | 実装済み |
| S6 | 再利用には `SortSpec`。外部の文字列は `SortWhitelist` を通してのみ | まだ実装されていない |
| S7 | キー: カラム、to-one のパス、`+ - *`、拡大変換。値の分岐は使えない。Kotlin の `?.` はパスとして読まれる | 実装済み（Kotlin のラムダではテストされていない） |
| S8 | `orderBy` だけで使う関連のパスは `left join` を使うので、関連のない行も残る。同じパスを `where` が使うと、ソートは `where` の結合を使う | 実装済み |
| S9 | ページングでは、キーに id が含まれていなければルートの `@Id asc` が追加される。コレクションの fetch join + ページングは拒否される | ルートの id については実装済み。結合の部分はまだ実装されていない |
| S10 | 解析結果はラムダごとにキャッシュされる。キャプチャした `Direction` は呼び出しのたびに読まれる | 実装済み |
| S11 | Spring の `Sort.Order` は `Direction` に 1:1 で対応（予定のモジュール）。`SortWhitelist.parse("price,desc_nulls_last")` | まだ実装されていない |
| S12 | join のクエリも同じ形: `{ o, c, by -> }`、`{ o, c, i, by -> }` | まだ実装されていない |
| S13 | プロパティ参照はキーが 1 つの短い書き方（`orderBy(Order::price)`）でのみ。コンパイルできても意味が違う参照は拒否される | まだ実装されていない（Kotlin のプロパティ参照） |
| S14 | サブクエリのソート: `limit` があれば残り、`limit` がなければ警告付きで取り除かれる。データベースの違いは自動で処理される（5 章を参照） | まだ実装されていない |

---

[Kotlin トラック](README.md) | 前: [3. 条件](03-where.md) | 次: [5. サブクエリ](05-subquery.md) | [このページの Java 版](../java/04-order-paging.md)
