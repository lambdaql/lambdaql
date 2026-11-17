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
 *   <li>Builder form: {@code (o, by) -> by.set(o.getStatus(), 1).set(o.getPrice(), p)}, for entities without setters.</li>
 * </ul>
 * Rules checked here: U1 (only setter calls, at least one), U3 (a value must not read a column that an earlier
 * assignment wrote — the SQL meaning is the value before the update, and MariaDB differs), U4 (no column twice),
 * U6 ({@code null} into a column that can never be NULL).
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/sem/AssignmentReaderTest.java">AssignmentReaderTest</a>):
 * {@snippet class="org.lambdaql.engine.sem.AssignmentReaderTest" region="read-assign"}
 */
public final class AssignmentReader {

    private static final String SET_BY = "org/lambdaql/query/SetBy";

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
     * Reads a builder-form lambda: {@code (o, by) -> by.set(o.getStatus(), 1).set(o.getPrice(), p)},
     * also as statements ({@code { by.set(a, 1); by.set(b, 2); }}) or Kotlin {@code by[o.status] = 1}.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/sem/AssignmentReaderTest.java">AssignmentReaderTest</a>):
     * {@snippet class="org.lambdaql.engine.sem.AssignmentReaderTest" region="read-by"}
     *
     * @param ref   the lambda
     * @param roots entity class of each entity parameter; the builder parameter comes after them
     * @param model entity information
     * @return the assignments in source order
     * @throws LambdaAnalysisException if the body has anything other than {@code by.set} calls, or a rule is broken
     */
    public static List<Assignment> readBuilder(LambdaRef ref, List<Class<?>> roots, EntityModel model) {
        if (!ref.isLambdaBody()) throw LqlMessage.METHOD_REFERENCE_IN_ASSIGNMENT.exceptionAt(ref.describe());
        int byIndex = roots.size();
        List<Assignment> out = new ArrayList<>();
        // each statement is one chain by.set(..).set(..) whose result is dropped (Java chain, Java/Kotlin statements,
        // Kotlin by[col] = value)
        for (Expr.Invoke statement : Decoder.decodeStatements(ref, BytecodeLoader.load(ref))) {
            List<Assignment> chain = new ArrayList<>();
            Expr e = statement;
            while (true) {
                while (e instanceof Expr.Convert c) e = c.operand();
                if (e instanceof Expr.Param p && p.index() == byIndex) break;
                if (e instanceof Expr.Invoke inv && inv.receiver() != null && inv.method().owner().equals(SET_BY)
                        && inv.method().name().equals("set") && inv.args().size() == 2) {
                    Expr col = Normalizer.normalizeValue(inv.args().get(0), roots, model, ref.classLoader(), ref.describe());
                    if (!(col instanceof Expr.Column target) || target.root() != 0) {
                        throw LqlMessage.BY_SET_TARGET.exceptionAt(ref.describe(), inv.args().get(0));
                    }
                    Expr value = Normalizer.normalizeValue(inv.args().get(1), roots, model, ref.classLoader(), ref.describe());
                    chain.add(0, checkTarget(new Assignment(target, value), ref));
                    e = inv.receiver();
                    continue;
                }
                throw LqlMessage.BY_SET_ONLY.exceptionAt(ref.describe(), e);
            }
            out.addAll(chain);
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
            default -> e;
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
            default -> { }
        }
    }
}
