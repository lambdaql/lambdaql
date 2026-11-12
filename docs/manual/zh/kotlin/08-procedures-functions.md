# 8. 存储过程与数据库函数（Kotlin）

> [!NOTE]
> **状态：已设计，尚未实现。**
> 本章是已经确定的设计（决策 D30 和 D31，里程碑 M11）。
> 这些 Kotlin 写法可以编译（kotlinc 2.3.21），调用已在 H2、MariaDB 和 Oracle 23ai 上使用 Hibernate 6.6.5 检查过。
> 目前它在 lambdaQL 中**还不能**运行。目前只有分析核心以及 `where` + `toList()` 可以工作
> （已在 H2、MariaDB 和 Oracle 上测试）。完整状态请参阅 [路线 README](README.md)。

[Kotlin 路线](README.md) | [手册首页](../README.md) | 上一章：[7. 更新、插入、删除](07-dml.md) | 下一章：[9. Kotlin 说明](09-kotlin-notes.md) | [本页的 Java 版本](../java/08-procedures-functions.md)

公共页面：[1. 概述](../01-overview.md) · [11. 错误与语言](../11-errors-and-languages.md)

本章分为两部分：

| 部分 | 内容 | 注解 | 调用方式 |
|---|---|---|---|
| [8.1 到 8.5](#81-declare-procedures) | **存储过程** | 加在抽象接口函数上的 `@SqlProcedure` | `qb.call(OrderProcs::class.java) { it.calc(id, rate) }.execute()` |
| [8.6 到 8.10](#86-declare-db-functions) | **数据库函数**（标量） | 加在接口 `companion object` 中 `@JvmStatic` 函数上的 `@SqlFunction` | 直接在任何 Lambda 中使用：`where { OrderFns.discount(it.price)!! > 10 }` |

两种情况下，你都只需在 Kotlin 接口中把签名写**一次**。
lambdaQL **从不执行**这些函数。它读取 Lambda 字节码中的调用，然后调用数据库。

## 目录

- [8.1 声明存储过程](#81-declare-procedures)
- [8.2 调用存储过程](#82-call-procedures)
- [8.3 OUT、INOUT 和结果集](#83-out-inout-and-result-sets)
- [8.4 警告：参数顺序](#84-warning-argument-order)
- [8.5 存储过程规则 PR1 到 PR15（摘要）](#85-procedure-rules-pr1-to-pr15-summary)
- [8.6 声明数据库函数](#86-declare-db-functions)
- [8.7 在查询中使用数据库函数](#87-use-db-functions-in-queries)
- [8.8 单独调用数据库函数](#88-call-a-db-function-alone)
- [8.9 警告：列上的函数与索引（SARGable）](#89-warning-functions-on-columns-and-indexes-sargable)
- [8.10 函数规则 FN1 到 FN11（摘要）](#810-function-rules-fn1-to-fn11-summary)
- [8.11 数据库支持](#811-database-support)

---

<a id="81-declare-procedures"></a>
## 8.1 声明存储过程

编写一个接口。在每个**抽象**函数上加上 `@SqlProcedure("NAME")`。
你不需要编写实现。lambdaQL 也不会生成实现。

```kotlin
interface OrderProcs {
    @SqlProcedure("SQ_CALC")     fun calc(id: Long, rate: Double): Double?               // 1 个 OUT → 返回值
    @SqlProcedure("SQ_CALC2")    fun calc2(id: Long, rate: Double): CalcOut              // 多个 OUT → data class
    @SqlProcedure("SP_ADJUST")   fun adjust(id: Long, @InOut balance: Int?): AdjOut      // INOUT
    @SqlProcedure("SP_RECENT")   fun recent(custId: Long): List<OrderView>               // 结果集 / Oracle REF CURSOR
    @SqlProcedure("SQ_PKG.CALC") fun pkgCalc(id: Long, rate: Double): Double?            // 包名或 schema 名
    @SqlProcedure("SP_PURGE")    fun purge(days: Int)                                    // 没有结果
}

data class CalcOut(val total: Double?, val cnt: Int?)
data class AdjOut(val balance: Int?, val msg: String?)
```

说明：

- `@SqlProcedure`（或 `@SqlFunction`）是**必需**的。它告诉 lambdaQL 这个函数是什么。
  （没有使用 `@Procedure` 和 `@Function` 这两个名字：`@Procedure` 也是 Spring Data JPA 的注解，而 `@Function` 与 Kotlin 的 `kotlin.Function` 冲突。）
- 如果值为空，名字取自函数名：`calcTotal` → `CALC_TOTAL`。
- 请使用**可空返回类型**（`Double?`）。如果存储过程向非空类型返回 NULL，lambdaQL 会抛出一个具名异常。
- 接口和结果 data class 是数据库签名的副本。这是类型安全的代价。lambdaQL 会对照数据库检查其中一部分（规则 PR2）。
- 你可以把存储过程（抽象函数）和数据库函数（在 `companion object` 中）放在**同一个接口**里。

---

<a id="82-call-procedures"></a>
## 8.2 调用存储过程

使用 `qb.call(Interface::class.java) { it.function(args) }`，然后调用 `execute()`。

```kotlin
val d: Double?           = qb.call(OrderProcs::class.java) { it.calc(id, rate) }.execute()
val co: CalcOut          = qb.call(OrderProcs::class.java) { it.calc2(id, rate) }.execute()
val a: AdjOut            = qb.call(OrderProcs::class.java) { it.adjust(id, balance) }.execute()
val r: List<OrderView>   = qb.call(OrderProcs::class.java) { it.recent(custId) }.execute()

// 使用简单值的命名参数
val d2 = qb.call(OrderProcs::class.java) { it.calc(rate = rate, id = id) }.execute()

// 没有结果：使用 exec
qb.exec(OrderProcs::class.java) { it.purge(30) }.execute()
```

为什么没有结果的函数使用 `exec` 而不是 `call`？

- 在 Java 中，如果两者都叫 `call`，会成为有歧义的重载（编译错误）。两种语言使用同一套 API。
- 没有选择 `run`：在 Kotlin 中，如果你忘了一个参数，`run { }` 会悄悄变成标准库的 `run`。

### Lambda 中可以包含什么（规则 PR12）

Lambda 的主体是**对一个桩函数的一次调用**，除此之外什么都没有。

| 允许 | 不允许 |
|---|---|
| `{ it.calc(id, rate) }` | 日志、两次调用、`if`、嵌套调用 |
| 参数：捕获值、常量以及它们的计算（`rate * 2`） | 实体路径（这里没有实体） |
| 使用**简单值**的命名参数（`calc(rate = rate, id = id)`） | 带计算的命名参数（`calc(rate = r * 2, id = id)`），见下文 |

对于带计算的命名参数，Kotlin 会生成隐藏的局部变量。lambdaQL 在 v0.1 中拒绝局部变量。
这种情况下请使用位置参数：`it.calc(id, r * 2)`。

其他要点：

- 对带有 OUT 参数的存储过程使用 `exec` 会报错。这可以防止 OUT 值被意外丢失。
- 带有**默认参数**的桩函数（`fun calc(id: Long, rate: Double = 1.0)`）会报错（规则 PR11）。Kotlin 会填入 Kotlin 的默认值，而不是数据库的默认值。
- 不能使用数据库为参数定义的默认值。请传入每一个 IN 参数。
- 以 `null` 作为 IN 值没有问题。
- 非空的 `Long`/`Double` 参数和可空的 `Long?`/`Double?` 参数都可以使用（规则 PR13）。

---

<a id="83-out-inout-and-result-sets"></a>
## 8.3 OUT、INOUT 和结果集

返回类型如何对应到存储过程（规则 PR3、PR4、PR5）：

| 存储过程包含 | 桩函数的返回类型 |
|---|---|
| 没有任何结果 | 没有返回类型（`Unit`）→ 用 `qb.exec(...)` 调用 |
| 1 个 OUT 参数 | 该值（`Double?`、`Int?`、...） |
| 多个 OUT / INOUT 参数 | 一个 Kotlin data class（或 Java record） |
| 1 个结果集（MariaDB `SELECT`、Oracle `SYS_REFCURSOR` OUT、H2 `ResultSet` 函数） | `List<T>` |
| 多个结果集 | 带有 `List` 属性的 data class |
| OUT 值和结果集（MariaDB） | 带有标量属性和 `List` 属性的 data class |

data class 属性的填充方式：

- **标量属性** ← OUT 和 INOUT 参数（不包括游标），按数据库中的顺序。
- **`List` 属性** ← 结果集，按顺序（Oracle：游标 OUT 参数的顺序；MariaDB：返回结果集的顺序）。
- 如果数量不一致，lambdaQL 会抛出错误。
- 像 IN、OUT、IN、INOUT 这样混合的位置没有问题。lambdaQL 从数据库元数据中读取位置。

### INOUT

用 `@InOut` 标记参数。你传入的值作为**输入**。**输出**的值是返回的 data class 的一个属性。

```kotlin
@SqlProcedure("SP_ADJUST") fun adjust(id: Long, @InOut balance: Int?): AdjOut

val a = qb.call(OrderProcs::class.java) { it.adjust(id, 100) }.execute()
// a.balance 是新的余额
```

### 结果集元素类型 `T`

| `T` 是 | lambdaQL 如何读取 |
|---|---|
| 实体 | Hibernate 结果类 |
| data class、record 或 DTO | JDBC `CallableStatement`，列**按标签**匹配（`camelCase` ↔ `snake_case`，忽略大小写） |

lambdaQL 从不按位置映射列，因为 MariaDB 和 Oracle 返回 `SELECT *` 的列顺序不同。
它会转换常见类型（例如把数字转换为 `Long`/`Int`，把时间戳转换为 `LocalDateTime`）。

限制：

- 不能对存储过程的结果分页。
- 不支持 Oracle 隐式结果（`DBMS_SQL.RETURN_RESULT`）。请使用 `SYS_REFCURSOR` OUT 参数。

### 事务（规则 PR9）

- 存储过程中的 DML 是调用方事务的一部分，会随之一起回滚。
- 调用之前，lambdaQL 会刷新（flush）持久化上下文，但仅在事务处于活动状态且存在变更时才会这样做。
- 在事务之外，调用是自动提交的。
- 调用之后，已加载的实体可能是过时的。请对这些实体使用 `refresh` 或 `evict`。
- 存储过程中的 `COMMIT` 或 DDL 会破坏事务。lambdaQL 无法阻止这一点。

### 受影响的行数（规则 PR10）

存储过程没有通用的"受影响行数"结果（Oracle 返回 -1）。
如果你需要这个数字，请通过 OUT 参数返回它。

---

<a id="84-warning-argument-order"></a>
## 8.4 警告：参数顺序

> [!WARNING]
> **桩函数参数的顺序必须与数据库中的顺序相同。**

lambdaQL **按位置**绑定参数（规则 PR2）。
即使注册了名字，Hibernate 6.6.5 也按位置绑定。
MariaDB 和 H2 驱动不支持命名参数。
所以如果顺序错误，你可能会**在没有任何错误的情况下得到错误的结果**。

Kotlin 命名参数（`calc(rate = rate, id = id)`）**不会**改变这一点。编译器会把参数重新排列为桩函数的顺序。只有桩函数的顺序才算数。

lambdaQL 在第一次调用时检查的内容（来自 JDBC 元数据）：

| 检查项 | 能发现吗？ |
|---|---|
| 参数个数 | 能，报错 |
| 模式（IN / OUT / INOUT / 游标） | 能，报错 |
| 类型 | 能，报错 |
| 两个**相同类型**的参数被交换 | **仅当参数名可用时** |

危险情况的示例：

```kotlin
// 数据库：SP_TRANSFER(p_to NUMBER, p_from NUMBER)
@SqlProcedure("SP_TRANSFER") fun transfer(from: Long, to: Long)   // 顺序反了！类型相同
```

个数、模式和类型全部一致。只有名字能暴露这个错误。
lambdaQL 在能获取名字时会比较名字：Kotlin `-java-parameters`（或 kotlin-reflect），或 `@Param`。
它会忽略 `p_` 前缀和大小写。如果名字不可用，且两个相邻参数类型相同，lambdaQL 会记录一条 WARN 日志。

**建议：**使用 `-java-parameters` 编译（参见 [Kotlin 编译器设置](02-getting-started.md#kotlin-compiler-settings)），并使用与数据库相同的名字。

在 Oracle 上，lambdaQL 还可以使用真正的按名绑定（`pass_procedure_paramater_names=true` 加上桩函数的参数名）。

---

<a id="85-procedure-rules-pr1-to-pr15-summary"></a>
## 8.5 存储过程规则 PR1 到 PR15（摘要）

| # | 规则 |
|---|---|
| PR1 | 名字：直接使用注解的值。值为空时：使用 UPPER_SNAKE 形式的函数名。包名或 schema 名（`SQ_PKG.CALC`、`LQL.SQ_CALC`）可以使用。你也可以写 `@SqlProcedure(schema = ..., pkg = ..., value = ...)` 以避免混淆。 |
| PR2 | 参数**按位置**绑定。第一次调用时，会对照数据库元数据检查个数、模式和类型。被交换的同类型参数只有在有参数名时才能发现（参见 [8.4](#84-warning-argument-order)）。在 v0.1 中，包内的 Oracle 重载会报错。 |
| PR3 | 一个 OUT → 返回值。多个 OUT/INOUT → data class 或 record。`@InOut` 标记 INOUT 参数。 |
| PR4 | 一个结果集 → `List<T>`。实体通过 Hibernate 读取；data class/record/DTO 通过 JDBC 按标签匹配读取。不能分页。 |
| PR5 | 多个结果集 → data class 的 `List` 属性，按顺序。Oracle 隐式结果会被拒绝。 |
| PR6 | 单独调用的数据库函数在每种数据库上使用不同的路径（参见 [8.8](#88-call-a-db-function-alone)）。 |
| PR7 | 查询中的数据库函数：参见 [8.6](#86-declare-db-functions) 和规则 FN1 到 FN11。 |
| PR8 | 条件中作用于列的数据库函数会产生 WARN，因为无法使用索引（参见 [8.9](#89-warning-functions-on-columns-and-indexes-sargable)）。 |
| PR9 | 存储过程在调用方的事务中运行。需要时 lambdaQL 会在调用前刷新。调用后已加载的实体可能是过时的。 |
| PR10 | 没有通用的受影响行数。请使用 OUT 参数。 |
| PR11 | `null` IN 值没有问题。不能使用数据库参数的默认值。桩函数上的 Kotlin 默认参数会报错。 |
| PR12 | Lambda 是一次桩函数调用。参数是捕获值、常量或它们的计算。命名参数只能使用简单值。对带 OUT 参数的存储过程使用 `exec` 会报错。 |
| PR13 | 非空的 `Long`/`Double` 和可空的 `Long?`/`Double?` 都可以使用。Boolean 参数在 MariaDB 和 Oracle 23ai 及以上版本中可以使用。 |
| PR14 | 每个 Lambda 只分析一次。每个名字的存储过程元数据只读取一次。 |
| PR15 | H2 没有 OUT、INOUT、多结果集或包。请在 MariaDB 或 Oracle 上测试存储过程（例如使用 Testcontainers）。 |

---

<a id="86-declare-db-functions"></a>
## 8.6 声明数据库函数

数据库函数是你在数据库中创建的函数，例如 `FN_DISCOUNT(price)`。
Kotlin 接口没有静态函数，所以请在接口的 **`companion object`** 中声明它，并加上 **`@JvmStatic`** 和 `@SqlFunction`。
函数主体只能抛出异常。它永远不会在 lambdaQL 内部执行。

```kotlin
interface OrderFns {
    companion object {
        @JvmStatic @SqlFunction("FN_DISCOUNT")
        fun discount(price: Double?): Double? = throw DbOnlyException()

        @JvmStatic @SqlFunction("FN_IS_VIP")            // Boolean：Oracle 23ai 及以上版本
        fun isVip(customerId: Long?): Boolean? = throw DbOnlyException()
    }
}
```

如果你在 lambdaQL Lambda **之外**调用 `OrderFns.discount(...)`，它会抛出异常。这是有意为之。

为什么要用 `@JvmStatic`？这样 Java 代码就可以把 `OrderFns.discount(...)` 当作静态函数调用，对于 Java 和 Kotlin 调用方，函数看起来是一样的。
lambdaQL 读取的是 `companion object` 函数上的注解。

其他声明方式：

| 声明方式 | 状态 |
|---|---|
| 接口 `companion object` + `@JvmStatic` | **推荐** |
| Kotlin `object` + `@JvmStatic` | 支持 |
| Java 接口静态方法（从 Kotlin 调用） | 支持 |
| Kotlin 顶层函数 | 支持，**不推荐**：如果忘了导入，可能会悄悄用上同名的标准库函数（例如 `maxOf`） |
| 接口默认函数 + 单例常量 | 支持，但写起来更长 |
| 你的 repository 实现函数接口（mixin） | 拒绝 |
| 函数式接口常量（`DISCOUNT.apply(x)`）、函数类型属性 | 拒绝 |
| 扩展函数 | 在 v0.1 中拒绝（KC11） |
| 带默认参数、`inline` 或 `vararg` 的 `@SqlFunction` | 拒绝（FN9） |

请**带上接口名**调用函数（`OrderFns.discount`）。导入函数也可以，但同名的成员函数可能会把它遮蔽。

---

<a id="87-use-db-functions-in-queries"></a>
## 8.7 在查询中使用数据库函数

直接调用函数即可，不需要上下文参数。它可以在所有 Lambda 中使用：
`where`、`select`、`orderBy`、`groupBy`、`having`、`set`，以及子查询内部。

```kotlin
qb.from(Order::class.java).where { OrderFns.discount(it.price)!! > 10 }.toList()
// → where function('FN_DISCOUNT' as Double, o.price) > 10

qb.from(Order::class.java).where { OrderFns.isVip(it.customer?.id)!! }.toList()     // 单独使用一个 Boolean 函数

qb.from(Order::class.java).where { it.price!! > OrderFns.discount(base)!! }.toList()  // 列这一侧保持干净

qb.from(Order::class.java).orderBy { o, by -> by.desc(OrderFns.discount(o.price)) }.toList()

qb.from(Order::class.java)
    .select { o -> OrderView(o.id, o.customer?.name, OrderFns.discount(o.price)) }
    .toList()

qb.update(Order::class.java).set { it.price = OrderFns.discount(it.price) }.all().execute()
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

`!!` 只是写给编译器看的（函数返回 `Double?`）。lambdaQL 会跳过它（规则 FN6）。

### lambdaQL 如何渲染

lambdaQL **总是**把数据库函数渲染为：

```sql
function('<NAME>' as <Type>, arg1, arg2, ...)
```

`<Type>` 来自声明的返回类型。只允许以下类型：

| 允许的返回类型 |
|---|
| `Double`、`Integer`、`Long`、`BigDecimal`、`String`、`LocalDate`、`LocalDateTime`、`Boolean` |

Kotlin 的 `Double?`、`Int?`、`Long?`、`Boolean?` 及其非空形式都对应到这些名字。
泛型返回（`fun <X> nvl(a: X, b: X): X`）、实体、集合和 `Unit` 会在分析时被拒绝。

### 注册（可选）

`function('FN_DISCOUNT' as Double, ...)` 这种形式不需要注册（已在三种数据库上检查）。
如果你注册了函数接口，Hibernate 还会保留名字的大小写，并检查参数的个数和类型。

lambdaQL 计划提供一个 `FunctionContributor`（通过 ServiceLoader 发现）。你在一个 Hibernate 属性中列出这些接口：

```properties
# 纯 Hibernate
lambdaql.functions.classes=com.acme.OrderFns,com.acme.CustomerFns

# Spring Boot
spring.jpa.properties.lambdaql.functions.classes=com.acme.OrderFns
```

如果没有设置该属性，就不会注册任何东西。v0.1 不支持包扫描。

### 大小写混合的名字需要注册

用引号创建的大小写混合的函数名（`"SQ_Fn_Mixed"`）在不注册时会失败（在 H2 和 Oracle 上名字会变成小写）。
请把键和 SQL 名分开，并注册该接口：

```kotlin
@JvmStatic @SqlFunction(key = "SQ_Fn_Mixed", sql = "\"SQ_Fn_Mixed\"")
fun mixed(v: Double?): Double? = throw DbOnlyException()
```

如果这样的函数没有注册，lambdaQL 会在第一次使用时失败。

---

<a id="88-call-a-db-function-alone"></a>
## 8.8 单独调用数据库函数

使用 `qb.call { ... }`，并传入一个**没有参数的 Lambda**：

```kotlin
val v: Double? = qb.call { OrderFns.discount(price) }.execute()
```

**各数据库的 SQL（计划中）** — Hibernate 对计划中 HQL 的翻译，由 `PlannedSqlTest` 记录；lambdaQL 目前还不会生成此查询。来自 `expected-sql/` 的 [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)，示例 `08-function-alone`：

```sql
-- H2, MariaDB, Oracle (same)
select fn_discount(?)
```

它作为没有 FROM 的 `select fn_discount(?)` 运行。Oracle 从 23ai 起才接受这种写法；在 Oracle 上，lambdaQL 改用 JDBC 函数调用（见下表）。

它与 `qb.call(OrderProcs::class.java) { ... }` 没有冲突，因为参数个数不同。

lambdaQL 的执行方式（规则 PR6）：

| 数据库 | 路径 |
|---|---|
| Oracle | JDBC 函数调用 `{? = call FN(?)}`。这也适用于执行 DML 的函数和返回游标的函数。 |
| MariaDB、H2 | HQL `select function('FN' as T, :p1)` |

lambdaQL 会先刷新待处理的变更（与 PR9 相同），所以函数能看到你最新的变更。

---

<a id="89-warning-functions-on-columns-and-indexes-sargable"></a>
## 8.9 警告：列上的函数与索引（SARGable）

lambdaQL 尽量生成**能够使用索引**的 SQL（参见 [10. 规则与限制](10-rules-and-limits.md#102-sargable-rule-index-friendly-sql)）。
包在列外面的函数通常**无法**使用普通索引：

```kotlin
.where { OrderFns.discount(it.price)!! > 10 }
// → function('FN_DISCOUNT' as Double, o.price) > 10   -- 不会使用 price 上的索引
```

**各数据库的 SQL（计划中）** — Hibernate 对计划中 HQL 的翻译，由 `PlannedSqlTest` 记录；lambdaQL 目前还不会生成此查询。来自 `expected-sql/` 的 [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)，示例 `08-function`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where fn_discount(o1_0.price)>10
```

所以 lambdaQL 会对这种写法记录一条 **WARN** 日志。在严格模式下，它是一个错误（规则 PR8、FN7）。

| 情况 | MariaDB | Oracle |
|---|---|---|
| 函数作用于列：`fn(o.price) > 10` | WARN（存储函数不能用于索引） | WARN，**除非**函数是 `DETERMINISTIC` **并且**存在针对相同表达式的函数索引 |
| 函数只作用于值：`o.price > fn(rate)` | 如果函数是 `NOT DETERMINISTIC`（这是默认值！）则 WARN。此时 MariaDB 会扫描整个索引。 | 没有警告（索引范围扫描） |

**建议：**

- 尽可能把函数放在值的一侧：`it.price!! > OrderFns.discount(base)!!`。
- 在 MariaDB 上，如果你的函数确实是确定性的，请把它声明为 `DETERMINISTIC`。
- 在 Oracle 上，`DETERMINISTIC` 函数加上函数索引可以消除警告。

---

<a id="810-function-rules-fn1-to-fn11-summary"></a>
## 8.10 函数规则 FN1 到 FN11（摘要）

| # | 规则 |
|---|---|
| FN1 | `@SqlFunction` 是函数注解。值为空时：使用 UPPER_SNAKE 形式的函数名。 |
| FN2 | lambdaQL 找到被调用的函数并读取它的注解。在 Kotlin 中它会跳过 `Companion` 接收者。函数主体只能抛出异常（`= throw ...`）。带有真实主体的函数会报错，因为应用程序和数据库可能给出不同的值。 |
| FN3 | 同名的所有重载必须对应到同一个数据库函数，否则报错。（Kotlin 的 `Double?` 参数会选择 `Double?` 重载；从 Kotlin 看到的 Java 实体 getter 可能会选择基本类型 `double` 的重载。） |
| FN4 | 没有注解的调用，或 lambdaQL 无法识别的调用，会报错。它永远不会被悄悄跳过。 |
| FN5 | 参数遵循 [3. 条件](03-where.md) 中的值规则。即使参数只由捕获值构成，也会被绑定并**在数据库中**求值。 |
| FN6 | `!!` 和装箱调用会被跳过。返回类型来自声明的函数。 |
| FN7 | 作用于列的函数会产生 WARN（参见 [8.9](#89-warning-functions-on-columns-and-indexes-sargable)）。 |
| FN8 | 与 Hibernate 内置函数（`COALESCE`、`ROUND`、`SYSDATE`、...）同名会报错。Hibernate 会悄悄使用内置函数。请使用前缀或 schema 名（`PUBLIC.SQ_FN`）。 |
| FN9 | Kotlin：默认参数、`inline` 和 `vararg` 会报错。 |
| FN10 | v0.1 只支持标量函数。用户自定义的聚合函数和窗口函数计划在 v0.2 中提供。没有参数的函数渲染为 `fn()`。 |
| FN11 | 已知的数据库差异：在 MariaDB 上，返回 VARCHAR 的函数可能与字符串字面量产生排序规则（collation）冲突。`BigDecimal` 结果的小数位数不同（MariaDB 和 Oracle 为 0，H2 为 1）。Boolean 结果需要 Oracle 23ai 及以上版本。 |

---

<a id="811-database-support"></a>
## 8.11 数据库支持

| 功能 | H2 2.3 | MariaDB 10.11 | Oracle 23ai |
|---|---|---|---|
| IN 参数 | 支持 | 支持 | 支持 |
| OUT / INOUT | **不支持**（H2 的限制） | 支持 | 支持 |
| 一个结果集 | 支持（`ResultSet` 函数） | 支持（存储过程中的 `SELECT`） | 支持（`SYS_REFCURSOR` OUT） |
| 多个结果集 | 不支持 | 支持 | 支持（多个游标 OUT 参数） |
| OUT 值与结果集同时使用 | 不支持 | 支持 | 支持 |
| 包名（`PKG.PROC`） | 不支持 | 不支持（需要 `sql_mode=ORACLE`） | 支持 |
| 查询中的数据库函数 | 支持 | 支持 | 支持 |
| Boolean 参数 / 结果 | – | 支持 | 支持（23ai 及以上版本） |

v0.1 不计划提供：用于动态存储过程名的字符串构建器（v0.2 候选）、Oracle 包重载、表值函数、存储过程结果的分页。

本章中的错误都是带有 `[LQL-xxxx]` 代码的 `LambdaQLException` 子类。参见 [11. 错误与语言](../11-errors-and-languages.md)。

---

[Kotlin 路线](README.md) | 上一章：[7. 更新、插入、删除](07-dml.md) | 下一章：[9. Kotlin 说明](09-kotlin-notes.md) | [本页的 Java 版本](../java/08-procedures-functions.md)
