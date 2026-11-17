package org.lambdaql.query;

/**
 * Java bodies of the H2 test functions ({@code CREATE ALIAS ... FOR}), used by {@link FunctionTest}.
 * MariaDB and Oracle get the same functions in SQL.
 */
public final class H2Functions {

    private H2Functions() {
    }

    /**
     * The test function {@code SQ_FN_DISCOUNT}: 90% of the price.
     *
     * @param price the price, or null
     * @return 90% of the price, or null
     */
    public static Double discount(Double price) {
        return price == null ? null : price * 0.9;
    }

    /**
     * The test function {@code SQ_FN_IS_GOLD}: a grade of 5 or more.
     *
     * @param grade the grade, or null
     * @return {@code true} for 5 or more, or null
     */
    public static Boolean isGold(Integer grade) {
        return grade == null ? null : grade >= 5;
    }

    /**
     * The test function {@code SQ_FN_HALF}: half of the value.
     *
     * @param value the value, or null
     * @return half of it, or null
     */
    public static Double half(Double value) {
        return value == null ? null : value / 2;
    }
}
