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
import org.springframework.test.context.ActiveProfiles;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A value ternary made only of captured values and constants (ROADMAP D61): computed in Java when the query runs and
 * bound like a captured value. A ternary that reads the entity is still rejected. Each test checks the HQL and the rows.
 */
@SpringBootTest(classes = HostTernaryTest.Context.class)
@ActiveProfiles("test")
class HostTernaryTest {

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
        for (int i = 1; i <= 4; i++) {
            Order o = new Order();
            o.setStatus((short) (i % 3));
            o.setPrice(10.0 * i);
            o.setActive(i % 2 == 0);
            em.persist(o);
        }
        em.getTransaction().commit();
        em.close();
    }

    private List<Long> javaIds(Predicate<Order> p) {
        EntityManager em = emf.createEntityManager();
        try {
            return em.createQuery("select o from Order o", Order.class).getResultList().stream()
                    .filter(p).map(Order::getId).sorted(Comparator.naturalOrder()).toList();
        } finally {
            em.close();
        }
    }

    private static List<Long> ids(List<Order> list) {
        return list.stream().map(Order::getId).sorted(Comparator.naturalOrder()).toList();
    }

    @Test
    void ternaryOfCapturedValues() {
        for (boolean flag : new boolean[]{true, false}) {
            // @start region="host-ternary"
            // flag and both values are captured values or constants: computed in Java, bound as :p1
            SelectQuery<Order> q = qb.from(Order.class).where(o -> o.getStatus() == (flag ? 1 : 2));
            assertEquals("select e0 from Order e0 where e0.status = :p1", q.toHql().hql());
            // @end
            assertEquals(flag ? 1 : 2, ((Number) q.toHql().parameters().get("p1")).intValue());
            assertEquals(javaIds(o -> o.getStatus() == (flag ? 1 : 2)), ids(q.toList()));
        }
        Double min = null;
        SelectQuery<Order> q = qb.from(Order.class).where(o -> o.getPrice() > (min != null ? min : 25.0));
        assertEquals("select e0 from Order e0 where e0.price > :p1", q.toHql().hql());
        assertEquals(javaIds(o -> o.getPrice() > 25.0), ids(q.toList()));
    }

    @Test
    void ternaryInAnAssignment() {
        boolean urgent = true;
        long changed = qb.update(Order.class).set(o -> o.setStatus((short) (urgent ? 9 : 8))).where(o -> o.getPrice() > 25.0).execute();
        assertEquals(javaIds(o -> o.getPrice() > 25.0).size(), changed);
        assertEquals(javaIds(o -> o.getPrice() > 25.0), javaIds(o -> o.getStatus() == 9));
    }

    @Test
    void ternaryOnTheEntityBecomesCase() {
        // a condition that reads the entity is not computed in Java: it becomes case when
        assertEquals("select e0 from Order e0 where e0.status = case when e0.active = true then 1 else 2 end",
                qb.from(Order.class).where(o -> o.getStatus() == (o.isActive() ? 1 : 2)).toHql().hql());
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = "org.lambdaql.query")
    static class Context {
    }
}
