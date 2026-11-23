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
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lambdaql.Agg.avg;
import static org.lambdaql.Agg.count;

/**
 * The examples of the Java manual, chapter 6 (select, aggregates, group by).
 * Each test checks the HQL inside the region and the values after it.
 */
@SpringBootTest(classes = ManualJavaSelectTest.Context.class)
@ActiveProfiles("test")
class ManualJavaSelectTest {

    /** One group: the customer id, the number of orders, the sum of the prices. */
    record Stat(Integer customerId, long count, Double total) {
    }

    /** Aggregates over all matching rows. */
    record Total(long count, Double average) {
    }

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
        Customer kim = new Customer(1, "Kim", null);
        Customer lee = new Customer(2, "Lee", null);
        em.persist(kim);
        em.persist(lee);
        Object[][] rows = {
                // price, status, customer
                {50.0, 1, kim},
                {150.0, 1, kim},
                {250.0, 2, lee},
                {120.0, 1, null},
                {80.0, 2, kim},
        };
        for (Object[] r : rows) {
            Order o = new Order();
            o.setPrice((Double) r[0]);
            o.setStatus(((Integer) r[1]).shortValue());
            o.setActive(true);
            o.setCustomer((Customer) r[2]);
            em.persist(o);
        }
        em.getTransaction().commit();
        all = em.createQuery("select o from Order o left join fetch o.customer", Order.class).getResultList();
        em.close();
    }

    @Test
    void selectPath() {
        // @start region="select-path"
        ValueQuery<String> names = qb.from(Order.class).select(o -> o.getCustomer().getName());
        // a path used only in select is a left join: the order without a customer gives null
        assertEquals("select j1.name from Order e0 left join e0.customer j1", names.toHql().hql());
        // @end
        List<String> expected = new ArrayList<>(all.stream()
                .map(o -> o.getCustomer() == null ? null : o.getCustomer().getName()).toList());
        expected.sort(Comparator.nullsFirst(Comparator.naturalOrder()));
        List<String> actual = new ArrayList<>(names.toList());
        actual.sort(Comparator.nullsFirst(Comparator.naturalOrder()));
        assertEquals(expected, actual);
    }

    @Test
    void staticImport() {
        // @start region="agg-static-import"
        // the same query: Agg.count() or, with "import static org.lambdaql.Agg.*", count()
        ValueQuery<Total> withClass = qb.from(Order.class)
                .select(o -> new Total(Agg.count(), Agg.avg(o.getPrice())));
        ValueQuery<Total> withImport = qb.from(Order.class)
                .select(o -> new Total(count(), avg(o.getPrice())));
        assertEquals("select count(*), avg(e0.price) from Order e0", withClass.toHql().hql());
        assertEquals(withClass.toHql().hql(), withImport.toHql().hql());
        // @end
        Total t = withImport.toList().get(0);
        assertEquals(all.size(), t.count());
        assertEquals(all.stream().mapToDouble(Order::getPrice).average().orElseThrow(), t.average(), 1e-9);
    }

    @Test
    void groupByOneKey() {
        // @start region="group-one"
        ValueQuery<Stat> q = qb.from(Order.class)
                .groupBy(o -> o.getCustomer().getId())
                .select(o -> new Stat(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice())));
        assertEquals("select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id",
                q.toHql().hql());
        List<Stat> stats = q.toList();
        // @end
        Map<Integer, List<Order>> groups = all.stream().collect(Collectors.groupingBy(
                o -> o.getCustomer() == null ? -1 : o.getCustomer().getId()));
        assertEquals(groups.size(), stats.size());                 // the orders without a customer are one group
        for (Stat s : stats) {
            List<Order> g = groups.get(s.customerId() == null ? -1 : s.customerId());
            assertEquals(g.size(), s.count());
            assertEquals(g.stream().mapToDouble(Order::getPrice).sum(), s.total(), 1e-9);
        }
        assertEquals(1, stats.stream().filter(s -> Objects.isNull(s.customerId())).count());
    }

    @Test
    void valuePage() {
        // @start region="value-page"
        ValueQuery<Double> paged = qb.from(Order.class)
                .orderBy((o, by) -> by.desc(o.getPrice()))
                .select(o -> o.getPrice())
                .limit(2).offset(1);
        // paging adds the id as the last sort key
        assertEquals("select e0.price from Order e0 order by e0.price desc, e0.id asc", paged.toHql().hql());
        List<Double> page = paged.toList();
        // @end
        assertEquals(all.stream().map(Order::getPrice).sorted(Comparator.reverseOrder()).skip(1).limit(2).toList(), page);
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = "org.lambdaql.query")
    static class Context {
    }
}
