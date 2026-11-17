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
            // a null captured value in a size comparison or as the equals receiver: bound as NULL, no row, WARN when it runs
            Double min = null;
            String none = null;
            assertEquals("select e0 from Order e0 where e0.price > :p1", qb.from(Order.class).where(o -> o.getPrice() > min).toHql().hql());
            assertEquals(List.of(), qb.from(Order.class).where(o -> o.getPrice() > min).toList());
            assertEquals(List.of(), qb.from(Order.class).where(o -> none.equals(o.getDescription())).toList());
            assertTrue(appender.list.stream().anyMatch(e -> e.getFormattedMessage().startsWith("[LQL-4045]")), String.valueOf(appender.list));
        } finally {
            log.setLevel(old);
            log.detachAppender(appender);
        }
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @EntityScan("org.lambdaql.query")
    static class Context {
    }
}
