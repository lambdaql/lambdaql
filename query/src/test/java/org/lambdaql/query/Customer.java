package org.lambdaql.query;

import jakarta.persistence.*;


import java.io.Serializable;
import java.util.List;

@Entity
@Table(name = "customers")
public class Customer implements Serializable {

    @Id
    @Column(name = "id")
    private int id;

    @org.lambdaql.Sortable                      // outside sorts may use "name" (SortPolicyTest)
    private String name;

    /** The customer grade, for example 1 to 5. Can be NULL (used by the planned subquery examples). */
    @org.lambdaql.Sortable("level")             // the outside name "level" sorts by grade
    private Integer grade;

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Order> orders;

    public Customer() {}

    public Customer(int id, String name, List<Order> orders) {
        this.id = id;
        this.name = name;
        this.orders = orders;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<Order> getOrders() {
        return orders;
    }

    /**
     * Returns the grade.
     *
     * @return the grade, or {@code null}
     */
    public Integer getGrade() {
        return grade;
    }

    /**
     * Sets the grade.
     *
     * @param grade the grade, or {@code null}
     */
    public void setGrade(Integer grade) {
        this.grade = grade;
    }

    /**
     * Sets the id (used by DML examples: {@code o.getCustomer().setId(x)} changes the foreign key).
     *
     * @param id the id
     */
    public void setId(int id) {
        this.id = id;
    }

    /**
     * Sets the name (used by a DML example that must be rejected).
     *
     * @param name the name
     */
    public void setName(String name) {
        this.name = name;
    }
}
