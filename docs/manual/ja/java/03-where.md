<a id="3-conditions-where-java"></a>
# 3. 条件（`where`）（Java）

> [!IMPORTANT]
> このページは韓国語版のマニュアルより古い内容です。最新のルールは[韓国語のページ](../../ko/java/03-where.md)を参照してください。

[Java トラック](README.md) | [マニュアルのホーム](../README.md) | 前: [2. はじめよう](02-getting-started.md) | 次: [4. ソートとページング](04-order-paging.md) | [このページの Kotlin 版](../kotlin/03-where.md)

共通ページ: [1. 概要](../01-overview.md) · [11. エラーと言語](../11-errors-and-languages.md)

---

<a id="31-the-idea-in-one-sentence"></a>
## 3.1 一文で言うと

**lambdaQL のラムダはコードではなくクエリです。** lambdaQL はラムダのバイトコードを読みます。ラムダを実行することはありません。
そのため、Java の実行時のルール（NullPointerException、オブジェクトの同一性、ボクシングのキャッシュ）は当てはまりません。
lambdaQL は独自のルールを使います。この章ではそのルールを説明します。

ルールがない書き方について、lambdaQL は推測しません。分かりやすいメッセージ付きの例外を投げます（[3.13](#313-what-is-rejected) を参照）。

<a id="32-example-entities"></a>
## 3.2 例で使うエンティティ

例では、すべて次のエンティティを使います（getter と setter は省略）。

```java
@Entity class Order {
    @Id Long id;
    Double price;
    Integer status;
    boolean active;            // getter: isActive()
    String description;
    @ManyToOne Customer customer;
    LocalDateTime updateAt;
    @OneToMany(mappedBy = "order") List<Item> items;
}
@Entity class Customer { @Id Long id; String name; Integer grade; }
@Entity class Item     { @Id Long id; @ManyToOne Order order; Double price; int qty; }
```

<a id="33-basic-form"></a>
## 3.3 基本の形

```java
QueryBuilder qb = new QueryBuilder(entityManagerFactory);

List<Order> list = qb.from(Order.class)
    .where(o -> o.getStatus() == 1 && o.getPrice() > 100.0)
    .toList();
// HQL: select e0 from Order e0 where e0.status = 1 and e0.price > 100.0
```

**SQL ブロックの読み方。** `…` は省略した select リスト（エンティティのすべてのカラム）で、`?` はバインドパラメーターです。各行は [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java) から取っています。このテストは各クエリを Hibernate 6.6.5 で H2 2.3、MariaDB 10.11、Oracle 23.26 に対して実行し、Hibernate の SQL が変わると失敗します。

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `to-hql`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>100.0
```

**(テスト済み)** と書かれた例は、[`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java) からコピーしたものです。
このテストは HQL をチェックし、クエリを実行して、同じ条件で Java 側で絞り込んだ行と比べます。H2、MariaDB、Oracle で通ります。
テストのエンティティは 3.2 と少し違います。`price` は `double`、`status` は `short`、`Customer.id` は `int` で、`Order` には enum の `kind`（`Order.Kind`: `NORMAL`、`RUSH`）があります。

`toHql()` は、クエリを実行せずに HQL とパラメーターを返します。
**(テスト済み)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java)、リージョン `to-hql`:

```java
RenderedQuery q = qb.from(Order.class)
        .where(o -> o.getStatus() == 1 && o.getPrice() > 100)
        .toHql();
// constants written in the lambda are literals
assertEquals("select e0 from Order e0 where e0.status = 1 and e0.price > 100.0", q.hql());
assertEquals(Map.of(), q.parameters());

double minPrice = 100;
RenderedQuery q2 = qb.from(Order.class)
        .where(o -> o.getStatus() == 1 && o.getPrice() > minPrice)
        .toHql();
// captured values are bind parameters
assertEquals("select e0 from Order e0 where e0.status = 1 and e0.price > :p1", q2.hql());
assertEquals(Map.of("p1", 100.0), q2.parameters());
```

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `to-hql`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>100.0
```

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `to-hql#2`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>?
```

`where()` は何回でも呼べます。条件は `AND` でつながります。
**(テスト済み)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java)、リージョン `where-many`:

```java
RenderedQuery q = qb.from(Order.class)
        .where(o -> o.getStatus() == 1 || o.getStatus() == 3)
        .where(o -> o.isActive())
        .toHql();
assertEquals("select e0 from Order e0 where (e0.status = 1 or e0.status = 3) and e0.active = true", q.hql());
```

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `where-many`:

```sql
-- H2
select …
from orders o1_0
where (o1_0.status=1 or o1_0.status=3) and o1_0.active=true

-- MariaDB, Oracle
select …
from orders o1_0
where (o1_0.status=1 or o1_0.status=3) and o1_0.active=1
```

メソッド参照も使えます。`where(Order::isActive)` は `where(o -> o.isActive())` と同じです。

**HQL の中の値。** ルールは、値がどこから来たかで決まります（決定 D37）。

| 値 | HQL での形 | 例 |
|---|---|---|
| ラムダの中に書いた定数: 数値（小数も）、文字列、`true`/`false`、`null`、enum 定数、コンパイル時の `static final` 定数 | **リテラル** | `o.getPrice() > 100` → `e0.price > 100.0` |
| キャプチャした値: ローカル変数、フィールド、メソッドの引数 | **バインドパラメーター** | `o.getPrice() > minPrice` → `e0.price > :p1` |
| 計算した値: `new BigDecimal("1.5")`、`form.getText().trim()`、`LocalDateTime.of(...)` | **バインドパラメーター** | `:p1` |

- 定数は呼び出しのたびに同じです。そのため SQL の文字列は変わらず、データベースはクエリの計画を立てるときに値を見ることができます。
- 文字列定数の中の引用符はエスケープされます。`"O'Brien"` は `'O''Brien'` になります。文字列はユーザーの入力ではなく、クラスにコンパイルされた定数なので、SQL インジェクションの危険はありません。
- キャプチャした値はユーザーから来ることがあるので、**常に** バインドパラメーターです。呼び出しごとに変わるのは値だけです。

enum 定数は、完全なクラス名で書かれます。
**(テスト済み)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java)、リージョン `enum`:

```java
RenderedQuery q = qb.from(Order.class).where(o -> o.getKind() == Order.Kind.RUSH).toHql();
assertEquals("select e0 from Order e0 where e0.kind = org.lambdaql.query.Order$Kind.RUSH", q.hql());
```

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `enum`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.kind='RUSH'
```

<a id="34-comparisons"></a>
## 3.4 比較

| Java | HQL | 注意 |
|---|---|---|
| `o.getStatus() == 1` | `e0.status = 1` | |
| `o.getStatus() != 1` | `e0.status <> 1` | |
| `o.getPrice() > p` | `e0.price > :p1` | `<`、`<=`、`>=` も同様 |
| `o.getUpdateAt().isAfter(since)` | `e0.updateAt > :p1` | `isBefore` は `<` になる |
| `o.getDescription().compareTo("m") < 0` | `e0.description < 'm'` | `compareTo(..) op 0` は `op` になる |

数値との比較（`== 1` や `> 100.0` のように片方がプリミティブ）は **SQL の意味** に従います。
カラムが NULL なら結果は UNKNOWN になり、その行は返されません。（Java なら NullPointerException になります。）

`+`、`-`、`*` による簡単な計算は設計に含まれています。
整数の `/` は避けてください。データベースによって結果が違います（MariaDB では `5/2 = 2.5`）。`/` の最終的なルールはまだ決まっていません。

**浮動小数点:** `float`/`double` のカラムについて、lambdaQL は Java とビット単位で同じ結果になることを約束しません。
キャプチャした値が `NaN` や `Infinity` の場合は、クエリの実行時に拒否されます。

<a id="35-and-or-not"></a>
## 3.5 AND、OR、NOT

| Java | HQL |
|---|---|
| `a && b` | `a and b` |
| `a \|\| b` | `a or b` |
| `!a` | `not (a)` |
| 括弧 | 必要に応じて残す |

```java
.where(o -> (o.getStatus() == 1 || o.isActive()) && o.getPrice() > minPrice)
// HQL: (e0.status = 1 or e0.active = true) and e0.price > :p1

.where(o -> !(o.getStatus() == 1 && o.getPrice() > minPrice))
// HQL: not (e0.status = 1 and e0.price > :p1)
```

ルール:

- 結果は **SQL の三値論理**（TRUE / FALSE / UNKNOWN）に従います。H2、MariaDB、Oracle で同じ行が返ることを lambdaQL で確認しました。
- javac は `!` の位置を動かすことがあります（ド・モルガン、`!(x > y)` を `x <= y` に）。これで結果が変わることはありません。
- lambdaQL は **条件を単純化しません**。`o.getPrice() > p || o.getPrice() <= p` と書けば、そのまま送られます。`price` が NULL の行は返されません。これは SQL の普通の動作です。
- `&&` と `||` を使ってください。短絡評価しない演算子 `&`、`|`、`^` は拒否されます。

**boolean の三項演算子。** 許されるのは 3 つの短い形だけです。これらは `AND`、`OR`、`NOT` と同じです。

| Java | 同じ意味 |
|---|---|
| `c ? x : false` | `c && x` |
| `c ? true : x` | `c \|\| x` |
| `c ? false : true` | `!c` |

それ以外の三項演算子（`c ? x : y`）や、値を選ぶ三項演算子（`o.getStatus() == (flag ? 1 : 2)`）は拒否されます。
選択がキャプチャした値だけで決まるなら、ラムダの前で値を計算してください。

```java
int wanted = flag ? 1 : 2;
qb.from(Order.class).where(o -> o.getStatus() == wanted).toList();
```

<a id="36-null-rules"></a>
## 3.6 NULL のルール

ここがいちばん重要な部分です。簡単な原則が 2 つあります。

1. **エンティティの値は SQL の意味で比べます**（決定 D48）。オブジェクト同士の `==`、`equals`、`Objects.equals`、`!=` は `=` と `<>` になります。値が NULL の行はどちらにも一致しません。結果が Java と異なりうるとき、lambdaQL は `LQL-5009` を `DEBUG` レベルでログに出します。
2. **キャプチャした値は Java のルールをそのまま使います。** キャプチャした値は先に Java で計算されます。キャプチャした `null` は `is null` または `is not null` になります。Java なら例外を投げる場合（アンボクシング、`null.equals(...)`）、lambdaQL も例外を投げます（[3.6.4](#364-captured-null-values) を参照）。

lambdaQL が null を扱うのは **あなたが書いたときだけ** です（`== null`、`!= null`）。
隠れた null チェックを勝手に追加することはありません。

<a id="361-rule-table"></a>
### 3.6.1 ルール表

| # | Java の書き方 | HQL | NULL の動作 |
|---|---|---|---|
| R1 | 片方がプリミティブ: `o.getStatus() == 1`、`o.getPrice() > p` | `e0.status = 1`、`e0.price > :p1` | SQL の意味。カラムが NULL: 行は返されない |
| R2 | 両方がオブジェクト: `o.getStatus() == status`（`Integer == Integer`）、`String == String`、enum | `e0.status = :p1`。キャプチャした `null`: `e0.status is null` | **値の比較。** カラムが NULL: 行は返されない |
| R2 | `o.getStatus() != status`（オブジェクト） | `e0.status <> :p1`。キャプチャした `null`: `e0.status is not null` | SQL の意味。カラムが NULL: 行は **返されない**（Java なら返される） |
| R2 | エンティティの値どうし: `o.getDescription() == o.getCustName()` | `e0.description = e0.custName` | SQL の意味。どちらかが NULL: 行は返されない（NULL と NULL も同じ） |
| R3 | `x == null`、`x != null` | `x is null`、`x is not null` | |
| R4 | `o.getDescription().equals(p)` | `e0.description = :p1` | SQL の意味。カラムが NULL: 行は返されない。`p == null`: 例外 |
| R5 | `Objects.equals(a, b)`、`"x".equals(a)`、`Boolean.TRUE.equals(a)` | R2 と同じ: `a = b`、`a = :p1`、キャプチャした `null` なら `a is null` | R2 と同じ |
| R9 | キャプチャした値 | バインドパラメーター | [3.6.4](#364-captured-null-values) を参照 |

どの Java の型が R1 になり、どの型が R2 になるかは、[9. Java の注意点](09-java-notes.md#93-equality-of-boxed-values) で説明しています。

<a id="362--on-objects-compares-values"></a>
### 3.6.2 オブジェクトの `==` は値を比較する

Java では、2 つの `Integer`、`Long`、`String` オブジェクトの `==` は **オブジェクトの同一性** をチェックします。
lambdaQL では、これは **値の比較** です。127 を超える `Long` の値や `new String(...)` も関係ありません。
ラムダはクエリなので、Java のメモリのルールは当てはまりません。

```java
Integer status = request.getStatus();    // null かもしれない
qb.from(Order.class).where(o -> o.getStatus() == status).toList();
//   status = 3    -> HQL: e0.status = :p1    status が 3 の行
//   status = null -> HQL: e0.status is null  status が NULL の行
```

カラムを関数で包むことはないので、データベースはインデックスを使えます。

lambdaQL は、クエリの実行時にキャプチャした値を見ます。`null` なら `is null`、それ以外の値なら単純な `= :p1` になります。
**(テスト済み)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java)、リージョン `null-safe`:

```java
// the SQL meaning (D48): = NULL matches no row; lambdaQL logs LQL-4045 at WARN when it runs with a null
String d = null;
RenderedQuery q = qb.from(Order.class).where(o -> Objects.equals(o.getDescription(), d)).toHql();
assertEquals("select e0 from Order e0 where e0.description = :p1", q.hql());
// to find NULL, write the null check
RenderedQuery q3 = qb.from(Order.class).where(o -> o.getDescription() == null).toHql();
assertEquals("select e0 from Order e0 where e0.description is null", q3.hql());

String d2 = "a";
RenderedQuery q2 = qb.from(Order.class).where(o -> Objects.equals(o.getDescription(), d2)).toHql();
assertEquals("select e0 from Order e0 where e0.description = :p1", q2.hql());
```

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `null-safe`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.description=?
```

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `null-safe#2`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.description=?
```

> [!TIP]
> **IDE の警告。** IntelliJ はこの書き方に対して「Number objects compared using `==`」と表示します。
> lambdaQL のラムダの中では、これは意図した書き方です。`@SuppressWarnings("NumberEquality")` を使えます。

**NULL になれないカラム。** NULL になれないカラムがあります。id、プリミティブのカラム、`nullable = false` のカラムです。
このようなカラムでは `is null` は常に偽なので、lambdaQL はそれを畳み込んで取り除きます。

- `o.getId() == null` は `1=0`（行なし）になります。
- `o.getId() != null && o.getId() == id` は `e0.id = :p1` になります。null チェックがなくなり、データベースはインデックスを使えます。

これは `Long?` の id に対する Kotlin の `it.id == id` のバイトコードでもあるので、Kotlin の書き方も単純な `e0.id = :p1` になります。
このルールは、パスに **関連のステップがない** ときだけ適用されます。注文には顧客がないこともあるので `o.getCustomer().getId()` は NULL になりえます。そのため、その null チェックは残ります。

**(テスト済み)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java)、リージョン `never-null`:

```java
// the id can never be NULL, so the null check is dropped (this is what Kotlin's it.id == id compiles to)
assertEquals("select e0 from Order e0 where e0.id = :p1",
        qb.from(Order.class).where(o -> o.getId() != null && o.getId() == id).toHql().hql());
assertEquals("select e0 from Order e0 where 1=0", qb.from(Order.class).where(o -> o.getId() == null).toHql().hql());
// through an association the row may have no customer, so the check stays
assertEquals("select e0 from Order e0 where e0.customer.id is null",
        qb.from(Order.class).where(o -> Objects.equals(o.getCustomer().getId(), null)).toHql().hql());
```

<a id="363-write-your-null-checks-yourself"></a>
### 3.6.3 null チェックは自分で書く

```java
// status が NULL ではなく、1 でもない行だけ:
.where(o -> o.getStatus() != 1)
// HQL: e0.status <> 1

// status が NULL の行も含める:
.where(o -> o.getStatus() == null || o.getStatus() != 1)
// HQL: e0.status is null or e0.status <> 1
```

`equals()` と `Objects.equals()` は違います。

```java
.where(o -> o.getDescription().equals(text))     // HQL: e0.description = :p1
.where(o -> Objects.equals(o.getDescription(), text))
                                                 // HQL: e0.description = :p1（text が null なら is null）
.where(o -> !Objects.equals(o.getStatus(), 1))   // HQL: e0.status <> 1
                                                 //      （NULL の行は返されない: SQL の意味）
```

**エンティティの値に対する SQL の意味**（決定 D48）。エンティティの値どうし、およびエンティティの値と値の `!=` による比較は SQL に従います。値が NULL の行は `=` にも `<>` にも一致しません。
Java では `!text.equals(null)` も `Objects.equals(null, null)` も true なので、Java ならそれらの行を返します。このようなことが起こりうるとき、lambdaQL は `LQL-5009` を `DEBUG` レベルでログに出します（条件ごとに 1 回）。NULL の行を残すには、ラムダの中に null チェックを書いてください。

**(テスト済み)** [`SqlMeaningTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlMeaningTest.java)、リージョン `sql-meaning`（H2、MariaDB、Oracle。行は description/custName の組 `a/a`、`a/b`、`null/a`、`null/null`、`b/null` で、`text` は `"a"`）:

```java
// two entity values: = and <> as in SQL; a row with a NULL on either side matches neither
SelectQuery<Order> same = qb.from(Order.class).where(o -> Objects.equals(o.getDescription(), o.getCustName()));
assertEquals("select e0 from Order e0 where e0.description = e0.custName", same.toHql().hql());
assertEquals(List.of("a/a"), pairs(same.toList()));                    // null/null is not "equal" in SQL
SelectQuery<Order> different = qb.from(Order.class).where(o -> !Objects.equals(o.getDescription(), o.getCustName()));
assertEquals(List.of("a/b"), pairs(different.toList()));               // rows with a NULL are left out
// an entity value and a value: a NULL column does not match <>
SelectQuery<Order> notA = qb.from(Order.class).where(o -> !text.equals(o.getDescription()));
assertEquals("select e0 from Order e0 where e0.description <> :p1", notA.toHql().hql());
assertEquals(List.of("b/null"), pairs(notA.toList()));
// to keep the NULL rows, say so in the lambda
SelectQuery<Order> notAOrNull = qb.from(Order.class).where(o -> o.getDescription() == null || !text.equals(o.getDescription()));
assertEquals(List.of("b/null", "null/a", "null/null"), pairs(notAOrNull.toList()));
// a NULL Boolean is not false
assertEquals(List.of("f"), qb.from(Coded.class).where(c -> !c.getVip()).toList().stream().map(Coded::getCode).toList());
```

<a id="364-captured-null-values"></a>
### 3.6.4 null のキャプチャ値

| 書き方 | キャプチャした値が `null` のとき |
|---|---|
| キャプチャした値との `==`、`!=`、`Objects.equals(...)`（R2、R5）: `o.getStatus() == status` | 許可: `is null` / `is not null` |
| `p.equals(o.getDescription())`（`p` がレシーバー） | クエリの実行時に **例外** |
| `o.getDescription().equals(p)` | **例外** |
| Java が値をアンボクシングする比較: `o.getPrice() > p`（`Double p`）、そしてすべての `<`、`<=`、`>`、`>=` | **例外** |

理由: Java では、これらの書き方は NullPointerException を投げるか、常に false になります。これを `IS NULL` に変えると、Java では決して返らない行が返ってしまいます。

**ルールの例外: null ガード。** 同じキャプチャした値を先に null チェックしていれば、null は許可されます。
**(テスト済み)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java)、リージョン `captured-null-guard`:

```java
Double min = null;                                       // for example an empty search field
RenderedQuery q = qb.from(Order.class).where(o -> min == null || o.getPrice() > min).toHql();
// the guard is decided in Java when the query runs: no filter at all
assertEquals("select e0 from Order e0", q.hql());

Double min2 = 100.0;
RenderedQuery q2 = qb.from(Order.class).where(o -> min2 == null || o.getPrice() > min2).toHql();
assertEquals("select e0 from Order e0 where e0.price > :p1", q2.hql());
assertEquals(Map.of("p1", 100.0), q2.parameters());
```

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `captured-null-guard`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
```

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `captured-null-guard#2`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.price>?
```

しくみ:

- キャプチャした値と定数だけを使う部分は、クエリの実行時に Java の中で決まります。Java と同じく、左から右へ、短絡評価で決めます。
- `min == null` なら、ガードが true なので条件全体が true です。絞り込みはありません。比較 `o.getPrice() > min` は書かれません（Java でも実行されません）。
- `min = 100.0` なら、ガードが false なので、`e0.price > :p1` だけが残ります。
- ラムダの解析は 1 回だけです。値によって変わるのは HQL のテキストだけです。
- ガードは先に書かなければなりません。`min == null` のとき `o.getPrice() > min || min == null` はクエリの実行時に例外を投げます。Java の NullPointerException と同じです。
- `&&` では、ガードは逆に働きます: `min == null` のとき `min != null && o.getPrice() > min` は行を返しません（`where 1=0`）。

<a id="365-converted-attributes-convert"></a>
### 3.6.5 変換される属性（`@Convert`）

JPA の `AttributeConverter` を持つ属性は、Java の値で比べます。Hibernate 6.6 は、このような属性と比べるリテラルを変換し、バインドパラメーターも変換します。そのため条件は Java の値で書き、保存された行を見つけられます（H2、MariaDB、Oracle で確認済み）。

**(テスト済み)** [`ConvertTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ConvertTest.java)、リージョン `convert`:

```java
// code is stored reversed ("abc" as "cba"); vip is stored as 'Y'/'N' (YesNoConverter)
SelectQuery<Coded> literal = qb.from(Coded.class).where(c -> c.getCode().equals("abc"));
assertEquals("select e0 from Coded e0 where e0.code = 'abc'", literal.toHql().hql());
assertEquals(List.of("abc"), codes(literal.toList()));       // Hibernate converts the literal
```

<a id="37-boolean-columns"></a>
## 3.7 boolean のカラム

例のエンティティでは、`active` はプリミティブの `boolean`（getter は `isActive()`）です。
`getActive()` の行は、nullable な `Boolean` のカラムの場合です。

| Java | HQL | NULL の行 |
|---|---|---|
| `o.isActive()` | `e0.active = true` | –（プリミティブの `boolean` は決して NULL にならない） |
| `!o.isActive()` | `e0.active = false` | –（プリミティブの `boolean` は決して NULL にならない） |
| `Boolean.TRUE.equals(o.getActive())`（nullable な `Boolean`） | `e0.active = :p1`（`:p1` は `true`） | 返されない |
| `!Boolean.TRUE.equals(o.getActive())` | `e0.active <> :p1` | **返されない**（SQL の意味、D48。Java なら返される） |
| `!o.getActive()`（nullable な `Boolean`） | `e0.active = false` | **返されない**（SQL の意味: NULL は false ではない） |

**(テスト済み)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java)、リージョン `boolean`:

```java
assertEquals("select e0 from Order e0 where e0.active = true",
        qb.from(Order.class).where(o -> o.isActive()).toHql().hql());
assertEquals("select e0 from Order e0 where e0.active = false",       // primitive boolean: never NULL
        qb.from(Order.class).where(o -> !o.isActive()).toHql().hql());
```

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `boolean`:

```sql
-- H2
select …
from orders o1_0
where o1_0.active=true

-- MariaDB, Oracle
select …
from orders o1_0
where o1_0.active=1
```

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `boolean#2`:

```sql
-- H2
select …
from orders o1_0
where o1_0.active=false

-- MariaDB, Oracle
select …
from orders o1_0
where o1_0.active=0
```

H2 には本物の boolean 型があるので、SQL は `true` / `false` になります。MariaDB と Oracle は boolean を数値で保存するので、Hibernate は `1` / `0` を書きます。

注意:

- nullable な `Boolean` のカラムに対する `!o.getActive()` は、Java ではアンボクシングの呼び出しです（NULL なら NullPointerException）。lambdaQL は SQL の意味を使います。NULL の行は返されません。
- lambdaQL は `is not true` や `coalesce(active, false)` を使いません。これらは Oracle で失敗します（ORA-00932）。
- より大きな条件の否定は、常に `not (...)` と書きます。lambdaQL が条件を `(...) = false` や `(...) is null` で囲むことはありません。Oracle 23 は `WHERE` の中でこれらを誤って評価するからです。

**2 つの boolean 値の比較**（`==`/`!=`）はサポートしています。

```java
.where(o -> o.isActive() == (o.getStatus() > s))
// HQL: (e0.active = true and e0.status > :p1) or (e0.active = false and e0.status <= :p1)
```

lambdaQL はこれを（`CASE` ではなく）`AND`/`OR` で書くので、データベースはインデックスを使えます。
両辺が同じ条件を共有している場合（たとえば `o.isActive() == o.isActive()`）、その書き方は拒否されます。

<a id="38-strings"></a>
## 3.8 文字列

- 文字列の `equals`、`Objects.equals`、`==` は **データベースの照合順序（collation）** を使います。
  - MariaDB の `_ci` 照合順序は、大文字と小文字の違いや末尾の空白を無視します。
  - Oracle は `''`（空文字列）を NULL として保存します。そのため、Oracle では `"".equals(o.getDescription())` が Java と違う結果になります。
- 2 つの文字列の `==` は、同一性の比較ではなく値の比較です（ルール R2）。[9.4](09-java-notes.md#94-strings) を参照してください。
- ラムダの中の文字列定数はリテラルとして書かれます。引用符はエスケープされます（`''`）。

**(テスト済み)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java)、リージョン `string-literal`:

```java
RenderedQuery q = qb.from(Order.class).where(o -> o.getDescription().equals("O'Brien")).toHql();
assertEquals("select e0 from Order e0 where e0.description = 'O''Brien'", q.hql());
```

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `string-literal`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.description='O''Brien'
```

- `startsWith`、`endsWith`、`contains`（LIKE）や、`trim()`、`toUpperCase()` のようなカラムに対する関数は **予定** で、最初のバージョンには入りません。
- 文字列の `+` は **予定** です。解析コアは、ラムダの中の `+` を、キャプチャした値だけをつなぐ場合も含めて、すでに拒否します。文字列はラムダの前で組み立ててください。

<a id="39-in-with-a-captured-collection"></a>
## 3.9 キャプチャしたコレクションでの IN

コレクションをラムダの **外** で作り、それに対して `contains` を呼びます。

```java
List<Long> ids = List.of(1L, 2L, 3L);
qb.from(Order.class).where(o -> ids.contains(o.getId())).toList();
// HQL: e0.id in (:p1)

qb.from(Order.class).where(o -> !ids.contains(o.getId())).toList();
// HQL: e0.id not in (:p1)
```

コレクションは 1 つのバインドパラメーターになります。
**(テスト済み)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java)、リージョン `in`（テストのエンティティでは status が `short` です）:

```java
List<Short> statuses = List.of((short) 2, (short) 3);
RenderedQuery q = qb.from(Order.class).where(o -> statuses.contains(o.getStatus())).toHql();
assertEquals("select e0 from Order e0 where e0.status in (:p1)", q.hql());
```

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `in`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status in (?,?)
```

`!contains` は `not in` になります。id は NULL にならないので、`is null` の部分はありません。NULL になりうるカラムでは、lambdaQL は `x is null or` を加えます。リストに `null` がなければ、Java の `!contains` は NULL の値に対して true になるからです。
**(テスト済み)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java)、リージョン `not-in`:

```java
List<Long> ids = List.of(1L, 2L);
// the id can never be NULL: a plain NOT IN
assertEquals("select e0 from Order e0 where e0.id not in (:p1)",
        qb.from(Order.class).where(o -> !ids.contains(o.getId())).toHql().hql());
// a column that can be NULL: Java keeps the rows with NULL (the list has no null), so "is null" is added
// (List.of(...).contains(null) would throw NullPointerException in Java; Arrays.asList returns false)
List<String> descs = Arrays.asList("a", "b");
assertEquals("select e0 from Order e0 where (e0.description is null or e0.description not in (:p1))",
        qb.from(Order.class).where(o -> !descs.contains(o.getDescription())).toHql().hql());
```

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `not-in`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.id not in (?,?)
```

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `not-in#2`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where (o1_0.description is null or o1_0.description not in (?,?))
```

| ケース | 結果 |
|---|---|
| レシーバーがキャプチャした `Collection`、`List`、`Set` | サポート |
| コレクションに `null` の要素がある | クエリの実行時に例外 |
| 空のコレクション | `contains` は FALSE、`!contains` は TRUE（Hibernate は `1=0` / `1=1` を書く） |
| 1,500 個の要素 | H2、MariaDB、Oracle 23ai で 1 つの文として動作（古い Oracle には 1,000 個の制限があります。テストはしていません） |
| レシーバーがエンティティのコレクション: `o.getItems().contains(item)` | **拒否**。`exists` サブクエリを使ってください（[5. サブクエリ](05-subquery.md)） |
| ラムダの中で作ったコレクション: `List.of(1, 2).contains(o.getStatus())` | **拒否**。外で作ってキャプチャしてください |

<a id="310-association-paths"></a>
## 3.10 関連のパス

`o.getCustomer().getName()` は **実行されません**。これは「結合した customer の `name` カラム」というカラムを指定するだけです。
lambdaQL は JPA Metamodel で各ステップをチェックします。

| パスのステップ | HQL | 例 |
|---|---|---|
| 対一の関連（`@ManyToOne`、`@OneToOne`） | エイリアス付きの `join`（デフォルトは内部結合、下記を参照） | `o.getCustomer().getName()` -> `join e0.customer j1` ... `j1.name` |
| 埋め込み（`@Embedded`） | 同じテーブル、結合なし | `Order` に埋め込みの `address` があれば: `o.getAddress().getCity()` -> `e0.address.city` |
| 対象の `@Id` で終わるパス | 外部キーのカラム、結合なし | `o.getCustomer().getId()` -> `e0.customer.id` |
| 関連そのものを `null` と比較 | 外部キーのカラム、結合なし | `o.getCustomer() == null` -> `e0.customer is null` |
| コレクション（`@OneToMany`、`@ManyToMany`） | **拒否** | `o.getItems()...` -> `exists` サブクエリを使う |

`where` の中では、関連のパスはデフォルトで **内部結合（inner join）** です（決定 D36）。
**(テスト済み)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java)、リージョン `inner-join`:

```java
String name = "Kim";
RenderedQuery q = qb.from(Order.class).where(o -> o.getCustomer().getName().equals(name)).toHql();
assertEquals("select e0 from Order e0 join e0.customer j1 where j1.name = :p1", q.hql());
```

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `inner-join`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name=?
```

- **customer のない** 注文は結果に入りません。結合する customer の行がないからです。
- lambdaQL は null チェックを代わりに追加しません。パスの前に `o.getCustomer() != null &&` を書く必要はありません。

**null ガードは customer のない注文を残します。** customer のない注文で条件が true になりうるなら、lambdaQL はそのパスに `left join` を使います。
内部結合では、Java と SQL で返る行が違ってしまいます。Java はその注文を返しますが、内部結合では失われるからです（決定 D36）。
**(テスト済み)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java)、リージョン `null-guard`:

```java
String name = "Kim";
// the SQL meaning (D48): a where path is an inner join, so "customer is null" never matches here
// (lambdaQL logs LQL-5009 at DEBUG); to keep orders without a customer, use leftJoin (D44)
RenderedQuery q = qb.from(Order.class)
        .where(o -> o.getCustomer() == null || o.getCustomer().getName().equals(name))
        .toHql();
assertEquals("select e0 from Order e0 join e0.customer j1 where e0.customer is null or j1.name = :p1", q.hql());

// the guard removes those orders, so an inner join is right
RenderedQuery q2 = qb.from(Order.class)
        .where(o -> o.getCustomer() != null && o.getCustomer().getName().equals(name))
        .toHql();
assertEquals("select e0 from Order e0 join e0.customer j1 where e0.customer is not null and j1.name = :p1", q2.hql());
```

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `null-guard`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.id is null or c1_0.name=?
```

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `null-guard#2`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.id is not null and c1_0.name=?
```

`left join` の後では、Hibernate は `e0.customer is null` を `c1_0.id is null` と書きます。意味は同じで、customer のない注文です。

- `||` の他の部分も注文を残すことがあります。`o.getStatus() == 1 || o.getCustomer().getName().equals(name)` も `left join` を使います。Java では、status が 1 の注文は customer がなくても条件に合うからです。
- 別の `where` 呼び出しがその注文をどのみち除く場合（たとえば `.where(o -> o.getCustomer().getName() != null)`）、結合は内部結合のままです。

customer の **ない** 注文を探すには、関連そのものを比較してください。これは外部キーのカラムをチェックし、結合はしません。
**(テスト済み)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java)、リージョン `no-association`:

```java
RenderedQuery q = qb.from(Order.class).where(o -> o.getCustomer() == null).toHql();
assertEquals("select e0 from Order e0 where e0.customer is null", q.hql());
```

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `no-association`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id is null
```

関連エンティティの `@Id` で終わるパスは、外部キーのカラムです。結合は不要です。
**(テスト済み)** [`SelectQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java)、リージョン `fk-path`:

```java
RenderedQuery q = qb.from(Order.class).where(o -> o.getCustomer().getId() == 1).toHql();
assertEquals("select e0 from Order e0 where e0.customer.id = 1", q.hql());
```

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `fk-path`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id=1
```

その他の事実:

- 同じパスには 1 つのエイリアスが付きます。それは `where`、`orderBy`、`select` で共有されます。
- `orderBy` か `select` **だけ** で使うパスは `left join` なので、そこでは行が失われません（ルール S8、J1）。同じパスを `where` でも使うと、`orderBy` と `select` は `where` の結合を使います。null ガードが `left join` にしていない限り、それは内部結合です。
- 2 つのエンティティの比較（`o.getCustomer() == c`）は **予定** です。今は id を比較してください: `o.getCustomer().getId() == c.getId()`（ここで `c` はキャプチャしたオブジェクトです）。

<a id="311-captured-values"></a>
## 3.11 キャプチャした値

ラムダは外の値を使えます。ローカル変数、フィールド、キャプチャしたオブジェクトに対するメソッド呼び出しです。

```java
LocalDateTime since = LocalDateTime.of(2026, 1, 1, 0, 0);
.where(o -> o.getUpdateAt().isAfter(since))              // ローカル変数
.where(o -> o.getStatus() == this.defaultStatus)          // this のフィールド
.where(o -> o.getPrice() > filter.getMinPrice())           // キャプチャしたオブジェクトのメソッド
.where(o -> o.getDescription().equals(form.getText().trim()))
```

ルール:

- エンティティを使わない部分は、**クエリを実行するたびに** Java 側で計算されます。その結果がバインドパラメーターになります。
- コンパイル時の定数（`static final int MAX = 5;`）はキャプチャした値ではありません。javac がラムダの中にコピーするので、`5` と同じくリテラルになります（[3.3](#33-basic-form) を参照）。
- ラムダは 1 回だけ解析され、結果はキャッシュされます。新しいキャプチャ値は呼び出しのたびに読まれます。
- SQL になるのはエンティティの部分だけです。`form.getText().trim()` は Java です。`o.getDescription().trim()` ならデータベースの関数になります（予定）。
- Java では、キャプチャするローカル変数は実質的に final でなければなりません。値が変わる場合は、ラムダの前で新しい変数に計算してください。
- フィールドを使うラムダは `this` をキャプチャします。[9.8](09-java-notes.md#98-lambdas-in-instance-methods-capture-this) を参照してください。

<a id="312-errors-that-depend-on-order-of-evaluation"></a>
## 3.12 評価の順番に依存するエラー

SQL は `AND`/`OR` を左から右へ評価することを約束しません。そのため、次のような「ガード」は SQL では安全ではありません。

```java
qb.from(Item.class)
  .where(i -> i.getQty() != 0 && i.getPrice() / i.getQty() > 2)   // 拒否される
```

ガードの下にある `/`、`%`、キャストは拒否されます。代わりに割り算を使わずに比較してください: `i.getQty() > 0 && i.getPrice() > 2 * i.getQty()`。

<a id="313-what-is-rejected"></a>
## 3.13 拒否されるもの

lambdaQL は、コード `[LQL-xxxx]` とソースの行を付けて例外を投げます。推測はしません。
ほとんどの形は、ラムダを解析するとき（最初に使うとき）に見つかります。問題のあるキャプチャ値（null、NaN）は、クエリの実行時に見つかります。
[11. エラーと言語](../11-errors-and-languages.md) を参照してください。

| 書き方 | 例 | どうするか |
|---|---|---|
| ブロックラムダの中のループ、`switch`、ローカル変数 | `o -> { int x = 1; return ...; }` | 1 つの式で書く |
| 値の三項演算子 | `o.getStatus() == (flag ? 1 : 2)` | ラムダの前で値を計算する |
| 一般的な boolean の三項演算子 | `c ? x : y` | [3.5](#35-and-or-not) の許される形を使う |
| 短絡評価しない演算子 | `a & b`、`a \| b`、`a ^ b` | `&&`、`\|\|` を使う |
| 文字列の `+` | `o.getDescription() == "A" + code` | ラムダの前で文字列を組み立てる（カラムに対しては予定） |
| ラムダの中のラムダやストリーム | `o -> list.stream().anyMatch(...)` | `contains`（[3.9](#39-in-with-a-captured-collection)）かサブクエリを使う |
| カラムに対する文字列関数 | `o.getDescription().startsWith("A")` | 予定 |
| エンティティのその他のメソッド | `o.calcSomething()` | getter だけを使う |
| エンティティのコレクションのパス | `o.getItems().size() > 0` | `exists` サブクエリ |
| エンティティとエンティティの比較 | `o.getCustomer() == c` | id を比較する（予定: エンティティの比較） |
| ガードの下の `/`、`%`、キャスト | [3.12](#312-errors-that-depend-on-order-of-evaluation) を参照 | ガードを取り除くか、条件を変える |
| R1/R4 の書き方での null のキャプチャ値 | `p == null` のときの `o.getDescription().equals(p)` | `Objects.equals` か null ガードを使う |
| `NaN` / `Infinity` のキャプチャ値 | `o.getPrice() > Double.NaN` | NaN を送らない |
| 匿名クラス、`Serializable` でないラムダ | `new Where<Order>() { ... }`、`java.util.function.Predicate` | ラムダを使う |

例外の型（すべて `LambdaQLException` を継承）:

| 例外 | いつ |
|---|---|
| `LambdaAnalysisException` | ラムダを読めない（シリアライズ可能なラムダではない、など） |
| `UnsupportedLambdaShapeException` | 形がサポートされていない（ループ、値の三項演算子、...） |
| `TranslationException` | 意味を変換できない（不明なメソッド、コレクションのパス、...） |

Java 固有の詳細は [9. Java の注意点](09-java-notes.md) にあります。すべてのルールを 1 か所にまとめたもの: [10. ルールと制限](10-rules-and-limits.md)。

---

[Java トラック](README.md) | 前: [2. はじめよう](02-getting-started.md) | 次: [4. ソートとページング](04-order-paging.md) | [このページの Kotlin 版](../kotlin/03-where.md)
