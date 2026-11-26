<a id="4-sorting-and-paging-java"></a>
# 4. 排序与分页（Java）

[Java 路线](README.md) | [手册首页](../README.md) | 上一章：[3. 条件（`where`）](03-where.md) | 下一章：[5. 子查询](05-subquery.md) | [本页的 Kotlin 版本](../kotlin/04-order-paging.md)

公共页面：[1. 概述](../01-overview.md) · [11. 错误与语言](../11-errors-and-languages.md)

---

<a id="41-one-lambda-for-the-whole-sort"></a>
## 4.1 用一个 Lambda 表达式表达整个排序

排序用**一个 Lambda 表达式**编写。Lambda 表达式接收实体，并用类 `By` 的排序函数描述排序键和方向。
连接 `desc(...)`、`asc(...)` 来添加排序键。

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

- 排序函数是 `By` 的 static 方法：`asc(key)`、`desc(key)`、`asc(key, Nulls.FIRST)`、`desc(key, Nulls.LAST)`、`sort(key, Direction)`。
- lambdaQL 不调用排序 Lambda 表达式，而是分析其字节码 opcode。排序函数只描述顺序。在排序 Lambda 表达式之外调用会抛出 `IllegalStateException`（`LQL-4046`）。

`assertEquals` 中的字符串就是 lambdaQL 生成的 HQL。
本章示例在 H2、MariaDB、Oracle 上返回的行顺序，与在 Java 中用同一排序得到的结果相同。
`price`（`double`）、`status`（`short`）、`id` 不能为 NULL，所以 HQL 中没有 `nulls` 部分。可以为 NULL 的排序键（`description`、`updateAt`、客户 `name`）会加上 `nulls first` 或 `nulls last`（见 [4.4](#44-null-ordering-is-the-same-on-every-database)）。

<!-- region: SortQueryTest.java#sort-basic -->
```java
RenderedQuery q = qb.from(Order.class)
        .where(o -> o.isActive())
        .orderBy(o -> desc(o.getPrice()).asc(o.getId()))
        .toHql();
// price and id can never be NULL, so there is no "nulls" part
assertEquals("select e0 from Order e0 where e0.active = true order by e0.price desc, e0.id asc", q.hql());
```

**各数据库的 SQL**

<!-- sql: sort-basic -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where o1_0.active=true
order by o1_0.price desc,o1_0.id
```

`toHql()` 不执行查询，只返回 HQL。`toList()` 执行查询。

示例代码：[`ManualJavaSortTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaSortTest.java)（`sort-overview`）、[`SortQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java)（`sort-basic`）。

<a id="42-the-short-forms-one-key"></a>
## 4.2 简短写法（一个排序键）

只有一个排序键时，可以不用排序函数，直接返回排序键。

| Java | HQL |
|---|---|
| `orderBy(o -> o.getPrice())` | `order by e0.price asc nulls first` |
| `orderBy(o -> o.getUpdateAt(), Direction.DESC)` | `order by e0.updateAt desc nulls last` |
| `orderBy(Order::getPrice)` | `order by e0.price asc nulls first` |
| `orderBy(Order::getPrice, Direction.DESC)` | `order by e0.price desc nulls last` |

没有 `Direction` 时，简短写法按升序（`ASC`）排序。

`Direction` 参数只用于单个排序键。像 `orderBy(o -> desc(o.getPrice()), Direction.ASC)` 这样同时使用排序函数和 `Direction` 参数，会以 `LQL-3069` 被拒绝。如果方向需要作为参数传入，请使用 `orderBy(o -> sort(o.getPrice(), direction))`。

凡是使用单个排序键的地方都可以使用方法引用。方法引用必须是实体的 getter，例如 `Order::getPrice`。static 方法、带参数的方法、绑定到对象的引用会被拒绝。

示例实体中 `price` 是 `double`，所以没有 `nulls` 部分：

<!-- region: SortQueryTest.java#sort-key -->
```java
assertEquals("select e0 from Order e0 order by e0.price asc",
        qb.from(Order.class).orderBy(o -> o.getPrice()).toHql().hql());
assertEquals("select e0 from Order e0 order by e0.price desc",
        qb.from(Order.class).orderBy(Order::getPrice, Direction.DESC).toHql().hql());
```

**各数据库的 SQL**

<!-- sql: sort-key -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
order by o1_0.price
```

**各数据库的 SQL**

<!-- sql: sort-key#2 -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
order by o1_0.price desc
```

示例代码：[`SortQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java)（`sort-key`）。

<a id="43-direction"></a>
## 4.3 `Direction`

`Direction` 有六个值。

| 值 | 含义 | NULL 位置 |
|---|---|---|
| `ASC` | 升序 | NULL 在**最前**（默认策略） |
| `DESC` | 降序 | NULL 在**最后**（默认策略） |
| `ASC_NULLS_FIRST` | 升序 | NULL 在最前。与 `ASC` 相同 |
| `ASC_NULLS_LAST` | 升序 | NULL 在最后 |
| `DESC_NULLS_FIRST` | 降序 | NULL 在最前 |
| `DESC_NULLS_LAST` | 降序 | NULL 在最后。与 `DESC` 相同 |

`Direction` 还有两个辅助方法。

- `Direction.parse(text)` 从请求参数等文本中读取方向。不区分大小写，`-`、`_`、空格视为相同：`Direction.parse("desc-nulls-last")` 是 `DESC_NULLS_LAST`。未知文本会抛出 `IllegalArgumentException`。
- `reversed()` 返回相反的方向。NULL 位置也一起反转，因此整个顺序完全颠倒：`ASC.reversed()` 是 `DESC`，`ASC_NULLS_LAST.reversed()` 是 `DESC_NULLS_FIRST`。

**要选择 NULL 位置，** 请用 enum `Nulls` 作为第二个参数：`asc(key, Nulls.LAST)`、`desc(key, Nulls.FIRST)`。
`Nulls.FIRST` 表示“NULL 在最前”，`Nulls.LAST` 表示“NULL 在最后”。
没有第二个参数时，`asc(key)` 和 `desc(key)` 使用默认 NULL 策略（见 [4.4](#44-null-ordering-is-the-same-on-every-database)）。

| 写法 | 等同于 |
|---|---|
| `asc(key)` | `ASC`（NULL 在最前，默认策略） |
| `desc(key)` | `DESC`（NULL 在最后，默认策略） |
| `asc(key, Nulls.FIRST)` | `ASC_NULLS_FIRST` |
| `asc(key, Nulls.LAST)` | `ASC_NULLS_LAST` |
| `desc(key, Nulls.FIRST)` | `DESC_NULLS_FIRST` |
| `desc(key, Nulls.LAST)` | `DESC_NULLS_LAST` |

`description` 是可以为 NULL 的 `String`，`id` 不能为 NULL：

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

**各数据库的 SQL**

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

**各数据库的 SQL**

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

**各数据库的 SQL**

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

SQL 中可以看到 NULL 策略。H2 和 MariaDB 在与自身默认值相同时省略该子句。Oracle 的默认值相反，所以写出 `asc nulls first` / `desc nulls last`。MariaDB 没有 `NULLS` 语法，非默认的位置先用 `case when … is null then … end` 排序。

**方向来自变量时，** 请把捕获的 `Direction` 与 `sort(key, direction)` 一起使用。

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

这里的三元运算符 `newestFirst ? ... : ...` 没有问题，因为它在 Lambda 表达式**之外**执行。

<!-- region: SortQueryTest.java#sort-dynamic -->
```java
Direction dir = Direction.parse(text);           // for example from a request parameter
OrderedSelectQuery<Order> q = qb.from(Order.class)
        .orderBy(o -> sort(o.getDescription(), dir).asc(o.getId()));
```

**各数据库的 SQL**

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

**各数据库的 SQL**

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

`text` 为 `"desc"` 时，HQL 是 `... order by e0.description desc nulls last, e0.id asc`。
`text` 为 `"asc_nulls_last"` 时，是 `... order by e0.description asc nulls last, e0.id asc`。
Lambda 表达式只分析一次。每次执行查询时读取 `dir`。方向为 `null` 时，执行查询时会抛出异常。

示例代码：[`SortQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java)（`sort-nulls`、`sort-dynamic`）、[`ManualJavaSortTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaSortTest.java)（`sort-direction`）。

<a id="44-null-ordering-is-the-same-on-every-database"></a>
## 4.4 NULL 顺序在所有数据库上相同

各数据库中 NULL 的位置不同。

| | H2 | MariaDB | Oracle |
|---|---|---|---|
| `ASC` 默认值 | NULL 在最前 | NULL 在最前 | NULL 在**最后** |
| `DESC` 默认值 | NULL 在最后 | NULL 在最后 | NULL 在**最前** |
| SQL 中的 `NULLS FIRST/LAST` | 支持 | **不支持（语法错误）** | 支持 |

lambdaQL 用一个策略解决这个问题：**NULL 是最小的值。**

- `ASC` = `ASC_NULLS_FIRST`
- `DESC` = `DESC_NULLS_LAST`

这与 Java 的 `Comparator.nullsFirst(Comparator.naturalOrder())` 相同。
有了这个策略，三种数据库返回相同的顺序（对数字和日期而言）。

**lambdaQL 的写法。** 对可以为 NULL 的排序键，lambdaQL 总是在 HQL 中写出 NULL 位置。例如：`e0.description asc nulls first`。
不能为 NULL 的排序键（`double` 这样的基本类型列或 `@Id`）没有 `nulls` 部分。
关联之后的列在没有关联时会成为 NULL（可能有没有客户的订单），所以会加上。路径中所有关联步骤都是必需的（`optional = false`）且值不能为 NULL 时，不会加上。

然后由 Hibernate 写出各数据库的 SQL。与数据库默认值相同时，省略该子句。
MariaDB 没有 `NULLS` 语法，非默认的位置由 Hibernate 写成 `CASE` 表达式。

生成的 SQL：

| 写法 | H2 | MariaDB | Oracle |
|---|---|---|---|
| `asc(key)`、`desc(key)`（`ASC`、`DESC`） | 无额外子句（已是默认值） | 无额外子句 | 添加 `nulls first` / `nulls last` |
| 与数据库默认值相同的 NULL 位置（`asc(key, Nulls.X)`、`desc(key, Nulls.X)`、`*_NULLS_*`） | 无额外子句 | 无额外子句 | 无额外子句 |
| **不是**数据库默认值的 NULL 位置 | `nulls first/last` | Hibernate 写出 `case when x is null ...` | `nulls first/last` |
| 不能为 NULL 的排序键（与方向无关） | 无额外子句 | 无额外子句 | 无额外子句 |

> [!TIP]
> 在 MariaDB 上，`asc(key, Nulls.LAST)`（`ASC_NULLS_LAST`）和 `desc(key, Nulls.FIRST)`（`DESC_NULLS_FIRST`）会变成 `CASE` 表达式。数据库无法对这种排序使用索引。尽可能使用 `asc(key)` / `desc(key)`。

<a id="45-what-a-sort-key-can-be"></a>
## 4.5 可以作为排序键的内容

| 排序键 | 是否允许 | 说明 |
|---|---|---|
| 列：`o.getPrice()` | 允许 | |
| 对一路径：`o.getCustomer().getName()` | 允许 | `left join`（必需关联使用 inner join），见下文 |
| `@Lob` 列 | 允许 | Oracle 会拒绝。`DEBUG` 级别 `LQL-5010`，见下文 |
| `@Enumerated(EnumType.STRING)` enum 列 | 允许 | 按名称排序（不是声明顺序）。`DEBUG` 级别 `LQL-5011`，见下文 |
| 算术：`o.getPrice() * 2`、`+`、`-` | 允许 | 见下面的示例 |
| 拓宽转换（`short` 到 `long` 等） | 允许 | 不转换，直接按列排序。见下面的示例 |
| 不使用实体的值：`asc(1)`、捕获的值 | **被拒绝** | 见 [4.6](#46-rules-for-the-lambda-body) |
| 列上的函数：`lower(name)` | 不支持 | |

即使实体类实现了 `Comparable`，lambdaQL 在读取 Lambda 表达式时也会拒绝把它用作排序键。

算术排序键按表达式本身排序。拓宽转换（`(long) o.getStatus()`）在 HQL 中消失，按列排序。

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

**关联路径使用 `left join`。** HQL 中的简单路径 `order by o.customer.name` 在三种数据库上都会变成 inner join，没有客户的订单会消失。
lambdaQL 改为添加 `left join`。没有客户的订单也留在结果中。这类订单的 `j1.name` 为 NULL，因此在 `asc` 中排在最前。

<!-- region: SortQueryTest.java#sort-path -->
```java
RenderedQuery q = qb.from(Order.class)
        .orderBy(o -> asc(o.getCustomer().getName()).asc(o.getId()))
        .toHql();
// a path used only for sorting is a LEFT JOIN: the order without a customer stays
assertEquals("select e0 from Order e0 left join e0.customer j1 order by j1.name asc nulls first, e0.id asc", q.hql());
```

**各数据库的 SQL**

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

**必需关联使用 inner join。** 用 `optional = false` 映射的关联（例如 `@ManyToOne(optional = false)`）目标总是存在，因此 inner join 不会丢失行。
所以即使路径只用于排序或 select，这类关联也使用 `join`。其他关联像上面一样使用 `left join`。
路径中所有关联步骤都是必需的且值不能为 NULL 时，排序也不加 `nulls first/last`。

示例实体 `OrderLine` 的 `@ManyToOne(optional = false) Order order`：

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

**`@Lob` 列的排序。** 在 `orderBy`、`distinct`、`groupBy` 中使用 `@Lob` 列时，H2 和 MariaDB 会执行，但 Oracle 会拒绝（ORA-22848、ORA-22849，实测）。`=` 比较在三种数据库上都可以执行。
lambdaQL 不修改查询，按列和用途各以 `DEBUG` 级别记录一次 `LQL-5010`。

`OrderLine.memo` 是 `@Lob`：

<!-- region: MappingRulesTest.java#lob -->
```java
// memo is @Lob: H2 and MariaDB sort and group it, Oracle rejects it (ORA-22848, ORA-22849).
// lambdaQL keeps the query and logs LQL-5010 at DEBUG
qb.from(OrderLine.class).orderBy(l -> asc(l.getMemo())).toHql();
qb.from(OrderLine.class).groupBy(l -> l.getMemo()).select(l -> l.getMemo()).toHql();
qb.from(OrderLine.class).select(l -> l.getMemo()).distinct().toHql();
```

**`EnumType.STRING` enum 的排序。** 用 `@Enumerated(EnumType.STRING)` 映射的 enum 列，数据库按常量**名称**的字符串顺序排序。Java 的 enum 比较按声明顺序，因此结果顺序可能不同。
lambdaQL 不修改查询，按列以 `DEBUG` 级别记录一次 `LQL-5011`。`ORDINAL`（或没有 `@Enumerated`）按声明顺序排序，因此不记录。

<!-- region: MappingRulesTest.java#enum-string-sort -->
```java
// kind is @Enumerated(EnumType.STRING): the database sorts by name, Java by declaration order (LQL-5011, DEBUG)
assertEquals("select e0 from Order e0 order by e0.kind asc nulls first",
        qb.from(Order.class).orderBy(o -> asc(o.getKind())).toHql().hql());
```

别名与 `where` 共用。如果 `where` 已经 join 了同一路径，排序就使用该 join。
下面的条件只在有客户的订单上为真，所以这个 join 是 inner join，没有客户的订单已经被排除（见 [3.10](03-where.md#310-association-paths)）。

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

**各数据库的 SQL**

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

示例代码：[`ManualJavaSortTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaSortTest.java)（`sort-arith`）、[`SortQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java)（`sort-path`、`sort-path-where`）、[`MappingRulesTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/MappingRulesTest.java)（`required-association`、`lob`、`enum-string-sort`）。

<a id="46-rules-for-the-lambda-body"></a>
## 4.6 Lambda 表达式主体的规则

主体必须是**单个排序键，或 `By` 函数的简单调用链**。

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

lambdaQL 在调用 `orderBy` 时检查这一点，并抛出 `LambdaQLException`（例如，按条件选择的排序函数为 `LQL-2023`，不使用实体的排序键为 `LQL-3031`）。
所有排序键都必须使用实体。只由常量或捕获值构成的排序键会被拒绝。
排序键值中的三元运算符（`asc(o.isActive() ? 0 : 1)`）会变成 `case when`（见 [3.5.1](03-where.md#351-values-chosen-by-a-condition)）。

如果需要在运行时改变方向，请用变量传入：`sort(key, dir)`。如果需要改变排序键本身，请在普通 Java 代码的两个分支中分别构建查询。

示例代码：[`ManualJavaSortTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaSortTest.java)（`sort-body`）。

<a id="47-order-of-calls"></a>
## 4.7 调用顺序

查询按 `from(...).where(...).orderBy(...).limit(...).offset(...)` 的顺序编写。
不使用 `orderBy` 而只使用 `limit` 或 `offset` 时，不会加上 `order by`（见 [4.8](#48-paging)）。

<a id="48-paging"></a>
## 4.8 分页

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

`limit` 和 `offset` 不写入 HQL，而是传给 Hibernate（`setMaxResults` / `setFirstResult`）。排序键中没有 id，所以 lambdaQL 添加了 `e0.id asc`（见[下一节](#tie-breaker-automatic-id)）。

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

**各数据库的 SQL**

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

分页 SQL 因数据库而异。H2 和 Oracle 得到 `offset ? rows fetch first ? rows only`，MariaDB 得到 `limit ?,?`。没有 offset 时，只写 `fetch first ? rows only` 或 `limit ?`（见下面的 `page-only`）。

`limit` 和 `offset` 接受 0 以上的值。传入负数会抛出 `IllegalArgumentException`。

lambdaQL 不在 HQL 中写 `LIMIT`。Hibernate 为各数据库写出正确的 SQL。

| 数据库 | SQL |
|---|---|
| H2 | `offset ? rows fetch first ? rows only` |
| MariaDB | `limit ?, ?` |
| Oracle | `offset ? rows fetch first ? rows only` |

**没有 `orderBy` 的分页。** 不调用 `orderBy` 而调用 `limit` 和 `offset` 时，不加 `order by`。哪一行出现在哪一页由数据库决定。要稳定地分页，请使用 `orderBy`。

<!-- region: SortQueryTest.java#page-only -->
```java
// paging without orderBy: no order by, so which rows come is up to the database; use orderBy for stable pages
RenderedQuery q = qb.from(Order.class).where(o -> o.isActive()).limit(2).toHql();
assertEquals("select e0 from Order e0 where e0.active = true", q.hql());
```

**各数据库的 SQL**

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

**第一行与总数。**

- `orderBy(...).first()` 以 `Optional` 返回该顺序中的第一行。只读取一行。`offset` 会保留：`orderBy(...).offset(1).first()` 返回第二行。
- `count()` 统计满足条件的全部行。在分页界面中，请把 where 阶段的查询保存在变量中。在该变量上调用 `count()` 得到总数，再调用 `orderBy(...).limit(...).toList()` 得到当前页。

详情见[获取结果](02-getting-started.md#getting-the-result)。

<a id="tie-breaker-automatic-id"></a>
### 决胜键（自动添加 `id`）

上面 HQL 的末尾有 lambdaQL 添加的 `e0.id asc`。原因如下。

- 多行可能有相同的 `status` 或 `price`。数据库可以按**任意顺序**返回这些行。Oracle 实际上就是这样（已测量）。
- 这样第 2 页和第 3 页可能出现同一行两次，或者漏掉某行。

规则：**同时使用 `orderBy` 和分页且排序键中没有 id 时，lambdaQL 把根实体的 `@Id asc` 添加为最后一个排序键。**

- 只添加一次。排序键中已有 id 时（例如 `desc(o.getId())`）不添加。
- 排序键中有主实体的唯一且不能为 NULL 的列时不添加。`nullable = false` 的 `@Column(unique = true)`、Hibernate `@NaturalId`、`@Id` 属于这种情况，因为仅凭这个排序键就能确定所有行的顺序。
  可以为 NULL 的唯一列不属于这种情况，因为 NULL 可能出现在多行中。
- 没有 `orderBy`，或没有 `limit` 和 `offset` 时，不添加任何内容。
- 有显式 join（`join`、`leftJoin`、`crossJoin`）时，也按 join 顺序添加所有被 join 实体的 id（见 [6.8](06-select-group.md#68-explicit-joins)）。

`OrderLine.code` 是 `@Column(unique = true, nullable = false)`：

<!-- region: MappingRulesTest.java#unique-key -->
```java
// code is @Column(unique = true, nullable = false): it orders every row, so paging adds no id tie-breaker
assertEquals("select e0 from OrderLine e0 order by e0.code asc",
        qb.from(OrderLine.class).orderBy(l -> asc(l.getCode())).limit(2).toHql().hql());
// a key that is not unique still gets the id tie-breaker
assertEquals("select e0 from OrderLine e0 join e0.order j1 order by j1.price asc, e0.id asc",
        qb.from(OrderLine.class).orderBy(l -> asc(l.getOrder().getPrice())).limit(2).toHql().hql());
```

示例代码：[`ManualJavaSortTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaSortTest.java)（`page`）、[`SortQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java)（`sort-page`、`page-only`）、[`MappingRulesTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/MappingRulesTest.java)（`unique-key`）。

<a id="49-strings-and-special-values"></a>
## 4.9 字符串与特殊值

- 字符串顺序遵循数据库排序规则（collation）。MariaDB `_ci` 忽略大小写。Oracle 使用 `NLS_SORT`，它跟随 JVM 区域设置（例如 `de_DE` 是德语顺序）。Oracle 建议使用 `NLS_SORT=BINARY`。
- Oracle 把 `''` 存为 NULL，因此空字符串按 NULL 排序。
- `NaN` 在各数据库中的排序位置不同。

<a id="410-reusable-and-external-sorts"></a>
## 4.10 可重用排序与外部排序

**`SortSpec`：可以保存的排序。** `SortSpec` 是保存在变量或字段中、供多个查询使用的排序。
用 `SortSpec.of(Order.class, o -> desc(...))` 创建。Lambda 表达式与 `orderBy(o -> ...)` 的 Lambda 表达式相同，因此适用同样的规则（见 [4.6](#46-rules-for-the-lambda-body)）。
然后传给 `orderBy(spec)`。

- `SortSpec` 不可变。`then()` 和 `reversed()` 返回新的 spec。因此一个 spec 可以被多个查询和线程安全地共用，例如可以放在 `static final` 字段中。
- lambdaQL 在 spec 用于 `orderBy` 时检查 Lambda 表达式。每个 Lambda 表达式只分析一次。
- 捕获的值是 Lambda 表达式创建那一刻的值。`static final` 字段中的 spec 一直保持该值。如果需要在运行时改变方向，请使用 `orderBy(o -> sort(key, dir))`（见 [4.3](#43-direction)）。
- `orderBy(spec)` 之后也可以分页：`limit`、`offset`、`toList()`、`first()`。

<!-- region: SortQueryTest.java#sort-spec -->
```java
// keep it, for example as a static final field, and use it in many queries
SortSpec<Order> byDescription = SortSpec.of(Order.class, o -> desc(o.getDescription()).asc(o.getId()));
RenderedQuery q = qb.from(Order.class).where(o -> o.isActive()).orderBy(byDescription).toHql();
assertEquals("select e0 from Order e0 where e0.active = true order by e0.description desc nulls last, e0.id asc", q.hql());
```

**各数据库的 SQL**

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

SQL 与用 `orderBy(o -> ...)` 编写同一排序时相同。Oracle 的 `desc` 默认值相反，因此加上 `nulls last`（见 [4.4](#44-null-ordering-is-the-same-on-every-database)）。

**`then()`：在已有排序键之后添加排序键。** `a.then(b)` 先按 `a` 的排序键排序，值相同的行再按 `b` 的排序键排序。
`then()` 接受另一个 `SortSpec` 或排序 Lambda 表达式。

示例实体中 `status` 和 `price` 不能为 NULL，所以没有 `nulls` 部分：

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

**各数据库的 SQL**

<!-- sql: sort-spec-then -->
```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
order by o1_0.status,o1_0.price desc
```

**`reversed()`：反转所有排序键。** `spec.reversed()` 反转所有排序键。NULL 位置也一起反转：`asc`（NULL 在前）变成 `desc`（NULL 在后）。因此整个行顺序正好颠倒。
这与 `Direction.reversed()` 相同（见 [4.3](#43-direction)）。`spec.reversed().reversed()` 回到原来的顺序。

<!-- region: SortQueryTest.java#sort-spec-reversed -->
```java
SortSpec<Order> spec = SortSpec.of(Order.class, o -> asc(o.getDescription()).asc(o.getId()));
// every key is reversed, the NULL position too: asc nulls first -> desc nulls last
assertEquals("select e0 from Order e0 order by e0.description desc nulls last, e0.id desc",
        qb.from(Order.class).orderBy(spec.reversed()).toHql().hql());
```

**各数据库的 SQL**

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

H2 和 MariaDB 在 `desc` 中已经把 NULL 放在后面，因此不写额外语法。Oracle 加上 `nulls last`。

**`SortSpec.ofPath`：用属性名创建排序。** 排序键有时以字符串形式从代码外部传入。Spring `Sort` 就是一例，Spring 模块用 `ofPath` 转换 Spring `Sort`（见 [12.5](12-spring-data-jpa.md#125-spring-sort-and-pageable)）。
`SortSpec.ofPath(Order.class, "customer.name", Direction.DESC)` 用属性路径和 `Direction` 创建 spec。

- 路径是用 `.` 连接属性名的字符串。每一部分都必须是持久化属性：中间是对一关联或嵌入值，末尾是基本值。
- 其他路径是错误（把 spec 传给 `orderBy` 时抛出 `IllegalArgumentException`）。因此字符串不会被转换成其他 HQL。
- 允许的名称由持久化单元的排序策略决定（见下面的[排序策略](#sort-policy)）。
  既没有策略也没有 `@Sortable` 字段时，接受**任何**持久化属性，lambdaQL 记录 `LQL-5003`（默认级别为 `INFO`）。
- 关联路径与排序 Lambda 表达式一样使用 `left join`（必需关联使用 inner join）。可以用 `then()` 连接 `ofPath` spec 和 Lambda 表达式 spec。

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
### 排序策略：允许的外部名称

**已实现**。

来自代码外部的排序名称（请求参数、Spring `Sort` 或 `Pageable`）可以指向任何持久化属性。
**排序策略**决定允许哪些名称。

- `SortRules<T>` 是一个实体的允许名称列表。每一列用 Lambda 表达式指定。
- `SortPolicy` 保存各实体的规则和模式（`SortMode`）。每个 `EntityManagerFactory`（即每个持久化单元）注册一个策略。使用该工厂的所有 `QueryBuilder` 和 repository 都使用这个策略。
- 在实体字段上加 `@Sortable`，无需代码即可指定规则（见下面的 [`@Sortable` 规则](#rules-from-sortable)）。

一个实体的规则按以下顺序只从**一个**地方获取：策略中的 `SortRules`（代码或 Spring `SortPolicy` bean）、Spring Boot 属性 `lambdaql.sort.*`（见 [12.5](12-spring-data-jpa.md#sort-policy-with-spring)）、实体的 `@Sortable` 字段。
不会合并两个地方的规则。

**检查对象。** 只检查由字符串创建的排序：`SortSpec.ofPath`，以及 Spring `Sort` 或 `Pageable`（Spring 模块使用 `ofPath`，见 [12.5](12-spring-data-jpa.md#sort-policy-with-spring)）。
排序 Lambda 表达式（`orderBy(o -> ...)`、`SortSpec.of`）是开发者的代码，因此不检查。

| 规则 | 允许的外部名称 |
|---|---|
| `allow(o -> o.getPrice())` | `price`（列的路径） |
| `allow(o -> o.getCustomer().getName())` | `customer.name`。经过关联的路径只有这样明确指定时才允许 |
| `alias("newest", o -> o.getUpdateAt())` | `newest`。排序依据是 `updateAt`。此时路径 `updateAt` 本身**不是**外部名称，因此不会向客户端暴露属性名 |

规则必须是实体的列。
其他排序键在第一次使用规则时以 `LQL-4041` 被拒绝。

<!-- region: ManualJavaSortTest.java#rule-not-column -->
```java
// a rule that is not a column of the entity is rejected when the rules are first used
SortPolicy.register(emf, SortPolicy.of(SortMode.STRICT,
        SortRules.of(Order.class).allow(o -> o.getPrice() * 2)));
LambdaQLException e = assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).orderBy(SortSpec.ofPath(Order.class, "price", Direction.ASC)).toHql());
assertEquals("LQL-4041", e.code());
```

| `SortMode` | 对规则不允许的名称的处理 |
|---|---|
| `OFF` | 执行排序，不检查 |
| `INFO` | 执行排序，并按实体和名称各以 `INFO` 级别记录一次 `LQL-5003`。**这是默认值：**没有注册策略时使用 `INFO`，规则只来自 `@Sortable` |
| `WARN` | 与 `INFO` 相同，只是日志级别为 `WARN` |
| `STRICT` | 以 `SortNotAllowedException`（`LQL-4040`）拒绝。既没有规则也没有 `@Sortable` 字段的实体会拒绝所有名称 |

因此，在注册策略之前，每个 `@Sortable` 不允许的外部排序名称都会按实体记录一次 `LQL-5003`。
要拒绝未知名称，请以 `STRICT` 注册策略；要关闭检查，请以 `OFF` 注册策略。

| 方法 | 功能 |
|---|---|
| `SortPolicy.of(mode, rules...)` | 用模式和各实体的规则创建策略 |
| `SortPolicy.register(emf, policy)` | 注册持久化单元的策略。替换之前的策略 |
| `SortPolicy.unregister(emf)` | 移除策略。恢复默认值（`INFO`，只使用 `@Sortable` 规则） |
| `SortPolicy.find(emf)` | 返回已注册的策略（`Optional`） |
| `SortPolicy.effective(emf)` | 返回正在生效的策略。没有注册策略时返回默认值 |

在 Spring Boot 中，请定义 `SortPolicy` bean，而不是调用 `register`（见 [12.5](12-spring-data-jpa.md#sort-policy-with-spring)）。

<!-- region: SortPolicyTest.java#rules -->
```java
SortRules<Order> orderRules = SortRules.of(Order.class)
        .allow(o -> o.getPrice())                        // the name "price"
        .allow(o -> o.getCustomer().getName())           // an association path: allowed only when listed
        .alias("newest", o -> o.getUpdateAt());          // the outside name "newest" sorts by updateAt
```

把规则放入策略，并注册到持久化单元。

<!-- region: SortPolicyTest.java#strict -->
```java
SortPolicy.register(emf, SortPolicy.of(SortMode.STRICT, orderRules));
```

下面示例中的 `hql(name, direction)` 是示例的辅助方法。
它返回 `qb.from(Order.class).orderBy(SortSpec.ofPath(Order.class, name, direction)).toHql().hql()`。

<!-- region: SortPolicyTest.java#allowed -->
```java
assertEquals("select e0 from Order e0 order by e0.price asc", hql("price", Direction.ASC));
assertEquals("select e0 from Order e0 left join e0.customer j1 order by j1.name asc nulls first", hql("customer.name", Direction.ASC));
// the alias: the client sends "newest", lambdaQL sorts by updateAt
assertEquals("select e0 from Order e0 order by e0.updateAt desc nulls last", hql("newest", Direction.DESC));
```

`STRICT` 模式：

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
**`@Sortable` 规则。** 在实体的字段或 getter 上标注 `@org.lambdaql.Sortable` 也会成为规则。

| 注解 | 允许的外部名称 |
|---|---|
| 字段 `name` 上的 `@Sortable` | `name` |
| 字段 `grade` 上的 `@Sortable("level")` | `level`。排序依据是 `grade`（别名） |
| 关联字段 `customer` 上的 `@Sortable(path = "name")` | `customer.name` |

- lambdaQL 只读取 `from(...)` 中指定的实体上的注解。其他实体上的注解不会允许关联路径，因此请在关联字段上指定 `path`。
- 只有策略中没有该实体的规则（代码、Spring bean、Spring Boot 属性）时才使用注解。
- 在没有注册策略的默认 `INFO` 模式下也有效。

上面的 `STRICT` 策略只有 `Order` 的规则：

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
**字符串规则。** `allowPath(String...)` 和 `aliasPath(name, path)` 以字符串接收属性路径。它们用于配置文件，[12.5](12-spring-data-jpa.md#sort-policy-with-spring) 中的 Spring Boot 属性使用这种形式。
路径在第一次使用规则时与实体对照。在代码中请使用 Lambda 表达式形式 `allow` 和 `alias`。

<!-- region: SortPolicyTest.java#string-paths -->
```java
// the string forms used by configuration files (lambdaql.sort.allow / lambdaql.sort.alias)
SortPolicy.register(emf, SortPolicy.of(SortMode.STRICT,
        SortRules.of(Order.class).allowPath("price", "customer.name").aliasPath("newest", "updateAt")));
assertEquals("select e0 from Order e0 order by e0.updateAt desc nulls last", hql("newest", Direction.DESC));
assertThrows(SortNotAllowedException.class, () -> hql("description", Direction.ASC));
```

- 检查发生在把 spec 传给 `orderBy` 时，在读取路径之前。
  因此在 `STRICT` 模式下，不在规则中的名称即使不是属性，也会抛出 `SortNotAllowedException`。
- 规则属于一个实体类。同一个类的 `SortRules` 传入两次时，使用最后一个。
- `SortNotAllowedException` 是 `LambdaQLException`（见 [11. 错误与语言](../11-errors-and-languages.md#exceptions)）。
  lambdaQL 没有 Web 依赖。HTTP 400 响应示例见 [12.5](12-spring-data-jpa.md#sort-policy-with-spring)。

外部字符串中的名称和方向请分别读取：方向用 `Direction.parse`（见 [4.3](#43-direction)），名称用 `SortSpec.ofPath`。

示例代码：[`SortQueryTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java)（`sort-spec`、`sort-spec-then`、`sort-spec-reversed`、`sort-spec-path`）、[`ManualJavaSortTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaSortTest.java)（`rule-not-column`）、[`SortPolicyTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortPolicyTest.java)（`rules`、`strict`、`allowed`、`rejected`、`warn`、`annotation`、`string-paths`）。

<a id="411-sorting-inside-subqueries"></a>
## 4.11 子查询内部的排序

子查询内部的排序只有与 `limit` 一起使用时（top-1 或 top-N）才保留。
没有 `limit` 时排序不影响结果，因此 lambdaQL 删除排序并记录警告。
见 [5. 子查询](05-subquery.md#59-sorting-inside-a-subquery)。

<a id="412-rules-summary-s1-to-s14"></a>
## 4.12 规则汇总（S1~S14）

| # | 规则 | 状态 |
|---|---|---|
| S1 | 排序是一个 Lambda 表达式：单个排序键（`o -> o.getPrice()`，可选 `Direction`）或用 `By` 的函数构成的排序键（`o -> desc(...)`）。join 时为 `(o, c) -> ...` | 已实现。join 写法 `(o, c) -> ...` 也已实现 |
| S2 | 排序键必须使用实体的值。实体本身不能作为排序键 | 已实现 |
| S3 | 方向由 `asc` / `desc` 或 `sort(key, Direction)` 决定。`asc(key)`/`desc(key)`（`ASC`/`DESC`）使用 NULL 策略，`asc(key, Nulls.X)`、`desc(key, Nulls.X)`、`*_NULLS_*` 按写法使用 | 已实现 |
| S4 | 主体只能是单个排序键或 `By` 函数的简单调用链。不能用 `if`、三元运算符选择排序函数，也不能用循环。排序键值中的三元运算符变成 `case when` | 已实现 |
| S5 | 每个查询只能调用一次 `orderBy`。`orderBy` 之后不能调用 `where`。`limit` 之后不能调用 `orderBy` | 已实现 |
| S6 | 用 `SortSpec`（`of`、`ofPath`、`then`、`reversed`、`orderBy(spec)`）重用。外部字符串由持久化单元的排序策略（`SortRules`、`SortPolicy`、`SortMode`，见 [4.10](#sort-policy)）检查 | 已实现 |
| S7 | 排序键：列、对一路径、`+ - *`、拓宽转换。排序键值中的三元运算符变成 `case when` | 已实现 |
| S8 | 只在 `orderBy` 中使用的关联路径使用 `left join`，因此没有关联的行也会保留。必需关联（`optional = false`）不会丢失行，因此使用 inner join（A4）。`where` 中使用同一路径时，排序使用 `where` 的 join | 已实现 |
| S9 | 同时使用 `orderBy` 和分页且排序键中没有 id 时，添加根实体的 `@Id asc`。没有 `orderBy` 时不加 `order by`。排序键中有唯一且不能为 NULL 的列（`unique = true` 且 `nullable = false`、`@NaturalId`）时不添加（A5） | 已实现（根实体 id 和显式 join 实体的 id） |
| S10 | 分析结果按 Lambda 表达式缓存。捕获的 `Direction` 每次调用时读取 | 已实现 |
| S11 | Spring `Sort.Order` 与 `Direction` 一一对应（Spring 模块，见 [12.5](12-spring-data-jpa.md#125-spring-sort-and-pageable)）。属性名经过排序策略检查，别名按原样传递 | Spring `Sort` 已实现（不支持 `ignoreCase`）。与排序策略一起使用也可以 |
| S12 | join 查询使用相同的形式：从 `(o, c) -> ...` 到 10 个实体 | 已实现（见 [6.8](06-select-group.md#68-explicit-joins)） |
| S13 | 在所有排序写法中，方法引用都可以作为单个排序键使用。能编译但含义错误的方法引用会被拒绝（见 [9. Java 说明](09-java-notes.md#99-method-references)） | 已实现（getter 引用） |
| S14 | 子查询排序：有 `limit` 时保留，没有时删除并发出警告。数据库差异由 lambdaQL 处理（见第 5 章） | 已实现 |

---

[Java 路线](README.md) | 上一章：[3. 条件（`where`）](03-where.md) | 下一章：[5. 子查询](05-subquery.md) | [本页的 Kotlin 版本](../kotlin/04-order-paging.md)
