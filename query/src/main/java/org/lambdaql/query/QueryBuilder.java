package org.lambdaql.query;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

/**
 * The entry point of lambdaQL: {@code qb.from(...)}, {@code qb.update(...)}, {@code qb.insert(...)}, {@code qb.delete(...)}.
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java">SelectQueryTest</a>):
 * {@snippet class="org.lambdaql.query.SelectQueryTest" region="to-list"}
 */
public class QueryBuilder {
    private final QueryContext context;

    /**
     * Creates a builder that opens a new {@link EntityManager} for each query and closes it after.
     *
     * @param emf the entity manager factory
     */
    public QueryBuilder(EntityManagerFactory emf) {
        this.context = new QueryContext(emf, null);
    }

    /**
     * Creates a builder that runs every query with the given {@link EntityManager}.
     * With Spring, pass the shared transaction-bound entity manager.
     *
     * @param entityManager the entity manager to use
     */
    public QueryBuilder(EntityManager entityManager) {
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


    /**
     * Starts an UPDATE (ROADMAP D28, M10).
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java">DmlQueryTest</a>):
     * {@snippet class="org.lambdaql.query.DmlQueryTest" region="update"}
     *
     * @param entityClass the entity class
     * @param <T>         the entity type
     * @return the start of the UPDATE: call {@code set}
     */
    public <T> UpdateQuery<T> update(Class<T> entityClass) {
        return new UpdateQuery<>(entityClass, context);
    }

    /**
     * Starts a DELETE.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java">DmlQueryTest</a>):
     * {@snippet class="org.lambdaql.query.DmlQueryTest" region="delete"}
     *
     * @param entityClass the entity class
     * @param <T>         the entity type
     * @return the start of the DELETE: call {@code where} or {@code all}
     */
    public <T> DeleteQuery<T> delete(Class<T> entityClass) {
        return new DeleteQuery<>(entityClass, context);
    }

    /**
     * Starts an INSERT.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java">DmlQueryTest</a>):
     * {@snippet class="org.lambdaql.query.DmlQueryTest" region="insert"}
     *
     * @param entityClass the entity class
     * @param <T>         the entity type
     * @return the start of the INSERT: call {@code values} or {@code from}
     */
    public <T> InsertQuery<T> insert(Class<T> entityClass) {
        return new InsertQuery<>(entityClass, context);
    }

    /**
     * Starts an upsert keyed by the {@code @Id} (ROADMAP D40).
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java">DmlQueryTest</a>):
     * {@snippet class="org.lambdaql.query.DmlQueryTest" region="upsert"}
     *
     * @param entityClass the entity class
     * @param <T>         the entity type
     * @return the start of the upsert: call {@code values}
     */
    public <T> UpsertQuery<T> upsert(Class<T> entityClass) {
        return new UpsertQuery<>(entityClass, context);
    }

    /**
     * Calls a stored procedure that gives a result: {@code qb.call(OrderProcs.class, p -> p.calc(id, rate)).execute()}
     * (ROADMAP M11, D30). The stub method needs {@link org.lambdaql.SqlProcedure}.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java">ProcedureTest</a>):
     * {@snippet class="org.lambdaql.query.ProcedureTest" region="call"}
     *
     * @param procs the stub interface
     * @param call  one stub call; the arguments are captured values or constants
     * @param <P>   the stub type
     * @param <R>   the result type
     * @return the call, run it with {@code execute()}
     */
    @SuppressWarnings("unchecked")
    public <P, R> ProcedureCall<R> call(Class<P> procs, Call<P, R> call) {
        return new ProcedureCall<>(() -> (R) context.procedure(procs, call, true));
    }

    /**
     * Calls a stored procedure without a result: {@code qb.exec(OrderProcs.class, p -> p.purge(30)).execute()}.
     * A procedure with OUT parameters is rejected, because their values would be lost (PR12).
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java">ProcedureTest</a>):
     * {@snippet class="org.lambdaql.query.ProcedureTest" region="exec"}
     *
     * @param procs the stub interface
     * @param call  one stub call
     * @param <P>   the stub type
     * @return the call, run it with {@code execute()}
     */
    public <P> ProcedureCall<Void> exec(Class<P> procs, Exec<P> call) {
        return new ProcedureCall<>(() -> {
            context.procedure(procs, call, false);
            return null;
        });
    }

    /**
     * Calls a database function on its own: {@code qb.call(() -> OrderFns.discount(price)).execute()} (PR6).
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProcedureTest.java">ProcedureTest</a>):
     * {@snippet class="org.lambdaql.query.ProcedureTest" region="function"}
     *
     * @param call one {@code @SqlFunction} call; the arguments are captured values or constants
     * @param <R>  the result type
     * @return the call, run it with {@code execute()}
     */
    @SuppressWarnings("unchecked")
    public <R> ProcedureCall<R> call(FunctionCall<R> call) {
        return new ProcedureCall<>(() -> (R) context.function(call));
    }
}
