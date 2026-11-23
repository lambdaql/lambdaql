package org.lambdaql.engine.ir;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Examples for the IR classes ({@link Expr}, {@link Pred}, {@link CmpOp}, {@link ArithOp}, {@link MethodRef}).
 * The region markers in this file are used by Javadoc snippet tags,
 * so the Javadoc examples are real, tested code.
 */
class IrExamplesTest {

    private static final Expr STATUS = new Expr.Param(0, Type.INT_TYPE);
    private static final Expr ONE = new Expr.Const(1, Type.INT_TYPE);
    private static final Expr TWO = new Expr.Const(2, Type.INT_TYPE);

    @Test
    void cmpOpNegate() {
        // @start region="cmpop-negate"
        assertEquals(CmpOp.GE, CmpOp.LT.negate());   // NOT (a < b)  is  a >= b
        assertEquals(CmpOp.NE, CmpOp.EQ.negate());   // NOT (a = b)  is  a <> b
        // @end
    }

    @Test
    void cmpOpFlip() {
        // @start region="cmpop-flip"
        assertEquals(CmpOp.GT, CmpOp.LT.flip());     // 10 < price  is  price > 10
        assertEquals(CmpOp.EQ, CmpOp.EQ.flip());     // = does not change
        // @end
    }

    @Test
    void arithSymbol() {
        // @start region="arith-symbol"
        assertEquals("*", ArithOp.MUL.symbol());
        assertEquals("%", ArithOp.REM.symbol());
        // @end
    }

    @Test
    void cmpOpSymbol() {
        // @start region="cmpop-symbol"
        assertEquals("<>", CmpOp.NE.symbol());
        assertEquals(">=", CmpOp.GE.symbol());
        // @end
    }

    @Test
    void predAndIsFlat() {
        Pred x = new Pred.Cmp(CmpOp.EQ, STATUS, ONE, false);
        Pred y = new Pred.Truth(new Expr.Param(1, Type.BOOLEAN_TYPE));
        Pred z = new Pred.Cmp(CmpOp.EQ, STATUS, TWO, false);
        // @start region="pred-and"
        Pred xy = Pred.and(x, y);
        assertEquals(new Pred.And(List.of(x, y, z)), Pred.and(xy, z));   // nested AND becomes flat
        assertEquals(z, Pred.and(Pred.Bool.TRUE, z));                     // TRUE AND z  is  z
        assertEquals(Pred.Bool.FALSE, Pred.and(Pred.Bool.FALSE, z));      // FALSE AND z is FALSE
        // @end
    }

    @Test
    void predOrIsFlat() {
        Pred x = new Pred.Cmp(CmpOp.EQ, STATUS, ONE, false);
        Pred y = new Pred.Cmp(CmpOp.EQ, STATUS, TWO, false);
        Pred z = new Pred.IsNull(STATUS);
        // @start region="pred-or"
        assertEquals(new Pred.Or(List.of(x, y, z)), Pred.or(x, Pred.or(y, z)));   // nested OR becomes flat
        assertEquals(z, Pred.or(Pred.Bool.FALSE, z));                              // FALSE OR z  is  z
        assertEquals(Pred.Bool.TRUE, Pred.or(Pred.Bool.TRUE, z));                  // TRUE OR z   is  TRUE
        // @end
    }

    @Test
    void predNot() {
        Pred x = new Pred.IsNull(STATUS);
        // @start region="pred-not"
        assertEquals(x, Pred.not(Pred.not(x)));                                   // double NOT is removed
        assertEquals(Pred.Bool.FALSE, Pred.not(Pred.Bool.TRUE));
        assertEquals(new Pred.Cmp(CmpOp.EQ, STATUS, ONE, false),
                Pred.not(new Pred.Cmp(CmpOp.NE, STATUS, ONE, false)));           // NOT (a <> 1) is a = 1
        // @end
    }

    @Test
    void methodRef() {
        // @start region="method-ref"
        MethodRef equals = new MethodRef("java/util/Objects", "equals",
                "(Ljava/lang/Object;Ljava/lang/Object;)Z", true, false);
        assertTrue(equals.is("java/util/Objects", "equals"));
        assertEquals(Type.BOOLEAN_TYPE, equals.returnType());
        assertEquals(2, equals.argumentTypes().length);
        assertEquals("Objects", equals.ownerSimpleName());
        // @end
    }

    @Test
    void wideTypes() {
        // @start region="expr-wide"
        assertTrue(Expr.isWide(Type.LONG_TYPE));      // long uses two stack slots
        assertTrue(Expr.isWide(Type.DOUBLE_TYPE));
        assertFalse(Expr.isWide(Type.INT_TYPE));
        // @end
    }

    @Test
    void constNull() {
        // @start region="const-null"
        Expr.Const nothing = Expr.Const.ofNull();
        assertTrue(nothing.isNull());
        assertEquals("null", nothing.toString());
        // @end
    }

    @Test
    void coalesce() {
        // @start region="coalesce"
        // Kotlin: it.price ?: 0.0
        Expr price = new Expr.Param(0, Type.DOUBLE_TYPE);
        Expr e = new Expr.Coalesce(price, new Expr.Const(0.0, Type.DOUBLE_TYPE), Type.DOUBLE_TYPE);
        assertEquals("coalesce(param0, 0.0)", e.toString());
        // @end
    }

    @Test
    void columnAndHost() {
        // @start region="column"
        // o.getCustomer().getName()
        Expr.Column name = new Expr.Column(0, List.of("customer", "name"), List.of(true, false),
                String.class, false, false);
        assertEquals("col0.customer.name", name.toString());
        assertEquals(List.of(true, false), name.association());   // "customer" needs a LEFT JOIN
        // @end
        // @start region="safe-nav"
        // Kotlin it.customer?.name: the decoder marks it.customer, the value checked by ?.
        Expr customer = new Expr.Invoke(new Expr.Param(0, Type.getType(Object.class)),
                new MethodRef("org/lambdaql/query/Order", "getCustomer", "()Lorg/lambdaql/query/Customer;", false, false), List.of());
        Expr.SafeNav checked = new Expr.SafeNav(customer);
        assertEquals(customer.type(), checked.type());
        // the normalizer puts the mark on the column: the "customer" step was reached with ?.
        Expr.Column safeName = new Expr.Column(0, List.of("customer", "name"), List.of(true, false), String.class,
                false, false, List.of(true, false));
        assertTrue(safeName.safeStep(0));
        // the same column as without ?. (only the join kind of a where can differ)
        assertEquals(new Expr.Column(0, List.of("customer", "name"), List.of(true, false), String.class, false, false), safeName);
        // @end
        // @start region="host"
        // user.getName().trim(): no entity inside, so lambdaQL computes it in Java and binds it
        Expr.Host host = new Expr.Host(new Expr.Captured(0, Type.getType(String.class)), String.class, false);
        assertEquals("host(captured0)", host.toString());
        // @end
    }

    @Test
    void predIn() {
        // @start region="pred-in"
        // ids.contains(o.getId())
        Pred in = new Pred.In(new Expr.Param(0, Type.LONG_TYPE), new Expr.Captured(0, Type.getType(List.class)));
        assertEquals("param0 in captured0", in.toString());
        // @end
    }
}
