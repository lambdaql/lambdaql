<a id="74-delete"></a>
# 7.4 削除（Java）

> [!NOTE]
> **状況: 実装済み、テスト済み**（H2 2.3、MariaDB 10.11、Oracle 23.26、マイルストーン M10）。`limit` と `executeOne()` も含みます（決定 D41）。
> `where` の中のサブクエリ（`sub`）も実装済み、テスト済みです（マイルストーン M8）。
> **まだ実装されていません:** 複合キーのエンティティでの `limit` / `executeOne()`。
> **(テスト済み)** と書かれた例は [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) と [`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java) からコピーしたものです。それ以外の例は設計に従っており、まだテストで確認していません。
> 全体の状況は [Java トラックの README](README.md#status) を参照してください。

[7. 更新、挿入、削除（Java）](07-dml.md) | [7.2 更新](07-2-update.md) | **7.4 削除** | [7.5 挿入](07-5-insert.md) | [7.6 Upsert](07-6-upsert.md) | [Kotlin](../kotlin/07-4-delete.md)

---


**(テスト済み)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)、リージョン `delete`:

```java
ExecutableDelete<Order> d = qb.delete(Order.class).where(o -> o.getStatus() == 3);
assertEquals("delete from Order e0 where e0.status = 3", d.toHql().hql());
int deleted = d.execute();
// a path: the same rows as qb.from(Order.class).where(same lambda) - orders without a customer stay
ExecutableDelete<Order> byName = qb.delete(Order.class).where(o -> o.getCustomer().getName() == null);
assertEquals("delete from Order e0 where (select s1.name from Customer s1 where s1.id = e0.customer.id) is null"
        + " and e0.customer.id is not null", byName.toHql().hql());
```

**SQL ブロックの読み方。** `…` は省略した select リスト（エンティティのすべてのカラム）で、`?` はバインドパラメーターです。各行は [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java) から取っています。このテストは各クエリを Hibernate 6.6.5 で H2 2.3、MariaDB 10.11、Oracle 23.26 に対して実行し、Hibernate の SQL が変わると失敗します。

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `delete`:

```sql
-- H2, Oracle
delete from orders o1_0
where o1_0.status=3

-- MariaDB
delete o1_0 from orders o1_0
where o1_0.status=3
```

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `delete#2`:

```sql
-- H2, Oracle
delete from orders o1_0
where (select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id) is null and o1_0.customer_id is not null

-- MariaDB
delete o1_0 from orders o1_0
where (select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id) is null and o1_0.customer_id is not null
```

MariaDB は `delete o1_0 from orders o1_0` と書きます。H2 と Oracle は `delete from orders o1_0` と書きます。

2 つ目の文は、`qb.from(Order.class).where(o -> o.getCustomer().getName() == null)` が返すのと **同じ行** を削除します。つまり、顧客に名前がない注文です。テストでは、削除した行をこの選択の結果と比べています。
顧客の **ない** 注文は削除されません（ルール U9、決定 D36）。それらも削除するには、`o.getCustomer() == null || o.getCustomer().getName() == null` と書いてください。この null ガードがあると、lambdaQL は `e0.customer.id is not null` を加えません。

すべての行を削除するには `all()` が必要です（ルール U5）。

```java
qb.delete(Order.class).all().execute();     // delete all rows
qb.delete(Order.class).execute();           // compile error: no execute()
```

id のリストで削除する（キャプチャしたコレクション、決定 D29）:

```java
qb.delete(Item.class).where(i -> ids.contains(i.getId())).execute();
```

`where` の中のサブクエリは、[5. サブクエリ](05-subquery.md) と同じように 2 つ目のラムダ引数 `sub` を使います。
品目を持たない注文を削除します。

**(テスト済み)** [`SubqueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java)、リージョン `dml-subquery`:

```java
ExecutableDelete<Order> d = qb.delete(Order.class)
        .where((o, sub) -> !sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).exists());
assertEquals("delete from Order e0 where not exists (select 1 from Item s1 where s1.order.id = e0.id)", d.toHql().hql());
```

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `dml-subquery`:

```sql
-- H2, Oracle
delete from orders o1_0
where not exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id)

-- MariaDB
delete o1_0 from orders o1_0
where not exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id)
```

すべての JPA の一括文と同じように、削除は JPA の `cascade` や `orphanRemoval` を実行せず、すでに読み込まれたエンティティも変更しません（ルール U16）。
削除にはトランザクションが必要です（[7.8](07-dml.md#transactions) を参照）。

<a id="delete-the-first-rows-limit"></a>
## 先頭の行を削除する: `limit`

`where(...)` または `all()` の後に `orderBy(...)` と `limit(n)` を付けると、最大 `n` 行を削除します。対象は `qb.from(X.class).where(同じ).orderBy(同じ)` の先頭 `n` 行です。
多くの行を少しずつ処理するときに使います。

- `orderBy` がない場合、順序は id 順です。
- lambdaQL は id を最後のソートキーとして加えるので、順序は常に同じです（ルール S9、[タイブレーカー](04-order-paging.md#tie-breaker-automatic-id) を参照）。
- `orderBy` には、select と同じようにソートのラムダか `SortSpec` を渡します（[4. ソートとページング](04-order-paging.md) を参照）。
- `limit` のない `orderBy` はエラーです（`IllegalStateException`）。その場合、順序に意味がないからです。

**(テスト済み)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)、リージョン `delete-limit`:

```java
// the first rows of qb.from(Order.class).where(same).orderBy(same); the id is the last sort key
ExecutableDelete<Order> d = qb.delete(Order.class)
        .where(o -> o.getStatus() == 1)
        .orderBy((o, by) -> by.asc(o.getPrice()))
        .limit(1);
assertEquals("delete from Order e0 where e0.id in (select t.id from (select x0.id as id from Order x0"
        + " where x0.status = 1 order by x0.price asc, x0.id asc limit 1) t)", d.toHql().hql());
int deleted = d.execute();
```

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `delete-limit`:

```sql
-- H2
delete from orders o1_0
where o1_0.id in (select t1_0.id from (select o2_0.id from orders o2_0 where o2_0.status=1 order by o2_0.price,o2_0.id fetch first 1 rows only) t1_0(id))

-- MariaDB
delete o1_0 from orders o1_0
where o1_0.id in (select t1_0.id from (select o2_0.id id from orders o2_0 where o2_0.status=1 order by o2_0.price,o2_0.id limit 1) t1_0)

-- Oracle
delete from orders o1_0
where o1_0.id in (select t1_0.id from (select o2_0.id id from orders o2_0 where o2_0.status=1 order by o2_0.price,o2_0.id fetch first 1 rows only) t1_0)
```

lambdaQL は limit を `e0.id in (select t.id from (select x0.id as id from X x0 ... order by ... limit n) t)` と書きます。

- 内側の select は、結合も含めて select のルールに従います（決定 D36）。そのため、この文は select が返す行とちょうど同じ行を削除します。
- 派生テーブル `t` は MariaDB のためのものです。これがないと、MariaDB は `in` サブクエリの中の `limit`（エラー 1235）と、対象テーブルを読むサブクエリ（エラー 1093）を拒否します。
- H2 と Oracle は `fetch first 1 rows only`、MariaDB は `limit 1` と書きます。

<a id="delete-exactly-one-row-executeone"></a>
## ちょうど 1 行を削除する: `executeOne()`

`executeOne()` は **1 行保護** 付きで削除を実行します。条件が 1 行だけに一致するはずのとき（たとえば一意キー）、`execute()` の代わりに使います。

1. lambdaQL は一致する行の id を最大 2 つ読みます。
2. id がない: 何も変更しません。結果は 0 です。
3. id が 2 つ: 何も変更しません。`executeOne()` は JPA の `NonUniqueResultException` を投げます。
4. id が 1 つ: lambdaQL はその行を `id = :p and (あなたの条件)` で削除します。結果は 1 です。その間に行が条件に一致しなくなった場合は 0 です。

文は 1 つの id を指定するので、2 行以上を変更することはありません。この保護はロールバックに頼りません。

**(テスト済み)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)、リージョン `execute-one`:

```java
// one row matches: it is deleted
int one = qb.delete(Order.class).where(o -> o.getId() == id).executeOne();
// two or more rows match: nothing is deleted
assertThrows(jakarta.persistence.NonUniqueResultException.class,
        () -> qb.delete(Order.class).where(o -> o.getPrice() > 100).executeOne());
```

- `executeOne()` を `orderBy` や `limit` と一緒に使うとエラーです（`IllegalStateException`）。
- `limit` と `executeOne()` は、複合キーのエンティティにはまだ対応していません。
- Spring のリポジトリでは、`deleteOne(where)` が同じことをし、`IncorrectResultSizeDataAccessException` を投げます（[12.4](12-spring-data-jpa.md#one-row-protection-deleteone-and-updateone) を参照）。

---

---

[7. 更新、挿入、削除（Java）](07-dml.md) | [7.2 更新](07-2-update.md) | **7.4 削除** | [7.5 挿入](07-5-insert.md) | [7.6 Upsert](07-6-upsert.md) | [Kotlin](../kotlin/07-4-delete.md)
