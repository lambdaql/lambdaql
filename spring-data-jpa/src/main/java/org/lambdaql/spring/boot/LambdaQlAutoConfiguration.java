package org.lambdaql.spring.boot;

import jakarta.persistence.EntityManagerFactory;
import org.lambdaql.query.QueryBuilder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.context.annotation.Bean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;

/**
 * Spring Boot auto-configuration: a {@link QueryBuilder} bean you can inject into services.
 * <p>
 * The bean uses a shared, transaction-bound {@code EntityManager}, so queries join the current
 * {@code @Transactional} transaction. It is made only when there is exactly one {@link EntityManagerFactory}
 * and you did not define your own {@link QueryBuilder}. The repository executor ({@code LambdaQlExecutor})
 * does not need this bean.
 * <p>
 * It runs after Hibernate JPA auto-configuration. Both class names (Spring Boot 3 and Spring Boot 4)
 * are given as text, so this class loads on both versions.
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/AutoConfigurationTest.java">AutoConfigurationTest</a>):
 * {@snippet class="org.lambdaql.spring.AutoConfigurationTest" region="query-builder-bean"}
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",    // Spring Boot 3
        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"   // Spring Boot 4
})
@ConditionalOnClass({QueryBuilder.class, EntityManagerFactory.class})
@ConditionalOnSingleCandidate(EntityManagerFactory.class)
public class LambdaQlAutoConfiguration {

    /**
     * Creates the auto-configuration (Spring Boot does this).
     */
    public LambdaQlAutoConfiguration() {
    }

    /**
     * Makes the {@link QueryBuilder} bean.
     *
     * @param entityManagerFactory the only entity manager factory
     * @return a query builder on a shared, transaction-bound entity manager
     */
    @Bean
    @ConditionalOnMissingBean
    public QueryBuilder lambdaQlQueryBuilder(EntityManagerFactory entityManagerFactory) {
        return new QueryBuilder(SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory));
    }
}
