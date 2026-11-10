package org.lambdaql.engine.sem;

import java.util.Optional;

/**
 * Information about entity properties that the semantic step needs.
 * <p>
 * The main version is {@link JpaEntityModel} (it reads the JPA Metamodel).
 * Tests can use a small fake version.
 *
 * <p>Example:
 * <pre>{@code
 * EntityModel model = new JpaEntityModel(emf.getMetamodel());
 * model.property(Order.class, "customer").get().kind();   // Kind.ASSOCIATION
 * }</pre>
 */
public interface EntityModel {

    /**
     * What kind of property it is.
     */
    enum Kind {
        /** A simple value column (number, string, date, enum, boolean ...). */
        BASIC,
        /** An {@code @Embedded} value object; its fields are in the same table. */
        EMBEDDED,
        /** A to-one association ({@code @ManyToOne}, {@code @OneToOne}); it needs a join. */
        ASSOCIATION,
        /** A collection ({@code @OneToMany}, {@code @ManyToMany}, {@code @ElementCollection}); not supported in paths. */
        COLLECTION
    }

    /**
     * One property of an entity or embeddable.
     *
     * @param name     property name in HQL, for example {@code customer}
     * @param javaType Java type of the value (boxed for primitives); for associations the target entity class
     * @param kind     the kind of property
     * @param id       {@code true} if this is the {@code @Id}
     * @param nonNull  {@code true} if the column can never be NULL (id, primitive type, {@code optional = false})
     *
     * <p>Example: {@code Property("price", Double.class, BASIC, false, true)} for {@code private double price;}
     */
    record Property(String name, Class<?> javaType, Kind kind, boolean id, boolean nonNull) {
    }

    /**
     * Checks if a class is a managed entity.
     *
     * @param type a class
     * @return {@code true} if it is an {@code @Entity}
     *
     * <p>Example: {@code model.isEntity(Order.class)} is {@code true}; {@code model.isEntity(String.class)} is {@code false}.
     */
    boolean isEntity(Class<?> type);

    /**
     * Returns the entity name used in HQL.
     *
     * @param type an entity class
     * @return the entity name, for example {@code Order}
     * @throws IllegalArgumentException if the class is not an entity
     */
    String entityName(Class<?> type);

    /**
     * Finds a property of an entity or embeddable class.
     *
     * @param owner the entity or embeddable class
     * @param name  property name, for example {@code price}
     * @return the property, or empty if there is no such property
     *
     * <p>Example: {@code model.property(Order.class, "price")}
     */
    Optional<Property> property(Class<?> owner, String name);
}
