package org.lambdaql.query;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lambdaql.LambdaQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The examples of the Java manual, chapter 4 (sorting and paging).
 * Each test checks the HQL inside the region and the rows after it.
 */
@SpringBootTest(classes = ManualJavaSortTest.Context.class)
@ActiveProfiles("test")
class ManualJavaSortTest {

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
                // price, status, active, description, customer, updateAt (day of January 2026)
                {50.0, 1, true, "a", kim, 3},
                {150.0, 1, true, "b", lee, 1},
                {250.0, 2, false, null, kim, 5},
                {120.0, 1, true, "c", null, 2},
                {80.0, 1, true, null, kim, 4},
                {90.0, 1, false, "d", lee, 6},
        };
        for (Object[] r : rows) {
            Order o = new Order();
            o.setPrice((Double) r[0]);
            o.setStatus(((Integer) r[1]).shortValue());
            o.setActive((Boolean) r[2]);
            o.setDescription((String) r[3]);
            o.setCustomer((Customer) r[4]);
            o.setUpdateAt(LocalDateTime.of(2026, 1, (Integer) r[5], 0, 0));
            em.persist(o);
        }
        em.getTransaction().commit();
        all = em.createQuery("select o from Order o left join fetch o.customer", Order.class).getResultList();
        em.close();
    }

    @AfterEach
    void noPolicy() {
        SortPolicy.unregister(emf);           // other tests share this persistence unit
    }

    private static List<Long> ids(List<Order> orders) {
        return orders.stream().map(Order::getId).toList();
    }

    private static String nameOf(Order o) {
        return o.getCustomer() == null ? null : o.getCustomer().getName();
    }

    @Test
    void overview() {
        // @start region="sort-overview"
        OrderedSelectQuery<Order> page = qb.from(Order.class)
                .where(o -> o.isActive())
                .orderBy((o, by) -> by.desc(o.getPrice()).asc(o.getCustomer().getName()).asc(o.getId()))
                .limit(2).offset(2);
        assertEquals("select e0 from Order e0 left join e0.customer j1 where e0.active = true"
                + " order by e0.price desc, j1.name asc nulls first, e0.id asc", page.toHql().hql());
        List<Order> rows = page.toList();
        // @end
        List<Long> expected = all.stream().filter(Order::isActive)
                .sorted(Comparator.comparing(Order::getPrice).reversed()
                        .thenComparing(ManualJavaSortTest::nameOf, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(Order::getId))
                .skip(2).limit(2).map(Order::getId).toList();
        assertEquals(expected, ids(rows));
    }

    @Test
    void direction() {
        boolean newestFirst = true;
        // @start region="sort-direction"
        // the second argument: false = NULL last
        OrderedSelectQuery<Order> nullsLast = qb.from(Order.class)
                .orderBy((o, by) -> by.desc(o.getDescription(), false).asc(o.getId()));
        assertEquals("select e0 from Order e0 order by e0.description desc nulls last, e0.id asc",
                nullsLast.toHql().hql());
        // a direction from a variable: the ternary runs outside the lambda
        Direction dir = newestFirst ? Direction.DESC : Direction.ASC;
        OrderedSelectQuery<Order> newest = qb.from(Order.class)
                .orderBy((o, by) -> by.sort(o.getUpdateAt(), dir).asc(o.getId()));   // dir is read on every call
        assertEquals("select e0 from Order e0 order by e0.updateAt desc nulls last, e0.id asc", newest.toHql().hql());
        // @end
        assertEquals(all.stream().sorted(Comparator.comparing(Order::getUpdateAt).reversed()).map(Order::getId).toList(),
                ids(newest.toList()));
        assertEquals(all.stream().sorted(Comparator.comparing(Order::getDescription,
                        Comparator.nullsLast(Comparator.<String>reverseOrder())).thenComparing(Order::getId))
                .map(Order::getId).toList(), ids(nullsLast.toList()));
    }

    @Test
    void lambdaBody() {
        boolean flag = true;
        // @start region="sort-body"
        // allowed: a simple chain of by calls
        assertEquals("select e0 from Order e0 order by e0.price desc, e0.id asc",
                qb.from(Order.class).orderBy((o, by) -> by.desc(o.getPrice()).asc(o.getId())).toHql().hql());
        // rejected: a key chosen by a condition
        LambdaQLException choice = assertThrows(LambdaQLException.class,
                () -> qb.from(Order.class).orderBy((o, by) -> flag ? by.desc(o.getPrice()) : by.asc(o.getId())));
        assertEquals("LQL-2023", choice.code());
        // rejected: a key without the entity
        LambdaQLException constant = assertThrows(LambdaQLException.class,
                () -> qb.from(Order.class).orderBy((o, by) -> by.asc(1)));
        assertEquals("LQL-3031", constant.code());
        // @end
    }

    @Test
    void paging() {
        // @start region="page"
        OrderedSelectQuery<Order> page = qb.from(Order.class)
                .where(o -> o.getStatus() == 1)
                .orderBy((o, by) -> by.desc(o.getPrice()))
                .limit(2)       // page size
                .offset(2);     // skip 2 rows (page 2)
        assertEquals("select e0 from Order e0 where e0.status = 1 order by e0.price desc, e0.id asc",
                page.toHql().hql());
        List<Order> rows = page.toList();
        // @end
        assertEquals(all.stream().filter(o -> o.getStatus() == 1)
                .sorted(Comparator.comparing(Order::getPrice).reversed().thenComparing(Order::getId))
                .skip(2).limit(2).map(Order::getId).toList(), ids(rows));
    }

    @Test
    void arithmeticKeys() {
        // @start region="sort-arith"
        // arithmetic on columns
        OrderedSelectQuery<Order> doubled = qb.from(Order.class)
                .orderBy((o, by) -> by.desc(o.getPrice() * 2).asc(o.getId()));
        assertEquals("select e0 from Order e0 order by (e0.price * 2.0) desc, e0.id asc", doubled.toHql().hql());
        // a widening conversion (short to long)
        OrderedSelectQuery<Order> widened = qb.from(Order.class)
                .orderBy((o, by) -> by.asc((long) o.getStatus()).asc(o.getId()));
        assertEquals("select e0 from Order e0 order by e0.status asc, e0.id asc", widened.toHql().hql());
        // @end
        assertEquals(all.stream().sorted(Comparator.comparing(Order::getPrice).reversed()).map(Order::getId).toList(),
                ids(doubled.toList()));
        assertEquals(all.stream().sorted(Comparator.comparing(Order::getStatus).thenComparing(Order::getId))
                .map(Order::getId).toList(), ids(widened.toList()));
    }

    @Test
    void ruleMustBeColumn() {
        // @start region="rule-not-column"
        // a rule that is not a column of the entity is rejected when the rules are first used
        SortPolicy.register(emf, SortPolicy.of(SortMode.STRICT,
                SortRules.of(Order.class).allow(o -> o.getPrice() * 2)));
        LambdaQLException e = assertThrows(LambdaQLException.class,
                () -> qb.from(Order.class).orderBy(SortSpec.ofPath(Order.class, "price", Direction.ASC)).toHql());
        assertEquals("LQL-4041", e.code());
        // @end
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = "org.lambdaql.query")
    static class Context {
    }
}
