package org.lambdaql;

import org.lambdaql.i18n.LqlMessage;

/**
 * Aggregate functions for {@code select} and {@code having} lambdas (ROADMAP D27, M9).
 * The only static helper class of lambdaQL: aggregates are SQL standard functions, so the library provides them.
 * <p>
 * The methods are only <b>markers</b>: lambdaQL reads the calls from the lambda bytecode and never runs the lambda.
 * Calling them anywhere else throws {@link IllegalStateException}. Using them in {@code where} is an analysis error;
 * use {@code having} for conditions on aggregates.
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java">ProjectionTest</a>):
 * {@snippet class="org.lambdaql.query.ProjectionTest" region="group-by"}
 */
public final class Agg {

    private Agg() {
    }

    private static IllegalStateException outside() {
        return new IllegalStateException(LqlMessage.PLACEHOLDER_CALLED.format("Agg"));
    }

    /**
     * {@code count(*)}: the number of rows.
     *
     * @return a placeholder
     */
    public static long count() {
        throw outside();
    }

    /**
     * {@code count(x)}: the number of rows where {@code x} is not NULL.
     *
     * @param x a value
     * @return a placeholder
     */
    public static long count(Object x) {
        throw outside();
    }

    /**
     * {@code count(distinct x)}.
     *
     * @param x a value
     * @return a placeholder
     */
    public static long countDistinct(Object x) {
        throw outside();
    }

    /**
     * {@code sum(x)}: NULL when there is no row (rule J4).
     *
     * @param x   a number
     * @param <N> the number type
     * @return a placeholder
     */
    public static <N extends Number> N sum(N x) {
        throw outside();
    }

    /**
     * {@code avg(x)}: NULL when there is no row (rule J4).
     *
     * @param x a number
     * @return a placeholder
     */
    public static Double avg(Number x) {
        throw outside();
    }

    /**
     * {@code min(x)}.
     *
     * @param x   a value
     * @param <R> the value type
     * @return a placeholder
     */
    public static <R extends Comparable<?>> R min(R x) {
        throw outside();
    }

    /**
     * {@code max(x)}.
     *
     * @param x   a value
     * @param <R> the value type
     * @return a placeholder
     */
    public static <R extends Comparable<?>> R max(R x) {
        throw outside();
    }
}
