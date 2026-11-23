package org.lambdaql.query;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.lambdaql.Agg;
import org.lambdaql.engine.render.RenderedQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
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
        for (String e : List.of("Item", "Order", "Customer", "Archive", "VOrder", "IdOrder")) {
            em.createQuery("delete from " + e).executeUpdate();     // empty tables: only the main statement runs
        }
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
        SortSpec<Order> byDescription = SortSpec.of(Order.class, (o, by) -> by.desc(o.getDescription()).asc(o.getId()));
        ex("sort-spec", qb.from(Order.class).where(o -> o.isActive()).orderBy(byDescription));
        SortSpec<Order> byStatus = SortSpec.of(Order.class, (o, by) -> by.asc(o.getStatus()));
        SortSpec<Order> byPrice = SortSpec.of(Order.class, (o, by) -> by.desc(o.getPrice()));
        ex("sort-spec-then", qb.from(Order.class).orderBy(byStatus.then(byPrice)));
        SortSpec<Order> spec = SortSpec.of(Order.class, (o, by) -> by.asc(o.getDescription()).asc(o.getId()));
        ex("sort-spec-reversed", qb.from(Order.class).orderBy(spec.reversed()));

        // ---- ResultQueryTest
        OrderedSelectQuery<Order> cheapest = qb.from(Order.class).orderBy((o, by) -> by.asc(o.getPrice()));
        record("first", qb.from(Order.class).orderBy((o, by) -> by.asc(o.getPrice())).limit(1).toHql(),
                () -> cheapest.first().stream().toList());
        SelectQuery<Order> active = qb.from(Order.class).where(o -> o.isActive());
        record("first#2", qb.from(Order.class).where(o -> o.isActive()).limit(1).toHql(), () -> active.first().stream().toList());
        long id = 1;
        SelectQuery<Order> byId = qb.from(Order.class).where(o -> o.getId() == id);
        record("one", byId.toHql(), () -> byId.one().stream().toList());
        record("count", active.countHql(), () -> List.of(active.count()), false);
        SelectQuery<Order> rush = qb.from(Order.class).where(o -> o.getKind() == Order.Kind.RUSH);
        record("exists", rush.existsHql(), () -> List.of(rush.exists()), false);

        // ---- HQL from the manual, given to Hibernate directly.
        // The manual's Order has a nullable Double price, so its HQL has "nulls last" for the price key;
        // the test Order has a primitive double price, so lambdaQL would leave that part out here.
        hql("readme-first", "select e0 from Order e0 where e0.status = 1 and e0.price > :p1"
                + " order by e0.price desc nulls last, e0.id asc", Map.of("p1", 100.0), 0, 20);
        hql("sort-overview", "select e0 from Order e0 left join e0.customer j1 where e0.active = true"
                + " order by e0.price desc nulls last, j1.name asc nulls first, e0.id asc", Map.of(), 40, 20);
        hql("page-design", "select e0 from Order e0 where e0.status = 1"
                + " order by e0.price desc nulls last, e0.id asc", Map.of(), 40, 20);

        // ---- DmlQueryTest (run on empty tables, in this order)
        short st = 5;
        ExecutableUpdate<Order> update = qb.update(Order.class)
                .set(o -> { o.setStatus(st); o.setPrice(o.getPrice() * 2); }).where(o -> o.getStatus() == 1);
        record("update", update.toHql(), () -> List.of(update.execute()), false);
        ExecutableUpdate<Order> updateValue = qb.update(Order.class).set(o -> org.lambdaql.Dml.value(o.getDescription(), "done"))
                .where(o -> o.getStatus() == 2);
        record("update-value", updateValue.toHql(), () -> List.of(updateValue.execute()), false);
        ExecutableUpdate<Order> updatePath = qb.update(Order.class).set(o -> o.setCustName("K"))
                .where(o -> o.getCustomer().getName().equals("Kim"));
        record("update-path", updatePath.toHql(), () -> List.of(updatePath.execute()), false);
        ExecutableUpdate<Order> copy = qb.update(Order.class).set(o -> o.setCustName(o.getCustomer().getName())).all();
        record("update-path#2", copy.toHql(), () -> List.of(copy.execute()), false);
        ExecutableUpdate<VOrder> versioned = qb.update(VOrder.class).set(v -> v.setStatus(2)).where(v -> v.getId() == 1L);
        record("update-versioned", versioned.toHql(), () -> List.of(versioned.execute()), false);
        ExecutableDelete<Order> delete = qb.delete(Order.class).where(o -> o.getStatus() == 3);
        record("delete", delete.toHql(), () -> List.of(delete.execute()), false);
        ExecutableDelete<Order> deletePath = qb.delete(Order.class).where(o -> o.getCustomer().getName() == null);
        record("delete#2", deletePath.toHql(), () -> List.of(deletePath.execute()), false);
        ExecutableInsert<Archive> insert = qb.insert(Archive.class)
                .values(a -> { a.setId(100L); a.setPrice(9.5); a.setCustomerName("Kim"); });
        record("insert", insert.toHql().get(0), () -> List.of(insert.execute()), false);
        List<DmlQueryTest.Line> lines = List.of(new DmlQueryTest.Line(201, 1.0), new DmlQueryTest.Line(202, 2.0));
        ExecutableInsert<Archive> insertList = qb.insert(Archive.class).values(lines, (a, l) -> { a.setId(l.id()); a.setPrice(l.price()); });
        record("insert-list", insertList.toHql().get(0), () -> List.of(insertList.execute()), false);
        ExecutableInsert<Archive> insertSelect = qb.insert(Archive.class).from(Order.class).where(o -> o.getStatus() == 1)
                .select((a, o) -> { a.setId(o.getId()); a.setPrice(o.getPrice()); a.setCustomerName(o.getCustomer().getName()); });
        record("insert-select", insertSelect.toHql().get(0), () -> List.of(insertSelect.execute()), false);
        ExecutableInsert<Archive> upsert = qb.upsert(Archive.class).values(a -> { a.setId(100L); a.setPrice(2.0); });
        record("upsert", upsert.toHql().get(0), () -> List.of(upsert.execute()), false);
        ExecutableInsert<Archive> onConflict = qb.insert(Archive.class).values(a -> { a.setId(100L); a.setPrice(5.0); })
                .onConflict(a -> a.getId()).update(a -> a.setPrice(a.getPrice() + 5.0));
        record("on-conflict", onConflict.toHql().get(0), () -> List.of(onConflict.execute()), false);
        ExecutableInsert<Archive> ignore = qb.insert(Archive.class).values(a -> { a.setId(300L); a.setPrice(1.0); })
                .onConflict(a -> a.getId()).ignore();
        record("insert-ignore", ignore.toHql().get(0), () -> List.of(ignore.execute()), false);

        // ---- KotlinQueryTest, safe call ?. and !! in where (D54): Java cannot write ?., so the HQL that those regions
        // assert is run as it is
        kotlinHql("safe-call-or", "select e0 from Order e0 left join e0.customer j1 where e0.status = 1 or j1.name = 'Kim'", false);
        kotlinHql("safe-call-null", "select e0 from Order e0 left join e0.customer j1 where j1.name is null", false);
        kotlinHql("safe-call-and", "select e0 from Order e0 join e0.customer j1 where j1.name = 'Kim' and e0.price > 100.0", false);
        kotlinHql("not-null-assert", "select e0 from Order e0 join e0.customer j1 where e0.status = 1 or j1.name = 'Kim'", false);
        kotlinHql("mixed-warn", "select e0 from Order e0 join e0.customer j1 where j1.name = 'Kim' or j1.grade > 3", false);
        kotlinHql("update-safe-call", "update Order e0 set e0.status = :p1"
                + " where e0.status = 1 or (select s1.name from Customer s1 where s1.id = fk(e0.customer)) = 'Kim'", true);

        // ---- SubqueryTest
        ex("sub-exists", qb.from(Order.class).where((o, sub) -> sub.from(Item.class)
                .where(i -> i.getOrder().getId() == o.getId() && i.getQty() > 10).exists()));
        ex("sub-not-exists", qb.from(Order.class).where((o, sub) -> o.getStatus() == 1
                || !sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).exists()));
        ex("sub-in", qb.from(Order.class).where((o, sub) -> sub.from(Customer.class).where(c -> c.getGrade() != null && c.getGrade() > 3)
                .select(c -> c.getId()).contains(o.getCustomer().getId())));
        ex("sub-not-in", qb.from(Order.class).where((o, sub) -> !sub.from(Customer.class).select(c -> c.getGrade()).contains((int) o.getStatus())));
        ex("sub-scalar", qb.from(Order.class).where((o, sub) -> o.getPrice() > sub.from(Order.class).avg(x -> x.getPrice())));
        ex("sub-count", qb.from(Order.class).where((o, sub) -> sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).count() >= 2));
        ex("sub-top-one", qb.from(Order.class).where((o, sub) -> o.getId().equals(sub.from(Order.class)
                .where(x -> x.getCustomer().getId() == o.getCustomer().getId())
                .orderBy((x, by) -> by.desc(x.getPrice())).limit(1).select(x -> x.getId()).first())));
        ex("sub-nested", qb.from(Customer.class).where((c, sub) -> sub.from(Order.class).where((o, sub2) -> o.getCustomer().getId() == c.getId()
                && sub2.from(Item.class).where(i -> i.getOrder().getId() == o.getId() && i.getQty() > 10).exists()).exists()));

        // ---- ProjectionTest (the select list is the point, so it is kept)
        val("value", qb.from(Order.class).where(o -> o.getStatus() == 1).orderBy((o, by) -> by.asc(o.getPrice()))
                .select(o -> o.getPrice()));
        val("record", qb.from(Order.class).select(o -> new ProjectionTest.OrderView(o.getId(), o.getCustomer().getName(), o.getPrice())));
        val("group-by", qb.from(Order.class).groupBy(o -> o.getCustomer().getId()).having(o -> Agg.sum(o.getPrice()) > 200)
                .select(o -> new ProjectionTest.Stat(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice()))));
        val("group-many", qb.from(Order.class).where(o -> o.getCustomer() != null)
                .groupBy(o -> List.of(o.getCustomer().getId(), o.getStatus()))
                .select(o -> new ProjectionTest.KeyCount(o.getCustomer().getId(), o.getStatus(), Agg.count())));
        val("total", qb.from(Order.class).where(o -> o.getStatus() == 1)
                .select(o -> new ProjectionTest.Total(Agg.count(), Agg.avg(o.getPrice()))));
        val("distinct", qb.from(Order.class).orderBy((o, by) -> by.desc(o.getPrice())).select(o -> o.getPrice()).distinct());
        ValueQuery<Double> paged = qb.from(Order.class).orderBy((o, by) -> by.desc(o.getPrice())).select(o -> o.getPrice()).limit(2).offset(1);
        record("value-page", paged.toHql(), paged::toList, false);
        ExecutableInsert<Summary> insertGroup = qb.insert(Summary.class).from(Order.class).where(o -> o.getCustomer() != null)
                .groupBy(o -> o.getCustomer().getId()).having(o -> Agg.count() >= 2)
                .select((s, o) -> { s.setCustomerId(o.getCustomer().getId()); s.setTotal(Agg.sum(o.getPrice())); s.setCnt(Agg.count()); });
        record("insert-group", insertGroup.toHql().get(0), () -> List.of(insertGroup.execute()), false);

        ExecutableDelete<Order> deleteLimit = qb.delete(Order.class).where(o -> o.getStatus() == 1)
                .orderBy((o, by) -> by.asc(o.getPrice())).limit(1);
        record("delete-limit", deleteLimit.toHql(), () -> List.of(deleteLimit.execute()), false);
        ExecutableDelete<Order> deleteSub = qb.delete(Order.class)
                .where((o, sub) -> !sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).exists());
        record("dml-subquery", deleteSub.toHql(), () -> List.of(deleteSub.execute()), false);
        ExecutableUpdate<Order> updateLimit = qb.update(Order.class).set(o -> o.setDescription("top"))
                .where(o -> o.getCustomer().getName().equals("Kim")).orderBy((o, by) -> by.desc(o.getPrice())).limit(1);
        record("update-limit", updateLimit.toHql(), () -> List.of(updateLimit.execute()), false);

        compare();
    }

    // ------------------------------------------------------------------ helpers

    private void val(String id, ValueQuery<?> q) {
        record(id, q.toHql(), q::toList, false);
    }

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

    /**
     * Records the SQL of an HQL string that a Kotlin region asserts (Kotlin-only forms such as ?. in where, D54).
     *
     * @param id     the example name, the same as the region name
     * @param hql    the HQL, copied from the Kotlin test
     * @param update {@code true} for UPDATE/DELETE (run in a transaction; {@code :p1} gets the value 7)
     */
    private void kotlinHql(String id, String hql, boolean update) {
        RenderedQuery rendered = new RenderedQuery(hql, update ? Map.of("p1", (short) 7) : Map.of());
        record(id, rendered, () -> {
            EntityManager em = emf.createEntityManager();
            try {
                if (!update) return em.createQuery(hql, Order.class).getResultList();
                em.getTransaction().begin();
                int n = em.createQuery(hql).setParameter("p1", (short) 7).executeUpdate();
                em.getTransaction().commit();
                return List.of(n);
            } finally {
                em.close();
            }
        }, !update);
    }

    private void record(String id, RenderedQuery hql, Supplier<List<?>> run) {
        record(id, hql, run, true);
    }

    /**
     * Runs a query and records its first SQL statement.
     *
     * @param shortSelect {@code false} to keep the select list (for {@code count()} and {@code exists()},
     *                    where the select list is the point)
     */
    private void record(String id, RenderedQuery hql, Supplier<List<?>> run, boolean shortSelect) {
        SqlCapture.start();
        run.get();
        List<String> sql = SqlCapture.stop();
        assertFalse(sql.isEmpty(), "no SQL for " + id);
        out.put(id, "-- HQL: " + hql.hql() + "\n" + docForm(sql.get(0), shortSelect));
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
        // lql.sql.dir: another folder for the SQL of another Hibernate version (testBoot3 uses expected-sql/hibernate6)
        Path file = Path.of(System.getProperty("lql.sql.dir", "src/test/resources/expected-sql"), db + ".sql");
        StringBuilder text = new StringBuilder()
                .append("-- SQL that Hibernate ").append(hibernateVersion()).append(" makes for the manual examples on ").append(db).append(".\n")
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
    @AutoConfigurationPackage(basePackages = "org.lambdaql.query")
    static class Context {
    }

    /** The major and minor version of the Hibernate on the class path, for example {@code 7.4}. */
    private static String hibernateVersion() {
        String[] parts = org.hibernate.Version.getVersionString().split("\\.");
        return parts[0] + "." + parts[1];
    }
}
