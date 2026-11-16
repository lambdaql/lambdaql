package org.lambdaql.query;

import org.lambdaql.engine.render.HqlRenderer;
import org.lambdaql.engine.render.RenderedQuery;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.SequencedSet;

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
    private final List<HqlRenderer.SortBound> sorts;
    private Integer limit;
    private int offset;

    /**
     * Creates the query. Use {@code SelectQuery.orderBy}, {@code limit} or {@code offset} instead.
     *
     * @param entityClass the entity class
     * @param context     the builder context
     * @param wheres      the conditions so far
     * @param sorts       the sort keys, one entry per sort lambda (empty for paging only)
     */
    OrderedSelectQuery(Class<T> entityClass, QueryContext context, List<HqlRenderer.Bound> wheres,
                       List<HqlRenderer.SortBound> sorts) {
        this.entityClass = entityClass;
        this.context = context;
        this.wheres = List.copyOf(wheres);
        this.sorts = List.copyOf(sorts);
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
                .select(entityClass, wheres, sorts, isPaged());
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

    /**
     * Returns the first row in this order (after {@code offset}), or empty if there is no row.
     * Only one row is read.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ResultQueryTest.java">ResultQueryTest</a>):
     * {@snippet class="org.lambdaql.query.ResultQueryTest" region="first"}
     *
     * @return the first row, or {@link Optional#empty()}
     */
    public Optional<T> first() {
        if (limit != null && limit == 0) return Optional.empty();
        RenderedQuery q = new HqlRenderer(context.model(), entityClass.getClassLoader())
                .select(entityClass, wheres, sorts, true);
        return context.resultList(entityClass, q, offset, 1).stream().findFirst();
    }

    private boolean isPaged() {
        return limit != null || offset > 0;
    }

    /**
     * Runs the query and returns the rows as a set that keeps the row order ({@link LinkedHashSet}).
     * Entity rows never repeat (lambdaQL joins only to-one associations), so the set has the same rows as
     * {@link #toList()}.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ResultQueryTest.java">ResultQueryTest</a>):
     * {@snippet class="org.lambdaql.query.ResultQueryTest" region="to-set"}
     *
     * @return the rows in order
     * @throws IllegalStateException if the set is smaller than the number of rows: then the entity's
     *                               {@code equals}/{@code hashCode} treat different rows as equal, or its {@code @Id} is
     *                               not unique (for example a view), and rows would be lost without a sign
     */
    public SequencedSet<T> toSet() {
        return DmlParts.toSet(toList());
    }
}
