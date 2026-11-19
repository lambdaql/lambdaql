package org.lambdaql.query;

import org.lambdaql.engine.render.HqlRenderer;
import org.lambdaql.engine.render.RenderedQuery;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SequencedSet;

/**
 * A query with explicit entity joins (ROADMAP D44): {@code qb.from(Order.class).leftJoin(Customer.class, (o, c) -> ...)}.
 * Every lambda gets all entities in join order. Without {@code select}, the result is the main entity only; when the
 * join matches several rows, the main row repeats. After {@code leftJoin}, a condition on a joined entity column has
 * the SQL meaning: with no match it is not true, so the row is left out.
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java">JoinQueryTest</a>):
 * {@snippet class="org.lambdaql.query.JoinQueryTest" region="left-join"}
 *
 * @param <T> the main entity type
 * @param <U> the joined entity type
 */
public class SelectJoinQuery<T, U> {

    /**
     * A join condition with all entities so far and the new one.
     *
     * @param <T> the main entity type
     * @param <U> the joined entity type
     */
    @FunctionalInterface
    public interface JoinOn<T, U> extends Serializable {
        /**
         * The join condition. lambdaQL reads it; it never runs it.
         *
         * @param o an entity
         * @param c an entity
         * @return the condition
         */
        boolean on(T o, U c);
    }

    /**
     * A condition on all entities.
     *
     * @param <T> the main entity type
     * @param <U> the joined entity type
     */
    @FunctionalInterface
    public interface Where<T, U> extends Serializable {
        /**
         * The condition. lambdaQL reads it; it never runs it.
         *
         * @param o an entity
         * @param c an entity
         * @return the condition
         */
        boolean clause(T o, U c);
    }

    /**
     * A sort on all entities with the builder last: {@code (o, c, by) -> by.desc(...)} (D18).
     *
     * @param <T> the main entity type
     * @param <U> the joined entity type
     */
    @FunctionalInterface
    public interface SortBy<T, U> extends Serializable {
        /**
         * The sort. lambdaQL reads it; it never runs it.
         *
         * @param o an entity
         * @param c an entity
         * @param by the sort builder
         * @return the builder
         */
        By sort(T o, U c, By by);
    }

    /**
     * A select on all entities.
     *
     * @param <T> the main entity type
     * @param <U> the joined entity type
     * @param <R> the value type
     */
    @FunctionalInterface
    public interface Select<T, U, R> extends Serializable {
        /**
         * The value. lambdaQL reads it; it never runs it.
         *
         * @param o an entity
         * @param c an entity
         * @return the value
         */
        R clause(T o, U c);
    }

    final SelectQuery<T> base;
    final List<Class<?>> joined;
    final List<HqlRenderer.JoinBound> joinBounds;

    SelectJoinQuery(SelectQuery<T> from, List<Class<?>> joinedBefore, List<HqlRenderer.JoinBound> boundsBefore,
            Class<?> entity, boolean left, Object condition) {
        this(from, joinedBefore, boundsBefore, entity, left, condition, null);
    }

    SelectJoinQuery(SelectQuery<T> from, List<Class<?>> joinedBefore, List<HqlRenderer.JoinBound> boundsBefore,
            Class<?> entity, boolean left, Object condition, HqlRenderer.JoinSource source) {
        this.joined = new ArrayList<>(joinedBefore);
        this.joined.add(entity);
        this.base = new SelectQuery<>(from.entityClass(), from.context());
        from.wheres().forEach(base::addWhere);
        base.withDistinct(from.isDistinct());
        this.joinBounds = new ArrayList<>(boundsBefore);
        if (condition == null) {
            this.joinBounds.add(new HqlRenderer.JoinBound(entity, false, null, source));  // cross join (D45)
        } else {
            var ref = QueryContext.read(condition);
            this.joinBounds.add(new HqlRenderer.JoinBound(entity, left,
                    new HqlRenderer.Bound(base.context().analyzeWhere(ref, roots()), ref.capturedArgs()), source));
        }
        base.withJoins(joinBounds);
    }

    /**
     * Returns each row of the main entity once: {@code select distinct e0} (ROADMAP D44 extension). A join repeats a
     * row once per match; {@code distinct()} removes the repeats. A sort key must then be a column of the main entity
     * itself (J7). An {@code exists} subquery in {@code where} gives the same rows without repeats, often faster.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java">JoinQueryTest</a>):
     * {@snippet class="org.lambdaql.query.JoinQueryTest" region="join-distinct"}
     *
     * @return this
     */
    public SelectJoinQuery<T, U> distinct() {
        base.withDistinct(true);
        return this;
    }

    private List<Class<?>> roots() {
        List<Class<?>> roots = new ArrayList<>();
        roots.add(base.entityClass());
        roots.addAll(joined);
        return roots;
    }

    /**
     * Adds a condition on all entities (joined with AND).
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java">JoinQueryTest</a>):
     * {@snippet class="org.lambdaql.query.JoinQueryTest" region="left-join"}
     *
     * @param condition the condition
     * @return this query
     */
    public SelectJoinQuery<T, U> where(Where<T, U> condition) {
        var ref = QueryContext.read(condition);
        base.addWhere(new HqlRenderer.Bound(base.context().analyzeWhere(ref, roots()), ref.capturedArgs()));
        return this;
    }

    /**
     * Adds an inner join with a third entity: {@code join(Item.class, (o, c, i) -> i.getOrder().getId() == o.getId())}.
     *
     * @param entity    the joined entity class
     * @param condition the join condition
     * @param <V>       the joined entity type
     * @return the join query with one more entity
     */
    public <V> SelectJoin3Query<T, U, V> join(Class<V> entity, SelectJoin3Query.JoinOn<T, U, V> condition) {
        return new SelectJoin3Query<>(base, joined, joinBounds, entity, false, condition);
    }

    /**
     * Adds a left join with a third entity: main rows without a match stay.
     *
     * @param entity    the joined entity class
     * @param condition the join condition
     * @param <V>       the joined entity type
     * @return the join query with one more entity
     */
    public <V> SelectJoin3Query<T, U, V> leftJoin(Class<V> entity, SelectJoin3Query.JoinOn<T, U, V> condition) {
        return new SelectJoin3Query<>(base, joined, joinBounds, entity, true, condition);
    }

    /**
     * Adds a cross join with a third entity: every pair of rows (ROADMAP D45). Put the conditions in {@code where}.
     *
     * @param entity the joined entity class
     * @param <V>    the joined entity type
     * @return the join query with one more entity
     */
    public <V> SelectJoin3Query<T, U, V> crossJoin(Class<V> entity) {
        return new SelectJoin3Query<>(base, joined, joinBounds, entity, false, null);
    }

    /**
     * Joins the rows of a CTE as the third root (ROADMAP D44 extension).
     *
     * @param cte       the CTE
     * @param condition the join condition
     * @param <V>       the row type
     * @return the join query
     */
    public <V> SelectJoin3Query<T, U, V> join(Cte<V> cte, SelectJoin3Query.JoinOn<T, U, V> condition) {
        return new SelectJoin3Query<>(base, joined, joinBounds, cte.source.rowType, false, condition, cte.source.joinSource());
    }

    /**
     * Left-joins the rows of a CTE as the third root (ROADMAP D44 extension).
     *
     * @param cte       the CTE
     * @param condition the join condition
     * @param <V>       the row type
     * @return the join query
     */
    public <V> SelectJoin3Query<T, U, V> leftJoin(Cte<V> cte, SelectJoin3Query.JoinOn<T, U, V> condition) {
        return new SelectJoin3Query<>(base, joined, joinBounds, cte.source.rowType, true, condition, cte.source.joinSource());
    }

    /**
     * Joins the rows of a query as a derived table, as the third root (ROADMAP D44 extension).
     *
     * @param rows      the inner query
     * @param condition the join condition
     * @param <V>       the row type
     * @return the join query
     */
    public <V> SelectJoin3Query<T, U, V> join(ValueQuery<V> rows, SelectJoin3Query.JoinOn<T, U, V> condition) {
        DerivedSource d = new DerivedSource(rows, null);
        return new SelectJoin3Query<>(base, joined, joinBounds, d.rowType, false, condition, d.joinSource());
    }

    /**
     * Left-joins the rows of a query as a derived table, as the third root (ROADMAP D44 extension).
     *
     * @param rows      the inner query
     * @param condition the join condition
     * @param <V>       the row type
     * @return the join query
     */
    public <V> SelectJoin3Query<T, U, V> leftJoin(ValueQuery<V> rows, SelectJoin3Query.JoinOn<T, U, V> condition) {
        DerivedSource d = new DerivedSource(rows, null);
        return new SelectJoin3Query<>(base, joined, joinBounds, d.rowType, true, condition, d.joinSource());
    }

    /**
     * Sorts by values of all entities: {@code orderBy((o, c, by) -> by.desc(c.getName()).asc(o.getId()))} (D18).
     * Paths use a left join, so rows are not lost.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java">JoinQueryTest</a>):
     * {@snippet class="org.lambdaql.query.JoinQueryTest" region="sort"}
     *
     * @param spec the sort lambda
     * @return the ordered query
     */
    public OrderedJoinQuery<T, U> orderBy(SortBy<T, U> spec) {
        var ref = QueryContext.read(spec);
        var items = base.context().analyzeSortBy(ref, roots());
        if (base.isDistinct()) distinctKeys(items);
        OrderedJoinQuery<T, U> q = new OrderedJoinQuery<>(base.entityClass(), base.context(), base.wheres(),
                List.of(new HqlRenderer.SortBound(items, ref.capturedArgs())), joinBounds, roots());
        q.withDistinct(base.isDistinct());
        return q;
    }

    /**
     * Selects values of all entities: {@code select((o, c) -> new OrderView(o.getId(), c.getName()))}.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java">JoinQueryTest</a>):
     * {@snippet class="org.lambdaql.query.JoinQueryTest" region="select"}
     *
     * @param clause the select lambda
     * @param <R>    the value type
     * @return the value query
     */
    public <R> ValueQuery<R> select(Select<T, U, R> clause) {
        ValueQuery<R> q = SelectJoinQuery.selectValues(base.entityClass(), base.context(), base.wheres(), List.of(), joinBounds, roots(), clause);
        return base.isDistinct() ? q.distinct() : q;
    }

    /** J7 with {@code distinct()}: a sort key must be a column of the main entity itself (it is in {@code select distinct e0}). */
    static void distinctKeys(List<org.lambdaql.engine.ir.SortItem> items) {
        for (org.lambdaql.engine.ir.SortItem item : items) {
            if (!(item.key() instanceof org.lambdaql.engine.ir.Expr.Column c) || c.root() != 0 || c.association().contains(true)) {
                throw org.lambdaql.i18n.LqlMessage.DISTINCT_SORT_NOT_SELECTED.exception(item.key());
            }
        }
    }

    static <R> ValueQuery<R> selectValues(Class<?> root, QueryContext context, List<HqlRenderer.Bound> wheres,
                                          List<HqlRenderer.SortBound> sorts, List<HqlRenderer.JoinBound> joins,
                                          List<Class<?>> roots, Object clause) {
        var ref = QueryContext.read(clause);
        var projection = context.analyzeSelect(ref, roots);
        org.lambdaql.engine.sem.SelectReader.checkGrouping(projection.columns(), List.of(), List.of(), ref.describe());
        return new ValueQuery<R>(root, context, wheres, projection, ref.capturedArgs(), ref.classLoader(), null, List.of(),
                sorts, null, 0).withJoins(joins);
    }

    /**
     * Builds the HQL.
     *
     * @return the HQL and its parameters
     */
    public RenderedQuery toHql() {
        return base.toHql();
    }

    /**
     * Runs the query and returns the main entities (repeated when the join matches several rows).
     *
     * @return the rows
     */
    public List<T> toList() {
        return base.toList();
    }

    /**
     * Returns the first main entity (smallest id), or empty.
     *
     * @return the first row
     */
    public Optional<T> first() {
        return base.first();
    }

    /**
     * Counts the result rows.
     *
     * @return the number of rows
     */
    public long count() {
        return base.count();
    }

    /**
     * Checks if there is a result row.
     *
     * @return {@code true} if a row matches
     */
    public boolean exists() {
        return base.exists();
    }

    /**
     * Returns at most {@code count} rows, sorted by the id of every entity (main first, then the joined ones).
     *
     * @param count the most rows
     * @return the ordered query
     */
    public OrderedSelectQuery<T> limit(int count) {
        return base.limit(count);
    }

    /**
     * Runs the query and returns the main entities as a set in order.
     *
     * @return the rows
     */
    public SequencedSet<T> toSet() {
        return base.toSet();
    }
}
