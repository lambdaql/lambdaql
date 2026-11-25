package org.lambdaql.query;

/**
 * What lambdaQL does with a condition that cannot use an index (ROADMAP PR8, 3.9 SARGable rule).
 * Registered per persistence unit with {@link SargablePolicy#register}, or in Spring with
 * {@code lambdaql.sargable.mode}.
 *
 * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java">FunctionTest</a>):
 * {@snippet class="org.lambdaql.query.FunctionTest" region="sargable-strict"}
 */
public enum SargableMode {
    /** No check, and no metadata query. */
    OFF,
    /** The default: a database function on a column (LQL-5001) and a non-deterministic function (LQL-5007) are logged at WARN. */
    WARN,
    /** The same findings throw {@link org.lambdaql.NonSargableException} (LQL-3066) when the lambda is analyzed. */
    STRICT
}
