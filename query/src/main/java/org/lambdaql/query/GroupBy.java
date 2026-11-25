package org.lambdaql.query;

import java.io.Serializable;

/**
 * A group key lambda: one key {@code o -> o.getCustomer().getId()}, or many keys
 * {@code o -> List.of(o.getCustomer().getId(), o.getStatus())} (Kotlin {@code listOf(...)}, {@code a to b}) (ROADMAP D27).
 *
 * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java">ProjectionTest</a>):
 * {@snippet class="org.lambdaql.query.ProjectionTest" region="group-many"}
 *
 * @param <T> the entity type
 */
@FunctionalInterface
public interface GroupBy<T> extends Serializable {
    /**
     * The key or keys. lambdaQL reads this method's bytecode; it never calls it.
     *
     * @param entity the entity
     * @return the key, or a list of keys
     */
    Object key(T entity);
}
