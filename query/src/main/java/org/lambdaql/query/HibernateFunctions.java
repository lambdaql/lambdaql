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

    /**
     * Finds the table and column of an entity property (for the Oracle function-based index check, PR8).
     *
     * @param sf     the session factory
     * @param entity the entity class
     * @param path   the property path, for example {@code price}
     * @return {table, column}, or null when it cannot be found
     */
    static String[] tableAndColumn(org.hibernate.SessionFactory sf, Class<?> entity, String path) {
        try {
            var persister = sf.unwrap(org.hibernate.engine.spi.SessionFactoryImplementor.class)
                    .getMappingMetamodel().getEntityDescriptor(entity);
            if (!(persister instanceof org.hibernate.persister.entity.AbstractEntityPersister p)) return null;
            String[] cols = p.getPropertyColumnNames(path);
            if (cols == null || cols.length != 1) return null;
            String table = p.getTableName();
            return new String[]{table.substring(table.lastIndexOf('.') + 1), cols[0]};
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }
}
