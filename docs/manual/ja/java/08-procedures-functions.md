<a id="8-stored-procedures-and-db-functions-java"></a>
# 8. ストアドプロシージャと DB 関数（Java）

[Java トラック](README.md) | [マニュアルのホーム](../README.md) | 前: [7. 更新、挿入、削除](07-dml.md) | 次: [9. Java の注意点](09-java-notes.md) | [このページの Kotlin 版](../kotlin/08-procedures-functions.md)

共通ページ: [1. 概要](../01-overview.md) · [11. エラーと言語](../11-errors-and-languages.md)

---

この章は 2 つの部分に分かれています。

| 部分 | 内容 | アノテーション | 呼び出し方 |
|---|---|---|---|
| [8.1 から 8.5](#81-declare-procedures) | **ストアドプロシージャ** | インターフェースの抽象メソッドに `@SqlProcedure` | `qb.call(OrderProcs.class, p -> p.calc(id, rate)).execute()` |
| [8.6 から 8.10](#86-declare-db-functions) | **DB 関数**（スカラー） | インターフェースの static メソッドに `@SqlFunction` | どのラムダの中でも直接: `where(o -> OrderFns.discount(o.getPrice()) > 10)` |

どちらの場合も、シグネチャは Java のインターフェースに **1 回だけ** 書きます。
lambdaQL がこれらのメソッドを **実行することはありません**。ラムダのバイトコードにある呼び出しを読み、データベースを呼び出します。

<a id="contents"></a>
## 目次

- [8.1 プロシージャの宣言](#81-declare-procedures)
- [8.2 プロシージャの呼び出し](#82-call-procedures)
- [8.3 OUT、INOUT、結果セット](#83-out-inout-and-result-sets)
- [8.4 注意: 引数の順序](#84-warning-argument-order)
- [8.5 プロシージャのルール PR1 から PR15（まとめ）](#85-procedure-rules-pr1-to-pr15-summary)
- [8.6 DB 関数の宣言](#86-declare-db-functions)
- [8.7 クエリでの DB 関数の使用](#87-use-db-functions-in-queries)
- [8.8 DB 関数の単独呼び出し](#88-call-a-db-function-alone)
- [8.9 注意: カラムに対する関数とインデックス（SARGable）](#89-warning-functions-on-columns-and-indexes-sargable)
- [8.10 関数のルール FN1 から FN11（まとめ）](#810-function-rules-fn1-to-fn11-summary)
- [8.11 データベースごとの対応](#811-database-support)

---

<a id="81-declare-procedures"></a>
## 8.1 プロシージャの宣言

インターフェースを書きます。各 **抽象** メソッドに `@SqlProcedure("NAME")` を付けてください。
実装は書きません。lambdaQL も実装を作りません。

**(テスト済み)** [`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java)、リージョン `declare`:

```java
/** One OUT value or more: a record in the database order. */
record Stat(Double total, Integer cnt) {
}

/** The INOUT value and the OUT value, in the database order. */
record Adjusted(Integer balance, String msg) {
}

/** A row of a result set: columns are matched by name (customer_id or customerId). */
record Line(Long id, Double price) {
}

/** A stub interface: lambdaQL reads the calls and never implements it. */
interface OrderProcs {
    @SqlProcedure("SQ_PROC_CALC")
    Double calc(Integer customerId, Double rate);                 // IN, IN, OUT

    @SqlProcedure("SQ_PROC_STAT")
    Stat stat(Integer customerId);                                // IN, OUT, OUT

    @SqlProcedure("SQ_PROC_ADJUST")
    Adjusted adjust(Integer add, @InOut Integer balance);         // IN, INOUT, OUT

    @SqlProcedure("SQ_PROC_RECENT")
    List<Line> recent(Integer customerId);                        // a result set (Oracle: a REF CURSOR)

    @SqlProcedure("SQ_PROC_PURGE")
    void purge(Integer status);                                   // no result

    @SqlProcedure("SQ_PROC_CALC")
    Double calcWrong(Integer customerId);                         // does not match the database: 1 argument
}

/** A database function. */
interface MathFns {
    @SqlFunction("SQ_FN_HALF")
    static Double half(Double value) {
        throw new DbOnlyException();
    }
}
```

テストは実行前に、MariaDB と Oracle にこれらのプロシージャを作ります。例（MariaDB）:

```sql
CREATE OR REPLACE PROCEDURE SQ_PROC_CALC(IN p_cust INT, IN p_rate DOUBLE, OUT p_total DOUBLE)
  BEGIN SELECT COALESCE(SUM(price), 0) * p_rate INTO p_total FROM SQ_orders WHERE customer_id = p_cust; END
```

`calcWrong` はわざと間違えて宣言したメソッドです。テストはこのメソッドでルール PR2 を確認します（[8.4](#84-warning-argument-order) を参照）。

注意:

- `@SqlProcedure`（または `@SqlFunction`）は **必須** です。そのメソッドが何であるかを lambdaQL に伝えます。
  （`@Procedure` と `@Function` という名前は使いませんでした。`@Procedure` は Spring Data JPA のアノテーションでもあり、`@Function` は Kotlin の `kotlin.Function` と衝突するからです。）
- 値が空の場合、名前はメソッド名から作られます: `calcTotal` → `CALC_TOTAL`。
- `@SqlProcedure` には `schema` と `pkg`（Oracle のパッケージ）もあります: `@SqlProcedure(value = "CALC", schema = "LQL", pkg = "SQ_PKG")`。
- 戻り値の型にはボクシング型（`Double`、`Integer`）を使ってください。プロシージャは NULL を返すことがあります。
- インターフェースと結果の record は、DB のシグネチャのコピーです。これは型安全のための代価です。lambdaQL はその一部をデータベースと照合します（ルール PR2）。
- プロシージャ（抽象メソッド）と DB 関数（static メソッド）は **同じインターフェース** に入れられます。

---

<a id="82-call-procedures"></a>
## 8.2 プロシージャの呼び出し

`qb.call(Interface.class, p -> p.method(args))` を使います。そのあと `execute()` を呼びます。

**(テスト済み)** [`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java)、リージョン `call`（MariaDB、Oracle）:

```java
Double total = qb.call(OrderProcs.class, p -> p.calc(customerId, rate)).execute();
Stat stat = qb.call(OrderProcs.class, p -> p.stat(customerId)).execute();
Adjusted adjusted = qb.call(OrderProcs.class, p -> p.adjust(5, 10)).execute();
List<Line> recent = qb.call(OrderProcs.class, p -> p.recent(customerId)).execute();
```

`customerId` と `rate` はキャプチャしたローカル値です。テストは各結果を Java で計算した値と比べます。たとえば `adjusted` は `new Adjusted(15, "ok")` です。

結果のない（`void`）プロシージャは `exec` で呼び出します。

**(テスト済み)** [`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java)、リージョン `exec`（MariaDB、Oracle）:

```java
qb.exec(OrderProcs.class, p -> p.purge(9)).execute();
```

なぜ void には `call` ではなく `exec` を使うのでしょうか。

- Java では、両方に `call` を使うとオーバーロードがあいまいになります（コンパイルエラー）。
- `run` は選びませんでした。Kotlin には同じ名前の標準ライブラリ関数があり、Kotlin から呼ぶときにエラーなしで間違ってそちらを呼んでしまう可能性があるからです。

<a id="what-the-lambda-may-contain-rule-pr12"></a>
### ラムダに書けるもの（ルール PR12）

ラムダ本体は **スタブメソッドの呼び出し 1 つ** です。それ以外は書けません。

| 書けるもの | 書けないもの |
|---|---|
| `p -> p.calc(id, rate)` | ログ出力、2 つの呼び出し、`if`、ネストした呼び出し |
| 引数: キャプチャ値、定数、それらの計算（`rate * 2`） | エンティティのパス（ここにはエンティティがありません） |

その他の点:

- OUT パラメーターを持つプロシージャに `exec` を使うとエラーです（`LQL-4037`）。OUT の値をうっかり失うことを防ぎます。
- `@SqlProcedure` のないメソッドを呼ぶとエラーです（`LQL-3060`）。
- パラメーターに対するデータベースのデフォルト値は使えません。すべての IN パラメーターを渡してください。
- IN の値として `null` を渡しても構いません。

---

<a id="83-out-inout-and-result-sets"></a>
## 8.3 OUT、INOUT、結果セット

戻り値の型とプロシージャの対応（ルール PR3、PR4、PR5）:

| プロシージャが持つもの | スタブメソッドの戻り値の型 |
|---|---|
| なし | `void` → `qb.exec(...)` で呼び出す |
| OUT パラメーター 1 つ | その値（`Double`、`Integer`、...） |
| 複数の OUT / INOUT パラメーター | Java の record（Kotlin: data class） |
| 結果セット 1 つ（MariaDB の `SELECT`、Oracle の `SYS_REFCURSOR` OUT、H2 の `ResultSet` 関数） | `List<T>` |
| 複数の結果セット | `List` のコンポーネントを持つ record |
| OUT の値と結果セット（MariaDB） | スカラーのコンポーネントと `List` のコンポーネントを持つ record |

record のコンポーネントの埋め方:

- **スカラーのコンポーネント** ← OUT と INOUT パラメーター（カーソルを除く）、DB の順序で。
- **`List` のコンポーネント** ← 結果セット、順番に（Oracle: カーソル OUT パラメーターの順序、MariaDB: 返された結果セットの順序）。
- 数が合わない場合、lambdaQL はエラーを投げます（`LQL-4038`）。
- IN、OUT、IN、INOUT のように混ざった位置でも問題ありません。lambdaQL は位置を DB のメタデータから読みます。

### INOUT

パラメーターに `@InOut` を付けてください。渡した値が **入力** になります。**出力** される値は、返される record のコンポーネントです。

テストの `SQ_PROC_ADJUST(p_add IN, p_balance IN OUT, p_msg OUT)` は、`p_balance` に `p_add` を足します。

```java
@SqlProcedure("SQ_PROC_ADJUST")
Adjusted adjust(Integer add, @InOut Integer balance);         // IN, INOUT, OUT

Adjusted adjusted = qb.call(OrderProcs.class, p -> p.adjust(5, 10)).execute();
// adjusted.balance() は 15（新しい残高）、adjusted.msg() は "ok"
```

`@InOut` は、データベースで INOUT である引数にだけ正確に付けてください。そうでないと最初の呼び出しが失敗します（`LQL-4036`）。

<a id="result-set-element-type-t"></a>
### 結果セットの要素の型 `T`

| `T` が | lambdaQL の読み方 |
|---|---|
| エンティティ | Hibernate の結果クラス（`StoredProcedureQuery`） |
| Java の record（Kotlin: data class） | JDBC の `CallableStatement`。カラムは **ラベルで** 対応付けます。`_` を取り除き、大文字と小文字を区別しないので、`customer_id` は `customerId` に対応します（`Line` でテスト済み） |
| 値 1 つ（`Long`、`String`、...） | 各行の最初のカラム |

通常の DTO クラスはここでは行の型として使えません。record を使ってください。
MariaDB と Oracle では `SELECT *` のカラムの順序が異なるため、lambdaQL は record のコンポーネントを位置で対応付けません。
よく使う型は変換します（たとえば数値を `Long`/`Integer` に、タイムスタンプを `LocalDateTime` に）。

制限:

- プロシージャの結果はページングできません。
- Oracle の暗黙の結果（`DBMS_SQL.RETURN_RESULT`）はサポートしません。`SYS_REFCURSOR` の OUT パラメーターを使ってください。

<a id="transactions-rule-pr9"></a>
### トランザクション（ルール PR9）

- プロシージャの中の DML は、呼び出し元のトランザクションの一部です。一緒にロールバックされます。
- 呼び出しの前に、lambdaQL は永続コンテキストをフラッシュします。ただし、トランザクションが有効で、変更がある場合だけです。
- `QueryBuilder(EntityManagerFactory)` は、呼び出しを自分のトランザクションで実行します。`EntityManager` を受け取った `QueryBuilder`（Spring の共有 `EntityManager` を含む）は、呼び出し元のトランザクションを使います。トランザクションがなければ、呼び出しは自動コミットされます。
- 呼び出しのあと、読み込み済みのエンティティが古い状態になっていることがあります。それらのエンティティに `refresh` か `evict` を使ってください。
- プロシージャの中の `COMMIT` や DDL はトランザクションを壊します。lambdaQL はこれを防げません。

<a id="affected-rows-rule-pr10"></a>
### 影響を受けた行数（ルール PR10）

プロシージャには、共通の「影響を受けた行数」の結果がありません（Oracle は -1 を返します）。
その数が必要な場合は、OUT パラメーターとして返してください。

---

<a id="84-warning-argument-order"></a>
## 8.4 注意: 引数の順序

> [!WARNING]
> **スタブメソッドのパラメーターの順序は、データベースでの順序と同じでなければなりません。**

lambdaQL は引数を **位置で** バインドします（ルール PR2）。
Hibernate 6.6.5 は、名前が登録されていても位置でバインドします。
MariaDB と H2 のドライバーは名前付きパラメーターをサポートしていません。
そのため、順序が間違っていると、**エラーなしで間違った結果** が得られることがあります。

最初の呼び出しで lambdaQL が JDBC のメタデータ（`getProcedureColumns`）で確認する項目です。結果はプロシージャ名ごとにキャッシュします。

| 確認項目 | 検出するか |
|---|---|
| プロシージャの存在 | はい、エラー `LQL-4035` |
| IN・INOUT のパラメーターの数 = スタブの引数の数 | はい、エラー `LQL-4036`（テスト済み） |
| INOUT のパラメーター = `@InOut` を付けた引数 | はい、エラー `LQL-4036` |
| OUT の値と結果セットの数 = 戻り値の型の構成要素の数 | はい、エラー `LQL-4038` |
| 型の種類（数値、文字、日付・時刻、真偽値、バイナリ） | はい、エラー `LQL-4036`（テスト済み）。同じ種類の中の違いは照合しない（下の「引数の型の照合」を参照） |
| 同じパラメーター名が別の順序で並ぶ | はい、エラー `LQL-4043`（テスト済み）。スタブの引数名が分かるときだけ（下を参照） |
| Oracle パッケージのプロシージャ名に複数のオーバーロードがある | はい、エラー `LQL-4044`（Oracle でテスト済み） |

**(テスト済み)** [`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java)、リージョン `rules`（MariaDB、Oracle）:

```java
// PR2: the stub is checked against the database parameters on the first call
assertEquals("LQL-4036", assertThrows(LambdaQLException.class,
        () -> qb.call(OrderProcs.class, p -> p.calcWrong(1)).execute()).code());
// exec cannot drop OUT values
assertEquals("LQL-4037", assertThrows(LambdaQLException.class,
        () -> qb.exec(OrderProcs.class, p -> p.calc(1, 1.0)).execute()).code());
// only @SqlProcedure methods of the stub can be called
assertEquals("LQL-3060", assertThrows(LambdaQLException.class,
        () -> qb.call(OrderProcs.class, p -> p.toString()).execute()).code());
```

最後のケースは `@SqlProcedure` のない `toString()` を呼んでいます。そのためコードは `LQL-3060`（メソッドが `@SqlProcedure` ではない）です。

危険なケースの例:

```java
// データベース: SP_TRANSFER(p_to NUMBER, p_from NUMBER)
@SqlProcedure("SP_TRANSFER") void transfer(Long from, Long to);   // 入れ替わっている! 型は同じ
```

数、モード、型はすべて一致しています。間違いを示せるのは名前だけです。
そこで lambdaQL は、最初の呼び出しで IN と INOUT の引数の名前を比べます。

- スタブの引数名はクラスファイルから読みます。名前が残るのは `-parameters` でコンパイルしたときだけです（[2. はじめよう](02-getting-started.md#recommended-compiler-option--parameters) を参照）。スタブの引数に `@org.lambdaql.DbParam("p_to")` を付けると、データベースの名前を直接指定できます。
- データベースの名前から、**すべて** の名前に共通で `_` で終わる接頭辞（`p_`、`in_`、`v_` など）を取り除きます。決まった接頭辞は仮定しません。そのあと、大文字と小文字、`_` を無視します。
- 名前が同じで順序だけが違う場合は、呼び出しの前に `LambdaQLException` `LQL-4043` が発生します。
- 名前が違う場合（スタブが別の命名規則に従う）はエラーにしません。チェックが働くように `@DbParam` を付けてください。
- クラスファイルに名前がなく、隣り合う 2 つの引数の型が同じ場合は、プロシージャごとに 1 回、`LQL-5004` を `INFO` レベルでログに出します。
- 型は種類だけを照合します（下の「引数の型の照合」を参照）。同じ種類の引数 2 つが入れ替わった場合は、名前でしか見つけられません。

**(テスト済み)** [`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java)、リージョン `names`（データベースのプロシージャは `SQ_PROC_DIFF(p_to, p_from)`。テストのスタブは `-parameters` でコンパイルしています）:

```java
/** Argument names: with -parameters lambdaQL compares them with the database names (p_to, p_from). */
interface DiffProcs {
    @SqlProcedure("SQ_PROC_DIFF")
    Integer diff(Integer to, Integer from);                       // same order as the database: runs

    @SqlProcedure("SQ_PROC_DIFF")
    Integer swapped(Integer from, Integer to);                    // same names, other order: LQL-4043

    @SqlProcedure("SQ_PROC_DIFF")
    Integer named(@DbParam("p_to") Integer target, @DbParam("p_from") Integer source);   // another naming rule

    @SqlProcedure("SQ_PROC_DIFF")
    Integer text(String to, Integer from);                        // a string for a number parameter: LQL-4036
}
```

**(テスト済み)** [`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java)、リージョン `name-check`（MariaDB、Oracle）:

```java
assertEquals(7, qb.call(DiffProcs.class, p -> p.diff(10, 3)).execute());
assertEquals(7, qb.call(DiffProcs.class, p -> p.named(10, 3)).execute());
// the same names in another order: stopped before the call (it would run with the values swapped)
assertEquals("LQL-4043", assertThrows(LambdaQLException.class,
        () -> qb.call(DiffProcs.class, p -> p.swapped(3, 10)).execute()).code());
```

**推奨:** `-parameters` でコンパイルしてデータベースと同じ名前を使う（共通の接頭辞は省略できます）か、`@DbParam` を付けてください。そうすれば、入れ替わった順序を検出します。

**引数の型の照合。** 最初の呼び出しで、各引数の Java の型と DB のパラメーターの型の種類を照合します。
種類は数値、文字、日付・時刻、真偽値、バイナリの 5 つで、違う場合は呼び出しの前に `LQL-4036` で止まります。

- 真偽値の引数は、数値のパラメーターにも合うものとみなします。MariaDB の `BOOLEAN` は `TINYINT(1)` で、Oracle 23 より前には `BOOLEAN` 型がないからです。
- 種類を決められない側（enum、オブジェクト型、カーソル）は照合しません。
- 同じ種類の中の違い（`Integer` の引数と `NUMBER(10,2)` のパラメーターなど）は、ドライバーの変換に任せます。

**(テスト済み)** [`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java)、リージョン `type-check`（MariaDB、Oracle）:

```java
// the kind of each argument is compared with the database parameter: a string for a number parameter stops
LambdaQLException wrong = assertThrows(LambdaQLException.class,
        () -> qb.call(DiffProcs.class, p -> p.text("10", 3)).execute());
assertEquals("LQL-4036", wrong.code(), wrong.getMessage());
```

**Oracle パッケージのオーバーロード。** Oracle のパッケージには、同じ名前のプロシージャが複数あることがあります。
lambdaQL はスタブがどれを指すのか区別できないので、そのような名前を呼ぶと `LQL-4044` で止まります。オーバーロードを 1 つ選んで呼ぶ機能は、今後の予定です。

**(テスト済み)** [`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java)、リージョン `overload`（Oracle のみ）。スタブ `PkgProcs` は `@SqlProcedure(pkg = "SQ_PKG", value = "TWICE")` と `@SqlProcedure(pkg = "SQ_PKG", value = "HALF")` を宣言しています:

```java
assertEquals(5.0, qb.call(PkgProcs.class, p -> p.twice(2.5)).execute(), 1e-9);
// HALF has two overloads in the package: lambdaQL cannot tell which one to call
assertEquals("LQL-4044", assertThrows(LambdaQLException.class,
        () -> qb.call(PkgProcs.class, p -> p.half(10.0)).execute()).code());
```

<a id="when-the-metadata-cannot-be-read"></a>
**メタデータを読めない場合。** データベースのユーザーに、ディクショナリビューや `information_schema` を読む権限がないことがあります。
メタデータの取得そのものが失敗した場合（`SQLException`）、lambdaQL は呼び出しを止めません。

- プロシージャごとに 1 回、`LQL-5005` を `INFO` レベルでログに出し、スタブは照合しません。
- 引数の並びはスタブから取ります。IN と INOUT の引数はスタブの順、その後に OUT パラメーター（結果の構成要素の順）、Oracle ではさらに `List` の構成要素ごとに `REF CURSOR` を 1 つです。そのため、メタデータがないときは、データベースの宣言で OUT パラメーターを IN パラメーターの後に置く必要があります。
- メタデータが必要なチェックは省きます: 数と `@InOut` のチェック（`LQL-4036`）、名前の順序のチェック（`LQL-4043`）。
- 取得はできたがプロシージャが見つからない場合は、これまでどおり `LQL-4035` です。
- Oracle のオーバーロードの取得（`all_arguments`）だけが失敗した場合は、`LQL-5006` を `INFO` レベルでログに出し、オーバーロードのチェックなしで呼び出します。

この代わりの動作は、MariaDB と Oracle でテスト済みです（`ProcedureTest` のテストメソッド `withoutMetadata`。失敗を模擬します）。

Oracle での実際の名前によるバインド（`pass_procedure_paramater_names=true` とスタブのパラメーター名の組み合わせ）も予定しています。

---

<a id="85-procedure-rules-pr1-to-pr15-summary"></a>
## 8.5 プロシージャのルール PR1 から PR15（まとめ）

| # | ルール | 状況 |
|---|---|---|
| PR1 | 名前: アノテーションの値をそのまま使います。値が空の場合はメソッド名を UPPER_SNAKE にしたもの。パッケージ名やスキーマ名（`SQ_PKG.CALC`、`LQL.SQ_CALC`）も使えます。混乱を避けるために `@SqlProcedure(schema = ..., pkg = ..., value = ...)` と書くこともできます。 | 実装済み。スキーマ名とパッケージ名はテストで確認していません |
| PR2 | 引数は **位置で** バインドされます。最初の呼び出しで、IN/INOUT のパラメーターの数と `@InOut` の位置を DB のメタデータと照合します。名前が分かれば名前も照合し、同じ名前が別の順序ならエラーです。引数の型の種類（数値、文字、日付・時刻、真偽値、バイナリ）も照合し、違えばエラー（`LQL-4036`）です（[8.4](#84-warning-argument-order) を参照）。メタデータを読めない場合は、チェックを省き、引数の並びをスタブから取ります（[8.4](#when-the-metadata-cannot-be-read) を参照）。 | 数、`@InOut`、名前の照合は実装済み（テスト済み）。Oracle パッケージのオーバーロードの検出も実装済み（Oracle でテスト済み）。型の照合も実装済み（MariaDB、Oracle でテスト済み）。メタデータなしの代わりの動作（`LQL-5005`、`LQL-5006`）は実装済み（MariaDB、Oracle でテスト済み）。 |
| PR3 | OUT が 1 つ → 戻り値。OUT/INOUT が複数 → record。`@InOut` は INOUT パラメーターを示します。 | 実装済み（テスト済み） |
| PR4 | 結果セットが 1 つ → `List<T>`。エンティティは Hibernate で、record はラベルの対応付けで JDBC を通して読みます。ページングはありません。 | 実装済み（record でテスト済み） |
| PR5 | 複数の結果セット → record の `List` コンポーネントに順番に入ります。Oracle の暗黙の結果はサポートしません。 | 実装済み、テストでは確認していません |
| PR6 | 単独で呼び出す DB 関数は、データベースごとに異なる経路を使います（[8.8](#88-call-a-db-function-alone) を参照）。 | 実装済み（テスト済み） |
| PR7 | クエリの中の DB 関数: [8.6](#86-declare-db-functions) とルール FN1 から FN11 を参照してください。 | 実装済み（テスト済み） |
| PR8 | 条件の中でカラムに DB 関数を使うと、インデックスが使えないため WARN を出します（[8.9](#89-warning-functions-on-columns-and-indexes-sargable) を参照）。コードは `LQL-5001`。 | 実装済み（テスト済み）。`SargableMode.STRICT` ではエラー（`LQL-3066`、D50） |
| PR9 | プロシージャは呼び出し元のトランザクションの中で実行されます。lambdaQL は必要なときに呼び出しの前にフラッシュします。呼び出しのあと、読み込み済みのエンティティが古い状態になっていることがあります。 | 実装済み |
| PR10 | 共通の影響行数はありません。OUT パラメーターを使ってください。 | – |
| PR11 | IN の値に `null` を使えます。DB のパラメーターのデフォルト値は使えません。 | 実装済み |
| PR12 | ラムダはスタブの呼び出し 1 つです。引数はキャプチャ値、定数、またはそれらの計算です。OUT パラメーターがあるときに `exec` を使うとエラーです。 | 実装済み（テスト済み） |
| PR13 | boolean のパラメーターは MariaDB と Oracle 23ai 以降で使えます。 | テストでは確認していません |
| PR14 | 各ラムダは 1 回だけ解析します。プロシージャのメタデータは名前ごとに 1 回だけ読みます。 | 実装済み |
| PR15 | H2 には OUT、INOUT、複数の結果セット、パッケージがありません。プロシージャは MariaDB か Oracle でテストしてください（たとえば Testcontainers を使って）。 | lambdaQL のテストもこの方式に従っています |

PR11、PR12、PR13 には Kotlin 用の追加部分（デフォルト引数、名前付き引数、null 許容型）があります。それらは [このページの Kotlin 版](../kotlin/08-procedures-functions.md) にあります。

---

<a id="86-declare-db-functions"></a>
## 8.6 DB 関数の宣言

DB 関数は、データベースに直接作った関数です。たとえば `FN_DISCOUNT(price)` です。
`@SqlFunction` を付けた **インターフェースの static メソッド** として宣言します。
本体は例外を投げるだけにします。lambdaQL の中で実行されることはありません。

**(テスト済み)** [`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java)、リージョン `declare`:

```java
/** Database functions: static methods of your own interface. The bodies only throw. */
public interface OrderFns {
    /** {@code SQ_FN_DISCOUNT(price)}: 90% of the price. */
    @SqlFunction("SQ_FN_DISCOUNT")
    static Double discount(Double price) {
        throw new DbOnlyException();
    }

    /** {@code SQ_FN_IS_GOLD(grade)}: a grade of 5 or more. */
    @SqlFunction("SQ_FN_IS_GOLD")
    static Boolean isGold(Integer grade) {
        throw new DbOnlyException();
    }

    /** A query name with its own SQL name; registered with lambdaql.functions.classes. */
    @SqlFunction(value = "SQ_TAX", sql = "SQ_FN_DISCOUNT")
    static Double tax(Double price) {
        throw new DbOnlyException();
    }
}
```

テストは実行前に `SQ_FN_DISCOUNT` と `SQ_FN_IS_GOLD` をデータベースに作り、終わると削除します。Boolean の結果には Oracle 23ai 以降が必要です。

lambdaQL のラムダの **外** で `OrderFns.discount(...)` を呼ぶと、`DbOnlyException`（`LQL-4034`）が発生します。これは意図した動作です。

`@SqlFunction` の値は 2 つです。

| 値 | 意味 |
|---|---|
| `value` | クエリで使う関数名。空の場合はメソッド名を UPPER_SNAKE にしたもの（`isVip` → `IS_VIP`）。スキーマ名も使えます（`PUBLIC.SQ_FN`） |
| `sql` | `value` と違う場合の SQL の名前。このような関数は登録が必要です（[登録](#registration-optional) を参照） |

その他の宣言方法:

| 宣言 | 状況 |
|---|---|
| インターフェースの static メソッド | **推奨**（テスト済み） |
| クラスの static メソッド | 設計済み、テストでは確認していません |
| インターフェースの default メソッド + シングルトンの定数 | 設計済み、テストでは確認していません |
| リポジトリが関数インターフェースを `implements` する（ミックスイン） | 拒否 |
| 関数型インターフェースの定数（`DISCOUNT.apply(x)`） | 拒否 |

関数は **インターフェース名を付けて** 呼び出してください（`OrderFns.discount`）。static import も使えますが、同じ名前のメンバーメソッドがあると隠れてしまうことがあります。

---

<a id="87-use-db-functions-in-queries"></a>
## 8.7 クエリでの DB 関数の使用

関数を直接呼び出してください。コンテキストの引数は必要ありません。
`where`、`select`、`orderBy`、`set` で使えます（テスト済み）。`groupBy`、`having`、サブクエリでも使えるように設計しましたが、テストでは確認していません。

**(テスト済み)** [`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java)、リージョン `where`:

```java
// the function call is written as function('<name>' as <type>, ...); the column is its argument
SelectQuery<Order> q = qb.from(Order.class).where(o -> OrderFns.discount(o.getPrice()) > 100);
assertEquals("select e0 from Order e0 where function('SQ_FN_DISCOUNT' as Double, e0.price) > 100.0", q.toHql().hql());
```

**(テスト済み)** [`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java)、リージョン `captured`:

```java
// a function of captured values only is still computed by the database (the value is bound)
SelectQuery<Order> q = qb.from(Order.class).where(o -> o.getPrice() > OrderFns.discount(base));
assertEquals("select e0 from Order e0 where e0.price > function('SQ_FN_DISCOUNT' as Double, :p1)", q.toHql().hql());
```

Boolean の関数は、条件全体として使えます。lambdaQL が `= true` を付けます。

**(テスト済み)** [`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java)、リージョン `boolean`:

```java
SelectQuery<Order> q = qb.from(Order.class).where(o -> OrderFns.isGold(o.getCustomer().getGrade()));
assertEquals("select e0 from Order e0 join e0.customer j1 where function('SQ_FN_IS_GOLD' as Boolean, j1.grade) = true",
        q.toHql().hql());
```

**(テスト済み)** [`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java)、リージョン `select`（`select` と `orderBy`）:

```java
ValueQuery<Priced> q = qb.from(Order.class)
        .orderBy((o, by) -> by.desc(OrderFns.discount(o.getPrice())))
        .select(o -> new Priced(o.getId(), OrderFns.discount(o.getPrice())));
assertEquals("select e0.id, function('SQ_FN_DISCOUNT' as Double, e0.price) from Order e0"
        + " order by function('SQ_FN_DISCOUNT' as Double, e0.price) desc nulls last", q.toHql().hql());
```

`Priced` は `record Priced(Long id, Double discounted)` です。

**(テスト済み)** [`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java)、リージョン `update`:

```java
ExecutableUpdate<Order> u = qb.update(Order.class)
        .set(o -> o.setPrice(OrderFns.discount(o.getPrice())))
        .where(o -> o.getPrice() > 100);
assertEquals("update Order e0 set e0.price = function('SQ_FN_DISCOUNT' as Double, e0.price) where e0.price > 100.0",
        u.toHql().hql());
```

テストは各クエリを実行し、結果の行を Java で同じ計算（`price * 0.9`）をした結果と比べます。

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

ジェネリックな戻り値（`<X> X nvl(X, X)`）、エンティティ、コレクション、`void` は解析時に拒否されます。

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
ネストしたインターフェースには、`$` を含むバイナリ名を書きます。テストの設定は `lambdaql.functions.classes: org.lambdaql.query.FunctionTest$OrderFns` です。

<a id="a-different-sql-name-needs-registration"></a>
### SQL の名前が違う関数の登録

`value` はクエリの中の名前で、`sql` は SQL の中の名前です。2 つが違う場合、関数を登録しなければなりません。
引用符で囲んで作った大文字・小文字混在の関数名（`"SQ_Fn_Mixed"`）に便利です。登録しないと、H2 と Oracle では名前が小文字になることがあります。

```java
@SqlFunction(value = "SQ_Fn_Mixed", sql = "\"SQ_Fn_Mixed\"")
static Double mixed(Double v) { throw new DbOnlyException(); }
```

**(テスト済み)** [`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java)、リージョン `registered`:

```java
// SQ_TAX is registered at start-up (lambdaql.functions.classes) and runs SQ_FN_DISCOUNT in SQL
SelectQuery<Order> q = qb.from(Order.class).where(o -> OrderFns.tax(o.getPrice()) > 100);
assertEquals("select e0 from Order e0 where function('SQ_TAX' as Double, e0.price) > 100.0", q.toHql().hql());
```

このような関数が登録されていない場合、lambdaQL はラムダを読むときに失敗します（`LQL-3058`、[8.10](#810-function-rules-fn1-to-fn11-summary) を参照）。

---

<a id="88-call-a-db-function-alone"></a>
## 8.8 DB 関数の単独呼び出し

**引数のないラムダ** で `qb.call(() -> ...)` を使ってください。

**(テスト済み)** [`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java)、リージョン `function`（H2、MariaDB、Oracle）:

```java
Double half = qb.call(() -> MathFns.half(price)).execute();
```

`price` はキャプチャした `double`（150）で、`half` は 75.0 です。ラムダは `@SqlFunction` の呼び出し 1 つでなければなりません（`LQL-3063`）。

**データベースごとの SQL（この形の HQL に対する Hibernate の SQL）** — `PlannedSqlTest` が名前 `FN_DISCOUNT` で記録しました。`expected-sql/` の [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)、例 `08-function-alone`:

```sql
-- H2, MariaDB, Oracle (same)
-- HQL: select function('FN_DISCOUNT' as Double, :p1)
select FN_DISCOUNT(?)
```

このクエリは FROM のない `select FN_DISCOUNT(?)` として実行されます。Oracle がこれを受け付けるのは 23ai からです。Oracle では、lambdaQL は代わりに JDBC の関数呼び出しを使います（下の表を参照）。

引数の数が異なるので、`qb.call(OrderProcs.class, p -> ...)` と衝突しません。

lambdaQL の実行方法（ルール PR6）:

| データベース | 経路 |
|---|---|
| Oracle | JDBC の関数呼び出し `{? = call FN(?)}`。DML を行う関数や、カーソルを返す関数でも使えます。 |
| MariaDB、H2 | HQL の `select function('FN' as T, :p1)` |

lambdaQL は先に保留中の変更をフラッシュします（PR9 と同じ）。そのため、関数は最新の変更を見ます。

---

<a id="89-warning-functions-on-columns-and-indexes-sargable"></a>
## 8.9 注意: カラムに対する関数とインデックス（SARGable）

lambdaQL は **インデックスを使える** SQL を作ろうとします（[10. ルールと制限](10-rules-and-limits.md#102-sargable-rule-index-friendly-sql) を参照）。
カラムを関数で囲むと、通常のインデックスは普通 **使えません**。

```java
.where(o -> OrderFns.discount(o.getPrice()) > 10)
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

**(テスト済み)** [`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java)、リージョン `sargable`（テストはログを集めます: H2 は `LQL-5001`、MariaDB は `LQL-5001` と `LQL-5007`、`SQ_FN_DISCOUNT(price)` の関数ベースインデックスがある Oracle はなし）:

```java
// the function wraps the column: an index on price cannot be used (LQL-5001 at WARN),
// unless Oracle has a function-based index on SQ_FN_DISCOUNT(price)
qb.from(Order.class).where(o -> OrderFns.discount(o.getPrice()) > 100).toHql();
// the function is on the value side: fine, unless MariaDB says NOT DETERMINISTIC (LQL-5007 at WARN)
qb.from(Order.class).where(o -> o.getPrice() > OrderFns.nondet(base)).toHql();
```

**SARGable モード**（決定 D50）。`SargableMode` には 3 つの値があります。

- `OFF`: チェックせず、メタデータも取得しません。
- `WARN`（デフォルト）: 警告をログに出します。
- `STRICT`: `WARN` なら警告を出す場合（`LQL-5001`、`LQL-5007`、`LQL-5012`）に、解析が `org.lambdaql.NonSargableException`（`LQL-3066`）で失敗します。

persistence unit ごとに `SargablePolicy.register(emf, SargableMode.STRICT)` で指定し、`SargablePolicy.unregister(emf)` で解除します。Spring では `lambdaql.sargable.mode=STRICT` を使います（[12](12-spring-data-jpa.md) を参照）。
チェックはラムダごとに 1 回、解析結果を共有キャッシュに入れるときに実行されます。そのため、最初のクエリの前にモードを登録してください。

**カラムに対する算術**（決定 D50）。

- カラム 1 つと値だけの `+ - * /`（`o.getCnt() * 2 > n`）は値の側に移せます。lambdaQL は `LQL-5012` を `WARN` レベルでログに出し、`STRICT` ではエラーです。
- カラム同士の算術（`o.getPrice() * o.getCnt() > n`）と `%` は移せないので、`LQL-5013` を `DEBUG` レベルでログに出すだけです。`STRICT` でも許可します。
- 移した形は意味が同じでなければなりません。`o.getCnt() > n / 2.0` のように小数で割ってください。整数の割り算 `n / 2` は、`>=`、`=`、`<` や負の `n` で違う行を返します。負の数を掛けていた場合は、比較の向きを逆にしてください。`LQL-5012` のメッセージがこれを案内します。
- インデックスがあるかを調べて警告を減らす方式は採用しませんでした。権限とシノニムのせいで「インデックスなし」と「不明」が混ざり、カラムの算術はまれな形だからです。

**(テスト済み)** [`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java)、リージョン `sargable-strict`:

```java
// STRICT: what WARN would log fails the analysis (NonSargableException, LQL-3066)
SargablePolicy.register(emf, SargableMode.STRICT);
NonSargableException fn = assertThrows(NonSargableException.class,
        () -> qb.from(Order.class).where(o -> OrderFns.discount(o.getTotal()) > 100).toHql());
assertEquals("LQL-3066", fn.code());
assertThrows(NonSargableException.class, () -> qb.from(Order.class).where(o -> o.getCnt() * 2 > n).toHql());
// the same meaning on the value side uses the index; divide by a decimal (n / 2 is integer division)
var moved = qb.from(Order.class).where(o -> o.getCnt() > n / 2.0).toHql();
assertEquals("select e0 from Order e0 where e0.cnt > :p1", moved.hql());
assertEquals(Map.of("p1", new java.math.BigDecimal("2.5")), moved.parameters());   // 2.5, never cut to 2
// arithmetic between columns cannot move, so STRICT allows it
qb.from(Order.class).where(o -> o.getCnt() * o.getStatus() > n).toHql();
```

**推奨:**

- できるだけ関数を値の側に置いてください: `o.getPrice() > OrderFns.discount(base)`。
- MariaDB では、実際に決定的な関数であれば `DETERMINISTIC` と宣言してください。
- Oracle では、同じ式の関数ベースインデックスがあると警告が消えます（このようなインデックスには `DETERMINISTIC` な関数が必要です）。

---

<a id="810-function-rules-fn1-to-fn11-summary"></a>
## 8.10 関数のルール FN1 から FN11（まとめ）

| # | ルール | 状況 |
|---|---|---|
| FN1 | `@SqlFunction` はメソッドのアノテーションです。値が空の場合はメソッド名を UPPER_SNAKE にしたもの。 | 実装済み |
| FN2 | lambdaQL は呼び出されたメソッドを見つけ、そのアノテーションを読みます。メソッドの本体は例外を投げるだけ（`throw new ...`）でなければなりません。本当の本体を持つメソッドは、Java とデータベースで異なる値になる可能性があるため、エラーです（`LQL-3052`）。 | 実装済み（テスト済み） |
| FN3 | 同じ名前のオーバーロードは、すべて同じ DB 関数に対応しなければなりません。そうでなければエラーです（`LQL-3053`）。 | 実装済み |
| FN4 | アノテーションのない呼び出しや、lambdaQL が特定できない呼び出しはエラーです。黙って読み飛ばすことはありません。 | 実装済み |
| FN5 | 引数は [3. 条件](03-where.md) の値のルールに従います。キャプチャ値だけで作った引数でも、バインドされて **データベースで** 評価されます。 | 実装済み（テスト済み） |
| FN6 | ボクシングの呼び出しは読み飛ばします。戻り値の型は宣言されたメソッドから決まります。`Double`、`Integer`、`Long`、`BigDecimal`、`String`、`LocalDate`、`LocalDateTime`、`Boolean` のいずれかでなければなりません（`LQL-3055`）。 | 実装済み（テスト済み） |
| FN7 | カラムに対する関数は WARN を出します（[8.9](#89-warning-functions-on-columns-and-indexes-sargable) を参照）。MariaDB では、値の側の `NOT DETERMINISTIC` な関数も WARN（`LQL-5007`）。 | 実装済み（テスト済み）。`SargableMode.STRICT` ではエラー（`LQL-3066`、D50） |
| FN8 | Hibernate の組み込み関数（`COALESCE`、`ROUND`、`SYSDATE`、...）と同じ名前はエラーです（`LQL-3054`）。Hibernate が黙って組み込み関数を使ってしまうからです。接頭辞かスキーマ名（`PUBLIC.SQ_FN`）を使ってください。 | 実装済み（テスト済み） |
| FN9 | Kotlin のみ（デフォルト引数、`inline`、`vararg`）。Kotlin トラックを参照してください。 | デフォルト引数と `vararg` は実装済み。`inline` は設計済み |
| FN10 | v0.1 はスカラー関数だけです。ユーザー定義の集約関数とウィンドウ関数は v0.2 の予定です。引数のない関数は `fn()` として出力されます。 | スカラー関数は実装済み |
| FN11 | 既知の DB の違い: MariaDB では、VARCHAR を返す関数が文字列リテラルと照合順序の衝突を起こすことがあります。`BigDecimal` の結果のスケールが異なります（MariaDB と Oracle は 0、H2 は 1）。boolean の結果には Oracle 23ai 以降が必要です。 | プロトタイプで実測 |

**(テスト済み)** [`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java)、リージョン `rules`:

```java
// the body never runs in Java
assertEquals("LQL-4034", assertThrows(DbOnlyException.class, () -> OrderFns.discount(1.0)).code());
// FN2: a body that computes a value is rejected (Java and the database could give different values)
assertEquals("LQL-3052", assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(o -> BadFns.computes(o.getPrice()) > 1)).code());
// FN8: a Hibernate built-in name would silently run the built-in function
assertEquals("LQL-3054", assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(o -> BadFns.builtin(o.getPrice()) > 1)).code());
// a function with its own SQL name must be registered
assertEquals("LQL-3058", assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(o -> BadFns.unregistered(o.getPrice()) > 1)).code());
// the return type must have an HQL type
assertEquals("LQL-3055", assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(o -> BadFns.anything(o.getPrice()) != null)).code());
```

`BadFns` は、ケースごとに誤った関数を 1 つずつ置いたテストの中のインターフェースです。`price` を返す本体、名前 `COALESCE`、登録していない `value = "SQ_OTHER"`・`sql = "SQ_FN_DISCOUNT"`、戻り値の型 `Object` の 4 つです。

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
| boolean のパラメーター / 結果 | – | はい | はい（23ai 以降） |

v0.1 では予定していないもの: 動的なプロシージャ名のための文字列ビルダー（v0.2 の候補）、Oracle パッケージのプロシージャのオーバーロードを 1 つ選んで呼ぶ機能（オーバーロードの検出は実装済み、`LQL-4044`）、テーブル値関数、プロシージャの結果のページング。

この章のエラーは、`[LQL-xxxx]` コードを持つ `LambdaQLException`（またはそのサブクラス）です。ラムダを読むときは `LQL-3052`〜`LQL-3063` と、`SargableMode.STRICT` でインデックスを使えない条件に対する `LQL-3066`（`NonSargableException`、[8.9](#89-warning-functions-on-columns-and-indexes-sargable) を参照）です。呼び出しを実行するときは `LQL-4034`〜`LQL-4039`、`LQL-4043`、`LQL-4044` です。呼び出しの間に `LQL-5004`、`LQL-5005`、`LQL-5006` を `INFO` レベルでログに出すこともあります。条件の中の DB 関数については、`LQL-5001`、`LQL-5007`（`WARN`）と `LQL-5008`（`INFO`）をログに出すことがあります。[11. エラーと言語](../11-errors-and-languages.md) を参照してください。

---

[Java トラック](README.md) | 前: [7. 更新、挿入、削除](07-dml.md) | 次: [9. Java の注意点](09-java-notes.md) | [このページの Kotlin 版](../kotlin/08-procedures-functions.md)
