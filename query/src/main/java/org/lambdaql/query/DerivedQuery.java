package org.lambdaql.query;

import org.lambdaql.engine.render.HqlRenderer;
import org.lambdaql.engine.render.RenderedQuery;
import org.lambdaql.i18n.LqlMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A query over a derived table or a CTE (ROADMAP M8-2): {@code qb.from(qb.from(Order.class).groupBy(...).select(...))}.
 * The lambdas get the row (a record or Kotlin data class); {@code r.total()} reads the column {@code total}.
 * The result is the row type, or the values of {@code select}.
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DerivedQueryTest.java">DerivedQueryTest</a>):
 * {@snippet class="org.lambdaql.query.DerivedQueryTest" region="derived"}
 *
 * @param <R> the row type
 */
public class DerivedQuery<R> {

    private final Class<R> row;
    private final QueryContext context;
    private final DerivedSource source;
    private final List<HqlRenderer.Bound> wheres = new ArrayList<>();
    private List<HqlRenderer.SortBound> sorts = List.of();
    private Integer limit;
    private int offset;

    @SuppressWarnings("unchecked")
    DerivedQuery(DerivedSource source, QueryContext context) {
        this.source = source;
        this.context = context;
        this.row = (Class<R>) source.rowType;
    }

    /**
     * Adds a condition on the row columns (joined with AND).
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DerivedQueryTest.java">DerivedQueryTest</a>):
     * {@snippet class="org.lambdaql.query.DerivedQueryTest" region="derived"}
     *
     * @param condition the condition, for example {@code r -> r.total() > 100}
     * @return this query
     */
    public DerivedQuery<R> where(SelectQuery.Where<R> condition) {
        var ref = QueryContext.read(condition);
        wheres.add(new HqlRenderer.Bound(context.analyzeWhere(ref, List.of(row)), ref.capturedArgs()));
        return this;
    }

    /**
     * Sorts by row columns: {@code orderBy((r, by) -> by.desc(r.total()))}.
     *
     * @param spec the sort lambda
     * @return this query
     */
    public DerivedQuery<R> orderBy(SortBy<R> spec) {
        var ref = QueryContext.read(spec);
        sorts = List.of(new HqlRenderer.SortBound(context.analyzeSortBy(ref, List.of(row)), ref.capturedArgs()));
        return this;
    }

    /**
     * Returns at most {@code count} rows. The row has no id, so sort by a unique column for stable pages.
     *
     * @param count the most rows (0 or more)
     * @return this query
     */
    public DerivedQuery<R> limit(int count) {
        if (count < 0) throw new IllegalArgumentException(LqlMessage.LIMIT_NEGATIVE.format(count));
        this.limit = count;
        return this;
    }

    /**
     * Skips the first {@code count} rows.
     *
     * @param count rows to skip (0 or more)
     * @return this query
     */
    public DerivedQuery<R> offset(int count) {
        if (count < 0) throw new IllegalArgumentException(LqlMessage.OFFSET_NEGATIVE.format(count));
        this.offset = count;
        return this;
    }

    /**
     * Selects values of the row: {@code select(r -> r.customerId())}.
     *
     * @param clause the select lambda
     * @param <X>    the value type
     * @return the value query
     */
    public <X> ValueQuery<X> select(SelectQuery.Select<R, X> clause) {
        var ref = QueryContext.read(clause);
        var projection = context.analyzeSelect(ref, List.of(row));
        return new ValueQuery<X>(row, context, wheres, projection, ref.capturedArgs(), ref.classLoader(), null, List.of(),
                sorts, limit, offset).withSource(source);
    }

    private ValueQuery<R> rows() {
        return new ValueQuery<R>(row, context, wheres, source.rowProjection(), new Object[0], row.getClassLoader(), null,
                List.of(), sorts, limit, offset).withSource(source);
    }

    /**
     * Builds the HQL.
     *
     * @return the HQL and its parameters
     */
    public RenderedQuery toHql() {
        return rows().toHql();
    }

    /**
     * Runs the query.
     *
     * @return the rows
     */
    public List<R> toList() {
        return rows().toList();
    }

    /**
     * Returns the first row, or empty.
     *
     * @return the first row
     */
    public Optional<R> first() {
        return rows().first();
    }

    /**
     * Counts the rows that match the conditions (limit and offset are not used).
     *
     * @return the number of rows
     */
    public long count() {
        HqlRenderer r = new HqlRenderer(context.model(), row.getClassLoader());
        source.apply(r);
        return ((Number) context.rows(r.countRows(row, wheres), 0, null).get(0)).longValue();
    }
}
