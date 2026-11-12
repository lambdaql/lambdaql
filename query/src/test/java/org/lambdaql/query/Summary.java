package org.lambdaql.query;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Test entity: order totals per customer (table {@code summary}).
 * <p>
 * The manual uses it for {@code insert ... select} with {@code group by}. See {@link PlannedSqlTest}.
 * <p>
 * Example (planned HQL):
 * <pre>{@code
 * insert into Summary (customerId, total, cnt)
 * select o.customer.id, sum(o.price), count(*) from Order o group by o.customer.id
 * }</pre>
 */
@Entity
@Table(name = "summary")
@Getter
@Setter
public class Summary {

    /** The customer id. It has the same type as {@link Customer#getId()}. */
    @Id
    private Integer customerId;

    /** The sum of the order prices. Can be NULL. */
    private Double total;

    /** The number of orders. */
    private Long cnt;

    /** Empty constructor for JPA. */
    public Summary() {}
}
