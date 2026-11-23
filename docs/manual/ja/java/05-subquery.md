<a id="5-subqueries-java"></a>
# 5. サブクエリ（Java）

[Java トラック](README.md) | [マニュアルのホーム](../README.md) | 前: [4. ソートとページング](04-order-paging.md) | 次: [6. 選択、集計、グループ化](06-select-group.md) | [このページの Kotlin 版](../kotlin/05-subquery.md)

共通ページ: [1. 概要](../01-overview.md) · [11. エラーと言語](../11-errors-and-languages.md)

---

<a id="51-the-idea-a-second-lambda-argument-sub"></a>
## 5.1 考え方: 2 つ目のラムダ引数 `sub`

`where` のラムダは **2 つ目の引数** としてサブクエリのファクトリーを受け取れます。このマニュアルではこれを `sub` と呼びます（どんな名前でも使えます）。
その中では、通常のクエリと **同じラムダの構文** でサブクエリを書きます。

```java
.where(o -> ...)              // サブクエリなし: 引数 1 つ
.where((o, sub) -> ...)       // サブクエリあり: 引数 2 つ
```

lambdaQL は引数の数で 2 つの形を区別します。
サブクエリは `&&`、`||`、`!` と自由に組み合わせられます。static import は必要ありません。

`sub` でできること:

| サブクエリ | 使い方 | HQL |
|---|---|---|
| `sub.from(X.class).where(...).exists()` | 条件 | `exists (select 1 from X x where ...)` |
| `!sub.from(X.class).where(...).exists()` | 条件 | `not exists (...)` |
| `sub.from(X.class).where(...).select(x -> v).contains(y)` | 条件 | `y in (select x.v from X x where ...)` |
| `!... .contains(y)` | 条件 | `y not in (select x.v from X x where ...)` |
| `sub.from(X.class).avg(x -> v)`（`sum`、`min`、`max`、`count()` も） | 値 | `(select avg(x.v) from X x)` |
| `sub.from(X.class)...orderBy(...).limit(1).select(x -> v).first()` | 値 | top-1 サブクエリ |
| `sub.from(A.class).join(B.class, (a, b) -> ...).where((a, b) -> ...).exists()` | 条件 | `exists (select 1 from A a join B b on ... where ...)`（[サブクエリの中の結合](#joins-inside-a-subquery)） |

> [!NOTE]
> 戻り値（`boolean`、`Double`、...）は、コードをコンパイルできるようにするための仮の値にすぎません。
> ラムダが実行されることはありません。これらのメソッドは lambdaQL のラムダの中でだけ意味を持ちます。

内側のラムダ（`sub.from(...).where(...)` の中の `i -> ...`）は、それ自体が `Serializable` である必要はありません。lambdaQL は外側のラムダのバイトコードの中からそれらを見つけます。

HQL では、サブクエリのルートのエイリアスは `s1`、`s2`、... です（外側のルートは `e0`）。サブクエリの中の結合には `s1j1` のようなエイリアスが付きます。

例は、[`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java) からそのままコピーしたものです。
このテストは HQL をチェックし、クエリを実行して、同じ条件で Java 側で絞り込んだ行と比べます。H2、MariaDB、Oracle で通ります。
テストは [3.3](03-where.md#33-basic-form) のテスト用エンティティを使います。`price` は `double`、`status` は `short`、`Customer.id` は `int` で、`Customer.grade` は `Integer` です。テストデータには 5、2、NULL のグレードと、顧客のない注文が 1 件あります。

## 5.2 EXISTS

`qty > 10` の品目を 1 つ以上持つ注文:

[`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java)、リージョン `exists`:

```java
// correlated: the inner lambda uses the outer order o
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId() && i.getQty() > 10).exists());
assertEquals("select e0 from Order e0 where exists (select 1 from Item s1 where s1.order.id = e0.id and s1.qty > 10)",
        q.toHql().hql());
```

**SQL ブロックの読み方。** `…` は省略した select リスト（エンティティのすべてのカラム）で、`?` はバインドパラメーターです。各行は [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java) から取っています。このテストは各クエリを Hibernate 7.4 で H2 2.3、MariaDB 10.11、Oracle 23.26 に対して実行し、Hibernate の SQL が変わると失敗します。

**データベースごとの SQL** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `sub-exists`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id and i1_0.qty>10)
```

これは **相関** サブクエリです。内側のラムダが外側の `o` を使っています（[5.6](#56-correlated-subqueries) を参照）。

## 5.3 NOT EXISTS

`!` を使います。ほかの条件と組み合わせることもできます。
ステータスが 1 の注文、または品目を持たない注文:

[`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java)、リージョン `not-exists`:

```java
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> o.getStatus() == 1 || !sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).exists());
assertEquals("select e0 from Order e0 where e0.status = 1 or not exists (select 1 from Item s1 where s1.order.id = e0.id)",
        q.toHql().hql());
```

**データベースごとの SQL** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `sub-not-exists`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 or not exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id)
```

## 5.4 IN

`grade > 3` の顧客の注文:

[`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java)、リージョン `in`:

```java
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> sub.from(Customer.class).where(c -> c.getGrade() != null && c.getGrade() > 3)
                .select(c -> c.getId()).contains(o.getCustomer().getId()));
assertEquals("select e0 from Order e0 where e0.customer.id in"
        + " (select s1.id from Customer s1 where s1.grade is not null and s1.grade > 3)", q.toHql().hql());
```

**データベースごとの SQL** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `sub-in`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id in (select c2_0.id from customers c2_0 where c2_0.grade is not null and c2_0.grade>3)
```

`o.getCustomer().getId()` は顧客の `@Id` で終わるので、外部キーのカラム `e0.customer.id` になります。結合は必要ありません。
自分で書いた null チェック（`c.getGrade() != null`）は、HQL に `s1.grade is not null` として残ります。

<a id="55-not-in-is-written-as-not-exists"></a>
## 5.5 NOT IN と NULL

`!values.contains(x)` は SQL の意味のまま `NOT IN` として出力されます（決定 D48）。

[`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java)、リージョン `not-in`:

```java
// the SQL meaning (D48): NOT IN. The grades are 5, 2 and NULL, so no row matches: x NOT IN (5, 2, NULL)
// is never true. To skip the NULL, say so in the subquery: where(c -> c.getGrade() != null)
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> !sub.from(Customer.class).select(c -> c.getGrade()).contains((int) o.getStatus()));
assertEquals("select e0 from Order e0 where e0.status not in (select s1.grade from Customer s1)", q.toHql().hql());
SelectQuery<Order> withoutNull = qb.from(Order.class)
        .where((o, sub) -> !sub.from(Customer.class).where(c -> c.getGrade() != null).select(c -> c.getGrade())
                .contains((int) o.getStatus()));
```

**データベースごとの SQL** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `sub-not-in`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status not in (select c2_0.grade from customers c2_0)
```

理由:

- `x NOT IN (subquery)` は、サブクエリが NULL を 1 つでも返すと **1 行も返しません**。lambdaQL はこの SQL の意味を保ち（決定 D48）、`LQL-5009` を `DEBUG` レベルで記録します。
- NULL を除外するには、テストの 2 つ目のクエリのように、サブクエリに `where(c -> c.getGrade() != null)` を書いてください。そうすると、NULL のグレードを除いた Java の `!grades.contains(status)` と同じ結果になります。
- `!sub...exists()` は引き続き `NOT EXISTS` として書かれます。

<a id="56-correlated-subqueries"></a>
## 5.6 相関サブクエリ

内側のラムダが外側のエンティティを使うと、サブクエリは **相関** サブクエリになります。特別な構文は必要ありません。外側の変数をそのまま使うだけです。

```java
.where((o, sub) -> sub.from(Item.class)
                      .where(i -> i.getOrder().getId() == o.getId())   // o は外側から来る
                      .exists())
```

lambdaQL が内側のラムダのキャプチャ値をどう読むか:

| 内側のラムダでキャプチャしたもの | 変換後 |
|---|---|
| 外側のエンティティ（`o`） | 外側のエイリアスへの参照（`e0.id`） |
| それ以外の値（ローカル変数、フィールド、...） | [3.11](03-where.md#311-captured-values) と同じバインドパラメーター |

```java
int minQty = 10;
.where((o, sub) -> sub.from(Item.class)
                      .where(i -> i.getOrder().getId() == o.getId() && i.getQty() > minQty)
                      .exists())
// HQL: select e0 from Order e0
//      where exists (select 1 from Item s1 where s1.order.id = e0.id and s1.qty > :p1)
// パラメーター: {p1=10}
```

[`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java) のテストメソッド `capturedValueInside` が、この HQL、パラメーター、行を確認します。

**データベースごとの SQL（この HQL に対する Hibernate の SQL）** — 同じ HQL を Hibernate に直接渡す [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java) が記録しました。`expected-sql/` の [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)、例 `05-correlated-param`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id and i1_0.qty>?)
```

**相関条件での `==`。** `i.getOrder().getId() == o.getId()` は 2 つの `Long` オブジェクトを比較します。エンティティの値どうしは SQL の意味で比べるので（決定 D48、[3.6](03-where.md#36-null-rules) を参照）、lambdaQL は単純な `=` を書きます（`!=` には `<>`）。
データベースはインデックスを使えます。

<a id="57-scalar-subqueries"></a>
## 5.7 スカラーサブクエリ

**1 つの値** を返すサブクエリは、比較の中で使えます。

価格が平均より高い注文:

[`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java)、リージョン `scalar`:

```java
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> o.getPrice() > sub.from(Order.class).avg(x -> x.getPrice()));
assertEquals("select e0 from Order e0 where e0.price > (select avg(s1.price) from Order s1)", q.toHql().hql());
```

**データベースごとの SQL** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `sub-scalar`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.price>(select avg(o2_0.price) from orders o2_0)
```

スカラーサブクエリは **常に 1 行を返さなければなりません**。使える形は 2 つです（ルール Q3）。

| 形 | 例 |
|---|---|
| 集計: `count()`、`sum`、`avg`、`min`、`max` | `sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).count()` |
| top-1: `orderBy(...).limit(1)...first()` | 下記を参照 |

それ以外の `first()` は解析時にエラー（`TranslationException`、`LQL-3017`）になります。

品目を 2 つ以上持つ注文（`count()`）:

[`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java)、リージョン `count`:

```java
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).count() >= 2);
assertEquals("select e0 from Order e0 where (select count(s1) from Item s1 where s1.order.id = e0.id) >= 2L", q.toHql().hql());
```

**データベースごとの SQL** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `sub-count`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where (select count(i1_0.id) from items i1_0 where i1_0.order_id=o1_0.id)>=2
```

top-1: 顧客ごとの最も高い注文。

[`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java)、リージョン `top-one`:

```java
// the most expensive order of each customer: a correlated scalar subquery with order + limit 1 (rule Q3)
SelectQuery<Order> q = qb.from(Order.class)
        .where((o, sub) -> o.getId().equals(sub.from(Order.class)
                .where(x -> x.getCustomer().getId() == o.getCustomer().getId())
                .orderBy((x, by) -> by.desc(x.getPrice()))
                .limit(1)
                .select(x -> x.getId())
                .first()));
assertEquals("select e0 from Order e0 where e0.id = (select s1.id from Order s1 where s1.customer.id = e0.customer.id"
        + " order by s1.price desc, s1.id asc limit 1)", q.toHql().hql());
// first() without limit(1) is rejected
assertThrows(LambdaQLException.class, () -> qb.from(Order.class)
        .where((o, sub) -> o.getId().equals(sub.from(Order.class).select(x -> x.getId()).first())));
```

**データベースごとの SQL** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `sub-top-one`:

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

lambdaQL は最後のソートキーとして `id`（`s1.id asc`）を追加するので、先頭の行は常に同じです（ルール S9）。
相関条件は顧客のエンティティではなく外部キー（`getCustomer().getId()`）を比較します。[5.10](#510-paths-and-null-inside-subqueries) を参照してください。

集計の NULL は SQL に従います。行がないとき、`sum`、`avg`、`min`、`max` は NULL を返します。`count` は 0 を返します。

<a id="58-nested-subqueries"></a>
## 5.8 入れ子のサブクエリ

サブクエリの `where` も `sub` 引数を受け取れます。どの階層でも同じルールがもう一度適用されます。

大きな品目を含む注文を持つ顧客:

[`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java)、リージョン `nested`:

```java
// customers that have an order with an item of quantity > 10: a subquery inside a subquery
SelectQuery<Customer> q = qb.from(Customer.class)
        .where((c, sub) -> sub.from(Order.class).where((o, sub2) -> o.getCustomer().getId() == c.getId()
                && sub2.from(Item.class).where(i -> i.getOrder().getId() == o.getId() && i.getQty() > 10).exists()).exists());
assertEquals("select e0 from Customer e0 where exists (select 1 from Order s1 where s1.customer.id = e0.id"
        + " and exists (select 1 from Item s2 where s2.order.id = s1.id and s2.qty > 10))", q.toHql().hql());
```

**データベースごとの SQL** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `sub-nested`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from customers c1_0
where exists(select 1 from orders o1_0 where o1_0.customer_id=c1_0.id and exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id and i1_0.qty>10))
```

Java では、入れ子のラムダで同じ名前を 2 回使えません。階層ごとに新しい名前（`sub2`、`o`、`i`）を使ってください。

<a id="59-sorting-inside-a-subquery"></a>
## 5.9 サブクエリの中のソート

| ケース | lambdaQL の動作 |
|---|---|
| `limit` 付きの `orderBy`（top-1、top-N） | `id` のタイブレーカーを付けて残す |
| `limit` なしの `orderBy` | **削除し**、警告をログに出す（結果は変わらない。Oracle では構文エラー ORA-00907 にもなる） |
| `limit` 付きのサブクエリに対する `contains`（MariaDB は `IN (... limit n)` をサポートしない。エラー 1235） | すべてのデータベースで派生テーブルに包む: `in (select t1.v from (select s1.id as v ... limit n) t1)` |
| MariaDB: `lateral`（MariaDB では結果が間違う） | `row_number() over (partition by ... order by ...)` での書き換え: **まだ実装されていません** |

[4. ソートとページング](04-order-paging.md) の NULL のポリシーと `id` の自動タイブレーカーは、サブクエリの中でも適用されます。

<a id="510-paths-and-null-inside-subqueries"></a>
## 5.10 サブクエリの中のパスと NULL

サブクエリの中では、条件、パス、NULL は **外側のクエリとまったく同じように** 動きます（[3. 条件（`where`）](03-where.md)）。

- オブジェクトに対する `==` は SQL の意味による値の比較です（D48）。キャプチャ値の `null` はどの行にも一致しません（`LQL-4045`）。
- サブクエリ自身のエンティティの関連のパスは、外側の `where` と同じ結合のルールに従います。常に内部結合の `join` です（決定 D48）。
- `@Id` で終わるパスは外部キーのカラムです（結合なし）。
- **外側のエンティティ** は、自身のカラムと外部キーの id（`o.getId()`、`o.getCustomer().getId()`）だけを使えます。結合が必要な外側のエンティティのパス（`o.getCustomer().getName()`）は解析時にエラーになります。代わりに外部キーの id を比較してください。
- コレクションのパス（`o.getItems()`）は拒否されます。コレクションについて問い合わせるにはサブクエリを使います。
- エンティティの `@SQLRestriction` と `@SoftDelete` の条件は、サブクエリ（`exists`、`in`）の中にも Hibernate が付けます（MariaDB 10.11、Oracle 23.26 で実測）。lambdaQL はこの条件を別に扱いません。

```java
// o.getItems().size() > 0（拒否される）の代わりに:
.where((o, sub) -> sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).exists())
```

<a id="joins-inside-a-subquery"></a>
### サブクエリの中の結合

サブクエリの中でも 2 つのエンティティを結合できます（決定 D51）。形は `sub.from(A.class).join(B.class, (a, b) -> ...)` と `leftJoin(...)` で、結果は `From2<A, B>` です。
`From2` のすべてのラムダは 2 つの行を受け取ります: `where((a, b) -> ...)`、`exists()`、`count()`、`select((a, b) -> v)` の後の `contains(x)` や `first()`、`max`、`min`、`avg`、`sum((a, b) -> v)`。

- HQL は `exists (select 1 from Order s1 join Item s2 on s2.order.id = s1.id where ...)` のようになります。
- ON 条件の中の関連のパスは `left join` になります。パスの結合は、そのパスが始まるルートの直後に置きます。
- サブクエリの中のエンティティは 2 つまでです。結合の形では `orderBy` と `limit` を使えません。
- `exists`、結合した行の `sum`、`left join` と ON の中の関連のパスを使った `in` を、3 つの DB で実測しました。

[`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java)、リージョン `sub-join`:

```java
// customers with an order that has an item of quantity > 10: the subquery joins Order and Item (D51)
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

<a id="511-subqueries-in-other-places"></a>
## 5.11 ほかの場所でのサブクエリ

- **UPDATE / DELETE の中のサブクエリ**: `where` で同じ `sub` の構文を使います。実装済みで、テスト済みです（リージョン `dml-subquery`）。[7.4 削除](07-4-delete.md) と [7.2 更新](07-2-update.md) を参照してください。
- **`set` の値の中のサブクエリ**（`o.setTotal(sub.from(...).sum(...))`）: 代入ラムダの 2 つ目の引数 `sub` で書きます（決定 D49）。[7.2 更新](07-2-update.md#an-aggregate-from-a-subquery) を参照してください。
- **`from` の中のサブクエリ（派生テーブル）と CTE**: 実装済みです（マイルストーン M8-2）。`sub` は使わず、値のクエリを `qb.from(...)` か `qb.with(...)` に渡します。[6.9 派生テーブル](06-select-group.md#69-derived-table-subquery-in-from) と [6.10 CTE](06-select-group.md#610-cte-with) を参照してください。

<a id="512-rules-summary-q1-to-q6"></a>
## 5.12 ルールのまとめ（Q1〜Q6）

| # | ルール |
|---|---|
| Q1 | サブクエリの中の条件、パス、NULL のルールは外側と同じです。条件の中の関連のパスも同じ結合のルール（デフォルトは内部結合の `join`）に従います。結合が必要な外側のエンティティのパスはエラーで、外部キーの id は使えます |
| Q2 | **NOT IN は SQL の意味を保ちます**（D48）: `!values.contains(x)` は `x not in (select c.sel from C c where <condition>)` になります。サブクエリに NULL があると行は返りません（`DEBUG` レベルの `LQL-5009`）。NULL を除外するには、サブクエリに `c.sel != null` を追加します |
| Q3 | スカラーサブクエリは 1 行を返さなければなりません: 集計（`max`、`min`、`avg`、`sum`、`count`）または `orderBy(...).limit(1)...first()`。それ以外の `first()` はエラーです |
| Q4 | サブクエリのソート: `limit` があれば残し、なければ警告を出して削除します。`IN (... limit)` は派生テーブルに包みます。`row_number()` を使う MariaDB の `lateral` の書き換えはまだ実装されていません |
| Q5 | 相関条件では、`==` を `=`、`!=` を `<>` として書きます（SQL の意味、D48）。どちらかが NULL なら一致しません。インデックスを使えます |
| Q6 | サブクエリの `where` は自分の `sub` 引数を受け取れます: `where((i, sub2) -> ...)`。どの階層でも同じルールが適用されます |

---

[Java トラック](README.md) | 前: [4. ソートとページング](04-order-paging.md) | 次: [6. 選択、集計、グループ化](06-select-group.md) | [このページの Kotlin 版](../kotlin/05-subquery.md)
