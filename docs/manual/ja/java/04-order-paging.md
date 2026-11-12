<a id="4-sorting-and-paging-java"></a>
# 4. ソートとページング（Java）

> [!NOTE]
> **状況**
> - **中核部分は実装済み、テスト済み**（H2、MariaDB 10.11、Oracle 23.26、Hibernate 6.6.5）: `by.asc`、`by.desc`、`by.sort` を使う `orderBy((o, by) -> ...)`、キーが 1 つの書き方 `orderBy(o -> key)` と `orderBy(key, Direction)`（`Order::getPrice` のような getter のメソッド参照も可）、`Direction.parse` / `reversed`、`limit` / `offset`。
> - **まだ実装されていないもの:** `SortSpec`、`SortWhitelist`、結合の書き方 `(o, c, by)`、サブクエリの中のソート、Spring の `Sort` / `Pageable` モジュール。
> - **(テスト済み)** と書かれた例は、テストスイートからコピーしたものです。この章のそれ以外の HQL も同じルールに従いますが、テストはされていません。

[Java トラック](README.md) | [マニュアルのホーム](../README.md) | 前: [3. 条件](03-where.md) | 次: [5. サブクエリ](05-subquery.md) | [このページの Kotlin 版](../kotlin/04-order-paging.md)

共通ページ: [1. 概要](../01-overview.md) · [11. エラーと言語](../11-errors-and-languages.md)

---

<a id="41-one-lambda-for-the-whole-sort"></a>
## 4.1 ソート全体を 1 つのラムダで書く

ソートは **1 つのラムダ** で書きます。ラムダは、エンティティと、最後の引数としてソートビルダー `by` を受け取ります。
キーと方向は `by.asc(...)` と `by.desc(...)` で追加します。

```java
qb.from(Order.class)
  .where(o -> o.isActive())
  .orderBy((o, by) -> by.desc(o.getPrice()).asc(o.getCustomer().getName()).asc(o.getId()))
  .limit(20).offset(40)
  .toList();
// HQL: select e0 from Order e0 left join e0.customer j1
//      where e0.active = true
//      order by e0.price desc nulls last, j1.name asc nulls first, e0.id asc
```

**SQL ブロックの読み方。** `…` は省略した select リスト（エンティティのすべてのカラム）で、`?` はバインドパラメーターです。各行は [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java) から取っています。このテストは各クエリを Hibernate 6.6.5 で H2 2.3、MariaDB 10.11、Oracle 23.26 に対して実行し、Hibernate の SQL が変わると失敗します。

**データベースごとの SQL（この HQL に対する Hibernate の SQL）** — マニュアルの `Order` には NULL になりうる `Double price` がありますが、テストの `Order` にはないので、`SqlByDatabaseTest` はこの HQL を Hibernate に直接渡します。`expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `sort-overview`:

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
- static import は必要ありません。`by` はラムダの引数です。
- キーとその方向は一緒に書きます。順序がずれることはありません。
- lambdaQL はラムダを読み取ります。実行はしません。`by` のメソッドは順序を記述するだけです。

**(テスト済み)** と書かれた例は、[`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java) からコピーしたものです。
このテストは HQL をチェックし、クエリを実行して、同じソートを Java 側で行った結果と行の順序を比べます。H2、MariaDB、Oracle で通ります。
テストでは [3.3](03-where.md#33-basic-form) のテスト用エンティティを使います。`price` は `double`、`status` は `short` です。これらのキーは NULL になることがないので、HQL に `nulls` の部分がありません。
それ以外の例では、`price` は NULL になりうる `Double` です（[3.2](03-where.md#32-example-entities) を参照）。そのため `nulls first` または `nulls last` が付きます（[4.4](#44-null-ordering-is-the-same-on-every-database) を参照）。

**(テスト済み)** [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java)、リージョン `sort-basic`:

```java
RenderedQuery q = qb.from(Order.class)
        .where(o -> o.isActive())
        .orderBy((o, by) -> by.desc(o.getPrice()).asc(o.getId()))
        .toHql();
// price and id can never be NULL, so there is no "nulls" part
assertEquals("select e0 from Order e0 where e0.active = true order by e0.price desc, e0.id asc", q.hql());
```

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `sort-basic`:

```sql
-- H2
select …
from orders o1_0
where o1_0.active=true
order by o1_0.price desc,o1_0.id

-- MariaDB, Oracle
select …
from orders o1_0
where o1_0.active=1
order by o1_0.price desc,o1_0.id
```

`toHql()` はクエリを実行せずに HQL を返します。`toList()` はクエリを実行します。

<a id="42-the-short-forms-one-key"></a>
## 4.2 短い書き方（キーが 1 つ）

キーが 1 つだけなら、`by` を省略できます。

| Java | HQL |
|---|---|
| `orderBy(o -> o.getPrice())` | `order by e0.price asc nulls first` |
| `orderBy(o -> o.getUpdateAt(), Direction.DESC)` | `order by e0.updateAt desc nulls last` |
| `orderBy(Order::getPrice)` | `order by e0.price asc nulls first` |
| `orderBy(Order::getPrice, Direction.DESC)` | `order by e0.price desc nulls last` |

`Direction` を指定しない場合、短い書き方は昇順（`ASC`）でソートします。
lambdaQL は、ラムダの引数の数（1 つか 2 つか）で書き方を区別します。Java コンパイラーはあいまいさなく正しいオーバーロードを選びます。

メソッド参照は、`Order::getPrice` のようなエンティティの getter でなければなりません。static メソッド、引数のあるメソッド、オブジェクトに束縛された参照は拒否されます（ルール S13）。
`by` を使う書き方（引数 2 つ）にはラムダが必要です。そこでメソッド参照を使うと拒否されます。

**(テスト済み)** [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java)、リージョン `sort-key`（テスト用エンティティでは `price` は `double` なので、`nulls` の部分はありません）:

```java
assertEquals("select e0 from Order e0 order by e0.price asc",
        qb.from(Order.class).orderBy(o -> o.getPrice()).toHql().hql());
assertEquals("select e0 from Order e0 order by e0.price desc",
        qb.from(Order.class).orderBy(Order::getPrice, Direction.DESC).toHql().hql());
```

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `sort-key`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
order by o1_0.price
```

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `sort-key#2`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
order by o1_0.price desc
```

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
- `reversed()` は逆の方向を返します。NULL の位置も反転するので、順序全体が逆になります: `ASC.reversed()` は `DESC`、`ASC_NULLS_LAST.reversed()` は `DESC_NULLS_FIRST` です。

**NULL の位置を選ぶ** には、2 番目の `boolean` 引数を使います: `by.asc(key, nullsFirst)` と `by.desc(key, nullsFirst)`（決定 D35）。
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

**(テスト済み)** [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java)、リージョン `sort-nulls`（`description` は NULL になりうる `String`、`id` は NULL になることがありません）:

```java
// default: asc puts NULL first, desc puts NULL last (the same on every database)
assertEquals("select e0 from Order e0 order by e0.description asc nulls first, e0.id asc",
        qb.from(Order.class).orderBy((o, by) -> by.asc(o.getDescription()).asc(o.getId())).toHql().hql());
// the second argument chooses: true = NULL first, false = NULL last
assertEquals("select e0 from Order e0 order by e0.description asc nulls last, e0.id asc",
        qb.from(Order.class).orderBy((o, by) -> by.asc(o.getDescription(), false).asc(o.getId())).toHql().hql());
assertEquals("select e0 from Order e0 order by e0.description desc nulls first, e0.id asc",
        qb.from(Order.class).orderBy((o, by) -> by.desc(o.getDescription(), true).asc(o.getId())).toHql().hql());
```

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `sort-nulls`:

```sql
-- H2, MariaDB
select …
from orders o1_0
order by o1_0.description,o1_0.id

-- Oracle
select …
from orders o1_0
order by o1_0.description asc nulls first,o1_0.id
```

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `sort-nulls#2`:

```sql
-- H2
select …
from orders o1_0
order by o1_0.description asc nulls last,o1_0.id

-- MariaDB
select …
from orders o1_0
order by case when (o1_0.description) is null then 1 else 0 end,o1_0.description,o1_0.id

-- Oracle
select …
from orders o1_0
order by o1_0.description,o1_0.id
```

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `sort-nulls#3`:

```sql
-- H2
select …
from orders o1_0
order by o1_0.description desc nulls first,o1_0.id

-- MariaDB
select …
from orders o1_0
order by case when (o1_0.description) is null then 0 else 1 end,o1_0.description desc,o1_0.id

-- Oracle
select …
from orders o1_0
order by o1_0.description desc,o1_0.id
```

SQL で NULL のポリシーを確認できます。H2 と MariaDB は、自分のデフォルトと同じなら句を省きます。Oracle は自分のデフォルトが逆なので、`asc nulls first` / `desc nulls last` を書きます。MariaDB には `NULLS` 構文がないので、デフォルトでない位置は先に `case when … is null then … end` で並べます。

**方向が変数から来るとき** は、キャプチャした `Direction` と一緒に `by.sort(key, direction)` を使います。

```java
.orderBy((o, by) -> by.desc(o.getDescription(), false).asc(o.getId()))   // nulls last

Direction dir = request.isNewestFirst() ? Direction.DESC : Direction.ASC;
.orderBy((o, by) -> by.sort(o.getUpdateAt(), dir).asc(o.getId()))   // dir は呼び出しのたびに読み取られる
```

ここでの三項演算子 `request.isNewestFirst() ? ... : ...` は問題ありません。ラムダの **外** で実行されるからです。

**(テスト済み)** [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java)、リージョン `sort-dynamic`:

```java
Direction dir = Direction.parse(text);           // for example from a request parameter
OrderedSelectQuery<Order> q = qb.from(Order.class)
        .orderBy((o, by) -> by.sort(o.getDescription(), dir).asc(o.getId()));
```

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `sort-dynamic`:

```sql
-- H2, MariaDB
select …
from orders o1_0
order by o1_0.description desc,o1_0.id

-- Oracle
select …
from orders o1_0
order by o1_0.description desc nulls last,o1_0.id
```

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `sort-dynamic#2`:

```sql
-- H2
select …
from orders o1_0
order by o1_0.description asc nulls last,o1_0.id

-- MariaDB
select …
from orders o1_0
order by case when (o1_0.description) is null then 1 else 0 end,o1_0.description,o1_0.id

-- Oracle
select …
from orders o1_0
order by o1_0.description,o1_0.id
```

テストはこのコードを 2 回実行します。`text` は `"desc"` と `"asc_nulls_last"` です。
HQL は `... order by e0.description desc nulls last, e0.id asc` と、次に `... order by e0.description asc nulls last, e0.id asc` になります。
ラムダの解析は 1 回だけです。`dir` はクエリを実行するたびに読み取られます（ルール S10）。方向が `null` なら、クエリの実行時に例外になります。

> [!TIP]
> `false` だけでは意味が伝わりにくいです。Java でも Kotlin でも `by.desc(x, false)` と書きます。Kotlin は Java のメソッドに名前付き引数（`nullsFirst = false`）を使えないからです。
> そのため、`// nulls last` のような短いコメントを付ける習慣がおすすめです。

<a id="44-null-ordering-is-the-same-on-every-database"></a>
## 4.4 NULL の順序はどのデータベースでも同じ

NULL をどこに置くかは、データベースによって異なります。

| | H2 | MariaDB | Oracle |
|---|---|---|---|
| `ASC` のデフォルト | NULL が先頭 | NULL が先頭 | NULL が **末尾** |
| `DESC` のデフォルト | NULL が末尾 | NULL が末尾 | NULL が **先頭** |
| SQL の `NULLS FIRST/LAST` | 使える | **使えない（構文エラー）** | 使える |

lambdaQL は 1 つのポリシーでこれを解決します。**NULL は最も小さい値です。**

- `ASC` = `ASC_NULLS_FIRST`
- `DESC` = `DESC_NULLS_LAST`

これは Java の `Comparator.nullsFirst(Comparator.naturalOrder())` と同じです。
このポリシーにより、3 つのデータベースはすべて同じ順序を返します（数値と日付の場合）。

**lambdaQL の書き方。** NULL になりうるキーにはすべて、lambdaQL が HQL に NULL の位置を書きます。たとえば `e0.description asc nulls first` です。
NULL になることがないキー（`double` のようなプリミティブのカラムや `@Id`）には `nulls` の部分を付けません。
関連の先にあるカラムは常に NULL になりえます（顧客のない注文がありうるため）。なので常に付けます。

その後、Hibernate がデータベースごとに SQL を書きます。データベースのデフォルトと同じなら、その句を省きます。
MariaDB には `NULLS` 構文がないので、デフォルトと異なる位置には Hibernate が `CASE` 式を書きます。

生成される SQL:

| 書いたもの | H2 | MariaDB | Oracle |
|---|---|---|---|
| `asc(key)`、`desc(key)`（`ASC`、`DESC`） | 追加の句なし（すでにデフォルト） | 追加の句なし | `nulls first` / `nulls last` を追加 |
| DB のデフォルトと同じ NULL の位置（`asc(key, b)`、`desc(key, b)`、`*_NULLS_*`） | 追加の句なし | 追加の句なし | 追加の句なし |
| DB のデフォルトと **異なる** NULL の位置 | `nulls first/last` | Hibernate が `case when x is null ...` を書く | `nulls first/last` |
| NULL になることがないキー（方向は問わない） | 追加の句なし | 追加の句なし | 追加の句なし |

> [!TIP]
> MariaDB では、`by.asc(key, false)`（`ASC_NULLS_LAST`）と `by.desc(key, true)`（`DESC_NULLS_FIRST`）は `CASE` 式になります。そのソートにはデータベースがインデックスを使えません。できるだけ `asc(key)` / `desc(key)` を使ってください。

<a id="45-what-a-sort-key-can-be"></a>
## 4.5 ソートキーにできるもの

| キー | 使えるか | 注意 |
|---|---|---|
| カラム: `o.getPrice()` | はい | テスト済み |
| to-one のパス: `o.getCustomer().getName()` | はい | `left join`、下記を参照（テスト済み） |
| 算術: `o.getPrice() * 2`、`+`、`-` | はい | まだテストされていない |
| 拡大変換（`int` から `long`） | はい | まだテストされていない |
| エンティティ: `by.desc(o.getCustomer())` | **コンパイルエラー** | エンティティは `Comparable` ではない |
| エンティティを使わない値: `by.asc(1)`、キャプチャした値 | **拒否** | [4.6](#46-rules-for-the-lambda-body) を参照（テスト済み） |
| カラムへの関数: `lower(name)` | 予定 | |

キーは `Comparable` でなければなりません。ボクシング型（`Double`、`Integer`、`Long`）、`String`、`java.time` の型は `Comparable` なので使えます。Java コンパイラーはエンティティのキーを拒否します。
エンティティのクラスが `Comparable` を実装している場合は、lambdaQL がラムダを読み取るときにキーとして拒否します。

**関連のパスには `left join` を使います。** HQL の単純なパス `order by o.customer.name` は、3 つのデータベースすべてで inner join になります。顧客のない注文が消えてしまいます。
lambdaQL は代わりに `left join` を追加します。顧客のない注文も結果に残ります。その `j1.name` は NULL なので、`asc` では先頭に来ます。

**(テスト済み)** [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java)、リージョン `sort-path`:

```java
RenderedQuery q = qb.from(Order.class)
        .orderBy((o, by) -> by.asc(o.getCustomer().getName()).asc(o.getId()))
        .toHql();
// a path used only for sorting is a LEFT JOIN: the order without a customer stays
assertEquals("select e0 from Order e0 left join e0.customer j1 order by j1.name asc nulls first, e0.id asc", q.hql());
```

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `sort-path`:

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

エイリアスは `where` と共有されます。同じパスを `where` がすでに結合していれば、ソートはその結合を使います。
ほとんどの場合、それは内部結合です。`where` の条件が、顧客のない注文をすでに除いているからです（決定 D36、[3.10](03-where.md#310-association-paths) を参照）。

**(テスト済み)** [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java)、リージョン `sort-path-where`:

```java
String name = "Kim";
RenderedQuery q = qb.from(Order.class)
        .where(o -> o.getCustomer().getName().equals(name))
        .orderBy((o, by) -> by.desc(o.getCustomer().getName()).desc(o.getPrice()))
        .toHql();
// the condition already joined the customer, so the sort uses the same inner join
assertEquals("select e0 from Order e0 join e0.customer j1 where j1.name = :p1"
        + " order by j1.name desc nulls last, e0.price desc", q.hql());
```

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `sort-path-where`:

```sql
-- H2, MariaDB
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name=?
order by c1_0.name desc,o1_0.price desc

-- Oracle
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name=?
order by c1_0.name desc nulls last,o1_0.price desc
```

<a id="46-rules-for-the-lambda-body"></a>
## 4.6 ラムダ本体のルール

本体は **`by` 呼び出しのまっすぐなチェーン** でなければなりません。

```java
// OK
.orderBy((o, by) -> by.desc(o.getPrice()).asc(o.getId()))

// 拒否: 条件によって変わるキー
.orderBy((o, by) -> flag ? by.desc(o.getPrice()) : by.asc(o.getId()))

// 拒否: ループ、if、ローカル変数
```

lambdaQL は `orderBy` を呼んだときにこれをチェックし、`LambdaAnalysisException` を投げます。
どのキーもエンティティを使わなければなりません。定数やキャプチャした値だけのキーは拒否されます。

**(テスト済み)** [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java)、リージョン `sort-rejected`:

```java
// a condition inside the sort lambda (rule S4)
assertThrows(LambdaAnalysisException.class, () -> qb.from(Order.class)
        .orderBy((o, by) -> o.isActive() ? by.asc(o.getPrice()) : by.desc(o.getPrice())));
// a key without the entity
assertThrows(LambdaAnalysisException.class, () -> qb.from(Order.class).orderBy((o, by) -> by.asc(1)));
```

実行時に方向を変える必要があるなら、変数として渡します: `by.sort(key, dir)`。キーそのものを変える必要があるなら、通常の Java コードの 2 つの分岐でクエリを組み立ててください。

<a id="47-order-of-calls"></a>
## 4.7 呼び出しの順序

ビルダーの型が順序を制御します。間違った順序はコンパイルできません。

| 呼び出し | 使えるか |
|---|---|
| `from(...).where(...).orderBy(...).limit(...).offset(...)` | はい |
| `from(...).where(...).limit(...).offset(...)`（`orderBy` なし） | はい。行は id でソートされる（[4.8](#48-paging) を参照） |
| `orderBy(...)` を 2 回 | いいえ（コンパイルエラー） |
| `orderBy(...)` の後の `where(...)` | いいえ（コンパイルエラー） |
| `limit(...)` の後の `orderBy(...)` | いいえ（コンパイルエラー） |

`orderBy`、`limit`、`offset` の後、クエリの型は `OrderedSelectQuery` になります。使えるのは `limit`、`offset`、`toHql()`、`toList()` だけです。

<a id="48-paging"></a>
## 4.8 ページング

```java
qb.from(Order.class)
  .where(o -> o.getStatus() == 1)
  .orderBy((o, by) -> by.desc(o.getPrice()))
  .limit(20)      // ページサイズ
  .offset(40)     // 40 行をスキップ（3 ページ目）
  .toList();
// HQL: select e0 from Order e0 where e0.status = 1 order by e0.price desc nulls last, e0.id asc
// limit と offset は Hibernate に渡される（setMaxResults / setFirstResult）
```

**データベースごとの SQL（この HQL に対する Hibernate の SQL）** — マニュアルの `Order` には NULL になりうる `Double price` がありますが、テストの `Order` にはないので、`SqlByDatabaseTest` はこの HQL を Hibernate に直接渡します。`expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `page-design`:

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

**(テスト済み)** [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java)、リージョン `sort-page`:

```java
OrderedSelectQuery<Order> page = qb.from(Order.class)
        .orderBy((o, by) -> by.asc(o.getStatus()))
        .limit(2)
        .offset(2);
// status has ties, so the id is added as the last key and rows do not move between pages
assertEquals("select e0 from Order e0 order by e0.status asc, e0.id asc", page.toHql().hql());
List<Order> rows = page.toList();
```

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `sort-page`:

```sql
-- H2, Oracle
select …
from orders o1_0
order by o1_0.status,o1_0.id
offset ? rows
fetch first ? rows only

-- MariaDB
select …
from orders o1_0
order by o1_0.status,o1_0.id
limit ?,?
```

ページングの SQL はデータベースごとに違います。H2 と Oracle は `offset ? rows fetch first ? rows only`、MariaDB は `limit ?,?` になります。offset がなければ、`fetch first ? rows only` または `limit ?` だけになります（下の `page-only` を参照）。

`limit` と `offset` には 0 以上の値を渡します。負の値は `IllegalArgumentException` を投げます。

lambdaQL は HQL に `LIMIT` を書きません。Hibernate がデータベースごとに正しい SQL を書きます。

| データベース | SQL |
|---|---|
| H2 | `offset ? rows fetch first ? rows only` |
| MariaDB | `limit ?, ?` |
| Oracle | `offset ? rows fetch first ? rows only` |

**`orderBy` なしのページング。** `orderBy` を使わずに `limit` と `offset` を呼ぶこともできます。そのときは lambdaQL が行を id でソートするので、ページは安定します。

**(テスト済み)** [`SortQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java)、リージョン `page-only`:

```java
RenderedQuery q = qb.from(Order.class).where(o -> o.isActive()).limit(2).toHql();
assertEquals("select e0 from Order e0 where e0.active = true order by e0.id asc", q.hql());
```

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `page-only`:

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

<a id="tie-breaker-automatic-id"></a>
### タイブレーカー（`id` の自動追加）

上の HQL を見てください。lambdaQL は最後に `e0.id asc` を追加しました。なぜでしょうか。

- 多くの行が同じ `status` や `price` を持つことがあります。データベースはそれらの行を **任意の順序** で返すことがあります。Oracle は実際にそうします（計測済み）。
- すると、2 ページ目と 3 ページ目に同じ行が 2 回表示されたり、行が飛ばされたりすることがあります。

ルール: **ページングを使い、ソートキーに id が含まれていない場合、lambdaQL はルートの `@Id asc` を最後のキーとして追加します。**

- 追加は 1 回だけです。キーにすでに id が含まれていれば（たとえば `by.desc(o.getId())`）、追加しません。
- `limit` と `offset` がなければ、何も追加しません。
- **まだ実装されていません**（結合がまだ実装されていないため）: to-many の結合がある場合は、結合したエンティティの id も追加します。コレクションの fetch join と `limit`/`offset` を一緒に使うと拒否されます。

<a id="49-strings-and-special-values"></a>
## 4.9 文字列と特殊な値

- 文字列の順序はデータベースの照合順序（collation）に従います。MariaDB の `_ci` は大文字と小文字を区別しません。Oracle は `NLS_SORT` を使い、これは JVM のロケールに従います（たとえば `de_DE` ではドイツ語の順序になります）。Oracle では `NLS_SORT=BINARY` をおすすめします。
- Oracle は `''` を NULL として保存するので、空文字列は NULL としてソートされます。
- `NaN` の順序はデータベースごとに異なります。

<a id="410-reusable-and-external-sorts"></a>
## 4.10 再利用できるソートと外部からのソート

**まだ実装されていません。** 設計には次のものもあります。

- 再利用できるソートのための `SortSpec`。`then()` と `reversed()` があり、`orderBy(spec)` に渡します。

  ```java
  var newest = SortSpec.of(Order.class, (o, by) -> by.desc(o.getUpdateAt()).asc(o.getId()));
  qb.from(Order.class).orderBy(newest).limit(20).toList();
  ```

- 外部の文字列（`"price,desc_nulls_last"` のようなリクエストパラメーター）をソートに変換する `SortWhitelist<T>`。外部の文字列はホワイトリストを通したときだけ受け付けます。
- **予定:** Spring の `Sort` / `Pageable` モジュール（Spring の各 `Sort.Order` は 1 つの `Direction` 値に対応）、`(o, c, by) -> ...` による結合、キーセットページング。

それまでは、`Direction.parse` と `by.sort(key, dir)` で実行時に方向を決められます（[4.3](#43-direction) を参照）。キーは通常の Java コードで選んでください。

<a id="411-sorting-inside-subqueries"></a>
## 4.11 サブクエリの中のソート

**まだ実装されていません**（サブクエリがまだ実装されていないため）。

サブクエリの中のソートは、`limit`（top-1 または top-N）と一緒に使ったときだけ残ります。
`limit` がなければ結果は変わらないので、lambdaQL はそのソートを削除し、警告をログに出します。
[5. サブクエリ](05-subquery.md#59-sorting-inside-a-subquery) を参照してください。

<a id="412-rules-summary-s1-to-s14"></a>
## 4.12 ルールのまとめ（S1〜S14）

| # | ルール | 状況 |
|---|---|---|
| S1 | ソートは 1 つのラムダ: `(o, by) -> by...`。キーが 1 つなら: `o -> key` と、省略可能な `Direction`。結合なら: `(o, c, by) -> by...`（`by` は常に最後） | 実装済み（結合の書き方を除く） |
| S2 | キーは `Comparable`。エンティティはキーにできない（コンパイルエラー）。キーはエンティティを使わなければならない | 実装済み |
| S3 | 方向は `asc` / `desc` または `sort(key, Direction)` で決まる。`asc(key)`/`desc(key)`（`ASC`/`DESC`）は NULL のポリシーを使い、`asc(key, nullsFirst)`、`desc(key, nullsFirst)`、`*_NULLS_*` は書いたとおりに使う | 実装済み |
| S4 | 本体は `by` 呼び出しのまっすぐなチェーンだけ。`if`、三項演算子、ループは不可 | 実装済み |
| S5 | `orderBy` はクエリごとに 1 回。`orderBy` の後に `where` は不可。`limit` の後に `orderBy` は不可（コンパイルエラー） | 実装済み |
| S6 | 再利用は `SortSpec` で。外部の文字列は `SortWhitelist` を通したときだけ | まだ実装されていない |
| S7 | キー: カラム、to-one のパス、`+ - *`、拡大変換。値の分岐は不可 | 実装済み。カラムと to-one のパスはテスト済み |
| S8 | `orderBy` だけで使う関連のパスは `left join` を使うので、関連のない行も残る。同じパスを `where` が使うと、ソートは `where` の結合を使う | 実装済み |
| S9 | ページングでは、キーに id が含まれていなければルートの `@Id asc` を追加。コレクションの fetch join + ページングは拒否 | ルートの id については実装済み。結合の部分はまだ実装されていない |
| S10 | 解析はラムダごとにキャッシュされる。キャプチャした `Direction` は呼び出しのたびに読み取られる | 実装済み |
| S11 | Spring の `Sort.Order` は `Direction` に 1:1 で対応（予定のモジュール）。`SortWhitelist.parse("price,desc_nulls_last")` | まだ実装されていない |
| S12 | 結合クエリも同じ形: `(o, c, by)`、`(o, c, i, by)` | まだ実装されていない |
| S13 | メソッド参照は、キーが 1 つの短い書き方でだけ使える。コンパイルはできるが意味が間違っているメソッド参照は拒否される（[9. Java の注意点](09-java-notes.md#99-method-references) を参照） | 実装済み（getter の参照） |
| S14 | サブクエリのソート: `limit` があれば残し、`limit` がなければ警告を出して削除。データベースの違いは lambdaQL が処理する（5 章を参照） | まだ実装されていない |

---

[Java トラック](README.md) | 前: [3. 条件](03-where.md) | 次: [5. サブクエリ](05-subquery.md) | [このページの Kotlin 版](../kotlin/04-order-paging.md)
