package org.lambdaql.query;

import org.lambdaql.i18n.LqlMessage;
import org.lambdaql.engine.ir.Expr;
import org.lambdaql.engine.render.HqlRenderer;
import org.lambdaql.engine.render.RenderedQuery;

import java.util.ArrayList;
import java.util.List;

/**
 * An INSERT with values that can run, or get an {@link #onConflict(ConflictKey)} part (upsert, ROADMAP D40).
 *
 * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java">DmlQueryTest</a>):
 * {@snippet class="org.lambdaql.query.DmlQueryTest" region="on-conflict"}
 *
 * @param <T> the entity type
 */
public class InsertValues<T> extends ExecutableInsert<T> {

    private final Class<T> entityClass;
    private final QueryContext context;
    private final List<HqlRenderer.AssignBound> rows;

    private InsertValues(Class<T> entityClass, QueryContext context, List<HqlRenderer.AssignBound> rows) {
        super(context, () -> plain(entityClass, context, rows));
        this.entityClass = entityClass;
        this.context = context;
        this.rows = rows;
    }

    static <T> InsertValues<T> of(Class<T> entityClass, QueryContext context, List<HqlRenderer.AssignBound> rows) {
        for (HqlRenderer.AssignBound row : rows.isEmpty() ? List.<HqlRenderer.AssignBound>of() : List.of(rows.get(0))) {
            DmlParts.noTargetRead(row);
            DmlParts.checkRules(List.of(row), entityClass);
        }
        return new InsertValues<>(entityClass, context, List.copyOf(rows));
    }

    /** One statement per chunk of rows, so a statement has at most about 500 values (U13). */
    private static List<RenderedQuery> plain(Class<?> type, QueryContext context, List<HqlRenderer.AssignBound> rows) {
        if (rows.isEmpty()) return List.of();
        int columns = rows.get(0).items().size();
        int perStatement = Math.max(1, DmlParts.MAX_VALUES_PER_STATEMENT / columns);
        List<RenderedQuery> out = new ArrayList<>();
        for (int i = 0; i < rows.size(); i += perStatement) {
            List<HqlRenderer.AssignBound> chunk = rows.subList(i, Math.min(rows.size(), i + perStatement));
            out.add(DmlParts.renderer(context, type).insertValues(type, chunk));
        }
        return out;
    }

    /**
     * Says what to do when a row with the same key already exists.
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java">DmlQueryTest</a>):
     * {@snippet class="org.lambdaql.query.DmlQueryTest" region="on-conflict"}
     *
     * @param key the key column: {@code o -> o.getId()} or {@code Order::getCode}; it needs a primary key or
     *            unique constraint in the database
     * @return the next step: {@code update(...)} or {@code ignore()}
     */
    public OnConflict<T> onConflict(ConflictKey<T> key) {
        Expr k = context.analyzeSortKey(QueryContext.read(key), entityClass);
        if (!(k instanceof Expr.Column c) || c.root() != 0 || c.association().contains(true)) {
            throw LqlMessage.CONFLICT_KEY_NOT_COLUMN.exception(k);
        }
        return new OnConflict<>(entityClass, context, rows, c);
    }

    /**
     * The conflict part of an upsert.
     *
     * @param <T> the entity type
     */
    public static final class OnConflict<T> {
        private final Class<T> entityClass;
        private final QueryContext context;
        private final List<HqlRenderer.AssignBound> rows;
        private final Expr.Column key;

        OnConflict(Class<T> entityClass, QueryContext context, List<HqlRenderer.AssignBound> rows, Expr.Column key) {
            this.entityClass = entityClass;
            this.context = context;
            this.rows = rows;
            this.key = key;
        }

        /**
         * On conflict, changes the existing row: {@code o -> o.setCnt(o.getCnt() + 1)}.
         * A getter here reads the existing row. Runs one statement per row.
         *
         * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java">DmlQueryTest</a>):
         * {@snippet class="org.lambdaql.query.DmlQueryTest" region="on-conflict"}
         *
         * @param assign the assignments for the existing row
         * @return the runnable upsert
         */
        public ExecutableInsert<T> update(Assign<T> assign) {
            return updateWith(DmlParts.setters(context, entityClass, assign));
        }

        /**
         * On conflict, changes the existing row with values that may use subqueries: {@code (o, sub) -> ...}
         * (ROADMAP D49).
         *
         * @param assign the assignments for the existing row
         * @return the runnable upsert
         */
        public ExecutableInsert<T> update(AssignSub<T> assign) {
            return updateWith(DmlParts.withSub(context, entityClass, assign));
        }

        private ExecutableInsert<T> updateWith(HqlRenderer.AssignBound updates) {
            DmlParts.checkRules(List.of(updates), entityClass);
            return new ExecutableInsert<>(context, () -> {
                List<RenderedQuery> out = new ArrayList<>();
                for (HqlRenderer.AssignBound row : rows) {
                    out.add(DmlParts.renderer(context, entityClass).upsert(entityClass, row, key, updates));
                }
                return out;
            });
        }

        /**
         * On conflict, keeps the existing row and inserts nothing. lambdaQL writes
         * {@code insert ... select ... where not exists (...)} (Hibernate's own form fails on H2 and Oracle, D40).
         * Not atomic: two sessions inserting the same key at the same moment can still get a duplicate key error.
         *
         * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java">DmlQueryTest</a>):
         * {@snippet class="org.lambdaql.query.DmlQueryTest" region="insert-ignore"}
         *
         * @return the runnable INSERT
         */
        public ExecutableInsert<T> ignore() {
            return new ExecutableInsert<>(context, () -> {
                List<RenderedQuery> out = new ArrayList<>();
                for (HqlRenderer.AssignBound row : rows) {
                    out.add(DmlParts.renderer(context, entityClass).insertIgnore(entityClass, row, key));
                }
                return out;
            });
        }
    }
}
