package org.lambdaql.query;

import org.lambdaql.engine.render.HqlRenderer;
import org.lambdaql.engine.render.RenderedQuery;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SequencedSet;

/**
 * A query with 10 explicitly joined entities (ROADMAP D44, D71): every lambda gets all entities in join order,
 * {@code (o, c, i, e3, e4, e5, e6, e7, e8, e9) -> ...}. Without {@code select}, the result is the main entity only. When the
 * join matches several rows, the SQL has one row per match: Hibernate returns each main entity once, but
 * {@code count()} and {@code limit} count the SQL rows; {@code distinct()} makes them count main rows. After
 * {@code leftJoin}, a condition on a joined entity column has the SQL meaning: with no match it is not true, so the
 * row is left out.
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java">JoinQueryTest</a>):
 * {@snippet class="org.lambdaql.query.JoinQueryTest" region="ten"}
 *
 * @param <T> the main entity type
 * @param <U> the joined entity type
 * @param <V> the joined entity type
 * @param <W> the joined entity type
 * @param <X> the joined entity type
 * @param <Y> the joined entity type
 * @param <Z> the joined entity type
 * @param <A> the joined entity type
 * @param <B> the joined entity type
 * @param <C> the joined entity type
 */
public class SelectJoin10Query<T, U, V, W, X, Y, Z, A, B, C> {

    /**
     * A join condition with all entities so far and the new one.
     *
     * @param <T> the main entity type
     * @param <U> the joined entity type
     * @param <V> the joined entity type
     * @param <W> the joined entity type
     * @param <X> the joined entity type
     * @param <Y> the joined entity type
     * @param <Z> the joined entity type
     * @param <A> the joined entity type
     * @param <B> the joined entity type
     * @param <C> the joined entity type
     */
    @FunctionalInterface
    public interface JoinOn<T, U, V, W, X, Y, Z, A, B, C> extends Serializable {
        /**
         * The join condition. lambdaQL reads it; it never runs it.
         *
         * @param o an entity
         * @param c an entity
         * @param i an entity
         * @param e3 an entity
         * @param e4 an entity
         * @param e5 an entity
         * @param e6 an entity
         * @param e7 an entity
         * @param e8 an entity
         * @param e9 an entity
         * @return the condition
         */
        boolean on(T o, U c, V i, W e3, X e4, Y e5, Z e6, A e7, B e8, C e9);
    }

    /**
     * A condition on all entities.
     *
     * @param <T> the main entity type
     * @param <U> the joined entity type
     * @param <V> the joined entity type
     * @param <W> the joined entity type
     * @param <X> the joined entity type
     * @param <Y> the joined entity type
     * @param <Z> the joined entity type
     * @param <A> the joined entity type
     * @param <B> the joined entity type
     * @param <C> the joined entity type
     */
    @FunctionalInterface
    public interface Where<T, U, V, W, X, Y, Z, A, B, C> extends Serializable {
        /**
         * The condition. lambdaQL reads it; it never runs it.
         *
         * @param o an entity
         * @param c an entity
         * @param i an entity
         * @param e3 an entity
         * @param e4 an entity
         * @param e5 an entity
         * @param e6 an entity
         * @param e7 an entity
         * @param e8 an entity
         * @param e9 an entity
         * @return the condition
         */
        boolean clause(T o, U c, V i, W e3, X e4, Y e5, Z e6, A e7, B e8, C e9);
    }

    /**
     * A sort on all entities: {@code (o, c, i, e3, e4, e5, e6, e7, e8, e9) -> asc(c.getName()).desc(e9.getId())}, or one key.
     *
     * @param <T> the main entity type
     * @param <U> the joined entity type
     * @param <V> the joined entity type
     * @param <W> the joined entity type
     * @param <X> the joined entity type
     * @param <Y> the joined entity type
     * @param <Z> the joined entity type
     * @param <A> the joined entity type
     * @param <B> the joined entity type
     * @param <C> the joined entity type
     */
    @FunctionalInterface
    public interface SortBy<T, U, V, W, X, Y, Z, A, B, C> extends Serializable {
        /**
         * The sort. lambdaQL reads it; it never runs it.
         *
         * @param o an entity
         * @param c an entity
         * @param i an entity
         * @param e3 an entity
         * @param e4 an entity
         * @param e5 an entity
         * @param e6 an entity
         * @param e7 an entity
         * @param e8 an entity
         * @param e9 an entity
         * @return one key, or the keys made with {@link By}
         */
        Comparable<?> sort(T o, U c, V i, W e3, X e4, Y e5, Z e6, A e7, B e8, C e9);
    }

    /**
     * A select on all entities.
     *
     * @param <T> the main entity type
     * @param <U> the joined entity type
     * @param <V> the joined entity type
     * @param <W> the joined entity type
     * @param <X> the joined entity type
     * @param <Y> the joined entity type
     * @param <Z> the joined entity type
     * @param <A> the joined entity type
     * @param <B> the joined entity type
     * @param <C> the joined entity type
     * @param <R> the value type
     */
    @FunctionalInterface
    public interface Select<T, U, V, W, X, Y, Z, A, B, C, R> extends Serializable {
        /**
         * The value. lambdaQL reads it; it never runs it.
         *
         * @param o an entity
         * @param c an entity
         * @param i an entity
         * @param e3 an entity
         * @param e4 an entity
         * @param e5 an entity
         * @param e6 an entity
         * @param e7 an entity
         * @param e8 an entity
         * @param e9 an entity
         * @return the value
         */
        R clause(T o, U c, V i, W e3, X e4, Y e5, Z e6, A e7, B e8, C e9);
    }

    final SelectQuery<T> base;
    final List<Class<?>> joined;
    final List<HqlRenderer.JoinBound> joinBounds;

    SelectJoin10Query(SelectQuery<T> from, List<Class<?>> joinedBefore, List<HqlRenderer.JoinBound> boundsBefore,
            Class<?> entity, boolean left, Object condition) {
        this(from, joinedBefore, boundsBefore, entity, left, condition, null);
    }

    SelectJoin10Query(SelectQuery<T> from, List<Class<?>> joinedBefore, List<HqlRenderer.JoinBound> boundsBefore,
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
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java">JoinQueryTest</a>):
     * {@snippet class="org.lambdaql.query.JoinQueryTest" region="join-distinct"}
     *
     * @return this
     */
    public SelectJoin10Query<T, U, V, W, X, Y, Z, A, B, C> distinct() {
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
    public SelectJoin10Query<T, U, V, W, X, Y, Z, A, B, C> where(Where<T, U, V, W, X, Y, Z, A, B, C> condition) {
        var ref = QueryContext.read(condition);
        base.addWhere(new HqlRenderer.Bound(base.context().analyzeWhere(ref, roots()), ref.capturedArgs()));
        return this;
    }

    /**
     * Sorts by values of all entities: {@code orderBy((o, c, i, e3, e4, e5, e6, e7, e8, e9) -> desc(c.getName()).asc(o.getId()))} (D18, D59).
     * Paths use a left join, so rows are not lost.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java">JoinQueryTest</a>):
     * {@snippet class="org.lambdaql.query.JoinQueryTest" region="sort"}
     *
     * @param spec the sort lambda
     * @return the ordered query
     */
    public OrderedJoin10Query<T, U, V, W, X, Y, Z, A, B, C> orderBy(SortBy<T, U, V, W, X, Y, Z, A, B, C> spec) {
        var ref = QueryContext.read(spec);
        var items = base.context().analyzeSortBy(ref, roots());
        if (base.isDistinct()) SelectJoinQuery.distinctKeys(items);
        OrderedJoin10Query<T, U, V, W, X, Y, Z, A, B, C> q = new OrderedJoin10Query<>(base.entityClass(), base.context(), base.wheres(),
                List.of(new HqlRenderer.SortBound(items, ref.capturedArgs())), joinBounds, roots());
        q.withDistinct(base.isDistinct());
        return q;
    }

    /**
     * Selects values of all entities: {@code select((o, c, i, e3, e4, e5, e6, e7, e8, e9) -> new OrderView(o.getId(), c.getName()))}. An entity
     * itself is a value too: {@code select((o, c, i, e3, e4, e5, e6, e7, e8, e9) -> e9)} or a record of entities (ROADMAP D70).
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java">JoinQueryTest</a>):
     * {@snippet class="org.lambdaql.query.JoinQueryTest" region="select"}
     *
     * @param clause the select lambda
     * @param <R>    the value type
     * @return the value query
     */
    public <R> ValueQuery<R> select(Select<T, U, V, W, X, Y, Z, A, B, C, R> clause) {
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
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java">JoinQueryTest</a>):
     * {@snippet class="org.lambdaql.query.JoinQueryTest" region="join-set"}
     *
     * @return the rows
     * @throws IllegalStateException if the entity's {@code equals}/{@code hashCode} treat two different rows as equal
     */
    public SequencedSet<T> toSet() {
        return base.toSet();
    }
}
