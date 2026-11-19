<a id="6-select-aggregates-and-group-by-kotlin"></a>
# 6. 選択、集計、グループ化（Kotlin）

> [!IMPORTANT]
> このページは韓国語版のマニュアルより古い内容です。最新のルールは[韓国語のページ](../../ko/kotlin/06-select-group.md)を参照してください。

[Kotlin トラック](README.md) | [マニュアルのホーム](../README.md) | 前: [5. サブクエリ](05-subquery.md) | 次: [7. 更新、挿入、削除](07-dml.md) | [このページの Java 版](../java/06-select-group.md)

共通ページ: [1. 概要](../01-overview.md) · [11. エラーと言語](../11-errors-and-languages.md)

---

この章では、クエリが **何を** 返すかを選ぶ方法を説明します。

- 行ごとに 1 つの値（`select { it.price }`）
- Kotlin の data class、`Pair`、`Triple`
- `groupBy` と `having` を使った集約（`count`、`sum`、`avg`、...）
- 値のクエリの `distinct`、ページング、結果の関数
- 他のエンティティとの明示的な結合（`join`、`leftJoin`、`crossJoin`）
- FROM 句の中のサブクエリ（派生テーブル）と CTE（`with`）

<a id="contents"></a>
## 目次

- [6.1 例で使うエンティティと結果の型](#61-example-entities-and-result-types)
- [6.2 1 つの値の選択](#62-select-one-value)
- [6.3 data class、Pair、Triple の選択](#63-select-a-data-class-pair-or-triple)
- [6.4 集約関数: `Agg`](#64-aggregate-functions-agg)
- [6.5 グループ化（group by）](#65-group-by)
- [6.6 having](#66-having)
- [6.7 distinct、ページング、結果](#67-distinct-paging-and-results)
- [6.8 明示的な結合](#68-explicit-joins)
- [6.9 派生テーブル（FROM の中のサブクエリ）](#69-derived-table-subquery-in-from)
- [6.10 CTE（`with`）](#610-cte-with)
- [6.11 ルール J1〜J8](#611-rules-j1-to-j8)
- [6.12 v0.1 に含まれないもの](#612-not-in-v01)

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
> このようなパラメーターに NULL が入ると、lambdaQL は `IllegalStateException` を投げます。プリミティブの場合、メッセージにはその引数と、ラッパー型を使うようにという案内が入っています。
> `count` は決して NULL を返さないので、`count: Long` で問題ありません。

**(テスト済み)** と書かれた例は [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt) から取ったものです。このテストは Java のテスト用エンティティ（`price` はプリミティブの `double`、`status` は `short`、`Customer.id` は `int`）と、次の結果の型を使います。

```kotlin
/** A row of one order (Kotlin data class). */
data class OrderRow(val id: Long?, val customerName: String?, val price: Double)

/** A row of one customer group. Aggregates can be NULL, so the types are nullable (J4). */
data class CustomerStat(val customerId: Int?, val count: Long, val total: Double?)
```

Java で書いたほかのクエリのテストが [`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java) にあります（H2、MariaDB、Oracle）。それらの例は [このページの Java 版](../java/06-select-group.md) を参照してください。

---

<a id="62-select-one-value"></a>
## 6.2 1 つの値の選択

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

**データベースごとの SQL（Java のテスト実行の結果）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `value`:

```sql
-- H2, MariaDB, Oracle (same)
select o1_0.price
from orders o1_0
where o1_0.status=1
order by o1_0.price
```

関連のパスは、`where` と同じくカラムです（ルール R24）。`select` では lambdaQL が `left join` を追加するので、顧客のない注文も失われません（`where` では同じパスがデフォルトで inner join になります）。
`optional = false` でマッピングした必須の関連は行を失わないので、`select` でも inner join です（[4.5](04-order-paging.md#45-what-a-sort-key-can-be) を参照）。

```kotlin
.select { it.customer?.name }
// HQL: select j1.name from Order e0 left join e0.customer j1
```

`it.customer!!.name` も同じ HQL になります。[6.3](#63-select-a-data-class-pair-or-triple) の data class の例で、この `left join` を確認できます。
パスが関連エンティティの `@Id` で終わる場合、結合はありません。
`it.customer?.id` は外部キーのカラム `customer_id` を読みます。

ほかの値の形（Kotlin 固有の形、設計済み。[9. Kotlin の注意点](09-kotlin-notes.md) を参照）:

```kotlin
.select { it.description ?: "" }              // coalesce(e0.description, '')
.select { it.customer?.name ?: "none" }       // coalesce(j1.name, 'none') + left join
.select { (it.price ?: 0.0) * 2 }             // coalesce(e0.price, 0.0) * 2
```

boolean の elvis（`select { it.flag ?: false }`）と値の `if`/`when` は拒否されます（制限 KC21、KC6）。

---

<a id="63-select-a-data-class-pair-or-triple"></a>
## 6.3 data class、Pair、Triple の選択

ラムダの中でコンストラクターを呼び出してください。コンストラクターの各引数は値の式です。

```kotlin
// data class
val list: List<OrderView> = qb.from(Order::class.java)
    .select { OrderView(it.id, it.customer?.name, it.price) }
    .toList()
// HQL: select e0.id, j1.name, e0.price from Order e0 left join e0.customer j1

// Pair（Triple も可）
val pairs: List<Pair<String?, Double?>> = qb.from(Order::class.java)
    .select { it.customer?.name to it.price }
    .toList()
// HQL: select j1.name, e0.price from Order e0 left join e0.customer j1
```

**(テスト済み)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)、リージョン `data-class`:

```kotlin
val q = qb.from(Order::class.java).select { OrderRow(it.id, it.customer?.name, it.price) }
assertEquals("select e0.id, j1.name, e0.price from Order e0 left join e0.customer j1", q.toHql().hql())
```

**(テスト済み)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)、リージョン `pair`:

```kotlin
val q = qb.from(Order::class.java).where { it.status.toInt() == 1 }.select { it.customer?.name to it.price }
assertEquals("select j1.name, e0.price from Order e0 left join e0.customer j1 where e0.status = 1", q.toHql().hql())
```

テスト用エンティティの `status` は `short` なので、テストは `it.status.toInt() == 1` で比較します。lambdaQL はこの変換を無視します。

**データベースごとの SQL（Java のテスト実行の結果。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `record`（data class の例）:

```sql
-- H2, MariaDB, Oracle (same)
select o1_0.id,c1_0.name,o1_0.price
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
```

lambdaQL は `select new com.acme.OrderView(...)` を書き **ません**（ルール J2）。
カラムを選択し、コンストラクターは lambdaQL が直接呼び出します。data class、`Pair`、`Triple` のどれでも同じです。
これにより、長いクラス名やネストしたクラス名による問題を避けられます。
lambdaQL は各値をコンストラクターのパラメーターの型に変換します（たとえば Hibernate の `Integer` を `Long?` のパラメーターに）。

返せるもの:

| 結果の型 | 単純な `select` | 派生テーブルや CTE の行の型（[6.9](#69-derived-table-subquery-in-from)） |
|---|---|---|
| 1 つの値（`it.price`） | 可 | – |
| Kotlin の data class | 可 | 可 |
| コンストラクターを持つ通常のクラス | 可 | **不可**（J5） |
| `Pair` / `Triple` | 可 | 不可 |
| Java の record（Java モジュールから） | 可 | 可 |

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

これは `xs.sum()` や `xs.count()` のような Kotlin 標準ライブラリの拡張関数と衝突しません（コンパイラーで確認済み）。

重要な点:

- 戻り値は **仮の値** にすぎません。ラムダは実行されないので、その値が使われることはありません。
- lambdaQL のラムダの **外** で `Agg.sum(...)` を呼び出すと、`IllegalStateException` を投げます。
- `Agg` は `select`、`having`、`insert ... select` の値（[7.5](07-5-insert.md)）で使えます。
- `where` では `Agg` を使えません。集約に対する条件には `having` を使ってください。`where` で `Agg` を使うと、解析時にエラーになります。

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

**データベースごとの SQL（Java のテスト実行の結果。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `total`:

```sql
-- H2, MariaDB, Oracle (same)
select count(*),avg(o1_0.price)
from orders o1_0
where o1_0.status=1
```

このクエリは常に 1 行を返します。

<a id="null-in-aggregates-rule-j4"></a>
### 集約での NULL（ルール J4）

| 関数 | 行がない、またはすべての値が NULL のとき |
|---|---|
| `sum`、`avg`、`min`、`max` | `NULL` |
| `count` | `0` |

これは通常の SQL の動作です。このような結果には nullable なパラメーター（`Double?`）を使ってください（[6.1](#61-example-entities-and-result-types) のヒントを参照）。

---

<a id="65-group-by"></a>
## 6.5 グループ化（group by）

グループのキーは `groupBy` で書きます。lambdaQL はキーを推測しません。
`groupBy` はグループ化したクエリを返します。`having { }`（任意）を呼び、次に `select { }` を呼んでください。

<a id="one-key"></a>
### キーが 1 つ

```kotlin
val stats: List<Stat> = qb.from(Order::class.java)
    .groupBy { it.customer?.id }
    .select { Stat(it.customer?.id, Agg.count(), Agg.sum(it.price)) }
    .toList()
// HQL: select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id
```

キーが `@Id` で終わるので、結合はありません。顧客のない注文は、キーが NULL の 1 つのグループになります。
`having` を使った例は [6.6](#66-having) にあります。

<a id="many-keys"></a>
### キーが複数

`listOf(...)` でキーのリストを返すか、`Pair`（`a to b`）や `Triple` を使ってください。

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

**(テスト済み)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)、リージョン `group-many`（結果は `Triple`）:

```kotlin
val q = qb.from(Order::class.java)
    .where { it.customer != null }
    .groupBy { listOf(it.customer?.id, it.status) }
    .select { Triple(it.customer?.id, it.status, Agg.count()) }
assertEquals("select e0.customer.id, e0.status, count(*) from Order e0 where e0.customer is not null"
    + " group by e0.customer.id, e0.status", q.toHql().hql())
```

**データベースごとの SQL（Java のテスト実行の結果。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `group-many`:

```sql
-- H2, MariaDB, Oracle (same)
select o1_0.customer_id,o1_0.status,count(*)
from orders o1_0
where o1_0.customer_id is not null
group by o1_0.customer_id,o1_0.status
```

キーは書いた順序で使われます。
`groupBy` の中の `listOf(...)` は、ラムダの中で作ったリストが許される **唯一の** 場所です。

キーのリストとして使えないもの:

| 形 | 理由 |
|---|---|
| `arrayOf(a, b)` | 型が混ざるとコンパイルできない |
| `setOf(a, b)` | セットは重複を取り除き、順序を持たない |

<a id="every-non-aggregate-value-must-be-a-key-rule-j3"></a>
### 集約でない値はすべてキー（ルール J3）

クエリに集約がある場合、`select` と `having` の中の集約 **でない** 値は、すべて `groupBy` のキーでなければなりません。これは SQL のルールです。lambdaQL は解析時にこれをチェックします。

```kotlin
// エラー: it.status はグループのキーではない
qb.from(Order::class.java)
    .groupBy { it.customer?.id }
    .select { KeyCount(it.customer?.id, it.status, Agg.count()) }
// → TranslationException(LQL-3035)
```

同じチェックを Java でテストしています（リージョン `rules`、[6.11](#611-rules-j1-to-j8) を参照）。

---

## 6.6 having

`having` はグループに対する条件です。`where` と同じラムダのルール（[3. 条件（`where`）](03-where.md)）を使い、`Agg` も使えます。
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

**(テスト済み)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)、リージョン `group-by`:

```kotlin
val q = qb.from(Order::class.java)
    .groupBy { it.customer?.id }
    .having { Agg.sum(it.price) > 200 }
    .select { CustomerStat(it.customer?.id, Agg.count(), Agg.sum(it.price)) }
assertEquals("select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id"
    + " having sum(e0.price) > 200.0", q.toHql().hql())
```

**データベースごとの SQL（Java のテスト実行の結果。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `group-by`:

```sql
-- H2, MariaDB, Oracle (same)
select o1_0.customer_id,count(*),sum(o1_0.price)
from orders o1_0
group by o1_0.customer_id
having sum(o1_0.price)>200.0
```

`Agg.sum` は Java のメソッドなので、結果はプラットフォーム型です。そのため `Agg.sum(it.price) > 200` は `!!` なしでコンパイルできます。`!!` を付けても、lambdaQL はこれを無視します（決定 D23）。

---

<a id="67-distinct-paging-and-results"></a>
## 6.7 distinct、ページング、結果

`select { }` は `ValueQuery` を返します。`ValueQuery` の関数は次のとおりです。

| 関数 | 動作 |
|---|---|
| `distinct()` | `select distinct` |
| `limit(n)`、`offset(n)` | ページング |
| `toList()` | クエリを実行し、値を順序どおりに返す |
| `toSet()` | `select distinct` でクエリを実行し、順序のある `SequencedSet` を返す |
| `first()` | 最初の値（Java の `Optional`） |
| `one()` | ただ 1 つの値（Java の `Optional`）。2 行以上なら `NonUniqueResultException` |
| `toHql()` | クエリを実行せずに HQL とパラメーターを返す |

```kotlin
val q = qb.from(Order::class.java)
    .orderBy { o, by -> by.desc(o.price) }
    .select { it.price }
    .distinct()
// HQL: select distinct e0.price from Order e0 order by e0.price desc nulls last
val prices = q.toSet()      // always select distinct
```

**データベースごとの SQL（Java のテスト実行の結果）** — Java のテストの `price` は `double` なので、その HQL には `nulls` の部分がありません。`expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `distinct`:

```sql
-- H2, MariaDB, Oracle (same)
select distinct o1_0.price
from orders o1_0
order by o1_0.price desc
```

- `distinct()` を使う場合、ソートキーはすべて選択した値でなければなりません（ルール J7）。そうでなければ、lambdaQL は解析時に `TranslationException`（`LQL-3046`）を投げます。
- 値のクエリの `toSet()` は常に `select distinct` を使います。ここでは値の重複は通常のことなので、件数のチェックはしません（エンティティの `toSet()` とは違います。[2. はじめよう](02-getting-started.md#toset) を参照）。
- `@Lob` カラムを `distinct` や `groupBy` のキーに使うと、Oracle は拒否します（ORA-22848、ORA-22849）。H2 と MariaDB は実行します。lambdaQL はクエリを変えず、`LQL-5010` を `DEBUG` レベルで記録します（[4.5](04-order-paging.md#45-what-a-sort-key-can-be) を参照）。

**ページング。** `limit` と `offset` は [4. ソートとページング](04-order-paging.md) と同じように動きます。
ページングでは、lambdaQL が最後のソートキーとして `id` を追加するので、ページが重なりません（ルール S9）。ソートキーが一意で NULL にならないカラムなら追加しません（[4.8](04-order-paging.md#tie-breaker-automatic-id) を参照）。
`first()` も 1 行だけを読むので、同じ補助ソートを追加します。ソートキーの値が同じ行が複数ある場合、毎回 `id` が最も小さい行の値を返します。
グループ化したクエリと `distinct` のクエリには追加しません。これらのクエリの select リストには `id` がないからです。

```kotlin
val page = qb.from(Order::class.java)
    .orderBy { o, by -> by.desc(o.price) }
    .select { it.price }
    .limit(2).offset(1)
    .toList()
// HQL: select e0.price from Order e0 order by e0.price desc nulls last, e0.id asc
```

**データベースごとの SQL（Java のテスト実行の結果）** — Java のテストの `price` は `double` なので、その HQL には `nulls` の部分がありません。`expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `value-page`:

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

<a id="68-explicit-joins"></a>
## 6.8 明示的な結合

関連のパス（`it.customer?.name`）は、to-one の関連を自動で結合します（[3.10](03-where.md#310-association-paths) を参照）。
別のエンティティを自分で決めた条件で、または条件なしで結合するには、`join`、`leftJoin`、`crossJoin` を使ってください（決定 D44、D45）。

| 関数 | HQL | 行 |
|---|---|---|
| `join(Customer::class.java) { o, c -> ... }` | `join Customer e1 on ...` | inner join: 一致する行だけ |
| `leftJoin(Customer::class.java) { o, c -> ... }` | `left join Customer e1 on ...` | 一致しない主エンティティの行も残る |
| `crossJoin(Customer::class.java)` | `cross join Customer e1` | すべての行の組み合わせ。条件は `where` に書く |

結合のあと、どのラムダも結合した順にすべてのエンティティを受け取ります。
`where { o, c -> ... }`、`orderBy { o, c, by -> ... }`（`by` は最後、ルール S12）、`select { o, c -> ... }` です。
結合したエンティティのエイリアスは `e1`、`e2` です。

結合は [`JoinQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java) で **Java** のラムダを使ってテストしています（H2、MariaDB、Oracle）。結合結果の `distinct()` と CTE の結合は、Kotlin のラムダでもテストしています（[`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)、リージョン `join-distinct`、`join-cte`）。
そのほかの下の Kotlin のコードは、テスト済みの Java のクエリを Kotlin の構文に書き換えたもので、HQL は Java のテストの結果です。テストのコードは [このページの Java 版](../java/06-select-group.md#68-explicit-joins) を参照してください。

```kotlin
val q = qb.from(Order::class.java)
    .join(Customer::class.java) { o, c -> o.customer?.id == c.id }
    .where { o, c -> c.grade != null && c.grade!! >= 5 }
// HQL: select e0 from Order e0 join Customer e1 on e0.customer.id = e1.id
//      where e1.grade is not null and e1.grade >= 5
```

**結果は主エンティティです。** `select` がなければ、`toList()` は `List<Order>` を返します。
結合したエンティティは、条件、ソート、`select` にだけ使います。
結合したエンティティの値を読むには、`select { o, c -> ... }` を使ってください（下を参照）。

<a id="left-join-and-null"></a>
### left join と NULL

```kotlin
// 顧客のない注文には c がない: c == null でそのような行を見つける
val q = qb.from(Order::class.java)
    .leftJoin(Customer::class.java) { o, c -> o.customer?.id == c.id }
    .where { o, c -> c == null || c.name == "Kim" }
// HQL: select e0 from Order e0 left join Customer e1 on e0.customer.id = e1.id
//      where e1.id is null or e1.name = 'Kim'
```

- `c == null` は「一致なし」を意味します。lambdaQL はこれを `e1.id is null`（結合したエンティティの `@Id`）と書きます。
  ラムダのパラメーター `c` は non-null の型なので、Kotlin コンパイラーが `c == null` は常に false だという警告を出すことがあります。lambdaQL はこの式を「一致なし」と読みます。
- `leftJoin` のあと、結合したエンティティのカラムに対する条件は **SQL の意味** に従います。一致がないとカラムは NULL なので条件は真にならず、その行は結果から除かれます。
  一致しない行を残すには、例のように `c == null || ...` と書いてください。

<a id="sort-and-select"></a>
### ソートと select

```kotlin
val q = qb.from(Order::class.java)
    .leftJoin(Customer::class.java) { o, c -> o.customer?.id == c.id }
    .orderBy { o, c, by -> by.asc(c.name).asc(o.id) }
// HQL: select e0 from Order e0 left join Customer e1 on e0.customer.id = e1.id
//      order by e1.name asc nulls first, e0.id asc

data class Row(val orderId: Long?, val customerName: String?)

val rows = qb.from(Order::class.java)
    .leftJoin(Customer::class.java) { o, c -> o.customer?.id == c.id }
    .orderBy { o, c, by -> by.asc(o.id) }
    .select { o, c -> Row(o.id, c.name) }
// HQL: select e0.id, e1.name from Order e0 left join Customer e1 on e0.customer.id = e1.id
//      order by e0.id asc
```

顧客のない注文では、`customerName` は `null` です。
`select` は `ValueQuery` を返すので、`distinct()`、`limit`、`offset` と [6.7](#67-distinct-paging-and-results) の結果の関数が使えます。

<a id="three-entities-and-repeated-rows"></a>
### 3 つのエンティティと繰り返される行

結合できるエンティティは 3 つまでです。3 つ目の結合のラムダは 3 つすべてを受け取ります: `{ o, c, i -> ... }`。

```kotlin
// 数量が 10 を超える品目を持つ、顧客のいる注文。条件に一致する品目ごとに注文が 1 回ずつ繰り返される
val q = qb.from(Order::class.java)
    .join(Customer::class.java) { o, c -> o.customer?.id == c.id }
    .join(Item::class.java) { o, c, i -> i.order?.id == o.id }
    .where { o, c, i -> i.qty > 10 }
// HQL: select e0 from Order e0 join Customer e1 on e0.customer.id = e1.id
//      join Item e2 on e2.order.id = e0.id where e2.qty > 10
```

> [!WARNING]
> 結合の条件に一致する行が複数あると、**主エンティティの行が繰り返されます**。一致ごとに 1 回です。`toList()` と `count()` は繰り返された行もすべて数えます（Java でテスト済み）。
> 注文ごとに 1 回だけ必要な場合は、結合の代わりに `exists()` のサブクエリ（[5. サブクエリ](05-subquery.md)）を使うか、結合クエリに `distinct()` を適用してください（[結合結果の `distinct()`](#distinct-on-a-join) を参照）。

<a id="cross-join"></a>
### cross join

`crossJoin` には結合の条件がありません。関数名で「すべての行の組み合わせが欲しい」という意図を示します。条件は `where` に書いてください。

```kotlin
// すべての行の組み合わせ。条件は where に書く
val q = qb.from(Order::class.java)
    .crossJoin(Customer::class.java)
    .where { o, c -> c.grade != null && o.price!! > 100 }
// HQL: select e0 from Order e0 cross join Customer e1 where e1.grade is not null and e0.price > 100.0
```

lambdaQL には `from(Order::class.java, Customer::class.java)`（カンマ結合）はありません。条件を書き忘れると誤ってすべての組み合わせを得ることになり、どのエンティティが結果なのかもはっきりしないからです（決定 D45）。そのような検索には `crossJoin` を使ってください。

<a id="paging"></a>
### ページング

ページングでは、lambdaQL は **すべての** エンティティの id を最後のソートキーとして追加します。そのため、ページが重なりません（ルール S9）。

```kotlin
// ページングでは、すべてのエンティティの id をソートに追加するので、ページが重ならない（S9）
val pairs = qb.from(Order::class.java)
    .crossJoin(Customer::class.java)
    .orderBy { o, c, by -> by.desc(o.price) }
    .select { o, c -> Row(o.id, c.name) }
    .limit(5)
// HQL: select e0.id, e1.name from Order e0 cross join Customer e1 order by e0.price desc, e0.id asc, e1.id asc
```

この HQL は、`price` がプリミティブの `double` である Java のテストの結果です。このマニュアルの nullable な `price: Double?` では、ソートキーに `nulls last` が付きます（[4.4](04-order-paging.md#44-null-ordering-is-the-same-on-every-database) を参照）。

<a id="distinct-on-a-join"></a>
### 結合結果の `distinct()`

結合クエリの `distinct()` は、結合のために繰り返される主エンティティの行を 1 回だけ返します（決定 D51）。HQL は `select distinct e0` で、`count()` は `count(distinct e0)` です。

- ソートキーは主エンティティ自身のカラムでなければなりません（J7、`LQL-3046`）。結合したエンティティのカラムや関連のパスでソートするとエラーです。
- ページングでは、主エンティティの id だけを同順位のキーとして追加します。結合したエンティティの id は追加しません。
- `distinct()` の後に `select(...)` を書くと、値を `select distinct` で取得します。
- まず `exists` のサブクエリ（[5. サブクエリ](05-subquery.md)）を検討してください。繰り返しなしで同じ行を返し、多くの場合より速く動きます。

**(テスト済み)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)、リージョン `join-distinct`:

```kotlin
// an order with two matching items would come twice; distinct() returns it once
val q = qb.from(Order::class.java)
    .join(Item::class.java) { o, i -> i.order?.id == o.id }
    .where { _, i -> i.qty > 0 }
    .distinct()
assertEquals("select distinct e0 from Order e0 join Item e1 on e1.order.id = e0.id where e1.qty > 0", q.toHql().hql())
```

<a id="join-a-derived-table-or-cte"></a>
### 派生テーブルと CTE の結合

[6.9](#69-derived-table-subquery-in-from) の派生テーブルと [6.10](#610-cte-with) の CTE は、エンティティと同じように結合できます（決定 D51）。
形は `join(cte) { c, x -> ... }`、`leftJoin(cte) { ... }`、`join(qb.from(...)...select { Row(...) }) { ... }`、`leftJoin(...) { ... }` で、2 つ目や 3 つ目のルートとして使えます。

- HQL は `with t as (...) ... join t e1 on ...` または `join (select ...) e1 on ...` です。
- 行の型は Kotlin の data class（または Java の record）です（J5）。プロパティ（`x.total`）がカラムを読みます。
- 同じ `Cte` オブジェクトを 1 つの文で 2 回読むと、定義は 1 回だけ書き、2 つの結合が同じ名前を読みます: `join t e1 on ... left join t e2 on ...`。異なる `Cte` オブジェクトは `with t as (...), t2 as (...)` のように別々の名前を受け取ります（3 つの DB で実測）。
- 派生した行には id がないので、`x == null` はエラー（`LQL-3067`）です。`x.customerId == null` のようにカラムを比較してください。
- Hibernate 6.6 は、エンティティの `select distinct`、派生テーブルと CTE の inner/left join、サブクエリの中のエンティティの結合を受け付けます（3 つの DB で実測）。

**(テスト済み)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)、リージョン `join-cte`:

```kotlin
// the total of each customer, joined to the customer row
val t = qb.with(qb.from(Order::class.java)
    .where { it.customer != null }
    .groupBy { it.customer?.id }
    .select { CustTotal(it.customer?.id, Agg.sum(it.price)) })
val rows = qb.from(Customer::class.java)
    .join(t) { c, x -> x.customerId == c.id }
    .where { _, x -> x.total!! > 100 }
    .select { c, x -> c.name to x.total }
assertEquals("with t as (select d0.customer.id as customerId, sum(d0.price) as total from Order d0"
        + " where d0.customer is not null group by d0.customer.id) select e0.name, e1.total from Customer e0"
        + " join t e1 on e1.customerId is not null and e1.customerId = e0.id where e1.total > 100.0", rows.toHql().hql())
```

`x.customerId` が nullable な `Int?` の場合、`x.customerId == c.id` は `e1.customerId is not null and e1.customerId = e0.id` になります（上のリージョンを参照）。名前のない派生テーブルの結合は、Java のテスト（[`JoinQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java)、リージョン `join-derived`）が確認します。

同じ CTE を 2 回読む形は、Java のテスト（[`JoinQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java)、リージョン `cte-reuse`）が確認します。

<a id="what-a-join-query-has"></a>
### 結合クエリの関数

| 呼び出しの後 | 関数 |
|---|---|
| `join`、`leftJoin`、`crossJoin` | `where`、`join` / `leftJoin` / `crossJoin`（エンティティ 3 つまで、派生テーブルと CTE を含む）、`distinct`、`orderBy`、`select`、`limit`、`toList`、`toSet`、`first`、`count`、`exists`、`toHql` |
| `orderBy { o, c, by -> ... }` | `select`、`limit`、`offset`、`toList`、`first`、そのほかソートしたクエリの関数（[4. ソートとページング](04-order-paging.md)） |

サブクエリの中の結合は [5. サブクエリ](05-subquery.md#joins-inside-a-subquery) を参照してください（D51）。
v0.1 にはまだないもの: insert の中の結合、4 つ以上のエンティティ、`SortSpec` の結合の形。
update と delete の中の結合は追加しないことにしました（D51）。関連のパス（U9）か `sub`（D49）を使ってください。

---

<a id="69-derived-table-subquery-in-from"></a>
## 6.9 派生テーブル（FROM の中のサブクエリ）

`select` で値のクエリ（data class、[6.3](#63-select-a-data-class-pair-or-triple) を参照）を作った後、それを `qb.from(...)` に渡してください。
その行が外側のクエリのテーブルになります（決定 D46）。
結果は `DerivedQuery<R>` で、`R` は行の型です。

派生テーブルは [`DerivedQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DerivedQueryTest.java) で **Java** のラムダを使ってテストしています（H2、MariaDB、Oracle）。Kotlin のテストはまだありません。
下の Kotlin のコードは、テスト済みの Java のクエリを Kotlin の構文に書き換えたものです。テストのコードは [このページの Java 版](../java/06-select-group.md#69-derived-table-subquery-in-from) を参照してください。

```kotlin
data class CustomerTotal(val customerId: Long?, val cnt: Long, val total: Double?)

// グループ化したクエリの行が外側のクエリのテーブルになる。r.total はカラム "total" を読む
val q = qb.from(qb.from(Order::class.java)
        .where { it.customer != null }
        .groupBy { it.customer?.id }
        .select { CustomerTotal(it.customer?.id, Agg.count(), Agg.sum(it.price)) })
    .where { r -> r.total!! > 100 }
    .orderBy { r, by -> by.desc(r.total) }
// HQL: select e0.customerId, e0.cnt, e0.total from (select d0.customer.id as customerId, count(*) as cnt,
//      sum(d0.price) as total from Order d0 where d0.customer is not null group by d0.customer.id) e0
//      where e0.total > 100.0 order by e0.total desc nulls last
```

- 内側のクエリのエイリアスは `d0`（結合は `dj1`、...）、外側のクエリのエイリアスは `e0` です。
- data class の各プロパティが、プライマリコンストラクターの順でカラム名になります: `as customerId`、`as cnt`、`as total`（ルール J5）。
- 外側のラムダでは、プロパティでカラムを読みます。`r.total` は `e0.total` です。
- 外側のクエリには `where`、`orderBy`、`limit`、`offset`、`select`、`toList`、`first`、`count`、`toHql` があります。

行の値を 1 つだけ選択することもできます（`.select { r -> r.customerId }`）。内側と外側のキャプチャ値は順番にバインドします。
内側のクエリの `limit` と `offset` は内側のクエリに残ります（HQL の `limit`、`offset`）。そのため、上位の行をもう一度ソートできます。

```kotlin
data class Priced(val id: Long?, val price: Double?)

// 最も高い 3 つの注文のうち、最も安い注文
val cheapestOfTop = qb.from(qb.from(Order::class.java)
        .orderBy { o, by -> by.desc(o.price) }
        .limit(3)
        .select { Priced(it.id, it.price) })
    .orderBy { r, by -> by.asc(r.price) }
    .first()
```

**SQL ブロックの読み方。** [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java) は同じ形のより単純な HQL を Hibernate に直接渡し、H2 2.3、MariaDB 10.11、Oracle 23.26（Hibernate 6.6.5）で出力された SQL を記録します。Hibernate の SQL が変わるとテストは失敗します。このブロックで、データベースごとの派生テーブルの書き方を確認できます。

**データベースごとの SQL（この形の HQL に対する Hibernate の SQL）** — `expected-sql/` の [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)、例 `06-derived`:

```sql
-- HQL: select r.customerId, r.cnt, r.total from (select o.customer.id as customerId, count(*) as cnt, sum(o.price) as total from Order o group by o.customer.id) r where r.total > 100

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
  lambdaQL は data class のプロパティから、プライマリコンストラクターの順でカラム名を取ります。
- 通常のクラス、`Pair`、`Triple`、値 1 つは、ここでは使えません。lambdaQL がカラム名を決められないからです。この場合 `TranslationException`（`LQL-3064`）が発生します（Java では値 1 つでテスト済み）。

エンティティのクエリに派生テーブルを結合する形は、[派生テーブルと CTE の結合](#join-a-derived-table-or-cte) を参照してください（D51）。

v0.1 にはまだないもの: 派生テーブル同士の結合、update、delete、insert の中の派生テーブル。

エンティティの `@SQLRestriction` と `@SoftDelete` の条件は、エンティティの結合の `on` と派生テーブルの中にも Hibernate が付けます（MariaDB 10.11、Oracle 23.26 で実測）。lambdaQL はこの条件を別に処理しません。

---

<a id="610-cte-with"></a>
## 6.10 CTE（`with`）

`qb.with(valueQuery)` で `Cte<R>` 型のハンドルを作ってください。その後 `qb.from(handle)` で読みます。
内側のクエリは派生テーブル（6.9）と同じです。

```kotlin
val perCustomer = qb.from(Order::class.java)
    .where { it.customer != null }
    .groupBy { it.customer?.id }
    .select { CustomerTotal(it.customer?.id, Agg.count(), Agg.sum(it.price)) }

val t: Cte<CustomerTotal> = qb.with(perCustomer)
val rows = qb.from(t).where { r -> r.cnt >= 2 }.toList()
// HQL: with t as (select d0.customer.id as customerId, count(*) as cnt, sum(d0.price) as total
//      from Order d0 where d0.customer is not null group by d0.customer.id)
//      select e0.customerId, e0.cnt, e0.total from t e0 where e0.cnt >= 2L
```

**データベースごとの SQL（この形の HQL に対する Hibernate の SQL）** — `PlannedSqlTest` が記録しました。`expected-sql/` の [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)、例 `06-cte`:

```sql
-- HQL: with t as (select o.customer.id as customerId, count(*) as cnt, sum(o.price) as total from Order o group by o.customer.id) select x.customerId, x.cnt, x.total from t x where x.total > 100

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

- CTE の名前は `t` です。外側のクエリでは必ずエイリアス `e0` を付けます（`from t e0`）。HQL ではエイリアスが必要です（ルール J6）。
- CTE のカラム名は select のエイリアス（`as customerId`）で決めます。HQL は CTE 名の後のカラムリストを受け付けず、カラムリストは Hibernate が SQL に追加します（ルール J6）。
- MariaDB と Oracle は本物の `with` を実行します。H2 では、Hibernate がそれを派生テーブルに展開します。結果は同じです（3 つのデータベースで Java のラムダによりテスト済み）。
- 行の型のルールは派生テーブルと同じで、data class または record です（J5）。
- エンティティのクエリに CTE を結合する形は、[派生テーブルと CTE の結合](#join-a-derived-table-or-cte) を参照してください（D51）。
- 同じ CTE を 1 つのクエリで 2 回読めます。定義は 1 回だけ書きます（[派生テーブルと CTE の結合](#join-a-derived-table-or-cte) を参照）。
- v0.1 にはまだないもの: update、delete、insert の中の CTE。

---

<a id="611-rules-j1-to-j8"></a>
## 6.11 ルール J1〜J8

| # | ルール | 状況 |
|---|---|---|
| J1 | `select` のラムダは **1 つの値の式** または **1 つのコンストラクター呼び出し** を返す。コンストラクターの各引数は値の式（[3. 条件（`where`）](03-where.md) のルール）。`select` だけで使う関連のパスは `left join` のエイリアスになるので、行は失われない。必須の関連（`optional = false`）は inner join（A4）。同じパスを `where` で使うと、`select` は `where` の結合を使う（R24）。 | 実装済み（Java と Kotlin のラムダでテスト済み） |
| J2 | コンストラクターの場合、lambdaQL はカラムだけを選択し、コンストラクターを直接呼び出す。`select new` は書かない。`Pair`/`Triple` も同じ方法で作る。 | 実装済み（Java と Kotlin のラムダでテスト済み） |
| J3 | 集約がある場合、`select` と `having`（と `insert ... select` の値）の中の集約でない値はすべて `groupBy` のキーでなければならない。そうでなければ、解析時にエラー。 | 実装済み（Java のラムダでテスト済み）。エラーは `TranslationException`（`LQL-3035`、[11](../11-errors-and-languages.md) を参照） |
| J4 | 行がない、またはすべての値が NULL のとき、`sum`/`avg`/`min`/`max` は NULL を返す。`count` は 0 を返す。コンストラクターのパラメーターは nullable にする。non-null のパラメーターに NULL が入ると `IllegalStateException` が発生する。 | 実装済み（Java のラムダでテスト済み） |
| J5 | 派生テーブルまたは CTE の行の型: **Kotlin の data class または Java の record のみ**。通常のクラスは単純な `select` でだけ使える。そうでなければ `LQL-3064`。結合した派生テーブルと CTE の行にも適用する（D51）。派生した行は `null` と比較できない（`LQL-3067`）。 | 実装済み（Java のラムダでテスト済み） |
| J6 | `qb.with(...)` で作った CTE は、select のエイリアスでカラム名を付ける: `with t as (select d0.customer.id as customerId, ...) select e0.customerId ... from t e0`。HQL は名前の後のカラムリストを受け付けない。Hibernate は `with t (col list) as (...)` を `SyntaxException` で拒否する。カラムリストは Hibernate 自身が SQL に書く（MariaDB と Oracle で `with t (customerId,cnt,total) as (...)`）。H2 は CTE を派生テーブルに変える。外側のクエリでは必ずエイリアスを使う（`from t e0`）。 | 実装済み（Java のラムダでテスト済み） |
| J7 | `distinct()` は `select distinct` を出力する。ソートと一緒に使う場合、ソートキーは select リストに含まれていなければならない（DB のルール）。lambdaQL は解析時にこれをチェックする。結合結果の `distinct()` では、ソートキーは主エンティティ自身のカラムでなければならない（D51）。 | 実装済み（Java のラムダでテスト済み） |
| J8 | 選択は、ソート（[4. ソートとページング](04-order-paging.md)）、ページング、サブクエリ（[5. サブクエリ](05-subquery.md)）と一緒に動作する。集約によるソート（`by.desc(s.total)`）は v0.2 の候補。 | 実装済み。集約によるソートは除く |

[`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java) の Java のテストのリージョン `rules` が、J3、`where` の中の `Agg`、J7、J4 を確認します。[このページの Java 版](../java/06-select-group.md#611-rules-j1-to-j8) を参照してください。

---

<a id="612-not-in-v01"></a>
## 6.12 v0.1 に含まれないもの

| 項目 | 状況 |
|---|---|
| 集約の結果や select のエイリアスによるソート（たとえば `orderBy { s, by -> by.desc(s.total) }`）、グループ化したクエリの `orderBy` | v0.2 の候補 |
| ユーザー定義の集約関数とウィンドウ関数 | v0.2 の候補 |
| insert の中の結合、4 つ以上のエンティティ、サブクエリの中の 3 つ以上のエンティティ | 今後 |
| update、delete の中の結合 | 追加しない（D51）。関連のパス（U9）か `sub`（D49）を使う |
| DML の中の派生テーブルと CTE | 今後 |

この章のエラーは、コードを持つ `TranslationException` です。間違ったグループのキー（J3）は `LQL-3035`、`where` の中の `Agg` は `LQL-3005`、`distinct` で選択していないソートキー（J7）は `LQL-3046`、派生テーブルまたは CTE の行の型（J5）は `LQL-3064` です。[11. エラーと言語](../11-errors-and-languages.md) を参照してください。

---

[Kotlin トラック](README.md) | 前: [5. サブクエリ](05-subquery.md) | 次: [7. 更新、挿入、削除](07-dml.md) | [このページの Java 版](../java/06-select-group.md)
