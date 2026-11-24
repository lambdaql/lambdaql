package org.lambdaql.engine.ir;

import org.objectweb.asm.Type;

import java.util.List;

/**
 * A value expression made from lambda bytecode.
 * <p>
 * lambdaQL never runs your lambda. It reads the bytecode and builds a tree of {@code Expr} nodes.
 * There are two kinds of nodes:
 * <ul>
 *   <li><b>Raw nodes</b> — made by the decoder from bytecode:
 *       {@link Param}, {@link Captured}, {@link Const}, {@link Invoke}, {@link GetField}, {@link Arith},
 *       {@link Neg}, {@link Convert}, {@link CmpResult}, {@link NewObject}, {@link ArrayLength}, {@link Coalesce}.</li>
 *   <li><b>Meaning nodes</b> — made later by the semantic step:
 *       {@link Column} (an entity column) and {@link Host} (a value we compute in Java and bind as a parameter).</li>
 * </ul>
 * The HQL renderer only accepts {@link Column}, {@link Host}, {@link Const}, {@link Arith}, {@link Neg} and {@link Coalesce}.
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/decode/ConditionDecodeTest.java">ConditionDecodeTest</a>):
 * {@snippet class="org.lambdaql.engine.decode.ConditionDecodeTest" region="arith"}
 */
public sealed interface Expr permits
        Expr.Param, Expr.Captured, Expr.Const, Expr.Invoke, Expr.GetField, Expr.Arith, Expr.Neg,
        Expr.Convert, Expr.CmpResult, Expr.NewObject, Expr.ArrayLength, Expr.Coalesce, Expr.Column, Expr.Host,
        Expr.Lambda, Expr.Root, Expr.Subquery, Expr.Aggregate, Expr.ArrayOf, Expr.Func, Expr.SafeNav, Expr.Choose, Expr.Case {

    /**
     * Returns the JVM type of this value.
     *
     * @return the ASM type (for example {@code Type.INT_TYPE} or {@code Type.getType(String.class)});
     *         {@code java/lang/Object} when the type is unknown
     */
    Type type();

    /**
     * A lambda parameter, such as the entity {@code o} in {@code o -> ...}.
     * Captured values are not counted.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/decode/ConditionDecodeTest.java">ConditionDecodeTest</a>):
     * {@snippet class="org.lambdaql.engine.decode.ConditionDecodeTest" region="simple-compare"}
     *
     * @param index position in the lambda parameter list (0 = first parameter)
     * @param type  JVM type of the parameter
     */
    record Param(int index, Type type) implements Expr {
        @Override public String toString() { return "param" + index; }
    }

    /**
     * A captured value: a local variable or field that the lambda uses from outside.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/decode/ConditionDecodeTest.java">ConditionDecodeTest</a>):
     * {@snippet class="org.lambdaql.engine.decode.ConditionDecodeTest" region="captured"}
     *
     * @param index position in {@code SerializedLambda.getCapturedArg(index)};
     *              when the lambda body is an instance method, index 0 is {@code this}
     * @param type  JVM type of the value
     */
    record Captured(int index, Type type) implements Expr {
        @Override public String toString() { return "captured" + index; }
    }

    /**
     * A constant value from the bytecode.
     * <p>
     * {@code boolean}, {@code char}, {@code short} and {@code byte} constants come as {@link Integer},
     * because the JVM uses int for them.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/ir/IrExamplesTest.java">IrExamplesTest</a>):
     * {@snippet class="org.lambdaql.engine.ir.IrExamplesTest" region="const-null"}
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/decode/ConditionDecodeTest.java">ConditionDecodeTest</a>):
     * {@snippet class="org.lambdaql.engine.decode.ConditionDecodeTest" region="simple-compare"}
     *
     * @param value the value; {@code null} for the {@code null} literal; an ASM {@link Type} for a class literal
     * @param type  JVM type of the value
     */
    record Const(Object value, Type type) implements Expr {
        /** Type used for the {@code null} literal. */
        public static final Type NULL_TYPE = Type.getObjectType("java/lang/Object");

        /**
         * Creates the {@code null} literal.
         *
         * @return a constant with value {@code null}
         */
        public static Const ofNull() { return new Const(null, NULL_TYPE); }

        /**
         * Checks if this is the {@code null} literal.
         *
         * @return {@code true} if the value is {@code null}
         */
        public boolean isNull() { return value == null; }

        @Override public String toString() { return value instanceof String s ? '"' + s + '"' : String.valueOf(value); }
    }

    /**
     * A method call.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/decode/ConditionDecodeTest.java">ConditionDecodeTest</a>):
     * {@snippet class="org.lambdaql.engine.decode.ConditionDecodeTest" region="value-lambda"}
     *
     * @param receiver the object the method is called on; {@code null} for a static method
     * @param method   the called method
     * @param args     the arguments, in order
     */
    record Invoke(Expr receiver, MethodRef method, List<Expr> args) implements Expr {
        /** Makes an unmodifiable copy of the argument list. */
        public Invoke {
            args = List.copyOf(args);
        }
        @Override public Type type() { return method.returnType(); }
        @Override public String toString() {
            String r = receiver == null ? method.ownerSimpleName() : receiver.toString();
            return r + "." + method.name() + args;
        }
    }

    /**
     * A field read.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/decode/ConditionDecodeTest.java">ConditionDecodeTest</a>):
     * {@snippet class="org.lambdaql.engine.decode.ConditionDecodeTest" region="captured-field"}
     *
     * @param receiver the object; {@code null} for a static field
     * @param owner    internal name of the class that declares the field (for example {@code com/acme/Order})
     * @param name     field name
     * @param type     JVM type of the field
     */
    record GetField(Expr receiver, String owner, String name, Type type) implements Expr {
        @Override public String toString() {
            return (receiver == null ? owner.substring(owner.lastIndexOf('/') + 1) : receiver.toString()) + "#" + name;
        }
    }

    /**
     * A math operation with two values.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/decode/ConditionDecodeTest.java">ConditionDecodeTest</a>):
     * {@snippet class="org.lambdaql.engine.decode.ConditionDecodeTest" region="arith"}
     *
     * @param op    the operation ({@code +}, {@code -}, {@code *}, {@code /}, {@code %})
     * @param left  left value
     * @param right right value
     * @param type  JVM type of the result
     */
    record Arith(ArithOp op, Expr left, Expr right, Type type) implements Expr {
        @Override public String toString() { return "(" + left + " " + op.symbol() + " " + right + ")"; }
    }

    /**
     * Unary minus.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/decode/ConditionDecodeTest.java">ConditionDecodeTest</a>):
     * {@snippet class="org.lambdaql.engine.decode.ConditionDecodeTest" region="neg"}
     *
     * @param operand the value
     * @param type    JVM type of the result
     */
    record Neg(Expr operand, Type type) implements Expr {
        @Override public String toString() { return "-" + operand; }
    }

    /**
     * A type change: a primitive conversion ({@code I2L} and so on) or a {@code CHECKCAST}.
     * It does not change the value, so later steps usually look through it.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/decode/ConditionDecodeTest.java">ConditionDecodeTest</a>):
     * {@snippet class="org.lambdaql.engine.decode.ConditionDecodeTest" region="convert"}
     *
     * @param operand the value
     * @param type    the new type
     */
    record Convert(Expr operand, Type type) implements Expr {
        @Override public String toString() { return operand.toString(); }
    }

    /**
     * The result (-1, 0 or 1) of {@code LCMP}, {@code FCMPL}, {@code FCMPG}, {@code DCMPL} or {@code DCMPG}.
     * The next {@code IFxx} jump turns it into a {@link Pred.Cmp}.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/decode/ConditionDecodeTest.java">ConditionDecodeTest</a>):
     * {@snippet class="org.lambdaql.engine.decode.ConditionDecodeTest" region="double-compare"}
     *
     * @param left       left value
     * @param right      right value
     * @param nanGreater {@code true} for the "G" opcodes (NaN gives 1); kept so the source operator can be found
     */
    record CmpResult(Expr left, Expr right, boolean nanGreater) implements Expr {
        @Override public Type type() { return Type.INT_TYPE; }
    }

    /**
     * A new object: {@code NEW}, {@code DUP}, arguments, then {@code INVOKESPECIAL <init>}.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/decode/ConditionDecodeTest.java">ConditionDecodeTest</a>):
     * {@snippet class="org.lambdaql.engine.decode.ConditionDecodeTest" region="new-object"}
     *
     * @param type        the class of the new object
     * @param constructor the called constructor
     * @param args        constructor arguments, in order
     */
    record NewObject(Type type, MethodRef constructor, List<Expr> args) implements Expr {
        /** Makes an unmodifiable copy of the argument list. */
        public NewObject {
            args = List.copyOf(args);
        }
    }

    /**
     * The length of an array ({@code ARRAYLENGTH}).
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/decode/ConditionDecodeTest.java">ConditionDecodeTest</a>):
     * {@snippet class="org.lambdaql.engine.decode.ConditionDecodeTest" region="array-length"}
     *
     * @param array the array value
     */
    record ArrayLength(Expr array) implements Expr {
        @Override public Type type() { return Type.INT_TYPE; }
    }

    /**
     * {@code coalesce(first, second)}: {@code first} if it is not null, else {@code second}.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/ir/IrExamplesTest.java">IrExamplesTest</a>):
     * {@snippet class="org.lambdaql.engine.ir.IrExamplesTest" region="coalesce"}
     *
     * @param first  the value to try first
     * @param second the fallback value
     * @param type   JVM type of the result
     */
    record Coalesce(Expr first, Expr second, Type type) implements Expr {
        @Override public String toString() { return "coalesce(" + first + ", " + second + ")"; }
    }

    /**
     * A value ternary made only of captured values and constants: {@code flag ? 1 : 2}, Kotlin {@code if (flag) 1 else 2}.
     * It is computed in Java when the query runs, like any other captured value (ROADMAP D61).
     *
     * <p>Example: {@code o.getStatus() == (flag ? 1 : 2)} binds {@code 1} or {@code 2} as {@code :p1}.
     *
     * @param cond      the condition (no entity value in it)
     * @param whenTrue  the value when the condition is true
     * @param whenFalse the value when the condition is false
     * @param type      JVM type of the result
     */
    record Choose(Pred cond, Expr whenTrue, Expr whenFalse, Type type) implements Expr {
        @Override public String toString() { return "(" + cond + " ? " + whenTrue + " : " + whenFalse + ")"; }
    }

    /**
     * A value chosen by conditions on the entity: {@code o.isActive() ? 1 : 2}, Kotlin {@code when { a -> x; else -> y }}.
     * Rendered as {@code case when c1 then v1 ... else v end} (ROADMAP D62).
     *
     * <p>Example: {@code select(o -> o.isActive() ? "Y" : "N")} selects {@code case when e0.active = true then 'Y' else 'N' end}.
     *
     * @param conds     the conditions, tested in order
     * @param values    the value of each condition
     * @param otherwise the value when no condition is true
     * @param type      JVM type of the result
     */
    record Case(List<Pred> conds, List<Expr> values, Expr otherwise, Type type) implements Expr {
        /** Makes unmodifiable copies of the lists. */
        public Case {
            conds = List.copyOf(conds);
            values = List.copyOf(values);
        }

        /**
         * Every value inside: the operands of the conditions, the values and the otherwise value.
         *
         * <p>Example: for {@code case when e0.active = true then 1 else 2 end}: {@code [e0.active, true, 1, 2]}.
         *
         * @return the values, in order
         */
        public List<Expr> exprs() {
            List<Expr> out = new java.util.ArrayList<>();
            for (Pred p : conds) operands(p, out);
            out.addAll(values);
            out.add(otherwise);
            return out;
        }

        /**
         * The same case with every value inside replaced.
         *
         * <p>Example: {@code c.map(x -> rename(x))} renames the columns in the conditions and the values.
         *
         * @param f the replacement
         * @return the new case
         */
        public Case map(java.util.function.Function<Expr, Expr> f) {
            return new Case(conds.stream().map(p -> mapPred(p, f)).toList(), values.stream().map(f).toList(), f.apply(otherwise), type);
        }

        private static void operands(Pred p, List<Expr> out) {
            switch (p) {
                case Pred.Cmp c -> { out.add(c.left()); out.add(c.right()); }
                case Pred.IsNull n -> out.add(n.operand());
                case Pred.Truth t -> out.add(t.operand());
                case Pred.In in -> { out.add(in.value()); out.add(in.values()); }
                case Pred.Like l -> { out.add(l.value()); out.add(l.text()); }
                case Pred.And a -> a.items().forEach(x -> operands(x, out));
                case Pred.Or o -> o.items().forEach(x -> operands(x, out));
                case Pred.Not n -> operands(n.operand(), out);
                case Pred.Bool b -> { }
            }
        }

        private static Pred mapPred(Pred p, java.util.function.Function<Expr, Expr> f) {
            return switch (p) {
                case Pred.Cmp c -> new Pred.Cmp(c.op(), f.apply(c.left()), f.apply(c.right()), c.nullSafe());
                case Pred.IsNull n -> new Pred.IsNull(f.apply(n.operand()));
                case Pred.Truth t -> new Pred.Truth(f.apply(t.operand()));
                case Pred.In in -> new Pred.In(f.apply(in.value()), f.apply(in.values()));
                case Pred.Like l -> new Pred.Like(f.apply(l.value()), f.apply(l.text()), l.kind(), l.ignoreCase(), l.negated());
                case Pred.And a -> new Pred.And(a.items().stream().map(x -> mapPred(x, f)).toList());
                case Pred.Or o -> new Pred.Or(o.items().stream().map(x -> mapPred(x, f)).toList());
                case Pred.Not n -> new Pred.Not(mapPred(n.operand(), f));
                case Pred.Bool b -> b;
            };
        }

        @Override public String toString() {
            StringBuilder sb = new StringBuilder("case");
            for (int i = 0; i < conds.size(); i++) sb.append(" when ").append(conds.get(i)).append(" then ").append(values.get(i));
            return sb.append(" else ").append(otherwise).append(" end").toString();
        }
    }

    /**
     * Meaning node: an entity column path.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/ir/IrExamplesTest.java">IrExamplesTest</a>):
     * {@snippet class="org.lambdaql.engine.ir.IrExamplesTest" region="column"}
     *
     * @param root        which FROM entity the path starts at (0 = main entity)
     * @param path        property names from the root (for example {@code ["customer", "name"]})
     * @param association one flag per path part: {@code true} if that part is a to-one association
     *                    (the renderer adds a LEFT JOIN for it)
     * @param javaType    Java type of the column value (boxed for primitives)
     * @param nonNull     {@code true} if the column can never be NULL (id, primitive, {@code nullable = false})
     * @param idPath      {@code true} if the path ends at the id of an association (a foreign key column,
     *                    so no join is needed)
     * @param safeCall    one flag per path part: {@code true} if the lambda reached the next part of that association
     *                    with the Kotlin safe call {@code ?.} (ROADMAP D54). Such a step may become a {@code left join}
     *                    in a where condition. It is not part of {@link #equals}: both forms name the same column.
     */
    record Column(int root, List<String> path, List<Boolean> association, Class<?> javaType,
                  boolean nonNull, boolean idPath, List<Boolean> safeCall) implements Expr {
        /** Makes unmodifiable copies of the lists; a missing or short {@code safeCall} list is filled with {@code false}. */
        public Column {
            path = List.copyOf(path);
            association = List.copyOf(association);
            List<Boolean> safe = new java.util.ArrayList<>(safeCall == null ? List.of() : safeCall);
            while (safe.size() < path.size()) safe.add(false);
            safeCall = List.copyOf(safe.subList(0, path.size()));
        }

        /**
         * A column without Kotlin safe calls (Java, or Kotlin without {@code ?.}).
         *
         * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/ir/IrExamplesTest.java">IrExamplesTest</a>):
         * {@snippet class="org.lambdaql.engine.ir.IrExamplesTest" region="column"}
         *
         * @param root        which FROM entity the path starts at
         * @param path        property names from the root
         * @param association one flag per path part: {@code true} for a to-one association
         * @param javaType    Java type of the column value
         * @param nonNull     {@code true} if the column can never be NULL
         * @param idPath      {@code true} if the path ends at the id of an association
         */
        public Column(int root, List<String> path, List<Boolean> association, Class<?> javaType, boolean nonNull, boolean idPath) {
            this(root, path, association, javaType, nonNull, idPath, List.of());
        }

        /**
         * Tells whether the step at {@code i} was reached with {@code ?.}.
         *
         * @param i index of an association part of the path
         * @return {@code true} for a Kotlin safe call on that association
         */
        public boolean safeStep(int i) {
            return safeCall.get(i);
        }

        @Override public Type type() { return Type.getType(javaType); }
        @Override public String toString() { return "col" + root + "." + String.join(".", path); }

        /**
         * Compares the column, not how the lambda reached it: {@code safeCall} is ignored.
         *
         * @param o the other object
         * @return {@code true} for the same root, path and column facts
         */
        @Override
        public boolean equals(Object o) {
            return o instanceof Column c && root == c.root && nonNull == c.nonNull && idPath == c.idPath
                    && path.equals(c.path) && association.equals(c.association) && javaType.equals(c.javaType);
        }

        /** @return a hash code that ignores {@code safeCall}, like {@link #equals} */
        @Override
        public int hashCode() {
            return java.util.Objects.hash(root, path, association, javaType, nonNull, idPath);
        }
    }

    /**
     * Raw node: a value that the Kotlin safe call {@code ?.} checked for null before its next member was read
     * ({@code it.customer?.name}: the value {@code it.customer}). The decoder keeps only the non-null path of the safe
     * call and marks the value with this node; the normalizer turns the mark into {@link Column#safeCall()} when the
     * value is an association (ROADMAP D54). Only values that start at a lambda parameter are marked.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/ir/IrExamplesTest.java">IrExamplesTest</a>):
     * {@snippet class="org.lambdaql.engine.ir.IrExamplesTest" region="safe-nav"}
     *
     * @param value the value that was checked for null
     */
    record SafeNav(Expr value) implements Expr {
        @Override public Type type() { return value.type(); }
        @Override public String toString() { return value + "?"; }
    }

    /**
     * Meaning node: a value that does not depend on any entity.
     * lambdaQL computes it in Java when the query runs and binds it as a parameter.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/ir/IrExamplesTest.java">IrExamplesTest</a>):
     * {@snippet class="org.lambdaql.engine.ir.IrExamplesTest" region="host"}
     *
     * @param expr            the expression to compute (uses only captured values and constants)
     * @param javaType        Java type to bind (numbers are converted to this type)
     * @param nonNullRequired {@code true} if a null value must be an error, because Java would throw
     *                        {@code NullPointerException} (for example the receiver of {@code p.equals(...)})
     */
    record Host(Expr expr, Class<?> javaType, boolean nonNullRequired, boolean autoUnboxed) implements Expr {
        /**
         * A value computed in Java.
         *
         * @param expr            the expression of captured values
         * @param javaType        the Java type of the value
         * @param nonNullRequired {@code true} when Java needs a non-null value here
         */
        public Host(Expr expr, Class<?> javaType, boolean nonNullRequired) {
            this(expr, javaType, nonNullRequired, false);
        }

        @Override public Type type() { return Type.getType(javaType); }
        @Override public String toString() { return "host(" + expr + ")"; }
    }

    /**
     * Raw node: a lambda made inside the lambda ({@code invokedynamic} with {@code LambdaMetafactory}), for example
     * the {@code i -> ...} in {@code sub.from(Item.class).where(i -> ...)}. Its body is read from the class file
     * (ROADMAP M8, 8.2); it does not need to be serializable.
     *
     * @param owner      internal name of the class that has the body method
     * @param method     the body method name
     * @param descriptor the body method descriptor
     * @param isStatic   {@code true} if the body is a static method
     * @param captured   the values captured by the inner lambda, in order (in the outer lambda's terms)
     * @param type       the functional interface type
     */
    record Lambda(String owner, String method, String descriptor, boolean isStatic, List<Expr> captured, Type type)
            implements Expr {
        /** Makes an unmodifiable copy of the captured list. */
        public Lambda {
            captured = List.copyOf(captured);
        }
        @Override public String toString() { return "lambda(" + method + captured + ")"; }
    }

    /**
     * Semantic node: an entity root that is already resolved to a query root index — used for the outer entity
     * inside a subquery (correlation).
     *
     * @param root the query root index
     * @param type the entity type
     */
    record Root(int root, Type type) implements Expr {
        @Override public String toString() { return "root" + root; }
    }

    /**
     * Meaning node: a subquery (ROADMAP M8).
     *
     * @param kind   what the subquery gives
     * @param root   the query root index of the subquery entity
     * @param entity the subquery entity
     * @param wheres the normalized conditions (joined with AND); they may use outer roots (correlation)
     * @param select the selected value ({@link Kind#VALUES}, {@link Kind#SCALAR}), or null
     * @param agg    the aggregate for {@link Kind#SCALAR}, or {@link Agg#NONE} (then a {@code first()} after limit 1)
     * @param sorts  the order (only with a limit)
     * @param limit  the row limit, or null
     * @param type   the JVM type of the value
     * @param joins  the entity joins inside the subquery ({@code sub.from(A).join(B, ...)}, ROADMAP D51)
     */
    record Subquery(Kind kind, int root, Class<?> entity, List<Pred> wheres, Expr select, Agg agg,
                    List<SortItem> sorts, Integer limit, Type type, List<Join> joins) implements Expr {
        /**
         * An entity join inside a subquery.
         *
         * @param root   the query root index of the joined entity
         * @param entity the joined entity
         * @param left   {@code true} for a left join
         * @param on     the normalized join condition
         */
        public record Join(int root, Class<?> entity, boolean left, Pred on) {
        }

        /** A subquery without joins. */
        public Subquery(Kind kind, int root, Class<?> entity, List<Pred> wheres, Expr select, Agg agg,
                        List<SortItem> sorts, Integer limit, Type type) {
            this(kind, root, entity, wheres, select, agg, sorts, limit, type, List.of());
        }

        /** What a subquery gives. */
        public enum Kind {
            /** {@code exists (select 1 ...)}: a boolean. */
            EXISTS,
            /** {@code (select v ...)} for {@code x in (...)}. */
            VALUES,
            /** One value: an aggregate, or the first value after limit 1. */
            SCALAR
        }

        /** The aggregate of a scalar subquery. */
        public enum Agg {
            /** No aggregate: the first value after {@code limit(1)}. */
            NONE,
            /** {@code count(alias)}. */
            COUNT,
            /** {@code max(value)}. */
            MAX,
            /** {@code min(value)}. */
            MIN,
            /** {@code avg(value)}. */
            AVG,
            /** {@code sum(value)}. */
            SUM
        }

        /** Makes unmodifiable copies of the lists. */
        public Subquery {
            wheres = List.copyOf(wheres);
            sorts = List.copyOf(sorts);
            joins = List.copyOf(joins);
        }
        @Override public String toString() {
            return "sub(" + kind + (agg == Agg.NONE ? "" : " " + agg) + " " + entity.getSimpleName() + "#" + root
                    + (select == null ? "" : " select " + select) + " where " + wheres
                    + (limit == null ? "" : " limit " + limit) + ")";
        }
    }

    /**
     * Meaning node: an aggregate function from {@code org.lambdaql.Agg} (ROADMAP M9).
     *
     * @param fn   the function
     * @param arg  the normalized argument, or null for {@code count(*)}
     * @param type the JVM type of the result
     */
    record Aggregate(Fn fn, Expr arg, Type type) implements Expr {
        /** An aggregate function. */
        public enum Fn {
            /** {@code count(*)}. */
            COUNT_ALL,
            /** {@code count(x)}. */
            COUNT,
            /** {@code count(distinct x)}. */
            COUNT_DISTINCT,
            /** {@code sum(x)}. */
            SUM,
            /** {@code avg(x)}. */
            AVG,
            /** {@code min(x)}. */
            MIN,
            /** {@code max(x)}. */
            MAX
        }
        @Override public String toString() { return fn.name().toLowerCase() + "(" + (arg == null ? "*" : arg) + ")"; }
    }

    /**
     * Raw node: an array made in the lambda ({@code anewarray} and {@code aastore}), for example the argument of
     * Kotlin {@code listOf(a, b)}. Elements that were not stored yet are null.
     *
     * @param type  the array type
     * @param items the elements
     */
    record ArrayOf(Type type, List<Expr> items) implements Expr {
        /** Makes a copy that allows null elements. */
        public ArrayOf {
            items = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(items));
        }
        @Override public String toString() { return "array" + items; }
    }

    /**
     * Checks if a JVM type takes two stack slots.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/ir/IrExamplesTest.java">IrExamplesTest</a>):
     * {@snippet class="org.lambdaql.engine.ir.IrExamplesTest" region="expr-wide"}
     *
     * @param t the type
     * @return {@code true} for {@code long} and {@code double}
     */
    static boolean isWide(Type t) {
        return t.getSort() == Type.LONG || t.getSort() == Type.DOUBLE;
    }

    /**
     * Meaning node: a call of a database function declared with {@code @SqlFunction} (ROADMAP M11, D31).
     * It is written as {@code function('<key>' as <asType>, args...)}.
     *
     * @param key    the function name used in the query
     * @param sql    the SQL name, or the same as {@code key}
     * @param asType the HQL type name after {@code as} ({@code Double}, {@code String} ...)
     * @param args   the normalized arguments
     * @param type   the JVM type of the result
     */
    record Func(String key, String sql, String asType, List<Expr> args, Type type) implements Expr {
        /** Makes an unmodifiable copy of the arguments. */
        public Func {
            args = List.copyOf(args);
        }

        @Override
        public String toString() {
            return key + args;
        }
    }
}
