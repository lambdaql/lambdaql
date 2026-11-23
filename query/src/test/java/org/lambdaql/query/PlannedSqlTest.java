package org.lambdaql.query;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Records the SQL that Hibernate makes for the <b>planned</b> HQL in the manual (chapters 5 to 8),
 * on the database chosen with {@code -Dlql.db} (H2 when not set), and compares it with
 * {@code src/test/resources/expected-sql/planned-<db>.sql}.
 * <p>
 * lambdaQL cannot make these queries yet. So this test gives the planned HQL to Hibernate directly.
 * The manual copies the SQL from the expected files. {@link SqlByDatabaseTest} does the same for the
 * examples that lambdaQL already runs.
 * <p>
 * Each block in the file is:
 * <pre>{@code
 * -- @example 05-exists
 * -- HQL: select o from Order o where exists (...)
 * select …
 * from orders o1_0
 * where exists(...)
 * }</pre>
 * Instead of the SQL, a block can have:
 * <ul>
 *   <li>{@code -- NOT VALID HQL: <message>}: Hibernate rejected the HQL. There is no SQL.</li>
 *   <li>{@code -- DB ERROR: <message>} and then the SQL: Hibernate made the SQL, but the database rejected it.</li>
 * </ul>
 * Notes:
 * <ul>
 *   <li>The tables are empty, and every statement runs in a transaction that is rolled back.
 *       So only the main statement runs, and the database does not change.</li>
 *   <li>The DB function {@code FN_DISCOUNT} of chapter 8 exists in the database as {@code SQ_FN_DISCOUNT}
 *       (the test prefix). The test makes it at the start and drops it at the end.
 *       The HQL in the file shows the name of the manual.</li>
 *   <li>Parameters get dummy values. They do not change the SQL.</li>
 * </ul>
 * To write the files again after a change:
 * {@code ./gradlew :query:test --tests '*PlannedSqlTest' -Dlql.sql.update=true}
 * (add {@code -Dlql.db=mariadb|oracle} for the other databases).
 */
@SpringBootTest(classes = PlannedSqlTest.Context.class)
@ActiveProfiles("test")
public class PlannedSqlTest {

    /** Name of the DB function in the manual. */
    private static final String FN = "'FN_DISCOUNT'";

    /** Name of the same function in the test database (with the test prefix). */
    private static final String DB_FN = "'SQ_FN_DISCOUNT'";

    @Autowired
    private EntityManagerFactory emf;

    @Autowired
    private DataSource dataSource;

    private String db;

    /** Examples whose HQL is written in the manual. */
    private final Map<String, String> fromManual = new LinkedHashMap<>();

    /** Examples where the manual shows only Java code; the HQL here follows the rules of the manual. */
    private final Map<String, String> derived = new LinkedHashMap<>();

    /** The map that {@link #record} writes to. */
    private Map<String, String> out = fromManual;

    /** How a statement runs, and how its SQL is shown. */
    private enum Kind {
        /** A select of whole entities: run with getResultList, the select list becomes {@code …}. */
        ENTITY,
        /** A select of values: run with getResultList, the select list is kept. */
        VALUES,
        /** An update, delete or insert: run with executeUpdate. */
        DML
    }

    /**
     * Runs every planned example and writes or compares the expected file.
     *
     * @throws Exception if the DB function cannot be made or the file cannot be used
     */
    @Test
    void sqlOfPlannedExamples() throws Exception {
        db = System.getProperty("lql.db", "h2").toLowerCase(Locale.ROOT);
        clearTables();
        createFunction();
        try {
            manualExamples();
            out = derived;
            derivedExamples();
        } finally {
            dropFunction();
        }
        compare();
    }

    /** Examples whose HQL is written in the manual (as a full query, or as a part that has only one full form). */
    private void manualExamples() {
        // ---- 05-subquery.md
        // 5.2 EXISTS
        record("05-exists", Kind.ENTITY,
                "select o from Order o where exists (select 1 from Item i where i.order.id = o.id and i.qty > 10)");
        // 5.3 NOT EXISTS
        record("05-not-exists-or", Kind.ENTITY,
                "select o from Order o where o.status = 1 or not exists (select 1 from Item i where i.order.id = o.id)");
        // 5.4 IN
        record("05-in-subquery", Kind.ENTITY,
                "select o from Order o where o.customer.id in (select c.id from Customer c where c.grade > 3)");
        // 5.5 NOT IN is written as NOT EXISTS
        record("05-not-in", Kind.ENTITY,
                "select o from Order o where not exists (select 1 from Customer c where c.grade > 3 and c.id = o.customer.id)");
        // 5.6 Correlated subqueries (the manual shows the exists part; minQty = 10)
        record("05-correlated-param", Kind.ENTITY,
                "select o from Order o where exists (select 1 from Item i where i.order.id = o.id and i.qty > :p1)", 10);
        // 5.7 Scalar subqueries
        record("05-scalar-avg", Kind.ENTITY,
                "select o from Order o where o.price > (select avg(x.price) from Order x)");
        // 5.8 Nested subqueries
        record("05-nested", Kind.ENTITY,
                "select c from Customer c where exists (select 1 from Order o where o.customer.id = c.id"
                        + " and exists (select 1 from Item i where i.order.id = o.id and i.qty > 10))");

        // ---- 06-select-group.md
        // 6.2 Select one value
        record("06-select-value", Kind.VALUES, "select o.price from Order o where o.status = 1");
        record("06-select-path", Kind.VALUES, "select c1.name from Order o left join o.customer c1");
        // 6.3 Select a record or DTO
        record("06-dto", Kind.VALUES, "select o.id, c1.name, o.price from Order o left join o.customer c1");
        // 6.4 Aggregate over all rows
        record("06-aggregate", Kind.VALUES, "select count(*), avg(o.price) from Order o where o.status = 1");
        // 6.5 Group by, one key
        record("06-group-by", Kind.VALUES,
                "select o.customer.id, count(*), sum(o.price) from Order o group by o.customer.id");
        // 6.6 Having
        record("06-group-having", Kind.VALUES,
                "select o.customer.id, count(*), sum(o.price) from Order o group by o.customer.id having sum(o.price) > 1000");
        // 6.7 Derived table. The manual shows a shape ("select ... from (...) r"); the column aliases are
        // the components of record Stat (rule J5).
        record("06-derived", Kind.VALUES,
                "select r.customerId, r.cnt, r.total from (select o.customer.id as customerId, count(*) as cnt,"
                        + " sum(o.price) as total from Order o group by o.customer.id) r where r.total > 100");
        // 6.8 CTE. The manual shows a shape; the aliases are the components of record Stat (rule J5).
        record("06-cte", Kind.VALUES,
                "with t as (select o.customer.id as customerId, count(*) as cnt, sum(o.price) as total"
                        + " from Order o group by o.customer.id) select x.customerId, x.cnt, x.total from t x where x.total > 100");

        // ---- 07-2-update.md
        // Basic form (st = 2)
        record("07-update", Kind.DML,
                "update Order o set o.status = :p1, o.price = o.price * 1.1 where o.status = 1", (short) 2);
        // Add to a value, default for NULL (with all())
        record("07-update-coalesce", Kind.DML, "update Order o set o.price = coalesce(o.price, 0.0) + 1");
        // Embedded fields (city = "Seoul", id = 1)
        record("07-update-embedded", Kind.DML,
                "update Order o set o.address.city = :p1 where o.id = :p2", "Seoul", 1L);
        // Change an association (foreign key). Customer.id is an int in the test entity.
        record("07-update-fk", Kind.DML, "update Order o set o.customer.id = :p1 where o.id = :p2", 1, 1L);
        record("07-update-fk-null", Kind.DML, "update Order o set o.customer = null where o.id = :p1", 1L);
        // A value from another table (rule U9)
        record("07-update-other-table", Kind.DML,
                "update Order o set o.custName = (select c.name from Customer c where c.id = o.customer.id)");

        // ---- 07-4-delete.md (HQL for the second example)
        record("07-delete-exists", Kind.DML,
                "delete from Order o where exists (select 1 from Customer c where c.id = o.customer.id and c.name is null)");

        // ---- 07-5-insert.md: Insert ... select
        record("07-insert-select", Kind.DML,
                "insert into Archive (id, price, customerName) select o.id, o.price, c.name"
                        + " from Order o left join o.customer c where o.status = 9");

        // ---- 08-procedures-functions.md
        // 8.7 / 8.9 DB function on a column
        record("08-function", Kind.ENTITY,
                "select o from Order o where function('FN_DISCOUNT' as Double, o.price) > 10");
        // 8.8 Call a DB function alone: the MariaDB / H2 path (price = 100.0)
        record("08-function-alone", Kind.VALUES, "select function('FN_DISCOUNT' as Double, :p1)", 100.0);
    }

    /**
     * Examples where the manual shows only Java code. The HQL here is written from that code
     * and the rules of the manual. The author should check it.
     */
    private void derivedExamples() {
        // 5.7 Top-1 scalar subquery. o.getId().equals(...) is "=". The correlation compares two nullable FKs
        // (Long == Long), so it is null-safe (R2, Q5). Sort: desc is "nulls last", and the id tie-breaker is added (chapter 4).
        record("05-scalar-top1", Kind.ENTITY,
                "select o from Order o where o.id = (select x.id from Order x where x.customer.id is not distinct from o.customer.id"
                        + " order by x.updateAt desc nulls last, x.id asc limit 1)");

        // 6.9 rule J6 says "with t (...) as (...)": a CTE with a column list
        record("06-cte-columns", Kind.VALUES,
                "with t (customerId, cnt, total) as (select o.customer.id, count(*), sum(o.price)"
                        + " from Order o group by o.customer.id) select x.customerId, x.cnt, x.total from t x where x.total > 100");

        // 7.2 Values: null, date, boolean (now = 2026-01-01, id = 1). o.id is an @Id, so "=" (Q5).
        record("07-update-values", Kind.DML,
                "update Order o set o.description = null, o.updateAt = :p1, o.active = true where o.id = :p2",
                LocalDateTime.of(2026, 1, 1, 0, 0), 1L);
        // 7.2 Add to a value
        record("07-update-add", Kind.DML, "update Order o set o.cnt = o.cnt + 1 where o.status = 1");
        // 7.2 Embedded fields: o.setAddress(addr) sets all columns of the embedded value
        record("07-update-embedded-all", Kind.DML,
                "update Order o set o.address = :p1 where o.id = :p2", new Address("Seoul", "04524"), 1L);
        // 7.2 An aggregate from a subquery
        record("07-update-sum", Kind.DML,
                "update Order o set o.total = (select sum(i.price) from Item i where i.order.id = o.id) where o.status = 1");
        // 7.2 Where with paths (rule U9: a correlated exists) and subqueries
        record("07-update-where-path", Kind.DML,
                "update Order o set o.status = 0 where exists (select 1 from Customer c where c.id = o.customer.id and c.grade > 3)");
        record("07-update-not-exists", Kind.DML,
                "update Order o set o.status = 9 where not exists (select 1 from Item i where i.order.id = o.id)");
        // 7.2 Optimistic locking (rule U15; id = 1, v = 0)
        record("07-update-versioned", Kind.DML,
                "update versioned VOrder o set o.status = 8 where o.id = :p1 and o.version = :p2", 1L, 0L);

        // 7.4 Delete: the third example, and delete by a list of ids
        record("07-delete-not-exists", Kind.DML,
                "delete from Order o where not exists (select 1 from Item i where i.order.id = o.id)");
        record("07-delete-ids", Kind.DML, "delete from Item i where i.id in (:p1)", List.of(1L, 2L, 3L));

        // 7.5 Generated id (p = 100.0), and insert ... select with aggregates
        record("07-insert-generated", Kind.DML, "insert into IdOrder (price, status) values (:p1, 1)", 100.0);
        record("07-insert-group", Kind.DML,
                "insert into Summary (customerId, total, cnt) select o.customer.id, sum(o.price), count(*)"
                        + " from Order o group by o.customer.id");

        // 8.7 Use DB functions in queries (base = 100.0)
        record("08-function-value-side", Kind.ENTITY,
                "select o from Order o where o.price > function('FN_DISCOUNT' as Double, :p1)", 100.0);
        record("08-function-select", Kind.VALUES,
                "select o.id, c1.name, function('FN_DISCOUNT' as Double, o.price) from Order o left join o.customer c1");
        record("08-function-update", Kind.DML, "update Order o set o.price = function('FN_DISCOUNT' as Double, o.price)");
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Runs one HQL statement and keeps its first SQL statement (or the error) under the id.
     * The statement runs in a transaction that is always rolled back.
     *
     * @param id     the example id, for example {@code 05-exists}
     * @param kind   how to run the statement and show the SQL
     * @param hql    the planned HQL, as in the manual
     * @param values the values of {@code :p1}, {@code :p2}, ... in order
     */
    private void record(String id, Kind kind, String hql, Object... values) {
        EntityManager em = emf.createEntityManager();
        RuntimeException error = null;
        List<String> sql;
        em.getTransaction().begin();
        SqlCapture.start();
        try {
            Query q = em.createQuery(hql.replace(FN, DB_FN));
            for (int i = 0; i < values.length; i++) q.setParameter("p" + (i + 1), values[i]);
            if (kind == Kind.DML) q.executeUpdate();
            else q.getResultList();
        } catch (RuntimeException e) {
            error = e;
        } finally {
            sql = SqlCapture.stop();
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            em.close();
        }
        StringBuilder text = new StringBuilder("-- HQL: ").append(hql).append('\n');
        if (sql.isEmpty()) {
            assertNotNull(error, "no SQL and no error for " + id);
            text.append("-- NOT VALID HQL: ").append(message(error, hql));
        } else {
            if (error != null) text.append("-- DB ERROR: ").append(message(error, hql)).append('\n');
            // Hibernate can write the function name in lower case (sq_fn_discount), so docForm does not remove the prefix.
            text.append(SqlByDatabaseTest.docForm(sql.get(0), kind == Kind.ENTITY).replaceAll("(?i)\\bsq_(?=fn_)", ""));
        }
        out.put(id, text.toString());
    }

    /**
     * Makes a short, stable one-line message: the deepest cause that has a message, its first line,
     * without the HQL that Hibernate repeats and without the MariaDB connection number.
     *
     * @param e   the error
     * @param hql the HQL of the manual
     * @return for example {@code SQLSyntaxErrorException: ORA-00907: missing right parenthesis}
     */
    private static String message(Throwable e, String hql) {
        Throwable found = e;
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t.getMessage() != null && !t.getMessage().isBlank()) found = t;
        }
        String m = String.valueOf(found.getMessage()).lines().findFirst().orElse("").trim();
        m = m.replace(" [" + hql.replace(FN, DB_FN) + "]", "")
                .replaceAll("^\\(conn=\\d+\\) ", "")
                .replaceAll(";? ?SQL statement:$", "");
        return found.getClass().getSimpleName() + ": " + m;
    }

    /** Deletes all rows, children first. Then only the main statement of each example runs. */
    private void clearTables() {
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        for (String entity : List.of("Item", "Order", "Customer", "VOrder", "IdOrder", "Archive", "Summary")) {
            em.createQuery("delete from " + entity).executeUpdate();
        }
        em.getTransaction().commit();
        em.close();
    }

    /**
     * Makes the DB function {@code SQ_FN_DISCOUNT(price)} that returns {@code price * 0.9}.
     *
     * @throws SQLException if the database rejects it
     */
    private void createFunction() throws SQLException {
        dropFunction();
        jdbc(switch (db) {
            case "h2" -> "create alias SQ_FN_DISCOUNT for 'org.lambdaql.query.PlannedSqlTest.discount'";
            case "mariadb" -> "create function SQ_FN_DISCOUNT(p double) returns double deterministic return p * 0.9";
            case "oracle" -> "create or replace function SQ_FN_DISCOUNT(p number) return number deterministic"
                    + " is begin return p * 0.9; end;";
            case "mysql" -> "create function SQ_FN_DISCOUNT(p double) returns double deterministic no sql return p * 0.9";
            case "postgresql" -> "create function SQ_FN_DISCOUNT(p double precision) returns double precision language plpgsql"
                    + " immutable as 'begin return p * 0.9; end'";
            default -> throw new IllegalStateException("unknown lql.db: " + db);
        });
    }

    /**
     * Drops the DB function of {@link #createFunction()} if it exists.
     *
     * @throws SQLException if the database rejects it
     */
    private void dropFunction() throws SQLException {
        jdbc(switch (db) {
            case "h2" -> "drop alias if exists SQ_FN_DISCOUNT";
            case "postgresql" -> "drop function if exists SQ_FN_DISCOUNT(double precision)";
            default -> "drop function if exists SQ_FN_DISCOUNT";
        });
    }

    /**
     * Runs one SQL statement with plain JDBC (not recorded by {@link SqlCapture}).
     *
     * @param sql the statement
     * @throws SQLException if the database rejects it
     */
    private void jdbc(String sql) throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    /**
     * The Java code of the H2 function {@code SQ_FN_DISCOUNT} ({@code create alias}).
     * Only H2 calls it.
     * <p>
     * Example:
     * <pre>{@code
     * discount(100.0)   // 90.0
     * discount(null)    // null
     * }</pre>
     *
     * @param price a price, or {@code null}
     * @return 90 % of the price, or {@code null}
     */
    public static Double discount(Double price) {
        return price == null ? null : price * 0.9;
    }

    /**
     * Writes the expected file ({@code -Dlql.sql.update=true}) or compares with it.
     *
     * @throws IOException if the file cannot be read or written
     */
    private void compare() throws IOException {
        // lql.sql.dir: another folder for the SQL of another Hibernate version (testBoot3 uses expected-sql/hibernate6)
        Path file = Path.of(System.getProperty("lql.sql.dir", "src/test/resources/expected-sql"), "planned-" + db + ".sql");
        StringBuilder text = new StringBuilder()
                .append("-- SQL that Hibernate ").append(hibernateVersion()).append(" makes on ").append(db)
                .append(" for the PLANNED HQL in the manual (lambdaQL does not make these queries yet).\n")
                .append("-- Made by PlannedSqlTest: the HQL is given to Hibernate directly. The select list of entity queries\n")
                .append("-- is shortened to …, and the test prefix SQ_ is removed.\n")
                .append("-- \"NOT VALID HQL\": Hibernate rejected the HQL. \"DB ERROR\": Hibernate made the SQL, but the database rejected it.\n");
        fromManual.forEach((id, sql) -> text.append("\n-- @example ").append(id).append('\n').append(sql).append('\n'));
        text.append("\n-- ---- The examples below have no HQL in the manual (it shows only the Java code).\n")
                .append("-- ---- PlannedSqlTest wrote their HQL from that code and the rules of the manual.\n");
        derived.forEach((id, sql) -> text.append("\n-- @example ").append(id).append('\n').append(sql).append('\n'));
        if (Boolean.getBoolean("lql.sql.update")) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, text, StandardCharsets.UTF_8);
            return;
        }
        assertEquals(Files.readString(file, StandardCharsets.UTF_8), text.toString(),
                "SQL changed on " + db + ". Check the change, then run again with -Dlql.sql.update=true");
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = "org.lambdaql.query")
    static class Context {
    }

    /** The major and minor version of the Hibernate on the class path, for example {@code 7.4}. */
    private static String hibernateVersion() {
        String[] parts = org.hibernate.Version.getVersionString().split("\\.");
        return parts[0] + "." + parts[1];
    }
}
