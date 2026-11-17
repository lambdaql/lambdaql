package org.lambdaql.engine.decode;

import org.junit.jupiter.api.Test;
import org.lambdaql.LambdaQLException;
import org.lambdaql.engine.ir.Pred;
import org.lambdaql.engine.load.BytecodeLoader;
import org.lambdaql.engine.load.LambdaRef;
import org.lambdaql.engine.load.SerializedLambdaExtractor;
import org.lambdaql.query.Order;
import org.lambdaql.query.SelectQuery.Select;
import org.lambdaql.query.SelectQuery.Where;
import org.objectweb.asm.tree.MethodNode;

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
        // @start region="decode-pipeline"
        LambdaRef ref = SerializedLambdaExtractor.extract(where);           // 1. find the lambda body
        MethodNode body = BytecodeLoader.load(ref);                         // 2. read its bytecode
        Node graph = Decoder.decodePredicate(ref, body);                    // 3. bytecode -> decision graph
        Pred pred = ConditionReducer.reduce(graph, ref.describe());         // 4. graph -> condition tree
        // @end
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
        assertThrows(LambdaQLException.class, () -> SerializedLambdaExtractor.extract(p));
    }

    @Test
    void simpleCompare() {
        // @start region="simple-compare"
        assertEquals("param0.getStatus[] = 1", cond(o -> o.getStatus() == 1));
        // @end
        assertEquals("param0.getStatus[] <> 1", cond(o -> o.getStatus() != 1));
        // @start region="double-compare"
        // double compare compiles to dcmpl + ifle; the decoder turns it back into price > 10.0
        assertEquals("param0.getPrice[] > 10.0", cond(o -> o.getPrice() > 10));
        // @end
        assertEquals("param0.getPrice[] <= 10.0", cond(o -> o.getPrice() <= 10));
        assertEquals("param0.getTex[] < 1.5", cond(o -> o.getTex() < 1.5f));
    }

    @Test
    void capturedValues() {
        // @start region="captured"
        long minId = 10;
        assertEquals("param0.getId[].longValue[] > captured0", cond(o -> o.getId() > minId));
        // @end
        // @start region="captured-field"
        // this.limit is a field: "this" is captured (captured0) and the field is read from it
        assertEquals("param0.getStatus[] > captured0#limit", cond(o -> o.getStatus() > this.limit));
        // @end
    }

    @Test
    void orChainKeepsOrder() {
        // @start region="or-chain"
        assertEquals("(param0.getStatus[] = 1 || param0.getStatus[] = 2 || param0.getStatus[] = 3)",
                cond(o -> o.getStatus() == 1 || o.getStatus() == 2 || o.getStatus() == 3));
        // @end
    }

    @Test
    void andRange() {
        assertEquals("(param0.getPrice[] >= 0.0 && param0.getPrice[] <= 1.0)",
                cond(o -> o.getPrice() >= 0 && o.getPrice() <= 1));
    }

    @Test
    void mixedAndOr() {
        // @start region="and-or"
        assertEquals("((param0.getStatus[] = 1 || truth(param0.isActive[])) && param0.getPrice[] > 10.0)",
                cond(o -> (o.getStatus() == 1 || o.isActive()) && o.getPrice() > 10));
        // @end
        assertEquals("(param0.getStatus[] = 1 || (param0.getPrice[] < 100.0 && param0.getPrice[] >= 5.0))",
                cond(o -> o.getStatus() == 1 || (o.getPrice() < 100 && o.getPrice() >= 5)));
        assertEquals("(param0.getStatus[] = 1 && (param0.getStatus[] = 2 || param0.getStatus[] = 3) && truth(param0.isActive[]))",
                cond(o -> o.getStatus() == 1 && (o.getStatus() == 2 || o.getStatus() == 3) && o.isActive()));
    }

    @Test
    void notOfComposite() {
        // @start region="not-composite"
        assertEquals("(!truth(param0.isActive[]) || param0.getTex[] <= 1.0)",
                cond(o -> !(o.isActive() && o.getTex() > 1f)));
        // @end
        assertEquals("!truth(param0.isActive[])", cond(o -> !o.isActive()));
    }

    @Test
    void booleanGetterAlone() {
        // @start region="boolean-getter"
        assertEquals("truth(param0.isActive[])", cond(o -> o.isActive()));
        // @end
    }

    @Test
    void nullGuardThenEquals() {
        String p = "x";
        // @start region="is-null"
        assertEquals("(param0.getDescription[] is null || truth(param0.getDescription[].equals[captured0]))",
                cond(o -> o.getDescription() == null || o.getDescription().equals(p)));
        // @end
        assertEquals("(!param0.getDescription[] is null && truth(param0.getDescription[].startsWith[\"a\"]))",
                cond(o -> o.getDescription() != null && o.getDescription().startsWith("a")));
    }

    @Test
    void referenceCompareIsNullSafe() {
        // @start region="null-safe"
        Long id = 7L;
        assertEquals("param0.getId[] =~ captured0", cond(o -> o.getId() == id));   // =~ is a null-safe compare
        // @end
    }

    @Test
    void constantResults() {
        // @start region="constants"
        assertEquals("true", cond(o -> true));
        assertEquals("param0.getStatus[] = 1", cond(o -> o.getStatus() == 1 && true));   // "&& true" is folded
        assertEquals("true", cond(o -> o.getStatus() == 1 || true));
        // @end
    }

    @Test
    void arithmeticWithCaptured() {
        // @start region="arith"
        double base = 50;
        assertEquals("param0.getPrice[] > (captured0 * 2.0)", cond(o -> o.getPrice() > base * 2));
        // @end
    }

    @Test
    void valueLambda() {
        // @start region="value-lambda"
        LambdaRef ref = SerializedLambdaExtractor.extract((Select<Order, Double>) o -> o.getPrice());
        Node node = Decoder.decodeValue(ref, BytecodeLoader.load(ref));
        // getPrice() returns double, so javac adds Double.valueOf(...) (boxing)
        assertEquals("Double.valueOf[param0.getPrice[]]", ((Node.Value) node).value().toString());
        // @end
    }

    @Test
    void negationAndConversion() {
        // @start region="neg"
        assertEquals("-param0.getPrice[] < -10.0", cond(o -> -o.getPrice() < -10));
        // @end
        // @start region="convert"
        // (long) status compiles to i2l; the conversion does not change the value
        assertEquals("param0.getStatus[] > 1", cond(o -> (long) o.getStatus() > 1L));
        // @end
    }

    @Test
    void arrayLength() {
        // @start region="array-length"
        assertEquals("ArrayLength[array=param0.getImage[]] > 0", cond(o -> o.getImage().length > 0));
        // @end
    }

    @Test
    void newObjectValue() {
        // @start region="new-object"
        LambdaRef ref = SerializedLambdaExtractor.extract(
                (Select<Order, java.math.BigDecimal>) o -> new java.math.BigDecimal(o.getStatus()));
        Node.Value node = (Node.Value) Decoder.decodeValue(ref, BytecodeLoader.load(ref));
        org.lambdaql.engine.ir.Expr.NewObject created = (org.lambdaql.engine.ir.Expr.NewObject) node.value();
        assertEquals("java/math/BigDecimal", created.type().getInternalName());
        assertEquals("param0.getStatus[]", created.args().get(0).toString());
        // @end
    }

    @Test
    void valueTernaryIsRejected() {
        // @start region="value-ternary"
        boolean flag = true;
        LambdaQLException e = assertThrows(LambdaQLException.class, () -> cond(o -> o.getStatus() == (flag ? 1 : 2)));
        assertEquals("LQL-2021", e.code());
        // @end
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
