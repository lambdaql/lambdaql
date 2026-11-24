package org.lambdaql.query;

/**
 * The keys of a sort, made by the functions of {@link By}: {@code desc(o.getPrice()).asc(o.getId())}.
 * <p>
 * It extends {@code Comparable} only so that one {@code orderBy} accepts both a plain key ({@code o -> o.getPrice()})
 * and these keys ({@code o -> desc(o.getPrice())}); a sort lambda returns {@code Comparable<?>}. lambdaQL reads the
 * calls from the lambda bytecode and never runs them, so {@link #compareTo} is never called.
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java">SortQueryTest</a>):
 * {@snippet class="org.lambdaql.query.SortQueryTest" region="sort-basic"}
 */
public interface SortKeys extends Comparable<SortKeys> {

    /**
     * Adds an ascending key. NULL comes first (default policy).
     *
     * <p>Example: {@code desc(o.getPrice()).asc(o.getId())}.
     *
     * @param key a column value
     * @return the keys
     */
    SortKeys asc(Comparable<?> key);

    /**
     * Adds a descending key. NULL comes last (default policy).
     *
     * <p>Example: {@code asc(o.getStatus()).desc(o.getPrice())}.
     *
     * @param key a column value
     * @return the keys
     */
    SortKeys desc(Comparable<?> key);

    /**
     * Adds an ascending key and chooses where NULL goes.
     *
     * <p>Example: {@code desc(o.getPrice()).asc(o.getDescription(), Nulls.LAST)}.
     *
     * @param key   a column value
     * @param nulls {@link Nulls#FIRST} or {@link Nulls#LAST}
     * @return the keys
     */
    SortKeys asc(Comparable<?> key, Nulls nulls);

    /**
     * Adds a descending key and chooses where NULL goes.
     *
     * <p>Example: {@code asc(o.getStatus()).desc(o.getDescription(), Nulls.FIRST)}.
     *
     * @param key   a column value
     * @param nulls {@link Nulls#FIRST} or {@link Nulls#LAST}
     * @return the keys
     */
    SortKeys desc(Comparable<?> key, Nulls nulls);

    /**
     * Adds a key with a direction given as a value.
     *
     * <p>Example: {@code asc(o.getStatus()).sort(o.getPrice(), dir)}.
     *
     * @param key       a column value
     * @param direction the direction (a captured value)
     * @return the keys
     */
    SortKeys sort(Comparable<?> key, Direction direction);
}
