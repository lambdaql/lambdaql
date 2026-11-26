<a id="12-spring-data-jpa-kotlin"></a>
# 12. Spring Data JPA（Kotlin）

[Kotlin トラック](README.md) | [マニュアルのホーム](../README.md) | 前: [11. エラーと言語](../11-errors-and-languages.md) | 次: [13. Spring Boot 3 と 4](../13-spring-boot-versions.md) | [このページの Java 版](../java/12-spring-data-jpa.md)

---

<a id="121-what-you-get"></a>
## 12.1 提供する機能

Spring Data JPA では、リポジトリーのインターフェースを書きます。lambdaQL は、そこにインターフェース `LambdaQlExecutor<T>` を 1 つ加えます。

リポジトリーがこのインターフェースを継承し、自分で作るクエリのメソッドは、lambdaQL を使う **本体を持つインターフェースのメソッド** として書きます。
これらのメソッドのための `...RepositoryImpl` クラスは書きません。

| 構成要素 | 機能 | 節 |
|---|---|---|
| `LambdaQlExecutor<T>` | すべてのリポジトリーで、ラムダ式の条件で `findAll`、`findSet`、`findFirst`、`findOne`、`count`、`exists`、`query()` を実行 | [12.3](#123-repository-setup)、[12.4](#124-executor-methods) |
| Spring の `Sort` と `Pageable` | `findAll(where, sort)`、`findSet(where, sort)`、Spring の `Page` を返す `findAll(where, pageable)` | [12.5](#125-spring-sort-and-pageable) |
| 更新、削除、挿入、upsert | `deleteAll { ... }`、`updateOne({ ... }) { ... }` のような短縮メソッドが、1 回の呼び出しで文を実行。`updateQuery()`、`deleteQuery()`、`insertQuery()`、`upsertQuery()` が、リポジトリーのエンティティに対して [7. 更新、挿入、削除](07-dml.md) の文を始める。どれも呼び出し側のトランザクションが必要 | [12.4](#124-executor-methods)、[12.6](#126-transactions-and-exceptions) |
| `QueryBuilder` の Bean | サービスで使う `QueryBuilder` の Bean を Spring Boot が作る | [12.8](#128-the-querybuilder-bean) |

<a id="122-modules"></a>
## 12.2 モジュール

コアは Spring に依存しないので、素の JPA でも使えます。`lambdaql` と `lambdaql-spring-data-jpa` は Spring Boot 3・4 共通で、スターターだけが Spring Boot のバージョンごとに分かれます（名前の末尾 `-boot3`、`-boot4`、[13. Spring Boot 3 と 4](../13-spring-boot-versions.md)）。

| アーティファクト | 含むもの | 必要な場合 |
|---|---|---|
| `lambdaql` | エンジンと `QueryBuilder`。Spring への依存なし。Spring Boot 3・4 共通 | 常に。下のアーティファクトが一緒に持ち込む |
| `lambdaql-spring-data-jpa` | `LambdaQlExecutor<T>`、Spring Data への登録、Spring Boot の自動構成（`QueryBuilder` の Bean）。Spring Data JPA 3.5・4.1 共通。Spring Data JPA はプロジェクトのものを使う | Spring Boot なしで Spring Data JPA を使うとき |
| `lambdaql-spring-boot-starter-boot4`、`lambdaql-spring-boot-starter-boot3` | コードなし。`lambdaql-spring-data-jpa` と、その Boot のバージョンの `spring-boot-starter-data-jpa` を持ち込む | Spring Boot を使うとき（プロジェクトの Boot のバージョンに合わせる） |

アーティファクトは Maven Central にあります（groupId `io.github.lambdaql`、バージョン `0.0.2`）。

Spring Boot 4 で使うときの Gradle（Kotlin DSL）。Spring Boot 3 なら名前の末尾を `-boot3` に変えてください:

```gradle-kts
repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.lambdaql:lambdaql-spring-boot-starter-boot4:0.0.2")
    implementation("org.jetbrains.kotlin:kotlin-reflect")           // Spring Data needs it for Kotlin
}
```

Maven:

```xml
<dependency>
  <groupId>io.github.lambdaql</groupId>
  <artifactId>lambdaql-spring-boot-starter-boot4</artifactId>
  <version>0.0.2</version>
</dependency>
```

Maven でも `kotlin-reflect` を追加してください（[12.9](#129-kotlin-compile-settings) を参照）。

Spring Boot なしで使うときは、既存の Spring Data JPA の構成（Spring Data JPA、Hibernate、JDBC ドライバー）に `lambdaql-spring-data-jpa` を追加してください。このアーティファクトは `lambdaql` を一緒に持ち込み、Spring Data JPA はプロジェクトにあるもの（3.5 または 4.1）を使います。

```gradle-kts
dependencies {
    implementation("io.github.lambdaql:lambdaql-spring-data-jpa:0.0.2")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
}
```

```xml
<dependency>
  <groupId>io.github.lambdaql</groupId>
  <artifactId>lambdaql-spring-data-jpa</artifactId>
  <version>0.0.2</version>
</dependency>
```

要件:

| 項目 | バージョン |
|---|---|
| Java（JVM） | 21 以上 |
| Spring Boot | 3.5 または 4.1 |
| Spring Data JPA | 3.5 または 4.1 |
| Hibernate | 6.6 または 7.4（Spring Boot に含まれる） |
| Kotlin | 2.2 以上。それより前のバージョンは [12.9](#129-kotlin-compile-settings) を参照 |
| `kotlin-reflect` | Spring Data の Kotlin サポートに必要 |

Spring Data JPA 3.3 以下は **サポートしていません**。lambdaQL は実行器を `META-INF/spring.factories` に登録し、Spring Data は 3.4 からこのファイルでリポジトリーのフラグメントを読みます。

<a id="123-repository-setup"></a>
## 12.3 リポジトリーの設定

リポジトリーの上位の型のリストに `LambdaQlExecutor<T>` を追加します。`T` はリポジトリーのエンティティです。
別の構成や `Impl` クラスは書きません。

この章の例は、Java で書いた例のエンティティ `Member`（`id`、`name`、`active`、`grade`、`team`）と `Team`（`id`、`name`）を、Kotlin のリポジトリーで使います。Kotlin では `grade` は `Int?` なので NULL になることがあり、`active` は `isActive` プロパティです。

例のリポジトリー名の `Kt` は、Java トラックの例のリポジトリーと区別するための接頭辞です。

<!-- region: ManualKotlinRepositories.kt#repository -->
```kotlin
/** No Impl class: Spring Data finds the LambdaQlExecutor implementation by itself. */
interface KtMemberRepository : JpaRepository<Member, Long>, LambdaQlExecutor<Member> {

    fun activeMembers(): List<Member> =
        findAll { it.isActive }

    fun byName(name: String): Optional<Member> =
        findOne { it.name == name }

    fun topGraded(n: Int): List<Member> =
        query()
            .where { it.isActive }
            .orderBy { m -> desc(m.grade).asc(m.id) }
            .limit(n)
            .toList()

    // update, delete, insert: call inside a transaction
    fun gradeInactive(grade: Int): Int =
        updateQuery().set { it.grade = grade }.where { !it.isActive }.execute()

    fun deleteWithoutTeam(): Int =
        deleteQuery().where { it.team == null }.execute()

    fun addGuest(name: String): Int =
        insertQuery().values { it.name = name; it.isActive = false }.execute()
}
```

最後の 3 つのメソッドは、更新、削除、挿入の文を実行します。これらのメソッドにはトランザクションが必要です（[12.6](#126-transactions-and-exceptions) を参照）。

動作のしくみ:

- **本体を持つインターフェースのメソッドは、Spring Data が直接実行します。** Spring Data はこのメソッドからクエリを作らず、本体を呼びます。lambdaQL はこの動作を変えません。Kotlin 2.2 未満は [12.9](#129-kotlin-compile-settings) を参照してください。
- **`LambdaQlExecutor<T>` は Spring Data のリポジトリーのフラグメントです。** 実装クラスは `lambdaql-spring-data-jpa` の `META-INF/spring.factories` に登録されています。Spring Data はこのファイルを読み、`LambdaQlExecutor<T>` を継承するすべてのリポジトリーに実装を追加します。
- **1 つの実行器オブジェクトを、すべてのリポジトリーが共有します。** 呼び出しごとに、そのリポジトリーのエンティティを使います。たとえば `TeamRepository` で、`teams.count { ... }` は `Team` の行の数を返します。

ルール:

| # | ルール |
|---|---|
| 1 | リポジトリーは `LambdaQlExecutor<T>` を **直接**（自分の上位の型のリストで）継承しなければなりません。 |
| 2 | この目的で `LambdaQlExecutor<T>` を継承するインターフェースに、`@NoRepositoryBean` を付けないでください。付けると、Spring Data は lambdaQL の実装を追加しません。 |
| 3 | lambdaQL のクエリは、**本体を持つインターフェースのメソッド** かサービスに書いてください。本体のないメソッドは、これまでどおり Spring Data のクエリメソッド（`findByName` など）として動きます。 |
| 4 | Spring Boot がなくても、リポジトリーの部分は普通の Spring Data なので動きます。`QueryBuilder` の Bean だけは自動で作られません（[12.8](#128-the-querybuilder-bean) を参照）。 |

コード例: [`ManualKotlinRepositories.kt`](https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/kotlin/org/lambdaql/spring/ManualKotlinRepositories.kt)（`repository`）。

<a id="124-executor-methods"></a>
## 12.4 実行器のメソッド

`Where<T>` は `where { ... }` と同じ条件のラムダ式です（[3. 条件](03-where.md) を参照）。`SortSpec<T>` は lambdaQL のソートです（[4.10](04-order-paging.md#410-reusable-and-external-sorts) を参照）。
`Sort`、`Pageable`、`Page` は Spring Data の型です。

| メソッド | 戻り値 | 説明 |
|---|---|---|
| `query()` | `SelectQuery<T>` | リポジトリーのエンティティに対するクエリを始める。短いメソッドで表せないクエリ（`where` を複数回、`limit`、`offset`、実行時の `Direction`）に使う |
| `from(Class<E>)` | `SelectQuery<E>` | 同じ永続化ユニットの別のエンティティに対するクエリを始める |
| `findAll(Where<T>)` | `List<T>` | 条件に合うすべての行。順序は決まっていない |
| `findAll(Where<T>, SortSpec<T>)` | `List<T>` | 条件に合うすべての行をソートして返す |
| `findAll(Where<T>, Sort)` | `List<T>` | Spring の `Sort` でソート（[12.5](#125-spring-sort-and-pageable) を参照）。`Sort.unsorted()` はソートなし |
| `findAll(Where<T>, Pageable)` | `Page<T>` | 1 ページ。count クエリは必要なときだけ実行。`Pageable.unpaged()` はすべての行 |
| `findSet(Where<T>)` | `SequencedSet<T>` | 条件に合うすべての行を Set で返す。順序は決まっていない（`findAll(Where<T>)` と同じ）。`distinct` なし。Set のサイズが行数より小さいと `IllegalStateException`（[`toSet()`](02-getting-started.md#toset) を参照） |
| `findSet(Where<T>, SortSpec<T>)` | `SequencedSet<T>` | 条件に合うすべての行を、ソートの順に Set で返す |
| `findSet(Where<T>, Sort)` | `SequencedSet<T>` | 上と同じ。Spring の `Sort` でソート（[12.5](#125-spring-sort-and-pageable) を参照） |
| `findFirst(Where<T>)` | `Optional<T>` | 条件に合う行の 1 つ。ソートしない |
| `findFirst(Where<T>, SortSpec<T>)` | `Optional<T>` | 指定した順序の最初の行。1 行だけ読む |
| `findFirst(Where<T>, Sort)` | `Optional<T>` | Spring の `Sort` の順序の最初の行 |
| `findOne(Where<T>)` | `Optional<T>` | 条件に合うただ 1 つの行。2 行以上なら `IncorrectResultSizeDataAccessException` |
| `count(Where<T>)` | `Long` | 条件に合う行の数 |
| `exists(Where<T>)` | `Boolean` | 条件に合う行があれば `true`。最大 1 行だけ読み、エンティティは読み込まない |
| `deleteAll(Where<T>)` | `Int` | 条件に合うすべての行を 1 つの SQL 文で削除（一括）。Spring Data の `deleteAll()` とは違う（[下](#deleteallwhere-is-not-spring-datas-deleteall) を参照）。トランザクションが必要 |
| `deleteOne(Where<T>)` | `Int` | 条件に合うただ 1 つの行を、[1 行保護](#one-row-protection-deleteone-and-updateone) を適用して削除。0 か 1 を返す。トランザクションが必要 |
| `updateAll(Where<T>, Assign<T>)` | `Int` | 条件に合うすべての行を更新。条件が先で、代入は末尾ラムダ式。トランザクションが必要 |
| `updateOne(Where<T>, Assign<T>)` | `Int` | 条件に合うただ 1 つの行を、1 行保護を適用して更新。0 か 1 を返す。トランザクションが必要 |
| `insertOne(Assign<T>)` | `Int` | 1 行を挿入。1 を返す。トランザクションが必要 |
| `insertAll(Collection<L>, AssignEach<T, L>)` | `Int` | 要素ごとに 1 行を挿入（1 文あたり値 500 個以下）。トランザクションが必要 |
| `upsertOne(Assign<T>)` | `Int` | 1 行を挿入。同じ `@Id` の行があれば、その行を更新。データベースが報告した行数を返す。トランザクションが必要 |
| `upsertAll(Collection<L>, AssignEach<T, L>)` | `Int` | 要素ごとに 1 行を upsert（行ごとに 1 つの文）。トランザクションが必要 |
| `updateQuery()` | `UpdateQuery<T>` | 更新を始める: `updateQuery().set { ... }.where { ... }.execute()`（[7.2](07-2-update.md) を参照）。トランザクションが必要 |
| `deleteQuery()` | `DeleteQuery<T>` | 削除を始める: `deleteQuery().where { ... }.execute()`（[7.4](07-4-delete.md) を参照）。トランザクションが必要 |
| `insertQuery()` | `InsertQuery<T>` | 挿入を始める: `insertQuery().values { ... }.execute()`（[7.5](07-5-insert.md) を参照）。トランザクションが必要 |
| `upsertQuery()` | `UpsertQuery<T>` | `@Id` をキーとする upsert を始める（[7.6](07-6-upsert.md) を参照）。トランザクションが必要 |

短いメソッドは、`query()` に結果関数を付けた形と同じです。`findAll(w)` は `query().where(w).toList()`、`findSet(w)` は `query().where(w).toSet()`、`findFirst(w)` は `query().where(w).first()`、`findOne(w)` は `query().where(w).one()` にあたります。結果関数は [結果を受け取る](02-getting-started.md#getting-the-result) で説明します。
`findAll { ... }` と `count { ... }` は引数の型が違うので、`JpaRepository` の `findAll(Sort)`、`findAll(Example)`、`count()` と衝突しません。
メソッドは Java の `Optional` を返します。

**`query()`: 完全なクエリ。** 12.3 の `topGraded` がこのメソッドを使います。

次の例で、`repository` は 12.3 の `KtMemberRepository` です。例のデータは会員 6 人（アクティブ 4 人、グレードが NULL の会員を含む）です。

<!-- region: ManualKotlinRepositoryTest.kt#executor -->
```kotlin
val byGrade = SortSpec.of(Member::class.java) { m -> desc(m.grade).asc(m.id) }
val active: List<Member> = repository.findAll({ it.isActive }, byGrade)

// the active member with the highest grade
val best: Optional<Member> = repository.findFirst({ it.isActive },
    SortSpec.of(Member::class.java) { m -> desc(m.grade) })

val kim: Optional<Member> = repository.findOne { it.name == "Kim" }
// two or more rows: Spring's IncorrectResultSizeDataAccessException
assertThrows<IncorrectResultSizeDataAccessException> { repository.findOne { it.isActive } }

val activeCount: Long = repository.count { it.isActive }
val anyInTeamB: Boolean = repository.exists { it.team?.name == "B" }

val byName: SequencedSet<Member> = repository.findSet({ it.isActive }, Sort.by("name"))
val anyOrder: SequencedSet<Member> = repository.findSet { it.isActive }   // no fixed order, like findAll { }
```

<a id="dml-shortcuts"></a>
### DML の短縮メソッド

次の 8 つのメソッドは、更新、削除、挿入、upsert を 1 回の呼び出しで実行します。

| 短縮メソッド | 同じ文 |
|---|---|
| `deleteAll { where }` | `deleteQuery().where { where }.execute()` |
| `deleteOne { where }` | `deleteQuery().where { where }.executeOne()` |
| `updateAll({ where }) { set }` | `updateQuery().set { set }.where { where }.execute()` |
| `updateOne({ where }) { set }` | `updateQuery().set { set }.where { where }.executeOne()` |
| `insertOne { values }` | `insertQuery().values { values }.execute()` |
| `insertAll(rows) { m, r -> values }` | `insertQuery().values(rows) { m, r -> values }.execute()` |
| `upsertOne { values }` | `upsertQuery().values { values }.execute()` |
| `upsertAll(rows) { m, r -> values }` | `upsertQuery().values(rows) { m, r -> values }.execute()` |

- **条件が先** に来ます。代入は末尾ラムダ式で書きます: `updateAll({ !it.active }) { it.grade = 7 }`。
- **`All`** は、条件に合うすべての行（削除、更新）またはリストのすべての要素（挿入、upsert）を意味します。
- **`One`** は、削除と更新では 1 行保護（次の節を参照）、挿入と upsert では 1 行を意味します。
- 呼び出し側のトランザクションが必要です（[12.6](#126-transactions-and-exceptions) を参照）。

12.3 と同じ、本体を持つインターフェースのメソッドです。ここの `gradeInactive` と `addGuest` は、12.3 のメソッドの短縮形です。

<!-- region: ManualKotlinRepositories.kt#shortcuts -->
```kotlin
/** A list element for insertAll. */
data class NewMember(val name: String, val grade: Int)

/** The DML shortcuts: one call runs the statement (inside the caller's transaction). */
interface KtMemberShortcutRepository : JpaRepository<Member, Long>, LambdaQlExecutor<Member> {

    fun purgeWithoutTeam(): Int = deleteAll { it.team == null }

    fun deleteByName(name: String): Int = deleteOne { it.name == name }

    fun gradeInactive(grade: Int): Int = updateAll({ !it.isActive }) { it.grade = grade }

    fun rename(id: Long, name: String): Int = updateOne({ it.id == id }) { it.name = name }

    fun addGuest(name: String): Int = insertOne { it.name = name; it.isActive = false }

    fun addMembers(rows: List<NewMember>): Int =
        insertAll(rows) { m, r -> m.name = r.name; m.grade = r.grade; m.isActive = true }
}
```

これらのメソッドは、トランザクションの中で呼んでください（`@Transactional` または `TransactionTemplate`）。トランザクションなしで呼ぶと、`InvalidDataAccessApiUsageException` が発生します。

<a id="one-row-protection-deleteone-and-updateone"></a>
### 1 行保護: `deleteOne` と `updateOne`

`deleteOne { ... }` と `updateOne({ ... }) { ... }` は、2 行以上を変更しません。

1. lambdaQL が、条件に合う行の id を最大 2 つ読みます。
2. id がなければ、何も変更せずに 0 を返します。
3. id が 2 つなら、何も変更せずに `IncorrectResultSizeDataAccessException` を投げます。
4. id が 1 つなら、`id = :p and (条件)` でその行だけを変更し、1 を返します。その間にその行が条件から外れていたら、0 を返します。

文そのものに id を 1 つ指定するので、その間に別のトランザクションが条件に合う行を追加しても、2 つ目の行は変更しません。この保護はロールバックに頼りません。

コアの API にも同じ保護があります: `executeOne()`（[7.4](07-4-delete.md#delete-exactly-one-row-executeone) を参照）。

`TransactionTemplate` の中:

<!-- region: ManualKotlinRepositoryTest.kt#dml-one -->
```kotlin
val renamed = repository.updateOne({ it.id == kimId }) { it.name = "Kim2" }   // exactly one row
// two or more rows match: nothing changes
assertThrows<IncorrectResultSizeDataAccessException> { repository.deleteOne { it.isActive } }
```

<a id="deleteallwhere-is-not-spring-datas-deleteall"></a>
### `deleteAll(where)` と Spring Data の `deleteAll()` の違い

| メソッド | 所属 | 削除の方法 | cascade、`@PreRemove` |
|---|---|---|---|
| `deleteAll()` | `CrudRepository` | すべてのエンティティを読み込んでから 1 つずつ削除 | 実行する |
| `deleteAll(Iterable)` | `CrudRepository` | 渡したエンティティを 1 つずつ削除 | 実行する |
| `deleteAll { ... }`（`Where<T>`） | `LambdaQlExecutor<T>` | SQL の `delete` 文 1 つ（一括） | **実行しない** |

`deleteAll { ... }` はエンティティを読み込みません。他の一括の文と同じく、すでに読み込まれたエンティティには反映されません。

多くの行を速く削除するときに使ってください。cascade や `@PreRemove` が必要なら、Spring Data のメソッドを使ってください。

コンパイラーは引数（引数なし、`Iterable`、ラムダ式）によってメソッドを選びます。`deleteAll()` と `deleteAll { ... }`（短縮メソッド）は、1 つのリポジトリーで一緒に使えます。

<a id="builders-for-complex-statements"></a>
### 複雑な文のためのビルダー

`updateQuery()`、`deleteQuery()`、`insertQuery()`、`upsertQuery()` は完全な文を始めます。短縮メソッドで表せない場合に使ってください: `set` を複数回、`all()`、`orderBy { ... }.limit(n)`（[7.4](07-4-delete.md#delete-the-first-rows-limit) を参照）、`toHql()`、`onConflict { ... }`。

メソッド名が `Query` で終わるので、`delete(...)` のような `JpaRepository` のメソッドと衝突しません。文は `QueryBuilder` で作る文と同じです（[7. 更新、挿入、削除](07-dml.md) を参照）。対象の行は、同じ `where` のラムダ式で選択した行と同じです。

短縮メソッドと同じく、呼び出し側のトランザクションが必要です。すべての JPA の一括の文と同じく、トランザクションの中ですでに読み込まれたエンティティには反映されません。

12.3 の `gradeInactive`、`deleteWithoutTeam`、`addGuest` のメソッドがこれらのメソッドを使います。upsert は、`@Id` を代入できる `Team` で示します（`KtTeamRepository : JpaRepository<Team, Long>, LambdaQlExecutor<Team>`）。

<!-- region: ManualKotlinRepositoryTest.kt#upsert -->
```kotlin
// teams is a KtTeamRepository, tx a Spring TransactionTemplate
val changed = tx.execute { teams.upsertQuery().values { it.id = teamId; it.name = "A2" }.execute() }
```

upsert は、データベースが報告した行数を返します。挿入なら 1 で、更新なら H2 と Oracle は 1、MariaDB は 2 です（[7.6](07-6-upsert.md#the-row-count) を参照）。

コード例: [`ManualKotlinRepositoryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/kotlin/org/lambdaql/spring/ManualKotlinRepositoryTest.kt)（`executor`、`dml-one`、`upsert`）、[`ManualKotlinRepositories.kt`](https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/kotlin/org/lambdaql/spring/ManualKotlinRepositories.kt)（`shortcuts`）。

<a id="125-spring-sort-and-pageable"></a>
## 12.5 Spring の `Sort` と `Pageable`

`findAll(where, sort)`、`findSet(where, sort)`、`findFirst(where, sort)`、`findAll(where, pageable)` は Spring Data の型を受け取ります。
lambdaQL は各 `Sort.Order` を、`SortSpec.ofPath` で 1 つのソートキーに変換します（[4.10](04-order-paging.md#410-reusable-and-external-sorts) を参照）。

| Spring | lambdaQL の扱い |
|---|---|
| プロパティ名（`"grade"`、`"team.name"`） | Spring Data のリポジトリーと同じく、Spring Data の `PropertyPath` で検証。存在しない名前なら `PropertyReferenceException`（パッケージ: Spring Data 3 は `org.springframework.data.mapping`、4 は `org.springframework.data.core`）。パスの終わりはエンティティではない基本の値でなければならない。`team.name` のような to-one のパスは `left join` を使うので、チームのない行も結果に含まれる。続けてソートポリシーが名前をチェックする（[下](#sort-policy-with-spring) を参照） |
| ソートポリシーの別名（`"level"`） | `PropertyPath` のチェックなしでそのまま渡す。ソートポリシーがパスに変える |
| `NullHandling.NATIVE`（デフォルト）の `ASC` / `DESC` | lambdaQL の NULL のルールを適用。昇順は NULL が先、降順は NULL が後。すべてのデータベースで同じ順序（[4.4](04-order-paging.md#44-null-ordering-is-the-same-on-every-database) を参照） |
| `NullHandling.NULLS_FIRST` / `NULLS_LAST` | 指定したとおりに使う（`ASC_NULLS_FIRST`、`DESC_NULLS_LAST` など） |
| `ignoreCase()` | まだサポートしていない。`UnsupportedOperationException` が発生 |
| `Sort.unsorted()` | `order by` なし |

Spring Data JPA 自身のリポジトリーは `NATIVE` の NULL の位置をデータベースに任せるので、データベースによって順序が違うことがあります。lambdaQL は独自のルールを適用します。

`findAll(where, pageable)` のページング:

- ページ番号は、Spring Data と同じく 0 から始まります。lambdaQL は `limit` と `offset` でページを読みます。
- 全体の件数は `count` クエリで求めます。ページだけで全体の件数が確定する場合（例: 最初のページの行数がページサイズより少ない場合）、Spring は count クエリを省きます。
- ソートキーに id がなければ lambdaQL が id を最後のキーとして追加するので、行がページの間で移動しません（[タイブレーカー](04-order-paging.md#tie-breaker-automatic-id) を参照）。ソートのないページには `order by` が付かないので、ページを安定して分けるには `Sort` を指定してください。
- `Pageable.unpaged()` は、すべての行を 1 ページで返します。

<!-- region: ManualKotlinRepositoryTest.kt#spring-sort -->
```kotlin
val rows: List<Member> = repository.findAll({ it.isActive },
    Sort.by(Sort.Order.desc("grade"), Sort.Order.asc("team.name"), Sort.Order.asc("id")))
// a property that does not exist: the same error as Spring Data's own repositories
val unknown = assertThrows<RuntimeException> { repository.findAll({ it.isActive }, Sort.by("nickname")) }
assertEquals("PropertyReferenceException", unknown.javaClass.simpleName)

// page 1 (the second page) of the active members by name; the total comes from a count query
val page: Page<Member> = repository.findAll({ it.isActive }, PageRequest.of(1, 2, Sort.by("name")))
```

**リクエストパラメーターで受け取るソート。** Spring MVC は、`?page=1&size=20&sort=name,desc` のようなリクエストパラメーターから `Pageable` を作れます。
ソートポリシーも `@Sortable` フィールドもなければ、永続プロパティはすべて受け付けるので、利用者はどのカラムでもソートできます。このとき lambdaQL は、エンティティと名前ごとに 1 回、`LQL-5003` を `INFO` レベルでログに出します。
一部の名前だけを許可するには、下のソートポリシーを使ってください。

<a id="sort-policy-with-spring"></a>
### Spring でのソートポリシー

ソートポリシーは、許可する外部のソートの名前を決めます。`SortRules`、`SortMode` とルールは [4.10](04-order-paging.md#sort-policy) で説明します。
Spring の `Sort` や `Pageable` は常に外部のソートなので、ポリシーがその中の名前をすべてチェックします。ソートのラムダ式はチェックしません。

**ルールの出所。** Spring Boot では、自動構成が `EntityManagerFactory` のポリシーを作り、すべてのシングルトンの Bean を作った後で登録します。
自動構成が読む場所は 3 つです。

| 出所 | 提供するもの |
|---|---|
| `SortPolicy` の Bean | モードと、ラムダ式で書いた `SortRules` |
| プロパティ `lambdaql.sort.*` | モードと、文字列で書いたルール |
| `@Sortable` フィールド（[4.10](04-order-paging.md#rules-from-sortable)） | Bean やプロパティにルールのないエンティティのルール |

- 1 つのエンティティのルールは、表の順で 1 か所からだけ取ります。2 か所のルールは合わせません。
- `lambdaql.sort.mode` は Bean のモードより優先します。どちらもなければ、モードは `INFO` です。
- 登録の条件は `EntityManagerFactory` がちょうど 1 つのときで、`QueryBuilder` の Bean と同じです（[12.8](#128-the-querybuilder-bean) を参照）。それ以外の場合は、`SortPolicy.register(emf, policy)` を直接呼んでください。
- Bean もプロパティもなければ何も登録しないので、デフォルトを使います: `INFO`、ルールは `@Sortable` からだけ取る。

**プロパティ。**

| プロパティ | 意味 |
|---|---|
| `lambdaql.sort.mode` | `OFF`、`INFO`（デフォルト）、`WARN`、`STRICT` |
| `lambdaql.sort.allow.<Entity>` | 許可するパスをカンマで区切る。例: `name,team.name` |
| `lambdaql.sort.alias.<Entity>.<外部の名前>` | 別名のパス。例: `lambdaql.sort.alias.Member.level=grade` |

**SARGable モードのプロパティ。** `lambdaql.sargable.mode` は `OFF`、`WARN`（デフォルト）、`STRICT` のどれかです。自動構成が `lambdaql.sort.mode` と同じ方法で登録します。`STRICT` なら、インデックスを使えない条件の解析が `NonSargableException`（`LQL-3066`）で失敗します（[8.9](08-procedures-functions.md#89-warning-functions-on-columns-and-indexes-sargable) を参照）。

`<Entity>` には JPA のエンティティ名を書きます。デフォルトは単純なクラス名で、`@Entity(name = ...)` で変えられます。

エンティティ名は永続化ユニットの中で一意です（同じ名前が 2 つあると、Hibernate が起動の段階で止まる）。そのため、パッケージが違う同じ名前のクラスも混ざりません。存在しない名前だと、アプリケーションの起動が `IllegalStateException`（LQL-4042）で止まります。
パスは、ルールを最初に使うときにエンティティと照合します（4.10 の [文字列のルール](04-order-paging.md#rules-as-strings) を参照）。

プロパティは Java と Kotlin で同じです（[このページの Java 版](../java/12-spring-data-jpa.md#sort-policy-with-spring) を参照）。自動構成が、プロパティから作ったポリシーを登録します。
`application.properties` の例:

```properties
lambdaql.sort.mode=STRICT
lambdaql.sort.allow.Member=name,team.name
lambdaql.sort.alias.Member.level=grade
```

**`SortPolicy` の Bean。** コンパイラーがチェックするルールを使うには、`SortPolicy` の Bean を定義してください。同じ自動構成の段階が、この Bean を登録します。
下の構成クラスは、[12.8](#128-the-querybuilder-bean) の `QueryBuilder` の Bean も一緒に定義します。

<!-- region: ManualKotlinBeansTest.kt#beans -->
```kotlin
/** Beans of your own: a sort policy, and a QueryBuilder (for example with two EntityManagerFactory beans). */
@Configuration
open class KtLambdaQlConfig {

    @Bean
    open fun sortPolicy(): SortPolicy = SortPolicy.of(SortMode.STRICT,
        SortRules.of(Member::class.java)
            .allow { it.name }
            .alias("level") { it.grade })

    @Bean
    open fun queryBuilder(entityManagerFactory: EntityManagerFactory) =
        // the same as the auto-configuration: a shared, transaction-bound EntityManager
        QueryBuilder(SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory))
}
```

kotlin-spring（all-open）のコンパイラープラグインを使わない場合は、上のように `@Configuration` クラスと `@Bean` 関数を `open` で宣言してください。

この Bean があるアプリケーションで、Spring の `Sort` を使います。

<!-- region: ManualKotlinBeansTest.kt#sort-policy -->
```kotlin
// the client sends "level"; lambdaQL sorts by grade
val rows: List<Member> = repository.findAll({ it.isActive }, Sort.by(Sort.Order.desc("level"), Sort.Order.asc("name")))
// a property that is not in the rules
assertThrows<SortNotAllowedException> { repository.findAll({ it.isActive }, Sort.by("grade")) }
```

- `level` のような別名は、`Member` のプロパティではありません。lambdaQL は Spring の `PropertyPath` のチェックなしでそのまま渡し、ポリシーが `grade` に変えます。
- 別名でもプロパティでもない名前なら、ポリシーのチェックの前に `PropertyReferenceException` が発生します。
- `STRICT` モードでルールにないプロパティを指定すると、`SortNotAllowedException`（`LQL-4040`）が発生します。Spring はこの例外を変換しません（[12.6](#126-transactions-and-exceptions) を参照）。

**HTTP 400 の応答。** lambdaQL には Web の依存関係がないので、HTTP のステータスを決めません。Web アプリケーションは、普通は `@RestControllerAdvice` の `@ExceptionHandler(SortNotAllowedException::class)` で 400 Bad Request を返します。
例外のメッセージには、エンティティクラスとソートの名前が入っています。クライアントにエンティティ名を見せたくなければ、`e.code()`（`"LQL-4040"`）だけを送ってください。

コード例: [`ManualKotlinRepositoryTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/kotlin/org/lambdaql/spring/ManualKotlinRepositoryTest.kt)（`spring-sort`）、[`ManualKotlinBeansTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/kotlin/org/lambdaql/spring/ManualKotlinBeansTest.kt)（`beans`、`sort-policy`）。

<a id="126-transactions-and-exceptions"></a>
## 12.6 トランザクションと例外

実行器は、現在のトランザクションに結び付く Spring の共有 `EntityManager` を使います。

**lambdaQL はメソッドに `@Transactional` を付けません。** 意図した設計です。
更新、削除、挿入、upsert には、呼び出し側のトランザクションが必要です。短縮メソッド（`deleteAll`、`updateOne` など）、ビルダー（`updateQuery()` など）、`QueryBuilder` の Bean のどれも同じです。

サービスのメソッドに `@Transactional` を付けるか、`TransactionTemplate` を使ってください。トランザクションがなければ、SQL を送る前に呼び出しが失敗します。

| 場合 | 動作 |
|---|---|
| `@Transactional` の中の選択 | lambdaQL は同じトランザクションで実行され、まだ flush されていない変更も照会します。ロールバックの後は、lambdaQL からもその変更は照会されません |
| `@Transactional` または `TransactionTemplate` の中の更新、削除、挿入、upsert | そのトランザクションで実行され、トランザクションと一緒にロールバックされます |
| トランザクションの外の更新、削除、挿入、upsert | SQL を送る前に失敗します。何も変更しません（次の表を参照） |

DML を自分のトランザクションで実行するビルダーは、`QueryBuilder(entityManagerFactory)` だけです（[7.8](07-dml.md#transactions) を参照）。Spring のアプリケーションでは、このビルダーを使わないでください（[12.8](#128-the-querybuilder-bean) を参照）。

エラーは、他のリポジトリーのメソッドと同じく、Spring の例外変換を通ります。

| 場合 | 例外 |
|---|---|
| `findOne` の条件に合う行が 2 つ以上 | JPA の `NonUniqueResultException` の代わりに、Spring の `IncorrectResultSizeDataAccessException` |
| `deleteOne` または `updateOne` の条件に合う行が 2 つ以上 | `IncorrectResultSizeDataAccessException`。何も変更しない |
| Spring の `Sort` の存在しないプロパティ | `PropertyReferenceException` |
| Spring の `Sort` の `ignoreCase()` | `UnsupportedOperationException` |
| `STRICT` モードでソートポリシーが許可しないソートの名前（[12.5](#sort-policy-with-spring)） | `SortNotAllowedException`（`LQL-4040`）、変換しない |
| リポジトリーの呼び出し（短縮メソッド、または本体を持つメソッドの中の `execute()`）で、トランザクションなしに更新、削除、挿入、upsert を実行 | `InvalidDataAccessApiUsageException`。Spring が JPA の `TransactionRequiredException` を変換した例外です。リポジトリーのメソッドの外（`QueryBuilder` の Bean）では JPA の `TransactionRequiredException` |
| ルールに違反した代入のラムダ式 | `LambdaQLException`（例: `TranslationException`、`LQL-3042`）。変換しない（[7.8](07-dml.md#rules-checked-at-analysis-time) を参照） |

`QueryBuilder` の Bean（12.8）はリポジトリーではないので、Spring の例外変換を通りません。この Bean で `one()` と `executeOne()` の条件に合う行が 2 つ以上あると、`NonUniqueResultException` が発生します。

<a id="127-other-fragments-and-jpaspecificationexecutor"></a>
## 12.7 他のフラグメントと `JpaSpecificationExecutor`

`LambdaQlExecutor<T>` は、複数のフラグメントの 1 つです。次の Kotlin のリポジトリーは、この構成をすべて一緒に使います。

| 一緒に使うもの | 動くか | 説明 |
|---|---|---|
| 自分で書いたフラグメント（`MemberRepositoryCustom` と `MemberRepositoryImpl`） | 動く | Spring Data がこれまでどおり `Impl` クラスを見つける |
| `JpaSpecificationExecutor<T>` | 動く | lambdaQL のラムダ式は引数が 1 つ、`Specification` のラムダ式は引数が 3 つなので、Kotlin も `findAll { it.isActive }` に lambdaQL の `findAll` を選ぶ。`Specification` は `Specification<Member> { root, _, cb -> ... }` と書く |
| `JpaRepository` のメソッド（`findAll()`、`count()`、`save(...)`） | 動く | 引数の型が違う |

<!-- region: ManualKotlinRepositories.kt#fragments -->
```kotlin
/** LambdaQlExecutor together with JpaSpecificationExecutor and a hand-written fragment. */
interface KtMemberSpecRepository : JpaRepository<Member, Long>, LambdaQlExecutor<Member>,
    JpaSpecificationExecutor<Member>, KtMemberRepositoryCustom

/** A hand-written fragment. */
interface KtMemberRepositoryCustom {
    fun countByHand(): Long
}

/** Its implementation: Spring Data still finds it by the name (fragment interface name + Impl). */
class KtMemberRepositoryCustomImpl(private val em: EntityManager) : KtMemberRepositoryCustom {
    override fun countByHand(): Long =
        em.createQuery("select count(m) from Member m", java.lang.Long::class.java).singleResult.toLong()
}
```

`findAll { it.isActive }`、`Specification` を受け取る `findAll`、`countByHand()`、`count()` を、同じリポジトリーで一緒に呼べます。

コード例: [`ManualKotlinRepositories.kt`](https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/kotlin/org/lambdaql/spring/ManualKotlinRepositories.kt)（`fragments`）。

<a id="128-the-querybuilder-bean"></a>
## 12.8 `QueryBuilder` の Bean

Spring Boot では、自動構成 `org.lambdaql.spring.boot.LambdaQlAutoConfiguration` が `QueryBuilder` の Bean を作ります。1 つのリポジトリーに属さないクエリは、この Bean をサービスに注入して書きます。

- Bean は、トランザクションに結び付く Spring の共有 `EntityManager` を使います。そのため、リポジトリーのメソッドと同じく、クエリが現在のトランザクションに参加します。
- Bean は、`EntityManagerFactory` がちょうど 1 つで、利用者が `QueryBuilder` の Bean を別に定義していない場合にだけ作られます。
- リポジトリーのメソッド（`LambdaQlExecutor<T>`）には、この Bean は要りません。
- この Bean で作った更新、削除、挿入、upsert の文にも、リポジトリーのメソッドと同じく呼び出し側のトランザクションが必要です。トランザクションがなければ、SQL を送る前に `TransactionRequiredException` が発生します。

<!-- region: ManualKotlinRepositories.kt#service -->
```kotlin
/** open: without the kotlin-spring compiler plugin, Spring needs an open class for the @Transactional proxy. */
@Service
open class KtMemberService(private val qb: QueryBuilder) {

    @Transactional(readOnly = true)
    open fun countByName(name: String): Long =
        qb.from(Member::class.java).where { it.name == name }.count()
}
```

`TransactionTemplate` の中で会員を保存した直後に `countByName` を呼ぶと、まだ flush されていない会員も件数に含まれます。サービスのクエリが呼び出し側のトランザクションに参加するからです。

Spring Boot なしで使う場合や、`EntityManagerFactory` の Bean が 2 つ以上ある場合は、Bean を自分で作ってください。自動構成と同じく、共有 `EntityManager` を使います。
[12.5](#sort-policy-with-spring) の `KtLambdaQlConfig` にある `queryBuilder` の Bean がその例です。この Bean は自動構成の Bean の代わりになり（`QueryBuilder` の Bean は `queryBuilder` の 1 つだけ）、呼び出し側のトランザクションに参加します。

`SharedEntityManagerCreator` は Spring のクラス（`org.springframework.orm.jpa`）です。
Spring のアプリケーションでは、`QueryBuilder(entityManagerFactory)` を使わないでください。このビルダーはクエリごとに自分の `EntityManager` を開き、この `EntityManager` は Spring のトランザクションに参加しません。

**`@DataJpaTest` のテスト。** `@DataJpaTest` のスライステストは、一部の自動構成だけを読み込みます。リポジトリーのメソッドは動きますが、`QueryBuilder` の Bean は作られません。

コード例: [`ManualKotlinRepositories.kt`](https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/kotlin/org/lambdaql/spring/ManualKotlinRepositories.kt)（`service`）。

<a id="129-kotlin-compile-settings"></a>
## 12.9 Kotlin のコンパイルの設定

Spring Data は、本体を持つリポジトリーのメソッドが、インターフェースの JVM の default メソッドのときだけ、その本体を実行します。

| Kotlin | 設定 |
|---|---|
| 2.2 以上 | 設定は不要。本体を持つメソッドは、デフォルトで JVM の default メソッドにコンパイルされます。 |
| 2.2 未満 | コンパイラーのオプション `-Xjvm-default=all` を追加します。このオプションがないとインターフェースにメソッドの本体がないので、Spring Data がメソッド名からクエリを作ろうとし、アプリケーションが起動しません（`No property ... found for type ...`）。 |
| すべてのバージョン | `kotlin-reflect` を追加します。ないと、Spring Data が `ClassNotFoundException: kotlin.reflect.jvm.ReflectJvmMapping` で失敗します。 |

```gradle-kts
// build.gradle.kts, only for Kotlin older than 2.2
kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}
```

Kotlin 2.2 未満の動作は、`-jvm-default=disable` で再現した結果です（実測）。

<a id="1210-not-implemented-yet"></a>
## 12.10 未実装の機能

- **Spring Boot のバージョン。** Spring Boot 3.5 と 4.1 以外のバージョンは確認していません（[13. Spring Boot 3 と 4](../13-spring-boot-versions.md) を参照）。
- Spring の `Sort` の **`ignoreCase()`**。

---

[Kotlin トラック](README.md) | 前: [11. エラーと言語](../11-errors-and-languages.md) | 次: [13. Spring Boot 3 と 4](../13-spring-boot-versions.md) | [このページの Java 版](../java/12-spring-data-jpa.md)
