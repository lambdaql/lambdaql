package org.lambdaql.query;

/**
 * A subquery: {@code sub.from(Item.class).where(i -> ...).exists()}. A {@code where} lambda that uses the outer
 * entity makes a correlated subquery. The methods only describe the subquery; lambdaQL reads them from the bytecode.
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java">SubqueryTest</a>):
 * {@snippet class="org.lambdaql.query.SubqueryTest" region="exists"}
 *
 * @param <X> the entity type
 */
public interface From<X> {
    /**
     * Adds a condition (joined with AND). The lambda may use the outer entity (correlation).
     *
     * @param condition the condition
     * @return this subquery
     */
    From<X> where(SelectQuery.Where<X> condition);

    /**
     * Adds a condition with a nested subquery: {@code (i, sub2) -> ...}.
     *
     * @param condition the condition
     * @return this subquery
     */
    From<X> where(WhereSub<X> condition);

    /**
     * Sorts the subquery; only useful with {@code limit} (for example the latest row: {@code limit(1).select(...).first()}).
     *
     * @param spec the sort lambda
     * @return the sorted subquery
     */
    OrderedFrom<X> orderBy(SortBy<X> spec);

    /**
     * Selects one value per row, for {@code contains} (IN) or {@code first()}.
     *
     * @param pick the value
     * @param <R>  the value type
     * @return the values
     */
    <R> Values<R> select(Pick<X, R> pick);

    /**
     * {@code exists (select 1 ...)}.
     *
     * @return a placeholder
     */
    boolean exists();

    /**
     * Joins another entity inside the subquery (ROADMAP D51): {@code sub.from(Order.class).join(Item.class,
     * (o, i) -> i.getOrder().getId() == o.getId())}. The next steps take both rows.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java">SubqueryTest</a>):
     * {@snippet class="org.lambdaql.query.SubqueryTest" region="sub-join"}
     *
     * @param entity    the joined entity class
     * @param condition the join condition
     * @param <Y>       the joined entity type
     * @return the subquery with two rows
     */
    <Y> From2<X, Y> join(Class<Y> entity, SelectJoinQuery.JoinOn<X, Y> condition);

    /**
     * Left-joins another entity inside the subquery (ROADMAP D51).
     *
     * @param entity    the joined entity class
     * @param condition the join condition
     * @param <Y>       the joined entity type
     * @return the subquery with two rows
     */
    <Y> From2<X, Y> leftJoin(Class<Y> entity, SelectJoinQuery.JoinOn<X, Y> condition);

    /**
     * {@code (select count(*) ...)}.
     *
     * @return a placeholder
     */
    long count();

    /**
     * {@code (select max(...) ...)}.
     *
     * @param pick the value
     * @param <R>  the value type
     * @return a placeholder
     */
    <R extends Comparable<? super R>> R max(Pick<X, R> pick);

    /**
     * {@code (select min(...) ...)}.
     *
     * @param pick the value
     * @param <R>  the value type
     * @return a placeholder
     */
    <R extends Comparable<? super R>> R min(Pick<X, R> pick);

    /**
     * {@code (select avg(...) ...)}.
     *
     * @param pick the value
     * @return a placeholder
     */
    Double avg(Pick<X, ? extends Number> pick);

    /**
     * {@code (select sum(...) ...)}.
     *
     * @param pick the value
     * @param <N>  the number type
     * @return a placeholder
     */
    <N extends Number> N sum(Pick<X, N> pick);
}
