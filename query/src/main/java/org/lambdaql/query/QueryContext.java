package org.lambdaql.query;

import org.lambdaql.i18n.LqlMessage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.lambdaql.engine.decode.ConditionReducer;
import org.lambdaql.engine.decode.Decoder;
import org.lambdaql.engine.ir.Assignment;
import org.lambdaql.engine.ir.Expr;
import org.lambdaql.engine.ir.Pred;
import org.lambdaql.engine.ir.SortItem;
import org.lambdaql.engine.load.BytecodeLoader;
import org.lambdaql.engine.load.LambdaRef;
import org.lambdaql.engine.load.SerializedLambdaExtractor;
import org.lambdaql.engine.sem.EntityModel;
import org.lambdaql.engine.sem.JpaEntityModel;
import org.lambdaql.engine.sem.Normalizer;
import org.lambdaql.engine.sem.AssignmentReader;
import org.lambdaql.engine.sem.SortReader;
import org.lambdaql.engine.render.RenderedQuery;
import jakarta.persistence.NonUniqueResultException;
import jakarta.persistence.TransactionRequiredException;
import jakarta.persistence.TypedQuery;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Shared state of one {@link QueryBuilder}: how to get an {@link EntityManager}, the entity model,
 * and the cache of analyzed lambdas.
 * <p>
 * A lambda is analyzed only once per lambda class (one class per place in your code).
 * Later calls only read the captured values again.
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java">SelectQueryTest</a>):
 * {@snippet class="org.lambdaql.query.SelectQueryTest" region="to-hql"}
 */
public final class QueryContext {

    private final EntityManagerFactory emf;
    private final EntityManager sharedEntityManager;
    private final org.lambdaql.engine.sem.RowModel model;

    /**
     * Creates a context.
     *
     * @param emf                 the factory; used to create an {@link EntityManager} when {@code sharedEntityManager} is null
     * @param sharedEntityManager an entity manager to use for every query (for example Spring's transaction-bound proxy), or null
     */
    QueryContext(EntityManagerFactory emf, EntityManager sharedEntityManager) {
        this.emf = emf;
        this.sharedEntityManager = sharedEntityManager;
        this.shared = shared(emf != null ? emf : sharedEntityManager.getEntityManagerFactory(),
                sharedEntityManager != null ? sharedEntityManager.getMetamodel() : emf.getMetamodel());
        this.model = shared.model;
    }

    /**
     * What every {@code QueryBuilder} on one persistence unit shares: the entity model (with the row types of derived
     * tables) and the analysis results. The results depend only on the lambda and the entity model, so a new
     * {@code QueryBuilder} does not analyze a lambda again. The cache is a {@link ClassValue}: an entry goes away with
     * its lambda class, so redeploying or reloading classes does not keep old class loaders alive.
     */
    private static final class Shared {
        final org.lambdaql.engine.sem.RowModel model;
        final ClassValue<Map<String, Object>> analyses = new ClassValue<>() {
            @Override
            protected Map<String, Object> computeValue(Class<?> type) {
                return new ConcurrentHashMap<>();
            }
        };

        /**
         * Result builders by result shape and class loader. Looking up the constructor again for every query was a
         * visible part of a select query's time (benchmark, 2026-11-23). The key compares the shape by value, so the
         * same shape from many queries uses one entry.
         */
        final Map<ProjectorKey, Projector> projectors = new ConcurrentHashMap<>();

        Shared(jakarta.persistence.metamodel.Metamodel metamodel) {
            this.model = new org.lambdaql.engine.sem.RowModel(new JpaEntityModel(metamodel));
        }
    }

    /**
     * The key of a shared result builder.
     *
     * @param projection the result shape
     * @param loader     the class loader that sees the result class
     */
    private record ProjectorKey(org.lambdaql.engine.sem.SelectReader.Projection projection, ClassLoader loader) {
    }

    /**
     * The result builder of a shape, made once per persistence unit and shared by all queries (D47).
     *
     * @param projection the result shape (columns and the constructor to call)
     * @param loader     the class loader that sees the result class
     * @return the builder
     */
    Projector projector(org.lambdaql.engine.sem.SelectReader.Projection projection, ClassLoader loader) {
        return shared.projectors.computeIfAbsent(new ProjectorKey(projection, loader), k -> new Projector(projection, loader));
    }

    private static final Map<EntityManagerFactory, Shared> SHARED = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /** Test hook: how many lambdas were analyzed (not found in the shared cache). Not public API. */
    static final java.util.concurrent.atomic.AtomicLong ANALYSES = new java.util.concurrent.atomic.AtomicLong();

    private final Shared shared;

    private static Shared shared(EntityManagerFactory factory, jakarta.persistence.metamodel.Metamodel metamodel) {
        synchronized (SHARED) {
            // a closed factory is never used again; its entry would stay because the model refers to the factory
            SHARED.keySet().removeIf(f -> f != factory && !isOpen(f));
            return SHARED.computeIfAbsent(factory, f -> new Shared(metamodel));
        }
    }

    private static boolean isOpen(EntityManagerFactory f) {
        try {
            return f.isOpen();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** One analysis of a lambda, shared by all builders on this persistence unit. */
    @SuppressWarnings("unchecked")
    private <V> V cached(LambdaRef ref, String kind, java.util.function.Supplier<V> analysis) {
        return (V) shared.analyses.get(ref.lambdaClass()).computeIfAbsent(kind, k -> {
            ANALYSES.incrementAndGet();
            return analysis.get();
        });
    }

    /**
     * Checks the database functions in an analysis result once, when it goes into the cache:
     * a function with its own SQL name must be registered, and a name must not be a Hibernate built-in (FN8).
     */
    private <X> X checked(X analyzed) {
        for (Expr.Func f : org.lambdaql.engine.sem.SqlFunctions.calls(analyzed)) {
            if (org.lambdaql.engine.sem.SqlFunctions.isRegistered(f.key())) continue;
            if (!f.sql().equals(f.key())) throw LqlMessage.FUNCTION_NOT_REGISTERED.exception(f.key());
            if (HibernateFunctions.isKnown(factory(), f.key())) throw LqlMessage.FUNCTION_BUILTIN_NAME.exception(f.key());
        }
        return analyzed;
    }

    /** The outside sort policy of this persistence unit (ROADMAP D43). */
    SortPolicy sortPolicy() {
        return SortPolicy.effective(factory());
    }

    private EntityManagerFactory factory() {
        return emf != null ? emf : sharedEntityManager.getEntityManagerFactory();
    }


    /**
     * Runs a procedure call (ROADMAP M11, D30): analysis is cached per lambda class, the argument values are computed
     * on every run. With an {@code EntityManagerFactory} the call gets its own transaction; with a shared entity
     * manager it runs in the caller's transaction, or with auto-commit when there is none (PR9).
     *
     * @param procs   the stub interface
     * @param lambda  the call lambda
     * @param returns {@code true} for {@code call}, {@code false} for {@code exec}
     * @return the result
     */
    Object procedure(Class<?> procs, Object lambda, boolean returns) {
        LambdaRef ref = read(lambda);
        var call = cached(ref, "proc", () -> org.lambdaql.engine.sem.ProcedureReader.readCall(ref, procs, returns));
        Object[] values = hostValues(call.args(), ref);
        return procedureWork(em -> ProcedureRunner.call(em, model, call, values, !returns));
    }

    /**
     * Runs a database function on its own (ROADMAP M11, PR6).
     *
     * @param lambda the call lambda {@code () -> Fns.f(x)}
     * @return the function value
     */
    Object function(Object lambda) {
        LambdaRef ref = read(lambda);
        var call = cached(ref, "fn", () -> checked(org.lambdaql.engine.sem.ProcedureReader.readFunction(ref)));
        Object[] values = hostValues(call.args(), ref);
        return procedureWork(em -> ProcedureRunner.function(em, call, values));
    }

    private static Object[] hostValues(List<Expr> args, LambdaRef ref) {
        var evaluator = new org.lambdaql.engine.sem.HostEvaluator(ref.classLoader());
        Object[] captured = ref.capturedArgs();
        return args.stream().map(a -> evaluator.eval(a, captured)).toArray();
    }

    /** Registers the row type of a derived table or CTE, so lambdas can read its columns (ROADMAP M8-2). */
    void registerRow(Class<?> row) {
        model.register(row);
    }

    private <R> R procedureWork(Function<EntityManager, R> work) {
        if (sharedEntityManager != null) return work.apply(sharedEntityManager);
        return inTransaction(work);
    }

    /**
     * Returns the entity model.
     *
     * @return the model that reads the JPA metamodel
     */
    public EntityModel model() {
        return model;
    }

    /**
     * Binds one parameter. A {@code BigDecimal} (or a list of them) is bound with its own type: the renderer uses it
     * for a value such as 2.5 compared with a whole-number column, which Hibernate would otherwise refuse or cut to
     * the column type (D48 B8).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void bindParameter(jakarta.persistence.Query q, String name, Object value) {
        if (value instanceof java.math.BigDecimal d) {
            q.unwrap(org.hibernate.query.Query.class).setParameter(name, d, org.hibernate.type.StandardBasicTypes.BIG_DECIMAL);
        } else if (value instanceof java.util.Collection<?> c && !c.isEmpty()
                && c.stream().allMatch(java.math.BigDecimal.class::isInstance)) {
            @SuppressWarnings("unchecked") java.util.Collection<java.math.BigDecimal> list = (java.util.Collection<java.math.BigDecimal>) c;
            q.unwrap(org.hibernate.query.Query.class).setParameterList(name, list, org.hibernate.type.StandardBasicTypes.BIG_DECIMAL);
        } else {
            q.setParameter(name, value);
        }
    }

    /**
     * Analyzes a condition lambda (cached per lambda class).
     *
     * @param ref   the lambda
     * @param roots entity class of each lambda parameter
     * @return the normalized condition (without captured values; they are read at run time)
     */
    Pred analyzeWhere(LambdaRef ref, List<Class<?>> roots) {
        if (!ref.isLambdaBody()) {
            throw LqlMessage.METHOD_REFERENCE_IN_WHERE.exceptionAt(ref.describe());
        }
        return cached(ref, "where", () -> {
            var graph = Decoder.decodePredicate(ref, BytecodeLoader.load(ref));
            Pred pred = ConditionReducer.reduce(graph, ref.describe());
            Pred normalized = checked(Normalizer.normalize(pred, roots, model, ref.classLoader(), ref.describe()));
            // PR8: functions that keep the condition from using an index (once per lambda)
            withEntityManager(em -> {
                SargableCheck.check(em, normalized, roots, ref.describe(), SargablePolicy.effective(factory()));
                return null;
            });
            return normalized;
        });
    }

    /**
     * Analyzes a sort lambda with the builder (cached per lambda class).
     * Run-time directions stay as expressions and are read on every run (S10).
     *
     * @param ref   the lambda {@code (o, by) -> by...}
     * @param roots entity class of each entity parameter
     * @return the sort keys
     */
    List<SortItem> analyzeSortBy(LambdaRef ref, List<Class<?>> roots) {
        return cached(ref, "sort", () -> {
            List<SortItem> items = checked(SortReader.readSortBy(ref, roots, model));
            LobCheck.check(model, roots, items.stream().map(SortItem::key).toList(), "order by");
            return items;
        });
    }

    /**
     * Analyzes a one-key sort lambda or getter reference (cached per lambda class).
     *
     * @param ref  the lambda {@code o -> o.getPrice()} or {@code Order::getPrice}
     * @param root the entity class
     * @return the normalized key
     */
    Expr analyzeSortKey(LambdaRef ref, Class<?> root) {
        return cached(ref, "sortKey", () -> {
            Expr key = checked(SortReader.readSortKey(ref, root, model));
            LobCheck.check(model, List.of(root), List.of(key), "order by");
            return key;
        });
    }

    /**
     * Analyzes a select lambda (cached per lambda class).
     *
     * @param ref   the lambda
     * @param roots entity class of each entity parameter
     * @return the projection
     */
    org.lambdaql.engine.sem.SelectReader.Projection analyzeSelect(LambdaRef ref, List<Class<?>> roots) {
        return cached(ref, "select", () -> checked(org.lambdaql.engine.sem.SelectReader.readSelect(ref, roots, model)));
    }

    /**
     * Analyzes a groupBy lambda (cached per lambda class).
     *
     * @param ref   the lambda
     * @param roots entity class of each entity parameter
     * @return the group keys
     */
    List<Expr> analyzeGroupKeys(LambdaRef ref, List<Class<?>> roots) {
        return cached(ref, "group", () -> {
            List<Expr> keys = checked(org.lambdaql.engine.sem.SelectReader.readGroupKeys(ref, roots, model));
            LobCheck.check(model, roots, keys, "group by");
            return keys;
        });
    }

    /**
     * Analyzes a having lambda: a condition where {@code Agg} calls are allowed (cached per lambda class).
     *
     * @param ref   the lambda
     * @param roots entity class of each entity parameter
     * @return the condition
     */
    Pred analyzeHaving(LambdaRef ref, List<Class<?>> roots) {
        return cached(ref, "having", () -> {
            var graph = Decoder.decodePredicate(ref, BytecodeLoader.load(ref));
            Pred pred = ConditionReducer.reduce(graph, ref.describe());
            return checked(Normalizer.normalize(pred, roots, model, ref.classLoader(), ref.describe(), true));
        });
    }

    /**
     * Runs a rendered query without a result class: one column gives values, many columns give {@code Object[]}.
     *
     * @param query  the HQL and parameters
     * @param offset rows to skip
     * @param limit  most rows, or null
     * @return the rows
     */
    List<?> rows(RenderedQuery query, int offset, Integer limit) {
        if (limit != null && limit == 0) return List.of();
        return withEntityManager(em -> {
            var q = em.createQuery(query.hql());
            query.parameters().forEach((k, v) -> bindParameter(q, k, v));
            if (offset > 0) q.setFirstResult(offset);
            if (limit != null) q.setMaxResults(limit);
            return q.getResultList();
        });
    }

    /**
     * Analyzes a setter-form assignment lambda (cached per lambda class).
     *
     * @param ref       the lambda
     * @param roots     entity class of each entity parameter (parameter 0 is the target)
     * @param hostParam a parameter read in Java (the list element), or -1
     * @return the assignments
     */
    List<Assignment> analyzeSetters(LambdaRef ref, List<Class<?>> roots, int hostParam) {
        return analyzeSetters(ref, roots, hostParam, false);
    }

    /**
     * Analyzes a setter-form assignment lambda; {@code Agg} calls are allowed when {@code aggregates} is true
     * (cached per lambda class).
     *
     * @param ref        the lambda
     * @param roots      entity class of each entity parameter (parameter 0 is the target)
     * @param hostParam  a parameter read in Java (the list element), or -1
     * @param aggregates {@code true} for INSERT ... SELECT with aggregates
     * @return the assignments
     */
    List<Assignment> analyzeSetters(LambdaRef ref, List<Class<?>> roots, int hostParam, boolean aggregates) {
        return cached(ref, "assign", () -> checked(AssignmentReader.readSetters(ref, roots, hostParam, model, aggregates)));
    }

    /**
     * Runs UPDATE/INSERT/DELETE statements and adds up the changed rows.
     * With an entity manager factory, one new transaction is used for all statements.
     * With a shared entity manager (Spring), the caller's transaction is required; without one this throws
     * {@code TransactionRequiredException} before any SQL is sent.
     *
     * @param statements the statements, run in order
     * @return the sum of the row counts that the database returned
     */
    int executeUpdate(List<RenderedQuery> statements) {
        return inTransaction(em -> run(em, statements));
    }

    /**
     * Runs the "one row" protection: reads the ids of the matching rows (at most 2), and changes the row only if
     * there is exactly one, by its id and the same conditions. So the statement can never change more than one row,
     * even if another session adds a matching row in between.
     *
     * @param ids  {@code select e0.id from ... where ...}
     * @param byId the UPDATE/DELETE for one id
     * @return 0 if no row matches, else the count of the statement (1, or 0 if the row stopped matching)
     * @throws NonUniqueResultException if two or more rows match; then nothing is changed
     */
    int executeOne(RenderedQuery ids, java.util.function.Function<Object, RenderedQuery> byId) {
        return inTransaction(em -> {
            var q = em.createQuery(ids.hql());
            ids.parameters().forEach((k, v) -> bindParameter(q, k, v));
            q.setMaxResults(2);
            List<?> found = q.getResultList();
            if (found.size() > 1) {
                throw new NonUniqueResultException(LqlMessage.MORE_THAN_ONE_ROW_CHANGED.format(ids.hql()));
            }
            if (found.isEmpty()) return 0;
            return run(em, List.of(byId.apply(found.get(0))));
        });
    }

    /**
     * Checks if an entity manager has a transaction: joined to the current one (JTA, Spring's shared entity manager),
     * or a resource-local transaction that is active (Hibernate answers {@code isJoinedToTransaction()} with false
     * for those, measured). {@code getTransaction()} is not allowed on JTA and Spring shared entity managers.
     */
    private static boolean inTransaction(EntityManager em) {
        if (em.isJoinedToTransaction()) return true;
        try {
            return em.getTransaction().isActive();
        } catch (IllegalStateException notResourceLocal) {
            return false;
        }
    }

    /**
     * Runs DML work in a transaction. With an entity manager factory, a new transaction is used.
     * With a shared entity manager (Spring), the caller's transaction is required: without one this fails
     * before any SQL is sent (author decision 2026-11-16: DML never runs without a transaction).
     */
    private <R> R inTransaction(Function<EntityManager, R> work) {
        if (sharedEntityManager != null) {
            if (!inTransaction(sharedEntityManager)) {
                throw new TransactionRequiredException(LqlMessage.TRANSACTION_REQUIRED.format());
            }
            return work.apply(sharedEntityManager);
        }
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            R r = work.apply(em);
            em.getTransaction().commit();
            return r;
        } catch (RuntimeException e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            throw e;
        } finally {
            em.close();
        }
    }

    private static int run(EntityManager em, List<RenderedQuery> statements) {
        int n = 0;
        for (RenderedQuery q : statements) {
            var query = em.createQuery(q.hql());
            q.parameters().forEach((k, v) -> bindParameter(query, k, v));
            n += query.executeUpdate();
        }
        return n;
    }

    /**
     * Runs a rendered select query.
     *
     * @param type   the entity class
     * @param query  the HQL and parameters
     * @param offset rows to skip (0 = none)
     * @param limit  most rows to return, or {@code null} for no limit
     * @param <T>    the entity type
     * @return the rows
     */
    <T> List<T> resultList(Class<T> type, RenderedQuery query, int offset, Integer limit) {
        if (limit != null && limit == 0) return List.of();
        return withEntityManager(em -> {
            TypedQuery<T> q = em.createQuery(query.hql(), type);
            query.parameters().forEach((k, v) -> bindParameter(q, k, v));
            if (offset > 0) q.setFirstResult(offset);
            if (limit != null) q.setMaxResults(limit);
            return q.getResultList();
        });
    }

    /**
     * Reads a lambda (no analysis).
     *
     * @param lambda a serializable lambda
     * @return the lambda information with this call's captured values
     */
    static LambdaRef read(Object lambda) {
        return SerializedLambdaExtractor.extract(lambda);
    }

    /**
     * Runs work with an {@link EntityManager}: the shared one, or a new one that is closed afterwards.
     *
     * @param work the work
     * @param <R>  result type
     * @return the result of the work
     */
    <R> R withEntityManager(Function<EntityManager, R> work) {
        if (sharedEntityManager != null) return work.apply(sharedEntityManager);
        EntityManager em = emf.createEntityManager();
        try {
            return work.apply(em);
        } finally {
            em.close();
        }
    }
}
