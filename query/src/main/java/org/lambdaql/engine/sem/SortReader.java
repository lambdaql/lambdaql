package org.lambdaql.engine.sem;

import org.lambdaql.i18n.LqlMessage;
import org.lambdaql.LambdaQLException;
import org.lambdaql.LambdaAnalysisException;
import org.lambdaql.engine.decode.Decoder;
import org.lambdaql.engine.decode.Node;
import org.lambdaql.engine.ir.Expr;
import org.lambdaql.engine.ir.MethodRef;
import org.lambdaql.engine.ir.SortItem;
import org.lambdaql.engine.load.BytecodeLoader;
import org.lambdaql.engine.load.LambdaRef;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandleInfo;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads sort lambdas (ROADMAP 3.7, S1 to S10).
 * <ul>
 *   <li>{@code (o, by) -> by.desc(o.getPrice()).asc(o.getId())}: the body must be one straight chain of
 *       {@code by} calls (rule S4). Conditions, loops and other calls are rejected.</li>
 *   <li>{@code o -> o.getPrice()}: one key, no builder.</li>
 *   <li>{@code Order::getPrice}: a getter reference is read like {@code o -> o.getPrice()} (rule S13).</li>
 * </ul>
 * Keys are normalized like condition values ({@link Normalizer}): getters become columns, and
 * Kotlin {@code ?.} paths, boxing and {@code checkcast Comparable} are skipped.
 * The direction values ({@code nullsFirst}, {@code Direction}) stay as constants or host values;
 * the renderer reads them on every run.
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/sem/SortReaderTest.java">SortReaderTest</a>):
 * {@snippet class="org.lambdaql.engine.sem.SortReaderTest" region="read-sort"}
 */
public final class SortReader {

    private static final String BY = "org/lambdaql/query/By";

    private final List<Class<?>> roots;
    private final EntityModel model;
    private final LambdaRef ref;

    private SortReader(LambdaRef ref, List<Class<?>> roots, EntityModel model) {
        this.ref = ref;
        this.roots = roots;
        this.model = model;
    }

    /**
     * Reads a sort lambda with the builder: {@code (o, by) -> by.desc(o.getPrice()).asc(o.getId())}.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/sem/SortReaderTest.java">SortReaderTest</a>):
     * {@snippet class="org.lambdaql.engine.sem.SortReaderTest" region="read-sort"}
     *
     * @param ref   the lambda
     * @param roots entity class of each entity parameter; the {@code by} parameter comes after them
     * @param model entity information
     * @return the keys in order (first key first)
     * @throws LambdaAnalysisException if the body is not a straight chain of {@code by} calls,
     *                                 or a key cannot become SQL
     */
    public static List<SortItem> readSortBy(LambdaRef ref, List<Class<?>> roots, EntityModel model) {
        if (!ref.isLambdaBody()) {
            throw LqlMessage.METHOD_REFERENCE_IN_SORT_BUILDER.exceptionAt(ref.describe());
        }
        return new SortReader(ref, roots, model).chain(body(ref));
    }

    /**
     * Reads a one-key sort lambda: {@code o -> o.getPrice()} or {@code Order::getPrice}.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/sem/SortReaderTest.java">SortReaderTest</a>):
     * {@snippet class="org.lambdaql.engine.sem.SortReaderTest" region="read-key"}
     *
     * @param ref   the lambda or getter reference
     * @param root  the entity class
     * @param model entity information
     * @return the normalized key
     * @throws LambdaAnalysisException if the key cannot become SQL, or the reference is not a getter of the entity
     */
    public static Expr readSortKey(LambdaRef ref, Class<?> root, EntityModel model) {
        SortReader r = new SortReader(ref, List.of(root), model);
        Expr raw = ref.isLambdaBody() ? body(ref) : getterReference(ref);
        return r.key(raw);
    }

    /**
     * Turns a getter reference ({@code Order::getPrice}) into the expression {@code param0.getPrice()}.
     * Only unbound instance methods with no arguments are allowed (rule S13): static methods,
     * methods with arguments and references bound to an object are rejected.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/sem/SortReaderTest.java">SortReaderTest</a>):
     * {@snippet class="org.lambdaql.engine.sem.SortReaderTest" region="read-key"}
     *
     * @param ref a method reference
     * @return the call expression on lambda parameter 0
     * @throws LambdaAnalysisException if the reference is not a getter
     */
    public static Expr getterReference(LambdaRef ref) {
        int kind = ref.implMethodKind();
        boolean instance = kind == MethodHandleInfo.REF_invokeVirtual || kind == MethodHandleInfo.REF_invokeInterface;
        Type desc = Type.getMethodType(ref.implMethodDescriptor());
        if (!instance || ref.capturedCount() != 0 || desc.getArgumentTypes().length != 0
                || desc.getReturnType().getSort() == Type.VOID) {
            throw LqlMessage.METHOD_REFERENCE_NOT_GETTER.exceptionAt(ref.describe());
        }
        MethodRef m = new MethodRef(ref.implClass(), ref.implMethodName(), ref.implMethodDescriptor(), false,
                kind == MethodHandleInfo.REF_invokeInterface);
        return new Expr.Invoke(new Expr.Param(0, Type.getObjectType(ref.implClass())), m, List.of());
    }

    /**
     * Turns a property path text ({@code "customer.name"}) into a column, for sorts that come from outside
     * the code (for example Spring {@code Sort}). Every part must be a real persistent property:
     * the parts before the last one must be to-one associations or embedded values, and the last part a basic value.
     * So the text can never become HQL that is not a column.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/sem/SortReaderTest.java">SortReaderTest</a>):
     * {@snippet class="org.lambdaql.engine.sem.SortReaderTest" region="read-path"}
     *
     * @param path  property names joined with {@code .}, for example {@code customer.name}
     * @param root  the entity class
     * @param model entity information
     * @return the column
     * @throws IllegalArgumentException if a part is not a property, or the path does not end at a basic value
     */
    public static Expr.Column readPath(String path, Class<?> root, EntityModel model) {
        if (path == null || path.isBlank()) throw new IllegalArgumentException(LqlMessage.SORT_PATH_EMPTY.format());
        String[] parts = path.split("\\.", -1);
        List<String> names = new ArrayList<>();
        List<Boolean> assoc = new ArrayList<>();
        Class<?> owner = root;
        boolean parentIsAssociation = false;
        for (int i = 0; i < parts.length; i++) {
            String name = parts[i];
            String ownerName = owner.getSimpleName();
            EntityModel.Property prop = model.property(owner, name).orElseThrow(() -> new IllegalArgumentException(LqlMessage.SORT_PATH_UNKNOWN.format(ownerName + "." + name, path)));
            names.add(prop.name());
            boolean last = i == parts.length - 1;
            if (last) {
                if (prop.kind() != EntityModel.Kind.BASIC) {
                    throw new IllegalArgumentException(LqlMessage.SORT_PATH_NOT_VALUE.format(path));
                }
                assoc.add(false);
                return new Expr.Column(0, names, assoc, prop.javaType(), prop.nonNull(), prop.id() && parentIsAssociation);
            }
            switch (prop.kind()) {
                case ASSOCIATION -> assoc.add(true);
                case EMBEDDED -> assoc.add(false);
                default -> throw new IllegalArgumentException(LqlMessage.SORT_PATH_MIDDLE.format(name, path));
            }
            parentIsAssociation = prop.kind() == EntityModel.Kind.ASSOCIATION;
            owner = prop.javaType();
        }
        throw new IllegalArgumentException(LqlMessage.SORT_PATH_EMPTY.format());
    }

    // ------------------------------------------------------------------ chain

    private static Expr body(LambdaRef ref) {
        Node node = Decoder.decodeValue(ref, BytecodeLoader.load(ref));
        if (node instanceof Node.Value v) return v.value();
        throw LqlMessage.CONDITION_IN_SORT.exceptionAt(ref.describe());
    }

    private List<SortItem> chain(Expr top) {
        int byIndex = roots.size();
        List<SortItem> items = new ArrayList<>();
        Expr e = top;
        while (true) {
            while (e instanceof Expr.Convert c) e = c.operand();
            if (e instanceof Expr.Param p && p.index() == byIndex) break;
            if (e instanceof Expr.Invoke inv && inv.receiver() != null && inv.method().owner().equals(BY)) {
                items.add(0, item(inv));
                e = inv.receiver();
                continue;
            }
            throw error(LqlMessage.SORT_NOT_CHAIN, e);
        }
        if (items.isEmpty()) throw error(LqlMessage.SORT_NO_KEY);
        return List.copyOf(items);
    }

    private SortItem item(Expr.Invoke inv) {
        String name = inv.method().name();
        List<Expr> args = inv.args();
        SortItem.Kind kind = switch (name) {
            case "asc" -> SortItem.Kind.ASC;
            case "desc" -> SortItem.Kind.DESC;
            case "sort" -> SortItem.Kind.SORT;
            default -> throw error(LqlMessage.SORT_UNSUPPORTED_METHOD, name);
        };
        Expr key = key(args.get(0));
        Expr arg = args.size() > 1 ? direction(args.get(1)) : null;
        return new SortItem(key, kind, arg);
    }

    private Expr key(Expr raw) {
        Expr key = Normalizer.normalizeValue(raw, roots, model, ref.classLoader(), ref.describe());
        if (!Normalizer.usesEntity(key)) {
            throw error(LqlMessage.SORT_KEY_NO_ENTITY, raw);
        }
        if (key instanceof Expr.Column c && model.isEntity(c.javaType())) {
            throw error(LqlMessage.SORT_BY_ENTITY, c);
        }
        return key;
    }

    private Expr direction(Expr raw) {
        Expr v = Normalizer.normalizeValue(raw, roots, model, ref.classLoader(), ref.describe());
        if (Normalizer.usesEntity(v)) {
            throw error(LqlMessage.SORT_DIRECTION_FROM_ENTITY, raw);
        }
        return v;
    }

    private LambdaQLException error(LqlMessage key, Object... args) {
        return key.exceptionAt(ref.describe(), args);
    }
}
