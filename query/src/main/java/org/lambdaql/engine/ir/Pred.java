package org.lambdaql.engine.ir;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A condition tree (the WHERE part of a query).
 * <p>
 * The decoder makes small "atoms" ({@link Cmp}, {@link IsNull}, {@link Truth}) from jump instructions.
 * The {@code ConditionReducer} joins them back into {@link And}, {@link Or} and {@link Not},
 * so you get the same logic you wrote in the source code.
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/decode/ConditionDecodeTest.java">ConditionDecodeTest</a>):
 * {@snippet class="org.lambdaql.engine.decode.ConditionDecodeTest" region="and-or"}
 */
public sealed interface Pred permits Pred.Cmp, Pred.IsNull, Pred.Truth, Pred.In, Pred.And, Pred.Or, Pred.Not, Pred.Bool {

    /**
     * A compare: {@code left op right}.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/decode/ConditionDecodeTest.java">ConditionDecodeTest</a>):
     * {@snippet class="org.lambdaql.engine.decode.ConditionDecodeTest" region="null-safe"}
     *
     * @param op       the operator
     * @param left     left value
     * @param right    right value
     * @param nullSafe {@code true} for a value compare that must treat NULL like a normal value
     *                 (reference {@code ==} on objects, {@code equals}, {@code Objects.equals}; see ROADMAP D12).
     *                 For example {@code o.getCode() == p} with {@code p = null} must find rows where code IS NULL.
     */
    record Cmp(CmpOp op, Expr left, Expr right, boolean nullSafe) implements Pred {
        @Override public String toString() {
            return left + (nullSafe ? " " + op.symbol() + "~ " : " " + op.symbol() + " ") + right;
        }
    }

    /**
     * {@code operand IS NULL}.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/decode/ConditionDecodeTest.java">ConditionDecodeTest</a>):
     * {@snippet class="org.lambdaql.engine.decode.ConditionDecodeTest" region="is-null"}
     *
     * @param operand the value to test
     */
    record IsNull(Expr operand) implements Pred {
        @Override public String toString() { return operand + " is null"; }
    }

    /**
     * A boolean value used as a condition.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/decode/ConditionDecodeTest.java">ConditionDecodeTest</a>):
     * {@snippet class="org.lambdaql.engine.decode.ConditionDecodeTest" region="boolean-getter"}
     *
     * @param operand a value of type {@code boolean} (or a call that returns boolean, like {@code equals})
     */
    record Truth(Expr operand) implements Pred {
        @Override public String toString() { return "truth(" + operand + ")"; }
    }

    /**
     * {@code value IN (values)}, made from {@code ids.contains(o.getId())} (Java) or {@code it.id in ids} (Kotlin).
     * The list comes from a captured collection and is bound as one parameter (ROADMAP D29).
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/ir/IrExamplesTest.java">IrExamplesTest</a>):
     * {@snippet class="org.lambdaql.engine.ir.IrExamplesTest" region="pred-in"}
     *
     * @param value  the tested value (usually a {@link Expr.Column})
     * @param values the collection (a {@link Expr.Host} after the semantic step)
     */
    record In(Expr value, Expr values) implements Pred {
        @Override public String toString() { return value + " in " + values; }
    }

    /**
     * All items must be true.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/ir/IrExamplesTest.java">IrExamplesTest</a>):
     * {@snippet class="org.lambdaql.engine.ir.IrExamplesTest" region="pred-and"}
     *
     * @param items the conditions (two or more)
     */
    record And(List<Pred> items) implements Pred {
        /** Makes an unmodifiable copy of the items. */
        public And {
            items = List.copyOf(items);
        }
        @Override public String toString() {
            return items.stream().map(Pred::toString).collect(Collectors.joining(" && ", "(", ")"));
        }
    }

    /**
     * At least one item must be true.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/ir/IrExamplesTest.java">IrExamplesTest</a>):
     * {@snippet class="org.lambdaql.engine.ir.IrExamplesTest" region="pred-or"}
     *
     * @param items the conditions (two or more)
     */
    record Or(List<Pred> items) implements Pred {
        /** Makes an unmodifiable copy of the items. */
        public Or {
            items = List.copyOf(items);
        }
        @Override public String toString() {
            return items.stream().map(Pred::toString).collect(Collectors.joining(" || ", "(", ")"));
        }
    }

    /**
     * Logical NOT.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/decode/ConditionDecodeTest.java">ConditionDecodeTest</a>):
     * {@snippet class="org.lambdaql.engine.decode.ConditionDecodeTest" region="not-composite"}
     *
     * @param operand the condition to negate
     */
    record Not(Pred operand) implements Pred {
        @Override public String toString() { return "!" + operand; }
    }

    /**
     * A condition that is always true or always false.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/decode/ConditionDecodeTest.java">ConditionDecodeTest</a>):
     * {@snippet class="org.lambdaql.engine.decode.ConditionDecodeTest" region="constants"}
     *
     * @param value the constant value
     */
    record Bool(boolean value) implements Pred {
        /** Always true. */
        public static final Bool TRUE = new Bool(true);
        /** Always false. */
        public static final Bool FALSE = new Bool(false);
        @Override public String toString() { return String.valueOf(value); }
    }

    /**
     * Makes {@code a AND b}. Nested ANDs are made flat, and constants are folded.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/ir/IrExamplesTest.java">IrExamplesTest</a>):
     * {@snippet class="org.lambdaql.engine.ir.IrExamplesTest" region="pred-and"}
     *
     * @param a first condition
     * @param b second condition
     * @return the joined condition
     */
    static Pred and(Pred a, Pred b) {
        if (a instanceof Bool x) return x.value() ? b : Bool.FALSE;
        if (b instanceof Bool y) return y.value() ? a : Bool.FALSE;
        List<Pred> out = new ArrayList<>();
        addFlat(out, a, true);
        addFlat(out, b, true);
        return new And(out);
    }

    /**
     * Makes {@code a OR b}. Nested ORs are made flat, and constants are folded.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/ir/IrExamplesTest.java">IrExamplesTest</a>):
     * {@snippet class="org.lambdaql.engine.ir.IrExamplesTest" region="pred-or"}
     *
     * @param a first condition
     * @param b second condition
     * @return the joined condition
     */
    static Pred or(Pred a, Pred b) {
        if (a instanceof Bool x) return x.value() ? Bool.TRUE : b;
        if (b instanceof Bool y) return y.value() ? Bool.TRUE : a;
        List<Pred> out = new ArrayList<>();
        addFlat(out, a, false);
        addFlat(out, b, false);
        return new Or(out);
    }

    /**
     * Makes {@code NOT p}. A double NOT is removed, and a compare gets the opposite operator.
     * Flipping the operator is safe with NULL values: {@code NOT (a < b)} and {@code a >= b}
     * are both UNKNOWN when a value is NULL, and a null-safe compare never gives UNKNOWN.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/ir/IrExamplesTest.java">IrExamplesTest</a>):
     * {@snippet class="org.lambdaql.engine.ir.IrExamplesTest" region="pred-not"}
     *
     * @param p the condition
     * @return the negated condition
     */
    static Pred not(Pred p) {
        if (p instanceof Not n) return n.operand();
        if (p instanceof Cmp c) return new Cmp(c.op().negate(), c.left(), c.right(), c.nullSafe());
        if (p instanceof Bool b) return b.value() ? Bool.FALSE : Bool.TRUE;
        return new Not(p);
    }

    private static void addFlat(List<Pred> out, Pred p, boolean and) {
        if (and && p instanceof And a) out.addAll(a.items());
        else if (!and && p instanceof Or o) out.addAll(o.items());
        else out.add(p);
    }
}
