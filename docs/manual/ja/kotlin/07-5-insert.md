<a id="75-insert"></a>
# 7.5 挿入（Kotlin）

> [!NOTE]
> **状況: 設計済み、まだ実装されていません。**
> この章は決定済みの設計です（決定 D28、マイルストーン M10）。
> Kotlin の構文はコンパイルでき（kotlinc 2.3.21）、SQL は Hibernate 6.6.5 を使って H2、MariaDB、Oracle で確認しました。
> 現在の lambdaQL ではまだ **動きません**。今動くのは解析コアと `where` + `toList()` だけです
> （H2、MariaDB、Oracle でテスト済み）。全体の状況は [トラックの README](README.md) を見てください。

[7. 更新、挿入、削除（Kotlin）](07-dml.md) | [7.2 更新](07-2-update.md) | [7.4 削除](07-4-delete.md) | **7.5 挿入** | [Java](../java/07-5-insert.md)

---


<a id="one-row"></a>
## 1 行

```kotlin
qb.insert(Order::class.java).values { it.id = id; it.price = p; it.status = 1 }.execute()

// val プロパティには by を使う
qb.insert(Order::class.java).values { o, by -> by[o.id] = id; by[o.status] = 1 }.execute()
```

<a id="many-rows-from-a-list"></a>
## リストから複数行

リストの要素ごとに 1 行です。ラムダの 2 つ目の引数が要素です。

```kotlin
val lines = listOf(Line(1L, 10.0), Line(2L, 20.0))

qb.insert(Order::class.java)
    .values(lines) { o, l -> o.id = l.id; o.price = l.price; o.status = 1 }
    .execute()
```

lambdaQL は、多くの VALUES 行を持つ 1 つの文を書きます。リストが大きい場合は、**バインド値の数** で処理を分割します（[7.7](07-dml.md#batching-for-many-rows) を参照）。

<a id="generated-id"></a>
## 生成される id

id が IDENTITY または SEQUENCE を使う場合は、id を省略できます（ルール U12）。

```kotlin
qb.insert(IdOrder::class.java).values { it.price = p; it.status = 1 }.execute()
```

## Insert ... select

別のエンティティから行をコピーします。`select` のラムダは対象（`a`）とソース（`o`）を受け取ります。

```kotlin
qb.insert(Archive::class.java).from(Order::class.java).where { it.status == 9 }
    .select { a, o -> a.id = o.id; a.price = o.price; a.customerName = o.customer?.name }
    .execute()
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

lambdaQL はソースのパスに明示的な `left join` を使うので、顧客がいない注文も失われません（ルール U10）。

`by` を使う場合、コンテキストオブジェクトは 3 つ目の引数です。

```kotlin
qb.insert(Archive::class.java).from(Order::class.java).where { it.status == 9 }
    .select { a, o, by -> by[a.id] = o.id; by[a.customerName] = o.customer?.name }
    .execute()
```

<a id="insert--select-with-aggregates"></a>
## 集約を使う insert ... select

[6. 選択、集計、グループ化](06-select-group.md) の `groupBy` と `Agg` 関数を使います。

```kotlin
qb.insert(Summary::class.java).from(Order::class.java).groupBy { it.customer?.id }
    .select { s, o -> s.customerId = o.customer?.id; s.total = Agg.sum(o.price); s.cnt = Agg.count() }
    .execute()
```

---

---

[7. 更新、挿入、削除（Kotlin）](07-dml.md) | [7.2 更新](07-2-update.md) | [7.4 削除](07-4-delete.md) | **7.5 挿入** | [Java](../java/07-5-insert.md)
