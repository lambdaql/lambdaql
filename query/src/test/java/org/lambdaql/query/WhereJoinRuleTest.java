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

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The join rule of association paths in where (ROADMAP D54, revised 2026-11-24), Java lambdas: a step is an inner join
 * unless an order without the association can make the whole condition true. Each test checks the HQL and compares
 * the rows with the same filter run in Java.
 */
@SpringBootTest(classes = WhereJoinRuleTest.Context.class)
@ActiveProfiles("test")
class WhereJoinRuleTest {

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
        Customer kim = new Customer(1, "Kim", null);
        Customer lee = new Customer(2, "Lee", null);
        Customer noName = new Customer(3, null, null);
        em.persist(kim);
        em.persist(lee);
        em.persist(noName);
        Object[][] rows = {{1, kim}, {1, null}, {2, lee}, {2, null}, {3, noName}};
        for (Object[] r : rows) {
            Order o = new Order();
            o.setStatus(((Integer) r[0]).shortValue());
            o.setPrice(10.0);
            o.setActive(true);
            o.setCustomer((Customer) r[1]);
            em.persist(o);
        }
        em.getTransaction().commit();
        em.close();
    }

    private List<Order> orders() {
        EntityManager em = emf.createEntityManager();
        try {
            return em.createQuery("select o from Order o left join fetch o.customer", Order.class).getResultList();
        } finally {
            em.close();
        }
    }

    private static List<Long> ids(List<Order> list) {
        return list.stream().map(Order::getId).sorted(Comparator.naturalOrder()).toList();
    }

    private List<Long> javaIds(Predicate<Order> p) {
        return ids(orders().stream().filter(p).toList());
    }

    @Test
    void notEqualsNeedsTheCustomer() {
        String name = "Kim";
        // a comparison through the path counts only for orders with a customer: an inner join
        SelectQuery<Order> q = qb.from(Order.class).where(o -> !o.getCustomer().getName().equals(name));
        assertEquals("select e0 from Order e0 join e0.customer j1 where j1.name <> :p1", q.toHql().hql());
        // Java would throw for an order without a customer; a customer without a name does not match (SQL meaning)
        assertEquals(javaIds(o -> o.getCustomer() != null && o.getCustomer().getName() != null
                && !name.equals(o.getCustomer().getName())), ids(q.toList()));
    }

    @Test
    void nullGuardInDelete() {
        String name = "Kim";
        ExecutableDelete<Order> d = qb.delete(Order.class).where(o -> o.getCustomer() == null || o.getCustomer().getName().equals(name));
        // the null guard keeps orders without a customer, so the left-joined step gets no "customer exists" check
        assertEquals("delete from Order e0 where e0.customer is null"
                + " or (select s1.name from Customer s1 where s1.id = fk(e0.customer)) = :p1", d.toHql().hql());
        List<Long> expected = javaIds(o -> o.getCustomer() == null || name.equals(o.getCustomer().getName()));
        List<Long> before = ids(orders());
        assertEquals(expected.size(), d.execute());
        List<Long> left = ids(orders());
        assertEquals(before.stream().filter(id -> !expected.contains(id)).toList(), left);
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = "org.lambdaql.query")
    static class Context {
    }
}
