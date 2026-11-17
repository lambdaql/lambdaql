package org.lambdaql.engine.load;

import org.lambdaql.i18n.LqlMessage;
import org.lambdaql.LambdaAnalysisException;

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
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/load/LoadExamplesTest.java">LoadExamplesTest</a>):
 * {@snippet class="org.lambdaql.engine.load.LoadExamplesTest" region="extract"}
 */
public final class SerializedLambdaExtractor {

    private SerializedLambdaExtractor() {
    }

    /**
     * Gets the lambda information.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/load/LoadExamplesTest.java">LoadExamplesTest</a>):
     * {@snippet class="org.lambdaql.engine.load.LoadExamplesTest" region="extract"}
     * {@snippet class="org.lambdaql.engine.load.LoadExamplesTest" region="not-serializable"}
     *
     * @param lambda a lambda whose functional interface extends {@link Serializable}
     * @return the body location and captured values
     * @throws LambdaAnalysisException if {@code lambda} is null, not serializable, or not a lambda
     *                                 (for example an anonymous class)
     */
    public static LambdaRef extract(Object lambda) {
        if (lambda == null) {
            throw LqlMessage.LAMBDA_NULL.exception();
        }
        if (!(lambda instanceof Serializable)) {
            throw LqlMessage.LAMBDA_NOT_SERIALIZABLE.exception(lambda.getClass().getName());
        }
        try (Capture out = new Capture()) {
            out.writeObject(lambda);
        } catch (Found found) {
            return new LambdaRef(lambda.getClass(), found.lambda);
        } catch (IOException e) {
            throw LqlMessage.SERIALIZED_LAMBDA_FAILED.exceptionCausedBy(null, e, lambda.getClass().getName());
        }
        throw LqlMessage.NOT_A_LAMBDA.exception(lambda.getClass().getName());
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
