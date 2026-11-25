package org.lambdaql;

import org.lambdaql.i18n.LqlMessage;

/**
 * Thrown in {@code SortMode.STRICT} when an outside sort (a Spring {@code Sort}, {@code SortSpec.ofPath}) uses a name
 * that the sort rules of the entity do not allow (ROADMAP D43, LQL-4040). A web layer usually answers it with
 * HTTP 400; lambdaQL has no web dependency, so map it in your own {@code @ExceptionHandler}.
 *
 * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortPolicyTest.java">SortPolicyTest</a>):
 * {@snippet class="org.lambdaql.query.SortPolicyTest" region="strict"}
 */
public class SortNotAllowedException extends LambdaQLException {

    /**
     * Makes the exception.
     *
     * @param entity the entity name
     * @param name   the sort name that is not allowed
     */
    public SortNotAllowedException(String entity, String name) {
        super(LqlMessage.SORT_NOT_ALLOWED, entity, name);
    }
}
