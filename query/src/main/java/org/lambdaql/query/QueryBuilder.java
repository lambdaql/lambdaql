package org.lambdaql.query;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.lambdaql.analyzer.EntityColumnResolver;

/**
 * The entry point of lambdaQL: {@code qb.from(...)}, {@code qb.update(...)}, {@code qb.insert(...)}, {@code qb.delete(...)}.
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java">SelectQueryTest</a>):
 * {@snippet class="org.lambdaql.query.SelectQueryTest" region="to-list"}
 */
public class QueryBuilder {
    private final EntityColumnResolver columnResolver;
    private final QueryContext context;

    /**
     * Creates a builder that opens a new {@link EntityManager} for each query and closes it after.
     *
     * @param emf the entity manager factory
     */
    public QueryBuilder(EntityManagerFactory emf) {
        this.columnResolver = new EntityColumnResolver(emf.getMetamodel(), emf);
        this.context = new QueryContext(emf, null);
    }

    /**
     * Creates a builder that runs every query with the given {@link EntityManager}.
     * With Spring, pass the shared transaction-bound entity manager.
     *
     * @param entityManager the entity manager to use
     */
    public QueryBuilder(EntityManager entityManager) {
        this.columnResolver = new EntityColumnResolver(entityManager.getMetamodel(), entityManager.getEntityManagerFactory());
        this.context = new QueryContext(entityManager.getEntityManagerFactory(), entityManager);
    }

    /**
     * Starts a select query on an entity.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java">SelectQueryTest</a>):
     * {@snippet class="org.lambdaql.query.SelectQueryTest" region="to-list"}
     *
     * @param entityClass the entity class
     * @param <T>         the entity type
     * @return a new select query
     */
    public <T> SelectQuery<T> from(Class<T> entityClass) {
        return new SelectQuery<>(entityClass, context);
    }


    public <T> UpdateQuery<T> update(Class<T> entityClass) {
        return new UpdateQuery<>(entityClass, columnResolver);
    }

    public <T> DeleteQuery<T> delete(Class<T> entityClass) {
        return new DeleteQuery<>(entityClass, columnResolver);
    }

    public <T> InsertQuery<T> insert(Class<T> entityClass) {
        return new InsertQuery<>(entityClass, columnResolver);
    }

    // insert(), delete(), select() 등도 비슷한 방식
}