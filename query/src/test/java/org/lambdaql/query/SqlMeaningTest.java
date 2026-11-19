package org.lambdaql.query;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lambdaql.engine.render.HqlRenderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import org.lambdaql.engine.render.RenderedQuery;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Entity values are compared with the SQL meaning (ROADMAP D48): a row whose value is NULL does not match = or <>,
 * and lambdaQL tells it at DEBUG (LQL-5009). The region markers are used by Javadoc snippet tags.
 */
@SpringBootTest(classes = SqlMeaningTest.Context.class)
@ActiveProfiles("test")
class SqlMeaningTest {

    @Autowired
    private EntityManagerFactory emf;

    private QueryBuilder qb;

    @BeforeEach
    void seed() {
        qb = new QueryBuilder(emf);
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        for (String e : List.of("Item", "Order", "Customer", "Coded")) em.createQuery("delete from " + e).executeUpdate();
        String[][] rows = {{"a", "a"}, {"a", "b"}, {null, "a"}, {null, null}, {"b", null}};
        for (String[] r : rows) {
            Order o = new Order();
            o.setDescription(r[0]);
            o.setCustName(r[1]);
            o.setActive(true);
            em.persist(o);
        }
        em.persist(new Coded("t", true));
        em.persist(new Coded("f", false));
        em.persist(new Coded("n", null));
        em.getTransaction().commit();
        em.close();
    }

    private static List<String> pairs(List<Order> rows) {
        return rows.stream().map(o -> o.getDescription() + "/" + o.getCustName()).sorted().toList();
    }

    @Test
    void sqlMeaning() {
        ch.qos.logback.classic.Logger log = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(HqlRenderer.class);
        Level old = log.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        log.addAppender(appender);
        log.setLevel(Level.DEBUG);
        try {
            String text = "a";
            // @start region="sql-meaning"
            // two entity values: = and <> as in SQL; a row with a NULL on either side matches neither
            SelectQuery<Order> same = qb.from(Order.class).where(o -> Objects.equals(o.getDescription(), o.getCustName()));
            assertEquals("select e0 from Order e0 where e0.description = e0.custName", same.toHql().hql());
            assertEquals(List.of("a/a"), pairs(same.toList()));                    // null/null is not "equal" in SQL
            SelectQuery<Order> different = qb.from(Order.class).where(o -> !Objects.equals(o.getDescription(), o.getCustName()));
            assertEquals(List.of("a/b"), pairs(different.toList()));               // rows with a NULL are left out
            // an entity value and a value: a NULL column does not match <>
            SelectQuery<Order> notA = qb.from(Order.class).where(o -> !text.equals(o.getDescription()));
            assertEquals("select e0 from Order e0 where e0.description <> :p1", notA.toHql().hql());
            assertEquals(List.of("b/null"), pairs(notA.toList()));
            // to keep the NULL rows, say so in the lambda
            SelectQuery<Order> notAOrNull = qb.from(Order.class).where(o -> o.getDescription() == null || !text.equals(o.getDescription()));
            assertEquals(List.of("b/null", "null/a", "null/null"), pairs(notAOrNull.toList()));
            // a NULL Boolean is not false
            assertEquals(List.of("f"), qb.from(Coded.class).where(c -> !c.getVip()).toList().stream().map(Coded::getCode).toList());
            // @end
            assertTrue(appender.list.stream().anyMatch(e -> e.getFormattedMessage().startsWith("[LQL-5009]")), String.valueOf(appender.list));
            // javac's automatic unboxing (min.doubleValue(), same bytecode as writing it): treated as automatic, so a null
            // min is bound as NULL, no row, WARN when it runs
            Double min = null;
            String none = null;
            assertEquals("select e0 from Order e0 where e0.price > :p1", qb.from(Order.class).where(o -> o.getPrice() > min).toHql().hql());
            assertEquals(List.of(), qb.from(Order.class).where(o -> o.getPrice() > min).toList());
            // the null came through automatic unboxing: LQL-4045 at DEBUG, not WARN
            assertTrue(appender.list.stream().anyMatch(e -> e.getLevel() == Level.DEBUG && e.getFormattedMessage().startsWith("[LQL-4045]")
                    && e.getFormattedMessage().contains("> :p")), String.valueOf(appender.list));
            // an unboxing to another primitive (Double.intValue()) is written by hand: it runs in Java and throws (LQL-4012)
            assertEquals("LQL-4012", org.junit.jupiter.api.Assertions.assertThrows(org.lambdaql.LambdaQLException.class,
                    () -> qb.from(Order.class).where(o -> o.getPrice() > min.intValue()).toHql()).code());
            // p.equals(column) is a comparison like column.equals(p): a null p is bound as NULL, no row, WARN (no exception)
            assertEquals("select e0 from Order e0 where e0.description = :p1",
                    qb.from(Order.class).where(o -> none.equals(o.getDescription())).toHql().hql());
            assertEquals(List.of(), qb.from(Order.class).where(o -> none.equals(o.getDescription())).toList());
            // a comparison with a null value (no Java method on it): bound as NULL, no row, WARN when it runs
            String d = null;
            assertEquals(List.of(), qb.from(Order.class).where(o -> Objects.equals(o.getDescription(), d)).toList());
            // a plain comparison with a null captured value: LQL-4045 at WARN
            assertTrue(appender.list.stream().anyMatch(e -> e.getLevel() == Level.WARN && e.getFormattedMessage().startsWith("[LQL-4045]")),
                    String.valueOf(appender.list));
        } finally {
            log.setLevel(old);
            log.detachAppender(appender);
        }
    }

    @Test
    void wholeNumberColumn() {
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        em.createQuery("update Order o set o.cnt = 2 where o.description = 'a'").executeUpdate();
        em.createQuery("update Order o set o.cnt = 3 where o.description = 'b'").executeUpdate();
        em.getTransaction().commit();
        em.close();
        // @start region="whole-number"
        // cnt is a whole number; 2.5 has no whole-number form, so the comparison keeps its meaning with one
        // (binding 2.5 as 2 would change the rows)
        double half = 2.5;
        RenderedQuery atLeast = qb.from(Order.class).where(o -> o.getCnt() >= half).toHql();
        assertEquals("select e0 from Order e0 where e0.cnt >= :p1", atLeast.hql());
        assertEquals(Map.of("p1", 3), atLeast.parameters());                         // cnt >= 2.5 is cnt >= 3
        RenderedQuery more = qb.from(Order.class).where(o -> o.getCnt() > half).toHql();
        assertEquals(Map.of("p1", 2), more.parameters());                            // cnt > 2.5 is cnt > 2
        assertEquals("select e0 from Order e0 where 1=0", qb.from(Order.class).where(o -> o.getCnt() == half).toHql().hql());
        // an IN list drops what can never equal a whole number
        List<Double> values = List.of(2.5, 3.0);
        RenderedQuery in = qb.from(Order.class).where(o -> values.contains(o.getCnt().doubleValue())).toHql();
        assertEquals("select e0 from Order e0 where e0.cnt in (:p1)", in.hql());
        assertEquals(Map.of("p1", List.of(3)), in.parameters());
        // @end
        assertEquals(List.of(3), qb.from(Order.class).where(o -> o.getCnt() >= half).select(o -> o.getCnt()).toList());
        assertEquals(List.of(3), qb.from(Order.class).where(o -> o.getCnt() > half).select(o -> o.getCnt()).toList());
        assertEquals(List.of(), qb.from(Order.class).where(o -> o.getCnt() == half).toList());
        // a constant written in the lambda gets the same rewrite
        RenderedQuery literal = qb.from(Order.class).where(o -> o.getCnt() >= 2.5).toHql();
        assertEquals("select e0 from Order e0 where e0.cnt >= :p1", literal.hql());
        assertEquals(Map.of("p1", 3), literal.parameters());
        assertEquals(List.of(3), qb.from(Order.class).where(o -> o.getCnt() >= 2.5).select(o -> o.getCnt()).toList());
        assertEquals(List.of(2, 2, 3), qb.from(Order.class).where(o -> o.getCnt() < 3.5).select(o -> o.getCnt()).toList()
                .stream().sorted().toList());
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @EntityScan("org.lambdaql.query")
    static class Context {
    }
}
