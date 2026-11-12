package org.lambdaql.engine.load;

import org.junit.jupiter.api.Test;
import org.lambdaql.analyzer.LambdaAnalysisException;
import org.lambdaql.query.Order;
import org.lambdaql.query.SelectQuery.Where;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Examples for {@link SerializedLambdaExtractor}, {@link LambdaRef} and {@link BytecodeLoader}.
 * The region markers are used by Javadoc {@code {@snippet}} tags.
 */
class LoadExamplesTest {

    private long minId = 5;

    @Test
    void extractCapturedValue() {
        // @start region="extract"
        long minId = 10;
        Where<Order> where = o -> o.getId() > minId;
        LambdaRef ref = SerializedLambdaExtractor.extract(where);

        assertTrue(ref.isLambdaBody());          // a lambda, not a method reference
        assertEquals(1, ref.capturedCount());    // it captured one value
        assertEquals(10L, ref.capturedArg(0));   // the value of minId
        // @end
    }

    @Test
    void instanceLambda() {
        // @start region="instance-lambda"
        Where<Order> where = o -> o.getId() > this.minId;   // uses a field, so "this" is captured
        LambdaRef ref = SerializedLambdaExtractor.extract(where);

        assertTrue(ref.implIsInstance());
        assertEquals(this, ref.capturedArg(0));            // captured value 0 is "this"
        // @end
    }

    @Test
    void methodReference() {
        // @start region="method-reference"
        LambdaRef ref = SerializedLambdaExtractor.extract((Where<Order>) Order::isActive);
        assertFalse(ref.isLambdaBody());                   // Order::isActive is a method reference
        assertEquals("isActive", ref.implMethodName());
        // @end
    }

    @Test
    void notSerializable() {
        // @start region="not-serializable"
        java.util.function.Predicate<Order> notSerializable = o -> o.isActive();
        assertThrows(LambdaAnalysisException.class,
                () -> SerializedLambdaExtractor.extract(notSerializable));
        // @end
    }

    @Test
    void parameterTypesAndName() {
        Where<Order> where = o -> o.isActive();
        // @start region="lambda-ref-info"
        LambdaRef ref = SerializedLambdaExtractor.extract(where);
        Type[] params = ref.lambdaParameterTypes();
        assertEquals(Type.getType(Order.class), params[0]);               // the lambda parameter o
        assertTrue(ref.describe().startsWith("org.lambdaql.engine.load.LoadExamplesTest.lambda$"));
        assertEquals(0, ref.capturedArgs().length);
        // @end
    }

    @Test
    void loadBody() {
        // @start region="load-body"
        LambdaRef ref = SerializedLambdaExtractor.extract((Where<Order>) o -> o.getStatus() == 1);
        MethodNode body = BytecodeLoader.load(ref);

        assertEquals(ref.implMethodName(), body.name);
        assertTrue(body.instructions.size() > 0);
        // @end
    }

    @Test
    void loadUnknownMethod() {
        // @start region="load-missing"
        assertThrows(LambdaAnalysisException.class, () -> BytecodeLoader.load(
                getClass().getClassLoader(), "org/lambdaql/query/Order", "noSuchMethod", "()V"));
        // @end
    }
}
