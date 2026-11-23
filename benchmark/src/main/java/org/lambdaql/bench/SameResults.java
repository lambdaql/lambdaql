package org.lambdaql.bench;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Runs every query of {@link QueryBenchmark} once in each of the four ways and checks that they give the same rows,
 * so the benchmark compares the same work. Run with {@code ./gradlew :benchmark:benchCheck [-Dlql.db=...]}.
 */
public final class SameResults {

    private SameResults() {
    }

    /**
     * Opens the database, compares the results and closes it.
     *
     * @param args not used
     * @throws Exception if a query fails or two ways give different rows
     */
    public static void main(String[] args) throws Exception {
        BenchDatabase db = new BenchDatabase();
        db.open();
        try {
            QueryBenchmark b = new QueryBenchmark();
            Function<List<BenchOrder>, List<String>> orders = l -> l.stream()
                    .map(o -> o.getId() + ":" + o.getStatus() + ":" + o.getPrice() + ":" + o.getNote()).sorted().toList();
            same("select", orders.apply(b.select_jdbc(db)), orders.apply(b.select_jpql(db)),
                    orders.apply(b.select_criteria(db)), orders.apply(b.select_lambdaql(db)));
            same("byId", orders.apply(b.byId_jdbc(db)), orders.apply(b.byId_jpql(db)),
                    orders.apply(b.byId_criteria(db)), orders.apply(b.byId_lambdaql(db)));
            same("join", orders.apply(b.join_jdbc(db)), orders.apply(b.join_jpql(db)),
                    orders.apply(b.join_criteria(db)), orders.apply(b.join_lambdaql(db)));
            Function<List<CustomerTotal>, List<String>> totals = l -> l.stream()
                    .sorted(Comparator.comparing(CustomerTotal::customerId))
                    .map(t -> t.customerId() + ":" + t.count() + ":" + Math.round(t.total())).toList();
            same("group", totals.apply(b.group_jdbc(db)), totals.apply(b.group_jpql(db)),
                    totals.apply(b.group_criteria(db)), totals.apply(b.group_lambdaql(db)));
            Function<List<BenchCustomer>, List<String>> customers = l -> l.stream().map(c -> c.getId() + ":" + c.getName()).sorted().toList();
            same("exists", customers.apply(b.exists_jdbc(db)), customers.apply(b.exists_jpql(db)),
                    customers.apply(b.exists_criteria(db)), customers.apply(b.exists_lambdaql(db)));
            same("update", List.of(b.update_jdbc(db)), List.of(b.update_jpql(db)), List.of(b.update_criteria(db)), List.of(b.update_lambdaql(db)));
        } finally {
            db.close();
        }
    }

    private static void same(String name, List<?> jdbc, List<?> jpql, List<?> criteria, List<?> lambdaql) {
        if (!Objects.equals(jdbc, jpql) || !Objects.equals(jdbc, criteria) || !Objects.equals(jdbc, lambdaql)) {
            throw new IllegalStateException(name + ": the results differ: jdbc " + jdbc.size() + ", jpql " + jpql.size()
                    + ", criteria " + criteria.size() + ", lambdaql " + lambdaql.size());
        }
        System.out.println(name + ": " + jdbc.size() + " rows, the same in all four ways");
    }
}
