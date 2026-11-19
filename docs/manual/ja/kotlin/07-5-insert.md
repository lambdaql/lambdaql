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

**SQL ブロックの読み方。** `…` は省略した select リスト（エンティティのすべてのカラム）で、`?` はバインドパラメーターです。各行は [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java) から取っています。このテストは各クエリを Hibernate 6.6.5 で H2 2.3、MariaDB 10.11、Oracle 23.26 に対して実行し、Hibernate の SQL が変わると失敗します。このテストは Java のラムダを使います。意味が同じ Kotlin のラムダは同じ HQL になるので、SQL も同じです。

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

最初の 2 行が、Kotlin のラムダで 1 行を挿入します。削除の部分は [7.4](07-4-delete.md) で説明しています。
Java で書いたほかの文は [`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java) でテストしています（H2、MariaDB、Oracle）。それらの例は [このページの Java 版](../java/07-5-insert.md) を参照してください。

ラムダの中の定数はリテラルのままです（決定 D37）。値から新しい行を読むことはできません。`it.price = it.price` はエラーです。

`val` プロパティには、`Dml.value` で 1 行を書き込みます（決定 D49、[7.3](07-dml.md#73-two-ways-to-assign-property-or-dmlvalue) を参照）。

```kotlin
qb.insert(Order::class.java).values { o -> value(o.id, id); value(o.status, 1) }.execute()
```

<a id="a-subquery-in-a-value"></a>
### 値の中のサブクエリ

`values { a, sub -> ... }` のように引数が 2 つのラムダを使うと、2 番目の引数 `sub` でサブクエリを作れます（決定 D49）。
upsert の `values { a, sub -> ... }` と `onConflict { ... }.update { a, sub -> ... }` も同じです（[7.6](07-6-upsert.md#your-own-change-onconflictkeyupdate) を参照）。

**(テスト済み)** [`KotlinQueryTest.kt`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt)、リージョン `insert-values-sub`:

```kotlin
// values { a, sub -> ... }: a subquery can read captured values, not the new row (D49)
val i = qb.insert(Archive::class.java)
    .values { a, sub -> a.id = 100L; a.price = sub.from(Item::class.java).sum { it.price } }
assertEquals("insert into Archive (id, price) values (100L, (select sum(s1.price) from Item s1))", i.toHql()[0].hql())
```

- 別のテーブルの集約（キャプチャパラメーターの有無を問わず）、複数行、対象テーブル自身を読むサブクエリ（MariaDB のエラー 1093 なし）がすべて動きます（H2、MariaDB 10.11、Oracle 23.26 で実測）。
- サブクエリはキャプチャ値を読めますが、新しく挿入する行は読めません。
- リストの形でもサブクエリを使えます（[リストから複数行](#many-rows-from-a-list) を参照）。
- upsert の挿入値と `do update set` の中のサブクエリも動きます。Oracle では Hibernate が `merge` で実行します（実測）。upsert の形は Java のテスト（[`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)、リージョン `insert-values-sub`）で確認しています。

<a id="many-rows-from-a-list"></a>
## リストから複数行

リストの要素ごとに 1 行を挿入します。ラムダの 2 番目の引数が要素です。

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

**リストの形のサブクエリ。** `values(list) { a, l, sub -> ... }` のように、3 番目の引数でサブクエリを作れます（決定 D49）。`qb.upsert(X::class.java).values(list) { a, l, sub -> ... }` も同じです。
サブクエリは要素の値を読めます。その値はクエリの前に読み、行ごとに 1 つのパラメーターとしてバインドします。新しく挿入する行は読めません（3 つの DB で実測）。
HQL は `values (:p1, (select sum(s1.price) from Item s1 where s1.order.id = :p2)), (:p3, ...)` になります。Java のテスト（[`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)、リージョン `insert-list-sub`）で確認しています。

<a id="generated-id"></a>
## 生成される id

id が IDENTITY か SEQUENCE を使う場合、id を省略できます（ルール U12）。
最初の例の `IdOrder` の行がこれにあたります。`IdOrder` の id は生成される値です。

<a id="insert--select"></a>
## insert ... select

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

lambdaQL はソースのパスに明示的な `left join` を使うので、顧客のいない注文も失われません（ルール U10）。SQL は 3 つのデータベースで同じです。

`val` プロパティの対象には `Dml.value` を使います。1 番目の引数は対象 `a` のプロパティです。

```kotlin
qb.insert(Archive::class.java).from(Order::class.java).where { it.status == 9 }
    .select { a, o -> value(a.id, o.id); value(a.customerName, o.customer?.name) }
    .execute()
```

**サブクエリ。** ソースの `where` は 2 番目の引数で、`select` は 3 番目の引数で、サブクエリの起点 `sub` を受け取ります（決定 D49）。Java のテスト（[`DmlQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java)、リージョン `insert-select-sub`）でこれを確認しています。次は設計上の例です。

```kotlin
qb.insert(Archive::class.java).from(Order::class.java)
    .where { o, sub -> sub.from(Item::class.java).where { x -> x.order?.id == o.id }.exists() }
    .select { a, o, sub -> a.id = o.id; a.price = sub.from(Item::class.java).where { x -> x.order?.id == o.id }.sum { it.price } }
    .execute()
```

<a id="insert--select-with-aggregates"></a>
## 集約を使う insert ... select

[6. 選択、集計、グループ化](06-select-group.md) の `groupBy`、`having`、`Agg` 関数を使います。
呼び出しの順序は `from(...)`、`where { }`、`groupBy { }`、`having { }`、`select { }` です。`where`、`groupBy`、`having` は省略できます。

```kotlin
// INSERT ... SELECT with groupBy: one summary row per customer
val i = qb.insert(Summary::class.java)
    .from(Order::class.java)
    .where { it.customer != null }
    .groupBy { it.customer?.id }
    .having { Agg.count() >= 2 }
    .select { s, o -> s.customerId = o.customer?.id; s.total = Agg.sum(o.price); s.cnt = Agg.count() }
// HQL: insert into Summary (customerId, total, cnt) select e0.customer.id, sum(e0.price), count(*) from Order e0
//      where e0.customer is not null group by e0.customer.id having count(*) >= 2L
```

同じ文を Java で書いたテストが [`ProjectionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java) にあります（リージョン `insert-group`）。

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
