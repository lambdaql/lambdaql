package org.lambdaql.query;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lambdaql.LambdaQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The examples of the Java manual, chapter 9 (Java notes): boxing, strings, this capture, method references.
 * Each test checks the HQL inside the region and the rows after it.
 */
@SpringBootTest(classes = ManualJavaNotesTest.Context.class)
@ActiveProfiles("test")
class ManualJavaNotesTest {

    @Autowired
    private EntityManagerFactory emf;

    private QueryBuilder qb;
    private List<Order> all;

    /** A field read by a lambda: the lambda captures {@code this}. */
    private int defaultStatus = 1;

    // @start region="this-capture"
    @Service
    public static class OrderService {

        private final QueryBuilder qb;
        private int defaultStatus = 1;

        public OrderService(QueryBuilder qb) {
            this.qb = qb;
        }

        public List<Order> defaults() {
            return qb.from(Order.class)
                     .where(o -> o.getStatus() == defaultStatus)   // means this.defaultStatus
                     .toList();
        }
    }
    // @end

    /** A static helper of your own (not a database function). */
    static String normalize(String text) {
        return text == null ? null : text.trim();
    }

    @BeforeEach
    void seed() {
        qb = new QueryBuilder(emf);
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        em.createQuery("delete from Item").executeUpdate();
        em.createQuery("delete from Order").executeUpdate();
        em.createQuery("delete from Customer").executeUpdate();
        Object[][] rows = {
                // status, active, description, cnt, updateAt (month of 2026; 0 = 2025-12)
                {1, true, "A7", 1, 3},
                {1, false, "b", 3, 0},
                {2, true, null, null, 5},
                {3, true, "A7", 2, 6},
        };
        for (Object[] r : rows) {
            Order o = new Order();
            o.setPrice(100.0);
            o.setStatus(((Integer) r[0]).shortValue());
            o.setActive((Boolean) r[1]);
            o.setDescription((String) r[2]);
            o.setCnt((Integer) r[3]);
            int month = (Integer) r[4];
            o.setUpdateAt(month == 0 ? LocalDateTime.of(2025, 12, 1, 0, 0) : LocalDateTime.of(2026, month, 1, 0, 0));
            em.persist(o);
        }
        em.getTransaction().commit();
        all = em.createQuery("select o from Order o", Order.class).getResultList();
        em.close();
    }

    private static List<Long> ids(List<Order> orders) {
        return orders.stream().map(Order::getId).sorted().toList();
    }

    private List<Long> javaIds(Predicate<Order> p) {
        return all.stream().filter(p).map(Order::getId).sorted().toList();
    }

    @Test
    void unboxing() {
        // @start region="unboxing"
        // cnt is an Integer column: javac writes o.getCnt().intValue() == 1; lambdaQL keeps only the meaning
        SelectQuery<Order> q = qb.from(Order.class).where(o -> o.getCnt() == 1);
        assertEquals("select e0 from Order e0 where e0.cnt = 1", q.toHql().hql());
        // a NULL cnt: no NullPointerException, the row is simply not returned (the SQL meaning)
        // @end
        assertEquals(javaIds(o -> o.getCnt() != null && o.getCnt() == 1), ids(q.toList()));
    }

    @Test
    void stringBuiltOutside() {
        String suffix = "7";
        // @start region="string-outside"
        String code = "A" + suffix;                                  // outside: plain Java
        SelectQuery<Order> q = qb.from(Order.class).where(o -> o.getDescription() == code);
        assertEquals("select e0 from Order e0 where e0.description = :p1", q.toHql().hql());
        // @end
        assertEquals(javaIds(o -> "A7".equals(o.getDescription())), ids(q.toList()));
    }

    @Test
    void notEquals() {
        // @start region="not-equals"
        SelectQuery<Order> notOne = qb.from(Order.class).where(o -> o.getCnt() != 1);
        assertEquals("select e0 from Order e0 where e0.cnt <> 1", notOne.toHql().hql());              // no NULL rows
        SelectQuery<Order> notEquals = qb.from(Order.class).where(o -> !Objects.equals(o.getCnt(), 1));
        assertEquals("select e0 from Order e0 where e0.cnt <> 1", notEquals.toHql().hql());           // no NULL rows either
        SelectQuery<Order> withNull = qb.from(Order.class)
                .where(o -> o.getCnt() == null || !Objects.equals(o.getCnt(), 1));
        assertEquals("select e0 from Order e0 where e0.cnt is null or e0.cnt <> 1", withNull.toHql().hql());  // NULL rows too
        // @end
        assertEquals(javaIds(o -> o.getCnt() != null && o.getCnt() != 1), ids(notOne.toList()));
        assertEquals(ids(notOne.toList()), ids(notEquals.toList()));
        assertEquals(javaIds(o -> !Objects.equals(o.getCnt(), 1)), ids(withNull.toList()));
    }

    @Test
    void thisCapture() {
        List<Order> rows = new OrderService(qb).defaults();
        assertEquals(javaIds(o -> o.getStatus() == 1), ids(rows));
        // @start region="local-copy"
        int st = defaultStatus;                                   // copied now
        SelectQuery<Order> q = qb.from(Order.class).where(o -> o.getStatus() == st);
        // the same HQL as the lambda that reads the field
        assertEquals("select e0 from Order e0 where e0.status = :p1", q.toHql().hql());
        assertEquals(q.toHql().hql(), qb.from(Order.class).where(o -> o.getStatus() == defaultStatus).toHql().hql());
        // @end
        assertEquals(ids(rows), ids(q.toList()));
    }

    @Test
    void methodReference() {
        // @start region="method-ref"
        // a method reference as a sort key: the same as o -> o.getPrice()
        assertEquals("select e0 from Order e0 order by e0.price asc",
                qb.from(Order.class).orderBy(Order::getPrice).toHql().hql());
        // a method reference as a condition is rejected: write a lambda
        assertEquals("LQL-1009", assertThrows(LambdaQLException.class,
                () -> qb.from(Order.class).where(Order::isActive)).code());
        // @end
    }

    @Test
    void staticHelpers() {
        String text = "A7";
        // @start region="static-helper"
        // a static method on captured values only: computed in Java when the query runs
        SelectQuery<Order> q = qb.from(Order.class)
                .where(o -> o.getUpdateAt().compareTo(LocalDateTime.of(2026, 1, 1, 0, 0)) > 0);
        assertEquals("select e0 from Order e0 where e0.updateAt > :p1", q.toHql().hql());
        // your own static method on an entity value: rejected
        assertEquals("LQL-3008", assertThrows(LambdaQLException.class,
                () -> qb.from(Order.class).where(o -> normalize(o.getDescription()) == text)).code());
        // @end
        assertEquals(javaIds(o -> o.getUpdateAt().isAfter(LocalDateTime.of(2026, 1, 1, 0, 0))), ids(q.toList()));
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = "org.lambdaql.query")
    static class Context {
    }
}
