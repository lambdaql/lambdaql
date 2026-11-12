package org.lambdaql.query;

import org.lambdaql.engine.render.HqlRenderer;
import org.lambdaql.engine.render.RenderedQuery;

import java.util.List;

/**
 * A select query after {@code orderBy}, {@code limit} or {@code offset}.
 * <p>
 * Only paging and running are left here, so you cannot add {@code where} after the sort or sort twice
 * (ROADMAP S5; the compiler checks it).
 * When the query is paged and the sort keys do not contain the id, lambdaQL adds {@code id asc} as the last key
 * (S9), so rows do not move between pages. Paging values go to Hibernate
 * ({@code setFirstResult}/{@code setMaxResults}); they are not in the HQL text.
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java">SortQueryTest</a>):
 * {@snippet class="org.lambdaql.query.SortQueryTest" region="sort-page"}
 *
 * @param <T> the entity type
 */
public class OrderedSelectQuery<T> {

    private final Class<T> entityClass;
    private final QueryContext context;
    private final List<HqlRenderer.Bound> wheres;
    private final HqlRenderer.SortBound sort;
    private Integer limit;
    private int offset;

    /**
     * Creates the query. Use {@code SelectQuery.orderBy}, {@code limit} or {@code offset} instead.
     *
     * @param entityClass the entity class
     * @param context     the builder context
     * @param wheres      the conditions so far
     * @param sort        the sort keys, or {@code null} (paging only)
     */
    OrderedSelectQuery(Class<T> entityClass, QueryContext context, List<HqlRenderer.Bound> wheres, HqlRenderer.SortBound sort) {
        this.entityClass = entityClass;
        this.context = context;
        this.wheres = List.copyOf(wheres);
        this.sort = sort;
    }

    /**
     * Returns at most {@code count} rows.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java">SortQueryTest</a>):
     * {@snippet class="org.lambdaql.query.SortQueryTest" region="sort-page"}
     *
     * @param count the most rows to return (0 or more)
     * @return this query
     * @throws IllegalArgumentException if {@code count} is negative
     */
    public OrderedSelectQuery<T> limit(int count) {
        if (count < 0) throw new IllegalArgumentException("limit must be 0 or more: " + count);
        this.limit = count;
        return this;
    }

    /**
     * Skips the first {@code count} rows.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java">SortQueryTest</a>):
     * {@snippet class="org.lambdaql.query.SortQueryTest" region="sort-page"}
     *
     * @param count rows to skip (0 or more)
     * @return this query
     * @throws IllegalArgumentException if {@code count} is negative
     */
    public OrderedSelectQuery<T> offset(int count) {
        if (count < 0) throw new IllegalArgumentException("offset must be 0 or more: " + count);
        this.offset = count;
        return this;
    }

    /**
     * Builds the HQL and the parameter values with the current captured values (including run-time directions).
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java">SortQueryTest</a>):
     * {@snippet class="org.lambdaql.query.SortQueryTest" region="sort-basic"}
     *
     * @return the HQL and its parameters
     */
    public RenderedQuery toHql() {
        return new HqlRenderer(context.model(), entityClass.getClassLoader())
                .select(entityClass, wheres, sort, isPaged());
    }

    /**
     * Runs the query and returns the rows in order.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java">SortQueryTest</a>):
     * {@snippet class="org.lambdaql.query.SortQueryTest" region="sort-page"}
     *
     * @return the rows
     */
    public List<T> toList() {
        return context.resultList(entityClass, toHql(), offset, limit);
    }

    private boolean isPaged() {
        return limit != null || offset > 0;
    }
}
