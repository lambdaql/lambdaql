package org.lambdaql.query;

import jakarta.persistence.EntityManagerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The SARGable mode of each persistence unit (ROADMAP PR8), registered the same way as {@link SortPolicy}.
 * Without a registration the mode is {@link SargableMode#WARN}.
 * <p>
 * The check runs once per condition lambda, when its analysis goes into the cache. Register the mode before the
 * first query; a lambda analyzed earlier is not checked again.
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java">FunctionTest</a>):
 * {@snippet class="org.lambdaql.query.FunctionTest" region="sargable-strict"}
 */
public final class SargablePolicy {

    private static final Map<EntityManagerFactory, SargableMode> REGISTRY = Collections.synchronizedMap(new WeakHashMap<>());

    private SargablePolicy() {
    }

    /**
     * Registers the mode of a persistence unit (replaces an earlier one).
     *
     * @param emf  the entity manager factory
     * @param mode the mode
     */
    public static void register(EntityManagerFactory emf, SargableMode mode) {
        REGISTRY.put(emf, mode);
    }

    /**
     * Removes the mode of a persistence unit; {@link SargableMode#WARN} applies again.
     *
     * @param emf the entity manager factory
     */
    public static void unregister(EntityManagerFactory emf) {
        REGISTRY.remove(emf);
    }

    /**
     * Returns the mode in force for a persistence unit.
     *
     * @param emf the entity manager factory
     * @return the registered mode, else {@link SargableMode#WARN}
     */
    public static SargableMode effective(EntityManagerFactory emf) {
        SargableMode mode = REGISTRY.get(emf);
        return mode == null ? SargableMode.WARN : mode;
    }
}
