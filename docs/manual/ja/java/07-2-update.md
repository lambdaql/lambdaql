<a id="72-update"></a>
# 7.2 更新（Java）

> [!NOTE]
> **状況: 設計済み、まだ実装されていません。**
> この章は決定済みの設計です（決定 D28、マイルストーン M10）。
> 構文は Java でコンパイルでき、SQL は Hibernate 6.6.5 を使って H2、MariaDB、Oracle で確認しました。
> 現在の lambdaQL では **動きません**。現在動くのは解析コアと `where` + `toList()` だけです
> （H2、MariaDB、Oracle でテスト済み）。全体の状況は [Java トラックの README](README.md#status) を参照してください。

[7. 更新、挿入、削除（Java）](07-dml.md) | **7.2 更新** | [7.4 削除](07-4-delete.md) | [7.5 挿入](07-5-insert.md) | [Kotlin](../kotlin/07-2-update.md)

---


<a id="basic-form"></a>
## 基本形

```java
int n = qb.update(Order.class)
    .set(o -> { o.setStatus(st); o.setPrice(o.getPrice() * 1.1); })
    .where(o -> o.getStatus() == 1)
    .execute();                    // 更新された行数
```

HQL:

```sql
update Order o set o.status = :p1, o.price = o.price * 1.1 where o.status = 1
```

**SQL ブロックの読み方。** `…` は省略した select リスト（エンティティのすべてのカラム）で、`?` はバインドパラメーターです。[`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java) はこのページの HQL を Hibernate に直接渡し、H2 2.3、MariaDB 10.11、Oracle 23.26（Hibernate 6.6.5）での SQL を記録します。lambdaQL はまだこれらのクエリを作りません。Hibernate の SQL が変わるとテストは失敗します。

**データベースごとの SQL（予定）** — 予定の HQL を Hibernate が変換したもので、`PlannedSqlTest` が記録しました。lambdaQL はまだこのクエリを作りません。`expected-sql/` の [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)、例 `07-update`:

```sql
-- H2, MariaDB
update orders o1_0
set status=?,price=(o1_0.price*1.1)
where o1_0.status=1

-- Oracle
update orders o1_0
set o1_0.status=?,o1_0.price=(o1_0.price*1.1)
where o1_0.status=1
```

Oracle は `set` にエイリアスを書きます（`set o1_0.status=?`）。H2 と MariaDB は書きません。

`st` はキャプチャした変数です。lambdaQL はこれをバインドパラメーターとして送ります。

<a id="values-null-date-boolean"></a>
## 値: null、日付、boolean

```java
qb.update(Order.class)
  .set(o -> { o.setDescription(null); o.setUpdateAt(now); o.setActive(true); })
  .where(o -> o.getId() == id)
  .execute();
```

enum の値も同じように使えます。キャプチャ値は、同じ値を 2 回使う場合でも、**それぞれ別のパラメーター** になります（ルール U7）。

<a id="add-to-a-value-default-for-null"></a>
## 値に加算する、NULL のときのデフォルト

```java
qb.update(Order.class).set(o -> o.setCnt(o.getCnt() + 1)).where(o -> o.getStatus() == 1).execute();

qb.update(Order.class)
  .set(o -> o.setPrice(Objects.requireNonNullElse(o.getPrice(), 0.0) + 1))
  .all()
  .execute();
// → set o.price = coalesce(o.price, 0.0) + 1
```

**データベースごとの SQL（予定）** — 予定の HQL を Hibernate が変換したもので、`PlannedSqlTest` が記録しました。lambdaQL はまだこのクエリを作りません。`expected-sql/` の [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)、例 `07-update-coalesce`:

```sql
-- H2, MariaDB
update orders o1_0
set price=(coalesce(o1_0.price,0.0)+1)

-- Oracle
update orders o1_0
set o1_0.price=(coalesce(o1_0.price,0.0)+1)
```

<a id="embedded-fields"></a>
## 埋め込みフィールド

```java
qb.update(Order.class).set(o -> o.getAddress().setCity(city)).where(o -> o.getId() == id).execute();
// → set o.address.city = :p1

qb.update(Order.class).set(o -> o.setAddress(addr)).where(o -> o.getId() == id).execute();
// → 埋め込み値のすべてのカラム（city、zip）を設定します
```

**データベースごとの SQL（予定）** — 予定の HQL を Hibernate が変換したもので、`PlannedSqlTest` が記録しました。lambdaQL はまだこのクエリを作りません。`expected-sql/` の [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)、例 `07-update-embedded`:

```sql
-- H2, MariaDB
update orders o1_0
set city=?
where o1_0.id=?

-- Oracle
update orders o1_0
set o1_0.city=?
where o1_0.id=?
```

<a id="change-an-association-foreign-key"></a>
## 関連（外部キー）を変更する

```java
qb.update(Order.class).set(o -> o.getCustomer().setId(cid)).where(o -> o.getId() == id).execute();
// → set o.customer.id = :p1      （FK カラム）

qb.update(Order.class).set(o -> o.setCustomer(null)).where(o -> o.getId() == id).execute();
// → set o.customer = null

qb.update(Order.class).set(o -> o.setCustomer(customer)).where(o -> o.getId() == id).execute();
// → キャプチャした customer の @Id をバインドします（ルール U11）
```

**データベースごとの SQL（予定）** — 予定の HQL を Hibernate が変換したもので、`PlannedSqlTest` が記録しました。lambdaQL はまだこのクエリを作りません。`expected-sql/` の [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)、例 `07-update-fk`:

```sql
-- H2, MariaDB
update orders o1_0
set customer_id=?
where o1_0.id=?

-- Oracle
update orders o1_0
set o1_0.customer_id=?
where o1_0.id=?
```

**データベースごとの SQL（予定）** — 予定の HQL を Hibernate が変換したもので、`PlannedSqlTest` が記録しました。lambdaQL はまだこのクエリを作りません。`expected-sql/` の [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)、例 `07-update-fk-null`:

```sql
-- H2, MariaDB
update orders o1_0
set customer_id=null
where o1_0.id=?

-- Oracle
update orders o1_0
set o1_0.customer_id=null
where o1_0.id=?
```

関連エンティティに設定できるのは `@Id`（FK）だけです。
`o.getCustomer().setName(x)` はエラーです。別のテーブルを変更してしまうからです。

<a id="a-value-from-another-table"></a>
## 別のテーブルの値

関連のパスを通して値を読みます。lambdaQL はこれを **相関スカラーサブクエリ** として出力します（ルール U9）。

```java
qb.update(Order.class).set(o -> o.setCustName(o.getCustomer().getName())).all().execute();
```

```sql
update Order o
set o.custName = (select c.name from Customer c where c.id = o.customer.id)
```

**データベースごとの SQL（予定）** — 予定の HQL を Hibernate が変換したもので、`PlannedSqlTest` が記録しました。lambdaQL はまだこのクエリを作りません。`expected-sql/` の [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)、例 `07-update-other-table`:

```sql
-- H2, MariaDB
update orders o1_0
set cust_name=(select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id)

-- Oracle
update orders o1_0
set o1_0.cust_name=(select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id)
```

注文に顧客がない場合、`custName` は NULL になります。

<a id="an-aggregate-from-a-subquery"></a>
## サブクエリの集約

引数が 2 つのラムダを使います。2 番目の引数はコンテキストオブジェクト（`sub`）です。
ここでは `Agg` は必要ありません。サブクエリには [5. サブクエリ](05-subquery.md) と同じように `sum`、`avg`、... があります。

```java
qb.update(Order.class)
  .set((o, sub) -> o.setTotal(
      sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).sum(i -> i.getPrice())))
  .where(o -> o.getStatus() == 1)
  .execute();
```

注文に明細がない場合、`total` は NULL になります。これは SQL の動作です。

<a id="where-with-paths-and-subqueries"></a>
## パスとサブクエリを使う where

`where` は選択クエリと同じです。

```java
qb.update(Order.class).set(o -> o.setStatus(0)).where(o -> o.getCustomer().getGrade() > 3).execute();

qb.update(Order.class).set(o -> o.setStatus(9))
  .where((o, sub) -> !sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).exists())
  .execute();
```

<a id="optimistic-locking-version"></a>
## 楽観的ロック（`@Version`）

エンティティに `@Version` フィールドがある場合、lambdaQL は `update versioned` を出力します。データベースがバージョンに 1 を加えます。

```java
int n = qb.update(VOrder.class)
    .set(o -> o.setStatus(8))
    .where(o -> o.getId() == id && o.getVersion() == v)
    .execute();
if (n == 0) throw new OptimisticLockException();   // 結果の確認はあなたの仕事です
```

`@Version` フィールドに自分で代入しないでください。エラーになります（ルール U15）。

<a id="many-set-calls-patch"></a>
## `set()` を何回も呼ぶ（PATCH）

`set()` は何回でも呼べます。すべての代入は、呼び出した順に 1 つの SET リストにまとめられます（ルール U14）。

```java
var u = qb.update(Order.class).set(o -> o.setStatus(dto.status()));
if (dto.description() != null) {
    u = u.set(o -> o.setDescription(dto.description()));
}
u.where(o -> o.getId() == id).execute();
```

<a id="update-all-rows-all"></a>
## すべての行を更新する: `all()`

`where` のない更新には `all()` が必要です。`where` も `all()` もない場合、コードは **コンパイルできません**（ルール U5）。

```java
qb.update(Order.class).set(o -> o.setStatus(0)).all().execute();       // OK
qb.update(Order.class).set(o -> o.setStatus(0)).execute();             // コンパイルエラー: execute() がない
```

---

---

[7. 更新、挿入、削除（Java）](07-dml.md) | **7.2 更新** | [7.4 削除](07-4-delete.md) | [7.5 挿入](07-5-insert.md) | [Kotlin](../kotlin/07-2-update.md)
