package org.lambdaql.engine.ir;

/**
 * Math operations that can appear in a query lambda.
 *
 * <p>Example: {@code o.getPrice() * 1.1} uses {@link #MUL}.
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
