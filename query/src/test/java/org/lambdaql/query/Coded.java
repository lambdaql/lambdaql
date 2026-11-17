package org.lambdaql.query;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Convert;
import jakarta.persistence.Converter;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A test entity with converted columns (ConvertTest): the stored values differ from the Java values. */
@Entity
@Table(name = "coded")
public class Coded {

    /** Stores a string reversed ("abc" is stored as "cba"), like an encoded column. */
    @Converter
    public static class Reverse implements AttributeConverter<String, String> {
        @Override
        public String convertToDatabaseColumn(String value) {
            return value == null ? null : new StringBuilder(value).reverse().toString();
        }

        @Override
        public String convertToEntityAttribute(String value) {
            return value == null ? null : new StringBuilder(value).reverse().toString();
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Convert(converter = Reverse.class)
    private String code;

    @Convert(converter = org.hibernate.type.YesNoConverter.class)
    private Boolean vip;

    /** For JPA. */
    public Coded() {
    }

    /**
     * Makes a row.
     *
     * @param code the code
     * @param vip  the flag
     */
    public Coded(String code, Boolean vip) {
        this.code = code;
        this.vip = vip;
    }

    /** @return the id */
    public Long getId() {
        return id;
    }

    /** @return the code */
    public String getCode() {
        return code;
    }

    /** @return the flag */
    public Boolean getVip() {
        return vip;
    }
}
