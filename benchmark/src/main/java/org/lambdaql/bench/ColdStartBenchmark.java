package org.lambdaql.bench;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The first query in a new JVM (the filter query of {@link QueryBenchmark}): class loading, Hibernate's first
 * parse of the query, and for lambdaQL the first analysis of the lambda bytecode. Every method runs once in each of
 * 10 new JVMs; the JPA start-up (the EntityManagerFactory) is in the setup and not measured.
 */
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 0)
@Measurement(iterations = 1)
@Fork(10)
public class ColdStartBenchmark {

    @Benchmark
    public List<BenchOrder> first_jpql(BenchDatabase db) {
        EntityManager em = db.emf.createEntityManager();
        try {
            return em.createQuery("select o from BenchOrder o where o.status = :s and o.price > :p", BenchOrder.class)
                    .setParameter("s", QueryBenchmark.STATUS).setParameter("p", QueryBenchmark.MIN_PRICE).getResultList();
        } finally {
            em.close();
        }
    }

    @Benchmark
    public List<BenchOrder> first_criteria(BenchDatabase db) {
        EntityManager em = db.emf.createEntityManager();
        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<BenchOrder> q = cb.createQuery(BenchOrder.class);
            Root<BenchOrder> o = q.from(BenchOrder.class);
            q.select(o).where(cb.equal(o.get("status"), QueryBenchmark.STATUS), cb.gt(o.get("price"), QueryBenchmark.MIN_PRICE));
            return em.createQuery(q).getResultList();
        } finally {
            em.close();
        }
    }

    @Benchmark
    public List<BenchOrder> first_lambdaql(BenchDatabase db) {
        int status = QueryBenchmark.STATUS;
        double minPrice = QueryBenchmark.MIN_PRICE;
        return db.qb.from(BenchOrder.class).where(o -> o.getStatus() == status && o.getPrice() > minPrice).toList();
    }

    @Benchmark
    public List<BenchOrder> first_jdbc(BenchDatabase db) throws java.sql.SQLException {
        try (var c = db.dataSource.getConnection();
             var ps = c.prepareStatement("select id, status, price, note, customer_id from SQ_bench_orders where status = ? and price > ?")) {
            ps.setInt(1, QueryBenchmark.STATUS);
            ps.setDouble(2, QueryBenchmark.MIN_PRICE);
            return QueryBenchmark.orders(ps);
        }
    }
}
