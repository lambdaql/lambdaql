<a id="72-update"></a>
# 7.2 更新（Java）

> [!NOTE]
> **状态：已设计，尚未实现。**
> 本章是已确定的设计（决定 D28，里程碑 M10）。
> 该语法在 Java 中可以编译，SQL 已在 H2、MariaDB 和 Oracle 上使用 Hibernate 6.6.5 检查过。
> 目前它在 lambdaQL 中**还不能**运行。目前只有分析核心以及 `where` + `toList()` 可以工作
> （已在 H2、MariaDB 和 Oracle 上测试）。完整状态见 [Java 路线 README](README.md#status)。

[7. 更新、插入、删除（Java）](07-dml.md) | **7.2 更新** | [7.4 删除](07-4-delete.md) | [7.5 插入](07-5-insert.md) | [Kotlin](../kotlin/07-2-update.md)

---


## 基本形式

```java
int n = qb.update(Order.class)
    .set(o -> { o.setStatus(st); o.setPrice(o.getPrice() * 1.1); })
    .where(o -> o.getStatus() == 1)
    .execute();                    // 更新的行数
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

`st` 是一个捕获的变量。lambdaQL 把它作为绑定参数发送。

## 值：null、日期、布尔

```java
qb.update(Order.class)
  .set(o -> { o.setDescription(null); o.setUpdateAt(now); o.setActive(true); })
  .where(o -> o.getId() == id)
  .execute();
```

枚举值也以同样的方式工作。每个捕获值都有**自己的参数**，即使同一个值使用了两次（规则 U7）。

## 在原值上累加，以及 NULL 的默认值

```java
qb.update(Order.class).set(o -> o.setCnt(o.getCnt() + 1)).where(o -> o.getStatus() == 1).execute();

qb.update(Order.class)
  .set(o -> o.setPrice(Objects.requireNonNullElse(o.getPrice(), 0.0) + 1))
  .all()
  .execute();
// → set o.price = coalesce(o.price, 0.0) + 1
```

**各数据库的 SQL（计划中）** — Hibernate 对计划中 HQL 的翻译，由 `PlannedSqlTest` 记录；lambdaQL 目前还不会生成此查询。来自 `expected-sql/` 的 [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)，示例 `07-update-coalesce`：

```sql
-- H2, MariaDB
update orders o1_0
set price=(coalesce(o1_0.price,0.0)+1)

-- Oracle
update orders o1_0
set o1_0.price=(coalesce(o1_0.price,0.0)+1)
```

## 嵌入字段

```java
qb.update(Order.class).set(o -> o.getAddress().setCity(city)).where(o -> o.getId() == id).execute();
// → set o.address.city = :p1

qb.update(Order.class).set(o -> o.setAddress(addr)).where(o -> o.getId() == id).execute();
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

```java
qb.update(Order.class).set(o -> o.getCustomer().setId(cid)).where(o -> o.getId() == id).execute();
// → set o.customer.id = :p1      （外键列）

qb.update(Order.class).set(o -> o.setCustomer(null)).where(o -> o.getId() == id).execute();
// → set o.customer = null

qb.update(Order.class).set(o -> o.setCustomer(customer)).where(o -> o.getId() == id).execute();
// → 绑定所捕获 customer 的 @Id（规则 U11）
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

对关联实体，只能设置它的 `@Id`（外键）。
`o.getCustomer().setName(x)` 是错误：它会修改另一张表。

## 来自另一张表的值

通过关联路径读取值。lambdaQL 把它渲染为**关联标量子查询**（规则 U9）。

```java
qb.update(Order.class).set(o -> o.setCustName(o.getCustomer().getName())).all().execute();
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
这里不需要 `Agg`；子查询本身有 `sum`、`avg` 等方法，与 [5. 子查询](05-subquery.md)相同。

```java
qb.update(Order.class)
  .set((o, sub) -> o.setTotal(
      sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).sum(i -> i.getPrice())))
  .where(o -> o.getStatus() == 1)
  .execute();
```

如果订单没有明细，`total` 会变成 NULL。这是 SQL 的行为。

## 使用路径和子查询的 where

`where` 与查询中的相同：

```java
qb.update(Order.class).set(o -> o.setStatus(0)).where(o -> o.getCustomer().getGrade() > 3).execute();

qb.update(Order.class).set(o -> o.setStatus(9))
  .where((o, sub) -> !sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).exists())
  .execute();
```

## 乐观锁（`@Version`）

如果实体有 `@Version` 字段，lambdaQL 会渲染 `update versioned`。数据库会把版本号加 1。

```java
int n = qb.update(VOrder.class)
    .set(o -> o.setStatus(8))
    .where(o -> o.getId() == id && o.getVersion() == v)
    .execute();
if (n == 0) throw new OptimisticLockException();   // 检查结果是你的工作
```

不要自己给 `@Version` 字段赋值。那是错误（规则 U15）。

## 多次调用 `set()`（PATCH）

`set()` 可以调用多次。所有赋值按调用顺序合并成一个 SET 列表（规则 U14）。

```java
var u = qb.update(Order.class).set(o -> o.setStatus(dto.status()));
if (dto.description() != null) {
    u = u.set(o -> o.setDescription(dto.description()));
}
u.where(o -> o.getId() == id).execute();
```

## 更新所有行：`all()`

没有 `where` 的更新必须写 `all()`。既没有 `where` 也没有 `all()` 时，代码**无法编译**（规则 U5）。

```java
qb.update(Order.class).set(o -> o.setStatus(0)).all().execute();       // 可以
qb.update(Order.class).set(o -> o.setStatus(0)).execute();             // 编译错误：没有 execute()
```

---

---

[7. 更新、插入、删除（Java）](07-dml.md) | **7.2 更新** | [7.4 删除](07-4-delete.md) | [7.5 插入](07-5-insert.md) | [Kotlin](../kotlin/07-2-update.md)
