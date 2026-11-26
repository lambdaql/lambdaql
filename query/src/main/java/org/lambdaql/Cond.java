package org.lambdaql;

/**
 * Conditions that have no Java operator, for {@code where} lambdas (ROADMAP D77).
 * <p>
 * lambdaQL reads the call from the lambda bytecode and writes it as SQL; the method body does not run in a query.
 * The body is still a normal Java method with the SQL meaning, so the same test can be used on a list in memory.
 *
 * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/TimeCompareTest.java">TimeCompareTest</a>):
 * {@snippet class="org.lambdaql.query.TimeCompareTest" region="between"}
 */
public final class Cond {

    private Cond() {
    }

    /**
     * Checks if a value is between two values, both ends included: SQL {@code from <= value and value <= to}
     * (the same as {@code value between from and to}).
     * <p>
     * In a query, a NULL value or a NULL end matches no row. Called in Java, the method gives {@code false} for them.
     * For a time range, a half-open range ({@code !t.isBefore(from) && t.isBefore(to)}) is often better: it does not
     * lose the last fraction of a second.
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/TimeCompareTest.java">TimeCompareTest</a>):
     * {@snippet class="org.lambdaql.query.TimeCompareTest" region="between"}
     *
     * @param value the tested value, usually an entity value such as {@code o.getUpdateAt()}
     * @param from  the smallest value that matches
     * @param to    the largest value that matches
     * @param <T>   the value type: a date, a time, a number, a string or another {@link Comparable}
     * @return {@code true} if {@code from <= value <= to}; {@code false} if one of them is {@code null}
     */
    public static <T extends Comparable<? super T>> boolean between(T value, T from, T to) {
        return value != null && from != null && to != null && from.compareTo(value) <= 0 && value.compareTo(to) <= 0;
    }
}
