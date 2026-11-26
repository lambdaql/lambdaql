<a id="6-select-aggregates-and-group-by-kotlin"></a>
# 6. 選択、集計、グループ化（Kotlin）

[Kotlin トラック](README.md) | [マニュアルのホーム](../README.md) | 前: [5. サブクエリ](05-subquery.md) | 次: [7. 更新、挿入、削除](07-dml.md) | [このページの Java 版](../java/06-select-group.md)

共通ページ: [1. 概要](../01-overview.md) · [11. エラーと言語](../11-errors-and-languages.md)

---

この章では、クエリが **何を** 返すかを選ぶ方法を説明します。

- 行ごとに 1 つの値（`select { it.price }`）
- Kotlin の data class、`Pair`、`Triple`
- `groupBy`、`having` と一緒に使う集約（`count`、`sum`、`avg`、...）
- 値のクエリの `distinct`、ページング、結果関数
- 他のエンティティとの明示的な結合（`join`、`leftJoin`、`crossJoin`）
- FROM 句のサブクエリ（派生テーブル）と CTE（`with`）

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
| `Order` | `id`、`price`、`status`、`active`、`customer`（many-to-one） |
| `Customer` | `id`、`name`、`grade` |
| `Item` | `order`、`price`、`qty` |

例では、Java で書いた例のエンティティ（`price` はプリミティブ型の `double`、`status` は `short`、`Customer.id` は `int`）と、次の結果の型を使います。

<!-- region: ManualKotlinSelectTest.kt#result-types -->
```kotlin
data class OrderView(val id: Long?, val customerName: String?, val price: Double?)
data class Stat(val customerId: Int?, val count: Long, val total: Double?)
data class KeyCount(val customerId: Int?, val status: Short?, val count: Long)
data class Total(val count: Long, val average: Double?)
```

Kotlin は型を厳密にチェックするので、コンストラクターのパラメーターの型はエンティティのプロパティの型に合わせます（`Customer.id` は `Int?`、`status` は `Short?`）。

> [!TIP]
> 結果には nullable な型（`Double?`、`String?`）を使ってください。
> カラムや `sum` のような集約は NULL になることがあります。non-null の `Double` や `Long` のパラメーターは JVM のプリミティブ型で、non-null の `String` のパラメーターも NULL を受け取りません。
> このようなパラメーターに NULL が入ると、lambdaQL は `IllegalStateException` を投げます。プリミティブ型の場合、メッセージにはその引数とラッパー型を使うようにという案内が含まれます。
> `count` は NULL を返さないので、`count: Long` は問題ありません。

コード例: [`ManualKotlinSelectTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSelectTest.kt)（`result-types`）。

---

<a id="62-select-one-value"></a>
## 6.2 1 つの値の選択

`select` はラムダ式を 1 つ受け取ります。ラムダ式は 1 つの値を返します。結果はその値の `ValueQuery` です。
`select` は `where` の後と `orderBy` の後で呼べます。ソート、`limit`、`offset` はそのまま残ります。

<!-- region: ManualKotlinSelectTest.kt#value -->
```kotlin
val q = qb.from(Order::class.java)
    .where { it.status.toInt() == 1 }
    .orderBy { o -> asc(o.price) }
    .select { it.price }
assertEquals("select e0.price from Order e0 where e0.status = 1 order by e0.price asc", q.toHql().hql())
val prices: List<Double> = q.toList()
```

`price` は NULL になれないので、ソートに `nulls` の部分はありません。NULL になりうるカラムなら `nulls first` が付きます（[4.4](04-order-paging.md#44-null-ordering-is-the-same-on-every-database) を参照）。

**データベースごとの SQL**

<!-- sql: value -->
```sql
-- H2, MariaDB, Oracle (same)
select o1_0.price
from orders o1_0
where o1_0.status=1
order by o1_0.price
```

関連のパスは、`where` と同じくカラムです。`select` では lambdaQL が `left join` を追加するので、顧客のない注文も抜けません（`where` では、顧客のない注文が条件に一致しえなければ、同じパスが inner join です）。
`optional = false` でマッピングした必須の関連は行を失わないので、`select` でも inner join です（[4.5](04-order-paging.md#45-what-a-sort-key-can-be) を参照）。

<!-- region: ManualKotlinSelectTest.kt#value-path -->
```kotlin
// a path in select is a left join: the order without a customer stays (its value is null)
val names = qb.from(Order::class.java).select { it.customer?.name }
assertEquals("select j1.name from Order e0 left join e0.customer j1", names.toHql().hql())
// !! gives the same query
assertEquals(names.toHql().hql(), qb.from(Order::class.java).select { it.customer!!.name }.toHql().hql())
// a path that ends with the @Id reads the foreign key column: no join
assertEquals("select e0.customer.id from Order e0", qb.from(Order::class.java).select { it.customer?.id }.toHql().hql())
```

`it.customer!!.name` も同じ HQL を作ります。[6.3](#63-select-a-data-class-pair-or-triple) の data class の例でも、この `left join` を確認できます。
パスが関連エンティティの `@Id` で終わると、結合はありません。`it.customer?.id` は外部キーのカラム `customer_id` を読みます。

**エルビス演算子。** boolean でないカラムの `?:` は `coalesce` になります。`?.` のパスの後の `?:`（`it.customer?.name ?: "none"`）は条件として読まれ、`LQL-2022` で拒否されます。

<!-- region: ManualKotlinSelectTest.kt#value-elvis -->
```kotlin
// ?: on a column that is not a boolean: coalesce
val q = qb.from(Order::class.java).select { it.description ?: "" }
assertEquals("select coalesce(e0.description, '') from Order e0", q.toHql().hql())
// ?: after a ?. path is rejected (LQL-2022)
val afterPath = assertThrows(LambdaQLException::class.java) {
    qb.from(Order::class.java).select { it.customer?.name ?: "none" }.toHql()
}
assertEquals("LQL-2022", afterPath.code())
```

コード例: [`ManualKotlinSelectTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSelectTest.kt)（`value`、`value-path`、`value-elvis`）。

---

<a id="63-select-a-data-class-pair-or-triple"></a>
## 6.3 data class、Pair、Triple の選択

ラムダ式の中でコンストラクターを呼んでください。コンストラクターの各引数は値の式です。

<!-- region: ManualKotlinSelectTest.kt#data-class -->
```kotlin
// only the columns are selected; lambdaQL calls the constructor
val views = qb.from(Order::class.java).select { OrderView(it.id, it.customer?.name, it.price) }
assertEquals("select e0.id, j1.name, e0.price from Order e0 left join e0.customer j1", views.toHql().hql())
// Pair (a to b) and Triple
val pairs = qb.from(Order::class.java).select { it.customer?.name to it.price }
assertEquals("select j1.name, e0.price from Order e0 left join e0.customer j1", pairs.toHql().hql())
val triples = qb.from(Order::class.java).select { Triple(it.id, it.status, it.isActive) }
assertEquals("select e0.id, e0.status, e0.active from Order e0", triples.toHql().hql())
```

**データベースごとの SQL**

<!-- sql: record -->
```sql
-- H2, MariaDB, Oracle (same)
select o1_0.id,c1_0.name,o1_0.price
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
```

lambdaQL は `select new com.acme.OrderView(...)` を書き **ません**。
カラムを選択し、コンストラクターは lambdaQL が直接呼びます。data class、`Pair`、`Triple` のどれも同じです。
そのため、長いクラス名や入れ子のクラス名で起きる問題を避けられます。上の例の `OrderView` も、別のクラスの中に入れ子になった data class です。

返せるもの:

| 結果の型 | 単純な `select` | 派生テーブルや CTE の行の型（[6.9](#69-derived-table-subquery-in-from)） |
|---|---|---|
| 1 つの値（`it.price`） | はい | – |
| Kotlin の data class | はい | はい |
| コンストラクターを持つ普通のクラス | はい | **いいえ** |
| `Pair` / `Triple` | はい | いいえ |
| Java の record（Java のモジュールで） | はい | はい |

コード例: [`ManualKotlinSelectTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSelectTest.kt)（`data-class`）。

---

<a id="64-aggregate-functions-agg"></a>
## 6.4 集約関数: `Agg`

集約関数は **`org.lambdaql.Agg` の static メソッド** です。
SQL の標準関数なので、lambdaQL が提供します。（自分で作った DB 関数は [8. プロシージャと DB 関数](08-procedures-functions.md) を参照してください。）

| 関数 | SQL |
|---|---|
| `Agg.count()` | `count(*)` |
| `Agg.count(x)` | `count(x)` |
| `Agg.countDistinct(x)` | `count(distinct x)` |
| `Agg.sum(x)` | `sum(x)` |
| `Agg.avg(x)` | `avg(x)` |
| `Agg.min(x)` | `min(x)` |
| `Agg.max(x)` | `max(x)` |

Kotlin から見える型:

| 関数 | 戻り値 |
|---|---|
| `count()`、`count(x)`、`countDistinct(x)` | `Long` |
| `sum(x)` | `x` の型（プラットフォーム型） |
| `avg(x)` | `Double`（SQL では NULL になりうるので、`Double?` として扱ってください） |
| `min(x)`、`max(x)` | `x` の型（すべての `Comparable`、nullable も問題ない） |

`Agg.sum` は、`xs.sum()` のような Kotlin 標準ライブラリの拡張と衝突しません。1 つのファイルで両方の `sum` を一緒に使えます。

重要な点:

- 戻り値は **プレースホルダー** にすぎません。lambdaQL はラムダ式を呼び出さずにバイトコードの opcode を解析するので、その値は使われません。
- lambdaQL のラムダ式の **外で** `Agg.sum(...)` を呼ぶと、`IllegalStateException` を投げます。
- `Agg` は `select`、`having`、`insert ... select` の値（[7.5](07-5-insert.md)）で使えます。
- `where` では `Agg` を使えません。集約に対する条件には `having` を使ってください。`where` で `Agg` を使うと、解析時にエラーになります。

<a id="aggregate-over-all-rows-no-group-by"></a>
### すべての行に対する集約（group by なし）

<!-- region: ManualKotlinSelectTest.kt#total -->
```kotlin
// aggregates without groupBy: always one row
val q = qb.from(Order::class.java)
    .where { it.status.toInt() == 1 }
    .select { Total(Agg.count(), Agg.avg(it.price)) }
assertEquals("select count(*), avg(e0.price) from Order e0 where e0.status = 1", q.toHql().hql())
val t: Total? = q.one().orElse(null)
```

**データベースごとの SQL**

<!-- sql: total -->
```sql
-- H2, MariaDB, Oracle (same)
select count(*),avg(o1_0.price)
from orders o1_0
where o1_0.status=1
```

このクエリは常に 1 行を返します。

<a id="null-in-aggregates-rule-j4"></a>
### 集約での NULL（ルール J4）

| 関数 | 行がないか、すべての値が NULL のとき |
|---|---|
| `sum`、`avg`、`min`、`max` | `NULL` |
| `count` | `0` |

これは正常な SQL の動作です。このような結果には nullable なパラメーター（`Double?`）を使ってください（[6.1](#61-example-entities-and-result-types) のヒントを参照）。

コード例: [`ManualKotlinSelectTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSelectTest.kt)（`total`）。

---

<a id="65-group-by"></a>
## 6.5 グループ化（group by）

グループのキーは `groupBy` で書きます。`select` の値からグループのキーを自動で決めることはしません。
`groupBy` はグループのクエリを返します。`having { }`（任意）を呼んでから `select { }` を呼んでください。

<a id="one-key"></a>
### キーが 1 つ

<!-- region: ManualKotlinSelectTest.kt#group-one -->
```kotlin
// the key ends with the @Id: no join. The orders without a customer are one group with the key NULL
val q = qb.from(Order::class.java)
    .groupBy { it.customer?.id }
    .select { Stat(it.customer?.id, Agg.count(), Agg.sum(it.price)) }
assertEquals("select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id", q.toHql().hql())
```

キーが `@Id` で終わるので、結合はありません。顧客のない注文は、キーが NULL のグループ 1 つになります。
`having` を使う例は [6.6](#66-having) にあります。

<a id="many-keys"></a>
### キーが複数

`listOf(...)` でキーのリストを返すか、`Pair`（`a to b`）や `Triple` を使ってください。

<!-- region: ManualKotlinSelectTest.kt#group-many -->
```kotlin
val q = qb.from(Order::class.java)
    .where { it.customer != null }
    .groupBy { listOf(it.customer?.id, it.status) }
    .select { KeyCount(it.customer?.id, it.status, Agg.count()) }
assertEquals("select e0.customer.id, e0.status, count(*) from Order e0 where e0.customer is not null"
    + " group by e0.customer.id, e0.status", q.toHql().hql())
// a Pair (a to b) gives the same keys; a Triple gives three
val pair = qb.from(Order::class.java)
    .where { it.customer != null }
    .groupBy { it.customer?.id to it.status }
    .select { KeyCount(it.customer?.id, it.status, Agg.count()) }
assertEquals(q.toHql().hql(), pair.toHql().hql())
val three = qb.from(Order::class.java)
    .groupBy { Triple(it.customer?.id, it.status, it.isActive) }
    .select { Triple(it.customer?.id, it.status, Agg.count()) }
assertEquals("select e0.customer.id, e0.status, count(*) from Order e0 group by e0.customer.id, e0.status, e0.active",
    three.toHql().hql())
```

**データベースごとの SQL（1 つ目のクエリ）**

<!-- sql: group-many -->
```sql
-- H2, MariaDB, Oracle (same)
select o1_0.customer_id,o1_0.status,count(*)
from orders o1_0
where o1_0.customer_id is not null
group by o1_0.customer_id,o1_0.status
```

キーは書いた順に使われます。
`groupBy` の `listOf(...)` は、ラムダ式の中で作ったリストが許される **唯一の** 場所です。

キーのリストとして許されないもの:

| 書き方 | 理由 |
|---|---|
| `setOf(a, b)` | 集合は重複をなくし、順序がない |

<a id="every-non-aggregate-value-must-be-a-key-rule-j3"></a>
### 集約でない値はすべてキー（ルール J3）

クエリに集約があると、`select` と `having` で集約 **でない** 値はすべて `groupBy` のキーでなければなりません。これは SQL のルールです。lambdaQL は解析時にこれをチェックします。

<!-- region: ManualKotlinSelectTest.kt#group-key-error -->
```kotlin
// status is not a group key
val e = assertThrows(LambdaQLException::class.java) {
    qb.from(Order::class.java)
        .groupBy { it.customer?.id }
        .select { KeyCount(it.customer?.id, it.status, Agg.count()) }
}
assertEquals("LQL-3035", e.code())
```

エラーは `select` を呼ぶときに発生します。その他のルールのチェックは [6.11](#611-rules-j1-to-j8) を参照してください。

コード例: [`ManualKotlinSelectTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSelectTest.kt)（`group-one`、`group-many`、`group-key-error`）。

---

<a id="66-having"></a>
## 6.6 having

`having` はグループに対する条件です。`where` と同じラムダ式のルール（[3. 条件（`where`）](03-where.md)）を使い、`Agg` を使えます。
`having` は何回でも呼べます。条件は `AND` でつながります。

<!-- region: ManualKotlinSelectTest.kt#having -->
```kotlin
// sum is imported (import org.lambdaql.Agg.sum); having can be called more than once (AND)
val q = qb.from(Order::class.java)
    .groupBy { it.customer?.id }
    .having { sum(it.price) > 200 }
    .having { Agg.count() >= 2 }
    .select { Stat(it.customer?.id, Agg.count(), sum(it.price)) }
assertEquals("select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id"
    + " having sum(e0.price) > 200.0 and count(*) >= 2L", q.toHql().hql())
```

`Agg.sum` は Java のメソッドなので、結果はプラットフォーム型です。そのため、`sum(it.price) > 200` は `!!` なしでコンパイルできます。`!!` を付けても、lambdaQL はそれを無視します。

コード例: [`ManualKotlinSelectTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSelectTest.kt)（`having`）。

---

<a id="67-distinct-paging-and-results"></a>
## 6.7 distinct、ページング、結果

`select { }` は `ValueQuery` を返します。`ValueQuery` の関数は次のとおりです。

| 関数 | 動作 |
|---|---|
| `distinct()` | `select distinct` |
| `limit(n)`、`offset(n)` | ページング |
| `toList()` | クエリを実行し、値を順に返す |
| `toSet()` | `select distinct` でクエリを実行し、順序のある `SequencedSet` を返す |
| `first()` | 最初の値（Java の `Optional`） |
| `one()` | ただ 1 つの値（Java の `Optional`）。行が 2 つ以上なら `NonUniqueResultException` |
| `toHql()` | クエリを実行せず、HQL とパラメーターを返す |

<!-- region: ManualKotlinSelectTest.kt#distinct -->
```kotlin
val q = qb.from(Order::class.java)
    .orderBy { o -> desc(o.price) }
    .select { it.price }
    .distinct()
assertEquals("select distinct e0.price from Order e0 order by e0.price desc", q.toHql().hql())
// toSet() always uses select distinct
val prices = q.toSet()
```

**データベースごとの SQL**

<!-- sql: distinct -->
```sql
-- H2, MariaDB, Oracle (same)
select distinct o1_0.price
from orders o1_0
order by o1_0.price desc
```

- `distinct()` を使うと、すべてのソートキーが select の値でなければなりません。そうでなければ、lambdaQL は解析時に `TranslationException`（`LQL-3046`）を投げます（[6.11](#611-rules-j1-to-j8) を参照）。
- 値のクエリの `toSet()` は常に `select distinct` を使います。ここでは値の繰り返しが正常なので、サイズのチェックはしません（エンティティの `toSet()` とは違います。[2. はじめよう](02-getting-started.md#toset) を参照）。
- `@Lob` のカラムを `distinct` や `groupBy` のキーに使うと、Oracle は拒否します（ORA-22848、ORA-22849）。H2 と MariaDB は実行します。lambdaQL はクエリを変えず、`LQL-5010` を `DEBUG` レベルでログに出します（[4.5](04-order-paging.md#45-what-a-sort-key-can-be) を参照）。

**ページング。** `limit` と `offset` は [4. ソートとページング](04-order-paging.md) と同じように動きます。
ページングを使うと、lambdaQL が最後のソートキーとして `id` を加えるので、ページは重なりません。ソートキーが一意で NULL になれないカラムなら加えません（[4.8](04-order-paging.md#tie-breaker-automatic-id) を参照）。
`first()` も 1 行だけを読むので、同じ補助のソートを加えます。ソートキーの値が同じ行が複数あると、毎回 `id` がいちばん小さい行の値を返します。
グループのクエリと `distinct` のクエリには加えません。これらのクエリの select リストには `id` がないからです。

<!-- region: ManualKotlinSelectTest.kt#value-page -->
```kotlin
// paging adds the id as the last key, so the pages do not overlap
val page = qb.from(Order::class.java)
    .orderBy { o -> desc(o.price) }
    .select { it.price }
    .limit(2).offset(1)
assertEquals("select e0.price from Order e0 order by e0.price desc, e0.id asc", page.toHql().hql())
```

**データベースごとの SQL**

<!-- sql: value-page -->
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

コード例: [`ManualKotlinSelectTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSelectTest.kt)（`distinct`、`value-page`）。

---

<a id="68-explicit-joins"></a>
## 6.8 明示的な結合

関連のパス（`it.customer?.name`）は、to-one の関連を自動で結合します（[3.10](03-where.md#310-association-paths) を参照）。
他のエンティティを自分で決めた条件で結合する場合や、条件なしで結合する場合は、`join`、`leftJoin`、`crossJoin` を使ってください。

| 関数 | HQL | 行 |
|---|---|---|
| `join(Customer::class.java) { o, c -> ... }` | `join Customer e1 on ...` | inner join: 相手のある行だけ |
| `leftJoin(Customer::class.java) { o, c -> ... }` | `left join Customer e1 on ...` | 相手のない主エンティティの行も残る |
| `crossJoin(Customer::class.java)` | `cross join Customer e1` | すべての行の組み合わせ。条件は `where` に書く |

結合の後のすべてのラムダ式は、結合の順にすべてのエンティティを受け取ります。
`where { o, c -> ... }`、`orderBy { o, c -> asc(c.name) }`、`select { o, c -> ... }` です。
結合したエンティティの別名は `e1`、`e2` です。

<!-- region: ManualKotlinSelectTest.kt#join -->
```kotlin
val q = qb.from(Order::class.java)
    .join(Customer::class.java) { o, c -> o.customer?.id == c.id }
    .where { _, c -> c.grade != null && c.grade >= 5 }
assertEquals("select e0 from Order e0 join Customer e1 on e0.customer is not null and e0.customer.id = e1.id"
    + " where e1.grade is not null and e1.grade >= 5", q.toHql().hql())
// without select, the result is the main entity
val result: List<Order> = q.toList()
```

Kotlin の `o.customer?.id == c.id` は NULL になりうる値の比較なので、lambdaQL が `e0.customer is not null and` を付け加えます。結果の行は同じです。

**結果は主エンティティです。** `select` がなければ、`toList()` は `List<Order>` を返します。
結合したエンティティは、条件、ソート、`select` にだけ使います。
結合したエンティティの値を読むには、`select { o, c -> ... }` を使ってください（下を参照）。

<a id="left-join-and-null"></a>
### left join と NULL

<!-- region: ManualKotlinSelectTest.kt#left-join-null -->
```kotlin
// c is missing for an order without a customer: c == null finds those rows
val q = qb.from(Order::class.java)
    .leftJoin(Customer::class.java) { o, c -> o.customer?.id == c.id }
    .where { _, c -> c == null || c.name == "Kim" }
assertEquals("select e0 from Order e0 left join Customer e1 on e0.customer is not null and e0.customer.id = e1.id"
    + " where e1.id is null or e1.name = 'Kim'", q.toHql().hql())
```

- `c == null` は「相手がない」という意味です。lambdaQL はこれを `e1.id is null`（結合したエンティティの `@Id`）と書きます。
  ラムダ式のパラメーター `c` は non-null 型なので、Kotlin のコンパイラーが `c == null` は常に false だという警告を出すことがあります。lambdaQL はこの式を「相手がない」と読みます。
- `leftJoin` の後で結合したエンティティのカラムにかける条件は、**SQL の意味** に従います。相手がないとカラムは NULL なので条件は真にならず、その行は結果から外れます。
  相手のない行を残すには、例のように `c == null || ...` と書いてください。

<a id="sort-and-select"></a>
### ソートと select

例の `Row` は次の data class です。

<!-- region: ManualKotlinSelectTest.kt#row-type -->
```kotlin
data class Row(val orderId: Long?, val customerName: String?)
```

<!-- region: ManualKotlinSelectTest.kt#join-sort-select -->
```kotlin
val sorted = qb.from(Order::class.java)
    .leftJoin(Customer::class.java) { o, c -> o.customer?.id == c.id }
    .orderBy { o, c -> asc(c.name).asc(o.id) }
assertEquals("select e0 from Order e0 left join Customer e1 on e0.customer is not null and e0.customer.id = e1.id"
    + " order by e1.name asc nulls first, e0.id asc", sorted.toHql().hql())
// the order without a customer has customerName = null
val rows = qb.from(Order::class.java)
    .leftJoin(Customer::class.java) { o, c -> o.customer?.id == c.id }
    .orderBy { o, _ -> asc(o.id) }
    .select { o, c -> Row(o.id, c.name) }
assertEquals("select e0.id, e1.name from Order e0 left join Customer e1 on e0.customer is not null and e0.customer.id = e1.id"
    + " order by e0.id asc", rows.toHql().hql())
```

顧客のない注文は、`customerName` が `null` です。
`select` は `ValueQuery` を返すので、`distinct()`、`limit`、`offset`、[6.7](#67-distinct-paging-and-results) の結果関数を使えます。

<a id="select-entities"></a>
### エンティティの選択

`select` にはエンティティそのものも書けます。結合したエンティティを選んだり、複数のエンティティを 1 つの data class や `Pair` に入れたりできます。

<!-- region: KotlinSelectEntityTest.kt#select-joined -->
```kotlin
// the customers that have an order, through an explicit join; Hibernate returns each entity once
val q = qb.from(Order::class.java)
    .join(Customer::class.java) { o, c -> o.customer?.id == c.id }
    .select { _, c -> c }
```

エンティティを 1 つ選ぶと、Hibernate は同じエンティティを 1 回だけ返します。HQL は `select e1 from Order e0 join Customer e1 on e0.customer is not null and e0.customer.id = e1.id` です。

<!-- region: KotlinSelectEntityTest.kt#select-entities -->
```kotlin
// an order and its customer together; with leftJoin an order without a customer has null
val q = qb.from(Order::class.java)
    .leftJoin(Customer::class.java) { o, c -> o.customer?.id == c.id }
    .select { o, c -> OrderWithCustomer(o, c) }
```

`OrderWithCustomer` は `data class OrderWithCustomer(val order: Order, val customer: Customer?)` です。HQL は `select e0, e1 from Order e0 left join Customer e1 on ...` で、注文ごとに 1 行です。`select { o, c -> o to c }` のように `Pair` で受け取ってもかまいません。

エンティティを入れた行は、派生テーブルや CTE の行として使えません（`LQL-3071`）。エンティティの id やカラムを選んでください。

コード例: [`KotlinSelectEntityTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinSelectEntityTest.kt)（`select-joined`、`select-entities`）。

<a id="three-entities-and-repeated-rows"></a>
### 複数のエンティティと繰り返される行

エンティティは 10 個まで結合できます（主エンティティと結合するエンティティ 9 個）。結合するたびに、ラムダ式が受け取るエンティティが 1 つずつ増えます: `{ o, c, i -> ... }`。

<!-- region: ManualKotlinSelectTest.kt#join-three -->
```kotlin
// orders of a customer with an item of quantity > 10 (an order with two such items is two SQL rows)
val q = qb.from(Order::class.java)
    .join(Customer::class.java) { o, c -> o.customer?.id == c.id }
    .join(Item::class.java) { o, _, i -> i.order?.id == o.id }
    .where { _, _, i -> i.qty > 10 }
assertEquals("select e0 from Order e0 join Customer e1 on e0.customer is not null and e0.customer.id = e1.id"
    + " join Item e2 on e2.order.id = e0.id where e2.qty > 10", q.toHql().hql())
```

エンティティ 10 個を結合した例です。

<!-- region: KotlinSelectEntityTest.kt#ten -->
```kotlin
// up to 10 entities: each lambda gets all of them in join order
val q = qb.from(Order::class.java)
    .join(Customer::class.java) { o, c -> o.customer?.id == c.id }
    .join(Order::class.java) { o, _, a -> a.id == o.id }
    .join(Order::class.java) { _, _, a, b -> b.id == a.id }
    .join(Order::class.java) { _, _, _, b, d -> d.id == b.id }
    .join(Order::class.java) { _, _, _, _, d, e -> e.id == d.id }
    .join(Order::class.java) { _, _, _, _, _, e, f -> f.id == e.id }
    .join(Order::class.java) { _, _, _, _, _, _, f, g -> g.id == f.id }
    .join(Order::class.java) { _, _, _, _, _, _, _, g, h -> h.id == g.id }
    .leftJoin(Order::class.java) { _, _, _, _, _, _, _, _, h, k -> k.id == h.id }
    .where { _, c, _, _, _, _, _, _, _, k -> c.name == "Kim" && k.price > 0.0 }
    .select { _, c, _, _, _, _, _, _, _, k -> c.name to k.id }
```

> [!WARNING]
> 結合の条件に合う行が複数あると、SQL では **主エンティティの行が相手ごとに繰り返されます**。`toList()` と `toSet()` は、Hibernate が同じエンティティを 1 回だけ返すので、繰り返しはありません。
> しかし `count()` と `limit` は、繰り返された SQL の行を数えます。注文の数を数えたり、ページを正確に埋めたりするには、結合の代わりに `exists()` サブクエリ（[5. サブクエリ](05-subquery.md)）を使うか、結合クエリに `distinct()` を適用してください（[結合結果の `distinct()`](#distinct-on-a-join) を参照）。

<a id="cross-join"></a>
### cross join

`crossJoin` には結合の条件がありません。関数名で、すべての行の組み合わせが欲しいという意図を示します。条件は `where` に書いてください。

<!-- region: ManualKotlinSelectTest.kt#cross-join -->
```kotlin
// every pair of rows; the conditions go in where
val q = qb.from(Order::class.java)
    .crossJoin(Customer::class.java)
    .where { o, c -> c.grade != null && o.price > 100 }
assertEquals("select e0 from Order e0 cross join Customer e1 where e1.grade is not null and e0.price > 100.0",
    q.toHql().hql())
```

lambdaQL には `from(Order::class.java, Customer::class.java)`（カンマ結合）はありません。条件を書き忘れると誤ってすべての組み合わせを得てしまい、どのエンティティが結果なのかもはっきりしないからです。そのような照会には `crossJoin` を使ってください。

<a id="paging"></a>
### ページング

ページングすると、lambdaQL は **すべての** エンティティの id を最後のソートキーとして付け加えます。そのため、ページは重なりません。

<!-- region: ManualKotlinSelectTest.kt#join-paging -->
```kotlin
// with paging, the id of every entity is added to the order, so the pages do not overlap
val pairs = qb.from(Order::class.java)
    .crossJoin(Customer::class.java)
    .orderBy { o, _ -> desc(o.price) }
    .select { o, c -> Row(o.id, c.name) }
assertEquals("select e0.id, e1.name from Order e0 cross join Customer e1 order by e0.price desc, e0.id asc, e1.id asc",
    pairs.limit(5).toHql().hql())
```

ページを順に読むと、すべての組み合わせが 1 回ずつ、ソートの順に出てきます。

<a id="distinct-on-a-join"></a>
### 結合結果の `distinct()`

結合クエリの `distinct()` は、主エンティティの行ごとに SQL の行を 1 つにします。HQL は `select distinct e0` で、`count()` は `count(distinct e0)` です。
`toList()` と `toSet()` は、`distinct()` なしでも主エンティティを 1 回ずつ返します。`count()` と `limit` で主エンティティの数を数えるには、`distinct()` が必要です。

- ソートキーは主エンティティ自身のカラムでなければなりません（`LQL-3046`）。結合したエンティティのカラムや関連のパスでソートするとエラーです。
- ページングすると、主エンティティの id だけをタイブレーカーとして付け加えます。結合したエンティティの id は付けません。
- `distinct()` の後に `select(...)` を使うと、値を `select distinct` で照会します。
- まず `exists` サブクエリ（[5. サブクエリ](05-subquery.md)）を検討してください。繰り返しなしで同じ行を返し、より速いことが多いです。

<!-- region: KotlinQueryTest.kt#join-distinct -->
```kotlin
// an order with two matching items is two SQL rows; distinct() makes it one row in SQL
val q = qb.from(Order::class.java)
    .join(Item::class.java) { o, i -> i.order?.id == o.id }
    .where { _, i -> i.qty > 0 }
    .distinct()
assertEquals("select distinct e0 from Order e0 join Item e1 on e1.order.id = e0.id where e1.qty > 0", q.toHql().hql())
```

<a id="join-a-derived-table-or-cte"></a>
### 派生テーブルと CTE の結合

[6.9](#69-derived-table-subquery-in-from) の派生テーブルと [6.10](#610-cte-with) の CTE は、エンティティのように結合できます。
形は `join(cte) { c, x -> ... }`、`leftJoin(cte) { ... }`、`join(qb.from(...)...select { Row(...) }) { ... }`、`leftJoin(...) { ... }` で、2 つ目や 3 つ目のルートとして使えます。

- HQL は `with t as (...) ... join t e1 on ...` または `join (select ...) e1 on ...` です。
- 行の型は Kotlin の data class（または Java の record）です。プロパティ（`x.total`）がカラムを読みます。
- 同じ `Cte` オブジェクトを 1 つの文で 2 回読むと、定義は 1 回だけ書き、2 つの結合が同じ名前を読みます: `join t e1 on ... left join t e2 on ...`。別々の `Cte` オブジェクトは、`with t as (...), t2 as (...)` のように別の名前を受け取ります（3 つの DB で実測）。
- 派生した行には id がないので、`x == null` はエラー（`LQL-3067`）です。`x.customerId == null` のようにカラムを比較してください。
- Hibernate 6.6 と 7.4 はどちらも、エンティティの `select distinct`、派生テーブルと CTE の inner/left join、サブクエリの中のエンティティの結合を受け付けます（3 つの DB で実測）。

<!-- region: KotlinQueryTest.kt#join-cte -->
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
        + " join t e1 on e1.customerId = e0.id where e1.total > 100.0", rows.toHql().hql())
```

`x.customerId` が nullable な `Int?` でも、`x.customerId == c.id` は `e1.customerId = e0.id` です。Kotlin が入れる null チェックは読みません。

名前のない派生テーブルの結合（`CustomerTotal` は [6.9](#69-derived-table-subquery-in-from) の行の型）:

<!-- region: ManualKotlinSelectTest.kt#join-derived -->
```kotlin
// a derived table without a name; a customer without orders has no row in it (left join)
val small = qb.from(Customer::class.java)
    .leftJoin(qb.from(Order::class.java)
        .where { it.customer != null }
        .groupBy { it.customer?.id }
        .select { CustomerTotal(it.customer?.id, Agg.count(), Agg.sum(it.price)) }) { c, x -> x.customerId == c.id }
    .where { _, x -> x.customerId == null || x.total!! < 210 }
assertEquals("select e0 from Customer e0 left join (select d0.customer.id as customerId, count(*) as cnt,"
    + " sum(d0.price) as total from Order d0 where d0.customer is not null group by d0.customer.id) e1"
    + " on e1.customerId = e0.id where e1.customerId is null or e1.total < 210.0", small.toHql().hql())
```

同じ CTE を 2 回読む形:

<!-- region: ManualKotlinSelectTest.kt#cte-reuse -->
```kotlin
// the same CTE read twice is written once
val reused = qb.from(Customer::class.java)
    .join(t) { c, x -> x.customerId == c.id }
    .leftJoin(t) { c, _, y -> y.customerId == c.id && y.total!! > 210 }
    .select { c, _, y -> c.name to y.total }
assertEquals("with t as (select d0.customer.id as customerId, count(*) as cnt, sum(d0.price) as total from Order d0"
    + " where d0.customer is not null group by d0.customer.id)"
    + " select e0.name, e2.total from Customer e0 join t e1 on e1.customerId = e0.id"
    + " left join t e2 on e2.customerId = e0.id and e2.total > 210.0", reused.toHql().hql())
```

<a id="what-a-join-query-has"></a>
### 結合クエリの関数

| 呼び出しの後 | 関数 |
|---|---|
| `join`、`leftJoin`、`crossJoin` | `where`、`join` / `leftJoin` / `crossJoin`（エンティティ 10 個まで、派生テーブルと CTE を含む）、`distinct`、`orderBy`、`select`、`limit`、`toList`、`toSet`、`first`、`count`、`exists`、`toHql` |
| `orderBy { o, c -> ... }` | `select`、`limit`、`offset`、`toList`、`first`、その他のソート済みクエリの関数（[4. ソートとページング](04-order-paging.md)） |

サブクエリの中の結合は [5. サブクエリ](05-subquery.md#joins-inside-a-subquery) を参照してください。
サポートしていないもの: insert の結合、エンティティ 11 個以上、`SortSpec` の結合の形。
update と delete の結合は追加しないことにしました。関連のパスか `sub` を使ってください。

コード例: [`ManualKotlinSelectTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSelectTest.kt)（`join`、`left-join-null`、`row-type`、`join-sort-select`、`join-three`、`cross-join`、`join-paging`、`join-derived`、`cte-reuse`）、[`KotlinQueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)（`join-distinct`、`join-cte`）、[`KotlinSelectEntityTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinSelectEntityTest.kt)（`ten`）。

---

<a id="69-derived-table-subquery-in-from"></a>
## 6.9 派生テーブル（FROM の中のサブクエリ）

`select` で値のクエリ（data class、[6.3](#63-select-a-data-class-pair-or-triple) を参照）を作ってから、`qb.from(...)` に渡してください。
その行が外側のクエリのテーブルになります。
結果は `DerivedQuery<R>` で、`R` は行の型です。

この節と [6.10](#610-cte-with) の行の型:

<!-- region: ManualKotlinSelectTest.kt#derived-types -->
```kotlin
/** A row of a derived table or CTE: the properties name the columns (J5). */
data class CustomerTotal(val customerId: Int?, val cnt: Long, val total: Double?)
data class Priced(val id: Long?, val price: Double?)
```

<!-- region: ManualKotlinSelectTest.kt#derived -->
```kotlin
// the rows of a group query are the table of the outer query; r.total reads the column "total"
val q = qb.from(qb.from(Order::class.java)
        .where { it.customer != null }
        .groupBy { it.customer?.id }
        .select { CustomerTotal(it.customer?.id, Agg.count(), Agg.sum(it.price)) })
    .where { r -> r.total!! > 100 }
    .orderBy { r -> desc(r.total) }
assertEquals("select e0.customerId, e0.cnt, e0.total from (select d0.customer.id as customerId, count(*) as cnt,"
    + " sum(d0.price) as total from Order d0 where d0.customer is not null group by d0.customer.id) e0"
    + " where e0.total > 100.0 order by e0.total desc nulls last", q.toHql().hql())
```

- 内側のクエリの別名は `d0`（結合は `dj1`、...）、外側のクエリの別名は `e0` です。
- data class のプロパティごとに、プライマリコンストラクターの順でカラム名が決まります: `as customerId`、`as cnt`、`as total`。
- 外側のラムダ式では、プロパティでカラムを読みます。`r.total` は `e0.total` です。
- 外側のクエリには `where`、`orderBy`、`limit`、`offset`、`select`、`toList`、`first`、`count`、`toHql` があります。

行の値を 1 つだけ select することもできます（`.select { r -> r.customerId }`）。内側と外側のキャプチャした値は、順にバインドします。
内側のクエリの `limit` と `offset` は内側のクエリに残ります（HQL の `limit`、`offset`）。そのため、上位の行をもう一度ソートできます。

<!-- region: ManualKotlinSelectTest.kt#derived-limit -->
```kotlin
// the three most expensive orders, then the cheapest of them
val cheapestOfTop = qb.from(qb.from(Order::class.java)
        .orderBy { o -> desc(o.price) }
        .limit(3)
        .select { Priced(it.id, it.price) })
    .orderBy { r -> asc(r.price) }
    .first()
```

Hibernate 7.4 は、派生テーブルのカラムに `c0`、`c1`、`c2` のような名前を付けます。H2 はカラムのリストを別名に付けます（`r1_0(c0,c1,c2)`）。MariaDB と Oracle は、サブクエリの中にカラムの別名を書きます。

ルール:

- 行の型は **Kotlin の data class**（または Java の record）でなければなりません。
  lambdaQL は、data class のプロパティからプライマリコンストラクターの順でカラム名を取ります。
- 普通のクラス、`Pair`、`Triple`、1 つの値はここでは使えません。lambdaQL がカラム名を決められないからです。この場合は `TranslationException`（`LQL-3064`）が発生します。

<!-- region: ManualKotlinSelectTest.kt#derived-row-type -->
```kotlin
// a single value (or a Pair, or a class that is not a data class) has no column names
val e = assertThrows(LambdaQLException::class.java) { qb.from(qb.from(Order::class.java).select { it.price }) }
assertEquals("LQL-3064", e.code())
```

エンティティのクエリに派生テーブルを結合する形は、[派生テーブルと CTE の結合](#join-a-derived-table-or-cte) を参照してください。

サポートしていないもの: 派生テーブルどうしの結合、update、delete、insert の中の派生テーブル。

エンティティの `@SQLRestriction` と `@SoftDelete` の条件は、エンティティの結合の `on` と派生テーブルの中にも Hibernate が付けます（MariaDB 10.11、Oracle 23.26 で実測）。lambdaQL はこの条件を別に扱いません。

コード例: [`ManualKotlinSelectTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSelectTest.kt)（`derived-types`、`derived`、`derived-limit`、`derived-row-type`）。

---

<a id="610-cte-with"></a>
## 6.10 CTE（`with`）

`qb.with(valueQuery)` で `Cte<R>` 型のハンドルを作ってください。それから `qb.from(handle)` で読みます。
内側のクエリは派生テーブル（6.9）と同じです。

<!-- region: ManualKotlinSelectTest.kt#cte -->
```kotlin
val perCustomer = qb.from(Order::class.java)
    .where { it.customer != null }
    .groupBy { it.customer?.id }
    .select { CustomerTotal(it.customer?.id, Agg.count(), Agg.sum(it.price)) }
val t: Cte<CustomerTotal> = qb.with(perCustomer)
val q = qb.from(t).where { r -> r.cnt >= 2 }
assertEquals("with t as (select d0.customer.id as customerId, count(*) as cnt, sum(d0.price) as total"
    + " from Order d0 where d0.customer is not null group by d0.customer.id)"
    + " select e0.customerId, e0.cnt, e0.total from t e0 where e0.cnt >= 2L", q.toHql().hql())
```

MariaDB と Oracle は本物の `with` を実行します。カラムのリストは Hibernate が自分で書きます: `with t (customerId,cnt,total) as (...)`。H2 は CTE を派生テーブルに変えます。

備考:

- CTE の名前は `t` です。外側のクエリは常に別名 `e0` を付けます（`from t e0`）。HQL では別名が必要です。
- CTE のカラム名は select の別名（`as customerId`）で決めます。HQL は CTE の名前の後のカラムのリストを受け付けず、カラムのリストは Hibernate が SQL に追加します。
- MariaDB と Oracle は実際の `with` を実行します。H2 では Hibernate がこれを派生テーブルに展開します。結果は同じです。
- 行の型のルールは派生テーブルと同じです: data class か record。
- エンティティのクエリに CTE を結合する形は、[派生テーブルと CTE の結合](#join-a-derived-table-or-cte) を参照してください。
- 同じ CTE を 1 つのクエリで 2 回読めます。定義は 1 回だけ書きます（[派生テーブルと CTE の結合](#join-a-derived-table-or-cte) を参照）。
- サポートしていないもの: update、delete、insert の中の CTE。

コード例: [`ManualKotlinSelectTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSelectTest.kt)（`cte`）。

---

<a id="611-rules-j1-to-j8"></a>
## 6.11 ルール J1〜J8

| # | ルール | 状態 |
|---|---|---|
| J1 | `select` のラムダ式は **1 つの値の式** か **1 つのコンストラクター呼び出し** を返します。コンストラクターの各引数は値の式です（[3. 条件（`where`）](03-where.md) のルール）。エンティティそのものも値になれます（[エンティティの選択](#select-entities)）。値を選ぶ `if` と `when` は `case when` になります（[3.5.1](03-where.md#351-values-chosen-by-a-condition)）。`select` にだけ使った関連のパスは `left join` の別名になるので、行を失いません。必須の関連（`optional = false`）は inner join です（A4）。同じパスを `where` で使うと、`select` は `where` の結合を使います（R24）。 | 実装済み |
| J2 | コンストラクターの場合、lambdaQL はカラムだけを選択し、コンストラクターを直接呼びます。`select new` は書きません。`Pair`/`Triple` も同じ方法で作ります。 | 実装済み |
| J3 | 集約があると、`select` と `having`（そして `insert ... select` の値）で集約でない値はすべて `groupBy` のキーでなければなりません。そうでなければ解析時にエラーが発生します。 | 実装済み。エラーは `TranslationException`（`LQL-3035`、[11](../11-errors-and-languages.md) を参照） |
| J4 | `sum`/`avg`/`min`/`max` は、行がないかすべての値が NULL なら NULL を返します。`count` は 0 を返します。コンストラクターのパラメーターは nullable にしてください。non-null のパラメーターに NULL が来ると `IllegalStateException` が発生します。 | 実装済み |
| J5 | 派生テーブルや CTE の行の型: **Kotlin の data class か Java の record だけ**。普通のクラスは単純な `select` でだけ許されます。そうでなければ `LQL-3064`。結合した派生テーブルと CTE の行にも適用します。派生した行は `null` と比較できません（`LQL-3067`）。 | 実装済み |
| J6 | `qb.with(...)` で作った CTE は、select の別名でカラム名を決めます: `with t as (select d0.customer.id as customerId, ...) select e0.customerId ... from t e0`。HQL は名前の後のカラムのリストを受け付けません。Hibernate は `with t (col list) as (...)` を `SyntaxException` で拒否します。カラムのリストは Hibernate が SQL に直接書きます（MariaDB と Oracle では `with t (customerId,cnt,total) as (...)`）。H2 は CTE を派生テーブルに変えます。外側のクエリは常に別名を使います（`from t e0`）。 | 実装済み |
| J7 | `distinct()` は `select distinct` を出力します。ソートと一緒に使うと、ソートキーが select リストになければなりません（DB のルール）。lambdaQL は解析時にこれをチェックします。結合結果の `distinct()` では、ソートキーは主エンティティ自身のカラムでなければなりません。 | 実装済み |
| J8 | 選択は、ソート（[4. ソートとページング](04-order-paging.md)）、ページング、サブクエリ（[5. サブクエリ](05-subquery.md)）と一緒に動きます。グループのクエリの集約の結果でソートする機能（`desc(s.total)`）はありません。 | 実装済み |

J3 の例は [6.5](#every-non-aggregate-value-must-be-a-key-rule-j3) にあります。`where` の中の `Agg`、J7、J4:

<!-- region: ManualKotlinSelectTest.kt#rules -->
```kotlin
// aggregates belong in having, not in where
val where = assertThrows(LambdaQLException::class.java) { qb.from(Order::class.java).where { Agg.count() > 1 } }
assertEquals("LQL-3005", where.code())
// with distinct, the sort key must be selected
val j7 = assertThrows(LambdaQLException::class.java) {
    qb.from(Order::class.java).orderBy { o -> asc(o.id) }.select { it.price }.distinct().toHql()
}
assertEquals("LQL-3046", j7.code())
// sum over no rows is NULL; the non-null parameter of StrictTotal(val total: Double) cannot take it
assertThrows(IllegalStateException::class.java) {
    qb.from(Order::class.java).where { it.status.toInt() == 9 }.select { StrictTotal(Agg.sum(it.price)) }.toList()
}
```

コード例: [`ManualKotlinSelectTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSelectTest.kt)（`rules`）。

---

<a id="612-not-in-v01"></a>
## 6.12 v0.1 に含まれないもの

この章の機能には、次のものがありません。

| 項目 | 備考 |
|---|---|
| 集約の結果や select の別名でのソート（例: `orderBy { s -> desc(s.total) }`）、グループのクエリの `orderBy` | |
| ユーザー定義の集約関数とウィンドウ関数 | |
| insert の結合、エンティティ 11 個以上、サブクエリの中のエンティティ 3 つ以上 | |
| update、delete の結合 | 追加しないことにした。関連のパスか `sub` を使う |
| DML の中の派生テーブルと CTE | |

この章のエラーは、コード付きの `TranslationException` です。誤ったグループのキーは `LQL-3035`、`where` の中の `Agg` は `LQL-3005`、`distinct` で select していないソートキーは `LQL-3046`、派生テーブルや CTE の行の型は `LQL-3064` です。[11. エラーと言語](../11-errors-and-languages.md) を参照してください。

---

[Kotlin トラック](README.md) | 前: [5. サブクエリ](05-subquery.md) | 次: [7. 更新、挿入、削除](07-dml.md) | [このページの Java 版](../java/06-select-group.md)
