<a id="8-stored-procedures-and-db-functions-java"></a>
# 8. 存储过程与数据库函数（Java）

> [!IMPORTANT]
> 本页比韩文手册旧。最新规则请参见[韩文页面](../../ko/java/08-procedures-functions.md)。

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

<a id="contents"></a>
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

**(已测试)** [`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java)，region `declare`：

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

测试在运行前会在 MariaDB 和 Oracle 上创建这些存储过程。例如（MariaDB）：

```sql
CREATE OR REPLACE PROCEDURE SQ_PROC_CALC(IN p_cust INT, IN p_rate DOUBLE, OUT p_total DOUBLE)
  BEGIN SELECT COALESCE(SUM(price), 0) * p_rate INTO p_total FROM SQ_orders WHERE customer_id = p_cust; END
```

`calcWrong` 是故意写错的。测试用它检查规则 PR2（见 [8.4](#84-warning-argument-order)）。

说明：

- `@SqlProcedure`（或 `@SqlFunction`）是**必需的**。它告诉 lambdaQL 这个方法是什么。
  （没有使用 `@Procedure` 和 `@Function` 这两个名字：`@Procedure` 也是 Spring Data JPA 的注解，而 `@Function` 与 Kotlin 的 `kotlin.Function` 冲突。）
- 如果值为空，名字取自方法名：`calcTotal` → `CALC_TOTAL`。
- `@SqlProcedure` 还有 `schema` 和 `pkg`（Oracle 包）：`@SqlProcedure(value = "CALC", schema = "LQL", pkg = "SQ_PKG")`。
- 请使用装箱的返回类型（`Double`、`Integer`）。存储过程可能返回 NULL。
- 接口和结果 record 是数据库签名的副本。这是类型安全的代价。lambdaQL 会对照数据库检查其中一部分（规则 PR2）。
- 存储过程（抽象方法）和数据库函数（静态方法）可以放在**同一个接口**中。

---

<a id="82-call-procedures"></a>
## 8.2 调用存储过程

使用 `qb.call(Interface.class, p -> p.method(args))`，然后调用 `execute()`。

**(已测试)** [`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java)，region `call`（MariaDB 和 Oracle）：

```java
Double total = qb.call(OrderProcs.class, p -> p.calc(customerId, rate)).execute();
Stat stat = qb.call(OrderProcs.class, p -> p.stat(customerId)).execute();
Adjusted adjusted = qb.call(OrderProcs.class, p -> p.adjust(5, 10)).execute();
List<Line> recent = qb.call(OrderProcs.class, p -> p.recent(customerId)).execute();
```

`customerId` 和 `rate` 是捕获的局部值。测试把每个结果与用 Java 进行的同样计算作比较。例如，`adjusted` 是 `new Adjusted(15, "ok")`。

没有结果（`void`）的存储过程使用 `exec`：

**(已测试)** [`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java)，region `exec`（MariaDB 和 Oracle）：

```java
qb.exec(OrderProcs.class, p -> p.purge(9)).execute();
```

为什么 void 用 `exec` 而不是 `call`？

- 在 Java 中，两者都用 `call` 会成为有歧义的重载（编译错误）。
- 没有选择 `run`：Kotlin 标准库中有同名函数，Kotlin 调用方可能会在没有任何错误的情况下误调用它。

<a id="what-the-lambda-may-contain-rule-pr12"></a>
### Lambda 中可以包含什么（规则 PR12）

Lambda 体是**对存根方法的一次调用**。除此之外什么都没有。

| 允许 | 不允许 |
|---|---|
| `p -> p.calc(id, rate)` | 日志、两次调用、`if`、嵌套调用 |
| 参数：捕获值、常量以及它们的计算（`rate * 2`） | 实体路径（这里没有实体） |

其他要点：

- 对带有 OUT 参数的存储过程使用 `exec` 是错误（`LQL-4037`）。这样可以防止 OUT 值被意外丢弃。
- 调用没有 `@SqlProcedure` 的方法是错误（`LQL-3060`）。
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
| 多个 OUT / INOUT 参数 | Java record（Kotlin：data class） |
| 1 个结果集（MariaDB 的 `SELECT`、Oracle 的 `SYS_REFCURSOR` OUT、H2 的 `ResultSet` 函数） | `List<T>` |
| 多个结果集 | 带 `List` 组件的 record |
| OUT 值和结果集（MariaDB） | 带标量组件和 `List` 组件的 record |

record 组件的填充方式：

- **标量组件** ← OUT 和 INOUT 参数（游标除外），按数据库中的顺序。
- **`List` 组件** ← 结果集，按顺序（Oracle：游标 OUT 参数的顺序；MariaDB：返回结果集的顺序）。
- 如果数量不匹配，lambdaQL 会抛出错误（`LQL-4038`）。
- 像 IN、OUT、IN、INOUT 这样混合的位置没有问题。lambdaQL 从数据库元数据中读取位置。

### INOUT

用 `@InOut` 标记参数。你传入的值**进入**存储过程。**出来**的值是返回 record 的一个组件。

测试中的 `SQ_PROC_ADJUST(p_add IN, p_balance IN OUT, p_msg OUT)` 把 `p_add` 加到 `p_balance` 上：

```java
@SqlProcedure("SQ_PROC_ADJUST")
Adjusted adjust(Integer add, @InOut Integer balance);         // IN, INOUT, OUT

Adjusted adjusted = qb.call(OrderProcs.class, p -> p.adjust(5, 10)).execute();
// adjusted.balance() 是 15（新的余额），adjusted.msg() 是 "ok"
```

`@InOut` 必须恰好标在数据库中为 INOUT 的参数上。否则第一次调用会失败（`LQL-4036`）。

<a id="result-set-element-type-t"></a>
### 结果集的元素类型 `T`

| `T` 是 | lambdaQL 的读取方式 |
|---|---|
| 实体 | Hibernate 结果类（`StoredProcedureQuery`） |
| Java record（Kotlin：data class） | JDBC `CallableStatement`，**按标签**匹配列：去掉 `_` 并忽略大小写，所以 `customer_id` 与 `customerId` 匹配（已用 `Line` 测试） |
| 一个值（`Long`、`String` 等） | 每行的第一列 |

普通 DTO 类不能作为这里的行类型。请使用 record。
lambdaQL 从不按位置映射 record 组件，因为 MariaDB 和 Oracle 返回 `SELECT *` 的列顺序不同。
它会转换常见类型（例如把数字转换为 `Long`/`Integer`，把时间戳转换为 `LocalDateTime`）。

限制：

- 无法对存储过程的结果分页。
- 不支持 Oracle 的隐式结果（`DBMS_SQL.RETURN_RESULT`）。请使用 `SYS_REFCURSOR` OUT 参数。

<a id="transactions-rule-pr9"></a>
### 事务（规则 PR9）

- 存储过程中的 DML 是调用方事务的一部分，会随之回滚。
- 调用之前，lambdaQL 会刷新持久化上下文，但仅在有活动事务且存在更改时才这样做。
- `QueryBuilder(EntityManagerFactory)` 在自己的事务中执行调用。带 `EntityManager` 的 `QueryBuilder`（包括 Spring 的共享 EntityManager）使用调用方的事务。没有事务时，调用是自动提交的。
- 调用之后，已加载的实体可能是过期的。请对这些实体使用 `refresh` 或 `evict`。
- 存储过程中的 `COMMIT` 或 DDL 会破坏事务。lambdaQL 无法阻止这种情况。

<a id="affected-rows-rule-pr10"></a>
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

lambdaQL 在第一次调用时检查的内容（来自 JDBC 元数据 `getProcedureColumns`）。结果按存储过程名缓存：

| 检查项 | 能发现吗？ |
|---|---|
| 存储过程存在 | 能，报错 `LQL-4035` |
| IN 和 INOUT 参数的个数 = 存根参数的个数 | 能，报错 `LQL-4036`（已测试） |
| INOUT 参数 = 带 `@InOut` 的参数 | 能，报错 `LQL-4036` |
| OUT 值和结果集的个数 = 返回类型的组成部分 | 能，报错 `LQL-4038` |
| 类型的种类（数字、字符、日期时间、布尔、二进制） | 是，错误 `LQL-4036`（已测试）。同一种类内部的差异不比对（见下文“参数类型比对”） |
| 相同的参数名以另一种顺序排列 | 是，错误 `LQL-4043`（已测试）。仅当能获得存根参数名时（见下文） |
| Oracle 包存储过程名有多个重载 | 是，错误 `LQL-4044`（已在 Oracle 上测试） |

**(已测试)** [`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java)，region `rules`（MariaDB 和 Oracle）：

```java
// PR2: the stub is checked against the database parameters on the first call
assertEquals("LQL-4036", assertThrows(LambdaQLException.class,
        () -> qb.call(OrderProcs.class, p -> p.calcWrong(1)).execute()).code());
// exec cannot drop OUT values
assertEquals("LQL-4037", assertThrows(LambdaQLException.class,
        () -> qb.exec(OrderProcs.class, p -> p.calc(1, 1.0)).execute()).code());
// only @SqlProcedure methods of the stub can be called
assertEquals("LQL-3060", assertThrows(LambdaQLException.class,
        () -> qb.call(OrderProcs.class, p -> p.toString()).execute()).code());
```

最后一种情况调用的是没有 `@SqlProcedure` 的 `toString()`。所以错误码是 `LQL-3060`（该方法不是 `@SqlProcedure`）。

危险情况的示例：

```java
// 数据库：SP_TRANSFER(p_to NUMBER, p_from NUMBER)
@SqlProcedure("SP_TRANSFER") void transfer(Long from, Long to);   // 顺序反了！类型相同
```

个数、模式和类型全部匹配。只有名字能暴露这个错误。
因此 lambdaQL 在第一次调用时比较 IN 和 INOUT 参数的名字：

- 存根参数名从类文件中读取。只有用 `-parameters` 编译时名字才会保留（见 [2. 入门](02-getting-started.md#recommended-compiler-option--parameters)）。在存根参数上加 `@org.lambdaql.DbParam("p_to")` 可以直接给出数据库中的名字。
- lambdaQL 从数据库参数名中去掉一个以 `_` 结尾、并且**所有**名字共有的前缀（`p_`、`in_`、`v_` 等）。不假定固定的前缀。然后忽略大小写和 `_`。
- 名字相同但顺序不同：在调用之前抛出 `LambdaQLException` `LQL-4043`。
- 名字不同（存根遵循另一种命名规则）：不报错。请加上 `@DbParam`，让检查仍然有效。
- 类文件中没有名字，并且两个相邻参数类型相同：每个存储过程以 `INFO` 级别记录一次 `LQL-5004`。
- 类型只比对种类（见下文“参数类型比对”）。两个同一种类的参数互换时，只能通过名字发现。

**(已测试)** [`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java)，region `names`（数据库存储过程是 `SQ_PROC_DIFF(p_to, p_from)`；测试存根用 `-parameters` 编译）：

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

**(已测试)** [`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java)，region `name-check`（MariaDB 和 Oracle）：

```java
assertEquals(7, qb.call(DiffProcs.class, p -> p.diff(10, 3)).execute());
assertEquals(7, qb.call(DiffProcs.class, p -> p.named(10, 3)).execute());
// the same names in another order: stopped before the call (it would run with the values swapped)
assertEquals("LQL-4043", assertThrows(LambdaQLException.class,
        () -> qb.call(DiffProcs.class, p -> p.swapped(3, 10)).execute()).code());
```

**建议：**用 `-parameters` 编译，并使用与数据库相同的名字（共同前缀可以省略），或者加上 `@DbParam`。这样就能发现顺序被交换的错误。

**参数类型比对。** 第一次调用时，lambdaQL 比对每个参数的 Java 类型与数据库参数类型的种类。
种类有数字、字符、日期时间、布尔、二进制五种。种类不同时，在调用之前以 `LQL-4036` 停止。

- 布尔参数也视为与数字参数相符。MariaDB 的 `BOOLEAN` 是 `TINYINT(1)`，Oracle 23 之前没有 `BOOLEAN` 类型。
- 无法确定种类的一方（enum、对象类型、游标）不比对。
- 同一种类内部的差异（例如 `Integer` 参数与 `NUMBER(10,2)` 数据库参数）交给驱动转换。

**(已测试)** [`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java)，region `type-check`（MariaDB 和 Oracle）：

```java
// the kind of each argument is compared with the database parameter: a string for a number parameter stops
LambdaQLException wrong = assertThrows(LambdaQLException.class,
        () -> qb.call(DiffProcs.class, p -> p.text("10", 3)).execute());
assertEquals("LQL-4036", wrong.code(), wrong.getMessage());
```

**Oracle 包重载。** Oracle 包中可以有多个同名的存储过程。
lambdaQL 无法区分存根指的是哪一个，因此调用这样的名字会以 `LQL-4044` 停止。选择并调用某一个重载计划在以后提供。

**(已测试)** [`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java)，region `overload`（仅 Oracle）。存根 `PkgProcs` 声明了 `@SqlProcedure(pkg = "SQ_PKG", value = "TWICE")` 和 `@SqlProcedure(pkg = "SQ_PKG", value = "HALF")`：

```java
assertEquals(5.0, qb.call(PkgProcs.class, p -> p.twice(2.5)).execute(), 1e-9);
// HALF has two overloads in the package: lambdaQL cannot tell which one to call
assertEquals("LQL-4044", assertThrows(LambdaQLException.class,
        () -> qb.call(PkgProcs.class, p -> p.half(10.0)).execute()).code());
```

<a id="when-the-metadata-cannot-be-read"></a>
**无法读取元数据时。** 有些数据库用户无权读取数据字典视图或 `information_schema`。如果元数据查询本身失败（`SQLException`），lambdaQL 不会停止调用：

- 每个存储过程以 `INFO` 级别记录一次 `LQL-5005`，并且不检查存根。
- 参数布局取自存根：IN 和 INOUT 参数按存根顺序，然后是 OUT 参数（按结果部分的顺序），在 Oracle 上再为每个 `List` 部分加一个 `REF CURSOR`。因此没有元数据时，数据库声明中 OUT 参数必须放在 IN 参数之后。
- 需要元数据的检查会被跳过：个数和 `@InOut` 检查（`LQL-4036`）以及名字顺序检查（`LQL-4043`）。
- 如果查询成功但没有找到存储过程，仍然是 `LQL-4035`。
- 如果只有 Oracle 重载查询（`all_arguments`）失败，lambdaQL 以 `INFO` 级别记录 `LQL-5006`，并在不检查重载的情况下调用。

这种降级行为已在 MariaDB 和 Oracle 上测试（`ProcedureTest` 的测试方法 `withoutMetadata`，模拟失败）。

Oracle 上真正的按名字绑定（`pass_procedure_paramater_names=true` 加上存根的参数名）也在计划中。

---

<a id="85-procedure-rules-pr1-to-pr15-summary"></a>
## 8.5 存储过程规则 PR1 至 PR15（汇总）

| # | 规则 | 状态 |
|---|---|---|
| PR1 | 名字：注解的值原样使用。值为空时：方法名转为 UPPER_SNAKE。可以使用包名或模式名（`SQ_PKG.CALC`、`LQL.SQ_CALC`）。为避免混淆，也可以写成 `@SqlProcedure(schema = ..., pkg = ..., value = ...)`。 | 已实现。模式名和包名没有测试覆盖 |
| PR2 | 参数**按位置**绑定。第一次调用时，会对照数据库元数据检查 IN/INOUT 参数的个数和 `@InOut` 的位置。能获得名字时也检查名字，相同名字顺序不同即为错误。也比对参数类型的种类（数字、字符、日期时间、布尔、二进制），不同即为错误（`LQL-4036`）（见 [8.4](#84-warning-argument-order)）。无法读取元数据时，跳过这些检查，参数布局取自存根（见 [8.4](#when-the-metadata-cannot-be-read)）。 | 个数、`@InOut` 和名字检查已实现（已测试）。Oracle 包重载检测也已实现（已在 Oracle 上测试）。类型比对也已实现（已在 MariaDB 和 Oracle 上测试）。没有元数据时的降级行为（`LQL-5005`、`LQL-5006`）也已实现（已在 MariaDB 和 Oracle 上测试）。 |
| PR3 | 一个 OUT → 返回值。多个 OUT/INOUT → record。`@InOut` 标记 INOUT 参数。 | 已实现（已测试） |
| PR4 | 一个结果集 → `List<T>`。实体通过 Hibernate；record 通过 JDBC 并按标签匹配。不能分页。 | 已实现（已用 record 测试） |
| PR5 | 多个结果集 → record 的 `List` 组件，按顺序。不支持 Oracle 隐式结果。 | 已实现，没有测试覆盖 |
| PR6 | 单独调用的数据库函数在不同数据库上使用不同的路径（见 [8.8](#88-call-a-db-function-alone)）。 | 已实现（已测试） |
| PR7 | 查询中的数据库函数：见 [8.6](#86-declare-db-functions) 和规则 FN1 至 FN11。 | 已实现（已测试） |
| PR8 | 条件中作用于列的数据库函数会产生 WARN，因为无法使用索引（见 [8.9](#89-warning-functions-on-columns-and-indexes-sargable)）。错误码为 `LQL-5001`。 | 已实现（已测试）。在 `SargableMode.STRICT` 下为错误（`LQL-3066`，D50） |
| PR9 | 存储过程在调用方的事务中运行。需要时 lambdaQL 会在调用前刷新。调用后已加载的实体可能是过期的。 | 已实现 |
| PR10 | 没有通用的受影响行数。请使用 OUT 参数。 | – |
| PR11 | IN 值为 `null` 没有问题。不能使用数据库参数的默认值。 | 已实现 |
| PR12 | Lambda 是一次存根调用。参数是捕获值、常量或它们的计算。带 OUT 参数时使用 `exec` 是错误。 | 已实现（已测试） |
| PR13 | 布尔参数在 MariaDB 和 Oracle 23ai 及以上版本中可用。 | 没有测试覆盖 |
| PR14 | 每个 Lambda 只分析一次。每个名字的存储过程元数据只读取一次。 | 已实现 |
| PR15 | H2 没有 OUT、INOUT、多结果集或包。请在 MariaDB 或 Oracle 上测试存储过程（例如使用 Testcontainers）。 | lambdaQL 的测试遵循这一点 |

PR11、PR12 和 PR13 有额外的 Kotlin 部分（默认参数、命名参数、可空类型）。它们在[本页的 Kotlin 版本](../kotlin/08-procedures-functions.md)中。

---

<a id="86-declare-db-functions"></a>
## 8.6 声明数据库函数

数据库函数是你在数据库中创建的函数，例如 `FN_DISCOUNT(price)`。
把它声明为带有 `@SqlFunction` 的**接口静态方法**。
方法体只能抛出异常。它在 lambdaQL 中从不执行。

**(已测试)** [`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java)，region `declare`：

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

    /** A query name with its own SQL name; registered with lambdaql.functions.classes. */
    @SqlFunction(value = "SQ_TAX", sql = "SQ_FN_DISCOUNT")
    static Double tax(Double price) {
        throw new DbOnlyException();
    }
}
```

测试在运行前会在数据库中创建 `SQ_FN_DISCOUNT` 和 `SQ_FN_IS_GOLD`，结束时删除它们。布尔结果需要 Oracle 23ai 及以上版本。

如果在 lambdaQL 的 Lambda **外部**调用 `OrderFns.discount(...)`，它会抛出 `DbOnlyException`（`LQL-4034`）。这是有意为之。

`@SqlFunction` 有两个值：

| 值 | 含义 |
|---|---|
| `value` | 查询中的函数名。为空时：方法名转为 UPPER_SNAKE（`isVip` → `IS_VIP`）。可以带模式名（`PUBLIC.SQ_FN`） |
| `sql` | SQL 名，当它与 `value` 不同时使用。这样的函数必须注册（见[注册](#registration-optional)） |

其他声明方式：

| 声明 | 状态 |
|---|---|
| 接口静态方法 | **推荐**（已测试） |
| 类静态方法 | 已设计，没有测试覆盖 |
| 接口 default 方法 + 单例常量 | 已设计，没有测试覆盖 |
| 你的仓库 `implements` 函数接口（mixin） | 不支持 |
| 函数式接口常量（`DISCOUNT.apply(x)`） | 不支持 |

请**带上接口名**调用函数（`OrderFns.discount`）。静态导入也可以，但同名的成员方法可能会遮蔽它。

---

<a id="87-use-db-functions-in-queries"></a>
## 8.7 在查询中使用数据库函数

直接调用函数即可。不需要上下文参数。它可以用在 `where`、`select`、`orderBy` 和 `set` 中（已测试），也可以用在 `groupBy`、`having` 和子查询中（已设计，没有测试覆盖）。

**(已测试)** [`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java)，region `where`：

```java
// the function call is written as function('<name>' as <type>, ...); the column is its argument
SelectQuery<Order> q = qb.from(Order.class).where(o -> OrderFns.discount(o.getPrice()) > 100);
assertEquals("select e0 from Order e0 where function('SQ_FN_DISCOUNT' as Double, e0.price) > 100.0", q.toHql().hql());
```

**(已测试)** [`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java)，region `captured`：

```java
// a function of captured values only is still computed by the database (the value is bound)
SelectQuery<Order> q = qb.from(Order.class).where(o -> o.getPrice() > OrderFns.discount(base));
assertEquals("select e0 from Order e0 where e0.price > function('SQ_FN_DISCOUNT' as Double, :p1)", q.toHql().hql());
```

Boolean 函数可以单独作为整个条件。lambdaQL 会加上 `= true`：

**(已测试)** [`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java)，region `boolean`：

```java
SelectQuery<Order> q = qb.from(Order.class).where(o -> OrderFns.isGold(o.getCustomer().getGrade()));
assertEquals("select e0 from Order e0 join e0.customer j1 where function('SQ_FN_IS_GOLD' as Boolean, j1.grade) = true",
        q.toHql().hql());
```

**(已测试)** [`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java)，region `select`（在 `select` 和 `orderBy` 中）：

```java
ValueQuery<Priced> q = qb.from(Order.class)
        .orderBy((o, by) -> by.desc(OrderFns.discount(o.getPrice())))
        .select(o -> new Priced(o.getId(), OrderFns.discount(o.getPrice())));
assertEquals("select e0.id, function('SQ_FN_DISCOUNT' as Double, e0.price) from Order e0"
        + " order by function('SQ_FN_DISCOUNT' as Double, e0.price) desc nulls last", q.toHql().hql());
```

`Priced` 是 `record Priced(Long id, Double discounted)`。

**(已测试)** [`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java)，region `update`：

```java
ExecutableUpdate<Order> u = qb.update(Order.class)
        .set(o -> o.setPrice(OrderFns.discount(o.getPrice())))
        .where(o -> o.getPrice() > 100);
assertEquals("update Order e0 set e0.price = function('SQ_FN_DISCOUNT' as Double, e0.price) where e0.price > 100.0",
        u.toHql().hql());
```

测试会执行每个查询，并把结果行与用 Java 进行的同样计算（`price * 0.9`）作比较。

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

<a id="how-lambdaql-renders-it"></a>
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

<a id="registration-optional"></a>
### 注册（可选）

`function('FN_DISCOUNT' as Double, ...)` 这种形式不需要注册（已在三种数据库上检查）。
如果注册了函数接口，Hibernate 还会保留名字的大小写，并检查参数个数和类型。

lambdaQL 提供一个 Hibernate `FunctionContributor`：`org.lambdaql.hibernate.LambdaQlFunctionContributor`（通过 ServiceLoader 发现）。你在 Hibernate 属性中列出这些接口：

```properties
# 纯 Hibernate
lambdaql.functions.classes=com.acme.OrderFns,com.acme.CustomerFns

# Spring Boot
spring.jpa.properties.lambdaql.functions.classes=com.acme.OrderFns
```

如果没有设置该属性，就不会注册任何东西。v0.1 中不支持包扫描。
对于嵌套接口，请使用带 `$` 的二进制名。测试环境中写的是 `lambdaql.functions.classes: org.lambdaql.query.FunctionTest$OrderFns`。

<a id="a-different-sql-name-needs-registration"></a>
### SQL 名不同时需要注册

`value` 是查询中的名字，`sql` 是 SQL 中的名字。两者不同时，必须注册该函数。
这对用引号创建的大小写混合的函数名（`"SQ_Fn_Mixed"`）很有用：不注册时，在 H2 和 Oracle 上名字可能会变成小写。

```java
@SqlFunction(value = "SQ_Fn_Mixed", sql = "\"SQ_Fn_Mixed\"")
static Double mixed(Double v) { throw new DbOnlyException(); }
```

**(已测试)** [`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java)，region `registered`：

```java
// SQ_TAX is registered at start-up (lambdaql.functions.classes) and runs SQ_FN_DISCOUNT in SQL
SelectQuery<Order> q = qb.from(Order.class).where(o -> OrderFns.tax(o.getPrice()) > 100);
assertEquals("select e0 from Order e0 where function('SQ_TAX' as Double, e0.price) > 100.0", q.toHql().hql());
```

如果这样的函数没有注册，lambdaQL 会在读取 Lambda 时失败（`LQL-3058`，见 [8.10](#810-function-rules-fn1-to-fn11-summary)）。

---

<a id="88-call-a-db-function-alone"></a>
## 8.8 单独调用数据库函数

使用带有**无参数 Lambda** 的 `qb.call(() -> ...)`：

**(已测试)** [`ProcedureTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java)，region `function`（H2、MariaDB 和 Oracle）：

```java
Double half = qb.call(() -> MathFns.half(price)).execute();
```

`price` 是捕获的 `double`（150），`half` 是 75.0。Lambda 必须是一次 `@SqlFunction` 调用（`LQL-3063`）。

**各数据库的 SQL（Hibernate 对这种 HQL 形式生成的 SQL）** — 由 `PlannedSqlTest` 以名字 `FN_DISCOUNT` 记录。来自 `expected-sql/` 的 [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)，示例 `08-function-alone`：

```sql
-- H2, MariaDB, Oracle (same)
-- HQL: select function('FN_DISCOUNT' as Double, :p1)
select FN_DISCOUNT(?)
```

它作为没有 FROM 的 `select FN_DISCOUNT(?)` 运行。Oracle 从 23ai 起才接受这种写法；在 Oracle 上，lambdaQL 改用 JDBC 函数调用（见下表）。

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
// → function('FN_DISCOUNT' as Double, e0.price) > 10   -- 不使用 price 上的索引
```

**各数据库的 SQL（Hibernate 对这种 HQL 形式生成的 SQL）** — 由 `PlannedSqlTest` 记录。来自 `expected-sql/` 的 [`planned-h2.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-h2.sql)、[`planned-mariadb.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-mariadb.sql)、[`planned-oracle.sql`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/resources/expected-sql/planned-oracle.sql)，示例 `08-function`：

```sql
-- H2, MariaDB, Oracle (same)
select …
from orders o1_0
where FN_DISCOUNT(o1_0.price)>10
```

因此 lambdaQL 会对这种写法记录 **WARN**。
每个 `where` Lambda 只检查一次，在缓存其分析结果时进行。没有 `@SqlFunction` 的条件不会查询数据库。

| 情况 | 错误码 | H2 | MariaDB | Oracle |
|---|---|---|---|---|
| ① 函数包住列：`fn(o.price) > 10`、作用于列的 Boolean 函数、`IN` 或 `IS NULL` 中的函数 | `LQL-5001`（WARN） | WARN | WARN（存储函数不能用于索引） | WARN，**除非** `user_ind_expressions` 中该表有相同表达式 `FN(COLUMN)` 的函数索引 |
| ② 值一侧的函数与列比较：`o.price > fn(rate)` | `LQL-5007`（WARN） | 不检查 | `information_schema.routines.is_deterministic = 'NO'`（默认！）时 WARN。此时 MariaDB 会扫描整个索引 | 不检查（Oracle 两种情况都用范围扫描） |

- 与 Oracle 索引比对时，表名和列名取自 Hibernate 映射。只比对只有一个列参数的函数。
- 如果元数据查询失败（例如没有权限），lambdaQL 以 `INFO` 级别记录 `LQL-5008`。① 仍然警告，② 不检查。

**(已测试)** [`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java)，region `sargable`（测试会收集日志：H2 为 `LQL-5001`，MariaDB 为 `LQL-5001` 和 `LQL-5007`，有 `SQ_FN_DISCOUNT(price)` 函数索引的 Oracle 没有警告）：

```java
// the function wraps the column: an index on price cannot be used (LQL-5001 at WARN),
// unless Oracle has a function-based index on SQ_FN_DISCOUNT(price)
qb.from(Order.class).where(o -> OrderFns.discount(o.getPrice()) > 100).toHql();
// the function is on the value side: fine, unless MariaDB says NOT DETERMINISTIC (LQL-5007 at WARN)
qb.from(Order.class).where(o -> o.getPrice() > OrderFns.nondet(base)).toHql();
```

**SARGable 模式**（决策 D50）。`SargableMode` 有三个值：

- `OFF`：不检查，也不查询元数据。
- `WARN`（默认）：记录警告。
- `STRICT`：在 `WARN` 会记录警告的情况下（`LQL-5001`、`LQL-5007`、`LQL-5012`），分析以 `org.lambdaql.NonSargableException`（`LQL-3066`）失败。

每个 persistence unit 用 `SargablePolicy.register(emf, SargableMode.STRICT)` 指定，用 `SargablePolicy.unregister(emf)` 解除。在 Spring 中使用 `lambdaql.sargable.mode=STRICT`（见 [12](12-spring-data-jpa.md)）。
检查在每个 Lambda 上只执行一次，在把分析结果放入共享缓存时进行。因此请在第一个查询之前注册模式。

**列上的算术**（决策 D50）。

- 只由一个列和值构成的 `+ - * /`（`o.getCnt() * 2 > n`）可以移到值一侧。lambdaQL 以 `WARN` 级别记录 `LQL-5012`，在 `STRICT` 下为错误。
- 列与列之间的算术（`o.getPrice() * o.getCnt() > n`）和 `%` 无法移动，因此只以 `DEBUG` 级别记录 `LQL-5013`。在 `STRICT` 下也允许。
- 移动后的形式必须含义相同。请像 `o.getCnt() > n / 2.0` 这样除以小数。整数除法 `n / 2` 在 `>=`、`=`、`<` 以及 `n` 为负数时会得到不同的行。如果乘以了负数，请反转比较方向。`LQL-5012` 的消息会给出这些提示。
- 没有采用查询索引是否存在来减少警告的方式。因为权限和同义词会让“没有索引”与“无法得知”混在一起，而且列上的算术是少见的写法。

**(已测试)** [`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java)，region `sargable-strict`：

```java
// STRICT: what WARN would log fails the analysis (NonSargableException, LQL-3066)
SargablePolicy.register(emf, SargableMode.STRICT);
NonSargableException fn = assertThrows(NonSargableException.class,
        () -> qb.from(Order.class).where(o -> OrderFns.discount(o.getTotal()) > 100).toHql());
assertEquals("LQL-3066", fn.code());
assertThrows(NonSargableException.class, () -> qb.from(Order.class).where(o -> o.getCnt() * 2 > n).toHql());
// the same meaning on the value side uses the index; divide by a decimal (n / 2 is integer division)
var moved = qb.from(Order.class).where(o -> o.getCnt() > n / 2.0).toHql();
assertEquals("select e0 from Order e0 where e0.cnt > :p1", moved.hql());
assertEquals(Map.of("p1", new java.math.BigDecimal("2.5")), moved.parameters());   // 2.5, never cut to 2
// arithmetic between columns cannot move, so STRICT allows it
qb.from(Order.class).where(o -> o.getCnt() * o.getStatus() > n).toHql();
```

**建议：**

- 尽可能把函数放在值这一侧：`o.getPrice() > OrderFns.discount(base)`。
- 在 MariaDB 上，如果函数确实是确定性的，请将它声明为 `DETERMINISTIC`。
- 在 Oracle 上，存在相同表达式的函数索引时警告会消失（这种索引需要 `DETERMINISTIC` 函数）。

---

<a id="810-function-rules-fn1-to-fn11-summary"></a>
## 8.10 函数规则 FN1 至 FN11（汇总）

| # | 规则 | 状态 |
|---|---|---|
| FN1 | `@SqlFunction` 是方法注解。值为空时：方法名转为 UPPER_SNAKE。 | 已实现 |
| FN2 | lambdaQL 找到被调用的方法并读取它的注解。方法体只能抛出异常（`throw new ...`）。有实际方法体的方法是错误（`LQL-3052`），因为 Java 和数据库可能给出不同的值。 | 已实现（已测试） |
| FN3 | 同名的所有重载必须映射到同一个数据库函数。否则报错（`LQL-3053`）。 | 已实现 |
| FN4 | 没有注解的调用，或 lambdaQL 无法识别的调用，是错误。它从不会被悄悄跳过。 | 已实现 |
| FN5 | 参数遵循 [3. 条件](03-where.md)中的值规则。即使参数只由捕获值构成，也会被绑定并**在数据库中**求值。 | 已实现（已测试） |
| FN6 | 装箱调用会被跳过。返回类型取自声明的方法，必须是 `Double`、`Integer`、`Long`、`BigDecimal`、`String`、`LocalDate`、`LocalDateTime` 或 `Boolean`（`LQL-3055`）。 | 已实现（已测试） |
| FN7 | 作用于列的函数会产生 WARN（见 [8.9](#89-warning-functions-on-columns-and-indexes-sargable)）。在 MariaDB 上，值一侧的 `NOT DETERMINISTIC` 函数也会产生 WARN（`LQL-5007`）。 | 已实现（已测试）。在 `SargableMode.STRICT` 下为错误（`LQL-3066`，D50） |
| FN8 | 与 Hibernate 内置函数（`COALESCE`、`ROUND`、`SYSDATE` 等）同名是错误（`LQL-3054`）。Hibernate 会悄悄使用内置函数。请使用前缀或模式名（`PUBLIC.SQ_FN`）。 | 已实现（已测试） |
| FN9 | 仅限 Kotlin（默认参数、`inline`、`vararg`）。见 Kotlin 路线。 | 默认参数和 `vararg`：已实现。`inline`：已设计 |
| FN10 | v0.1 只支持标量函数。用户自定义聚合函数和窗口函数计划在 v0.2 中提供。没有参数的函数渲染为 `fn()`。 | 标量函数已实现 |
| FN11 | 已知的数据库差异：在 MariaDB 上，返回 VARCHAR 的函数可能与字符串字面量发生排序规则（collation）冲突。`BigDecimal` 结果的小数位数不同（MariaDB 和 Oracle 为 0，H2 为 1）。布尔结果需要 Oracle 23ai 及以上版本。 | 已用原型实测 |

**(已测试)** [`FunctionTest.java`](https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java)，region `rules`：

```java
// the body never runs in Java
assertEquals("LQL-4034", assertThrows(DbOnlyException.class, () -> OrderFns.discount(1.0)).code());
// FN2: a body that computes a value is rejected (Java and the database could give different values)
assertEquals("LQL-3052", assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(o -> BadFns.computes(o.getPrice()) > 1)).code());
// FN8: a Hibernate built-in name would silently run the built-in function
assertEquals("LQL-3054", assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(o -> BadFns.builtin(o.getPrice()) > 1)).code());
// a function with its own SQL name must be registered
assertEquals("LQL-3058", assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(o -> BadFns.unregistered(o.getPrice()) > 1)).code());
// the return type must have an HQL type
assertEquals("LQL-3055", assertThrows(LambdaQLException.class,
        () -> qb.from(Order.class).where(o -> BadFns.anything(o.getPrice()) != null)).code());
```

`BadFns` 是测试中的一个接口，每种情况各有一个有问题的函数：返回 `price` 的方法体、名字 `COALESCE`、`value = "SQ_OTHER"` 配 `sql = "SQ_FN_DISCOUNT"`（未注册），以及返回类型 `Object`。

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
| 查询中的数据库函数 | 支持（已测试） | 支持（已测试） | 支持（已测试） |
| 单独调用数据库函数 | 支持（已测试） | 支持（已测试） | 支持（已测试） |
| lambdaQL 中的存储过程调用 | – | 支持（已测试） | 支持（已测试） |
| 布尔参数 / 结果 | – | 支持 | 支持（23ai 及以上） |

v0.1 中未计划的内容：用于动态存储过程名的字符串构建器（v0.2 候选）、选择并调用 Oracle 包存储过程的某个重载（重载检测已实现，`LQL-4044`）、表值函数、存储过程结果的分页。

本章的错误是带有 `[LQL-xxxx]` 错误码的 `LambdaQLException`（或其子类）：读取 Lambda 时为 `LQL-3052` 至 `LQL-3063`，以及在 `SargableMode.STRICT` 下无法使用索引的条件的 `LQL-3066`（`NonSargableException`，见 [8.9](#89-warning-functions-on-columns-and-indexes-sargable)）。执行调用时为 `LQL-4034` 至 `LQL-4039`、`LQL-4043` 和 `LQL-4044`。调用时还可能以 `INFO` 级别记录 `LQL-5004`、`LQL-5005` 或 `LQL-5006`。对于条件中的数据库函数，可能记录 `LQL-5001`、`LQL-5007`（`WARN`）和 `LQL-5008`（`INFO`）。见 [11. 错误与语言](../11-errors-and-languages.md)。

---

[Java 路线](README.md) | 上一章：[7. 更新、插入、删除](07-dml.md) | 下一章：[9. Java 说明](09-java-notes.md) | [本页的 Kotlin 版本](../kotlin/08-procedures-functions.md)
