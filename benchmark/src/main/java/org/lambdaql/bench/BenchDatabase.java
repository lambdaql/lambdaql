package org.lambdaql.bench;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import jakarta.persistence.PersistenceConfiguration;
import org.lambdaql.query.QueryBuilder;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

/**
 * The database of one benchmark run: a connection pool shared by all four ways (JDBC, JPQL, Criteria API, lambdaQL),
 * a JPA EntityManagerFactory on the same pool, and the data (100 customers, 10,000 orders).
 * <p>
 * The database is chosen with {@code -Dlql.db}: {@code h2} (in memory, the default) or {@code postgresql},
 * {@code mariadb}, {@code mysql}, {@code oracle} (the local verification databases; the password comes from the
 * environment variable {@code LQL_DB_PASSWORD}). The tables are named {@code SQ_bench_*} and dropped at the end.
 */
@State(Scope.Benchmark)
public class BenchDatabase {

    /** Customers in the data. */
    public static final int CUSTOMERS = 100;
    /** Orders in the data. */
    public static final int ORDERS = 10_000;

    /** The shared connection pool. */
    public HikariDataSource dataSource;
    /** JPA on the same pool. */
    public EntityManagerFactory emf;
    /** lambdaQL on the same EntityManagerFactory. */
    public QueryBuilder qb;

    /**
     * Opens the pool, starts JPA and writes the data.
     *
     * @throws SQLException if the data cannot be written
     */
    @Setup(Level.Trial)
    public void open() throws SQLException {
        String db = System.getProperty("lql.db", "h2").toLowerCase(Locale.ROOT);
        HikariConfig config = new HikariConfig();
        String password = System.getenv().getOrDefault("LQL_DB_PASSWORD", "");
        switch (db) {
            case "h2" -> config.setJdbcUrl("jdbc:h2:mem:bench;DB_CLOSE_DELAY=-1");
            case "postgresql" -> login(config, "jdbc:postgresql://localhost:5432/lql_verify", "lql", password);
            case "mariadb" -> login(config, "jdbc:mariadb://localhost:3306/lql_verify", "root", password);
            case "mysql" -> login(config, "jdbc:mysql://localhost:3307/lql_verify", "root", password);
            case "oracle" -> login(config, "jdbc:oracle:thin:@localhost:1521/FREEPDB1", "LQL", password);
            default -> throw new IllegalArgumentException("unknown lql.db: " + db);
        }
        config.setMaximumPoolSize(4);
        dataSource = new HikariDataSource(config);
        dropTables();
        emf = Persistence.createEntityManagerFactory(new PersistenceConfiguration("bench")
                .provider("org.hibernate.jpa.HibernatePersistenceProvider")
                .managedClass(BenchCustomer.class)
                .managedClass(BenchOrder.class)
                .property("hibernate.connection.datasource", dataSource)
                .property("jakarta.persistence.schema-generation.database.action", "create"));
        seed();
        qb = new QueryBuilder(emf);
    }

    private static void login(HikariConfig config, String url, String user, String password) {
        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(password);
    }

    /** 100 customers {@code c0}..{@code c99}; order {@code i}: status {@code i % 10}, price {@code (i * 7) % 1000}. */
    private void seed() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement("insert into SQ_bench_customers (id, name) values (?, ?)")) {
                for (long i = 0; i < CUSTOMERS; i++) {
                    ps.setLong(1, i);
                    ps.setString(2, "c" + i);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "insert into SQ_bench_orders (id, status, price, note, customer_id) values (?, ?, ?, ?, ?)")) {
                for (long i = 0; i < ORDERS; i++) {
                    ps.setLong(1, i);
                    ps.setInt(2, (int) (i % 10));
                    ps.setDouble(3, (i * 7) % 1000);
                    ps.setString(4, "n");
                    ps.setLong(5, i % CUSTOMERS);
                    ps.addBatch();
                    if (i % 1000 == 999) ps.executeBatch();
                }
                ps.executeBatch();
            }
            c.commit();
            // fresh statistics, so the planner sees the data and an automatic analyze does not run while measuring
            String analyze = switch (System.getProperty("lql.db", "h2").toLowerCase(Locale.ROOT)) {
                case "postgresql" -> "analyze SQ_bench_customers, SQ_bench_orders";
                case "mariadb", "mysql" -> "analyze table SQ_bench_customers, SQ_bench_orders";
                case "oracle" -> "begin dbms_stats.gather_table_stats(user, 'SQ_BENCH_ORDERS');"
                        + " dbms_stats.gather_table_stats(user, 'SQ_BENCH_CUSTOMERS'); end;";
                default -> null;
            };
            if (analyze != null) {
                c.setAutoCommit(true);
                try (Statement st = c.createStatement()) {
                    st.execute(analyze);
                }
            }
        }
    }

    /** Drops the tables and closes JPA and the pool. */
    @TearDown(Level.Trial)
    public void close() throws SQLException {
        emf.close();
        dropTables();
        dataSource.close();
    }

    private void dropTables() throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            for (String table : new String[]{"SQ_bench_orders", "SQ_bench_customers"}) {
                try {
                    st.execute("drop table " + table);
                } catch (SQLException e) {
                    // not there yet
                }
            }
        }
    }
}
