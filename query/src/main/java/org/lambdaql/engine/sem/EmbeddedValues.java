package org.lambdaql.engine.sem;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Reads one property of a captured value object, when a value object comparison is written column by column
 * (ROADMAP D48, B6): {@code o.getAddress().equals(addr)} becomes {@code city = :p1 and zip = :p2}, and the values
 * come from {@code addr} when the query runs. A null value object gives null for every property, so each part is
 * compared with NULL (the SQL meaning). Not public API; called by the value evaluator.
 */
public final class EmbeddedValues {

    private EmbeddedValues() {
    }

    /**
     * Returns one property of a value object.
     *
     * @param owner the value object, or null
     * @param name  the property name
     * @return the property value, or null when {@code owner} is null
     */
    public static Object property(Object owner, String name) {
        if (owner == null) return null;
        String cap = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        for (Class<?> c = owner.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (String getter : new String[]{"get" + cap, "is" + cap, name}) {
                try {
                    Method m = c.getDeclaredMethod(getter);
                    m.trySetAccessible();
                    return m.invoke(owner);
                } catch (NoSuchMethodException e) {
                    // try the next form
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(org.lambdaql.i18n.LqlMessage.INTERNAL_ERROR.format(e), e);
                }
            }
            try {
                Field f = c.getDeclaredField(name);
                f.trySetAccessible();
                return f.get(owner);
            } catch (NoSuchFieldException e) {
                // look in the superclass
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(org.lambdaql.i18n.LqlMessage.INTERNAL_ERROR.format(e), e);
            }
        }
        throw new IllegalStateException(org.lambdaql.i18n.LqlMessage.PROPERTY_NOT_FOUND.format(owner.getClass().getSimpleName() + "." + name));
    }
}
