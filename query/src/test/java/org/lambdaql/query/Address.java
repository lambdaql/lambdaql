package org.lambdaql.query;

import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

/**
 * Test embedded value: the address of an {@link Order} (columns {@code city} and {@code zip}).
 * <p>
 * The manual uses it for updates of embedded fields. See {@link PlannedSqlTest}.
 * <p>
 * Example (planned HQL):
 * <pre>{@code
 * update Order o set o.address.city = :p1 where o.id = :p2
 * }</pre>
 */
@Embeddable
@Getter
@Setter
public class Address {

    /** The city. Can be NULL. */
    private String city;

    /** The zip code. Can be NULL. */
    private String zip;

    /** Empty constructor for JPA. */
    public Address() {}

    /**
     * Makes an address.
     *
     * @param city the city
     * @param zip  the zip code
     */
    public Address(String city, String zip) {
        this.city = city;
        this.zip = zip;
    }
}
