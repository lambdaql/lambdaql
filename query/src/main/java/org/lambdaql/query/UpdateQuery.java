package org.lambdaql.query;

/**
 * The start of an UPDATE: {@code qb.update(Order.class).set(...).where(...).execute()} (ROADMAP D28, M10).
 * Only {@code set} is available here, so an UPDATE without assignments cannot be written (rule U5).
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java">DmlQueryTest</a>):
 * {@snippet class="org.lambdaql.query.DmlQueryTest" region="update"}
 *
 * @param <T> the entity type
 */
public class UpdateQuery<T> {

    private final Class<T> entityClass;
    private final QueryContext context;

    /**
     * Creates the query. Use {@link QueryBuilder#update(Class)}.
     *
     * @param entityClass the entity class
     * @param context     the builder context
     */
    UpdateQuery(Class<T> entityClass, QueryContext context) {
        this.entityClass = entityClass;
        this.context = context;
    }

    /**
     * Adds assignments written with setters: {@code o -> { o.setStatus(2); }}.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java">DmlQueryTest</a>):
     * {@snippet class="org.lambdaql.query.DmlQueryTest" region="update"}
     *
     * @param assign the assignment lambda
     * @return the next step: more {@code set}, then {@code where} or {@code all}
     * @throws org.lambdaql.analyzer.LambdaAnalysisException if the lambda breaks a rule (U1, U3, U4, U6)
     */
    public UpdateSet<T> set(Assign<T> assign) {
        return new UpdateSet<>(entityClass, context).set(assign);
    }

    /**
     * Adds assignments written with the builder: {@code (o, by) -> by.set(o.getStatus(), 2)}.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java">DmlQueryTest</a>):
     * {@snippet class="org.lambdaql.query.DmlQueryTest" region="update-by"}
     *
     * @param assign the assignment lambda
     * @return the next step
     * @throws org.lambdaql.analyzer.LambdaAnalysisException if the lambda breaks a rule
     */
    public UpdateSet<T> set(AssignBy<T> assign) {
        return new UpdateSet<>(entityClass, context).set(assign);
    }
}
