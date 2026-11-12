package org.lambdaql.query;

import java.io.Serializable;

/**
 * A sort lambda with the builder: {@code (o, by) -> by.desc(o.getPrice()).asc(o.getId())}.
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java">SortQueryTest</a>):
 * {@snippet class="org.lambdaql.query.SortQueryTest" region="sort-basic"}
 *
 * @param <T> the entity type
 */
@FunctionalInterface
public interface SortBy<T> extends Serializable {
    /**
     * Describes the order. lambdaQL reads this method's bytecode; it never calls it.
     *
     * @param entity the entity
     * @param by     the sort builder
     * @return the builder after the last key
     */
    By sort(T entity, By by);
}
