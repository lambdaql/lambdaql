package org.lambdaql.query;

import jakarta.persistence.EntityManagerFactory;
import org.lambdaql.SortNotAllowedException;
import org.lambdaql.i18n.LqlMessage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The outside sort rules of one persistence unit (ROADMAP D43). Register it once for an {@link EntityManagerFactory};
 * every {@code QueryBuilder} and repository on that factory uses it. In Spring Boot, a {@code SortPolicy} bean is
 * registered for you.
 * <p>
 * Only sorts made from strings are checked: a Spring {@code Sort} or {@code Pageable} and {@link SortSpec#ofPath}.
 * The rules of an entity come from one place: the rules given here, else its {@link org.lambdaql.Sortable} fields.
 * Without a registered policy, lambdaQL uses {@link SortMode#INFO} with rules only from {@link org.lambdaql.Sortable}.
 *
 * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortPolicyTest.java">SortPolicyTest</a>):
 * {@snippet class="org.lambdaql.query.SortPolicyTest" region="strict"}
 */
public final class SortPolicy {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(SortPolicy.class);
    private static final Map<EntityManagerFactory, SortPolicy> REGISTRY = Collections.synchronizedMap(new WeakHashMap<>());
    private static final SortPolicy DEFAULT = new SortPolicy(SortMode.INFO, Map.of());

    private static final Map<Class<?>, Optional<SortRules<?>>> ANNOTATED = new ConcurrentHashMap<>();

    private final SortMode mode;
    private final Map<Class<?>, SortRules<?>> rules;
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    private SortPolicy(SortMode mode, Map<Class<?>, SortRules<?>> rules) {
        this.mode = mode;
        this.rules = rules;
    }

    /**
     * Makes a policy.
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortPolicyTest.java">SortPolicyTest</a>):
     * {@snippet class="org.lambdaql.query.SortPolicyTest" region="strict"}
     *
     * @param mode  what to do with a sort that is not allowed
     * @param rules the rules of each entity
     * @return the policy
     */
    public static SortPolicy of(SortMode mode, SortRules<?>... rules) {
        Map<Class<?>, SortRules<?>> map = new LinkedHashMap<>();
        for (SortRules<?> r : rules) map.put(r.type(), r);
        return new SortPolicy(mode, Map.copyOf(map));
    }

    /**
     * Registers the policy of a persistence unit (replaces an earlier one).
     *
     * @param emf    the entity manager factory
     * @param policy the policy
     */
    public static void register(EntityManagerFactory emf, SortPolicy policy) {
        REGISTRY.put(emf, policy);
    }

    /**
     * Removes the policy of a persistence unit; the default ({@link SortMode#INFO}, rules only from {@link org.lambdaql.Sortable}) applies again.
     *
     * @param emf the entity manager factory
     */
    public static void unregister(EntityManagerFactory emf) {
        REGISTRY.remove(emf);
    }

    /**
     * Returns the registered policy of a persistence unit.
     *
     * @param emf the entity manager factory
     * @return the policy, or empty when none is registered
     */
    public static Optional<SortPolicy> find(EntityManagerFactory emf) {
        return Optional.ofNullable(REGISTRY.get(emf));
    }

    /**
     * Returns the policy in force for a persistence unit: the registered one, else the default
     * ({@link SortMode#INFO}, rules only from {@link org.lambdaql.Sortable}).
     *
     * @param emf the entity manager factory
     * @return the policy
     */
    public static SortPolicy effective(EntityManagerFactory emf) {
        return find(emf).orElse(DEFAULT);
    }

    /**
     * Returns the mode.
     *
     * @return the mode
     */
    public SortMode mode() {
        return mode;
    }

    /**
     * Returns the rules of each entity given to {@link #of(SortMode, SortRules[])} (not the {@code @Sortable} rules).
     *
     * @return the rules
     */
    public java.util.Collection<SortRules<?>> rules() {
        return rules.values();
    }

    /**
     * Checks if a name is an outside name (alias) of an entity, so it is not an entity property path.
     *
     * @param type the entity class
     * @param name the outside name
     * @return {@code true} for an alias
     */
    public boolean isAlias(Class<?> type, String name) {
        SortRules<?> r = rules.get(type);
        if (r == null) r = ANNOTATED.computeIfAbsent(type, t -> Optional.ofNullable(SortRules.fromAnnotations(t))).orElse(null);
        return r != null && r.isAlias(name);
    }

    /** Turns an outside name into a path, or rejects or warns when the rules do not allow it. */
    String check(QueryContext context, Class<?> root, String name) {
        SortRules<?> r = rules.get(root);
        if (r == null) r = ANNOTATED.computeIfAbsent(root, t -> Optional.ofNullable(SortRules.fromAnnotations(t))).orElse(null);
        if (r != null) {
            String path = r.names(context).get(name);
            if (path != null) return path;
        }
        switch (mode) {
            case STRICT -> throw new SortNotAllowedException(root.getSimpleName(), name);
            case WARN -> {
                if (warned.add(root.getName() + "#" + name) && LOG.isWarnEnabled()) {
                    LOG.warn("{}", LqlMessage.SORT_NOT_ALLOWED_WARN.format(root.getSimpleName(), name));
                }
            }
            case INFO -> {
                if (warned.add(root.getName() + "#" + name) && LOG.isInfoEnabled()) {
                    LOG.info("{}", LqlMessage.SORT_NOT_ALLOWED_WARN.format(root.getSimpleName(), name));
                }
            }
            case OFF -> { }
        }
        return name;
    }
}
