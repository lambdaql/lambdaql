<a id="6-select-aggregates-and-group-by-java"></a>
# 6. 選択、集計、グループ化（Java）

[Java トラック](README.md) | [マニュアルのホーム](../README.md) | 前: [5. サブクエリ](05-subquery.md) | 次: [7. 更新、挿入、削除](07-dml.md) | [このページの Kotlin 版](../kotlin/06-select-group.md)

共通ページ: [1. 概要](../01-overview.md) · [11. エラーと言語](../11-errors-and-languages.md)

---

この章では、クエリが **何を** 返すかを選ぶ方法を説明します。

- 行ごとに 1 つの値（`select(o -> o.getPrice())`）
- Java の record または DTO クラス
- `groupBy` と `having` を使った集約（`count`、`sum`、`avg`、...）
- 値のクエリの `distinct`、ページング、結果のメソッド
- 他のエンティティとの明示的な結合（`join`、`leftJoin`、`crossJoin`）
- FROM 句の中のサブクエリ（派生テーブル）と CTE（`with`）

<a id="contents"></a>
## 目次

- [6.1 例で使うエンティティと結果の型](#61-example-entities-and-result-types)
- [6.2 1 つの値を選択する](#62-select-one-value)
- [6.3 record または DTO を選択する](#63-select-a-record-or-dto)
- [6.4 集約関数: `Agg`](#64-aggregate-functions-agg)
- [6.5 Group by](#65-group-by)
- [6.6 Having](#66-having)
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

| エンティティ | ここで使うフィールド |
|---|---|
| `Order` | `id`、`price`、`status`、`active`、`customer`（多対一） |
| `Customer` | `id`、`name`、`grade` |
| `Item` | `order`、`price`、`qty` |

**(テスト済み)** と書かれた例は、[`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java) からコピーしたものです。
このテストは HQL をチェックし、クエリを実行して、同じ計算を Java で行った値と比べます。H2、MariaDB、Oracle で通ります。
テストは [3.3](03-where.md#33-basic-form) のテスト用エンティティを使います。`price` は `double`、`status` は `short`、`Customer.id` は `int` です。テストデータには顧客のない注文が 1 件あります。

テストでは次の結果の型を使います。

```java
record OrderView(Long id, String customerName, double price) {}
record Stat(Integer customerId, long count, Double total) {}
record KeyCount(Integer customerId, short status, long count) {}
record Total(long count, Double average) {}
```

> [!TIP]
> NULL になりうる値には、ボクシング型（`Double`、`Long`、`Integer`）を使ってください。
> カラムや `sum` のような集約は NULL になることがあります。プリミティブの `double` の引数は NULL を保持できません。そのとき lambdaQL は `IllegalStateException` を投げます（ルール J4、[6.4](#null-in-aggregates-rule-j4) を参照）。
> `count` は決して NULL にならないので、`long count` で問題ありません。

---

<a id="62-select-one-value"></a>
## 6.2 1 つの値を選択する

`select` は 1 つのラムダを受け取ります。ラムダは 1 つの値を返します。結果はその値の `ValueQuery` です。
`select` は `where` の後でも `orderBy` の後でも呼び出せます。ソート、`limit`、`offset` はそのまま残ります。

**(テスト済み)** [`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java)、リージョン `value`:

```java
ValueQuery<Double> q = qb.from(Order.class)
        .where(o -> o.getStatus() == 1)
        .orderBy((o, by) -> by.asc(o.getPrice()))
        .select(o -> o.getPrice());
assertEquals("select e0.price from Order e0 where e0.status = 1 order by e0.price asc", q.toHql().hql());
```

**SQL ブロックの読み方。** `?` はバインドパラメーターです。各行は [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java) から取っています。このテストは各クエリを Hibernate 6.6.5 で H2 2.3、MariaDB 10.11、Oracle 23.26 に対して実行し、Hibernate の SQL が変わると失敗します。この章では select リストが要点なので、省略しません。

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `value`:

```sql
-- H2, MariaDB, Oracle (same)
select o1_0.price
from orders o1_0
where o1_0.status=1
order by o1_0.price
```

関連のパスは、`where` と同じくカラムです（ルール R24）。`select` では lambdaQL は `left join` を追加するので、customer のない注文も失われません（`where` では同じパスがデフォルトで内部結合になります）。

```java
.select(o -> o.getCustomer().getName())
// HQL: select j1.name from Order e0 left join e0.customer j1
```

[6.3](#63-select-a-record-or-dto) の record の例が、この `left join` をテストしています。
パスが関連エンティティの `@Id` で終わる場合、結合はありません。
`o.getCustomer().getId()` は外部キーのカラム `customer_id` を読みます。

---

<a id="63-select-a-record-or-dto"></a>
## 6.3 record または DTO を選択する

ラムダの中でコンストラクターを呼び出します。コンストラクターの各引数は値の式です。
Java の record も、コンストラクターを持つ通常の DTO クラスも使えます。

**(テスト済み)** [`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java)、リージョン `record`:

```java
// only the columns are selected; the record is made in Java. The path uses a left join,
// so the order without a customer stays (customerName is null)
ValueQuery<OrderView> q = qb.from(Order.class)
        .select(o -> new OrderView(o.getId(), o.getCustomer().getName(), o.getPrice()));
assertEquals("select e0.id, j1.name, e0.price from Order e0 left join e0.customer j1", q.toHql().hql());
```

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `record`:

```sql
-- H2, MariaDB, Oracle (same)
select o1_0.id,c1_0.name,o1_0.price
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
```

lambdaQL は `select new com.acme.OrderView(...)` を書き **ません**（ルール J2）。
カラムを選択し、コンストラクターを Java で呼び出します。
これにより、長いクラス名やネストしたクラス名の問題を避けられます。
lambdaQL は各値をコンストラクターの引数の型に変換します（たとえば Hibernate の `Long` を `Integer` の引数に）。

返せるもの:

| 結果の型 | 単純な `select` | 派生テーブルまたは CTE の行の型（[6.9](#69-derived-table-subquery-in-from)） |
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
- `Agg` は `select`、`having`、`insert ... select` の値（[7.5](07-5-insert.md)）で使えます。
- `where` では `Agg` を使えません。集約に対する条件には `having` を使います。`where` で `Agg` を使うと、解析時にエラーになります。

<a id="aggregate-over-all-rows-no-group-by"></a>
### すべての行に対する集約（group by なし）

**(テスト済み)** [`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java)、リージョン `total`:

```java
// aggregates without groupBy: one row over all matching orders
ValueQuery<Total> q = qb.from(Order.class)
        .where(o -> o.getStatus() == 1)
        .select(o -> new Total(Agg.count(), Agg.avg(o.getPrice())));
assertEquals("select count(*), avg(e0.price) from Order e0 where e0.status = 1", q.toHql().hql());
```

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `total`:

```sql
-- H2, MariaDB, Oracle (same)
select count(*),avg(o1_0.price)
from orders o1_0
where o1_0.status=1
```

このクエリは常に 1 行を返します。テストは `q.one()` でそれを読みます。

<a id="null-in-aggregates-rule-j4"></a>
### 集約での NULL（ルール J4）

| 関数 | 行がない、またはすべての値が NULL |
|---|---|
| `sum`、`avg`、`min`、`max` | `NULL` |
| `count` | `0` |

これは通常の SQL の動作です。
NULL がプリミティブのコンストラクター引数（`double`、`long`）に入ると、lambdaQL は `IllegalStateException` を投げます。メッセージは引数を示し、ラッパー型（`Double`、`Long`）を使うように伝えます。テスト済みの例は [6.11](#611-rules-j1-to-j8) を参照してください。

---

## 6.5 Group by

グループのキーは `groupBy` で書きます。lambdaQL はキーを推測しません。
`groupBy` はグループ化したクエリを返します。`having(...)`（任意）を呼び、次に `select(...)` を呼びます。

<a id="one-key"></a>
### キーが 1 つ

```java
List<Stat> stats = qb.from(Order.class)
    .groupBy(o -> o.getCustomer().getId())
    .select(o -> new Stat(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice())))
    .toList();
// HQL: select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id
```

キーが `@Id` で終わるので、結合はありません。顧客のない注文は、キーが NULL の 1 つのグループになります。
`having` を使ったテスト済みの例は [6.6](#66-having) にあります。

<a id="many-keys"></a>
### キーが複数

`List.of(...)` でキーのリストを返します。

**(テスト済み)** [`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java)、リージョン `group-many`:

```java
// many keys: List.of(...) (Kotlin listOf(...), a to b, Triple)
ValueQuery<KeyCount> q = qb.from(Order.class)
        .where(o -> o.getCustomer() != null)
        .groupBy(o -> List.of(o.getCustomer().getId(), o.getStatus()))
        .select(o -> new KeyCount(o.getCustomer().getId(), o.getStatus(), Agg.count()));
assertEquals("select e0.customer.id, e0.status, count(*) from Order e0 where e0.customer is not null"
        + " group by e0.customer.id, e0.status", q.toHql().hql());
```

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `group-many`:

```sql
-- H2, MariaDB, Oracle (same)
select o1_0.customer_id,o1_0.status,count(*)
from orders o1_0
where o1_0.customer_id is not null
group by o1_0.customer_id,o1_0.status
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
  .select(o -> new KeyCount(o.getCustomer().getId(), o.getStatus(), Agg.count()));
// → TranslationException (LQL-3035)
```

テスト済みの例は [6.11](#611-rules-j1-to-j8) にあります。

---

## 6.6 Having

`having` はグループに対する条件です。`where` と同じラムダのルール（[3. 条件](03-where.md)）を使い、`Agg` も使えます。
`having` は何回でも呼び出せます。条件は `AND` でつながります。

**(テスト済み)** [`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java)、リージョン `group-by`:

```java
ValueQuery<Stat> q = qb.from(Order.class)
        .groupBy(o -> o.getCustomer().getId())
        .having(o -> Agg.sum(o.getPrice()) > 200)
        .select(o -> new Stat(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice())));
assertEquals("select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id"
        + " having sum(e0.price) > 200.0", q.toHql().hql());
```

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `group-by`:

```sql
-- H2, MariaDB, Oracle (same)
select o1_0.customer_id,count(*),sum(o1_0.price)
from orders o1_0
group by o1_0.customer_id
having sum(o1_0.price)>200.0
```

ここでは `Agg.sum(o.getPrice())` は `double` なので、javac はそれを `200.0` と比較します。定数はリテラルのまま残ります（決定 D37）。

---

<a id="67-distinct-paging-and-results"></a>
## 6.7 distinct、ページング、結果

`select(...)` は `ValueQuery` を返します。次のメソッドがあります。

| メソッド | 動作 |
|---|---|
| `distinct()` | `select distinct` |
| `limit(n)`、`offset(n)` | ページング |
| `toList()` | クエリを実行する。値を順序どおりに返す |
| `toSet()` | `select distinct` でクエリを実行する。順序どおりの `SequencedSet` |
| `first()` | 最初の値、または `Optional.empty()` |
| `one()` | ただ 1 つの値、または `Optional.empty()`。2 行以上なら `NonUniqueResultException` |
| `toHql()` | クエリを実行せずに HQL とパラメーターを返す |

**(テスト済み)** [`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java)、リージョン `distinct`:

```java
ValueQuery<Double> q = qb.from(Order.class)
        .orderBy((o, by) -> by.desc(o.getPrice()))
        .select(o -> o.getPrice())
        .distinct();
assertEquals("select distinct e0.price from Order e0 order by e0.price desc", q.toHql().hql());
// toSet() always uses select distinct
List<Double> prices = q.toSet().stream().toList();
```

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `distinct`:

```sql
-- H2, MariaDB, Oracle (same)
select distinct o1_0.price
from orders o1_0
order by o1_0.price desc
```

- `distinct()` を使う場合、ソートキーはすべて選択した値でなければなりません（ルール J7）。そうでなければ、lambdaQL は解析時に `TranslationException`（`LQL-3046`）を投げます。
- 値のクエリの `toSet()` は常に `select distinct` を使います。ここでは値の重複は想定内なので、件数のチェックはありません（エンティティの `toSet()` とは違います。[2. はじめよう](02-getting-started.md#toset) を参照）。

**ページング。** `limit` と `offset` は [4. ソートとページング](04-order-paging.md) と同じように動きます。
ページングでは、lambdaQL は最後のソートキーとして `id` を追加するので、ページが重なりません（ルール S9）。
グループ化したクエリと `distinct` のクエリには追加しません。`id` がその select リストにないからです。

```java
ValueQuery<Double> paged = qb.from(Order.class)
        .orderBy((o, by) -> by.desc(o.getPrice()))
        .select(o -> o.getPrice())
        .limit(2).offset(1);
// HQL: select e0.price from Order e0 order by e0.price desc, e0.id asc
```

[`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java) のテストメソッド `pagingAndFirst` が、この HQL、`first()`、ページを確認します。

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `value-page`:

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

関連のパス（`o.getCustomer().getName()`）は、to-one の関連を自動で結合します（[3.10](03-where.md#310-association-paths) を参照）。
別のエンティティを自分の条件で、または条件なしで結合するには、`join`、`leftJoin`、`crossJoin` を使います（決定 D44、D45）。

| メソッド | HQL | 行 |
|---|---|---|
| `join(Customer.class, (o, c) -> ...)` | `join Customer e1 on ...` | 内部結合: 一致する行だけ |
| `leftJoin(Customer.class, (o, c) -> ...)` | `left join Customer e1 on ...` | 一致しない主エンティティの行も残る |
| `crossJoin(Customer.class)` | `cross join Customer e1` | すべての行の組み合わせ。条件は `where` に書く |

結合のあと、どのラムダも結合した順にすべてのエンティティを受け取ります:
`where((o, c) -> ...)`、`orderBy((o, c, by) -> ...)`（`by` は最後、ルール S12）、`select((o, c) -> ...)`。
結合したエンティティには `e1`、`e2` のエイリアスが付きます。

**(テスト済み)** と書かれた例は [`JoinQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java) からコピーしたものです。
テストは各クエリを H2、MariaDB、Oracle で実行し、行を Java での同じ計算と比べます。

**(テスト済み)** [`JoinQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java)、リージョン `join`:

```java
SelectJoinQuery<Order, Customer> q = qb.from(Order.class)
        .join(Customer.class, (o, c) -> o.getCustomer().getId() == c.getId())
        .where((o, c) -> c.getGrade() != null && c.getGrade() >= 5);
assertEquals("select e0 from Order e0 join Customer e1 on e0.customer.id = e1.id"
        + " where e1.grade is not null and e1.grade >= 5", q.toHql().hql());
```

**結果は主エンティティです。** `select` がなければ、`toList()` は `List<Order>` を返します。
結合したエンティティは、条件、ソート、`select` にだけ使います。
結合したエンティティの値を読むには、`select((o, c) -> ...)` を使います（下を参照）。

<a id="left-join-and-null"></a>
### 左外部結合と NULL

**(テスト済み)** [`JoinQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java)、リージョン `left-join`:

```java
// c is missing for an order without a customer: c == null finds those rows
SelectJoinQuery<Order, Customer> q = qb.from(Order.class)
        .leftJoin(Customer.class, (o, c) -> o.getCustomer().getId() == c.getId())
        .where((o, c) -> c == null || c.getName().equals("Kim"));
assertEquals("select e0 from Order e0 left join Customer e1 on e0.customer.id = e1.id"
        + " where e1.id is null or e1.name = 'Kim'", q.toHql().hql());
```

- `c == null` は「一致なし」を意味します。lambdaQL はこれを `e1.id is null`（結合したエンティティの `@Id`）と書きます。
- `leftJoin` のあと、結合したエンティティのカラムに対する条件は **SQL の意味** を持ちます。一致がないとカラムは NULL になり、条件は true にならず、その行は除かれます。
  Java では、存在しない `c` に対する `c.getName()` は `NullPointerException` を投げます。lambdaQL はこれをまねしません。
  一致しない行を残すには、例のように `c == null || ...` と書いてください。

<a id="sort-and-select"></a>
### ソートと select

**(テスト済み)** [`JoinQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java)、リージョン `sort`:

```java
OrderedJoinQuery<Order, Customer> q = qb.from(Order.class)
        .leftJoin(Customer.class, (o, c) -> o.getCustomer().getId() == c.getId())
        .orderBy((o, c, by) -> by.asc(c.getName()).asc(o.getId()));
assertEquals("select e0 from Order e0 left join Customer e1 on e0.customer.id = e1.id"
        + " order by e1.name asc nulls first, e0.id asc", q.toHql().hql());
```

[4.4](04-order-paging.md#44-null-ordering-is-the-same-on-every-database) の NULL の順序のルールが適用されます。顧客のない注文は `e1.name` が NULL なので、`asc` では先頭に来ます。

**(テスト済み)** [`JoinQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java)、リージョン `select`:

```java
ValueQuery<Row> rows = qb.from(Order.class)
        .leftJoin(Customer.class, (o, c) -> o.getCustomer().getId() == c.getId())
        .orderBy((o, c, by) -> by.asc(o.getId()))
        .select((o, c) -> new Row(o.getId(), c.getName()));
assertEquals("select e0.id, e1.name from Order e0 left join Customer e1 on e0.customer.id = e1.id"
        + " order by e0.id asc", rows.toHql().hql());
```

`Row` は `record Row(Long orderId, String customerName)` です。顧客のない注文では、`customerName` は `null` です。
`select` は `ValueQuery` を返すので、`distinct()`、`limit`、`offset` と [6.7](#67-distinct-paging-and-results) の結果のメソッドが使えます。

<a id="three-entities-and-repeated-rows"></a>
### 3 つのエンティティと繰り返される行

結合できるエンティティは 3 つまでです。3 つ目の結合のラムダは 3 つすべてを受け取ります: `(o, c, i) -> ...`。

**(テスト済み)** [`JoinQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java)、リージョン `three`:

```java
// orders of a customer with an item of quantity > 10; an order repeats once per matching item
SelectJoin3Query<Order, Customer, Item> q = qb.from(Order.class)
        .join(Customer.class, (o, c) -> o.getCustomer().getId() == c.getId())
        .join(Item.class, (o, c, i) -> i.getOrder().getId() == o.getId())
        .where((o, c, i) -> i.getQty() > 10);
assertEquals("select e0 from Order e0 join Customer e1 on e0.customer.id = e1.id"
        + " join Item e2 on e2.order.id = e0.id where e2.qty > 10", q.toHql().hql());
```

> [!WARNING]
> 結合が複数の行に一致すると、**主エンティティの行が繰り返されます**。一致ごとに 1 回です。`toList()` と `count()` は繰り返しもすべて数えます（テスト済み）。
> 各注文を 1 回だけ得たい場合は、結合の代わりに `exists()` のサブクエリ（[5. サブクエリ](05-subquery.md)）を使うか、`distinct()` で値を選択してください。

<a id="cross-join"></a>
### クロス結合

`crossJoin` には結合の条件がありません。名前が「すべての行の組み合わせが欲しい」という意図を示します。条件は `where` に書きます。

**(テスト済み)** [`JoinQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java)、リージョン `cross-join`:

```java
// every pair of rows; the conditions go in where
SelectJoinQuery<Order, Customer> q = qb.from(Order.class)
        .crossJoin(Customer.class)
        .where((o, c) -> c.getGrade() != null && o.getPrice() > 100);
assertEquals("select e0 from Order e0 cross join Customer e1 where e1.grade is not null and e0.price > 100.0",
        q.toHql().hql());
```

lambdaQL には `from(Order.class, Customer.class)`（カンマ結合）はありません。条件がないと間違ってすべての組み合わせを返し、どのエンティティが結果なのかもはっきりしないからです（決定 D45）。その用途には `crossJoin` を使ってください。

<a id="paging"></a>
### ページング

ページングでは、lambdaQL は **すべての** エンティティの id を最後のソートキーとして追加するので、ページが重なることはありません（ルール S9）。

**(テスト済み)** [`JoinQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java)、リージョン `cross-join-paging`:

```java
// with paging, the id of every entity is added to the order, so pages never overlap (S9)
ValueQuery<Row> pairs = qb.from(Order.class)
        .crossJoin(Customer.class)
        .orderBy((o, c, by) -> by.desc(o.getPrice()))
        .select((o, c) -> new Row(o.getId(), c.getName()));
assertEquals("select e0.id, e1.name from Order e0 cross join Customer e1 order by e0.price desc, e0.id asc, e1.id asc",
        pairs.limit(5).toHql().hql());
```

<a id="what-a-join-query-has"></a>
### 結合クエリが持つもの

| 呼び出しの後 | メソッド |
|---|---|
| `join`、`leftJoin`、`crossJoin` | `where`、`join` / `leftJoin` / `crossJoin`（エンティティ 3 つまで）、`orderBy`、`select`、`limit`、`toList`、`toSet`、`first`、`count`、`exists`、`toHql` |
| `orderBy((o, c, by) -> ...)` | `select`、`limit`、`offset`、`toList`、`first`、そのほかソートしたクエリのメソッド（[4. ソートとページング](04-order-paging.md)） |

v0.1 にはまだないもの: update、delete、insert の中の結合、サブクエリの中の結合、4 つ以上のエンティティ、`SortSpec` の結合の形、派生テーブルとの結合。

---

<a id="69-derived-table-subquery-in-from"></a>
## 6.9 派生テーブル（FROM の中のサブクエリ）

`select` で値のクエリ（record、[6.3](#63-select-a-record-or-dto) を参照）を作ります。そして、それを `qb.from(...)` に渡します。
その行が外側のクエリのテーブルになります（決定 D46）。
結果は `DerivedQuery<R>` です。`R` は行の型です。

**(テスト済み)** [`DerivedQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DerivedQueryTest.java)、リージョン `derived`:

```java
// the rows of a group query are the table of the outer query; r.total() reads the column "total"
DerivedQuery<Stat> q = qb.from(qb.from(Order.class)
                .where(o -> o.getCustomer() != null)
                .groupBy(o -> o.getCustomer().getId())
                .select(o -> new Stat(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice()))))
        .where(r -> r.total() > 100)
        .orderBy((r, by) -> by.desc(r.total()));
assertEquals("select e0.customerId, e0.cnt, e0.total from (select d0.customer.id as customerId, count(*) as cnt,"
        + " sum(d0.price) as total from Order d0 where d0.customer is not null group by d0.customer.id) e0"
        + " where e0.total > 100.0 order by e0.total desc nulls last", q.toHql().hql());
```

このテストの `Stat` は `record Stat(Integer customerId, Long cnt, Double total)` です。
テストはクエリを H2、MariaDB、Oracle で実行し、行と `count()` を Java での同じ計算と比べます。

- 内側のクエリはエイリアス `d0`（その結合は `dj1`、...）を使います。外側のクエリは `e0` を使います。
- record の各コンポーネントがカラム名になります: `as customerId`、`as cnt`、`as total`（ルール J5）。
- 外側のラムダでは、record のアクセサーでカラムを読みます: `r.total()` は `e0.total` です。
- 外側のクエリには `where`、`orderBy`、`limit`、`offset`、`select`、`toList`、`first`、`count`、`toHql` があります。

行の値を 1 つだけ選択することもできます。内側と外側のキャプチャ値は順番にバインドされます。

**(テスト済み)** [`DerivedQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DerivedQueryTest.java)、リージョン `select`:

```java
// a value of the row; captured values inside and outside are bound in order
ValueQuery<Integer> ids = qb.from(qb.from(Order.class)
                .where(o -> o.getCustomer() != null && o.getPrice() > min)
                .groupBy(o -> o.getCustomer().getId())
                .select(o -> new Stat(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice()))))
        .where(r -> r.total() > min * 2)
        .select(r -> r.customerId());
```

内側のクエリの `limit` と `offset` は内側のクエリに残ります（HQL の `limit` と `offset`）。そのため、上位の行をもう一度ソートできます。

**(テスト済み)** [`DerivedQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DerivedQueryTest.java)、リージョン `inner-limit`:

```java
// the three most expensive orders, then the cheapest of them
Optional<Priced> cheapestOfTop = qb.from(qb.from(Order.class)
                .orderBy((o, by) -> by.desc(o.getPrice()))
                .limit(3)
                .select(o -> new Priced(o.getId(), o.getPrice())))
        .orderBy((r, by) -> by.asc(r.price()))
        .first();
```

`Priced` は `record Priced(Long id, Double price)` です。

**SQL ブロックについて。** [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java) は同じ形のより単純な HQL を Hibernate に直接渡し、H2 2.3、MariaDB 10.11、Oracle 23.26（Hibernate 6.6.5）での SQL を記録します。Hibernate の SQL が変わるとテストは失敗します。各データベースが派生テーブルをどう書くかが分かります。

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

- 行の型は **Java の record** でなければなりません（Kotlin の data class も使えます）（ルール J5）。
  lambdaQL は record のコンポーネントから、順番にカラム名を取ります。
- 通常の DTO クラスや値 1 つは、ここでは使えません。lambdaQL がカラムに名前を付けられないからです。lambdaQL は `TranslationException`（`LQL-3064`）を投げます。

**(テスト済み)** [`DerivedQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DerivedQueryTest.java)、リージョン `rules`:

```java
// J5: the row type must be a record or Kotlin data class (its components name the columns)
assertEquals("LQL-3064", assertThrows(LambdaQLException.class,
        () -> qb.from(qb.from(Order.class).select(o -> o.getPrice()))).code());
```

v0.1 にはまだないもの: 2 つの派生テーブルの結合、派生テーブルとエンティティの結合、update、delete、insert の中の派生テーブル。

---

<a id="610-cte-with"></a>
## 6.10 CTE（`with`）

`qb.with(valueQuery)` で `Cte<R>` 型のハンドルを作ります。そして `qb.from(handle)` でそれを読みます。
内側のクエリは派生テーブル（6.9）と同じです。

**(テスト済み)** [`DerivedQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DerivedQueryTest.java)、リージョン `cte`:

```java
Cte<Stat> t = qb.with(perCustomer());
DerivedQuery<Stat> q = qb.from(t).where(r -> r.cnt() >= 2);
assertEquals("with t as (select d0.customer.id as customerId, count(*) as cnt, sum(d0.price) as total"
        + " from Order d0 where d0.customer is not null group by d0.customer.id)"
        + " select e0.customerId, e0.cnt, e0.total from t e0 where e0.cnt >= 2L", q.toHql().hql());
```

`perCustomer()` はテストの中のヘルパーです。[6.9](#69-derived-table-subquery-in-from) の内側のクエリと同じ値のクエリを返します。

```java
private ValueQuery<Stat> perCustomer() {
    return qb.from(Order.class)
            .where(o -> o.getCustomer() != null)
            .groupBy(o -> o.getCustomer().getId())
            .select(o -> new Stat(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice())));
}
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
- CTE のカラム名は select のエイリアス（`as customerId`）で付けます。HQL は CTE 名の後のカラムリストを受け付けません。カラムリストは Hibernate が SQL に追加します（ルール J6）。
- MariaDB と Oracle は本物の `with` を実行します。H2 では、Hibernate がそれを派生テーブルに展開します。結果は同じです（3 つのデータベースでテスト済み）。
- 行の型のルールは派生テーブルと同じで、record です（J5）。
- v0.1 にはまだないもの: 1 つのクエリで同じ CTE を 2 回使うこと、update、delete、insert の中の CTE。

---

<a id="611-rules-j1-to-j8"></a>
## 6.11 ルール J1〜J8

| # | ルール | 状況 |
|---|---|---|
| J1 | `select` のラムダは **1 つの値の式** または **1 つのコンストラクター呼び出し** を返す。コンストラクターの各引数は値の式（ルールは [3. 条件](03-where.md)）。`select` だけで使う関連のパスは `left join` のエイリアスになるので、行は失われない。同じパスを `where` が使うと、`select` は `where` の結合を使う（R24）。 | 実装済み（テスト済み） |
| J2 | コンストラクターの場合、lambdaQL はカラムだけを選択し、コンストラクターを Java で呼び出す。`select new` は書かない。 | 実装済み（テスト済み） |
| J3 | 集約がある場合、`select` と `having`（と `insert ... select` の値）の中の集約でない値はすべて `groupBy` のキーでなければならない。そうでなければ、解析時にエラー。 | 実装済み（テスト済み）。エラーは `TranslationException`（`LQL-3035`、[11](../11-errors-and-languages.md) を参照） |
| J4 | 行がない、またはすべての値が NULL のとき、`sum`/`avg`/`min`/`max` は NULL を返す。`count` は 0 を返す。NULL になりうる値にはボクシング型を使う。プリミティブのコンストラクター引数に NULL が入ると `IllegalStateException` を投げ、その引数を示す。 | 実装済み（テスト済み） |
| J5 | 派生テーブルまたは CTE の行の型: **Java の record**（Kotlin のコードでは data class も使える）。通常の DTO クラスは単純な `select` でだけ使える。そうでなければ `LQL-3064`。 | 実装済み（テスト済み） |
| J6 | `qb.with(...)` で作った CTE は、select のエイリアスでカラム名を付ける: `with t as (select d0.customer.id as customerId, ...) select e0.customerId ... from t e0`。HQL は名前の後のカラムリストを受け付けない。Hibernate は `with t (col list) as (...)` を `SyntaxException` で拒否する。カラムリストは Hibernate 自身が SQL に書く（MariaDB と Oracle で `with t (customerId,cnt,total) as (...)`）。H2 は CTE を派生テーブルに変える。外側のクエリでは必ずエイリアスを使う（`from t e0`）。 | 実装済み（テスト済み） |
| J7 | `distinct()` は `select distinct` を出力する。ソートと一緒に使う場合、ソートキーは select リストに含まれていなければならない（DB のルール）。lambdaQL は解析時にこれをチェックする。 | 実装済み（テスト済み） |
| J8 | 選択は、ソート（[4. ソートとページング](04-order-paging.md)）、ページング、サブクエリ（[5. サブクエリ](05-subquery.md)）と一緒に使える。集約によるソート（`by.desc(s.total())`）は v0.2 の候補。 | 実装済み（集約によるソートを除く） |

**(テスト済み)** [`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java)、リージョン `rules`:

```java
// J3: a value without an aggregate must be a group key
LambdaQLException j3 = assertThrows(LambdaQLException.class, () -> qb.from(Order.class)
        .groupBy(o -> o.getCustomer().getId())
        .select(o -> new KeyCount(o.getCustomer().getId(), o.getStatus(), Agg.count())));
assertEquals("LQL-3035", j3.code());
// aggregates belong in having, not in where
LambdaQLException where = assertThrows(LambdaQLException.class, () -> qb.from(Order.class).where(o -> Agg.count() > 1));
assertEquals("LQL-3005", where.code());
// J7: with distinct, the sort key must be selected
LambdaQLException j7 = assertThrows(LambdaQLException.class, () -> qb.from(Order.class)
        .orderBy((o, by) -> by.asc(o.getId())).select(o -> o.getPrice()).distinct().toHql());
assertEquals("LQL-3046", j7.code());
// J4: the group without orders gives NULL; a primitive argument cannot take it
assertThrows(IllegalStateException.class, () -> qb.from(Order.class)
        .where(o -> o.getStatus() == 9)
        .select(o -> new Bad(null, Agg.sum(o.getPrice()))).toList());
```

`Bad` は `record Bad(Integer customerId, double total)` です。ステータスが 9 の注文はないので `sum` は NULL になり、プリミティブの `double` はそれを受け取れません。

---

<a id="612-not-in-v01"></a>
## 6.12 v0.1 に含まれないもの

| 項目 | 状況 |
|---|---|
| 集約の結果や select のエイリアスによるソート（たとえば `orderBy((s, by) -> by.desc(s.total()))`）と、グループ化したクエリの `orderBy` | v0.2 の候補 |
| ユーザー定義の集約関数とウィンドウ関数 | v0.2 の候補 |
| update、delete、insert の中の結合、サブクエリの中の結合、4 つ以上のエンティティ、派生テーブルとの結合 | 今後 |
| 1 つのクエリで同じ CTE を 2 回使うこと、DML の中の派生テーブルと CTE | 今後 |

この章のエラーは、コードを持つ `TranslationException` です: `LQL-3035`（間違ったグループのキー、J3）、`LQL-3005`（`where` の中の `Agg`）、`LQL-3046`（`distinct` で選択していないソートキー、J7）、`LQL-3064`（派生テーブルまたは CTE の行の型、J5）。[11. エラーと言語](../11-errors-and-languages.md) を参照してください。

---

[Java トラック](README.md) | 前: [5. サブクエリ](05-subquery.md) | 次: [7. 更新、挿入、削除](07-dml.md) | [このページの Kotlin 版](../kotlin/06-select-group.md)
