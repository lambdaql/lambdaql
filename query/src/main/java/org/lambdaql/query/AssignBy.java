package org.lambdaql.query;

import java.io.Serializable;

/**
 * An assignment lambda with the builder: {@code (o, by) -> by.set(o.getStatus(), 2)}.
 * <p>
 * The lambda returns nothing, so all of these work: a chain {@code by.set(a, 1).set(b, 2)}, several statements
 * {@code { by.set(a, 1); by.set(b, 2); }}, and the Kotlin index operator {@code { o, by -> by[o.status] = 2 }}
 * (Kotlin turns {@code by[x] = y} into {@code by.set(x, y)}).
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java">DmlQueryTest</a>):
 * {@snippet class="org.lambdaql.query.DmlQueryTest" region="update-by"}
 *
 * @param <T> the entity type
 */
@FunctionalInterface
public interface AssignBy<T> extends Serializable {
    /**
     * The assignments. lambdaQL reads this method's bytecode; it never calls it.
     *
     * @param entity the entity
     * @param by     the assignment builder
     */
    void set(T entity, SetBy by);
}
