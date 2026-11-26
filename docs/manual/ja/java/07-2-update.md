<a id="72-update"></a>
# 7.2 更新（update）（Java）

[7. 更新、挿入、削除（Java）](07-dml.md) | **7.2 更新（update）** | [7.4 削除（delete）](07-4-delete.md) | [7.5 挿入（insert）](07-5-insert.md) | [7.6 Upsert](07-6-upsert.md) | [Kotlin](../kotlin/07-2-update.md)

---

<a id="basic-form"></a>
## 基本形

<!-- region: DmlQueryTest.java#update -->
```java
short st = 5;
ExecutableUpdate<Order> u = qb.update(Order.class)
        .set(o -> { o.setStatus(st); o.setPrice(o.getPrice() * 2); })
        .where(o -> o.getStatus() == 1);
// the price on the right is the value before the change
assertEquals("update Order e0 set e0.status = :p1, e0.price = (e0.price * 2.0) where e0.status = 1", u.toHql().hql());
int changed = u.execute();
```

`st` はキャプチャした変数なので、バインドパラメーター（`:p1`）になります。定数 `2` はラムダ式に書いた値なので、リテラル（`2.0`）のままです。
`toHql()` は文を実行せずに HQL とパラメーターを返します。`execute()` は文を実行し、更新した行数を返します。

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

Oracle は `set` にエイリアスを書きます（`set o1_0.status=?`）。H2 と MariaDB は書きません。

コード例: [`DmlQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)（`update`）。

<a id="values-null-date-boolean"></a>
## 値: null、日付、boolean

<!-- region: ManualJavaDmlTest.java#update-values -->
```java
ExecutableUpdate<Order> u = qb.update(Order.class)
        .set(o -> { o.setDescription(null); o.setUpdateAt(now); o.setKind(Order.Kind.RUSH); o.setActive(false); })
        .where(o -> o.getId() == id);
assertEquals("update Order e0 set e0.description = null, e0.updateAt = :p1,"
        + " e0.kind = org.lambdaql.query.Order$Kind.RUSH, e0.active = false where e0.id = :p2", u.toHql().hql());
int changed = u.execute();
```

`null`、ラムダ式に書いた enum 定数と boolean 定数はリテラルで、キャプチャ値（`now`、`id`）はバインドパラメーターです。同じ値を 2 回使っても、各キャプチャ値は **それぞれ別のパラメーター** になります。

コード例: [`ManualJavaDmlTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaDmlTest.java)（`update-values`）。

<a id="add-to-a-value-default-for-null"></a>
## 値への加算、NULL のときのデフォルト値

<!-- region: ManualJavaDmlTest.java#update-add -->
```java
ExecutableUpdate<Order> plusOne = qb.update(Order.class)
        .set(o -> o.setCnt(o.getCnt() + 1))
        .where(o -> o.getStatus() == 1);
assertEquals("update Order e0 set e0.cnt = (e0.cnt + 1) where e0.status = 1", plusOne.toHql().hql());
// a default for NULL: Objects.requireNonNullElse becomes coalesce
ExecutableUpdate<Order> orZero = qb.update(Order.class)
        .set(o -> o.setTotal(Objects.requireNonNullElse(o.getTotal(), 0.0) + 1))
        .all();
assertEquals("update Order e0 set e0.total = (coalesce(e0.total, 0.0) + 1.0)", orZero.toHql().hql());
```

`cnt` が NULL の行では、`cnt + 1` は NULL のままです（SQL の意味）。NULL を別の値に置き換えるには `Objects.requireNonNullElse` を使ってください。

コード例: [`ManualJavaDmlTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaDmlTest.java)（`update-add`）。

<a id="embedded-fields"></a>
## 埋め込みフィールド

<!-- region: ManualJavaDmlTest.java#update-embedded -->
```java
ExecutableUpdate<Order> oneColumn = qb.update(Order.class)
        .set(o -> o.getAddress().setCity(city)).where(o -> o.getId() == id);
assertEquals("update Order e0 set e0.address.city = :p1 where e0.id = :p2", oneColumn.toHql().hql());
// the whole value: every column of the embedded value (city, zip)
ExecutableUpdate<Order> whole = qb.update(Order.class)
        .set(o -> o.setAddress(addr)).where(o -> o.getId() == id);
assertEquals("update Order e0 set e0.address = :p1 where e0.id = :p2", whole.toHql().hql());
```

値オブジェクト全体を代入すると HQL は `e0.address = :p1` の 1 つだけで、Hibernate が埋め込み値のすべてのカラム（`city`、`zip`）を SQL の SET リストに書きます。

コード例: [`ManualJavaDmlTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaDmlTest.java)（`update-embedded`）。

<a id="change-an-association-foreign-key"></a>
## 関連の変更（外部キー）

<!-- region: ManualJavaDmlTest.java#update-fk -->
```java
ExecutableUpdate<Order> byId = qb.update(Order.class)
        .set(o -> o.getCustomer().setId(cid)).where(o -> o.getId() == id);
assertEquals("update Order e0 set e0.customer.id = :p1 where e0.id = :p2", byId.toHql().hql());
ExecutableUpdate<Order> none = qb.update(Order.class)
        .set(o -> o.setCustomer(null)).where(o -> o.getId() == id);
assertEquals("update Order e0 set e0.customer = null where e0.id = :p1", none.toHql().hql());
// a captured entity: its @Id is bound
ExecutableUpdate<Order> entity = qb.update(Order.class)
        .set(o -> o.setCustomer(customer)).where(o -> o.getId() == other);
assertEquals("update Order e0 set e0.customer = :p1 where e0.id = :p2", entity.toHql().hql());
```

関連エンティティに設定できるのは `@Id`（FK）だけです。
`o.getCustomer().setName(x)` はエラーです。別のテーブルを変更することになるからです（[7.8](07-dml.md#rules-checked-at-analysis-time) を参照）。

コード例: [`ManualJavaDmlTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaDmlTest.java)（`update-fk`）。

<a id="a-value-from-another-table"></a>
## 別のテーブルの値

関連のパスで値を読んでください。lambdaQL はこれを **相関スカラーサブクエリ** としてレンダリングします。
`where` のパスも同じ形のサブクエリになります。

<!-- region: DmlQueryTest.java#update-path -->
```java
// a path in the condition: a subquery, plus "customer exists" (the same rows as the select, U9)
ExecutableUpdate<Order> u = qb.update(Order.class)
        .set(o -> o.setCustName("K"))
        .where(o -> o.getCustomer().getName().equals("Kim"));
assertEquals("update Order e0 set e0.custName = 'K' where (select s1.name from Customer s1 where s1.id = fk(e0.customer)) = 'Kim'"
        + " and e0.customer.id is not null", u.toHql().hql());
// a path in a value: a subquery; NULL when there is no customer
ExecutableUpdate<Order> copy = qb.update(Order.class).set(o -> o.setCustName(o.getCustomer().getName())).all();
assertEquals("update Order e0 set e0.custName = (select s1.name from Customer s1 where s1.id = fk(e0.customer))", copy.toHql().hql());
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

注文に顧客がない場合、`custName` は NULL になります。
`where` では lambdaQL が `e0.customer.id is not null` を加えます。そのため、更新の対象行は `qb.from(Order.class).where(同じラムダ式)` の結果と同じです。

コード例: [`DmlQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)（`update-path`）。

<a id="an-aggregate-from-a-subquery"></a>
## サブクエリの集計

引数が 2 つのラムダ式 `set((o, sub) -> ...)` を使ってください。2 番目の引数はサブクエリの起点 `sub` です。
ここでは `Agg` は必要ありません。サブクエリには [5. サブクエリ](05-subquery.md) と同じように `sum`、`avg`、... があります。

<!-- region: DmlQueryTest.java#update-sub -->
```java
// the second parameter starts a subquery, as in where((o, sub) -> ...)
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

- 別のテーブルの集計も、同じテーブルの集計も使えます（H2、MariaDB 10.11、Oracle 23.26 で実測）。`update` では MariaDB もエラー 1093 を出しません。
- 一致する行がない場合、集計は NULL です。NOT NULL カラムでは DB のエラーになるので、`Objects.requireNonNullElse(…, 0.0)` で包んで `coalesce(...)` にしてください。

コード例: [`DmlQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)（`update-sub`）。

<a id="where-with-paths-and-subqueries"></a>
## パスとサブクエリを使う where

`where` は選択クエリと同じです。

<!-- region: ManualJavaDmlTest.java#update-where -->
```java
// a path in the condition: a scalar subquery
ExecutableUpdate<Order> byGrade = qb.update(Order.class)
        .set(o -> o.setStatus((short) 0))
        .where(o -> o.getCustomer().getGrade() > 3);
assertEquals("update Order e0 set e0.status = 0 where (select s1.grade from Customer s1 where s1.id = fk(e0.customer)) > 3"
        + " and e0.customer.id is not null", byGrade.toHql().hql());
// a subquery with sub
ExecutableUpdate<Order> noItems = qb.update(Order.class)
        .set(o -> o.setStatus((short) 9))
        .where((o, sub) -> !sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).exists());
assertEquals("update Order e0 set e0.status = 9 where not exists (select 1 from Item s1 where s1.order.id = e0.id)",
        noItems.toHql().hql());
```

1 つ目の文では、パスがスカラーサブクエリになり、顧客がある注文だけが対象になります。
2 つ目の文は [5. サブクエリ](05-subquery.md) と同じように `sub` を使います。

コード例: [`ManualJavaDmlTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaDmlTest.java)（`update-where`）。

<a id="optimistic-locking-version"></a>
## 楽観的ロック（`@Version`）

エンティティに `@Version` フィールドがある場合、lambdaQL は `update versioned` をレンダリングします。データベースがバージョンに 1 を加えます。

<!-- region: DmlQueryTest.java#update-versioned -->
```java
// an entity with a version field (Version annotation): the version goes up too
ExecutableUpdate<VOrder> u = qb.update(VOrder.class).set(v -> v.setStatus(2)).where(v -> v.getId() == 1L);
assertEquals("update versioned VOrder e0 set e0.status = 2 where e0.id = 1L", u.toHql().hql());
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

<!-- region: ManualJavaDmlTest.java#update-version -->
```java
// put the version in the condition and check the result
ExecutableUpdate<VOrder> u = qb.update(VOrder.class)
        .set(o -> o.setStatus(8))
        .where(o -> o.getId() == id && o.getVersion() == v);
assertEquals("update versioned VOrder e0 set e0.status = 8 where e0.id = :p1 and e0.version = :p2", u.toHql().hql());
int n = u.execute();
if (n == 0) throw new OptimisticLockException();   // checking the result is your job
```

同じ文をもう一度実行すると、バージョンが合わないので 0 を返します。最初の実行でバージョンが上がったため、古いバージョンに一致する行はありません。

`@Version` フィールドには値を代入できません。代入すると `LQL-3068` で拒否されます。

コード例: [`DmlQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)（`update-versioned`）、[`ManualJavaDmlTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaDmlTest.java)（`update-version`）。

<a id="many-set-calls-patch"></a>
## 複数回の `set()` 呼び出し（PATCH）

`set()` は何回でも呼べます。すべての代入は、呼び出した順に 1 つの SET リストにまとめられます。
前に代入した列をもう一度読めないルールと、同じ列に二度代入できないルールは、複数の `set()` 呼び出しにまたがっても適用されます。

<!-- region: DmlQueryTest.java#update-patch -->
```java
// PATCH: assign only the fields that have a value; every set() adds assignments
String memo = "checked";
Short newStatus = null;
UpdateSet<Order> u = qb.update(Order.class).set(o -> o.setDescription(memo));
if (newStatus != null) u = u.set(o -> o.setStatus(newStatus));
int changed = u.where(o -> o.getId() == id).execute();

// reading a column that an earlier assignment wrote is an error: the databases disagree on its value
assertThrows(LambdaQLException.class,
        () -> qb.update(Order.class).set(o -> { o.setPrice(1); o.setTex((float) o.getPrice()); }));
// the same column twice is an error, also across set() calls
assertThrows(LambdaQLException.class,
        () -> qb.update(Order.class).set(o -> o.setStatus((short) 1)).set(o -> o.setStatus((short) 2)));
```

`set()` は `UpdateSet` を返します。この型には `set`、`where`、`all` があり、`execute()` はありません。

コード例: [`DmlQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)（`update-patch`）。

<a id="update-all-rows-all"></a>
## すべての行の更新: `all()`

`where` のない update には `all()` を明示する必要があります。

<!-- region: ManualJavaDmlTest.java#update-all -->
```java
// without where, all() is required; without both, there is no execute() (a compile error)
ExecutableUpdate<Order> u = qb.update(Order.class).set(o -> o.setStatus((short) 0)).all();
assertEquals("update Order e0 set e0.status = 0", u.toHql().hql());
int changed = u.execute();
```

update にはトランザクションが必要です（[7.8](07-dml.md#transactions) を参照）。

コード例: [`ManualJavaDmlTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaDmlTest.java)（`update-all`）。

<a id="update-the-first-rows-limit"></a>
## 先頭の行の更新: `limit`

`where(...)` または `all()` のあとに `orderBy(...)` と `limit(n)` を付けると、最大 `n` 行を更新します。対象は `qb.from(X.class).where(same).orderBy(same)` の先頭 `n` 行です。

ルールは delete と同じです（[7.4](07-4-delete.md#delete-the-first-rows-limit) を参照）。`orderBy` がなければ id 順で、id は常に最後のソートキーです。`limit` のない `orderBy` はエラーです。

<!-- region: DmlQueryTest.java#update-limit -->
```java
// a path in the condition: the nested select joins, so the rows are the same as the select
ExecutableUpdate<Order> u = qb.update(Order.class)
        .set(o -> o.setDescription("top"))
        .where(o -> o.getCustomer().getName().equals("Kim"))
        .orderBy(o -> desc(o.getPrice()))
        .limit(1);
assertEquals("update Order e0 set e0.description = 'top' where e0.id in (select t.id from (select x0.id as id from Order x0"
        + " join x0.customer k1 where k1.name = 'Kim' order by x0.price desc, x0.id asc limit 1) t)", u.toHql().hql());
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

パス `o.getCustomer().getName()` は、選択と同じように内側の select で結合になります。update 文そのものは別のテーブルを読みません。

コード例: [`DmlQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)（`update-limit`）。

<a id="update-exactly-one-row-executeone"></a>
## ちょうど 1 行の更新: `executeOne()`

`executeOne()` は 1 行保護を適用して update を実行します。条件に一致する id を最大 2 つ読みます。

id が 2 つなら、何も変更せずに `NonUniqueResultException` を投げます。id が 1 つなら、`id = :p and (条件)` でその行を更新します。手順は delete と同じです（[7.4](07-4-delete.md#delete-exactly-one-row-executeone) を参照）。

<!-- region: ManualJavaDmlTest.java#update-one -->
```java
int one = qb.update(Order.class)
        .set(o -> o.setDescription("checked"))
        .where(o -> o.getId() == id)
        .executeOne();                             // 0 or 1
// two or more rows match: nothing changes
assertThrows(NonUniqueResultException.class, () -> qb.update(Order.class)
        .set(o -> o.setDescription("checked"))
        .where(o -> o.getStatus() == 1)
        .executeOne());
```

2 回目の呼び出しのように 2 行以上が条件に一致すると、`NonUniqueResultException` を投げて何も変更しません。

Spring のリポジトリでは、`updateOne(where, set)` が同じ動作をします（[12.4](12-spring-data-jpa.md#one-row-protection-deleteone-and-updateone) を参照）。

---

[7. 更新、挿入、削除（Java）](07-dml.md) | **7.2 更新（update）** | [7.4 削除（delete）](07-4-delete.md) | [7.5 挿入（insert）](07-5-insert.md) | [7.6 Upsert](07-6-upsert.md) | [Kotlin](../kotlin/07-2-update.md)

コード例: [`ManualJavaDmlTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaDmlTest.java)（`update-one`）。
