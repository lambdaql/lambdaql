package org.lambdaql.query;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lambdaql.LambdaQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Text matches (ROADMAP D60): {@code startsWith}, {@code endsWith} and {@code contains} on an entity value become
 * {@code like} with an escaped pattern. Each test checks the HQL and compares the rows with the same filter in Java.
 * The texts avoid upper/lower case pairs, because MariaDB and MySQL compare text without case by default.
 */
@SpringBootTest(classes = LikeTest.Context.class)
@ActiveProfiles("test")
class LikeTest {

    private static final List<String> TEXTS = Arrays.asList("Kim's order", "50%_off sale", "50x off", "tall", "Stall", null);

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
        for (String t : TEXTS) {
            Order o = new Order();
            o.setStatus((short) 1);
            o.setPrice(10.0);
            o.setActive(true);
            o.setDescription(t);
            em.persist(o);
        }
        em.getTransaction().commit();
        em.close();
    }

    private List<Long> javaIds(Predicate<String> p) {
        EntityManager em = emf.createEntityManager();
        try {
            return em.createQuery("select o from Order o", Order.class).getResultList().stream()
                    .filter(o -> o.getDescription() != null && p.test(o.getDescription()))
                    .map(Order::getId).sorted(Comparator.naturalOrder()).toList();
        } finally {
            em.close();
        }
    }

    private static List<Long> ids(List<Order> list) {
        return list.stream().map(Order::getId).sorted(Comparator.naturalOrder()).toList();
    }

    @Test
    void startsWithEscapesWildcards() {
        String p = "50%_";
        // @start region="like"
        // % and _ in the text are not wildcards: only "50%_off sale" matches, not "50x off"
        SelectQuery<Order> q = qb.from(Order.class).where(o -> o.getDescription().startsWith(p));
        assertEquals("select e0 from Order e0 where e0.description like :p1 escape '!'", q.toHql().hql());
        assertEquals("50!%!_%", q.toHql().parameters().get("p1"));
        // a constant is written in the HQL
        SelectQuery<Order> ends = qb.from(Order.class).where(o -> o.getDescription().endsWith("all"));
        assertEquals("select e0 from Order e0 where e0.description like '%all' escape '!'", ends.toHql().hql());
        // @end
        assertEquals(javaIds(d -> d.endsWith("all")), ids(ends.toList()));
        assertEquals(javaIds(d -> d.startsWith(p)), ids(q.toList()));
    }

    @Test
    void endsWithAndContainsConstants() {
        SelectQuery<Order> ends = qb.from(Order.class).where(o -> o.getDescription().endsWith("all"));
        assertEquals("select e0 from Order e0 where e0.description like '%all' escape '!'", ends.toHql().hql());
        assertEquals(javaIds(d -> d.endsWith("all")), ids(ends.toList()));
        // a quote in a constant is doubled in the literal
        SelectQuery<Order> quote = qb.from(Order.class).where(o -> o.getDescription().contains("'s"));
        assertEquals("select e0 from Order e0 where e0.description like '%''s%' escape '!'", quote.toHql().hql());
        assertEquals(javaIds(d -> d.contains("'s")), ids(quote.toList()));
    }

    @Test
    void notLike() {
        String p = "50%_";
        // NOT: rows with a NULL description do not match either (SQL meaning)
        SelectQuery<Order> q = qb.from(Order.class).where(o -> !o.getDescription().startsWith(p));
        assertEquals("select e0 from Order e0 where e0.description not like :p1 escape '!'", q.toHql().hql());
        assertEquals(javaIds(d -> !d.startsWith(p)), ids(q.toList()));
    }

    @Test
    void capturedNullIsAnError() {
        String p = null;
        LambdaQLException e = assertThrows(LambdaQLException.class,
                () -> qb.from(Order.class).where(o -> o.getDescription().contains(p)).toHql());
        assertEquals("LQL-4047", e.code());
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = "org.lambdaql.query")
    static class Context {
    }
}
