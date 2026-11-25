package org.lambdaql.query;

import org.lambdaql.engine.render.HqlRenderer;

import java.util.List;

/**
 * A sorted query with 5 joined entities (ROADMAP D44, D71): limit, offset, results, and a select on all entities.
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/JoinQueryTest.java">JoinQueryTest</a>):
 * {@snippet class="org.lambdaql.query.JoinQueryTest" region="sort"}
 *
 * @param <T> the main entity type
 * @param <U> the joined entity type
 * @param <V> the joined entity type
 * @param <W> the joined entity type
 * @param <X> the joined entity type
 */
public class OrderedJoin5Query<T, U, V, W, X> extends OrderedSelectQuery<T> {

    private final Class<T> root;
    private final QueryContext ctx;
    private final List<HqlRenderer.Bound> conditions;
    private final List<HqlRenderer.SortBound> order;
    private final List<HqlRenderer.JoinBound> joinBounds;
    private final List<Class<?>> roots;

    OrderedJoin5Query(Class<T> root, QueryContext context, List<HqlRenderer.Bound> wheres, List<HqlRenderer.SortBound> sorts,
            List<HqlRenderer.JoinBound> joins, List<Class<?>> roots) {
        super(root, context, wheres, sorts);
        withJoins(joins);
        this.root = root;
        this.ctx = context;
        this.conditions = List.copyOf(wheres);
        this.order = List.copyOf(sorts);
        this.joinBounds = List.copyOf(joins);
        this.roots = List.copyOf(roots);
    }

    /**
     * Selects values of all entities in this order: {@code select((o, c, i, e3, e4) -> ...)}.
     *
     * @param clause the select lambda
     * @param <R>    the value type
     * @return the value query
     */
    public <R> ValueQuery<R> select(SelectJoin5Query.Select<T, U, V, W, X, R> clause) {
        ValueQuery<R> q = SelectJoinQuery.selectValues(root, ctx, conditions, order, joinBounds, roots, clause);
        return isDistinct() ? q.distinct() : q;
    }
}
