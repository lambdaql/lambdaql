package org.lambdaql.bench;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.io.Serializable;

/** A customer of the benchmark data (100 rows). */
@Entity
@Table(name = "SQ_bench_customers")
public class BenchCustomer implements Serializable {

    @Id
    private Long id;

    private String name;

    /** For JPA. */
    protected BenchCustomer() {
    }

    /**
     * Makes a customer.
     *
     * @param id   the id
     * @param name the name, for example {@code c7}
     */
    public BenchCustomer(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    /** @return the id */
    public Long getId() {
        return id;
    }

    /** @return the name */
    public String getName() {
        return name;
    }
}
