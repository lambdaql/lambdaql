package org.lambdaql.query;

import org.lambdaql.engine.render.HqlRenderer;
import org.lambdaql.engine.render.RenderedQuery;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The start of an INSERT (ROADMAP D28, M10):
 * {@code qb.insert(Order.class).values(o -> { o.setStatus(1); }).execute()}, many rows from a list,
 * or INSERT ... SELECT with {@code from(Source.class)}.
 * If the id is generated (IDENTITY, SEQUENCE), you can leave it out.
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java">DmlQueryTest</a>):
 * {@snippet class="org.lambdaql.query.DmlQueryTest" region="insert"}
 *
 * @param <T> the entity type
 */
public class InsertQuery<T> {

    private final Class<T> entityClass;
    private final QueryContext context;

    InsertQuery(Class<T> entityClass, QueryContext context) {
        this.entityClass = entityClass;
        this.context = context;
    }

    /**
     * Inserts one row written with setters.
     *
     * @param assign the assignment lambda; values cannot read the new row
     * @return the runnable INSERT (and {@code onConflict})
     */
    public InsertValues<T> values(Assign<T> assign) {
        return InsertValues.of(entityClass, context, List.of(DmlParts.setters(context, entityClass, assign)));
    }

    /**
     * Inserts one row written with the builder.
     *
     * @param assign the assignment lambda
     * @return the runnable INSERT
     */
    public InsertValues<T> values(AssignBy<T> assign) {
        return InsertValues.of(entityClass, context, List.of(DmlParts.builder(context, entityClass, assign)));
    }

    /**
     * Inserts one row per list element.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java">DmlQueryTest</a>):
     * {@snippet class="org.lambdaql.query.DmlQueryTest" region="insert-list"}
     *
     * @param rows   the elements; an empty list inserts nothing
     * @param assign the assignment lambda; the element's getters are read in Java
     * @param <L>    the element type
     * @return the runnable INSERT
     */
    public <L> InsertValues<T> values(Collection<L> rows, AssignEach<T, L> assign) {
        return InsertValues.of(entityClass, context, DmlParts.each(context, entityClass, rows, assign));
    }

    /**
     * Starts INSERT ... SELECT from another entity.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java">DmlQueryTest</a>):
     * {@snippet class="org.lambdaql.query.DmlQueryTest" region="insert-select"}
     *
     * @param source the source entity class
     * @param <S>    the source type
     * @return the next step: {@code where}, then {@code select}
     */
    public <S> InsertFrom<T, S> from(Class<S> source) {
        return new InsertFrom<>(entityClass, source, context);
    }

    /**
     * INSERT ... SELECT: the source part.
     *
     * @param <T> the target type
     * @param <S> the source type
     */
    public static final class InsertFrom<T, S> {
        private final Class<T> target;
        private final Class<S> source;
        private final QueryContext context;
        private final List<HqlRenderer.Bound> wheres = new ArrayList<>();

        InsertFrom(Class<T> target, Class<S> source, QueryContext context) {
            this.target = target;
            this.source = source;
            this.context = context;
        }

        /**
         * Limits the source rows (same rules as a select condition).
         *
         * @param condition the condition on the source
         * @return this
         */
        public InsertFrom<T, S> where(SelectQuery.Where<S> condition) {
            wheres.add(DmlParts.where(context, source, condition));
            return this;
        }

        /**
         * Maps a source row to a new row: {@code (a, o) -> { a.setId(o.getId()); }}.
         * Source paths through an association use a left join, so source rows without it stay (U10).
         *
         * @param assign the mapping
         * @return the runnable INSERT
         */
        public ExecutableInsert<T> select(AssignFrom<T, S> assign) {
            var ref = QueryContext.read(assign);
            var items = context.analyzeSetters(ref, List.of(target, source), -1).stream()
                    .map(a -> new org.lambdaql.engine.ir.Assignment(a.target(),
                            org.lambdaql.engine.sem.AssignmentReader.reroot(a.value(), 1, 0)))
                    .toList();
            for (var a : items) {
                if (org.lambdaql.engine.sem.AssignmentReader.columns(a.value()).stream().anyMatch(c -> c.root() != 0)) {
                    throw new org.lambdaql.analyzer.LambdaAnalysisException("insert … select 값에서는 새 행을 읽을 수 없습니다 — " + ref.describe());
                }
            }
            HqlRenderer.AssignBound bound = new HqlRenderer.AssignBound(items, ref.capturedArgs());
            List<HqlRenderer.Bound> w = List.copyOf(wheres);
            return new ExecutableInsert<>(context,
                    () -> List.<RenderedQuery>of(DmlParts.renderer(context, target).insertSelect(target, source, bound, w)));
        }
    }
}
