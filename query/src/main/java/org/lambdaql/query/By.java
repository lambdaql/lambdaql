package org.lambdaql.query;

import org.lambdaql.i18n.LqlMessage;

/**
 * The sort functions of {@code orderBy}: {@code orderBy(o -> desc(o.getPrice()).asc(o.getId()))}.
 * <p>
 * Use them with a static import ({@code import static org.lambdaql.query.By.*;}) or with the class name
 * ({@code By.desc(o.getPrice())}). lambdaQL reads the calls from the lambda bytecode; the lambda is not run, so the
 * functions only describe the order. Called outside a sort lambda, they throw {@code IllegalStateException}.
 *
 * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java">SortQueryTest</a>):
 * {@snippet class="org.lambdaql.query.SortQueryTest" region="sort-basic"}
 */
public final class By {

    private By() {
    }

    /**
     * Sorts ascending by a key. NULL comes first (default policy, ROADMAP D16).
     *
     * <p>Example: {@code orderBy(o -> asc(o.getPrice()))} gives {@code order by e0.price asc}.
     *
     * @param key a column value, for example {@code o.getPrice()}
     * @return the keys, to add the next key
     * @throws IllegalStateException always, when called outside a sort lambda
     */
    public static SortKeys asc(Comparable<?> key) {
        throw outside("asc");
    }

    /**
     * Sorts descending by a key. NULL comes last (default policy, ROADMAP D16).
     *
     * <p>Example: {@code orderBy(o -> desc(o.getPrice()))} gives {@code order by e0.price desc}.
     *
     * @param key a column value
     * @return the keys
     * @throws IllegalStateException always, when called outside a sort lambda
     */
    public static SortKeys desc(Comparable<?> key) {
        throw outside("desc");
    }

    /**
     * Sorts ascending by a key and chooses where NULL goes.
     *
     * <p>Example: {@code orderBy(o -> asc(o.getDescription(), Nulls.LAST))}.
     *
     * @param key   a column value
     * @param nulls {@link Nulls#FIRST} or {@link Nulls#LAST}
     * @return the keys
     * @throws IllegalStateException always, when called outside a sort lambda
     */
    public static SortKeys asc(Comparable<?> key, Nulls nulls) {
        throw outside("asc");
    }

    /**
     * Sorts descending by a key and chooses where NULL goes.
     *
     * <p>Example: {@code orderBy(o -> desc(o.getDescription(), Nulls.FIRST))}.
     *
     * @param key   a column value
     * @param nulls {@link Nulls#FIRST} or {@link Nulls#LAST}
     * @return the keys
     * @throws IllegalStateException always, when called outside a sort lambda
     */
    public static SortKeys desc(Comparable<?> key, Nulls nulls) {
        throw outside("desc");
    }

    /**
     * Sorts by a key in a direction given as a value, for example one read from a request.
     *
     * <p>Example: {@code orderBy(o -> sort(o.getPrice(), Direction.parse(text)))}.
     *
     * @param key       a column value
     * @param direction the direction (a captured value)
     * @return the keys
     * @throws IllegalStateException always, when called outside a sort lambda
     */
    public static SortKeys sort(Comparable<?> key, Direction direction) {
        throw outside("sort");
    }

    private static IllegalStateException outside(String name) {
        return new IllegalStateException(LqlMessage.SORT_OUTSIDE_LAMBDA.format(name));
    }
}
