package org.lambdaql.query;

import java.io.Serializable;

/**
 * The conflict key of an upsert: {@code o -> o.getId()}, or a getter reference {@code Order::getCode}.
 * It must be a column with a primary key or unique constraint.
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java">DmlQueryTest</a>):
 * {@snippet class="org.lambdaql.query.DmlQueryTest" region="on-conflict"}
 *
 * @param <T> the entity type
 */
@FunctionalInterface
public interface ConflictKey<T> extends Serializable {
    /**
     * The key column. lambdaQL reads this method's bytecode; it never calls it.
     *
     * @param entity the entity
     * @return the key
     */
    Object key(T entity);
}
