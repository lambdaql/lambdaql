package org.lambdaql.query;

import org.lambdaql.i18n.LqlMessage;
import org.lambdaql.engine.ir.Assignment;
import org.lambdaql.engine.ir.Expr;
import org.lambdaql.engine.load.LambdaRef;
import org.lambdaql.engine.render.HqlRenderer;
import org.lambdaql.engine.sem.AssignmentReader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/** Shared helpers of the UPDATE/DELETE/INSERT query classes (not public API). */
final class DmlParts {

    /** Rows per INSERT statement are limited so that one statement has at most this many values (ROADMAP U13). */
    static final int MAX_VALUES_PER_STATEMENT = 500;

    private DmlParts() {
    }

    static HqlRenderer.AssignBound setters(QueryContext context, Class<?> type, Assign<?> assign) {
        LambdaRef ref = QueryContext.read(assign);
        return new HqlRenderer.AssignBound(context.analyzeSetters(ref, List.of(type), -1), ref.capturedArgs());
    }

    /** Reads an assignment lambda {@code (o, sub) -> ...}: parameter 1 starts subqueries (D49). */
    static HqlRenderer.AssignBound withSub(QueryContext context, Class<?> type, AssignSub<?> assign) {
        LambdaRef ref = QueryContext.read(assign);
        return new HqlRenderer.AssignBound(context.analyzeSetters(ref, List.of(type), -1), ref.capturedArgs());
    }

    static HqlRenderer.Bound where(QueryContext context, Class<?> type, Object where) {
        LambdaRef ref = QueryContext.read(where);
        return new HqlRenderer.Bound(context.analyzeWhere(ref, List.of(type)), ref.capturedArgs());
    }

    /** One row per element: the element is added after the captured values of the lambda. */
    static <L> List<HqlRenderer.AssignBound> each(QueryContext context, Class<?> type, Collection<L> rows, Object assign) {
        LambdaRef ref = QueryContext.read(assign);
        List<Assignment> items = context.analyzeSetters(ref, List.of(type), 1);
        Object[] base = ref.capturedArgs();
        List<HqlRenderer.AssignBound> out = new ArrayList<>();
        for (L row : rows) {
            Object[] captured = Arrays.copyOf(base, base.length + 1);
            captured[base.length] = row;
            out.add(new HqlRenderer.AssignBound(items, captured));
        }
        return out;
    }

    /** Checks U3/U4 over all assignments of one statement. */
    static void checkRules(List<HqlRenderer.AssignBound> parts, Class<?> type) {
        List<Assignment> all = new ArrayList<>();
        for (HqlRenderer.AssignBound b : parts) all.addAll(b.items());
        AssignmentReader.checkRules(all, type.getSimpleName());
    }

    /** INSERT values cannot read the new row (there is no "value before"). */
    static void noTargetRead(HqlRenderer.AssignBound row) {
        for (Assignment a : row.items()) {
            for (Expr.Column c : AssignmentReader.columns(a.value())) {
                if (c.root() == 0) {
                    throw LqlMessage.INSERT_READS_TARGET.exception(String.join(".", c.path()));
                }
            }
        }
    }

    /** The @Id column of an entity, the default upsert key. */
    static Expr.Column idColumn(QueryContext context, Class<?> type) {
        String id = context.model().idName(type)
                .orElseThrow(() -> new IllegalArgumentException(LqlMessage.UPSERT_COMPOSITE_KEY.format(type.getName())));
        return new Expr.Column(0, List.of(id), List.of(false), Object.class, true, false);
    }

    static HqlRenderer renderer(QueryContext context, Class<?> type) {
        return new HqlRenderer(context.model(), type.getClassLoader());
    }

    /**
     * Makes an ordered set of entity rows and checks that no row was lost (ROADMAP D42, D68).
     * <p>
     * In one query result Hibernate gives the same object for the same row, so a row that a join repeats is the same
     * object: it keeps its first place and is not counted as lost. Two different objects that {@code equals} treats as
     * equal would make one row disappear without a sign, so that throws.
     *
     * <p>Example: orders joined to their items repeat once per item; the set has each order once. Two {@code Tag}
     * rows whose {@code equals} compares only the name throw.
     *
     * @param rows the rows of one query, in order
     * @param <T>  the entity type
     * @return the rows as an ordered set
     * @throws IllegalStateException if two different rows are equal by {@code equals}/{@code hashCode}
     */
    static <T> java.util.SequencedSet<T> toSet(List<T> rows) {
        java.util.Set<T> objects = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        java.util.LinkedHashSet<T> set = new java.util.LinkedHashSet<>();
        for (T row : rows) {
            if (objects.add(row)) set.add(row);   // a repeated row is the same object: only its first place counts
        }
        if (set.size() < objects.size()) {
            throw new IllegalStateException(LqlMessage.SET_SIZE_MISMATCH.format(objects.size(), set.size()));
        }
        return set;
    }
}
