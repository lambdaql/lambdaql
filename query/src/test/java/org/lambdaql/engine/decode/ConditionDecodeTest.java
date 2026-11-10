package org.lambdaql.engine.decode;

import org.junit.jupiter.api.Test;
import org.lambdaql.analyzer.LambdaAnalysisException;
import org.lambdaql.engine.ir.Pred;
import org.lambdaql.engine.load.BytecodeLoader;
import org.lambdaql.engine.load.LambdaRef;
import org.lambdaql.engine.load.SerializedLambdaExtractor;
import org.lambdaql.query.Order;
import org.lambdaql.query.SelectQuery.Where;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for M1 (loading) and M2/M3 (decoding and reducing) with javac-compiled lambdas.
 * The expected text uses the {@code toString()} format of the IR:
 * {@code param0} is the entity, {@code captured0} is the first captured value,
 * and {@code =~} is a null-safe compare.
 */
class ConditionDecodeTest {

    private int limit = 100;

    /**
     * Decodes a lambda and returns its condition as text.
     *
     * @param where the lambda
     * @return the reduced condition, for example {@code param0.getStatus[] = 1}
     */
    private static String cond(Where<Order> where) {
        LambdaRef ref = SerializedLambdaExtractor.extract(where);
        Node graph = Decoder.decodePredicate(ref, BytecodeLoader.load(ref));
        Pred pred = ConditionReducer.reduce(graph, ref.describe());
        return pred.toString();
    }

    @Test
    void extractsWithoutReflection() {
        long minId = 10;
        LambdaRef ref = SerializedLambdaExtractor.extract((Where<Order>) o -> o.getId() > minId);
        assertTrue(ref.isLambdaBody());
        assertFalse(ref.implIsInstance());
        assertEquals(1, ref.capturedCount());
        assertEquals(10L, ref.capturedArg(0));
    }

    @Test
    void methodReferenceIsDetected() {
        LambdaRef ref = SerializedLambdaExtractor.extract((Where<Order>) Order::isActive);
        assertFalse(ref.isLambdaBody());
    }

    @Test
    void notSerializableIsRejected() {
        java.util.function.Predicate<Order> p = o -> true;
        assertThrows(LambdaAnalysisException.class, () -> SerializedLambdaExtractor.extract(p));
    }

    @Test
    void simpleCompare() {
        assertEquals("param0.getStatus[] = 1", cond(o -> o.getStatus() == 1));
        assertEquals("param0.getStatus[] <> 1", cond(o -> o.getStatus() != 1));
        assertEquals("param0.getPrice[] > 10.0", cond(o -> o.getPrice() > 10));
        assertEquals("param0.getPrice[] <= 10.0", cond(o -> o.getPrice() <= 10));
        assertEquals("param0.getTex[] < 1.5", cond(o -> o.getTex() < 1.5f));
    }

    @Test
    void capturedValues() {
        long minId = 10;
        assertEquals("param0.getId[].longValue[] > captured0", cond(o -> o.getId() > minId));
        assertEquals("param0.getStatus[] > captured0#limit", cond(o -> o.getStatus() > this.limit));
    }

    @Test
    void orChainKeepsOrder() {
        assertEquals("(param0.getStatus[] = 1 || param0.getStatus[] = 2 || param0.getStatus[] = 3)",
                cond(o -> o.getStatus() == 1 || o.getStatus() == 2 || o.getStatus() == 3));
    }

    @Test
    void andRange() {
        assertEquals("(param0.getPrice[] >= 0.0 && param0.getPrice[] <= 1.0)",
                cond(o -> o.getPrice() >= 0 && o.getPrice() <= 1));
    }

    @Test
    void mixedAndOr() {
        assertEquals("((param0.getStatus[] = 1 || truth(param0.isActive[])) && param0.getPrice[] > 10.0)",
                cond(o -> (o.getStatus() == 1 || o.isActive()) && o.getPrice() > 10));
        assertEquals("(param0.getStatus[] = 1 || (param0.getPrice[] < 100.0 && param0.getPrice[] >= 5.0))",
                cond(o -> o.getStatus() == 1 || (o.getPrice() < 100 && o.getPrice() >= 5)));
        assertEquals("(param0.getStatus[] = 1 && (param0.getStatus[] = 2 || param0.getStatus[] = 3) && truth(param0.isActive[]))",
                cond(o -> o.getStatus() == 1 && (o.getStatus() == 2 || o.getStatus() == 3) && o.isActive()));
    }

    @Test
    void notOfComposite() {
        assertEquals("!(truth(param0.isActive[]) && param0.getTex[] > 1.0)",
                cond(o -> !(o.isActive() && o.getTex() > 1f)));
        assertEquals("!truth(param0.isActive[])", cond(o -> !o.isActive()));
    }

    @Test
    void booleanGetterAlone() {
        assertEquals("truth(param0.isActive[])", cond(Order -> Order.isActive()));
    }

    @Test
    void nullGuardThenEquals() {
        String p = "x";
        assertEquals("(param0.getDescription[] is null || truth(param0.getDescription[].equals[captured0]))",
                cond(o -> o.getDescription() == null || o.getDescription().equals(p)));
        assertEquals("(!param0.getDescription[] is null && truth(param0.getDescription[].startsWith[\"a\"]))",
                cond(o -> o.getDescription() != null && o.getDescription().startsWith("a")));
    }

    @Test
    void referenceCompareIsNullSafe() {
        Long id = 7L;
        assertEquals("param0.getId[] =~ captured0", cond(o -> o.getId() == id));
    }

    @Test
    void constantResults() {
        assertEquals("true", cond(o -> true));
        assertEquals("param0.getStatus[] = 1", cond(o -> o.getStatus() == 1 && true));
        assertEquals("true", cond(o -> o.getStatus() == 1 || true));
    }

    @Test
    void valueTernaryIsRejected() {
        boolean flag = true;
        assertThrows(LambdaAnalysisException.class, () -> cond(o -> o.getStatus() == (flag ? 1 : 2)));
    }

    @Test
    void longOrChain() {
        int a = 1, b = 2, c = 3, d = 4, e = 5, f = 6, g = 7, h = 8, i = 9, j = 10, k = 11, l = 12, m = 13, n = 14;
        String text = cond(o -> o.getStatus() == a || o.getStatus() == b || o.getStatus() == c || o.getStatus() == d
                || o.getStatus() == e || o.getStatus() == f || o.getStatus() == g || o.getStatus() == h
                || o.getStatus() == i || o.getStatus() == j || o.getStatus() == k || o.getStatus() == l
                || o.getStatus() == m || o.getStatus() == n);
        StringBuilder expected = new StringBuilder("(");
        for (int x = 0; x < 14; x++) {
            if (x > 0) expected.append(" || ");
            expected.append("param0.getStatus[] = captured").append(x);
        }
        assertEquals(expected.append(")").toString(), text);
    }
}
