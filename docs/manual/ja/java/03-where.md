<a id="3-conditions-where-java"></a>
# 3. 条件（`where`）（Java）

[Java トラック](README.md) | [マニュアルのホーム](../README.md) | 前: [2. はじめよう](02-getting-started.md) | 次: [4. ソートとページング](04-order-paging.md) | [このページの Kotlin 版](../kotlin/03-where.md)

共通ページ: [1. 概要](../01-overview.md) · [11. エラーと言語](../11-errors-and-languages.md)

---

<a id="31-the-idea-in-one-sentence"></a>
## 3.1 概要

**lambdaQL のラムダ式はコードではなく、クエリ式です。** lambdaQL は条件のラムダ式を呼び出さず、ラムダ式のバイト opcode を解析します。
キャプチャ値だけでできた式だけを、Java で先に計算します（[3.11](#311-captured-values) を参照）。
そのため、エンティティを扱う部分には Java の実行ルール（オブジェクトの同一性、ボクシングのキャッシュ）は適用されません。
lambdaQL は独自のルールを使います。この章ではそのルールを説明します。

lambdaQL はラムダ式をクエリとして解析できない場合、コード付きの例外を投げます（[3.13](#313-what-is-rejected) を参照）。

<a id="32-example-entities"></a>
## 3.2 例で使うエンティティ

例では、エンティティ `Order`、`Customer`、`Item` を使います。プロパティの一覧は [2. はじめようの例で使うエンティティ](02-getting-started.md#example-entities) にあります。

この章でよく使うプロパティは次のとおりです。

- `Order.price`（`double`）と `Order.status`（`short`）はプリミティブ型なので、NULL になれません。
- `Order.cnt`（`Integer`）、`Order.total`（`Double`）、`Order.description`（`String`）は NULL になれます。
- `Order.customer` は `Customer` への `@ManyToOne` の関連で、`Order.kind` は enum `Order.Kind`（`NORMAL`、`RUSH`）です。

<a id="33-basic-form"></a>
## 3.3 基本の形

<!-- region: SelectQueryTest.java#to-list -->
```java
QueryBuilder qb = new QueryBuilder(emf);
List<Order> orders = qb.from(Order.class)
        .where(o -> o.getStatus() == 1 && o.getPrice() > 100)
        .toList();
```

**データベースごとの SQL**

<!-- sql: to-hql -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>100.0
```

この章の例は、H2、MariaDB、Oracle で、Java で同じ絞り込みをした結果と同じ行を返します。

`toHql()` はクエリを実行せずに、HQL とパラメーターを返します。`assertEquals` の文字列が、lambdaQL が作る HQL です。
<!-- region: SelectQueryTest.java#to-hql -->
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

**データベースごとの SQL**

<!-- sql: to-hql -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>100.0
```

**データベースごとの SQL**

<!-- sql: to-hql#2 -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>?
```

`where()` は何回でも呼べます。条件は `AND` で結合されます。
<!-- region: SelectQueryTest.java#where-many -->
```java
RenderedQuery q = qb.from(Order.class)
        .where(o -> o.getStatus() == 1 || o.getStatus() == 3)
        .where(o -> o.isActive())
        .toHql();
assertEquals("select e0 from Order e0 where (e0.status = 1 or e0.status = 3) and e0.active = true", q.hql());
```

**データベースごとの SQL**

<!-- sql: where-many -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where (o1_0.status=1 or o1_0.status=3) and o1_0.active=true
```

`where` にはメソッド参照を使えません。`where(Order::isActive)` は `LQL-1009` で拒否されるので、`where(o -> o.isActive())` と書いてください（[9.9](09-java-notes.md#99-method-references) を参照）。

**HQL の中の値。** ルールは、値がどこから来たかで決まります。

| 値 | HQL での形 | 例 |
|---|---|---|
| ラムダ式に直接書いた定数: 数値（小数も）、文字列、`true`/`false`、`null`、enum 定数、コンパイル時の `static final` 定数 | **リテラル** | `o.getPrice() > 100` → `e0.price > 100.0` |
| キャプチャ値: ローカル変数、フィールド、メソッドのパラメーター | **バインドパラメーター** | `o.getPrice() > minPrice` → `e0.price > :p1` |
| 計算した値: `new BigDecimal("1.5")`、`form.getText().trim()`、`LocalDateTime.of(...)` | **バインドパラメーター** | `:p1` |

- 定数は呼び出しのたびに同じです。そのため SQL 文は変わらず、データベースはクエリの計画を立てるときに値を見ることができます。
- 文字列定数の中の単一引用符はエスケープされます: `"O'Brien"` は `'O''Brien'` になります。この文字列はユーザーの入力ではなく、クラスにコンパイルされた定数なので、SQL インジェクションの危険はありません。
- キャプチャ値はユーザーから来ることがあるので、バインドパラメーターです。呼び出しごとに値だけが変わります。
- 例外は 1 つです。整数カラムと比較する小数のキャプチャ値は、小数リテラル（`2.5BD`）で書きます（[3.4](#34-comparisons) の「整数カラムと小数の値」）。数値に変換した値だけを書くので、SQL インジェクションの危険はありません。

enum 定数は完全なクラス名で書きます。
<!-- region: SelectQueryTest.java#enum -->
```java
RenderedQuery q = qb.from(Order.class).where(o -> o.getKind() == Order.Kind.RUSH).toHql();
assertEquals("select e0 from Order e0 where e0.kind = org.lambdaql.query.Order$Kind.RUSH", q.hql());
```

**データベースごとの SQL**

<!-- sql: enum -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.kind='RUSH'
```

コード例: [`SelectQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java)（`to-list`、`to-hql`、`where-many`、`enum`）。

<a id="34-comparisons"></a>
## 3.4 比較

| Java | HQL | 注意 |
|---|---|---|
| `o.getStatus() == 1` | `e0.status = 1` | |
| `o.getStatus() != 1` | `e0.status <> 1` | |
| `o.getPrice() > p` | `e0.price > :p1` | `<`、`<=`、`>=` も同様 |
| `o.getUpdateAt().compareTo(since) > 0` | `e0.updateAt > :p1` | 日付と時刻も `compareTo` で比較。`isAfter`、`isBefore` はサポートしない（`LQL-3008`） |
| `o.getDescription().compareTo("m") < 0` | `e0.description < 'm'` | `compareTo(..) op 0` は `op` になる |

数値との比較（`== 1` や `> 100.0` のように片方がプリミティブ型）は **SQL の意味** に従います。
カラムが NULL の行は返されません。

`+`、`-`、`*`、`/` を使った簡単な算術を使えます。
カラムを含む整数の `/` は避けてください。SQL で計算するので、データベースごとに結果が違います（MariaDB は `5/2 = 2.5`）。
キャプチャ値どうしの計算（下の `n / 2.0`）はクエリの前に Java で行うので、この違いはありません。

下の例の `cnt` は、`Order` の整数カラム（`Integer cnt`）です。

**カラムに対する算術とインデックス**。`o.getCnt() * 2 > n` のようにカラムに算術をかけると、インデックスを使えません。lambdaQL は `LQL-5012` を `WARN` レベルで記録し、`SargableMode.STRICT` では解析が失敗します（`LQL-3066`）。
値の側へ移すときは、`o.getCnt() > n / 2.0` のように小数で割ってください。整数の割り算 `n / 2` は違う行を返します。負の数を掛けた場合は、比較の向きを逆にしてください。詳しくは [10.2](10-rules-and-limits.md#db-functions-on-a-column) を参照してください。

**整数カラムと小数の値**。整数カラムを小数の値と比較すると、lambdaQL は値を書いたとおりに比較します。丸めや値の除外はしません。

- ラムダ式に書いた定数はリテラルのまま残ります。`o.getCnt() >= 2.5` は `e0.cnt >= 2.5` になります。
- キャプチャ値も小数リテラルで書きます。`e0.cnt >= 2.5BD` になり、バインドパラメーターはありません。`BD` は HQL の `BigDecimal` リテラルの接尾辞です。
- そのような要素がある IN リストは、すべての要素を小数リテラルで書きます（`e0.cnt in (2.5BD, 3.0BD)`）。

キャプチャ値をバインドパラメーターで送る原則の例外です。Hibernate 7.4 は、整数カラムと比較するパラメーターに小数をバインドすると、`BigDecimal` 型を指定しても拒否します（`QueryArgumentException`、実測）。

以前は値をカラムの型に切り詰めていたので `2.5` が `2` になり、誤った行を返していました。Hibernate 6.6 は整数カラムの型でバインドした小数を拒否し（`CoercionException`）、IN リストの要素は何も知らせずに切り詰めます（3 つの DB で実測）。
整数の比較に変える方法（`>= 3`）は採用しませんでした。実行される SQL がラムダ式と違ってしまい、エンティティのマッピング（`Integer`）と DB のカラム型（例: 小数を持つ `DECIMAL`）が違うと誤った行を返すからです。

結果は H2、MariaDB 10.11、Oracle 23.26 で正しいです（実測）。3 つの DB とも、SQL は `cnt>=2.5`、`cnt in (2.5,3.0)` のようにカラムをそのままにして小数と比較します。
MariaDB の `EXPLAIN` はインデックスの範囲スキャン（`type=range`）を示します。Oracle はカラムに変換が付かないので、インデックスを使える形です。インデックスのヒントを与えると `INDEX RANGE SCAN` になり、3000 行のテーブルでヒントがなければ、整数リテラル（`cnt >= 99`）と同じく全件スキャンを選びます（すべて実測）。

<!-- region: SqlMeaningTest.java#whole-number -->
```java
// cnt is a whole number; 2.5 is compared as written (it is never cut to 2): a decimal literal
double half = 2.5;
RenderedQuery atLeast = qb.from(Order.class).where(o -> o.getCnt() >= half).toHql();
assertEquals("select e0 from Order e0 where e0.cnt >= 2.5BD", atLeast.hql());
assertEquals(Map.of(), atLeast.parameters());                                // written as a decimal literal
// a constant written in the lambda stays a literal
assertEquals("select e0 from Order e0 where e0.cnt >= 2.5", qb.from(Order.class).where(o -> o.getCnt() >= 2.5).toHql().hql());
// an IN list with such a value is written as decimal literals
List<Double> values = List.of(2.5, 3.0);
RenderedQuery in = qb.from(Order.class).where(o -> values.contains(o.getCnt().doubleValue())).toHql();
assertEquals("select e0 from Order e0 where e0.cnt in (2.5BD, 3.0BD)", in.hql());
```

**浮動小数点:** `float`/`double` のカラムについて、lambdaQL は Java とビット単位まで同じ結果を約束しません。
キャプチャした `NaN` や `Infinity` の値は、クエリの実行時に拒否されます。

コード例: [`SqlMeaningTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlMeaningTest.java)（`whole-number`）。

<a id="35-and-or-not"></a>
## 3.5 AND, OR, NOT

| Java | HQL |
|---|---|
| `a && b` | `a and b` |
| `a \|\| b` | `a or b` |
| `!a` | `not (a)` |
| 括弧 | 必要な分だけ残す |

<!-- region: ManualJavaWhereTest.java#and-or-not -->
```java
double minPrice = 100;
SelectQuery<Order> both = qb.from(Order.class)
        .where(o -> (o.getStatus() == 1 || o.isActive()) && o.getPrice() > minPrice);
assertEquals("select e0 from Order e0 where (e0.status = 1 or e0.active = true) and e0.price > :p1",
        both.toHql().hql());
SelectQuery<Order> not = qb.from(Order.class)
        .where(o -> !(o.getStatus() == 1 && o.getPrice() > minPrice));
// the NOT is pushed into the comparisons: the same rows (status and price are never NULL)
assertEquals("select e0 from Order e0 where e0.status <> 1 or e0.price <= :p1", not.toHql().hql());
```

ルール:

- 結果は **SQL の 3 値論理**（TRUE / FALSE / UNKNOWN）に従います。lambdaQL は、この方式が H2、MariaDB、Oracle で同じ行を返すことを確認しました。
- javac と lambdaQL は `!` の位置を移すことがあります（ド・モルガン、`!(x > y)` を `x <= y` に）。上の例の `!(... && ...)` は `e0.status <> 1 or e0.price <= :p1` になります。2 つのカラムは NULL になれないので、結果は `not (...)` と同じです。
- lambdaQL は **条件を単純化しません**。`o.getPrice() > p || o.getPrice() <= p` と書けば、書いたとおりに送ります。`price` が NULL の行は返されません。これは正常な SQL の動作です。
- `&&` と `||` を使ってください。短絡評価をしない演算子 `&`、`|`、`^` は拒否されます。

**boolean の三項演算子。** 短い形 3 つだけを許可します。それぞれ `AND`、`OR`、`NOT` と同じです。

| Java | 同じ意味 |
|---|---|
| `c ? x : false` | `c && x` |
| `c ? true : x` | `c \|\| x` |
| `c ? false : true` | `!c` |

キャプチャ値と定数だけで値を選ぶ三項演算子（`o.getStatus() == (flag ? 1 : 2)`）は、クエリの実行時に Java で計算し、バインドパラメーターとして渡します。
エンティティの値で値を選ぶ三項演算子（`o.getStatus() == (o.isActive() ? 1 : 2)`）は、SQL の `case when … end` になります（[3.5.1](#351-values-chosen-by-a-condition) を参照）。
それ以外の boolean の三項演算子（`c ? x : y`）は拒否されます（`LQL-2021`）。

<!-- region: ManualJavaWhereTest.java#ternary-outside -->
```java
// a ternary of captured values and constants: computed in Java, bound as :p1
SelectQuery<Order> query = qb.from(Order.class).where(o -> o.getStatus() == (flag ? 1 : 2));
assertEquals("select e0 from Order e0 where e0.status = :p1", query.toHql().hql());
// a ternary that reads the entity becomes case when
SelectQuery<Order> byEntity = qb.from(Order.class).where(o -> o.getStatus() == (o.isActive() ? 1 : 2));
assertEquals("select e0 from Order e0 where e0.status = case when e0.active = true then 1 else 2 end",
        byEntity.toHql().hql());
```

コード例: [`ManualJavaWhereTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaWhereTest.java)（`and-or-not`、`ternary-outside`）。

<a id="351-values-chosen-by-a-condition"></a>
### 3.5.1 条件で選ぶ値（`case when`）

エンティティの値で値を選ぶ三項演算子は、SQL の `case when … end` 式 1 つになります。
条件には `&&`、`||`、`!` を組み合わせて書けます。三項演算子を連ねると（`a ? x : b ? y : z`）、分岐が複数ある `case` 1 つになります。

`case` は、`where` の比較値、`select` の取得値、`groupBy` のキー、ソートキー、`update` の代入値に使えます。

<!-- region: CaseWhenTest.java#case-where -->
```java
SelectQuery<Order> q = qb.from(Order.class).where(o -> o.getStatus() == (o.isActive() ? 1 : 2));
assertEquals("select e0 from Order e0 where e0.status = case when e0.active = true then 1 else 2 end", q.toHql().hql());
```

<!-- region: CaseWhenTest.java#case-select -->
```java
ValueQuery<String> q = qb.from(Order.class).select(o -> o.getPrice() > 100 ? "big" : "small");
assertEquals("select case when e0.price > 100.0 then 'big' else 'small' end from Order e0", q.toHql().hql());
```

<!-- region: CaseWhenTest.java#case-group -->
```java
// orders per price band: the case is the group key and the selected value
ValueQuery<String> q = qb.from(Order.class)
        .groupBy(o -> o.getPrice() > 100 ? "big" : "small")
        .select(o -> o.getPrice() > 100 ? "big" : "small");
assertEquals("select case when e0.price > 100.0 then 'big' else 'small' end from Order e0"
        + " group by case when e0.price > 100.0 then 'big' else 'small' end", q.toHql().hql());
```

<!-- region: CaseWhenTest.java#case-sort -->
```java
// active orders first, then by price
OrderedSelectQuery<Order> q = qb.from(Order.class).orderBy(o -> asc(o.isActive() ? 0 : 1).asc(o.getPrice())).limit(10);
assertEquals("select e0 from Order e0 order by case when e0.active = true then 0 else 1 end asc, e0.price asc, e0.id asc",
        q.toHql().hql());
```

<!-- region: CaseWhenTest.java#case-set -->
```java
// 10 % off the orders above 100
ExecutableUpdate<Order> u = qb.update(Order.class).set(o -> o.setPrice(o.getPrice() > 100 ? o.getPrice() * 0.9 : o.getPrice())).all();
assertEquals("update Order e0 set e0.price = case when e0.price > 100.0 then (e0.price * 0.9) else e0.price end", u.toHql().hql());
```

ルール:

- `case` は分岐を上から順に検査します。そのため、2 番目以降の分岐の条件は、ラムダ式に書いた条件のままです。
- `where` で比較する `case` は行ごとに計算されるので、データベースはその中のカラムのインデックスを使えません。lambdaQL はこのとき `WARN` レベルで `LQL-5013` を記録します。
- ソート関数のチェーン自体を条件で選ぶ形（`flag ? desc(o.getPrice()) : asc(o.getId())`）は拒否されます（`LQL-2023`）。条件はキーの値の中に書いてください。

コード例: [`CaseWhenTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/CaseWhenTest.java)（`case-where`、`case-select`、`case-group`、`case-sort`、`case-set`）。

<a id="36-null-rules"></a>
## 3.6 NULL のルール

最も重要な部分です。単純な原則が 2 つあります。

1. **エンティティの値は SQL の意味で比較します**。オブジェクトどうしの `==`、`equals`、`Objects.equals`、`!=` は `=` と `<>` になります。
   値が NULL の行は、どちらにも一致しません。
   結果が Java と違う可能性がある場合、lambdaQL は `LQL-5009` を `DEBUG` レベルで記録します。
2. **キャプチャ値は Java で先に計算し、比較はやはり SQL の意味に従います。** キャプチャ値の `null` は NULL としてバインドされるので、`= :p1` と `<> :p1` はどの行にも一致しません。
   この場合、lambdaQL は実行時に `LQL-4045` を `WARN` レベルで記録します。NULL を探すには `x == null` と書いてください（`is null` になります）。
   キャプチャ値だけでできた式（`p.trim()`、別のプリミティブ型に変換する `min.intValue()` など）は Java で計算した値なので、レシーバーが `null` なら Java と同じように例外（`LQL-4012`）を投げます。
   自身のプリミティブ型へのアンボクシング（`min.doubleValue()`）は javac の自動アンボクシングと同じバイト opcode なので、実行せずにボクシングされた値をそのままバインドします（[3.6.4](#364-captured-null-values) を参照）。

lambdaQL は **自分で書いた場合にだけ null を処理します**（`== null`、`!= null`）。
隠れた null チェックを代わりに追加することはありません。

<a id="361-rule-table"></a>
### 3.6.1 ルール表

| # | Java の形 | HQL | NULL の動作 |
|---|---|---|---|
| R1 | 片方がプリミティブ型: `o.getStatus() == 1`、`o.getPrice() > p` | `e0.status = 1`、`e0.price > :p1` | SQL の意味。カラムが NULL なら行は返されない |
| R2 | 両方がオブジェクト: `o.getCnt() == wanted`（`Integer == Integer`）、`String == String`、enum | `e0.cnt = :p1` | **値の比較。** カラムが NULL なら行は返されない。キャプチャ値が `null`: 一致する行なし（`LQL-4045`） |
| R2 | `o.getCnt() != wanted`（オブジェクト） | `e0.cnt <> :p1` | SQL の意味。カラムが NULL なら行は返され **ない**（Java なら返される）。キャプチャ値が `null`: 一致する行なし（`LQL-4045`） |
| R2 | エンティティの値 2 つ: `o.getDescription() == o.getCustName()` | `e0.description = e0.custName` | SQL の意味。どちらかが NULL なら行は返されない（NULL と NULL も同じ） |
| R3 | `x == null`、`x != null` | `x is null`、`x is not null` | |
| R4 | `o.getDescription().equals(p)` | `e0.description = :p1` | SQL の意味。カラムが NULL なら行は返されない。`p == null` なら一致する行なし（`LQL-4045`） |
| R5 | `Objects.equals(a, b)`、`"x".equals(a)`、`Boolean.TRUE.equals(a)` | R2 と同じ: `a = b`、`a = :p1` | R2 と同じ |
| R9 | キャプチャ値 | バインドパラメーター | [3.6.4](#364-captured-null-values) を参照 |

どの Java の型が R1 になり、どの型が R2 になるかは、[9. Java の注意点](09-java-notes.md#93-equality-of-boxed-values) で説明します。

<a id="362--on-objects-compares-values"></a>
### 3.6.2 オブジェクトに対する `==` は値を比較

Java では、2 つの `Integer`、`Long`、`String` オブジェクトに対する `==` は **オブジェクトの同一性** を検査します。
lambdaQL では **値の比較** です。127 より大きい `Long` の値や `new String(...)` でも問題ありません。
ラムダ式はクエリなので、Java のメモリのルールは適用されません。

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

cnt が NULL の行を探すには、上の例の最後のクエリのように null チェックを書いてください。

カラムを関数で包まないので、データベースはインデックスを使えます。

lambdaQL は、クエリの実行時にキャプチャ値をバインドします。`null` は NULL としてバインドされ、一致しうる行がないので、lambdaQL は `LQL-4045` を `WARN` レベルで記録します。
<!-- region: SelectQueryTest.java#null-safe -->
```java
// the SQL meaning: = NULL matches no row; lambdaQL logs LQL-4045 at WARN when it runs with a null
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

**データベースごとの SQL**

<!-- sql: null-safe -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.description=?
```

**データベースごとの SQL**

<!-- sql: null-safe#2 -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.description=?
```

> [!TIP]
> **IDE の警告。** IntelliJ はこの形に "Number objects compared using `==`" を表示します。
> lambdaQL のラムダ式の中では意図した動作です。メソッドに `@SuppressWarnings("NumberEquality")` を宣言すると、警告を消せます。

**NULL になれないカラム。** id、プリミティブ型のカラム、`nullable = false` のカラムは NULL になれません。
このようなカラムの `is null` は常に偽なので、lambdaQL はこのチェックを定数に畳み込んで削除します。

- `o.getId() == null` は `1=0`（行なし）になります。
- `o.getId() != null && o.getId() == id` は `e0.id = :p1` になります。null チェックが消えるので、データベースはインデックスを使えます。

`Long?` の id に対する Kotlin の `it.id == id` も同じバイト opcode にコンパイルされるので、Kotlin の形も単純な `e0.id = :p1` になります。

このルールは、パスに **関連の段階がないときだけ** 適用します。顧客のない注文があるので `o.getCustomer().getId()` は NULL になりえ、このパスの null チェックは残します。

<!-- region: SelectQueryTest.java#never-null -->
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

<!-- region: ManualJavaWhereTest.java#null-check -->
```java
// only rows where cnt is not NULL and not 1
SelectQuery<Order> notOne = qb.from(Order.class).where(o -> o.getCnt() != 1);
assertEquals("select e0 from Order e0 where e0.cnt <> 1", notOne.toHql().hql());
// rows where cnt is NULL too
SelectQuery<Order> notOneOrNull = qb.from(Order.class).where(o -> o.getCnt() == null || o.getCnt() != 1);
assertEquals("select e0 from Order e0 where e0.cnt is null or e0.cnt <> 1", notOneOrNull.toHql().hql());
```

`equals()` と `Objects.equals()` は同じ比較（`=`）になります。否定した `!Objects.equals(...)` は `<>` になり、値が NULL の行は返されません。

<!-- region: ManualJavaWhereTest.java#equals-forms -->
```java
String text = "a";
assertEquals("select e0 from Order e0 where e0.description = :p1",
        qb.from(Order.class).where(o -> o.getDescription().equals(text)).toHql().hql());
// the same comparison; when text is null, no row matches
assertEquals("select e0 from Order e0 where e0.description = :p1",
        qb.from(Order.class).where(o -> Objects.equals(o.getDescription(), text)).toHql().hql());
// rows where cnt is NULL are not returned (the SQL meaning)
SelectQuery<Order> notOne = qb.from(Order.class).where(o -> !Objects.equals(o.getCnt(), 1));
assertEquals("select e0 from Order e0 where e0.cnt <> 1", notOne.toHql().hql());
```

**エンティティの値に対する SQL の意味**。エンティティの値 2 つの比較と、エンティティの値を値と `!=` で比較する場合は SQL に従います。値が NULL の行は、`=` にも `<>` にも一致しません。

Java では `!text.equals(null)` は true で、`Objects.equals(null, null)` も true なので、Java ならこのような行を返します。
このような違いが生じうる場合、lambdaQL は `LQL-5009` を `DEBUG` レベルで条件ごとに 1 回記録します。NULL の行を残すには、ラムダ式に null チェックを自分で書いてください。

H2、MariaDB、Oracle。行は description/custName の組 `a/a`、`a/b`、`null/a`、`null/null`、`b/null` で、`text` は `"a"`:

<!-- region: SqlMeaningTest.java#sql-meaning -->
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

| 形 | キャプチャ値が `null` のとき |
|---|---|
| キャプチャ値との `==`、`!=`、`Objects.equals(...)`、`o.getDescription().equals(p)` | 実行される: NULL をバインドした `= :p1` / `<> :p1` なので、一致する行は **ない**。`WARN` レベルの `LQL-4045` |
| `p.equals(o.getDescription())`（`p` がレシーバー） | 上と同じ: `e0.description = :p1`（NULL）、一致する行は **ない**。`WARN` レベルの `LQL-4045`。例外はない |
| Java が値をアンボクシングする比較: `o.getPrice() > p`（`Double p`）、`i.getQty() == q`（`int qty`、`Integer q`） | 実行される: `e0.price > :p1` に NULL をバインド、一致する行は **ない**。`DEBUG` レベルの `LQL-4045`。自身のプリミティブ型へのアンボクシング（`p.doubleValue()`）は実行しない |
| 別のプリミティブ型に変換するアンボクシング（`p.intValue()`、`Double p`）とメソッド呼び出し（`p.trim()`） | クエリの実行時に **例外**（`LQL-4012`）。Java で値を計算するため |

**`p.equals(o.getDescription())` の形はお勧めしません。** SQL としては `o.getDescription().equals(p)` と同じ比較（`e0.description = :p1`）なので、`p` が `null` でも例外は出ません。
Java のコードとして読むと `null.equals(...)` が NullPointerException のように見えるので、エンティティの値を前に置く `o.getDescription().equals(p)` と書いてください。

理由: ラムダ式の中の比較は SQL の意味に従います。SQL では、NULL との `=`、`<>` は真になりません。
キャプチャ値が null かどうかはクエリの実行時にしか分からないので、lambdaQL は実行時に警告を記録します。

キャプチャ値だけでできた式は、クエリの前に Java で計算する値です。`p.trim()` のようなメソッド呼び出しと、別のプリミティブ型に変換するアンボクシング（`p.intValue()`、`Double p`）がこれに当たり、`null` なら Java と同じように例外（`LQL-4012`）を投げます。
ただし、自身のプリミティブ型へのアンボクシング（`Double.doubleValue()`、`Integer.intValue()`、`Long.longValue()` など。後に続く拡大変換も含む）は計算しません。
javac の自動アンボクシングと手で書いた呼び出しは同じバイト opcode なので、lambdaQL はこれを自動アンボクシングとみなし、ボクシングされた値をバインドします。`null` なら一致する行はなく、`LQL-4045` を `DEBUG` レベルで記録します。
NULL の行を探すには `x == null` と書いてください。

**null ガードによるフィルターの省略。** 同じキャプチャ値を null チェックすると、値が null のとき、その条件はフィルターなしで消えます。
<!-- region: SelectQueryTest.java#captured-null-guard -->
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

動作の仕組み:

- キャプチャ値と定数だけを使う部分は、クエリの実行時に Java で決めます。Java と同じように、左から右へ短絡評価（short-circuit）で処理します。
- `min == null` ならガードが真なので、条件全体が真です。フィルターはありません。比較 `o.getPrice() > min` は HQL に書きません（Java もこの比較を実行しません）。
- `min = 100.0` ならガードが偽なので、`e0.price > :p1` だけが残ります。
- ラムダ式は相変わらず 1 回だけ解析します。値によって変わるのは HQL のテキストだけです。
- この場合、ガードの位置は結果に影響しません。`min == null` のとき、`o.getPrice() > min || min == null` もフィルターがありません。比較部分のアンボクシングを Java で実行しないからです（Java なら NullPointerException）。
- キャプチャ値のメソッドを呼び出す比較（`o.getDescription().equals(p.trim())`）は違います。ガードが後ろにあると `p.trim()` を先に計算するので、`p == null` のとき例外（`LQL-4012`）を投げます。
- `&&` では、ガードは逆に動作します。`min == null` のとき、`min != null && o.getPrice() > min` は行を返しません（`where 1=0`）。

<a id="365-converted-attributes-convert"></a>
### 3.6.5 変換されるプロパティ（`@Convert`）

JPA の `AttributeConverter` が付いたプロパティは、Java の値で比較します。Hibernate（7.4、以前の 6.6 も）は、このようなプロパティと比較するリテラルを変換し、バインドパラメーターも変換します。

したがって、条件を Java の値で書いても、保存された行を見つけます（H2、MariaDB、Oracle で確認済み）。

<!-- region: ConvertTest.java#convert -->
```java
// code is stored reversed ("abc" as "cba"); vip is stored as 'Y'/'N' (YesNoConverter)
SelectQuery<Coded> literal = qb.from(Coded.class).where(c -> c.getCode().equals("abc"));
assertEquals("select e0 from Coded e0 where e0.code = 'abc'", literal.toHql().hql());
assertEquals(List.of("abc"), codes(literal.toList()));       // Hibernate converts the literal
```

コード例: [`ManualJavaWhereTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaWhereTest.java)（`boxed-equals`、`null-check`、`equals-forms`）、[`SelectQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java)（`null-safe`、`never-null`、`captured-null-guard`）、[`SqlMeaningTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlMeaningTest.java)（`sql-meaning`）、[`ConvertTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ConvertTest.java)（`convert`）。

<a id="37-boolean-columns"></a>
## 3.7 boolean のカラム

例のエンティティでは、`active` はプリミティブ型の `boolean` です（getter は `isActive()`）。
`getActive()` が出てくる行は、nullable な `Boolean` カラムの場合です。

| Java | HQL | NULL の行 |
|---|---|---|
| `o.isActive()` | `e0.active = true` | –（プリミティブ型の `boolean` は NULL になれない） |
| `!o.isActive()` | `e0.active = false` | –（プリミティブ型の `boolean` は NULL になれない） |
| `Boolean.TRUE.equals(o.getActive())`（nullable な `Boolean`） | `e0.active = :p1`（`:p1` は `true`） | 返されない |
| `!Boolean.TRUE.equals(o.getActive())` | `e0.active <> :p1` | **返されない**（SQL の意味。Java なら返される） |
| `!o.getActive()`（nullable な `Boolean`） | `e0.active = false` | **返されない**（SQL の意味: NULL は false ではない） |

<!-- region: SelectQueryTest.java#boolean -->
```java
assertEquals("select e0 from Order e0 where e0.active = true",
        qb.from(Order.class).where(o -> o.isActive()).toHql().hql());
assertEquals("select e0 from Order e0 where e0.active = false",       // primitive boolean: never NULL
        qb.from(Order.class).where(o -> !o.isActive()).toHql().hql());
```

**データベースごとの SQL**

<!-- sql: boolean -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.active=true
```

**データベースごとの SQL**

<!-- sql: boolean#2 -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.active=false
```

Hibernate 7.4 は 3 つの DB とも、SQL に `true` / `false` を書きます。Hibernate 6.6 は MariaDB と Oracle で `1` / `0` を書いていました。

注意:

- nullable な `Boolean` カラムに対する `!o.getActive()` は、Java ではアンボクシングの呼び出しです（NULL なら NullPointerException）。lambdaQL は SQL の意味を使います。NULL の行は返されません。
- lambdaQL は `is not true` や `coalesce(active, false)` を使いません。Oracle で失敗します（ORA-00932）。
- より大きな条件の否定は、常に `not (...)` と書きます。lambdaQL は条件を `(...) = false` や `(...) is null` で包みません。Oracle 23 が `WHERE` の中でこれを誤って評価するからです。

**boolean カラムと条件の比較**（`o.isActive() == (o.getStatus() > s)`）では、条件の側は `case when … then true else false end` になります。
この `case` は行ごとに計算されるので、データベースは `status` のインデックスを使えません（`WARN` レベルの `LQL-5013`）。
インデックスが必要なら、2 つの場合を `&&` と `||` に分けて書いてください。この形は `AND`/`OR` で書かれます。

<!-- region: ManualJavaWhereTest.java#boolean-compare -->
```java
int s = 1;
// a boolean column compared with a condition: the condition becomes case when
SelectQuery<Order> compared = qb.from(Order.class).where(o -> o.isActive() == (o.getStatus() > s));
assertEquals("select e0 from Order e0 where e0.active = case when e0.status > :p1 then true else false end",
        compared.toHql().hql());
// the same rows with && and ||: plain comparisons that an index can serve
SelectQuery<Order> query = qb.from(Order.class)
        .where(o -> (o.isActive() && o.getStatus() > s) || (!o.isActive() && o.getStatus() <= s));
assertEquals("select e0 from Order e0 where (e0.active = true and e0.status > :p1)"
        + " or (e0.active = false and e0.status <= :p2)", query.toHql().hql());
```

コード例: [`SelectQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java)（`boolean`）、[`ManualJavaWhereTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaWhereTest.java)（`boolean-compare`）。

<a id="38-strings"></a>
## 3.8 文字列

- 文字列に対する `equals`、`Objects.equals`、`==` は **データベースの照合順序** を使います。
  - MariaDB の `_ci` 照合順序は、大文字小文字と末尾の空白を無視します。
  - Oracle は `''`（空文字列）を NULL として保存します。そのため Oracle では、`"".equals(o.getDescription())` は Java と違う動作になります。
- 2 つの文字列に対する `==` は、同一性の比較ではなく値の比較です。[9.4](09-java-notes.md#94-strings) を参照してください。
- ラムダ式の中の文字列定数はリテラルで書きます。単一引用符はエスケープされます（`''`）。

<!-- region: SelectQueryTest.java#string-literal -->
```java
RenderedQuery q = qb.from(Order.class).where(o -> o.getDescription().equals("O'Brien")).toHql();
assertEquals("select e0 from Order e0 where e0.description = 'O''Brien'", q.hql());
```

**データベースごとの SQL**

<!-- sql: string-literal -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.description='O''Brien'
```

**`startsWith`、`endsWith`、`contains`** は `like` になります。検索語の中の `%` と `_` はワイルドカードではなく、文字そのものとして探します。

<!-- region: LikeTest.java#like -->
```java
// % and _ in the text are not wildcards: only "50%_off sale" matches, not "50x off"
SelectQuery<Order> q = qb.from(Order.class).where(o -> o.getDescription().startsWith(p));
assertEquals("select e0 from Order e0 where e0.description like :p1 escape '!'", q.toHql().hql());
assertEquals("50!%!_%", q.toHql().parameters().get("p1"));
// a constant is written in the HQL
SelectQuery<Order> ends = qb.from(Order.class).where(o -> o.getDescription().endsWith("all"));
assertEquals("select e0 from Order e0 where e0.description like '%all' escape '!'", ends.toHql().hql());
```

- 定数は HQL にそのまま書き、キャプチャ値はバインドパラメーターになります。
- `endsWith` と `contains` はパターンの先頭が `%` なので、カラムの通常のインデックスを使えません。
- MariaDB と MySQL は、デフォルトの照合順序では `like` が大文字小文字を区別しません。そのため、Java の `startsWith` と結果が違うことがあります。
- 検索語に使ったキャプチャ値が `null` ならエラーです（`LQL-4047`）。
- `trim()`、`toUpperCase()` のようなほかの文字列メソッドはサポートしません（`LQL-3008`）。
- ラムダ式の中の文字列の `+` は拒否されます（`LQL-2007`）。キャプチャ値だけを連結する場合も同じです。文字列はラムダ式の前に作ってください。

<!-- region: ManualJavaWhereTest.java#rejected-forms -->
```java
// a String method on an entity value other than startsWith, endsWith and contains
assertEquals("LQL-3008", assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(o -> o.getDescription().trim().equals("a"))).code());
// string concatenation inside the lambda, even of captured values only
assertEquals("LQL-2007", assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(o -> o.getDescription().equals("A" + code))).code());
// an entity compared with an entity: compare the ids
assertEquals("LQL-3011", assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(o -> o.getCustomer() == c)).code());
assertEquals("select e0 from Order e0 where e0.customer.id = :p1",
        qb.from(Order.class).where(o -> o.getCustomer().getId() == c.getId()).toHql().hql());
// operators without short-circuit
assertEquals("LQL-2013", assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(o -> o.isActive() & o.getStatus() == 1)).code());
```

コード例: [`SelectQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java)（`string-literal`）、[`ManualJavaWhereTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaWhereTest.java)（`rejected-forms`）、[`LikeTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/LikeTest.java)（`like`）。

<a id="39-in-with-a-captured-collection"></a>
## 3.9 キャプチャしたコレクションを使う IN

コレクションをラムダ式の **外で** 作り、そのコレクションの `contains` を呼び出してください。コレクションはバインドパラメーター 1 つになります。
例のエンティティの status は `short`:

<!-- region: SelectQueryTest.java#in -->
```java
List<Short> statuses = List.of((short) 2, (short) 3);
RenderedQuery q = qb.from(Order.class).where(o -> statuses.contains(o.getStatus())).toHql();
assertEquals("select e0 from Order e0 where e0.status in (:p1)", q.hql());
```

**データベースごとの SQL**

<!-- sql: in -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status in (?,?)
```

`!contains` は `not in` になります。NULL になれるカラムも、単純な `not in` です（SQL の意味）。
値が NULL の行は `not in` に一致せず、lambdaQL は `LQL-5009` を `DEBUG` レベルで記録します。Java の `!contains` は NULL の値に対して true なので、このような行を残すには `o.getDescription() == null || !descs.contains(o.getDescription())` のように null チェックを書いてください。
<!-- region: SelectQueryTest.java#not-in -->
```java
List<Long> ids = List.of(1L, 2L);
// the id can never be NULL: a plain NOT IN
assertEquals("select e0 from Order e0 where e0.id not in (:p1)",
        qb.from(Order.class).where(o -> !ids.contains(o.getId())).toHql().hql());
// a column that can be NULL: the SQL meaning, a NULL value is not "not in" the list (DEBUG LQL-5009);
// to keep those rows, write o.getDescription() == null || !descs.contains(o.getDescription())
List<String> descs = Arrays.asList("a", "b");
assertEquals("select e0 from Order e0 where e0.description not in (:p1)",
        qb.from(Order.class).where(o -> !descs.contains(o.getDescription())).toHql().hql());
```

**データベースごとの SQL**

<!-- sql: not-in -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.id not in (?,?)
```

**データベースごとの SQL**

<!-- sql: not-in#2 -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.description not in (?,?)
```

| ケース | 結果 |
|---|---|
| レシーバーがキャプチャした `Collection`、`List`、`Set` | サポート |
| コレクションに `null` の要素がある | クエリの実行時に例外 |
| 空のコレクション | `contains` は FALSE、`!contains` は TRUE（Hibernate は `1=0` / `1=1` を書く） |
| 要素 1,500 個 | H2、MariaDB、Oracle 23ai で 1 つの文として動作（以前の Oracle のバージョンには 1,000 個の制限がある。以前のバージョンでは未確認） |
| レシーバーがエンティティのコレクション: `c.getOrders().contains(order)` | **拒否**。`exists` サブクエリを使ってください（[5. サブクエリ](05-subquery.md)） |
| ラムダ式の中で作ったコレクション: `List.of(1, 2).contains(o.getStatus())` | **拒否**。外で作ってキャプチャしてください |

コード例: [`SelectQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java)（`in`、`not-in`）。

<a id="310-association-paths"></a>
## 3.10 関連のパス

lambdaQL は `o.getCustomer().getName()` を **呼び出しません**。この式は「結合した顧客の `name` カラム」を指します。
lambdaQL は JPA の Metamodel で各段階を確認します。

| パスの段階 | HQL | 例 |
|---|---|---|
| to-one の関連（`@ManyToOne`、`@OneToOne`） | エイリアス付きの `join` または `left join`（下記を参照） | `o.getCustomer().getName()` -> `join e0.customer j1` ... `j1.name` |
| 埋め込み（`@Embedded`） | 同じテーブル、結合なし。値オブジェクト全体の等価比較はカラムごとに展開（下記を参照） | `o.getAddress().getCity()` -> `e0.address.city` |
| 対象の `@Id` で終わるパス | 外部キーのカラム、結合なし | `o.getCustomer().getId()` -> `e0.customer.id` |
| 関連そのものを `null` と比較 | 外部キーのカラム、結合なし | `o.getCustomer() == null` -> `e0.customer is null` |
| コレクション（`@OneToMany`、`@ManyToMany`） | **拒否** | `c.getOrders()...` -> `exists` サブクエリを使う |

**値オブジェクト全体の比較。** 埋め込みの値オブジェクト（`@Embedded`）全体を等価で比較すると、lambdaQL はカラムごとに 1 つの比較へ展開します。
`o.getAddress().equals(addr)`、`Objects.equals(o.getAddress(), addr)`、`addr.equals(o.getAddress())` は、すべて `e0.address.city = :p1 and e0.address.zip = :p2` になります。

- 否定（`!o.getAddress().equals(addr)`）は `e0.address.city <> :p1 or e0.address.zip <> :p2` です。
- `o.getAddress() == null` は `e0.address.city is null and e0.address.zip is null` で、`!= null` は `e0.address.city is not null or e0.address.zip is not null` です。
- 各カラムは SQL の意味に従います。NULL のカラムは `=` にも `<>` にも一致しません。たとえば (Seoul, NULL) の行は、等価比較にも否定にも一致しません（3 つの DB で実測）。
- キャプチャした値オブジェクトの各プロパティは、クエリの実行時に読んでバインドします。キャプチャしたオブジェクトが `null` なら、すべてのカラムに NULL をバインドするので一致する行はなく、カラムごとに `LQL-4045` を `WARN` レベルで記録します。
- 入れ子の値オブジェクトも展開します。
- 大小の比較（`<` など）と別の型との比較は、引き続き `LQL-3012` のエラーです。フィールドを比較してください。

<!-- region: MappingRulesTest.java#embedded-compare -->
```java
// address is @Embedded (city, zip): comparing the whole value object compares each column
Address addr = new Address("Seoul", "04524");
SelectQuery<Order> same = qb.from(Order.class).where(o -> o.getAddress().equals(addr));
assertEquals("select e0 from Order e0 where e0.address.city = :p1 and e0.address.zip = :p2", same.toHql().hql());
assertEquals(Map.of("p1", "Seoul", "p2", "04524"), same.toHql().parameters());
// Objects.equals and addr.equals(...) are the same comparison
assertEquals(same.toHql().hql(), qb.from(Order.class).where(o -> Objects.equals(o.getAddress(), addr)).toHql().hql());
// null checks each column: "no address" means every column is NULL
SelectQuery<Order> none = qb.from(Order.class).where(o -> o.getAddress() == null);
assertEquals("select e0 from Order e0 where e0.address.city is null and e0.address.zip is null", none.toHql().hql());
SelectQuery<Order> some = qb.from(Order.class).where(o -> o.getAddress() != null);
assertEquals("select e0 from Order e0 where e0.address.city is not null or e0.address.zip is not null",
        some.toHql().hql());
// each column follows the SQL meaning: a NULL column matches neither = nor <>
SelectQuery<Order> other = qb.from(Order.class).where(o -> !o.getAddress().equals(addr));
assertEquals("select e0 from Order e0 where e0.address.city <> :p1 or e0.address.zip <> :p2", other.toHql().hql());
```

`where` での関連のパスの結合の種類は、条件全体で決めます。
顧客のない注文が条件に合いうるなら `left join`、合いえないなら **inner join** です。
結果は、同じラムダ式を注文のリストに実行した結果と同じです。
`o.getCustomer().getName()` を経由する比較は、顧客のある注文でだけ真です。下の条件はその比較 1 つだけなので、inner join です。
<!-- region: SelectQueryTest.java#inner-join -->
```java
String name = "Kim";
RenderedQuery q = qb.from(Order.class).where(o -> o.getCustomer().getName().equals(name)).toHql();
assertEquals("select e0 from Order e0 join e0.customer j1 where j1.name = :p1", q.hql());
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

- **顧客のない注文** は結果にありません。条件に合いえないからです。
- lambdaQL は null チェックを代わりに追加しません。パスの前に `o.getCustomer() != null &&` を書く必要はありません。

**null ガードは顧客のない注文を残します。** `o.getCustomer() == null || ...` は顧客のない注文も条件に合うので、`left join` になります。
`o.getCustomer() != null && ...` は顧客のない注文が合いえないので、inner join です。
<!-- region: SelectQueryTest.java#null-guard -->
```java
String name = "Kim";
// the null guard keeps orders without a customer, as in Java: a left join
RenderedQuery q = qb.from(Order.class)
        .where(o -> o.getCustomer() == null || o.getCustomer().getName().equals(name))
        .toHql();
assertEquals("select e0 from Order e0 left join e0.customer j1 where e0.customer is null or j1.name = :p1", q.hql());

// no order without a customer can match: an inner join
RenderedQuery q2 = qb.from(Order.class)
        .where(o -> o.getCustomer() != null && o.getCustomer().getName().equals(name))
        .toHql();
assertEquals("select e0 from Order e0 join e0.customer j1 where e0.customer is not null and j1.name = :p1", q2.hql());
```

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

Hibernate は `e0.customer is null` を `c1_0.id is null` と書きます。

- `||` のほかの部分も同じです。`o.getStatus() == 1 || o.getCustomer().getName().equals(name)` は、status が 1 なら顧客がなくても合うので `left join` で、そのような注文も返されます。
- NULL のカラムで真になりうる比較（`is null`、`coalesce`）がパスを経由すると、lambdaQL はその比較にだけ `e0.customer is not null` を加えます。そのため、この比較も顧客のある注文でだけ真です。
  `o.getCustomer() == null || o.getCustomer().getName() == null` は `left join ... where e0.customer is null or (e0.customer is not null and j1.name is null)` になります。
- 値との比較は SQL の意味に従います。`!o.getCustomer().getName().equals(name)` は顧客のない注文を返しません。
- `optional = false` でマッピングした必須の関連は、常に inner join です。

顧客の **ない** 注文を探すには、関連そのものを比較してください。結合せずに外部キーのカラムを検査します。
<!-- region: SelectQueryTest.java#no-association -->
```java
RenderedQuery q = qb.from(Order.class).where(o -> o.getCustomer() == null).toHql();
assertEquals("select e0 from Order e0 where e0.customer is null", q.hql());
```

**データベースごとの SQL**

<!-- sql: no-association -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id is null
```

関連エンティティの `@Id` で終わるパスは外部キーのカラムです。結合は必要ありません。
<!-- region: SelectQueryTest.java#fk-path -->
```java
RenderedQuery q = qb.from(Order.class).where(o -> o.getCustomer().getId() == 1).toHql();
assertEquals("select e0 from Order e0 where e0.customer.id = 1", q.hql());
```

**データベースごとの SQL**

<!-- sql: fk-path -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id=1
```

補足:

- 同じパスにはエイリアスが 1 つ付きます。このエイリアスは `where`、`orderBy`、`select` で共有します。
- `orderBy` や `select` に **だけ** 使ったパスは `left join` なので、そこで行を失うことはありません。同じパスを `where` でも使うと、`orderBy` と `select` は `where` の結合を使います。
- 2 つのエンティティの比較（`o.getCustomer() == c`）は拒否されます（`LQL-3011`）。id を比較してください: `o.getCustomer().getId() == c.getId()`（ここで `c` はキャプチャしたオブジェクト。[3.8](#38-strings) の `rejected-forms` の例を参照）。

コード例: [`MappingRulesTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/MappingRulesTest.java)（`embedded-compare`）、[`SelectQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java)（`inner-join`、`null-guard`、`no-association`、`fk-path`）。

<a id="311-captured-values"></a>
## 3.11 キャプチャ値

ラムダ式は外の値を使えます。ローカル変数、フィールド、キャプチャしたオブジェクトのメソッド呼び出しです。

<!-- region: ManualJavaWhereTest.java#captured-forms -->
```java
LocalDateTime since = LocalDateTime.of(2026, 1, 1, 0, 0);
Filter filter = new Filter(60.0);
Form form = new Form(" a ");
SelectQuery<Order> query = qb.from(Order.class)
        .where(o -> o.getUpdateAt().compareTo(since) > 0)            // a local variable
        .where(o -> o.getStatus() == this.defaultStatus)             // a field of this
        .where(o -> o.getPrice() > filter.minPrice())                // a method of a captured object
        .where(o -> o.getDescription().equals(form.text().trim()));  // computed in Java
assertEquals("select e0 from Order e0 where e0.updateAt > :p1 and e0.status = :p2"
        + " and e0.price > :p3 and e0.description = :p4", query.toHql().hql());
```

ルール:

- エンティティを使わない部分は、**クエリの実行のたびに** Java で計算します。その結果がバインドパラメーターになります。
- コンパイル時の定数（`static final int MAX = 5;`）はキャプチャ値ではありません。javac がラムダ式の中に値をコピーするので、`5` のようにリテラルになります（[3.3](#33-basic-form) を参照）。
- ラムダ式は 1 回解析され、結果はキャッシュされます。新しいキャプチャ値は呼び出しのたびに読みます。
- エンティティの部分だけが SQL になります。`form.text().trim()` は Java で計算します。エンティティの値に対する `o.getDescription().trim()` は拒否されます（`LQL-3008`）。
- Java でキャプチャするローカル変数は、事実上 final でなければなりません。値が変わる場合は、ラムダ式の前に新しい変数に計算しておいてください。
- フィールドを使うラムダ式は `this` をキャプチャします。[9.8](09-java-notes.md#98-lambdas-in-instance-methods-capture-this) を参照してください。

コード例: [`ManualJavaWhereTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaWhereTest.java)（`captured-forms`）。

<a id="312-errors-that-depend-on-order-of-evaluation"></a>
## 3.12 評価の順序によって変わるエラー

SQL は、`AND`/`OR` を左から右へ評価することを約束しません。そのため、次のような「ガード」は SQL では安全ではありません。
lambdaQL はガードを書いたとおりに HQL へ移します。データベースが割り算を先に計算すると、`qty` が 0 の行でゼロ除算のエラーになることがあります。

<!-- region: ManualJavaWhereTest.java#guard -->
```java
// the guard is written as it is; SQL does not promise to check "qty <> 0" before the division
assertEquals("select e0 from Item e0 where e0.qty <> 0 and (e0.price / e0.qty) > 2.0",
        qb.from(Item.class).where(i -> i.getQty() != 0 && i.getPrice() / i.getQty() > 2).toHql().hql());
// compare without the division
SelectQuery<Item> query = qb.from(Item.class).where(i -> i.getQty() > 0 && i.getPrice() > 2 * i.getQty());
assertEquals("select e0 from Item e0 where e0.qty > 0 and e0.price > (2.0 * e0.qty)", query.toHql().hql());
```

ガードに頼らず、割り算なしで比較してください: `i.getQty() > 0 && i.getPrice() > 2 * i.getQty()`。

コード例: [`ManualJavaWhereTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaWhereTest.java)（`guard`）。

<a id="313-what-is-rejected"></a>
## 3.13 拒否されるもの

lambdaQL は解析に失敗すると、コード `[LQL-xxxx]` とソースの行番号を含む例外を投げます。
ほとんどの形はラムダ式を解析するとき（最初に使うとき）に見つかります。誤ったキャプチャ値（メソッドを呼び出した `null`、NaN、`null` の要素があるコレクション）は、クエリの実行時に見つかります。
[11. エラーと言語](../11-errors-and-languages.md) を参照してください。

| 形 | 例 | 解決方法 |
|---|---|---|
| ブロックのラムダ式の中のループ、`switch`、ローカル変数 | `o -> { int x = 1; return ...; }` | 1 つの式で書く |
| 一般的な boolean の三項演算子 | `c ? x : y` | [3.5](#35-and-or-not) の許可された形を使う |
| 短絡評価をしない演算子（`LQL-2013`） | `a & b`、`a \| b`、`a ^ b` | `&&`、`\|\|` を使う |
| 文字列の `+`（`LQL-2007`） | `o.getDescription().equals("A" + code)` | ラムダ式の前に文字列を作る |
| ラムダ式の中のラムダ式やストリーム | `o -> list.stream().anyMatch(...)` | `contains`（[3.9](#39-in-with-a-captured-collection)）かサブクエリを使う |
| エンティティの値に対する文字列メソッド（`LQL-3008`） | `o.getDescription().trim()`（`startsWith`、`endsWith`、`contains` はサポート、[3.8](#38-strings)） | サポートしない |
| エンティティのほかのメソッド | `o.calcSomething()` | getter だけを使う |
| エンティティのコレクションのパス | `c.getOrders().size() > 0`（`Customer.orders`） | `exists` サブクエリ |
| エンティティとエンティティの比較（`LQL-3011`） | `o.getCustomer() == c` | id を比較 |
| 値オブジェクトの大小の比較、別の型との比較（`LQL-3012`） | `o.getAddress().compareTo(a) > 0`（`Address` が `Comparable` のとき） | フィールドを比較。等価比較はサポート |
| メソッドを呼び出す null のキャプチャ値 | `p == null` のときの `o.getDescription().equals(p.trim())`、`o.getStatus() > p.intValue()`（`Double p`） | 前に null ガード（`p == null \|\| ...`）を書くか、クエリの前に `p` を確認 |
| `NaN` / `Infinity` のキャプチャ値 | `o.getPrice() > Double.NaN` | NaN を送らない |
| 匿名クラス、`Serializable` でないラムダ式 | `new Where<Order>() { ... }`、`java.util.function.Predicate` | ラムダ式を使う |

例外の型（すべて `LambdaQLException` を継承）:

| 例外 | 発生するとき |
|---|---|
| `LambdaAnalysisException` | ラムダ式を読めないとき（シリアライズ可能なラムダ式でない場合など） |
| `UnsupportedLambdaShapeException` | サポートしない形のとき（ループ、`switch`、...） |
| `TranslationException` | 意味を翻訳できないとき（未知のメソッド、コレクションのパス、...） |

Java だけの詳細は [9. Java の注意点](09-java-notes.md) にあります。すべてのルールを 1 か所で見るには: [10. ルールと制限](10-rules-and-limits.md)。

---

[Java トラック](README.md) | 前: [2. はじめよう](02-getting-started.md) | 次: [4. ソートとページング](04-order-paging.md) | [このページの Kotlin 版](../kotlin/03-where.md)
