package org.lambdaql.query;

/**
 * The subquery factory, given as the last argument of a condition lambda: {@code (o, sub) -> sub.from(Item.class)...}
 * (ROADMAP M8, D19). lambdaQL reads the calls from the bytecode; the lambda is never run, so the return values
 * are only placeholders with the right type.
 *
 * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java">SubqueryTest</a>):
 * {@snippet class="org.lambdaql.query.SubqueryTest" region="exists"}
 */
public interface Sub {
    /**
     * Starts a subquery on an entity.
     *
     * @param type the entity class
     * @param <X>  the entity type
     * @return the subquery
     */
    <X> From<X> from(Class<X> type);
}
