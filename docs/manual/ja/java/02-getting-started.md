<a id="2-getting-started-java"></a>
# 2. はじめよう（Java）

> [!NOTE]
> **状況:** 現在、lambdaQL をソースからビルドし、テストを実行できます。
> `where` の条件と `toList()` は最初から最後まで動きます。H2、MariaDB、Oracle でテスト済みです。
> ソートとページング（`orderBy`、`limit`、`offset`）の中核部分も動きます。
> サブクエリ、`select`、update/insert/delete、プロシージャ、Spring モジュールは **設計済みで、まだ実装されていません**。
> 成果物（アーティファクト）はまだ公開していません。下のアーティファクト名は一時的なものです。

[Java トラック](README.md) | [マニュアルのホーム](../README.md) | 前: [1. 概要](../01-overview.md) | 次: [3. 条件](03-where.md) | [このページの Kotlin 版](../kotlin/02-getting-started.md)

共通ページ: [1. 概要](../01-overview.md) · [11. エラーと言語](../11-errors-and-languages.md)

---

<a id="requirements"></a>
## 必要な環境

| 項目 | バージョン | 注意 |
|---|---|---|
| Java | 21 | lambdaQL のビルドと利用に必要です。 |
| JPA プロバイダー | Hibernate ORM 6.6 | lambdaQL は Hibernate 6 の HQL を組み立てます。設計は 6.6.5 で検証しました。他の JPA プロバイダーはサポートしていません。 |
| Jakarta Persistence | 3.1 | Hibernate 6.6 に含まれています。 |
| データベース | H2 2.3、MariaDB 10.11、Oracle 23.26 (23ai) | この 3 つで検証しました。他のデータベースでも動くかもしれませんが、テストはしていません。 |
| Spring Boot | 3.4 | 任意です。プロジェクトのテストは Spring Boot 3.4 を使います。 |
| コンパイラー | javac | ECJ（Eclipse のコンパイラー）でコンパイルしたラムダは保証しません。[9. Java の注意点](09-java-notes.md#912-compiler-and-build) を参照してください。 |

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

jar をローカルの Maven リポジトリに公開します。

```bash
./gradlew :query:publishToMavenLocal
```

それから、自分のプロジェクトに追加します。

Gradle（Groovy DSL）:

```groovy
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation 'org.lambdaql:lambadaql-query:1.0.0'
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

`QueryBuilder` が入り口です。JPA の `EntityManagerFactory` が必要です。

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

Spring Boot はすでに `EntityManagerFactory` を作っています。それを注入して、`QueryBuilder` の Bean を作ります。

```java
@Configuration
public class LambdaQlConfig {

    @Bean
    public QueryBuilder queryBuilder(EntityManagerFactory entityManagerFactory) {
        return new QueryBuilder(entityManagerFactory);
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

- モジュール `lambdaql-spring-data-jpa` がリリースされるまでは、Bean は自分で作ります。リリース後は、そのモジュールが Bean を作ります。
- Spring のトランザクションの中では、lambdaQL はそのトランザクションの共有 `EntityManager` を使います。
- Spring Data のリポジトリの中で lambdaQL のクエリを書く方法は、[12. Spring Data JPA](12-spring-data-jpa.md) を参照してください。

<a id="your-first-query"></a>
## 最初のクエリ

```java
List<Order> orders = qb.from(Order.class)
    .where(o -> o.getStatus() == 1 && o.getPrice() > 100)
    .toList();
// HQL: select e0 from Order e0 where e0.status = 1 and e0.price > 100.0
```

**SQL ブロックの読み方。** `…` は省略した select リスト（エンティティのすべてのカラム）で、`?` はバインドパラメーターです。各行は [`SqlByDatabaseTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SqlByDatabaseTest.java) から取っています。このテストは各クエリを Hibernate 6.6.5 で H2 2.3、MariaDB 10.11、Oracle 23.26 に対して実行し、Hibernate の SQL が変わると失敗します。

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
- 今決まっている結果メソッドは `toList()` だけです。他の結果メソッドは予定です。

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
null ガードが customer のない注文を残す場合（`o.getCustomer() == null || ...`）、lambdaQL は `left join` を使います。
[3.10](03-where.md#310-association-paths) を参照してください。

<a id="things-to-know"></a>
## 知っておくこと

| ルール | 例 |
|---|---|
| ラムダかメソッド参照を使います。匿名クラスは拒否されます。 | `where(o -> o.isActive())`、`where(Order::isActive)` |
| ラムダは 1 つの式でなければなりません（条件、ソート、選択の場合）。 | ラムダの中のループやローカル変数は拒否されます。 |
| 実行されなければならないコードをラムダに入れないでください。ラムダは実行されません。 | `System.out.println(...)` のような副作用のある呼び出しは拒否されます。 |
| javac でコンパイルします。 | ECJ の出力は保証しません。 |
| 新しい JDK のバージョンには、対応する ASM のバージョンが必要です。 | lambdaQL は ASM 9.11.1 を使います（Java 28 までのクラスファイルを読めます）。もっと新しい JDK でコンパイルする場合は、ASM を更新してください。 |

Java の詳細は [9. Java の注意点](09-java-notes.md) にあります。

<a id="next"></a>
## 次へ

- [3. 条件（`where`）](03-where.md): すべての演算子と NULL のルール。
- [4. ソートとページング](04-order-paging.md)

---

[Java トラック](README.md) | 前: [1. 概要](../01-overview.md) | 次: [3. 条件](03-where.md) | [このページの Kotlin 版](../kotlin/02-getting-started.md)
