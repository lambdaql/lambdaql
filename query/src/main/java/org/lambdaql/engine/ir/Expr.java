package org.lambdaql.engine.ir;

import org.objectweb.asm.Type;

import java.util.List;
import java.util.Objects;

/**
 * A value expression made from lambda bytecode.
 * <p>
 * lambdaQL never runs your lambda. It reads the bytecode and builds a tree of {@code Expr} nodes.
 * There are two kinds of nodes:
 * <ul>
 *   <li><b>Raw nodes</b> — made by the decoder from bytecode:
 *       {@link Param}, {@link Captured}, {@link Const}, {@link Invoke}, {@link GetField}, {@link Arith},
 *       {@link Neg}, {@link Convert}, {@link CmpResult}, {@link NewObject}, {@link ArrayLength}, {@link Coalesce}.</li>
 *   <li><b>Meaning nodes</b> — made later by the semantic step:
 *       {@link Column} (an entity column) and {@link Host} (a value we compute in Java and bind as a parameter).</li>
 * </ul>
 * The HQL renderer only accepts {@link Column}, {@link Host}, {@link Const}, {@link Arith}, {@link Neg} and {@link Coalesce}.
 *
 * <p>Example: the lambda {@code o -> o.getPrice() > base * 2} becomes
 * <pre>{@code
 * Cmp(GT,
 *     Invoke(Param(0), Order.getPrice()),
 *     Arith(MUL, Captured(0), Const(2)))
 * }</pre>
 * and after the semantic step
 * <pre>{@code
 * Cmp(GT, Column(o.price), Host(Arith(MUL, Captured(0), Const(2))))
 * }</pre>
 */
public sealed interface Expr permits
        Expr.Param, Expr.Captured, Expr.Const, Expr.Invoke, Expr.GetField, Expr.Arith, Expr.Neg,
        Expr.Convert, Expr.CmpResult, Expr.NewObject, Expr.ArrayLength, Expr.Coalesce, Expr.Column, Expr.Host {

    /**
     * Returns the JVM type of this value.
     *
     * @return the ASM type (for example {@code Type.INT_TYPE} or {@code Type.getType(String.class)});
     *         {@code java/lang/Object} when the type is unknown
     */
    Type type();

    /**
     * A lambda parameter, such as the entity {@code o} in {@code o -> ...}.
     * Captured values are not counted.
     *
     * @param index position in the lambda parameter list (0 = first parameter)
     * @param type  JVM type of the parameter
     *
     * <p>Example: in {@code (o, c) -> ...}, {@code o} is {@code Param(0)} and {@code c} is {@code Param(1)}.
     */
    record Param(int index, Type type) implements Expr {
        @Override public String toString() { return "param" + index; }
    }

    /**
     * A captured value: a local variable or field that the lambda uses from outside.
     *
     * @param index position in {@code SerializedLambda.getCapturedArg(index)};
     *              when the lambda body is an instance method, index 0 is {@code this}
     * @param type  JVM type of the value
     *
     * <p>Example:
     * <pre>{@code
     * long minId = 10;
     * where(o -> o.getId() > minId)   // minId is Captured(0)
     * }</pre>
     */
    record Captured(int index, Type type) implements Expr {
        @Override public String toString() { return "captured" + index; }
    }

    /**
     * A constant value from the bytecode.
     * <p>
     * {@code boolean}, {@code char}, {@code short} and {@code byte} constants come as {@link Integer},
     * because the JVM uses int for them.
     *
     * @param value the value; {@code null} for the {@code null} literal; an ASM {@link Type} for a class literal
     * @param type  JVM type of the value
     *
     * <p>Example: {@code o.getStatus() == 10} gives {@code Const(10, int)}.
     */
    record Const(Object value, Type type) implements Expr {
        /** Type used for the {@code null} literal. */
        public static final Type NULL_TYPE = Type.getObjectType("java/lang/Object");

        /**
         * Creates the {@code null} literal.
         *
         * @return a constant with value {@code null}
         */
        public static Const ofNull() { return new Const(null, NULL_TYPE); }

        /**
         * Checks if this is the {@code null} literal.
         *
         * @return {@code true} if the value is {@code null}
         */
        public boolean isNull() { return value == null; }

        @Override public String toString() { return value instanceof String s ? '"' + s + '"' : String.valueOf(value); }
    }

    /**
     * A method call.
     *
     * @param receiver the object the method is called on; {@code null} for a static method
     * @param method   the called method
     * @param args     the arguments, in order
     *
     * <p>Example: {@code o.getCustomer().getName()} is
     * {@code Invoke(Invoke(Param(0), getCustomer), getName)}.
     */
    record Invoke(Expr receiver, MethodRef method, List<Expr> args) implements Expr {
        /** Makes an unmodifiable copy of the argument list. */
        public Invoke {
            args = List.copyOf(args);
        }
        @Override public Type type() { return method.returnType(); }
        @Override public String toString() {
            String r = receiver == null ? method.ownerSimpleName() : receiver.toString();
            return r + "." + method.name() + args;
        }
    }

    /**
     * A field read.
     *
     * @param receiver the object; {@code null} for a static field
     * @param owner    internal name of the class that declares the field (for example {@code com/acme/Order})
     * @param name     field name
     * @param type     JVM type of the field
     *
     * <p>Example: {@code this.limit} inside a lambda is {@code GetField(Captured(0), "Repo", "limit")}.
     */
    record GetField(Expr receiver, String owner, String name, Type type) implements Expr {
        @Override public String toString() {
            return (receiver == null ? owner.substring(owner.lastIndexOf('/') + 1) : receiver.toString()) + "#" + name;
        }
    }

    /**
     * A math operation with two values.
     *
     * @param op    the operation ({@code +}, {@code -}, {@code *}, {@code /}, {@code %})
     * @param left  left value
     * @param right right value
     * @param type  JVM type of the result
     *
     * <p>Example: {@code o.getPrice() * 1.1} is {@code Arith(MUL, price, Const(1.1))}.
     */
    record Arith(ArithOp op, Expr left, Expr right, Type type) implements Expr {
        @Override public String toString() { return "(" + left + " " + op.symbol() + " " + right + ")"; }
    }

    /**
     * Unary minus.
     *
     * @param operand the value
     * @param type    JVM type of the result
     *
     * <p>Example: {@code -o.getPrice()} is {@code Neg(price)}.
     */
    record Neg(Expr operand, Type type) implements Expr {
        @Override public String toString() { return "-" + operand; }
    }

    /**
     * A type change: a primitive conversion ({@code I2L} and so on) or a {@code CHECKCAST}.
     * It does not change the value, so later steps usually look through it.
     *
     * @param operand the value
     * @param type    the new type
     *
     * <p>Example: {@code (long) o.getStatus()} is {@code Convert(status, long)}.
     */
    record Convert(Expr operand, Type type) implements Expr {
        @Override public String toString() { return operand.toString(); }
    }

    /**
     * The result (-1, 0 or 1) of {@code LCMP}, {@code FCMPL}, {@code FCMPG}, {@code DCMPL} or {@code DCMPG}.
     * The next {@code IFxx} jump turns it into a {@link Pred.Cmp}.
     *
     * @param left       left value
     * @param right      right value
     * @param nanGreater {@code true} for the "G" opcodes (NaN gives 1); kept so the source operator can be found
     *
     * <p>Example: {@code o.getPrice() > 10.0} compiles to {@code dcmpl; ifle},
     * which becomes {@code CmpResult(price, 10.0)} and then {@code Cmp(LE, price, 10.0)} on the jump.
     */
    record CmpResult(Expr left, Expr right, boolean nanGreater) implements Expr {
        @Override public Type type() { return Type.INT_TYPE; }
    }

    /**
     * A new object: {@code NEW}, {@code DUP}, arguments, then {@code INVOKESPECIAL <init>}.
     *
     * @param type        the class of the new object
     * @param constructor the called constructor
     * @param args        constructor arguments, in order
     *
     * <p>Example: {@code new OrderView(o.getId(), o.getPrice())} in a select lambda.
     */
    record NewObject(Type type, MethodRef constructor, List<Expr> args) implements Expr {
        /** Makes an unmodifiable copy of the argument list. */
        public NewObject {
            args = List.copyOf(args);
        }
    }

    /**
     * The length of an array ({@code ARRAYLENGTH}).
     *
     * @param array the array value
     *
     * <p>Example: {@code o.getImage().length}.
     */
    record ArrayLength(Expr array) implements Expr {
        @Override public Type type() { return Type.INT_TYPE; }
    }

    /**
     * {@code coalesce(first, second)}: {@code first} if it is not null, else {@code second}.
     *
     * @param first  the value to try first
     * @param second the fallback value
     * @param type   JVM type of the result
     *
     * <p>Example (Kotlin): {@code it.price ?: 0.0} becomes {@code Coalesce(price, 0.0)}.
     */
    record Coalesce(Expr first, Expr second, Type type) implements Expr {
        @Override public String toString() { return "coalesce(" + first + ", " + second + ")"; }
    }

    /**
     * Meaning node: an entity column path.
     *
     * @param root        which FROM entity the path starts at (0 = main entity)
     * @param path        property names from the root (for example {@code ["customer", "name"]})
     * @param association one flag per path part: {@code true} if that part is a to-one association
     *                    (the renderer adds a LEFT JOIN for it)
     * @param javaType    Java type of the column value (boxed for primitives)
     * @param nonNull     {@code true} if the column can never be NULL (id, primitive, {@code nullable = false})
     * @param idPath      {@code true} if the path ends at the id of an association (a foreign key column,
     *                    so no join is needed)
     *
     * <p>Example: {@code o.getCustomer().getName()} becomes
     * {@code Column(0, ["customer", "name"], [true, false], String.class, false, false)}.
     */
    record Column(int root, List<String> path, List<Boolean> association, Class<?> javaType,
                  boolean nonNull, boolean idPath) implements Expr {
        /** Makes unmodifiable copies of the lists. */
        public Column {
            path = List.copyOf(path);
            association = List.copyOf(association);
        }
        @Override public Type type() { return Type.getType(javaType); }
        @Override public String toString() { return "col" + root + "." + String.join(".", path); }
    }

    /**
     * Meaning node: a value that does not depend on any entity.
     * lambdaQL computes it in Java when the query runs and binds it as a parameter.
     *
     * @param expr            the expression to compute (uses only captured values and constants)
     * @param javaType        Java type to bind (numbers are converted to this type)
     * @param nonNullRequired {@code true} if a null value must be an error, because Java would throw
     *                        {@code NullPointerException} (for example the receiver of {@code p.equals(...)})
     *
     * <p>Example: {@code o.getName().equals(user.getName().trim())} — the right side
     * {@code user.getName().trim()} becomes a {@code Host} and is bound as {@code :p1}.
     */
    record Host(Expr expr, Class<?> javaType, boolean nonNullRequired) implements Expr {
        @Override public Type type() { return Type.getType(javaType); }
        @Override public String toString() { return "host(" + expr + ")"; }
    }

    /**
     * Checks if a JVM type takes two stack slots.
     *
     * @param t the type
     * @return {@code true} for {@code long} and {@code double}
     *
     * <p>Example: {@code isWide(Type.LONG_TYPE)} is {@code true}; {@code isWide(Type.INT_TYPE)} is {@code false}.
     */
    static boolean isWide(Type t) {
        return t.getSort() == Type.LONG || t.getSort() == Type.DOUBLE;
    }

    /**
     * Checks if two expressions are the same tree.
     *
     * @param a first expression (may be null)
     * @param b second expression (may be null)
     * @return {@code true} if both trees are equal
     */
    static boolean sameValue(Expr a, Expr b) {
        return Objects.equals(a, b);
    }
}
