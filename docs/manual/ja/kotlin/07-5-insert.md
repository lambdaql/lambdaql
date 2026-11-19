<a id="75-insert"></a>
# 7.5 挿入（Kotlin）

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

**SQL ブロックの読み方。** `…` は省略した select リスト（エンティティのすべてのカラム）で、`?` はバインドパラメーターです。各行は [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java) から取っています。このテストは各クエリを Hibernate 6.6.5 で H2 2.3、MariaDB 10.11、Oracle 23.26 に対して実行し、Hibernate の SQL が変わると失敗します。 このテストは Java のラムダを使います。意味が同じ Kotlin のラムダは同じ HQL になるので、SQL も同じです。

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `insert`:

```sql
-- H2, MariaDB, Oracle (same)
insert into archive(id,price,customer_name)
values (100,9.5,'Kim')
```

**(テスト済み)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)、リージョン `insert-delete`:

```kotlin
val i = qb.insert(Archive::class.java).values { it.id = 100L; it.price = 9.5; it.customerName = "Kim" }
assertEquals(1, i.execute())
val d = qb.delete(Order::class.java).where { o, sub -> !sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
assertEquals("delete from Order e0 where not exists (select 1 from Item s1 where s1.order.id = e0.id)", d.toHql().hql())
```

最初の 2 行が、Kotlin のラムダで 1 行を挿入します。delete の部分は [7.4](07-4-delete.md) で説明しています。
Java で書いたほかの文は [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) でテストしています（H2、MariaDB、Oracle）。それらの例は [このページの Java 版](../java/07-5-insert.md) を見てください。

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

[6. 選択、集計、グループ化](06-select-group.md) の `groupBy`、`having`、`Agg` 関数を使います。
順序は `from(...)`、`where { }`、`groupBy { }`、`having { }`、`select { }` です。`where`、`groupBy`、`having` は省略できます。

```kotlin
// INSERT ... SELECT と groupBy: 顧客ごとに集計の行を 1 つ
val i = qb.insert(Summary::class.java)
    .from(Order::class.java)
    .where { it.customer != null }
    .groupBy { it.customer?.id }
    .having { Agg.count() >= 2 }
    .select { s, o -> s.customerId = o.customer?.id; s.total = Agg.sum(o.price); s.cnt = Agg.count() }
// HQL: insert into Summary (customerId, total, cnt) select e0.customer.id, sum(e0.price), count(*) from Order e0
//      where e0.customer is not null group by e0.customer.id having count(*) >= 2L
```

同じ文を Java で書いたものは [`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java) でテストしています（リージョン `insert-group`）。

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `insert-group`:

```sql
-- H2, MariaDB, Oracle (same)
insert into summary(customer_id,total,cnt)
select o1_0.customer_id,sum(o1_0.price),count(*)
from orders o1_0
where o1_0.customer_id is not null
group by o1_0.customer_id
having count(*)>=2
```

- 集約のない値はすべて `groupBy` のキーでなければなりません（ルール J3）。そうでなければ、lambdaQL は解析時に `TranslationException`（`LQL-3035`）を投げます。
- `where` では `Agg` を使えません。`having` を使ってください。

---

---

[7. 更新、挿入、削除（Kotlin）](07-dml.md) | [7.2 更新](07-2-update.md) | [7.4 削除](07-4-delete.md) | **7.5 挿入** | [7.6 Upsert](07-6-upsert.md) | [Java](../java/07-5-insert.md)
