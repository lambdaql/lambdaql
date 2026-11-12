<a id="12-spring-data-jpa-java"></a>
# 12. Spring Data JPA（Java）

> [!NOTE]
> **状況: 設計済み、まだ実装されていません。**
> この章では、予定しているモジュール `lambdaql-spring-data-jpa` を説明します。
> 組み込みの仕組み（リポジトリのフラグメント、トランザクション）は、Spring Boot 3.4.2 と Spring Data JPA 3.4.2 上のプロトタイプで確認しました。
> 現在動くのは解析コアと `where` + `toList()` だけです（H2、MariaDB、Oracle でテスト済み）。
> 全体の状況は [Java トラックの README](README.md#status) を参照してください。

[Java トラック](README.md) | [マニュアルのホーム](../README.md) | 前: [11. エラーと言語](../11-errors-and-languages.md) | [このページの Kotlin 版](../kotlin/12-spring-data-jpa.md)

---

<a id="121-what-you-get"></a>
## 12.1 できること

Spring Data JPA では、リポジトリのインターフェースを書きます。lambdaQL はインターフェースを 1 つ追加します。`LambdaQlExecutor<T>` です。
リポジトリでこれを継承し、独自のクエリメソッドを、中で lambdaQL を使う **default メソッド** として書きます。
これらのメソッドのために `...RepositoryImpl` クラスを作る必要はありません。

```java
public interface OrderRepository extends JpaRepository<Order, Long>, LambdaQlExecutor<Order> {

    default List<Order> active(int status) {
        return findAll(o -> o.getStatus() == status && o.isActive());
    }

    default List<Order> recent() {
        return query()
            .where(o -> o.isActive())
            .orderBy((o, by) -> by.desc(o.getUpdateAt()))
            .limit(20)
            .toList();
    }
}
```

```java
@Service
public class OrderService {
    private final OrderRepository orders;

    public OrderService(OrderRepository orders) {
        this.orders = orders;
    }

    @Transactional(readOnly = true)
    public List<Order> activeOrders() {
        return orders.active(1);
    }
}
```

<a id="122-modules"></a>
## 12.2 モジュール

lambdaQL には 2 つの成果物があります。コアは Spring に依存しないので、素の JPA でも使えます。

| 成果物 | 内容 | 必要になるとき |
|---|---|---|
| `lambdaql-core` | エンジン、`QueryBuilder`、select、DML、プロシージャ | 常に |
| `lambdaql-spring-data-jpa` | `LambdaQlExecutor<T>`、その Spring への登録、Spring Boot の自動構成（`QueryBuilder` の Bean） | Spring Data JPA または Spring Boot を使うとき |

```groovy
// build.gradle（予定の座標です。まだ何も公開していません）
dependencies {
    implementation 'org.lambdaql:lambdaql-spring-data-jpa:0.1.0'   // lambdaql-core も一緒に入ります
}
```

必要な環境:

| 項目 | バージョン |
|---|---|
| Spring Boot | 3.4 以降、および Spring Boot 4 |
| Spring Data JPA | 3.4 以降 |
| Hibernate | 6.6 以降（Spring Boot にすでに含まれています） |

Spring Data JPA 3.3 以前は **サポートしません**。lambdaQL が使う登録の方法（12.6 を参照）は 3.4 からあるものです。

## 12.3 `LambdaQlExecutor<T>`

`T` はリポジトリのエンティティです。

| メソッド | 何をするか | 同じ意味のコード |
|---|---|---|
| `query()` | このリポジトリのエンティティに対する select を始める | `qb.from(Order.class)` |
| `from(Class<E>)` | 別のエンティティに対する select を始める | `qb.from(Customer.class)` |
| `findAll(Where<T>)` | 条件に合うすべての行 | `query().where(w).toList()` |
| `findOne(Where<T>)` | 1 行、または空（`Optional`） | |
| `count(Where<T>)` | 条件に合う行の数 | |
| `exists(Where<T>)` | 条件に合う行が 1 つ以上あれば `true` | |
| `updateQuery()` | このエンティティに対する update を始める | `qb.update(Order.class)` |
| `deleteQuery()` | このエンティティに対する delete を始める | `qb.delete(Order.class)` |
| `insertQuery()` | このエンティティに対する insert を始める | `qb.insert(Order.class)` |

DML のメソッド名が `Query` で終わるのは、`JpaRepository` にすでに `delete(T)` などのメソッドがあるからです。
`findAll(Where<T>)` と `count(Where<T>)` は、引数の型が違うので、`findAll(Example)` や `count()` とぶつかりません。

このマニュアルのすべての章の内容は、リポジトリの中でも使えます。いくつかの例:

```java
public interface OrderRepository extends JpaRepository<Order, Long>, LambdaQlExecutor<Order> {

    // 3. 条件、キャプチャ値
    default Optional<Order> findPaid(long id) {
        return findOne(o -> o.getId() == id && o.getStatus() == 2);
    }

    // 5. サブクエリ
    default List<Order> withBigItems() {
        return query()
            .where((o, sub) -> sub.from(Item.class)
                                  .where(i -> i.getOrder().getId() == o.getId() && i.getQty() > 10)
                                  .exists())
            .toList();
    }

    // 6. record への select
    default List<OrderView> views() {
        return query().select(o -> new OrderView(o.getId(), o.getCustomer().getName(), o.getPrice())).toList();
    }

    // 7. update（トランザクションが必要です。12.5 を参照）
    default int closeOld(LocalDateTime before) {
        return updateQuery()
            .set(o -> o.setStatus(9))
            .where(o -> o.getUpdateAt().isBefore(before))
            .execute();
    }
}
```

<a id="124-how-it-works"></a>
## 12.4 仕組み

- **default メソッドは Spring Data 自身が実行します。** Spring Data は default メソッドからクエリを作ろうとはしません。メソッドの本体をそのまま呼ぶだけです。そのため、lambdaQL は Spring Data の default メソッドの扱いを変える必要がありません。
- **`LambdaQlExecutor<T>` は Spring Data の「リポジトリのフラグメント」です。** lambdaQL は実装を `META-INF/spring.factories` に登録します。Spring Data 3.4 以降はこのファイルを読み、`LambdaQlExecutor<T>` を継承するすべてのリポジトリにその実装を追加します。設定を書く必要はありません。
- **エンティティの型** は、ラムダ（`findAll(o -> ...)` の場合）またはリポジトリ（`query()` の場合）から決まります。

リポジトリにすでに独自のカスタム実装がある場合でも動きます。

```java
public interface OrderRepository extends JpaRepository<Order, Long>,
                                         LambdaQlExecutor<Order>,
                                         OrderRepositoryCustom {          // 独自のフラグメント
}
class OrderRepositoryCustomImpl implements OrderRepositoryCustom { ... }  // これまでどおり動きます
```

<a id="125-transactions"></a>
## 12.5 トランザクション

lambdaQL は、Spring が現在のトランザクションに割り当てる `EntityManager` を使います。

| ケース | どうなるか |
|---|---|
| `@Transactional` の中 | lambdaQL は、まだ flush されていない変更も見えます。ロールバックと `readOnly` はいつもどおり動きます。 |
| トランザクションの外での select メソッド | それぞれ専用の読み取り専用トランザクションで実行されます。 |
| トランザクションの外での `execute()`（update、insert、delete） | エラーです。呼び出す側のサービスメソッドに `@Transactional` を付けてください。 |

```java
@Transactional
public int closeOldOrders() {
    return orders.closeOld(LocalDateTime.now().minusYears(1));
}
```

一括 DML は、同じトランザクションですでに読み込まれたエンティティを更新しません。[7. 更新、挿入、削除](07-dml.md) を参照してください。

<a id="126-rules"></a>
## 12.6 ルール

| # | ルール |
|---|---|
| 1 | リポジトリは `LambdaQlExecutor<T>` を **直接** 継承する必要があります（そのリポジトリ自身の `extends` リストに書きます）。 |
| 2 | この目的で `LambdaQlExecutor<T>` を継承するインターフェースに `@NoRepositoryBean` を付けないでください。付けると、Spring Data は lambdaQL の実装を追加しません。 |
| 3 | lambdaQL のクエリは **default メソッド** かサービスの中に書いてください。抽象メソッドは、これまでどおり Spring Data のクエリメソッド（`findByName` など）です。 |
| 4 | Spring Boot がなくても、リポジトリの部分は動きます（普通の Spring Data だからです）。`QueryBuilder` の Bean だけは自分で作る必要があります（[2. はじめよう](02-getting-started.md) を参照）。 |
| 5 | `@DataJpaTest` を使うテストでは、lambdaQL の自動構成をインポートしてください（正確なクラス名は、モジュールのリリース時にここに記載します）。 |

<a id="127-the-old-defaultmethodsupportjparepositoryfactorybean"></a>
## 12.7 以前の `DefaultMethodSupportJpaRepositoryFactoryBean`

以前の lambdaQL のコードには `org.lambdaql.jpa.DefaultMethodSupportJpaRepositoryFactoryBean` があります。その目的は、default メソッドで `...Impl` クラスを置き換えられるようにすることでした。
Spring Data JPA 3.4 では、これがなくても Java の default メソッドはすでに動きます（12.4 を参照）。
このクラスは **検討中** です。`lambdaql-spring-data-jpa` に移す予定で、詳細はリリース前に決めます。

---

[Java トラック](README.md) | 前: [11. エラーと言語](../11-errors-and-languages.md) | [このページの Kotlin 版](../kotlin/12-spring-data-jpa.md)
