<a id="72-update"></a>
# 7.2 更新（Kotlin）

> [!NOTE]
> **状況: 設計済み、まだ実装されていません。**
> この章は決定済みの設計です（決定 D28、マイルストーン M10）。
> Kotlin の構文はコンパイルでき（kotlinc 2.3.21）、SQL は Hibernate 6.6.5 を使って H2、MariaDB、Oracle で確認しました。
> 現在の lambdaQL ではまだ **動きません**。今動くのは解析コアと `where` + `toList()` だけです
> （H2、MariaDB、Oracle でテスト済み）。全体の状況は [トラックの README](README.md) を見てください。

[7. 更新、挿入、削除（Kotlin）](07-dml.md) | **7.2 更新** | [7.4 削除](07-4-delete.md) | [7.5 挿入](07-5-insert.md) | [Java](../java/07-2-update.md)

---


<a id="basic-form"></a>
## 基本の形

```kotlin
val n = qb.update(Order::class.java)
    .set { it.status = st; it.price = it.price!! * 1.1 }
    .where { it.status == 1 }
    .execute()                     // 更新された行の数
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

`st` はキャプチャ値です。lambdaQL はこれをバインドパラメーターとして送ります。`!!` は無視されます（D23）。

<a id="values-null-date-boolean"></a>
## 値: null、日付、boolean

```kotlin
qb.update(Order::class.java)
    .set { it.description = null; it.updateAt = now; it.active = true }
    .where { it.id == id }
    .execute()
```

enum の値も同じように使えます。同じ値を 2 回使う場合でも、キャプチャ値ごとに **専用のパラメーター** が作られます（ルール U7）。

<a id="add-to-a-value-default-for-null"></a>
## 値に足す、NULL のときのデフォルト

```kotlin
qb.update(Order::class.java).set { it.cnt += 1 }.where { it.status == 1 }.execute()
// → set o.cnt = o.cnt + 1

qb.update(Order::class.java)
    .set { it.price = (it.price ?: 0.0) + 1 }
    .all()
    .execute()
// → set o.price = coalesce(o.price, 0.0) + 1.0
```

Kotlin では、`+=` には null にならないプロパティ（`cnt: Int`）が必要です。null になり得るプロパティでは、`it.total = it.total!! + 1` と書くか、上のように `?:` を使ってください。

<a id="embedded-fields"></a>
## 埋め込みフィールド

```kotlin
qb.update(Order::class.java).set { it.address.city = city }.where { it.id == id }.execute()
// → set o.address.city = :p1

qb.update(Order::class.java).set { it.address = addr }.where { it.id == id }.execute()
// → 埋め込み値のすべてのカラム（city、zip）を設定する
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
## 関連（外部キー）を変える

```kotlin
qb.update(Order::class.java).set { it.customer?.id = cid }.where { it.id == id }.execute()
// → set o.customer.id = :p1      （FK カラム。?. の代入は常に代入する）

qb.update(Order::class.java).set { it.customer = null }.where { it.id == id }.execute()
// → set o.customer = null

qb.update(Order::class.java).set { it.customer = customer }.where { it.id == id }.execute()
// → キャプチャした customer の @Id をバインドする（ルール U11）
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
`it.customer?.name = x` はエラーです。別のテーブルを変更することになるからです。

<a id="a-value-from-another-table"></a>
## 別のテーブルの値

関連のパスを通して値を読みます。lambdaQL はこれを **相関スカラーサブクエリ** として出力します（ルール U9）。

```kotlin
qb.update(Order::class.java).set { it.custName = it.customer?.name }.all().execute()
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

顧客がいない注文では、`custName` は NULL になります。

<a id="an-aggregate-from-a-subquery"></a>
## サブクエリからの集約

引数が 2 つのラムダを使います。2 つ目の引数はコンテキストオブジェクト（`sub`）です。
ここでは `Agg` は必要ありません。[5. サブクエリ](05-subquery.md) と同じく、サブクエリに `sum`、`avg`、... があります。

```kotlin
qb.update(Order::class.java)
    .set { o, sub ->
        o.total = sub.from(Item::class.java).where { i -> i.order?.id == o.id }.sum { i -> i.price }
    }
    .where { it.status == 1 }
    .execute()
```

item がない注文では、`total` は NULL になります。これは SQL の動作です。

引数が 1 つのトレイリングラムダ（`set { it... }`）と引数が 2 つのもの（`set { o, sub -> ... }`）は、別々のオーバーロードです。Kotlin が正しい方を選びます。

<a id="where-with-paths-and-subqueries"></a>
## パスとサブクエリを使った where

`where` は選択クエリと同じです。

```kotlin
qb.update(Order::class.java).set { it.status = 0 }.where { it.customer?.grade!! > 3 }.execute()

qb.update(Order::class.java).set { it.status = 9 }
    .where { o, sub -> !sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
    .execute()
```

<a id="optimistic-locking-version"></a>
## 楽観的ロック（`@Version`）

エンティティに `@Version` プロパティがあると、lambdaQL は `update versioned` を出力します。データベースがバージョンに 1 を足します。

```kotlin
val n = qb.update(VOrder::class.java)
    .set { it.status = 8 }
    .where { it.id == id && it.version == v }
    .execute()
if (n == 0) throw OptimisticLockException()   // 結果の確認はあなたの仕事
```

`@Version` プロパティを自分で代入しないでください。エラーになります（ルール U15）。

<a id="many-set-calls-patch"></a>
## `set()` を何度も呼ぶ（PATCH）

`set()` は何度でも呼べます。すべての代入は、呼び出した順に 1 つの SET リストにまとめられます（ルール U14）。

```kotlin
var u = qb.update(Order::class.java).set { it.status = dto.status }
if (dto.description != null) {
    u = u.set { it.description = dto.description }
}
u.where { it.id == id }.execute()
```

<a id="update-all-rows-all"></a>
## すべての行を更新する: `all()`

`where` のない更新では、`all()` と書く必要があります。`where` も `all()` もないと、コードは **コンパイルできません**（ルール U5）。

```kotlin
qb.update(Order::class.java).set { it.status = 0 }.all().execute()   // OK
qb.update(Order::class.java).set { it.status = 0 }.execute()         // コンパイルエラー: execute() がない
```

---

---

[7. 更新、挿入、削除（Kotlin）](07-dml.md) | **7.2 更新** | [7.4 削除](07-4-delete.md) | [7.5 挿入](07-5-insert.md) | [Java](../java/07-2-update.md)
