<a id="72-update"></a>
# 7.2 更新（Kotlin）

> [!NOTE]
> **状态：已设计，尚未实现。**
> 本章是已确定的设计（决定 D28，里程碑 M10）。
> 该 Kotlin 语法可以编译（kotlinc 2.3.21），SQL 已在 H2、MariaDB 和 Oracle 上使用 Hibernate 6.6.5 检查过。
> 目前它在 lambdaQL 中**还不能**运行。目前只有分析核心以及 `where` + `toList()` 可以工作
> （已在 H2、MariaDB 和 Oracle 上测试）。完整状态见[路线 README](README.md)。

[7. 更新、插入、删除（Kotlin）](07-dml.md) | **7.2 更新** | [7.4 删除](07-4-delete.md) | [7.5 插入](07-5-insert.md) | [Java](../java/07-2-update.md)

---


## 基本形式

```kotlin
val n = qb.update(Order::class.java)
    .set { it.status = st; it.price = it.price!! * 1.1 }
    .where { it.status == 1 }
    .execute()                     // 更新的行数
```

HQL：

```sql
update Order o set o.status = :p1, o.price = o.price * 1.1 where o.status = 1
```

**关于 SQL 块。** `…` 是缩短的 select 列表（实体的所有列），`?` 是绑定参数。[`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java) 把本页的 HQL 直接交给 Hibernate，并记录在 H2 2.3、MariaDB 10.11 和 Oracle 23.26（Hibernate 6.6.5）上得到的 SQL。lambdaQL 目前还不会生成这些查询。如果 Hibernate 的 SQL 发生变化，测试就会失败。

**各数据库的 SQL（计划中）** — Hibernate 对计划中 HQL 的翻译，由 `PlannedSqlTest` 记录；lambdaQL 目前还不会生成此查询。来自 `expected-sql/` 的 [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)，示例 `07-update`：

```sql
-- H2, MariaDB
update orders o1_0
set status=?,price=(o1_0.price*1.1)
where o1_0.status=1

-- Oracle
update orders o1_0
set o1_0.status=?,o1_0.price=(o1_0.price*1.1)
where o1_0.status=1
```

Oracle 在 `set` 中写出别名（`set o1_0.status=?`）；H2 和 MariaDB 不写。

`st` 是捕获值。lambdaQL 把它作为绑定参数发送。`!!` 会被忽略（D23）。

## 值：null、日期、布尔值

```kotlin
qb.update(Order::class.java)
    .set { it.description = null; it.updateAt = now; it.active = true }
    .where { it.id == id }
    .execute()
```

枚举值的处理方式相同。每个捕获值都会得到**自己的参数**，即使同一个值使用了两次（规则 U7）。

## 在值上累加，为 NULL 提供默认值

```kotlin
qb.update(Order::class.java).set { it.cnt += 1 }.where { it.status == 1 }.execute()
// → set o.cnt = o.cnt + 1

qb.update(Order::class.java)
    .set { it.price = (it.price ?: 0.0) + 1 }
    .all()
    .execute()
// → set o.price = coalesce(o.price, 0.0) + 1.0
```

在 Kotlin 中，`+=` 需要非空属性（`cnt: Int`）。对于可空属性，请写 `it.total = it.total!! + 1`，或者像上面那样使用 `?:`。

## 嵌入字段

```kotlin
qb.update(Order::class.java).set { it.address.city = city }.where { it.id == id }.execute()
// → set o.address.city = :p1

qb.update(Order::class.java).set { it.address = addr }.where { it.id == id }.execute()
// → 设置嵌入值的所有列（city、zip）
```

**各数据库的 SQL（计划中）** — Hibernate 对计划中 HQL 的翻译，由 `PlannedSqlTest` 记录；lambdaQL 目前还不会生成此查询。来自 `expected-sql/` 的 [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)，示例 `07-update-embedded`：

```sql
-- H2, MariaDB
update orders o1_0
set city=?
where o1_0.id=?

-- Oracle
update orders o1_0
set o1_0.city=?
where o1_0.id=?
```

## 修改关联（外键）

```kotlin
qb.update(Order::class.java).set { it.customer?.id = cid }.where { it.id == id }.execute()
// → set o.customer.id = :p1      （外键列；?. 赋值总是会执行赋值）

qb.update(Order::class.java).set { it.customer = null }.where { it.id == id }.execute()
// → set o.customer = null

qb.update(Order::class.java).set { it.customer = customer }.where { it.id == id }.execute()
// → 绑定捕获的 customer 的 @Id（规则 U11）
```

**各数据库的 SQL（计划中）** — Hibernate 对计划中 HQL 的翻译，由 `PlannedSqlTest` 记录；lambdaQL 目前还不会生成此查询。来自 `expected-sql/` 的 [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)，示例 `07-update-fk`：

```sql
-- H2, MariaDB
update orders o1_0
set customer_id=?
where o1_0.id=?

-- Oracle
update orders o1_0
set o1_0.customer_id=?
where o1_0.id=?
```

**各数据库的 SQL（计划中）** — Hibernate 对计划中 HQL 的翻译，由 `PlannedSqlTest` 记录；lambdaQL 目前还不会生成此查询。来自 `expected-sql/` 的 [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)，示例 `07-update-fk-null`：

```sql
-- H2, MariaDB
update orders o1_0
set customer_id=null
where o1_0.id=?

-- Oracle
update orders o1_0
set o1_0.customer_id=null
where o1_0.id=?
```

对于关联实体，你只能设置它的 `@Id`（外键）。
`it.customer?.name = x` 是错误：它会修改另一张表。

## 来自另一张表的值

通过关联路径读取值。lambdaQL 把它输出为**关联标量子查询**（规则 U9）。

```kotlin
qb.update(Order::class.java).set { it.custName = it.customer?.name }.all().execute()
```

```sql
update Order o
set o.custName = (select c.name from Customer c where c.id = o.customer.id)
```

**各数据库的 SQL（计划中）** — Hibernate 对计划中 HQL 的翻译，由 `PlannedSqlTest` 记录；lambdaQL 目前还不会生成此查询。来自 `expected-sql/` 的 [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)，示例 `07-update-other-table`：

```sql
-- H2, MariaDB
update orders o1_0
set cust_name=(select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id)

-- Oracle
update orders o1_0
set o1_0.cust_name=(select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id)
```

如果订单没有客户，`custName` 会变成 NULL。

## 来自子查询的聚合

使用两个参数的 Lambda。第二个参数是上下文对象（`sub`）。
这里不需要 `Agg`；子查询有 `sum`、`avg` 等，与 [5. 子查询](05-subquery.md) 中相同。

```kotlin
qb.update(Order::class.java)
    .set { o, sub ->
        o.total = sub.from(Item::class.java).where { i -> i.order?.id == o.id }.sum { i -> i.price }
    }
    .where { it.status == 1 }
    .execute()
```

如果订单没有订单项，`total` 会变成 NULL。这是 SQL 的行为。

一个参数的尾随 Lambda（`set { it... }`）和两个参数的尾随 Lambda（`set { o, sub -> ... }`）是不同的重载。Kotlin 会选择正确的那一个。

## 带路径和子查询的 where

`where` 与查询中的相同：

```kotlin
qb.update(Order::class.java).set { it.status = 0 }.where { it.customer?.grade!! > 3 }.execute()

qb.update(Order::class.java).set { it.status = 9 }
    .where { o, sub -> !sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
    .execute()
```

## 乐观锁（`@Version`）

如果实体有 `@Version` 属性，lambdaQL 会输出 `update versioned`。数据库会把版本加 1。

```kotlin
val n = qb.update(VOrder::class.java)
    .set { it.status = 8 }
    .where { it.id == id && it.version == v }
    .execute()
if (n == 0) throw OptimisticLockException()   // 检查结果是你的工作
```

不要自己给 `@Version` 属性赋值。那是错误（规则 U15）。

## 多次调用 `set()`（PATCH）

你可以多次调用 `set()`。所有赋值会按调用顺序合并为一个 SET 列表（规则 U14）。

```kotlin
var u = qb.update(Order::class.java).set { it.status = dto.status }
if (dto.description != null) {
    u = u.set { it.description = dto.description }
}
u.where { it.id == id }.execute()
```

## 更新所有行：`all()`

没有 `where` 的更新必须写 `all()`。既没有 `where` 也没有 `all()` 时，代码**无法编译**（规则 U5）。

```kotlin
qb.update(Order::class.java).set { it.status = 0 }.all().execute()   // 可以
qb.update(Order::class.java).set { it.status = 0 }.execute()         // 编译错误：没有 execute()
```

---

---

[7. 更新、插入、删除（Kotlin）](07-dml.md) | **7.2 更新** | [7.4 删除](07-4-delete.md) | [7.5 插入](07-5-insert.md) | [Java](../java/07-2-update.md)
