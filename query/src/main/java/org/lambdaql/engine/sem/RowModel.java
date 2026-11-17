package org.lambdaql.engine.sem;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An entity model that also knows the row types of derived tables and CTEs (ROADMAP M8-2, J5): Java records and
 * Kotlin data classes. Their components are columns, so {@code r.total()} (Kotlin {@code r.total}) reads the column
 * {@code total} of the derived table. Everything else goes to the JPA model.
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DerivedQueryTest.java">DerivedQueryTest</a>):
 * {@snippet class="org.lambdaql.query.DerivedQueryTest" region="derived"}
 */
public final class RowModel implements EntityModel {

    private final EntityModel jpa;
    private final Set<Class<?>> rows = ConcurrentHashMap.newKeySet();

    /**
     * Wraps the JPA model.
     *
     * @param jpa the JPA entity model
     */
    public RowModel(EntityModel jpa) {
        this.jpa = jpa;
    }

    /**
     * Registers a row type of a derived table or CTE.
     *
     * @param row a record or Kotlin data class
     */
    public void register(Class<?> row) {
        rows.add(row);
    }

    @Override
    public boolean isEntity(Class<?> type) {
        return rows.contains(type) || jpa.isEntity(type);
    }

    @Override
    public String entityName(Class<?> type) {
        return rows.contains(type) ? type.getSimpleName() : jpa.entityName(type);
    }

    @Override
    public Optional<Property> property(Class<?> owner, String name) {
        if (!rows.contains(owner)) return jpa.property(owner, name);
        if (owner.isRecord()) {
            for (RecordComponent c : owner.getRecordComponents()) {
                if (c.getName().equals(name)) return Optional.of(new Property(name, box(c.getType()), Kind.BASIC, false, false));
            }
            return Optional.empty();
        }
        for (Field f : owner.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) && f.getName().equals(name)) {
                return Optional.of(new Property(name, box(f.getType()), Kind.BASIC, false, false));
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<String> idName(Class<?> entity) {
        return rows.contains(entity) ? Optional.empty() : jpa.idName(entity);
    }

    @Override
    public boolean versioned(Class<?> entity) {
        return !rows.contains(entity) && jpa.versioned(entity);
    }

    private static Class<?> box(Class<?> c) {
        if (!c.isPrimitive()) return c;
        if (c == int.class) return Integer.class;
        if (c == long.class) return Long.class;
        if (c == double.class) return Double.class;
        if (c == boolean.class) return Boolean.class;
        if (c == float.class) return Float.class;
        if (c == short.class) return Short.class;
        return c;
    }
}
