<a id="9-java-notes"></a>
# 9. Java の注意点

[Java トラック](README.md) | [マニュアルのホーム](../README.md) | 前: [8. プロシージャと DB 関数](08-procedures-functions.md) | 次: [10. ルールと制限](10-rules-and-limits.md) | [このページの Kotlin 版](../kotlin/09-kotlin-notes.md)

共通ページ: [1. 概要](../01-overview.md) · [11. エラーと言語](../11-errors-and-languages.md)

---

このページは、lambdaQL のラムダ式の中の **Java** コードに特有の点をまとめたものです。
要点はいつも同じです: **lambdaQL のラムダ式はコードではなく、クエリの式です。**
lambdaQL はラムダ式を呼び出さずに、バイトコードの opcode を解析します。そのため、Java の実行時のルールの一部は適用されません。

<a id="contents"></a>
## 目次

- [9.1 ラムダ式はシリアライズ可能なラムダ式でなければならない](#91-lambdas-must-be-serializable-lambdas)
- [9.2 ボクシングとアンボクシング](#92-boxing-and-unboxing)
- [9.3 ボクシングされた値の等価性](#93-equality-of-boxed-values)
- [9.4 文字列](#94-strings)
- [9.5 null-safe なヘルパー](#95-null-safe-helpers)
- [9.6 小さな型: char、short、byte、boolean](#96-small-types-char-short-byte-and-boolean)
- [9.7 float、double、BigDecimal](#97-float-double-and-bigdecimal)
- [9.8 インスタンスメソッド内のラムダ式は this をキャプチャする](#98-lambdas-in-instance-methods-capture-this)
- [9.9 メソッド参照](#99-method-references)
- [9.10 期待しないほうがよい static ヘルパー](#910-static-helpers-you-should-not-expect)
- [9.11 その他の Java の書き方](#911-other-java-forms)
- [9.12 コンパイラーとビルド](#912-compiler-and-build)

---

<a id="91-lambdas-must-be-serializable-lambdas"></a>
## 9.1 ラムダ式はシリアライズ可能なラムダ式でなければならない

lambdaQL は Java のシリアライズ（`SerializedLambda`）を通してラムダ式の本体を見つけます。
`setAccessible` は使いません。キャプチャ値を実際にシリアライズすることもありません。

| 渡すもの | 結果 |
|---|---|
| 呼び出しの中に直接書いたラムダ式: `where(o -> o.isActive())` | OK。`where` の引数の型は `Serializable` な関数型インターフェース |
| 条件のメソッド参照: `where(Order::isActive)` | **拒否**（`LQL-1009`）。ラムダ式を使ってください（[9.9](#99-method-references) を参照） |
| lambdaQL の引数の型の変数に入れたラムダ式 | OK |
| `java.util.function.Predicate`（`Serializable` ではない） | **拒否**（`LambdaAnalysisException`） |
| 匿名クラス: `new Where<Order>() { ... }` | **拒否** |

注意:

- キャプチャ値（とキャプチャした `this`）は `Serializable` である必要は **ありません**。lambdaQL はその値を読むだけで、シリアライズはしません。
- サブクエリの内側のラムダ式（`sub.from(Item.class).where(i -> ...)`）は、それ自体が `Serializable` である必要はありません。lambdaQL は外側のラムダ式のバイトコードの opcode からそれを見つけます。

<a id="92-boxing-and-unboxing"></a>
## 9.2 ボクシングとアンボクシング

例のエンティティ `Order` には、ボクシング型のプロパティ（`Long id`、`Integer cnt`、`Double total`）とプリミティブ型のプロパティ（`double price`、`short status`）が混在しています。
ボクシングされた値をプリミティブ型と比較すると、javac は **アンボクシング** の呼び出し（`intValue()`、`doubleValue()`）を追加します。

<!-- region: ManualJavaNotesTest.java#unboxing -->
```java
// cnt is an Integer column: javac writes o.getCnt().intValue() == 1; lambdaQL keeps only the meaning
SelectQuery<Order> q = qb.from(Order.class).where(o -> o.getCnt() == 1);
assertEquals("select e0 from Order e0 where e0.cnt = 1", q.toHql().hql());
// a NULL cnt: no NullPointerException, the row is simply not returned (the SQL meaning)
```

lambdaQL はボクシングとアンボクシングの呼び出し（`valueOf`、`intValue`、`doubleValue`、...）を **無視します**。意味だけを保ちます。

NULL に関してこれが意味すること:

| Java の書き方 | Java では（実行した場合） | lambdaQL では |
|---|---|---|
| `o.getCnt() == 1`、cnt が NULL | NullPointerException | SQL の意味: UNKNOWN、行は返らない |
| `o.getTotal() > 100`、total が NULL | NullPointerException | 行は返らない |
| `!c.getVip()`（`Boolean`、例のエンティティ `Coded`）、vip が NULL | NullPointerException | 行は返らない（[3.6.3](03-where.md#363-write-your-null-checks-yourself) を参照） |
| `o.getPrice() > p`、キャプチャした `Double p` が `null` | NullPointerException | `e0.price > :p1` に NULL をバインド、一致する行はない（`DEBUG` レベルの `LQL-4045`） |
| `i.getQty() == q`、`qty` は `int`、キャプチャした `Integer q` が `null` | NullPointerException | `i.qty = :p1` に NULL をバインド、一致する行はない（`DEBUG` レベルの `LQL-4045`） |
| `o.getStatus() > p.intValue()`、キャプチャした `Double p` が `null` | NullPointerException | クエリの実行時に **例外**（`LQL-4012`） |

つまり、NULL のエンティティ値は SQL に従います。キャプチャ値を自分のプリミティブ型にアンボクシングする呼び出し（`Double.doubleValue()`、`Integer.intValue()` など。その後の拡大変換も含む）は、javac の自動アンボクシングと同じバイトコードの opcode なので、実行せずにボクシングされた値をバインドします。
別のプリミティブ型に変える呼び出し（`Double` の `intValue()`）は手でしか書けない計算なので Java で実行し、`null` なら Java と同じくエラーになります。

NULL の行がほしい場合は、確認を自分で書いてください。

<!-- region: ManualJavaWhereTest.java#null-check -->
```java
// only rows where cnt is not NULL and not 1
SelectQuery<Order> notOne = qb.from(Order.class).where(o -> o.getCnt() != 1);
assertEquals("select e0 from Order e0 where e0.cnt <> 1", notOne.toHql().hql());
// rows where cnt is NULL too
SelectQuery<Order> notOneOrNull = qb.from(Order.class).where(o -> o.getCnt() == null || o.getCnt() != 1);
assertEquals("select e0 from Order e0 where e0.cnt is null or e0.cnt <> 1", notOneOrNull.toHql().hql());
```

コード例: [`ManualJavaNotesTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaNotesTest.java)（`unboxing`）、[`ManualJavaWhereTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaWhereTest.java)（`null-check`）。

<a id="93-equality-of-boxed-values"></a>
## 9.3 ボクシングされた値の等価性

Java では、2 つの **オブジェクト** の間の `==` は同一性（メモリ上の同じオブジェクトかどうか）を確認します。
lambdaQL では、2 つのオブジェクトの間の `==` は **値の比較** です。
エンティティ値もキャプチャ値も SQL の意味に従います（[3.6](03-where.md#36-null-rules) を参照）。
ボクシングのキャッシュ（-128 から 127 までの `Integer` と `Long`）と `new String(...)` は関係ありません。

javac が作る比較は、両辺の **Java の型** によって変わります。

| 左辺 | 右辺 | javac が作るもの | HQL |
|---|---|---|---|
| `Integer` のカラム | `int` の定数または変数 | アンボクシング、数値の比較 | `e0.cnt = 1` |
| `Integer` のカラム | `Integer` の変数 | オブジェクトの比較 | `e0.cnt = :p1` |
| `Long` の `@Id` カラム | `Long` の変数 | オブジェクトの比較 | `e0.id = :p1` |
| `Long` のカラム | `long` の変数 | アンボクシング | `e0.id = :p1` |
| `String` | `String` | オブジェクトの比較 | `e0.description = :p1` |
| `String` のカラム | `String` のカラム | オブジェクトの比較 | `e0.description = e0.custName`（どちらかが NULL なら一致しない） |
| enum | enum | オブジェクトの比較 | `x = :p1` |
| `Boolean` のカラム | `Boolean.TRUE` | オブジェクトの比較 | `e0.active = :p1` |
| `Boolean` のカラム | `true` | アンボクシング | `e0.active = true` |
| 値オブジェクト（`@Embedded`） | 同じ型のキャプチャした値オブジェクト | `equals` / オブジェクトの比較 | `e0.address.city = :p1 and e0.address.zip = :p2`（[3.10](03-where.md#310-association-paths) を参照） |
| 何でも | `null` | null チェック | `x is null` |
| エンティティ | エンティティ（`o.getCustomer() == c`） | オブジェクトの比較 | –（代わりに id を比較） |

キャプチャ値と比較するとき:

| カラム | キャプチャ値 | HQL | 結果 |
|---|---|---|---|
| 3 | 3 | `e0.cnt = :p1` | true |
| NULL | `null` | `e0.cnt = :p1` | **false**: `= NULL` は真にならない（`WARN` レベルの `LQL-4045`） |
| NULL | 3 | `e0.cnt = :p1` | false |
| 3 | `null` | `e0.cnt = :p1` | false |

<!-- region: ManualJavaWhereTest.java#boxed-equals -->
```java
// cnt is an Integer column; Integer == Integer compares values, not objects
Integer wanted = 3;
SelectQuery<Order> three = qb.from(Order.class).where(o -> o.getCnt() == wanted);
assertEquals("select e0 from Order e0 where e0.cnt = :p1", three.toHql().hql());
// a captured null is bound as NULL: "= NULL" matches no row (WARN LQL-4045)
Integer none = null;
SelectQuery<Order> noRow = qb.from(Order.class).where(o -> o.getCnt() == none);
assertEquals("select e0 from Order e0 where e0.cnt = :p1", noRow.toHql().hql());
// to find NULL, write the null check
SelectQuery<Order> nullCnt = qb.from(Order.class).where(o -> o.getCnt() == null);
assertEquals("select e0 from Order e0 where e0.cnt is null", nullCnt.toHql().hql());
```

カラムを関数で包まないので、データベースはインデックスを使えます。

オブジェクトの間の `!=` は `<>` になります。値が NULL の行は `<>` に一致しません（SQL の意味）。Java ならこの行を返します。
このような行を残すには、`o.getCnt() == null || o.getCnt() != wanted` と書いてください。

2 つのエンティティ値（`o.getDescription() == o.getCustName()`）は `=` と `<>` になります。どちらかが NULL なら一致せず、NULL と NULL も同じです。
結果が Java と異なる可能性がある場合、lambdaQL は `LQL-5009` を `DEBUG` レベルで記録します（[3.6.3](03-where.md#363-write-your-null-checks-yourself) を参照）。

コード例: [`ManualJavaWhereTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaWhereTest.java)（`boxed-equals`）。

<a id="94-strings"></a>
## 9.4 文字列

| Java の書き方 | HQL | NULL のカラム | `p` が `null` |
|---|---|---|---|
| `o.getDescription() == p` | `e0.description = :p1` | 行は返らない | 実行される、一致する行はない（`LQL-4045`） |
| `o.getDescription().equals(p)` | `e0.description = :p1` | 行は返らない | 実行される、一致する行はない（`LQL-4045`） |
| `p.equals(o.getDescription())` | `e0.description = :p1` | 行は返らない | 実行される、一致する行はない（`LQL-4045`） |
| `"abc".equals(o.getDescription())`（定数のレシーバー） | `e0.description = 'abc'`（定数は NULL にならないので単純な `=`） | 行は返らない | – |
| `Objects.equals(o.getDescription(), p)` | `e0.description = :p1` | 行は返らない | 実行される、一致する行はない（`LQL-4045`） |
| `o.getDescription().compareTo(p) < 0` | `e0.description < :p1` | 行は返らない | 実行される、一致する行はない（`LQL-4045`） |
| `o.getDescription() == null` | `e0.description is null` | 返る | – |

覚えておくこと:

- 通常の Java では、文字列に対する `==` はバグです。lambdaQL のラムダ式の中では正しい値の比較です。
- `==`、`equals`、`Objects.equals` は NULL に対して同じように動作します。カラムが NULL か `p` が `null` なら、一致する行はありません。NULL の行がほしい場合は `o.getDescription() == null` を自分で書いてください。
- 比較は Java ではなく **データベースの照合順序（コレーション）** に従います。MariaDB の `_ci` コレーションは大文字と小文字、末尾の空白を無視します。Oracle は `''` を NULL として保存します。
- ラムダ式に直接書いた文字列定数は HQL のリテラルになり、単一引用符はエスケープされます（`'O''Brien'`）。キャプチャした文字列は常にバインドパラメーターです（[3.3](03-where.md#33-basic-form) を参照）。
- カラムに対する `startsWith`、`endsWith`、`contains` は `like` になります（[3.8](03-where.md#38-strings)）。`trim`、`toUpperCase` などの他の文字列メソッドはサポートしません（`LQL-3008`）。
- ラムダ式の中の文字列の `+` は拒否されます（`LQL-2007`）。キャプチャ値だけでも同じです（javac は `+` を `invokedynamic` にコンパイルします）。文字列はラムダ式の前に作ってください。

<!-- region: ManualJavaNotesTest.java#string-outside -->
```java
String code = "A" + suffix;                                  // outside: plain Java
SelectQuery<Order> q = qb.from(Order.class).where(o -> o.getDescription() == code);
assertEquals("select e0 from Order e0 where e0.description = :p1", q.toHql().hql());
```

コード例: [`ManualJavaNotesTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaNotesTest.java)（`string-outside`）。

<a id="95-null-safe-helpers"></a>
## 9.5 null-safe なヘルパー

次の Java のヘルパーは `==` と同じルール、つまり SQL の意味に従います。キャプチャ値の `null` はどの行にも一致しません。

| Java | HQL | 注意 |
|---|---|---|
| `Objects.equals(o.getCnt(), s)` | `e0.cnt = :p1` | `s` が `null` なら一致する行はない（`LQL-4045`） |
| `!Objects.equals(o.getCnt(), 1)` | `e0.cnt <> 1` | NULL の行は **返らない**（SQL の意味。Java なら返る） |
| `Objects.equals(o.getDescription(), o.getCustomer().getName())` | `e0.description = j1.name` + `join e0.customer j1` | 2 つのカラム: どちらかが NULL なら一致しない |
| `Boolean.TRUE.equals(c.getVip())` | `e0.vip = :p1` | |
| `!Boolean.TRUE.equals(c.getVip())` | `e0.vip <> :p1` | NULL の行は **返らない**（SQL の意味） |
| `"x".equals(o.getDescription())` | `e0.description = 'x'` | 定数のレシーバー: NULL にならないので単純な `=` |
| `set` の値の中の `Objects.requireNonNullElse(o.getTotal(), 0.0)` | `coalesce(e0.total, 0.0)` | [7. 更新、挿入、削除](07-dml.md) を参照 |

`!=` との比較:

<!-- region: ManualJavaNotesTest.java#not-equals -->
```java
SelectQuery<Order> notOne = qb.from(Order.class).where(o -> o.getCnt() != 1);
assertEquals("select e0 from Order e0 where e0.cnt <> 1", notOne.toHql().hql());              // no NULL rows
SelectQuery<Order> notEquals = qb.from(Order.class).where(o -> !Objects.equals(o.getCnt(), 1));
assertEquals("select e0 from Order e0 where e0.cnt <> 1", notEquals.toHql().hql());           // no NULL rows either
SelectQuery<Order> withNull = qb.from(Order.class)
        .where(o -> o.getCnt() == null || !Objects.equals(o.getCnt(), 1));
assertEquals("select e0 from Order e0 where e0.cnt is null or e0.cnt <> 1", withNull.toHql().hql());  // NULL rows too
```

コード例: [`ManualJavaNotesTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaNotesTest.java)（`not-equals`）。

<a id="96-small-types-char-short-byte-and-boolean"></a>
## 9.6 小さな型: char、short、byte、boolean

JVM には `char`、`short`、`byte`、`boolean` 専用の比較命令がありません。javac はこれらを `int` としてコンパイルします。
lambdaQL はこれを知っています。

- `long`、`int`、`short`、`byte`、`char`、`float`、`double` の値の比較をサポートします。
- 定数はカラムの型に変換されます。たとえば `int` の定数 `10` は、`short` のカラムに対しては `short` の値になります。
- `boolean` の getter は単独で条件になります: `o.isActive()` は `e0.active = true` になります。
- boolean のカラムと条件の比較 `o.isActive() == (o.getStatus() > s)` では、条件の側が `case when` になります。インデックスが必要なら `&&` と `||` に分けて書いてください（[3.7](03-where.md#37-boolean-columns) を参照）。
- プリミティブ型のプロパティ（`boolean active`、`int qty`）は、`@Id` や `nullable = false` のカラムと同じく「NULL にならない」ものとして扱います。そのため lambdaQL はこのプロパティに対する null チェックを省略でき、`o.getId() == null` は `1=0` になります（[3.6.2](03-where.md#362--on-objects-compares-values)）。

<a id="97-float-double-and-bigdecimal"></a>
## 9.7 float、double、BigDecimal

| テーマ | ルール |
|---|---|
| `float` / `double` の定数（`100.0`、`1.5f`） | ラムダ式の中の他の定数と同じく、HQL にリテラルとして書く（`100.0`、`1.5F`）。ラムダ式の中で作った `BigDecimal`（`new BigDecimal("1.5")`）は計算した値なので、バインドパラメーター |
| ビット単位で同じ結果 | **保証しない**。データベースによって `float` と `double` の保存と比較の方式が異なる。lambdaQL は `CAST` を追加しない。カラムのマッピングを自分で選んでください（たとえば Oracle の `binary_float` / `binary_double`） |
| キャプチャした `NaN`、`Infinity` | クエリの実行時に **例外** |
| データベースに保存された `NaN` | データベースに従う（H2 は最大の値としてソート、MariaDB は保存できない） |
| `BigDecimal` の等価性 | Java の `equals` はスケールを比較する（`1.0` は `1.00` ではない）。lambdaQL は **値** を比較する |
| 整数の `/` | データベースによって結果が異なる（MariaDB では `5/2 = 2.5`）。最終的なルールは決まっていない。使わないでください |

<a id="98-lambdas-in-instance-methods-capture-this"></a>
## 9.8 インスタンスメソッド内のラムダ式は this をキャプチャする

インスタンスメソッドの中のラムダ式がフィールドを使うと、javac はラムダ式の本体を **インスタンスメソッド** にし、`this` をキャプチャします。

<!-- region: ManualJavaNotesTest.java#this-capture -->
```java
@Service
public static class OrderService {

    private final QueryBuilder qb;
    private int defaultStatus = 1;

    public OrderService(QueryBuilder qb) {
        this.qb = qb;
    }

    public List<Order> defaults() {
        return qb.from(Order.class)
                 .where(o -> o.getStatus() == defaultStatus)   // means this.defaultStatus
                 .toList();
    }
}
```

**データベースごとの SQL**

<!-- sql: this-capture -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=?
```

lambdaQL が行うこと:

- 最初のキャプチャ値が `this` であることを知っています。
- `this.defaultStatus` はエンティティを使わないので、Java で計算されてパラメーターとしてバインドされます。
- フィールドは **クエリを実行するたびに** 読みます。解析結果はオブジェクトごとではなく、ラムダ式ごとにキャッシュされます。
- クラスが `Serializable` である必要はありません。
- エンティティを使わない `this` のメソッド呼び出し（`this.minPrice()`）も、クエリを実行するたびに Java で計算されます。副作用がないようにしてください。

ローカル変数は異なります。Java はラムダ式が作られるときにその値をコピーします。Java はローカル変数が実質的に final であることを要求します。

<!-- region: ManualJavaNotesTest.java#local-copy -->
```java
int st = defaultStatus;                                   // copied now
SelectQuery<Order> q = qb.from(Order.class).where(o -> o.getStatus() == st);
// the same HQL as the lambda that reads the field
assertEquals("select e0 from Order e0 where e0.status = :p1", q.toHql().hql());
assertEquals(q.toHql().hql(), qb.from(Order.class).where(o -> o.getStatus() == defaultStatus).toHql().hql());
```

どちらの書き方も同じ HQL を作ります。

コード例: [`ManualJavaNotesTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaNotesTest.java)（`this-capture`、`local-copy`）。

<a id="99-method-references"></a>
## 9.9 メソッド参照

| メソッド参照 | 同じ意味 | 状況 |
|---|---|---|
| `where(Order::isActive)` | `where(o -> o.isActive())` | **拒否**（`LQL-1009`）。ラムダ式を使ってください |
| `orderBy(Order::getPrice)` | `orderBy(o -> o.getPrice())` | 実装済み |
| `orderBy(Order::getPrice, Direction.DESC)` | `orderBy(o -> o.getPrice(), Direction.DESC)` | 実装済み |
| `SortSpec.of(Order.class, Order::getPrice)` | `SortSpec.of(Order.class, o -> o.getPrice())` | 実装済み。キーを 1 つ使うソートの書き方ならどこでも使える |

lambdaQL はメソッド参照を読み込むときにそれを検出します。参照先のメソッドの本体は読みません。ソートキーでは `o.getPrice()` の呼び出しを自分で作り、条件では拒否します。

<!-- region: ManualJavaNotesTest.java#method-ref -->
```java
// a method reference as a sort key: the same as o -> o.getPrice()
assertEquals("select e0 from Order e0 order by e0.price asc",
        qb.from(Order.class).orderBy(Order::getPrice).toHql().hql());
// a method reference as a condition is rejected: write a lambda
assertEquals("LQL-1009", assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(Order::isActive)).code());
```

コンパイルはできても意味が正しくないメソッド参照があります。lambdaQL はこれを解析時に拒否します。

- 引数が 1 つのインスタンスメソッドを、引数が 2 つのラムダ式として使った場合（2 番目の引数がメソッドの引数になる）、
- 引数が 2 つの static メソッドを、引数が 2 つのラムダ式として使った場合。

`orderBy` ではエンティティの getter だけを許可します。static メソッド、引数のあるメソッド、オブジェクトに束縛された参照は拒否されます。

単純な getter でなければ、ラムダ式を使ってください。

コード例: [`ManualJavaNotesTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaNotesTest.java)（`method-ref`）。

<a id="910-static-helpers-you-should-not-expect"></a>
## 9.10 期待しないほうがよい static ヘルパー

lambdaQL の static ヘルパークラス `By`（ソート関数）、`Agg`（集計）、`Dml`（値の代入）は、ラムダ式の中でだけ使います。
サブクエリのファクトリーは **ラムダ式の引数**（`sub`）として渡されます。それ以外は通常の Java で書きます。

| 期待しそうなもの | lambdaQL での書き方 |
|---|---|
| `orderBy(asc(...), desc(...))` | `orderBy(o -> asc(...).desc(...))`: 複数のキーを 1 つのラムダ式の中でつなげる（[4](04-order-paging.md)） |
| `exists(...)`、`in(...)`、`notIn(...)` | `(o, sub) -> sub.from(X.class).where(...).exists()` / `.select(...).contains(x)`（[5](05-subquery.md)） |
| `set(path, value)` | `set(o -> o.setX(v))` または `set(o -> value(o.getX(), v))`（`Dml.value`）（[7](07-dml.md)） |
| Q クラス、`Order_.price` | getter: `o.getPrice()` |
| `count()`、`sum(...)`、... | `Agg.count()`、`Agg.sum(...)`（[6](06-select-group.md)） |
| `coalesce(a, b)` | `set` の値の中の `Objects.requireNonNullElse(a, b)` |
| `like(...)`、`lower(...)`、`upper(...)` | サポートしない（カラムに対する文字列関数） |
| `currentTimestamp()` | `LocalDateTime.now()`。アプリケーションで計算されてバインドされる |
| エンティティ値に対する自作の static ユーティリティメソッド | **拒否**。ただし `@SqlFunction` を付けた DB 関数は例外（[8](08-procedures-functions.md)） |

**キャプチャ値だけ** を使う static メソッドは問題ありません。クエリを実行するたびに Java で実行されます。

<!-- region: ManualJavaNotesTest.java#static-helper -->
```java
// a static method on captured values only: computed in Java when the query runs
SelectQuery<Order> q = qb.from(Order.class)
        .where(o -> o.getUpdateAt().compareTo(LocalDateTime.of(2026, 1, 1, 0, 0)) > 0);
assertEquals("select e0 from Order e0 where e0.updateAt > :p1", q.toHql().hql());
// your own static method on an entity value: rejected
assertEquals("LQL-3008", assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(o -> normalize(o.getDescription()) == text)).code());
```

コード例: [`ManualJavaNotesTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaNotesTest.java)（`static-helper`）。

<a id="911-other-java-forms"></a>
## 9.11 その他の Java の書き方

| Java の書き方 | lambdaQL のラムダ式の中では |
|---|---|
| 式 1 つ `o -> ...` | 通常の書き方 |
| ローカル変数のあるブロックラムダ式 `o -> { ...; return ...; }` | `where`、`orderBy`、`select` では拒否 |
| setter 呼び出しだけのブロックラムダ式 | `set`、`values`、`insert ... select` で許可（[7](07-dml.md)） |
| `if`、`for`、`while`、`switch`（文または式）、`try` | 拒否 |
| 三項演算子 `c ? x : y` | `c ? x : false`、`c ? true : x`、`c ? false : true` だけ（[3.5](03-where.md#35-and-or-not)） |
| boolean に対する `&`、`\|`、`^` | 拒否。`&&`、`\|\|` を使ってください |
| ストリーム、ネストしたラムダ式（`sub` のサブクエリを除く） | 拒否 |
| ラムダ式の中の `List.of(...)` | 拒否。`groupBy` のキーとして使う場合は例外 |
| `compareTo(...) < 0` | `<`、`>` になる。`LocalDateTime` の `isBefore`、`isAfter` は拒否（`LQL-3008`） |
| `captured.length`（キャプチャ値の配列） | Java で計算される |
| レコード | 結果の型（`select`）として、また派生テーブルと CTE の行の型として（`r.total()` で読む） |
| `var` | ビルダーの変数に使える。型は `SelectQuery<T>`、`OrderedSelectQuery<T>`、`ValueQuery<R>` など |
| ジェネリックな `value(o.getStatus(), "abc")` | Java ではコンパイルできる。解析時にカラムの型と照合して `LQL-3065` エラー |
| 独自の `value(...)` メソッドを持つクラス（たとえば `value` コンポーネントを持つレコード）の中の `value(...)` | 静的インポートが隠される。`Dml.value(...)` と書く |
| ネストしたラムダ式での同じ引数名 | Java が許可しない。`sub2`、`i`、`x` を使ってください |

<a id="912-compiler-and-build"></a>
## 9.12 コンパイラーとビルド

| テーマ | 知っておくこと |
|---|---|
| コンパイラー | lambdaQL は **javac** の出力を前提に動作します。**ECJ**（Eclipse のコンパイラー）でコンパイルしたラムダ式は保証しません。Eclipse IDE と、Eclipse JDT をベースにした他のツールは ECJ を使います。テストは Gradle か Maven（javac）で実行してください |
| JDK のバージョン | Java 21 以上 |
| クラスファイルの読み取り（ASM） | Java 21〜23 では ASM 9.11.1 でクラスファイルを読みます（Java 28 のクラスファイルまで）。より新しい JDK でコンパイルする場合は ASM を更新してください。lambdaQL は ASM をシェーディングしないので、自分で更新できます。Java 24 以上では実行中の JDK の `java.lang.classfile` で読むので、ASM のバージョンとは関係ありません。lambdaQL の jar は Multi-Release JAR で、JVM が実行バージョンに合うほうを選びます |
| `-parameters` | 推奨。lambdaQL がプロシージャの引数名を確認できます（[8.4](08-procedures-functions.md#84-warning-argument-order)） |
| デバッグ情報 | 行番号を残してください（javac のデフォルト）。lambdaQL はエラーメッセージにソースの行を入れます |
| IntelliJ の "Number objects compared using `==`" | lambdaQL のラムダ式の中では意図した動作です。メソッドに `@SuppressWarnings("NumberEquality")` を宣言すると警告を消せます |
| 文字列の `==` に対する IntelliJ の警告 | lambdaQL のラムダ式の中では、これも意図した動作です |

---

[Java トラック](README.md) | 前: [8. プロシージャと DB 関数](08-procedures-functions.md) | 次: [10. ルールと制限](10-rules-and-limits.md) | [このページの Kotlin 版](../kotlin/09-kotlin-notes.md)
