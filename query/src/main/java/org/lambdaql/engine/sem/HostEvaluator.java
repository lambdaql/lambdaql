package org.lambdaql.engine.sem;

import org.lambdaql.i18n.LqlMessage;
import org.lambdaql.LambdaAnalysisException;
import org.lambdaql.engine.ir.Expr;
import org.lambdaql.engine.ir.MethodRef;
import org.objectweb.asm.Type;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Computes a {@link Expr.Host} value in Java when the query runs.
 * <p>
 * A host expression only uses captured values, constants, fields and method calls that do not touch
 * the entity. The result is bound as a query parameter.
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/sem/HostEvaluatorTest.java">HostEvaluatorTest</a>):
 * {@snippet class="org.lambdaql.engine.sem.HostEvaluatorTest" region="eval-call"}
 */
public final class HostEvaluator {

    private final ClassLoader loader;

    /**
     * Creates an evaluator.
     *
     * @param loader class loader used to find classes named in the bytecode
     */
    public HostEvaluator(ClassLoader loader) {
        this.loader = loader;
    }

    /**
     * Computes the value of an expression.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/sem/HostEvaluatorTest.java">HostEvaluatorTest</a>):
     * {@snippet class="org.lambdaql.engine.sem.HostEvaluatorTest" region="eval-arith"}
     * {@snippet class="org.lambdaql.engine.sem.HostEvaluatorTest" region="eval-null-receiver"}
     *
     * @param e        the expression (no entity parts)
     * @param captured captured values of the lambda, in order
     * @return the value (boxed for primitives), may be {@code null}
     * @throws LambdaAnalysisException if a method or field cannot be found, or a called method throws
     */
    public Object eval(Expr e, Object[] captured) {
        return switch (e) {
            case Expr.Const c -> c.value() instanceof Type t ? classOf(t) : c.value();
            case Expr.Captured c -> captured[c.index()];
            case Expr.Host h -> convert(eval(h.expr(), captured), h.javaType());
            case Expr.GetField f -> getField(f, captured);
            case Expr.Invoke inv -> invoke(inv, captured);
            case Expr.Arith a -> arith(a, eval(a.left(), captured), eval(a.right(), captured));
            case Expr.Neg n -> neg(eval(n.operand(), captured), n.type());
            case Expr.Convert c -> c.type().getSort() == Type.OBJECT || c.type().getSort() == Type.ARRAY
                    ? eval(c.operand(), captured) : toPrimitive(eval(c.operand(), captured), c.type());
            case Expr.Coalesce c -> {
                Object first = eval(c.first(), captured);
                yield first != null ? first : eval(c.second(), captured);
            }
            case Expr.NewObject n -> construct(n, captured);
            case Expr.ArrayLength a -> java.lang.reflect.Array.getLength(eval(a.array(), captured));
            default -> throw LqlMessage.HOST_NOT_EVALUABLE.exception(e);
        };
    }

    /**
     * Converts a number to the wanted wrapper type, so Hibernate gets the type it expects.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/sem/HostEvaluatorTest.java">HostEvaluatorTest</a>):
     * {@snippet class="org.lambdaql.engine.sem.HostEvaluatorTest" region="convert"}
     *
     * @param value  the value (may be null)
     * @param target the wanted type, for example {@code Short.class}
     * @return the converted value, or {@code value} unchanged if no conversion is needed
     */
    public static Object convert(Object value, Class<?> target) {
        if (!(value instanceof Number n) || target == null || target.isInstance(value)) return value;
        if (target == Integer.class) return n.intValue();
        if (target == Long.class) return n.longValue();
        if (target == Double.class) return n.doubleValue();
        if (target == Float.class) return n.floatValue();
        if (target == Short.class) return n.shortValue();
        if (target == Byte.class) return n.byteValue();
        if (target == BigDecimal.class) return new BigDecimal(n.toString());
        if (target == BigInteger.class) return BigInteger.valueOf(n.longValue());
        return value;
    }

    // ------------------------------------------------------------------ helpers

    private Class<?> classOf(Type t) {
        return switch (t.getSort()) {
            case Type.BOOLEAN -> boolean.class;
            case Type.CHAR -> char.class;
            case Type.BYTE -> byte.class;
            case Type.SHORT -> short.class;
            case Type.INT -> int.class;
            case Type.FLOAT -> float.class;
            case Type.LONG -> long.class;
            case Type.DOUBLE -> double.class;
            case Type.VOID -> void.class;
            default -> load(t.getSort() == Type.ARRAY ? t.getDescriptor().replace('/', '.') : t.getClassName());
        };
    }

    private Class<?> load(String name) {
        try {
            return Class.forName(name, false, loader);
        } catch (ClassNotFoundException ex) {
            throw LqlMessage.CLASS_NOT_FOUND.exceptionCausedBy(null, ex, name);
        }
    }

    private Object getField(Expr.GetField f, Object[] captured) {
        Object target = f.receiver() == null ? null : eval(f.receiver(), captured);
        Class<?> owner = load(f.owner().replace('/', '.'));
        try {
            Field field = findField(owner, f.name());
            if (!Modifier.isPublic(field.getModifiers()) || !Modifier.isPublic(field.getDeclaringClass().getModifiers())) {
                field.trySetAccessible();
            }
            return field.get(target);
        } catch (ReflectiveOperationException ex) {
            throw LqlMessage.FIELD_UNREADABLE.exceptionCausedBy(null, ex, f);
        }
    }

    private static Field findField(Class<?> c, String name) throws NoSuchFieldException {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try {
                return k.getDeclaredField(name);
            } catch (NoSuchFieldException ignore) {
                // look in the super class
            }
        }
        throw new NoSuchFieldException(c.getName() + "." + name);
    }

    private Object invoke(Expr.Invoke inv, Object[] captured) {
        MethodRef m = inv.method();
        Object target = inv.receiver() == null ? null : eval(inv.receiver(), captured);
        if (target == null && !m.isStatic()) {
            throw LqlMessage.NULL_METHOD_CALL.exception(inv);
        }
        Object[] args = new Object[inv.args().size()];
        Type[] types = m.argumentTypes();
        Class<?>[] params = new Class<?>[types.length];
        for (int i = 0; i < args.length; i++) {
            args[i] = eval(inv.args().get(i), captured);
            params[i] = classOf(types[i]);
            if (params[i].isPrimitive()) args[i] = toPrimitive(args[i], types[i]);
        }
        try {
            Method method = findMethod(load(m.owner().replace('/', '.')), m.name(), params);
            if (!Modifier.isPublic(method.getModifiers()) || !Modifier.isPublic(method.getDeclaringClass().getModifiers())) {
                method.trySetAccessible();
            }
            return method.invoke(target, args);
        } catch (InvocationTargetException ex) {
            throw LqlMessage.HOST_METHOD_FAILED.exceptionCausedBy(null, ex.getCause(), inv);
        } catch (ReflectiveOperationException ex) {
            throw LqlMessage.HOST_METHOD_UNCALLABLE.exceptionCausedBy(null, ex, inv);
        }
    }

    private static Method findMethod(Class<?> c, String name, Class<?>[] params) throws NoSuchMethodException {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try {
                return k.getDeclaredMethod(name, params);
            } catch (NoSuchMethodException ignore) {
                // look in the super class
            }
        }
        return c.getMethod(name, params); // interface default methods
    }

    private Object construct(Expr.NewObject n, Object[] captured) {
        Type[] types = n.constructor().argumentTypes();
        Class<?>[] params = new Class<?>[types.length];
        Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            params[i] = classOf(types[i]);
            args[i] = eval(n.args().get(i), captured);
            if (params[i].isPrimitive()) args[i] = toPrimitive(args[i], types[i]);
        }
        try {
            Constructor<?> ctor = load(n.type().getClassName()).getDeclaredConstructor(params);
            ctor.trySetAccessible();
            return ctor.newInstance(args);
        } catch (InvocationTargetException ex) {
            throw LqlMessage.HOST_METHOD_FAILED.exceptionCausedBy(null, ex.getCause(), n);
        } catch (ReflectiveOperationException ex) {
            throw LqlMessage.HOST_OBJECT_UNCREATABLE.exceptionCausedBy(null, ex, n);
        }
    }

    private static Object arith(Expr.Arith a, Object l, Object r) {
        if (l == null || r == null) throw LqlMessage.NULL_ARITHMETIC.exception(a);
        Number x = (Number) toNumber(l), y = (Number) toNumber(r);
        return switch (a.type().getSort()) {
            case Type.LONG -> switch (a.op()) {
                case ADD -> x.longValue() + y.longValue();
                case SUB -> x.longValue() - y.longValue();
                case MUL -> x.longValue() * y.longValue();
                case DIV -> x.longValue() / y.longValue();
                case REM -> x.longValue() % y.longValue();
            };
            case Type.DOUBLE -> switch (a.op()) {
                case ADD -> x.doubleValue() + y.doubleValue();
                case SUB -> x.doubleValue() - y.doubleValue();
                case MUL -> x.doubleValue() * y.doubleValue();
                case DIV -> x.doubleValue() / y.doubleValue();
                case REM -> x.doubleValue() % y.doubleValue();
            };
            case Type.FLOAT -> switch (a.op()) {
                case ADD -> x.floatValue() + y.floatValue();
                case SUB -> x.floatValue() - y.floatValue();
                case MUL -> x.floatValue() * y.floatValue();
                case DIV -> x.floatValue() / y.floatValue();
                case REM -> x.floatValue() % y.floatValue();
            };
            default -> switch (a.op()) {
                case ADD -> x.intValue() + y.intValue();
                case SUB -> x.intValue() - y.intValue();
                case MUL -> x.intValue() * y.intValue();
                case DIV -> x.intValue() / y.intValue();
                case REM -> x.intValue() % y.intValue();
            };
        };
    }

    private static Object neg(Object v, Type t) {
        Number n = (Number) toNumber(v);
        return switch (t.getSort()) {
            case Type.LONG -> -n.longValue();
            case Type.DOUBLE -> -n.doubleValue();
            case Type.FLOAT -> -n.floatValue();
            default -> -n.intValue();
        };
    }

    private static Object toNumber(Object v) {
        if (v instanceof Character c) return (int) c;
        if (v instanceof Boolean b) return b ? 1 : 0;
        return v;
    }

    /**
     * Converts a value to the boxed form of a primitive JVM type ({@code int} constants for {@code boolean} and so on).
     *
     * @param v the value
     * @param t the primitive JVM type
     * @return the converted value
     */
    private static Object toPrimitive(Object v, Type t) {
        if (v == null) return null;
        return switch (t.getSort()) {
            case Type.BOOLEAN -> v instanceof Boolean ? v : ((Number) toNumber(v)).intValue() != 0;
            case Type.CHAR -> v instanceof Character ? v : (char) ((Number) toNumber(v)).intValue();
            case Type.BYTE -> ((Number) toNumber(v)).byteValue();
            case Type.SHORT -> ((Number) toNumber(v)).shortValue();
            case Type.INT -> ((Number) toNumber(v)).intValue();
            case Type.LONG -> ((Number) toNumber(v)).longValue();
            case Type.FLOAT -> ((Number) toNumber(v)).floatValue();
            case Type.DOUBLE -> ((Number) toNumber(v)).doubleValue();
            default -> v;
        };
    }
}
