package org.lambdaql.query;

import java.io.Serializable;

/**
 * A sort lambda: one key ({@code o -> o.getPrice()}, ascending) or keys made with {@link By}
 * ({@code o -> desc(o.getPrice()).asc(o.getId())}). A method reference to a getter ({@code Order::getPrice}) is one key.
 * Both return {@code Comparable<?>}, so one {@code orderBy} takes both forms; lambdaQL tells them apart when it reads
 * the lambda. An entity is not {@code Comparable}, so {@code o -> o.getCustomer()} does not compile.
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
     * @return one key, or the keys made with {@link By}
     */
    Comparable<?> sort(T entity);
}
