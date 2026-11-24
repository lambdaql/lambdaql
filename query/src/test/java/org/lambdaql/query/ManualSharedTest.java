package org.lambdaql.query;

import static org.lambdaql.query.By.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lambdaql.Agg;
import org.lambdaql.DbOnlyException;
import org.lambdaql.LambdaQLException;
import org.lambdaql.UnsupportedLambdaShapeException;
import org.lambdaql.i18n.LqlMessages;
import org.lambdaql.query.FunctionTest.OrderFns;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Java examples of the shared manual pages: the manual home (README), 1. Overview and 11. Errors and languages.
 * Each test also computes the expected rows in Java from the seeded data.
 * The region markers are used by the manual.
 */
@SpringBootTest(classes = ManualSharedTest.Context.class)
@ActiveProfiles("test")
class ManualSharedTest {

    /** A row with two columns. */
    record OrderView(Long id, double price) {
    }

    /** The order count and the total of one customer. */
    record CustomerTotal(Integer customerId, long count, Double total) {
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
        for (String e : List.of("Summary", "Item", "Order", "Customer")) em.createQuery("delete from " + e).executeUpdate();
        Customer kim = new Customer(1, "Kim", null);
        Customer lee = new Customer(2, "Lee", null);
        em.persist(kim);
        em.persist(lee);
        Object[][] rows = {{1, 50.0, true, kim}, {1, 150.0, true, lee}, {2, 250.0, true, null}, {1, 120.0, false, null},
                {3, 980.0, true, kim}, {2, 90.0, true, kim}};
        for (Object[] r : rows) {
            Order o = new Order();
            o.setStatus(((Integer) r[0]).shortValue());
            o.setPrice((Double) r[1]);
            o.setActive((Boolean) r[2]);
            o.setCustomer((Customer) r[3]);
            o.setDescription(o.getPrice() > 100 ? "x" : null);
            em.persist(o);
        }
        em.getTransaction().commit();
        orders = em.createQuery("select o from Order o left join fetch o.customer", Order.class).getResultList();
        em.close();
    }

    private static List<Long> ids(List<Order> list) {
        return list.stream().map(Order::getId).sorted().toList();
    }

    private static String customerName(Order o) {
        return o.getCustomer() == null ? null : o.getCustomer().getName();
    }

    @Test
    void overviewFirst() {
        // @start region="overview-first"
        long minPrice = 100;

        SelectQuery<Order> q = qb.from(Order.class)
                .where(o -> o.getStatus() == 1 || o.getCustomer().getName().equals("Kim"))
                .where(o -> o.getPrice() > minPrice);
        assertEquals("select e0 from Order e0 left join e0.customer j1"
                + " where (e0.status = 1 or j1.name = 'Kim') and e0.price > :p1", q.toHql().hql());
        List<Order> rows = q.toList();
        // @end
        // an order without a customer stays when its status is 1, as in Java
        assertEquals(ids(orders.stream().filter(o -> (o.getStatus() == 1 || "Kim".equals(customerName(o)))
                && o.getPrice() > minPrice).toList()), ids(rows));
    }

    @Test
    void oneLambdaPerClause() {
        // @start region="one-lambda-per-clause"
        ValueQuery<OrderView> q = qb.from(Order.class)
                .where(o -> o.getStatus() == 1 && o.isActive())                 // one condition lambda
                .orderBy(o -> desc(o.getPrice()).asc(o.getId()))     // one sort lambda for all keys
                .select(o -> new OrderView(o.getId(), o.getPrice()));          // one select lambda
        assertEquals("select e0.id, e0.price from Order e0 where e0.status = 1 and e0.active = true"
                + " order by e0.price desc, e0.id asc", q.toHql().hql());
        // @end
        assertEquals(orders.stream().filter(o -> o.getStatus() == 1 && o.isActive())
                .sorted(Comparator.comparing(Order::getPrice).reversed().thenComparing(Order::getId))
                .map(o -> new OrderView(o.getId(), o.getPrice())).toList(), q.toList());
    }

    @Test
    void aggregate() {
        // @start region="agg"
        ValueQuery<CustomerTotal> q = qb.from(Order.class)
                .groupBy(o -> o.getCustomer().getId())
                .having(o -> Agg.sum(o.getPrice()) > 1000)
                .select(o -> new CustomerTotal(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice())));
        assertEquals("select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id"
                + " having sum(e0.price) > 1000.0", q.toHql().hql());
        // @end
        Map<Integer, List<Order>> groups = orders.stream()
                .collect(Collectors.groupingBy(o -> o.getCustomer() == null ? -1 : o.getCustomer().getId()));
        List<CustomerTotal> expected = groups.entrySet().stream()
                .map(e -> new CustomerTotal(e.getKey() == -1 ? null : e.getKey(), e.getValue().size(),
                        e.getValue().stream().mapToDouble(Order::getPrice).sum()))
                .filter(t -> t.total() > 1000).toList();
        assertEquals(expected, q.toList());
    }

    @Test
    void readmeFirst() {
        double minPrice = 100;
        // @start region="readme-first"
        List<Order> orders = qb.from(Order.class)
                .where(o -> o.getStatus() == 1 && o.getPrice() > minPrice)
                .orderBy(o -> desc(o.getPrice()).asc(o.getId()))
                .limit(20)
                .toList();
        // @end
        assertEquals(this.orders.stream().filter(o -> o.getStatus() == 1 && o.getPrice() > minPrice)
                .sorted(Comparator.comparing(Order::getPrice).reversed().thenComparing(Order::getId))
                .limit(20).map(Order::getId).toList(), orders.stream().map(Order::getId).toList());
        assertEquals("select e0 from Order e0 where e0.status = 1 and e0.price > :p1 order by e0.price desc, e0.id asc",
                qb.from(Order.class)
                        .where(o -> o.getStatus() == 1 && o.getPrice() > minPrice)
                        .orderBy(o -> desc(o.getPrice()).asc(o.getId()))
                        .limit(20).toHql().hql());
    }

    @Test
    void sargableForms() {
        Long id = orders.get(0).getId();
        String text = "x";
        // a null check that the id never needs is left out
        SelectQuery<Order> byId = qb.from(Order.class).where(o -> o.getId() != null && o.getId() == id);
        assertEquals("select e0 from Order e0 where e0.id = :p1", byId.toHql().hql());
        assertEquals(List.of(id), ids(byId.toList()));
        // a reference == on a string is a plain =, not a null-safe compare
        @SuppressWarnings("StringEquality")
        SelectQuery<Order> byText = qb.from(Order.class).where(o -> o.getDescription() == text);
        assertEquals("select e0 from Order e0 where e0.description = :p1", byText.toHql().hql());
        assertEquals(ids(orders.stream().filter(o -> Objects.equals(o.getDescription(), text)).toList()), ids(byText.toList()));
    }

    @Test
    void catchTheError() {
        // @start region="catch"
        String logged;
        try {
            qb.from(Order.class)
                    .where(o -> switch (o.getStatus()) { case 1, 2 -> true; default -> false; })   // a switch: not supported
                    .toList();
            logged = null;
        } catch (LambdaQLException e) {
            // for example log.error("lambdaQL error {}: {}", e.code(), e.getMessage(Locale.ROOT))
            logged = "lambdaQL error " + e.code() + ": " + e.getMessage(Locale.ROOT);
        }
        // @end
        assertTrue(logged != null && logged.startsWith("lambdaQL error LQL-2010: [LQL-2010] "), logged);
    }

    @Test
    void checkTheCode() {
        // @start region="check-code"
        var e = assertThrows(LambdaQLException.class,
                () -> qb.from(Order.class).where(o -> switch (o.getStatus()) { case 1, 2 -> true; default -> false; }));
        assertEquals("LQL-2010", e.code());                 // good
        assertInstanceOf(UnsupportedLambdaShapeException.class, e);
        // assertEquals("Could not ...", e.getMessage());   // do not do this
        // @end
    }

    @Test
    void dbOnly() {
        // @start region="db-only"
        // the body never runs in Java
        assertEquals("LQL-4034", assertThrows(DbOnlyException.class, () -> OrderFns.discount(1.0)).code());
        // @end
    }

    @Test
    void setLocale() {
        try {
            // @start region="set-locale"
            // at application start
            LqlMessages.setLocale(Locale.JAPANESE);   // Japanese messages
            Locale current = LqlMessages.locale();    // the locale lambdaQL uses now
            LqlMessages.setLocale(null);              // back to the default: the system property, then the JVM default
            // @end
            assertEquals(Locale.JAPANESE, current);
        } finally {
            LqlMessages.setLocale(null);
        }
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = "org.lambdaql.query")
    static class Context {
    }
}
