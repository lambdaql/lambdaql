package org.lambdaql.query;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lambdaql.LambdaQLException;
import org.lambdaql.engine.render.RenderedQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * End-to-end tests for {@code orderBy}, {@code limit} and {@code offset}: the HQL text and the row order.
 * Each row test also sorts the same data in Java and compares the id lists.
 * Descriptions are lower case only, so the order does not depend on the database collation.
 * The region markers are used by Javadoc snippet tags.
 */
@SpringBootTest(classes = SortQueryTest.Context.class)
@ActiveProfiles("test")
class SortQueryTest {

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
                // price, status, active, description, customer
                {50.0, 1, true, "b", kim},
                {150.0, 1, true, null, kim},
                {250.0, 2, false, "a", noName},
                {120.0, 1, false, "c", null},
                {80.0, 3, true, null, kim},
        };
        for (Object[] r : rows) {
            Order o = new Order();
            o.setPrice((Double) r[0]);
            o.setStatus(((Integer) r[1]).shortValue());
            o.setActive((Boolean) r[2]);
            o.setDescription((String) r[3]);
            o.setCustomer((Customer) r[4]);
            em.persist(o);
        }
        em.getTransaction().commit();
        all = em.createQuery("select o from Order o left join fetch o.customer", Order.class).getResultList();
        em.close();
    }

    private static List<Long> ids(List<Order> orders) {
        return orders.stream().map(Order::getId).toList();
    }

    private List<Long> javaIds(Comparator<Order> order) {
        return all.stream().sorted(order).map(Order::getId).toList();
    }

    private static <K extends Comparable<K>> Comparator<Order> nullsFirst(Function<Order, K> key) {
        return Comparator.comparing(key, Comparator.nullsFirst(Comparator.naturalOrder()));
    }

    private static <K extends Comparable<K>> Comparator<Order> nullsLast(Function<Order, K> key) {
        return Comparator.comparing(key, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private static final Comparator<Order> BY_ID = Comparator.comparing(Order::getId);

    @Test
    void basicSort() {
        // @start region="sort-basic"
        RenderedQuery q = qb.from(Order.class)
                .where(o -> o.isActive())
                .orderBy((o, by) -> by.desc(o.getPrice()).asc(o.getId()))
                .toHql();
        // price and id can never be NULL, so there is no "nulls" part
        assertEquals("select e0 from Order e0 where e0.active = true order by e0.price desc, e0.id asc", q.hql());
        // @end
        List<Order> rows = qb.from(Order.class).where(o -> o.isActive())
                .orderBy((o, by) -> by.desc(o.getPrice()).asc(o.getId())).toList();
        assertEquals(all.stream().filter(Order::isActive)
                .sorted(Comparator.comparing(Order::getPrice).reversed().thenComparing(BY_ID))
                .map(Order::getId).toList(), ids(rows));
    }

    @Test
    void nullPosition() {
        // @start region="sort-nulls"
        // default: asc puts NULL first, desc puts NULL last (the same on every database)
        assertEquals("select e0 from Order e0 order by e0.description asc nulls first, e0.id asc",
                qb.from(Order.class).orderBy((o, by) -> by.asc(o.getDescription()).asc(o.getId())).toHql().hql());
        // the second argument chooses: true = NULL first, false = NULL last
        assertEquals("select e0 from Order e0 order by e0.description asc nulls last, e0.id asc",
                qb.from(Order.class).orderBy((o, by) -> by.asc(o.getDescription(), false).asc(o.getId())).toHql().hql());
        assertEquals("select e0 from Order e0 order by e0.description desc nulls first, e0.id asc",
                qb.from(Order.class).orderBy((o, by) -> by.desc(o.getDescription(), true).asc(o.getId())).toHql().hql());
        // @end
        assertEquals(javaIds(nullsFirst(Order::getDescription).thenComparing(BY_ID)),
                ids(qb.from(Order.class).orderBy((o, by) -> by.asc(o.getDescription()).asc(o.getId())).toList()));
        assertEquals(javaIds(nullsLast(Order::getDescription).thenComparing(BY_ID)),
                ids(qb.from(Order.class).orderBy((o, by) -> by.asc(o.getDescription(), false).asc(o.getId())).toList()));
        assertEquals(javaIds(nullsLast(Order::getDescription).reversed().thenComparing(BY_ID)),
                ids(qb.from(Order.class).orderBy((o, by) -> by.desc(o.getDescription(), true).asc(o.getId())).toList()));
        assertEquals(javaIds(nullsFirst(Order::getDescription).reversed().thenComparing(BY_ID)),
                ids(qb.from(Order.class).orderBy((o, by) -> by.desc(o.getDescription()).asc(o.getId())).toList()));
    }

    @Test
    void runTimeDirection() {
        List<String> hql = new ArrayList<>();
        List<List<Long>> rows = new ArrayList<>();
        for (String text : List.of("desc", "asc_nulls_last")) {
            // @start region="sort-dynamic"
            Direction dir = Direction.parse(text);           // for example from a request parameter
            OrderedSelectQuery<Order> q = qb.from(Order.class)
                    .orderBy((o, by) -> by.sort(o.getDescription(), dir).asc(o.getId()));
            // @end
            hql.add(q.toHql().hql());
            rows.add(ids(q.toList()));
        }
        // the same lambda with two directions: the analysis is cached, the direction is read on every run
        assertEquals("select e0 from Order e0 order by e0.description desc nulls last, e0.id asc", hql.get(0));
        assertEquals("select e0 from Order e0 order by e0.description asc nulls last, e0.id asc", hql.get(1));
        assertEquals(javaIds(nullsFirst(Order::getDescription).reversed().thenComparing(BY_ID)), rows.get(0));
        assertEquals(javaIds(nullsLast(Order::getDescription).thenComparing(BY_ID)), rows.get(1));
    }

    @Test
    void associationPathIsLeftJoin() {
        // @start region="sort-path"
        RenderedQuery q = qb.from(Order.class)
                .orderBy((o, by) -> by.asc(o.getCustomer().getName()).asc(o.getId()))
                .toHql();
        // a path used only for sorting is a LEFT JOIN: the order without a customer stays
        assertEquals("select e0 from Order e0 left join e0.customer j1 order by j1.name asc nulls first, e0.id asc", q.hql());
        // @end
        List<Order> rows = qb.from(Order.class)
                .orderBy((o, by) -> by.asc(o.getCustomer().getName()).asc(o.getId())).toList();
        assertEquals(5, rows.size());
        assertEquals(javaIds(nullsFirst((Order o) -> o.getCustomer() == null ? null : o.getCustomer().getName())
                .thenComparing(BY_ID)), ids(rows));
    }

    @Test
    void wherePathStaysInnerJoin() {
        // @start region="sort-path-where"
        String name = "Kim";
        RenderedQuery q = qb.from(Order.class)
                .where(o -> o.getCustomer().getName().equals(name))
                .orderBy((o, by) -> by.desc(o.getCustomer().getName()).desc(o.getPrice()))
                .toHql();
        // the condition already joined the customer, so the sort uses the same inner join
        assertEquals("select e0 from Order e0 join e0.customer j1 where j1.name = :p1"
                + " order by j1.name desc nulls last, e0.price desc", q.hql());
        // @end
    }

    @Test
    void oneKey() {
        // @start region="sort-key"
        assertEquals("select e0 from Order e0 order by e0.price asc",
                qb.from(Order.class).orderBy(o -> o.getPrice()).toHql().hql());
        assertEquals("select e0 from Order e0 order by e0.price desc",
                qb.from(Order.class).orderBy(Order::getPrice, Direction.DESC).toHql().hql());
        // @end
        assertEquals(javaIds(Comparator.comparing(Order::getPrice).reversed()),
                ids(qb.from(Order.class).orderBy(Order::getPrice, Direction.DESC).toList()));
    }

    @Test
    void paging() {
        // @start region="sort-page"
        OrderedSelectQuery<Order> page = qb.from(Order.class)
                .orderBy((o, by) -> by.asc(o.getStatus()))
                .limit(2)
                .offset(2);
        // status has ties, so the id is added as the last key and rows do not move between pages
        assertEquals("select e0 from Order e0 order by e0.status asc, e0.id asc", page.toHql().hql());
        List<Order> rows = page.toList();
        // @end
        List<Long> expected = javaIds(Comparator.comparing(Order::getStatus).thenComparing(BY_ID));
        assertEquals(expected.subList(2, 4), ids(rows));

        // every page together gives every row once
        Set<Long> seen = new HashSet<>();
        List<Long> walked = new ArrayList<>();
        for (int offset = 0; offset < 5; offset += 2) {
            for (Order o : qb.from(Order.class).orderBy((x, by) -> by.asc(x.getStatus())).limit(2).offset(offset).toList()) {
                walked.add(o.getId());
                seen.add(o.getId());
            }
        }
        assertEquals(expected, walked);
        assertEquals(5, seen.size());

        // the id is not added twice
        assertEquals("select e0 from Order e0 order by e0.id desc",
                qb.from(Order.class).orderBy((o, by) -> by.desc(o.getId())).limit(2).toHql().hql());
    }

    @Test
    void pagingWithoutSort() {
        // @start region="page-only"
        RenderedQuery q = qb.from(Order.class).where(o -> o.isActive()).limit(2).toHql();
        assertEquals("select e0 from Order e0 where e0.active = true order by e0.id asc", q.hql());
        // @end
        assertEquals(all.stream().filter(Order::isActive).map(Order::getId).sorted().limit(2).toList(),
                ids(qb.from(Order.class).where(o -> o.isActive()).limit(2).toList()));
    }

    @Test
    void reusableSpec() {
        // @start region="sort-spec"
        // keep it, for example as a static final field, and use it in many queries
        SortSpec<Order> byDescription = SortSpec.of(Order.class, (o, by) -> by.desc(o.getDescription()).asc(o.getId()));
        RenderedQuery q = qb.from(Order.class).where(o -> o.isActive()).orderBy(byDescription).toHql();
        assertEquals("select e0 from Order e0 where e0.active = true order by e0.description desc nulls last, e0.id asc", q.hql());
        // @end
        assertEquals(all.stream().filter(Order::isActive)
                        .sorted(nullsFirst(Order::getDescription).reversed().thenComparing(BY_ID)).map(Order::getId).toList(),
                ids(qb.from(Order.class).where(o -> o.isActive()).orderBy(byDescription).toList()));
    }

    @Test
    void specThen() {
        // @start region="sort-spec-then"
        SortSpec<Order> byStatus = SortSpec.of(Order.class, (o, by) -> by.asc(o.getStatus()));
        SortSpec<Order> byPrice = SortSpec.of(Order.class, (o, by) -> by.desc(o.getPrice()));
        assertEquals("select e0 from Order e0 order by e0.status asc, e0.price desc",
                qb.from(Order.class).orderBy(byStatus.then(byPrice)).toHql().hql());
        // then() also takes a lambda
        assertEquals("select e0 from Order e0 order by e0.status asc, e0.id desc",
                qb.from(Order.class).orderBy(byStatus.then((o, by) -> by.desc(o.getId()))).toHql().hql());
        // @end
        assertEquals(javaIds(Comparator.comparing(Order::getStatus).thenComparing(Comparator.comparing(Order::getPrice).reversed())),
                ids(qb.from(Order.class).orderBy(byStatus.then(byPrice)).toList()));
    }

    @Test
    void specReversed() {
        // @start region="sort-spec-reversed"
        SortSpec<Order> spec = SortSpec.of(Order.class, (o, by) -> by.asc(o.getDescription()).asc(o.getId()));
        // every key is reversed, the NULL position too: asc nulls first -> desc nulls last
        assertEquals("select e0 from Order e0 order by e0.description desc nulls last, e0.id desc",
                qb.from(Order.class).orderBy(spec.reversed()).toHql().hql());
        // @end
        List<Long> forward = ids(qb.from(Order.class).orderBy(spec).toList());
        List<Long> backward = new ArrayList<>(ids(qb.from(Order.class).orderBy(spec.reversed()).toList()));
        java.util.Collections.reverse(backward);
        assertEquals(forward, backward);
        // reversing twice gives the first order again
        assertEquals("select e0 from Order e0 order by e0.description asc nulls first, e0.id asc",
                qb.from(Order.class).orderBy(spec.reversed().reversed()).toHql().hql());
    }

    @Test
    void specFromPathText() {
        // @start region="sort-spec-path"
        // for sorts chosen outside the code (Spring Sort uses this); only real properties are accepted
        SortSpec<Order> byName = SortSpec.ofPath(Order.class, "customer.name", Direction.DESC)
                .then(SortSpec.ofPath(Order.class, "id", Direction.ASC));
        assertEquals("select e0 from Order e0 left join e0.customer j1 order by j1.name desc nulls last, e0.id asc",
                qb.from(Order.class).orderBy(byName).toHql().hql());
        assertThrows(IllegalArgumentException.class,
                () -> qb.from(Order.class).orderBy(SortSpec.ofPath(Order.class, "price; drop table x", Direction.ASC)));
        // @end
        assertEquals(javaIds(nullsFirst((Order o) -> o.getCustomer() == null ? null : o.getCustomer().getName())
                        .reversed().thenComparing(BY_ID)),
                ids(qb.from(Order.class).orderBy(byName).toList()));
    }

    @Test
    void rejected() {
        // @start region="sort-rejected"
        // a condition inside the sort lambda (rule S4)
        assertThrows(LambdaQLException.class, () -> qb.from(Order.class)
                .orderBy((o, by) -> o.isActive() ? by.asc(o.getPrice()) : by.desc(o.getPrice())));
        // a key without the entity
        assertThrows(LambdaQLException.class, () -> qb.from(Order.class).orderBy((o, by) -> by.asc(1)));
        // @end
    }

    /** Minimal Spring context: H2 from application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @EntityScan("org.lambdaql.query")
    static class Context {
    }
}
