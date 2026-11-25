package org.lambdaql;

import org.lambdaql.i18n.LqlMessage;

/**
 * lambdaQL cannot load or read the lambda (codes 1xxx): a non-serializable lambda, an anonymous class, a class file that cannot be read.
 * It is a {@link LambdaQLException}; check {@link #code()} in tests.
 *
 * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/i18n/LqlMessagesTest.java">LqlMessagesTest</a>):
 * {@snippet class="org.lambdaql.i18n.LqlMessagesTest" region="exception"}
 */
public class LambdaAnalysisException extends LambdaQLException {

    /**
     * Makes an exception with a message.
     *
     * @param key  the message
     * @param args the message arguments
     */
    public LambdaAnalysisException(LqlMessage key, Object... args) {
        super(key, args);
    }

    /**
     * Makes an exception with a message, the place of the lambda, extra lines and a cause.
     *
     * @param key    the message
     * @param where  the lambda body method (and line); may be null
     * @param detail extra lines, not translated; may be null
     * @param cause  the original exception; may be null
     * @param args   the message arguments
     */
    public LambdaAnalysisException(LqlMessage key, String where, String detail, Throwable cause, Object[] args) {
        super(key, where, detail, cause, args);
    }
}
