package org.lambdaql.query;

import static org.lambdaql.query.By.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    /**
     * Loads entities inside a transaction. OrderLine has a @Lob column; on PostgreSQL Hibernate maps it to a large
     * object (oid), which can be read only inside a transaction ("Unable to access lob stream" otherwise).
     */
    private <T> T inTransaction(java.util.function.Function<QueryBuilder, T> work) {
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        try {
            return work.apply(new QueryBuilder(em));
        } finally {
            em.getTransaction().rollback();
            em.close();
        }
    }

    @Test
    void requiredAssociation() {
        // @start region="required-association"
        // order is optional = false: a sort or select path through it cannot lose rows, so it is an inner join
        OrderedSelectQuery<OrderLine> sorted = qb.from(OrderLine.class).orderBy(l -> asc(l.getOrder().getPrice()));
        assertEquals("select e0 from OrderLine e0 join e0.order j1 order by j1.price asc", sorted.toHql().hql());
        ValueQuery<Double> values = qb.from(OrderLine.class).select(l -> l.getOrder().getPrice());
        assertEquals("select j1.price from OrderLine e0 join e0.order j1", values.toHql().hql());
        // an association that can be missing keeps the left join (Order.customer)
        assertEquals("select e0 from Order e0 left join e0.customer j1 order by j1.name asc nulls first",
                qb.from(Order.class).orderBy(o -> asc(o.getCustomer().getName())).toHql().hql());
        // @end
        assertEquals(3, inTransaction(q -> q.from(OrderLine.class).orderBy(l -> asc(l.getOrder().getPrice())).toList()).size());
        assertEquals(prices, qb.from(OrderLine.class).orderBy(l -> asc(l.getOrder().getPrice()))
                .select(l -> l.getOrder().getPrice()).toList());
        assertEquals(prices, values.toList().stream().sorted(Comparator.naturalOrder()).toList());
    }

    @Test
    void uniqueSortKey() {
        // @start region="unique-key"
        // code is @Column(unique = true, nullable = false): it orders every row, so paging adds no id tie-breaker
        assertEquals("select e0 from OrderLine e0 order by e0.code asc",
                qb.from(OrderLine.class).orderBy(l -> asc(l.getCode())).limit(2).toHql().hql());
        // a key that is not unique still gets the id tie-breaker (S9)
        assertEquals("select e0 from OrderLine e0 join e0.order j1 order by j1.price asc, e0.id asc",
                qb.from(OrderLine.class).orderBy(l -> asc(l.getOrder().getPrice())).limit(2).toHql().hql());
        // @end
        assertEquals(List.of("memo-10.0", "memo-20.0"), inTransaction(q -> q.from(OrderLine.class).orderBy(l -> asc(l.getCode())).limit(2)
                .toList().stream().map(OrderLine::getCode).toList()));
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
            qb.from(OrderLine.class).orderBy(l -> asc(l.getMemo())).toHql();
            qb.from(OrderLine.class).groupBy(l -> l.getMemo()).select(l -> l.getMemo()).toHql();
            qb.from(OrderLine.class).select(l -> l.getMemo()).distinct().toHql();
            // @end
            List<String> found = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .filter(m -> m.startsWith("[LQL-5010]")).toList();
            assertEquals(3, found.size(), String.valueOf(appender.list));
            // @start region="enum-string-sort"
            // kind is @Enumerated(EnumType.STRING): the database sorts by name, Java by declaration order (LQL-5011, DEBUG)
            assertEquals("select e0 from Order e0 order by e0.kind asc nulls first",
                    qb.from(Order.class).orderBy(o -> asc(o.getKind())).toHql().hql());
            // @end
            assertEquals(1, appender.list.stream().filter(e -> e.getFormattedMessage().startsWith("[LQL-5011]")).count(),
                    String.valueOf(appender.list));
        } finally {
            log.setLevel(old);
            log.detachAppender(appender);
        }
    }

    @Test
    void embeddedCompare() {
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        em.createQuery("update Order o set o.address.city = 'Seoul', o.address.zip = '04524' where o.price = 10").executeUpdate();
        em.createQuery("update Order o set o.address.city = 'Seoul' where o.price = 20").executeUpdate();
        em.getTransaction().commit();
        em.close();
        // @start region="embedded-compare"
        // address is @Embedded (city, zip): comparing the whole value object compares each column
        Address addr = new Address("Seoul", "04524");
        SelectQuery<Order> same = qb.from(Order.class).where(o -> o.getAddress().equals(addr));
        assertEquals("select e0 from Order e0 where e0.address.city = :p1 and e0.address.zip = :p2", same.toHql().hql());
        assertEquals(Map.of("p1", "Seoul", "p2", "04524"), same.toHql().parameters());
        // Objects.equals and addr.equals(...) are the same comparison
        assertEquals(same.toHql().hql(), qb.from(Order.class).where(o -> Objects.equals(o.getAddress(), addr)).toHql().hql());
        // null checks each column: "no address" means every column is NULL
        SelectQuery<Order> none = qb.from(Order.class).where(o -> o.getAddress() == null);
        assertEquals("select e0 from Order e0 where e0.address.city is null and e0.address.zip is null", none.toHql().hql());
        SelectQuery<Order> some = qb.from(Order.class).where(o -> o.getAddress() != null);
        assertEquals("select e0 from Order e0 where e0.address.city is not null or e0.address.zip is not null",
                some.toHql().hql());
        // each column follows the SQL meaning: a NULL column matches neither = nor <>
        SelectQuery<Order> other = qb.from(Order.class).where(o -> !o.getAddress().equals(addr));
        assertEquals("select e0 from Order e0 where e0.address.city <> :p1 or e0.address.zip <> :p2", other.toHql().hql());
        // @end
        assertEquals(same.toHql().hql(), qb.from(Order.class).where(o -> addr.equals(o.getAddress())).toHql().hql());
        assertEquals(List.of(10.0), same.select(o -> o.getPrice()).toList());
        assertEquals(List.of(30.0), none.select(o -> o.getPrice()).toList());
        assertEquals(List.of(10.0, 20.0), some.select(o -> o.getPrice()).toList().stream().sorted().toList());
        // (Seoul, NULL): city <> 'Seoul' is false and zip <> '04524' is unknown, so no row; (NULL, NULL) neither
        assertEquals(List.of(), other.select(o -> o.getPrice()).toList());
        // a null value object binds NULL for each column: no row, and LQL-4045 is logged at WARN when it runs
        Address nothing = null;
        assertEquals(List.of(), qb.from(Order.class).where(o -> o.getAddress().equals(nothing)).select(o -> o.getPrice()).toList());
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = "org.lambdaql.query")
    static class Context {
    }
}
