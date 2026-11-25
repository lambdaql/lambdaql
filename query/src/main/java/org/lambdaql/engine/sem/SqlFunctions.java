package org.lambdaql.engine.sem;

import org.lambdaql.SqlFunction;
import org.lambdaql.engine.ir.Expr;
import org.lambdaql.engine.ir.MethodRef;
import org.lambdaql.engine.load.BytecodeLoader;
import org.lambdaql.i18n.LqlMessage;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Finds the database functions declared with {@link SqlFunction} and checks their rules (ROADMAP M11, D31, FN1-FN10).
 *
 * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java">FunctionTest</a>):
 * {@snippet class="org.lambdaql.query.FunctionTest" region="where"}
 */
public final class SqlFunctions {

    /**
     * A database function.
     *
     * @param key    the name used in the query (FN1)
     * @param sql    the SQL name; the same as {@code key} unless {@code @SqlFunction(sql = ...)} is set
     * @param asType the HQL type after {@code as}
     * @param method the declared method
     */
    public record Info(String key, String sql, String asType, Method method) {
        /**
         * Checks if the function has its own SQL name, so it must be registered.
         *
         * @return {@code true} when {@code sql} is not {@code key}
         */
        public boolean needsRegistration() {
            return !sql.equals(key);
        }
    }

    private static final Map<Class<?>, String> AS_TYPES = Map.of(
            Double.class, "Double", Integer.class, "Integer", Long.class, "Long",
            java.math.BigDecimal.class, "BigDecimal", String.class, "String",
            java.time.LocalDate.class, "LocalDate", java.time.LocalDateTime.class, "LocalDateTime", Boolean.class, "Boolean");

    private static final Map<String, Optional<Info>> CACHE = new ConcurrentHashMap<>();
    private static final java.util.Set<String> REGISTERED = ConcurrentHashMap.newKeySet();

    private SqlFunctions() {
    }

    /**
     * Finds the database function a call goes to.
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java">FunctionTest</a>):
     * {@snippet class="org.lambdaql.query.FunctionTest" region="where"}
     *
     * @param m      the called method
     * @param loader the class loader of the lambda
     * @param where  the lambda, for error messages
     * @return the function, or empty when the method is not a {@code @SqlFunction}
     * @throws org.lambdaql.LambdaQLException if the method breaks a rule (FN2, FN3, FN9, return type)
     */
    public static Optional<Info> find(MethodRef m, ClassLoader loader, String where) {
        String cacheKey = System.identityHashCode(loader) + ":" + m.owner() + "." + m.name() + m.descriptor();
        Optional<Info> hit = CACHE.get(cacheKey);
        if (hit != null) return hit;
        Optional<Info> found = lookup(m, loader, where);
        CACHE.put(cacheKey, found);
        return found;
    }

    /**
     * Reads one declared method (used when functions are registered at start-up).
     *
     * @param method a method with {@code @SqlFunction}
     * @return the function
     * @throws org.lambdaql.LambdaQLException if the method breaks a rule
     */
    public static Info describe(Method method) {
        return check(method, method.getDeclaringClass().getName() + "." + method.getName());
    }

    private static Optional<Info> lookup(MethodRef m, ClassLoader loader, String where) {
        Class<?> owner;
        try {
            owner = Class.forName(m.owner().replace('/', '.'), false, loader);
        } catch (ClassNotFoundException | LinkageError e) {
            return Optional.empty();
        }
        if (m.name().endsWith("$default")) {
            String base = m.name().substring(0, m.name().length() - "$default".length());
            for (Method x : owner.getDeclaredMethods()) {
                if (x.getName().equals(base) && x.isAnnotationPresent(SqlFunction.class)) {
                    throw LqlMessage.FUNCTION_KOTLIN_DEFAULT.exceptionAt(where, owner.getName() + "." + base);
                }
            }
            return Optional.empty();
        }
        for (Method x : owner.getDeclaredMethods()) {
            if (x.getName().equals(m.name()) && Type.getMethodDescriptor(x).equals(m.descriptor())) {
                if (!x.isAnnotationPresent(SqlFunction.class)) return Optional.empty();
                return Optional.of(check(x, where));
            }
        }
        return Optional.empty();
    }

    private static Info check(Method x, String where) {
        String name = x.getDeclaringClass().getName() + "." + x.getName();
        if (x.isVarArgs()) throw LqlMessage.FUNCTION_VARARG.exceptionAt(where, name);
        SqlFunction a = x.getAnnotation(SqlFunction.class);
        String key = a.value().isBlank() ? upperSnake(x.getName()) : a.value().trim();
        String sql = a.sql().isBlank() ? key : a.sql().trim();
        String asType = AS_TYPES.get(box(x.getReturnType()));
        if (asType == null) throw LqlMessage.FUNCTION_RETURN_TYPE.exceptionAt(where, name, x.getGenericReturnType().getTypeName());
        // FN3: overloads with the same name go to the same database function
        for (Method o : x.getDeclaringClass().getDeclaredMethods()) {
            if (o.getName().equals(x.getName()) && o.isAnnotationPresent(SqlFunction.class)) {
                SqlFunction b = o.getAnnotation(SqlFunction.class);
                String k2 = b.value().isBlank() ? upperSnake(o.getName()) : b.value().trim();
                if (!k2.equals(key)) throw LqlMessage.FUNCTION_OVERLOAD_MISMATCH.exceptionAt(where, name);
            }
        }
        // FN2: the body only throws (Java runs it otherwise and gets a value that the database might not give)
        if (!onlyThrows(x)) throw LqlMessage.FUNCTION_NOT_GUARD.exceptionAt(where, name);
        return new Info(key, sql, asType, x);
    }

    private static boolean onlyThrows(Method x) {
        if (Modifier.isAbstract(x.getModifiers())) return false;
        Class<?> owner = x.getDeclaringClass();
        MethodNode node = BytecodeLoader.load(owner.getClassLoader(), Type.getInternalName(owner), x.getName(), Type.getMethodDescriptor(x));
        boolean athrow = false;
        for (AbstractInsnNode n : node.instructions) {
            int op = n.getOpcode();
            if (op == Opcodes.ATHROW) athrow = true;
            // a Kotlin @JvmStatic bridge in the interface calls the companion method: check that one instead
            if (n instanceof MethodInsnNode call && call.owner.endsWith("$Companion") && call.name.equals(x.getName())) {
                for (Method c : companion(owner, call.owner)) {
                    if (c.getName().equals(call.name) && Type.getMethodDescriptor(c).equals(call.desc)) return onlyThrows(c);
                }
            }
            if (op >= Opcodes.IRETURN && op <= Opcodes.ARETURN) return false;
        }
        return athrow;
    }

    private static Method[] companion(Class<?> owner, String internalName) {
        try {
            return Class.forName(internalName.replace('/', '.'), false, owner.getClassLoader()).getDeclaredMethods();
        } catch (ClassNotFoundException e) {
            return new Method[0];
        }
    }

    private static Class<?> box(Class<?> c) {
        if (!c.isPrimitive()) return c;
        if (c == double.class) return Double.class;
        if (c == int.class) return Integer.class;
        if (c == long.class) return Long.class;
        if (c == boolean.class) return Boolean.class;
        return c;
    }

    /**
     * Remembers that a function was registered in Hibernate by lambdaQL (setting {@code lambdaql.functions.classes}).
     *
     * @param key the function name used in the query
     */
    public static void markRegistered(String key) {
        REGISTERED.add(key.toUpperCase(Locale.ROOT));
    }

    /**
     * Checks if lambdaQL registered a function in Hibernate.
     *
     * @param key the function name used in the query
     * @return {@code true} if it was registered by lambdaQL
     */
    public static boolean isRegistered(String key) {
        return REGISTERED.contains(key.toUpperCase(Locale.ROOT));
    }

    /**
     * Collects the database function calls in an analysis result (conditions, values, sort keys, assignments).
     *
     * @param node an analysis result: a {@code Pred}, an {@code Expr}, a record of them or a list
     * @return the function calls
     */
    public static java.util.List<Expr.Func> calls(Object node) {
        java.util.List<Expr.Func> out = new java.util.ArrayList<>();
        collect(node, out);
        return out;
    }

    private static void collect(Object node, java.util.List<Expr.Func> out) {
        if (node == null) return;
        if (node instanceof Expr.Func f) out.add(f);
        if (node instanceof java.util.Collection<?> c) {
            for (Object x : c) collect(x, out);
            return;
        }
        if (node instanceof Record r) {
            for (java.lang.reflect.RecordComponent rc : r.getClass().getRecordComponents()) {
                if (rc.getType().isPrimitive()) continue;
                try {
                    collect(rc.getAccessor().invoke(r), out);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(LqlMessage.INTERNAL_ERROR.format(e), e);
                }
            }
        }
    }

    /**
     * Turns a method name into UPPER_SNAKE case (FN1): {@code isVip} becomes {@code IS_VIP}.
     *
     * @param name a method name
     * @return the name in UPPER_SNAKE case
     */
    public static String upperSnake(String name) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c) && i > 0 && !Character.isUpperCase(name.charAt(i - 1))) s.append('_');
            s.append(c);
        }
        return s.toString().toUpperCase(Locale.ROOT);
    }
}
