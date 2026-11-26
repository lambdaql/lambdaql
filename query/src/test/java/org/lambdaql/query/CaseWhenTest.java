package org.lambdaql.query;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lambdaql.query.By.*;

/**
 * Values chosen by conditions on the entity become {@code case when ... end} (ROADMAP D62): in select, in a compared
 * value, as a sort key and as an assigned value; with a compound condition and with a nested ternary.
 * Each test checks the HQL and compares the rows with the same lambda run in Java.
 */
@SpringBootTest(classes = CaseWhenTest.Context.class)
@ActiveProfiles("test")
class CaseWhenTest {

    @Autowired
    private EntityManagerFactory emf;

    private QueryBuilder qb;

    @BeforeEach
    void seed() {
        qb = new QueryBuilder(emf);
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        for (String e : List.of("Item", "Order", "Customer", "Archive", "VOrder", "IdOrder")) {
            em.createQuery("delete from " + e).executeUpdate();
        }
        for (int i = 1; i <= 6; i++) {
            Order o = new Order();
            o.setStatus((short) (i % 3));
            o.setPrice(40.0 * i);
            o.setActive(i % 2 == 0);
            em.persist(o);
        }
        em.getTransaction().commit();
        em.close();
    }

    private List<Order> all() {
        EntityManager em = emf.createEntityManager();
        try {
            return em.createQuery("select o from Order o", Order.class).getResultList();
        } finally {
            em.close();
        }
    }

    private List<Long> javaIds(Predicate<Order> p) {
        return all().stream().filter(p).map(Order::getId).sorted(Comparator.naturalOrder()).toList();
    }

    private static List<Long> ids(List<Order> list) {
        return list.stream().map(Order::getId).sorted(Comparator.naturalOrder()).toList();
    }

    @Test
    void inACondition() {
        // @start region="case-where"
        SelectQuery<Order> q = qb.from(Order.class).where(o -> o.getStatus() == (o.isActive() ? 1 : 2));
        assertEquals("select e0 from Order e0 where e0.status = case when e0.active = true then 1 else 2 end", q.toHql().hql());
        // @end
        assertEquals(javaIds(o -> o.getStatus() == (o.isActive() ? 1 : 2)), ids(q.toList()));
    }

    @Test
    void comparedCaseWarns() {
        ch.qos.logback.classic.Logger log = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(SargableCheck.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        log.addAppender(appender);
        try {
            // a compared case reads active on every row: an index on active is not used (WARN LQL-5013)
            qb.from(Order.class).where(o -> o.getStatus() == (o.isActive() ? 1 : 2)).toHql();
            assertEquals(List.of("WARN LQL-5013"), appender.list.stream()
                    .map(e -> e.getLevel() + " " + e.getFormattedMessage().substring(1, 9)).toList(), String.valueOf(appender.list));
        } finally {
            log.detachAppender(appender);
        }
    }

    @Test
    void entityValueInABranch() {
        // @start region="case-branch-value"
        SelectQuery<Order> q = qb.from(Order.class).where(o -> (o.isActive() ? o.getPrice() : 0.0) > 100);
        assertEquals("select e0 from Order e0 where case when e0.active = true then e0.price else 0.0 end > 100.0", q.toHql().hql());
        // @end
        assertEquals(javaIds(o -> (o.isActive() ? o.getPrice() : 0.0) > 100), ids(q.toList()));
    }

    @Test
    void booleanColumnEqualsACondition() {
        int s = 1;
        // @start region="case-boolean-compare"
        SelectQuery<Order> q = qb.from(Order.class).where(o -> o.isActive() == (o.getStatus() > s));
        assertEquals("select e0 from Order e0 where e0.active = case when e0.status > :p1 then true else false end", q.toHql().hql());
        // @end
        assertEquals(javaIds(o -> o.isActive() == (o.getStatus() > 1)), ids(q.toList()));
    }

    @Test
    void inSelect() {
        // @start region="case-select"
        ValueQuery<String> q = qb.from(Order.class).select(o -> o.getPrice() > 100 ? "big" : "small");
        assertEquals("select case when e0.price > 100.0 then 'big' else 'small' end from Order e0", q.toHql().hql());
        // @end
        assertEquals(all().stream().map(o -> o.getPrice() > 100 ? "big" : "small").sorted().toList(), q.toList().stream().sorted().toList());
    }

    @Test
    void asAGroupKey() {
        // @start region="case-group"
        // orders per price band: the case is the group key and the selected value
        ValueQuery<String> q = qb.from(Order.class)
                .groupBy(o -> o.getPrice() > 100 ? "big" : "small")
                .select(o -> o.getPrice() > 100 ? "big" : "small");
        assertEquals("select case when e0.price > 100.0 then 'big' else 'small' end from Order e0"
                + " group by case when e0.price > 100.0 then 'big' else 'small' end", q.toHql().hql());
        // @end
        assertEquals(all().stream().map(o -> o.getPrice() > 100 ? "big" : "small").distinct().sorted().toList(),
                q.toList().stream().sorted().toList());
    }

    @Test
    void compoundAndNested() {
        // a compound condition and a nested ternary: one case with several branches
        ValueQuery<Integer> q = qb.from(Order.class)
                .select(o -> o.isActive() && o.getPrice() > 100 ? 1 : o.getStatus() == 0 ? 2 : 3);
        assertEquals("select case when e0.active = true and e0.price > 100.0 then 1"
                + " when e0.status = 0 then 2 else 3 end from Order e0", q.toHql().hql());
        assertEquals(all().stream().map(o -> o.isActive() && o.getPrice() > 100 ? 1 : o.getStatus() == 0 ? 2 : 3).sorted().toList(),
                q.toList().stream().sorted().toList());
    }

    @Test
    void asASortKey() {
        // @start region="case-sort"
        // active orders first, then by price
        OrderedSelectQuery<Order> q = qb.from(Order.class).orderBy(o -> asc(o.isActive() ? 0 : 1).asc(o.getPrice())).limit(10);
        assertEquals("select e0 from Order e0 order by case when e0.active = true then 0 else 1 end asc, e0.price asc, e0.id asc",
                q.toHql().hql());
        // @end
        assertEquals(all().stream().sorted(Comparator.comparing((Order o) -> o.isActive() ? 0 : 1).thenComparing(Order::getPrice))
                .map(Order::getId).toList(), q.toList().stream().map(Order::getId).toList());
    }

    @Test
    void asAnAssignedValue() {
        // @start region="case-set"
        // 10 % off the orders above 100
        ExecutableUpdate<Order> u = qb.update(Order.class).set(o -> o.setPrice(o.getPrice() > 100 ? o.getPrice() * 0.9 : o.getPrice())).all();
        assertEquals("update Order e0 set e0.price = case when e0.price > 100.0 then (e0.price * 0.9) else e0.price end", u.toHql().hql());
        // @end
        List<Double> expected = all().stream().map(o -> o.getPrice() > 100 ? o.getPrice() * 0.9 : o.getPrice()).sorted().toList();
        u.execute();
        List<Double> actual = all().stream().map(Order::getPrice).sorted().toList();
        for (int i = 0; i < expected.size(); i++) assertEquals(expected.get(i), actual.get(i), 1e-9);
    }

    @Test
    void toggleInSet() {
        // @start region="case-toggle"
        // a toggle is a value chosen by a condition too
        ExecutableUpdate<Order> u = qb.update(Order.class).set(o -> o.setActive(!o.isActive())).all();
        assertEquals("update Order e0 set e0.active = case when e0.active = false then true else false end", u.toHql().hql());
        // @end
        java.util.Map<Long, Boolean> before = new java.util.HashMap<>();
        all().forEach(o -> before.put(o.getId(), o.isActive()));
        u.execute();
        all().forEach(o -> assertEquals(!before.get(o.getId()), o.isActive()));
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = "org.lambdaql.query")
    static class Context {
    }
}
