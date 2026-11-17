<a id="72-update"></a>
# 7.2 更新（Kotlin）

> [!NOTE]
> **状況: 実装済み、テスト済み**（H2 2.3、MariaDB 10.11、Oracle 23.26、マイルストーン M10）。ほとんどのテストは **Java** のラムダを使っています。プロパティへの代入とインデックスの書き方 `by[column] = value` は Kotlin のラムダでもテストしています（[`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)、**(テスト済み)** と表示）。 `limit` と `executeOne()` も同じです（決定 D41）。
> `where` の中のサブクエリ（`sub`）も実装済みです（マイルストーン M8）。削除の形は Java のラムダでテスト済みです。[7.4](07-4-delete.md) を参照してください。
> **まだ実装されていません:** `set` の値の中の `sub` サブクエリ、複合キーのエンティティでの `limit` / `executeOne()`。
> Kotlin コードはテスト済みの Java の文を Kotlin の構文で書いたもので、HQL は [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) の Java のテストから取っています。HQL のない例は設計に従っています。
> 全体の状況は [トラックの README](README.md) を見てください。

[7. 更新、挿入、削除（Kotlin）](07-dml.md) | **7.2 更新** | [7.4 削除](07-4-delete.md) | [7.5 挿入](07-5-insert.md) | [7.6 Upsert](07-6-upsert.md) | [Java](../java/07-2-update.md)

---


<a id="basic-form"></a>
## 基本の形

```kotlin
val st = 5
val u = qb.update(Order::class.java)
    .set { it.status = st; it.price = it.price!! * 2.0 }
    .where { it.status == 1 }
// the price on the right is the value before the change
// HQL: update Order e0 set e0.status = :p1, e0.price = (e0.price * 2.0) where e0.status = 1
val changed = u.execute()                      // number of updated rows
```

`st` はキャプチャ値なので、バインドパラメーター（`:p1`）になります。`!!` は無視されます（D23）。定数 `2.0` はリテラルのままです（決定 D37）。
`toHql()` は文を実行せずに HQL とパラメーターを返します。

**SQL ブロックの読み方。** `…` は省略した select リスト（エンティティのすべてのカラム）で、`?` はバインドパラメーターです。各行は [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java) から取っています。このテストは各クエリを Hibernate 6.6.5 で H2 2.3、MariaDB 10.11、Oracle 23.26 に対して実行し、Hibernate の SQL が変わると失敗します。 このテストは Java のラムダを使います。意味が同じ Kotlin のラムダは同じ HQL になるので、SQL も同じです。 **（予定）** と書かれたブロックは [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java) から取っています。lambdaQL はまだこれらの文を作らないので、このテストが予定の HQL を Hibernate に直接渡します。

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `update`:

```sql
-- H2, MariaDB
update orders o1_0
set status=?,price=(o1_0.price*2.0)
where o1_0.status=1

-- Oracle
update orders o1_0
set o1_0.status=?,o1_0.price=(o1_0.price*2.0)
where o1_0.status=1
```

Oracle は `set` にエイリアスを書きます（`set o1_0.status=?`）。H2 と MariaDB は書きません。

**(テスト済み)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)、リージョン `update`:

```kotlin
val u = qb.update(Order::class.java)
    .set { it.status = st; it.price = it.price * 2 }
    .where { it.customer?.name == "Kim" }
assertEquals("update Order e0 set e0.status = :p1, e0.price = (e0.price * 2.0)"
    + " where (select s1.name from Customer s1 where s1.id = e0.customer.id) = 'Kim' and e0.customer.id is not null",
    u.toHql().hql())
```

`st` はキャプチャした `Short`（`7`）です。テストのエンティティの `price` はプリミティブの `double` なので、`!!` はありません。update の `where` の中のパス `it.customer?.name` はサブクエリになります（下の [パスとサブクエリを使った where](#where-with-paths-and-subqueries) を参照）。テストは文を実行し、行を読み直します。
Java で書いたほかの文は [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) でテストしています（H2、MariaDB、Oracle）。それらの例は [このページの Java 版](../java/07-2-update.md) を見てください。

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
// → sets all columns of the embedded value (city, zip)
```

<a id="change-an-association-foreign-key"></a>
## 関連（外部キー）を変える

```kotlin
qb.update(Order::class.java).set { it.customer?.id = cid }.where { it.id == id }.execute()
// → set o.customer.id = :p1      (the FK column; the ?. assignment always assigns)

qb.update(Order::class.java).set { it.customer = null }.where { it.id == id }.execute()
// → set o.customer = null

qb.update(Order::class.java).set { it.customer = customer }.where { it.id == id }.execute()
// → binds the @Id of the captured customer (rule U11)
```

関連エンティティに設定できるのは `@Id`（FK）だけです。
`it.customer?.name = x` はエラーです。別のテーブルを変更することになるからです（ルール U6）。

<a id="a-value-from-another-table"></a>
## 別のテーブルの値

関連のパスを通して値を読みます。lambdaQL はこれを **相関スカラーサブクエリ** として出力します（ルール U9）。
`where` の中のパスも、同じ種類のサブクエリになります。

```kotlin
// a path in the condition: a subquery, plus "customer exists" (the same rows as the select, D36, U9)
val u = qb.update(Order::class.java)
    .set { it.custName = "K" }
    .where { it.customer?.name == "Kim" }
// HQL: update Order e0 set e0.custName = 'K' where (select s1.name from Customer s1 where s1.id = e0.customer.id) = 'Kim' and e0.customer.id is not null

// a path in a value: a subquery; NULL when there is no customer
val copy = qb.update(Order::class.java).set { it.custName = it.customer?.name }.all()
// HQL: update Order e0 set e0.custName = (select s1.name from Customer s1 where s1.id = e0.customer.id)
```

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `update-path`:

```sql
-- H2, MariaDB
update orders o1_0
set cust_name='K'
where (select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id)='Kim' and o1_0.customer_id is not null

-- Oracle
update orders o1_0
set o1_0.cust_name='K'
where (select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id)='Kim' and o1_0.customer_id is not null
```

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `update-path#2`:

```sql
-- H2, MariaDB
update orders o1_0
set cust_name=(select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id)

-- Oracle
update orders o1_0
set o1_0.cust_name=(select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id)
```

顧客がいない注文では、`custName` は NULL になります。
`where` では lambdaQL が `e0.customer.id is not null` を加えるので、この更新は `qb.from(Order::class.java).where { 同じラムダ }` と同じ行を変更します（決定 D36）。Java のテストでこれを確認しています。

<a id="an-aggregate-from-a-subquery"></a>
## サブクエリからの集約

引数が 2 つのラムダを使います。2 つ目の引数はコンテキストオブジェクト（`sub`）です。
ここでは `Agg` は必要ありません。[5. サブクエリ](05-subquery.md) と同じく、サブクエリに `sum`、`avg`、... があります。
この書き方は **まだ実装されていません**。現在動くのは `where` の中のサブクエリだけです。

```kotlin
qb.update(Order::class.java)
    .set { o, sub ->
        o.total = sub.from(Item::class.java).where { i -> i.order?.id == o.id }.sum { i -> i.price }
    }
    .where { it.status == 1 }
    .execute()
```

**データベースごとの SQL（予定）** — 予定の HQL を Hibernate が変換したもので、`PlannedSqlTest` が記録しました。lambdaQL はまだこのクエリを作りません。`expected-sql/` の [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)、例 `07-update-sum`:

```sql
-- H2, MariaDB
update orders o1_0
set total=(select sum(i1_0.price) from items i1_0 where i1_0.order_id=o1_0.id)
where o1_0.status=1

-- Oracle
update orders o1_0
set o1_0.total=(select sum(i1_0.price) from items i1_0 where i1_0.order_id=o1_0.id)
where o1_0.status=1
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

どちらの例も現在動きます。
1 つ目の例は、上のテスト済みのルールに従います。パスはスカラーサブクエリになります。
2 つ目の例は [5. サブクエリ](05-subquery.md) と同じように `sub` を使います。その HQL は `update Order e0 set e0.status = 9 where not exists (select 1 from Item s1 where s1.order.id = e0.id)` です。
同じ `where` を削除で使う形は Java のラムダでテスト済みです（リージョン `dml-subquery`、[7.4](07-4-delete.md) を参照）。更新の形は、まだ専用のテストでカバーされていません。

**データベースごとの SQL（この HQL に対する Hibernate の SQL）** — 同じ文を Hibernate に直接渡す [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java) が記録しました。`expected-sql/` の [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)、例 `07-update-not-exists`:

```sql
-- H2, MariaDB
update orders o1_0
set status=9
where not exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id)

-- Oracle
update orders o1_0
set o1_0.status=9
where not exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id)
```

<a id="optimistic-locking-version"></a>
## 楽観的ロック（`@Version`）

エンティティに `@Version` プロパティがあると、lambdaQL は `update versioned` を出力します。データベースがバージョンに 1 を足します。

```kotlin
// an entity with a version property (@Version): the version goes up too
val u = qb.update(VOrder::class.java).set { it.status = 2 }.where { it.id == 1L }
// HQL: update versioned VOrder e0 set e0.status = 2 where e0.id = 1L
```

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `update-versioned`:

```sql
-- H2, MariaDB
update v_orders v1_0
set status=2,version=(v1_0.version+1)
where v1_0.id=1

-- Oracle
update v_orders v1_0
set v1_0.status=2,v1_0.version=(v1_0.version+1)
where v1_0.id=1
```

Hibernate は SET リストに `version=(v1_0.version+1)` を加えます。バージョンを楽観的ロックとして使うには、`where` に入れて結果を確認します。

```kotlin
val n = qb.update(VOrder::class.java)
    .set { it.status = 8 }
    .where { it.id == id && it.version == v }
    .execute()
if (n == 0) throw OptimisticLockException()   // checking the result is your job
```

`@Version` プロパティを自分で代入しないでください（ルール U15）。

<a id="many-set-calls-patch"></a>
## `set()` を何度も呼ぶ（PATCH）

`set()` は何度でも呼べます。すべての代入は、呼び出した順に 1 つの SET リストにまとめられます（ルール U14）。
ルール U3 と U4 は、`set()` 呼び出しをまたいでも適用されます。

```kotlin
// PATCH: assign only the properties that have a value; every set() adds assignments
val memo = "checked"
val newStatus: Int? = null
var u = qb.update(Order::class.java).set { it.description = memo }
if (newStatus != null) u = u.set { it.status = newStatus }
val changed = u.where { it.id == id }.execute()

// reading a column that an earlier assignment wrote is an error (U3): TranslationException (LQL-3042)
qb.update(Order::class.java).set { it.price = 1.0; it.total = it.price }
// the same column twice is an error (U4), also across set() calls: TranslationException (LQL-3041)
qb.update(Order::class.java).set { it.status = 1 }.set { it.status = 2 }
```

`set()` は `UpdateSet` を返します。`set`、`where`、`all` はありますが、`execute()` はありません。

<a id="update-all-rows-all"></a>
## すべての行を更新する: `all()`

`where` のない更新では、`all()` と書く必要があります。`where` も `all()` もないと、コードは **コンパイルできません**（ルール U5）。

```kotlin
qb.update(Order::class.java).set { it.status = 0 }.all().execute()   // OK
qb.update(Order::class.java).set { it.status = 0 }.execute()         // compile error: no execute()
```

更新にはトランザクションが必要です（[7.8](07-dml.md#transactions) を参照）。

<a id="update-the-first-rows-limit"></a>
## 先頭の行を更新する: `limit`

`where { ... }` または `all()` の後に `orderBy { ... }` と `limit(n)` を付けると、最大 `n` 行を変更します。対象は `qb.from(X::class.java).where { 同じ }.orderBy { 同じ }` の先頭 `n` 行です。
ルールは削除と同じです（[7.4](07-4-delete.md#delete-the-first-rows-limit) を参照）。`orderBy` がなければ id 順、id は常に最後のソートキー、`limit` のない `orderBy` はエラーです。

```kotlin
// a path in the condition: the nested select joins, so the rows are the same as the select
val u = qb.update(Order::class.java)
    .set { it.description = "top" }
    .where { it.customer?.name == "Kim" }
    .orderBy { o, by -> by.desc(o.price) }
    .limit(1)
// HQL: update Order e0 set e0.description = 'top' where e0.id in (select t.id from (select x0.id as id from Order x0 join x0.customer k1 where k1.name = 'Kim' order by x0.price desc, x0.id asc limit 1) t)
```

同じ文を Java で [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) がテストしています（リージョン `update-limit`）。

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `update-limit`:

```sql
-- H2
update orders o1_0
set description='top'
where o1_0.id in (select t1_0.id from (select o2_0.id from orders o2_0 join customers c1_0 on c1_0.id=o2_0.customer_id where c1_0.name='Kim' order by o2_0.price desc,o2_0.id fetch first 1 rows only) t1_0(id))

-- MariaDB
update orders o1_0
set description='top'
where o1_0.id in (select t1_0.id from (select o2_0.id id from orders o2_0 join customers c1_0 on c1_0.id=o2_0.customer_id where c1_0.name='Kim' order by o2_0.price desc,o2_0.id limit 1) t1_0)

-- Oracle
update orders o1_0
set o1_0.description='top'
where o1_0.id in (select t1_0.id from (select o2_0.id id from orders o2_0 join customers c1_0 on c1_0.id=o2_0.customer_id where c1_0.name='Kim' order by o2_0.price desc,o2_0.id fetch first 1 rows only) t1_0)
```

パス `it.customer?.name` は、select と同じように内側の select で結合になります（決定 D36）。更新そのものはほかのテーブルを読みません。

<a id="update-exactly-one-row-executeone"></a>
## ちょうど 1 行を更新する: `executeOne()`

`executeOne()` は 1 行保護付きで更新を実行します。一致する id を最大 2 つ読みます。id が 2 つなら何も変更せず、`NonUniqueResultException` を投げます。id が 1 つなら、その行を `id = :p and (あなたの条件)` で変更します。手順は削除と同じです（[7.4](07-4-delete.md#delete-exactly-one-row-executeone) を参照）。

```kotlin
val one = qb.update(Order::class.java)
    .set { it.description = "checked" }
    .where { it.id == id }
    .executeOne()                             // 0 or 1; 2 or more rows: NonUniqueResultException
```

`execute-one` リージョンの Java のテストメソッド（`DmlQueryTest` の `executeOneProtects`）は、更新での `executeOne()` も確認しています。2 行以上に一致すると何も変更しません。
Spring のリポジトリでは、`updateOne({ ... }) { ... }` が同じことをします（[12.4](12-spring-data-jpa.md#one-row-protection-deleteone-and-updateone) を参照）。

---

---

[7. 更新、挿入、削除（Kotlin）](07-dml.md) | **7.2 更新** | [7.4 削除](07-4-delete.md) | [7.5 挿入](07-5-insert.md) | [7.6 Upsert](07-6-upsert.md) | [Java](../java/07-2-update.md)
