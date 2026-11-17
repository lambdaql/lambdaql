package org.lambdaql.query;

import java.util.function.Supplier;

/**
 * A procedure or function call that is ready to run: {@code qb.call(Procs.class, p -> p.calc(id, rate)).execute()}
 * (ROADMAP M11, D30).
 * <p>
 * The procedure runs in the current transaction when there is one; changes in the persistence context are flushed
 * first (PR9). Without a transaction, a {@code QueryBuilder} made from an {@code EntityManagerFactory} uses its own.
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java">ProcedureTest</a>):
 * {@snippet class="org.lambdaql.query.ProcedureTest" region="call"}
 *
 * @param <R> the result type ({@code Void} for {@code exec})
 */
public class ProcedureCall<R> {

    private final Supplier<R> run;

    ProcedureCall(Supplier<R> run) {
        this.run = run;
    }

    /**
     * Runs the call.
     *
     * @return the result, or null for {@code exec}
     * @throws org.lambdaql.LambdaQLException if the stub does not match the database procedure (PR2) or the result
     *                                        does not fit the return type
     */
    public R execute() {
        return run.get();
    }
}
