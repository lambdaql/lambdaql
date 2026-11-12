<a id="5-subqueries-kotlin"></a>
# 5. サブクエリ（Kotlin）

> [!NOTE]
> **状況**
> - **設計済み、まだ実装されていません**（マイルストーン M8）。
> - Kotlin の構文はコンパイルでき、そのバイトコードはプロトタイプで確認しました（kotlinc 2.3.21）。SQL の形は、H2 2.3、MariaDB 10.11、Oracle 23ai で Hibernate 6.6.5 を使って確認しました。
> - この章の HQL は **予定の** 出力です。

[Kotlin トラック](README.md) | [マニュアルのホーム](../README.md) | 前: [4. ソートとページング](04-order-paging.md) | 次: [6. 選択、集計、グループ化](06-select-group.md) | [このページの Java 版](../java/05-subquery.md)

共通ページ: [1. 概要](../01-overview.md) · [11. エラーと言語](../11-errors-and-languages.md)

---

<a id="51-the-idea-a-second-lambda-argument-sub"></a>
## 5.1 考え方: 2 つ目のラムダ引数 `sub`

`where` のラムダは **2 つ目の引数** を受け取れます。サブクエリのファクトリーです。このマニュアルでは `sub` と呼びます（どんな名前でも使えます）。
その中で、普通のクエリと **同じラムダの構文** でサブクエリを書きます。

```kotlin
.where { ... }                // サブクエリなし: 引数は 1 つ（it）
.where { o, sub -> ... }      // サブクエリあり: 引数は 2 つ
```

lambdaQL は引数の数で 2 つの形を区別します。Kotlin は正しいオーバーロードを選びます。
サブクエリは `&&`、`||`、`!` と自由に組み合わせられます。import は必要ありません。

`sub` でできること:

| サブクエリ | 使い方 | HQL |
|---|---|---|
| `sub.from(X::class.java).where { ... }.exists()` | 条件 | `exists (select 1 from X x where ...)` |
| `!sub.from(X::class.java).where { ... }.exists()` | 条件 | `not exists (...)` |
| `y in sub.from(X::class.java).where { ... }.select { x -> v }` | 条件 | `y in (select x.v from X x where ...)` |
| `y !in sub.from(...)...select { ... }` | 条件 | `not exists (... and x.v = y)` |
| `sub.from(X::class.java).avg { x -> v }`（`sum`、`min`、`max`、`count()` も） | 値 | `(select avg(x.v) from X x)` |
| `sub.from(X::class.java)...orderBy { ... }.limit(1).select { x -> v }.first()` | 値 | top-1 のサブクエリ |

> [!NOTE]
> 戻り値（`Boolean`、`Double`、...）は、コードをコンパイルできるようにするための仮の値にすぎません。
> ラムダが実行されることはありません。これらの関数は lambdaQL のラムダの中でだけ意味を持ちます。

Kotlin では、`y in values` は `values.contains(y)` にコンパイルされます。そのため、Kotlin の `in` 演算子は追加のコードなしでサブクエリの API と一緒に使えます。

内側のラムダ（`sub.from(...).where { }` の中の `{ i -> ... }`）は、それ自体が `Serializable` である必要はありません。lambdaQL は外側のラムダのバイトコードの中からそれを見つけます。

## 5.2 EXISTS

`qty > 10` のアイテムを 1 つ以上持つ注文:

```kotlin
qb.from(Order::class.java)
    .where { o, sub ->
        sub.from(Item::class.java)
            .where { i -> i.order?.id == o.id && i.qty > 10 }
            .exists()
    }
    .toList()
// HQL: select o from Order o
//      where exists (select 1 from Item i where i.order.id = o.id and i.qty > 10)
```

**SQL ブロックの読み方。** `…` は省略した select リスト（エンティティのすべてのカラム）で、`?` はバインドパラメーターです。[`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java) はこのページの HQL を Hibernate に直接渡し、H2 2.3、MariaDB 10.11、Oracle 23.26（Hibernate 6.6.5）での SQL を記録します。lambdaQL はまだこれらのクエリを作りません。Hibernate の SQL が変わるとテストは失敗します。

**データベースごとの SQL（予定）** — 予定の HQL を Hibernate が変換したもので、`PlannedSqlTest` が記録しました。lambdaQL はまだこのクエリを作りません。`expected-sql/` の [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)、例 `05-exists`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id and i1_0.qty>10)
```

これは **相関** サブクエリです。内側のラムダが外側の `o` を使っています（[5.6](#56-correlated-subqueries) を参照）。

## 5.3 NOT EXISTS

`!` を使います。ほかの条件と組み合わせられます。

```kotlin
// status が 1 の注文、またはアイテムのない注文
qb.from(Order::class.java)
    .where { o, sub ->
        o.status == 1 ||
            !sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists()
    }
    .toList()
// HQL: select o from Order o
//      where o.status = 1
//         or not exists (select 1 from Item i where i.order.id = o.id)
```

**データベースごとの SQL（予定）** — 予定の HQL を Hibernate が変換したもので、`PlannedSqlTest` が記録しました。lambdaQL はまだこのクエリを作りません。`expected-sql/` の [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)、例 `05-not-exists-or`:

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
// HQL: select o from Order o
//      where o.customer.id in (select c.id from Customer c where c.grade > 3)
```

**データベースごとの SQL（予定）** — 予定の HQL を Hibernate が変換したもので、`PlannedSqlTest` が記録しました。lambdaQL はまだこのクエリを作りません。`expected-sql/` の [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)、例 `05-in-subquery`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id in (select c2_0.id from customers c2_0 where c2_0.grade>3)
```

`o.customer?.id` は顧客の `@Id` で終わるので、外部キーのカラム `o.customer.id` です。join は必要ありません。

<a id="55-not-in-is-written-as-not-exists"></a>
## 5.5 NOT IN は NOT EXISTS として書かれる

`x !in values` は `NOT IN` として出力 **されません**。lambdaQL は `NOT EXISTS` を書きます。

```kotlin
qb.from(Order::class.java)
    .where { o, sub ->
        o.customer?.id !in sub.from(Customer::class.java)
            .where { c -> c.grade!! > 3 }
            .select { c -> c.id }
    }
    .toList()
// HQL: select o from Order o
//      where not exists (select 1 from Customer c
//                        where c.grade > 3 and c.id = o.customer.id)
```

**データベースごとの SQL（予定）** — 予定の HQL を Hibernate が変換したもので、`PlannedSqlTest` が記録しました。lambdaQL はまだこのクエリを作りません。`expected-sql/` の [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)、例 `05-not-in`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where not exists(select 1 from customers c2_0 where c2_0.grade>3 and c2_0.id=o1_0.customer_id)
```

理由:

- `x NOT IN (subquery)` は、サブクエリが NULL を 1 つでも返すと **1 行も返しません**。これはよくある SQL の落とし穴です。
- `NOT EXISTS` にはこの問題がありません。データベースも（アンチジョインとして）効率よく実行します。
- 結果: 顧客のない注文は上のクエリで返されます。その注文に一致する顧客がいないからです。

<a id="56-correlated-subqueries"></a>
## 5.6 相関サブクエリ

内側のラムダが外側のエンティティを使うと、サブクエリは **相関** サブクエリになります。特別な構文は必要ありません。外側の変数をそのまま使うだけです。

```kotlin
.where { o, sub ->
    sub.from(Item::class.java)
        .where { i -> i.order?.id == o.id }   // o は外側から来る
        .exists()
}
```

lambdaQL が内側のラムダのキャプチャ値をどう読むか:

| 内側のラムダでキャプチャしたもの | 変換先 |
|---|---|
| 外側のエンティティ（`o`） | 外側のエイリアスへの参照（`o.id`） |
| それ以外の値（ローカルの `val`、プロパティ、...） | バインドパラメーター。[3.11](03-where.md#311-captured-values) と同じ |

```kotlin
val minQty = 10
.where { o, sub ->
    sub.from(Item::class.java)
        .where { i -> i.order?.id == o.id && i.qty > minQty }
        .exists()
}
// HQL: exists (select 1 from Item i where i.order.id = o.id and i.qty > :p1)
```

**データベースごとの SQL（予定）** — 予定の HQL を Hibernate が変換したもので、`PlannedSqlTest` が記録しました。lambdaQL はまだこのクエリを作りません。`expected-sql/` の [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)、例 `05-correlated-param`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id and i1_0.qty>?)
```

**相関の中の `==`。** `i.order?.id == o.id` は 2 つの `Long?` の値を比較します。通常のルールでは、これは null-safe 比較です（`is not distinct from`、[3.6](03-where.md#36-null-rules) を参照）。
しかし `o.id` は `@Id` なので、決して NULL になりません。この場合、lambdaQL は単純な `=` を書きます（`!=` には `(a <> b or a is null)`）。
結果は同じで、データベースはインデックスを使えます。Oracle では `is not distinct from` は `decode(...)` になり、インデックスを使えません。

<a id="57-scalar-subqueries"></a>
## 5.7 スカラーサブクエリ

**1 つの値** を返すサブクエリは、比較の中で使えます。

平均価格より高い注文:

```kotlin
qb.from(Order::class.java)
    .where { o, sub -> o.price!! > sub.from(Order::class.java).avg { x -> x.price } }
    .toList()
// HQL: select o from Order o
//      where o.price > (select avg(x.price) from Order x)
```

**データベースごとの SQL（予定）** — 予定の HQL を Hibernate が変換したもので、`PlannedSqlTest` が記録しました。lambdaQL はまだこのクエリを作りません。`expected-sql/` の [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)、例 `05-scalar-avg`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.price>(select avg(o2_0.price) from orders o2_0)
```

スカラーサブクエリは **常に 1 行** を返さなければなりません。使える形は 2 つです。

| 形 | 例 |
|---|---|
| 集約: `count()`、`sum`、`avg`、`min`、`max` | `sub.from(Item::class.java).where { i -> i.order?.id == o.id }.count()` |
| top-1: `orderBy { ... }.limit(1)...first()` | 下を参照 |

それ以外の `first()` は解析時のエラーになります。

top-1 の例: 同じ顧客の最新の注文。

```kotlin
qb.from(Order::class.java)
    .where { o, sub ->
        o.id == sub.from(Order::class.java)
            .where { x -> x.customer?.id == o.customer?.id }
            .orderBy { x, by -> by.desc(x.updateAt) }
            .limit(1)
            .select { x -> x.id }
            .first()
    }
    .toList()
```

集約の NULL は SQL に従います。`sum`、`avg`、`min`、`max` は行がないと NULL を返します。`count` は 0 を返します。

<a id="58-nested-subqueries"></a>
## 5.8 ネストしたサブクエリ

サブクエリの `where` も `sub` 引数を受け取れます。どの階層でも同じルールがもう一度当てはまります。

大きなアイテムを含む注文を持つ顧客:

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
// HQL: select c from Customer c
//      where exists (select 1 from Order o
//                    where o.customer.id = c.id
//                      and exists (select 1 from Item i
//                                  where i.order.id = o.id and i.qty > 10))
```

**データベースごとの SQL（予定）** — 予定の HQL を Hibernate が変換したもので、`PlannedSqlTest` が記録しました。lambdaQL はまだこのクエリを作りません。`expected-sql/` の [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)、例 `05-nested`:

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
| `limit` がある `orderBy`（top-1、top-N） | 残す |
| `limit` がない `orderBy` | **取り除き**、警告をログに出す（結果は変わらない。Oracle では構文エラー ORA-00907 にもなる） |
| MariaDB: `IN (... limit n)`（MariaDB はサポートしない） | 自動で包む: `in (select x.id from (... limit n) x)` |
| MariaDB: `lateral`（MariaDB では結果が間違う） | `row_number() over (partition by ... order by ...)` で書き換える |

[4. ソートとページング](04-order-paging.md) の NULL のポリシーと自動の `id` タイブレーカーは、サブクエリの中にも当てはまります。

<a id="510-paths-and-null-inside-subqueries"></a>
## 5.10 サブクエリの中のパスと NULL

サブクエリの中では、条件、パス、NULL は **外側のクエリとまったく同じ** に動きます（[3. 条件](03-where.md)）。

- nullable な 2 つの値の間の `==` は、null-safe な値の比較です。
- `!!` は無視されます。パスの `?.` は同じカラムを指します。
- サブクエリの条件の中の関連のパスは、外側の `where` と同じ結合のルールに従います（決定 D36）。デフォルトは内部結合の `join` で、null ガードが関連のない行を残す場合は `left join` です。
- `@Id` で終わるパスは外部キーのカラムです（join なし）。
- コレクションのパス（`o.items`）は拒否されます。コレクションについて調べるにはサブクエリを使います。

```kotlin
// it.items.size > 0（拒否される）の代わりに:
.where { o, sub -> sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
```

<a id="511-subqueries-in-other-places"></a>
## 5.11 ほかの場所でのサブクエリ

- **`from` の中のサブクエリ（派生テーブル）と CTE**（`qb.from { sub -> ... }`、`qb.with { sub -> ... }`）: [6. 選択、集計、グループ化](06-select-group.md) を参照してください。
- **update / delete の中のサブクエリ**: 同じ `sub` の構文です。[7. 更新、挿入、削除](07-dml.md) を参照してください。

<a id="512-rules-summary-q1-to-q6"></a>
## 5.12 ルールのまとめ（Q1 から Q6）

| # | ルール |
|---|---|
| Q1 | サブクエリの中の条件、パス、NULL のルールは外側と同じ。条件の中の関連のパスも同じ結合のルールを使う（デフォルトは内部結合の `join`） |
| Q2 | **NOT IN は NOT EXISTS として出力される**: `x !in values` は `not exists (select 1 from C c where <condition> and c.sel = x)` になる。これで `NOT IN` + NULL の落とし穴を避ける |
| Q3 | スカラーサブクエリは 1 行を返さなければならない: 集約（`max`、`min`、`avg`、`sum`、`count`）または `orderBy { }.limit(1)...first()`。それ以外の `first()` はエラー |
| Q4 | サブクエリのソート: `limit` があれば残り、`limit` がなければ警告付きで取り除かれる。MariaDB の `IN (... limit)` は包まれ、`lateral` は `row_number()` で書き換えられる |
| Q5 | 相関の中で、片側が決して NULL にならない（`@Id`、`nullable = false`、プリミティブ）とき、`==` は `=` として書かれる。`!=` は `(a <> b or a is null)` になる。これでインデックスを使える |
| Q6 | サブクエリの `where` は自分の `sub` 引数を受け取れる: `where { i, sub2 -> ... }`。どの階層でも同じルールが当てはまる |

---

[Kotlin トラック](README.md) | 前: [4. ソートとページング](04-order-paging.md) | 次: [6. 選択、集計、グループ化](06-select-group.md) | [このページの Java 版](../java/05-subquery.md)
