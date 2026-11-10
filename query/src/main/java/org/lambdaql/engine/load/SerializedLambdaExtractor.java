package org.lambdaql.engine.load;

import org.lambdaql.analyzer.LambdaAnalysisException;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.invoke.SerializedLambda;

/**
 * Gets the {@link SerializedLambda} of a serializable lambda.
 * <p>
 * It does not call the private {@code writeReplace} method with reflection ({@code setAccessible}).
 * Instead it starts normal Java serialization: {@link ObjectOutputStream} calls {@code writeReplace} itself
 * and passes the result to {@link ObjectOutputStream#replaceObject}. We catch the {@code SerializedLambda}
 * there and stop. Because we stop early, the captured values do not need to be serializable.
 *
 * <p>Example:
 * <pre>{@code
 * SelectQuery.Where<Order> w = o -> o.getStatus() == 1;
 * LambdaRef ref = SerializedLambdaExtractor.extract(w);
 * }</pre>
 */
public final class SerializedLambdaExtractor {

    private SerializedLambdaExtractor() {
    }

    /**
     * Gets the lambda information.
     *
     * @param lambda a lambda whose functional interface extends {@link Serializable}
     * @return the body location and captured values
     * @throws LambdaAnalysisException if {@code lambda} is null, not serializable, or not a lambda
     *                                 (for example an anonymous class)
     *
     * <p>Example:
     * <pre>{@code
     * LambdaRef ref = SerializedLambdaExtractor.extract((Where<Order>) o -> o.isActive());
     * ref.capturedCount();  // 0
     * }</pre>
     */
    public static LambdaRef extract(Object lambda) {
        if (lambda == null) {
            throw new LambdaAnalysisException("람다가 null입니다");
        }
        if (!(lambda instanceof Serializable)) {
            throw new LambdaAnalysisException("Serializable 람다만 분석할 수 있습니다: " + lambda.getClass().getName());
        }
        try (Capture out = new Capture()) {
            out.writeObject(lambda);
        } catch (Found found) {
            return new LambdaRef(lambda.getClass(), found.lambda);
        } catch (IOException e) {
            throw new LambdaAnalysisException("SerializedLambda를 얻지 못했습니다: " + lambda.getClass().getName(), e);
        }
        throw new LambdaAnalysisException(
                "람다가 아닌 Serializable 객체입니다(익명 클래스 등): " + lambda.getClass().getName());
    }

    /** Thrown inside {@link Capture} to stop serialization as soon as the lambda is found. */
    private static final class Found extends RuntimeException {
        /** The found lambda. */
        final transient SerializedLambda lambda;

        /**
         * @param lambda the found lambda
         */
        Found(SerializedLambda lambda) {
            super(null, null, false, false);
            this.lambda = lambda;
        }
    }

    /** An output stream that writes nothing and stops at the first {@link SerializedLambda}. */
    private static final class Capture extends ObjectOutputStream {
        /**
         * @throws IOException never in practice (the target is a null stream)
         */
        Capture() throws IOException {
            super(OutputStream.nullOutputStream());
            enableReplaceObject(true);
        }

        /**
         * Called by Java serialization after {@code writeReplace}.
         *
         * @param obj the object to write
         * @return {@code obj} when it is not a lambda
         * @throws Found when {@code obj} is a {@link SerializedLambda}
         */
        @Override
        protected Object replaceObject(Object obj) {
            if (obj instanceof SerializedLambda sl) {
                throw new Found(sl);
            }
            return obj;
        }
    }
}
