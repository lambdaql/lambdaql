package org.lambdaql.engine.sem;

import org.lambdaql.SqlProcedure;
import org.lambdaql.engine.decode.Decoder;
import org.lambdaql.engine.decode.Node;
import org.lambdaql.engine.ir.Expr;
import org.lambdaql.engine.ir.MethodRef;
import org.lambdaql.engine.load.BytecodeLoader;
import org.lambdaql.engine.load.LambdaRef;
import org.lambdaql.i18n.LqlMessage;
import org.objectweb.asm.Type;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

/**
 * Reads procedure call lambdas {@code p -> p.calc(id, rate)} and function call lambdas
 * {@code () -> OrderFns.discount(price)} (ROADMAP M11, D30, PR6, PR12).
 * The body must be one call; the arguments are captured values, constants or a calculation of them,
 * which are computed in Java when the call runs.
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java">ProcedureTest</a>):
 * {@snippet class="org.lambdaql.query.ProcedureTest" region="call"}
 */
public final class ProcedureReader {

    private static final Set<String> BOXES = Set.of("java/lang/Integer", "java/lang/Long", "java/lang/Double",
            "java/lang/Float", "java/lang/Short", "java/lang/Byte", "java/lang/Boolean", "java/lang/Character");

    /**
     * A procedure call.
     *
     * @param stub   the stub method
     * @param schema the schema, or null
     * @param pkg    the Oracle package, or null
     * @param name   the procedure name (PR1)
     * @param args   the argument values (computed in Java)
     */
    public record ProcCall(Method stub, String schema, String pkg, String name, List<Expr> args) {
        /** Makes an unmodifiable copy of the arguments. */
        public ProcCall {
            args = List.copyOf(args);
        }

        /**
         * Returns the name with the schema and package, as written in the call.
         *
         * @return for example {@code LQL.SQ_PKG.CALC}
         */
        public String fullName() {
            return (schema == null ? "" : schema + ".") + (pkg == null ? "" : pkg + ".") + name;
        }
    }

    /**
     * A database function call on its own.
     *
     * @param fn   the function
     * @param args the argument values (computed in Java)
     */
    public record FnCall(SqlFunctions.Info fn, List<Expr> args) {
        /** Makes an unmodifiable copy of the arguments. */
        public FnCall {
            args = List.copyOf(args);
        }
    }

    private ProcedureReader() {
    }

    /**
     * Reads a procedure call lambda.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java">ProcedureTest</a>):
     * {@snippet class="org.lambdaql.query.ProcedureTest" region="call"}
     *
     * @param ref     the lambda
     * @param procs   the stub interface
     * @param returns {@code true} for {@code call} (a value lambda), {@code false} for {@code exec}
     * @return the call
     * @throws org.lambdaql.LambdaQLException if the lambda is not one stub call, or the method is not a procedure
     */
    public static ProcCall readCall(LambdaRef ref, Class<?> procs, boolean returns) {
        Expr body;
        if (returns) {
            Node node = Decoder.decodeValue(ref, BytecodeLoader.load(ref));
            if (!(node instanceof Node.Value v)) throw LqlMessage.PROC_LAMBDA_SHAPE.exceptionAt(ref.describe());
            body = unwrap(v.value());
        } else {
            List<Expr.Invoke> calls = Decoder.decodeStatements(ref, BytecodeLoader.load(ref));
            if (calls.size() != 1) throw LqlMessage.PROC_LAMBDA_SHAPE.exceptionAt(ref.describe());
            body = calls.get(0);
        }
        if (!(body instanceof Expr.Invoke inv) || !(inv.receiver() instanceof Expr.Param p) || p.index() != 0) {
            throw LqlMessage.PROC_LAMBDA_SHAPE.exceptionAt(ref.describe());
        }
        MethodRef m = inv.method();
        if (m.name().endsWith("$default")) throw LqlMessage.PROC_KOTLIN_DEFAULT.exceptionAt(ref.describe(), m.name());
        Method stub = null;
        for (Method x : procs.getMethods()) {
            if (x.getName().equals(m.name()) && Type.getMethodDescriptor(x).equals(m.descriptor())) stub = x;
        }
        if (stub == null || !stub.isAnnotationPresent(SqlProcedure.class)) {
            throw LqlMessage.PROC_NOT_ANNOTATED.exceptionAt(ref.describe(), procs.getName() + "." + m.name());
        }
        SqlProcedure a = stub.getAnnotation(SqlProcedure.class);
        String name = a.value().isBlank() ? SqlFunctions.upperSnake(stub.getName()) : a.value().trim();
        return new ProcCall(stub, blankToNull(a.schema()), blankToNull(a.pkg()), name, hostArgs(inv.args(), ref));
    }

    /**
     * Reads a function call lambda {@code () -> OrderFns.discount(price)}.
     *
     * @param ref the lambda
     * @return the call
     * @throws org.lambdaql.LambdaQLException if the lambda is not one {@code @SqlFunction} call
     */
    public static FnCall readFunction(LambdaRef ref) {
        Node node = Decoder.decodeValue(ref, BytecodeLoader.load(ref));
        if (!(node instanceof Node.Value v) || !(unwrap(v.value()) instanceof Expr.Invoke inv)) {
            throw LqlMessage.FUNCTION_CALL_SHAPE.exceptionAt(ref.describe());
        }
        SqlFunctions.Info fn = SqlFunctions.find(inv.method(), ref.classLoader(), ref.describe())
                .orElseThrow(() -> LqlMessage.FUNCTION_CALL_SHAPE.exceptionAt(ref.describe()));
        return new FnCall(fn, hostArgs(inv.args(), ref));
    }

    private static List<Expr> hostArgs(List<Expr> args, LambdaRef ref) {
        for (Expr arg : args) {
            if (Normalizer.usesEntity(arg)) throw LqlMessage.PROC_ARG_NOT_VALUE.exceptionAt(ref.describe(), arg);
        }
        return args;
    }

    private static Expr unwrap(Expr e) {
        while (true) {
            if (e instanceof Expr.Convert c) e = c.operand();
            else if (e instanceof Expr.Invoke i && i.method().isStatic() && i.method().name().equals("valueOf")
                    && BOXES.contains(i.method().owner()) && i.args().size() == 1) e = i.args().get(0);
            else return e;
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
