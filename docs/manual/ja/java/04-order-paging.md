<a id="4-sorting-and-paging-java"></a>
# 4. ソートとページング（Java）

[Java トラック](README.md) | [マニュアルのホーム](../README.md) | 前: [3. 条件（`where`）](03-where.md) | 次: [5. サブクエリ](05-subquery.md) | [このページの Kotlin 版](../kotlin/04-order-paging.md)

共通ページ: [1. 概要](../01-overview.md) · [11. エラーと言語](../11-errors-and-languages.md)

---

<a id="41-one-lambda-for-the-whole-sort"></a>
## 4.1 ソート全体を 1 つのラムダ式で書く

ソートは **1 つのラムダ式** で書きます。ラムダ式はエンティティを受け取り、クラス `By` のソート関数でキーと方向を記述します。
`desc(...)`、`asc(...)` をつなげてキーを追加します。

<!-- region: ManualJavaSortTest.java#sort-overview -->
```java
import static org.lambdaql.query.By.desc;

OrderedSelectQuery<Order> page = qb.from(Order.class)
        .where(o -> o.isActive())
        .orderBy(o -> desc(o.getPrice()).asc(o.getCustomer().getName()).asc(o.getId()))
        .limit(2).offset(2);
assertEquals("select e0 from Order e0 left join e0.customer j1 where e0.active = true"
        + " order by e0.price desc, j1.name asc nulls first, e0.id asc", page.toHql().hql());
List<Order> rows = page.toList();
```

- ソート関数は `By` の static メソッドです: `asc(key)`、`desc(key)`、`asc(key, Nulls.FIRST)`、`desc(key, Nulls.LAST)`、`sort(key, Direction)`。
- lambdaQL はソートのラムダ式を呼び出さず、バイトコードの opcode を解析します。ソート関数は順序を記述するだけです。ソートのラムダ式の外で呼び出すと `IllegalStateException`（`LQL-4046`）が発生します。

`assertEquals` の文字列が、lambdaQL が作る HQL です。
この章の例は、H2、MariaDB、Oracle で、同じソートを Java で行った結果と同じ順序で行を返します。
`price`（`double`）、`status`（`short`）、`id` は NULL になりえないので、HQL に `nulls` の部分はありません。NULL になりうるキー（`description`、`updateAt`、顧客の `name`）には `nulls first` または `nulls last` が付きます（[4.4](#44-null-ordering-is-the-same-on-every-database) を参照）。

<!-- region: SortQueryTest.java#sort-basic -->
```java
RenderedQuery q = qb.from(Order.class)
        .where(o -> o.isActive())
        .orderBy(o -> desc(o.getPrice()).asc(o.getId()))
        .toHql();
// price and id can never be NULL, so there is no "nulls" part
assertEquals("select e0 from Order e0 where e0.active = true order by e0.price desc, e0.id asc", q.hql());
```

**データベースごとの SQL**

<!-- sql: sort-basic -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.active=true
order by o1_0.price desc,o1_0.id
```

`toHql()` はクエリを実行せずに HQL を返します。`toList()` はクエリを実行します。

コード例: [`ManualJavaSortTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaSortTest.java)（`sort-overview`）、[`SortQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java)（`sort-basic`）。

<a id="42-the-short-forms-one-key"></a>
## 4.2 短い書き方（キーが 1 つ）

キーが 1 つだけなら、ソート関数を使わずにキーだけを返しても構いません。

| Java | HQL |
|---|---|
| `orderBy(o -> o.getPrice())` | `order by e0.price asc nulls first` |
| `orderBy(o -> o.getUpdateAt(), Direction.DESC)` | `order by e0.updateAt desc nulls last` |
| `orderBy(Order::getPrice)` | `order by e0.price asc nulls first` |
| `orderBy(Order::getPrice, Direction.DESC)` | `order by e0.price desc nulls last` |

`Direction` がない場合、短い書き方は昇順（`ASC`）でソートします。

`Direction` 引数はキー 1 つのときだけ使います。`orderBy(o -> desc(o.getPrice()), Direction.ASC)` のようにソート関数と `Direction` 引数を一緒に使うと、`LQL-3069` で拒否されます。方向を引数で受け取る必要があるなら、`orderBy(o -> sort(o.getPrice(), direction))` を使ってください。

メソッド参照は、キー 1 つを使う場所ならどこでも使えます。メソッド参照は `Order::getPrice` のようなエンティティの getter でなければなりません。static メソッド、引数のあるメソッド、オブジェクトに束縛された参照は拒否されます。

例のエンティティでは `price` は `double` なので、`nulls` の部分はありません:

<!-- region: SortQueryTest.java#sort-key -->
```java
assertEquals("select e0 from Order e0 order by e0.price asc",
        qb.from(Order.class).orderBy(o -> o.getPrice()).toHql().hql());
assertEquals("select e0 from Order e0 order by e0.price desc",
        qb.from(Order.class).orderBy(Order::getPrice, Direction.DESC).toHql().hql());
```

**データベースごとの SQL**

<!-- sql: sort-key -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
order by o1_0.price
```

**データベースごとの SQL**

<!-- sql: sort-key#2 -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
order by o1_0.price desc
```

コード例: [`SortQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java)（`sort-key`）。

<a id="43-direction"></a>
## 4.3 `Direction`

`Direction` には 6 つの値があります。

| 値 | 意味 | NULL の位置 |
|---|---|---|
| `ASC` | 昇順 | NULL が **先頭**（デフォルトのポリシー） |
| `DESC` | 降順 | NULL が **末尾**（デフォルトのポリシー） |
| `ASC_NULLS_FIRST` | 昇順 | NULL が先頭。`ASC` と同じ |
| `ASC_NULLS_LAST` | 昇順 | NULL が末尾 |
| `DESC_NULLS_FIRST` | 降順 | NULL が先頭 |
| `DESC_NULLS_LAST` | 降順 | NULL が末尾。`DESC` と同じ |

`Direction` には補助メソッドも 2 つあります。

- `Direction.parse(text)` は、リクエストパラメーターのようなテキストから方向を読み取ります。大文字と小文字は区別せず、`-`、`_`、空白は同じものとして扱います: `Direction.parse("desc-nulls-last")` は `DESC_NULLS_LAST` です。不明なテキストは `IllegalArgumentException` を投げます。
- `reversed()` は逆の方向を返します。NULL の位置も一緒に反転するので、順序全体が逆になります: `ASC.reversed()` は `DESC`、`ASC_NULLS_LAST.reversed()` は `DESC_NULLS_FIRST` です。

**NULL の位置を選ぶ** には、2 番目の引数に enum `Nulls` を使ってください: `asc(key, Nulls.LAST)`、`desc(key, Nulls.FIRST)`。
`Nulls.FIRST` は「NULL が先頭」、`Nulls.LAST` は「NULL が末尾」という意味です。
2 番目の引数がなければ、`asc(key)` と `desc(key)` はデフォルトの NULL のポリシーを使います（[4.4](#44-null-ordering-is-the-same-on-every-database) を参照）。

| 書いたもの | 同じ意味 |
|---|---|
| `asc(key)` | `ASC`（NULL が先頭、デフォルトのポリシー） |
| `desc(key)` | `DESC`（NULL が末尾、デフォルトのポリシー） |
| `asc(key, Nulls.FIRST)` | `ASC_NULLS_FIRST` |
| `asc(key, Nulls.LAST)` | `ASC_NULLS_LAST` |
| `desc(key, Nulls.FIRST)` | `DESC_NULLS_FIRST` |
| `desc(key, Nulls.LAST)` | `DESC_NULLS_LAST` |

`description` は NULL になりうる `String` で、`id` は NULL になりえません:

<!-- region: SortQueryTest.java#sort-nulls -->
```java
// default: asc puts NULL first, desc puts NULL last (the same on every database)
assertEquals("select e0 from Order e0 order by e0.description asc nulls first, e0.id asc",
        qb.from(Order.class).orderBy(o -> asc(o.getDescription()).asc(o.getId())).toHql().hql());
// the second argument chooses: true = NULL first, false = NULL last
assertEquals("select e0 from Order e0 order by e0.description asc nulls last, e0.id asc",
        qb.from(Order.class).orderBy(o -> asc(o.getDescription(), Nulls.LAST).asc(o.getId())).toHql().hql());
assertEquals("select e0 from Order e0 order by e0.description desc nulls first, e0.id asc",
        qb.from(Order.class).orderBy(o -> desc(o.getDescription(), Nulls.FIRST).asc(o.getId())).toHql().hql());
```

**データベースごとの SQL**

<!-- sql: sort-nulls -->
```sql
-- H2, MariaDB
select …
from orders o1_0
order by o1_0.description,o1_0.id

-- Oracle
select …
from orders o1_0
order by o1_0.description asc nulls first,o1_0.id
```

**データベースごとの SQL**

<!-- sql: sort-nulls#2 -->
```sql
-- H2
select …
from orders o1_0
order by o1_0.description asc nulls last,o1_0.id

-- MariaDB
select …
from orders o1_0
order by case when (o1_0.description) is null then 1 else 0 end,o1_0.description,o1_0.id

-- Oracle
select …
from orders o1_0
order by o1_0.description,o1_0.id
```

**データベースごとの SQL**

<!-- sql: sort-nulls#3 -->
```sql
-- H2
select …
from orders o1_0
order by o1_0.description desc nulls first,o1_0.id

-- MariaDB
select …
from orders o1_0
order by case when (o1_0.description) is null then 0 else 1 end,o1_0.description desc,o1_0.id

-- Oracle
select …
from orders o1_0
order by o1_0.description desc,o1_0.id
```

SQL で NULL のポリシーを確認できます。H2 と MariaDB は、自身のデフォルトと同じなら句を省きます。Oracle は自身のデフォルトが逆なので、`asc nulls first` / `desc nulls last` を書きます。MariaDB には `NULLS` 構文がないので、デフォルトでない位置は先に `case when … is null then … end` で並べます。

**方向が変数から来るとき** は、キャプチャした `Direction` と一緒に `sort(key, direction)` を使ってください。

<!-- region: ManualJavaSortTest.java#sort-direction -->
```java
// the second argument: false = NULL last
OrderedSelectQuery<Order> nullsLast = qb.from(Order.class)
        .orderBy(o -> desc(o.getDescription(), Nulls.LAST).asc(o.getId()));
assertEquals("select e0 from Order e0 order by e0.description desc nulls last, e0.id asc",
        nullsLast.toHql().hql());
// a direction from a variable: the ternary runs outside the lambda
Direction dir = newestFirst ? Direction.DESC : Direction.ASC;
OrderedSelectQuery<Order> newest = qb.from(Order.class)
        .orderBy(o -> sort(o.getUpdateAt(), dir).asc(o.getId()));   // dir is read on every call
assertEquals("select e0 from Order e0 order by e0.updateAt desc nulls last, e0.id asc", newest.toHql().hql());
```

ここでの三項演算子 `newestFirst ? ... : ...` は問題ありません。ラムダ式の **外で** 実行されるからです。

<!-- region: SortQueryTest.java#sort-dynamic -->
```java
Direction dir = Direction.parse(text);           // for example from a request parameter
OrderedSelectQuery<Order> q = qb.from(Order.class)
        .orderBy(o -> sort(o.getDescription(), dir).asc(o.getId()));
```

**データベースごとの SQL**

<!-- sql: sort-dynamic -->
```sql
-- H2, MariaDB
select …
from orders o1_0
order by o1_0.description desc,o1_0.id

-- Oracle
select …
from orders o1_0
order by o1_0.description desc nulls last,o1_0.id
```

**データベースごとの SQL**

<!-- sql: sort-dynamic#2 -->
```sql
-- H2
select …
from orders o1_0
order by o1_0.description asc nulls last,o1_0.id

-- MariaDB
select …
from orders o1_0
order by case when (o1_0.description) is null then 1 else 0 end,o1_0.description,o1_0.id

-- Oracle
select …
from orders o1_0
order by o1_0.description,o1_0.id
```

`text` が `"desc"` なら、HQL は `... order by e0.description desc nulls last, e0.id asc` です。
`text` が `"asc_nulls_last"` なら、`... order by e0.description asc nulls last, e0.id asc` です。
ラムダ式の解析は 1 回だけです。`dir` はクエリを実行するたびに読み取ります。方向が `null` なら、クエリの実行時に例外が発生します。

コード例: [`SortQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java)（`sort-nulls`、`sort-dynamic`）、[`ManualJavaSortTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaSortTest.java)（`sort-direction`）。

<a id="44-null-ordering-is-the-same-on-every-database"></a>
## 4.4 NULL の順序はどのデータベースでも同じ

NULL の位置はデータベースによって異なります。

| | H2 | MariaDB | Oracle |
|---|---|---|---|
| `ASC` のデフォルト | NULL が先頭 | NULL が先頭 | NULL が **末尾** |
| `DESC` のデフォルト | NULL が末尾 | NULL が末尾 | NULL が **先頭** |
| SQL の `NULLS FIRST/LAST` | 対応 | **非対応（構文エラー）** | 対応 |

lambdaQL は 1 つのポリシーでこれを解決します。**NULL は最も小さい値です。**

- `ASC` = `ASC_NULLS_FIRST`
- `DESC` = `DESC_NULLS_LAST`

これは Java の `Comparator.nullsFirst(Comparator.naturalOrder())` と同じです。
このポリシーにより、3 つのデータベースはすべて同じ順序を返します（数値と日付の場合）。

**lambdaQL の書き方。** NULL になりうるキーには、lambdaQL が常に HQL に NULL の位置を書きます。例: `e0.description asc nulls first`。
NULL になりえないキー（`double` のようなプリミティブのカラムや `@Id`）には `nulls` の部分がありません。
関連の先にあるカラムは、関連がなければ NULL になるので（顧客のない注文がありうるため）付きます。パスのすべての関連の段階が必須（`optional = false`）で、値が NULL になりえなければ付きません。

その後、Hibernate がデータベースごとの SQL を書きます。データベースのデフォルトと同じなら、この句を省きます。
MariaDB には `NULLS` 構文がないので、デフォルトでない位置は Hibernate が `CASE` 式で書きます。

生成される SQL:

| 書いたもの | H2 | MariaDB | Oracle |
|---|---|---|---|
| `asc(key)`、`desc(key)`（`ASC`、`DESC`） | 追加の句なし（すでにデフォルト） | 追加の句なし | `nulls first` / `nulls last` を追加 |
| DB のデフォルトと同じ NULL の位置（`asc(key, Nulls.X)`、`desc(key, Nulls.X)`、`*_NULLS_*`） | 追加の句なし | 追加の句なし | 追加の句なし |
| DB のデフォルトと **異なる** NULL の位置 | `nulls first/last` | Hibernate が `case when x is null ...` を書く | `nulls first/last` |
| NULL になりえないキー（方向は問わない） | 追加の句なし | 追加の句なし | 追加の句なし |

> [!TIP]
> MariaDB では、`asc(key, Nulls.LAST)`（`ASC_NULLS_LAST`）と `desc(key, Nulls.FIRST)`（`DESC_NULLS_FIRST`）は `CASE` 式になります。データベースはこのソートにインデックスを使えません。できるだけ `asc(key)` / `desc(key)` を使ってください。

<a id="45-what-a-sort-key-can-be"></a>
## 4.5 ソートキーにできるもの

| キー | 可否 | 注意 |
|---|---|---|
| カラム: `o.getPrice()` | 可 | |
| to-one のパス: `o.getCustomer().getName()` | 可 | `left join`（必須の関連は inner join）、下記を参照 |
| `@Lob` カラム | 可 | Oracle は拒否する。`DEBUG` レベルの `LQL-5010`、下記を参照 |
| `@Enumerated(EnumType.STRING)` の enum カラム | 可 | 名前の順にソートされる（宣言の順ではない）。`DEBUG` レベルの `LQL-5011`、下記を参照 |
| 算術: `o.getPrice() * 2`、`+`、`-` | 可 | 下の例を参照 |
| 拡大変換（`short` から `long` など） | 可 | 変換せずにカラムでソート。下の例を参照 |
| エンティティを使わない値: `asc(1)`、キャプチャした値 | **拒否** | [4.6](#46-rules-for-the-lambda-body) を参照 |
| カラムに対する関数: `lower(name)` | 非対応 | |

エンティティのクラスが `Comparable` を実装していても、lambdaQL はラムダ式を読み取るときに、それをキーとして使うことを拒否します。

算術のキーは式のままソートします。拡大変換（`(long) o.getStatus()`）は HQL から消え、カラムでソートします。

<!-- region: ManualJavaSortTest.java#sort-arith -->
```java
// arithmetic on columns
OrderedSelectQuery<Order> doubled = qb.from(Order.class)
        .orderBy(o -> desc(o.getPrice() * 2).asc(o.getId()));
assertEquals("select e0 from Order e0 order by (e0.price * 2.0) desc, e0.id asc", doubled.toHql().hql());
// a widening conversion (short to long)
OrderedSelectQuery<Order> widened = qb.from(Order.class)
        .orderBy(o -> asc((long) o.getStatus()).asc(o.getId()));
assertEquals("select e0 from Order e0 order by e0.status asc, e0.id asc", widened.toHql().hql());
```

**関連のパスには `left join` を使います。** HQL の単純なパス `order by o.customer.name` は、3 つのデータベースすべてで inner join になります。顧客のない注文が消えてしまいます。
lambdaQL は代わりに `left join` を追加します。顧客のない注文も結果に残ります。そのような注文の `j1.name` は NULL なので、`asc` では先頭に来ます。

<!-- region: SortQueryTest.java#sort-path -->
```java
RenderedQuery q = qb.from(Order.class)
        .orderBy(o -> asc(o.getCustomer().getName()).asc(o.getId()))
        .toHql();
// a path used only for sorting is a LEFT JOIN: the order without a customer stays
assertEquals("select e0 from Order e0 left join e0.customer j1 order by j1.name asc nulls first, e0.id asc", q.hql());
```

**データベースごとの SQL**

<!-- sql: sort-path -->
```sql
-- H2, MariaDB
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
order by c1_0.name,o1_0.id

-- Oracle
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
order by c1_0.name asc nulls first,o1_0.id
```

**必須の関連には inner join を使います。** `optional = false` でマッピングした関連（例: `@ManyToOne(optional = false)`）は対象が常に存在するので、inner join で行を失いません。
そのため、ソートや select だけで使ったパスでも、このような関連には `join` を使います。それ以外の関連は上のように `left join` を使います。
パスのすべての関連の段階が必須で、値が NULL になりえなければ、ソートに `nulls first/last` も付きません。

例のエンティティ `OrderLine` の `@ManyToOne(optional = false) Order order`:

<!-- region: MappingRulesTest.java#required-association -->
```java
// order is optional = false: a sort or select path through it cannot lose rows, so it is an inner join
OrderedSelectQuery<OrderLine> sorted = qb.from(OrderLine.class).orderBy(l -> asc(l.getOrder().getPrice()));
assertEquals("select e0 from OrderLine e0 join e0.order j1 order by j1.price asc", sorted.toHql().hql());
ValueQuery<Double> values = qb.from(OrderLine.class).select(l -> l.getOrder().getPrice());
assertEquals("select j1.price from OrderLine e0 join e0.order j1", values.toHql().hql());
// an association that can be missing keeps the left join (Order.customer)
assertEquals("select e0 from Order e0 left join e0.customer j1 order by j1.name asc nulls first",
        qb.from(Order.class).orderBy(o -> asc(o.getCustomer().getName())).toHql().hql());
```

**`@Lob` カラムのソート。** `@Lob` カラムを `orderBy`、`distinct`、`groupBy` に使うと、H2 と MariaDB は実行しますが、Oracle は拒否します（ORA-22848、ORA-22849、実測）。`=` の比較は 3 つのデータベースすべてで動作します。
lambdaQL はクエリを変えず、カラムと使用箇所ごとに 1 回、`LQL-5010` を `DEBUG` レベルで記録します。

`OrderLine.memo` は `@Lob`:

<!-- region: MappingRulesTest.java#lob -->
```java
// memo is @Lob: H2 and MariaDB sort and group it, Oracle rejects it (ORA-22848, ORA-22849).
// lambdaQL keeps the query and logs LQL-5010 at DEBUG
qb.from(OrderLine.class).orderBy(l -> asc(l.getMemo())).toHql();
qb.from(OrderLine.class).groupBy(l -> l.getMemo()).select(l -> l.getMemo()).toHql();
qb.from(OrderLine.class).select(l -> l.getMemo()).distinct().toHql();
```

**`EnumType.STRING` の enum のソート。** `@Enumerated(EnumType.STRING)` でマッピングした enum カラムは、データベースが定数の **名前** の文字列順でソートします。Java の enum の比較は宣言の順に従うので、結果の順序が異なることがあります。
lambdaQL はクエリを変えず、カラムごとに 1 回、`LQL-5011` を `DEBUG` レベルで記録します。`ORDINAL`（または `@Enumerated` なし）は宣言の順でソートするので記録しません。

<!-- region: MappingRulesTest.java#enum-string-sort -->
```java
// kind is @Enumerated(EnumType.STRING): the database sorts by name, Java by declaration order (LQL-5011, DEBUG)
assertEquals("select e0 from Order e0 order by e0.kind asc nulls first",
        qb.from(Order.class).orderBy(o -> asc(o.getKind())).toHql().hql());
```

エイリアスは `where` と一緒に使います。`where` が同じパスをすでに結合していれば、ソートはその結合を使います。
下の条件は顧客のある注文でのみ真になるので、この結合は inner join で、顧客のない注文はすでに除かれています（[3.10](03-where.md#310-association-paths) を参照）。

<!-- region: SortQueryTest.java#sort-path-where -->
```java
String name = "Kim";
RenderedQuery q = qb.from(Order.class)
        .where(o -> o.getCustomer().getName().equals(name))
        .orderBy(o -> desc(o.getCustomer().getName()).desc(o.getPrice()))
        .toHql();
// the condition already joined the customer, so the sort uses the same inner join
assertEquals("select e0 from Order e0 join e0.customer j1 where j1.name = :p1"
        + " order by j1.name desc nulls last, e0.price desc", q.hql());
```

**データベースごとの SQL**

<!-- sql: sort-path-where -->
```sql
-- H2, MariaDB
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name=?
order by c1_0.name desc,o1_0.price desc

-- Oracle
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name=?
order by c1_0.name desc nulls last,o1_0.price desc
```

コード例: [`ManualJavaSortTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaSortTest.java)（`sort-arith`）、[`SortQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java)（`sort-path`、`sort-path-where`）、[`MappingRulesTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/MappingRulesTest.java)（`required-association`、`lob`、`enum-string-sort`）。

<a id="46-rules-for-the-lambda-body"></a>
## 4.6 ラムダ式の本体のルール

本体は **キー 1 つか、`By` の関数の単純なチェーン** でなければなりません。

<!-- region: ManualJavaSortTest.java#sort-body -->
```java
// allowed: a simple chain of by calls
assertEquals("select e0 from Order e0 order by e0.price desc, e0.id asc",
        qb.from(Order.class).orderBy(o -> desc(o.getPrice()).asc(o.getId())).toHql().hql());
// rejected: sort functions chosen by a condition (a condition inside one key is allowed)
LambdaQLException choice = assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).orderBy(o -> flag ? desc(o.getPrice()) : asc(o.getId())));
assertEquals("LQL-2023", choice.code());
// rejected: a key without the entity
LambdaQLException constant = assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).orderBy(o -> asc(1)));
assertEquals("LQL-3031", constant.code());
```

lambdaQL は `orderBy` を呼び出したときにこれを確認し、`LambdaQLException` を投げます（例: 条件で選んだソート関数は `LQL-2023`、エンティティを使わないキーは `LQL-3031`）。
すべてのキーはエンティティを使わなければなりません。定数やキャプチャした値だけのキーは拒否されます。
キーの値の中の三項演算子（`asc(o.isActive() ? 0 : 1)`）は `case when` になります（[3.5.1](03-where.md#351-values-chosen-by-a-condition) を参照）。

実行時に方向を変える必要があるなら、変数で渡してください: `sort(key, dir)`。キー自体を変える必要があるなら、通常の Java コードの 2 つの分岐でクエリを別々に作ってください。

コード例: [`ManualJavaSortTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaSortTest.java)（`sort-body`）。

<a id="47-order-of-calls"></a>
## 4.7 呼び出しの順序

クエリは `from(...).where(...).orderBy(...).limit(...).offset(...)` の順に書きます。
`orderBy` なしで `limit` や `offset` だけを使うと、`order by` は付きません（[4.8](#48-paging) を参照）。

<a id="48-paging"></a>
## 4.8 ページング

<!-- region: ManualJavaSortTest.java#page -->
```java
OrderedSelectQuery<Order> page = qb.from(Order.class)
        .where(o -> o.getStatus() == 1)
        .orderBy(o -> desc(o.getPrice()))
        .limit(2)       // page size
        .offset(2);     // skip 2 rows (page 2)
assertEquals("select e0 from Order e0 where e0.status = 1 order by e0.price desc, e0.id asc",
        page.toHql().hql());
List<Order> rows = page.toList();
```

`limit` と `offset` は HQL に書かず、Hibernate に渡します（`setMaxResults` / `setFirstResult`）。ソートキーに id がないので、lambdaQL が `e0.id asc` を追加しました（[次の節](#tie-breaker-automatic-id) を参照）。

<!-- region: SortQueryTest.java#sort-page -->
```java
OrderedSelectQuery<Order> page = qb.from(Order.class)
        .orderBy(o -> asc(o.getStatus()))
        .limit(2)
        .offset(2);
// status has ties, so the id is added as the last key and rows do not move between pages
assertEquals("select e0 from Order e0 order by e0.status asc, e0.id asc", page.toHql().hql());
List<Order> rows = page.toList();
```

**データベースごとの SQL**

<!-- sql: sort-page -->
```sql
-- H2, Oracle
select …
from orders o1_0
order by o1_0.status,o1_0.id
offset ? rows
fetch first ? rows only

-- MariaDB
select …
from orders o1_0
order by o1_0.status,o1_0.id
limit ?,?
```

ページングの SQL はデータベースによって異なります。H2 と Oracle は `offset ? rows fetch first ? rows only`、MariaDB は `limit ?,?` を受け取ります。offset がなければ `fetch first ? rows only` または `limit ?` だけを書きます（下の `page-only` を参照）。

`limit` と `offset` は 0 以上の値を受け取ります。負の値を渡すと `IllegalArgumentException` が発生します。

lambdaQL は HQL に `LIMIT` を書きません。Hibernate がデータベースごとに正しい SQL を書きます。

| データベース | SQL |
|---|---|
| H2 | `offset ? rows fetch first ? rows only` |
| MariaDB | `limit ?, ?` |
| Oracle | `offset ? rows fetch first ? rows only` |

**`orderBy` なしのページング。** `orderBy` なしで `limit` と `offset` を呼び出すと、`order by` を付けません。どの行がどのページに来るかはデータベースが決めます。ページを安定して分けるには `orderBy` を使ってください。

<!-- region: SortQueryTest.java#page-only -->
```java
// paging without orderBy: no order by, so which rows come is up to the database; use orderBy for stable pages
RenderedQuery q = qb.from(Order.class).where(o -> o.isActive()).limit(2).toHql();
assertEquals("select e0 from Order e0 where e0.active = true", q.hql());
```

**データベースごとの SQL**

<!-- sql: page-only -->
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

**最初の行と全体の件数。**

- `orderBy(...).first()` は、その順序の最初の行を `Optional` で返します。1 行だけを読みます。`offset` は維持されます: `orderBy(...).offset(1).first()` は 2 番目の行を返します。
- `count()` は条件に合うすべての行を数えます。ページング画面では、where の段階のクエリを変数に入れておいてください。その変数で `count()` を呼び出して全体の件数を得て、続けて `orderBy(...).limit(...).toList()` を呼び出してページを得ます。

詳しくは [結果の取得](02-getting-started.md#getting-the-result) を参照してください。

<a id="tie-breaker-automatic-id"></a>
### 同順位の処理（自動の `id`）

上の HQL の最後には、lambdaQL が追加した `e0.id asc` があります。理由は次のとおりです。

- 複数の行が同じ `status` や `price` を持つことがあります。データベースはそのような行を **任意の順序で** 返せます。Oracle は実際にそうします（実測）。
- すると、2 ページ目と 3 ページ目に同じ行が 2 回出たり、行が抜けたりすることがあります。

ルール: **`orderBy` とページングを一緒に使い、ソートキーに id がなければ、lambdaQL はルートの `@Id asc` を最後のキーとして追加します。**

- 追加するのは 1 回だけです。キーにすでに id があれば（例: `desc(o.getId())`）追加しません。
- ソートキーに主エンティティの一意で NULL になりえないカラムがあれば追加しません。`nullable = false` の `@Column(unique = true)`、Hibernate の `@NaturalId`、`@Id` がこれに当たり、このキーだけですべての行の順序が決まるからです。
  NULL になりうる一意のカラムは当たりません。NULL は複数の行にありうるからです。
- `orderBy` がないか、`limit` と `offset` がなければ何も追加しません。
- 明示的な結合（`join`、`leftJoin`、`crossJoin`）があれば、結合したすべてのエンティティの id も結合の順に追加します（[6.8](06-select-group.md#68-explicit-joins) を参照）。

`OrderLine.code` は `@Column(unique = true, nullable = false)`:

<!-- region: MappingRulesTest.java#unique-key -->
```java
// code is @Column(unique = true, nullable = false): it orders every row, so paging adds no id tie-breaker
assertEquals("select e0 from OrderLine e0 order by e0.code asc",
        qb.from(OrderLine.class).orderBy(l -> asc(l.getCode())).limit(2).toHql().hql());
// a key that is not unique still gets the id tie-breaker
assertEquals("select e0 from OrderLine e0 join e0.order j1 order by j1.price asc, e0.id asc",
        qb.from(OrderLine.class).orderBy(l -> asc(l.getOrder().getPrice())).limit(2).toHql().hql());
```

コード例: [`ManualJavaSortTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaSortTest.java)（`page`）、[`SortQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java)（`sort-page`、`page-only`）、[`MappingRulesTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/MappingRulesTest.java)（`unique-key`）。

<a id="49-strings-and-special-values"></a>
## 4.9 文字列と特殊な値

- 文字列の順序はデータベースの照合順序に従います。MariaDB の `_ci` は大文字と小文字を区別しません。Oracle は `NLS_SORT` を使い、これは JVM のロケールに従います（例: `de_DE` はドイツ語の順序）。Oracle には `NLS_SORT=BINARY` をおすすめします。
- Oracle は `''` を NULL として保存するので、空文字列は NULL のようにソートされます。
- `NaN` のソートはデータベースによって異なります。

<a id="410-reusable-and-external-sorts"></a>
## 4.10 再利用するソートと外部からのソート

**`SortSpec`: 保存しておけるソート。** `SortSpec` は、変数やフィールドに保存して複数のクエリで使うソートです。
`SortSpec.of(Order.class, o -> desc(...))` で作ります。ラムダ式は `orderBy(o -> ...)` のラムダ式と同じなので、同じルールが適用されます（[4.6](#46-rules-for-the-lambda-body) を参照）。
その後、`orderBy(spec)` に渡します。

- `SortSpec` は変更できません。`then()` と `reversed()` は新しい spec を返します。そのため、1 つの spec を複数のクエリやスレッドで安全に共有できます。たとえば `static final` フィールドに置いても構いません。
- lambdaQL は spec が `orderBy` で使われたときにラムダ式を検査します。各ラムダ式の解析は 1 回だけです。
- キャプチャした値は、ラムダ式が作られた時点の値です。`static final` フィールドの spec はその値を保ち続けます。実行時に方向を変える必要があるなら、`orderBy(o -> sort(key, dir))` を使ってください（[4.3](#43-direction) を参照）。
- `orderBy(spec)` の後でもページングできます: `limit`、`offset`、`toList()`、`first()`。

<!-- region: SortQueryTest.java#sort-spec -->
```java
// keep it, for example as a static final field, and use it in many queries
SortSpec<Order> byDescription = SortSpec.of(Order.class, o -> desc(o.getDescription()).asc(o.getId()));
RenderedQuery q = qb.from(Order.class).where(o -> o.isActive()).orderBy(byDescription).toHql();
assertEquals("select e0 from Order e0 where e0.active = true order by e0.description desc nulls last, e0.id asc", q.hql());
```

**データベースごとの SQL**

<!-- sql: sort-spec -->
```sql
-- H2, MariaDB
select …
from orders o1_0
where o1_0.active=true
order by o1_0.description desc,o1_0.id

-- Oracle
select …
from orders o1_0
where o1_0.active=true
order by o1_0.description desc nulls last,o1_0.id
```

SQL は、同じソートを `orderBy(o -> ...)` で書いた場合と同じです。Oracle は `desc` のデフォルトが逆なので `nulls last` を付けます（[4.4](#44-null-ordering-is-the-same-on-every-database) を参照）。

**`then()`: 既存のキーの後にキーを追加。** `a.then(b)` は、まず `a` のキーでソートします。値が同じ行は、続けて `b` のキーでソートします。
`then()` は別の `SortSpec` かソートのラムダ式を受け取ります。

例のエンティティでは `status` と `price` は NULL になりえないので、`nulls` の部分はありません:

<!-- region: SortQueryTest.java#sort-spec-then -->
```java
SortSpec<Order> byStatus = SortSpec.of(Order.class, o -> asc(o.getStatus()));
SortSpec<Order> byPrice = SortSpec.of(Order.class, o -> desc(o.getPrice()));
assertEquals("select e0 from Order e0 order by e0.status asc, e0.price desc",
        qb.from(Order.class).orderBy(byStatus.then(byPrice)).toHql().hql());
// then() also takes a lambda
assertEquals("select e0 from Order e0 order by e0.status asc, e0.id desc",
        qb.from(Order.class).orderBy(byStatus.then(o -> desc(o.getId()))).toHql().hql());
```

**データベースごとの SQL**

<!-- sql: sort-spec-then -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
order by o1_0.status,o1_0.price desc
```

**`reversed()`: すべてのキーを逆に。** `spec.reversed()` はすべてのキーを反転します。NULL の位置も一緒に反転します: `asc`（NULL が先）は `desc`（NULL が後）になります。そのため、行の順序全体がちょうど逆になります。
これは `Direction.reversed()` と同じです（[4.3](#43-direction) を参照）。`spec.reversed().reversed()` は元の順序に戻ります。

<!-- region: SortQueryTest.java#sort-spec-reversed -->
```java
SortSpec<Order> spec = SortSpec.of(Order.class, o -> asc(o.getDescription()).asc(o.getId()));
// every key is reversed, the NULL position too: asc nulls first -> desc nulls last
assertEquals("select e0 from Order e0 order by e0.description desc nulls last, e0.id desc",
        qb.from(Order.class).orderBy(spec.reversed()).toHql().hql());
```

**データベースごとの SQL**

<!-- sql: sort-spec-reversed -->
```sql
-- H2, MariaDB
select …
from orders o1_0
order by o1_0.description desc,o1_0.id desc

-- Oracle
select …
from orders o1_0
order by o1_0.description desc nulls last,o1_0.id desc
```

H2 と MariaDB は `desc` ですでに NULL を後に置くので、追加の構文を書きません。Oracle は `nulls last` を付けます。

**`SortSpec.ofPath`: プロパティ名で作るソート。** ソートキーがコードの外から文字列で入ってくることがあります。Spring の `Sort` がその例で、Spring モジュールは Spring の `Sort` を `ofPath` に変換します（[12.5](12-spring-data-jpa.md#125-spring-sort-and-pageable) を参照）。
`SortSpec.ofPath(Order.class, "customer.name", Direction.DESC)` は、プロパティのパスと `Direction` から spec を作ります。

- パスは、プロパティ名を `.` でつないだ文字列です。各部分は永続プロパティでなければなりません。途中には to-one の関連か埋め込み値、最後には基本の値が来ます。
- それ以外のパスはエラーです（spec を `orderBy` に渡したときに `IllegalArgumentException`）。そのため、文字列が別の HQL に変換されることはありません。
- 受け付ける名前は、persistence unit のソートポリシーが決めます（下の [ソートポリシー](#sort-policy) を参照）。
  ポリシーも `@Sortable` フィールドもなければ、永続プロパティは **どれでも** 受け付け、lambdaQL が `LQL-5003` を記録します（デフォルトのレベルは `INFO`）。
- 関連のパスは、ソートのラムダ式と同じく `left join`（必須の関連は inner join）を使います。`then()` で `ofPath` の spec とラムダ式の spec をつなげられます。

<!-- region: SortQueryTest.java#sort-spec-path -->
```java
// for sorts chosen outside the code (Spring Sort uses this); only real properties are accepted
SortSpec<Order> byName = SortSpec.ofPath(Order.class, "customer.name", Direction.DESC)
        .then(SortSpec.ofPath(Order.class, "id", Direction.ASC));
assertEquals("select e0 from Order e0 left join e0.customer j1 order by j1.name desc nulls last, e0.id asc",
        qb.from(Order.class).orderBy(byName).toHql().hql());
assertThrows(IllegalArgumentException.class,
        () -> qb.from(Order.class).orderBy(SortSpec.ofPath(Order.class, "price; drop table x", Direction.ASC)));
```

<a id="sort-policy"></a>
### ソートポリシー: 受け付ける外部の名前

**実装済み**。

コードの外から来たソートの名前（リクエストパラメーター、Spring の `Sort` や `Pageable`）は、どの永続プロパティでも指せます。
**ソートポリシー** は、受け付ける名前を決めます。

- `SortRules<T>` は、1 つのエンティティで受け付ける名前のリストです。各カラムをラムダ式で指定します。
- `SortPolicy` は、エンティティごとのルールとモード（`SortMode`）を持ちます。ポリシーは `EntityManagerFactory`、つまり persistence unit ごとに 1 つ登録します。そのファクトリーを使うすべての `QueryBuilder` とリポジトリがこのポリシーを使います。
- エンティティのフィールドの `@Sortable` で、コードなしでルールを指定できます（下の [`@Sortable` のルール](#rules-from-sortable) を参照）。

エンティティのルールは、次の順序で **1 か所** からだけ取得します: ポリシーの `SortRules`（コードまたは Spring の `SortPolicy` Bean）、Spring Boot のプロパティ `lambdaql.sort.*`（[12.5](12-spring-data-jpa.md#sort-policy-with-spring) を参照）、エンティティの `@Sortable` フィールド。
2 か所のルールを合わせることはありません。

**検査の対象。** 文字列から作ったソートだけを検査します: `SortSpec.ofPath`、そして Spring の `Sort` や `Pageable`（Spring モジュールは `ofPath` を使う、[12.5](12-spring-data-jpa.md#sort-policy-with-spring) を参照）。
ソートのラムダ式（`orderBy(o -> ...)`、`SortSpec.of`）は開発者のコードなので検査しません。

| ルール | 受け付ける外部の名前 |
|---|---|
| `allow(o -> o.getPrice())` | `price`（カラムのパス） |
| `allow(o -> o.getCustomer().getName())` | `customer.name`。関連を経由するパスは、このように明示した場合にだけ受け付けます |
| `alias("newest", o -> o.getUpdateAt())` | `newest`。ソートの基準は `updateAt` です。このとき、パス `updateAt` 自体は外部の名前では **ない** ので、クライアントにプロパティ名が見えません |

ルールはエンティティのカラムでなければなりません。
それ以外のキーは、ルールを最初に使うときに `LQL-4041` で拒否します。

<!-- region: ManualJavaSortTest.java#rule-not-column -->
```java
// a rule that is not a column of the entity is rejected when the rules are first used
SortPolicy.register(emf, SortPolicy.of(SortMode.STRICT,
        SortRules.of(Order.class).allow(o -> o.getPrice() * 2)));
LambdaQLException e = assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).orderBy(SortSpec.ofPath(Order.class, "price", Direction.ASC)).toHql());
assertEquals("LQL-4041", e.code());
```

| `SortMode` | ルールが受け付けない名前の扱い |
|---|---|
| `OFF` | ソートを実行します。検査しません |
| `INFO` | ソートを実行し、エンティティと名前ごとに 1 回、`LQL-5003` を `INFO` レベルで記録します。**デフォルトです:** 登録したポリシーがなければ `INFO` を使い、ルールは `@Sortable` からだけ取得します |
| `WARN` | `INFO` と同じで、ログのレベルだけが `WARN` です |
| `STRICT` | `SortNotAllowedException`（`LQL-4040`）で拒否します。ルールも `@Sortable` フィールドもないエンティティは、すべての名前を拒否します |

そのため、ポリシーを登録する前は、`@Sortable` が受け付けない外部のソートの名前ごとに、エンティティ単位で `LQL-5003` が 1 回記録されます。
不明な名前を拒否するには `STRICT` で、検査を止めるには `OFF` でポリシーを登録してください。

| メソッド | 機能 |
|---|---|
| `SortPolicy.of(mode, rules...)` | モードとエンティティごとのルールからポリシーを作成 |
| `SortPolicy.register(emf, policy)` | persistence unit のポリシーを登録。以前のポリシーを置き換える |
| `SortPolicy.unregister(emf)` | ポリシーを削除。デフォルト（`INFO`、`@Sortable` のルールだけを使用）に戻る |
| `SortPolicy.find(emf)` | 登録したポリシーを返す（`Optional`） |
| `SortPolicy.effective(emf)` | 適用中のポリシーを返す。登録したポリシーがなければデフォルト |

Spring Boot では、`register` を呼び出す代わりに `SortPolicy` Bean を定義してください（[12.5](12-spring-data-jpa.md#sort-policy-with-spring) を参照）。

<!-- region: SortPolicyTest.java#rules -->
```java
SortRules<Order> orderRules = SortRules.of(Order.class)
        .allow(o -> o.getPrice())                        // the name "price"
        .allow(o -> o.getCustomer().getName())           // an association path: allowed only when listed
        .alias("newest", o -> o.getUpdateAt());          // the outside name "newest" sorts by updateAt
```

ルールをポリシーに入れて persistence unit に登録します。

<!-- region: SortPolicyTest.java#strict -->
```java
SortPolicy.register(emf, SortPolicy.of(SortMode.STRICT, orderRules));
```

次の例の `hql(name, direction)` は、例の補助メソッドです。
`qb.from(Order.class).orderBy(SortSpec.ofPath(Order.class, name, direction)).toHql().hql()` を返します。

<!-- region: SortPolicyTest.java#allowed -->
```java
assertEquals("select e0 from Order e0 order by e0.price asc", hql("price", Direction.ASC));
assertEquals("select e0 from Order e0 left join e0.customer j1 order by j1.name asc nulls first", hql("customer.name", Direction.ASC));
// the alias: the client sends "newest", lambdaQL sorts by updateAt
assertEquals("select e0 from Order e0 order by e0.updateAt desc nulls last", hql("newest", Direction.DESC));
```

`STRICT` モード:

<!-- region: SortPolicyTest.java#rejected -->
```java
// not in the rules, an unlisted association path, the path behind an alias, an entity without rules
for (String name : new String[]{"description", "customer.grade", "updateAt"}) {
    assertEquals("LQL-4040", assertThrows(SortNotAllowedException.class, () -> hql(name, Direction.ASC)).code());
}
assertThrows(SortNotAllowedException.class,
        () -> qb.from(Item.class).orderBy(SortSpec.ofPath(Item.class, "qty", Direction.ASC)).toHql());
// a sort written as a lambda is code, not outside input: never checked
assertEquals("select e0 from Order e0 order by e0.description asc nulls first",
        qb.from(Order.class).orderBy(o -> asc(o.getDescription())).toHql().hql());
```

<!-- region: SortPolicyTest.java#warn -->
```java
// INFO (the default) and WARN: the sort runs and LQL-5003 is logged once per entity and name
SortPolicy.register(emf, SortPolicy.of(SortMode.WARN));
assertEquals("select e0 from Order e0 order by e0.description asc nulls first", hql("description", Direction.ASC));
```

<a id="rules-from-sortable"></a>
**`@Sortable` のルール。** エンティティのフィールドや getter に `@org.lambdaql.Sortable` を付けても、ルールになります。

| アノテーション | 受け付ける外部の名前 |
|---|---|
| フィールド `name` の `@Sortable` | `name` |
| フィールド `grade` の `@Sortable("level")` | `level`。ソートの基準は `grade`（エイリアス） |
| 関連フィールド `customer` の `@Sortable(path = "name")` | `customer.name` |

- lambdaQL は、`from(...)` に指定したエンティティのアノテーションだけを読みます。別のエンティティのアノテーションでは関連のパスは受け付けられないので、関連フィールドに `path` を指定してください。
- アノテーションは、ポリシーにそのエンティティのルール（コード、Spring Bean、Spring Boot のプロパティ）がないときにだけ使います。
- ポリシーを登録していないデフォルトの `INFO` モードでも動作します。

上の `STRICT` ポリシーは `Order` のルールだけを持つ:

<!-- region: SortPolicyTest.java#annotation -->
```java
// Customer has no rules in the policy, so its @Sortable fields are the rules:
//   @Sortable private String name;   @Sortable("level") private Integer grade;
assertEquals("select e0 from Customer e0 order by e0.name asc nulls first",
        qb.from(Customer.class).orderBy(SortSpec.ofPath(Customer.class, "name", Direction.ASC)).toHql().hql());
assertEquals("select e0 from Customer e0 order by e0.grade desc nulls last",
        qb.from(Customer.class).orderBy(SortSpec.ofPath(Customer.class, "level", Direction.DESC)).toHql().hql());
assertThrows(SortNotAllowedException.class,
        () -> qb.from(Customer.class).orderBy(SortSpec.ofPath(Customer.class, "id", Direction.ASC)).toHql());
```

<a id="rules-as-strings"></a>
**文字列のルール。** `allowPath(String...)` と `aliasPath(name, path)` は、プロパティのパスを文字列で受け取ります。設定ファイル用で、[12.5](12-spring-data-jpa.md#sort-policy-with-spring) の Spring Boot のプロパティがこの形を使います。
パスは、ルールを最初に使うときにエンティティと照合します。コードでは、ラムダ式の形の `allow` と `alias` を使ってください。

<!-- region: SortPolicyTest.java#string-paths -->
```java
// the string forms used by configuration files (lambdaql.sort.allow / lambdaql.sort.alias)
SortPolicy.register(emf, SortPolicy.of(SortMode.STRICT,
        SortRules.of(Order.class).allowPath("price", "customer.name").aliasPath("newest", "updateAt")));
assertEquals("select e0 from Order e0 order by e0.updateAt desc nulls last", hql("newest", Direction.DESC));
assertThrows(SortNotAllowedException.class, () -> hql("description", Direction.ASC));
```

- 検査のタイミングは spec を `orderBy` に渡したときで、パスを読む前です。
  そのため、`STRICT` モードでは、ルールにない名前がプロパティでなくても `SortNotAllowedException` が発生します。
- ルールは 1 つのエンティティクラスに属します。同じクラスの `SortRules` を 2 回渡すと、最後のものを使います。
- `SortNotAllowedException` は `LambdaQLException` です（[11. エラーと言語](../11-errors-and-languages.md#exceptions) を参照）。
  lambdaQL には Web の依存関係がありません。HTTP 400 の応答の例は [12.5](12-spring-data-jpa.md#sort-policy-with-spring) を参照してください。

外部の文字列の名前と方向は別々に読んでください。方向は `Direction.parse`（[4.3](#43-direction) を参照）、名前は `SortSpec.ofPath` で読みます。

コード例: [`SortQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java)（`sort-spec`、`sort-spec-then`、`sort-spec-reversed`、`sort-spec-path`）、[`ManualJavaSortTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaSortTest.java)（`rule-not-column`）、[`SortPolicyTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortPolicyTest.java)（`rules`、`strict`、`allowed`、`rejected`、`warn`、`annotation`、`string-paths`）。

<a id="411-sorting-inside-subqueries"></a>
## 4.11 サブクエリの中のソート

サブクエリの中のソートは、`limit` と一緒に使うとき（top-1 または top-N）だけ維持されます。
`limit` がなければ結果に影響しないので、lambdaQL はソートを取り除き、警告をログに残します。
[5. サブクエリ](05-subquery.md#59-sorting-inside-a-subquery) を参照してください。

<a id="412-rules-summary-s1-to-s14"></a>
## 4.12 ルールのまとめ（S1〜S14）

| # | ルール | 状態 |
|---|---|---|
| S1 | ソートは 1 つのラムダ式です: キー 1 つ（`o -> o.getPrice()`、任意で `Direction`）または `By` の関数で作ったキー（`o -> desc(...)`）。結合なら `(o, c) -> ...` | 実装済み。結合の形 `(o, c) -> ...` も実装済み |
| S2 | キーはエンティティの値を使わなければなりません。エンティティ自体はキーになれません | 実装済み |
| S3 | 方向は `asc` / `desc` か `sort(key, Direction)` で決めます。`asc(key)`/`desc(key)`（`ASC`/`DESC`）は NULL のポリシーを使い、`asc(key, Nulls.X)`、`desc(key, Nulls.X)`、`*_NULLS_*` は書いたとおりに使います | 実装済み |
| S4 | 本体はキー 1 つか `By` の関数の単純なチェーンだけです。ソート関数を選ぶ `if` と三項演算子、ループは使えません。キーの値の中の三項演算子は `case when` になります | 実装済み |
| S5 | `orderBy` はクエリごとに 1 回です。`orderBy` の後に `where` は使えません。`limit` の後に `orderBy` は使えません | 実装済み |
| S6 | `SortSpec`（`of`、`ofPath`、`then`、`reversed`、`orderBy(spec)`）で再利用します。外部の文字列は persistence unit のソートポリシー（`SortRules`、`SortPolicy`、`SortMode`、[4.10](#sort-policy) を参照）が検査します | 実装済み |
| S7 | キー: カラム、to-one のパス、`+ - *`、拡大変換。キーの値の中の三項演算子は `case when` になります | 実装済み |
| S8 | `orderBy` だけで使った関連のパスは `left join` を使うので、関連のない行も残ります。必須の関連（`optional = false`）は行を失わないので inner join です（A4）。同じパスを `where` で使うと、ソートは `where` の結合を使います | 実装済み |
| S9 | `orderBy` とページングを一緒に使うとき、キーに id がなければルートの `@Id asc` を追加します。`orderBy` がなければ `order by` を付けません。キーに一意で NULL になりえないカラム（`unique = true` かつ `nullable = false`、`@NaturalId`）があれば追加しません（A5） | 実装済み（ルートの id と明示的に結合したエンティティの id） |
| S10 | 解析の結果はラムダ式ごとにキャッシュされます。キャプチャした `Direction` は呼び出しのたびに読み取ります | 実装済み |
| S11 | Spring の `Sort.Order` は `Direction` に 1 対 1 で対応します（Spring モジュール、[12.5](12-spring-data-jpa.md#125-spring-sort-and-pageable) を参照）。プロパティ名はソートポリシーの検査を通り、エイリアスはそのまま渡します | Spring の `Sort` は実装済み（`ignoreCase` は非対応）。ソートポリシーと一緒に使っても動作する |
| S12 | 結合のクエリも同じ形を使います: `(o, c) -> ...` からエンティティ 10 個まで | 実装済み（[6.8](06-select-group.md#68-explicit-joins) を参照） |
| S13 | メソッド参照は、すべてのソートの形でキー 1 つとして使えます。コンパイルは通るが意味が誤っているメソッド参照は拒否されます（[9. Java の注意点](09-java-notes.md#99-method-references) を参照） | 実装済み（getter の参照） |
| S14 | サブクエリのソート: `limit` があれば維持、なければ警告とともに取り除く。データベースごとの違いは lambdaQL が処理します（5 章を参照） | 実装済み |

---

[Java トラック](README.md) | 前: [3. 条件（`where`）](03-where.md) | 次: [5. サブクエリ](05-subquery.md) | [このページの Kotlin 版](../kotlin/04-order-paging.md)
