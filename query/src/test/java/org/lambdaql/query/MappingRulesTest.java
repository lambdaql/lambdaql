package org.lambdaql.query;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Queries that use the mapping annotations (ROADMAP D48): a required association ({@code optional = false}) and a
 * {@code @Lob} column. The region markers are used by Javadoc snippet tags.
 */
@SpringBootTest(classes = MappingRulesTest.Context.class)
@ActiveProfiles("test")
class MappingRulesTest {

    @Autowired
    private EntityManagerFactory emf;

    private QueryBuilder qb;
    private List<Double> prices;

    @BeforeEach
    void seed() {
        qb = new QueryBuilder(emf);
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        for (String e : List.of("OrderLine", "Item", "Order", "Customer")) em.createQuery("delete from " + e).executeUpdate();
        for (double price : new double[]{30, 10, 20}) {
            Order o = new Order();
            o.setPrice(price);
            o.setActive(true);
            em.persist(o);
            em.persist(new OrderLine(o, "memo " + price));
        }
        em.getTransaction().commit();
        em.close();
        prices = List.of(10.0, 20.0, 30.0);
    }

    @Test
    void requiredAssociation() {
        // @start region="required-association"
        // order is optional = false: a sort or select path through it cannot lose rows, so it is an inner join
        OrderedSelectQuery<OrderLine> sorted = qb.from(OrderLine.class).orderBy((l, by) -> by.asc(l.getOrder().getPrice()));
        assertEquals("select e0 from OrderLine e0 join e0.order j1 order by j1.price asc", sorted.toHql().hql());
        ValueQuery<Double> values = qb.from(OrderLine.class).select(l -> l.getOrder().getPrice());
        assertEquals("select j1.price from OrderLine e0 join e0.order j1", values.toHql().hql());
        // an association that can be missing keeps the left join (Order.customer)
        assertEquals("select e0 from Order e0 left join e0.customer j1 order by j1.name asc nulls first",
                qb.from(Order.class).orderBy((o, by) -> by.asc(o.getCustomer().getName())).toHql().hql());
        // @end
        assertEquals(3, sorted.toList().size());
        assertEquals(prices, qb.from(OrderLine.class).orderBy((l, by) -> by.asc(l.getOrder().getPrice()))
                .select(l -> l.getOrder().getPrice()).toList());
        assertEquals(prices, values.toList().stream().sorted(Comparator.naturalOrder()).toList());
    }

    @Test
    void lobColumn() {
        ch.qos.logback.classic.Logger log = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(LobCheck.class);
        Level old = log.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        log.addAppender(appender);
        log.setLevel(Level.DEBUG);
        try {
            // @start region="lob"
            // memo is @Lob: H2 and MariaDB sort and group it, Oracle rejects it (ORA-22848, ORA-22849).
            // lambdaQL keeps the query and logs LQL-5010 at DEBUG
            qb.from(OrderLine.class).orderBy((l, by) -> by.asc(l.getMemo())).toHql();
            qb.from(OrderLine.class).groupBy(l -> l.getMemo()).select(l -> l.getMemo()).toHql();
            qb.from(OrderLine.class).select(l -> l.getMemo()).distinct().toHql();
            // @end
            List<String> found = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .filter(m -> m.startsWith("[LQL-5010]")).toList();
            assertEquals(3, found.size(), String.valueOf(appender.list));
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
