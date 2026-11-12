# 8. 存储过程与数据库函数（Java）

> [!NOTE]
> **状态：已设计，尚未实现。**
> 本章是已确定的设计（决定 D30 和 D31，里程碑 M11）。
> 该语法在 Java 中可以编译，调用已在 H2、MariaDB 和 Oracle 23ai 上使用 Hibernate 6.6.5 检查过。
> 目前它在 lambdaQL 中**还不能**运行。目前只有分析核心以及 `where` + `toList()` 可以工作
> （已在 H2、MariaDB 和 Oracle 上测试）。完整状态见 [Java 路线 README](README.md#status)。

[Java 路线](README.md) | [手册首页](../README.md) | 上一章：[7. 更新、插入、删除](07-dml.md) | 下一章：[9. Java 说明](09-java-notes.md) | [本页的 Kotlin 版本](../kotlin/08-procedures-functions.md)

公共页面：[1. 概述](../01-overview.md) · [11. 错误与语言](../11-errors-and-languages.md)

---

本章分为两部分：

| 部分 | 内容 | 注解 | 调用方式 |
|---|---|---|---|
| [8.1 至 8.5](#81-declare-procedures) | **存储过程** | 抽象接口方法上的 `@SqlProcedure` | `qb.call(OrderProcs.class, p -> p.calc(id, rate)).execute()` |
| [8.6 至 8.10](#86-declare-db-functions) | **数据库函数**（标量） | 静态接口方法上的 `@SqlFunction` | 直接在任何 Lambda 中调用：`where(o -> OrderFns.discount(o.getPrice()) > 10)` |

两种情况下，你都只需在 Java 接口中写**一次**签名。
lambdaQL **从不执行**这些方法。它读取 Lambda 字节码中的调用，然后调用数据库。

## 目录

- [8.1 声明存储过程](#81-declare-procedures)
- [8.2 调用存储过程](#82-call-procedures)
- [8.3 OUT、INOUT 和结果集](#83-out-inout-and-result-sets)
- [8.4 警告：参数顺序](#84-warning-argument-order)
- [8.5 存储过程规则 PR1 至 PR15（汇总）](#85-procedure-rules-pr1-to-pr15-summary)
- [8.6 声明数据库函数](#86-declare-db-functions)
- [8.7 在查询中使用数据库函数](#87-use-db-functions-in-queries)
- [8.8 单独调用数据库函数](#88-call-a-db-function-alone)
- [8.9 警告：列上的函数与索引（SARGable）](#89-warning-functions-on-columns-and-indexes-sargable)
- [8.10 函数规则 FN1 至 FN11（汇总）](#810-function-rules-fn1-to-fn11-summary)
- [8.11 数据库支持情况](#811-database-support)

---

<a id="81-declare-procedures"></a>
## 8.1 声明存储过程

编写一个接口。在每个**抽象**方法上加上 `@SqlProcedure("NAME")`。
你不需要编写实现。lambdaQL 也不会生成实现。

```java
interface OrderProcs {
    @SqlProcedure("SQ_CALC")     Double calc(Long id, Double rate);              // 1 个 OUT → 返回值
    @SqlProcedure("SQ_CALC2")    CalcOut calc2(Long id, Double rate);            // 多个 OUT → record
    @SqlProcedure("SP_ADJUST")   AdjOut adjust(Long id, @InOut Integer balance); // INOUT
    @SqlProcedure("SP_RECENT")   List<OrderView> recent(Long custId);           // 结果集 / Oracle REF CURSOR
    @SqlProcedure("SQ_PKG.CALC") Double pkgCalc(Long id, Double rate);           // 包名或模式名
    @SqlProcedure("SP_PURGE")    void purge(Integer days);                       // 没有结果
}

record CalcOut(Double total, Integer cnt) {}
record AdjOut(Integer balance, String msg) {}
```

说明：

- `@SqlProcedure`（或 `@SqlFunction`）是**必需的**。它告诉 lambdaQL 这个方法是什么。
  （没有使用 `@Procedure` 和 `@Function` 这两个名字：`@Procedure` 也是 Spring Data JPA 的注解，而 `@Function` 与 Kotlin 的 `kotlin.Function` 冲突。）
- 如果值为空，名字取自方法名：`calcTotal` → `CALC_TOTAL`。
- 请使用装箱的返回类型（`Double`、`Integer`）。存储过程可能返回 NULL。
- 接口和结果 record 是数据库签名的副本。这是类型安全的代价。lambdaQL 会对照数据库检查其中一部分（规则 PR2）。
- 存储过程（抽象方法）和数据库函数（静态方法）可以放在**同一个接口**中。

---

<a id="82-call-procedures"></a>
## 8.2 调用存储过程

使用 `qb.call(Interface.class, p -> p.method(args))`，然后调用 `execute()`。

```java
Double d          = qb.call(OrderProcs.class, p -> p.calc(id, rate)).execute();
CalcOut co        = qb.call(OrderProcs.class, p -> p.calc2(id, rate)).execute();
AdjOut a          = qb.call(OrderProcs.class, p -> p.adjust(id, balance)).execute();
List<OrderView> r = qb.call(OrderProcs.class, p -> p.recent(custId)).execute();

// 没有结果（void）：使用 exec
qb.exec(OrderProcs.class, p -> p.purge(30)).execute();
```

为什么 void 用 `exec` 而不是 `call`？

- 在 Java 中，两者都用 `call` 会成为有歧义的重载（编译错误）。
- 没有选择 `run`：Kotlin 标准库中有同名函数，Kotlin 调用方可能会在没有任何错误的情况下误调用它。

### Lambda 中可以包含什么（规则 PR12）

Lambda 体是**对存根方法的一次调用**。除此之外什么都没有。

| 允许 | 不允许 |
|---|---|
| `p -> p.calc(id, rate)` | 日志、两次调用、`if`、嵌套调用 |
| 参数：捕获值、常量以及它们的计算（`rate * 2`） | 实体路径（这里没有实体） |

其他要点：

- 对带有 OUT 参数的存储过程使用 `exec` 是错误。这样可以防止 OUT 值被意外丢弃。
- 不能使用数据库中参数的默认值。请传入每个 IN 参数。
- 用 `null` 作为 IN 值没有问题。

---

<a id="83-out-inout-and-result-sets"></a>
## 8.3 OUT、INOUT 和结果集

返回类型与存储过程的对应关系（规则 PR3、PR4、PR5）：

| 存储过程包含 | 存根方法的返回类型 |
|---|---|
| 什么都没有 | `void` → 用 `qb.exec(...)` 调用 |
| 1 个 OUT 参数 | 该值（`Double`、`Integer` 等） |
| 多个 OUT / INOUT 参数 | Java record |
| 1 个结果集（MariaDB 的 `SELECT`、Oracle 的 `SYS_REFCURSOR` OUT、H2 的 `ResultSet` 函数） | `List<T>` |
| 多个结果集 | 带 `List` 组件的 record |
| OUT 值和结果集（MariaDB） | 带标量组件和 `List` 组件的 record |

record 组件的填充方式：

- **标量组件** ← OUT 和 INOUT 参数（游标除外），按数据库中的顺序。
- **`List` 组件** ← 结果集，按顺序（Oracle：游标 OUT 参数的顺序；MariaDB：返回结果集的顺序）。
- 如果数量不匹配，lambdaQL 会抛出错误。
- 像 IN、OUT、IN、INOUT 这样混合的位置没有问题。lambdaQL 从数据库元数据中读取位置。

### INOUT

用 `@InOut` 标记参数。你传入的值**进入**存储过程。**出来**的值是返回 record 的一个组件。

```java
@SqlProcedure("SP_ADJUST") AdjOut adjust(Long id, @InOut Integer balance);

AdjOut a = qb.call(OrderProcs.class, p -> p.adjust(id, 100)).execute();
// a.balance() 是新的余额
```

### 结果集的元素类型 `T`

| `T` 是 | lambdaQL 的读取方式 |
|---|---|
| 实体 | Hibernate 结果类 |
| record 或 DTO | JDBC `CallableStatement`，**按标签**匹配列（`camelCase` ↔ `snake_case`，忽略大小写） |

lambdaQL 从不按位置映射列，因为 MariaDB 和 Oracle 返回 `SELECT *` 的列顺序不同。
它会转换常见类型（例如把数字转换为 `Long`/`Integer`，把时间戳转换为 `LocalDateTime`）。

限制：

- 无法对存储过程的结果分页。
- 不支持 Oracle 的隐式结果（`DBMS_SQL.RETURN_RESULT`）。请使用 `SYS_REFCURSOR` OUT 参数。

### 事务（规则 PR9）

- 存储过程中的 DML 是调用方事务的一部分，会随之回滚。
- 调用之前，lambdaQL 会刷新持久化上下文，但仅在有活动事务且存在更改时才这样做。
- 在事务之外，调用是自动提交的。
- 调用之后，已加载的实体可能是过期的。请对这些实体使用 `refresh` 或 `evict`。
- 存储过程中的 `COMMIT` 或 DDL 会破坏事务。lambdaQL 无法阻止这种情况。

### 受影响的行数（规则 PR10）

存储过程没有通用的“受影响行数”结果（Oracle 返回 -1）。
如果需要这个数字，请把它作为 OUT 参数返回。

---

<a id="84-warning-argument-order"></a>
## 8.4 警告：参数顺序

> [!WARNING]
> **存根方法参数的顺序必须与数据库中的顺序相同。**

lambdaQL **按位置**绑定参数（规则 PR2）。
即使注册了名字，Hibernate 6.6.5 也按位置绑定。
MariaDB 和 H2 的驱动不支持命名参数。
所以如果顺序错误，你可能会得到**错误的结果却没有任何报错**。

lambdaQL 在第一次调用时检查的内容（来自 JDBC 元数据）：

| 检查项 | 能发现吗？ |
|---|---|
| 参数个数 | 能，报错 |
| 模式（IN / OUT / INOUT / 游标） | 能，报错 |
| 类型 | 能，报错 |
| 两个**相同类型**的参数互换 | **仅当参数名可用时** |

危险情况的示例：

```java
// 数据库：SP_TRANSFER(p_to NUMBER, p_from NUMBER)
@SqlProcedure("SP_TRANSFER") void transfer(Long from, Long to);   // 顺序反了！类型相同
```

个数、模式和类型都匹配。只有名字才能暴露这个错误。
当 lambdaQL 能获取名字时会比较它们：用 javac `-parameters` 编译，或使用 `@Param`。
它会忽略 `p_` 前缀和大小写。如果名字不可用，并且两个相邻参数类型相同，lambdaQL 会记录一条 WARN。

**建议：** 用 `-parameters` 编译（见 [2. 入门](02-getting-started.md#recommended-compiler-option--parameters)），并使用与数据库相同的名字。

在 Oracle 上，lambdaQL 还可以使用真正的按名字绑定（`pass_procedure_paramater_names=true` 加上存根的参数名）。

---

<a id="85-procedure-rules-pr1-to-pr15-summary"></a>
## 8.5 存储过程规则 PR1 至 PR15（汇总）

| # | 规则 |
|---|---|
| PR1 | 名字：注解的值原样使用。值为空时：方法名转为 UPPER_SNAKE。可以使用包名或模式名（`SQ_PKG.CALC`、`LQL.SQ_CALC`）。为避免混淆，也可以写成 `@SqlProcedure(schema = ..., pkg = ..., value = ...)`。 |
| PR2 | 参数**按位置**绑定。第一次调用时，会对照数据库元数据检查个数、模式和类型。相同类型参数的互换只能通过参数名发现（见 [8.4](#84-warning-argument-order)）。包中的 Oracle 重载在 v0.1 中是错误。 |
| PR3 | 一个 OUT → 返回值。多个 OUT/INOUT → record。`@InOut` 标记 INOUT 参数。 |
| PR4 | 一个结果集 → `List<T>`。实体通过 Hibernate；record/DTO 通过 JDBC 并按标签匹配。不能分页。 |
| PR5 | 多个结果集 → record 的 `List` 组件，按顺序。拒绝 Oracle 隐式结果。 |
| PR6 | 单独调用的数据库函数在不同数据库上使用不同的路径（见 [8.8](#88-call-a-db-function-alone)）。 |
| PR7 | 查询中的数据库函数：见 [8.6](#86-declare-db-functions) 和规则 FN1 至 FN11。 |
| PR8 | 条件中作用于列的数据库函数会产生 WARN，因为无法使用索引（见 [8.9](#89-warning-functions-on-columns-and-indexes-sargable)）。 |
| PR9 | 存储过程在调用方的事务中运行。需要时 lambdaQL 会在调用前刷新。调用后已加载的实体可能是过期的。 |
| PR10 | 没有通用的受影响行数。请使用 OUT 参数。 |
| PR11 | IN 值为 `null` 没有问题。不能使用数据库参数的默认值。 |
| PR12 | Lambda 是一次存根调用。参数是捕获值、常量或它们的计算。带 OUT 参数时使用 `exec` 是错误。 |
| PR13 | 布尔参数在 MariaDB 和 Oracle 23ai 及以上版本中可用。 |
| PR14 | 每个 Lambda 只分析一次。每个名字的存储过程元数据只读取一次。 |
| PR15 | H2 没有 OUT、INOUT、多结果集或包。请在 MariaDB 或 Oracle 上测试存储过程（例如使用 Testcontainers）。 |

PR11、PR12 和 PR13 有额外的 Kotlin 部分（默认参数、命名参数、可空类型）。它们在[本页的 Kotlin 版本](../kotlin/08-procedures-functions.md)中。

---

<a id="86-declare-db-functions"></a>
## 8.6 声明数据库函数

数据库函数是你在数据库中创建的函数，例如 `FN_DISCOUNT(price)`。
把它声明为带有 `@SqlFunction` 的**接口静态方法**。
方法体只能抛出异常。它在 lambdaQL 中从不执行。

```java
public interface OrderFns {
    @SqlFunction("FN_DISCOUNT") static Double discount(Double price) { throw new DbOnlyException(); }
    @SqlFunction("FN_IS_VIP")   static Boolean isVip(Long customerId) { throw new DbOnlyException(); }  // Boolean：Oracle 23ai 及以上
}
```

如果在 lambdaQL 的 Lambda **外部**调用 `OrderFns.discount(...)`，它会抛出异常。这是有意为之。

其他声明方式：

| 声明 | 状态 |
|---|---|
| 接口静态方法 | **推荐** |
| 类静态方法 | 支持 |
| 接口 default 方法 + 单例常量 | 支持，但写起来更长 |
| 你的仓库 `implements` 函数接口（mixin） | 不支持 |
| 函数式接口常量（`DISCOUNT.apply(x)`） | 不支持 |

请**带上接口名**调用函数（`OrderFns.discount`）。静态导入也可以，但同名的成员方法可能会遮蔽它。

---

<a id="87-use-db-functions-in-queries"></a>
## 8.7 在查询中使用数据库函数

直接调用函数即可。不需要上下文参数。它在每种 Lambda 中都可用：
`where`、`select`、`orderBy`、`groupBy`、`having`、`set`，以及子查询内部。

```java
qb.from(Order.class).where(o -> OrderFns.discount(o.getPrice()) > 10).toList();
// → where function('FN_DISCOUNT' as Double, o.price) > 10

qb.from(Order.class).where(o -> OrderFns.isVip(o.getCustomer().getId())).toList();   // 单独使用 Boolean 函数

qb.from(Order.class).where(o -> o.getPrice() > OrderFns.discount(base)).toList();     // 列这一侧保持干净

qb.from(Order.class).orderBy((o, by) -> by.desc(OrderFns.discount(o.getPrice()))).toList();

qb.from(Order.class).select(o -> new OrderView(o.getId(), o.getCustomer().getName(), OrderFns.discount(o.getPrice()))).toList();

qb.update(Order.class).set(o -> o.setPrice(OrderFns.discount(o.getPrice()))).all().execute();
```

**关于 SQL 块。** `…` 是缩短的 select 列表（实体的所有列），`?` 是绑定参数。[`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java) 把本页的 HQL 直接交给 Hibernate，并记录在 H2 2.3、MariaDB 10.11 和 Oracle 23.26（Hibernate 6.6.5）上得到的 SQL。lambdaQL 目前还不会生成这些查询。如果 Hibernate 的 SQL 发生变化，测试就会失败。

**各数据库的 SQL（计划中）** — Hibernate 对计划中 HQL 的翻译，由 `PlannedSqlTest` 记录；lambdaQL 目前还不会生成此查询。来自 `expected-sql/` 的 [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)，示例 `08-function`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where fn_discount(o1_0.price)>10
```

除非注册了函数，否则函数名会变成小写（`fn_discount`）。见下面的“注册”。

### lambdaQL 的渲染方式

lambdaQL **总是**把数据库函数渲染为：

```sql
function('<NAME>' as <Type>, arg1, arg2, ...)
```

`<Type>` 取自声明的返回类型。只允许以下类型：

| 允许的返回类型 |
|---|
| `Double`、`Integer`、`Long`、`BigDecimal`、`String`、`LocalDate`、`LocalDateTime`、`Boolean` |

泛型返回（`<X> X nvl(X, X)`）、实体、集合和 `void` 会在分析时被拒绝。

### 注册（可选）

`function('FN_DISCOUNT' as Double, ...)` 这种形式不需要注册（已在三种数据库上检查）。
如果注册了函数接口，Hibernate 还会保留名字的大小写，并检查参数个数和类型。

lambdaQL 计划提供一个 `FunctionContributor`（通过 ServiceLoader 发现）。你在 Hibernate 属性中列出这些接口：

```properties
# 纯 Hibernate
lambdaql.functions.classes=com.acme.OrderFns,com.acme.CustomerFns

# Spring Boot
spring.jpa.properties.lambdaql.functions.classes=com.acme.OrderFns
```

如果没有设置该属性，就不会注册任何东西。v0.1 中不支持包扫描。

### 大小写混合的名字需要注册

用引号创建的大小写混合的函数名（`"SQ_Fn_Mixed"`）在不注册时会失败（在 H2 和 Oracle 上名字会变成小写）。
请把键和 SQL 名分开，并注册该接口：

```java
@SqlFunction(key = "SQ_Fn_Mixed", sql = "\"SQ_Fn_Mixed\"")
static Double mixed(Double v) { throw new DbOnlyException(); }
```

如果这样的函数没有注册，lambdaQL 会在第一次使用时失败。

---

<a id="88-call-a-db-function-alone"></a>
## 8.8 单独调用数据库函数

使用带有**无参数 Lambda** 的 `qb.call(() -> ...)`：

```java
Double v = qb.call(() -> OrderFns.discount(price)).execute();
```

**各数据库的 SQL（计划中）** — Hibernate 对计划中 HQL 的翻译，由 `PlannedSqlTest` 记录；lambdaQL 目前还不会生成此查询。来自 `expected-sql/` 的 [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)，示例 `08-function-alone`：

```sql
-- H2, MariaDB, Oracle (same)
select fn_discount(?)
```

它作为没有 FROM 的 `select fn_discount(?)` 运行。Oracle 从 23ai 起才接受这种写法；在 Oracle 上，lambdaQL 改用 JDBC 函数调用（见下表）。

它与 `qb.call(OrderProcs.class, p -> ...)` 不冲突，因为参数个数不同。

lambdaQL 的执行方式（规则 PR6）：

| 数据库 | 路径 |
|---|---|
| Oracle | JDBC 函数调用 `{? = call FN(?)}`。这也适用于执行 DML 的函数和返回游标的函数。 |
| MariaDB、H2 | HQL `select function('FN' as T, :p1)` |

lambdaQL 会先刷新待处理的更改（与 PR9 相同），所以函数能看到你最新的更改。

---

<a id="89-warning-functions-on-columns-and-indexes-sargable"></a>
## 8.9 警告：列上的函数与索引（SARGable）

lambdaQL 尽量生成**能够使用索引**的 SQL（见 [10. 规则与限制](10-rules-and-limits.md#102-sargable-rule-index-friendly-sql)）。
包在列外面的函数通常**无法**使用普通索引：

```java
.where(o -> OrderFns.discount(o.getPrice()) > 10)
// → function('FN_DISCOUNT' as Double, o.price) > 10   -- 不使用 price 上的索引
```

**各数据库的 SQL（计划中）** — Hibernate 对计划中 HQL 的翻译，由 `PlannedSqlTest` 记录；lambdaQL 目前还不会生成此查询。来自 `expected-sql/` 的 [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)，示例 `08-function`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where fn_discount(o1_0.price)>10
```

所以 lambdaQL 会对这种形式记录一条 **WARN**。在严格模式下，它是错误（规则 PR8、FN7）。

| 情况 | MariaDB | Oracle |
|---|---|---|
| 作用于列的函数：`fn(o.price) > 10` | WARN（存储函数不能用于索引） | WARN，**除非**函数是 `DETERMINISTIC` **并且**存在针对同一表达式的函数索引 |
| 只作用于值的函数：`o.price > fn(rate)` | 如果函数是 `NOT DETERMINISTIC`（这是默认值！）则 WARN。此时 MariaDB 会扫描整个索引。 | 没有警告（索引范围扫描） |

**建议：**

- 尽可能把函数放在值这一侧：`o.getPrice() > OrderFns.discount(base)`。
- 在 MariaDB 上，如果函数确实是确定性的，请将它声明为 `DETERMINISTIC`。
- 在 Oracle 上，`DETERMINISTIC` 函数加上函数索引可以消除警告。

---

<a id="810-function-rules-fn1-to-fn11-summary"></a>
## 8.10 函数规则 FN1 至 FN11（汇总）

| # | 规则 |
|---|---|
| FN1 | `@SqlFunction` 是方法注解。值为空时：方法名转为 UPPER_SNAKE。 |
| FN2 | lambdaQL 找到被调用的方法并读取它的注解。方法体只能抛出异常（`throw new ...`）。有实际方法体的方法是错误，因为 Java 和数据库可能给出不同的值。 |
| FN3 | 同名的所有重载必须映射到同一个数据库函数。否则报错。 |
| FN4 | 没有注解的调用，或 lambdaQL 无法识别的调用，是错误。它从不会被悄悄跳过。 |
| FN5 | 参数遵循 [3. 条件](03-where.md)中的值规则。即使参数只由捕获值构成，也会被绑定并**在数据库中**求值。 |
| FN6 | 装箱调用会被跳过。返回类型取自声明的方法。 |
| FN7 | 作用于列的函数会产生 WARN（见 [8.9](#89-warning-functions-on-columns-and-indexes-sargable)）。 |
| FN8 | 与 Hibernate 内置函数（`COALESCE`、`ROUND`、`SYSDATE` 等）同名是错误。Hibernate 会悄悄使用内置函数。请使用前缀或模式名（`PUBLIC.SQ_FN`）。 |
| FN9 | 仅限 Kotlin（默认参数、`inline`、`vararg`）。见 Kotlin 路线。 |
| FN10 | v0.1 只支持标量函数。用户自定义聚合函数和窗口函数计划在 v0.2 中提供。没有参数的函数渲染为 `fn()`。 |
| FN11 | 已知的数据库差异：在 MariaDB 上，返回 VARCHAR 的函数可能与字符串字面量发生排序规则（collation）冲突。`BigDecimal` 结果的小数位数不同（MariaDB 和 Oracle 为 0，H2 为 1）。布尔结果需要 Oracle 23ai 及以上版本。 |

---

<a id="811-database-support"></a>
## 8.11 数据库支持情况

| 功能 | H2 2.3 | MariaDB 10.11 | Oracle 23ai |
|---|---|---|---|
| IN 参数 | 支持 | 支持 | 支持 |
| OUT / INOUT | **不支持**（H2 的限制） | 支持 | 支持 |
| 一个结果集 | 支持（`ResultSet` 函数） | 支持（存储过程中的 `SELECT`） | 支持（`SYS_REFCURSOR` OUT） |
| 多个结果集 | 不支持 | 支持 | 支持（多个游标 OUT 参数） |
| 同时有 OUT 值和结果集 | 不支持 | 支持 | 支持 |
| 包名（`PKG.PROC`） | 不支持 | 不支持（需要 `sql_mode=ORACLE`） | 支持 |
| 查询中的数据库函数 | 支持 | 支持 | 支持 |
| 布尔参数 / 结果 | – | 支持 | 支持（23ai 及以上） |

v0.1 中未计划的内容：用于动态存储过程名的字符串构建器（v0.2 候选）、Oracle 包重载、表值函数、存储过程结果的分页。

本章的错误是带有 `[LQL-xxxx]` 代码的 `LambdaQLException` 子类。见 [11. 错误与语言](../11-errors-and-languages.md)。

---

[Java 路线](README.md) | 上一章：[7. 更新、插入、删除](07-dml.md) | 下一章：[9. Java 说明](09-java-notes.md) | [本页的 Kotlin 版本](../kotlin/08-procedures-functions.md)
