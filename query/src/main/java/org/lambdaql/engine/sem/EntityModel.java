package org.lambdaql.engine.sem;

import java.util.List;
import java.util.Optional;

/**
 * Information about entity properties that the semantic step needs.
 * <p>
 * The main version is {@link JpaEntityModel} (it reads the JPA Metamodel).
 * Tests can use a small fake version.
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/sem/JpaEntityModelTest.java">JpaEntityModelTest</a>):
 * {@snippet class="org.lambdaql.engine.sem.JpaEntityModelTest" region="entity-model"}
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
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/sem/JpaEntityModelTest.java">JpaEntityModelTest</a>):
     * {@snippet class="org.lambdaql.engine.sem.JpaEntityModelTest" region="entity-model"}
     * {@snippet class="org.lambdaql.engine.sem.JpaEntityModelTest" region="entity-model-id"}
     *
     * @param name     property name in HQL, for example {@code customer}
     * @param javaType Java type of the value (boxed for primitives); for associations the target entity class
     * @param kind     the kind of property
     * @param id       {@code true} if this is the {@code @Id}
     * @param nonNull  {@code true} if the column can never be NULL (id, primitive type, {@code optional = false})
     */
    record Property(String name, Class<?> javaType, Kind kind, boolean id, boolean nonNull) {
    }

    /**
     * Checks if a class is a managed entity.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/sem/JpaEntityModelTest.java">JpaEntityModelTest</a>):
     * {@snippet class="org.lambdaql.engine.sem.JpaEntityModelTest" region="entity-model"}
     *
     * @param type a class
     * @return {@code true} if it is an {@code @Entity}
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
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/sem/JpaEntityModelTest.java">JpaEntityModelTest</a>):
     * {@snippet class="org.lambdaql.engine.sem.JpaEntityModelTest" region="entity-model"}
     *
     * @param owner the entity or embeddable class
     * @param name  property name, for example {@code price}
     * @return the property, or empty if there is no such property
     */
    Optional<Property> property(Class<?> owner, String name);

    /**
     * Returns the property names of an embeddable class, in name order. Used to write a value object comparison
     * column by column (ROADMAP D48, B6): {@code o.getAddress().equals(addr)} becomes
     * {@code address.city = :p1 and address.zip = :p2}.
     *
     * <p>Example: for {@code @Embeddable class Address { String city; String zip; }} this returns
     * {@code [city, zip]}.
     *
     * @param embeddable an {@code @Embeddable} class
     * @return the property names; empty if the class is not known
     */
    default List<String> attributeNames(Class<?> embeddable) {
        return List.of();
    }

    /**
     * Returns the name of the {@code @Id} property of an entity.
     * Used to add a tie-breaker key when a sorted query is paged (ROADMAP S9).
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/sem/JpaEntityModelTest.java">JpaEntityModelTest</a>):
     * {@snippet class="org.lambdaql.engine.sem.JpaEntityModelTest" region="entity-model-id"}
     *
     * @param entity an entity class
     * @return the id property name, for example {@code id}; empty for a composite id ({@code @IdClass})
     */
    Optional<String> idName(Class<?> entity);

    /**
     * Checks if an entity has a {@code @Version} property. Then UPDATE is rendered as {@code update versioned},
     * so Hibernate also increases the version (ROADMAP M10).
     *
     * @param entity an entity class
     * @return {@code true} if the entity has a version property
     */
    boolean versioned(Class<?> entity);
}
