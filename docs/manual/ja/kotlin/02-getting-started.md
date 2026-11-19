<a id="2-getting-started-kotlin"></a>
# 2. はじめよう（Kotlin）

[Kotlin トラック](README.md) | [マニュアルのホーム](../README.md) | 前: [1. 概要](../01-overview.md) | 次: [3. 条件](03-where.md) | [このページの Java 版](../java/02-getting-started.md)

共通ページ: [1. 概要](../01-overview.md) · [11. エラーと言語](../11-errors-and-languages.md)

---

<a id="requirements"></a>
## 必要な環境

| 項目 | バージョン | 注意 |
|---|---|---|
| Kotlin | 2.x | 設計は kotlinc 1.7.22、2.1.10、2.2.21、2.3.0、2.3.21 で確認しました。 |
| JVM | Java 21 | lambdaQL のビルドに必要です。 |
| `-jvm-target` | 1.8、11、17、21 | 4 つとも、クエリのラムダを同じようにコンパイルします。 |
| JPA プロバイダー | Hibernate ORM 7.4 | lambdaQL は Hibernate 7 の HQL を組み立てます。7.4.13 でテストしました。他の JPA プロバイダーはサポートしていません。 |
| Jakarta Persistence | 3.2 | Hibernate 7.4 に含まれています。 |
| データベース | H2 2.3、MariaDB 10.11、Oracle 23.26 (23ai) | この 3 つで検証しました。他のデータベースでも動くかもしれませんが、テストはしていません。 |
| Spring Boot | 4.1 | 任意です。プロジェクトのテストは Spring Boot 4.1.2 を使います。 |
| Kotlin ランタイム | – | lambdaQL は `kotlin-stdlib` や `kotlin-reflect` を依存関係に追加しません。 |

<a id="build-from-source"></a>
## ソースからビルドする

```bash
git clone https://github.com/codebit0/lambdaql.git
cd lambdaql
./gradlew build
```

- Gradle ラッパーは 9.9.1 です。リポジトリには `gradle/gradle-daemon-jvm.properties` があるので、Gradle デーモンは Java 21 を使います。
- ビルドには JDK 21 と **JDK 24 以上** の JDK 1 つが必要です。JDK 24 以上は `java.lang.classfile` のリーダー（`src/main/java24`）をコンパイルし、そのリーダーでテストをもう一度実行します。デフォルトのバージョンは 25 で、`-Plql.classfile.jdk=24` のように変更できます。Gradle が JDK を見つけられない場合は、`~/.gradle/gradle.properties` の `org.gradle.java.installations.paths` にパスを書いてください。
- IntelliJ IDEA では、**Settings > Build Tools > Gradle > Gradle JVM** を Java 21 の JDK に設定してください。
- ビルドには 3 つのモジュールがあります。`query`（アーティファクト `lambdaql-core`）、`spring-data-jpa`（`lambdaql-spring-data-jpa`）、`spring-boot-starter`（`lambdaql-spring-boot-starter`）です。Spring のモジュールは [12. Spring Data JPA](12-spring-data-jpa.md) で説明します。

ユニットテストを実行します。

```bash
./gradlew :query:test
```

[`SelectQueryTest.java`](../../../../query/src/test/java/org/lambdaql/query/SelectQueryTest.java) のテストで、lambdaQL が現在何をできるかが分かります。`qb.from(...).where(...)`、HQL、パラメーター、そして H2、MariaDB、Oracle での行です。[`ConditionDecodeTest.java`](../../../../query/src/test/java/org/lambdaql/engine/decode/ConditionDecodeTest.java) は内部の条件デコーダーをテストします。これらのテストは Java で書かれています。Kotlin のテストは予定です（`query/src/test/kotlin`）。

<a id="use-the-local-build-in-your-project"></a>
### ローカルビルドを自分のプロジェクトで使う

3 つのモジュールの jar をローカルの Maven リポジトリに公開します。

```bash
./gradlew publishToMavenLocal
```

それから、`lambdaql-core` を自分のプロジェクトに追加します（Gradle Kotlin DSL）。

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("org.lambdaql:lambdaql-core:1.0.0")
}
```

Maven:

```xml
<dependency>
  <groupId>org.lambdaql</groupId>
  <artifactId>lambdaql-core</artifactId>
  <version>1.0.0</version>
</dependency>
```

Spring Data JPA や Spring Boot を使う場合は、代わりに `lambdaql-spring-boot-starter` か `lambdaql-spring-data-jpa` を使ってください。どちらも `lambdaql-core` を持ち込みます（[12.2](12-spring-data-jpa.md#122-modules) を参照）。

> [!WARNING]
> バージョン `1.0.0` はリポジトリの `gradle.properties` で定義した値で、最初のリリースまでに変わることがあります。
> lambdaQL が Maven Central に公開されたら、このページをもう一度確認してください。

<a id="kotlin-compiler-settings"></a>
## Kotlin コンパイラーの設定

| 設定 | すること | 理由 |
|---|---|---|
| `-Xsam-conversions` | デフォルト（`indy`）のままにします。`-Xsam-conversions=class` は設定 **しないで** ください。 | `class` にすると、Kotlin はシリアライズ可能なラムダではなくクラスを作ります。lambdaQL はそれを読めません（制限 KC1）。 |
| `-java-parameters` | ストアドプロシージャを呼ぶなら推奨です。 | lambdaQL がパラメーター名をデータベースと比べられるようになります（[8.4](08-procedures-functions.md#84-warning-argument-order) を参照）。 |
| JPA プラグイン | `kotlin("plugin.jpa")` と all-open（または `kotlin("plugin.spring")`）を使います。 | JPA には引数なしのコンストラクターと open なクラスが必要です。これは Kotlin の JPA エンティティでは普通のことです。 |

例（`build.gradle.kts`）:

```kotlin
plugins {
    kotlin("jvm")
    kotlin("plugin.jpa")
    kotlin("plugin.spring")   // または all-open プラグイン
}

kotlin {
    compilerOptions {
        javaParameters = true   // -java-parameters と同じ
    }
}
```

プラグインのバージョンは、使っている Kotlin のバージョンに合わせてください。

<a id="example-entities"></a>
## 例で使うエンティティ

Kotlin トラックの例では、すべて次のエンティティを使います。

```kotlin
@Entity
@Table(name = "orders")
class Order(
    @Id @GeneratedValue
    var id: Long? = null,
    var price: Double? = null,
    var status: Int? = null,
    var active: Boolean = false,          // Kotlin の Boolean: JVM のプリミティブ、NULL にならない
    var description: String? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    var customer: Customer? = null,
    var updateAt: LocalDateTime? = null,
    @OneToMany(mappedBy = "order")
    var items: MutableList<Item> = mutableListOf(),
)

@Entity
class Customer(
    @Id @GeneratedValue
    var id: Long? = null,
    var name: String? = null,
    var grade: Int? = null,
)

@Entity
class Item(
    @Id @GeneratedValue
    var id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    var order: Order? = null,
    var price: Double? = null,
    var qty: Int = 0,                     // Kotlin の Int: JVM のプリミティブ、NULL にならない
)
```

エンティティのルール:

| ルール | 理由 |
|---|---|
| 普通の public な `var` か `val` のプロパティを使います。 | lambdaQL は getter を読み（`it.price` は getter の `getPrice()`）、それを JPA の属性にマッピングします。 |
| NULL になりうるカラムには nullable な型（`Double?`、`String?`）を使います。 | Kotlin の non-null 型でも、データベースからの NULL は防げません。Hibernate はチェックなしでカラムの値をフィールドに書き込みます。 |
| クエリでは `@JvmField`、`internal` プロパティ、`value class` のプロパティ、`@get:JvmName` を使わないでください。 | lambdaQL はこれらを JPA の属性にマッピングできません（制限 KC15、KC16）。 |
| `Boolean`、`Int`、`Long`、`Double`（nullable でないもの）は JVM のプリミティブです。 | NULL を持つことはありません。lambdaQL はこれを知っていて、より単純な SQL を書けます。 |

<a id="create-a-querybuilder"></a>
## QueryBuilder を作る

`QueryBuilder` が入り口です。JPA の `EntityManagerFactory` か `EntityManager` が必要です。

```kotlin
val qb = QueryBuilder(entityManagerFactory)
```

`QueryBuilder` は 1 つ作って再利用してください。クエリごとに新しく作らないでください。

<a id="with-plain-jpa"></a>
### 素の JPA で

```kotlin
val emf = Persistence.createEntityManagerFactory("my-unit")
val qb = QueryBuilder(emf)
```

<a id="with-spring-boot"></a>
### Spring Boot で

`lambdaql-spring-boot-starter` を使うと、Spring Boot が `QueryBuilder` の Bean を作ります（[12.8](12-spring-data-jpa.md#128-the-querybuilder-bean) を参照）。
スターターを使わない場合は、Bean を自分で作ります。自動構成と同じように、Spring の共有 `EntityManager` を使ってください。

```kotlin
@Configuration
class LambdaQlConfig {

    @Bean
    fun queryBuilder(entityManagerFactory: EntityManagerFactory) =
        QueryBuilder(SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory))
}
```

あとは、必要な場所に `QueryBuilder` を注入します。

```kotlin
@Service
class OrderService(private val qb: QueryBuilder) {

    @Transactional(readOnly = true)
    fun activeOrders(): List<Order> =
        qb.from(Order::class.java)
            .where { it.active }
            .toList()
}
```

- 共有 `EntityManager` は現在のトランザクションに結び付いています。`@Transactional` の中では、lambdaQL は同じトランザクションで実行されます。
- Spring のアプリケーションでは、`EntityManagerFactory` を受け取るコンストラクターを使わないでください。このコンストラクターで作ったビルダーはクエリごとに自分の `EntityManager` を開き、その `EntityManager` は Spring のトランザクションに参加しません。
- Spring Data のリポジトリの中で lambdaQL のクエリを書く方法は、[12. Spring Data JPA](12-spring-data-jpa.md) を参照してください。

<a id="your-first-query"></a>
## 最初のクエリ

```kotlin
val orders = qb.from(Order::class.java)
    .where { it.status == 1 && it.price!! > 100.0 }
    .toList()
// HQL: select e0 from Order e0 where e0.status = 1 and e0.price > 100.0
```

**SQL ブロックの読み方。** `…` は省略した select リスト（エンティティのすべてのカラム）で、`?` はバインドパラメーターです。各行は [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java) から取っています。このテストは各クエリを Hibernate 7.4 で H2 2.3、MariaDB 10.11、Oracle 23.26 に対して実行し、Hibernate の SQL が変わると失敗します。このテストは Java のラムダを使います。意味が同じ Kotlin のラムダは同じ HQL になるので、SQL も同じです。

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `to-hql`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>100.0
```

- `qb.from(Order::class.java)` で `Order` に対するクエリを始めます。
- `where { ... }` は条件のラムダを 1 つ受け取ります。末尾ラムダと `it` を使います。
- `toList()` はクエリを実行し、`List<Order>` を返します。
- `toList()` はすべての行を返します。他の結果の関数（`first()`、`one()`、`count()`、`exists()`、`toSet()`）は [結果を受け取る](#getting-the-result) にあります。

なぜ `!!` なのでしょうか。`price` は `Double?` で、Kotlin は nullable な値に対する `it.price > 100.0` をコンパイルしません。`!!` を付けるとコンパイルできます。lambdaQL は `!!` を **無視** します。`price` が NULL の行は単に返されません（SQL の意味）。

なぜ `1` と `100.0` はバインドパラメーターではなく、HQL に直接書かれるのでしょうか。これらは **ラムダの中に書いた定数** だからです。lambdaQL は定数（数値、文字列、boolean、`null`、enum 定数、`const val`）をリテラルとして HQL に書きます。ラムダの外から来る値は違います。それらは常にバインドパラメーターになります（次の節と [3.3](03-where.md#33-basic-form) を参照）。

<a id="use-values-from-outside-the-lambda"></a>
### ラムダの外の値を使う

ラムダは、ローカルの値、プロパティ、関数のパラメーターを使えます。これらは **キャプチャした値** です。
lambdaQL はそれをバインドパラメーターに変えます。クエリを実行するたびに新しい値を読みます。

```kotlin
fun expensive(status: Int, minPrice: Double): List<Order> =
    qb.from(Order::class.java)
        .where { it.status == status && it.price!! >= minPrice }
        .toList()
// HQL: select e0 from Order e0 where e0.status = :p1 and e0.price >= :p2
```

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `start-captured`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=? and o1_0.price>=?
```

<a id="many-where-calls"></a>
### 複数の `where` 呼び出し

`where` は 2 回以上呼べます。lambdaQL は条件を `AND` でつなぎます。

```kotlin
qb.from(Order::class.java)
    .where { it.active }
    .where { it.status == 1 || it.status == 2 }
    .toList()
// HQL: select e0 from Order e0
//      where e0.active = true and (e0.status = 1 or e0.status = 2)
```

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `start-many-where`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.active=true and (o1_0.status=1 or o1_0.status=2)
```

条件を少しずつ追加するときに便利です。

```kotlin
var query = qb.from(Order::class.java).where { it.active }
if (minPrice != null) {
    query = query.where { it.price!! >= minPrice }
}
val orders = query.toList()
```

<a id="paths-to-other-entities"></a>
### 他のエンティティへのパス

`it.customer?.name` のようなパスは、結合したエンティティのカラムを指定します。
`where` の中では、lambdaQL はデフォルトで内部結合の `join` を追加します。customer のない注文は結果に **入りません**。

```kotlin
qb.from(Order::class.java)
    .where { it.customer?.name == name }
    .toList()
// HQL: select e0 from Order e0 join e0.customer j1 where j1.name = :p1
//      （ここでは name は non-null の String なので、lambdaQL は単純な = を書きます。3.6 を参照）
```

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `inner-join`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name=?
```

`it.customer!!.name` でも同じ HQL になります。好きな書き方を使ってください。
customer のない注文を探すには、関連を比較してください。`it.customer == null` は `e0.customer is null` になります（結合なし）。
`it.customer?.name == null` ではそれらの注文は **含まれません**。
null ガード（`it.customer == null || ...`）を書いても、customer のない注文は残りません。パスは内部結合だからです（決定 D48）。
そのような注文を残すには `leftJoin` を使ってください（[6.8](06-select-group.md#68-explicit-joins)）。[3.10](03-where.md#310-association-paths) を参照してください。

<a id="getting-the-result"></a>
## 結果を受け取る

`toList()` だけがクエリを実行する方法ではありません。lambdaQL には結果を得る関数が 6 つあります（決定 D38、D42）。

| 関数 | 戻り値 | 呼べる場所 | 何をするか |
|---|---|---|---|
| `toList()` | `List<T>` | `where` の後、および `orderBy` / `limit` / `offset` の後 | すべての行（または 1 ページ分）を返します。 |
| `first()` | `Optional<T>` | `where` の後、および `orderBy` / `limit` / `offset` の後 | 最初の行を返します。読むのは 1 行だけです。 |
| `one()` | `Optional<T>` | `where` の後だけ | ただ 1 つの行を返します。2 行以上が合うと `NonUniqueResultException` を投げます。読むのは最大 2 行です。 |
| `count()` | `Long` | `where` の後だけ | 条件に合う行の数を返します。 |
| `exists()` | `Boolean` | `where` の後だけ | 合う行があれば `true` を返します。読むのは 1 行だけで、エンティティは読み込みません。 |
| `toSet()` | `SequencedSet<T>` | `where` の後、および `orderBy` / `limit` / `offset` の後 | すべての行（または 1 ページ分）を、行の順序を保つ Set として返します。Set が行数より小さいと `IllegalStateException` を投げます。 |

「`where` の後」とは、`qb.from(...)` と `where { }` で得られるクエリ（型は `SelectQuery`）のことです。このマニュアルではこれを **where 段階** と呼びます。`where` なしで、`qb.from(...)` のすぐ後に呼ぶこともできます。

`orderBy`、`limit`、`offset` の後、クエリの型は `OrderedSelectQuery` になります。そこで使える結果の関数は `toList()`、`toSet()`、`first()` だけです。`one()`、`count()`、`exists()` はそこではコンパイルできません。
これは意図的です（決定 D38）。`limit(10).count()` は「すべての行」とも「最大 10 行」とも読めるので、lambdaQL はこれを許しません。

`first()` と `one()` は Java の `Optional` を返します。Kotlin では `.orElse(null)` で nullable な値（`Order?`）にできます。

結果の関数についての Kotlin のテストはまだありません。同じ呼び出しを Java で書いたものは [`ResultQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ResultQueryTest.java) でテストしています（H2、MariaDB、Oracle）。テスト済みの例は [このページの Java 版](../java/02-getting-started.md#getting-the-result) を参照してください。

<a id="first"></a>
### `first()`

`orderBy` がない場合、「最初」とは id が最も小さい行のことです（ルール S9）。だから、いつも同じ行が返ります。
順序を選ぶには、先に `orderBy { }` を呼びます。すると `first()` はその順序で最初の行を返します。`offset` はそのまま使われます。`orderBy { ... }.offset(1).first()` は 2 番目の行を返します。
合う行がなければ、結果は `Optional.empty()` です。

```kotlin
val cheapest: Order? = qb.from(Order::class.java)
    .orderBy { o, by -> by.asc(o.price) }
    .first()
    .orElse(null)
// HQL: select e0 from Order e0 order by e0.price asc nulls first, e0.id asc

// without orderBy, "first" is the row with the smallest id
val firstActive = qb.from(Order::class.java).where { it.active }.first()
// HQL: select e0 from Order e0 where e0.active = true order by e0.id asc
```

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `first#2`（`firstActive`）:

```sql
-- H2, Oracle
select …
from orders o1_0
where o1_0.active=true
order by o1_0.id
fetch first ? rows only

-- MariaDB
select …
from orders o1_0
where o1_0.active=true
order by o1_0.id
limit ?
```

`first()` は 1 行だけを読むので、ページングしたクエリになります。lambdaQL は最後のソートキーとして `id` を追加し（[4.8](04-order-paging.md#tie-breaker-automatic-id) を参照）、Hibernate はデータベースごとにページングを書きます。H2 と Oracle では `fetch first ? rows only`、MariaDB では `limit ?` です。`?` は 1 です。
`cheapest` のテストでは、NULL にならない `price` を使っているので、その HQL には `nulls` の部分がありません。ここでは `price` が `Double?` なので、HQL が少し違います（Java のページの例 `first` を参照）。

<a id="one"></a>
### `one()`

条件に合う行が多くても 1 つしかないとき（たとえばユニークキー）に `one()` を使います。
行がなければ `Optional.empty()` になります。2 行以上が合うと `jakarta.persistence.NonUniqueResultException` を投げます。合う行ならどれでもよいときは `first()` を使ってください。

```kotlin
fun find(id: Long): Order? =
    qb.from(Order::class.java).where { it.id == id }.one().orElse(null)
// HQL: select e0 from Order e0 where e0.id = :p1

// two or more rows: an exception (use first() if any row is fine)
assertThrows<NonUniqueResultException> { qb.from(Order::class.java).where { it.active }.one() }
```

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `one`:

```sql
-- H2, Oracle
select …
from orders o1_0
where o1_0.id=?
fetch first ? rows only

-- MariaDB
select …
from orders o1_0
where o1_0.id=?
limit ?
```

`one()` はデータベースに最大 2 行を求めます。2 行目があるかどうかを知るにはそれで十分です。そのため SQL には `fetch first ? rows only`（H2、Oracle）または `limit ?`（MariaDB）が付き、`?` は 2 です。ここでは順序は関係ないので、`order by` はありません。

<a id="count-and-a-paged-screen"></a>
### `count()` とページ付きの画面

`count()` は条件に合う行の数を返します。where 段階にしかないので、`limit` と `offset` が結果を変えることはありません。

ページ付きの画面では、where 段階のクエリを値に入れておきます。合計を得るにはそれに `count()` を呼びます。次に、ページを得るには同じ値に `orderBy { }.limit(...).toList()` を呼びます。

```kotlin
val active = qb.from(Order::class.java).where { it.active }
val total: Long = active.count()                       // every matching row
val page = active.orderBy { o, by -> by.desc(o.price) }.limit(20).toList()
// count HQL: select count(e0) from Order e0 where e0.active = true
```

**データベースごとの SQL（Java のテスト実行から。Kotlin も同じ HQL になります）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `count`:

```sql
-- H2, MariaDB, Oracle (same)
select count(o1_0.id)
from orders o1_0
where o1_0.active=true
```

Hibernate は `count(e0)` を `select count(o1_0.id)` に変えます。つまり id の列を数えます。件数のクエリにはページングはありません。

<a id="exists"></a>
### `exists()`

`exists()` は、合う行が 1 つ以上あれば `true` を返します。`select 1` を実行し、読むのは最大 1 行です。エンティティは読み込みません。

```kotlin
val anyHighStatus: Boolean = qb.from(Order::class.java).where { it.status == 3 }.exists()
// HQL: select 1 from Order e0 where e0.status = 3
```

テスト済みの例（Java のテストの `exists`）は、Kotlin の例のエンティティにはない enum のプロパティを使っています。その SQL も同じ形です。H2 と Oracle では `select 1 from orders o1_0 where … fetch first ? rows only`、MariaDB では `… limit ?` です。`?` は 1 です。[このページの Java 版](../java/02-getting-started.md#exists) を参照してください。

<a id="toset"></a>
### `toSet()`

`toSet()` は `toList()` と同じようにクエリを実行し、`SequencedSet<T>` を返します（決定 D42）。
これは `LinkedHashSet` なので、要素は行の順序を保ちます。`orderBy { }` があれば、ソートの順序です。

```kotlin
val byPrice: SequencedSet<Order> = qb.from(Order::class.java)
    .orderBy { o, by -> by.desc(o.price) }
    .toSet()
```

SQL は `toList()` と同じです。lambdaQL は `distinct` を **付けません**:

- エンティティの行には主キーがあります。そのため、1 つのテーブルだけのクエリでも同じ行が 2 回返ることはありません。
- lambdaQL が結合するのは to-one の関連だけです。コレクションは `exists` サブクエリになります。そのため、結合で行が繰り返されることはありません。
- したがって `distinct` は結果を変えず、コストを増やすだけです。（データベースの制限が理由ではありません。Oracle 23.26 は LOB の列でも `distinct` を受け付けます。これは実測です。）

値を返すクエリ（`select`）では、`distinct()` と `toSet()` が `select distinct` を使います（[6.7](06-select-group.md#67-distinct-paging-and-results) を参照）。

**サイズのチェック。** Set が行数より小さいと、`toSet()` は `IllegalStateException` を投げます。これは次の場合に起きます:

- エンティティの `equals` / `hashCode` が、違う 2 つの行を等しいと判断する
- `@Id` が一意でない（たとえばビューにマッピングしたエンティティ）

このチェックがないと、行が何の兆候もなく失われます。このような場合は `toList()` を使ってください。
Kotlin の `data class` は、プライマリコンストラクターのプロパティだけを比べます。`@Id` がその中にないと、違う 2 つの行が等しくなることがあります。

```kotlin
// Tag.equals compares only the name: two different rows would become one element, so toSet() refuses
assertThrows<IllegalStateException> { qb.from(Tag::class.java).toSet() }
val tags: List<Tag> = qb.from(Tag::class.java).toList()   // use toList() instead
```

`toSet()` についての Kotlin のテストはまだありません。同じ呼び出しを Java で書いたものは [`ResultQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ResultQueryTest.java) でテストしています（リージョン `to-set` と `to-set-check`、H2、MariaDB、Oracle）。[このページの Java 版](../java/02-getting-started.md#toset) を参照してください。

<a id="things-to-know"></a>
## 知っておくこと

| ルール | 例 |
|---|---|
| ラムダリテラルかプロパティ参照を渡します。 | `where { it.active }`、`where(Order::active)` |
| ラムダを先に Kotlin の関数型の変数に入れないでください。 | `val f: (Order) -> Boolean = { it.active }; where(f)` は拒否されます（KC4）。 |
| クエリのラムダを `inline fun` の中で作らないでください。 | 拒否されます（KC2）。ヘルパーを inline でなくするか、呼び出し側からラムダを渡してください。 |
| ラムダは 1 つの式でなければなりません（条件、ソート、選択の場合）。 | ラムダの中のループやローカルの `val`/`var` は拒否されます。 |
| 実行されなければならないコードをラムダに入れないでください。ラムダは実行されません。 | `println(...)` のような副作用のある呼び出しは拒否されます。 |
| Java 21〜23 で実行する場合は、新しい JDK でコンパイルしたクラスに対応する ASM のバージョンが必要です。 | lambdaQL は Java 21〜23 では ASM 9.11.1 を使います（Java 28 までのクラスファイルを読めます）。Java 24 以上では、実行中の JDK の `java.lang.classfile` で読むので、ASM のバージョンとは関係ありません。 |

Kotlin の制限はすべて [9. Kotlin の注意点](09-kotlin-notes.md#914-kotlin-limits-kc1-to-kc21) にあります。

<a id="next"></a>
## 次へ

- [3. 条件（`where`）](03-where.md): すべての演算子と null のルール。
- [4. ソートとページング](04-order-paging.md)

---

[Kotlin トラック](README.md) | 前: [1. 概要](../01-overview.md) | 次: [3. 条件](03-where.md) | [このページの Java 版](../java/02-getting-started.md)
