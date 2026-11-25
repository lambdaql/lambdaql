package org.lambdaql.engine.render;

import java.util.Map;

/**
 * An HQL query and its parameter values, ready to run.
 *
 * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java">SelectQueryTest</a>):
 * {@snippet class="org.lambdaql.query.SelectQueryTest" region="to-hql"}
 *
 * @param hql        the HQL text with named parameters ({@code :p1}, {@code :p2}, ...)
 * @param parameters parameter name (without {@code :}) to value, in order
 */
public record RenderedQuery(String hql, Map<String, Object> parameters) {
}
