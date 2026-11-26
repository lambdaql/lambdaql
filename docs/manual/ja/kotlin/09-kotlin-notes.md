<a id="9-kotlin-notes"></a>
# 9. Kotlin の注意点

> [!IMPORTANT]
> このページは韓国語版のマニュアルより古い内容です。最新のルールは[韓国語のページ](../../ko/kotlin/09-kotlin-notes.md)を参照してください。

[Kotlin トラック](README.md) | [マニュアルのホーム](../README.md) | 前: [8. プロシージャと DB 関数](08-procedures-functions.md) | 次: [10. ルールと制限](10-rules-and-limits.md) | [このページの Java 版: 9. Java の注意点](../java/09-java-notes.md)

共通ページ: [1. 概要](../01-overview.md) · [11. エラーと言語](../11-errors-and-languages.md)

lambdaQL は、**同じエンジン** と同じ API で Kotlin と Java をサポートします。
意味が同じ Kotlin のラムダ式と Java のラムダ式は、同じ HQL を作ります。
この章では、Kotlin で特別なことを 1 か所にまとめました。

<a id="contents"></a>
## 目次

- [9.1 設定](#91-setup)
- [9.2 動くラムダ式の形](#92-lambda-forms-that-work)
- [9.3 自分で書いた null チェックとコンパイラーの null チェック](#93-only-your-null-checks-count)
- [9.4 等値比較: `==`、`!=`、`===` と `areEqual`](#94-equality----and-areequal)
- [9.5 null 演算子: `?.`、`?:` と `!!`](#95-null-operators---and-)
- [9.6 `in`、範囲と `when`](#96-in-ranges-and-when)
- [9.7 文字列](#97-strings)
- [9.8 キャプチャした値](#98-captured-values)
- [9.9 プロパティ参照とスコープ関数](#99-property-references-and-scope-functions)
- [9.10 Kotlin の演算子](#910-kotlin-operators)
- [9.11 結果: data class、`Pair` と `Triple`](#911-results-data-classes-pair-and-triple)
- [9.12 更新、挿入、削除: プロパティへの代入と `Dml.value`](#912-update-insert-delete-property-assignment-and-dmlvalue)
- [9.13 プロシージャと DB 関数](#913-procedures-and-db-functions)
- [9.14 Kotlin の制限（KC1〜KC21）](#914-kotlin-limits-kc1-to-kc21)
- [9.15 lambdaQL が Kotlin のコードの実行と違う点](#915-where-lambdaql-differs-from-running-kotlin-code)

例の読み方:

- 例の `assertEquals` の文字列が、lambdaQL の作る HQL です。
- ラムダ式に直接書いた定数（数値、文字列、boolean、`null`、enum 定数、`const val`）は、HQL にリテラルとして書きます。キャプチャした値と計算した値は、常にバインドパラメーター（`:p1`、`:p2`、...）です。
- `e0` はルートエンティティの別名で、`j1` は lambdaQL が関連のパスのために追加する結合の別名です。`where` では、関連のない行が条件に一致しうるなら `left join`、一致しえないなら内部結合の `join` です。パスを `orderBy` や `select` にだけ使うと `left join` です。サブクエリのルートは `s1`、`s2`、... です（[5. サブクエリ](05-subquery.md) を参照）。
- エンティティは、Java で書いた例のエンティティ（`Order`、`Customer`、`Item`、`Coded`）です。Kotlin は Java の getter をプラットフォーム型のプロパティとして見ます。
  `Order.status` は `short` なので条件は `it.status.toInt() == 1` と書き、boolean の `active` は `it.isActive` で読みます。NULL になりうる Boolean の例は `Coded.vip` を使います。

---

<a id="91-setup"></a>
## 9.1 設定

| 項目 | 値 |
|---|---|
| Kotlin | 2.x。設計は kotlinc 1.7.22、2.1.10、2.2.21、2.3.0、2.3.21 で確認 |
| JVM | Java 21 以上 |
| コンパイラーのオプション | デフォルトの `-Xsam-conversions=indy` のままにする。`-Xsam-conversions=class` は使わ **ない** |
| `-jvm-target` | 1.8、11、17、21 のどれでも動く |
| Kotlin ランタイム | lambdaQL は `kotlin-stdlib` や `kotlin-reflect` を依存関係に追加しない |
| JPA エンティティ | 普通の Kotlin の JPA エンティティ（フィールドに付けたアノテーションも問題ない） |

詳細: [2. はじめよう](02-getting-started.md#kotlin-compiler-settings)。

<a id="92-lambda-forms-that-work"></a>
## 9.2 動くラムダ式の形

lambdaQL の関数は、Java の `Serializable` な関数型インターフェースを受け取ります。
Kotlin では **ラムダ式のリテラル** を渡します。末尾ラムダ式と `it` を使ってください。

<!-- region: ManualKotlinNotesTest.kt#where-chain -->
```kotlin
val q = qb.from(Order::class.java)
    .where { it.status.toInt() == 1 }
    .where { it.price > 100.0 }        // more where() calls are joined with AND
assertEquals("select e0 from Order e0 where e0.status = 1 and e0.price > 100.0", q.toHql().hql())
```

**データベースごとの SQL**

<!-- sql: to-hql -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>100.0
```

パラメーターが 2 つ以上のラムダ式は名前を使います。使わないパラメーターには `_` を使ってください。

<!-- region: ManualKotlinNotesTest.kt#named-params -->
```kotlin
// lambdas with two parameters use names
val q = qb.from(Order::class.java)
    .where { o, sub -> sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
    .orderBy { o -> desc(o.price).asc(o.id) }
assertEquals("select e0 from Order e0 where exists (select 1 from Item s1 where s1.order.id = e0.id)"
    + " order by e0.price desc, e0.id asc", q.toHql().hql())
```

Kotlin のプロパティは getter で読みます。`it.price` は、Java の呼び出し `getPrice()` と同じバイトコードの opcode です。
lambdaQL は getter を JPA の属性にマッピングします。
Java のエンティティの boolean プロパティ `active` は getter が `isActive()` なので、Kotlin では `it.isActive` で読みます。lambdaQL はこれを `active` にマッピングします。

| 形 | 動くか |
|---|---|
| `where { it.isActive }`（ラムダ式のリテラル） | はい |
| `run { }`、`let { }`、`forEach { }`、inline でない関数、`suspend` 関数の中のラムダ式 | はい |
| 呼び出し側が lambdaQL の型の `inline fun` パラメーターに渡したラムダ式 | はい |
| SAM コンストラクター、先に `val` に保存した場合も含む | はい |
| 条件に渡したプロパティ参照や Java の getter の参照: `where(Order::isActive)` | **いいえ**（`LQL-1009`）。ラムダ式を使う |
| `val f: (Order) -> Boolean = { it.isActive }; where(f)` | **いいえ** |
| `object : ...`（ラムダ式の代わりのオブジェクト式） | **いいえ** |
| `inline fun` の本体の中で作ったラムダ式 | **いいえ** |
| `-Xsam-conversions=class` でコンパイル | **いいえ** |

コード例: [`ManualKotlinNotesTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinNotesTest.kt)（`where-chain`、`named-params`）。

<a id="93-only-your-null-checks-count"></a>
## 9.3 自分で書いた null チェックとコンパイラーの null チェック

lambdaQL はラムダ式を呼び出さず、バイトコードの opcode を解析して HQL を作ります。
そのため、いくつかの Kotlin のルールは違う動きをします。

| テーマ | ルール |
|---|---|
| 関連のパス | `it.customer?.name` のようなパスは、呼び出しのチェーンではなく **カラム** です。`where` では、to-one の関連は、顧客のない注文が条件に一致しうるなら `left join`、一致しえないなら inner join です。顧客のない注文だけを探すには、`it.customer == null` と書いてください。 |
| `?.` と結合 | `?.` を通った比較は、顧客のない注文では顧客のカラムを NULL とみなして SQL の意味で評価します。`it.status.toInt() == 1 \|\| it.customer?.name == "Kim"`、`it.customer?.name == null`、null ガードは、顧客のない注文が条件に一致しうるので `left join` です。`?.` と `!!` は 1 つの条件の中で混ぜて使ってもかまいません。 |
| `!!` | `!!` は値を変えません。`it.customer!!.name` と `it.customer?.name` は同じカラムです。ただし `where` で関連の段階を `!!` で通った比較は、顧客のある注文でだけ真になります（`it.customer!!.name == null` は inner join）。 |
| `==`、`===` | オブジェクトどうしの `==`（と `===`）は、オブジェクトの同一性ではなく **値の比較** を意味します。 |

NULL になりうる数値のプロパティ（`Integer`）を定数と比較すると、Kotlin のコンパイラーはアンボクシングの前に null チェックを入れます。
lambdaQL は、ソースに書いていないこのチェックを読みません。そのため、Java と同じ SQL の意味になります。
`it.cnt == 1` は `e0.cnt = 1` で、`it.cnt != 1` は `e0.cnt <> 1` なので、`cnt` が NULL の行は返しません。NULL の行が必要なら、`it.cnt == null || it.cnt != 1` のように自分で書いてください。

この動作の例は [9.4](#94-equality----and-areequal) にあります。

<a id="94-equality----and-areequal"></a>
## 9.4 等値比較: `==`、`!=`、`===` と `areEqual`

<a id="how-kotlin-compiles-"></a>
### Kotlin が `==` をコンパイルする方法

| Kotlin | コンパイルの結果 | HQL |
|---|---|---|
| non-null のプリミティブ型 2 つ: `it.qty == 5` | 比較の分岐（Java と同じ） | `e0.qty = 5` |
| nullable なプリミティブ型と定数: `it.cnt == 1`（`Integer`） | null チェック + 比較 | `e0.cnt = 1`（null チェックは読まない） |
| `it.cnt != 1`（`Integer`） | null チェック + 比較 | `e0.cnt <> 1`（NULL の行は返され **ない**） |
| 参照型か、両側とも nullable: `it.description == text`、`it.cnt == q`（`q: Int?`） | `Intrinsics.areEqual(a, b)` の呼び出し | `e0.description = :p1`（キャプチャした値が `null`: 一致する行なし） |
| `it.description != "x"` | `!Intrinsics.areEqual(a, b)` | `e0.description <> 'x'`（NULL の行は返され **ない**） |
| エンティティの値 2 つ: `it.description == it.custName` | `Intrinsics.areEqual(a, b)` | `e0.description = e0.custName`（どちらかが NULL なら不一致） |
| `it.id === q`、`!==` | `if_acmpeq` / `if_acmpne` | `==` と同じ |
| enum: `it.kind == Order.Kind.RUSH` | `if_acmpne` | `e0.kind = org.lambdaql.query.Order$Kind.RUSH`（完全なクラス名で書いたリテラル） |

`Intrinsics.areEqual` は、`==` の裏にある Kotlin のランタイム関数です。lambdaQL はこれを名前で認識し、**値の比較** として読みます。
エンティティの値は SQL の意味に従います。どちらかが NULL なら一致せず、NULL と NULL も同じです。
キャプチャした `null` は NULL としてバインドされ、どの行にも一致しません（`WARN` レベルの `LQL-4045`）。NULL を探すには `x == null` と書いてください。

<!-- region: ManualKotlinNotesTest.kt#hidden-null-check -->
```kotlin
// cnt is a nullable Integer: the null check Kotlin adds before the unboxing is not read (SQL meaning)
val one = qb.from(Order::class.java).where { it.cnt == 1 }
assertEquals("select e0 from Order e0 where e0.cnt = 1", one.toHql().hql())
val notOne = qb.from(Order::class.java).where { it.cnt != 1 }          // NULL rows are left out, as in SQL
assertEquals("select e0 from Order e0 where e0.cnt <> 1", notOne.toHql().hql())
// both sides nullable: Intrinsics.areEqual, a value comparison
val same = qb.from(Order::class.java).where { it.cnt == q }
assertEquals("select e0 from Order e0 where e0.cnt = :p1", same.toHql().hql())
// two entity values: NULL on either side does not match
val columns = qb.from(Order::class.java).where { it.description == it.custName }
assertEquals("select e0 from Order e0 where e0.description = e0.custName", columns.toHql().hql())
```

<a id="plain--and-"></a>
### 単純な `=` と `<>`

`Intrinsics.areEqual` にコンパイルされる比較に、lambdaQL は単純な `=` と `<>` を書きます。
`is [not] distinct from` は書きません。この形は Oracle で `decode(...)` になり、インデックスを使えません。

<!-- region: ManualKotlinNotesTest.kt#equality -->
```kotlin
// plain = and <>: a row whose description is NULL matches neither
val same = qb.from(Order::class.java).where { it.description == text }
assertEquals("select e0 from Order e0 where e0.description = :p1", same.toHql().hql())
val other = qb.from(Order::class.java).where { it.description != "x" }
assertEquals("select e0 from Order e0 where e0.description <> 'x'", other.toHql().hql())
// NULL rows too: write the null check yourself
val orNull = qb.from(Order::class.java).where { it.description == null || it.description != "x" }
assertEquals("select e0 from Order e0 where e0.description is null or e0.description <> 'x'", orNull.toHql().hql())
```

> [!WARNING]
> **Kotlin の non-null 型は証拠になりません。** `var code: String` というプロパティにも、データベースからの NULL が入っていることがあります。
> Hibernate はチェックなしでカラムの値をフィールドに書き込むからです。
> lambdaQL が見るのは Kotlin の型ではなく、JPA のメタデータです。

<a id="-and-"></a>
### `===` と `!==`

lambdaQL では、`===` は `==` と同じく **値の比較** を意味します。
クエリにはオブジェクトの同一性がありません。Kotlin 自身は、値が 1000 の別々の 2 つの `Long` のボックスに対して `false` を返しますが、lambdaQL は `true` を返します。

<a id="bigdecimal"></a>
### `BigDecimal`

Kotlin では、`BigDecimal` に対する `==` は `equals` を呼び、これはスケールまで比較します（`1.0 != 1.00`）。
SQL の `=` は値だけを比較します。**lambdaQL は値の比較を使います。**
`BigDecimal`、`String`、`LocalDate`、`BigInteger` に対する `<`、`>`、`<=`、`>=` をサポートします（`compareTo` にコンパイルされる）。

<a id="booleans"></a>
### boolean

<!-- region: ManualKotlinNotesTest.kt#booleans -->
```kotlin
val active = qb.from(Order::class.java).where { it.isActive }
assertEquals("select e0 from Order e0 where e0.active = true", active.toHql().hql())
val inactive = qb.from(Order::class.java).where { !it.isActive }
assertEquals("select e0 from Order e0 where e0.active = false", inactive.toHql().hql())
```

`!it.isActive` は `e0.active = false` になります。

<a id="java-entities-from-kotlin-platform-types"></a>
### Kotlin から使う Java のエンティティ（プラットフォーム型）

エンティティが Java クラスなら、Kotlin はその getter をプラットフォーム型（`Long!`）として見ます。
この章の例はすべてこの場合です。パス `it.customer?.name` は結合したカラムです（`where` の結合の種類は [3.10](03-where.md#310-association-paths) を参照）。

コード例: [`ManualKotlinNotesTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinNotesTest.kt)（`hidden-null-check`、`equality`、`booleans`）。

<a id="95-null-operators---and-"></a>
## 9.5 null 演算子: `?.`、`?:` と `!!`

<a id="summary"></a>
### まとめ

| Kotlin | lambdaQL での意味 | HQL の例 |
|---|---|---|
| `it.customer?.name` | カラム `customer.name`。顧客のない注文では NULL のカラムとして比較（SQL の意味） | `j1.name` + `join` または `left join e0.customer j1` |
| `it.customer!!.name` | 同じカラム。比較は顧客のある注文でだけ真 | `j1.name` + `join` または `left join e0.customer j1` |
| `it.customer?.id == 1` | パスが対象の `@Id` で終わる: FK のカラム、結合なし | `e0.customer is not null and e0.customer.id = 1` |
| `it.customer == null` | 顧客のない注文だけ | `e0.customer is null`（FK のカラム、結合なし） |
| `it.customer != null` | 顧客のある注文だけ | `e0.customer is not null`（FK のカラム、結合なし） |
| `it.description ?: ""`（値） | デフォルト値 | `coalesce(e0.description, '')` |
| `it.vip ?: false`（条件） | NULL は false として扱う | `e0.vip is not null and e0.vip = true` |
| `lo!!`（キャプチャした値） | `!!` は無視される。`lo` が null なら NULL をバインドするので、一致する行はない（`DEBUG` レベルの `LQL-4045`、[9.8](#98-captured-values) を参照） | `:p1` |

<a id="-and--on-association-paths"></a>
### 関連のパスの `?.` と `!!`

<!-- region: ManualKotlinNotesTest.kt#path-forms -->
```kotlin
// ?. and !! on a path: the same column; no order without a customer can match, so both are an inner join
val safe = qb.from(Order::class.java).where { it.customer?.name == "Kim" }
assertEquals("select e0 from Order e0 join e0.customer j1 where j1.name = 'Kim'", safe.toHql().hql())
val sure = qb.from(Order::class.java).where { it.customer!!.name == "Kim" }
assertEquals("select e0 from Order e0 join e0.customer j1 where j1.name = 'Kim'", sure.toHql().hql())
// a path that ends at the @Id of the target: the FK column, no join
val fk = qb.from(Order::class.java).where { it.customer?.id == 1 }
assertEquals("select e0 from Order e0 where e0.customer is not null and e0.customer.id = 1", fk.toHql().hql())
```

**データベースごとの SQL**

<!-- sql: path-literal -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name='Kim'
```

パスは呼び出しのチェーンではなく **カラム** です。上の条件は顧客のない注文が一致しえないので、`?.` も `!!` も inner join です。
そのため、パスの前に `it.customer != null &&` を書く必要はありません。
`it.customer?.id == 1` は、Kotlin が入れた null チェック（`e0.customer is not null`）をそのまま書きますが、結合はありません。

**`?.` で書いた条件は、Kotlin と同じく顧客のない注文を残します。** Kotlin では、`it.customer?.name == null` は顧客がないとき `true` です。
lambdaQL はこの条件を `left join` で書き、顧客のない注文も返します。`!!` で書いた比較は顧客のある注文でだけ真になるので inner join で、顧客の名前がない注文だけを返します。

<!-- region: ManualKotlinNotesTest.kt#null-paths -->
```kotlin
// orders without a customer: the FK column, no join
val none = qb.from(Order::class.java).where { it.customer == null }
assertEquals("select e0 from Order e0 where e0.customer is null", none.toHql().hql())
// orders without a customer, and orders of a customer without a name: a left join
val safe = qb.from(Order::class.java).where { it.customer?.name == null }
assertEquals("select e0 from Order e0 left join e0.customer j1 where j1.name is null", safe.toHql().hql())
// only orders of a customer without a name: the inner join leaves out orders without a customer
val sure = qb.from(Order::class.java).where { it.customer!!.name == null }
assertEquals("select e0 from Order e0 join e0.customer j1 where j1.name is null", sure.toHql().hql())
```

**データベースごとの SQL**

<!-- sql: no-association -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.customer_id is null
```

<a id="-is-just-for-the-compiler"></a>
### `!!` はコンパイラーのためのものにすぎない

プロパティが `Double?` なら、Kotlin は `it.total > 10.0` をコンパイルしません。
`it.total!! > 10.0` と書いてください。lambdaQL は `!!` を無視します。lambdaQL はラムダ式を呼び出さないので、NPE は起きません。
値が NULL の行は返されません（SQL のルール）。

<a id="-elvis"></a>
### `?:`（エルビス）

select の値と文字列の条件では、`?:` は `coalesce` になります。

<!-- region: ManualKotlinNotesTest.kt#elvis-value -->
```kotlin
// ?: in a select value: coalesce
val text = qb.from(Order::class.java).select { it.description ?: "" }
assertEquals("select coalesce(e0.description, '') from Order e0", text.toHql().hql())
// ?: after ?. in a select value is rejected
val error = assertThrows(LambdaQLException::class.java) {
    qb.from(Order::class.java).select { it.customer?.name ?: "none" }
}
assertEquals("LQL-2022", error.code())
// ?: with a number: coalesce too
val number = qb.from(Order::class.java).where { (it.total ?: 0.0) > 3.0 }
assertEquals("select e0 from Order e0 where coalesce(e0.total, 0.0) > 3.0", number.toHql().hql())
// in a string condition, ?: is coalesce too
val x = qb.from(Order::class.java).where { (it.description ?: "") == "x" }
assertEquals("select e0 from Order e0 where coalesce(e0.description, '') = 'x'", x.toHql().hql())
```

- 数値の条件の `?:` も `coalesce` です: `(it.total ?: 0.0) > 3.0` は `coalesce(e0.total, 0.0) > 3.0` です。
- `?.` の後の `?:` を select の値として使うと拒否されます（`LQL-2022`）。
- カラムに `coalesce` がかかるので、`description` の通常のインデックスは使われません。
- DML の値の `?:` は [7.2](07-2-update.md#add-to-a-value-default-for-null) を参照してください。

**boolean のエルビス** は **条件で** サポートします。

<!-- region: ManualKotlinNotesTest.kt#boolean-elvis -->
```kotlin
// Coded.vip is a nullable Boolean: in a condition, ?: false treats NULL as false
val vip = qb.from(Coded::class.java).where { it.vip ?: false }
assertEquals("select e0 from Coded e0 where e0.vip is not null and e0.vip = true", vip.toHql().hql())
val notVip = qb.from(Coded::class.java).where { !(it.vip ?: false) }
assertEquals("select e0 from Coded e0 where e0.vip is null or e0.vip = false", notVip.toHql().hql())
```

**値やソートキー** として使う boolean のエルビス（`select { it.vip ?: false }`）はサポートしていません（`LQL-2022`）。
ソートにはキー `it.vip` を使い、`asc(key, Nulls.LAST)` や `desc(key, Nulls.FIRST)` のように `Nulls` で NULL の位置を選んでください（[4. ソートとページング](04-order-paging.md#43-direction)）。

<a id="-in-an-assignment"></a>
### 代入での `?.`

`set { }` と `values { }` では、`it.customer?.id = cid` は **常に代入します**。`?.` は無視されます。例は [7.2](07-2-update.md#change-an-association-foreign-key) にあります。

コード例: [`ManualKotlinNotesTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinNotesTest.kt)（`path-forms`、`null-paths`、`elvis-value`、`boolean-elvis`）。

<a id="96-in-ranges-and-when"></a>
## 9.6 `in`、範囲と `when`

詳細とさらに多くの例: [3.9](03-where.md#39-in-with-a-captured-collection)、[3.14](03-where.md#314-ranges-kotlin-only)、[3.15](03-where.md#315-when-kotlin-only)。

| Kotlin | HQL | 備考 |
|---|---|---|
| `it.id in ids`（キャプチャしたコレクション） | `e0.id in (:p1)` | 空なら一致する行なし。`null` の要素はエラー（`LQL-4008`） |
| `it.id !in ids` | `e0.id not in (:p1)`。NULL になりうるカラムも同じ（NULL の行は返されない） | |
| `it.status.toInt() in 1..5` | `1 <= e0.status and e0.status < 6` | |
| `it.status.toInt() !in 1..5` | `1 > e0.status or e0.status >= 6` | NULL の行は返されない |
| `in 1 until 5`、`in 1..<5` | `1 <= e0.status and e0.status < 5` | |
| `it.status.toInt() in lo..hi` | `:p1 <= e0.status and e0.status <= :p2` | |
| `when { a -> true; b -> true; else -> false }` | `a or b` | |
| `when (it.status.toInt()) { 1, 2 -> true; else -> false }` | **拒否される**（`LQL-2010`） | `\|\|` で書く |
| `it.customer?.id in sub.from(...).select { ... }` | `e0.customer.id in (select ...)` | サブクエリ、[5.4](05-subquery.md#54-in) を参照 |
| `x !in sub.from(...).select { ... }` | `x not in (select ...)`（SQL の意味） | [5.5](05-subquery.md#55-not-in-is-written-as-not-exists) を参照 |
| `it.id in listOf(1L, 2L)`（ラムダ式の中で作ったリスト） | **拒否される**（`LQL-3025`） | リストは外で作る |

<!-- region: ManualKotlinNotesTest.kt#ranges -->
```kotlin
val closed = qb.from(Order::class.java).where { it.status.toInt() in 1..2 }
assertEquals("select e0 from Order e0 where 1 <= e0.status and e0.status < 3", closed.toHql().hql())
val outside = qb.from(Order::class.java).where { it.status.toInt() !in 1..2 }
assertEquals("select e0 from Order e0 where 1 > e0.status or e0.status >= 3", outside.toHql().hql())
val until = qb.from(Order::class.java).where { it.status.toInt() in 1 until 3 }
assertEquals("select e0 from Order e0 where 1 <= e0.status and e0.status < 3", until.toHql().hql())
val captured = qb.from(Order::class.java).where { it.status.toInt() in lo..hi }
assertEquals("select e0 from Order e0 where :p1 <= e0.status and e0.status <= :p2", captured.toHql().hql())
// a captured collection
val inIds = qb.from(Order::class.java).where { it.id in ids }
assertEquals("select e0 from Order e0 where e0.id in (:p1)", inIds.toHql().hql())
// a nullable target gets no null guard: NULL rows are returned by neither in nor !in (SQL)
val notIn = qb.from(Order::class.java).where { it.cnt !in counts }
assertEquals("select e0 from Order e0 where e0.cnt not in (:p1)", notIn.toHql().hql())
// when without a subject, with true / false branches: OR
val anyOf = qb.from(Order::class.java).where { when { it.status.toInt() == 3 -> true; it.price > 200.0 -> true; else -> false } }
assertEquals("select e0 from Order e0 where e0.status = 3 or e0.price > 200.0", anyOf.toHql().hql())
```

**nullable な対象には null ガードが付きません**。Kotlin は `null in 1..5` を false、`null !in 1..5` を true と定義しています。
lambdaQL はガードを追加しません。NULL の対象は、`in` でも返されず、`!in` でも返され **ません**（SQL のルール）。
NULL の行が欲しいなら、`it.cnt == null || it.cnt !in 1..5` と書いてください。

キャプチャしたコレクションに対する `!in` も同じです。上の例の `x not in (:p1)` は NULL の行を返しません（SQL の意味）。

コード例: [`ManualKotlinNotesTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinNotesTest.kt)（`ranges`）。

<a id="97-strings"></a>
## 9.7 文字列

エンティティの値に対する `startsWith`、`endsWith`、`contains`（`ignoreCase` を含む）は `like` になります（[3.8](03-where.md#38-strings)）。
その他の文字列関数は、lambdaQL がラムダ式を読むときに `TranslationException`（`LQL-3008`）で拒否します。`lowercase`、`length`、`isNullOrEmpty`、`isBlank`、`equals(s, ignoreCase = true)` がこれにあたります。
条件は、値を直接比較する形で書いてください。

| Kotlin | lambdaQL |
|---|---|
| `it.description.equals(text)` | `==` と同じ: `e0.description = :p1` |
| `(it.description ?: "") == "x"` | `coalesce(e0.description, '') = 'x'`（[9.5](#-elvis)） |
| 上の文字列関数 | 拒否される（`LQL-3008`） |
| 文字列の連結とテンプレート（`"${it.description}-x"`、`"$a-$b" == it.description`、`it.description + "x"`） | 拒否される（`LQL-2007`）。キャプチャした値だけを使うテンプレートも拒否されるので、文字列はラムダ式の外で作ってキャプチャする |

<a id="98-captured-values"></a>
## 9.8 キャプチャした値

| キャプチャしたもの | lambdaQL の使い方 |
|---|---|
| `val`（`Long?`、`String`、... も同じ） | バインドパラメーター |
| `var` | バインドパラメーター。値は **クエリを実行するたびに** 読む |
| 外側のクラスのプロパティ（`this.minStatus`、`private` も同じ） | クエリを実行するたびに読む |
| `companion object` の `val`、トップレベルの `val`、`object` の `val` | バインドパラメーター |
| `const val` | HQL にリテラルとして書く |
| ラムダ式の中でのキャプチャした変数への書き込み（`n++`） | **拒否される** |

<!-- region: ManualKotlinNotesTest.kt#order-repo -->
```kotlin
class OrderRepo(private val qb: QueryBuilder) {
    private val minStatus = 2

    fun open(lo: Int?) = qb.from(Order::class.java)
        .where { it.status >= minStatus }                // a private property: read each time the query runs
        .where { lo == null || it.status > lo }          // lo == null: no filter
}
```

<!-- region: ManualKotlinNotesTest.kt#order-repo-use -->
```kotlin
val all = repo.open(null)
val above = repo.open(2)
assertEquals("select e0 from Order e0 where e0.status >= :p1", all.toHql().hql())
assertEquals("select e0 from Order e0 where e0.status >= :p1 and e0.status > :p2", above.toHql().hql())
```

キャプチャした `null` は、同じ値に対する null ガードの下（`lo == null || ...`）では許されます。ガードはクエリを作るときにアプリケーションで決めます。
`lo == null` ならこの `where` は絞り込みを追加せず、値があれば `e0.status > :p2` になります。

`==`、`!=` に使ったキャプチャした `null` は NULL としてバインドされるので、一致する行はありません（`WARN` レベルの `LQL-4045`）。
ガードなしで書いた大小の比較（`lo == null` のときの `it.status > lo!!`）は NULL をバインドするので、一致する行はありません（`DEBUG` レベルの `LQL-4045`）。`lo!!` とアンボクシングは実行しません。
キャプチャした値のメソッドを呼ぶ式（`text!!.trim()` など）は、値が `null` なら値を計算するときにエラー（`LQL-3027`）を投げます。

<!-- region: ManualKotlinNotesTest.kt#captured-null -->
```kotlin
// a captured null in == is bound as NULL: no row matches
val text: String? = null
val none = qb.from(Order::class.java).where { it.description == text }
assertEquals("select e0 from Order e0 where e0.description = :p1", none.toHql().hql())
assertEquals(0, none.toList().size)
// a method call on a captured null fails when the value is computed
val error = assertThrows(LambdaQLException::class.java) {
    qb.from(Order::class.java).where { it.description == text!!.trim() }.toList()
}
assertEquals("LQL-3027", error.code())
```

コード例: [`ManualKotlinNotesTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinNotesTest.kt)（`order-repo`、`order-repo-use`、`captured-null`）。

<a id="99-property-references-and-scope-functions"></a>
## 9.9 プロパティ参照とスコープ関数

<a id="property-references"></a>
### プロパティ参照

| 形 | 動くか |
|---|---|
| 条件に渡した Kotlin のプロパティ参照や Java の getter の参照（`where(Order::isActive)`） | いいえ（`LQL-1009`）。ラムダ式 `where { it.isActive }` を使う |
| Java の合成プロパティ `JOrder::price` | いいえ。Kotlin 自身がコンパイルしない。 |
| `Order::price.getter` とその他の `kotlin-reflect` の API | いいえ |
| 自分で作った Kotlin の `fun interface` に渡した参照 | いいえ。ラムダ式のリテラルを渡す |

<a id="scope-functions-and-inline-functions-in-conditions"></a>
### 条件の中のスコープ関数と inline 関数

自分で作った `inline fun` は条件で動きます。コンパイラーが本体をラムダ式の中にコピーするので、lambdaQL は普通の条件として見ます。

<!-- region: ManualKotlinNotesTest.kt#inline-fun -->
```kotlin
/** Your own inline function: the compiler copies the body into the lambda, so lambdaQL reads a plain condition. */
private inline fun Order.isOpenInline() = status.toInt() == 1
```

<!-- region: ManualKotlinNotesTest.kt#scope-functions -->
```kotlin
val inline = qb.from(Order::class.java).where { it.isOpenInline() }     // inline fun Order.isOpenInline()
assertEquals("select e0 from Order e0 where e0.status = 1", inline.toHql().hql())
```

| 形 | サポート |
|---|---|
| 自分で作った `inline fun` | はい |
| `?.let { }`、`run { }`、`with(it) { }`、`also { }` | いいえ（`LQL-2013`）。条件を直接書く |
| 自分で作った inline でない拡張関数や拡張プロパティ | いいえ。`inline` にするか、条件を直接書く |
| `takeIf { }` | いいえ（`LQL-2021`） |

更新と挿入のラムダ式では、`apply`、`with`、`also` は拒否されます（`LQL-2003`、[9.12](#912-update-insert-delete-property-assignment-and-dmlvalue) を参照）。

コード例: [`ManualKotlinNotesTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinNotesTest.kt)（`inline-fun`、`scope-functions`）。

<a id="910-kotlin-operators"></a>
## 9.10 Kotlin の演算子

この表は、演算子の形 126 個についてのコンパイラーの確認結果から作りました（kotlinc 2.3.21、設計ドキュメント KOTLIN.md の 9 節）。

| 演算子 | lambdaQL のラムダ式の中で |
|---|---|
| 数値に対する `< > <= >=`（nullable には `!!` を使う） | サポート |
| `BigDecimal`、`String`、`LocalDate`、`BigInteger` に対する `< > <= >=` | サポート（`compareTo` にコンパイルされる） |
| `x.compareTo(y) > 0` | サポート、`x > y` として読む |
| `==`、`!=` | サポート（[9.4](#94-equality----and-areequal)） |
| `===`、`!==` | サポート、値の比較 |
| enum の `==` | サポート |
| エンティティ `==` エンティティ | **拒否される**（`LQL-3011`）。id を比較する |
| 値オブジェクト（`@Embedded`）の `==`、`!=` | サポート。カラムごとに展開（[3.10](03-where.md#310-association-paths) を参照）。大小の比較は `LQL-3012` |
| `+ - *`、単項の `-` | サポート |
| `%` | サポート、`mod` として出力 |
| 整数の `/` | **注意。** データベースによって結果が違う（MariaDB は `5/2` が `2.5`、Kotlin は `2`）。lambdaQL は `(e0.cnt / 2)` のようにそのまま書く |
| `BigDecimal` / `BigInteger` の `+ - * %`、単項の `-` | サポート、SQL の演算子として出力 |
| `BigDecimal` の `/` | サポート（Kotlin は `divide(x, HALF_EVEN)` を使う）。結果のスケールは DB に従う。他の `divide` の形は拒否される |
| `in a..b`、`until`、`..<` | サポート（[9.6](#96-in-ranges-and-when)） |
| `x in list`、`x !in list` | サポート（[9.6](#96-in-ranges-and-when)） |
| 文字列の `+`、文字列テンプレート | **拒否される**（`LQL-2007`）。備考: NULL に対して Kotlin は `"null"` を、SQL は NULL を返す |
| boolean の中置関数 `and`、`or`、`xor` | **拒否される**（`LQL-2013`）。`&&` と `\|\|` を使う（短絡評価をする。`and`/`or` はしない） |
| キャプチャした配列に対する `arr[0]` | キャプチャした値として扱うか、明確なエラー |
| エンティティのパスに対する `it.tags["k"]`、`it.lines[0]` | **拒否される** |
| エンティティの `operator` 関数（`plus`、`compareTo`）、自分で作った inline でない `infix` | **拒否される** |
| `@JvmInline value class` のプロパティ | **拒否される** |

<a id="no-kotlin-only-operator-api"></a>
### Kotlin 専用の演算子 API はない

lambdaQL には **Kotlin 専用の演算子 API はありません**（決定済み）。普通の Java のメソッド名が、すでに Kotlin の演算子として動きます。

| Kotlin の形 | 動く理由 | 状態 |
|---|---|---|
| `x in sub.from(...).select { ... }` | Kotlin は `x in v` を `v.contains(x)` に変える | 維持 |
| `Agg.sum(x)!! + 1 > 3` | プレースホルダーの値に対する普通の算術 | 維持 |
| ソートの演算子（`+key`、`-key`） | Java の利用者に奇妙なメソッドが増え、Kotlin 専用の拡張が必要 | 却下 |
| DB 関数のための `invoke` 演算子 | DB 関数の宣言方法（インターフェースの static メソッド）と衝突 | 却下 |
| `o["price"]` | 文字列のパス。型安全がない | 却下 |
| `infix` 関数 | Java のメソッドは `infix` になれない | 不可能 |
| `between` | `x in a..b` で書く（nullable には `!!`、`LocalDate?` には `!!` が必要） | API は不要 |

<a id="911-results-data-classes-pair-and-triple"></a>
## 9.11 結果: data class、`Pair` と `Triple`

下の例の data class は、`data class OrderRow(val id: Long?, val customerName: String?, val price: Double)`、`data class CustomerStat(val customerId: Int?, val count: Long, val total: Double?)` です。

<!-- region: KotlinQueryTest.kt#data-class -->
```kotlin
val q = qb.from(Order::class.java).select { OrderRow(it.id, it.customer?.name, it.price) }
assertEquals("select e0.id, j1.name, e0.price from Order e0 left join e0.customer j1", q.toHql().hql())
```

<!-- region: KotlinQueryTest.kt#group-by -->
```kotlin
val q = qb.from(Order::class.java)
    .groupBy { it.customer?.id }
    .having { Agg.sum(it.price) > 200 }
    .select { CustomerStat(it.customer?.id, Agg.count(), Agg.sum(it.price)) }
assertEquals("select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id"
    + " having sum(e0.price) > 200.0", q.toHql().hql())
```

<!-- region: ManualKotlinNotesTest.kt#group-pair -->
```kotlin
// a Pair as the group key: two keys
val q = qb.from(Order::class.java)
    .where { it.customer != null }
    .groupBy { it.customer?.id to it.status }
    .select { Triple(it.customer?.id, it.status, Agg.count()) }
assertEquals("select e0.customer.id, e0.status, count(*) from Order e0 where e0.customer is not null"
    + " group by e0.customer.id, e0.status", q.toHql().hql())
```

`Pair` の結果と `listOf(...)` のグループのキーの例は、[6 章](06-select-group.md) にあります。

| ルール | 備考 |
|---|---|
| data class には nullable な型を使う | `sum`、`avg`、`min`、`max` は NULL になりうる。non-null のパラメーターに NULL が入ると、オブジェクトを作るときに NPE が発生する |
| `Pair`、`Triple` | 結果とグループのキーとして許可 |
| `groupBy` の中の `listOf(...)` | 許可（ラムダ式の中で作ったリストが許される唯一の場所） |
| `groupBy` の中の `arrayOf(...)`、`setOf(...)` | 不許可 |
| 派生テーブルや CTE の行の型 | Kotlin の data class（または Java の record）だけ。カラムは `componentN` の順に従う |
| `Agg` の関数 | `import org.lambdaql.Agg.sum` が動く。コレクションに対する Kotlin の `sum()` / `count()` と衝突しない |

詳細: [6. 選択、集計、グループ化](06-select-group.md)。

コード例: [`KotlinQueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)（`data-class`、`group-by`）、[`ManualKotlinNotesTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinNotesTest.kt)（`group-pair`）。

<a id="912-update-insert-delete-property-assignment-and-dmlvalue"></a>
## 9.12 更新、挿入、削除: プロパティへの代入と `Dml.value`

Kotlin のプロパティへの代入は Java と同じ setter の呼び出しにコンパイルされるので、ルールも同じです。

<!-- region: ManualKotlinDmlTest.kt#update-fk -->
```kotlin
// the FK column: the ?. assignment always assigns
val fk = qb.update(Order::class.java).set { it.customer?.id = cid }.where { it.id == first }
assertEquals("update Order e0 set e0.customer.id = :p1 where e0.id = :p2", fk.toHql().hql())
// no customer
val none = qb.update(Order::class.java).set { it.customer = null }.where { it.status.toInt() == 3 }
assertEquals("update Order e0 set e0.customer = null where e0.status = 3", none.toHql().hql())
// a captured entity: its @Id is bound
val entity = qb.update(Order::class.java).set { it.customer = kim }.where { it.id == third }
assertEquals("update Order e0 set e0.customer = :p1 where e0.id = :p2", entity.toHql().hql())
```

| Kotlin の形 | 意味 |
|---|---|
| `value(o.status, st)` | `import org.lambdaql.Dml.value` で取り込んだ関数。`val` プロパティ（setter なし）を持つエンティティに使う。プロパティへの代入と 1 つのラムダ式の中で混ぜられる（[7.3](07-dml.md#73-two-ways-to-assign-property-or-dmlvalue) を参照） |
| `it.cnt += 1` | サポート（[7.2](07-2-update.md#add-to-a-value-default-for-null) を参照） |
| `it.custName = it.description ?: "none"` | サポート: `coalesce(e0.description, 'none')` |
| `it.cnt++`、`--` | 拒否される（`LQL-2003`）。`it.cnt += 1` と書く |
| `set` の中の `apply { }`、`with(it) { }`、`also { }` | 拒否される（`LQL-2003`）。直接の代入で書く |
| `set { }` と `set { o, sub -> }` | 別々のオーバーロード。Kotlin はパラメーターの数で選ぶ。2 つ目の引数 `sub` はサブクエリの起点 |

詳細: [7. 更新、挿入、削除](07-dml.md)。

コード例: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`update-fk`）。

<a id="913-procedures-and-db-functions"></a>
## 9.13 プロシージャと DB 関数

例は [8. プロシージャと DB 関数](08-procedures-functions.md) にあります。この節では、Kotlin で知っておくべきルールだけをまとめました。

<a id="procedures"></a>
### プロシージャ

| ルール | 理由 |
|---|---|
| nullable な戻り値の型（`Double?`）を使う | non-null の型に NULL の OUT の値が入ると失敗する。lambdaQL は名前の付いたエラーを投げる |
| スタブ関数にデフォルト引数を使わない | Kotlin は呼び出しを `...$default` に変え、Kotlin のデフォルト値を埋める。lambdaQL はエラーを報告する |
| 名前付き引数は単純な値（変数、定数）だけ | 式を使うと Kotlin が隠れたローカル変数を作るが、lambdaQL はこれを受け付けない |
| 戻り値のないプロシージャには（`call` ではなく）`exec` を使う | 両方とも `call` だと Java で曖昧になり、`run` は Kotlin の `run { }` と衝突する |
| `-java-parameters` でコンパイルする | そうすると、lambdaQL が同じ型の入れ替わった引数をチェックできる（[8.4](08-procedures-functions.md#84-warning-argument-order)） |

<a id="db-functions-interface-companion-object--jvmstatic"></a>
### DB 関数: インターフェースの `companion object` + `@JvmStatic`

Kotlin のインターフェースには static 関数がありません。`companion object` と `@JvmStatic` を使ってください。
本体は呼び出されません。ラムダ式の外で直接呼ぶと例外を投げます。
Kotlin が companion の関数を呼ぶと、バイトコードの opcode はまず `Companion` オブジェクトを読み込みます。lambdaQL はその段階を飛ばし、関数のアノテーションを読みます。

| 宣言 | サポート |
|---|---|
| インターフェースの `companion object` + `@JvmStatic` | **はい（推奨）** |
| Kotlin の `object` + `@JvmStatic` | はい |
| トップレベル関数 | はい、ただし推奨しない: import を忘れ、その名前が stdlib の名前（`maxOf`）でもあると、Kotlin は黙って stdlib の関数を呼ぶ |
| 拡張関数 | いいえ |
| デフォルト引数のある `@SqlFunction` | いいえ |
| `inline` の `@SqlFunction` | いいえ（呼び出しが消える） |
| `vararg` の `@SqlFunction` | いいえ |
| 関数型のプロパティ / 関数型インターフェースの定数 | いいえ |

カラムに適用した関数（`OrderFns.discount(it.price)!! > 10`）は、通常のインデックスを使えません。
lambdaQL はこれについて WARN をログに出します（[8.9](08-procedures-functions.md#89-warning-functions-on-columns-and-indexes-sargable) を参照）。

<a id="914-kotlin-limits-kc1-to-kc21"></a>
## 9.14 Kotlin の制限（KC1〜KC21）

lambdaQL はラムダ式をクエリとして解析できないと、コード
`[LQL-xxxx]`、ソースの行、ヒントを含む例外を投げます（[11. エラーと言語](../11-errors-and-languages.md) を参照）。
KC1〜KC5 は、ラムダ式を読み込むときに見つかります。この場合、lambdaQL はすぐに止まります。

| # | このように書かないでください | 理由 | 代わりにこう書いてください |
|---|---|---|---|
| KC1 | `-Xsam-conversions=class` でコンパイル | `SerializedLambda` が作られない。ラムダ式がクラスになる | デフォルトの `-Xsam-conversions=indy` のままにする |
| KC2 | `inline fun` の本体（普通、`reified`、`crossinline`）の中で作ったクエリのラムダ式 | ラムダ式がラムダ式ではなくクラスになる | ヘルパーを inline でない関数にするか、呼び出し側からラムダ式のリテラルを渡す |
| KC3 | 自分で作った Kotlin の `fun interface` に対する呼び出し可能参照（`kkey(Order::price)`） | アダプターのクラスになる | ラムダ式のリテラル `{ it.price }` |
| KC4 | Kotlin の関数型の値: `val f: (Order) -> Boolean = {...}; where(f)` | lambdaQL は `Function1` の中身を読めない | リテラルで `where { ... }` |
| KC5 | `object : ...`（オブジェクト式）、`Serializable` でない関数型インターフェース | シリアライズ可能なラムダ式ではない | ラムダ式のリテラル |
| KC6 | `takeIf`、一般的な boolean の `if (a) x else y` | サポートしない（`LQL-2021`）。値を選ぶ `if`/`when` はサポート（キャプチャした値だけならバインドパラメーター、エンティティの値なら `case when`） | `&&` / `\|\|` |
| KC7 | *（D25 で削除）* | – | – |
| KC8 | ラムダ式の中の `in listOf(1, 2, 3)` や `setOf(...)` | ラムダ式の中で作ったコレクション | 外で `val ids = listOf(1, 2, 3)`、それから `it.id in ids` |
| KC9 | `isBlank()`、`isNullOrBlank()` | Unicode の空白には移植性のある SQL の形がない | 直接比較する |
| KC10 | *（D21 で削除）* | – | – |
| KC11 | 自分で作った inline でない拡張関数や拡張プロパティ、エンティティの `operator` 関数 | lambdaQL は呼び出しの中を見られない | `inline fun` にするか、条件を直接書く |
| KC12 | キャプチャした変数に対する `n++` | ラムダ式が変数に書き込む | ラムダ式の外で値を計算する |
| KC13 | エンティティ `==` エンティティ（`it.customer == c`） | サポートしない（`LQL-3011`） | `it.customer?.id == c.id` |
| KC14 | `isNaN()` | サポートしない | NaN を保存しない。[10.5](10-rules-and-limits.md#105-kotlin-and-sql-differences) を参照 |
| KC15 | `@JvmField` のエンティティのプロパティ | getter ではなくフィールドへのアクセス | 普通の `var` / `val` |
| KC16 | 変形した getter の名前: `internal` プロパティ、`value class`、`@get:JvmName` | getter を JPA の属性にマッピングできない | public の普通のプロパティ |
| KC17 | 文字列テンプレートの中のエンティティの値 | 文字列の連結はサポートしない（`LQL-2007`） | カラムを別々に比較する |
| KC18 | Java の合成プロパティの参照 `JOrder::price` | Kotlin のコンパイルエラー | ラムダ式 `{ it.price }` |
| KC19 | `Order::price.getter` とその他の `kotlin-reflect` の API | `kotlin-reflect` を使わない | ラムダ式 `{ it.price }` |
| KC20 | カラムに対するその他の stdlib の関数（`coerceAtLeast`、`trim`、`toLocalDate`、...） | 許可リストにない（`LQL-3008`） | `@SqlFunction` を使った DB 関数 |
| KC21 | 値やソートキーとして使う boolean のエルビス（`{ it.vip ?: false }`） | boolean に対する `coalesce` は Oracle で失敗する（ORA-00932） | NULL の位置を指定して `{ it.vip }` でソートする。条件では `it.vip ?: false` も問題ない |

<a id="915-where-lambdaql-differs-from-running-kotlin-code"></a>
## 9.15 lambdaQL が Kotlin のコードの実行と違う点

この違いは設計の一部です。バグではありません。

| Kotlin のコード | lambdaQL（SQL） |
|---|---|
| `!!` は NPE を投げる | 値の `!!` は無視される。NULL は SQL のルールに従う。`where` で関連の段階を `!!` で通った比較は、関連のある行でだけ真 |
| `it.description != "x"` は `description == null` のとき `true` | `e0.description <> 'x'` は NULL に対して UNKNOWN なので、行は返されない。含めるには `it.description == null \|\| ...` と書く |
| 顧客がなければ `it.customer?.name == null` は `true` | 同じ: `?.` で通った段階は `left join` になり、顧客のない注文も返される。`it.customer!!.name == null` は顧客のある注文でだけ真なので、返されない |
| 対象が NULL の範囲は false で、`!in` は `true` | 行は返されない |
| `===` はオブジェクトの同一性 | 値の比較 |
| `BigDecimal` の `==` はスケールを比較する（`1.0 != 1.00`） | 値の比較 |
| non-null 型の `String` は null ではない | カラムは依然として NULL になりうる。lambdaQL は JPA のメタデータを使う |
| 整数の `/` は小数部分を切り捨てる | DB によって違うことがある（MariaDB は `5/2 = 2.5`） |

nullable な数値のプロパティと定数の比較（`it.cnt != 1`）も `e0.cnt <> 1` になるので、`cnt` が NULL の行は返しません。Kotlin と結果が違うことがあります（[9.3](#93-only-your-null-checks-count)）。

その他のルール: [10. ルールと制限](10-rules-and-limits.md)。

---

[Kotlin トラック](README.md) | 前: [8. プロシージャと DB 関数](08-procedures-functions.md) | 次: [10. ルールと制限](10-rules-and-limits.md) | [このページの Java 版: 9. Java の注意点](../java/09-java-notes.md)
