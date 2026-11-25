package org.lambdaql.spring;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Checks a property name of a Spring {@code Sort} with Spring Data's {@code PropertyPath}, on Spring Data 3 and 4.
 * <p>
 * {@code PropertyPath} (and its {@code PropertyReferenceException}) is in {@code org.springframework.data.mapping} in
 * Spring Data 3 and in {@code org.springframework.data.core} in Spring Data 4. This is the only class of
 * lambdaql-spring-data-jpa that depends on the Spring Data version, so the jar works with both (ROADMAP D55):
 * it finds the class of the running Spring Data once and calls it by reflection. An unknown property still throws
 * Spring Data's own {@code PropertyReferenceException}, as Spring Data's repositories do.
 *
 * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java">ExecutorTest</a>):
 * {@snippet class="org.lambdaql.spring.ExecutorTest" region="find-all-spring-sort"}
 */
final class SpringDataPaths {

    /** Spring Data 4 first, then Spring Data 3. */
    private static final String[] CLASS_NAMES = {
            "org.springframework.data.core.PropertyPath", "org.springframework.data.mapping.PropertyPath"};

    private static final Method FROM;
    private static final Method TO_DOT_PATH;

    static {
        Class<?> type = null;
        for (String name : CLASS_NAMES) {
            try {
                type = Class.forName(name, false, SpringDataPaths.class.getClassLoader());
                break;
            } catch (ClassNotFoundException e) {
                // the other Spring Data version
            }
        }
        if (type == null) throw new IllegalStateException("Spring Data PropertyPath not found: " + String.join(", ", CLASS_NAMES));
        try {
            FROM = type.getMethod("from", String.class, Class.class);
            TO_DOT_PATH = type.getMethod("toDotPath");
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Spring Data PropertyPath has no from(String, Class) or toDotPath()", e);
        }
    }

    private SpringDataPaths() {
    }

    /**
     * Reads a property name the way Spring Data's own repositories do.
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java">ExecutorTest</a>):
     * {@snippet class="org.lambdaql.spring.ExecutorTest" region="find-all-spring-sort"}
     *
     * @param name the property name of a {@code Sort.Order}, for example {@code team.name} or {@code teamName}
     * @param type the entity class
     * @return the property path with dots, for example {@code team.name}
     * @throws RuntimeException Spring Data's {@code PropertyReferenceException} if the property does not exist
     */
    static String dotPath(String name, Class<?> type) {
        try {
            return (String) TO_DOT_PATH.invoke(FROM.invoke(null, name, type));
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException r) throw r;
            throw new IllegalStateException(e.getCause());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
