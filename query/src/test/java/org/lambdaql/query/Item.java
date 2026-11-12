package org.lambdaql.query;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Test entity: one line of an {@link Order} (table {@code items}).
 * <p>
 * The manual uses it for subqueries ({@code exists}, {@code not exists}, {@code sum}).
 * See {@link PlannedSqlTest}.
 * <p>
 * Example (planned HQL):
 * <pre>{@code
 * select o from Order o where exists (select 1 from Item i where i.order.id = o.id and i.qty > 10)
 * }</pre>
 */
@Entity
@Table(name = "items")
@Getter
@Setter
public class Item {

    /** The id. The database makes it. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The order of this line (foreign key {@code order_id}). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    /** The price of this line. Can be NULL. */
    private Double price;

    /** The quantity. Never NULL (primitive). */
    private int qty;

    /** Empty constructor for JPA. */
    public Item() {}
}
