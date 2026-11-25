package org.lambdaql.query;

import java.io.Serializable;

/**
 * A procedure call without a result: {@code p -> p.purge(30)}. The lambda is read, never run (ROADMAP M11).
 *
 * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java">ProcedureTest</a>):
 * {@snippet class="org.lambdaql.query.ProcedureTest" region="call"}
 *
 * @param <P> the stub interface
 */
@FunctionalInterface
public interface Exec<P> extends Serializable {
    /**
     * The call.
     *
     * @param procs the stub instance (never a real object; lambdaQL only reads the call)
     */
    void run(P procs);
}
