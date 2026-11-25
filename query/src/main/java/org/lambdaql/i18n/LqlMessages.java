package org.lambdaql.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chooses the message language and makes message texts (ROADMAP 3.10, D33).
 * <p>
 * The language comes from, in this order: {@link #setLocale(Locale)}, the system property {@code lambdaql.locale},
 * the JVM default locale. Only the language part counts: {@code ja} gives Japanese, {@code zh} gives Chinese
 * (Simplified, also for {@code zh_TW}), {@code ko} gives Korean, anything else gives English.
 * lambdaQL reads the bundle files itself, so the {@code ResourceBundle} fallback to the JVM locale never applies
 * (a Korean JVM still gives English when English is asked for).
 *
 * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/i18n/LqlMessagesTest.java">LqlMessagesTest</a>):
 * {@snippet class="org.lambdaql.i18n.LqlMessagesTest" region="locale"}
 */
public final class LqlMessages {

    /** The system property for the language, for example {@code -Dlambdaql.locale=ja}. */
    public static final String LOCALE_PROPERTY = "lambdaql.locale";

    private static final String BUNDLE = "LqlMessages";
    private static final Map<String, ResourceBundle> BUNDLES = new ConcurrentHashMap<>();
    private static volatile Locale configured;

    private LqlMessages() {
    }

    /**
     * Sets the message language for the whole application.
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/i18n/LqlMessagesTest.java">LqlMessagesTest</a>):
     * {@snippet class="org.lambdaql.i18n.LqlMessagesTest" region="locale"}
     *
     * @param locale the language, or {@code null} to use the system property and then the JVM default again
     */
    public static void setLocale(Locale locale) {
        configured = locale;
    }

    /**
     * Returns the locale lambdaQL uses now: the one given to {@link #setLocale(Locale)}, else the system property
     * {@code lambdaql.locale}, else the JVM default.
     *
     * @return the locale
     */
    public static Locale locale() {
        Locale l = configured;
        if (l != null) return l;
        String p = System.getProperty(LOCALE_PROPERTY);
        if (p != null && !p.isBlank()) return Locale.forLanguageTag(p.trim().replace('_', '-'));
        return Locale.getDefault();
    }

    /**
     * Returns the bundle locale for a locale: {@link Locale#JAPANESE}, {@link Locale#CHINESE}, {@link Locale#KOREAN}
     * or {@link Locale#ROOT} (English).
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/i18n/LqlMessagesTest.java">LqlMessagesTest</a>):
     * {@snippet class="org.lambdaql.i18n.LqlMessagesTest" region="locale"}
     *
     * @param locale any locale, or {@code null} for the current one
     * @return the locale of the bundle that is used
     */
    public static Locale bundleLocale(Locale locale) {
        String lang = (locale == null ? locale() : locale).getLanguage();
        return switch (lang) {
            case "ja" -> Locale.JAPANESE;
            case "zh" -> Locale.CHINESE;
            case "ko" -> Locale.KOREAN;
            default -> Locale.ROOT;
        };
    }

    /**
     * Returns the message text without the code and without filling the arguments.
     * A key missing in a translated bundle falls back to English.
     *
     * @param key    the message
     * @param locale the language
     * @return the text, for example {@code "limit must be 0 or more: {0}"}
     */
    public static String pattern(LqlMessage key, Locale locale) {
        String suffix = suffix(bundleLocale(locale));
        ResourceBundle b = bundle(suffix);
        if (b.containsKey(key.name())) return b.getString(key.name());
        return bundle("").getString(key.name());
    }

    /**
     * Makes the full message: the code, then the text with the arguments filled in.
     *
     * @param key    the message
     * @param locale the language
     * @param args   the arguments
     * @return the message, for example {@code "[LQL-4014] limit must be 0 or more: -1"}
     */
    public static String format(LqlMessage key, Locale locale, Object... args) {
        return "[" + key.code() + "] " + fill(pattern(key, locale), args);
    }

    /**
     * Fills {@code {0}}, {@code {1}} ... with the arguments. Unlike {@code java.text.MessageFormat}, quotes and
     * numbers are left as they are ({@code can't} stays, 1234567 is not written as 1,234,567).
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/i18n/LqlMessagesTest.java">LqlMessagesTest</a>):
     * {@snippet class="org.lambdaql.i18n.LqlMessagesTest" region="fill"}
     *
     * @param pattern the text
     * @param args    the arguments ({@code null} is written as {@code null})
     * @return the text with the arguments
     */
    public static String fill(String pattern, Object... args) {
        StringBuilder out = new StringBuilder(pattern.length() + 32);
        int i = 0;
        while (i < pattern.length()) {
            char c = pattern.charAt(i);
            if (c == '{') {
                int end = pattern.indexOf('}', i + 1);
                if (end > i + 1 && isDigits(pattern, i + 1, end)) {
                    int n = Integer.parseInt(pattern.substring(i + 1, end));
                    if (args != null && n < args.length) {
                        out.append(args[n]);
                        i = end + 1;
                        continue;
                    }
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static boolean isDigits(String s, int from, int to) {
        for (int k = from; k < to; k++) if (!Character.isDigit(s.charAt(k))) return false;
        return to - from <= 2;
    }

    private static String suffix(Locale bundleLocale) {
        return bundleLocale.getLanguage().isEmpty() ? "" : "_" + bundleLocale.getLanguage();
    }

    private static ResourceBundle bundle(String suffix) {
        return BUNDLES.computeIfAbsent(suffix, s -> {
            String name = BUNDLE + s + ".properties";
            try (InputStream in = LqlMessages.class.getResourceAsStream(name)) {
                if (in == null) {
                    if (s.isEmpty()) throw new IllegalStateException("lambdaQL message bundle is missing: " + name);
                    return bundle("");
                }
                return new PropertyResourceBundle(new InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException("lambdaQL message bundle cannot be read: " + name, e);
            }
        });
    }
}
