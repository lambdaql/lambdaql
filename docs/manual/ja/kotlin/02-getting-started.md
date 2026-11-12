<a id="2-getting-started-kotlin"></a>
# 2. はじめよう（Kotlin）

> [!NOTE]
> **状況:** 現在、lambdaQL をソースからビルドし、テストを実行できます。
> `where` の条件と `toList()` は最初から最後まで動きます。H2、MariaDB、Oracle で **Java** のラムダを使ってテスト済みです。Kotlin のラムダはまだテストしていません。
> ソートとページング（`orderBy`、`limit`、`offset`）の中核部分も動きます（これも Java のラムダでのみテスト済み）。
> サブクエリ、`select`、update/insert/delete、プロシージャ、Spring モジュールは **設計済みで、まだ実装されていません**。
> 成果物（アーティファクト）はまだ公開していません。下のアーティファクト名は一時的なものです。

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
| JPA プロバイダー | Hibernate ORM 6.6 | lambdaQL は Hibernate 6 の HQL を組み立てます。設計は 6.6.5 で検証しました。他の JPA プロバイダーはサポートしていません。 |
| Jakarta Persistence | 3.1 | Hibernate 6.6 に含まれています。 |
| データベース | H2 2.3、MariaDB 10.11、Oracle 23.26 (23ai) | この 3 つで検証しました。他のデータベースでも動くかもしれませんが、テストはしていません。 |
| Spring Boot | 3.4 | 任意です。プロジェクトのテストは Spring Boot 3.4 を使います。 |
| Kotlin ランタイム | – | lambdaQL は `kotlin-stdlib` や `kotlin-reflect` を依存関係に追加しません。 |

<a id="build-from-source"></a>
## ソースからビルドする

```bash
git clone https://github.com/codebit0/lambdaql.git
cd lambdaql
./gradlew :query:build
```

- Gradle ラッパー（8.10）は **JDK 23 以下** でしか動きません。JDK 21 を使ってください。
- リポジトリには `gradle/gradle-daemon-jvm.properties` があるので、Gradle デーモンは Java 21 を使います。
- IntelliJ IDEA では、**Settings > Build Tools > Gradle > Gradle JVM** を Java 21 の JDK に設定してください。

ユニットテストを実行します。

```bash
./gradlew :query:test
```

[`SelectQueryTest.java`](../../../../query/src/test/java/org/lambdaql/query/SelectQueryTest.java) のテストで、lambdaQL が現在何をできるかが分かります。`qb.from(...).where(...)`、HQL、パラメーター、そして H2、MariaDB、Oracle での行です。[`ConditionDecodeTest.java`](../../../../query/src/test/java/org/lambdaql/engine/decode/ConditionDecodeTest.java) は内部の条件デコーダーをテストします。これらのテストは Java で書かれています。Kotlin のテストは予定です（`query/src/test/kotlin`）。

<a id="use-the-local-build-in-your-project"></a>
### ローカルビルドを自分のプロジェクトで使う

jar をローカルの Maven リポジトリに公開します。

```bash
./gradlew :query:publishToMavenLocal
```

それから、自分のプロジェクトに追加します（Gradle Kotlin DSL）。

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("org.lambdaql:lambadaql-query:1.0.0")
}
```

Maven:

```xml
<dependency>
  <groupId>org.lambdaql</groupId>
  <artifactId>lambadaql-query</artifactId>
  <version>1.0.0</version>
</dependency>
```

> [!WARNING]
> この座標は **一時的** なものです。
> `lambadaql` という名前にはタイプミスがあり、最初のリリースまでに直します。
> グループ、名前、バージョンは変わることがあります。lambdaQL が公開されたら、このページをもう一度確認してください。

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

`QueryBuilder` が入り口です。JPA の `EntityManagerFactory` が必要です。

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

Spring Boot はすでに `EntityManagerFactory` を作っています。それを注入して、`QueryBuilder` の Bean を作ります。

```kotlin
@Configuration
class LambdaQlConfig {

    @Bean
    fun queryBuilder(entityManagerFactory: EntityManagerFactory) = QueryBuilder(entityManagerFactory)
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

- モジュール `lambdaql-spring-data-jpa` がリリースされるまでは、Bean は自分で作ります。リリース後は、そのモジュールが Bean を作ります。
- Spring のトランザクションの中では、lambdaQL はそのトランザクションの共有 `EntityManager` を使います。
- Spring Data のリポジトリの中で lambdaQL のクエリを書く方法は、[12. Spring Data JPA](12-spring-data-jpa.md) を参照してください。

<a id="your-first-query"></a>
## 最初のクエリ

```kotlin
val orders = qb.from(Order::class.java)
    .where { it.status == 1 && it.price!! > 100.0 }
    .toList()
// HQL: select e0 from Order e0 where e0.status = 1 and e0.price > 100.0
```

**SQL ブロックの読み方。** `…` は省略した select リスト（エンティティのすべてのカラム）で、`?` はバインドパラメーターです。各行は [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java) から取っています。このテストは各クエリを Hibernate 6.6.5 で H2 2.3、MariaDB 10.11、Oracle 23.26 に対して実行し、Hibernate の SQL が変わると失敗します。 このテストは Java のラムダを使います。意味が同じ Kotlin のラムダは同じ HQL になるので、SQL も同じです。

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
- 今決まっている結果メソッドは `toList()` だけです。他の結果メソッドは予定です。

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
-- H2
select …
from orders o1_0
where o1_0.active=true and (o1_0.status=1 or o1_0.status=2)

-- MariaDB, Oracle
select …
from orders o1_0
where o1_0.active=1 and (o1_0.status=1 or o1_0.status=2)
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
null ガードが customer のない注文を残す場合（`it.customer == null || ...`）、lambdaQL は `left join` を使います。[3.10](03-where.md#310-association-paths) を参照してください。

<a id="things-to-know"></a>
## 知っておくこと

| ルール | 例 |
|---|---|
| ラムダリテラルかプロパティ参照を渡します。 | `where { it.active }`、`where(Order::active)` |
| ラムダを先に Kotlin の関数型の変数に入れないでください。 | `val f: (Order) -> Boolean = { it.active }; where(f)` は拒否されます（KC4）。 |
| クエリのラムダを `inline fun` の中で作らないでください。 | 拒否されます（KC2）。ヘルパーを inline でなくするか、呼び出し側からラムダを渡してください。 |
| ラムダは 1 つの式でなければなりません（条件、ソート、選択の場合）。 | ラムダの中のループやローカルの `val`/`var` は拒否されます。 |
| 実行されなければならないコードをラムダに入れないでください。ラムダは実行されません。 | `println(...)` のような副作用のある呼び出しは拒否されます。 |
| 新しい JDK のバージョンには、対応する ASM のバージョンが必要です。 | lambdaQL は ASM 9.11.1 を使います（Java 28 までのクラスファイルを読めます）。 |

Kotlin の制限はすべて [9. Kotlin の注意点](09-kotlin-notes.md#914-kotlin-limits-kc1-to-kc21) にあります。

<a id="next"></a>
## 次へ

- [3. 条件（`where`）](03-where.md): すべての演算子と null のルール。
- [4. ソートとページング](04-order-paging.md)

---

[Kotlin トラック](README.md) | 前: [1. 概要](../01-overview.md) | 次: [3. 条件](03-where.md) | [このページの Java 版](../java/02-getting-started.md)
