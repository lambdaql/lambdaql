package org.lambdaql.query;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Test entity: an order whose id the database makes (IDENTITY, table {@code id_orders}).
 * <p>
 * The manual uses it for an insert without an id. See {@link PlannedSqlTest}.
 * <p>
 * Example (planned HQL):
 * <pre>{@code
 * insert into IdOrder (price, status) values (:p1, 1)
 * }</pre>
 */
@Entity
@Table(name = "id_orders")
@Getter
@Setter
public class IdOrder {

    /** The id. The database makes it. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The price. Can be NULL. */
    private Double price;

    /** The status. Can be NULL. */
    private Integer status;

    /** Empty constructor for JPA. */
    public IdOrder() {}
}
