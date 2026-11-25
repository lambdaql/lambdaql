package org.lambdaql.engine.ir;

/**
 * One {@code column = value} part of an UPDATE {@code SET}, or one column of an INSERT (ROADMAP M10).
 *
 * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/sem/AssignmentReaderTest.java">AssignmentReaderTest</a>):
 * {@snippet class="org.lambdaql.engine.sem.AssignmentReaderTest" region="read-assign"}
 *
 * @param target the column that gets the value: a basic column, an embedded field or value object,
 *               an association (its foreign key) or the id of an association ({@code customer.id})
 * @param value  the new value: constants, host values, columns (the value before the update) and math
 */
public record Assignment(Expr.Column target, Expr value) {
    @Override
    public String toString() {
        return target + " := " + value;
    }
}
