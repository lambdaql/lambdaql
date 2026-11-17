package org.lambdaql.query;

import java.io.Serializable;

/**
 * A value lambda inside a subquery: {@code x -> x.getPrice()}.
 *
 * @param <X> the entity type
 * @param <R> the value type
 */
@FunctionalInterface
public interface Pick<X, R> extends Serializable {
    /**
     * The value. lambdaQL reads this method's bytecode; it never calls it.
     *
     * @param entity the entity
     * @return the value
     */
    R pick(X entity);
}
