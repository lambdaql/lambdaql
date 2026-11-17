package org.lambdaql.query;

/**
 * A CTE handle made with {@code qb.with(valueQuery)} (ROADMAP M8-2, J6). Read it with {@code qb.from(t)}; lambdaQL writes
 * {@code with t as (select ... as customerId, ...) select ... from t e0}. The columns are named after the row type.
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DerivedQueryTest.java">DerivedQueryTest</a>):
 * {@snippet class="org.lambdaql.query.DerivedQueryTest" region="cte"}
 *
 * @param <R> the row type (a record or Kotlin data class)
 */
public final class Cte<R> {

    final DerivedSource source;

    Cte(ValueQuery<R> inner, String name) {
        this.source = new DerivedSource(inner, name);
    }
}
