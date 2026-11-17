package org.lambdaql.engine.decode;

import org.junit.jupiter.api.Test;
import org.lambdaql.engine.ir.CmpOp;
import org.lambdaql.engine.ir.Expr;
import org.lambdaql.engine.ir.Pred;
import org.objectweb.asm.Type;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Examples for {@link NegationMinimizer}. The region markers are used by Javadoc {@code {@snippet}} tags.
 */
class NegationMinimizerTest {

    private static final Expr STATUS = new Expr.Param(0, Type.INT_TYPE);
    private static final Expr ACTIVE = new Expr.Param(1, Type.BOOLEAN_TYPE);

    private static Expr num(int v) {
        return new Expr.Const(v, Type.INT_TYPE);
    }

    @Test
    void deMorgan() {
        // @start region="de-morgan"
        // !(status <> 1 && status <> 2)   ==>   status = 1 || status = 2
        Pred compiled = Pred.not(new Pred.And(List.of(
                new Pred.Cmp(CmpOp.NE, STATUS, num(1), false),
                new Pred.Cmp(CmpOp.NE, STATUS, num(2), false))));
        Pred expected = new Pred.Or(List.of(
                new Pred.Cmp(CmpOp.EQ, STATUS, num(1), false),
                new Pred.Cmp(CmpOp.EQ, STATUS, num(2), false)));
        assertEquals(expected, NegationMinimizer.minimize(compiled));
        // @end
    }

    @Test
    void pushNotIntoConditions() {
        // @start region="push-not"
        // !(active && status > 1)  ==>  !active || status <= 1 : a NOT on one condition has its own SQL form
        Pred source = Pred.not(new Pred.And(List.of(
                new Pred.Truth(ACTIVE),
                new Pred.Cmp(CmpOp.GT, STATUS, num(1), false))));
        assertEquals("(!truth(param1) || param0 <= 1)", NegationMinimizer.minimize(source).toString());
        // @end
    }
}
