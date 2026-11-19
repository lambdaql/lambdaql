<a id="8-stored-procedures-and-db-functions-kotlin"></a>
# 8. ストアドプロシージャと DB 関数（Kotlin）

[Kotlin トラック](README.md) | [マニュアルのホーム](../README.md) | 前: [7. 更新、挿入、削除](07-dml.md) | 次: [9. Kotlin の注意点](09-kotlin-notes.md) | [このページの Java 版](../java/08-procedures-functions.md)

共通ページ: [1. 概要](../01-overview.md) · [11. エラーと言語](../11-errors-and-languages.md)

---

この章は 2 つの部分に分かれています。

| 部分 | 内容 | アノテーション | 呼び出し方 |
|---|---|---|---|
| [8.1 から 8.5](#81-declare-procedures) | **ストアドプロシージャ** | インターフェースの抽象関数に `@SqlProcedure` | `qb.call(OrderProcs::class.java) { it.calc(id, rate) }.execute()` |
| [8.6 から 8.10](#86-declare-db-functions) | **DB 関数**（スカラー） | インターフェースの `companion object` の中の `@JvmStatic` 関数に `@SqlFunction` | どのラムダの中でも直接呼び出し: `where { OrderFns.discount(it.price)!! > 10 }` |

どちらの場合も、シグネチャは Kotlin のインターフェースに **1 回だけ** 書きます。
lambdaQL がこれらの関数を **実行することはありません**。ラムダのバイトコードにある呼び出しを読み、データベースを呼び出します。

<a id="contents"></a>
## 目次

- [8.1 プロシージャの宣言](#81-declare-procedures)
- [8.2 プロシージャの呼び出し](#82-call-procedures)
- [8.3 OUT、INOUT と結果セット](#83-out-inout-and-result-sets)
- [8.4 注意: 引数の順序](#84-warning-argument-order)
- [8.5 プロシージャのルール PR1 から PR15（まとめ）](#85-procedure-rules-pr1-to-pr15-summary)
- [8.6 DB 関数の宣言](#86-declare-db-functions)
- [8.7 クエリでの DB 関数の使用](#87-use-db-functions-in-queries)
- [8.8 DB 関数の単独呼び出し](#88-call-a-db-function-alone)
- [8.9 注意: カラムに適用した関数とインデックス（SARGable）](#89-warning-functions-on-columns-and-indexes-sargable)
- [8.10 関数のルール FN1 から FN11（まとめ）](#810-function-rules-fn1-to-fn11-summary)
- [8.11 データベースごとの対応](#811-database-support)

---

<a id="81-declare-procedures"></a>
## 8.1 プロシージャの宣言

インターフェースを書いてください。各 **抽象** 関数に `@SqlProcedure("NAME")` を付けてください。
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

注意:

- `@SqlProcedure`（または `@SqlFunction`）は **必須** です。その関数が何であるかを lambdaQL に伝えます。
  （`@Procedure` と `@Function` という名前は使いませんでした。`@Procedure` は Spring Data JPA のアノテーションでもあり、`@Function` は Kotlin の `kotlin.Function` と衝突するからです。）
- 値が空の場合、名前は関数名から作られます: `calcTotal` → `CALC_TOTAL`。
- `@SqlProcedure` には `schema` と `pkg`（Oracle のパッケージ）もあります: `@SqlProcedure(value = "CALC", schema = "LQL", pkg = "SQ_PKG")`。
- **null 許容の戻り値の型**（`Double?`）を使ってください。null を許容しない型にプロシージャが NULL を返すと、lambdaQL はコード `LQL-4039` を持つ `IllegalStateException` を投げます。
- インターフェースと結果の data class は、DB のシグネチャのコピーです。これは型安全のための代価です。lambdaQL はその一部をデータベースと照合します（ルール PR2）。
- プロシージャ（抽象関数）と DB 関数（`companion object` の中）は **同じインターフェース** に置けます。

---

<a id="82-call-procedures"></a>
## 8.2 プロシージャの呼び出し

`qb.call(Interface::class.java) { it.function(args) }` を使ってください。そのあと `execute()` を呼びます。

```kotlin
val d: Double?           = qb.call(OrderProcs::class.java) { it.calc(id, rate) }.execute()
val co: CalcOut          = qb.call(OrderProcs::class.java) { it.calc2(id, rate) }.execute()
val a: AdjOut            = qb.call(OrderProcs::class.java) { it.adjust(id, balance) }.execute()
val r: List<OrderView>   = qb.call(OrderProcs::class.java) { it.recent(custId) }.execute()

// 単純な値を使う名前付き引数
val d2 = qb.call(OrderProcs::class.java) { it.calc(rate = rate, id = id) }.execute()

// 結果なし: exec を使う
qb.exec(OrderProcs::class.java) { it.purge(30) }.execute()
```

結果のない関数に、なぜ `call` ではなく `exec` を使うのでしょうか。

- Java では、両方に `call` を使うとオーバーロードがあいまいになります（コンパイルエラー）。2 つの言語は同じ API を使います。
- `run` は選びませんでした。Kotlin では、引数を書き忘れると `run { }` が黙って標準ライブラリの `run` になってしまうからです。

<a id="what-the-lambda-may-contain-rule-pr12"></a>
### ラムダに書けるもの（ルール PR12）

ラムダ本体は **スタブ関数の呼び出し 1 つ** です。それ以外は書けません。

| 書けるもの | 書けないもの |
|---|---|
| `{ it.calc(id, rate) }` | ログ出力、2 つの呼び出し、`if`、ネストした呼び出し |
| 引数: キャプチャ値、定数、それらの計算（`rate * 2`） | エンティティのパス（ここにはエンティティがありません） |
| **単純な値** を使う名前付き引数（`calc(rate = rate, id = id)`） | 計算を使う名前付き引数（`calc(rate = r * 2, id = id)`）。下を参照 |

計算を含む名前付き引数に対して、Kotlin は隠れたローカル変数を作ります。v0.1 の lambdaQL はローカル変数を拒否します。
この場合は位置引数を使ってください: `it.calc(id, r * 2)`。

その他の点:

- OUT パラメーターを持つプロシージャに `exec` を使うとエラーです（`LQL-4037`、Java でテスト済み）。OUT の値をうっかり失うことを防ぎます。
- `@SqlProcedure` のない関数を呼ぶとエラーです（`LQL-3060`、Java でテスト済み）。
- スタブ関数の **デフォルト引数**（`fun calc(id: Long, rate: Double = 1.0)`）を使う呼び出しはエラーです（`LQL-3062`、ルール PR11）。Kotlin は DB のデフォルト値ではなく Kotlin のデフォルト値を入れてしまうからです。
- パラメーターに対するデータベースのデフォルト値は使えません。すべての IN パラメーターを渡してください。
- IN の値に `null` を使っても構いません。
- null を許容しない `Long`/`Double` のパラメーターも、null 許容の `Long?`/`Double?` のパラメーターも、どちらも使えます（ルール PR13）。

---

<a id="83-out-inout-and-result-sets"></a>
## 8.3 OUT、INOUT と結果セット

戻り値の型とプロシージャの対応（ルール PR3、PR4、PR5）:

| プロシージャが持つもの | スタブ関数の戻り値の型 |
|---|---|
| なし | 戻り値の型なし（`Unit`）→ `qb.exec(...)` で呼び出す |
| OUT パラメーター 1 つ | その値（`Double?`、`Int?`、...） |
| 複数の OUT / INOUT パラメーター | Kotlin の data class（または Java の record） |
| 結果セット 1 つ（MariaDB の `SELECT`、Oracle の `SYS_REFCURSOR` OUT、H2 の `ResultSet` 関数） | `List<T>` |
| 複数の結果セット | `List` プロパティを持つ data class |
| OUT の値と結果セット（MariaDB） | スカラーのプロパティと `List` プロパティを持つ data class |

data class のプロパティの埋め方:

- **スカラーのプロパティ** ← OUT と INOUT のパラメーター（カーソルを除く）、DB の順序で。
- **`List` のプロパティ** ← 結果セット、順番どおりに（Oracle: カーソル OUT パラメーターの順序、MariaDB: 返された結果セットの順序）。
- 数が合わない場合、lambdaQL はエラーを投げます（`LQL-4038`）。
- IN、OUT、IN、INOUT のように混ざった位置でも問題ありません。lambdaQL は DB のメタデータから位置を読みます。

### INOUT

パラメーターに `@InOut` を付けてください。渡した値が **入力** になります。**出力** される値は、返される data class のプロパティです。

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
| Kotlin の data class または Java の record | JDBC の `CallableStatement`。カラムは **ラベルで** 対応付けます。`_` を取り除き、大文字と小文字を区別しないので、`customer_id` は `customerId` に対応します（Java では record でテスト済み） |
| 値 1 つ（`Long?`、`String?`、...） | 各行の最初のカラム |

通常のクラスはここでは行の型として使えません。data class を使ってください。
MariaDB と Oracle は `SELECT *` のカラムを異なる順序で返すので、lambdaQL がプロパティを位置で対応付けることはありません。
よく使う型は変換します（たとえば数値を `Long`/`Int` に、タイムスタンプを `LocalDateTime` に）。

制限:

- プロシージャの結果はページングできません。
- Oracle の暗黙の結果（`DBMS_SQL.RETURN_RESULT`）はサポートしません。`SYS_REFCURSOR` の OUT パラメーターを使ってください。

<a id="transactions-rule-pr9"></a>
### トランザクション（ルール PR9）

- プロシージャの中の DML は、呼び出し元のトランザクションの一部です。一緒にロールバックされます。
- 呼び出しの前に、lambdaQL は永続コンテキストをフラッシュします。ただし、トランザクションが有効で、変更がある場合だけです。
- `QueryBuilder(EntityManagerFactory)` は、呼び出しを自分のトランザクションで実行します。`EntityManager` を受け取った `QueryBuilder`（Spring の共有 `EntityManager` を含む）は、呼び出し元のトランザクションを使います。トランザクションがなければ、呼び出しは自動コミットされます。
- 呼び出しのあと、読み込み済みのエンティティが古い状態になっていることがあります。そのエンティティに `refresh` か `evict` を使ってください。
- プロシージャの中の `COMMIT` や DDL はトランザクションを壊します。lambdaQL はこれを防げません。

<a id="affected-rows-rule-pr10"></a>
### 影響を受けた行数（ルール PR10）

プロシージャには、共通の「影響を受けた行数」の結果がありません（Oracle は -1 を返します）。
その数が必要な場合は、OUT パラメーターとして返してください。

---

<a id="84-warning-argument-order"></a>
## 8.4 注意: 引数の順序

> [!WARNING]
> **スタブ関数のパラメーターの順序は、データベースでの順序と同じでなければなりません。**

lambdaQL は引数を **位置で** バインドします（ルール PR2）。
Hibernate 6.6.5 は、名前を登録しても位置でバインドします。
MariaDB と H2 のドライバーは名前付きパラメーターをサポートしていません。
そのため、順序が間違っていると、**エラーなしで間違った結果** が得られることがあります。

Kotlin の名前付き引数（`calc(rate = rate, id = id)`）を使っても、これは **変わりません**。コンパイラーが引数をスタブ関数の順序に並べ直します。意味があるのはスタブ関数の順序だけです。

最初の呼び出しで lambdaQL が JDBC のメタデータ（`getProcedureColumns`）で確認する項目です。結果はプロシージャ名ごとにキャッシュします。

| 確認項目 | 検出するか |
|---|---|
| プロシージャの存在 | はい、エラー `LQL-4035` |
| IN・INOUT のパラメーターの数 = スタブの引数の数 | はい、エラー `LQL-4036`（Java でテスト済み） |
| INOUT のパラメーター = `@InOut` を付けた引数 | はい、エラー `LQL-4036` |
| OUT の値と結果セットの数 = 戻り値の型の構成要素の数 | はい、エラー `LQL-4038` |
| 型の種類（数値、文字、日付・時刻、真偽値、バイナリ） | はい、エラー `LQL-4036`（Java でテスト済み）。同じ種類の中の違いは照合しない（下の「引数の型の照合」を参照） |
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
- 名前が同じで順序だけが違う場合は、呼び出しの前に `LambdaQLException` `LQL-4043` が発生します。
- 名前が違う場合（スタブが別の命名規則に従う）はエラーにしません。チェックが働くように `@DbParam` を付けてください。
- クラスファイルに名前がなく、隣り合う 2 つの引数の型が同じ場合は、プロシージャごとに 1 回、`LQL-5004` を `INFO` レベルでログに出します。
- 型は種類だけを照合します（下の「引数の型の照合」を参照）。同じ種類の引数 2 つが入れ替わった場合は、名前でしか見つけられません。

同じスタブを Java でテストしました（[`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java)、リージョン `names`、`name-check`、MariaDB と Oracle。[このページの Java 版](../java/08-procedures-functions.md#84-warning-argument-order) を参照）。
Kotlin で名前を残すには `-java-parameters` が必要です。次は設計上の例です。

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

**推奨:** `-java-parameters` でコンパイルしてデータベースと同じ名前を使う（共通の接頭辞は省略できます）か、`@DbParam` を付けてください。そうすれば、入れ替わった順序を検出します。

**引数の型の照合。** 最初の呼び出しで、各引数の型と DB のパラメーターの型の種類を照合します。
種類は数値、文字、日付・時刻、真偽値、バイナリの 5 つで、違う場合は呼び出しの前に `LQL-4036` で止まります。

- 真偽値の引数は、数値のパラメーターにも合うものとみなします。MariaDB の `BOOLEAN` は `TINYINT(1)` で、Oracle 23 より前には `BOOLEAN` 型がないからです。
- 種類を決められない側（enum、オブジェクト型、カーソル）は照合しません。
- 同じ種類の中の違い（`Int?` の引数と `NUMBER(10,2)` のパラメーターなど）は、ドライバーの変換に任せます。

Java のテスト（[`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java)、リージョン `type-check`、MariaDB と Oracle）が、数値のパラメーターに文字列を渡すと `LQL-4036` で止まることを確認しています。

**Oracle パッケージのオーバーロード。** Oracle のパッケージには、同じ名前のプロシージャが複数あることがあります。
lambdaQL はスタブがどれを指すのか区別できないので、そのような名前を呼ぶと `LQL-4044` で止まります。オーバーロードを 1 つ選んで呼ぶ機能は、今後の予定です。

Java でテストしました（[`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java)、リージョン `overload`、Oracle のみ）。次は設計上の例です。

```kotlin
interface PkgProcs {
    @SqlProcedure(pkg = "SQ_PKG", value = "TWICE") fun twice(value: Double?): Double?
    @SqlProcedure(pkg = "SQ_PKG", value = "HALF")  fun half(value: Double?): Double?    // HALF has two overloads
}

qb.call(PkgProcs::class.java) { it.twice(2.5) }.execute()     // 5.0
qb.call(PkgProcs::class.java) { it.half(10.0) }.execute()     // LambdaQLException LQL-4044
```

<a id="when-the-metadata-cannot-be-read"></a>
**メタデータを読めない場合。** データベースのユーザーに、ディクショナリビューや `information_schema` を読む権限がないことがあります。
メタデータの取得そのものが失敗した場合（`SQLException`）、lambdaQL は呼び出しを止めません。

- プロシージャごとに 1 回、`LQL-5005` を `INFO` レベルでログに出し、スタブは照合しません。
- 引数の並びはスタブから取ります。IN と INOUT の引数はスタブの順、その後に OUT パラメーター（結果の構成要素の順）、Oracle ではさらに `List` の構成要素ごとに `REF CURSOR` を 1 つです。そのため、メタデータがないときは、データベースの宣言で OUT パラメーターを IN パラメーターの後に置く必要があります。
- メタデータが必要なチェックは省きます: 数と `@InOut` のチェック（`LQL-4036`）、名前の順序のチェック（`LQL-4043`）。
- 取得はできたがプロシージャが見つからない場合は、これまでどおり `LQL-4035` です。
- Oracle のオーバーロードの取得（`all_arguments`）だけが失敗した場合は、`LQL-5006` を `INFO` レベルでログに出し、オーバーロードのチェックなしで呼び出します。

この代わりの動作は、MariaDB と Oracle で Java によりテストしました（`ProcedureTest` のテストメソッド `withoutMetadata`。失敗を模擬します）。

Oracle での実際の名前によるバインド（`pass_procedure_paramater_names=true` とスタブのパラメーター名の組み合わせ）も予定しています。

---

<a id="85-procedure-rules-pr1-to-pr15-summary"></a>
## 8.5 プロシージャのルール PR1 から PR15（まとめ）

| # | ルール | 状況 |
|---|---|---|
| PR1 | 名前: アノテーションの値をそのまま使う。値が空の場合は関数名を UPPER_SNAKE にしたもの。パッケージ名やスキーマ名（`SQ_PKG.CALC`、`LQL.SQ_CALC`）も使える。混乱を避けるために `@SqlProcedure(schema = ..., pkg = ..., value = ...)` と書くこともできる。 | 実装済み。スキーマ名とパッケージ名はテストで確認していません |
| PR2 | 引数は **位置で** バインドされる。最初の呼び出しで、IN/INOUT のパラメーターの数と `@InOut` の位置を DB のメタデータと照合する。名前が分かれば名前も照合し、同じ名前が別の順序ならエラー。引数の型の種類（数値、文字、日付・時刻、真偽値、バイナリ）も照合し、違えばエラー（`LQL-4036`）（[8.4](#84-warning-argument-order) を参照）。メタデータを読めない場合は、チェックを省き、引数の並びをスタブから取る（[8.4](#when-the-metadata-cannot-be-read) を参照）。 | 数、`@InOut`、名前の照合は実装済み（Java でテスト済み）。Oracle パッケージのオーバーロードの検出も実装済み（Oracle でテスト済み）。型の照合も実装済み（Java でテスト済み）。メタデータなしの代わりの動作（`LQL-5005`、`LQL-5006`）は実装済み（MariaDB、Oracle で Java によりテスト済み）。 |
| PR3 | OUT 1 つ → 戻り値。OUT/INOUT が複数 → data class または record。`@InOut` は INOUT パラメーターを示す。 | 実装済み（Java でテスト済み） |
| PR4 | 結果セット 1 つ → `List<T>`。エンティティは Hibernate で、data class/record はラベルで対応付ける JDBC で読む。ページングなし。 | 実装済み（Java で record によりテスト済み） |
| PR5 | 複数の結果セット → data class の `List` プロパティに順番どおり。Oracle の暗黙の結果はサポートしない。 | 実装済み、テストでは確認していません |
| PR6 | 単独で呼び出す DB 関数は、データベースごとに異なる経路を使う（[8.8](#88-call-a-db-function-alone) を参照）。 | 実装済み（Java でテスト済み） |
| PR7 | クエリの中の DB 関数: [8.6](#86-declare-db-functions) とルール FN1 から FN11 を参照。 | 実装済み（Java でテスト済み） |
| PR8 | 条件の中でカラムに DB 関数を適用すると、インデックスが使えないため WARN を出す（[8.9](#89-warning-functions-on-columns-and-indexes-sargable) を参照）。コードは `LQL-5001`。 | 実装済み（Java のラムダでテスト済み）。`SargableMode.STRICT` ではエラー（`LQL-3066`、D50） |
| PR9 | プロシージャは呼び出し元のトランザクションの中で実行される。lambdaQL は必要なときに呼び出しの前にフラッシュする。呼び出しのあと、読み込み済みのエンティティが古い状態になっていることがある。 | 実装済み |
| PR10 | 共通の影響行数はない。OUT パラメーターを使う。 | – |
| PR11 | IN の値に `null` を使える。DB のパラメーターのデフォルト値は使えない。スタブ関数の Kotlin のデフォルト引数はエラー（`LQL-3062`）。 | 実装済み |
| PR12 | ラムダはスタブの呼び出し 1 つ。引数はキャプチャ値、定数、またはそれらの計算。名前付き引数は単純な値だけ。OUT パラメーターがあるときに `exec` を使うとエラー。 | 実装済み（Java でテスト済み）。名前付き引数はテストで確認していません |
| PR13 | null を許容しない `Long`/`Double` も、null 許容の `Long?`/`Double?` も使える。Boolean のパラメーターは MariaDB と Oracle 23ai 以降で使える。 | テストでは確認していません |
| PR14 | 各ラムダは 1 回だけ解析される。プロシージャのメタデータは名前ごとに 1 回だけ読む。 | 実装済み |
| PR15 | H2 には OUT、INOUT、複数の結果セット、パッケージがない。プロシージャは MariaDB か Oracle でテストしてください（たとえば Testcontainers を使って）。 | lambdaQL のテストもこの方式に従っています |

---

<a id="86-declare-db-functions"></a>
## 8.6 DB 関数の宣言

DB 関数は、データベースに直接作った関数です。たとえば `FN_DISCOUNT(price)` です。
Kotlin のインターフェースには static 関数がないので、インターフェースの **`companion object`** の中で **`@JvmStatic`** と `@SqlFunction` を付けて宣言してください。
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

lambdaQL のラムダの **外** で `OrderFns.discount(...)` を呼ぶと、`DbOnlyException`（`LQL-4034`）が発生します。これは意図した動作です。

`@SqlFunction` の値は 2 つです。

| 値 | 意味 |
|---|---|
| `value` | クエリで使う関数名。空の場合は関数名を UPPER_SNAKE にしたもの（`isVip` → `IS_VIP`）。スキーマ名も使える（`PUBLIC.SQ_FN`） |
| `sql` | `value` と違う場合の SQL の名前。このような関数は登録が必要（[登録](#registration-optional) を参照） |

Java のテストは、関数を Java インターフェースの static メソッドとして宣言しています（[このページの Java 版](../java/08-procedures-functions.md#86-declare-db-functions) を参照）。Kotlin の `companion object` の関数も同じ方法で読みますが、まだテストでは確認していません。

なぜ `@JvmStatic` なのでしょうか。こうすると Java のコードから `OrderFns.discount(...)` を static 関数として呼べるので、Java からも Kotlin からも関数が同じに見えます。
lambdaQL は `companion object` の関数に付いたアノテーションを読みます。

その他の宣言方法:

| 宣言 | 状況 |
|---|---|
| インターフェースの `companion object` + `@JvmStatic` | **推奨**（実装済み、まだテストでは確認していません） |
| Kotlin の `object` + `@JvmStatic` | 設計済み、テストでは確認していません |
| Java インターフェースの static メソッド（Kotlin から呼び出す） | サポート（Java の形はテスト済み） |
| Kotlin のトップレベル関数 | 設計済み、**非推奨**: import を忘れると、同じ名前の標準ライブラリ関数（たとえば `maxOf`）が黙って使われることがあります |
| インターフェースの default 関数 + シングルトンの定数 | 設計済み、テストでは確認していません |
| リポジトリが関数インターフェースを実装する（ミックスイン） | 拒否 |
| 関数型インターフェースの定数（`DISCOUNT.apply(x)`）、関数型のプロパティ | 拒否 |
| 拡張関数 | v0.1 では拒否（KC11） |
| デフォルト引数、`inline`、`vararg` を持つ `@SqlFunction` | 拒否（FN9: デフォルト引数は `LQL-3056`、`vararg` は `LQL-3057`） |

関数は **インターフェース名を付けて** 呼び出してください（`OrderFns.discount`）。関数を import しても動きますが、同じ名前のメンバー関数があると隠れてしまうことがあります。

---

<a id="87-use-db-functions-in-queries"></a>
## 8.7 クエリでの DB 関数の使用

関数を直接呼び出してください。コンテキストの引数は必要ありません。
`where`、`select`、`orderBy`、`set` で使えます（[`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java) で Java のラムダによりテスト済み）。`groupBy`、`having`、サブクエリでも使えるように設計しましたが、テストでは確認していません。

```kotlin
qb.from(Order::class.java).where { OrderFns.discount(it.price)!! > 10 }.toList()
// → where function('FN_DISCOUNT' as Double, e0.price) > 10

qb.from(Order::class.java).where { OrderFns.isVip(it.customer?.id)!! }.toList()     // Boolean 関数を単独で: ... = true

qb.from(Order::class.java).where { it.price!! > OrderFns.discount(base)!! }.toList()  // カラム側は手を加えないまま

qb.from(Order::class.java).orderBy { o, by -> by.desc(OrderFns.discount(o.price)) }.toList()

qb.from(Order::class.java)
    .select { o -> OrderView(o.id, o.customer?.name, OrderFns.discount(o.price)) }
    .toList()

qb.update(Order::class.java).set { it.price = OrderFns.discount(it.price) }.all().execute()
```

Java のテストは同じ形を名前 `SQ_FN_DISCOUNT` で確認しています。例: `select e0 from Order e0 where function('SQ_FN_DISCOUNT' as Double, e0.price) > 100.0`。テストは各クエリを実行し、結果の行を Java で同じ計算をした結果と比べます。

**SQL ブロックの読み方。** `…` は省略した select リスト（エンティティのすべてのカラム）で、`?` はバインドパラメーターです。[`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java) は同じ形の HQL（名前は `FN_DISCOUNT`）を Hibernate に直接渡し、H2 2.3、MariaDB 10.11、Oracle 23.26（Hibernate 6.6.5）で出た SQL を記録します。Hibernate の SQL が変わるとテストは失敗します。

**データベースごとの SQL（この形の HQL に対する Hibernate の SQL）** — `PlannedSqlTest` が記録しました。`expected-sql/` の [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)、例 `08-function`:

```sql
-- H2, MariaDB, Oracle (same)
-- HQL: select o from Order o where function('FN_DISCOUNT' as Double, o.price) > 10
select …
from orders o1_0
where FN_DISCOUNT(o1_0.price)>10
```

テストの設定には `lambdaql.functions.classes` があります。この設定があると、Hibernate は登録していない関数でも名前の大文字・小文字（`FN_DISCOUNT`）を保ちます（3 つのデータベースで観察。原因はまだ確認していません）。
登録が何もない場合、Hibernate が名前を小文字で書くことがあります。引用符のない名前はどちらでも動きます。

`!!` はコンパイラーのためだけのものです（関数が `Double?` を返すため）。lambdaQL はこれを読み飛ばします（ルール FN6）。

<a id="how-lambdaql-renders-it"></a>
### lambdaQL の出力形式

lambdaQL は DB 関数を **常に** 次の形で出力します。

```sql
function('<NAME>' as <Type>, arg1, arg2, ...)
```

`<Type>` は宣言された戻り値の型から決まります。使える型は次のものだけです。

| 使える戻り値の型 |
|---|
| `Double`, `Integer`, `Long`, `BigDecimal`, `String`, `LocalDate`, `LocalDateTime`, `Boolean` |

Kotlin の `Double?`、`Int?`、`Long?`、`Boolean?` と、その null を許容しない形は、これらの名前に対応します。
ジェネリックな戻り値（`fun <X> nvl(a: X, b: X): X`）、エンティティ、コレクション、`Unit` は解析時に拒否されます。

<a id="registration-optional"></a>
### 登録（任意）

`function('FN_DISCOUNT' as Double, ...)` の形では登録は必要ありません（3 つのデータベースで確認済み）。
関数インターフェースを登録すると、Hibernate は名前の大文字・小文字も保ち、引数の数と型も確認します。

lambdaQL には Hibernate の `FunctionContributor` である `org.lambdaql.hibernate.LambdaQlFunctionContributor` があります（ServiceLoader で見つかります）。インターフェースは Hibernate のプロパティに列挙します。

```properties
# Hibernate のみの場合
lambdaql.functions.classes=com.acme.OrderFns,com.acme.CustomerFns

# Spring Boot
spring.jpa.properties.lambdaql.functions.classes=com.acme.OrderFns
```

プロパティを設定しない場合、何も登録されません。パッケージのスキャンは v0.1 にはありません。

<a id="a-different-sql-name-needs-registration"></a>
### SQL の名前が違う関数の登録

`value` はクエリの中の名前で、`sql` は SQL の中の名前です。2 つが違う場合、関数を登録しなければなりません。
引用符で囲んで作った大文字・小文字混在の関数名（`"SQ_Fn_Mixed"`）に便利です。登録しないと、H2 と Oracle では名前が小文字になることがあります。

```kotlin
@JvmStatic @SqlFunction(value = "SQ_Fn_Mixed", sql = "\"SQ_Fn_Mixed\"")
fun mixed(v: Double?): Double? = throw DbOnlyException()
```

このような関数が登録されていない場合、lambdaQL はラムダを読むときに失敗します（`LQL-3058`）。Java のテストは、登録した関数（`value = "SQ_TAX"`、`sql = "SQ_FN_DISCOUNT"`、リージョン `registered`）を確認しています。

---

<a id="88-call-a-db-function-alone"></a>
## 8.8 DB 関数の単独呼び出し

**引数のないラムダ** で `qb.call { ... }` を使ってください。

```kotlin
val v: Double? = qb.call { OrderFns.discount(price) }.execute()
```

Java の形は H2、MariaDB、Oracle でテストしました（`ProcedureTest`、リージョン `function`）。ラムダは `@SqlFunction` の呼び出し 1 つでなければなりません（`LQL-3063`）。

**データベースごとの SQL（この形の HQL に対する Hibernate の SQL）** — `PlannedSqlTest` が名前 `FN_DISCOUNT` で記録しました。`expected-sql/` の [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)、例 `08-function-alone`:

```sql
-- H2, MariaDB, Oracle (same)
-- HQL: select function('FN_DISCOUNT' as Double, :p1)
select FN_DISCOUNT(?)
```

このクエリは FROM のない `select FN_DISCOUNT(?)` として実行されます。Oracle がこれを受け付けるのは 23ai からです。Oracle では、lambdaQL は代わりに JDBC の関数呼び出しを使います（下の表を参照）。

引数の数が異なるので、`qb.call(OrderProcs::class.java) { ... }` と衝突しません。

lambdaQL の実行方法（ルール PR6）:

| データベース | 経路 |
|---|---|
| Oracle | JDBC の関数呼び出し `{? = call FN(?)}`。DML を行う関数や、カーソルを返す関数でも使える。 |
| MariaDB、H2 | HQL の `select function('FN' as T, :p1)` |

lambdaQL は先に保留中の変更をフラッシュするので（PR9 と同じ）、関数は最新の変更を見ます。

---

<a id="89-warning-functions-on-columns-and-indexes-sargable"></a>
## 8.9 注意: カラムに適用した関数とインデックス（SARGable）

lambdaQL は **インデックスを使える** SQL を作ろうとします（[10. ルールと制限](10-rules-and-limits.md#102-sargable-rule-index-friendly-sql) を参照）。
カラムを関数で囲むと、通常のインデックスは普通 **使えません**。

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
| ① 関数がカラムを包む: `fn(o.price) > 10`、カラムに対する Boolean の関数、`IN` や `IS NULL` の中の関数 | `LQL-5001`（WARN） | WARN | WARN（ストアド関数はインデックスに使えない） | WARN。**ただし**、`user_ind_expressions` に同じ式 `FN(COLUMN)` の関数ベースインデックスがそのテーブルにあれば出さない |
| ② 値の側の関数をカラムと比べる: `o.price > fn(rate)` | `LQL-5007`（WARN） | 確認しない | `information_schema.routines.is_deterministic = 'NO'`（デフォルト!）なら WARN。このとき MariaDB はインデックス全体をスキャンする | 確認しない（Oracle はどちらでも範囲スキャン） |

- Oracle のインデックスとの照合では、テーブル名とカラム名を Hibernate のマッピングから取ります。カラムの引数が 1 つの関数だけを照合します。
- メタデータの取得が失敗した場合（たとえば権限がない）、`LQL-5008` を `INFO` レベルでログに出します。① はそのまま警告し、② は確認しません。

同じチェックを Java でテストしました（[`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java)、リージョン `sargable`: H2 は `LQL-5001`、MariaDB は `LQL-5001` と `LQL-5007`、関数ベースインデックスがある Oracle はなし）。次は設計上の例です（`nondet` は `discount` と同じ `@SqlFunction` です）。

```kotlin
// the function wraps the column: an index on price cannot be used (LQL-5001 at WARN),
// unless Oracle has a function-based index on SQ_FN_DISCOUNT(price)
qb.from(Order::class.java).where { OrderFns.discount(it.price)!! > 100 }.toList()
// the function is on the value side: fine, unless MariaDB says NOT DETERMINISTIC (LQL-5007 at WARN)
qb.from(Order::class.java).where { it.price!! > OrderFns.nondet(base)!! }.toList()
```

**SARGable モード**（決定 D50）。`SargableMode` には 3 つの値があります。

- `OFF`: チェックせず、メタデータも取得しません。
- `WARN`（デフォルト）: 警告をログに出します。
- `STRICT`: `WARN` なら警告を出す場合（`LQL-5001`、`LQL-5007`、`LQL-5012`）に、解析が `org.lambdaql.NonSargableException`（`LQL-3066`）で失敗します。

persistence unit ごとに `SargablePolicy.register(emf, SargableMode.STRICT)` で指定し、`SargablePolicy.unregister(emf)` で解除します。Spring では `lambdaql.sargable.mode=STRICT` を使います（[12](12-spring-data-jpa.md) を参照）。
チェックはラムダごとに 1 回、解析結果を共有キャッシュに入れるときに実行されます。そのため、最初のクエリの前にモードを登録してください。

**カラムに対する算術**（決定 D50）。

- カラム 1 つと値だけの `+ - * /`（`it.cnt * 2 > n`）は値の側に移せます。lambdaQL は `LQL-5012` を `WARN` レベルでログに出し、`STRICT` ではエラーです。
- カラム同士の算術（`it.price!! * it.cnt > n`）と `%` は移せないので、`LQL-5013` を `DEBUG` レベルでログに出すだけです。`STRICT` でも許可します。
- 移した形は意味が同じでなければなりません。`it.cnt > n / 2.0` のように小数で割ってください。整数の割り算 `n / 2` は、`>=`、`=`、`<` や負の `n` で違う行を返します。負の数を掛けていた場合は、比較の向きを逆にしてください。`LQL-5012` のメッセージがこれを案内します。
- インデックスがあるかを調べて警告を減らす方式は採用しませんでした。権限とシノニムのせいで「インデックスなし」と「不明」が混ざり、カラムの算術はまれな形だからです。

これに対する Kotlin のテストはありません。Java のテスト（[`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java)、リージョン `sargable-strict`）が `STRICT` モードを確認しています。

**推奨:**

- できるだけ関数を値の側に置いてください: `it.price!! > OrderFns.discount(base)!!`。
- MariaDB では、関数が実際に決定的であれば `DETERMINISTIC` と宣言してください。
- Oracle では、同じ式の関数ベースインデックスがあると警告が消えます（このようなインデックスには `DETERMINISTIC` な関数が必要です）。

---

<a id="810-function-rules-fn1-to-fn11-summary"></a>
## 8.10 関数のルール FN1 から FN11（まとめ）

| # | ルール | 状況 |
|---|---|---|
| FN1 | `@SqlFunction` は関数のアノテーション。値が空の場合は関数名を UPPER_SNAKE にしたもの。 | 実装済み |
| FN2 | lambdaQL は呼び出された関数を見つけ、そのアノテーションを読む。Kotlin では `Companion` のレシーバーを読み飛ばす。本体は例外を投げるだけでなければならない（`= throw ...`）。アプリケーションとデータベースで異なる値になる可能性があるため、本当の本体を持つ関数はエラー（`LQL-3052`）。 | 実装済み（Java でテスト済み） |
| FN3 | 同じ名前のオーバーロードは、すべて同じ DB 関数に対応しなければならない。そうでなければエラー（`LQL-3053`）。（Kotlin の `Double?` の引数は `Double?` のオーバーロードを選ぶ。Kotlin から見た Java のエンティティの getter は、プリミティブの `double` のオーバーロードを選ぶことがある。） | 実装済み |
| FN4 | アノテーションのない呼び出しや、lambdaQL が特定できない呼び出しはエラー。黙って読み飛ばすことはない。 | 実装済み |
| FN5 | 引数は [3. 条件](03-where.md) の値のルールに従う。キャプチャ値だけで作った引数でも、バインドされて **データベースで** 評価される。 | 実装済み（Java でテスト済み） |
| FN6 | `!!` とボクシングの呼び出しは読み飛ばす。戻り値の型は宣言された関数から決まる。`Double`、`Integer`、`Long`、`BigDecimal`、`String`、`LocalDate`、`LocalDateTime`、`Boolean` のいずれかに対応しなければならない（`LQL-3055`）。 | 実装済み（Java でテスト済み） |
| FN7 | カラムに適用した関数は WARN を出す（[8.9](#89-warning-functions-on-columns-and-indexes-sargable) を参照）。MariaDB では、値の側の `NOT DETERMINISTIC` な関数も WARN（`LQL-5007`）。 | 実装済み（Java のラムダでテスト済み）。`SargableMode.STRICT` ではエラー（`LQL-3066`、D50） |
| FN8 | Hibernate の組み込み関数（`COALESCE`、`ROUND`、`SYSDATE`、...）と同じ名前はエラー（`LQL-3054`）。Hibernate が黙って組み込み関数を使うため。接頭辞かスキーマ名（`PUBLIC.SQ_FN`）を使う。 | 実装済み（Java でテスト済み） |
| FN9 | Kotlin: デフォルト引数（`LQL-3056`）、`vararg`（`LQL-3057`）、`inline` はエラー。 | デフォルト引数と `vararg` は実装済み。`inline` は設計済み |
| FN10 | v0.1 はスカラー関数だけをサポート。ユーザー定義の集約関数とウィンドウ関数は v0.2 の予定。引数のない関数は `fn()` として出力される。 | スカラー関数は実装済み |
| FN11 | 既知の DB の違い: MariaDB では、VARCHAR を返す関数が文字列リテラルと照合順序の衝突を起こすことがある。`BigDecimal` の結果のスケールが異なる（MariaDB と Oracle は 0、H2 は 1）。Boolean の結果には Oracle 23ai 以降が必要。 | プロトタイプで実測 |

ルールのチェック（`LQL-4034`、`LQL-3052`、`LQL-3054`、`LQL-3058`、`LQL-3055`）は Java でテストしました（[`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java)、リージョン `rules`。[このページの Java 版](../java/08-procedures-functions.md#810-function-rules-fn1-to-fn11-summary) を参照）。

---

<a id="811-database-support"></a>
## 8.11 データベースごとの対応

| 機能 | H2 2.3 | MariaDB 10.11 | Oracle 23ai |
|---|---|---|---|
| IN パラメーター | はい | はい | はい |
| OUT / INOUT | **いいえ**（H2 の制限） | はい | はい |
| 結果セット 1 つ | はい（`ResultSet` 関数） | はい（プロシージャ内の `SELECT`） | はい（`SYS_REFCURSOR` OUT） |
| 複数の結果セット | いいえ | はい | はい（複数のカーソル OUT パラメーター） |
| OUT の値と結果セットの組み合わせ | いいえ | はい | はい |
| パッケージ名（`PKG.PROC`） | いいえ | いいえ（`sql_mode=ORACLE` が必要） | はい |
| クエリの中の DB 関数 | はい（テスト済み） | はい（テスト済み） | はい（テスト済み） |
| DB 関数の単独呼び出し | はい（テスト済み） | はい（テスト済み） | はい（テスト済み） |
| lambdaQL でのプロシージャ呼び出し | – | はい（テスト済み） | はい（テスト済み） |
| Boolean のパラメーター / 結果 | – | はい | はい（23ai 以降） |

v0.1 では予定していないもの: 動的なプロシージャ名のための文字列ビルダー（v0.2 の候補）、Oracle パッケージのプロシージャのオーバーロードを 1 つ選んで呼ぶ機能（オーバーロードの検出は実装済み、`LQL-4044`）、テーブル値関数、プロシージャの結果のページング。

この章のエラーは、`[LQL-xxxx]` コードを持つ `LambdaQLException`（またはそのサブクラス）です。ラムダを読むときは `LQL-3052`〜`LQL-3063` と、`SargableMode.STRICT` でインデックスを使えない条件に対する `LQL-3066`（`NonSargableException`、[8.9](#89-warning-functions-on-columns-and-indexes-sargable) を参照）です。呼び出しを実行するときは `LQL-4034`〜`LQL-4039`、`LQL-4043`、`LQL-4044` です。呼び出しの間に `LQL-5004`、`LQL-5005`、`LQL-5006` を `INFO` レベルでログに出すこともあります。条件の中の DB 関数については、`LQL-5001`、`LQL-5007`（`WARN`）と `LQL-5008`（`INFO`）をログに出すことがあります。[11. エラーと言語](../11-errors-and-languages.md) を参照してください。

---

[Kotlin トラック](README.md) | 前: [7. 更新、挿入、削除](07-dml.md) | 次: [9. Kotlin の注意点](09-kotlin-notes.md) | [このページの Java 版](../java/08-procedures-functions.md)
