package org.lambdaql.query;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Test entity: a copy of an old order (table {@code archive}).
 * <p>
 * The manual uses it for {@code insert ... select}. See {@link PlannedSqlTest}.
 * <p>
 * Example (planned HQL):
 * <pre>{@code
 * insert into Archive (id, price, customerName)
 * select o.id, o.price, c.name from Order o left join o.customer c where o.status = 9
 * }</pre>
 */
@Entity
@Table(name = "archive")
@Getter
@Setter
public class Archive {

    /** The id of the old order. You set it yourself. */
    @Id
    private Long id;

    /** The price. Can be NULL. */
    private Double price;

    /** The customer name. Can be NULL. */
    private String customerName;

    /** Empty constructor for JPA. */
    public Archive() {}
}
