package org.lambdaql.query;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lambdaql.LambdaQLException;
import org.lambdaql.engine.render.RenderedQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * End-to-end tests for subqueries (ROADMAP M8): {@code where((o, sub) -> sub.from(...)...)}.
 * Each test also computes the expected rows in Java from the seeded data.
 * The region markers are used by Javadoc snippet tags.
 */
@SpringBootTest(classes = SubqueryTest.Context.class)
@ActiveProfiles("test")
class SubqueryTest {

    @Autowired
    private EntityManagerFactory emf;

    private QueryBuilder qb;
    private final List<long[]> items = new ArrayList<>();       // {orderId, qty}
    private final Map<String, Long> ids = new java.util.HashMap<>();
    private List<Order> orders;
    private List<Customer> customers;

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
        Customer park = new Customer(3, "Park", null);       // grade NULL
        em.persist(kim);
        em.persist(lee);
        em.persist(park);
        Object[][] rows = {{"o1", 1, 50.0, kim}, {"o2", 1, 150.0, kim}, {"o3", 2, 250.0, lee}, {"o4", 1, 120.0, null}, {"o5", 3, 80.0, park}};
        for (Object[] r : rows) {
            Order o = new Order();
            o.setStatus(((Integer) r[1]).shortValue());
            o.setPrice((Double) r[2]);
            o.setActive(true);
            o.setCustomer((Customer) r[3]);
            em.persist(o);
            ids.put((String) r[0], o.getId());
        }
        Object[][] its = {{"o1", 5}, {"o1", 20}, {"o2", 15}, {"o3", 1}, {"o4", 12}};
        for (Object[] r : its) {
            Item i = new Item();
            i.setOrder(em.find(Order.class, ids.get((String) r[0])));
            i.setQty((Integer) r[1]);
            i.setPrice(1.0);
            em.persist(i);
            items.add(new long[]{ids.get((String) r[0]), (Integer) r[1]});
        }
        em.getTransaction().commit();
        orders = em.createQuery("select o from Order o left join fetch o.customer", Order.class).getResultList();
        customers = em.createQuery("select c from Customer c", Customer.class).getResultList();
        em.close();
    }

    private static List<Long> idsOf(List<Order> list) {
        return list.stream().map(Order::getId).sorted().toList();
    }

    private List<Long> java(Predicate<Order> p) {
        return orders.stream().filter(p).map(Order::getId).sorted().toList();
    }

    private boolean hasItem(Order o, Predicate<long[]> p) {
        return items.stream().anyMatch(i -> i[0] == o.getId() && p.test(i));
    }

    @Test
    void exists() {
        // @start region="exists"
        // correlated: the inner lambda uses the outer order o
        SelectQuery<Order> q = qb.from(Order.class)
                .where((o, sub) -> sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId() && i.getQty() > 10).exists());
        assertEquals("select e0 from Order e0 where exists (select 1 from Item s1 where s1.order.id = e0.id and s1.qty > 10)",
                q.toHql().hql());
        // @end
        assertEquals(java(o -> hasItem(o, i -> i[1] > 10)), idsOf(q.toList()));
    }

    @Test
    void notExistsWithOr() {
        // @start region="not-exists"
        SelectQuery<Order> q = qb.from(Order.class)
                .where((o, sub) -> o.getStatus() == 1 || !sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).exists());
        assertEquals("select e0 from Order e0 where e0.status = 1 or not exists (select 1 from Item s1 where s1.order.id = e0.id)",
                q.toHql().hql());
        // @end
        assertEquals(java(o -> o.getStatus() == 1 || !hasItem(o, i -> true)), idsOf(q.toList()));
    }

    @Test
    void in() {
        // @start region="in"
        SelectQuery<Order> q = qb.from(Order.class)
                .where((o, sub) -> sub.from(Customer.class).where(c -> c.getGrade() != null && c.getGrade() > 3)
                        .select(c -> c.getId()).contains(o.getCustomer().getId()));
        assertEquals("select e0 from Order e0 where e0.customer.id in"
                + " (select s1.id from Customer s1 where s1.grade is not null and s1.grade > 3)", q.toHql().hql());
        // @end
        assertEquals(java(o -> o.getCustomer() != null && o.getCustomer().getGrade() != null && o.getCustomer().getGrade() > 3),
                idsOf(q.toList()));
    }

    @Test
    void notInIsNotExists() {
        // @start region="not-in"
        // the SQL meaning (D48): NOT IN. The grades are 5, 2 and NULL, so no row matches: x NOT IN (5, 2, NULL)
        // is never true. To skip the NULL, say so in the subquery: where(c -> c.getGrade() != null)
        SelectQuery<Order> q = qb.from(Order.class)
                .where((o, sub) -> !sub.from(Customer.class).select(c -> c.getGrade()).contains((int) o.getStatus()));
        assertEquals("select e0 from Order e0 where e0.status not in (select s1.grade from Customer s1)", q.toHql().hql());
        SelectQuery<Order> withoutNull = qb.from(Order.class)
                .where((o, sub) -> !sub.from(Customer.class).where(c -> c.getGrade() != null).select(c -> c.getGrade())
                        .contains((int) o.getStatus()));
        // @end
        assertEquals(List.of(), idsOf(q.toList()));
        List<Integer> grades = customers.stream().map(Customer::getGrade).filter(java.util.Objects::nonNull).toList();
        assertEquals(java(o -> !grades.contains((int) o.getStatus())), idsOf(withoutNull.toList()));
    }

    @Test
    void scalarAggregate() {
        // @start region="scalar"
        SelectQuery<Order> q = qb.from(Order.class)
                .where((o, sub) -> o.getPrice() > sub.from(Order.class).avg(x -> x.getPrice()));
        assertEquals("select e0 from Order e0 where e0.price > (select avg(s1.price) from Order s1)", q.toHql().hql());
        // @end
        double avg = orders.stream().mapToDouble(Order::getPrice).average().orElseThrow();
        assertEquals(java(o -> o.getPrice() > avg), idsOf(q.toList()));
    }

    @Test
    void count() {
        // @start region="count"
        SelectQuery<Order> q = qb.from(Order.class)
                .where((o, sub) -> sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).count() >= 2);
        assertEquals("select e0 from Order e0 where (select count(s1) from Item s1 where s1.order.id = e0.id) >= 2L", q.toHql().hql());
        // @end
        assertEquals(java(o -> items.stream().filter(i -> i[0] == o.getId()).count() >= 2), idsOf(q.toList()));
    }

    @Test
    void topOne() {
        // @start region="top-one"
        // the most expensive order of each customer: a correlated scalar subquery with order + limit 1 (rule Q3)
        SelectQuery<Order> q = qb.from(Order.class)
                .where((o, sub) -> o.getId().equals(sub.from(Order.class)
                        .where(x -> x.getCustomer().getId() == o.getCustomer().getId())
                        .orderBy((x, by) -> by.desc(x.getPrice()))
                        .limit(1)
                        .select(x -> x.getId())
                        .first()));
        assertEquals("select e0 from Order e0 where e0.id = (select s1.id from Order s1 where s1.customer.id = e0.customer.id"
                + " order by s1.price desc, s1.id asc limit 1)", q.toHql().hql());
        // first() without limit(1) is rejected
        assertThrows(LambdaQLException.class, () -> qb.from(Order.class)
                .where((o, sub) -> o.getId().equals(sub.from(Order.class).select(x -> x.getId()).first())));
        // @end
        assertEquals(java(o -> o.getCustomer() != null && orders.stream()
                .filter(x -> x.getCustomer() != null && x.getCustomer().getId() == o.getCustomer().getId())
                .max(java.util.Comparator.comparing(Order::getPrice)).orElseThrow().getId().equals(o.getId())), idsOf(q.toList()));
    }

    @Test
    void nested() {
        // @start region="nested"
        // customers that have an order with an item of quantity > 10: a subquery inside a subquery
        SelectQuery<Customer> q = qb.from(Customer.class)
                .where((c, sub) -> sub.from(Order.class).where((o, sub2) -> o.getCustomer().getId() == c.getId()
                        && sub2.from(Item.class).where(i -> i.getOrder().getId() == o.getId() && i.getQty() > 10).exists()).exists());
        assertEquals("select e0 from Customer e0 where exists (select 1 from Order s1 where s1.customer.id = e0.id"
                + " and exists (select 1 from Item s2 where s2.order.id = s1.id and s2.qty > 10))", q.toHql().hql());
        // @end
        List<Integer> expected = customers.stream().filter(c -> orders.stream().anyMatch(o -> o.getCustomer() != null
                && o.getCustomer().getId() == c.getId() && hasItem(o, i -> i[1] > 10))).map(Customer::getId).sorted().toList();
        assertEquals(expected, q.toList().stream().map(Customer::getId).sorted().toList());
    }

    @Test
    void capturedValueInside() {
        int minQty = 10;
        RenderedQuery q = qb.from(Order.class)
                .where((o, sub) -> sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId() && i.getQty() > minQty).exists())
                .toHql();
        assertEquals("select e0 from Order e0 where exists (select 1 from Item s1 where s1.order.id = e0.id and s1.qty > :p1)", q.hql());
        assertEquals(Map.of("p1", 10), q.parameters());
        assertEquals(java(o -> hasItem(o, i -> i[1] > minQty)),
                idsOf(qb.from(Order.class).where((o, sub) -> sub.from(Item.class)
                        .where(i -> i.getOrder().getId() == o.getId() && i.getQty() > minQty).exists()).toList()));
    }

    @Test
    void deleteWithSubquery() {
        // @start region="dml-subquery"
        ExecutableDelete<Order> d = qb.delete(Order.class)
                .where((o, sub) -> !sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).exists());
        assertEquals("delete from Order e0 where not exists (select 1 from Item s1 where s1.order.id = e0.id)", d.toHql().hql());
        // @end
        List<Long> without = java(o -> !hasItem(o, i -> true));
        assertEquals(without.size(), d.execute());
        assertEquals(java(o -> hasItem(o, i -> true)), idsOf(qb.from(Order.class).toList()));
        assertEquals(0, Objects.requireNonNullElse(null, 0));
    }

    @Test
    void joinInSubquery() {
        // @start region="sub-join"
        // customers with an order that has an item of quantity > 10: the subquery joins Order and Item (D51)
        SelectQuery<Customer> q = qb.from(Customer.class).where((c, sub) -> sub.from(Order.class)
                .join(Item.class, (o, i) -> i.getOrder().getId() == o.getId())
                .where((o, i) -> o.getCustomer().getId() == c.getId() && i.getQty() > 10)
                .exists());
        assertEquals("select e0 from Customer e0 where exists (select 1 from Order s1 join Item s2 on s2.order.id = s1.id"
                + " where s1.customer.id = e0.id and s2.qty > 10)", q.toHql().hql());
        // an aggregate over the joined rows
        SelectQuery<Customer> total = qb.from(Customer.class).where((c, sub) -> sub.from(Order.class)
                .join(Item.class, (o, i) -> i.getOrder().getId() == o.getId())
                .where((o, i) -> o.getCustomer().getId() == c.getId())
                .sum((o, i) -> i.getQty()) > 30);
        assertEquals("select e0 from Customer e0 where (select sum(s2.qty) from Order s1 join Item s2 on s2.order.id = s1.id"
                + " where s1.customer.id = e0.id) > 30", total.toHql().hql());
        // @end
        Map<Integer, Long> qtyByCustomer = new java.util.HashMap<>();
        for (long[] i : items) {
            Order o = orders.stream().filter(x -> x.getId() == i[0]).findFirst().orElseThrow();
            if (o.getCustomer() != null) qtyByCustomer.merge(o.getCustomer().getId(), i[1], Long::sum);
        }
        List<String> big = customers.stream().filter(c -> items.stream().anyMatch(i -> i[1] > 10 && orders.stream()
                .anyMatch(o -> o.getId() == i[0] && o.getCustomer() != null && o.getCustomer().getId() == c.getId())))
                .map(Customer::getName).sorted().toList();
        assertEquals(big, q.toList().stream().map(Customer::getName).sorted().toList());
        assertEquals(customers.stream().filter(c -> qtyByCustomer.getOrDefault(c.getId(), 0L) > 30).map(Customer::getName).sorted().toList(),
                total.toList().stream().map(Customer::getName).sorted().toList());
        // a left join with an association path in its ON condition
        SelectQuery<Order> in = qb.from(Order.class).where((o, sub) -> sub.from(Item.class)
                .leftJoin(Customer.class, (i, c) -> i.getOrder().getCustomer().getId() == c.getId())
                .where((i, c) -> c.getGrade() != null && c.getGrade() > 1)
                .select((i, c) -> i.getOrder().getId()).contains(o.getId()));
        assertEquals("select e0 from Order e0 where e0.id in (select s1.order.id from Item s1 left join s1.order s1j1"
                + " left join Customer s2 on s1j1.customer.id = s2.id where s2.grade is not null and s2.grade > 1)", in.toHql().hql());
        assertEquals(3, in.toList().size());
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @EntityScan("org.lambdaql.query")
    static class Context {
    }
}
