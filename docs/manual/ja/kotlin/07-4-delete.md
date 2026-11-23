<a id="74-delete"></a>
# 7.4 削除（Kotlin）

[7. 更新、挿入、削除（Kotlin）](07-dml.md) | [7.2 更新](07-2-update.md) | **7.4 削除** | [7.5 挿入](07-5-insert.md) | [7.6 Upsert](07-6-upsert.md) | [Java](../java/07-4-delete.md)

---


```kotlin
val d = qb.delete(Order::class.java).where { it.status == 3 }
// HQL: delete from Order e0 where e0.status = 3
val deleted = d.execute()

// a path: the same rows as qb.from(Order::class.java).where { same lambda } - with !! orders without a customer stay
val byName = qb.delete(Order::class.java).where { it.customer!!.name == null }
// HQL: delete from Order e0 where (select s1.name from Customer s1 where s1.id = fk(e0.customer)) is null and e0.customer.id is not null
```

**SQL ブロックの読み方。** `…` は省略した select リスト（エンティティのすべてのカラム）で、`?` はバインドパラメーターです。各行は [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java) から取っています。このテストは各クエリを Hibernate 7.4 で H2 2.3、MariaDB 10.11、Oracle 23.26 に対して実行し、Hibernate の SQL が変わると失敗します。このテストは Java のラムダを使います。意味が同じ Kotlin のラムダは同じ HQL になるので、SQL も同じです。

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `delete`:

```sql
-- H2, Oracle
delete from orders o1_0
where o1_0.status=3

-- MariaDB
delete o1_0 from orders o1_0
where o1_0.status=3
```

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `delete#2`:

```sql
-- H2, Oracle
delete from orders o1_0
where (select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id) is null and o1_0.customer_id is not null

-- MariaDB
delete from orders
where (select c1_0.name from customers c1_0 where c1_0.id=orders.customer_id) is null and customer_id is not null
```

MariaDB は `delete o1_0 from orders o1_0` と書きます。H2 と Oracle は `delete from orders o1_0` と書きます。

[`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)、リージョン `insert-delete`:

```kotlin
val i = qb.insert(Archive::class.java).values { it.id = 100L; it.price = 9.5; it.customerName = "Kim" }
assertEquals(1, i.execute())
val d = qb.delete(Order::class.java).where { o, sub -> !sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
assertEquals("delete from Order e0 where not exists (select 1 from Item s1 where s1.order.id = e0.id)", d.toHql().hql())
```

テストはまず `Archive` の行を 1 つ挿入し、品目のない注文を削除した後、残った行を Kotlin で同じ条件を適用した結果と比べます。
Java で書いたほかの文は [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) でテストしています（H2、MariaDB、Oracle）。それらの例は [このページの Java 版](../java/07-4-delete.md) を参照してください。

2 つ目の文は、`qb.from(Order::class.java).where { it.customer!!.name == null }` が返すのと **同じ行**、つまり顧客に名前がない注文を削除します。Java のテストでは、同じ条件を Java で書き、削除した行を選択の結果と比べています。
顧客の **いない** 注文は削除されません（ルール U9、決定 D36）。`!!` は関連が必ずあるという宣言だからです。

`?.` で書くと（`it.customer?.name == null`）、選択と同じく `left join` と同じ意味になり、`e0.customer.id is not null` を加えません（決定 D54）。そのため、顧客のいない注文も削除されます。
同じ方式の UPDATE の例は [7.2](07-2-update.md) のリージョン `update-safe-call` を参照してください。顧客のいない注文だけを削除するには、`it.customer == null` と書いてください。

すべての行を削除するには `all()` が必要です（ルール U5）。

```kotlin
qb.delete(Order::class.java).all().execute()     // delete all rows
qb.delete(Order::class.java).execute()           // compile error: no execute()
```

id のリストで削除する（キャプチャしたコレクション、決定 D29）:

```kotlin
qb.delete(Item::class.java).where { it.id in ids }.execute()
```

`where` の中のサブクエリは、[5. サブクエリ](05-subquery.md) と同じように 2 つ目のラムダ引数 `sub` を使います。
品目を持たない注文を削除する例:

```kotlin
qb.delete(Order::class.java)
    .where { o, sub -> !sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
    .execute()
// HQL: delete from Order e0 where not exists (select 1 from Item s1 where s1.order.id = e0.id)
```

同じ文を Java で書いたテストが [`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java) にあります（リージョン `dml-subquery`）。

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `dml-subquery`:

```sql
-- H2, Oracle
delete from orders o1_0
where not exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id)

-- MariaDB
delete from orders
where not exists(select 1 from items i1_0 where i1_0.order_id=orders.id)
```

すべての JPA の一括文と同じように、削除は JPA の `cascade` や `orphanRemoval` を実行せず、すでに読み込まれたエンティティにも反映されません（ルール U16）。
削除にはトランザクションが必要です（[7.8](07-dml.md#transactions) を参照）。

<a id="delete-with-a-subquery-on-the-same-table"></a>
## 同じテーブルを読むサブクエリ

`where` のサブクエリは、削除対象のエンティティ（または同じ継承階層のエンティティ）を読むことができます。サブクエリ自身の `from`、サブクエリの中の結合、U9 の相関スカラーサブクエリがこれに当たります。
このとき lambdaQL は、まず対象の id を派生テーブルとして読み、その id で削除します（ルール U17）。

```
delete from Order d0 where d0.id in (select t.id from (select e0.id as id from Order e0 where <condition>) t)
```

- 理由: サブクエリが対象テーブルを読むと、MariaDB は Hibernate の `delete t from …` を拒否します（エラー 1093、"specified twice"）。
- 相関サブクエリでも同じ形が動きます。サブクエリだけを囲む方法は、相関サブクエリには使えません。
- H2、MariaDB 10.11、Oracle 23.26 で実測しました（非相関の `avg`、相関の `exists`、`max`、サブクエリの中の結合）。3 つの DB で削除した行数は同じで、MariaDB も失敗しません。
- 別のテーブルを読むサブクエリは、これまでの形のままです（上の `not exists` の例）。
- `update` は囲みません。自分のテーブルを読むサブクエリを含む `update` は、3 つの DB すべてで動きます（MariaDB 10.11 で実測）。
- 複合 id のエンティティは囲みません。`limit` 付きの削除は、もともと下の囲んだ形（エイリアス `x0`）を使います。

[`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)、リージョン `delete-self-subquery`:

```kotlin
// the subquery reads the target table: the matching ids are read through a derived table first (U17, MariaDB 1093)
val d = qb.delete(Order::class.java).where { o, sub -> o.price < sub.from(Order::class.java).avg { x -> x.price } }
assertEquals("delete from Order d0 where d0.id in (select t.id from (select e0.id as id from Order e0"
        + " where e0.price < (select avg(s1.price) from Order s1)) t)", d.toHql().hql())
```

<a id="delete-the-first-rows-limit"></a>
## 先頭の行を削除する: `limit`

`where { ... }` または `all()` の後に `orderBy { ... }` と `limit(n)` を付けると、最大 `n` 行を削除します。対象は `qb.from(X::class.java).where { 同じ }.orderBy { 同じ }` の先頭 `n` 行です。
多くの行を少しずつ処理するときに使います。

- `orderBy` がない場合、順序は id 順です。
- lambdaQL は id を最後のソートキーとして加えるので、順序は常に同じです（ルール S9、[タイブレーカー](04-order-paging.md#tie-breaker-automatic-id) を参照）。
- `orderBy` には、select と同じようにソートのラムダか `SortSpec` を渡します（[4. ソートとページング](04-order-paging.md) を参照）。
- `limit` のない `orderBy` はエラーです（`IllegalStateException`）。その場合、順序に意味がないからです。

```kotlin
// the first rows of qb.from(Order::class.java).where { same }.orderBy { same }; the id is the last sort key
val d = qb.delete(Order::class.java)
    .where { it.status == 1 }
    .orderBy { o, by -> by.asc(o.price) }
    .limit(1)
// HQL: delete from Order e0 where e0.id in (select t.id from (select x0.id as id from Order x0 where x0.status = 1 order by x0.price asc, x0.id asc limit 1) t)
val deleted = d.execute()
```

同じ文を Java で書いたテストが [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) にあります（リージョン `delete-limit`）。

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `delete-limit`:

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

lambdaQL は limit を `e0.id in (select t.id from (select x0.id as id from X x0 ... order by ... limit n) t)` と書きます。

- 内側の select は、結合も含めて select のルールに従います（決定 D36）。そのため、この文は select が返す行とちょうど同じ行を削除します。
- 派生テーブル `t` は MariaDB のためのものです。これがないと、MariaDB は `in` サブクエリの中の `limit`（エラー 1235）と、対象テーブルを読むサブクエリ（エラー 1093）を拒否します。
- H2 と Oracle は `fetch first 1 rows only`、MariaDB は `limit 1` と書きます。

<a id="delete-exactly-one-row-executeone"></a>
## ちょうど 1 行を削除する: `executeOne()`

`executeOne()` は **1 行保護** 付きで削除を実行します。条件が 1 行だけに一致するはずのとき（たとえば一意キー）、`execute()` の代わりに使います。

1. lambdaQL は条件に一致する行の id を最大 2 つ読みます。
2. id がなければ、何も変更せず 0 を返します。
3. id が 2 つなら、何も変更せず JPA の `NonUniqueResultException` を投げます。
4. id が 1 つなら、その行を `id = :p and (条件)` で削除し、1 を返します。その間に行が条件に一致しなくなった場合は 0 を返します。

文は 1 つの id を指定するので、2 行以上を変更することはありません。この保護はロールバックに頼りません。

```kotlin
// one row matches: it is deleted
val one = qb.delete(Order::class.java).where { it.id == id }.executeOne()
// two or more rows match: nothing is deleted, NonUniqueResultException
qb.delete(Order::class.java).where { it.price!! > 100.0 }.executeOne()
```

同じ文を Java で書いたテストが [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) にあります（リージョン `execute-one`）。id は NULL になれないので、`it.id == id` は単純な `e0.id = :p1` になります（[3.6.2](03-where.md#362--compares-values) を参照）。

- `executeOne()` を `orderBy` や `limit` と一緒に使うとエラーです（`IllegalStateException`）。
- `limit` と `executeOne()` は、複合キーのエンティティにはまだ対応していません。
- Spring のリポジトリでは、`deleteOne { ... }` が同じ動作をし、`IncorrectResultSizeDataAccessException` を投げます（[12.4](12-spring-data-jpa.md#one-row-protection-deleteone-and-updateone) を参照）。

---

---

[7. 更新、挿入、削除（Kotlin）](07-dml.md) | [7.2 更新](07-2-update.md) | **7.4 削除** | [7.5 挿入](07-5-insert.md) | [7.6 Upsert](07-6-upsert.md) | [Java](../java/07-4-delete.md)
