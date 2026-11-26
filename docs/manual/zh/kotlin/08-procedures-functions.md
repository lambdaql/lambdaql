<a id="8-stored-procedures-and-db-functions-kotlin"></a>
# 8. 存储过程与数据库函数（Kotlin）

[Kotlin 路线](README.md) | [手册首页](../README.md) | 上一章：[7. 更新、插入、删除](07-dml.md) | 下一章：[9. Kotlin 说明](09-kotlin-notes.md) | [本页的 Java 版本](../java/08-procedures-functions.md)

公共页面：[1. 概述](../01-overview.md) · [11. 错误与语言](../11-errors-and-languages.md)

---

本章分为两部分。

| 部分 | 对象 | 注解 | 调用方式 |
|---|---|---|---|
| [8.1 到 8.5](#81-declare-procedures) | **存储过程** | 加在接口抽象函数上的 `@SqlProcedure` | `qb.call(OrderProcs::class.java) { it.calc(id, rate) }.execute()` |
| [8.6 到 8.10](#86-declare-db-functions) | **数据库函数**（标量） | 加在接口 `companion object` 中 `@JvmStatic` 函数上的 `@SqlFunction` | 在任何 Lambda 表达式中直接调用：`where { OrderFns.discount(it.price)!! > 10 }` |

两种情况下，签名都只需在 Kotlin 接口中写**一次**。
这些函数的主体不会被调用。lambdaQL 从 Lambda 表达式的字节 opcode 中读取调用，然后调用数据库中的存储过程或函数。

<a id="contents"></a>
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

请编写一个接口，并在每个**抽象**函数上加上 `@SqlProcedure("NAME")`。
不需要编写实现。lambdaQL 也不会生成实现。

<!-- region: ManualKotlinProcedureTest.kt#declare -->
```kotlin
/** Two OUT values: a data class, in the database order. */
data class Stat(val total: Double?, val cnt: Int?)

/** The INOUT value and the OUT value, in the database order. */
data class Adjusted(val balance: Int?, val msg: String?)

/** A row of a result set: columns are matched by name (customer_id or customerId). */
data class Line(val id: Long?, val price: Double?)

/** A stub interface: lambdaQL reads the calls and never implements it. */
interface OrderProcs {
    @SqlProcedure("SQ_PROC_CALC")
    fun calc(customerId: Int, rate: Double): Double?                // IN, IN, OUT (non-null parameters)

    @SqlProcedure("SQ_PROC_STAT")
    fun stat(customerId: Int?): Stat                                // IN, OUT, OUT

    @SqlProcedure("SQ_PROC_ADJUST")
    fun adjust(add: Int?, @InOut balance: Int?): Adjusted           // IN, INOUT, OUT

    @SqlProcedure("SQ_PROC_RECENT")
    fun recent(customerId: Int?): List<Line>                        // a result set (Oracle, PostgreSQL: a cursor)

    @SqlProcedure("SQ_PROC_PURGE")
    fun purge(status: Int?)                                         // no result

    @SqlProcedure("SQ_PROC_CALC")
    fun calcWrong(customerId: Int?): Double?                        // does not match the database: 1 argument
}
```

本章的示例调用已在数据库中创建好的存储过程（`SQ_PROC_CALC` 等）。示例中的 `Order` 有 `customer_id` 和 `price` 列。

说明：

- `@SqlProcedure`（或 `@SqlFunction`）是**必需**的。它告诉 lambdaQL 这个函数是什么。
  （没有使用 `@Procedure` 和 `@Function` 这两个名字：`@Procedure` 也是 Spring Data JPA 的注解，而 `@Function` 与 Kotlin 的 `kotlin.Function` 冲突。）
- 如果值为空，名字取自函数名：`calcTotal` → `CALC_TOTAL`。
- Oracle 包中的存储过程用 `pkg` 指定：`@SqlProcedure(pkg = "SQ_PKG", value = "TWICE")`（见 [8.4](#84-warning-argument-order) 中的 `PkgProcs`）。
- 请使用**可空返回类型**（`Double?`）。如果存储过程向非空类型返回 NULL，lambdaQL 会抛出带错误码 `LQL-4039` 的 `IllegalStateException`。
- 接口和结果 data class 是数据库签名的副本。这是类型安全的代价。lambdaQL 会对照数据库检查其中一部分。
- 可以把存储过程（抽象函数）和数据库函数（在 `companion object` 中）放在**同一个接口**里。

示例代码：[`ManualKotlinProcedureTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinProcedureTest.kt)（`declare`）。

---

<a id="82-call-procedures"></a>
## 8.2 调用存储过程

请使用 `qb.call(Interface::class.java) { it.function(args) }`，然后调用 `execute()`。

<!-- region: ManualKotlinProcedureTest.kt#call -->
```kotlin
val total: Double? = qb.call(OrderProcs::class.java) { it.calc(customerId, rate) }.execute()
val stat: Stat = qb.call(OrderProcs::class.java) { it.stat(customerId) }.execute()
val recent: List<Line> = qb.call(OrderProcs::class.java) { it.recent(customerId) }.execute()
// named arguments with plain values: the compiler puts them back in the stub order
val same: Double? = qb.call(OrderProcs::class.java) { it.calc(rate = rate, customerId = customerId) }.execute()
```

没有结果的存储过程用 `exec` 调用。

<!-- region: ManualKotlinProcedureTest.kt#exec -->
```kotlin
// no result: exec, not call
qb.exec(OrderProcs::class.java) { it.purge(9) }.execute()
```

为什么没有结果的函数使用 `exec` 而不是 `call`？

- 没有选择 `run`：在 Kotlin 中，如果漏写了参数，`run { }` 会悄悄变成标准库的 `run`。

<a id="what-the-lambda-may-contain-rule-pr12"></a>
### Lambda 表达式中可以包含什么（规则 PR12）

Lambda 表达式的主体是**一次桩函数调用**，除此之外什么都没有。

| 允许 | 不允许 |
|---|---|
| `{ it.calc(customerId, rate) }` | 日志、两次调用、`if`、嵌套调用 |
| 参数：捕获值、常量以及它们的计算（`rate * 2`） | 实体路径（这里没有实体） |
| 使用**简单值**的命名参数（`calc(rate = rate, customerId = customerId)`） | 带计算的命名参数（`calc(rate = r * 2, customerId = customerId)`），见下文 |

对于带计算的命名参数，Kotlin 会生成隐藏的局部变量。lambdaQL 拒绝局部变量。
这种情况下请使用位置参数：`it.calc(customerId, r * 2)`。

其他要点：

- 对带有 OUT 参数的存储过程使用 `exec` 会报错（`LQL-4037`）。这可以防止 OUT 值被意外丢失。
- 不能使用数据库为参数定义的默认值。请传入所有 IN 参数。
- 以 `null` 作为 IN 值没有问题。
- 非空参数（`calc` 的 `Int`、`Double`）和可空参数（`stat` 的 `Int?`）都可以使用。

调用没有 `@SqlProcedure` 的函数时，lambdaQL 在读取 Lambda 表达式时就会报错（`LQL-3060`）。这发生在查询数据库之前，因此在 H2 上也能确认。

<!-- region: ManualKotlinProcedureTest.kt#not-annotated -->
```kotlin
// only @SqlProcedure functions of the stub can be called
assertEquals("LQL-3060", assertThrows<LambdaQLException> {
    qb.call(OrderProcs::class.java) { it.toString() }.execute()
}.code())
```

桩函数与数据库的比对，以及 `exec` 的 OUT 检查：

<!-- region: ManualKotlinProcedureTest.kt#rules -->
```kotlin
// the stub is checked against the database parameters on the first call
assertEquals("LQL-4036", assertThrows<LambdaQLException> {
    qb.call(OrderProcs::class.java) { it.calcWrong(1) }.execute()
}.code())
// exec cannot drop OUT values
assertEquals("LQL-4037", assertThrows<LambdaQLException> {
    qb.exec(OrderProcs::class.java) { it.calc(1, 1.0) }.execute()
}.code())
```

示例代码：[`ManualKotlinProcedureTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinProcedureTest.kt)（`call`、`exec`、`not-annotated`、`rules`）。

---

<a id="83-out-inout-and-result-sets"></a>
## 8.3 OUT、INOUT 和结果集

返回类型如何对应到存储过程：

| 存储过程包含 | 桩函数的返回类型 |
|---|---|
| 没有任何结果 | 没有返回类型（`Unit`）→ 用 `qb.exec(...)` 调用 |
| 1 个 OUT 参数 | 该值（`Double?`、`Int?`、...） |
| 多个 OUT / INOUT 参数 | Kotlin data class（或 Java record） |
| 1 个结果集（MariaDB·MySQL `SELECT`、Oracle `SYS_REFCURSOR` OUT、PostgreSQL `REFCURSOR` OUT） | `List<T>` |

data class 属性的填充方式：

- **标量属性** ← OUT 和 INOUT 参数（不包括游标），按数据库中的顺序。
- 如果数量不一致，lambdaQL 会抛出错误（`LQL-4038`）。
- lambdaQL 从数据库元数据中读取参数位置。

### INOUT

请用 `@InOut` 标记参数。传入的值是**输入**的值。**输出**的值是返回的 data class 的属性。

<!-- region: ManualKotlinProcedureTest.kt#inout -->
```kotlin
// 10 goes in as the INOUT value; the new balance comes back in the data class
val adjusted: Adjusted = qb.call(OrderProcs::class.java) { it.adjust(5, 10) }.execute()
assertEquals(Adjusted(15, "ok"), adjusted)
```

`@InOut` 必须恰好标在数据库中为 INOUT 的参数上。否则第一次调用会失败（`LQL-4036`）。

<a id="result-set-element-type-t"></a>
### 结果集元素类型 `T`

| `T` 是 | lambdaQL 如何读取 |
|---|---|
| 实体 | Hibernate 结果类（`StoredProcedureQuery`） |
| Kotlin data class 或 Java record | JDBC `CallableStatement`，列**按标签**匹配。去掉 `_` 并忽略大小写，所以 `customer_id` 对应 `customerId` |
| 一个值（`Long?`、`String?`、...） | 每行的第一列 |

普通类不能作为这里的行类型。请使用 data class。
MariaDB 和 Oracle 返回 `SELECT *` 的列顺序不同，因此 lambdaQL 从不按绝对位置映射属性。
常见类型会被转换（例如把数字转换为 `Long`/`Int`，把时间戳转换为 `LocalDateTime`）。

限制：

- 不能对存储过程的结果分页。
- 不支持 Oracle 隐式结果（`DBMS_SQL.RETURN_RESULT`）。请使用 `SYS_REFCURSOR` OUT 参数。

<a id="transactions-rule-pr9"></a>
### 事务

- 存储过程中的 DML 是调用方事务的一部分，会随之一起回滚。
- 调用之前，lambdaQL 会刷新（flush）持久化上下文，但仅在事务处于活动状态且存在变更时才刷新。
- `QueryBuilder(EntityManagerFactory)` 在自己的事务中执行调用。接收 `EntityManager` 的 `QueryBuilder`（包括 Spring 的共享 `EntityManager`）使用调用方的事务。没有事务时，调用会自动提交。
- 调用之后，已加载的实体可能是过时的。请对这些实体使用 `refresh` 或 `evict`。
- 存储过程中的 `COMMIT` 或 DDL 会破坏事务。lambdaQL 无法阻止这一点。

<a id="affected-rows-rule-pr10"></a>
### 受影响的行数（规则 PR10）

存储过程没有通用的“受影响行数”结果（Oracle 返回 -1）。
如果需要这个数字，请通过 OUT 参数返回它。

示例代码：[`ManualKotlinProcedureTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinProcedureTest.kt)（`inout`）。

---

<a id="84-warning-argument-order"></a>
## 8.4 警告：参数顺序

> [!WARNING]
> **桩函数参数的顺序必须与数据库中的顺序相同。**

lambdaQL **按位置**绑定参数。
即使注册了名字，Hibernate 6.6.5 也按位置绑定（实测，7.4 未测）。
MariaDB 和 H2 驱动不支持命名参数。
所以如果顺序错误，可能会**在没有任何错误的情况下得到错误的结果**。

Kotlin 的命名参数（`calc(rate = rate, id = id)`）**不会**改变这一点。编译器会把参数重新排列为桩函数的顺序。只有桩函数的顺序才重要。

第一次调用时，lambdaQL 通过 JDBC 元数据（`getProcedureColumns`）检查以下项目。结果按存储过程名缓存。

| 检查项 | 能否发现 |
|---|---|
| 存储过程是否存在 | 能，错误 `LQL-4035` |
| IN·INOUT 参数个数 = 桩函数参数个数 | 能，错误 `LQL-4036` |
| INOUT 参数 = 带 `@InOut` 的参数 | 能，错误 `LQL-4036` |
| OUT 值和结果集的个数 = 返回类型的组成部分数 | 能，错误 `LQL-4038` |
| 类型的种类（数字、字符、日期时间、布尔、二进制） | 能，错误 `LQL-4036`。同一种类内部的差异不比对（见下文“参数类型比对”） |
| 相同的参数名以另一种顺序排列 | 能，错误 `LQL-4043`。仅当能获得桩函数参数名时（见下文） |
| Oracle 包存储过程名有多个重载 | 能，错误 `LQL-4044` |

例如，对数据库中的 `SQ_PROC_DIFF(p_to, p_from)`，如果把桩函数声明为 `fun swapped(from: Int?, to: Int?)`，参数就会互换。
个数、模式和类型全部一致。只有名字能暴露这个错误。
因此 lambdaQL 在第一次调用时比较 IN 和 INOUT 参数的名字。

- 桩函数参数名从类文件中读取。kotlinc 只有用 `-java-parameters` 编译时才会保留名字（见 [Kotlin 编译器设置](02-getting-started.md#kotlin-compiler-settings)）。在桩函数参数上加 `@org.lambdaql.DbParam("p_to")`，无需编译选项即可直接指定数据库中的名字。
- 从数据库名中去掉**所有**名字共有、以 `_` 结尾的前缀（`p_`、`in_`、`v_` 等）。不假定固定的前缀。然后忽略大小写和 `_`。
- 名字相同而顺序不同时，在调用之前抛出 `LambdaQLException` `LQL-4043`。
- 名字不同（桩函数遵循另一种命名规则）时不报错。请加上 `@DbParam`，让检查生效。
- 类文件中没有名字，并且两个相邻参数类型相同时，每个存储过程以 `INFO` 级别记录一次 `LQL-5004`。
- 类型只比对种类（见下文“参数类型比对”）。两个同一种类的参数互换时，只能通过名字发现。

不使用 `-java-parameters` 编译时，请用 `@DbParam` 指定名字。下面的桩函数就是这样的例子。

<!-- region: ManualKotlinProcedureTest.kt#names -->
```kotlin
/** kotlinc keeps argument names only with -java-parameters; @DbParam gives the database name without it. */
interface DiffProcs {
    @SqlProcedure("SQ_PROC_DIFF")
    fun diff(@DbParam("p_to") to: Int?, @DbParam("p_from") from: Int?): Int?        // the database order: runs

    @SqlProcedure("SQ_PROC_DIFF")
    fun swapped(@DbParam("p_from") from: Int?, @DbParam("p_to") to: Int?): Int?     // the same names, other order

    @SqlProcedure("SQ_PROC_DIFF")
    fun text(to: String?, from: Int?): Int?                                         // a string for a number parameter
}
```

<!-- region: ManualKotlinProcedureTest.kt#name-check -->
```kotlin
assertEquals(7, qb.call(DiffProcs::class.java) { it.diff(10, 3) }.execute())
// the same names in another order: stopped before the call (it would run with the values swapped)
assertEquals("LQL-4043", assertThrows<LambdaQLException> {
    qb.call(DiffProcs::class.java) { it.swapped(3, 10) }.execute()
}.code())
// the kind of each argument is compared with the database parameter: a string for a number parameter stops
assertEquals("LQL-4036", assertThrows<LambdaQLException> {
    qb.call(DiffProcs::class.java) { it.text("10", 3) }.execute()
}.code())
```

**建议：**用 `-java-parameters` 编译，并使用与数据库相同的名字（共同前缀可以省略），或者加上 `@DbParam`。这样就能发现顺序被互换的错误。

**参数类型比对。** 第一次调用时，比对每个参数的类型与数据库参数类型的种类。
种类有数字、字符、日期时间、布尔、二进制五种，不同时在调用之前以 `LQL-4036` 停止。上面示例中的 `text` 就是这种情况。

- 布尔参数也视为与数字参数相符。MariaDB 的 `BOOLEAN` 是 `TINYINT(1)`，Oracle 23 之前没有 `BOOLEAN` 类型。
- 无法确定种类的一方（enum、对象类型、游标）不比对。
- 同一种类内部的差异（例如 `Int?` 参数与 `NUMBER(10,2)` 数据库参数）交给驱动转换。

**Oracle 包重载。** Oracle 包中可以有多个同名的存储过程。
lambdaQL 无法区分桩函数指的是哪一个，因此调用这样的名字会以 `LQL-4044` 停止（仅限 Oracle）。

<!-- region: ManualKotlinProcedureTest.kt#package -->
```kotlin
/** Oracle package procedures: TWICE has one form, HALF has two (an overload). */
interface PkgProcs {
    @SqlProcedure(pkg = "SQ_PKG", value = "TWICE")
    fun twice(value: Double?): Double?

    @SqlProcedure(pkg = "SQ_PKG", value = "HALF")
    fun half(value: Double?): Double?
}
```

<!-- region: ManualKotlinProcedureTest.kt#overload -->
```kotlin
assertEquals(5.0, qb.call(PkgProcs::class.java) { it.twice(2.5) }.execute()!!, 1e-9)
// HALF has two overloads in the package: lambdaQL cannot tell which one to call
assertEquals("LQL-4044", assertThrows<LambdaQLException> {
    qb.call(PkgProcs::class.java) { it.half(10.0) }.execute()
}.code())
```

<a id="when-the-metadata-cannot-be-read"></a>
**无法读取元数据时。** 数据库用户可能没有读取数据字典视图或 `information_schema` 的权限。
如果元数据查询本身失败（`SQLException`），lambdaQL 不会停止调用。

- 每个存储过程以 `INFO` 级别记录一次 `LQL-5005`，并且不比对桩函数。
- 参数布局取自桩函数：IN 和 INOUT 参数按桩函数顺序，然后是 OUT 参数（按结果组成部分的顺序），在 Oracle 上再为每个 `List` 组成部分加一个 `REF CURSOR`。因此没有元数据时，数据库声明中 OUT 参数必须放在 IN 参数之后。
- 需要元数据的检查会被跳过：个数和 `@InOut` 检查（`LQL-4036`）以及名字顺序检查（`LQL-4043`）。
- 如果查询成功但没有找到存储过程，仍然是 `LQL-4035`。
- 如果只有 Oracle 重载查询（`all_arguments`）失败，以 `INFO` 级别记录 `LQL-5006`，并在不检查重载的情况下调用。

示例代码：[`ManualKotlinProcedureTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinProcedureTest.kt)（`names`、`name-check`、`package`、`overload`）。

---

<a id="85-procedure-rules-pr1-to-pr15-summary"></a>
## 8.5 存储过程规则 PR1 到 PR15（摘要）

| # | 规则 | 状态 |
|---|---|---|
| PR1 | 名字：直接使用注解的值。值为空时使用 UPPER_SNAKE 形式的函数名。Oracle 包使用 `@SqlProcedure(pkg = ..., value = ...)`。 | 已实现 |
| PR2 | 参数**按位置**绑定。第一次调用时，对照数据库元数据检查 IN/INOUT 参数的个数和 `@InOut` 的位置。能获得名字时也比对名字，相同名字顺序不同即为错误。也比对参数类型的种类（数字、字符、日期时间、布尔、二进制），不同即为错误（`LQL-4036`）（见 [8.4](#84-warning-argument-order)）。无法读取元数据时，跳过检查，参数布局取自桩函数（见 [8.4](#when-the-metadata-cannot-be-read)）。 | 个数、`@InOut`、名字比对已实现。Oracle 包重载检测也已实现。类型比对也已实现。没有元数据时的替代行为（`LQL-5005`、`LQL-5006`）已实现（MariaDB）。 |
| PR3 | 1 个 OUT → 返回值。多个 OUT/INOUT → data class 或 record。`@InOut` 标记 INOUT 参数。 | 已实现 |
| PR4 | 1 个结果集 → `List<T>`。实体通过 Hibernate，data class/record 通过按标签匹配的 JDBC 读取。不能分页。 | 已实现 |
| PR5 | 不支持 Oracle 隐式结果。请使用 `SYS_REFCURSOR` OUT 参数。 | – |
| PR6 | 单独调用的数据库函数在每种数据库上使用不同的路径（见 [8.8](#88-call-a-db-function-alone)）。 | 已实现 |
| PR7 | 查询中的数据库函数：见 [8.6](#86-declare-db-functions) 和规则 FN1 到 FN11。 | 已实现 |
| PR8 | 条件中对列使用数据库函数时无法使用索引，因此记录 WARN（见 [8.9](#89-warning-functions-on-columns-and-indexes-sargable)）。错误码为 `LQL-5001`。 | 已实现。在 `SargableMode.STRICT` 下为错误（`LQL-3066`） |
| PR9 | 存储过程在调用方的事务中运行。需要时 lambdaQL 会在调用前刷新。调用后已加载的实体可能是过时的。 | 已实现 |
| PR10 | 没有通用的受影响行数。请使用 OUT 参数。 | – |
| PR11 | `null` IN 值没有问题。不能使用数据库参数的默认值。 | 已实现 |
| PR12 | Lambda 表达式是一次桩函数调用。参数是捕获值、常量或它们的计算。命名参数只能使用简单值。有 OUT 参数却使用 `exec` 会报错。 | 已实现 |
| PR13 | 非空参数（`Int`、`Double`）和可空参数（`Int?`、`Double?`）都可以使用。 | 已实现 |
| PR14 | 每个 Lambda 表达式只分析一次。存储过程元数据每个名字只读取一次。 | 已实现 |
| PR15 | H2 没有 OUT、INOUT 和包。请在 MariaDB、MySQL、PostgreSQL、Oracle 这样的数据库上测试存储过程（例如使用 Testcontainers）。 | – |

---

<a id="86-declare-db-functions"></a>
## 8.6 声明数据库函数

数据库函数是在数据库中自己创建的函数，例如 `SQ_FN_DISCOUNT(price)`。
Kotlin 接口没有静态函数，所以请在接口的 **`companion object`** 中声明它，并加上 **`@JvmStatic`** 和 `@SqlFunction`。
函数主体只能抛出异常。主体不会被调用。在 Lambda 表达式之外直接调用时会抛出异常。

<!-- region: ManualKotlinFunctionTest.kt#declare -->
```kotlin
/** Database functions: functions in the companion object of your own interface. The bodies only throw. */
interface OrderFns {
    companion object {
        /** `SQ_FN_DISCOUNT(price)`: 90% of the price. */
        @JvmStatic
        @SqlFunction("SQ_FN_DISCOUNT")
        fun discount(price: Double?): Double? = throw DbOnlyException()

        /** `SQ_FN_IS_GOLD(grade)`: a grade of 5 or more. */
        @JvmStatic
        @SqlFunction("SQ_FN_IS_GOLD")
        fun isGold(grade: Int?): Boolean? = throw DbOnlyException()
    }
}
```

示例使用已在数据库中创建好的两个函数（H2 用 `CREATE ALIAS` 创建）。`SQ_FN_DISCOUNT` 返回价格的 90%，`SQ_FN_IS_GOLD` 在等级为 5 及以上时为真。

在 lambdaQL Lambda 表达式**之外**调用 `OrderFns.discount(...)` 时，会抛出 `DbOnlyException`（`LQL-4034`）。这是有意为之（见 [8.10](#810-function-rules-fn1-to-fn11-summary)）。

`@SqlFunction` 有两个值。

| 值 | 含义 |
|---|---|
| `value` | 查询中使用的函数名。为空时为 UPPER_SNAKE 形式的函数名（`isGold` → `IS_GOLD`）。也可以带 schema 名（`PUBLIC.SQ_FN`） |
| `sql` | 与 `value` 不同时的 SQL 名。这样的函数需要注册（见[注册](#registration-optional)） |

为什么要用 `@JvmStatic`？这样 Java 代码就可以把 `OrderFns.discount(...)` 当作静态函数调用，对于 Java 和 Kotlin 调用方，函数看起来是一样的。
lambdaQL 读取的是 `companion object` 函数上的注解。

其他声明方式：

| 声明方式 | 状态 |
|---|---|
| 接口 `companion object` + `@JvmStatic` | **推荐** |
| Kotlin `object` + `@JvmStatic` | 可用 |
| Java 接口静态方法（从 Kotlin 调用） | 可用 |
| Kotlin 顶层函数 | 可用，**不推荐**：如果忘了导入，可能会悄悄用上同名的标准库函数（例如 `maxOf`） |
| 函数类型的值（`val fn: (Double?) -> Double?`） | 拒绝（`LQL-3008`） |
| 带默认参数、`vararg` 的 `@SqlFunction` | 拒绝（默认参数为 `LQL-3056`，`vararg` 为 `LQL-3057`） |

`object`、顶层函数和 Java 静态方法生成相同的 HQL。

<!-- region: ManualKotlinFunctionTest.kt#other-forms -->
```kotlin
// a Kotlin object with @JvmStatic, and a top-level function: the same HQL
val inObject = qb.from(Order::class.java).where { PriceFns.discount(it.price)!! > 100 }
val topLevel = qb.from(Order::class.java).where { manualKotlinTopLevelDiscount(it.price)!! > 100 }
assertEquals(inObject.toHql().hql(), topLevel.toHql().hql())
```

请**带上接口名**调用函数（`OrderFns.discount`）。导入函数也可以，但同名的成员函数可能会把它遮蔽。

示例代码：[`ManualKotlinFunctionTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinFunctionTest.kt)（`declare`、`other-forms`）。

---

<a id="87-use-db-functions-in-queries"></a>
## 8.7 在查询中使用数据库函数

请直接调用函数，不需要上下文参数。
可以用在 `where`、`select`、`orderBy` 和 `set` 中。

<!-- region: ManualKotlinFunctionTest.kt#where -->
```kotlin
// the function call is written as function('<name>' as <type>, ...); the column is its argument
val q = qb.from(Order::class.java).where { OrderFns.discount(it.price)!! > 100 }
assertEquals("select e0 from Order e0 where function('SQ_FN_DISCOUNT' as Double, e0.price) > 100.0", q.toHql().hql())
```

返回 Boolean 的函数单独就构成条件（`... = true`）。

<!-- region: ManualKotlinFunctionTest.kt#boolean -->
```kotlin
val q = qb.from(Order::class.java).where { OrderFns.isGold(it.customer!!.grade)!! }
assertEquals("select e0 from Order e0 join e0.customer j1 where function('SQ_FN_IS_GOLD' as Boolean, j1.grade) = true",
    q.toHql().hql())
```

只用捕获值作参数时，列一侧保持干净，函数用绑定的值在数据库中计算。

<!-- region: ManualKotlinFunctionTest.kt#captured -->
```kotlin
// a function of captured values only is still computed by the database (the value is bound)
val q = qb.from(Order::class.java).where { it.price > OrderFns.discount(base)!! }
assertEquals("select e0 from Order e0 where e0.price > function('SQ_FN_DISCOUNT' as Double, :p1)", q.toHql().hql())
```

`select` 和 `orderBy`（`Priced` 是 `data class Priced(val id: Long?, val discounted: Double?)`）：

<!-- region: ManualKotlinFunctionTest.kt#select -->
```kotlin
val q = qb.from(Order::class.java)
    .orderBy { o -> desc(OrderFns.discount(o.price)) }
    .select { Priced(it.id, OrderFns.discount(it.price)) }
assertEquals("select e0.id, function('SQ_FN_DISCOUNT' as Double, e0.price) from Order e0"
    + " order by function('SQ_FN_DISCOUNT' as Double, e0.price) desc nulls last", q.toHql().hql())
```

`set`：

<!-- region: ManualKotlinFunctionTest.kt#update -->
```kotlin
val u = qb.update(Order::class.java)
    .set { it.price = OrderFns.discount(it.price)!! }
    .where { it.price > 100 }
assertEquals("update Order e0 set e0.price = function('SQ_FN_DISCOUNT' as Double, e0.price) where e0.price > 100.0",
    u.toHql().hql())
```

有 `lambdaql.functions.classes` 设置时，即使是没有注册的函数，Hibernate 也会保留名字的大小写（`FN_DISCOUNT`）（在三种数据库上观察到，原因尚未确认）。
完全没有注册时，Hibernate 可能会把名字写成小写。不带引号的名字两种情况下都能工作。

`!!` 只是写给编译器看的（因为函数返回 `Double?`）。lambdaQL 会跳过它。

<a id="how-lambdaql-renders-it"></a>
### lambdaQL 输出的形式

lambdaQL **总是**把数据库函数输出为以下形式。

```text
function('<NAME>' as <Type>, arg1, arg2, ...)
```

`<Type>` 来自声明的返回类型。只允许以下类型。

| 允许的返回类型 |
|---|
| `Double`、`Integer`、`Long`、`BigDecimal`、`String`、`LocalDate`、`LocalDateTime`、`Boolean` |

Kotlin 的 `Double?`、`Int?`、`Long?`、`Boolean?` 及其非空形式都对应到这些名字。
泛型返回（`fun <X> nvl(a: X, b: X): X`）、实体、集合和 `Unit` 会在分析时被拒绝。

<a id="registration-optional"></a>
### 注册（可选）

`function('FN_DISCOUNT' as Double, ...)` 这种形式不需要注册（已在三种数据库上确认）。
注册函数接口后，Hibernate 还会保留名字的大小写，并检查参数的个数和类型。

lambdaQL 提供 Hibernate `FunctionContributor` `org.lambdaql.hibernate.LambdaQlFunctionContributor`（通过 ServiceLoader 发现）。接口列在 Hibernate 属性中。

```properties
# 普通 Hibernate
lambdaql.functions.classes=com.acme.OrderFns,com.acme.CustomerFns

# Spring Boot
spring.jpa.properties.lambdaql.functions.classes=com.acme.OrderFns
```

不设置该属性时，不会注册任何东西。不支持包扫描。

<a id="a-different-sql-name-needs-registration"></a>
### SQL 名不同的函数的注册

`value` 是查询中的名字，`sql` 是 SQL 中的名字。两者不同时，必须注册该函数。
这对用引号创建的大小写混合的函数名（`"SQ_Fn_Mixed"`）很有用。不注册时，在 H2 和 Oracle 上名字可能会变成小写。

这样的函数没有注册时，lambdaQL 会在读取 Lambda 表达式时失败（`LQL-3058`）。[8.10](#810-function-rules-fn1-to-fn11-summary) 中的 `BadFns.unregistered`（`value = "SQ_TAX_KT"`、`sql = "SQ_FN_DISCOUNT"`）就是这种情况。注册方法见[注册](#registration-optional)。

示例代码：[`ManualKotlinFunctionTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinFunctionTest.kt)（`where`、`boolean`、`captured`、`select`、`update`）。

---

<a id="88-call-a-db-function-alone"></a>
## 8.8 单独调用数据库函数

请使用 `qb.call { ... }`，并传入一个**没有参数的 Lambda 表达式**。

<!-- region: ManualKotlinFunctionTest.kt#call -->
```kotlin
// a function on its own: a lambda without parameters
val discounted: Double? = qb.call { OrderFns.discount(price) }.execute()
```

Lambda 表达式必须是一次 `@SqlFunction` 调用（`LQL-3063`）。

这个查询作为没有 FROM 的 `select FN_DISCOUNT(?)` 执行。Oracle 从 23ai 起才接受这种写法。在 Oracle 上，lambdaQL 改用 JDBC 函数调用（见下表）。

参数个数不同，因此它与 `qb.call(OrderProcs::class.java) { ... }` 不冲突。

lambdaQL 的执行方式：

| 数据库 | 路径 |
|---|---|
| Oracle | JDBC 函数调用 `{? = call FN(?)}`。也适用于执行 DML 的函数和返回游标的函数。 |
| MariaDB、H2 | HQL `select function('FN' as T, :p1)` |

lambdaQL 会先刷新待处理的变更（与存储过程调用相同），所以函数能看到最新的变更。

示例代码：[`ManualKotlinFunctionTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinFunctionTest.kt)（`call`）。

---

<a id="89-warning-functions-on-columns-and-indexes-sargable"></a>
## 8.9 警告：列上的函数与索引（SARGable）

lambdaQL 尽量生成**能够使用索引**的 SQL（见 [10. 规则与限制](10-rules-and-limits.md#102-sargable-rule-index-friendly-sql)）。
包住列的函数通常**无法**使用普通索引。

[8.7](#87-use-db-functions-in-queries) 中的 `where { OrderFns.discount(it.price)!! > 100 }` 会成为 `function('SQ_FN_DISCOUNT' as Double, e0.price) > 100.0`，不会使用 `price` 的索引。

因此 lambdaQL 会对这种写法记录 **WARN**。
每个 `where` Lambda 表达式只检查一次，在缓存分析结果时进行。没有 `@SqlFunction` 的条件不会查询数据库。

| 情况 | 错误码 | H2 | MariaDB | Oracle |
|---|---|---|---|---|
| ① 函数包住列：`fn(o.price) > 10`、作用于列的 Boolean 函数、`IN` 或 `IS NULL` 中的函数 | `LQL-5001`（WARN） | WARN | WARN（存储函数不能用于索引） | WARN。**但**如果 `user_ind_expressions` 中该表有相同表达式 `FN(COLUMN)` 的函数索引，则不记录。由于列类型与函数参数类型不同而由 Oracle 插入的数字转换（如 `FN(TO_NUMBER(COLUMN))`）也视为相同表达式 |
| ② 值一侧的函数与列比较：`o.price > fn(rate)` | `LQL-5007`（WARN） | 不检查 | `information_schema.routines.is_deterministic = 'NO'`（默认值！）时 WARN。此时 MariaDB 会扫描整个索引 | 不检查（Oracle 两种情况都用范围扫描） |

- 与 Oracle 索引比对时使用的表名和列名取自 Hibernate 映射。只比对只有一个列参数的函数。
- 如果元数据查询失败（例如没有权限），以 `INFO` 级别记录 `LQL-5008`。① 照常警告，② 不检查。

下面示例记录的警告码因数据库而异。H2 为 `LQL-5001`，MariaDB 和 MySQL 为 `LQL-5001` 和 `LQL-5007`，有函数索引的 PostgreSQL 为 `LQL-5007`，有函数索引的 Oracle 没有警告。`CheckFns.nondet` 是与 `discount` 形式相同的 `@SqlFunction`（`SQ_FN_NONDET`），在 MariaDB 和 MySQL 中声明为 `NOT DETERMINISTIC`，在 PostgreSQL 中声明为 `VOLATILE`。

<!-- region: ManualKotlinFunctionTest.kt#sargable -->
```kotlin
// the function wraps the column: an index on price cannot be used (LQL-5001 at WARN),
// unless Oracle or PostgreSQL has a function-based index on SQ_FN_DISCOUNT(price)
qb.from(Order::class.java).where { OrderFns.discount(it.price)!! > 100 }.toHql()
// the function is on the value side: fine, unless the database says it is not deterministic (LQL-5007 at WARN)
qb.from(Order::class.java).where { it.price > CheckFns.nondet(base)!! }.toHql()
```

**SARGable 模式**。`SargableMode` 有三个值。

- `OFF`：不检查，也不查询元数据。
- `WARN`（默认）：记录警告。
- `STRICT`：在 `WARN` 会记录警告的情况下（`LQL-5001`、`LQL-5007`、`LQL-5012`），分析以 `org.lambdaql.NonSargableException`（`LQL-3066`）失败。

每个 persistence unit 用 `SargablePolicy.register(emf, SargableMode.STRICT)` 指定，用 `SargablePolicy.unregister(emf)` 解除。在 Spring 中使用 `lambdaql.sargable.mode=STRICT`（见 [12](12-spring-data-jpa.md)）。
检查在每个 Lambda 表达式上只执行一次，在把分析结果放入共享缓存时进行。因此请在第一个查询之前注册模式。

**列上的算术**。

- 只由一个列和值构成的 `+ - * /`（`it.cnt * 2 > n`）可以移到值一侧。lambdaQL 以 `WARN` 级别记录 `LQL-5012`，在 `STRICT` 下为错误。
- 列与列之间的算术（`it.price!! * it.cnt > n`）和 `%` 无法移动，因此只以 `DEBUG` 级别记录 `LQL-5013`。在 `STRICT` 下也允许。
- 移动后的形式必须含义相同。请像 `it.cnt > n / 2.0` 这样除以小数。整数除法 `n / 2` 在 `>=`、`=`、`<` 以及 `n` 为负数时会得到不同的行。如果乘以了负数，请反转比较方向。`LQL-5012` 的消息会给出这些提示。
- 没有采用查询索引是否存在来减少警告的方式。因为权限和同义词会让“没有索引”与“无法得知”混在一起，而且列上的算术是少见的写法。

`STRICT` 模式与列上的算术：

<!-- region: ManualKotlinFunctionTest.kt#sargable-strict -->
```kotlin
// STRICT: what WARN would log fails the analysis (NonSargableException, LQL-3066)
SargablePolicy.register(emf, SargableMode.STRICT)
val fn = assertThrows<NonSargableException> {
    qb.from(Order::class.java).where { OrderFns.discount(it.total)!! > 100 }.toHql()
}
assertEquals("LQL-3066", fn.code())
assertThrows<NonSargableException> { qb.from(Order::class.java).where { it.cnt!! * 2 > n }.toHql() }
// the same meaning on the value side uses the index; divide by a decimal (n / 2 is integer division)
val moved = qb.from(Order::class.java).where { it.cnt!! > n / 2.0 }.toHql()
assertEquals("select e0 from Order e0 where e0.cnt > 2.5BD", moved.hql())
// arithmetic between columns cannot move, so STRICT allows it
qb.from(Order::class.java).where { it.cnt!! * it.status > n }.toHql()
```

**建议：**

- 尽可能把函数放在值的一侧：`it.price!! > OrderFns.discount(base)!!`。
- 在 MariaDB 上，如果函数确实是确定性的，请把它声明为 `DETERMINISTIC`。
- 在 Oracle 上，存在相同表达式的函数索引时警告会消失（这种索引需要 `DETERMINISTIC` 函数）。

示例代码：[`ManualKotlinFunctionTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinFunctionTest.kt)（`sargable`、`sargable-strict`）。

---

<a id="810-function-rules-fn1-to-fn11-summary"></a>
## 8.10 函数规则 FN1 到 FN11（摘要）

| # | 规则 | 状态 |
|---|---|---|
| FN1 | `@SqlFunction` 是函数注解。值为空时使用 UPPER_SNAKE 形式的函数名。 | 已实现 |
| FN2 | lambdaQL 找到被调用的函数并读取它的注解。在 Kotlin 中跳过 `Companion` 接收者。函数主体只能抛出异常（`= throw ...`）。应用程序和数据库可能给出不同的值，因此带有真实主体的函数会报错（`LQL-3052`）。 | 已实现 |
| FN3 | 同名的所有重载必须对应到同一个数据库函数，否则报错（`LQL-3053`）。（Kotlin 的 `Double?` 参数会选择 `Double?` 重载；从 Kotlin 看到的 Java 实体 getter 可能会选择基本类型 `double` 的重载。） | 已实现 |
| FN4 | 没有注解的调用，或 lambdaQL 无法识别的调用，会报错。不会被悄悄跳过。 | 已实现 |
| FN5 | 参数遵循 [3. 条件](03-where.md) 中的值规则。只由捕获值构成的参数也会被绑定并**在数据库中**求值。 | 已实现 |
| FN6 | `!!` 和装箱调用会被跳过。返回类型来自声明的函数，必须对应到 `Double`、`Integer`、`Long`、`BigDecimal`、`String`、`LocalDate`、`LocalDateTime`、`Boolean` 之一（`LQL-3055`）。 | 已实现 |
| FN7 | 作用于列的函数会产生 WARN（见 [8.9](#89-warning-functions-on-columns-and-indexes-sargable)）。在 MariaDB 上，值一侧的 `NOT DETERMINISTIC` 函数也会产生 WARN（`LQL-5007`）。 | 已实现。在 `SargableMode.STRICT` 下为错误（`LQL-3066`） |
| FN8 | 与 Hibernate 内置函数（`COALESCE`、`ROUND`、`SYSDATE`、...）同名会报错（`LQL-3054`），因为 Hibernate 会悄悄使用内置函数。请使用前缀或 schema 名（`PUBLIC.SQ_FN`）。 | 已实现 |
| FN9 | Kotlin：使用默认参数的调用（`LQL-3056`）、`vararg`（`LQL-3057`）会报错。 | 已实现 |
| FN10 | 只支持标量函数。没有参数的函数输出为 `fn()`。 | 已实现 |
| FN11 | 已知的数据库差异：在 MariaDB 上，返回 VARCHAR 的函数可能与字符串字面量产生排序规则（collation）冲突。`BigDecimal` 结果的小数位数不同（MariaDB 和 Oracle 为 0，H2 为 1）。Boolean 结果需要 Oracle 23ai 及以上版本。 | 已用原型实测 |

违反规则的声明：

<!-- region: ManualKotlinFunctionTest.kt#bad-declare -->
```kotlin
/** Functions that break the rules. */
interface BadFns {
    companion object {
        @JvmStatic
        @SqlFunction("SQ_FN_DISCOUNT")
        fun computes(price: Double?): Double? = price                  // a body that computes a value

        @JvmStatic
        @SqlFunction("SQ_FN_DISCOUNT")
        fun withDefault(price: Double?, rate: Double = 0.9): Double? = throw DbOnlyException()   // a default argument

        @JvmStatic
        @SqlFunction("SQ_FN_DISCOUNT")
        fun many(vararg prices: Double?): Double? = throw DbOnlyException()                      // vararg

        @JvmStatic
        @SqlFunction(value = "SQ_TAX_KT", sql = "SQ_FN_DISCOUNT")
        fun unregistered(price: Double?): Double? = throw DbOnlyException()   // its own SQL name, not registered
    }
}
```

<!-- region: ManualKotlinFunctionTest.kt#rules -->
```kotlin
// the body never runs in Kotlin
assertEquals("LQL-4034", assertThrows<DbOnlyException> { OrderFns.discount(1.0) }.code())
// a body that computes a value is rejected (Kotlin and the database could give different values)
assertEquals("LQL-3052", assertThrows<LambdaQLException> {
    qb.from(Order::class.java).where { BadFns.computes(it.price)!! > 1 }
}.code())
// a call that uses a Kotlin default argument, and a vararg function
assertEquals("LQL-3056", assertThrows<LambdaQLException> {
    qb.from(Order::class.java).where { BadFns.withDefault(it.price)!! > 1 }
}.code())
assertEquals("LQL-3057", assertThrows<LambdaQLException> {
    qb.from(Order::class.java).where { BadFns.many(it.price)!! > 1 }
}.code())
// a function with its own SQL name must be registered (lambdaql.functions.classes)
assertEquals("LQL-3058", assertThrows<LambdaQLException> {
    qb.from(Order::class.java).where { BadFns.unregistered(it.price)!! > 1 }
}.code())
```

与内置函数同名（`LQL-3054`）和无法对应的返回类型（`LQL-3055`）也会在读取 Lambda 表达式时被拒绝。

示例代码：[`ManualKotlinFunctionTest.kt`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/ManualKotlinFunctionTest.kt)（`bad-declare`、`rules`）。

---

<a id="811-database-support"></a>
## 8.11 数据库支持

| 功能 | H2 | MariaDB、MySQL | PostgreSQL | Oracle |
|---|---|---|---|---|
| lambdaQL 的存储过程调用（IN、OUT、INOUT） | **否**（H2 没有 OUT） | 是 | 是 | 是 |
| 1 个结果集 | – | 是（存储过程中的 `SELECT`） | 是（`REFCURSOR` OUT） | 是（`SYS_REFCURSOR` OUT） |
| 包（`pkg`） | – | – | – | 是 |
| 查询中的数据库函数 | 是 | 是 | 是 | 是 |
| 单独调用数据库函数 | 是 | 是 | 是 | 是 |
| 返回 Boolean 结果的数据库函数 | 是 | 是 | 是 | 是（23ai 及以上） |

此表是在各数据库上确认过行为的范围。

不支持：选择并调用 Oracle 包存储过程的某个重载（重载检测已实现，`LQL-4044`）、表值函数、存储过程结果的分页。

本章中的错误是带有 `[LQL-xxxx]` 错误码的 `LambdaQLException`（或其子类）。读取 Lambda 表达式时为 `LQL-3052` 至 `LQL-3063`，以及在 `SargableMode.STRICT` 下无法使用索引的条件的 `LQL-3066`（`NonSargableException`，见 [8.9](#89-warning-functions-on-columns-and-indexes-sargable)）。执行调用时为 `LQL-4034` 至 `LQL-4039`、`LQL-4043` 和 `LQL-4044`。调用中还可能以 `INFO` 级别记录 `LQL-5004`、`LQL-5005`、`LQL-5006`。对于条件中的数据库函数，可能记录 `LQL-5001`、`LQL-5007`（`WARN`）和 `LQL-5008`（`INFO`）。见 [11. 错误与语言](../11-errors-and-languages.md)。

---

[Kotlin 路线](README.md) | 上一章：[7. 更新、插入、删除](07-dml.md) | 下一章：[9. Kotlin 说明](09-kotlin-notes.md) | [本页的 Java 版本](../java/08-procedures-functions.md)
