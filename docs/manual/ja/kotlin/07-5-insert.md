<a id="75-insert"></a>
# 7.5 挿入（Kotlin）

> [!NOTE]
> **状況: 実装済み、テスト済み**（H2 2.3、MariaDB 10.11、Oracle 23.26、マイルストーン M10）。テストは **Java** のラムダを使っています。**DML での Kotlin のラムダはまだテストしていません。**
> **まだ実装されていません:** `insert ... select` の集計（マイルストーン M9）と、`insert ... select` の `by.set` の書き方。
> Kotlin コードはテスト済みの Java の文を Kotlin の構文で書いたもので、HQL は [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) の Java のテストから取っています。HQL のない例は設計に従っています。
> 全体の状況は [トラックの README](README.md) を見てください。

[7. 更新、挿入、削除（Kotlin）](07-dml.md) | [7.2 更新](07-2-update.md) | [7.4 削除](07-4-delete.md) | **7.5 挿入** | [7.6 Upsert](07-6-upsert.md) | [Java](../java/07-5-insert.md)

---


<a id="one-row"></a>
## 1 行

```kotlin
val i = qb.insert(Archive::class.java)
    .values { it.id = 100L; it.price = 9.5; it.customerName = "Kim" }
// HQL: insert into Archive (id, price, customerName) values (100L, 9.5, 'Kim')
val inserted = i.execute()
// a generated id (IDENTITY) can be left out
val generated = qb.insert(IdOrder::class.java).values { it.price = 1.0; it.status = 1 }.execute()
```

**SQL ブロックの読み方。** `…` は省略した select リスト（エンティティのすべてのカラム）で、`?` はバインドパラメーターです。各行は [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java) から取っています。このテストは各クエリを Hibernate 6.6.5 で H2 2.3、MariaDB 10.11、Oracle 23.26 に対して実行し、Hibernate の SQL が変わると失敗します。 このテストは Java のラムダを使います。意味が同じ Kotlin のラムダは同じ HQL になるので、SQL も同じです。 **（予定）** と書かれたブロックは [`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java) から取っています。lambdaQL はまだこれらの文を作らないので、このテストが予定の HQL を Hibernate に直接渡します。

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `insert`:

```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price,customer_name)
values (100,9.5,'Kim')
```

Kotlin のテストはまだありません。Java で書いた同じ文は [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) でテストしています（H2、MariaDB、Oracle）。テスト済みの例は [このページの Java 版](../java/07-5-insert.md) を見てください。

ラムダの中の定数はリテラルのままです（決定 D37）。値から新しい行を読むことはできません。`it.price = it.price` はエラーです。

1 行の挿入でも、`val` プロパティ向けの `by.set` の書き方が使えます。

```kotlin
qb.insert(Order::class.java).values { o, by -> by.set(o.id, id).set(o.status, 1) }.execute()
```

<a id="many-rows-from-a-list"></a>
## リストから複数行

リストの要素ごとに 1 行です。ラムダの 2 つ目の引数が要素です。

```kotlin
val lines = listOf(Line(201L, 1.0), Line(202L, 2.0))
val i = qb.insert(Archive::class.java).values(lines) { a, l -> a.id = l.id; a.price = l.price }
// HQL: insert into Archive (id, price) values (:p1, :p2), (:p3, :p4)
```

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `insert-list`:

```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price)
values (?,?), (?,?)
```

lambdaQL は要素のプロパティを Java で読むので、すべての値がバインドパラメーターになります。
VALUES 行を多数持つ 1 つの文を書きます。長いリストは、1 つの文の値が最大 500 個になるように分割されます（[7.8](07-dml.md#batching-for-many-rows) を参照）。空のリストでは SQL を実行せず、0 を返します。

<a id="generated-id"></a>
## 生成される id

id が IDENTITY か SEQUENCE を使う場合、id を省略できます（ルール U12）。
最初の例の `IdOrder` の行がこれにあたります。`IdOrder` の id は生成されます。

## Insert ... select

別のエンティティから行をコピーします。`select` のラムダは対象（`a`）とソース（`o`）を受け取ります。

```kotlin
val i = qb.insert(Archive::class.java)
    .from(Order::class.java)
    .where { it.status == 1 }
    .select { a, o -> a.id = o.id; a.price = o.price; a.customerName = o.customer?.name }
// a path in the select list: left join, so orders without a customer stay (U10)
// HQL: insert into Archive (id, price, customerName) select e0.id, e0.price, j1.name from Order e0 left join e0.customer j1 where e0.status = 1
```

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `insert-select`:

```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price,customer_name)
select o1_0.id,o1_0.price,c1_0.name
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where o1_0.status=1
```

lambdaQL はソースのパスに明示的な `left join` を使うので、顧客のいない注文は失われません（ルール U10）。SQL は 3 つのデータベースで同じです。

設計には、コンテキストオブジェクトを 3 つ目の引数にする `by.set` の書き方もあります。これは **まだ実装されていません**。

```kotlin
qb.insert(Archive::class.java).from(Order::class.java).where { it.status == 9 }
    .select { a, o, by -> by.set(a.id, o.id).set(a.customerName, o.customer?.name) }
    .execute()
```

<a id="insert--select-with-aggregates"></a>
## 集約を使う insert ... select

[6. 選択、集計、グループ化](06-select-group.md) の `groupBy` と `Agg` 関数を使います。
この書き方は **まだ実装されていません**（マイルストーン M9）。

```kotlin
qb.insert(Summary::class.java).from(Order::class.java).groupBy { it.customer?.id }
    .select { s, o -> s.customerId = o.customer?.id; s.total = Agg.sum(o.price); s.cnt = Agg.count() }
    .execute()
```

**データベースごとの SQL（予定）** — 予定の HQL を Hibernate が変換したもので、`PlannedSqlTest` が記録しました。lambdaQL はまだこのクエリを作りません。`expected-sql/` の [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)、例 `07-insert-group`:

```sql
-- H2, MariaDB, Oracle (same)
insert into summary(customer_id,total,cnt)
select o1_0.customer_id,sum(o1_0.price),count(*)
from orders o1_0
group by o1_0.customer_id
```

---

---

[7. 更新、挿入、削除（Kotlin）](07-dml.md) | [7.2 更新](07-2-update.md) | [7.4 削除](07-4-delete.md) | **7.5 挿入** | [7.6 Upsert](07-6-upsert.md) | [Java](../java/07-5-insert.md)
