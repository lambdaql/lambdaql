package org.lambdaql.engine.sem;

import org.lambdaql.i18n.LqlMessage;
import org.lambdaql.LambdaAnalysisException;
import org.lambdaql.engine.decode.Decoder;
import org.lambdaql.engine.decode.Node;
import org.lambdaql.engine.ir.Expr;
import org.lambdaql.engine.ir.Pred;
import org.lambdaql.engine.load.BytecodeLoader;
import org.lambdaql.engine.load.LambdaRef;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads {@code select} and {@code groupBy} lambdas (ROADMAP M9, D27).
 * <ul>
 *   <li>{@code o -> o.getPrice()}: one value.</li>
 *   <li>{@code o -> new OrderView(o.getId(), o.getCustomer().getName())}: a record or DTO. lambdaQL selects only the
 *       columns and calls the constructor in Java (rule J2).</li>
 *   <li>Kotlin {@code a to b} ({@code Pair}) and {@code Triple(a, b, c)}.</li>
 *   <li>Group keys: one value, Java {@code List.of(a, b)}, Kotlin {@code listOf(a, b)}, {@code a to b}.</li>
 * </ul>
 * Aggregates ({@code org.lambdaql.Agg}) are allowed in select; rule J3 (every value without an aggregate must be a group
 * key) is checked by {@link #checkGrouping}.
 *
 * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java">ProjectionTest</a>):
 * {@snippet class="org.lambdaql.query.ProjectionTest" region="record"}
 */
public final class SelectReader {

    private SelectReader() {
    }

    /**
     * What a select lambda gives.
     *
     * @param columns     the normalized values, in order
     * @param className   internal class name for a constructor result, or null for one value
     * @param constructor the constructor descriptor, or null
     */
    public record Projection(List<Expr> columns, String className, String constructor) {
        /** Makes an unmodifiable copy of the columns. */
        public Projection {
            columns = List.copyOf(columns);
        }

        /**
         * Checks if the result is built with a constructor.
         *
         * @return {@code true} for a record, DTO, Pair or Triple
         */
        public boolean constructs() {
            return className != null;
        }
    }

    /**
     * Reads a select lambda.
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java">ProjectionTest</a>):
     * {@snippet class="org.lambdaql.query.ProjectionTest" region="record"}
     *
     * @param ref   the lambda
     * @param roots entity class of each entity parameter
     * @param model entity information
     * @return the projection
     * @throws LambdaAnalysisException if the body is not a value or one constructor call
     */
    public static Projection readSelect(LambdaRef ref, List<Class<?>> roots, EntityModel model) {
        Expr body = unwrap(body(ref));
        if (body instanceof Expr.NewObject n) {
            return new Projection(columns(n.args(), ref, roots, model, true), n.type().getInternalName(), n.constructor().descriptor());
        }
        if (body instanceof Expr.Invoke inv && inv.method().isStatic() && inv.method().is("kotlin/TuplesKt", "to")) {
            return new Projection(columns(inv.args(), ref, roots, model, true), "kotlin/Pair", "(Ljava/lang/Object;Ljava/lang/Object;)V");
        }
        return new Projection(List.of(Normalizer.normalizeSelected(body, roots, model, ref.classLoader(), ref.describe())), null, null);
    }

    /**
     * Reads a groupBy lambda.
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java">ProjectionTest</a>):
     * {@snippet class="org.lambdaql.query.ProjectionTest" region="group-many"}
     *
     * @param ref   the lambda
     * @param roots entity class of each entity parameter
     * @param model entity information
     * @return the keys in order
     * @throws LambdaAnalysisException if a key does not use the entity, or the form is not supported
     */
    public static List<Expr> readGroupKeys(LambdaRef ref, List<Class<?>> roots, EntityModel model) {
        Expr body = unwrap(body(ref));
        List<Expr> raw;
        if (body instanceof Expr.Invoke inv && inv.method().isStatic() && inv.method().is("java/util/List", "of")) {
            raw = inv.args().size() == 1 && unwrap(inv.args().get(0)) instanceof Expr.ArrayOf a ? a.items() : inv.args();
        } else if (body instanceof Expr.Invoke inv && inv.method().isStatic() && inv.method().is("kotlin/collections/CollectionsKt", "listOf")) {
            raw = unwrap(inv.args().get(0)) instanceof Expr.ArrayOf a ? a.items() : inv.args();
        } else if (body instanceof Expr.Invoke inv && inv.method().isStatic() && inv.method().is("kotlin/TuplesKt", "to")) {
            raw = inv.args();
        } else if (body instanceof Expr.NewObject n && n.type().getInternalName().equals("kotlin/Triple")) {
            raw = n.args();
        } else {
            raw = List.of(body);
        }
        List<Expr> keys = columns(raw, ref, roots, model, false);
        for (Expr k : keys) {
            if (!Normalizer.usesEntity(k)) throw LqlMessage.GROUP_KEY_NO_ENTITY.exceptionAt(ref.describe(), k);
        }
        return keys;
    }

    /**
     * Checks rule J3: when there are aggregates or group keys, every select value and every having condition part
     * without an aggregate must be a group key (an SQL rule).
     *
     * @param columns the select values
     * @param keys    the group keys
     * @param having  the having conditions
     * @param where   a name for error messages
     * @throws LambdaAnalysisException if a value is not a key
     */
    public static void checkGrouping(List<Expr> columns, List<Expr> keys, List<Pred> having, String where) {
        boolean grouped = !keys.isEmpty() || columns.stream().anyMatch(SelectReader::hasAggregate);
        if (!grouped) return;
        for (Expr c : columns) {
            if (!hasAggregate(c) && Normalizer.usesEntity(c) && !keys.contains(c)) {
                throw LqlMessage.NOT_GROUP_KEY.exceptionAt(where, c);
            }
        }
        for (Pred p : having) {
            for (Expr.Column c : bareColumns(p)) {
                if (!keys.contains(c)) throw LqlMessage.HAVING_NOT_GROUP_KEY.exceptionAt(where, c);
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Tells whether a lambda returns its first entity parameter itself ({@code o -> o}, Kotlin {@code { it }}): the
     * whole row, which {@code count(o -> o)} counts as {@code count(*)} (ROADMAP D69).
     *
     * <p>Example: {@code o -> o} returns {@code true}; {@code o -> o.getId()} returns {@code false}.
     *
     * @param ref the lambda
     * @return {@code true} if the body is the first parameter
     */
    public static boolean isWholeRow(LambdaRef ref) {
        if (!ref.isLambdaBody()) return false;
        Node node = Decoder.decodeValue(ref, BytecodeLoader.load(ref));
        if (!(node instanceof Node.Value v)) return false;
        Expr e = v.value();
        while (e instanceof Expr.Convert c) e = c.operand();
        return e instanceof Expr.Param p && p.index() == 0;
    }

    private static Expr body(LambdaRef ref) {
        if (!ref.isLambdaBody()) {
            Expr getter = SortReader.getterReference(ref);
            return getter;
        }
        Node node = Decoder.decodeValue(ref, BytecodeLoader.load(ref));
        if (node instanceof Node.Value v) return v.value();
        throw LqlMessage.CONDITION_IN_SELECT.exceptionAt(ref.describe());
    }

    private static Expr unwrap(Expr e) {
        while (e instanceof Expr.Convert c) e = c.operand();
        return e;
    }

    private static List<Expr> columns(List<Expr> raw, LambdaRef ref, List<Class<?>> roots, EntityModel model, boolean aggregates) {
        List<Expr> out = new ArrayList<>();
        for (Expr e : raw) {
            if (e == null) throw LqlMessage.EMPTY_ARRAY_ELEMENT.exceptionAt(ref.describe());
            out.add(aggregates ? Normalizer.normalizeSelected(e, roots, model, ref.classLoader(), ref.describe())   // select values (D70)
                    : Normalizer.normalizeValue(e, roots, model, ref.classLoader(), ref.describe(), false));
        }
        return out;
    }

    private static boolean hasAggregate(Expr e) {
        return switch (e) {
            case Expr.Aggregate a -> true;
            case Expr.Arith a -> hasAggregate(a.left()) || hasAggregate(a.right());
            case Expr.Neg n -> hasAggregate(n.operand());
            case Expr.Coalesce c -> hasAggregate(c.first()) || hasAggregate(c.second());
            case Expr.Case c -> c.exprs().stream().anyMatch(SelectReader::hasAggregate);
            default -> false;
        };
    }

    private static List<Expr.Column> bareColumns(Pred p) {
        List<Expr.Column> out = new ArrayList<>();
        switch (p) {
            case Pred.And a -> a.items().forEach(i -> out.addAll(bareColumns(i)));
            case Pred.Or o -> o.items().forEach(i -> out.addAll(bareColumns(i)));
            case Pred.Not n -> out.addAll(bareColumns(n.operand()));
            case Pred.Cmp c -> {
                bare(c.left(), out);
                bare(c.right(), out);
            }
            case Pred.IsNull n -> bare(n.operand(), out);
            case Pred.Truth t -> bare(t.operand(), out);
            case Pred.In in -> bare(in.value(), out);
            case Pred.Like l -> bare(l.value(), out);
            case Pred.Bool b -> { }
        }
        return out;
    }

    private static void bare(Expr e, List<Expr.Column> out) {
        switch (e) {
            case Expr.Column c -> out.add(c);
            case Expr.Arith a -> {
                bare(a.left(), out);
                bare(a.right(), out);
            }
            case Expr.Neg n -> bare(n.operand(), out);
            case Expr.Coalesce c -> {
                bare(c.first(), out);
                bare(c.second(), out);
            }
            case Expr.Case c -> c.exprs().forEach(x -> bare(x, out));
            default -> { }
        }
    }
}
