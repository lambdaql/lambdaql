<a id="6-select-aggregates-and-group-by-java"></a>
# 6. 選択、集約、グループ化（Java）

> [!IMPORTANT]
> このページは韓国語版のマニュアルより古い内容です。最新のルールは[韓国語のページ](../../ko/java/06-select-group.md)を参照してください。

[Java トラック](README.md) | [マニュアルのホーム](../README.md) | 前: [5. サブクエリ](05-subquery.md) | 次: [7. 更新、挿入、削除](07-dml.md) | [このページの Kotlin 版](../kotlin/06-select-group.md)

共通ページ: [1. 概要](../01-overview.md) · [11. エラーと言語](../11-errors-and-languages.md)

---

この章では、クエリが **何を** 返すかを選ぶ方法を説明します。

- 行ごとに 1 つの値（`select(o -> o.getPrice())`）
- Java の record や DTO クラス
- `groupBy`、`having` と一緒に使う集約（`count`、`sum`、`avg`、...）
- 値クエリの `distinct`、ページング、結果メソッド
- 他のエンティティとの明示的な結合（`join`、`leftJoin`、`crossJoin`）
- FROM 句のサブクエリ（派生テーブル）と CTE（`with`）

<a id="contents"></a>
## 目次

- [6.1 例で使うエンティティと結果の型](#61-example-entities-and-result-types)
- [6.2 1 つの値の選択](#62-select-one-value)
- [6.3 record または DTO の選択](#63-select-a-record-or-dto)
- [6.4 集約関数: `Agg`](#64-aggregate-functions-agg)
- [6.5 グループ化（group by）](#65-group-by)
- [6.6 having](#66-having)
- [6.7 distinct、ページング、結果](#67-distinct-paging-and-results)
- [6.8 明示的な結合](#68-explicit-joins)
- [6.9 派生テーブル（FROM の中のサブクエリ）](#69-derived-table-subquery-in-from)
- [6.10 CTE（`with`）](#610-cte-with)
- [6.11 ルール J1〜J8](#611-rules-j1-to-j8)
- [6.12 サポートしないもの](#612-not-in-v01)

---

<a id="61-example-entities-and-result-types"></a>
## 6.1 例で使うエンティティと結果の型

例では次のエンティティを使います（[2. はじめよう](02-getting-started.md#example-entities) を参照）。

| エンティティ | ここで使うフィールド |
|---|---|
| `Order` | `id`、`price`、`status`、`active`、`customer`（many-to-one） |
| `Customer` | `id`、`name`、`grade` |
| `Item` | `order`、`price`、`qty` |

`price` は `double`、`status` は `short`、`Customer.id` は `int` です。例のデータには、顧客のない注文が 1 つあります。
この章の例は、H2、MariaDB、Oracle で、同じ計算を Java で行った結果と同じ値を返します。

例では次の結果の型を使います。

- `record OrderView(Long id, String customerName, double price)`
- `record Stat(Integer customerId, long count, Double total)`
- `record KeyCount(Integer customerId, short status, long count)`
- `record Total(long count, Double average)`

一部の例（[`ManualJavaSelectTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaSelectTest.java)）は、同じ名前の `Stat`、`Total` の record を別に宣言して使います。

> [!TIP]
> NULL になりうる値には、ボクシング型（`Double`、`Long`、`Integer`）を使ってください。
> カラムや `sum` のような集約は NULL になりえます。プリミティブの `double` の引数は NULL を保持できず、このとき lambdaQL は `IllegalStateException` を投げます（[6.4](#null-in-aggregates-rule-j4) を参照）。
> `count` は NULL にならないので、`long count` を使っても構いません。

---

<a id="62-select-one-value"></a>
## 6.2 1 つの値の選択

`select` は 1 つのラムダ式を受け取ります。ラムダ式は 1 つの値を返します。結果はその値の `ValueQuery` です。
`select` は `where` の後と `orderBy` の後で呼び出せます。ソート、`limit`、`offset` はそのまま維持されます。

<!-- region: ProjectionTest.java#value -->
```java
ValueQuery<Double> q = qb.from(Order.class)
        .where(o -> o.getStatus() == 1)
        .orderBy(o -> asc(o.getPrice()))
        .select(o -> o.getPrice());
assertEquals("select e0.price from Order e0 where e0.status = 1 order by e0.price asc", q.toHql().hql());
```

**データベースごとの SQL**

<!-- sql: value -->
```sql
-- H2, MariaDB, Oracle (same)
select o1_0.price
from orders o1_0
where o1_0.status=1
order by o1_0.price
```

関連のパスは、`where` と同じくカラムです。`select` では lambdaQL が `left join` を追加するので、顧客のない注文も抜けません（`where` では、顧客のない注文が条件に合いえない場合、同じパスは inner join です）。
`optional = false` でマッピングした必須の関連は行を失わないので、`select` でも inner join です（[4.5](04-order-paging.md#45-what-a-sort-key-can-be) を参照）。

<!-- region: ManualJavaSelectTest.java#select-path -->
```java
ValueQuery<String> names = qb.from(Order.class).select(o -> o.getCustomer().getName());
// a path used only in select is a left join: the order without a customer gives null
assertEquals("select j1.name from Order e0 left join e0.customer j1", names.toHql().hql());
```

[6.3](#63-select-a-record-or-dto) の record の例も、この `left join` を使います。
パスが関連エンティティの `@Id` で終わる場合は結合がありません。
`o.getCustomer().getId()` は外部キーのカラム `customer_id` を読みます。

コード例: [`ProjectionTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java)（`value`）、[`ManualJavaSelectTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaSelectTest.java)（`select-path`）。

---

<a id="63-select-a-record-or-dto"></a>
## 6.3 record または DTO の選択

ラムダ式の中でコンストラクターを呼び出してください。コンストラクターの各引数は値の式です。
Java の record と、コンストラクターを持つ通常の DTO クラスのどちらも使えます。

<!-- region: ProjectionTest.java#record -->
```java
// only the columns are selected; the record is made in Java. The path uses a left join,
// so the order without a customer stays (customerName is null)
ValueQuery<OrderView> q = qb.from(Order.class)
        .select(o -> new OrderView(o.getId(), o.getCustomer().getName(), o.getPrice()));
assertEquals("select e0.id, j1.name, e0.price from Order e0 left join e0.customer j1", q.toHql().hql());
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
カラムを選択し、コンストラクターは Java で呼び出します。
こうすることで、長いクラス名や入れ子のクラス名の問題を避けられます。
lambdaQL は各値をコンストラクターの引数の型に変換します（例: Hibernate の `Long` を `Integer` の引数へ）。

返せるもの:

| 結果の型 | 単純な `select` | 派生テーブルや CTE の行の型（[6.9](#69-derived-table-subquery-in-from)） |
|---|---|---|
| 1 つの値（`o.getPrice()`） | 可 | – |
| Java の record | 可 | 可 |
| 通常の DTO クラス（コンストラクター） | 可 | **不可** |

コード例: [`ProjectionTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java)（`record`）。

---

<a id="64-aggregate-functions-agg"></a>
## 6.4 集約関数: `Agg`

集約関数は **`org.lambdaql.Agg` の static メソッド** です。
SQL 標準の関数なので、lambdaQL が提供します。（独自に作った DB 関数は [8. プロシージャと DB 関数](08-procedures-functions.md) を参照してください。）

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

- `Agg.count()`: `long`
- `Agg.sum(x)`: `<N extends Number> N`（引数と同じ型）
- `Agg.avg(x)`: `Double`
- `Agg.min(x)`、`Agg.max(x)`: `<R extends Comparable<?>> R`（引数と同じ型）

重要な点:

- 戻り値は **プレースホルダー** にすぎません。lambdaQL は `Agg` の呼び出しを実行せず SQL の集約に移すので、この値は使われません。
- lambdaQL のラムダ式の **外で** `Agg.sum(...)` を呼び出すと、`IllegalStateException` を投げます。
- `Agg` は `select`、`having`、`insert ... select` の値（[7.5](07-5-insert.md)）で使えます。
- `where` では `Agg` を使えません。集約に対する条件には `having` を使ってください。`where` で `Agg` を使うと、解析時にエラーになります。

<a id="aggregate-over-all-rows-no-group-by"></a>
### すべての行に対する集約（group by なし）

<!-- region: ProjectionTest.java#total -->
```java
// aggregates without groupBy: one row over all matching orders
ValueQuery<Total> q = qb.from(Order.class)
        .where(o -> o.getStatus() == 1)
        .select(o -> new Total(Agg.count(), Agg.avg(o.getPrice())));
assertEquals("select count(*), avg(e0.price) from Order e0 where e0.status = 1", q.toHql().hql());
```

**データベースごとの SQL**

<!-- sql: total -->
```sql
-- H2, MariaDB, Oracle (same)
select count(*),avg(o1_0.price)
from orders o1_0
where o1_0.status=1
```

このクエリは常に 1 行を返します。`q.one()` でこの行を読めます。

<a id="null-in-aggregates-rule-j4"></a>
### 集約での NULL（ルール J4）

| 関数 | 行がない、またはすべての値が NULL のとき |
|---|---|
| `sum`、`avg`、`min`、`max` | `NULL` |
| `count` | `0` |

これは通常の SQL の動作です。
NULL がプリミティブのコンストラクター引数（`double`、`long`）に入ると、lambdaQL は `IllegalStateException` を投げます。メッセージには、該当する引数と、ラッパー型（`Double`、`Long`）を使うようにという案内が含まれます。例は [6.11](#611-rules-j1-to-j8) にあります。

コード例: [`ProjectionTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java)（`total`）。

---

<a id="65-group-by"></a>
## 6.5 グループ化（group by）

グループのキーは `groupBy` で書きます。`select` の値からグループのキーを自動で決めることはありません。
`groupBy` はグループクエリを返します。`having(...)`（任意）を呼び出してから、`select(...)` を呼び出してください。

<a id="one-key"></a>
### キーが 1 つ

<!-- region: ManualJavaSelectTest.java#group-one -->
```java
ValueQuery<Stat> q = qb.from(Order.class)
        .groupBy(o -> o.getCustomer().getId())
        .select(o -> new Stat(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice())));
assertEquals("select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id",
        q.toHql().hql());
List<Stat> stats = q.toList();
```

キーが `@Id` で終わるので結合はありません。顧客のない注文は、キーが NULL のグループ 1 つになります。
`having` を使う例は [6.6](#66-having) にあります。

<a id="many-keys"></a>
### キーが複数

`List.of(...)` でキーのリストを返してください。

<!-- region: ProjectionTest.java#group-many -->
```java
// many keys: List.of(...) (Kotlin listOf(...), a to b, Triple)
ValueQuery<KeyCount> q = qb.from(Order.class)
        .where(o -> o.getCustomer() != null)
        .groupBy(o -> List.of(o.getCustomer().getId(), o.getStatus()))
        .select(o -> new KeyCount(o.getCustomer().getId(), o.getStatus(), Agg.count()));
assertEquals("select e0.customer.id, e0.status, count(*) from Order e0 where e0.customer is not null"
        + " group by e0.customer.id, e0.status", q.toHql().hql());
```

**データベースごとの SQL**

<!-- sql: group-many -->
```sql
-- H2, MariaDB, Oracle (same)
select o1_0.customer_id,o1_0.status,count(*)
from orders o1_0
where o1_0.customer_id is not null
group by o1_0.customer_id,o1_0.status
```

キーは書いた順に使われます。
ラムダ式の中で作ったコレクションが許される唯一の場所です。

キーのリストとして許されないもの:

| 形 | 理由 |
|---|---|
| `Set.of(a, b)` | 集合は重複を取り除き、順序がない |

<a id="every-non-aggregate-value-must-be-a-key-rule-j3"></a>
### 集約でない値はすべてキーであること（ルール J3）

クエリに集約があれば、`select` と `having` で集約 **でない** 値はすべて `groupBy` のキーでなければなりません。これは SQL のルールです。lambdaQL は解析時にこれを検査します。

たとえば、`groupBy(o -> o.getCustomer().getId())` の後に、グループのキーでない `o.getStatus()` を `select` に書くと、`TranslationException`（`LQL-3035`）が発生します。

例は [6.11](#611-rules-j1-to-j8) にあります。

コード例: [`ManualJavaSelectTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaSelectTest.java)（`group-one`）、[`ProjectionTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java)（`group-many`）。

---

<a id="66-having"></a>
## 6.6 having

`having` はグループに対する条件です。`where` と同じラムダ式のルール（[3. 条件（`where`）](03-where.md)）を使い、`Agg` を使えます。
`having` は複数回呼び出せます。条件は `AND` でつながります。

<!-- region: ProjectionTest.java#group-by -->
```java
ValueQuery<Stat> q = qb.from(Order.class)
        .groupBy(o -> o.getCustomer().getId())
        .having(o -> Agg.sum(o.getPrice()) > 200)
        .select(o -> new Stat(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice())));
assertEquals("select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id"
        + " having sum(e0.price) > 200.0", q.toHql().hql());
```

**データベースごとの SQL**

<!-- sql: group-by -->
```sql
-- H2, MariaDB, Oracle (same)
select o1_0.customer_id,count(*),sum(o1_0.price)
from orders o1_0
group by o1_0.customer_id
having sum(o1_0.price)>200.0
```

ここで `Agg.sum(o.getPrice())` は `double` なので、javac はこれを `200.0` と比較します。定数はリテラルのまま残ります。

コード例: [`ProjectionTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java)（`group-by`）。

---

<a id="67-distinct-paging-and-results"></a>
## 6.7 distinct、ページング、結果

`select(...)` は `ValueQuery` を返します。`ValueQuery` のメソッドは次のとおりです。

| メソッド | 動作 |
|---|---|
| `distinct()` | `select distinct` |
| `limit(n)`、`offset(n)` | ページング |
| `toList()` | クエリを実行し、値を順に返す |
| `toSet()` | `select distinct` でクエリを実行し、順序のある `SequencedSet` を返す |
| `first()` | 最初の値、または `Optional.empty()` |
| `one()` | 唯一の値、または `Optional.empty()`。行が 2 つ以上なら `NonUniqueResultException` |
| `toHql()` | クエリを実行せずに HQL とパラメーターを返す |

<!-- region: ProjectionTest.java#distinct -->
```java
ValueQuery<Double> q = qb.from(Order.class)
        .orderBy(o -> desc(o.getPrice()))
        .select(o -> o.getPrice())
        .distinct();
assertEquals("select distinct e0.price from Order e0 order by e0.price desc", q.toHql().hql());
// toSet() always uses select distinct
List<Double> prices = q.toSet().stream().toList();
```

**データベースごとの SQL**

<!-- sql: distinct -->
```sql
-- H2, MariaDB, Oracle (same)
select distinct o1_0.price
from orders o1_0
order by o1_0.price desc
```

- `distinct()` を使うと、すべてのソートキーが select の値でなければなりません。そうでなければ、lambdaQL は解析時に `TranslationException`（`LQL-3046`）を投げます。
- 値クエリの `toSet()` は常に `select distinct` を使います。ここでは値の繰り返しが正常なので、サイズの検査をしません（エンティティの `toSet()` とは異なる、[2. はじめよう](02-getting-started.md#toset) を参照）。
- `@Lob` カラムを `distinct` や `groupBy` のキーに使うと、Oracle は拒否します（ORA-22848、ORA-22849）。H2 と MariaDB は実行します。lambdaQL はクエリを変えず、`LQL-5010` を `DEBUG` レベルで記録します（[4.5](04-order-paging.md#45-what-a-sort-key-can-be) を参照）。

**ページング。** `limit` と `offset` は [4. ソートとページング](04-order-paging.md) と同じように動作します。
ページングを使うと、lambdaQL が最後のソートキーとして `id` を追加するので、ページが重なりません。ソートキーが一意で NULL になりえないカラムなら追加しません（[4.8](04-order-paging.md#tie-breaker-automatic-id) を参照）。
`first()` も 1 行だけを読むので、同じ補助のソートを追加します。ソートキーの値が同じ行が複数あれば、毎回 `id` が最も小さい行の値を返します。
グループクエリと `distinct` のクエリには追加しません。これらのクエリの select リストには `id` がないからです。

<!-- region: ManualJavaSelectTest.java#value-page -->
```java
ValueQuery<Double> paged = qb.from(Order.class)
        .orderBy(o -> desc(o.getPrice()))
        .select(o -> o.getPrice())
        .limit(2).offset(1);
// paging adds the id as the last sort key
assertEquals("select e0.price from Order e0 order by e0.price desc, e0.id asc", paged.toHql().hql());
List<Double> page = paged.toList();
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

コード例: [`ProjectionTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java)（`distinct`）、[`ManualJavaSelectTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaSelectTest.java)（`value-page`）。

---

<a id="68-explicit-joins"></a>
## 6.8 明示的な結合

関連のパス（`o.getCustomer().getName()`）は、to-one の関連を自動で結合します（[3.10](03-where.md#310-association-paths) を参照）。
他のエンティティを自分で決めた条件で結合する場合や、条件なしで結合する場合は、`join`、`leftJoin`、`crossJoin` を使ってください。

| メソッド | HQL | 行 |
|---|---|---|
| `join(Customer.class, (o, c) -> ...)` | `join Customer e1 on ...` | inner join: 対になる行があるものだけ |
| `leftJoin(Customer.class, (o, c) -> ...)` | `left join Customer e1 on ...` | 対のない主エンティティの行も残る |
| `crossJoin(Customer.class)` | `cross join Customer e1` | すべての行の組み合わせ。条件は `where` に書く |

結合の後のすべてのラムダ式は、結合の順にすべてのエンティティを受け取ります。
`where((o, c) -> ...)`、`orderBy((o, c) -> asc(c.getName()).desc(o.getPrice()))`、`select((o, c) -> ...)` です。
結合したエンティティのエイリアスは `e1`、`e2` です。

下の例は、H2、MariaDB、Oracle で、同じ計算を Java で行った結果と同じ行を返します。

<!-- region: JoinQueryTest.java#join -->
```java
SelectJoinQuery<Order, Customer> q = qb.from(Order.class)
        .join(Customer.class, (o, c) -> o.getCustomer().getId() == c.getId())
        .where((o, c) -> c.getGrade() != null && c.getGrade() >= 5);
assertEquals("select e0 from Order e0 join Customer e1 on e0.customer.id = e1.id"
        + " where e1.grade is not null and e1.grade >= 5", q.toHql().hql());
```

**結果は主エンティティです。** `select` がなければ、`toList()` は `List<Order>` を返します。
結合したエンティティは、条件、ソート、`select` にだけ使います。
結合したエンティティの値を読むには、`select((o, c) -> ...)` を使ってください（下記を参照）。

<a id="left-join-and-null"></a>
### left join と NULL

<!-- region: JoinQueryTest.java#left-join -->
```java
// c is missing for an order without a customer: c == null finds those rows
SelectJoinQuery<Order, Customer> q = qb.from(Order.class)
        .leftJoin(Customer.class, (o, c) -> o.getCustomer().getId() == c.getId())
        .where((o, c) -> c == null || c.getName().equals("Kim"));
assertEquals("select e0 from Order e0 left join Customer e1 on e0.customer.id = e1.id"
        + " where e1.id is null or e1.name = 'Kim'", q.toHql().hql());
```

- `c == null` は「対がない」という意味です。lambdaQL はこれを `e1.id is null`（結合したエンティティの `@Id`）と書きます。
- `leftJoin` の後に結合したエンティティのカラムにかける条件は、**SQL の意味** に従います。対がなければカラムは NULL なので条件は真にならず、その行は結果から抜けます。
  Java では存在しない `c` に `c.getName()` を呼び出すと `NullPointerException` が発生しますが、lambdaQL はこの動作に従いません。
  対のない行を残すには、例のように `c == null || ...` と書いてください。

<a id="sort-and-select"></a>
### ソートと select

<!-- region: JoinQueryTest.java#sort -->
```java
OrderedJoinQuery<Order, Customer> q = qb.from(Order.class)
        .leftJoin(Customer.class, (o, c) -> o.getCustomer().getId() == c.getId())
        .orderBy((o, c) -> asc(c.getName()).asc(o.getId()));
assertEquals("select e0 from Order e0 left join Customer e1 on e0.customer.id = e1.id"
        + " order by e1.name asc nulls first, e0.id asc", q.toHql().hql());
```

[4.4](04-order-paging.md#44-null-ordering-is-the-same-on-every-database) の NULL のソートのルールが適用されます。顧客のない注文は `e1.name` が NULL なので、`asc` では先頭に来ます。

<!-- region: JoinQueryTest.java#select -->
```java
ValueQuery<Row> rows = qb.from(Order.class)
        .leftJoin(Customer.class, (o, c) -> o.getCustomer().getId() == c.getId())
        .orderBy((o, c) -> asc(o.getId()))
        .select((o, c) -> new Row(o.getId(), c.getName()));
assertEquals("select e0.id, e1.name from Order e0 left join Customer e1 on e0.customer.id = e1.id"
        + " order by e0.id asc", rows.toHql().hql());
```

`Row` は `record Row(Long orderId, String customerName)` です。顧客のない注文は `customerName` が `null` です。
`select` は `ValueQuery` を返すので、`distinct()`、`limit`、`offset`、[6.7](#67-distinct-paging-and-results) の結果メソッドを使えます。

<a id="select-entities"></a>
### エンティティの選択

`select` にはエンティティそのものも書けます。結合したエンティティを選んだり、複数のエンティティを 1 つの record にまとめたりできます。

<!-- region: SelectEntityTest.java#select-joined -->
```java
// the customers that have an order, through an explicit join; Hibernate returns each entity once
ValueQuery<Customer> q = qb.from(Order.class)
        .join(Customer.class, (o, c) -> o.getCustomer().getId() == c.getId())
        .select((o, c) -> c);
```

エンティティを 1 つ選ぶと、Hibernate は同じエンティティを 1 回だけ返します。HQL は `select e1 from Order e0 join Customer e1 on e0.customer.id = e1.id` です。

<!-- region: SelectEntityTest.java#select-entities -->
```java
// an order and its customer together; with leftJoin an order without a customer has null
ValueQuery<OrderWithCustomer> q = qb.from(Order.class)
        .leftJoin(Customer.class, (o, c) -> o.getCustomer().getId() == c.getId())
        .select((o, c) -> new OrderWithCustomer(o, c));
```

`OrderWithCustomer` は `record OrderWithCustomer(Order order, Customer customer)` です。HQL は `select e0, e1 from Order e0 left join Customer e1 on e0.customer.id = e1.id` で、注文ごとに 1 行です。

<!-- region: SelectEntityTest.java#select-self -->
```java
// o -> o selects the entity itself: the same rows as toList()
ValueQuery<Order> q = qb.from(Order.class).where(o -> o.getPrice() > 150).select(o -> o);
```

- `select(o -> o)` は、`select` なしで `toList()` を呼んだ場合と結果が同じです。
- エンティティを含む行は、派生テーブルや CTE の行として使えません（`LQL-3071`）。エンティティの id やカラムを選んでください。

CTE には値だけを入れ、外側のクエリでエンティティと結合してエンティティを選びます。

<!-- region: SelectEntityTest.java#select-entity-cte -->
```java
// the CTE has values only (customer id, total); the outer query joins it to the entity and selects the entity
Cte<Total> totals = qb.with(qb.from(Order.class)
        .where(o -> o.getCustomer() != null)
        .groupBy(o -> o.getCustomer().getId())
        .select(o -> new Total(o.getCustomer().getId(), Agg.sum(o.getPrice()))));
ValueQuery<Customer> big = qb.from(Customer.class)
        .join(totals, (c, t) -> t.customerId().equals(c.getId()))
        .where((c, t) -> t.total() > 250)
        .select((c, t) -> c);
```

コード例: [`SelectEntityTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectEntityTest.java)（`select-joined`、`select-entities`、`select-self`、`select-entity-cte`）。

<a id="three-entities-and-repeated-rows"></a>
### 複数のエンティティと繰り返される行

エンティティは 10 個まで結合できます（主エンティティと結合するエンティティ 9 個）。結合するたびに、ラムダ式が受け取るエンティティが 1 つずつ増えます: `(o, c, i) -> ...`。

<!-- region: JoinQueryTest.java#three -->
```java
// orders of a customer with an item of quantity > 10 (an order with two such items is two SQL rows)
SelectJoin3Query<Order, Customer, Item> q = qb.from(Order.class)
        .join(Customer.class, (o, c) -> o.getCustomer().getId() == c.getId())
        .join(Item.class, (o, c, i) -> i.getOrder().getId() == o.getId())
        .where((o, c, i) -> i.getQty() > 10);
assertEquals("select e0 from Order e0 join Customer e1 on e0.customer.id = e1.id"
        + " join Item e2 on e2.order.id = e0.id where e2.qty > 10", q.toHql().hql());
```

エンティティを 10 個結合した例です。

<!-- region: JoinQueryTest.java#ten -->
```java
// up to 10 entities: each lambda gets all of them in join order
SelectJoin10Query<Order, Customer, Item, Order, Order, Order, Order, Order, Order, Order> q = qb.from(Order.class)
        .join(Customer.class, (o, c) -> o.getCustomer().getId() == c.getId())
        .join(Item.class, (o, c, i) -> i.getOrder().getId() == o.getId())
        .join(Order.class, (o, c, i, a) -> a.getId() == o.getId())
        .join(Order.class, (o, c, i, a, b) -> b.getId() == a.getId())
        .join(Order.class, (o, c, i, a, b, d) -> d.getId() == b.getId())
        .join(Order.class, (o, c, i, a, b, d, e) -> e.getId() == d.getId())
        .join(Order.class, (o, c, i, a, b, d, e, f) -> f.getId() == e.getId())
        .join(Order.class, (o, c, i, a, b, d, e, f, g) -> g.getId() == f.getId())
        .leftJoin(Order.class, (o, c, i, a, b, d, e, f, g, h) -> h.getId() == g.getId())
        .where((o, c, i, a, b, d, e, f, g, h) -> i.getQty() > 10 && h.getPrice() > 0);
```

> [!WARNING]
> 結合条件に合う行が複数あると、SQL では **主エンティティの行が対ごとに繰り返されます**。`toList()` と `toSet()` は、Hibernate が同じエンティティを 1 回だけ返すので繰り返しがありません。
> しかし `count()` と `limit` は繰り返された SQL の行を数えます。注文の数を数えたりページを正確に埋めたりするには、結合の代わりに `exists()` のサブクエリ（[5. サブクエリ](05-subquery.md)）を使うか、結合のクエリに `distinct()` を適用してください（[結合結果の `distinct()`](#distinct-on-a-join) を参照）。

<a id="cross-join"></a>
### cross join

`crossJoin` には結合条件がありません。メソッド名で、すべての行の組み合わせが欲しいという意図を示します。条件は `where` に書いてください。

<!-- region: JoinQueryTest.java#cross-join -->
```java
// every pair of rows; the conditions go in where
SelectJoinQuery<Order, Customer> q = qb.from(Order.class)
        .crossJoin(Customer.class)
        .where((o, c) -> c.getGrade() != null && o.getPrice() > 100);
assertEquals("select e0 from Order e0 cross join Customer e1 where e1.grade is not null and e0.price > 100.0",
        q.toHql().hql());
```

lambdaQL には `from(Order.class, Customer.class)`（カンマ結合）がありません。条件を書き忘れると誤ってすべての組み合わせを得てしまい、どのエンティティが結果なのかもはっきりしないからです。そのような選択には `crossJoin` を使ってください。

<a id="paging"></a>
### ページング

ページングすると、lambdaQL は **すべての** エンティティの id を最後のソートキーとして追加します。そのため、ページが重なりません。

<!-- region: JoinQueryTest.java#cross-join-paging -->
```java
// with paging, the id of every entity is added to the order, so pages never overlap
ValueQuery<Row> pairs = qb.from(Order.class)
        .crossJoin(Customer.class)
        .orderBy((o, c) -> desc(o.getPrice()))
        .select((o, c) -> new Row(o.getId(), c.getName()));
assertEquals("select e0.id, e1.name from Order e0 cross join Customer e1 order by e0.price desc, e0.id asc, e1.id asc",
        pairs.limit(5).toHql().hql());
```

<a id="distinct-on-a-join"></a>
### 結合結果の `distinct()`

`SelectJoinQuery.distinct()` と `SelectJoin3Query.distinct()` は、主エンティティの行ごとに SQL の行を 1 つにします。HQL は `select distinct e0` で、`count()` は `count(distinct e0)` です。
`toList()` と `toSet()` は、`distinct()` がなくても主エンティティを 1 回ずつ返します。`count()` と `limit` に主エンティティの数を数えさせるには `distinct()` が必要です。

- ソートキーは主エンティティ自身のカラムでなければなりません（`LQL-3046`）。結合したエンティティのカラムや関連のパスでソートするとエラーです。
- ページングすると、主エンティティの id だけを同順位のキーとして追加します。結合したエンティティの id は付けません。
- `distinct()` の後に `select(...)` を使うと、値を `select distinct` で選択します。
- まず `exists` のサブクエリ（[5. サブクエリ](05-subquery.md)）を検討してください。繰り返しなしで同じ行を返し、より速いことが多いです。

<!-- region: JoinQueryTest.java#join-distinct -->
```java
// an order with two matching items is two SQL rows; distinct() makes it one row in SQL
SelectJoinQuery<Order, Item> q = qb.from(Order.class)
        .join(Item.class, (o, i) -> i.getOrder().getId() == o.getId())
        .where((o, i) -> i.getQty() > 0)
        .distinct();
assertEquals("select distinct e0 from Order e0 join Item e1 on e1.order.id = e0.id where e1.qty > 0", q.toHql().hql());
// a sort key must be a column of the main entity
OrderedJoinQuery<Order, Item> sorted = q.orderBy((o, i) -> desc(o.getPrice()));
assertEquals("select distinct e0 from Order e0 join Item e1 on e1.order.id = e0.id where e1.qty > 0"
        + " order by e0.price desc", sorted.toHql().hql());
```

`distinct()` なしで `toSet()` を使うと、結合したエンティティのカラムでもソートできます。ただし `count()` は SQL の行を数えます。

<!-- region: JoinQueryTest.java#join-set -->
```java
SelectJoinQuery<Order, Item> joined = qb.from(Order.class)
        .join(Item.class, (o, i) -> i.getOrder().getId() == o.getId());
// the order with two items is two SQL rows, but Hibernate returns it once: toList() and toSet() have no repeats
SequencedSet<Order> orders = joined.orderBy((o, i) -> desc(i.getQty()).asc(o.getId())).toSet();
// count() and limit count the SQL rows; distinct() makes them count the orders
long rows = joined.count();
long distinctOrders = qb.from(Order.class)
        .join(Item.class, (o, i) -> i.getOrder().getId() == o.getId())
        .distinct()
        .count();
```

<a id="join-a-derived-table-or-cte"></a>
### 派生テーブルと CTE の結合

[6.9](#69-derived-table-subquery-in-from) の派生テーブルと [6.10](#610-cte-with) の CTE は、エンティティと同じように結合できます。
形は `join(cte, (c, x) -> ...)`、`leftJoin(cte, ...)`、`join(qb.from(...)...select(new Row(...)), ...)`、`leftJoin(...)` で、2 番目や 3 番目のルートとして使えます。

- HQL は `with t as (...) ... join t e1 on ...` または `join (select ...) e1 on ...` です。
- 行の型は record です。アクセサー（`x.total()`）がカラムを読みます。
- 同じ `Cte` オブジェクトを 1 つの文で 2 回読むと、定義は 1 回だけ書き、2 つの結合が同じ名前を読みます: `join t e1 on ... left join t e2 on ...`。異なる `Cte` オブジェクトは、`with t as (...), t2 as (...)` のように別々の名前を受け取ります（3 つの DB で実測）。
- 派生の行には id がないので、`x == null` はエラー（`LQL-3067`）です。`x.customerId() == null` のようにカラムを比較してください。
- Hibernate 7.4（以前の 6.6 も）は、エンティティの `select distinct`、派生テーブルと CTE の inner/left join、サブクエリの中のエンティティの結合を受け付けます（3 つの DB で実測）。

<!-- region: JoinQueryTest.java#join-cte -->
```java
// the total of each customer, joined to the customer row
Cte<Total> t = qb.with(qb.from(Order.class)
        .where(o -> o.getCustomer() != null)
        .groupBy(o -> o.getCustomer().getId())
        .select(o -> new Total(o.getCustomer().getId(), Agg.sum(o.getPrice()))));
ValueQuery<NameTotal> rows = qb.from(Customer.class)
        .join(t, (c, x) -> x.customerId().equals(c.getId()))
        .where((c, x) -> x.total() > 100)
        .select((c, x) -> new NameTotal(c.getName(), x.total()));
assertEquals("with t as (select d0.customer.id as customerId, sum(d0.price) as total from Order d0"
        + " where d0.customer is not null group by d0.customer.id)"
        + " select e0.name, e1.total from Customer e0 join t e1 on e1.customerId = e0.id where e1.total > 100.0",
        rows.toHql().hql());
```

<!-- region: JoinQueryTest.java#join-derived -->
```java
// a derived table without a name; a customer without orders has no row in it (left join)
SelectJoinQuery<Customer, Total> small = qb.from(Customer.class)
        .leftJoin(totals(), (c, x) -> x.customerId().equals(c.getId()))
        .where((c, x) -> x.customerId() == null || x.total() < 210);
assertEquals("select e0 from Customer e0 left join (select d0.customer.id as customerId, sum(d0.price) as total"
        + " from Order d0 where d0.customer is not null group by d0.customer.id) e1 on e1.customerId = e0.id"
        + " where e1.customerId is null or e1.total < 210.0", small.toHql().hql());
```

<!-- region: JoinQueryTest.java#cte-reuse -->
```java
// the same CTE read twice is written once
ValueQuery<NameTotal> reused = qb.from(Customer.class)
        .join(t, (c, x) -> x.customerId().equals(c.getId()))
        .leftJoin(t, (c, x, y) -> y.customerId().equals(c.getId()) && y.total() > 210)
        .select((c, x, y) -> new NameTotal(c.getName(), y.total()));
assertEquals("with t as (select d0.customer.id as customerId, sum(d0.price) as total from Order d0"
        + " where d0.customer is not null group by d0.customer.id)"
        + " select e0.name, e2.total from Customer e0 join t e1 on e1.customerId = e0.id"
        + " left join t e2 on e2.customerId = e0.id and e2.total > 210.0", reused.toHql().hql());
```

<a id="what-a-join-query-has"></a>
### 結合クエリのメソッド

| 呼び出しの後 | メソッド |
|---|---|
| `join`、`leftJoin`、`crossJoin` | `where`、`join` / `leftJoin` / `crossJoin`（エンティティ 10 個まで、派生テーブルと CTE を含む）、`distinct`、`orderBy`、`select`、`limit`、`toList`、`toSet`、`first`、`count`、`exists`、`toHql` |
| `orderBy((o, c) -> ...)` | `select`、`limit`、`offset`、`toList`、`first`、その他のソート済みクエリのメソッド（[4. ソートとページング](04-order-paging.md)） |

サブクエリの中の結合は [5. サブクエリ](05-subquery.md#joins-inside-a-subquery) を参照してください。
サポートしないもの: insert の結合、11 個以上のエンティティ、`SortSpec` の結合の形。
update と delete の結合は追加しないことにしました。関連のパスか `sub` を使ってください。

コード例: [`JoinQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java)（`join`、`left-join`、`sort`、`select`、`three`、`cross-join`、`cross-join-paging`、`join-distinct`、`join-set`、`join-cte`、`join-derived`、`cte-reuse`、`ten`）。

---

<a id="69-derived-table-subquery-in-from"></a>
## 6.9 派生テーブル（FROM の中のサブクエリ）

`select` で値クエリ（record、[6.3](#63-select-a-record-or-dto) を参照）を作り、`qb.from(...)` に渡してください。
その行が外側のクエリのテーブルになります。
結果は `DerivedQuery<R>` で、`R` は行の型です。

<!-- region: DerivedQueryTest.java#derived -->
```java
// the rows of a group query are the table of the outer query; r.total() reads the column "total"
DerivedQuery<Stat> q = qb.from(qb.from(Order.class)
                .where(o -> o.getCustomer() != null)
                .groupBy(o -> o.getCustomer().getId())
                .select(o -> new Stat(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice()))))
        .where(r -> r.total() > 100)
        .orderBy(r -> desc(r.total()));
assertEquals("select e0.customerId, e0.cnt, e0.total from (select d0.customer.id as customerId, count(*) as cnt,"
        + " sum(d0.price) as total from Order d0 where d0.customer is not null group by d0.customer.id) e0"
        + " where e0.total > 100.0 order by e0.total desc nulls last", q.toHql().hql());
```

この例で `Stat` は `record Stat(Integer customerId, Long cnt, Double total)` です。
結果の行と `count()` は、H2、MariaDB、Oracle で、同じ計算を Java で行った結果と同じです。

- 内側のクエリのエイリアスは `d0`（結合は `dj1`、...）、外側のクエリのエイリアスは `e0` です。
- record のコンポーネントごとにカラム名が決まります: `as customerId`、`as cnt`、`as total`。
- 外側のラムダ式では、record のアクセサーでカラムを読みます。`r.total()` は `e0.total` です。
- 外側のクエリには `where`、`orderBy`、`limit`、`offset`、`select`、`toList`、`first`、`count`、`toHql` があります。

行の 1 つの値を select することもできます。内側と外側のキャプチャした値は、順にバインドします。

<!-- region: DerivedQueryTest.java#select -->
```java
// a value of the row; captured values inside and outside are bound in order
ValueQuery<Integer> ids = qb.from(qb.from(Order.class)
                .where(o -> o.getCustomer() != null && o.getPrice() > min)
                .groupBy(o -> o.getCustomer().getId())
                .select(o -> new Stat(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice()))))
        .where(r -> r.total() > min * 2)
        .select(r -> r.customerId());
```

内側のクエリの `limit` と `offset` は内側のクエリに残ります（HQL の `limit`、`offset`）。そのため、上位の行をソートし直せます。

<!-- region: DerivedQueryTest.java#inner-limit -->
```java
// the three most expensive orders, then the cheapest of them
Optional<Priced> cheapestOfTop = qb.from(qb.from(Order.class)
                .orderBy(o -> desc(o.getPrice()))
                .limit(3)
                .select(o -> new Priced(o.getId(), o.getPrice())))
        .orderBy(r -> asc(r.price()))
        .first();
```

`Priced` は `record Priced(Long id, Double price)` です。

Hibernate 7.4 は、派生テーブルのカラムに `c0`、`c1`、`c2` のような名前を付けます。H2 はカラムのリストをエイリアスに付けます（`r1_0(c0,c1,c2)`）。MariaDB と Oracle は、サブクエリの中にカラムのエイリアスを書きます。

ルール:

- 行の型は **Java の record** でなければなりません（Kotlin の data class も可）。
  lambdaQL は record のコンポーネントから順にカラム名を取得します。
- 通常の DTO クラスや 1 つの値はここでは使えません。lambdaQL がカラム名を決められないからです。この場合、`TranslationException`（`LQL-3064`）が発生します。

<!-- region: DerivedQueryTest.java#rules -->
```java
// the row type must be a record or Kotlin data class (its components name the columns)
assertEquals("LQL-3064", assertThrows(LambdaQLException.class,
        () -> qb.from(qb.from(Order.class).select(o -> o.getPrice()))).code());
```

エンティティのクエリに派生テーブルを結合する形は、[派生テーブルと CTE の結合](#join-a-derived-table-or-cte) を参照してください。

サポートしないもの: 派生テーブル同士の結合、update、delete、insert の中の派生テーブル。

エンティティの `@SQLRestriction` と `@SoftDelete` の条件は、エンティティの結合の `on` と派生テーブルの中にも Hibernate が付けます（MariaDB 10.11、Oracle 23.26 で実測）。lambdaQL はこの条件を別に扱いません。

コード例: [`DerivedQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DerivedQueryTest.java)（`derived`、`select`、`inner-limit`、`rules`）。

---

<a id="610-cte-with"></a>
## 6.10 CTE（`with`）

`qb.with(valueQuery)` で `Cte<R>` 型のハンドルを作ってください。その後、`qb.from(handle)` で読みます。
内側のクエリは派生テーブル（6.9）と同じです。

<!-- region: DerivedQueryTest.java#cte -->
```java
Cte<Stat> t = qb.with(perCustomer());
DerivedQuery<Stat> q = qb.from(t).where(r -> r.cnt() >= 2);
assertEquals("with t as (select d0.customer.id as customerId, count(*) as cnt, sum(d0.price) as total"
        + " from Order d0 where d0.customer is not null group by d0.customer.id)"
        + " select e0.customerId, e0.cnt, e0.total from t e0 where e0.cnt >= 2L", q.toHql().hql());
```

`perCustomer()` は例のヘルパーメソッドです。[6.9](#69-derived-table-subquery-in-from) の内側のクエリ（顧客のある注文を顧客の id でまとめて `Stat` を作るグループクエリ）と同じ値クエリを返します。

MariaDB と Oracle は本物の `with` を実行します。カラムのリストは Hibernate が自分で書きます: `with t (customerId,cnt,total) as (...)`。H2 は CTE を派生テーブルに変えます。

注意:

- CTE の名前は `t` です。外側のクエリは常にエイリアス `e0` を付けます（`from t e0`）。HQL ではエイリアスが必要です。
- CTE のカラム名は select のエイリアス（`as customerId`）で決めます。HQL は CTE 名の後のカラムのリストを受け付けず、カラムのリストは Hibernate が SQL に追加します。
- MariaDB と Oracle は実際の `with` を実行します。H2 では Hibernate がこれを派生テーブルに展開します。結果は同じです。
- 行の型のルールは派生テーブルと同じです: record。
- エンティティのクエリに CTE を結合する形は、[派生テーブルと CTE の結合](#join-a-derived-table-or-cte) を参照してください。
- 同じ CTE を 1 つのクエリで 2 回読めます。定義は 1 回だけ書きます（[派生テーブルと CTE の結合](#join-a-derived-table-or-cte) を参照）。
- update、delete、insert の中の CTE はサポートしません。

コード例: [`DerivedQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DerivedQueryTest.java)（`cte`）。

---

<a id="611-rules-j1-to-j8"></a>
## 6.11 ルール J1〜J8

| # | ルール | 状態 |
|---|---|---|
| J1 | `select` のラムダ式は **1 つの値の式** か **1 つのコンストラクター呼び出し** を返します。コンストラクターの各引数は値の式です（[3. 条件（`where`）](03-where.md) のルール）。エンティティそのものも値になれます（[エンティティの選択](#select-entities)）。値を選ぶ三項演算子は `case when` になります（[3.5.1](03-where.md#351-values-chosen-by-a-condition)）。`select` だけで使った関連のパスは `left join` のエイリアスになるので、行を失いません。必須の関連（`optional = false`）は inner join です（A4）。同じパスを `where` で使うと、`select` は `where` の結合を使います（R24）。 | 実装済み |
| J2 | コンストラクターの場合、lambdaQL はカラムだけを選択し、コンストラクターは Java で呼び出します。`select new` は書きません。 | 実装済み |
| J3 | 集約があれば、`select` と `having`（そして `insert ... select` の値）で集約でない値はすべて `groupBy` のキーでなければなりません。そうでなければ解析時にエラーが発生します。 | 実装済み。エラーは `TranslationException`（`LQL-3035`、[11](../11-errors-and-languages.md) を参照） |
| J4 | `sum`/`avg`/`min`/`max` は、行がないかすべての値が NULL なら NULL を返します。`count` は 0 を返します。NULL になりうる値にはボクシング型を使ってください。プリミティブのコンストラクター引数に NULL が来ると、該当する引数を示す `IllegalStateException` が発生します。 | 実装済み |
| J5 | 派生テーブルや CTE の行の型: **Java の record**（Kotlin のコードは data class も使用可）。通常の DTO クラスは単純な `select` でだけ許されます。そうでなければ `LQL-3064`。結合した派生テーブルと CTE の行にも適用します。派生の行は `null` と比較できません（`LQL-3067`）。 | 実装済み |
| J6 | `qb.with(...)` で作った CTE は、select のエイリアスでカラム名を決めます: `with t as (select d0.customer.id as customerId, ...) select e0.customerId ... from t e0`。HQL は名前の後のカラムのリストを受け付けません。Hibernate は `with t (col list) as (...)` を `SyntaxException` で拒否します。カラムのリストは Hibernate が SQL に直接書きます（MariaDB と Oracle で `with t (customerId,cnt,total) as (...)`）。H2 は CTE を派生テーブルに変えます。外側のクエリは常にエイリアスを使います（`from t e0`）。 | 実装済み |
| J7 | `distinct()` は `select distinct` をレンダリングします。ソートと一緒に使うと、ソートキーが select リストになければなりません（DB のルール）。lambdaQL は解析時にこれを検査します。結合結果の `distinct()` では、ソートキーが主エンティティ自身のカラムでなければなりません。 | 実装済み |
| J8 | 選択は、ソート（[4. ソートとページング](04-order-paging.md)）、ページング、サブクエリ（[5. サブクエリ](05-subquery.md)）と一緒に動作します。集約を基準にしたソート（`desc(s.total())`）はサポートしません。 | 実装済み。集約を基準にしたソートは除く |

<!-- region: ProjectionTest.java#rules -->
```java
// a value without an aggregate must be a group key
LambdaQLException j3 = assertThrows(LambdaQLException.class, () -> qb.from(Order.class)
        .groupBy(o -> o.getCustomer().getId())
        .select(o -> new KeyCount(o.getCustomer().getId(), o.getStatus(), Agg.count())));
assertEquals("LQL-3035", j3.code());
// aggregates belong in having, not in where
LambdaQLException where = assertThrows(LambdaQLException.class, () -> qb.from(Order.class).where(o -> Agg.count() > 1));
assertEquals("LQL-3005", where.code());
// with distinct, the sort key must be selected
LambdaQLException j7 = assertThrows(LambdaQLException.class, () -> qb.from(Order.class)
        .orderBy(o -> asc(o.getId())).select(o -> o.getPrice()).distinct().toHql());
assertEquals("LQL-3046", j7.code());
// the group without orders gives NULL; a primitive argument cannot take it
assertThrows(IllegalStateException.class, () -> qb.from(Order.class)
        .where(o -> o.getStatus() == 9)
        .select(o -> new Bad(null, Agg.sum(o.getPrice()))).toList());
```

`Bad` は `record Bad(Integer customerId, double total)` です。status が 9 の注文はないので `sum` は NULL で、プリミティブの `double` はこの値を受け取れません。

コード例: [`ProjectionTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java)（`rules`）。

---

<a id="612-not-in-v01"></a>
## 6.12 サポートしないもの

| 項目 | 状態 |
|---|---|
| 集約の結果や select のエイリアスでのソート（例: `orderBy(s -> desc(s.total()))`）、グループクエリの `orderBy` | サポートしない |
| ユーザー定義の集約関数とウィンドウ関数 | サポートしない |
| insert の結合、11 個以上のエンティティ、サブクエリの中の 3 つ以上のエンティティ | サポートしない |
| update、delete の結合 | 追加しない。関連のパスか `sub` を使う |
| DML の中の派生テーブルと CTE | サポートしない |

この章のエラーは、コードの付いた `TranslationException` です。誤ったグループのキーは `LQL-3035`、`where` の中の `Agg` は `LQL-3005`、`distinct` で select していないソートキーは `LQL-3046`、派生テーブルや CTE の行の型は `LQL-3064` です。[11. エラーと言語](../11-errors-and-languages.md) を参照してください。

---

[Java トラック](README.md) | 前: [5. サブクエリ](05-subquery.md) | 次: [7. 更新、挿入、削除](07-dml.md) | [このページの Kotlin 版](../kotlin/06-select-group.md)
