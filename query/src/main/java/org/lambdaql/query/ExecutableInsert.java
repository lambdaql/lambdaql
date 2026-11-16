package org.lambdaql.query;

import org.lambdaql.engine.render.RenderedQuery;

import java.util.List;
import java.util.function.Supplier;

/**
 * An INSERT (or upsert) that can run. A list of rows can become more than one statement (ROADMAP U13),
 * so {@link #toHql()} returns a list.
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java">DmlQueryTest</a>):
 * {@snippet class="org.lambdaql.query.DmlQueryTest" region="insert"}
 *
 * @param <T> the entity type
 */
public class ExecutableInsert<T> {

    private final QueryContext context;
    private final Supplier<List<RenderedQuery>> plan;

    ExecutableInsert(QueryContext context, Supplier<List<RenderedQuery>> plan) {
        this.context = context;
        this.plan = plan;
    }

    /**
     * Builds the statements with the current captured values.
     *
     * @return the HQL statements, in the order they run
     */
    public List<RenderedQuery> toHql() {
        return plan.get();
    }

    /**
     * Runs the statements (own transaction, or the caller's with a shared entity manager).
     * For an upsert the count is what the database returns: MariaDB counts an updated row as 2, H2 and Oracle as 1 (D40).
     *
     * @return the sum of the row counts
     */
    public int execute() {
        return context.executeUpdate(toHql());
    }
}
