<a id="7-update-insert-delete-java"></a>
# 7. 更新、挿入、削除（Java）

> [!NOTE]
> **状況**
> - **実装済み、テスト済み**（H2 2.3、MariaDB 10.11、Oracle 23.26、Hibernate 6.6.5、マイルストーン M10、決定 D28 と D40）: 更新（setter、`by.set`、複数回の `set()`、`@Version`）、削除、挿入（1 行、リスト、生成される id、`insert ... select`）、upsert、ルール U1、U3、U4、U6 の確認、および（決定 D41）更新と削除の `limit` と 1 行保護 `executeOne()`。
> - **まだ実装されていません:** DML の中の `sub` サブクエリ（マイルストーン M8）、`insert ... select` の集計（マイルストーン M9）、複合キーのエンティティでの `limit` / `executeOne()`。
> - **(テスト済み)** と書かれた例は、テストスイートからコピーしたものです。それ以外の例は設計に従っています。まだテストで確認しておらず、HQL の細かい部分（たとえば `e0` のようなエイリアス名）が違うことがあります。
> - 全体の状況は [Java トラックの README](README.md#status) を参照してください。

[Java トラック](README.md) | [マニュアルのホーム](../README.md) | 前: [6. 選択、集計、グループ化](06-select-group.md) | 次: [8. プロシージャと DB 関数](08-procedures-functions.md) | [このページの Kotlin 版](../kotlin/07-dml.md)

共通ページ: [1. 概要](../01-overview.md) · [11. エラーと言語](../11-errors-and-languages.md)

---

lambdaQL には 5 つの入口があります。

| 入口 | 用途 |
|---|---|
| `qb.from(X.class)` | 選択（3 章から 6 章） |
| `qb.update(X.class)` | 行の更新 |
| `qb.insert(X.class)` | 行の挿入 |
| `qb.delete(X.class)` | 行の削除 |
| `qb.upsert(X.class)` | 行を挿入する。同じ `@Id` の行があれば、その行を変更する |

考え方はクエリと同じです。

- 各部分に **ラムダを 1 つ** 書きます。static ヘルパーはありません。
- ラムダは **実行されません**。lambdaQL はそのバイトコードを読みます。`o.setStatus(1)` のような setter 呼び出しは `set e0.status = 1` になります。
- `where` は [3. 条件](03-where.md) と同じルールを使い、同じラムダの選択と同じ行を変更します。
- `execute()` は文を実行し、**影響を受けた行数**（`int`）を返します。
- `executeOne()` は、**最大 1 行** だけを変更すべき更新や削除を実行します（[1 行保護](#one-row-protection) を参照）。
- `orderBy(...).limit(n)` は先頭の `n` 行だけを変更します（[limit](#limit-change-the-first-rows) を参照）。
- `toHql()` は、文を実行せずに HQL とパラメーターを返します。
- 文にはトランザクションが必要です（[トランザクション](#transactions) を参照）。

<a id="contents"></a>
## 目次

- [7.1 例で使うエンティティ](#71-example-entities)
- [7.2 更新](#72-update)
- [7.3 代入の 2 つの方法: setter か `by.set` か](#73-two-ways-to-assign-setter-or-byset)
- [7.4 削除](#74-delete)
- [7.5 挿入](#75-insert)
- [7.6 Upsert](#76-upsert)
- [7.7 DML に関する Java の注意点](#77-java-notes-for-dml)
- [7.8 重要なルール](#78-important-rules)
- [7.9 ルール U1 から U21 のすべて（まとめ）](#79-all-rules-u1-to-u21-summary)
- [7.10 v0.1 にないもの](#710-not-in-v01)

---

<a id="71-example-entities"></a>
## 7.1 例で使うエンティティ

この章では `Order`、`Customer`、`Item` を使います（[2. はじめよう](02-getting-started.md#example-entities) を参照）。
ここでは `Order` にいくつかフィールドを追加しています。

| フィールド | 型 | 説明 |
|---|---|---|
| `id`、`price`、`status`、`active`、`description`、`updateAt`、`customer` | | 他の章と同じ |
| `total` | `Double` | 明細の価格の合計 |
| `cnt` | `Integer` | カウンター |
| `custName` | `String` | 顧客名のコピー |
| `address` | `Address`（埋め込み: `city`、`zip`） | 埋め込み値 |

その他のエンティティ: `VOrder`（`@Version` フィールド `version` を持つ注文）、`IdOrder`（id が生成される注文）、`Archive`（`id`、`price`、`customerName`）、`Summary`（`customerId`、`total`、`cnt`）。

テスト済みの例は、[`query/src/test/java/org/lambdaql/query`](https://github.com/codebit0/lambdaql/tree/main/query/src/test/java/org/lambdaql/query) のテスト用エンティティを使います。少し違いがあります。テストの `Order` では `price` が `double`、`status` が `short` で、`float` のフィールド `tex` があります。`IdOrder` の id は IDENTITY です。

---

<a id="72-update"></a>
## 7.2 更新

→ [7.2 更新](07-2-update.md)

<a id="73-two-ways-to-assign-setter-or-byset"></a>
## 7.3 代入の 2 つの方法: setter か `by.set` か

| 書き方 | 使う場面 | 例 |
|---|---|---|
| setter | エンティティに setter がある。コンパイラーが値の型を確認します。 | `set(o -> o.setStatus(1))` |
| `by.set(column, value)` | エンティティに **setter がない**（たとえば DDD スタイルのエンティティ）。 | `set((o, by) -> by.set(o.getStatus(), 1))` |

**(テスト済み)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)、リージョン `update-by`:

```java
ExecutableUpdate<Order> u = qb.update(Order.class)
        .set((o, by) -> by.set(o.getDescription(), "done"))
        .where(o -> o.getStatus() == 2);
assertEquals("update Order e0 set e0.description = 'done' where e0.status = 2", u.toHql().hql());
```

**SQL ブロックの読み方。** `…` は省略した select リスト（エンティティのすべてのカラム）で、`?` はバインドパラメーターです。各行は [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java) から取っています。このテストは各クエリを Hibernate 6.6.5 で H2 2.3、MariaDB 10.11、Oracle 23.26 に対して実行し、Hibernate の SQL が変わると失敗します。

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `update-by`:

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

Oracle は `set` にエイリアスを書きます（`set o1_0.status=?`）。H2 と MariaDB は書きません。

この章の setter の例は、すべて `by.set` でも書けます。呼び出しはつなげて書きます。ラムダは最後の `by.set(...)` を返します。

```java
qb.update(Order.class)
  .set((o, by) -> by.set(o.getStatus(), st).set(o.getPrice(), o.getPrice() * 1.1))
  .where(o -> o.getStatus() == 1)
  .execute();

qb.update(Order.class).set((o, by) -> by.set(o.getAddress().getCity(), city)).where(o -> o.getId() == id).execute(); // embedded
qb.update(Order.class).set((o, by) -> by.set(o.getCustomer().getId(), cid)).where(o -> o.getId() == id).execute();  // FK
```

設計では、ラムダの 2 番目の引数は **1 つのコンテキストオブジェクト** です。`set(column, value)` とサブクエリのメソッド `from(...)` の両方を持っています。
名前は `by` でも `sub` でも好きに付けられます。名前に意味はありません。
サブクエリの部分は **まだ実装されていません**（マイルストーン M8）。現在このオブジェクトが持つのは `set` だけです。

```java
qb.update(Order.class)
  .set((o, by) -> by.set(o.getTotal(),
        by.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).sum(i -> i.getPrice())))
  .all()
  .execute();
```

`by.set` のルール（ルール U20）:

- 最初の引数は **対象から始まるカラムのパス** でなければなりません。フィールド、埋め込みのパス、または関連の `@Id` です。
  キャプチャ値、計算、ソースエンティティのパスはエラーです。
- ラムダは `by.set(...)` 呼び出しをつなげた 1 つの式でなければなりません。それ以外の文はエラーです。
- Java では、型がジェネリックなので `by.set(o.getStatus(), "abc")` はコンパイルできます。
  設計では、解析時にカラムの型と値の型を確認します。この確認はまだ実装されていません。
- **1 つのラムダの中で** setter と `by.set` を混ぜないでください。別々の `set()` 呼び出しなら混ぜられます。

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

<a id="77-java-notes-for-dml"></a>
## 7.7 DML に関する Java の注意点

| Java の書き方 | 意味 |
|---|---|
| ブロックラムダ `o -> { o.setA(...); o.setB(...); }` | `set`、`values`、`insert ... select`、`onConflict(...).update(...)` で使えますが、**setter 呼び出しだけ** です。ローカル変数、`if`、ループは使えません（ルール U1） |
| `Objects.requireNonNullElse(a, b)` | `coalesce(a, b)`（ルール U2） |
| `o.setCnt(o.getCnt() + 1)` | サポートします。javac は値をアンボクシングしてボクシングしますが、lambdaQL はそれを無視します |
| `o.setStatus((short) 1)` | `short` や `byte` のフィールドに javac が必要とするキャストです。定数はリテラルのままです |
| `o.setActive(!o.isActive())`（反転） | **v0.1 にはありません**（条件付きの値です）。`where` の異なる 2 つの更新を使ってください |
| `o.setDescription(o.getDescription() + "_x")` | **v0.1 にはありません**（文字列の連結） |
| 値としての `LocalDateTime.now()` | アプリケーションで計算してバインドします（ルール U8） |
| `UpdateSet<Order> u = qb.update(...).set(...)`（または `var`） | 更新を段階的に組み立てます（[PATCH](07-2-update.md#many-set-calls-patch) を参照） |

---

<a id="78-important-rules"></a>
## 7.8 重要なルール

<a id="the-right-side-reads-the-old-value"></a>
### 右辺は古い値を読む

SQL では、SET の右辺にあるすべてのカラムは更新 **前** の値を意味します。lambdaQL もこのルールに従います。

```java
.set(o -> { o.setPrice(o.getPrice() * 2); o.setTotal(o.getPrice()); })
// error: price is assigned first, then read again
```

**同じ文の中で、カラムに代入したあとにそのカラムを読む** と、lambdaQL は `set(...)` を呼んだときに `LambdaAnalysisException` を投げます（ルール U3）。
理由: この場合 MariaDB は **新しい** 値を使い、H2 と Oracle は古い値を使います。結果が異なります（確認結果: MariaDB は 200、H2 と Oracle は 100）。

次のケースは問題ありません。3 つのデータベースの結果が一致するからです。

| 書き方 | OK? |
|---|---|
| `o.setPrice(o.getPrice() * 1.1)`（自分自身を読む） | はい |
| `o.setTotal(o.getPrice()); o.setPrice(0.0);`（先に読み、あとで代入） | はい |
| `o.setPrice(...); o.setTotal(o.getPrice());`（先に代入し、あとで読む） | **エラー** |

この確認は、物理カラム（埋め込みのサブカラム、FK カラム）単位で、複数の `set()` 呼び出しにまたがって行われます（[PATCH](07-2-update.md#many-set-calls-patch) でテスト済み）。

<a id="assign-a-column-only-once"></a>
### カラムへの代入は 1 回だけ

同じカラムに 2 回代入するとエラーです（ルール U4）。`setAddress(...)` のあとに `getAddress().setCity(...)` を呼ぶ場合や、
`setCustomer(...)` のあとに `getCustomer().setId(...)` を呼ぶ場合も含みます。

<a id="rules-checked-at-analysis-time"></a>
### 解析時に確認するルール

lambdaQL は、ラムダを読むときにルール U1、U3、U4、U6 を確認します。このテストはユーザー API ではなく、内部クラス `AssignmentReader` を呼びます。ユーザー API も、`set(...)` と `values(...)` から同じ `LambdaAnalysisException` を投げます。

**(テスト済み)** [`AssignmentReaderTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/sem/AssignmentReaderTest.java)、リージョン `rules`:

```java
// U1: only setters; no condition, no local variable
assertThrows(LambdaAnalysisException.class, () -> setters(o -> { if (o.isActive()) o.setStatus((short) 1); }));
// U6: an association's other properties belong to another table
assertThrows(LambdaAnalysisException.class, () -> setters(o -> o.getCustomer().setName("x")));
// U3: a value reads a column written before
assertThrows(LambdaAnalysisException.class, () -> AssignmentReader.checkRules(
        AssignmentReader.readSetters(SerializedLambdaExtractor.extract((Assign<Order>) o -> {
            o.setPrice(1);
            o.setTex((float) o.getPrice());
        }), List.of(Order.class), -1, new JpaEntityModel(emf.getMetamodel())), "test"));
```

<a id="where-or-all-is-required"></a>
### `where` か `all()` が必要

`set()` は `execute()` を **持たない** 型（`UpdateSet`）を返します。`execute()` を持つ型（`ExecutableUpdate`）を返すのは `where()` と `all()` だけです。
そのため、`where` を書き忘れるとコンパイルエラーになります（ルール U5）。`delete` も同じです。

<a id="association-paths-same-rows-as-select"></a>
### 関連のパス: 選択と同じ行

lambdaQL は、DML の中の FK でない関連のパスを、`where` でも SET の右辺でも **相関スカラーサブクエリ** として書きます（ルール U9）。次の行は [7.2](07-2-update.md#a-value-from-another-table) のテスト済みの例から取っています。

```java
.where(o -> o.getCustomer().getName().equals("Kim"))
// → where (select s1.name from Customer s1 where s1.id = e0.customer.id) = 'Kim' and e0.customer.id is not null

o.setCustName(o.getCustomer().getName())
// → set e0.custName = (select s1.name from Customer s1 where s1.id = e0.customer.id)
```

なぜでしょうか。lambdaQL がパスをそのまま Hibernate に渡すと、Hibernate はデータベースごとに異なる結合の SQL を作り
（H2 は `merge`、MariaDB は `update ... join`、Oracle はインラインビュー）、SET にパスがあると **顧客のない行が失われました**
（確認結果: `.all()` で 10 行のうち 9 行しか更新されませんでした）。
lambdaQL はサブクエリを自分で書きます。そうすると文は自分のテーブルだけを読むので、対象テーブルの読み取りに関する MariaDB の制限（エラー 1093）は当てはまりません。

選択では `where` の中のパスは内部結合です（決定 D36）。そのため DML では、lambdaQL はそのようなパスに `e0.customer.id is not null` を加えます。
こうして `update` と `delete` は、`qb.from(X.class).where(同じラムダ)` が返す **まったく同じ行** を対象にします（テスト済み: 削除した行を選択の結果と比べています）。
`o.getCustomer() == null || ...` のような null ガードは、選択でも顧客のない行を残すので、そのパスには `is not null` の確認を加えません。

`@Id` で終わるパス（`o.getCustomer().getId()`）は FK カラムです。サブクエリも結合も使いません。

<a id="batching-for-many-rows"></a>
### 多数の行のバッチ処理

`values(list, ...)` は、VALUES 行を多数持つ 1 つの文を書きます（ルール U13）。

| 項目 | ルール |
|---|---|
| 分割の単位 | 行数ではなく、値の数（行 x カラム） |
| 上限 | 1 文あたり最大 500 個の値（固定） |
| 戻り値 | すべての文の合計 |
| トランザクション | すべての文を 1 つのトランザクションで実行します |
| 空のリスト | SQL を実行せず、0 を返します（テスト済み） |

Oracle にはバインド値 65,535 個という固定の上限があります（ORA-01745）。それよりずっと前にパースがとても遅くなります
（設計時のテストでは 21,845 行で約 6 分）。そのため上限を小さくしています。

<a id="limit-change-the-first-rows"></a>
### limit: 先頭の行を変更する

`where(...)` または `all()` の後の `orderBy(...).limit(n)` で、更新や削除が変更する行を最大 `n` 行にします。対象は `qb.from(X.class).where(同じ).orderBy(同じ)` の先頭 `n` 行です。

```java
qb.delete(Order.class).where(o -> o.getStatus() == 1).orderBy((o, by) -> by.asc(o.getPrice())).limit(100).execute();
```

| 項目 | ルール |
|---|---|
| 順序 | `orderBy` のキー、その後に id（ルール S9）。`orderBy` がなければ id 順 |
| `limit` のない `orderBy` | エラー（`IllegalStateException`） |
| 出力 | `e0.id in (select t.id from (select x0.id as id from X x0 ... order by ... limit n) t)` |
| 行 | 内側の select は結合も含めて select のルールに従う（D36）ので、行は select と同じ |
| MariaDB | 派生テーブル `t` でエラー 1093 と 1235 を避ける |

テスト済みの例と各データベースの SQL: [7.4](07-4-delete.md#delete-the-first-rows-limit)（削除）と [7.2](07-2-update.md#update-the-first-rows-limit)（更新）。

<a id="one-row-protection"></a>
### 1 行保護

`execute()` の代わりに `executeOne()` を使うと、変更は **最大 1 行** になります。

1. lambdaQL は一致する行の id を最大 2 つ読みます。
2. id が 2 つなら、何も変更せずに `NonUniqueResultException` を投げます。
3. id が 1 つなら、その行を `id = :p and (あなたの条件)` で変更します。

2 行以上を変更することはなく、ロールバックにも頼りません。結果は 0 か 1 です。`executeOne()` を `orderBy` や `limit` と一緒に使うとエラーです（`IllegalStateException`）。
テスト済みの例: [7.4](07-4-delete.md#delete-exactly-one-row-executeone)。Spring のリポジトリでは `deleteOne` と `updateOne` です（[12.4](12-spring-data-jpa.md#one-row-protection-deleteone-and-updateone) を参照）。

<a id="transactions"></a>
### トランザクション

一括の文にはトランザクションが必要です。lambdaQL が自分でトランザクションを開くのは、`EntityManagerFactory` のコンストラクターを使ったときだけです。

| ビルダー | トランザクション |
|---|---|
| `new QueryBuilder(entityManagerFactory)` | `execute()` と `executeOne()` が自分でトランザクションを開いてコミットします。エラーのときはロールバックします |
| `EntityManager` を使うビルダー: `new QueryBuilder(entityManager)`、Spring の `QueryBuilder` の Bean、リポジトリの実行器 | 呼び出し側のトランザクション（`@Transactional`、またはその `EntityManager` の有効なトランザクション）。トランザクションがなければ、SQL を送る前に `TransactionRequiredException` で失敗します（テスト済み）。Spring のリポジトリ呼び出しでは `InvalidDataAccessApiUsageException` になります（[12.6](12-spring-data-jpa.md#126-transactions-and-exceptions) を参照） |

<a id="the-persistence-context-is-not-updated"></a>
### 永続コンテキストは更新されない

一括 DML は JPA の永続コンテキストを通りません（ルール U16）。

- Hibernate は文の前に保留中の変更をフラッシュします。
- 文のあと、同じ `EntityManager` ですでに読み込まれたエンティティは **古い値** を持っています。それらを再び使う場合は `entityManager.clear()` を呼んでください。
- JPA の `cascade` と `orphanRemoval` は **実行されません**。子を先に削除するか、データベースで `ON DELETE CASCADE` を使ってください。

---

<a id="79-all-rules-u1-to-u21-summary"></a>
## 7.9 ルール U1 から U21 のすべて（まとめ）

lambdaQL がラムダを読むときに確認するもの: U1、U3、U4、U6（テスト済み）。コンパイラーが確認するもの: U5。
U11、U12、U15、U20 の lambdaQL による確認はまだ実装されていません。U17 にはサブクエリ（マイルストーン M8）が必要です。

| # | ルール |
|---|---|
| U1 | 代入のラムダには、対象、その埋め込みパス、または関連の `@Id` に対する **setter 呼び出しだけ** を書きます。`if`、三項演算子 `c ? a : b`、ループ、ローカル変数、その他のメソッド呼び出しはエラーです。代入のないラムダもエラーです。 |
| U2 | 右辺は `where` の値のルール（[3. 条件](03-where.md)）に従います。キャプチャ値はバインドパラメーター、ラムダの中の定数はリテラルです（D37）。`Objects.requireNonNullElse(a, b)` は `coalesce(a, b)` になります。 |
| U3 | 右辺は **古い** 値を読みます。同じ文の中で、カラムに代入したあとにそのカラムを読むとエラーです。 |
| U4 | 同じ物理カラムに 2 回代入するとエラーです。`set()` 呼び出しをまたいでも同じです。 |
| U5 | `where()` か `all()` がなければ `execute()` はありません。コンパイラーが確認します。 |
| U6 | 省略できないカラム（`@Id`、`nullable=false`、プリミティブ）に `null` を代入するとエラーです。コレクション、または関連エンティティの `@Id` 以外のフィールドに代入するとエラーです。 |
| U7 | キャプチャ値は、使うたびに別のパラメーターになります。（テストでは、STRING カラムと ORDINAL カラムで 1 つの enum パラメーターを共有すると、間違った値が黙って保存されました。） |
| U8 | `LocalDateTime.now()` はアプリケーションで評価してバインドします。データベースの時刻（`current_timestamp`）はサポートしません。 |
| U9 | DML の中の FK でない関連のパスは、`where` でも SET の右辺でも相関スカラーサブクエリになります。`where` では、選択が内部結合するパス（D36）に `customer.id is not null` も加えます。DML は選択と同じ行を対象にします。 |
| U10 | `insert ... select` では、ソースのパスは明示的な `left join` になります。 |
| U11 | `o.setCustomer(entity)` はエンティティの `@Id` をバインドします。id が null の場合、バインドは失敗します。 |
| U12 | IDENTITY と SEQUENCE の id は省略できます。TABLE ジェネレーターは `values` でだけ使え、`insert ... select` では使えません。ジェネレーターがなければ id は必須です。その他の省略できないフィールドも必須です。 |
| U13 | 多数の行は分割されます。1 文あたり最大 500 個の値です。結果は合計されます。すべての文を 1 つのトランザクションで実行します。 |
| U14 | 1 つの `set` ラムダで複数のカラムに代入でき、`set()` は何回でも呼べます。すべて呼び出した順に 1 つの SET リストにまとめられます。 |
| U15 | `@Version` を持つエンティティは `update versioned` を使います。バージョンに自分で代入しないでください。`execute() == 0` の確認はあなたの仕事です。 |
| U16 | 一括 DML は永続コンテキストを通りません。前にフラッシュ、後のエンティティは古いまま、cascade はありません。 |
| U17 | DELETE のサブクエリが同じエンティティを読む場合、lambdaQL はそれを派生テーブルで包みます。（そうしないと MariaDB でエラー 1093 になります。） |
| U18 | 継承: SINGLE_TABLE は完全にサポートします。JOINED と TABLE_PER_CLASS の update/delete は一時テーブルを使います。それらのサブクラスへの insert は拒否されます。 |
| U19 | クエリキャッシュのキーには、入口、対象エンティティ、set のラムダ、where/all、versioned、分割サイズが含まれます。 |
| U20 | `by.set(column, value)`: カラムは対象に対する getter のパスでなければなりません。型は解析時に確認されます。1 つのラムダの中で setter と `by.set` を混ぜないでください。 |
| U21 | 各ラムダは 1 回だけ解析され、キャッシュされます。`set()` 呼び出しをまたぐ確認は、キャッシュされた要約を比べるだけです（プロトタイプで約 0.1〜0.2 µs）。 |

upsert のルール（決定 D40）は [7.6 Upsert](07-6-upsert.md#rules) にあります。

---

<a id="710-not-in-v01"></a>
## 7.10 v0.1 にないもの

| 項目 | 予定 | 今できること |
|---|---|---|
| 条件付きの値（`c ? a : b`、値としての `!o.isActive()`） | v0.2（CASE WHEN） | – |
| 代入を選ぶためのラムダ内の `if` | v0.2 | `set()` を何回も呼ぶ（U14） |
| 文字列の連結（`o.getDescription() + "_x"`） | v0.2 | – |
| `insert ... select` での明示的な結合 | v0.2 | 多対一のパスを使う |
| `returning` | 拒否 | – |
| データベースの時刻（`current_timestamp`） | 拒否 | アプリケーションの時刻をバインドする（U8） |

現在、ルールに違反すると、簡単なメッセージ（今は韓国語）付きの `LambdaAnalysisException` が投げられます。予定している `[LQL-xxxx]` コードと基底クラス `LambdaQLException` は [11. エラーと言語](../11-errors-and-languages.md) にあります。
すべてのルールを 1 か所にまとめたもの: [10. ルールと制限](10-rules-and-limits.md)。

---

[Java トラック](README.md) | 前: [6. 選択、集計、グループ化](06-select-group.md) | 次: [8. プロシージャと DB 関数](08-procedures-functions.md) | [このページの Kotlin 版](../kotlin/07-dml.md)
