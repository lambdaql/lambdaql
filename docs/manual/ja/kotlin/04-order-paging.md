<a id="4-sorting-and-paging-kotlin"></a>
# 4. ソートとページング（Kotlin）

> [!NOTE]
> **状況**
> - **中核部分は実装済み**、H2、MariaDB 10.11、Oracle 23.26（Hibernate 6.6.5）でテスト済み: `by.asc`、`by.desc`、`by.sort` を使う `orderBy { o, by -> ... }`、キーが 1 つの書き方 `orderBy { it.key }` と `orderBy({ it.key }, Direction)`、`Direction.parse` / `reversed`、`limit` / `offset`、`orderBy` の後の `first()`、再利用できるソート `SortSpec`（`of`、`ofPath`、`then`、`reversed`、`orderBy(spec)`）。Spring の `Sort` と `Pageable` は Spring Data JPA モジュールで使えます（[12.5](12-spring-data-jpa.md#125-spring-sort-and-pageable)）。ほとんどのテストは **Java** のラムダを使います。`limit` を使う Kotlin のソートもテストしています（[`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)、**(テスト済み)** と表示）。
> - **こちらも実装済み（Java のラムダでテスト済み）:** `join`、`leftJoin`、`crossJoin` の後の結合の書き方 `{ o, c, by -> ... }`（[6.8 明示的な結合](06-select-group.md#68-explicit-joins) を参照）と、外部のソートのためのソートポリシー（`SortRules`、`SortPolicy`、`SortMode`。[4.10](#sort-policy) を参照）。
> - **まだ実装されていないもの:** Kotlin のプロパティ参照（`orderBy(Order::price)`）、`SortSpec` の結合の書き方。
> - Kotlin の構文はコンパイルできます（プロトタイプで確認、kotlinc 2.3.21）。nullable なキー、`?.` のキー、プロパティ参照はコンパイルできます。エンティティのキーはコンパイルできません。
> - この章のそのほかの HQL はライブラリのルールに従いますが、Kotlin のラムダではテストされていません。

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

**(テスト済み)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)、リージョン `sort`:

```kotlin
val q = qb.from(Order::class.java)
    .where { it.isActive }
    .orderBy { o, by -> by.desc(o.price).asc(o.customer?.name) }
    .limit(3)
assertEquals("select e0 from Order e0 left join e0.customer j1 where e0.active = true"
    + " order by e0.price desc, j1.name asc nulls first, e0.id asc", q.toHql().hql())
```

このテストは Java のテスト用エンティティを使います: `price` はプリミティブの `double`（そのため `nulls` の部分がありません）、`active` の getter は `isActive()`（Kotlin では `it.isActive`）です。テストはクエリを H2、MariaDB、Oracle で実行し、行を Kotlin での同じソートと比べます。
Java で書いたほかのソートは [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java) でテストされています。それらの例は [このページの Java 版](../java/04-order-paging.md) を参照してください。

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

lambdaQL は `orderBy` を呼んだときにこれをチェックし、`LambdaQLException` を投げます（たとえば条件なら `LQL-2023`、エンティティを使わないキーなら `LQL-3031`）。
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

`orderBy`、`limit`、`offset` の後、クエリの型は `OrderedSelectQuery` になります。使えるのは `limit`、`offset`、`toHql()`、`toList()`、`toSet()`、`first()` だけです。 `one()`、`count()`、`exists()` は `orderBy`、`limit`、`offset` の前だけで使えます（[結果を受け取る](02-getting-started.md#getting-the-result) を参照）。

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

**最初の行と合計の件数。**

- `orderBy { ... }.first()` は、その順序で最初の行を `Optional` として返します。読むのは 1 行だけです。`offset` はそのまま使われます。`orderBy { ... }.offset(1).first()` は 2 番目の行を返します。
- `count()` は where 段階（`orderBy`、`limit`、`offset` の前）にしかないので、いつも条件に合うすべての行を数えます。ページ付きの画面では、where 段階のクエリを値に入れておきます。合計を得るにはそれに `count()` を呼び、ページを得るにはそれに `orderBy { ... }.limit(...).toList()` を呼びます。

[結果を受け取る](02-getting-started.md#getting-the-result) を参照してください。

<a id="tie-breaker-automatic-id"></a>
### タイブレーカー（自動の `id`）

上の HQL を見てください。lambdaQL は最後に `e0.id asc` を追加しました。なぜでしょうか。

- 多くの行が同じ `price` を持つことがあります。データベースはそれらの行を **どんな順序でも** 返す可能性があります。Oracle は実際にそうします（測定済み）。
- すると、2 ページ目と 3 ページ目で同じ行が 2 回表示されたり、行が抜けたりします。

ルール: **ページングを使い、ソートキーに id が含まれていない場合、lambdaQL はルートの `@Id asc` を最後のキーとして追加します。**

- 追加されるのは 1 回だけです。キーにすでに id が含まれていれば（たとえば `by.desc(o.id)`）、追加されません。
- `limit` と `offset` がなければ、何も追加されません。
- 明示的な結合（`join`、`leftJoin`、`crossJoin`）では、結合したすべてのエンティティの id も結合の順に追加されます（Java のラムダでテスト済み、[6.8](06-select-group.md#68-explicit-joins) を参照）。
- まだ実装されていないもの: コレクションの fetch join と `limit`/`offset` を一緒に使うと拒否されます。

<a id="49-strings-and-special-values"></a>
## 4.9 文字列と特殊な値

- 文字列の順序はデータベースの照合順序に従います。MariaDB の `_ci` は大文字と小文字を区別しません。Oracle は `NLS_SORT` を使い、これは JVM のロケールに従います（たとえば `de_DE` ではドイツ語の順序になります）。Oracle では `NLS_SORT=BINARY` をおすすめします。
- Oracle は `''` を NULL として保存するので、空文字列は NULL としてソートされます。
- `NaN` の順序はデータベースごとに違います。

<a id="410-reusable-and-external-sorts"></a>
## 4.10 再利用できるソートと外部からのソート

**`SortSpec`: 取っておけるソート。** `SortSpec` は、値に入れておき、多くのクエリで使えるソートです。
`SortSpec.of(Order::class.java) { o, by -> ... }` で作ります。ラムダは `orderBy { o, by -> ... }` と同じなので、同じルールが当てはまります（[4.6](#46-rules-for-the-lambda-body) を参照）。
それを `orderBy(spec)` に渡します。

```kotlin
// for example at the top level or in a companion object
val NEWEST: SortSpec<Order> = SortSpec.of(Order::class.java) { o, by -> by.desc(o.updateAt).asc(o.id) }

qb.from(Order::class.java).where { it.active }.orderBy(NEWEST).limit(20).toList()
// HQL: select e0 from Order e0 where e0.active = true order by e0.updateAt desc nulls last, e0.id asc
```

- `SortSpec` は変更できません。`then()` と `reversed()` は新しい spec を返します。そのため、1 つの spec を多くのクエリやスレッドで安全に共有できます（たとえばトップレベルの `val` として）。
- lambdaQL は、spec が `orderBy` で使われたときにラムダをチェックします。各ラムダの解析は 1 回だけです。
- キャプチャした値は、ラムダが作られた時点の値です。トップレベルの `val` の spec は、その値をずっと持ち続けます。実行時に方向を変える必要があるなら、`orderBy { o, by -> by.sort(key, dir) }` を使ってください（[4.3](#43-direction) を参照）。
- `orderBy(spec)` の後は、いつもどおりページングできます: `limit`、`offset`、`toList()`、`first()`。

`SortSpec` についての Kotlin のテストはまだありません。同じ spec を Java で書いたものは [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java) でテストされています（H2、MariaDB、Oracle）。テスト済みの例は [このページの Java 版](../java/04-order-paging.md#410-reusable-and-external-sorts) を参照してください。

```kotlin
val byDescription = SortSpec.of(Order::class.java) { o, by -> by.desc(o.description).asc(o.id) }
qb.from(Order::class.java).where { it.active }.orderBy(byDescription).toList()
// HQL: select e0 from Order e0 where e0.active = true order by e0.description desc nulls last, e0.id asc
```

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `sort-spec`:

```sql
-- H2
select …
from orders o1_0
where o1_0.active=true
order by o1_0.description desc,o1_0.id

-- MariaDB
select …
from orders o1_0
where o1_0.active=1
order by o1_0.description desc,o1_0.id

-- Oracle
select …
from orders o1_0
where o1_0.active=1
order by o1_0.description desc nulls last,o1_0.id
```

SQL は、同じソートを `orderBy { o, by -> ... }` で書いた場合と同じです。Oracle は `desc` の既定が逆なので、`nulls last` を加えます（[4.4](#44-null-ordering-is-the-same-on-every-database) を参照）。

**`then()`: 今あるキーの後にキーを足す。** `a.then(b)` は、まず `a` のキーでソートします。等しい行は、次に `b` のキーでソートします。
`then()` には別の `SortSpec` か、ソートのラムダを渡せます。

```kotlin
val byStatus = SortSpec.of(Order::class.java) { o, by -> by.asc(o.status) }
val byPrice = SortSpec.of(Order::class.java) { o, by -> by.desc(o.price) }
qb.from(Order::class.java).orderBy(byStatus.then(byPrice)).toList()
// HQL: select e0 from Order e0 order by e0.status asc nulls first, e0.price desc nulls last
qb.from(Order::class.java).orderBy(byStatus.then { o, by -> by.desc(o.id) }).toList()
// HQL: select e0 from Order e0 order by e0.status asc nulls first, e0.id desc
```

ここでは `status` と `price` は NULL になりえる（`Int?`、`Double?`）ので、HQL に `nulls` の部分があります。Java のテスト用エンティティではこれらは NULL にならないので、テスト済みの HQL と SQL（例 `sort-spec-then`）には `nulls` の部分がありません。[このページの Java 版](../java/04-order-paging.md#410-reusable-and-external-sorts) を参照してください。

**`reversed()`: すべてのキーを逆向きに。** `spec.reversed()` はすべてのキーを逆にします。NULL の位置も逆になります。`asc`（NULL が先頭）は `desc`（NULL が末尾）になります。だから行の順序全体がちょうど逆になります。
これは `Direction.reversed()` と同じです（[4.3](#43-direction) を参照）。`spec.reversed().reversed()` は元の順序に戻ります。

```kotlin
val spec = SortSpec.of(Order::class.java) { o, by -> by.asc(o.description).asc(o.id) }
qb.from(Order::class.java).orderBy(spec.reversed()).toList()
// HQL: select e0 from Order e0 order by e0.description desc nulls last, e0.id desc
```

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `sort-spec-reversed`:

```sql
-- H2, MariaDB
select …
from orders o1_0
order by o1_0.description desc,o1_0.id desc

-- Oracle
select …
from orders o1_0
order by o1_0.description desc nulls last,o1_0.id desc
```

H2 と MariaDB は `desc` で NULL をもともと末尾に置くので、余分な句を書きません。Oracle は `nulls last` を加えます。

**`SortSpec.ofPath`: プロパティ名からのソート。** `SortSpec.ofPath(Order::class.java, "customer.name", Direction.DESC)` は、プロパティのパスから spec を作ります。ソートのキーがコードの外からテキストとして来る場合に使います（Spring モジュールは Spring の `Sort` のためにこれを使います。[12.5](12-spring-data-jpa.md#125-spring-sort-and-pageable) を参照）。
パスのどの部分も永続プロパティで、最後の部分は基本型の値でなければなりません。それ以外はエラーです。
どの名前を許可するかはソートポリシーが決めます（下の [ソートポリシー](#sort-policy) を参照）。ポリシーも `@Sortable` フィールドもなければ永続プロパティならどれでも受け付け、lambdaQL は警告 `LQL-5003` をログに出します。
ここにはラムダがないので、Kotlin でも Java と同じように動きます。Java のテストは [このページの Java 版](../java/04-order-paging.md#410-reusable-and-external-sorts) にあります（リージョン `sort-spec-path`）。

```kotlin
val byName = SortSpec.ofPath(Order::class.java, "customer.name", Direction.DESC)
    .then(SortSpec.ofPath(Order::class.java, "id", Direction.ASC))
qb.from(Order::class.java).orderBy(byName).toList()
// HQL: select e0 from Order e0 left join e0.customer j1 order by j1.name desc nulls last, e0.id asc
```

<a id="sort-policy"></a>
### ソートポリシー: 許可する外部の名前

**実装済み**（決定 D43）。H2、MariaDB 10.11、Oracle 23.26 で Java のラムダでテスト済み。

コードの外から来たソートの名前（リクエストパラメーター、Spring の `Sort` や `Pageable`）は、どの永続プロパティでも指せます。
どの名前を許可するかは **ソートポリシー** が決めます。

- `SortRules<T>` は、1 つのエンティティで許可する名前のリストです。各カラムをラムダで書くので、コンパイラーがチェックします。
- `SortPolicy` は、エンティティごとのルールとモード（`SortMode`）を持ちます。ポリシーは `EntityManagerFactory`、つまり永続化ユニットごとに 1 つ登録します。そのファクトリーを使うすべての `QueryBuilder` とリポジトリーがこのポリシーを使います。
- エンティティのフィールドに `@Sortable` を付けると、コードなしでルールを書けます（下の [`@Sortable` によるルール](#rules-from-sortable) を参照）。

エンティティのルールは、次の順で **1 か所** からだけ取られます: ポリシーの `SortRules`（コードか Spring の `SortPolicy` Bean）、Spring Boot のプロパティ `lambdaql.sort.*`（[12.5](12-spring-data-jpa.md#sort-policy-with-spring) を参照）、エンティティの `@Sortable` フィールド。2 か所のルールは合わせられません。

**チェックの対象。** 文字列から作ったソートだけです: `SortSpec.ofPath`、そして Spring の `Sort` や `Pageable`（Spring モジュールは `ofPath` を使います。[12.5](12-spring-data-jpa.md#sort-policy-with-spring) を参照）。
ソートのラムダ（`orderBy { o, by -> ... }`、`SortSpec.of`）は自分のコードなので、チェックされません。

| ルール | 許可する外部の名前 |
|---|---|
| `allow { it.price }` | `price`（カラムのパス） |
| `allow { it.customer?.name }` | `customer.name`。関連を通るパスは、このように書いたときだけ許可されます |
| `alias("newest") { it.updateAt }` | `newest`。`updateAt` でソートします。このときパス `updateAt` そのものは外部の名前では **ありません**。そのため、クライアントにプロパティ名が見えません |

ルールはエンティティのカラムでなければなりません。
それ以外のキーは、ルールを最初に使うときに `LQL-4041` で拒否されます（まだテストされていません）。

| `SortMode` | ルールが許可しない名前の扱い |
|---|---|
| `OFF` | ソートを実行します。チェックしません |
| `WARN` | ソートを実行し、エンティティと名前ごとに 1 回、警告 `LQL-5003` をログに出します。**これがデフォルトです:** 登録されたポリシーがなければ `WARN` を使い、ルールは `@Sortable` からだけ取ります |
| `STRICT` | `SortNotAllowedException`（`LQL-4040`）で拒否します。ルールも `@Sortable` フィールドもないエンティティは、すべての名前を拒否します |

そのため、ポリシーを登録するまでは、`@Sortable` が許可しない外部のソートの名前ごとに、エンティティごとに 1 回 `LQL-5003` が出ます。
知らない名前を拒否するには `STRICT` で、チェックを止めるには `OFF` でポリシーを登録してください。

| メソッド | 働き |
|---|---|
| `SortPolicy.of(mode, rules...)` | モードとエンティティごとのルールからポリシーを作る |
| `SortPolicy.register(emf, policy)` | 永続化ユニットのポリシーを登録する。前のポリシーは置き換えられる |
| `SortPolicy.unregister(emf)` | ポリシーを取り除く。デフォルト（`WARN`、ルールは `@Sortable` だけ）に戻る |
| `SortPolicy.find(emf)` | 登録されたポリシーを返す（`Optional`） |
| `SortPolicy.effective(emf)` | 有効なポリシーを返す。登録されたものがなければデフォルト |

Spring Boot では、`register` を呼ぶ代わりに `SortPolicy` の Bean を定義してください（[12.5](12-spring-data-jpa.md#sort-policy-with-spring) を参照）。

ソートポリシーについての Kotlin のテストはまだありません。同じルールを Java で書いたものは [`SortPolicyTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortPolicyTest.java) でテストされています（H2、MariaDB、Oracle）。テスト済みの例は [このページの Java 版](../java/04-order-paging.md#sort-policy) を参照してください。
次は設計上の例です。

```kotlin
val orderRules = SortRules.of(Order::class.java)
    .allow { it.price }                      // the name "price"
    .allow { it.customer?.name }             // an association path: allowed only when listed
    .alias("newest") { it.updateAt }         // the outside name "newest" sorts by updateAt
SortPolicy.register(emf, SortPolicy.of(SortMode.STRICT, orderRules))

qb.from(Order::class.java).orderBy(SortSpec.ofPath(Order::class.java, "newest", Direction.DESC)).toList()
// HQL: select e0 from Order e0 order by e0.updateAt desc nulls last
qb.from(Order::class.java).orderBy(SortSpec.ofPath(Order::class.java, "updateAt", Direction.ASC)).toList()
// SortNotAllowedException (LQL-4040): the path behind an alias is not an outside name
qb.from(Order::class.java).orderBy { o, by -> by.asc(o.description) }.toList()
// a sort lambda is your code: never checked
```

<a id="rules-from-sortable"></a>
**`@Sortable` によるルール。** エンティティのフィールドや getter に `@org.lambdaql.Sortable` を付けても、ルールになります。

| アノテーション | 許可する外部の名前 |
|---|---|
| プロパティ `name` の `@field:Sortable` | `name` |
| プロパティ `grade` の `@field:Sortable("level")` | `level`。`grade` でソートします（エイリアス） |
| 関連プロパティ `customer` の `@field:Sortable(path = "name")` | `customer.name` |

- lambdaQL が読むのは、`from(...)` に書いたエンティティのアノテーションだけです。別のエンティティのアノテーションでは関連のパスは許可されないので、関連フィールドに `path` を書いてください。
- アノテーションは、ポリシーにそのエンティティのルール（コード、Spring の Bean、Spring Boot のプロパティ）がないときだけ使われます。
- ポリシーを登録していないデフォルトの `WARN` モードでも働きます。

Kotlin では、アノテーションが JVM のフィールドに付くように `@field:Sortable` と書きます。同じルールは Java でテストされています（[`SortPolicyTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortPolicyTest.java)、リージョン `annotation`。[このページの Java 版](../java/04-order-paging.md#rules-from-sortable) を参照）。次は設計上の例です。

```kotlin
@Entity
class Customer(
    @Id @GeneratedValue
    var id: Long? = null,
    @field:Sortable                     // the outside name "name"
    var name: String? = null,
    @field:Sortable("level")            // the outside name "level" sorts by grade
    var grade: Int? = null,
)

// Customer has no SortRules in the policy, so its @Sortable fields are the rules
qb.from(Customer::class.java).orderBy(SortSpec.ofPath(Customer::class.java, "level", Direction.DESC)).toList()
// HQL: select e0 from Customer e0 order by e0.grade desc nulls last
```

<a id="rules-as-strings"></a>
**文字列によるルール。** `allowPath(String...)` と `aliasPath(name, path)` は、プロパティのパスを文字列で受け取ります。設定ファイル向けで、[12.5](12-spring-data-jpa.md#sort-policy-with-spring) の Spring Boot のプロパティがこれを使います。
パスは、ルールを最初に使うときにエンティティと照らし合わせられます。コードでは、コンパイラーがチェックするラムダの `allow` と `alias` を使ってください。

Java でテストされています（[`SortPolicyTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortPolicyTest.java)、リージョン `string-paths`）。ラムダがないので、Kotlin でも同じように動きます。

```kotlin
// the string forms used by configuration files (lambdaql.sort.allow / lambdaql.sort.alias)
SortPolicy.register(emf, SortPolicy.of(SortMode.STRICT,
    SortRules.of(Order::class.java).allowPath("price", "customer.name").aliasPath("newest", "updateAt")))
```

- チェックは、spec を `orderBy` で使ったとき、パスを読む前に行われます。
  そのため `STRICT` モードでは、ルールにない名前は、プロパティでなくても `SortNotAllowedException` になります。
- ルールは 1 つのエンティティクラスに属します。同じクラスの `SortRules` を 2 つ渡すと、最後のものが使われます。
- `SortNotAllowedException` は `LambdaQLException` です（[11. エラーと言語](../11-errors-and-languages.md#exceptions) を参照）。
  lambdaQL は Web への依存を持ちません。HTTP 400 で応答する例は [12.5](12-spring-data-jpa.md#sort-policy-with-spring) を参照してください。

**まだ実装されていないもの:**

- 名前と方向を 1 つにまとめた文字列（`"price,desc_nulls_last"`、ルール S11）。
  方向は `Direction.parse`（[4.3](#43-direction) を参照）、名前は `SortSpec.ofPath` で読んでください。
- **予定:** `SortSpec` の結合の書き方、キーセットページング。（結合のためのラムダの書き方 `{ o, c, by -> ... }` は実装済みです。[6.8](06-select-group.md#68-explicit-joins) を参照。）

<a id="411-sorting-inside-subqueries"></a>
## 4.11 サブクエリの中のソート

**実装済み**（マイルストーン M8-1、[5. サブクエリ](05-subquery.md) のサブクエリとともに）。MariaDB の `lateral` の書き換えはまだ実装されていません。

サブクエリの中のソートは、`limit`（top-1 または top-N）と一緒に使うときだけ残ります。
`limit` がなければ結果は変わらないので、lambdaQL はソートを取り除き、警告をログに出します。
[5. サブクエリ](05-subquery.md#59-sorting-inside-a-subquery) を参照してください。

<a id="412-rules-summary-s1-to-s14"></a>
## 4.12 ルールのまとめ（S1 から S14）

| # | ルール | 状況 |
|---|---|---|
| S1 | ソートは 1 つのラムダ: `{ o, by -> by... }`。キーが 1 つなら `{ it.key }` と、省略可能な `Direction`。join では `{ o, c, by -> by... }`（`by` は常に最後） | 実装済み。結合の書き方 `{ o, c, by -> }` も（Java のラムダでテスト済み） |
| S2 | キーは `Comparable`（Kotlin の nullable なキーは使える）。エンティティはキーにできない（コンパイルエラー）。キーはエンティティを使わなければならない | 実装済み |
| S3 | 方向は `asc` / `desc` または `sort(key, Direction)` で決まる。`asc(key)`/`desc(key)`（`ASC`/`DESC`）は NULL のポリシーを使い、`asc(key, nullsFirst)`、`desc(key, nullsFirst)`、`*_NULLS_*` は書いたとおりに使われる | 実装済み |
| S4 | 本体は `by` の呼び出しをまっすぐつなげたものだけ。`if`、`when`、ループは使えない | 実装済み |
| S5 | `orderBy` はクエリごとに 1 回。`orderBy` の後に `where` は使えない。`limit` の後に `orderBy` は使えない（コンパイルエラー） | 実装済み |
| S6 | 再利用には `SortSpec`（`of`、`ofPath`、`then`、`reversed`、`orderBy(spec)`）。外部の文字列は永続化ユニットのソートポリシー（`SortRules`、`SortPolicy`、`SortMode`。[4.10](#sort-policy) を参照）がチェック | `SortSpec` とソートポリシーは実装済み（Java のラムダでテスト済み）。`SortSpec` の結合の書き方はまだ実装されていない |
| S7 | キー: カラム、to-one のパス、`+ - *`、拡大変換。値の分岐は使えない。Kotlin の `?.` はパスとして読まれる | 実装済み（Kotlin のラムダではテストされていない） |
| S8 | `orderBy` だけで使う関連のパスは `left join` を使うので、関連のない行も残る。同じパスを `where` が使うと、ソートは `where` の結合を使う | 実装済み |
| S9 | ページングでは、キーに id が含まれていなければルートの `@Id asc` が追加される。コレクションの fetch join + ページングは拒否される | ルートの id と、明示的に結合したエンティティの id については実装済み（Java のラムダでテスト済み）。fetch join のチェックはまだ実装されていない |
| S10 | 解析結果はラムダごとにキャッシュされる。キャプチャした `Direction` は呼び出しのたびに読まれる | 実装済み |
| S11 | Spring の `Sort.Order` は `Direction` に 1:1 で対応（Spring モジュール。[12.5](12-spring-data-jpa.md#125-spring-sort-and-pageable) を参照）。プロパティ名はソートポリシーのチェックを通り、エイリアスはそのまま渡される | Spring の `Sort` は実装済み、Java でテスト済み（`ignoreCase` はまだサポートしていない）。ソートポリシーと合わせた場合もテスト済み。`"price,desc_nulls_last"` のような 1 つの文字列の読み取りはまだ実装されていない |
| S12 | join のクエリも同じ形: `{ o, c, by -> }`、`{ o, c, i, by -> }` | 実装済み（Java のラムダでテスト済み、[6.8](06-select-group.md#68-explicit-joins) を参照） |
| S13 | プロパティ参照はキーが 1 つの短い書き方（`orderBy(Order::price)`）でのみ。コンパイルできても意味が違う参照は拒否される | まだ実装されていない（Kotlin のプロパティ参照） |
| S14 | サブクエリのソート: `limit` があれば残り、`limit` がなければ警告付きで取り除かれる。データベースの違いは自動で処理される（5 章を参照） | 実装済み（テスト済み）。MariaDB の `lateral` の書き換えはまだ実装されていない |

---

[Kotlin トラック](README.md) | 前: [3. 条件](03-where.md) | 次: [5. サブクエリ](05-subquery.md) | [このページの Java 版](../java/04-order-paging.md)
