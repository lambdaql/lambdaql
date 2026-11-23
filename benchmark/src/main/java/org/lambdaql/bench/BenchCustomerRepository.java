package org.lambdaql.bench;

import org.lambdaql.spring.LambdaQlExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** The Spring Data JPA repository of the benchmark customers (the exists query). */
public interface BenchCustomerRepository extends JpaRepository<BenchCustomer, Long>, LambdaQlExecutor<BenchCustomer> {

    /**
     * {@code @Query} of the exists query.
     *
     * @param price the lower price limit of an order (not included)
     * @return the customers with such an order
     */
    @Query("select c from BenchCustomer c where exists (select 1 from BenchOrder o where o.customer.id = c.id and o.price > :p)")
    List<BenchCustomer> existsQuery(@Param("p") double price);
}
