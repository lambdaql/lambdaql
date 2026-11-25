package org.lambdaql;

import org.lambdaql.i18n.LqlMessage;

/**
 * The assignment function for UPDATE and INSERT lambdas (ROADMAP D49): {@code value(column, newValue)}, for entities
 * without setters (DDD style, Kotlin {@code val}). The column is written as a getter call on the target.
 * <p>
 * The method is only a <b>marker</b>: lambdaQL reads the call from the lambda bytecode and never runs the lambda.
 * Calling it anywhere else throws {@link IllegalStateException}. Setter calls and {@code value} can be mixed in one
 * lambda.
 * <p>
 * A class with its own {@code value(...)} method (a record with a {@code value} component, a wrapper type) hides the
 * static import inside its body; write {@code Dml.value(...)} there.
 *
 * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java">DmlQueryTest</a>):
 * {@snippet class="org.lambdaql.query.DmlQueryTest" region="update-value"}
 */
public final class Dml {

    private Dml() {
    }

    /**
     * Assigns a new value to a column: {@code value(o.getStatus(), 2)} becomes {@code set e0.status = 2}.
     * The value may read the row (the value before the update), captured values and subqueries ({@code sub}).
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java">DmlQueryTest</a>):
     * {@snippet class="org.lambdaql.query.DmlQueryTest" region="update-value"}
     *
     * @param column   the column, written as a getter call on the target, for example {@code o.getStatus()}
     * @param newValue the new value
     * @param <V>      the value type
     */
    public static <V> void value(V column, V newValue) {
        throw new IllegalStateException(LqlMessage.PLACEHOLDER_CALLED.format("Dml"));
    }
}
