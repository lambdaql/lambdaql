package org.lambdaql;

import org.lambdaql.i18n.LqlMessage;

/**
 * Thrown in {@code SargableMode.STRICT} when a condition cannot use an index (ROADMAP PR8, LQL-3066): a database
 * function on a column, or on MariaDB a non-deterministic function compared with a column. The message holds the
 * finding that {@code SargableMode.WARN} would log.
 *
 * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java">FunctionTest</a>):
 * {@snippet class="org.lambdaql.query.FunctionTest" region="sargable-strict"}
 */
public class NonSargableException extends TranslationException {

    /**
     * Makes the exception.
     *
     * @param finding the finding, as the warning would say it
     */
    public NonSargableException(String finding) {
        super(LqlMessage.NON_SARGABLE, finding);
    }
}
