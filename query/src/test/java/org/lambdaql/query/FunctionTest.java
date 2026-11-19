package org.lambdaql.query;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lambdaql.NonSargableException;
import org.junit.jupiter.api.TestInstance;
import org.lambdaql.DbOnlyException;
import org.lambdaql.LambdaQLException;
import org.lambdaql.SqlFunction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * End-to-end tests for database functions in queries (ROADMAP M11, D31): {@code @SqlFunction} static methods.
 * The functions {@code SQ_FN_DISCOUNT} and {@code SQ_FN_IS_GOLD} are created for the test and dropped at the end.
 * The region markers are used by Javadoc snippet tags.
 */
@SpringBootTest(classes = FunctionTest.Context.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FunctionTest {

    // @start region="declare"
    /** Database functions: static methods of your own interface. The bodies only throw. */
    public interface OrderFns {
        /** {@code SQ_FN_DISCOUNT(price)}: 90% of the price. */
        @SqlFunction("SQ_FN_DISCOUNT")
        static Double discount(Double price) {
            throw new DbOnlyException();
        }

        /** {@code SQ_FN_IS_GOLD(grade)}: a grade of 5 or more. */
        @SqlFunction("SQ_FN_IS_GOLD")
        static Boolean isGold(Integer grade) {
            throw new DbOnlyException();
        }

        /** {@code SQ_FN_NONDET(value)}: on MariaDB declared NOT DETERMINISTIC (SARGable test). */
        @SqlFunction("SQ_FN_NONDET")
        static Double nondet(Double value) {
            throw new DbOnlyException();
        }

        /** A query name with its own SQL name; registered with lambdaql.functions.classes. */
        @SqlFunction(value = "SQ_TAX", sql = "SQ_FN_DISCOUNT")
        static Double tax(Double price) {
            throw new DbOnlyException();
        }
    }
    // @end

    /** Functions that break the rules (not registered). */
    interface BadFns {
        @SqlFunction("SQ_FN_DISCOUNT")
        static Double computes(Double price) {
            return price;                                   // FN2: a body that computes a value
        }

        @SqlFunction("COALESCE")
        static Double builtin(Double price) {
            throw new DbOnlyException();                    // FN8: a Hibernate built-in name
        }

        @SqlFunction(value = "SQ_OTHER", sql = "SQ_FN_DISCOUNT")
        static Double unregistered(Double price) {
            throw new DbOnlyException();                    // its own SQL name, but not registered
        }

        @SqlFunction("SQ_FN_DISCOUNT")
        static Object anything(Double price) {
            throw new DbOnlyException();                    // a return type with no HQL type
        }
    }

    /** A row with a computed value. */
    record Priced(Long id, Double discounted) {
    }

    private static final String DB = System.getProperty("lql.db", "h2");

    private static final Map<String, List<String>> CREATE = Map.of(
            "h2", List.of(
                    "CREATE ALIAS IF NOT EXISTS SQ_FN_DISCOUNT FOR \"org.lambdaql.query.H2Functions.discount\"",
                    "CREATE ALIAS IF NOT EXISTS SQ_FN_IS_GOLD FOR \"org.lambdaql.query.H2Functions.isGold\""),
            "mariadb", List.of(
                    "CREATE OR REPLACE FUNCTION SQ_FN_DISCOUNT(p DOUBLE) RETURNS DOUBLE DETERMINISTIC RETURN p * 0.9",
                    "CREATE OR REPLACE FUNCTION SQ_FN_IS_GOLD(g INT) RETURNS BOOLEAN DETERMINISTIC RETURN g >= 5"),
            "oracle", List.of(
                    "CREATE OR REPLACE FUNCTION SQ_FN_DISCOUNT(p IN NUMBER) RETURN NUMBER DETERMINISTIC IS BEGIN RETURN p * 0.9; END;",
                    "CREATE OR REPLACE FUNCTION SQ_FN_IS_GOLD(g IN NUMBER) RETURN BOOLEAN DETERMINISTIC IS BEGIN RETURN g >= 5; END;"));

    private static final Map<String, List<String>> DROP = Map.of(
            "h2", List.of("DROP ALIAS IF EXISTS SQ_FN_DISCOUNT", "DROP ALIAS IF EXISTS SQ_FN_IS_GOLD"),
            "mariadb", List.of("DROP FUNCTION IF EXISTS SQ_FN_DISCOUNT", "DROP FUNCTION IF EXISTS SQ_FN_IS_GOLD"),
            "oracle", List.of("DROP FUNCTION SQ_FN_DISCOUNT", "DROP FUNCTION SQ_FN_IS_GOLD", "PURGE RECYCLEBIN"));

    @Autowired
    private EntityManagerFactory emf;

    private QueryBuilder qb;
    private List<Order> orders;

    @BeforeEach
    void seed() {
        qb = new QueryBuilder(emf);
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        for (String sql : CREATE.get(DB)) em.createNativeQuery(sql).executeUpdate();
        for (String e : List.of("Item", "Order", "Customer")) em.createQuery("delete from " + e).executeUpdate();
        Customer kim = new Customer(1, "Kim", null);
        kim.setGrade(5);
        Customer lee = new Customer(2, "Lee", null);
        lee.setGrade(2);
        em.persist(kim);
        em.persist(lee);
        Object[][] rows = {{50.0, kim}, {150.0, kim}, {250.0, lee}, {120.0, null}};
        for (Object[] r : rows) {
            Order o = new Order();
            o.setStatus((short) 1);
            o.setPrice((Double) r[0]);
            o.setActive(true);
            o.setCustomer((Customer) r[1]);
            em.persist(o);
        }
        em.getTransaction().commit();
        orders = em.createQuery("select o from Order o left join fetch o.customer", Order.class).getResultList();
        em.close();
    }

    @AfterAll
    void dropFunctions() {
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        for (String sql : DROP.get(DB)) em.createNativeQuery(sql).executeUpdate();
        em.getTransaction().commit();
        em.close();
    }

    private static List<Long> idsOf(List<Order> list) {
        return list.stream().map(Order::getId).sorted().toList();
    }

    private List<Long> java(Predicate<Order> p) {
        return orders.stream().filter(p).map(Order::getId).sorted().toList();
    }

    @Test
    void where() {
        // @start region="where"
        // the function call is written as function('<name>' as <type>, ...); the column is its argument
        SelectQuery<Order> q = qb.from(Order.class).where(o -> OrderFns.discount(o.getPrice()) > 100);
        assertEquals("select e0 from Order e0 where function('SQ_FN_DISCOUNT' as Double, e0.price) > 100.0", q.toHql().hql());
        // @end
        assertEquals(java(o -> o.getPrice() * 0.9 > 100), idsOf(q.toList()));
    }

    @Test
    void capturedArgument() {
        double base = 150;
        // @start region="captured"
        // a function of captured values only is still computed by the database (the value is bound)
        SelectQuery<Order> q = qb.from(Order.class).where(o -> o.getPrice() > OrderFns.discount(base));
        assertEquals("select e0 from Order e0 where e0.price > function('SQ_FN_DISCOUNT' as Double, :p1)", q.toHql().hql());
        // @end
        assertEquals(java(o -> o.getPrice() > base * 0.9), idsOf(q.toList()));
    }

    @Test
    void booleanFunction() {
        // @start region="boolean"
        SelectQuery<Order> q = qb.from(Order.class).where(o -> OrderFns.isGold(o.getCustomer().getGrade()));
        assertEquals("select e0 from Order e0 join e0.customer j1 where function('SQ_FN_IS_GOLD' as Boolean, j1.grade) = true",
                q.toHql().hql());
        // @end
        assertEquals(java(o -> o.getCustomer() != null && o.getCustomer().getGrade() >= 5), idsOf(q.toList()), q.toHql().hql());
    }

    @Test
    void selectAndSort() {
        // @start region="select"
        ValueQuery<Priced> q = qb.from(Order.class)
                .orderBy((o, by) -> by.desc(OrderFns.discount(o.getPrice())))
                .select(o -> new Priced(o.getId(), OrderFns.discount(o.getPrice())));
        assertEquals("select e0.id, function('SQ_FN_DISCOUNT' as Double, e0.price) from Order e0"
                + " order by function('SQ_FN_DISCOUNT' as Double, e0.price) desc nulls last", q.toHql().hql());
        // @end
        List<Priced> rows = q.toList();
        List<Long> expected = orders.stream().sorted(Comparator.comparing(Order::getPrice).reversed()).map(Order::getId).toList();
        assertEquals(expected, rows.stream().map(Priced::id).toList());
        for (Priced p : rows) {
            double price = orders.stream().filter(o -> o.getId().equals(p.id())).findFirst().orElseThrow().getPrice();
            assertEquals(price * 0.9, p.discounted(), 1e-9);
        }
    }

    @Test
    void update() {
        // @start region="update"
        ExecutableUpdate<Order> u = qb.update(Order.class)
                .set(o -> o.setPrice(OrderFns.discount(o.getPrice())))
                .where(o -> o.getPrice() > 100);
        assertEquals("update Order e0 set e0.price = function('SQ_FN_DISCOUNT' as Double, e0.price) where e0.price > 100.0",
                u.toHql().hql());
        // @end
        assertEquals(orders.stream().filter(o -> o.getPrice() > 100).count(), u.execute());
    }

    @Test
    void registered() {
        // @start region="registered"
        // SQ_TAX is registered at start-up (lambdaql.functions.classes) and runs SQ_FN_DISCOUNT in SQL
        SelectQuery<Order> q = qb.from(Order.class).where(o -> OrderFns.tax(o.getPrice()) > 100);
        assertEquals("select e0 from Order e0 where function('SQ_TAX' as Double, e0.price) > 100.0", q.toHql().hql());
        // @end
        assertEquals(java(o -> o.getPrice() * 0.9 > 100), idsOf(q.toList()));
    }

    @Test
    void sargableStrictAndArithmetic() {
        ch.qos.logback.classic.Logger log = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(SargableCheck.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        log.addAppender(appender);
        ch.qos.logback.classic.Level old = log.getLevel();
        log.setLevel(ch.qos.logback.classic.Level.DEBUG);
        int n = 5;
        try {
            // WARN (the default): arithmetic on one column with values can move to the value side (LQL-5012, WARN);
            // arithmetic between columns cannot, so it is only a DEBUG note (LQL-5013)
            qb.from(Order.class).where(o -> o.getStatus() * 2 > n).toHql();
            qb.from(Order.class).where(o -> o.getPrice() * o.getStatus() > n).toHql();
            assertEquals(List.of("WARN LQL-5012", "DEBUG LQL-5013"), appender.list.stream()
                    .map(e -> e.getLevel() + " " + e.getFormattedMessage().substring(1, 9)).toList(), String.valueOf(appender.list));
            // @start region="sargable-strict"
            // STRICT: what WARN would log fails the analysis (NonSargableException, LQL-3066)
            SargablePolicy.register(emf, SargableMode.STRICT);
            NonSargableException fn = assertThrows(NonSargableException.class,
                    () -> qb.from(Order.class).where(o -> OrderFns.discount(o.getTotal()) > 100).toHql());
            assertEquals("LQL-3066", fn.code());
            assertThrows(NonSargableException.class, () -> qb.from(Order.class).where(o -> o.getCnt() * 2 > n).toHql());
            // the same meaning on the value side uses the index; divide by a decimal (n / 2 is integer division)
            var moved = qb.from(Order.class).where(o -> o.getCnt() > n / 2.0).toHql();
            assertEquals("select e0 from Order e0 where e0.cnt > :p1", moved.hql());
            assertEquals(Map.of("p1", new java.math.BigDecimal("2.5")), moved.parameters());   // 2.5, never cut to 2
            // arithmetic between columns cannot move, so STRICT allows it
            qb.from(Order.class).where(o -> o.getCnt() * o.getStatus() > n).toHql();
            // @end
        } finally {
            SargablePolicy.unregister(emf);
            log.setLevel(old);
            log.detachAppender(appender);
        }
    }

    @Test
    void sargableWarnings() {
        ch.qos.logback.classic.Logger log = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(SargableCheck.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        log.addAppender(appender);
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        if (DB.equals("oracle")) em.createNativeQuery("CREATE INDEX SQ_IX_DISCOUNT ON SQ_orders (SQ_FN_DISCOUNT(price))").executeUpdate();
        if (DB.equals("mariadb")) em.createNativeQuery("CREATE OR REPLACE FUNCTION SQ_FN_NONDET(p DOUBLE) RETURNS DOUBLE NOT DETERMINISTIC RETURN p").executeUpdate();
        em.getTransaction().commit();
        try {
            double base = 100;
            // @start region="sargable"
            // the function wraps the column: an index on price cannot be used (LQL-5001 at WARN),
            // unless Oracle has a function-based index on SQ_FN_DISCOUNT(price)
            qb.from(Order.class).where(o -> OrderFns.discount(o.getPrice()) > 100).toHql();
            // the function is on the value side: fine, unless MariaDB says NOT DETERMINISTIC (LQL-5007 at WARN)
            qb.from(Order.class).where(o -> o.getPrice() > OrderFns.nondet(base)).toHql();
            // @end
            List<String> codes = appender.list.stream().map(e -> e.getFormattedMessage().substring(1, 9)).toList();
            assertEquals(DB.equals("oracle") ? List.of() : DB.equals("mariadb") ? List.of("LQL-5001", "LQL-5007") : List.of("LQL-5001"),
                    codes, String.valueOf(appender.list));
        } finally {
            log.detachAppender(appender);
            em.getTransaction().begin();
            if (DB.equals("oracle")) em.createNativeQuery("DROP INDEX SQ_IX_DISCOUNT").executeUpdate();
            if (DB.equals("mariadb")) em.createNativeQuery("DROP FUNCTION IF EXISTS SQ_FN_NONDET").executeUpdate();
            em.getTransaction().commit();
            em.close();
        }
    }

    @Test
    void rules() {
        // @start region="rules"
        // the body never runs in Java
        assertEquals("LQL-4034", assertThrows(DbOnlyException.class, () -> OrderFns.discount(1.0)).code());
        // FN2: a body that computes a value is rejected (Java and the database could give different values)
        assertEquals("LQL-3052", assertThrows(LambdaQLException.class,
                () -> qb.from(Order.class).where(o -> BadFns.computes(o.getPrice()) > 1)).code());
        // FN8: a Hibernate built-in name would silently run the built-in function
        assertEquals("LQL-3054", assertThrows(LambdaQLException.class,
                () -> qb.from(Order.class).where(o -> BadFns.builtin(o.getPrice()) > 1)).code());
        // a function with its own SQL name must be registered
        assertEquals("LQL-3058", assertThrows(LambdaQLException.class,
                () -> qb.from(Order.class).where(o -> BadFns.unregistered(o.getPrice()) > 1)).code());
        // the return type must have an HQL type
        assertEquals("LQL-3055", assertThrows(LambdaQLException.class,
                () -> qb.from(Order.class).where(o -> BadFns.anything(o.getPrice()) != null)).code());
        // @end
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @EntityScan("org.lambdaql.query")
    static class Context {
    }
}
