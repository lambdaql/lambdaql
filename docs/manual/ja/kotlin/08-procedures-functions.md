<a id="8-stored-procedures-and-db-functions-kotlin"></a>
# 8. ストアドプロシージャと DB 関数（Kotlin）

> [!NOTE]
> **状況: 設計済み、まだ実装されていません。**
> この章は決定済みの設計です（決定 D30 と D31、マイルストーン M11）。
> Kotlin の構文はコンパイルでき（kotlinc 2.3.21）、呼び出しは Hibernate 6.6.5 を使って H2、MariaDB、Oracle 23ai で確認しました。
> 現在の lambdaQL ではまだ **動きません**。今動くのは解析コアと `where` + `toList()` だけです
> （H2、MariaDB、Oracle でテスト済み）。全体の状況は [トラックの README](README.md) を見てください。

[Kotlin トラック](README.md) | [マニュアルのホーム](../README.md) | 前: [7. 更新、挿入、削除](07-dml.md) | 次: [9. Kotlin の注意点](09-kotlin-notes.md) | [このページの Java 版](../java/08-procedures-functions.md)

共通ページ: [1. 概要](../01-overview.md) · [11. エラーと言語](../11-errors-and-languages.md)

この章は 2 つの部分に分かれています。

| 部分 | 内容 | アノテーション | 呼び出し方 |
|---|---|---|---|
| [8.1 から 8.5](#81-declare-procedures) | **ストアドプロシージャ** | 抽象インターフェース関数に `@SqlProcedure` | `qb.call(OrderProcs::class.java) { it.calc(id, rate) }.execute()` |
| [8.6 から 8.10](#86-declare-db-functions) | **DB 関数**（スカラー） | インターフェースの `companion object` にある `@JvmStatic` 関数に `@SqlFunction` | どのラムダの中でも直接: `where { OrderFns.discount(it.price)!! > 10 }` |

どちらの場合も、シグネチャは Kotlin のインターフェースに **1 回だけ** 書きます。
lambdaQL がこれらの関数を **実行することはありません**。ラムダのバイトコードの中の呼び出しを読み、データベースを呼び出します。

<a id="contents"></a>
## 目次

- [8.1 プロシージャを宣言する](#81-declare-procedures)
- [8.2 プロシージャを呼び出す](#82-call-procedures)
- [8.3 OUT、INOUT、結果セット](#83-out-inout-and-result-sets)
- [8.4 注意: 引数の順序](#84-warning-argument-order)
- [8.5 プロシージャのルール PR1 から PR15（まとめ）](#85-procedure-rules-pr1-to-pr15-summary)
- [8.6 DB 関数を宣言する](#86-declare-db-functions)
- [8.7 クエリで DB 関数を使う](#87-use-db-functions-in-queries)
- [8.8 DB 関数を単独で呼び出す](#88-call-a-db-function-alone)
- [8.9 注意: カラムに対する関数とインデックス（SARGable）](#89-warning-functions-on-columns-and-indexes-sargable)
- [8.10 関数のルール FN1 から FN11（まとめ）](#810-function-rules-fn1-to-fn11-summary)
- [8.11 データベースごとのサポート](#811-database-support)

---

<a id="81-declare-procedures"></a>
## 8.1 プロシージャを宣言する

インターフェースを書きます。各 **抽象** 関数に `@SqlProcedure("NAME")` を付けます。
実装は書きません。lambdaQL も実装を作りません。

```kotlin
interface OrderProcs {
    @SqlProcedure("SQ_CALC")     fun calc(id: Long, rate: Double): Double?               // OUT 1 つ → 戻り値
    @SqlProcedure("SQ_CALC2")    fun calc2(id: Long, rate: Double): CalcOut              // OUT 複数 → data class
    @SqlProcedure("SP_ADJUST")   fun adjust(id: Long, @InOut balance: Int?): AdjOut      // INOUT
    @SqlProcedure("SP_RECENT")   fun recent(custId: Long): List<OrderView>               // 結果セット / Oracle の REF CURSOR
    @SqlProcedure("SQ_PKG.CALC") fun pkgCalc(id: Long, rate: Double): Double?            // パッケージ名またはスキーマ名
    @SqlProcedure("SP_PURGE")    fun purge(days: Int)                                    // 結果なし
}

data class CalcOut(val total: Double?, val cnt: Int?)
data class AdjOut(val balance: Int?, val msg: String?)
```

注意点:

- `@SqlProcedure`（または `@SqlFunction`）は **必須** です。関数が何であるかを lambdaQL に伝えます。
  （`@Procedure` と `@Function` という名前は使いませんでした。`@Procedure` は Spring Data JPA のアノテーションでもあり、`@Function` は Kotlin の `kotlin.Function` と衝突するからです。）
- 値が空の場合、名前は関数名から作られます: `calcTotal` → `CALC_TOTAL`。
- **null 許容の戻り値の型**（`Double?`）を使ってください。null を許容しない型にプロシージャが NULL を返すと、lambdaQL は名前付きの例外を投げます。
- インターフェースと結果の data class は、DB のシグネチャのコピーです。これは型安全のための代価です。lambdaQL はその一部をデータベースと照合します（ルール PR2）。
- プロシージャ（抽象関数）と DB 関数（`companion object` の中）を **同じインターフェース** に置けます。

---

<a id="82-call-procedures"></a>
## 8.2 プロシージャを呼び出す

`qb.call(Interface::class.java) { it.function(args) }` を使い、続けて `execute()` を呼びます。

```kotlin
val d: Double?           = qb.call(OrderProcs::class.java) { it.calc(id, rate) }.execute()
val co: CalcOut          = qb.call(OrderProcs::class.java) { it.calc2(id, rate) }.execute()
val a: AdjOut            = qb.call(OrderProcs::class.java) { it.adjust(id, balance) }.execute()
val r: List<OrderView>   = qb.call(OrderProcs::class.java) { it.recent(custId) }.execute()

// 単純な値の名前付き引数
val d2 = qb.call(OrderProcs::class.java) { it.calc(rate = rate, id = id) }.execute()

// 結果なし: exec を使う
qb.exec(OrderProcs::class.java) { it.purge(30) }.execute()
```

結果のない関数に、なぜ `call` ではなく `exec` を使うのでしょうか。

- Java では、両方に `call` を使うとオーバーロードがあいまいになります（コンパイルエラー）。両方の言語で同じ API を使います。
- `run` は選びませんでした。Kotlin では、引数を書き忘れると `run { }` が黙って標準ライブラリの `run` になってしまうからです。

<a id="what-the-lambda-may-contain-rule-pr12"></a>
### ラムダに書けるもの（ルール PR12）

ラムダ本体は **スタブ関数の呼び出し 1 つ** です。それ以外は書けません。

| 書けるもの | 書けないもの |
|---|---|
| `{ it.calc(id, rate) }` | ログ出力、2 つの呼び出し、`if`、ネストした呼び出し |
| 引数: キャプチャ値、定数、それらの計算（`rate * 2`） | エンティティのパス（ここにはエンティティがありません） |
| **単純な値** の名前付き引数（`calc(rate = rate, id = id)`） | 計算を含む名前付き引数（`calc(rate = r * 2, id = id)`）。下を参照 |

Kotlin は、計算を含む名前付き引数に対して隠れたローカル変数を作ります。v0.1 の lambdaQL はローカル変数を拒否します。
その場合は位置引数を使ってください: `it.calc(id, r * 2)`。

その他の点:

- OUT パラメーターを持つプロシージャに `exec` を使うとエラーです。OUT の値をうっかり失うことを防ぎます。
- **デフォルト引数** を持つスタブ関数（`fun calc(id: Long, rate: Double = 1.0)`）はエラーです（ルール PR11）。Kotlin は DB のデフォルトではなく Kotlin のデフォルトを入れてしまうからです。
- パラメーターのデータベース側のデフォルト値は使えません。すべての IN パラメーターを渡してください。
- IN の値に `null` を渡すのは問題ありません。
- null を許容しない `Long`/`Double` のパラメーターも、null 許容の `Long?`/`Double?` のパラメーターも、どちらも使えます（ルール PR13）。

---

<a id="83-out-inout-and-result-sets"></a>
## 8.3 OUT、INOUT、結果セット

戻り値の型とプロシージャの対応（ルール PR3、PR4、PR5）:

| プロシージャにあるもの | スタブ関数の戻り値の型 |
|---|---|
| 何もない | 戻り値の型なし（`Unit`）→ `qb.exec(...)` で呼び出す |
| OUT パラメーター 1 つ | その値（`Double?`、`Int?`、...） |
| 複数の OUT / INOUT パラメーター | Kotlin の data class（または Java の record） |
| 結果セット 1 つ（MariaDB の `SELECT`、Oracle の `SYS_REFCURSOR` OUT、H2 の `ResultSet` 関数） | `List<T>` |
| 複数の結果セット | `List` プロパティを持つ data class |
| OUT の値と結果セット（MariaDB） | スカラーと `List` のプロパティを持つ data class |

data class のプロパティの埋め方:

- **スカラーのプロパティ** ← OUT と INOUT のパラメーター（カーソルを除く）、DB の順序で。
- **`List` のプロパティ** ← 結果セット、順番どおりに（Oracle: カーソル OUT パラメーターの順序、MariaDB: 返された結果セットの順序）。
- 数が合わない場合、lambdaQL はエラーを投げます。
- IN、OUT、IN、INOUT のように混ざった位置でも問題ありません。lambdaQL は DB のメタデータから位置を読みます。

### INOUT

パラメーターに `@InOut` を付けます。渡した値が **入力** になります。**出力** される値は、返される data class のプロパティになります。

```kotlin
@SqlProcedure("SP_ADJUST") fun adjust(id: Long, @InOut balance: Int?): AdjOut

val a = qb.call(OrderProcs::class.java) { it.adjust(id, 100) }.execute()
// a.balance が新しい残高
```

<a id="result-set-element-type-t"></a>
### 結果セットの要素の型 `T`

| `T` が | lambdaQL の読み方 |
|---|---|
| エンティティ | Hibernate の結果クラス |
| data class、record、DTO | JDBC の `CallableStatement`。カラムは **ラベルで** 対応付けます（`camelCase` ↔ `snake_case`、大文字と小文字は区別しません） |

MariaDB と Oracle は `SELECT *` のカラムを異なる順序で返すので、lambdaQL がカラムを位置で対応付けることはありません。
一般的な型は変換します（たとえば数値を `Long`/`Int` に、タイムスタンプを `LocalDateTime` に）。

制限:

- プロシージャの結果のページングはできません。
- Oracle の暗黙的な結果（`DBMS_SQL.RETURN_RESULT`）はサポートしていません。`SYS_REFCURSOR` の OUT パラメーターを使ってください。

<a id="transactions-rule-pr9"></a>
### トランザクション（ルール PR9）

- プロシージャの中の DML は、呼び出し元のトランザクションの一部です。一緒にロールバックされます。
- 呼び出しの前に、lambdaQL は永続化コンテキストをフラッシュします。ただし、トランザクションが有効で、変更がある場合だけです。
- トランザクションの外では、呼び出しは自動コミットです。
- 呼び出しのあと、読み込まれたエンティティが古くなっていることがあります。そのエンティティに `refresh` か `evict` を使ってください。
- プロシージャの中の `COMMIT` や DDL はトランザクションを壊します。lambdaQL はこれを止められません。

<a id="affected-rows-rule-pr10"></a>
### 影響を受けた行（ルール PR10）

プロシージャには、共通の「影響を受けた行数」の結果がありません（Oracle は -1 を返します）。
その数が必要なら、OUT パラメーターとして返してください。

---

<a id="84-warning-argument-order"></a>
## 8.4 注意: 引数の順序

> [!WARNING]
> **スタブ関数のパラメーターの順序は、データベースでの順序と同じでなければなりません。**

lambdaQL は引数を **位置で** バインドします（ルール PR2）。
Hibernate 6.6.5 は、名前が登録されていても位置でバインドします。
MariaDB と H2 のドライバーは名前付きパラメーターをサポートしていません。
そのため、順序が間違っていると、**エラーなしで間違った結果** になることがあります。

Kotlin の名前付き引数（`calc(rate = rate, id = id)`）でも、これは **変わりません**。コンパイラーは引数をスタブ関数の順序に並べ直します。意味があるのはスタブ関数の順序だけです。

最初の呼び出しで lambdaQL が確認すること（JDBC のメタデータから）:

| 確認 | 見つかる? |
|---|---|
| パラメーターの数 | はい、エラー |
| モード（IN / OUT / INOUT / カーソル） | はい、エラー |
| 型 | はい、エラー |
| **同じ型** の 2 つのパラメーターの入れ替わり | **パラメーター名が使える場合だけ** |

危険なケースの例:

```kotlin
// データベース: SP_TRANSFER(p_to NUMBER, p_from NUMBER)
@SqlProcedure("SP_TRANSFER") fun transfer(from: Long, to: Long)   // 入れ替わっている! 同じ型
```

数、モード、型はすべて一致します。間違いを示せるのは名前だけです。
lambdaQL は、名前を取得できるときは名前を比べます: Kotlin の `-java-parameters`（または kotlin-reflect）、あるいは `@Param`。
`p_` という接頭辞と大文字・小文字は無視します。名前が使えず、隣り合う 2 つのパラメーターが同じ型の場合、lambdaQL は WARN をログに出します。

**アドバイス:** `-java-parameters` を付けてコンパイルし（[Kotlin コンパイラーの設定](02-getting-started.md#kotlin-compiler-settings) を参照）、データベースと同じ名前を使ってください。

Oracle では、lambdaQL は本当の名前によるバインドも使えます（`pass_procedure_paramater_names=true` とスタブのパラメーター名を一緒に使います）。

---

<a id="85-procedure-rules-pr1-to-pr15-summary"></a>
## 8.5 プロシージャのルール PR1 から PR15（まとめ）

| # | ルール |
|---|---|
| PR1 | 名前: アノテーションの値をそのまま使います。値が空なら、関数名を UPPER_SNAKE にしたもの。パッケージ名やスキーマ名（`SQ_PKG.CALC`、`LQL.SQ_CALC`）も使えます。混乱を避けるため、`@SqlProcedure(schema = ..., pkg = ..., value = ...)` と書くこともできます。 |
| PR2 | 引数は **位置で** バインドされます。最初の呼び出しで、数、モード、型を DB のメタデータと照合します。同じ型の引数の入れ替わりは、パラメーター名がある場合にだけ見つかります（[8.4](#84-warning-argument-order) を参照）。パッケージ内の Oracle のオーバーロードは v0.1 ではエラーです。 |
| PR3 | OUT 1 つ → 戻り値。複数の OUT/INOUT → data class または record。`@InOut` は INOUT パラメーターを示します。 |
| PR4 | 結果セット 1 つ → `List<T>`。エンティティは Hibernate を通して、data class/record/DTO は JDBC を通してラベルで対応付けます。ページングはありません。 |
| PR5 | 複数の結果セット → data class の `List` プロパティに順番どおり。Oracle の暗黙的な結果は拒否されます。 |
| PR6 | 単独で呼び出す DB 関数は、データベースごとに異なる経路を使います（[8.8](#88-call-a-db-function-alone) を参照）。 |
| PR7 | クエリの中の DB 関数: [8.6](#86-declare-db-functions) とルール FN1 から FN11 を参照。 |
| PR8 | 条件の中でカラムに DB 関数を使うと、インデックスが使えないので WARN になります（[8.9](#89-warning-functions-on-columns-and-indexes-sargable) を参照）。 |
| PR9 | プロシージャは呼び出し元のトランザクションの中で実行されます。必要なら lambdaQL は呼び出しの前にフラッシュします。呼び出しのあと、読み込まれたエンティティが古くなっていることがあります。 |
| PR10 | 共通の影響行数はありません。OUT パラメーターを使ってください。 |
| PR11 | IN の値に `null` を使うのは問題ありません。DB のパラメーターのデフォルトは使えません。スタブ関数の Kotlin のデフォルト引数はエラーです。 |
| PR12 | ラムダはスタブの呼び出し 1 つです。引数はキャプチャ値、定数、またはそれらの計算です。名前付き引数は単純な値の場合だけです。OUT パラメーターがあるのに `exec` を使うとエラーです。 |
| PR13 | null を許容しない `Long`/`Double` も、null 許容の `Long?`/`Double?` も使えます。Boolean のパラメーターは MariaDB と Oracle 23ai 以降で使えます。 |
| PR14 | 各ラムダは 1 回だけ解析されます。プロシージャのメタデータは名前ごとに 1 回だけ読まれます。 |
| PR15 | H2 には OUT、INOUT、複数の結果セット、パッケージがありません。プロシージャは MariaDB か Oracle でテストしてください（たとえば Testcontainers を使って）。 |

---

<a id="86-declare-db-functions"></a>
## 8.6 DB 関数を宣言する

DB 関数は、データベースの中で作った関数です。たとえば `FN_DISCOUNT(price)` です。
Kotlin のインターフェースには static 関数がないので、インターフェースの **`companion object`** の中に、**`@JvmStatic`** と `@SqlFunction` を付けて宣言します。
本体は例外を投げるだけにします。lambdaQL の中で実行されることはありません。

```kotlin
interface OrderFns {
    companion object {
        @JvmStatic @SqlFunction("FN_DISCOUNT")
        fun discount(price: Double?): Double? = throw DbOnlyException()

        @JvmStatic @SqlFunction("FN_IS_VIP")            // Boolean: Oracle 23ai 以降
        fun isVip(customerId: Long?): Boolean? = throw DbOnlyException()
    }
}
```

lambdaQL のラムダの **外** で `OrderFns.discount(...)` を呼ぶと、例外が投げられます。これは意図的です。

なぜ `@JvmStatic` なのでしょうか。こうすると Java のコードから `OrderFns.discount(...)` を static 関数として呼べ、Java からも Kotlin からも同じ関数に見えるからです。
lambdaQL は `companion object` の関数に付いたアノテーションを読みます。

その他の宣言方法:

| 宣言 | 状況 |
|---|---|
| インターフェースの `companion object` + `@JvmStatic` | **推奨** |
| Kotlin の `object` + `@JvmStatic` | サポート |
| Java インターフェースの static メソッド（Kotlin から呼び出す） | サポート |
| Kotlin のトップレベル関数 | サポート、**非推奨**: import を忘れると、同じ名前の標準ライブラリ関数（たとえば `maxOf`）が黙って使われることがあります |
| インターフェースの default 関数 + シングルトン定数 | サポート、ただし書くのが長い |
| リポジトリが関数インターフェースを実装する（mixin） | 拒否 |
| 関数型インターフェースの定数（`DISCOUNT.apply(x)`）、関数型のプロパティ | 拒否 |
| 拡張関数 | v0.1 では拒否（KC11） |
| デフォルト引数、`inline`、`vararg` を使う `@SqlFunction` | 拒否（FN9） |

関数は **インターフェース名を付けて** 呼んでください（`OrderFns.discount`）。関数を import しても動きますが、同じ名前のメンバー関数に隠されることがあります。

---

<a id="87-use-db-functions-in-queries"></a>
## 8.7 クエリで DB 関数を使う

関数を直接呼びます。コンテキスト引数は必要ありません。すべてのラムダで使えます:
`where`、`select`、`orderBy`、`groupBy`、`having`、`set`、そしてサブクエリの中。

```kotlin
qb.from(Order::class.java).where { OrderFns.discount(it.price)!! > 10 }.toList()
// → where function('FN_DISCOUNT' as Double, o.price) > 10

qb.from(Order::class.java).where { OrderFns.isVip(it.customer?.id)!! }.toList()     // Boolean 関数を単独で

qb.from(Order::class.java).where { it.price!! > OrderFns.discount(base)!! }.toList()  // カラム側はそのまま

qb.from(Order::class.java).orderBy { o, by -> by.desc(OrderFns.discount(o.price)) }.toList()

qb.from(Order::class.java)
    .select { o -> OrderView(o.id, o.customer?.name, OrderFns.discount(o.price)) }
    .toList()

qb.update(Order::class.java).set { it.price = OrderFns.discount(it.price) }.all().execute()
```

**SQL ブロックの読み方。** `…` は省略した select リスト（エンティティのすべてのカラム）で、`?` はバインドパラメーターです。[`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java) はこのページの HQL を Hibernate に直接渡し、H2 2.3、MariaDB 10.11、Oracle 23.26（Hibernate 6.6.5）での SQL を記録します。lambdaQL はまだこれらのクエリを作りません。Hibernate の SQL が変わるとテストは失敗します。

**データベースごとの SQL（予定）** — 予定の HQL を Hibernate が変換したもので、`PlannedSqlTest` が記録しました。lambdaQL はまだこのクエリを作りません。`expected-sql/` の [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)、例 `08-function`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where fn_discount(o1_0.price)>10
```

関数を登録しないと、関数名は小文字になります（`fn_discount`）。下の「登録」を参照してください。

`!!` はコンパイラーのためだけのものです（関数は `Double?` を返します）。lambdaQL はこれを読み飛ばします（ルール FN6）。

<a id="how-lambdaql-renders-it"></a>
### lambdaQL の出力のしかた

lambdaQL は DB 関数を **常に** 次の形で出力します。

```sql
function('<NAME>' as <Type>, arg1, arg2, ...)
```

`<Type>` は宣言した戻り値の型から決まります。使える型は次のものだけです。

| 使える戻り値の型 |
|---|
| `Double`、`Integer`、`Long`、`BigDecimal`、`String`、`LocalDate`、`LocalDateTime`、`Boolean` |

Kotlin の `Double?`、`Int?`、`Long?`、`Boolean?` と、その null を許容しない形は、これらの名前に対応します。
ジェネリックな戻り値（`fun <X> nvl(a: X, b: X): X`）、エンティティ、コレクション、`Unit` は解析時に拒否されます。

<a id="registration-optional"></a>
### 登録（任意）

`function('FN_DISCOUNT' as Double, ...)` の形には登録は必要ありません（3 つのデータベースで確認済み）。
関数インターフェースを登録すると、Hibernate は名前の大文字・小文字も保ち、引数の数と型も確認します。

lambdaQL は `FunctionContributor`（ServiceLoader で見つけるもの）を予定しています。インターフェースは Hibernate のプロパティに列挙します。

```properties
# 素の Hibernate
lambdaql.functions.classes=com.acme.OrderFns,com.acme.CustomerFns

# Spring Boot
spring.jpa.properties.lambdaql.functions.classes=com.acme.OrderFns
```

プロパティを設定しないと、何も登録されません。パッケージのスキャンは v0.1 にはありません。

<a id="mixed-case-names-need-registration"></a>
### 大文字と小文字が混ざった名前には登録が必要

引用符を付けて大文字と小文字を混ぜて作った関数名（`"SQ_Fn_Mixed"`）は、登録しないと失敗します（H2 と Oracle では名前が小文字になります）。
キーと SQL の名前を分けて、インターフェースを登録してください。

```kotlin
@JvmStatic @SqlFunction(key = "SQ_Fn_Mixed", sql = "\"SQ_Fn_Mixed\"")
fun mixed(v: Double?): Double? = throw DbOnlyException()
```

このような関数が登録されていないと、lambdaQL は最初に使ったときに失敗します。

---

<a id="88-call-a-db-function-alone"></a>
## 8.8 DB 関数を単独で呼び出す

**引数のないラムダ** で `qb.call { ... }` を使います。

```kotlin
val v: Double? = qb.call { OrderFns.discount(price) }.execute()
```

**データベースごとの SQL（予定）** — 予定の HQL を Hibernate が変換したもので、`PlannedSqlTest` が記録しました。lambdaQL はまだこのクエリを作りません。`expected-sql/` の [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)、例 `08-function-alone`:

```sql
-- H2, MariaDB, Oracle (same)
select fn_discount(?)
```

これは FROM のない `select fn_discount(?)` として実行されます。Oracle がこれを受け付けるのは 23ai からです。Oracle では、lambdaQL は代わりに JDBC の関数呼び出しを使います（下の表を参照）。

引数の数が違うので、`qb.call(OrderProcs::class.java) { ... }` とは衝突しません。

lambdaQL の実行のしかた（ルール PR6）:

| データベース | 経路 |
|---|---|
| Oracle | JDBC の関数呼び出し `{? = call FN(?)}`。DML を行う関数やカーソルを返す関数でも動きます。 |
| MariaDB、H2 | HQL の `select function('FN' as T, :p1)` |

lambdaQL は先に保留中の変更をフラッシュするので（PR9 と同じ）、関数は最新の変更を見ることができます。

---

<a id="89-warning-functions-on-columns-and-indexes-sargable"></a>
## 8.9 注意: カラムに対する関数とインデックス（SARGable）

lambdaQL は **インデックスを使える** SQL を作ろうとします（[10. ルールと制限](10-rules-and-limits.md#102-sargable-rule-index-friendly-sql) を参照）。
カラムを関数で囲むと、通常のインデックスはたいてい **使えません**。

```kotlin
.where { OrderFns.discount(it.price)!! > 10 }
// → function('FN_DISCOUNT' as Double, o.price) > 10   -- price のインデックスは使われない
```

**データベースごとの SQL（予定）** — 予定の HQL を Hibernate が変換したもので、`PlannedSqlTest` が記録しました。lambdaQL はまだこのクエリを作りません。`expected-sql/` の [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)、例 `08-function`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where fn_discount(o1_0.price)>10
```

そのため、lambdaQL はこの形に対して **WARN** をログに出します。strict モードではエラーです（ルール PR8、FN7）。

| ケース | MariaDB | Oracle |
|---|---|---|
| カラムに対する関数: `fn(o.price) > 10` | WARN（ストアド関数はインデックスに使えません） | WARN。**ただし** 関数が `DETERMINISTIC` で、**かつ** 同じ式の関数ベースインデックスがある場合は除きます |
| 値だけに対する関数: `o.price > fn(rate)` | 関数が `NOT DETERMINISTIC`（これがデフォルト!）なら WARN。その場合、MariaDB はインデックス全体をスキャンします。 | 警告なし（インデックスの範囲スキャン） |

**アドバイス:**

- できるだけ関数を値の側に置いてください: `it.price!! > OrderFns.discount(base)!!`。
- MariaDB では、関数が決定的なら `DETERMINISTIC` と宣言してください。
- Oracle では、`DETERMINISTIC` な関数と関数ベースインデックスを用意すると警告が出なくなります。

---

<a id="810-function-rules-fn1-to-fn11-summary"></a>
## 8.10 関数のルール FN1 から FN11（まとめ）

| # | ルール |
|---|---|
| FN1 | `@SqlFunction` は関数に付けるアノテーションです。値が空なら、関数名を UPPER_SNAKE にしたもの。 |
| FN2 | lambdaQL は呼び出された関数を見つけ、そのアノテーションを読みます。Kotlin では `Companion` のレシーバーを読み飛ばします。本体は例外を投げるだけにします（`= throw ...`）。本当の本体を持つ関数はエラーです。アプリケーションとデータベースで値が異なるおそれがあるからです。 |
| FN3 | 同じ名前のオーバーロードは、すべて同じ DB 関数に対応しなければなりません。そうでなければエラーです。（Kotlin の `Double?` の引数は `Double?` のオーバーロードを選びます。Kotlin から見た Java のエンティティの getter は、プリミティブの `double` のオーバーロードを選ぶことがあります。） |
| FN4 | アノテーションのない呼び出しや、lambdaQL が特定できない呼び出しはエラーです。黙って読み飛ばすことはありません。 |
| FN5 | 引数は [3. 条件](03-where.md) の値のルールに従います。キャプチャ値だけでできた引数でも、バインドされて **データベースで** 評価されます。 |
| FN6 | `!!` とボクシングの呼び出しは読み飛ばされます。戻り値の型は宣言した関数から決まります。 |
| FN7 | カラムに対する関数は WARN になります（[8.9](#89-warning-functions-on-columns-and-indexes-sargable) を参照）。 |
| FN8 | Hibernate の組み込み関数（`COALESCE`、`ROUND`、`SYSDATE`、...）と同じ名前はエラーです。Hibernate が黙って組み込み関数を使ってしまうからです。接頭辞かスキーマ名（`PUBLIC.SQ_FN`）を使ってください。 |
| FN9 | Kotlin: デフォルト引数、`inline`、`vararg` はエラーです。 |
| FN10 | v0.1 にはスカラー関数だけがあります。ユーザー定義の集約関数とウィンドウ関数は v0.2 の予定です。引数のない関数は `fn()` として出力されます。 |
| FN11 | 既知の DB の違い: MariaDB では、VARCHAR を返す関数が文字列リテラルと照合順序の衝突を起こすことがあります。`BigDecimal` の結果のスケールが異なります（MariaDB と Oracle は 0、H2 は 1）。Boolean の結果には Oracle 23ai 以降が必要です。 |

---

<a id="811-database-support"></a>
## 8.11 データベースごとのサポート

| 機能 | H2 2.3 | MariaDB 10.11 | Oracle 23ai |
|---|---|---|---|
| IN パラメーター | はい | はい | はい |
| OUT / INOUT | **いいえ**（H2 の制限） | はい | はい |
| 結果セット 1 つ | はい（`ResultSet` 関数） | はい（プロシージャの中の `SELECT`） | はい（`SYS_REFCURSOR` OUT） |
| 複数の結果セット | いいえ | はい | はい（複数のカーソル OUT パラメーター） |
| OUT の値と結果セットを一緒に | いいえ | はい | はい |
| パッケージ名（`PKG.PROC`） | いいえ | いいえ（`sql_mode=ORACLE` が必要） | はい |
| クエリの中の DB 関数 | はい | はい | はい |
| Boolean のパラメーター / 結果 | – | はい | はい（23ai 以降） |

v0.1 では予定していないもの: 動的なプロシージャ名のための文字列ビルダー（v0.2 の候補）、Oracle のパッケージのオーバーロード、テーブル値関数、プロシージャの結果のページング。

この章のエラーは、`[LQL-xxxx]` コードを持つ `LambdaQLException` のサブクラスです。[11. エラーと言語](../11-errors-and-languages.md) を参照してください。

---

[Kotlin トラック](README.md) | 前: [7. 更新、挿入、削除](07-dml.md) | 次: [9. Kotlin の注意点](09-kotlin-notes.md) | [このページの Java 版](../java/08-procedures-functions.md)
