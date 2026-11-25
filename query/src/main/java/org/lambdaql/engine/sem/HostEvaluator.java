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
 * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/sem/HostEvaluatorTest.java">HostEvaluatorTest</a>):
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
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/sem/HostEvaluatorTest.java">HostEvaluatorTest</a>):
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
            case Expr.Case c -> evalCase(c, captured);
            case Expr.NewObject n -> construct(n, captured);
            case Expr.SafeNav n -> eval(n.value(), captured);
            case Expr.ArrayLength a -> java.lang.reflect.Array.getLength(eval(a.array(), captured));
            case Expr.Choose c -> holds(c.cond(), captured) ? eval(c.whenTrue(), captured) : eval(c.whenFalse(), captured);
            default -> throw LqlMessage.HOST_NOT_EVALUABLE.exception(e);
        };
    }

    private Object evalCase(Expr.Case c, Object[] captured) {
        for (int i = 0; i < c.conds().size(); i++) {
            if (holds(c.conds().get(i), captured)) return eval(c.values().get(i), captured);
        }
        return eval(c.otherwise(), captured);
    }

    /**
     * Evaluates the condition of a value ternary of captured values (ROADMAP D61), with Java's meaning.
     *
     * <p>Example: for {@code flag ? 1 : 2}, the condition {@code flag} is true when the captured boolean is true.
     *
     * @param p        the condition (no entity value in it)
     * @param captured the captured values
     * @return the result
     * @throws NullPointerException when Java would throw it (a null compared with {@code <}, or unboxed)
     */
    boolean holds(org.lambdaql.engine.ir.Pred p, Object[] captured) {
        return switch (p) {
            case org.lambdaql.engine.ir.Pred.Bool b -> b.value();
            case org.lambdaql.engine.ir.Pred.Not n -> !holds(n.operand(), captured);
            case org.lambdaql.engine.ir.Pred.And a -> a.items().stream().allMatch(x -> holds(x, captured));
            case org.lambdaql.engine.ir.Pred.Or o -> o.items().stream().anyMatch(x -> holds(x, captured));
            case org.lambdaql.engine.ir.Pred.IsNull n -> eval(n.operand(), captured) == null;
            case org.lambdaql.engine.ir.Pred.Truth t -> {
                Object v = eval(t.operand(), captured);
                if (v == null) throw new NullPointerException(t.toString());
                yield v instanceof Boolean b ? b : ((Number) v).intValue() != 0;
            }
            case org.lambdaql.engine.ir.Pred.Cmp c -> compare(c.op(), eval(c.left(), captured), eval(c.right(), captured), c);
            default -> throw LqlMessage.HOST_NOT_EVALUABLE.exception(p);
        };
    }

    private static boolean compare(org.lambdaql.engine.ir.CmpOp op, Object a, Object b, Object where) {
        if ((a instanceof Number || a instanceof Character) && (b instanceof Number || b instanceof Character)) {
            int r = decimal(a instanceof Character ch ? (int) ch : (Number) a).compareTo(decimal(b instanceof Character ch ? (int) ch : (Number) b));
            return switch (op) {
                case EQ -> r == 0;
                case NE -> r != 0;
                case LT -> r < 0;
                case LE -> r <= 0;
                case GT -> r > 0;
                case GE -> r >= 0;
            };
        }
        return switch (op) {
            case EQ -> java.util.Objects.equals(a, b);
            case NE -> !java.util.Objects.equals(a, b);
            default -> {
                if (a == null || b == null) throw new NullPointerException(String.valueOf(where));
                @SuppressWarnings("unchecked") int r = ((Comparable<Object>) a).compareTo(b);
                yield switch (op) {
                    case LT -> r < 0;
                    case LE -> r <= 0;
                    case GT -> r > 0;
                    default -> r >= 0;
                };
            }
        };
    }

    /**
     * Converts a number to the wanted wrapper type, so Hibernate gets the type it expects.
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/sem/HostEvaluatorTest.java">HostEvaluatorTest</a>):
     * {@snippet class="org.lambdaql.engine.sem.HostEvaluatorTest" region="convert"}
     *
     * @param value  the value (may be null)
     * @param target the wanted type, for example {@code Short.class}
     * @return the converted value, or {@code value} unchanged if no conversion is needed
     */
    public static Object convert(Object value, Class<?> target) {
        if (!(value instanceof Number n) || target == null || target.isInstance(value)) return value;
        // never cut a value: 2.5 or 1e20 has no exact whole-number form, so it stays as it is (the renderer rewrites
        // a comparison with such a value, and Hibernate rejects a binding instead of changing it)
        if (INTEGRAL.contains(target) && exactWhole(n, target) == null) return value;
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

    private static final java.util.Set<Class<?>> INTEGRAL = java.util.Set.of(Byte.class, Short.class, Integer.class,
            Long.class, BigInteger.class);

    /**
     * Checks if a number is a whole number in the range of an integral type.
     *
     * @param n      a number
     * @param target an integral wrapper type, or {@code BigInteger}
     * @return the value as a {@code BigDecimal} when it fits exactly, else null
     */
    public static BigDecimal exactWhole(Number n, Class<?> target) {
        BigDecimal bd = decimal(n);
        if (bd == null) return null;
        if (bd.signum() != 0 && bd.stripTrailingZeros().scale() > 0) return null;
        BigDecimal[] range = range(target);
        if (range != null && (bd.compareTo(range[0]) < 0 || bd.compareTo(range[1]) > 0)) return null;
        return bd;
    }

    /** The exact decimal value of a number; null for NaN and the infinities. */
    public static BigDecimal decimal(Number n) {
        if (n instanceof BigDecimal b) return b;
        if (n instanceof BigInteger b) return new BigDecimal(b);
        if (n instanceof Double || n instanceof Float) {
            double d = n.doubleValue();
            return Double.isFinite(d) ? BigDecimal.valueOf(d) : null;
        }
        return BigDecimal.valueOf(n.longValue());
    }

    /**
     * The smallest and largest value of an integral type.
     *
     * @param target an integral wrapper type
     * @return {@code [min, max]}, or null for {@code BigInteger} (no limit) and other types
     */
    public static BigDecimal[] range(Class<?> target) {
        if (target == Byte.class) return new BigDecimal[]{BigDecimal.valueOf(Byte.MIN_VALUE), BigDecimal.valueOf(Byte.MAX_VALUE)};
        if (target == Short.class) return new BigDecimal[]{BigDecimal.valueOf(Short.MIN_VALUE), BigDecimal.valueOf(Short.MAX_VALUE)};
        if (target == Integer.class) return new BigDecimal[]{BigDecimal.valueOf(Integer.MIN_VALUE), BigDecimal.valueOf(Integer.MAX_VALUE)};
        if (target == Long.class) return new BigDecimal[]{BigDecimal.valueOf(Long.MIN_VALUE), BigDecimal.valueOf(Long.MAX_VALUE)};
        return null;
    }

    /**
     * Checks if a type is a whole-number type that a fraction would be cut for.
     *
     * @param type a wrapper type
     * @return true for Byte, Short, Integer, Long and BigInteger
     */
    public static boolean integral(Class<?> type) {
        return type != null && INTEGRAL.contains(type);
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
