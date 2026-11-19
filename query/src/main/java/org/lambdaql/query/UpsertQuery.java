package org.lambdaql.query;

import org.lambdaql.engine.ir.Expr;
import org.lambdaql.engine.render.HqlRenderer;
import org.lambdaql.engine.render.RenderedQuery;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The start of an upsert (ROADMAP D40): insert the row, or, if a row with the same {@code @Id} exists,
 * change its other assigned columns to the new values.
 * Hibernate writes {@code merge} on H2 and Oracle and {@code on duplicate key update} on MariaDB.
 * For another key or another change, use {@code qb.insert(X).values(...).onConflict(key).update(...)}.
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java">DmlQueryTest</a>):
 * {@snippet class="org.lambdaql.query.DmlQueryTest" region="upsert"}
 *
 * @param <T> the entity type
 */
public class UpsertQuery<T> {

    private final Class<T> entityClass;
    private final QueryContext context;

    UpsertQuery(Class<T> entityClass, QueryContext context) {
        this.entityClass = entityClass;
        this.context = context;
    }

    /**
     * Upserts one row written with setters. The row must assign the {@code @Id}.
     *
     * @param assign the assignment lambda
     * @return the runnable upsert
     */
    public ExecutableInsert<T> values(Assign<T> assign) {
        return plan(List.of(DmlParts.setters(context, entityClass, assign)));
    }

    /**
     * Upserts one row whose values may use subqueries: {@code (a, sub) -> ...} (ROADMAP D49).
     *
     * @param assign the assignment lambda
     * @return the runnable upsert
     */
    public ExecutableInsert<T> values(AssignSub<T> assign) {
        return plan(List.of(DmlParts.withSub(context, entityClass, assign)));
    }

    /**
     * Upserts one row per list element (one statement per row).
     *
     * @param rows   the elements
     * @param assign the assignment lambda
     * @param <L>    the element type
     * @return the runnable upsert
     */
    public <L> ExecutableInsert<T> values(Collection<L> rows, AssignEach<T, L> assign) {
        return plan(DmlParts.each(context, entityClass, rows, assign));
    }

    /**
     * Upserts one row per list element, with values that may use subqueries: {@code (a, l, sub) -> ...} (ROADMAP D49).
     *
     * @param rows   the elements
     * @param assign the assignment lambda
     * @param <L>    the element type
     * @return the runnable upsert
     */
    public <L> ExecutableInsert<T> values(Collection<L> rows, AssignEachSub<T, L> assign) {
        return plan(DmlParts.each(context, entityClass, rows, assign));
    }

    private ExecutableInsert<T> plan(List<HqlRenderer.AssignBound> rows) {
        if (!rows.isEmpty()) {
            DmlParts.noTargetRead(rows.get(0));
            DmlParts.checkRules(List.of(rows.get(0)), entityClass);
        }
        Expr.Column id = DmlParts.idColumn(context, entityClass);
        return new ExecutableInsert<>(context, () -> {
            List<RenderedQuery> out = new ArrayList<>();
            for (HqlRenderer.AssignBound row : rows) {
                out.add(DmlParts.renderer(context, entityClass).upsert(entityClass, row, id, null));
            }
            return out;
        });
    }
}
