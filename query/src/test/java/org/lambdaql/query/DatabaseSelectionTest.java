package org.lambdaql.query;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks that {@code -Dlql.db=mariadb|oracle} really connects to that database.
 * Without this check, a wrong setting would quietly run every test on H2.
 */
@SpringBootTest(classes = DatabaseSelectionTest.Context.class)
@ActiveProfiles("test")
class DatabaseSelectionTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void connectedToTheChosenDatabase() throws Exception {
        String wanted = System.getProperty("lql.db", "h2").toLowerCase(Locale.ROOT);
        try (Connection c = dataSource.getConnection()) {
            String product = c.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
            String version = c.getMetaData().getDatabaseProductVersion();
            System.out.println("[lql.db] " + wanted + " -> " + product + " " + version);
            assertTrue(product.contains(wanted), "lql.db=" + wanted + " but connected to " + product);
        }
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml. */
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = "org.lambdaql.query")
    static class Context {
    }
}
