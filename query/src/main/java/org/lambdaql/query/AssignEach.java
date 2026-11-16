package org.lambdaql.query;

import java.io.Serializable;

/**
 * An assignment lambda for one element of a list: {@code (o, line) -> { o.setPrice(line.price()); }}.
 * It makes one inserted row per element.
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java">DmlQueryTest</a>):
 * {@snippet class="org.lambdaql.query.DmlQueryTest" region="insert-list"}
 *
 * @param <T> the entity type
 * @param <L> the element type
 */
@FunctionalInterface
public interface AssignEach<T, L> extends Serializable {
    /**
     * The assignments. lambdaQL reads this method's bytecode; it never calls it.
     *
     * @param entity  the new row
     * @param element the list element (its getters are read in Java)
     */
    void set(T entity, L element);
}
