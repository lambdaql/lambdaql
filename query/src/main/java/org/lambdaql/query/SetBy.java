package org.lambdaql.query;

/**
 * The assignment builder, for entities without setters (Kotlin {@code val}, DDD style):
 * {@code (o, by) -> by.set(o.getStatus(), 2).set(o.getPrice(), p)} (ROADMAP D28).
 * lambdaQL reads the calls; the lambda is never run.
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java">DmlQueryTest</a>):
 * {@snippet class="org.lambdaql.query.DmlQueryTest" region="update-by"}
 */
public interface SetBy {
    /**
     * Assigns a value to a column.
     *
     * @param column the column, written as a getter call, for example {@code o.getStatus()}
     * @param value  the new value
     * @param <V>    the value type
     * @return the builder, for the next assignment
     */
    <V> SetBy set(V column, V value);
}
