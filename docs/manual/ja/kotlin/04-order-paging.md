<a id="4-sorting-and-paging-kotlin"></a>
# 4. ソートとページング（Kotlin）

[Kotlin トラック](README.md) | [マニュアルのホーム](../README.md) | 前: [3. 条件（`where`）](03-where.md) | 次: [5. サブクエリ](05-subquery.md) | [このページの Java 版](../java/04-order-paging.md)

共通ページ: [1. 概要](../01-overview.md) · [11. エラーと言語](../11-errors-and-languages.md)

---

<a id="41-one-lambda-for-the-whole-sort"></a>
## 4.1 ラムダ式によるソートの表現

ソートは **1 つのラムダ式** で書きます。ラムダ式はエンティティを受け取り、クラス `By` のソート関数でキーと方向を記述します。
`desc(...)`、`asc(...)` をつなげてキーを追加します。

<!-- region: KotlinQueryTest.kt#sort -->
```kotlin
import org.lambdaql.query.By.desc

val q = qb.from(Order::class.java)
    .where { it.isActive }
    .orderBy { o -> desc(o.price).asc(o.customer?.name) }
    .limit(3)
assertEquals("select e0 from Order e0 left join e0.customer j1 where e0.active = true"
    + " order by e0.price desc, j1.name asc nulls first, e0.id asc", q.toHql().hql())
```

例では、Java で書いた例のエンティティ（`Order`、`Customer`、`Item`、`OrderLine`）を使います。`price` はプリミティブ型の `double` なので `nulls` の部分はなく、`active` の getter は `isActive()` です（Kotlin では `it.isActive`）。
`limit` を使うと、lambdaQL は最後のソートキーとして `e0.id asc` を付け加えます（[タイブレーカー](#tie-breaker-automatic-id) を参照）。

- ソート関数は `By` の static メソッドです: `asc(key)`、`desc(key)`、`asc(key, Nulls.FIRST)`、`desc(key, Nulls.LAST)`、`sort(key, Direction)`。
- lambdaQL はラムダ式を呼び出さず、バイトコードの opcode を解析します。ソート関数は順序を記述するだけです。ソートのラムダ式の外で呼ぶと `IllegalStateException`（`LQL-4046`）が発生します。

コード例: [`KotlinQueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)（`sort`）。

<a id="42-the-short-forms-one-key"></a>
## 4.2 短い書き方（キーが 1 つ）

キーが 1 つだけなら、ソート関数なしでキーだけを返してもかまいません。

| Kotlin | HQL |
|---|---|
| `orderBy { it.price }` | `order by e0.price asc` |
| `orderBy({ it.updateAt }, Direction.DESC)` | `order by e0.updateAt desc nulls last` |

<!-- region: ManualKotlinSortTest.kt#short-form -->
```kotlin
// one key: the key alone, ascending unless a Direction is given
val byPrice = qb.from(Order::class.java).orderBy { it.price }
assertEquals("select e0 from Order e0 order by e0.price asc", byPrice.toHql().hql())
val newest = qb.from(Order::class.java).orderBy({ it.updateAt }, Direction.DESC)
assertEquals("select e0 from Order e0 order by e0.updateAt desc nulls last", newest.toHql().hql())
```

`Direction` がなければ、短い書き方は昇順（`ASC`）でソートします。
`Direction` 引数はキー 1 つにだけ使います。`orderBy({ o -> desc(o.price) }, Direction.ASC)` のようにソート関数と `Direction` 引数を一緒に使うと、`LQL-3069` で拒否されます。方向を引数で受け取る必要があるなら、`orderBy { o -> sort(o.price, direction) }` を使ってください。

コード例: [`ManualKotlinSortTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSortTest.kt)（`short-form`）。

## 4.3 `Direction`

`Direction` には値が 6 つあります。

| 値 | 意味 | NULL の位置 |
|---|---|---|
| `ASC` | 昇順 | NULL が **最初**（デフォルトのポリシー） |
| `DESC` | 降順 | NULL が **最後**（デフォルトのポリシー） |
| `ASC_NULLS_FIRST` | 昇順 | NULL が最初。`ASC` と同じ |
| `ASC_NULLS_LAST` | 昇順 | NULL が最後 |
| `DESC_NULLS_FIRST` | 降順 | NULL が最初 |
| `DESC_NULLS_LAST` | 降順 | NULL が最後。`DESC` と同じ |

`Direction` にはヘルパーメソッドも 2 つあります。

- `Direction.parse(text)` は、リクエストパラメーターのようなテキストから方向を読みます。大文字と小文字は区別せず、`-`、`_`、空白は同じものとして扱います: `Direction.parse("desc-nulls-last")` は `DESC_NULLS_LAST` です。不明なテキストは `IllegalArgumentException` を投げます。
- `reversed()` は逆の方向を返します。NULL の位置も一緒に反転するので、順序全体が逆になります: `Direction.ASC.reversed()` は `DESC` で、`Direction.ASC_NULLS_LAST.reversed()` は `DESC_NULLS_FIRST` です。

**NULL の位置を選ぶには**、2 番目の引数に enum の `Nulls` を使ってください: `asc(key, Nulls.LAST)`、`desc(key, Nulls.FIRST)`。
`Nulls.FIRST` は「NULL が最初」、`Nulls.LAST` は「NULL が最後」という意味です。
2 番目の引数がなければ、`asc(key)` と `desc(key)` はデフォルトの NULL ポリシーを使います（[4.4](#44-null-ordering-is-the-same-on-every-database) を参照）。

| 書いたもの | 同じ意味 |
|---|---|
| `asc(key)` | `ASC`（NULL が最初、デフォルトのポリシー） |
| `desc(key)` | `DESC`（NULL が最後、デフォルトのポリシー） |
| `asc(key, Nulls.FIRST)` | `ASC_NULLS_FIRST` |
| `asc(key, Nulls.LAST)` | `ASC_NULLS_LAST` |
| `desc(key, Nulls.FIRST)` | `DESC_NULLS_FIRST` |
| `desc(key, Nulls.LAST)` | `DESC_NULLS_LAST` |

**方向が変数から来るときは**、キャプチャした `Direction` と一緒に `sort(key, direction)` を使ってください。

<!-- region: ManualKotlinSortTest.kt#nulls-and-direction -->
```kotlin
// the second argument: true = NULL first, false = NULL last
val nullsLast = qb.from(Order::class.java).orderBy { o -> asc(o.description, Nulls.LAST).asc(o.id) }
assertEquals("select e0 from Order e0 order by e0.description asc nulls last, e0.id asc", nullsLast.toHql().hql())
// a direction from a variable: choose it outside the lambda
val dir = if (newestFirst) Direction.DESC else Direction.ASC
val byDate = qb.from(Order::class.java).orderBy { o -> sort(o.updateAt, dir).asc(o.id) }
assertEquals("select e0 from Order e0 order by e0.updateAt desc nulls last, e0.id asc", byDate.toHql().hql())
// the short form with a NULL position
val byName = qb.from(Order::class.java).orderBy({ it.customer?.name }, Direction.DESC_NULLS_LAST)
assertEquals("select e0 from Order e0 left join e0.customer j1 order by j1.name desc nulls last", byName.toHql().hql())
```

ラムダ式は 1 回だけ解析します。`dir` はクエリを実行するたびに読みます。同じラムダ式に別の方向を渡すと、HQL と結果の行はその方向に従います。方向が `null` なら、クエリの実行時に例外が発生します。

`dir` はラムダ式の **外で** 選んでください。ソートのラムダ式の中の `if` は拒否されます（[4.6](#46-rules-for-the-lambda-body) を参照）。

コード例: [`ManualKotlinSortTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSortTest.kt)（`nulls-and-direction`）。

<a id="44-null-ordering-is-the-same-on-every-database"></a>
## 4.4 NULL の順序はどのデータベースでも同じ

NULL の位置はデータベースによって違います。

| | H2 | MariaDB | Oracle |
|---|---|---|---|
| `ASC` のデフォルト | NULL が最初 | NULL が最初 | NULL が **最後** |
| `DESC` のデフォルト | NULL が最後 | NULL が最後 | NULL が **最初** |
| SQL の `NULLS FIRST/LAST` | サポート | **非サポート（構文エラー）** | サポート |

lambdaQL は 1 つのポリシーでこれを解決します: **NULL はいちばん小さい値です**。

- `ASC` = `ASC_NULLS_FIRST`
- `DESC` = `DESC_NULLS_LAST`

これは Kotlin のコンパレーター `nullsFirst(naturalOrder())` と同じです。
このポリシーを使うと、3 つのデータベースすべてが同じ順序を返します（数値と日付の場合）。

**lambdaQL の書き方。** NULL になりうるキーには、lambdaQL は常に HQL に NULL の位置を書きます。例: `e0.description asc nulls first`。
NULL になれないキー（`active: Boolean` や `qty: Int` のような JVM のプリミティブ型、または `@Id`）には `nulls` の部分はありません。
lambdaQL が見るのは Kotlin の型ではなく、JPA のメタデータです。そのため、`@Id` である `id`（Kotlin では `Long?`）には `nulls` の部分がなく、`description`（`String?`）には付きます（[4.10](#410-reusable-and-external-sorts) を参照）。
関連の先のカラムは、関連がないと NULL になるので（顧客のない注文がありうる）付きます。パスのすべての関連の段階が必須（`optional = false`）で、値が NULL になれないなら付きません。

次に Hibernate がデータベースごとの SQL を書きます。データベースのデフォルトと同じなら、この句は省きます。
MariaDB には `NULLS` 構文がないので、デフォルトでない位置は Hibernate が `CASE` 式で書きます。

生成される SQL:

| 書いたもの | H2 | MariaDB | Oracle |
|---|---|---|---|
| `asc(key)`、`desc(key)`（`ASC`、`DESC`） | 追加の句なし（すでにデフォルト） | 追加の句なし | `nulls first` / `nulls last` を追加 |
| DB のデフォルトと同じ NULL の位置（`asc(key, nulls)`、`desc(key, nulls)`、`*_NULLS_*`） | 追加の句なし | 追加の句なし | 追加の句なし |
| DB のデフォルトでは **ない** NULL の位置 | `nulls first/last` | Hibernate が `case when x is null ...` を書く | `nulls first/last` |
| NULL になれないキー（方向に関係なく） | 追加の句なし | 追加の句なし | 追加の句なし |

> [!TIP]
> MariaDB では、`asc(key, Nulls.LAST)`（`ASC_NULLS_LAST`）と `desc(key, Nulls.FIRST)`（`DESC_NULLS_FIRST`）は `CASE` 式になります。データベースはこのソートにインデックスを使えません。できれば `asc(key)` / `desc(key)` を使ってください。

<a id="45-what-a-sort-key-can-be"></a>
## 4.5 ソートキーにできるもの

| キー | 許可 | 備考 |
|---|---|---|
| カラム: `o.price` | はい | nullable なキー（`Double?`、`String?`）も問題ない |
| to-one のパス: `o.customer?.name`、`o.customer!!.name` | はい | `left join`（必須の関連は inner join）、下を参照 |
| `@Lob` のカラム | はい | Oracle は拒否する。`DEBUG` レベルの `LQL-5010`、下を参照 |
| `@Enumerated(EnumType.STRING)` の enum のカラム | はい | 名前の順にソートされる（宣言の順ではない）。`DEBUG` レベルの `LQL-5011`、下を参照 |
| 算術: `o.price * 2`、`+`、`-` | はい | `(e0.price * 2.0)` |
| 拡大変換: `o.status.toLong()` | はい | 変換は HQL に現れない |
| boolean でない値のエルビス: `o.description ?: ""` | はい | `coalesce(e0.description, '') asc nulls first` |
| エンティティを使わない値: `asc(1)`、キャプチャした値 | **拒否される** | [4.6](#46-rules-for-the-lambda-body) を参照 |
| キーの値の中の `if`: `asc(if (o.isActive) 0 else 1)` | はい | `case when`（[3.5.1](03-where.md#351-values-chosen-by-a-condition)） |
| ソート関数を選ぶ `if` | **拒否される**（`LQL-2023`） | [4.6](#46-rules-for-the-lambda-body) を参照 |

<!-- region: ManualKotlinSortTest.kt#sort-keys -->
```kotlin
// arithmetic, a widening conversion and the Elvis operator of a value
assertEquals("select e0 from Order e0 order by (e0.price * 2.0) asc",
    qb.from(Order::class.java).orderBy { o -> asc(o.price * 2) }.toHql().hql())
assertEquals("select e0 from Order e0 order by e0.status asc",
    qb.from(Order::class.java).orderBy { o -> asc(o.status.toLong()) }.toHql().hql())
val elvis = qb.from(Order::class.java).orderBy { o -> asc(o.description ?: "").asc(o.id) }
assertEquals("select e0 from Order e0 order by coalesce(e0.description, '') asc nulls first, e0.id asc", elvis.toHql().hql())
```

**関連のパスは `left join` を使います。** HQL では、単純なパス `order by o.customer.name` は 3 つのデータベースすべてで inner join になります。顧客のない注文は消えます。
lambdaQL は代わりに `left join` を追加します。顧客のない注文も結果に残ります。そのような注文の `j1.name` は NULL なので、`asc` では最初に来ます。

<!-- region: ManualKotlinSortTest.kt#sort-path -->
```kotlin
// a path used only for sorting is a left join: the orders without a customer stay
val q = qb.from(Order::class.java).orderBy { o -> asc(o.customer?.name).asc(o.id) }
assertEquals("select e0 from Order e0 left join e0.customer j1 order by j1.name asc nulls first, e0.id asc", q.toHql().hql())
```

**データベースごとの SQL**

<!-- sql: sort-path -->
```sql
-- H2, MariaDB
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
order by c1_0.name,o1_0.id

-- Oracle
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
order by c1_0.name asc nulls first,o1_0.id
```

SQL で NULL のポリシーを確認できます。H2 と MariaDB は、自身のデフォルトと同じなら句を省きます。Oracle は自身のデフォルトが逆なので、`asc nulls first` / `desc nulls last` を書きます。MariaDB には `NULLS` 構文がないので、デフォルトでない位置は、まず `case when … is null then … end` でソートします。

**必須の関連は inner join を使います。** `optional = false` でマッピングした関連（例: `@ManyToOne(optional = false)`）は対象が常にあるので、inner join で行を失いません。
そのため、ソートや select にだけ使ったパスでも、このような関連は `join` を使います。それ以外の関連は、上のように `left join` を使います。
パスのすべての関連の段階が必須で、値が NULL になれないなら、ソートに `nulls first/last` も付きません。

例のエンティティ `OrderLine` の `order` は `@ManyToOne(optional = false)` です。

<!-- region: ManualKotlinSortTest.kt#required-association -->
```kotlin
// order is optional = false: a sort or select path through it cannot lose rows, so it is an inner join
val sorted = qb.from(OrderLine::class.java).orderBy { l -> asc(l.order.price) }
assertEquals("select e0 from OrderLine e0 join e0.order j1 order by j1.price asc", sorted.toHql().hql())
val prices = qb.from(OrderLine::class.java).select { it.order.price }
assertEquals("select j1.price from OrderLine e0 join e0.order j1", prices.toHql().hql())
```

**`@Lob` のカラムのソート。** `@Lob` のカラムを `orderBy`、`distinct`、`groupBy` に使うと、H2 と MariaDB は実行しますが、Oracle は拒否します（ORA-22848、ORA-22849、実測）。`=` の比較は 3 つのデータベースすべてで動きます。
lambdaQL はクエリを変えず、カラムと使う場所ごとに 1 回、`LQL-5010` を `DEBUG` レベルでログに出します。

下の 3 つのクエリは、それぞれ `LQL-5010` を 1 回ログに出します。

<!-- region: ManualKotlinSortTest.kt#lob -->
```kotlin
// memo is @Lob: H2 and MariaDB sort it, group it and make it distinct, Oracle rejects it (ORA-22848, ORA-22849).
// lambdaQL keeps the query and logs LQL-5010 at DEBUG
qb.from(OrderLine::class.java).orderBy { l -> asc(l.memo) }.toHql()
qb.from(OrderLine::class.java).groupBy { it.memo }.select { it.memo }.toHql()
qb.from(OrderLine::class.java).select { it.memo }.distinct().toHql()
```

**`EnumType.STRING` の enum のソート。** `@Enumerated(EnumType.STRING)` でマッピングした enum のカラムは、データベースが定数の **名前** の文字列の順でソートします。Java の enum の比較は宣言の順に従うので、結果の順序が違うことがあります。
lambdaQL はクエリを変えず、カラムごとに 1 回、`LQL-5011` を `DEBUG` レベルでログに出します。`ORDINAL`（または `@Enumerated` なし）は宣言の順でソートするので、ログには出しません。

下のクエリは `LQL-5011` を 1 回ログに出します。

<!-- region: ManualKotlinSortTest.kt#enum-string-sort -->
```kotlin
// kind is @Enumerated(EnumType.STRING): the database sorts by name, Kotlin by declaration order (LQL-5011, DEBUG)
val q = qb.from(Order::class.java).orderBy { o -> asc(o.kind) }
assertEquals("select e0 from Order e0 order by e0.kind asc nulls first", q.toHql().hql())
```

別名は `where` と共有します。`where` が同じパスをすでに結合していれば、ソートはその結合を使います。
`where` がこのパスを inner join で使ったなら、顧客のない注文はすでに除かれています。`where` が `left join` で使ったパスなら、ソートもその `left join` を使います（[3.10](03-where.md#310-association-paths) を参照）。

コード例: [`ManualKotlinSortTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSortTest.kt)（`sort-keys`、`sort-path`、`required-association`、`lob`、`enum-string-sort`）。

<a id="46-rules-for-the-lambda-body"></a>
## 4.6 ラムダ式の本体のルール

本体は **キー 1 つ** か、**`By` の関数をまっすぐつないだチェーン** でなければなりません。

<!-- region: ManualKotlinSortTest.kt#lambda-rules -->
```kotlin
// allowed: a straight chain of by calls
qb.from(Order::class.java).orderBy { o -> desc(o.price).asc(o.id) }
// rejected when orderBy is called: sort functions chosen by a condition (a condition inside one key is allowed)
val branch = assertThrows(LambdaQLException::class.java) {
    qb.from(Order::class.java).orderBy { o -> if (flag) desc(o.price) else asc(o.id) }
}
assertEquals("LQL-2023", branch.code())
// rejected: a key that does not use the entity
val constant = assertThrows(LambdaQLException::class.java) {
    qb.from(Order::class.java).orderBy { _ -> asc(1) }
}
assertEquals("LQL-3031", constant.code())
```

lambdaQL は `orderBy` を呼ぶときにこれを確認し、`LambdaQLException` を投げます（条件で選んだソート関数は `LQL-2023`、エンティティを使わないキーは `LQL-3031`）。
すべてのキーはエンティティを使わなければなりません。定数やキャプチャした値だけのキーは拒否されます。

実行中に方向を変える必要があるなら、変数で渡してください: `sort(key, dir)`。キーそのものを変える必要があるなら、普通の Kotlin のコードの 2 つの分岐で、それぞれクエリを作ってください。

コード例: [`ManualKotlinSortTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSortTest.kt)（`lambda-rules`）。

<a id="47-order-of-calls"></a>
## 4.7 呼び出しの順序

クエリは `from(...).where { }.orderBy { }.limit(...).offset(...)` の順に書きます。
`orderBy` なしで `limit` や `offset` だけを使うと、`order by` は付きません（[4.8](#48-paging) を参照）。

<a id="48-paging"></a>
## 4.8 ページング

<!-- region: ManualKotlinSortTest.kt#page -->
```kotlin
val page = qb.from(Order::class.java)
    .where { it.status.toInt() == 1 }
    .orderBy { o -> desc(o.price) }
    .limit(2)      // page size
    .offset(2)     // skip 2 rows (page 2)
// limit and offset go to Hibernate; the id is added as the last key
assertEquals("select e0 from Order e0 where e0.status = 1 order by e0.price desc, e0.id asc", page.toHql().hql())
```

`limit` と `offset` は Hibernate に渡されます（`setMaxResults`、`setFirstResult`）。例のエンティティの `status` は `short` なので、`it.status.toInt() == 1` で比較します。lambdaQL はこの変換を無視します。

`limit` と `offset` は 0 以上の値を受け取ります。負の数を渡すと `IllegalArgumentException` が発生します。

lambdaQL は HQL に `LIMIT` を書きません。Hibernate がデータベースごとに適切な SQL を書きます。

| データベース | SQL |
|---|---|
| H2 | `offset ? rows fetch first ? rows only` |
| MariaDB | `limit ?, ?` |
| Oracle | `offset ? rows fetch first ? rows only` |

**`orderBy` なしのページング。** `orderBy` なしで `limit` と `offset` を呼ぶと、`order by` は付けません。どの行がどのページに来るかはデータベースが決めます。ページを安定して分けるには、`orderBy` を使ってください。

<!-- region: ManualKotlinSortTest.kt#page-only -->
```kotlin
// paging without orderBy: no order by, so which rows come is up to the database; use orderBy for stable pages
val q = qb.from(Order::class.java).where { it.isActive }.limit(2)
assertEquals("select e0 from Order e0 where e0.active = true", q.toHql().hql())
```

**データベースごとの SQL**

<!-- sql: page-only -->
```sql
-- H2, Oracle
select …
from orders o1_0
where o1_0.active=true
order by o1_0.id
fetch first ? rows only

-- MariaDB
select …
from orders o1_0
where o1_0.active=true
order by o1_0.id
limit ?
```

ページングの SQL はデータベースによって違います。H2 と Oracle は `fetch first ? rows only`、MariaDB は `limit ?` を受け取ります。offset があると、H2 と Oracle は `offset ? rows` を加え、MariaDB は `limit ?,?` を書きます。

**最初の行と全体の件数。**

- `orderBy { ... }.first()` は、その順序の最初の行を `Optional` で返します。読むのは 1 行だけです。`offset` はそのまま使われます: `orderBy { ... }.offset(1).first()` は 2 番目の行を返します。
- `count()` は条件に合うすべての行を数えます。ページ付きの画面では、where 段階のクエリを値に入れておきます。その値で `count()` を呼んで全体の件数を得て、続けて `orderBy { ... }.limit(...).toList()` を呼んでページを得ます。

<!-- region: ManualKotlinSortTest.kt#first-and-count -->
```kotlin
// keep the where step in a value: count() gives the number of all rows, orderBy ... limit gives one page
val active = qb.from(Order::class.java).where { it.isActive }
val total = active.count()
val page = active.orderBy { o -> desc(o.price) }.limit(2).toList()
// first() reads one row; offset(1).first() is the second row
val second = active.orderBy { o -> desc(o.price) }.offset(1).first()
```

[結果を受け取る](02-getting-started.md#getting-the-result) を参照してください。

<a id="tie-breaker-automatic-id"></a>
### タイブレーカー（`id` の自動追加）

上の HQL の最後には、lambdaQL が追加した `e0.id asc` があります。理由は次のとおりです。

- 複数の行が同じ `price` を持つことがあります。データベースはそのような行を **任意の順序で** 返せます。Oracle は実際にそうします（測定済み）。
- すると、2 ページ目と 3 ページ目に同じ行が 2 回出たり、ある行が抜けたりすることがあります。

ルール: **`orderBy` とページングを一緒に使い、ソートキーに id がなければ、lambdaQL はルートの `@Id asc` を最後のキーとして追加します。**

- 追加するのは 1 回だけです。キーにすでに id があれば（例: `desc(o.id)`）追加しません。
- ソートキーに主エンティティの一意で NULL になれないカラムがあれば、追加しません。`nullable = false` の `@Column(unique = true)`、Hibernate の `@NaturalId`、`@Id` がこれにあたり、このキーだけですべての行の順序が決まるからです。
  NULL になりうる一意のカラムはあたりません。NULL は複数の行にありうるからです。
- `orderBy` がないか、`limit` と `offset` がなければ、何も追加しません。
- 明示的な結合（`join`、`leftJoin`、`crossJoin`）があれば、結合したすべてのエンティティの id も結合の順に追加します（[6.8](06-select-group.md#68-explicit-joins) を参照）。

例のエンティティ `OrderLine` の `code` は `@Column(unique = true, nullable = false)` です。

<!-- region: ManualKotlinSortTest.kt#unique-key -->
```kotlin
// code is @Column(unique = true, nullable = false): it orders every row, so paging adds no id tie-breaker
assertEquals("select e0 from OrderLine e0 order by e0.code asc",
    qb.from(OrderLine::class.java).orderBy { l -> asc(l.code) }.limit(2).toHql().hql())
// a key that is not unique still gets the id tie-breaker
assertEquals("select e0 from OrderLine e0 join e0.order j1 order by j1.price asc, e0.id asc",
    qb.from(OrderLine::class.java).orderBy { l -> asc(l.order.price) }.limit(2).toHql().hql())
```

コード例: [`ManualKotlinSortTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSortTest.kt)（`page`、`page-only`、`first-and-count`、`unique-key`）。

<a id="49-strings-and-special-values"></a>
## 4.9 文字列と特殊な値

- 文字列の順序はデータベースの照合順序に従います。MariaDB の `_ci` は大文字と小文字を無視します。Oracle は JVM のロケールに従う `NLS_SORT` を使います（例: `de_DE` ならドイツ語の順序）。Oracle では `NLS_SORT=BINARY` を推奨します。
- Oracle は `''` を NULL として保存するので、空文字列は NULL のようにソートされます。
- `NaN` の順序はデータベースによって違います。

<a id="410-reusable-and-external-sorts"></a>
## 4.10 再利用できるソートと外部からのソート

**`SortSpec`: 保存しておけるソート。** `SortSpec` は、値に保存しておき、複数のクエリで使うソートです。
`SortSpec.of(Order::class.java) { o -> desc(o.id) }` のように作ります。ラムダ式は `orderBy { o -> ... }` のラムダ式と同じなので、同じルールが適用されます（[4.6](#46-rules-for-the-lambda-body) を参照）。
それから `orderBy(spec)` に渡します。

<!-- region: ManualKotlinSortTest.kt#sort-spec -->
```kotlin
// keep it in a value (for example at the top level or in a companion object) and use it in many queries
val byDescription = SortSpec.of(Order::class.java) { o -> desc(o.description).asc(o.id) }
val q = qb.from(Order::class.java).where { it.isActive }.orderBy(byDescription)
assertEquals("select e0 from Order e0 where e0.active = true order by e0.description desc nulls last, e0.id asc",
    q.toHql().hql())
```

- `SortSpec` は変更できません。`then()` と `reversed()` は新しい spec を返します。そのため、1 つの spec を複数のクエリとスレッドで安全に共有できます。たとえばトップレベルの `val` に置いてもかまいません。
- lambdaQL は、spec が `orderBy` で使われるときにラムダ式を検査します。各ラムダ式は 1 回だけ解析されます。
- キャプチャした値は、ラムダ式が作られた時点の値です。トップレベルの `val` の spec は、その値を持ち続けます。実行時に方向を変える必要があるなら、`orderBy { o -> sort(key, dir) }` を使ってください（[4.3](#43-direction) を参照）。
- `orderBy(spec)` の後でもページングできます: `limit`、`offset`、`toList()`、`first()`。

**データベースごとの SQL**

<!-- sql: sort-spec -->
```sql
-- H2, MariaDB
select …
from orders o1_0
where o1_0.active=true
order by o1_0.description desc,o1_0.id

-- Oracle
select …
from orders o1_0
where o1_0.active=true
order by o1_0.description desc nulls last,o1_0.id
```

SQL は、同じソートを `orderBy { o -> ... }` で書いたときと同じです。Oracle は `desc` のデフォルトが逆なので、`nulls last` を付けます（[4.4](#44-null-ordering-is-the-same-on-every-database) を参照）。

**`then()`: 既存のキーの後にキーを追加。** `a.then(b)` は、まず `a` のキーでソートします。値が同じ行は、続けて `b` のキーでソートします。
`then()` は別の `SortSpec` か、ソートのラムダ式を受け取ります。

<!-- region: ManualKotlinSortTest.kt#sort-spec-then -->
```kotlin
val byStatus = SortSpec.of(Order::class.java) { o -> asc(o.status) }
val byPrice = SortSpec.of(Order::class.java) { o -> desc(o.price) }
val q = qb.from(Order::class.java).orderBy(byStatus.then(byPrice))
assertEquals("select e0 from Order e0 order by e0.status asc, e0.price desc", q.toHql().hql())
// then() also takes a sort lambda
assertEquals("select e0 from Order e0 order by e0.status asc, e0.id desc",
    qb.from(Order::class.java).orderBy(byStatus.then { o -> desc(o.id) }).toHql().hql())
```

`status`（`short`）と `price`（`double`）は NULL になれないので、HQL に `nulls` の部分はありません。

**`reversed()`: すべてのキーを逆に。** `spec.reversed()` はすべてのキーを反転します。NULL の位置も一緒に反転します: `asc`（NULL が先）は `desc`（NULL が後）になります。そのため、行の順序全体が正確に逆になります。
これは `Direction.reversed()` と同じです（[4.3](#43-direction) を参照）。`spec.reversed().reversed()` は元の順序に戻ります。

<!-- region: ManualKotlinSortTest.kt#sort-spec-reversed -->
```kotlin
val spec = SortSpec.of(Order::class.java) { o -> asc(o.description).asc(o.id) }
// every key is reversed, the NULL position too: asc nulls first -> desc nulls last
val q = qb.from(Order::class.java).orderBy(spec.reversed())
assertEquals("select e0 from Order e0 order by e0.description desc nulls last, e0.id desc", q.toHql().hql())
```

`reversed()` の結果の行は、元の順序を正確に逆にした順序です。

**データベースごとの SQL**

<!-- sql: sort-spec-reversed -->
```sql
-- H2, MariaDB
select …
from orders o1_0
order by o1_0.description desc,o1_0.id desc

-- Oracle
select …
from orders o1_0
order by o1_0.description desc nulls last,o1_0.id desc
```

H2 と MariaDB は `desc` ですでに NULL を後に置くので、追加の構文を書きません。Oracle は `nulls last` を付けます。

**`SortSpec.ofPath`: プロパティ名から作るソート。** `SortSpec.ofPath(Order::class.java, "customer.name", Direction.DESC)` は、プロパティのパスから spec を作ります。コードの外から文字列で来るソートキーに使い、Spring モジュールは Spring の `Sort` をこの方法で変換します（[12.5](12-spring-data-jpa.md#125-spring-sort-and-pageable) を参照）。
パスの各部分は永続プロパティでなければならず、最後の部分は基本の値でなければなりません。それ以外のパスはエラーです。
許可する名前はソートポリシーが決めます（下の [ソートポリシー](#sort-policy) を参照）。ポリシーも `@Sortable` フィールドもなければ、どの永続プロパティでも受け付け、lambdaQL が `LQL-5003` をログに出します（デフォルトのレベルは `INFO`）。
<!-- region: ManualKotlinSortTest.kt#sort-spec-path -->
```kotlin
// for sorts chosen outside the code (the Spring module uses it); only real properties are accepted
val byName = SortSpec.ofPath(Order::class.java, "customer.name", Direction.DESC)
    .then(SortSpec.ofPath(Order::class.java, "id", Direction.ASC))
val q = qb.from(Order::class.java).orderBy(byName)
assertEquals("select e0 from Order e0 left join e0.customer j1 order by j1.name desc nulls last, e0.id asc", q.toHql().hql())
assertThrows(IllegalArgumentException::class.java) {
    qb.from(Order::class.java).orderBy(SortSpec.ofPath(Order::class.java, "price; drop table x", Direction.ASC))
}
```

<a id="sort-policy"></a>
### ソートポリシー: 許可する外部の名前

**実装済み**。

コードの外から来たソートの名前（リクエストパラメーター、Spring の `Sort` や `Pageable`）は、どの永続プロパティでも指せます。
どの名前を許可するかは **ソートポリシー** が決めます。

- `SortRules<T>` は、1 つのエンティティで許可する名前のリストです。各カラムをラムダ式で指定します。
- `SortPolicy` は、エンティティごとのルールとモード（`SortMode`）を持ちます。ポリシーは `EntityManagerFactory`、つまり永続化ユニットごとに 1 つ登録します。そのファクトリーを使うすべての `QueryBuilder` とリポジトリーがこのポリシーを使います。
- エンティティのフィールドの `@Sortable` で、コードなしでルールを指定できます（下の [`@Sortable` によるルール](#rules-from-sortable) を参照）。

エンティティのルールは、次の順で **1 か所** からだけ取ります: ポリシーの `SortRules`（コードか Spring の `SortPolicy` Bean）、Spring Boot のプロパティ `lambdaql.sort.*`（[12.5](12-spring-data-jpa.md#sort-policy-with-spring) を参照）、エンティティの `@Sortable` フィールド。
2 か所のルールは合わせません。

**チェックの対象。** 文字列から作ったソートだけをチェックします: `SortSpec.ofPath`、そして Spring の `Sort` や `Pageable`（Spring モジュールは `ofPath` を使います。[12.5](12-spring-data-jpa.md#sort-policy-with-spring) を参照）。
ソートのラムダ式（`orderBy { o -> ... }`、`SortSpec.of`）は開発者のコードなので、チェックしません。

| ルール | 許可する外部の名前 |
|---|---|
| `allow { it.price }` | `price`（カラムのパス） |
| `allow { it.customer?.name }` | `customer.name`。関連を通るパスは、このように明示したときだけ許可します |
| `alias("newest") { it.updateAt }` | `newest`。ソートの基準は `updateAt` です。このときパス `updateAt` そのものは外部の名前では **ない** ので、クライアントにプロパティ名が見えません |

ルールはエンティティのカラムでなければなりません。
それ以外のキー（例: `allow { it.price * 2 }`）は、ルールを最初に使うときに `LQL-4041` で拒否します。

| `SortMode` | ルールが許可しない名前の扱い |
|---|---|
| `OFF` | ソートを実行します。チェックしません |
| `INFO` | ソートを実行し、エンティティと名前ごとに 1 回、`LQL-5003` を `INFO` レベルでログに出します。**これがデフォルトです:** 登録したポリシーがなければ `INFO` を使い、ルールは `@Sortable` からだけ取ります |
| `WARN` | `INFO` と同じで、ログレベルだけが `WARN` です |
| `STRICT` | `SortNotAllowedException`（`LQL-4040`）で拒否します。ルールも `@Sortable` フィールドもないエンティティは、すべての名前を拒否します |

そのため、ポリシーを登録するまでは、`@Sortable` が許可しない外部のソートの名前ごとに、エンティティごとに 1 回 `LQL-5003` がログに出ます。
知らない名前を拒否するには `STRICT` で、チェックを止めるには `OFF` でポリシーを登録してください。

| メソッド | 働き |
|---|---|
| `SortPolicy.of(mode, rules...)` | モードとエンティティごとのルールからポリシーを作る |
| `SortPolicy.register(emf, policy)` | 永続化ユニットのポリシーを登録する。前のポリシーを置き換える |
| `SortPolicy.unregister(emf)` | ポリシーを取り除く。デフォルト（`INFO`、`@Sortable` のルールだけを使う）に戻る |
| `SortPolicy.find(emf)` | 登録したポリシーを返す（`Optional`） |
| `SortPolicy.effective(emf)` | 適用中のポリシーを返す。登録したポリシーがなければデフォルト |

Spring Boot では、`register` を呼ぶ代わりに `SortPolicy` の Bean を定義してください（[12.5](12-spring-data-jpa.md#sort-policy-with-spring) を参照）。

登録したポリシーは、その永続化ユニットのすべてのクエリに適用され続けます。ポリシーが不要になったら、`SortPolicy.unregister(emf)` で取り除いてください。

<!-- region: ManualKotlinSortTest.kt#policy -->
```kotlin
val orderRules = SortRules.of(Order::class.java)
    .allow { it.price }                      // the name "price"
    .allow { it.customer?.name }             // an association path: allowed only when listed
    .alias("newest") { it.updateAt }         // the outside name "newest" sorts by updateAt
SortPolicy.register(emf, SortPolicy.of(SortMode.STRICT, orderRules))

val newest = qb.from(Order::class.java).orderBy(SortSpec.ofPath(Order::class.java, "newest", Direction.DESC))
assertEquals("select e0 from Order e0 order by e0.updateAt desc nulls last", newest.toHql().hql())
// not in the rules, and the path behind an alias: SortNotAllowedException when orderBy is called
for (name in listOf("description", "updateAt")) {
    val e = assertThrows(SortNotAllowedException::class.java) {
        qb.from(Order::class.java).orderBy(SortSpec.ofPath(Order::class.java, name, Direction.ASC))
    }
    assertEquals("LQL-4040", e.code())
}
// a sort lambda is your code: never checked
assertEquals("select e0 from Order e0 order by e0.description asc nulls first",
    qb.from(Order::class.java).orderBy { o -> asc(o.description) }.toHql().hql())
```

<a id="rules-from-sortable"></a>
**`@Sortable` によるルール。** エンティティのフィールドや getter に `@org.lambdaql.Sortable` を付けても、ルールになります。

| アノテーション | 許可する外部の名前 |
|---|---|
| フィールド `name` の `@Sortable` | `name` |
| フィールド `grade` の `@Sortable("level")` | `level`。ソートの基準は `grade`（別名） |
| 関連のフィールド `customer` の `@Sortable(path = "name")` | `customer.name` |

- lambdaQL は、`from(...)` に指定したエンティティのアノテーションだけを読みます。別のエンティティのアノテーションでは関連のパスは許可されないので、関連のフィールドに `path` を指定してください。
- アノテーションは、ポリシーにそのエンティティのルール（コード、Spring の Bean、Spring Boot のプロパティ）がないときだけ使います。
- ポリシーを登録していないデフォルトの `INFO` モードでも動きます。

例のエンティティ `Customer`（Java クラス）の `name` には `@Sortable`、`grade` には `@Sortable("level")` が付いています。例は、`Order` のルールだけを持つ `STRICT` ポリシーが登録された状態で実行します。

<!-- region: ManualKotlinSortTest.kt#annotation -->
```kotlin
// Customer has no rules in the policy, so its @Sortable fields are the rules:
//   @Sortable private String name;   @Sortable("level") private Integer grade;
val byLevel = qb.from(Customer::class.java).orderBy(SortSpec.ofPath(Customer::class.java, "level", Direction.DESC))
assertEquals("select e0 from Customer e0 order by e0.grade desc nulls last", byLevel.toHql().hql())
assertThrows(SortNotAllowedException::class.java) {
    qb.from(Customer::class.java).orderBy(SortSpec.ofPath(Customer::class.java, "id", Direction.ASC))
}
```

<a id="rules-as-strings"></a>
**文字列のルール。** `allowPath(String...)` と `aliasPath(name, path)` は、プロパティのパスを文字列で受け取ります。設定ファイル向けで、[12.5](12-spring-data-jpa.md#sort-policy-with-spring) の Spring Boot のプロパティがこの形を使います。
パスは、ルールを最初に使うときにエンティティと照合します。コードでは、ラムダ式の形の `allow` と `alias` を使ってください。

<!-- region: ManualKotlinSortTest.kt#string-paths -->
```kotlin
// the string forms used by configuration files (lambdaql.sort.allow / lambdaql.sort.alias)
SortPolicy.register(emf, SortPolicy.of(SortMode.STRICT,
    SortRules.of(Order::class.java).allowPath("price", "customer.name").aliasPath("newest", "updateAt")))
assertEquals("select e0 from Order e0 order by e0.updateAt desc nulls last",
    qb.from(Order::class.java).orderBy(SortSpec.ofPath(Order::class.java, "newest", Direction.DESC)).toHql().hql())
// in STRICT mode a name that is not in the rules is rejected, even when it is not a property
assertThrows(SortNotAllowedException::class.java) {
    qb.from(Order::class.java).orderBy(SortSpec.ofPath(Order::class.java, "noSuchName", Direction.ASC))
}
```

- チェックするのは spec を `orderBy` に渡すときで、パスを読む前です。
  そのため `STRICT` モードでは、ルールにない名前がプロパティでなくても `SortNotAllowedException` が発生します。
- ルールは 1 つのエンティティクラスに属します。同じクラスの `SortRules` を 2 回渡すと、最後のものを使います。
- `SortNotAllowedException` は `LambdaQLException` です（[11. エラーと言語](../11-errors-and-languages.md#exceptions) を参照）。
  lambdaQL には Web の依存関係がありません。HTTP 400 の応答の例は [12.5](12-spring-data-jpa.md#sort-policy-with-spring) を参照してください。
- リクエストから名前と方向を受け取るなら、方向は `Direction.parse`（[4.3](#43-direction) を参照）で、名前は `SortSpec.ofPath` で読んでください。

コード例: [`ManualKotlinSortTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSortTest.kt)（`sort-spec`、`sort-spec-then`、`sort-spec-reversed`、`sort-spec-path`、`policy`、`annotation`、`string-paths`）。

<a id="411-sorting-inside-subqueries"></a>
## 4.11 サブクエリの中のソート

サブクエリの中のソートは、`limit` と一緒に使うとき（top-1 または top-N）だけ残ります。
`limit` がなければ結果を変えないので、lambdaQL はソートを取り除き、警告をログに出します。
[5. サブクエリ](05-subquery.md#59-sorting-inside-a-subquery) を参照してください。

<a id="412-rules-summary-s1-to-s14"></a>
## 4.12 ルールのまとめ（S1〜S14）

| # | ルール | 状態 |
|---|---|---|
| S1 | ソートは 1 つのラムダ式です: キー 1 つ（`{ it.price }`、任意で `Direction`）か、`By` の関数で作ったキー（`{ o -> desc(...) }`）。結合なら `{ o, c -> ... }` | 実装済み。結合の形 `{ o, c -> }` も実装済み |
| S2 | キーはエンティティの値を使わなければなりません（Kotlin の nullable なキーも可）。エンティティそのものはキーになれません | 実装済み |
| S3 | 方向は `asc` / `desc` か `sort(key, Direction)` で決めます。`asc(key)`/`desc(key)`（`ASC`/`DESC`）は NULL のポリシーを使い、`asc(key, nulls)`、`desc(key, nulls)`、`*_NULLS_*` は書いたとおりに使います | 実装済み |
| S4 | 本体はキー 1 つか、`By` の関数をまっすぐつないだチェーンだけです。ソート関数を選ぶ `if` と `when`、ループは使えません。キーの値の中の `if` は `case when` になります | 実装済み |
| S5 | `orderBy` はクエリごとに 1 回。`orderBy` の後に `where` は不可。`limit` の後に `orderBy` は不可 | 実装済み |
| S6 | 再利用は `SortSpec`（`of`、`ofPath`、`then`、`reversed`、`orderBy(spec)`）で。外部の文字列は永続化ユニットのソートポリシー（`SortRules`、`SortPolicy`、`SortMode`、[4.10](#sort-policy) を参照）がチェック | 実装済み |
| S7 | キー: カラム、to-one のパス、`+ - *`、拡大変換。キーの値の中の `if` は `case when` になります。Kotlin の `?.` はパスとして読みます | 実装済み |
| S8 | `orderBy` にだけ使った関連のパスは `left join` を使うので、関連のない行も残ります。必須の関連（`optional = false`）は行を失わないので inner join です（A4）。同じパスを `where` で使うと、ソートは `where` の結合を使います | 実装済み |
| S9 | `orderBy` とページングを一緒に使うと、キーに id がないときにルートの `@Id asc` を追加します。`orderBy` がなければ `order by` は付けません。明示的に結合したエンティティの id も追加します。キーに一意で NULL になれないカラム（`unique = true` かつ `nullable = false`、`@NaturalId`）があれば追加しません（A5） | 実装済み |
| S10 | 解析の結果はラムダ式ごとにキャッシュされます。キャプチャした `Direction` は呼び出しのたびに読みます | 実装済み |
| S11 | Spring の `Sort.Order` は `Direction` に 1 対 1 で対応します（Spring モジュール、[12.5](12-spring-data-jpa.md#125-spring-sort-and-pageable) を参照）。プロパティ名はソートポリシーのチェックを通り、別名はそのまま渡します | 実装済み（`ignoreCase` はサポートしない）。ソートポリシーと一緒に使っても動く |
| S12 | 結合クエリも同じ形を使います: `{ o, c -> }` からエンティティ 10 個まで | 実装済み（[6.8](06-select-group.md#68-explicit-joins) を参照） |
| S14 | サブクエリのソート: `limit` があれば残し、なければ警告とともに取り除く（5 章を参照） | 実装済み |

---

[Kotlin トラック](README.md) | 前: [3. 条件（`where`）](03-where.md) | 次: [5. サブクエリ](05-subquery.md) | [このページの Java 版](../java/04-order-paging.md)
