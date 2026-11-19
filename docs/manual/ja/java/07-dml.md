<a id="7-update-insert-delete-java"></a>
# 7. 更新、挿入、削除（Java）

> [!IMPORTANT]
> このページは韓国語版のマニュアルより古い内容です。最新のルールは[韓国語のページ](../../ko/java/07-dml.md)を参照してください。

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

- 各部分に **ラムダを 1 つ** 書きます。static ヘルパーは、setter のないエンティティ向けの `Dml.value` だけです（[7.3](#73-two-ways-to-assign-setter-or-dmlvalue)）。
- ラムダは **実行されません**。lambdaQL はそのバイトコードを読みます。`o.setStatus(1)` のような setter 呼び出しは `set e0.status = 1` になります。
- `where` は [3. 条件（`where`）](03-where.md) と同じルールを使います。対象の行は、同じラムダで選択した行と同じです。
- `execute()` は文を実行し、**影響を受けた行数**（`int`）を返します。
- `executeOne()` は、**最大 1 行** だけを変更すべき更新や削除を実行します（[1 行保護](#one-row-protection) を参照）。
- `orderBy(...).limit(n)` は先頭の `n` 行だけを変更します（[limit](#limit-change-the-first-rows) を参照）。
- `toHql()` は、文を実行せずに HQL とパラメーターを返します。
- 文の実行にはトランザクションが必要です（[トランザクション](#transactions) を参照）。

<a id="contents"></a>
## 目次

- [7.1 例で使うエンティティ](#71-example-entities)
- [7.2 更新（update）](#72-update)
- [7.3 値を代入する 2 つの方法: setter か `Dml.value` か](#73-two-ways-to-assign-setter-or-dmlvalue)
- [7.4 削除（delete）](#74-delete)
- [7.5 挿入（insert）](#75-insert)
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

テスト済みの例は、[`query/src/test/java/org/lambdaql/query`](https://github.com/codebit0/lambdaql/tree/main/query/src/test/java/org/lambdaql/query) のテスト用エンティティを使います。このエンティティには少し違いがあります。テストの `Order` では `price` が `double`、`status` が `short` で、`float` のフィールド `tex` があります。`IdOrder` の id は IDENTITY 方式です。

---

<a id="72-update"></a>
## 7.2 更新（update）

→ [7.2 更新（update）](07-2-update.md)

<a id="73-two-ways-to-assign-setter-or-dmlvalue"></a>
## 7.3 値を代入する 2 つの方法: setter か `Dml.value` か

| 書き方 | 使う場面 | 例 |
|---|---|---|
| setter | エンティティに setter があるとき。コンパイラーが値の型を確認します。 | `set(o -> o.setStatus(1))` |
| `Dml.value(column, value)` | エンティティに **setter がないとき**（たとえば DDD スタイルのエンティティ）。 | `set(o -> value(o.getStatus(), 1))` |

`value` は静的関数 `org.lambdaql.Dml.value` です（決定 D49）。`import static org.lambdaql.Dml.value;` で取り込み、引数 1 つのラムダの中で使います。

**(テスト済み)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)、リージョン `update-value`:

```java
// entities without setters: Dml.value(column, value), the column written as a getter call
ExecutableUpdate<Order> u = qb.update(Order.class)
        .set(o -> value(o.getDescription(), "done"))
        .where(o -> o.getStatus() == 2);
assertEquals("update Order e0 set e0.description = 'done' where e0.status = 2", u.toHql().hql());
```

**SQL ブロックの読み方。** `…` は省略した select リスト（エンティティのすべてのカラム）で、`?` はバインドパラメーターです。各行は [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java) から取っています。このテストは各クエリを Hibernate 6.6.5 で H2 2.3、MariaDB 10.11、Oracle 23.26 に対して実行し、Hibernate の SQL が変わると失敗します。

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `update-value`:

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

この章の setter の例は、すべて `value` でも書けます。1 つの文に `value` 呼び出しを 1 つ書きます。

```java
import static org.lambdaql.Dml.value;

qb.update(Order.class)
  .set(o -> { value(o.getStatus(), st); value(o.getPrice(), o.getPrice() * 1.1); })
  .where(o -> o.getStatus() == 1)
  .execute();

qb.update(Order.class).set(o -> value(o.getAddress().getCity(), city)).where(o -> o.getId() == id).execute(); // embedded
qb.update(Order.class).set(o -> value(o.getCustomer().getId(), cid)).where(o -> o.getId() == id).execute();  // FK
```

`value` のルール（ルール U20）:

- 最初の引数は **対象から始まる getter 呼び出し** でなければなりません。フィールド、埋め込みのパス、または関連の `@Id` です。
  キャプチャ値、計算式、ソースエンティティのパスは解析時のエラー（`LQL-3039`）です。
- 同じプロパティの setter のルールをそのまま適用します。`value(o.getX(), v)` は `o.setX(v)` と同じなので U1 と U6 が適用され、関連には `@Id` だけを代入できます（`LQL-3050`）。
- setter と `value` は **1 つのラムダの中で混ぜて使えます**。
- Java では、型がジェネリックなので `value(o.getStatus(), "abc")` はコンパイルできます。
  lambdaQL は解析時にカラムの型と値の型を照合し、合わなければ `LQL-3065` エラーを出します。
  数値は数値カラムに合います。ただし、小数は整数カラムに合いません（小数部分が失われるため）。型がわからない値（`Object`、ジェネリックな集計の `Number`）は照合しません。
- 独自の `value(...)` メソッドを持つクラス（たとえば `value` コンポーネントを持つレコード）の中では、静的インポートが隠されます。そこでは `Dml.value(...)` と書いてください。
- `o.getTotal() = 1` のような書き方は Java ではコンパイルエラーなので使えません。

**(テスト済み)** [`AssignmentReaderTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/sem/AssignmentReaderTest.java)、リージョン `value-type`:

```java
// value(V, V) compiles with any two types, so lambdaQL checks the value type against the column (U20)
assertEquals("[col0.status := 2, col0.price := 1]", setters(o -> { value(o.getStatus(), 2); value(o.getPrice(), 1); }));
String text = "abc";
LambdaQLException wrong = assertThrows(LambdaQLException.class, () -> setters(o -> value(o.getStatus(), text)));
assertEquals("LQL-3065", wrong.code(), wrong.getMessage());
// a decimal into a whole number column would lose the fraction
LambdaQLException fraction = assertThrows(LambdaQLException.class, () -> setters(o -> value(o.getStatus(), 1.5)));
assertEquals("LQL-3065", fraction.code(), fraction.getMessage());
```

**(テスト済み)** [`AssignmentReaderTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/sem/AssignmentReaderTest.java)、リージョン `read-value`:

```java
// Dml.value(column, value) for entities without setters; constants stay literals, also when they are boxed
// for the generic value(V, V) (D37, D49)
assertEquals("[col0.status := 2, col0.price := 1.0]",
        setters(o -> { value(o.getStatus(), (short) 2); value(o.getPrice(), 1.0); }));
// setters and value can be mixed in one lambda
assertEquals("[col0.status := 2, col0.customer.id := 1]",
        setters(o -> { o.setStatus((short) 2); value(o.getCustomer().getId(), 1); }));
```

<a id="subquery-in-a-set-value"></a>
### `set` の値の中のサブクエリ

代入ラムダの 2 番目の引数は、サブクエリの起点 `sub` です。`where((o, sub) -> ...)` と同じ書き方です（決定 D49）。
`insert ... select` では 3 番目の引数が `sub` です（[7.5](07-5-insert.md) を参照）。

**(テスト済み)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)、リージョン `update-sub`:

```java
// the second parameter starts a subquery, as in where((o, sub) -> ...) (D49)
ExecutableUpdate<Order> u = qb.update(Order.class)
        .set((o, sub) -> o.setPrice(sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).sum(i -> i.getPrice())))
        .where((o, sub) -> sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).exists());
assertEquals("update Order e0 set e0.price = (select sum(s1.price) from Item s1 where s1.order.id = e0.id)"
        + " where exists (select 1 from Item s1 where s1.order.id = e0.id)", u.toHql().hql());
// sum gives NULL when there is no row; coalesce keeps a NOT NULL column valid for every row
ExecutableUpdate<Order> all = qb.update(Order.class)
        .set((o, sub) -> value(o.getPrice(), Objects.requireNonNullElse(
                sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).sum(i -> i.getPrice()), 0.0)))
        .all();
assertEquals("update Order e0 set e0.price = coalesce((select sum(s1.price) from Item s1 where s1.order.id = e0.id), 0.0)",
        all.toHql().hql());
```

- 別のテーブルの集計も同じテーブルの集計も、`set` の値に使えます（H2、MariaDB 10.11、Oracle 23.26 で実測）。`update` では MariaDB もエラー 1093 を出しません。
- 一致する行がなければ、`sum` などは NULL を返します。NOT NULL カラムではデータベースのエラーになるので、`Objects.requireNonNullElse(…, 0.0)` で包んでください。`coalesce(...)` になります。
- insert の `values((a, sub) -> ...)` と upsert（`onConflict(...).update((a, sub) -> ...)`）でも、同じ方法でサブクエリを使います（[7.5](07-5-insert.md#a-subquery-in-a-value) を参照）。リスト形式では `values(list, (a, l, sub) -> ...)` のように 3 番目の引数が `sub` です。

---

<a id="74-delete"></a>
## 7.4 削除（delete）

→ [7.4 削除（delete）](07-4-delete.md)

<a id="75-insert"></a>
## 7.5 挿入（insert）

→ [7.5 挿入（insert）](07-5-insert.md)

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
| `o.setActive(!o.isActive())`（反転） | **v0.1 にはありません**（条件付きの値です）。`where` の異なる 2 つの更新を使います |
| `o.setDescription(o.getDescription() + "_x")` | **v0.1 にはありません**（文字列の連結） |
| 値としての `LocalDateTime.now()` | アプリケーションで計算してバインドします（ルール U8） |
| `UpdateSet<Order> u = qb.update(...).set(...)`（または `var`） | 更新を段階的に組み立てます（[PATCH](07-2-update.md#many-set-calls-patch) を参照） |

---

<a id="78-important-rules"></a>
## 7.8 重要なルール

<a id="the-right-side-reads-the-old-value"></a>
### 右辺の基準: 古い値

SQL では、SET の右辺にあるすべてのカラムは更新 **前** の値を意味します。lambdaQL もこのルールに従います。

```java
.set(o -> { o.setPrice(o.getPrice() * 2); o.setTotal(o.getPrice()); })
// error: price is assigned first, then read again
```

**同じ文の中で、カラムに代入したあとにそのカラムを読む** と、lambdaQL は `set(...)` を呼んだ時点で `TranslationException`（`LQL-3042`）を投げます（ルール U3）。
理由: この場合 MariaDB は **新しい** 値を使い、H2 と Oracle は古い値を使います。結果が異なります（実測: MariaDB は 200、H2 と Oracle は 100）。

次のケースは 3 つのデータベースの結果が一致するので、許可します。

| 書き方 | 許可 |
|---|---|
| `o.setPrice(o.getPrice() * 1.1)`（自分自身を読む） | 許可 |
| `o.setTotal(o.getPrice()); o.setPrice(0.0);`（先に読み、あとで代入） | 許可 |
| `o.setPrice(...); o.setTotal(o.getPrice());`（先に代入し、あとで読む） | **エラー** |

この確認は物理カラム（埋め込みのサブカラム、FK カラム）単位で行われ、複数の `set()` 呼び出しにまたがっても適用されます（[PATCH](07-2-update.md#many-set-calls-patch) でテスト済み）。

<a id="assign-a-column-only-once"></a>
### カラムへの代入は 1 回だけ

同じカラムに 2 回代入するとエラーです（ルール U4）。`setAddress(...)` のあとの `getAddress().setCity(...)`、
または `setCustomer(...)` のあとの `getCustomer().setId(...)` も含みます。

<a id="rules-checked-at-analysis-time"></a>
### 解析時のルール確認

lambdaQL は、ラムダを読むときにルール U1、U3、U4、U6 を確認します。次のテストはユーザー API ではなく、内部クラス `AssignmentReader` を呼びます。ユーザー API では、`set(...)` と `values(...)` が同じ `LambdaQLException` を投げます（たとえば U1 は `LQL-3037`、U3 は `LQL-3042`、U4 は `LQL-3041`）。

**(テスト済み)** [`AssignmentReaderTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/sem/AssignmentReaderTest.java)、リージョン `rules`:

```java
// U1: only setters; no condition, no local variable
assertThrows(LambdaQLException.class, () -> setters(o -> { if (o.isActive()) o.setStatus((short) 1); }));
// U6: an association's other properties belong to another table
assertThrows(LambdaQLException.class, () -> setters(o -> o.getCustomer().setName("x")));
// U3: a value reads a column written before
assertThrows(LambdaQLException.class, () -> AssignmentReader.checkRules(
        AssignmentReader.readSetters(SerializedLambdaExtractor.extract((Assign<Order>) o -> {
            o.setPrice(1);
            o.setTex((float) o.getPrice());
        }), List.of(Order.class), -1, new JpaEntityModel(emf.getMetamodel())), "test"));
```

<a id="where-or-all-is-required"></a>
### `where` か `all()` の指定が必須

`set()` は `execute()` を **持たない** 型（`UpdateSet`）を返します。`execute()` を持つ型（`ExecutableUpdate`）を返すのは `where()` と `all()` だけです。
そのため、`where` を書き忘れるとコンパイルエラーになります（ルール U5）。`delete` も同じです。

<a id="association-paths-same-rows-as-select"></a>
### 関連のパス: 選択と同じ行

lambdaQL は、DML の中の FK でない関連のパスを **相関スカラーサブクエリ** として書きます。`where` でも SET の右辺でも同じです（ルール U9）。次の行は [7.2](07-2-update.md#a-value-from-another-table) のテスト済みの例から取っています。

```java
.where(o -> o.getCustomer().getName().equals("Kim"))
// → where (select s1.name from Customer s1 where s1.id = e0.customer.id) = 'Kim' and e0.customer.id is not null

o.setCustName(o.getCustomer().getName())
// → set e0.custName = (select s1.name from Customer s1 where s1.id = e0.customer.id)
```

理由は次のとおりです。lambdaQL がパスをそのまま Hibernate に渡すと、Hibernate はデータベースごとに異なる結合の SQL を作ります
（H2 は `merge`、MariaDB は `update ... join`、Oracle はインラインビュー）。また、SET にパスがあると **顧客のない行が抜けました**
（実測: `.all()` で 10 行のうち 9 行だけが更新されました）。
lambdaQL はサブクエリを自分で書きます。そうすると文は自分のテーブルだけを読むので、対象テーブルの読み取りに関する MariaDB の制約（エラー 1093）に当たりません。

選択では、`where` の中のパスに内部結合を使います（決定 D36）。そのため DML では、lambdaQL はそのようなパスに `e0.customer.id is not null` を加えます。
その結果、`update` と `delete` は `qb.from(X.class).where(同じラムダ)` が返す **まったく同じ行** だけを対象にします（テスト済み: 削除した行を選択の結果と比べています）。
`o.getCustomer() == null || ...` のような null ガードがあっても同じです。選択でもこのパスに内部結合を使うので（決定 D48）、確認は残ります。

`@Id` で終わるパス（`o.getCustomer().getId()`）は FK カラムです。サブクエリも結合も使いません。

<a id="batching-for-many-rows"></a>
### 多数の行のバッチ処理

`values(list, ...)` は、VALUES 行を多数持つ 1 つの文を書きます（ルール U13）。

| 項目 | ルール |
|---|---|
| 分割の単位 | 行数ではなく、値の数（行 x カラム） |
| 上限 | 1 文あたり値 500 個以下（固定） |
| 戻り値 | すべての文の合計 |
| トランザクション | すべての文を 1 つのトランザクションで実行 |
| 空のリスト | SQL なし、0 を返す（テスト済み） |

Oracle にはバインド値 65,535 個という厳しい上限があります（ORA-01745）。パースはそれよりずっと前からとても遅くなります
（設計時のテストでは 21,845 行で約 6 分）。そのため上限を小さくしています。

<a id="limit-change-the-first-rows"></a>
### limit: 先頭の行の変更

`where(...)` または `all()` のあとに `orderBy(...).limit(n)` を付けると、更新や削除は最大 `n` 行だけを変更します。対象は `qb.from(X.class).where(same).orderBy(same)` の先頭 `n` 行です。

```java
qb.delete(Order.class).where(o -> o.getStatus() == 1).orderBy((o, by) -> by.asc(o.getPrice())).limit(100).execute();
```

| 項目 | ルール |
|---|---|
| 順序 | `orderBy` のキー、その後に id（ルール S9）。`orderBy` がなければ id 順 |
| `limit` のない `orderBy` | エラー（`IllegalStateException`） |
| レンダリング | `e0.id in (select t.id from (select x0.id as id from X x0 ... order by ... limit n) t)` |
| 対象の行 | 内側の select は結合も含めて選択のルール（D36）に従うので、選択と同じ行 |
| MariaDB | 派生テーブル `t` でエラー 1093 と 1235 を回避 |

テスト済みの例とデータベースごとの SQL: [7.4](07-4-delete.md#delete-the-first-rows-limit)（削除）、[7.2](07-2-update.md#update-the-first-rows-limit)（更新）。

<a id="one-row-protection"></a>
### 1 行保護

`execute()` の代わりに `executeOne()` を呼ぶと、**最大 1 行** だけを変更します。

1. lambdaQL は条件に一致する行の id を最大 2 つ読みます。
2. id が 2 つなら、何も変更せずに `NonUniqueResultException` を投げます。
3. id が 1 つなら、`id = :p and (条件)` でその行を変更します。

2 行以上を変更することはなく、ロールバックにも頼りません。結果は 0 か 1 です。`executeOne()` を `orderBy` や `limit` と一緒に使うとエラー（`IllegalStateException`）です。

テスト済みの例: [7.4](07-4-delete.md#delete-exactly-one-row-executeone)。Spring のリポジトリでは `deleteOne` と `updateOne` を使います（[12.4](12-spring-data-jpa.md#one-row-protection-deleteone-and-updateone) を参照）。

<a id="transactions"></a>
### トランザクション

一括の文にはトランザクションが必要です。lambdaQL がトランザクションを開くのは、`EntityManagerFactory` のコンストラクターを使ったビルダーだけです。

| ビルダー | トランザクション |
|---|---|
| `new QueryBuilder(entityManagerFactory)` | `execute()` と `executeOne()` が自分でトランザクションを開いてコミットします。エラーのときはロールバックします |
| `EntityManager` を使うビルダー: `new QueryBuilder(entityManager)`、Spring の `QueryBuilder` の Bean、リポジトリの実行器 | 呼び出し側のトランザクション（`@Transactional`、またはその `EntityManager` の有効なトランザクション）。トランザクションがなければ、SQL を送る前に `TransactionRequiredException` で失敗します（テスト済み）。Spring のリポジトリ呼び出しでは `InvalidDataAccessApiUsageException` になります（[12.6](12-spring-data-jpa.md#126-transactions-and-exceptions) を参照） |

<a id="the-persistence-context-is-not-updated"></a>
### 永続コンテキストへの未反映

一括 DML は JPA の永続コンテキストを通りません（ルール U16）。

- Hibernate は文の実行前に保留中の変更をフラッシュします。
- 文の実行後、同じ `EntityManager` ですでに読み込まれたエンティティは **古い値** を持っています。それらを再び使う場合は `entityManager.clear()` を呼んでください。
- JPA の `cascade` と `orphanRemoval` は **実行されません**。子を先に削除するか、データベースで `ON DELETE CASCADE` を使ってください。

<a id="sqlrestriction-and-softdelete"></a>
### `@SQLRestriction` と `@SoftDelete`

Hibernate は、エンティティの `@SQLRestriction` の条件と `@SoftDelete` の条件を、lambdaQL が作るすべての文に付けます（MariaDB 10.11、Oracle 23.26 で実測。H2 は未測定）。
一括の `update` と `delete` も同じです。例: `where m1_0.order_id=? and (m1_0.hidden = 0)`。

`@SoftDelete` エンティティの `delete` は `update … set deleted = 1 where … and deleted = 0` になります。行は実際には削除されません。
lambdaQL はこの条件を別に処理しません。

---

<a id="79-all-rules-u1-to-u21-summary"></a>
## 7.9 ルール U1 から U21 のすべて（まとめ）

lambdaQL がラムダを読むときに確認するルール: U1、U3、U4、U6（テスト済み）。コンパイラーが確認するルール: U5。
U11、U12、U15 の lambdaQL による確認はまだ実装されていません。

| # | ルール |
|---|---|
| U1 | 代入のラムダには、対象、その埋め込みパス、関連の `@Id` に対する **setter 呼び出しと `Dml.value(column, value)` だけ** を書きます（`LQL-3037`）。`if`、三項演算子 `c ? a : b`、ループ、ローカル変数、その他のメソッド呼び出しはエラーです。代入のないラムダもエラーです。 |
| U2 | 右辺は `where` の値のルール（[3. 条件（`where`）](03-where.md)）に従います。キャプチャ値はバインドパラメーター、ラムダに書いた定数はリテラルです（D37）。`Objects.requireNonNullElse(a, b)` は `coalesce(a, b)` になります。 |
| U3 | 右辺は **古い** 値を読みます。同じ文の中で、カラムに代入したあとにそのカラムを読むとエラーです。 |
| U4 | 同じ物理カラムに 2 回代入するとエラーです。複数の `set()` 呼び出しにまたがる場合も同じです。 |
| U5 | `where()` か `all()` がなければ `execute()` はありません。コンパイラーが確認します。 |
| U6 | 省略できないカラム（`@Id`、`nullable=false`、プリミティブ）に `null` を代入するとエラーです。コレクション、または関連エンティティの `@Id` 以外のフィールドに代入してもエラーです。 |
| U7 | キャプチャ値は、使うたびに別のパラメーターになります。（テストでは、STRING カラムと ORDINAL カラムが 1 つの enum パラメーターを共有すると、エラーなしで間違った値が保存されました。） |
| U8 | `LocalDateTime.now()` はアプリケーションで評価してバインドします。データベースの時刻（`current_timestamp`）はサポートしません。 |
| U9 | DML の中の FK でない関連のパスは、`where` でも SET の右辺でも相関スカラーサブクエリになります。`where` では、選択なら内部結合するパス（D36）に `customer.id is not null` を加えます。DML は選択と同じ行を対象にします。 |
| U10 | `insert ... select` では、ソースのパスは明示的な `left join` になります。 |
| U11 | `o.setCustomer(entity)` はエンティティの `@Id` をバインドします。id が null の場合、バインドは失敗します。 |
| U12 | IDENTITY と SEQUENCE の id は省略できます。TABLE ジェネレーターは `values` でだけ動作し、`insert ... select` では動作しません。ジェネレーターがなければ id は必須です。その他の省略できないフィールドも必須です。 |
| U13 | 多数の行は、1 文あたり値 500 個以下に分割します。結果は合計されます。すべての文を 1 つのトランザクションで実行します。 |
| U14 | 1 つの `set` ラムダで複数のカラムに代入でき、`set()` は何回でも呼べます。すべて呼び出した順に 1 つの SET リストにまとめられます。setter と `Dml.value` は 1 つのラムダの中でも混ぜて使えます（D49）。 |
| U15 | `@Version` を持つエンティティは `update versioned` を使います。バージョンに直接代入しないでください。`execute() == 0` の確認は利用者が行います。 |
| U16 | 一括 DML は永続コンテキストを通りません。実行前にフラッシュ、実行後のエンティティは古いまま、cascade はありません。 |
| U17 | DELETE のサブクエリが対象のエンティティ（または同じ継承階層）を読むと、lambdaQL は対象の id を先に派生テーブルで読みます: `d0.id in (select t.id from (select e0.id as id from … where <condition>) t)`。そうしないと MariaDB でエラー 1093 になります。相関サブクエリでも動作し、`update` と複合 id のエンティティは包みません（[7.4](07-4-delete.md#delete-with-a-subquery-on-the-same-table) を参照）。 |
| U18 | 継承: SINGLE_TABLE は完全にサポートします。JOINED と TABLE_PER_CLASS の update/delete は一時テーブルを使います。それらのサブクラスへの insert は拒否されます。 |
| U19 | クエリキャッシュのキーには、入口、対象エンティティ、set のラムダ、where/all、versioned かどうか、分割サイズが含まれます。 |
| U20 | `Dml.value(column, value)`（D49）: 最初の引数は、対象（または `insert ... select` の対象 `a`）から始まる getter 呼び出しでなければなりません（`LQL-3039`）。同じプロパティの setter のルール（U1、U6、関連は `@Id` だけ）をそのまま適用します。値の型はカラムの型と照合します（`LQL-3065`）。setter と混ぜて使えます。 |
| U21 | 各ラムダは 1 回だけ解析され、キャッシュされます。複数の `set()` 呼び出しにまたがる確認は、キャッシュされた要約を比べるだけです（プロトタイプで約 0.1〜0.2 µs）。 |

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

ルールに違反すると、コード `[LQL-xxxx]` 付きの `LambdaQLException` が発生します。ほとんどのルールは `TranslationException`、代入ラムダの中の条件やローカル変数は `UnsupportedLambdaShapeException` です。[11. エラーと言語](../11-errors-and-languages.md) を参照してください。
すべてのルールを 1 か所で見るには: [10. ルールと制限](10-rules-and-limits.md)。

---

[Java トラック](README.md) | 前: [6. 選択、集計、グループ化](06-select-group.md) | 次: [8. プロシージャと DB 関数](08-procedures-functions.md) | [このページの Kotlin 版](../kotlin/07-dml.md)
