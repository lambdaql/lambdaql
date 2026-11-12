<a id="8-stored-procedures-and-db-functions-java"></a>
# 8. ストアドプロシージャと DB 関数（Java）

> [!NOTE]
> **状況: 設計済み、まだ実装されていません。**
> この章は決定済みの設計です（決定 D30 と D31、マイルストーン M11）。
> 構文は Java でコンパイルでき、呼び出しは Hibernate 6.6.5 を使って H2、MariaDB、Oracle 23ai で確認しました。
> 現在の lambdaQL では **動きません**。現在動くのは解析コアと `where` + `toList()` だけです
> （H2、MariaDB、Oracle でテスト済み）。全体の状況は [Java トラックの README](README.md#status) を参照してください。

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

- [8.1 プロシージャを宣言する](#81-declare-procedures)
- [8.2 プロシージャを呼び出す](#82-call-procedures)
- [8.3 OUT、INOUT、結果セット](#83-out-inout-and-result-sets)
- [8.4 警告: 引数の順序](#84-warning-argument-order)
- [8.5 プロシージャのルール PR1 から PR15（まとめ）](#85-procedure-rules-pr1-to-pr15-summary)
- [8.6 DB 関数を宣言する](#86-declare-db-functions)
- [8.7 クエリで DB 関数を使う](#87-use-db-functions-in-queries)
- [8.8 DB 関数を単独で呼び出す](#88-call-a-db-function-alone)
- [8.9 警告: カラムに対する関数とインデックス（SARGable）](#89-warning-functions-on-columns-and-indexes-sargable)
- [8.10 関数のルール FN1 から FN11（まとめ）](#810-function-rules-fn1-to-fn11-summary)
- [8.11 データベースごとの対応](#811-database-support)

---

<a id="81-declare-procedures"></a>
## 8.1 プロシージャを宣言する

インターフェースを書きます。各 **抽象** メソッドに `@SqlProcedure("NAME")` を付けます。
実装は書きません。lambdaQL も実装を作りません。

```java
interface OrderProcs {
    @SqlProcedure("SQ_CALC")     Double calc(Long id, Double rate);              // OUT が 1 つ → 戻り値
    @SqlProcedure("SQ_CALC2")    CalcOut calc2(Long id, Double rate);            // OUT が複数 → record
    @SqlProcedure("SP_ADJUST")   AdjOut adjust(Long id, @InOut Integer balance); // INOUT
    @SqlProcedure("SP_RECENT")   List<OrderView> recent(Long custId);           // 結果セット / Oracle の REF CURSOR
    @SqlProcedure("SQ_PKG.CALC") Double pkgCalc(Long id, Double rate);           // パッケージ名またはスキーマ名
    @SqlProcedure("SP_PURGE")    void purge(Integer days);                       // 結果なし
}

record CalcOut(Double total, Integer cnt) {}
record AdjOut(Integer balance, String msg) {}
```

注意:

- `@SqlProcedure`（または `@SqlFunction`）は **必須** です。そのメソッドが何であるかを lambdaQL に伝えます。
  （`@Procedure` と `@Function` という名前は使いませんでした。`@Procedure` は Spring Data JPA のアノテーションでもあり、`@Function` は Kotlin の `kotlin.Function` と衝突するからです。）
- 値が空の場合、名前はメソッド名から作られます: `calcTotal` → `CALC_TOTAL`。
- 戻り値の型にはボクシング型（`Double`、`Integer`）を使ってください。プロシージャは NULL を返すことがあります。
- インターフェースと結果の record は、DB のシグネチャのコピーです。これは型安全のための代価です。lambdaQL はその一部をデータベースと照合します（ルール PR2）。
- プロシージャ（抽象メソッド）と DB 関数（static メソッド）は **同じインターフェース** に入れられます。

---

<a id="82-call-procedures"></a>
## 8.2 プロシージャを呼び出す

`qb.call(Interface.class, p -> p.method(args))` を使います。そのあと `execute()` を呼びます。

```java
Double d          = qb.call(OrderProcs.class, p -> p.calc(id, rate)).execute();
CalcOut co        = qb.call(OrderProcs.class, p -> p.calc2(id, rate)).execute();
AdjOut a          = qb.call(OrderProcs.class, p -> p.adjust(id, balance)).execute();
List<OrderView> r = qb.call(OrderProcs.class, p -> p.recent(custId)).execute();

// 結果なし（void）: exec を使う
qb.exec(OrderProcs.class, p -> p.purge(30)).execute();
```

なぜ void には `call` ではなく `exec` を使うのでしょうか。

- Java では、両方に `call` を使うとオーバーロードがあいまいになります（コンパイルエラー）。
- `run` は選びませんでした。Kotlin には同じ名前の標準ライブラリ関数があり、Kotlin から呼ぶときにエラーなしで間違ってそちらを呼んでしまう可能性があるからです。

<a id="what-the-lambda-may-contain-rule-pr12"></a>
### ラムダに書けるもの（ルール PR12）

ラムダ本体は **スタブメソッドの呼び出し 1 つ** だけです。それ以外は書けません。

| 書けるもの | 書けないもの |
|---|---|
| `p -> p.calc(id, rate)` | ログ出力、2 つの呼び出し、`if`、ネストした呼び出し |
| 引数: キャプチャ値、定数、それらの計算（`rate * 2`） | エンティティのパス（ここにはエンティティがありません） |

その他の点:

- OUT パラメーターを持つプロシージャに `exec` を使うとエラーです。これにより、OUT の値をうっかり失うことを防ぎます。
- パラメーターに対するデータベースのデフォルト値は使えません。すべての IN パラメーターを渡してください。
- IN の値として `null` を渡すのは問題ありません。

---

<a id="83-out-inout-and-result-sets"></a>
## 8.3 OUT、INOUT、結果セット

戻り値の型とプロシージャの対応（ルール PR3、PR4、PR5）:

| プロシージャが持つもの | スタブメソッドの戻り値の型 |
|---|---|
| なし | `void` → `qb.exec(...)` で呼び出す |
| OUT パラメーター 1 つ | その値（`Double`、`Integer`、...） |
| 複数の OUT / INOUT パラメーター | Java の record |
| 結果セット 1 つ（MariaDB の `SELECT`、Oracle の `SYS_REFCURSOR` OUT、H2 の `ResultSet` 関数） | `List<T>` |
| 複数の結果セット | `List` のコンポーネントを持つ record |
| OUT の値と結果セット（MariaDB） | スカラーと `List` のコンポーネントを持つ record |

record のコンポーネントの埋め方:

- **スカラーのコンポーネント** ← OUT と INOUT パラメーター（カーソルを除く）、DB の順序で。
- **`List` のコンポーネント** ← 結果セット、順番に（Oracle: カーソル OUT パラメーターの順序、MariaDB: 返された結果セットの順序）。
- 数が合わない場合、lambdaQL はエラーを投げます。
- IN、OUT、IN、INOUT のように混ざった位置でも問題ありません。lambdaQL は位置を DB のメタデータから読みます。

### INOUT

パラメーターに `@InOut` を付けます。渡した値が **入力** になります。**出力** される値は、返される record のコンポーネントです。

```java
@SqlProcedure("SP_ADJUST") AdjOut adjust(Long id, @InOut Integer balance);

AdjOut a = qb.call(OrderProcs.class, p -> p.adjust(id, 100)).execute();
// a.balance() が新しい残高です
```

<a id="result-set-element-type-t"></a>
### 結果セットの要素の型 `T`

| `T` が | lambdaQL の読み方 |
|---|---|
| エンティティ | Hibernate の結果クラス |
| record または DTO | JDBC の `CallableStatement`。カラムは **ラベルで** 対応付けます（`camelCase` ↔ `snake_case`、大文字と小文字は区別しない） |

MariaDB と Oracle では `SELECT *` のカラムの順序が異なるため、lambdaQL がカラムを位置で対応付けることはありません。
よく使う型は変換します（たとえば数値を `Long`/`Integer` に、タイムスタンプを `LocalDateTime` に）。

制限:

- プロシージャの結果をページングすることはできません。
- Oracle の暗黙の結果（`DBMS_SQL.RETURN_RESULT`）はサポートしません。`SYS_REFCURSOR` の OUT パラメーターを使ってください。

<a id="transactions-rule-pr9"></a>
### トランザクション（ルール PR9）

- プロシージャの中の DML は、呼び出し元のトランザクションの一部です。一緒にロールバックされます。
- 呼び出しの前に、lambdaQL は永続コンテキストをフラッシュします。ただし、トランザクションが有効で、変更がある場合だけです。
- トランザクションの外では、呼び出しは自動コミットです。
- 呼び出しのあと、読み込み済みのエンティティが古くなっていることがあります。それらのエンティティに `refresh` か `evict` を使ってください。
- プロシージャの中の `COMMIT` や DDL はトランザクションを壊します。lambdaQL はこれを止められません。

<a id="affected-rows-rule-pr10"></a>
### 影響を受けた行数（ルール PR10）

プロシージャには、共通の「影響を受けた行数」の結果がありません（Oracle は -1 を返します）。
その数が必要な場合は、OUT パラメーターとして返してください。

---

<a id="84-warning-argument-order"></a>
## 8.4 警告: 引数の順序

> [!WARNING]
> **スタブメソッドのパラメーターの順序は、データベースでの順序と同じでなければなりません。**

lambdaQL は引数を **位置で** バインドします（ルール PR2）。
Hibernate 6.6.5 は、名前が登録されていても位置でバインドします。
MariaDB と H2 のドライバーは名前付きパラメーターをサポートしていません。
そのため、順序が間違っていると、**エラーなしで間違った結果** が得られることがあります。

最初の呼び出しで lambdaQL が（JDBC のメタデータから）確認すること:

| 確認 | 見つかるか |
|---|---|
| パラメーターの数 | はい、エラー |
| モード（IN / OUT / INOUT / カーソル） | はい、エラー |
| 型 | はい、エラー |
| **同じ型** の 2 つのパラメーターの入れ替わり | **パラメーター名が取得できる場合だけ** |

危険なケースの例:

```java
// データベース: SP_TRANSFER(p_to NUMBER, p_from NUMBER)
@SqlProcedure("SP_TRANSFER") void transfer(Long from, Long to);   // 入れ替わっている! 型は同じ
```

数、モード、型はすべて一致しています。間違いを示せるのは名前だけです。
lambdaQL は、名前が取得できる場合は名前を比較します。javac の `-parameters` でコンパイルするか、`@Param` を使ってください。
`p_` の接頭辞と大文字・小文字は無視します。名前が取得できず、隣り合う 2 つのパラメーターが同じ型の場合、lambdaQL は WARN をログに出します。

**アドバイス:** `-parameters` でコンパイルし（[2. はじめよう](02-getting-started.md#recommended-compiler-option--parameters) を参照）、データベースと同じ名前を使ってください。

Oracle では、lambdaQL は本当の名前によるバインドも使えます（`pass_procedure_paramater_names=true` とスタブのパラメーター名を組み合わせます）。

---

<a id="85-procedure-rules-pr1-to-pr15-summary"></a>
## 8.5 プロシージャのルール PR1 から PR15（まとめ）

| # | ルール |
|---|---|
| PR1 | 名前: アノテーションの値をそのまま使います。値が空の場合: メソッド名を UPPER_SNAKE にしたもの。パッケージ名やスキーマ名（`SQ_PKG.CALC`、`LQL.SQ_CALC`）も使えます。混乱を避けるために `@SqlProcedure(schema = ..., pkg = ..., value = ...)` と書くこともできます。 |
| PR2 | 引数は **位置で** バインドされます。最初の呼び出しで、数、モード、型を DB のメタデータと照合します。同じ型の引数の入れ替わりは、パラメーター名がある場合だけ見つかります（[8.4](#84-warning-argument-order) を参照）。パッケージ内の Oracle のオーバーロードは v0.1 ではエラーです。 |
| PR3 | OUT が 1 つ → 戻り値。OUT/INOUT が複数 → record。`@InOut` は INOUT パラメーターを示します。 |
| PR4 | 結果セットが 1 つ → `List<T>`。エンティティは Hibernate で、record/DTO はラベルの対応付けを使って JDBC で読みます。ページングはありません。 |
| PR5 | 複数の結果セット → record の `List` コンポーネントに順番に入ります。Oracle の暗黙の結果は拒否されます。 |
| PR6 | 単独で呼び出す DB 関数は、データベースごとに異なる方法を使います（[8.8](#88-call-a-db-function-alone) を参照）。 |
| PR7 | クエリの中の DB 関数: [8.6](#86-declare-db-functions) とルール FN1 から FN11 を参照してください。 |
| PR8 | 条件の中でカラムに DB 関数を使うと、インデックスが使えないため WARN になります（[8.9](#89-warning-functions-on-columns-and-indexes-sargable) を参照）。 |
| PR9 | プロシージャは呼び出し元のトランザクションで実行されます。lambdaQL は必要なときに呼び出しの前にフラッシュします。呼び出しのあと、読み込み済みのエンティティが古くなっていることがあります。 |
| PR10 | 共通の影響行数はありません。OUT パラメーターを使ってください。 |
| PR11 | IN の値に `null` を使えます。DB のパラメーターのデフォルト値は使えません。 |
| PR12 | ラムダはスタブの呼び出し 1 つです。引数はキャプチャ値、定数、またはそれらの計算です。OUT パラメーターがあるのに `exec` を使うとエラーです。 |
| PR13 | boolean のパラメーターは MariaDB と Oracle 23ai 以降で使えます。 |
| PR14 | 各ラムダは 1 回だけ解析されます。プロシージャのメタデータは名前ごとに 1 回だけ読みます。 |
| PR15 | H2 には OUT、INOUT、複数の結果セット、パッケージがありません。プロシージャは MariaDB か Oracle でテストしてください（たとえば Testcontainers を使って）。 |

PR11、PR12、PR13 には Kotlin 用の追加部分（デフォルト引数、名前付き引数、null 許容型）があります。それらは [このページの Kotlin 版](../kotlin/08-procedures-functions.md) にあります。

---

<a id="86-declare-db-functions"></a>
## 8.6 DB 関数を宣言する

DB 関数は、データベースの中に作った関数です。たとえば `FN_DISCOUNT(price)` です。
これを `@SqlFunction` を付けた **インターフェースの static メソッド** として宣言します。
本体は例外を投げるだけにします。lambdaQL の中で実行されることはありません。

```java
public interface OrderFns {
    @SqlFunction("FN_DISCOUNT") static Double discount(Double price) { throw new DbOnlyException(); }
    @SqlFunction("FN_IS_VIP")   static Boolean isVip(Long customerId) { throw new DbOnlyException(); }  // Boolean: Oracle 23ai 以降
}
```

lambdaQL のラムダの **外** で `OrderFns.discount(...)` を呼ぶと、例外が投げられます。これは意図的です。

その他の宣言方法:

| 宣言 | 状況 |
|---|---|
| インターフェースの static メソッド | **推奨** |
| クラスの static メソッド | サポート |
| インターフェースの default メソッド + シングルトンの定数 | サポート。ただし書く量が多い |
| リポジトリが関数インターフェースを `implements` する（ミックスイン） | 拒否 |
| 関数型インターフェースの定数（`DISCOUNT.apply(x)`） | 拒否 |

関数は **インターフェース名を付けて**（`OrderFns.discount`）呼び出してください。static import も使えますが、同じ名前のメンバーメソッドがあると隠れてしまうことがあります。

---

<a id="87-use-db-functions-in-queries"></a>
## 8.7 クエリで DB 関数を使う

関数を直接呼び出します。コンテキストの引数は必要ありません。すべてのラムダで使えます:
`where`、`select`、`orderBy`、`groupBy`、`having`、`set`、そしてサブクエリの中です。

```java
qb.from(Order.class).where(o -> OrderFns.discount(o.getPrice()) > 10).toList();
// → where function('FN_DISCOUNT' as Double, o.price) > 10

qb.from(Order.class).where(o -> OrderFns.isVip(o.getCustomer().getId())).toList();   // Boolean の関数だけ

qb.from(Order.class).where(o -> o.getPrice() > OrderFns.discount(base)).toList();     // カラム側はそのまま

qb.from(Order.class).orderBy((o, by) -> by.desc(OrderFns.discount(o.getPrice()))).toList();

qb.from(Order.class).select(o -> new OrderView(o.getId(), o.getCustomer().getName(), OrderFns.discount(o.getPrice()))).toList();

qb.update(Order.class).set(o -> o.setPrice(OrderFns.discount(o.getPrice()))).all().execute();
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

<a id="how-lambdaql-renders-it"></a>
### lambdaQL の出力

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

lambdaQL は `FunctionContributor`（ServiceLoader で見つかるもの）を予定しています。インターフェースは Hibernate のプロパティに列挙します。

```properties
# Hibernate のみの場合
lambdaql.functions.classes=com.acme.OrderFns,com.acme.CustomerFns

# Spring Boot
spring.jpa.properties.lambdaql.functions.classes=com.acme.OrderFns
```

プロパティが設定されていない場合、何も登録されません。パッケージのスキャンは v0.1 にはありません。

<a id="mixed-case-names-need-registration"></a>
### 大文字と小文字が混ざった名前には登録が必要

引用符で囲み、大文字と小文字を混ぜて作った関数名（`"SQ_Fn_Mixed"`）は、登録しないと失敗します（H2 と Oracle では名前が小文字になります）。
キーと SQL の名前を分けて、インターフェースを登録してください。

```java
@SqlFunction(key = "SQ_Fn_Mixed", sql = "\"SQ_Fn_Mixed\"")
static Double mixed(Double v) { throw new DbOnlyException(); }
```

このような関数が登録されていない場合、lambdaQL は最初に使ったときに失敗します。

---

<a id="88-call-a-db-function-alone"></a>
## 8.8 DB 関数を単独で呼び出す

**引数のないラムダ** で `qb.call(() -> ...)` を使います。

```java
Double v = qb.call(() -> OrderFns.discount(price)).execute();
```

**データベースごとの SQL（予定）** — 予定の HQL を Hibernate が変換したもので、`PlannedSqlTest` が記録しました。lambdaQL はまだこのクエリを作りません。`expected-sql/` の [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)、例 `08-function-alone`:

```sql
-- H2, MariaDB, Oracle (same)
select fn_discount(?)
```

これは FROM のない `select fn_discount(?)` として実行されます。Oracle がこれを受け付けるのは 23ai からです。Oracle では、lambdaQL は代わりに JDBC の関数呼び出しを使います（下の表を参照）。

引数の数が異なるので、`qb.call(OrderProcs.class, p -> ...)` と衝突することはありません。

lambdaQL の実行方法（ルール PR6）:

| データベース | 方法 |
|---|---|
| Oracle | JDBC の関数呼び出し `{? = call FN(?)}`。DML を行う関数や、カーソルを返す関数でも使えます。 |
| MariaDB、H2 | HQL の `select function('FN' as T, :p1)` |

lambdaQL は先に保留中の変更をフラッシュするので（PR9 と同じ）、関数は最新の変更を見ることができます。

---

<a id="89-warning-functions-on-columns-and-indexes-sargable"></a>
## 8.9 警告: カラムに対する関数とインデックス（SARGable）

lambdaQL は **インデックスを使える** SQL を作ろうとします（[10. ルールと制限](10-rules-and-limits.md#102-sargable-rule-index-friendly-sql) を参照）。
カラムを関数で囲むと、通常のインデックスは普通 **使えません**。

```java
.where(o -> OrderFns.discount(o.getPrice()) > 10)
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
| カラムに対する関数: `fn(o.price) > 10` | WARN（ストアド関数はインデックスに使えない） | WARN。**ただし**、関数が `DETERMINISTIC` で、**かつ** 同じ式の関数ベースインデックスがある場合は除く |
| 値だけに対する関数: `o.price > fn(rate)` | 関数が `NOT DETERMINISTIC`（これがデフォルトです!）の場合は WARN。この場合、MariaDB はインデックス全体をスキャンします。 | 警告なし（インデックスの範囲スキャン） |

**アドバイス:**

- できるだけ関数を値の側に置いてください: `o.getPrice() > OrderFns.discount(base)`。
- MariaDB では、関数が決定的であれば `DETERMINISTIC` と宣言してください。
- Oracle では、`DETERMINISTIC` な関数と関数ベースインデックスを組み合わせると警告が消えます。

---

<a id="810-function-rules-fn1-to-fn11-summary"></a>
## 8.10 関数のルール FN1 から FN11（まとめ）

| # | ルール |
|---|---|
| FN1 | `@SqlFunction` はメソッドのアノテーションです。値が空の場合: メソッド名を UPPER_SNAKE にしたもの。 |
| FN2 | lambdaQL は呼び出されたメソッドを見つけ、そのアノテーションを読みます。メソッドの本体は例外を投げるだけ（`throw new ...`）でなければなりません。本当の本体を持つメソッドはエラーです。Java とデータベースで異なる値になる可能性があるからです。 |
| FN3 | 同じ名前のオーバーロードは、すべて同じ DB 関数に対応しなければなりません。そうでなければエラーです。 |
| FN4 | アノテーションのない呼び出しや、lambdaQL が特定できない呼び出しはエラーです。黙って読み飛ばすことはありません。 |
| FN5 | 引数は [3. 条件](03-where.md) の値のルールに従います。キャプチャ値だけで作った引数でも、バインドされて **データベースで** 評価されます。 |
| FN6 | ボクシングの呼び出しは読み飛ばします。戻り値の型は宣言されたメソッドから決まります。 |
| FN7 | カラムに対する関数は WARN になります（[8.9](#89-warning-functions-on-columns-and-indexes-sargable) を参照）。 |
| FN8 | Hibernate の組み込み関数（`COALESCE`、`ROUND`、`SYSDATE`、...）と同じ名前はエラーです。Hibernate が黙って組み込み関数を使ってしまうからです。接頭辞かスキーマ名（`PUBLIC.SQ_FN`）を使ってください。 |
| FN9 | Kotlin のみ（デフォルト引数、`inline`、`vararg`）。Kotlin トラックを参照してください。 |
| FN10 | v0.1 はスカラー関数だけです。ユーザー定義の集約関数とウィンドウ関数は v0.2 の予定です。引数のない関数は `fn()` として出力されます。 |
| FN11 | 既知の DB の違い: MariaDB では、VARCHAR を返す関数が文字列リテラルと照合順序の衝突を起こすことがあります。`BigDecimal` の結果のスケールが異なります（MariaDB と Oracle は 0、H2 は 1）。boolean の結果には Oracle 23ai 以降が必要です。 |

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
| クエリの中の DB 関数 | はい | はい | はい |
| boolean のパラメーター / 結果 | – | はい | はい（23ai 以降） |

v0.1 では予定していないもの: 動的なプロシージャ名のための文字列ビルダー（v0.2 の候補）、Oracle パッケージのオーバーロード、テーブル値関数、プロシージャの結果のページング。

この章のエラーは、`[LQL-xxxx]` コードを持つ `LambdaQLException` のサブクラスです。[11. エラーと言語](../11-errors-and-languages.md) を参照してください。

---

[Java トラック](README.md) | 前: [7. 更新、挿入、削除](07-dml.md) | 次: [9. Java の注意点](09-java-notes.md) | [このページの Kotlin 版](../kotlin/08-procedures-functions.md)
