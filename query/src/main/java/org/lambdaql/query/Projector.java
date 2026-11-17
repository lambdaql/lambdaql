package org.lambdaql.query;

import org.lambdaql.i18n.LqlMessage;
import org.lambdaql.engine.sem.HostEvaluator;
import org.lambdaql.engine.sem.SelectReader;
import org.objectweb.asm.Type;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

/** Builds result objects from selected columns: the constructor is called in Java (rule J2). Not public API. */
final class Projector {

    private final SelectReader.Projection projection;
    private final Constructor<?> constructor;
    private final Class<?>[] types;

    Projector(SelectReader.Projection projection, ClassLoader loader) {
        this.projection = projection;
        if (!projection.constructs()) {
            this.constructor = null;
            this.types = null;
            return;
        }
        try {
            Class<?> type = Class.forName(projection.className().replace('/', '.'), false, loader);
            Type[] args = Type.getArgumentTypes(projection.constructor());
            types = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) types[i] = classOf(args[i], loader);
            constructor = type.getDeclaredConstructor(types);
            constructor.trySetAccessible();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(LqlMessage.RESULT_CONSTRUCTOR_NOT_FOUND.format(projection.className() + projection.constructor()), e);
        }
    }

    List<Object> build(List<?> rows) {
        List<Object> out = new ArrayList<>(rows.size());
        for (Object row : rows) out.add(build(row));
        return out;
    }

    private Object build(Object row) {
        if (constructor == null) return row;
        Object[] values = row instanceof Object[] a ? a : new Object[]{row};
        Object[] args = new Object[values.length];
        for (int i = 0; i < values.length; i++) {
            Class<?> t = types[i];
            if (values[i] == null && t.isPrimitive()) {
                throw new IllegalStateException(LqlMessage.NULL_INTO_PRIMITIVE.format(i + 1, constructor));
            }
            args[i] = HostEvaluator.convert(values[i], box(t));
        }
        try {
            return constructor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(LqlMessage.RESULT_OBJECT_FAILED.format(constructor), e);
        }
    }

    private static Class<?> classOf(Type t, ClassLoader loader) throws ClassNotFoundException {
        return switch (t.getSort()) {
            case Type.BOOLEAN -> boolean.class;
            case Type.CHAR -> char.class;
            case Type.BYTE -> byte.class;
            case Type.SHORT -> short.class;
            case Type.INT -> int.class;
            case Type.FLOAT -> float.class;
            case Type.LONG -> long.class;
            case Type.DOUBLE -> double.class;
            case Type.ARRAY -> Class.forName(t.getDescriptor().replace('/', '.'), false, loader);
            default -> Class.forName(t.getClassName(), false, loader);
        };
    }

    private static Class<?> box(Class<?> c) {
        if (!c.isPrimitive()) return c;
        if (c == int.class) return Integer.class;
        if (c == long.class) return Long.class;
        if (c == double.class) return Double.class;
        if (c == float.class) return Float.class;
        if (c == short.class) return Short.class;
        if (c == byte.class) return Byte.class;
        if (c == boolean.class) return Boolean.class;
        return Character.class;
    }
}
