package org.lambdaql.query;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;

/** Test entity with a wrong equals (by name only), to show the toSet size check. */
@Entity
@Table(name = "tags")
public class Tag {
    @Id
    private Long id;
    private String name;

    /** For JPA. */
    protected Tag() {
    }

    /**
     * @param id   the id
     * @param name the name
     */
    public Tag(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    /** @return the name */
    public String getName() {
        return name;
    }

    /** Wrong on purpose: two rows with the same name are "equal". */
    @Override
    public boolean equals(Object o) {
        return o instanceof Tag t && Objects.equals(name, t.name);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name);
    }
}
