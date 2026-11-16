package org.lambdaql.query;

import org.lambdaql.analyzer.LambdaAnalysisException;
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

    static HqlRenderer.AssignBound builder(QueryContext context, Class<?> type, AssignBy<?> assign) {
        LambdaRef ref = QueryContext.read(assign);
        return new HqlRenderer.AssignBound(context.analyzeBuilder(ref, List.of(type)), ref.capturedArgs());
    }

    static HqlRenderer.Bound where(QueryContext context, Class<?> type, SelectQuery.Where<?> where) {
        LambdaRef ref = QueryContext.read(where);
        return new HqlRenderer.Bound(context.analyzeWhere(ref, List.of(type)), ref.capturedArgs());
    }

    /** One row per element: the element is added after the captured values of the lambda. */
    static <L> List<HqlRenderer.AssignBound> each(QueryContext context, Class<?> type, Collection<L> rows, AssignEach<?, L> assign) {
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
                    throw new LambdaAnalysisException("insert 값에서 대상 엔티티를 읽을 수 없습니다: " + String.join(".", c.path()));
                }
            }
        }
    }

    /** The @Id column of an entity, the default upsert key. */
    static Expr.Column idColumn(QueryContext context, Class<?> type) {
        String id = context.model().idName(type)
                .orElseThrow(() -> new IllegalArgumentException("복합 키 엔티티의 upsert는 onConflict(키)를 쓰세요: " + type.getName()));
        return new Expr.Column(0, List.of(id), List.of(false), Object.class, true, false);
    }

    static HqlRenderer renderer(QueryContext context, Class<?> type) {
        return new HqlRenderer(context.model(), type.getClassLoader());
    }

    /**
     * Makes an ordered set of entity rows and checks that no row was lost. Entity rows never repeat
     * (only to-one joins), so a smaller set means {@code equals}/{@code hashCode} or the {@code @Id} is wrong.
     */
    static <T> java.util.SequencedSet<T> toSet(List<T> rows) {
        java.util.LinkedHashSet<T> set = new java.util.LinkedHashSet<>(rows);
        if (set.size() < rows.size()) {
            throw new IllegalStateException("toSet/findSet: 행 " + rows.size() + "개가 Set에서 " + set.size()
                    + "개로 줄었습니다. 엔티티의 equals/hashCode가 서로 다른 행을 같게 보거나 @Id가 유일하지 않습니다. toList()를 쓰세요");
        }
        return set;
    }
}
