package org.lambdaql.engine.ir;

/**
 * Compare operators.
 *
 * <p>Example: {@code o.getStatus() >= 2} uses {@link #GE}.
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
     * @return the opposite operator
     *
     * <p>Example: {@code LT.negate()} is {@code GE}; {@code EQ.negate()} is {@code NE}.
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
     * @return the operator for swapped sides
     *
     * <p>Example: {@code 10 < o.price} is the same as {@code o.price > 10}, so {@code LT.flip()} is {@code GT}.
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
