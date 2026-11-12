package org.lambdaql.engine.decode;

import org.lambdaql.engine.ir.Expr;
import org.lambdaql.engine.ir.Pred;

/**
 * A node of the decision graph that the {@link Decoder} builds from jump instructions.
 * <p>
 * Short-circuit code ({@code &&}, {@code ||}) jumps to shared places, so the same node can have many parents.
 * The graph has no loops. {@link ConditionReducer} turns it back into a condition tree.
 *
 * <p>Diagram: {@code a || b} gives
 * <pre>{@code
 * Test(a) --true--> TRUE
 *         --false-> Test(b) --true--> TRUE
 *                           --false-> FALSE
 * }</pre>
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/decode/ConditionDecodeTest.java">ConditionDecodeTest</a>):
 * {@snippet class="org.lambdaql.engine.decode.ConditionDecodeTest" region="or-chain"}
 */
public sealed interface Node permits Node.Leaf, Node.Test, Node.Value {

    /**
     * An end of the graph with a fixed result. Only {@link #TRUE} and {@link #FALSE} exist,
     * so you can compare them with {@code ==}.
     */
    final class Leaf implements Node {
        /** The lambda returns {@code true} here. */
        public static final Leaf TRUE = new Leaf(true);
        /** The lambda returns {@code false} here. */
        public static final Leaf FALSE = new Leaf(false);
        /** The result value. */
        public final boolean value;

        private Leaf(boolean value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value ? "T" : "F";
        }
    }

    /**
     * A branch: go to {@link #onTrue} when {@link #atom} is true, else go to {@link #onFalse}.
     * The reducer changes these fields in place while it joins nodes.
     */
    final class Test implements Node {
        /** The condition of this branch (the jump is taken when it is true). */
        public Pred atom;
        /** Next node when the condition is true. */
        public Node onTrue;
        /** Next node when the condition is false. */
        public Node onFalse;
        /** Source line of the jump, or -1 if unknown. Used in error messages. */
        public final int line;

        /**
         * @param atom    the condition
         * @param onTrue  next node when true
         * @param onFalse next node when false
         * @param line    source line, or -1
         */
        public Test(Pred atom, Node onTrue, Node onFalse, int line) {
            this.atom = atom;
            this.onTrue = onTrue;
            this.onFalse = onFalse;
            this.line = line;
        }

        @Override
        public String toString() {
            return "Test(" + atom + " ? " + onTrue + " : " + onFalse + ")";
        }
    }

    /**
     * An end of the graph for a value lambda (select, orderBy key and so on).
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/decode/ConditionDecodeTest.java">ConditionDecodeTest</a>):
     * {@snippet class="org.lambdaql.engine.decode.ConditionDecodeTest" region="value-lambda"}
     *
     * @param value the returned value
     */
    record Value(Expr value) implements Node {
    }
}
