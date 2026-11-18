package org.lambdaql.query;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A test entity with a required association (optional = false) and a @Lob column (MappingRulesTest). */
@Entity
@Table(name = "order_lines")
public class OrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Every line belongs to an order: a sort or select path through it can use an inner join. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /** A large text. */
    @Lob
    private String memo;

    /** For JPA. */
    public OrderLine() {
    }

    /**
     * Makes a line.
     *
     * @param order the order
     * @param memo  the memo
     */
    public OrderLine(Order order, String memo) {
        this.order = order;
        this.memo = memo;
    }

    /** @return the id */
    public Long getId() {
        return id;
    }

    /** @return the order */
    public Order getOrder() {
        return order;
    }

    /** @return the memo */
    public String getMemo() {
        return memo;
    }
}
