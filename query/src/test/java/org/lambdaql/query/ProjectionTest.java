package org.lambdaql.query;

import static org.lambdaql.query.By.*;

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
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end tests for projection and aggregates (ROADMAP M9): {@code select}, {@code groupBy}, {@code having},
 * {@code distinct}. Each test also computes the expected values in Java from the seeded data.
 * The region markers are used by Javadoc snippet tags.
 */
@SpringBootTest(classes = ProjectionTest.Context.class)
@ActiveProfiles("test")
class ProjectionTest {

    /** A row of one order. */
    record OrderView(Long id, String customerName, double price) {
    }

    /** A row of one customer group. */
    record Stat(Integer customerId, long count, Double total) {
    }

    /** A row of one (customer, status) group. */
    record KeyCount(Integer customerId, short status, long count) {
    }

    /** One row over all orders. */
    record Total(long count, Double average) {
    }

    /** A wrong row type: {@code double} cannot hold NULL. */
    record Bad(Integer customerId, double total) {
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
        Object[][] rows = {{1, 50.0, kim}, {1, 150.0, kim}, {2, 250.0, lee}, {1, 120.0, null}, {3, 80.0, kim}, {1, 150.0, lee}};
        for (Object[] r : rows) {
            Order o = new Order();
            o.setStatus(((Integer) r[0]).shortValue());
            o.setPrice((Double) r[1]);
            o.setActive(true);
            o.setCustomer((Customer) r[2]);
            em.persist(o);
        }
        em.getTransaction().commit();
        orders = em.createQuery("select o from Order o left join fetch o.customer", Order.class).getResultList();
        em.close();
    }

    private static Integer customerId(Order o) {
        return o.getCustomer() == null ? null : o.getCustomer().getId();
    }

    @Test
    void value() {
        // @start region="value"
        ValueQuery<Double> q = qb.from(Order.class)
                .where(o -> o.getStatus() == 1)
                .orderBy(o -> asc(o.getPrice()))
                .select(o -> o.getPrice());
        assertEquals("select e0.price from Order e0 where e0.status = 1 order by e0.price asc", q.toHql().hql());
        // @end
        assertEquals(orders.stream().filter(o -> o.getStatus() == 1).map(Order::getPrice).sorted().toList(), q.toList());
    }

    @Test
    void record() {
        // @start region="record"
        // only the columns are selected; the record is made in Java. The path uses a left join,
        // so the order without a customer stays (customerName is null)
        ValueQuery<OrderView> q = qb.from(Order.class)
                .select(o -> new OrderView(o.getId(), o.getCustomer().getName(), o.getPrice()));
        assertEquals("select e0.id, j1.name, e0.price from Order e0 left join e0.customer j1", q.toHql().hql());
        // @end
        List<OrderView> expected = orders.stream()
                .map(o -> new OrderView(o.getId(), o.getCustomer() == null ? null : o.getCustomer().getName(), o.getPrice()))
                .sorted(Comparator.comparing(OrderView::id)).toList();
        assertEquals(expected, q.toList().stream().sorted(Comparator.comparing(OrderView::id)).toList());
    }

    @Test
    void groupBy() {
        // @start region="group-by"
        ValueQuery<Stat> q = qb.from(Order.class)
                .groupBy(o -> o.getCustomer().getId())
                .having(o -> Agg.sum(o.getPrice()) > 200)
                .select(o -> new Stat(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice())));
        assertEquals("select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id"
                + " having sum(e0.price) > 200.0", q.toHql().hql());
        // @end
        Map<Integer, List<Order>> groups = orders.stream()
                .collect(Collectors.groupingBy(o -> Optional.ofNullable(customerId(o)).orElse(-1)));
        List<Stat> expected = groups.entrySet().stream()
                .map(e -> new Stat(e.getKey() == -1 ? null : e.getKey(), e.getValue().size(),
                        e.getValue().stream().mapToDouble(Order::getPrice).sum()))
                .filter(s -> s.total() > 200)
                .sorted(Comparator.comparing(Stat::customerId, Comparator.nullsFirst(Comparator.naturalOrder()))).toList();
        assertEquals(expected, q.toList().stream()
                .sorted(Comparator.comparing(Stat::customerId, Comparator.nullsFirst(Comparator.naturalOrder()))).toList());
    }

    @Test
    void groupByMany() {
        // @start region="group-many"
        // many keys: List.of(...) (Kotlin listOf(...), a to b, Triple)
        ValueQuery<KeyCount> q = qb.from(Order.class)
                .where(o -> o.getCustomer() != null)
                .groupBy(o -> List.of(o.getCustomer().getId(), o.getStatus()))
                .select(o -> new KeyCount(o.getCustomer().getId(), o.getStatus(), Agg.count()));
        assertEquals("select e0.customer.id, e0.status, count(*) from Order e0 where e0.customer is not null"
                + " group by e0.customer.id, e0.status", q.toHql().hql());
        // @end
        Comparator<KeyCount> order = Comparator.comparing(KeyCount::customerId).thenComparing(KeyCount::status);
        List<KeyCount> expected = orders.stream().filter(o -> o.getCustomer() != null)
                .collect(Collectors.groupingBy(o -> List.of(o.getCustomer().getId(), o.getStatus()), Collectors.counting()))
                .entrySet().stream()
                .map(e -> new KeyCount((Integer) e.getKey().get(0), (Short) e.getKey().get(1), e.getValue()))
                .sorted(order).toList();
        assertEquals(expected, q.toList().stream().sorted(order).toList());
    }

    @Test
    void total() {
        // @start region="total"
        // aggregates without groupBy: one row over all matching orders
        ValueQuery<Total> q = qb.from(Order.class)
                .where(o -> o.getStatus() == 1)
                .select(o -> new Total(Agg.count(), Agg.avg(o.getPrice())));
        assertEquals("select count(*), avg(e0.price) from Order e0 where e0.status = 1", q.toHql().hql());
        // @end
        List<Order> ones = orders.stream().filter(o -> o.getStatus() == 1).toList();
        Total t = q.one().orElseThrow();
        assertEquals(ones.size(), t.count());
        assertEquals(ones.stream().mapToDouble(Order::getPrice).average().orElseThrow(), t.average(), 1e-9);
    }

    @Test
    void distinct() {
        // @start region="distinct"
        ValueQuery<Double> q = qb.from(Order.class)
                .orderBy(o -> desc(o.getPrice()))
                .select(o -> o.getPrice())
                .distinct();
        assertEquals("select distinct e0.price from Order e0 order by e0.price desc", q.toHql().hql());
        // toSet() always uses select distinct
        List<Double> prices = q.toSet().stream().toList();
        // @end
        List<Double> expected = orders.stream().map(Order::getPrice).distinct().sorted(Comparator.reverseOrder()).toList();
        assertEquals(expected, q.toList());
        assertEquals(expected, prices);
    }

    @Test
    void pagingAndFirst() {
        ValueQuery<Double> q = qb.from(Order.class)
                .orderBy(o -> desc(o.getPrice()))
                .select(o -> o.getPrice());
        List<Double> sorted = orders.stream().sorted(Comparator.comparing(Order::getPrice).reversed()
                .thenComparing(Order::getId)).map(Order::getPrice).toList();
        assertEquals(sorted.get(0), q.first().orElseThrow());
        assertEquals(sorted.subList(1, 3), q.limit(2).offset(1).toList());
        // paging adds the id tie-breaker (S9), so the pages do not overlap
        assertTrue(q.toHql().hql().endsWith("order by e0.price desc, e0.id asc"), q.toHql().hql());
        // first() reads one row, so it gets the same tie-breaker: of two orders with price 150, always the smaller id
        ch.qos.logback.classic.Logger sql = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("org.hibernate.SQL");
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> seen = new ch.qos.logback.core.read.ListAppender<>();
        seen.start();
        sql.addAppender(seen);
        ch.qos.logback.classic.Level old = sql.getLevel();
        sql.setLevel(ch.qos.logback.classic.Level.DEBUG);
        try {
            Long tied = qb.from(Order.class).where(o -> o.getPrice() == 150)
                    .orderBy(o -> desc(o.getPrice())).select(o -> o.getId()).first().orElseThrow();
            assertEquals(orders.stream().filter(o -> o.getPrice() == 150).map(Order::getId).min(Comparator.naturalOrder()).orElseThrow(), tied);
            String last = seen.list.get(seen.list.size() - 1).getFormattedMessage().replaceAll("\\s+", " ");
            assertTrue(last.matches(".*order by o1_0\\.price desc, ?o1_0\\.id.*"), last);
        } finally {
            sql.setLevel(old);
            sql.detachAppender(seen);
        }
    }

    @Test
    void insertGroup() {
        // @start region="insert-group"
        // INSERT ... SELECT with groupBy: one summary row per customer
        ExecutableInsert<Summary> i = qb.insert(Summary.class)
                .from(Order.class)
                .where(o -> o.getCustomer() != null)
                .groupBy(o -> o.getCustomer().getId())
                .having(o -> Agg.count() >= 2)
                .select((s, o) -> { s.setCustomerId(o.getCustomer().getId()); s.setTotal(Agg.sum(o.getPrice())); s.setCnt(Agg.count()); });
        assertEquals("insert into Summary (customerId, total, cnt) select e0.customer.id, sum(e0.price), count(*) from Order e0"
                + " where e0.customer is not null group by e0.customer.id having count(*) >= 2L", i.toHql().get(0).hql());
        // @end
        Map<Integer, List<Order>> groups = orders.stream().filter(o -> o.getCustomer() != null)
                .collect(Collectors.groupingBy(ProjectionTest::customerId));
        long expected = groups.values().stream().filter(l -> l.size() >= 2).count();
        assertEquals(expected, i.execute());
        List<Stat> rows = qb.from(Summary.class).orderBy(s -> asc(s.getCustomerId()))
                .select(s -> new Stat(s.getCustomerId(), s.getCnt(), s.getTotal())).toList();
        List<Stat> want = groups.entrySet().stream().filter(e -> e.getValue().size() >= 2)
                .map(e -> new Stat(e.getKey(), e.getValue().size(), e.getValue().stream().mapToDouble(Order::getPrice).sum()))
                .sorted(Comparator.comparing(Stat::customerId)).toList();
        assertEquals(want, rows);
    }

    @Test
    void rules() {
        // @start region="rules"
        // J3: a value without an aggregate must be a group key
        LambdaQLException j3 = assertThrows(LambdaQLException.class, () -> qb.from(Order.class)
                .groupBy(o -> o.getCustomer().getId())
                .select(o -> new KeyCount(o.getCustomer().getId(), o.getStatus(), Agg.count())));
        assertEquals("LQL-3035", j3.code());
        // aggregates belong in having, not in where
        LambdaQLException where = assertThrows(LambdaQLException.class, () -> qb.from(Order.class).where(o -> Agg.count() > 1));
        assertEquals("LQL-3005", where.code());
        // J7: with distinct, the sort key must be selected
        LambdaQLException j7 = assertThrows(LambdaQLException.class, () -> qb.from(Order.class)
                .orderBy(o -> asc(o.getId())).select(o -> o.getPrice()).distinct().toHql());
        assertEquals("LQL-3046", j7.code());
        // J4: the group without orders gives NULL; a primitive argument cannot take it
        assertThrows(IllegalStateException.class, () -> qb.from(Order.class)
                .where(o -> o.getStatus() == 9)
                .select(o -> new Bad(null, Agg.sum(o.getPrice()))).toList());
        // @end
        assertEquals(0, Objects.requireNonNullElse(null, 0));
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = "org.lambdaql.query")
    static class Context {
    }
}
