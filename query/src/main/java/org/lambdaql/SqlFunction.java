package org.lambdaql;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a database function that lambdaQL calls inside queries (ROADMAP M11, D31).
 * Declare it as a static method of your own interface (Kotlin: a {@code @JvmStatic} method of the interface
 * companion). The body is never run; it only throws {@link DbOnlyException}.
 * <p>
 * lambdaQL writes the call as {@code function('<key>' as <type>, args...)}. The return type must be Double,
 * Integer, Long, BigDecimal, String, LocalDate, LocalDateTime or Boolean (primitive types become their wrapper).
 *
 * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java">FunctionTest</a>):
 * {@snippet class="org.lambdaql.query.FunctionTest" region="declare"}
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SqlFunction {

    /**
     * The function name used in the query. Empty means the method name in UPPER_SNAKE case
     * ({@code isVip} becomes {@code IS_VIP}). A schema-qualified name ({@code PUBLIC.SQ_FN}) is allowed.
     *
     * @return the name
     */
    String value() default "";

    /**
     * The SQL name, when it is not the same as {@link #value()}: for example a quoted mixed-case name
     * {@code "\"SQ_Fn_Mixed\""}. Such a function must be registered with the setting
     * {@code lambdaql.functions.classes}.
     *
     * @return the SQL name, or empty for the same as the name
     */
    String sql() default "";
}
