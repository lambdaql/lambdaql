<a id="9-java-notes"></a>
# 9. Java の注意点

> [!NOTE]
> **状況**
> - **実装済み（解析コア）:** シリアライズ可能なラムダの読み込み、`this` をキャプチャするラムダ、メソッド参照の検出、`Serializable` でないラムダの拒否。テストは [9.13](#913-what-you-can-check-today) を参照してください。
> - **実装済み:** キーが 1 つのソートでの getter のメソッド参照。たとえば `orderBy(Order::getPrice)`（[9.9](#99-method-references) を参照）。
> - **設計済み、まだ実装されていないもの:** このページのそれ以外のすべて（`==` の意味、ボクシングのルール、`String` のルール、クエリでのメソッド参照など）。

[Java トラック](README.md) | [マニュアルのホーム](../README.md) | 前: [8. プロシージャと DB 関数](08-procedures-functions.md) | 次: [10. ルールと制限](10-rules-and-limits.md) | [このページの Kotlin 版](../kotlin/09-kotlin-notes.md)

共通ページ: [1. 概要](../01-overview.md) · [11. エラーと言語](../11-errors-and-languages.md)

---

このページには、lambdaQL のラムダに書く **Java** のコードに特有の点をまとめています。
基本の考え方はいつも同じです。**lambdaQL のラムダはコードではなくクエリです。**
lambdaQL は書かれたものを読みます。実行はしません。そのため、Java の実行時のルールの一部は当てはまりません。

<a id="contents"></a>
## 目次

- [9.1 ラムダはシリアライズ可能なラムダでなければならない](#91-lambdas-must-be-serializable-lambdas)
- [9.2 ボクシングとアンボクシング](#92-boxing-and-unboxing)
- [9.3 ボクシングされた値の等価性](#93-equality-of-boxed-values)
- [9.4 文字列](#94-strings)
- [9.5 null-safe なヘルパー](#95-null-safe-helpers)
- [9.6 小さな型: char、short、byte、boolean](#96-small-types-char-short-byte-and-boolean)
- [9.7 float、double、BigDecimal](#97-float-double-and-bigdecimal)
- [9.8 インスタンスメソッド内のラムダは this をキャプチャする](#98-lambdas-in-instance-methods-capture-this)
- [9.9 メソッド参照](#99-method-references)
- [9.10 期待しないほうがよい static ヘルパー](#910-static-helpers-you-should-not-expect)
- [9.11 その他の Java の書き方](#911-other-java-forms)
- [9.12 コンパイラーとビルド](#912-compiler-and-build)
- [9.13 現在確認できること](#913-what-you-can-check-today)

---

<a id="91-lambdas-must-be-serializable-lambdas"></a>
## 9.1 ラムダはシリアライズ可能なラムダでなければならない

lambdaQL は、Java のシリアライズの仕組み（`SerializedLambda`）を通じてラムダの本体を見つけます。
`setAccessible` は使いません。キャプチャ値を実際にシリアライズすることもありません。

| 渡すもの | 結果 |
|---|---|
| 呼び出しの中に直接書いたラムダ: `where(o -> o.isActive())` | OK。`where` の引数の型は `Serializable` な関数型インターフェースです |
| メソッド参照: `where(Order::isActive)` | OK（[9.9](#99-method-references) を参照） |
| lambdaQL の引数の型の変数に入れたラムダ | OK |
| `java.util.function.Predicate`（`Serializable` ではない） | **拒否**（`LambdaAnalysisException`） |
| 匿名クラス: `new Where<Order>() { ... }` | **拒否** |

注意:

- キャプチャ値（およびキャプチャされた `this`）は `Serializable` で **ある必要はありません**。lambdaQL は値を読むだけで、シリアライズはしません。
- サブクエリの内側のラムダ（`sub.from(Item.class).where(i -> ...)`）は、それ自体が `Serializable` である必要はありません。lambdaQL は外側のラムダのバイトコードの中からそれを見つけます。
- lambdaQL の関数型インターフェースの名前はまだ確定していません。できるだけラムダを直接渡してください。

<a id="92-boxing-and-unboxing"></a>
## 9.2 ボクシングとアンボクシング

例のエンティティはボクシングされた型を使っています: `Long id`、`Double price`、`Integer status`。
ボクシングされた値をプリミティブと比較すると、javac は **アンボクシング** の呼び出し（`intValue()`、`doubleValue()`）を追加します。

```java
.where(o -> o.getStatus() == 1)     // javac: o.getStatus().intValue() == 1
// HQL: e0.status = 1
```

lambdaQL はボクシングとアンボクシングの呼び出し（`valueOf`、`intValue`、`doubleValue`、...）を **無視します**。意味だけを残します。

NULL についての意味:

| Java の書き方 | Java では（実行した場合） | lambdaQL では |
|---|---|---|
| `o.getStatus() == 1`、status が NULL | NullPointerException | SQL の意味: UNKNOWN、行は返らない（ルール R1） |
| `o.getPrice() > 100`、price が NULL | NullPointerException | 行は返らない（R1） |
| `!o.getActive()`（`Boolean`）、active が NULL | NullPointerException | 行は返らない |
| `o.getPrice() > p`、キャプチャした `Double p` が `null` | NullPointerException | クエリの実行時に **例外** |
| `i.getQty() == q`、`qty` は `int`、キャプチャした `Integer q` が `null` | NullPointerException | クエリの実行時に **例外** |

つまり、エンティティの値が NULL の場合は SQL に従います。アンボクシングされる位置でキャプチャ値が `null` の場合は、Java に結果がないのでエラーになります。

NULL の行が欲しい場合は、チェックを自分で書いてください。

```java
.where(o -> o.getStatus() == null || o.getStatus() != 1)
// HQL: e0.status is null or e0.status <> 1
```

<a id="93-equality-of-boxed-values"></a>
## 9.3 ボクシングされた値の等価性

Java では、2 つの **オブジェクト** の間の `==` は同一性（メモリ上で同じオブジェクトか）を調べます。
lambdaQL では、2 つのオブジェクトの間の `==` は **null-safe な値の比較** です（決定 D12）。
ボクシングのキャッシュ（-128 から 127 の `Integer` と `Long`）や `new String(...)` は関係ありません。

どのルールになるかは、両辺の **Java の型** で決まります。

| 左辺 | 右辺 | javac が作るもの | lambdaQL のルール | HQL |
|---|---|---|---|---|
| `Integer` のカラム | `int` の定数または変数 | アンボクシング、数値の比較 | R1、SQL の意味 | `e0.status = 1` |
| `Integer` のカラム | `Integer` の変数 | オブジェクトの比較 | R2、null-safe | `e0.status is not distinct from :p1` |
| `Long` の `@Id` カラム | `Long` の変数 | オブジェクトの比較 | R2、ただし片側は決して NULL にならない | `e0.id = :p1` |
| `Long` のカラム | `long` の変数 | アンボクシング | R1 | `e0.id = :p1` |
| `String` | `String` | オブジェクトの比較 | R2、null-safe | `e0.description is not distinct from :p1` |
| enum | enum | オブジェクトの比較 | R2、null-safe | `x is not distinct from :p1` |
| `Boolean` のカラム | `Boolean.TRUE` | オブジェクトの比較 | null-safe | `e0.active is not distinct from true` |
| `Boolean` のカラム | `true` | アンボクシング | R1 | `e0.active = true` |
| 何でも | `null` | null チェック | R3 | `x is null` |
| エンティティ | エンティティ（`o.getCustomer() == c`） | オブジェクトの比較 | **v0.1 にはない** | –（代わりに id を比較する） |

null-safe の意味:

| カラム | キャプチャ値 | 結果 |
|---|---|---|
| 3 | 3 | true |
| NULL | `null` | **true** |
| NULL | 3 | false |
| 3 | `null` | false |

```java
Integer wanted = request.getStatus();        // null の場合もある
qb.from(Order.class).where(o -> o.getStatus() == wanted).toList();
// wanted = 3    -> status が 3 の行
// wanted = null -> status が NULL の行
```

片側が決して NULL にならない場合（`@Id`、`nullable = false` のカラム、プリミティブのカラム、null でないキャプチャ値）、lambdaQL は普通の `=` を書きます。結果は同じで、データベースはインデックスを使えます。

オブジェクト間の `!=` はその逆です: `is distinct from`（片側が決して NULL にならない場合は `(a <> b or a is null)`）。

<a id="94-strings"></a>
## 9.4 文字列

| Java の書き方 | HQL | NULL のカラム | `p` が `null` |
|---|---|---|---|
| `o.getDescription() == p` | `e0.description is not distinct from :p1` | `p == null` のときだけ一致 | 可 |
| `o.getDescription().equals(p)` | `e0.description = :p1` | 行は返らない | **例外** |
| `p.equals(o.getDescription())` | `e0.description = :p1` | 行は返らない | **例外** |
| `"abc".equals(o.getDescription())`（定数のレシーバー） | `e0.description = 'abc'`（定数は決して NULL にならないので単純な `=`） | 行は返らない | – |
| `Objects.equals(o.getDescription(), p)` | `e0.description is not distinct from :p1` | `p == null` のときだけ一致 | 可 |
| `o.getDescription().compareTo(p) < 0` | `e0.description < :p1` | 行は返らない | **例外** |
| `o.getDescription() == null` | `e0.description is null` | 返る | – |

覚えておくこと:

- 普通の Java では、文字列に `==` を使うのはバグです。lambdaQL のラムダでは、正しい値の比較です。
- `equals` と `==` の動きが違うのは NULL のときだけです。欲しい NULL の動きで選んでください。
- 比較は Java ではなく **データベースの照合順序（collation）** に従います。MariaDB の `_ci` の照合順序は、大文字と小文字や末尾の空白を区別しません。Oracle は `''` を NULL として保存します。
- ラムダの中に書いた文字列定数は HQL のリテラルになり、引用符はエスケープされます（`'O''Brien'`）。キャプチャした文字列は常にバインドパラメーターです（[3.3](03-where.md#33-basic-form) を参照）。
- カラムに対する文字列メソッド（`startsWith`、`contains`、`trim`、`toUpperCase`）は予定で、v0.1 にはありません。
- 文字列の `+` は、キャプチャ値だけを使う場合でも v0.1 では拒否されます（javac は `+` を `invokedynamic` でコンパイルします）。文字列はラムダの前に組み立ててください。

```java
String code = "A" + suffix;                                  // 外側: 普通の Java
qb.from(Order.class).where(o -> o.getDescription() == code).toList();
```

<a id="95-null-safe-helpers"></a>
## 9.5 null-safe なヘルパー

次の Java のヘルパーは、`null` に対する結果が決まっています。lambdaQL はその結果を保ちます（ルール R5）。

| Java | HQL | 注意 |
|---|---|---|
| `Objects.equals(o.getStatus(), s)` | `e0.status is not distinct from :p1` | |
| `!Objects.equals(o.getStatus(), 1)` | `e0.status is distinct from 1` | Java と同じく、NULL の行も **返ります** |
| `Objects.equals(o.getDescription(), o.getCustomer().getName())` | `e0.description is not distinct from j1.name` | 2 つのカラム |
| `Boolean.TRUE.equals(o.getActive())` | `e0.active is not distinct from true` | |
| `!Boolean.TRUE.equals(o.getActive())` | `e0.active is distinct from true` | 3 つのデータベースすべてで動く |
| `"x".equals(o.getDescription())` | `e0.description = 'x'` | 定数のレシーバー: 決して NULL にならないので単純な `=` |
| `set` の値の中の `Objects.requireNonNullElse(o.getPrice(), 0.0)` | `coalesce(o.price, 0.0)` | [7. 更新、挿入、削除](07-dml.md) を参照 |

`!=` との比較:

```java
.where(o -> o.getStatus() != 1)                 // e0.status <> 1           （NULL の行は返らない）
.where(o -> !Objects.equals(o.getStatus(), 1))  // e0.status is distinct from 1 （NULL の行も返る）
```

<a id="96-small-types-char-short-byte-and-boolean"></a>
## 9.6 小さな型: char、short、byte、boolean

JVM には、`char`、`short`、`byte`、`boolean` 専用の比較命令がありません。javac はこれらを `int` としてコンパイルします。
lambdaQL はこのことを知っています。

- `long`、`int`、`short`、`byte`、`char`、`float`、`double` の値の比較をサポートします（ルール R1）。
- 定数はそのカラムの型に変換されます。たとえば、`short` のカラムに対しては、`int` の定数 `10` が `short` の値になります。
- `boolean` の getter だけで条件になります: `o.isActive()` は `e0.active = true` になります。
- `o.isActive() == (o.getStatus() > s)`（2 つの boolean の式）をサポートします。これは `AND`/`OR` になります（[3.7](03-where.md#37-boolean-columns) を参照）。
- プリミティブのプロパティ（`boolean active`、`int qty`）は、`@Id` や `nullable = false` のカラムと同じく「決して NULL にならない」ものとして扱います。そのため lambdaQL は `is not distinct from` の代わりに普通の `=` を書けます（[9.3](#93-equality-of-boxed-values)）。

<a id="97-float-double-and-bigdecimal"></a>
## 9.7 float、double、BigDecimal

| テーマ | ルール |
|---|---|
| `float` / `double` の定数（`100.0`、`1.5f`） | ラムダの中の他の定数と同じく、リテラルとして HQL に書かれる（`100.0`、`1.5F`）（D37）。ラムダの中で作った `BigDecimal`（`new BigDecimal("1.5")`）は計算した値なので、バインドパラメーターになる |
| ビット単位で同じ結果 | **保証しません**。データベースによって `float` と `double` の保存と比較のしかたが違います。lambdaQL は `CAST` を追加しません。カラムのマッピング（たとえば Oracle の `binary_float` / `binary_double`）を選んでください |
| キャプチャした `NaN`、`Infinity` | クエリの実行時に **例外** |
| データベースに保存された `NaN` | データベースに従う（H2 は最大の値としてソートします。MariaDB は保存できません） |
| `BigDecimal` の等価性 | Java の `equals` はスケールも比較します（`1.0` と `1.00` は等しくない）。lambdaQL は **値** を比較します |
| 整数の `/` | データベースによって結果が違います（MariaDB では `5/2 = 2.5`）。最終的なルールは決まっていません。使わないでください |

<a id="98-lambdas-in-instance-methods-capture-this"></a>
## 9.8 インスタンスメソッド内のラムダは this をキャプチャする

インスタンスメソッドの中のラムダがフィールドを使うと、javac はラムダの本体を **インスタンスメソッド** にして、`this` をキャプチャします。

```java
@Service
public class OrderService {

    private final QueryBuilder qb;
    private int defaultStatus = 1;

    public OrderService(QueryBuilder qb) {
        this.qb = qb;
    }

    public List<Order> defaults() {
        return qb.from(Order.class)
                 .where(o -> o.getStatus() == defaultStatus)   // this.defaultStatus の意味
                 .toList();
        // HQL: select e0 from Order e0 where e0.status = :p1
    }
}
```

**SQL ブロックの読み方。** `…` は省略した select リスト（エンティティのすべてのカラム）で、`?` はバインドパラメーターです。各行は [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java) から取っています。このテストは各クエリを Hibernate 6.6.5 で H2 2.3、MariaDB 10.11、Oracle 23.26 に対して実行し、Hibernate の SQL が変わると失敗します。

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `this-capture`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=?
```

lambdaQL がすること:

- 最初のキャプチャ値が `this` であることを知っています。（この部分は実装済みで、テストがあります。）
- `this.defaultStatus` はエンティティを使わないので、Java で計算されてパラメーターとしてバインドされます。
- フィールドは **クエリを実行するたびに** 読まれます。解析結果はオブジェクトごとではなく、ラムダごとにキャッシュされます。
- クラスが `Serializable` である必要はありません。
- エンティティを使わない `this` のメソッド呼び出し（`this.minPrice()`）も、クエリを実行するたびに Java で計算されます。副作用がないようにしてください。

ローカル変数は違います。Java はラムダが作られたときにその値をコピーします。Java では、その変数が実質的に final である必要があります。

```java
int st = defaultStatus;                                   // ここでコピーされる
qb.from(Order.class).where(o -> o.getStatus() == st).toList();
```

どちらの書き方でも、同じ HQL になります。

<a id="99-method-references"></a>
## 9.9 メソッド参照

| メソッド参照 | 同じ意味のコード | 状況 |
|---|---|---|
| `where(Order::isActive)` | `where(o -> o.isActive())` | 設計済み（検出は実装済み） |
| `orderBy(Order::getPrice)` | `orderBy(o -> o.getPrice())` | 実装済み、テスト済み |
| `by` を使うソートの書き方 `orderBy((o, by) -> ...)` でのメソッド参照 | – | **使えません**。ラムダを使ってください（ルール S13） |
| 結合のソートのラムダでのメソッド参照 | – | **使えません**。ラムダを使ってください（ルール S13） |

lambdaQL はメソッド参照を読み込むときに検出します。参照先のメソッドの本体は読みません。`o.isActive()` という呼び出しを自分で組み立てます。

コンパイルは通っても、意味が間違っているメソッド参照があります。lambdaQL は解析時にそれらを拒否します（ルール S13）。

- 引数 1 つのインスタンスメソッドを、引数 2 つのラムダとして使う場合（2 つ目の引数がメソッドの引数になってしまいます）
- 引数 2 つの static メソッドを、引数 2 つのラムダとして使う場合

`orderBy` で使えるのは、エンティティの getter だけです。static メソッド、引数のあるメソッド、オブジェクトに束縛された参照は拒否されます。

単純な getter 以外には、ラムダを使ってください。

<a id="910-static-helpers-you-should-not-expect"></a>
## 9.10 期待しないほうがよい static ヘルパー

lambdaQL には **static な DSL がありません**。`Agg` の任意のインポートを除き、lambdaQL のために `import static` を書く必要はありません。
ヘルパーは **ラムダの引数**（`by`、`sub`）として渡されるか、普通の Java で書きます。

| 期待しそうなもの | lambdaQL での書き方 |
|---|---|
| `orderBy(asc(...), desc(...))` | `orderBy((o, by) -> by.asc(...).desc(...))`（[4](04-order-paging.md)） |
| `exists(...)`、`in(...)`、`notIn(...)` | `(o, sub) -> sub.from(X.class).where(...).exists()` / `.select(...).contains(x)`（[5](05-subquery.md)） |
| `set(path, value)` | `set(o -> o.setX(v))` または `set((o, by) -> by.set(o.getX(), v))`（[7](07-dml.md)） |
| Q クラス、`Order_.price` | getter: `o.getPrice()` |
| `count()`、`sum(...)`、... | `Agg.count()`、`Agg.sum(...)`: **唯一の static ヘルパークラス**（[6](06-select-group.md)） |
| `coalesce(a, b)` | `set` の値の中で `Objects.requireNonNullElse(a, b)` |
| `like(...)`、`lower(...)`、`upper(...)` | 予定（カラムに対する文字列関数） |
| `currentTimestamp()` | `LocalDateTime.now()`。アプリケーションで計算されてバインドされます |
| エンティティの値に対する独自の static ユーティリティメソッド | **拒否**。ただし、そのメソッドが `@SqlFunction` の付いた DB 関数である場合を除きます（[8](08-procedures-functions.md)） |

**キャプチャ値だけ** を使う static メソッドは問題ありません。クエリを実行するたびに Java で実行されます。

```java
.where(o -> o.getUpdateAt().isAfter(LocalDateTime.of(2026, 1, 1, 0, 0)))   // OK: Java で計算される
.where(o -> MyUtil.normalize(o.getDescription()) == text)                   // 拒否: エンティティを使っている
```

> [!NOTE]
> リポジトリには、古いプロトタイプのコード（たとえば `JpqlFunction` クラス）がまだ残っています。
> これは新しい設計の一部ではありません。クエリでは使わないでください。

<a id="911-other-java-forms"></a>
## 9.11 その他の Java の書き方

| Java の書き方 | lambdaQL のラムダの中では |
|---|---|
| 1 つの式 `o -> ...` | 普通の書き方 |
| ローカル変数を使うブロックラムダ `o -> { ...; return ...; }` | `where`、`orderBy`、`select` では拒否 |
| setter の呼び出しだけのブロックラムダ | `set`、`values`、`insert ... select` では可（[7](07-dml.md)） |
| `if`、`for`、`while`、`switch`（文または式）、`try` | 拒否 |
| 三項演算子 `c ? x : y` | `c ? x : false`、`c ? true : x`、`c ? false : true` だけ（[3.5](03-where.md#35-and-or-not)） |
| boolean に対する `&`、`\|`、`^` | 拒否。`&&`、`\|\|` を使ってください |
| ストリーム、入れ子のラムダ（`sub` のサブクエリを除く） | 拒否 |
| ラムダの中の `List.of(...)` | `groupBy` のキーとして使う場合を除き、拒否 |
| `compareTo(...) < 0`、`isBefore`、`isAfter` | `<`、`>` になる |
| `captured.length`（キャプチャ値の配列） | Java で計算される |
| record | 結果の型（`select`）として、また派生テーブルと CTE の行の型として（`r.total()` で読む） |
| `var` | ビルダーの変数に使ってください。ビルダーの型名は確定していません |
| ジェネリックな `by.set(o.getStatus(), "abc")` | Java ではコンパイルが通ります。lambdaQL は解析時に型をチェックします |
| 入れ子のラムダで同じ引数名 | Java では許されません。`sub2`、`i`、`x` を使ってください |

<a id="912-compiler-and-build"></a>
## 9.12 コンパイラーとビルド

| テーマ | 知っておくこと |
|---|---|
| コンパイラー | lambdaQL は **javac** でテストしています。**ECJ**（Eclipse のコンパイラー）でコンパイルしたラムダは保証しません。Eclipse IDE や、Eclipse JDT をベースにした他のツールは ECJ を使います。テストは Gradle か Maven（javac）で実行してください |
| JDK のバージョン | Java 21 |
| ASM | lambdaQL は ASM 9.11.1 でクラスファイルを読みます（Java 28 のクラスファイルまで）。より新しい JDK でコンパイルする場合は、ASM を更新してください。lambdaQL は ASM をシェーディングしていないので、自分で更新できます |
| `-parameters` | 推奨します。lambdaQL がプロシージャの引数名をチェックできるようになります（[8.4](08-procedures-functions.md#84-warning-argument-order)） |
| デバッグ情報 | 行番号を残してください（javac のデフォルト）。lambdaQL はソースの行番号をエラーメッセージに入れます |
| IntelliJ の「Number objects compared using `==`」 | lambdaQL のラムダでは意図どおりです。`@SuppressWarnings("NumberEquality")` を使えます |
| 文字列の `==` に対する IntelliJ の警告 | これも lambdaQL のラムダでは意図どおりです |

<a id="913-what-you-can-check-today"></a>
## 9.13 現在確認できること

解析コアは実装済みです。次のテストの多くはユーザー API ではなく内部クラスを使っていますが、このページで説明した Java の動きを示しています。

| テスト | 示していること |
|---|---|
| [`LoadExamplesTest.java`](../../../../query/src/test/java/org/lambdaql/engine/load/LoadExamplesTest.java) `instanceLambda` | フィールドを使うラムダは、最初のキャプチャ値として `this` をキャプチャする（[9.8](#98-lambdas-in-instance-methods-capture-this)） |
| [`LoadExamplesTest.java`](../../../../query/src/test/java/org/lambdaql/engine/load/LoadExamplesTest.java) `methodReference` | `Order::isActive` はメソッド参照として検出される（[9.9](#99-method-references)） |
| [`LoadExamplesTest.java`](../../../../query/src/test/java/org/lambdaql/engine/load/LoadExamplesTest.java) `notSerializable` | `java.util.function.Predicate` は拒否される（[9.1](#91-lambdas-must-be-serializable-lambdas)） |
| [`ConditionDecodeTest.java`](../../../../query/src/test/java/org/lambdaql/engine/decode/ConditionDecodeTest.java) | 比較でのアンボクシング（`o.getId() > minId`）、`&&`、`\|\|`、`!` |
| [`SortQueryTest.java`](../../../../query/src/test/java/org/lambdaql/query/SortQueryTest.java) リージョン `sort-key` | `Order::getPrice` は、`o -> o.getPrice()` と同じく、キーが 1 つのソートとして動く（[9.9](#99-method-references)） |
| [`HostEvaluatorTest.java`](../../../../query/src/test/java/org/lambdaql/engine/sem/HostEvaluatorTest.java) `convertNumbers` | 数値はカラムの型に変換される（たとえば `int` から `short`）（[9.6](#96-small-types-char-short-byte-and-boolean)） |

---

[Java トラック](README.md) | 前: [8. プロシージャと DB 関数](08-procedures-functions.md) | 次: [10. ルールと制限](10-rules-and-limits.md) | [このページの Kotlin 版](../kotlin/09-kotlin-notes.md)
