package org.lambdaql.query;

import java.io.Serializable;

/**
 * A sort lambda with one key and no builder: {@code o -> o.getPrice()}.
 *
 * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java">SortQueryTest</a>):
 * {@snippet class="org.lambdaql.query.SortQueryTest" region="sort-key"}
 *
 * @param <T> the entity type
 */
@FunctionalInterface
public interface SortKey<T> extends Serializable {
    /**
     * Returns the key. lambdaQL reads this method's bytecode; it never calls it.
     *
     * @param entity the entity
     * @return the key
     */
    Comparable<?> key(T entity);
}
