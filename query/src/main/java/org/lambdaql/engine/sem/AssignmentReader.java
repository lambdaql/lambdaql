package org.lambdaql.engine.sem;

import org.lambdaql.i18n.LqlMessage;
import org.lambdaql.LambdaAnalysisException;
import org.lambdaql.engine.decode.Decoder;
import org.lambdaql.engine.ir.Assignment;
import org.lambdaql.engine.ir.Expr;
import org.lambdaql.engine.load.BytecodeLoader;
import org.lambdaql.engine.load.LambdaRef;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads assignment lambdas of UPDATE and INSERT (ROADMAP M10, D28) into {@link Assignment}s, and checks the rules.
 * <ul>
 *   <li>Setter form: {@code o -> { o.setStatus(1); o.setPrice(o.getPrice() * 1.1); }} (Kotlin: {@code it.status = 1}).</li>
 *   <li>{@code Dml.value} form: {@code o -> { value(o.getStatus(), 1); value(o.getPrice(), p); }}, for entities without
 *       setters (D49). Both forms can be mixed in one lambda.</li>
 *   <li>A parameter after the entities ({@code (o, sub) -> ...}) starts subqueries in the values.</li>
 * </ul>
 * Rules checked here: U1 (only setter calls, at least one), U3 (a value must not read a column that an earlier
 * assignment wrote — the SQL meaning is the value before the update, and MariaDB differs), U4 (no column twice),
 * U6 ({@code null} into a column that can never be NULL).
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/sem/AssignmentReaderTest.java">AssignmentReaderTest</a>):
 * {@snippet class="org.lambdaql.engine.sem.AssignmentReaderTest" region="read-assign"}
 */
public final class AssignmentReader {

    private static final String DML = "org/lambdaql/Dml";

    private AssignmentReader() {
    }

    /**
     * Reads a setter-form lambda.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/sem/AssignmentReaderTest.java">AssignmentReaderTest</a>):
     * {@snippet class="org.lambdaql.engine.sem.AssignmentReaderTest" region="read-assign"}
     *
     * @param ref        the lambda
     * @param roots      entity class of each entity parameter; parameter 0 is the target
     * @param hostParam  index of a parameter that is not an entity but a value read in Java (the list element of
     *                   {@code values(list, (o, l) -> ...)}), or -1
     * @param model      entity information
     * @return the assignments in source order
     * @throws LambdaAnalysisException if the lambda breaks a rule
     */
    public static List<Assignment> readSetters(LambdaRef ref, List<Class<?>> roots, int hostParam, EntityModel model) {
        return readSetters(ref, roots, hostParam, model, false);
    }

    /**
     * Reads a setter-form lambda, and allows aggregate functions ({@code Agg}) in the values when {@code aggregates}
     * is true (INSERT ... SELECT with groupBy, ROADMAP M9).
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java">ProjectionTest</a>):
     * {@snippet class="org.lambdaql.query.ProjectionTest" region="insert-group"}
     *
     * @param ref        the lambda
     * @param roots      entity class of each entity parameter; parameter 0 is the target
     * @param hostParam  index of a parameter read in Java, or -1
     * @param model      entity information
     * @param aggregates {@code true} to allow {@code Agg} calls in the values
     * @return the assignments in source order
     * @throws LambdaAnalysisException if the lambda breaks a rule
     */
    public static List<Assignment> readSetters(LambdaRef ref, List<Class<?>> roots, int hostParam, EntityModel model,
                                               boolean aggregates) {
        if (!ref.isLambdaBody()) throw LqlMessage.METHOD_REFERENCE_IN_ASSIGNMENT.exceptionAt(ref.describe());
        List<Expr.Invoke> calls = Decoder.decodeStatements(ref, BytecodeLoader.load(ref));
        List<Assignment> out = new ArrayList<>();
        for (Expr.Invoke call : calls) {
            if (call.receiver() == null && call.method().owner().equals(DML) && call.method().name().equals("value")
                    && call.args().size() == 2) {
                // Dml.value(column, value): the column is a getter path on the target (D49)
                Expr col = Normalizer.normalizeValue(asHost(call.args().get(0), hostParam, ref), roots, model, ref.classLoader(),
                        ref.describe());
                Expr.Invoke getter = getterCall(call.args().get(0));
                if (!(col instanceof Expr.Column c) || c.root() != 0 || getter == null) {
                    throw LqlMessage.VALUE_TARGET.exceptionAt(ref.describe(), call.args().get(0));
                }
                // the same rules as the setter of that property (U1, U6): value(o.getX(), v) is o.setX(v)
                Expr.Column target = Normalizer.assignTarget(getter.receiver(), setterOf(getter.method().name()), roots, model,
                        ref.classLoader(), ref.describe());
                Expr value = Normalizer.normalizeValue(asHost(call.args().get(1), hostParam, ref), roots, model,
                        ref.classLoader(), ref.describe(), aggregates);
                checkValueType(target, value, ref);
                out.add(checkTarget(new Assignment(target, value), ref));
                continue;
            }
            if (call.receiver() == null || call.args().size() != 1) {
                throw LqlMessage.ASSIGN_ONLY_SETTERS.exceptionAt(ref.describe(), call.method().ownerSimpleName() + "." + call.method().name());
            }
            Expr receiver = asHost(call.receiver(), hostParam, ref);
            Expr.Column target = Normalizer.assignTarget(receiver, call.method().name(), roots, model, ref.classLoader(), ref.describe());
            Expr value = Normalizer.normalizeValue(asHost(call.args().get(0), hostParam, ref), roots, model, ref.classLoader(),
                    ref.describe(), aggregates);
            out.add(checkTarget(new Assignment(target, value), ref));
        }
        if (out.isEmpty()) throw LqlMessage.ASSIGN_NONE.exceptionAt(ref.describe());
        return List.copyOf(out);
    }

    /**
     * Checks the rules between all assignments of one statement (also across many {@code set()} calls).
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/sem/AssignmentReaderTest.java">AssignmentReaderTest</a>):
     * {@snippet class="org.lambdaql.engine.sem.AssignmentReaderTest" region="rules"}
     *
     * @param all   the assignments in order
     * @param where a name for error messages
     * @throws LambdaAnalysisException for U3 (reading a column written before) or U4 (writing a column twice)
     */
    public static void checkRules(List<Assignment> all, String where) {
        for (int i = 0; i < all.size(); i++) {
            Assignment a = all.get(i);
            for (int j = 0; j < i; j++) {
                Expr.Column earlier = all.get(j).target();
                if (overlaps(earlier, a.target())) {
                    throw LqlMessage.ASSIGN_TWICE.exceptionAt(where, String.join(".", a.target().path()));
                }
                for (Expr.Column read : columns(a.value())) {
                    if (read.root() == earlier.root() && overlaps(earlier, read)) {
                        throw LqlMessage.ASSIGN_READS_WRITTEN.exceptionAt(where, String.join(".", read.path()));
                    }
                }
            }
        }
    }

    /**
     * Changes the root index of every column, for INSERT ... SELECT where the source is lambda parameter 1
     * but the select statement names it as root 0.
     *
     * @param e    an expression
     * @param from the old root index
     * @param to   the new root index
     * @return the same expression with changed columns
     */
    public static Expr reroot(Expr e, int from, int to) {
        return switch (e) {
            case Expr.Column c -> c.root() == from
                    ? new Expr.Column(to, c.path(), c.association(), c.javaType(), c.nonNull(), c.idPath()) : c;
            case Expr.Arith a -> new Expr.Arith(a.op(), reroot(a.left(), from, to), reroot(a.right(), from, to), a.type());
            case Expr.Neg n -> new Expr.Neg(reroot(n.operand(), from, to), n.type());
            case Expr.Coalesce c -> new Expr.Coalesce(reroot(c.first(), from, to), reroot(c.second(), from, to), c.type());
            case Expr.Aggregate a -> a.arg() == null ? a : new Expr.Aggregate(a.fn(), reroot(a.arg(), from, to), a.type());
            case Expr.Func f -> new Expr.Func(f.key(), f.sql(), f.asType(), f.args().stream().map(x -> reroot(x, from, to)).toList(), f.type());
            case Expr.Subquery q -> new Expr.Subquery(q.kind(), q.root(), q.entity(),
                    q.wheres().stream().map(p -> reroot(p, from, to)).toList(),
                    q.select() == null ? null : reroot(q.select(), from, to), q.agg(),
                    q.sorts().stream().map(i -> new org.lambdaql.engine.ir.SortItem(reroot(i.key(), from, to), i.kind(), i.arg())).toList(),
                    q.limit(), q.type(), q.joins().stream().map(j -> new Expr.Subquery.Join(j.root(), j.entity(), j.left(),
                            reroot(j.on(), from, to))).toList());
            default -> e;
        };
    }

    /** {@link #reroot(Expr, int, int)} for a condition inside a subquery. */
    private static org.lambdaql.engine.ir.Pred reroot(org.lambdaql.engine.ir.Pred p, int from, int to) {
        return switch (p) {
            case org.lambdaql.engine.ir.Pred.Cmp c -> new org.lambdaql.engine.ir.Pred.Cmp(c.op(), reroot(c.left(), from, to), reroot(c.right(), from, to), c.nullSafe());
            case org.lambdaql.engine.ir.Pred.IsNull n -> new org.lambdaql.engine.ir.Pred.IsNull(reroot(n.operand(), from, to));
            case org.lambdaql.engine.ir.Pred.Truth t -> new org.lambdaql.engine.ir.Pred.Truth(reroot(t.operand(), from, to));
            case org.lambdaql.engine.ir.Pred.In i -> new org.lambdaql.engine.ir.Pred.In(reroot(i.value(), from, to), reroot(i.values(), from, to));
            case org.lambdaql.engine.ir.Pred.And a -> new org.lambdaql.engine.ir.Pred.And(a.items().stream().map(x -> reroot(x, from, to)).toList());
            case org.lambdaql.engine.ir.Pred.Or o -> new org.lambdaql.engine.ir.Pred.Or(o.items().stream().map(x -> reroot(x, from, to)).toList());
            case org.lambdaql.engine.ir.Pred.Not n -> new org.lambdaql.engine.ir.Pred.Not(reroot(n.operand(), from, to));
            case org.lambdaql.engine.ir.Pred.Bool b -> b;
        };
    }

    /**
     * Lists the columns used in a value.
     *
     * @param e a normalized value
     * @return the columns
     */
    public static List<Expr.Column> columns(Expr e) {
        List<Expr.Column> out = new ArrayList<>();
        collect(e, out);
        return out;
    }

    // ------------------------------------------------------------------ helpers

    private static Assignment checkTarget(Assignment a, LambdaRef ref) {
        if (a.target().root() != 0) {
            throw LqlMessage.ASSIGN_TARGET_NOT_FIRST.exceptionAt(ref.describe(), a.target());
        }
        if (a.value() instanceof Expr.Const c && c.isNull() && a.target().nonNull()) {
            throw LqlMessage.ASSIGN_NULL_NOT_NULL.exceptionAt(ref.describe(), String.join(".", a.target().path()));
        }
        return a;
    }

    private static final List<Class<?>> INTEGRAL = List.of(Byte.class, Short.class, Integer.class, Long.class,
            java.math.BigInteger.class);

    /**
     * U20: {@code <V> value(V, V)} lets any two types compile ({@code value(o.getStatus(), "abc")}), so the value type
     * is checked against the column here. A number fits a number column as in SQL, but a decimal does not fit a whole
     * number column (the fraction would be lost). An unknown type ({@code Object}) is not checked.
     */
    private static void checkValueType(Expr.Column target, Expr value, LambdaRef ref) {
        if (value instanceof Expr.Const c && c.isNull()) return;
        org.objectweb.asm.Type vt = value.type();
        if (vt.getSort() == org.objectweb.asm.Type.OBJECT && vt.getInternalName().equals("java/lang/Object")) return;
        Class<?> v;
        try {
            v = box(switch (vt.getSort()) {
                case org.objectweb.asm.Type.BOOLEAN -> boolean.class;
                case org.objectweb.asm.Type.CHAR -> char.class;
                case org.objectweb.asm.Type.BYTE -> byte.class;
                case org.objectweb.asm.Type.SHORT -> short.class;
                case org.objectweb.asm.Type.INT -> int.class;
                case org.objectweb.asm.Type.FLOAT -> float.class;
                case org.objectweb.asm.Type.LONG -> long.class;
                case org.objectweb.asm.Type.DOUBLE -> double.class;
                default -> Class.forName(vt.getClassName(), false, ref.classLoader());
            });
        } catch (ClassNotFoundException | LinkageError e) {
            return;
        }
        // an erased generic type (sum(...) gives Number, a generic method gives an interface) says too little
        if (v == Number.class || v.isInterface()) return;
        Class<?> t = box(target.javaType());
        if (t.isAssignableFrom(v)) return;
        if (Number.class.isAssignableFrom(t) && Number.class.isAssignableFrom(v)
                && (!INTEGRAL.contains(t) || INTEGRAL.contains(v))) return;
        throw LqlMessage.VALUE_TYPE.exceptionAt(ref.describe(), String.join(".", target.path()), t.getSimpleName(),
                v.getSimpleName());
    }

    private static Class<?> box(Class<?> c) {
        if (!c.isPrimitive()) return c;
        if (c == int.class) return Integer.class;
        if (c == long.class) return Long.class;
        if (c == double.class) return Double.class;
        if (c == float.class) return Float.class;
        if (c == boolean.class) return Boolean.class;
        if (c == short.class) return Short.class;
        if (c == byte.class) return Byte.class;
        if (c == char.class) return Character.class;
        return c;
    }

    /** The getter call of {@code Dml.value}'s column argument, without boxing; null if it is not a getter call. */
    private static Expr.Invoke getterCall(Expr e) {
        while (true) {
            if (e instanceof Expr.Convert c) {
                e = c.operand();
            } else if (e instanceof Expr.Invoke i && i.method().isStatic() && i.method().name().equals("valueOf")
                    && i.args().size() == 1 && i.method().owner().startsWith("java/lang/")) {
                e = i.args().get(0);   // Integer.valueOf(o.getStatus()): boxing for the generic parameter
            } else {
                break;
            }
        }
        return e instanceof Expr.Invoke i && i.receiver() != null && i.args().isEmpty() ? i : null;
    }

    /** The setter name for a getter name: getStatus and isActive give setStatus and setActive; status gives setStatus. */
    private static String setterOf(String getter) {
        if (getter.startsWith("get") && getter.length() > 3) return "set" + getter.substring(3);
        if (getter.startsWith("is") && getter.length() > 2 && Character.isUpperCase(getter.charAt(2))) return "set" + getter.substring(2);
        return "set" + Character.toUpperCase(getter.charAt(0)) + getter.substring(1);
    }

    /** Replaces the host parameter with a captured slot after the real captured values. */
    private static Expr asHost(Expr e, int hostParam, LambdaRef ref) {
        if (hostParam < 0) return e;
        return replaceParam(e, hostParam, new Expr.Captured(ref.capturedCount(), org.objectweb.asm.Type.getType(Object.class)));
    }

    private static Expr replaceParam(Expr e, int index, Expr with) {
        return switch (e) {
            case Expr.Param p -> p.index() == index ? with : p;
            case Expr.Invoke i -> new Expr.Invoke(i.receiver() == null ? null : replaceParam(i.receiver(), index, with),
                    i.method(), i.args().stream().map(a -> replaceParam(a, index, with)).toList());
            case Expr.GetField f -> f.receiver() == null ? f
                    : new Expr.GetField(replaceParam(f.receiver(), index, with), f.owner(), f.name(), f.type());
            case Expr.Arith a -> new Expr.Arith(a.op(), replaceParam(a.left(), index, with), replaceParam(a.right(), index, with), a.type());
            case Expr.Neg n -> new Expr.Neg(replaceParam(n.operand(), index, with), n.type());
            case Expr.Convert c -> new Expr.Convert(replaceParam(c.operand(), index, with), c.type());
            case Expr.Coalesce c -> new Expr.Coalesce(replaceParam(c.first(), index, with), replaceParam(c.second(), index, with), c.type());
            case Expr.NewObject n -> new Expr.NewObject(n.type(), n.constructor(), n.args().stream().map(a -> replaceParam(a, index, with)).toList());
            default -> e;
        };
    }

    private static void collect(org.lambdaql.engine.ir.Pred p, List<Expr.Column> out) {
        switch (p) {
            case org.lambdaql.engine.ir.Pred.Cmp c -> {
                collect(c.left(), out);
                collect(c.right(), out);
            }
            case org.lambdaql.engine.ir.Pred.IsNull n -> collect(n.operand(), out);
            case org.lambdaql.engine.ir.Pred.Truth t -> collect(t.operand(), out);
            case org.lambdaql.engine.ir.Pred.In i -> {
                collect(i.value(), out);
                collect(i.values(), out);
            }
            case org.lambdaql.engine.ir.Pred.And a -> a.items().forEach(x -> collect(x, out));
            case org.lambdaql.engine.ir.Pred.Or o -> o.items().forEach(x -> collect(x, out));
            case org.lambdaql.engine.ir.Pred.Not n -> collect(n.operand(), out);
            case org.lambdaql.engine.ir.Pred.Bool b -> { }
        }
    }

    private static boolean overlaps(Expr.Column a, Expr.Column b) {
        if (a.root() != b.root()) return false;
        List<String> x = a.path(), y = b.path();
        int n = Math.min(x.size(), y.size());
        return x.subList(0, n).equals(y.subList(0, n));
    }

    private static void collect(Expr e, List<Expr.Column> out) {
        switch (e) {
            case Expr.Column c -> out.add(c);
            case Expr.Arith a -> {
                collect(a.left(), out);
                collect(a.right(), out);
            }
            case Expr.Neg n -> collect(n.operand(), out);
            case Expr.Coalesce c -> {
                collect(c.first(), out);
                collect(c.second(), out);
            }
            case Expr.Aggregate a -> {
                if (a.arg() != null) collect(a.arg(), out);
            }
            case Expr.Func f -> f.args().forEach(x -> collect(x, out));
            case Expr.Subquery q -> {
                // the outer row's columns read inside a subquery (its own roots start at q.root())
                List<Expr.Column> inner = new ArrayList<>();
                q.wheres().forEach(p -> collect(p, inner));
                q.joins().forEach(j -> collect(j.on(), inner));
                if (q.select() != null) collect(q.select(), inner);
                for (Expr.Column c : inner) if (c.root() < q.root()) out.add(c);
            }
            default -> { }
        }
    }
}
