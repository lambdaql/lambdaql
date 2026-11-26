package org.lambdaql.query;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.lambdaql.Cond.between;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code isAfter}, {@code isBefore} and {@code isEqual} on java.time values become comparisons (ROADMAP D75).
 * Each test checks the HQL and compares the rows with the same test run in Java (a null time never matches in SQL).
 */
@SpringBootTest(classes = TimeCompareTest.Context.class)
@ActiveProfiles("test")
class TimeCompareTest {

    @Autowired
    private EntityManagerFactory emf;

    private QueryBuilder qb;

    @BeforeEach
    void seed() {
        qb = new QueryBuilder(emf);
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        for (String e : List.of("Item", "Order", "Customer", "Archive", "VOrder", "IdOrder")) {
            em.createQuery("delete from " + e).executeUpdate();
        }
        for (int month = 0; month <= 4; month++) {
            Order o = new Order();
            o.setStatus((short) month);
            o.setUpdateAt(month == 0 ? null : LocalDateTime.of(2026, month, 1, 0, 0));
            em.persist(o);
        }
        em.getTransaction().commit();
        em.close();
    }

    private List<Long> javaIds(Predicate<LocalDateTime> test) {
        EntityManager em = emf.createEntityManager();
        try {
            return em.createQuery("select o from Order o", Order.class).getResultList().stream()
                    .filter(o -> o.getUpdateAt() != null && test.test(o.getUpdateAt()))
                    .map(Order::getId).sorted(Comparator.naturalOrder()).toList();
        } finally {
            em.close();
        }
    }

    private static List<Long> ids(List<Order> list) {
        return list.stream().map(Order::getId).sorted(Comparator.naturalOrder()).toList();
    }

    @Test
    void afterBeforeEqual() {
        LocalDateTime since = LocalDateTime.of(2026, 2, 1, 0, 0);
        // @start region="is-after"
        SelectQuery<Order> after = qb.from(Order.class).where(o -> o.getUpdateAt().isAfter(since));
        assertEquals("select e0 from Order e0 where e0.updateAt > :p1", after.toHql().hql());

        SelectQuery<Order> before = qb.from(Order.class).where(o -> o.getUpdateAt().isBefore(since));
        assertEquals("select e0 from Order e0 where e0.updateAt < :p1", before.toHql().hql());

        SelectQuery<Order> same = qb.from(Order.class).where(o -> o.getUpdateAt().isEqual(since));
        assertEquals("select e0 from Order e0 where e0.updateAt = :p1", same.toHql().hql());
        // @end
        assertEquals(javaIds(t -> t.isAfter(since)), ids(after.toList()));
        assertEquals(javaIds(t -> t.isBefore(since)), ids(before.toList()));
        assertEquals(javaIds(t -> t.isEqual(since)), ids(same.toList()));
    }

    @Test
    void negatedAndValueFirst() {
        LocalDateTime since = LocalDateTime.of(2026, 2, 1, 0, 0);
        // !isAfter is "not after": <=
        SelectQuery<Order> notAfter = qb.from(Order.class).where(o -> !o.getUpdateAt().isAfter(since));
        assertEquals("select e0 from Order e0 where e0.updateAt <= :p1", notAfter.toHql().hql());
        assertEquals(javaIds(t -> !t.isAfter(since)), ids(notAfter.toList()));
        // the captured value first: since.isBefore(updateAt) is updateAt > since
        SelectQuery<Order> valueFirst = qb.from(Order.class).where(o -> since.isBefore(o.getUpdateAt()));
        assertEquals("select e0 from Order e0 where :p1 < e0.updateAt", valueFirst.toHql().hql());
        assertEquals(javaIds(since::isBefore), ids(valueFirst.toList()));
    }

    @Test
    void betweenBothEnds() {
        LocalDateTime from = LocalDateTime.of(2026, 2, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 3, 1, 0, 0);
        // @start region="between"
        // Cond.between(x, from, to): both ends included, like SQL between
        SelectQuery<Order> q = qb.from(Order.class).where(o -> between(o.getUpdateAt(), from, to));
        assertEquals("select e0 from Order e0 where :p1 <= e0.updateAt and e0.updateAt <= :p2", q.toHql().hql());
        // @end
        assertEquals(javaIds(t -> between(t, from, to)), ids(q.toList()));
        // !between: outside the range (a NULL time matches neither)
        SelectQuery<Order> outside = qb.from(Order.class).where(o -> !between(o.getUpdateAt(), from, to));
        assertEquals("select e0 from Order e0 where :p1 > e0.updateAt or e0.updateAt > :p2", outside.toHql().hql());
        assertEquals(javaIds(t -> !between(t, from, to)), ids(outside.toList()));
        // the same method in Java: false for a null value
        assertEquals(false, between(null, from, to));
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = "org.lambdaql.query")
    static class Context {
    }
}
