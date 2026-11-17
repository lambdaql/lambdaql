package org.lambdaql.query;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.lambdaql.DbOnlyException;
import org.lambdaql.InOut;
import org.lambdaql.LambdaQLException;
import org.lambdaql.SqlFunction;
import org.lambdaql.SqlProcedure;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * End-to-end tests for stored procedures and database functions called on their own (ROADMAP M11, D30, PR6).
 * H2 has no OUT parameters, so the procedure tests run on MariaDB and Oracle only (PR15); the function test runs on
 * all three. The procedures and the function are created for the test and dropped at the end.
 * The region markers are used by Javadoc snippet tags.
 */
@SpringBootTest(classes = ProcedureTest.Context.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProcedureTest {

    // @start region="declare"
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

    private static final String DB = System.getProperty("lql.db", "h2");

    private static final Map<String, List<String>> CREATE = Map.of(
            "h2", List.of("CREATE ALIAS IF NOT EXISTS SQ_FN_HALF FOR \"org.lambdaql.query.H2Functions.half\""),
            "mariadb", List.of(
                    "CREATE OR REPLACE FUNCTION SQ_FN_HALF(p DOUBLE) RETURNS DOUBLE DETERMINISTIC RETURN p / 2",
                    "CREATE OR REPLACE PROCEDURE SQ_PROC_CALC(IN p_cust INT, IN p_rate DOUBLE, OUT p_total DOUBLE)"
                            + " BEGIN SELECT COALESCE(SUM(price), 0) * p_rate INTO p_total FROM SQ_orders WHERE customer_id = p_cust; END",
                    "CREATE OR REPLACE PROCEDURE SQ_PROC_STAT(IN p_cust INT, OUT p_total DOUBLE, OUT p_cnt INT)"
                            + " BEGIN SELECT SUM(price), COUNT(*) INTO p_total, p_cnt FROM SQ_orders WHERE customer_id = p_cust; END",
                    "CREATE OR REPLACE PROCEDURE SQ_PROC_ADJUST(IN p_add INT, INOUT p_balance INT, OUT p_msg VARCHAR(20))"
                            + " BEGIN SET p_balance = p_balance + p_add; SET p_msg = 'ok'; END",
                    "CREATE OR REPLACE PROCEDURE SQ_PROC_RECENT(IN p_cust INT)"
                            + " BEGIN SELECT id, price FROM SQ_orders WHERE customer_id = p_cust ORDER BY id; END",
                    "CREATE OR REPLACE PROCEDURE SQ_PROC_PURGE(IN p_status INT) BEGIN DELETE FROM SQ_orders WHERE status = p_status; END"),
            "oracle", List.of(
                    "CREATE OR REPLACE FUNCTION SQ_FN_HALF(p IN NUMBER) RETURN NUMBER DETERMINISTIC IS BEGIN RETURN p / 2; END;",
                    "CREATE OR REPLACE PROCEDURE SQ_PROC_CALC(p_cust IN NUMBER, p_rate IN NUMBER, p_total OUT NUMBER) IS"
                            + " BEGIN SELECT NVL(SUM(price), 0) * p_rate INTO p_total FROM SQ_orders WHERE customer_id = p_cust; END;",
                    "CREATE OR REPLACE PROCEDURE SQ_PROC_STAT(p_cust IN NUMBER, p_total OUT NUMBER, p_cnt OUT NUMBER) IS"
                            + " BEGIN SELECT SUM(price), COUNT(*) INTO p_total, p_cnt FROM SQ_orders WHERE customer_id = p_cust; END;",
                    "CREATE OR REPLACE PROCEDURE SQ_PROC_ADJUST(p_add IN NUMBER, p_balance IN OUT NUMBER, p_msg OUT VARCHAR2) IS"
                            + " BEGIN p_balance := p_balance + p_add; p_msg := 'ok'; END;",
                    "CREATE OR REPLACE PROCEDURE SQ_PROC_RECENT(p_cust IN NUMBER, p_cur OUT SYS_REFCURSOR) IS"
                            + " BEGIN OPEN p_cur FOR SELECT id, price FROM SQ_orders WHERE customer_id = p_cust ORDER BY id; END;",
                    "CREATE OR REPLACE PROCEDURE SQ_PROC_PURGE(p_status IN NUMBER) IS BEGIN DELETE FROM SQ_orders WHERE status = p_status; END;"));

    private static final Map<String, List<String>> DROP = Map.of(
            "h2", List.of("DROP ALIAS IF EXISTS SQ_FN_HALF"),
            "mariadb", List.of("DROP FUNCTION IF EXISTS SQ_FN_HALF", "DROP PROCEDURE IF EXISTS SQ_PROC_CALC",
                    "DROP PROCEDURE IF EXISTS SQ_PROC_STAT", "DROP PROCEDURE IF EXISTS SQ_PROC_ADJUST",
                    "DROP PROCEDURE IF EXISTS SQ_PROC_RECENT", "DROP PROCEDURE IF EXISTS SQ_PROC_PURGE"),
            "oracle", List.of("DROP FUNCTION SQ_FN_HALF", "DROP PROCEDURE SQ_PROC_CALC", "DROP PROCEDURE SQ_PROC_STAT",
                    "DROP PROCEDURE SQ_PROC_ADJUST", "DROP PROCEDURE SQ_PROC_RECENT", "DROP PROCEDURE SQ_PROC_PURGE",
                    "PURGE RECYCLEBIN"));

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
        em.persist(kim);
        Object[][] rows = {{1, 50.0, kim}, {1, 150.0, kim}, {9, 70.0, kim}, {9, 250.0, null}};
        for (Object[] r : rows) {
            Order o = new Order();
            o.setStatus(((Integer) r[0]).shortValue());
            o.setPrice((Double) r[1]);
            o.setActive(true);
            o.setCustomer((Customer) r[2]);
            em.persist(o);
        }
        em.getTransaction().commit();
        orders = em.createQuery("select o from Order o left join fetch o.customer order by o.id", Order.class).getResultList();
        em.close();
    }

    @AfterAll
    void drop() {
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        for (String sql : DROP.get(DB)) em.createNativeQuery(sql).executeUpdate();
        em.getTransaction().commit();
        em.close();
    }

    private List<Order> ofKim() {
        return orders.stream().filter(o -> o.getCustomer() != null && o.getCustomer().getId() == 1).toList();
    }

    @Test
    void call() {
        assumeFalse(DB.equals("h2"), "H2 has no OUT parameters (PR15)");
        int customerId = 1;
        double rate = 2;
        // @start region="call"
        Double total = qb.call(OrderProcs.class, p -> p.calc(customerId, rate)).execute();
        Stat stat = qb.call(OrderProcs.class, p -> p.stat(customerId)).execute();
        Adjusted adjusted = qb.call(OrderProcs.class, p -> p.adjust(5, 10)).execute();
        List<Line> recent = qb.call(OrderProcs.class, p -> p.recent(customerId)).execute();
        // @end
        double sum = ofKim().stream().mapToDouble(Order::getPrice).sum();
        assertEquals(sum * rate, total, 1e-9);
        assertEquals(sum, stat.total(), 1e-9);
        assertEquals(ofKim().size(), stat.cnt());
        assertEquals(new Adjusted(15, "ok"), adjusted);
        assertEquals(ofKim().stream().map(o -> new Line(o.getId(), o.getPrice())).toList(), recent);
    }

    @Test
    void exec() {
        assumeFalse(DB.equals("h2"), "H2 has no OUT parameters (PR15)");
        // @start region="exec"
        qb.exec(OrderProcs.class, p -> p.purge(9)).execute();
        // @end
        assertEquals(orders.stream().filter(o -> o.getStatus() != 9).map(Order::getId).toList(),
                qb.from(Order.class).toList().stream().map(Order::getId).sorted().toList());
    }

    @Test
    void function() {
        double price = 150;
        // @start region="function"
        Double half = qb.call(() -> MathFns.half(price)).execute();
        // @end
        assertEquals(75.0, half, 1e-9);
    }

    @Test
    void rules() {
        assumeFalse(DB.equals("h2"), "H2 has no OUT parameters (PR15)");
        // @start region="rules"
        // PR2: the stub is checked against the database parameters on the first call
        assertEquals("LQL-4036", assertThrows(LambdaQLException.class,
                () -> qb.call(OrderProcs.class, p -> p.calcWrong(1)).execute()).code());
        // exec cannot drop OUT values
        assertEquals("LQL-4037", assertThrows(LambdaQLException.class,
                () -> qb.exec(OrderProcs.class, p -> p.calc(1, 1.0)).execute()).code());
        // only @SqlProcedure methods of the stub can be called
        assertEquals("LQL-3060", assertThrows(LambdaQLException.class,
                () -> qb.call(OrderProcs.class, p -> p.toString()).execute()).code());
        // @end
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @EntityScan("org.lambdaql.query")
    static class Context {
    }
}
