<a id="74-delete"></a>
# 7.4 削除（Kotlin）

[7. 更新、挿入、削除（Kotlin）](07-dml.md) | [7.2 更新](07-2-update.md) | **7.4 削除** | [7.5 挿入](07-5-insert.md) | [7.6 Upsert](07-6-upsert.md) | [Java](../java/07-4-delete.md)

---

<!-- region: ManualKotlinDmlTest.kt#delete -->
```kotlin
val d = qb.delete(Order::class.java).where { it.status.toInt() == 3 }
assertEquals("delete from Order e0 where e0.status = 3", d.toHql().hql())
val deleted = d.execute()

// a path: the same rows as qb.from(Order::class.java).where { same lambda } - with !! orders without a customer stay
val byName = qb.delete(Order::class.java).where { it.customer!!.name == null }
assertEquals("delete from Order e0 where (select s1.name from Customer s1 where s1.id = fk(e0.customer)) is null"
    + " and e0.customer.id is not null", byName.toHql().hql())
```

**データベースごとの SQL**

<!-- sql: delete -->
```sql
-- H2, Oracle
delete from orders o1_0
where o1_0.status=3

-- MariaDB
delete o1_0 from orders o1_0
where o1_0.status=3
```

**データベースごとの SQL**

<!-- sql: delete#2 -->
```sql
-- H2, Oracle
delete from orders o1_0
where (select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id) is null and o1_0.customer_id is not null

-- MariaDB
delete from orders
where (select c1_0.name from customers c1_0 where c1_0.id=orders.customer_id) is null and customer_id is not null
```

MariaDB は `delete o1_0 from orders o1_0` と書きます。H2 と Oracle は `delete from orders o1_0` と書きます。

2 つ目の文は、`qb.from(Order::class.java).where { it.customer!!.name == null }` が返すのと **同じ行**、つまり顧客の名前がない注文を削除します。
顧客の **ない** 注文は削除されません。`!!` を通った比較は、顧客のある注文でだけ真になるからです。

`?.` で書くと（`it.customer?.name == null`）、顧客のない注文も条件に一致するので選択は `left join` になり、DML は `e0.customer.id is not null` を加えません。そのため、顧客のない注文も削除されます。
<!-- region: ManualKotlinDmlTest.kt#delete-safe-call -->
```kotlin
// ?. : no "customer exists" check, so orders without a customer are deleted too, as the select returns them
val d = qb.delete(Order::class.java).where { it.customer?.name == null }
assertEquals("delete from Order e0 where (select s1.name from Customer s1 where s1.id = fk(e0.customer)) is null",
    d.toHql().hql())
```

同じ方法の UPDATE の例は [7.2](07-2-update.md) を参照してください。顧客のない注文だけを削除するには、`it.customer == null` と書いてください。

すべての行を削除するときは `all()` が必要です。
id のリストで削除するときは、キャプチャしたコレクションを使います。

<!-- region: ManualKotlinDmlTest.kt#delete-all -->
```kotlin
// ids in a captured collection
val byIds = qb.delete(Item::class.java).where { it.id in ids }
assertEquals("delete from Item e0 where e0.id in (:p1)", byIds.toHql().hql())
byIds.execute()
// every row: all() is required
val all = qb.delete(Order::class.java).all()
assertEquals("delete from Order e0", all.toHql().hql())
all.execute()
```

`where` の中のサブクエリは、[5. サブクエリ](05-subquery.md) と同じく 2 つ目のラムダ式の引数 `sub` を使います。
品目のない注文を削除する例:

<!-- region: ManualKotlinDmlTest.kt#delete-not-exists -->
```kotlin
// orders without items: the subquery starts with the second lambda parameter sub
val d = qb.delete(Order::class.java)
    .where { o, sub -> !sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
assertEquals("delete from Order e0 where not exists (select 1 from Item s1 where s1.order.id = e0.id)", d.toHql().hql())
val deleted = d.execute()
```

**データベースごとの SQL**

<!-- sql: dml-subquery -->
```sql
-- H2, Oracle
delete from orders o1_0
where not exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id)

-- MariaDB
delete from orders
where not exists(select 1 from items i1_0 where i1_0.order_id=orders.id)
```

すべての JPA の一括の文と同じく、delete は JPA の `cascade` や `orphanRemoval` を実行せず、すでに読み込まれたエンティティにも反映されません。
delete にはトランザクションが必要です（[7.8](07-dml.md#transactions) を参照）。

コード例: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`delete`、`delete-safe-call`、`delete-all`、`delete-not-exists`）。

<a id="delete-with-a-subquery-on-the-same-table"></a>
## 同じテーブルを読むサブクエリ

`where` のサブクエリが、削除の対象のエンティティ（または同じ継承階層のエンティティ）を読むことがあります。サブクエリ自身の `from`、サブクエリの中の結合、U9 の相関スカラーサブクエリがこれにあたります。
このとき lambdaQL は、まず対象の id を派生テーブルで読み、その id で削除します。

```
delete from Order d0 where d0.id in (select t.id from (select e0.id as id from Order e0 where <condition>) t)
```

- 理由: サブクエリが対象のテーブルを読むと、MariaDB は Hibernate の `delete t from …` を拒否します（エラー 1093、"specified twice"）。
- 相関サブクエリにも同じ形が動きます。サブクエリだけを包む方法は、相関サブクエリには使えません。
- H2、MariaDB 10.11、Oracle 23.26 で実測しました（非相関の `avg`、相関の `exists`、`max`、サブクエリの中の結合）。3 つの DB で削除した行数が同じで、MariaDB も失敗しません。
- 別のテーブルを読むサブクエリは、これまでの形のままです（上の `not exists` の例）。
- `update` は包みません。自分のテーブルを読むサブクエリのある `update` は、3 つの DB すべてで動きます（MariaDB 10.11 で実測）。
- 複合 id のエンティティは包みません。`limit` のある delete は、もともと下の包んだ形（別名 `x0`）を使います。

<!-- region: KotlinQueryTest.kt#delete-self-subquery -->
```kotlin
// the subquery reads the target table: the matching ids are read through a derived table first (U17, MariaDB 1093)
val d = qb.delete(Order::class.java).where { o, sub -> o.price < sub.from(Order::class.java).avg { x -> x.price } }
assertEquals("delete from Order d0 where d0.id in (select t.id from (select e0.id as id from Order e0"
        + " where e0.price < (select avg(s1.price) from Order s1)) t)", d.toHql().hql())
```

コード例: [`KotlinQueryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)（`delete-self-subquery`）。

<a id="delete-the-first-rows-limit"></a>
## 先頭の行を削除する: `limit`

`where { ... }` または `all()` の後に `orderBy { ... }` と `limit(n)` を付けると、多くても `n` 行を削除します。対象は `qb.from(X::class.java).where { same }.orderBy { same }` の先頭の `n` 行です。
多くの行を小さな単位に分けて処理するときに使います。

- `orderBy` がなければ id の順です。
- lambdaQL は id を最後のソートキーとして追加するので、順序は常に同じです（[タイブレーカー](04-order-paging.md#tie-breaker-automatic-id) を参照）。
- `orderBy` には、選択と同じくソートのラムダ式か `SortSpec` を指定します（[4. ソートとページング](04-order-paging.md) を参照）。
- `limit` のない `orderBy` はエラー（`IllegalStateException`）です。この場合、順序が何の役にも立たないからです。

<!-- region: ManualKotlinDmlTest.kt#delete-limit -->
```kotlin
// the first rows of qb.from(Order::class.java).where { same }.orderBy { same }; the id is the last sort key
val d = qb.delete(Order::class.java)
    .where { it.status.toInt() == 1 }
    .orderBy { o -> asc(o.price) }
    .limit(1)
assertEquals("delete from Order e0 where e0.id in (select t.id from (select x0.id as id from Order x0"
    + " where x0.status = 1 order by x0.price asc, x0.id asc limit 1) t)", d.toHql().hql())
val deleted = d.execute()
```

**データベースごとの SQL**

<!-- sql: delete-limit -->
```sql
-- H2
delete from orders o1_0
where o1_0.id in (select t1_0.c0 from (select o2_0.id from orders o2_0 where o2_0.status=1 order by o2_0.price,o2_0.id fetch first 1 rows only) t1_0(c0))

-- MariaDB
delete o1_0 from orders o1_0
where o1_0.id in (select * from (select t1_0.c0 from (select * from (select o2_0.id c0 from orders o2_0 where o2_0.status=1 order by o2_0.price,o2_0.id limit 1) _sub_) t1_0) _sub_)

-- Oracle
delete from orders o1_0
where o1_0.id in (select t1_0.c0 from (select o2_0.id c0 from orders o2_0 where o2_0.status=1 order by o2_0.price,o2_0.id fetch first 1 rows only) t1_0)
```

lambdaQL は limit を `e0.id in (select t.id from (select x0.id as id from X x0 ... order by ... limit n) t)` として書きます。

- 内側の select は、結合を含めて選択のルールに従います。そのため、この文は選択が返す行とまったく同じ行を削除します。
- 派生テーブル `t` は MariaDB のためのものです。派生テーブルがないと、MariaDB は `in` サブクエリの `limit`（エラー 1235）と、対象のテーブルを読むサブクエリ（エラー 1093）を拒否します。
- H2 と Oracle は `fetch first 1 rows only`、MariaDB は `limit 1` と書きます。

コード例: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`delete-limit`）。

<a id="delete-exactly-one-row-executeone"></a>
## ちょうど 1 行を削除する: `executeOne()`

`executeOne()` は **1 行保護** を適用して delete を実行します。条件が 1 行に合うはずの場合（例: ユニークキー）に、`execute()` の代わりに使ってください。

1. lambdaQL が、条件に合う行の id を最大 2 つ読みます。
2. id がなければ、何も変更せずに 0 を返します。
3. id が 2 つなら、何も変更せずに JPA の `NonUniqueResultException` を投げます。
4. id が 1 つなら、`id = :p and (条件)` でその行を削除し、1 を返します。その間にその行が条件から外れていたら、0 を返します。

文に id を 1 つ指定するので、2 行以上を変更することはありません。この保護はロールバックに頼りません。

<!-- region: ManualKotlinDmlTest.kt#execute-one -->
```kotlin
// one row matches: it is deleted
val one = qb.delete(Order::class.java).where { it.id == id }.executeOne()
// two or more rows match: nothing is deleted
assertThrows(NonUniqueResultException::class.java) {
    qb.delete(Order::class.java).where { it.price > 100.0 }.executeOne()
}
```

2 つ目の文は、価格が 100 より大きい注文が 2 つ以上あるので、何も削除しません。
id は NULL になれないので、`it.id == id` は単純な `e0.id = :p1` になります（[3.6.2](03-where.md#362--compares-values) を参照）。

- `executeOne()` を `orderBy` や `limit` と一緒に使うとエラー（`IllegalStateException`）です。
- `limit` と `executeOne()` は、まだ複合キーのエンティティをサポートしていません。
- Spring のリポジトリーでは `deleteOne { ... }` が同じ動作をし、`IncorrectResultSizeDataAccessException` を投げます（[12.4](12-spring-data-jpa.md#one-row-protection-deleteone-and-updateone) を参照）。

---

[7. 更新、挿入、削除（Kotlin）](07-dml.md) | [7.2 更新](07-2-update.md) | **7.4 削除** | [7.5 挿入](07-5-insert.md) | [7.6 Upsert](07-6-upsert.md) | [Java](../java/07-4-delete.md)

コード例: [`ManualKotlinDmlTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinDmlTest.kt)（`execute-one`）。
