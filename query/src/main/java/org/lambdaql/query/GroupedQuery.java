package org.lambdaql.query;

import org.lambdaql.engine.render.HqlRenderer;

import java.util.ArrayList;
import java.util.List;

/**
 * A query with group keys: {@code qb.from(Order.class).groupBy(o -> o.getCustomer().getId()).having(...).select(...)}
 * (ROADMAP M9, D27). Every select value without an aggregate must be a group key (rule J3).
 *
 * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java">ProjectionTest</a>):
 * {@snippet class="org.lambdaql.query.ProjectionTest" region="group-by"}
 *
 * @param <T> the entity type
 */
public class GroupedQuery<T> {

    private final Class<T> entityClass;
    private final QueryContext context;
    private final List<HqlRenderer.Bound> wheres;
    private final HqlRenderer.ValueBound keys;
    private final List<HqlRenderer.Bound> havings = new ArrayList<>();

    GroupedQuery(Class<T> entityClass, QueryContext context, List<HqlRenderer.Bound> wheres, HqlRenderer.ValueBound keys) {
        this.entityClass = entityClass;
        this.context = context;
        this.wheres = List.copyOf(wheres);
        this.keys = keys;
    }

    /**
     * Adds a condition on groups (joined with AND). Aggregates ({@code Agg.sum(...)}) are allowed here.
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java">ProjectionTest</a>):
     * {@snippet class="org.lambdaql.query.ProjectionTest" region="group-by"}
     *
     * @param condition the condition
     * @return this query
     */
    public GroupedQuery<T> having(SelectQuery.Where<T> condition) {
        var ref = QueryContext.read(condition);
        havings.add(new HqlRenderer.Bound(context.analyzeHaving(ref, List.of(entityClass)), ref.capturedArgs()));
        return this;
    }

    /**
     * Selects the values of each group.
     *
     * @param clause the select lambda, for example {@code o -> new Stat(o.getCustomer().getId(), Agg.count())}
     * @param <R>    the value type
     * @return the value query
     * @throws org.lambdaql.LambdaAnalysisException if a value without an aggregate is not a group key (J3)
     */
    public <R> ValueQuery<R> select(SelectQuery.Select<T, R> clause) {
        var ref = QueryContext.read(clause);
        var projection = context.analyzeSelect(ref, List.of(entityClass));
        org.lambdaql.engine.sem.SelectReader.checkGrouping(projection.columns(), keys.exprs(),
                havings.stream().map(HqlRenderer.Bound::pred).toList(), ref.describe());
        return new ValueQuery<>(entityClass, context, wheres, projection, ref.capturedArgs(), ref.classLoader(), keys, havings,
                List.of(), null, 0);
    }
}
