<a id="11-errors-and-languages"></a>
# 11. エラーと言語

[マニュアルのホーム](README.md) | トラック: [Java](java/README.md) | [Kotlin](kotlin/README.md) | 前: 10. ルールと制限（[Java](java/10-rules-and-limits.md) | [Kotlin](kotlin/10-rules-and-limits.md)） | 次: 12. Spring Data JPA（[Java](java/12-spring-data-jpa.md) | [Kotlin](kotlin/12-spring-data-jpa.md)）

> [!NOTE]
> **状況:** 設計済み、**まだ実装されていません**。
> 現在の解析コアは、シンプルなメッセージ（今は韓国語）と元の原因を付けて `LambdaAnalysisException` を投げます。`[LQL-xxxx]` コードやメッセージバンドルはまだありません。
> エラーコード、基底クラス `LambdaQLException`、4 つのメッセージ言語、SLF4J によるログは設計で決まっています。このページのコード例は設計した API を示しています。設計上の例です。

このページは両方の言語向けです。コードは **Java** と **Kotlin** の別々のセクションに分けて載せています。

<a id="what-works-today"></a>
## 現在動くもの

解析コアは、すでに一部の書き方を拒否します。次のユニットテストでそれを確認できます。これらはユーザー向け API ではなく内部クラスを呼び出し、`LambdaAnalysisException` を期待します。

| テスト | 拒否するもの |
|---|---|
| [LoadExamplesTest.java](../../../query/src/test/java/org/lambdaql/engine/load/LoadExamplesTest.java)（`notSerializable`） | シリアライズ可能でないラムダ（`java.util.function.Predicate`） |
| [ConditionDecodeTest.java](../../../query/src/test/java/org/lambdaql/engine/decode/ConditionDecodeTest.java)（`valueTernaryIsRejected`） | 値の三項演算子: `o.getStatus() == (flag ? 1 : 2)` |

このセクションより下はすべて設計です。

<a id="error-codes"></a>
## エラーコード

lambdaQL のメッセージは、すべて `[LQL-xxxx]` の形のコードで始まります。
コードは言語に依存しません。コードの後ろのテキストが翻訳されます。

```
[LQL-2004] ...message text in the selected language...
```

| 範囲 | 領域 | 問題の例 |
|---|---|---|
| `LQL-1xxx` | 入力とラムダの読み込み | 引数がシリアライズ可能なラムダではない。匿名クラスである。クラスファイルを読めない。 |
| `LQL-2xxx` | バイトコードのデコード | ラムダの中のループ。値の三項演算子（`a ? 1 : 2`、Kotlin の `if (a) 1 else 2`）。lambdaQL がサポートしない命令。 |
| `LQL-3xxx` | 意味とホスト値 | SQL で意味を持たないメソッド呼び出し。`groupBy` のキーではない、集計されていない値。lambdaQL がキャプチャした値を評価するときの問題。 |
| `LQL-4xxx` | 生成と実行 | lambdaQL が HQL を組み立てる、または実行するときの問題。 |
| `LQL-5xxx` | 警告（ログに出力） | インデックスを使えない条件（SARGable でない）。 |

コードのルール:

- リリース後にコードが再利用されることはありません。コードで検索したり、コードの中でチェックしたりしても安全です。
- 正確なコード番号はまだ確定していません。メッセージが実装されたら、ここに一覧を載せます。

<a id="exceptions"></a>
## 例外

lambdaQL の例外はすべて `LambdaQLException` を継承します。これは `RuntimeException` なので、Kotlin でも Java でも同じように扱えます。

| 例外 | いつ | 例 |
|---|---|---|
| `LambdaQLException` | lambdaQL のすべての例外の基底クラス | – |
| `LambdaAnalysisException` | lambdaQL がラムダを読み込めない | ラムダではなく匿名クラス（Kotlin の `object : ...`）。シリアライズ可能でないラムダ。 |
| `UnsupportedLambdaShapeException` | バイトコードの形を lambdaQL がサポートしていない | ループ。値の三項演算子、値としての `if`/`when`。一般的な boolean の三項演算子 `c ? x : y`。 |
| `TranslationException` | ラムダに HQL での意味が定義されていない | getter ではないエンティティのメソッド。2 つのエンティティを `==` で比較する（まだサポートしていない）。`groupBy` のキーではない、集計されていない値。 |

`LambdaQLException` のメソッド:

| メソッド | 戻り値 |
|---|---|
| `code()` | 変わらないコード。たとえば `"LQL-2004"` |
| `key()` | メッセージキー（`LqlMessage` の値） |
| `args()` | メッセージの引数 |
| `getMessage()` | 例外が作られたときに選ばれていた lambdaQL の言語でのメッセージ |
| `getMessage(Locale locale)` | 同じメッセージを別の言語で。たとえば英語なら `Locale.ROOT` |

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
- 最後に、決まった形式でラムダの場所: ラムダ本体を持つメソッドと **ソースの行番号**。
- デコードのエラーでは、そのあとにバイトコードのダンプが追加の行として続くことがあります。この追加の行は翻訳されません。
- ほとんどのメッセージは、問題の直し方も教えてくれます。たとえば Kotlin のメッセージは、リストをラムダの外で作ってキャプチャするように伝えます。

<a id="in-tests-check-the-code"></a>
### テストではコードをチェックする

メッセージのテキストは変わることがあり、言語にも依存します。コードは変わりません。

#### Java

```java
var e = assertThrows(LambdaQLException.class, () -> query.toList());
assertEquals("LQL-2004", e.code());     // 良い
// assertEquals("Loops are ...", e.getMessage());   // これはしない
```

#### Kotlin

```kotlin
val e = assertThrows<LambdaQLException> { query.toList() }   // org.junit.jupiter.api.assertThrows
assertEquals("LQL-2004", e.code())      // 良い
// assertEquals("Loops are ...", e.message)          // これはしない
```

<a id="when-errors-happen"></a>
## エラーが起きるタイミング

lambdaQL は 3 つのタイミングで問題を見つけます。

| いつ | チェックされること | 例 |
|---|---|---|
| **コンパイル時** | Java や Kotlin のコンパイラーが lambdaQL API の型をチェックします。 | エンティティをソートキーに使う。`orderBy` を 2 回呼ぶ。`orderBy` の後に `where`。`where(...)` も `all()` もない update や delete で `execute()`。 |
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
「NULL にも一致させたい」ときは、null チェックを自分で書いてください。
null のルールはすべて 3. 条件（[Java](java/03-where.md) | [Kotlin](kotlin/03-where.md)）にあります。

#### Java

| 書き方 | `name` が `null` のとき | 結果 |
|---|---|---|
| `o.getDescription() == name`（参照の `==`） | 許可 | `e0.description is not distinct from :p1`（null 安全） |
| `Objects.equals(o.getDescription(), name)` | 許可 | null 安全、上と同じ |
| `o.getDescription().equals(name)` | バインド時にエラー | Java では決して true になりません。lambdaQL はこれを `is null` に変えません。 |
| `name.equals(o.getDescription())` | バインド時にエラー | Java では `NullPointerException` を投げます。 |
| `o.getStatus() > minStatus`（`minStatus` が `null`） | バインド時にエラー | null との大小比較は Java で意味を持ちません。 |

「NULL にも一致させる」: `o.getDescription() == null || o.getDescription().equals(name)`。

#### Kotlin

| 書き方 | キャプチャした値が `null` のとき | 結果 |
|---|---|---|
| `it.description == name` | 許可 | `e0.description is not distinct from :p1`（null 安全） |
| `minPrice == null \|\| it.price!! > minPrice` | 許可 | null チェックを書いたので、lambdaQL はそれを使います。 |
| `it.price!! > minPrice!!`（`minPrice` が `null`） | バインド時にエラー | null との大小比較は意味を持ちません。 |
| `it.id in ids`（`ids` に `null` の要素がある） | バインド時にエラー | null の要素を含む `IN` は SQL の落とし穴です。 |

<a id="placeholders-called-outside-a-lambda"></a>
### ラムダの外で呼ばれたプレースホルダー

ラムダの中で読まれるためだけに存在するメソッドがあります。その戻り値はプレースホルダーです。

| メソッド | クエリラムダの外で呼んだとき |
|---|---|
| `Agg.count()`、`Agg.sum(...)`、その他の `Agg` メソッド | `IllegalStateException` を投げる |
| 自分の `@SqlFunction` メソッド | lambdaQL が本体を実行することはありません。設計例の本体は `DbOnlyException` を投げます。 |

#### Java

```java
public interface OrderFns {
    @SqlFunction("FN_DISCOUNT") static Double discount(Double price) { throw new DbOnlyException(); }
}
```

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
org/lambdaql/i18n/LqlMessages.properties      English (root)
org/lambdaql/i18n/LqlMessages_ja.properties   Japanese
org/lambdaql/i18n/LqlMessages_zh.properties   Chinese (Simplified)
org/lambdaql/i18n/LqlMessages_ko.properties   Korean
```

- 引数は `{0}`、`{1}`、... と書きます。
- lambdaQL は `MessageFormat` を使いません。そのため、シングルクォート（`can't`）はそのまま書けます。数値は書式化されません（行番号 `1234567` は `1234567` のままです）。
- どのキーも 4 つのファイルすべてに必要です。ユニットテストがこれをチェックします。新しいメッセージは 4 つの言語すべてで追加してください。

<a id="logging"></a>
## ログ

lambdaQL は **SLF4J API** を通してログを出します。

- lambdaQL が依存するのは `slf4j-api` だけです。ログのバックエンド（Logback、Log4j 2 など）は自分で選びます。
- Spring Boot には SLF4J と Logback がすでに入っているので、何も追加する必要はありません。
- 警告は `WARN` レベルを使い、`LQL-5xxx` の範囲のコードで始まります。
- `WARN` レベルがオフなら、lambdaQL はメッセージのテキストを組み立てません。

警告の例（コードとテキストは例です。最終的なテキストは違うことがあります）:

```
WARN  [LQL-5001] ...the condition on column "price" cannot use an index...
```

たとえば次のようなときに、このような警告が出ます。

- 条件のカラム側でデータベース関数を使っていて（Java の `OrderFns.discount(o.getPrice()) > 10`、Kotlin の `OrderFns.discount(it.price)!! > 10`）、データベースがインデックスを使えないとき
- 大文字と小文字を区別しない比較に、そのカラムに合うインデックスや照合順序がないとき
- サブクエリに `orderBy` があって `limit` がないため、lambdaQL がソートを取り除くとき（ソートに効果がないため）

これらの警告をエラーに変える「厳格モード」を予定しています。

> [!NOTE]
> 現在の `query` モジュールには、まだいくつかの Spring Boot スターターが通常の依存関係として入っています。そのため、それら（と Logback）が lambdaQL と一緒にプロジェクトに入ってきます。設計では、これらを `compileOnly` に移します。その変更のあとは、lambdaQL が持ち込むのは `slf4j-api` だけになります。

<a id="summary"></a>
## まとめ

| 質問 | 答え |
|---|---|
| lambdaQL のエラーはどう見分けますか？ | `LambdaQLException` で、メッセージが `[LQL-xxxx]` で始まります。 |
| コードやテストでエラーをチェックするには？ | `e.code()` を使います。テキストを比較しないでください。 |
| 英語以外のサーバーで英語のテキストを得るには？ | `LqlMessages.setLocale(Locale.ROOT)`、`-Dlambdaql.locale=en`、または `e.getMessage(Locale.ROOT)`。 |
| 警告はどこに出ますか？ | SLF4J へ、`WARN` レベルで。 |

[マニュアルのホーム](README.md) | [Java トラック](java/README.md) | [Kotlin トラック](kotlin/README.md)
