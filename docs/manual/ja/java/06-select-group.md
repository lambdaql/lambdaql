<a id="6-select-aggregates-and-group-by-java"></a>
# 6. 選択、集計、グループ化（Java）

> [!NOTE]
> **状況: 設計済み、まだ実装されていません。**
> この章の内容はすべて決定済みの設計です（決定 D27、マイルストーン M9）。
> 構文はコンパイルし、H2、MariaDB、Oracle でプロトタイプによって確認しました。
> 現在の lambdaQL では **動きません**。現在動くのは解析コアと `where` + `toList()` だけです
> （H2、MariaDB、Oracle でテスト済み）。全体の状況は [Java トラックの README](README.md#status) を参照してください。

[Java トラック](README.md) | [マニュアルのホーム](../README.md) | 前: [5. サブクエリ](05-subquery.md) | 次: [7. 更新、挿入、削除](07-dml.md) | [このページの Kotlin 版](../kotlin/06-select-group.md)

共通ページ: [1. 概要](../01-overview.md) · [11. エラーと言語](../11-errors-and-languages.md)

---

この章では、クエリが **何を** 返すかを選ぶ方法を説明します。

- 行ごとに 1 つの値（`select(o -> o.getPrice())`）
- Java の record または DTO クラス
- `groupBy` と `having` を使った集約（`count`、`sum`、`avg`、...）
- FROM 句の中のサブクエリ（派生テーブル）と CTE（`with`）

<a id="contents"></a>
## 目次

- [6.1 例で使うエンティティと結果の型](#61-example-entities-and-result-types)
- [6.2 1 つの値を選択する](#62-select-one-value)
- [6.3 record または DTO を選択する](#63-select-a-record-or-dto)
- [6.4 集約関数: `Agg`](#64-aggregate-functions-agg)
- [6.5 Group by](#65-group-by)
- [6.6 Having](#66-having)
- [6.7 派生テーブル（FROM の中のサブクエリ）](#67-derived-table-subquery-in-from)
- [6.8 CTE（`with`）](#68-cte-with)
- [6.9 ルール J1〜J8](#69-rules-j1-to-j8)
- [6.10 v0.1 に含まれないもの](#610-not-in-v01)

---

<a id="61-example-entities-and-result-types"></a>
## 6.1 例で使うエンティティと結果の型

例では次のエンティティを使います（[2. はじめよう](02-getting-started.md#example-entities) を参照）。

| エンティティ | ここで使うフィールド |
|---|---|
| `Order` | `id`、`price`、`status`、`active`、`customer`（多対一） |
| `Customer` | `id`、`name`、`grade` |
| `Item` | `order`、`price`、`qty` |

また、次の結果の型を使います。

```java
public record OrderView(Long id, String customerName, Double price) {}
public record Stat(Long customerId, long cnt, Double total) {}
public record Total(long cnt, Double avgPrice) {}
```

> [!TIP]
> NULL になりうる値には、ボクシング型（`Double`、`Long`）を使ってください。
> カラムや `sum` のような集約は NULL になることがあります。プリミティブの `double` のコンポーネントは NULL を保持できません。
> `count` は決して NULL にならないので、`long cnt` で問題ありません（ルール J4）。

---

<a id="62-select-one-value"></a>
## 6.2 1 つの値を選択する

`select` は 1 つのラムダを受け取ります。ラムダは 1 つの値を返します。結果はその値のリストです。

```java
List<Double> prices = qb.from(Order.class)
    .where(o -> o.getStatus() == 1)
    .select(o -> o.getPrice())
    .toList();
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

```java
.select(o -> o.getCustomer().getName())
// → select c1.name from Order o left join o.customer c1
```

**データベースごとの SQL（予定）** — 予定の HQL を Hibernate が変換したもので、`PlannedSqlTest` が記録しました。lambdaQL はまだこのクエリを作りません。`expected-sql/` の [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)、例 `06-select-path`:

```sql
-- H2, MariaDB, Oracle (same)
select c1_0.name
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
```

パスが関連エンティティの `@Id` で終わる場合、結合はありません。
`o.getCustomer().getId()` は外部キーのカラム `customer_id` を読みます。

---

<a id="63-select-a-record-or-dto"></a>
## 6.3 record または DTO を選択する

ラムダの中でコンストラクターを呼び出します。コンストラクターの各引数は値の式です。

```java
// record または通常の DTO クラス
List<OrderView> list = qb.from(Order.class)
    .select(o -> new OrderView(o.getId(), o.getCustomer().getName(), o.getPrice()))
    .toList();
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

返せるもの:

| 結果の型 | 単純な `select` | 派生テーブルまたは CTE の行の型 |
|---|---|---|
| 1 つの値（`o.getPrice()`） | はい | – |
| Java の record | はい | はい |
| 通常の DTO クラス（コンストラクター） | はい | **いいえ**（J5） |

---

<a id="64-aggregate-functions-agg"></a>
## 6.4 集約関数: `Agg`

集約関数は **`org.lambdaql.Agg` の static メソッド** です。
これらは SQL 標準の関数なので、lambdaQL が提供します。（独自の DB 関数については [8. プロシージャと DB 関数](08-procedures-functions.md) を参照してください。）
`Agg` は lambdaQL で唯一の static ヘルパークラスです。

| メソッド | SQL |
|---|---|
| `Agg.count()` | `count(*)` |
| `Agg.count(x)` | `count(x)` |
| `Agg.countDistinct(x)` | `count(distinct x)` |
| `Agg.sum(x)` | `sum(x)` |
| `Agg.avg(x)` | `avg(x)` |
| `Agg.min(x)` | `min(x)` |
| `Agg.max(x)` | `max(x)` |

Java の型:

```java
long count();
<N extends Number> N sum(N x);
Double avg(Number x);
<R extends Comparable<?>> R min(R x);   // max も同じ
```

static import は任意です。次の 2 行は同じです。

```java
.select(o -> new Total(Agg.count(), Agg.avg(o.getPrice())))

import static org.lambdaql.Agg.*;
.select(o -> new Total(count(), avg(o.getPrice())))
```

重要な点:

- 戻り値は **仮の値** にすぎません。ラムダは実行されないので、その値が使われることはありません。
- lambdaQL のラムダの **外** で `Agg.sum(...)` を呼び出すと、`IllegalStateException` を投げます。
- `where` では `Agg` を使えません。集約に対する条件には `having` を使います。`where` で `Agg` を使うと、解析時にエラーになります。

<a id="aggregate-over-all-rows-no-group-by"></a>
### すべての行に対する集約（group by なし）

```java
List<Total> t = qb.from(Order.class)
    .where(o -> o.getStatus() == 1)
    .select(o -> new Total(Agg.count(), Agg.avg(o.getPrice())))
    .toList();
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
### 集約での NULL（ルール J4）

| 関数 | 行がない、またはすべての値が NULL |
|---|---|
| `sum`、`avg`、`min`、`max` | `NULL` |
| `count` | `0` |

これは通常の SQL の動作です。

---

## 6.5 Group by

グループのキーは `groupBy` で書きます。lambdaQL はキーを推測しません。

<a id="one-key"></a>
### キーが 1 つ

```java
List<Stat> stats = qb.from(Order.class)
    .groupBy(o -> o.getCustomer().getId())
    .select(o -> new Stat(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice())))
    .toList();
```

HQL（キーが `@Id` で終わるので、結合はありません）:

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

`List.of(...)` でキーのリストを返します。

```java
.groupBy(o -> List.of(o.getCustomer().getId(), o.getStatus()))
// → group by o.customer.id, o.status
```

キーは書いた順序で使われます。
ラムダの中で作ったコレクションが許されるのは、ここだけです。

キーのリストとして使えないもの:

| 形 | 理由 |
|---|---|
| `Set.of(a, b)` | セットは重複を取り除き、順序を持たない |

<a id="every-non-aggregate-value-must-be-a-key-rule-j3"></a>
### 集約でない値はすべてキーでなければならない（ルール J3）

クエリに集約がある場合、`select` と `having` の中の集約 **でない** 値は、すべて `groupBy` のキーでなければなりません。これは SQL のルールです。lambdaQL は解析時にこれをチェックします。

```java
// エラー: o.getStatus() はグループのキーではない
qb.from(Order.class)
  .groupBy(o -> o.getCustomer().getId())
  .select(o -> new Stat2(o.getCustomer().getId(), o.getStatus(), Agg.count()));   // Stat2: 何かの record
// → TranslationException
```

---

## 6.6 Having

`having` はグループに対する条件です。`where` と同じラムダのルール（[3. 条件](03-where.md)）を使い、`Agg` も使えます。

```java
List<Stat> big = qb.from(Order.class)
    .groupBy(o -> o.getCustomer().getId())
    .having(o -> Agg.sum(o.getPrice()) > 1000)
    .select(o -> new Stat(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice())))
    .toList();
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

`Agg.sum(o.getPrice())` は `Double` を返します。javac は `> 1000` のためにそれをアンボクシングします。これは通常の数値の比較です（ルール R1）。

---

<a id="67-derived-table-subquery-in-from"></a>
## 6.7 派生テーブル（FROM の中のサブクエリ）

`qb.from` にラムダを渡します。ラムダはサブクエリのファクトリー（`sub`）を受け取り、内側のクエリを組み立てます。
内側の `select` が行の型を決めます。外側のクエリでは、その行をエンティティのように使います。

```java
List<Stat> rows = qb.from(sub -> sub.from(Order.class)
        .groupBy(o -> o.getCustomer().getId())
        .select(o -> new Stat(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice()))))
    .where(r -> r.total() > 100)
    .toList();
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

- 行の型は **Java の record** でなければなりません（ルール J5）。
  lambdaQL は record のコンポーネントの順序からカラムのエイリアスを取ります。
- 通常の DTO クラスはここでは使えません。lambdaQL はそのコンストラクターのパラメーター名を知ることができません。
- 外側のラムダでは、record のアクセサーで行を読みます: `r.total()`。

---

<a id="68-cte-with"></a>
## 6.8 CTE（`with`）

`qb.with(...)` でハンドルを作ります。そして、そのハンドルを `qb.from(...)` で使います。
同じハンドルを複数回使えます。

```java
var t = qb.with(sub -> sub.from(Order.class)
        .groupBy(o -> o.getCustomer().getId())
        .select(o -> new Stat(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice()))));

List<Stat> rows = qb.from(t).where(r -> r.total() > 100).toList();
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
- 行の型のルールは派生テーブルと同じで、record です（J5）。
- ハンドルには `var` を使ってください。その型名は確定していません。

---

<a id="69-rules-j1-to-j8"></a>
## 6.9 ルール J1〜J8

| # | ルール |
|---|---|
| J1 | `select` のラムダは **1 つの値の式** または **1 つのコンストラクター呼び出し** を返す。コンストラクターの各引数は値の式（ルールは [3. 条件](03-where.md)）。`select` だけで使う関連のパスは `left join` のエイリアスになるので、行は失われない。同じパスを `where` が使うと、`select` は `where` の結合を使う（R24）。 |
| J2 | コンストラクターの場合、lambdaQL はカラムだけを選択し、あなたのクラスを使った Hibernate の型付きクエリを使う。`select new` は書かない。 |
| J3 | 集約がある場合、`select` と `having` の中の集約でない値はすべて `groupBy` のキーでなければならない。そうでなければ、解析時に `TranslationException`。 |
| J4 | 行がない、またはすべての値が NULL のとき、`sum`/`avg`/`min`/`max` は NULL を返す。`count` は 0 を返す。NULL になりうる値にはボクシング型を使う。 |
| J5 | 派生テーブルまたは CTE の行の型: **Java の record**（Kotlin のコードでは data class も使える）。通常の DTO クラスは単純な `select` でだけ使える。 |
| J6 | `qb.with(...)` で作った CTE は、select のエイリアスでカラム名を付ける: `with t as (select o.customer.id as customerId, ...) select x.customerId ... from t x`。HQL は名前の後のカラムリストを受け付けない。Hibernate は `with t (col list) as (...)` を `SyntaxException` で拒否する。カラムリストは Hibernate 自身が SQL に書く（MariaDB と Oracle で `with t (customerId,cnt,total) as (...)`）。H2 は CTE を派生テーブルに変える。外側のクエリでは必ずエイリアスを使う（`from t x`）。 |
| J7 | `distinct()` は `select distinct` を出力する。ソートと一緒に使う場合、ソートキーは select リストに含まれていなければならない（DB のルール）。lambdaQL は解析時にこれをチェックする。 |
| J8 | 選択は、ソート（[4. ソートとページング](04-order-paging.md)）、ページング、サブクエリ（[5. サブクエリ](05-subquery.md)）と一緒に使える。 |

---

<a id="610-not-in-v01"></a>
## 6.10 v0.1 に含まれないもの

| 項目 | 状況 |
|---|---|
| 集約の結果や select のエイリアスによるソート。たとえば `orderBy((s, by) -> by.desc(s.total()))` | v0.2 の候補 |
| ユーザー定義の集約関数とウィンドウ関数 | v0.2 の候補 |
| 結合に対する射影 `select((o, c) -> new OrderView(o.getId(), c.getName(), o.getPrice()))` | 予定。結合の構文はまだ確定していない |
| `toList()` 以外の結果メソッド | 予定 |

この章のエラー（間違ったグループのキー、`where` の中の `Agg`、派生テーブルの行としての DTO クラス）は、`[LQL-xxxx]` コードを持つ `LambdaQLException` のサブクラスです。[11. エラーと言語](../11-errors-and-languages.md) を参照してください。

---

[Java トラック](README.md) | 前: [5. サブクエリ](05-subquery.md) | 次: [7. 更新、挿入、削除](07-dml.md) | [このページの Kotlin 版](../kotlin/06-select-group.md)
