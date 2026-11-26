package org.lambdaql.query;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lambdaql.LambdaQLException;
import org.lambdaql.engine.render.RenderedQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * End-to-end tests for {@code qb.from(X).where(...)}: the HQL text, the parameters, and the rows from H2.
 * Each row test also filters the same data in Java and compares the ids.
 * The region markers are used by Javadoc snippet tags.
 */
@SpringBootTest(classes = SelectQueryTest.Context.class)
@ActiveProfiles("test")
class SelectQueryTest {

    @Autowired
    private EntityManagerFactory emf;

    private QueryBuilder qb;
    private List<Order> all;

    @BeforeEach
    void seed() {
        qb = new QueryBuilder(emf);
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        em.createQuery("delete from Order").executeUpdate();
        em.createQuery("delete from Customer").executeUpdate();
        Customer kim = new Customer(1, "Kim", null);
        Customer noName = new Customer(2, null, null);
        em.persist(kim);
        em.persist(noName);
        Object[][] rows = {
                // price, status, active, description, kind, customer
                {50.0, 1, true, "a", Order.Kind.NORMAL, kim},
                {150.0, 1, true, "b", Order.Kind.RUSH, kim},
                {250.0, 2, false, null, Order.Kind.NORMAL, noName},
                {120.0, 1, false, "O'Brien", null, null},
                {80.0, 3, true, "x", Order.Kind.RUSH, kim},
        };
        for (Object[] r : rows) {
            Order o = new Order();
            o.setPrice((Double) r[0]);
            o.setStatus(((Integer) r[1]).shortValue());
            o.setActive((Boolean) r[2]);
            o.setDescription((String) r[3]);
            o.setKind((Order.Kind) r[4]);
            o.setCustomer((Customer) r[5]);
            em.persist(o);
        }
        em.getTransaction().commit();
        all = em.createQuery("select o from Order o left join fetch o.customer", Order.class).getResultList();
        em.close();
    }

    private List<Long> ids(List<Order> orders) {
        return orders.stream().map(Order::getId).sorted().toList();
    }

    private List<Long> javaIds(Predicate<Order> p) {
        return all.stream().filter(p).map(Order::getId).sorted(Comparator.naturalOrder()).toList();
    }

    @Test
    void literalsAndBinds() {
        // @start region="to-hql"
        RenderedQuery q = qb.from(Order.class)
                .where(o -> o.getStatus() == 1 && o.getPrice() > 100)
                .toHql();
        // constants written in the lambda are literals
        assertEquals("select e0 from Order e0 where e0.status = 1 and e0.price > 100.0", q.hql());
        assertEquals(Map.of(), q.parameters());

        double minPrice = 100;
        RenderedQuery q2 = qb.from(Order.class)
                .where(o -> o.getStatus() == 1 && o.getPrice() > minPrice)
                .toHql();
        // captured values are bind parameters
        assertEquals("select e0 from Order e0 where e0.status = 1 and e0.price > :p1", q2.hql());
        assertEquals(Map.of("p1", 100.0), q2.parameters());
        // @end
    }

    @Test
    void toListRuns() {
        // @start region="to-list"
        QueryBuilder qb = new QueryBuilder(emf);
        List<Order> orders = qb.from(Order.class)
                .where(o -> o.getStatus() == 1 && o.getPrice() > 100)
                .toList();
        // @end
        assertEquals(javaIds(o -> o.getStatus() == 1 && o.getPrice() > 100), ids(orders));
    }

    @Test
    void manyWhereCalls() {
        // @start region="where-many"
        RenderedQuery q = qb.from(Order.class)
                .where(o -> o.getStatus() == 1 || o.getStatus() == 3)
                .where(o -> o.isActive())
                .toHql();
        assertEquals("select e0 from Order e0 where (e0.status = 1 or e0.status = 3) and e0.active = true", q.hql());
        // @end
        assertEquals(javaIds(o -> (o.getStatus() == 1 || o.getStatus() == 3) && o.isActive()),
                ids(qb.from(Order.class).where(o -> o.getStatus() == 1 || o.getStatus() == 3).where(o -> o.isActive()).toList()));
    }

    @Test
    void stringLiteralIsEscaped() {
        // @start region="string-literal"
        RenderedQuery q = qb.from(Order.class).where(o -> o.getDescription().equals("O'Brien")).toHql();
        assertEquals("select e0 from Order e0 where e0.description = 'O''Brien'", q.hql());
        // @end
        assertEquals(javaIds(o -> "O'Brien".equals(o.getDescription())),
                ids(qb.from(Order.class).where(o -> o.getDescription().equals("O'Brien")).toList()));
    }

    @Test
    void nullSafeCapturedValue() {
        // @start region="null-safe"
        // the SQL meaning: = NULL matches no row; lambdaQL logs LQL-4045 at WARN when it runs with a null
        String d = null;
        RenderedQuery q = qb.from(Order.class).where(o -> Objects.equals(o.getDescription(), d)).toHql();
        assertEquals("select e0 from Order e0 where e0.description = :p1", q.hql());
        // to find NULL, write the null check
        RenderedQuery q3 = qb.from(Order.class).where(o -> o.getDescription() == null).toHql();
        assertEquals("select e0 from Order e0 where e0.description is null", q3.hql());

        String d2 = "a";
        RenderedQuery q2 = qb.from(Order.class).where(o -> Objects.equals(o.getDescription(), d2)).toHql();
        assertEquals("select e0 from Order e0 where e0.description = :p1", q2.hql());
        // @end
        assertEquals(List.of(), ids(qb.from(Order.class).where(o -> Objects.equals(o.getDescription(), d)).toList()));
        assertEquals(javaIds(o -> Objects.equals(o.getDescription(), d2)),
                ids(qb.from(Order.class).where(o -> Objects.equals(o.getDescription(), d2)).toList()));
    }

    @Test
    void associationPathIsInnerJoin() {
        // @start region="inner-join"
        String name = "Kim";
        RenderedQuery q = qb.from(Order.class).where(o -> o.getCustomer().getName().equals(name)).toHql();
        assertEquals("select e0 from Order e0 join e0.customer j1 where j1.name = :p1", q.hql());
        // @end
        assertEquals(javaIds(o -> o.getCustomer() != null && name.equals(o.getCustomer().getName())),
                ids(qb.from(Order.class).where(o -> o.getCustomer().getName().equals(name)).toList()));
    }

    @Test
    void nullGuardKeepsRowsWithoutAssociation() {
        // @start region="null-guard"
        String name = "Kim";
        // the null guard keeps orders without a customer, as in Java: a left join
        RenderedQuery q = qb.from(Order.class)
                .where(o -> o.getCustomer() == null || o.getCustomer().getName().equals(name))
                .toHql();
        assertEquals("select e0 from Order e0 left join e0.customer j1 where e0.customer is null or j1.name = :p1", q.hql());

        // no order without a customer can match: an inner join
        RenderedQuery q2 = qb.from(Order.class)
                .where(o -> o.getCustomer() != null && o.getCustomer().getName().equals(name))
                .toHql();
        assertEquals("select e0 from Order e0 join e0.customer j1 where e0.customer is not null and j1.name = :p1", q2.hql());
        // @end
        assertEquals(javaIds(o -> o.getCustomer() == null || name.equals(o.getCustomer().getName())),
                ids(qb.from(Order.class).where(o -> o.getCustomer() == null || o.getCustomer().getName().equals(name)).toList()));
        assertEquals(javaIds(o -> o.getCustomer() != null && name.equals(o.getCustomer().getName())),
                ids(qb.from(Order.class).where(o -> o.getCustomer() != null && o.getCustomer().getName().equals(name)).toList()));
        // a guard in one where call and a plain path in another: the AND drops the missing rows, so inner join
        assertEquals("select e0 from Order e0 join e0.customer j1 where (e0.customer is null or j1.name = :p1) and j1.name is not null",
                qb.from(Order.class)
                        .where(o -> o.getCustomer() == null || o.getCustomer().getName().equals(name))
                        .where(o -> o.getCustomer().getName() != null)
                        .toHql().hql());
        // a status 1 order without a customer matches the first part, as in Java: a left join
        assertEquals(javaIds(o -> (o.getCustomer() == null && o.getStatus() == 1)
                        || (o.getCustomer() != null && name.equals(o.getCustomer().getName()))),
                ids(qb.from(Order.class)
                        .where(o -> (o.getCustomer() == null && o.getStatus() == 1) || o.getCustomer().getName().equals(name))
                        .toList()));
    }

    @Test
    void capturedNullGuard() {
        // @start region="captured-null-guard"
        Double min = null;                                       // for example an empty search field
        RenderedQuery q = qb.from(Order.class).where(o -> min == null || o.getPrice() > min).toHql();
        // the guard is decided in Java when the query runs: no filter at all
        assertEquals("select e0 from Order e0", q.hql());

        Double min2 = 100.0;
        RenderedQuery q2 = qb.from(Order.class).where(o -> min2 == null || o.getPrice() > min2).toHql();
        assertEquals("select e0 from Order e0 where e0.price > :p1", q2.hql());
        assertEquals(Map.of("p1", 100.0), q2.parameters());
        // @end
        assertEquals(javaIds(o -> true), ids(qb.from(Order.class).where(o -> min == null || o.getPrice() > min).toList()));
        assertEquals(javaIds(o -> o.getPrice() > min2),
                ids(qb.from(Order.class).where(o -> min2 == null || o.getPrice() > min2).toList()));
        // automatic unboxing is not run in Java, so the guard may also come last: the same result, no filter
        assertEquals("select e0 from Order e0", qb.from(Order.class).where(o -> o.getPrice() > min || min == null).toHql().hql());
        // the guard with AND: no rows, and the compare is never rendered
        assertEquals("select e0 from Order e0 where 1=0",
                qb.from(Order.class).where(o -> min != null && o.getPrice() > min).toHql().hql());
    }

    @Test
    void notInWithCapturedList() {
        // @start region="not-in"
        List<Long> ids = List.of(1L, 2L);
        // the id can never be NULL: a plain NOT IN
        assertEquals("select e0 from Order e0 where e0.id not in (:p1)",
                qb.from(Order.class).where(o -> !ids.contains(o.getId())).toHql().hql());
        // a column that can be NULL: the SQL meaning, a NULL value is not "not in" the list (DEBUG LQL-5009);
        // to keep those rows, write o.getDescription() == null || !descs.contains(o.getDescription())
        List<String> descs = Arrays.asList("a", "b");
        assertEquals("select e0 from Order e0 where e0.description not in (:p1)",
                qb.from(Order.class).where(o -> !descs.contains(o.getDescription())).toHql().hql());
        // @end
        assertEquals(javaIds(o -> !ids.contains(o.getId())),
                ids(qb.from(Order.class).where(o -> !ids.contains(o.getId())).toList()));
        assertEquals(javaIds(o -> o.getDescription() != null && !descs.contains(o.getDescription())),
                ids(qb.from(Order.class).where(o -> !descs.contains(o.getDescription())).toList()));
    }

    @Test
    void neverNullColumn() {
        long id = all.get(0).getId();
        // @start region="never-null"
        // the id can never be NULL, so the null check is dropped (this is what Kotlin's it.id == id compiles to)
        assertEquals("select e0 from Order e0 where e0.id = :p1",
                qb.from(Order.class).where(o -> o.getId() != null && o.getId() == id).toHql().hql());
        assertEquals("select e0 from Order e0 where 1=0", qb.from(Order.class).where(o -> o.getId() == null).toHql().hql());
        // through an association the row may have no customer, so the check stays
        assertEquals("select e0 from Order e0 where e0.customer.id is null",
                qb.from(Order.class).where(o -> Objects.equals(o.getCustomer().getId(), null)).toHql().hql());
        // @end
        assertEquals(List.of(id), ids(qb.from(Order.class).where(o -> o.getId() != null && o.getId() == id).toList()));
    }

    @Test
    void foreignKeyPathHasNoJoin() {
        // @start region="fk-path"
        RenderedQuery q = qb.from(Order.class).where(o -> o.getCustomer().getId() == 1).toHql();
        assertEquals("select e0 from Order e0 where e0.customer.id = 1", q.hql());
        // @end
        assertEquals(javaIds(o -> o.getCustomer() != null && o.getCustomer().getId() == 1),
                ids(qb.from(Order.class).where(o -> o.getCustomer().getId() == 1).toList()));
    }

    @Test
    void noAssociation() {
        // @start region="no-association"
        RenderedQuery q = qb.from(Order.class).where(o -> o.getCustomer() == null).toHql();
        assertEquals("select e0 from Order e0 where e0.customer is null", q.hql());
        // @end
        assertEquals(javaIds(o -> o.getCustomer() == null),
                ids(qb.from(Order.class).where(o -> o.getCustomer() == null).toList()));
    }

    @Test
    void inWithCapturedList() {
        // @start region="in"
        List<Short> statuses = List.of((short) 2, (short) 3);
        RenderedQuery q = qb.from(Order.class).where(o -> statuses.contains(o.getStatus())).toHql();
        assertEquals("select e0 from Order e0 where e0.status in (:p1)", q.hql());
        // @end
        assertEquals(javaIds(o -> statuses.contains(o.getStatus())),
                ids(qb.from(Order.class).where(o -> statuses.contains(o.getStatus())).toList()));
        List<Short> none = List.of();
        assertEquals("select e0 from Order e0 where 1=0", qb.from(Order.class).where(o -> none.contains(o.getStatus())).toHql().hql());
    }

    @Test
    void booleanColumn() {
        // @start region="boolean"
        assertEquals("select e0 from Order e0 where e0.active = true",
                qb.from(Order.class).where(o -> o.isActive()).toHql().hql());
        assertEquals("select e0 from Order e0 where e0.active = false",       // primitive boolean: never NULL
                qb.from(Order.class).where(o -> !o.isActive()).toHql().hql());
        // @end
        assertEquals(javaIds(o -> !o.isActive()), ids(qb.from(Order.class).where(o -> !o.isActive()).toList()));
    }

    @Test
    void enumLiteral() {
        // @start region="enum"
        RenderedQuery q = qb.from(Order.class).where(o -> o.getKind() == Order.Kind.RUSH).toHql();
        assertEquals("select e0 from Order e0 where e0.kind = org.lambdaql.query.Order$Kind.RUSH", q.hql());
        // @end
        assertEquals(javaIds(o -> o.getKind() == Order.Kind.RUSH),
                ids(qb.from(Order.class).where(o -> o.getKind() == Order.Kind.RUSH).toList()));
    }

    @Test
    void notAndOr() {
        assertEquals(javaIds(o -> !(o.isActive() && o.getPrice() > 100) || o.getStatus() == 2),
                ids(qb.from(Order.class).where(o -> !(o.isActive() && o.getPrice() > 100) || o.getStatus() == 2).toList()));
    }

    @Test
    void unsupportedCallIsRejected() {
        // @start region="rejected"
        assertThrows(LambdaQLException.class,
                () -> qb.from(Order.class).where(o -> o.getDescription().trim().equals("a")));
        // @end
    }

    /** Minimal Spring context: H2 from application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = "org.lambdaql.query")
    static class Context {
    }
}
