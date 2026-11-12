package org.lambdaql.query;

import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.ArrayList;
import java.util.List;

/**
 * Test helper: records the SQL that Hibernate sends to the database.
 * It is set in {@code application-test.yaml} ({@code hibernate.session_factory.statement_inspector}).
 * Recording is off until {@link #start()} is called on the current thread.
 */
public class SqlCapture implements StatementInspector {

    private static final ThreadLocal<List<String>> RECORDED = new ThreadLocal<>();

    /** Starts recording on this thread (and forgets older SQL). */
    public static void start() {
        RECORDED.set(new ArrayList<>());
    }

    /**
     * Stops recording on this thread.
     *
     * @return the SQL recorded since {@link #start()}, in order
     */
    public static List<String> stop() {
        List<String> out = RECORDED.get();
        RECORDED.remove();
        return out == null ? List.of() : out;
    }

    /**
     * Records the SQL and returns it unchanged.
     *
     * @param sql the SQL Hibernate is about to run
     * @return the same SQL
     */
    @Override
    public String inspect(String sql) {
        List<String> list = RECORDED.get();
        if (list != null) list.add(sql);
        return sql;
    }
}
