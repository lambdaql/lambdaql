package org.lambdaql.query;

import org.lambdaql.engine.render.HqlRenderer;
import org.lambdaql.engine.render.RenderedQuery;

import java.util.ArrayList;
import java.util.List;

/**
 * An UPDATE that can run.
 * Like every JPA bulk statement, it changes the database directly: entities already loaded in the
 * {@code EntityManager} are not changed.
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java">DmlQueryTest</a>):
 * {@snippet class="org.lambdaql.query.DmlQueryTest" region="update"}
 *
 * @param <T> the entity type
 */
public class ExecutableUpdate<T> {

    private final Class<T> entityClass;
    private final QueryContext context;
    private final List<HqlRenderer.AssignBound> sets;
    private final List<HqlRenderer.Bound> wheres = new ArrayList<>();
    private final List<HqlRenderer.SortBound> sorts = new ArrayList<>();
    private Integer limit;

    ExecutableUpdate(Class<T> entityClass, QueryContext context, List<HqlRenderer.AssignBound> sets) {
        this.entityClass = entityClass;
        this.context = context;
        this.sets = List.copyOf(sets);
    }

    /**
     * Adds a condition (joined with AND).
     *
     * @param condition the condition
     * @return this
     */
    public ExecutableUpdate<T> where(SelectQuery.Where<T> condition) {
        wheres.add(DmlParts.where(context, entityClass, condition));
        return this;
    }

    /**
     * Builds the HQL with the current captured values.
     *
     * @return the HQL and its parameters
     */
    public RenderedQuery toHql() {
        return render(scope());
    }

    private RenderedQuery render(HqlRenderer.DmlScope scope) {
        return DmlParts.renderer(context, entityClass).update(entityClass, sets, wheres, scope);
    }

    /**
     * Runs the UPDATE. With {@code QueryBuilder(EntityManagerFactory)} it runs in its own transaction;
     * with a shared entity manager (Spring) it needs the caller's transaction.
     *
     * @return the number of changed rows (as the database counts them)
     */
    public int execute() {
        if (limit != null && limit == 0) return 0;
        return context.executeUpdate(List.of(toHql()));
    }

    /**
     * Sets the order for {@link #limit(int)}. Without a limit it has no use, so it is an error then.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java">DmlQueryTest</a>):
     * {@snippet class="org.lambdaql.query.DmlQueryTest" region="update-limit"}
     *
     * @param spec the sort lambda
     * @return this
     */
    public ExecutableUpdate<T> orderBy(SortBy<T> spec) {
        var ref = QueryContext.read(spec);
        sorts.add(new HqlRenderer.SortBound(context.analyzeSortBy(ref, List.of(entityClass)), ref.capturedArgs()));
        return this;
    }

    /**
     * Sets the order for {@link #limit(int)} with a reusable spec.
     *
     * @param spec the spec
     * @return this
     */
    public ExecutableUpdate<T> orderBy(SortSpec<T> spec) {
        sorts.addAll(spec.bind(context, entityClass));
        return this;
    }

    /**
     * Changes at most {@code count} rows: the first rows of {@code qb.from(X).where(same).orderBy(same)}
     * (by id when there is no order). Useful to work through many rows in small steps.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java">DmlQueryTest</a>):
     * {@snippet class="org.lambdaql.query.DmlQueryTest" region="update-limit"}
     *
     * @param count the most rows (0 or more)
     * @return this
     * @throws IllegalArgumentException if {@code count} is negative
     */
    public ExecutableUpdate<T> limit(int count) {
        if (count < 0) throw new IllegalArgumentException("limit must be 0 or more: " + count);
        this.limit = count;
        return this;
    }

    /**
     * Runs the statement with the "one row" protection: if two or more rows match, nothing is changed
     * and {@link jakarta.persistence.NonUniqueResultException} is thrown. It reads the ids first (at most 2),
     * then changes the one row by its id and the same conditions, so it can never change more than one row.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java">DmlQueryTest</a>):
     * {@snippet class="org.lambdaql.query.DmlQueryTest" region="execute-one"}
     *
     * @return 0 or 1
     * @throws jakarta.persistence.NonUniqueResultException if two or more rows match
     * @throws IllegalStateException                        if {@code limit} or {@code orderBy} was set
     */
    public int executeOne() {
        if (limit != null || !sorts.isEmpty()) throw new IllegalStateException("executeOne() cannot be used with limit/orderBy");
        HqlRenderer ids = DmlParts.renderer(context, entityClass);
        return context.executeOne(ids.ids(entityClass, wheres), id -> render(new HqlRenderer.DmlScope(List.of(), null, id)));
    }

    private HqlRenderer.DmlScope scope() {
        if (!sorts.isEmpty() && limit == null) throw new IllegalStateException("orderBy() needs limit()");
        return new HqlRenderer.DmlScope(List.copyOf(sorts), limit, null);
    }
}
