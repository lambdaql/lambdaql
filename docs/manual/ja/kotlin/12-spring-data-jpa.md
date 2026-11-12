<a id="12-spring-data-jpa-kotlin"></a>
# 12. Spring Data JPA（Kotlin）

> [!NOTE]
> **状況: 設計済み、まだ実装されていません。**
> この章では、予定しているモジュール `lambdaql-spring-data-jpa` について説明します。
> 組み込みの仕組み（リポジトリフラグメント、トランザクション、Kotlin のインターフェースメソッド）は、Spring Boot 3.4.2、Spring Data JPA 3.4.2、kotlinc 2.3.21 のプロトタイプで確認しました。
> 現在動作するのは解析コアと `where` + `toList()` だけです（H2、MariaDB、Oracle でテスト済み）。
> 全体の状況は [Kotlin トラックの README](README.md) を参照してください。

[Kotlin トラック](README.md) | [マニュアルのホーム](../README.md) | 前: [11. エラーと言語](../11-errors-and-languages.md) | [このページの Java 版](../java/12-spring-data-jpa.md)

---

<a id="121-what-you-get"></a>
## 12.1 できること

Spring Data JPA では、リポジトリのインターフェースを書きます。lambdaQL はインターフェースを 1 つ追加します。`LambdaQlExecutor<T>` です。
リポジトリがこれを継承すると、独自のクエリメソッドを **本体を持つインターフェースメソッド** として書き、その中で lambdaQL を使えます。
これらのメソッドのために `...RepositoryImpl` クラスを作る必要はありません。

```kotlin
interface OrderRepository : JpaRepository<Order, Long>, LambdaQlExecutor<Order> {

    fun active(status: Int): List<Order> =
        findAll { it.status == status && it.active }

    fun recent(): List<Order> =
        query()
            .where { it.active }
            .orderBy { o, by -> by.desc(o.updateAt) }
            .limit(20)
            .toList()
}
```

```kotlin
@Service
class OrderService(private val orders: OrderRepository) {

    @Transactional(readOnly = true)
    fun activeOrders(): List<Order> = orders.active(1)
}
```

<a id="122-modules"></a>
## 12.2 モジュール

lambdaQL には 2 つの成果物（アーティファクト）があります。コアは Spring に依存しないので、普通の JPA でも使えます。

| アーティファクト | 含まれるもの | 必要になるとき |
|---|---|---|
| `lambdaql-core` | エンジン、`QueryBuilder`、select、DML、プロシージャ | 常に |
| `lambdaql-spring-data-jpa` | `LambdaQlExecutor<T>`、その Spring への登録、Spring Boot の自動構成（`QueryBuilder` の Bean） | Spring Data JPA または Spring Boot を使うとき |

```kotlin
// build.gradle.kts（予定の座標です。まだ何も公開していません）
dependencies {
    implementation("org.lambdaql:lambdaql-spring-data-jpa:0.1.0")   // lambdaql-core も一緒に入ります
    implementation("org.jetbrains.kotlin:kotlin-reflect")           // Spring Data が Kotlin のために必要とします
}
```

必要な環境:

| 項目 | バージョン |
|---|---|
| Spring Boot | 3.4 以降、および Spring Boot 4 |
| Spring Data JPA | 3.4 以降 |
| Hibernate | 6.6 以降（Spring Boot がすでに含んでいます） |
| Kotlin | 2.2 以降。それより古い Kotlin については 12.6 を参照 |
| `kotlin-reflect` | Spring Data が Kotlin のために必要とします |

Spring Data JPA 3.3 以前は **サポートしません**。lambdaQL が使う登録の方法（12.6 を参照）は 3.4 からあるものです。

## 12.3 `LambdaQlExecutor<T>`

`T` はリポジトリのエンティティです。

| メソッド | 動作 | 同等の書き方 |
|---|---|---|
| `query()` | このリポジトリのエンティティに対する select を始めます | `qb.from(Order::class.java)` |
| `from(Class<E>)` | 別のエンティティに対する select を始めます | `qb.from(Customer::class.java)` |
| `findAll(Where<T>)` | 条件に合うすべての行 | `query().where(w).toList()` |
| `findOne(Where<T>)` | 1 行、または空（`Optional`） | |
| `count(Where<T>)` | 条件に合う行の数 | |
| `exists(Where<T>)` | 条件に合う行が 1 つ以上あれば `true` | |
| `updateQuery()` | このエンティティに対する update を始めます | `qb.update(Order::class.java)` |
| `deleteQuery()` | このエンティティに対する delete を始めます | `qb.delete(Order::class.java)` |
| `insertQuery()` | このエンティティに対する insert を始めます | `qb.insert(Order::class.java)` |

DML のメソッド名が `Query` で終わるのは、`JpaRepository` にすでに `delete(T)` などのメソッドがあるからです。
`findAll { ... }` と `count { ... }` は、引数の型が違うので `findAll(Example)` や `count()` とは衝突しません。

このマニュアルのすべての章の内容は、リポジトリの中でも使えます。いくつか例を示します。

```kotlin
interface OrderRepository : JpaRepository<Order, Long>, LambdaQlExecutor<Order> {

    // 3. 条件、キャプチャ値
    fun findPaid(id: Long): Optional<Order> =
        findOne { it.id == id && it.status == 2 }

    // 5. サブクエリ
    fun withBigItems(): List<Order> =
        query()
            .where { o, sub -> sub.from(Item::class.java)
                                  .where { i -> i.order?.id == o.id && i.qty > 10 }
                                  .exists() }
            .toList()

    // 6. data class への select
    fun views(): List<OrderView> =
        query().select { OrderView(it.id, it.customer?.name, it.price) }.toList()

    // 7. update（トランザクションが必要です。12.5 を参照）
    fun closeOld(before: LocalDateTime): Int =
        updateQuery()
            .set { it.status = 9 }
            .where { it.updateAt!! < before }
            .execute()
}
```

<a id="124-how-it-works"></a>
## 12.4 仕組み

- **本体を持つインターフェースメソッドは、Spring Data 自身が実行します。** Spring Data はそのメソッドからクエリを作ろうとはしません。本体を呼ぶだけです。そのため lambdaQL は、Spring Data の扱い方を変える必要がありません。
- **`LambdaQlExecutor<T>` は Spring Data の「リポジトリフラグメント」です。** lambdaQL は実装を `META-INF/spring.factories` に登録します。Spring Data 3.4 以降はこのファイルを読み、`LambdaQlExecutor<T>` を継承するすべてのリポジトリに実装を追加します。設定を書く必要はありません。
- **エンティティの型** は、ラムダ（`findAll { ... }` の場合）またはリポジトリ（`query()` の場合）から決まります。

リポジトリがすでに独自のカスタム実装を持っている場合も動作します。

```kotlin
interface OrderRepository : JpaRepository<Order, Long>,
                            LambdaQlExecutor<Order>,
                            OrderRepositoryCustom          // 独自のフラグメント

class OrderRepositoryCustomImpl : OrderRepositoryCustom { /* ... */ }   // そのまま動作します
```

<a id="125-transactions"></a>
## 12.5 トランザクション

lambdaQL は、Spring が現在のトランザクションに渡す `EntityManager` を使います。

| ケース | 何が起きるか |
|---|---|
| `@Transactional` の中 | lambdaQL は、まだ flush されていない変更も見えます。ロールバックと `readOnly` は通常どおり動作します。 |
| トランザクションの外での select メソッド | それぞれ独自の読み取り専用トランザクションで実行されます。 |
| トランザクションの外での `execute()`（update、insert、delete） | エラーです。呼び出す側のサービスメソッドに `@Transactional` を付けてください。 |

```kotlin
@Transactional
fun closeOldOrders(): Int = orders.closeOld(LocalDateTime.now().minusYears(1))
```

バルク DML は、同じトランザクションですでに読み込まれたエンティティを更新しません。[7. 更新、挿入、削除](07-dml.md) を参照してください。

<a id="126-rules"></a>
## 12.6 ルール

| # | ルール |
|---|---|
| 1 | リポジトリは `LambdaQlExecutor<T>` を **直接**（自身のスーパータイプのリストで）継承する必要があります。 |
| 2 | この目的で `LambdaQlExecutor<T>` を継承するインターフェースに `@NoRepositoryBean` を付けないでください。付けると、Spring Data は lambdaQL の実装を追加しません。 |
| 3 | lambdaQL のクエリは **本体を持つインターフェースメソッド** かサービスに書いてください。本体のないメソッドは、これまでどおり Spring Data のクエリメソッド（`findByName` など）です。 |
| 4 | **Kotlin 2.2 以降:** 本体を持つメソッドは Java の default メソッドになり、動作します。**2.2 より古い Kotlin:** コンパイラーオプション `-Xjvm-default=all` を追加してください。これがないと、メソッドはインターフェースの中で本体を持たず、Spring Data がメソッド名からクエリを作ろうとして、アプリケーションが起動しません（`No property ... found for type ...`）。 |
| 5 | `kotlin-reflect` を追加してください。これがないと、Spring Data は `ClassNotFoundException: kotlin.reflect.jvm.ReflectJvmMapping` で失敗します。 |
| 6 | Spring Boot がなくても、リポジトリの部分は動作します（普通の Spring Data です）。`QueryBuilder` の Bean だけは自分で作る必要があります（[2. はじめよう](02-getting-started.md) を参照）。 |
| 7 | `@DataJpaTest` を使うテストでは、lambdaQL の自動構成を import してください（正確なクラス名は、モジュールのリリース時にここに記載します）。 |

```kotlin
// build.gradle.kts。2.2 より古い Kotlin の場合だけ
kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}
```

<a id="127-the-old-defaultmethodsupportjparepositoryfactorybean"></a>
## 12.7 以前の `DefaultMethodSupportJpaRepositoryFactoryBean`

以前の lambdaQL のコードには `org.lambdaql.jpa.DefaultMethodSupportJpaRepositoryFactoryBean` があります。その目的は、本体を持つインターフェースメソッドで `...Impl` クラスを置き換えられるようにすることでした。
Spring Data JPA 3.4 と Kotlin 2.2 では、これがなくてもすでに動作します（12.4 を参照）。
このクラスは **検討中** です。`-Xjvm-default=all` なしで 2.2 より古い Kotlin をサポートする方法になるかもしれません。`lambdaql-spring-data-jpa` に移す予定で、詳細はリリース前に決めます。

---

[Kotlin トラック](README.md) | 前: [11. エラーと言語](../11-errors-and-languages.md) | [このページの Java 版](../java/12-spring-data-jpa.md)
