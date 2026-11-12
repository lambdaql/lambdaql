package org.lambdaql.query;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Test entity: an order with a {@code @Version} field (table {@code v_orders}).
 * <p>
 * The manual uses it for {@code update versioned} (optimistic locking). See {@link PlannedSqlTest}.
 * <p>
 * Example (planned HQL):
 * <pre>{@code
 * update versioned VOrder o set o.status = 8 where o.id = :p1 and o.version = :p2
 * }</pre>
 */
@Entity
@Table(name = "v_orders")
@Getter
@Setter
public class VOrder {

    /** The id. You set it yourself. */
    @Id
    private Long id;

    /** The status. Can be NULL. */
    private Integer status;

    /** The version. Hibernate adds 1 on each versioned update. */
    @Version
    private Long version;

    /** Empty constructor for JPA. */
    public VOrder() {}
}
