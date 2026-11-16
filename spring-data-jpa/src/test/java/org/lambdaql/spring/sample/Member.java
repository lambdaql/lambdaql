package org.lambdaql.spring.sample;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Test entity: a member with an optional grade and an optional team. */
@Entity
@Table(name = "members")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private boolean active;

    private Integer grade;

    @ManyToOne
    private Team team;

    /** For JPA. */
    protected Member() {
    }

    /**
     * Makes a member.
     *
     * @param name   the name
     * @param active {@code true} if active
     * @param grade  the grade, may be null
     * @param team   the team, may be null
     */
    public Member(String name, boolean active, Integer grade, Team team) {
        this.name = name;
        this.active = active;
        this.grade = grade;
        this.team = team;
    }

    /** @return the id */
    public Long getId() {
        return id;
    }

    /** @return the name */
    public String getName() {
        return name;
    }

    /** @return {@code true} if active */
    public boolean isActive() {
        return active;
    }

    /** @return the grade, may be null */
    public Integer getGrade() {
        return grade;
    }

    /** @return the team, may be null */
    public Team getTeam() {
        return team;
    }

    /** @param name the name */
    public void setName(String name) {
        this.name = name;
    }

    /** @param active {@code true} if active */
    public void setActive(boolean active) {
        this.active = active;
    }

    /** @param grade the grade, may be null */
    public void setGrade(Integer grade) {
        this.grade = grade;
    }
}
