# 8. 存储过程与数据库函数（Kotlin）

> [!NOTE]
> **状态**
> - **已实现**（决策 D30 和 D31，里程碑 M11，Hibernate 6.6.5），并用 **Java** Lambda 测试：
>   查询中的数据库函数（`@SqlFunction`），在 H2 2.3、MariaDB 10.11 和 Oracle 23.26 上测试（[`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java)）；
>   存储过程调用（`@SqlProcedure`、`@InOut`、`qb.call`、`qb.exec`）在 MariaDB 和 Oracle 上测试，单独调用数据库函数在全部三种数据库上测试（[`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java)）。
>   lambdaQL 也能读取 Kotlin 写法：带 `@JvmStatic` 的 `companion object` 函数，以及作为结果的 Kotlin data class。本章还没有 Kotlin 测试。
> - **也已实现（已在 MariaDB 和 Oracle 上用 Java 测试）：**对照数据库检查桩函数参数的**名字**（规则 PR2 的一部分），以及 Oracle 包重载的检测。
> - **尚未实现：** 作用于列的函数的 SARGable 警告（规则 PR8、FN7）、对照数据库检查参数的**类型**（规则 PR2 的一部分），以及选择并调用 Oracle 包存储过程的某个重载。
> - 本章的 Kotlin 代码用 Kotlin 语法展示已测试的 Java 调用。已测试的 Java 代码见[本页的 Java 版本](../java/08-procedures-functions.md)。完整状态请参阅[路线 README](README.md)。

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
- `@SqlProcedure` 还有 `schema` 和 `pkg`（Oracle 包）：`@SqlProcedure(value = "CALC", schema = "LQL", pkg = "SQ_PKG")`。
- 请使用**可空返回类型**（`Double?`）。如果存储过程向非空类型返回 NULL，lambdaQL 会抛出带错误码 `LQL-4039` 的 `IllegalStateException`。
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

- 对带有 OUT 参数的存储过程使用 `exec` 会报错（`LQL-4037`，已用 Java 测试）。这可以防止 OUT 值被意外丢失。
- 调用没有 `@SqlProcedure` 的函数会报错（`LQL-3060`，已用 Java 测试）。
- 使用桩函数**默认参数**的调用（`fun calc(id: Long, rate: Double = 1.0)`）会报错（`LQL-3062`，规则 PR11）。Kotlin 会填入 Kotlin 的默认值，而不是数据库的默认值。
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
- 如果数量不一致，lambdaQL 会抛出错误（`LQL-4038`）。
- 像 IN、OUT、IN、INOUT 这样混合的位置没有问题。lambdaQL 从数据库元数据中读取位置。

### INOUT

用 `@InOut` 标记参数。你传入的值作为**输入**。**输出**的值是返回的 data class 的一个属性。

```kotlin
@SqlProcedure("SP_ADJUST") fun adjust(id: Long, @InOut balance: Int?): AdjOut

val a = qb.call(OrderProcs::class.java) { it.adjust(id, 100) }.execute()
// a.balance 是新的余额
```

`@InOut` 必须恰好标在数据库中为 INOUT 的参数上。否则第一次调用会失败（`LQL-4036`）。

### 结果集元素类型 `T`

| `T` 是 | lambdaQL 如何读取 |
|---|---|
| 实体 | Hibernate 结果类（`StoredProcedureQuery`） |
| Kotlin data class 或 Java record | JDBC `CallableStatement`，列**按标签**匹配：去掉 `_` 并忽略大小写，所以 `customer_id` 与 `customerId` 匹配（已用 Java 的 record 测试） |
| 一个值（`Long?`、`String?` 等） | 每行的第一列 |

普通类不能作为这里的行类型。请使用 data class。
lambdaQL 从不按位置映射属性，因为 MariaDB 和 Oracle 返回 `SELECT *` 的列顺序不同。
它会转换常见类型（例如把数字转换为 `Long`/`Int`，把时间戳转换为 `LocalDateTime`）。

限制：

- 不能对存储过程的结果分页。
- 不支持 Oracle 隐式结果（`DBMS_SQL.RETURN_RESULT`）。请使用 `SYS_REFCURSOR` OUT 参数。

### 事务（规则 PR9）

- 存储过程中的 DML 是调用方事务的一部分，会随之一起回滚。
- 调用之前，lambdaQL 会刷新（flush）持久化上下文，但仅在事务处于活动状态且存在变更时才会这样做。
- `QueryBuilder(EntityManagerFactory)` 在自己的事务中执行调用。带 `EntityManager` 的 `QueryBuilder`（包括 Spring 的共享 EntityManager）使用调用方的事务。没有事务时，调用是自动提交的。
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

lambdaQL 在第一次调用时检查的内容（来自 JDBC 元数据 `getProcedureColumns`）。结果按存储过程名缓存：

| 检查项 | 能发现吗？ |
|---|---|
| 存储过程存在 | 能，报错 `LQL-4035` |
| IN 和 INOUT 参数的个数 = 桩函数参数的个数 | 能，报错 `LQL-4036`（已用 Java 测试） |
| INOUT 参数 = 带 `@InOut` 的参数 | 能，报错 `LQL-4036` |
| OUT 值和结果集的个数 = 返回类型的组成部分 | 能，报错 `LQL-4038` |
| 类型 | **尚未实现** |
| 相同的参数名以另一种顺序排列 | 是，错误 `LQL-4043`（已用 Java 测试）。仅当能获得存根参数名时（见下文） |
| Oracle 包存储过程名有多个重载 | 是，错误 `LQL-4044`（已在 Oracle 上用 Java 测试） |

危险情况的示例：

```kotlin
// 数据库：SP_TRANSFER(p_to NUMBER, p_from NUMBER)
@SqlProcedure("SP_TRANSFER") fun transfer(from: Long, to: Long)   // 顺序反了！类型相同
```

个数、模式和类型全部匹配。只有名字能暴露这个错误。
因此 lambdaQL 在第一次调用时比较 IN 和 INOUT 参数的名字：

- 桩函数参数名从类文件中读取。只有用 `-java-parameters` 编译时名字才会保留（见 [Kotlin 编译器设置](02-getting-started.md#kotlin-compiler-settings)）。在桩函数参数上加 `@org.lambdaql.DbParam("p_to")` 可以直接给出数据库中的名字。
- lambdaQL 从数据库参数名中去掉一个以 `_` 结尾、并且**所有**名字共有的前缀（`p_`、`in_`、`v_` 等）。不假定固定的前缀。然后忽略大小写和 `_`。
- 名字相同但顺序不同：在调用之前抛出 `LambdaQLException` `LQL-4043`。
- 名字不同（桩函数遵循另一种命名规则）：不报错。请加上 `@DbParam`，让检查仍然有效。
- 类文件中没有名字，并且两个相邻参数类型相同：每个存储过程以 `INFO` 级别记录一次 `LQL-5004`。
- 类型尚不比较。

同样的桩函数已用 Java 测试（[`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java)，region `names` 和 `name-check`，MariaDB 和 Oracle；参见[本页的 Java 版本](../java/08-procedures-functions.md#84-warning-argument-order)）。Kotlin 需要 `-java-parameters` 才能保留名字。下面是设计示例：

```kotlin
// Database: SQ_PROC_DIFF(p_to, p_from)
interface DiffProcs {
    @SqlProcedure("SQ_PROC_DIFF")
    fun diff(to: Int?, from: Int?): Int?                             // same order as the database: runs

    @SqlProcedure("SQ_PROC_DIFF")
    fun swapped(from: Int?, to: Int?): Int?                          // same names, other order: LQL-4043

    @SqlProcedure("SQ_PROC_DIFF")
    fun named(@DbParam("p_to") target: Int?, @DbParam("p_from") source: Int?): Int?   // another naming rule
}

qb.call(DiffProcs::class.java) { it.diff(10, 3) }.execute()       // 7
qb.call(DiffProcs::class.java) { it.swapped(3, 10) }.execute()    // LambdaQLException LQL-4043, before the call
```

**建议：**用 `-java-parameters` 编译，并使用与数据库相同的名字（共同前缀可以省略），或者加上 `@DbParam`。这样就能发现顺序被交换的错误。

**Oracle 包重载。** Oracle 包中可以有多个同名的存储过程。
lambdaQL 无法区分桩函数指的是哪一个，因此调用这样的名字会以 `LQL-4044` 停止。选择并调用某一个重载计划在以后提供。

已用 Java 测试（[`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java)，region `overload`，仅 Oracle）。下面是设计示例：

```kotlin
interface PkgProcs {
    @SqlProcedure(pkg = "SQ_PKG", value = "TWICE") fun twice(value: Double?): Double?
    @SqlProcedure(pkg = "SQ_PKG", value = "HALF")  fun half(value: Double?): Double?    // HALF has two overloads
}

qb.call(PkgProcs::class.java) { it.twice(2.5) }.execute()     // 5.0
qb.call(PkgProcs::class.java) { it.half(10.0) }.execute()     // LambdaQLException LQL-4044
```

<a id="when-the-metadata-cannot-be-read"></a>
**无法读取元数据时。** 有些数据库用户无权读取数据字典视图或 `information_schema`。如果元数据查询本身失败（`SQLException`），lambdaQL 不会停止调用：

- 每个存储过程以 `INFO` 级别记录一次 `LQL-5005`，并且不检查桩函数。
- 参数布局取自桩函数：IN 和 INOUT 参数按桩函数顺序，然后是 OUT 参数（按结果部分的顺序），在 Oracle 上再为每个 `List` 部分加一个 `REF CURSOR`。因此没有元数据时，数据库声明中 OUT 参数必须放在 IN 参数之后。
- 需要元数据的检查会被跳过：个数和 `@InOut` 检查（`LQL-4036`）以及名字顺序检查（`LQL-4043`）。
- 如果查询成功但没有找到存储过程，仍然是 `LQL-4035`。
- 如果只有 Oracle 重载查询（`all_arguments`）失败，lambdaQL 以 `INFO` 级别记录 `LQL-5006`，并在不检查重载的情况下调用。

这种降级行为已在 MariaDB 和 Oracle 上用 Java 测试（`ProcedureTest` 的测试方法 `withoutMetadata`，模拟失败）。

Oracle 上真正的按名绑定（`pass_procedure_paramater_names=true` 加上桩函数的参数名）也在计划中。

---

<a id="85-procedure-rules-pr1-to-pr15-summary"></a>
## 8.5 存储过程规则 PR1 到 PR15（摘要）

| # | 规则 | 状态 |
|---|---|---|
| PR1 | 名字：直接使用注解的值。值为空时：使用 UPPER_SNAKE 形式的函数名。包名或 schema 名（`SQ_PKG.CALC`、`LQL.SQ_CALC`）可以使用。你也可以写 `@SqlProcedure(schema = ..., pkg = ..., value = ...)` 以避免混淆。 | 已实现。schema 名和包名没有测试覆盖 |
| PR2 | 参数**按位置**绑定。第一次调用时，会对照数据库元数据检查 IN/INOUT 参数的个数和 `@InOut` 的位置。能获得名字时也检查名字，相同名字顺序不同即为错误。类型检查在计划中（参见 [8.4](#84-warning-argument-order)）。无法读取元数据时，跳过这些检查，参数布局取自桩函数（见 [8.4](#when-the-metadata-cannot-be-read)）。 | 个数、`@InOut` 和名字检查已实现（已用 Java 测试）。Oracle 包重载检测也已实现（已在 Oracle 上测试）。类型检查尚未实现。没有元数据时的降级行为（`LQL-5005`、`LQL-5006`）也已实现（已在 MariaDB 和 Oracle 上用 Java 测试）。 |
| PR3 | 一个 OUT → 返回值。多个 OUT/INOUT → data class 或 record。`@InOut` 标记 INOUT 参数。 | 已实现（已用 Java 测试） |
| PR4 | 一个结果集 → `List<T>`。实体通过 Hibernate 读取；data class 和 record 通过 JDBC 按标签匹配读取。不能分页。 | 已实现（已用 Java 的 record 测试） |
| PR5 | 多个结果集 → data class 的 `List` 属性，按顺序。不支持 Oracle 隐式结果。 | 已实现，没有测试覆盖 |
| PR6 | 单独调用的数据库函数在每种数据库上使用不同的路径（参见 [8.8](#88-call-a-db-function-alone)）。 | 已实现（已用 Java 测试） |
| PR7 | 查询中的数据库函数：参见 [8.6](#86-declare-db-functions) 和规则 FN1 到 FN11。 | 已实现（已用 Java 测试） |
| PR8 | 条件中作用于列的数据库函数会产生 WARN，因为无法使用索引（参见 [8.9](#89-warning-functions-on-columns-and-indexes-sargable)）。 | 尚未实现 |
| PR9 | 存储过程在调用方的事务中运行。需要时 lambdaQL 会在调用前刷新。调用后已加载的实体可能是过时的。 | 已实现 |
| PR10 | 没有通用的受影响行数。请使用 OUT 参数。 | – |
| PR11 | `null` IN 值没有问题。不能使用数据库参数的默认值。桩函数上的 Kotlin 默认参数会报错（`LQL-3062`）。 | 已实现 |
| PR12 | Lambda 是一次桩函数调用。参数是捕获值、常量或它们的计算。命名参数只能使用简单值。对带 OUT 参数的存储过程使用 `exec` 会报错。 | 已实现（已用 Java 测试）。命名参数没有测试覆盖 |
| PR13 | 非空的 `Long`/`Double` 和可空的 `Long?`/`Double?` 都可以使用。Boolean 参数在 MariaDB 和 Oracle 23ai 及以上版本中可以使用。 | 没有测试覆盖 |
| PR14 | 每个 Lambda 只分析一次。每个名字的存储过程元数据只读取一次。 | 已实现 |
| PR15 | H2 没有 OUT、INOUT、多结果集或包。请在 MariaDB 或 Oracle 上测试存储过程（例如使用 Testcontainers）。 | lambdaQL 的测试遵循这一点 |

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

如果你在 lambdaQL Lambda **之外**调用 `OrderFns.discount(...)`，它会抛出 `DbOnlyException`（`LQL-4034`）。这是有意为之。

`@SqlFunction` 有两个值：

| 值 | 含义 |
|---|---|
| `value` | 查询中的函数名。为空时：函数名转为 UPPER_SNAKE（`isVip` → `IS_VIP`）。可以带 schema 名（`PUBLIC.SQ_FN`） |
| `sql` | SQL 名，当它与 `value` 不同时使用。这样的函数必须注册（参见[注册](#registration-optional)） |

Java 测试把它的函数声明为 Java 接口静态方法（参见[本页的 Java 版本](../java/08-procedures-functions.md#86-declare-db-functions)）。Kotlin `companion object` 函数会以同样的方式读取，但还没有测试覆盖。

为什么要用 `@JvmStatic`？这样 Java 代码就可以把 `OrderFns.discount(...)` 当作静态函数调用，对于 Java 和 Kotlin 调用方，函数看起来是一样的。
lambdaQL 读取的是 `companion object` 函数上的注解。

其他声明方式：

| 声明方式 | 状态 |
|---|---|
| 接口 `companion object` + `@JvmStatic` | **推荐**（已实现，还没有测试覆盖） |
| Kotlin `object` + `@JvmStatic` | 已设计，没有测试覆盖 |
| Java 接口静态方法（从 Kotlin 调用） | 支持（Java 写法已测试） |
| Kotlin 顶层函数 | 已设计，**不推荐**：如果忘了导入，可能会悄悄用上同名的标准库函数（例如 `maxOf`） |
| 接口默认函数 + 单例常量 | 已设计，没有测试覆盖 |
| 你的 repository 实现函数接口（mixin） | 拒绝 |
| 函数式接口常量（`DISCOUNT.apply(x)`）、函数类型属性 | 拒绝 |
| 扩展函数 | 在 v0.1 中拒绝（KC11） |
| 带默认参数、`inline` 或 `vararg` 的 `@SqlFunction` | 拒绝（FN9：默认参数为 `LQL-3056`，`vararg` 为 `LQL-3057`） |

请**带上接口名**调用函数（`OrderFns.discount`）。导入函数也可以，但同名的成员函数可能会把它遮蔽。

---

<a id="87-use-db-functions-in-queries"></a>
## 8.7 在查询中使用数据库函数

直接调用函数即可，不需要上下文参数。它可以用在 `where`、`select`、`orderBy` 和 `set` 中（已用 Java Lambda 在 [`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java) 中测试），也可以用在 `groupBy`、`having` 和子查询中（已设计，没有测试覆盖）。

```kotlin
qb.from(Order::class.java).where { OrderFns.discount(it.price)!! > 10 }.toList()
// → where function('FN_DISCOUNT' as Double, e0.price) > 10

qb.from(Order::class.java).where { OrderFns.isVip(it.customer?.id)!! }.toList()     // 单独使用一个 Boolean 函数：... = true

qb.from(Order::class.java).where { it.price!! > OrderFns.discount(base)!! }.toList()  // 列这一侧保持干净

qb.from(Order::class.java).orderBy { o, by -> by.desc(OrderFns.discount(o.price)) }.toList()

qb.from(Order::class.java)
    .select { o -> OrderView(o.id, o.customer?.name, OrderFns.discount(o.price)) }
    .toList()

qb.update(Order::class.java).set { it.price = OrderFns.discount(it.price) }.all().execute()
```

Java 测试用名字 `SQ_FN_DISCOUNT` 检查同样的写法，例如 `select e0 from Order e0 where function('SQ_FN_DISCOUNT' as Double, e0.price) > 100.0`。测试会执行每个查询，并把结果行与用 Java 进行的同样计算作比较。

**关于 SQL 块。** `…` 是缩短的 select 列表（实体的所有列），`?` 是绑定参数。[`PlannedSqlTest`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/PlannedSqlTest.java) 把同样形式的 HQL（函数名为 `FN_DISCOUNT`）直接交给 Hibernate，并记录在 H2 2.3、MariaDB 10.11 和 Oracle 23.26（Hibernate 6.6.5）上得到的 SQL。如果 Hibernate 的 SQL 发生变化，测试就会失败。

**各数据库的 SQL（Hibernate 对这种 HQL 形式生成的 SQL）** — 由 `PlannedSqlTest` 记录。来自 `expected-sql/` 的 [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)，示例 `08-function`：

```sql
-- H2, MariaDB, Oracle (same)
-- HQL: select o from Order o where function('FN_DISCOUNT' as Double, o.price) > 10
select …
from orders o1_0
where FN_DISCOUNT(o1_0.price)>10
```

测试环境中有 `lambdaql.functions.classes` 设置。有了它，Hibernate 会保留函数名的大小写（`FN_DISCOUNT`），即使是没有注册的函数也一样（在全部三种数据库上观察到；原因尚不清楚）。完全没有注册时，Hibernate 可能会把名字写成小写。不带引号的名字两种情况下都能工作。

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

<a id="registration-optional"></a>
### 注册（可选）

`function('FN_DISCOUNT' as Double, ...)` 这种形式不需要注册（已在三种数据库上检查）。
如果你注册了函数接口，Hibernate 还会保留名字的大小写，并检查参数的个数和类型。

lambdaQL 提供一个 Hibernate `FunctionContributor`：`org.lambdaql.hibernate.LambdaQlFunctionContributor`（通过 ServiceLoader 发现）。你在一个 Hibernate 属性中列出这些接口：

```properties
# 纯 Hibernate
lambdaql.functions.classes=com.acme.OrderFns,com.acme.CustomerFns

# Spring Boot
spring.jpa.properties.lambdaql.functions.classes=com.acme.OrderFns
```

如果没有设置该属性，就不会注册任何东西。v0.1 不支持包扫描。

<a id="a-different-sql-name-needs-registration"></a>
### SQL 名不同时需要注册

`value` 是查询中的名字，`sql` 是 SQL 中的名字。两者不同时，必须注册该函数。
这对用引号创建的大小写混合的函数名（`"SQ_Fn_Mixed"`）很有用：不注册时，在 H2 和 Oracle 上名字可能会变成小写。

```kotlin
@JvmStatic @SqlFunction(value = "SQ_Fn_Mixed", sql = "\"SQ_Fn_Mixed\"")
fun mixed(v: Double?): Double? = throw DbOnlyException()
```

如果这样的函数没有注册，lambdaQL 会在读取 Lambda 时失败（`LQL-3058`）。Java 测试检查了一个已注册的函数（`value = "SQ_TAX"`、`sql = "SQ_FN_DISCOUNT"`，region `registered`）。

---

<a id="88-call-a-db-function-alone"></a>
## 8.8 单独调用数据库函数

使用 `qb.call { ... }`，并传入一个**没有参数的 Lambda**：

```kotlin
val v: Double? = qb.call { OrderFns.discount(price) }.execute()
```

Java 写法已在 H2、MariaDB 和 Oracle 上测试（`ProcedureTest`，region `function`）。Lambda 必须是一次 `@SqlFunction` 调用（`LQL-3063`）。

**各数据库的 SQL（Hibernate 对这种 HQL 形式生成的 SQL）** — 由 `PlannedSqlTest` 记录。来自 `expected-sql/` 的 [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)，示例 `08-function-alone`：

```sql
-- H2, MariaDB, Oracle (same)
-- HQL: select function('FN_DISCOUNT' as Double, :p1)
select FN_DISCOUNT(?)
```

它作为没有 FROM 的 `select FN_DISCOUNT(?)` 运行。Oracle 从 23ai 起才接受这种写法；在 Oracle 上，lambdaQL 改用 JDBC 函数调用（见下表）。

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

> [!NOTE]
> 本节的警告**尚未实现**（规则 PR8、FN7）。目前 lambdaQL 生成这些查询时不会发出警告。

lambdaQL 尽量生成**能够使用索引**的 SQL（参见 [10. 规则与限制](10-rules-and-limits.md#102-sargable-rule-index-friendly-sql)）。
包在列外面的函数通常**无法**使用普通索引：

```kotlin
.where { OrderFns.discount(it.price)!! > 10 }
// → function('FN_DISCOUNT' as Double, e0.price) > 10   -- 不会使用 price 上的索引
```

**各数据库的 SQL（Hibernate 对这种 HQL 形式生成的 SQL）** — 由 `PlannedSqlTest` 记录。来自 `expected-sql/` 的 [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)，示例 `08-function`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where FN_DISCOUNT(o1_0.price)>10
```

所以 lambdaQL 将对这种写法记录一条 **WARN** 日志。在严格模式下，它是一个错误（规则 PR8、FN7）。

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

| # | 规则 | 状态 |
|---|---|---|
| FN1 | `@SqlFunction` 是函数注解。值为空时：使用 UPPER_SNAKE 形式的函数名。 | 已实现 |
| FN2 | lambdaQL 找到被调用的函数并读取它的注解。在 Kotlin 中它会跳过 `Companion` 接收者。函数主体只能抛出异常（`= throw ...`）。带有真实主体的函数会报错（`LQL-3052`），因为应用程序和数据库可能给出不同的值。 | 已实现（已用 Java 测试） |
| FN3 | 同名的所有重载必须对应到同一个数据库函数，否则报错（`LQL-3053`）。（Kotlin 的 `Double?` 参数会选择 `Double?` 重载；从 Kotlin 看到的 Java 实体 getter 可能会选择基本类型 `double` 的重载。） | 已实现 |
| FN4 | 没有注解的调用，或 lambdaQL 无法识别的调用，会报错。它永远不会被悄悄跳过。 | 已实现 |
| FN5 | 参数遵循 [3. 条件](03-where.md) 中的值规则。即使参数只由捕获值构成，也会被绑定并**在数据库中**求值。 | 已实现（已用 Java 测试） |
| FN6 | `!!` 和装箱调用会被跳过。返回类型来自声明的函数，必须对应到 `Double`、`Integer`、`Long`、`BigDecimal`、`String`、`LocalDate`、`LocalDateTime` 或 `Boolean`（`LQL-3055`）。 | 已实现（已用 Java 测试） |
| FN7 | 作用于列的函数会产生 WARN（参见 [8.9](#89-warning-functions-on-columns-and-indexes-sargable)）。 | 尚未实现 |
| FN8 | 与 Hibernate 内置函数（`COALESCE`、`ROUND`、`SYSDATE`、...）同名会报错（`LQL-3054`）。Hibernate 会悄悄使用内置函数。请使用前缀或 schema 名（`PUBLIC.SQ_FN`）。 | 已实现（已用 Java 测试） |
| FN9 | Kotlin：默认参数（`LQL-3056`）、`vararg`（`LQL-3057`）和 `inline` 会报错。 | 默认参数和 `vararg`：已实现。`inline`：已设计 |
| FN10 | v0.1 只支持标量函数。用户自定义的聚合函数和窗口函数计划在 v0.2 中提供。没有参数的函数渲染为 `fn()`。 | 标量函数已实现 |
| FN11 | 已知的数据库差异：在 MariaDB 上，返回 VARCHAR 的函数可能与字符串字面量产生排序规则（collation）冲突。`BigDecimal` 结果的小数位数不同（MariaDB 和 Oracle 为 0，H2 为 1）。Boolean 结果需要 Oracle 23ai 及以上版本。 | 已用原型测量 |

规则检查（`LQL-4034`、`LQL-3052`、`LQL-3054`、`LQL-3058`、`LQL-3055`）已用 Java 测试（[`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java)，region `rules`，参见[本页的 Java 版本](../java/08-procedures-functions.md#810-function-rules-fn1-to-fn11-summary)）。

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
| 查询中的数据库函数 | 支持（已测试） | 支持（已测试） | 支持（已测试） |
| 单独调用数据库函数 | 支持（已测试） | 支持（已测试） | 支持（已测试） |
| lambdaQL 中的存储过程调用 | – | 支持（已测试） | 支持（已测试） |
| Boolean 参数 / 结果 | – | 支持 | 支持（23ai 及以上版本） |

v0.1 不计划提供：用于动态存储过程名的字符串构建器（v0.2 候选）、选择并调用 Oracle 包存储过程的某个重载（重载检测已实现，`LQL-4044`）、表值函数、存储过程结果的分页。

本章中的错误都是带有 `[LQL-xxxx]` 错误码的 `LambdaQLException`（或其子类）：读取 Lambda 时为 `LQL-3052` 至 `LQL-3063`，执行调用时为 `LQL-4034` 至 `LQL-4039`、`LQL-4043` 和 `LQL-4044`。调用时还可能以 `INFO` 级别记录 `LQL-5004`、`LQL-5005` 或 `LQL-5006`。参见 [11. 错误与语言](../11-errors-and-languages.md)。

---

[Kotlin 路线](README.md) | 上一章：[7. 更新、插入、删除](07-dml.md) | 下一章：[9. Kotlin 说明](09-kotlin-notes.md) | [本页的 Java 版本](../java/08-procedures-functions.md)
