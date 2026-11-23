package org.lambdaql.bench;

import org.lambdaql.spring.LambdaQlExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * The Spring Data JPA repository of the benchmark orders: derived query methods, {@code @Query} methods, the
 * {@code Specification} executor, and the lambdaQL repository executor ({@link LambdaQlExecutor}).
 */
public interface BenchOrderRepository extends JpaRepository<BenchOrder, Long>, JpaSpecificationExecutor<BenchOrder>,
        LambdaQlExecutor<BenchOrder> {

    /**
     * Derived query method of the filter query.
     *
     * @param status the status
     * @param price  the lower price limit (not included)
     * @return the orders
     */
    List<BenchOrder> findByStatusAndPriceGreaterThan(int status, double price);

    /**
     * Derived query method of the join query.
     *
     * @param name the customer name
     * @return the orders of that customer
     */
    List<BenchOrder> findByCustomerName(String name);

    /**
     * {@code @Query} of the filter query.
     *
     * @param status the status
     * @param price  the lower price limit (not included)
     * @return the orders
     */
    @Query("select o from BenchOrder o where o.status = :s and o.price > :p")
    List<BenchOrder> selectQuery(@Param("s") int status, @Param("p") double price);

    /**
     * {@code @Query} of the join query.
     *
     * @param name the customer name
     * @return the orders of that customer
     */
    @Query("select o from BenchOrder o join o.customer c where c.name = :n")
    List<BenchOrder> joinQuery(@Param("n") String name);

    /**
     * {@code @Query} of the group-by query.
     *
     * @param total the lower total limit (not included)
     * @return one row per customer
     */
    @Query("select new org.lambdaql.bench.CustomerTotal(o.customer.id, count(*), sum(o.price))"
            + " from BenchOrder o group by o.customer.id having sum(o.price) > :t")
    List<CustomerTotal> groupQuery(@Param("t") double total);

    /**
     * {@code @Modifying @Query} of the update (needs a transaction).
     *
     * @param note   the new note
     * @param status the status of the orders to change
     * @return the number of changed rows
     */
    @Modifying
    @Query("update BenchOrder o set o.note = :n where o.status = :s")
    int updateQuery(@Param("n") String note, @Param("s") int status);
}
