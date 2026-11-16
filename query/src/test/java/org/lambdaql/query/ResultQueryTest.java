package org.lambdaql.query;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.NonUniqueResultException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end tests for the result functions {@code first()}, {@code one()}, {@code count()} and {@code exists()}.
 * Each test also computes the same answer in Java from the seeded rows.
 * The region markers are used by Javadoc snippet tags.
 */
@SpringBootTest(classes = ResultQueryTest.Context.class)
@ActiveProfiles("test")
class ResultQueryTest {

    @Autowired
    private EntityManagerFactory emf;

    private QueryBuilder qb;
    private List<Order> all;

    @BeforeEach
    void seed() {
        qb = new QueryBuilder(emf);
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        em.createQuery("delete from Order").executeUpdate();
        em.createQuery("delete from Customer").executeUpdate();
        Object[][] rows = {
                // price, status, active, kind
                {50.0, 1, true, Order.Kind.NORMAL},
                {150.0, 1, true, Order.Kind.RUSH},
                {250.0, 2, false, Order.Kind.NORMAL},
                {120.0, 1, false, null},
                {80.0, 3, true, Order.Kind.RUSH},
        };
        for (Object[] r : rows) {
            Order o = new Order();
            o.setPrice((Double) r[0]);
            o.setStatus(((Integer) r[1]).shortValue());
            o.setActive((Boolean) r[2]);
            o.setKind((Order.Kind) r[3]);
            em.persist(o);
        }
        em.getTransaction().commit();
        all = em.createQuery("select o from Order o", Order.class).getResultList();
        em.close();
    }

    @Test
    void first() {
        // @start region="first"
        Optional<Order> cheapest = qb.from(Order.class)
                .orderBy((o, by) -> by.asc(o.getPrice()))
                .first();
        // without orderBy, "first" is the row with the smallest id
        Optional<Order> firstActive = qb.from(Order.class).where(o -> o.isActive()).first();
        Optional<Order> none = qb.from(Order.class).where(o -> o.getStatus() == 9).first();
        // @end
        assertEquals(all.stream().min(Comparator.comparing(Order::getPrice)).orElseThrow().getId(), cheapest.orElseThrow().getId());
        assertEquals(all.stream().filter(Order::isActive).map(Order::getId).min(Comparator.naturalOrder()).orElseThrow(),
                firstActive.orElseThrow().getId());
        assertTrue(none.isEmpty());
        // offset is kept: the second cheapest
        assertEquals(all.stream().sorted(Comparator.comparing(Order::getPrice)).skip(1).findFirst().orElseThrow().getId(),
                qb.from(Order.class).orderBy((o, by) -> by.asc(o.getPrice())).offset(1).first().orElseThrow().getId());
    }

    @Test
    void one() {
        long knownId = all.get(0).getId();
        // @start region="one"
        long id = knownId;
        Optional<Order> order = qb.from(Order.class).where(o -> o.getId() == id).one();
        Optional<Order> missing = qb.from(Order.class).where(o -> o.getId() == -1L).one();
        // two or more rows: an exception (use first() if any row is fine)
        assertThrows(NonUniqueResultException.class, () -> qb.from(Order.class).where(o -> o.isActive()).one());
        // @end
        assertEquals(knownId, order.orElseThrow().getId());
        assertTrue(missing.isEmpty());
    }

    @Test
    void count() {
        // @start region="count"
        SelectQuery<Order> active = qb.from(Order.class).where(o -> o.isActive());
        long total = active.count();                                   // every matching row
        List<Order> page = active.orderBy((o, by) -> by.desc(o.getPrice())).limit(2).toList();
        // @end
        assertEquals("select count(e0) from Order e0 where e0.active = true", active.countHql().hql());
        assertEquals(all.stream().filter(Order::isActive).count(), total);
        assertEquals(2, page.size());
        assertEquals(all.size(), qb.from(Order.class).count());
        assertEquals(0, qb.from(Order.class).where(o -> o.getStatus() == 9).count());
    }

    @Test
    void exists() {
        // @start region="exists"
        boolean anyRush = qb.from(Order.class).where(o -> o.getKind() == Order.Kind.RUSH).exists();
        boolean anyStatus9 = qb.from(Order.class).where(o -> o.getStatus() == 9).exists();
        // @end
        assertEquals("select 1 from Order e0 where e0.kind = org.lambdaql.query.Order$Kind.RUSH",
                qb.from(Order.class).where(o -> o.getKind() == Order.Kind.RUSH).existsHql().hql());
        assertTrue(anyRush);
        assertFalse(anyStatus9);
    }

    @Test
    void toSet() {
        // @start region="to-set"
        java.util.SequencedSet<Order> byPrice = qb.from(Order.class)
                .orderBy((o, by) -> by.desc(o.getPrice()))
                .toSet();
        // @end
        assertEquals(all.stream().sorted(Comparator.comparing(Order::getPrice).reversed()).map(Order::getId).toList(),
                byPrice.stream().map(Order::getId).toList());
        assertEquals(all.size(), qb.from(Order.class).toSet().size());
    }

    @Test
    void toSetSizeCheck() {
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        em.createQuery("delete from Tag").executeUpdate();
        em.persist(new Tag(1L, "same"));
        em.persist(new Tag(2L, "same"));
        em.getTransaction().commit();
        em.close();
        // @start region="to-set-check"
        // Tag.equals compares only the name: two different rows would become one element, so toSet() refuses
        assertThrows(IllegalStateException.class, () -> qb.from(Tag.class).toSet());
        assertEquals(2, qb.from(Tag.class).toList().size());
        // @end
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @EntityScan("org.lambdaql.query")
    static class Context {
    }
}
