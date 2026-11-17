package org.lambdaql.query;

import java.io.Serializable;

/**
 * A procedure call that gives a result: {@code p -> p.calc(id, rate)}. The lambda is read, never run (ROADMAP M11).
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java">ProcedureTest</a>):
 * {@snippet class="org.lambdaql.query.ProcedureTest" region="call"}
 *
 * @param <P> the stub interface
 * @param <R> the result type
 */
@FunctionalInterface
public interface Call<P, R> extends Serializable {
    /**
     * The call.
     *
     * @param procs the stub instance (never a real object; lambdaQL only reads the call)
     * @return the result of the procedure
     */
    R call(P procs);
}
