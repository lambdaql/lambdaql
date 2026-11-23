package org.lambdaql.bench;

import jakarta.persistence.EntityManagerFactory;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.Map;

/**
 * Spring Data JPA on the benchmark database, without Spring Boot: a Spring context with an EntityManagerFactory on the
 * same connection pool as the other ways, a transaction manager and the repositories. The tables and the data come
 * from {@link BenchDatabase}.
 */
@State(Scope.Benchmark)
public class SpringDataState {

    /** The Spring context. */
    public AnnotationConfigApplicationContext context;
    /** The order repository. */
    public BenchOrderRepository orders;
    /** The customer repository. */
    public BenchCustomerRepository customers;
    /** Runs a statement that changes data in a transaction. */
    public TransactionTemplate transaction;

    /**
     * Starts the Spring context.
     *
     * @param db the database (pool, tables and data)
     */
    @Setup(Level.Trial)
    public void open(BenchDatabase db) {
        context = new AnnotationConfigApplicationContext();
        // a delegate that cannot be closed: closing the Spring context must not close the shared pool
        context.registerBean(DataSource.class, () -> new DelegatingDataSource(db.dataSource));
        context.register(Config.class);
        context.refresh();
        orders = context.getBean(BenchOrderRepository.class);
        customers = context.getBean(BenchCustomerRepository.class);
        transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    }

    /** Closes the Spring context. */
    @TearDown(Level.Trial)
    public void close() {
        context.close();
    }

    /** The JPA and repository beans. */
    @Configuration
    @EnableJpaRepositories(basePackageClasses = BenchOrderRepository.class)
    static class Config {

        /**
         * The EntityManagerFactory of Spring (Hibernate), on the shared pool; the schema already exists.
         *
         * @param dataSource the shared pool
         * @return the factory bean
         */
        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            LocalContainerEntityManagerFactoryBean f = new LocalContainerEntityManagerFactoryBean();
            f.setDataSource(dataSource);
            f.setPackagesToScan(BenchOrder.class.getPackageName());
            f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none"));
            return f;
        }

        /**
         * The transaction manager.
         *
         * @param emf the factory
         * @return the manager
         */
        @Bean
        JpaTransactionManager transactionManager(EntityManagerFactory emf) {
            return new JpaTransactionManager(emf);
        }
    }
}
