package org.lambdaql.bench;

/**
 * One row of the group-by benchmark.
 *
 * @param customerId the customer id
 * @param count      the number of orders
 * @param total      the sum of the prices
 */
public record CustomerTotal(Long customerId, Long count, Double total) {
}
