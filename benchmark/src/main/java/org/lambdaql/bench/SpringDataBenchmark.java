package org.lambdaql.bench;

import jakarta.persistence.criteria.Join;
import org.lambdaql.Agg;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The queries of {@link QueryBenchmark} through Spring Data JPA repositories:
 * <ul>
 *   <li>{@code springDerived}: a derived query method ({@code findByStatusAndPriceGreaterThan}).</li>
 *   <li>{@code springQuery}: an {@code @Query} method with JPQL.</li>
 *   <li>{@code springSpec}: {@code findAll(Specification)} (Criteria API in a lambda).</li>
 *   <li>{@code springFindById}: {@code findById} ({@code EntityManager.find}, not a query).</li>
 *   <li>{@code springLambdaql}: the lambdaQL repository executor ({@code LambdaQlExecutor}).</li>
 * </ul>
 * Only the ways that can write a query are measured for it (a {@code Specification} has a condition only, a derived
 * method cannot group). Statements that change data run in a transaction ({@code TransactionTemplate}).
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class SpringDataBenchmark {

    // ------------------------------------------------------------------ 1. filter

    @Benchmark
    public List<BenchOrder> select_springDerived(SpringDataState s) {
        return s.orders.findByStatusAndPriceGreaterThan(QueryBenchmark.STATUS, QueryBenchmark.MIN_PRICE);
    }

    @Benchmark
    public List<BenchOrder> select_springQuery(SpringDataState s) {
        return s.orders.selectQuery(QueryBenchmark.STATUS, QueryBenchmark.MIN_PRICE);
    }

    @Benchmark
    public List<BenchOrder> select_springSpec(SpringDataState s) {
        Specification<BenchOrder> spec = (o, q, cb) -> cb.and(cb.equal(o.get("status"), QueryBenchmark.STATUS),
                cb.gt(o.get("price"), QueryBenchmark.MIN_PRICE));
        return s.orders.findAll(spec);
    }

    @Benchmark
    public List<BenchOrder> select_springLambdaql(SpringDataState s) {
        int status = QueryBenchmark.STATUS;
        double minPrice = QueryBenchmark.MIN_PRICE;
        return s.orders.findAll(o -> o.getStatus() == status && o.getPrice() > minPrice);
    }

    // ------------------------------------------------------------------ 2. one row by id

    @Benchmark
    public List<BenchOrder> byId_springFindById(SpringDataState s) {
        return s.orders.findById(QueryBenchmark.ID).stream().toList();
    }

    @Benchmark
    public List<BenchOrder> byId_springLambdaql(SpringDataState s) {
        long id = QueryBenchmark.ID;
        return s.orders.findOne(o -> o.getId() == id).stream().toList();
    }

    // ------------------------------------------------------------------ 3. association path (join)

    @Benchmark
    public List<BenchOrder> join_springDerived(SpringDataState s) {
        return s.orders.findByCustomerName(QueryBenchmark.NAME);
    }

    @Benchmark
    public List<BenchOrder> join_springQuery(SpringDataState s) {
        return s.orders.joinQuery(QueryBenchmark.NAME);
    }

    @Benchmark
    public List<BenchOrder> join_springSpec(SpringDataState s) {
        Specification<BenchOrder> spec = (o, q, cb) -> {
            Join<BenchOrder, BenchCustomer> c = o.join("customer");
            return cb.equal(c.get("name"), QueryBenchmark.NAME);
        };
        return s.orders.findAll(spec);
    }

    @Benchmark
    public List<BenchOrder> join_springLambdaql(SpringDataState s) {
        String name = QueryBenchmark.NAME;
        return s.orders.findAll(o -> o.getCustomer().getName().equals(name));
    }

    // ------------------------------------------------------------------ 4. group by

    @Benchmark
    public List<CustomerTotal> group_springQuery(SpringDataState s) {
        return s.orders.groupQuery(QueryBenchmark.MIN_TOTAL);
    }

    @Benchmark
    public List<CustomerTotal> group_springLambdaql(SpringDataState s) {
        double minTotal = QueryBenchmark.MIN_TOTAL;
        return s.orders.query()
                .groupBy(o -> o.getCustomer().getId())
                .having(o -> Agg.sum(o.getPrice()) > minTotal)
                .select(o -> new CustomerTotal(o.getCustomer().getId(), Agg.count(), Agg.sum(o.getPrice())))
                .toList();
    }

    // ------------------------------------------------------------------ 5. exists

    @Benchmark
    public List<BenchCustomer> exists_springQuery(SpringDataState s) {
        return s.customers.existsQuery(QueryBenchmark.EXISTS_PRICE);
    }

    @Benchmark
    public List<BenchCustomer> exists_springLambdaql(SpringDataState s) {
        double price = QueryBenchmark.EXISTS_PRICE;
        return s.customers.query()
                .where((c, sub) -> sub.from(BenchOrder.class)
                        .where(o -> o.getCustomer().getId().equals(c.getId()) && o.getPrice() > price).exists())
                .toList();
    }

    // ------------------------------------------------------------------ 6. bulk update (in a transaction)

    @Benchmark
    public Integer update_springQuery(SpringDataState s) {
        return s.transaction.execute(t -> s.orders.updateQuery("u", QueryBenchmark.STATUS));
    }

    @Benchmark
    public Integer update_springLambdaql(SpringDataState s) {
        int status = QueryBenchmark.STATUS;
        String note = "u";
        return s.transaction.execute(t -> s.orders.updateAll(o -> o.getStatus() == status, o -> o.setNote(note)));
    }
}
