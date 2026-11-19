package org.lambdaql.query;

import java.io.Serializable;

/**
 * The mapping of INSERT ... SELECT whose values may use subqueries (ROADMAP D49):
 * {@code (a, o, sub) -> a.setItemCount(sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).count())}.
 * The third parameter starts a subquery; it may read the source row {@code o}, not the new row {@code a}.
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java">DmlQueryTest</a>):
 * {@snippet class="org.lambdaql.query.DmlQueryTest" region="insert-select-sub"}
 *
 * @param <T> the target type
 * @param <S> the source type
 */
@FunctionalInterface
public interface AssignFromSub<T, S> extends Serializable {
    /**
     * The assignments. lambdaQL reads this method's bytecode; it never calls it.
     *
     * @param target the new row
     * @param source the source row
     * @param sub    starts a subquery
     */
    void set(T target, S source, Sub sub);
}
