<a id="12-spring-data-jpa-java"></a>
# 12. Spring Data JPA（Java）

[Java トラック](README.md) | [マニュアルのホーム](../README.md) | 前: [11. エラーと言語](../11-errors-and-languages.md) | 次: [13. Spring Boot 3 と 4](../13-spring-boot-versions.md) | [このページの Kotlin 版](../kotlin/12-spring-data-jpa.md)

---

<a id="121-what-you-get"></a>
## 12.1 提供する機能

Spring Data JPA では、リポジトリのインターフェースを書きます。lambdaQL はここにインターフェース `LambdaQlExecutor<T>` を 1 つ加えます。

リポジトリがこのインターフェースを継承し、独自のクエリメソッドは lambdaQL を使う **default メソッド** として書きます。
これらのメソッドのための `...RepositoryImpl` クラスは書きません。

| 構成要素 | 機能 | 節 |
|---|---|---|
| `LambdaQlExecutor<T>` | すべてのリポジトリで、ラムダ式の条件で `findAll`、`findSet`、`findFirst`、`findOne`、`count`、`exists`、`query()` を実行 | [12.3](#123-repository-setup)、[12.4](#124-executor-methods) |
| Spring の `Sort` と `Pageable` | `findAll(where, sort)`、`findSet(where, sort)`、Spring の `Page` を返す `findAll(where, pageable)` | [12.5](#125-spring-sort-and-pageable) |
| 更新、削除、挿入、upsert | `deleteAll(where)`、`updateOne(where, set)` のような短縮メソッドが 1 回の呼び出しで文を実行。`updateQuery()`、`deleteQuery()`、`insertQuery()`、`upsertQuery()` が、リポジトリのエンティティに対して [7. 更新、挿入、削除](07-dml.md) の文を開始。どれも呼び出し側のトランザクションが必要 | [12.4](#124-executor-methods)、[12.6](#126-transactions-and-exceptions) |
| `QueryBuilder` の Bean | サービスで使う `QueryBuilder` の Bean を Spring Boot が作成 | [12.8](#128-the-querybuilder-bean) |

<a id="122-modules"></a>
## 12.2 モジュール

コアは Spring に依存しないので、素の JPA でも使えます。`lambdaql` と `lambdaql-spring-data-jpa` は Spring Boot 3・4 共通で、スターターだけが Spring Boot のバージョンごとに別にあります（名前の末尾 `-boot3`、`-boot4`、[13. Spring Boot 3 と 4](../13-spring-boot-versions.md)）。

| 成果物 | 内容 | 必要になるとき |
|---|---|---|
| `lambdaql` | エンジンと `QueryBuilder`。Spring への依存なし。Spring Boot 3・4 共通 | 常に。下の成果物が一緒に持ち込む |
| `lambdaql-spring-data-jpa` | `LambdaQlExecutor<T>`、Spring Data への登録、Spring Boot の自動構成（`QueryBuilder` の Bean）。Spring Data JPA 3.5・4.1 共通。Spring Data JPA はプロジェクトのものを使う | Spring Boot なしで Spring Data JPA を使うとき |
| `lambdaql-spring-boot-starter-boot4`、`lambdaql-spring-boot-starter-boot3` | コードなし。`lambdaql-spring-data-jpa` と、その Boot バージョンの `spring-boot-starter-data-jpa` を持ち込む | Spring Boot を使うとき（プロジェクトの Boot バージョンに合わせる） |

成果物は Maven Central にあります（groupId `io.github.lambdaql`、バージョン `0.0.2`）。

Spring Boot 4 で使う場合の Gradle（Groovy DSL）。Spring Boot 3 なら名前の末尾を `-boot3` に変えてください:

```groovy
repositories {
    mavenCentral()
}

dependencies {
    implementation 'io.github.lambdaql:lambdaql-spring-boot-starter-boot4:0.0.2'
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

Spring Boot なしで使う場合は、既存の Spring Data JPA の構成（Spring Data JPA、Hibernate、JDBC ドライバー）に `lambdaql-spring-data-jpa` を追加してください。この成果物は `lambdaql` を一緒に持ち込み、Spring Data JPA はプロジェクトにあるもの（3.5 または 4.1）を使います。

```groovy
dependencies {
    implementation 'io.github.lambdaql:lambdaql-spring-data-jpa:0.0.2'
}
```

```xml
<dependency>
  <groupId>io.github.lambdaql</groupId>
  <artifactId>lambdaql-spring-data-jpa</artifactId>
  <version>0.0.2</version>
</dependency>
```

必要な環境:

| 項目 | バージョン |
|---|---|
| Java | 21 以上 |
| Spring Boot | 3.5 または 4.1 |
| Spring Data JPA | 3.5 または 4.1 |
| Hibernate | 6.6 または 7.4（Spring Boot に含まれる） |

Spring Data JPA 3.3 以前は **サポートしません**。lambdaQL は実行器を `META-INF/spring.factories` に登録し、Spring Data がこのファイルからリポジトリのフラグメントを読むのは 3.4 からです。

<a id="123-repository-setup"></a>
## 12.3 リポジトリの設定

リポジトリの `extends` リストに `LambdaQlExecutor<T>` を追加します。`T` はリポジトリのエンティティです。
別の構成や `Impl` クラスは書きません。

この章の例は、例のエンティティ `Member`（`id`、`name`、`active`、`grade`、`team`）と `Team`（`id`、`name`）を使います。`grade` は `Integer` なので、NULL になることがあります。

<!-- region: ManualMemberRepository.java#repository -->
```java
/** No Impl class: Spring Data finds the LambdaQlExecutor implementation by itself. */
public interface ManualMemberRepository extends JpaRepository<Member, Long>, LambdaQlExecutor<Member>,
        JpaSpecificationExecutor<Member> {

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
                .orderBy(m -> desc(m.getGrade()).asc(m.getId()))
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

最後の 3 つのメソッドは、更新、削除、挿入の文を実行します。これらのメソッドにはトランザクションが必要です（[12.6](#126-transactions-and-exceptions) を参照）。

このリポジトリは `JpaSpecificationExecutor<Member>` も継承しています。lambdaQL には必要ありません。一緒に使うときの動作を示すための構成です。
別の例のリポジトリ [`MemberRepository`](https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/sample/MemberRepository.java) は、同じメソッドに加えて、手で書いたフラグメント `MemberRepositoryCustom` も継承しています（[12.7](#127-other-fragments-and-jpaspecificationexecutor) を参照）。

仕組み:

- **default メソッドは Spring Data が直接実行します。** Spring Data は default メソッドからクエリを作らず、メソッドの本体を呼び出します。lambdaQL はこの動作を変えません。
- **`LambdaQlExecutor<T>` は Spring Data のリポジトリのフラグメントです。** 実装クラスは `lambdaql-spring-data-jpa` の `META-INF/spring.factories` に登録されています。Spring Data はこのファイルを読み、`LambdaQlExecutor<T>` を継承するすべてのリポジトリに実装を追加します。
- **実行器のオブジェクト 1 つをすべてのリポジトリが共有します。** 呼び出しごとに、そのリポジトリのエンティティを使います。たとえば `TeamRepository` の `teams.count(t -> ...)` は `Team` の行数を返します。

ルール:

| # | ルール |
|---|---|
| 1 | リポジトリは `LambdaQlExecutor<T>` を **直接**（自身の `extends` リストで）継承しなければなりません。 |
| 2 | この目的で `LambdaQlExecutor<T>` を継承するインターフェースに `@NoRepositoryBean` を付けないでください。付けると、Spring Data は lambdaQL の実装を追加しません。 |
| 3 | lambdaQL のクエリは **default メソッド** かサービスに書いてください。抽象メソッドは、これまでどおり Spring Data のクエリメソッド（`findByName` など）として動作します。 |
| 4 | Spring Boot がなくても、リポジトリの部分は普通の Spring Data なので動作します。`QueryBuilder` の Bean だけは自動では作られません（[12.8](#128-the-querybuilder-bean) を参照）。 |

コード例: [`ManualMemberRepository.java`](https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ManualMemberRepository.java)（`repository`）。

<a id="124-executor-methods"></a>
## 12.4 実行器のメソッド

`Where<T>` は `where(...)` と同じ条件のラムダ式です（[3. 条件](03-where.md) を参照）。`SortSpec<T>` は lambdaQL のソートです（[4.10](04-order-paging.md#410-reusable-and-external-sorts) を参照）。
`Sort`、`Pageable`、`Page` は Spring Data の型です。

| メソッド | 戻り値 | 説明 |
|---|---|---|
| `query()` | `SelectQuery<T>` | リポジトリのエンティティに対するクエリを開始。短いメソッドで表せないクエリ（複数回の `where`、`limit`、`offset`、実行時の `Direction`）に使う |
| `from(Class<E>)` | `SelectQuery<E>` | 同じ永続化ユニットの別のエンティティに対するクエリを開始 |
| `findAll(Where<T>)` | `List<T>` | 条件に合うすべての行。順序は決まっていない |
| `findAll(Where<T>, SortSpec<T>)` | `List<T>` | 条件に合うすべての行をソートして返す |
| `findAll(Where<T>, Sort)` | `List<T>` | Spring の `Sort` でソート（[12.5](#125-spring-sort-and-pageable) を参照）。`Sort.unsorted()` はソートなし |
| `findAll(Where<T>, Pageable)` | `Page<T>` | 1 ページ分。count のクエリは必要なときだけ実行。`Pageable.unpaged()` はすべての行 |
| `findSet(Where<T>)` | `SequencedSet<T>` | 条件に合うすべての行を Set で返す。順序は決まっていない（`findAll(Where<T>)` と同じ）。`distinct` なし。Set のサイズが行数より小さければ `IllegalStateException`（[`toSet()`](02-getting-started.md#toset) を参照） |
| `findSet(Where<T>, SortSpec<T>)` | `SequencedSet<T>` | 条件に合うすべての行を、ソートの順序で Set として返す |
| `findSet(Where<T>, Sort)` | `SequencedSet<T>` | 上と同じ。Spring の `Sort` でソート（[12.5](#125-spring-sort-and-pageable) を参照） |
| `findFirst(Where<T>)` | `Optional<T>` | 条件に合う行のうちの 1 つ。ソートしない |
| `findFirst(Where<T>, SortSpec<T>)` | `Optional<T>` | 指定した順序での最初の行。1 行だけ読む |
| `findFirst(Where<T>, Sort)` | `Optional<T>` | Spring の `Sort` の順序での最初の行 |
| `findOne(Where<T>)` | `Optional<T>` | 条件に合う唯一の行。2 行以上なら `IncorrectResultSizeDataAccessException` |
| `count(Where<T>)` | `long` | 条件に合う行の数 |
| `exists(Where<T>)` | `boolean` | 条件に合う行があれば `true`。最大 1 行だけ読み、エンティティは読み込まない |
| `deleteAll(Where<T>)` | `int` | 条件に合うすべての行を 1 つの SQL 文で削除（一括）。Spring Data の `deleteAll()` とは違う（[後述](#deleteallwhere-is-not-spring-datas-deleteall) を参照）。トランザクションが必要 |
| `deleteOne(Where<T>)` | `int` | 条件に合う唯一の行を [1 行保護](#one-row-protection-deleteone-and-updateone) を適用して削除。0 か 1 を返す。トランザクションが必要 |
| `updateAll(Where<T>, Assign<T>)` | `int` | 条件に合うすべての行を更新。条件が先。トランザクションが必要 |
| `updateOne(Where<T>, Assign<T>)` | `int` | 条件に合う唯一の行を 1 行保護を適用して更新。0 か 1 を返す。トランザクションが必要 |
| `insertOne(Assign<T>)` | `int` | 1 行を挿入。1 を返す。トランザクションが必要 |
| `insertAll(Collection<L>, AssignEach<T, L>)` | `int` | 要素ごとに 1 行を挿入（1 つの文の値は 500 個以下）。トランザクションが必要 |
| `upsertOne(Assign<T>)` | `int` | 1 行を挿入。同じ `@Id` の行があればその行を更新。データベースが報告した行数を返す。トランザクションが必要 |
| `upsertAll(Collection<L>, AssignEach<T, L>)` | `int` | 要素ごとに 1 行を upsert（1 行に 1 つの文）。トランザクションが必要 |
| `updateQuery()` | `UpdateQuery<T>` | 更新を開始: `updateQuery().set(...).where(...).execute()`（[7.2](07-2-update.md) を参照）。トランザクションが必要 |
| `deleteQuery()` | `DeleteQuery<T>` | 削除を開始: `deleteQuery().where(...).execute()`（[7.4](07-4-delete.md) を参照）。トランザクションが必要 |
| `insertQuery()` | `InsertQuery<T>` | 挿入を開始: `insertQuery().values(...).execute()`（[7.5](07-5-insert.md) を参照）。トランザクションが必要 |
| `upsertQuery()` | `UpsertQuery<T>` | `@Id` をキーとする upsert を開始（[7.6](07-6-upsert.md) を参照）。トランザクションが必要 |

短いメソッドは、`query()` に結果の関数を付けた形と同じです。`findAll(w)` は `query().where(w).toList()`、`findSet(w)` は `query().where(w).toSet()`、`findFirst(w)` は `query().where(w).first()`、`findOne(w)` は `query().where(w).one()` に当たります。結果の関数は [結果を受け取る](02-getting-started.md#getting-the-result) で説明しています。
`findAll(Where<T>)` と `count(Where<T>)` は引数の型が違うので、`JpaRepository` の `findAll(Sort)`、`findAll(Example)`、`count()` と衝突しません。

**`query()`: 完全なクエリ。**
12.3 のリポジトリの一部:

<!-- region: MemberRepository.java#query -->
```java
/**
 * @param n how many
 * @return the active members with the highest grades
 */
default List<Member> topGraded(int n) {
    return query()
            .where(m -> m.isActive())
            .orderBy(m -> desc(m.getGrade()).asc(m.getId()))
            .limit(n)
            .toList();
}
```

次の例で、`repository` は `MemberRepository` です。

<!-- region: ExecutorTest.java#find-all-sort -->
```java
SortSpec<Member> byGrade = SortSpec.of(Member.class, m -> desc(m.getGrade()).asc(m.getId()));
List<Member> active = repository.findAll(m -> m.isActive(), byGrade);
```

grade が最も高いアクティブなメンバー:

<!-- region: ExecutorTest.java#find-first -->
```java
Optional<Member> best = repository.findFirst(m -> m.isActive(),
        SortSpec.of(Member.class, m -> desc(m.getGrade())));
```

<!-- region: ExecutorTest.java#find-one -->
```java
Optional<Member> kim = repository.findOne(m -> m.getName().equals("Kim"));
// two or more rows: Spring's IncorrectResultSizeDataAccessException
assertThrows(IncorrectResultSizeDataAccessException.class, () -> repository.findOne(m -> m.isActive()));
```

<!-- region: ExecutorTest.java#count-exists -->
```java
long active = repository.count(m -> m.isActive());
boolean anyInTeamB = repository.exists(m -> m.getTeam().getName().equals("B"));
```

<!-- region: ExecutorTest.java#find-set -->
```java
java.util.SequencedSet<Member> active = repository.findSet(m -> m.isActive(), Sort.by(Sort.Order.desc("grade"), Sort.Order.asc("id")));
java.util.SequencedSet<Member> any = repository.findSet(m -> m.isActive());
```

`active` は `Sort` の順序を保ちます。`any` は `findAll(where)` と同じく、順序が決まっていません。

<a id="dml-shortcuts"></a>
### DML の短縮メソッド

次の 8 つのメソッドは、更新、削除、挿入、upsert を 1 回の呼び出しで実行します。

| 短縮メソッド | 同じ文 |
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
- 呼び出し側のトランザクションが必要です（[12.6](#126-transactions-and-exceptions) を参照）。例では `TransactionTemplate` の中で呼び出します。

<!-- region: ExecutorTest.java#dml-shortcuts -->
```java
int changed = repository.updateAll(m -> !m.isActive(), m -> m.setGrade(7));      // condition first
int deleted = repository.deleteAll(m -> m.getTeam() == null);                     // bulk: no cascade
int one = repository.insertOne(m -> { m.setName("Guest"); m.setActive(false); });
int many = repository.insertAll(List.of(new NewMember("A", 1), new NewMember("B", 2)),
        (m, r) -> { m.setName(r.name()); m.setGrade(r.grade()); m.setActive(true); });
```

`NewMember` は例の record `record NewMember(String name, int grade)` です。

<a id="one-row-protection-deleteone-and-updateone"></a>
### 1 行保護: `deleteOne` と `updateOne`

`deleteOne(where)` と `updateOne(where, set)` は、2 行以上を変更しません。

1. lambdaQL は条件に合う行の id を最大 2 つ読みます。
2. id がなければ、何も変更せず 0 を返します。
3. id が 2 つなら、何も変更せず `IncorrectResultSizeDataAccessException` を投げます。
4. id が 1 つなら、`id = :p and (条件)` でその行だけを変更し、1 を返します。その間にその行が条件から外れた場合は 0 を返します。

文そのものに 1 つの id を指定するので、その間に別のトランザクションが条件に合う行を追加しても、2 行目は変更しません。この保護はロールバックに頼りません。

コアの API にも同じ保護があります: `executeOne()`（[7.4](07-4-delete.md#delete-exactly-one-row-executeone) を参照）。

<!-- region: ExecutorTest.java#dml-one -->
```java
int renamed = repository.updateOne(m -> m.getId() == kimId, m -> m.setName("Kim2"));   // exactly one row
// two or more rows match: nothing changes
assertThrows(org.springframework.dao.IncorrectResultSizeDataAccessException.class,
        () -> repository.deleteOne(m -> m.isActive()));
```

<a id="deleteallwhere-is-not-spring-datas-deleteall"></a>
### `deleteAll(where)` と Spring Data の `deleteAll()` の違い

| メソッド | 定義元 | 削除の方法 | cascade、`@PreRemove` |
|---|---|---|---|
| `deleteAll()` | `CrudRepository` | すべてのエンティティを読み込んでから 1 つずつ削除 | 実行する |
| `deleteAll(Iterable)` | `CrudRepository` | 渡したエンティティを 1 つずつ削除 | 実行する |
| `deleteAll(Where<T>)` | `LambdaQlExecutor<T>` | 1 つの SQL `delete` 文（一括） | **実行しない** |

`deleteAll(where)` はエンティティを読み込みません。ほかの一括文と同じように、すでに読み込まれたエンティティには反映されません。

多くの行を速く削除するときに使ってください。cascade や `@PreRemove` が必要なときは、Spring Data のメソッドを使ってください。

コンパイラーは引数（引数なし、`Iterable`、ラムダ式）によってメソッドを選びます。javac と kotlinc 2.3.21 のバイトコードの opcode で確認しました（実測）。

<a id="builders-for-complex-statements"></a>
### 複雑な文のためのビルダー

`updateQuery()`、`deleteQuery()`、`insertQuery()`、`upsertQuery()` は完全な文を開始します。短縮メソッドで表せない場合に使ってください: 複数回の `set()`、`all()`、`orderBy(...).limit(n)`（[7.4](07-4-delete.md#delete-the-first-rows-limit) を参照）、`toHql()`、`onConflict(...)`。

メソッド名が `Query` で終わるので、`delete(...)` のような `JpaRepository` のメソッドと衝突しません。文は `QueryBuilder` で作る文と同じです（[7. 更新、挿入、削除](07-dml.md) を参照）。対象の行は、同じ `where` のラムダ式で select した行と同じです。

短縮メソッドと同じく、呼び出し側のトランザクションが必要です。すべての JPA の一括文と同じように、トランザクションの中ですでに読み込まれたエンティティには反映されません。

12.3 のリポジトリの一部:

<!-- region: MemberRepository.java#dml -->
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

<!-- region: ExecutorTest.java#upsert -->
```java
int changed = tx.execute(s -> teams.upsertQuery().values(t -> { t.setId(teamId); t.setName("A2"); }).execute());
```

upsert はデータベースが報告した行数を返します。挿入なら 1、更新なら H2 と Oracle は 1、MariaDB は 2 です（[7.6](07-6-upsert.md#the-row-count) を参照）。

コード例: [`MemberRepository.java`](https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/sample/MemberRepository.java)（`query`、`dml`）、[`ExecutorTest.java`](https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java)（`find-all-sort`、`find-first`、`find-one`、`count-exists`、`find-set`、`dml-shortcuts`、`dml-one`、`upsert`）。

<a id="125-spring-sort-and-pageable"></a>
## 12.5 Spring の `Sort` と `Pageable`

`findAll(where, sort)`、`findSet(where, sort)`、`findFirst(where, sort)`、`findAll(where, pageable)` は Spring Data の型を受け取ります。
lambdaQL は、各 `Sort.Order` を `SortSpec.ofPath` で 1 つのソートキーに変換します（[4.10](04-order-paging.md#410-reusable-and-external-sorts) を参照）。

| Spring | lambdaQL での扱い |
|---|---|
| プロパティ名（`"grade"`、`"team.name"`） | Spring Data のリポジトリと同じく、Spring Data の `PropertyPath` で検証。存在しない名前なら `PropertyReferenceException`（パッケージ: Spring Data 3 は `org.springframework.data.mapping`、4 は `org.springframework.data.core`）。パスの末尾はエンティティではなく基本の値でなければならない。`team.name` のような to-one のパスは `left join` を使うので、チームのない行も結果に含まれる。続いてソートポリシーが名前をチェックする（[後述](#sort-policy-with-spring) を参照） |
| ソートポリシーのエイリアス（`"level"`） | `PropertyPath` のチェックなしでそのまま渡す。ソートポリシーがパスに変える |
| `NullHandling.NATIVE`（デフォルト）の `ASC` / `DESC` | lambdaQL の NULL のルールを適用。昇順は NULL が先、降順は NULL が後。どのデータベースでも同じ順序（[4.4](04-order-paging.md#44-null-ordering-is-the-same-on-every-database) を参照） |
| `NullHandling.NULLS_FIRST` / `NULLS_LAST` | 指定したとおりに使う（`ASC_NULLS_FIRST`、`DESC_NULLS_LAST` など） |
| `ignoreCase()` | サポートしない。`UnsupportedOperationException` が発生 |
| `Sort.unsorted()` | `order by` なし |

Spring Data JPA 自身のリポジトリは `NATIVE` の NULL の位置をデータベースに任せるので、データベースごとに順序が違うことがあります。lambdaQL は自身のルールを適用します。

<!-- region: ExecutorTest.java#find-all-spring-sort -->
```java
List<Member> rows = repository.findAll(m -> m.isActive(),
        Sort.by(Sort.Order.desc("grade"), Sort.Order.asc("team.name"), Sort.Order.asc("id")));
// a property that does not exist: the same error as Spring Data's own repositories
RuntimeException unknown = assertThrows(RuntimeException.class,
        () -> repository.findAll(m -> m.isActive(), Sort.by("nickname")));
assertEquals("PropertyReferenceException", unknown.getClass().getSimpleName());
```

`findAll(where, pageable)` のページング:

- ページ番号は Spring Data と同じく 0 から始まります。lambdaQL は `limit` と `offset` でページを読みます。
- 総数は `count` のクエリで求めます。ページだけで総数が確定する場合（たとえば最初のページの行数がページサイズより少ない場合）、Spring は count のクエリを省きます。
- ソートキーに id がなければ lambdaQL が id を最後のキーとして加えるので、行がページの間で移動しません（[タイブレーカー](04-order-paging.md#tie-breaker-automatic-id) を参照）。ソートのないページには `order by` が付かないので、ページを安定して分けるには `Sort` を指定してください。
- `Pageable.unpaged()` は、すべての行を 1 つのページで返します。

<!-- region: ExecutorTest.java#page -->
```java
Page<Member> page = repository.findAll(m -> m.isActive(), PageRequest.of(1, 2, Sort.by("name")));
// page 1 (the second page) of the active members by name; the total comes from a count query
```

**リクエストパラメーターで受け取るソート。** Spring MVC は、`?page=1&size=20&sort=name,desc` のようなリクエストパラメーターから `Pageable` を作れます。
ソートポリシーも `@Sortable` フィールドもなければ、永続プロパティはすべて受け付けるので、ユーザーはどのカラムでもソートできます。このとき lambdaQL は、エンティティと名前ごとに 1 回、`LQL-5003` を `INFO` レベルでログに出します。
一部の名前だけを許可するには、下のソートポリシーを使ってください。

<a id="sort-policy-with-spring"></a>
### Spring でのソートポリシー

ソートポリシーは、許可する外部のソートの名前を決めます。`SortRules`、`SortMode` とルールは [4.10](04-order-paging.md#sort-policy) で説明しています。
Spring の `Sort` や `Pageable` は常に外部のソートなので、ポリシーがその中の名前をすべてチェックします。ソートのラムダ式はチェックしません。

**ルールの出どころ。** Spring Boot では、自動構成が `EntityManagerFactory` のポリシーを作り、すべてのシングルトン Bean を作った後で登録します。
自動構成が読むのは次の 3 か所です。

| 出どころ | 提供するもの |
|---|---|
| `SortPolicy` の Bean | モードと、ラムダ式で書いた `SortRules` |
| プロパティ `lambdaql.sort.*` | モードと、文字列で書いたルール |
| `@Sortable` フィールド（[4.10](04-order-paging.md#rules-from-sortable)） | Bean にもプロパティにもルールがないエンティティのルール |

- 1 つのエンティティのルールは、表の順序に従って 1 か所からだけ取ります。2 か所のルールを合わせることはしません。
- `lambdaql.sort.mode` は Bean のモードより優先されます。どちらもなければ、モードは `INFO` です。
- 登録の条件は `EntityManagerFactory` がちょうど 1 つのときで、`QueryBuilder` の Bean と同じです（[12.8](#128-the-querybuilder-bean) を参照）。それ以外の場合は、`SortPolicy.register(emf, policy)` を直接呼んでください。
- Bean もプロパティもなければ何も登録しないので、デフォルトを使います: `INFO`、ルールは `@Sortable` からだけ取ります。

**プロパティ。**

| プロパティ | 意味 |
|---|---|
| `lambdaql.sort.mode` | `OFF`、`INFO`（デフォルト）、`WARN`、`STRICT` |
| `lambdaql.sort.allow.<Entity>` | 許可するパスをカンマで区切る。例: `name,team.name` |
| `lambdaql.sort.alias.<Entity>.<外部の名前>` | エイリアスのパス。例: `lambdaql.sort.alias.Member.level=grade` |

**SARGable モードのプロパティ。** `lambdaql.sargable.mode` は `OFF`、`WARN`（デフォルト）、`STRICT` のいずれかです。自動構成が `lambdaql.sort.mode` と同じ方法で登録します。`STRICT` なら、インデックスを使えない条件の解析が `NonSargableException`（`LQL-3066`）で失敗します（[8.9](08-procedures-functions.md#89-warning-functions-on-columns-and-indexes-sargable) を参照）。

`<Entity>` には JPA のエンティティ名を書きます。デフォルトは単純クラス名で、`@Entity(name = ...)` で変えられます。

エンティティ名は persistence unit の中で一意です（同じ名前が 2 つあると Hibernate が起動の段階で止まります）。そのため、パッケージが違う同じ名前のクラスも混ざりません。存在しない名前なら、アプリケーションの起動が `IllegalStateException`（LQL-4042）で止まります。
パスは、ルールを最初に使うときにエンティティと照合します（4.10 の [文字列によるルール](04-order-paging.md#rules-as-strings) を参照）。

アプリケーションでは `application.properties` や `application.yaml` に書きます:

<!-- region: SortPropertiesTest.java#properties -->
```java
@SpringBootTest(classes = SampleApplication.class, properties = {
        "lambdaql.sort.mode=STRICT",
        "lambdaql.sort.allow.Member=name,team.name",
        "lambdaql.sort.alias.Member.level=grade"})
```

<!-- region: SortPropertiesTest.java#properties-use -->
```java
List<Member> byLevel = repository.findAll(m -> m.isActive(), Sort.by(Sort.Order.desc("level")));
List<Member> byTeam = repository.findAll(m -> m.isActive(), Sort.by("team.name", "name"));
assertThrows(SortNotAllowedException.class, () -> repository.findAll(m -> m.isActive(), Sort.by("grade")));
```

自動構成がこのプロパティでソートポリシーを登録します。

**`SortPolicy` の Bean。** コンパイラーがチェックするルールを使うには、`SortPolicy` の Bean を定義してください。同じ自動構成の段階がこの Bean を登録します。
この Bean があると、`Sort.Order.desc("level")` は `grade` でソートされ、`Sort.by("grade")` は `SortNotAllowedException` で拒否されます。その次の例は、同じポリシーを直接登録します。

<!-- region: ManualJavaSpringTest.java#sort-policy-bean -->
```java
@Configuration
static class SortConfig {

    @Bean
    public SortPolicy sortPolicy() {
        return SortPolicy.of(SortMode.STRICT,
                SortRules.of(Member.class)
                        .allow(m -> m.getName())
                        .alias("level", m -> m.getGrade()));
    }
}
```

下のコードは `try` ブロックの中の部分です。`finally` ブロックで `SortPolicy.unregister(emf)` を呼び、ポリシーを解除します。

<!-- region: ExecutorTest.java#sort-policy -->
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

- `level` のようなエイリアスは `Member` のプロパティではありません。lambdaQL は Spring の `PropertyPath` のチェックなしでそのまま渡し、ポリシーが `grade` に変えます。
- エイリアスでもプロパティでもない名前は、ポリシーのチェックの前に `PropertyReferenceException` が発生します。
- `STRICT` モードでルールにないプロパティを指定すると、`SortNotAllowedException`（`LQL-4040`）が発生します。Spring はこの例外を変換しません（[12.6](#126-transactions-and-exceptions) を参照）。

**HTTP 400 の応答。** lambdaQL は Web への依存がないので、HTTP のステータスを決めません。Web アプリケーションは普通、`@ExceptionHandler(SortNotAllowedException.class)` で 400 Bad Request を返し、エラーコードは `e.code()`（`"LQL-4040"`）で読みます。

メッセージにはエンティティクラスとソートの名前が入っています。クライアントにエンティティ名を見せたくない場合は、`e.code()` だけを送ってください。

コード例: [`ExecutorTest.java`](https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java)（`find-all-spring-sort`、`page`、`sort-policy`）、[`SortPropertiesTest.java`](https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/SortPropertiesTest.java)（`properties`、`properties-use`）、[`ManualJavaSpringTest.java`](https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ManualJavaSpringTest.java)（`sort-policy-bean`）。

<a id="126-transactions-and-exceptions"></a>
## 12.6 トランザクションと例外

実行器は、現在のトランザクションに結び付く Spring の共有 `EntityManager` を使います。

**lambdaQL はメソッドに `@Transactional` を付けません。** 意図した設計です。
更新、削除、挿入、upsert には呼び出し側のトランザクションが必要です。短縮メソッド（`deleteAll`、`updateOne` など）、ビルダー（`updateQuery()` など）、`QueryBuilder` の Bean のどれも同じです。

サービスのメソッドに `@Transactional` を付けるか、`TransactionTemplate` を使ってください。トランザクションがなければ、SQL を送る前に呼び出しが失敗します。

| ケース | 動作 |
|---|---|
| `@Transactional` の中の select | lambdaQL は同じトランザクションで実行され、まだ flush されていない変更も参照します。ロールバックの後は、lambdaQL からもその変更は見えません |
| トランザクションの外の select | Spring が呼び出しごとに新しい `EntityManager` を使い、呼び出しが終わると閉じるので、返されたエンティティは detached の状態です（下の `detached` の例） |
| `@Transactional` または `TransactionTemplate` の中の更新、削除、挿入、upsert | そのトランザクションで実行され、トランザクションと一緒にロールバックされます |
| トランザクションの外の更新、削除、挿入、upsert | SQL を送る前に失敗します。何も変更しません（次の表を参照） |

<!-- region: ManualJavaSpringTest.java#detached -->
```java
// outside a transaction each call uses a new EntityManager: the returned entity is detached
Member kim = repository.findOne(m -> m.getName().equals("Kim")).orElseThrow();
boolean managed = new TransactionTemplate(txManager).execute(s -> entityManager.contains(kim));
assertFalse(managed);
```

DML を自分のトランザクションで実行するビルダーは、`new QueryBuilder(entityManagerFactory)` だけです（[7.8](07-dml.md#transactions) を参照）。Spring のアプリケーションではこのビルダーを使わないでください（[12.8](#128-the-querybuilder-bean) を参照）。

エラーは、ほかのリポジトリのメソッドと同じく、Spring の例外変換を通ります。

| ケース | 例外 |
|---|---|
| `findOne` の条件に合う行が 2 行以上 | JPA の `NonUniqueResultException` の代わりに Spring の `IncorrectResultSizeDataAccessException` |
| `deleteOne` または `updateOne` の条件に合う行が 2 行以上 | `IncorrectResultSizeDataAccessException`。何も変更しない |
| Spring の `Sort` に存在しないプロパティ | `PropertyReferenceException` |
| Spring の `Sort` の `ignoreCase()` | `UnsupportedOperationException` |
| `STRICT` モードでソートポリシーが許可しないソートの名前（[12.5](#sort-policy-with-spring)） | `SortNotAllowedException`（`LQL-4040`）、変換しない |
| リポジトリの呼び出し（短縮メソッド、または default メソッドの中の `execute()`）で、トランザクションなしに更新、削除、挿入、upsert を実行 | `InvalidDataAccessApiUsageException`。Spring が JPA の `TransactionRequiredException` を変換した例外です。リポジトリのメソッドの外（`QueryBuilder` の Bean）では JPA の `TransactionRequiredException` |
| ルールに違反した代入のラムダ式 | `LambdaQLException`（例: `TranslationException`、`LQL-3042`）。変換しない（[7.8](07-dml.md#rules-checked-at-analysis-time) を参照） |

`QueryBuilder` の Bean（12.8）はリポジトリではないので、Spring の例外変換を通りません。この Bean で `one()` と `executeOne()` の条件に合う行が 2 行以上あれば、`NonUniqueResultException` が発生します。

コード例: [`ManualJavaSpringTest.java`](https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ManualJavaSpringTest.java)（`detached`）。

<a id="127-other-fragments-and-jpaspecificationexecutor"></a>
## 12.7 ほかのフラグメントと `JpaSpecificationExecutor`

`LambdaQlExecutor<T>` は複数のフラグメントの 1 つです。12.3 のリポジトリは、次の構成をすべて一緒に使います。

| 一緒に使う対象 | 動作するか | 説明 |
|---|---|---|
| 手で書いたフラグメント（`MemberRepositoryCustom` と `MemberRepositoryImpl`） | 動作する | Spring Data がこれまでどおり `Impl` クラスを見つける |
| `JpaSpecificationExecutor<T>` | 動作する | lambdaQL のラムダ式は引数が 1 つ、`Specification` のラムダ式は引数が 3 つなので、Java は常に正しい `findAll` を選ぶ |
| `JpaRepository` のメソッド（`findAll()`、`count()`、`save(...)`） | 動作する | 引数の型が違う |

次の例の `repository` は `MemberRepository` です。

<!-- region: ManualJavaSpringTest.java#fragments -->
```java
long byHand = repository.countByHand();                                               // hand-written fragment
List<Member> bySpec = repository.findAll((root, query, cb) -> cb.isTrue(root.get("active")));   // Specification: three arguments
List<Member> byLambda = repository.findAll(m -> m.isActive());                        // lambdaQL Where: one argument
long total = repository.count();                                                      // Spring Data's own count()
```

コード例: [`ManualJavaSpringTest.java`](https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ManualJavaSpringTest.java)（`fragments`）。

<a id="128-the-querybuilder-bean"></a>
## 12.8 `QueryBuilder` の Bean

Spring Boot では、自動構成 `org.lambdaql.spring.boot.LambdaQlAutoConfiguration` が `QueryBuilder` の Bean を作ります。1 つのリポジトリに属さないクエリは、この Bean をサービスに注入して書きます。

- この Bean は、トランザクションに結び付く Spring の共有 `EntityManager` を使います。そのため、リポジトリのメソッドと同じく、クエリは現在のトランザクションに参加します。
- この Bean が作られるのは、`EntityManagerFactory` がちょうど 1 つで、ユーザーが `QueryBuilder` の Bean を別に定義していない場合だけです。
- リポジトリのメソッド（`LambdaQlExecutor<T>`）には、この Bean は必要ありません。
- この Bean で作った更新、削除、挿入、upsert の文にも、リポジトリのメソッドと同じく呼び出し側のトランザクションが必要です。トランザクションがなければ、SQL を送る前に `TransactionRequiredException` が発生します。

`qb` は注入された Bean:

<!-- region: AutoConfigurationTest.java#query-builder-bean -->
```java
// inject it into a service: it joins the current transaction
TransactionTemplate tx = new TransactionTemplate(txManager);
long seen = tx.execute(status -> {
    members.save(new Member("Kim", true, 3, null));                       // not flushed yet
    return qb.from(Member.class).where(m -> m.getName().equals("Kim")).count();
});
```

Spring Boot なしで使う場合や、`EntityManagerFactory` の Bean が 2 つ以上ある場合は、Bean を自分で作ってください。自動構成と同じく、共有 `EntityManager` を使います。

<!-- region: ManualJavaStartTest.java#config -->
```java
@Configuration
public static class LambdaQlConfig {

    @Bean
    public QueryBuilder queryBuilder(EntityManagerFactory entityManagerFactory) {
        return new QueryBuilder(SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory));
    }
}
```

`SharedEntityManagerCreator` は Spring のクラス（`org.springframework.orm.jpa`）です。
Spring のアプリケーションでは `new QueryBuilder(entityManagerFactory)` を使わないでください。このビルダーはクエリごとに自分の `EntityManager` を開き、この `EntityManager` は Spring のトランザクションに参加しません。

**`@DataJpaTest` のテスト。** `@DataJpaTest` のスライステストは、一部の自動構成だけを読み込みます。リポジトリのメソッドは動作しますが、`QueryBuilder` の Bean は作られません。
テストでこの Bean が必要なら、`@SpringBootTest` のように自動構成全体を読み込むテストを使ってください。

コード例: [`AutoConfigurationTest.java`](https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/AutoConfigurationTest.java)（`query-builder-bean`）、[`ManualJavaStartTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaStartTest.java)（`config`）。

<a id="129-kotlin-repositories"></a>
## 12.9 Kotlin のリポジトリ

Kotlin で書いたリポジトリには、コンパイラーの設定が必要です。Kotlin 2.2 以降はデフォルトの設定で動作し、2.2 より前は `-Xjvm-default=all` が必要です。Spring Data には `kotlin-reflect` も必要です。
詳しくは [このページの Kotlin 版](../kotlin/12-spring-data-jpa.md#129-kotlin-compile-settings) を参照してください。

<a id="1210-not-implemented-yet"></a>
## 12.10 サポートしないもの

- Spring の `Sort` の **`ignoreCase()`**。呼び出すと `UnsupportedOperationException` が発生します（[12.6](#126-transactions-and-exceptions) を参照）。

---

[Java トラック](README.md) | 前: [11. エラーと言語](../11-errors-and-languages.md) | 次: [13. Spring Boot 3 と 4](../13-spring-boot-versions.md) | [このページの Kotlin 版](../kotlin/12-spring-data-jpa.md)
