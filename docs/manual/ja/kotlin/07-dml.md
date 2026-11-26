<a id="7-update-insert-delete-kotlin"></a>
# 7. 更新、挿入、削除（Kotlin）

[Kotlin トラック](README.md) | [マニュアルのホーム](../README.md) | 前: [6. 選択、集計、グループ化](06-select-group.md) | 次: [8. プロシージャと DB 関数](08-procedures-functions.md) | [このページの Java 版](../java/07-dml.md)

共通ページ: [1. 概要](../01-overview.md) · [11. エラーと言語](../11-errors-and-languages.md)

lambdaQL の入り口は 5 つです。

| 入り口 | 用途 |
|---|---|
| `qb.from(X::class.java)` | 選択（3〜6 章） |
| `qb.update(X::class.java)` | 行の更新 |
| `qb.insert(X::class.java)` | 行の挿入 |
| `qb.delete(X::class.java)` | 行の削除 |
| `qb.upsert(X::class.java)` | 行の挿入。同じ `@Id` の行があれば、その行を更新 |

基本の考え方はクエリと同じです。

- 各部分を **1 つのラムダ式** で書きます。`val` プロパティには `Dml.value` で値を代入します（[7.3](#73-two-ways-to-assign-property-or-dmlvalue)）。
- lambdaQL はラムダ式を呼び出さず、バイトコードの opcode を解析します。`it.status = 1` のようなプロパティへの代入は setter の呼び出しにコンパイルされ、`set e0.status = 1` になります。
- `where` は [3. 条件（`where`）](03-where.md) と同じルールを使います。対象の行は、同じラムダ式で選択した行と同じです。
- `execute()` は文を実行し、**影響を受けた行数**（`Int`）を返します。
- `executeOne()` は、**多くても 1 行** だけを変更すべき update や delete を実行します（[1 行保護](#one-row-protection) を参照）。
- `orderBy { ... }.limit(n)` は先頭の `n` 行だけを変更します（[limit](#limit-change-the-first-rows) を参照）。
- `toHql()` は文を実行せず、HQL とパラメーターを返します。
- 文の実行にはトランザクションが必要です（[トランザクション](#transactions) を参照）。

<a id="contents"></a>
## 目次

- [7.1 例で使うエンティティ](#71-example-entities)
- [7.2 更新](#72-update)
- [7.3 2 つの代入方法: プロパティか `Dml.value` か](#73-two-ways-to-assign-property-or-dmlvalue)
- [7.4 削除](#74-delete)
- [7.5 挿入](#75-insert)
- [7.6 Upsert](#76-upsert)
- [7.7 DML での Kotlin の注意点](#77-kotlin-notes-for-dml)
- [7.8 重要なルール](#78-important-rules)
- [7.9 ルール U1 から U21 のすべて（まとめ）](#79-all-rules-u1-to-u21-summary)
- [7.10 v0.1 にないもの](#710-not-in-v01)

---

<a id="71-example-entities"></a>
## 7.1 例で使うエンティティ

この章では `Order`、`Customer`、`Item` を使います（[2. はじめよう](02-getting-started.md#example-entities) を参照）。
例では、[`query/src/test/java/org/lambdaql/query`](https://github.com/lambdaql/lambdaql/tree/main/query/src/test/java/org/lambdaql/query) の Java の例のエンティティを Kotlin からそのまま使います。
Kotlin は Java の getter と setter をプロパティとして見るので、`it.status = st` のように代入します。

| クラス | この章で使うプロパティ |
|---|---|
| `Order` | `id`（IDENTITY）、`price`（`double`）、`status`（`short`）、`active`（`boolean`、Kotlin では `isActive`）、`description`、`updateAt`、`kind`（enum `Order.Kind`）、`customer`。NULL になりうる `total`（`Double`）、`cnt`（`Integer`）、`custName`、`address`（`@Embedded` の `Address`: `city`、`zip`） |
| `VOrder` | `id`、`status`（`Integer`）、`version`（`@Version`） |
| `Archive` | `id`（ジェネレーターなし）、`price`、`customerName` |
| `Summary` | `customerId`（`Integer`）、`total`、`cnt`（`Long`） |
| `IdOrder` | `id`（IDENTITY）、`price`、`status` |

リストの挿入に使う `Line` と `Ref` は普通の data class です（例: `data class Line(val id: Long, val price: Double)`）。
`status` は `short` なので、条件は `it.status.toInt() == 1` と書きます。

---

<a id="72-update"></a>
## 7.2 更新

→ [7.2 更新](07-2-update.md)

<a id="73-two-ways-to-assign-property-or-dmlvalue"></a>
## 7.3 2 つの代入方法: プロパティか `Dml.value` か

| 方法 | 使うとき | 例 |
|---|---|---|
| プロパティへの代入 | プロパティが `var` のとき（setter がある）。コンパイラーが値の型をチェックします。 | `set { it.status = 1 }` |
| `Dml.value(column, value)` | プロパティが `val` のとき（setter がない）。例: DDD スタイルのエンティティ。 | `set { o -> value(o.status, 1) }` |

`value` は関数 `org.lambdaql.Dml.value` です。`import org.lambdaql.Dml.value` で取り込み、引数が 1 つのラムダ式の中で使います。

<!-- region: KotlinQueryTest.kt#update-value -->
```kotlin
// entities without setters (val): Dml.value(column, value)
val u = qb.update(Order::class.java)
    .set { o -> value(o.description, "done") }
    .where { it.status.toInt() == 2 }
assertEquals("update Order e0 set e0.description = 'done' where e0.status = 2", u.toHql().hql())
```

例のエンティティの `status` は `short` なので、条件は `it.status.toInt() == 2` と書きます。同じ文の Java の形は [このページの Java 版](../java/07-dml.md#73-two-ways-to-assign-setter-or-dmlvalue) にあります。

**データベースごとの SQL**

<!-- sql: update-value -->
```sql
-- H2, MariaDB
update orders o1_0
set description='done'
where o1_0.status=2

-- Oracle
update orders o1_0
set o1_0.description='done'
where o1_0.status=2
```

Oracle は `set` に別名を書きます（`set o1_0.status=?`）。H2 と MariaDB は書きません。

この章のすべての例は `value` でも書けます。Kotlin の 1 つの文に `value` の呼び出しを 1 つ書きます。

<!-- region: ManualKotlinDmlTest.kt#value-forms -->
```kotlin
// Dml.value(column, value) for properties without a setter (val); one value call per statement
val u = qb.update(Order::class.java)
    .set { o -> value(o.status, st); value(o.price, o.price * 1.1) }
    .where { it.status.toInt() == 1 }
assertEquals("update Order e0 set e0.status = :p1, e0.price = (e0.price * 1.1) where e0.status = 1", u.toHql().hql())
val embedded = qb.update(Order::class.java).set { o -> value(o.address.city, city) }.where { it.id == id }
assertEquals("update Order e0 set e0.address.city = :p1 where e0.id = :p2", embedded.toHql().hql())
val fk = qb.update(Order::class.java).set { o -> value(o.customer?.id, cid) }.where { it.id == id }
assertEquals("update Order e0 set e0.customer.id = :p1 where e0.id = :p2", fk.toHql().hql())
```

`value` のルール:

- 1 つ目の引数は、**対象から始まるプロパティの読み取り（getter の呼び出し）** でなければなりません: プロパティ、埋め込みのパス、または関連の `@Id`。
  キャプチャした値、計算式、ソースのエンティティのパスは、解析時のエラー（`LQL-3039`）です。
- 同じプロパティの setter のルールをそのまま適用します。`value(o.x, v)` は `o.x = v` と同じなので U1 と U6 が適用され、関連には `@Id` だけを代入できます（`LQL-3050`）。
- プロパティへの代入と `value` は、**1 つのラムダ式の中で混ぜて使えます**。
- `value(column, value)` はジェネリックなので、コンパイラーは値がカラムに合うかをチェックしません。
  lambdaQL は解析時にカラムの型と値の型を照合し、合わなければ `LQL-3065` のエラーを出します。
  数値は数値のカラムに合います。ただし、小数は整数のカラムに合いません。型がわからない値（`Any`、ジェネリックな集約の `Number`）は照合しません。
- 自身の `value(...)` 関数を持つクラスの中では、import した `value` が隠れます。その場所では `Dml.value(...)` と書いてください。
- `val` プロパティには `value(...)` で代入します。

<a id="subquery-in-a-set-value"></a>
### `set` の値の中のサブクエリ

代入のラムダ式の 2 つ目の引数は、サブクエリの起点 `sub` です。`where { o, sub -> ... }` と同じ書き方です。
`insert ... select` では、3 つ目の引数が `sub` です（[7.5](07-5-insert.md) を参照）。

<!-- region: KotlinQueryTest.kt#update-sub -->
```kotlin
// the second parameter starts a subquery, as in where { o, sub -> ... }
val u = qb.update(Order::class.java)
    .set { o, sub -> o.price = sub.from(Item::class.java).where { i -> i.order?.id == o.id }.sum { it.price } }
    .where { o, sub -> sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
assertEquals("update Order e0 set e0.price = (select sum(s1.price) from Item s1 where s1.order.id = e0.id)"
        + " where exists (select 1 from Item s1 where s1.order.id = e0.id)", u.toHql().hql())
```

- 別のテーブルの集約も同じテーブルの集約も、`set` の値に使えます（H2、MariaDB 10.11、Oracle 23.26 で実測）。`update` では、MariaDB もエラー 1093 を出しません。
- 一致する行がなければ、`sum` などは NULL を返します。NOT NULL のカラムなら DB のエラーになるので、`?: 0.0` を付けてください。`coalesce(...)` になります（[7.2](07-2-update.md#an-aggregate-from-a-subquery) を参照）。
- insert の `values { a, sub -> ... }` と upsert（`onConflict { ... }.update { a, sub -> ... }`）でも、同じ方法でサブクエリを使います（[7.5](07-5-insert.md#a-subquery-in-a-value) を参照）。リストの形は `values(list) { a, l, sub -> ... }` のように、3 つ目の引数が `sub` です。

コード例: [`KotlinQueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)（`update-value`、`update-sub`）、[`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`value-forms`）。

---

<a id="74-delete"></a>
## 7.4 削除

→ [7.4 削除](07-4-delete.md)

<a id="75-insert"></a>
## 7.5 挿入

→ [7.5 挿入](07-5-insert.md)

<a id="76-upsert"></a>
## 7.6 Upsert

→ [7.6 Upsert](07-6-upsert.md)

<a id="77-kotlin-notes-for-dml"></a>
## 7.7 DML での Kotlin の注意点

| Kotlin の書き方 | 意味 |
|---|---|
| `it.status = x` | setter の呼び出し: `set e0.status = :p1` |
| `a ?: b`（文字列や数値の値） | `coalesce(a, b)` |
| `it.customer?.id = x` | 常に代入する（`?.` は無視される） |
| `it.cnt += 1` | サポート（`set e0.cnt = e0.cnt + 1`） |
| `value(o.status, x)` | `val` プロパティ用の代入。文ごとに `value` を 1 つ |
| `value(o.cnt, o.cnt + 1)` | サポート（`set e0.cnt = e0.cnt + 1`） |
| `it.cnt++`、`it.cnt--` | 拒否される（`LQL-2003`）。`+= 1` を使う |
| `it.apply { }`、`with(it) { }`、`it.also { }` | 拒否される（`LQL-2003`）。直接代入する |
| 値を選ぶ `if` / `when`、トグル `!it.isActive` | `case when`（[3.5.1](03-where.md#351-values-chosen-by-a-condition)） |
| 代入するかどうかを選ぶ `if (c) it.price = x` | 拒否される（`LQL-2009`）。条件は `where` に書くか、`set()` を何回も呼ぶ |
| ラムダ式の中のローカルの `val` | 拒否される（`LQL-2003`）。代入したカラムをもう一度読む問題を隠すことがある |

<!-- region: KotlinCaseWhenTest.kt#set-toggle -->
```kotlin
// a toggle and a number elvis are values too
val toggle = qb.update(Order::class.java).set { it.isActive = !it.isActive }.all()
assertEquals("update Order e0 set e0.active = case when e0.active = false then true else false end", toggle.toHql().hql())
val zero = qb.update(Order::class.java).set { it.cnt = it.cnt ?: 0 }.all()
assertEquals("update Order e0 set e0.cnt = coalesce(e0.cnt, 0)", zero.toHql().hql())
// an if that chooses whether to assign is rejected (LQL-2009): put that condition in where
val e = assertThrows(LambdaQLException::class.java) {
    qb.update(Order::class.java).set { if (it.isActive) it.price = 1.0 }
}
assertEquals("LQL-2009", e.code())
```

コード例: [`KotlinCaseWhenTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinCaseWhenTest.kt)（`set-toggle`）。

Kotlin について詳しくは: [9. Kotlin の注意点](09-kotlin-notes.md)。

---

<a id="78-important-rules"></a>
## 7.8 重要なルール

<a id="the-right-side-reads-the-old-value"></a>
### 右辺の基準: 古い値

SQL では、SET の右辺のすべてのカラムは、更新 **前** の値を意味します。lambdaQL はこのルールに従います。

<!-- region: ManualKotlinDmlTest.kt#old-value -->
```kotlin
// the right side reads the value before the change
val self = qb.update(Order::class.java).set { it.price = it.price * 1.1 }.all()             // reads itself: allowed
val readFirst = qb.update(Order::class.java).set { it.total = it.price; it.price = 0.0 }.all() // read, then assign: allowed
assertEquals("update Order e0 set e0.total = e0.price, e0.price = 0.0", readFirst.toHql().hql())
// assign, then read again: an error at set(), because MariaDB would read the new value
val error = assertThrows(LambdaQLException::class.java) {
    qb.update(Order::class.java).set { it.price = it.price * 2; it.total = it.price }
}
assertEquals("LQL-3042", error.code())
```

**同じ文でカラムに代入した後で、そのカラムを読むと**、lambdaQL は `set(...)` を呼ぶ時点で `TranslationException`（`LQL-3042`）を投げます。
理由: この場合、MariaDB は **新しい** 値を使い、H2 と Oracle は古い値を使います。結果が違います（実測: MariaDB 200、H2 と Oracle 100）。

次の場合は 3 つのデータベースの結果が同じなので、許可します。

| 書き方 | 許可 |
|---|---|
| `it.price = it.price * 1.1`（自分自身を読む） | 許可 |
| `it.total = it.price; it.price = 0.0`（先に読み、後で代入） | 許可 |
| `it.price = ...; it.total = it.price`（先に代入し、後で読む） | **エラー** |

このチェックは物理カラム（埋め込みの下位カラム、FK のカラム）の単位で動き、複数の `set()` 呼び出しの間にも適用されます。

<a id="assign-a-column-only-once"></a>
### カラムへの代入は 1 回だけ

同じカラムに 2 回代入するとエラーです。`it.address = ...` の後の `it.address.city = ...`、
または `it.customer = ...` の後の `it.customer?.id = ...` も含みます。

<a id="rules-checked-at-analysis-time"></a>
### 解析時のルール確認

lambdaQL はラムダ式を読むときに代入のルールをチェックし、`set(...)` や `values(...)` で `LambdaQLException` を投げます（例: `LQL-3037`、`LQL-3042`、`LQL-3041`）。
Kotlin のラムダ式で書いた次の形は、ラムダ式を読むときに拒否されます。代入したカラムをもう一度読む形と、同じカラムに 2 回代入する形の例は、[右辺の基準: 古い値](#the-right-side-reads-the-old-value) と [7.2](07-2-update.md#many-set-calls-patch) にあります。

<!-- region: ManualKotlinDmlTest.kt#assign-rules -->
```kotlin
// forms that the assignment rules reject when the lambda is read
val increment = assertThrows(LambdaQLException::class.java) {
    qb.update(Order::class.java).set { it.cnt++ }                       // ++: write += 1
}
val scope = assertThrows(LambdaQLException::class.java) {
    qb.update(Order::class.java).set { it.apply { status = 1 } }        // apply: assign directly
}
val other = assertThrows(LambdaQLException::class.java) {
    qb.update(Order::class.java).set { it.customer?.name = "x" }        // a property of another table
}
val noNull = assertThrows(LambdaQLException::class.java) {
    qb.update(Archive::class.java).set { it.id = null }.all()            // null into the @Id
}
```

`++`、スコープ関数、ローカル変数は `UnsupportedLambdaShapeException`（`LQL-2003`）で、関連の `@Id` でないプロパティは `LQL-3050`、`@Id` に代入した `null` は `LQL-3044` です。

<a id="where-or-all-is-required"></a>
### `where` か `all()` の指定が必須

update と delete を実行するには、`where(...)` か `all()` を指定しなければなりません。

<a id="association-paths-same-rows-as-select"></a>
### 関連のパス: 選択と同じ行

lambdaQL は DML で、FK でない関連のパスを **相関スカラーサブクエリ** として書きます。`where` でも SET の右辺でも同じです。
例: `where { it.customer?.name == "Kim" }` は `where (select s1.name from Customer s1 where s1.id = fk(e0.customer)) = 'Kim' and e0.customer.id is not null` になり、`it.custName = it.customer?.name` は `set e0.custName = (select s1.name from Customer s1 where s1.id = fk(e0.customer))` になります。例は [7.2](07-2-update.md#a-value-from-another-table) にあります。

このようにする理由は次のとおりです。lambdaQL がパスを Hibernate にそのまま渡すと、Hibernate はデータベースごとに違う結合の SQL を作ります
（H2 は `merge`、MariaDB は `update ... join`、Oracle はインラインビュー）。また、SET にパスがあると **顧客のない行が抜けていました**
（実測: `.all()` で 10 行のうち 9 行だけが更新された）。
lambdaQL はサブクエリを直接書きます。すると文は自分のテーブルだけを読むので、対象テーブルの読み取りに対する MariaDB の制約（エラー 1093）にかかりません。

選択の `where` で inner join になるパスには、DML で lambdaQL が `e0.customer.id is not null` を加えます。
その結果、`update` と `delete` は、`qb.from(X::class.java).where { 同じラムダ式 }` が返す **まさにその行** だけを対象にします（削除した行を選択の結果と比較）。
null ガード（`it.customer == null || it.customer!!.name == ...`）や `it.status == 1 || ...` のように選択で `left join` になるパスには、このチェックを加えません。`?.` で書いても `!!` で書いても同じです。このときも、DML は選択と同じ行を対象にします（[7.2](07-2-update.md)、[7.4](07-4-delete.md) を参照）。

`@Id` で終わるパス（`it.customer?.id`）は FK のカラムです。サブクエリも結合もありません。

<a id="batching-for-many-rows"></a>
### 多数の行のバッチ処理

`values(list) { ... }` は、VALUES の行が複数ある 1 つの文を書きます。

| 項目 | ルール |
|---|---|
| 分割の単位 | 行数ではなく値の数（行 x カラム） |
| 上限 | 1 文あたり値 500 個以下（固定） |
| 戻り値 | すべての文の合計 |
| トランザクション | すべての文を 1 つのトランザクションで実行 |
| 空のリスト | SQL なし、0 を返す |

Oracle には、バインド値 65,535 個という厳しい上限があります（ORA-01745）。解析はそれよりずっと前からとても遅くなります
（設計段階の実測: 21,845 行で約 6 分）。そのため上限を小さくしました。

<a id="limit-change-the-first-rows"></a>
### limit: 先頭の行の変更

`where { ... }` または `all()` の後に `orderBy { ... }.limit(n)` を付けると、update や delete は多くても `n` 行だけを変更します。対象は `qb.from(X::class.java).where { same }.orderBy { same }` の先頭の `n` 行です。

| 項目 | ルール |
|---|---|
| 順序 | `orderBy` のキーの次に id。`orderBy` がなければ id の順 |
| `limit` のない `orderBy` | エラー（`IllegalStateException`） |
| 書き方 | `e0.id in (select t.id from (select x0.id as id from X x0 ... order by ... limit n) t)` |
| 対象の行 | 内側の select が結合を含めて選択のルールに従うので、選択と同じ行 |
| MariaDB | 派生テーブル `t` でエラー 1093 と 1235 を回避 |

例とデータベースごとの SQL: [7.4](07-4-delete.md#delete-the-first-rows-limit)（delete）、[7.2](07-2-update.md#update-the-first-rows-limit)（update）。

<a id="one-row-protection"></a>
### 1 行保護

`execute()` の代わりに `executeOne()` を呼ぶと、**多くても 1 行** だけを変更します。

1. lambdaQL が条件に合う行の id を最大 2 つ読みます。
2. id が 2 つなら、何も変更せずに `NonUniqueResultException` を投げます。
3. id が 1 つなら、`id = :p and (条件)` でその行を変更します。

2 行以上を変更することはなく、ロールバックに頼りません。結果は 0 か 1 です。`executeOne()` を `orderBy` や `limit` と一緒に使うとエラー（`IllegalStateException`）です。

例: [7.4](07-4-delete.md#delete-exactly-one-row-executeone)。Spring のリポジトリでは `deleteOne` と `updateOne` を使います（[12.4](12-spring-data-jpa.md#one-row-protection-deleteone-and-updateone) を参照）。

<a id="transactions"></a>
### トランザクション

一括の文にはトランザクションが必要です。lambdaQL がトランザクションを開くのは、`EntityManagerFactory` のコンストラクターを使ったビルダーだけです。

| ビルダー | トランザクション |
|---|---|
| `QueryBuilder(entityManagerFactory)` | `execute()` と `executeOne()` が自分のトランザクションを開いてコミットします。エラーになるとロールバックします |
| `EntityManager` を使うビルダー: `QueryBuilder(entityManager)`、Spring の `QueryBuilder` Bean、リポジトリーの実行器 | 呼び出し側のトランザクション（`@Transactional`、またはその `EntityManager` のアクティブなトランザクション）。トランザクションがなければ、SQL を送る前に `TransactionRequiredException` で失敗します（Java）。Spring のリポジトリーの呼び出しでは `InvalidDataAccessApiUsageException` になります（[12.6](12-spring-data-jpa.md#126-transactions-and-exceptions) を参照） |

<a id="the-persistence-context-is-not-updated"></a>
### 永続コンテキストへの未反映

一括の DML は JPA の永続コンテキストを通りません。

- Hibernate は文の実行前に、保留中の変更を flush します。
- 文の実行後、同じ `EntityManager` にすでに読み込まれたエンティティは **古い値** を持ちます。そのエンティティをもう一度使うなら、`entityManager.clear()` を呼んでください。
- JPA の `cascade` と `orphanRemoval` は実行され **ません**。子を先に削除するか、データベースで `ON DELETE CASCADE` を使ってください。

<a id="sqlrestriction-and-softdelete"></a>
### `@SQLRestriction` と `@SoftDelete`

Hibernate は、エンティティの `@SQLRestriction` の条件と `@SoftDelete` の条件を、lambdaQL が作るすべての文に付けます（MariaDB 10.11、Oracle 23.26 で実測。H2 は未測定）。
一括の `update` と `delete` も同じです。例: `where m1_0.order_id=? and (m1_0.hidden = 0)`。

`@SoftDelete` のエンティティの `delete` は `update … set deleted = 1 where … and deleted = 0` になります。行は実際には消えません。
lambdaQL はこの条件を別に扱いません。

コード例: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`old-value`、`assign-rules`）。

---

<a id="79-all-rules-u1-to-u21-summary"></a>
## 7.9 ルール U1 から U21 のすべて（まとめ）

lambdaQL がラムダ式を読むときにチェックするルール: U1、U3、U4、U6、U15。
lambdaQL は `@Version` プロパティへの代入を拒否します（`LQL-3068`）。U11 と U12 はチェックしません。

| # | ルール |
|---|---|
| U1 | 代入のラムダ式には、対象、対象の埋め込みのパス、または関連の `@Id` に対する **代入（setter の呼び出し）と `Dml.value(column, value)` だけ** が入ります（`LQL-3037`）。`if`、`when`、ループ、ローカル変数、`++`、スコープ関数、その他の呼び出しはエラーです。代入のないラムダ式もエラーです。 |
| U2 | 右辺は `where` の値のルール（[3. 条件（`where`）](03-where.md)）に従います。キャプチャした値はバインドパラメーターで、ラムダ式に書いた定数はリテラルです。`a ?: b` は `coalesce(a, b)` になります。 |
| U3 | 右辺は **古い** 値を読みます。同じ文でカラムに代入した後にそのカラムを読むとエラーです。 |
| U4 | 同じ物理カラムに 2 回代入するとエラーです。`set()` の呼び出しが違っても同じです。 |
| U5 | `where()` か `all()` がなければ `execute()` はありません。コンパイラーがチェックします。 |
| U6 | 任意でないカラム（`@Id`、`nullable=false`、プリミティブ型）に `null` を代入するとエラーです。コレクションや、関連エンティティの `@Id` でないプロパティに代入してもエラーです。 |
| U7 | キャプチャした値は、使うたびに自分のパラメーターを受け取ります。（STRING のカラムと ORDINAL のカラムが 1 つの enum パラメーターを共有すると、H2 と MariaDB がエラーなしで誤った値を保存します。） |
| U8 | `LocalDateTime.now()` はアプリケーションで計算してバインドします。データベースの時刻（`current_timestamp`）はサポートしていません。 |
| U9 | DML で FK でない関連のパスは、`where` でも SET の右辺でも相関スカラーサブクエリになります。`where` で、選択なら inner join するパスには `customer.id is not null` を加え、`left join` するパスには加えません。DML は選択と同じ行を対象にします。 |
| U10 | `insert ... select` では、ソースのパスは明示的な `left join` になります。 |
| U11 | `it.customer = entity` はエンティティの `@Id` をバインドします。id が null ならバインドが失敗します。 |
| U12 | IDENTITY と SEQUENCE の id は省略できます。TABLE ジェネレーターは `values` でだけ動き、`insert ... select` では動きません。ジェネレーターがなければ id が必要です。その他の任意でないプロパティも必要です。 |
| U13 | 複数行は、1 文あたり値 500 個以下に分けます。結果は合計します。すべての文は 1 つのトランザクションで実行されます。 |
| U14 | 1 つの `set` のラムダ式で複数のカラムに代入でき、`set()` は何回でも呼べます。すべて呼び出しの順に 1 つの SET リストにまとめられます。プロパティへの代入と `Dml.value` は、1 つのラムダ式の中でも混ぜられます。 |
| U15 | `@Version` のエンティティは `update versioned` を使います。バージョンに直接代入すると `LQL-3068` です。`execute() == 0` の確認は利用者が行います。 |
| U16 | 一括の DML は永続コンテキストを飛ばします。実行前に flush、実行後は古いエンティティ、cascade なし。 |
| U17 | DELETE のサブクエリが対象のエンティティ（または同じ継承階層）を読むと、lambdaQL は対象の id を派生テーブルで先に読みます: `d0.id in (select t.id from (select e0.id as id from … where <condition>) t)`。そうしないと MariaDB のエラー 1093 になります。相関サブクエリでも動き、`update` と複合 id のエンティティは包みません（[7.4](07-4-delete.md#delete-with-a-subquery-on-the-same-table) を参照）。 |
| U18 | 継承: SINGLE_TABLE は完全にサポートします。JOINED と TABLE_PER_CLASS の update/delete は一時テーブルを使います。それらのサブクラスへの insert は拒否されます。 |
| U19 | クエリキャッシュのキーには、入り口、対象のエンティティ、set のラムダ式、where/all、versioned、分割サイズが入ります。 |
| U20 | `Dml.value(column, value)`: 1 つ目の引数は、対象（または `insert ... select` の対象 `a`）から始まるプロパティの読み取りでなければなりません（`LQL-3039`）。同じプロパティの setter のルール（U1、U6、関連は `@Id` だけ）をそのまま適用します。値の型はカラムの型と照合します（`LQL-3065`）。プロパティへの代入と混ぜて使えます。 |
| U21 | 各ラムダ式は 1 回解析されてキャッシュされます。`set()` の呼び出しの間のチェックは、キャッシュした要約だけを比べます（プロトタイプで約 0.1〜0.2 µs）。 |

Upsert のルールは [7.6 Upsert](07-6-upsert.md#rules) にあります。

---

<a id="710-not-in-v01"></a>
## 7.10 v0.1 にないもの

| 項目 | 今できる方法 |
|---|---|
| 代入を選ぶためのラムダ式の中の `if`: `LQL-2009` | `set()` を何回も呼ぶ |
| `++`、`--`、`apply`、`with`、`also`: `LQL-2003` | `+= 1` か直接の代入を使う |
| 文字列テンプレートや `+`（`"${it.description}_x"`）: `LQL-2007` | 文字列はラムダ式の外で作ってキャプチャする |
| `insert ... select` の明示的な結合 | 多対一のパスを使う |
| `returning` | DML の後にもう一度選択する |
| データベースの時刻（`current_timestamp`） | アプリケーションの時刻をバインドする |

ルールに違反すると、コード `[LQL-xxxx]` の付いた `LambdaQLException` が発生します。ほとんどのルールは `TranslationException`、代入のラムダ式の中の条件やローカル変数は `UnsupportedLambdaShapeException` です。[11. エラーと言語](../11-errors-and-languages.md) を参照してください。
すべてのルールを 1 か所で見るには: [10. ルールと制限](10-rules-and-limits.md)。

---

[Kotlin トラック](README.md) | 前: [6. 選択、集計、グループ化](06-select-group.md) | 次: [8. プロシージャと DB 関数](08-procedures-functions.md) | [このページの Java 版](../java/07-dml.md)
