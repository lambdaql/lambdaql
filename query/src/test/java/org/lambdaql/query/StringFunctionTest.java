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
import java.util.Objects;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lambdaql.query.By.*;

/**
 * String methods on an entity value become standard HQL functions (ROADMAP D74): lower, upper, trim, length,
 * substring, locate, replace and concat; isEmpty, isBlank and equalsIgnoreCase become conditions.
 * Each test checks the HQL and compares the rows with the same test run in Java. Java throws a
 * NullPointerException on a null text where SQL gives NULL, so the Java side skips null texts.
 */
@SpringBootTest(classes = StringFunctionTest.Context.class)
@ActiveProfiles("test")
class StringFunctionTest {

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
        String[] texts = {" Kim ", "kim", "PARK", "", "   ", null, "lee-01"};
        for (int i = 0; i < texts.length; i++) {
            Order o = new Order();
            o.setDescription(texts[i]);
            o.setStatus((short) i);
            o.setPrice(10.0 * (i + 1));
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

    /** The ids of the orders with a text that pass the test in Java. */
    private List<Long> javaIds(Predicate<String> test) {
        return all().stream().filter(o -> o.getDescription() != null && test.test(o.getDescription()))
                .map(Order::getId).sorted(Comparator.naturalOrder()).toList();
    }

    private static List<Long> ids(List<Order> list) {
        return list.stream().map(Order::getId).sorted(Comparator.naturalOrder()).toList();
    }

    @Test
    void lowerAndUpper() {
        // @start region="lower"
        SelectQuery<Order> q = qb.from(Order.class).where(o -> o.getDescription().toLowerCase().equals("park"));
        assertEquals("select e0 from Order e0 where lower(e0.description) = 'park'", q.toHql().hql());
        // @end
        assertEquals(javaIds(d -> d.toLowerCase().equals("park")), ids(q.toList()));
        SelectQuery<Order> upper = qb.from(Order.class).where(o -> o.getDescription().toUpperCase().equals("KIM"));
        assertEquals("select e0 from Order e0 where upper(e0.description) = 'KIM'", upper.toHql().hql());
        assertEquals(javaIds(d -> d.toUpperCase().equals("KIM")), ids(upper.toList()));
    }

    @Test
    void trimAndLength() {
        // @start region="trim-length"
        SelectQuery<Order> trimmed = qb.from(Order.class).where(o -> o.getDescription().trim().equals("Kim"));
        assertEquals("select e0 from Order e0 where trim(e0.description) = 'Kim'", trimmed.toHql().hql());

        SelectQuery<Order> longer = qb.from(Order.class).where(o -> o.getDescription().length() > 3);
        assertEquals("select e0 from Order e0 where length(e0.description) > 3", longer.toHql().hql());
        // @end
        assertEquals(javaIds(d -> d.trim().equals("Kim")), ids(trimmed.toList()));
        assertEquals(javaIds(d -> d.length() > 3), ids(longer.toList()));
    }

    @Test
    void emptyAndBlank() {
        // @start region="empty-blank"
        SelectQuery<Order> empty = qb.from(Order.class).where(o -> o.getDescription().isEmpty());
        assertEquals("select e0 from Order e0 where length(e0.description) = 0", empty.toHql().hql());

        SelectQuery<Order> blank = qb.from(Order.class).where(o -> o.getDescription().isBlank());
        assertEquals("select e0 from Order e0 where length(trim(e0.description)) = 0", blank.toHql().hql());
        // @end
        assertEquals(javaIds(String::isEmpty), ids(empty.toList()));
        assertEquals(javaIds(String::isBlank), ids(blank.toList()));
        SelectQuery<Order> notBlank = qb.from(Order.class).where(o -> !o.getDescription().isBlank());
        assertEquals("select e0 from Order e0 where length(trim(e0.description)) <> 0", notBlank.toHql().hql());
        assertEquals(javaIds(d -> !d.isBlank()), ids(notBlank.toList()));
    }

    @Test
    void equalsIgnoreCase() {
        // @start region="ignore-case"
        String name = "KIM";
        SelectQuery<Order> q = qb.from(Order.class).where(o -> o.getDescription().equalsIgnoreCase(name));
        assertEquals("select e0 from Order e0 where lower(e0.description) = lower(:p1)", q.toHql().hql());
        // @end
        assertEquals(javaIds(d -> d.equalsIgnoreCase(name)), ids(q.toList()));
    }

    @Test
    void substring() {
        // @start region="substring"
        // Java counts from 0, SQL from 1: substring(0, 3) is the first three characters
        SelectQuery<Order> q = qb.from(Order.class).where(o -> o.getDescription().substring(0, 3).equals("lee"));
        assertEquals("select e0 from Order e0 where substring(e0.description, 1, 3) = 'lee'", q.toHql().hql());
        // @end
        // Java throws for a text shorter than 3; SQL gives the shorter text
        assertEquals(javaIds(d -> d.length() >= 3 && d.substring(0, 3).equals("lee")), ids(q.toList()));
        int from = 4;
        ValueQuery<String> tail = qb.from(Order.class).where(o -> o.getStatus() == 6).select(o -> o.getDescription().substring(from));
        // from + 1 has no entity value: it is computed in Java and bound as one value
        assertEquals("select substring(e0.description, :p1) from Order e0 where e0.status = 6", tail.toHql().hql());
        assertEquals(List.of("01"), tail.toList());
    }

    @Test
    void indexOf() {
        // @start region="index-of"
        // Java gives -1 when not found and SQL locate gives 0, so 1 is taken away
        SelectQuery<Order> q = qb.from(Order.class).where(o -> o.getDescription().indexOf("-") == 3);
        assertEquals("select e0 from Order e0 where (locate('-', e0.description) - 1) = 3", q.toHql().hql());
        // @end
        assertEquals(javaIds(d -> d.indexOf("-") == 3), ids(q.toList()));
        SelectQuery<Order> missing = qb.from(Order.class).where(o -> o.getDescription().indexOf("-") < 0);
        assertEquals(javaIds(d -> d.indexOf("-") < 0), ids(missing.toList()));
    }

    @Test
    void replace() {
        // @start region="replace"
        SelectQuery<Order> q = qb.from(Order.class).where(o -> o.getDescription().replace("-", "_").equals("lee_01"));
        assertEquals("select e0 from Order e0 where replace(e0.description, '-', '_') = 'lee_01'", q.toHql().hql());
        // @end
        assertEquals(javaIds(d -> d.replace("-", "_").equals("lee_01")), ids(q.toList()));
    }

    @Test
    void concatenation() {
        // @start region="concat"
        String prefix = "A-";
        SelectQuery<Order> q = qb.from(Order.class).where(o -> (prefix + o.getDescription() + "!").equals("A-PARK!"));
        assertEquals("select e0 from Order e0 where concat(:p1, e0.description, '!') = 'A-PARK!'", q.toHql().hql());

        // a number is converted with str()
        ValueQuery<String> labels = qb.from(Order.class).where(o -> o.getStatus() == 2).select(o -> o.getDescription() + "#" + o.getStatus());
        assertEquals("select concat(e0.description, '#', str(e0.status)) from Order e0 where e0.status = 2", labels.toHql().hql());
        // @end
        assertEquals(javaIds(d -> (prefix + d + "!").equals("A-PARK!")), ids(q.toList()));
        assertEquals(List.of("PARK#2"), labels.toList());
    }

    @Test
    void concatenationOfCapturedValues() {
        // @start region="concat-captured"
        // only captured values: the text is made in Java and bound
        String code = "01";
        SelectQuery<Order> q = qb.from(Order.class).where(o -> o.getDescription().equals("lee-" + code));
        assertEquals("select e0 from Order e0 where e0.description = :p1", q.toHql().hql());
        // @end
        assertEquals(javaIds(d -> d.equals("lee-" + code)), ids(q.toList()));
    }

    @Test
    void inSelectSortAndSet() {
        // @start region="select-sort-set"
        ValueQuery<String> upper = qb.from(Order.class).where(o -> o.getStatus() == 2).select(o -> o.getDescription().toUpperCase());
        assertEquals("select upper(e0.description) from Order e0 where e0.status = 2", upper.toHql().hql());

        OrderedSelectQuery<Order> sorted = qb.from(Order.class).orderBy(o -> asc(o.getDescription().length()).asc(o.getId()));
        assertEquals("select e0 from Order e0 order by length(e0.description) asc nulls first, e0.id asc", sorted.toHql().hql());

        ExecutableUpdate<Order> trim = qb.update(Order.class).set(o -> o.setDescription(o.getDescription().trim())).all();
        assertEquals("update Order e0 set e0.description = trim(e0.description)", trim.toHql().hql());
        // @end
        assertEquals(List.of("PARK"), upper.toList());
        assertEquals(sorted.toList().size(), 7);
        trim.execute();
        assertEquals(all().stream().map(Order::getDescription).filter(Objects::nonNull).sorted().toList(),
                List.of("", "", "Kim", "PARK", "kim", "lee-01"));
    }

    @Test
    void concatenationInSet() {
        // @start region="concat-set"
        ExecutableUpdate<Order> u = qb.update(Order.class).set(o -> o.setDescription(o.getDescription() + "_x")).where(o -> o.getStatus() == 2);
        assertEquals("update Order e0 set e0.description = concat(e0.description, '_x') where e0.status = 2", u.toHql().hql());
        // @end
        u.execute();
        assertEquals(List.of("PARK_x"), all().stream().filter(o -> o.getStatus() == 2).map(Order::getDescription).toList());
    }

    @Test
    void textMatchOnAFunction() {
        // startsWith on lower(...): the match is written on the function
        SelectQuery<Order> q = qb.from(Order.class).where(o -> o.getDescription().toLowerCase().startsWith("ki"));
        assertEquals(javaIds(d -> d.toLowerCase().startsWith("ki")), ids(q.toList()));
    }

    @Test
    void functionOnAColumnWarns() {
        ch.qos.logback.classic.Logger log = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(SargableCheck.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        log.addAppender(appender);
        try {
            // lower(e0.description) = ... reads every row: an index on description is not used (WARN LQL-5001)
            qb.from(Order.class).where(o -> o.getDescription().toLowerCase().equals("park")).toHql();
            assertEquals(List.of("WARN LQL-5001"), appender.list.stream()
                    .map(e -> e.getLevel() + " " + e.getFormattedMessage().substring(1, 9)).toList(), String.valueOf(appender.list));
        } finally {
            log.detachAppender(appender);
        }
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = "org.lambdaql.query")
    static class Context {
    }
}
