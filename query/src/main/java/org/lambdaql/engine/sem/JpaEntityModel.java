package org.lambdaql.engine.sem;

import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.Metamodel;
import jakarta.persistence.metamodel.SingularAttribute;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link EntityModel} that reads the JPA {@link Metamodel}. Only standard JPA API is used
 * (no Hibernate internal classes). Results are cached.
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/sem/JpaEntityModelTest.java">JpaEntityModelTest</a>):
 * {@snippet class="org.lambdaql.engine.sem.JpaEntityModelTest" region="entity-model"}
 */
public final class JpaEntityModel implements EntityModel {

    private final Metamodel metamodel;
    private final Map<String, Optional<Property>> cache = new ConcurrentHashMap<>();

    /**
     * Creates a model that reads the given metamodel.
     *
     * @param metamodel the JPA metamodel, from {@code EntityManagerFactory.getMetamodel()}
     */
    public JpaEntityModel(Metamodel metamodel) {
        this.metamodel = metamodel;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isEntity(Class<?> type) {
        try {
            metamodel.entity(type);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** {@inheritDoc} */
    @Override
    public String entityName(Class<?> type) {
        return metamodel.entity(type).getName();
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Property> property(Class<?> owner, String name) {
        return cache.computeIfAbsent(owner.getName() + "#" + name, k -> lookup(owner, name));
    }

    /** {@inheritDoc} */
    @Override
    public List<String> attributeNames(Class<?> embeddable) {
        try {
            return metamodel.managedType(embeddable).getAttributes().stream()
                    .map(Attribute::getName).sorted().toList();
        } catch (IllegalArgumentException e) {
            return List.of();
        }
    }

    /** {@inheritDoc} */
    @Override
    public Optional<String> idName(Class<?> entity) {
        EntityType<?> type = metamodel.entity(entity);
        if (!type.hasSingleIdAttribute()) return Optional.empty();
        return type.getSingularAttributes().stream()
                .filter(SingularAttribute::isId)
                .map(Attribute::getName)
                .findFirst();
    }

    /** {@inheritDoc} */
    @Override
    public boolean versioned(Class<?> entity) {
        return metamodel.entity(entity).hasVersionAttribute();
    }

    private Optional<Property> lookup(Class<?> owner, String name) {
        ManagedType<?> type;
        try {
            type = metamodel.managedType(owner);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        Attribute<?, ?> attr;
        try {
            attr = type.getAttribute(name);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        Kind kind = switch (attr.getPersistentAttributeType()) {
            case BASIC -> Kind.BASIC;
            case EMBEDDED -> Kind.EMBEDDED;
            case MANY_TO_ONE, ONE_TO_ONE -> Kind.ASSOCIATION;
            default -> Kind.COLLECTION;
        };
        boolean id = attr instanceof SingularAttribute<?, ?> s && s.isId();
        Class<?> javaType = box(attr.getJavaType());
        boolean primitive = attr.getJavaType().isPrimitive();
        boolean optional = !(attr instanceof SingularAttribute<?, ?> s2) || s2.isOptional();
        boolean nonNull = id || primitive || (!optional && kind == Kind.BASIC);
        if (kind == Kind.ASSOCIATION && type instanceof EntityType<?>) {
            nonNull = !optional;
        }
        return Optional.of(new Property(name, javaType, kind, id, nonNull));
    }

    /**
     * Returns the wrapper class for a primitive class.
     *
     * @param c a class
     * @return {@code Integer.class} for {@code int.class} and so on; other classes unchanged
     */
    static Class<?> box(Class<?> c) {
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
}
