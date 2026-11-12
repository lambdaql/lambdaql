<a id="6-select-aggregates-and-group-by-kotlin"></a>
# 6. 選択、集計、グループ化（Kotlin）

> [!NOTE]
> **状況: 設計済み、まだ実装されていません。**
> この章の内容はすべて決定済みの設計です（決定 D27、マイルストーン M9）。
> Kotlin の構文はプロトタイプでコンパイルして確認し（kotlinc 2.3.21）、HQL の形は H2、MariaDB、Oracle で実行しました。
> 現在の lambdaQL では **動きません**。今動くのは解析コアと `where` + `toList()` だけです
> （H2、MariaDB、Oracle でテスト済み）。全体の状況は [トラックの README](README.md) を参照してください。

[Kotlin トラック](README.md) | [マニュアルのホーム](../README.md) | 前: [5. サブクエリ](05-subquery.md) | 次: [7. 更新、挿入、削除](07-dml.md) | [このページの Java 版](../java/06-select-group.md)

共通ページ: [1. 概要](../01-overview.md) · [11. エラーと言語](../11-errors-and-languages.md)

この章では、クエリが **何を** 返すかを選ぶ方法を説明します。

- 行ごとに 1 つの値（`select { it.price }`）
- Kotlin の data class、`Pair`、`Triple`
- `groupBy` と `having` を使った集約（`count`、`sum`、`avg`、...）
- FROM 句の中のサブクエリ（派生テーブル）と CTE（`with`）

<a id="contents"></a>
## 目次

- [6.1 例で使うエンティティと結果の型](#61-example-entities-and-result-types)
- [6.2 1 つの値を選択する](#62-select-one-value)
- [6.3 data class、Pair、Triple を選択する](#63-select-a-data-class-pair-or-triple)
- [6.4 集約関数: `Agg`](#64-aggregate-functions-agg)
- [6.5 グループ化（group by）](#65-group-by)
- [6.6 having](#66-having)
- [6.7 派生テーブル（FROM の中のサブクエリ）](#67-derived-table-subquery-in-from)
- [6.8 CTE（`with`）](#68-cte-with)
- [6.9 ルール J1 から J8](#69-rules-j1-to-j8)
- [6.10 v0.1 にないもの](#610-not-in-v01)

---

<a id="61-example-entities-and-result-types"></a>
## 6.1 例で使うエンティティと結果の型

例では次のエンティティを使います（[2. はじめよう](02-getting-started.md#example-entities) を参照）。

| エンティティ | ここで使うプロパティ |
|---|---|
| `Order` | `id`、`price`、`status`、`active`、`customer`（many-to-one） |
| `Customer` | `id`、`name`、`grade` |
| `Item` | `order`、`price`、`qty` |

結果の型は次のとおりです。

```kotlin
data class OrderView(val id: Long?, val customerName: String?, val price: Double?)
data class Stat(val customerId: Long?, val cnt: Long, val total: Double?)
data class Total(val cnt: Long, val avgPrice: Double?)
```

> [!TIP]
> data class では nullable な型（`Double?`）を使ってください。
> カラムや集約は NULL になることがあります。NULL が non-null の
> パラメーターに入ると、コンストラクターは `NullPointerException` を投げます（ルール J4）。
> `count` は決して NULL を返さないので、`cnt: Long` で問題ありません。

---

<a id="62-select-one-value"></a>
## 6.2 1 つの値を選択する

`select` は 1 つのラムダを受け取ります。ラムダは 1 つの値を返します。結果はその値のリストです。

```kotlin
val prices: List<Double?> = qb.from(Order::class.java)
    .where { it.status == 1 }
    .select { it.price }
    .toList()
```

HQL:

```sql
select o.price from Order o where o.status = 1
```

**SQL ブロックの読み方。** `…` は省略した select リスト（エンティティのすべてのカラム）で、`?` はバインドパラメーターです。[`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java) はこのページの HQL を Hibernate に直接渡し、H2 2.3、MariaDB 10.11、Oracle 23.26（Hibernate 6.6.5）での SQL を記録します。lambdaQL はまだこれらのクエリを作りません。Hibernate の SQL が変わるとテストは失敗します。

**データベースごとの SQL（予定）** — 予定の HQL を Hibernate が変換したもので、`PlannedSqlTest` が記録しました。lambdaQL はまだこのクエリを作りません。`expected-sql/` の [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)、例 `06-select-value`:

```sql
-- H2, MariaDB, Oracle (same)
select o1_0.price
from orders o1_0
where o1_0.status=1
```

関連のパスは、`where` と同じくカラムです（ルール R24）。`select` では lambdaQL は `left join` を追加するので、customer のない注文も失われません（`where` では同じパスがデフォルトで内部結合になります）。

```kotlin
.select { it.customer?.name }
// → select c1.name from Order o left join o.customer c1
```

**データベースごとの SQL（予定）** — 予定の HQL を Hibernate が変換したもので、`PlannedSqlTest` が記録しました。lambdaQL はまだこのクエリを作りません。`expected-sql/` の [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)、例 `06-select-path`:

```sql
-- H2, MariaDB, Oracle (same)
select c1_0.name
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
```

`it.customer!!.name` も同じ HQL になります。
パスが関連エンティティの `@Id` で終わる場合、join はありません。
`it.customer?.id` は外部キーのカラム `customer_id` を読みます。

ほかの値の形:

```kotlin
.select { it.description ?: "" }              // coalesce(o.description, '')
.select { it.customer?.name ?: "none" }       // coalesce(c1.name, 'none') + left join
.select { (it.price ?: 0.0) * 2 }             // coalesce(o.price, 0.0) * 2
```

boolean の elvis（`select { it.flag ?: false }`）と値の `if`/`when` は拒否されます（制限 KC21、KC6）。

---

<a id="63-select-a-data-class-pair-or-triple"></a>
## 6.3 data class、Pair、Triple を選択する

ラムダの中でコンストラクターを呼び出します。コンストラクターの各引数は値の式です。

```kotlin
// data class
val list = qb.from(Order::class.java)
    .select { o -> OrderView(o.id, o.customer?.name, o.price) }
    .toList()

// Pair（Triple も使える）
val pairs = qb.from(Order::class.java)
    .select { o -> o.customer?.name to o.price }
    .toList()
```

HQL（lambdaQL はカラムだけを選択します。ルール J2）:

```sql
select o.id, c1.name, o.price from Order o left join o.customer c1
```

**データベースごとの SQL（予定）** — 予定の HQL を Hibernate が変換したもので、`PlannedSqlTest` が記録しました。lambdaQL はまだこのクエリを作りません。`expected-sql/` の [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)、例 `06-dto`:

```sql
-- H2, MariaDB, Oracle (same)
select o1_0.id,c1_0.name,o1_0.price
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
```

lambdaQL は `select new com.acme.OrderView(...)` を書き **ません**。
カラムを選択し、Hibernate に `OrderView` の型付きの結果を求めます。
これにより、長いクラス名やネストしたクラス名の問題を避けられます。
`Pair` と `Triple` の場合、lambdaQL はカラムを読んで自分でオブジェクトを作ります。

返せるもの:

| 結果の型 | 単純な `select` | 派生テーブルや CTE の行の型 |
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
| `sum(x)` | `x` の型（`Double?` なら `Double?`） |
| `avg(x)` | `Double`（SQL では NULL になることがあるので、`Double?` として扱ってください） |
| `min(x)`、`max(x)` | `x` の型（任意の `Comparable`、nullable でもよい） |

import は任意です。関数を 1 つだけ import することもできます。

```kotlin
import org.lambdaql.Agg.sum

.having { sum(it.price)!! > 1000 }
```

これは `xs.sum()` や `xs.count()` のような Kotlin 標準ライブラリの拡張関数とぶつかりません（コンパイラーで確認済み）。

重要な点:

- 戻り値は **仮の値** にすぎません。ラムダは実行されないので、その値が使われることはありません。
- lambdaQL のラムダの **外** で `Agg.sum(...)` を呼び出すと、`IllegalStateException` を投げます。
- `where` では `Agg` を使えません。集約に対する条件には `having` を使ってください。`where` で `Agg` を使うと解析時のエラーになります。

<a id="aggregate-over-all-rows-no-group-by"></a>
### すべての行に対する集約（group by なし）

```kotlin
val t: List<Total> = qb.from(Order::class.java)
    .where { it.status == 1 }
    .select { Total(Agg.count(), Agg.avg(it.price)) }
    .toList()
// → select count(*), avg(o.price) from Order o where o.status = 1
```

**データベースごとの SQL（予定）** — 予定の HQL を Hibernate が変換したもので、`PlannedSqlTest` が記録しました。lambdaQL はまだこのクエリを作りません。`expected-sql/` の [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)、例 `06-aggregate`:

```sql
-- H2, MariaDB, Oracle (same)
select count(*),avg(o1_0.price)
from orders o1_0
where o1_0.status=1
```

<a id="null-in-aggregates-rule-j4"></a>
### 集約の中の NULL（ルール J4）

| 関数 | 行がない、またはすべての値が NULL のとき |
|---|---|
| `sum`、`avg`、`min`、`max` | `NULL` |
| `count` | `0` |

これは SQL の普通の動作です。

---

<a id="65-group-by"></a>
## 6.5 グループ化（group by）

グループのキーは `groupBy` で書きます。lambdaQL はキーを推測しません。

<a id="one-key"></a>
### キーが 1 つ

```kotlin
val stats = qb.from(Order::class.java)
    .groupBy { it.customer?.id }
    .select { Stat(it.customer?.id, Agg.count(), Agg.sum(it.price)) }
    .toList()
```

HQL（キーが `@Id` で終わるので、join はありません）:

```sql
select o.customer.id, count(*), sum(o.price) from Order o group by o.customer.id
```

**データベースごとの SQL（予定）** — 予定の HQL を Hibernate が変換したもので、`PlannedSqlTest` が記録しました。lambdaQL はまだこのクエリを作りません。`expected-sql/` の [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)、例 `06-group-by`:

```sql
-- H2, MariaDB, Oracle (same)
select o1_0.customer_id,count(*),sum(o1_0.price)
from orders o1_0
group by o1_0.customer_id
```

<a id="many-keys"></a>
### キーが複数

`listOf(...)` でキーのリストを返すか、`Pair`（`a to b`）や `Triple` を使います。

```kotlin
.groupBy { listOf(it.customer?.id, it.status) }
// → group by o.customer.id, o.status

.groupBy { it.customer?.id to it.status }                     // Pair: 結果は同じ
.groupBy { Triple(it.customer?.id, it.status, it.active) }    // キーが 3 つ
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
data class Stat2(val customerId: Long?, val status: Int?, val cnt: Long)

// エラー: it.status はグループのキーではない
qb.from(Order::class.java)
    .groupBy { it.customer?.id }
    .select { Stat2(it.customer?.id, it.status, Agg.count()) }
// → TranslationException
```

---

## 6.6 having

`having` はグループに対する条件です。`where` と同じラムダのルールを使い（[3. 条件](03-where.md)）、`Agg` を使えます。

```kotlin
val big = qb.from(Order::class.java)
    .groupBy { it.customer?.id }
    .having { Agg.sum(it.price)!! > 1000 }
    .select { Stat(it.customer?.id, Agg.count(), Agg.sum(it.price)) }
    .toList()
```

HQL:

```sql
select o.customer.id, count(*), sum(o.price)
from Order o
group by o.customer.id
having sum(o.price) > 1000
```

**データベースごとの SQL（予定）** — 予定の HQL を Hibernate が変換したもので、`PlannedSqlTest` が記録しました。lambdaQL はまだこのクエリを作りません。`expected-sql/` の [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)、例 `06-group-having`:

```sql
-- H2, MariaDB, Oracle (same)
select o1_0.customer_id,count(*),sum(o1_0.price)
from orders o1_0
group by o1_0.customer_id
having sum(o1_0.price)>1000
```

`!!` はコードをコンパイルできるようにするためだけのものです（`Double?` の `sum` は `Double?`）。lambdaQL はこれを無視します（決定 D23）。

---

<a id="67-derived-table-subquery-in-from"></a>
## 6.7 派生テーブル（FROM の中のサブクエリ）

`qb.from` にラムダを渡します。ラムダはサブクエリのファクトリー（`sub`）を受け取り、内側のクエリを組み立てます。
内側の `select` が行の型を決めます。外側のクエリでは、その行をエンティティのように使います。

```kotlin
val rows: List<Stat> = qb.from { sub ->
        sub.from(Order::class.java)
            .groupBy { it.customer?.id }
            .select { Stat(it.customer?.id, Agg.count(), Agg.sum(it.price)) }
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
  lambdaQL はカラムのエイリアスを data class の `componentN` の順序から取ります。
- ここでは普通のクラスは使えません。lambdaQL はそのコンストラクターのパラメーター名を知ることができないからです。
- 外側のラムダでは、行をそのプロパティで読みます（`r.total`）。

---

<a id="68-cte-with"></a>
## 6.8 CTE（`with`）

`qb.with { ... }` でハンドルを作ります。そして、そのハンドルを `qb.from(...)` で使います。
同じハンドルを 2 回以上使えます。

```kotlin
val t = qb.with { sub ->
    sub.from(Order::class.java)
        .groupBy { it.customer?.id }
        .select { Stat(it.customer?.id, Agg.count(), Agg.sum(it.price)) }
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

補足:

- lambdaQL は外側のクエリで、CTE に必ずエイリアスを付けます（`from t x`）。HQL ではエイリアスが必要です（ルール J6）。
- CTE のカラム名は select のエイリアス（`as customerId`）で付けます。HQL は CTE 名の後のカラムリストを受け付けません。カラムリストは Hibernate が SQL に追加します（ルール J6）。
- MariaDB と Oracle は本物の `with` を実行します。H2 では、Hibernate がそれを派生テーブルに展開します。結果は同じです（確認済み）。
- 行の型のルールは派生テーブルと同じです: data class または record（J5）。

---

<a id="69-rules-j1-to-j8"></a>
## 6.9 ルール J1 から J8

| # | ルール |
|---|---|
| J1 | `select` のラムダは **1 つの値の式** または **1 つのコンストラクター呼び出し** を返す。コンストラクターの各引数は値の式（ルールは [3. 条件](03-where.md)）。`select` だけで使う関連のパスは `left join` のエイリアスになるので、行は失われない。同じパスを `where` が使うと、`select` は `where` の結合を使う（R24）。 |
| J2 | コンストラクターの場合、lambdaQL はカラムだけを選択し、あなたのクラスで Hibernate の型付きクエリを使う。`select new` は書かない。`Pair`/`Triple` は lambdaQL がカラムから作る。 |
| J3 | 集約があるとき、`select` と `having` の中の集約でない値は、すべて `groupBy` のキーでなければならない。そうでなければ、解析時に `TranslationException`。 |
| J4 | `sum`/`avg`/`min`/`max` は、行がないとき、またはすべての値が NULL のとき NULL を返す。`count` は 0 を返す。nullable なコンストラクターのパラメーターを使うこと。 |
| J5 | 派生テーブルや CTE の行の型: **Kotlin の data class または Java の record のみ**。普通のクラスは単純な `select` でのみ使える。 |
| J6 | `qb.with { }` で作った CTE は、select のエイリアスでカラム名を付ける: `with t as (select o.customer.id as customerId, ...) select x.customerId ... from t x`。HQL は名前の後のカラムリストを受け付けない。Hibernate は `with t (col list) as (...)` を `SyntaxException` で拒否する。カラムリストは Hibernate 自身が SQL に書く（MariaDB と Oracle で `with t (customerId,cnt,total) as (...)`）。H2 は CTE を派生テーブルに変える。外側のクエリでは必ずエイリアスを使う（`from t x`）。 |
| J7 | `distinct()` は `select distinct` を出力する。ソートと一緒に使うとき、ソートキーは select のリストに含まれていなければならない（DB のルール）。lambdaQL は解析時にこれを確認する。 |
| J8 | 選択は、ソート（[4. ソートとページング](04-order-paging.md)）、ページング、サブクエリ（[5. サブクエリ](05-subquery.md)）と一緒に使える。 |

---

<a id="610-not-in-v01"></a>
## 6.10 v0.1 にないもの

| 項目 | 状況 |
|---|---|
| 集約の結果や select のエイリアスによるソート。たとえば `orderBy { s, by -> by.desc(s.total) }` | v0.2 の候補 |
| ユーザー定義の集約関数とウィンドウ関数 | v0.2 の候補 |
| join に対する射影、`select { o, c -> OrderView(o.id, c.name, o.price) }` | 予定。join の構文はまだ確定していない |
| `toList()` 以外の結果関数 | 予定 |

この章のエラー（間違ったグループキー、`where` の中の `Agg`、派生テーブルの行としての普通のクラス）は、`[LQL-xxxx]` のコードを持つ `LambdaQLException` のサブクラスです。[11. エラーと言語](../11-errors-and-languages.md) を参照してください。

---

[Kotlin トラック](README.md) | 前: [5. サブクエリ](05-subquery.md) | 次: [7. 更新、挿入、削除](07-dml.md) | [このページの Java 版](../java/06-select-group.md)
