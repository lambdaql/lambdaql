<a id="13-spring-boot-3-and-4"></a>
# 13. Spring Boot 3 と 4

[マニュアルのホーム](README.md) | トラック: [Java](java/README.md) | [Kotlin](kotlin/README.md) | 前: 12. Spring Data JPA（[Java](java/12-spring-data-jpa.md) | [Kotlin](kotlin/12-spring-data-jpa.md)）

このページは両方の言語向けです。

lambdaQL は Spring Boot 3（Hibernate 6.6）と Spring Boot 4（Hibernate 7.4）を両方サポートします（決定 D55）。
コアの `lambdaql-core` と Spring Data JPA モジュールの `lambdaql-spring-data-jpa` は、両方のバージョンに共通の jar です。スターターだけが Spring Boot のバージョンごとに別にあり、名前の末尾が `-boot3` または `-boot4` です。
2 つの共通 jar は Spring Boot 4 でコンパイルし、同じコンパイル結果を Spring Boot 3 のランタイムでもテストします。

<a id="131-versions"></a>
## 13.1 バージョン

| 項目 | Spring Boot 3 | Spring Boot 4 |
|---|---|---|
| テストした Spring Boot | 3.5.16 | 4.1.2 |
| Hibernate ORM | 6.6（6.6.53 でテスト済み） | 7.4（7.4.13 でテスト済み） |
| Jakarta Persistence | 3.1 | 3.2 |
| Spring Data JPA | 3.5（3.5.13 でテスト済み） | 4.1（4.1.2 でテスト済み） |
| コア | `lambdaql-core` | `lambdaql-core`（同じ jar） |
| Spring Data JPA モジュール | `lambdaql-spring-data-jpa` | `lambdaql-spring-data-jpa`（同じ jar） |
| スターター | `lambdaql-spring-boot-starter-boot3` | `lambdaql-spring-boot-starter-boot4` |
| Java | 21 以上 | 21 以上 |

- Java 24 以上では、どちらのバージョンも JDK の `java.lang.classfile` でラムダを読みます（決定 D52）。Java 21〜23 では ASM を使います。
- Spring Data JPA 3.3 以前はサポートしません。lambdaQL は実行器を `META-INF/spring.factories` に登録しますが、Spring Data がこのファイルからリポジトリのフラグメントを読むのは 3.4 からです。
- Spring Boot 3.4.2（Hibernate 6.6.5）でも同じテストが通りました（2026-11-19 実測）。3.4 はサポートが終わったラインなので、正式なテスト対象は 3.5 です。

<a id="132-dependencies"></a>
## 13.2 依存関係の追加

成果物は **まだ Maven Central に公開していません**。先にソースからビルドし、ローカルの Maven リポジトリに公開してください。

Spring Boot 4、Gradle（Groovy DSL）:

```groovy
dependencies {
    implementation 'org.lambdaql:lambdaql-spring-boot-starter-boot4:1.0.0'
}
```

Spring Boot 3、Gradle（Kotlin DSL）:

```kotlin
dependencies {
    implementation("org.lambdaql:lambdaql-spring-boot-starter-boot3:1.0.0")
}
```

Maven:

```xml
<dependency>
  <groupId>org.lambdaql</groupId>
  <artifactId>lambdaql-spring-boot-starter-boot4</artifactId>
  <version>1.0.0</version>
</dependency>
```

- Spring Boot なしで Spring Data JPA だけを使う場合は、`lambdaql-spring-data-jpa` を追加してください。Spring Data JPA 3.5 と 4.1 のどちらでも動き、Spring Data JPA はプロジェクトにあるものを使います（この成果物はバージョンを変えません）。
- Spring なしで JPA だけを使う場合は、`lambdaql-core` 1 つで足ります。Hibernate 6.6 と 7.4 のどちらでも動きます。
- スターターはプロジェクトの Spring Boot のバージョンに合わせてください。スターターはそのバージョンの `spring-boot-starter-data-jpa` を持ち込むので、Spring Boot 4 のプロジェクトに `-boot3` のスターターを入れると、Spring Data と Hibernate のバージョンが混ざることがあります。

<a id="133-what-is-the-same"></a>
## 13.3 同じもの

- API、ラムダの書き方のルール、エラーコード、設定（`lambdaql.*` プロパティ）は同じです。
- lambdaQL が組み立てる **HQL は同じです**。両方のバージョンが同じ `lambdaql-core` を使うからです。
- クエリが返す **行は同じです**。同じテストを両方のバージョンで実行し、行を比べています（13.5）。

Hibernate が HQL を SQL に変える方法は、バージョンごとに少し違います。マニュアルのデータベースごとの SQL ブロックは Hibernate 7.4 のものです。違いは 13.4 にまとめました。

<a id="134-sql-differences"></a>
## 13.4 Hibernate 6.6 と 7.4 の SQL の違い

同じ HQL について、両方のバージョンが作った SQL を比べた結果です（2026-11-23 実測）。
比較の対象はマニュアルの例 129 個（実装済みのクエリ 88 個、予定のクエリの HQL 41 個）です。

| データベース | SQL が違う例 |
|---|---|
| H2 2.3 | 3 |
| PostgreSQL 17 | 3 |
| Oracle 23.26 | 16 |
| MariaDB 10.11 | 28 |
| MySQL 8.4 | 28 |

違いは 5 つです。

| 違い | データベース | Hibernate 6.6 | Hibernate 7.4 |
|---|---|---|---|
| boolean の定数 | MariaDB、MySQL、Oracle | `o1_0.active=1` | `o1_0.active=true` |
| 派生テーブルの列名 | 5 つの DB | `select t1_0.id from (select o2_0.id id ...) t1_0` | `select t1_0.c0 from (select o2_0.id c0 ...) t1_0` |
| UPDATE・DELETE のテーブルのエイリアス | MariaDB、MySQL | `update orders o1_0 ... o1_0.customer_id` | `update orders ... orders.customer_id` |
| UPDATE の Embedded の列 | Oracle | `set o1_0.city=?,o1_0.zip=?` | `set city=?,zip=?` |
| UPDATE で対象テーブルを読むサブクエリ | MySQL | Hibernate がそのまま書くので MySQL のエラー 1093 | Hibernate が派生テーブルを加える |

最後の違いは、lambdaQL が HQL の段階で処理します。UPDATE のサブクエリが対象テーブルを読み、外側の行を使わない場合は、派生テーブルとして読みます（ルール U22）。
そのため、どちらのバージョンでも同じ行を更新します。外側の行を読む相関サブクエリは、MySQL と Hibernate 6.6 の組み合わせでは今もエラー 1093 です。

正解ファイルの場所は次のとおりです。

- Hibernate 7.4: [`query/src/test/resources/expected-sql/`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/)
- Hibernate 6.6: [`query/src/test/resources/expected-sql/hibernate6/`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/hibernate6/)

<a id="135-what-is-tested"></a>
## 13.5 検証の範囲

| 項目 | 値 |
|---|---|
| Spring Boot | 3.5.16、4.1.2 |
| データベース | H2 2.3、MariaDB 10.11、MySQL 8.4、PostgreSQL 17、Oracle 23.26 |
| JDK | 21（ASM で読む、両方の Spring Boot バージョン）、25（`java.lang.classfile` で読む、jar の `META-INF/versions/24`、Spring Boot 4） |
| テスト | Spring Boot のバージョンごとに同じテスト 250 個（コア 232 個、Spring Data JPA モジュール 18 個） |

テストは 1 組で、1 回だけコンパイルします。各モジュールの `test` タスクが Spring Boot 4 で、`testBoot3` タスクが同じコンパイル結果を Spring Boot 3 のランタイムで実行します。`java.lang.classfile` のリーダー（JDK 25）は Spring Boot 4 のランタイムで実行します（リーダーは Hibernate と無関係）。
JDK 24 はこのリポジトリのテスト環境にないため、実行を確認していません。

---

[マニュアルのホーム](README.md) | 前: 12. Spring Data JPA（[Java](java/12-spring-data-jpa.md) | [Kotlin](kotlin/12-spring-data-jpa.md)）
