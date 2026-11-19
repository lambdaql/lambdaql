<a id="5-subqueries-kotlin"></a>
# 5. サブクエリ（Kotlin）

[Kotlin トラック](README.md) | [マニュアルのホーム](../README.md) | 前: [4. ソートとページング](04-order-paging.md) | 次: [6. 選択、集計、グループ化](06-select-group.md) | [このページの Java 版](../java/05-subquery.md)

共通ページ: [1. 概要](../01-overview.md) · [11. エラーと言語](../11-errors-and-languages.md)

---

<a id="51-the-idea-a-second-lambda-argument-sub"></a>
## 5.1 考え方: 2 つ目のラムダ引数 `sub`

`where` のラムダは **2 つ目の引数** としてサブクエリのファクトリーを受け取れます。このマニュアルではこれを `sub` と呼びます（どんな名前でも使えます）。
その中では、通常のクエリと **同じラムダの構文** でサブクエリを書きます。

```kotlin
.where { ... }                // サブクエリなし: 引数 1 つ（it）
.where { o, sub -> ... }      // サブクエリあり: 引数 2 つ
```

lambdaQL は引数の数で 2 つの形を区別します。Kotlin は適切なオーバーロードを選びます。
サブクエリは `&&`、`||`、`!` と自由に組み合わせられます。import は必要ありません。

`sub` でできること:

| サブクエリ | 使い方 | HQL |
|---|---|---|
| `sub.from(X::class.java).where { ... }.exists()` | 条件 | `exists (select 1 from X x where ...)` |
| `!sub.from(X::class.java).where { ... }.exists()` | 条件 | `not exists (...)` |
| `y in sub.from(X::class.java).where { ... }.select { x -> v }` | 条件 | `y in (select x.v from X x where ...)` |
| `y !in sub.from(...)...select { ... }` | 条件 | `y not in (select x.v from X x where ...)` |
| `sub.from(X::class.java).avg { x -> v }`（`sum`、`min`、`max`、`count()` も） | 値 | `(select avg(x.v) from X x)` |
| `sub.from(X::class.java)...orderBy { ... }.limit(1).select { x -> v }.first()` | 値 | top-1 サブクエリ |
| `sub.from(A::class.java).join(B::class.java) { a, b -> ... }.where { a, b -> ... }.exists()` | 条件 | `exists (select 1 from A a join B b on ... where ...)`（[サブクエリの中の結合](#joins-inside-a-subquery)） |

> [!NOTE]
> 戻り値（`Boolean`、`Double`、...）は、コードをコンパイルできるようにするための仮の値にすぎません。
> ラムダが実行されることはありません。これらの関数は lambdaQL のラムダの中でだけ意味を持ちます。

Kotlin では、`y in values` は `values.contains(y)` にコンパイルされます。そのため、Kotlin の `in` 演算子は追加のコードなしでサブクエリの API と一緒に使えます。

内側のラムダ（`sub.from(...).where { }` の中の `{ i -> ... }`）は、それ自体が `Serializable` である必要はありません。lambdaQL は外側のラムダのバイトコードの中からそれを見つけます。

HQL では、サブクエリのルートのエイリアスは `s1`、`s2`、... です（外側のルートは `e0`）。サブクエリの中の結合には `s1j1` のようなエイリアスが付きます。

**(テスト済み)** と書かれた例は [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt) から取ったものです。このテストは Java のテスト用エンティティを使います。`price` はプリミティブの `double`、`qty` は `int` です。
Java で書いたほかのクエリのテストは [`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java) にあります（H2、MariaDB、Oracle）。それらの例は [このページの Java 版](../java/05-subquery.md) を参照してください。

## 5.2 EXISTS

`qty > 10` の品目を 1 つ以上持つ注文:

```kotlin
qb.from(Order::class.java)
    .where { o, sub ->
        sub.from(Item::class.java)
            .where { i -> i.order?.id == o.id && i.qty > 10 }
            .exists()
    }
    .toList()
// HQL: select e0 from Order e0
//      where exists (select 1 from Item s1 where s1.order.id = e0.id and s1.qty > 10)
```

**(テスト済み)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)、リージョン `exists`:

```kotlin
val q = qb.from(Order::class.java)
    .where { o, sub -> sub.from(Item::class.java).where { i -> i.order?.id == o.id && i.qty > 10 }.exists() }
assertEquals("select e0 from Order e0 where exists (select 1 from Item s1 where s1.order.id = e0.id and s1.qty > 10)",
    q.toHql().hql())
```

**SQL ブロックの読み方。** `…` は省略した select リスト（エンティティのすべてのカラム）で、`?` はバインドパラメーターです。各行は [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java) から取っています。このテストは各クエリを Hibernate 6.6.5 で H2 2.3、MariaDB 10.11、Oracle 23.26 に対して実行し、Hibernate の SQL が変わると失敗します。このテストは Java のラムダを使います。意味が同じ Kotlin のラムダは同じ HQL になるので、SQL も同じです。

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `sub-exists`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id and i1_0.qty>10)
```

これは **相関** サブクエリです。内側のラムダが外側の `o` を使っています（[5.6](#56-correlated-subqueries) を参照）。

## 5.3 NOT EXISTS

`!` を使います。ほかの条件と組み合わせることもできます。

```kotlin
// ステータスが 1 の注文、または品目を持たない注文
qb.from(Order::class.java)
    .where { o, sub ->
        o.status == 1 ||
            !sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists()
    }
    .toList()
// HQL: select e0 from Order e0
//      where e0.status = 1 or not exists (select 1 from Item s1 where s1.order.id = e0.id)
```

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `sub-not-exists`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 or not exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id)
```

## 5.4 IN

`grade > 3` の顧客の注文。`in` を使います。

```kotlin
qb.from(Order::class.java)
    .where { o, sub ->
        o.customer?.id in sub.from(Customer::class.java)
            .where { c -> c.grade!! > 3 }
            .select { c -> c.id }
    }
    .toList()
// HQL: select e0 from Order e0
//      where e0.customer.id in (select s1.id from Customer s1 where s1.grade > 3)
```

**(テスト済み)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)、リージョン `in`:

```kotlin
val q = qb.from(Order::class.java)
    .where { o, sub -> o.customer?.id in sub.from(Customer::class.java).where { c -> c.grade!! > 3 }.select { c -> c.id } }
assertEquals("select e0 from Order e0 where e0.customer.id in (select s1.id from Customer s1 where s1.grade > 3)",
    q.toHql().hql())
```

**データベースごとの SQL（この HQL に対する Hibernate の SQL）** — 同じ HQL を Hibernate に直接渡す [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java) が記録しました。Java のテスト（リージョン `in`）には null チェック `c.getGrade() != null` もあり、その SQL は例 `sub-in` です。`expected-sql/` の [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)、例 `05-in-subquery`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id in (select c2_0.id from customers c2_0 where c2_0.grade>3)
```

`o.customer?.id` は顧客の `@Id` で終わるので、外部キーのカラム `e0.customer.id` になります。結合は必要ありません。
`!!` は無視されます（決定 D23）。`c.grade!! > 3` は `s1.grade > 3` です。

<a id="55-not-in-is-written-as-not-exists"></a>
## 5.5 NOT IN と NULL

`x !in values` は SQL の意味のまま `NOT IN` として出力されます（決定 D48）。

```kotlin
// ステータスがどの顧客のグレードとも等しくない注文
qb.from(Order::class.java)
    .where { o, sub -> o.status !in sub.from(Customer::class.java).select { c -> c.grade } }
    .toList()
// HQL: select e0 from Order e0
//      where e0.status not in (select s1.grade from Customer s1)
```

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `sub-not-in`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status not in (select c2_0.grade from customers c2_0)
```

理由:

- `x NOT IN (subquery)` は、サブクエリが NULL を 1 つでも返すと **1 行も返しません**。lambdaQL はこの SQL の意味を保ち（決定 D48）、`LQL-5009` を `DEBUG` レベルで記録します。
- NULL を除外するには、`sub.from(Customer::class.java).where { c -> c.grade != null }.select { c -> c.grade }` のようにサブクエリに null チェックを書いてください。
  Java のテストは、NULL のグレードを含むデータで両方のクエリを確認します。
- `!sub...exists()` は引き続き `NOT EXISTS` として書かれます。

<a id="56-correlated-subqueries"></a>
## 5.6 相関サブクエリ

内側のラムダが外側のエンティティを使うと、サブクエリは **相関** サブクエリになります。特別な構文は必要ありません。外側の変数をそのまま使います。

```kotlin
.where { o, sub ->
    sub.from(Item::class.java)
        .where { i -> i.order?.id == o.id }   // o は外側から来る
        .exists()
}
```

lambdaQL が内側のラムダのキャプチャ値をどう読むか:

| 内側のラムダでキャプチャしたもの | 変換後 |
|---|---|
| 外側のエンティティ（`o`） | 外側のエイリアスへの参照（`e0.id`） |
| それ以外の値（ローカルの `val`、プロパティ、...） | [3.11](03-where.md#311-captured-values) と同じバインドパラメーター |

```kotlin
val minQty = 10
.where { o, sub ->
    sub.from(Item::class.java)
        .where { i -> i.order?.id == o.id && i.qty > minQty }
        .exists()
}
// HQL: select e0 from Order e0
//      where exists (select 1 from Item s1 where s1.order.id = e0.id and s1.qty > :p1)
// パラメーター: {p1=10}
```

[`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java) の Java のテストメソッド `capturedValueInside` が、この HQL、パラメーター、行を確認します。

**データベースごとの SQL（この HQL に対する Hibernate の SQL）** — 同じ HQL を Hibernate に直接渡す [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java) が記録しました。`expected-sql/` の [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)、例 `05-correlated-param`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id and i1_0.qty>?)
```

**相関条件での `==`。** `i.order?.id == o.id` は 2 つの `Long?` の値を比較します。エンティティの値どうしは SQL の意味で比べるので（決定 D48、[3.6](03-where.md#36-null-rules) を参照）、lambdaQL は単純な `=` を書きます（`!=` には `<>`）。
データベースはインデックスを使えます。

<a id="57-scalar-subqueries"></a>
## 5.7 スカラーサブクエリ

**1 つの値** を返すサブクエリは、比較の中で使えます。

価格が平均より高い注文:

```kotlin
qb.from(Order::class.java)
    .where { o, sub -> o.price!! > sub.from(Order::class.java).avg { x -> x.price } }
    .toList()
// HQL: select e0 from Order e0
//      where e0.price > (select avg(s1.price) from Order s1)
```

**(テスト済み)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)、リージョン `scalar`（テストの `price` は `double` なので `!!` はありません）:

```kotlin
val q = qb.from(Order::class.java).where { o, sub -> o.price > sub.from(Order::class.java).avg { x -> x.price } }
assertEquals("select e0 from Order e0 where e0.price > (select avg(s1.price) from Order s1)", q.toHql().hql())
```

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `sub-scalar`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.price>(select avg(o2_0.price) from orders o2_0)
```

スカラーサブクエリは **常に 1 行を返さなければなりません**。使える形は 2 つです（ルール Q3）。

| 形 | 例 |
|---|---|
| 集計: `count()`、`sum`、`avg`、`min`、`max` | `sub.from(Item::class.java).where { i -> i.order?.id == o.id }.count()` |
| top-1: `orderBy { ... }.limit(1)...first()` | 下記を参照 |

それ以外の `first()` は解析時のエラー（`TranslationException`、`LQL-3017`）です。

品目を 2 つ以上持つ注文（`count()`）:

```kotlin
qb.from(Order::class.java)
    .where { o, sub -> sub.from(Item::class.java).where { i -> i.order?.id == o.id }.count() >= 2 }
    .toList()
// HQL: select e0 from Order e0
//      where (select count(s1) from Item s1 where s1.order.id = e0.id) >= 2L
```

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `sub-count`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where (select count(i1_0.id) from items i1_0 where i1_0.order_id=o1_0.id)>=2
```

top-1 の例: 同じ顧客の最も高い注文。

```kotlin
qb.from(Order::class.java)
    .where { o, sub ->
        o.id == sub.from(Order::class.java)
            .where { x -> x.customer?.id == o.customer?.id }
            .orderBy { x, by -> by.desc(x.price) }
            .limit(1)
            .select { x -> x.id }
            .first()
    }
    .toList()
```

同じクエリを Java でテストしました（リージョン `top-one`、例 `sub-top-one`）。lambdaQL はサブクエリの中で最後のソートキーとして `id` を追加します（ルール S9）。
Java のテストでは、`price` と `Customer.id` はプリミティブです。Kotlin のエンティティの nullable な `price: Double?` と `customer?.id` には、[3.6](03-where.md#36-null-rules) と [4.4](04-order-paging.md#44-null-ordering-is-the-same-on-every-database) の NULL のルールが適用されます。そのため、HQL の `nulls` の部分と比較の方法が変わることがあります。
相関条件は顧客のエンティティではなく外部キー（`customer?.id`）を比較します。[5.10](#510-paths-and-null-inside-subqueries) を参照してください。

集計の NULL は SQL に従います。行がないとき、`sum`、`avg`、`min`、`max` は NULL を返します。`count` は 0 を返します。

<a id="58-nested-subqueries"></a>
## 5.8 入れ子のサブクエリ

サブクエリの `where` も `sub` 引数を受け取れます。どの階層でも同じルールがもう一度適用されます。

大きな品目を含む注文を持つ顧客:

```kotlin
qb.from(Customer::class.java)
    .where { c, sub ->
        sub.from(Order::class.java)
            .where { o, sub2 ->
                o.customer?.id == c.id &&
                    sub2.from(Item::class.java)
                        .where { i -> i.order?.id == o.id && i.qty > 10 }
                        .exists()
            }
            .exists()
    }
    .toList()
// HQL: select e0 from Customer e0
//      where exists (select 1 from Order s1 where s1.customer.id = e0.id
//                    and exists (select 1 from Item s2 where s2.order.id = s1.id and s2.qty > 10))
```

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `sub-nested`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from customers c1_0
where exists(select 1 from orders o1_0 where o1_0.customer_id=c1_0.id and exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id and i1_0.qty>10))
```

階層ごとに新しい名前（`sub2`、`o`、`i`）を使ってください。Kotlin では同じ名前をもう一度使えますが、名前のシャドーイングについて警告が出て、コードも読みにくくなります。

<a id="59-sorting-inside-a-subquery"></a>
## 5.9 サブクエリの中のソート

| ケース | lambdaQL の動作 |
|---|---|
| `limit` 付きの `orderBy`（top-1、top-N） | `id` のタイブレーカーを付けて残す |
| `limit` なしの `orderBy` | **削除し**、警告をログに出す（結果は変わらない。Oracle では構文エラー ORA-00907 にもなる） |
| `limit` 付きのサブクエリに対する `in`（MariaDB は `IN (... limit n)` をサポートしない。エラー 1235） | すべてのデータベースで派生テーブルに包む: `in (select t1.v from (select s1.id as v ... limit n) t1)` |
| MariaDB: `lateral`（MariaDB では結果が間違う） | `row_number() over (partition by ... order by ...)` での書き換え: **まだ実装されていません** |

[4. ソートとページング](04-order-paging.md) の NULL のポリシーと `id` の自動タイブレーカーは、サブクエリの中でも適用されます。

<a id="510-paths-and-null-inside-subqueries"></a>
## 5.10 サブクエリの中のパスと NULL

サブクエリの中では、条件、パス、NULL は **外側のクエリとまったく同じように** 動きます（[3. 条件（`where`）](03-where.md)）。

- エンティティの値どうしの `==` は SQL の意味による値の比較です（D48）。キャプチャ値の `null` はどの行にも一致しません（`LQL-4045`）。
- `!!` は無視されます。パスの `?.` は同じカラムを指します。
- サブクエリ自身のエンティティの関連のパスは、外側の `where` と同じ結合のルールに従います。常に内部結合の `join` です（決定 D48）。
- `@Id` で終わるパスは外部キーのカラムです（結合なし）。
- **外側のエンティティ** は、自身のカラムと外部キーの id（`o.id`、`o.customer?.id`）だけを使えます。結合が必要な外側のエンティティのパス（`o.customer?.name`）は解析時のエラーです。代わりに外部キーの id を比較します。
- コレクションのパス（`o.items`）は拒否されます。コレクションについて問い合わせるにはサブクエリを使います。
- エンティティの `@SQLRestriction` と `@SoftDelete` の条件は、サブクエリ（`exists`、`in`）の中にも Hibernate が付けます（MariaDB 10.11、Oracle 23.26 で実測）。lambdaQL はこの条件を別に扱いません。

```kotlin
// it.items.size > 0 の代わりに（拒否される）:
.where { o, sub -> sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
```

<a id="joins-inside-a-subquery"></a>
### サブクエリの中の結合

サブクエリの中でも 2 つのエンティティを結合できます（決定 D51）。形は `sub.from(A::class.java).join(B::class.java) { a, b -> ... }` と `leftJoin(...) { ... }` で、結果は `From2<A, B>` です。
`From2` のすべてのラムダは 2 つの行を受け取ります: `where { a, b -> ... }`、`exists()`、`count()`、`select { a, b -> v }` の後の `contains(x)` や `first()`、`max`、`min`、`avg`、`sum { a, b -> v }`。

- HQL は `exists (select 1 from Order s1 join Item s2 on s2.order.id = s1.id where ...)` のようになります。
- ON 条件の中の関連のパスは `left join` になります。パスの結合は、そのパスが始まるルートの直後に置きます。
- サブクエリの中のエンティティは 2 つまでです。結合の形では `orderBy` と `limit` を使えません。
- `exists`、結合した行の `sum`、`left join` と ON の中の関連のパスを使った `in` を、3 つの DB で実測しました。

**(テスト済み)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)、リージョン `sub-join`:

```kotlin
// customers with an order that has an item of quantity > 10: the subquery joins Order and Item (D51)
val q = qb.from(Customer::class.java).where { c, sub ->
    sub.from(Order::class.java)
        .join(Item::class.java) { o, i -> i.order?.id == o.id }
        .where { o, i -> o.customer?.id == c.id && i.qty > 10 }
        .exists()
}
assertEquals("select e0 from Customer e0 where exists (select 1 from Order s1 join Item s2 on s2.order.id = s1.id"
        + " where s1.customer is not null and s1.customer.id = e0.id and s2.qty > 10)", q.toHql().hql())
```

Kotlin の `o.customer?.id == c.id` は `s1.customer is not null and ...` を追加します（上のリージョンを参照）。集計の形は Java のテスト（[`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java)、リージョン `sub-join`）が確認します。

<a id="511-subqueries-in-other-places"></a>
## 5.11 ほかの場所でのサブクエリ

- **update / delete の中のサブクエリ**: `where` で同じ `sub` の構文を使います。実装済みです（Java のラムダでテスト済み、リージョン `dml-subquery`）。[7.4 削除](07-4-delete.md) と [7.2 更新](07-2-update.md) を参照してください。
- **`set` の値の中のサブクエリ**（`o.total = sub.from(...).sum { ... }`）: 代入ラムダの 2 つ目の引数 `sub` で書きます（決定 D49）。[7.2 更新](07-2-update.md#an-aggregate-from-a-subquery) を参照してください。
- **`from` の中のサブクエリ（派生テーブル）と CTE**: 実装済みです（マイルストーン M8-2）。`sub` は使わず、値のクエリを `qb.from(...)` か `qb.with(...)` に渡します。[6.9 派生テーブル](06-select-group.md#69-derived-table-subquery-in-from) と [6.10 CTE](06-select-group.md#610-cte-with) を参照してください。

<a id="512-rules-summary-q1-to-q6"></a>
## 5.12 ルールのまとめ（Q1〜Q6）

| # | ルール |
|---|---|
| Q1 | サブクエリの中の条件、パス、NULL のルールは外側と同じです。条件の中の関連のパスも同じ結合のルール（デフォルトは内部結合の `join`）に従います。結合が必要な外側のエンティティのパスはエラーで、外部キーの id は使えます |
| Q2 | **NOT IN は SQL の意味を保ちます**（D48）: `x !in values` は `x not in (select c.sel from C c where <condition>)` になります。サブクエリに NULL があると行は返りません（`DEBUG` レベルの `LQL-5009`）。NULL を除外するには、サブクエリに `c.sel != null` を追加します |
| Q3 | スカラーサブクエリは 1 行を返さなければなりません: 集計（`max`、`min`、`avg`、`sum`、`count`）または `orderBy { }.limit(1)...first()`。それ以外の `first()` はエラーです |
| Q4 | サブクエリのソート: `limit` があれば残し、なければ警告を出して削除します。`IN (... limit)` は派生テーブルに包みます。`row_number()` を使う MariaDB の `lateral` の書き換えはまだ実装されていません |
| Q5 | 相関条件では、`==` を `=`、`!=` を `<>` として書きます（SQL の意味、D48）。どちらかが NULL なら一致しません。インデックスを引き続き使えます |
| Q6 | サブクエリの `where` は自分の `sub` 引数を受け取れます: `where { i, sub2 -> ... }`。どの階層でも同じルールが適用されます |

---

[Kotlin トラック](README.md) | 前: [4. ソートとページング](04-order-paging.md) | 次: [6. 選択、集計、グループ化](06-select-group.md) | [このページの Java 版](../java/05-subquery.md)
