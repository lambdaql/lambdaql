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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The examples of the Java manual, chapter 5 (subqueries).
 * Each test checks the HQL inside the region and the rows after it.
 */
@SpringBootTest(classes = ManualJavaSubqueryTest.Context.class)
@ActiveProfiles("test")
class ManualJavaSubqueryTest {

    @Autowired
    private EntityManagerFactory emf;

    private QueryBuilder qb;
    private List<Order> orders;
    private List<Item> items;

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
        Customer park = new Customer(3, "Park", null);      // a customer without orders
        em.persist(kim);
        em.persist(lee);
        em.persist(park);
        Order[] os = new Order[3];
        Customer[] owners = {kim, kim, lee};
        for (int k = 0; k < os.length; k++) {
            os[k] = new Order();
            os[k].setPrice(100.0 * (k + 1));
            os[k].setStatus((short) 1);
            os[k].setActive(true);
            os[k].setCustomer(owners[k]);
            em.persist(os[k]);
        }
        int[][] rows = {{0, 5}, {0, 20}, {1, 8}};    // order index, qty; the third order has no items
        for (int[] r : rows) {
            Item i = new Item();
            i.setOrder(os[r[0]]);
            i.setPrice(1.0);
            i.setQty(r[1]);
            em.persist(i);
        }
        em.getTransaction().commit();
        orders = em.createQuery("select o from Order o", Order.class).getResultList();
        items = em.createQuery("select i from Item i join fetch i.order", Item.class).getResultList();
        em.close();
    }

    private static List<Long> ids(List<Order> list) {
        return list.stream().map(Order::getId).sorted().toList();
    }

    @Test
    void correlated() {
        // @start region="correlated"
        int minQty = 10;
        SelectQuery<Order> q = qb.from(Order.class)
                .where((o, sub) -> sub.from(Item.class)
                        .where(i -> i.getOrder().getId() == o.getId() && i.getQty() > minQty)   // o comes from outside
                        .exists());
        assertEquals("select e0 from Order e0"
                + " where exists (select 1 from Item s1 where s1.order.id = e0.id and s1.qty > :p1)", q.toHql().hql());
        assertEquals(Map.of("p1", 10), q.toHql().parameters());
        // @end
        List<Long> expected = orders.stream()
                .filter(o -> items.stream().anyMatch(i -> i.getOrder().getId().equals(o.getId()) && i.getQty() > 10))
                .map(Order::getId).sorted().toList();
        assertEquals(expected, ids(q.toList()));
    }

    @Test
    void collectionPath() {
        // @start region="collection-path"
        // a collection path is rejected
        LambdaQLException e = assertThrows(LambdaQLException.class,
                () -> qb.from(Customer.class).where(c -> c.getOrders().size() > 0));
        assertEquals("LQL-3010", e.code());
        // ask with a subquery instead
        SelectQuery<Customer> withOrders = qb.from(Customer.class)
                .where((c, sub) -> sub.from(Order.class).where(o -> o.getCustomer().getId() == c.getId()).exists());
        assertEquals("select e0 from Customer e0"
                + " where exists (select 1 from Order s1 where s1.customer.id = e0.id)", withOrders.toHql().hql());
        // @end
        assertEquals(List.of(1, 2), withOrders.toList().stream().map(Customer::getId).sorted().toList());
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = "org.lambdaql.query")
    static class Context {
    }
}
