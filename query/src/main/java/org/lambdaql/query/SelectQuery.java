package org.lambdaql.query;

import org.lambdaql.i18n.LqlMessage;
import jakarta.persistence.NonUniqueResultException;
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
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.SequencedSet;

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
     * @throws org.lambdaql.LambdaAnalysisException if the lambda cannot become a condition
     */
    public SelectQuery<T> where(Where<T> condition) {
        LambdaRef ref = QueryContext.read(condition);
        Pred pred = context.analyzeWhere(ref, List.of(entityClass));
        wheres.add(new HqlRenderer.Bound(pred, ref.capturedArgs()));
        return this;
    }

    /**
     * Adds a condition that can use subqueries: {@code (o, sub) -> sub.from(Item.class).where(i -> ...).exists()}
     * (ROADMAP M8). Several calls are joined with AND.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java">SubqueryTest</a>):
     * {@snippet class="org.lambdaql.query.SubqueryTest" region="exists"}
     *
     * @param condition the condition lambda
     * @return this query
     * @throws org.lambdaql.LambdaAnalysisException if the lambda cannot become a condition
     */
    public SelectQuery<T> where(WhereSub<T> condition) {
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
        return renderer().select(entityClass, wheres);
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
     * Returns the first row, or empty if there is no row.
     * Without {@code orderBy}, "first" means the row with the smallest id (S9), so the result is always the same.
     * To choose the order, use {@code orderBy(...).first()}.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ResultQueryTest.java">ResultQueryTest</a>):
     * {@snippet class="org.lambdaql.query.ResultQueryTest" region="first"}
     *
     * @return the first row, or {@link Optional#empty()}
     */
    public Optional<T> first() {
        return limit(1).first();
    }

    /**
     * Returns the only row, or empty if there is no row.
     * Use it when the condition can match at most one row, for example a unique key.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ResultQueryTest.java">ResultQueryTest</a>):
     * {@snippet class="org.lambdaql.query.ResultQueryTest" region="one"}
     *
     * @return the row, or {@link Optional#empty()}
     * @throws NonUniqueResultException if two or more rows match
     */
    public Optional<T> one() {
        List<T> rows = context.resultList(entityClass, toHql(), 0, 2);
        if (rows.size() > 1) {
            throw new NonUniqueResultException(LqlMessage.MORE_THAN_ONE_ROW.format(toHql().hql()));
        }
        return rows.stream().findFirst();
    }

    /**
     * Counts the rows that match. For a paged screen, count with this query, then add {@code orderBy} and paging.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ResultQueryTest.java">ResultQueryTest</a>):
     * {@snippet class="org.lambdaql.query.ResultQueryTest" region="count"}
     *
     * @return the number of rows
     */
    public long count() {
        return context.resultList(Long.class, countHql(), 0, null).get(0);
    }

    /**
     * Counts the rows whose value of one column is not NULL: {@code select count(e0.description) from ...}.
     * After {@link #distinct()} each different value is counted once: {@code count(distinct e0.customer.id)}.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/CountColumnTest.java">CountColumnTest</a>):
     * {@snippet class="org.lambdaql.query.CountColumnTest" region="count-column"}
     * {@snippet class="org.lambdaql.query.CountColumnTest" region="count-distinct"}
     *
     * @param column the column lambda, for example {@code o -> o.getDescription()} or {@code o -> o.getCustomer().getId()}
     * @return the number of non-NULL values, or of different non-NULL values after {@link #distinct()}
     * @throws org.lambdaql.LambdaAnalysisException if the lambda is not one value of the entity
     */
    public long count(Select<T, ?> column) {
        return context.resultList(Long.class, countHql(column, distinct), 0, null).get(0);
    }

    /**
     * Makes the query distinct: {@code count(column)} then counts each different value once
     * ({@code count(distinct x)}), and the rows are read with {@code select distinct e0}. Without a join the rows
     * of the entity are already different, so only {@code count(column)} changes.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/CountColumnTest.java">CountColumnTest</a>):
     * {@snippet class="org.lambdaql.query.CountColumnTest" region="count-distinct"}
     *
     * @return this
     */
    public SelectQuery<T> distinct() {
        this.distinct = true;
        return this;
    }

    /**
     * Builds the HQL used by {@link #count(Select)}. The column lambda is read like
     * a {@code select} lambda and its value is put in {@code count(...)}, so paths join the same way as in
     * {@code select}.
     *
     * @param column   the column lambda
     * @param distinct {@code count(distinct x)} instead of {@code count(x)}
     * @return {@code select count(x) from ...}
     */
    RenderedQuery countHql(Select<T, ?> column, boolean distinct) {
        LambdaRef ref = QueryContext.read(column);
        var projection = context.analyzeSelect(ref, List.of(entityClass));
        if (projection.constructs() || projection.columns().size() != 1
                || projection.columns().get(0) instanceof org.lambdaql.engine.ir.Expr.Aggregate) {
            throw org.lambdaql.i18n.LqlMessage.COUNT_COLUMN_NOT_VALUE.exceptionAt(ref.describe());
        }
        var fn = distinct ? org.lambdaql.engine.ir.Expr.Aggregate.Fn.COUNT_DISTINCT : org.lambdaql.engine.ir.Expr.Aggregate.Fn.COUNT;
        var counted = new org.lambdaql.engine.sem.SelectReader.Projection(
                List.of(new org.lambdaql.engine.ir.Expr.Aggregate(fn, projection.columns().get(0), org.objectweb.asm.Type.LONG_TYPE)), null, null);
        return new ValueQuery<Long>(entityClass, context, wheres, counted, ref.capturedArgs(), ref.classLoader(), null, List.of(),
                List.of(), null, 0).withJoins(joins).toHql();
    }

    /**
     * Checks if at least one row matches. Only one row is read, and no entity is loaded.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ResultQueryTest.java">ResultQueryTest</a>):
     * {@snippet class="org.lambdaql.query.ResultQueryTest" region="exists"}
     *
     * @return {@code true} if a row matches
     */
    public boolean exists() {
        return !context.resultList(Integer.class, existsHql(), 0, 1).isEmpty();
    }

    /**
     * Builds the HQL used by {@link #count()}.
     *
     * @return {@code select count(e0) from ...}
     */
    RenderedQuery countHql() {
        return renderer().count(entityClass, wheres);
    }

    /**
     * Builds the HQL used by {@link #exists()}.
     *
     * @return {@code select 1 from ...}
     */
    RenderedQuery existsHql() {
        return renderer().exists(entityClass, wheres);
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
     * @throws org.lambdaql.LambdaAnalysisException if the lambda is not a chain of {@code by} calls,
     *                                                       or a key cannot become SQL
     */
    public OrderedSelectQuery<T> orderBy(SortBy<T> spec) {
        LambdaRef ref = QueryContext.read(spec);
        List<SortItem> items = context.analyzeSortBy(ref, List.of(entityClass));
        return new OrderedSelectQuery<>(entityClass, context, wheres, List.of(new HqlRenderer.SortBound(items, ref.capturedArgs()))).withJoins(joins).withDistinct(distinct);
    }

    /**
     * Sorts with a reusable {@link SortSpec}.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java">SortQueryTest</a>):
     * {@snippet class="org.lambdaql.query.SortQueryTest" region="sort-spec"}
     *
     * @param spec the spec
     * @return the sorted query
     * @throws IllegalArgumentException                      if the spec is for another entity class,
     *                                                       or a path part ({@link SortSpec#ofPath}) is not a property
     * @throws org.lambdaql.LambdaAnalysisException if a lambda of the spec cannot become SQL
     */
    public OrderedSelectQuery<T> orderBy(SortSpec<T> spec) {
        Objects.requireNonNull(spec, "spec");
        if (!spec.type().isAssignableFrom(entityClass)) {
            throw new IllegalArgumentException(LqlMessage.SORT_SPEC_TYPE.format(spec.type().getName(), entityClass.getName()));
        }
        return new OrderedSelectQuery<>(entityClass, context, wheres, spec.bind(context, entityClass)).withJoins(joins).withDistinct(distinct);
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
     * @throws org.lambdaql.LambdaAnalysisException if the key cannot become SQL
     */
    public OrderedSelectQuery<T> orderBy(SortBy<T> key, Direction direction) {
        Objects.requireNonNull(direction, "direction");
        LambdaRef ref = QueryContext.read(key);
        Expr k = context.analyzeSortKey(ref, entityClass);
        SortItem item = new SortItem(k, SortItem.Kind.SORT, new Expr.Const(direction, Type.getType(Direction.class)));
        return new OrderedSelectQuery<>(entityClass, context, wheres, List.of(new HqlRenderer.SortBound(List.of(item), ref.capturedArgs()))).withJoins(joins).withDistinct(distinct);
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
        return new OrderedSelectQuery<>(entityClass, context, wheres, List.of()).limit(count).withJoins(joins).withDistinct(distinct);
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
        return new OrderedSelectQuery<>(entityClass, context, wheres, List.of()).offset(count).withJoins(joins).withDistinct(distinct);
    }

    /**
     * Selects values instead of entities: one value {@code o -> o.getPrice()}, or a record/DTO
     * {@code o -> new OrderView(o.getId(), o.getCustomer().getName())} (ROADMAP M9). Aggregates over all rows
     * ({@code o -> new Total(Agg.count(), Agg.avg(o.getPrice()))}) are allowed.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java">ProjectionTest</a>):
     * {@snippet class="org.lambdaql.query.ProjectionTest" region="record"}
     *
     * @param clause the select lambda
     * @param <R>    the value type
     * @return the value query
     * @throws org.lambdaql.LambdaAnalysisException if the lambda is not a value or one constructor call, or mixes
     *                                                       aggregates with other values (J3)
     */
    public <R> ValueQuery<R> select(Select<T, R> clause) {
        LambdaRef ref = QueryContext.read(clause);
        var projection = context.analyzeSelect(ref, List.of(entityClass));
        org.lambdaql.engine.sem.SelectReader.checkGrouping(projection.columns(), List.of(), List.of(), ref.describe());
        return new ValueQuery<R>(entityClass, context, wheres, projection, ref.capturedArgs(), ref.classLoader(), null, List.of(),
                List.of(), null, 0).withJoins(joins);
    }

    /**
     * Groups the rows: one key {@code o -> o.getCustomer().getId()} or many keys
     * {@code o -> List.of(o.getCustomer().getId(), o.getStatus())} (ROADMAP D27).
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java">ProjectionTest</a>):
     * {@snippet class="org.lambdaql.query.ProjectionTest" region="group-by"}
     *
     * @param keys the key lambda
     * @return the grouped query: {@code having}, then {@code select}
     */
    public GroupedQuery<T> groupBy(GroupBy<T> keys) {
        LambdaRef ref = QueryContext.read(keys);
        return new GroupedQuery<>(entityClass, context, wheres,
                new HqlRenderer.ValueBound(context.analyzeGroupKeys(ref, List.of(entityClass)), ref.capturedArgs()));
    }

    /**
     * Adds an inner join with another entity: {@code join(Customer.class, (o, c) -> o.getCustomer().getId() == c.getId())}
     * (ROADMAP D44). Hibernate writes it as an entity join {@code join Customer e1 on ...}.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java">JoinQueryTest</a>):
     * {@snippet class="org.lambdaql.query.JoinQueryTest" region="join"}
     *
     * @param entity    the joined entity class
     * @param condition the join condition
     * @param <U>       the joined entity type
     * @return the join query
     */
    public <U> SelectJoinQuery<T, U> join(Class<U> entity, SelectJoinQuery.JoinOn<T, U> condition) {
        return new SelectJoinQuery<>(this, List.of(), List.of(), entity, false, condition);
    }

    /**
     * Adds a left join with another entity: rows of the main entity without a match stay (ROADMAP D44).
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java">JoinQueryTest</a>):
     * {@snippet class="org.lambdaql.query.JoinQueryTest" region="left-join"}
     *
     * @param entity    the joined entity class
     * @param condition the join condition
     * @param <U>       the joined entity type
     * @return the join query
     */
    public <U> SelectJoinQuery<T, U> leftJoin(Class<U> entity, SelectJoinQuery.JoinOn<T, U> condition) {
        return new SelectJoinQuery<>(this, List.of(), List.of(), entity, true, condition);
    }

    /**
     * Adds a cross join with another entity: every pair of rows, for example all customers with all products
     * (ROADMAP D45). The name says that the product of rows is meant; put the conditions in {@code where}.
     * To join on a condition, use {@link #join(Class, SelectJoinQuery.JoinOn)}.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java">JoinQueryTest</a>):
     * {@snippet class="org.lambdaql.query.JoinQueryTest" region="cross-join"}
     *
     * @param entity the joined entity class
     * @param <U>    the joined entity type
     * @return the join query
     */
    public <U> SelectJoinQuery<T, U> crossJoin(Class<U> entity) {
        return new SelectJoinQuery<>(this, List.of(), List.of(), entity, false, null);
    }

    /**
     * Joins the rows of a CTE: {@code with t as (...) select e0 from X e0 join t e1 on ...} (ROADMAP D44 extension).
     * The row type of the CTE is the second lambda parameter; its accessors read the CTE columns.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java">JoinQueryTest</a>):
     * {@snippet class="org.lambdaql.query.JoinQueryTest" region="join-cte"}
     *
     * @param cte       the CTE from {@code qb.with(...)}
     * @param condition the join condition
     * @param <U>       the row type
     * @return the join query
     */
    public <U> SelectJoinQuery<T, U> join(Cte<U> cte, SelectJoinQuery.JoinOn<T, U> condition) {
        return new SelectJoinQuery<>(this, List.of(), List.of(), cte.source.rowType, false, condition, cte.source.joinSource());
    }

    /**
     * Left-joins the rows of a CTE: rows of the main entity without a match stay (ROADMAP D44 extension).
     *
     * @param cte       the CTE from {@code qb.with(...)}
     * @param condition the join condition
     * @param <U>       the row type
     * @return the join query
     */
    public <U> SelectJoinQuery<T, U> leftJoin(Cte<U> cte, SelectJoinQuery.JoinOn<T, U> condition) {
        return new SelectJoinQuery<>(this, List.of(), List.of(), cte.source.rowType, true, condition, cte.source.joinSource());
    }

    /**
     * Joins the rows of a query as a derived table: {@code join (select ...) e1 on ...} (ROADMAP D44 extension).
     * The query must select a record or a Kotlin data class (J5); its accessors read the columns.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java">JoinQueryTest</a>):
     * {@snippet class="org.lambdaql.query.JoinQueryTest" region="join-derived"}
     *
     * @param rows      the inner query
     * @param condition the join condition
     * @param <U>       the row type
     * @return the join query
     */
    public <U> SelectJoinQuery<T, U> join(ValueQuery<U> rows, SelectJoinQuery.JoinOn<T, U> condition) {
        DerivedSource d = new DerivedSource(rows, null);
        return new SelectJoinQuery<>(this, List.of(), List.of(), d.rowType, false, condition, d.joinSource());
    }

    /**
     * Left-joins the rows of a query as a derived table (ROADMAP D44 extension).
     *
     * @param rows      the inner query
     * @param condition the join condition
     * @param <U>       the row type
     * @return the join query
     */
    public <U> SelectJoinQuery<T, U> leftJoin(ValueQuery<U> rows, SelectJoinQuery.JoinOn<T, U> condition) {
        DerivedSource d = new DerivedSource(rows, null);
        return new SelectJoinQuery<>(this, List.of(), List.of(), d.rowType, true, condition, d.joinSource());
    }

    /**
     * Runs the query and returns the rows as a set that keeps the row order ({@link LinkedHashSet}).
     * The set has the same rows as {@link #toList()}: Hibernate returns each entity once, also after an explicit join,
     * and a repeated object would keep its first place (D68).
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ResultQueryTest.java">ResultQueryTest</a>):
     * {@snippet class="org.lambdaql.query.ResultQueryTest" region="to-set"}
     *
     * @return the rows in order
     * @throws IllegalStateException if the entity's {@code equals}/{@code hashCode} treat two different rows as equal:
     *                               one of them would be lost without a sign
     */
    public SequencedSet<T> toSet() {
        return DmlParts.toSet(toList());
    }

    // ------------------------------------------------------------------ explicit joins (ROADMAP D44)

    private List<HqlRenderer.JoinBound> joins = List.of();

    /** Sets the entity joins of a join query; the joined entities are roots 1, 2 ... Not public API. */
    SelectQuery<T> withJoins(List<HqlRenderer.JoinBound> joins) {
        this.joins = List.copyOf(joins);
        return this;
    }

    private boolean distinct;

    /** Makes the rows distinct ({@code select distinct e0}) for a join query. Not public API. */
    SelectQuery<T> withDistinct(boolean distinct) {
        this.distinct = distinct;
        return this;
    }

    /** Whether the rows are distinct. Not public API. */
    boolean isDistinct() {
        return distinct;
    }

    private HqlRenderer renderer() {
        return new HqlRenderer(context.model(), entityClass.getClassLoader()).withJoins(joins).withDistinct(distinct);
    }

    /** Adds a condition that was analyzed with all join roots. Not public API. */
    void addWhere(HqlRenderer.Bound where) {
        wheres.add(where);
    }

    List<HqlRenderer.JoinBound> joins() {
        return joins;
    }

    Class<T> entityClass() {
        return entityClass;
    }

    QueryContext context() {
        return context;
    }

    List<HqlRenderer.Bound> wheres() {
        return wheres;
    }
}
