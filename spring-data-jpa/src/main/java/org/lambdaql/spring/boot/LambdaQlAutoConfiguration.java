package org.lambdaql.spring.boot;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;
import org.lambdaql.query.SargableMode;
import org.lambdaql.query.SargablePolicy;
import org.lambdaql.query.SortMode;
import org.lambdaql.query.SortPolicy;
import org.lambdaql.query.SortRules;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.ResolvableType;
import org.springframework.core.env.Environment;
import org.lambdaql.query.QueryBuilder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.context.annotation.Bean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/AutoConfigurationTest.java">AutoConfigurationTest</a>):
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

    /**
     * Registers the outside sort policy for the entity manager factory (ROADMAP D43). The rules of an entity come from
     * one place, in this order: the {@link SortPolicy} bean, the properties, the {@code @Sortable} fields.
     * <pre>
     * lambdaql.sort.mode=STRICT                       # OFF, INFO (default), WARN, STRICT; wins over the bean's mode
     * lambdaql.sort.allow.Order=price,customer.name   # the JPA entity name (by default the simple class name)
     * lambdaql.sort.alias.Order.newest=updateAt       # outside name newest sorts by updateAt
     * </pre>
     *
     * @param entityManagerFactory the only entity manager factory
     * @param policy               the sort policy bean, if any
     * @param environment          the Spring environment with the {@code lambdaql.sort.*} properties
     * @return a callback that runs after all singletons are made
     */
    @Bean
    public SmartInitializingSingleton lambdaQlSortPolicyRegistration(EntityManagerFactory entityManagerFactory,
                                                                     ObjectProvider<SortPolicy> policy, Environment environment) {
        return () -> {
            SortPolicy bean = policy.getIfAvailable();
            Binder binder = Binder.get(environment);
            SortMode mode = binder.bind("lambdaql.sort.mode", SortMode.class).orElse(null);
            Map<String, List<String>> allow = binder.bind("lambdaql.sort.allow",
                    Bindable.mapOf(String.class, String.class).withExistingValue(null)).orElse(Map.of()).entrySet().stream()
                    .collect(LinkedHashMap::new, (m, e) -> m.put(e.getKey(), List.of(e.getValue().split(","))), Map::putAll);
            Map<String, Map<String, String>> alias = binder.bind("lambdaql.sort.alias",
                    Bindable.of(ResolvableType.forClassWithGenerics(Map.class, ResolvableType.forClass(String.class),
                            ResolvableType.forClassWithGenerics(Map.class, String.class, String.class)))).map(m -> {
                        @SuppressWarnings("unchecked") Map<String, Map<String, String>> x = (Map<String, Map<String, String>>) m;
                        return x;
                    }).orElse(Map.of());
            if (bean == null && mode == null && allow.isEmpty() && alias.isEmpty()) return;
            Map<Class<?>, SortRules<?>> rules = new LinkedHashMap<>();
            if (bean != null) bean.rules().forEach(r -> rules.put(r.type(), r));
            Set<String> keys = new LinkedHashSet<>(allow.keySet());
            keys.addAll(alias.keySet());
            for (String key : keys) {
                Class<?> type = entity(entityManagerFactory, key);
                if (rules.containsKey(type)) continue;                     // the bean wins for its entities
                SortRules<?> r = SortRules.of(type);
                r.allowPath(allow.getOrDefault(key, List.of()).toArray(String[]::new));
                alias.getOrDefault(key, Map.of()).forEach(r::aliasPath);
                rules.put(type, r);
            }
            SortMode m = mode != null ? mode : bean != null ? bean.mode() : SortMode.INFO;
            SortPolicy.register(entityManagerFactory, SortPolicy.of(m, rules.values().toArray(SortRules<?>[]::new)));
        };
    }

    /**
     * Registers the SARGable mode for the entity manager factory (ROADMAP PR8):
     * {@code lambdaql.sargable.mode=STRICT} (OFF, WARN (default), STRICT). Without the property nothing is registered
     * and the mode is WARN.
     *
     * @param entityManagerFactory the only entity manager factory
     * @param environment          the Spring environment with {@code lambdaql.sargable.mode}
     * @return a callback that runs after all singletons are made
     */
    @Bean
    public SmartInitializingSingleton lambdaQlSargableRegistration(EntityManagerFactory entityManagerFactory, Environment environment) {
        return () -> Binder.get(environment).bind("lambdaql.sargable.mode", SargableMode.class)
                .ifBound(mode -> SargablePolicy.register(entityManagerFactory, mode));
    }

    private static Class<?> entity(EntityManagerFactory emf, String key) {
        for (EntityType<?> e : emf.getMetamodel().getEntities()) {
            // the JPA entity name is unique in a persistence unit (Hibernate rejects duplicates at start-up),
            // so it never matches two classes; by default it is the simple class name
            if (e.getName().equals(key)) return e.getJavaType();
        }
        throw new IllegalStateException(org.lambdaql.i18n.LqlMessage.SORT_UNKNOWN_ENTITY.format(key));
    }
}
