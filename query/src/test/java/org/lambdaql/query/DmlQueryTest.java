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

import java.util.List;
import java.util.Objects;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lambdaql.Dml.value;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end tests for UPDATE, DELETE, INSERT and upsert (ROADMAP M10, D28, D40): the HQL and the rows after the run.
 * Statements with a condition are checked against {@code qb.from(X).where(same lambda)}, so they change the same rows.
 * The region markers are used by Javadoc snippet tags.
 */
@SpringBootTest(classes = DmlQueryTest.Context.class)
@ActiveProfiles("test")
class DmlQueryTest {

    @Autowired
    private EntityManagerFactory emf;

    private QueryBuilder qb;

    /** A list element for {@code values(list, ...)}. */
    record Line(long id, double price) {
    }

    @BeforeEach
    void seed() {
        qb = new QueryBuilder(emf);
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        for (String e : List.of("Item", "Order", "Customer", "Archive", "VOrder", "IdOrder")) {
            em.createQuery("delete from " + e).executeUpdate();
        }
        Customer kim = new Customer(1, "Kim", null);
        Customer noName = new Customer(2, null, null);
        em.persist(kim);
        em.persist(noName);
        Object[][] rows = {
                // price, status, customer
                {50.0, 1, kim},
                {150.0, 1, noName},
                {250.0, 2, null},
                {120.0, 3, kim},
        };
        for (Object[] r : rows) {
            Order o = new Order();
            o.setPrice((Double) r[0]);
            o.setStatus(((Integer) r[1]).shortValue());
            o.setActive(true);
            o.setCustomer((Customer) r[2]);
            em.persist(o);
        }
        VOrder v = new VOrder();
        v.setId(1L);
        v.setStatus(1);
        em.persist(v);
        em.getTransaction().commit();
        em.close();
    }

    private List<Order> orders() {
        EntityManager em = emf.createEntityManager();
        try {
            return em.createQuery("select o from Order o left join fetch o.customer order by o.id", Order.class).getResultList();
        } finally {
            em.close();
        }
    }

    private <X> X find(Class<X> type, Object id) {
        EntityManager em = emf.createEntityManager();
        try {
            return em.find(type, id);
        } finally {
            em.close();
        }
    }

    private static List<Long> ids(List<Order> list) {
        return list.stream().map(Order::getId).sorted().toList();
    }

    private static boolean mariadb() {
        return System.getProperty("lql.db", "h2").toLowerCase(Locale.ROOT).equals("mariadb");
    }

    @Test
    void update() {
        List<Long> target = ids(qb.from(Order.class).where(o -> o.getStatus() == 1).toList());
        // @start region="update"
        short st = 5;
        ExecutableUpdate<Order> u = qb.update(Order.class)
                .set(o -> { o.setStatus(st); o.setPrice(o.getPrice() * 2); })
                .where(o -> o.getStatus() == 1);
        // the price on the right is the value before the change
        assertEquals("update Order e0 set e0.status = :p1, e0.price = (e0.price * 2.0) where e0.status = 1", u.toHql().hql());
        int changed = u.execute();
        // @end
        assertEquals(target.size(), changed);
        for (Order o : orders()) {
            if (target.contains(o.getId())) {
                assertEquals(5, o.getStatus());
                assertTrue(o.getPrice() == 100.0 || o.getPrice() == 300.0);
            }
        }
    }

    @Test
    void updateWithValue() {
        // @start region="update-value"
        // entities without setters: Dml.value(column, value), the column written as a getter call
        ExecutableUpdate<Order> u = qb.update(Order.class)
                .set(o -> value(o.getDescription(), "done"))
                .where(o -> o.getStatus() == 2);
        assertEquals("update Order e0 set e0.description = 'done' where e0.status = 2", u.toHql().hql());
        // @end
        assertEquals(1, u.execute());
        assertEquals(1, orders().stream().filter(o -> "done".equals(o.getDescription())).count());
    }

    private void items() {
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        List<Order> all = em.createQuery("select o from Order o order by o.id", Order.class).getResultList();
        double[][] lines = {{0, 10}, {0, 20}, {2, 5}};     // order index, price
        for (double[] l : lines) {
            Item i = new Item();
            i.setOrder(all.get((int) l[0]));
            i.setPrice(l[1]);
            em.persist(i);
        }
        em.getTransaction().commit();
        em.close();
    }

    @Test
    void updateWithSubquery() {
        items();
        // @start region="update-sub"
        // the second parameter starts a subquery, as in where((o, sub) -> ...) (D49)
        ExecutableUpdate<Order> u = qb.update(Order.class)
                .set((o, sub) -> o.setPrice(sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).sum(i -> i.getPrice())))
                .where((o, sub) -> sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).exists());
        assertEquals("update Order e0 set e0.price = (select sum(s1.price) from Item s1 where s1.order.id = e0.id)"
                + " where exists (select 1 from Item s1 where s1.order.id = e0.id)", u.toHql().hql());
        // sum gives NULL when there is no row; coalesce keeps a NOT NULL column valid for every row
        ExecutableUpdate<Order> all = qb.update(Order.class)
                .set((o, sub) -> value(o.getPrice(), Objects.requireNonNullElse(
                        sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).sum(i -> i.getPrice()), 0.0)))
                .all();
        assertEquals("update Order e0 set e0.price = coalesce((select sum(s1.price) from Item s1 where s1.order.id = e0.id), 0.0)",
                all.toHql().hql());
        // @end
        assertEquals(2, u.execute());
        assertEquals(List.of(30.0, 150.0, 5.0, 120.0), orders().stream().map(Order::getPrice).toList());
        assertEquals(4, all.execute());
        assertEquals(List.of(30.0, 0.0, 5.0, 0.0), orders().stream().map(Order::getPrice).toList());
        // a subquery on the target table: works on all three databases (no MariaDB 1093 for UPDATE)
        seed();
        assertEquals(4, qb.update(Order.class).set((o, sub) -> o.setPrice(sub.from(Order.class).avg(x -> x.getPrice()))).all().execute());
        assertEquals(List.of(142.5, 142.5, 142.5, 142.5), orders().stream().map(Order::getPrice).toList());
    }

    @Test
    void insertSelectWithSubquery() {
        items();
        // @start region="insert-select-sub"
        // where((o, sub) -> ...) on the source, and select((a, o, sub) -> ...): the third parameter starts a subquery
        ExecutableInsert<Archive> i = qb.insert(Archive.class).from(Order.class)
                .where((o, sub) -> sub.from(Item.class).where(x -> x.getOrder().getId() == o.getId()).exists())
                .select((a, o, sub) -> {
                    a.setId(o.getId());
                    a.setPrice(sub.from(Item.class).where(x -> x.getOrder().getId() == o.getId()).sum(x -> x.getPrice()));
                });
        assertEquals("insert into Archive (id, price) select e0.id, (select sum(s2.price) from Item s2 where s2.order.id = e0.id)"
                + " from Order e0 where exists (select 1 from Item s1 where s1.order.id = e0.id)", i.toHql().get(0).hql());
        // @end
        assertEquals(2, i.execute());
        List<Long> ids = orders().stream().map(Order::getId).toList();
        assertEquals(30.0, find(Archive.class, ids.get(0)).getPrice());
        assertEquals(5.0, find(Archive.class, ids.get(2)).getPrice());
    }

    @Test
    void valuesSubProbe() {
        items();
        String[] hqls = {
            "insert into Archive (id, price) values (100, (select sum(i.price) from Item i))",
            "insert into Archive (id, price) values (101, (select sum(i.price) from Item i where i.order.id = :p1)), (102, (select max(o.price) from Order o))",
            "insert into Archive (id, price) values (103, 1.0)",
            "insert into Archive (id, price) values (104, (select max(a.price) from Archive a))",
            "insert into Archive e0 (id, price) values (103, (select sum(i.price) from Item i)) on conflict(id) do update set price = excluded.price",
            "insert into Archive e0 (id, price) values (105, (select sum(i.price) from Item i)) on conflict(id) do update set price = excluded.price",
            "insert into Archive e0 (id, price) values (103, 2.0) on conflict(id) do update set price = (select max(o.price) from Order o)",
        };
        long oid = orders().get(0).getId();
        int n = 0;
        for (String h : hqls) {
            n++;
            EntityManager em = emf.createEntityManager();
            try {
                em.getTransaction().begin();
                var q = em.createQuery(h);
                if (h.contains(":p1")) q.setParameter("p1", oid);
                int r = q.executeUpdate();
                em.getTransaction().commit();
                Object rows = em.createQuery("select a.id, a.price from Archive a order by a.id").getResultList().stream()
                        .map(x -> java.util.Arrays.toString((Object[]) x)).toList();
                System.out.println("VSUB " + n + " -> " + r + " " + rows);
            } catch (RuntimeException e) {
                if (em.getTransaction().isActive()) em.getTransaction().rollback();
                Throwable c = e; while (c.getCause() != null) c = c.getCause();
                System.out.println("VSUB " + n + " -> FAIL " + c.getClass().getSimpleName() + ": " + String.valueOf(c.getMessage()).lines().findFirst().orElse(""));
            } finally {
                em.close();
            }
        }
    }

    @Test
    void updatePatchAndRules() {
        long id = orders().get(0).getId();
        // @start region="update-patch"
        // PATCH: assign only the fields that have a value; every set() adds assignments
        String memo = "checked";
        Short newStatus = null;
        UpdateSet<Order> u = qb.update(Order.class).set(o -> o.setDescription(memo));
        if (newStatus != null) u = u.set(o -> o.setStatus(newStatus));
        int changed = u.where(o -> o.getId() == id).execute();

        // reading a column that an earlier assignment wrote is an error (U3): the databases disagree on its value
        assertThrows(LambdaQLException.class,
                () -> qb.update(Order.class).set(o -> { o.setPrice(1); o.setTex((float) o.getPrice()); }));
        // the same column twice is an error (U4), also across set() calls
        assertThrows(LambdaQLException.class,
                () -> qb.update(Order.class).set(o -> o.setStatus((short) 1)).set(o -> o.setStatus((short) 2)));
        // @end
        assertEquals(1, changed);
        assertEquals("checked", find(Order.class, id).getDescription());
        // null into a column that can never be NULL (U6)
        assertThrows(LambdaQLException.class,
                () -> qb.update(Archive.class).set(a -> a.setId(null)).all());
    }

    @Test
    void updateWithAssociationPath() {
        List<Long> target = ids(qb.from(Order.class).where(o -> o.getCustomer().getName().equals("Kim")).toList());
        // @start region="update-path"
        // a path in the condition: a subquery, plus "customer exists" (the same rows as the select, D36, U9)
        ExecutableUpdate<Order> u = qb.update(Order.class)
                .set(o -> o.setCustName("K"))
                .where(o -> o.getCustomer().getName().equals("Kim"));
        assertEquals("update Order e0 set e0.custName = 'K' where (select s1.name from Customer s1 where s1.id = e0.customer.id) = 'Kim'"
                + " and e0.customer.id is not null", u.toHql().hql());
        // a path in a value: a subquery; NULL when there is no customer
        ExecutableUpdate<Order> copy = qb.update(Order.class).set(o -> o.setCustName(o.getCustomer().getName())).all();
        assertEquals("update Order e0 set e0.custName = (select s1.name from Customer s1 where s1.id = e0.customer.id)", copy.toHql().hql());
        // @end
        assertEquals(target.size(), u.execute());
        assertEquals(target, orders().stream().filter(o -> "K".equals(o.getCustName())).map(Order::getId).toList());
        assertEquals(4, copy.execute());
        for (Order o : orders()) {
            assertEquals(o.getCustomer() == null ? null : o.getCustomer().getName(), o.getCustName());
        }
    }

    @Test
    void versionedUpdate() {
        long before = find(VOrder.class, 1L).getVersion();
        // @start region="update-versioned"
        // an entity with a version field (Version annotation): the version goes up too
        ExecutableUpdate<VOrder> u = qb.update(VOrder.class).set(v -> v.setStatus(2)).where(v -> v.getId() == 1L);
        assertEquals("update versioned VOrder e0 set e0.status = 2 where e0.id = 1L", u.toHql().hql());
        // @end
        assertEquals(1, u.execute());
        assertEquals(before + 1, find(VOrder.class, 1L).getVersion());
    }

    @Test
    void delete() {
        // @start region="delete"
        ExecutableDelete<Order> d = qb.delete(Order.class).where(o -> o.getStatus() == 3);
        assertEquals("delete from Order e0 where e0.status = 3", d.toHql().hql());
        int deleted = d.execute();
        // a path: the same rows as qb.from(Order.class).where(same lambda) - orders without a customer stay
        ExecutableDelete<Order> byName = qb.delete(Order.class).where(o -> o.getCustomer().getName() == null);
        assertEquals("delete from Order e0 where (select s1.name from Customer s1 where s1.id = e0.customer.id) is null"
                + " and e0.customer.id is not null", byName.toHql().hql());
        // @end
        assertEquals(1, deleted);
        List<Long> selected = ids(qb.from(Order.class).where(o -> o.getCustomer().getName() == null).toList());
        List<Long> before = ids(orders());
        assertEquals(selected.size(), byName.execute());
        List<Long> after = ids(orders());
        assertEquals(before.stream().filter(i -> !selected.contains(i)).toList(), after);
        assertEquals(after.size(), qb.delete(Order.class).all().execute());
    }

    @Test
    void insert() {
        // @start region="insert"
        ExecutableInsert<Archive> i = qb.insert(Archive.class)
                .values(a -> { a.setId(100L); a.setPrice(9.5); a.setCustomerName("Kim"); });
        assertEquals("insert into Archive (id, price, customerName) values (100L, 9.5, 'Kim')", i.toHql().get(0).hql());
        int inserted = i.execute();
        // a generated id (IDENTITY) can be left out
        int generated = qb.insert(IdOrder.class).values(o -> { o.setPrice(1.0); o.setStatus(1); }).execute();
        // @end
        assertEquals(1, inserted);
        assertEquals(1, generated);
        assertEquals("Kim", find(Archive.class, 100L).getCustomerName());
        // values cannot read the new row
        assertThrows(LambdaQLException.class,
                () -> qb.insert(Archive.class).values(a -> { a.setId(1L); a.setPrice(a.getPrice()); }));
    }

    @Test
    void insertList() {
        // @start region="insert-list"
        List<Line> lines = List.of(new Line(201, 1.0), new Line(202, 2.0));
        ExecutableInsert<Archive> i = qb.insert(Archive.class).values(lines, (a, l) -> { a.setId(l.id()); a.setPrice(l.price()); });
        assertEquals("insert into Archive (id, price) values (:p1, :p2), (:p3, :p4)", i.toHql().get(0).hql());
        // @end
        assertEquals(2, i.execute());
        assertEquals(2.0, find(Archive.class, 202L).getPrice());
        assertEquals(0, qb.insert(Archive.class).values(List.<Line>of(), (a, l) -> a.setId(l.id())).execute());
    }

    @Test
    void insertSelect() {
        List<Order> source = qb.from(Order.class).where(o -> o.getStatus() == 1).toList();
        // @start region="insert-select"
        ExecutableInsert<Archive> i = qb.insert(Archive.class)
                .from(Order.class)
                .where(o -> o.getStatus() == 1)
                .select((a, o) -> { a.setId(o.getId()); a.setPrice(o.getPrice()); a.setCustomerName(o.getCustomer().getName()); });
        // a path in the select list: left join, so orders without a customer stay (U10)
        assertEquals("insert into Archive (id, price, customerName) select e0.id, e0.price, j1.name"
                + " from Order e0 left join e0.customer j1 where e0.status = 1", i.toHql().get(0).hql());
        // @end
        assertEquals(source.size(), i.execute());
        for (Order o : orders()) {
            if (o.getStatus() != 1) continue;
            Archive a = find(Archive.class, o.getId());
            assertEquals(o.getPrice(), a.getPrice());
            assertEquals(o.getCustomer() == null ? null : o.getCustomer().getName(), a.getCustomerName());
        }
    }

    @Test
    void upsert() {
        // @start region="upsert"
        // the key is the @Id; on conflict, the other assigned columns get the new values
        int inserted = qb.upsert(Archive.class).values(a -> { a.setId(100L); a.setPrice(1.0); }).execute();
        ExecutableInsert<Archive> again = qb.upsert(Archive.class).values(a -> { a.setId(100L); a.setPrice(2.0); });
        assertEquals("insert into Archive e0 (id, price) values (100L, 2.0) on conflict(id) do update set price = excluded.price",
                again.toHql().get(0).hql());
        int updated = again.execute();                     // MariaDB counts an updated row as 2, H2 and Oracle as 1
        // @end
        assertEquals(1, inserted);
        assertEquals(mariadb() ? 2 : 1, updated);
        assertEquals(2.0, find(Archive.class, 100L).getPrice());
    }

    @Test
    void onConflictUpdate() {
        // @start region="on-conflict"
        // on conflict, change the existing row: a getter here reads the existing row
        ExecutableInsert<Archive> add = qb.insert(Archive.class)
                .values(a -> { a.setId(100L); a.setPrice(5.0); })
                .onConflict(a -> a.getId())
                .update(a -> a.setPrice(a.getPrice() + 5.0));
        assertEquals("insert into Archive e0 (id, price) values (100L, 5.0) on conflict(id) do update set price = (e0.price + 5.0)",
                add.toHql().get(0).hql());
        add.execute();                                     // inserted: 5.0
        add.execute();                                     // conflict: 5.0 + 5.0
        // @end
        assertEquals(10.0, find(Archive.class, 100L).getPrice());
    }

    @Test
    void insertIgnore() {
        // @start region="insert-ignore"
        ExecutableInsert<Archive> once = qb.insert(Archive.class)
                .values(a -> { a.setId(300L); a.setPrice(1.0); })
                .onConflict(a -> a.getId())
                .ignore();
        assertEquals("insert into Archive (id, price) select 300L, 1.0 where not exists (select 1 from Archive x where x.id = 300L)",
                once.toHql().get(0).hql());
        int first = once.execute();                        // 1: inserted
        int second = qb.insert(Archive.class).values(a -> { a.setId(300L); a.setPrice(2.0); })
                .onConflict(a -> a.getId()).ignore().execute();   // 0: the row exists, nothing changes
        // @end
        assertEquals(1, first);
        assertEquals(0, second);
        assertEquals(1.0, find(Archive.class, 300L).getPrice());
    }

    @Test
    void deleteWithSelfSubquery() {
        double avg = orders().stream().mapToDouble(Order::getPrice).average().orElseThrow();
        List<Long> below = ids(orders().stream().filter(o -> o.getPrice() < avg).toList());
        // @start region="delete-self-subquery"
        // the subquery reads the target table: lambdaQL reads the matching ids through a derived table first (U17),
        // because MariaDB cannot delete from a table that a subquery reads (error 1093)
        ExecutableDelete<Order> d = qb.delete(Order.class)
                .where((o, sub) -> o.getPrice() < sub.from(Order.class).avg(x -> x.getPrice()));
        assertEquals("delete from Order d0 where d0.id in (select t.id from (select e0.id as id from Order e0"
                + " where e0.price < (select avg(s1.price) from Order s1)) t)", d.toHql().hql());
        // a subquery on another table keeps the plain form
        ExecutableDelete<Order> unused = qb.delete(Order.class)
                .where((o, sub) -> !sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).exists());
        assertEquals("delete from Order e0 where not exists (select 1 from Item s1 where s1.order.id = e0.id)",
                unused.toHql().hql());
        // @end
        assertEquals(below.size(), d.execute());
        assertTrue(orders().stream().noneMatch(o -> below.contains(o.getId())));
        // correlated: the cheaper order of each status that has a more expensive one
        seed();
        List<Order> all = orders();
        List<Long> cheaper = ids(all.stream().filter(o -> all.stream()
                .anyMatch(x -> x.getStatus() == o.getStatus() && x.getPrice() > o.getPrice())).toList());
        ExecutableDelete<Order> c = qb.delete(Order.class).where((o, sub) -> sub.from(Order.class)
                .where(x -> x.getStatus() == o.getStatus() && x.getPrice() > o.getPrice()).exists());
        assertEquals(cheaper.size(), c.execute());
        assertTrue(orders().stream().noneMatch(o -> cheaper.contains(o.getId())));
    }

    @Test
    void deleteWithLimit() {
        long cheapest = orders().stream().filter(o -> o.getStatus() == 1).min(java.util.Comparator.comparing(Order::getPrice)).orElseThrow().getId();
        // @start region="delete-limit"
        // the first rows of qb.from(Order.class).where(same).orderBy(same); the id is the last sort key
        ExecutableDelete<Order> d = qb.delete(Order.class)
                .where(o -> o.getStatus() == 1)
                .orderBy((o, by) -> by.asc(o.getPrice()))
                .limit(1);
        assertEquals("delete from Order e0 where e0.id in (select t.id from (select x0.id as id from Order x0"
                + " where x0.status = 1 order by x0.price asc, x0.id asc limit 1) t)", d.toHql().hql());
        int deleted = d.execute();
        // @end
        assertEquals(1, deleted);
        assertTrue(orders().stream().noneMatch(o -> o.getId() == cheapest));
        assertEquals(3, orders().size());
        assertThrows(IllegalStateException.class, () -> qb.delete(Order.class).all().orderBy((o, by) -> by.asc(o.getId())).toHql());
    }

    @Test
    void updateWithLimit() {
        // @start region="update-limit"
        // a path in the condition: the nested select joins, so the rows are the same as the select
        ExecutableUpdate<Order> u = qb.update(Order.class)
                .set(o -> o.setDescription("top"))
                .where(o -> o.getCustomer().getName().equals("Kim"))
                .orderBy((o, by) -> by.desc(o.getPrice()))
                .limit(1);
        assertEquals("update Order e0 set e0.description = 'top' where e0.id in (select t.id from (select x0.id as id from Order x0"
                + " join x0.customer k1 where k1.name = 'Kim' order by x0.price desc, x0.id asc limit 1) t)", u.toHql().hql());
        // @end
        assertEquals(1, u.execute());
        Order top = orders().stream().filter(o -> "top".equals(o.getDescription())).findFirst().orElseThrow();
        assertEquals(120.0, top.getPrice());
    }

    @Test
    void executeOneProtects() {
        long id = orders().get(0).getId();
        // @start region="execute-one"
        // one row matches: it is deleted
        int one = qb.delete(Order.class).where(o -> o.getId() == id).executeOne();
        // two or more rows match: nothing is deleted
        assertThrows(jakarta.persistence.NonUniqueResultException.class,
                () -> qb.delete(Order.class).where(o -> o.getPrice() > 100).executeOne());
        // @end
        assertEquals(1, one);
        assertEquals(3, orders().size());
        assertEquals(0, qb.delete(Order.class).where(o -> o.getStatus() == 9).executeOne());
        assertThrows(jakarta.persistence.NonUniqueResultException.class,
                () -> qb.update(Order.class).set(o -> o.setDescription("x")).where(o -> o.isActive()).executeOne());
        assertEquals(0, orders().stream().filter(o -> "x".equals(o.getDescription())).count());
        long other = orders().get(0).getId();
        assertEquals(1, qb.update(Order.class).set(o -> o.setDescription("y")).where(o -> o.getId() == other).executeOne());
    }

    @Test
    void sharedEntityManagerNeedsTransaction() {
        EntityManager em = emf.createEntityManager();
        try {
            QueryBuilder shared = new QueryBuilder(em);
            // a shared entity manager (as with Spring) without a transaction: rejected before any SQL
            assertThrows(jakarta.persistence.TransactionRequiredException.class,
                    () -> shared.delete(Order.class).where(o -> o.getStatus() == 3).execute());
            em.getTransaction().begin();
            assertEquals(1, shared.delete(Order.class).where(o -> o.getStatus() == 3).execute());
            em.getTransaction().rollback();
        } finally {
            em.close();
        }
        assertEquals(4, orders().size());
    }

    @Test
    void renderedForLogs() {
        RenderedQuery q = qb.delete(Order.class).all().toHql();
        assertEquals("delete from Order e0", q.hql());
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @EntityScan("org.lambdaql.query")
    static class Context {
    }
}
