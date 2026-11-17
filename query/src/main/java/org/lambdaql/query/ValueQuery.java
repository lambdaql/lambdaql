package org.lambdaql.query;

import org.lambdaql.i18n.LqlMessage;
import jakarta.persistence.NonUniqueResultException;
import org.lambdaql.engine.render.HqlRenderer;
import org.lambdaql.engine.render.RenderedQuery;
import org.lambdaql.engine.sem.SelectReader;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.SequencedSet;

/**
 * A query that returns values instead of entities: {@code qb.from(Order.class).select(o -> o.getPrice())},
 * a record or DTO {@code select(o -> new OrderView(...))}, or aggregates with {@code groupBy} (ROADMAP M9).
 * <p>
 * lambdaQL selects only the columns and calls the constructor in Java (rule J2). Paths in select use a left join,
 * so rows without the association stay (J1).
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java">ProjectionTest</a>):
 * {@snippet class="org.lambdaql.query.ProjectionTest" region="record"}
 *
 * @param <R> the value type
 */
public class ValueQuery<R> {

    private final Class<?> root;
    private final QueryContext context;
    private final List<HqlRenderer.Bound> wheres;
    private final HqlRenderer.ValueBound select;
    private final HqlRenderer.ValueBound groupBy;
    private final List<HqlRenderer.Bound> havings;
    private final List<HqlRenderer.SortBound> sorts;
    private final Projector projector;
    private final org.lambdaql.engine.sem.SelectReader.Projection projection;
    private final ClassLoader loader;
    private DerivedSource source;
    private boolean distinct;
    private Integer limit;
    private int offset;

    ValueQuery(Class<?> root, QueryContext context, List<HqlRenderer.Bound> wheres, SelectReader.Projection projection,
               Object[] selectCaptured, ClassLoader loader, HqlRenderer.ValueBound groupBy, List<HqlRenderer.Bound> havings,
               List<HqlRenderer.SortBound> sorts, Integer limit, int offset) {
        this.root = root;
        this.context = context;
        this.wheres = List.copyOf(wheres);
        this.select = new HqlRenderer.ValueBound(projection.columns(), selectCaptured);
        this.groupBy = groupBy;
        this.havings = List.copyOf(havings);
        this.sorts = List.copyOf(sorts);
        this.projector = new Projector(projection, loader);
        this.projection = projection;
        this.loader = loader;
        this.limit = limit;
        this.offset = offset;
    }

    /**
     * Removes repeated rows in the database: {@code select distinct}. With a sort, the sort keys must be selected
     * values (rule J7).
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java">ProjectionTest</a>):
     * {@snippet class="org.lambdaql.query.ProjectionTest" region="distinct"}
     *
     * @return this query
     */
    public ValueQuery<R> distinct() {
        this.distinct = true;
        return this;
    }

    /**
     * Returns at most {@code count} rows.
     *
     * @param count the most rows (0 or more)
     * @return this query
     * @throws IllegalArgumentException if {@code count} is negative
     */
    public ValueQuery<R> limit(int count) {
        if (count < 0) throw new IllegalArgumentException(LqlMessage.LIMIT_NEGATIVE.format(count));
        this.limit = count;
        return this;
    }

    /**
     * Skips the first {@code count} rows.
     *
     * @param count rows to skip (0 or more)
     * @return this query
     * @throws IllegalArgumentException if {@code count} is negative
     */
    public ValueQuery<R> offset(int count) {
        if (count < 0) throw new IllegalArgumentException(LqlMessage.OFFSET_NEGATIVE.format(count));
        this.offset = count;
        return this;
    }

    /**
     * Builds the HQL with the current captured values.
     *
     * @return the HQL and its parameters
     */
    public RenderedQuery toHql() {
        return render(distinct);
    }

    private RenderedQuery render(boolean distinctRows) {
        return renderer()
                .values(root, wheres, select, groupBy, havings, sorts, distinctRows, limit != null || offset > 0);
    }

    /**
     * Runs the query.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java">ProjectionTest</a>):
     * {@snippet class="org.lambdaql.query.ProjectionTest" region="value"}
     *
     * @return the values in order
     * @throws IllegalStateException if a NULL goes into a primitive constructor argument (rule J4)
     */
    @SuppressWarnings("unchecked")
    public List<R> toList() {
        return (List<R>) projector.build(context.rows(toHql(), offset, limit));
    }

    /**
     * Runs the query with {@code select distinct} and returns the values as a set in order. Repeated values are
     * expected here, so there is no size check (unlike entity {@code toSet()}).
     *
     * @return the distinct values in order
     */
    @SuppressWarnings("unchecked")
    public SequencedSet<R> toSet() {
        return new LinkedHashSet<>((List<R>) projector.build(context.rows(render(true), offset, limit)));
    }

    /**
     * Returns the first value (in the query order), or empty.
     *
     * @return the first value
     */
    @SuppressWarnings("unchecked")
    public Optional<R> first() {
        if (limit != null && limit == 0) return Optional.empty();
        return ((List<R>) projector.build(context.rows(toHql(), offset, 1))).stream().findFirst();
    }

    /**
     * Returns the only value, or empty.
     *
     * @return the value
     * @throws NonUniqueResultException if two or more rows match
     */
    @SuppressWarnings("unchecked")
    public Optional<R> one() {
        List<R> rows = (List<R>) projector.build(context.rows(toHql(), offset, 2));
        if (rows.size() > 1) throw new NonUniqueResultException(LqlMessage.MORE_THAN_ONE_ROW.format(toHql().hql()));
        return rows.stream().findFirst();
    }

    // ------------------------------------------------------------------ explicit joins (ROADMAP D44)

    private List<HqlRenderer.JoinBound> joins = List.of();

    /** Sets the entity joins of a join query; the joined entities are roots 1, 2 ... Not public API. */
    ValueQuery<R> withJoins(List<HqlRenderer.JoinBound> joins) {
        this.joins = List.copyOf(joins);
        return this;
    }

    private HqlRenderer renderer() {
        HqlRenderer r = new HqlRenderer(context.model(), root.getClassLoader()).withJoins(joins);
        if (source != null) source.apply(r);
        return r;
    }

    /** Reads from a derived table or CTE instead of the entity (ROADMAP M8-2). Not public API. */
    ValueQuery<R> withSource(DerivedSource source) {
        this.source = source;
        return this;
    }

    /**
     * Writes this query as the inner query of a derived table or CTE: the values are named after the row type (J6),
     * and limit and offset are written in the HQL. Not public API.
     */
    String renderInner(HqlRenderer inner, List<String> names) {
        inner.withJoins(joins).withAliases(names);
        if (source != null) source.apply(inner);
        StringBuilder hql = new StringBuilder(inner.values(root, wheres, select, groupBy, havings, sorts, distinct,
                limit != null || offset > 0).hql());
        if (limit != null) hql.append(" limit ").append(limit);
        if (offset > 0) hql.append(" offset ").append(offset);
        return hql.toString();
    }

    /** The row type that select builds (a record or Kotlin data class), or null for one value. Not public API. */
    Class<?> rowClass() {
        if (!projection.constructs()) return null;
        try {
            return Class.forName(projection.className().replace('/', '.'), false, loader);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(org.lambdaql.i18n.LqlMessage.RESULT_CONSTRUCTOR_NOT_FOUND.format(projection.className()), e);
        }
    }

    QueryContext context() {
        return context;
    }
}
