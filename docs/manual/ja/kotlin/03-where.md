<a id="3-conditions-where-kotlin"></a>
# 3. 条件（`where`）（Kotlin）

[Kotlin トラック](README.md) | [マニュアルのホーム](../README.md) | 前: [2. はじめよう](02-getting-started.md) | 次: [4. ソートとページング](04-order-paging.md) | [このページの Java 版](../java/03-where.md)

共通ページ: [1. 概要](../01-overview.md) · [11. エラーと言語](../11-errors-and-languages.md)

---

<a id="31-the-idea-in-one-sentence"></a>
## 3.1 概要

**lambdaQL のラムダ式はコードではなく、クエリの式です。** lambdaQL はラムダ式を呼び出さず、バイトコードの opcode を解析します。
そのため、エンティティを扱う部分には Kotlin の実行ルール（`===` のオブジェクトの同一性、コンパイラーが追加する null チェック）は適用されません。
キャプチャした値だけでできた式は、Kotlin で先に計算します（[3.11](#311-captured-values) を参照）。
lambdaQL は独自のルールを使います。この章ではそのルールを説明します。

lambdaQL はラムダ式をクエリとして解析できないと、コード付きの例外を投げます（[3.13](#313-what-is-rejected) を参照）。

<a id="32-example-entities"></a>
## 3.2 例で使うエンティティ

すべての例は、例のエンティティ（Java クラス）を使います。詳しくは [2. はじめよう](02-getting-started.md#example-entities) にあります。

| エンティティ | この章で使うプロパティ（Java の型） |
|---|---|
| `Order` | `Long id`、`double price`、`short status`、`boolean active`（Kotlin では `isActive`）、`String description`、`Integer cnt`、`String custName`、`LocalDateTime updateAt`、`Kind kind`、`Customer customer`、`Address address` |
| `Customer` | `int id`、`String name`、`Integer grade` |
| `Item` | `Long id`、`Order order`、`Double price`、`int qty` |
| `Coded` | `Long id`、`String code`（`@Convert` で逆順にして保存）、`Boolean vip`（NULL 可、`YesNoConverter`） |

`price`、`status`、`active`、`qty` はプリミティブ型なので NULL になれません。参照型のプロパティは NULL になることがあり、Kotlin ではプラットフォーム型です。
`status` は `Short` なので、例では `it.status.toInt() == 1` と書きます。lambdaQL はこの変換を無視します。

<a id="33-basic-form"></a>
## 3.3 基本の形

<!-- region: ManualKotlinStartTest.kt#first-query -->
```kotlin
val q = qb.from(Order::class.java).where { it.status.toInt() == 1 && it.price > 100.0 }
assertEquals("select e0 from Order e0 where e0.status = 1 and e0.price > 100.0", q.toHql().hql())
val orders: List<Order> = q.toList()
```

`qb` は `QueryBuilder` です（[2. はじめよう](02-getting-started.md#create-a-querybuilder) を参照）。

**データベースごとの SQL**

<!-- sql: to-hql -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>100.0
```

<!-- region: KotlinSelectTest.kt#captured -->
```kotlin
val min = 100.0
val q = qb.from(Order::class.java).where { it.price > min && it.status.toInt() == 1 }
assertEquals("select e0 from Order e0 where e0.price > :p1 and e0.status = 1", q.toHql().hql())
```

`min` はキャプチャしたローカルの `val` なので、バインドパラメーターになります。`price` はプリミティブ型の `double` なので、`!!` は要りません。
同じクエリを Java で書いた例は [このページの Java 版](../java/03-where.md) にあります。

`where()` は何回でも呼べます。条件は `AND` でつながります。

<!-- region: ManualKotlinWhereTest.kt#many-where -->
```kotlin
val q = qb.from(Order::class.java)
    .where { it.status.toInt() == 1 }
    .where { it.isActive }
assertEquals("select e0 from Order e0 where e0.status = 1 and e0.active = true", q.toHql().hql())
```

**データベースごとの SQL**

<!-- sql: many-where -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.active=true
```

**HQL の中の値。** ルールは、値がどこから来たかで決まります。

| 値 | HQL では | 例 |
|---|---|---|
| ラムダ式に直接書いた定数: 数値（小数を含む）、文字列、`true`/`false`、`null`、enum 定数、`const val` | **リテラル** | `it.price > 100.0` → `e0.price > 100.0` |
| キャプチャした値: ローカルの `val` や `var`、プロパティ、関数のパラメーター | **バインドパラメーター** | `it.price > minPrice` → `e0.price > :p1` |
| 計算した値: `BigDecimal("1.5")`、`form.text.trim()`、`LocalDateTime.of(...)` | **バインドパラメーター** | `:p1` |

- 定数は呼び出しのたびに同じです。そのため SQL 文は変わらず、データベースはクエリプランを立てるときに値を見られます。
- 文字列定数の中のシングルクォートはエスケープされます。`"O'Brien"` は `'O''Brien'` になります。この文字列はユーザーの入力ではなく、クラスにコンパイルされた定数なので、SQL インジェクションの危険はありません。
- キャプチャした値はユーザーから来ることがあるので、バインドパラメーターです。呼び出しごとに値だけが変わります。
- 例外が 1 つあります。整数のカラムと比較する小数のキャプチャした値は、小数リテラル（`2.5BD`）で書きます（[3.4](#34-comparisons) の「整数のカラムと小数の値」）。数値に変換した値だけを書くので、SQL インジェクションの危険はありません。
- enum 定数は完全なクラス名で書きます。例: `e0.kind = org.lambdaql.query.Order$Kind.RUSH`。

**`!!` はコンパイラーのためのものです。** Kotlin のエンティティのプロパティが `Double?` のように nullable だと、Kotlin は `it.price > 100.0` をコンパイルしないので、`it.price!! > 100.0` と書きます。lambdaQL は `!!` を無視します。値が NULL の行は返されません。

<!-- region: ManualKotlinWhereTest.kt#bang -->
```kotlin
// !! is only for the compiler: lambdaQL ignores it, and rows with a NULL cnt are not returned
val q = qb.from(Order::class.java).where { it.cnt!! > 1 }
assertEquals("select e0 from Order e0 where e0.cnt > 1", q.toHql().hql())
```

コード例: [`ManualKotlinStartTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinStartTest.kt)（`first-query`）、[`KotlinSelectTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinSelectTest.kt)（`captured`）、[`ManualKotlinWhereTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt)（`many-where`、`bang`）。

<a id="34-comparisons"></a>
## 3.4 比較

| Kotlin | HQL | 備考 |
|---|---|---|
| `it.status == 1` | `e0.status = 1` | |
| `it.status != 1` | `e0.status <> 1` | NULL の行は返されない（[3.6](#36-null-rules) を参照） |
| `it.price!! > p` | `e0.price > :p1` | `<`、`<=`、`>=` も同じ |
| `it.updateAt!! > since` | `e0.updateAt > :p1` | Kotlin では日付を `<`、`>` で比較 |
| `it.updateAt!!.isAfter(since)` | `e0.updateAt > :p1` | `isBefore` は `<` |
| `it.description!! < "m"` | `e0.description < 'm'` | `BigDecimal`、`LocalDate`、`BigInteger` も同じ |
| `it.status!!.compareTo(3) > 0` | `e0.status > 3` | `compareTo(..) op 0` は `op` になる |

nullable なカラムとの比較は **SQL の意味** に従います。
カラムが NULL の行は返されません。

**算術。** `+`、`-`、`*`、`/`、単項の `-` をサポートします。`%` は `mod` として書かれます。
`/` も使えますが、カラムを含む整数の `/` は避けてください。SQL で計算するので、データベースによって結果が違います（MariaDB は `5/2 = 2.5`、Kotlin は `2`）。カラム側の `/` の結果をそろえるルールは、まだ決まっていません。
キャプチャした値どうしの計算（下の `n / 2.0`）はクエリの前に行うので、この違いはありません。

下の例の `cnt` は `Order` の整数のカラム（`Integer cnt`）です。

**カラムに対する算術とインデックス**。`it.cnt * 2 > n` のようにカラムに算術をかけると、インデックスを使えません。lambdaQL は `LQL-5012` を `WARN` レベルでログに出し、`SargableMode.STRICT` では解析が失敗します（`LQL-3066`）。
値の側に移すときは、`it.cnt > n / 2.0` のように小数で割ってください。整数の割り算 `n / 2` では別の行が返ります。負の数を掛けたときは、比較の向きを逆にしてください。詳しくは [10.2](10-rules-and-limits.md#db-functions-on-a-column) を参照してください。

**整数のカラムと小数の値**。整数のカラムを小数の値と比較すると、lambdaQL は書いたとおりの値で比較します。丸めや値の除外はしません。

- ラムダ式に書いた定数はリテラルのまま残ります。`it.cnt >= 2.5` は `e0.cnt >= 2.5` になります。
- キャプチャした値も小数リテラルで書きます。`e0.cnt >= 2.5BD` になり、バインドパラメーターはありません。`BD` は HQL の `BigDecimal` リテラルの接尾辞です。
- そのような要素がある IN リストは、すべての要素を小数リテラルで書きます（`e0.cnt in (2.5BD, 3.0BD)`）。

これは、キャプチャした値をバインドパラメーターで送る原則の例外です。Hibernate 7 は、整数のカラムと比較するパラメーターに小数をバインドすると、`BigDecimal` 型を指定しても拒否します（`QueryArgumentException`、実測）。

以前は値をカラムの型に切り捨てて `2.5` が `2` になり、間違った行を返していました。Hibernate 6.6 は、整数のカラムの型でバインドした小数を拒否し（`CoercionException`）、IN リストの要素は黙って切り捨てます（3 つの DB で実測）。
整数の比較に変える方法（`>= 3`）は採用しませんでした。実行される SQL がラムダ式と違ってしまい、エンティティのマッピング（`Integer`）と DB のカラムの型（たとえば小数を持つ `DECIMAL`）が違うと、間違った行を返すからです。

結果は H2、MariaDB 10.11、Oracle 23.26 で正しいです（実測）。3 つの DB とも、SQL は `cnt>=2.5`、`cnt in (2.5,3.0)` のようにカラムをそのままにして小数と比較します。
MariaDB の `EXPLAIN` はインデックスの範囲スキャン（`type=range`）を示します。Oracle はカラムに変換が付かないので、インデックスを使える形です。インデックスヒントを与えると `INDEX RANGE SCAN` になり、3000 行のテーブルでヒントがないと、整数リテラル（`cnt >= 99`）と同じくフルスキャンを選びます（すべて実測）。

<!-- region: ManualKotlinWhereTest.kt#whole-number -->
```kotlin
// cnt is an integer column: a decimal value is compared as it is, not cut to 2
val literal = qb.from(Order::class.java).where { it.cnt >= 2.5 }
assertEquals("select e0 from Order e0 where e0.cnt >= 2.5", literal.toHql().hql())

val v = 2.5
val captured = qb.from(Order::class.java).where { it.cnt >= v }
assertEquals("select e0 from Order e0 where e0.cnt >= 2.5BD", captured.toHql().hql())
```

**浮動小数点:** `Float`/`Double` のカラムについて、lambdaQL は Kotlin とビット単位で同じ結果になることを約束しません。
キャプチャした `NaN` や `Infinity` の値は、クエリの実行時に拒否されます。

コード例: [`ManualKotlinWhereTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt)（`whole-number`）。

<a id="35-and-or-not"></a>
## 3.5 AND, OR, NOT

| Kotlin | HQL |
|---|---|
| `a && b` | `a and b` |
| `a \|\| b` | `a or b` |
| `!a` | `not (a)` |
| 括弧 | 必要な分だけ残す |

<!-- region: ManualKotlinWhereTest.kt#and-or-not -->
```kotlin
val both = qb.from(Order::class.java).where { (it.status.toInt() == 1 || it.isActive) && it.price > minPrice }
assertEquals("select e0 from Order e0 where (e0.status = 1 or e0.active = true) and e0.price > :p1", both.toHql().hql())

// the compiler moves the ! inside (De Morgan): the rows are the same
val not = qb.from(Order::class.java).where { !(it.status.toInt() == 1 && it.price > minPrice) }
assertEquals("select e0 from Order e0 where e0.status <> 1 or e0.price <= :p1", not.toHql().hql())
```

ルール:

- 結果は **SQL の 3 値論理**（TRUE / FALSE / UNKNOWN）に従います。lambdaQL は、この方法が H2、MariaDB、Oracle で同じ行を返すことを確認しました。
- コンパイラーはときどき `!` の位置を動かします（ド・モルガン、`!(x > y)` を `x <= y` に）。それでも結果は変わりません。
- lambdaQL は **条件を単純化しません**。`it.price!! > p || it.price!! <= p` と書けば、書いたとおりに送ります。`price` が NULL の行は返されません。これは正常な SQL の動作です。
- `&&` と `||` を使ってください。中置関数の `and`、`or`、`xor` は拒否されます（短絡評価をしません）。

**`true`/`false` を返す `if` と `when`。** 許されるのは次の短い形だけです。それぞれ `AND`、`OR` と同じです。

| Kotlin | 同じ意味 |
|---|---|
| `if (c) x else false` | `c && x` |
| `if (c) true else x` | `c \|\| x` |
| `when { a -> true; b -> true; else -> false }` | `a \|\| b` |

<!-- region: ManualKotlinWhereTest.kt#if-when -->
```kotlin
val ifAnd = qb.from(Order::class.java).where { if (it.isActive) it.price > 100.0 else false }
assertEquals("select e0 from Order e0 where e0.active = true and e0.price > 100.0", ifAnd.toHql().hql())

val ifOr = qb.from(Order::class.java).where { if (it.isActive) true else it.price > 100.0 }
assertEquals("select e0 from Order e0 where e0.active = true or e0.price > 100.0", ifOr.toHql().hql())

val whenOr = qb.from(Order::class.java).where { when { it.status.toInt() == 3 -> true; it.price > 200.0 -> true; else -> false } }
assertEquals("select e0 from Order e0 where e0.status = 3 or e0.price > 200.0", whenOr.toHql().hql())
```

キャプチャした値と定数だけで値を選ぶ `if`（`it.status.toInt() == (if (flag) 1 else 2)`）は、クエリの実行時に Kotlin で計算してバインドパラメーターとして渡します。
エンティティの値で値を選ぶ `if` と `when` は、SQL の `case when … end` になります（[3.5.1](#351-values-chosen-by-a-condition) を参照）。
それ以外の boolean の `if (c) x else y` は拒否されます。

<!-- region: ManualKotlinWhereTest.kt#value-before -->
```kotlin
// an if of captured values and constants: computed in Kotlin, bound as :p1
val q = qb.from(Order::class.java).where { it.status.toInt() == (if (flag) 1 else 2) }
assertEquals("select e0 from Order e0 where e0.status = :p1", q.toHql().hql())

// an if that reads the entity becomes case when
val c = qb.from(Order::class.java).where { it.status.toInt() == (if (it.isActive) 1 else 2) }
assertEquals("select e0 from Order e0 where e0.status = case when e0.active = true then 1 else 2 end", c.toHql().hql())
```

コード例: [`ManualKotlinWhereTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt)（`and-or-not`、`if-when`、`value-before`）。

<a id="351-values-chosen-by-a-condition"></a>
### 3.5.1 条件で選ぶ値（`case when`）

エンティティの値で値を選ぶ `if (c) x else y`、`if … else if … else`、対象のない `when { }` は、SQL の `case when … end` の式 1 つになります。
条件には `&&`、`||`、`!` を混ぜて使えます。

`case` は、`where` の比較の値、`select` の選択の値、`groupBy` のキー、ソートキー、`update` の代入の値に使えます。

<!-- region: KotlinCaseWhenTest.kt#case-where -->
```kotlin
val q = qb.from(Order::class.java).where { it.status.toInt() == if (it.isActive) 1 else 2 }
assertEquals("select e0 from Order e0 where e0.status = case when e0.active = true then 1 else 2 end", q.toHql().hql())
```

<!-- region: KotlinCaseWhenTest.kt#case-when-select -->
```kotlin
val q = qb.from(Order::class.java).select {
    when {
        it.price > 200.0 -> "big"
        it.price > 100.0 -> "medium"
        else -> "small"
    }
}
assertEquals("select case when e0.price > 200.0 then 'big' when e0.price > 100.0 then 'medium' else 'small' end from Order e0",
    q.toHql().hql())
```

<!-- region: KotlinCaseWhenTest.kt#case-group -->
```kotlin
// orders per price band: the when is the group key and the selected value
val q = qb.from(Order::class.java)
    .groupBy { if (it.price > 100.0) "big" else "small" }
    .select { if (it.price > 100.0) "big" else "small" }
assertEquals("select case when e0.price > 100.0 then 'big' else 'small' end from Order e0"
    + " group by case when e0.price > 100.0 then 'big' else 'small' end", q.toHql().hql())
```

<!-- region: KotlinCaseWhenTest.kt#case-sort -->
```kotlin
// active orders first, then by price
val q = qb.from(Order::class.java).orderBy { o -> asc(if (o.isActive) 0 else 1).asc(o.price) }.limit(10)
assertEquals("select e0 from Order e0 order by case when e0.active = true then 0 else 1 end asc, e0.price asc, e0.id asc",
    q.toHql().hql())
```

<!-- region: KotlinCaseWhenTest.kt#case-set -->
```kotlin
// 10 % off the orders above 100
val u = qb.update(Order::class.java).set { it.price = if (it.price > 100.0) it.price * 0.9 else it.price }.all()
assertEquals("update Order e0 set e0.price = case when e0.price > 100.0 then (e0.price * 0.9) else e0.price end", u.toHql().hql())
```

ルール:

- `case` は分岐を上から順に調べます。そのため、2 番目以降の分岐の条件は、ラムダ式に書いた条件そのままです。
- `where` で比較する `case` は行ごとに計算されるので、データベースはその中のカラムのインデックスを使えません。このとき lambdaQL は `WARN` レベルで `LQL-5013` をログに出します。
- ソート関数のチェーンそのものを条件で選ぶ形（`if (flag) desc(o.price) else asc(o.id)`）は拒否されます（`LQL-2023`）。条件はキーの値の中に書いてください。
- 対象のある `when (x)` は [3.15](#315-when-kotlin-only) のルールに従います。`takeIf` はサポートしていません。

コード例: [`KotlinCaseWhenTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinCaseWhenTest.kt)（`case-where`、`case-when-select`、`case-group`、`case-sort`、`case-set`）。

<a id="36-null-rules"></a>
## 3.6 NULL のルール

ここがいちばん重要な部分です。単純な原則が 3 つあります。

1. **null チェックを自分で書いた場所では、lambdaQL はそのチェックを使います。**（`== null`、`!= null`、`?.`、`?:`）
2. **書いていない場所では、lambdaQL は SQL の意味を使います。** カラムが NULL なら比較は UNKNOWN になり、その行は返されません。
   エンティティの値どうしの `==`、`!=` にも同じルールを適用します。
   結果が Kotlin と異なりうるとき、lambdaQL は `LQL-5009` を `DEBUG` レベルでログに出します。
3. **キャプチャした値は先に計算し、比較はやはり SQL の意味に従います。** キャプチャした `null` は NULL としてバインドされるので、`= :p1` と `<> :p1` はどの行にも一致しません。
   この場合、lambdaQL は実行時に `LQL-4045` を `WARN` レベルでログに出します。NULL を探すには `x == null` と書いてください（`is null` になります）。
   キャプチャした値のメソッドを呼ぶ式（`text.trim()`、別のプリミティブ型に変える `min.toInt()` など）は先に計算するので、`null` なら Kotlin と同じく例外（`LQL-4012`）を投げます。
   キャプチャした値の `!!` と、自身のプリミティブ型へのアンボクシング（`min!!`）は実行しません。`null` なら NULL をバインドし、`LQL-4045` を `DEBUG` レベルでログに出します。

Kotlin のコンパイラーは、バイトコードの opcode に **隠れた** null チェックを追加します（例: キャプチャした `id: Long` と比較する `it.id == id`）。意味を持つのは、ソースコードにある null チェックだけです。[3.6.2](#362--compares-values) を参照してください。

<a id="361-rule-table"></a>
### 3.6.1 ルール表

| # | Kotlin の書き方 | HQL | NULL の動作 |
|---|---|---|---|
| R12 | NULL になれないカラムと non-null の値: `it.id == id`（`id: Long`） | `e0.id = :p1` | コンパイラーが追加した null チェックを消す |
| R1 | 大小の比較: `it.price > p`、`it.cnt!! > 1` | `e0.price > :p1`、`e0.cnt > 1` | SQL の意味 |
| R11 | nullable なカラムと nullable なキャプチャした値: `it.cnt == n`（`n: Int?`）、`it.description == text`（`String?`）、enum の `==` | `e0.cnt = :p1` | 値の比較。カラムが NULL なら行は返されない。キャプチャした値が `null`: 一致する行なし（`LQL-4045`） |
| R11 | `it.cnt != c` | `e0.cnt <> :p1` | SQL の意味。カラムが NULL なら行は返され **ない**（Kotlin なら返される）。キャプチャした値が `null`: 一致する行なし（`LQL-4045`） |
| R11 | エンティティの値 2 つ: `it.description == it.custName` | `e0.description = e0.custName` | SQL の意味。どちらかが NULL なら行は返されない（NULL と NULL も同じ） |
| R3 | `x == null`、`x != null` | `x is null`、`x is not null` | |
| R17 | `===`、`!==` | `==`、`!=` と同じ | オブジェクトの同一性ではなく、値の比較 |
| R9 | キャプチャした値 | バインドパラメーター | [3.6.4](#364-captured-null-values) を参照 |

<a id="362--compares-values"></a>
### 3.6.2 `==` は値を比較する

Kotlin の `==` はもともと値を比較します。lambdaQL では、`===` も **値** を比較します。
クエリにはオブジェクトの同一性がありません。同じ値を持つ別々の 2 つの `Long` のボックスは等しくなります。

<!-- region: ManualKotlinWhereTest.kt#captured-eq -->
```kotlin
val n: Int? = 3
val q = qb.from(Order::class.java).where { it.cnt == n }
assertEquals("select e0 from Order e0 where e0.cnt = :p1", q.toHql().hql())

// a captured null matches no row: "= NULL" is never true (WARN LQL-4045)
val none: Int? = null
assertEquals(0, qb.from(Order::class.java).where { it.cnt == none }.toList().size)
```

`cnt` が NULL の行を探すには、null チェックを書いてください。`it.cnt == null` は `e0.cnt is null` になります。

カラムを関数で包まないので、データベースはインデックスを使えます（[10.2](10-rules-and-limits.md#102-sargable-rule-index-friendly-sql) を参照）。

<!-- region: ManualKotlinWhereTest.kt#string-eq -->
```kotlin
val eq = qb.from(Order::class.java).where { it.description == text }
assertEquals("select e0 from Order e0 where e0.description = :p1", eq.toHql().hql())

// a NULL description matches neither = nor <> (SQL meaning)
val ne = qb.from(Order::class.java).where { it.description != text }
assertEquals("select e0 from Order e0 where e0.description <> :p1", ne.toHql().hql())
```

**NULL になれないカラム: `it.id == id`。** 例のエンティティの `id` は `Long` で、Kotlin ではプラットフォーム型です。Kotlin は `it.id == id`（キャプチャした `id: Long`）を null チェックと比較にコンパイルします。

このバイトコードの opcode をそのまま変換すると `not (e0.id is null or e0.id <> :p1)` になります。結果は正しいのですが、データベースはインデックスをうまく使えません。
id は NULL になれないので、id の `is null` は常に偽です。lambdaQL はこのチェックを取り除き、`e0.id = :p1` と書きます。

プリミティブ型のプロパティと `nullable = false` のカラムにも同じルールを適用します。ただし、パスに **関連の段階がない** ときだけです。顧客のない注文があるので `it.customer?.id` は NULL になりえます。このパスの null チェックは残します。

<!-- region: ManualKotlinWhereTest.kt#never-null -->
```kotlin
// the id can never be NULL: the null check that Kotlin adds is dropped
val byId = qb.from(Order::class.java).where { it.id == id }
assertEquals("select e0 from Order e0 where e0.id = :p1", byId.toHql().hql())

val noId = qb.from(Order::class.java).where { it.id == null }
assertEquals("select e0 from Order e0 where 1=0", noId.toHql().hql())

// a path through an association can be NULL: the check stays
val noCustomerId = qb.from(Order::class.java).where { it.customer?.id == null }
assertEquals("select e0 from Order e0 where e0.customer.id is null", noCustomerId.toHql().hql())
```

> [!WARNING]
> **Kotlin の non-null 型は証拠になりません。** `var code: String` というプロパティにも、データベースからの NULL が入っていることがあります。
> Hibernate はチェックなしでカラムの値をフィールドに書き込むからです。
> lambdaQL が見るのは Kotlin の型ではなく、JPA のメタデータです。

<a id="363-write-your-null-checks-yourself"></a>
### 3.6.3 null チェックは自分で書く

Kotlin では `null != 3` は `true` です。lambdaQL では違います。下の `it.cnt != c` は、`cnt` が NULL の行を返し **ません**。null チェックを書いていないからです。

<!-- region: ManualKotlinWhereTest.kt#null-check -->
```kotlin
val c: Int? = 3
// rows with a NULL cnt are left out (SQL meaning); Kotlin would keep them
val ne = qb.from(Order::class.java).where { it.cnt != c }
assertEquals("select e0 from Order e0 where e0.cnt <> :p1", ne.toHql().hql())

// to keep the NULL rows, write the null check
val keep = qb.from(Order::class.java).where { it.cnt == null || it.cnt != c }
assertEquals("select e0 from Order e0 where e0.cnt is null or e0.cnt <> :p1", keep.toHql().hql())
```

> [!IMPORTANT]
> Kotlin のコードを実行するのとは違います。NULL の行を残すには、`it.cnt == null || it.cnt != c` のように null チェックを自分で書いてください。

このような違いが起こりうるとき、lambdaQL は `LQL-5009` を `DEBUG` レベルで、条件ごとに 1 回ログに出します。

`String?` に対する `it.description.equals(text)` は `==` と同じです。

**エンティティの値に対する SQL の意味**。エンティティの値 2 つの比較と、NULL になりうる `Boolean` の比較も SQL の意味に従います。

<!-- region: ManualKotlinWhereTest.kt#sql-meaning -->
```kotlin
// two entity values: = as in SQL; a row with a NULL on either side does not match
val same = qb.from(Order::class.java).where { it.description == it.custName }
assertEquals("select e0 from Order e0 where e0.description = e0.custName", same.toHql().hql())

// to keep the NULL rows, say so in the lambda
val keep = qb.from(Order::class.java).where { it.description == null || it.description != text }
assertEquals("select e0 from Order e0 where e0.description is null or e0.description <> :p1", keep.toHql().hql())

// a NULL Boolean is not false
val notVip = qb.from(Coded::class.java).where { it.vip == false }
assertEquals("select e0 from Coded e0 where e0.vip = false", notVip.toHql().hql())
```

`?.`、`?:`、`!!` について詳しくは: [9. Kotlin の注意点](09-kotlin-notes.md)。

<a id="364-captured-null-values"></a>
### 3.6.4 null のキャプチャした値

| 書き方 | キャプチャした値が `null` のとき |
|---|---|
| キャプチャした値との `==`、`!=`: `it.cnt == n`、`it.description != text` | 実行される: NULL をバインドした `= :p1` / `<> :p1` なので、一致する行は **ない**。`WARN` レベルの `LQL-4045` |
| 大小の比較: `it.price!! > min!!`、すべての `<`、`<=`、`>`、`>=` | 実行される: `e0.price > :p1` に NULL をバインドし、一致する行は **ない**。`DEBUG` レベルの `LQL-4045`。`min!!` と自身のプリミティブ型へのアンボクシングは実行しない |
| キャプチャした値のメソッド呼び出し: `text.trim()`、別のプリミティブ型に変える `min.toInt()` | クエリの実行時に **例外**（`LQL-4012`）。先に計算するため |
| `in` に使うコレクションの中の null の要素 | クエリの実行時に **例外** |

理由: ラムダ式の中の比較は SQL の意味に従います。SQL では、NULL との `=`、`<>` は真になりません。
キャプチャした値が null かどうかはクエリの実行時にしかわからないので、lambdaQL は実行時に警告をログに出します。

キャプチャした値のメソッドを呼ぶ式は、クエリの前に計算する値です。`text.trim()`、別のプリミティブ型に変える `min.toInt()` がこれにあたり、`null` なら Kotlin と同じく例外（`LQL-4012`）を投げます。
キャプチャした値の `!!` と、自身のプリミティブ型へのアンボクシングは実行せず、ボックスの値をバインドします。`null` なら一致する行はなく、`LQL-4045` を `DEBUG` レベルでログに出します。
NULL の行を探すには `x == null` と書いてください。

**null ガードによる絞り込みの省略。** 同じキャプチャした値を null チェックすると、値が null のとき、その条件は絞り込みなしで消えます。

<!-- region: ManualKotlinWhereTest.kt#captured-null-guard -->
```kotlin
fun byMinPrice(min: Double?) = qb.from(Order::class.java).where { min == null || it.price > min }

val all = byMinPrice(null)
assertEquals("select e0 from Order e0", all.toHql().hql())               // no filter
val some = byMinPrice(100.0)
assertEquals("select e0 from Order e0 where e0.price > :p1", some.toHql().hql())
```

**データベースごとの SQL**

<!-- sql: captured-null-guard -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
```

**データベースごとの SQL**

<!-- sql: captured-null-guard#2 -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.price>?
```

動作のしくみ:

- キャプチャした値と定数だけを使う部分は、クエリの実行時にアプリケーションで決めます。Kotlin と同じく左から右へ、短絡評価（short-circuit）で処理します。
- `min == null` ならガードが真なので、条件全体が真です。絞り込みはありません。`min` との比較は HQL に書きません（Kotlin もこの比較は実行しません）。
- `min = 100.0` ならガードが偽なので、`e0.price > :p1` だけが残ります。
- ラムダ式を解析するのは、やはり 1 回だけです。値によって変わるのは HQL のテキストだけです。
- この場合、ガードの位置は結果に影響しません。`min == null` のとき、`it.price > min!! || min == null` も絞り込みなしです。`min!!` とアンボクシングを実行しないからです（Kotlin なら `NullPointerException`）。
- キャプチャした値のメソッドを呼ぶ比較（`it.description == text.trim()`）は違います。ガードが後ろにあると `text.trim()` を先に計算するので、`text == null` のとき例外（`LQL-4012`）を投げます。

<a id="365-converted-attributes-convert"></a>
### 3.6.5 変換される属性（`@Convert`）

JPA の `AttributeConverter` が付いた属性は、Kotlin の値で比較します。Hibernate（7.4、以前の 6.6 も）は、このような属性と比較するリテラルを変換し、バインドパラメーターも変換します（H2、MariaDB、Oracle で確認済み）。

<!-- region: ManualKotlinWhereTest.kt#convert -->
```kotlin
// code is stored reversed ("abc" as "cba"): Hibernate converts the literal
val q = qb.from(Coded::class.java).where { it.code == "abc" }
assertEquals("select e0 from Coded e0 where e0.code = 'abc'", q.toHql().hql())
```

コード例: [`ManualKotlinWhereTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt)（`captured-eq`、`string-eq`、`never-null`、`null-check`、`sql-meaning`、`captured-null-guard`、`convert`）。

<a id="37-boolean-columns"></a>
## 3.7 boolean のカラム

`Order` の `active` はプリミティブ型の `boolean` です（NULL ではない、Kotlin のプロパティ名は `isActive`）。
`vip` が出てくる行は、`Coded` の NULL になりうる `Boolean vip` のカラムです。

| Kotlin | HQL | NULL の行 |
|---|---|---|
| `it.isActive` | `e0.active = true` | –（NULL ではない） |
| `it.vip == true` | `e0.vip = true` | 返されない |
| `it.vip != true` | `e0.vip <> true` | **返されない**（SQL の意味。Kotlin なら返される） |
| `it.vip == false` | `e0.vip = false` | 返されない（NULL は false ではない） |
| `it.vip ?: false` | `e0.vip is not null and e0.vip = true` | 返されない |
| `!(it.vip ?: false)` | `e0.vip is null or e0.vip = false` | **返される** |

備考:

- lambdaQL は `is not true` や `coalesce(flag, false)` を使いません。Oracle で失敗します（ORA-00932）。
- より大きな条件の否定は、常に `not (...)` で書きます。lambdaQL は条件を `(...) = false` や `(...) is null` で包みません。Oracle 23 が `WHERE` でこれを誤って評価するからです。
- `it.vip ?: false` は **条件の中では** 問題ありません。値やソートキーとして使うと拒否されます。

<!-- region: ManualKotlinWhereTest.kt#nullable-boolean -->
```kotlin
// vip is a Boolean column that can be NULL
val yes = qb.from(Coded::class.java).where { it.vip == true }
assertEquals("select e0 from Coded e0 where e0.vip = true", yes.toHql().hql())

val notYes = qb.from(Coded::class.java).where { it.vip != true }
assertEquals("select e0 from Coded e0 where e0.vip <> true", notYes.toHql().hql())

val elvis = qb.from(Coded::class.java).where { it.vip ?: false }
assertEquals("select e0 from Coded e0 where e0.vip is not null and e0.vip = true", elvis.toHql().hql())

val notElvis = qb.from(Coded::class.java).where { !(it.vip ?: false) }
assertEquals("select e0 from Coded e0 where e0.vip is null or e0.vip = false", notElvis.toHql().hql())
```

コード例: [`ManualKotlinWhereTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt)（`nullable-boolean`）。

<a id="38-strings"></a>
## 3.8 文字列

- 文字列に対する `==` と `equals` は **データベースの照合順序** を使います。
  - MariaDB の `_ci` 照合順序は、大文字と小文字の違いと末尾の空白を無視します。
  - Oracle は `''`（空文字列）を NULL として保存します。そのため Oracle では、`it.description == ""` は Kotlin と違う動きをします。
**`startsWith`、`endsWith`、`contains`** は `like` になります。検索語の中の `%` と `_` はワイルドカードではなく、文字そのものとして探します。

<!-- region: KotlinLikeTest.kt#like -->
```kotlin
// % and _ in the text are not wildcards: only "50%_off sale" matches, not "50x off"
val starts = qb.from(Order::class.java).where { it.description.startsWith(p) }
assertEquals("select e0 from Order e0 where e0.description like :p1 escape '!'", starts.toHql().hql())
// ignoreCase = true: both sides in lower case
val any = qb.from(Order::class.java).where { it.description.contains("all", ignoreCase = true) }
assertEquals("select e0 from Order e0 where lower(e0.description) like '%all%' escape '!'", any.toHql().hql())
```

- 定数は HQL にそのまま書き、キャプチャした値はバインドパラメーターになります。
- `ignoreCase = true` は両側を `lower(...)` に変えます。カラムの通常のインデックスを使えないので、`WARN` レベルの `LQL-5013` をログに出します。
- `endsWith` と `contains` はパターンの先頭が `%` なので、カラムの通常のインデックスを使えません。
- MariaDB と MySQL は、デフォルトの照合順序では `like` が大文字と小文字を区別しません。そのため、Kotlin の `startsWith` と結果が違うことがあります。
- 検索語に使ったキャプチャした値が `null` だとエラーです（`LQL-4047`）。
- `lowercase()`、`isBlank()`、`trim()` のような他の文字列関数はサポートしていません（`LQL-3008`）。
- ラムダ式の中の文字列テンプレートと `+` は拒否されます（`LQL-2007`）。キャプチャした値だけを使うテンプレート（`"$a-$b"`）も同じなので、ラムダ式の前に文字列を作ってキャプチャしてください。

<!-- region: ManualKotlinWhereTest.kt#string-rejected -->
```kotlin
// string functions on a column other than startsWith, endsWith and contains are not translated
val f = assertThrows<LambdaQLException> {
    qb.from(Order::class.java).where { it.description.lowercase() == "a" }.toHql()
}
assertTrue(f.message!!.startsWith("[LQL-3008]"))

// string building in the lambda is rejected: build the string before the lambda
val a = "x"
val b = "y"
val t = assertThrows<LambdaQLException> {
    qb.from(Order::class.java).where { "$a-$b" == it.description }.toHql()
}
assertTrue(t.message!!.startsWith("[LQL-2007]"))
```

コード例: [`ManualKotlinWhereTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt)（`string-rejected`）、[`KotlinLikeTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinLikeTest.kt)（`like`）。

<a id="39-in-with-a-captured-collection"></a>
## 3.9 キャプチャしたコレクションでの IN

コレクションはラムダ式の **外で** 作り、`in` を使ってください。

<!-- region: ManualKotlinWhereTest.kt#in-ids -->
```kotlin
val ids = seeded.toList()        // two order ids, built outside the lambda

val inList = qb.from(Order::class.java).where { it.id in ids }
assertEquals("select e0 from Order e0 where e0.id in (:p1)", inList.toHql().hql())

val notInList = qb.from(Order::class.java).where { it.id !in ids }
assertEquals("select e0 from Order e0 where e0.id not in (:p1)", notInList.toHql().hql())
```

`seeded` は、あらかじめ保存した注文の id 2 つです。

**データベースごとの SQL**

<!-- sql: not-in -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.id not in (?,?)
```

<!-- region: KotlinSelectTest.kt#in -->
```kotlin
val q = qb.from(Order::class.java).where { it.status in statuses }
assertEquals("select e0 from Order e0 where e0.status in (:p1)", q.toHql().hql())
```

`statuses` はキャプチャした `listOf<Short>(1, 3)` です。

`!in` は `not in` になります。NULL になりうるカラムでも単純な `not in` です（SQL の意味）。
値が NULL の行は `not in` に一致せず、lambdaQL は `LQL-5009` を `DEBUG` レベルでログに出します。Kotlin の `!in` は NULL の値に対して true なので、このような行を残すには `it.description == null || it.description !in descs` のように null チェックを書いてください。

| 場合 | 結果 |
|---|---|
| コレクションがキャプチャした `List`、`Set`、または他の `Collection` | サポート |
| コレクションに `null` の要素がある | クエリの実行時に例外 |
| 空のコレクション | `in` は FALSE、`!in` は TRUE（Hibernate は `1=0` / `1=1` を書く） |
| 要素 1,500 個 | H2、MariaDB、Oracle 23ai で 1 つの文として動く（以前の Oracle のバージョンには 1,000 個の制限がある。未検証） |
| コレクションがエンティティのコレクション: `item in it.items` | **拒否される**。`exists()` サブクエリを使う（[5. サブクエリ](05-subquery.md)） |
| ラムダ式の中で作ったコレクション: `it.status in listOf(1, 2)` | **拒否される**。外で作ってキャプチャする |

Kotlin には範囲（`it.status in 1..5`）と、定数を使った `when` もあります。[3.14](#314-ranges-kotlin-only) と [3.15](#315-when-kotlin-only) を参照してください。

コード例: [`ManualKotlinWhereTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt)（`in-ids`）、[`KotlinSelectTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinSelectTest.kt)（`in`）。

<a id="310-association-paths"></a>
## 3.10 関連のパス

lambdaQL は `it.customer?.name` を実行しません。このパスは「結合した顧客の `name` カラム」を指すだけです。
lambdaQL は JPA Metamodel で各段階を確認します。

| パスの段階 | HQL | 例 |
|---|---|---|
| to-one の関連（`@ManyToOne`、`@OneToOne`） | 別名の付いた `join` または `left join`（下を参照） | `it.customer?.name` -> `join e0.customer j1` ... `j1.name` |
| 埋め込み（`@Embedded`） | 同じテーブル、結合なし。値オブジェクト全体の等値比較はカラムごとに展開（下を参照） | `Order` に埋め込みの `address` がある場合: `it.address.city` -> `e0.address.city` |
| 対象の `@Id` で終わるパス | 外部キーのカラム、結合なし | `it.customer?.id` -> `e0.customer.id` |
| 関連そのものを `null` と比較 | 外部キーのカラム、結合なし | `it.customer == null` -> `e0.customer is null` |
| コレクション（`@OneToMany`、`@ManyToMany`） | **拒否される** | `it.items...` -> `exists()` サブクエリを使う |

**値オブジェクト全体の比較。** 埋め込みの値オブジェクト（`@Embedded`）全体を等値で比較すると、lambdaQL はカラムごとの比較 1 つずつに展開します。
`it.address == addr` は `e0.address.city = :p1 and e0.address.zip = :p2` になります。

- 否定（`it.address != addr`）は `e0.address.city <> :p1 or e0.address.zip <> :p2` です。
- `it.address == null` は `e0.address.city is null and e0.address.zip is null` で、`!= null` は `e0.address.city is not null or e0.address.zip is not null` です。
- 各カラムは SQL の意味に従います。NULL のカラムは `=` にも `<>` にも一致しません。たとえば (Seoul, NULL) の行は、等値比較にも否定にも一致しません（3 つの DB で実測）。
- キャプチャした値オブジェクトの各プロパティは、クエリの実行時に読んでバインドします。キャプチャしたオブジェクトが `null` なら、すべてのカラムに NULL をバインドするので一致する行はなく、カラムごとに `LQL-4045` を `WARN` レベルでログに出します。
- 入れ子の値オブジェクトも展開します。
- 大小の比較（`<` など）と別の型との比較は、これまでどおり `LQL-3012` のエラーです。フィールドを比較してください。

<!-- region: KotlinQueryTest.kt#embedded-compare -->
```kotlin
// address is @Embedded (city, zip): == with a value object compares each column
val addr = Address("Seoul", "04524")
val same = qb.from(Order::class.java).where { it.address == addr }
assertEquals("select e0 from Order e0 where e0.address.city = :p1 and e0.address.zip = :p2", same.toHql().hql())
val none = qb.from(Order::class.java).where { it.address == null }
assertEquals("select e0 from Order e0 where e0.address.city is null and e0.address.zip is null", none.toHql().hql())
```

`it.customer?.name`、`it.customer!!.name`、そして（Java のエンティティなら）`it.customer.name` は、どれも **同じカラム** `j1.name` を意味します。
`where` では、結合の種類は条件全体で決めます。顧客のない注文が条件に一致しうるなら `left join`、一致しえないなら内部結合の `join` です。
結果は、同じラムダ式をリストに対して実行した結果と同じです。`&&` と `||` の短絡評価も反映します。

顧客のない注文では、各比較を、関連の段階をどうたどったかによって次のように評価します。

| `where` で関連の段階をたどった方法 | 顧客のない注文での比較 |
|---|---|
| `!!`、または印なし（non-null 型の `customer: Customer`、Java のエンティティのプラットフォーム型） | 真ではない。顧客のある注文でだけ真 |
| `?.` | 顧客のカラムを NULL とみなして SQL の意味で比較。`j1.name = 'Kim'` は真ではなく、`j1.name is null` は真 |

値の比較はどちらの場合も SQL の意味に従います。たとえば `it.customer?.name != "Kim"` は、顧客のない注文に一致しません。
`?.` と `!!`（または印なし）は、1 つの条件の中で混ぜて使ってもかまいません。

**inner join になる場合。** 顧客のない注文が条件に一致しえないなら、`?.` で書いても inner join です。結果は同じです。

<!-- region: ManualKotlinStartTest.kt#join-path -->
```kotlin
val name = "Kim"
val q = qb.from(Order::class.java).where { it.customer?.name == name }
assertEquals("select e0 from Order e0 join e0.customer j1 where j1.name = :p1", q.toHql().hql())
```

**データベースごとの SQL**

<!-- sql: inner-join -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name=?
```

- **顧客のない注文** は結果にありません。結合する顧客の行がないからです。
- lambdaQL が代わりに null チェックを追加することはありません。パスの前に `it.customer != null &&` を書く必要はありません。

<!-- region: KotlinQueryTest.kt#safe-call-and -->
```kotlin
// no order without a customer can match: an inner join, also with ?.
val q = qb.from(Order::class.java).where { it.customer?.name == "Kim" && it.price > 100.0 }
assertEquals("select e0 from Order e0 join e0.customer j1 where j1.name = 'Kim' and e0.price > 100.0", q.toHql().hql())
```

**`?.` と `||`。** status が 1 の注文は顧客がなくても条件に一致するので、`left join` です。
status が 1 で顧客のない注文も結果に残ります。Kotlin で同じ条件を実行した結果と同じです。

<!-- region: KotlinQueryTest.kt#safe-call-or -->
```kotlin
// ?. says the customer may be missing: an order without a customer stays when the rest of the OR is true
val q = qb.from(Order::class.java).where { it.status.toInt() == 1 || it.customer?.name == "Kim" }
assertEquals("select e0 from Order e0 left join e0.customer j1 where e0.status = 1 or j1.name = 'Kim'", q.toHql().hql())
```

**データベースごとの SQL**

<!-- sql: safe-call-or -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where o1_0.status=1 or c1_0.name='Kim'
```

**`?.` と `== null`。** `it.customer?.name == null` は、Kotlin と同じく、顧客のない注文と名前のない顧客の注文の両方を探します。
`!!` で書いた `it.customer!!.name == null` は顧客のある注文でだけ真になるので inner join で、名前のない顧客の注文だけを探します。

<!-- region: KotlinQueryTest.kt#safe-call-null -->
```kotlin
// "no customer, or a customer without a name": a left join, as in Kotlin
val q = qb.from(Order::class.java).where { it.customer?.name == null }
assertEquals("select e0 from Order e0 left join e0.customer j1 where j1.name is null", q.toHql().hql())
```

**データベースごとの SQL**

<!-- sql: safe-call-null -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name is null
```

**null ガード。** `it.customer == null || ...` は顧客のない注文が条件に一致するので `left join` で、顧客のない注文が残ります。`?.` で書いても `!!` で書いても同じです。
`it.customer != null && ...` は顧客のない注文が条件に一致しえないので、inner join です。

<!-- region: ManualKotlinWhereTest.kt#null-guard -->
```kotlin
// the null guard keeps orders without a customer: a left join (?. or !! give the same)
val safe = qb.from(Order::class.java).where { it.customer == null || it.customer?.name == name }
assertEquals("select e0 from Order e0 left join e0.customer j1 where e0.customer is null or j1.name = :p1",
    safe.toHql().hql())
val sure = qb.from(Order::class.java).where { it.customer == null || it.customer!!.name == name }
assertEquals("select e0 from Order e0 left join e0.customer j1 where e0.customer is null or j1.name = :p1",
    sure.toHql().hql())

// no order without a customer can match: an inner join
val and = qb.from(Order::class.java).where { it.customer != null && it.customer?.name == name }
assertEquals("select e0 from Order e0 join e0.customer j1 where e0.customer is not null and j1.name = :p1",
    and.toHql().hql())
```

下の 1 つ目の SQL は `!!` で書いた 2 番目の形が、2 つ目の SQL は `&&` で書いた 3 番目の形が作ります。

**データベースごとの SQL**

<!-- sql: null-guard -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.id is null or c1_0.name=?
```

**データベースごとの SQL**

<!-- sql: null-guard#2 -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.id is not null and c1_0.name=?
```

Hibernate は `e0.customer is null` を `c1_0.id is null` と書きます。inner join の後では、この条件は真になりません。

**`!!` と `||`。** `!!` を通った比較は、顧客のある注文でだけ真です。
Kotlin では、`||` が `!!` に届く前に終わる行（status 1、顧客なし）が残ります。この行が条件に一致するので、lambdaQL も `left join` を使ってその行を残します。

<!-- region: KotlinQueryTest.kt#not-null-assert -->
```kotlin
// !! counts only for orders with a customer; || stops before it, so the status 1 order without a customer
// stays, as in Kotlin: a left join
val q = qb.from(Order::class.java).where { it.status.toInt() == 1 || it.customer!!.name == "Kim" }
assertEquals("select e0 from Order e0 left join e0.customer j1 where e0.status = 1 or j1.name = 'Kim'", q.toHql().hql())
```

**`?.` と `!!` を混ぜた場合。** 同じ関連の段階を、ある箇所では `?.` で、別の箇所では `!!` か印なしでたどってもかまいません。比較ごとに上の表の評価を適用します。
1 つ目の例は、どちらも顧客のない注文で真になりえないので inner join です。2 つ目の例は、`?.` の側が顧客のない注文で真になるので `left join` です。
`left join` で、`j1.grade is null` のように NULL のカラムで真になりうる比較（`is null`、`coalesce`）を `!!` か印なしで書くと、lambdaQL はその比較にだけ `e0.customer is not null` を加えます。

<!-- region: KotlinQueryTest.kt#safe-and-assert -->
```kotlin
// the same association with ?. in one place and !! in another: neither side can match an order without
// a customer, so an inner join (no warning)
val q = qb.from(Order::class.java).where { it.customer?.name == "Kim" || it.customer!!.grade!! > 3 }
assertEquals("select e0 from Order e0 join e0.customer j1 where j1.name = 'Kim' or j1.grade > 3", q.toHql().hql())
// ?. side true without a customer: a left join, and the !! side counts only with a customer
val keep = qb.from(Order::class.java).where { it.customer?.name == null || it.customer!!.grade == null }
assertEquals("select e0 from Order e0 left join e0.customer j1"
    + " where j1.name is null or (e0.customer is not null and j1.grade is null)", keep.toHql().hql())
```

- パスが複数の段階からなる場合（`Item` の `it.order?.customer?.name`）は、段階ごとに同じルールで結合の種類を決めます。

顧客の **ない** 注文を探すには、関連そのものを比較してください。結合なしで外部キーのカラムを調べます。

<!-- region: ManualKotlinWhereTest.kt#no-customer -->
```kotlin
val q = qb.from(Order::class.java).where { it.customer == null }
assertEquals("select e0 from Order e0 where e0.customer is null", q.toHql().hql())
```

**データベースごとの SQL**

<!-- sql: no-association -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id is null
```

補足:

- 同じパスは 1 つの別名を受け取ります。この別名は `where`、`orderBy`、`select` が共有します。
- `it.customer?.id == 5L` は外部キーのカラム `e0.customer.id` を使います。結合は要りません。
- `orderBy` や `select` に **だけ** 使ったパスは `left join` なので、そこでは行を失いません。`!!` があっても同じです。同じパスを `where` でも使うと、`orderBy` と `select` は `where` の結合を使います。
- この結合のルールは、選択、`count`、サブクエリ、結合クエリの `where` に適用します。明示的な結合の `on` の中のパスは、これまでどおり `left join` です。必須の関連（`optional = false`）は常に inner join です。
- 関連をエンティティのオブジェクトと比較せず（`it.customer == c`）、id を比較してください: `it.customer?.id == c.id`（ここで `c` はキャプチャしたオブジェクト）。

コード例: [`KotlinQueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)（`embedded-compare`、`safe-call-and`、`safe-call-or`、`safe-call-null`、`not-null-assert`、`safe-and-assert`）、[`ManualKotlinStartTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinStartTest.kt)（`join-path`）、[`ManualKotlinWhereTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt)（`null-guard`、`no-customer`）。

<a id="311-captured-values"></a>
## 3.11 キャプチャした値

ラムダ式は外の値を使えます。ローカルの値、プロパティ、キャプチャしたオブジェクトのプロパティです。

<!-- region: ManualKotlinWhereTest.kt#captured-kinds -->
```kotlin
val since = LocalDateTime.of(2026, 1, 1, 0, 0)
val local = qb.from(Order::class.java).where { it.updateAt > since }                     // a local val
assertEquals("select e0 from Order e0 where e0.updateAt > :p1", local.toHql().hql())

val property = qb.from(Order::class.java).where { it.status == defaultStatus }           // a property of this class
assertEquals("select e0 from Order e0 where e0.status = :p1", property.toHql().hql())

val objectProperty = qb.from(Order::class.java).where { it.price > filter.minPrice!! }   // a property of a captured object
assertEquals("select e0 from Order e0 where e0.price > :p1", objectProperty.toHql().hql())

val call = qb.from(Order::class.java).where { it.updateAt > LocalDateTime.of(2026, 1, 1, 0, 0) }   // a call without the entity
assertEquals("select e0 from Order e0 where e0.updateAt > :p1", call.toHql().hql())
```

`defaultStatus` はクエリを作るクラスのプロパティ（`Short`）で、`filter` はキャプチャした `PriceFilter` オブジェクトです。

| キャプチャしたもの | lambdaQL の使い方 |
|---|---|
| `val`（`Long?`、`String`、... も同じ） | バインドパラメーター |
| `var` | バインドパラメーター。値は **クエリを実行するたびに** 読む |
| 外側のクラスのプロパティ（`private` も同じ） | クエリを実行するたびに読む |
| `companion object` の `val`、トップレベルの `val`、`object` の `val` | バインドパラメーター |
| `const val` | HQL にリテラルとして書く |
| ラムダ式の中でのキャプチャした変数への書き込み（`n++`） | **拒否される** |

ルール:

- エンティティを使わない部分は、**クエリを実行するたびに** アプリケーションで計算します。その結果がバインドパラメーターになります。
- ラムダ式は 1 回解析され、結果はキャッシュされます。新しいキャプチャした値は呼び出しのたびに読みます。
- SQL になるのはエンティティの部分だけです。

コード例: [`ManualKotlinWhereTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt)（`captured-kinds`）。

<a id="312-errors-that-depend-on-order-of-evaluation"></a>
## 3.12 評価の順番に依存するエラー

SQL は `AND`/`OR` を左から右へ評価することを約束しません。そのため、次のような「ガード」は SQL では安全ではありません。
lambdaQL はこのガードを拒否せず、書いたとおりに HQL に書きます。データベースが `e0.qty <> 0` より先に割り算を計算すると、ゼロ除算のエラーになることがあります。

<!-- region: ManualKotlinWhereTest.kt#division-guard -->
```kotlin
// lambdaQL sends the guard as written; SQL does not promise to check qty <> 0 first
val q = qb.from(Item::class.java).where { it.qty != 0 && it.price!! / it.qty > 2 }
assertEquals("select e0 from Item e0 where e0.qty <> 0 and (e0.price / e0.qty) > 2.0", q.toHql().hql())

// the same rows when qty is positive, without a division
val safe = qb.from(Item::class.java).where { it.qty > 0 && it.price!! > 2.0 * it.qty }
assertEquals("select e0 from Item e0 where e0.qty > 0 and e0.price > (2.0 * e0.qty)", safe.toHql().hql())
```

割り算なしで条件を書いてください。2 つ目のクエリは、`qty` が正のとき 1 つ目のクエリと同じ行を返します。

コード例: [`ManualKotlinWhereTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt)（`division-guard`）。

<a id="313-what-is-rejected"></a>
## 3.13 拒否されるもの

lambdaQL は解析に失敗すると、コード `[LQL-xxxx]` とソースの行番号を含む例外を投げます。
ほとんどの形は、ラムダ式を解析するとき（最初に使うとき）に見つかります。誤ったキャプチャした値（メソッドを呼んだ `null`、NaN、`null` の要素があるコレクション）は、クエリの実行時に見つかります。
[11. エラーと言語](../11-errors-and-languages.md) を参照してください。

| 書き方 | 例 | 解決方法 |
|---|---|---|
| ローカルの `val`/`var`、ループ、複数の文があるブロック | `{ val x = 1; it.status == x }` | 1 つの式で書く |
| 一般の `if (c) x else y` | | [3.5](#35-and-or-not) の許された形を使う |
| 中置の `and`、`or`、`xor` | `it.isActive and (it.price > 100.0)` | `&&`、`\|\|` を使う |
| ラムダ式の中の文字列テンプレートや `+`（`LQL-2007`） | `it.description == "A" + it.status` | ラムダ式の前に文字列を作ってキャプチャする |
| ラムダ式の中で作ったコレクション | `it.status in listOf(1, 2)` | 外で作ってキャプチャする |
| ラムダ式の中のラムダ式 | `ids.any { id -> id == it.id }` | `in`（[3.9](#39-in-with-a-captured-collection)）かサブクエリを使う |
| Kotlin の文字列関数と他の stdlib の関数（`LQL-3008`）。`startsWith`、`endsWith`、`contains` はサポート（[3.8](#38-strings)） | `it.description?.trim()`、`isBlank()`、`lowercase()` | 直接比較する |
| `switch` にコンパイルされる `when`（`LQL-2010`） | `when (it.status.toInt()) { 1, 2 -> true; else -> false }` | `\|\|` でつないだ比較（[3.15](#315-when-kotlin-only)） |
| 範囲オブジェクトが必要な範囲（`LQL-3008`） | `it.customer?.grade in 1..5`、`LocalDateTime` の範囲 | 2 つの比較で書く（[3.14](#314-ranges-kotlin-only)） |
| 自分で作った inline でない拡張関数 | `it.isOpen()` | `inline fun` にするか、条件を直接書く |
| エンティティの他のメソッド | `it.calcSomething()` | プロパティだけを使う |
| エンティティのコレクションのパス | `it.items.size > 0` | `exists()` サブクエリ |
| エンティティとエンティティの比較 | `it.customer == c` | id を比較する |
| 値オブジェクトの大小の比較、別の型との比較（`LQL-3012`） | `it.address > a`（`Address` が `Comparable` のとき） | フィールドを比較する。`==`、`!=` はサポート |
| `NaN` / `Infinity` のキャプチャした値 | `it.price!! > Double.NaN` | NaN を送らない |
| `isNaN()` | `it.price!!.isNaN()` | – |
| キャプチャした変数への書き込み | `{ n++; it.isActive }` | ラムダ式の外で計算する |
| 匿名オブジェクト、関数型の値 | `val f: (Order) -> Boolean` で `where(f)` | ラムダ式のリテラルを渡す |

例外の型（すべて `LambdaQLException` を継承）:

| 例外 | 発生するとき |
|---|---|
| `LambdaAnalysisException` | ラムダ式を読めないとき（シリアライズ可能なラムダ式ではない、クラスにコンパイルされたなど） |
| `UnsupportedLambdaShapeException` | サポートしていない形のとき（ループ、対象のある `when` の `switch`、...） |
| `TranslationException` | 意味を変換できないとき（不明な関数、コレクションのパス、...） |

Kotlin のすべての制限: [9. Kotlin の注意点](09-kotlin-notes.md#914-kotlin-limits-kc1-to-kc21)。すべてのルール: [10. ルールと制限](10-rules-and-limits.md)。

<a id="314-ranges-kotlin-only"></a>
## 3.14 範囲（Kotlin だけ）

Kotlin のコンパイラーは、NULL になれない数値と文字列を対象とする範囲を、2 つの比較にコンパイルします。lambdaQL はその比較をそのまま書きます（`between` ではありません）。
上限が定数の `a..b` は、コンパイラーが `< b+1` に変えます。たとえば `in 1..2` は `e0.status < 3` になります。

<!-- region: ManualKotlinWhereTest.kt#ranges -->
```kotlin
val closed = qb.from(Order::class.java).where { it.status.toInt() in 1..2 }
assertEquals("select e0 from Order e0 where 1 <= e0.status and e0.status < 3", closed.toHql().hql())

val outside = qb.from(Order::class.java).where { it.status.toInt() !in 1..2 }
assertEquals("select e0 from Order e0 where 1 > e0.status or e0.status >= 3", outside.toHql().hql())

val until = qb.from(Order::class.java).where { it.status.toInt() in 1 until 3 }
assertEquals("select e0 from Order e0 where 1 <= e0.status and e0.status < 3", until.toHql().hql())

val open = qb.from(Order::class.java).where { it.status.toInt() in 1..<3 }
assertEquals("select e0 from Order e0 where 1 <= e0.status and e0.status < 3", open.toHql().hql())

val captured = qb.from(Order::class.java).where { it.cnt in lo..hi }                  // lo, hi: captured values
assertEquals("select e0 from Order e0 where :p1 <= e0.cnt and e0.cnt <= :p2", captured.toHql().hql())

val prices = qb.from(Order::class.java).where { it.price in 100.0..200.0 }
assertEquals("select e0 from Order e0 where 100.0 <= e0.price and e0.price <= 200.0", prices.toHql().hql())

val texts = qb.from(Order::class.java).where { it.description in "a".."m" }
assertEquals("select e0 from Order e0 where e0.description >= 'a' and e0.description <= 'm'", texts.toHql().hql())
```

| 範囲の書き方 | サポート |
|---|---|
| `a..b`、`a until b`、`a..<b`（`Int`、`Double`、`String` が対象） | はい。2 つの比較 |
| `!in` | はい。比較を反転して `or` でつなぐ |
| キャプチャした値の境界（`lo..hi`） | はい。バインドパラメーター |
| nullable な対象（`Int?`）、`LocalDate`・`LocalDateTime` の範囲 | いいえ（`LQL-3008`） |
| `downTo`、`step` | はい。値を展開して `in (:p1)` でバインド |

**NULL になりうる対象。** Java のエンティティの `Integer` プロパティ（`cnt`）はプラットフォーム型なので、コンパイラーは 2 つの比較にコンパイルします。lambdaQL は null ガードを追加し **ない** ので、`cnt` が NULL の行は返されません（SQL の意味）。
`?.` で得た `Int?` の対象と日付の範囲は、範囲オブジェクトにコンパイルされるので拒否されます。2 つの比較で書いてください。

<!-- region: ManualKotlinWhereTest.kt#range-rejected -->
```kotlin
// a range object is not translated: a nullable subject, or a range of dates
val nullable = assertThrows<LambdaQLException> {
    qb.from(Order::class.java).where { it.customer?.grade in 1..5 }.toHql()
}
assertTrue(nullable.message!!.startsWith("[LQL-3008]"))

val dates = assertThrows<LambdaQLException> {
    qb.from(Order::class.java).where { it.updateAt in from..to }.toHql()
}
assertTrue(dates.message!!.startsWith("[LQL-3008]"))

// write the two comparisons instead
val q = qb.from(Order::class.java).where { it.updateAt >= from && it.updateAt <= to }
assertEquals("select e0 from Order e0 where e0.updateAt >= :p1 and e0.updateAt <= :p2", q.toHql().hql())
```

コード例: [`ManualKotlinWhereTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt)（`ranges`、`range-rejected`）。

<a id="315-when-kotlin-only"></a>
## 3.15 `when`（Kotlin だけ）

`when` は **条件として** サポートします。すべての分岐は `true` か `false`（または条件）で終わらなければなりません。
lambdaQL はこれを比較、`OR`、`AND` に変えます。

<!-- region: ManualKotlinWhereTest.kt#when -->
```kotlin
val twoTexts = qb.from(Order::class.java).where { when (it.description) { "a", "b" -> true; else -> false } }
assertEquals("select e0 from Order e0 where e0.description = 'a' or e0.description = 'b'", twoTexts.toHql().hql())

val oneValue = qb.from(Order::class.java).where { when (it.status.toInt()) { 1 -> true; else -> false } }
assertEquals("select e0 from Order e0 where e0.status = 1", oneValue.toHql().hql())

val range = qb.from(Order::class.java).where { when (it.status.toInt()) { in 1..2 -> true; else -> false } }
assertEquals("select e0 from Order e0 where 1 <= e0.status and e0.status < 3", range.toHql().hql())

val condition = qb.from(Order::class.java).where { when (it.status.toInt()) { 1 -> it.isActive; else -> false } }
assertEquals("select e0 from Order e0 where e0.status = 1 and e0.active = true", condition.toHql().hql())
```

対象が NULL の行は返されません。Kotlin のコードを実行するなら `else` に行くところですが、lambdaQL は SQL の意味を使います。

コンパイラーが `when` を `switch` にコンパイルする場合は、まだサポートしていません（`LQL-2010`）。定数が 2 つ以上の `Int` の対象、定数が 3 つ以上の `String` の対象がこれにあたります。enum の対象もサポートしていません。
このような条件は、`||` でつないだ比較か、キャプチャしたコレクションの `in`（[3.9](#39-in-with-a-captured-collection)）で書いてください。
エンティティの値で値を選ぶ対象のない `when { }` は `case when` になります（[3.5.1](#351-values-chosen-by-a-condition) を参照）。

<!-- region: ManualKotlinWhereTest.kt#when-rejected -->
```kotlin
// when compiled to a switch is not supported yet (LQL-2010): two or more Int values, three or more strings
val ints = assertThrows<LambdaQLException> {
    qb.from(Order::class.java).where { when (it.status.toInt()) { 1, 2 -> true; else -> false } }.toHql()
}
assertTrue(ints.message!!.startsWith("[LQL-2010]"))

val texts = assertThrows<LambdaQLException> {
    qb.from(Order::class.java).where { when (it.description) { "a", "b", "c" -> true; else -> false } }.toHql()
}
assertTrue(texts.message!!.startsWith("[LQL-2010]"))

// an enum subject is not supported either
assertThrows<LambdaQLException> {
    qb.from(Order::class.java).where { when (it.kind) { Order.Kind.RUSH -> true; else -> false } }.toHql()
}
```

代わりに `&&` と `||` で条件を書いてください。

コード例: [`ManualKotlinWhereTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinWhereTest.kt)（`when`、`when-rejected`）。

---

[Kotlin トラック](README.md) | 前: [2. はじめよう](02-getting-started.md) | 次: [4. ソートとページング](04-order-paging.md) | [このページの Java 版](../java/03-where.md)
