package org.lambdaql.query;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.NonUniqueResultException;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lambdaql.LambdaQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lambdaql.Dml.value;

/**
 * The examples of the Java manual, chapter 7 (update, delete, insert, upsert).
 * Each test checks the HQL inside the region and the rows after the run.
 */
@SpringBootTest(classes = ManualJavaDmlTest.Context.class)
@ActiveProfiles("test")
class ManualJavaDmlTest {

    /** A list element for {@code values(list, ...)}. */
    record Line(long id, double price) {
    }

    @Autowired
    private EntityManagerFactory emf;

    private QueryBuilder qb;
    private Customer kim;
    private Customer lee;

    @BeforeEach
    void seed() {
        qb = new QueryBuilder(emf);
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        for (String e : List.of("Item", "Order", "Customer", "Archive", "VOrder", "IdOrder")) {
            em.createQuery("delete from " + e).executeUpdate();
        }
        kim = new Customer(1, "Kim", null);
        kim.setGrade(5);
        lee = new Customer(2, "Lee", null);
        lee.setGrade(2);
        em.persist(kim);
        em.persist(lee);
        Object[][] rows = {
                // price, status, customer, cnt, total
                {50.0, 1, kim, 1, null},
                {150.0, 1, lee, null, 10.0},
                {250.0, 2, null, 3, null},
                {120.0, 3, kim, 2, 5.0},
        };
        for (Object[] r : rows) {
            Order o = new Order();
            o.setPrice((Double) r[0]);
            o.setStatus(((Integer) r[1]).shortValue());
            o.setActive(true);
            o.setDescription("d");
            o.setCustomer((Customer) r[2]);
            o.setCnt((Integer) r[3]);
            o.setTotal((Double) r[4]);
            em.persist(o);
        }
        Order first = em.createQuery("select o from Order o where o.price = 50.0", Order.class).getSingleResult();
        Item item = new Item();
        item.setOrder(first);
        item.setPrice(1.0);
        item.setQty(1);
        em.persist(item);
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

    private Order order(long id) {
        return orders().stream().filter(o -> o.getId() == id).findFirst().orElseThrow();
    }

    private long idOf(double price) {
        return orders().stream().filter(o -> o.getPrice() == price).findFirst().orElseThrow().getId();
    }

    private <X> X find(Class<X> type, Object key) {
        EntityManager em = emf.createEntityManager();
        try {
            return em.find(type, key);
        } finally {
            em.close();
        }
    }

    // ------------------------------------------------------------------ 07-dml

    @Test
    void valueForms() {
        long id = idOf(50.0);
        short st = 7;
        String city = "Seoul";
        int cid = 2;
        // @start region="value-forms"
        ExecutableUpdate<Order> u = qb.update(Order.class)
                .set(o -> { value(o.getStatus(), st); value(o.getPrice(), o.getPrice() * 1.1); })
                .where(o -> o.getStatus() == 1);
        assertEquals("update Order e0 set e0.status = :p1, e0.price = (e0.price * 1.1) where e0.status = 1", u.toHql().hql());
        // an embedded column and a foreign key
        ExecutableUpdate<Order> embedded = qb.update(Order.class)
                .set(o -> value(o.getAddress().getCity(), city)).where(o -> o.getId() == id);
        assertEquals("update Order e0 set e0.address.city = :p1 where e0.id = :p2", embedded.toHql().hql());
        ExecutableUpdate<Order> fk = qb.update(Order.class)
                .set(o -> value(o.getCustomer().getId(), cid)).where(o -> o.getId() == id);
        assertEquals("update Order e0 set e0.customer.id = :p1 where e0.id = :p2", fk.toHql().hql());
        // @end
        assertEquals(2, u.execute());
        assertEquals(1, embedded.execute());
        assertEquals(1, fk.execute());
        Order changed = order(id);
        assertEquals(7, changed.getStatus());
        assertEquals(55.0, changed.getPrice(), 1e-9);
        assertEquals("Seoul", changed.getAddress().getCity());
        assertEquals(2, changed.getCustomer().getId());
    }

    @Test
    void readsOldValue() {
        // @start region="old-value"
        // price is assigned first and read again: rejected (MariaDB would read the new value)
        LambdaQLException e = assertThrows(LambdaQLException.class,
                () -> qb.update(Order.class).set(o -> { o.setPrice(o.getPrice() * 2); o.setTotal(o.getPrice()); }));
        assertEquals("LQL-3042", e.code());
        // read first, assign later: allowed, total gets the old price
        ExecutableUpdate<Order> u = qb.update(Order.class)
                .set(o -> { o.setTotal(o.getPrice()); o.setPrice(0.0); })
                .where(o -> o.getStatus() == 3);
        assertEquals("update Order e0 set e0.total = e0.price, e0.price = 0.0 where e0.status = 3", u.toHql().hql());
        // @end
        assertEquals(1, u.execute());
        Order o = orders().stream().filter(x -> x.getStatus() == 3).findFirst().orElseThrow();
        assertEquals(120.0, o.getTotal(), 1e-9);
        assertEquals(0.0, o.getPrice(), 1e-9);
    }

    // ------------------------------------------------------------------ 07-2 update

    @Test
    void updateValues() {
        long id = idOf(50.0);
        LocalDateTime now = LocalDateTime.of(2026, 3, 1, 12, 0);
        // @start region="update-values"
        ExecutableUpdate<Order> u = qb.update(Order.class)
                .set(o -> { o.setDescription(null); o.setUpdateAt(now); o.setKind(Order.Kind.RUSH); o.setActive(false); })
                .where(o -> o.getId() == id);
        assertEquals("update Order e0 set e0.description = null, e0.updateAt = :p1,"
                + " e0.kind = org.lambdaql.query.Order$Kind.RUSH, e0.active = false where e0.id = :p2", u.toHql().hql());
        int changed = u.execute();
        // @end
        assertEquals(1, changed);
        Order o = order(id);
        assertNull(o.getDescription());
        assertEquals(now, o.getUpdateAt());
        assertEquals(Order.Kind.RUSH, o.getKind());
        assertEquals(false, o.isActive());
    }

    @Test
    void updateAdd() {
        // @start region="update-add"
        ExecutableUpdate<Order> plusOne = qb.update(Order.class)
                .set(o -> o.setCnt(o.getCnt() + 1))
                .where(o -> o.getStatus() == 1);
        assertEquals("update Order e0 set e0.cnt = (e0.cnt + 1) where e0.status = 1", plusOne.toHql().hql());
        // a default for NULL: Objects.requireNonNullElse becomes coalesce
        ExecutableUpdate<Order> orZero = qb.update(Order.class)
                .set(o -> o.setTotal(Objects.requireNonNullElse(o.getTotal(), 0.0) + 1))
                .all();
        assertEquals("update Order e0 set e0.total = (coalesce(e0.total, 0.0) + 1.0)", orZero.toHql().hql());
        // @end
        assertEquals(2, plusOne.execute());
        assertEquals(4, orZero.execute());
        List<Order> after = orders();
        assertEquals(2, after.get(0).getCnt());          // 1 + 1
        assertNull(after.get(1).getCnt());               // NULL + 1 is NULL
        assertEquals(List.of(1.0, 11.0, 1.0, 6.0), after.stream().map(Order::getTotal).toList());
    }

    @Test
    void updateEmbedded() {
        long id = idOf(50.0);
        String city = "Busan";
        Address addr = new Address("Seoul", "04524");
        // @start region="update-embedded"
        ExecutableUpdate<Order> oneColumn = qb.update(Order.class)
                .set(o -> o.getAddress().setCity(city)).where(o -> o.getId() == id);
        assertEquals("update Order e0 set e0.address.city = :p1 where e0.id = :p2", oneColumn.toHql().hql());
        // the whole value: every column of the embedded value (city, zip)
        ExecutableUpdate<Order> whole = qb.update(Order.class)
                .set(o -> o.setAddress(addr)).where(o -> o.getId() == id);
        assertEquals("update Order e0 set e0.address = :p1 where e0.id = :p2", whole.toHql().hql());
        // @end
        assertEquals(1, oneColumn.execute());
        assertEquals("Busan", order(id).getAddress().getCity());
        assertEquals(1, whole.execute());
        assertEquals("04524", order(id).getAddress().getZip());
        assertEquals("Seoul", order(id).getAddress().getCity());
    }

    @Test
    void updateForeignKey() {
        long id = idOf(50.0);
        long other = idOf(150.0);
        int cid = 2;
        Customer customer = kim;
        // @start region="update-fk"
        ExecutableUpdate<Order> byId = qb.update(Order.class)
                .set(o -> o.getCustomer().setId(cid)).where(o -> o.getId() == id);
        assertEquals("update Order e0 set e0.customer.id = :p1 where e0.id = :p2", byId.toHql().hql());
        ExecutableUpdate<Order> none = qb.update(Order.class)
                .set(o -> o.setCustomer(null)).where(o -> o.getId() == id);
        assertEquals("update Order e0 set e0.customer = null where e0.id = :p1", none.toHql().hql());
        // a captured entity: its @Id is bound
        ExecutableUpdate<Order> entity = qb.update(Order.class)
                .set(o -> o.setCustomer(customer)).where(o -> o.getId() == other);
        assertEquals("update Order e0 set e0.customer = :p1 where e0.id = :p2", entity.toHql().hql());
        // @end
        assertEquals(1, byId.execute());
        assertEquals(2, order(id).getCustomer().getId());
        assertEquals(1, none.execute());
        assertNull(order(id).getCustomer());
        assertEquals(1, entity.execute());
        assertEquals(1, order(other).getCustomer().getId());
    }

    @Test
    void updateWhere() {
        // @start region="update-where"
        // a path in the condition: a scalar subquery
        ExecutableUpdate<Order> byGrade = qb.update(Order.class)
                .set(o -> o.setStatus((short) 0))
                .where(o -> o.getCustomer().getGrade() > 3);
        assertEquals("update Order e0 set e0.status = 0 where (select s1.grade from Customer s1 where s1.id = fk(e0.customer)) > 3"
                + " and e0.customer.id is not null", byGrade.toHql().hql());
        // a subquery with sub
        ExecutableUpdate<Order> noItems = qb.update(Order.class)
                .set(o -> o.setStatus((short) 9))
                .where((o, sub) -> !sub.from(Item.class).where(i -> i.getOrder().getId() == o.getId()).exists());
        assertEquals("update Order e0 set e0.status = 9 where not exists (select 1 from Item s1 where s1.order.id = e0.id)",
                noItems.toHql().hql());
        // @end
        List<Long> kimOrders = orders().stream()
                .filter(o -> o.getCustomer() != null && o.getCustomer().getId() == 1).map(Order::getId).toList();
        assertEquals(kimOrders.size(), byGrade.execute());
        assertEquals(3, noItems.execute());                  // every order but the one with an item
        for (Order o : orders()) {
            if (o.getPrice() == 50.0) assertEquals(0, o.getStatus());
            else assertEquals(9, o.getStatus());
        }
    }

    @Test
    void optimisticLock() {
        long id = 1L;
        Long v = find(VOrder.class, 1L).getVersion();
        // @start region="update-version"
        // put the version in the condition and check the result
        ExecutableUpdate<VOrder> u = qb.update(VOrder.class)
                .set(o -> o.setStatus(8))
                .where(o -> o.getId() == id && o.getVersion() == v);
        assertEquals("update versioned VOrder e0 set e0.status = 8 where e0.id = :p1 and e0.version = :p2", u.toHql().hql());
        int n = u.execute();
        if (n == 0) throw new OptimisticLockException();   // checking the result is your job
        // @end
        assertEquals(1, n);
        assertEquals(0, u.execute());                      // the version went up: the old version matches no row
        assertEquals(v + 1, find(VOrder.class, 1L).getVersion());
    }

    @Test
    void updateAll() {
        // @start region="update-all"
        // without where, all() is required; without both, there is no execute() (a compile error)
        ExecutableUpdate<Order> u = qb.update(Order.class).set(o -> o.setStatus((short) 0)).all();
        assertEquals("update Order e0 set e0.status = 0", u.toHql().hql());
        int changed = u.execute();
        // @end
        assertEquals(4, changed);
    }

    @Test
    void updateOne() {
        long id = idOf(50.0);
        // @start region="update-one"
        int one = qb.update(Order.class)
                .set(o -> o.setDescription("checked"))
                .where(o -> o.getId() == id)
                .executeOne();                             // 0 or 1
        // two or more rows match: nothing changes
        assertThrows(NonUniqueResultException.class, () -> qb.update(Order.class)
                .set(o -> o.setDescription("checked"))
                .where(o -> o.getStatus() == 1)
                .executeOne());
        // @end
        assertEquals(1, one);
        assertEquals(1, orders().stream().filter(o -> "checked".equals(o.getDescription())).count());
    }

    // ------------------------------------------------------------------ 07-4 delete

    @Test
    void deleteAll() {
        qb.delete(Item.class).all().execute();
        // @start region="delete-all"
        ExecutableDelete<Order> d = qb.delete(Order.class).all();
        assertEquals("delete from Order e0", d.toHql().hql());
        int deleted = d.execute();
        // @end
        assertEquals(4, deleted);
    }

    @Test
    void deleteByIds() {
        EntityManager em = emf.createEntityManager();
        List<Long> all = em.createQuery("select i.id from Item i", Long.class).getResultList();
        em.close();
        // @start region="delete-in"
        List<Long> ids = all;                               // ids read before, for example from a request
        ExecutableDelete<Item> d = qb.delete(Item.class).where(i -> ids.contains(i.getId()));
        assertEquals("delete from Item e0 where e0.id in (:p1)", d.toHql().hql());
        int deleted = d.execute();
        // @end
        assertEquals(1, deleted);
    }

    // ------------------------------------------------------------------ 07-5 insert

    @Test
    void insertWithValue() {
        long id = 400L;
        // @start region="insert-value"
        ExecutableInsert<Archive> i = qb.insert(Archive.class)
                .values(a -> { value(a.getId(), id); value(a.getPrice(), 1.0); });
        assertEquals("insert into Archive (id, price) values (:p1, 1.0)", i.toHql().get(0).hql());
        int inserted = i.execute();
        // @end
        assertEquals(1, inserted);
        assertEquals(1.0, find(Archive.class, 400L).getPrice(), 1e-9);
    }

    @Test
    void insertSelectWithValue() {
        // @start region="insert-select-value"
        ExecutableInsert<Archive> i = qb.insert(Archive.class).from(Order.class).where(o -> o.getStatus() == 1)
                .select((a, o) -> { value(a.getId(), o.getId()); value(a.getCustomerName(), o.getCustomer().getName()); });
        assertEquals("insert into Archive (id, customerName) select e0.id, j1.name"
                + " from Order e0 left join e0.customer j1 where e0.status = 1", i.toHql().get(0).hql());
        int inserted = i.execute();
        // @end
        assertEquals(2, inserted);
        for (Order o : orders()) {
            if (o.getStatus() == 1) assertEquals(o.getCustomer().getName(), find(Archive.class, o.getId()).getCustomerName());
        }
    }

    // ------------------------------------------------------------------ 07-6 upsert

    @Test
    void upsertList() {
        // @start region="upsert-list"
        // one statement per row; execute() returns the sum
        List<Line> lines = List.of(new Line(501, 1.0), new Line(502, 2.0));
        int n = qb.upsert(Archive.class)
                .values(lines, (a, l) -> { a.setId(l.id()); a.setPrice(l.price()); })
                .execute();
        // @end
        assertEquals(2, n);
        // the same keys again: the rows are changed, not added
        List<Line> again = List.of(new Line(501, 10.0), new Line(502, 20.0));
        int m = qb.upsert(Archive.class).values(again, (a, l) -> { a.setId(l.id()); a.setPrice(l.price()); }).execute();
        assertTrue(m > 0);
        assertEquals(10.0, find(Archive.class, 501L).getPrice(), 1e-9);
        assertEquals(20.0, find(Archive.class, 502L).getPrice(), 1e-9);
        EntityManager em = emf.createEntityManager();
        assertEquals(2L, em.createQuery("select count(a) from Archive a", Long.class).getSingleResult());
        em.close();
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = "org.lambdaql.query")
    static class Context {
    }
}
