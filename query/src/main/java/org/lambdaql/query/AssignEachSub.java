package org.lambdaql.query;

import java.io.Serializable;

/**
 * An assignment lambda for one list element whose values may use subqueries:
 * {@code (a, l, sub) -> a.setPrice(sub.from(Item.class).where(i -> i.getOrder().getId() == l.orderId()).sum(i -> i.getPrice()))}
 * (ROADMAP D49). The element's getters are read in Java; the third parameter starts a subquery, which may use the
 * element's values but not the new row.
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java">DmlQueryTest</a>):
 * {@snippet class="org.lambdaql.query.DmlQueryTest" region="insert-list-sub"}
 *
 * @param <T> the entity type
 * @param <L> the element type
 */
@FunctionalInterface
public interface AssignEachSub<T, L> extends Serializable {
    /**
     * The assignments. lambdaQL reads this method's bytecode; it never calls it.
     *
     * @param entity  the new row
     * @param element the list element
     * @param sub     starts a subquery
     */
    void set(T entity, L element, Sub sub);
}
