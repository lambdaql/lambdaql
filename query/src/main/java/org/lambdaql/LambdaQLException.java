package org.lambdaql;

import org.lambdaql.i18n.LqlMessage;
import org.lambdaql.i18n.LqlMessages;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The base class of all lambdaQL exceptions (ROADMAP 3.10, D33). Every message starts with a code that does not
 * depend on the language, for example {@code [LQL-2004]}. Check {@link #code()} in tests, not the text.
 * <p>
 * The message is made when the exception is made, in the language of {@link LqlMessages#locale()}.
 * {@link #getMessage(Locale)} gives it in another language.
 *
 * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/i18n/LqlMessagesTest.java">LqlMessagesTest</a>):
 * {@snippet class="org.lambdaql.i18n.LqlMessagesTest" region="exception"}
 */
public class LambdaQLException extends RuntimeException {

    private final LqlMessage key;
    private final transient Object[] args;
    private final String[] argTexts;
    private final String where;
    private final String detail;

    /**
     * Makes an exception with a message.
     *
     * @param key  the message
     * @param args the message arguments
     */
    public LambdaQLException(LqlMessage key, Object... args) {
        this(key, null, null, null, args == null ? new Object[0] : args);
    }

    /**
     * Makes an exception with a message, the place of the lambda, extra lines and a cause.
     *
     * @param key    the message
     * @param where  the lambda body method (and line), added after the text; may be null
     * @param detail extra lines (for example a bytecode dump), not translated; may be null
     * @param cause  the original exception; may be null
     * @param args   the message arguments
     */
    public LambdaQLException(LqlMessage key, String where, String detail, Throwable cause, Object[] args) {
        super(compose(key, LqlMessages.locale(), where, detail, args), cause);
        this.key = key;
        this.args = args.clone();
        this.argTexts = Arrays.stream(args).map(String::valueOf).toArray(String[]::new);
        this.where = where;
        this.detail = detail;
    }

    /**
     * Makes the exception class that fits the code range: 1xxx {@link LambdaAnalysisException},
     * 2xxx {@link UnsupportedLambdaShapeException}, 3xxx {@link TranslationException}, others this class.
     *
     * @param key    the message
     * @param where  the lambda body method (and line); may be null
     * @param detail extra lines, not translated; may be null
     * @param cause  the original exception; may be null
     * @param args   the message arguments
     * @return the exception (the caller throws it)
     */
    public static LambdaQLException of(LqlMessage key, String where, String detail, Throwable cause, Object... args) {
        Object[] a = args == null ? new Object[0] : args;
        return switch (key.code().charAt(4)) {
            case '1' -> new LambdaAnalysisException(key, where, detail, cause, a);
            case '2' -> new UnsupportedLambdaShapeException(key, where, detail, cause, a);
            case '3' -> new TranslationException(key, where, detail, cause, a);
            default -> new LambdaQLException(key, where, detail, cause, a);
        };
    }

    /**
     * Returns the stable code.
     *
     * @return the code, for example {@code "LQL-2004"}
     */
    public String code() {
        return key.code();
    }

    /**
     * Returns the message key.
     *
     * @return the key
     */
    public LqlMessage key() {
        return key;
    }

    /**
     * Returns the message arguments. After deserialization they are the string forms.
     *
     * @return the arguments
     */
    public List<Object> args() {
        return args != null ? Arrays.asList(args) : List.of((Object[]) argTexts);
    }

    /**
     * Returns the place of the lambda: the method that holds the lambda body, and the line when it is known.
     *
     * @return the place, or null
     */
    public String where() {
        return where;
    }

    /**
     * Returns the same message in another language.
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/i18n/LqlMessagesTest.java">LqlMessagesTest</a>):
     * {@snippet class="org.lambdaql.i18n.LqlMessagesTest" region="exception"}
     *
     * @param locale the language, for example {@link Locale#ROOT} for English
     * @return the message with its code
     */
    public String getMessage(Locale locale) {
        return compose(key, locale, where, detail, args != null ? args : argTexts);
    }

    private static String compose(LqlMessage key, Locale locale, String where, String detail, Object[] args) {
        StringBuilder s = new StringBuilder(LqlMessages.format(key, locale, args));
        if (where != null) s.append(" — ").append(where);
        if (detail != null) s.append('\n').append(detail);
        return s.toString();
    }
}
