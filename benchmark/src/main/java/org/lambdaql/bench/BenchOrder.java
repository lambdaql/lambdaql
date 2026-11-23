package org.lambdaql.bench;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.io.Serializable;

/** An order of the benchmark data (10,000 rows). */
@Entity
@Table(name = "SQ_bench_orders")
public class BenchOrder implements Serializable {

    @Id
    private Long id;

    /** 0 to 9. */
    private int status;

    /** 0 to 999. */
    private double price;

    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private BenchCustomer customer;

    /** For JPA. */
    protected BenchOrder() {
    }

    /**
     * Makes an order.
     *
     * @param id       the id
     * @param status   the status, 0 to 9
     * @param price    the price
     * @param note     a short text
     * @param customer the customer, or null
     */
    public BenchOrder(Long id, int status, double price, String note, BenchCustomer customer) {
        this.id = id;
        this.status = status;
        this.price = price;
        this.note = note;
        this.customer = customer;
    }

    /** @return the id */
    public Long getId() {
        return id;
    }

    /** @return the status */
    public int getStatus() {
        return status;
    }

    /** @return the price */
    public double getPrice() {
        return price;
    }

    /** @return the note */
    public String getNote() {
        return note;
    }

    /** @param note the new note */
    public void setNote(String note) {
        this.note = note;
    }

    /** @return the customer (a lazy reference) */
    public BenchCustomer getCustomer() {
        return customer;
    }
}
