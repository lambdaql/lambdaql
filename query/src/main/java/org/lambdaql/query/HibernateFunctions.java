package org.lambdaql.query;

import jakarta.persistence.EntityManagerFactory;

/** Asks Hibernate about its function registry. Hibernate is given by the user at run time. Not public API. */
final class HibernateFunctions {

    private HibernateFunctions() {
    }

    /**
     * Checks if Hibernate already knows a function name (a built-in function such as {@code coalesce} or
     * {@code round}, or a dialect function).
     *
     * @param emf the entity manager factory
     * @param key the function name
     * @return {@code true} if the name is in the Hibernate function registry; {@code false} when Hibernate is not used
     */
    static boolean isKnown(EntityManagerFactory emf, String key) {
        try {
            var sf = emf.unwrap(org.hibernate.engine.spi.SessionFactoryImplementor.class);
            return sf.getQueryEngine().getSqmFunctionRegistry().findFunctionDescriptor(key) != null;
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }
}
