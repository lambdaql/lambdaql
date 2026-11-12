package org.lambdaql.engine.ir;

/**
 * One key of an {@code order by}, read from a sort lambda.
 * <p>
 * The direction can be fixed in the code ({@code by.asc(key)}, {@code by.desc(key, false)})
 * or known only at run time ({@code by.sort(key, dir)} with a captured {@code dir}).
 * Run-time parts are kept as expressions and read again on every execution, so the analysis can be cached.
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/sem/SortReaderTest.java">SortReaderTest</a>):
 * {@snippet class="org.lambdaql.engine.sem.SortReaderTest" region="read-sort"}
 *
 * @param key  the normalized key ({@link Expr.Column}, or math on columns)
 * @param kind which builder method made this key
 * @param arg  for {@link Kind#ASC}/{@link Kind#DESC}: the {@code nullsFirst} value ({@link Expr.Const} or
 *             {@link Expr.Host}), or {@code null} for the default NULL position;
 *             for {@link Kind#SORT}: the {@code Direction} value ({@link Expr.Const} or {@link Expr.Host})
 */
public record SortItem(Expr key, Kind kind, Expr arg) {

    /** The builder method. */
    public enum Kind {
        /** {@code by.asc(key)} or {@code by.asc(key, nullsFirst)}. */
        ASC,
        /** {@code by.desc(key)} or {@code by.desc(key, nullsFirst)}. */
        DESC,
        /** {@code by.sort(key, direction)}. */
        SORT
    }

    @Override
    public String toString() {
        return kind.name().toLowerCase() + "(" + key + (arg == null ? "" : ", " + arg) + ")";
    }
}
