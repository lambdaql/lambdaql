package org.lambdaql.query;

import java.io.Serializable;

/**
 * A database function call on its own: {@code () -> OrderFns.discount(price)}. The lambda is read, never run (ROADMAP M11).
 *
 * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java">ProcedureTest</a>):
 * {@snippet class="org.lambdaql.query.ProcedureTest" region="call"}
 *
 * @param <R> the result type
 */
@FunctionalInterface
public interface FunctionCall<R> extends Serializable {
    /**
     * The call.
     *
     * @return the function value
     */
    R value();
}
