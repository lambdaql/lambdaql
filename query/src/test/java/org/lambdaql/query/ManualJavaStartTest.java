package org.lambdaql.query;

import static org.lambdaql.query.By.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The examples of the Java manual, chapter 2 (getting started).
 * Each test checks the HQL inside the region and the rows after it.
 */
@SpringBootTest(classes = ManualJavaStartTest.Context.class)
@ActiveProfiles("test")
class ManualJavaStartTest {

    @Autowired
    private EntityManagerFactory emf;

    @Autowired
    private OrderService orderService;

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
        Customer kim = new Customer(1, "Kim", null);
        Customer lee = new Customer(2, "Lee", null);
        em.persist(kim);
        em.persist(lee);
        Object[][] rows = {
                // price, status, active, customer
                {50.0, 1, true, kim},
                {150.0, 1, true, lee},
                {250.0, 2, false, kim},
                {120.0, 1, false, null},
                {80.0, 2, true, kim},
        };
        for (Object[] r : rows) {
            Order o = new Order();
            o.setPrice((Double) r[0]);
            o.setStatus(((Integer) r[1]).shortValue());
            o.setActive((Boolean) r[2]);
            o.setCustomer((Customer) r[3]);
            em.persist(o);
        }
        em.getTransaction().commit();
        all = em.createQuery("select o from Order o left join fetch o.customer", Order.class).getResultList();
        em.close();
    }

    private static List<Long> ids(List<Order> orders) {
        return orders.stream().map(Order::getId).sorted().toList();
    }

    private List<Long> javaIds(Predicate<Order> p) {
        return all.stream().filter(p).map(Order::getId).sorted().toList();
    }

    @Test
    void createBuilder() {
        EntityManager em = emf.createEntityManager();
        // @start region="create"
        // one builder for the whole application; each query opens and closes its own EntityManager
        QueryBuilder qb = new QueryBuilder(emf);
        // or on an EntityManager that you manage: the queries run on it (and in its transaction)
        QueryBuilder qbOnEm = new QueryBuilder(em);
        // @end
        assertEquals(5, qb.from(Order.class).count());
        assertEquals(5, qbOnEm.from(Order.class).count());
        em.close();
    }

    // @start region="config"
    @Configuration
    public static class LambdaQlConfig {

        @Bean
        public QueryBuilder queryBuilder(EntityManagerFactory entityManagerFactory) {
            return new QueryBuilder(SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory));
        }
    }
    // @end

    // @start region="service"
    @Service
    public static class OrderService {

        private final QueryBuilder qb;

        public OrderService(QueryBuilder qb) {
            this.qb = qb;
        }

        @Transactional(readOnly = true)
        public List<Order> activeOrders() {
            return qb.from(Order.class)
                     .where(o -> o.isActive())
                     .toList();
        }
    }
    // @end

    @Test
    void serviceUsesTheBean() {
        assertEquals(javaIds(Order::isActive), ids(orderService.activeOrders()));
    }

    @Test
    void firstQuery() {
        // @start region="first-query"
        SelectQuery<Order> query = qb.from(Order.class)
                .where(o -> o.getStatus() == 1 && o.getPrice() > 100);
        assertEquals("select e0 from Order e0 where e0.status = 1 and e0.price > 100.0", query.toHql().hql());
        List<Order> orders = query.toList();
        // @end
        assertEquals(javaIds(o -> o.getStatus() == 1 && o.getPrice() > 100), ids(orders));
    }

    @Test
    void capturedValues() {
        // @start region="captured"
        int status = 1;
        double minPrice = 100;
        SelectQuery<Order> query = qb.from(Order.class)
                .where(o -> o.getStatus() == status && o.getPrice() >= minPrice);
        assertEquals("select e0 from Order e0 where e0.status = :p1 and e0.price >= :p2", query.toHql().hql());
        List<Order> orders = query.toList();
        // @end
        assertEquals(javaIds(o -> o.getStatus() == 1 && o.getPrice() >= 100), ids(orders));
    }

    @Test
    void manyWhereCalls() {
        // @start region="many-where"
        SelectQuery<Order> query = qb.from(Order.class)
                .where(o -> o.isActive())
                .where(o -> o.getStatus() == 1 || o.getStatus() == 2);
        assertEquals("select e0 from Order e0 where e0.active = true and (e0.status = 1 or e0.status = 2)",
                query.toHql().hql());
        // @end
        assertEquals(javaIds(o -> o.isActive() && (o.getStatus() == 1 || o.getStatus() == 2)), ids(query.toList()));
    }

    @Test
    void stepByStep() {
        Double input = 100.0;
        // @start region="step-by-step"
        Double minPrice = input;                         // for example from a search form; may be null
        SelectQuery<Order> query = qb.from(Order.class).where(o -> o.isActive());
        if (minPrice != null) {
            query = query.where(o -> o.getPrice() >= minPrice);
        }
        assertEquals("select e0 from Order e0 where e0.active = true and e0.price >= :p1", query.toHql().hql());
        List<Order> orders = query.toList();
        // @end
        assertEquals(javaIds(o -> o.isActive() && o.getPrice() >= 100), ids(orders));
    }

    @Test
    void readmeExample() {
        // @start region="readme"
        double minPrice = 100;
        OrderedSelectQuery<Order> query = qb.from(Order.class)
                .where(o -> o.getStatus() == 1 && o.getPrice() > minPrice)
                .orderBy(o -> desc(o.getPrice()).asc(o.getId()))
                .limit(20);
        assertEquals("select e0 from Order e0 where e0.status = 1 and e0.price > :p1"
                + " order by e0.price desc, e0.id asc", query.toHql().hql());
        List<Order> orders = query.toList();
        // @end
        List<Long> expected = all.stream().filter(o -> o.getStatus() == 1 && o.getPrice() > 100)
                .sorted(java.util.Comparator.comparing(Order::getPrice).reversed()).map(Order::getId).toList();
        assertEquals(expected, orders.stream().map(Order::getId).toList());
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml, the test entities, and the manual's beans. */
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = "org.lambdaql.query")
    @Import({LambdaQlConfig.class, OrderService.class})
    static class Context {
    }
}
