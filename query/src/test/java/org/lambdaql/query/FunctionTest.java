package org.lambdaql.query;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
