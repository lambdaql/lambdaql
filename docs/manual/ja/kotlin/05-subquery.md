<a id="5-subqueries-kotlin"></a>
# 5. サブクエリ（Kotlin）

[Kotlin トラック](README.md) | [マニュアルのホーム](../README.md) | 前: [4. ソートとページング](04-order-paging.md) | 次: [6. 選択、集計、グループ化](06-select-group.md) | [このページの Java 版](../java/05-subquery.md)

共通ページ: [1. 概要](../01-overview.md) · [11. エラーと言語](../11-errors-and-languages.md)

---

<a id="51-the-idea-a-second-lambda-argument-sub"></a>
## 5.1 考え方: 2 つ目のラムダ式の引数 `sub`

`where` のラムダ式は、**2 つ目の引数** としてサブクエリのファクトリーを受け取れます。このマニュアルではこれを `sub` と呼びます（どんな名前でも使えます）。
その中で、普通のクエリと **同じラムダ式の書き方** でサブクエリを書きます。

引数が 1 つの `where { ... }`（`it`）にはサブクエリがなく、引数が 2 つの `where { o, sub -> ... }` にはサブクエリがあります。
lambdaQL は引数の数で 2 つの形を区別します。Kotlin は適切なオーバーロードを選びます。
サブクエリは `&&`、`||`、`!` と自由に組み合わせられます。import は要りません。

`sub` でできること:

| サブクエリ | 用途 | HQL |
|---|---|---|
| `sub.from(X::class.java).where { ... }.exists()` | 条件 | `exists (select 1 from X x where ...)` |
| `!sub.from(X::class.java).where { ... }.exists()` | 条件 | `not exists (...)` |
| `y in sub.from(X::class.java).where { ... }.select { x -> v }` | 条件 | `y in (select x.v from X x where ...)` |
| `y !in sub.from(...)...select { ... }` | 条件 | `y not in (select x.v from X x where ...)` |
| `sub.from(X::class.java).avg { x -> v }`（`sum`、`min`、`max`、`count()` も） | 値 | `(select avg(x.v) from X x)` |
| `sub.from(X::class.java)...orderBy { ... }.limit(1).select { x -> v }.first()` | 値 | top-1 のサブクエリ |
| `sub.from(A::class.java).join(B::class.java) { a, b -> ... }.where { a, b -> ... }.exists()` | 条件 | `exists (select 1 from A a join B b on ... where ...)`（[サブクエリの中の結合](#joins-inside-a-subquery)） |

> [!NOTE]
> 戻り値（`Boolean`、`Double`、...）は、コードをコンパイルするためのプレースホルダーにすぎません。
> これらの関数は lambdaQL のラムダ式の中でだけ使います。lambdaQL はラムダ式を呼び出さず、バイトコードの opcode を解析します。

Kotlin では、`y in values` は `values.contains(y)` にコンパイルされます。そのため、Kotlin の `in` 演算子は追加のコードなしでサブクエリ API と一緒に動きます。

内側のラムダ式（`sub.from(...).where { }` の中の `{ i -> ... }`）は、別に `Serializable` である必要はありません。lambdaQL は外側のラムダ式のバイトコードの opcode からこれを見つけます。

HQL では、サブクエリのルートの別名は `s1`、`s2`、... です（外側のルートは `e0`）。サブクエリの中の結合は `s1j1` のような別名を受け取ります。

例では、Java で書いた例のエンティティを使います。`price` はプリミティブ型の `double`、`qty` は `int`、`status` は `short`、`Customer.id` は `int` です。

## 5.2 EXISTS

`qty > 10` の品目が 1 つ以上ある注文:

<!-- region: KotlinQueryTest.kt#exists -->
```kotlin
val q = qb.from(Order::class.java)
    .where { o, sub -> sub.from(Item::class.java).where { i -> i.order?.id == o.id && i.qty > 10 }.exists() }
assertEquals("select e0 from Order e0 where exists (select 1 from Item s1 where s1.order.id = e0.id and s1.qty > 10)",
    q.toHql().hql())
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

コード例: [`KotlinQueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)（`exists`）。

## 5.3 NOT EXISTS

`!` を使います。他の条件と組み合わせられます。

<!-- region: ManualKotlinSubqueryTest.kt#not-exists -->
```kotlin
// orders with status 1, or orders without items
val q = qb.from(Order::class.java)
    .where { o, sub -> o.status.toInt() == 1 || !sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
assertEquals("select e0 from Order e0 where e0.status = 1 or not exists (select 1 from Item s1 where s1.order.id = e0.id)",
    q.toHql().hql())
```

**データベースごとの SQL**

<!-- sql: sub-not-exists -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 or not exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id)
```

コード例: [`ManualKotlinSubqueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSubqueryTest.kt)（`not-exists`）。

## 5.4 IN

`grade > 3` の顧客の注文。`in` を使います。

<!-- region: KotlinQueryTest.kt#in -->
```kotlin
val q = qb.from(Order::class.java)
    .where { o, sub -> o.customer?.id in sub.from(Customer::class.java).where { c -> c.grade!! > 3 }.select { c -> c.id } }
assertEquals("select e0 from Order e0 where e0.customer.id in (select s1.id from Customer s1 where s1.grade > 3)",
    q.toHql().hql())
```

`o.customer?.id` は顧客の `@Id` で終わるので、外部キーのカラム `e0.customer.id` です。結合は要りません。
`!!` は無視されます。`c.grade!! > 3` は `s1.grade > 3` です。

コード例: [`KotlinQueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)（`in`）。

<a id="55-not-in-is-written-as-not-exists"></a>
## 5.5 NOT IN と NULL

`x !in values` は、SQL の意味のまま `NOT IN` として出力されます。例では、status がどの顧客のグレードとも同じでない注文を探します。

<!-- region: ManualKotlinSubqueryTest.kt#not-in -->
```kotlin
// the SQL meaning: NOT IN. The grades are 5, 2 and NULL, so no row matches: x NOT IN (5, 2, NULL)
// is never true. To skip the NULL, say so in the subquery: where { c -> c.grade != null }
val q = qb.from(Order::class.java)
    .where { o, sub -> o.status.toInt() !in sub.from(Customer::class.java).select { c -> c.grade } }
assertEquals("select e0 from Order e0 where e0.status not in (select s1.grade from Customer s1)", q.toHql().hql())
val withoutNull = qb.from(Order::class.java)
    .where { o, sub -> o.status.toInt() !in sub.from(Customer::class.java).where { c -> c.grade != null }.select { c -> c.grade } }
assertEquals("select e0 from Order e0 where e0.status not in (select s1.grade from Customer s1 where s1.grade is not null)",
    withoutNull.toHql().hql())
```

`status` は `short`、`grade` は `Int?` なので、`o.status.toInt()` で合わせます。lambdaQL はこの変換を無視します。

**データベースごとの SQL（1 つ目のクエリ）**

<!-- sql: sub-not-in -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status not in (select c2_0.grade from customers c2_0)
```

理由:

- `x NOT IN (subquery)` は、サブクエリが NULL を 1 つでも返すと **行を 1 つも返しません**。lambdaQL はこの SQL の意味を保ち、`LQL-5009` を `DEBUG` レベルでログに出します。
- NULL を飛ばすには、上の例の `withoutNull` のように、サブクエリに null チェック（`where { c -> c.grade != null }`）を書いてください。
  例のデータには NULL のグレードがあるので、1 つ目のクエリは行を返しません。
- `!sub...exists()` は、これまでどおり `NOT EXISTS` として書かれます。

コード例: [`ManualKotlinSubqueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSubqueryTest.kt)（`not-in`）。

<a id="56-correlated-subqueries"></a>
## 5.6 相関サブクエリ

内側のラムダ式が外側のエンティティを使うと、サブクエリは **相関** サブクエリになります。特別な書き方は要りません。外側の変数をそのまま使います。

lambdaQL が内側のラムダ式のキャプチャした値を読む方法:

| 内側のラムダ式でキャプチャしたもの | 変換の結果 |
|---|---|
| 外側のエンティティ（`o`） | 外側の別名への参照（`e0.id`） |
| それ以外の値（ローカルの `val`、プロパティ、...） | [3.11](03-where.md#311-captured-values) と同じバインドパラメーター |

<!-- region: ManualKotlinSubqueryTest.kt#correlated-param -->
```kotlin
// o comes from the outer query; minQty is a captured value: a bind parameter
val minQty = 10
val q = qb.from(Order::class.java)
    .where { o, sub -> sub.from(Item::class.java).where { i -> i.order?.id == o.id && i.qty > minQty }.exists() }
assertEquals("select e0 from Order e0 where exists (select 1 from Item s1 where s1.order.id = e0.id and s1.qty > :p1)",
    q.toHql().hql())
assertEquals(mapOf("p1" to 10), q.toHql().parameters())
```

**相関条件の `==`。** `i.order?.id == o.id` は 2 つの `Long?` の値を比較します。エンティティの値 2 つは SQL の意味で比較するので（[3.6](03-where.md#36-null-rules) を参照）、lambdaQL は単純な `=` を書きます（`!=` は `<>`）。
データベースはインデックスを使えます。

コード例: [`ManualKotlinSubqueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSubqueryTest.kt)（`correlated-param`）。

<a id="57-scalar-subqueries"></a>
## 5.7 スカラーサブクエリ

**1 つの値** を返すサブクエリは、比較に使えます。

平均価格より高い注文:

<!-- region: KotlinQueryTest.kt#scalar -->
```kotlin
val q = qb.from(Order::class.java).where { o, sub -> o.price > sub.from(Order::class.java).avg { x -> x.price } }
assertEquals("select e0 from Order e0 where e0.price > (select avg(s1.price) from Order s1)", q.toHql().hql())
```

**データベースごとの SQL**

<!-- sql: sub-scalar -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.price>(select avg(o2_0.price) from orders o2_0)
```

スカラーサブクエリは **常に 1 行を返さなければ** なりません。許される形は 2 つです。

| 形 | 例 |
|---|---|
| 集約: `count()`、`sum`、`avg`、`min`、`max` | `sub.from(Item::class.java).where { i -> i.order?.id == o.id }.count()` |
| top-1: `orderBy { ... }.limit(1)...first()` | 下を参照 |

それ以外の `first()` は解析時のエラー（`TranslationException`、`LQL-3017`）です。

品目が 2 つ以上ある注文（`count()`）:

<!-- region: ManualKotlinSubqueryTest.kt#count -->
```kotlin
// orders with two or more items
val q = qb.from(Order::class.java)
    .where { o, sub -> sub.from(Item::class.java).where { i -> i.order?.id == o.id }.count() >= 2 }
assertEquals("select e0 from Order e0 where (select count(s1) from Item s1 where s1.order.id = e0.id) >= 2L", q.toHql().hql())
```

**データベースごとの SQL**

<!-- sql: sub-count -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where (select count(i1_0.id) from items i1_0 where i1_0.order_id=o1_0.id)>=2
```

top-1 の例: 同じ顧客のいちばん高い注文。

<!-- region: ManualKotlinSubqueryTest.kt#top-one -->
```kotlin
// the most expensive order of each customer: a correlated scalar subquery with orderBy and limit(1)
val q = qb.from(Order::class.java)
    .where { o, sub ->
        o.id == sub.from(Order::class.java)
            .where { x -> x.customer?.id == o.customer?.id }
            .orderBy { x -> desc(x.price) }
            .limit(1)
            .select { x -> x.id }
            .first()
    }
assertEquals("select e0 from Order e0 where e0.id = (select s1.id from Order s1 where s1.customer.id = e0.customer.id"
    + " order by s1.price desc, s1.id asc limit 1)", q.toHql().hql())
// first() without orderBy and limit(1) is rejected
val e = assertThrows(LambdaQLException::class.java) {
    qb.from(Order::class.java).where { o, sub -> o.id == sub.from(Order::class.java).select { x -> x.id }.first() }
}
assertEquals("LQL-3017", e.code())
```

lambdaQL は、サブクエリの中で最後のソートキーとして `id` を加えます。
相関条件は、顧客のエンティティではなく外部キー（`customer?.id`）を比較します。顧客のない注文は `s1.customer.id = e0.customer.id` が真にならないので、結果から外れます。[5.10](#510-paths-and-null-inside-subqueries) を参照してください。

集約の NULL は SQL に従います。`sum`、`avg`、`min`、`max` は行がなければ NULL を返します。`count` は 0 を返します。

コード例: [`KotlinQueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)（`scalar`）、[`ManualKotlinSubqueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSubqueryTest.kt)（`count`、`top-one`）。

<a id="58-nested-subqueries"></a>
## 5.8 入れ子のサブクエリ

サブクエリの `where` も `sub` 引数を受け取れます。すべての段階で同じルールがもう一度適用されます。

大きな品目のある注文を持つ顧客:

<!-- region: ManualKotlinSubqueryTest.kt#nested -->
```kotlin
// customers that have an order with an item of quantity > 10: a subquery inside a subquery
val q = qb.from(Customer::class.java)
    .where { c, sub ->
        sub.from(Order::class.java)
            .where { o, sub2 ->
                o.customer?.id == c.id &&
                    sub2.from(Item::class.java).where { i -> i.order?.id == o.id && i.qty > 10 }.exists()
            }
            .exists()
    }
assertEquals("select e0 from Customer e0 where exists (select 1 from Order s1 where s1.customer is not null"
    + " and s1.customer.id = e0.id and exists (select 1 from Item s2 where s2.order.id = s1.id and s2.qty > 10))", q.toHql().hql())
```

Kotlin の `o.customer?.id == c.id` は `s1.customer is not null and ...` を加えます。下の SQL は、この条件のない同じ意味の HQL から出たものです。

段階ごとに新しい名前（`sub2`、`o`、`i`）を使ってください。Kotlin は同じ名前の再利用を許しますが、名前の隠蔽（shadowing）の警告を出し、コードも読みにくくなります。

コード例: [`ManualKotlinSubqueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSubqueryTest.kt)（`nested`）。

<a id="59-sorting-inside-a-subquery"></a>
## 5.9 サブクエリの中のソート

| 場合 | lambdaQL の動作 |
|---|---|
| `limit` と一緒に使った `orderBy`（top-1、top-N） | `id` のタイブレーカーとともに残す |
| `limit` のない `orderBy` | **取り除き**、警告をログに出す（結果を変えない。Oracle では構文エラー ORA-00907 にまでなる） |
| `limit` のあるサブクエリに対する `in`（MariaDB は `IN (... limit n)` をサポートしない、エラー 1235） | すべてのデータベースで派生テーブルで包む: `in (select t1.v from (select s1.id as v ... limit n) t1)` |

[4. ソートとページング](04-order-paging.md) の NULL のポリシーと `id` の自動タイブレーカーは、サブクエリの中でも適用されます。

<a id="510-paths-and-null-inside-subqueries"></a>
## 5.10 サブクエリの中のパスと NULL

サブクエリの中では、条件、パス、NULL は **外側のクエリとまったく同じに** 動きます（[3. 条件（`where`）](03-where.md)）。

- エンティティの値どうしの `==` は、SQL の意味の値の比較です。キャプチャした `null` はどの行にも一致しません（`LQL-4045`）。
- `!!` は値を変えません。パスの `?.` と `!!` は同じカラムを指します。
- サブクエリ自身のエンティティに対する関連のパスは、外側の `where` と同じ結合のルールに従います。関連のない行が条件に一致しうるなら `left join`、一致しえないなら内部結合の `join` です。
- `@Id` で終わるパスは外部キーのカラムです（結合なし）。
- **外側のエンティティ** は、自身のカラムと外部キーの id（`o.id`、`o.customer?.id`）だけを使えます。結合が必要な外側のエンティティのパス（`o.customer?.name`）は解析時のエラー（`LQL-3047`）です。代わりに外部キーの id を比較します。
- コレクションのパス（`Customer.orders` のような `@OneToMany`）は拒否されます（`LQL-3010`）。コレクションについて調べるには、サブクエリを使います。
- エンティティの `@SQLRestriction` と `@SoftDelete` の条件は、サブクエリ（`exists`、`in`）の中にも Hibernate が付けます（MariaDB 10.11、Oracle 23.26 で実測）。lambdaQL はこの条件を別に扱いません。

<!-- region: ManualKotlinSubqueryTest.kt#rejected-paths -->
```kotlin
// a collection path is rejected: ask about the collection with a subquery
val collection = assertThrows(LambdaQLException::class.java) {
    qb.from(Customer::class.java).where { it.orders.size > 0 }.toHql()
}
assertEquals("LQL-3010", collection.code())
val withOrders = qb.from(Customer::class.java)
    .where { c, sub -> sub.from(Order::class.java).where { o -> o.customer?.id == c.id }.exists() }
assertEquals("select e0 from Customer e0 where exists (select 1 from Order s1 where s1.customer is not null and s1.customer.id = e0.id)",
    withOrders.toHql().hql())
// a path of the outer entity that needs a join is rejected: compare the foreign key id instead
val outerJoin = assertThrows(LambdaQLException::class.java) {
    qb.from(Order::class.java)
        .where { o, sub -> sub.from(Customer::class.java).where { c -> c.name == o.customer?.name }.exists() }.toHql()
}
assertEquals("LQL-3047", outerJoin.code())
```

<a id="joins-inside-a-subquery"></a>
### サブクエリの中の結合

サブクエリの中でも 2 つのエンティティを結合できます。形は `sub.from(A::class.java).join(B::class.java) { a, b -> ... }` と `leftJoin(...) { ... }` で、結果は `From2<A, B>` です。
`From2` のすべてのラムダ式は 2 つの行を受け取ります: `where { a, b -> ... }`、`exists()`、`count()`、`select { a, b -> v }` の後の `contains(x)` や `first()`、`max`、`min`、`avg`、`sum { a, b -> v }`。

- HQL は `exists (select 1 from Order s1 join Item s2 on s2.order.id = s1.id where ...)` のようになります。
- ON 条件の中の関連のパスは `left join` になります。パスの結合は、そのパスが始まるルートのすぐ後に置きます。
- サブクエリの中のエンティティは 2 つまでです。結合の形では `orderBy` と `limit` は使えません。
- `exists`、結合した行の `sum`、`left join` と ON の中の関連のパスを使った `in` を、3 つの DB で実測しました。

<!-- region: KotlinQueryTest.kt#sub-join -->
```kotlin
// customers with an order that has an item of quantity > 10: the subquery joins Order and Item
val q = qb.from(Customer::class.java).where { c, sub ->
    sub.from(Order::class.java)
        .join(Item::class.java) { o, i -> i.order?.id == o.id }
        .where { o, i -> o.customer?.id == c.id && i.qty > 10 }
        .exists()
}
assertEquals("select e0 from Customer e0 where exists (select 1 from Order s1 join Item s2 on s2.order.id = s1.id"
        + " where s1.customer is not null and s1.customer.id = e0.id and s2.qty > 10)", q.toHql().hql())
```

Kotlin の `o.customer?.id == c.id` は `s1.customer is not null and ...` を加えます（上の例を参照）。

結合した行に対する集約（`sum { a, b -> v }`）:

<!-- region: ManualKotlinSubqueryTest.kt#sub-join-sum -->
```kotlin
// an aggregate over the joined rows: customers whose items have a total quantity over 30
val q = qb.from(Customer::class.java).where { c, sub ->
    sub.from(Order::class.java)
        .join(Item::class.java) { o, i -> i.order?.id == o.id }
        .where { o, _ -> o.customer?.id == c.id }
        .sum { _, i -> i.qty } > 30
}
assertEquals("select e0 from Customer e0 where (select sum(s2.qty) from Order s1 join Item s2 on s2.order.id = s1.id"
    + " where s1.customer is not null and s1.customer.id = e0.id) > 30", q.toHql().hql())
```

コード例: [`ManualKotlinSubqueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSubqueryTest.kt)（`rejected-paths`、`sub-join-sum`）、[`KotlinQueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)（`sub-join`）。

<a id="511-subqueries-in-other-places"></a>
## 5.11 ほかの場所でのサブクエリ

- **update / delete のサブクエリ**: `where` で同じ `sub` の書き方を使います。[7.4 削除（delete）](07-4-delete.md) と [7.2 更新（update）](07-2-update.md) を参照してください。
- **`set` の値の中のサブクエリ**（`o.total = sub.from(...).sum { ... }`）: 代入のラムダ式の 2 つ目の引数 `sub` で書きます。[7.2 更新（update）](07-2-update.md#an-aggregate-from-a-subquery) を参照してください。
- **`from` のサブクエリ（派生テーブル）と CTE**: 実装済み。`sub` を使わず、値のクエリを `qb.from(...)` や `qb.with(...)` に渡します。[6.9 派生テーブル](06-select-group.md#69-derived-table-subquery-in-from) と [6.10 CTE](06-select-group.md#610-cte-with) を参照してください。

<a id="512-rules-summary-q1-to-q6"></a>
## 5.12 ルールのまとめ（Q1〜Q6）

| # | ルール |
|---|---|
| Q1 | サブクエリの中の条件、パス、NULL のルールは外側と同じです。条件の中の関連のパスも同じ結合のルール（関連のない行が条件に一致しうるなら `left join`、そうでなければ内部結合の `join`）に従います。結合が必要な外側のエンティティのパスはエラーで、外部キーの id は許可されます |
| Q2 | **NOT IN は SQL の意味を保ちます**: `x !in values` は `x not in (select c.sel from C c where <condition>)` になります。サブクエリに NULL があると行は返されません（`DEBUG` レベルの `LQL-5009`）。NULL を飛ばすには、サブクエリに `c.sel != null` を追加します |
| Q3 | スカラーサブクエリは 1 行を返さなければなりません: 集約（`max`、`min`、`avg`、`sum`、`count`）または `orderBy { }.limit(1)...first()`。それ以外の `first()` はエラーです |
| Q4 | サブクエリのソート: `limit` があれば残し、なければ警告とともに取り除く。`IN (... limit)` は派生テーブルで包みます |
| Q5 | 相関条件では `==` を `=` で、`!=` を `<>` で書きます（SQL の意味）。どちらかが NULL なら一致しません。インデックスは引き続き使えます |
| Q6 | サブクエリの `where` は自身の `sub` 引数を受け取れます: `where { i, sub2 -> ... }`。すべての段階で同じルールが適用されます |

---

[Kotlin トラック](README.md) | 前: [4. ソートとページング](04-order-paging.md) | 次: [6. 選択、集計、グループ化](06-select-group.md) | [このページの Java 版](../java/05-subquery.md)
