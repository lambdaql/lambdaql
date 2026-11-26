<a id="72-update"></a>
# 7.2 更新（Kotlin）

[7. 更新、挿入、削除（Kotlin）](07-dml.md) | **7.2 更新** | [7.4 削除](07-4-delete.md) | [7.5 挿入](07-5-insert.md) | [7.6 Upsert](07-6-upsert.md) | [Java](../java/07-2-update.md)

---

<a id="basic-form"></a>
## 基本形

<!-- region: ManualKotlinDmlTest.kt#update -->
```kotlin
val st: Short = 5
val u = qb.update(Order::class.java)
    .set { it.status = st; it.price = it.price * 2.0 }
    .where { it.status.toInt() == 1 }
// the price on the right is the value before the change
assertEquals("update Order e0 set e0.status = :p1, e0.price = (e0.price * 2.0) where e0.status = 1", u.toHql().hql())
val changed = u.execute()                      // number of updated rows
```

`st` はキャプチャした値なので、バインドパラメーター（`:p1`）になります。定数 `2.0` はリテラルのまま残ります。
例のエンティティ `Order` は Java クラスで、`status` は `short` なので、条件は `it.status.toInt() == 1` と書きます。
`toHql()` は文を実行せず、HQL とパラメーターを返します。

**データベースごとの SQL**

<!-- sql: update -->
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

Oracle は `set` に別名を書きます（`set o1_0.status=?`）。H2 と MariaDB は書きません。

コード例: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`update`）。

<a id="values-null-date-boolean"></a>
## 値: null、日付、boolean

<!-- region: ManualKotlinDmlTest.kt#update-values -->
```kotlin
// null, a captured date, a boolean and an enum constant
val u = qb.update(Order::class.java)
    .set { it.description = null; it.updateAt = now; it.isActive = false; it.kind = Order.Kind.RUSH }
    .where { it.id == id }
assertEquals("update Order e0 set e0.description = null, e0.updateAt = :p1, e0.active = false,"
    + " e0.kind = org.lambdaql.query.Order\$Kind.RUSH where e0.id = :p2", u.toHql().hql())
```

`null`、boolean、enum 定数はリテラルになります。enum 定数は完全なクラス名（`org.lambdaql.query.Order$Kind.RUSH`）で書きます。
boolean プロパティ `active` の Java の getter は `isActive()` なので、Kotlin では `it.isActive` に代入します。
キャプチャした値は、同じ値を 2 回使っても、それぞれ **自分のパラメーター** を受け取ります。

コード例: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`update-values`）。

<a id="add-to-a-value-default-for-null"></a>
## 値に加算する、NULL のときのデフォルト

<!-- region: ManualKotlinDmlTest.kt#update-add -->
```kotlin
// += on a property: the old value plus 1
val add = qb.update(Order::class.java).set { it.cnt += 1 }.where { it.status.toInt() == 1 }
assertEquals("update Order e0 set e0.cnt = (e0.cnt + 1) where e0.status = 1", add.toHql().hql())
// ?: gives a default for NULL: coalesce
val name = qb.update(Order::class.java).set { it.custName = it.description ?: "none" }.all()
assertEquals("update Order e0 set e0.custName = coalesce(e0.description, 'none')", name.toHql().hql())
```

例のエンティティの `cnt` は Java の `Integer` なので、Kotlin はプラットフォーム型として見て `+=` をコンパイルします。`cnt` が NULL の行は、SQL のルールに従って NULL のまま残ります。
`?:` は文字列でも数値でも `coalesce(...)` になります。`it.total = (it.total ?: 0.0) + 1` は `set e0.total = (coalesce(e0.total, 0.0) + 1.0)` です。

コード例: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`update-add`）。

<a id="embedded-fields"></a>
## 埋め込みフィールド

<!-- region: ManualKotlinDmlTest.kt#update-embedded -->
```kotlin
// one column of the embedded value
val one = qb.update(Order::class.java).set { it.address.city = city }.where { it.id == id }
assertEquals("update Order e0 set e0.address.city = :p1 where e0.id = :p2", one.toHql().hql())
// the whole embedded value: every column (city, zip)
val all = qb.update(Order::class.java).set { it.address = addr }.where { it.status.toInt() == 1 }
assertEquals("update Order e0 set e0.address = :p1 where e0.status = 1", all.toHql().hql())
```

埋め込みの値全体を代入すると HQL は `set e0.address = :p1` で、Hibernate がすべてのカラム（`city`、`zip`）を書きます。

コード例: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`update-embedded`）。

<a id="change-an-association-foreign-key"></a>
## 関連（外部キー）を変更する

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

関連エンティティでは、`@Id`（FK）だけを設定できます。
`it.customer?.name = x` はエラー（`LQL-3050`）です。別のテーブルを変更することになるからです（[7.8](07-dml.md#rules-checked-at-analysis-time) を参照）。

コード例: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`update-fk`）。

<a id="a-value-from-another-table"></a>
## 別のテーブルの値

関連のパスで値を読んでください。lambdaQL はこれを **相関スカラーサブクエリ** として出力します。
`where` のパスも同じ形のサブクエリになります。

<!-- region: ManualKotlinDmlTest.kt#update-path -->
```kotlin
// a path in the condition: a subquery, plus "customer exists" (the same rows as the select, U9)
val u = qb.update(Order::class.java)
    .set { it.custName = "K" }
    .where { it.customer?.name == "Kim" }
assertEquals("update Order e0 set e0.custName = 'K' where (select s1.name from Customer s1 where s1.id = fk(e0.customer)) = 'Kim'"
    + " and e0.customer.id is not null", u.toHql().hql())
// a path in a value: a subquery; NULL when there is no customer
val copy = qb.update(Order::class.java).set { it.custName = it.customer?.name }.all()
assertEquals("update Order e0 set e0.custName = (select s1.name from Customer s1 where s1.id = fk(e0.customer))",
    copy.toHql().hql())
```

**データベースごとの SQL**

<!-- sql: update-path -->
```sql
-- H2
update orders o1_0
set cust_name='K'
where (select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id)='Kim' and o1_0.customer_id is not null

-- MariaDB
update orders
set cust_name='K'
where (select c1_0.name from customers c1_0 where c1_0.id=orders.customer_id)='Kim' and customer_id is not null

-- Oracle
update orders o1_0
set o1_0.cust_name='K'
where (select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id)='Kim' and o1_0.customer_id is not null
```

**データベースごとの SQL**

<!-- sql: update-path#2 -->
```sql
-- H2
update orders o1_0
set cust_name=(select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id)

-- MariaDB
update orders
set cust_name=(select c1_0.name from customers c1_0 where c1_0.id=orders.customer_id)

-- Oracle
update orders o1_0
set o1_0.cust_name=(select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id)
```

注文に顧客がなければ、`custName` は NULL になります。
選択で inner join になるパスには、DML も顧客の存在チェック（`e0.customer.id is not null`）を加えます。上の `where { it.customer?.name == "Kim" }` は顧客のない注文が条件に一致しえないので inner join で、このチェックが付きます。
選択で `left join` になるパスには、このチェックを加えません。顧客のない注文ではサブクエリが NULL になるので、`= 'Kim'` のような比較には一致しません。どちらの場合も、更新の対象の行は `qb.from(Order::class.java).where { 同じラムダ式 }` の結果と同じです。

**`?.` と `||`。** `it.status == 1 || it.customer?.name == "Kim"` のように、顧客のない注文が条件を真にできるなら、そのような注文も選択とまったく同じく更新の対象になります（[3.10](03-where.md#310-association-paths) を参照）。

<!-- region: KotlinQueryTest.kt#update-safe-call -->
```kotlin
// UPDATE: the left join of ?. drops the "customer exists" check, so the order without a customer is changed too
val u = qb.update(Order::class.java)
    .set { it.status = st }
    .where { it.status.toInt() == 1 || it.customer?.name == "Kim" }
assertEquals("update Order e0 set e0.status = :p1"
    + " where e0.status = 1 or (select s1.name from Customer s1 where s1.id = fk(e0.customer)) = 'Kim'", u.toHql().hql())
```

**データベースごとの SQL**

<!-- sql: update-safe-call -->
```sql
-- H2
update orders o1_0
set status=?
where o1_0.status=1 or (select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id)='Kim'

-- MariaDB
update orders
set status=?
where status=1 or (select c1_0.name from customers c1_0 where c1_0.id=orders.customer_id)='Kim'

-- Oracle
update orders o1_0
set o1_0.status=?
where o1_0.status=1 or (select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id)='Kim'
```

コード例: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`update-path`）、[`KotlinQueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)（`update-safe-call`）。

<a id="an-aggregate-from-a-subquery"></a>
## サブクエリの集約

引数が 2 つのラムダ式 `set { o, sub -> ... }` を使ってください。2 つ目の引数はサブクエリの起点 `sub` です。
ここでは `Agg` は要りません。サブクエリには、[5. サブクエリ](05-subquery.md) と同じく `sum`、`avg`、... があります。

<!-- region: KotlinQueryTest.kt#update-sub -->
```kotlin
// the second parameter starts a subquery, as in where { o, sub -> ... }
val u = qb.update(Order::class.java)
    .set { o, sub -> o.price = sub.from(Item::class.java).where { i -> i.order?.id == o.id }.sum { it.price } }
    .where { o, sub -> sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
assertEquals("update Order e0 set e0.price = (select sum(s1.price) from Item s1 where s1.order.id = e0.id)"
        + " where exists (select 1 from Item s1 where s1.order.id = e0.id)", u.toHql().hql())
```

- 別のテーブルの集約も同じテーブルの集約も使えます（H2、MariaDB 10.11、Oracle 23.26 で実測）。`update` では、MariaDB もエラー 1093 を出しません。
- 一致する行がなければ、集約は NULL です。NOT NULL のカラムなら DB のエラーになるので、`?: 0.0` を付けて `coalesce(...)` にしてください。

<!-- region: ManualKotlinDmlTest.kt#update-sub-coalesce -->
```kotlin
// sum gives NULL when there is no row; ?: becomes coalesce and keeps the NOT NULL price valid
val u = qb.update(Order::class.java)
    .set { o, sub ->
        o.price = sub.from(Item::class.java).where { i -> i.order?.id == o.id }.sum { it.price } ?: 0.0
    }
    .all()
assertEquals("update Order e0 set e0.price = coalesce((select sum(s1.price) from Item s1 where s1.order.id = e0.id), 0.0)",
    u.toHql().hql())
```

品目のない注文の価格は `0.0` になります。

引数が 1 つの末尾ラムダ式（`set { it... }`）と引数が 2 つの末尾ラムダ式（`set { o, sub -> ... }`）は、別々のオーバーロードです。Kotlin は適切なほうを選びます。

コード例: [`KotlinQueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)（`update-sub`）、[`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`update-sub-coalesce`）。

<a id="where-with-paths-and-subqueries"></a>
## パスとサブクエリを使う where

`where` は選択のクエリと同じです。

<!-- region: ManualKotlinDmlTest.kt#update-where -->
```kotlin
// a path: a subquery, as above
val gold = qb.update(Order::class.java).set { it.status = 0 }.where { it.customer?.grade!! > 3 }
assertEquals("update Order e0 set e0.status = 0 where (select s1.grade from Customer s1 where s1.id = fk(e0.customer)) > 3"
    + " and e0.customer.id is not null", gold.toHql().hql())
// a subquery with sub, as in chapter 5
val noItems = qb.update(Order::class.java).set { it.status = 9 }
    .where { o, sub -> !sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
assertEquals("update Order e0 set e0.status = 9 where not exists (select 1 from Item s1 where s1.order.id = e0.id)",
    noItems.toHql().hql())
```

1 つ目の文のパスは、上のルールのとおりスカラーサブクエリになります。2 つ目の文は、[5. サブクエリ](05-subquery.md) と同じく `sub` を使います。

コード例: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`update-where`）。

<a id="optimistic-locking-version"></a>
## 楽観的ロック（`@Version`）

エンティティに `@Version` プロパティがあると、lambdaQL は `update versioned` を出力します。データベースがバージョンに 1 を加えます。

<!-- region: ManualKotlinDmlTest.kt#update-versioned -->
```kotlin
// an entity with a version property (@Version): the version goes up too
val u = qb.update(VOrder::class.java).set { it.status = 2 }.where { it.id == 1L }
assertEquals("update versioned VOrder e0 set e0.status = 2 where e0.id = 1L", u.toHql().hql())
```

**データベースごとの SQL**

<!-- sql: update-versioned -->
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

Hibernate は SET リストに `version=(v1_0.version+1)` を加えます。バージョンを楽観的ロックとして使うには、`where` にバージョンを入れて結果を確認してください。

<!-- region: ManualKotlinDmlTest.kt#update-version-check -->
```kotlin
val n = qb.update(VOrder::class.java)
    .set { it.status = 8 }
    .where { it.id == id && it.version == v }
    .execute()
if (n == 0) throw OptimisticLockException()   // checking the result is your job
```

同じ文をもう一度実行すると、`v` は古いバージョンなので 0 行が更新され、`OptimisticLockException` が発生します。

`@Version` プロパティには代入できません。代入すると `LQL-3068` で拒否されます。

コード例: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`update-versioned`、`update-version-check`）。

<a id="many-set-calls-patch"></a>
## `set()` を何回も呼ぶ（PATCH）

`set()` は何回でも呼べます。すべての代入は、呼び出しの順に 1 つの SET リストにまとめられます。
前に代入したカラムをもう一度読めないというルールと、同じカラムに 2 回代入できないというルールは、複数の `set()` 呼び出しの間にも適用されます。

<!-- region: ManualKotlinDmlTest.kt#update-patch -->
```kotlin
// PATCH: assign only the properties that have a value; every set() adds assignments
val memo = "checked"
val newStatus: Short? = null
var u = qb.update(Order::class.java).set { it.description = memo }
if (newStatus != null) u = u.set { it.status = newStatus }
val changed = u.where { it.id == id }.execute()

// reading a column that an earlier assignment wrote is an error
val read = assertThrows(LambdaQLException::class.java) {
    qb.update(Order::class.java).set { it.price = 1.0; it.total = it.price }
}
assertEquals("LQL-3042", read.code())
// the same column twice is an error, also across set() calls
val twice = assertThrows(LambdaQLException::class.java) {
    qb.update(Order::class.java).set { it.status = 1 }.set { it.status = 2 }
}
assertEquals("LQL-3041", twice.code())
```

`set()` は `UpdateSet` を返します。この型には `set`、`where`、`all` があり、`execute()` はありません。

コード例: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`update-patch`）。

<a id="update-all-rows-all"></a>
## すべての行を更新する: `all()`

`where` のない update では、`all()` を明示しなければなりません。

<!-- region: ManualKotlinDmlTest.kt#update-all -->
```kotlin
// no where: all() is required, without it there is no execute()
val u = qb.update(Order::class.java).set { it.status = 0 }.all()
assertEquals("update Order e0 set e0.status = 0", u.toHql().hql())
val changed = u.execute()
```


update にはトランザクションが必要です（[7.8](07-dml.md#transactions) を参照）。

コード例: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`update-all`）。

<a id="update-the-first-rows-limit"></a>
## 先頭の行を更新する: `limit`

`where { ... }` または `all()` の後に `orderBy { ... }` と `limit(n)` を付けると、多くても `n` 行を更新します。対象は `qb.from(X::class.java).where { same }.orderBy { same }` の先頭の `n` 行です。

ルールは delete と同じです（[7.4](07-4-delete.md#delete-the-first-rows-limit) を参照）。`orderBy` がなければ id の順で、id は常に最後のソートキーで、`limit` のない `orderBy` はエラーです。

<!-- region: ManualKotlinDmlTest.kt#update-limit -->
```kotlin
// a path in the condition: the nested select joins, so the rows are the same as the select
val u = qb.update(Order::class.java)
    .set { it.description = "top" }
    .where { it.customer?.name == "Kim" }
    .orderBy { o -> desc(o.price) }
    .limit(1)
assertEquals("update Order e0 set e0.description = 'top' where e0.id in (select t.id from (select x0.id as id from Order x0"
    + " join x0.customer k1 where k1.name = 'Kim' order by x0.price desc, x0.id asc limit 1) t)", u.toHql().hql())
```

**データベースごとの SQL**

<!-- sql: update-limit -->
```sql
-- H2
update orders o1_0
set description='top'
where o1_0.id in (select t1_0.c0 from (select o2_0.id from orders o2_0 join customers c1_0 on c1_0.id=o2_0.customer_id where c1_0.name='Kim' order by o2_0.price desc,o2_0.id fetch first 1 rows only) t1_0(c0))

-- MariaDB
update orders o1_0
set description='top'
where o1_0.id in (select * from (select t1_0.c0 from (select * from (select o2_0.id c0 from orders o2_0 join customers c1_0 on c1_0.id=o2_0.customer_id where c1_0.name='Kim' order by o2_0.price desc,o2_0.id limit 1) _sub_) t1_0) _sub_)

-- Oracle
update orders o1_0
set o1_0.description='top'
where o1_0.id in (select t1_0.c0 from (select o2_0.id c0 from orders o2_0 join customers c1_0 on c1_0.id=o2_0.customer_id where c1_0.name='Kim' order by o2_0.price desc,o2_0.id fetch first 1 rows only) t1_0)
```

パス `it.customer?.name` は、選択と同じく内側の select で結合になります。update の文そのものは別のテーブルを読みません。

コード例: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`update-limit`）。

<a id="update-exactly-one-row-executeone"></a>
## ちょうど 1 行を更新する: `executeOne()`

`executeOne()` は 1 行保護を適用して update を実行します。条件に合う id を最大 2 つ読みます。

id が 2 つなら、何も変更せずに `NonUniqueResultException` を投げます。id が 1 つなら、`id = :p and (条件)` でその行を更新します。手順は delete と同じです（[7.4](07-4-delete.md#delete-exactly-one-row-executeone) を参照）。

<!-- region: ManualKotlinDmlTest.kt#update-one -->
```kotlin
// one row matches: it is changed
val one = qb.update(Order::class.java)
    .set { it.description = "checked" }
    .where { it.id == id }
    .executeOne()                             // 0 or 1
// two or more rows match: nothing is changed
assertThrows(NonUniqueResultException::class.java) {
    qb.update(Order::class.java).set { it.description = "x" }.where { it.isActive }.executeOne()
}
```

2 つ目の文は、すべての注文が条件に合うので、どの行も変更しません。

Spring のリポジトリーでは、`updateOne({ ... }) { ... }` が同じ動作をします（[12.4](12-spring-data-jpa.md#one-row-protection-deleteone-and-updateone) を参照）。

---

[7. 更新、挿入、削除（Kotlin）](07-dml.md) | **7.2 更新** | [7.4 削除](07-4-delete.md) | [7.5 挿入](07-5-insert.md) | [7.6 Upsert](07-6-upsert.md) | [Java](../java/07-2-update.md)

コード例: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`update-one`）。
