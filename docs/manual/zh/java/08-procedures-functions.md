<a id="8-stored-procedures-and-db-functions-java"></a>
# 8. 存储过程与数据库函数（Java）

[Java 路线](README.md) | [手册首页](../README.md) | 上一章：[7. 更新、插入、删除](07-dml.md) | 下一章：[9. Java 说明](09-java-notes.md) | [本页的 Kotlin 版本](../kotlin/08-procedures-functions.md)

公共页面：[1. 概述](../01-overview.md) · [11. 错误与语言](../11-errors-and-languages.md)

---

本章分为两部分。

| 部分 | 对象 | 注解 | 调用方式 |
|---|---|---|---|
| [8.1 ~ 8.5](#81-declare-procedures) | **存储过程** | 接口抽象方法上的 `@SqlProcedure` | `qb.call(OrderProcs.class, p -> p.calc(id, rate)).execute()` |
| [8.6 ~ 8.10](#86-declare-db-functions) | **数据库函数**（标量） | 接口 static 方法上的 `@SqlFunction` | 在任何 Lambda 表达式中直接调用：`where(o -> OrderFns.discount(o.getPrice()) > 10)` |

两种情况下，签名都只在 Java 接口中写**一次**。
lambdaQL **不在 Java 中调用**这些方法。它从 Lambda 表达式的字节码 opcode 中读取调用，然后调用数据库中的存储过程或函数。

<a id="contents"></a>
## 目录

- [8.1 声明存储过程](#81-declare-procedures)
- [8.2 调用存储过程](#82-call-procedures)
- [8.3 OUT、INOUT、结果集](#83-out-inout-and-result-sets)
- [8.4 注意：参数顺序](#84-warning-argument-order)
- [8.5 存储过程规则 PR1 ~ PR15（汇总）](#85-procedure-rules-pr1-to-pr15-summary)
- [8.6 声明数据库函数](#86-declare-db-functions)
- [8.7 在查询中使用数据库函数](#87-use-db-functions-in-queries)
- [8.8 单独调用数据库函数](#88-call-a-db-function-alone)
- [8.9 注意：列上的函数与索引（SARGable）](#89-warning-functions-on-columns-and-indexes-sargable)
- [8.10 函数规则 FN1 ~ FN11（汇总）](#810-function-rules-fn1-to-fn11-summary)
- [8.11 数据库支持情况](#811-database-support)

---

<a id="81-declare-procedures"></a>
## 8.1 声明存储过程

编写一个接口，在每个**抽象**方法上加上 `@SqlProcedure("NAME")`。
不编写实现。lambdaQL 也不生成实现。

<!-- region: ManualJavaFunctionTest.java#declare-procs -->
```java
/** One OUT value or more: a record in the database order. */
record Stat(Double total, Integer cnt) {
}

/** The INOUT value and the OUT value, in the database order. */
record Adjusted(Integer balance, String msg) {
}

/** A row of a result set: columns are matched by name (customer_id or customerId). */
record Line(Long id, Double price) {
}

/** A stub interface: lambdaQL reads the calls and never implements it. */
interface OrderProcs {
    @SqlProcedure("SQ_PROC_CALC")
    Double calc(Integer customerId, Double rate);                 // IN, IN, OUT

    @SqlProcedure("SQ_PROC_STAT")
    Stat stat(Integer customerId);                                // IN, OUT, OUT

    @SqlProcedure("SQ_PROC_ADJUST")
    Adjusted adjust(Integer add, @InOut Integer balance);         // IN, INOUT, OUT

    @SqlProcedure("SQ_PROC_RECENT")
    List<Line> recent(Integer customerId);                        // a result set (Oracle: a REF CURSOR)

    @SqlProcedure("SQ_PROC_PURGE")
    void purge(Integer status);                                   // no result

    @SqlProcedure("SQ_PROC_CALC")
    Double calcWrong(Integer customerId);                         // does not match the database: 1 argument
}

/** A database function. */
interface MathFns {
    @SqlFunction("SQ_FN_HALF")
    static Double half(Double value) {
        throw new DbOnlyException();
    }
}
```

下面的 `call`、`exec`、`rules` 示例使用这些声明。
数据库中必须有对应的存储过程。例如 MariaDB 的 `SQ_PROC_CALC(IN p_cust INT, IN p_rate DOUBLE, OUT p_total DOUBLE)` 把该客户的 `price` 合计乘以 `p_rate`，放入 `p_total`。

`calcWrong` 是为了演示参数顺序检查而故意声明错误的方法（见 [8.4](#84-warning-argument-order)）。

说明：

- `@SqlProcedure`（或 `@SqlFunction`）是**必需的**。它告诉 lambdaQL 这个方法是什么。
  （没有使用 `@Procedure` 和 `@Function` 这两个名字。`@Procedure` 也是 Spring Data JPA 的注解，`@Function` 与 Kotlin 的 `kotlin.Function` 冲突。）
- 值为空时，名字由方法名生成：`calcTotal` → `CALC_TOTAL`。
- `@SqlProcedure` 还有 `pkg`（Oracle 包）：`@SqlProcedure(pkg = "SQ_PKG", value = "TWICE")`（见 [8.4](#84-warning-argument-order)）。
- 请使用装箱类型（`Double`、`Integer`）作为返回类型。存储过程可能返回 NULL。
- 接口和结果 record 是数据库签名的副本，这是类型安全的代价。lambdaQL 会对照数据库检查其中一部分。
- 存储过程（抽象方法）和数据库函数（static 方法）可以放在**同一个接口**中。

示例代码：[`ManualJavaFunctionTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaFunctionTest.java)（`declare-procs`）。

---

<a id="82-call-procedures"></a>
## 8.2 调用存储过程

使用 `qb.call(Interface.class, p -> p.method(args))`，然后调用 `execute()`。

MariaDB、Oracle：

<!-- region: ProcedureTest.java#call -->
```java
Double total = qb.call(OrderProcs.class, p -> p.calc(customerId, rate)).execute();
Stat stat = qb.call(OrderProcs.class, p -> p.stat(customerId)).execute();
Adjusted adjusted = qb.call(OrderProcs.class, p -> p.adjust(5, 10)).execute();
List<Line> recent = qb.call(OrderProcs.class, p -> p.recent(customerId)).execute();
```

`customerId` 和 `rate` 是捕获的局部值。例如 `adjusted` 是 `new Adjusted(15, "ok")`。

没有结果（`void`）的存储过程用 `exec` 调用。

MariaDB、Oracle：

<!-- region: ProcedureTest.java#exec -->
```java
qb.exec(OrderProcs.class, p -> p.purge(9)).execute();
```

为什么 void 用 `exec` 而不用 `call`？

- 没有选择 `run`。Kotlin 标准库中有同名函数，Kotlin 调用方可能在没有任何错误的情况下误调用该函数。

<a id="what-the-lambda-may-contain-rule-pr12"></a>
### Lambda 表达式中可以包含的内容（规则 PR12）

Lambda 表达式体是**一次存根方法调用**，除此之外什么都没有。

| 允许 | 不允许 |
|---|---|
| `p -> p.calc(id, rate)` | 日志、两次调用、`if`、嵌套调用 |
| 参数：捕获值、常量及它们的计算（`rate * 2`） | 实体路径（这里没有实体） |

其他事项：

- 对带有 OUT 参数的存储过程使用 `exec` 是错误（`LQL-4037`）。以防 OUT 值被意外丢失。
- 调用没有 `@SqlProcedure` 的方法是错误（`LQL-3060`）。
- 不能使用参数的数据库默认值。请传入所有 IN 参数。
- IN 值可以传 `null`。

示例代码：[`ProcedureTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java)（`call`、`exec`）。

---

<a id="83-out-inout-and-result-sets"></a>
## 8.3 OUT、INOUT、结果集

返回类型与存储过程的对应方式：

| 存储过程包含 | 存根方法的返回类型 |
|---|---|
| 无 | `void` → 用 `qb.exec(...)` 调用 |
| 1 个 OUT 参数 | 该值（`Double`、`Integer`、...） |
| 多个 OUT / INOUT 参数 | Java record（Kotlin：data class） |
| 1 个结果集（MariaDB `SELECT`、Oracle `SYS_REFCURSOR` OUT、H2 `ResultSet` 函数） | `List<T>` |

record 组件的填充方式：

- **标量组件** ← OUT 和 INOUT 参数（游标除外），按数据库中的顺序。
- 数量不匹配时，lambdaQL 抛出错误（`LQL-4038`）。
- IN、OUT、IN、INOUT 这样混合的位置也没有问题。lambdaQL 从数据库元数据中读取位置。

### INOUT

在参数上加 `@InOut`。传入的值是**进入**的值，**出来**的值是返回 record 的组件。

示例中的 `SQ_PROC_ADJUST(p_add IN, p_balance IN OUT, p_msg OUT)` 把 `p_add` 加到 `p_balance` 上。

上面声明中的 `adjust(Integer add, @InOut Integer balance)` 就是这种形式。8.2 `call` 示例中的 `p.adjust(5, 10)` 返回 `new Adjusted(15, "ok")`。`balance()` 是新余额 15，`msg()` 是 `"ok"`。

`@InOut` 必须恰好加在数据库中为 INOUT 的参数上。否则第一次调用会失败（`LQL-4036`）。

<a id="result-set-element-type-t"></a>
### 结果集的元素类型 `T`

| `T` 是 | lambdaQL 的读取方式 |
|---|---|
| 实体 | Hibernate 结果类（`StoredProcedureQuery`） |
| Java record（Kotlin：data class） | JDBC `CallableStatement`，**按标签**匹配列。去掉 `_` 并忽略大小写，因此 `customer_id` 与 `customerId` 匹配 |
| 一个值（`Long`、`String`、...） | 每行的第一列 |

普通 DTO 类不能作为这里的行类型。请使用 record。
MariaDB 和 Oracle 以不同顺序返回 `SELECT *` 的列，因此 lambdaQL 不按位置映射 record 组件。
常见类型会被转换（例如把数字转换为 `Long`/`Integer`，把时间戳转换为 `LocalDateTime`）。

限制：

- 不能对存储过程结果分页。
- 不支持 Oracle 隐式结果（`DBMS_SQL.RETURN_RESULT`）。请使用 `SYS_REFCURSOR` OUT 参数。

<a id="transactions-rule-pr9"></a>
### 事务

- 存储过程中的 DML 是调用方事务的一部分，会一起回滚。
- 调用前 lambdaQL 会 flush 持久化上下文，但仅限于事务处于活动状态且有更改时。
- `QueryBuilder(EntityManagerFactory)` 在自己的事务中执行调用。接收 `EntityManager` 的 `QueryBuilder`（包括 Spring 的共享 `EntityManager`）使用调用方的事务。没有事务时，调用自动提交。
- 调用后已加载的实体可能是过期的。请对这些实体使用 `refresh` 或 `evict`。
- 存储过程中的 `COMMIT` 或 DDL 会破坏事务。lambdaQL 无法阻止。

<a id="affected-rows-rule-pr10"></a>
### 受影响的行（规则 PR10）

存储过程没有通用的“受影响行数”结果（Oracle 返回 -1）。
需要这个数时，请通过 OUT 参数返回。

---

<a id="84-warning-argument-order"></a>
## 8.4 注意：参数顺序

> [!WARNING]
> **存根方法参数的顺序必须与数据库中的顺序相同。**

lambdaQL **按位置**绑定参数。
即使注册了名字，Hibernate 6.6.5 也按位置绑定（实测，7.4 未测）。
MariaDB 和 H2 驱动不支持命名参数。
因此顺序错误时，可能得到**没有任何报错的错误结果**。

第一次调用时 lambdaQL 通过 JDBC 元数据（`getProcedureColumns`）检查的项目如下。结果按存储过程名缓存。

| 检查项目 | 能否发现 |
|---|---|
| 存储过程是否存在 | 能，错误 `LQL-4035` |
| IN·INOUT 参数个数 = 存根参数个数 | 能，错误 `LQL-4036` |
| INOUT 参数 = 加了 `@InOut` 的参数 | 能，错误 `LQL-4036` |
| OUT 值和结果集的个数 = 返回类型的组成部分数 | 能，错误 `LQL-4038` |
| 类型的种类（数字、字符、日期时间、布尔、二进制） | 能，错误 `LQL-4036`。同一种类内部的差异不比对（见下文“参数类型比对”） |
| 相同的参数名以不同顺序排列 | 能，错误 `LQL-4043`。仅限能获得存根参数名时（见下文） |
| Oracle 包存储过程名有多个重载 | 能，错误 `LQL-4044` |

MariaDB、Oracle：

<!-- region: ProcedureTest.java#rules -->
```java
// the stub is checked against the database parameters on the first call
assertEquals("LQL-4036", assertThrows(LambdaQLException.class,
        () -> qb.call(OrderProcs.class, p -> p.calcWrong(1)).execute()).code());
// exec cannot drop OUT values
assertEquals("LQL-4037", assertThrows(LambdaQLException.class,
        () -> qb.exec(OrderProcs.class, p -> p.calc(1, 1.0)).execute()).code());
// only @SqlProcedure methods of the stub can be called
assertEquals("LQL-3060", assertThrows(LambdaQLException.class,
        () -> qb.call(OrderProcs.class, p -> p.toString()).execute()).code());
```

最后一种情况调用的是没有 `@SqlProcedure` 的 `toString()`。因此错误码是 `LQL-3060`（方法不是 `@SqlProcedure`）。

危险情况的示例：

例如数据库中是 `SP_TRANSFER(p_to NUMBER, p_from NUMBER)`，而存根声明为 `void transfer(Long from, Long to)`，顺序就颠倒了。两个参数的类型相同。

个数、模式、类型全部一致。只有名字能暴露这个错误。
因此 lambdaQL 在第一次调用时比较 IN 和 INOUT 参数的名字。

- 存根参数名从类文件中读取。只有用 `-parameters` 编译时名字才会保留（见 [2. 入门](02-getting-started.md#recommended-compiler-option--parameters)）。在存根参数上加 `@org.lambdaql.DbParam("p_to")` 可以直接指定数据库中的名字。
- 从数据库名中去掉**所有**名字共有且以 `_` 结尾的前缀（`p_`、`in_`、`v_` 等）。不假定固定的前缀。然后忽略大小写和 `_`。
- 名字相同而只有顺序不同时，在调用前抛出 `LambdaQLException` `LQL-4043`。
- 名字不同时（存根遵循另一种命名规则）不是错误。请加上 `@DbParam`，使检查生效。
- 类文件中没有名字且相邻两个参数类型相同时，每个存储过程以 `INFO` 级别记录一次 `LQL-5004`。
- 类型只比对种类（见下文“参数类型比对”）。同一种类的两个参数互换时，只能通过名字发现。

数据库存储过程为 `SQ_PROC_DIFF(p_to, p_from)`。示例存根用 `-parameters` 编译：

<!-- region: ProcedureTest.java#names -->
```java
/** Argument names: with -parameters lambdaQL compares them with the database names (p_to, p_from). */
interface DiffProcs {
    @SqlProcedure("SQ_PROC_DIFF")
    Integer diff(Integer to, Integer from);                       // same order as the database: runs

    @SqlProcedure("SQ_PROC_DIFF")
    Integer swapped(Integer from, Integer to);                    // same names, other order: LQL-4043

    @SqlProcedure("SQ_PROC_DIFF")
    Integer named(@DbParam("p_to") Integer target, @DbParam("p_from") Integer source);   // another naming rule

    @SqlProcedure("SQ_PROC_DIFF")
    Integer text(String to, Integer from);                        // a string for a number parameter: LQL-4036
}
```

MariaDB、Oracle：

<!-- region: ProcedureTest.java#name-check -->
```java
assertEquals(7, qb.call(DiffProcs.class, p -> p.diff(10, 3)).execute());
assertEquals(7, qb.call(DiffProcs.class, p -> p.named(10, 3)).execute());
// the same names in another order: stopped before the call (it would run with the values swapped)
assertEquals("LQL-4043", assertThrows(LambdaQLException.class,
        () -> qb.call(DiffProcs.class, p -> p.swapped(3, 10)).execute()).code());
```

**建议：** 用 `-parameters` 编译并使用与数据库相同的名字（共同前缀可以省略），或者加上 `@DbParam`。这样就能检测出颠倒的顺序。

**参数类型比对。** 第一次调用时，比对每个参数的 Java 类型与数据库参数类型的种类。
种类有数字、字符、日期时间、布尔、二进制五种，不同时在调用前以 `LQL-4036` 中止。

- 布尔参数也视为与数字参数相符。MariaDB 的 `BOOLEAN` 是 `TINYINT(1)`，Oracle 23 之前没有 `BOOLEAN` 类型。
- 无法确定种类的一方（enum、对象类型、游标）不比对。
- 同一种类内部的差异（如 `Integer` 参数与 `NUMBER(10,2)` 参数）交给驱动转换。

MariaDB、Oracle：

<!-- region: ProcedureTest.java#type-check -->
```java
// the kind of each argument is compared with the database parameter: a string for a number parameter stops
LambdaQLException wrong = assertThrows(LambdaQLException.class,
        () -> qb.call(DiffProcs.class, p -> p.text("10", 3)).execute());
assertEquals("LQL-4036", wrong.code(), wrong.getMessage());
```

**Oracle 包重载。** Oracle 包中可以有多个同名存储过程。
lambdaQL 无法区分存根指的是哪一个，因此调用这样的名字时以 `LQL-4044` 中止。

（仅 Oracle）。存根 `PkgProcs` 声明了 `@SqlProcedure(pkg = "SQ_PKG", value = "TWICE")` 和 `@SqlProcedure(pkg = "SQ_PKG", value = "HALF")`：

<!-- region: ProcedureTest.java#overload -->
```java
assertEquals(5.0, qb.call(PkgProcs.class, p -> p.twice(2.5)).execute(), 1e-9);
// HALF has two overloads in the package: lambdaQL cannot tell which one to call
assertEquals("LQL-4044", assertThrows(LambdaQLException.class,
        () -> qb.call(PkgProcs.class, p -> p.half(10.0)).execute()).code());
```

<a id="when-the-metadata-cannot-be-read"></a>
**无法读取元数据时。** 数据库用户可能没有读取数据字典视图或 `information_schema` 的权限。
元数据查询本身失败时（`SQLException`），lambdaQL 不中止调用。

- 每个存储过程以 `INFO` 级别记录一次 `LQL-5005`，不检查存根。
- 参数布局取自存根。IN 和 INOUT 参数按存根顺序，之后是 OUT 参数（按结果组成部分的顺序），在 Oracle 中再之后为每个 `List` 组成部分加一个 `REF CURSOR`。因此没有元数据时，数据库声明中 OUT 参数必须位于 IN 参数之后。
- 跳过需要元数据的检查：个数和 `@InOut` 检查（`LQL-4036`）、名字顺序检查（`LQL-4043`）。
- 查询成功但没有该存储过程时，仍然是 `LQL-4035`。
- 只有 Oracle 重载查询（`all_arguments`）失败时，以 `INFO` 级别记录 `LQL-5006`，并在不检查重载的情况下调用。

这种替代行为在 MariaDB 和 Oracle 上有效。

示例代码：[`ProcedureTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java)（`rules`、`names`、`name-check`、`type-check`、`overload`）。

---

<a id="85-procedure-rules-pr1-to-pr15-summary"></a>
## 8.5 存储过程规则 PR1 ~ PR15（汇总）

| # | 规则 | 状态 |
|---|---|---|
| PR1 | 名字：注解的值原样使用。值为空时，方法名转为 UPPER_SNAKE。Oracle 包存储过程写作 `@SqlProcedure(pkg = ..., value = ...)`。 | 已实现。包名在 Oracle 上有效 |
| PR2 | 参数**按位置**绑定。第一次调用时，对照数据库元数据检查 IN/INOUT 参数个数和 `@InOut` 位置。能获得名字时也比对名字，相同名字顺序不同即为错误。也比对参数类型的种类（数字、字符、日期时间、布尔、二进制），不同即为错误（`LQL-4036`）（见 [8.4](#84-warning-argument-order)）。无法读取元数据时跳过检查，参数布局取自存根（见 [8.4](#when-the-metadata-cannot-be-read)）。 | 个数、`@InOut`、名字比对已实现。Oracle 包重载检测也已实现。类型比对也已实现（MariaDB）。无元数据时的替代行为（`LQL-5005`、`LQL-5006`）已实现（MariaDB）。 |
| PR3 | 1 个 OUT → 返回值。多个 OUT/INOUT → record。`@InOut` 标记 INOUT 参数。 | 已实现 |
| PR4 | 1 个结果集 → `List<T>`。实体通过 Hibernate 读取，record 通过 JDBC 按标签匹配读取。不能分页。 | 已实现 |
| PR5 | 不支持 Oracle 隐式结果（`DBMS_SQL.RETURN_RESULT`）。请使用 `SYS_REFCURSOR` OUT 参数。 | – |
| PR6 | 单独调用的数据库函数在不同数据库上使用不同的路径（见 [8.8](#88-call-a-db-function-alone)）。 | 已实现 |
| PR7 | 查询中的数据库函数：见 [8.6](#86-declare-db-functions) 和规则 FN1 ~ FN11。 | 已实现 |
| PR8 | 条件中在列上使用数据库函数时无法使用索引，因此记录 WARN（见 [8.9](#89-warning-functions-on-columns-and-indexes-sargable)）。错误码为 `LQL-5001`。 | 已实现。在 `SargableMode.STRICT` 下为错误（`LQL-3066`） |
| PR9 | 存储过程在调用方的事务中执行。需要时 lambdaQL 在调用前 flush。调用后已加载的实体可能是过期的。 | 已实现 |
| PR10 | 没有通用的受影响行数。请使用 OUT 参数。 | – |
| PR11 | IN 值为 `null` 没有问题。不能使用数据库参数默认值。 | 已实现 |
| PR12 | Lambda 表达式是一次存根调用。参数是捕获值、常量或它们的计算。有 OUT 参数时 `exec` 是错误。 | 已实现 |
| PR13 | 在参数类型比对中，布尔参数也视为与数字参数相符（[8.4](#84-warning-argument-order) 的“参数类型比对”）。 | 已实现 |
| PR14 | 每个 Lambda 表达式只分析一次。存储过程元数据每个名字只读取一次。 | 已实现 |
| PR15 | H2 没有 OUT、INOUT、多结果集、包。请在 MariaDB 或 Oracle 上测试存储过程（例如使用 Testcontainers）。 | – |

PR11、PR12、PR13 还有 Kotlin 专属部分（默认参数、命名参数、可空类型），见[本页的 Kotlin 版本](../kotlin/08-procedures-functions.md)。

---

<a id="86-declare-db-functions"></a>
## 8.6 声明数据库函数

数据库函数是直接在数据库中创建的函数，例如 `FN_DISCOUNT(price)`。
用带有 `@SqlFunction` 的**接口 static 方法**声明。
方法体不会被调用，只应抛出异常。

<!-- region: FunctionTest.java#declare -->
```java
/** Database functions: static methods of your own interface. The bodies only throw. */
public interface OrderFns {
    /** {@code SQ_FN_DISCOUNT(price)}: 90% of the price. */
    @SqlFunction("SQ_FN_DISCOUNT")
    static Double discount(Double price) {
        throw new DbOnlyException();
    }

    /** {@code SQ_FN_IS_GOLD(grade)}: a grade of 5 or more. */
    @SqlFunction("SQ_FN_IS_GOLD")
    static Boolean isGold(Integer grade) {
        throw new DbOnlyException();
    }

    /** {@code SQ_FN_NONDET(value)}: on MariaDB declared NOT DETERMINISTIC (SARGable test). */
    @SqlFunction("SQ_FN_NONDET")
    static Double nondet(Double value) {
        throw new DbOnlyException();
    }

    /** A query name with its own SQL name; registered with lambdaql.functions.classes. */
    @SqlFunction(value = "SQ_TAX", sql = "SQ_FN_DISCOUNT")
    static Double tax(Double price) {
        throw new DbOnlyException();
    }
}
```

数据库中必须预先有 `SQ_FN_DISCOUNT` 和 `SQ_FN_IS_GOLD` 函数。Boolean 结果需要 Oracle 23ai 及以上版本。

在 lambdaQL Lambda 表达式**之外**调用 `OrderFns.discount(...)` 会抛出 `DbOnlyException`（`LQL-4034`）。这是有意的行为。

`@SqlFunction` 有两个值。

| 值 | 含义 |
|---|---|
| `value` | 查询中使用的函数名。为空时方法名转为 UPPER_SNAKE（`isVip` → `IS_VIP`）。也允许带模式名（`PUBLIC.SQ_FN`） |
| `sql` | 与 `value` 不同时的 SQL 名。这样的函数需要注册（见[注册](#registration-optional)） |

其他声明方式：

| 声明 | 状态 |
|---|---|
| 接口 static 方法 | **推荐** |
| repository `implements` 函数接口（mixin） | 拒绝 |
| 函数式接口常量（`DISCOUNT.apply(x)`） | 拒绝 |

请**带上接口名**调用函数（`OrderFns.discount`）。static import 也可以，但同名的成员方法可能会遮蔽它。

示例代码：[`FunctionTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java)（`declare`）。

---

<a id="87-use-db-functions-in-queries"></a>
## 8.7 在查询中使用数据库函数

直接调用函数即可，不需要上下文参数。
可用于 `where`、`select`、`orderBy`、`set`、`having`、聚合函数的参数和子查询（见下面的 `function-group`、`function-sub` 示例）。

<!-- region: FunctionTest.java#where -->
```java
// the function call is written as function('<name>' as <type>, ...); the column is its argument
SelectQuery<Order> q = qb.from(Order.class).where(o -> OrderFns.discount(o.getPrice()) > 100);
assertEquals("select e0 from Order e0 where function('SQ_FN_DISCOUNT' as Double, e0.price) > 100.0", q.toHql().hql());
```

<!-- region: FunctionTest.java#captured -->
```java
// a function of captured values only is still computed by the database (the value is bound)
SelectQuery<Order> q = qb.from(Order.class).where(o -> o.getPrice() > OrderFns.discount(base));
assertEquals("select e0 from Order e0 where e0.price > function('SQ_FN_DISCOUNT' as Double, :p1)", q.toHql().hql());
```

Boolean 函数可以作为整个条件使用。lambdaQL 会加上 `= true`。

<!-- region: FunctionTest.java#boolean -->
```java
SelectQuery<Order> q = qb.from(Order.class).where(o -> OrderFns.isGold(o.getCustomer().getGrade()));
assertEquals("select e0 from Order e0 join e0.customer j1 where function('SQ_FN_IS_GOLD' as Boolean, j1.grade) = true",
        q.toHql().hql());
```

`select` 和 `orderBy`：

<!-- region: FunctionTest.java#select -->
```java
ValueQuery<Priced> q = qb.from(Order.class)
        .orderBy(o -> desc(OrderFns.discount(o.getPrice())))
        .select(o -> new Priced(o.getId(), OrderFns.discount(o.getPrice())));
assertEquals("select e0.id, function('SQ_FN_DISCOUNT' as Double, e0.price) from Order e0"
        + " order by function('SQ_FN_DISCOUNT' as Double, e0.price) desc nulls last", q.toHql().hql());
```

`Priced` 是 `record Priced(Long id, Double discounted)`。

<!-- region: FunctionTest.java#update -->
```java
ExecutableUpdate<Order> u = qb.update(Order.class)
        .set(o -> o.setPrice(OrderFns.discount(o.getPrice())))
        .where(o -> o.getPrice() > 100);
assertEquals("update Order e0 set e0.price = function('SQ_FN_DISCOUNT' as Double, e0.price) where e0.price > 100.0",
        u.toHql().hql());
```

各查询的结果行与用 Java 进行同样计算（`price * 0.9`）的结果相同。

在聚合函数的参数、`having`、子查询中也是同样的形式。下面示例中的 `Discounted` 是 `record Discounted(Integer customerId, Double total)`。

<!-- region: ManualJavaFunctionTest.java#function-group -->
```java
// a database function inside an aggregate, in having and in select
ValueQuery<Discounted> q = qb.from(Order.class)
        .where(o -> o.getCustomer() != null)
        .groupBy(o -> o.getCustomer().getId())
        .having(o -> Agg.sum(OrderFns.discount(o.getPrice())) > 200)
        .select(o -> new Discounted(o.getCustomer().getId(), Agg.sum(OrderFns.discount(o.getPrice()))));
assertEquals("select e0.customer.id, sum(function('SQ_FN_DISCOUNT' as Double, e0.price)) from Order e0"
        + " where e0.customer is not null group by e0.customer.id"
        + " having sum(function('SQ_FN_DISCOUNT' as Double, e0.price)) > 200.0", q.toHql().hql());
List<Discounted> rows = q.toList();
```

<!-- region: ManualJavaFunctionTest.java#function-sub -->
```java
SelectQuery<Customer> q = qb.from(Customer.class).where((c, sub) -> sub.from(Order.class)
        .where(o -> o.getCustomer().getId() == c.getId() && OrderFns.discount(o.getPrice()) > 100)
        .exists());
assertEquals("select e0 from Customer e0 where exists (select 1 from Order s1"
        + " where s1.customer.id = e0.id and function('SQ_FN_DISCOUNT' as Double, s1.price) > 100.0)", q.toHql().hql());
```

设置 `lambdaql.functions.classes` 后，即使是未注册的函数，Hibernate 也会保留名字的大小写（`FN_DISCOUNT`）（在三种数据库上观察到，原因尚未确认）。
完全没有注册时，Hibernate 可能把名字写成小写。不带引号的名字两种情况下都有效。

<a id="how-lambdaql-renders-it"></a>
### lambdaQL 的渲染方式

lambdaQL **总是**按如下形式渲染数据库函数。

`function('<NAME>' as <Type>, arg1, arg2, ...)`

`<Type>` 取自声明的返回类型。只允许以下类型。

| 允许的返回类型 |
|---|
| `Double`、`Integer`、`Long`、`BigDecimal`、`String`、`LocalDate`、`LocalDateTime`、`Boolean` |

泛型返回（`<X> X nvl(X, X)`）、实体、集合、`void` 在分析时被拒绝。

<a id="registration-optional"></a>
### 注册（可选）

`function('FN_DISCOUNT' as Double, ...)` 形式不需要注册（已在三种数据库上确认）。
注册函数接口后，Hibernate 还会保留名字的大小写，并检查参数个数和类型。

lambdaQL 提供 Hibernate `FunctionContributor` `org.lambdaql.hibernate.LambdaQlFunctionContributor`（通过 ServiceLoader 发现）。在 Hibernate 属性中列出接口。

```properties
# 普通 Hibernate
lambdaql.functions.classes=com.acme.OrderFns,com.acme.CustomerFns

# Spring Boot
spring.jpa.properties.lambdaql.functions.classes=com.acme.OrderFns
```

不设置该属性时不注册任何内容。不支持包扫描。
嵌套接口写作带 `$` 的二进制名。例如 `com.acme.Fns` 中的 `OrderFns` 写作 `com.acme.Fns$OrderFns`。

<a id="a-different-sql-name-needs-registration"></a>
### SQL 名不同的函数的注册

`value` 是查询中的名字，`sql` 是 SQL 中的名字。两个名字不同时必须注册函数。
这对用引号创建的大小写混合的函数名（`"SQ_Fn_Mixed"`）很有用。不注册时，在 H2 和 Oracle 上名字可能变成小写。

声明写作 `@SqlFunction(value = "SQ_Fn_Mixed", sql = "\"SQ_Fn_Mixed\"")`。上面声明中的 `OrderFns.tax`（`value = "SQ_TAX"`、`sql = "SQ_FN_DISCOUNT"`）也是同样的形式。

<!-- region: FunctionTest.java#registered -->
```java
// SQ_TAX is registered at start-up (lambdaql.functions.classes) and runs SQ_FN_DISCOUNT in SQL
SelectQuery<Order> q = qb.from(Order.class).where(o -> OrderFns.tax(o.getPrice()) > 100);
assertEquals("select e0 from Order e0 where function('SQ_TAX' as Double, e0.price) > 100.0", q.toHql().hql());
```

这样的函数没有注册时，lambdaQL 在读取 Lambda 表达式时失败（`LQL-3058`，见 [8.10](#810-function-rules-fn1-to-fn11-summary)）。

示例代码：[`FunctionTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java)（`registered`）、[`FunctionTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java)（`where`、`captured`、`boolean`、`select`、`update`）、[`ManualJavaFunctionTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ManualJavaFunctionTest.java)（`function-group`、`function-sub`）。

---

<a id="88-call-a-db-function-alone"></a>
## 8.8 单独调用数据库函数

用**无参数的 Lambda 表达式**调用 `qb.call(() -> ...)`。

H2、MariaDB、Oracle：

<!-- region: ProcedureTest.java#function -->
```java
Double half = qb.call(() -> MathFns.half(price)).execute();
```

`price` 是捕获的 `double`（150），`half` 是 75.0。Lambda 表达式必须是一次 `@SqlFunction` 调用（`LQL-3063`）。

该查询以没有 FROM 的 `select FN_DISCOUNT(?)` 执行。Oracle 从 23ai 起才接受这种写法。在 Oracle 上 lambdaQL 改用 JDBC 函数调用（见下表）。

参数个数不同，因此不会与 `qb.call(OrderProcs.class, p -> ...)` 冲突。

lambdaQL 的执行方式：

| 数据库 | 路径 |
|---|---|
| Oracle | JDBC 函数调用 `{? = call FN(?)}`。对执行 DML 的函数和返回游标的函数也有效。 |
| MariaDB、H2 | HQL `select function('FN' as T, :p1)` |

lambdaQL 先 flush 待处理的更改（与过程调用相同），因此函数能看到最新的更改。

示例代码：[`ProcedureTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java)（`function`）。

---

<a id="89-warning-functions-on-columns-and-indexes-sargable"></a>
## 8.9 注意：列上的函数与索引（SARGable）

lambdaQL 力求生成**能够使用索引**的 SQL（见 [10. 规则与限制](10-rules-and-limits.md#102-sargable-rule-index-friendly-sql)）。
包裹列的函数通常**无法使用**普通索引。

[8.7](#87-use-db-functions-in-queries) 的 `where` 示例 `OrderFns.discount(o.getPrice()) > 100` 会变成 `function('SQ_FN_DISCOUNT' as Double, e0.price) > 100.0`。这个条件不使用 `price` 上的索引。

因此 lambdaQL 对这种形式记录 **WARN**。
检查在每个 `where` Lambda 表达式缓存分析结果时执行一次。没有 `@SqlFunction` 的条件不查询数据库。

| 情况 | 代码 | H2 | MariaDB | Oracle |
|---|---|---|---|---|
| ① 函数包裹列：`fn(o.price) > 10`、作用于列的 Boolean 函数、`IN` 或 `IS NULL` 中的函数 | `LQL-5001`（WARN） | WARN | WARN（存储函数不能用于索引） | WARN。**但**如果 `user_ind_expressions` 中该表有相同表达式 `FN(COLUMN)` 的函数索引，则不记录。列类型与函数参数类型不同时 Oracle 插入的数字转换（`FN(TO_NUMBER(COLUMN))` 等）也视为相同表达式 |
| ② 值一侧的函数与列比较：`o.price > fn(rate)` | `LQL-5007`（WARN） | 不检查 | `information_schema.routines.is_deterministic = 'NO'`（默认值！）时 WARN。此时 MariaDB 扫描整个索引 | 不检查（Oracle 两种情况都是范围扫描） |

- Oracle 索引比对所用的表名和列名取自 Hibernate 映射。只比对只有一个列参数的函数。
- 元数据查询失败时（例如没有权限），以 `INFO` 级别记录 `LQL-5008`。① 照常警告，② 不检查。

记录的警告：H2 为 `LQL-5001`，MariaDB 为 `LQL-5001` 和 `LQL-5007`，有 `SQ_FN_DISCOUNT(price)` 函数索引的 Oracle 没有警告：

<!-- region: FunctionTest.java#sargable -->
```java
// the function wraps the column: an index on price cannot be used (LQL-5001 at WARN),
// unless Oracle has a function-based index on SQ_FN_DISCOUNT(price)
qb.from(Order.class).where(o -> OrderFns.discount(o.getPrice()) > 100).toHql();
// the function is on the value side: fine, unless MariaDB says NOT DETERMINISTIC (LQL-5007 at WARN)
qb.from(Order.class).where(o -> o.getPrice() > OrderFns.nondet(base)).toHql();
```

**SARGable 模式**。`SargableMode` 有三个值。

- `OFF`：不检查，也不查询元数据。
- `WARN`（默认值）：记录警告。
- `STRICT`：在 `WARN` 模式下会记录警告的情况（`LQL-5001`、`LQL-5007`、`LQL-5012`），分析以 `org.lambdaql.NonSargableException`（`LQL-3066`）失败。

按 persistence unit 用 `SargablePolicy.register(emf, SargableMode.STRICT)` 指定，用 `SargablePolicy.unregister(emf)` 解除。在 Spring 中使用 `lambdaql.sargable.mode=STRICT`（见 [12](12-spring-data-jpa.md)）。
检查在每个 Lambda 表达式的分析结果放入共享缓存时执行一次。因此请在第一次查询之前注册模式。

**列上的算术**。

- 只由一个列和值构成的 `+ - * /`（`o.getCnt() * 2 > n`）可以移到值一侧。lambdaQL 以 `WARN` 级别记录 `LQL-5012`，在 `STRICT` 下为错误。
- 列之间的算术（`o.getPrice() * o.getCnt() > n`）和 `%` 无法移动，因此只以 `DEBUG` 级别记录 `LQL-5013`。在 `STRICT` 下也允许。
- 移动后的形式必须语义相同。请像 `o.getCnt() > n / 2.0` 这样用小数除。整数除法 `n / 2` 在 `>=`、`=`、`<` 和负数 `n` 时会得到不同的行。乘以负数时请反转比较方向。`LQL-5012` 的消息会给出提示。
- 没有采用通过查询是否存在索引来减少警告的方式。因为权限和同义词会使“没有索引”与“未知”混在一起，而且列上的算术是少见的形式。

<!-- region: FunctionTest.java#sargable-strict -->
```java
// STRICT: what WARN would log fails the analysis (NonSargableException, LQL-3066)
SargablePolicy.register(emf, SargableMode.STRICT);
NonSargableException fn = assertThrows(NonSargableException.class,
        () -> qb.from(Order.class).where(o -> OrderFns.discount(o.getTotal()) > 100).toHql());
assertEquals("LQL-3066", fn.code());
assertThrows(NonSargableException.class, () -> qb.from(Order.class).where(o -> o.getCnt() * 2 > n).toHql());
// the same meaning on the value side uses the index; divide by a decimal (n / 2 is integer division)
var moved = qb.from(Order.class).where(o -> o.getCnt() > n / 2.0).toHql();
assertEquals("select e0 from Order e0 where e0.cnt > 2.5BD", moved.hql());   // 2.5, never cut to 2
// arithmetic between columns cannot move, so STRICT allows it
qb.from(Order.class).where(o -> o.getCnt() * o.getStatus() > n).toHql();
```

**建议：**

- 尽可能把函数放在值一侧：`o.getPrice() > OrderFns.discount(base)`。
- 在 MariaDB 中，如果函数确实是确定性的，请声明为 `DETERMINISTIC`。
- 在 Oracle 中，有相同表达式的函数索引时警告会消失（这种索引需要 `DETERMINISTIC` 函数）。

示例代码：[`FunctionTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java)（`sargable`、`sargable-strict`）。

---

<a id="810-function-rules-fn1-to-fn11-summary"></a>
## 8.10 函数规则 FN1 ~ FN11（汇总）

| # | 规则 | 状态 |
|---|---|---|
| FN1 | `@SqlFunction` 是方法注解。值为空时方法名转为 UPPER_SNAKE。 | 已实现 |
| FN2 | lambdaQL 找到被调用的方法并读取注解。方法体只应抛出异常（`throw new ...`）。有实际方法体的方法在 Java 和数据库中可能得出不同的值，因此是错误（`LQL-3052`）。 | 已实现 |
| FN3 | 同名的所有重载必须对应同一个数据库函数，否则是错误（`LQL-3053`）。 | 已实现 |
| FN4 | 没有注解的调用或 lambdaQL 无法识别的调用是错误，不会静默跳过。 | 已实现 |
| FN5 | 参数遵循 [3. 条件](03-where.md)的值规则。只由捕获值构成的参数也会被绑定，在**数据库中**求值。 | 已实现 |
| FN6 | 跳过装箱调用。返回类型取自声明的方法，必须是 `Double`、`Integer`、`Long`、`BigDecimal`、`String`、`LocalDate`、`LocalDateTime`、`Boolean` 之一（`LQL-3055`）。 | 已实现 |
| FN7 | 列上的函数产生 WARN（见 [8.9](#89-warning-functions-on-columns-and-indexes-sargable)）。在 MariaDB 中，值一侧的 `NOT DETERMINISTIC` 函数也产生 WARN（`LQL-5007`）。 | 已实现。在 `SargableMode.STRICT` 下为错误（`LQL-3066`） |
| FN8 | 与 Hibernate 内置函数（`COALESCE`、`ROUND`、`SYSDATE`、...）同名是错误（`LQL-3054`），因为 Hibernate 会静默使用内置函数。请使用前缀或模式名（`PUBLIC.SQ_FN`）。 | 已实现 |
| FN9 | Kotlin 专属（默认参数、`vararg`）。见 Kotlin 路线。 | 默认参数和 `vararg` 已实现 |
| FN10 | 只支持标量函数。不支持用户聚合函数和窗口函数。无参数函数渲染为 `fn()`。 | 标量函数已实现 |
| FN11 | 已知的数据库差异：在 MariaDB 中返回 VARCHAR 的函数可能与字符串字面量发生排序规则冲突。`BigDecimal` 结果的标度不同（MariaDB 和 Oracle 为 0，H2 为 1）。boolean 结果需要 Oracle 23ai 及以上版本。 | 已用原型实测 |

<!-- region: FunctionTest.java#rules -->
```java
// the body never runs in Java
assertEquals("LQL-4034", assertThrows(DbOnlyException.class, () -> OrderFns.discount(1.0)).code());
// a body that computes a value is rejected (Java and the database could give different values)
assertEquals("LQL-3052", assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(o -> BadFns.computes(o.getPrice()) > 1)).code());
// a Hibernate built-in name would silently run the built-in function
assertEquals("LQL-3054", assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(o -> BadFns.builtin(o.getPrice()) > 1)).code());
// a function with its own SQL name must be registered
assertEquals("LQL-3058", assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(o -> BadFns.unregistered(o.getPrice()) > 1)).code());
// the return type must have an HQL type
assertEquals("LQL-3055", assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(o -> BadFns.anything(o.getPrice()) != null)).code());
```

`BadFns` 是示例中的接口，每种情况放一个错误的函数：返回 `price` 的方法体、名字 `COALESCE`、未注册的 `value = "SQ_OTHER"`·`sql = "SQ_FN_DISCOUNT"`、返回类型 `Object`，共四种。

示例代码：[`FunctionTest.java`](https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java)（`rules`）。

---

<a id="811-database-support"></a>
## 8.11 数据库支持情况

| 功能 | H2 2.3 | MariaDB 10.11 | Oracle 23ai |
|---|---|---|---|
| IN 参数 | 是 | 是 | 是 |
| OUT / INOUT | **否**（H2 限制） | 是 | 是 |
| 1 个结果集 | 是（`ResultSet` 函数） | 是（存储过程中的 `SELECT`） | 是（`SYS_REFCURSOR` OUT） |
| 包名（`PKG.PROC`） | 否 | 否（需要 `sql_mode=ORACLE`） | 是 |
| 查询中的数据库函数 | 是 | 是 | 是 |
| 单独调用数据库函数 | 是 | 是 | 是 |
| lambdaQL 的存储过程调用 | – | 是 | 是 |
| boolean 结果的数据库函数 | 是 | 是 | 是（23ai 及以上） |

不支持的内容：动态存储过程名、选择 Oracle 包存储过程的某一个重载进行调用（重载检测已实现，`LQL-4044`）、表值函数、存储过程结果分页。

本章的错误是带有 `[LQL-xxxx]` 错误码的 `LambdaQLException`（或其子类）。读取 Lambda 表达式时为 `LQL-3052`～`LQL-3063`，以及 `SargableMode.STRICT` 下无法使用索引的条件的 `LQL-3066`（`NonSargableException`，见 [8.9](#89-warning-functions-on-columns-and-indexes-sargable)）。执行调用时为 `LQL-4034`～`LQL-4039`、`LQL-4043`、`LQL-4044`。调用过程中也可能以 `INFO` 级别记录 `LQL-5004`、`LQL-5005`、`LQL-5006`。对于条件中的数据库函数，可能记录 `LQL-5001`、`LQL-5007`（`WARN`）和 `LQL-5008`（`INFO`）。见 [11. 错误与语言](../11-errors-and-languages.md)。

---

[Java 路线](README.md) | 上一章：[7. 更新、插入、删除](07-dml.md) | 下一章：[9. Java 说明](09-java-notes.md) | [本页的 Kotlin 版本](../kotlin/08-procedures-functions.md)
