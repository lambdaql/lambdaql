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

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A collection made in the lambda ({@code List.of(1, 2)}, {@code Arrays.asList("a", "b")}) is computed in Java and
 * bound like a captured collection (ROADMAP D76). Each test checks the HQL and compares the rows with Java.
 */
@SpringBootTest(classes = InListTest.Context.class)
@ActiveProfiles("test")
class InListTest {

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
        String[] texts = {"a", "b", "c", null};
        for (int i = 0; i < texts.length; i++) {
            Order o = new Order();
            o.setDescription(texts[i]);
            o.setCnt(i + 1);
            o.setStatus((short) (i + 1));
            em.persist(o);
        }
        em.getTransaction().commit();
        em.close();
    }

    private List<Long> javaIds(Predicate<Order> test) {
        EntityManager em = emf.createEntityManager();
        try {
            return em.createQuery("select o from Order o", Order.class).getResultList().stream().filter(test)
                    .map(Order::getId).sorted(Comparator.naturalOrder()).toList();
        } finally {
            em.close();
        }
    }

    private static List<Long> ids(List<Order> list) {
        return list.stream().map(Order::getId).sorted(Comparator.naturalOrder()).toList();
    }

    @Test
    void madeInTheLambda() {
        // @start region="in-made-in-lambda"
        // a collection made in the lambda is computed in Java and bound like a captured collection
        SelectQuery<Order> listOf = qb.from(Order.class).where(o -> List.of(1, 2).contains(o.getCnt()));
        assertEquals("select e0 from Order e0 where e0.cnt in (:p1)", listOf.toHql().hql());

        SelectQuery<Order> asList = qb.from(Order.class).where(o -> Arrays.asList("a", "b").contains(o.getDescription()));
        assertEquals("select e0 from Order e0 where e0.description in (:p1)", asList.toHql().hql());
        // @end
        assertEquals(javaIds(o -> List.of(1, 2).contains(o.getCnt())), ids(listOf.toList()));
        assertEquals(javaIds(o -> Arrays.asList("a", "b").contains(o.getDescription())), ids(asList.toList()));
    }

    @Test
    void manyElementsAndNot() {
        // more than ten elements: List.of(E...) takes an array
        SelectQuery<Order> many = qb.from(Order.class).where(o -> List.of(1, 3, 5, 7, 9, 11, 13, 15, 17, 19, 21).contains(o.getCnt()));
        assertEquals(List.of(1, 3, 5, 7, 9, 11, 13, 15, 17, 19, 21), many.toHql().parameters().get("p1"));
        assertEquals(javaIds(o -> o.getCnt() % 2 == 1), ids(many.toList()));
        // NOT IN, with the SQL meaning: the row with a NULL description is not returned
        SelectQuery<Order> not = qb.from(Order.class).where(o -> !Set.of("a", "b").contains(o.getDescription()));
        assertEquals("select e0 from Order e0 where e0.description not in (:p1)", not.toHql().hql());
        assertEquals(javaIds(o -> o.getDescription() != null && !Set.of("a", "b").contains(o.getDescription())), ids(not.toList()));
    }

    @Test
    void capturedValueInside() {
        int n = 3;
        // a captured value in the list: computed again on every run
        SelectQuery<Order> q = qb.from(Order.class).where(o -> Arrays.asList(1, n).contains(o.getCnt()));
        assertEquals(List.of(1, 3), q.toHql().parameters().get("p1"));
        assertEquals(javaIds(o -> o.getCnt() == 1 || o.getCnt() == 3), ids(q.toList()));
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = "org.lambdaql.query")
    static class Context {
    }
}
