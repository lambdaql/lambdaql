package org.lambdaql.engine.load;

import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandleInfo;
import java.lang.invoke.SerializedLambda;

/**
 * One serializable lambda: where its body is, and the values it captured this time.
 * <p>
 * The body location (class, method, descriptor) is the same for every call of the same lambda in your code.
 * The captured values can change on every call.
 *
 * @param lambdaClass the runtime class of the lambda object
 * @param serialized  the {@link SerializedLambda} of the lambda object
 *
 * <p>Example:
 * <pre>{@code
 * long minId = 10;
 * Where<Order> w = o -> o.getId() > minId;
 * LambdaRef ref = SerializedLambdaExtractor.extract(w);
 * ref.implMethodName();   // "lambda$find$0"
 * ref.capturedArg(0);     // 10L
 * }</pre>
 */
public record LambdaRef(Class<?> lambdaClass, SerializedLambda serialized) {

    /**
     * Returns the class that holds the lambda body.
     *
     * @return internal class name, for example {@code com/acme/OrderRepo}
     */
    public String implClass() {
        return serialized.getImplClass();
    }

    /**
     * Returns the name of the method that holds the lambda body.
     *
     * @return method name, for example {@code lambda$find$0} (Java) or {@code find$lambda$0} (Kotlin)
     */
    public String implMethodName() {
        return serialized.getImplMethodName();
    }

    /**
     * Returns the descriptor of the body method. Captured values come first, then lambda parameters.
     *
     * @return JVM method descriptor, for example {@code (JLcom/acme/Order;)Z}
     */
    public String implMethodDescriptor() {
        return serialized.getImplMethodSignature();
    }

    /**
     * Returns how the body method is called.
     *
     * @return a {@link MethodHandleInfo} {@code REF_*} constant, for example {@code REF_invokeStatic}
     */
    public int implMethodKind() {
        return serialized.getImplMethodKind();
    }

    /**
     * Returns the number of captured values.
     *
     * @return the count (0 if the lambda captures nothing)
     */
    public int capturedCount() {
        return serialized.getCapturedArgCount();
    }

    /**
     * Returns one captured value.
     *
     * @param index position, from 0
     * @return the value; for an instance lambda body, index 0 is {@code this}
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    public Object capturedArg(int index) {
        return serialized.getCapturedArg(index);
    }

    /**
     * Returns all captured values.
     *
     * @return a new array with the captured values in order
     */
    public Object[] capturedArgs() {
        Object[] out = new Object[capturedCount()];
        for (int i = 0; i < out.length; i++) out[i] = capturedArg(i);
        return out;
    }

    /**
     * Returns the class loader to read the body class from.
     *
     * @return the lambda's class loader, or the system class loader if it has none
     */
    public ClassLoader classLoader() {
        ClassLoader cl = lambdaClass.getClassLoader();
        return cl != null ? cl : ClassLoader.getSystemClassLoader();
    }

    /**
     * Checks if the body method is an instance method. Then captured value 0 is {@code this}.
     *
     * @return {@code true} for virtual, special or interface calls
     *
     * <p>Example: a lambda that uses a field of the enclosing object ({@code o -> o.getId() > this.minId})
     * has an instance body.
     */
    public boolean implIsInstance() {
        int k = implMethodKind();
        return k == MethodHandleInfo.REF_invokeVirtual || k == MethodHandleInfo.REF_invokeSpecial
                || k == MethodHandleInfo.REF_invokeInterface;
    }

    /**
     * Checks if the body is a compiler-made lambda body (not a method reference).
     * javac names it {@code lambda$...}; Kotlin names it {@code ...$lambda$N}.
     * Kotlin body methods are not marked synthetic, so the name is used.
     *
     * @return {@code true} for a lambda body; {@code false} for a method reference like {@code Order::isActive}
     */
    public boolean isLambdaBody() {
        String n = implMethodName();
        return n.startsWith("lambda$") || n.contains("$lambda$") || n.contains("$lambda-");
    }

    /**
     * Returns the types of the lambda parameters (without captured values).
     *
     * @return JVM types, for example {@code [Lcom/acme/Order;]}
     */
    public Type[] lambdaParameterTypes() {
        return Type.getArgumentTypes(serialized.getInstantiatedMethodType());
    }

    /**
     * Returns a readable name of the body method for error messages.
     *
     * @return for example {@code com.acme.OrderRepo.lambda$find$0(JLcom/acme/Order;)Z}
     */
    public String describe() {
        return implClass().replace('/', '.') + "." + implMethodName() + implMethodDescriptor();
    }
}
