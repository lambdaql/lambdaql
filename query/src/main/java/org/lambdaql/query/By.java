package org.lambdaql.query;

/**
 * The sort builder, given as the last lambda argument of {@code orderBy}.
 * lambdaQL reads the calls from the lambda bytecode; the lambda is never run, so the methods only describe the order.
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java">SortQueryTest</a>):
 * {@snippet class="org.lambdaql.query.SortQueryTest" region="sort-basic"}
 */
public interface By {

    /**
     * Sorts ascending by a key. NULL comes first (default policy, ROADMAP D16).
     *
     * @param key a column value, for example {@code o.getPrice()}
     * @return the builder, to add the next key
     */
    By asc(Comparable<?> key);

    /**
     * Sorts descending by a key. NULL comes last (default policy, ROADMAP D16).
     *
     * @param key a column value
     * @return the builder
     */
    By desc(Comparable<?> key);

    /**
     * Sorts ascending by a key and chooses where NULL goes.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java">SortQueryTest</a>):
     * {@snippet class="org.lambdaql.query.SortQueryTest" region="sort-nulls"}
     *
     * @param key        a column value
     * @param nullsFirst {@code true}: NULL first; {@code false}: NULL last
     * @return the builder
     */
    By asc(Comparable<?> key, boolean nullsFirst);

    /**
     * Sorts descending by a key and chooses where NULL goes.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java">SortQueryTest</a>):
     * {@snippet class="org.lambdaql.query.SortQueryTest" region="sort-nulls"}
     *
     * @param key        a column value
     * @param nullsFirst {@code true}: NULL first; {@code false}: NULL last
     * @return the builder
     */
    By desc(Comparable<?> key, boolean nullsFirst);

    /**
     * Sorts by a key with a direction that is known only at run time (a captured value).
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java">SortQueryTest</a>):
     * {@snippet class="org.lambdaql.query.SortQueryTest" region="sort-dynamic"}
     *
     * @param key       a column value
     * @param direction the direction (and NULL position)
     * @return the builder
     */
    By sort(Comparable<?> key, Direction direction);
}
