package org.lambdaql.query;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lambdaql.Agg;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Entities as select values (ROADMAP D70): {@code select(o -> o)}, a joined entity {@code select((o, c) -> c)} and
 * several entities in one record {@code select((o, c) -> new OrderWithCustomer(o, c))}. Each test checks the HQL and
 * compares the rows with the same answer computed in Java.
 */
@SpringBootTest(classes = SelectEntityTest.Context.class)
@ActiveProfiles("test")
class SelectEntityTest {

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
        Customer[] owners = {kim, kim, lee, null};
        for (int i = 0; i < owners.length; i++) {
            Order o = new Order();
            o.setPrice(100.0 * (i + 1));
            o.setCustomer(owners[i]);
            em.persist(o);
        }
        em.getTransaction().commit();
        all = em.createQuery("select o from Order o left join fetch o.customer order by o.id", Order.class).getResultList();
        em.close();
    }

    private static Customer customer(EntityManager em, int id, String name) {
        Customer c = new Customer();
        c.setId(id);
        c.setName(name);
        em.persist(c);
        return c;
    }

    /** An order and its customer, both entities. */
    record OrderWithCustomer(Order order, Customer customer) {
    }

    @Test
    void wholeEntity() {
        // @start region="select-self"
        // o -> o selects the entity itself: the same rows as toList()
        ValueQuery<Order> q = qb.from(Order.class).where(o -> o.getPrice() > 150).select(o -> o);
        // @end
        assertEquals("select e0 from Order e0 where e0.price > 150.0", q.toHql().hql());
        assertEquals(all.stream().filter(o -> o.getPrice() > 150).map(Order::getId).toList(),
                q.toList().stream().map(Order::getId).sorted().toList());
    }

    @Test
    void joinedEntity() {
        // @start region="select-joined"
        // the customers that have an order, through an explicit join; Hibernate returns each entity once
        ValueQuery<Customer> q = qb.from(Order.class)
                .join(Customer.class, (o, c) -> o.getCustomer().getId() == c.getId())
                .select((o, c) -> c);
        // @end
        assertEquals("select e1 from Order e0 join Customer e1 on e0.customer.id = e1.id", q.toHql().hql());
        List<String> expected = all.stream().map(Order::getCustomer).filter(Objects::nonNull).map(Customer::getName).distinct().sorted().toList();
        assertEquals(expected, q.toList().stream().map(Customer::getName).sorted().toList());
    }

    @Test
    void severalEntities() {
        // @start region="select-entities"
        // an order and its customer together; with leftJoin an order without a customer has null
        ValueQuery<OrderWithCustomer> q = qb.from(Order.class)
                .leftJoin(Customer.class, (o, c) -> o.getCustomer().getId() == c.getId())
                .select((o, c) -> new OrderWithCustomer(o, c));
        // @end
        assertEquals("select e0, e1 from Order e0 left join Customer e1 on e0.customer.id = e1.id", q.toHql().hql());
        List<OrderWithCustomer> rows = new ArrayList<>(q.toList());
        rows.sort(Comparator.comparing(r -> r.order().getId()));
        assertEquals(all.size(), rows.size());
        for (int i = 0; i < all.size(); i++) {
            assertEquals(all.get(i).getId(), rows.get(i).order().getId());
            Customer c = all.get(i).getCustomer();
            if (c == null) assertNull(rows.get(i).customer());
            else assertEquals(c.getId(), rows.get(i).customer().getId());
        }
        // the same row is the same object: the customer of two orders is one Customer
        List<OrderWithCustomer> kims = rows.stream().filter(r -> r.customer() != null && r.customer().getId() == 1).toList();
        assertSame(kims.get(0).customer(), kims.get(1).customer());
    }

    @Test
    void notInADerivedTable() {
        // a derived table or CTE has value columns: a row that holds entities is rejected
        ValueQuery<OrderWithCustomer> rows = qb.from(Order.class)
                .leftJoin(Customer.class, (o, c) -> o.getCustomer().getId() == c.getId())
                .select((o, c) -> new OrderWithCustomer(o, c));
        org.lambdaql.LambdaQLException e = org.junit.jupiter.api.Assertions.assertThrows(org.lambdaql.LambdaQLException.class,
                () -> qb.from(rows));
        assertEquals("LQL-3071", e.code());
    }

    /** The total price of a customer's orders. */
    record Total(Integer customerId, Double total) {
    }

    @Test
    void entityNextToACte() {
        // @start region="select-entity-cte"
        // the CTE has values only (customer id, total); the outer query joins it to the entity and selects the entity
        Cte<Total> totals = qb.with(qb.from(Order.class)
                .where(o -> o.getCustomer() != null)
                .groupBy(o -> o.getCustomer().getId())
                .select(o -> new Total(o.getCustomer().getId(), Agg.sum(o.getPrice()))));
        ValueQuery<Customer> big = qb.from(Customer.class)
                .join(totals, (c, t) -> t.customerId().equals(c.getId()))
                .where((c, t) -> t.total() > 250)
                .select((c, t) -> c);
        // @end
        assertEquals("with t as (select d0.customer.id as customerId, sum(d0.price) as total from Order d0"
                + " where d0.customer is not null group by d0.customer.id)"
                + " select e0 from Customer e0 join t e1 on e1.customerId = e0.id where e1.total > 250.0", big.toHql().hql());
        // Kim: 100 + 200, Lee: 300
        assertEquals(List.of("Kim", "Lee"), big.toList().stream().map(Customer::getName).sorted().toList());
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = "org.lambdaql.query")
    static class Context {
    }
}
