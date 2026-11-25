package org.lambdaql.query;

/**
 * A subquery with an entity join: {@code sub.from(Order.class).join(Item.class, (o, i) -> ...)} (ROADMAP D51).
 * The lambdas take both rows. lambdaQL reads the calls; the subquery is never run in Java.
 *
 * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java">SubqueryTest</a>):
 * {@snippet class="org.lambdaql.query.SubqueryTest" region="sub-join"}
 *
 * @param <X> the subquery entity
 * @param <Y> the joined entity
 */
public interface From2<X, Y> {

    /**
     * Adds a condition (joined with AND).
     *
     * @param condition the condition on both rows
     * @return this subquery
     */
    From2<X, Y> where(SelectJoinQuery.Where<X, Y> condition);

    /**
     * {@code exists (select 1 ...)}.
     *
     * @return a placeholder
     */
    boolean exists();

    /**
     * {@code (select count(*) ...)}.
     *
     * @return a placeholder
     */
    long count();

    /**
     * Selects one value per row, for {@code contains} or {@code first}.
     *
     * @param pick the value of both rows
     * @param <R>  the value type
     * @return the values
     */
    <R> Values<R> select(SelectJoinQuery.Select<X, Y, R> pick);

    /**
     * {@code (select max(...) ...)}.
     *
     * @param pick the value of both rows
     * @param <R>  the value type
     * @return a placeholder
     */
    <R extends Comparable<? super R>> R max(SelectJoinQuery.Select<X, Y, R> pick);

    /**
     * {@code (select min(...) ...)}.
     *
     * @param pick the value of both rows
     * @param <R>  the value type
     * @return a placeholder
     */
    <R extends Comparable<? super R>> R min(SelectJoinQuery.Select<X, Y, R> pick);

    /**
     * {@code (select avg(...) ...)}.
     *
     * @param pick the value of both rows
     * @return a placeholder
     */
    Double avg(SelectJoinQuery.Select<X, Y, ? extends Number> pick);

    /**
     * {@code (select sum(...) ...)}.
     *
     * @param pick the value of both rows
     * @param <N>  the number type
     * @return a placeholder
     */
    <N extends Number> N sum(SelectJoinQuery.Select<X, Y, N> pick);
}
