package org.lambdaql.query;

/**
 * How lambdaQL treats an outside sort that the sort rules do not allow (ROADMAP D43).
 * Only sorts made from strings are checked (a Spring {@code Sort} or {@code Pageable}, {@link SortSpec#ofPath});
 * sorts written as lambdas in your code are never checked.
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortPolicyTest.java">SortPolicyTest</a>):
 * {@snippet class="org.lambdaql.query.SortPolicyTest" region="strict"}
 */
public enum SortMode {
    /** No check. */
    OFF,
    /** Run the sort, and log LQL-5003 at INFO level once per entity and name. The default when no policy is registered. */
    INFO,
    /** Run the sort, and log LQL-5003 at WARN level once per entity and name. */
    WARN,
    /** Reject the sort with {@link org.lambdaql.SortNotAllowedException} (LQL-4040). */
    STRICT
}
