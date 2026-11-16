package org.lambdaql.spring;

import org.junit.jupiter.api.Test;
import org.lambdaql.spring.sample.SampleApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks that {@code -Dlql.db=mariadb|oracle} really connects to that database. */
@SpringBootTest(classes = SampleApplication.class)
@ActiveProfiles("test")
class DatabaseSelectionTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void connectedToTheChosenDatabase() throws Exception {
        String wanted = System.getProperty("lql.db", "h2").toLowerCase(Locale.ROOT);
        try (Connection c = dataSource.getConnection()) {
            String product = c.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
            System.out.println("[lql.db] " + wanted + " -> " + product + " " + c.getMetaData().getDatabaseProductVersion());
            assertTrue(product.contains(wanted), "lql.db=" + wanted + " but connected to " + product);
        }
    }
}
