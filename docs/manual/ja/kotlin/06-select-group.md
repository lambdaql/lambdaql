<a id="6-select-aggregates-and-group-by-kotlin"></a>
# 6. 選択、集計、グループ化（Kotlin）

> [!NOTE]
> **状況**
> - **実装済み**（マイルストーン M9、決定 D27）、H2 2.3、MariaDB 10.11、Oracle 23.26（Hibernate 6.6.5）でテスト済み: 1 つの値、data class、`Pair`、`Triple` の `select`、キーが 1 つまたは複数（`listOf`、`a to b`、`Triple`）の `groupBy`、`having`、`Agg` 関数、`distinct()`、`limit`、`offset`、`toList`、`toSet`、`first`、`one`、`orderBy { }.select { }`、ルール J3、J4、J7 のチェック。テストは **Java** のラムダを使います。**Kotlin のラムダはまだビルドの中でテストされていません。**
> - この章の Kotlin の形（data class、`to`、`it.customer?.id` を含む `listOf` のキー、`Triple`、`select` と `having` の中の `Agg`）は、ビルドの外で kotlinc 2.3.21 でコンパイルしました。lambdaQL はこれらに対して Java の形と同じ HQL を作ります。
> - **設計済み、まだ実装されていないもの:** 派生テーブルと CTE（6.8 と 6.9、マイルストーン M8-2）、集約によるソートとグループ化したクエリの `orderBy`（v0.2 の候補）、結合に対する射影 `{ o, c -> ... }`。
> - Kotlin のコードは、テスト済みの Java のクエリを Kotlin の構文で示したものです。テスト済みの Java の例は [このページの Java 版](../java/06-select-group.md) にあります。全体の状況は [トラックの README](README.md) を参照してください。

[Kotlin トラック](README.md) | [マニュアルのホーム](../README.md) | 前: [5. サブクエリ](05-subquery.md) | 次: [7. 更新、挿入、削除](07-dml.md) | [このページの Java 版](../java/06-select-group.md)

共通ページ: [1. 概要](../01-overview.md) · [11. エラーと言語](../11-errors-and-languages.md)

---

この章では、クエリが **何を** 返すかを選ぶ方法を説明します。

- 行ごとに 1 つの値（`select { it.price }`）
- Kotlin の data class、`Pair`、`Triple`
- `groupBy` と `having` を使った集約（`count`、`sum`、`avg`、...）
- 値のクエリの `distinct`、ページング、結果の関数
- FROM 句の中のサブクエリ（派生テーブル）と CTE（`with`）: 設計のみ

<a id="contents"></a>
## 目次

- [6.1 例で使うエンティティと結果の型](#61-example-entities-and-result-types)
- [6.2 1 つの値を選択する](#62-select-one-value)
- [6.3 data class、Pair、Triple を選択する](#63-select-a-data-class-pair-or-triple)
- [6.4 集約関数: `Agg`](#64-aggregate-functions-agg)
- [6.5 グループ化（group by）](#65-group-by)
- [6.6 having](#66-having)
- [6.7 distinct、ページング、結果](#67-distinct-paging-and-results)
- [6.8 派生テーブル（FROM の中のサブクエリ）](#68-derived-table-subquery-in-from)
- [6.9 CTE（`with`）](#69-cte-with)
- [6.10 ルール J1 から J8](#610-rules-j1-to-j8)
- [6.11 v0.1 にないもの](#611-not-in-v01)

---

<a id="61-example-entities-and-result-types"></a>
## 6.1 例で使うエンティティと結果の型

例では次のエンティティを使います（[2. はじめよう](02-getting-started.md#example-entities) を参照）。

| エンティティ | ここで使うプロパティ |
|---|---|
| `Order` | `id`、`price`、`status`、`active`、`customer`（多対一） |
| `Customer` | `id`、`name`、`grade` |
| `Item` | `order`、`price`、`qty` |

また、次の結果の型を使います。

```kotlin
data class OrderView(val id: Long?, val customerName: String?, val price: Double?)
data class Stat(val customerId: Long?, val count: Long, val total: Double?)
data class KeyCount(val customerId: Long?, val status: Int?, val count: Long)
data class Total(val count: Long, val average: Double?)
```

> [!TIP]
> 結果には nullable な型（`Double?`、`String?`）を使ってください。
> カラムや `sum` のような集約は NULL になることがあります（ルール J4）。non-null の `Double` や `Long` のパラメーターは JVM のプリミティブで、non-null の `String` のパラメーターも NULL を受け付けません。
> NULL がそのようなパラメーターに入ると、lambdaQL は `IllegalStateException` を投げます。プリミティブの場合、メッセージは引数を示し、ラッパー型を使うように伝えます。
> `count` は決して NULL を返さないので、`count: Long` で問題ありません。

Kotlin のテストはまだありません。Java で書いた同じクエリは [`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java) でテストしています（H2、MariaDB、Oracle）。テスト済みの例は [このページの Java 版](../java/06-select-group.md) を見てください。

---

<a id="62-select-one-value"></a>
## 6.2 1 つの値を選択する

`select` は 1 つのラムダを受け取ります。ラムダは 1 つの値を返します。結果はその値の `ValueQuery` です。
`select` は `where` の後でも `orderBy` の後でも呼び出せます。ソート、`limit`、`offset` はそのまま残ります。

```kotlin
val prices: List<Double?> = qb.from(Order::class.java)
    .where { it.status == 1 }
    .orderBy { o, by -> by.asc(o.price) }
    .select { it.price }
    .toList()
// HQL: select e0.price from Order e0 where e0.status = 1 order by e0.price asc nulls first
```

ここでは `price` は `Double?` なので、ソートに `nulls first` が付きます（[4.4](04-order-paging.md#44-null-ordering-is-the-same-on-every-database) を参照）。Java のテストでは `price` は `double` なので、その HQL（例 `value`）には `nulls` の部分がありません。

**SQL ブロックの読み方。** `?` はバインドパラメーターです。各行は [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java) から取っています。このテストは各クエリを Hibernate 6.6.5 で H2 2.3、MariaDB 10.11、Oracle 23.26 に対して実行し、Hibernate の SQL が変わると失敗します。この章では select リストが要点なので、省略しません。このテストは Java のラムダを使います。意味が同じ Kotlin のラムダは同じ HQL になるので、SQL も同じです。

**データベースごとの SQL（Java のテスト実行から）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `value`:

```sql
-- H2, MariaDB, Oracle (same)
select o1_0.price
from orders o1_0
where o1_0.status=1
order by o1_0.price
```

関連のパスは、`where` と同じくカラムです（ルール R24）。`select` では lambdaQL は `left join` を追加するので、customer のない注文も失われません（`where` では同じパスがデフォルトで内部結合になります）。

```kotlin
.select { it.customer?.name }
// HQL: select j1.name from Order e0 left join e0.customer j1
```

`it.customer!!.name` も同じ HQL になります。[6.3](#63-select-a-data-class-pair-or-triple) の data class の例が、この `left join` を示しています。
パスが関連エンティティの `@Id` で終わる場合、join はありません。
`it.customer?.id` は外部キーのカラム `customer_id` を読みます。

ほかの値の形（Kotlin 固有の形、設計。[9. Kotlin の注意点](09-kotlin-notes.md) を参照）:

```kotlin
.select { it.description ?: "" }              // coalesce(e0.description, '')
.select { it.customer?.name ?: "none" }       // coalesce(j1.name, 'none') + left join
.select { (it.price ?: 0.0) * 2 }             // coalesce(e0.price, 0.0) * 2
```

boolean の elvis（`select { it.flag ?: false }`）と値の `if`/`when` は拒否されます（制限 KC21、KC6）。

---

<a id="63-select-a-data-class-pair-or-triple"></a>
## 6.3 data class、Pair、Triple を選択する

ラムダの中でコンストラクターを呼び出します。コンストラクターの各引数は値の式です。

```kotlin
// data class
val list: List<OrderView> = qb.from(Order::class.java)
    .select { OrderView(it.id, it.customer?.name, it.price) }
    .toList()
// HQL: select e0.id, j1.name, e0.price from Order e0 left join e0.customer j1

// Pair（Triple も使える）
val pairs: List<Pair<String?, Double?>> = qb.from(Order::class.java)
    .select { it.customer?.name to it.price }
    .toList()
// HQL: select j1.name, e0.price from Order e0 left join e0.customer j1
```

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `record`（data class の例）:

```sql
-- H2, MariaDB, Oracle (same)
select o1_0.id,c1_0.name,o1_0.price
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
```

lambdaQL は `select new com.acme.OrderView(...)` を書き **ません**（ルール J2）。
カラムを選択し、自分でコンストラクターを呼び出します。data class、`Pair`、`Triple` のどれでも同じです。
これにより、長いクラス名やネストしたクラス名の問題を避けられます。
lambdaQL は各値をコンストラクターのパラメーターの型に変換します（たとえば Hibernate の `Integer` を `Long?` のパラメーターに）。

返せるもの:

| 結果の型 | 単純な `select` | 派生テーブルや CTE の行の型（設計） |
|---|---|---|
| 1 つの値（`it.price`） | 使える | – |
| Kotlin の data class | 使える | 使える |
| コンストラクターを持つ普通のクラス | 使える | **使えない**（J5） |
| `Pair` / `Triple` | 使える | 使えない |
| Java の record（Java モジュールから） | 使える | 使える |

---

<a id="64-aggregate-functions-agg"></a>
## 6.4 集約関数: `Agg`

集約関数は **`org.lambdaql.Agg` の static メソッド** です。
SQL 標準の関数なので、lambdaQL が提供します。（独自の DB 関数については [8. プロシージャと DB 関数](08-procedures-functions.md) を参照してください。）

| 関数 | SQL |
|---|---|
| `Agg.count()` | `count(*)` |
| `Agg.count(x)` | `count(x)` |
| `Agg.countDistinct(x)` | `count(distinct x)` |
| `Agg.sum(x)` | `sum(x)` |
| `Agg.avg(x)` | `avg(x)` |
| `Agg.min(x)` | `min(x)` |
| `Agg.max(x)` | `max(x)` |

Kotlin から見た型:

| 関数 | 戻り値 |
|---|---|
| `count()`、`count(x)`、`countDistinct(x)` | `Long` |
| `sum(x)` | `x` の型（プラットフォーム型） |
| `avg(x)` | `Double`（SQL では NULL になることがあるので、`Double?` として扱ってください） |
| `min(x)`、`max(x)` | `x` の型（任意の `Comparable`、nullable でもよい） |

import は任意です。関数を 1 つだけ import することもできます。

```kotlin
import org.lambdaql.Agg.sum

.having { sum(it.price) > 200 }
```

これは `xs.sum()` や `xs.count()` のような Kotlin 標準ライブラリの拡張関数とぶつかりません（コンパイラーで確認済み）。

重要な点:

- 戻り値は **仮の値** にすぎません。ラムダは実行されないので、その値が使われることはありません。
- lambdaQL のラムダの **外** で `Agg.sum(...)` を呼び出すと、`IllegalStateException` を投げます。
- `Agg` は `select`、`having`、`insert ... select` の値（[7.5](07-5-insert.md)）で使えます。
- `where` では `Agg` を使えません。集約に対する条件には `having` を使ってください。`where` で `Agg` を使うと解析時のエラーになります。

<a id="aggregate-over-all-rows-no-group-by"></a>
### すべての行に対する集約（group by なし）

```kotlin
val t: Total? = qb.from(Order::class.java)
    .where { it.status == 1 }
    .select { Total(Agg.count(), Agg.avg(it.price)) }
    .one()
    .orElse(null)
// HQL: select count(*), avg(e0.price) from Order e0 where e0.status = 1
```

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `total`:

```sql
-- H2, MariaDB, Oracle (same)
select count(*),avg(o1_0.price)
from orders o1_0
where o1_0.status=1
```

このクエリは常に 1 行を返します。

<a id="null-in-aggregates-rule-j4"></a>
### 集約の中の NULL（ルール J4）

| 関数 | 行がない、またはすべての値が NULL |
|---|---|
| `sum`、`avg`、`min`、`max` | `NULL` |
| `count` | `0` |

これは普通の SQL の動作です。これらの結果には nullable なパラメーター（`Double?`）を使ってください（[6.1](#61-example-entities-and-result-types) のヒントを参照）。

---

<a id="65-group-by"></a>
## 6.5 グループ化（group by）

グループのキーは `groupBy` で書きます。lambdaQL はキーを推測しません。
`groupBy` はグループ化したクエリを返します。`having { }`（任意）を呼び、次に `select { }` を呼びます。

<a id="one-key"></a>
### キーが 1 つ

```kotlin
val stats: List<Stat> = qb.from(Order::class.java)
    .groupBy { it.customer?.id }
    .select { Stat(it.customer?.id, Agg.count(), Agg.sum(it.price)) }
    .toList()
// HQL: select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id
```

キーが `@Id` で終わるので、join はありません。顧客のない注文は、キーが NULL の 1 つのグループになります。
`having` を使った例は [6.6](#66-having) にあります。

<a id="many-keys"></a>
### キーが複数

`listOf(...)` でキーのリストを返すか、`Pair`（`a to b`）や `Triple` を使います。

```kotlin
val counts: List<KeyCount> = qb.from(Order::class.java)
    .where { it.customer != null }
    .groupBy { listOf(it.customer?.id, it.status) }
    .select { KeyCount(it.customer?.id, it.status, Agg.count()) }
    .toList()
// HQL: select e0.customer.id, e0.status, count(*) from Order e0 where e0.customer is not null
//      group by e0.customer.id, e0.status

.groupBy { it.customer?.id to it.status }                     // Pair: 結果は同じ
.groupBy { Triple(it.customer?.id, it.status, it.active) }    // キーが 3 つ
```

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `group-many`:

```sql
-- H2, MariaDB, Oracle (same)
select o1_0.customer_id,o1_0.status,count(*)
from orders o1_0
where o1_0.customer_id is not null
group by o1_0.customer_id,o1_0.status
```

キーは書いた順に使われます。
`groupBy` の中の `listOf(...)` は、ラムダの中で作ったリストが許される **唯一の** 場所です。

キーのリストとして使えないもの:

| 形 | 理由 |
|---|---|
| `arrayOf(a, b)` | 型が混ざるとコンパイルできない |
| `setOf(a, b)` | set は重複を取り除き、順序がない |

<a id="every-non-aggregate-value-must-be-a-key-rule-j3"></a>
### 集約でない値はすべてキーでなければならない（ルール J3）

クエリに集約があるとき、`select` と `having` の中で集約 **でない** 値は、すべて `groupBy` のキーでなければなりません。これは SQL のルールです。lambdaQL は解析時にこれを確認します。

```kotlin
// エラー: it.status はグループのキーではない
qb.from(Order::class.java)
    .groupBy { it.customer?.id }
    .select { KeyCount(it.customer?.id, it.status, Agg.count()) }
// → LambdaAnalysisException
```

同じチェックは Java でテストしています（リージョン `rules`、[6.10](#610-rules-j1-to-j8) を参照）。

---

## 6.6 having

`having` はグループに対する条件です。`where` と同じラムダのルールを使い（[3. 条件](03-where.md)）、`Agg` を使えます。
`having` は何回でも呼び出せます。条件は `AND` でつながります。

```kotlin
val big: List<Stat> = qb.from(Order::class.java)
    .groupBy { it.customer?.id }
    .having { Agg.sum(it.price) > 200 }
    .select { Stat(it.customer?.id, Agg.count(), Agg.sum(it.price)) }
    .toList()
// HQL: select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id
//      having sum(e0.price) > 200.0
```

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `group-by`:

```sql
-- H2, MariaDB, Oracle (same)
select o1_0.customer_id,count(*),sum(o1_0.price)
from orders o1_0
group by o1_0.customer_id
having sum(o1_0.price)>200.0
```

`Agg.sum` は Java のメソッドなので、その結果はプラットフォーム型です。`Agg.sum(it.price) > 200` は `!!` なしでコンパイルできます。`!!` を付けても、lambdaQL はそれを無視します（決定 D23）。

---

<a id="67-distinct-paging-and-results"></a>
## 6.7 distinct、ページング、結果

`select { }` は `ValueQuery` を返します。次の関数があります。

| 関数 | 動作 |
|---|---|
| `distinct()` | `select distinct` |
| `limit(n)`、`offset(n)` | ページング |
| `toList()` | クエリを実行する。値を順序どおりに返す |
| `toSet()` | `select distinct` でクエリを実行する。順序どおりの `SequencedSet` |
| `first()` | 最初の値を Java の `Optional` で返す |
| `one()` | ただ 1 つの値を Java の `Optional` で返す。2 行以上なら `NonUniqueResultException` |
| `toHql()` | クエリを実行せずに HQL とパラメーターを返す |

```kotlin
val q = qb.from(Order::class.java)
    .orderBy { o, by -> by.desc(o.price) }
    .select { it.price }
    .distinct()
// HQL: select distinct e0.price from Order e0 order by e0.price desc nulls last
val prices = q.toSet()      // 常に select distinct
```

**データベースごとの SQL（Java のテスト実行から）** — Java のテストの `price` は `double` なので、その HQL には `nulls` の部分がありません。`expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `distinct`:

```sql
-- H2, MariaDB, Oracle (same)
select distinct o1_0.price
from orders o1_0
order by o1_0.price desc
```

- `distinct()` を使う場合、ソートキーはすべて選択した値でなければなりません（ルール J7）。そうでなければ、lambdaQL は解析時に `LambdaAnalysisException` を投げます。
- 値のクエリの `toSet()` は常に `select distinct` を使います。ここでは値の重複は想定内なので、件数のチェックはありません（エンティティの `toSet()` とは違います。[2. はじめよう](02-getting-started.md#toset) を参照）。

**ページング。** `limit` と `offset` は [4. ソートとページング](04-order-paging.md) と同じように動きます。
ページングでは、lambdaQL は最後のソートキーとして `id` を追加するので、ページが重なりません（ルール S9）。
グループ化したクエリと `distinct` のクエリには追加しません。`id` がその select リストにないからです。

```kotlin
val page = qb.from(Order::class.java)
    .orderBy { o, by -> by.desc(o.price) }
    .select { it.price }
    .limit(2).offset(1)
    .toList()
// HQL: select e0.price from Order e0 order by e0.price desc nulls last, e0.id asc
```

**データベースごとの SQL（Java のテスト実行から）** — Java のテストの `price` は `double` なので、その HQL には `nulls` の部分がありません。`expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `value-page`:

```sql
-- H2, Oracle
select o1_0.price
from orders o1_0
order by o1_0.price desc,o1_0.id
offset ? rows
fetch first ? rows only

-- MariaDB
select o1_0.price
from orders o1_0
order by o1_0.price desc,o1_0.id
limit ?,?
```

---

<a id="68-derived-table-subquery-in-from"></a>
## 6.8 派生テーブル（FROM の中のサブクエリ）

> [!NOTE]
> **設計済み、まだ実装されていません**（マイルストーン M8-2）。以下の HQL は **予定の** 出力です。

`qb.from` にラムダを渡します。ラムダはサブクエリのファクトリー（`sub`）を受け取り、内側のクエリを作ります。
内側の `select` が行の型を決めます。外側のクエリでは、その行をエンティティのように使います。

```kotlin
data class CustomerTotal(val customerId: Long?, val cnt: Long, val total: Double?)

val rows: List<CustomerTotal> = qb.from { sub ->
        sub.from(Order::class.java)
            .groupBy { it.customer?.id }
            .select { CustomerTotal(it.customer?.id, Agg.count(), Agg.sum(it.price)) }
    }
    .where { r -> r.total!! > 100 }
    .toList()
```

HQL（select のエイリアス `customerId`、`cnt`、`total` がカラム名になります。ルール J5）:

```sql
select r.customerId, r.cnt, r.total
from (select o.customer.id as customerId, count(*) as cnt, sum(o.price) as total
      from Order o group by o.customer.id) r
where r.total > 100
```

**予定の SQL ブロックについて。** [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java) はこの節と 6.9 の予定の HQL を Hibernate に直接渡し、H2 2.3、MariaDB 10.11、Oracle 23.26（Hibernate 6.6.5）での SQL を記録します。lambdaQL はまだこれらのクエリを作りません。Hibernate の SQL が変わるとテストは失敗します。

**データベースごとの SQL（予定）** — 予定の HQL を Hibernate が変換したもので、`PlannedSqlTest` が記録しました。lambdaQL はまだこのクエリを作りません。`expected-sql/` の [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)、例 `06-derived`:

```sql
-- H2
select r1_0.customerId,r1_0.cnt,r1_0.total
from (select o1_0.customer_id,count(*),sum(o1_0.price) from orders o1_0 group by o1_0.customer_id) r1_0(customerId,cnt,total)
where r1_0.total>100

-- MariaDB, Oracle
select r1_0.customerId,r1_0.cnt,r1_0.total
from (select o1_0.customer_id customerId,count(*) cnt,sum(o1_0.price) total from orders o1_0 group by o1_0.customer_id) r1_0
where r1_0.total>100
```

H2 はカラムのリストをエイリアスに付けます（`r1_0(customerId,cnt,total)`）。MariaDB と Oracle はサブクエリの中にカラムのエイリアスを書きます。

ルール:

- 行の型は **Kotlin の data class**（または Java の record）でなければなりません（ルール J5）。
  lambdaQL は data class の `componentN` の順序からカラムのエイリアスを取ります。
- 普通のクラスはここでは使えません。lambdaQL はそのコンストラクターのパラメーター名を知ることができません。
- 外側のラムダでは、プロパティで行を読みます（`r.total`）。

---

<a id="69-cte-with"></a>
## 6.9 CTE（`with`）

> [!NOTE]
> **設計済み、まだ実装されていません**（マイルストーン M8-2）。以下の HQL は **予定の** 出力です。

`qb.with { ... }` でハンドルを作ります。そして、そのハンドルを `qb.from(...)` で使います。
同じハンドルを複数回使えます。

```kotlin
val t = qb.with { sub ->
    sub.from(Order::class.java)
        .groupBy { it.customer?.id }
        .select { CustomerTotal(it.customer?.id, Agg.count(), Agg.sum(it.price)) }
}

val rows = qb.from(t).where { r -> r.total!! > 100 }.toList()
```

HQL（select のエイリアス `customerId`、`cnt`、`total` がカラム名になります。ルール J5）:

```sql
with t as (select o.customer.id as customerId, count(*) as cnt, sum(o.price) as total
           from Order o group by o.customer.id)
select x.customerId, x.cnt, x.total from t x where x.total > 100
```

**データベースごとの SQL（予定）** — 予定の HQL を Hibernate が変換したもので、`PlannedSqlTest` が記録しました。lambdaQL はまだこのクエリを作りません。`expected-sql/` の [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)、例 `06-cte`:

```sql
-- H2
select x1_0.customerId,x1_0.cnt,x1_0.total
from (select o1_0.customer_id,count(*),sum(o1_0.price) from orders o1_0 group by o1_0.customer_id) x1_0(customerId,cnt,total)
where x1_0.total>100

-- MariaDB, Oracle
with t (customerId,cnt,total) as (select o1_0.customer_id,count(*),sum(o1_0.price) from orders o1_0 group by o1_0.customer_id)
select x1_0.customerId,x1_0.cnt,x1_0.total
from t x1_0
where x1_0.total>100
```

MariaDB と Oracle は本物の `with` を実行します。カラムのリストは Hibernate 自身が書きます: `with t (customerId,cnt,total) as (...)`。H2 は CTE を派生テーブルに変えます。

注意:

- lambdaQL は外側のクエリで CTE に必ずエイリアスを付けます（`from t x`）。HQL ではそれが必要です（ルール J6）。
- CTE のカラム名は select のエイリアス（`as customerId`）で付けます。HQL は CTE 名の後のカラムリストを受け付けません。カラムリストは Hibernate が SQL に追加します（ルール J6）。
- MariaDB と Oracle は本物の `with` を実行します。H2 では、Hibernate がそれを派生テーブルに展開します。結果は同じです（確認済み）。
- 行の型のルールは派生テーブルと同じで、data class または record です（J5）。

---

<a id="610-rules-j1-to-j8"></a>
## 6.10 ルール J1 から J8

| # | ルール | 状況 |
|---|---|---|
| J1 | `select` のラムダは **1 つの値の式** または **1 つのコンストラクター呼び出し** を返す。コンストラクターの各引数は値の式（ルールは [3. 条件](03-where.md)）。`select` だけで使う関連のパスは `left join` のエイリアスになるので、行は失われない。同じパスを `where` が使うと、`select` は `where` の結合を使う（R24）。 | 実装済み（Java のラムダでテスト済み） |
| J2 | コンストラクターの場合、lambdaQL はカラムだけを選択し、自分でコンストラクターを呼び出す。`select new` は書かない。`Pair`/`Triple` も同じように作る。 | 実装済み（Java のラムダでテスト済み） |
| J3 | 集約がある場合、`select` と `having`（と `insert ... select` の値）の中の集約でない値はすべて `groupBy` のキーでなければならない。そうでなければ、解析時にエラー。 | 実装済み（Java のラムダでテスト済み）。現在のエラーは `LambdaAnalysisException`。`TranslationException` は設計済み（[11](../11-errors-and-languages.md)） |
| J4 | 行がない、またはすべての値が NULL のとき、`sum`/`avg`/`min`/`max` は NULL を返す。`count` は 0 を返す。コンストラクターのパラメーターは nullable にする。non-null のパラメーターに NULL が入ると `IllegalStateException` を投げる。 | 実装済み（Java のラムダでテスト済み） |
| J5 | 派生テーブルまたは CTE の行の型: **Kotlin の data class または Java の record のみ**。普通のクラスは単純な `select` でだけ使える。 | 設計済み（M8-2） |
| J6 | `qb.with { }` で作った CTE は、select のエイリアスでカラム名を付ける: `with t as (select o.customer.id as customerId, ...) select x.customerId ... from t x`。HQL は名前の後のカラムリストを受け付けない。Hibernate は `with t (col list) as (...)` を `SyntaxException` で拒否する。カラムリストは Hibernate 自身が SQL に書く（MariaDB と Oracle で `with t (customerId,cnt,total) as (...)`）。H2 は CTE を派生テーブルに変える。外側のクエリでは必ずエイリアスを使う（`from t x`）。 | 設計済み（M8-2） |
| J7 | `distinct()` は `select distinct` を出力する。ソートと一緒に使う場合、ソートキーは select リストに含まれていなければならない（DB のルール）。lambdaQL は解析時にこれを確認する。 | 実装済み（Java のラムダでテスト済み） |
| J8 | 選択は、ソート（[4. ソートとページング](04-order-paging.md)）、ページング、サブクエリ（[5. サブクエリ](05-subquery.md)）と一緒に使える。集約によるソート（`by.desc(s.total)`）は v0.2 の候補。 | 実装済み（集約によるソートを除く） |

[`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java) の Java のテストのリージョン `rules` が、J3、`where` の中の `Agg`、J7、J4 を確認します。[このページの Java 版](../java/06-select-group.md#610-rules-j1-to-j8) を参照してください。

---

<a id="611-not-in-v01"></a>
## 6.11 v0.1 にないもの

| 項目 | 状況 |
|---|---|
| 集約の結果や select のエイリアスによるソート（たとえば `orderBy { s, by -> by.desc(s.total) }`）と、グループ化したクエリの `orderBy` | v0.2 の候補 |
| 派生テーブルと CTE（6.8、6.9） | 設計済み、まだ実装されていない（マイルストーン M8-2） |
| ユーザー定義の集約関数とウィンドウ関数 | v0.2 の候補 |
| 結合に対する射影 `select { o, c -> OrderView(o.id, c.name, o.price) }` | 予定。結合の構文はまだ確定していない |

現在、この章のエラー（間違ったグループのキー、`where` の中の `Agg`、`distinct` で選択していないソートキー）は `LambdaAnalysisException` です。`[LQL-xxxx]` コードと `LambdaQLException` のサブクラスは設計済みです。[11. エラーと言語](../11-errors-and-languages.md) を参照してください。

---

[Kotlin トラック](README.md) | 前: [5. サブクエリ](05-subquery.md) | 次: [7. 更新、挿入、削除](07-dml.md) | [このページの Java 版](../java/06-select-group.md)
