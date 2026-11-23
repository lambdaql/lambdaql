package org.lambdaql.query;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.lambdaql.Agg;
import org.lambdaql.DbOnlyException;
import org.lambdaql.InOut;
import org.lambdaql.SqlFunction;
import org.lambdaql.SqlProcedure;
import org.lambdaql.query.FunctionTest.OrderFns;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The examples of the Java manual, chapter 8 (procedures and database functions).
 * The procedure declarations only need to compile (ProcedureTest runs the same stubs on MariaDB and Oracle).
 * The function tests create SQ_FN_DISCOUNT on the chosen database, the same way as FunctionTest.
 */
@SpringBootTest(classes = ManualJavaFunctionTest.Context.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ManualJavaFunctionTest {

    // @start region="declare-procs"
    /** One OUT value or more: a record in the database order. */
    record Stat(Double total, Integer cnt) {
    }

    /** The INOUT value and the OUT value, in the database order. */
    record Adjusted(Integer balance, String msg) {
    }

    /** A row of a result set: columns are matched by name (customer_id or customerId). */
    record Line(Long id, Double price) {
    }

    /** A stub interface: lambdaQL reads the calls and never implements it. */
    interface OrderProcs {
        @SqlProcedure("SQ_PROC_CALC")
        Double calc(Integer customerId, Double rate);                 // IN, IN, OUT

        @SqlProcedure("SQ_PROC_STAT")
        Stat stat(Integer customerId);                                // IN, OUT, OUT

        @SqlProcedure("SQ_PROC_ADJUST")
        Adjusted adjust(Integer add, @InOut Integer balance);         // IN, INOUT, OUT

        @SqlProcedure("SQ_PROC_RECENT")
        List<Line> recent(Integer customerId);                        // a result set (Oracle: a REF CURSOR)

        @SqlProcedure("SQ_PROC_PURGE")
        void purge(Integer status);                                   // no result

        @SqlProcedure("SQ_PROC_CALC")
        Double calcWrong(Integer customerId);                         // does not match the database: 1 argument
    }

    /** A database function. */
    interface MathFns {
        @SqlFunction("SQ_FN_HALF")
        static Double half(Double value) {
            throw new DbOnlyException();
        }
    }
    // @end

    /** A group: the customer id and the sum of the discounted prices. */
    record Discounted(Integer customerId, Double total) {
    }

    private static final String DB = System.getProperty("lql.db", "h2");

    /** SQ_FN_DISCOUNT(price) = price * 0.9, as in FunctionTest. */
    private static final Map<String, List<String>> CREATE = Map.of(
            "h2", List.of("CREATE ALIAS IF NOT EXISTS SQ_FN_DISCOUNT FOR \"org.lambdaql.query.H2Functions.discount\""),
            "mariadb", List.of("CREATE OR REPLACE FUNCTION SQ_FN_DISCOUNT(p DOUBLE) RETURNS DOUBLE DETERMINISTIC RETURN p * 0.9"),
            "oracle", List.of("CREATE OR REPLACE FUNCTION SQ_FN_DISCOUNT(p IN NUMBER) RETURN NUMBER DETERMINISTIC IS BEGIN RETURN p * 0.9; END;"),
            "mysql", List.of("DROP FUNCTION IF EXISTS SQ_FN_DISCOUNT",
                    "CREATE FUNCTION SQ_FN_DISCOUNT(p DOUBLE) RETURNS DOUBLE DETERMINISTIC NO SQL RETURN p * 0.9"),
            "postgresql", List.of("CREATE OR REPLACE FUNCTION SQ_FN_DISCOUNT(p DOUBLE PRECISION) RETURNS DOUBLE PRECISION"
                    + " LANGUAGE plpgsql IMMUTABLE AS 'BEGIN RETURN p * 0.9; END'"));

    private static final Map<String, List<String>> DROP = Map.of(
            "h2", List.of("DROP ALIAS IF EXISTS SQ_FN_DISCOUNT"),
            "mariadb", List.of("DROP FUNCTION IF EXISTS SQ_FN_DISCOUNT"),
            "oracle", List.of("DROP FUNCTION SQ_FN_DISCOUNT", "PURGE RECYCLEBIN"),
            "mysql", List.of("DROP FUNCTION IF EXISTS SQ_FN_DISCOUNT"),
            "postgresql", List.of("DROP FUNCTION IF EXISTS SQ_FN_DISCOUNT(DOUBLE PRECISION)"));

    @Autowired
    private EntityManagerFactory emf;

    private QueryBuilder qb;

    @BeforeEach
    void seed() {
        qb = new QueryBuilder(emf);
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        for (String sql : CREATE.get(DB)) em.createNativeQuery(sql).executeUpdate();
        for (String e : List.of("Item", "Order", "Customer")) em.createQuery("delete from " + e).executeUpdate();
        Customer kim = new Customer(1, "Kim", null);
        Customer lee = new Customer(2, "Lee", null);
        Customer park = new Customer(3, "Park", null);
        em.persist(kim);
        em.persist(lee);
        em.persist(park);
        Object[][] rows = {{50.0, kim}, {150.0, kim}, {250.0, lee}, {120.0, null}, {60.0, park}};
        for (Object[] r : rows) {
            Order o = new Order();
            o.setStatus((short) 1);
            o.setPrice((Double) r[0]);
            o.setActive(true);
            o.setCustomer((Customer) r[1]);
            em.persist(o);
        }
        em.getTransaction().commit();
        em.close();
    }

    @AfterAll
    void dropFunction() {
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        for (String sql : DROP.get(DB)) em.createNativeQuery(sql).executeUpdate();
        em.getTransaction().commit();
        em.close();
    }

    @Test
    void functionInGroupQuery() {
        // @start region="function-group"
        // a database function inside an aggregate, in having and in select
        ValueQuery<Discounted> q = qb.from(Order.class)
                .where(o -> o.getCustomer() != null)
                .groupBy(o -> o.getCustomer().getId())
                .having(o -> Agg.sum(OrderFns.discount(o.getPrice())) > 200)
                .select(o -> new Discounted(o.getCustomer().getId(), Agg.sum(OrderFns.discount(o.getPrice()))));
        assertEquals("select e0.customer.id, sum(function('SQ_FN_DISCOUNT' as Double, e0.price)) from Order e0"
                + " where e0.customer is not null group by e0.customer.id"
                + " having sum(function('SQ_FN_DISCOUNT' as Double, e0.price)) > 200.0", q.toHql().hql());
        List<Discounted> rows = q.toList();
        // @end
        assertEquals(1, rows.size());                    // Kim: 45 + 135 = 180, Lee: 225, Park: 54
        assertEquals(2, rows.get(0).customerId());
        assertEquals(225.0, rows.get(0).total(), 1e-6);
    }

    @Test
    void functionInSubquery() {
        // @start region="function-sub"
        SelectQuery<Customer> q = qb.from(Customer.class).where((c, sub) -> sub.from(Order.class)
                .where(o -> o.getCustomer().getId() == c.getId() && OrderFns.discount(o.getPrice()) > 100)
                .exists());
        assertEquals("select e0 from Customer e0 where exists (select 1 from Order s1"
                + " where s1.customer.id = e0.id and function('SQ_FN_DISCOUNT' as Double, s1.price) > 100.0)", q.toHql().hql());
        // @end
        assertEquals(List.of(1, 2), q.toList().stream().map(Customer::getId).sorted().toList());
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = "org.lambdaql.query")
    static class Context {
    }
}
