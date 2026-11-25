package org.lambdaql;

import org.lambdaql.i18n.LqlMessage;

/**
 * Thrown when a {@link SqlFunction} method is called in Java. A database function works only inside a lambdaQL
 * lambda, where lambdaQL reads the call and never runs it.
 *
 * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java">FunctionTest</a>):
 * {@snippet class="org.lambdaql.query.FunctionTest" region="declare"}
 */
public class DbOnlyException extends LambdaQLException {

    /** Makes the exception. */
    public DbOnlyException() {
        super(LqlMessage.DB_ONLY_CALLED, "@SqlFunction");
    }
}
