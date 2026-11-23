package org.lambdaql.query;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lambdaql.Agg;
import org.lambdaql.LambdaQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * End-to-end tests for derived tables and CTEs (ROADMAP M8-2, J5, J6): {@code qb.from(valueQuery)},
 * {@code qb.with(valueQuery)}. Each test also computes the expected rows in Java from the seeded data.
 * The region markers are used by Javadoc snippet tags.
 */
@SpringBootTest(classes = DerivedQueryTest.Context.class)
@ActiveProfiles("test")
class DerivedQueryTest {

    /** A row of the derived table: the components name the columns (J5). */
    record Stat(Integer customerId, Long cnt, Double total) {
    }

    /** A row with an order id and price. */
    record Priced(Long id, Double price) {
    }

    @Autowired
    private EntityManagerFactory emf;

    private QueryBuilder qb;
    private List<Order> orders;

    @BeforeEach
    void seed() {
        qb = new QueryBuilder(emf);
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        for (String e : List.of("Item", "Order", "Customer")) em.createQuery("delete from " + e).executeUpdate();
        Customer kim = new Customer(1, "Kim", null);
        Customer lee = new Customer(2, "Lee", null);
        Customer park = new Customer(3, "Park", null);
        em.persist(kim);
        em.persist(lee);
        em.persist(park);
        Object[][] rows = {{50.0, kim}, {150.0, kim}, {250.0, lee}, {30.0, park}, {40.0, park}, {120.0, null}};
        for (Object[] r : rows) {
            Order o = new Order();
            o.setStatus((short) 1);
            o.setPrice((Double) r[0]);
            o.setActive(true);
            o.setCustomer((Customer) r[1]);
            em.persist(o);
        }
        em.getTransaction().commit();
        orders = em.createQuery("select o from Order o left join fetch o.customer", Order.class).getResultList();
        em.close();
    }

    private List<Stat> stats() {
        Map<Integer, List<Order>> groups = orders.stream().filter(o -> o.getCustomer() != null)
                .collect(Collectors.groupingBy(o -> o.getCustomer().getId()));
        return groups.entrySet().stream().map(e -> new Stat(e.getKey(), (long) e.getValue().size(),
                e.getValue().stream().mapToDouble(Order::getPrice).sum())).toList();
    }

    private ValueQuery<Stat> perCustomer() {
        return qb.from(Order.class)
                .where(o -> o.getCustomer() != null)
                .groupBy(o -> o.getCustomer().getId())
                .select(o -> new Stat(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice())));
    }

    @Test
    void derived() {
        // @start region="derived"
        // the rows of a group query are the table of the outer query; r.total() reads the column "total"
        DerivedQuery<Stat> q = qb.from(qb.from(Order.class)
                        .where(o -> o.getCustomer() != null)
                        .groupBy(o -> o.getCustomer().getId())
                        .select(o -> new Stat(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice()))))
                .where(r -> r.total() > 100)
                .orderBy((r, by) -> by.desc(r.total()));
        assertEquals("select e0.customerId, e0.cnt, e0.total from (select d0.customer.id as customerId, count(*) as cnt,"
                + " sum(d0.price) as total from Order d0 where d0.customer is not null group by d0.customer.id) e0"
                + " where e0.total > 100.0 order by e0.total desc nulls last", q.toHql().hql());
        // @end
        List<Stat> expected = stats().stream().filter(s -> s.total() > 100)
                .sorted(Comparator.comparing(Stat::total).reversed()).toList();
        assertEquals(expected, q.toList());
        assertEquals(expected.size(), q.count());
    }

    @Test
    void cte() {
        // @start region="cte"
        Cte<Stat> t = qb.with(perCustomer());
        DerivedQuery<Stat> q = qb.from(t).where(r -> r.cnt() >= 2);
        assertEquals("with t as (select d0.customer.id as customerId, count(*) as cnt, sum(d0.price) as total"
                + " from Order d0 where d0.customer is not null group by d0.customer.id)"
                + " select e0.customerId, e0.cnt, e0.total from t e0 where e0.cnt >= 2L", q.toHql().hql());
        // @end
        List<Stat> expected = stats().stream().filter(s -> s.cnt() >= 2).sorted(Comparator.comparing(Stat::customerId)).toList();
        assertEquals(expected, q.toList().stream().sorted(Comparator.comparing(Stat::customerId)).toList());
    }

    @Test
    void selectAndCapturedValue() {
        double min = 60;
        // @start region="select"
        // a value of the row; captured values inside and outside are bound in order
        ValueQuery<Integer> ids = qb.from(qb.from(Order.class)
                        .where(o -> o.getCustomer() != null && o.getPrice() > min)
                        .groupBy(o -> o.getCustomer().getId())
                        .select(o -> new Stat(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice()))))
                .where(r -> r.total() > min * 2)
                .select(r -> r.customerId());
        // @end
        List<Integer> expected = orders.stream().filter(o -> o.getCustomer() != null && o.getPrice() > min)
                .collect(Collectors.groupingBy(o -> o.getCustomer().getId(), Collectors.summingDouble(Order::getPrice)))
                .entrySet().stream().filter(e -> e.getValue() > min * 2).map(Map.Entry::getKey).sorted().toList();
        assertEquals(expected, ids.toList().stream().sorted().toList(), ids.toHql().hql());
    }

    @Test
    void innerLimit() {
        // @start region="inner-limit"
        // the three most expensive orders, then the cheapest of them
        Optional<Priced> cheapestOfTop = qb.from(qb.from(Order.class)
                        .orderBy((o, by) -> by.desc(o.getPrice()))
                        .limit(3)
                        .select(o -> new Priced(o.getId(), o.getPrice())))
                .orderBy((r, by) -> by.asc(r.price()))
                .first();
        // @end
        Order expected = orders.stream().sorted(Comparator.comparing(Order::getPrice).reversed()).limit(3)
                .min(Comparator.comparing(Order::getPrice)).orElseThrow();
        assertEquals(new Priced(expected.getId(), expected.getPrice()), cheapestOfTop.orElseThrow());
    }

    @Test
    void rules() {
        // @start region="rules"
        // J5: the row type must be a record or Kotlin data class (its components name the columns)
        assertEquals("LQL-3064", assertThrows(LambdaQLException.class,
                () -> qb.from(qb.from(Order.class).select(o -> o.getPrice()))).code());
        // @end
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = "org.lambdaql.query")
    static class Context {
    }
}
