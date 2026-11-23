package org.lambdaql.spring;

import org.springframework.data.core.PropertyPath;

/**
 * Checks a property name of a Spring {@code Sort} with Spring Data's {@code PropertyPath}, for Spring Data 4 (Spring Boot 4).
 * <p>
 * {@code PropertyPath} and {@code PropertyReferenceException} are in {@code org.springframework.data.mapping} in
 * Spring Data 3 and in {@code org.springframework.data.core} in Spring Data 4. This small class is the only code that
 * names them; each lambdaql-spring-data-jpa module ({@code -boot3}, {@code -boot4}) has its own copy (ROADMAP D55).
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java">ExecutorTest</a>):
 * {@snippet class="org.lambdaql.spring.ExecutorTest" region="find-all-spring-sort"}
 */
final class SpringDataPaths {

    private SpringDataPaths() {
    }

    /**
     * Reads a property name the way Spring Data's own repositories do.
     *
     * @param name the property name of a {@code Sort.Order}, for example {@code team.name} or {@code teamName}
     * @param type the entity class
     * @return the property path with dots, for example {@code team.name}
     * @throws RuntimeException Spring Data's {@code PropertyReferenceException} if the property does not exist
     */
    static String dotPath(String name, Class<?> type) {
        return PropertyPath.from(name, type).toDotPath();
    }
}
