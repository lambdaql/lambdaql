package org.lambdaql.query;

import org.lambdaql.engine.ir.Expr;
import org.lambdaql.engine.ir.Pred;
import org.lambdaql.engine.ir.SortItem;
import org.lambdaql.engine.load.LambdaRef;
import org.lambdaql.engine.render.HqlRenderer;
import org.lambdaql.engine.render.RenderedQuery;
import org.objectweb.asm.Type;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A select query on one entity: {@code qb.from(Order.class).where(...).toList()}.
 * <p>
 * Each {@link #where(Where)} call adds a condition; all conditions are joined with AND.
 * The lambda is analyzed when you call {@code where} (errors show up early), and only once per lambda class.
 * The HQL is built when the query runs, because captured values can change the HQL
 * (for example a null value gives {@code is null}).
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java">SelectQueryTest</a>):
 * {@snippet class="org.lambdaql.query.SelectQueryTest" region="to-list"}
 *
 * @param <T> the entity type
 */
public class SelectQuery<T> {

    private final Class<T> entityClass;
    private final QueryContext context;
    private final List<HqlRenderer.Bound> wheres = new ArrayList<>();

    /**
     * A condition lambda: {@code o -> o.getStatus() == 1}.
     *
     * @param <U> the entity type
     */
    @FunctionalInterface
    public interface Where<U> extends Serializable {
        /**
         * The condition. lambdaQL reads this method's bytecode; it never calls it.
         *
         * @param obj the entity
         * @return the condition
         */
        boolean clause(U obj);
    }

    /**
     * A join condition lambda (planned).
     *
     * @param <T> the main entity type
     * @param <U> the joined entity type
     */
    @FunctionalInterface
    public interface JoinOn<T, U> extends Serializable {
        /**
         * The join condition.
         *
         * @param t    the main entity
         * @param join the joined entity
         * @return the condition
         */
        boolean on(T t, U join);
    }

    /**
     * A value lambda: {@code o -> o.getPrice()} or {@code o -> new OrderView(...)}.
     *
     * @param <T> the entity type
     * @param <R> the value type
     */
    @FunctionalInterface
    public interface Select<T, R> extends Serializable {
        /**
         * The value. lambdaQL reads this method's bytecode; it never calls it.
         *
         * @param entity the entity
         * @return the value
         */
        R clause(T entity);
    }

    /**
     * Creates a select query. Use {@link QueryBuilder#from(Class)} instead.
     *
     * @param entityClass the entity class
     * @param context     the builder context
     */
    SelectQuery(Class<T> entityClass, QueryContext context) {
        this.entityClass = entityClass;
        this.context = context;
    }

    /**
     * Adds a condition. Several calls are joined with AND.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java">SelectQueryTest</a>):
     * {@snippet class="org.lambdaql.query.SelectQueryTest" region="where-many"}
     *
     * @param condition the condition lambda
     * @return this query
     * @throws org.lambdaql.analyzer.LambdaAnalysisException if the lambda cannot become a condition
     */
    public SelectQuery<T> where(Where<T> condition) {
        LambdaRef ref = QueryContext.read(condition);
        Pred pred = context.analyzeWhere(ref, List.of(entityClass));
        wheres.add(new HqlRenderer.Bound(pred, ref.capturedArgs()));
        return this;
    }

    /**
     * Builds the HQL and the parameter values with the current captured values. Useful for logs and tests.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java">SelectQueryTest</a>):
     * {@snippet class="org.lambdaql.query.SelectQueryTest" region="to-hql"}
     *
     * @return the HQL and its parameters
     */
    public RenderedQuery toHql() {
        return new HqlRenderer(context.model(), entityClass.getClassLoader()).select(entityClass, wheres);
    }

    /**
     * Runs the query and returns all rows.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java">SelectQueryTest</a>):
     * {@snippet class="org.lambdaql.query.SelectQueryTest" region="to-list"}
     *
     * @return the matching entities
     */
    public List<T> toList() {
        return context.resultList(entityClass, toHql(), 0, null);
    }

    /**
     * Sorts with the builder: {@code (o, by) -> by.desc(o.getPrice()).asc(o.getId())}.
     * The lambda must be one straight chain of {@code by} calls (S4).
     * Association paths used only here become {@code LEFT JOIN}s, so rows without the association stay (S8).
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java">SortQueryTest</a>):
     * {@snippet class="org.lambdaql.query.SortQueryTest" region="sort-basic"}
     *
     * @param spec the sort lambda
     * @return the sorted query (only paging and running are left)
     * @throws org.lambdaql.analyzer.LambdaAnalysisException if the lambda is not a chain of {@code by} calls,
     *                                                       or a key cannot become SQL
     */
    public OrderedSelectQuery<T> orderBy(SortBy<T> spec) {
        LambdaRef ref = QueryContext.read(spec);
        List<SortItem> items = context.analyzeSortBy(ref, List.of(entityClass));
        return new OrderedSelectQuery<>(entityClass, context, wheres, new HqlRenderer.SortBound(items, ref.capturedArgs()));
    }

    /**
     * Sorts ascending by one key: {@code o -> o.getPrice()} or {@code Order::getPrice}. NULL comes first (D16).
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java">SortQueryTest</a>):
     * {@snippet class="org.lambdaql.query.SortQueryTest" region="sort-key"}
     *
     * @param key the key lambda or a getter reference
     * @return the sorted query
     * @throws org.lambdaql.analyzer.LambdaAnalysisException if the key cannot become SQL
     */
    public OrderedSelectQuery<T> orderBy(SortKey<T> key) {
        return orderBy(key, Direction.ASC);
    }

    /**
     * Sorts by one key with a direction: {@code orderBy(o -> o.getPrice(), Direction.DESC)}.
     * The direction can be a value known only at run time.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java">SortQueryTest</a>):
     * {@snippet class="org.lambdaql.query.SortQueryTest" region="sort-key"}
     *
     * @param key       the key lambda or a getter reference
     * @param direction the direction (and NULL position)
     * @return the sorted query
     * @throws NullPointerException                          if {@code direction} is null
     * @throws org.lambdaql.analyzer.LambdaAnalysisException if the key cannot become SQL
     */
    public OrderedSelectQuery<T> orderBy(SortKey<T> key, Direction direction) {
        Objects.requireNonNull(direction, "direction");
        LambdaRef ref = QueryContext.read(key);
        Expr k = context.analyzeSortKey(ref, entityClass);
        SortItem item = new SortItem(k, SortItem.Kind.SORT, new Expr.Const(direction, Type.getType(Direction.class)));
        return new OrderedSelectQuery<>(entityClass, context, wheres, new HqlRenderer.SortBound(List.of(item), ref.capturedArgs()));
    }

    /**
     * Returns at most {@code count} rows, without your own sort.
     * The rows are sorted by the id, so pages are stable (S9).
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java">SortQueryTest</a>):
     * {@snippet class="org.lambdaql.query.SortQueryTest" region="page-only"}
     *
     * @param count the most rows to return (0 or more)
     * @return the paged query
     * @throws IllegalArgumentException if {@code count} is negative
     */
    public OrderedSelectQuery<T> limit(int count) {
        return new OrderedSelectQuery<>(entityClass, context, wheres, null).limit(count);
    }

    /**
     * Skips the first {@code count} rows, without your own sort.
     * The rows are sorted by the id, so pages are stable (S9).
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java">SortQueryTest</a>):
     * {@snippet class="org.lambdaql.query.SortQueryTest" region="page-only"}
     *
     * @param count rows to skip (0 or more)
     * @return the paged query
     * @throws IllegalArgumentException if {@code count} is negative
     */
    public OrderedSelectQuery<T> offset(int count) {
        return new OrderedSelectQuery<>(entityClass, context, wheres, null).offset(count);
    }

    /**
     * Planned (milestone M9): select values or DTOs. Not implemented yet; it does nothing now.
     *
     * @param clause the value lambda
     * @param <R>    the value type
     * @return this query
     */
    @Deprecated
    public <R> SelectQuery<T> select(Select<T, R> clause) {
        return this;
    }

    /**
     * Planned: explicit joins. Not implemented yet.
     *
     * @param entityClass the joined entity
     * @param onCondition the join condition
     * @param <U>         the joined entity type
     * @return a placeholder
     */
    @Deprecated
    public <U> SelectJoinQuery<T, U> leftJoin(Class<U> entityClass, JoinOn<T, U> onCondition) {
        return new SelectJoinQuery<>();
    }
}
