<a id="1-overview"></a>
# 1. 概要

[マニュアルのホーム](README.md) | トラック: [Java](java/README.md) | [Kotlin](kotlin/README.md) | 次: 2. はじめよう（[Java](java/02-getting-started.md) | [Kotlin](kotlin/02-getting-started.md)）

このページは両方の言語向けです。コードは **Java** と **Kotlin** のセクションに分けて載せています。

<a id="what-is-lambdaql"></a>
## lambdaQL とは

lambdaQL は、JPA と Hibernate の上で動く Java・Kotlin 用のクエリライブラリです。

- lambdaQL は JPA クエリを **ラムダ式** で表現し、記述します。
- ラムダ式の部分は実行せず、**解析してクエリに変換します**。
- lambdaQL はメソッドとラムダ式の実行結果を **HQL（JPQL）** クエリにします。

### Java

<!-- region: ManualSharedTest.java#overview-first -->
```java
long minPrice = 100;

SelectQuery<Order> q = qb.from(Order.class)
        .where(o -> o.getStatus() == 1 || o.getCustomer().getName().equals("Kim"))
        .where(o -> o.getPrice() > minPrice);
assertEquals("select e0 from Order e0 left join e0.customer j1"
        + " where (e0.status = 1 or j1.name = 'Kim') and e0.price > :p1", q.toHql().hql());
List<Order> rows = q.toList();
```

### Kotlin

<!-- region: ManualKotlinSharedTest.kt#overview-first -->
```kotlin
val minPrice = 100.0

val q = qb.from(Order::class.java)
    .where { it.status.toInt() == 1 || it.customer!!.name == "Kim" }
    .where { it.price > minPrice }
assertEquals("select e0 from Order e0 left join e0.customer j1"
    + " where (e0.status = 1 or j1.name = 'Kim') and e0.price > :p1", q.toHql().hql())
val rows = q.toList()
```

<a id="hql-the-same-for-both"></a>
### HQL（両方の言語で同じ）

```
select e0 from Order e0 left join e0.customer j1
where (e0.status = 1 or j1.name = 'Kim') and e0.price > :p1
```

**データベースごとの SQL**

<!-- sql: overview-first -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where (o1_0.status=1 or c1_0.name='Kim') and o1_0.price>?
```

- 2 つの `where` 呼び出しは `AND` で結合されます。
- 定数 `"Kim"` はリテラルとしてクエリに書かれ、キャプチャした `minPrice` はバインドパラメーター（`:p1`）として扱われます。
- `where` で `o.getCustomer().getName()` のように関連エンティティのプロパティを使うと、lambdaQL は顧客テーブルを結合します。
  顧客のない注文が条件に合いうる場合は `left join`、合いえない場合は内部結合の `join` です。
  - 上の例では、status が 1 の注文は顧客がなくても条件に合うので `left join` です。
  - Where 条件が `o.getCustomer().getName().equals("Kim")` だけなら、内部結合の `join` です。

ラムダ式は普通の Java や Kotlin のコードに見えます。
しかし lambdaQL では、ラムダ式は Java や Kotlin の構文で書いた **クエリ言語** です。
`o.getCustomer().getName()`（Kotlin では `it.customer?.name`）は getter を呼び出さず、**カラム名として評価されます**。

コード例: [`ManualSharedTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualSharedTest.java)（`overview-first`）、[`ManualKotlinSharedTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSharedTest.kt)（`overview-first`）。

<a id="why-lambdaql"></a>
## なぜ lambdaQL なのか

| 目的 | lambdaQL の方法 |
|---|---|
| 型安全 | コンパイラーがラムダ式をチェックします。IDE でプロパティの名前を変えると、クエリの中の名前も一緒に変わります。 |
| コード生成なし | メタモデルジェネレーターや Q クラスは不要です。lambdaQL は、Hibernate が実行時にすでに持っている JPA Metamodel を使います。 |
| なじみのある構文 | すでに知っている `==`、`&&`、`\|\|`、`!`、getter やプロパティ、メソッド呼び出しを使います。 |
| 安全な値 | キャプチャした値と計算した値は必ずバインドパラメーターになります。クエリのテキストに書くのは、コードにコンパイルされた定数だけです（シングルクォートはエスケープします）。 |
| ラムダ式を解析して変換 | ラムダ式の部分は実行せずに解析します。エンティティのオブジェクトを作らず、getter も呼び出さないので、直感的なクエリ文を作れます。 |
| 解析の失敗はエラー | lambdaQL はラムダ式をクエリとして解析できないと、コード付きの例外を投げます。 |

<a id="how-it-works-5-steps"></a>
## 動作のしくみ（5 ステップ）

```
 your lambda ──► 1. find body ──► 2. read bytecode ──► 3. rebuild condition
                                                              │
     result ◄── 5. run with Hibernate ◄── 4. build HQL ◄──────┘
```

| ステップ | 内容 |
|---|---|
| 1. ラムダ式の本体を見つける | lambdaQL は、ラムダ式の本体がコンパイルされた場所と、ラムダ式がキャプチャした値を見つけます。 |
| 2. バイトコードを読む | lambdaQL は、ステップ 1 で見つけたラムダ式本体のコンパイル済みコード（バイトコード）を読みます。コードは実行しません。Java 21〜23 では ASM を、Java 24 以上では JDK の `java.lang.classfile` を使います。 |
| 3. 条件を組み立て直す | Java と Kotlin は `&&`、`\|\|`、`!` をジャンプ命令にコンパイルします。lambdaQL はこのジャンプ命令から元の論理ツリーを組み立て直します。 |
| 4. HQL を作る | getter とプロパティは JPA のプロパティパスになります（`o.getPrice()` または `it.price` → `e0.price`）。ラムダ式の中の定数はリテラルになり、キャプチャした値はパラメーター（`:p1`）になります。 |
| 5. 実行する | lambdaQL はパラメーターをバインドし、Hibernate で HQL を実行します。各ラムダ式の解析結果はキャッシュされるので、バイトコードを読むのは 1 回だけです。 |

lambdaQL はラムダ式ごとに解析結果をキャッシュするので、バイトコードを読んで解析するのは 1 回だけです。
新しい JVM での最初の呼び出しはラムダ式の解析のため JPQL より約 26ms 長くかかりますが、その後クエリごとに加わるコストは約 0.5〜3µs とごくわずかです（H2 での実測、[ベンチマーク](../../design/BENCHMARK.md)）。

lambdaQL に渡す Kotlin のラムダ式リテラルは、Java のラムダ式と同じ種類のシリアライズ可能なラムダ式にコンパイルされます。
そのため、1 つのエンジンで両方の言語を読めます。

<a id="design-principles"></a>
## 設計方針

<a id="one-lambda-per-clause"></a>
### 1 つのクエリ句に 1 つのラムダ式

`where`、`orderBy`、`select` のようなクエリ句は、それぞれ **1 つの** ラムダ式を受け取ります。
各句の 1 つのラムダ式に、取得するカラムやソートキーをすべて書きます。

#### Java

<!-- region: ManualSharedTest.java#one-lambda-per-clause -->
```java
ValueQuery<OrderView> q = qb.from(Order.class)
        .where(o -> o.getStatus() == 1 && o.isActive())                 // one condition lambda
        .orderBy(o -> desc(o.getPrice()).asc(o.getId()))     // one sort lambda for all keys
        .select(o -> new OrderView(o.getId(), o.getPrice()));          // one select lambda
assertEquals("select e0.id, e0.price from Order e0 where e0.status = 1 and e0.active = true"
        + " order by e0.price desc, e0.id asc", q.toHql().hql());
```

#### Kotlin

<!-- region: ManualKotlinSharedTest.kt#one-lambda-per-clause -->
```kotlin
val q = qb.from(Order::class.java)
    .where { it.status.toInt() == 1 && it.isActive }               // one condition lambda
    .orderBy { o -> desc(o.price).asc(o.id) }               // one sort lambda for all keys
    .select { OrderView(it.id, it.price) }                         // one select lambda
assertEquals("select e0.id, e0.price from Order e0 where e0.status = 1 and e0.active = true"
    + " order by e0.price desc, e0.id asc", q.toHql().hql())
```

<a id="no-static-helpers-except-agg"></a>
### static ヘルパー `By`、`Agg`、`Dml`

| クラス | 用途 |
|---|---|
| `org.lambdaql.query.By` | ソート関数（`asc`、`desc`、`sort`） |
| `org.lambdaql.Agg` | 集計（`count`、`sum`、`avg`、`min`、`max`、`countDistinct`） |
| `org.lambdaql.Dml` | setter のないエンティティへの値の代入（`Dml.value(column, value)`） |

#### Java

<!-- region: ManualSharedTest.java#agg -->
```java
ValueQuery<CustomerTotal> q = qb.from(Order.class)
        .groupBy(o -> o.getCustomer().getId())
        .having(o -> Agg.sum(o.getPrice()) > 1000)
        .select(o -> new CustomerTotal(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice())));
assertEquals("select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id"
        + " having sum(e0.price) > 1000.0", q.toHql().hql());
```

#### Kotlin

<!-- region: ManualKotlinSharedTest.kt#agg -->
```kotlin
val q = qb.from(Order::class.java)
    .groupBy { it.customer?.id }
    .having { Agg.sum(it.price) > 1000 }
    .select { CustomerTotal(it.customer?.id, Agg.count(), Agg.sum(it.price)) }
assertEquals("select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id"
    + " having sum(e0.price) > 1000.0", q.toHql().hql())
```

<a id="a-lambda-is-a-query-not-code"></a>
### ラムダ式はコードではなくクエリ式

lambdaQL は Java や Kotlin の実行ルールではなく、ドキュメントに書かれた独自のルールに従います。

#### Java

| 書いたコード | lambdaQL での意味 | 理由 |
|---|---|---|
| `o.getId() == id`（2 つの `Long` オブジェクト） | 値の等価比較: `e0.id = :p1` | クエリでは、オブジェクトの `==` は「同じオブジェクト」ではなく「同じ値」を意味します。キャプチャした値の `null` は NULL としてバインドされ、どの行にも一致しません（`WARN` レベルの `LQL-4045`）。NULL を探すには `o.getId() == null` と書いてください。 |
| `Objects.equals(o.getDescription(), o.getCustName())`、`!text.equals(o.getDescription())` | `e0.description = e0.custName`、`e0.description <> :p1`（SQL の意味） | エンティティの値どうしを比較したり、値と `!=` で比較したりすると、値が NULL の行は一致しません。Java ならこれらの行の一部を返します。このような行を残すには `o.getDescription() == null \|\| ...` と書いてください。 |
| `o.getCustomer().getName()` | 結合した顧客の `name` カラム | 顧客テーブルを内部結合の `join` または `left join` で結合します。詳しいルールは [3.10 関連のパス](java/03-where.md#310-association-paths) を参照してください。 |
| `status` カラムが NULL のときの `o.getStatus() == 1` | 行は返されない（SQL の意味） | `status` カラムが NULL の行は結果に含まれません。 |
| `o.getDescription() == null` | `e0.description is null` | null チェックを直接書いたので、lambdaQL はそれを使います。 |

> [!TIP]
> IntelliJ は `o.getId() == id` に「Number objects compared using `==`」という警告を出すことがあります。
> lambdaQL のラムダ式では意図した動作です。メソッドに `@SuppressWarnings("NumberEquality")` を付けると警告を消せます。

#### Kotlin

| 書いたコード | lambdaQL での意味 | 理由 |
|---|---|---|
| `it.id == id`（`id: Long?`、nullable な変数） | 値の等価比較: `e0.id = :p1` | Kotlin の `==` はもともと「同じ値」を意味します。キャプチャした値の `null` は NULL としてバインドされ、どの行にも一致しません（`WARN` レベルの `LQL-4045`）。 |
| `it.description == it.custName`、`it.description != text` | `e0.description = e0.custName`、`e0.description <> :p1`（SQL の意味） | エンティティの値どうしを比較したり、値と `!=` で比較したりすると、値が NULL の行は一致しません。このような行を残すには `it.description == null \|\| ...` と書いてください。 |
| `it.customer?.name` または `it.customer!!.name` | 結合した顧客の `name` カラム | 顧客テーブルを内部結合の `join` または `left join` で結合します。詳しいルールは [3.10 関連のパス](kotlin/03-where.md#310-association-paths) を参照してください。 |
| `price` カラムが NULL のときの `it.price!! > 100.0` | 行は返されない（SQL の意味） | `price` カラムが NULL の行は結果に含まれません。 |
| `it.description == null` | `e0.description is null` | null チェックを直接書いたので、lambdaQL はそれを使います。 |

**NULL は SQL と同じように扱います。**
比較するカラムが NULL の行は、NULL の比較条件に合わないので結果に含まれません。
たとえば `where(o -> !text.equals(o.getDescription()))`（Kotlin では `where { it.description != text }`）は HQL `e0.description <> :p1` になります。
`description` カラムが NULL の注文はこの条件に合わないので結果に含まれません。Java や Kotlin のリストでは NULL は `text` と異なるので含まれる点が違います。
NULL の注文も取得するには、`o.getDescription() == null || !text.equals(o.getDescription())` のように null チェックを直接書いてください。
詳しいルールは 3.6 NULL のルール（[Java](java/03-where.md#36-null-rules) | [Kotlin](kotlin/03-where.md#36-null-rules)）を参照してください。

### SARGable な SQL

lambdaQL はインデックスを使える（「SARGable」な）SQL を作ります。

- カラム側に関数、演算、`CASE` を置きません。
- 値を変える必要があるとき（たとえば小文字にする）は、lambdaQL はバインドする前に Java 側で **パラメーター** を変えます。
- ルール上カラムに関数が必要で、インデックスが役に立たない場合、lambdaQL は警告をログに出します。

#### Java

| 書いたコード | lambdaQL が作ら**ない**もの | lambdaQL が作るもの |
|---|---|---|
| `o.getId() != null && o.getId() == id` | `e0.id is not null and e0.id = :p1` | `e0.id = :p1`（id は NULL になれない） |
| `o.getDescription() == text` | `e0.description is not distinct from :p1`（Oracle では `decode(...)`、インデックスなし） | `e0.description = :p1` |

#### Kotlin

| 書いたコード | lambdaQL が作ら**ない**もの | lambdaQL が作るもの |
|---|---|---|
| `it.id == id`（`it.id` は `Long?`） | `not (e0.id is null or e0.id <> :p1)` | `e0.id = :p1`（id は NULL になれない） |

<a id="java-and-kotlin"></a>
### Java と Kotlin

Java と Kotlin は同じ解析エンジンを使います。そのため、同じ意味の Kotlin のラムダ式と Java のラムダ式は同じ HQL を作ります。
たとえば Java の `o.getPrice() > 100` と Kotlin の `it.price > 100.0` は、どちらも `e0.price > 100.0` になります。
ただし、すべての構文をクエリに変換できるわけではありません。ループ、`switch` と Kotlin の対象付き `when (x)`、カラムに対するほとんどの文字列関数（`trim()` など）のような構文を使うと、lambdaQL はクエリを実行する前にエラーを出します。
言語ごとの違いは 9 章（[Java の注意点](java/09-java-notes.md) | [Kotlin の注意点](kotlin/09-kotlin-notes.md)）を参照してください。

<a id="fail-loudly"></a>
### 解析できないラムダ式

lambdaQL はラムダ式をクエリとして解析できないと、`[LQL-2004]` のようなコード付きの例外を投げます（[11. エラーと言語](11-errors-and-languages.md) を参照）。

拒否される書き方の例:

| 書き方 | Java | Kotlin |
|---|---|---|
| 条件によって選ばれる値 | `o.getStatus() == (flag ? 1 : 2)` | `it.status == if (flag) 1 else 2` |
| ラムダ式の中のループやローカル変数 | はい、拒否されます | はい、拒否されます |
| ラムダ式ではないもの | 匿名クラス | `object : ...` 式、または Kotlin の関数型の値（`val f: (Order) -> Boolean`） |
| SQL で意味を持たないメソッド呼び出し | はい、拒否されます | はい、拒否されます（自分で書いた inline でない拡張関数も含む） |

コード例: [`ManualSharedTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualSharedTest.java)（`one-lambda-per-clause`、`agg`）、[`ManualKotlinSharedTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinSharedTest.kt)（`one-lambda-per-clause`、`agg`）。

<a id="comparison-with-other-approaches"></a>
## ほかの方法との比較

| | JPQL / HQL 文字列 | JPA Criteria API | QueryDSL | lambdaQL |
|---|---|---|---|---|
| クエリの書き方 | 文字列の中のテキスト | Java のビルダーオブジェクト | 生成された Q クラス | ラムダ式 |
| コンパイラーによるチェック | ❌（実行時に Hibernate がチェック） | ✅ | ✅ | ✅ |
| コード生成が必要 | ❌ | 任意（静的メタモデル） | ✅（Q クラス） | ❌ |
| IDE での名前変更に強い | ❌ | 静的メタモデルを使う場合 | ✅ | ✅ |
| 見た目 | SQL | Java のビルダーコード | SQL に似た Java のメソッド | SQL に似た Java / Kotlin のラムダ式 |
| Kotlin | 文字列 | 動くが冗長 | 動く | サポート |

<a id="next"></a>
## 次へ

- 2. はじめよう: lambdaQL をビルドして最初のクエリを書きます。[Java](java/02-getting-started.md) | [Kotlin](kotlin/02-getting-started.md)
- [マニュアルのホーム](README.md)
