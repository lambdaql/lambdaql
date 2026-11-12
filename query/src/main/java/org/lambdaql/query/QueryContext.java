package org.lambdaql.query;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.lambdaql.engine.decode.ConditionReducer;
import org.lambdaql.engine.decode.Decoder;
import org.lambdaql.engine.ir.Pred;
import org.lambdaql.engine.load.BytecodeLoader;
import org.lambdaql.engine.load.LambdaRef;
import org.lambdaql.engine.load.SerializedLambdaExtractor;
import org.lambdaql.engine.sem.EntityModel;
import org.lambdaql.engine.sem.JpaEntityModel;
import org.lambdaql.engine.sem.Normalizer;

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
    private final EntityModel model;
    private final Map<Class<?>, Pred> whereCache = new ConcurrentHashMap<>();

    /**
     * Creates a context.
     *
     * @param emf                 the factory; used to create an {@link EntityManager} when {@code sharedEntityManager} is null
     * @param sharedEntityManager an entity manager to use for every query (for example Spring's transaction-bound proxy), or null
     */
    QueryContext(EntityManagerFactory emf, EntityManager sharedEntityManager) {
        this.emf = emf;
        this.sharedEntityManager = sharedEntityManager;
        this.model = new JpaEntityModel(sharedEntityManager != null ? sharedEntityManager.getMetamodel() : emf.getMetamodel());
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
     * Analyzes a condition lambda (cached per lambda class).
     *
     * @param ref   the lambda
     * @param roots entity class of each lambda parameter
     * @return the normalized condition (without captured values; they are read at run time)
     */
    Pred analyzeWhere(LambdaRef ref, List<Class<?>> roots) {
        if (!ref.isLambdaBody()) {
            throw new org.lambdaql.analyzer.LambdaAnalysisException(
                    "메서드 레퍼런스는 아직 지원하지 않습니다(M7). 람다로 쓰세요: " + ref.describe());
        }
        return whereCache.computeIfAbsent(ref.lambdaClass(), k -> {
            var graph = Decoder.decodePredicate(ref, BytecodeLoader.load(ref));
            Pred pred = ConditionReducer.reduce(graph, ref.describe());
            return Normalizer.normalize(pred, roots, model, ref.classLoader(), ref.describe());
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
