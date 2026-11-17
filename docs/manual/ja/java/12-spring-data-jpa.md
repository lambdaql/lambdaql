<a id="12-spring-data-jpa-java"></a>
# 12. Spring Data JPA（Java）

> [!NOTE]
> **状況: クエリの実行器と Spring Boot の自動構成は実装済みです。**
> `LambdaQlExecutor<T>`、Spring の `Sort` / `Pageable` への対応（ソートポリシーを含む）、`QueryBuilder` の Bean は、Spring Boot 3.4.2 を使って H2、MariaDB 10.11、Oracle 23.26 でテスト済みです。
> 更新、削除、挿入、upsert のメソッドもテスト済みです。短縮メソッド（`deleteAll`、`deleteOne`、`updateAll`、`updateOne`、`insertOne`、`insertAll`、`upsertOne`、`upsertAll`）とビルダー（`updateQuery()`、`deleteQuery()`、`insertQuery()`、`upsertQuery()`）です。これらには呼び出し側のトランザクションが必要です。lambdaQL は `@Transactional` を付けません（決定 D41）。
> **まだ検証していないもの:** Spring Boot 4。
> **(テスト済み)** と書かれた例は、テストスイートからコピーしたものです。
> 全体の状況は [Java トラックの README](README.md#status) を参照してください。

[Java トラック](README.md) | [マニュアルのホーム](../README.md) | 前: [11. エラーと言語](../11-errors-and-languages.md) | [このページの Kotlin 版](../kotlin/12-spring-data-jpa.md)

---

<a id="121-what-you-get"></a>
## 12.1 できること

Spring Data JPA では、リポジトリのインターフェースを書きます。lambdaQL はインターフェースを 1 つ追加します。`LambdaQlExecutor<T>` です。
リポジトリでこれを継承します。そして独自のクエリメソッドを、中で lambdaQL を使う **default メソッド** として書きます。
これらのメソッドのために `...RepositoryImpl` クラスを書く必要はありません。

| 部分 | 何をするか | 節 |
|---|---|---|
| `LambdaQlExecutor<T>` | ラムダの条件を受け取る `findAll`、`findSet`、`findFirst`、`findOne`、`count`、`exists` と `query()` を、すべてのリポジトリに追加します | [12.3](#123-repository-setup)、[12.4](#124-executor-methods) |
| Spring の `Sort` と `Pageable` | `findAll(where, sort)`、`findSet(where, sort)` と、Spring の `Page` を返す `findAll(where, pageable)` | [12.5](#125-spring-sort-and-pageable) |
| 更新、削除、挿入、upsert | `deleteAll(where)` や `updateOne(where, set)` のような短縮メソッドは、1 回の呼び出しで文を実行します。`updateQuery()`、`deleteQuery()`、`insertQuery()`、`upsertQuery()` が、リポジトリのエンティティに対して [7. 更新、挿入、削除](07-dml.md) の文を始めます。どれも呼び出し側のトランザクションが必要です | [12.4](#124-executor-methods)、[12.6](#126-transactions-and-exceptions) |
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

Spring Boot を使う場合、Gradle（Groovy DSL）:

```groovy
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation 'org.lambdaql:lambdaql-spring-boot-starter:1.0.0'
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

Spring Boot を使わない場合は、自分の Spring Data JPA の構成（Hibernate と JDBC ドライバー）に `lambdaql-spring-data-jpa` を追加します。これは `lambdaql-core` と Spring Data JPA を持ち込みます。

```groovy
dependencies {
    implementation 'org.lambdaql:lambdaql-spring-data-jpa:1.0.0'
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
| Java | 21 |
| Spring Boot | 3.4 以降（3.4.2 でテスト済み）。Spring Boot 4 はまだ検証していません |
| Spring Data JPA | 3.4 以降 |
| Hibernate | 6.6（Spring Boot 3.4 にすでに含まれています） |

Spring Data JPA 3.3 以前は **サポートしません**。lambdaQL は実行器を `META-INF/spring.factories` に登録しますが、Spring Data がこのファイルからリポジトリのフラグメントを読むのは 3.4 からです。

<a id="123-repository-setup"></a>
## 12.3 リポジトリの設定

リポジトリの `extends` リストに `LambdaQlExecutor<T>` を追加します。`T` はリポジトリのエンティティです。
設定も `Impl` クラスも書きません。

この章の例は、テスト用のエンティティ `Member`（`id`、`name`、`active`、`grade`、`team`）と `Team`（`id`、`name`）を使います。`grade` は `Integer` なので、NULL になることがあります。

**(テスト済み)** [`MemberRepository.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/sample/MemberRepository.java)、リージョン `repository`:

```java
/** No Impl class: Spring Data finds the LambdaQlExecutor implementation by itself. */
public interface MemberRepository extends JpaRepository<Member, Long>, LambdaQlExecutor<Member>,
        JpaSpecificationExecutor<Member>, MemberRepositoryCustom {

    /** @return the active members */
    default List<Member> activeMembers() {
        return findAll(m -> m.isActive());
    }

    /**
     * @param name a name
     * @return the member with that name, or empty
     */
    default Optional<Member> byName(String name) {
        return findOne(m -> name.equals(m.getName()));
    }

    /**
     * @param n how many
     * @return the active members with the highest grades
     */
    default List<Member> topGraded(int n) {
        return query()
                .where(m -> m.isActive())
                .orderBy((m, by) -> by.desc(m.getGrade()).asc(m.getId()))
                .limit(n)
                .toList();
    }

    /**
     * @param grade the new grade
     * @return how many inactive members changed (call inside a transaction)
     */
    default int gradeInactive(int grade) {
        return updateQuery().set(m -> m.setGrade(grade)).where(m -> !m.isActive()).execute();
    }

    /** @return how many members without a team were deleted (call inside a transaction) */
    default int deleteWithoutTeam() {
        return deleteQuery().where(m -> m.getTeam() == null).execute();
    }

    /**
     * @param name a name
     * @return 1 (call inside a transaction)
     */
    default int addGuest(String name) {
        return insertQuery().values(m -> { m.setName(name); m.setActive(false); }).execute();
    }
}
```

最後の 3 つのメソッドは、更新、削除、挿入の文を実行します。トランザクションが必要です（[12.6](#126-transactions-and-exceptions) を参照）。
このリポジトリは `JpaSpecificationExecutor<Member>` と、手で書いたフラグメント `MemberRepositoryCustom` も継承しています。lambdaQL にはどちらも必要ありません。すべてが一緒に動くことを確かめるために入れてあります（[12.7](#127-other-fragments-and-jpaspecificationexecutor) を参照）。

仕組み:

- **default メソッドは Spring Data 自身が実行します。** Spring Data は default メソッドからクエリを作ろうとしません。メソッドの本体を呼び出すだけです。lambdaQL はこの動きを変えません。
- **`LambdaQlExecutor<T>` は Spring Data のリポジトリのフラグメントです。** その実装は `lambdaql-spring-data-jpa` の `META-INF/spring.factories` に書かれています。Spring Data はこのファイルを読み、`LambdaQlExecutor<T>` を継承するすべてのリポジトリに実装を追加します。
- **実行器のオブジェクトは 1 つで、すべてのリポジトリが共有します。** それでも、各呼び出しは自分のリポジトリのエンティティを使います。たとえば `TeamRepository` の `teams.count(t -> ...)` は `Team` の行を数えます（[`AutoConfigurationTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/AutoConfigurationTest.java) でテスト済み）。

ルール:

| # | ルール |
|---|---|
| 1 | リポジトリは `LambdaQlExecutor<T>` を **直接** 継承する必要があります（そのリポジトリ自身の `extends` リストに書きます）。 |
| 2 | この目的で `LambdaQlExecutor<T>` を継承するインターフェースに `@NoRepositoryBean` を付けないでください。付けると、Spring Data は lambdaQL の実装を追加しません。 |
| 3 | lambdaQL のクエリは **default メソッド** かサービスの中に書いてください。抽象メソッドは、これまでどおり Spring Data のクエリメソッド（`findByName` など）です。 |
| 4 | Spring Boot がなくても、リポジトリの部分は動きます。普通の Spring Data だからです。`QueryBuilder` の Bean だけは自動では作られません（[12.8](#128-the-querybuilder-bean) を参照）。 |

<a id="124-executor-methods"></a>
## 12.4 実行器のメソッド

`Where<T>` は `where(...)` と同じ条件のラムダです（[3. 条件](03-where.md) を参照）。`SortSpec<T>` は lambdaQL のソートです（[4.10](04-order-paging.md#410-reusable-and-external-sorts) を参照）。`Sort`、`Pageable`、`Page` は Spring Data の型です。

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
| `count(Where<T>)` | `long` | 条件に合う行の数 |
| `exists(Where<T>)` | `boolean` | 条件に合う行があれば `true`。読むのは最大 1 行で、エンティティは読み込みません |
| `deleteAll(Where<T>)` | `int` | 条件に合うすべての行を、1 つの SQL 文で削除します（一括）。Spring Data の `deleteAll()` とは違います（[後述](#deleteallwhere-is-not-spring-datas-deleteall) を参照）。トランザクションが必要です |
| `deleteOne(Where<T>)` | `int` | 条件に合う唯一の行を、[1 行保護](#one-row-protection-deleteone-and-updateone) 付きで削除します。0 か 1 を返します。トランザクションが必要です |
| `updateAll(Where<T>, Assign<T>)` | `int` | 条件に合うすべての行を変更します。条件が先です。トランザクションが必要です |
| `updateOne(Where<T>, Assign<T>)` | `int` | 条件に合う唯一の行を、1 行保護付きで変更します。0 か 1 を返します。トランザクションが必要です |
| `insertOne(Assign<T>)` | `int` | 1 行を挿入します。1 を返します。トランザクションが必要です |
| `insertAll(Collection<L>, AssignEach<T, L>)` | `int` | 要素ごとに 1 行を挿入します（1 つの文の値は最大 500 個、ルール U13）。トランザクションが必要です |
| `upsertOne(Assign<T>)` | `int` | 1 行を挿入します。同じ `@Id` の行があれば、その行を変更します。データベースの行数を返します。トランザクションが必要です |
| `upsertAll(Collection<L>, AssignEach<T, L>)` | `int` | 要素ごとに 1 行を upsert します（1 行に 1 つの文）。トランザクションが必要です |
| `updateQuery()` | `UpdateQuery<T>` | 更新を始めます: `updateQuery().set(...).where(...).execute()`（[7.2](07-2-update.md) を参照）。トランザクションが必要です |
| `deleteQuery()` | `DeleteQuery<T>` | 削除を始めます: `deleteQuery().where(...).execute()`（[7.4](07-4-delete.md) を参照）。トランザクションが必要です |
| `insertQuery()` | `InsertQuery<T>` | 挿入を始めます: `insertQuery().values(...).execute()`（[7.5](07-5-insert.md) を参照）。トランザクションが必要です |
| `upsertQuery()` | `UpsertQuery<T>` | `@Id` をキーとする upsert を始めます（[7.6](07-6-upsert.md) を参照）。トランザクションが必要です |

短いメソッドは、`query()` に結果の関数を付けたものと同じです。`findAll(w)` は `query().where(w).toList()`、`findSet(w)` は `query().where(w).toSet()`、`findFirst(w)` は `query().where(w).first()`、`findOne(w)` は `query().where(w).one()` です（ほかも同様）。結果の関数は [結果を受け取る](02-getting-started.md#getting-the-result) で説明しています。
`findAll(Where<T>)` と `count(Where<T>)` は、`JpaRepository` の `findAll(Sort)`、`findAll(Example)`、`count()` とぶつかりません。引数の型が違うからです。

**`query()`: 完全なクエリ。** **(テスト済み)** [`MemberRepository.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/sample/MemberRepository.java)、リージョン `query`（12.3 のリポジトリの一部）:

```java
/**
 * @param n how many
 * @return the active members with the highest grades
 */
default List<Member> topGraded(int n) {
    return query()
            .where(m -> m.isActive())
            .orderBy((m, by) -> by.desc(m.getGrade()).asc(m.getId()))
            .limit(n)
            .toList();
}
```

次の例では、`repository` は `MemberRepository` です。

**(テスト済み)** [`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java)、リージョン `find-all-sort`:

```java
SortSpec<Member> byGrade = SortSpec.of(Member.class, (m, by) -> by.desc(m.getGrade()).asc(m.getId()));
List<Member> active = repository.findAll(m -> m.isActive(), byGrade);
```

**(テスト済み)** [`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java)、リージョン `find-first`（grade が最も高い、アクティブなメンバー）:

```java
Optional<Member> best = repository.findFirst(m -> m.isActive(),
        SortSpec.of(Member.class, (m, by) -> by.desc(m.getGrade())));
```

**(テスト済み)** [`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java)、リージョン `find-one`:

```java
Optional<Member> kim = repository.findOne(m -> m.getName().equals("Kim"));
// two or more rows: Spring's IncorrectResultSizeDataAccessException
assertThrows(IncorrectResultSizeDataAccessException.class, () -> repository.findOne(m -> m.isActive()));
```

**(テスト済み)** [`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java)、リージョン `count-exists`:

```java
long active = repository.count(m -> m.isActive());
boolean anyInTeamB = repository.exists(m -> m.getTeam().getName().equals("B"));
```

**(テスト済み)** [`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java)、リージョン `find-set`:

```java
java.util.SequencedSet<Member> active = repository.findSet(m -> m.isActive(), Sort.by(Sort.Order.desc("grade"), Sort.Order.asc("id")));
java.util.SequencedSet<Member> any = repository.findSet(m -> m.isActive());
```

`active` は `Sort` の順序を保ちます。`any` は `findAll(where)` と同じく、順序が決まっていません。

<a id="dml-shortcuts"></a>
### DML の短縮メソッド

8 つのメソッドが、更新、削除、挿入、upsert を 1 回の呼び出しで実行します。

| 短縮メソッド | 同じもの |
|---|---|
| `deleteAll(where)` | `deleteQuery().where(where).execute()` |
| `deleteOne(where)` | `deleteQuery().where(where).executeOne()` |
| `updateAll(where, set)` | `updateQuery().set(set).where(where).execute()` |
| `updateOne(where, set)` | `updateQuery().set(set).where(where).executeOne()` |
| `insertOne(values)` | `insertQuery().values(values).execute()` |
| `insertAll(rows, values)` | `insertQuery().values(rows, values).execute()` |
| `upsertOne(values)` | `upsertQuery().values(values).execute()` |
| `upsertAll(rows, values)` | `upsertQuery().values(rows, values).execute()` |

- **条件が先**、代入が後です: `updateAll(m -> !m.isActive(), m -> m.setGrade(7))`。
- **`All`** は、条件に合うすべての行（削除、更新）、またはリストのすべての要素（挿入、upsert）を意味します。
- **`One`** は、削除と更新では 1 行保護（次の項を参照）、挿入と upsert では 1 行を意味します。
- 呼び出し側のトランザクションが必要です（[12.6](#126-transactions-and-exceptions) を参照）。テストでは `TransactionTemplate` の中で実行しています。

**(テスト済み)** [`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java)、リージョン `dml-shortcuts`:

```java
int changed = repository.updateAll(m -> !m.isActive(), m -> m.setGrade(7));      // condition first
int deleted = repository.deleteAll(m -> m.getTeam() == null);                     // bulk: no cascade
int one = repository.insertOne(m -> { m.setName("Guest"); m.setActive(false); });
int many = repository.insertAll(List.of(new NewMember("A", 1), new NewMember("B", 2)),
        (m, r) -> { m.setName(r.name()); m.setGrade(r.grade()); m.setActive(true); });
```

`NewMember` はテストの record です: `record NewMember(String name, int grade)`。

<a id="one-row-protection-deleteone-and-updateone"></a>
### 1 行保護: `deleteOne` と `updateOne`

`deleteOne(where)` と `updateOne(where, set)` は、2 行以上を変更することはありません。

1. lambdaQL は一致する行の id を最大 2 つ読みます。
2. id がない: 何も変更しません。結果は 0 です。
3. id が 2 つ: 何も変更しません。メソッドは `IncorrectResultSizeDataAccessException` を投げます。
4. id が 1 つ: lambdaQL はその行を `id = :p and (あなたの条件)` で変更します。結果は 1 です。その間に行が条件に一致しなくなった場合は 0 です。

文そのものが 1 つの id を指定するので、その間に別のトランザクションが一致する行を追加しても、2 行目を変更することはありません。この保護はロールバックに頼りません。
コアの API にも同じ保護があります: `executeOne()`（[7.4](07-4-delete.md#delete-exactly-one-row-executeone) を参照）。

**(テスト済み)** [`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java)、リージョン `dml-one`:

```java
int renamed = repository.updateOne(m -> m.getId() == kimId, m -> m.setName("Kim2"));   // exactly one row
// two or more rows match: nothing changes
assertThrows(org.springframework.dao.IncorrectResultSizeDataAccessException.class,
        () -> repository.deleteOne(m -> m.isActive()));
```

<a id="deleteallwhere-is-not-spring-datas-deleteall"></a>
### `deleteAll(where)` は Spring Data の `deleteAll()` とは違う

| メソッド | 定義元 | 削除の方法 | cascade、`@PreRemove` |
|---|---|---|---|
| `deleteAll()` | `CrudRepository` | すべてのエンティティを読み込み、1 つずつ削除する | 実行される |
| `deleteAll(Iterable)` | `CrudRepository` | 渡されたエンティティを 1 つずつ削除する | 実行される |
| `deleteAll(Where<T>)` | `LambdaQlExecutor<T>` | 1 つの SQL `delete` 文（一括） | **実行されない** |

`deleteAll(where)` はエンティティを読み込みません。すべての一括文と同じように、すでに読み込まれたエンティティは変更しません（ルール U16）。
多くの行を速く削除するときに使ってください。cascade や `@PreRemove` が必要なときは Spring Data のメソッドを使ってください。

コンパイラーは引数でメソッドを選びます。引数なし、`Iterable`、ラムダのどれかです。これは javac と kotlinc 2.3.21 のバイトコードで確認しました。

<a id="builders-for-complex-statements"></a>
### 複雑な文のためのビルダー

`updateQuery()`、`deleteQuery()`、`insertQuery()`、`upsertQuery()` は完全な文を始めます。短縮メソッドでできないことに使ってください: 複数回の `set()`、`all()`、`orderBy(...).limit(n)`（[7.4](07-4-delete.md#delete-the-first-rows-limit) を参照）、`toHql()`、`onConflict(...)`。
名前が `Query` で終わるので、`delete(...)` のような `JpaRepository` のメソッドとぶつかりません。文は `QueryBuilder` のものと同じです（[7. 更新、挿入、削除](07-dml.md) を参照）。対象になる行は、同じ `where` ラムダの選択と同じです。
短縮メソッドと同じく、呼び出し側のトランザクションが必要です。すべての JPA の一括文と同じように、トランザクションですでに読み込まれたエンティティは変更しません。

**(テスト済み)** [`MemberRepository.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/sample/MemberRepository.java)、リージョン `dml`（12.3 のリポジトリの一部）:

```java
/**
 * @param grade the new grade
 * @return how many inactive members changed (call inside a transaction)
 */
default int gradeInactive(int grade) {
    return updateQuery().set(m -> m.setGrade(grade)).where(m -> !m.isActive()).execute();
}

/** @return how many members without a team were deleted (call inside a transaction) */
default int deleteWithoutTeam() {
    return deleteQuery().where(m -> m.getTeam() == null).execute();
}

/**
 * @param name a name
 * @return 1 (call inside a transaction)
 */
default int addGuest(String name) {
    return insertQuery().values(m -> { m.setName(name); m.setActive(false); }).execute();
}
```

upsert もトランザクションの中で実行します。`teams` は `TeamRepository`、`tx` は Spring の `TransactionTemplate` です。

**(テスト済み)** [`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java)、リージョン `upsert`:

```java
int changed = tx.execute(s -> teams.upsertQuery().values(t -> { t.setId(teamId); t.setName("A2"); }).execute());
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

**(テスト済み)** [`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java)、リージョン `find-all-spring-sort`:

```java
List<Member> rows = repository.findAll(m -> m.isActive(),
        Sort.by(Sort.Order.desc("grade"), Sort.Order.asc("team.name"), Sort.Order.asc("id")));
// a property that does not exist: the same error as Spring Data's own repositories
assertThrows(PropertyReferenceException.class,
        () -> repository.findAll(m -> m.isActive(), Sort.by("nickname")));
```

`findAll(where, pageable)` でのページング:

- ページ番号は Spring Data と同じく 0 から始まります。lambdaQL はページを `limit` と `offset` で読みます。
- 総数は `count` のクエリから得ます。ページだけで総数がわかるとき（たとえば、最初のページの行数がページサイズより少ないとき）、Spring はこのクエリを省きます。
- ソートキーに id が含まれていないとき、lambdaQL は id を最後のキーとして追加します。そのため、行がページの間で動きません（[タイブレーカー](04-order-paging.md#tie-breaker-automatic-id) を参照）。ソートのないページは id の順になります。
- `Pageable.unpaged()` は、すべての行を 1 つのページで返します。

**(テスト済み)** [`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java)、リージョン `page`:

```java
Page<Member> page = repository.findAll(m -> m.isActive(), PageRequest.of(1, 2, Sort.by("name")));
// page 1 (the second page) of the active members by name; the total comes from a count query
```

**リクエストパラメーターからのソート。** Spring MVC は、`?page=1&size=20&sort=name,desc` のようなリクエストパラメーターから `Pageable` を作れます。ソートポリシーも `@Sortable` フィールドもなければ、永続プロパティならどれでも受け付けるので、ユーザーはどのカラムでもソートできます。このとき lambdaQL は、エンティティと名前ごとに 1 回、`LQL-5003` を `INFO` レベルでログに出します。一部の名前だけを許可するには、次のソートポリシーを使ってください。

<a id="sort-policy-with-spring"></a>
### Spring でのソートポリシー

ソートポリシー（決定 D43）は、どの外部のソートの名前を許可するかを決めます。`SortRules`、`SortMode` とルールは [4.10](04-order-paging.md#sort-policy) で説明しています。
Spring の `Sort` や `Pageable` は常に外部のソートなので、ポリシーはその中の名前をすべてチェックします。ソートのラムダはチェックされません。

**ルールの出どころ。** Spring Boot では、自動構成が `EntityManagerFactory` のポリシーを作り、すべてのシングルトン Bean ができた後で登録します。読むのは次の 3 か所です。

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

`<Entity>` には JPA のエンティティ名を書きます。既定は単純クラス名で、`@Entity(name = ...)` で変えられます。エンティティ名は永続化ユニットの中で一意です（同じ名前が 2 つあると Hibernate が起動時に止まります）。そのため、パッケージが違う同じ名前のクラスが混ざることはありません。知らない名前なら、アプリケーションの起動が `IllegalStateException`（LQL-4042）で止まります。
パスは、ルールを最初に使うときにエンティティと照らし合わせられます（4.10 の [文字列によるルール](04-order-paging.md#rules-as-strings) を参照）。

**(テスト済み)** [`SortPropertiesTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/SortPropertiesTest.java)、リージョン `properties`（テストのプロパティ。アプリケーションでは `application.properties` や `application.yaml` に書きます）:

```java
@SpringBootTest(classes = SampleApplication.class, properties = {
        "lambdaql.sort.mode=STRICT",
        "lambdaql.sort.allow.Member=name,team.name",
        "lambdaql.sort.alias.Member.level=grade"})
```

**(テスト済み)** [`SortPropertiesTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/SortPropertiesTest.java)、リージョン `properties-use`:

```java
List<Member> byLevel = repository.findAll(m -> m.isActive(), Sort.by(Sort.Order.desc("level")));
List<Member> byTeam = repository.findAll(m -> m.isActive(), Sort.by("team.name", "name"));
assertThrows(SortNotAllowedException.class, () -> repository.findAll(m -> m.isActive(), Sort.by("grade")));
```

このテストは、自動構成がポリシーを登録することも確かめています。

**`SortPolicy` の Bean。** コンパイラーがチェックするルールを使うには、`SortPolicy` の Bean を定義してください。同じ自動構成の処理がそれを登録します。
この Bean は設計上の例です。下のテストは同じポリシーを手で登録しています。

```java
@Bean
public SortPolicy sortPolicy() {
    return SortPolicy.of(SortMode.STRICT,
            SortRules.of(Member.class)
                    .allow(m -> m.getName())
                    .alias("level", m -> m.getGrade()));
}
```

**(テスト済み)** [`ExecutorTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java)、リージョン `sort-policy`（リージョンは `try` の中で終わります。`finally` ブロックで `SortPolicy.unregister(emf)` を呼びます）:

```java
// usually a @Bean SortPolicy; the Boot auto-configuration registers it for the EntityManagerFactory
org.lambdaql.query.SortPolicy.register(emf, org.lambdaql.query.SortPolicy.of(org.lambdaql.query.SortMode.STRICT,
        org.lambdaql.query.SortRules.of(Member.class)
                .allow(m -> m.getName())
                .alias("level", m -> m.getGrade())));
try {
    // the client sends "level"; lambdaQL sorts by grade
    List<Member> rows = repository.findAll(m -> m.isActive(), Sort.by(Sort.Order.desc("level"), Sort.Order.asc("name")));
    // a property that is not in the rules
    assertThrows(org.lambdaql.SortNotAllowedException.class,
            () -> repository.findAll(m -> m.isActive(), Sort.by("grade")));
```

- `level` のようなエイリアスは `Member` のプロパティではありません。lambdaQL は Spring の `PropertyPath` のチェックをせずにそのまま渡し、ポリシーが `grade` に変えます。
- エイリアスでもプロパティでもない名前は、ポリシーに聞く前に `PropertyReferenceException` になります。
- `STRICT` モードでは、ルールにないプロパティは `SortNotAllowedException`（`LQL-4040`）になります。Spring はこれを変換しません（[12.6](#126-transactions-and-exceptions) を参照）。

**HTTP 400。** lambdaQL は Web への依存を持たないので、HTTP のステータスを決めません。Web アプリケーションは通常、`SortNotAllowedException` に 400 Bad Request で応答します。
次のハンドラーは例です（テストされていません）。

```java
@RestControllerAdvice
public class SortErrorHandler {

    @ExceptionHandler(SortNotAllowedException.class)
    public ProblemDetail sortNotAllowed(SortNotAllowedException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setProperty("code", e.code());      // "LQL-4040"
        return problem;
    }
}
```

メッセージにはエンティティクラスとソートの名前が入ります。クライアントにエンティティ名を見せたくない場合は、`e.code()` だけを送ってください。

<a id="126-transactions-and-exceptions"></a>
## 12.6 トランザクションと例外

実行器は Spring の共有 `EntityManager` を使います。これは現在のトランザクションに結び付いています。

**lambdaQL は、意図的にメソッドに `@Transactional` を付けていません**（決定 D41）。
更新、削除、挿入、upsert には呼び出し側のトランザクションが必要です。これは短縮メソッド（`deleteAll`、`updateOne` など）、ビルダー（`updateQuery()` など）、`QueryBuilder` の Bean のすべてに当てはまります。
サービスのメソッドに `@Transactional` を付けるか、`TransactionTemplate` を使ってください。トランザクションがないと、呼び出しは SQL を送る前に失敗します。

| ケース | どうなるか |
|---|---|
| `@Transactional` の中の選択 | lambdaQL は同じトランザクションで実行されます。まだ flush されていない変更も見えます。ロールバックの後は、それらの変更は lambdaQL からも見えなくなります（`ExecutorTest` でテスト済み） |
| トランザクションの外の選択 | Spring は呼び出しごとに新しい `EntityManager` を使い、呼び出しの後で閉じます。そのため、返されたエンティティは detached の状態です。これは Spring の通常の動きで、lambdaQL ではテストしていません |
| `@Transactional` や `TransactionTemplate` の中の更新、削除、挿入、upsert | そのトランザクションで実行され、一緒にロールバックされます（テスト済み） |
| トランザクションの外の更新、削除、挿入、upsert | SQL を送る前に失敗します。何も変更されません（テスト済み、次の表を参照） |

自分のトランザクションで DML を実行するのは `new QueryBuilder(entityManagerFactory)` だけです（[7.8](07-dml.md#transactions) を参照）。Spring のアプリケーションでは使わないでください（[12.8](#128-the-querybuilder-bean) を参照）。

エラーは、ほかのリポジトリのメソッドと同じく、Spring の例外変換を通ります。

| ケース | 例外 |
|---|---|
| `findOne` が 2 行以上に一致する | JPA の `NonUniqueResultException` ではなく、`IncorrectResultSizeDataAccessException`（Spring）（テスト済み） |
| `deleteOne` や `updateOne` が 2 行以上に一致する | `IncorrectResultSizeDataAccessException`。何も変更されません（テスト済み） |
| Spring の `Sort` に知らないプロパティがある | `PropertyReferenceException`（テスト済み） |
| Spring の `Sort` で `ignoreCase()` を使う | `UnsupportedOperationException`（テスト済み） |
| `STRICT` モードで、ソートポリシーが許可しないソートの名前（[12.5](#sort-policy-with-spring)） | `SortNotAllowedException`（`LQL-4040`）。変換されません（テスト済み） |
| リポジトリの呼び出し（短縮メソッド、または default メソッドの中の `execute()`）で、トランザクションなしに更新、削除、挿入、upsert を実行する | `InvalidDataAccessApiUsageException`（テスト済み）。JPA の `TransactionRequiredException` から Spring が作ります。リポジトリのメソッドの外（`QueryBuilder` の Bean）では JPA の `TransactionRequiredException` |
| ルールに違反する代入のラムダ（U1、U3、U4、U6） | `LambdaQLException`（例: `TranslationException`、`LQL-3042`）。変換されません（[7.8](07-dml.md#rules-checked-at-analysis-time) を参照） |

`QueryBuilder` の Bean（12.8）はリポジトリではないので、Spring はそのエラーを変換しません。そこでは、2 行以上に一致する `one()` と `executeOne()` は `NonUniqueResultException` を投げます。

<a id="127-other-fragments-and-jpaspecificationexecutor"></a>
## 12.7 ほかのフラグメントと `JpaSpecificationExecutor`

`LambdaQlExecutor<T>` は数あるフラグメントの 1 つです。12.3 のリポジトリは、次のものをすべて同時に使っています（`ExecutorTest` でテスト済み）。

| 一緒に使うもの | 動くか | 説明 |
|---|---|---|
| 手で書いたフラグメント（`MemberRepositoryCustom` と `MemberRepositoryImpl`） | はい | Spring Data はいつもどおり `Impl` クラスを見つけます |
| `JpaSpecificationExecutor<T>` | はい | lambdaQL のラムダの引数は 1 つ、`Specification` のラムダの引数は 3 つなので、Java は必ず正しい `findAll` を選びます |
| `JpaRepository` のメソッド（`findAll()`、`count()`、`save(...)`） | はい | 引数の型が違います |

次の呼び出しは、`ExecutorTest` のテストメソッド `worksWithOtherFragments` から取ったものです。

```java
repository.countByHand();                                                  // hand-written fragment
repository.findAll((root, query, cb) -> cb.isTrue(root.get("active")));    // Specification: three arguments
repository.findAll(m -> m.isActive());                                     // lambdaQL Where: one argument
repository.count();                                                        // Spring Data's own count()
```

<a id="128-the-querybuilder-bean"></a>
## 12.8 `QueryBuilder` の Bean

Spring Boot では、自動構成 `org.lambdaql.spring.boot.LambdaQlAutoConfiguration` が `QueryBuilder` の Bean を作ります。1 つのリポジトリに属さないクエリのために、これをサービスに注入してください。

- この Bean は、トランザクションに結び付いた Spring の共有 `EntityManager` を使います。そのクエリは、リポジトリのメソッドと同じく、現在のトランザクションに参加します。
- この Bean が作られるのは、`EntityManagerFactory` がちょうど 1 つで、自分で `QueryBuilder` の Bean を定義していないときだけです。
- リポジトリのメソッド（`LambdaQlExecutor<T>`）には、この Bean は必要ありません。
- この Bean の更新、削除、挿入、upsert の文には、リポジトリのメソッドと同じく、呼び出し側のトランザクションが必要です。トランザクションがないと、SQL を送る前に `TransactionRequiredException` を投げます。

**(テスト済み)** [`AutoConfigurationTest.java`](https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/AutoConfigurationTest.java)、リージョン `query-builder-bean`（`qb` は注入された Bean）:

```java
// inject it into a service: it joins the current transaction
TransactionTemplate tx = new TransactionTemplate(txManager);
long seen = tx.execute(status -> {
    members.save(new Member("Kim", true, 3, null));                       // not flushed yet
    return qb.from(Member.class).where(m -> m.getName().equals("Kim")).count();
});
```

Spring Boot を使わない場合や、`EntityManagerFactory` の Bean が 2 つ以上ある場合は、Bean を自分で作ってください。自動構成と同じように、共有 `EntityManager` を使います。

```java
@Bean
public QueryBuilder queryBuilder(EntityManagerFactory entityManagerFactory) {
    // the same as the auto-configuration: a shared, transaction-bound EntityManager
    return new QueryBuilder(SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory));
}
```

`SharedEntityManagerCreator` は Spring のクラスです（`org.springframework.orm.jpa`）。Spring のアプリケーションでは `new QueryBuilder(entityManagerFactory)` を使わないでください。そのビルダーはクエリごとに自分の `EntityManager` を開くので、Spring のトランザクションの外で実行されます。

**`@DataJpaTest` を使うテスト。** `@DataJpaTest` のスライステストは、一部の自動構成だけを読み込みます。そこでもリポジトリのメソッドは動きますが、`QueryBuilder` の Bean は作られません。テストで必要な場合は、テストクラスに `@ImportAutoConfiguration(LambdaQlAutoConfiguration.class)` を付けてください（まだテストしていません）。

<a id="129-kotlin-repositories"></a>
## 12.9 Kotlin のリポジトリ

Kotlin で書いたリポジトリには、いくつかのコンパイラー設定が必要です。Kotlin 2.2 以降はデフォルトの設定で動きます。それより古い Kotlin には `-Xjvm-default=all` が必要です。また、Spring Data には `kotlin-reflect` が必要です。
[このページの Kotlin 版](../kotlin/12-spring-data-jpa.md#129-kotlin-compile-settings) を参照してください。

<a id="1210-not-implemented-yet"></a>
## 12.10 まだ実装されていないもの

- **Spring Boot 4。** 自動構成は Spring Boot 4 のクラス名も書いていますが、テストしたのは Spring Boot 3.4.2 だけです。
- Spring の `Sort` の **`ignoreCase()`**。
- **Kotlin のリポジトリ** はまだテストしていません。

---

[Java トラック](README.md) | 前: [11. エラーと言語](../11-errors-and-languages.md) | [このページの Kotlin 版](../kotlin/12-spring-data-jpa.md)
