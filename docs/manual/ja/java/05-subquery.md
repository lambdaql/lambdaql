<a id="5-subqueries-java"></a>
# 5. サブクエリ（Java）

[Java トラック](README.md) | [マニュアルのホーム](../README.md) | 前: [4. ソートとページング](04-order-paging.md) | 次: [6. 選択、集計、グループ化](06-select-group.md) | [このページの Kotlin 版](../kotlin/05-subquery.md)

共通ページ: [1. 概要](../01-overview.md) · [11. エラーと言語](../11-errors-and-languages.md)

---

<a id="51-the-idea-a-second-lambda-argument-sub"></a>
## 5.1 考え方: 2 番目のラムダ式の引数 `sub`

`where` のラムダ式は、**2 番目の引数** としてサブクエリのファクトリーを受け取れます。このマニュアルではこれを `sub` と呼びます（どんな名前でも使えます）。
その中で、通常のクエリと **同じラムダ式の構文** でサブクエリを書きます。

サブクエリがなければ `.where(o -> ...)` のように引数は 1 つで、サブクエリがあれば `.where((o, sub) -> ...)` のように引数は 2 つです。
lambdaQL は引数の数で 2 つの形を区別します。
サブクエリは `&&`、`||`、`!` と自由に組み合わせられます。

`sub` でできること:

| サブクエリ | 用途 | HQL |
|---|---|---|
| `sub.from(X.class).where(...).exists()` | 条件 | `exists (select 1 from X x where ...)` |
| `!sub.from(X.class).where(...).exists()` | 条件 | `not exists (...)` |
| `sub.from(X.class).where(...).select(x -> v).contains(y)` | 条件 | `y in (select x.v from X x where ...)` |
| `!... .contains(y)` | 条件 | `y not in (select x.v from X x where ...)` |
| `sub.from(X.class).avg(x -> v)`（`sum`、`min`、`max`、`count()` も） | 値 | `(select avg(x.v) from X x)` |
| `sub.from(X.class)...orderBy(...).limit(1).select(x -> v).first()` | 値 | top-1 サブクエリ |
| `sub.from(A.class).join(B.class, (a, b) -> ...).where((a, b) -> ...).exists()` | 条件 | `exists (select 1 from A a join B b on ... where ...)`（[サブクエリの中の結合](#joins-inside-a-subquery)） |

> [!NOTE]
> 戻り値（`boolean`、`Double`、...）は、コードをコンパイルできるようにするためのプレースホルダーにすぎません。
> これらのメソッドは lambdaQL のラムダ式の中でだけ使います。lambdaQL はラムダ式を呼び出さず、バイト opcode を解析します。

内側のラムダ式（`sub.from(...).where(...)` の中の `i -> ...`）は、別に `Serializable` である必要はありません。lambdaQL は外側のラムダ式のバイト opcode からそれを見つけます。

HQL では、サブクエリのルートのエイリアスは `s1`、`s2`、... です（外側のルートは `e0`）。サブクエリの中の結合には `s1j1` のようなエイリアスが付きます。

例では [例で使うエンティティ](02-getting-started.md#example-entities) を使います。`price` は `double`、`status` は `short`、`Customer.id` は `int` で、`Customer.grade` は `Integer` です。
例のデータには、グレード 5、2、NULL と、顧客のない注文が 1 件あります。
この章の例は、H2、MariaDB、Oracle で、Java で同じ絞り込みをした結果と同じ行を返します。

## 5.2 EXISTS

`qty > 10` の品目を 1 つ以上持つ注文:

<!-- region: SubqueryTest.java#exists -->
```java
// correlated: the inner lambda uses the outer order o
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId() && i.getQty() > 10).exists());
assertEquals("select e0 from Order e0 where exists (select 1 from Item s1 where s1.order.id = e0.id and s1.qty > 10)",
        q.toHql().hql());
```

**データベースごとの SQL**

<!-- sql: sub-exists -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id and i1_0.qty>10)
```

これは **相関** サブクエリです。内側のラムダ式が外側の `o` を使います（[5.6](#56-correlated-subqueries) を参照）。

コード例: [`SubqueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java)（`exists`）。

## 5.3 NOT EXISTS

`!` を使ってください。ほかの条件と組み合わせられます。
status が 1 の注文、または品目のない注文:

<!-- region: SubqueryTest.java#not-exists -->
```java
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> o.getStatus() == 1 || !sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).exists());
assertEquals("select e0 from Order e0 where e0.status = 1 or not exists (select 1 from Item s1 where s1.order.id = e0.id)",
        q.toHql().hql());
```

**データベースごとの SQL**

<!-- sql: sub-not-exists -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 or not exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id)
```

コード例: [`SubqueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java)（`not-exists`）。

## 5.4 IN

`grade > 3` の顧客の注文:

<!-- region: SubqueryTest.java#in -->
```java
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> sub.from(Customer.class).where(c -> c.getGrade() != null && c.getGrade() > 3)
                .select(c -> c.getId()).contains(o.getCustomer().getId()));
assertEquals("select e0 from Order e0 where e0.customer.id in"
        + " (select s1.id from Customer s1 where s1.grade is not null and s1.grade > 3)", q.toHql().hql());
```

**データベースごとの SQL**

<!-- sql: sub-in -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id in (select c2_0.id from customers c2_0 where c2_0.grade is not null and c2_0.grade>3)
```

`o.getCustomer().getId()` は顧客の `@Id` で終わるので、外部キーのカラム `e0.customer.id` です。結合は必要ありません。
自分で書いた null チェック（`c.getGrade() != null`）は、HQL に `s1.grade is not null` として残ります。

コード例: [`SubqueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java)（`in`）。

<a id="55-not-in-is-written-as-not-exists"></a>
## 5.5 NOT IN と NULL

`!values.contains(x)` は SQL の意味のまま `NOT IN` として出力されます。

<!-- region: SubqueryTest.java#not-in -->
```java
// the SQL meaning: NOT IN. The grades are 5, 2 and NULL, so no row matches: x NOT IN (5, 2, NULL)
// is never true. To skip the NULL, say so in the subquery: where(c -> c.getGrade() != null)
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> !sub.from(Customer.class).select(c -> c.getGrade()).contains((int) o.getStatus()));
assertEquals("select e0 from Order e0 where e0.status not in (select s1.grade from Customer s1)", q.toHql().hql());
SelectQuery<Order> withoutNull = qb.from(Order.class)
        .where((o, sub) -> !sub.from(Customer.class).where(c -> c.getGrade() != null).select(c -> c.getGrade())
                .contains((int) o.getStatus()));
```

**データベースごとの SQL**

<!-- sql: sub-not-in -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status not in (select c2_0.grade from customers c2_0)
```

理由:

- `x NOT IN (subquery)` は、サブクエリが NULL を 1 つでも返すと **1 行も返しません**。lambdaQL はこの SQL の意味を保ち、`LQL-5009` を `DEBUG` レベルで記録します。
- NULL を除外するには、例の 2 番目のクエリのように、サブクエリに `where(c -> c.getGrade() != null)` を書いてください。そうすると、NULL のグレードを除いた Java の `!grades.contains(status)` と同じ結果になります。
- `!sub...exists()` は引き続き `NOT EXISTS` として書かれます。

コード例: [`SubqueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java)（`not-in`）。

<a id="56-correlated-subqueries"></a>
## 5.6 相関サブクエリ

内側のラムダ式が外側のエンティティを使うと、サブクエリは **相関** サブクエリになります。特別な構文は必要ありません。外側の変数をそのまま使ってください（下の例の `o`）。

lambdaQL が内側のラムダ式のキャプチャ値を読む方法:

| 内側のラムダ式でキャプチャしたもの | 変換結果 |
|---|---|
| 外側のエンティティ（`o`） | 外側のエイリアスへの参照（`e0.id`） |
| それ以外の値（ローカル変数、フィールド、...） | [3.11](03-where.md#311-captured-values) と同じバインドパラメーター |

<!-- region: ManualJavaSubqueryTest.java#correlated -->
```java
int minQty = 10;
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> sub.from(Item.class)
                .where(i -> i.getOrder().getId() == o.getId() && i.getQty() > minQty)   // o comes from outside
                .exists());
assertEquals("select e0 from Order e0"
        + " where exists (select 1 from Item s1 where s1.order.id = e0.id and s1.qty > :p1)", q.toHql().hql());
assertEquals(Map.of("p1", 10), q.toHql().parameters());
```

**相関条件の `==`。** `i.getOrder().getId() == o.getId()` は 2 つの `Long` オブジェクトを比較します。エンティティの値どうしは SQL の意味で比較するので（[3.6](03-where.md#36-null-rules) を参照）、lambdaQL は単純な `=` を書きます（`!=` は `<>`）。
データベースはインデックスを使えます。

コード例: [`ManualJavaSubqueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaSubqueryTest.java)（`correlated`）。

<a id="57-scalar-subqueries"></a>
## 5.7 スカラーサブクエリ

**1 つの値** を返すサブクエリは、比較に使えます。

平均価格より高い注文:

<!-- region: SubqueryTest.java#scalar -->
```java
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> o.getPrice() > sub.from(Order.class).avg(x -> x.getPrice()));
assertEquals("select e0 from Order e0 where e0.price > (select avg(s1.price) from Order s1)", q.toHql().hql());
```

**データベースごとの SQL**

<!-- sql: sub-scalar -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.price>(select avg(o2_0.price) from orders o2_0)
```

スカラーサブクエリは **常に 1 行を返さなければなりません**。使える形は 2 つです。

| 形 | 例 |
|---|---|
| 集計: `count()`、`sum`、`avg`、`min`、`max` | `sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).count()` |
| top-1: `orderBy(...).limit(1)...first()` | 下記を参照 |

それ以外の `first()` は解析時にエラー（`TranslationException`、`LQL-3017`）になります。

品目が 2 つ以上ある注文（`count()`）:

<!-- region: SubqueryTest.java#count -->
```java
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).count() >= 2);
assertEquals("select e0 from Order e0 where (select count(s1) from Item s1 where s1.order.id = e0.id) >= 2L", q.toHql().hql());
```

**データベースごとの SQL**

<!-- sql: sub-count -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where (select count(i1_0.id) from items i1_0 where i1_0.order_id=o1_0.id)>=2
```

top-1: 顧客ごとの最も高い注文。

<!-- region: SubqueryTest.java#top-one -->
```java
// the most expensive order of each customer: a correlated scalar subquery with order + limit 1
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> o.getId().equals(sub.from(Order.class)
                .where(x -> x.getCustomer().getId() == o.getCustomer().getId())
                .orderBy(x -> desc(x.getPrice()))
                .limit(1)
                .select(x -> x.getId())
                .first()));
assertEquals("select e0 from Order e0 where e0.id = (select s1.id from Order s1 where s1.customer.id = e0.customer.id"
        + " order by s1.price desc, s1.id asc limit 1)", q.toHql().hql());
// first() without limit(1) is rejected
assertThrows(LambdaQLException.class, () -> qb.from(Order.class)
        .where((o, sub) -> o.getId().equals(sub.from(Order.class).select(x -> x.getId()).first())));
```

**データベースごとの SQL**

<!-- sql: sub-top-one -->
```sql
-- H2, Oracle
select …
from orders o1_0
where o1_0.id=(select o2_0.id from orders o2_0 where o2_0.customer_id=o1_0.customer_id order by o2_0.price desc,o2_0.id fetch first 1 rows only)

-- MariaDB
select …
from orders o1_0
where o1_0.id=(select o2_0.id from orders o2_0 where o2_0.customer_id=o1_0.customer_id order by o2_0.price desc,o2_0.id limit 1)
```

サブクエリの中のソートも、外側のクエリと同じように `By` のソート関数を使います: `orderBy(x -> desc(x.getPrice()))`（[4.1](04-order-paging.md#41-one-lambda-for-the-whole-sort) を参照）。
lambdaQL は最後のソートキーとして `id`（`s1.id asc`）を追加するので、先頭の行は常に同じです。
相関条件は顧客のエンティティではなく外部キー（`getCustomer().getId()`）を比較します。[5.10](#510-paths-and-null-inside-subqueries) を参照してください。

集計の NULL は SQL に従います。`sum`、`avg`、`min`、`max` は行がなければ NULL を返します。`count` は 0 を返します。

コード例: [`SubqueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java)（`scalar`、`count`、`top-one`）。

<a id="58-nested-subqueries"></a>
## 5.8 入れ子のサブクエリ

サブクエリの `where` も `sub` 引数を受け取れます。どの階層でも同じルールがもう一度適用されます。

大きな品目を含む注文を持つ顧客:

<!-- region: SubqueryTest.java#nested -->
```java
// customers that have an order with an item of quantity > 10: a subquery inside a subquery
SelectQuery<Customer> q = qb.from(Customer.class)
        .where((c, sub) -> sub.from(Order.class).where((o, sub2) -> o.getCustomer().getId() == c.getId()
                && sub2.from(Item.class).where(i -> i.getOrder().getId() == o.getId() && i.getQty() > 10).exists()).exists());
assertEquals("select e0 from Customer e0 where exists (select 1 from Order s1 where s1.customer.id = e0.id"
        + " and exists (select 1 from Item s2 where s2.order.id = s1.id and s2.qty > 10))", q.toHql().hql());
```

**データベースごとの SQL**

<!-- sql: sub-nested -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from customers c1_0
where exists(select 1 from orders o1_0 where o1_0.customer_id=c1_0.id and exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id and i1_0.qty>10))
```

Java では、入れ子のラムダ式で同じ名前を 2 回使えません。階層ごとに新しい名前（`sub2`、`o`、`i`）を使ってください。

コード例: [`SubqueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java)（`nested`）。

<a id="59-sorting-inside-a-subquery"></a>
## 5.9 サブクエリの中のソート

| ケース | lambdaQL の動作 |
|---|---|
| `limit` のある `orderBy`（top-1、top-N） | `id` のタイブレーカーを付けて残す |
| `limit` のない `orderBy` | **削除し**、警告をログに出す（結果に影響しない。Oracle では構文エラー ORA-00907 にもなる） |
| `limit` のあるサブクエリに対する `contains`（MariaDB は `IN (... limit n)` をサポートしない。エラー 1235） | すべてのデータベースで派生テーブルに包む: `in (select t1.v from (select s1.id as v ... limit n) t1)` |

[4. ソートとページング](04-order-paging.md) の NULL のポリシーと `id` の自動タイブレーカーは、サブクエリの中にも適用されます。

<a id="510-paths-and-null-inside-subqueries"></a>
## 5.10 サブクエリの中のパスと NULL

サブクエリの中では、条件、パス、NULL は **外側のクエリとまったく同じように** 動作します（[3. 条件（`where`）](03-where.md)）。

- オブジェクトに対する `==` は SQL の意味による値の比較です。キャプチャ値の `null` はどの行にも一致しません（`LQL-4045`）。
- サブクエリ自身のエンティティの関連のパスは、外側の `where` と同じ結合のルールに従います。関連のない行が条件に合いうるなら `left join`、合いえないなら内部結合の `join` です。
- `@Id` で終わるパスは外部キーのカラムです（結合なし）。
- **外側のエンティティ** は、自身のカラムと外部キーの id（`o.getId()`、`o.getCustomer().getId()`）だけを使えます。結合が必要な外側のエンティティのパス（`o.getCustomer().getName()`）は解析時にエラーになります。代わりに外部キーの id を比較してください。
- コレクションのパス（`c.getOrders()`）は拒否されます（`LQL-3010`）。コレクションについて調べるにはサブクエリを使ってください。
- エンティティの `@SQLRestriction` と `@SoftDelete` の条件は、サブクエリ（`exists`、`in`）の中にも Hibernate が付けます（MariaDB 10.11、Oracle 23.26 で実測）。lambdaQL はこの条件を別に扱いません。

次の例は、「注文のある顧客」を `Customer.orders` コレクションの代わりにサブクエリで探します。

<!-- region: ManualJavaSubqueryTest.java#collection-path -->
```java
// a collection path is rejected
LambdaQLException e = assertThrows(LambdaQLException.class,
        () -> qb.from(Customer.class).where(c -> c.getOrders().size() > 0));
assertEquals("LQL-3010", e.code());
// ask with a subquery instead
SelectQuery<Customer> withOrders = qb.from(Customer.class)
        .where((c, sub) -> sub.from(Order.class).where(o -> o.getCustomer().getId() == c.getId()).exists());
assertEquals("select e0 from Customer e0"
        + " where exists (select 1 from Order s1 where s1.customer.id = e0.id)", withOrders.toHql().hql());
```

<a id="joins-inside-a-subquery"></a>
### サブクエリの中の結合

サブクエリの中でも 2 つのエンティティを結合できます。形は `sub.from(A.class).join(B.class, (a, b) -> ...)` と `leftJoin(...)` で、結果は `From2<A, B>` です。
`From2` のすべてのラムダ式は 2 つの行を受け取ります: `where((a, b) -> ...)`、`exists()`、`count()`、`select((a, b) -> v)` の後の `contains(x)` や `first()`、`max`、`min`、`avg`、`sum((a, b) -> v)`。

- HQL は `exists (select 1 from Order s1 join Item s2 on s2.order.id = s1.id where ...)` のようになります。
- ON 条件の中の関連のパスは `left join` になります。パスの結合は、そのパスが始まるルートの直後に置きます。
- サブクエリの中のエンティティは 2 つまでです。結合の形では `orderBy` と `limit` を使えません。
- `exists`、結合した行の `sum`、`left join` と ON の中の関連のパスを使った `in` を、3 つの DB で実測しました。

<!-- region: SubqueryTest.java#sub-join -->
```java
// customers with an order that has an item of quantity > 10: the subquery joins Order and Item
SelectQuery<Customer> q = qb.from(Customer.class).where((c, sub) -> sub.from(Order.class)
        .join(Item.class, (o, i) -> i.getOrder().getId() == o.getId())
        .where((o, i) -> o.getCustomer().getId() == c.getId() && i.getQty() > 10)
        .exists());
assertEquals("select e0 from Customer e0 where exists (select 1 from Order s1 join Item s2 on s2.order.id = s1.id"
        + " where s1.customer.id = e0.id and s2.qty > 10)", q.toHql().hql());
// an aggregate over the joined rows
SelectQuery<Customer> total = qb.from(Customer.class).where((c, sub) -> sub.from(Order.class)
        .join(Item.class, (o, i) -> i.getOrder().getId() == o.getId())
        .where((o, i) -> o.getCustomer().getId() == c.getId())
        .sum((o, i) -> i.getQty()) > 30);
assertEquals("select e0 from Customer e0 where (select sum(s2.qty) from Order s1 join Item s2 on s2.order.id = s1.id"
        + " where s1.customer.id = e0.id) > 30", total.toHql().hql());
```

コード例: [`ManualJavaSubqueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaSubqueryTest.java)（`collection-path`）、[`SubqueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java)（`sub-join`）。

<a id="511-subqueries-in-other-places"></a>
## 5.11 ほかの場所のサブクエリ

- **UPDATE / DELETE の中のサブクエリ**: `where` で同じ `sub` の構文を使います。[7.4 削除](07-4-delete.md) と [7.2 更新](07-2-update.md) を参照してください。
- **`set` の値の中のサブクエリ**（`o.setTotal(sub.from(...).sum(...))`）: 代入のラムダ式の 2 番目の引数 `sub` で書きます。[7.2 更新](07-2-update.md#an-aggregate-from-a-subquery) を参照してください。
- **`from` の中のサブクエリ（派生テーブル）と CTE**: 実装済みです。`sub` は使わず、値のクエリを `qb.from(...)` か `qb.with(...)` に渡します。[6.9 派生テーブル](06-select-group.md#69-derived-table-subquery-in-from) と [6.10 CTE](06-select-group.md#610-cte-with) を参照してください。

<a id="512-rules-summary-q1-to-q6"></a>
## 5.12 ルールのまとめ（Q1〜Q6）

| # | ルール |
|---|---|
| Q1 | サブクエリの中の条件、パス、NULL のルールは外側と同じです。条件の中の関連のパスも同じ結合のルール（関連のない行が条件に合いうるなら `left join`、そうでなければ内部結合の `join`）に従います。結合が必要な外側のエンティティのパスはエラーで、外部キーの id は使えます |
| Q2 | **NOT IN は SQL の意味を保ちます**: `!values.contains(x)` は `x not in (select c.sel from C c where <condition>)` になります。サブクエリに NULL があると行は返りません（`DEBUG` レベルの `LQL-5009`）。NULL を除外するには、サブクエリに `c.sel != null` を追加します |
| Q3 | スカラーサブクエリは 1 行を返さなければなりません: 集計（`max`、`min`、`avg`、`sum`、`count`）または `orderBy(...).limit(1)...first()`。それ以外の `first()` はエラーです |
| Q4 | サブクエリのソート: `limit` があれば残し、なければ警告を出して削除します。`IN (... limit)` は派生テーブルに包みます |
| Q5 | 相関条件では、`==` を `=`、`!=` を `<>` として書きます（SQL の意味）。どちらかが NULL なら一致しません。インデックスを使えます |
| Q6 | サブクエリの `where` は自分の `sub` 引数を受け取れます: `where((i, sub2) -> ...)`。どの階層でも同じルールが適用されます |

---

[Java トラック](README.md) | 前: [4. ソートとページング](04-order-paging.md) | 次: [6. 選択、集計、グループ化](06-select-group.md) | [このページの Kotlin 版](../kotlin/05-subquery.md)
