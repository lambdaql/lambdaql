<a id="12-spring-data-jpa-kotlin"></a>
# 12. Spring Data JPA（Kotlin）

> [!IMPORTANT]
> このページは韓国語版のマニュアルより古い内容です。最新のルールは[韓国語のページ](../../ko/kotlin/12-spring-data-jpa.md)を参照してください。

[Kotlin トラック](README.md) | [マニュアルのホーム](../README.md) | 前: [11. エラーと言語](../11-errors-and-languages.md) | [このページの Java 版](../java/12-spring-data-jpa.md)

---

<a id="121-what-you-get"></a>
## 12.1 できること

Spring Data JPA では、リポジトリのインターフェースを書きます。lambdaQL はインターフェースを 1 つ追加します。`LambdaQlExecutor<T>` です。

リポジトリでこれを継承します。そして独自のクエリメソッドを、中で lambdaQL を使う **本体を持つインターフェースメソッド** として書きます。
これらのメソッドのために `...RepositoryImpl` クラスを書く必要はありません。

| 部分 | 何をするか | 節 |
|---|---|---|
| `LambdaQlExecutor<T>` | ラムダの条件を受け取る `findAll`、`findSet`、`findFirst`、`findOne`、`count`、`exists` と `query()` を、すべてのリポジトリに追加します | [12.3](#123-repository-setup)、[12.4](#124-executor-methods) |
| Spring の `Sort` と `Pageable` | `findAll(where, sort)`、`findSet(where, sort)` と、Spring の `Page` を返す `findAll(where, pageable)` | [12.5](#125-spring-sort-and-pageable) |
| 更新、削除、挿入、upsert | `deleteAll { ... }` や `updateOne({ ... }) { ... }` のような短縮メソッドは、1 回の呼び出しで文を実行します。`updateQuery()`、`deleteQuery()`、`insertQuery()`、`upsertQuery()` が、リポジトリのエンティティに対して [7. 更新、挿入、削除](07-dml.md) の文を始めます。どれも呼び出し側のトランザクションが必要です | [12.4](#124-executor-methods)、[12.6](#126-transactions-and-exceptions) |
| `QueryBuilder` の Bean | Spring Boot が、サービスで使う `QueryBuilder` の Bean を作ります | [12.8](#128-the-querybuilder-bean) |

<a id="122-modules"></a>
## 12.2 モジュール

lambdaQL には 3 つの成果物（アーティファクト）があります。コアは Spring に依存しないので、素の JPA でも使えます。

| 成果物 | 内容 | 必要になるとき |
|---|---|---|
| `lambdaql-core` | エンジンと `QueryBuilder`。Spring には依存しません | 常に。ほかの 2 つの成果物がこれを持ち込みます |
| `lambdaql-spring-data-jpa` | `LambdaQlExecutor<T>`、その Spring Data への登録、Spring Boot の自動構成（`QueryBuilder` の Bean） | Spring Boot なしで Spring Data JPA を使うとき |
| `lambdaql-spring-boot-starter` | コードはありません。`lambdaql-spring-data-jpa` と `spring-boot-starter-data-jpa` を持ち込みます | Spring Boot を使うとき |

成果物は **まだ Maven Central に公開していません**。先にソースからビルドし、ローカルの Maven リポジトリに公開してください（[2. はじめよう](02-getting-started.md#build-from-source) を参照）。バージョンは `1.0.0` です。

Spring Boot を使う場合、Gradle（Kotlin DSL）:

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("org.lambdaql:lambdaql-spring-boot-starter:1.0.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect")           // Spring Data needs it for Kotlin
}
```

Maven:

```xml
<dependency>
  <groupId>org.lambdaql</groupId>
  <artifactId>lambdaql-spring-boot-starter</artifactId>
  <version>1.0.0</version>
</dependency>
```

Maven では `kotlin-reflect` も追加してください（[12.9](#129-kotlin-compile-settings) を参照）。

Spring Boot を使わない場合は、自分の Spring Data JPA の構成（Hibernate と JDBC ドライバー）に `lambdaql-spring-data-jpa` を追加します。これは `lambdaql-core` と Spring Data JPA を持ち込みます。

```kotlin
dependencies {
    implementation("org.lambdaql:lambdaql-spring-data-jpa:1.0.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
}
```

```xml
<dependency>
  <groupId>org.lambdaql</groupId>
  <artifactId>lambdaql-spring-data-jpa</artifactId>
  <version>1.0.0</version>
</dependency>
```

必要な環境:

| 項目 | バージョン |
|---|---|
| Java（JVM） | 21 |
| Spring Boot | 3.4 以降（3.4.2 でテスト済み）。Spring Boot 4 はまだ検証していません |
| Spring Data JPA | 3.4 以降 |
| Hibernate | 6.6（Spring Boot 3.4 にすでに含まれています） |
| Kotlin | 2.2 以降。それより古い Kotlin については [12.9](#129-kotlin-compile-settings) を参照 |
| `kotlin-reflect` | Kotlin のために Spring Data が必要とします |

Spring Data JPA 3.3 以前は **サポートしません**。lambdaQL は実行器を `META-INF/spring.factories` に登録しますが、Spring Data がこのファイルからリポジトリのフラグメントを読むのは 3.4 からです。

<a id="123-repository-setup"></a>
## 12.3 リポジトリの設定

リポジトリのスーパータイプのリストに `LambdaQlExecutor<T>` を追加します。`T` はリポジトリのエンティティです。
設定も `Impl` クラスも書きません。

この章の例は、Java のテストのエンティティ `Member`（`id`、`name`、`active`、`grade`、`team`）と `Team`（`id`、`name`）を使います。Kotlin では `grade` は `Int?` なので、NULL になることがあります。

Spring モジュールの Kotlin のテストはまだありません。同じリポジトリを Java で書いたものは [`MemberRepository.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/sample/MemberRepository.java) でテストしています（H2、MariaDB、Oracle）。

```kotlin
interface MemberRepository : JpaRepository<Member, Long>, LambdaQlExecutor<Member> {

    fun activeMembers(): List<Member> =
        findAll { it.active }

    fun byName(name: String): Optional<Member> =
        findOne { it.name == name }

    fun topGraded(n: Int): List<Member> =
        query()
            .where { it.active }
            .orderBy { m, by -> by.desc(m.grade).asc(m.id) }
            .limit(n)
            .toList()

    // update, delete, insert: call inside a transaction
    fun gradeInactive(grade: Int): Int =
        updateQuery().set { it.grade = grade }.where { !it.active }.execute()

    fun deleteWithoutTeam(): Int =
        deleteQuery().where { it.team == null }.execute()

    fun addGuest(name: String): Int =
        insertQuery().values { it.name = name; it.active = false }.execute()
}
```

最後の 3 つのメソッドは、更新、削除、挿入の文を実行します。トランザクションが必要です（[12.6](#126-transactions-and-exceptions) を参照）。

仕組み:

- **本体を持つインターフェースメソッドは、Spring Data 自身が実行します。** Spring Data はそれらからクエリを作ろうとしません。本体を呼び出すだけです。lambdaQL はこの動きを変えません。Kotlin 2.2 より前については [12.9](#129-kotlin-compile-settings) を参照してください。
- **`LambdaQlExecutor<T>` は Spring Data のリポジトリのフラグメントです。** その実装は `lambdaql-spring-data-jpa` の `META-INF/spring.factories` に書かれています。Spring Data はこのファイルを読み、`LambdaQlExecutor<T>` を継承するすべてのリポジトリに実装を追加します。
- **実行器のオブジェクトは 1 つで、すべてのリポジトリが共有します。** それでも、各呼び出しは自分のリポジトリのエンティティを使います。たとえば `TeamRepository` の `teams.count { ... }` は `Team` の行を数えます（Java でテスト済み、[`AutoConfigurationTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/AutoConfigurationTest.java)）。

ルール:

| # | ルール |
|---|---|
| 1 | リポジトリは `LambdaQlExecutor<T>` を **直接** 継承する必要があります（そのリポジトリ自身のスーパータイプのリストに書きます）。 |
| 2 | この目的で `LambdaQlExecutor<T>` を継承するインターフェースに `@NoRepositoryBean` を付けないでください。付けると、Spring Data は lambdaQL の実装を追加しません。 |
| 3 | lambdaQL のクエリは **本体を持つインターフェースメソッド** かサービスの中に書いてください。本体のないメソッドは、これまでどおり Spring Data のクエリメソッド（`findByName` など）です。 |
| 4 | Spring Boot がなくても、リポジトリの部分は動きます。普通の Spring Data だからです。`QueryBuilder` の Bean だけは自動では作られません（[12.8](#128-the-querybuilder-bean) を参照）。 |

<a id="124-executor-methods"></a>
## 12.4 実行器のメソッド

`Where<T>` は `where { ... }` と同じ条件のラムダです（[3. 条件](03-where.md) を参照）。`SortSpec<T>` は lambdaQL のソートです（[4.10](04-order-paging.md#410-reusable-and-external-sorts) を参照）。
`Sort`、`Pageable`、`Page` は Spring Data の型です。

| メソッド | 戻り値 | 説明 |
|---|---|---|
| `query()` | `SelectQuery<T>` | リポジトリのエンティティに対するクエリを始めます。短いメソッドではできないこと（`where` の追加の呼び出し、`limit`、`offset`、実行時の `Direction`）すべてに使います |
| `from(Class<E>)` | `SelectQuery<E>` | 同じ永続化ユニットの別のエンティティに対するクエリを始めます |
| `findAll(Where<T>)` | `List<T>` | 条件に合うすべての行。順序は決まっていません |
| `findAll(Where<T>, SortSpec<T>)` | `List<T>` | 条件に合うすべての行を、ソートして返します |
| `findAll(Where<T>, Sort)` | `List<T>` | Spring の `Sort` でソートします（[12.5](#125-spring-sort-and-pageable) を参照）。`Sort.unsorted()` では順序を付けません |
| `findAll(Where<T>, Pageable)` | `Page<T>` | 1 ページ分です。count のクエリは必要なときだけ実行します。`Pageable.unpaged()` ではすべての行を返します |
| `findSet(Where<T>)` | `SequencedSet<T>` | 条件に合うすべての行を Set で返します。順序は決まっていません（`findAll(Where<T>)` と同じ）。`distinct` は付けません。Set が行数より小さいと `IllegalStateException`（[`toSet()`](02-getting-started.md#toset) を参照） |
| `findSet(Where<T>, SortSpec<T>)` | `SequencedSet<T>` | 条件に合うすべての行を、ソートの順序で Set として返します |
| `findSet(Where<T>, Sort)` | `SequencedSet<T>` | 同じく、Spring の `Sort` でソートします（[12.5](#125-spring-sort-and-pageable) を参照） |
| `findFirst(Where<T>)` | `Optional<T>` | 条件に合う行のうち、id が最も小さい行 |
| `findFirst(Where<T>, SortSpec<T>)` | `Optional<T>` | その順序での最初の行。1 行だけ読みます |
| `findFirst(Where<T>, Sort)` | `Optional<T>` | Spring の `Sort` の順序での最初の行 |
| `findOne(Where<T>)` | `Optional<T>` | 条件に合う唯一の行。2 行以上なら `IncorrectResultSizeDataAccessException` |
| `count(Where<T>)` | `Long` | 条件に合う行の数 |
| `exists(Where<T>)` | `Boolean` | 条件に合う行があれば `true`。読むのは最大 1 行で、エンティティは読み込みません |
| `deleteAll(Where<T>)` | `Int` | 条件に合うすべての行を、1 つの SQL 文で削除します（一括）。Spring Data の `deleteAll()` とは違います（[後述](#deleteallwhere-is-not-spring-datas-deleteall) を参照）。トランザクションが必要です |
| `deleteOne(Where<T>)` | `Int` | 条件に合う唯一の行を、[1 行保護](#one-row-protection-deleteone-and-updateone) 付きで削除します。0 か 1 を返します。トランザクションが必要です |
| `updateAll(Where<T>, Assign<T>)` | `Int` | 条件に合うすべての行を変更します。条件が先で、代入は末尾のラムダです。トランザクションが必要です |
| `updateOne(Where<T>, Assign<T>)` | `Int` | 条件に合う唯一の行を、1 行保護付きで変更します。0 か 1 を返します。トランザクションが必要です |
| `insertOne(Assign<T>)` | `Int` | 1 行を挿入します。1 を返します。トランザクションが必要です |
| `insertAll(Collection<L>, AssignEach<T, L>)` | `Int` | 要素ごとに 1 行を挿入します（1 つの文の値は最大 500 個、ルール U13）。トランザクションが必要です |
| `upsertOne(Assign<T>)` | `Int` | 1 行を挿入します。同じ `@Id` の行があれば、その行を変更します。データベースの行数を返します。トランザクションが必要です |
| `upsertAll(Collection<L>, AssignEach<T, L>)` | `Int` | 要素ごとに 1 行を upsert します（1 行に 1 つの文）。トランザクションが必要です |
| `updateQuery()` | `UpdateQuery<T>` | 更新を始めます: `updateQuery().set { ... }.where { ... }.execute()`（[7.2](07-2-update.md) を参照）。トランザクションが必要です |
| `deleteQuery()` | `DeleteQuery<T>` | 削除を始めます: `deleteQuery().where { ... }.execute()`（[7.4](07-4-delete.md) を参照）。トランザクションが必要です |
| `insertQuery()` | `InsertQuery<T>` | 挿入を始めます: `insertQuery().values { ... }.execute()`（[7.5](07-5-insert.md) を参照）。トランザクションが必要です |
| `upsertQuery()` | `UpsertQuery<T>` | `@Id` をキーとする upsert を始めます（[7.6](07-6-upsert.md) を参照）。トランザクションが必要です |

短いメソッドは、`query()` に結果の関数を付けたものと同じです。`findAll(w)` は `query().where(w).toList()`、`findSet(w)` は `query().where(w).toSet()`、`findFirst(w)` は `query().where(w).first()`、`findOne(w)` は `query().where(w).one()` に当たります。結果の関数は [結果を受け取る](02-getting-started.md#getting-the-result) で説明しています。
`findAll { ... }` と `count { ... }` は、`JpaRepository` の `findAll(Sort)`、`findAll(Example)`、`count()` とぶつかりません。引数の型が違うからです。
メソッドは Java の `Optional` を返します。nullable な値がほしい場合は `.orElse(null)` を使ってください。

**`query()`: 完全なクエリ。** 12.3 の `topGraded` がこれを使っています。

次の例では、`repository` は `MemberRepository` です。同じ呼び出しを Java で [`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java) でテストしています（リージョン `find-all-sort`、`find-first`、`find-one`、`count-exists`、`find-set`）。

```kotlin
val byGrade = SortSpec.of(Member::class.java) { m, by -> by.desc(m.grade).asc(m.id) }
val active: List<Member> = repository.findAll({ it.active }, byGrade)

// the active member with the highest grade
val best: Optional<Member> = repository.findFirst({ it.active },
    SortSpec.of(Member::class.java) { m, by -> by.desc(m.grade) })

val kim: Optional<Member> = repository.findOne { it.name == "Kim" }
repository.findOne { it.active }        // two or more rows: Spring's IncorrectResultSizeDataAccessException

val activeCount: Long = repository.count { it.active }
val anyInTeamB: Boolean = repository.exists { it.team?.name == "B" }

val byName: SequencedSet<Member> = repository.findSet({ it.active }, Sort.by("name"))
val anyOrder: SequencedSet<Member> = repository.findSet { it.active }   // no fixed order, like findAll { }
```

<a id="dml-shortcuts"></a>
### DML の短縮メソッド

8 つのメソッドが、更新、削除、挿入、upsert を 1 回の呼び出しで実行します。

| 短縮メソッド | 同じもの |
|---|---|
| `deleteAll { where }` | `deleteQuery().where { where }.execute()` |
| `deleteOne { where }` | `deleteQuery().where { where }.executeOne()` |
| `updateAll({ where }) { set }` | `updateQuery().set { set }.where { where }.execute()` |
| `updateOne({ where }) { set }` | `updateQuery().set { set }.where { where }.executeOne()` |
| `insertOne { values }` | `insertQuery().values { values }.execute()` |
| `insertAll(rows) { m, r -> values }` | `insertQuery().values(rows) { m, r -> values }.execute()` |
| `upsertOne { values }` | `upsertQuery().values { values }.execute()` |
| `upsertAll(rows) { m, r -> values }` | `upsertQuery().values(rows) { m, r -> values }.execute()` |

- **条件が先です。** 代入は末尾のラムダです: `updateAll({ !it.active }) { it.grade = 7 }`。
- **`All`** は、条件に合うすべての行（削除、更新）、またはリストのすべての要素（挿入、upsert）を意味します。
- **`One`** は、削除と更新では 1 行保護（次の項を参照）、挿入と upsert では 1 行を意味します。
- 呼び出し側のトランザクションが必要です（[12.6](#126-transactions-and-exceptions) を参照）。

12.3 と同じく、本体を持つインターフェースのメソッドです。ここの `gradeInactive` と `addGuest` は、12.3 のメソッドの短い形です。`NewMember` は `data class NewMember(val name: String, val grade: Int)` です。

```kotlin
fun purgeWithoutTeam(): Int = deleteAll { it.team == null }
fun deleteByName(name: String): Int = deleteOne { it.name == name }
fun gradeInactive(grade: Int): Int = updateAll({ !it.active }) { it.grade = grade }
fun rename(id: Long, name: String): Int = updateOne({ it.id == id }) { it.name = name }
fun addGuest(name: String): Int = insertOne { it.name = name; it.active = false }
fun addMembers(rows: List<NewMember>): Int = insertAll(rows) { m, r -> m.name = r.name; m.grade = r.grade; m.active = true }
fun putMember(id: Long, name: String): Int = upsertOne { it.id = id; it.name = name; it.active = true }
```

これらの Kotlin の書き方は、kotlinc 2.3.21 でコンパイルだけを確認しました（実測）。ビルドにはまだ Kotlin のテストがありません（未測定）。同じ呼び出しは Java で [`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java) がテストしています（リージョン `dml-shortcuts` と `dml-one`）。

<a id="one-row-protection-deleteone-and-updateone"></a>
### 1 行保護: `deleteOne` と `updateOne`

`deleteOne { ... }` と `updateOne({ ... }) { ... }` は、2 行以上を変更することはありません。

1. lambdaQL は一致する行の id を最大 2 つ読みます。
2. id がない: 何も変更しません。結果は 0 です。
3. id が 2 つ: 何も変更しません。メソッドは `IncorrectResultSizeDataAccessException` を投げます。
4. id が 1 つ: lambdaQL はその行を `id = :p and (条件)` で変更します。結果は 1 です。その間に行が条件に一致しなくなった場合は 0 です。

文そのものが 1 つの id を指定するので、その間に別のトランザクションが一致する行を追加しても、2 行目を変更することはありません。この保護はロールバックに頼りません。

コアの API にも同じ保護があります: `executeOne()`（[7.4](07-4-delete.md#delete-exactly-one-row-executeone) を参照）。

```kotlin
val renamed = repository.updateOne({ it.id == kimId }) { it.name = "Kim2" }   // exactly one row
repository.deleteOne { it.active }   // two or more rows match: nothing changes, IncorrectResultSizeDataAccessException
```

<a id="deleteallwhere-is-not-spring-datas-deleteall"></a>
### `deleteAll(where)` は Spring Data の `deleteAll()` とは違う

| メソッド | 定義元 | 削除の方法 | cascade、`@PreRemove` |
|---|---|---|---|
| `deleteAll()` | `CrudRepository` | すべてのエンティティを読み込み、1 つずつ削除する | 実行される |
| `deleteAll(Iterable)` | `CrudRepository` | 渡されたエンティティを 1 つずつ削除する | 実行される |
| `deleteAll { ... }`（`Where<T>`） | `LambdaQlExecutor<T>` | 1 つの SQL `delete` 文（一括） | **実行されない** |

`deleteAll { ... }` はエンティティを読み込みません。すべての一括文と同じように、すでに読み込まれたエンティティは変更しません（ルール U16）。

多くの行を速く削除するときに使ってください。cascade や `@PreRemove` が必要なときは Spring Data のメソッドを使ってください。

コンパイラーは引数でメソッドを選びます。引数なし、`Iterable`、ラムダのどれかです。これは kotlinc 2.3.21 と javac のバイトコードで確認しました（実測）。

<a id="builders-for-complex-statements"></a>
### 複雑な文のためのビルダー

`updateQuery()`、`deleteQuery()`、`insertQuery()`、`upsertQuery()` は完全な文を始めます。短縮メソッドでできないことに使ってください: 複数回の `set`、`all()`、`orderBy { ... }.limit(n)`（[7.4](07-4-delete.md#delete-the-first-rows-limit) を参照）、`toHql()`、`onConflict { ... }`。

名前が `Query` で終わるので、`delete(...)` のような `JpaRepository` のメソッドとぶつかりません。文は `QueryBuilder` のものと同じです（[7. 更新、挿入、削除](07-dml.md) を参照）。対象になる行は、同じ `where` ラムダの選択と同じです。

短縮メソッドと同じく、呼び出し側のトランザクションが必要です。すべての JPA の一括文と同じように、トランザクションですでに読み込まれたエンティティは変更しません。

12.3 の `gradeInactive`、`deleteWithoutTeam`、`addGuest` がこれらを使っています。同じメソッドは Java の [`MemberRepository.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/sample/MemberRepository.java)（リージョン `dml`）で、upsert は [`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java)（リージョン `upsert`）でテストしています。

```kotlin
// teams is a TeamRepository, tx a Spring TransactionTemplate
val changed = tx.execute { teams.upsertQuery().values { it.id = teamId; it.name = "A2" }.execute() }
```

upsert はデータベースの行数を返します。挿入では 1、更新では H2 と Oracle が 1、MariaDB が 2 です（[7.6](07-6-upsert.md#the-row-count) を参照）。

<a id="125-spring-sort-and-pageable"></a>
## 12.5 Spring の `Sort` と `Pageable`

`findAll(where, sort)`、`findSet(where, sort)`、`findFirst(where, sort)`、`findAll(where, pageable)` は Spring Data の型を受け取ります。
lambdaQL は、各 `Sort.Order` を `SortSpec.ofPath` で 1 つのソートキーに変えます（[4.10](04-order-paging.md#410-reusable-and-external-sorts) を参照）。

| Spring | lambdaQL での扱い |
|---|---|
| プロパティ名（`"grade"`、`"team.name"`） | Spring Data 自身のリポジトリと同じく、Spring Data の `PropertyPath` でチェックします。知らない名前なら `PropertyReferenceException` になります。パスは、エンティティではなく基本型の値で終わる必要があります。`team.name` のような to-one のパスは `left join` を使うので、チームのない行も残ります。その後、ソートポリシーが名前をチェックします（[下](#sort-policy-with-spring) を参照） |
| ソートポリシーのエイリアス（`"level"`） | `PropertyPath` のチェックをせず、そのまま渡します。ソートポリシーがパスに変えます |
| `NullHandling.NATIVE`（デフォルト）での `ASC` / `DESC` | lambdaQL の NULL のルールです。昇順では NULL が先頭、降順では NULL が末尾です。順序はどのデータベースでも同じです（[4.4](04-order-paging.md#44-null-ordering-is-the-same-on-every-database) を参照） |
| `NullHandling.NULLS_FIRST` / `NULLS_LAST` | 書いたとおりに使います（`ASC_NULLS_FIRST`、`DESC_NULLS_LAST` など） |
| `ignoreCase()` | まだサポートしていません。`UnsupportedOperationException` を投げます |
| `Sort.unsorted()` | `order by` を付けません |

Spring Data JPA 自身のリポジトリは、`NATIVE` の NULL の位置をデータベースに任せます。そのため、そこではデータベースごとに順序が変わることがあります。lambdaQL は代わりに自分のルールを使います。

`findAll(where, pageable)` でのページング:

- ページ番号は Spring Data と同じく 0 から始まります。lambdaQL はページを `limit` と `offset` で読みます。
- 総数は `count` のクエリから得ます。ページだけで総数がわかるとき（たとえば、最初のページの行数がページサイズより少ないとき）、Spring はこのクエリを省きます。
- ソートキーに id が含まれていないとき、lambdaQL は id を最後のキーとして追加します。そのため、行がページの間で動きません（[タイブレーカー](04-order-paging.md#tie-breaker-automatic-id) を参照）。ソートのないページは id の順になります。
- `Pageable.unpaged()` は、すべての行を 1 つのページで返します。

同じ呼び出しを Java で [`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java) でテストしています（リージョン `find-all-spring-sort` と `page`）。

```kotlin
val rows: List<Member> = repository.findAll({ it.active },
    Sort.by(Sort.Order.desc("grade"), Sort.Order.asc("team.name"), Sort.Order.asc("id")))
repository.findAll({ it.active }, Sort.by("nickname"))   // no such property: PropertyReferenceException

// page 1 (the second page) of the active members by name; the total comes from a count query
val page: Page<Member> = repository.findAll({ it.active }, PageRequest.of(1, 2, Sort.by("name")))
```

**リクエストパラメーターからのソート。** Spring MVC は、`?page=1&size=20&sort=name,desc` のようなリクエストパラメーターから `Pageable` を作れます。
ソートポリシーも `@Sortable` フィールドもなければ、永続プロパティならどれでも受け付けるので、ユーザーはどのカラムでもソートできます。このとき lambdaQL は、エンティティと名前ごとに 1 回、`LQL-5003` を `INFO` レベルでログに出します。
一部の名前だけを許可するには、次のソートポリシーを使ってください。

<a id="sort-policy-with-spring"></a>
### Spring でのソートポリシー

ソートポリシー（決定 D43）は、どの外部のソートの名前を許可するかを決めます。`SortRules`、`SortMode` とルールは [4.10](04-order-paging.md#sort-policy) で説明しています。
Spring の `Sort` や `Pageable` は常に外部のソートなので、ポリシーはその中の名前をすべてチェックします。ソートのラムダはチェックされません。

**ルールの出どころ。** Spring Boot では、自動構成が `EntityManagerFactory` のポリシーを作り、すべてのシングルトン Bean ができた後で登録します。
読むのは次の 3 か所です。

| 出どころ | 与えるもの |
|---|---|
| `SortPolicy` の Bean | モードと、ラムダで書いた `SortRules` |
| プロパティ `lambdaql.sort.*` | モードと、文字列で書いたルール |
| `@Sortable` フィールド（[4.10](04-order-paging.md#rules-from-sortable)） | Bean にもプロパティにもルールがないエンティティのルール |

- 1 つのエンティティのルールは、表の順で 1 か所からだけ取られます。2 か所のルールは合わせられません。
- `lambdaql.sort.mode` は Bean のモードより優先されます。どちらもなければ、モードは `INFO` です。
- これが働くのは `EntityManagerFactory` がちょうど 1 つのときで、`QueryBuilder` の Bean と同じ条件です（[12.8](#128-the-querybuilder-bean) を参照）。そうでない場合は、`SortPolicy.register(emf, policy)` を自分で呼んでください。
- Bean もプロパティもなければ何も登録されないので、デフォルトが使われます: `INFO`、ルールは `@Sortable` だけ。

**プロパティ。**

| プロパティ | 意味 |
|---|---|
| `lambdaql.sort.mode` | `OFF`、`INFO`（デフォルト）、`WARN`、`STRICT` |
| `lambdaql.sort.allow.<Entity>` | 許可するパスをカンマで区切ったもの。例: `name,team.name` |
| `lambdaql.sort.alias.<Entity>.<外部の名前>` | エイリアスのパス。例: `lambdaql.sort.alias.Member.level=grade` |

**SARGable モードのプロパティ。** `lambdaql.sargable.mode` は `OFF`、`WARN`（デフォルト）、`STRICT` のいずれかです（決定 D50）。自動構成が `lambdaql.sort.mode` と同じ方法で登録します。`STRICT` では、インデックスを使えない条件の解析が `NonSargableException`（`LQL-3066`）で失敗します（[8.9](08-procedures-functions.md#89-warning-functions-on-columns-and-indexes-sargable) を参照）。

`<Entity>` には JPA のエンティティ名を書きます。既定は単純クラス名で、`@Entity(name = ...)` で変えられます。

エンティティ名は永続化ユニットの中で一意です（同じ名前が 2 つあると Hibernate が起動時に止まります）。そのため、パッケージが違う同じ名前のクラスが混ざることはありません。知らない名前なら、アプリケーションの起動が `IllegalStateException`（LQL-4042）で止まります。
パスは、ルールを最初に使うときにエンティティと照らし合わせられます（4.10 の [文字列によるルール](04-order-paging.md#rules-as-strings) を参照）。

プロパティは Java でも Kotlin でも同じです。Java のテスト [`SortPropertiesTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/SortPropertiesTest.java) で確かめています（リージョン `properties`、`properties-use`。[このページの Java 版](../java/12-spring-data-jpa.md#sort-policy-with-spring) を参照）。このテストは、自動構成がポリシーを登録することも確かめています。
`application.properties` の例:

```properties
lambdaql.sort.mode=STRICT
lambdaql.sort.allow.Member=name,team.name
lambdaql.sort.alias.Member.level=grade
```

Kotlin での呼び出し（設計上の例）:

```kotlin
val byLevel: List<Member> = repository.findAll({ it.active }, Sort.by(Sort.Order.desc("level")))
val byTeam: List<Member> = repository.findAll({ it.active }, Sort.by("team.name", "name"))
repository.findAll({ it.active }, Sort.by("grade"))   // not in the rules: SortNotAllowedException
```

**`SortPolicy` の Bean。** コンパイラーがチェックするルールを使うには、`SortPolicy` の Bean を定義してください。同じ自動構成の処理がそれを登録します。
同じポリシーは Java で [`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java) でテストされています（リージョン `sort-policy`、手で登録）。
次は設計上の例です。

```kotlin
@Bean
fun sortPolicy(): SortPolicy = SortPolicy.of(SortMode.STRICT,
    SortRules.of(Member::class.java)
        .allow { it.name }
        .alias("level") { it.grade })

// the client sends "level"; lambdaQL sorts by grade
val rows: List<Member> = repository.findAll({ it.active }, Sort.by(Sort.Order.desc("level"), Sort.Order.asc("name")))
repository.findAll({ it.active }, Sort.by("grade"))   // not in the rules: SortNotAllowedException
```

- `level` のようなエイリアスは `Member` のプロパティではありません。lambdaQL は Spring の `PropertyPath` のチェックをせずにそのまま渡し、ポリシーが `grade` に変えます。
- エイリアスでもプロパティでもない名前は、ポリシーに聞く前に `PropertyReferenceException` になります。
- `STRICT` モードでは、ルールにないプロパティは `SortNotAllowedException`（`LQL-4040`）になります。Spring はこれを変換しません（[12.6](#126-transactions-and-exceptions) を参照）。

**HTTP 400。** lambdaQL は Web への依存を持たないので、HTTP のステータスを決めません。Web アプリケーションは通常、`SortNotAllowedException` に 400 Bad Request で応答します。
次のハンドラーは例です（テストされていません）。

```kotlin
@RestControllerAdvice
class SortErrorHandler {

    @ExceptionHandler(SortNotAllowedException::class)
    fun sortNotAllowed(e: SortNotAllowedException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.message).apply {
            setProperty("code", e.code())      // "LQL-4040"
        }
}
```

メッセージにはエンティティクラスとソートの名前が入ります。クライアントにエンティティ名を見せたくない場合は、`e.code()` だけを送ってください。

<a id="126-transactions-and-exceptions"></a>
## 12.6 トランザクションと例外

実行器は Spring の共有 `EntityManager` を使います。これは現在のトランザクションに結び付いています。

**lambdaQL はメソッドに `@Transactional` を付けません。** これは意図した設計です（決定 D41）。
更新、削除、挿入、upsert には呼び出し側のトランザクションが必要です。これは短縮メソッド（`deleteAll`、`updateOne` など）、ビルダー（`updateQuery()` など）、`QueryBuilder` の Bean のすべてに当てはまります。

サービスのメソッドに `@Transactional` を付けるか、`TransactionTemplate` を使ってください。トランザクションがないと、呼び出しは SQL を送る前に失敗します。

| ケース | どうなるか |
|---|---|
| `@Transactional` の中の選択 | lambdaQL は同じトランザクションで実行されます。まだ flush されていない変更も見えます。ロールバックの後は、それらの変更は lambdaQL からも見えなくなります（Java でテスト済み、`ExecutorTest`） |
| トランザクションの外の選択 | Spring は呼び出しごとに新しい `EntityManager` を使い、呼び出しの後で閉じます。そのため、返されたエンティティは detached の状態です。これは Spring の通常の動きで、lambdaQL ではテストしていません |
| `@Transactional` や `TransactionTemplate` の中の更新、削除、挿入、upsert | そのトランザクションで実行され、一緒にロールバックされます（Java でテスト済み） |
| トランザクションの外の更新、削除、挿入、upsert | SQL を送る前に失敗します。何も変更されません（Java でテスト済み、次の表を参照） |

自分のトランザクションで DML を実行するのは `QueryBuilder(entityManagerFactory)` だけです（[7.8](07-dml.md#transactions) を参照）。Spring のアプリケーションでは使わないでください（[12.8](#128-the-querybuilder-bean) を参照）。

エラーは、ほかのリポジトリのメソッドと同じく、Spring の例外変換を通ります。

| ケース | 例外 |
|---|---|
| `findOne` が 2 行以上に一致する | JPA の `NonUniqueResultException` ではなく、`IncorrectResultSizeDataAccessException`（Spring）（Java でテスト済み） |
| `deleteOne` や `updateOne` が 2 行以上に一致する | `IncorrectResultSizeDataAccessException`。何も変更されません（Java でテスト済み） |
| Spring の `Sort` に知らないプロパティがある | `PropertyReferenceException`（Java でテスト済み） |
| Spring の `Sort` で `ignoreCase()` を使う | `UnsupportedOperationException`（Java でテスト済み） |
| `STRICT` モードで、ソートポリシーが許可しないソートの名前（[12.5](#sort-policy-with-spring)） | `SortNotAllowedException`（`LQL-4040`）。変換されません（Java でテスト済み） |
| リポジトリの呼び出し（短縮メソッド、または本体を持つメソッドの中の `execute()`）で、トランザクションなしに更新、削除、挿入、upsert を実行する | `InvalidDataAccessApiUsageException`（Java でテスト済み）。JPA の `TransactionRequiredException` から Spring が作ります。リポジトリのメソッドの外（`QueryBuilder` の Bean）では JPA の `TransactionRequiredException` |
| ルールに違反する代入のラムダ（U1、U3、U4、U6） | `LambdaQLException`（例: `TranslationException`、`LQL-3042`）。変換されません（[7.8](07-dml.md#rules-checked-at-analysis-time) を参照） |

`QueryBuilder` の Bean（12.8）はリポジトリではないので、Spring はそのエラーを変換しません。そこでは、2 行以上に一致する `one()` と `executeOne()` は `NonUniqueResultException` を投げます。

<a id="127-other-fragments-and-jpaspecificationexecutor"></a>
## 12.7 ほかのフラグメントと `JpaSpecificationExecutor`

`LambdaQlExecutor<T>` は数あるフラグメントの 1 つです。Java のテストのリポジトリは、次のものをすべて同時に使っています（Java でテスト済み、`ExecutorTest`）。

| 一緒に使うもの | 動くか | 説明 |
|---|---|---|
| 手で書いたフラグメント（`MemberRepositoryCustom` と `MemberRepositoryImpl`） | はい | Spring Data はいつもどおり `Impl` クラスを見つけます |
| `JpaSpecificationExecutor<T>` | はい（Java） | lambdaQL のラムダの引数は 1 つ、`Specification` のラムダの引数は 3 つなので、Java は必ず正しい `findAll` を選びます。Kotlin のラムダではまだテストしていません |
| `JpaRepository` のメソッド（`findAll()`、`count()`、`save(...)`） | はい | 引数の型が違います |

```kotlin
interface MemberRepository : JpaRepository<Member, Long>, LambdaQlExecutor<Member>,
    JpaSpecificationExecutor<Member>, MemberRepositoryCustom {
    // ...
}

class MemberRepositoryImpl(/* ... */) : MemberRepositoryCustom {   // still found by Spring Data
    // ...
}
```

<a id="128-the-querybuilder-bean"></a>
## 12.8 `QueryBuilder` の Bean

Spring Boot では、自動構成 `org.lambdaql.spring.boot.LambdaQlAutoConfiguration` が `QueryBuilder` の Bean を作ります。1 つのリポジトリに属さないクエリのために、これをサービスに注入してください。

- この Bean は、トランザクションに結び付いた Spring の共有 `EntityManager` を使います。そのクエリは、リポジトリのメソッドと同じく、現在のトランザクションに参加します。
- この Bean が作られるのは、`EntityManagerFactory` がちょうど 1 つで、自分で `QueryBuilder` の Bean を定義していないときだけです。
- リポジトリのメソッド（`LambdaQlExecutor<T>`）には、この Bean は必要ありません。
- この Bean の更新、削除、挿入、upsert の文には、リポジトリのメソッドと同じく、呼び出し側のトランザクションが必要です。トランザクションがないと、SQL を送る前に `TransactionRequiredException` を投げます。

この Bean は Java で [`AutoConfigurationTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/AutoConfigurationTest.java) でテストしています（リージョン `query-builder-bean`）。トランザクションの中で、まだ flush されていないメンバーが数えられます。

```kotlin
@Service
class MemberService(private val qb: QueryBuilder) {

    @Transactional(readOnly = true)
    fun countByName(name: String): Long =
        qb.from(Member::class.java).where { it.name == name }.count()
}
```

Spring Boot を使わない場合や、`EntityManagerFactory` の Bean が 2 つ以上ある場合は、Bean を自分で作ってください。自動構成と同じように、共有 `EntityManager` を使います。

```kotlin
@Bean
fun queryBuilder(entityManagerFactory: EntityManagerFactory) =
    // the same as the auto-configuration: a shared, transaction-bound EntityManager
    QueryBuilder(SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory))
```

`SharedEntityManagerCreator` は Spring のクラスです（`org.springframework.orm.jpa`）。
Spring のアプリケーションでは `QueryBuilder(entityManagerFactory)` を使わないでください。このビルダーはクエリごとに自分の `EntityManager` を開き、その `EntityManager` は Spring のトランザクションに参加しません。

**`@DataJpaTest` を使うテスト。** `@DataJpaTest` のスライステストは、一部の自動構成だけを読み込みます。そこでもリポジトリのメソッドは動きますが、`QueryBuilder` の Bean は作られません。
テストでこの Bean が必要な場合は、テストクラスに `@ImportAutoConfiguration(LambdaQlAutoConfiguration::class)` を付けてください（まだテストしていません）。

<a id="129-kotlin-compile-settings"></a>
## 12.9 Kotlin のコンパイル設定

Spring Data が本体を持つリポジトリのメソッドを実行するのは、それがインターフェースの JVM の default メソッドになっている場合だけです。

| Kotlin | すること |
|---|---|
| 2.2 以降 | 何もしません。本体を持つメソッドは、デフォルトで JVM の default メソッドになります。 |
| 2.2 より前 | コンパイラーオプション `-Xjvm-default=all` を追加します。これがないと、インターフェースのメソッドに本体がありません。すると Spring Data がメソッド名からクエリを作ろうとし、アプリケーションが起動しません（`No property ... found for type ...`）。 |
| すべてのバージョン | `kotlin-reflect` を追加します。これがないと、Spring Data は `ClassNotFoundException: kotlin.reflect.jvm.ReflectJvmMapping` で失敗します。 |

```kotlin
// build.gradle.kts, only for Kotlin older than 2.2
kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}
```

これらの設定はプロトタイプで確認しました（実測）。kotlinc 2.3.21 を使い、Kotlin 2.2 より前の動きは `-jvm-default=disable` で再現しました。lambdaQL のテストスイートには、まだ Kotlin のリポジトリがありません。

<a id="1210-not-implemented-yet"></a>
## 12.10 まだ実装されていないもの

- **Spring Boot 4。** 自動構成は Spring Boot 4 のクラス名も書いていますが、テストしたのは Spring Boot 3.4.2 だけです。
- Spring の `Sort` の **`ignoreCase()`**。
- **Kotlin のリポジトリ** のテスト。

---

[Kotlin トラック](README.md) | 前: [11. エラーと言語](../11-errors-and-languages.md) | [このページの Java 版](../java/12-spring-data-jpa.md)
