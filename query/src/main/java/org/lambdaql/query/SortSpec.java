package org.lambdaql.query;

import org.lambdaql.engine.ir.Expr;
import org.lambdaql.engine.ir.SortItem;
import org.lambdaql.engine.sem.SortReader;
import org.objectweb.asm.Type;
import org.lambdaql.engine.load.LambdaRef;
import org.lambdaql.engine.render.HqlRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A sort that you can keep and use again (ROADMAP S6), for example as a constant:
 * {@code static final SortSpec<Order> NEWEST = SortSpec.of(Order.class, (o, by) -> by.desc(o.getUpdateAt()).asc(o.getId()))}.
 * <p>
 * A {@code SortSpec} cannot be changed. {@link #then(SortSpec)} and {@link #reversed()} return new objects,
 * so one spec can be shared by many queries and threads.
 * The lambdas are checked when the spec is used in {@code orderBy}, and each lambda is analyzed only once.
 * Captured values are the values at the moment the lambda was made.
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java">SortQueryTest</a>):
 * {@snippet class="org.lambdaql.query.SortQueryTest" region="sort-spec"}
 *
 * @param <T> the entity type
 */
public final class SortSpec<T> {

    /**
     * One part of the spec: a sort lambda, or one property path with a direction.
     *
     * @param ref       the lambda, or {@code null} for a path part
     * @param path      the property path, or {@code null} for a lambda part
     * @param direction the direction of a path part
     * @param reversed  {@code true} if every key of this part is reversed
     */
    private record Part(LambdaRef ref, String path, Direction direction, boolean reversed) {
        Part reverse() {
            return new Part(ref, path, direction, !reversed);
        }
    }

    private final Class<T> type;
    private final List<Part> parts;

    private SortSpec(Class<T> type, List<Part> parts) {
        this.type = type;
        this.parts = List.copyOf(parts);
    }

    /**
     * Makes a spec from a sort lambda.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java">SortQueryTest</a>):
     * {@snippet class="org.lambdaql.query.SortQueryTest" region="sort-spec"}
     *
     * @param type the entity class
     * @param spec the sort lambda, for example {@code (o, by) -> by.desc(o.getPrice())}
     * @param <T>  the entity type
     * @return the spec
     * @throws NullPointerException if an argument is null
     */
    public static <T> SortSpec<T> of(Class<T> type, SortBy<T> spec) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(spec, "spec");
        return new SortSpec<>(type, List.of(new Part(QueryContext.read(spec), null, null, false)));
    }

    /**
     * Makes a spec from a property path text, for sorts chosen outside the code
     * (for example Spring {@code Sort}, which the lambdaQL Spring module turns into this).
     * <p>
     * When the spec is used, every part of the path must be a persistent property: to-one associations or
     * embedded values in the middle, a basic value at the end. Anything else is an error, so the text can never
     * become other HQL. But any property can be used, so if the text comes from a user (a request parameter),
     * check it against your own list of allowed names first.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java">SortQueryTest</a>):
     * {@snippet class="org.lambdaql.query.SortQueryTest" region="sort-spec-path"}
     *
     * @param type      the entity class
     * @param path      property names joined with {@code .}, for example {@code "customer.name"}
     * @param direction the direction and NULL position
     * @param <T>       the entity type
     * @return the spec
     * @throws NullPointerException if an argument is null
     */
    public static <T> SortSpec<T> ofPath(Class<T> type, String path, Direction direction) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(direction, "direction");
        return new SortSpec<>(type, List.of(new Part(null, path, direction, false)));
    }

    /**
     * Returns a spec that sorts by this spec first, then by {@code next} for rows that are equal.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java">SortQueryTest</a>):
     * {@snippet class="org.lambdaql.query.SortQueryTest" region="sort-spec-then"}
     *
     * @param next the keys to add after this spec's keys
     * @return a new spec
     */
    public SortSpec<T> then(SortSpec<T> next) {
        List<Part> all = new ArrayList<>(parts);
        all.addAll(next.parts);
        return new SortSpec<>(type, all);
    }

    /**
     * Returns a spec that sorts by this spec first, then by the keys of {@code next}.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java">SortQueryTest</a>):
     * {@snippet class="org.lambdaql.query.SortQueryTest" region="sort-spec-then"}
     *
     * @param next a sort lambda
     * @return a new spec
     */
    public SortSpec<T> then(SortBy<T> next) {
        return then(of(type, next));
    }

    /**
     * Returns a spec with every key in the opposite order. The NULL position is reversed too
     * ({@link Direction#reversed()}), so the whole row order is exactly backwards.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java">SortQueryTest</a>):
     * {@snippet class="org.lambdaql.query.SortQueryTest" region="sort-spec-reversed"}
     *
     * @return a new spec
     */
    public SortSpec<T> reversed() {
        List<Part> all = new ArrayList<>();
        for (Part p : parts) all.add(p.reverse());
        return new SortSpec<>(type, all);
    }

    /**
     * Returns the entity class of this spec.
     *
     * @return the class given to {@link #of(Class, SortBy)}
     */
    public Class<T> type() {
        return type;
    }

    /**
     * Analyzes the lambdas (cached per lambda) and returns the keys for the renderer.
     *
     * @param context the builder context
     * @param root    the entity class of the query
     * @return one entry per lambda
     */
    List<HqlRenderer.SortBound> bind(QueryContext context, Class<?> root) {
        List<HqlRenderer.SortBound> out = new ArrayList<>();
        for (Part p : parts) {
            if (p.ref() == null) {
                Expr key = SortReader.readPath(p.path(), root, context.model());
                SortItem item = new SortItem(key, SortItem.Kind.SORT, new Expr.Const(p.direction(), Type.getType(Direction.class)));
                out.add(new HqlRenderer.SortBound(List.of(item), new Object[0], p.reversed()));
                continue;
            }
            List<SortItem> items = context.analyzeSortBy(p.ref(), List.of(root));
            out.add(new HqlRenderer.SortBound(items, p.ref().capturedArgs(), p.reversed()));
        }
        return out;
    }
}
