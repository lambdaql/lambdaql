package org.lambdaql;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method of a stub interface as a stored procedure (ROADMAP M11, D30). lambdaQL never implements the
 * interface; it reads the call in {@code qb.call(Procs.class, p -> p.calc(id, rate))} and runs the procedure.
 * <p>
 * The stub arguments are the IN and INOUT parameters in the database order (mark INOUT with {@link InOut}).
 * The result: one OUT value is the return value, more OUT values are a record (or Kotlin data class) in the database
 * order, a result set or REF CURSOR is a {@code List<T>}, and {@code void} means no result.
 *
 * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java">ProcedureTest</a>):
 * {@snippet class="org.lambdaql.query.ProcedureTest" region="declare"}
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SqlProcedure {

    /**
     * The procedure name. Empty means the method name in UPPER_SNAKE case ({@code purgeOld} becomes {@code PURGE_OLD}).
     *
     * @return the name
     */
    String value() default "";

    /**
     * The schema, when it is not the schema of the connection.
     *
     * @return the schema, or empty
     */
    String schema() default "";

    /**
     * The Oracle package that holds the procedure.
     *
     * @return the package, or empty
     */
    String pkg() default "";
}
