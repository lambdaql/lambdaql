package org.lambdaql.query;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lambdaql.LambdaQLException;
import org.lambdaql.Agg;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * End-to-end tests for explicit entity joins (ROADMAP D44): {@code join}, {@code leftJoin}, conditions, sorting and
 * select on all entities. Each test also computes the expected rows in Java from the seeded data.
 * The region markers are used by Javadoc snippet tags.
 */
@SpringBootTest(classes = JoinQueryTest.Context.class)
@ActiveProfiles("test")
class JoinQueryTest {

    /** A row with values of two entities. */
    record Row(Long orderId, String customerName) {
    }

    @Autowired
    private EntityManagerFactory emf;

    private QueryBuilder qb;
    private List<Order> orders;
    private List<Customer> customers;
    private final List<long[]> items = new ArrayList<>();       // {orderId, qty}

    @BeforeEach
    void seed() {
        qb = new QueryBuilder(emf);
        items.clear();
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        for (String e : List.of("Item", "Order", "Customer")) em.createQuery("delete from " + e).executeUpdate();
        Customer kim = new Customer(1, "Kim", null);
        kim.setGrade(5);
        Customer lee = new Customer(2, "Lee", null);
        lee.setGrade(2);
        Customer park = new Customer(3, "Park", null);
        em.persist(kim);
        em.persist(lee);
        em.persist(park);
        Object[][] rows = {{50.0, kim}, {150.0, kim}, {250.0, lee}, {120.0, null}};
        List<Order> saved = new ArrayList<>();
        for (Object[] r : rows) {
            Order o = new Order();
            o.setStatus((short) 1);
            o.setPrice((Double) r[0]);
            o.setActive(true);
            o.setCustomer((Customer) r[1]);
            em.persist(o);
            saved.add(o);
        }
        int[][] its = {{0, 5}, {0, 20}, {1, 15}, {2, 1}};
        for (int[] r : its) {
            Item i = new Item();
            i.setOrder(saved.get(r[0]));
            i.setQty(r[1]);
            i.setPrice(1.0);
            em.persist(i);
            items.add(new long[]{saved.get(r[0]).getId(), r[1]});
        }
        em.getTransaction().commit();
        orders = em.createQuery("select o from Order o left join fetch o.customer", Order.class).getResultList();
        customers = em.createQuery("select c from Customer c", Customer.class).getResultList();
        em.close();
    }

    private static List<Long> ids(List<Order> list) {
        return list.stream().map(Order::getId).sorted().toList();
    }

    private Customer customerOf(Order o) {
        return o.getCustomer() == null ? null
                : customers.stream().filter(c -> c.getId() == o.getCustomer().getId()).findFirst().orElseThrow();
    }

    @Test
    void join() {
        // @start region="join"
        SelectJoinQuery<Order, Customer> q = qb.from(Order.class)
                .join(Customer.class, (o, c) -> o.getCustomer().getId() == c.getId())
                .where((o, c) -> c.getGrade() != null && c.getGrade() >= 5);
        assertEquals("select e0 from Order e0 join Customer e1 on e0.customer.id = e1.id"
                + " where e1.grade is not null and e1.grade >= 5", q.toHql().hql());
        // @end
        assertEquals(ids(orders.stream().filter(o -> customerOf(o) != null && customerOf(o).getGrade() != null
                && customerOf(o).getGrade() >= 5).toList()), ids(q.toList()));
        assertEquals(2, q.count());
    }

    @Test
    void leftJoin() {
        // @start region="left-join"
        // c is missing for an order without a customer: c == null finds those rows
        SelectJoinQuery<Order, Customer> q = qb.from(Order.class)
                .leftJoin(Customer.class, (o, c) -> o.getCustomer().getId() == c.getId())
                .where((o, c) -> c == null || c.getName().equals("Kim"));
        assertEquals("select e0 from Order e0 left join Customer e1 on e0.customer.id = e1.id"
                + " where e1.id is null or e1.name = 'Kim'", q.toHql().hql());
        // @end
        assertEquals(ids(orders.stream().filter(o -> customerOf(o) == null || customerOf(o).getName().equals("Kim")).toList()),
                ids(q.toList()), q.toHql().hql());
    }

    @Test
    void sortAndSelect() {
        // @start region="sort"
        OrderedJoinQuery<Order, Customer> q = qb.from(Order.class)
                .leftJoin(Customer.class, (o, c) -> o.getCustomer().getId() == c.getId())
                .orderBy((o, c, by) -> by.asc(c.getName()).asc(o.getId()));
        assertEquals("select e0 from Order e0 left join Customer e1 on e0.customer.id = e1.id"
                + " order by e1.name asc nulls first, e0.id asc", q.toHql().hql());
        // @end
        List<Long> expected = orders.stream().sorted(Comparator.comparing((Order o) -> customerOf(o) == null ? null : customerOf(o).getName(),
                Comparator.nullsFirst(Comparator.naturalOrder())).thenComparing(Order::getId)).map(Order::getId).toList();
        assertEquals(expected, q.toList().stream().map(Order::getId).toList(), q.toHql().hql());
        // @start region="select"
        ValueQuery<Row> rows = qb.from(Order.class)
                .leftJoin(Customer.class, (o, c) -> o.getCustomer().getId() == c.getId())
                .orderBy((o, c, by) -> by.asc(o.getId()))
                .select((o, c) -> new Row(o.getId(), c.getName()));
        assertEquals("select e0.id, e1.name from Order e0 left join Customer e1 on e0.customer.id = e1.id"
                + " order by e0.id asc", rows.toHql().hql());
        // @end
        assertEquals(orders.stream().sorted(Comparator.comparing(Order::getId))
                .map(o -> new Row(o.getId(), customerOf(o) == null ? null : customerOf(o).getName())).toList(), rows.toList(), rows.toHql().hql());
    }

    @Test
    void threeEntities() {
        // @start region="three"
        // orders of a customer with an item of quantity > 10; an order repeats once per matching item
        SelectJoin3Query<Order, Customer, Item> q = qb.from(Order.class)
                .join(Customer.class, (o, c) -> o.getCustomer().getId() == c.getId())
                .join(Item.class, (o, c, i) -> i.getOrder().getId() == o.getId())
                .where((o, c, i) -> i.getQty() > 10);
        assertEquals("select e0 from Order e0 join Customer e1 on e0.customer.id = e1.id"
                + " join Item e2 on e2.order.id = e0.id where e2.qty > 10", q.toHql().hql());
        // @end
        List<Long> expected = new ArrayList<>();
        for (Order o : orders) {
            if (customerOf(o) == null) continue;
            for (long[] i : items) if (i[0] == o.getId() && i[1] > 10) expected.add(o.getId());
        }
        assertEquals(expected.stream().sorted().toList(), ids(q.toList()), q.toHql().hql());
        assertEquals(expected.size(), q.count());
        assertEquals(0, Objects.requireNonNullElse(null, 0));
    }

    /** The order total of one customer, the row type of a CTE or derived table. */
    record Total(Integer customerId, Double total) {
    }

    /** A customer name with a total. */
    record NameTotal(String name, Double total) {
    }

    private ValueQuery<Total> totals() {
        return qb.from(Order.class)
                .where(o -> o.getCustomer() != null)
                .groupBy(o -> o.getCustomer().getId())
                .select(o -> new Total(o.getCustomer().getId(), Agg.sum(o.getPrice())));
    }

    @Test
    void distinctRows() {
        // @start region="join-distinct"
        // an order with two matching items would come twice; distinct() returns it once
        SelectJoinQuery<Order, Item> q = qb.from(Order.class)
                .join(Item.class, (o, i) -> i.getOrder().getId() == o.getId())
                .where((o, i) -> i.getQty() > 0)
                .distinct();
        assertEquals("select distinct e0 from Order e0 join Item e1 on e1.order.id = e0.id where e1.qty > 0", q.toHql().hql());
        // a sort key must be a column of the main entity (J7)
        OrderedJoinQuery<Order, Item> sorted = q.orderBy((o, i, by) -> by.desc(o.getPrice()));
        assertEquals("select distinct e0 from Order e0 join Item e1 on e1.order.id = e0.id where e1.qty > 0"
                + " order by e0.price desc", sorted.toHql().hql());
        // @end
        List<Long> expected = items.stream().filter(i -> i[1] > 0).map(i -> i[0]).distinct().sorted().toList();
        assertEquals(expected, ids(q.toList()));
        assertEquals(expected.size(), q.count());
        assertEquals(orders.stream().filter(o -> expected.contains(o.getId()))
                .sorted(Comparator.comparing(Order::getPrice).reversed()).map(Order::getId).toList(),
                sorted.toList().stream().map(Order::getId).toList());
        // paging adds only the main id: distinct rows do not repeat
        assertTrue(sorted.limit(1).toHql().hql().endsWith("order by e0.price desc, e0.id asc"));
        LambdaQLException j7 = assertThrows(LambdaQLException.class, () -> q.orderBy((o, i, by) -> by.desc(i.getQty())));
        assertEquals("LQL-3046", j7.code());
    }

    @Test
    void joinDerived() {
        // @start region="join-cte"
        // the total of each customer, joined to the customer row
        Cte<Total> t = qb.with(qb.from(Order.class)
                .where(o -> o.getCustomer() != null)
                .groupBy(o -> o.getCustomer().getId())
                .select(o -> new Total(o.getCustomer().getId(), Agg.sum(o.getPrice()))));
        ValueQuery<NameTotal> rows = qb.from(Customer.class)
                .join(t, (c, x) -> x.customerId().equals(c.getId()))
                .where((c, x) -> x.total() > 100)
                .select((c, x) -> new NameTotal(c.getName(), x.total()));
        assertEquals("with t as (select d0.customer.id as customerId, sum(d0.price) as total from Order d0"
                + " where d0.customer is not null group by d0.customer.id)"
                + " select e0.name, e1.total from Customer e0 join t e1 on e1.customerId = e0.id where e1.total > 100.0",
                rows.toHql().hql());
        // @end
        assertEquals(List.of(new NameTotal("Kim", 200.0), new NameTotal("Lee", 250.0)),
                rows.toList().stream().sorted(Comparator.comparing(NameTotal::name)).toList());
        // @start region="join-derived"
        // a derived table without a name; a customer without orders has no row in it (left join)
        SelectJoinQuery<Customer, Total> small = qb.from(Customer.class)
                .leftJoin(totals(), (c, x) -> x.customerId().equals(c.getId()))
                .where((c, x) -> x.customerId() == null || x.total() < 210);
        assertEquals("select e0 from Customer e0 left join (select d0.customer.id as customerId, sum(d0.price) as total"
                + " from Order d0 where d0.customer is not null group by d0.customer.id) e1 on e1.customerId = e0.id"
                + " where e1.customerId is null or e1.total < 210.0", small.toHql().hql());
        // @end
        assertEquals(List.of("Kim", "Park"), small.toList().stream().map(Customer::getName).sorted().toList());
        assertEquals(2, small.count());
        // the row has no id, so x == null cannot say "no match": compare a column (LQL-3067)
        LambdaQLException noId = assertThrows(LambdaQLException.class, () -> qb.from(Customer.class)
                .leftJoin(totals(), (c, x) -> x.customerId().equals(c.getId())).where((c, x) -> x == null));
        assertEquals("LQL-3067", noId.code());
        // two CTEs in one statement get their own names
        Cte<Total> again = qb.with(totals());
        ValueQuery<NameTotal> twice = qb.from(Customer.class)
                .join(t, (c, x) -> x.customerId().equals(c.getId()))
                .join(again, (c, x, y) -> y.customerId().equals(c.getId()))
                .select((c, x, y) -> new NameTotal(c.getName(), y.total()));
        assertTrue(twice.toHql().hql().startsWith("with t as (") && twice.toHql().hql().contains("), t2 as ("), twice.toHql().hql());
        assertEquals(2, twice.toList().size());
        // @start region="cte-reuse"
        // the same CTE read twice is written once
        ValueQuery<NameTotal> reused = qb.from(Customer.class)
                .join(t, (c, x) -> x.customerId().equals(c.getId()))
                .leftJoin(t, (c, x, y) -> y.customerId().equals(c.getId()) && y.total() > 210)
                .select((c, x, y) -> new NameTotal(c.getName(), y.total()));
        assertEquals("with t as (select d0.customer.id as customerId, sum(d0.price) as total from Order d0"
                + " where d0.customer is not null group by d0.customer.id)"
                + " select e0.name, e2.total from Customer e0 join t e1 on e1.customerId = e0.id"
                + " left join t e2 on e2.customerId = e0.id and e2.total > 210.0", reused.toHql().hql());
        // @end
        assertEquals(List.of(new NameTotal("Kim", null), new NameTotal("Lee", 250.0)),
                reused.toList().stream().sorted(Comparator.comparing(NameTotal::name)).toList());
    }

    @Test
    void crossJoin() {
        // @start region="cross-join"
        // every pair of rows; the conditions go in where
        SelectJoinQuery<Order, Customer> q = qb.from(Order.class)
                .crossJoin(Customer.class)
                .where((o, c) -> c.getGrade() != null && o.getPrice() > 100);
        assertEquals("select e0 from Order e0 cross join Customer e1 where e1.grade is not null and e0.price > 100.0",
                q.toHql().hql());
        // @end
        long graded = customers.stream().filter(c -> c.getGrade() != null).count();
        assertEquals(orders.stream().filter(o -> o.getPrice() > 100).count() * graded, q.count());
    }

    @Test
    void crossJoinPaging() {
        // @start region="cross-join-paging"
        // with paging, the id of every entity is added to the order, so pages never overlap (S9)
        ValueQuery<Row> pairs = qb.from(Order.class)
                .crossJoin(Customer.class)
                .orderBy((o, c, by) -> by.desc(o.getPrice()))
                .select((o, c) -> new Row(o.getId(), c.getName()));
        assertEquals("select e0.id, e1.name from Order e0 cross join Customer e1 order by e0.price desc, e0.id asc, e1.id asc",
                pairs.limit(5).toHql().hql());
        // @end
        List<Row> all = new ArrayList<>();
        for (int page = 0; page < 3; page++) all.addAll(pairs.limit(5).offset(page * 5).toList());
        List<Row> expected = new ArrayList<>();
        for (Order o : orders.stream().sorted(Comparator.comparing(Order::getPrice).reversed().thenComparing(Order::getId)).toList()) {
            for (Customer c : customers.stream().sorted(Comparator.comparing(Customer::getId)).toList()) {
                expected.add(new Row(o.getId(), c.getName()));
            }
        }
        assertEquals(expected, all);
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @EntityScan("org.lambdaql.query")
    static class Context {
    }
}
