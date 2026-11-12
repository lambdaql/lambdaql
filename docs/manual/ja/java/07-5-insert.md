<a id="75-insert"></a>
# 7.5 挿入（Java）

> [!NOTE]
> **状況: 設計済み、まだ実装されていません。**
> この章は決定済みの設計です（決定 D28、マイルストーン M10）。
> 構文は Java でコンパイルでき、SQL は Hibernate 6.6.5 を使って H2、MariaDB、Oracle で確認しました。
> 現在の lambdaQL では **動きません**。現在動くのは解析コアと `where` + `toList()` だけです
> （H2、MariaDB、Oracle でテスト済み）。全体の状況は [Java トラックの README](README.md#status) を参照してください。

[7. 更新、挿入、削除（Java）](07-dml.md) | [7.2 更新](07-2-update.md) | [7.4 削除](07-4-delete.md) | **7.5 挿入** | [Kotlin](../kotlin/07-5-insert.md)

---


<a id="one-row"></a>
## 1 行

```java
qb.insert(Order.class).values(o -> { o.setId(id); o.setPrice(p); o.setStatus(1); }).execute();
```

<a id="many-rows-from-a-list"></a>
## リストから複数行

リストの要素ごとに 1 行です。ラムダの 2 番目の引数が要素です。

```java
qb.insert(Order.class)
  .values(lines, (o, l) -> { o.setId(l.id()); o.setPrice(l.price()); o.setStatus(1); })
  .execute();
```

lambdaQL は、VALUES 行を多数持つ 1 つの文を書きます。リストが大きい場合は、**バインド値の数** で処理を分割します（[7.7](07-dml.md#batching-for-many-rows) を参照）。

<a id="generated-id"></a>
## 生成される id

id が IDENTITY か SEQUENCE を使う場合、id を省略できます（ルール U12）。

```java
qb.insert(IdOrder.class).values(o -> { o.setPrice(p); o.setStatus(1); }).execute();
```

## Insert ... select

別のエンティティから行をコピーします。`select` のラムダは対象（`a`）とソース（`o`）を受け取ります。

```java
qb.insert(Archive.class).from(Order.class).where(o -> o.getStatus() == 9)
  .select((a, o) -> { a.setId(o.getId()); a.setPrice(o.getPrice()); a.setCustomerName(o.getCustomer().getName()); })
  .execute();
```

```sql
insert into Archive (id, price, customerName)
select o.id, o.price, c.name from Order o left join o.customer c where o.status = 9
```

**SQL ブロックの読み方。** `…` は省略した select リスト（エンティティのすべてのカラム）で、`?` はバインドパラメーターです。[`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java) はこのページの HQL を Hibernate に直接渡し、H2 2.3、MariaDB 10.11、Oracle 23.26（Hibernate 6.6.5）での SQL を記録します。lambdaQL はまだこれらのクエリを作りません。Hibernate の SQL が変わるとテストは失敗します。

**データベースごとの SQL（予定）** — 予定の HQL を Hibernate が変換したもので、`PlannedSqlTest` が記録しました。lambdaQL はまだこのクエリを作りません。`expected-sql/` の [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)、例 `07-insert-select`:

```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price,customer_name)
select o1_0.id,o1_0.price,c1_0.name
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where o1_0.status=9
```

lambdaQL はソースのパスに明示的な `left join` を使うので、顧客のない注文は失われません（ルール U10）。

`by.set` を使う場合、コンテキストオブジェクトは 3 番目の引数です。

```java
qb.insert(Archive.class).from(Order.class).where(o -> o.getStatus() == 9)
  .select((a, o, by) -> by.set(a.getId(), o.getId()).set(a.getCustomerName(), o.getCustomer().getName()))
  .execute();
```

<a id="insert--select-with-aggregates"></a>
## 集約を使う insert ... select

[6. 選択、集計、グループ化](06-select-group.md) の `groupBy` と `Agg` 関数を使います。

```java
qb.insert(Summary.class).from(Order.class).groupBy(o -> o.getCustomer().getId())
  .select((s, o) -> { s.setCustomerId(o.getCustomer().getId()); s.setTotal(Agg.sum(o.getPrice())); s.setCnt(Agg.count()); })
  .execute();
```

---

---

[7. 更新、挿入、削除（Java）](07-dml.md) | [7.2 更新](07-2-update.md) | [7.4 削除](07-4-delete.md) | **7.5 挿入** | [Kotlin](../kotlin/07-5-insert.md)
