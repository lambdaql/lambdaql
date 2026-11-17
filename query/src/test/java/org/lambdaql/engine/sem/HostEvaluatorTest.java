package org.lambdaql.engine.sem;

import org.junit.jupiter.api.Test;
import org.lambdaql.LambdaQLException;
import org.lambdaql.engine.ir.ArithOp;
import org.lambdaql.engine.ir.Expr;
import org.lambdaql.engine.ir.MethodRef;
import org.objectweb.asm.Type;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Examples for {@link HostEvaluator}. The region markers are used by Javadoc {@code {@snippet}} tags.
 */
class HostEvaluatorTest {

    private final HostEvaluator evaluator = new HostEvaluator(getClass().getClassLoader());

    @Test
    void arithmetic() {
        // @start region="eval-arith"
        // captured0 * 2   with captured0 = 21
        Expr e = new Expr.Arith(ArithOp.MUL, new Expr.Captured(0, Type.INT_TYPE),
                new Expr.Const(2, Type.INT_TYPE), Type.INT_TYPE);
        assertEquals(42, evaluator.eval(e, new Object[]{21}));
        // @end
    }

    @Test
    void methodCall() {
        // @start region="eval-call"
        // captured0.trim()   with captured0 = "  Kim "
        MethodRef trim = new MethodRef("java/lang/String", "trim", "()Ljava/lang/String;", false, false);
        Expr e = new Expr.Invoke(new Expr.Captured(0, Type.getType(String.class)), trim, List.of());
        assertEquals("Kim", evaluator.eval(e, new Object[]{"  Kim "}));
        // @end
    }

    @Test
    void nullReceiver() {
        // @start region="eval-null-receiver"
        // captured0.trim()   with captured0 = null  -> Java would throw NullPointerException
        MethodRef trim = new MethodRef("java/lang/String", "trim", "()Ljava/lang/String;", false, false);
        Expr e = new Expr.Invoke(new Expr.Captured(0, Type.getType(String.class)), trim, List.of());
        assertThrows(LambdaQLException.class, () -> evaluator.eval(e, new Object[]{null}));
        // @end
    }

    @Test
    void convertNumbers() {
        // @start region="convert"
        assertEquals((short) 10, HostEvaluator.convert(10, Short.class));        // int -> short column
        assertEquals(10L, HostEvaluator.convert(10, Long.class));
        assertEquals(new BigDecimal("1.5"), HostEvaluator.convert(1.5, BigDecimal.class));
        assertEquals("text", HostEvaluator.convert("text", Short.class));        // not a number: unchanged
        // @end
    }
}
