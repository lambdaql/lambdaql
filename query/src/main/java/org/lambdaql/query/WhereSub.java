package org.lambdaql.query;

import java.io.Serializable;

/**
 * A condition lambda that can use subqueries: {@code (o, sub) -> sub.from(Item.class).where(...).exists()}.
 * The number of arguments tells it apart from {@link SelectQuery.Where} (Java and Kotlin).
 *
 * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java">SubqueryTest</a>):
 * {@snippet class="org.lambdaql.query.SubqueryTest" region="exists"}
 *
 * @param <T> the entity type
 */
@FunctionalInterface
public interface WhereSub<T> extends Serializable {
    /**
     * The condition. lambdaQL reads this method's bytecode; it never calls it.
     *
     * @param entity the entity
     * @param sub    the subquery factory
     * @return the condition
     */
    boolean clause(T entity, Sub sub);
}
