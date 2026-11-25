package org.lambdaql.query;

import org.lambdaql.engine.render.HqlRenderer;
import org.lambdaql.engine.render.RenderedQuery;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SequencedSet;

/**
 * A query with 5 explicitly joined entities (ROADMAP D44, D71): every lambda gets all entities in join order,
 * {@code (o, c, i, e3, e4) -> ...}. Up to 10 entities can be joined (ROADMAP D71). Without {@code select}, the result is the main entity only. When the
 * join matches several rows, the SQL has one row per match: Hibernate returns each main entity once, but
 * {@code count()} and {@code limit} count the SQL rows; {@code distinct()} makes them count main rows. After
 * {@code leftJoin}, a condition on a joined entity column has the SQL meaning: with no match it is not true, so the
 * row is left out.
 *
 * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java">JoinQueryTest</a>):
 * {@snippet class="org.lambdaql.query.JoinQueryTest" region="ten"}
 *
 * @param <T> the main entity type
 * @param <U> the joined entity type
 * @param <V> the joined entity type
 * @param <W> the joined entity type
 * @param <X> the joined entity type
 */
public class SelectJoin5Query<T, U, V, W, X> {

    /**
     * A join condition with all entities so far and the new one.
     *
     * @param <T> the main entity type
     * @param <U> the joined entity type
     * @param <V> the joined entity type
     * @param <W> the joined entity type
     * @param <X> the joined entity type
     */
    @FunctionalInterface
    public interface JoinOn<T, U, V, W, X> extends Serializable {
        /**
         * The join condition. lambdaQL reads it; it never runs it.
         *
         * @param o an entity
         * @param c an entity
         * @param i an entity
         * @param e3 an entity
         * @param e4 an entity
         * @return the condition
         */
        boolean on(T o, U c, V i, W e3, X e4);
    }

    /**
     * A condition on all entities.
     *
     * @param <T> the main entity type
     * @param <U> the joined entity type
     * @param <V> the joined entity type
     * @param <W> the joined entity type
     * @param <X> the joined entity type
     */
    @FunctionalInterface
    public interface Where<T, U, V, W, X> extends Serializable {
        /**
         * The condition. lambdaQL reads it; it never runs it.
         *
         * @param o an entity
         * @param c an entity
         * @param i an entity
         * @param e3 an entity
         * @param e4 an entity
         * @return the condition
         */
        boolean clause(T o, U c, V i, W e3, X e4);
    }

    /**
     * A sort on all entities: {@code (o, c, i, e3, e4) -> asc(c.getName()).desc(e4.getId())}, or one key.
     *
     * @param <T> the main entity type
     * @param <U> the joined entity type
     * @param <V> the joined entity type
     * @param <W> the joined entity type
     * @param <X> the joined entity type
     */
    @FunctionalInterface
    public interface SortBy<T, U, V, W, X> extends Serializable {
        /**
         * The sort. lambdaQL reads it; it never runs it.
         *
         * @param o an entity
         * @param c an entity
         * @param i an entity
         * @param e3 an entity
         * @param e4 an entity
         * @return one key, or the keys made with {@link By}
         */
        Comparable<?> sort(T o, U c, V i, W e3, X e4);
    }

    /**
     * A select on all entities.
     *
     * @param <T> the main entity type
     * @param <U> the joined entity type
     * @param <V> the joined entity type
     * @param <W> the joined entity type
     * @param <X> the joined entity type
     * @param <R> the value type
     */
    @FunctionalInterface
    public interface Select<T, U, V, W, X, R> extends Serializable {
        /**
         * The value. lambdaQL reads it; it never runs it.
         *
         * @param o an entity
         * @param c an entity
         * @param i an entity
         * @param e3 an entity
         * @param e4 an entity
         * @return the value
         */
        R clause(T o, U c, V i, W e3, X e4);
    }

    final SelectQuery<T> base;
    final List<Class<?>> joined;
    final List<HqlRenderer.JoinBound> joinBounds;

    SelectJoin5Query(SelectQuery<T> from, List<Class<?>> joinedBefore, List<HqlRenderer.JoinBound> boundsBefore,
            Class<?> entity, boolean left, Object condition) {
        this(from, joinedBefore, boundsBefore, entity, left, condition, null);
    }

    SelectJoin5Query(SelectQuery<T> from, List<Class<?>> joinedBefore, List<HqlRenderer.JoinBound> boundsBefore,
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
     * Makes each main row one SQL row: {@code select distinct e0} (ROADMAP D44 extension). A join has one SQL row per
     * match; Hibernate already returns each main entity once, but {@code count()} and {@code limit} count the SQL rows.
     * With {@code distinct()} they count main rows, so a page of {@code limit} rows is full. A sort key must then be a
     * column of the main entity itself (J7). An {@code exists} subquery in {@code where} gives the same rows without
     * repeated SQL rows, often faster.
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java">JoinQueryTest</a>):
     * {@snippet class="org.lambdaql.query.JoinQueryTest" region="join-distinct"}
     *
     * @return this
     */
    public SelectJoin5Query<T, U, V, W, X> distinct() {
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
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java">JoinQueryTest</a>):
     * {@snippet class="org.lambdaql.query.JoinQueryTest" region="left-join"}
     *
     * @param condition the condition
     * @return this query
     */
    public SelectJoin5Query<T, U, V, W, X> where(Where<T, U, V, W, X> condition) {
        var ref = QueryContext.read(condition);
        base.addWhere(new HqlRenderer.Bound(base.context().analyzeWhere(ref, roots()), ref.capturedArgs()));
        return this;
    }

    /**
     * Adds an inner join with a sixth entity: {@code join(Item.class, (o, c, i, e3, e4, e5) -> ...)}.
     *
     * @param entity    the joined entity class
     * @param condition the join condition
     * @param <Y>       the joined entity type
     * @return the join query with one more entity
     */
    public <Y> SelectJoin6Query<T, U, V, W, X, Y> join(Class<Y> entity, SelectJoin6Query.JoinOn<T, U, V, W, X, Y> condition) {
        return new SelectJoin6Query<>(base, joined, joinBounds, entity, false, condition);
    }

    /**
     * Adds a left join with a sixth entity: main rows without a match stay.
     *
     * @param entity    the joined entity class
     * @param condition the join condition
     * @param <Y>       the joined entity type
     * @return the join query with one more entity
     */
    public <Y> SelectJoin6Query<T, U, V, W, X, Y> leftJoin(Class<Y> entity, SelectJoin6Query.JoinOn<T, U, V, W, X, Y> condition) {
        return new SelectJoin6Query<>(base, joined, joinBounds, entity, true, condition);
    }

    /**
     * Adds a cross join with a sixth entity: every pair of rows (ROADMAP D45). Put the conditions in {@code where}.
     *
     * @param entity the joined entity class
     * @param <Y>    the joined entity type
     * @return the join query with one more entity
     */
    public <Y> SelectJoin6Query<T, U, V, W, X, Y> crossJoin(Class<Y> entity) {
        return new SelectJoin6Query<>(base, joined, joinBounds, entity, false, null);
    }

    /**
     * Joins the rows of a CTE as the sixth root (ROADMAP D44 extension).
     *
     * @param cte       the CTE
     * @param condition the join condition
     * @param <Y>       the row type
     * @return the join query
     */
    public <Y> SelectJoin6Query<T, U, V, W, X, Y> join(Cte<Y> cte, SelectJoin6Query.JoinOn<T, U, V, W, X, Y> condition) {
        return new SelectJoin6Query<>(base, joined, joinBounds, cte.source.rowType, false, condition, cte.source.joinSource());
    }

    /**
     * Left-joins the rows of a CTE as the sixth root (ROADMAP D44 extension).
     *
     * @param cte       the CTE
     * @param condition the join condition
     * @param <Y>       the row type
     * @return the join query
     */
    public <Y> SelectJoin6Query<T, U, V, W, X, Y> leftJoin(Cte<Y> cte, SelectJoin6Query.JoinOn<T, U, V, W, X, Y> condition) {
        return new SelectJoin6Query<>(base, joined, joinBounds, cte.source.rowType, true, condition, cte.source.joinSource());
    }

    /**
     * Joins the rows of a query as a derived table, as the sixth root (ROADMAP D44 extension).
     *
     * @param rows      the inner query
     * @param condition the join condition
     * @param <Y>       the row type
     * @return the join query
     */
    public <Y> SelectJoin6Query<T, U, V, W, X, Y> join(ValueQuery<Y> rows, SelectJoin6Query.JoinOn<T, U, V, W, X, Y> condition) {
        DerivedSource d = new DerivedSource(rows, null);
        return new SelectJoin6Query<>(base, joined, joinBounds, d.rowType, false, condition, d.joinSource());
    }

    /**
     * Left-joins the rows of a query as a derived table, as the sixth root (ROADMAP D44 extension).
     *
     * @param rows      the inner query
     * @param condition the join condition
     * @param <Y>       the row type
     * @return the join query
     */
    public <Y> SelectJoin6Query<T, U, V, W, X, Y> leftJoin(ValueQuery<Y> rows, SelectJoin6Query.JoinOn<T, U, V, W, X, Y> condition) {
        DerivedSource d = new DerivedSource(rows, null);
        return new SelectJoin6Query<>(base, joined, joinBounds, d.rowType, true, condition, d.joinSource());
    }

    /**
     * Sorts by values of all entities: {@code orderBy((o, c, i, e3, e4) -> desc(c.getName()).asc(o.getId()))} (D18, D59).
     * Paths use a left join, so rows are not lost.
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java">JoinQueryTest</a>):
     * {@snippet class="org.lambdaql.query.JoinQueryTest" region="sort"}
     *
     * @param spec the sort lambda
     * @return the ordered query
     */
    public OrderedJoin5Query<T, U, V, W, X> orderBy(SortBy<T, U, V, W, X> spec) {
        var ref = QueryContext.read(spec);
        var items = base.context().analyzeSortBy(ref, roots());
        if (base.isDistinct()) SelectJoinQuery.distinctKeys(items);
        OrderedJoin5Query<T, U, V, W, X> q = new OrderedJoin5Query<>(base.entityClass(), base.context(), base.wheres(),
                List.of(new HqlRenderer.SortBound(items, ref.capturedArgs())), joinBounds, roots());
        q.withDistinct(base.isDistinct());
        return q;
    }

    /**
     * Selects values of all entities: {@code select((o, c, i, e3, e4) -> new OrderView(o.getId(), c.getName()))}. An entity
     * itself is a value too: {@code select((o, c, i, e3, e4) -> e4)} or a record of entities (ROADMAP D70).
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java">JoinQueryTest</a>):
     * {@snippet class="org.lambdaql.query.JoinQueryTest" region="select"}
     *
     * @param clause the select lambda
     * @param <R>    the value type
     * @return the value query
     */
    public <R> ValueQuery<R> select(Select<T, U, V, W, X, R> clause) {
        ValueQuery<R> q = SelectJoinQuery.selectValues(base.entityClass(), base.context(), base.wheres(), List.of(), joinBounds, roots(), clause);
        return base.isDistinct() ? q.distinct() : q;
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
     * Runs the query and returns the main entities, each once (Hibernate removes the rows that the join repeats).
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
     * Counts the SQL rows: one per join match. After {@code distinct()} it counts main rows.
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
     * Runs the query and returns the main entities as a set in order: the same rows as {@link #toList()}, each once
     * (D68). Without {@code distinct()}, {@code limit} counts the SQL rows that the join repeats, so a page can have
     * fewer entities.
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java">JoinQueryTest</a>):
     * {@snippet class="org.lambdaql.query.JoinQueryTest" region="join-set"}
     *
     * @return the rows
     * @throws IllegalStateException if the entity's {@code equals}/{@code hashCode} treat two different rows as equal
     */
    public SequencedSet<T> toSet() {
        return base.toSet();
    }
}
