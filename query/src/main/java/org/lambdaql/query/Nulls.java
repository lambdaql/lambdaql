package org.lambdaql.query;

/**
 * Where NULL goes in a sort: {@code asc(o.getDescription(), Nulls.LAST)}.
 *
 * <p>Example: {@code orderBy(o -> desc(o.getDescription(), Nulls.FIRST))} gives
 * {@code order by e0.description desc nulls first}.
 */
public enum Nulls {
    /** NULL before the other values. */
    FIRST,
    /** NULL after the other values. */
    LAST
}
