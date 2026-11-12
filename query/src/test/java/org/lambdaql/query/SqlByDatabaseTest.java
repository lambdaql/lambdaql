package org.lambdaql.query;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.lambdaql.engine.render.RenderedQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Records the SQL that Hibernate makes for each manual example, on the database chosen with {@code -Dlql.db}
 * (H2 when not set), and compares it with {@code src/test/resources/expected-sql/<db>.sql}.
 * <p>
 * The example names are the region names in {@link SelectQueryTest} and {@link SortQueryTest};
 * {@code #2} marks the second query of a region. The queries here are the same as in those regions.
 * The manual copies the SQL from the expected files.
 * <p>
 * To write the files again after a change: {@code ./gradlew :query:test --tests '*SqlByDatabaseTest' -Dlql.sql.update=true}
 * (add {@code -Dlql.db=mariadb|oracle} for the other databases).
 */
@SpringBootTest(classes = SqlByDatabaseTest.Context.class)
@ActiveProfiles("test")
class SqlByDatabaseTest {

    @Autowired
    private EntityManagerFactory emf;

    private QueryBuilder qb;
    /** A field read by a lambda (the manual's "this capture" example). */
    private int defaultStatus = 1;
    private final Map<String, String> out = new LinkedHashMap<>();

    @Test
    void sqlOfManualExamples() throws IOException {
        qb = new QueryBuilder(emf);
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        em.createQuery("delete from Order").executeUpdate();     // empty tables: only the main SELECT runs
        em.createQuery("delete from Customer").executeUpdate();
        em.getTransaction().commit();
        em.close();

        // ---- SelectQueryTest
        ex("to-hql", qb.from(Order.class).where(o -> o.getStatus() == 1 && o.getPrice() > 100));
        double minPrice = 100;
        ex("to-hql#2", qb.from(Order.class).where(o -> o.getStatus() == 1 && o.getPrice() > minPrice));
        ex("where-many", qb.from(Order.class).where(o -> o.getStatus() == 1 || o.getStatus() == 3).where(o -> o.isActive()));
        ex("string-literal", qb.from(Order.class).where(o -> o.getDescription().equals("O'Brien")));
        String d = null;
        ex("null-safe", qb.from(Order.class).where(o -> Objects.equals(o.getDescription(), d)));
        String d2 = "a";
        ex("null-safe#2", qb.from(Order.class).where(o -> Objects.equals(o.getDescription(), d2)));
        String name = "Kim";
        ex("inner-join", qb.from(Order.class).where(o -> o.getCustomer().getName().equals(name)));
        ex("null-guard", qb.from(Order.class).where(o -> o.getCustomer() == null || o.getCustomer().getName().equals(name)));
        ex("null-guard#2", qb.from(Order.class).where(o -> o.getCustomer() != null && o.getCustomer().getName().equals(name)));
        Double min = null;
        ex("captured-null-guard", qb.from(Order.class).where(o -> min == null || o.getPrice() > min));
        Double min2 = 100.0;
        ex("captured-null-guard#2", qb.from(Order.class).where(o -> min2 == null || o.getPrice() > min2));
        ex("fk-path", qb.from(Order.class).where(o -> o.getCustomer().getId() == 1));
        ex("no-association", qb.from(Order.class).where(o -> o.getCustomer() == null));
        List<Short> statuses = List.of((short) 2, (short) 3);
        ex("in", qb.from(Order.class).where(o -> statuses.contains(o.getStatus())));
        ex("boolean", qb.from(Order.class).where(o -> o.isActive()));
        ex("boolean#2", qb.from(Order.class).where(o -> !o.isActive()));
        ex("enum", qb.from(Order.class).where(o -> o.getKind() == Order.Kind.RUSH));
        List<Long> ids = List.of(1L, 2L);
        ex("not-in", qb.from(Order.class).where(o -> !ids.contains(o.getId())));
        List<String> descs = java.util.Arrays.asList("a", "b");
        ex("not-in#2", qb.from(Order.class).where(o -> !descs.contains(o.getDescription())));

        // ---- other implemented examples in the manual (README, 01, 02, 03, 09)
        long minPriceL = 100;
        ex("overview-first", qb.from(Order.class)
                .where(o -> o.getStatus() == 1 || o.getCustomer().getName().equals("Kim"))
                .where(o -> o.getPrice() > minPriceL));
        int status = 1;
        ex("start-captured", qb.from(Order.class).where(o -> o.getStatus() == status && o.getPrice() >= minPrice));
        ex("start-many-where", qb.from(Order.class).where(o -> o.isActive()).where(o -> o.getStatus() == 1 || o.getStatus() == 2));
        ex("many-where", qb.from(Order.class).where(o -> o.getStatus() == 1).where(o -> o.isActive()));
        ex("this-capture", qb.from(Order.class).where(o -> o.getStatus() == defaultStatus));
        ex("path-literal", qb.from(Order.class).where(o -> o.getCustomer().getName().equals("Kim")));
        ex("fk-literal", qb.from(Order.class).where(o -> o.getCustomer().getId() == 5));
        ex("null-guard-name-null", qb.from(Order.class).where(o -> o.getCustomer() == null || o.getCustomer().getName() == null));

        // ---- SortQueryTest
        ex("sort-basic", qb.from(Order.class).where(o -> o.isActive()).orderBy((o, by) -> by.desc(o.getPrice()).asc(o.getId())));
        ex("sort-nulls", qb.from(Order.class).orderBy((o, by) -> by.asc(o.getDescription()).asc(o.getId())));
        ex("sort-nulls#2", qb.from(Order.class).orderBy((o, by) -> by.asc(o.getDescription(), false).asc(o.getId())));
        ex("sort-nulls#3", qb.from(Order.class).orderBy((o, by) -> by.desc(o.getDescription(), true).asc(o.getId())));
        Direction dir = Direction.parse("desc");
        ex("sort-dynamic", qb.from(Order.class).orderBy((o, by) -> by.sort(o.getDescription(), dir).asc(o.getId())));
        Direction dir2 = Direction.parse("asc_nulls_last");
        ex("sort-dynamic#2", qb.from(Order.class).orderBy((o, by) -> by.sort(o.getDescription(), dir2).asc(o.getId())));
        ex("sort-path", qb.from(Order.class).orderBy((o, by) -> by.asc(o.getCustomer().getName()).asc(o.getId())));
        ex("sort-path-where", qb.from(Order.class).where(o -> o.getCustomer().getName().equals(name))
                .orderBy((o, by) -> by.desc(o.getCustomer().getName()).desc(o.getPrice())));
        ex("sort-key", qb.from(Order.class).orderBy(o -> o.getPrice()));
        ex("sort-key#2", qb.from(Order.class).orderBy(Order::getPrice, Direction.DESC));
        ex("sort-page", qb.from(Order.class).orderBy((o, by) -> by.asc(o.getStatus())).limit(2).offset(2));
        ex("page-only", qb.from(Order.class).where(o -> o.isActive()).limit(2));

        // ---- HQL from the manual, given to Hibernate directly.
        // The manual's Order has a nullable Double price, so its HQL has "nulls last" for the price key;
        // the test Order has a primitive double price, so lambdaQL would leave that part out here.
        hql("readme-first", "select e0 from Order e0 where e0.status = 1 and e0.price > :p1"
                + " order by e0.price desc nulls last, e0.id asc", Map.of("p1", 100.0), 0, 20);
        hql("sort-overview", "select e0 from Order e0 left join e0.customer j1 where e0.active = true"
                + " order by e0.price desc nulls last, j1.name asc nulls first, e0.id asc", Map.of(), 40, 20);
        hql("page-design", "select e0 from Order e0 where e0.status = 1"
                + " order by e0.price desc nulls last, e0.id asc", Map.of(), 40, 20);

        compare();
    }

    // ------------------------------------------------------------------ helpers

    private void ex(String id, SelectQuery<?> q) {
        record(id, q.toHql(), q::toList);
    }

    private void ex(String id, OrderedSelectQuery<?> q) {
        record(id, q.toHql(), q::toList);
    }

    /**
     * Records the SQL of an HQL text from the manual (not made by lambdaQL in this test).
     *
     * @param id     the example name
     * @param hql    the HQL as written in the manual
     * @param params parameter values
     * @param offset rows to skip
     * @param limit  most rows
     */
    private void hql(String id, String hql, Map<String, Object> params, int offset, int limit) {
        EntityManager em = emf.createEntityManager();
        List<String> sql;
        try {
            var q = em.createQuery(hql, Order.class);
            params.forEach(q::setParameter);
            if (offset > 0) q.setFirstResult(offset);
            q.setMaxResults(limit);
            SqlCapture.start();
            q.getResultList();
        } finally {
            sql = SqlCapture.stop();
            em.close();
        }
        assertFalse(sql.isEmpty(), "no SQL for " + id);
        out.put(id, "-- HQL: " + hql + "\n-- (this HQL is given to Hibernate directly)\n" + docForm(sql.get(0)));
    }

    private void record(String id, RenderedQuery hql, Supplier<List<?>> run) {
        SqlCapture.start();
        run.get();
        List<String> sql = SqlCapture.stop();
        assertFalse(sql.isEmpty(), "no SQL for " + id);
        out.put(id, "-- HQL: " + hql.hql() + "\n" + docForm(sql.get(0)));
    }

    /**
     * Makes the SQL easy to read and the same on every run: the test table prefix {@code SQ_} is removed,
     * the select list (all columns of the entity) becomes {@code …}, and main clauses start on a new line.
     * <p>
     * Same as {@code docForm(sql, true)}.
     *
     * @param sql the SQL that Hibernate sent
     * @return the SQL for the manual
     */
    static String docForm(String sql) {
        return docForm(sql, true);
    }

    /**
     * Makes the SQL easy to read and the same on every run.
     * <ul>
     *   <li>The test table prefix {@code SQ_} is removed.</li>
     *   <li>If {@code shortSelect} is true, the select list of the main query becomes {@code …}.
     *       Use it when the query selects whole entities (the list is all columns of the entity).</li>
     *   <li>Main clauses start on a new line. Clauses inside brackets (subqueries) stay on their line.</li>
     * </ul>
     * Example:
     * <pre>{@code
     * docForm("select o1_0.id,o1_0.price from SQ_orders o1_0 where exists(select 1 from SQ_items i1_0)", true)
     * // "select …\nfrom orders o1_0\nwhere exists(select 1 from items i1_0)"
     * }</pre>
     *
     * @param sql         the SQL that Hibernate sent
     * @param shortSelect true to show the select list of the main query as {@code …}
     * @return the SQL for the manual
     */
    static String docForm(String sql, boolean shortSelect) {
        String s = sql.replace("SQ_", "").replaceAll("\\s+", " ").trim();
        if (shortSelect) {
            Matcher m = Pattern.compile("^select (.+?) from ").matcher(s);
            if (m.find()) s = "select … from " + s.substring(m.end());
        }
        return breakLines(s);
    }

    /** A main clause word. The SQL has one space before it and one after it. */
    private static final Pattern CLAUSE = Pattern.compile(
            "(select|from|left join|join|where|group by|having|order by|offset|fetch first|limit|set|values) ");

    /**
     * Puts a new line before each main clause that is not inside brackets.
     *
     * @param s SQL on one line
     * @return the SQL with one main clause per line
     */
    private static String breakLines(String s) {
        StringBuilder out = new StringBuilder(s.length() + 16);
        int depth = 0;
        boolean quoted = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\'') quoted = !quoted;
            else if (!quoted && ch == '(') depth++;
            else if (!quoted && ch == ')') depth--;
            boolean newLine = ch == ' ' && depth == 0 && !quoted && startsClause(s, i + 1);
            out.append(newLine ? '\n' : ch);
        }
        return out.toString();
    }

    /**
     * Tells if a main clause starts at this position.
     * {@code join} after {@code left}, and {@code from} after {@code delete}, {@code delete o1_0} (MariaDB)
     * or {@code distinct}, do not count.
     *
     * @param s  SQL on one line
     * @param at the position right after a space
     * @return true if a main clause starts here
     */
    private static boolean startsClause(String s, int at) {
        Matcher m = CLAUSE.matcher(s).region(at, s.length());
        if (!m.lookingAt()) return false;
        String before = s.substring(0, at - 1);
        return switch (m.group(1)) {
            case "join" -> !before.endsWith("left");
            case "from" -> !before.endsWith("delete") && !before.matches("delete \\S+") && !before.endsWith("distinct");
            default -> true;
        };
    }

    private void compare() throws IOException {
        String db = System.getProperty("lql.db", "h2").toLowerCase(Locale.ROOT);
        Path file = Path.of("src/test/resources/expected-sql/" + db + ".sql");
        StringBuilder text = new StringBuilder()
                .append("-- SQL that Hibernate 6.6 makes for the manual examples on ").append(db).append(".\n")
                .append("-- Made by SqlByDatabaseTest; the select list is shortened to … and the test table prefix is removed.\n");
        out.forEach((id, sql) -> text.append("\n-- @example ").append(id).append('\n').append(sql).append('\n'));
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
    @EntityScan("org.lambdaql.query")
    static class Context {
    }
}
