package org.lambdaql.query;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lambdaql.LambdaQLException;
import org.lambdaql.UnsupportedLambdaShapeException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The examples of the Java manual, chapter 3 (conditions).
 * Each test checks the HQL inside the region and the rows after it.
 */
@SpringBootTest(classes = ManualJavaWhereTest.Context.class)
@ActiveProfiles("test")
class ManualJavaWhereTest {

    @Autowired
    private EntityManagerFactory emf;

    private QueryBuilder qb;
    private List<Order> all;

    /** A field read by a lambda: the lambda captures {@code this}. */
    private int defaultStatus = 1;

    /** A search filter, captured by a lambda. */
    record Filter(double minPrice) {
    }

    /** A form with text, captured by a lambda. */
    record Form(String text) {
    }

    @BeforeEach
    void seed() {
        qb = new QueryBuilder(emf);
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        em.createQuery("delete from Item").executeUpdate();
        em.createQuery("delete from Order").executeUpdate();
        em.createQuery("delete from Customer").executeUpdate();
        Object[][] rows = {
                // price, status, active, description, cnt, updateAt (month of 2026; 0 = 2025-12)
                {50.0, 1, true, "a", 1, 3},
                {150.0, 1, true, "b", 3, 0},
                {250.0, 2, false, null, null, 5},
                {120.0, 1, false, "a", 2, 6},
                {80.0, 3, true, "x", null, 2},
        };
        for (Object[] r : rows) {
            Order o = new Order();
            o.setPrice((Double) r[0]);
            o.setStatus(((Integer) r[1]).shortValue());
            o.setActive((Boolean) r[2]);
            o.setDescription((String) r[3]);
            o.setCnt((Integer) r[4]);
            int month = (Integer) r[5];
            o.setUpdateAt(month == 0 ? LocalDateTime.of(2025, 12, 1, 0, 0) : LocalDateTime.of(2026, month, 1, 0, 0));
            em.persist(o);
        }
        Order first = em.createQuery("select o from Order o where o.price = 50.0", Order.class).getSingleResult();
        for (double[] it : new double[][]{{10.0, 2}, {3.0, 2}}) {     // price, qty
            Item item = new Item();
            item.setOrder(first);
            item.setPrice(it[0]);
            item.setQty((int) it[1]);
            em.persist(item);
        }
        em.getTransaction().commit();
        all = em.createQuery("select o from Order o", Order.class).getResultList();
        em.close();
    }

    private static List<Long> ids(List<Order> orders) {
        return orders.stream().map(Order::getId).sorted().toList();
    }

    private List<Long> javaIds(Predicate<Order> p) {
        return all.stream().filter(p).map(Order::getId).sorted().toList();
    }

    @Test
    void andOrNot() {
        // @start region="and-or-not"
        double minPrice = 100;
        SelectQuery<Order> both = qb.from(Order.class)
                .where(o -> (o.getStatus() == 1 || o.isActive()) && o.getPrice() > minPrice);
        assertEquals("select e0 from Order e0 where (e0.status = 1 or e0.active = true) and e0.price > :p1",
                both.toHql().hql());
        SelectQuery<Order> not = qb.from(Order.class)
                .where(o -> !(o.getStatus() == 1 && o.getPrice() > minPrice));
        // the NOT is pushed into the comparisons: the same rows (status and price are never NULL)
        assertEquals("select e0 from Order e0 where e0.status <> 1 or e0.price <= :p1", not.toHql().hql());
        // @end
        assertEquals(javaIds(o -> (o.getStatus() == 1 || o.isActive()) && o.getPrice() > 100), ids(both.toList()));
        assertEquals(javaIds(o -> !(o.getStatus() == 1 && o.getPrice() > 100)), ids(not.toList()));
    }

    @Test
    void ternaryOutside() {
        boolean flag = true;
        // @start region="ternary-outside"
        // a ternary of captured values and constants: computed in Java, bound as :p1
        SelectQuery<Order> query = qb.from(Order.class).where(o -> o.getStatus() == (flag ? 1 : 2));
        assertEquals("select e0 from Order e0 where e0.status = :p1", query.toHql().hql());
        // a ternary that reads the entity becomes case when
        SelectQuery<Order> byEntity = qb.from(Order.class).where(o -> o.getStatus() == (o.isActive() ? 1 : 2));
        assertEquals("select e0 from Order e0 where e0.status = case when e0.active = true then 1 else 2 end",
                byEntity.toHql().hql());
        // @end
        assertEquals(javaIds(o -> o.getStatus() == 1), ids(query.toList()));
        assertEquals(javaIds(o -> o.getStatus() == (o.isActive() ? 1 : 2)), ids(byEntity.toList()));
    }

    @Test
    void boxedEquals() {
        // @start region="boxed-equals"
        // cnt is an Integer column; Integer == Integer compares values, not objects
        Integer wanted = 3;
        SelectQuery<Order> three = qb.from(Order.class).where(o -> o.getCnt() == wanted);
        assertEquals("select e0 from Order e0 where e0.cnt = :p1", three.toHql().hql());
        // a captured null is bound as NULL: "= NULL" matches no row (WARN LQL-4045)
        Integer none = null;
        SelectQuery<Order> noRow = qb.from(Order.class).where(o -> o.getCnt() == none);
        assertEquals("select e0 from Order e0 where e0.cnt = :p1", noRow.toHql().hql());
        // to find NULL, write the null check
        SelectQuery<Order> nullCnt = qb.from(Order.class).where(o -> o.getCnt() == null);
        assertEquals("select e0 from Order e0 where e0.cnt is null", nullCnt.toHql().hql());
        // @end
        assertEquals(javaIds(o -> Objects.equals(o.getCnt(), 3)), ids(three.toList()));
        assertTrue(noRow.toList().isEmpty());
        assertEquals(javaIds(o -> o.getCnt() == null), ids(nullCnt.toList()));
    }

    @Test
    void nullChecks() {
        // @start region="null-check"
        // only rows where cnt is not NULL and not 1
        SelectQuery<Order> notOne = qb.from(Order.class).where(o -> o.getCnt() != 1);
        assertEquals("select e0 from Order e0 where e0.cnt <> 1", notOne.toHql().hql());
        // rows where cnt is NULL too
        SelectQuery<Order> notOneOrNull = qb.from(Order.class).where(o -> o.getCnt() == null || o.getCnt() != 1);
        assertEquals("select e0 from Order e0 where e0.cnt is null or e0.cnt <> 1", notOneOrNull.toHql().hql());
        // @end
        assertEquals(javaIds(o -> o.getCnt() != null && o.getCnt() != 1), ids(notOne.toList()));
        assertEquals(javaIds(o -> o.getCnt() == null || o.getCnt() != 1), ids(notOneOrNull.toList()));
    }

    @Test
    void equalsForms() {
        // @start region="equals-forms"
        String text = "a";
        assertEquals("select e0 from Order e0 where e0.description = :p1",
                qb.from(Order.class).where(o -> o.getDescription().equals(text)).toHql().hql());
        // the same comparison; when text is null, no row matches
        assertEquals("select e0 from Order e0 where e0.description = :p1",
                qb.from(Order.class).where(o -> Objects.equals(o.getDescription(), text)).toHql().hql());
        // rows where cnt is NULL are not returned (the SQL meaning)
        SelectQuery<Order> notOne = qb.from(Order.class).where(o -> !Objects.equals(o.getCnt(), 1));
        assertEquals("select e0 from Order e0 where e0.cnt <> 1", notOne.toHql().hql());
        // @end
        assertEquals(javaIds(o -> o.getCnt() != null && o.getCnt() != 1), ids(notOne.toList()));
        assertEquals(javaIds(o -> "a".equals(o.getDescription())),
                ids(qb.from(Order.class).where(o -> o.getDescription().equals(text)).toList()));
    }

    @Test
    void booleanCompare() {
        // @start region="boolean-compare"
        int s = 1;
        // a boolean column compared with a condition: the condition becomes case when
        SelectQuery<Order> compared = qb.from(Order.class).where(o -> o.isActive() == (o.getStatus() > s));
        assertEquals("select e0 from Order e0 where e0.active = case when e0.status > :p1 then true else false end",
                compared.toHql().hql());
        // the same rows with && and ||: plain comparisons that an index can serve
        SelectQuery<Order> query = qb.from(Order.class)
                .where(o -> (o.isActive() && o.getStatus() > s) || (!o.isActive() && o.getStatus() <= s));
        assertEquals("select e0 from Order e0 where (e0.active = true and e0.status > :p1)"
                + " or (e0.active = false and e0.status <= :p2)", query.toHql().hql());
        // @end
        assertEquals(javaIds(o -> o.isActive() == (o.getStatus() > 1)), ids(query.toList()));
        assertEquals(javaIds(o -> o.isActive() == (o.getStatus() > 1)), ids(compared.toList()));
    }

    @Test
    void capturedForms() {
        // @start region="captured-forms"
        LocalDateTime since = LocalDateTime.of(2026, 1, 1, 0, 0);
        Filter filter = new Filter(60.0);
        Form form = new Form(" a ");
        SelectQuery<Order> query = qb.from(Order.class)
                .where(o -> o.getUpdateAt().compareTo(since) > 0)            // a local variable
                .where(o -> o.getStatus() == this.defaultStatus)             // a field of this
                .where(o -> o.getPrice() > filter.minPrice())                // a method of a captured object
                .where(o -> o.getDescription().equals(form.text().trim()));  // computed in Java
        assertEquals("select e0 from Order e0 where e0.updateAt > :p1 and e0.status = :p2"
                + " and e0.price > :p3 and e0.description = :p4", query.toHql().hql());
        // @end
        assertEquals(javaIds(o -> o.getUpdateAt().isAfter(since) && o.getStatus() == 1 && o.getPrice() > 60
                && "a".equals(o.getDescription())), ids(query.toList()));
    }

    @Test
    void guardWithDivision() {
        // @start region="guard"
        // the guard is written as it is; SQL does not promise to check "qty <> 0" before the division
        assertEquals("select e0 from Item e0 where e0.qty <> 0 and (e0.price / e0.qty) > 2.0",
                qb.from(Item.class).where(i -> i.getQty() != 0 && i.getPrice() / i.getQty() > 2).toHql().hql());
        // compare without the division
        SelectQuery<Item> query = qb.from(Item.class).where(i -> i.getQty() > 0 && i.getPrice() > 2 * i.getQty());
        assertEquals("select e0 from Item e0 where e0.qty > 0 and e0.price > (2.0 * e0.qty)", query.toHql().hql());
        // @end
        assertEquals(List.of(10.0), query.toList().stream().map(Item::getPrice).toList());
    }

    @Test
    void rejectedForms() {
        Customer c = new Customer(1, "Kim", null);
        // @start region="rejected-forms"
        // a String method that has no SQL function of the same meaning
        assertEquals("LQL-3008", assertThrows(LambdaQLException.class,
                () -> qb.from(Order.class).where(o -> o.getDescription().strip().equals("a"))).code());
        // an entity compared with an entity: compare the ids
        assertEquals("LQL-3011", assertThrows(LambdaQLException.class,
                () -> qb.from(Order.class).where(o -> o.getCustomer() == c)).code());
        assertEquals("select e0 from Order e0 where e0.customer.id = :p1",
                qb.from(Order.class).where(o -> o.getCustomer().getId() == c.getId()).toHql().hql());
        // operators without short-circuit
        assertEquals("LQL-2013", assertThrows(LambdaQLException.class,
                () -> qb.from(Order.class).where(o -> o.isActive() & o.getStatus() == 1)).code());
        // @end
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = "org.lambdaql.query")
    static class Context {
    }
}
