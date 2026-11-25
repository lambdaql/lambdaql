package org.lambdaql;

import org.lambdaql.i18n.LqlMessage;

/**
 * The lambda has no defined meaning in HQL (codes 3xxx): a method call with no SQL form, comparing two entities, a value that is not a {@code groupBy} key.
 * It is a {@link LambdaQLException}; check {@link #code()} in tests.
 *
 * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/i18n/LqlMessagesTest.java">LqlMessagesTest</a>):
 * {@snippet class="org.lambdaql.i18n.LqlMessagesTest" region="exception"}
 */
public class TranslationException extends LambdaQLException {

    /**
     * Makes an exception with a message.
     *
     * @param key  the message
     * @param args the message arguments
     */
    public TranslationException(LqlMessage key, Object... args) {
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
    public TranslationException(LqlMessage key, String where, String detail, Throwable cause, Object[] args) {
        super(key, where, detail, cause, args);
    }
}
