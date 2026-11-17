package org.lambdaql.query;

/**
 * A sorted subquery. Give a limit, then select the value: {@code orderBy(...).limit(1).select(x -> x.getId()).first()}.
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java">SubqueryTest</a>):
 * {@snippet class="org.lambdaql.query.SubqueryTest" region="top-one"}
 *
 * @param <X> the entity type
 */
public interface OrderedFrom<X> {
    /**
     * Limits the rows.
     *
     * @param count the most rows
     * @return this subquery
     */
    OrderedFrom<X> limit(int count);

    /**
     * Selects one value per row.
     *
     * @param pick the value
     * @param <R>  the value type
     * @return the values
     */
    <R> Values<R> select(Pick<X, R> pick);
}
