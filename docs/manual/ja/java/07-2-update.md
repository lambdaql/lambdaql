<a id="72-update"></a>
# 7.2 更新（Java）

[7. 更新、挿入、削除（Java）](07-dml.md) | **7.2 更新** | [7.4 削除](07-4-delete.md) | [7.5 挿入](07-5-insert.md) | [7.6 Upsert](07-6-upsert.md) | [Kotlin](../kotlin/07-2-update.md)

---


<a id="basic-form"></a>
## 基本形

**(テスト済み)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)、リージョン `update`:

```java
short st = 5;
ExecutableUpdate<Order> u = qb.update(Order.class)
        .set(o -> { o.setStatus(st); o.setPrice(o.getPrice() * 2); })
        .where(o -> o.getStatus() == 1);
// the price on the right is the value before the change
assertEquals("update Order e0 set e0.status = :p1, e0.price = (e0.price * 2.0) where e0.status = 1", u.toHql().hql());
int changed = u.execute();
```

`st` はキャプチャした変数なので、バインドパラメーター（`:p1`）になります。定数 `2` はラムダの中に書かれているので、リテラル（`2.0`）のままです（決定 D37）。
`toHql()` は文を実行せずに HQL とパラメーターを返します。`execute()` は文を実行し、変更した行数を返します。

**SQL ブロックの読み方。** `…` は省略した select リスト（エンティティのすべてのカラム）で、`?` はバインドパラメーターです。各行は [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java) から取っています。このテストは各クエリを Hibernate 7.4 で H2 2.3、MariaDB 10.11、Oracle 23.26 に対して実行し、Hibernate の SQL が変わると失敗します。

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `update`:

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

<a id="embedded-fields"></a>
## 埋め込みフィールド

```java
qb.update(Order.class).set(o -> o.getAddress().setCity(city)).where(o -> o.getId() == id).execute();
// → set o.address.city = :p1

qb.update(Order.class).set(o -> o.setAddress(addr)).where(o -> o.getId() == id).execute();
// → sets all columns of the embedded value (city, zip)
```

<a id="change-an-association-foreign-key"></a>
## 関連（外部キー）を変更する

```java
qb.update(Order.class).set(o -> o.getCustomer().setId(cid)).where(o -> o.getId() == id).execute();
// → set o.customer.id = :p1      (the FK column)

qb.update(Order.class).set(o -> o.setCustomer(null)).where(o -> o.getId() == id).execute();
// → set o.customer = null

qb.update(Order.class).set(o -> o.setCustomer(customer)).where(o -> o.getId() == id).execute();
// → binds the @Id of the captured customer (rule U11)
```

関連エンティティに設定できるのは `@Id`（FK）だけです。
`o.getCustomer().setName(x)` はエラーです。別のテーブルを変更してしまうからです（ルール U6、[7.8](07-dml.md#rules-checked-at-analysis-time) でテスト済み）。

<a id="a-value-from-another-table"></a>
## 別のテーブルの値

関連のパスを通して値を読みます。lambdaQL はこれを **相関スカラーサブクエリ** として出力します（ルール U9）。
`where` の中のパスも、同じ種類のサブクエリになります。

**(テスト済み)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)、リージョン `update-path`:

```java
// a path in the condition: a subquery, plus "customer exists" (the same rows as the select, D36, U9)
ExecutableUpdate<Order> u = qb.update(Order.class)
        .set(o -> o.setCustName("K"))
        .where(o -> o.getCustomer().getName().equals("Kim"));
assertEquals("update Order e0 set e0.custName = 'K' where (select s1.name from Customer s1 where s1.id = fk(e0.customer)) = 'Kim'"
        + " and e0.customer.id is not null", u.toHql().hql());
// a path in a value: a subquery; NULL when there is no customer
ExecutableUpdate<Order> copy = qb.update(Order.class).set(o -> o.setCustName(o.getCustomer().getName())).all();
assertEquals("update Order e0 set e0.custName = (select s1.name from Customer s1 where s1.id = fk(e0.customer))", copy.toHql().hql());
```

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `update-path`:

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

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `update-path#2`:

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

注文に顧客がない場合、`custName` は NULL になります。
`where` では lambdaQL が `e0.customer.id is not null` を加えます。そのため、更新の対象行は `qb.from(Order.class).where(同じラムダ)` の結果と同じです（決定 D36）。テストでこれを確認しています。

<a id="an-aggregate-from-a-subquery"></a>
## サブクエリの集約

引数が 2 つのラムダ `set((o, sub) -> ...)` を使います。2 番目の引数はサブクエリの起点 `sub` です（決定 D49）。
ここでは `Agg` は必要ありません。サブクエリには [5. サブクエリ](05-subquery.md) と同じように `sum`、`avg`、... があります。

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

- 別のテーブルの集約も、同じテーブルの集約も使えます（H2、MariaDB 10.11、Oracle 23.26 で実測）。`update` では MariaDB もエラー 1093 を出しません。
- 一致する行がない場合、集約は NULL です。NOT NULL カラムでは DB エラーになるので、`Objects.requireNonNullElse(…, 0.0)` で囲んで `coalesce(...)` にしてください。

<a id="where-with-paths-and-subqueries"></a>
## パスとサブクエリを使う where

`where` は選択クエリと同じです。

```java
qb.update(Order.class).set(o -> o.setStatus(0)).where(o -> o.getCustomer().getGrade() > 3).execute();

qb.update(Order.class).set(o -> o.setStatus(9))
  .where((o, sub) -> !sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).exists())
  .execute();
```

どちらの例も現在動きます。
1 つ目の例では、上のテスト済みの例と同じように、パスはスカラーサブクエリになります。
2 つ目の例は [5. サブクエリ](05-subquery.md) と同じように `sub` を使います。その HQL は `update Order e0 set e0.status = 9 where not exists (select 1 from Item s1 where s1.order.id = e0.id)` です。
同じ `where` を削除で使う形はテスト済みです（リージョン `dml-subquery`、[7.4](07-4-delete.md) を参照）。更新の形は、まだ専用のテストでカバーされていません。

**データベースごとの SQL（この HQL に対する Hibernate の SQL）** — 同じ文を Hibernate に直接渡す [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java) が記録しました。`expected-sql/` の [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)、例 `07-update-not-exists`:

```sql
-- H2
update orders o1_0
set status=9
where not exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id)

-- MariaDB
update orders
set status=9
where not exists(select 1 from items i1_0 where i1_0.order_id=orders.id)

-- Oracle
update orders o1_0
set o1_0.status=9
where not exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id)
```

<a id="optimistic-locking-version"></a>
## 楽観的ロック（`@Version`）

エンティティに `@Version` フィールドがある場合、lambdaQL は `update versioned` を出力します。データベースがバージョンに 1 を加えます。

**(テスト済み)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)、リージョン `update-versioned`:

```java
// an entity with a version field (Version annotation): the version goes up too
ExecutableUpdate<VOrder> u = qb.update(VOrder.class).set(v -> v.setStatus(2)).where(v -> v.getId() == 1L);
assertEquals("update versioned VOrder e0 set e0.status = 2 where e0.id = 1L", u.toHql().hql());
```

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `update-versioned`:

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

```java
int n = qb.update(VOrder.class)
    .set(o -> o.setStatus(8))
    .where(o -> o.getId() == id && o.getVersion() == v)
    .execute();
if (n == 0) throw new OptimisticLockException();   // checking the result is your job
```

`@Version` フィールドに直接代入しないでください（ルール U15）。

<a id="many-set-calls-patch"></a>
## `set()` を何回も呼ぶ（PATCH）

`set()` は何回でも呼べます。すべての代入は、呼び出した順に 1 つの SET リストにまとめられます（ルール U14）。
ルール U3 と U4 は、`set()` 呼び出しをまたいでも適用されます。

**(テスト済み)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)、リージョン `update-patch`:

```java
// PATCH: assign only the fields that have a value; every set() adds assignments
String memo = "checked";
Short newStatus = null;
UpdateSet<Order> u = qb.update(Order.class).set(o -> o.setDescription(memo));
if (newStatus != null) u = u.set(o -> o.setStatus(newStatus));
int changed = u.where(o -> o.getId() == id).execute();

// reading a column that an earlier assignment wrote is an error (U3): the databases disagree on its value
assertThrows(LambdaQLException.class,
        () -> qb.update(Order.class).set(o -> { o.setPrice(1); o.setTex((float) o.getPrice()); }));
// the same column twice is an error (U4), also across set() calls
assertThrows(LambdaQLException.class,
        () -> qb.update(Order.class).set(o -> o.setStatus((short) 1)).set(o -> o.setStatus((short) 2)));
```

`set()` は `UpdateSet` を返します。この型には `set`、`where`、`all` があり、`execute()` はありません。

<a id="update-all-rows-all"></a>
## すべての行を更新する: `all()`

`where` のない更新には `all()` が必要です。`where` も `all()` もない場合、コードは **コンパイルできません**（ルール U5）。

```java
qb.update(Order.class).set(o -> o.setStatus(0)).all().execute();       // OK
qb.update(Order.class).set(o -> o.setStatus(0)).execute();             // compile error: no execute()
```

更新にはトランザクションが必要です（[7.8](07-dml.md#transactions) を参照）。

<a id="update-the-first-rows-limit"></a>
## 先頭の行を更新する: `limit`

`where(...)` または `all()` の後に `orderBy(...)` と `limit(n)` を付けると、最大 `n` 行を変更します。対象は `qb.from(X.class).where(同じ).orderBy(同じ)` の先頭 `n` 行です。

ルールは削除と同じです（[7.4](07-4-delete.md#delete-the-first-rows-limit) を参照）。`orderBy` がなければ id 順、id は常に最後のソートキー、`limit` のない `orderBy` はエラーです。

**(テスト済み)** [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)、リージョン `update-limit`:

```java
// a path in the condition: the nested select joins, so the rows are the same as the select
ExecutableUpdate<Order> u = qb.update(Order.class)
        .set(o -> o.setDescription("top"))
        .where(o -> o.getCustomer().getName().equals("Kim"))
        .orderBy((o, by) -> by.desc(o.getPrice()))
        .limit(1);
assertEquals("update Order e0 set e0.description = 'top' where e0.id in (select t.id from (select x0.id as id from Order x0"
        + " join x0.customer k1 where k1.name = 'Kim' order by x0.price desc, x0.id asc limit 1) t)", u.toHql().hql());
```

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `update-limit`:

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

パス `o.getCustomer().getName()` は、select と同じように内側の select で結合になります（決定 D36）。更新文そのものはほかのテーブルを読みません。

<a id="update-exactly-one-row-executeone"></a>
## ちょうど 1 行を更新する: `executeOne()`

`executeOne()` は 1 行保護付きで更新を実行します。一致する id を最大 2 つ読みます。

id が 2 つなら何も変更せず、`NonUniqueResultException` を投げます。id が 1 つなら、その行を `id = :p and (条件)` で変更します。手順は削除と同じです（[7.4](07-4-delete.md#delete-exactly-one-row-executeone) を参照）。

```java
int one = qb.update(Order.class)
        .set(o -> o.setDescription("checked"))
        .where(o -> o.getId() == id)
        .executeOne();                             // 0 or 1; 2 or more rows: NonUniqueResultException
```

`execute-one` リージョンのテストメソッド（`DmlQueryTest` の `executeOneProtects`）は、更新での `executeOne()` も確認しています。2 行以上に一致すると何も変更しません。

Spring のリポジトリでは、`updateOne(where, set)` が同じ動作をします（[12.4](12-spring-data-jpa.md#one-row-protection-deleteone-and-updateone) を参照）。

---

---

[7. 更新、挿入、削除（Java）](07-dml.md) | **7.2 更新** | [7.4 削除](07-4-delete.md) | [7.5 挿入](07-5-insert.md) | [7.6 Upsert](07-6-upsert.md) | [Kotlin](../kotlin/07-2-update.md)
