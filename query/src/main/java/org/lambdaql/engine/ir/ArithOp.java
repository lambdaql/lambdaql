package org.lambdaql.engine.ir;

/**
 * Math operations that can appear in a query lambda.
 *
 * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/ir/IrExamplesTest.java">IrExamplesTest</a>):
 * {@snippet class="org.lambdaql.engine.ir.IrExamplesTest" region="arith-symbol"}
 * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/decode/ConditionDecodeTest.java">ConditionDecodeTest</a>):
 * {@snippet class="org.lambdaql.engine.decode.ConditionDecodeTest" region="arith"}
 */
public enum ArithOp {
    /** {@code a + b} */
    ADD("+"),
    /** {@code a - b} */
    SUB("-"),
    /** {@code a * b} */
    MUL("*"),
    /** {@code a / b} */
    DIV("/"),
    /** {@code a % b} */
    REM("%");

    private final String symbol;

    ArithOp(String symbol) {
        this.symbol = symbol;
    }

    /**
     * Returns the operator as text.
     *
     * @return the symbol, for example {@code "+"}
     */
    public String symbol() {
        return symbol;
    }
}
