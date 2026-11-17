package org.lambdaql.i18n;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lambdaql.LambdaAnalysisException;
import org.lambdaql.LambdaQLException;
import org.lambdaql.TranslationException;
import org.lambdaql.UnsupportedLambdaShapeException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the message codes and the four message languages (ROADMAP 3.10, D33, rules I1-I7).
 * The region markers are used by Javadoc snippet tags.
 */
class LqlMessagesTest {

    private static final List<String> SUFFIXES = List.of("", "_ja", "_zh", "_ko");

    @AfterEach
    void reset() {
        LqlMessages.setLocale(null);
        System.clearProperty(LqlMessages.LOCALE_PROPERTY);
    }

    @Test
    void format() {
        // @start region="format"
        LqlMessages.setLocale(Locale.ENGLISH);
        assertEquals("[LQL-4014] limit must be 0 or more: -1", LqlMessage.LIMIT_NEGATIVE.format(-1));
        // the same message in another language: the code stays
        assertEquals("[LQL-4014] limit은 0 이상이어야 합니다: -1", LqlMessage.LIMIT_NEGATIVE.format(Locale.KOREAN, -1));
        // @end
        assertEquals("[LQL-4014] limit は 0 以上でなければなりません: -1", LqlMessage.LIMIT_NEGATIVE.format(Locale.JAPANESE, -1));
        assertEquals("[LQL-4014] limit 必须大于或等于 0：-1", LqlMessage.LIMIT_NEGATIVE.format(Locale.SIMPLIFIED_CHINESE, -1));
    }

    @Test
    void fill() {
        // @start region="fill"
        // no MessageFormat: quotes and big numbers stay as they are (rule I4)
        assertEquals("can't read line 1234567", LqlMessages.fill("can't read line {0}", 1234567));
        assertEquals("a null b", LqlMessages.fill("a {0} b", (Object) null));
        assertEquals("{x} {9}", LqlMessages.fill("{x} {9}", "unused"));
        // @end
    }

    @Test
    void locale() {
        // @start region="locale"
        // 1. setLocale wins, 2. then -Dlambdaql.locale, 3. then the JVM default
        LqlMessages.setLocale(Locale.JAPANESE);
        assertEquals(Locale.JAPANESE, LqlMessages.locale());
        LqlMessages.setLocale(null);
        System.setProperty(LqlMessages.LOCALE_PROPERTY, "ko_KR");
        assertEquals(Locale.KOREAN, LqlMessages.bundleLocale(LqlMessages.locale()));
        // only the language counts; other languages get English (the root bundle)
        assertEquals(Locale.CHINESE, LqlMessages.bundleLocale(Locale.TRADITIONAL_CHINESE));
        assertEquals(Locale.ROOT, LqlMessages.bundleLocale(Locale.FRENCH));
        // @end
    }

    @Test
    void englishOnKoreanJvm() {
        // the ResourceBundle fallback would give Korean here; lambdaQL picks the bundle itself (rule I2)
        Locale old = Locale.getDefault();
        try {
            Locale.setDefault(Locale.KOREAN);
            assertEquals("[LQL-1001] The lambda is null.", LqlMessage.LAMBDA_NULL.format(Locale.ENGLISH));
            assertEquals("[LQL-1001] The lambda is null.", LqlMessage.LAMBDA_NULL.format(Locale.FRENCH));
        } finally {
            Locale.setDefault(old);
        }
    }

    @Test
    void exception() {
        // @start region="exception"
        LqlMessages.setLocale(Locale.KOREAN);
        LambdaQLException e = LqlMessage.LOOP_NOT_SUPPORTED.exceptionAt("app.Repo.lambda$find$0 (line 12)");
        // check the code in tests, not the text
        assertEquals("LQL-2004", e.code());
        assertInstanceOf(UnsupportedLambdaShapeException.class, e);
        assertTrue(e.getMessage().startsWith("[LQL-2004] 람다 안의 반복문은"), e.getMessage());
        // the same message in English, with the place of the lambda at the end
        assertEquals("[LQL-2004] Loops are not supported in a lambda. Build the list outside the lambda and capture it."
                + " — app.Repo.lambda$find$0 (line 12)", e.getMessage(Locale.ROOT));
        // @end
        assertInstanceOf(LambdaAnalysisException.class, LqlMessage.LAMBDA_NULL.exception());
        assertInstanceOf(TranslationException.class, LqlMessage.NOT_GROUP_KEY.exception("x"));
        assertEquals(LambdaQLException.class, LqlMessage.IN_COLLECTION_NULL.exception().getClass());
    }

    @Test
    void everyKeyInEveryBundle() throws IOException {
        // rule I7: a new message must be added in all four languages
        Set<String> keys = Arrays.stream(LqlMessage.values()).map(Enum::name).collect(Collectors.toCollection(TreeSet::new));
        for (String suffix : SUFFIXES) {
            Properties p = load(suffix);
            assertEquals(keys, new TreeSet<>(p.stringPropertyNames()), "keys of LqlMessages" + suffix);
        }
    }

    @Test
    void argumentCounts() throws IOException {
        Pattern arg = Pattern.compile("\\{(\\d+)}");
        for (String suffix : SUFFIXES) {
            Properties p = load(suffix);
            for (LqlMessage m : LqlMessage.values()) {
                Set<Integer> found = new HashSet<>();
                Matcher x = arg.matcher(p.getProperty(m.name()));
                while (x.find()) found.add(Integer.parseInt(x.group(1)));
                Set<Integer> expected = new HashSet<>();
                for (int i = 0; i < m.argCount(); i++) expected.add(i);
                assertEquals(expected, found, m.name() + " in LqlMessages" + suffix);
            }
        }
    }

    @Test
    void codesAreUnique() {
        Set<String> codes = new HashSet<>();
        for (LqlMessage m : LqlMessage.values()) {
            assertTrue(m.code().matches("LQL-[1-5]\\d{3}"), m.code());
            assertTrue(codes.add(m.code()), "duplicate " + m.code());
        }
    }

    private static Properties load(String suffix) throws IOException {
        Properties p = new Properties();
        try (InputStream in = LqlMessages.class.getResourceAsStream("LqlMessages" + suffix + ".properties")) {
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return p;
    }
}
