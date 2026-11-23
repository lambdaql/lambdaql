<a id="2-getting-started-java"></a>
# 2. はじめよう（Java）

[Java トラック](README.md) | [マニュアルのホーム](../README.md) | 前: [1. 概要](../01-overview.md) | 次: [3. 条件](03-where.md) | [このページの Kotlin 版](../kotlin/02-getting-started.md)

共通ページ: [1. 概要](../01-overview.md) · [11. エラーと言語](../11-errors-and-languages.md)

---

<a id="requirements"></a>
## 必要な環境

| 項目 | バージョン | 注意 |
|---|---|---|
| Java | 21 | lambdaQL のビルドと利用に必要です。 |
| JPA プロバイダー | Hibernate ORM 6.6 または 7.4 | lambdaQL は Hibernate の HQL を組み立てます。6.6.53（Spring Boot 3.5.16）と 7.4.13（Spring Boot 4.1.2）でテストしました。他の JPA プロバイダーはサポートしていません。[13. Spring Boot 3 と 4](../13-spring-boot-versions.md) を参照してください。 |
| Jakarta Persistence | 3.1 または 3.2 | Hibernate 6.6 は 3.1、7.4 は 3.2 を使います。 |
| データベース | H2 2.3、MariaDB 10.11、MySQL 8.4、PostgreSQL 17、Oracle 23.26 (23ai) | この 5 つで検証しました。マニュアルのデータベースごとの SQL は H2、MariaDB、Oracle のものです。他のデータベースでも動くかもしれませんが、テストはしていません。 |
| Spring Boot | 3.5 または 4.1 | 任意です。プロジェクトのテストは Spring Boot 3.5.16 と 4.1.2 を使います。 |
| コンパイラー | javac | ECJ（Eclipse のコンパイラー）でコンパイルしたラムダは保証しません。[9. Java の注意点](09-java-notes.md#912-compiler-and-build) を参照してください。 |

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
- ビルドの主なモジュールは `query`（アーティファクト `lambdaql-core`）、`spring-data-jpa`（`lambdaql-spring-data-jpa`）、`spring-boot-starter-boot3`・`spring-boot-starter-boot4`（`lambdaql-spring-boot-starter-boot3`・`-boot4`）です。`lambdaql-core` と `lambdaql-spring-data-jpa` は Spring Boot 3・4 共通の jar で、スターターだけが Boot のバージョンごとに分かれます（[13. Spring Boot 3 と 4](../13-spring-boot-versions.md)）。Spring のモジュールは [12. Spring Data JPA](12-spring-data-jpa.md) で説明します。

ユニットテストを実行します。

```bash
./gradlew :query:test
```

これらのテストで、lambdaQL が現在何をできるかが分かります。`SelectQueryTest` はユーザー向け API を使い、その他のテストは内部クラスをチェックします。

| テストファイル | 分かること |
|---|---|
| [`SelectQueryTest.java`](../../../../query/src/test/java/org/lambdaql/query/SelectQueryTest.java) | `qb.from(...).where(...)`: HQL、パラメーター、行。デフォルトでは H2 で実行します。他のデータベースには `-Dlql.db=mariadb` か `-Dlql.db=oracle` を付けます |
| [`LoadExamplesTest.java`](../../../../query/src/test/java/org/lambdaql/engine/load/LoadExamplesTest.java) | ラムダの読み込み: キャプチャした値、`this` をキャプチャするラムダ、メソッド参照、`Serializable` でないラムダ |
| [`ConditionDecodeTest.java`](../../../../query/src/test/java/org/lambdaql/engine/decode/ConditionDecodeTest.java) | 条件のデコード: 比較、キャプチャした値、`&&`、`\|\|`、`!` |
| [`IrExamplesTest.java`](../../../../query/src/test/java/org/lambdaql/engine/ir/IrExamplesTest.java) | 内部の条件ツリー |
| [`HostEvaluatorTest.java`](../../../../query/src/test/java/org/lambdaql/engine/sem/HostEvaluatorTest.java) | キャプチャした値を Java 側で計算する、数値の変換 |

たとえば `ConditionDecodeTest` は、`o -> o.getStatus() == 1 || o.getStatus() == 2 || o.getStatus() == 3` が、3 つの比較を持つ 1 つの `OR` に、ソースの順番どおりにデコードされることをチェックします。

<a id="use-the-local-build-in-your-project"></a>
### ローカルビルドを自分のプロジェクトで使う

3 つのモジュールの jar をローカルの Maven リポジトリに公開します。

```bash
./gradlew publishToMavenLocal
```

それから、`lambdaql-core` を自分のプロジェクトに追加します。

Gradle（Groovy DSL）:

```groovy
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation 'org.lambdaql:lambdaql-core:1.0.0'
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

Spring Boot を使う場合は Boot のバージョンに合った `lambdaql-spring-boot-starter-boot4`（`-boot3`）を、Spring Boot なしで Spring Data JPA だけを使う場合は `lambdaql-spring-data-jpa` を、代わりに使ってください。どちらも `lambdaql-core` を持ち込みます（[12.2](12-spring-data-jpa.md#122-modules) を参照）。

> [!WARNING]
> バージョン `1.0.0` はリポジトリの `gradle.properties` で定義した値で、最初のリリースまでに変わることがあります。
> lambdaQL が Maven Central に公開されたら、このページをもう一度確認してください。

<a id="recommended-compiler-option--parameters"></a>
### 推奨するコンパイラーオプション: `-parameters`

`-parameters` を付けてコンパイルしてください。そうすると、lambdaQL がプロシージャのスタブメソッドのパラメーター名を見て、入れ替わった引数を見つけられます（[8.4](08-procedures-functions.md#84-warning-argument-order) を参照）。

Gradle（Groovy DSL）:

```groovy
tasks.withType(JavaCompile).configureEach {
    options.compilerArgs << '-parameters'
}
```

Maven（`maven-compiler-plugin`）:

```xml
<configuration>
  <parameters>true</parameters>
</configuration>
```

<a id="example-entities"></a>
## 例で使うエンティティ

Java トラックの例では、すべて次のエンティティを使います。

```java
@Entity
@Table(name = "orders")
public class Order {
    @Id @GeneratedValue
    private Long id;
    private Double price;
    private Integer status;
    private boolean active;              // getter: isActive()
    private String description;
    @ManyToOne(fetch = FetchType.LAZY)
    private Customer customer;
    private LocalDateTime updateAt;
    @OneToMany(mappedBy = "order")
    private List<Item> items;

    // getter と setter
}

@Entity
public class Customer {
    @Id @GeneratedValue
    private Long id;
    private String name;
    private Integer grade;

    // getter と setter
}

@Entity
public class Item {
    @Id @GeneratedValue
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    private Order order;
    private Double price;
    private int qty;

    // getter と setter
}
```

エンティティのルール:

- lambdaQL は **getter**（`o.getPrice()`、`o.isActive()`）を読みます。JPA Metamodel を使って、それを JPA の属性にマッピングします。フィールドを直接読むことはありません。
- 生成コードは不要です（Q クラスも静的メタモデルも要りません）。
- ほとんどのフィールドはボクシングされた型（`Long`、`Double`、`Integer`）です。これは `==` に関係します。[9. Java の注意点](09-java-notes.md#92-boxing-and-unboxing) を参照してください。

<a id="create-a-querybuilder"></a>
## QueryBuilder を作る

`QueryBuilder` が入り口です。JPA の `EntityManagerFactory` か `EntityManager` が必要です。

```java
QueryBuilder qb = new QueryBuilder(entityManagerFactory);
```

`QueryBuilder` は 1 つ作って再利用してください。クエリごとに新しく作らないでください。

<a id="with-plain-jpa"></a>
### 素の JPA で

```java
EntityManagerFactory emf = Persistence.createEntityManagerFactory("my-unit");
QueryBuilder qb = new QueryBuilder(emf);
```

<a id="with-spring-boot"></a>
### Spring Boot で

`lambdaql-spring-boot-starter-boot4`（`-boot3`）を使うと、Spring Boot が `QueryBuilder` の Bean を作ります（[12.8](12-spring-data-jpa.md#128-the-querybuilder-bean) を参照）。
スターターを使わない場合は、Bean を自分で作ります。自動構成と同じように、Spring の共有 `EntityManager` を使ってください。

```java
@Configuration
public class LambdaQlConfig {

    @Bean
    public QueryBuilder queryBuilder(EntityManagerFactory entityManagerFactory) {
        return new QueryBuilder(SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory));
    }
}
```

あとは、必要な場所に `QueryBuilder` を注入します。

```java
@Service
public class OrderService {

    private final QueryBuilder qb;

    public OrderService(QueryBuilder qb) {
        this.qb = qb;
    }

    @Transactional(readOnly = true)
    public List<Order> activeOrders() {
        return qb.from(Order.class)
                 .where(o -> o.isActive())
                 .toList();
    }
}
```

- 共有 `EntityManager` は現在のトランザクションに結び付いています。`@Transactional` の中では、lambdaQL は同じトランザクションで実行されます。
- Spring のアプリケーションでは、`EntityManagerFactory` を受け取るコンストラクターを使わないでください。このコンストラクターで作ったビルダーはクエリごとに自分の `EntityManager` を開き、その `EntityManager` は Spring のトランザクションに参加しません。
- Spring Data のリポジトリの中で lambdaQL のクエリを書く方法は、[12. Spring Data JPA](12-spring-data-jpa.md) を参照してください。

<a id="your-first-query"></a>
## 最初のクエリ

```java
List<Order> orders = qb.from(Order.class)
    .where(o -> o.getStatus() == 1 && o.getPrice() > 100)
    .toList();
// HQL: select e0 from Order e0 where e0.status = 1 and e0.price > 100.0
```

**SQL ブロックの読み方。** `…` は省略した select リスト（エンティティのすべてのカラム）で、`?` はバインドパラメーターです。各行は [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java) から取っています。このテストは各クエリを Hibernate 7.4 で H2 2.3、MariaDB 10.11、Oracle 23.26 に対して実行し、Hibernate の SQL が変わると失敗します。

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `to-hql`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>100.0
```

- `qb.from(Order.class)` で `Order` に対するクエリを始めます。
- `where(...)` は条件のラムダを 1 つ受け取ります。
- `toList()` はクエリを実行し、`List<Order>` を返します。
- `toList()` はすべての行を返します。他の結果の関数（`first()`、`one()`、`count()`、`exists()`、`toSet()`）は [結果を受け取る](#getting-the-result) にあります。

なぜ `1` と `100.0` はバインドパラメーターではなく、HQL に直接書かれるのでしょうか。
これらは **ラムダの中に書いた定数** だからです。lambdaQL は定数（数値、文字列、boolean、`null`、enum 定数）をリテラルとして HQL に書きます。
`price` は `Double` なので、javac は `100` を小数 `100.0` としてコンパイルします。
ラムダの外から来る値は違います。それらは常にバインドパラメーターになります（次の節と [3.3](03-where.md#33-basic-form) を参照）。

<a id="use-values-from-outside-the-lambda"></a>
### ラムダの外の値を使う

ラムダは、ローカル変数、フィールド、メソッドのパラメーターを使えます。これらは **キャプチャした値** です。
lambdaQL はそれをバインドパラメーターに変えます。クエリを実行するたびに新しい値を読みます。

```java
public List<Order> expensive(int status, double minPrice) {
    return qb.from(Order.class)
             .where(o -> o.getStatus() == status && o.getPrice() >= minPrice)
             .toList();
    // HQL: select e0 from Order e0 where e0.status = :p1 and e0.price >= :p2
}
```

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `start-captured`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.status=? and o1_0.price>=?
```

<a id="many-where-calls"></a>
### 複数の `where` 呼び出し

`where` は 2 回以上呼べます。lambdaQL は条件を `AND` でつなぎます。

```java
qb.from(Order.class)
  .where(o -> o.isActive())
  .where(o -> o.getStatus() == 1 || o.getStatus() == 2)
  .toList();
// HQL: select e0 from Order e0
//      where e0.active = true and (e0.status = 1 or e0.status = 2)
```

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `start-many-where`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.active=true and (o1_0.status=1 or o1_0.status=2)
```

条件を少しずつ追加するときに便利です。

```java
var query = qb.from(Order.class).where(o -> o.isActive());
if (minPrice != null) {
    query = query.where(o -> o.getPrice() >= minPrice);
}
List<Order> orders = query.toList();
```

ビルダーの変数には `var` を使ってください。ビルダーの型名はまだ確定していません。

<a id="paths-to-other-entities"></a>
### 他のエンティティへのパス

`o.getCustomer().getName()` のようなパスは、結合したエンティティのカラムを指定します。
`where` の中では、lambdaQL はデフォルトで内部結合の `join` を追加します。customer のない注文は結果に **入りません**。

```java
qb.from(Order.class)
  .where(o -> o.getCustomer().getName().equals(name))
  .toList();
// HQL: select e0 from Order e0 join e0.customer j1 where j1.name = :p1
```

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `inner-join`:

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name=?
```

customer のない注文を探すには、関連を比較してください。`o.getCustomer() == null` は `e0.customer is null` になります（結合なし）。
null ガード（`o.getCustomer() == null || ...`）を書いても、customer のない注文は残りません。パスは内部結合だからです（決定 D48）。
そのような注文を残すには `leftJoin` を使ってください（[6.8](06-select-group.md#68-explicit-joins)）。
[3.10](03-where.md#310-association-paths) を参照してください。

<a id="getting-the-result"></a>
## 結果を受け取る

`toList()` だけがクエリを実行する方法ではありません。lambdaQL には結果を得る関数が 6 つあります（決定 D38、D42）。

| 関数 | 戻り値 | 呼べる場所 | 何をするか |
|---|---|---|---|
| `toList()` | `List<T>` | `where` の後、および `orderBy` / `limit` / `offset` の後 | すべての行（または 1 ページ分）を返します。 |
| `first()` | `Optional<T>` | `where` の後、および `orderBy` / `limit` / `offset` の後 | 最初の行を返します。読むのは 1 行だけです。 |
| `one()` | `Optional<T>` | `where` の後だけ | ただ 1 つの行を返します。2 行以上が合うと `NonUniqueResultException` を投げます。読むのは最大 2 行です。 |
| `count()` | `long` | `where` の後だけ | 条件に合う行の数を返します。 |
| `exists()` | `boolean` | `where` の後だけ | 合う行があれば `true` を返します。読むのは 1 行だけで、エンティティは読み込みません。 |
| `toSet()` | `SequencedSet<T>` | `where` の後、および `orderBy` / `limit` / `offset` の後 | すべての行（または 1 ページ分）を、行の順序を保つ Set として返します。Set が行数より小さいと `IllegalStateException` を投げます。 |

「`where` の後」とは、`qb.from(...)` と `where(...)` で得られるクエリ（型は `SelectQuery`）のことです。このマニュアルではこれを **where 段階** と呼びます。`where` なしで、`qb.from(...)` のすぐ後に呼ぶこともできます。

`orderBy`、`limit`、`offset` の後、クエリの型は `OrderedSelectQuery` になります。そこで使える結果の関数は `toList()`、`toSet()`、`first()` だけです。`one()`、`count()`、`exists()` はそこではコンパイルできません。
これは意図的です（決定 D38）。`limit(10).count()` は「すべての行」とも「最大 10 行」とも読めるので、lambdaQL はこれを許しません。

下の例は [`ResultQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ResultQueryTest.java) からコピーしたものです。テストはこれらを H2、MariaDB、Oracle で実行し、結果を Java で計算した同じ答えと比べます。
テストでは [3.3](03-where.md#33-basic-form) のテスト用エンティティを使います。`price` は `double` なのでソートに `nulls` の部分がなく、`Order` には enum の `kind` があります。

<a id="first"></a>
### `first()`

`orderBy` がない場合、「最初」とは id が最も小さい行のことです（ルール S9）。だから、いつも同じ行が返ります。
順序を選ぶには、先に `orderBy(...)` を呼びます。すると `first()` はその順序で最初の行を返します。`offset` はそのまま使われます。`orderBy(...).offset(1).first()` は 2 番目の行を返します。
合う行がなければ、結果は `Optional.empty()` です。

**(テスト済み)** [`ResultQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ResultQueryTest.java)、リージョン `first`:

```java
Optional<Order> cheapest = qb.from(Order.class)
        .orderBy((o, by) -> by.asc(o.getPrice()))
        .first();
// without orderBy, "first" is the row with the smallest id
Optional<Order> firstActive = qb.from(Order.class).where(o -> o.isActive()).first();
Optional<Order> none = qb.from(Order.class).where(o -> o.getStatus() == 9).first();
```

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `first`（`cheapest`）:

```sql
-- H2, Oracle
select …
from orders o1_0
order by o1_0.price,o1_0.id
fetch first ? rows only

-- MariaDB
select …
from orders o1_0
order by o1_0.price,o1_0.id
limit ?
```

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `first#2`（`firstActive`）:

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

<a id="one"></a>
### `one()`

条件に合う行が多くても 1 つしかないとき（たとえばユニークキー）に `one()` を使います。
行がなければ `Optional.empty()` になります。2 行以上が合うと `jakarta.persistence.NonUniqueResultException` を投げます。合う行ならどれでもよいときは `first()` を使ってください。

**(テスト済み)** [`ResultQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ResultQueryTest.java)、リージョン `one`:

```java
long id = knownId;
Optional<Order> order = qb.from(Order.class).where(o -> o.getId() == id).one();
Optional<Order> missing = qb.from(Order.class).where(o -> o.getId() == -1L).one();
// two or more rows: an exception (use first() if any row is fine)
assertThrows(NonUniqueResultException.class, () -> qb.from(Order.class).where(o -> o.isActive()).one());
```

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `one`:

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

ページ付きの画面では、where 段階のクエリを変数に入れておきます。合計を得るにはそれに `count()` を呼びます。次に、ページを得るには同じ変数に `orderBy(...).limit(...).toList()` を呼びます。

**(テスト済み)** [`ResultQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ResultQueryTest.java)、リージョン `count`:

```java
SelectQuery<Order> active = qb.from(Order.class).where(o -> o.isActive());
long total = active.count();                                   // every matching row
List<Order> page = active.orderBy((o, by) -> by.desc(o.getPrice())).limit(2).toList();
```

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `count`:

```sql
-- H2, MariaDB, Oracle (same)
select count(o1_0.id)
from orders o1_0
where o1_0.active=true
```

HQL は `select count(e0) from Order e0 where e0.active = true` です。Hibernate は `count(e0)` を `select count(o1_0.id)` に変えます。つまり id の列を数えます。件数のクエリにはページングはありません。

<a id="exists"></a>
### `exists()`

`exists()` は、合う行が 1 つ以上あれば `true` を返します。`select 1` を実行し、読むのは最大 1 行です。エンティティは読み込みません。

**(テスト済み)** [`ResultQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ResultQueryTest.java)、リージョン `exists`:

```java
boolean anyRush = qb.from(Order.class).where(o -> o.getKind() == Order.Kind.RUSH).exists();
boolean anyStatus9 = qb.from(Order.class).where(o -> o.getStatus() == 9).exists();
```

**データベースごとの SQL（テスト済み）** — `expected-sql/` の [`h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/h2.sql)、[`mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/mariadb.sql)、[`oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/oracle.sql)、例 `exists`（`anyRush`）:

```sql
-- H2, Oracle
select 1
from orders o1_0
where o1_0.kind='RUSH'
fetch first ? rows only

-- MariaDB
select 1
from orders o1_0
where o1_0.kind='RUSH'
limit ?
```

HQL は `select 1 from Order e0 where e0.kind = org.lambdaql.query.Order$Kind.RUSH` です。SQL は定数 `1` だけを選ぶので列は読まず、ページング（`?` は 1）によって最初の行で止まります。

<a id="toset"></a>
### `toSet()`

`toSet()` は `toList()` と同じようにクエリを実行し、`SequencedSet<T>` を返します（決定 D42）。
これは `LinkedHashSet` なので、要素は行の順序を保ちます。`orderBy` があれば、ソートの順序です。

**(テスト済み)** [`ResultQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ResultQueryTest.java)、リージョン `to-set`:

```java
java.util.SequencedSet<Order> byPrice = qb.from(Order.class)
        .orderBy((o, by) -> by.desc(o.getPrice()))
        .toSet();
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

次のテストでは、`Tag.equals` は名前だけを比べ、テーブルには同じ名前の行が 2 つあります。

**(テスト済み)** [`ResultQueryTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ResultQueryTest.java)、リージョン `to-set-check`:

```java
// Tag.equals compares only the name: two different rows would become one element, so toSet() refuses
assertThrows(IllegalStateException.class, () -> qb.from(Tag.class).toSet());
assertEquals(2, qb.from(Tag.class).toList().size());
```

<a id="things-to-know"></a>
## 知っておくこと

| ルール | 例 |
|---|---|
| ラムダかメソッド参照を使います。匿名クラスは拒否されます。 | `where(o -> o.isActive())`、`where(Order::isActive)` |
| ラムダは 1 つの式でなければなりません（条件、ソート、選択の場合）。 | ラムダの中のループやローカル変数は拒否されます。 |
| 実行されなければならないコードをラムダに入れないでください。ラムダは実行されません。 | `System.out.println(...)` のような副作用のある呼び出しは拒否されます。 |
| javac でコンパイルします。 | ECJ の出力は保証しません。 |
| Java 21〜23 で実行する場合は、新しい JDK でコンパイルしたクラスに対応する ASM のバージョンが必要です。 | lambdaQL は Java 21〜23 では ASM 9.11.1 を使います（Java 28 までのクラスファイルを読めます）。もっと新しい JDK でコンパイルする場合は、ASM を更新してください。Java 24 以上では、実行中の JDK の `java.lang.classfile` で読むので、ASM のバージョンとは関係ありません。 |

Java の詳細は [9. Java の注意点](09-java-notes.md) にあります。

<a id="next"></a>
## 次へ

- [3. 条件（`where`）](03-where.md): すべての演算子と NULL のルール。
- [4. ソートとページング](04-order-paging.md)

---

[Java トラック](README.md) | 前: [1. 概要](../01-overview.md) | 次: [3. 条件](03-where.md) | [このページの Kotlin 版](../kotlin/02-getting-started.md)
