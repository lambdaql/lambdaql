package org.lambdaql.query;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lambdaql.Agg;
import org.lambdaql.LambdaQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@code count(column)} and {@code distinct().count(column)}: count the non-NULL values of one column, or the different
 * ones. Each test checks the HQL and computes the same number in Java from the seeded rows.
 */
@SpringBootTest(classes = CountColumnTest.Context.class)
@ActiveProfiles("test")
class CountColumnTest {

    @Autowired
    private EntityManagerFactory emf;

    private QueryBuilder qb;
    private List<Order> all;

    @BeforeEach
    void seed() {
        qb = new QueryBuilder(emf);
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        for (String e : List.of("Item", "Order", "Customer")) {
            em.createQuery("delete from " + e).executeUpdate();
        }
        Customer kim = customer(em, 1, "Kim");
        Customer lee = customer(em, 2, "Lee");
        Object[][] rows = {
                // description, customer, active
                {"gift", kim, true},
                {null, kim, true},
                {"rush", lee, false},
                {"gift", null, true},
                {null, null, false},
        };
        for (Object[] r : rows) {
            Order o = new Order();
            o.setPrice(100.0);
            o.setDescription((String) r[0]);
            o.setCustomer((Customer) r[1]);
            o.setActive((Boolean) r[2]);
            em.persist(o);
        }
        em.getTransaction().commit();
        all = em.createQuery("select o from Order o left join fetch o.customer", Order.class).getResultList();
        em.close();
    }

    private static Customer customer(EntityManager em, int id, String name) {
        Customer c = new Customer();
        c.setId(id);
        c.setName(name);
        em.persist(c);
        return c;
    }

    @Test
    void countColumn() {
        // @start region="count-column"
        // rows whose description is not NULL
        SelectQuery<Order> orders = qb.from(Order.class);
        long described = orders.count(o -> o.getDescription());
        // @end
        assertEquals("select count(e0.description) from Order e0", orders.countHql(o -> o.getDescription(), false).hql());
        assertEquals(all.stream().filter(o -> o.getDescription() != null).count(), described);
    }

    @Test
    void countPathWithWhere() {
        SelectQuery<Order> active = qb.from(Order.class).where(o -> o.isActive());
        assertEquals("select count(e0.customer.id) from Order e0 where e0.active = true", active.countHql(o -> o.getCustomer().getId(), false).hql());
        assertEquals(all.stream().filter(o -> o.isActive() && o.getCustomer() != null).count(),
                active.count(o -> o.getCustomer().getId()));
        // a path past the id joins like in select: left join, so orders without a customer are not lost
        assertEquals("select count(j1.name) from Order e0 left join e0.customer j1 where e0.active = true",
                active.countHql(o -> o.getCustomer().getName(), false).hql());
        assertEquals(all.stream().filter(o -> o.isActive() && o.getCustomer() != null).count(),
                active.count(o -> o.getCustomer().getName()));
    }

    @Test
    void countDistinct() {
        // @start region="count-distinct"
        // the number of different customers that have an order
        SelectQuery<Order> orders = qb.from(Order.class);
        long customers = orders.distinct().count(o -> o.getCustomer().getId());
        // @end
        assertEquals("select count(distinct e0.customer.id) from Order e0", orders.countHql(o -> o.getCustomer().getId(), true).hql());
        assertEquals(all.stream().map(Order::getCustomer).filter(Objects::nonNull).map(Customer::getId).distinct().count(), customers);
        assertEquals(all.stream().map(Order::getDescription).filter(Objects::nonNull).distinct().count(),
                qb.from(Order.class).distinct().count(o -> o.getDescription()));
    }

    @Test
    void notOneValue() {
        // a constructor or an aggregate is not one column
        LambdaQLException made = assertThrows(LambdaQLException.class,
                () -> qb.from(Order.class).count(o -> new Pair(o.getId(), o.getDescription())));
        assertEquals("LQL-3070", made.code());
        LambdaQLException agg = assertThrows(LambdaQLException.class, () -> qb.from(Order.class).count(o -> Agg.count()));
        assertEquals("LQL-3070", agg.code());
    }

    /** Two values, to try a constructor in the column lambda. */
    record Pair(Long id, String description) {
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = "org.lambdaql.query")
    static class Context {
    }
}
