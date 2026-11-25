package org.lambdaql.query;

import java.io.Serializable;

/**
 * An assignment lambda for INSERT ... SELECT: {@code (a, o) -> { a.setId(o.getId()); a.setPrice(o.getPrice()); }}.
 * The first argument is the new row, the second the source row.
 *
 * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java">DmlQueryTest</a>):
 * {@snippet class="org.lambdaql.query.DmlQueryTest" region="insert-select"}
 *
 * @param <T> the target entity type
 * @param <S> the source entity type
 */
@FunctionalInterface
public interface AssignFrom<T, S> extends Serializable {
    /**
     * The assignments. lambdaQL reads this method's bytecode; it never calls it.
     *
     * @param target the new row
     * @param source the source row
     */
    void set(T target, S source);
}
