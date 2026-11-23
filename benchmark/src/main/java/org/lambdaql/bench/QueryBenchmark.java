package org.lambdaql.bench;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.CriteriaUpdate;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.lambdaql.Agg;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The same queries written four ways, each run as one complete unit of work (build, run, read the rows):
 * <ul>
 *   <li>{@code jdbc}: plain SQL with a PreparedStatement; the rows are copied into objects by hand.</li>
 *   <li>{@code jpql}: a JPQL string ({@code EntityManager.createQuery}).</li>
 *   <li>{@code criteria}: the JPA Criteria API.</li>
 *   <li>{@code lambdaql}: lambdaQL ({@code QueryBuilder}).</li>
 * </ul>
 * JPQL, Criteria and lambdaQL open and close one EntityManager per operation (lambdaQL does it itself);
 * JDBC takes one connection from the same pool.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class QueryBenchmark {

    /** status = 1 and price > 900: 100 rows. */
    static final int STATUS = 1;
    static final double MIN_PRICE = 900;
    /** One customer: 100 rows. */
    static final String NAME = "c7";
    /** Group totals above this: about half of the 100 customers. */
    static final double MIN_TOTAL = 50_000;
    /** Customers with an order above this. */
    static final double EXISTS_PRICE = 990;
    static final long ID = 4242;

    // ------------------------------------------------------------------ 1. filter: two conditions, entities

    @Benchmark
    public List<BenchOrder> select_jdbc(BenchDatabase db) throws SQLException {
        try (Connection c = db.dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "select id, status, price, note, customer_id from SQ_bench_orders where status = ? and price > ?")) {
            ps.setInt(1, STATUS);
            ps.setDouble(2, MIN_PRICE);
            return orders(ps);
        }
    }

    @Benchmark
    public List<BenchOrder> select_jpql(BenchDatabase db) {
        EntityManager em = db.emf.createEntityManager();
        try {
            return em.createQuery("select o from BenchOrder o where o.status = :s and o.price > :p", BenchOrder.class)
                    .setParameter("s", STATUS).setParameter("p", MIN_PRICE).getResultList();
        } finally {
            em.close();
        }
    }

    @Benchmark
    public List<BenchOrder> select_criteria(BenchDatabase db) {
        EntityManager em = db.emf.createEntityManager();
        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<BenchOrder> q = cb.createQuery(BenchOrder.class);
            Root<BenchOrder> o = q.from(BenchOrder.class);
            q.select(o).where(cb.equal(o.get("status"), STATUS), cb.gt(o.get("price"), MIN_PRICE));
            return em.createQuery(q).getResultList();
        } finally {
            em.close();
        }
    }

    @Benchmark
    public List<BenchOrder> select_lambdaql(BenchDatabase db) {
        int status = STATUS;
        double minPrice = MIN_PRICE;
        return db.qb.from(BenchOrder.class).where(o -> o.getStatus() == status && o.getPrice() > minPrice).toList();
    }

    // ------------------------------------------------------------------ 2. one row by id

    @Benchmark
    public List<BenchOrder> byId_jdbc(BenchDatabase db) throws SQLException {
        try (Connection c = db.dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("select id, status, price, note, customer_id from SQ_bench_orders where id = ?")) {
            ps.setLong(1, ID);
            return orders(ps);
        }
    }

    @Benchmark
    public List<BenchOrder> byId_jpql(BenchDatabase db) {
        EntityManager em = db.emf.createEntityManager();
        try {
            return em.createQuery("select o from BenchOrder o where o.id = :id", BenchOrder.class).setParameter("id", ID).getResultList();
        } finally {
            em.close();
        }
    }

    @Benchmark
    public List<BenchOrder> byId_criteria(BenchDatabase db) {
        EntityManager em = db.emf.createEntityManager();
        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<BenchOrder> q = cb.createQuery(BenchOrder.class);
            Root<BenchOrder> o = q.from(BenchOrder.class);
            q.select(o).where(cb.equal(o.get("id"), ID));
            return em.createQuery(q).getResultList();
        } finally {
            em.close();
        }
    }

    @Benchmark
    public List<BenchOrder> byId_lambdaql(BenchDatabase db) {
        long id = ID;
        return db.qb.from(BenchOrder.class).where(o -> o.getId() == id).toList();
    }

    // ------------------------------------------------------------------ 3. association path (join)

    @Benchmark
    public List<BenchOrder> join_jdbc(BenchDatabase db) throws SQLException {
        try (Connection c = db.dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("select o.id, o.status, o.price, o.note, o.customer_id from SQ_bench_orders o"
                     + " join SQ_bench_customers c on c.id = o.customer_id where c.name = ?")) {
            ps.setString(1, NAME);
            return orders(ps);
        }
    }

    @Benchmark
    public List<BenchOrder> join_jpql(BenchDatabase db) {
        EntityManager em = db.emf.createEntityManager();
        try {
            return em.createQuery("select o from BenchOrder o join o.customer c where c.name = :n", BenchOrder.class)
                    .setParameter("n", NAME).getResultList();
        } finally {
            em.close();
        }
    }

    @Benchmark
    public List<BenchOrder> join_criteria(BenchDatabase db) {
        EntityManager em = db.emf.createEntityManager();
        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<BenchOrder> q = cb.createQuery(BenchOrder.class);
            Root<BenchOrder> o = q.from(BenchOrder.class);
            Join<BenchOrder, BenchCustomer> c = o.join("customer");
            q.select(o).where(cb.equal(c.get("name"), NAME));
            return em.createQuery(q).getResultList();
        } finally {
            em.close();
        }
    }

    @Benchmark
    public List<BenchOrder> join_lambdaql(BenchDatabase db) {
        String name = NAME;
        return db.qb.from(BenchOrder.class).where(o -> o.getCustomer().getName().equals(name)).toList();
    }

    // ------------------------------------------------------------------ 4. group by with having, into a record

    @Benchmark
    public List<CustomerTotal> group_jdbc(BenchDatabase db) throws SQLException {
        try (Connection c = db.dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("select customer_id, count(*), sum(price) from SQ_bench_orders"
                     + " group by customer_id having sum(price) > ?")) {
            ps.setDouble(1, MIN_TOTAL);
            List<CustomerTotal> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(new CustomerTotal(rs.getLong(1), rs.getLong(2), rs.getDouble(3)));
            }
            return out;
        }
    }

    @Benchmark
    public List<CustomerTotal> group_jpql(BenchDatabase db) {
        EntityManager em = db.emf.createEntityManager();
        try {
            return em.createQuery("select new org.lambdaql.bench.CustomerTotal(o.customer.id, count(*), sum(o.price))"
                            + " from BenchOrder o group by o.customer.id having sum(o.price) > :t", CustomerTotal.class)
                    .setParameter("t", MIN_TOTAL).getResultList();
        } finally {
            em.close();
        }
    }

    @Benchmark
    public List<CustomerTotal> group_criteria(BenchDatabase db) {
        EntityManager em = db.emf.createEntityManager();
        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<CustomerTotal> q = cb.createQuery(CustomerTotal.class);
            Root<BenchOrder> o = q.from(BenchOrder.class);
            var customerId = o.get("customer").<Long>get("id");
            var total = cb.sum(o.<Double>get("price"));
            q.select(cb.construct(CustomerTotal.class, customerId, cb.count(o), total))
                    .groupBy(customerId).having(cb.gt(total, MIN_TOTAL));
            return em.createQuery(q).getResultList();
        } finally {
            em.close();
        }
    }

    @Benchmark
    public List<CustomerTotal> group_lambdaql(BenchDatabase db) {
        double minTotal = MIN_TOTAL;
        return db.qb.from(BenchOrder.class)
                .groupBy(o -> o.getCustomer().getId())
                .having(o -> Agg.sum(o.getPrice()) > minTotal)
                .select(o -> new CustomerTotal(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice())))
                .toList();
    }

    // ------------------------------------------------------------------ 5. correlated exists subquery

    @Benchmark
    public List<BenchCustomer> exists_jdbc(BenchDatabase db) throws SQLException {
        try (Connection c = db.dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("select c.id, c.name from SQ_bench_customers c where exists"
                     + " (select 1 from SQ_bench_orders o where o.customer_id = c.id and o.price > ?)")) {
            ps.setDouble(1, EXISTS_PRICE);
            List<BenchCustomer> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(new BenchCustomer(rs.getLong(1), rs.getString(2)));
            }
            return out;
        }
    }

    @Benchmark
    public List<BenchCustomer> exists_jpql(BenchDatabase db) {
        EntityManager em = db.emf.createEntityManager();
        try {
            return em.createQuery("select c from BenchCustomer c where exists"
                            + " (select 1 from BenchOrder o where o.customer.id = c.id and o.price > :p)", BenchCustomer.class)
                    .setParameter("p", EXISTS_PRICE).getResultList();
        } finally {
            em.close();
        }
    }

    @Benchmark
    public List<BenchCustomer> exists_criteria(BenchDatabase db) {
        EntityManager em = db.emf.createEntityManager();
        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<BenchCustomer> q = cb.createQuery(BenchCustomer.class);
            Root<BenchCustomer> c = q.from(BenchCustomer.class);
            Subquery<Integer> sub = q.subquery(Integer.class);
            Root<BenchOrder> o = sub.from(BenchOrder.class);
            sub.select(cb.literal(1)).where(cb.equal(o.get("customer").get("id"), c.get("id")), cb.gt(o.get("price"), EXISTS_PRICE));
            q.select(c).where(cb.exists(sub));
            return em.createQuery(q).getResultList();
        } finally {
            em.close();
        }
    }

    @Benchmark
    public List<BenchCustomer> exists_lambdaql(BenchDatabase db) {
        double price = EXISTS_PRICE;
        return db.qb.from(BenchCustomer.class)
                .where((c, sub) -> sub.from(BenchOrder.class)
                        .where(o -> o.getCustomer().getId().equals(c.getId()) && o.getPrice() > price).exists())
                .toList();
    }

    // ------------------------------------------------------------------ 6. bulk update (1,000 rows, committed)

    @Benchmark
    public int update_jdbc(BenchDatabase db) throws SQLException {
        try (Connection c = db.dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement("update SQ_bench_orders set note = ? where status = ?")) {
                ps.setString(1, "u");
                ps.setInt(2, STATUS);
                int n = ps.executeUpdate();
                c.commit();
                return n;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    @Benchmark
    public int update_jpql(BenchDatabase db) {
        EntityManager em = db.emf.createEntityManager();
        try {
            em.getTransaction().begin();
            int n = em.createQuery("update BenchOrder o set o.note = :n where o.status = :s")
                    .setParameter("n", "u").setParameter("s", STATUS).executeUpdate();
            em.getTransaction().commit();
            return n;
        } finally {
            em.close();
        }
    }

    @Benchmark
    public int update_criteria(BenchDatabase db) {
        EntityManager em = db.emf.createEntityManager();
        try {
            em.getTransaction().begin();
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaUpdate<BenchOrder> u = cb.createCriteriaUpdate(BenchOrder.class);
            Root<BenchOrder> o = u.from(BenchOrder.class);
            u.set(o.<String>get("note"), "u").where(cb.equal(o.get("status"), STATUS));
            int n = em.createQuery(u).executeUpdate();
            em.getTransaction().commit();
            return n;
        } finally {
            em.close();
        }
    }

    @Benchmark
    public int update_lambdaql(BenchDatabase db) {
        int status = STATUS;
        String note = "u";
        return db.qb.update(BenchOrder.class).set(o -> o.setNote(note)).where(o -> o.getStatus() == status).execute();
    }

    // ------------------------------------------------------------------ helpers

    static List<BenchOrder> orders(PreparedStatement ps) throws SQLException {
        List<BenchOrder> out = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                // the customer is not read (like the lazy reference of JPA); only its id is in the row
                rs.getLong(5);
                out.add(new BenchOrder(rs.getLong(1), rs.getInt(2), rs.getDouble(3), rs.getString(4), null));
            }
        }
        return out;
    }
}
