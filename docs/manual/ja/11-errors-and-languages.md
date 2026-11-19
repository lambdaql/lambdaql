<a id="11-errors-and-languages"></a>
# 11. エラーと言語

[マニュアルのホーム](README.md) | トラック: [Java](java/README.md) | [Kotlin](kotlin/README.md) | 前: 10. ルールと制限（[Java](java/10-rules-and-limits.md) | [Kotlin](kotlin/10-rules-and-limits.md)） | 次: 12. Spring Data JPA（[Java](java/12-spring-data-jpa.md) | [Kotlin](kotlin/12-spring-data-jpa.md)）

このページは両方の言語向けです。コードは **Java** と **Kotlin** の別々のセクションに分けて載せています。

<a id="error-codes"></a>
## エラーコード

lambdaQL のメッセージは、すべて `[LQL-xxxx]` の形のコードで始まります。
コードは言語に依存しません。翻訳されるのはコードの後ろのテキストだけです。

**(テスト済み)** [`LqlMessagesTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/i18n/LqlMessagesTest.java)、リージョン `format`:

```java
LqlMessages.setLocale(Locale.ENGLISH);
assertEquals("[LQL-4014] limit must be 0 or more: -1", LqlMessage.LIMIT_NEGATIVE.format(-1));
// the same message in another language: the code stays
assertEquals("[LQL-4014] limit은 0 이상이어야 합니다: -1", LqlMessage.LIMIT_NEGATIVE.format(Locale.KOREAN, -1));
```

すべてのメッセージは enum `org.lambdaql.i18n.LqlMessage` の値です。値ごとにコードと引数の数を持ちます。

| 範囲 | 領域 | 現在のコード | 例外 |
|---|---|---|---|
| `LQL-1xxx` | 入力とラムダの読み込み | `LQL-1001`〜`LQL-1013` | `LambdaAnalysisException` |
| `LQL-2xxx` | バイトコードのデコード | `LQL-2001`〜`LQL-2025` | `UnsupportedLambdaShapeException` |
| `LQL-3xxx` | 意味の解釈と Java で計算する値 | `LQL-3001`〜`LQL-3067` | `TranslationException`（`LQL-3066` はサブクラスの `NonSargableException`） |
| `LQL-4xxx` | 生成、バインド、実行 | `LQL-4001`〜`LQL-4045`、`LQL-4999`（内部エラー） | `LambdaQLException` そのもの、または JDK/JPA の標準例外（[例外](#exceptions) を参照）。`LQL-4045` は `WARN` ログ（同じ基本型へのアンボクシングだけがある比較は `DEBUG`） |
| `LQL-5xxx` | 警告（ログに出力） | `LQL-5001`〜`LQL-5013` | –（ログ 1 行。レベルは `WARN`。`LQL-5004`〜`LQL-5006`、`LQL-5008` と、デフォルトのソートモードの `LQL-5003` は `INFO`、`LQL-5009`〜`LQL-5011`、`LQL-5013` は `DEBUG`） |

実際のコードの例:

| コード | 例外 | メッセージ（日本語） |
|---|---|---|
| `LQL-1002` | `LambdaAnalysisException` | Serializable なラムダだけを解析できます。java.util.function ではなく lambdaQL のラムダ型を使ってください: {0} |
| `LQL-2004` | `UnsupportedLambdaShapeException` | ラムダ内のループはサポートしていません。リストはラムダの外で作ってキャプチャしてください。 |
| `LQL-3005` | `TranslationException` | Agg の集計関数は where で使えません。having を使ってください（J3）: Agg.{0} |
| `LQL-3035` | `TranslationException` | 集計と一緒に使う値は groupBy のキーでなければなりません（J3）: {0} |
| `LQL-3058` | `TranslationException` | DB 関数 {0} は SQL 名が別にあるため登録が必要です。クラスを lambdaql.functions.classes の設定に追加してください。 |
| `LQL-3064` | `TranslationException` | 派生テーブル・CTE の行の型は Java の record か Kotlin の data class でなければなりません（J5）: {0} |
| `LQL-3065` | `TranslationException` | Dml.value: 値の型 {2} が列 {0}（{1}）に合いません（U20） |
| `LQL-3066` | `NonSargableException` | インデックスを使えない条件で、SARGable モードが STRICT です: {0} |
| `LQL-3067` | `TranslationException` | 派生テーブル・CTE の行には id がないため null と比較できません。列を比較してください（例: x.customerId() == null）: {0} |
| `LQL-4009` | `LambdaQLException` | null の値は <、<=、>、>= で比較できません（Java では NullPointerException）: {0} |
| `LQL-4014` | `IllegalArgumentException` | limit は 0 以上でなければなりません: {0} |
| `LQL-4025` | `NonUniqueResultException` | one() の条件に合う行が 2 行以上あります。条件を絞るか first() を使ってください: {0} |
| `LQL-4034` | `DbOnlyException` | {0} はデータベース関数です。lambdaQL のラムダの中でだけ使い、Java では実行されません。 |
| `LQL-4036` | `LambdaQLException` | スタブがデータベースのプロシージャ {0} と一致しません: {1} |
| `LQL-4040` | `SortNotAllowedException` | {0} ではこの名前でソートできません: {1}。SortRules の allow か alias で許可してください。 |
| `LQL-4043` | `LambdaQLException` | {0} のスタブ引数は、データベースの引数と名前が同じで順序だけが違います。スタブ: {1}、データベース: {2} |
| `LQL-4044` | `LambdaQLException` | Oracle パッケージにこの名前のプロシージャが複数あります（オーバーロード）。どれを呼ぶか区別できません: {0} |
| `LQL-4045` | –（実行時の `WARN` ログ。同じ基本型へのアンボクシングを経た比較は `DEBUG`） | {0} でキャプチャした値が null です。SQL では = NULL と <> NULL はどの行にも合いません。ラムダで x == null を使うか、クエリの前に値を確認してください。 |
| `LQL-5001` | –（`WARN` ログ） | 関数 {0} が列 {1} に適用されているため、列の通常のインデックスを使えず、全件スキャンになることがあります。列を値と比較する形に変えるか、（Oracle）関数ベースのインデックスを作ってください: {2} |
| `LQL-5002` | –（`WARN` ログ） | limit のないサブクエリのソートは結果に影響しないため削除しました（Q4）: {0} |
| `LQL-5003` | –（`INFO` または `WARN` ログ） | 外部からのソートが {0} のソート規則にありません: {1}。今は実行しますが、SortMode.STRICT では拒否されます。 |
| `LQL-5004` | –（`INFO` ログ） | クラスファイルにスタブ引数の名前がないため、{0} の引数の順序を確認しません。-parameters（Kotlin: -java-parameters）でコンパイルするか、@DbParam を使ってください。 |
| `LQL-5005` | –（`INFO` ログ） | データベースのメタデータから {0} の引数を読めませんでした（{1}）。スタブは照合せず、IN・INOUT はスタブの順、その後に OUT 引数が来るとみなします。 |
| `LQL-5006` | –（`INFO` ログ） | {0} のオーバーロードを確認できませんでした（{1}）。確認せずに呼び出します。 |
| `LQL-5007` | –（`WARN` ログ） | MariaDB の関数 {0} は NOT DETERMINISTIC のため行ごとに計算され、比較する列のインデックスが範囲検索に使われません。決定的な関数なら DETERMINISTIC と宣言してください: {1} |
| `LQL-5008` | –（`INFO` ログ） | {0} のインデックス・関数のメタデータを読めませんでした（{1}）。メタデータなしで SARGable の確認を続けます。 |
| `LQL-5009` | –（`DEBUG` ログ） | SQL の意味: {0} では値が NULL の行は条件に合いません（Java の null の扱いとは異なります）。含めるには `x == null \|\| ...` のように null の確認を書いてください。 |
| `LQL-5010` | –（`DEBUG` ログ） | 列 {0} は @Lob の列です。一部のデータベース（例: Oracle）は LOB を {1} に使えません。 |
| `LQL-5011` | –（`DEBUG` ログ） | 列挙型の列 {0} は文字列として保存されている（@Enumerated(STRING)）ため、データベースは宣言順ではなく名前順でソートします。 |
| `LQL-5012` | –（`WARN` ログ） | 列 {0} に演算をかけると条件がインデックスを使えません。同じ意味のまま値の側に移してください。小数で割り（o.getQty() * 2 > n の代わりに o.getQty() > n / 2.0。n / 2 は整数除算）、負の数を掛けた場合は比較の向きを逆にしてください: {1} |
| `LQL-5013` | –（`DEBUG` ログ） | 条件が列 {0} を計算するため、その列のインデックスを使えません: {1} |

`{0}` と `{1}` は引数です。たとえば式やプロシージャ名が入ります。
一覧はメッセージファイルにあります（[翻訳者向け](#for-translators) を参照）。

コードのルール:

- リリース後にコードが再利用されることはありません。コードで検索したり、コードの中でコードの値をチェックしたりしても安全です。
- すべてのコードが重複せず、`LQL-` と 4 桁の数字の形であることを、ユニットテストがチェックします。

<a id="exceptions"></a>
## 例外

lambdaQL の例外はすべて `org.lambdaql.LambdaQLException` を継承します。これは `RuntimeException` なので、Kotlin でも Java でも同じように扱えます。
例外のクラスはコードの範囲で決まります。

| 例外 | コード | いつ | 例 |
|---|---|---|---|
| `LambdaQLException` | `LQL-4xxx` | lambdaQL のすべての例外の基底クラス。生成、バインド、実行の問題では、このクラスそのものを投げます | プロシージャのスタブがデータベースと一致しない（`LQL-4036`）。スタブ引数がデータベースの引数名と違う順序（`LQL-4043`。[8.4](java/08-procedures-functions.md#84-warning-argument-order) の `@DbParam` を参照）。 |
| `LambdaAnalysisException` | `LQL-1xxx` | lambdaQL がラムダを読み込めないとき | ラムダではなく匿名クラス（Kotlin の `object : ...`）。シリアライズ可能でないラムダ（`LQL-1002`）。 |
| `UnsupportedLambdaShapeException` | `LQL-2xxx` | バイトコードの形を lambdaQL がサポートしていないとき | ループ（`LQL-2004`）。値の三項演算子、値としての `if`/`when`。一般的な boolean の三項演算子 `c ? x : y`。 |
| `TranslationException` | `LQL-3xxx` | ラムダに HQL での意味が定義されていないとき | getter ではないエンティティのメソッド。2 つのエンティティを `==` で比較する（まだサポートしていない）。値オブジェクトの大小比較（`LQL-3012`）。`groupBy` のキーではない、集計されていない値（`LQL-3035`）。 |
| `DbOnlyException` | `LQL-4034` | 自分の `@SqlFunction` メソッドを、lambdaQL のラムダの外で Java から呼んだとき | `OrderFns.discount(1.0)`（[ラムダの外で呼ばれたプレースホルダー](#placeholders-called-outside-a-lambda) を参照） |
| `NonSargableException` | `LQL-3066` | `SargableMode.STRICT` で、インデックスを使えない条件（`LQL-5001`、`LQL-5007`、`LQL-5012` に当たる場合）を解析したとき。`TranslationException` のサブクラス | `OrderFns.discount(o.getTotal()) > 100`、`o.getCnt() * 2 > n`（決定 D50） |
| `SortNotAllowedException` | `LQL-4040` | `SortMode.STRICT` で、外部のソート（Spring の `Sort`、`SortSpec.ofPath`）がソートのルールにない名前を使ったとき | `name` だけを許可しているときの `Sort.by("grade")`（[4.10 ソートポリシー](java/04-order-paging.md#sort-policy) を参照）。Web アプリケーションは通常 HTTP 400 で応答します（[12.5](java/12-spring-data-jpa.md#sort-policy-with-spring)） |

これらのクラスはすべてパッケージ `org.lambdaql` にあります。

**標準の例外は型を保ちます。** Java のコードが JDK や JPA の例外を期待する場面では、lambdaQL もその型を投げ、メッセージにだけコードを付けます。

| 例外 | 例 | コード |
|---|---|---|
| `IllegalArgumentException` | `limit(-1)` | `LQL-4014` |
| `IllegalStateException` | プリミティブのコンストラクター引数への NULL（ルール J4）。ラムダの外で呼んだ `Agg.sum(...)`。行数より要素が少なくなった `toSet()` | `LQL-4021`、`LQL-4032`、`LQL-4019` |
| `jakarta.persistence.NonUniqueResultException` | `one()` の結果が 2 行以上。`executeOne()` の条件に 2 行以上が一致 | `LQL-4025`、`LQL-4023` |
| `jakarta.persistence.TransactionRequiredException` | トランザクションなしの update、insert、delete | `LQL-4024` |

`LambdaQLException` のメソッド:

| メソッド | 戻り値 |
|---|---|
| `code()` | 変わらないコード。たとえば `"LQL-2004"` |
| `key()` | メッセージキー（`LqlMessage` の値） |
| `args()` | メッセージの引数 |
| `where()` | ラムダの場所（ラムダ本体を持つメソッドと、分かる場合は行番号）。なければ `null` |
| `getMessage()` | 例外が作られたときに選ばれていた lambdaQL の言語でのメッセージ |
| `getMessage(Locale locale)` | 同じメッセージを別の言語で。たとえば英語なら `Locale.ROOT` |

**(テスト済み)** [`LqlMessagesTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/i18n/LqlMessagesTest.java)、リージョン `exception`:

```java
LqlMessages.setLocale(Locale.KOREAN);
LambdaQLException e = LqlMessage.LOOP_NOT_SUPPORTED.exceptionAt("app.Repo.lambda$find$0 (line 12)");
// check the code in tests, not the text
assertEquals("LQL-2004", e.code());
assertInstanceOf(UnsupportedLambdaShapeException.class, e);
assertTrue(e.getMessage().startsWith("[LQL-2004] 람다 안의 반복문은"), e.getMessage());
// the same message in English, with the place of the lambda at the end
assertEquals("[LQL-2004] Loops are not supported in a lambda. Build the list outside the lambda and capture it."
        + " — app.Repo.lambda$find$0 (line 12)", e.getMessage(Locale.ROOT));
```

このテストは例外を直接作っています。実際のコードでは、lambdaQL がラムダを読むときに例外を作ります。
扱い方は次のとおりです（設計上の例）。

### Java

```java
try {
    List<Order> orders = qb.from(Order.class)
        .where(o -> o.getStatus() == (flag ? 1 : 2))   // 値の三項演算子: サポートしていない
        .toList();
} catch (LambdaQLException e) {
    log.error("lambdaQL error {}: {}", e.code(), e.getMessage(Locale.ROOT));
    throw e;
}
```

### Kotlin

```kotlin
try {
    qb.from(Order::class.java)
        .where { it.status == if (flag) 1 else 2 }      // 値としての if: サポートしていない
        .toList()
} catch (e: LambdaQLException) {
    log.error("lambdaQL error {}: {}", e.code(), e.getMessage(Locale.ROOT))
    throw e
}
```

<a id="what-a-message-has"></a>
### メッセージに含まれるもの

- コード、続いて選択した言語のテキスト。
- 最後に、決まった形式でラムダの場所が付きます。` — ` に続けて、ラムダ本体を持つメソッドと **ソースの行番号** が来ます（`where()` でも取得できます）。
- デコードのエラーでは、バイトコードのダンプを含む行が追加で続くことがあります。この追加の行は翻訳されません。
- ほとんどのメッセージは、問題の直し方も示します。たとえば Kotlin のメッセージは、リストをラムダの外で作ってキャプチャするように伝えます。

<a id="in-tests-check-the-code"></a>
### テストではコードをチェックする

メッセージのテキストは変わることがあり、言語にも依存します。コードは変わりません。

#### Java

```java
var e = assertThrows(LambdaQLException.class, () -> query.toList());
assertEquals("LQL-2004", e.code());     // 良い
// assertEquals("Loops are ...", e.getMessage());   // これはしないでください
```

#### Kotlin

```kotlin
val e = assertThrows<LambdaQLException> { query.toList() }   // org.junit.jupiter.api.assertThrows
assertEquals("LQL-2004", e.code())      // 良い
// assertEquals("Loops are ...", e.message)          // これはしないでください
```

<a id="when-errors-happen"></a>
## エラーが起きるタイミング

lambdaQL は 3 つのタイミングで問題を見つけます。

| いつ | チェックされること | 例 |
|---|---|---|
| **コンパイル時** | Java や Kotlin のコンパイラーが lambdaQL API の型をチェックします。 | エンティティをソートキーに使う。`orderBy` を 2 回呼ぶ。`orderBy` の後に `where`。`where(...)` も `all()` もない update や delete で `execute()` を呼ぶ。 |
| **解析時** | SQL がデータベースに送られる前に、lambdaQL がラムダのバイトコードを読みます。 | ループ、値の三項演算子、サポートしていないメソッド呼び出し、単純な `by` チェーンではないソートラムダ、`groupBy` のキーではない `select` の値。 |
| **実行時（バインド）** | クエリを実行するたびに、lambdaQL がキャプチャした値をバインドします。 | ルールが null を許さない場所でキャプチャした値が `null`（下を参照）。`NaN` や `Infinity` のパラメーター。`IN` に使うキャプチャしたコレクションの中に `null` の要素。 |

解析時のエラーはデータに依存しません。同じラムダは、いつも同じように失敗します。
lambdaQL は失敗した結果もキャッシュするので、問題のあるラムダは次からすぐに失敗します。

<a id="compile-time-examples"></a>
### コンパイル時の例

#### Java

```java
.orderBy((o, by) -> by.desc(o.getCustomer()))   // コンパイルエラー: エンティティはソートキーではない
```

#### Kotlin

```kotlin
.orderBy { o, by -> by.desc(o.customer) }        // コンパイルエラー: エンティティはソートキーではない
```

<a id="analysis-time-errors-that-only-kotlin-has"></a>
### Kotlin だけにある解析時のエラー

Kotlin の一部の書き方では、lambdaQL が読めるラムダになりません。lambdaQL は分かりやすいエラーでそれを拒否します。
一覧は [9. Kotlin の注意点](kotlin/09-kotlin-notes.md) にあります。

| Kotlin の書き方 | 代わりに書くもの |
|---|---|
| `val f: (Order) -> Boolean = { ... }; where(f)` | ラムダリテラルを使った `where { ... }` |
| `inline fun` の本体の中で作られたクエリラムダ | ヘルパーを inline でなくする、または呼び出し側からラムダリテラルを渡す |
| `it.status in listOf(1, 2, 3)`（ラムダの中で作ったリスト） | 外で `val ids = listOf(1, 2, 3)` を作り、`it.status in ids` |
| `-Xsam-conversions=class` でのコンパイル | デフォルトの `-Xsam-conversions=indy` のままにする |

<a id="null-captured-values"></a>
### null のキャプチャ値

キャプチャした値が `null` でもよい書き方と、そうでない書き方があります。
`=` や `<>` に使われたキャプチャ値の `null` は、どの行にも一致しません（SQL の意味、決定 D48）。このとき lambdaQL は、実行時に `LQL-4045` を `WARN` レベルでログに出します。大小比較のように同じ基本型へのアンボクシング（`min.doubleValue()`）を経た場合は `DEBUG` レベルです。
NULL に一致させるには、null チェックを自分で書いてください。
null のルールはすべて 3. 条件（[Java](java/03-where.md) | [Kotlin](kotlin/03-where.md)）にあります。

#### Java

| 書き方 | `name` が `null` のとき | 結果 |
|---|---|---|
| `o.getDescription() == name`（参照の `==`） | 実行される | NULL をバインドした `e0.description = :p1`: 一致する行はない。`WARN` レベルの `LQL-4045` |
| `Objects.equals(o.getDescription(), name)` | 実行される | 上と同じ |
| `o.getDescription().equals(name)` | 実行される | 上と同じ |
| `name.equals(o.getDescription())` | 実行される | 上と同じ |
| `o.getStatus() > minStatus`（`minStatus` が `null`） | 実行される | NULL をバインドした `e0.status > :p1`: 一致する行はない。同じ基本型へのアンボクシング（`minStatus.intValue()`）は実行しない。`DEBUG` レベルの `LQL-4045` |
| `name.trim()`、`minStatus.longValue()`（`Integer` を別の基本型に）のようなキャプチャ値のメソッド呼び出し | エラー（`LQL-4012`） | クエリの前に Java で値を準備する段階なので、Java と同じく `NullPointerException` に当たります。 |

NULL を探すには `o.getDescription() == null` と書きます。
`name` が null のときにフィルターを省くには、`name == null || o.getDescription().equals(name)` と書きます（ガードはクエリの実行前に決まります）。

#### Kotlin

| 書き方 | キャプチャした値が `null` のとき | 結果 |
|---|---|---|
| `it.description == name` | 実行される | NULL をバインドした `e0.description = :p1`: 一致する行はない。`WARN` レベルの `LQL-4045` |
| `minPrice == null \|\| it.price!! > minPrice` | 許可 | null チェックを書いたので、lambdaQL はそれを使います。 |
| `it.price!! > minPrice!!`（`minPrice` が `null`） | 実行される | NULL をバインドした `e0.price > :p1`: 一致する行はない。`!!` とアンボクシングは実行しない。`DEBUG` レベルの `LQL-4045` |
| `it.id in ids`（`ids` に `null` の要素がある） | バインド時にエラー | null の要素を含む `IN` は SQL の落とし穴です。 |

<a id="placeholders-called-outside-a-lambda"></a>
### ラムダの外で呼ばれたプレースホルダー

ラムダの中で読まれるためだけに存在するメソッドがあります。その戻り値はプレースホルダーです。

| メソッド | クエリラムダの外で呼んだとき |
|---|---|
| `Agg.count()`、`Agg.sum(...)`、その他の `Agg` メソッド | `IllegalStateException` を投げる（`LQL-4032`） |
| 自分の `@SqlFunction` メソッド | lambdaQL が本体を実行することはありません。本体は `DbOnlyException`（`LQL-4034`）を投げるだけにします。値を計算する本体は拒否されます（`LQL-3052`）。 |

#### Java

**(テスト済み)** [`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java)、リージョン `rules`（最初の部分）:

```java
// the body never runs in Java
assertEquals("LQL-4034", assertThrows(DbOnlyException.class, () -> OrderFns.discount(1.0)).code());
```

`OrderFns` の宣言は 8. プロシージャと DB 関数（[Java](java/08-procedures-functions.md#86-declare-db-functions) | [Kotlin](kotlin/08-procedures-functions.md#86-declare-db-functions)）にあります。

#### Kotlin

```kotlin
interface OrderFns { companion object {
    @JvmStatic @SqlFunction("FN_DISCOUNT") fun discount(price: Double?): Double? = throw DbOnlyException()
} }
```

<a id="message-language"></a>
## メッセージの言語

lambdaQL のメッセージは **4 つの言語** で用意されています。

| 言語 | ロケール |
|---|---|
| 英語 | デフォルト（root） |
| 日本語 | `ja` |
| 中国語（簡体字） | `zh` |
| 韓国語 | `ko` |

<a id="how-lambdaql-chooses-the-language"></a>
### lambdaQL が言語を選ぶ方法

lambdaQL は次の 3 つの場所をこの順番でチェックします。最初に設定されているものが使われます。

| 順番 | 設定 | 例 |
|---|---|---|
| 1 | コードの中の `LqlMessages.setLocale(...)` | 下のコードを参照 |
| 2 | システムプロパティ `lambdaql.locale` | `java -Dlambdaql.locale=ja -jar app.jar` |
| 3 | JVM のデフォルトロケール | `Locale.getDefault()` |

そのあと、lambdaQL はそのロケールの **言語** だけを見ます。

| ロケールの言語 | メッセージの言語 |
|---|---|
| `ja`（たとえば `ja_JP`） | 日本語 |
| `zh`（`zh_CN`、さらに `zh_TW` も） | 中国語（簡体字） |
| `ko`（たとえば `ko_KR`） | 韓国語 |
| それ以外（`en`、`fr`、`de`、...） | 英語 |

**(テスト済み)** [`LqlMessagesTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/i18n/LqlMessagesTest.java)、リージョン `locale`:

```java
// 1. setLocale wins, 2. then -Dlambdaql.locale, 3. then the JVM default
LqlMessages.setLocale(Locale.JAPANESE);
assertEquals(Locale.JAPANESE, LqlMessages.locale());
LqlMessages.setLocale(null);
System.setProperty(LqlMessages.LOCALE_PROPERTY, "ko_KR");
assertEquals(Locale.KOREAN, LqlMessages.bundleLocale(LqlMessages.locale()));
// only the language counts; other languages get English (the root bundle)
assertEquals(Locale.CHINESE, LqlMessages.bundleLocale(Locale.TRADITIONAL_CHINESE));
assertEquals(Locale.ROOT, LqlMessages.bundleLocale(Locale.FRENCH));
```

`LqlMessages.LOCALE_PROPERTY` は名前 `"lambdaql.locale"` です。`LqlMessages.bundleLocale(locale)` は、そのロケールがどのメッセージファイルを使うかを返します。

アプリケーションでは次のように書きます（設計上の例）。

#### Java

```java
// アプリケーションの起動時に
LqlMessages.setLocale(Locale.JAPANESE);   // 日本語のメッセージ
LqlMessages.setLocale(null);              // 元に戻す: システムプロパティ、次に JVM のデフォルト
Locale current = LqlMessages.locale();    // lambdaQL が今使っているロケール
```

#### Kotlin

```kotlin
// アプリケーションの起動時に
LqlMessages.setLocale(Locale.JAPANESE)    // 日本語のメッセージ
LqlMessages.setLocale(null)               // 元に戻す: システムプロパティ、次に JVM のデフォルト
val current: Locale = LqlMessages.locale() // lambdaQL が今使っているロケール
```

注意:

- lambdaQL は言語を自分で選びます。通常の `ResourceBundle` のフォールバックは使いません。そのため、英語を指定したのに韓国語の JVM で韓国語のメッセージが出る、ということはありません。
- メッセージは例外が作られたときに決まります。あとで別の言語が必要なら、`e.getMessage(locale)` を呼んでください。
- サーバーは英語以外の JVM ロケールで動いていることがあります。英語のログが必要なら、`LqlMessages.setLocale(Locale.ROOT)` を呼ぶか、`-Dlambdaql.locale=en` を使ってください。
- 繁体字中国語のメッセージは、要望があれば追加できます。
- 日本語と中国語のテキストは、ネイティブスピーカーではなく作者が最初に書いたものです。改善の手伝いを歓迎します。

<a id="for-translators"></a>
### 翻訳者向け

メッセージファイルは jar の中にある UTF-8 の properties ファイルです。

```
org/lambdaql/i18n/LqlMessages.properties      英語（root）
org/lambdaql/i18n/LqlMessages_ja.properties   日本語
org/lambdaql/i18n/LqlMessages_zh.properties   中国語（簡体字）
org/lambdaql/i18n/LqlMessages_ko.properties   韓国語
```

- キーは `LqlMessage` の値の名前です。たとえば `LOOP_NOT_SUPPORTED`。
- 引数は `{0}`、`{1}`、... と書きます。
- lambdaQL は `MessageFormat` を使いません。そのため、シングルクォート（`can't`）はそのまま書けます。数値は書式化されません（行番号 `1234567` は `1234567` のままです）。
- どのキーも 4 つのファイルすべてに、同じ引数で必要です。ユニットテストがこれをチェックします。新しいメッセージは 4 つの言語すべてで追加してください。

**(テスト済み)** [`LqlMessagesTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/i18n/LqlMessagesTest.java)、リージョン `fill`:

```java
// no MessageFormat: quotes and big numbers stay as they are (rule I4)
assertEquals("can't read line 1234567", LqlMessages.fill("can't read line {0}", 1234567));
assertEquals("a null b", LqlMessages.fill("a {0} b", (Object) null));
assertEquals("{x} {9}", LqlMessages.fill("{x} {9}", "unused"));
```

<a id="logging"></a>
## ログ

lambdaQL は **SLF4J API** を通してログを出します。

- lambdaQL が依存するのは `slf4j-api` だけです。ログのバックエンド（Logback、Log4j 2 など）は自分で選びます。
- Spring Boot には SLF4J と Logback がすでに入っているので、何も追加する必要はありません。
- 警告メッセージはコードで始まります。コードの多くは `LQL-5xxx` の範囲で、`LQL-4045` もログにだけ出します。
- レベル（`WARN`、`INFO`、`DEBUG`）はコードごとに異なります。コードごとのレベルは下の表に書いています。
- そのレベルがオフなら、lambdaQL はメッセージのテキストを組み立てません。

現在 lambdaQL が出す警告:

| コード | いつ |
|---|---|
| `LQL-4045` | `=`・`<>`（`==`、`!=`、`equals`、`Objects.equals`）でキャプチャした値が `null` のため、合う行がないとき（SQL の意味、決定 D48）。実行するときにだけ分かる。`WARN` レベルで出る。大小比較のように同じ基本型へのアンボクシング（`min.doubleValue()`）を経た場合は `DEBUG` レベル。NULL を探すには `x == null`（3.6 を参照: [Java](java/03-where.md#36-null-rules) \| [Kotlin](kotlin/03-where.md#36-null-rules)） |
| `LQL-5001` | 条件で DB 関数がカラムを包んでいるとき（`OrderFns.discount(o.getPrice()) > 10`、カラムに対する Boolean の関数、`IN` や `IS NULL` の中の関数）。通常のインデックスを使えない。`where` のラムダごとに 1 回、`WARN` レベルで出る。Oracle で同じ式の関数ベースのインデックスがあれば出ない（[8.9](java/08-procedures-functions.md#89-warning-functions-on-columns-and-indexes-sargable) を参照） |
| `LQL-5002` | サブクエリに `orderBy` があって `limit` がないため、lambdaQL がソートを取り除くとき（ソートは結果に影響しない、ルール Q4） |
| `LQL-5003` | 外部のソート（Spring の `Sort`、`SortSpec.ofPath`）がソートのルールにない名前を使い、モードが `SortMode.INFO`（デフォルト、`INFO` レベルで出る）か `SortMode.WARN`（`WARN` レベルで出る）のとき。ソートは実行される。エンティティと名前ごとに 1 回出る（[4.10 ソートポリシー](java/04-order-paging.md#sort-policy) を参照） |
| `LQL-5004` | クラスファイルにプロシージャのスタブの引数名がなく（`-parameters`、Kotlin の `-java-parameters` なしでコンパイルし、`@DbParam` もない）、隣り合う 2 つの引数の型が同じとき。プロシージャごとに 1 回、`INFO` レベルで出る（[8.4](java/08-procedures-functions.md#84-warning-argument-order) を参照） |
| `LQL-5005` | プロシージャのメタデータを読めないとき（たとえばディクショナリビューや `information_schema` の権限がなく、取得が失敗する）。スタブは照合せず、引数の並びはスタブから取る。プロシージャごとに 1 回、`INFO` レベルで出る（[8.4](java/08-procedures-functions.md#when-the-metadata-cannot-be-read) を参照） |
| `LQL-5006` | Oracle のオーバーロードの取得（`all_arguments`）だけが失敗したとき。オーバーロードのチェックなしで呼び出す。`INFO` レベルで出る |
| `LQL-5007` | MariaDB のみ: 値の側の `NOT DETERMINISTIC` な DB 関数をカラムと比べるとき（`o.getPrice() > OrderFns.nondet(base)`）。インデックスが範囲検索に使われない。`WARN` レベルで出る |
| `LQL-5008` | SARGable の確認のためのインデックスや関数のメタデータを読めないとき（たとえば権限がない）。`INFO` レベルで出る。`LQL-5001` はそのまま出し、`LQL-5007` は確認しない |
| `LQL-5009` | 条件がエンティティの値を SQL の意味で比べるため（決定 D48）、Java や Kotlin なら合う NULL の行が外れることがあるとき: エンティティの値どうしの `==`/`equals`、値との `!=`、NULL になりうる `Boolean` の `!`、関連のパスの null ガード（パスは INNER JOIN のまま）、サブクエリの `NOT IN`。条件の文ごとに 1 回、`DEBUG` レベルで出る（3.6.3 を参照: [Java](java/03-where.md#363-write-your-null-checks-yourself) \| [Kotlin](kotlin/03-where.md#363-write-your-null-checks-yourself)） |
| `LQL-5010` | `@Lob` カラムを `order by`、`distinct`、`group by` に使うとき。H2 と MariaDB は実行するが、Oracle は拒否する（ORA-22848、ORA-22849）。`=` の比較は 3 つのデータベースすべてで動く。lambdaQL はクエリを変えない。カラムと使い方ごとに 1 回、`DEBUG` レベルで出る（[4.5](java/04-order-paging.md#45-what-a-sort-key-can-be) を参照） |
| `LQL-5011` | `@Enumerated(EnumType.STRING)` の enum カラムでソートするとき。データベースは名前順、Java・Kotlin は宣言順でソートするので、順序が異なることがある。lambdaQL はクエリを変えない。カラムごとに 1 回、`DEBUG` レベルで出る。`ORDINAL` では出ない（[4.5](java/04-order-paging.md#45-what-a-sort-key-can-be) を参照） |
| `LQL-5012` | カラム 1 つと値だけの演算（`+ - * /`）が条件のカラム側にあるとき（`o.getCnt() * 2 > n`）。値の側に移せる。`WARN` レベルで出る。`SargableMode.STRICT` ではエラー `LQL-3066`（決定 D50、[8.9](java/08-procedures-functions.md#89-warning-functions-on-columns-and-indexes-sargable) を参照） |
| `LQL-5013` | カラムどうしの演算や `%` が条件にあるとき（`o.getPrice() * o.getCnt() > n`）。移せないので `DEBUG` レベルでだけ出る。`STRICT` でも許可 |

```
WARN  [LQL-5002] limit のないサブクエリのソートは結果に影響しないため削除しました（Q4）: ...
```

予定している警告（まだ実装されていません）:

- 大文字と小文字を区別しない比較に、そのカラムに合うインデックスや照合順序がないとき

すべての警告を一度にエラーに変える一般的な厳格モードは実装されていません。
代わりに、2 つの領域にそれぞれの `STRICT` モードがあります。

- ソートポリシー（決定 D43）: `SortMode.STRICT` では、`LQL-5003` の場合に `SortNotAllowedException`（`LQL-4040`）が発生します。
- SARGable（決定 D50）: `SargableMode.STRICT` では、`LQL-5001`、`LQL-5007`、`LQL-5012` の場合に `NonSargableException`（`LQL-3066`）が発生します。

> [!NOTE]
> `lambdaql-core` は Spring に依存しません。ログの依存関係は `slf4j-api` 1 つだけなので、ログの実装は利用者が選びます。Spring モジュール（`lambdaql-spring-data-jpa`、`lambdaql-spring-boot-starter`）は Spring Data JPA を持ち込み、スターターはさらに `spring-boot-starter-data-jpa` と Logback も持ち込みます。

<a id="summary"></a>
## まとめ

| 質問 | 答え |
|---|---|
| lambdaQL のエラーはどう見分けますか？ | `LambdaQLException` で、メッセージが `[LQL-xxxx]` で始まります。 |
| コードやテストでエラーをチェックするには？ | `e.code()` を使います。テキストを比較しないでください。 |
| 英語以外のサーバーで英語のテキストを得るには？ | `LqlMessages.setLocale(Locale.ROOT)`、`-Dlambdaql.locale=en`、または `e.getMessage(Locale.ROOT)`。 |
| 警告はどこに出ますか？ | SLF4J へ、`WARN` レベルで出ます。 |

[マニュアルのホーム](README.md) | [Java トラック](java/README.md) | [Kotlin トラック](kotlin/README.md)
