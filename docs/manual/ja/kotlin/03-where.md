<a id="3-conditions-where-kotlin"></a>
# 3. 条件（`where`）（Kotlin）

> [!NOTE]
> **状況**
> - **実装済み、H2・MariaDB・Oracle でテスト済み:** `where` の条件と `toList()`（カラムへのマッピング、HQL の出力、クエリの実行）。テストは **Java** のラムダを使っています。**Kotlin のラムダはまだテストしていません。**
> - **設計済み、まだ実装されていないもの:** この章の Kotlin 固有の書き方（`?.`、`?:`、`!!`、範囲、`when`、`in`）。そのバイトコードはコンパイルしてプロトタイプで確認しました（kotlinc 1.7.22 から 2.3.21）。また、多くの条件を H2 で 1 行ずつ確認しました。サブクエリ、`select`、プロシージャも設計だけです。ソートとページングの中核部分（[4. ソートとページング](04-order-paging.md)）、update/insert/delete/upsert（[7. 更新、挿入、削除](07-dml.md)）、Spring Data JPA モジュール（[12. Spring Data JPA](12-spring-data-jpa.md)）は実装済みです。
> - この章の HQL は **予定の** 出力です。細かい部分（たとえば `e0`、`j1` のようなエイリアス名）はまだ変わることがあります。

[Kotlin トラック](README.md) | [マニュアルのホーム](../README.md) | 前: [2. はじめよう](02-getting-started.md) | 次: [4. ソートとページング](04-order-paging.md) | [このページの Java 版](../java/03-where.md)

共通ページ: [1. 概要](../01-overview.md) · [11. エラーと言語](../11-errors-and-languages.md)

---

<a id="31-the-idea-in-one-sentence"></a>
## 3.1 一文で言うと

**lambdaQL のラムダはコードではなくクエリです。** lambdaQL はラムダのバイトコードを読みます。ラムダを実行することはありません。
そのため、Kotlin の実行時のルール（`!!` による `NullPointerException`、`===` のオブジェクトの同一性、コンパイラーが追加する null チェック）は当てはまりません。
lambdaQL は独自のルールを使います。この章ではそのルールを説明します。

ルールがない書き方について、lambdaQL は推測しません。分かりやすいメッセージ付きの例外を投げます（[3.13](#313-what-is-rejected) を参照）。

<a id="32-example-entities"></a>
## 3.2 例で使うエンティティ

例では、すべて次のエンティティを使います（コード全体は [2. はじめよう](02-getting-started.md#example-entities) にあります）。

```kotlin
@Entity class Order(
    @Id var id: Long? = null,
    var price: Double? = null,
    var status: Int? = null,
    var active: Boolean = false,          // プリミティブ: NULL にならない
    var description: String? = null,
    @ManyToOne var customer: Customer? = null,
    var updateAt: LocalDateTime? = null,
    @OneToMany(mappedBy = "order") var items: MutableList<Item> = mutableListOf(),
)
@Entity class Customer(@Id var id: Long? = null, var name: String? = null, var grade: Int? = null)
@Entity class Item(@Id var id: Long? = null, @ManyToOne var order: Order? = null, var price: Double? = null, var qty: Int = 0)
```

いくつかの例では、プロパティがもう 1 つ必要です。その場合は「`flag: Boolean?`」のように書いてあります。

<a id="33-basic-form"></a>
## 3.3 基本の形

```kotlin
val qb = QueryBuilder(entityManagerFactory)

val list = qb.from(Order::class.java)
    .where { it.status == 1 && it.price!! > 100.0 }
    .toList()
// HQL: select e0 from Order e0 where e0.status = 1 and e0.price > 100.0
```

**SQL ブロックの読み方。** `…` は省略した select リスト（エンティティのすべてのカラム）で、`?` はバインドパラメーターです。各行は [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java) から取っています。このテストは各クエリを Hibernate 6.6.5 で H2 2.3、MariaDB 10.11、Oracle 23.26 に対して実行し、Hibernate の SQL が変わると失敗します。 このテストは Java のラムダを使います。意味が同じ Kotlin のラムダは同じ HQL になるので、SQL も同じです。

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `to-hql`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>100.0
```

Kotlin のテストはまだありません。同じクエリを Java で書いたものは [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java) でテストしています（H2、MariaDB、Oracle）。テスト済みの例は [このページの Java 版](../java/03-where.md) を参照してください。

`where()` は何回でも呼べます。条件は `AND` でつながります。

```kotlin
qb.from(Order::class.java)
    .where { it.status == 1 }
    .where { it.active }
    .toList()
// HQL: select e0 from Order e0 where e0.status = 1 and e0.active = true
```

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `many-where`:

```sql
-- H2
select …
from orders o1_0
where o1_0.status=1 and o1_0.active=true

-- MariaDB, Oracle
select …
from orders o1_0
where o1_0.status=1 and o1_0.active=1
```

**HQL の中の値。** ルールは、値がどこから来たかで決まります（決定 D37）。

| 値 | HQL での形 | 例 |
|---|---|---|
| ラムダの中に書いた定数: 数値（小数も）、文字列、`true`/`false`、`null`、enum 定数、`const val` | **リテラル** | `it.price!! > 100.0` → `e0.price > 100.0` |
| キャプチャした値: ローカルの `val` や `var`、プロパティ、関数の引数 | **バインドパラメーター** | `it.price!! > minPrice` → `e0.price > :p1` |
| 計算した値: `BigDecimal("1.5")`、`form.text.trim()`、`LocalDateTime.of(...)` | **バインドパラメーター** | `:p1` |

- 定数は呼び出しのたびに同じです。そのため SQL の文字列は変わらず、データベースはクエリの計画を立てるときに値を見ることができます。
- 文字列定数の中の引用符はエスケープされます。`"O'Brien"` は `'O''Brien'` になります。文字列はユーザーの入力ではなく、クラスにコンパイルされた定数なので、SQL インジェクションの危険はありません。
- キャプチャした値はユーザーから来ることがあるので、**常に** バインドパラメーターです。呼び出しごとに変わるのは値だけです。
- enum 定数は、完全なクラス名で書かれます。たとえば `e0.kind = com.example.OrderKind.GIFT` です。

**`!!` はコンパイラーのためだけのものです。** `price` が `Double?` のとき、Kotlin は `it.price > 100.0` をコンパイルしません。`it.price!! > 100.0` と書いてください。lambdaQL は `!!` を無視します（決定 D23）。`price` が NULL の行は返されません。

<a id="34-comparisons"></a>
## 3.4 比較

| Kotlin | HQL | 注意 |
|---|---|---|
| `it.status == 1` | `e0.status = 1` | |
| `it.status != 1` | `e0.status <> 1` | NULL の行は返されない（[3.6](#36-null-rules) を参照） |
| `it.price!! > p` | `e0.price > :p1` | `<`、`<=`、`>=` も同様 |
| `it.updateAt!! > since` | `e0.updateAt > :p1` | Kotlin では日付を `<`、`>` で比較できる |
| `it.updateAt!!.isAfter(since)` | `e0.updateAt > :p1` | `isBefore` は `<` になる |
| `it.description!! < "m"` | `e0.description < 'm'` | `BigDecimal`、`LocalDate`、`BigInteger` も同様 |
| `it.status!!.compareTo(3) > 0` | `e0.status > 3` | `compareTo(..) op 0` は `op` になる |

nullable なカラムとの比較は **SQL の意味** に従います。
カラムが NULL なら結果は UNKNOWN になり、その行は返されません。（Kotlin のコードを実行すれば、`!!` で `NullPointerException` が投げられます。）

**計算。** `+`、`-`、`*` と単項の `-` をサポートします。`%` は `mod` として書かれます。
整数の `/` は避けてください。データベースによって結果が違います（MariaDB では `5/2 = 2.5`、Kotlin では `2`）。`/` の最終的なルールはまだ決まっていません。最初のバージョンでは拒否されるかもしれません。

**浮動小数点:** `Float`/`Double` のカラムについて、lambdaQL は Kotlin とビット単位で同じ結果になることを約束しません。
キャプチャした値が `NaN` や `Infinity` の場合は、クエリの実行時に拒否されます。

<a id="35-and-or-not"></a>
## 3.5 AND、OR、NOT

| Kotlin | HQL |
|---|---|
| `a && b` | `a and b` |
| `a \|\| b` | `a or b` |
| `!a` | `not (a)` |
| 括弧 | 必要に応じて残す |

```kotlin
.where { (it.status == 1 || it.active) && it.price!! > minPrice }
// HQL: (e0.status = 1 or e0.active = true) and e0.price > :p1

.where { !(it.status == 1 && it.price!! > minPrice) }
// HQL: not (e0.status = 1 and e0.price > :p1)
```

ルール:

- 結果は **SQL の三値論理**（TRUE / FALSE / UNKNOWN）に従います。H2、MariaDB、Oracle で同じ行が返ることを lambdaQL で確認しました。
- コンパイラーは `!` の位置を動かすことがあります（ド・モルガン、`!(x > y)` を `x <= y` に）。これで結果が変わることはありません。
- lambdaQL は **条件を単純化しません**。`it.price!! > p || it.price!! <= p` と書けば、そのまま送られます。`price` が NULL の行は返されません。これは SQL の普通の動作です。
- `&&` と `||` を使ってください。中置関数 `and`、`or`、`xor` は拒否されます（短絡評価しないため）。

**`true`/`false` を返す `if` と `when`。** 許されるのは次の短い形だけです。これらは `AND`、`OR` と同じです。

| Kotlin | 同じ意味 |
|---|---|
| `if (c) x else false` | `c && x` |
| `if (c) true else x` | `c \|\| x` |
| `when { a -> true; b -> true; else -> false }` | `a \|\| b` |

それ以外の `if (c) x else y` や、**値** を選ぶ `if` や `when`（`it.status == (if (flag) 1 else 2)`）は拒否されます（制限 KC6）。
選択がキャプチャした値だけで決まるなら、ラムダの前で値を計算してください。

```kotlin
val wanted = if (flag) 1 else 2
.where { it.status == wanted }
```

<a id="36-null-rules"></a>
## 3.6 NULL のルール

ここがいちばん重要な部分です。簡単な原則が 2 つあります。

1. **null チェックを書いた場所では、lambdaQL はそれを使います。**（`== null`、`!= null`、`?.`、`?:`、2 つの nullable な値の間の `==`）
2. **書いていない場所では、lambdaQL は SQL の意味を使います。** カラムが NULL なら比較は UNKNOWN になり、その行は返されません。

Kotlin コンパイラーは、バイトコードに **隠れた** null チェックを追加します（たとえば `status` が `Int?` のときの `it.status == 1`）。lambdaQL はそれを **無視** します（決定 D22）。意味を持つのは、ソースコードにある null チェックだけです。
そのため、同じ意味の Kotlin のラムダと Java のラムダは、同じ SQL になります。

<a id="361-rule-table"></a>
### 3.6.1 ルール表

| # | Kotlin の書き方 | HQL | NULL の動作 |
|---|---|---|---|
| R12 | nullable なカラムと、定数または non-null の値: `it.status == 1`、`it.id == id`（`id: Long`） | `e0.status = 1`、`e0.id = :p1` | SQL の意味。カラムが NULL: 行は返されない |
| R12 | `it.status != 1` | `e0.status <> 1` | SQL の意味。カラムが NULL: 行は返され **ない** |
| R1 | 大小比較: `it.price!! > p`、`it.status!! <= 3` | `e0.price > :p1`、`e0.status <= 3` | SQL の意味 |
| R11 | どちらも NULL になりうる 2 つの値: `it.status == q`（`q: Int?`）、`it.description == text`（`String?`）、enum の `==` | `e0.status is not distinct from :p1` | **null 安全な値の比較。** `null == null` は true。片方だけ null: false |
| R11 | `it.status != q`（両方 nullable） | `e0.status is distinct from :p1` | null 安全。`q` が null でなければ、NULL の行が返 **される** |
| R3 | `x == null`、`x != null` | `x is null`、`x is not null` | |
| R17 | `===`、`!==` | `==`、`!=` と同じ | オブジェクトの同一性ではなく、値の比較 |
| R9 | キャプチャした値 | バインドパラメーター | [3.6.4](#364-captured-null-values) を参照 |

<a id="362--compares-values"></a>
### 3.6.2 `==` は値を比較する

Kotlin の `==` はもともと値を比較します。lambdaQL では、`===` も **値** を比較します（ルール R17）。
クエリにはオブジェクトの同一性がありません。同じ値を持つ 2 つの別々の `Long` のボックスは等しくなります。

```kotlin
val status: Int? = request.status    // null かもしれない
.where { it.status == status }
// HQL: e0.status is not distinct from :p1
//   status = 3    -> status が 3 の行
//   status = null -> status が NULL の行
```

Hibernate がデータベースごとに `is not distinct from` をどう書くか（確認済み）:

| データベース | SQL |
|---|---|
| H2 | `status is not distinct from ?` |
| MariaDB | `status <=> ?` |
| Oracle | `decode(status, ?, 0, 1) = 0` |

片方が決して NULL にならないと lambdaQL が分かっている場合は、代わりに単純な `=` を書きます。結果は同じで、データベースはインデックスを使えます（[10.2](10-rules-and-limits.md#102-sargable-rule-index-friendly-sql) を参照）。
「決して NULL にならない」とは、`@Id`、`nullable = false` のカラム、プリミティブのプロパティ（`Int`、`Long`、`Double`、`Boolean`）、またはクエリの実行時に null でないキャプチャ値のことです。

```kotlin
.where { it.description == text }    // クエリの実行時に text は null ではない
// HQL: e0.description = :p1
.where { it.description != text }
// HQL: (e0.description <> :p1 or e0.description is null)
```

**NULL になれないカラム: `it.id == id`。** 例のエンティティでは、`id` は `Long?` です。Kotlin は `it.id == id`（キャプチャした `id: Long`）を、null チェックと比較にコンパイルします。
そのまま変換すると `not (e0.id is null or e0.id <> :p1)` になります。この形は正しいのですが、データベースはインデックスをうまく使えません。
id は NULL になれないので、それに対する `is null` は常に偽です。lambdaQL はそれを畳み込んで取り除き、`e0.id = :p1` と書きます。

同じルールはプリミティブのプロパティと `nullable = false` のカラムにも当てはまりますが、パスに **関連のステップがない** ときだけです。注文には顧客がないこともあるので `it.customer?.id` は NULL になりえます。そのため、その null チェックは残ります。

```kotlin
.where { it.id == id }              // id: Long (captured)
// HQL: e0.id = :p1
.where { it.id == null }
// HQL: 1=0
.where { it.customer?.id == null }
// HQL: e0.customer.id is null
```

同じ HQL を Java のラムダで [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java) がテストしています（リージョン `never-null`、[このページの Java 版](../java/03-where.md#362--on-objects-compares-values) を参照）。Java のテストは、Kotlin の `it.id == id` がコンパイルされた形の条件を書いています。

> [!WARNING]
> **Kotlin の non-null 型は証明になりません。** `var code: String` というプロパティでも、データベースからの NULL を持つことがあります。
> Hibernate はチェックなしでカラムの値をフィールドに書き込むからです。
> lambdaQL が見るのは Kotlin の型ではなく、JPA のメタデータです。

<a id="363-write-your-null-checks-yourself"></a>
### 3.6.3 null チェックは自分で書く

```kotlin
// status が NULL ではなく、1 でもない行だけ:
.where { it.status != 1 }
// HQL: e0.status <> 1

// status が NULL の行も含める:
.where { it.status == null || it.status != 1 }
// HQL: e0.status is null or e0.status <> 1
```

> [!IMPORTANT]
> これは Kotlin のコードを実行した場合とは違います。Kotlin では `null != 1` は `true` です。
> lambdaQL では、null チェックを書いていないので、`it.status != 1` は NULL の行を返し **ません**。
> `1` のような定数は nullable ではありません。同じ条件を Java で書いても、同じ SQL になります。

定数と nullable な変数を比べてみます。

```kotlin
val q: Int? = 3
.where { it.status != 3 }    // e0.status <> 3                                 NULL の行は返されない
.where { it.status != q }    // (e0.status <> :p1 or e0.status is null)        NULL の行が返される
```

2 つめの書き方は、どちらも NULL になりうる 2 つの値を比較しています。Kotlin は null に対する結果を定めているので、lambdaQL はそれを守ります（null 安全）。

`String?` に対する `it.description.equals(text)` は `==` と同じです（null 安全）。

`?.`、`?:`、`!!` についての詳細: [9. Kotlin の注意点](09-kotlin-notes.md)。

<a id="364-captured-null-values"></a>
### 3.6.4 null のキャプチャ値

| 書き方 | キャプチャした値が `null` のとき |
|---|---|
| null 安全な書き方: `it.status == q`、`it.description != text` | 許可 |
| 大小比較: `it.price!! > min!!`、すべての `<`、`<=`、`>`、`>=` | クエリの実行時に **例外** |
| `in` で使うコレクションの中の `null` の要素 | クエリの実行時に **例外** |

理由: Kotlin なら、ここで `NullPointerException` を投げます（`min!!`）。これを `IS NULL` に変えると、Kotlin では決して返らない行が返ってしまいます。

**ルールの例外: null ガード。** 同じキャプチャした値を先に null チェックしていれば、null は許可されます。

```kotlin
val min: Double? = request.minPrice   // null かもしれない
.where { min == null || it.price!! > min }
// min == null:  select e0 from Order e0                        (絞り込みなし)
// min = 100.0:  select e0 from Order e0 where e0.price > :p1
```

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `captured-null-guard`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
```

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `captured-null-guard#2`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.price>?
```

しくみ:

- キャプチャした値と定数だけを使う部分は、クエリの実行時にアプリケーションの中で決まります。Kotlin と同じく、左から右へ、短絡評価で決めます。
- `min == null` なら、ガードが true なので条件全体が true です。絞り込みはありません。`min` との比較は書かれません（Kotlin でも実行されません）。
- `min = 100.0` なら、ガードが false なので、`e0.price > :p1` だけが残ります。
- ラムダの解析は 1 回だけです。値によって変わるのは HQL のテキストだけです。
- ガードは先に書かなければなりません。`min == null` のとき `it.price!! > min!! || min == null` はクエリの実行時に例外を投げます。Kotlin の `NullPointerException` と同じです。

これについての Kotlin のテストはまだありません。同じガードを Java で書いたものはテスト済みです。[このページの Java 版](../java/03-where.md#364-captured-null-values) を参照してください。

<a id="37-boolean-columns"></a>
## 3.7 boolean のカラム

例のエンティティでは、`active` は Kotlin の `Boolean`（JVM のプリミティブ、NULL にならない）です。
`flag` の行は、nullable なカラム `var flag: Boolean? = null` の場合です。

| Kotlin | HQL | NULL の行 |
|---|---|---|
| `it.active` | `e0.active = true` | –（NULL にならない） |
| `!it.active` | `e0.active = false` | – |
| `it.flag == true` | `e0.flag is not distinct from true` | 返されない |
| `it.flag != true` | `e0.flag is distinct from true` | **返される**（Kotlin と同じ） |
| `it.flag ?: false` | `(e0.flag is not null and e0.flag = true)` | 返されない |
| `!(it.flag ?: false)` | `(e0.flag is null or not (e0.flag = true))` | **返される** |
| `it.flag!!` | `e0.flag = true` | 返されない（`!!` は無視される） |

注意:

- lambdaQL は `is not true` や `coalesce(flag, false)` を使いません。これらは Oracle で失敗します（ORA-00932）。
- より大きな条件の否定は、常に `not (...)` と書きます。lambdaQL が条件を `(...) = false` や `(...) is null` で囲むことはありません。Oracle 23 は `WHERE` の中でこれらを誤って評価するからです。
- `it.flag ?: false` は **条件の中** なら問題ありません。値やソートキーとしては拒否されます（制限 KC21）。

**2 つの boolean 値の比較**（`==`/`!=`）はサポートしています（決定 D25）。

```kotlin
.where { it.active == (it.status!! > s) }
// HQL: (e0.active = true and e0.status > :p1) or (e0.active = false and e0.status <= :p1)
```

lambdaQL はこれを（`CASE` ではなく）`AND`/`OR` で書くので、データベースはインデックスを使えます。
両辺が同じ条件を共有している場合（たとえば `it.active == it.active`）、その書き方は拒否されます。

<a id="38-strings"></a>
## 3.8 文字列

- 文字列の `==` と `equals` は **データベースの照合順序（collation）** を使います。
  - MariaDB の `_ci` 照合順序は、大文字と小文字の違いや末尾の空白を無視します。
  - Oracle は `''`（空文字列）を NULL として保存します。そのため、Oracle では `it.description == ""` が Kotlin と違う結果になります。
- カラムに対する Kotlin の文字列関数（`startsWith`、`endsWith`、`contains`、`lowercase()`、`length`、`isNullOrEmpty()`、`ignoreCase = true`）には、Kotlin 用の設計があります（ルール R21）。[9. Kotlin の注意点](09-kotlin-notes.md#97-strings) を参照してください。
- カラムに対する `isBlank()`、`isNullOrBlank()`、`trim()` などの標準ライブラリ関数は拒否されます（制限 KC9、KC20）。
- **キャプチャした値だけ** を使う文字列テンプレートは、動くように設計されています。`"$a-$b" == it.description` は `:p1 = e0.description` になり、文字列はアプリケーション側で組み立てられます。ただし、現在の解析コアはラムダの中での文字列の連結をすべて拒否するので、今は文字列をラムダの前で組み立ててください。
- **エンティティの値** を使う文字列テンプレートや `+`（`"${it.description}-x"`）は、まだサポートしていません（KC17、予定）。

<a id="39-in-with-a-captured-collection"></a>
## 3.9 キャプチャしたコレクションでの IN

コレクションをラムダの **外** で作り、`in` を使います。

```kotlin
val ids = listOf(1L, 2L, 3L)

qb.from(Order::class.java).where { it.id in ids }.toList()
// HQL: e0.id in (:p1)

qb.from(Order::class.java).where { it.id !in ids }.toList()
// HQL: e0.id not in (:p1)
```

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `not-in`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.id not in (?,?)
```

`!in` は `not in` になります。id は NULL にならないので、`is null` の部分はありません。NULL になりうるカラムでは、lambdaQL は `x is null or` を加えます。リストに `null` がなければ、Kotlin の `!in` は NULL の値に対して true になるからです。

| ケース | 結果 |
|---|---|
| コレクションがキャプチャした `List`、`Set`、その他の `Collection` | サポート |
| コレクションに `null` の要素がある | クエリの実行時に例外 |
| 空のコレクション | `in` は FALSE、`!in` は TRUE（Hibernate は `1=0` / `1=1` を書く） |
| 1,500 個の要素 | H2、MariaDB、Oracle 23ai で 1 つの文として動作（古い Oracle には 1,000 個の制限があります。テストはしていません） |
| コレクションがエンティティのコレクション: `item in it.items` | **拒否**。`exists()` サブクエリを使ってください（[5. サブクエリ](05-subquery.md)） |
| ラムダの中で作ったコレクション: `it.status in listOf(1, 2)` | **拒否**（KC8）。外で作ってキャプチャしてください |

Kotlin には範囲（`it.status in 1..5`）や定数を使った `when` もあります。[3.14](#314-ranges-kotlin-only) と [3.15](#315-when-kotlin-only) を参照してください。

<a id="310-association-paths"></a>
## 3.10 関連のパス

`it.customer?.name` は **実行されません**。これは「結合した customer の `name` カラム」というカラムを指定するだけです。
lambdaQL は JPA Metamodel で各ステップをチェックします。

| パスのステップ | HQL | 例 |
|---|---|---|
| 対一の関連（`@ManyToOne`、`@OneToOne`） | エイリアス付きの `join`（デフォルトは内部結合、下記を参照） | `it.customer?.name` -> `join e0.customer j1` ... `j1.name` |
| 埋め込み（`@Embedded`） | 同じテーブル、結合なし | `Order` に埋め込みの `address` があれば: `it.address.city` -> `e0.address.city` |
| 対象の `@Id` で終わるパス | 外部キーのカラム、結合なし | `it.customer?.id` -> `e0.customer.id` |
| 関連そのものを `null` と比較 | 外部キーのカラム、結合なし | `it.customer == null` -> `e0.customer is null` |
| コレクション（`@OneToMany`、`@ManyToMany`） | **拒否** | `it.items...` -> `exists()` サブクエリを使う |

`it.customer?.name`、`it.customer!!.name`、そして（Java のエンティティなら）`it.customer.name` は、すべて **同じカラム** `j1.name` を意味します。

`where` の中では、関連のパスはデフォルトで **内部結合（inner join）** です（決定 D36）。

```kotlin
qb.from(Order::class.java)
    .where { it.customer?.name == name }
    .toList()
// HQL: select e0 from Order e0 join e0.customer j1 where j1.name = :p1
```

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `inner-join`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name=?
```

- **customer のない** 注文は結果に入りません。結合する customer の行がないからです。
- lambdaQL は null チェックを代わりに追加しません。パスの前に `it.customer != null &&` を書く必要はありません。

**null ガードは customer のない注文を残します。** customer のない注文で条件が true になりうるなら、lambdaQL はそのパスに `left join` を使います。
内部結合では、Kotlin と SQL で返る行が違ってしまいます。Kotlin はその注文を返しますが、内部結合では失われるからです（決定 D36）。

```kotlin
.where { it.customer == null || it.customer?.name == name }
// HQL: select e0 from Order e0 left join e0.customer j1 where e0.customer is null or j1.name = :p1

.where { it.customer != null && it.customer?.name == name }
// HQL: select e0 from Order e0 join e0.customer j1 where e0.customer is not null and j1.name = :p1
```

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `null-guard`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.id is null or c1_0.name=?
```

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `null-guard#2`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.id is not null and c1_0.name=?
```

`left join` の後では、Hibernate は `e0.customer is null` を `c1_0.id is null` と書きます。意味は同じで、customer のない注文です。

- `||` の他の部分も注文を残すことがあります。`it.status == 1 || it.customer?.name == name` も `left join` を使います。Kotlin では、status が 1 の注文は customer がなくても条件に合うからです。
- 別の `where` 呼び出しがその注文をどのみち除く場合（たとえば `.where { it.customer?.name != null }`）、結合は内部結合のままです。
- これについての Kotlin のテストはまだありません。同じ条件を Java で書いたものはテスト済みです。[このページの Java 版](../java/03-where.md#310-association-paths) を参照してください。

customer の **ない** 注文を探すには、関連そのものを比較してください。これは外部キーのカラムをチェックし、結合はしません。

```kotlin
.where { it.customer == null }
// HQL: select e0 from Order e0 where e0.customer is null
```

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `no-association`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id is null
```

> [!WARNING]
> **`?.` を使っても customer のない注文は戻りません。** Kotlin では、customer がないとき `it.customer?.name == null` は `true` です。
> lambdaQL ではここでパスが内部結合なので、`it.customer?.name == null` は customer に名前がない注文だけを返します。
> customer のない注文も含めるには、`it.customer == null || it.customer?.name == null` と書いてください。この null ガードによって、パスは `left join` になります（上記を参照）。

その他の事実:

- 同じパスには 1 つのエイリアスが付きます。それは `where`、`orderBy`、`select` で共有されます。
- `it.customer?.id == 5L` は外部キーのカラム `e0.customer.id` を使います。結合は不要です。
- `orderBy` か `select` **だけ** で使うパスは `left join` なので、そこでは行が失われません（ルール S8、J1）。同じパスを `where` でも使うと、`orderBy` と `select` は `where` の結合を使います。null ガードが `left join` にしていない限り、それは内部結合です。
- 2 つのエンティティの比較（`it.customer == c`）は **予定** です（KC13）。今は id を比較してください: `it.customer?.id == c.id`（ここで `c` はキャプチャしたオブジェクトです）。

<a id="311-captured-values"></a>
## 3.11 キャプチャした値

ラムダは外の値を使えます。ローカルの値、プロパティ、キャプチャしたオブジェクトのプロパティです。

```kotlin
val since = LocalDateTime.of(2026, 1, 1, 0, 0)
.where { it.updateAt!! > since }                 // ローカルの val
.where { it.status == defaultStatus }            // 外側のクラスのプロパティ
.where { it.price!! > filter.minPrice!! }        // キャプチャしたオブジェクトのプロパティ
.where { it.updateAt!! > LocalDateTime.of(2026, 1, 1, 0, 0) }   // エンティティを使わない呼び出し
```

| キャプチャするもの | lambdaQL の使い方 |
|---|---|
| `val`（`Long?`、`String`、... も） | バインドパラメーター |
| `var` | バインドパラメーター。値は **クエリを実行するたびに** 読まれる |
| 外側のクラスのプロパティ（`private` も） | クエリを実行するたびに読まれる |
| `companion object` の `val`、トップレベルの `val`、`object` の `val` | バインドパラメーター |
| `const val` | リテラルとして HQL に書かれる（D37） |
| ラムダの中でキャプチャした変数に書き込む（`n++`） | **拒否**（KC12） |

ルール:

- エンティティを使わない部分は、**クエリを実行するたびに** アプリケーション側で計算されます。その結果がバインドパラメーターになります。
- ラムダは 1 回だけ解析され、結果はキャッシュされます。新しいキャプチャ値は呼び出しのたびに読まれます。
- SQL になるのはエンティティの部分だけです。

<a id="312-errors-that-depend-on-order-of-evaluation"></a>
## 3.12 評価の順番に依存するエラー

SQL は `AND`/`OR` を左から右へ評価することを約束しません。そのため、次のような「ガード」は SQL では安全ではありません。

```kotlin
qb.from(Item::class.java)
    .where { it.qty != 0 && it.price!! / it.qty > 2 }   // 拒否される
```

ガードの下にある `/`、`%`、キャストは拒否されます。割り算を使わずに条件を書いてください。たとえば `it.qty > 0 && it.price!! > 2.0 * it.qty` です（`qty` が正なら同じ行になります）。

<a id="313-what-is-rejected"></a>
## 3.13 拒否されるもの

lambdaQL は、コード `[LQL-xxxx]` とソースの行を付けて例外を投げます。推測はしません。
ほとんどの形は、ラムダを解析するとき（最初に使うとき）に見つかります。問題のあるキャプチャ値（null、NaN）は、クエリの実行時に見つかります。
[11. エラーと言語](../11-errors-and-languages.md) を参照してください。

| 書き方 | 例 | どうするか |
|---|---|---|
| ローカルの `val`/`var`、ループ、複数の文を持つブロック | `{ val x = 1; it.status == x }` | 1 つの式で書く |
| 値としての `if`/`when`（KC6） | `it.status == (if (flag) 1 else 2)` | ラムダの前で値を計算する |
| 一般的な `if (c) x else y`（KC6） | | [3.5](#35-and-or-not) の許される形を使う |
| 中置の `and`、`or`、`xor` | `it.active and (it.status == 1)` | `&&`、`\|\|` を使う |
| エンティティの値を使う文字列テンプレートや `+`（KC17） | `it.description == "A" + it.status` | カラムを別々に比較する（予定） |
| ラムダの中で作ったコレクション（KC8） | `it.status in listOf(1, 2)` | 外で作ってキャプチャする |
| ラムダの中のラムダ | `ids.any { id -> id == it.id }` | `in`（[3.9](#39-in-with-a-captured-collection)）かサブクエリを使う |
| サポートしていない標準ライブラリ関数（KC9、KC20） | `it.description?.trim()`、`isBlank()` | `isNullOrEmpty()` か直接の比較 |
| 自分で書いた inline でない拡張関数（KC11） | `it.isOpen()` | `inline fun` にするか、条件を書く |
| エンティティのその他のメソッド | `it.calcSomething()` | プロパティだけを使う |
| エンティティのコレクションのパス | `it.items.size > 0` | `exists()` サブクエリ |
| エンティティとエンティティの比較（KC13） | `it.customer == c` | id を比較する（予定: エンティティの比較） |
| ガードの下の `/`、`%`、キャスト | [3.12](#312-errors-that-depend-on-order-of-evaluation) を参照 | ガードを取り除くか、条件を変える |
| 大小比較での null のキャプチャ値 | `min == null` のときの `it.price!! > min!!` | null ガードを追加する |
| `NaN` / `Infinity` のキャプチャ値 | `it.price!! > Double.NaN` | NaN を送らない |
| `isNaN()`（KC14） | `it.price!!.isNaN()` | – |
| キャプチャした変数への書き込み（KC12） | `{ n++; it.active }` | ラムダの外で計算する |
| 匿名オブジェクト、関数型の値（KC4、KC5） | `val f: (Order) -> Boolean` での `where(f)` | ラムダリテラルを渡す |

例外の型（すべて `LambdaQLException` を継承）:

| 例外 | いつ |
|---|---|
| `LambdaAnalysisException` | ラムダを読めない（シリアライズ可能なラムダではない、クラスとしてコンパイルされた、など） |
| `UnsupportedLambdaShapeException` | 形がサポートされていない（ループ、値としての `if`、...） |
| `TranslationException` | 意味を変換できない（不明な関数、コレクションのパス、...） |

Kotlin の制限の一覧: [9. Kotlin の注意点](09-kotlin-notes.md#914-kotlin-limits-kc1-to-kc21)。ルールの一覧: [10. ルールと制限](10-rules-and-limits.md)。

<a id="314-ranges-kotlin-only"></a>
## 3.14 範囲（Kotlin だけ）

次の書き方はすべてサポートしています（決定 D21）。

```kotlin
.where { it.status in 1..5 }          // e0.status between 1 and 5
.where { it.status !in 1..5 }         // not (e0.status between 1 and 5)
.where { it.status in 1 until 5 }     // e0.status >= 1 and e0.status < 5
.where { it.status in 1..<5 }         // e0.status >= 1 and e0.status < 5
.where { it.status in lo..hi }        // e0.status between :p1 and :p2   （lo、hi はキャプチャした値）
.where { it.price!! in 1.0..10.0 }    // e0.price between 1.0 and 10.0
.where { it.status in 5 downTo 1 }    // サポート（1..5 と同じ行）
```

| 範囲の書き方 | サポート |
|---|---|
| `a..b`、`a until b`、`a..<b`、`a downTo b` | はい |
| `!in` | はい、`not (...)` |
| `LocalDate`、`LocalDateTime`、`BigDecimal`、`String` の範囲 | はい（`compareTo` にコンパイルされる） |
| `step` | いいえ |

**nullable な対象。** Kotlin は `Int?` と `Long?` に対する `in` を受け付けます。lambdaQL は null ガードを追加 **しません**（D22）。
NULL の行は返されず、`!in` でも返されません（SQL のルール）。
それ以外の nullable な型（たとえば `LocalDateTime?`）では、Kotlin は `in` をコンパイルしません。`!!` を付けてください。

```kotlin
.where { it.updateAt!! in from..to }  // e0.updateAt between :p1 and :p2
```

<a id="315-when-kotlin-only"></a>
## 3.15 `when`（Kotlin だけ）

`when` は **条件として** サポートしています。どの分岐も `true` か `false`（または条件）で終わらなければなりません。
lambdaQL はそれを `IN`、`OR`、`AND` に変えます。

```kotlin
.where { when (it.status) { 1, 2 -> true; else -> false } }
// e0.status in (1, 2)

.where { when (it.description) { "a", "b", "c" -> true; else -> false } }
// e0.description in ('a', 'b', 'c')

.where { when (it.status) { in 1..5 -> true; else -> false } }
// e0.status between 1 and 5

.where { when { it.status == 1 -> true; it.active -> true; else -> false } }
// e0.status = 1 or e0.active = true

.where { when (it.status) { 1 -> it.active; else -> false } }
// e0.status = 1 and e0.active = true
```

enum の対象でも動きます（ここでは追加のプロパティ `kind: OrderKind?` を使い、`OrderKind` はパッケージ `com.example` にあります）。

```kotlin
.where { when (it.kind) { OrderKind.EXPRESS, OrderKind.GIFT -> true; else -> false } }
// e0.kind in (com.example.OrderKind.EXPRESS, com.example.OrderKind.GIFT)
```

nullable な対象には null ガードが付きません（D22）。対象が NULL の行は返されず、
`NOT` の下でも返されません。Kotlin のコードを実行すれば `else` に進みますが、lambdaQL は SQL の意味を使います。これは意図したルールです。

**値を選ぶ** `when`（または `if`）はサポートしていません（KC6）。

```kotlin
// サポートしていない
.where { (if (it.active) it.price else 0.0)!! > 10.0 }
```

代わりに `&&` と `||` で条件を書いてください。

---

[Kotlin トラック](README.md) | 前: [2. はじめよう](02-getting-started.md) | 次: [4. ソートとページング](04-order-paging.md) | [このページの Java 版](../java/03-where.md)
