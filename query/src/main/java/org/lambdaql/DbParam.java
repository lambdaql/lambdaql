package org.lambdaql;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Gives the database parameter name of a {@link SqlProcedure} stub argument (ROADMAP M11, PR2). Optional: use it when
 * the Java names follow another naming rule than the database, so that lambdaQL can still find arguments in the
 * wrong order. Without it, lambdaQL compares the Java argument names (compiled with {@code -parameters}).
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java">ProcedureTest</a>):
 * {@snippet class="org.lambdaql.query.ProcedureTest" region="names"}
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface DbParam {

    /**
     * The database parameter name, for example {@code P_TARGET_ACCOUNT}.
     *
     * @return the name
     */
    String value();
}
