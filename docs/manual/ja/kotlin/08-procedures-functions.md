<a id="8-stored-procedures-and-db-functions-kotlin"></a>
# 8. ストアドプロシージャと DB 関数（Kotlin）

[Kotlin トラック](README.md) | [マニュアルのホーム](../README.md) | 前: [7. 更新、挿入、削除](07-dml.md) | 次: [9. Kotlin の注意点](09-kotlin-notes.md) | [このページの Java 版](../java/08-procedures-functions.md)

共通ページ: [1. 概要](../01-overview.md) · [11. エラーと言語](../11-errors-and-languages.md)

---

この章は 2 つの部分からなります。

| 部分 | 対象 | アノテーション | 呼び出し方 |
|---|---|---|---|
| [8.1〜8.5](#81-declare-procedures) | **ストアドプロシージャ** | インターフェースの抽象関数に `@SqlProcedure` | `qb.call(OrderProcs::class.java) { it.calc(id, rate) }.execute()` |
| [8.6〜8.10](#86-declare-db-functions) | **DB 関数**（スカラー） | インターフェースの `companion object` の中の `@JvmStatic` 関数に `@SqlFunction` | どのラムダ式の中でも直接呼ぶ: `where { OrderFns.discount(it.price)!! > 10 }` |

どちらの場合も、シグネチャは Kotlin のインターフェースに **1 回だけ** 書きます。
これらの関数の本体は呼び出されません。lambdaQL はラムダ式のバイトコードの opcode から呼び出しを読み、データベースのプロシージャや関数を呼びます。

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

<!-- region: ManualKotlinProcedureTest.kt#declare -->
```kotlin
/** Two OUT values: a data class, in the database order. */
data class Stat(val total: Double?, val cnt: Int?)

/** The INOUT value and the OUT value, in the database order. */
data class Adjusted(val balance: Int?, val msg: String?)

/** A row of a result set: columns are matched by name (customer_id or customerId). */
data class Line(val id: Long?, val price: Double?)

/** A stub interface: lambdaQL reads the calls and never implements it. */
interface OrderProcs {
    @SqlProcedure("SQ_PROC_CALC")
    fun calc(customerId: Int, rate: Double): Double?                // IN, IN, OUT (non-null parameters)

    @SqlProcedure("SQ_PROC_STAT")
    fun stat(customerId: Int?): Stat                                // IN, OUT, OUT

    @SqlProcedure("SQ_PROC_ADJUST")
    fun adjust(add: Int?, @InOut balance: Int?): Adjusted           // IN, INOUT, OUT

    @SqlProcedure("SQ_PROC_RECENT")
    fun recent(customerId: Int?): List<Line>                        // a result set (Oracle, PostgreSQL: a cursor)

    @SqlProcedure("SQ_PROC_PURGE")
    fun purge(status: Int?)                                         // no result

    @SqlProcedure("SQ_PROC_CALC")
    fun calcWrong(customerId: Int?): Double?                        // does not match the database: 1 argument
}
```

この章の例は、データベースに作っておいたプロシージャ（`SQ_PROC_CALC` など）を呼びます。例の `Order` は `customer_id` と `price` のカラムを持ちます。

備考:

- `@SqlProcedure`（または `@SqlFunction`）は **必須** です。この関数が何なのかを lambdaQL に伝えます。
  （`@Procedure` と `@Function` という名前は使いませんでした。`@Procedure` は Spring Data JPA のアノテーションでもあり、`@Function` は Kotlin の `kotlin.Function` と衝突します。）
- 値が空なら、名前は関数名から作ります: `calcTotal` → `CALC_TOTAL`。
- Oracle のパッケージのプロシージャは `pkg` で指定します: `@SqlProcedure(pkg = "SQ_PKG", value = "TWICE")`（[8.4](#84-warning-argument-order) の `PkgProcs` を参照）。
- **nullable な戻り値の型**（`Double?`）を使ってください。プロシージャが non-null の型に NULL を返すと、lambdaQL はコード `LQL-4039` の付いた `IllegalStateException` を投げます。
- インターフェースと結果の data class は、DB のシグネチャのコピーです。型安全のための代償です。lambdaQL はその一部をデータベースと照合してチェックします。
- プロシージャ（抽象関数）と DB 関数（`companion object` の中）を、**同じインターフェース** に置けます。

コード例: [`ManualKotlinProcedureTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinProcedureTest.kt)（`declare`）。

---

<a id="82-call-procedures"></a>
## 8.2 プロシージャの呼び出し

`qb.call(Interface::class.java) { it.function(args) }` を使ってください。それから `execute()` を呼びます。

<!-- region: ManualKotlinProcedureTest.kt#call -->
```kotlin
val total: Double? = qb.call(OrderProcs::class.java) { it.calc(customerId, rate) }.execute()
val stat: Stat = qb.call(OrderProcs::class.java) { it.stat(customerId) }.execute()
val recent: List<Line> = qb.call(OrderProcs::class.java) { it.recent(customerId) }.execute()
// named arguments with plain values: the compiler puts them back in the stub order
val same: Double? = qb.call(OrderProcs::class.java) { it.calc(rate = rate, customerId = customerId) }.execute()
```

結果のないプロシージャは `exec` で呼びます。

<!-- region: ManualKotlinProcedureTest.kt#exec -->
```kotlin
// no result: exec, not call
qb.exec(OrderProcs::class.java) { it.purge(9) }.execute()
```

結果のない関数には、なぜ `call` ではなく `exec` を使うのでしょうか。

- `run` は選びませんでした。Kotlin では、引数を書き忘れると `run { }` が黙って標準ライブラリの `run` になってしまいます。

<a id="what-the-lambda-may-contain-rule-pr12"></a>
### ラムダ式に書けるもの（ルール PR12）

ラムダ式の本体は **スタブ関数の呼び出し 1 つ** です。それ以外には何もありません。

| 許可 | 不許可 |
|---|---|
| `{ it.calc(customerId, rate) }` | ログ出力、2 回の呼び出し、`if`、入れ子の呼び出し |
| 引数: キャプチャした値、定数、それらの計算（`rate * 2`） | エンティティのパス（ここにはエンティティがない） |
| **単純な値** を使う名前付き引数（`calc(rate = rate, customerId = customerId)`） | 計算を使う名前付き引数（`calc(rate = r * 2, customerId = customerId)`）、下を参照 |

計算のある名前付き引数に対して、Kotlin は隠れたローカル変数を作ります。lambdaQL はローカル変数を拒否します。
この場合は位置引数を使ってください: `it.calc(customerId, r * 2)`。

その他:

- OUT パラメーターのあるプロシージャに `exec` を使うとエラーです（`LQL-4037`）。うっかり OUT の値を失わないようにします。
- パラメーターのデータベースのデフォルト値は使えません。すべての IN パラメーターを渡してください。
- IN の値に `null` を使うのは問題ありません。
- non-null のパラメーター（`calc` の `Int`、`Double`）も nullable なパラメーター（`stat` の `Int?`）も動きます。

`@SqlProcedure` のない関数を呼ぶと、lambdaQL がラムダ式を読むときにエラーになります（`LQL-3060`）。データベースを照会する前なので、H2 でも確認します。

<!-- region: ManualKotlinProcedureTest.kt#not-annotated -->
```kotlin
// only @SqlProcedure functions of the stub can be called
assertEquals("LQL-3060", assertThrows<LambdaQLException> {
    qb.call(OrderProcs::class.java) { it.toString() }.execute()
}.code())
```

スタブとデータベースの照合、`exec` の OUT のチェック:

<!-- region: ManualKotlinProcedureTest.kt#rules -->
```kotlin
// the stub is checked against the database parameters on the first call
assertEquals("LQL-4036", assertThrows<LambdaQLException> {
    qb.call(OrderProcs::class.java) { it.calcWrong(1) }.execute()
}.code())
// exec cannot drop OUT values
assertEquals("LQL-4037", assertThrows<LambdaQLException> {
    qb.exec(OrderProcs::class.java) { it.calc(1, 1.0) }.execute()
}.code())
```

コード例: [`ManualKotlinProcedureTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinProcedureTest.kt)（`call`、`exec`、`not-annotated`、`rules`）。

---

<a id="83-out-inout-and-result-sets"></a>
## 8.3 OUT、INOUT と結果セット

戻り値の型がプロシージャに対応する方法:

| プロシージャにあるもの | スタブ関数の戻り値の型 |
|---|---|
| なし | 戻り値の型なし（`Unit`）→ `qb.exec(...)` で呼ぶ |
| OUT パラメーター 1 つ | その値（`Double?`、`Int?`、...） |
| OUT / INOUT パラメーター複数 | Kotlin の data class（または Java の record） |
| 結果セット 1 つ（MariaDB・MySQL の `SELECT`、Oracle の `SYS_REFCURSOR` OUT、PostgreSQL の `REFCURSOR` OUT） | `List<T>` |

data class のプロパティを埋める方法:

- **スカラーのプロパティ** ← OUT と INOUT のパラメーター（カーソルを除く）、DB の順に。
- 数が合わなければ、lambdaQL はエラーを投げます（`LQL-4038`）。
- lambdaQL は DB のメタデータからパラメーターの位置を読みます。

### INOUT

パラメーターに `@InOut` を付けてください。渡した値は **入る** 値です。**出る** 値は、返された data class のプロパティです。

<!-- region: ManualKotlinProcedureTest.kt#inout -->
```kotlin
// 10 goes in as the INOUT value; the new balance comes back in the data class
val adjusted: Adjusted = qb.call(OrderProcs::class.java) { it.adjust(5, 10) }.execute()
assertEquals(Adjusted(15, "ok"), adjusted)
```

`@InOut` は、データベースで INOUT の引数にだけ正確に付けなければなりません。そうでなければ、最初の呼び出しが失敗します（`LQL-4036`）。

<a id="result-set-element-type-t"></a>
### 結果セットの要素の型 `T`

| `T` が | lambdaQL の読み方 |
|---|---|
| エンティティ | Hibernate の結果クラス（`StoredProcedureQuery`） |
| Kotlin の data class か Java の record | JDBC の `CallableStatement`。カラムは **ラベルで** 照合。`_` を除き大文字と小文字を無視するので、`customer_id` が `customerId` に対応 |
| 1 つの値（`Long?`、`String?`、...） | 各行の最初のカラム |

普通のクラスは、ここでは行の型として使えません。data class を使ってください。
MariaDB と Oracle は `SELECT *` のカラムを違う順序で返すので、lambdaQL は絶対的な位置でプロパティをマッピングしません。
よくある型は変換します（例: 数値を `Long`/`Int` に、タイムスタンプを `LocalDateTime` に）。

制限:

- プロシージャの結果はページングできません。
- Oracle の暗黙の結果（`DBMS_SQL.RETURN_RESULT`）はサポートしていません。`SYS_REFCURSOR` の OUT パラメーターを使ってください。

<a id="transactions-rule-pr9"></a>
### トランザクション

- プロシージャの中の DML は、呼び出し側のトランザクションの一部です。一緒にロールバックされます。
- 呼び出しの前に、lambdaQL は永続コンテキストをフラッシュします。ただし、トランザクションがアクティブで変更があるときだけです。
- `QueryBuilder(EntityManagerFactory)` は呼び出しを自分のトランザクションで実行します。`EntityManager` を受け取った `QueryBuilder`（Spring の共有 `EntityManager` を含む）は呼び出し側のトランザクションを使います。トランザクションがなければ、呼び出しは自動コミットされます。
- 呼び出しの後、読み込まれたエンティティは古い状態かもしれません。そのエンティティに `refresh` か `evict` を使ってください。
- プロシージャの中の `COMMIT` や DDL はトランザクションを壊します。lambdaQL はこれを防げません。

<a id="affected-rows-rule-pr10"></a>
### 影響を受けた行数（ルール PR10）

プロシージャには、共通の「影響を受けた行数」の結果がありません（Oracle は -1 を返す）。
その数が必要なら、OUT パラメーターで返してください。

コード例: [`ManualKotlinProcedureTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinProcedureTest.kt)（`inout`）。

---

<a id="84-warning-argument-order"></a>
## 8.4 注意: 引数の順序

> [!WARNING]
> **スタブ関数のパラメーターの順序は、データベースの順序と同じでなければなりません。**

lambdaQL は引数を **位置で** バインドします。
Hibernate 6.6.5 は、名前を登録しても位置でバインドします（実測、7.4 は未測定）。
MariaDB と H2 のドライバーは名前付きパラメーターをサポートしていません。
そのため、順序が間違っていると **何のエラーもなく誤った結果** を得ることがあります。

Kotlin の名前付き引数（`calc(rate = rate, id = id)`）はこれを変え **ません**。コンパイラーが引数をスタブ関数の順序に戻します。重要なのはスタブ関数の順序だけです。

最初の呼び出しのときに、lambdaQL が JDBC のメタデータ（`getProcedureColumns`）でチェックする項目です。結果はプロシージャ名ごとにキャッシュします。

| チェック | 検出 |
|---|---|
| プロシージャの存在 | はい、エラー `LQL-4035` |
| IN・INOUT パラメーターの数 = スタブの引数の数 | はい、エラー `LQL-4036` |
| INOUT パラメーター = `@InOut` を付けた引数 | はい、エラー `LQL-4036` |
| OUT の値と結果セットの数 = 戻り値の型の構成要素の数 | はい、エラー `LQL-4038` |
| 型の種類（数値、文字、日付・時刻、ブール、バイナリ） | はい、エラー `LQL-4036`。同じ種類の中の違いは照合しない（下の「引数の型の照合」を参照） |
| 同じパラメーター名が違う順序に置かれている | はい、エラー `LQL-4043`。スタブの引数名がわかるときだけ（下を参照） |
| Oracle のパッケージのプロシージャ名にオーバーロードが複数 | はい、エラー `LQL-4044` |

たとえば、データベースの `SQ_PROC_DIFF(p_to, p_from)` に対してスタブを `fun swapped(from: Int?, to: Int?)` と宣言すると、引数が入れ替わります。
数、モード、型はすべて一致します。間違いを明らかにできるのは名前だけです。
そのため lambdaQL は、最初の呼び出しのときに IN と INOUT の引数の名前を比べます。

- スタブの引数名はクラスファイルから読みます。kotlinc は `-java-parameters` でコンパイルした場合にだけ名前を残します（[Kotlin コンパイラーの設定](02-getting-started.md#kotlin-compiler-settings) を参照）。スタブの引数に `@org.lambdaql.DbParam("p_to")` を付けると、コンパイルオプションなしで DB の名前を直接指定できます。
- DB の名前から、**すべての** 名前に共通で `_` で終わる接頭辞（`p_`、`in_`、`v_` など）を取り除きます。決まった接頭辞を前提にはしません。続けて、大文字と小文字と `_` を無視します。
- 名前が同じで順序だけが違うと、呼び出しの前に `LambdaQLException` の `LQL-4043` が発生します。
- 名前が違う（スタブが別の命名規則に従っている）場合はエラーではありません。チェックが動くように `@DbParam` を付けてください。
- クラスファイルに名前がなく、隣り合う 2 つの引数の型が同じなら、プロシージャごとに 1 回 `LQL-5004` を `INFO` レベルでログに出します。
- 型は種類だけを照合します（下の「引数の型の照合」を参照）。同じ種類の 2 つの引数が入れ替わった場合は、名前でしか見つけられません。

`-java-parameters` なしでコンパイルする場合は、`@DbParam` で名前を指定してください。下のスタブがその例です。

<!-- region: ManualKotlinProcedureTest.kt#names -->
```kotlin
/** kotlinc keeps argument names only with -java-parameters; @DbParam gives the database name without it. */
interface DiffProcs {
    @SqlProcedure("SQ_PROC_DIFF")
    fun diff(@DbParam("p_to") to: Int?, @DbParam("p_from") from: Int?): Int?        // the database order: runs

    @SqlProcedure("SQ_PROC_DIFF")
    fun swapped(@DbParam("p_from") from: Int?, @DbParam("p_to") to: Int?): Int?     // the same names, other order

    @SqlProcedure("SQ_PROC_DIFF")
    fun text(to: String?, from: Int?): Int?                                         // a string for a number parameter
}
```

<!-- region: ManualKotlinProcedureTest.kt#name-check -->
```kotlin
assertEquals(7, qb.call(DiffProcs::class.java) { it.diff(10, 3) }.execute())
// the same names in another order: stopped before the call (it would run with the values swapped)
assertEquals("LQL-4043", assertThrows<LambdaQLException> {
    qb.call(DiffProcs::class.java) { it.swapped(3, 10) }.execute()
}.code())
// the kind of each argument is compared with the database parameter: a string for a number parameter stops
assertEquals("LQL-4036", assertThrows<LambdaQLException> {
    qb.call(DiffProcs::class.java) { it.text("10", 3) }.execute()
}.code())
```

**推奨:** `-java-parameters` でコンパイルして DB と同じ名前を使う（共通の接頭辞は省略可）か、`@DbParam` を付けてください。そうすれば、入れ替わった順序を検出します。

**引数の型の照合。** 最初の呼び出しのときに、各引数の型と DB のパラメーターの型の種類を照合します。
種類は数値、文字、日付・時刻、ブール、バイナリの 5 つで、違えば呼び出しの前に `LQL-4036` で中断します。上の例の `text` がこの場合です。

- ブールの引数は、数値のパラメーターにも合うとみなします。MariaDB の `BOOLEAN` は `TINYINT(1)` で、Oracle 23 より前には `BOOLEAN` 型がありません。
- 種類を決められない側（enum、オブジェクト型、カーソル）は照合しません。
- 同じ種類の中の違い（`Int?` の引数と `NUMBER(10,2)` のパラメーターなど）は、ドライバーの変換に任せます。

**Oracle のパッケージのオーバーロード。** Oracle のパッケージには、同じ名前のプロシージャが複数あることがあります。
lambdaQL はスタブがどれを指すのか区別できないので、そのような名前を呼ぶと `LQL-4044` で中断します（Oracle だけ）。

<!-- region: ManualKotlinProcedureTest.kt#package -->
```kotlin
/** Oracle package procedures: TWICE has one form, HALF has two (an overload). */
interface PkgProcs {
    @SqlProcedure(pkg = "SQ_PKG", value = "TWICE")
    fun twice(value: Double?): Double?

    @SqlProcedure(pkg = "SQ_PKG", value = "HALF")
    fun half(value: Double?): Double?
}
```

<!-- region: ManualKotlinProcedureTest.kt#overload -->
```kotlin
assertEquals(5.0, qb.call(PkgProcs::class.java) { it.twice(2.5) }.execute()!!, 1e-9)
// HALF has two overloads in the package: lambdaQL cannot tell which one to call
assertEquals("LQL-4044", assertThrows<LambdaQLException> {
    qb.call(PkgProcs::class.java) { it.half(10.0) }.execute()
}.code())
```

<a id="when-the-metadata-cannot-be-read"></a>
**メタデータを読めない場合。** DB ユーザーに、ディクショナリビューや `information_schema` を読む権限がないことがあります。
メタデータの照会そのものが失敗すると（`SQLException`）、lambdaQL は呼び出しを中断しません。

- プロシージャごとに 1 回 `LQL-5005` を `INFO` レベルでログに出し、スタブを照合しません。
- 引数の並びはスタブから取ります。IN と INOUT の引数はスタブの順、その後に OUT パラメーター（結果の構成要素の順）、Oracle ではその後に `List` の構成要素ごとに `REF CURSOR` 1 つです。そのため、メタデータがない場合は、DB の宣言で OUT パラメーターが IN パラメーターの後になければなりません。
- メタデータが必要なチェックは飛ばします: 数と `@InOut` のチェック（`LQL-4036`）、名前の順序のチェック（`LQL-4043`）。
- 照会は成功したがプロシージャがない場合は、やはり `LQL-4035` です。
- Oracle のオーバーロードの照会（`all_arguments`）だけが失敗すると、`LQL-5006` を `INFO` レベルでログに出し、オーバーロードを確認せずに呼びます。

コード例: [`ManualKotlinProcedureTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinProcedureTest.kt)（`names`、`name-check`、`package`、`overload`）。

---

<a id="85-procedure-rules-pr1-to-pr15-summary"></a>
## 8.5 プロシージャのルール PR1 から PR15（まとめ）

| # | ルール | 状態 |
|---|---|---|
| PR1 | 名前: アノテーションの値をそのまま使う。値が空なら関数名を UPPER_SNAKE に。Oracle のパッケージは `@SqlProcedure(pkg = ..., value = ...)`。 | 実装済み |
| PR2 | 引数は **位置で** バインドされる。最初の呼び出しのときに、IN/INOUT パラメーターの数と `@InOut` の位置を DB のメタデータと照合する。名前がわかれば名前も照合し、同じ名前が違う順序ならエラー。引数の型の種類（数値、文字、日付・時刻、ブール、バイナリ）も照合し、違えばエラー（`LQL-4036`）（[8.4](#84-warning-argument-order) を参照）。メタデータを読めなければチェックを飛ばし、引数の並びをスタブから取る（[8.4](#when-the-metadata-cannot-be-read) を参照）。 | 数、`@InOut`、名前の照合は実装済み。Oracle のパッケージのオーバーロードの検出も実装済み。型の照合も実装済み。メタデータなしの代替動作（`LQL-5005`、`LQL-5006`）は実装済み（MariaDB）。 |
| PR3 | OUT 1 つ → 戻り値。OUT/INOUT 複数 → data class か record。`@InOut` は INOUT パラメーターを示す。 | 実装済み |
| PR4 | 結果セット 1 つ → `List<T>`。エンティティは Hibernate で、data class/record はラベルで照合する JDBC で。ページングなし。 | 実装済み |
| PR5 | Oracle の暗黙の結果はサポートしない。`SYS_REFCURSOR` の OUT パラメーターを使う。 | – |
| PR6 | 単独で呼んだ DB 関数は、データベースごとに違う経路を使う（[8.8](#88-call-a-db-function-alone) を参照）。 | 実装済み |
| PR7 | クエリの中の DB 関数: [8.6](#86-declare-db-functions) とルール FN1〜FN11 を参照。 | 実装済み |
| PR8 | 条件でカラムに DB 関数を適用するとインデックスを使えないので、WARN をログに出す（[8.9](#89-warning-functions-on-columns-and-indexes-sargable) を参照）。コードは `LQL-5001`。 | 実装済み。`SargableMode.STRICT` ではエラー（`LQL-3066`） |
| PR9 | プロシージャは呼び出し側のトランザクションで実行される。lambdaQL は必要なとき呼び出しの前にフラッシュする。呼び出しの後、読み込まれたエンティティは古い状態かもしれない。 | 実装済み |
| PR10 | 共通の影響を受けた行数はない。OUT パラメーターを使う。 | – |
| PR11 | `null` の IN の値は問題ない。DB のパラメーターのデフォルト値は使えない。 | 実装済み |
| PR12 | ラムダ式はスタブの呼び出し 1 つ。引数はキャプチャした値、定数、またはそれらの計算。名前付き引数は単純な値だけ。OUT パラメーターがあるのに `exec` を使うとエラー。 | 実装済み |
| PR13 | non-null のパラメーター（`Int`、`Double`）も nullable なパラメーター（`Int?`、`Double?`）も動く。 | 実装済み |
| PR14 | 各ラムダ式は 1 回だけ解析される。プロシージャのメタデータは名前ごとに 1 回だけ読む。 | 実装済み |
| PR15 | H2 には OUT、INOUT、パッケージがない。プロシージャは MariaDB、MySQL、PostgreSQL、Oracle のようなデータベースでテストしてください（例: Testcontainers を使う）。 | – |

---

<a id="86-declare-db-functions"></a>
## 8.6 DB 関数の宣言

DB 関数は、データベースに自分で作った関数です。たとえば `SQ_FN_DISCOUNT(price)` です。
Kotlin のインターフェースには static 関数がないので、インターフェースの **`companion object`** の中に **`@JvmStatic`** と `@SqlFunction` を付けて宣言してください。
本体は例外を投げるだけでなければなりません。本体は呼び出されません。ラムダ式の外で直接呼ぶと例外を投げます。

<!-- region: ManualKotlinFunctionTest.kt#declare -->
```kotlin
/** Database functions: functions in the companion object of your own interface. The bodies only throw. */
interface OrderFns {
    companion object {
        /** `SQ_FN_DISCOUNT(price)`: 90% of the price. */
        @JvmStatic
        @SqlFunction("SQ_FN_DISCOUNT")
        fun discount(price: Double?): Double? = throw DbOnlyException()

        /** `SQ_FN_IS_GOLD(grade)`: a grade of 5 or more. */
        @JvmStatic
        @SqlFunction("SQ_FN_IS_GOLD")
        fun isGold(grade: Int?): Boolean? = throw DbOnlyException()
    }
}
```

例では、データベースに作っておいた 2 つの関数を使います（H2 は `CREATE ALIAS` で作る）。`SQ_FN_DISCOUNT` は価格の 90%、`SQ_FN_IS_GOLD` はグレードが 5 以上なら真です。

lambdaQL のラムダ式の **外で** `OrderFns.discount(...)` を呼ぶと、`DbOnlyException`（`LQL-4034`）が発生します。意図した動作です（[8.10](#810-function-rules-fn1-to-fn11-summary) を参照）。

`@SqlFunction` の値は 2 つです。

| 値 | 意味 |
|---|---|
| `value` | クエリで使う関数名。空なら関数名を UPPER_SNAKE に（`isGold` → `IS_GOLD`）。スキーマ名も可（`PUBLIC.SQ_FN`） |
| `sql` | `value` と違うときの SQL の名前。このような関数は登録が必要（[登録](#registration-optional) を参照） |

なぜ `@JvmStatic` なのでしょうか。そうすると Java のコードが `OrderFns.discount(...)` を static 関数として呼べ、Java と Kotlin の呼び出し側に関数がまったく同じに見えます。
lambdaQL は、`companion object` の関数に付いたアノテーションを読みます。

ほかの宣言方法:

| 宣言 | 状態 |
|---|---|
| インターフェースの `companion object` + `@JvmStatic` | **推奨** |
| Kotlin の `object` + `@JvmStatic` | 動く |
| Java のインターフェースの static メソッド（Kotlin から呼ぶ） | 動く |
| Kotlin のトップレベル関数 | 動く、**推奨しない**: import を忘れると、同じ名前の標準ライブラリの関数（例: `maxOf`）が黙って使われることがある |
| 関数型の値（`val fn: (Double?) -> Double?`） | 拒否（`LQL-3008`） |
| デフォルト引数、`vararg` のある `@SqlFunction` | 拒否（デフォルト引数は `LQL-3056`、`vararg` は `LQL-3057`） |

`object`、トップレベル関数、Java の static メソッドは同じ HQL を作ります。

<!-- region: ManualKotlinFunctionTest.kt#other-forms -->
```kotlin
// a Kotlin object with @JvmStatic, and a top-level function: the same HQL
val inObject = qb.from(Order::class.java).where { PriceFns.discount(it.price)!! > 100 }
val topLevel = qb.from(Order::class.java).where { manualKotlinTopLevelDiscount(it.price)!! > 100 }
assertEquals(inObject.toHql().hql(), topLevel.toHql().hql())
```

関数は **インターフェース名と一緒に** 呼んでください（`OrderFns.discount`）。関数を import しても動きますが、同じ名前のメンバー関数がその関数を隠すことがあります。

コード例: [`ManualKotlinFunctionTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinFunctionTest.kt)（`declare`、`other-forms`）。

---

<a id="87-use-db-functions-in-queries"></a>
## 8.7 クエリでの DB 関数の使用

関数を直接呼んでください。コンテキストの引数は要りません。
`where`、`select`、`orderBy`、`set` で動きます。

<!-- region: ManualKotlinFunctionTest.kt#where -->
```kotlin
// the function call is written as function('<name>' as <type>, ...); the column is its argument
val q = qb.from(Order::class.java).where { OrderFns.discount(it.price)!! > 100 }
assertEquals("select e0 from Order e0 where function('SQ_FN_DISCOUNT' as Double, e0.price) > 100.0", q.toHql().hql())
```

Boolean を返す関数は、単独で条件になります（`... = true`）。

<!-- region: ManualKotlinFunctionTest.kt#boolean -->
```kotlin
val q = qb.from(Order::class.java).where { OrderFns.isGold(it.customer!!.grade)!! }
assertEquals("select e0 from Order e0 join e0.customer j1 where function('SQ_FN_IS_GOLD' as Boolean, j1.grade) = true",
    q.toHql().hql())
```

キャプチャした値だけを引数にすると、カラム側はきれいなままで、関数はバインドした値でデータベースで計算されます。

<!-- region: ManualKotlinFunctionTest.kt#captured -->
```kotlin
// a function of captured values only is still computed by the database (the value is bound)
val q = qb.from(Order::class.java).where { it.price > OrderFns.discount(base)!! }
assertEquals("select e0 from Order e0 where e0.price > function('SQ_FN_DISCOUNT' as Double, :p1)", q.toHql().hql())
```

`select` と `orderBy`（`Priced` は `data class Priced(val id: Long?, val discounted: Double?)`）:

<!-- region: ManualKotlinFunctionTest.kt#select -->
```kotlin
val q = qb.from(Order::class.java)
    .orderBy { o -> desc(OrderFns.discount(o.price)) }
    .select { Priced(it.id, OrderFns.discount(it.price)) }
assertEquals("select e0.id, function('SQ_FN_DISCOUNT' as Double, e0.price) from Order e0"
    + " order by function('SQ_FN_DISCOUNT' as Double, e0.price) desc nulls last", q.toHql().hql())
```

`set`:

<!-- region: ManualKotlinFunctionTest.kt#update -->
```kotlin
val u = qb.update(Order::class.java)
    .set { it.price = OrderFns.discount(it.price)!! }
    .where { it.price > 100 }
assertEquals("update Order e0 set e0.price = function('SQ_FN_DISCOUNT' as Double, e0.price) where e0.price > 100.0",
    u.toHql().hql())
```

`lambdaql.functions.classes` の設定があると、Hibernate は登録していない関数でも名前の大文字と小文字（`FN_DISCOUNT`）を保ちます（3 つのデータベースで観察、原因はまだ確認していない）。
登録がまったくないと、Hibernate が名前を小文字で書くことがあります。引用符のない名前はどちらでも動きます。

`!!` はコンパイラーのためのものにすぎません（関数が `Double?` を返すため）。lambdaQL はこれを飛ばします。

<a id="how-lambdaql-renders-it"></a>
### lambdaQL の出力形式

lambdaQL は DB 関数を **常に** 次の形で出力します。

```text
function('<NAME>' as <Type>, arg1, arg2, ...)
```

`<Type>` は、宣言された戻り値の型から来ます。次の型だけが許されます。

| 許される戻り値の型 |
|---|
| `Double`、`Integer`、`Long`、`BigDecimal`、`String`、`LocalDate`、`LocalDateTime`、`Boolean` |

Kotlin の `Double?`、`Int?`、`Long?`、`Boolean?` とその non-null の形は、これらの名前に対応します。
ジェネリックな戻り値（`fun <X> nvl(a: X, b: X): X`）、エンティティ、コレクション、`Unit` は解析時に拒否されます。

<a id="registration-optional"></a>
### 登録（任意）

`function('FN_DISCOUNT' as Double, ...)` の形には登録は要りません（3 つのデータベースで確認済み）。
関数のインターフェースを登録すると、Hibernate が名前の大文字と小文字も保ち、引数の数と型もチェックします。

lambdaQL には、Hibernate の `FunctionContributor` である `org.lambdaql.hibernate.LambdaQlFunctionContributor` があります（ServiceLoader で見つかる）。インターフェースは Hibernate のプロパティに並べます。

```properties
# Hibernate のみ
lambdaql.functions.classes=com.acme.OrderFns,com.acme.CustomerFns

# Spring Boot
spring.jpa.properties.lambdaql.functions.classes=com.acme.OrderFns
```

プロパティを設定しなければ、何も登録されません。パッケージのスキャンはサポートしていません。

<a id="a-different-sql-name-needs-registration"></a>
### SQL の名前が違う関数の登録

`value` はクエリの名前で、`sql` は SQL の名前です。2 つの名前が違うなら、関数を登録しなければなりません。
引用符で囲んで大文字と小文字を混ぜて作った関数名（`"SQ_Fn_Mixed"`）に便利です。登録しないと、H2 と Oracle で名前が小文字になることがあります。

このような関数が登録されていないと、lambdaQL はラムダ式を読むときに失敗します（`LQL-3058`）。[8.10](#810-function-rules-fn1-to-fn11-summary) の `BadFns.unregistered`（`value = "SQ_TAX_KT"`、`sql = "SQ_FN_DISCOUNT"`）がこの場合です。登録の方法は [登録](#registration-optional) を参照してください。

コード例: [`ManualKotlinFunctionTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinFunctionTest.kt)（`where`、`boolean`、`captured`、`select`、`update`）。

---

<a id="88-call-a-db-function-alone"></a>
## 8.8 DB 関数の単独呼び出し

**引数のないラムダ式** と一緒に `qb.call { ... }` を使ってください。

<!-- region: ManualKotlinFunctionTest.kt#call -->
```kotlin
// a function on its own: a lambda without parameters
val discounted: Double? = qb.call { OrderFns.discount(price) }.execute()
```

ラムダ式は `@SqlFunction` の呼び出し 1 つでなければなりません（`LQL-3063`）。

このクエリは FROM なしで `select FN_DISCOUNT(?)` として実行されます。Oracle は 23ai 以降でしかこれを受け付けません。Oracle では、lambdaQL は代わりに JDBC の関数呼び出しを使います（下の表を参照）。

引数の数が違うので、`qb.call(OrderProcs::class.java) { ... }` とは衝突しません。

lambdaQL の実行方法:

| データベース | 経路 |
|---|---|
| Oracle | JDBC の関数呼び出し `{? = call FN(?)}`。DML を行う関数とカーソルを返す関数でも動く。 |
| MariaDB、H2 | HQL の `select function('FN' as T, :p1)` |

lambdaQL は先に保留中の変更をフラッシュするので（プロシージャの呼び出しと同じ）、関数は最新の変更を見ます。

コード例: [`ManualKotlinFunctionTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinFunctionTest.kt)（`call`）。

---

<a id="89-warning-functions-on-columns-and-indexes-sargable"></a>
## 8.9 注意: カラムに適用した関数とインデックス（SARGable）

lambdaQL は **インデックスを使える** SQL を作ろうとします（[10. ルールと制限](10-rules-and-limits.md#102-sargable-rule-index-friendly-sql) を参照）。
カラムを包む関数は、普通は通常のインデックスを使え **ません**。

[8.7](#87-use-db-functions-in-queries) の `where { OrderFns.discount(it.price)!! > 100 }` は `function('SQ_FN_DISCOUNT' as Double, e0.price) > 100.0` になり、`price` のインデックスを使いません。

そのため lambdaQL は、この形に **WARN** をログに出します。
チェックは `where` のラムダ式ごとに 1 回、解析の結果をキャッシュするときに行います。`@SqlFunction` のない条件は DB を照会しません。

| 場合 | コード | H2 | MariaDB | Oracle |
|---|---|---|---|---|
| ① 関数がカラムを包む: `fn(o.price) > 10`、カラムに適用した Boolean 関数、`IN` や `IS NULL` の中の関数 | `LQL-5001`（WARN） | WARN | WARN（ストアド関数はインデックスに使えない） | WARN。**ただし**、`user_ind_expressions` に同じ式 `FN(COLUMN)` の関数ベースのインデックスがそのテーブルにあればログに出さない。カラムの型が関数の引数の型と違うために Oracle が入れた数値変換（`FN(TO_NUMBER(COLUMN))` など）も同じ式とみなす |
| ② 値の側の関数をカラムと比較: `o.price > fn(rate)` | `LQL-5007`（WARN） | チェックしない | `information_schema.routines.is_deterministic = 'NO'`（デフォルト!）なら WARN。このとき MariaDB はインデックス全体をスキャンする | チェックしない（Oracle はどちらでも範囲スキャン） |

- Oracle のインデックスの照合に使うテーブル名・カラム名は、Hibernate のマッピングから取ります。カラムの引数が 1 つの関数だけを照合します。
- メタデータの照会が失敗すると（例: 権限なし）、`LQL-5008` を `INFO` レベルでログに出します。① はそのまま警告し、② はチェックしません。

下の例がログに出す警告のコードは、データベースによって違います。H2 は `LQL-5001`、MariaDB と MySQL は `LQL-5001` と `LQL-5007`、関数ベースのインデックスがある PostgreSQL は `LQL-5007`、関数ベースのインデックスがある Oracle は警告なしです。`CheckFns.nondet` は `discount` と同じ形の `@SqlFunction`（`SQ_FN_NONDET`）で、MariaDB と MySQL では `NOT DETERMINISTIC`、PostgreSQL では `VOLATILE` と宣言した関数です。

<!-- region: ManualKotlinFunctionTest.kt#sargable -->
```kotlin
// the function wraps the column: an index on price cannot be used (LQL-5001 at WARN),
// unless Oracle or PostgreSQL has a function-based index on SQ_FN_DISCOUNT(price)
qb.from(Order::class.java).where { OrderFns.discount(it.price)!! > 100 }.toHql()
// the function is on the value side: fine, unless the database says it is not deterministic (LQL-5007 at WARN)
qb.from(Order::class.java).where { it.price > CheckFns.nondet(base)!! }.toHql()
```

**SARGable モード**。`SargableMode` には 3 つの値があります。

- `OFF`: チェックせず、メタデータも照会しません。
- `WARN`（デフォルト）: 警告をログに出します。
- `STRICT`: `WARN` なら警告をログに出す場合（`LQL-5001`、`LQL-5007`、`LQL-5012`）に、解析が `org.lambdaql.NonSargableException`（`LQL-3066`）で失敗します。

永続化ユニットごとに `SargablePolicy.register(emf, SargableMode.STRICT)` で指定し、`SargablePolicy.unregister(emf)` で解除します。Spring では `lambdaql.sargable.mode=STRICT` を使います（[12](12-spring-data-jpa.md) を参照）。
チェックはラムダ式ごとに 1 回、解析の結果を共有キャッシュに入れるときに実行されます。そのため、最初のクエリの前にモードを登録してください。

**カラムに対する算術**。

- カラム 1 つと値だけからなる `+ - * /`（`it.cnt * 2 > n`）は、値の側に移せます。lambdaQL は `LQL-5012` を `WARN` レベルでログに出し、`STRICT` ではエラーです。
- カラムどうしの算術（`it.price!! * it.cnt > n`）と `%` は移せないので、`LQL-5013` を `DEBUG` レベルでだけログに出します。`STRICT` でも許可します。
- 移した形は意味が同じでなければなりません。`it.cnt > n / 2.0` のように小数で割ってください。整数の割り算 `n / 2` は、`>=`、`=`、`<` と負の `n` で別の行を返します。負の数を掛けたときは比較の向きを逆にしてください。`LQL-5012` のメッセージがこれを案内します。
- インデックスがあるかを照会して警告を減らす方法は採用しませんでした。権限とシノニムのために「インデックスなし」と「不明」が混ざり、カラムの算術はまれな形だからです。

`STRICT` モードとカラムの算術:

<!-- region: ManualKotlinFunctionTest.kt#sargable-strict -->
```kotlin
// STRICT: what WARN would log fails the analysis (NonSargableException, LQL-3066)
SargablePolicy.register(emf, SargableMode.STRICT)
val fn = assertThrows<NonSargableException> {
    qb.from(Order::class.java).where { OrderFns.discount(it.total)!! > 100 }.toHql()
}
assertEquals("LQL-3066", fn.code())
assertThrows<NonSargableException> { qb.from(Order::class.java).where { it.cnt!! * 2 > n }.toHql() }
// the same meaning on the value side uses the index; divide by a decimal (n / 2 is integer division)
val moved = qb.from(Order::class.java).where { it.cnt!! > n / 2.0 }.toHql()
assertEquals("select e0 from Order e0 where e0.cnt > 2.5BD", moved.hql())
// arithmetic between columns cannot move, so STRICT allows it
qb.from(Order::class.java).where { it.cnt!! * it.status > n }.toHql()
```

**推奨:**

- できれば関数を値の側に置いてください: `it.price!! > OrderFns.discount(base)!!`。
- MariaDB では、関数が本当に決定的なら `DETERMINISTIC` と宣言してください。
- Oracle では、同じ式の関数ベースのインデックスがあれば警告が消えます（このようなインデックスには `DETERMINISTIC` な関数が必要です）。

コード例: [`ManualKotlinFunctionTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinFunctionTest.kt)（`sargable`、`sargable-strict`）。

---

<a id="810-function-rules-fn1-to-fn11-summary"></a>
## 8.10 関数のルール FN1 から FN11（まとめ）

| # | ルール | 状態 |
|---|---|---|
| FN1 | `@SqlFunction` は関数のアノテーション。値が空なら関数名を UPPER_SNAKE に。 | 実装済み |
| FN2 | lambdaQL は呼ばれた関数を見つけ、そのアノテーションを読む。Kotlin では `Companion` のレシーバーを飛ばす。本体は例外を投げるだけでなければならない（`= throw ...`）。アプリケーションとデータベースが違う値を返しうるので、実際の本体を持つ関数はエラー（`LQL-3052`）。 | 実装済み |
| FN3 | 同じ名前のすべてのオーバーロードは、同じ DB 関数に対応しなければならない。そうでなければエラー（`LQL-3053`）。（Kotlin の `Double?` の引数は `Double?` のオーバーロードを選ぶ。Kotlin から見た Java のエンティティの getter は、プリミティブ型の `double` のオーバーロードを選ぶことがある。） | 実装済み |
| FN4 | アノテーションのない呼び出しや、lambdaQL が識別できない呼び出しはエラー。黙って飛ばさない。 | 実装済み |
| FN5 | 引数は [3. 条件](03-where.md) の値のルールに従う。キャプチャした値だけで作った引数もバインドされ、**データベースで** 評価される。 | 実装済み |
| FN6 | `!!` とボクシングの呼び出しは飛ばす。戻り値の型は宣言された関数から来る。`Double`、`Integer`、`Long`、`BigDecimal`、`String`、`LocalDate`、`LocalDateTime`、`Boolean` のどれかに対応しなければならない（`LQL-3055`）。 | 実装済み |
| FN7 | カラムに適用した関数は WARN を出す（[8.9](#89-warning-functions-on-columns-and-indexes-sargable) を参照）。MariaDB では、値の側の `NOT DETERMINISTIC` な関数も WARN（`LQL-5007`）。 | 実装済み。`SargableMode.STRICT` ではエラー（`LQL-3066`） |
| FN8 | Hibernate の組み込み関数（`COALESCE`、`ROUND`、`SYSDATE`、...）と同じ名前はエラー（`LQL-3054`）。Hibernate が黙って組み込み関数を使うため。接頭辞かスキーマ名（`PUBLIC.SQ_FN`）を使う。 | 実装済み |
| FN9 | Kotlin: デフォルト引数を使う呼び出し（`LQL-3056`）、`vararg`（`LQL-3057`）はエラー。 | 実装済み |
| FN10 | スカラー関数だけをサポート。引数のない関数は `fn()` として出力される。 | 実装済み |
| FN11 | 既知の DB の違い: MariaDB で VARCHAR を返す関数は、文字列リテラルと照合順序の衝突を起こすことがある。`BigDecimal` の結果のスケールが違う（MariaDB と Oracle は 0、H2 は 1）。Boolean の結果には Oracle 23ai 以上が必要。 | プロトタイプで実測 |

ルールに違反する宣言:

<!-- region: ManualKotlinFunctionTest.kt#bad-declare -->
```kotlin
/** Functions that break the rules. */
interface BadFns {
    companion object {
        @JvmStatic
        @SqlFunction("SQ_FN_DISCOUNT")
        fun computes(price: Double?): Double? = price                  // a body that computes a value

        @JvmStatic
        @SqlFunction("SQ_FN_DISCOUNT")
        fun withDefault(price: Double?, rate: Double = 0.9): Double? = throw DbOnlyException()   // a default argument

        @JvmStatic
        @SqlFunction("SQ_FN_DISCOUNT")
        fun many(vararg prices: Double?): Double? = throw DbOnlyException()                      // vararg

        @JvmStatic
        @SqlFunction(value = "SQ_TAX_KT", sql = "SQ_FN_DISCOUNT")
        fun unregistered(price: Double?): Double? = throw DbOnlyException()   // its own SQL name, not registered
    }
}
```

<!-- region: ManualKotlinFunctionTest.kt#rules -->
```kotlin
// the body never runs in Kotlin
assertEquals("LQL-4034", assertThrows<DbOnlyException> { OrderFns.discount(1.0) }.code())
// a body that computes a value is rejected (Kotlin and the database could give different values)
assertEquals("LQL-3052", assertThrows<LambdaQLException> {
    qb.from(Order::class.java).where { BadFns.computes(it.price)!! > 1 }
}.code())
// a call that uses a Kotlin default argument, and a vararg function
assertEquals("LQL-3056", assertThrows<LambdaQLException> {
    qb.from(Order::class.java).where { BadFns.withDefault(it.price)!! > 1 }
}.code())
assertEquals("LQL-3057", assertThrows<LambdaQLException> {
    qb.from(Order::class.java).where { BadFns.many(it.price)!! > 1 }
}.code())
// a function with its own SQL name must be registered (lambdaql.functions.classes)
assertEquals("LQL-3058", assertThrows<LambdaQLException> {
    qb.from(Order::class.java).where { BadFns.unregistered(it.price)!! > 1 }
}.code())
```

組み込み関数と同じ名前（`LQL-3054`）と、対応しない戻り値の型（`LQL-3055`）も、ラムダ式を読むときに拒否されます。

コード例: [`ManualKotlinFunctionTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinFunctionTest.kt)（`bad-declare`、`rules`）。

---

<a id="811-database-support"></a>
## 8.11 データベースごとの対応

| 機能 | H2 | MariaDB、MySQL | PostgreSQL | Oracle |
|---|---|---|---|---|
| lambdaQL のプロシージャ呼び出し（IN、OUT、INOUT） | **いいえ**（H2 には OUT がない） | はい | はい | はい |
| 結果セット 1 つ | – | はい（プロシージャの中の `SELECT`） | はい（`REFCURSOR` OUT） | はい（`SYS_REFCURSOR` OUT） |
| パッケージ（`pkg`） | – | – | – | はい |
| クエリの中の DB 関数 | はい | はい | はい | はい |
| DB 関数の単独呼び出し | はい | はい | はい | はい |
| Boolean の結果の DB 関数 | はい | はい | はい | はい（23ai 以上） |

表は、各データベースで動作を確認した範囲です。

サポートしていないもの: Oracle のパッケージのプロシージャのオーバーロードを 1 つ選んで呼ぶ機能（オーバーロードの検出は実装済み、`LQL-4044`）、テーブルを返す関数、プロシージャの結果のページング。

この章のエラーは、`[LQL-xxxx]` のコードを持つ `LambdaQLException`（またはそのサブクラス）です。ラムダ式を読むときは、`LQL-3052`〜`LQL-3063` と、`SargableMode.STRICT` でインデックスを使えない条件の `LQL-3066`（`NonSargableException`、[8.9](#89-warning-functions-on-columns-and-indexes-sargable) を参照）です。呼び出しを実行するときは、`LQL-4034`〜`LQL-4039`、`LQL-4043`、`LQL-4044` です。呼び出しの間に `LQL-5004`、`LQL-5005`、`LQL-5006` を `INFO` レベルでログに出すこともあります。条件の中の DB 関数については、`LQL-5001`、`LQL-5007`（`WARN`）と `LQL-5008`（`INFO`）をログに出すことがあります。[11. エラーと言語](../11-errors-and-languages.md) を参照してください。

---

[Kotlin トラック](README.md) | 前: [7. 更新、挿入、削除](07-dml.md) | 次: [9. Kotlin の注意点](09-kotlin-notes.md) | [このページの Java 版](../java/08-procedures-functions.md)
