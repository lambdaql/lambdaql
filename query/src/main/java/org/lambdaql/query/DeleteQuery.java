package org.lambdaql.query;

/**
 * The start of a DELETE: {@code qb.delete(Order.class).where(...).execute()} (ROADMAP D28, M10).
 * {@code execute()} is not here, so deleting every row must say {@code all()} (U5).
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java">DmlQueryTest</a>):
 * {@snippet class="org.lambdaql.query.DmlQueryTest" region="delete"}
 *
 * @param <T> the entity type
 */
public class DeleteQuery<T> {

    private final Class<T> entityClass;
    private final QueryContext context;

    DeleteQuery(Class<T> entityClass, QueryContext context) {
        this.entityClass = entityClass;
        this.context = context;
    }

    /**
     * Limits the rows, with the same rules (and the same rows) as a select condition.
     *
     * @param condition the condition
     * @return the runnable DELETE
     */
    public ExecutableDelete<T> where(SelectQuery.Where<T> condition) {
        return new ExecutableDelete<>(entityClass, context).where(condition);
    }

    /**
     * Deletes every row. You must call this on purpose (U5).
     *
     * @return the runnable DELETE
     */
    public ExecutableDelete<T> all() {
        return new ExecutableDelete<>(entityClass, context);
    }
}
