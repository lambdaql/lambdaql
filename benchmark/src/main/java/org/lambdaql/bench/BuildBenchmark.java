package org.lambdaql.bench;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.lambdaql.Agg;
import org.lambdaql.engine.render.RenderedQuery;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * Only the building of the query, without the database round trip (the filter query of {@link QueryBenchmark}):
 * <ul>
 *   <li>{@code jpql}: {@code createQuery} with a JPQL string and the parameters (Hibernate finds the parsed form in
 *       its query plan cache).</li>
 *   <li>{@code criteria}: the Criteria API objects and {@code createQuery}.</li>
 *   <li>{@code lambdaql}: {@code toHql()}: the lambda analysis comes from the cache, then lambdaQL writes the HQL.</li>
 *   <li>{@code lambdaqlQuery}: {@code toHql()} and then {@code createQuery} with that HQL, which is what lambdaQL
 *       does before it runs a query.</li>
 *   <li>{@code lambdaqlGroup}: {@code toHql()} of the group-by query (three lambdas).</li>
 * </ul>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class BuildBenchmark {

    private EntityManager em;

    /**
     * Opens one EntityManager for all building operations.
     *
     * @param db the database
     */
    @Setup(Level.Trial)
    public void open(BenchDatabase db) {
        em = db.emf.createEntityManager();
    }

    /** Closes the EntityManager. */
    @TearDown(Level.Trial)
    public void close() {
        em.close();
    }

    @Benchmark
    public TypedQuery<BenchOrder> build_jpql() {
        return em.createQuery("select o from BenchOrder o where o.status = :s and o.price > :p", BenchOrder.class)
                .setParameter("s", QueryBenchmark.STATUS).setParameter("p", QueryBenchmark.MIN_PRICE);
    }

    @Benchmark
    public TypedQuery<BenchOrder> build_criteria() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<BenchOrder> q = cb.createQuery(BenchOrder.class);
        Root<BenchOrder> o = q.from(BenchOrder.class);
        q.select(o).where(cb.equal(o.get("status"), QueryBenchmark.STATUS), cb.gt(o.get("price"), QueryBenchmark.MIN_PRICE));
        return em.createQuery(q);
    }

    @Benchmark
    public RenderedQuery build_lambdaql(BenchDatabase db) {
        int status = QueryBenchmark.STATUS;
        double minPrice = QueryBenchmark.MIN_PRICE;
        return db.qb.from(BenchOrder.class).where(o -> o.getStatus() == status && o.getPrice() > minPrice).toHql();
    }

    /**
     * The group-by query of {@link QueryBenchmark#group_lambdaql}: three lambdas (groupBy, having, select) are read
     * and the HQL is written, without running it.
     */
    @Benchmark
    public RenderedQuery build_lambdaqlGroup(BenchDatabase db) {
        double minTotal = QueryBenchmark.MIN_TOTAL;
        return db.qb.from(BenchOrder.class)
                .groupBy(o -> o.getCustomer().getId())
                .having(o -> Agg.sum(o.getPrice()) > minTotal)
                .select(o -> new CustomerTotal(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice())))
                .toHql();
    }

    @Benchmark
    public TypedQuery<BenchOrder> build_lambdaqlQuery(BenchDatabase db) {
        int status = QueryBenchmark.STATUS;
        double minPrice = QueryBenchmark.MIN_PRICE;
        RenderedQuery r = db.qb.from(BenchOrder.class).where(o -> o.getStatus() == status && o.getPrice() > minPrice).toHql();
        TypedQuery<BenchOrder> q = em.createQuery(r.hql(), BenchOrder.class);
        r.parameters().forEach(q::setParameter);
        return q;
    }
}
