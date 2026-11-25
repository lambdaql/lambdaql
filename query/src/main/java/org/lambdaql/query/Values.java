package org.lambdaql.query;

/**
 * The values of a subquery.
 *
 * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java">SubqueryTest</a>):
 * {@snippet class="org.lambdaql.query.SubqueryTest" region="in"}
 *
 * @param <R> the value type
 */
public interface Values<R> {
    /**
     * {@code x in (select ...)}; with {@code !} it becomes {@code not exists (select 1 ... and value = x)}
     * (rule Q2: NOT IN gives no rows when the subquery has a NULL).
     * Kotlin {@code x in values} compiles to this call.
     *
     * @param value the outer value
     * @return a placeholder
     */
    boolean contains(R value);

    /**
     * The only value: allowed after {@code orderBy(...).limit(1)} (rule Q3).
     *
     * @return a placeholder
     */
    R first();
}
