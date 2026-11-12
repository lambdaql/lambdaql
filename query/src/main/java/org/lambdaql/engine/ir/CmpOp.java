package org.lambdaql.engine.ir;

/**
 * Compare operators.
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/ir/IrExamplesTest.java">IrExamplesTest</a>):
 * {@snippet class="org.lambdaql.engine.ir.IrExamplesTest" region="cmpop-symbol"}
 */
public enum CmpOp {
    /** {@code a = b} */
    EQ("="),
    /** {@code a <> b} */
    NE("<>"),
    /** {@code a < b} */
    LT("<"),
    /** {@code a <= b} */
    LE("<="),
    /** {@code a > b} */
    GT(">"),
    /** {@code a >= b} */
    GE(">=");

    private final String symbol;

    CmpOp(String symbol) {
        this.symbol = symbol;
    }

    /**
     * Returns the SQL operator text.
     *
     * @return the symbol, for example {@code "<>"} for {@link #NE}
     */
    public String symbol() {
        return symbol;
    }

    /**
     * Returns the opposite operator.
     * This is safe in SQL too: {@code NOT (a < b)} and {@code a >= b} both give UNKNOWN when a value is NULL.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/ir/IrExamplesTest.java">IrExamplesTest</a>):
     * {@snippet class="org.lambdaql.engine.ir.IrExamplesTest" region="cmpop-negate"}
     *
     * @return the opposite operator
     */
    public CmpOp negate() {
        return switch (this) {
            case EQ -> NE;
            case NE -> EQ;
            case LT -> GE;
            case LE -> GT;
            case GT -> LE;
            case GE -> LT;
        };
    }

    /**
     * Returns the operator to use when the two sides are swapped ({@code a op b} is the same as {@code b flip(op) a}).
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/ir/IrExamplesTest.java">IrExamplesTest</a>):
     * {@snippet class="org.lambdaql.engine.ir.IrExamplesTest" region="cmpop-flip"}
     *
     * @return the operator for swapped sides
     */
    public CmpOp flip() {
        return switch (this) {
            case EQ, NE -> this;
            case LT -> GT;
            case LE -> GE;
            case GT -> LT;
            case GE -> LE;
        };
    }
}
