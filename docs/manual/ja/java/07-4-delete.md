<a id="74-delete"></a>
# 7.4 削除（Java）

> [!NOTE]
> **状況: 設計済み、まだ実装されていません。**
> この章は決定済みの設計です（決定 D28、マイルストーン M10）。
> 構文は Java でコンパイルでき、SQL は Hibernate 6.6.5 を使って H2、MariaDB、Oracle で確認しました。
> 現在の lambdaQL では **動きません**。現在動くのは解析コアと `where` + `toList()` だけです
> （H2、MariaDB、Oracle でテスト済み）。全体の状況は [Java トラックの README](README.md#status) を参照してください。

[7. 更新、挿入、削除（Java）](07-dml.md) | [7.2 更新](07-2-update.md) | **7.4 削除** | [7.5 挿入](07-5-insert.md) | [Kotlin](../kotlin/07-4-delete.md)

---


```java
qb.delete(Order.class).where(o -> o.getStatus() == 9).execute();

qb.delete(Order.class).where(o -> o.getCustomer().getName() == null).execute();

qb.delete(Order.class)
  .where((o, sub) -> !sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).exists())
  .execute();

qb.delete(Order.class).all().execute();     // すべての行を削除: all() が必要
```

2 番目の例の HQL:

```sql
delete from Order o
where exists (select 1 from Customer c where c.id = o.customer.id and c.name is null)
```

**SQL ブロックの読み方。** `…` は省略した select リスト（エンティティのすべてのカラム）で、`?` はバインドパラメーターです。[`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java) はこのページの HQL を Hibernate に直接渡し、H2 2.3、MariaDB 10.11、Oracle 23.26（Hibernate 6.6.5）での SQL を記録します。lambdaQL はまだこれらのクエリを作りません。Hibernate の SQL が変わるとテストは失敗します。

**データベースごとの SQL（予定）** — 予定の HQL を Hibernate が変換したもので、`PlannedSqlTest` が記録しました。lambdaQL はまだこのクエリを作りません。`expected-sql/` の [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)、例 `07-delete-exists`:

```sql
-- H2, Oracle
delete from orders o1_0
where exists(select 1 from customers c1_0 where c1_0.id=o1_0.customer_id and c1_0.name is null)

-- MariaDB
delete o1_0 from orders o1_0
where exists(select 1 from customers c1_0 where c1_0.id=o1_0.customer_id and c1_0.name is null)
```

MariaDB は `delete o1_0 from orders o1_0` と書きます。H2 と Oracle は `delete from orders o1_0` と書きます。

これは、`qb.from(Order.class).where(o -> o.getCustomer().getName() == null)` が返すのと **同じ行** を削除します。つまり、顧客に名前がない注文です。
顧客の **ない** 注文は削除されません（ルール U9、決定 D36）。それらも削除するには、`o.getCustomer() == null || o.getCustomer().getName() == null` と書いてください。

id のリストで削除する（キャプチャしたコレクション、決定 D29）:

```java
qb.delete(Item.class).where(i -> ids.contains(i.getId())).execute();
```

---

---

[7. 更新、挿入、削除（Java）](07-dml.md) | [7.2 更新](07-2-update.md) | **7.4 削除** | [7.5 挿入](07-5-insert.md) | [Kotlin](../kotlin/07-4-delete.md)
