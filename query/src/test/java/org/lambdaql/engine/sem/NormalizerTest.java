package org.lambdaql.engine.sem;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.lambdaql.engine.decode.ConditionReducer;
import org.lambdaql.engine.decode.Decoder;
import org.lambdaql.engine.ir.Pred;
import org.lambdaql.engine.load.BytecodeLoader;
import org.lambdaql.engine.load.LambdaRef;
import org.lambdaql.engine.load.SerializedLambdaExtractor;
import org.lambdaql.query.Order;
import org.lambdaql.query.SelectQuery.Where;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Examples for {@link Normalizer}: decoded conditions become columns, host values and literals.
 * The region markers are used by Javadoc snippet tags.
 */
@SpringBootTest(classes = NormalizerTest.Context.class)
@ActiveProfiles("test")
class NormalizerTest {

    @Autowired
    private EntityManagerFactory emf;

    private String normalized(Where<Order> where) {
        EntityModel model = new JpaEntityModel(emf.getMetamodel());
        // @start region="normalize"
        LambdaRef ref = SerializedLambdaExtractor.extract(where);
        Pred decoded = ConditionReducer.reduce(Decoder.decodePredicate(ref, BytecodeLoader.load(ref)), ref.describe());
        Pred pred = Normalizer.normalize(decoded, List.of(Order.class), model, ref.classLoader(), ref.describe());
        // @end
        return pred.toString();
    }

    @Test
    void gettersBecomeColumns() {
        String name = "Kim";
        assertEquals("(col0.status = 1 && col0.price > 100.0)",
                normalized(o -> o.getStatus() == 1 && o.getPrice() > 100));
        assertEquals("col0.customer.name =~ host(captured0)",
                normalized(o -> o.getCustomer().getName().equals(name)));
        assertEquals("col0.customer.id = 1", normalized(o -> o.getCustomer().getId() == 1));
    }

    /** Minimal Spring context: H2 from application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @EntityScan("org.lambdaql.query")
    static class Context {
    }
}
