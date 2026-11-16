package org.lambdaql.spring.sample;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Test entity: a team of members. */
@Entity
@Table(name = "teams")
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    /** For JPA. */
    protected Team() {
    }

    /**
     * Makes a team.
     *
     * @param name the team name
     */
    public Team(String name) {
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

    /** @param id the id */
    public void setId(Long id) {
        this.id = id;
    }

    /** @param name the name */
    public void setName(String name) {
        this.name = name;
    }
}
