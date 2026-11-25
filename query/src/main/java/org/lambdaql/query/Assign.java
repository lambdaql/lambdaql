package org.lambdaql.query;

import java.io.Serializable;

/**
 * An assignment lambda with setters: {@code o -> { o.setStatus(2); o.setPrice(o.getPrice() * 1.1); }}
 * (Kotlin: {@code { it.status = 2 }}). lambdaQL reads the setter calls; the lambda is never run (ROADMAP D28).
 * <p>
 * Only setter calls on the entity, its embedded values, or the id of an association ({@code o.getCustomer().setId(x)})
 * are allowed (rule U1). A value means the value <b>before</b> the change, so a value must not read a column that an
 * earlier setter in the same statement wrote (U3).
 *
 * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java">DmlQueryTest</a>):
 * {@snippet class="org.lambdaql.query.DmlQueryTest" region="update"}
 *
 * @param <T> the entity type
 */
@FunctionalInterface
public interface Assign<T> extends Serializable {
    /**
     * The assignments. lambdaQL reads this method's bytecode; it never calls it.
     *
     * @param entity the entity
     */
    void set(T entity);
}
