package org.lambdaql.query;

import java.io.Serializable;

/**
 * An assignment lambda whose values may use subqueries (ROADMAP D49):
 * {@code (o, sub) -> o.setTotal(sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).sum(i -> i.getPrice()))}.
 * The second parameter starts a subquery, the same as in {@code where((o, sub) -> ...)}.
 * <p>
 * The assignments follow {@link Assign}: setter calls, or {@link org.lambdaql.Dml#value Dml.value(column, value)} for
 * entities without setters. A subquery value gives NULL when it finds no row (an aggregate other than {@code count}).
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java">DmlQueryTest</a>):
 * {@snippet class="org.lambdaql.query.DmlQueryTest" region="update-sub"}
 *
 * @param <T> the entity type
 */
@FunctionalInterface
public interface AssignSub<T> extends Serializable {
    /**
     * The assignments. lambdaQL reads this method's bytecode; it never calls it.
     *
     * @param entity the entity
     * @param sub    starts a subquery
     */
    void set(T entity, Sub sub);
}
