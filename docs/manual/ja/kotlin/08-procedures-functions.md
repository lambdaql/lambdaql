<a id="8-stored-procedures-and-db-functions-kotlin"></a>
# 8. ストアドプロシージャと DB 関数（Kotlin）

> [!NOTE]
> **状況**
> - **実装済み**（決定 D30 と D31、マイルストーン M11、Hibernate 6.6.5）。テストは **Java** のラムダで書かれています:
>   クエリの中の DB 関数（`@SqlFunction`）は H2 2.3、MariaDB 10.11、Oracle 23.26 で（[`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java)）、
>   プロシージャの呼び出し（`@SqlProcedure`、`@InOut`、`qb.call`、`qb.exec`）は MariaDB と Oracle で、DB 関数の単独呼び出しは 3 つのデータベースすべてでテストしています（[`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java)）。
>   lambdaQL は Kotlin の書き方も読みます: `@JvmStatic` を付けた `companion object` の関数と、結果としての Kotlin の data class。この章の Kotlin のテストはまだありません。
> - **こちらも実装済み（MariaDB と Oracle で Java によりテスト済み）:** スタブの引数 **名** とデータベースとの照合（ルール PR2 の一部）、Oracle パッケージのオーバーロードの検出。
> - **こちらも実装済み（Java のラムダでテスト済み）:** 条件の中の DB 関数についての SARGable 警告（ルール PR8、FN7。[8.9](#89-warning-functions-on-columns-and-indexes-sargable) を参照）。
> - **まだ実装されていないもの:** パラメーターの **型** のデータベースとの照合（ルール PR2 の一部）、Oracle パッケージのプロシージャのオーバーロードを 1 つ選んで呼ぶこと。
> - この章の Kotlin のコードは、テスト済みの Java の呼び出しを Kotlin の構文で示したものです。テスト済みの Java のコードは [このページの Java 版](../java/08-procedures-functions.md) にあります。全体の状況は [トラックの README](README.md) を見てください。

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
- `@SqlProcedure` には `schema` と `pkg`（Oracle のパッケージ）もあります: `@SqlProcedure(value = "CALC", schema = "LQL", pkg = "SQ_PKG")`。
- **null 許容の戻り値の型**（`Double?`）を使ってください。null を許容しない型にプロシージャが NULL を返すと、lambdaQL はコード `LQL-4039` を持つ `IllegalStateException` を投げます。
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

- OUT パラメーターを持つプロシージャに `exec` を使うとエラーです（`LQL-4037`、Java でテスト済み）。OUT の値をうっかり失うことを防ぎます。
- `@SqlProcedure` のない関数を呼ぶとエラーです（`LQL-3060`、Java でテスト済み）。
- スタブ関数（`fun calc(id: Long, rate: Double = 1.0)`）の **デフォルト引数** を使う呼び出しはエラーです（`LQL-3062`、ルール PR11）。Kotlin は DB のデフォルトではなく Kotlin のデフォルトを入れてしまうからです。
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
- 数が合わない場合、lambdaQL はエラーを投げます（`LQL-4038`）。
- IN、OUT、IN、INOUT のように混ざった位置でも問題ありません。lambdaQL は DB のメタデータから位置を読みます。

### INOUT

パラメーターに `@InOut` を付けます。渡した値が **入力** になります。**出力** される値は、返される data class のプロパティになります。

```kotlin
@SqlProcedure("SP_ADJUST") fun adjust(id: Long, @InOut balance: Int?): AdjOut

val a = qb.call(OrderProcs::class.java) { it.adjust(id, 100) }.execute()
// a.balance が新しい残高
```

`@InOut` は、データベースで INOUT である引数にだけ正確に付けてください。そうでないと最初の呼び出しが失敗します（`LQL-4036`）。

<a id="result-set-element-type-t"></a>
### 結果セットの要素の型 `T`

| `T` が | lambdaQL の読み方 |
|---|---|
| エンティティ | Hibernate の結果クラス（`StoredProcedureQuery`） |
| Kotlin の data class または Java の record | JDBC の `CallableStatement`。カラムは **ラベルで** 対応付けます: `_` を取り除き、大文字と小文字を区別しないので、`customer_id` は `customerId` に一致します（Java では record でテスト済み） |
| 値 1 つ（`Long?`、`String?`、...） | 各行の最初のカラム |

通常のクラスはここでは行の型になりません。data class を使ってください。
MariaDB と Oracle は `SELECT *` のカラムを異なる順序で返すので、lambdaQL がプロパティを位置で対応付けることはありません。
一般的な型は変換します（たとえば数値を `Long`/`Int` に、タイムスタンプを `LocalDateTime` に）。

制限:

- プロシージャの結果のページングはできません。
- Oracle の暗黙的な結果（`DBMS_SQL.RETURN_RESULT`）はサポートしていません。`SYS_REFCURSOR` の OUT パラメーターを使ってください。

<a id="transactions-rule-pr9"></a>
### トランザクション（ルール PR9）

- プロシージャの中の DML は、呼び出し元のトランザクションの一部です。一緒にロールバックされます。
- 呼び出しの前に、lambdaQL は永続化コンテキストをフラッシュします。ただし、トランザクションが有効で、変更がある場合だけです。
- `QueryBuilder(EntityManagerFactory)` は、呼び出しを自分のトランザクションで実行します。`EntityManager` を持つ `QueryBuilder`（Spring の共有のものも含む）は、呼び出し元のトランザクションを使います。トランザクションがなければ、呼び出しは自動コミットです。
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

最初の呼び出しで lambdaQL が確認すること（JDBC のメタデータ `getProcedureColumns` から）。結果はプロシージャ名ごとにキャッシュします。

| 確認 | 見つかるか |
|---|---|
| プロシージャが存在する | はい、エラー `LQL-4035` |
| IN と INOUT のパラメーターの数 = スタブの引数の数 | はい、エラー `LQL-4036`（Java でテスト済み） |
| INOUT のパラメーター = `@InOut` を付けた引数 | はい、エラー `LQL-4036` |
| OUT の値と結果セットの数 = 戻り値の型の部分の数 | はい、エラー `LQL-4038` |
| 型 | **まだ実装されていません** |
| 同じパラメーター名が別の順序で並ぶ | はい、エラー `LQL-4043`（Java でテスト済み）。スタブの引数名が分かるときだけ（下を参照） |
| Oracle パッケージのプロシージャ名に複数のオーバーロードがある | はい、エラー `LQL-4044`（Oracle で Java によりテスト済み） |

危険なケースの例:

```kotlin
// データベース: SP_TRANSFER(p_to NUMBER, p_from NUMBER)
@SqlProcedure("SP_TRANSFER") fun transfer(from: Long, to: Long)   // 入れ替わっている! 同じ型
```

数、モード、型はすべて一致しています。間違いを示せるのは名前だけです。
そこで lambdaQL は、最初の呼び出しで IN と INOUT の引数の名前を比べます。

- スタブの引数名はクラスファイルから読みます。名前が残るのは `-java-parameters` でコンパイルしたときだけです（[Kotlin コンパイラーの設定](02-getting-started.md#kotlin-compiler-settings) を参照）。スタブの引数に `@org.lambdaql.DbParam("p_to")` を付けると、データベースの名前を直接指定できます。
- データベースの名前から、**すべて** の名前に共通で `_` で終わる接頭辞（`p_`、`in_`、`v_` など）を取り除きます。決まった接頭辞は仮定しません。そのあと、大文字と小文字、`_` を無視します。
- 同じ名前で順序だけが違う場合: 呼び出しの前に `LambdaQLException` `LQL-4043`。
- 名前が違う場合（スタブが別の命名規則に従う）: エラーにしません。チェックが働くように `@DbParam` を付けてください。
- クラスファイルに名前がなく、隣り合う 2 つの引数の型が同じ場合: プロシージャごとに 1 回、`LQL-5004` を `INFO` レベルでログに出します。
- 型はまだ比べません。

同じスタブは Java でテストされています（[`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java)、リージョン `names` と `name-check`、MariaDB と Oracle。[このページの Java 版](../java/08-procedures-functions.md#84-warning-argument-order) を参照）。Kotlin で名前を残すには `-java-parameters` が必要です。次は設計上の例です。

```kotlin
// Database: SQ_PROC_DIFF(p_to, p_from)
interface DiffProcs {
    @SqlProcedure("SQ_PROC_DIFF")
    fun diff(to: Int?, from: Int?): Int?                             // same order as the database: runs

    @SqlProcedure("SQ_PROC_DIFF")
    fun swapped(from: Int?, to: Int?): Int?                          // same names, other order: LQL-4043

    @SqlProcedure("SQ_PROC_DIFF")
    fun named(@DbParam("p_to") target: Int?, @DbParam("p_from") source: Int?): Int?   // another naming rule
}

qb.call(DiffProcs::class.java) { it.diff(10, 3) }.execute()       // 7
qb.call(DiffProcs::class.java) { it.swapped(3, 10) }.execute()    // LambdaQLException LQL-4043, before the call
```

**アドバイス:** `-java-parameters` でコンパイルしてデータベースと同じ名前を使う（共通の接頭辞は省略できます）か、`@DbParam` を付けてください。そうすれば、入れ替わった順序が見つかります。

**Oracle パッケージのオーバーロード。** Oracle のパッケージには、同じ名前のプロシージャが複数あることがあります。
lambdaQL はスタブがどれを指すのか区別できないので、そのような名前を呼ぶと `LQL-4044` で止まります。オーバーロードを 1 つ選んで呼ぶ機能は、後で予定しています。

Java でテストされています（[`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java)、リージョン `overload`、Oracle のみ）。次は設計上の例です。

```kotlin
interface PkgProcs {
    @SqlProcedure(pkg = "SQ_PKG", value = "TWICE") fun twice(value: Double?): Double?
    @SqlProcedure(pkg = "SQ_PKG", value = "HALF")  fun half(value: Double?): Double?    // HALF has two overloads
}

qb.call(PkgProcs::class.java) { it.twice(2.5) }.execute()     // 5.0
qb.call(PkgProcs::class.java) { it.half(10.0) }.execute()     // LambdaQLException LQL-4044
```

<a id="when-the-metadata-cannot-be-read"></a>
**メタデータを読めない場合。** データベースのユーザーが、ディクショナリビューや `information_schema` を読めないことがあります。メタデータの取得そのものが失敗した場合（`SQLException`）、lambdaQL は呼び出しを止めません。

- プロシージャごとに 1 回、`LQL-5005` を `INFO` レベルでログに出し、スタブは照合しません。
- 引数の並びはスタブから取ります: IN と INOUT の引数をスタブの順に、その後に OUT パラメーター（結果の部分の順）、Oracle ではさらに `List` の部分ごとに `REF CURSOR` を 1 つ。そのため、メタデータがないときは、データベースの宣言で OUT パラメーターを IN パラメーターの後に置く必要があります。
- メタデータが必要なチェックは省かれます: 数と `@InOut` のチェック（`LQL-4036`）、名前の順序のチェック（`LQL-4043`）。
- 取得はできたがプロシージャが見つからない場合は、これまでどおり `LQL-4035` です。
- Oracle のオーバーロードの取得（`all_arguments`）だけが失敗した場合は、`LQL-5006` を `INFO` レベルでログに出し、オーバーロードのチェックなしで呼び出します。

この代わりの動作は、MariaDB と Oracle で Java によりテスト済みです（`ProcedureTest` のテストメソッド `withoutMetadata`。失敗を模擬します）。

Oracle での本当の名前によるバインド（`pass_procedure_paramater_names=true` とスタブのパラメーター名の組み合わせ）も予定しています。

---

<a id="85-procedure-rules-pr1-to-pr15-summary"></a>
## 8.5 プロシージャのルール PR1 から PR15（まとめ）

| # | ルール | 状況 |
|---|---|---|
| PR1 | 名前: アノテーションの値をそのまま使います。値が空なら、関数名を UPPER_SNAKE にしたもの。パッケージ名やスキーマ名（`SQ_PKG.CALC`、`LQL.SQ_CALC`）も使えます。混乱を避けるため、`@SqlProcedure(schema = ..., pkg = ..., value = ...)` と書くこともできます。 | 実装済み。スキーマ名とパッケージ名はテストで確認していません |
| PR2 | 引数は **位置で** バインドされます。最初の呼び出しで、IN/INOUT のパラメーターの数と `@InOut` の位置を DB のメタデータと照合します。名前が分かれば名前も照合し、同じ名前が別の順序ならエラーです。型の照合は予定です（[8.4](#84-warning-argument-order) を参照）。メタデータを読めない場合は、チェックを省き、引数の並びをスタブから取ります（[8.4](#when-the-metadata-cannot-be-read) を参照）。 | 数、`@InOut`、名前の照合は実装済み（Java でテスト済み）。Oracle パッケージのオーバーロードの検出も実装済み（Oracle でテスト済み）。型の照合はまだ実装されていない。メタデータなしの代わりの動作（`LQL-5005`、`LQL-5006`）も実装済み（MariaDB と Oracle で Java によりテスト済み）。 |
| PR3 | OUT 1 つ → 戻り値。複数の OUT/INOUT → data class または record。`@InOut` は INOUT パラメーターを示します。 | 実装済み（Java でテスト済み） |
| PR4 | 結果セット 1 つ → `List<T>`。エンティティは Hibernate を通して、data class と record は JDBC を通してラベルで対応付けます。ページングはありません。 | 実装済み（Java で record によりテスト済み） |
| PR5 | 複数の結果セット → data class の `List` プロパティに順番どおり。Oracle の暗黙的な結果はサポートしません。 | 実装済み、テストでは確認していません |
| PR6 | 単独で呼び出す DB 関数は、データベースごとに異なる経路を使います（[8.8](#88-call-a-db-function-alone) を参照）。 | 実装済み（Java でテスト済み） |
| PR7 | クエリの中の DB 関数: [8.6](#86-declare-db-functions) とルール FN1 から FN11 を参照。 | 実装済み（Java でテスト済み） |
| PR8 | 条件の中でカラムに DB 関数を使うと、インデックスが使えないので WARN になります（[8.9](#89-warning-functions-on-columns-and-indexes-sargable) を参照）。コードは `LQL-5001`。 | 実装済み（Java のラムダでテスト済み）。厳格モードはまだ実装されていません |
| PR9 | プロシージャは呼び出し元のトランザクションの中で実行されます。必要なら lambdaQL は呼び出しの前にフラッシュします。呼び出しのあと、読み込まれたエンティティが古くなっていることがあります。 | 実装済み |
| PR10 | 共通の影響行数はありません。OUT パラメーターを使ってください。 | – |
| PR11 | IN の値に `null` を使うのは問題ありません。DB のパラメーターのデフォルトは使えません。スタブ関数の Kotlin のデフォルト引数はエラーです（`LQL-3062`）。 | 実装済み |
| PR12 | ラムダはスタブの呼び出し 1 つです。引数はキャプチャ値、定数、またはそれらの計算です。名前付き引数は単純な値の場合だけです。OUT パラメーターがあるのに `exec` を使うとエラーです。 | 実装済み（Java でテスト済み）。名前付き引数はテストで確認していません |
| PR13 | null を許容しない `Long`/`Double` も、null 許容の `Long?`/`Double?` も使えます。Boolean のパラメーターは MariaDB と Oracle 23ai 以降で使えます。 | テストでは確認していません |
| PR14 | 各ラムダは 1 回だけ解析されます。プロシージャのメタデータは名前ごとに 1 回だけ読まれます。 | 実装済み |
| PR15 | H2 には OUT、INOUT、複数の結果セット、パッケージがありません。プロシージャは MariaDB か Oracle でテストしてください（たとえば Testcontainers を使って）。 | lambdaQL のテストはこれに従っています |

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

lambdaQL のラムダの **外** で `OrderFns.discount(...)` を呼ぶと、`DbOnlyException`（`LQL-4034`）が投げられます。これは意図的です。

`@SqlFunction` には 2 つの値があります。

| 値 | 意味 |
|---|---|
| `value` | クエリの中の関数名。空なら、関数名を UPPER_SNAKE にしたもの（`isVip` → `IS_VIP`）。スキーマ名も使えます（`PUBLIC.SQ_FN`） |
| `sql` | SQL の名前が `value` と違う場合の SQL の名前。このような関数は登録が必要です（[登録](#registration-optional) を参照） |

Java のテストは、関数を Java インターフェースの static メソッドとして宣言しています（[このページの Java 版](../java/08-procedures-functions.md#86-declare-db-functions) を参照）。Kotlin の `companion object` の関数も同じように読みますが、まだテストでは確認していません。

なぜ `@JvmStatic` なのでしょうか。こうすると Java のコードから `OrderFns.discount(...)` を static 関数として呼べ、Java からも Kotlin からも同じ関数に見えるからです。
lambdaQL は `companion object` の関数に付いたアノテーションを読みます。

その他の宣言方法:

| 宣言 | 状況 |
|---|---|
| インターフェースの `companion object` + `@JvmStatic` | **推奨**（実装済み、まだテストでは確認していません） |
| Kotlin の `object` + `@JvmStatic` | 設計済み、テストでは確認していません |
| Java インターフェースの static メソッド（Kotlin から呼び出す） | サポート（Java の書き方はテスト済み） |
| Kotlin のトップレベル関数 | 設計済み、**非推奨**: import を忘れると、同じ名前の標準ライブラリ関数（たとえば `maxOf`）が黙って使われることがあります |
| インターフェースの default 関数 + シングルトン定数 | 設計済み、テストでは確認していません |
| リポジトリが関数インターフェースを実装する（mixin） | 拒否 |
| 関数型インターフェースの定数（`DISCOUNT.apply(x)`）、関数型のプロパティ | 拒否 |
| 拡張関数 | v0.1 では拒否（KC11） |
| デフォルト引数、`inline`、`vararg` を使う `@SqlFunction` | 拒否（FN9: デフォルト引数は `LQL-3056`、`vararg` は `LQL-3057`） |

関数は **インターフェース名を付けて** 呼んでください（`OrderFns.discount`）。関数を import しても動きますが、同じ名前のメンバー関数に隠されることがあります。

---

<a id="87-use-db-functions-in-queries"></a>
## 8.7 クエリで DB 関数を使う

関数を直接呼びます。コンテキスト引数は必要ありません。`where`、`select`、`orderBy`、`set` で使えます（Java のラムダで [`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java) によりテスト済み）。`groupBy`、`having`、サブクエリの中でも使えます（設計済み、テストでは確認していません）。

```kotlin
qb.from(Order::class.java).where { OrderFns.discount(it.price)!! > 10 }.toList()
// → where function('FN_DISCOUNT' as Double, e0.price) > 10

qb.from(Order::class.java).where { OrderFns.isVip(it.customer?.id)!! }.toList()     // Boolean 関数を単独で: ... = true

qb.from(Order::class.java).where { it.price!! > OrderFns.discount(base)!! }.toList()  // カラム側はそのまま

qb.from(Order::class.java).orderBy { o, by -> by.desc(OrderFns.discount(o.price)) }.toList()

qb.from(Order::class.java)
    .select { o -> OrderView(o.id, o.customer?.name, OrderFns.discount(o.price)) }
    .toList()

qb.update(Order::class.java).set { it.price = OrderFns.discount(it.price) }.all().execute()
```

Java のテストは同じ書き方を名前 `SQ_FN_DISCOUNT` で確認しています。たとえば `select e0 from Order e0 where function('SQ_FN_DISCOUNT' as Double, e0.price) > 100.0` です。テストは各クエリを実行し、行を Java での同じ計算と比べます。

**SQL ブロックの読み方。** `…` は省略した select リスト（エンティティのすべてのカラム）で、`?` はバインドパラメーターです。[`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java) は同じ形の HQL（名前は `FN_DISCOUNT`）を Hibernate に直接渡し、H2 2.3、MariaDB 10.11、Oracle 23.26（Hibernate 6.6.5）での SQL を記録します。Hibernate の SQL が変わるとテストは失敗します。

**データベースごとの SQL（この形の HQL に対する Hibernate の SQL）** — `PlannedSqlTest` が記録しました。`expected-sql/` の [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)、例 `08-function`:

```sql
-- H2, MariaDB, Oracle (same)
-- HQL: select o from Order o where function('FN_DISCOUNT' as Double, o.price) > 10
select …
from orders o1_0
where FN_DISCOUNT(o1_0.price)>10
```

テストの設定には `lambdaql.functions.classes` があります。この設定があると、Hibernate は登録していない関数でも名前の大文字・小文字を保ちます（`FN_DISCOUNT`。3 つのデータベースで確認。理由はまだ分かっていません）。登録が何もない場合、Hibernate が名前を小文字で書くことがあります。引用符のない名前はどちらでも動きます。

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

lambdaQL には Hibernate の `FunctionContributor` である `org.lambdaql.hibernate.LambdaQlFunctionContributor`（ServiceLoader で見つけるもの）があります。インターフェースは Hibernate のプロパティに列挙します。

```properties
# 素の Hibernate
lambdaql.functions.classes=com.acme.OrderFns,com.acme.CustomerFns

# Spring Boot
spring.jpa.properties.lambdaql.functions.classes=com.acme.OrderFns
```

プロパティを設定しないと、何も登録されません。パッケージのスキャンは v0.1 にはありません。

<a id="a-different-sql-name-needs-registration"></a>
### SQL の名前が違う関数には登録が必要

`value` はクエリの中の名前で、`sql` は SQL の中の名前です。2 つが違う場合、関数を登録しなければなりません。
これは、引用符を付けて大文字と小文字を混ぜて作った関数名（`"SQ_Fn_Mixed"`）に便利です。登録しないと、H2 と Oracle では名前が小文字になることがあります。

```kotlin
@JvmStatic @SqlFunction(value = "SQ_Fn_Mixed", sql = "\"SQ_Fn_Mixed\"")
fun mixed(v: Double?): Double? = throw DbOnlyException()
```

このような関数が登録されていないと、lambdaQL はラムダを読むときに失敗します（`LQL-3058`）。Java のテストは登録した関数（`value = "SQ_TAX"`、`sql = "SQ_FN_DISCOUNT"`、リージョン `registered`）を確認しています。

---

<a id="88-call-a-db-function-alone"></a>
## 8.8 DB 関数を単独で呼び出す

**引数のないラムダ** で `qb.call { ... }` を使います。

```kotlin
val v: Double? = qb.call { OrderFns.discount(price) }.execute()
```

Java の書き方は H2、MariaDB、Oracle でテスト済みです（`ProcedureTest`、リージョン `function`）。ラムダは `@SqlFunction` の呼び出し 1 つでなければなりません（`LQL-3063`）。

**データベースごとの SQL（この形の HQL に対する Hibernate の SQL）** — 名前 `FN_DISCOUNT` で `PlannedSqlTest` が記録しました。`expected-sql/` の [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)、例 `08-function-alone`:

```sql
-- H2, MariaDB, Oracle (same)
-- HQL: select function('FN_DISCOUNT' as Double, :p1)
select FN_DISCOUNT(?)
```

これは FROM のない `select FN_DISCOUNT(?)` として実行されます。Oracle がこれを受け付けるのは 23ai からです。Oracle では、lambdaQL は代わりに JDBC の関数呼び出しを使います（下の表を参照）。

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

> [!NOTE]
> **実装済み、Java のラムダでテスト済み**（H2、MariaDB 10.11、Oracle 23.26、ルール PR8、FN7）。まだ実装されていないもの: 厳格モード（警告の代わりにエラー）、関数を使わないカラムの算術演算の警告。

lambdaQL は **インデックスを使える** SQL を作ろうとします（[10. ルールと制限](10-rules-and-limits.md#102-sargable-rule-index-friendly-sql) を参照）。
カラムを関数で囲むと、通常のインデックスはたいてい **使えません**。

```kotlin
.where { OrderFns.discount(it.price)!! > 10 }
// → function('FN_DISCOUNT' as Double, e0.price) > 10   -- price のインデックスは使われない
```

**データベースごとの SQL（この形の HQL に対する Hibernate の SQL）** — `PlannedSqlTest` が記録しました。`expected-sql/` の [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)、例 `08-function`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where FN_DISCOUNT(o1_0.price)>10
```

そのため、lambdaQL はこの形に **WARN** を出します。
チェックは `where` のラムダごとに 1 回、解析結果をキャッシュするときに行います。`@SqlFunction` のない条件では、データベースを調べません。

| 場合 | コード | H2 | MariaDB | Oracle |
|---|---|---|---|---|
| ① 関数がカラムを包む: `fn(o.price) > 10`、カラムに対する Boolean の関数、`IN` や `IS NULL` の中の関数 | `LQL-5001`（WARN） | WARN | WARN（ストアド関数はインデックスに使えない） | WARN。**ただし**、`user_ind_expressions` に同じ式 `FN(COLUMN)` の関数ベースインデックスがそのテーブルにあれば出ない |
| ② 値の側の関数をカラムと比べる: `o.price > fn(rate)` | `LQL-5007`（WARN） | 確認しない | `information_schema.routines.is_deterministic = 'NO'`（デフォルト!）なら WARN。このとき MariaDB はインデックス全体をスキャンする | 確認しない（Oracle はどちらでも範囲スキャン） |

- Oracle のインデックスとの照合では、テーブル名とカラム名を Hibernate のマッピングから取ります。カラムの引数が 1 つの関数だけを照合します。
- メタデータの取得が失敗した場合（たとえば権限がない）、`LQL-5008` を `INFO` レベルでログに出します。① はそのまま警告し、② は確認しません。

同じチェックは Java でテストされています（[`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java)、リージョン `sargable`: H2 は `LQL-5001`、MariaDB は `LQL-5001` と `LQL-5007`、関数ベースインデックスがある Oracle はなし）。次は設計上の例です（`nondet` は `discount` と同じ `@SqlFunction` です）。

```kotlin
// the function wraps the column: an index on price cannot be used (LQL-5001 at WARN),
// unless Oracle has a function-based index on SQ_FN_DISCOUNT(price)
qb.from(Order::class.java).where { OrderFns.discount(it.price)!! > 100 }.toList()
// the function is on the value side: fine, unless MariaDB says NOT DETERMINISTIC (LQL-5007 at WARN)
qb.from(Order::class.java).where { it.price!! > OrderFns.nondet(base)!! }.toList()
```

**アドバイス:**

- できるだけ関数を値の側に置いてください: `it.price!! > OrderFns.discount(base)!!`。
- MariaDB では、関数が決定的なら `DETERMINISTIC` と宣言してください。
- Oracle では、同じ式の関数ベースインデックスがあると警告が消えます（このようなインデックスには `DETERMINISTIC` な関数が必要です）。

---

<a id="810-function-rules-fn1-to-fn11-summary"></a>
## 8.10 関数のルール FN1 から FN11（まとめ）

| # | ルール | 状況 |
|---|---|---|
| FN1 | `@SqlFunction` は関数に付けるアノテーションです。値が空なら、関数名を UPPER_SNAKE にしたもの。 | 実装済み |
| FN2 | lambdaQL は呼び出された関数を見つけ、そのアノテーションを読みます。Kotlin では `Companion` のレシーバーを読み飛ばします。本体は例外を投げるだけにします（`= throw ...`）。本当の本体を持つ関数はエラーです（`LQL-3052`）。アプリケーションとデータベースで値が異なるおそれがあるからです。 | 実装済み（Java でテスト済み） |
| FN3 | 同じ名前のオーバーロードは、すべて同じ DB 関数に対応しなければなりません。そうでなければエラーです（`LQL-3053`）。（Kotlin の `Double?` の引数は `Double?` のオーバーロードを選びます。Kotlin から見た Java のエンティティの getter は、プリミティブの `double` のオーバーロードを選ぶことがあります。） | 実装済み |
| FN4 | アノテーションのない呼び出しや、lambdaQL が特定できない呼び出しはエラーです。黙って読み飛ばすことはありません。 | 実装済み |
| FN5 | 引数は [3. 条件](03-where.md) の値のルールに従います。キャプチャ値だけでできた引数でも、バインドされて **データベースで** 評価されます。 | 実装済み（Java でテスト済み） |
| FN6 | `!!` とボクシングの呼び出しは読み飛ばされます。戻り値の型は宣言した関数から決まります。型は `Double`、`Integer`、`Long`、`BigDecimal`、`String`、`LocalDate`、`LocalDateTime`、`Boolean` のいずれかに対応しなければなりません（`LQL-3055`）。 | 実装済み（Java でテスト済み） |
| FN7 | カラムに対する関数は WARN になります（[8.9](#89-warning-functions-on-columns-and-indexes-sargable) を参照）。MariaDB では、値の側の `NOT DETERMINISTIC` な関数も WARN（`LQL-5007`）。 | 実装済み（Java のラムダでテスト済み）。厳格モードはまだ実装されていません |
| FN8 | Hibernate の組み込み関数（`COALESCE`、`ROUND`、`SYSDATE`、...）と同じ名前はエラーです（`LQL-3054`）。Hibernate が黙って組み込み関数を使ってしまうからです。接頭辞かスキーマ名（`PUBLIC.SQ_FN`）を使ってください。 | 実装済み（Java でテスト済み） |
| FN9 | Kotlin: デフォルト引数（`LQL-3056`）、`vararg`（`LQL-3057`）、`inline` はエラーです。 | デフォルト引数と `vararg`: 実装済み。`inline`: 設計済み |
| FN10 | v0.1 にはスカラー関数だけがあります。ユーザー定義の集約関数とウィンドウ関数は v0.2 の予定です。引数のない関数は `fn()` として出力されます。 | スカラー関数は実装済み |
| FN11 | 既知の DB の違い: MariaDB では、VARCHAR を返す関数が文字列リテラルと照合順序の衝突を起こすことがあります。`BigDecimal` の結果のスケールが異なります（MariaDB と Oracle は 0、H2 は 1）。Boolean の結果には Oracle 23ai 以降が必要です。 | プロトタイプで測定 |

ルールの確認（`LQL-4034`、`LQL-3052`、`LQL-3054`、`LQL-3058`、`LQL-3055`）は Java でテスト済みです（[`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java)、リージョン `rules`。[このページの Java 版](../java/08-procedures-functions.md#810-function-rules-fn1-to-fn11-summary) を参照）。

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
| クエリの中の DB 関数 | はい（テスト済み） | はい（テスト済み） | はい（テスト済み） |
| DB 関数の単独呼び出し | はい（テスト済み） | はい（テスト済み） | はい（テスト済み） |
| lambdaQL でのプロシージャ呼び出し | – | はい（テスト済み） | はい（テスト済み） |
| Boolean のパラメーター / 結果 | – | はい | はい（23ai 以降） |

v0.1 では予定していないもの: 動的なプロシージャ名のための文字列ビルダー（v0.2 の候補）、Oracle パッケージのプロシージャのオーバーロードを 1 つ選んで呼ぶこと（オーバーロードの検出は実装済み、`LQL-4044`）、テーブル値関数、プロシージャの結果のページング。

この章のエラーは、`[LQL-xxxx]` コードを持つ `LambdaQLException`（またはそのサブクラス）です: lambdaQL がラムダを読むときは `LQL-3052`〜`LQL-3063`、呼び出しを実行するときは `LQL-4034`〜`LQL-4039`、`LQL-4043`、`LQL-4044`。呼び出しでは `LQL-5004`、`LQL-5005`、`LQL-5006` が `INFO` レベルで出ることもあります。[11. エラーと言語](../11-errors-and-languages.md) を参照してください。

---

[Kotlin トラック](README.md) | 前: [7. 更新、挿入、削除](07-dml.md) | 次: [9. Kotlin の注意点](09-kotlin-notes.md) | [このページの Java 版](../java/08-procedures-functions.md)
