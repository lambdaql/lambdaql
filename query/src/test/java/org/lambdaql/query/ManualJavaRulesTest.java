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

import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The examples of the Java manual, chapter 10 (rules and limits).
 * Each test checks the HQL inside the region and the rows after it.
 */
@SpringBootTest(classes = ManualJavaRulesTest.Context.class)
@ActiveProfiles("test")
class ManualJavaRulesTest {

    @Autowired
    private EntityManagerFactory emf;

    private QueryBuilder qb;
    private List<Order> all;

    @BeforeEach
    void seed() {
        qb = new QueryBuilder(emf);
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        em.createQuery("delete from Item").executeUpdate();
        em.createQuery("delete from Order").executeUpdate();
        em.createQuery("delete from Customer").executeUpdate();
        Object[][] rows = {
                // status, active, description
                {1, true, "a"},
                {1, false, "b"},
                {2, true, null},
                {3, false, "a"},
        };
        for (Object[] r : rows) {
            Order o = new Order();
            o.setPrice(100.0);
            o.setStatus(((Integer) r[0]).shortValue());
            o.setActive((Boolean) r[1]);
            o.setDescription((String) r[2]);
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
    void plainEquals() {
        String text = "a";
        // @start region="plain-equals"
        // when the query runs, text is not null
        SelectQuery<Order> same = qb.from(Order.class).where(o -> o.getDescription() == text);
        assertEquals("select e0 from Order e0 where e0.description = :p1", same.toHql().hql());
        SelectQuery<Order> other = qb.from(Order.class).where(o -> o.getDescription() != text);
        // a NULL description matches neither
        assertEquals("select e0 from Order e0 where e0.description <> :p1", other.toHql().hql());
        // @end
        assertEquals(javaIds(o -> "a".equals(o.getDescription())), ids(same.toList()));
        assertEquals(javaIds(o -> o.getDescription() != null && !"a".equals(o.getDescription())), ids(other.toList()));
    }

    @Test
    void shapeOutsideTheLambda() {
        boolean flag = true;
        // @start region="cache-shape"
        // good: one lambda; only the value changes
        int status = 1;
        SelectQuery<Order> one = qb.from(Order.class).where(o -> o.getStatus() == status);
        assertEquals("select e0 from Order e0 where e0.status = :p1", one.toHql().hql());
        // rejected: the shape of the query changes inside the lambda
        assertEquals("LQL-2021", assertThrows(LambdaQLException.class,
                () -> qb.from(Order.class).where(o -> flag ? o.getStatus() == 1 : o.isActive())).code());
        // instead: choose the lambda outside (two lambdas, each analyzed once)
        List<Order> list = flag
                ? qb.from(Order.class).where(o -> o.getStatus() == 1).toList()
                : qb.from(Order.class).where(o -> o.isActive()).toList();
        // @end
        assertEquals(javaIds(o -> o.getStatus() == 1), ids(one.toList()));
        assertEquals(javaIds(o -> o.getStatus() == 1), ids(list));
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = "org.lambdaql.query")
    static class Context {
    }
}
