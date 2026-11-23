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
        SpringDataState sd = new SpringDataState();
        sd.open(db);
        try {
            QueryBenchmark b = new QueryBenchmark();
            SpringDataBenchmark s = new SpringDataBenchmark();
            Function<List<BenchOrder>, List<String>> orders = l -> l.stream()
                    .map(o -> o.getId() + ":" + o.getStatus() + ":" + o.getPrice() + ":" + o.getNote()).sorted().toList();
            same("select", orders.apply(b.select_jdbc(db)), orders.apply(b.select_jpql(db)),
                    orders.apply(b.select_criteria(db)), orders.apply(b.select_lambdaql(db)));
            same("select (Spring Data)", orders.apply(b.select_jdbc(db)), orders.apply(s.select_springDerived(sd)),
                    orders.apply(s.select_springQuery(sd)), orders.apply(s.select_springSpec(sd)), orders.apply(s.select_springLambdaql(sd)));
            same("byId", orders.apply(b.byId_jdbc(db)), orders.apply(b.byId_jpql(db)),
                    orders.apply(b.byId_criteria(db)), orders.apply(b.byId_lambdaql(db)));
            same("byId (Spring Data)", orders.apply(b.byId_jdbc(db)), orders.apply(s.byId_springFindById(sd)),
                    orders.apply(s.byId_springLambdaql(sd)));
            same("join", orders.apply(b.join_jdbc(db)), orders.apply(b.join_jpql(db)),
                    orders.apply(b.join_criteria(db)), orders.apply(b.join_lambdaql(db)));
            same("join (Spring Data)", orders.apply(b.join_jdbc(db)), orders.apply(s.join_springDerived(sd)),
                    orders.apply(s.join_springQuery(sd)), orders.apply(s.join_springSpec(sd)), orders.apply(s.join_springLambdaql(sd)));
            Function<List<CustomerTotal>, List<String>> totals = l -> l.stream()
                    .sorted(Comparator.comparing(CustomerTotal::customerId))
                    .map(t -> t.customerId() + ":" + t.count() + ":" + Math.round(t.total())).toList();
            same("group", totals.apply(b.group_jdbc(db)), totals.apply(b.group_jpql(db)),
                    totals.apply(b.group_criteria(db)), totals.apply(b.group_lambdaql(db)));
            same("group (Spring Data)", totals.apply(b.group_jdbc(db)), totals.apply(s.group_springQuery(sd)),
                    totals.apply(s.group_springLambdaql(sd)));
            Function<List<BenchCustomer>, List<String>> customers = l -> l.stream().map(c -> c.getId() + ":" + c.getName()).sorted().toList();
            same("exists", customers.apply(b.exists_jdbc(db)), customers.apply(b.exists_jpql(db)),
                    customers.apply(b.exists_criteria(db)), customers.apply(b.exists_lambdaql(db)));
            same("exists (Spring Data)", customers.apply(b.exists_jdbc(db)), customers.apply(s.exists_springQuery(sd)),
                    customers.apply(s.exists_springLambdaql(sd)));
            same("update", List.of(b.update_jdbc(db)), List.of(b.update_jpql(db)), List.of(b.update_criteria(db)), List.of(b.update_lambdaql(db)));
            same("update (Spring Data)", List.of(b.update_jdbc(db)), List.of(s.update_springQuery(sd)), List.of(s.update_springLambdaql(sd)));
        } finally {
            sd.close();
            db.close();
        }
    }

    /**
     * Checks that every way gave the same result as the first one (plain JDBC).
     *
     * @param name    the query name
     * @param results the results, JDBC first
     */
    private static void same(String name, List<?>... results) {
        for (List<?> r : results) {
            if (!Objects.equals(results[0], r)) {
                throw new IllegalStateException(name + ": the results differ: " + java.util.Arrays.stream(results).map(List::size).toList());
            }
        }
        System.out.println(name + ": " + results[0] + (results[0].size() > 3 ? "" : "") + " -> " + results[0].size()
                + " rows, the same in all " + results.length + " ways");
    }
}
