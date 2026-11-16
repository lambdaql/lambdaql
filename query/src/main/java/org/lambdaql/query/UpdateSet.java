package org.lambdaql.query;

import org.lambdaql.engine.render.HqlRenderer;

import java.util.ArrayList;
import java.util.List;

/**
 * An UPDATE with assignments. Call {@code set} again to add more (useful for PATCH: only the fields that have a value),
 * then {@code where} or {@code all}. {@code execute()} is not here, so an UPDATE of every row must say {@code all()} (U5).
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java">DmlQueryTest</a>):
 * {@snippet class="org.lambdaql.query.DmlQueryTest" region="update-patch"}
 *
 * @param <T> the entity type
 */
public class UpdateSet<T> {

    private final Class<T> entityClass;
    private final QueryContext context;
    private final List<HqlRenderer.AssignBound> sets = new ArrayList<>();

    UpdateSet(Class<T> entityClass, QueryContext context) {
        this.entityClass = entityClass;
        this.context = context;
    }

    /**
     * Adds assignments written with setters.
     *
     * @param assign the assignment lambda
     * @return this
     * @throws org.lambdaql.analyzer.LambdaAnalysisException if the lambda breaks a rule, also together with earlier
     *                                                       {@code set} calls (U3, U4)
     */
    public UpdateSet<T> set(Assign<T> assign) {
        sets.add(DmlParts.setters(context, entityClass, assign));
        DmlParts.checkRules(sets, entityClass);
        return this;
    }

    /**
     * Adds assignments written with the builder.
     *
     * @param assign the assignment lambda
     * @return this
     * @throws org.lambdaql.analyzer.LambdaAnalysisException if the lambda breaks a rule
     */
    public UpdateSet<T> set(AssignBy<T> assign) {
        sets.add(DmlParts.builder(context, entityClass, assign));
        DmlParts.checkRules(sets, entityClass);
        return this;
    }

    /**
     * Limits the rows, with the same rules as a select condition. The rows are the same as
     * {@code qb.from(X).where(same lambda)} (D36): association paths become subqueries (U9).
     *
     * @param condition the condition
     * @return the runnable UPDATE
     */
    public ExecutableUpdate<T> where(SelectQuery.Where<T> condition) {
        return new ExecutableUpdate<>(entityClass, context, sets).where(condition);
    }

    /**
     * Changes every row. You must call this on purpose; there is no UPDATE without {@code where} or {@code all} (U5).
     *
     * @return the runnable UPDATE
     */
    public ExecutableUpdate<T> all() {
        return new ExecutableUpdate<>(entityClass, context, sets);
    }
}
