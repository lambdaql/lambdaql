package org.lambdaql.query;

import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;

/**
 * Test-only naming strategy: every table name gets the prefix {@code SQ_}.
 * It is used when the tests run on the shared MariaDB / Oracle verification schemas,
 * so test tables never clash with other objects there ({@code -Dlql.db=mariadb|oracle}).
 */
public class SqPrefixNamingStrategy extends CamelCaseToUnderscoresNamingStrategy {

    /**
     * Adds {@code SQ_} in front of the table name.
     *
     * @param name    the logical name
     * @param context the JDBC environment
     * @return the prefixed physical name, for example {@code SQ_ORDERS}
     */
    @Override
    public Identifier toPhysicalTableName(Identifier name, JdbcEnvironment context) {
        Identifier base = super.toPhysicalTableName(name, context);
        return Identifier.toIdentifier("SQ_" + base.getText(), base.isQuoted());
    }
}
