package org.lambdaql.query;

import org.lambdaql.i18n.LqlMessage;
import java.util.Locale;

/**
 * Sort direction, with an optional NULL position.
 * <p>
 * {@link #ASC} and {@link #DESC} use the default NULL position of lambdaQL (ROADMAP D16, "nulls are smallest"):
 * {@code ASC} puts NULL first, {@code DESC} puts NULL last. This is the same on every database.
 * Use it with {@code by.sort(key, direction)} when the direction is known only at run time.
 *
 * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java">SortQueryTest</a>):
 * {@snippet class="org.lambdaql.query.SortQueryTest" region="sort-dynamic"}
 */
public enum Direction {
    /** Ascending; NULL first (default policy). */
    ASC,
    /** Descending; NULL last (default policy). */
    DESC,
    /** Ascending; NULL first. */
    ASC_NULLS_FIRST,
    /** Ascending; NULL last. */
    ASC_NULLS_LAST,
    /** Descending; NULL first. */
    DESC_NULLS_FIRST,
    /** Descending; NULL last. */
    DESC_NULLS_LAST;

    /**
     * Checks if this direction is ascending.
     *
     * @return {@code true} for the three {@code ASC} values
     */
    public boolean ascending() {
        return this == ASC || this == ASC_NULLS_FIRST || this == ASC_NULLS_LAST;
    }

    /**
     * Tells where NULL goes.
     *
     * @return {@code true} if NULL comes first
     */
    public boolean nullsFirst() {
        return switch (this) {
            case ASC, ASC_NULLS_FIRST, DESC_NULLS_FIRST -> true;
            case DESC, ASC_NULLS_LAST, DESC_NULLS_LAST -> false;
        };
    }

    /**
     * Returns the opposite direction. The NULL position also flips, so the whole order is reversed.
     *
     * @return the reversed direction
     *
     * <p>Example: {@code ASC.reversed()} is {@code DESC}; {@code ASC_NULLS_LAST.reversed()} is {@code DESC_NULLS_FIRST}.
     */
    public Direction reversed() {
        return switch (this) {
            case ASC -> DESC;
            case DESC -> ASC;
            case ASC_NULLS_FIRST -> DESC_NULLS_LAST;
            case ASC_NULLS_LAST -> DESC_NULLS_FIRST;
            case DESC_NULLS_FIRST -> ASC_NULLS_LAST;
            case DESC_NULLS_LAST -> ASC_NULLS_FIRST;
        };
    }

    /**
     * Reads a direction from text, for example a request parameter.
     * Case and {@code -} / {@code _} / space do not matter.
     *
     * @param text for example {@code "desc"}, {@code "DESC_NULLS_LAST"}, {@code "asc nulls first"}
     * @return the direction
     * @throws IllegalArgumentException if the text is not a direction
     *
     * <p>Example: {@code Direction.parse("desc-nulls-last")} is {@code DESC_NULLS_LAST}.
     */
    public static Direction parse(String text) {
        if (text == null) throw new IllegalArgumentException(LqlMessage.DIRECTION_NULL.format());
        String norm = text.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return valueOf(norm);
    }
}
